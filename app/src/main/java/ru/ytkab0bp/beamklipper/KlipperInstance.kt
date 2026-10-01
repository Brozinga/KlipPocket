package ru.ytkab0bp.beamklipper

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import ru.ytkab0bp.beamklipper.events.InstanceStateChangedEvent
import ru.ytkab0bp.beamklipper.events.InstancesRefreshedEvent
import ru.ytkab0bp.beamklipper.events.WebStateChangedEvent
import ru.ytkab0bp.beamklipper.service.*
import ru.ytkab0bp.beamklipper.utils.Prefs
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class KlipperInstance {
    @JvmField
    var name: String = ""
    @JvmField
    var id: String? = null
    @JvmField
    var icon: InstanceIcon = InstanceIcon.PRINTER
    @JvmField
    var autostart = false

    private var state: State = State.IDLE
    private var klippyIntent: Intent? = null
    private var klippyConnection: ServiceConnection? = null
    private var klippyConnected = false
    private var moonrakerIntent: Intent? = null
    private var moonrakerConnection: ServiceConnection? = null
    private var moonrakerConnected = false
    private var slot = 0
    private var lastRunning = 0L
    private var watchdogCount = 0
    private var isUserStop = false
    private var wasPrinting = false
    @Volatile private var stopRequested = false
    private val stopSentinel = AtomicBoolean(false)

    fun getState(): State = state

    val directory: File
        get() = File(KlipperApp.INSTANCE.filesDir, "instance${File.separator}$id")

    val publicDirectory: File
        get() = File(directory, "public")

    fun start() {
        if (state == State.RUNNING || state == State.STARTING) return
        if (state == State.STOPPING) {
            Log.i(TAG, "start: state is STOPPING; resetting stop flags and transitioning to IDLE first for id=$id name=$name")
            stopSentinel.set(false)
            stopRequested = false
            isUserStop = false
            watchdogCount = 0
            notifyStateChanged(State.IDLE)
        }
        if (state != State.IDLE) return
        isUserStop = false
        stopRequested = false
        stopSentinel.set(false)
        watchdogCount = 0
        notifyStateChanged(State.STARTING)

        if (!directory.exists() && !directory.mkdirs()) {
            Log.w(TAG, "Failed to create instance directory ($id)")
            stop()
            return
        }
        if (!publicDirectory.exists() && !publicDirectory.mkdirs()) {
            Log.w(TAG, "Failed to create public instance directory ($id)")
            stop()
            return
        }
        val cfg = File(publicDirectory, "config")
        if (!cfg.exists() && !cfg.mkdirs()) {
            Log.w(TAG, "Failed to create config directory ($id)")
        }
        val gcodes = File(publicDirectory, "gcodes")
        if (!gcodes.exists() && !gcodes.mkdirs()) {
            Log.w(TAG, "Failed to create gcodes directory ($id)")
        }
        val logs = File(publicDirectory, "logs")
        if (!logs.exists() && !logs.mkdirs()) {
            Log.w(TAG, "Failed to create logs directory ($id)")
        }
        val timelapses = File(publicDirectory, "timelapses")
        if (!timelapses.exists() && !timelapses.mkdirs()) {
            Log.w(TAG, "Failed to create timelapses directory ($id)")
        }

        val instIdForSlot = id ?: throw IllegalStateException("KlipperInstance.id is null at start()")
        slot = SlotAllocator.allocate(instIdForSlot, slotById, SLOTS_COUNT)
        Log.i(TAG, "start: recorded slot=$slot for id=$instIdForSlot (name=$name)")
        slotById[instIdForSlot] = slot
        instanceBySlotId[instIdForSlot] = this
        @Suppress("DEPRECATION")
        slots[this] = slot
        val instId = id
        mainHandler.post {
            if (stopRequested || stopSentinel.get()) {
                Log.w(TAG, "start: aborting bind for id=$instId because stop already requested")
                notifyStateChanged(State.IDLE)
                return@post
            }
            try {
                val kIntent = Intent(KlipperApp.INSTANCE, Class.forName("ru.ytkab0bp.beamklipper.service.KlippyService_$slot"))
                klippyIntent = kIntent
                kIntent.putExtra(BasePythonService.KEY_INSTANCE, instId)
                val kConn = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, service: IBinder) {
                        if (stopRequested || stopSentinel.get() || state == State.STOPPING || state == State.IDLE) {
                            Log.w(TAG, "start: klippy onServiceConnected after stop; tearing down immediately for id=$instId")
                            try { KlipperApp.INSTANCE.unbindService(this) } catch (_: Throwable) {}
                            try { KlipperApp.INSTANCE.stopService(kIntent) } catch (_: Throwable) {}
                            return
                        }
                        Log.i("beam_service", "klippy connected!"); klippyConnected = true
                        watchdogCount = 0
                        if (moonrakerConnected) {
                            notifyStateChanged(State.RUNNING)
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName) {
                        Log.w("beam_service", "klippy onServiceDisconnected for id=$instId")
                        onKlippyUnbound(watchdogRestart = !stopRequested && !stopSentinel.get())
                    }

                    override fun onBindingDied(name: ComponentName) {
                        Log.w("beam_service", "klippy onBindingDied for id=$instId")
                        onKlippyUnbound(watchdogRestart = !stopRequested && !stopSentinel.get())
                    }
                }
                val b1 = KlipperApp.INSTANCE.bindService(kIntent, kConn, Context.BIND_AUTO_CREATE)
                klippyConnection = kConn
                Log.i("beam_service", "bind klippy: $b1")
            } catch (e: ClassNotFoundException) {
                throw RuntimeException(e)
            }
            try {
                val mIntent = Intent(KlipperApp.INSTANCE, Class.forName("ru.ytkab0bp.beamklipper.service.MoonrakerService_$slot"))
                moonrakerIntent = mIntent
                mIntent.putExtra(BasePythonService.KEY_INSTANCE, instId)
                val mConn = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, service: IBinder) {
                        if (stopRequested || stopSentinel.get() || state == State.STOPPING || state == State.IDLE) {
                            Log.w(TAG, "start: moonraker onServiceConnected after stop; tearing down immediately for id=$instId")
                            try { KlipperApp.INSTANCE.unbindService(this) } catch (_: Throwable) {}
                            try { KlipperApp.INSTANCE.stopService(mIntent) } catch (_: Throwable) {}
                            return
                        }
                        Log.i("beam_service", "moonraker connected!"); moonrakerConnected = true
                        if (klippyConnected) {
                            notifyStateChanged(State.RUNNING)
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName) {
                        Log.w("beam_service", "moonraker onServiceDisconnected for id=$instId")
                        onMoonrakerUnbound(watchdogRestart = !stopRequested && !stopSentinel.get())
                    }

                    override fun onBindingDied(name: ComponentName) {
                        Log.w("beam_service", "moonraker onBindingDied for id=$instId")
                        onMoonrakerUnbound(watchdogRestart = !stopRequested && !stopSentinel.get())
                    }
                }
                KlipperApp.INSTANCE.bindService(mIntent, mConn, Context.BIND_AUTO_CREATE)
                moonrakerConnection = mConn
            } catch (e: ClassNotFoundException) {
                throw RuntimeException(e)
            }
        }
    }

    fun stop() {
        if (state != State.RUNNING && state != State.STARTING) return
        Log.d(TAG, "stop() called for instance id=$id name=$name, currentState=$state")
        val alreadyStopping = !stopSentinel.compareAndSet(false, true)
        if (alreadyStopping) {
            Log.w(TAG, "stop: already stopping or stopped for id=$id; ignoring duplicate stop()")
            return
        }
        isUserStop = true
        stopRequested = true
        watchdogCount = 0
        notifyStateChanged(State.STOPPING)

        val capturedSlot = slot
        val capturedKlippyConn = klippyConnection
        val capturedMoonrakerConn = moonrakerConnection
        val capturedKlippyIntent = klippyIntent
        val capturedMoonrakerIntent = moonrakerIntent

        mainHandler.post {
            val nm = KlipperApp.INSTANCE.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (capturedKlippyConn != null) {
                try { KlipperApp.INSTANCE.unbindService(capturedKlippyConn) } catch (_: Throwable) {}
            }
            if (capturedMoonrakerConn != null) {
                try { KlipperApp.INSTANCE.unbindService(capturedMoonrakerConn) } catch (_: Throwable) {}
            }
            if (capturedKlippyIntent != null) {
                try { KlipperApp.INSTANCE.stopService(capturedKlippyIntent) } catch (_: Throwable) {}
            }
            if (capturedMoonrakerIntent != null) {
                try { KlipperApp.INSTANCE.stopService(capturedMoonrakerIntent) } catch (_: Throwable) {}
            }
            onKlippyUnbound()
            onMoonrakerUnbound()
            try { nm.cancel(BaseKlippyService.BASE_ID + capturedSlot) } catch (_: Throwable) {}
            try { nm.cancel(BaseMoonrakerService.BASE_ID + capturedSlot) } catch (_: Throwable) {}
            try { nm.cancel(WATCHDOG_BASE_ID + capturedSlot) } catch (_: Throwable) {}
        }
        klippyConnected = false
        moonrakerConnected = false
        klippyConnection = null
        moonrakerConnection = null
        klippyIntent = null
        moonrakerIntent = null

        mainHandler.postDelayed({
            val nm = KlipperApp.INSTANCE.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            try { nm.cancel(BaseKlippyService.BASE_ID + capturedSlot) } catch (_: Throwable) {}
            try { nm.cancel(BaseMoonrakerService.BASE_ID + capturedSlot) } catch (_: Throwable) {}
            try { nm.cancel(WATCHDOG_BASE_ID + capturedSlot) } catch (_: Throwable) {}
            if (state == State.STOPPING) {
                notifyStateChanged(State.IDLE)
            }
        }, 1000L)
    }

    private fun fireWatchdogNotification(serviceName: String, attempt: Int, isRestarting: Boolean) {
        try {
            val ctx = KlipperApp.INSTANCE
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val mainIntent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pi = PendingIntent.getActivity(ctx, WATCHDOG_REQ_ID + slot, mainIntent, flags)
            val title = ctx.getString(R.string.WatchdogCrashTitle, name)
            val text = if (isRestarting) {
                ctx.getString(R.string.WatchdogCrashRestarting, serviceName, attempt, WATCHDOG_MAX_RETRIES)
            } else {
                ctx.getString(R.string.WatchdogCrashGaveUp, serviceName, WATCHDOG_MAX_RETRIES)
            }
            val notif = NotificationCompat.Builder(ctx, KlipperApp.WATCHDOG_CHANNEL)
                .setSmallIcon(R.drawable.icon_static)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(if (isRestarting) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setDefaults(Notification.DEFAULT_VIBRATE or Notification.DEFAULT_SOUND or Notification.DEFAULT_LIGHTS)
                .setOngoing(!isRestarting)
                .setAutoCancel(isRestarting)
                .setColor(0xFFD4A017.toInt())
                .setContentIntent(pi)
                .build()
            nm.notify(WATCHDOG_BASE_ID + slot, notif)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to fire watchdog notification", t)
        }
    }

    private fun tryWatchdogRestart(whichService: String) {
        if (isUserStop || stopRequested || stopSentinel.get()) {
            Log.i(TAG, "tryWatchdogRestart suppressed: user initiated stop for id=$id")
            return
        }
        val currentState = state
        if (currentState != State.RUNNING && currentState != State.STARTING) {
            Log.i(TAG, "tryWatchdogRestart suppressed: state=$currentState for id=$id")
            return
        }
        val decision = WatchdogPolicy.decide(watchdogCount, lastRunning, System.currentTimeMillis())
        val wasShort = decision.wasShort
        watchdogCount = decision.attempt
        val shouldRestart = decision.shouldRestart
        Log.w(TAG, "tryWatchdogRestart: id=$id which=$whichService count=$watchdogCount max=$WATCHDOG_MAX_RETRIES lastRunDelta=${System.currentTimeMillis() - lastRunning}ms wasShort=$wasShort shouldRestart=$shouldRestart")
        if (wasShort) {
            Log.w(TAG, "watchdog: service kept dying too fast (looped death), giving up to avoid thrashing")
        }
        fireWatchdogNotification(whichService, watchdogCount, shouldRestart)

        if (!shouldRestart) {
            stop()
            return
        }
        notifyStateChanged(State.STARTING)

        try {
            val nm = KlipperApp.INSTANCE.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (klippyConnection != null) {
                try { KlipperApp.INSTANCE.unbindService(klippyConnection!!) } catch (_: Throwable) {}
                try { KlipperApp.INSTANCE.stopService(klippyIntent) } catch (_: Throwable) {}
                try { nm.cancel(BaseKlippyService.BASE_ID + slot) } catch (_: Throwable) {}
            }
            if (moonrakerConnection != null) {
                try { KlipperApp.INSTANCE.unbindService(moonrakerConnection!!) } catch (_: Throwable) {}
                try { KlipperApp.INSTANCE.stopService(moonrakerIntent) } catch (_: Throwable) {}
                try { nm.cancel(BaseMoonrakerService.BASE_ID + slot) } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
        klippyConnected = false
        moonrakerConnected = false
        klippyConnection = null
        moonrakerConnection = null
        klippyIntent = null
        moonrakerIntent = null

        val delayMs = WatchdogPolicy.restartDelayMs(watchdogCount)
        val instId = id
        val capturedSlot = slot
        val capturedKlippyClass = try { Class.forName("ru.ytkab0bp.beamklipper.service.KlippyService_$capturedSlot") } catch (_: Throwable) { null }
        val capturedMoonrakerClass = try { Class.forName("ru.ytkab0bp.beamklipper.service.MoonrakerService_$capturedSlot") } catch (_: Throwable) { null }
        mainHandler.postDelayed({
            if (isUserStop || stopRequested || stopSentinel.get()) {
                Log.i(TAG, "watchdog: canceling delayed restart for id=$instId because stop requested")
                return@postDelayed
            }
            Log.i(TAG, "watchdog: restarting services for id=$instId (attempt $watchdogCount)")
            try {
                if (capturedKlippyClass != null) {
                    val kIntent = Intent(KlipperApp.INSTANCE, capturedKlippyClass)
                    klippyIntent = kIntent
                    kIntent.putExtra(BasePythonService.KEY_INSTANCE, instId)
                    val kConn = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, service: IBinder) {
                            if (isUserStop || stopRequested || stopSentinel.get() || state == State.STOPPING || state == State.IDLE) {
                                Log.w(TAG, "watchdog: klippy onServiceConnected after stop; tearing down for id=$instId")
                                try { KlipperApp.INSTANCE.unbindService(this) } catch (_: Throwable) {}
                                try { KlipperApp.INSTANCE.stopService(kIntent) } catch (_: Throwable) {}
                                return
                            }
                            Log.i("beam_service", "watchdog klippy reconnected for id=$instId")
                            klippyConnected = true
                            watchdogCount = WatchdogPolicy.attemptsAfterReconnect(watchdogCount)
                            if (moonrakerConnected) {
                                notifyStateChanged(State.RUNNING)
                            }
                        }
                        override fun onServiceDisconnected(name: ComponentName) { onKlippyUnbound(watchdogRestart = !stopRequested && !stopSentinel.get()) }
                        override fun onBindingDied(name: ComponentName) { onKlippyUnbound(watchdogRestart = !stopRequested && !stopSentinel.get()) }
                    }
                    KlipperApp.INSTANCE.bindService(kIntent, kConn, Context.BIND_AUTO_CREATE)
                    klippyConnection = kConn
                }
                if (capturedMoonrakerClass != null) {
                    val mIntent = Intent(KlipperApp.INSTANCE, capturedMoonrakerClass)
                    moonrakerIntent = mIntent
                    mIntent.putExtra(BasePythonService.KEY_INSTANCE, instId)
                    val mConn = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, service: IBinder) {
                            if (isUserStop || stopRequested || stopSentinel.get() || state == State.STOPPING || state == State.IDLE) {
                                Log.w(TAG, "watchdog: moonraker onServiceConnected after stop; tearing down for id=$instId")
                                try { KlipperApp.INSTANCE.unbindService(this) } catch (_: Throwable) {}
                                try { KlipperApp.INSTANCE.stopService(mIntent) } catch (_: Throwable) {}
                                return
                            }
                            Log.i("beam_service", "watchdog moonraker reconnected for id=$instId")
                            moonrakerConnected = true
                            if (klippyConnected) {
                                notifyStateChanged(State.RUNNING)
                            }
                        }
                        override fun onServiceDisconnected(name: ComponentName) { onMoonrakerUnbound(watchdogRestart = !stopRequested && !stopSentinel.get()) }
                        override fun onBindingDied(name: ComponentName) { onMoonrakerUnbound(watchdogRestart = !stopRequested && !stopSentinel.get()) }
                    }
                    KlipperApp.INSTANCE.bindService(mIntent, mConn, Context.BIND_AUTO_CREATE)
                    moonrakerConnection = mConn
                }
            } catch (t: Throwable) {
                Log.e(TAG, "watchdog restart failed for id=$instId", t)
            }
        }, delayMs)
    }

    private fun onKlippyUnbound(watchdogRestart: Boolean = false) {
        klippyConnection = null
        klippyConnected = false
        if (!moonrakerConnected) {
            if (watchdogRestart) tryWatchdogRestart("Klippy")
            if (!watchdogRestart || watchdogCount > WATCHDOG_MAX_RETRIES) notifyStateChanged(State.IDLE)
        } else if (watchdogRestart) {
            tryWatchdogRestart("Klippy")
        }
    }

    private fun onMoonrakerUnbound(watchdogRestart: Boolean = false) {
        moonrakerConnection = null
        moonrakerConnected = false
        if (!klippyConnected) {
            if (watchdogRestart) tryWatchdogRestart("Moonraker")
            if (!watchdogRestart || watchdogCount > WATCHDOG_MAX_RETRIES) notifyStateChanged(State.IDLE)
        } else if (watchdogRestart) {
            tryWatchdogRestart("Moonraker")
        }
    }

    private fun notifyStateChanged(state: State) {
        Log.d(TAG, "notifyStateChanged: id=$id name=$name state=$state")
        if (state == State.RUNNING) lastRunning = System.currentTimeMillis()
        this.state = state
        KlipperApp.EVENT_BUS.fireEvent(InstanceStateChangedEvent(requireNotNull(id), state))

        if (state == State.IDLE) {
            val ownId = id
            if (ownId != null) {
                slotById.remove(ownId)
                instanceBySlotId.remove(ownId)
            }
            @Suppress("DEPRECATION")
            slots.remove(this)
            @Suppress("DEPRECATION")
            for (entry in slots.keys.toList()) {
                if (entry.id == ownId) {
                    slots.remove(entry)
                }
            }
            try {
                val nm = KlipperApp.INSTANCE.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(WATCHDOG_BASE_ID + slot)
            } catch (_: Throwable) {}
            mainHandler.post { stopServerServicesIfNoSlots() }
        } else if (state == State.RUNNING) {
            mainHandler.post {
                if (webServerConnection == null) {
                    KlipperApp.EVENT_BUS.fireEvent(WebStateChangedEvent(ru.ytkab0bp.beamklipper.KlipperInstance.State.STARTING))
                    KlipperApp.INSTANCE.bindService(Intent(KlipperApp.INSTANCE, WebService::class.java), object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, service: IBinder) {
                            KlipperApp.EVENT_BUS.fireEvent(WebStateChangedEvent(ru.ytkab0bp.beamklipper.KlipperInstance.State.RUNNING))
                        }

                        override fun onServiceDisconnected(name: ComponentName) {}
                    }.also { webServerConnection = it }, Context.BIND_AUTO_CREATE)
                }

                if (Prefs.isCameraEnabled) bindCameraServer()

                // OctoEverywhere is one companion process for the whole app (like the
                // camera server), bound to whichever instance happened to reach RUNNING
                // first. It links to that one instance's Moonraker; a second concurrent
                // instance doesn't get its own companion.
                if (Prefs.isOctoEverywhereEnabled && octoEverywhereConnection == null) {
                    val iid = id
                    if (iid != null) {
                        KlipperApp.INSTANCE.bindService(
                            Intent(KlipperApp.INSTANCE, OctoEverywhereService::class.java).putExtra(BasePythonService.KEY_INSTANCE, iid),
                            object : ServiceConnection {
                                override fun onServiceConnected(name: ComponentName, service: IBinder) {}
                                override fun onServiceDisconnected(name: ComponentName) {}
                            }.also { octoEverywhereConnection = it },
                            Context.BIND_AUTO_CREATE
                        )
                    }
                }

                // Same one-companion-for-the-app story as OctoEverywhere above.
                if (Prefs.isObicoEnabled && obicoConnection == null) {
                    val iid = id
                    if (iid != null) {
                        KlipperApp.INSTANCE.bindService(
                            Intent(KlipperApp.INSTANCE, ObicoService::class.java).putExtra(BasePythonService.KEY_INSTANCE, iid),
                            object : ServiceConnection {
                                override fun onServiceConnected(name: ComponentName, service: IBinder) {}
                                override fun onServiceDisconnected(name: ComponentName) {}
                            }.also { obicoConnection = it },
                            Context.BIND_AUTO_CREATE
                        )
                    }
                }
            }
        }
    }

    enum class State {
        IDLE, STARTING, RUNNING, STOPPING
    }

    companion object {
        @JvmField
        val SLOTS_COUNT: Int = BuildConfig.SLOTS_COUNT
        private const val TAG = "beam_instance"

        private const val WATCHDOG_BASE_ID = 9000
        private const val WATCHDOG_REQ_ID = 9000
        private const val WATCHDOG_MAX_RETRIES = WatchdogPolicy.MAX_RETRIES

        private val mainHandler = Handler(Looper.getMainLooper())
        @Deprecated("Use slotById instead (object identity bug). Kept temporarily for slotById migration helper access.")
        private val slots = HashMap<KlipperInstance, Int>()
        private val slotById = HashMap<String, Int>()
        private val instanceBySlotId = HashMap<String, KlipperInstance>()
        private var webServerConnection: ServiceConnection? = null
        private var cameraServerConnection: ServiceConnection? = null
        private var octoEverywhereConnection: ServiceConnection? = null
        private var obicoConnection: ServiceConnection? = null
        private var instances: List<KlipperInstance> = emptyList()
        private val instanceMap = object : HashMap<String, KlipperInstance>() {
            override fun get(key: String): KlipperInstance? {
                var inst = super.get(key)
                if (inst == null) {
                    for (i in instances) {
                        if (key == i.id) {
                            put(key, i)
                            inst = i
                            break
                        }
                    }
                }
                return inst
            }
        }

        @JvmStatic
        fun onInstancesLoadedFromDB(loaded: List<KlipperInstance>) {
            Log.i("beam_instance", "onInstancesLoadedFromDB: count=${loaded.size}")
            for (inst in loaded) {
                val canonical = getInstance(inst.id ?: continue) ?: continue
                if (canonical !== inst) {
                    var changed = false
                    if (canonical.name != inst.name) { canonical.name = inst.name; changed = true }
                    if (canonical.icon != inst.icon) { canonical.icon = inst.icon; changed = true }
                    if (canonical.autostart != inst.autostart) { canonical.autostart = inst.autostart; changed = true }
                    if (changed) {
                        Log.i("beam_instance", "  refreshed canonical fields for id=${canonical.id}: name=${canonical.name} autostart=${canonical.autostart}")
                    }
                }
            }

            val loadedIds = loaded.mapNotNull { it.id }.toHashSet()
            for ((slotInstId, slotValue) in slotById.entries.toList()) {
                if (slotInstId !in loadedIds) {
                    val orphan = instanceBySlotId.remove(slotInstId)
                    Log.i(TAG, "instance $slotInstId (name=${orphan?.name}) no longer in DB, stopping (slot=$slotValue)")
                    slotById.remove(slotInstId)
                    try { orphan?.stop() } catch (_: Throwable) {}
                }
            }
            mainHandler.post { stopServerServicesIfNoSlots() }

            instances = loaded.map { dbInst -> getInstance(dbInst.id ?: return@map null) }.filterNotNull()
            instanceMap.clear()
            for (canonical in instances) {
                canonical.id?.let { instanceMap[it] = canonical }
            }
            KlipperApp.EVENT_BUS.fireEvent(InstancesRefreshedEvent())

            for (inst in instances) {
                Log.i("beam_instance", "instance id=${inst.id} name=${inst.name} autostart=${inst.autostart} state=${inst.getState()}")
                if (inst.autostart && inst.getState() == State.IDLE) {
                    Log.i("beam_instance", "  -> calling start()")
                    inst.start()
                }
            }
        }

        @JvmStatic
        fun getInstance(id: String): KlipperInstance? {
            var inst = instanceMap[id]
            if (inst == null) {
                for (i in instances) {
                    if (id == i.id) {
                        instanceMap[id] = i
                        inst = i
                        break
                    }
                }
            }
            if (inst == null) {
                val db = try { KlipperApp.getDatabaseOrNull() } catch (_: Throwable) { null }
                if (db != null) {
                    val all = try { db.getInstances() } catch (_: Throwable) { return null }
                    for (i in all) {
                        if (id == i.id) {
                            inst = i
                            instanceMap[id] = i
                            if (instances.isEmpty()) {
                                instances = all
                            }
                            break
                        }
                    }
                }
                if (inst == null) {
                    inst = KlipperInstance().apply { this.id = id }
                    instanceMap[id] = inst
                }
            }
            return inst
        }

        @JvmStatic
        fun getInstances(): List<KlipperInstance> {
            if (instances.isEmpty()) {
                val db = try { KlipperApp.getDatabaseOrNull() } catch (_: Throwable) { null } ?: return emptyList()
                val loaded = try { db.getInstances() } catch (_: Throwable) { emptyList() }
                if (loaded.isNotEmpty()) {
                    instances = loaded.map { dbInst -> getInstance(dbInst.id ?: return@map null) }.filterNotNull()
                    for (canonical in instances) {
                        canonical.id?.let { instanceMap[it] = canonical }
                    }
                }
            }
            return instances
        }

        @JvmStatic
        fun hasFreeSlots(): Boolean = slotById.size < SLOTS_COUNT

        @JvmStatic
        fun isWebServerRunning(): Boolean = webServerConnection != null

        private fun hasAnyRunningInstance(): Boolean {
            for ((id, _) in slotById) {
                val inst = instanceBySlotId[id] ?: continue
                val s = inst.getState()
                if (s == State.RUNNING || s == State.STARTING || s == State.STOPPING) {
                    return true
                }
            }
            return false
        }

        private fun stopServerServicesIfNoSlots() {
            Log.d(TAG, "stopServerServicesIfNoSlots: slotById.size=${slotById.size}, hasRunning=${hasAnyRunningInstance()}, webConn=${webServerConnection != null}")
            if (hasAnyRunningInstance()) {
                Log.d(TAG, "stopServerServicesIfNoSlots: returning early due to running instances")
                return
            }
            Log.d(TAG, "stopServerServicesIfNoSlots: proceeding to stop services")
            slotById.clear()
            instanceBySlotId.clear()
            if (webServerConnection != null) {
                KlipperApp.EVENT_BUS.fireEvent(WebStateChangedEvent(ru.ytkab0bp.beamklipper.KlipperInstance.State.STOPPING))
                try { KlipperApp.INSTANCE.unbindService(webServerConnection!!) } catch (_: Throwable) {}
                try { KlipperApp.INSTANCE.stopService(Intent(KlipperApp.INSTANCE, WebService::class.java)) } catch (_: Throwable) {}
                KlipperApp.EVENT_BUS.fireEvent(WebStateChangedEvent(ru.ytkab0bp.beamklipper.KlipperInstance.State.IDLE))
                webServerConnection = null
            }
            // The camera server is intentionally left running: it follows the
            // "Enable camera server" switch, not whether a printer is running,
            // so the preview tab keeps working with every printer stopped.
            if (octoEverywhereConnection != null) {
                try { KlipperApp.INSTANCE.unbindService(octoEverywhereConnection!!) } catch (_: Throwable) {}
                try { KlipperApp.INSTANCE.stopService(Intent(KlipperApp.INSTANCE, OctoEverywhereService::class.java)) } catch (_: Throwable) {}
                octoEverywhereConnection = null
            }
            if (obicoConnection != null) {
                try { KlipperApp.INSTANCE.unbindService(obicoConnection!!) } catch (_: Throwable) {}
                try { KlipperApp.INSTANCE.stopService(Intent(KlipperApp.INSTANCE, ObicoService::class.java)) } catch (_: Throwable) {}
                obicoConnection = null
            }
        }

        // Must run on the main thread (all callers post to mainHandler).
        private fun bindCameraServer() {
            if (cameraServerConnection != null) return
            KlipperApp.INSTANCE.bindService(Intent(KlipperApp.INSTANCE, CameraService::class.java), object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {}
                override fun onServiceDisconnected(name: ComponentName) {}
            }.also { cameraServerConnection = it }, Context.BIND_AUTO_CREATE)
        }

        // Starts the camera server if it's enabled, with or without a running
        // printer. Called from the foreground UI (a camera foreground service
        // can't be started from the background on recent Android versions).
        @JvmStatic
        fun ensureCameraServer() {
            mainHandler.post { if (Prefs.isCameraEnabled) bindCameraServer() }
        }

        @JvmStatic
        fun onCameraConfigChanged(enable: Boolean) {
            mainHandler.post {
                if (enable) {
                    bindCameraServer()
                } else if (cameraServerConnection != null && !enable) {
                    try { KlipperApp.INSTANCE.unbindService(cameraServerConnection!!) } catch (_: Throwable) {}
                    try { KlipperApp.INSTANCE.stopService(Intent(KlipperApp.INSTANCE, CameraService::class.java)) } catch (_: Throwable) {}
                    cameraServerConnection = null
                }
            }
        }

        @JvmStatic
        fun onCameraSourceChanged() {
            mainHandler.post {
                // CameraService lives in its own :camera process, so it never observes a
                // SharedPreferences write made from here — only a fresh process start reads
                // Prefs.cameraId truthfully. Bounce it so the new pick takes effect.
                if (cameraServerConnection != null) {
                    try { KlipperApp.INSTANCE.unbindService(cameraServerConnection!!) } catch (_: Throwable) {}
                    try { KlipperApp.INSTANCE.stopService(Intent(KlipperApp.INSTANCE, CameraService::class.java)) } catch (_: Throwable) {}
                    cameraServerConnection = null
                    if (Prefs.isCameraEnabled) bindCameraServer()
                }
            }
        }

        @JvmStatic
        fun onOctoEverywhereConfigChanged(enable: Boolean) {
            mainHandler.post {
                if (octoEverywhereConnection == null && enable) {
                    val inst = instanceBySlotId.values.firstOrNull { it.getState() == State.RUNNING } ?: return@post
                    val iid = inst.id ?: return@post
                    KlipperApp.INSTANCE.bindService(
                        Intent(KlipperApp.INSTANCE, OctoEverywhereService::class.java).putExtra(BasePythonService.KEY_INSTANCE, iid),
                        object : ServiceConnection {
                            override fun onServiceConnected(name: ComponentName, service: IBinder) {}
                            override fun onServiceDisconnected(name: ComponentName) {}
                        }.also { octoEverywhereConnection = it },
                        Context.BIND_AUTO_CREATE
                    )
                } else if (octoEverywhereConnection != null && !enable) {
                    try { KlipperApp.INSTANCE.unbindService(octoEverywhereConnection!!) } catch (_: Throwable) {}
                    try { KlipperApp.INSTANCE.stopService(Intent(KlipperApp.INSTANCE, OctoEverywhereService::class.java)) } catch (_: Throwable) {}
                    octoEverywhereConnection = null
                }
            }
        }

        @JvmStatic
        fun onObicoConfigChanged(enable: Boolean) {
            mainHandler.post {
                if (obicoConnection == null && enable) {
                    val inst = instanceBySlotId.values.firstOrNull { it.getState() == State.RUNNING } ?: return@post
                    val iid = inst.id ?: return@post
                    KlipperApp.INSTANCE.bindService(
                        Intent(KlipperApp.INSTANCE, ObicoService::class.java).putExtra(BasePythonService.KEY_INSTANCE, iid),
                        object : ServiceConnection {
                            override fun onServiceConnected(name: ComponentName, service: IBinder) {}
                            override fun onServiceDisconnected(name: ComponentName) {}
                        }.also { obicoConnection = it },
                        Context.BIND_AUTO_CREATE
                    )
                } else if (obicoConnection != null && !enable) {
                    try { KlipperApp.INSTANCE.unbindService(obicoConnection!!) } catch (_: Throwable) {}
                    try { KlipperApp.INSTANCE.stopService(Intent(KlipperApp.INSTANCE, ObicoService::class.java)) } catch (_: Throwable) {}
                    obicoConnection = null
                }
            }
        }

        @JvmStatic
        fun onObicoRelink() {
            mainHandler.post {
                // ObicoService lives in its own ":obico" process, so it never
                // observes a SharedPreferences write made from here — only a
                // fresh process start reads the new server URL / auth_token
                // truthfully. Bounce it so a just-completed link (or a
                // changed server) takes effect immediately, same story as
                // onCameraSourceChanged.
                if (obicoConnection != null) {
                    try { KlipperApp.INSTANCE.unbindService(obicoConnection!!) } catch (_: Throwable) {}
                    try { KlipperApp.INSTANCE.stopService(Intent(KlipperApp.INSTANCE, ObicoService::class.java)) } catch (_: Throwable) {}
                    obicoConnection = null
                    if (Prefs.isObicoEnabled) {
                        val inst = instanceBySlotId.values.firstOrNull { it.getState() == State.RUNNING }
                        val iid = inst?.id
                        if (iid != null) {
                            KlipperApp.INSTANCE.bindService(
                                Intent(KlipperApp.INSTANCE, ObicoService::class.java).putExtra(BasePythonService.KEY_INSTANCE, iid),
                                object : ServiceConnection {
                                    override fun onServiceConnected(name: ComponentName, service: IBinder) {}
                                    override fun onServiceDisconnected(name: ComponentName) {}
                                }.also { obicoConnection = it },
                                Context.BIND_AUTO_CREATE
                            )
                        }
                    }
                }
            }
        }

        @JvmStatic
        fun resetSlotsForFreshStart() {
            Log.i(TAG, "resetSlotsForFreshStart: clearing slotById (was size=${slotById.size}), webServerConnection=${webServerConnection != null}, cameraServerConnection=${cameraServerConnection != null}")
            slotById.clear()
            instanceBySlotId.clear()
            slots.clear()
            webServerConnection = null
            octoEverywhereConnection = null
            obicoConnection = null
        }

        @JvmStatic
        fun getSlotInstancesNames(): List<String> {
            return instanceBySlotId.values.mapNotNull { it.name }
        }
    }
}

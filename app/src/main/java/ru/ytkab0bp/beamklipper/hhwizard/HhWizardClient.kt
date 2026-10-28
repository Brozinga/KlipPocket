package ru.ytkab0bp.beamklipper.hhwizard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The app side of [HhWizardService]. [call] blocks until the answer arrives, so call it off the main thread. The service is bound
 * on the first call and let go after a while without calls, which ends its process.
 */
object HhWizardClient {
    private const val IDLE_MS = 3 * 60_000L
    private const val BIND_TIMEOUT_MS = 20_000L

    private val ids = AtomicInteger()
    private val waiting = ConcurrentHashMap<Int, Pending>()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var service: Messenger? = null
    @Volatile private var bound = false
    private var bindLatch = CountDownLatch(1)
    private lateinit var appContext: Context

    private class Pending { val done = CountDownLatch(1); @Volatile var result: String? = null }

    private val replies = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != HhWizardService.MSG_RESULT) return
            waiting.remove(msg.arg1)?.let { it.result = msg.data.getString(HhWizardService.KEY_RESULT); it.done.countDown() }
        }
    })

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) { service = Messenger(binder); bindLatch.countDown() }
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
            waiting.values.forEach { it.done.countDown() }
        }
    }

    private val release = Runnable { release() }

    @Synchronized private fun ensureBound(context: Context): Messenger? {
        appContext = context.applicationContext
        if (service == null) {
            if (!bound) {
                bindLatch = CountDownLatch(1)
                bound = appContext.bindService(Intent(appContext, HhWizardService::class.java), connection, Context.BIND_AUTO_CREATE)
                if (!bound) return null
            }
        }
        return service
    }

    @Synchronized private fun release() {
        if (bound && waiting.isEmpty()) {
            try { appContext.unbindService(connection) } catch (_: Exception) {}
            bound = false
            service = null
        }
    }

    /** Sends [request] (from [HhWizardRules.request]) and returns the wizard's JSON answer, or a failure object. */
    fun call(context: Context, request: String, timeoutMs: Long = 180_000L): JSONObject {
        ensureBound(context)
        if (!bindLatch.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS) || service == null) return HhWizardRules.failure("service")
        val id = ids.incrementAndGet()
        val pending = Pending().also { waiting[id] = it }
        try {
            val msg = Message.obtain(null, HhWizardService.MSG_CALL, id, 0)
            msg.replyTo = replies
            msg.data = Bundle().apply { putString(HhWizardService.KEY_REQUEST, request) }
            service?.send(msg) ?: return HhWizardRules.failure("service")
            if (!pending.done.await(timeoutMs, TimeUnit.MILLISECONDS)) return HhWizardRules.failure("timeout")
            val text = pending.result ?: return HhWizardRules.failure("service")
            return try { JSONObject(text) } catch (e: Exception) { HhWizardRules.failure("answer", text.take(200)) }
        } catch (e: Exception) {
            return HhWizardRules.failure("service", e.message ?: "")
        } finally {
            waiting.remove(id)
            main.removeCallbacks(release)
            main.postDelayed(release, IDLE_MS)
        }
    }
}

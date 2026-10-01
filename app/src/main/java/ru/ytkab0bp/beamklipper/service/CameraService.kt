package ru.ytkab0bp.beamklipper.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.util.Range
import android.view.Surface
import ru.ytkab0bp.beamklipper.BuildConfig
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.utils.CameraFocus
import ru.ytkab0bp.beamklipper.utils.CameraZoom
import ru.ytkab0bp.beamklipper.utils.CameraHttp
import ru.ytkab0bp.beamklipper.utils.CameraRules
import ru.ytkab0bp.beamklipper.utils.JpegQualityController
import ru.ytkab0bp.beamklipper.utils.Prefs
import ru.ytkab0bp.beamklipper.utils.ViewUtils
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

@SuppressLint("MissingPermission")
class CameraService : Service() {
    companion object {
        const val ACTION_TOGGLE_FLASHLIGHT = "${BuildConfig.APPLICATION_ID}.action.TOGGLE_FLASHLIGHT"
        const val ACTION_TOGGLE_FOCUS = "${BuildConfig.APPLICATION_ID}.action.TOGGLE_FOCUS"
        const val KEY_FLASHLIGHT = "flashlight"
        const val ACTION_TAP_FOCUS = "${BuildConfig.APPLICATION_ID}.action.TAP_FOCUS"
        const val ACTION_RESET_FOCUS = "${BuildConfig.APPLICATION_ID}.action.RESET_FOCUS"
        const val KEY_TAP_X = "tap_x"
        const val KEY_TAP_Y = "tap_y"
        const val KEY_AUTOFOCUS = "autofocus"
        const val KEY_FOCUS = "focus"
        private const val TAG = "beam_camera"
        private const val DESCRIPTOR = "ru.ytkab0bp.beamklipper.ICameraService"
        private const val PORT = 8889
        private const val ID = 400000
        private const val WRITE_TIMEOUT_MS = 4000L
        private val IO_POOL = Executors.newSingleThreadExecutor()
        // Only used to force-close a socket whose write() has been blocking
        // past WRITE_TIMEOUT_MS (a client on a bad/dead connection) — plain
        // java.net.Socket has no write timeout of its own, and without this a
        // single stalled viewer parks its handler thread forever.
        private val WRITE_WATCHDOG = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "beam_camera_watchdog").apply { isDaemon = true }
        }
        private val handlerThreads = CopyOnWriteArrayList<CameraHandlerThread>()
    }

    private var notificationManager: NotificationManager? = null
    private var serverThread: ServerThread? = null
    private lateinit var cameraManager: CameraManager
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var captureSession: CameraCaptureSession? = null
    private var captureRequestBuilder: CaptureRequest.Builder? = null
    private var openCameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var activeCameraId: String? = null
    private var availabilityCallback: CameraManager.AvailabilityCallback? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val stoppedByUser = java.util.concurrent.atomic.AtomicBoolean(false)

    // Shrinks new frames automatically when a viewer's network can't keep
    // up (e.g. a congested 2.4GHz network) instead of leaving the frame size
    // fixed and letting the per-viewer backpressure in deliverFrame() just
    // drop whichever frames miss the send-timing window. Read from the
    // IO_POOL encode thread, written from client handler threads.
    private val jpegQualityController = JpegQualityController()
    private val jpegQuality: Int get() = jpegQualityController.quality

    private fun onFrameWriteTiming(elapsedMs: Long) {
        val before = jpegQualityController.quality
        val after = jpegQualityController.onWriteTiming(elapsedMs)
        if (after < before) {
            Log.i(TAG, "Sustained slow client writes, lowering JPEG quality to $after")
        } else if (after > before) {
            Log.i(TAG, "Client writes fast again, raising JPEG quality to $after")
        }
    }

    // A USB UVC webcam shows up through Camera2 as a regular camera ID with
    // LENS_FACING_EXTERNAL once the OS/HAL enumerates it (standard since API 28
    // on devices whose camera HAL implements the external-camera provider).
    // When the user hasn't pinned a specific camera (Prefs.cameraId == null),
    // we prefer that external webcam over the built-in camera for monitoring.
    private fun isExternal(id: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return try {
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_EXTERNAL
        } catch (_: CameraAccessException) {
            false
        }
    }

    private fun resolveCameraId(): String? {
        val ids = try { cameraManager.cameraIdList } catch (_: CameraAccessException) { return null }
        return CameraRules.resolveId(ids.toList(), Prefs.cameraId) { isExternal(it) }
    }

    private val serviceBinder = object : Binder() {
        override fun getInterfaceDescriptor(): String = DESCRIPTOR
    }

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_TOGGLE_FLASHLIGHT -> {
                    val flashlight = intent.getBooleanExtra(KEY_FLASHLIGHT, false)
                    Prefs.isFlashlightEnabled = flashlight
                    try {
                        captureRequestBuilder?.set(CaptureRequest.FLASH_MODE,
                            if (flashlight) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF)
                        captureSession?.setRepeatingRequest(captureRequestBuilder!!.build(), null, null)
                    } catch (e: CameraAccessException) {
                        Log.e(TAG, "Failed to update camera settings", e)
                    }
                }
                ACTION_TAP_FOCUS -> focusAt(
                    intent.getFloatExtra(KEY_TAP_X, 0.5f),
                    intent.getFloatExtra(KEY_TAP_Y, 0.5f)
                )
                ACTION_RESET_FOCUS -> resetFocus()
                ACTION_TOGGLE_FOCUS -> {
                    Prefs.clearSavedFocus()
                    val autofocus = intent.getBooleanExtra(KEY_AUTOFOCUS, false)
                    Prefs.isAutofocusEnabled = autofocus
                    val focus = intent.getFloatExtra(KEY_FOCUS, 0f)
                    Prefs.focusDistance = focus
                    try {
                        captureRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE,
                            if (autofocus) CaptureRequest.CONTROL_AF_MODE_AUTO else CaptureRequest.CONTROL_AF_MODE_OFF)
                        captureRequestBuilder?.set(CaptureRequest.LENS_FOCUS_DISTANCE, focus)
                        captureSession?.setRepeatingRequest(captureRequestBuilder!!.build(), null, null)
                    } catch (e: CameraAccessException) {
                        Log.e(TAG, "Failed to update camera settings", e)
                    }
                }
            }
        }
    }

    // One-shot AF at a point of the served frame: AUTO mode + region, then
    // CANCEL -> START -> IDLE so the lens focuses once and holds. Silently
    // ignored on cameras that can't do it (see CameraFocus.isSupported).
    private fun focusAt(nx: Float, ny: Float) {
        val id = activeCameraId ?: return
        val builder = captureRequestBuilder ?: return
        val session = captureSession ?: return
        try {
            val chars = cameraManager.getCameraCharacteristics(id)
            if (!CameraFocus.isSupported(chars)) return
            val zoom = CameraZoom.clamp(Prefs.cameraZoom, CameraZoom.options(CameraZoom.maxZoom(chars)))
            val region = CameraFocus.region(chars, zoom, Prefs.cameraRotation, nx, ny) ?: return
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(region))
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL)
            session.capture(builder.build(), null, null)
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
            session.capture(builder.build(), null, null)
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
            pendingFocusTap = floatArrayOf(nx, ny)
            focusTapAt = SystemClock.elapsedRealtime()
            sawFocusScan = false
            session.setRepeatingRequest(builder.build(), focusSaveCallback, cameraHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to focus at $nx,$ny", e)
        }
    }

    // Tap-to-focus that is waiting for the lens to settle so it can be saved.
    private var pendingFocusTap: FloatArray? = null
    private var focusTapAt = 0L
    private var sawFocusScan = false

    private val focusSaveCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            val tap = pendingFocusTap ?: return
            val state = result.get(CaptureResult.CONTROL_AF_STATE) ?: return
            if (state == CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN) {
                sawFocusScan = true
                return
            }
            if (state != CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED &&
                state != CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) return
            // A lock reported before any scan is the previous one, unless the
            // HAL simply focused instantly.
            if (!sawFocusScan && SystemClock.elapsedRealtime() - focusTapAt < 800) return
            pendingFocusTap = null
            if (state == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) return
            val id = activeCameraId ?: return
            Prefs.savedFocusCamera = id
            Prefs.savedFocusX = tap[0]
            Prefs.savedFocusY = tap[1]
            Prefs.savedFocusDistance = result.get(CaptureResult.LENS_FOCUS_DISTANCE) ?: -1f
            Log.i(TAG, "Saved focus for camera $id: distance=${Prefs.savedFocusDistance}")
        }
    }

    // Forgets the saved focus and goes back to the configured focus mode.
    private fun resetFocus() {
        Prefs.clearSavedFocus()
        pendingFocusTap = null
        val builder = captureRequestBuilder ?: return
        val session = captureSession ?: return
        try {
            builder.set(CaptureRequest.CONTROL_AF_MODE,
                if (Prefs.isAutofocusEnabled) CaptureRequest.CONTROL_AF_MODE_AUTO else CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, Prefs.focusDistance)
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, null)
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL)
            session.capture(builder.build(), null, null)
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
            session.setRepeatingRequest(builder.build(), null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset focus", e)
        }
    }

    // Puts the focus saved by tap-to-focus back when a session starts.
    // Returns the tap point to re-run AF at when the lens position wasn't
    // saved (some HALs never report it); null otherwise.
    private fun applySavedFocus(builder: CaptureRequest.Builder, chars: CameraCharacteristics, id: String): FloatArray? {
        val savedId = Prefs.savedFocusCamera ?: return null
        if (savedId != id) {
            // A different camera than the one that was focused: start over.
            Prefs.clearSavedFocus()
            return null
        }
        val minDistance = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        val distance = Prefs.savedFocusDistance
        if (distance >= 0f && minDistance > 0f) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, distance.coerceAtMost(minDistance))
            Log.i(TAG, "Restored saved focus for camera $id: distance=$distance")
            return null
        }
        val x = Prefs.savedFocusX
        val y = Prefs.savedFocusY
        return if (x in 0f..1f && y in 0f..1f && CameraFocus.isSupported(chars)) floatArrayOf(x, y) else null
    }

    override fun onBind(intent: Intent?): IBinder? {
        acquireLocks()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val not = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, KlipperApp.SERVICES_CHANNEL)
        else
            Notification.Builder(this)
        not.setContentTitle(getString(R.string.CameraTitle))
            .setContentText(getString(R.string.CameraDescription))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
        notificationManager!!.notify(ID, not.build())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(ID, not.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(ID, not.build())
        }
        return serviceBinder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_REDELIVER_INTENT

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (stoppedByUser.get()) return
    }

    private fun acquireLocks() {
        try {
            if (wakeLock?.isHeld != true) {
                val powerManager = getSystemService(POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BeamKlipper::CameraWakeLock").apply {
                    setReferenceCounted(false)
                    acquire(10 * 24 * 60 * 60 * 1000L)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to acquire wakelock", t)
        }
        try {
            if (wifiLock?.isHeld != true) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wifiLock = wm.createWifiLock(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL,
                    "BeamKlipper::CameraWiFiLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to acquire wifilock", t)
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Throwable) {}
        wakeLock = null
        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Throwable) {}
        wifiLock = null
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag", "WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        acquireLocks()

        cameraThread = HandlerThread("camera").also { it.start() }
        cameraHandler = Handler(cameraThread!!.looper)
        cameraHandler?.post { Process.setThreadPriority(-10) }

        serverThread = ServerThread().also { it.start() }

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        availabilityCallback = object : CameraManager.AvailabilityCallback() {
            override fun onCameraAvailable(cameraId: String) {
                // Only auto-switch in "auto" mode (no camera pinned by the user):
                // when a USB webcam is plugged in, prefer it for monitoring.
                if (Prefs.cameraId != null) return
                if (cameraId == activeCameraId) return
                if (!isExternal(cameraId)) return
                cameraHandler?.post {
                    Log.i(TAG, "USB webcam $cameraId attached, switching to it")
                    openSelectedCamera()
                }
            }
        }
        try {
            cameraManager.registerAvailabilityCallback(availabilityCallback!!, cameraHandler)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to register camera availability callback", t)
        }
        cameraHandler?.post { openSelectedCamera() }

        val filter = IntentFilter(ACTION_TOGGLE_FLASHLIGHT).apply { addAction(ACTION_TOGGLE_FOCUS); addAction(ACTION_TAP_FOCUS); addAction(ACTION_RESET_FOCUS) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, KlipperApp.PERMISSION, ViewUtils.getUiHandler(), Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter, KlipperApp.PERMISSION, ViewUtils.getUiHandler())
        }
    }

    private fun rotateJpeg(data: ByteArray, degrees: Int): ByteArray {
        return try {
            val src = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return data
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
            val out = ByteArrayOutputStream()
            rotated.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
            if (rotated != src) rotated.recycle()
            src.recycle()
            out.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rotate frame", e)
            data
        }
    }

    private fun deliverFrame(data: ByteArray, size: Int, onRelease: () -> Unit) {
        // A viewer that hasn't finished writing the previous frame yet (slow
        // client, backgrounded tab, congested WiFi) is skipped instead of
        // queued: without this, every new frame just piles another Runnable
        // onto that viewer's Handler, and a viewer that can't keep up in
        // real time never catches up — the backlog (and the lag) only grows.
        // This caps every viewer to at most one frame in flight; oneShot
        // (snapshot) requests always get delivered since there's only ever one.
        val recipients = handlerThreads.filter { it.oneShot || it.framesInFlight.compareAndSet(false, true) }
        if (recipients.isEmpty()) {
            onRelease()
            return
        }
        val done = AtomicInteger()
        val total = recipients.size
        for (t in recipients) {
            t.handler.post {
                val watchdog = WRITE_WATCHDOG.schedule({
                    Log.w(TAG, "Client write stalled past ${WRITE_TIMEOUT_MS}ms, dropping it")
                    try { t.socket.close() } catch (_: Exception) {}
                }, WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                var failed = false
                val writeStart = System.nanoTime()
                try {
                    val out = t.out
                    if (!t.oneShot) {
                        out.write(CameraHttp.framePartHeader(size).toByteArray())
                    } else {
                        out.write(CameraHttp.snapshotHeaders(size).toByteArray())
                    }
                    out.write(data, 0, size)
                    if (!t.oneShot) {
                        out.write("\r\n\r\n".toByteArray())
                    }
                    out.flush()
                } catch (e: Exception) {
                    failed = true
                } finally {
                    watchdog.cancel(false)
                }
                // Any failure (not just an already-observed closed socket —
                // a broken pipe from a remote disconnect throws without ever
                // marking the local Socket as closed) must tear this client
                // down. Leaving it in handlerThreads otherwise leaks a dead
                // HandlerThread that keeps "receiving" (and re-failing on)
                // every future frame forever.
                if (t.oneShot || failed) {
                    t.quit()
                } else {
                    t.framesInFlight.set(false)
                    // Snapshot (oneShot) fetches aren't part of the
                    // continuous stream cadence — only feed live-stream
                    // write timing into the adaptive quality controller.
                    onFrameWriteTiming((System.nanoTime() - writeStart) / 1_000_000)
                }
                if (done.incrementAndGet() == total) {
                    onRelease()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun openSelectedCamera() {
        val id = resolveCameraId()
        if (id == null) {
            Log.e(TAG, "No camera available to open")
            return
        }
        if (id == activeCameraId && openCameraDevice != null) return
        closeActiveCamera()
        activeCameraId = id
        // Start each fresh session at the base quality rather than
        // carrying over whatever a previous, possibly-congested session
        // had throttled down to.
        jpegQualityController.reset()
        try {
            cameraManager.openCamera(id, CaptureStateCallback(), cameraHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to open camera $id", e)
            activeCameraId = null
        }
    }

    private fun closeActiveCamera() {
        try { captureSession?.close() } catch (_: Exception) {}
        captureSession = null
        captureRequestBuilder = null
        try { openCameraDevice?.close() } catch (_: Exception) {}
        openCameraDevice = null
        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null
    }

    private inner class CaptureStateCallback : CameraDevice.StateCallback() {
        private val bufferStack = java.util.Stack<ByteArray>()
        private var bufferSize = 0
        // At most one frame is encoded at a time. Encoding a full-size YUV frame
        // is slower than the sensor delivers them, so queueing every image on
        // IO_POOL left all of the reader's buffers held by pending tasks and the
        // next acquireLatestImage() threw "maxImages (4) has already been
        // acquired", killing the :camera process. Extra frames are just dropped.
        private val encoding = java.util.concurrent.atomic.AtomicBoolean(false)

        override fun onOpened(camera: CameraDevice) {
            // openCamera() is async: a rapid hot-plug (webcam attached then
            // immediately detached, or two openSelectedCamera() calls in a row)
            // can let this fire after activeCameraId has already moved on to a
            // different id. Close the now-unwanted device instead of adopting it.
            if (camera.id != activeCameraId) {
                try { camera.close() } catch (_: Exception) {}
                return
            }
            openCameraDevice = camera
            try {
                var width = Prefs.cameraWidth
                var height = Prefs.cameraHeight
                val chars = cameraManager.getCameraCharacteristics(camera.id)
                val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val sizes = map?.getOutputSizes(ImageFormat.YUV_420_888) ?: emptyArray()
                if (sizes.isNotEmpty()) {
                    val best = sizes.minByOrNull {
                        val dw = it.width - width
                        val dh = it.height - height
                        dw * dw + dh * dh
                    }
                    if (best != null) {
                        width = best.width
                        height = best.height
                    }
                }
                val targets = ArrayList<Surface>()
                val reader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 4)
                imageReader = reader
                reader.setOnImageAvailableListener({ r ->
                    val img = try {
                        r.acquireLatestImage()
                    } catch (e: IllegalStateException) {
                        Log.w(TAG, "Image buffers exhausted, dropping frame", e)
                        null
                    } ?: return@setOnImageAvailableListener
                    if (handlerThreads.isEmpty() || !encoding.compareAndSet(false, true)) {
                        img.close()
                        return@setOnImageAvailableListener
                    }
                    try {
                        IO_POOL.submit {
                          try {
                        val yBuffer = img.planes[0].buffer
                        val uBuffer = img.planes[1].buffer
                        val vBuffer = img.planes[2].buffer

                        val ySize = yBuffer.remaining()
                        val uSize = uBuffer.remaining()
                        val vSize = vBuffer.remaining()

                        val bufSize = ySize + uSize + vSize
                        if (bufferSize < bufSize) {
                            bufferStack.clear()
                            bufferSize = bufSize
                        }
                        val buffer = if (bufferStack.isEmpty()) ByteArray(bufferSize) else bufferStack.pop()

                        yBuffer.get(buffer, 0, ySize)
                        vBuffer.get(buffer, ySize, vSize)
                        uBuffer.get(buffer, ySize + vSize, uSize)

                        val yuvImage = YuvImage(buffer, ImageFormat.NV21, img.width, img.height, null)
                        val conv = ByteArrayOutputStream()
                        // Base quality 75 rather than 85: this feed can be
                        // relayed through OctoEverywhere's cloud connection,
                        // not just served over LAN — see the
                        // cameraWidth/cameraHeight comment in Prefs.kt for
                        // the measured bandwidth reasoning. jpegQuality can
                        // drop further (down to MIN_JPEG_QUALITY) on its own
                        // when a viewer's network is struggling — see
                        // onFrameWriteTiming().
                        yuvImage.compressToJpeg(Rect(0, 0, img.width, img.height), jpegQuality, conv)
                        bufferStack.push(buffer)

                        // Rotation is the uncommon case (mounting-dependent),
                        // so it's an extra decode/re-encode pass only when
                        // actually configured — the 0° fast path (nearly
                        // every user) is untouched.
                        val converted = Prefs.cameraRotation.let { deg ->
                            if (deg == 0) conv.toByteArray() else rotateJpeg(conv.toByteArray(), deg)
                        }
                        deliverFrame(converted, converted.size) {}
                          } catch (e: Exception) {
                              Log.e(TAG, "Frame encode failed", e)
                          } finally {
                              img.close()
                              encoding.set(false)
                          }
                        }
                    } catch (e: java.util.concurrent.RejectedExecutionException) {
                        img.close()
                        encoding.set(false)
                    }
                }, cameraHandler)
                targets.add(reader.surface)
                camera.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        Log.d(TAG, "Configured")
                        captureSession = session
                        try {
                            captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                            // Not every UVC webcam reports AE target FPS ranges — unlike the
                            // built-in camera path this used to assume one always exists and
                            // crashed the :camera process otherwise. Just skip it if absent.
                            val rangeArray = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                            if (rangeArray != null && rangeArray.isNotEmpty()) {
                                // Fastest range that still caps at a sane
                                // ceiling for a remote-monitoring feed — not
                                // the sensor's absolute max, which can be
                                // 60fps+ and would undo the bandwidth work in
                                // cameraWidth/cameraHeight (Prefs.kt). Falls
                                // back to the slowest available range if
                                // every one exceeds the cap.
                                val targetFpsCap = 30
                                val selectedRange = rangeArray.filter { it.upper <= targetFpsCap }.maxByOrNull { it.upper }
                                    ?: rangeArray.minByOrNull { it.upper }
                                    ?: rangeArray[0]
                                captureRequestBuilder!!.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, selectedRange)
                            }
                            captureRequestBuilder!!.set(CaptureRequest.FLASH_MODE,
                                if (Prefs.isFlashlightEnabled) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF)
                            captureRequestBuilder!!.set(CaptureRequest.LENS_FOCUS_DISTANCE, Prefs.focusDistance)
                            captureRequestBuilder!!.set(CaptureRequest.CONTROL_AF_MODE,
                                if (Prefs.isAutofocusEnabled) CaptureRequest.CONTROL_AF_MODE_AUTO else CaptureRequest.CONTROL_AF_MODE_OFF)
                            val refocus = applySavedFocus(captureRequestBuilder!!, chars, camera.id)
                            CameraZoom.apply(captureRequestBuilder!!, chars, Prefs.cameraZoom)
                            captureRequestBuilder!!.addTarget(reader.surface)
                            session.setRepeatingRequest(captureRequestBuilder!!.build(), null, null)
                            if (refocus != null) {
                                // Let the stream warm up before triggering AF
                                cameraHandler?.postDelayed({ focusAt(refocus[0], refocus[1]) }, 1500)
                            }
                        } catch (e: CameraAccessException) {
                            Log.e(TAG, "Failed to start repeating request", e)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.d(TAG, "Configure failed")
                    }
                }, cameraHandler)
            } catch (e: CameraAccessException) {
                Log.e(TAG, "Failed to configure opened camera ${camera.id}", e)
            }
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.d(TAG, "Disconnected: ${camera.id}")
            if (camera.id == activeCameraId) {
                closeActiveCamera()
                activeCameraId = null
                cameraHandler?.post { openSelectedCamera() }
            }
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.d(TAG, "Error $error on ${camera.id}")
            if (camera.id == activeCameraId) {
                closeActiveCamera()
                activeCameraId = null
                cameraHandler?.post { openSelectedCamera() }
            }
        }
    }

    override fun onDestroy() {
        stoppedByUser.set(true)
        super.onDestroy()
        try { availabilityCallback?.let { cameraManager.unregisterAvailabilityCallback(it) } } catch (_: Throwable) {}
        closeActiveCamera()
        for (h in handlerThreads) h.quit()
        handlerThreads.clear()
        serverThread?.interrupt()
        cameraThread?.quit()
        cameraHandler = null
        stopForeground(true)
        notificationManager?.cancel(ID)
        unregisterReceiver(receiver)
        releaseLocks()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private class ServerThread : Thread() {
        init {
            name = "beam_camera_server"
            isDaemon = true
        }

        override fun run() {
            Process.setThreadPriority(-10)
            try {
                val socket = ServerSocket(PORT)
                while (!isInterrupted) {
                    val sock = socket.accept()
                    // Nagle's algorithm batches small writes to wait for an
                    // ACK, adding latency to every single frame (headers +
                    // JPEG body are separate write() calls below) — not
                    // needed on a streaming connection like this one.
                    try { sock.tcpNoDelay = true } catch (_: Exception) {}
                    CameraHandlerThread(sock)
                }
                socket.close()
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
    }

    private class CameraHandlerThread(sock: Socket) : HandlerThread("beam_camera_handler", -10) {
        val socket: Socket = sock
        // Buffered so the boundary/headers/JPEG-body writes for one frame
        // coalesce into a single flush() instead of 3-4 separate small
        // writes (each its own TCP segment without Nagle) — fewer syscalls,
        // less latency variance. 64KB comfortably covers a whole frame at
        // any of the resolution presets, so the buffer is rarely bypassed.
        val out: OutputStream = BufferedOutputStream(sock.outputStream, 64 * 1024)
        val oneShot: Boolean
        val handler: Handler
        val framesInFlight = AtomicBoolean(false)

        init {
            val input = sock.getInputStream()
            val r = BufferedReader(InputStreamReader(input))
            oneShot = CameraHttp.isSnapshotRequest(r.readLine())

            start()
            handler = Handler(looper)
            handler.post {
                try {
                    // The snapshot response's headers need Content-Length, so
                    // they're written once the frame size is known, in
                    // deliverFrame() below — not here.
                    if (!oneShot) {
                        out.write(CameraHttp.STREAM_HEADERS.toByteArray())
                        out.flush()
                    }
                    handlerThreads.add(this@CameraHandlerThread)
                } catch (e: Exception) {
                    Log.e(name, "Failed to write headers", e)
                    quit()
                }
            }
        }

        override fun quit(): Boolean {
            try { socket.close() } catch (_: Exception) {}
            handlerThreads.remove(this@CameraHandlerThread)
            return super.quit()
        }
    }
}

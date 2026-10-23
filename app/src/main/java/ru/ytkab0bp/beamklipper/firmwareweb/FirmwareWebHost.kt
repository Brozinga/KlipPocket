package ru.ytkab0bp.beamklipper.firmwareweb

import android.content.Context
import android.util.Log
import ru.ytkab0bp.beamklipper.utils.Prefs
import java.io.IOException

/** Owns the firmware web server of the app (one per process): started with the app, kept alive by [FirmwareWebService]. */
object FirmwareWebHost {
    private const val TAG = "firmware_web"
    private var server: FirmwareWebServer? = null

    private val pinStore = object : PinStore {
        @Synchronized override fun pin(): String {
            val saved = Prefs.firmwareWebPin
            if (WebPin.isValid(saved)) return saved
            return WebPin.generate().also { Prefs.firmwareWebPin = it }
        }

        @Synchronized override fun renew(): String = WebPin.generate().also { Prefs.firmwareWebPin = it; server?.pinRenewed() }
    }

    fun pin(): String = pinStore.pin()

    /** A new PIN: every open session of the page is closed. */
    fun renewPin(): String = pinStore.renew()

    val running: Boolean @Synchronized get() = server?.isAlive == true

    @Synchronized fun start(ctx: Context) {
        if (server?.isAlive == true) return
        val app = ctx.applicationContext
        val s = FirmwareWebServer(
            FirmwareWebServer.PORT, AndroidFirmwareBackend(app), AndroidControlBackend(app), pinStore,
            { path -> try { app.assets.open(path) } catch (e: IOException) { null } },
        )
        try {
            s.start()
            server = s
            Log.i(TAG, "firmware page on port ${FirmwareWebServer.PORT}")
        } catch (e: IOException) {
            // port busy or no network permission: the rest of the app must not be affected
            Log.w(TAG, "could not open port ${FirmwareWebServer.PORT}: ${e.message}")
        }
    }
}

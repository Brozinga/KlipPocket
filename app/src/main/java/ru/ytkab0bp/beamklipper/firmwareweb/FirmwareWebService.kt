package ru.ytkab0bp.beamklipper.firmwareweb

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.R

/** A light foreground service so the firmware page stays reachable while the app is in the background. */
class FirmwareWebService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, KlipperApp.SERVICES_CHANNEL) else Notification.Builder(this)
        val notification = builder
            .setContentTitle(getString(R.string.FirmwareWebTitle))
            .setContentText(getString(R.string.FirmwareWebNotification, FirmwareWebServer.PORT))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIFICATION_ID, notification)
        FirmwareWebHost.start(this)
        return START_STICKY
    }

    companion object {
        private const val NOTIFICATION_ID = 300010

        /** Starts the service; never throws (Android may refuse a foreground start from the background). */
        fun start(ctx: Context) {
            try { ContextCompat.startForegroundService(ctx, Intent(ctx, FirmwareWebService::class.java)) } catch (_: Throwable) {}
        }
    }
}

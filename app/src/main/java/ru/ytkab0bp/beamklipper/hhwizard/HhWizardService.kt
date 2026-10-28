package ru.ytkab0bp.beamklipper.hhwizard

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.util.Log
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.service.BasePythonService
import java.io.File

/**
 * Runs the Happy Hare setup wizard (hh_wizard.py) in a process of its own, like the other Python helpers, so its memory goes back
 * as soon as the last screen lets go. One request per message: the JSON text goes in, the JSON answer comes back.
 */
class HhWizardService : BasePythonService() {
    companion object {
        private const val TAG = "hh_wizard"
        const val MSG_CALL = 1
        const val MSG_RESULT = 2
        const val KEY_REQUEST = "request"
        const val KEY_RESULT = "result"
        private const val START_TIMEOUT_MS = 90_000L
    }

    override val needsInstance get() = false

    private var callThread: HandlerThread? = null
    private var messenger: Messenger? = null

    override fun onCreate() {
        super.onCreate()
        val thread = HandlerThread("hh_wizard_calls").also { it.start() }
        callThread = thread
        messenger = Messenger(object : Handler(thread.looper) {
            override fun handleMessage(msg: Message) {
                if (msg.what != MSG_CALL) return
                val answer = run(msg.data.getString(KEY_REQUEST).orEmpty())
                val reply = Message.obtain(null, MSG_RESULT, msg.arg1, 0)
                reply.data = Bundle().apply { putString(KEY_RESULT, answer) }
                try { msg.replyTo?.send(reply) } catch (e: Exception) { Log.w(TAG, "reply failed", e) }
            }
        })
    }

    override fun onBind(intent: Intent?): IBinder? {
        super.onBind(intent)
        return messenger?.binder
    }

    override fun onDestroy() {
        callThread?.quitSafely()
        super.onDestroy()
    }

    private fun run(request: String): String {
        val py = awaitPython(START_TIMEOUT_MS) ?: return HhWizardRules.failure("python", "Python did not start").toString()
        return try {
            val dir = File(KlipperApp.INSTANCE.filesDir, "hh_wizard")
            val path = py.getModule("sys")["path"]!!
            if (path.callAttr("count", dir.absolutePath).toInt() == 0) path.callAttr("insert", 0, dir.absolutePath)
            py.getModule("hh_wizard").callAttr("handle", request).toString()
        } catch (e: Throwable) {
            Log.e(TAG, "wizard call failed", e)
            HhWizardRules.failure("python", e.javaClass.simpleName + ": " + (e.message ?: "")).toString()
        }
    }
}

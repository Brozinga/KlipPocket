package ru.ytkab0bp.beamklipper.firmwareweb

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ru.ytkab0bp.beamklipper.KlipperInstance
import ru.ytkab0bp.beamklipper.hhwizard.HhWizardClient
import ru.ytkab0bp.beamklipper.hhwizard.HhWizardRules
import java.io.File

/** What the dashboard's Happy Hare tab needs; the web server only talks to this (the app screen uses the same [AndroidMmuBackend]). */
interface MmuBackend {
    /** The printers the wizard can set up: `{"printers":[{"id","name"}]}`. */
    fun printers(): JSONObject

    /** One call of the setup wizard for printer [id] (see [HhWizardRules.request]). Null when there is no such printer. */
    fun call(id: String, body: JSONObject): JSONObject?
}

class AndroidMmuBackend(private val ctx: Context) : MmuBackend {
    override fun printers(): JSONObject = JSONObject().put("printers", JSONArray().also { a ->
        KlipperInstance.getInstances().forEach { a.put(JSONObject().put("id", it.id).put("name", it.name)) }
    })

    override fun call(id: String, body: JSONObject): JSONObject? {
        val instance = KlipperInstance.getInstances().firstOrNull { it.id == id } ?: return null
        val configDir = File(instance.publicDirectory, "config").also { it.mkdirs() }
        val root = File(ctx.filesDir, "hh_wizard/happyhare")
        if (!File(root, "installer/Kconfig").exists()) return HhWizardRules.failure("bundle", "Happy Hare setup files are not unpacked yet")
        val request = HhWizardRules.request(root.absolutePath, configDir.absolutePath, body) ?: return HhWizardRules.failure("request")
        return HhWizardClient.call(ctx, request)
    }
}

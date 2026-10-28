package ru.ytkab0bp.beamklipper.hhwizard

import org.json.JSONObject

/** What the Happy Hare setup wizard accepts from the app screen and the web page; no Android needed, so it is tested on the JVM. */
object HhWizardRules {
    /** The calls of hh_wizard.handle() a screen may make. */
    val OPS = setOf("open", "status", "page", "set", "unset", "reset", "review", "generate", "add_to_printer")

    private val SYMBOL = Regex("[A-Za-z0-9_]{1,128}")
    private val PATH = Regex("[0-9]+(\\.[0-9]+){0,12}|")
    private const val MAX_TEXT = 500

    /**
     * The JSON text for hh_wizard.handle(), or null when [body] asks for something else. The folders always come from the app
     * ([hhRoot] where the wizard and the installer files are unpacked, [configDir] of the chosen printer), never from the caller.
     */
    fun request(hhRoot: String, configDir: String, body: JSONObject): String? {
        val op = body.optString("op")
        if (op !in OPS) return null
        val out = JSONObject().put("hh_root", hhRoot).put("config_dir", configDir).put("op", op)
        if (body.has("symbol")) {
            val symbol = body.optString("symbol")
            if (!SYMBOL.matches(symbol)) return null
            out.put("symbol", symbol)
        }
        if (body.has("path")) {
            val path = body.optString("path")
            if (!PATH.matches(path)) return null
            out.put("path", path)
        }
        if (body.has("value")) {
            when (val v = body.get("value")) {
                is Boolean, is Int, is Long, is Double -> out.put("value", v)
                is String -> if (v.length <= MAX_TEXT) out.put("value", v) else return null
                else -> return null
            }
        }
        for (flag in listOf("include", "keep_state", "load_saved")) {
            if (body.has(flag)) out.put(flag, body.optBoolean(flag, false))
        }
        if (op == "set" && !(out.has("symbol") && out.has("value"))) return null
        if (op == "unset" && !out.has("symbol")) return null
        return out.toString()
    }

    /** A failure the screens can show without the Python side: the key tells what went wrong. */
    fun failure(key: String, message: String = key): JSONObject = JSONObject().put("ok", false).put("error", message).put("key", key)
}

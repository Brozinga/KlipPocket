package ru.ytkab0bp.beamklipper.utils

import org.json.JSONObject
import java.net.URLEncoder

// Parsing and URL rules of the OctoEverywhere and Obico integrations, free of
// Android so they can be unit-tested.
object OctoEverywhereLink {
    private val PRINTER_ID = Regex("(?m)^\\s*printer_id\\s*=\\s*(.+?)\\s*$")

    // OctoEverywhere writes its generated printer id to an INI-style secrets
    // file once it first starts.
    fun printerId(secretsText: String): String? =
        PRINTER_ID.find(secretsText)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    fun linkUrl(printerId: String): String = "https://octoeverywhere.com/getstarted?printerid=$printerId"
}

object ObicoLink {
    private val AUTH_TOKEN = Regex("(?m)^\\s*auth_token\\s*=\\s*(\\S+)\\s*$")

    // What moonraker_obico's discovery writes for the app to show: the
    // one-time code to type into Obico, and whether linking already happened.
    data class DiscoveryStatus(val isLinked: Boolean, val passcode: String, val passlink: String)

    fun parseDiscoveryStatus(json: String): DiscoveryStatus? = try {
        val o = JSONObject(json)
        DiscoveryStatus(
            isLinked = o.optBoolean("is_linked", false),
            passcode = o.optString("one_time_passcode", ""),
            passlink = o.optString("one_time_passlink", "")
        )
    } catch (_: Throwable) { null }

    // Linking completes inside the Python process, which writes the token
    // straight into moonraker-obico.cfg.
    fun authTokenFromConfig(configText: String): String? =
        AUTH_TOKEN.find(configText)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    // The one call Obico's own linking script makes underneath, for a code
    // the user typed: POST {server}/api/v1/octo/verify/?code=XXXXXX
    fun verifyUrl(serverUrl: String, code: String): String =
        serverUrl.trimEnd('/') + "/api/v1/octo/verify/?code=" + URLEncoder.encode(code.trim(), "UTF-8")

    sealed class Result {
        object Success : Result()
        object InvalidCode : Result()
        data class NetworkError(val message: String?) : Result()
    }

    // 2xx links the printer, 4xx means the server rejected the code, anything
    // else is a server or network problem.
    fun classifyResponse(httpCode: Int): Result? = when (httpCode) {
        in 200..299 -> null
        in 400..499 -> Result.InvalidCode
        else -> Result.NetworkError("HTTP $httpCode")
    }

    fun authTokenFromResponse(body: String): String =
        JSONObject(body).getJSONObject("printer").getString("auth_token")

    fun isCloud(url: String): Boolean = url == Prefs.OBICO_CLOUD_URL
}

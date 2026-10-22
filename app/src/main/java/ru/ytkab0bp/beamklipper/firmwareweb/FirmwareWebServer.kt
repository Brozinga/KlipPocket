package ru.ytkab0bp.beamklipper.firmwareweb

import org.json.JSONArray
import org.json.JSONObject
import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.NanoHTTPD
import org.nanohttpd.protocols.http.request.Method
import org.nanohttpd.protocols.http.response.Response
import org.nanohttpd.protocols.http.response.Status
import ru.ytkab0bp.beamklipper.firmware.FirmwareStore
import java.io.File
import java.io.InputStream

/** Where the PIN lives; the app keeps it in Prefs, the tests in memory. */
interface PinStore {
    fun pin(): String
    fun renew(): String
}

/**
 * The firmware web page: static files plus a small JSON API, on port 4400. Everything under /api needs the PIN
 * (a session cookie after /api/login) and every request that changes something carries the guard header.
 * All the real work is done by the [FirmwareBackend], which is the app's own Firmware tab code.
 */
class FirmwareWebServer(
    port: Int,
    private val backend: FirmwareBackend,
    private val pins: PinStore,
    private val assets: (String) -> InputStream?,
    private val limiter: LoginLimiter = LoginLimiter(),
    private val sessions: WebSessions = WebSessions(),
) : NanoHTTPD(port) {

    companion object {
        const val PORT = 4400
        private const val MAX_JSON_BODY = 8 * 1024L
    }

    /** The PIN was replaced: everyone has to log in again. */
    fun pinRenewed() = sessions.clear()

    private fun json(status: Status, body: JSONObject): Response =
        Response.newFixedLengthResponse(status, "application/json; charset=utf-8", body.toString()).also { common(it) }

    private fun ok(body: JSONObject = JSONObject()) = json(Status.OK, body)

    private fun error(status: Status, key: String, message: String = key, closeConnection: Boolean = false): Response =
        json(status, JSONObject().put("error", key).put("message", message)).also { if (closeConnection) it.addHeader("Connection", "close") }

    private fun common(r: Response) {
        r.addHeader("Cache-Control", "no-store")
        r.addHeader("X-Content-Type-Options", "nosniff")
        r.addHeader("Content-Security-Policy", WebRequests.CSP)
        r.addHeader("Referrer-Policy", "no-referrer")
    }

    private fun serveStatic(path: String): Response {
        val p = if (path == "/") "/index.html" else path
        if (path !in WebRequests.STATIC) return Response.newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "Not Found")
        val stream = assets("firmware_web$p") ?: return Response.newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "Not Found")
        return Response.newChunkedResponse(Status.OK, WebRequests.mimeFor(p), stream).also { common(it) }
    }

    private fun contentLength(s: IHTTPSession): Long = s.headers["content-length"]?.toLongOrNull() ?: 0

    /** The JSON body of the request, read (and so removed from the connection) once, whether the route wants it or not. */
    private fun body(s: IHTTPSession): JSONObject? {
        val files = HashMap<String, String>()
        s.parseBody(files)
        val raw = files["postData"] ?: return JSONObject()
        return try { JSONObject(raw) } catch (e: Exception) { null }
    }

    override fun serve(session: IHTTPSession): Response {
        val path = session.uri
        if (!path.startsWith("/api/")) return if (session.method == Method.GET || session.method == Method.HEAD) serveStatic(path)
        else Response.newFixedLengthResponse(Status.METHOD_NOT_ALLOWED, "text/plain", "Method Not Allowed")
        return try { api(session, path) } catch (e: NanoHTTPD.ResponseException) {
            error(e.status as Status, "request", e.message ?: "bad request", closeConnection = true)
        } catch (e: Exception) {
            error(Status.INTERNAL_ERROR, "server", e.javaClass.simpleName)
        }
    }

    private fun api(s: IHTTPSession, path: String): Response {
        val ip = s.remoteIpAddress ?: ""
        if (path == "/api/login" && s.method == Method.POST) return login(s, ip)
        val authenticated = sessions.valid(WebRequests.cookie(s.headers["cookie"], WebRequests.COOKIE))
        if (path == "/api/session" && s.method == Method.GET) return ok(JSONObject().put("authenticated", authenticated))
        if (!authenticated) return error(Status.UNAUTHORIZED, "auth", closeConnection = contentLength(s) > 0)
        val changes = s.method != Method.GET && s.method != Method.HEAD
        if (changes && s.headers[WebRequests.GUARD_HEADER] == null) return error(Status.FORBIDDEN, "guard", closeConnection = true)
        val q = s.parameters
        // A body nobody reads would be taken for the start of the next request on a kept-alive connection.
        var json: JSONObject? = JSONObject()
        if (path != "/api/upload" && contentLength(s) > 0) {
            if (contentLength(s) > MAX_JSON_BODY) return error(Status.PAYLOAD_TOO_LARGE, "size", closeConnection = true)
            json = body(s)
        }

        return when {
            path == "/api/logout" && s.method == Method.POST -> {
                sessions.revoke(WebRequests.cookie(s.headers["cookie"], WebRequests.COOKIE))
                ok().also { it.addHeader("Set-Cookie", "${WebRequests.COOKIE}=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict") }
            }
            path == "/api/state" && s.method == Method.GET -> ok(state())
            path == "/api/files" && s.method == Method.DELETE -> {
                val name = q["name"]?.firstOrNull() ?: return error(Status.BAD_REQUEST, "name")
                if (backend.deleteFile(name)) ok(JSONObject().put("files", files())) else error(Status.NOT_FOUND, "file")
            }
            path == "/api/files/delete-all" && s.method == Method.POST -> { backend.deleteAll(); ok(JSONObject().put("files", files())) }
            path == "/api/upload" && s.method == Method.PUT -> upload(s, q["name"]?.firstOrNull())
            path == "/api/download" && s.method == Method.POST -> {
                val url = (json ?: return error(Status.BAD_REQUEST, "json")).optString("url")
                val problem = backend.startDownload(url)
                if (problem == null) ok(JSONObject().put("download", download()))
                else error(if (problem == "busy") Status.CONFLICT else Status.BAD_REQUEST, if (problem == "busy") "busy" else "link", problem)
            }
            path == "/api/download/reset" && s.method == Method.POST -> { backend.resetDownload(); ok(JSONObject().put("download", download())) }
            path == "/api/devices" && s.method == Method.GET -> ok(JSONObject().put("devices", devices()))
            path == "/api/experimental" && s.method == Method.GET -> ok(JSONObject().put("experimental",
                backend.experimental(q["file"]?.firstOrNull().orEmpty(), q["device"]?.firstOrNull().orEmpty(), q["mode"]?.firstOrNull().orEmpty())))
            path == "/api/flash" && s.method == Method.POST -> {
                val b = json ?: return error(Status.BAD_REQUEST, "json")
                val address = if (b.has("startAddress") && !b.isNull("startAddress")) parseAddress(b.optString("startAddress")) ?: return error(Status.BAD_REQUEST, "address") else null
                val problem = backend.startFlash(b.optString("file"), b.optString("device"), b.optString("mode"), address)
                if (problem == null) ok(JSONObject().put("flash", flash()))
                else error(if (problem == "busy") Status.CONFLICT else Status.BAD_REQUEST, problem)
            }
            path == "/api/flash" && s.method == Method.GET -> ok(JSONObject().put("flash", flash()))
            path == "/api/flash/reset" && s.method == Method.POST -> { backend.resetFlash(); ok(JSONObject().put("flash", flash())) }
            path == "/api/log" && s.method == Method.GET -> ok(JSONObject().put("log", backend.log()))
            else -> error(Status.NOT_FOUND, "route")
        }
    }

    private fun parseAddress(text: String): Long? = text.trim().removePrefix("0x").removePrefix("0X").toLongOrNull(16)

    private fun login(s: IHTTPSession, ip: String): Response {
        if (!limiter.allowed(ip)) return error(Status.TOO_MANY_REQUESTS, "locked", limiter.secondsLeft(ip).toString(), closeConnection = true)
        if (contentLength(s) > MAX_JSON_BODY) return error(Status.PAYLOAD_TOO_LARGE, "size", closeConnection = true)
        val given = (body(s) ?: return error(Status.BAD_REQUEST, "json")).optString("pin")
        if (!WebPin.isValid(given) || !WebPin.matches(pins.pin(), given)) {
            limiter.failed(ip)
            return error(Status.UNAUTHORIZED, "pin")
        }
        limiter.succeeded(ip)
        val token = sessions.issue()
        return ok(JSONObject().put("authenticated", true)).also {
            it.addHeader("Set-Cookie", "${WebRequests.COOKIE}=$token; Path=/; Max-Age=86400; HttpOnly; SameSite=Strict")
        }
    }

    private fun upload(s: IHTTPSession, name: String?): Response {
        if (name == null) return error(Status.BAD_REQUEST, "name", closeConnection = true)
        val length = contentLength(s)
        if (length > FirmwareStore.MAX_BYTES) return error(Status.PAYLOAD_TOO_LARGE, "size", closeConnection = true)
        if (!FirmwareStore.isAllowedName(FirmwareStore.safeName(name))) return error(Status.BAD_REQUEST, "type", closeConnection = true)
        val files = HashMap<String, String>()
        s.parseBody(files)
        val tmp = files["content"]?.takeIf { it.isNotEmpty() }?.let { File(it) } ?: return error(Status.BAD_REQUEST, "empty")
        return try {
            val saved = backend.saveUpload(name, tmp)
            ok(JSONObject().put("saved", saved.name).put("files", files()))
        } catch (e: IllegalArgumentException) {
            error(if (e.message == "size") Status.PAYLOAD_TOO_LARGE else Status.BAD_REQUEST, e.message ?: "upload")
        }
    }

    private fun files() = JSONArray().also { a -> backend.files().forEach { a.put(JSONObject().put("name", it.name).put("kind", it.kind).put("size", it.size)) } }

    private fun devices() = JSONArray().also { a ->
        backend.devices().forEach {
            a.put(JSONObject().put("id", it.id).put("label", it.label).put("vendor", it.vendor).put("product", it.product)
                .put("path", it.path).put("driver", it.driver ?: JSONObject.NULL).put("serialPath", it.serialPath ?: JSONObject.NULL))
        }
    }

    private fun download() = backend.download().let {
        JSONObject().put("state", it.state).put("percent", it.percent).put("message", it.message).put("files", JSONArray(it.files))
    }

    private fun flash() = backend.flash().let {
        JSONObject().put("state", it.state).put("stage", it.stage).put("percent", it.percent).put("overall", it.overall)
            .put("success", it.success).put("failure", it.failure ?: JSONObject.NULL).put("detail", it.detail).put("noReturn", it.noReturn)
            .put("file", it.file).put("device", it.device).put("mode", it.mode)
    }

    private fun state() = JSONObject()
        .put("language", backend.language()).put("dark", backend.dark())
        .put("modes", JSONArray(WebRequests.MODES)).put("maxBytes", FirmwareStore.MAX_BYTES)
        .put("files", files()).put("devices", devices()).put("download", download()).put("flash", flash())
}

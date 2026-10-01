package ru.ytkab0bp.beamklipper.service.web

// What the embedded web server forwards to Moonraker, and how. Free of
// Android so it can be unit-tested.
object ProxyRules {
    const val LOCAL_ADDRESS = "127.0.0.1"

    // Hop-by-hop and length headers the proxy rebuilds itself, plus
    // "remote-addr" / "http-client-ip", which NanoHTTPD injects into the
    // header map on its own: forwarding those confuses Moonraker's proxy
    // detection.
    private val DROPPED_REQUEST_HEADERS = setOf(
        "host", "connection", "content-length", "transfer-encoding",
        "keep-alive", "proxy-connection", "remote-addr", "http-client-ip"
    )

    private val DROPPED_RESPONSE_HEADERS = setOf(
        "content-length", "transfer-encoding", "connection", "keep-alive"
    )

    private val METHODS_WITH_BODY = setOf("POST", "PUT", "PATCH")

    fun upstreamUrl(port: Int, uri: String, query: String?): String =
        "http://$LOCAL_ADDRESS:$port/${uri.substring(1)}" + queryPart(query)

    fun websocketUrl(port: Int, query: String?): String =
        "ws://$LOCAL_ADDRESS:$port/websocket" + queryPart(query)

    private fun queryPart(query: String?) = if (query.isNullOrEmpty()) "" else "?$query"

    fun forwardRequestHeader(name: String): Boolean = name.lowercase() !in DROPPED_REQUEST_HEADERS

    // The proxy's own status line carries no header with a null or empty name.
    fun forwardResponseHeader(name: String?): Boolean =
        !name.isNullOrEmpty() && name.lowercase() !in DROPPED_RESPONSE_HEADERS

    fun hasBody(method: String): Boolean = method.uppercase() in METHODS_WITH_BODY

    // The /beam/* endpoints drive the phone (flashlight, beeper, ffmpeg): only
    // the app's own Klipper process on the same device may call them.
    fun isLocalRequest(remoteAddress: String?): Boolean = remoteAddress == LOCAL_ADDRESS
}

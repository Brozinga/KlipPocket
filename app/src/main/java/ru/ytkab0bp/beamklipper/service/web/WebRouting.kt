package ru.ytkab0bp.beamklipper.service.web

// Routing rules of the embedded web server (front end assets + Moonraker
// proxy), kept free of Android so they can be unit-tested.
object WebRouting {
    private val API_PATTERN = Regex("^/(printer|api|access|machine|server)/")

    // Moonraker's config is Python-configparser style: the key/value separator
    // may be ':' or '='.
    private val MOONRAKER_PORT = Regex("^\\s*port\\s*[:=]\\s*(\\d+)", RegexOption.MULTILINE)

    // A listening (state 0A) IPv4/IPv6 socket in /proc/net/tcp[6]: local port in hex.
    private val LISTENING_SOCKET = Regex(
        "^\\s*[0-9A-Fa-f]+:\\s*[0-9A-Fa-f]+:([0-9A-Fa-f]{4})\\s+[0-9A-Fa-f]+:[0-9A-Fa-f]+\\s+0A",
        RegexOption.MULTILINE
    )

    val MOONRAKER_PORT_SCAN_RANGE = 7100..9000
    const val DEFAULT_MOONRAKER_PORT = 7125

    // Requests forwarded to Moonraker; everything else is a front end file.
    fun isApiPath(uri: String): Boolean = API_PATTERN.containsMatchIn(uri)

    fun moonrakerPortFromConfig(configText: String): Int? =
        MOONRAKER_PORT.find(configText)?.groupValues?.get(1)?.toIntOrNull()

    // Fallback when moonraker.conf has no port: the lowest listening port in
    // Moonraker's usual range. NanoHTTPD does no content-type guessing, so
    // anything not mapped here would go out as text/plain and WebView would
    // refuse to use fonts or SVG logos.
    fun lowestListeningPort(procNetTcp: String): Int? =
        LISTENING_SOCKET.findAll(procNetTcp)
            .map { it.groupValues[1].toInt(16) }
            .filter { it in MOONRAKER_PORT_SCAN_RANGE }
            .minOrNull()

    fun mimeTypeFor(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "js", "mjs" -> "text/javascript"
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "json", "map" -> "application/json"
        "webmanifest" -> "application/manifest+json"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "ico" -> "image/x-icon"
        "woff2" -> "font/woff2"
        "woff" -> "font/woff"
        "ttf" -> "font/ttf"
        "eot" -> "application/vnd.ms-fontobject"
        "wasm" -> "application/wasm"
        "xml" -> "application/xml"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }

    fun resolveStaticPath(path: String): String = if (path == "/") "/index.html" else path

    // The bundled folder of the active front end ("fluidd", "mainsail", "voyager").
    fun assetPath(frontend: String, resolvedPath: String): String = frontend + resolvedPath

    // Entry documents and manifests must always be revalidated; hashed assets
    // never change, so they are cached for a week.
    fun isRevalidated(resolvedPath: String): Boolean =
        resolvedPath.endsWith(".html") || resolvedPath.endsWith(".json") || resolvedPath.endsWith(".webmanifest")

    // A missing path with a file extension is a real 404 (a stale hashed
    // chunk, a bad asset URL). Only extensionless paths are client-side
    // routes that must fall through to index.html: returning HTML for a
    // missing .js just yields a MIME error.
    fun isRealNotFound(path: String, resolvedPath: String): Boolean =
        path == "/index.html" || path == "/" || resolvedPath.substringAfterLast('/').contains('.')
}

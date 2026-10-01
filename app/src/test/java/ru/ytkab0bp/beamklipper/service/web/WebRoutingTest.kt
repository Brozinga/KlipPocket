package ru.ytkab0bp.beamklipper.service.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebRoutingTest {
    @Test
    fun `moonraker endpoints are proxied`() {
        listOf("/printer/info", "/api/version", "/access/oneshot_token", "/machine/system_info", "/server/info")
            .forEach { assertTrue(it, WebRouting.isApiPath(it)) }
    }

    @Test
    fun `front end files and beam endpoints are not proxied`() {
        listOf("/", "/index.html", "/assets/app.js", "/beam/ffmpeg", "/printerx/info", "/server", "/webcam/")
            .forEach { assertFalse(it, WebRouting.isApiPath(it)) }
    }

    @Test
    fun `the port is read from moonraker conf with a colon`() {
        assertEquals(7126, WebRouting.moonrakerPortFromConfig("[server]\nhost: 0.0.0.0\nport: 7126\n"))
    }

    @Test
    fun `the port is read from moonraker conf with an equals sign`() {
        assertEquals(7130, WebRouting.moonrakerPortFromConfig("[server]\n  port = 7130\n"))
    }

    @Test
    fun `only a key at the start of a line counts as the port`() {
        assertNull(WebRouting.moonrakerPortFromConfig("[server]\n# port: 7126\nreport: 9\n"))
        assertNull(WebRouting.moonrakerPortFromConfig(""))
    }

    private val procNetTcp = """
        sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
         0: 0100007F:1BB5 00000000:0000 0A 00000000:00000000 00:00000000 00000000 10001        0 1 1 0000000000000000 100 0 0 10 0
         1: 00000000:1BB6 00000000:0000 0A 00000000:00000000 00:00000000 00000000 10001        0 2 1 0000000000000000 100 0 0 10 0
         2: 00000000:0050 00000000:0000 0A 00000000:00000000 00:00000000 00000000 10001        0 3 1 0000000000000000 100 0 0 10 0
         3: 00000000:1F40 0100007F:D2A4 01 00000000:00000000 00:00000000 00000000 10001        0 4 1 0000000000000000 100 0 0 10 0
    """.trimIndent()

    @Test
    fun `the lowest listening port in moonraker's range is chosen`() {
        // 0x1BB5 = 7093 (below the range), 0x1BB6 = 7094 (below), 0x0050 = 80
        assertNull(WebRouting.lowestListeningPort(procNetTcp))
        val text = procNetTcp + "\n         4: 00000000:1BCD 00000000:0000 0A 00000000:00000000 00:00000000 00000000 1 0 5 1 0 0 0 0 0\n" +
            "         5: 00000000:1BCF 00000000:0000 0A 00000000:00000000 00:00000000 00000000 1 0 6 1 0 0 0 0 0"
        assertEquals(0x1BCD, WebRouting.lowestListeningPort(text))
    }

    @Test
    fun `sockets that are not listening are ignored`() {
        val established = "         0: 00000000:1BCD 0100007F:D2A4 01 00000000:00000000 00:00000000 00000000 1 0 5 1 0 0 0 0 0"
        assertNull(WebRouting.lowestListeningPort(established))
    }

    @Test
    fun `web assets get the right mime type`() {
        val expected = mapOf(
            "app.js" to "text/javascript", "chunk.mjs" to "text/javascript",
            "index.html" to "text/html", "old.htm" to "text/html", "site.css" to "text/css",
            "data.json" to "application/json", "app.js.map" to "application/json",
            "manifest.webmanifest" to "application/manifest+json", "logo.svg" to "image/svg+xml",
            "a.png" to "image/png", "a.JPG" to "image/jpeg", "a.jpeg" to "image/jpeg", "a.gif" to "image/gif",
            "a.webp" to "image/webp", "favicon.ico" to "image/x-icon",
            "font.woff2" to "font/woff2", "font.woff" to "font/woff", "font.ttf" to "font/ttf",
            "font.eot" to "application/vnd.ms-fontobject", "lib.wasm" to "application/wasm",
            "a.xml" to "application/xml", "robots.txt" to "text/plain"
        )
        expected.forEach { (file, mime) -> assertEquals(file, mime, WebRouting.mimeTypeFor("/assets/$file")) }
    }

    @Test
    fun `unknown or missing extensions are binary`() {
        assertEquals("application/octet-stream", WebRouting.mimeTypeFor("/blob.xyz"))
        assertEquals("application/octet-stream", WebRouting.mimeTypeFor("/noextension"))
    }

    @Test
    fun `the root serves index html`() {
        assertEquals("/index.html", WebRouting.resolveStaticPath("/"))
        assertEquals("/assets/a.js", WebRouting.resolveStaticPath("/assets/a.js"))
    }

    @Test
    fun `the asset path lives in the front end's folder`() {
        assertEquals("voyager/index.html", WebRouting.assetPath("voyager", "/index.html"))
        assertEquals("fluidd/assets/a.js", WebRouting.assetPath("fluidd", "/assets/a.js"))
    }

    @Test
    fun `documents and manifests are revalidated but hashed assets are cached`() {
        assertTrue(WebRouting.isRevalidated("/index.html"))
        assertTrue(WebRouting.isRevalidated("/config.json"))
        assertTrue(WebRouting.isRevalidated("/site.webmanifest"))
        assertFalse(WebRouting.isRevalidated("/assets/index-abc123.js"))
        assertFalse(WebRouting.isRevalidated("/assets/font.woff2"))
    }

    @Test
    fun `a missing file is a 404 but a missing route falls back to index html`() {
        assertTrue(WebRouting.isRealNotFound("/assets/old-hash.js", "/assets/old-hash.js"))
        assertTrue(WebRouting.isRealNotFound("/index.html", "/index.html"))
        assertTrue(WebRouting.isRealNotFound("/", "/index.html"))
        assertFalse(WebRouting.isRealNotFound("/dashboard", "/dashboard"))
        assertFalse(WebRouting.isRealNotFound("/settings/printer", "/settings/printer"))
    }
}

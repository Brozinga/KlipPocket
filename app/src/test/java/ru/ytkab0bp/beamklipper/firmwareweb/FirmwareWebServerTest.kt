package ru.ytkab0bp.beamklipper.firmwareweb

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL

/** Records what the page asked the app to do, and answers like the Firmware tab would. */
private class FakeBackend : FirmwareBackend {
    val stored = LinkedHashMap<String, ByteArray>()
    var flashRequest: List<Any?>? = null
    var flashState = WebFlash("idle", "", 0, 0, false, null, "", false, "", "", "")
    var downloadState = WebDownload("idle", 0, "", emptyList())
    var downloadUrl: String? = null
    val devicesList = listOf(WebDevice("/dev/bus/usb/001/005", "rp2040", "2e8a", "3", "/dev/bus/usb/001/005", null, null))

    override fun language() = "pt-BR"
    override fun dark() = false
    override fun files() = stored.map { WebFile(it.key, it.key.substringAfterLast('.').uppercase(), it.value.size.toLong()) }
    override fun deleteFile(name: String) = stored.remove(name) != null
    override fun deleteAll(): Int { val n = stored.size; stored.clear(); return n }
    override fun saveUpload(name: String, source: File): WebFile {
        val bytes = source.readBytes()
        require(bytes.isNotEmpty()) { "empty" }
        stored[name] = bytes
        return WebFile(name, "BIN", bytes.size.toLong())
    }
    override fun startDownload(url: String): String? {
        if (downloadState.state == "running") return "busy"
        if (!url.startsWith("https://")) return "only https links are accepted"
        downloadUrl = url
        downloadState = WebDownload("running", 10, "", emptyList())
        return null
    }
    override fun download() = downloadState
    override fun resetDownload() { downloadState = WebDownload("idle", 0, "", emptyList()) }
    override fun devices() = devicesList
    override fun experimental(file: String, device: String, mode: String) = mode != "RP2040"
    override fun startFlash(file: String, device: String, mode: String, startAddress: Long?): String? {
        if (flashState.state == "running") return "busy"
        if (file !in stored) return "file"
        flashRequest = listOf(file, device, mode, startAddress)
        flashState = WebFlash("running", "WRITE", 40, 60, false, null, "", false, file, device, mode)
        return null
    }
    override fun flash() = flashState
    override fun resetFlash() { flashState = WebFlash("idle", "", 0, 0, false, null, "", false, "", "", "") }
    override fun log() = "line 1\nline 2"

    var logsCleared = false
    override fun dashboard() = JSONObject("{\"host\":{\"model\":\"Pixel\"},\"storage\":[],\"instances\":[],\"usb\":[]}")
    override fun metrics() = JSONObject("{\"cpu\":12.5,\"memTotal\":100,\"memAvailable\":40}")
    override fun logSources() = listOf(WebLogSource("app", "App / Web"), WebLogSource("flash", "Firmware"))
    override fun readLog(id: String) = if (id == "flash" && !logsCleared) "[ts] flashed" else if (id == "app") "app line" else if (id == "flash") "" else null
    override fun clearLogs(): Boolean { logsCleared = true; return true }
}

private class FakeControl : ControlBackend {
    val applied = ArrayList<Pair<String, JSONObject>>()
    val controls = ArrayList<Pair<String?, String>>()
    var focus: Pair<Float, Float>? = null
    var focusReset = false
    var cameraOn = true
    override fun settings() = JSONObject("{\"engine\":{\"value\":\"klipper\",\"options\":[]}}")
    override fun applySetting(key: String, body: JSONObject): String? {
        if (key == "cameraEnabled" && body.optBoolean("value")) return "permission"
        if (key == "nope") return "key"
        applied += key to body
        return null
    }
    override fun control(id: String?, action: String): String? {
        if (id == "full") return "slots"
        controls += id to action
        return null
    }
    val added = ArrayList<List<Any>>()
    override fun newPrinterInfo() = JSONObject("{\"templates\":[\"a.cfg\",\"b.cfg\"],\"default\":\"a.cfg\",\"defaultName\":\"Printer 1\",\"canCreate\":true}")
    override fun addPrinter(name: String, config: String, autostart: Boolean): String? {
        if (config == "full") return "slots"
        if (config != "a.cfg" && config != "b.cfg") return "config"
        added += listOf(name, config, autostart)
        return null
    }
    override fun camera() = JSONObject("{\"enabled\":$cameraOn,\"permission\":true,\"focusSupported\":true}")
    override fun openCameraStream() = if (cameraOn) CameraFeed("multipart/x-mixed-replace; boundary=camera-frame",
        ByteArrayInputStream("--camera-frame\r\nframe".toByteArray())) else null
    override fun focusAt(x: Float, y: Float) { focus = x to y }
    override fun resetFocus() { focusReset = true }
}

class FirmwareWebServerTest {
    private lateinit var control: FakeControl
    private val pin = "123456"
    private lateinit var backend: FakeBackend
    private lateinit var server: FirmwareWebServer
    private var base = ""
    private var cookie: String? = null

    private val pinStore = object : PinStore {
        var current = pin
        override fun pin() = current
        override fun renew(): String { current = "654321"; return current }
    }

    @Before fun start() {
        backend = FakeBackend()
        control = FakeControl()
        server = FirmwareWebServer(0, backend, control, pinStore, { path ->
            if (path.startsWith("firmware_web/")) ByteArrayInputStream("<html>$path</html>".toByteArray()) else null
        })
        server.start()
        base = "http://127.0.0.1:${server.listeningPort}"
    }

    @After fun stop() { server.stop() }

    private class Reply(val code: Int, val body: String, val headers: Map<String, List<String>>) {
        fun json() = JSONObject(body)
    }

    private fun call(method: String, path: String, body: ByteArray? = null, json: JSONObject? = null, guard: Boolean = true, withCookie: Boolean = true): Reply {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        if (guard && method != "GET") c.setRequestProperty("X-KlipPocket", "1")
        if (withCookie) cookie?.let { c.setRequestProperty("Cookie", it) }
        val payload = body ?: json?.toString()?.toByteArray()
        if (payload != null) {
            c.doOutput = true
            if (json != null) c.setRequestProperty("Content-Type", "application/json")
            c.setFixedLengthStreamingMode(payload.size)
            c.outputStream.use { it.write(payload) }
        }
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream)?.readBytes()?.toString(Charsets.UTF_8).orEmpty()
        return Reply(code, text, c.headerFields.filterKeys { it != null })
    }

    private fun login(): Reply {
        val r = call("POST", "/api/login", json = JSONObject().put("pin", pin), withCookie = false)
        cookie = r.headers["Set-Cookie"]?.firstOrNull()?.substringBefore(';')
        return r
    }

    // --- static files and access -------------------------------------------------------------------------------

    @Test fun servesOnlyThePagesOwnFilesWithASafePolicy() {
        val index = call("GET", "/")
        assertEquals(200, index.code)
        assertTrue(index.body.contains("firmware_web/index.html"))
        assertTrue(index.headers["Content-Security-Policy"]!!.first().contains("default-src 'self'"))
        assertEquals("nosniff", index.headers["X-Content-Type-Options"]!!.first())
        assertEquals(200, call("GET", "/app.js").code)
        assertEquals(404, call("GET", "/../etc/passwd").code)
        assertEquals(404, call("GET", "/AndroidManifest.xml").code)
        assertEquals(404, call("GET", "/firmware_web/index.html").code)
    }

    @Test fun everyApiRouteNeedsTheLogin() {
        for ((m, p) in listOf("GET" to "/api/state", "GET" to "/api/files", "GET" to "/api/devices", "GET" to "/api/flash", "GET" to "/api/log", "GET" to "/api/dashboard", "GET" to "/api/metrics", "GET" to "/api/logs", "GET" to "/api/logs/read?id=app", "POST" to "/api/logs/clear", "GET" to "/api/settings", "POST" to "/api/settings", "POST" to "/api/control", "GET" to "/api/printers/new", "POST" to "/api/printers", "GET" to "/api/camera", "GET" to "/api/camera/stream", "POST" to "/api/camera/focus", "POST" to "/api/flash")) {
            assertEquals("$m $p", 401, call(m, p, withCookie = false).code)
        }
        assertEquals(false, call("GET", "/api/session", withCookie = false).json().getBoolean("authenticated"))
    }

    @Test fun aWrongPinIsRefusedAndTooManyLockTheAddress() {
        repeat(5) { assertEquals(401, call("POST", "/api/login", json = JSONObject().put("pin", "000000"), withCookie = false).code) }
        val locked = call("POST", "/api/login", json = JSONObject().put("pin", pin), withCookie = false)
        assertEquals("even the right PIN waits", 429, locked.code)
        assertTrue(locked.json().getString("message").toInt() in 1..60)
    }

    @Test fun theRightPinGivesAnHttpOnlySameSiteCookie() {
        val r = login()
        assertEquals(200, r.code)
        val set = r.headers["Set-Cookie"]!!.first()
        assertTrue(set.contains("HttpOnly") && set.contains("SameSite=Strict"))
        assertEquals(true, call("GET", "/api/session").json().getBoolean("authenticated"))
    }

    @Test fun aPinThatIsNotSixDigitsIsRefused() {
        assertEquals(401, call("POST", "/api/login", json = JSONObject().put("pin", "12345"), withCookie = false).code)
        assertEquals(401, call("POST", "/api/login", json = JSONObject().put("pin", "abcdef"), withCookie = false).code)
    }

    @Test fun renewingThePinClosesEverySession() {
        login()
        assertEquals(200, call("GET", "/api/state").code)
        pinStore.renew(); server.pinRenewed()
        assertEquals(401, call("GET", "/api/state").code)
    }

    @Test fun requestsThatChangeThingsNeedTheGuardHeader() {
        login()
        assertEquals(403, call("POST", "/api/flash/reset", json = JSONObject(), guard = false).code)
        assertEquals(403, call("PUT", "/api/upload?name=a.bin", body = byteArrayOf(1), guard = false).code)
        assertEquals(403, call("DELETE", "/api/files?name=a.bin", guard = false).code)
    }

    @Test fun logoutEndsTheSession() {
        login()
        call("POST", "/api/logout", json = JSONObject())
        assertEquals(401, call("GET", "/api/state").code)
    }

    // --- state ---------------------------------------------------------------------------------------------------

    @Test fun oneStateAnswerHasWhatThePageNeeds() {
        login()
        backend.stored["a.bin"] = ByteArray(10)
        val s = call("GET", "/api/state").json()
        assertEquals("pt-BR", s.getString("language"))
        assertFalse(s.getBoolean("dark"))
        assertEquals(listOf("USB", "UART", "DFU", "RP2040"), (0 until 4).map { s.getJSONArray("modes").getString(it) })
        assertEquals("a.bin", s.getJSONArray("files").getJSONObject(0).getString("name"))
        val d = s.getJSONArray("devices").getJSONObject(0)
        assertEquals("rp2040", d.getString("label"))
        assertEquals("/dev/bus/usb/001/005", d.getString("path"))
        assertEquals("idle", s.getJSONObject("flash").getString("state"))
        assertEquals(16L * 1024 * 1024, s.getLong("maxBytes"))
    }

    // --- files ---------------------------------------------------------------------------------------------------

    @Test fun anUploadKeepsEveryByteOfABinaryFile() {
        login()
        val data = ByteArray(5000) { (it * 31).toByte() } + byteArrayOf(0, -1, 13, 10, 0)
        val r = call("PUT", "/api/upload?name=pico.uf2", body = data)
        assertEquals(r.body, 200, r.code)
        assertArrayEquals(data, backend.stored["pico.uf2"])
        assertEquals("pico.uf2", r.json().getString("saved"))
        assertEquals(1, r.json().getJSONArray("files").length())
    }

    @Test fun uploadsAreCheckedForNameAndSize() {
        login()
        assertEquals(400, call("PUT", "/api/upload?name=notes.txt", body = byteArrayOf(1)).code)
        assertEquals(400, call("PUT", "/api/upload", body = byteArrayOf(1)).code)
        assertEquals(400, call("PUT", "/api/upload?name=empty.bin", body = ByteArray(0)).code)
        // a body that is declared too big is refused before it is read
        Socket("127.0.0.1", server.listeningPort).use { s ->
            s.soTimeout = 5000
            val cookieLine = cookie?.let { "Cookie: $it\r\n" }.orEmpty()
            s.getOutputStream().write(("PUT /api/upload?name=big.bin HTTP/1.1\r\nHost: x\r\nX-KlipPocket: 1\r\n$cookieLine" +
                    "Content-Length: ${64L * 1024 * 1024}\r\n\r\n").toByteArray())
            val line = s.getInputStream().bufferedReader().readLine()
            assertTrue(line, line.contains("413"))
        }
        assertTrue(backend.stored.isEmpty())
    }

    @Test fun filesCanBeDeletedOneByOneOrAll() {
        login()
        backend.stored["a.bin"] = byteArrayOf(1); backend.stored["b.hex"] = byteArrayOf(2); backend.stored["c.uf2"] = byteArrayOf(3)
        assertEquals(200, call("DELETE", "/api/files?name=a.bin").code)
        assertEquals(404, call("DELETE", "/api/files?name=a.bin").code)
        assertEquals(400, call("DELETE", "/api/files").code)
        assertEquals(0, call("POST", "/api/files/delete-all", json = JSONObject()).json().getJSONArray("files").length())
    }

    // --- download from a link -----------------------------------------------------------------------------------

    @Test fun aLinkStartsADownloadAndReportsItsProgress() {
        login()
        val r = call("POST", "/api/download", json = JSONObject().put("url", "https://github.com/utkabobr/klipper/releases/download/prebuilt-v0.12.0/stm32f401.bin"))
        assertEquals(200, r.code)
        assertEquals("running", r.json().getJSONObject("download").getString("state"))
        assertEquals(409, call("POST", "/api/download", json = JSONObject().put("url", "https://x/y.bin")).code)
        assertEquals("running", call("GET", "/api/state").json().getJSONObject("download").getString("state"))
        backend.downloadState = WebDownload("done", 100, "", listOf("stm32f401.bin"))
        assertEquals("idle", call("POST", "/api/download/reset", json = JSONObject()).json().getJSONObject("download").getString("state"))
    }

    @Test fun aBadLinkIsRefusedWithAReason() {
        login()
        val r = call("POST", "/api/download", json = JSONObject().put("url", "http://example.com/a.bin"))
        assertEquals(400, r.code)
        assertEquals("link", r.json().getString("error"))
        assertEquals(400, call("POST", "/api/download", body = "not json".toByteArray()).code)
    }

    // --- flashing --------------------------------------------------------------------------------------------------

    @Test fun theFlashRequestGoesToTheAppAndItsProgressCanBeRead() {
        login()
        backend.stored["pico.uf2"] = ByteArray(300)
        val r = call("POST", "/api/flash", json = JSONObject().put("file", "pico.uf2").put("device", "/dev/bus/usb/001/005").put("mode", "RP2040"))
        assertEquals(r.body, 200, r.code)
        assertEquals(listOf("pico.uf2", "/dev/bus/usb/001/005", "RP2040", null), backend.flashRequest)
        val f = call("GET", "/api/flash").json().getJSONObject("flash")
        assertEquals("running", f.getString("state"))
        assertEquals(60, f.getInt("overall"))
        assertEquals("WRITE", f.getString("stage"))
        assertEquals("pico.uf2", f.getString("file"))
        assertEquals("only one write at a time", 409, call("POST", "/api/flash", json = JSONObject().put("file", "pico.uf2").put("device", "d").put("mode", "USB")).code)
    }

    @Test fun aStartAddressIsReadAsHex() {
        login()
        backend.stored["f.bin"] = ByteArray(10)
        call("POST", "/api/flash", json = JSONObject().put("file", "f.bin").put("device", "d").put("mode", "DFU").put("startAddress", "0x08008000"))
        assertEquals(0x08008000L, backend.flashRequest!![3])
        backend.resetFlash()
        assertEquals(400, call("POST", "/api/flash", json = JSONObject().put("file", "f.bin").put("device", "d").put("mode", "DFU").put("startAddress", "zz")).code)
    }

    @Test fun aFailureIsReportedWithItsReasonAndCanBeAcknowledged() {
        login()
        backend.flashState = WebFlash("done", "", 100, 0, false, "NO_ANSWER", "no answer", false, "f.bin", "d", "UART")
        val f = call("GET", "/api/flash").json().getJSONObject("flash")
        assertEquals("done", f.getString("state"))
        assertFalse(f.getBoolean("success"))
        assertEquals("NO_ANSWER", f.getString("failure"))
        assertEquals("no answer", f.getString("detail"))
        assertEquals("idle", call("POST", "/api/flash/reset", json = JSONObject()).json().getJSONObject("flash").getString("state"))
    }

    @Test fun theStateOfARunSurvivesAnotherLoginAsAfterAPageReload() {
        login()
        backend.stored["pico.uf2"] = ByteArray(300)
        call("POST", "/api/flash", json = JSONObject().put("file", "pico.uf2").put("device", "d").put("mode", "USB"))
        cookie = null
        login()
        val f = call("GET", "/api/state").json().getJSONObject("flash")
        assertEquals("running", f.getString("state"))
        assertEquals("USB", f.getString("mode"))
    }

    @Test fun theExperimentalFlagAndTheLogAreAvailable() {
        login()
        assertTrue(call("GET", "/api/experimental?file=a.bin&device=d&mode=UART").json().getBoolean("experimental"))
        assertFalse(call("GET", "/api/experimental?file=a.uf2&device=d&mode=RP2040").json().getBoolean("experimental"))
        assertEquals("line 1\nline 2", call("GET", "/api/log").json().getString("log"))
    }

    @Test fun theDashboardServesTheHostAndTheLiveNumbers() {
        login()
        assertEquals("Pixel", call("GET", "/api/dashboard").json().getJSONObject("host").getString("model"))
        val m = call("GET", "/api/metrics").json()
        assertEquals(12.5, m.getDouble("cpu"), 0.001)
        assertEquals(40, m.getLong("memAvailable"))
    }

    @Test fun logsAreListedReadAndClearedLikeInTheApp() {
        login()
        val sources = call("GET", "/api/logs").json().getJSONArray("sources")
        assertEquals(listOf("app", "flash"), (0 until sources.length()).map { sources.getJSONObject(it).getString("id") })
        assertEquals("[ts] flashed", call("GET", "/api/logs/read?id=flash").json().getString("text"))
        assertEquals(404, call("GET", "/api/logs/read?id=nope").code)
        assertEquals(403, call("POST", "/api/logs/clear", guard = false).code)
        assertTrue(call("POST", "/api/logs/clear").json().getBoolean("cleared"))
        assertEquals("", call("GET", "/api/logs/read?id=flash").json().getString("text"))
    }

    @Test fun settingsAreReadAndChangedLikeInTheApp() {
        login()
        assertEquals("klipper", call("GET", "/api/settings").json().getJSONObject("engine").getString("value"))
        assertEquals(200, call("POST", "/api/settings", json = JSONObject().put("key", "cameraRotation").put("value", 90)).code)
        assertEquals("cameraRotation", control.applied.last().first)
        assertEquals(90, control.applied.last().second.getInt("value"))
        assertEquals("the camera permission is asked on the phone", 409, call("POST", "/api/settings", json = JSONObject().put("key", "cameraEnabled").put("value", true)).code)
        assertEquals(400, call("POST", "/api/settings", json = JSONObject().put("key", "nope")).code)
        assertEquals(403, call("POST", "/api/settings", json = JSONObject().put("key", "engine").put("value", "kalico"), guard = false).code)
    }

    @Test fun printersCanBeStartedAndStopped() {
        login()
        assertEquals(200, call("POST", "/api/control", json = JSONObject().put("id", "abc").put("action", "start")).code)
        assertEquals(200, call("POST", "/api/control", json = JSONObject().put("action", "stop")).code)
        assertEquals(listOf<Pair<String?, String>>("abc" to "start", null to "stop"), control.controls)
        assertEquals("no free slot", 409, call("POST", "/api/control", json = JSONObject().put("id", "full").put("action", "start")).code)
    }

    @Test fun aPrinterCanBeAddedWithTheAppsOwnOptions() {
        login()
        val info = call("GET", "/api/printers/new").json()
        assertEquals("a.cfg", info.getString("default"))
        assertEquals(200, call("POST", "/api/printers", json = JSONObject().put("name", " Voron ").put("config", "b.cfg").put("autostart", true)).code)
        assertEquals(listOf<Any>("Voron", "b.cfg", true), control.added.single())
        assertEquals(400, call("POST", "/api/printers", json = JSONObject().put("config", "../../etc/passwd")).code)
        assertEquals(409, call("POST", "/api/printers", json = JSONObject().put("config", "full")).code)
    }

    @Test fun theCameraStreamAndTapToFocusGoThroughTheApp() {
        login()
        assertTrue(call("GET", "/api/camera").json().getBoolean("focusSupported"))
        val stream = call("GET", "/api/camera/stream")
        assertEquals(200, stream.code)
        assertTrue(stream.headers["Content-Type"]!!.first().startsWith("multipart/x-mixed-replace"))
        assertTrue(stream.body.contains("--camera-frame"))
        assertEquals(200, call("POST", "/api/camera/focus", json = JSONObject().put("x", 0.25).put("y", 0.75)).code)
        assertEquals(0.25f to 0.75f, control.focus)
        assertEquals("a point outside the frame", 400, call("POST", "/api/camera/focus", json = JSONObject().put("x", 1.5).put("y", 0.5)).code)
        assertEquals(200, call("POST", "/api/camera/focus/reset", json = JSONObject()).code)
        assertTrue(control.focusReset)
        control.cameraOn = false
        assertEquals(503, call("GET", "/api/camera/stream").code)
    }

    @Test fun unknownRoutesAndMethodsAreRefused() {
        login()
        assertEquals(404, call("GET", "/api/nothing").code)
        assertEquals(404, call("DELETE", "/api/state").code)
        assertEquals(405, call("POST", "/index.html", json = JSONObject()).code)
    }
}

class FirmwareWebRulesTest {
    @Test fun pinsAreSixDigitsAndComparedExactly() {
        repeat(50) { assertTrue(WebPin.isValid(WebPin.generate())) }
        assertTrue(WebPin.matches("012345", "012345"))
        assertFalse(WebPin.matches("012345", "012346"))
        assertFalse(WebPin.isValid("12345")); assertFalse(WebPin.isValid("1234567")); assertFalse(WebPin.isValid("12 456"))
    }

    @Test fun theLimiterLocksAfterTheFifthFailureAndOpensAgain() {
        var now = 1_000L
        val l = LoginLimiter(maxFails = 5, lockMs = 60_000, clock = { now })
        repeat(4) { l.failed("a") }
        assertTrue(l.allowed("a"))
        l.failed("a")
        assertFalse(l.allowed("a"))
        assertTrue("another address is not affected", l.allowed("b"))
        assertEquals(60, l.secondsLeft("a"))
        now += 60_001
        assertTrue(l.allowed("a"))
        l.failed("a")
        assertTrue("the count starts again", l.allowed("a"))
    }

    @Test fun aSuccessForgetsEarlierFailures() {
        val l = LoginLimiter(maxFails = 3)
        l.failed("a"); l.failed("a"); l.succeeded("a"); l.failed("a"); l.failed("a")
        assertTrue(l.allowed("a"))
    }

    @Test fun sessionsAreRandomRevocableAndLimited() {
        val s = WebSessions(max = 3)
        val a = s.issue(); val b = s.issue()
        assertTrue(a != b && a.length == 48)
        assertTrue(s.valid(a))
        assertFalse(s.valid(null)); assertFalse(s.valid("nope"))
        s.revoke(a); assertFalse(s.valid(a))
        s.issue(); s.issue(); s.issue()
        assertFalse("the oldest is dropped", s.valid(b))
        s.clear(); assertFalse(s.valid(s.issue().reversed()))
    }

    @Test fun cookiesAreReadByName() {
        assertEquals("abc", WebRequests.cookie("x=1; kp_fw=abc; y=2", "kp_fw"))
        assertNull(WebRequests.cookie("x=1", "kp_fw"))
        assertNull(WebRequests.cookie(null, "kp_fw"))
        assertNull(WebRequests.cookie("kp_fw=", "kp_fw"))
        assertNotNull(WebRequests.mimeFor("app.js"))
    }
}

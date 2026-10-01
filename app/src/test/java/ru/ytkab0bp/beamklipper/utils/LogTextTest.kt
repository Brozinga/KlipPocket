package ru.ytkab0bp.beamklipper.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogTextTest {
    @Test
    fun `a short log is shown whole`() {
        assertEquals("hello", LogText.tail("hello", 100))
    }

    @Test
    fun `a long log keeps its end and says it was cut`() {
        val text = "A".repeat(3000) + "END"
        val out = LogText.tail(text, 2000)
        assertTrue(out.startsWith("…(início cortado, mostrando os últimos 2 KB)…\n"))
        assertTrue(out.endsWith("END"))
        assertEquals(2000, out.substringAfter("\n").length)
    }

    @Test
    fun `a log exactly at the limit is not cut`() {
        val text = "x".repeat(100)
        assertEquals(text, LogText.tail(text, 100))
    }

    @Test
    fun `a missing instance log explains that the service did not run`() {
        val out = LogText.describeInstanceLog("klippy.log", exists = false, text = "")
        assertTrue(out.contains("klippy.log"))
        assertTrue(out.contains("ainda não foi criado"))
    }

    @Test
    fun `an empty instance log says it is empty`() {
        assertEquals("(moonraker.log está vazio)", LogText.describeInstanceLog("moonraker.log", true, "  \n"))
    }

    @Test
    fun `an instance log with content is shown`() {
        assertEquals("line1\nline2", LogText.describeInstanceLog("obico.log", true, "line1\nline2"))
    }

    @Test
    fun `a huge instance log is cut to its tail`() {
        val text = "x".repeat(LogText.MAX_CHARS + 500)
        assertTrue(LogText.describeInstanceLog("klippy.log", true, text).startsWith("…(início cortado"))
    }

    @Test
    fun `the combined diagnostic is capped`() {
        val text = "a".repeat(300) + "tail"
        val out = LogText.capCombined(text, 100)
        assertEquals(100, out.length)
        assertTrue(out.endsWith("tail"))
        assertEquals("short", LogText.capCombined("short", 100))
    }
}

class CrashReportTest {
    @Test
    fun `the report lists the process, device and stack`() {
        val text = CrashReport.format(
            timeMs = 1700000000000, process = "com.brozinga.klippocket:camera", thread = "main",
            manufacturer = "samsung", model = "SM-G975F", sdk = 31,
            abis = listOf("arm64-v8a", "armeabi-v7a"), stackTrace = "java.lang.RuntimeException: boom\n\tat A.b(A.kt:1)"
        )
        val lines = text.lines()
        assertEquals("time=1700000000000", lines[0])
        assertEquals("process=com.brozinga.klippocket:camera", lines[1])
        assertEquals("thread=main", lines[2])
        assertEquals("device=samsung SM-G975F sdk=31", lines[3])
        assertEquals("abi=arm64-v8a,armeabi-v7a", lines[4])
        assertEquals("", lines[5])
        assertEquals("java.lang.RuntimeException: boom", lines[6])
        assertTrue(text.endsWith("at A.b(A.kt:1)"))
    }
}

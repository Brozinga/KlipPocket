package ru.ytkab0bp.beamklipper.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WebPortFileTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `the port file lives directly under the app's files dir`() {
        assertEquals(File(tmp.root, "web_port"), WebPortFile.path(tmp.root))
    }

    @Test
    fun `writing saves the port as plain text`() {
        WebPortFile.write(tmp.root, 4410)
        assertEquals("4410", WebPortFile.path(tmp.root).readText())
    }

    @Test
    fun `writing again replaces the previous port`() {
        WebPortFile.write(tmp.root, 4408)
        WebPortFile.write(tmp.root, 4409)
        assertEquals("4409", WebPortFile.path(tmp.root).readText())
    }

    @Test
    fun `a write that cannot happen does not throw`() {
        // filesDir does not exist, so the write fails silently.
        WebPortFile.write(File(tmp.root, "missing/deeper"), 4409)
        assertFalse(File(tmp.root, "missing/deeper/web_port").exists())
    }
}

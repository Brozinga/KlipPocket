package ru.ytkab0bp.beamklipper.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MoonrakerConfigTest {
    // The template the app really ships, so a renamed placeholder breaks a test
    private val template = File("src/main/assets/moonraker/default.conf").readText()

    private fun render(port: Int = 7125) = MoonrakerConfig.render(
        template, "/data/instance/x/klippy_uds", port, "/data/instance/x/frames", "/data/public/timelapses"
    )

    @Test
    fun `the bundled template has every placeholder the renderer fills`() {
        listOf("\${KLIPPY_UDS}", "\${MOONRAKER_PORT}", "\${TIMELAPSE_FRAME_PATH}", "\${TIMELAPSE_OUTPUT}")
            .forEach { assertTrue("$it missing from default.conf", template.contains(it)) }
    }

    @Test
    fun `rendering leaves no placeholder behind`() {
        assertFalse(render().contains("\${"))
    }

    @Test
    fun `rendering writes the instance's paths and port`() {
        val out = render(7127)
        assertTrue(out.contains("port: 7127"))
        assertTrue(out.contains("klippy_uds_address: /data/instance/x/klippy_uds"))
        assertTrue(out.contains("output_path: /data/public/timelapses"))
        assertTrue(out.contains("frame_path: /data/instance/x/frames"))
    }

    @Test
    fun `the rendered port can be read back`() {
        assertEquals(7127, MoonrakerConfig.portOf(render(7127)))
    }

    @Test
    fun `no port in the config reads as null`() {
        assertNull(MoonrakerConfig.portOf("[server]\nhost: 0.0.0.0\n"))
    }

    @Test
    fun `the first port starts at 7125`() {
        assertEquals(7125, MoonrakerConfig.nextFreePort(emptySet()))
    }

    @Test
    fun `the next free port skips the ones in use`() {
        assertEquals(7126, MoonrakerConfig.nextFreePort(setOf(7125)))
        assertEquals(7128, MoonrakerConfig.nextFreePort(setOf(7125, 7126, 7127)))
    }

    @Test
    fun `a gap left by a deleted instance is reused`() {
        assertEquals(7126, MoonrakerConfig.nextFreePort(setOf(7125, 7127)))
    }

    @Test
    fun `ports outside the range do not matter`() {
        assertEquals(7125, MoonrakerConfig.nextFreePort(setOf(8000, 9000)))
    }
}

package ru.ytkab0bp.beamklipper.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterCfgTest {
    private val gcodes = "/data/instance/abc/public/gcodes"
    private val block = "[virtual_sdcard]\npath: $gcodes"

    private fun count(text: String, needle: String) = text.split(needle).size - 1

    @Test
    fun `a config without the section gets one at the end`() {
        val out = PrinterCfg.withVirtualSdcard("[mcu]\nserial: /dev/x\n", gcodes)
        assertEquals("[mcu]\nserial: /dev/x\n\n$block\n", out)
    }

    @Test
    fun `a stale path is replaced`() {
        val src = "[mcu]\nserial: /dev/x\n\n[virtual_sdcard]\npath: /old/uuid/gcodes\n\n[fan]\npin: PA1\n"
        val out = PrinterCfg.withVirtualSdcard(src, gcodes)
        assertEquals(1, count(out, "[virtual_sdcard]"))
        assertTrue(out.contains("path: $gcodes"))
        assertTrue(!out.contains("/old/uuid"))
        assertTrue(out.contains("[fan]\npin: PA1"))
        assertTrue(out.contains("[mcu]\nserial: /dev/x"))
    }

    @Test
    fun `the section header is matched case insensitively`() {
        val out = PrinterCfg.withVirtualSdcard("[Virtual_SDCard]\npath: /x\n", gcodes)
        assertEquals(1, count(out.lowercase(), "[virtual_sdcard]"))
        assertTrue(out.contains("path: $gcodes"))
    }

    @Test
    fun `the section goes above klipper's autosave block`() {
        val src = "[mcu]\nserial: /dev/x\n\n#*# <---------------------- SAVE_CONFIG ---------------------->\n#*# [extruder]\n#*# control = pid\n"
        val out = PrinterCfg.withVirtualSdcard(src, gcodes)
        assertTrue(out.indexOf("[virtual_sdcard]") < out.indexOf("#*#"))
        assertTrue(out.endsWith("#*# control = pid\n"))
        assertTrue(out.contains("$block\n\n\n#*# <----"))
    }

    @Test
    fun `an existing section above the autosave block is replaced there`() {
        val src = "[virtual_sdcard]\npath: /old\n\n#*# <--- SAVE_CONFIG --->\n#*# [extruder]\n"
        val out = PrinterCfg.withVirtualSdcard(src, gcodes)
        assertEquals(1, count(out, "[virtual_sdcard]"))
        assertTrue(out.indexOf("[virtual_sdcard]") < out.indexOf("#*#"))
    }

    @Test
    fun `comments and other sections after the old section are kept`() {
        val src = "[virtual_sdcard]\npath: /old\n# keep me\n[heater_bed]\nheater_pin: PA0\n"
        val out = PrinterCfg.withVirtualSdcard(src, gcodes)
        assertTrue(out.contains("# keep me"))
        assertTrue(out.contains("[heater_bed]\nheater_pin: PA0"))
    }

    @Test
    fun `an indented continuation of the old section is dropped with it`() {
        val src = "[virtual_sdcard]\npath: /old\n  /continued\n\n[fan]\npin: PA1\n"
        val out = PrinterCfg.withVirtualSdcard(src, gcodes)
        assertTrue(!out.contains("/continued"))
        assertTrue(out.contains("[fan]"))
    }

    @Test
    fun `running it twice changes nothing more`() {
        val once = PrinterCfg.withVirtualSdcard("[mcu]\nserial: /dev/x\n\n[virtual_sdcard]\npath: /old\n", gcodes)
        assertEquals(once, PrinterCfg.withVirtualSdcard(once, gcodes))
    }

    @Test
    fun `an empty file becomes just the section`() {
        assertEquals("\n\n$block\n", PrinterCfg.withVirtualSdcard("", gcodes))
    }
}

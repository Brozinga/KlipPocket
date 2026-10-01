package ru.ytkab0bp.beamklipper.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentIdsTest {
    private val root = "/data/instance/abc/public"

    @Test
    fun `a file inside the instance gets a relative id`() {
        assertEquals("instance:abc:config/printer.cfg", DocumentIds.format("abc", root, "$root/config/printer.cfg"))
    }

    @Test
    fun `the instance's root has an empty path`() {
        assertEquals("instance:abc:", DocumentIds.format("abc", root, root))
    }

    @Test
    fun `a root with a trailing slash is handled`() {
        assertEquals("instance:abc:gcodes/a.gcode", DocumentIds.format("abc", "$root/", "$root/gcodes/a.gcode"))
    }

    @Test
    fun `an id is split into the instance and the path`() {
        val parsed = DocumentIds.parse("instance:abc:config/printer.cfg")!!
        assertEquals("abc", parsed.instanceId)
        assertEquals("config/printer.cfg", parsed.relativePath)
    }

    @Test
    fun `the root id has an empty path`() {
        assertEquals("", DocumentIds.parse("instance:abc:")!!.relativePath)
    }

    @Test
    fun `a path may contain colons`() {
        assertEquals("gcodes/a:b.gcode", DocumentIds.parse("instance:abc:gcodes/a:b.gcode")!!.relativePath)
    }

    @Test
    fun `ids that are not ours or are broken do not parse`() {
        assertNull(DocumentIds.parse("other:abc:file"))
        assertNull(DocumentIds.parse("instance:abc"))
        assertNull(DocumentIds.parse(""))
    }

    @Test
    fun `an id survives a round trip`() {
        val id = DocumentIds.format("abc", root, "$root/timelapses/video.mp4")
        val parsed = DocumentIds.parse(id)!!
        assertEquals("abc", parsed.instanceId)
        assertEquals("timelapses/video.mp4", parsed.relativePath)
    }
}

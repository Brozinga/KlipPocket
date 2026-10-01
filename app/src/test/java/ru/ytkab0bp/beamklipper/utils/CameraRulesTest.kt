package ru.ytkab0bp.beamklipper.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraRulesTest {
    @Test
    fun `a pinned camera is used when it still exists`() {
        assertEquals("2", CameraRules.resolveId(listOf("0", "1", "2"), "2") { it == "1" })
    }

    @Test
    fun `a pinned camera that disappeared falls back to the first`() {
        assertEquals("0", CameraRules.resolveId(listOf("0", "1"), "5") { it == "1" })
    }

    @Test
    fun `with nothing pinned a usb webcam is preferred`() {
        assertEquals("3", CameraRules.resolveId(listOf("0", "1", "3"), null) { it == "3" })
    }

    @Test
    fun `with nothing pinned and no usb webcam the first camera is used`() {
        assertEquals("0", CameraRules.resolveId(listOf("0", "1"), null) { false })
    }

    @Test
    fun `a pinned camera wins over a usb webcam`() {
        assertEquals("0", CameraRules.resolveId(listOf("0", "3"), "0") { it == "3" })
    }

    @Test
    fun `no cameras means none is chosen`() {
        assertNull(CameraRules.resolveId(emptyList(), null) { true })
        assertNull(CameraRules.resolveId(emptyList(), "1") { true })
    }

    @Test
    fun `zoom cycles through the steps and wraps around`() {
        val options = listOf(1f, 2f, 4f)
        assertEquals(2f, CameraRules.nextZoom(1f, options), 0f)
        assertEquals(4f, CameraRules.nextZoom(2f, options), 0f)
        assertEquals(1f, CameraRules.nextZoom(4f, options), 0f)
    }

    @Test
    fun `a saved zoom between steps snaps down before advancing`() {
        assertEquals(4f, CameraRules.nextZoom(2.6f, listOf(1f, 2f, 4f)), 0f)
    }

    @Test
    fun `a camera with a single zoom step stays where it is`() {
        assertEquals(1f, CameraRules.nextZoom(1f, listOf(1f)), 0f)
    }

    @Test
    fun `the 35mm equivalent of a typical phone main camera`() {
        // 4.25mm lens on a 1/2.55" sensor (5.6 x 4.2mm) is about 26mm
        assertEquals(26, CameraRules.equivalentFocalLength35mm(4.25f, 5.6f, 4.2f))
    }

    @Test
    fun `a full frame sensor has the same focal length`() {
        assertEquals(50, CameraRules.equivalentFocalLength35mm(50f, 36f, 24f))
    }

    @Test
    fun `a sensor without a size has no equivalent`() {
        assertNull(CameraRules.equivalentFocalLength35mm(4f, 0f, 0f))
    }
}

class FocusRegionTest {
    // A 4000x3000 sensor: the metering square is 12% of the short side = 360
    private fun region(zoom: Float = 1f, rotation: Int = 0, nx: Float, ny: Float) =
        FocusRegion.compute(0, 0, 4000, 3000, zoom, rotation, nx, ny)

    @Test
    fun `the centre of the frame is the centre of the sensor`() {
        val r = region(nx = 0.5f, ny = 0.5f)
        assertEquals(360, r.side)
        assertEquals(2000 - 180, r.left)
        assertEquals(1500 - 180, r.top)
    }

    @Test
    fun `the square is a fixed fraction of the short side`() {
        assertEquals((3000 * FocusRegion.REGION_FRACTION).toInt(), region(nx = .5f, ny = .5f).side)
    }

    @Test
    fun `a tap near a corner keeps the square inside the sensor`() {
        val tl = region(nx = 0f, ny = 0f)
        assertEquals(0, tl.left)
        assertEquals(0, tl.top)
        val br = region(nx = 1f, ny = 1f)
        assertEquals(4000 - 360, br.left)
        assertEquals(3000 - 360, br.top)
    }

    @Test
    fun `taps outside the frame are clamped`() {
        val r = region(nx = -3f, ny = 9f)
        assertEquals(0, r.left)
        assertEquals(3000 - 360, r.top)
    }

    @Test
    fun `zoom narrows the area a tap can reach`() {
        // at 2x only the centre half (1000..3000 x 750..2250) is visible
        val topLeft = region(zoom = 2f, nx = 0f, ny = 0f)
        assertEquals(1000 - 180, topLeft.left)
        assertEquals(750 - 180, topLeft.top)
    }

    @Test
    fun `a zoom below 1x is treated as 1x`() {
        assertEquals(region(zoom = 1f, nx = .2f, ny = .8f), region(zoom = 0.3f, nx = .2f, ny = .8f))
    }

    @Test
    fun `a frame served rotated 90 degrees maps taps back to the sensor`() {
        // served frame rotated clockwise 90: its top-left is the sensor's bottom-left
        val r = region(rotation = 90, nx = 0f, ny = 0f)
        assertEquals(0, r.left)
        assertEquals(3000 - 360, r.top)
    }

    @Test
    fun `a frame served rotated 180 degrees is mirrored`() {
        val r = region(rotation = 180, nx = 0f, ny = 0f)
        assertEquals(4000 - 360, r.left)
        assertEquals(3000 - 360, r.top)
    }

    @Test
    fun `a frame served rotated 270 degrees maps taps back to the sensor`() {
        val r = region(rotation = 270, nx = 0f, ny = 0f)
        assertEquals(4000 - 360, r.left)
        assertEquals(0, r.top)
    }

    @Test
    fun `a tiny sensor still gets a usable square`() {
        assertEquals(8, FocusRegion.compute(0, 0, 40, 30, 1f, 0, .5f, .5f).side)
    }

    @Test
    fun `an offset active array is respected`() {
        val r = FocusRegion.compute(100, 200, 4100, 3200, 1f, 0, 0.5f, 0.5f)
        assertEquals(100 + 2000 - 180, r.left)
        assertEquals(200 + 1500 - 180, r.top)
    }
}

class JpegQualityControllerTest {
    private fun feed(c: JpegQualityController, ms: Long, times: Int) = repeat(times) { c.onWriteTiming(ms) }

    @Test
    fun `it starts at the base quality`() {
        assertEquals(75, JpegQualityController().quality)
    }

    @Test
    fun `fast writes keep the base quality`() {
        val c = JpegQualityController()
        feed(c, 5, 50)
        assertEquals(75, c.quality)
    }

    @Test
    fun `a single slow write does not change anything`() {
        val c = JpegQualityController()
        c.onWriteTiming(500)
        assertEquals(75, c.quality)
    }

    @Test
    fun `sustained slow writes lower the quality`() {
        val c = JpegQualityController()
        feed(c, 200, 30)
        assertTrue(c.quality < 75)
    }

    @Test
    fun `quality never goes below the minimum`() {
        val c = JpegQualityController()
        feed(c, 1000, 500)
        assertEquals(35, c.quality)
    }

    @Test
    fun `quality steps down by ten`() {
        val c = JpegQualityController()
        var last = c.quality
        val seen = mutableListOf<Int>()
        repeat(200) {
            c.onWriteTiming(1000)
            if (c.quality != last) { seen += last - c.quality; last = c.quality }
        }
        assertTrue(seen.dropLast(1).all { it == 10 })
    }

    @Test
    fun `recovery raises the quality back gradually up to the base`() {
        val c = JpegQualityController()
        feed(c, 1000, 200)
        assertEquals(35, c.quality)
        feed(c, 1, 200)
        assertEquals(75, c.quality)
    }

    @Test
    fun `quality comes back in steps of five while it drops in steps of ten`() {
        val c = JpegQualityController()
        val drops = mutableListOf<Int>()
        var last = c.quality
        repeat(100) { c.onWriteTiming(1000); if (c.quality != last) { drops += last - c.quality; last = c.quality } }
        val rises = mutableListOf<Int>()
        repeat(200) { c.onWriteTiming(1); if (c.quality != last) { rises += c.quality - last; last = c.quality } }
        assertTrue(drops.isNotEmpty() && drops.all { it == 10 })
        assertTrue(rises.isNotEmpty() && rises.all { it == 5 })
    }

    @Test
    fun `a new camera session starts at the base quality again`() {
        val c = JpegQualityController()
        feed(c, 1000, 100)
        assertTrue(c.quality < 75)
        c.reset()
        assertEquals(75, c.quality)
        feed(c, 5, 20)
        assertEquals(75, c.quality)
    }

    @Test
    fun `the latest quality is returned for the next frame`() {
        val c = JpegQualityController()
        var returned = 75
        repeat(50) { returned = c.onWriteTiming(1000) }
        assertEquals(c.quality, returned)
    }
}

class CameraHttpTest {
    @Test
    fun `a snapshot path is recognised`() {
        assertTrue(CameraHttp.isSnapshotRequest("GET /snapshot HTTP/1.1"))
        assertTrue(CameraHttp.isSnapshotRequest("GET /snapshot?cb=123 HTTP/1.0"))
    }

    @Test
    fun `the stream path and everything else is not a snapshot`() {
        assertFalse(CameraHttp.isSnapshotRequest("GET / HTTP/1.1"))
        assertFalse(CameraHttp.isSnapshotRequest("GET /stream HTTP/1.1"))
        assertFalse(CameraHttp.isSnapshotRequest("POST /snapshot HTTP/1.1"))
    }

    @Test
    fun `a missing or malformed request line gets the stream`() {
        assertFalse(CameraHttp.isSnapshotRequest(null))
        assertFalse(CameraHttp.isSnapshotRequest(""))
        assertFalse(CameraHttp.isSnapshotRequest("hello"))
    }

    @Test
    fun `the stream is multipart with cors and no caching`() {
        val h = CameraHttp.STREAM_HEADERS
        assertTrue(h.startsWith("HTTP/1.0 200 OK\r\n"))
        assertTrue(h.contains("Access-Control-Allow-Origin: *\r\n"))
        assertTrue(h.contains("Content-Type: multipart/x-mixed-replace; boundary=camera-frame\r\n"))
        assertTrue(h.contains("Cache-Control: no-cache, private\r\n"))
        assertTrue(h.endsWith("\r\n\r\n"))
    }

    @Test
    fun `a snapshot is a plain jpeg with its length`() {
        val h = CameraHttp.snapshotHeaders(1234)
        assertTrue(h.contains("Content-Type: image/jpeg\r\n"))
        assertTrue(h.contains("Content-Length: 1234\r\n"))
        assertTrue(h.contains("Access-Control-Allow-Origin: *\r\n"))
        assertFalse(h.contains("multipart"))
        assertTrue(h.endsWith("\r\n\r\n"))
    }

    @Test
    fun `each stream frame starts with the boundary`() {
        val part = CameraHttp.framePartHeader(77)
        assertEquals("--camera-frame\r\nContent-Type: image/jpeg\r\nContent-Length: 77\r\n\r\n", part)
    }
}

package ru.ytkab0bp.beamklipper.service.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FfmpegCommandTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `a timelapse command is parsed`() {
        val cmd = FfmpegCommand.parse(
            "ffmpeg -r 30 -i '/tmp/frames/frame%6d.jpg' -vf 'transpose=1' -c:v libx264 '/tmp/out/video.mp4'"
        )
        assertEquals(30, cmd.fps)
        assertEquals("/tmp/frames/frame%6d.jpg", cmd.inputPath)
        assertEquals("/tmp/out/video.mp4", cmd.outputPath)
        assertEquals("transpose=1", cmd.filter)
        assertTrue(cmd.isTimelapse)
        assertTrue(cmd.isComplete)
    }

    @Test
    fun `a single image command has no fps`() {
        val cmd = FfmpegCommand.parse("ffmpeg -i '/tmp/in.jpg' -vf 'hflip' '/tmp/out.jpg'")
        assertNull(cmd.fps)
        assertFalse(cmd.isTimelapse)
        assertEquals("/tmp/in.jpg", cmd.inputPath)
        assertEquals("/tmp/out.jpg", cmd.outputPath)
    }

    @Test
    fun `a command without a filter has none`() {
        assertNull(FfmpegCommand.parse("ffmpeg -i '/a.jpg' '/b.jpg'").filter)
    }

    @Test
    fun `a command without paths is incomplete`() {
        assertFalse(FfmpegCommand.parse("ffmpeg -r 30").isComplete)
        assertFalse(FfmpegCommand.parse("").isComplete)
    }

    @Test
    fun `transpose filters become rotations and flips`() {
        assertEquals(listOf(FilterOp.Rotate(-90f), FilterOp.FlipVertical), VideoFilters.parse("transpose=0"))
        assertEquals(listOf(FilterOp.Rotate(90f)), VideoFilters.parse("transpose=1"))
        assertEquals(listOf(FilterOp.Rotate(-90f)), VideoFilters.parse("transpose=2"))
        assertEquals(listOf(FilterOp.Rotate(90f), FilterOp.FlipVertical), VideoFilters.parse("transpose=3"))
    }

    @Test
    fun `flip filters are understood`() {
        assertEquals(listOf(FilterOp.FlipHorizontal, FilterOp.FlipVertical), VideoFilters.parse("hflip, vflip"))
    }

    @Test
    fun `rotate takes radians`() {
        val op = VideoFilters.parse("rotate=3.14159265").single() as FilterOp.Rotate
        assertEquals(180f, op.degrees, 0.01f)
        assertEquals(0f, (VideoFilters.parse("rotate=abc").single() as FilterOp.Rotate).degrees, 0f)
    }

    @Test
    fun `unknown filters and no filter do nothing`() {
        assertTrue(VideoFilters.parse(null).isEmpty())
        assertTrue(VideoFilters.parse("scale=640:480,eq=brightness=0.1").isEmpty())
    }

    @Test
    fun `filters keep the order they were given in`() {
        assertEquals(
            listOf(FilterOp.FlipHorizontal, FilterOp.Rotate(90f)),
            VideoFilters.parse("hflip,transpose=1")
        )
    }

    private fun touch(name: String) = tmp.newFile(name)

    @Test
    fun `a printf sequence is expanded in numeric order`() {
        listOf("frame000010.jpg", "frame000002.jpg", "frame000001.jpg", "other.jpg", "frame000003.png")
            .forEach { touch(it) }
        val files = FrameSequence.expand(File(tmp.root, "frame%6d.jpg").path)
        assertEquals(listOf("frame000001.jpg", "frame000002.jpg", "frame000010.jpg"), files.map { it.name })
    }

    @Test
    fun `a zero padded printf sequence is expanded too`() {
        listOf("img_001.jpg", "img_020.jpg", "img_003.jpg").forEach { touch(it) }
        assertEquals(
            listOf("img_001.jpg", "img_003.jpg", "img_020.jpg"),
            FrameSequence.expand(File(tmp.root, "img_%03d.jpg").path).map { it.name }
        )
    }

    @Test
    fun `a wildcard is expanded by name`() {
        listOf("b.jpg", "a.jpg", "c.png").forEach { touch(it) }
        assertEquals(listOf("a.jpg", "b.jpg"), FrameSequence.expand(File(tmp.root, "*.jpg").path).map { it.name })
        assertEquals(listOf("a.jpg", "b.jpg"), FrameSequence.expand(File(tmp.root, "?.jpg").path).map { it.name })
    }

    @Test
    fun `a plain path is returned only if it exists`() {
        val f = touch("only.jpg")
        assertEquals(listOf(f), FrameSequence.expand(f.path))
        assertTrue(FrameSequence.expand(File(tmp.root, "missing.jpg").path).isEmpty())
    }

    @Test
    fun `a sequence in a missing folder is empty`() {
        assertTrue(FrameSequence.expand("/no/such/dir/frame%6d.jpg").isEmpty())
    }

    @Test
    fun `small frames are kept and rounded to even sizes`() {
        assertEquals(640 to 480, EncoderSize.fit(640, 480))
        assertEquals(642 to 480, EncoderSize.fit(641, 479))
    }

    @Test
    fun `large frames are scaled down into 1280x720`() {
        assertEquals(1280 to 720, EncoderSize.fit(1920, 1080))
        assertEquals(1280 to 720, EncoderSize.fit(2560, 1440))
    }

    @Test
    fun `a tall frame is limited by its height`() {
        val (w, h) = EncoderSize.fit(1080, 1920)
        assertEquals(720, h)
        assertEquals(406 to 720, w to h)  // 405 rounds up to the next even size
    }

    @Test
    fun `encoder dimensions are always even`() {
        for ((w, h) in listOf(1919 to 1079, 333 to 777, 4000 to 3001, 1281 to 721)) {
            val (ew, eh) = EncoderSize.fit(w, h)
            assertEquals("$w x $h", 0, ew % 2)
            assertEquals("$w x $h", 0, eh % 2)
        }
    }
}

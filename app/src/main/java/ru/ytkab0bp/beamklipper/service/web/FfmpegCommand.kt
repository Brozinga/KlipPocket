package ru.ytkab0bp.beamklipper.service.web

import java.io.File

// The one ffmpeg command line moonraker-timelapse (and Moonraker's thumbnail
// helper) send to the app in place of a bundled ffmpeg binary. Only the few
// options they use are understood.
data class FfmpegCommand(
    val fps: Int?,
    val inputPath: String,
    val outputPath: String,
    val filter: String?
) {
    // -r N means a frame sequence to encode; without it, a single image.
    val isTimelapse: Boolean get() = fps != null

    val isComplete: Boolean get() = inputPath.isNotBlank() && outputPath.isNotBlank()

    companion object {
        private val FPS = Regex("""-r\s+(\d+)""")
        private val INPUT = Regex("""-i\s+'([^']+)'""")
        private val FILTER = Regex("""-vf\s+'([^']+)'""")
        private val QUOTED = Regex("""'([^']+)'""")

        fun parse(cmd: String): FfmpegCommand = FfmpegCommand(
            fps = FPS.find(cmd)?.groupValues?.get(1)?.toInt(),
            inputPath = INPUT.find(cmd)?.groupValues?.get(1) ?: "",
            // The output is the last quoted path of the command.
            outputPath = QUOTED.findAll(cmd).map { it.groupValues[1] }.lastOrNull() ?: "",
            filter = FILTER.find(cmd)?.groupValues?.get(1)
        )
    }
}

// The -vf filters that matter for a camera mounted sideways or mirrored.
sealed class FilterOp {
    data class Rotate(val degrees: Float) : FilterOp()
    object FlipHorizontal : FilterOp()
    object FlipVertical : FilterOp()
}

object VideoFilters {
    fun parse(filter: String?): List<FilterOp> {
        if (filter == null) return emptyList()
        val ops = ArrayList<FilterOp>()
        for (f in filter.split(",")) {
            val t = f.trim()
            when {
                t == "transpose=0" -> { ops += FilterOp.Rotate(-90f); ops += FilterOp.FlipVertical }
                t == "transpose=1" -> ops += FilterOp.Rotate(90f)
                t == "transpose=2" -> ops += FilterOp.Rotate(-90f)
                t == "transpose=3" -> { ops += FilterOp.Rotate(90f); ops += FilterOp.FlipVertical }
                t == "hflip" -> ops += FilterOp.FlipHorizontal
                t == "vflip" -> ops += FilterOp.FlipVertical
                t.startsWith("rotate=") -> {
                    val radians = t.substringAfter("=").toFloatOrNull() ?: 0f
                    ops += FilterOp.Rotate(Math.toDegrees(radians.toDouble()).toFloat())
                }
            }
        }
        return ops
    }
}

object FrameSequence {
    // ffmpeg's image2 sequence pattern: %d, %6d, %06d ...
    private val PRINTF_INT = Regex("%0?\\d*d")

    // moonraker-timelapse asks for "frameNNNNNN.jpg" as "frame%6d.jpg", which
    // is a printf-style sequence, not a shell wildcard: match the frames by
    // number and keep them in numeric order. Plain paths and * / ? wildcards
    // are supported too.
    fun expand(path: String): List<File> {
        val file = File(path)
        val printf = PRINTF_INT.find(file.name)
        if (printf != null) {
            val name = file.name
            val parent = file.parentFile ?: File("/")
            val pattern = Regex(
                Regex.escape(name.substring(0, printf.range.first)) + "(\\d+)" +
                    Regex.escape(name.substring(printf.range.last + 1))
            )
            return (parent.listFiles() ?: emptyArray())
                .filter { it.isFile }
                .mapNotNull { f -> pattern.matchEntire(f.name)?.let { it.groupValues[1].toLong() to f } }
                .sortedBy { it.first }
                .map { it.second }
        }
        if (!path.contains("*") && !path.contains("?")) {
            return if (file.exists()) listOf(file) else emptyList()
        }
        val parent = file.parentFile ?: File("/")
        val namePattern = file.name
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")
            .toRegex()
        return (parent.listFiles() ?: emptyArray())
            .filter { namePattern.matches(it.name) && it.isFile }
            .sortedBy { it.name }
    }
}

object EncoderSize {
    const val MAX_WIDTH = 1280
    const val MAX_HEIGHT = 720

    // H.264 wants even dimensions; never upscale, cap at 1280x720.
    fun fit(width: Int, height: Int): Pair<Int, Int> {
        val scale = minOf(MAX_WIDTH.toFloat() / width, MAX_HEIGHT.toFloat() / height, 1f)
        return Math.round(width * scale / 2f) * 2 to Math.round(height * scale / 2f) * 2
    }
}

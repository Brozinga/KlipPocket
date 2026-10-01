package ru.ytkab0bp.beamklipper.service

import java.util.regex.Pattern

// Rules for the moonraker.conf each instance gets, free of Android.
object MoonrakerConfig {
    const val DEFAULT_PORT = 7125

    // "port: 7125" as written by the bundled template.
    @JvmField
    val PORT_PATTERN: Pattern = Pattern.compile("port: (\\d+)")

    fun portOf(configText: String): Int? {
        val m = PORT_PATTERN.matcher(configText)
        return if (m.find()) m.group(1).toIntOrNull() else null
    }

    // Every instance runs its own Moonraker, so each needs its own port: the
    // first one from 7125 up that no other instance's config already uses.
    fun nextFreePort(used: Set<Int>, start: Int = DEFAULT_PORT): Int {
        var port = start
        while (port in used) port++
        return port
    }

    fun render(
        template: String,
        klippyUds: String,
        port: Int,
        timelapseFramePath: String,
        timelapseOutput: String
    ): String = template
        .replace("\${KLIPPY_UDS}", klippyUds)
        .replace("\${MOONRAKER_PORT}", port.toString())
        .replace("\${TIMELAPSE_FRAME_PATH}", timelapseFramePath)
        .replace("\${TIMELAPSE_OUTPUT}", timelapseOutput)
}

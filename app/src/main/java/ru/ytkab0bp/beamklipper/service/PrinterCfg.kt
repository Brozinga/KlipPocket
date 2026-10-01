package ru.ytkab0bp.beamklipper.service

// Edits the app makes to a user's printer.cfg on every start.
object PrinterCfg {
    // [virtual_sdcard] is app-managed: its path must point at THIS instance's
    // gcodes folder or Moonraker's file_manager throws "GCode path received
    // from Klipper does not match expected location". Users paste configs
    // with a stale path (old instance UUID, or /data/data vs /data/user/0),
    // so whatever [virtual_sdcard] section is there is stripped and our own
    // is inserted -- before Klipper's "#*# SAVE_CONFIG" autosave marker so
    // Klipper's own SAVE_CONFIG does not drop it.
    fun withVirtualSdcard(text: String, gcodesPath: String): String {
        // Drop any existing [virtual_sdcard] header plus its option lines
        // (only ever "path:") and blank lines in between. Line-by-line so we
        // never eat a following comment or another section.
        val outLines = ArrayList<String>()
        val lines = text.split("\n")
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.trim().equals("[virtual_sdcard]", ignoreCase = true)) {
                i++
                while (i < lines.size) {
                    val t = lines[i].trim()
                    val isOpt = t.isEmpty() ||
                        t.startsWith("path", ignoreCase = true) ||
                        lines[i].firstOrNull()?.isWhitespace() == true
                    if (t.startsWith("[") || t.startsWith("#") || !isOpt) break
                    i++
                }
                // trim trailing blank lines accumulated before this
                while (outLines.isNotEmpty() && outLines.last().isBlank()) {
                    outLines.removeAt(outLines.size - 1)
                }
                continue
            }
            outLines.add(line)
            i++
        }
        val stripped = outLines.joinToString("\n")

        val block = "[virtual_sdcard]\npath: $gcodesPath\n"
        // Klipper always writes its autosave block starting with a "#*#" line.
        val markerIdx = stripped.indexOf("#*#")
        return if (markerIdx >= 0) {
            stripped.substring(0, markerIdx).trimEnd('\n', ' ', '\t') +
                "\n\n" + block + "\n\n" + stripped.substring(markerIdx)
        } else {
            stripped.trimEnd('\n', ' ', '\t') + "\n\n" + block
        }
    }
}

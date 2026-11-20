package ru.ytkab0bp.beamklipper.update

// The text of a GitHub release is Markdown (headings, bold, links, a table of
// APKs). The app and the Dashboard show it as plain text, so this turns the
// Markdown into something readable without a renderer.
object ReleaseNotes {
    private val LINK = Regex("""!?\[([^\]]*)]\([^)]*\)""")
    private val TABLE_SEPARATOR = Regex("""^\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?$""")

    fun plain(markdown: String?): String {
        if (markdown.isNullOrBlank()) return ""
        val out = ArrayList<String>()
        // the release text may start with the "Description" heading of the release template
        val lines = markdown.replace("\r\n", "\n").split('\n').dropWhile { it.isBlank() }
            .let { if (it.firstOrNull()?.trim()?.trimStart('#')?.trim().equals("Description", ignoreCase = true)) it.drop(1) else it }
        for (raw in lines) {
            var line = raw.trimEnd()
            if (TABLE_SEPARATOR.matches(line.trim())) continue
            if (line.trim().startsWith("|")) {
                val cells = line.trim().trim('|').split('|').map { it.trim() }.filter { it.isNotEmpty() }
                // the header row of the APK table adds nothing next to its rows
                line = cells.joinToString(" — ")
            }
            line = line.replace(Regex("""^\s{0,3}#{1,6}\s*"""), "")
            line = line.replace(Regex("""^\s*>\s?"""), "")
            line = line.replace(Regex("""^(\s*)[-*]\s+""")) { "${it.groupValues[1]}• " }
            line = LINK.replace(line) { it.groupValues[1].ifBlank { "" } }
            line = line.replace("**", "").replace("__", "").replace("`", "")
            out += line
        }
        return out.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
    }
}

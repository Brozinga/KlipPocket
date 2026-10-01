package ru.ytkab0bp.beamklipper.ui.state

// Naming rules for printer profiles, free of Android so they can be tested.
object InstanceNames {
    private val TRAILING_PARENS = Regex("""^(.*?)\s*\(\s*\d+\s*\)\s*$""")
    private val TRAILING_NUMBER = Regex("""^(.*?)\s+\d+\s*$""")

    const val FALLBACK_BASE = "Printer"
    private const val MAX_TRIES = 1000

    // "Printer 3" and "Printer (3)" both become "Printer".
    fun stripTrailingNumber(value: String): String {
        val s = value.trim()
        TRAILING_PARENS.matchEntire(s)?.let { return it.groupValues[1].trim() }
        TRAILING_NUMBER.matchEntire(s)?.let { return it.groupValues[1].trim() }
        return s
    }

    // First "Printer N" (from the localized template) nobody uses yet.
    fun defaultName(template: (Int) -> String, taken: Collection<String>): String {
        for (n in 1..MAX_TRIES) {
            val candidate = template(n)
            if (candidate !in taken) return candidate
        }
        val base = runCatching { stripTrailingNumber(template(1)) }
            .getOrDefault(FALLBACK_BASE).ifEmpty { FALLBACK_BASE }
        for (n in 1..MAX_TRIES) {
            val candidate = "$base $n"
            if (candidate !in taken) return candidate
        }
        return runCatching { template(1) }.getOrDefault("$FALLBACK_BASE 1")
    }

    // [taken] must not include the name of the profile being edited, so that
    // saving it without renaming keeps its own name.
    fun unique(desired: String, taken: Collection<String>, defaultName: () -> String): String {
        if (desired.isEmpty()) return defaultName()
        if (desired !in taken) return desired
        val base = stripTrailingNumber(desired).ifEmpty { FALLBACK_BASE }
        for (n in 2..MAX_TRIES) {
            val candidate = "$base $n"
            if (candidate !in taken) return candidate
        }
        return desired
    }

    // The starter printer.cfg preselected in the new profile sheet.
    fun defaultConfig(files: List<String>): String? =
        files.firstOrNull { it.lowercase().contains("example") } ?: files.firstOrNull()
}

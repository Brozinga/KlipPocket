package ru.ytkab0bp.beamklipper.utils

// Text shaping for the in-app log viewer, free of Android.
object LogText {
    // How much of any single source is kept in memory, shown and shared.
    const val MAX_CHARS = 240_000

    // Keeps the end of a long log (the recent part matters), marking the cut.
    fun tail(s: String, maxChars: Int = MAX_CHARS): String =
        if (s.length <= maxChars) s
        else "…(início cortado, mostrando os últimos ${maxChars / 1000} KB)…\n" +
            s.substring(s.length - maxChars)

    // What to show for a per-instance log file that is missing, empty or full.
    fun describeInstanceLog(fileName: String, exists: Boolean, text: String): String = when {
        !exists -> "($fileName ainda não foi criado — o serviço não chegou a rodar nesta sessão)"
        text.isBlank() -> "($fileName está vazio)"
        else -> tail(text)
    }

    // The whole diagnostic bundle is capped so it stays shareable.
    fun capCombined(text: String, maxChars: Int = MAX_CHARS): String =
        if (text.length > maxChars) text.substring(text.length - maxChars) else text
}

// The last_crash.txt the app writes on an uncaught exception, so a crash on a
// device nobody can attach to leaves a readable stack trace behind.
object CrashReport {
    fun format(
        timeMs: Long, process: String, thread: String,
        manufacturer: String, model: String, sdk: Int, abis: List<String>,
        stackTrace: String
    ): String = buildString {
        append("time=").append(timeMs).append('\n')
        append("process=").append(process).append('\n')
        append("thread=").append(thread).append('\n')
        append("device=").append(manufacturer).append(' ').append(model)
            .append(" sdk=").append(sdk).append('\n')
        append("abi=").append(abis.joinToString(",")).append("\n\n")
        append(stackTrace)
    }
}

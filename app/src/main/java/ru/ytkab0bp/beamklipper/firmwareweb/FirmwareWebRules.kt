package ru.ytkab0bp.beamklipper.firmwareweb

import java.security.MessageDigest
import java.security.SecureRandom

// Rules of the firmware web page (port 4400) that do not need Android, so they can be tested on the JVM.

/** The PIN the page asks for: six digits shown in the app's Firmware tab. */
object WebPin {
    fun generate(random: SecureRandom = SecureRandom()): String = "%06d".format(random.nextInt(1_000_000))

    fun isValid(pin: String) = pin.length == 6 && pin.all { it in '0'..'9' }

    /** Compares in constant time, so the answer time does not reveal how many digits were right. */
    fun matches(expected: String, given: String): Boolean =
        MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), given.toByteArray(Charsets.UTF_8))
}

/** Locks an address out for a while after too many wrong PINs. */
class LoginLimiter(
    private val maxFails: Int = 5,
    private val lockMs: Long = 60_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Entry(var fails: Int = 0, var lockedUntil: Long = 0)
    private val entries = HashMap<String, Entry>()

    @Synchronized fun allowed(address: String): Boolean {
        val e = entries[address] ?: return true
        if (e.lockedUntil > clock()) return false
        if (e.lockedUntil != 0L) { e.fails = 0; e.lockedUntil = 0 }
        return true
    }

    @Synchronized fun failed(address: String) {
        val e = entries.getOrPut(address) { Entry() }
        e.fails++
        if (e.fails >= maxFails) e.lockedUntil = clock() + lockMs
    }

    @Synchronized fun succeeded(address: String) { entries.remove(address) }

    /** Seconds until [address] may try again (0 when it may try now). */
    @Synchronized fun secondsLeft(address: String): Int {
        val left = (entries[address]?.lockedUntil ?: 0) - clock()
        return if (left > 0) ((left + 999) / 1000).toInt() else 0
    }
}

/** Login sessions of the page: random tokens kept in memory, dropped when the PIN changes. */
class WebSessions(private val random: SecureRandom = SecureRandom(), private val max: Int = 20) {
    private val tokens = LinkedHashSet<String>()

    @Synchronized fun issue(): String {
        val b = ByteArray(24)
        random.nextBytes(b)
        val t = b.joinToString("") { "%02x".format(it) }
        tokens += t
        while (tokens.size > max) tokens.remove(tokens.first())
        return t
    }

    @Synchronized fun valid(token: String?): Boolean = token != null && token in tokens
    @Synchronized fun revoke(token: String?) { tokens.remove(token) }
    @Synchronized fun clear() { tokens.clear() }
}

object WebRequests {
    const val COOKIE = "kp_fw"
    /** Header every request that changes something must carry: browsers cannot add it to a cross-site form. */
    const val GUARD_HEADER = "x-klippocket"

    fun cookie(header: String?, name: String): String? =
        header?.split(';')?.map { it.trim() }?.firstOrNull { it.startsWith("$name=") }?.substringAfter('=')?.takeIf { it.isNotEmpty() }

    /** Modes the page offers: the four of the first version of the Firmware tab. */
    val MODES = listOf("USB", "UART", "DFU", "RP2040")

    fun mimeFor(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "html" -> "text/html; charset=utf-8"
        "js" -> "text/javascript; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "json" -> "application/json; charset=utf-8"
        else -> "application/octet-stream"
    }

    /** The page's own files; nothing else is ever served from the assets. */
    val STATIC = setOf("/", "/index.html", "/app.css", "/app.js", "/i18n.js", "/logo.png")

    const val CSP = "default-src 'self'; style-src 'self'; script-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'"
}

package ru.ytkab0bp.beamklipper.serial

import org.json.JSONObject

// The serial speed the app opens a USB-serial adapter with. It must match the
// board's firmware (CONFIG_SERIAL_BAUD): 250000 is what Klipper builds use by
// default, a few boards (some Creality ones, for example) use 115200.
//
// Chosen per board, by the same id that names its virtual serial port, and
// stored only when the user changes it: a board nobody touched keeps
// [DEFAULT], exactly as before.
object UsbBaud {
    const val DEFAULT = 250000
    val OPTIONS = listOf(57600, 115200, 230400, DEFAULT, 460800, 500000, 1000000)

    fun isValid(baud: Int) = baud in OPTIONS

    // What a board uses: its saved speed, else the default.
    fun resolve(saved: Map<String, Int>, uid: String): Int = saved[uid]?.takeIf { isValid(it) } ?: DEFAULT

    // The saved speeds after the user picks [baud] for [uid]. Picking the default forgets the entry,
    // and a speed that is not on the list changes nothing.
    fun with(saved: Map<String, Int>, uid: String, baud: Int): Map<String, Int> {
        if (uid.isEmpty() || !isValid(baud)) return saved
        val next = LinkedHashMap(saved)
        if (baud == DEFAULT) next.remove(uid) else next[uid] = baud
        return next
    }

    fun decode(json: String?): Map<String, Int> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val o = JSONObject(json)
            val out = LinkedHashMap<String, Int>()
            for (key in o.keys()) {
                val v = o.optInt(key, -1)
                if (isValid(v) && v != DEFAULT) out[key] = v
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun encode(saved: Map<String, Int>): String = JSONObject().also { o -> saved.forEach { (k, v) -> o.put(k, v) } }.toString()
}

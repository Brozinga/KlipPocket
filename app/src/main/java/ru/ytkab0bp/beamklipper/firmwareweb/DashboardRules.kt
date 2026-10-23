package ru.ytkab0bp.beamklipper.firmwareweb

import org.json.JSONArray
import org.json.JSONObject

// Rules of the dashboard numbers that do not need Android, so they can be tested on the JVM.

object DashboardRules {
    /** [busy, total] jiffies from the aggregate "cpu" line of /proc/stat, or null when it cannot be read. */
    fun parseCpuStat(text: String): LongArray? {
        val line = text.lineSequence().firstOrNull { it.startsWith("cpu ") } ?: return null
        val v = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (v.size < 4) return null
        val idle = v[3] + v.getOrElse(4) { 0 }
        val total = v.take(8).sum()
        return longArrayOf(total - idle, total)
    }

    /** /proc/meminfo as name -> kB. */
    fun parseMeminfo(text: String): Map<String, Long> = text.lineSequence().mapNotNull { line ->
        val name = line.substringBefore(':', "").trim()
        val value = line.substringAfter(':', "").trim().substringBefore(' ').toLongOrNull()
        if (name.isEmpty() || value == null) null else name to value
    }.toMap()

    /** utime + stime (clock ticks) of a /proc/<pid>/stat line; the command name may hold spaces and parentheses. */
    fun processTicks(stat: String): Long? {
        val rest = stat.substringAfterLast(')', "").trim().split(' ')
        // after the command: state(0) ppid(1) ... utime(11) stime(12)
        val utime = rest.getOrNull(11)?.toLongOrNull() ?: return null
        val stime = rest.getOrNull(12)?.toLongOrNull() ?: return null
        return utime + stime
    }

    /** Resident pages (second field) of /proc/<pid>/statm. */
    fun residentPages(statm: String): Long? = statm.trim().split(' ').getOrNull(1)?.toLongOrNull()

    /** Which sensor a /sys/class/thermal zone type is: "cpu", "gpu" or "skin" (the outside of the phone); null for the rest. */
    fun thermalKind(type: String): String? {
        val t = type.lowercase()
        return when {
            t.contains("gpu") -> "gpu"
            t.contains("skin") || t.contains("quiet") || t.contains("case") -> "skin"
            t.contains("cpu") || t.contains("soc") || t.contains("tsens") || t.contains("cluster") || t.contains("big") || t.contains("little") -> "cpu"
            else -> null
        }
    }

    fun percent(part: Double, whole: Double): Double = if (whole <= 0.0) 0.0 else (part / whole * 100.0).coerceIn(0.0, 100.0)

    /** Names of the MCU objects of a Moonraker `/printer/objects/list` answer ("mcu", "mcu toolhead"...). */
    fun mcuObjectNames(listAnswer: JSONObject): List<String> {
        val objects = listAnswer.optJSONObject("result")?.optJSONArray("objects") ?: return emptyList()
        return (0 until objects.length()).map { objects.optString(it) }.filter { it == "mcu" || it.startsWith("mcu ") }
    }

    /** One MCU per name from a Moonraker `/printer/objects/query` answer: chip, firmware version, clock. */
    fun parseMcus(queryAnswer: JSONObject): JSONArray {
        val status = queryAnswer.optJSONObject("result")?.optJSONObject("status") ?: return JSONArray()
        val out = JSONArray()
        status.keys().asSequence().sorted().forEach { name ->
            val o = status.optJSONObject(name) ?: return@forEach
            val constants = o.optJSONObject("mcu_constants")
            out.put(JSONObject()
                .put("name", if (name == "mcu") "mcu" else name.removePrefix("mcu ").trim())
                .put("chip", constants?.optString("MCU", "") ?: "")
                .put("version", o.optString("mcu_version", ""))
                .put("clock", constants?.optLong("CLOCK_FREQ", 0) ?: 0L))
        }
        return out
    }
}

/** Turns a growing counter of CPU time into a usage percentage between two calls. */
class CpuMeter(private val cores: Int) {
    private var lastTicks = -1L
    private var lastNanos = 0L

    /** [ticks] is the total CPU time so far in ticks of [tickHz]; the answer is % of the whole device, or null for the first call. */
    @Synchronized fun sample(ticks: Long, nowNanos: Long, tickHz: Long): Double? {
        val had = lastTicks >= 0
        val dTicks = ticks - lastTicks
        val dNanos = nowNanos - lastNanos
        lastTicks = ticks
        lastNanos = nowNanos
        if (!had || dTicks < 0 || dNanos <= 0) return null
        return DashboardRules.percent(dTicks.toDouble() / tickHz, dNanos / 1e9 * cores.coerceAtLeast(1))
    }
}

package ru.ytkab0bp.beamklipper.firmwareweb

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import android.os.StatFs
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import org.json.JSONArray
import org.json.JSONObject
import ru.ytkab0bp.beamklipper.KlipperInstance
import ru.ytkab0bp.beamklipper.service.web.WebRouting
import java.io.File
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL

/**
 * Reads the numbers of the dashboard: slow facts about the device ([info]) and the live values ([metrics]).
 * Everything is best-effort: a value the Android version hides is simply left out, never an error.
 */
class DashboardCollector(private val ctx: Context) {
    private val cores = Runtime.getRuntime().availableProcessors()
    private val tickHz = try { Os.sysconf(OsConstants._SC_CLK_TCK).takeIf { it > 0 } ?: 100L } catch (_: Throwable) { 100L }
    private val appCpu = CpuMeter(cores)
    private var lastStat: LongArray? = null
    private var lastMetrics: JSONObject? = null
    private var lastMetricsAt = 0L
    private val mcuCache = HashMap<String, Pair<Long, JSONArray>>()

    private fun read(path: String): String? = try { File(path).readText() } catch (_: Throwable) { null }

    // ---- live values ----------------------------------------------------------------------------------------

    /** One sample; asking again within a second returns the same one, so several open pages do not distort the percentages. */
    @Synchronized fun metrics(): JSONObject {
        val now = SystemClock.elapsedRealtime()
        lastMetrics?.let { if (now - lastMetricsAt < 800) return it }
        val m = JSONObject().put("t", System.currentTimeMillis()).put("uptimeMs", now)

        // device-wide processor use: /proc/stat is hidden from apps on Android 8+, so it is often missing
        val stat = read("/proc/stat")?.let { DashboardRules.parseCpuStat(it) }
        if (stat != null) {
            val prev = lastStat
            if (prev != null && stat[1] > prev[1]) m.put("cpu", DashboardRules.percent((stat[0] - prev[0]).toDouble(), (stat[1] - prev[1]).toDouble()))
            lastStat = stat
        }

        // KlipPocket is several processes (app, Klipper, Moonraker, camera...). Android hides the sibling ones from /proc,
        // so their memory comes from the system (PSS) and their processor time only when /proc happens to show them.
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val uid = Process.myUid()
        val pids = (am.runningAppProcesses ?: emptyList()).filter { it.uid == uid }.map { it.pid }.toMutableSet().also { it.add(Process.myPid()) }
        var ticks = 0L
        for (pid in pids) {
            try { DashboardRules.processTicks(File("/proc/$pid/stat").readText())?.let { ticks += it } } catch (_: Throwable) { /* hidden */ }
        }
        val rss = try { am.getProcessMemoryInfo(pids.toIntArray()).sumOf { it.totalPss.toLong() } * 1024 } catch (_: Throwable) { 0L }
        appCpu.sample(ticks, System.nanoTime(), tickHz)?.let { m.put("appCpu", it) }
        m.put("appRss", rss).put("appProcesses", pids.size)

        val mem = DashboardRules.parseMeminfo(read("/proc/meminfo").orEmpty())
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val total = mem["MemTotal"]?.times(1024) ?: mi.totalMem
        val avail = mem["MemAvailable"]?.times(1024) ?: mi.availMem
        m.put("memTotal", total).put("memAvailable", avail)
        val swapTotal = mem["SwapTotal"]?.times(1024) ?: 0L
        if (swapTotal > 0) m.put("swapTotal", swapTotal).put("swapFree", (mem["SwapFree"] ?: 0L) * 1024)

        // sensors: each one is only added when this phone lets the app read it, the page hides the cards of the others
        battery()?.let { m.put("battery", it) }
        val temps = thermalTemperatures()
        temps["cpu"]?.let { m.put("cpuTemp", it) }
        temps["gpu"]?.let { m.put("gpuTemp", it) }
        temps["skin"]?.let { m.put("skinTemp", it) }
        cpuFrequencyMhz()?.let { m.put("cpuMhz", it) }
        read("/proc/loadavg")?.trim()?.substringBefore(' ')?.toDoubleOrNull()?.let { m.put("load", it) }
        wifi()?.let { (dbm, mbps) -> m.put("wifiDbm", dbm); if (mbps > 0) m.put("wifiMbps", mbps) }
        traffic(now)?.let { (rx, tx) -> m.put("netRx", rx).put("netTx", tx) }

        lastMetrics = m
        lastMetricsAt = now
        return m
    }

    private fun battery(): JSONObject? {
        val i: Intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return null
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return JSONObject().put("percent", level * 100 / scale)
            .put("temp", i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0)
            .put("voltage", i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0))
            .put("charging", status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
    }

    /** Hottest reading per kind of thermal zone ("cpu", "gpu", "skin") in °C; many phones do not let apps read these. */
    private fun thermalTemperatures(): Map<String, Double> {
        val zones = File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") } ?: return emptyMap()
        val out = HashMap<String, Double>()
        for (z in zones) {
            val type = read("${z.path}/type")?.trim()?.lowercase() ?: continue
            val kind = DashboardRules.thermalKind(type) ?: continue
            val raw = read("${z.path}/temp")?.trim()?.toDoubleOrNull() ?: continue
            val c = if (raw > 1000) raw / 1000.0 else raw
            if (c in 1.0..150.0) out[kind] = maxOf(out[kind] ?: c, c)
        }
        return out
    }

    /** The fastest core's current frequency in MHz. */
    private fun cpuFrequencyMhz(): Int? =
        (0 until cores).mapNotNull { read("/sys/devices/system/cpu/cpu$it/cpufreq/scaling_cur_freq")?.trim()?.toLongOrNull() }
            .maxOrNull()?.let { (it / 1000).toInt() }

    /** Wi-Fi signal (dBm) and link speed (Mbps), or null when not on Wi-Fi. */
    @Suppress("DEPRECATION")
    private fun wifi(): Pair<Int, Int>? = try {
        val info = (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo
        // networkId reads -1 for apps without the location permission even when connected, so only the signal is checked
        if (info == null || info.rssi >= 0 || info.rssi <= -127) null else info.rssi to info.linkSpeed
    } catch (_: Throwable) { null }

    private var lastNet: Triple<Long, Long, Long>? = null

    /** Bytes per second received and sent by the whole phone since the previous sample, or null the first time. */
    private fun traffic(now: Long): Pair<Long, Long>? {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) return null
        val prev = lastNet
        lastNet = Triple(now, rx, tx)
        if (prev == null || now <= prev.first || rx < prev.second || tx < prev.third) return null
        val secs = (now - prev.first) / 1000.0
        return ((rx - prev.second) / secs).toLong() to ((tx - prev.third) / secs).toLong()
    }

    // ---- facts that change slowly ---------------------------------------------------------------------------

    fun info(usbDevices: JSONArray): JSONObject {
        val host = JSONObject()
            .put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL).put("device", Build.DEVICE)
            .put("hardware", Build.HARDWARE).put("board", Build.BOARD)
            .put("android", Build.VERSION.RELEASE).put("sdk", Build.VERSION.SDK_INT)
            .put("abis", JSONArray(Build.SUPPORTED_ABIS.toList())).put("cores", cores)
            .put("kernel", System.getProperty("os.version") ?: "")
            .put("addresses", JSONArray(addresses()))
        if (Build.VERSION.SDK_INT >= 31) host.put("soc", "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}".trim())
        maxFrequencyMhz()?.let { host.put("maxMhz", it) }
        try {
            val p = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            host.put("app", p.versionName ?: "")
        } catch (_: Throwable) { /* unknown */ }

        val storage = JSONArray()
        volume("internal", ctx.filesDir)?.let { storage.put(it) }
        // a second external directory is a removable card; the first one shares the internal partition
        val ext = ctx.getExternalFilesDirs(null).filterNotNull()
        ext.drop(1).forEachIndexed { i, d -> volume("sd$i", d)?.let { storage.put(it) } }

        return JSONObject().put("host", host).put("storage", storage).put("instances", instances()).put("usb", usbDevices)
    }

    private fun volume(id: String, dir: File): JSONObject? = try {
        val s = StatFs(dir.path)
        JSONObject().put("id", id).put("total", s.totalBytes).put("free", s.availableBytes)
    } catch (_: Throwable) { null }

    private fun addresses(): List<String> = try {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { n -> n.inetAddresses.toList().filter { it.address.size == 4 }.map { it.hostAddress ?: "" } }.filter { it.isNotEmpty() }
    } catch (_: Throwable) { emptyList() }

    /** The fastest core's top frequency (cpu0 is usually a slow core on big.LITTLE phones). */
    private fun maxFrequencyMhz(): Int? =
        (0 until cores).mapNotNull { read("/sys/devices/system/cpu/cpu$it/cpufreq/cpuinfo_max_freq")?.trim()?.toLongOrNull() }
            .maxOrNull()?.let { (it / 1000).toInt() }

    private fun instances(): JSONArray {
        val out = JSONArray()
        for (inst in KlipperInstance.getInstances()) {
            val state = inst.getState()
            val o = JSONObject().put("id", inst.id ?: "").put("name", inst.name).put("state", state.name)
            val mcus = if (state == KlipperInstance.State.RUNNING) mcus(inst) else JSONArray()
            out.put(o.put("mcus", mcus))
        }
        return out
    }

    /** Asks the instance's own Moonraker (on this phone) which MCUs Klipper is talking to; cached for a few seconds. */
    private fun mcus(inst: KlipperInstance): JSONArray {
        val key = inst.id ?: inst.name
        val now = SystemClock.elapsedRealtime()
        mcuCache[key]?.let { if (now - it.first < 5000) return it.second }
        val cfg = File(File(inst.publicDirectory, "config"), "moonraker.conf")
        val port = try { WebRouting.moonrakerPortFromConfig(cfg.readText()) } catch (_: Throwable) { null } ?: WebRouting.DEFAULT_MOONRAKER_PORT
        val result = try {
            val names = DashboardRules.mcuObjectNames(JSONObject(get(port, "/printer/objects/list")))
            if (names.isEmpty()) JSONArray()
            else DashboardRules.parseMcus(JSONObject(get(port, "/printer/objects/query?" + names.joinToString("&") { java.net.URLEncoder.encode(it, "UTF-8") })))
        } catch (_: Throwable) { JSONArray() }
        mcuCache[key] = now to result
        return result
    }

    private fun get(port: Int, path: String): String {
        val c = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        c.connectTimeout = 700
        c.readTimeout = 1200
        try { return c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
    }
}

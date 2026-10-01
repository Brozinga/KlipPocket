package ru.ytkab0bp.beamklipper.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionConfigTest {
    private fun obico(token: String?) =
        ObicoConfig.render("https://app.obico.io", token, 7126, "/data/logs/obico.log")

    private fun sections(cfg: String): Map<String, Map<String, String>> {
        val out = LinkedHashMap<String, MutableMap<String, String>>()
        var current: MutableMap<String, String>? = null
        for (line in cfg.lines()) {
            val t = line.trim()
            if (t.startsWith("[") && t.endsWith("]")) {
                current = LinkedHashMap<String, String>().also { out[t.substring(1, t.length - 1)] = it }
            } else if (t.contains("=") && current != null) {
                current[t.substringBefore("=").trim()] = t.substringAfter("=").trim()
            }
        }
        return out
    }

    @Test
    fun `the obico config points at the server and moonraker`() {
        val s = sections(obico("tok"))
        assertEquals("https://app.obico.io", s["server"]?.get("url"))
        assertEquals("127.0.0.1", s["moonraker"]?.get("host"))
        assertEquals("7126", s["moonraker"]?.get("port"))
    }

    @Test
    fun `a linked printer carries its auth token`() {
        assertEquals("tok", sections(obico("tok"))["server"]?.get("auth_token"))
    }

    @Test
    fun `an unlinked printer has no auth token so obico runs its own linking`() {
        assertFalse(obico(null).contains("auth_token"))
        assertFalse(obico("").contains("auth_token"))
        assertFalse(obico("   ").contains("auth_token"))
    }

    @Test
    fun `obico streams snapshots only and never reports crashes`() {
        val s = sections(obico(null))
        assertEquals("True", s["webcam"]?.get("disable_video_streaming"))
        assertEquals("out", s["misc"]?.get("sentry_opt"))
    }

    @Test
    fun `obico logs to the given file`() {
        val s = sections(obico(null))
        assertEquals("/data/logs/obico.log", s["logging"]?.get("path"))
        assertEquals("INFO", s["logging"]?.get("level"))
    }

    @Test
    fun `octoeverywhere is started with the instance's folders`() {
        val json = OctoEverywhereConfig.build(
            "abc", "/files/octoeverywhere", "/inst/abc/octoeverywhere",
            "/pub/config", "/pub/logs", "/pub/config/moonraker.conf"
        )
        assertEquals("klippocket-abc", json.getString("ServiceName"))
        assertEquals("/files/octoeverywhere", json.getString("RepoRootFolder"))
        assertEquals("/files/octoeverywhere", json.getString("VirtualEnvPath"))
        assertEquals("/inst/abc/octoeverywhere", json.getString("LocalFileStoragePath"))
        assertEquals("/pub/config", json.getString("ConfigFolder"))
        assertEquals("/pub/logs", json.getString("LogFolder"))
        assertEquals("/pub/config/moonraker.conf", json.getString("MoonrakerConfigFile"))
    }

    @Test
    fun `octoeverywhere never rewrites moonraker conf and is not a companion`() {
        val json = OctoEverywhereConfig.build("abc", "/a", "/b", "/c", "/d", "/e")
        assertTrue(json.getBoolean("DisableMoonrakerConfigFileWrites"))
        assertFalse(json.getBoolean("IsCompanion"))
        assertFalse(json.getBoolean("IsDockerContainer"))
    }
}

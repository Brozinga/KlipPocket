package ru.ytkab0bp.beamklipper.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// JSON-fixture parsing only — no real network call (mirrors the existing gap
// where SettingsViewModel.linkObico's HTTP call also has no unit test,
// verified manually on-device instead).
class GitHubReleasesTest {
    @Test
    fun `parseReleaseTag extracts tag_name from a releases-latest response`() {
        val json = """{"tag_name":"v1.38.0","name":"1.38.0","draft":false}"""
        assertEquals("v1.38.0", GitHubReleases.parseReleaseTag(json))
    }

    @Test
    fun `parseCommitSha extracts sha from a commits response`() {
        val json = """{"sha":"3a1f884dc83c44714bfc93238525cb57f4474588","commit":{"message":"..."}}"""
        assertEquals("3a1f884dc83c44714bfc93238525cb57f4474588", GitHubReleases.parseCommitSha(json))
    }

    @Test
    fun `parseLatestVersionTag picks the highest tag from a tags response`() {
        val json = """[{"name":"v0.12.0"},{"name":"v0.13.0"},{"name":"v0.9.1"}]"""
        assertEquals("v0.13.0", GitHubReleases.parseLatestVersionTag(json))
    }

    @Test
    fun `parseReleaseTag is null when the field is missing`() {
        assertNull(GitHubReleases.parseReleaseTag("""{"message":"Not Found"}"""))
    }

    @Test
    fun `parseCommitSha is null when the field is missing`() {
        assertNull(GitHubReleases.parseCommitSha("""{"message":"Not Found"}"""))
    }
}

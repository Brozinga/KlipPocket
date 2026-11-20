package ru.ytkab0bp.beamklipper.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
    @Test
    fun `tagNewer only for a strictly higher version`() {
        assertTrue(VersionCompare.tagNewer("0.0.9", "v0.0.10"))
        assertTrue(VersionCompare.tagNewer("0.0.9", "v0.1.0"))
        assertFalse(VersionCompare.tagNewer("0.0.9", "v0.0.9"))
        assertFalse(VersionCompare.tagNewer("0.0.9", "v0.0.8"))
        assertFalse(VersionCompare.tagNewer("0.0.9", null))
        assertFalse(VersionCompare.tagNewer("dev", "v0.0.9"))
    }

    @Test
    fun `parseRelease reads the tag, the text and the files`() {
        val json = """{"tag_name":"v0.0.9","body":"## Hi","html_url":"https://x/y",
            "assets":[{"name":"KlipPocket_v0.0.9_arm64.apk","browser_download_url":"https://d/a","size":10},
                      {"name":"SHA256SUMS.txt","browser_download_url":"https://d/s","size":1}]}"""
        val r = GitHubReleases.parseRelease(json)!!
        assertEquals("v0.0.9", r.tagName)
        assertEquals("## Hi", r.body)
        assertEquals(2, r.assets!!.size)
        assertEquals("https://d/a", r.assets!![0].url)
    }

    @Test
    fun `parseRelease without a tag has no tag`() {
        assertNull(GitHubReleases.parseRelease("""{"message":"Not Found"}""")?.tagName)
    }

    @Test
    fun `suffixFor maps the processors to the release file names`() {
        assertEquals("arm64", AppUpdateChecker.suffixFor("arm64"))
        assertEquals("arm64", AppUpdateChecker.suffixFor("arm64-v8a"))
        assertEquals("armv7", AppUpdateChecker.suffixFor("arm"))
        assertEquals("armv7", AppUpdateChecker.suffixFor("armeabi-v7a"))
        assertEquals("amd64", AppUpdateChecker.suffixFor("x86_64"))
        assertNull(AppUpdateChecker.suffixFor("mips"))
    }

    @Test
    fun `expectedHash finds the line of the file`() {
        val sums = "AAA111  KlipPocket_v0.0.9_amd64.apk\nbbb222 *KlipPocket_v0.0.9_arm64.apk\n"
        assertEquals("bbb222", AppUpdateChecker.expectedHash(sums, "KlipPocket_v0.0.9_arm64.apk"))
        assertEquals("aaa111", AppUpdateChecker.expectedHash(sums, "KlipPocket_v0.0.9_amd64.apk"))
        assertNull(AppUpdateChecker.expectedHash(sums, "other.apk"))
    }

    @Test
    fun `release notes become plain text`() {
        val md = """
            ## 🚀 KlipPocket v0.0.9

            ### ✨ What's new
            - **Spanish** translation, see the [README](https://x/y)
            - `Home` app

            | File | For |
            |---|---|
            | `a.apk` | Modern devices |

            > **Note:** careful
        """.trimIndent()
        val text = ReleaseNotes.plain(md)
        assertEquals(
            "🚀 KlipPocket v0.0.9\n\n✨ What's new\n• Spanish translation, see the README\n• Home app\n\nFile — For\na.apk — Modern devices\n\nNote: careful",
            text
        )
    }

    @Test
    fun `release notes of nothing are empty`() {
        assertEquals("", ReleaseNotes.plain(null))
        assertEquals("", ReleaseNotes.plain("  \n"))
    }

    @Test
    fun `the Description heading of the template is dropped`() {
        assertEquals("Hello", ReleaseNotes.plain("# Description\nHello"))
        assertEquals("Description of things", ReleaseNotes.plain("Description of things"))
    }
}

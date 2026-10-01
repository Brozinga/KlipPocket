package ru.ytkab0bp.beamklipper.update

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

// Unauthenticated GitHub REST calls, matching this codebase's existing
// runtime HTTP convention (raw HttpURLConnection — see
// SettingsViewModel.linkObico, CameraPreviewScreen's MJPEG GET; OkHttp is a
// Gradle dependency but nothing at runtime actually uses it). Every call
// throws on failure; callers (FrontendUpdateChecker) wrap in try/catch, same
// as linkObico does.
object GitHubReleases {
    private val gson = Gson()
    private const val TIMEOUT_MS = 10_000

    // Package-visible (not private) so the JSON parsing itself — the only
    // part of this object that doesn't need a real network call — can be
    // unit-tested against fixture strings.
    internal data class ReleaseInfo(@SerializedName("tag_name") val tagName: String?)
    internal data class CommitInfo(@SerializedName("sha") val sha: String?)

    internal fun parseReleaseTag(json: String): String? = gson.fromJson(json, ReleaseInfo::class.java)?.tagName
    internal fun parseCommitSha(json: String): String? = gson.fromJson(json, CommitInfo::class.java)?.sha

    internal data class TagInfo(@SerializedName("name") val name: String?)

    // Klipper and Moonraker publish plain git tags (vX.Y.Z), not GitHub
    // releases, so "latest" is the highest such tag. The tags endpoint isn't
    // ordered by version, hence the explicit max instead of taking the first.
    internal fun parseLatestVersionTag(json: String): String? {
        val tags = gson.fromJson(json, Array<TagInfo>::class.java) ?: return null
        return VersionCompare.highestTag(tags.mapNotNull { it.name })
    }

    private fun get(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw java.io.IOException("HTTP ${conn.responseCode} for $urlStr")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun latestReleaseTag(owner: String, repo: String): String? =
        parseReleaseTag(get("https://api.github.com/repos/$owner/$repo/releases/latest"))

    fun latestVersionTag(owner: String, repo: String): String? =
        parseLatestVersionTag(get("https://api.github.com/repos/$owner/$repo/tags?per_page=100"))

    fun latestCommitSha(owner: String, repo: String, branch: String = "master"): String? =
        parseCommitSha(get("https://api.github.com/repos/$owner/$repo/commits/$branch"))

    // Streams into destFile with byte-counted progress against Content-Length.
    // A chunked response (length == -1) just reports 0 until the copy
    // finishes, then the caller moves on to the next step — acceptable given
    // these are always a few-MB static-site zips with a real Content-Length
    // in practice.
    fun downloadToFile(urlStr: String, destFile: File, onProgress: (percent: Int) -> Unit) {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.instanceFollowRedirects = true
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw java.io.IOException("HTTP ${conn.responseCode} for $urlStr")
            }
            val total = conn.contentLengthLong
            var read = 0L
            val buf = ByteArray(64 * 1024)
            conn.inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    while (true) {
                        val n = input.read(buf)
                        if (n == -1) break
                        output.write(buf, 0, n)
                        read += n
                        if (total > 0) {
                            onProgress((read * 100 / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
            onProgress(100)
        } finally {
            conn.disconnect()
        }
    }
}

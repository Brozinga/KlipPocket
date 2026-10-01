package ru.ytkab0bp.beamklipper.update

// Klipper/Moonraker: informational only, no update button ever possible (see
// docs/klipper-vendor-update-procedure — native chelper needs recompiling,
// and the Android source patches are literal String.replace() calls that
// silently no-op if upstream text drifts). "bundled" and "latest" are version
// tags (e.g. v0.13.0), or null if the marker is missing/unreadable or the
// check failed.
data class VersionStatus(
    val bundled: String?,
    val latest: String?,
    val checkedAtMs: Long,
    val error: Boolean = false,
)

// Fluidd/Mainsail/Voyager-UI: "active" is whichever version is actually being
// served right now (an in-app override if one was ever installed, else the
// version baked into the APK).
data class FrontendVersionStatus(
    val active: String,
    val latest: String?,
    val checkedAtMs: Long,
    val error: Boolean = false,
)

object VersionCompare {
    // GitHub's releases/latest is trusted as "the newest" outright — no
    // semver parsing to get wrong, just "is it different from what we have."
    // A failed/unknown check (latest == null) never claims an update exists.
    fun tagDiffers(active: String, latest: String?): Boolean =
        latest != null && latest != active

    private val TAG_RE = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""")

    private fun tagKey(tag: String): Triple<Int, Int, Int>? =
        TAG_RE.matchEntire(tag.trim())?.destructured?.let { (a, b, c) ->
            Triple(a.toInt(), b.toInt(), c.toInt())
        }

    // Highest plain vX.Y.Z tag; anything else (release candidates, odd names)
    // is ignored.
    fun highestTag(tags: List<String>): String? =
        tags.mapNotNull { t -> tagKey(t)?.let { t to it } }
            .maxWithOrNull(compareBy({ it.second.first }, { it.second.second }, { it.second.third }))
            ?.first

    // Klipper/Moonraker: both sides are version tags. A missing/unknown
    // bundled marker never claims the component is behind — there is nothing
    // to compare against. Only a strictly newer upstream tag counts.
    fun tagBehind(bundled: String?, latest: String?): Boolean {
        val b = bundled?.let { tagKey(it) } ?: return false
        val l = latest?.let { tagKey(it) } ?: return false
        return compareBy<Triple<Int, Int, Int>>({ it.first }, { it.second }, { it.third }).compare(l, b) > 0
    }
}

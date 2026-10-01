package ru.ytkab0bp.beamklipper.update

import ru.ytkab0bp.beamklipper.utils.Prefs

// Upstream repos + release zip asset names for the three frontends Beam can
// hot-swap in-app. Mirrors registerFrontendBundle's zipUrl closures in
// app/build.gradle exactly, which already prove these are the right URLs.
object FrontendRepos {
    data class Repo(val owner: String, val repo: String, val zipAssetName: String)

    val FRONTENDS: Map<String, Repo> = mapOf(
        Prefs.FRONTEND_FLUIDD to Repo("fluidd-core", "fluidd", "fluidd.zip"),
        Prefs.FRONTEND_MAINSAIL to Repo("mainsail-crew", "mainsail", "mainsail.zip"),
        Prefs.FRONTEND_VOYAGER to Repo("ozancs", "voyager-ui", "voyager-ui.zip"),
    )

    fun zipUrl(frontend: String, tag: String): String? {
        val repo = FRONTENDS[frontend] ?: return null
        return "https://github.com/${repo.owner}/${repo.repo}/releases/download/$tag/${repo.zipAssetName}"
    }

    // Klipper and Moonraker have no GitHub releases, only version tags —
    // "latest" is the highest vX.Y.Z tag, checked via the tags API.
    const val KLIPPER_OWNER = "Klipper3d"
    const val KLIPPER_REPO = "klipper"
    const val MOONRAKER_OWNER = "Arksine"
    const val MOONRAKER_REPO = "moonraker"
}

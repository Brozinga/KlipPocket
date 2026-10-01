package ru.ytkab0bp.beamklipper.utils

import ru.ytkab0bp.beamklipper.R

// The web front ends the app can serve, one port each. Kept free of Android
// state so the selection rules can be unit-tested on the JVM.
object Frontends {
    const val PORT_FLUIDD = 4408
    const val PORT_MAINSAIL = 4409
    const val PORT_VOYAGER = 4410

    // Order of the cycle offered by the settings tile.
    val ALL: List<String> = listOf(
        Prefs.FRONTEND_FLUIDD,
        Prefs.FRONTEND_MAINSAIL,
        Prefs.FRONTEND_VOYAGER
    )

    fun portFor(frontend: String): Int = when (frontend) {
        Prefs.FRONTEND_FLUIDD -> PORT_FLUIDD
        Prefs.FRONTEND_VOYAGER -> PORT_VOYAGER
        else -> PORT_MAINSAIL
    }

    // Next front end in [ALL]; anything unknown starts the cycle over.
    fun next(current: String): String {
        val i = ALL.indexOf(current)
        return ALL[(i + 1) % ALL.size]
    }

    fun nameRes(frontend: String): Int = when (frontend) {
        Prefs.FRONTEND_FLUIDD -> R.string.Fluidd
        Prefs.FRONTEND_VOYAGER -> R.string.Voyager
        else -> R.string.Mainsail
    }

    fun iconRes(frontend: String): Int = when (frontend) {
        Prefs.FRONTEND_FLUIDD -> R.drawable.ic_square_stack_up_outline_28
        Prefs.FRONTEND_VOYAGER -> R.drawable.ic_compass_24
        else -> R.drawable.ic_sailing_24
    }
}

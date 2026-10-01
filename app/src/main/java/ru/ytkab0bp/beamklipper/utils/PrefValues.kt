package ru.ytkab0bp.beamklipper.utils

// Value rules behind Prefs, kept free of Android so they can be unit-tested.
object PrefValues {
    // Coerce a stored value of the wrong type (an older build wrote it as
    // another type) into the type that is read now. null = cannot convert.
    fun boolFromAny(raw: Any?, default: Boolean): Boolean = when (raw) {
        is Boolean -> raw
        is Number -> raw.toInt() != 0
        is String -> raw.toBooleanStrictOrNull() ?: default
        else -> default
    }

    fun intFromAny(raw: Any?, default: Int): Int = when (raw) {
        is Number -> raw.toInt()
        is String -> raw.toIntOrNull() ?: default
        else -> default
    }

    fun floatFromAny(raw: Any?, default: Float): Float = when (raw) {
        is Number -> raw.toFloat()
        is String -> raw.toFloatOrNull() ?: default
        else -> default
    }

    // Only 0/90/180/270 are valid rotations; anything else wraps around.
    fun rotation(value: Int): Int = ((value % 360) + 360) % 360

    fun resolution(value: Int, presetCount: Int): Int = value.coerceIn(0, presetCount - 1)

    fun zoom(value: Float): Float = value.coerceAtLeast(1f)

    // Any URL is accepted (Obico Cloud or a self-hosted server); a blank one
    // falls back to the default.
    fun obicoServerUrl(value: String, default: String): String =
        value.trim().trimEnd('/').ifEmpty { default }

    // Before the front end became a setting there was a "mainsail" boolean.
    fun frontendFromLegacyFlag(mainsail: Boolean): String =
        if (mainsail) Prefs.FRONTEND_MAINSAIL else Prefs.FRONTEND_FLUIDD

    // "kalico_frontend" was never a real front end; it used Mainsail's assets.
    @Suppress("DEPRECATION")
    fun migrateFrontend(stored: String): String =
        if (stored == Prefs.FRONTEND_KALICO) Prefs.FRONTEND_MAINSAIL else stored

    fun nextRotation(current: Int): Int = rotation(current + 90)

    fun nextResolution(current: Int, presetCount: Int): Int = (current + 1) % presetCount
}

package ru.ytkab0bp.beamklipper.db

// Reading the instances table: SQLite hands the autostart flag back as
// whatever type the row was written with.
object InstanceRow {
    fun autostart(raw: Any?): Boolean = when (raw) {
        is Boolean -> raw
        is Int -> raw != 0
        is Long -> raw != 0L
        is String -> raw == "1"
        else -> false
    }
}

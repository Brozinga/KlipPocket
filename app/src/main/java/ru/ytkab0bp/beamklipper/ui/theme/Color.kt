package ru.ytkab0bp.beamklipper.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** One complete set of KlipPocket colours. */
class KlipPalette(
    val paper: Color,
    val paperAlt: Color,
    val line: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkDim: Color,
    val accent: Color,
    val mint: Color,
    val danger: Color,
    val amber: Color,
    val isDark: Boolean,
)

val DarkPalette = KlipPalette(
    paper = Color(0xFF111315),
    paperAlt = Color(0xFF1E2227),
    line = Color(0xFF2A2F37),
    ink = Color(0xFFFFFFFF),
    inkMuted = Color(0xFF9AA0A6),
    inkDim = Color(0xFF6B7178),
    accent = Color(0xFF49CBEB),
    mint = Color(0xFF71F0C4),
    danger = Color(0xFFFF5A5F),
    amber = Color(0xFFFFB74D),
    isDark = true,
)

val LightPalette = KlipPalette(
    paper = Color(0xFFF4F5F6),
    paperAlt = Color(0xFFFFFFFF),
    line = Color(0xFFDDE1E6),
    ink = Color(0xFF111315),
    inkMuted = Color(0xFF5F666D),
    inkDim = Color(0xFF8C9298),
    accent = Color(0xFF49CBEB),
    mint = Color(0xFF71F0C4),
    danger = Color(0xFFE5484D),
    amber = Color(0xFFE8960C),
    isDark = false,
)

/** Palette currently applied by [KlipTheme]; reads inside composition are observed. */
object ActivePalette {
    var current by mutableStateOf(DarkPalette)
}

val Paper: Color get() = ActivePalette.current.paper
val PaperAlt: Color get() = ActivePalette.current.paperAlt
val Line: Color get() = ActivePalette.current.line
val Ink: Color get() = ActivePalette.current.ink
val InkMuted: Color get() = ActivePalette.current.inkMuted
val InkDim: Color get() = ActivePalette.current.inkDim
val Accent: Color get() = ActivePalette.current.accent
val Ok: Color get() = ActivePalette.current.mint
val Danger: Color get() = ActivePalette.current.danger
val Warn: Color get() = ActivePalette.current.amber

// Text/icons placed on top of cyan, mint or amber fills (always dark for contrast).
val InkOnAccent = Color(0xFF111315)
val InkOnOk = Color(0xFF111315)
val InkOnWarn = Color(0xFF111315)

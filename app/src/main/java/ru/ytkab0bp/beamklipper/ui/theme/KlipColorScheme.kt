package ru.ytkab0bp.beamklipper.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

fun klipColorScheme(p: KlipPalette): ColorScheme {
    val base = if (p.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p.accent,
        onPrimary = InkOnAccent,
        primaryContainer = p.accent,
        onPrimaryContainer = InkOnAccent,
        secondary = p.ink,
        onSecondary = p.paper,
        secondaryContainer = p.paperAlt,
        onSecondaryContainer = p.ink,
        tertiary = p.mint,
        onTertiary = InkOnOk,
        tertiaryContainer = p.mint,
        onTertiaryContainer = InkOnOk,
        background = p.paper,
        onBackground = p.ink,
        surface = p.paper,
        onSurface = p.ink,
        surfaceVariant = p.paperAlt,
        onSurfaceVariant = p.inkMuted,
        surfaceContainerLowest = p.paper,
        surfaceContainerLow = p.paper,
        surfaceContainer = p.paper,
        surfaceContainerHigh = p.paperAlt,
        surfaceContainerHighest = p.paperAlt,
        outline = p.line,
        outlineVariant = p.line,
        error = p.danger,
        onError = InkOnAccent,
    )
}

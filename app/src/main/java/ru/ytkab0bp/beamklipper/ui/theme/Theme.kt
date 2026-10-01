package ru.ytkab0bp.beamklipper.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

val KlipShape = RoundedCornerShape(16.dp)
val KlipShapeSmall = RoundedCornerShape(12.dp)
val KlipShapePill = RoundedCornerShape(50)

/**
 * Dark is the main/default look. The light variant is used only when the system
 * supports theme switching (Android 10+) and is currently in light mode.
 */
@Composable
fun KlipTheme(content: @Composable () -> Unit) {
    val dark = if (Build.VERSION.SDK_INT >= 29) isSystemInDarkTheme() else true
    val palette = if (dark) DarkPalette else LightPalette
    SideEffect { ActivePalette.current = palette }
    MaterialTheme(
        colorScheme = klipColorScheme(palette),
        typography = AppTypography,
        content = content
    )
}

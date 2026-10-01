package ru.ytkab0bp.beamklipper.ui.components

import androidx.compose.foundation.background
import ru.ytkab0bp.beamklipper.ui.theme.Line
import ru.ytkab0bp.beamklipper.ui.theme.KlipShape
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import ru.ytkab0bp.beamklipper.ui.theme.Ink
import ru.ytkab0bp.beamklipper.ui.theme.Paper

@Composable
fun KlipTile(
    modifier: Modifier = Modifier,
    background: Color = Paper,
    onClick: (() -> Unit)? = null,
    offsetX: Int = 0,
    offsetY: Int = 0,
    borderWidth: Int = 2,
    content: @Composable () -> Unit
) {
    val shape = KlipShape
    Box(
        modifier = modifier
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(background, shape)
                .border(borderWidth.dp, Line, shape)
                .padding(16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            content()
        }
    }
}

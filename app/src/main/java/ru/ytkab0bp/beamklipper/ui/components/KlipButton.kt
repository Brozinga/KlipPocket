package ru.ytkab0bp.beamklipper.ui.components

import androidx.compose.foundation.background
import ru.ytkab0bp.beamklipper.ui.theme.Line
import ru.ytkab0bp.beamklipper.ui.theme.KlipShapePill
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.ytkab0bp.beamklipper.ui.theme.Accent
import ru.ytkab0bp.beamklipper.ui.theme.Ink
import ru.ytkab0bp.beamklipper.ui.theme.InkOnAccent
import ru.ytkab0bp.beamklipper.ui.theme.PaperAlt

@Composable
fun KlipButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    background: Color = Accent,
    contentColor: Color = InkOnAccent,
    minHeight: Dp = 40.dp
) {
    val shape = KlipShapePill
    val bg = if (enabled) background else background.copy(alpha = 0.4f)
    Box(
        modifier = modifier
            .heightIn(min = minHeight)
            .clip(shape)
            .background(bg, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) contentColor else contentColor.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}

@Composable
fun KlipTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = Ink
) {
    val shape = KlipShapePill
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .background(PaperAlt, shape)
            .border(1.dp, Line, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

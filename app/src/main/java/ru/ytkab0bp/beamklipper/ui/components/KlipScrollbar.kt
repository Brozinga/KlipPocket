package ru.ytkab0bp.beamklipper.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.ytkab0bp.beamklipper.ui.theme.Ink

// Plain Modifier.verticalScroll() draws no scroll indicator at all — on a
// short viewport (landscape especially: the screen is only ~1080px tall
// instead of ~2280px in portrait, so far more content sits below the fold)
// there was no visual cue that there was more to scroll to, and some
// settings rows were effectively undiscoverable. This is a minimal
// always-on thumb, not a fading Material scrollbar, so it stays visible.
//
// Must go BEFORE .verticalScroll(state) in the modifier chain (i.e. applied
// to the un-scrolled container), so `size` here reflects the viewport, not
// the full (unbounded) scrolled content height.
fun Modifier.klipScrollbar(
    state: ScrollState,
    width: Dp = 4.dp,
    color: Color = Ink.copy(alpha = 0.35f)
): Modifier = drawWithContent {
    drawContent()
    val viewportHeight = size.height
    val contentHeight = viewportHeight + state.maxValue
    if (state.maxValue <= 0 || contentHeight <= 0f) return@drawWithContent
    val thumbHeight = (viewportHeight / contentHeight * viewportHeight).coerceAtLeast(width.toPx() * 4)
    val maxThumbOffset = viewportHeight - thumbHeight
    val thumbOffset = (state.value.toFloat() / state.maxValue) * maxThumbOffset
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width - width.toPx(), thumbOffset),
        size = Size(width.toPx(), thumbHeight),
        cornerRadius = CornerRadius(width.toPx() / 2)
    )
}

// Horizontal counterpart, for a row that overflows sideways (e.g. the Logs
// tab strip) rather than a column that overflows downward. Same rule: must
// go BEFORE .horizontalScroll(state) in the modifier chain. The gesture
// itself already works via a plain .horizontalScroll() with no indicator at
// all, but a tab cut off mid-label at the viewport edge reads as clipped/
// broken rather than "there's more, swipe" — this makes that discoverable.
fun Modifier.klipScrollbarHorizontal(
    state: ScrollState,
    height: Dp = 4.dp,
    color: Color = Ink.copy(alpha = 0.35f)
): Modifier = drawWithContent {
    drawContent()
    val viewportWidth = size.width
    val contentWidth = viewportWidth + state.maxValue
    if (state.maxValue <= 0 || contentWidth <= 0f) return@drawWithContent
    val thumbWidth = (viewportWidth / contentWidth * viewportWidth).coerceAtLeast(height.toPx() * 4)
    val maxThumbOffset = viewportWidth - thumbWidth
    val thumbOffset = (state.value.toFloat() / state.maxValue) * maxThumbOffset
    drawRoundRect(
        color = color,
        topLeft = Offset(thumbOffset, size.height - height.toPx()),
        size = Size(thumbWidth, height.toPx()),
        cornerRadius = CornerRadius(height.toPx() / 2)
    )
}

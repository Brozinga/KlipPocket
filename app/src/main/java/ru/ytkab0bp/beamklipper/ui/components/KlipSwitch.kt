package ru.ytkab0bp.beamklipper.ui.components

import androidx.compose.foundation.background
import ru.ytkab0bp.beamklipper.ui.theme.Line
import ru.ytkab0bp.beamklipper.ui.theme.KlipShapePill
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.ytkab0bp.beamklipper.ui.theme.Ink
import ru.ytkab0bp.beamklipper.ui.theme.Ok
import ru.ytkab0bp.beamklipper.ui.theme.InkOnOk

@Composable
fun KlipSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val shape = KlipShapePill
    Box(
        modifier = modifier
            .size(width = 56.dp, height = 32.dp)
            .let { if (onCheckedChange != null) it.clickable { onCheckedChange(!checked) } else it }
            .background(if (checked) Ok else Line, shape)
            .padding(4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .background(if (checked) InkOnOk else Ink, shape)
        )
    }
}

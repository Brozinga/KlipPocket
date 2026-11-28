package ru.ytkab0bp.beamklipper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.ui.components.KlipButton
import ru.ytkab0bp.beamklipper.ui.components.SummaryCard
import ru.ytkab0bp.beamklipper.ui.state.PrintMonitorViewModel
import ru.ytkab0bp.beamklipper.ui.theme.Ink
import ru.ytkab0bp.beamklipper.ui.theme.KlipShape
import ru.ytkab0bp.beamklipper.ui.theme.Line
import ru.ytkab0bp.beamklipper.ui.theme.Paper
import ru.ytkab0bp.beamklipper.ui.theme.PaperAlt

@Composable
fun PrintMonitorScreen(
    printMonitorViewModel: PrintMonitorViewModel
) {
    val temperatures by printMonitorViewModel.temperatures
        .collectAsStateWithLifecycle()

    val activeInstances = printMonitorViewModel.activeInstances
    val selectedId = printMonitorViewModel.selectedInstanceId.value

    LaunchedEffect(printMonitorViewModel) {
        printMonitorViewModel.startTemperaturePolling()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
            text = "Print Monitor",
            style = MaterialTheme.typography.headlineMedium,
            color = Ink
        )

        if (selectedId == null) {
            Text(
                text = stringResource(R.string.SelectPrinterViewTemperatures),
                style = MaterialTheme.typography.bodyMedium,
                color = Ink
            )

            if (activeInstances.isEmpty()) {
                Text(
                    text = stringResource(R.string.PrintersNoActive),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ink
                )
            } else {
                activeInstances.forEach { instance ->
                    val key = instance.id ?: instance.name
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(KlipShape)
                            .background(PaperAlt, KlipShape)
                            .border(2.dp, Line, KlipShape)
                            .clickable {
                                printMonitorViewModel.selectInstance(key)
                            }
                            .padding(16.dp)
                    ) {
                        Text(
                            text = instance.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink
                        )
                    }
                }
            }
        } else {
            val instance = activeInstances.find { (it.id ?: it.name) == selectedId }
            if (instance != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = instance.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink
                    )
                    KlipButton(
                        text = stringResource(R.string.Back),
                        onClick = {
                            printMonitorViewModel.selectInstance(null)
                        }
                    )
                }

                val tempInfo = temperatures[selectedId]

                val extruderCurrent =
                    tempInfo?.extruder?.current?.toFloat() ?: 0f

                val extruderTarget =
                    tempInfo?.extruder?.target?.toFloat() ?: 0f

                val bedCurrent =
                    tempInfo?.bed?.current?.toFloat() ?: 0f

                val bedTarget =
                    tempInfo?.bed?.target?.toFloat() ?: 0f

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    SummaryCard(
                        title = stringResource(R.string.Extruder),
                        temperature = extruderCurrent,
                        target = extruderTarget,
                        color = Color(0xFFF97316),
                        modifier = Modifier.weight(1f)
                    )

                    SummaryCard(
                        title = stringResource(R.string.HotBed),
                        temperature = bedCurrent,
                        target = bedTarget,
                        color = Color(0xFFEAB308),
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.SelectPrinterNoActive),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ink
                )
                KlipButton(
                    text = stringResource(R.string.Back),
                    onClick = {
                        printMonitorViewModel.selectInstance(null)
                    }
                )
            }
        }
    }
}
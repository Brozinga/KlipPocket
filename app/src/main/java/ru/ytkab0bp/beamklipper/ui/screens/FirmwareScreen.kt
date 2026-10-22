package ru.ytkab0bp.beamklipper.ui.screens

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.firmware.FirmwareDownloader
import ru.ytkab0bp.beamklipper.firmware.FirmwareException
import ru.ytkab0bp.beamklipper.firmware.FirmwareKind
import ru.ytkab0bp.beamklipper.firmware.FirmwareStore
import ru.ytkab0bp.beamklipper.firmware.FlashCoordinator
import ru.ytkab0bp.beamklipper.firmware.FlashFailure
import ru.ytkab0bp.beamklipper.firmware.FlashMode
import ru.ytkab0bp.beamklipper.firmware.FlashOptions
import ru.ytkab0bp.beamklipper.firmware.isExperimental
import ru.ytkab0bp.beamklipper.firmware.FlashStage
import ru.ytkab0bp.beamklipper.firmware.FlashState
import ru.ytkab0bp.beamklipper.firmware.Picoboot
import ru.ytkab0bp.beamklipper.serial.KlipperProbeTable
import ru.ytkab0bp.beamklipper.serial.UsbDeviceLabel
import ru.ytkab0bp.beamklipper.ui.components.KlipButton
import ru.ytkab0bp.beamklipper.ui.components.KlipTextButton
import ru.ytkab0bp.beamklipper.ui.components.KlipTile
import ru.ytkab0bp.beamklipper.ui.theme.Accent
import ru.ytkab0bp.beamklipper.ui.theme.Danger
import ru.ytkab0bp.beamklipper.ui.theme.Ink
import ru.ytkab0bp.beamklipper.ui.theme.InkMuted
import ru.ytkab0bp.beamklipper.ui.theme.KlipShape
import ru.ytkab0bp.beamklipper.ui.theme.KlipShapePill
import ru.ytkab0bp.beamklipper.ui.theme.Warn
import ru.ytkab0bp.beamklipper.ui.theme.Line
import ru.ytkab0bp.beamklipper.ui.theme.PaperAlt

private const val WIZARD_STEPS = 4

@Composable
fun FirmwareScreen(onOpenLogs: () -> Unit) {
    val flashState by FlashCoordinator.state.collectAsStateWithLifecycle()
    var wizard by rememberSaveable { mutableStateOf(false) }
    // A flash in progress (or its result) always owns the screen, even after leaving the tab.
    if (wizard || flashState !is FlashState.Idle) {
        FirmwareWizard(
            onClose = { FlashCoordinator.reset(); wizard = false },
            onOpenLogs = { FlashCoordinator.reset(); wizard = false; onOpenLogs() }
        )
    } else {
        FirmwareHome(onInstall = { wizard = true })
    }
}

@Composable
private fun FirmwareHome(onInstall: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showUsb by remember { mutableStateOf(false) }
    var showDownload by remember { mutableStateOf(false) }
    var files by remember { mutableStateOf(FirmwareStore.list()) }
    var deleting by remember { mutableStateOf<FirmwareStore.Entry?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val msg = withContext(Dispatchers.IO) {
                try { context.getString(R.string.FirmwareDownloaded, FirmwareStore.import(context, uri).name) }
                catch (e: Exception) { context.getString(R.string.FirmwareDownloadFailed, e.message ?: "") }
            }
            files = FirmwareStore.list()
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 20.dp)
    ) {
        SectionLabel(stringResource(R.string.FirmwareTitle))
        Row(modifier = Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile(Modifier.weight(1f), R.drawable.ic_grid_layout_outline_28, stringResource(R.string.ListUSB)) { showUsb = true }
            ActionTile(Modifier.weight(1f), R.drawable.ic_firmware_download_28, stringResource(R.string.FirmwareDownload)) { showDownload = true }
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.height(IntrinsicSize.Max)) {
            ActionTile(Modifier.weight(1f), R.drawable.ic_mcu_firmware_outline_28, stringResource(R.string.FirmwareInstall), onClick = onInstall)
        }

        Spacer(Modifier.height(28.dp))
        SectionLabel(stringResource(R.string.FirmwareFiles))
        if (files.isEmpty()) {
            Text(stringResource(R.string.FirmwareNoFiles), style = MaterialTheme.typography.bodyMedium, color = InkMuted, modifier = Modifier.padding(start = 4.dp))
        }
        files.forEach { f ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(KlipShape).background(PaperAlt, KlipShape)
                    .border(2.dp, Line, KlipShape).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(f.name, style = MaterialTheme.typography.titleSmall, color = Ink)
                    Text("${f.kind} · ${formatSize(f.size)}", style = MaterialTheme.typography.bodySmall, color = InkMuted)
                }
                Text(
                    stringResource(R.string.FirmwareDelete), style = MaterialTheme.typography.labelLarge, color = Danger,
                    modifier = Modifier.clip(KlipShape).clickable { deleting = f }.padding(8.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        KlipTextButton(text = stringResource(R.string.FirmwareImport), onClick = { picker.launch(arrayOf("*/*")) })
    }

    if (showUsb) ListUsbDialog(context, onDismiss = { showUsb = false })
    if (showDownload) DownloadDialog(onDismiss = { showDownload = false; files = FirmwareStore.list() })
    deleting?.let { f ->
        KlipAlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(f.name, style = MaterialTheme.typography.titleLarge, color = Ink) },
            text = { Text(stringResource(R.string.FirmwareDelete) + "?", color = Ink) },
            confirmButton = { KlipButton(text = stringResource(R.string.FirmwareDelete), background = Danger, onClick = { FirmwareStore.delete(f); files = FirmwareStore.list(); deleting = null }) },
            dismissButton = { KlipTextButton(text = stringResource(R.string.FirmwareCancel), onClick = { deleting = null }) }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = InkMuted, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
}

@Composable
private fun ActionTile(modifier: Modifier, icon: Int, label: String, onClick: () -> Unit) {
    KlipTile(modifier = modifier.heightIn(min = 120.dp), background = PaperAlt, onClick = onClick) {
        Column {
            Icon(painter = painterResource(icon), contentDescription = null, tint = Ink, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(16.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, color = Ink)
        }
    }
}

private fun formatSize(b: Long) = if (b >= 1024 * 1024) "%.1f MB".format(b / 1048576.0) else "%.1f KB".format(b / 1024.0)

// --- download ----------------------------------------------------------------

@Composable
private fun DownloadDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(TextFieldValue("")) }
    var busy by remember { mutableStateOf(false) }
    var percent by remember { mutableIntStateOf(-1) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var cancel by remember { mutableStateOf(false) }

    KlipAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.FirmwareDownload), style = MaterialTheme.typography.titleLarge, color = Ink) },
        text = {
            Column {
                Text(stringResource(R.string.FirmwareDownloadHint), style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                Spacer(Modifier.height(12.dp))
                KlipTextField(url, { if (!busy) url = it }, stringResource(R.string.FirmwareDownloadUrl) + " (https://…/stm32f401.bin)")
                if (busy) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { if (percent >= 0) percent / 100f else 0f },
                        color = Accent, trackColor = Line, modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.FirmwareDownloading, if (percent >= 0) "$percent%" else "…"), style = MaterialTheme.typography.bodySmall, color = InkMuted, modifier = Modifier.padding(top = 4.dp))
                }
                message?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = if (failed) Danger else Ink)
                }
            }
        },
        confirmButton = {
            KlipButton(
                text = stringResource(R.string.FirmwareDownloadStart),
                enabled = !busy && url.text.isNotBlank(),
                onClick = {
                    busy = true; failed = false; message = null; percent = -1; cancel = false
                    val link = url.text.trim()
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            try {
                                val saved = FirmwareDownloader.download(link, { percent = it }, { cancel })
                                context.getString(R.string.FirmwareDownloaded, saved.joinToString { it.name }) to false
                            } catch (e: Exception) {
                                context.getString(R.string.FirmwareDownloadFailed, e.message ?: e.javaClass.simpleName) to true
                            }
                        }
                        message = result.first; failed = result.second; busy = false
                    }
                }
            )
        },
        dismissButton = {
            KlipTextButton(text = stringResource(if (busy) R.string.FirmwareCancel else R.string.FirmwareDone),
                onClick = { if (busy) cancel = true else onDismiss() })
        }
    )
}

// --- install wizard -----------------------------------------------------------

/** "0x08008000" or "08008000" -> value, only for the STM32 flash window. */
private fun parseAddress(text: String): Long? {
    val v = text.trim().removePrefix("0x").removePrefix("0X").toLongOrNull(16) ?: return null
    return if (v in 0x08000000L until 0x10000000L) v else null
}

private fun modeLabel(m: FlashMode): Int = when (m) {
    FlashMode.USB -> R.string.FirmwareModeUsb; FlashMode.UART -> R.string.FirmwareModeUart
    FlashMode.DFU -> R.string.FirmwareModeDfu; FlashMode.RP2040 -> R.string.FirmwareModeRp2040
}

private fun modeDesc(m: FlashMode): Int = when (m) {
    FlashMode.USB -> R.string.FirmwareModeUsbDesc; FlashMode.UART -> R.string.FirmwareModeUartDesc
    FlashMode.DFU -> R.string.FirmwareModeDfuDesc; FlashMode.RP2040 -> R.string.FirmwareModeRp2040Desc
}

private fun suggestMode(d: UsbDevice): FlashMode = when {
    Picoboot.isBootsel(d) -> FlashMode.RP2040
    d.vendorId == 0x0483 && d.productId == 0xDF11 -> FlashMode.DFU
    d.vendorId == 0x1D50 || d.vendorId == 0x2E8A -> FlashMode.USB
    KlipperProbeTable.getInstance().findDriver(d) != null -> FlashMode.USB
    else -> FlashMode.UART
}

private fun deviceTitle(d: UsbDevice): String {
    val name = UsbDeviceLabel.baseName(try { d.productName } catch (e: SecurityException) { null })
    return (if (name.isEmpty()) "USB" else name) + " · " + "%04x:%04x".format(d.vendorId, d.productId)
}

@Composable
private fun FirmwareWizard(onClose: () -> Unit, onOpenLogs: () -> Unit) {
    val context = LocalContext.current
    val flashState by FlashCoordinator.state.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var fileName by rememberSaveable { mutableStateOf<String?>(null) }
    var deviceName by rememberSaveable { mutableStateOf<String?>(null) }
    var modeName by rememberSaveable { mutableStateOf<String?>(null) }
    var startText by rememberSaveable { mutableStateOf("0x08000000") }
    var files by remember { mutableStateOf(FirmwareStore.list()) }
    val usb = remember { context.getSystemService(Context.USB_SERVICE) as UsbManager }
    var devices by remember { mutableStateOf(usb.deviceList.values.sortedBy { it.deviceName }) }
    val running = flashState is FlashState.Running
    val inRun = flashState !is FlashState.Idle

    // The screen stays on while the board is being written.
    val view = LocalView.current
    DisposableEffect(running) { view.keepScreenOn = running; onDispose { view.keepScreenOn = false } }

    BackHandler(enabled = true) {
        when {
            running -> {}
            inRun -> onClose()
            step > 0 -> step--
            else -> onClose()
        }
    }
    LaunchedEffect(step) {
        while (step == 1) { devices = usb.deviceList.values.sortedBy { it.deviceName }; kotlinx.coroutines.delay(2000) }
    }

    val file = files.firstOrNull { it.name == fileName }
    val device = devices.firstOrNull { it.deviceName == deviceName }
    val mode = modeName?.let { runCatching { FlashMode.valueOf(it) }.getOrNull() }
    // The start address only matters for raw .bin files written to STM32 boards.
    val needsAddress = mode != null && mode != FlashMode.RP2040 && file?.kind == FirmwareKind.BIN
    val startAddress = parseAddress(startText)
    val addressOk = !needsAddress || startAddress != null
    val experimental = mode != null && file != null && isExperimental(mode, file.kind)

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 16.dp)) {
        if (!inRun) {
            Text(
                stringResource(R.string.FirmwareStepOf, step + 1, WIZARD_STEPS).uppercase(),
                style = MaterialTheme.typography.labelMedium, color = InkMuted, modifier = Modifier.padding(start = 4.dp)
            )
            Text(
                stringResource(listOf(R.string.FirmwareStepFile, R.string.FirmwareStepDevice, R.string.FirmwareStepMode, R.string.FirmwareWarnTitle)[step]),
                style = MaterialTheme.typography.headlineSmall, color = if (step == 3) Danger else Ink, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
            )
        }
        val scroll = rememberScrollState()
        LaunchedEffect(step, inRun) { scroll.scrollTo(0) }
        Column(Modifier.weight(1f).verticalScroll(scroll)) {
            if (inRun) RunPanel(flashState) else when (step) {
                0 -> {
                    if (files.isEmpty()) Text(stringResource(R.string.FirmwareNoFiles), color = InkMuted, style = MaterialTheme.typography.bodyMedium)
                    files.forEach { f ->
                        ChoiceRow(f.name, "${f.kind} · ${formatSize(f.size)}", f.name == fileName) { fileName = f.name }
                    }
                }
                1 -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.FirmwareSelectDevice), style = MaterialTheme.typography.bodySmall, color = InkMuted, modifier = Modifier.weight(1f).padding(start = 4.dp))
                        KlipTextButton(stringResource(R.string.FirmwareRefresh), { devices = usb.deviceList.values.sortedBy { it.deviceName } })
                    }
                    Spacer(Modifier.height(8.dp))
                    if (devices.isEmpty()) Text(stringResource(R.string.ListUSBNoDevices), color = InkMuted)
                    devices.forEach { d ->
                        ChoiceRow(deviceTitle(d), d.deviceName, d.deviceName == deviceName) {
                            deviceName = d.deviceName
                            if (mode == null) modeName = suggestMode(d).name
                        }
                    }
                }
                2 -> {
                    FlashMode.values().forEach { m ->
                        val exp = file != null && isExperimental(m, file.kind)
                        ChoiceRow(
                            stringResource(modeLabel(m)),
                            stringResource(modeDesc(m)),
                            m == mode,
                            note = if (exp) stringResource(R.string.FirmwareExperimentalNote) else null
                        ) { modeName = m.name }
                    }
                    if (needsAddress) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.FirmwareStartAddress), style = MaterialTheme.typography.labelMedium, color = InkMuted, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                        KlipTextField(TextFieldValue(startText), { startText = it.text.trim() }, "0x08000000")
                        Text(
                            stringResource(R.string.FirmwareStartAddressHint), style = MaterialTheme.typography.bodySmall,
                            color = if (startAddress == null) Danger else InkMuted, modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                        )
                    }
                }
                else -> {
                    Text(stringResource(R.string.FirmwareWarnText), style = MaterialTheme.typography.bodyLarge, color = Ink)
                    if (experimental) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.FirmwareExperimentalWarn), style = MaterialTheme.typography.bodyMedium, color = Warn,
                            modifier = Modifier.fillMaxWidth().clip(KlipShape).border(2.dp, Warn, KlipShape).padding(12.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.FirmwareSummary, file?.name ?: "", device?.let { deviceTitle(it) } ?: "", mode?.let { stringResource(modeLabel(it)) } ?: ""),
                        style = MaterialTheme.typography.bodyMedium, color = InkMuted
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            when {
                running -> {}
                inRun -> {
                    val done = flashState as? FlashState.Done
                    KlipTextButton(stringResource(R.string.FirmwareViewLog), onOpenLogs)
                    KlipButton(stringResource(R.string.FirmwareDone), onClose)
                }
                else -> {
                    KlipTextButton(stringResource(if (step == 0) R.string.FirmwareCancel else R.string.FirmwareBack), { if (step == 0) onClose() else step-- })
                    if (step < 3) {
                        val ok = when (step) { 0 -> file != null; 1 -> device != null; else -> mode != null && addressOk }
                        KlipButton(stringResource(R.string.FirmwareNext), { step++ }, enabled = ok)
                    } else {
                        KlipButton(stringResource(R.string.FirmwareConfirm), {
                            if (file != null && device != null && mode != null) FlashCoordinator.start(context, file, device, mode, FlashOptions(startAddress ?: FlashOptions.DEFAULT_STM32_START))
                        }, background = Danger, contentColor = ru.ytkab0bp.beamklipper.ui.theme.InkOnAccent)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(title: String, subtitle: String, selected: Boolean, note: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(KlipShape)
            .background(PaperAlt, KlipShape).border(2.dp, if (selected) Accent else Line, KlipShape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkMuted)
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = Warn, modifier = Modifier.padding(top = 2.dp))
        }
        if (selected) Box(Modifier.size(12.dp).background(Accent, KlipShape))
    }
}

@Composable
private fun RunPanel(state: FlashState) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        when (state) {
            is FlashState.Running -> {
                Text(stringResource(R.string.FirmwareFlashing), style = MaterialTheme.typography.headlineSmall, color = Ink)
                Spacer(Modifier.height(16.dp))
                FlashProgressBar(state.overall)
                Spacer(Modifier.height(8.dp))
                Text(stageText(state.stage) + " · ${state.overall}%", style = MaterialTheme.typography.bodyMedium, color = Ink)
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.FirmwareKeepOpen), style = MaterialTheme.typography.bodySmall, color = InkMuted)
            }
            is FlashState.Done -> {
                Text(
                    stringResource(if (state.success) R.string.FirmwareSuccess else R.string.FirmwareFailed),
                    style = MaterialTheme.typography.headlineSmall, color = if (state.success) ru.ytkab0bp.beamklipper.ui.theme.Ok else Danger
                )
                if (state.success) {
                    // A write can be over in a second: the full bar fills here so the result is still visible.
                    Spacer(Modifier.height(16.dp))
                    FlashProgressBar(100)
                }
                if (state.success && state.detail == FlashCoordinator.NOTE_NO_RETURN) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.FirmwareNoReturn), style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                }
                if (!state.success) {
                    Spacer(Modifier.height(12.dp))
                    Text(failureText(state.failure), style = MaterialTheme.typography.bodyLarge, color = Ink)
                    if (state.detail.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.FirmwareDetail, state.detail), style = MaterialTheme.typography.bodySmall, color = InkMuted)
                    }
                }
            }
            FlashState.Idle -> {}
        }
    }
}

/** Fills as the write advances (smoothly, never backwards) and keeps a light sweeping over the filled part. */
@Composable
private fun FlashProgressBar(overall: Int) {
    var best by remember { mutableIntStateOf(0) }
    if (overall > best) best = overall
    // A spring keeps its velocity when the target changes many times per second (a tween would restart and lag behind).
    val shown by animateFloatAsState(best / 100f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 90f), label = "flash-progress")
    val sweep = rememberInfiniteTransition(label = "flash-sweep")
    val x by sweep.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "flash-sweep-x")
    Box(Modifier.fillMaxWidth().height(14.dp).clip(KlipShapePill).background(Line)) {
        Box(
            Modifier.fillMaxWidth(shown.coerceIn(0.02f, 1f)).fillMaxHeight().clip(KlipShapePill).background(Accent)
                .drawWithContent {
                    drawContent()
                    val band = size.height * 8
                    val cx = -band + (size.width + band * 2) * x
                    drawRect(Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.5f), Color.Transparent),
                        startX = cx - band / 2, endX = cx + band / 2))
                }
        )
    }
}

@Composable
private fun stageText(s: FlashStage): String = stringResource(when (s) {
    FlashStage.PREPARE -> R.string.FirmwareStagePrepare
    FlashStage.ENTER_BOOTSEL -> R.string.FirmwareStageEnterBootsel
    FlashStage.WAIT_BOOTSEL -> R.string.FirmwareStageWaitBootsel
    FlashStage.PERMISSION -> R.string.FirmwareStagePermission
    FlashStage.HANDSHAKE -> R.string.FirmwareStageHandshake
    FlashStage.ERASE -> R.string.FirmwareStageErase
    FlashStage.WRITE -> R.string.FirmwareStageWrite
    FlashStage.VERIFY -> R.string.FirmwareStageVerify
    FlashStage.REBOOT -> R.string.FirmwareStageReboot
    FlashStage.WAIT_DEVICE -> R.string.FirmwareStageWaitDevice
})

@Composable
private fun failureText(f: FlashFailure?): String = stringResource(when (f) {
    FlashFailure.INCOMPATIBLE_FILE -> R.string.FirmwareFailIncompat
    FlashFailure.NOT_SERIAL -> R.string.FirmwareFailNotSerial
    FlashFailure.NO_ANSWER -> R.string.FirmwareFailNoAnswer
    FlashFailure.BAD_ADDRESS -> R.string.FirmwareFailAddress
    FlashFailure.WRONG_DEVICE -> R.string.FirmwareFailWrongDevice
    FlashFailure.FILE_INVALID -> R.string.FirmwareFailFileInvalid
    FlashFailure.NEEDS_RP2040_UF2 -> R.string.FirmwareFailNeedsRp2040
    FlashFailure.RP2350 -> R.string.FirmwareFailRp2350
    FlashFailure.PERMISSION_DENIED -> R.string.FirmwareFailPermission
    FlashFailure.NO_BOOTLOADER -> R.string.FirmwareFailBootsel
    FlashFailure.VERIFY_MISMATCH -> R.string.FirmwareFailVerify
    FlashFailure.CANCELLED -> R.string.FirmwareFailCancelled
    else -> R.string.FirmwareFailUsb
})

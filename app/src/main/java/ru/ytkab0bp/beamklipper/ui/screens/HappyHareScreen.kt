package ru.ytkab0bp.beamklipper.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.ui.components.KlipButton
import ru.ytkab0bp.beamklipper.ui.components.KlipSwitch
import ru.ytkab0bp.beamklipper.ui.components.KlipTextButton
import ru.ytkab0bp.beamklipper.ui.components.KlipTile
import ru.ytkab0bp.beamklipper.ui.components.klipScrollbar
import ru.ytkab0bp.beamklipper.ui.state.HappyHareViewModel
import ru.ytkab0bp.beamklipper.ui.state.HhItem
import ru.ytkab0bp.beamklipper.ui.state.HhStage
import ru.ytkab0bp.beamklipper.ui.theme.Accent
import ru.ytkab0bp.beamklipper.ui.theme.Danger
import ru.ytkab0bp.beamklipper.ui.theme.Ink
import ru.ytkab0bp.beamklipper.ui.theme.InkMuted
import ru.ytkab0bp.beamklipper.ui.theme.PaperAlt
import ru.ytkab0bp.beamklipper.ui.theme.Warn

private const val USB_SYMBOL = "MMU_SERIAL_DEVICE_OTHER"

/** The Happy Hare setup: the same questions as Happy Hare's own install menu, answered here instead of on a computer. */
@Composable
fun HappyHareScreen(onClose: () -> Unit, viewModel: HappyHareViewModel = viewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    BackHandler { if (!viewModel.up()) onClose() }
    val scroll = rememberScrollState()
    // A new page starts at its top
    LaunchedEffect(s.stage, s.pathStack) { scroll.scrollTo(0) }

    var editing by remember { mutableStateOf<HhItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .klipScrollbar(scroll)
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (s.busy) Text(stringResource(R.string.HHWorking), style = MaterialTheme.typography.labelMedium, color = InkMuted)
        s.error?.let { message ->
            KlipTile(modifier = Modifier.fillMaxWidth(), background = PaperAlt) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.HHFailed, message), style = MaterialTheme.typography.bodyMedium, color = Danger)
                    s.problems.take(12).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = InkMuted) }
                    KlipTextButton(text = stringResource(android.R.string.ok), onClick = { viewModel.dismissError() })
                }
            }
        }
        when (s.stage) {
            HhStage.PRINTERS -> {
                Title(stringResource(R.string.HHChoosePrinter), stringResource(R.string.HappyHareSubtitle))
                if (s.printers.isEmpty()) Note(stringResource(R.string.HHNoPrinters))
                s.printers.forEach { (id, name) -> NavRow(title = name.ifEmpty { id }, value = null) { viewModel.selectPrinter(id) } }
            }

            HhStage.HOME -> {
                Title(s.printerName.ifEmpty { stringResource(R.string.HappyHare) }, stringResource(R.string.HappyHareSubtitle))
                Note(if (s.installed) stringResource(R.string.HHConfigured, s.types.firstOrNull { it.symbol == s.currentType }?.title ?: "Happy Hare")
                else stringResource(R.string.HHNotConfigured))
                if (s.installed) {
                    KlipButton(text = stringResource(R.string.HHEdit), onClick = { viewModel.start(edit = true) }, modifier = Modifier.fillMaxWidth())
                    KlipTextButton(text = stringResource(R.string.HHStartOver), onClick = { viewModel.start(edit = false) }, modifier = Modifier.fillMaxWidth())
                } else {
                    KlipButton(text = stringResource(R.string.HHStart), onClick = { viewModel.start(edit = false) }, modifier = Modifier.fillMaxWidth())
                }
            }

            HhStage.TYPE -> {
                Title(stringResource(R.string.HHChooseMmu), null)
                s.types.forEach { t -> NavRow(title = t.title, value = null) { viewModel.chooseType(t.symbol) } }
            }

            HhStage.PAGE -> {
                Title(s.pageTitle, null)
                if (s.pageHelp.isNotEmpty()) Note(s.pageHelp)
                if (s.items.none { it.kind != "heading" && it.kind != "note" }) Note(stringResource(R.string.HHNothingHere))
                s.items.forEach { Question(it, onEdit = { item -> editing = item }, onToggle = viewModel::setValue, onOpen = viewModel::openMenu) }
                if (s.pathStack.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    KlipButton(text = stringResource(R.string.HHReview), onClick = { viewModel.review() }, modifier = Modifier.fillMaxWidth())
                }
            }

            HhStage.REVIEW -> {
                Title(stringResource(R.string.HHReview), null)
                if (s.problems.isNotEmpty()) {
                    Heading(stringResource(R.string.HHProblemsTitle), Danger)
                    Note(stringResource(R.string.HHProblemsHelp))
                    s.problems.take(20).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = InkMuted) }
                }
                if (s.warnings.isNotEmpty()) {
                    Heading(stringResource(R.string.HHWarningsTitle), Warn)
                    s.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = InkMuted) }
                }
                Switch(stringResource(R.string.HHIncludePrinter), s.include) { viewModel.setInclude(it) }
                Note(stringResource(R.string.HHIncludeHelp))
                Switch(stringResource(R.string.HHResetState), s.resetState) { viewModel.setResetState(it) }
                Note(stringResource(R.string.HHReplaceNote))
                KlipButton(
                    text = stringResource(R.string.HHInstall), onClick = { viewModel.generate() },
                    enabled = s.problems.isEmpty() && !s.busy, modifier = Modifier.fillMaxWidth()
                )
            }

            HhStage.DONE -> {
                Title(stringResource(R.string.HHDone), null)
                Note(stringResource(R.string.HHDoneRestart))
                if (s.added) {
                    Note(stringResource(R.string.HHAdded))
                } else {
                    Heading(stringResource(R.string.HHManualTitle), Ink)
                    Note(stringResource(R.string.HHManualPrinter))
                    Code(s.includeLines.joinToString("\n"))
                    Note(stringResource(R.string.HHManualMoonraker))
                    Code(s.moonrakerLines.joinToString("\n"))
                    KlipButton(text = stringResource(R.string.HHAddNow), onClick = { viewModel.addNow() }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    editing?.let { item ->
        val usb = if (item.symbol == USB_SYMBOL) {
            LaunchedEffect(Unit) { viewModel.loadUsb() }
            s.usb
        } else emptyList()
        EditDialog(
            item = item, usb = usb,
            onDismiss = { editing = null },
            onChoose = { symbol -> editing = null; viewModel.setValue(symbol, true) },
            onText = { symbol, text -> editing = null; viewModel.setValue(symbol, text) },
            onRestore = { symbol -> editing = null; viewModel.restoreDefault(symbol) },
        )
    }
}

@Composable
private fun Title(title: String, subtitle: String?) {
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Ink)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkMuted)
    }
}

@Composable
private fun Heading(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = InkMuted, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun Code(text: String) {
    KlipTile(modifier = Modifier.fillMaxWidth(), background = PaperAlt) {
        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = Ink)
    }
}

@Composable
private fun NavRow(title: String, value: String?, enabled: Boolean = true, onClick: () -> Unit) {
    KlipTile(modifier = Modifier.fillMaxWidth(), background = PaperAlt, onClick = if (enabled) onClick else null) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) Ink else InkMuted)
                if (!value.isNullOrEmpty()) {
                    Text(value, style = MaterialTheme.typography.bodySmall, color = InkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(painterResource(R.drawable.ic_chevron_right_28), contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun Switch(title: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    KlipTile(modifier = Modifier.fillMaxWidth(), background = PaperAlt, onClick = if (enabled) ({ onChange(!checked) }) else null) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) Ink else InkMuted, modifier = Modifier.weight(1f))
            KlipSwitch(checked = checked, onCheckedChange = if (enabled) onChange else null)
        }
    }
}

@Composable
private fun Question(item: HhItem, onEdit: (HhItem) -> Unit, onToggle: (String, Any) -> Unit, onOpen: (String) -> Unit) {
    val pad = Modifier.padding(start = (item.indent * 12).dp)
    when (item.kind) {
        "heading" -> Heading(item.title, Ink)
        "note" -> Note(item.title)
        "menu" -> Column(pad) { NavRow(item.title, null, enabled = item.enabled) { item.path?.let(onOpen) } }
        "bool" -> Column(pad) { Switch(item.title, item.flag, enabled = !item.fixed) { v -> item.symbol?.let { onToggle(it, v) } } }
        "choice" -> Column(pad) { NavRow(item.title, item.options.firstOrNull { it.symbol == item.value }?.title) { onEdit(item) } }
        else -> Column(pad) { NavRow(item.title, item.value) { onEdit(item) } }
    }
}

@Composable
private fun EditDialog(
    item: HhItem,
    usb: List<ru.ytkab0bp.beamklipper.ui.state.HhUsb>,
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit,
    onText: (String, String) -> Unit,
    onRestore: (String) -> Unit,
) {
    var text by remember(item.id) { mutableStateOf(TextFieldValue(item.value ?: "")) }
    val symbol = item.symbol
    KlipAlertDialog(
        onDismissRequest = onDismiss,
        wideInLandscape = true,
        title = { Text(item.title, style = MaterialTheme.typography.titleLarge, color = Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (item.help.isNotEmpty()) Text(item.help, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                if (item.kind == "choice") {
                    item.options.forEach { o ->
                        KlipTile(modifier = Modifier.fillMaxWidth(), background = if (o.symbol == item.value) Accent else PaperAlt, onClick = { onChoose(o.symbol) }) {
                            Column {
                                Text(o.title, style = MaterialTheme.typography.bodyLarge, color = if (o.symbol == item.value) ru.ytkab0bp.beamklipper.ui.theme.InkOnAccent else Ink)
                                if (o.help.isNotEmpty()) Text(o.help.lines().first(), style = MaterialTheme.typography.bodySmall, color = InkMuted, maxLines = 2)
                            }
                        }
                    }
                } else {
                    KlipTextField(value = text, onValueChange = { text = it }, placeholder = item.title)
                    if (item.symbol == USB_SYMBOL) {
                        if (usb.isNotEmpty()) Text(stringResource(R.string.HHUsbPick), style = MaterialTheme.typography.labelMedium, color = InkMuted)
                        usb.forEach { d ->
                            KlipTile(modifier = Modifier.fillMaxWidth(), background = PaperAlt, onClick = { text = TextFieldValue(d.path) }) {
                                Column {
                                    Text(d.label, style = MaterialTheme.typography.bodyLarge, color = Ink)
                                    Text(d.path, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (item.kind != "choice" && symbol != null) KlipButton(text = stringResource(android.R.string.ok), onClick = { onText(symbol, text.text) })
            else KlipButton(text = stringResource(android.R.string.cancel), onClick = onDismiss)
        },
        dismissButton = {
            if (symbol != null && !item.isDefault && item.kind != "choice") KlipTextButton(text = stringResource(R.string.HHRestoreDefault), onClick = { onRestore(symbol) })
            else if (item.kind != "choice") KlipTextButton(text = stringResource(android.R.string.cancel), onClick = onDismiss)
        }
    )
}

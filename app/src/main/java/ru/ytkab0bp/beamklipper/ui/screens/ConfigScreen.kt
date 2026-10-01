package ru.ytkab0bp.beamklipper.ui.screens

import android.Manifest
import ru.ytkab0bp.beamklipper.ui.theme.Line
import ru.ytkab0bp.beamklipper.ui.theme.KlipShape
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.MainActivity
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.utils.CameraZoom
import ru.ytkab0bp.beamklipper.serial.KlipperProbeTable
import ru.ytkab0bp.beamklipper.serial.UsbSerialManager
import ru.ytkab0bp.beamklipper.ui.components.KlipButton
import ru.ytkab0bp.beamklipper.ui.components.KlipSwitch
import ru.ytkab0bp.beamklipper.ui.components.KlipTextButton
import ru.ytkab0bp.beamklipper.ui.components.KlipTile
import ru.ytkab0bp.beamklipper.ui.components.klipScrollbar
import ru.ytkab0bp.beamklipper.ui.state.SettingsViewModel
import ru.ytkab0bp.beamklipper.update.FrontendUpdateStep
import ru.ytkab0bp.beamklipper.update.VersionCompare
import ru.ytkab0bp.beamklipper.ui.theme.Accent
import ru.ytkab0bp.beamklipper.ui.theme.Ink
import ru.ytkab0bp.beamklipper.ui.theme.InkOnAccent
import ru.ytkab0bp.beamklipper.ui.theme.InkMuted
import ru.ytkab0bp.beamklipper.ui.theme.Paper
import ru.ytkab0bp.beamklipper.ui.theme.PaperAlt
import ru.ytkab0bp.beamklipper.utils.Languages
import ru.ytkab0bp.beamklipper.utils.ObicoLink
import ru.ytkab0bp.beamklipper.utils.Prefs
import java.io.File

@Composable
fun ConfigScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val engine by viewModel.engine.collectAsStateWithLifecycle()
    val webFrontend by viewModel.webFrontend.collectAsStateWithLifecycle()
    val usbNaming by viewModel.usbNaming.collectAsStateWithLifecycle()
    val cameraEnabled by viewModel.cameraEnabled.collectAsStateWithLifecycle()
    val cameraSourceId by viewModel.cameraSourceId.collectAsStateWithLifecycle()
    val cameraRotation by viewModel.cameraRotation.collectAsStateWithLifecycle()
    val cameraResolution by viewModel.cameraResolution.collectAsStateWithLifecycle()
    val cameraZoom by viewModel.cameraZoom.collectAsStateWithLifecycle()
    val octoEverywhereEnabled by viewModel.octoEverywhereEnabled.collectAsStateWithLifecycle()
    val obicoEnabled by viewModel.obicoEnabled.collectAsStateWithLifecycle()
    val obicoServerUrl by viewModel.obicoServerUrl.collectAsStateWithLifecycle()
    val obicoLinked by viewModel.obicoLinked.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val klipperVersion by viewModel.klipperVersion.collectAsStateWithLifecycle()
    val moonrakerVersion by viewModel.moonrakerVersion.collectAsStateWithLifecycle()
    val fluiddVersion by viewModel.fluiddVersion.collectAsStateWithLifecycle()
    val mainsailVersion by viewModel.mainsailVersion.collectAsStateWithLifecycle()
    val voyagerVersion by viewModel.voyagerVersion.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showListUsb by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var showCameraSource by remember { mutableStateOf(false) }
    var octoEverywhereLinkUrl by remember { mutableStateOf<String?>(null) }
    var showOctoEverywhereNotReady by remember { mutableStateOf(false) }
    var showObicoServer by remember { mutableStateOf(false) }
    var showObicoLink by remember { mutableStateOf(false) }
    // Which frontend's confirm/progress dialog is up, plus the release tag
    // it would update to (carried from confirm into the progress dialog).
    var confirmUpdateFrontend by remember { mutableStateOf<String?>(null) }
    var progressUpdate by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Settings-open is the only trigger for a version check — no background
    // timer (see FrontendUpdateChecker).
    LaunchedEffect(Unit) {
        viewModel.refreshVersionChecks()
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshCameraSwitch(granted)
    }

    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .klipScrollbar(scrollState)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 40.dp)
    ) {
        KlipSectionHeader(stringResource(R.string.EngineFrontend))
        Row(modifier = Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KlipTile(
                modifier = Modifier.weight(1f).heightIn(min = 140.dp),
                background = Accent,
                onClick = { viewModel.cycleEngine() }
            ) {
                Column {
                    Icon(
                        painter = painterResource(R.drawable.ic_memory_chip_outline_28),
                        contentDescription = null,
                        tint = InkOnAccent,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.FirmwareEngine),
                        style = MaterialTheme.typography.titleMedium,
                        color = InkOnAccent
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = viewModel.engineTitle(engine),
                        style = MaterialTheme.typography.bodySmall,
                        color = InkOnAccent.copy(alpha = 0.75f)
                    )
                }
            }
            KlipTile(
                modifier = Modifier.weight(1f).heightIn(min = 140.dp),
                background = Accent,
                onClick = { viewModel.cycleFrontend() }
            ) {
                Column {
                    Icon(
                        painter = painterResource(R.drawable.ic_sync_outline_28),
                        contentDescription = null,
                        tint = InkOnAccent,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.WebFrontend),
                        style = MaterialTheme.typography.titleMedium,
                        color = InkOnAccent
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = viewModel.frontendTitle(webFrontend),
                        style = MaterialTheme.typography.bodySmall,
                        color = InkOnAccent.copy(alpha = 0.75f)
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        KlipSectionHeader(stringResource(R.string.SoftwareVersions))
        KlipInfoRow(
            title = stringResource(R.string.KlipperApp),
            value = versionStatusText(klipperVersion.bundled, klipperVersion.latest)
        )
        Spacer(Modifier.height(8.dp))
        KlipInfoRow(
            title = stringResource(R.string.Moonraker),
            value = versionStatusText(moonrakerVersion.bundled, moonrakerVersion.latest)
        )
        Spacer(Modifier.height(8.dp))
        KlipUpdatableRow(
            title = stringResource(R.string.Fluidd),
            value = frontendVersionText(fluiddVersion.active, fluiddVersion.latest),
            showUpdateButton = VersionCompare.tagDiffers(fluiddVersion.active, fluiddVersion.latest),
            onUpdateClick = { confirmUpdateFrontend = Prefs.FRONTEND_FLUIDD }
        )
        Spacer(Modifier.height(8.dp))
        KlipUpdatableRow(
            title = stringResource(R.string.Mainsail),
            value = frontendVersionText(mainsailVersion.active, mainsailVersion.latest),
            showUpdateButton = VersionCompare.tagDiffers(mainsailVersion.active, mainsailVersion.latest),
            onUpdateClick = { confirmUpdateFrontend = Prefs.FRONTEND_MAINSAIL }
        )
        Spacer(Modifier.height(8.dp))
        KlipUpdatableRow(
            title = stringResource(R.string.Voyager),
            value = frontendVersionText(voyagerVersion.active, voyagerVersion.latest),
            showUpdateButton = VersionCompare.tagDiffers(voyagerVersion.active, voyagerVersion.latest),
            onUpdateClick = { confirmUpdateFrontend = Prefs.FRONTEND_VOYAGER }
        )

        Spacer(Modifier.height(28.dp))
        KlipSectionHeader(stringResource(R.string.USB))
        Row(modifier = Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KlipTile(
                modifier = Modifier.weight(1f).heightIn(min = 140.dp),
                background = PaperAlt,
                onClick = { viewModel.cycleUsbNaming() }
            ) {
                Column {
                    Icon(
                        painter = painterResource(R.drawable.ic_usb_cable_28),
                        contentDescription = null,
                        tint = Ink,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.USBDeviceNaming),
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = viewModel.usbNamingTitle(usbNaming),
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted
                    )
                }
            }
            KlipTile(
                modifier = Modifier.weight(1f).heightIn(min = 140.dp),
                background = PaperAlt,
                onClick = { showListUsb = true }
            ) {
                Column {
                    Icon(
                        painter = painterResource(R.drawable.ic_grid_layout_outline_28),
                        contentDescription = null,
                        tint = Ink,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.ListUSB),
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        KlipSectionHeader(stringResource(R.string.Camera))
        KlipSwitchRow(
            title = stringResource(R.string.EnableCamera),
            checked = cameraEnabled,
            onCheckedChange = { checked ->
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                } else {
                    viewModel.setCameraEnabled(checked)
                }
            }
        )
        Spacer(Modifier.height(8.dp))
        KlipValueRow(
            title = stringResource(R.string.CameraSource),
            value = viewModel.cameraSourceTitle(cameraSourceId),
            onClick = { showCameraSource = true }
        )
        Spacer(Modifier.height(8.dp))
        KlipValueRow(
            title = stringResource(R.string.CameraRotation),
            value = "$cameraRotation°",
            onClick = { viewModel.cycleCameraRotation() }
        )
        Spacer(Modifier.height(8.dp))
        KlipValueRow(
            title = stringResource(R.string.CameraResolution),
            value = viewModel.cameraResolutionTitle(cameraResolution),
            onClick = { viewModel.cycleCameraResolution() }
        )
        Spacer(Modifier.height(8.dp))
        // Re-read on every source change: the steps depend on the camera.
        val zoomOptions = remember(cameraSourceId) { viewModel.cameraZoomOptions() }
        KlipValueRow(
            title = stringResource(R.string.CameraZoom),
            value = if (zoomOptions.size > 1)
                CameraZoom.label(viewModel.effectiveCameraZoom(cameraZoom))
            else stringResource(R.string.CameraZoomUnsupported),
            onClick = { viewModel.cycleCameraZoom() }
        )

        Spacer(Modifier.height(28.dp))
        KlipSectionHeader(stringResource(R.string.RemoteAccess))
        KlipSwitchRow(
            title = stringResource(R.string.EnableOctoEverywhere),
            checked = octoEverywhereEnabled,
            onCheckedChange = { checked -> viewModel.setOctoEverywhereEnabled(checked) }
        )
        if (octoEverywhereEnabled) {
            Spacer(Modifier.height(8.dp))
            KlipValueRow(
                title = stringResource(R.string.LinkOctoEverywhere),
                value = stringResource(R.string.LinkOctoEverywhereHint),
                onClick = {
                    val url = viewModel.octoEverywhereLinkUrl()
                    if (url != null) octoEverywhereLinkUrl = url else showOctoEverywhereNotReady = true
                }
            )
        }

        Spacer(Modifier.height(28.dp))
        KlipSectionHeader(stringResource(R.string.Obico))
        KlipSwitchRow(
            title = stringResource(R.string.EnableObico),
            checked = obicoEnabled,
            onCheckedChange = { checked -> viewModel.setObicoEnabled(checked) }
        )
        if (obicoEnabled) {
            Spacer(Modifier.height(8.dp))
            KlipValueRow(
                title = stringResource(R.string.ObicoServer),
                value = viewModel.obicoServerLabel(obicoServerUrl),
                onClick = { showObicoServer = true }
            )
            Spacer(Modifier.height(8.dp))
            KlipValueRow(
                title = stringResource(R.string.ObicoLink),
                value = stringResource(if (obicoLinked) R.string.ObicoLinked else R.string.ObicoNotLinked),
                onClick = { showObicoLink = true }
            )
        }

        Spacer(Modifier.height(28.dp))
        KlipSectionHeader(stringResource(R.string.Other))
        Row(modifier = Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (context is MainActivity && context.isCurrentLauncher()) {
                KlipTile(
                    modifier = Modifier.weight(1f).heightIn(min = 140.dp),
                    background = PaperAlt,
                    onClick = { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                ) {
                    Column {
                        Icon(
                            painter = painterResource(R.drawable.ic_services_outline_28),
                            contentDescription = null,
                            tint = Ink,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = stringResource(R.string.SystemSettings),
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink
                        )
                    }
                }
            }
            KlipTile(
                modifier = Modifier.weight(1f).heightIn(min = 140.dp),
                background = PaperAlt,
                onClick = { showQr = true }
            ) {
                Column {
                    Icon(
                        painter = painterResource(R.drawable.ic_download_outline_28),
                        contentDescription = null,
                        tint = Ink,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.OtherGetFirmware),
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        KlipSectionHeader(stringResource(R.string.AppSettings))
        KlipTile(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp),
            background = Accent,
            onClick = { showLanguage = true }
        ) {
            Column {
                Icon(
                    painter = painterResource(R.drawable.ic_globe_outline_28),
                    contentDescription = null,
                    tint = InkOnAccent,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.AppLanguage),
                    style = MaterialTheme.typography.titleMedium,
                    color = InkOnAccent
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = viewModel.languageTitle(appLanguage),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkOnAccent.copy(alpha = 0.75f)
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        KlipSectionHeader(stringResource(R.string.About))
        KlipTile(
            modifier = Modifier.fillMaxWidth(),
            background = PaperAlt
        ) {
            Column {
                Text(
                    text = stringResource(R.string.IntroTitle),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Ink
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.IntroText),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ink
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        KlipTile(
            modifier = Modifier.fillMaxWidth(),
            background = PaperAlt
        ) {
            Text(
                text = stringResource(R.string.PrivacyNote),
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted
            )
        }

        Spacer(Modifier.height(12.dp))
        KlipTile(
            modifier = Modifier.fillMaxWidth(),
            background = PaperAlt
        ) {
            Column {
                Text(
                    text = stringResource(R.string.CreditsTitle),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Ink
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.CreditsText),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        KlipSectionHeader("Information")
        KlipTile(
            modifier = Modifier.fillMaxWidth(),
            background = PaperAlt,
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/utkabobr/BeamKlipper")))
            }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_external_link_outline_24),
                    contentDescription = null,
                    tint = Ink,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.OriginalBeamKlipper),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ink,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    painterResource(R.drawable.ic_chevron_right_28),
                    contentDescription = null,
                    tint = Ink,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        KlipTile(
            modifier = Modifier.fillMaxWidth(),
            background = PaperAlt,
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Brozinga/KlipPocket")))
            }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_github_28),
                    contentDescription = null,
                    tint = Ink,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.GitHubRepo),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ink,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    painterResource(R.drawable.ic_chevron_right_28),
                    contentDescription = null,
                    tint = Ink,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showListUsb) {
        ListUsbDialog(context, onDismiss = { showListUsb = false })
    }
    if (showQr) {
        QRCodeDialog(
            link = "https://github.com/utkabobr/klipper/releases/tag/prebuilt-v0.12.0",
            onDismiss = { showQr = false }
        )
    }
    if (showLanguage) {
        LanguageDialog(onDismiss = { showLanguage = false })
    }
    if (showCameraSource) {
        CameraSourceDialog(
            options = viewModel.cameraSourceOptions(),
            selectedId = cameraSourceId,
            onSelect = { viewModel.setCameraSource(it) },
            onDismiss = { showCameraSource = false }
        )
    }
    octoEverywhereLinkUrl?.let { url ->
        QRCodeDialog(link = url, onDismiss = { octoEverywhereLinkUrl = null })
    }
    if (showOctoEverywhereNotReady) {
        KlipAlertDialog(
            onDismissRequest = { showOctoEverywhereNotReady = false },
            title = { Text(stringResource(R.string.LinkOctoEverywhere), style = MaterialTheme.typography.titleLarge, color = Ink) },
            text = { Text(stringResource(R.string.LinkOctoEverywhereNotReady), color = Ink) },
            confirmButton = {
                KlipButton(text = stringResource(android.R.string.ok), onClick = { showOctoEverywhereNotReady = false })
            }
        )
    }
    if (showObicoServer) {
        ObicoServerDialog(
            viewModel = viewModel,
            currentUrl = obicoServerUrl,
            onDismiss = { showObicoServer = false }
        )
    }
    if (showObicoLink) {
        ObicoLinkDialog(
            viewModel = viewModel,
            linked = obicoLinked,
            onDismiss = { showObicoLink = false }
        )
    }
    confirmUpdateFrontend?.let { frontend ->
        val status = when (frontend) {
            Prefs.FRONTEND_FLUIDD -> fluiddVersion
            Prefs.FRONTEND_MAINSAIL -> mainsailVersion
            else -> voyagerVersion
        }
        val latest = status.latest
        if (latest != null) {
            FrontendUpdateConfirmDialog(
                viewModel = viewModel,
                title = viewModel.frontendTitle(frontend),
                fromVersion = status.active,
                toVersion = latest,
                onConfirm = {
                    confirmUpdateFrontend = null
                    progressUpdate = frontend to latest
                },
                onDismiss = { confirmUpdateFrontend = null }
            )
        } else {
            confirmUpdateFrontend = null
        }
    }
    progressUpdate?.let { (frontend, targetTag) ->
        FrontendUpdateProgressDialog(
            viewModel = viewModel,
            frontend = frontend,
            targetTag = targetTag,
            onDone = { progressUpdate = null }
        )
    }
}

@Composable
private fun KlipSectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = InkMuted,
        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
    )
}

@Composable
private fun KlipSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    KlipTile(
        modifier = Modifier.fillMaxWidth(),
        background = PaperAlt,
        onClick = { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = Ink,
                modifier = Modifier.weight(1f)
            )
            KlipSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun KlipValueRow(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    KlipTile(
        modifier = Modifier.fillMaxWidth(),
        background = PaperAlt,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ink
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                painterResource(R.drawable.ic_chevron_right_28),
                contentDescription = null,
                tint = Ink,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// Klipper/Moonraker: display-only, never an update affordance (see
// docs/klipper-vendor-update-procedure — native chelper + fragile source
// patches make an in-app update unsafe). No chevron, no onClick, unlike
// KlipValueRow, so it doesn't look tappable.
@Composable
private fun KlipInfoRow(title: String, value: String) {
    KlipTile(modifier = Modifier.fillMaxWidth(), background = PaperAlt) {
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = Ink)
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// Fluidd/Mainsail/Voyager-UI: the row itself isn't clickable (unlike
// KlipValueRow) since its one action is the nested Update button — two
// clickables on the same tile would conflict.
@Composable
private fun KlipUpdatableRow(
    title: String,
    value: String,
    showUpdateButton: Boolean,
    onUpdateClick: () -> Unit
) {
    KlipTile(modifier = Modifier.fillMaxWidth(), background = PaperAlt) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, color = Ink)
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showUpdateButton) {
                Spacer(Modifier.width(12.dp))
                KlipButton(text = stringResource(R.string.FrontendUpdateAction), onClick = onUpdateClick)
            }
        }
    }
}

@Composable
private fun versionStatusText(bundled: String?, latest: String?): String {
    if (bundled == null) return stringResource(R.string.FrontendVersionUnknown)
    return if (VersionCompare.tagBehind(bundled, latest)) {
        stringResource(R.string.FrontendVersionAvailable, bundled, latest ?: "?")
    } else {
        stringResource(R.string.FrontendVersionUpToDate, bundled)
    }
}

@Composable
private fun frontendVersionText(active: String, latest: String?): String {
    return if (VersionCompare.tagDiffers(active, latest)) {
        stringResource(R.string.FrontendVersionAvailable, active, latest!!)
    } else {
        stringResource(R.string.FrontendVersionUpToDate, active)
    }
}

@Composable
private fun FrontendUpdateConfirmDialog(
    viewModel: SettingsViewModel,
    title: String,
    fromVersion: String,
    toVersion: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // isPrintActive() does a network round trip (best-effort, short timeout)
    // — computed off the main thread once per dialog open, never blocking
    // composition.
    var printActive by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        printActive = withContext(Dispatchers.IO) { viewModel.isPrintActive() }
    }
    KlipAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.FrontendUpdateConfirmTitle, title), style = MaterialTheme.typography.titleLarge, color = Ink) },
        text = {
            Column {
                Text(stringResource(R.string.FrontendUpdateConfirmMessage, fromVersion, toVersion), color = Ink)
                if (printActive) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.FrontendUpdatePrintWarning),
                        style = MaterialTheme.typography.bodySmall,
                        color = Accent
                    )
                }
            }
        },
        confirmButton = {
            KlipButton(text = stringResource(R.string.FrontendUpdateAction), onClick = onConfirm)
        },
        dismissButton = {
            KlipButton(text = stringResource(android.R.string.cancel), onClick = onDismiss)
        }
    )
}

@Composable
private fun FrontendUpdateProgressDialog(
    viewModel: SettingsViewModel,
    frontend: String,
    targetTag: String,
    onDone: () -> Unit
) {
    var step by remember { mutableStateOf<FrontendUpdateStep>(FrontendUpdateStep.Downloading(0)) }

    LaunchedEffect(frontend, targetTag) {
        viewModel.updateFrontend(frontend, targetTag) { step = it }
        if (step is FrontendUpdateStep.Done) {
            delay(1200)
            onDone()
        }
    }

    val isTerminal = step is FrontendUpdateStep.Done || step is FrontendUpdateStep.Error
    KlipAlertDialog(
        onDismissRequest = { if (isTerminal) onDone() },
        title = { Text(stringResource(R.string.FrontendUpdateAction), style = MaterialTheme.typography.titleLarge, color = Ink) },
        text = {
            Column {
                when (val s = step) {
                    is FrontendUpdateStep.Downloading -> {
                        Text(stringResource(R.string.FrontendUpdateDownloading, s.percent), color = Ink)
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .background(PaperAlt, KlipShape)
                                .border(2.dp, Line, KlipShape)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = (s.percent / 100f).coerceIn(0f, 1f))
                                    .background(Accent, KlipShape)
                            )
                        }
                    }
                    FrontendUpdateStep.Extracting -> Text(stringResource(R.string.FrontendUpdateExtracting), color = Ink)
                    FrontendUpdateStep.Replacing -> Text(stringResource(R.string.FrontendUpdateReplacing), color = Ink)
                    FrontendUpdateStep.Done -> Text(stringResource(R.string.FrontendUpdateDone), color = Ink)
                    is FrontendUpdateStep.Error -> Text(
                        s.message?.let { stringResource(R.string.FrontendUpdateError, it) } ?: stringResource(R.string.FrontendUpdateErrorGeneric),
                        color = Accent
                    )
                }
            }
        },
        confirmButton = {
            if (isTerminal) {
                KlipButton(text = stringResource(android.R.string.ok), onClick = onDone)
            }
        }
    )
}

@Composable
internal fun KlipAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismissRequest) {
        // Dialog windows are bounded to the screen, but a plain wrap-content
        // Column doesn't know that — on a short viewport (landscape) content
        // that doesn't fit just gets clipped by the window edge with no way
        // to reach it, since nothing here was scrollable. BoxWithConstraints
        // gives the real available height so the middle (text()) can be
        // capped and scrolled while title/buttons stay fully visible.
        BoxWithConstraints(modifier = Modifier.padding(20.dp)) {
            val dialogMaxHeight = maxHeight
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = dialogMaxHeight)
                    .background(PaperAlt, KlipShape)
                    .border(2.dp, Line, KlipShape)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    title()
                    Spacer(Modifier.height(8.dp))
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .klipScrollbar(scrollState)
                            .verticalScroll(scrollState)
                    ) {
                        text()
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        dismissButton?.let {
                            it()
                            Spacer(Modifier.width(8.dp))
                        }
                        confirmButton()
                    }
                }
            }
        }
    }
}

@Composable
private fun ListUsbDialog(context: Context, onDismiss: () -> Unit) {
    val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    val list = mutableListOf<String>()
    for (dev in manager.deviceList.values) {
        val drv = KlipperProbeTable.getInstance().findDriver(dev)
        list.add(
            Integer.toHexString(dev.vendorId) + "/" + Integer.toHexString(dev.productId) +
                    " - " + dev.deviceName +
                    (if (drv != null) " - " + drv.name + "\n" +
                            File(KlipperApp.INSTANCE.filesDir, "serial/" + UsbSerialManager.getUID(dev)).absolutePath else "")
        )
    }
    KlipAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ListUSBTitle), style = MaterialTheme.typography.titleLarge, color = Ink) },
        text = {
            if (list.isEmpty()) {
                Text(stringResource(R.string.ListUSBNoDevices), color = Ink)
            } else {
                Column {
                    list.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Ink) }
                }
            }
        },
        confirmButton = {
            KlipButton(
                text = stringResource(android.R.string.ok),
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun CameraSourceDialog(
    options: List<SettingsViewModel.CameraSourceOption>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    KlipAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.CameraSourceTitle), style = MaterialTheme.typography.titleLarge, color = Ink) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(KlipShape)
                            .clickable {
                                onSelect(option.id)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            option.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (option.id == selectedId) Accent else Ink,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            KlipButton(
                text = stringResource(android.R.string.cancel),
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun LanguageDialog(onDismiss: () -> Unit) {
    val options = Languages.ALL.map { stringResource(Languages.nameRes(it)) }
    KlipAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.AppLanguage), style = MaterialTheme.typography.titleLarge, color = Ink) },
        text = {
            Column {
                options.forEachIndexed { index, label ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(KlipShape)
                            .clickable {
                                Prefs.appLanguage = Languages.ALL[index]
                                Prefs.applyAppLanguage()
                                onDismiss()
                            }
                            .padding(vertical = 12.dp)
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyLarge, color = Ink)
                    }
                }
            }
        },
        confirmButton = {
            KlipButton(
                text = stringResource(android.R.string.cancel),
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun KlipTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        cursorBrush = SolidColor(Ink),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PaperAlt, KlipShape)
                    .border(2.dp, Line, KlipShape)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (value.text.isEmpty()) {
                    Text(text = placeholder, style = MaterialTheme.typography.bodyLarge, color = InkMuted)
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun ObicoServerDialog(
    viewModel: SettingsViewModel,
    currentUrl: String,
    onDismiss: () -> Unit
) {
    var cloud by remember { mutableStateOf(viewModel.isObicoCloud(currentUrl)) }
    var selfHostedUrl by remember {
        mutableStateOf(TextFieldValue(if (viewModel.isObicoCloud(currentUrl)) "" else currentUrl))
    }
    KlipAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ObicoServer), style = MaterialTheme.typography.titleLarge, color = Ink) },
        text = {
            Column {
                Text(stringResource(R.string.ObicoServerHint), style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(KlipShape)
                        .clickable { cloud = true }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.ObicoServerCloud),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (cloud) Accent else Ink,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(KlipShape)
                        .clickable { cloud = false }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.ObicoServerSelfHosted),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (!cloud) Accent else Ink,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!cloud) {
                    Spacer(Modifier.height(8.dp))
                    KlipTextField(
                        value = selfHostedUrl,
                        onValueChange = { selfHostedUrl = it },
                        placeholder = stringResource(R.string.ObicoServerSelfHostedHint)
                    )
                }
            }
        },
        confirmButton = {
            KlipButton(
                text = stringResource(android.R.string.ok),
                onClick = {
                    viewModel.setObicoServer(cloud, selfHostedUrl.text)
                    onDismiss()
                }
            )
        },
        dismissButton = {
            KlipButton(text = stringResource(android.R.string.cancel), onClick = onDismiss)
        }
    )
}

@Composable
private fun ObicoLinkDialog(
    viewModel: SettingsViewModel,
    linked: Boolean,
    onDismiss: () -> Unit
) {
    var code by remember { mutableStateOf(TextFieldValue("")) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var discoveryStatus by remember { mutableStateOf<ObicoLink.DiscoveryStatus?>(null) }
    var justLinked by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val invalidCodeText = stringResource(R.string.ObicoInvalidCode)
    val networkErrorText = stringResource(R.string.ObicoNetworkError)

    // moonraker_obico generates its own one-time passcode on the server's
    // side (PrinterDiscovery, running inside ObicoService as soon as Obico
    // is enabled) — the code Obico's "Klipper (self-installed)" onboarding
    // actually expects. It refreshes every couple seconds and the process
    // may take a moment to come up after enabling the toggle, so this polls
    // rather than reading it once.
    LaunchedEffect(linked) {
        if (linked) return@LaunchedEffect
        while (true) {
            val status = withContext(Dispatchers.IO) { viewModel.syncObicoLinkStatus() }
            discoveryStatus = status
            if (status?.isLinked == true) {
                justLinked = true
                delay(1200)
                onDismiss()
                break
            }
            delay(2000)
        }
    }

    KlipAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ObicoLink), style = MaterialTheme.typography.titleLarge, color = Ink) },
        text = {
            Column {
                if (linked || justLinked) {
                    Text(stringResource(R.string.ObicoAlreadyLinkedHint), color = Ink)
                } else {
                    Text(stringResource(R.string.ObicoLinkHint), style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                    Spacer(Modifier.height(16.dp))

                    val passcode = discoveryStatus?.passcode
                    if (!passcode.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.ObicoGeneratedCodeLabel),
                            style = MaterialTheme.typography.labelMedium,
                            color = InkMuted
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(KlipShape)
                                .background(PaperAlt, KlipShape)
                                .border(2.dp, Line, KlipShape)
                                .clickable {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Obico code", passcode))
                                    Toast.makeText(context, context.getString(R.string.ObicoCodeCopied), Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = passcode,
                                style = MaterialTheme.typography.headlineMedium,
                                color = Ink,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.ObicoCodeTapToCopy),
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted
                        )
                        val passlink = discoveryStatus?.passlink
                        if (!passlink.isNullOrBlank()) {
                            Spacer(Modifier.height(10.dp))
                            KlipTextButton(
                                text = stringResource(R.string.ObicoOpenLink),
                                onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(passlink)))
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.ObicoWaitingForCode),
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkMuted
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.ObicoManualCodeHint),
                        style = MaterialTheme.typography.labelMedium,
                        color = InkMuted
                    )
                    Spacer(Modifier.height(8.dp))
                    KlipTextField(
                        value = code,
                        onValueChange = { code = it; error = null },
                        placeholder = stringResource(R.string.ObicoLinkCodeHint)
                    )
                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = Accent, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            if (linked || justLinked) {
                if (linked) {
                    KlipButton(
                        text = stringResource(R.string.ObicoUnlink),
                        onClick = {
                            viewModel.unlinkObico()
                            onDismiss()
                        }
                    )
                }
            } else {
                KlipButton(
                    text = if (loading) stringResource(R.string.ObicoLinking) else stringResource(R.string.ObicoLinkAction),
                    onClick = {
                        if (loading) return@KlipButton
                        loading = true
                        error = null
                        scope.launch {
                            when (val result = viewModel.linkObico(code.text)) {
                                is ObicoLink.Result.Success -> onDismiss()
                                is ObicoLink.Result.InvalidCode -> {
                                    loading = false
                                    error = invalidCodeText
                                }
                                is ObicoLink.Result.NetworkError -> {
                                    loading = false
                                    error = result.message?.let { "$networkErrorText: $it" } ?: networkErrorText
                                }
                            }
                        }
                    }
                )
            }
        },
        dismissButton = {
            KlipButton(text = stringResource(android.R.string.cancel), onClick = onDismiss)
        }
    )
}

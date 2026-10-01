package ru.ytkab0bp.beamklipper.ui.screens

import android.content.Context
import ru.ytkab0bp.beamklipper.ui.theme.PaperAlt
import ru.ytkab0bp.beamklipper.ui.theme.KlipShapePill
import ru.ytkab0bp.beamklipper.ui.theme.Line
import ru.ytkab0bp.beamklipper.ui.theme.KlipShape
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ytkab0bp.beamklipper.KlipperInstance
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.service.WebService
import ru.ytkab0bp.beamklipper.ui.components.KlipButton
import ru.ytkab0bp.beamklipper.ui.components.KlipTextButton
import ru.ytkab0bp.beamklipper.ui.components.KlipTile
import ru.ytkab0bp.beamklipper.ui.state.MainViewModel
import ru.ytkab0bp.beamklipper.ui.theme.Accent
import ru.ytkab0bp.beamklipper.ui.theme.Ink
import ru.ytkab0bp.beamklipper.ui.theme.Danger
import ru.ytkab0bp.beamklipper.ui.theme.InkOnAccent
import ru.ytkab0bp.beamklipper.ui.theme.InkMuted
import ru.ytkab0bp.beamklipper.ui.theme.Paper
import ru.ytkab0bp.beamklipper.utils.Frontends
import ru.ytkab0bp.beamklipper.utils.Prefs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    isCurrentLauncher: Boolean,
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val instances by mainViewModel.instances.collectAsStateWithLifecycle()
    val instanceStates by mainViewModel.instanceStates.collectAsStateWithLifecycle()
    val webState by mainViewModel.webState.collectAsStateWithLifecycle()
    val webFrontend by mainViewModel.webFrontend.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var deleteInstance by remember { mutableStateOf<KlipperInstance?>(null) }
    var noFreeSlots by remember { mutableStateOf(false) }
    var editorInstance by remember { mutableStateOf<KlipperInstance?>(null) }
    var editorVisible by remember { mutableStateOf(false) }
    val anyRunning by remember(instances, instanceStates) {
        derivedStateOf {
            instances.any {
                val s = instanceStates[it.id ?: it.name] ?: it.getState()
                s == KlipperInstance.State.RUNNING || s == KlipperInstance.State.STARTING
            }
        }
    }

    val listState = rememberLazyListState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Paper)
            ) {
                Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)) {
                        val running = webState == KlipperInstance.State.RUNNING
                        val webBg = if (running) Accent else PaperAlt
                        val webFg = if (running) InkOnAccent else Ink
                        val webFgMuted = if (running) InkOnAccent.copy(alpha = 0.7f) else InkMuted
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clip(KlipShape)
                                .background(webBg, KlipShape)
                                .border(2.dp, Line, KlipShape)
                                .clickable(
                                    onClick = {
                                        if (running) {
                                            openWebFrontend(context)
                                        } else {
                                            mainViewModel.runStopAll()
                                        }
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(Frontends.iconRes(webFrontend)),
                                    contentDescription = null,
                                    tint = webFg,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(Frontends.nameRes(webFrontend)),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = webFg,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (running) {
                                        Text(
                                            text = webIpInfo(context),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = webFgMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, top = 0.dp, end = 20.dp, bottom = 140.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                stickyHeader(key = "instances-label", contentType = "label") {
                Text(
                    text = "INSTANCES",
                    style = MaterialTheme.typography.labelMedium,
                    color = Ink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Paper)
                        .padding(start = 4.dp, top = 8.dp, bottom = 8.dp)
                )
            }

            items(
                instances.chunked(2),
                key = { it.joinToString("|") { inst -> inst.id ?: inst.name } },
                contentType = { "inst-row" }
            ) { chunk ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    chunk.forEach { inst ->
                        key(inst.id ?: inst.name) {
                            val k = inst.id ?: inst.name
                            val stateSnapshot by rememberUpdatedState(instanceStates)
                            val stateVal by remember(instanceStates, k) {
                                derivedStateOf { stateSnapshot[k] ?: inst.getState() }
                            }
                            val onClickInst by rememberUpdatedState(onClick@{
                                val id = inst.id ?: return@onClick
                                val canonical = KlipperInstance.getInstance(id) ?: return@onClick
                                editorInstance = canonical
                                editorVisible = true
                            })
                            val onLongClickInst by rememberUpdatedState(onLongClick@{
                                val id = inst.id ?: return@onLongClick
                                val canonical = KlipperInstance.getInstance(id) ?: return@onLongClick
                                deleteInstance = canonical
                            })
                            val capturedInst = inst
                            val onToggleInst by rememberUpdatedState(onToggle@{
                                when (stateVal) {
                                    KlipperInstance.State.STARTING, KlipperInstance.State.STOPPING -> {}
                                    KlipperInstance.State.IDLE -> {
                                        if (!KlipperInstance.hasFreeSlots()) {
                                            noFreeSlots = true
                                        } else {
                                            mainViewModel.toggle(capturedInst)
                                        }
                                    }
                                    else -> mainViewModel.toggle(capturedInst)
                                }
                            })
                            val instBg = PaperAlt
                            val toggleBg = when (stateVal) {
                                KlipperInstance.State.RUNNING, KlipperInstance.State.STOPPING -> Danger
                                else -> Accent
                            }
                            KlipTile(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 130.dp)
                                    .combinedClickable(
                                        onClick = onClickInst,
                                        onLongClick = onLongClickInst
                                    ),
                                background = instBg
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            painter = painterResource(inst.icon.drawable),
                                            contentDescription = null,
                                            tint = Ink,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = inst.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(16.dp)
                                    ) {
                                        val statusText: String? = when (stateVal) {
                                            KlipperInstance.State.STARTING -> stringResource(R.string.InstanceStarting)
                                            KlipperInstance.State.STOPPING -> stringResource(R.string.InstanceStopping)
                                            KlipperInstance.State.RUNNING -> stringResource(R.string.InstanceRunning)
                                            KlipperInstance.State.IDLE -> stringResource(R.string.InstanceIdle)
                                        }
                                        if (statusText != null) {
                                            Text(
                                                text = statusText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = InkMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(KlipShapePill)
                                                .background(toggleBg, KlipShapePill)
                                                .clickable(
                                                    enabled = stateVal != KlipperInstance.State.STARTING && stateVal != KlipperInstance.State.STOPPING,
                                                    onClick = onToggleInst
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painterResource(if (stateVal == KlipperInstance.State.RUNNING || stateVal == KlipperInstance.State.STOPPING) R.drawable.ic_stop_24 else R.drawable.ic_play_28),
                                                contentDescription = null,
                                                tint = InkOnAccent,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (chunk.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        }

        val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val fabSize = if (landscape) 56.dp else 72.dp
        Row(
            modifier = Modifier
                .align(if (landscape) Alignment.BottomEnd else Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = if (landscape) 12.dp else 32.dp),
            horizontalArrangement = Arrangement.spacedBy(if (landscape) 16.dp else 32.dp)
        ) {
            val fabShape = KlipShapePill
            Box(
                modifier = Modifier.size(fabSize)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(fabShape)
                        .background(Accent, fabShape)
                        .let {
                                it.clickable(
                                    onClick = {
                                        if (instances.size >= KlipperInstance.SLOTS_COUNT) {
                                            noFreeSlots = true
                                        } else {
                                            editorInstance = null
                                            editorVisible = true
                                        }
                                    }
                                )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(R.drawable.ic_add_outline_28),
                        contentDescription = stringResource(R.string.NewInstance),
                        tint = InkOnAccent,
                        modifier = Modifier.size(fabSize / 2)
                    )
                }
            }
            if (instances.isNotEmpty()) {
                Box(
                    modifier = Modifier.size(fabSize)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(fabShape)
                            .background(if (anyRunning) Danger else Accent, fabShape)
                                .let {
                                it.clickable(onClick = { mainViewModel.runStopAll() })
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painterResource(if (anyRunning) R.drawable.ic_stop_24 else R.drawable.ic_play_28),
                            contentDescription = null,
                            tint = InkOnAccent,
                            modifier = Modifier.size(fabSize / 2)
                        )
                    }
                }
            }
        }
    }

    deleteInstance?.let { inst ->
        Dialog(onDismissRequest = { deleteInstance = null }) {
            val dlgShape = KlipShape
            Box(modifier = Modifier.padding(20.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PaperAlt, dlgShape)
                        .border(2.dp, Line, dlgShape)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = stringResource(R.string.InstanceDelete, inst.name),
                            style = MaterialTheme.typography.titleLarge,
                            color = Ink
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.InstanceDeleteConfirm),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            KlipTextButton(
                                text = stringResource(android.R.string.cancel),
                                onClick = { deleteInstance = null }
                            )
                            Spacer(Modifier.width(8.dp))
                            KlipButton(
                                text = stringResource(android.R.string.ok),
                                onClick = {
                                    mainViewModel.delete(inst)
                                    deleteInstance = null
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (noFreeSlots) {
        Dialog(onDismissRequest = { noFreeSlots = false }) {
            val dlgShape = KlipShape
            Box(modifier = Modifier.padding(20.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PaperAlt, dlgShape)
                        .border(2.dp, Line, dlgShape)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = stringResource(R.string.NoFreeSlotsDescription, KlipperInstance.SLOTS_COUNT),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Ink
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            KlipButton(
                                text = stringResource(android.R.string.ok),
                                onClick = { noFreeSlots = false }
                            )
                        }
                    }
                }
            }
        }
    }

    if (editorVisible) {
        InstanceEditorSheet(
            editInstance = editorInstance,
            onDismiss = { editorVisible = false }
        )
    }
}

private fun openWebFrontend(context: Context) {
    val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val i = wm.connectionInfo.ipAddress
    val ip = if (i == 0 || !KlipperInstance.isWebServerRunning()) "127.0.0.1" else Formatter.formatIpAddress(i)
    val t = System.currentTimeMillis()
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://$ip:${WebService.getPort()}/?t=$t"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    context.startActivity(intent)
}

private fun webIpInfo(context: Context): String {
    val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    return context.getString(R.string.IPInfo, Formatter.formatIpAddress(wm.connectionInfo.ipAddress), WebService.getPort())
}

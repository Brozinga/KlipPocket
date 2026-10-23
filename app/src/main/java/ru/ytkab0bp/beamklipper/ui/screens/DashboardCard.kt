package ru.ytkab0bp.beamklipper.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.firmwareweb.FirmwareWebHost
import ru.ytkab0bp.beamklipper.firmwareweb.FirmwareWebServer
import ru.ytkab0bp.beamklipper.ui.components.KlipTextButton
import ru.ytkab0bp.beamklipper.ui.theme.Accent
import ru.ytkab0bp.beamklipper.ui.theme.Ink
import ru.ytkab0bp.beamklipper.ui.theme.InkMuted
import ru.ytkab0bp.beamklipper.ui.theme.KlipShape
import ru.ytkab0bp.beamklipper.ui.theme.Line
import ru.ytkab0bp.beamklipper.ui.theme.PaperAlt
import ru.ytkab0bp.beamklipper.ui.theme.Warn

/** Where the KlipPocket Dashboard (web page, port 4400) is and the PIN it asks for; shown at the end of the home screen. */
@Composable
fun DashboardCard() {
    val context = LocalContext.current
    var pin by remember { mutableStateOf(FirmwareWebHost.pin()) }
    val ip = remember {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        @Suppress("DEPRECATION") val i = wm.connectionInfo.ipAddress
        if (i == 0) "127.0.0.1" else android.text.format.Formatter.formatIpAddress(i)
    }
    Text(
        stringResource(R.string.FirmwareWebSection).uppercase(), style = MaterialTheme.typography.labelMedium, color = InkMuted,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 12.dp)
    )
    Column(
        modifier = Modifier.fillMaxWidth().clip(KlipShape).background(PaperAlt, KlipShape).border(2.dp, Line, KlipShape).padding(14.dp)
    ) {
        Text(stringResource(R.string.FirmwareWebHint), style = MaterialTheme.typography.bodySmall, color = InkMuted)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.FirmwareWebAddress), style = MaterialTheme.typography.labelMedium, color = InkMuted)
        Text("http://$ip:${FirmwareWebServer.PORT}", style = MaterialTheme.typography.titleMedium, color = Accent)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.FirmwareWebPin), style = MaterialTheme.typography.labelMedium, color = InkMuted)
                Text(pin, style = MaterialTheme.typography.headlineSmall, color = Ink)
            }
            KlipTextButton(stringResource(R.string.FirmwareWebNewPin), { pin = FirmwareWebHost.renewPin() })
        }
        if (!FirmwareWebHost.running) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.FirmwareWebOff), style = MaterialTheme.typography.bodySmall, color = Warn)
        }
    }
}


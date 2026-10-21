package net.agolyakov.firelamp.ui.screen.device

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalWifi0Bar
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Wifi1Bar
import androidx.compose.material.icons.filled.Wifi2Bar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.agolyakov.firelamp.R
import net.agolyakov.firelamp.protocol.WifiAuth
import net.agolyakov.firelamp.protocol.WifiNetwork
import net.agolyakov.firelamp.protocol.WifiScan
import net.agolyakov.firelamp.protocol.WifiScanState

/**
 * Networks the lamp hears, strongest first: the lamp scans itself, so only 2.4 GHz networks it
 * can actually join (with their signal where the lamp stands) are listed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiNetworkPicker(
    scan: WifiScan?,
    onRescan: () -> Unit,
    onPick: (WifiNetwork) -> Unit,
    onManual: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scanning = scan?.state == WifiScanState.SCANNING
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 24.dp, end = 12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.wifi_picker_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.wifi_band_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Box(contentAlignment = Alignment.Center) {
                    if (scanning) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(12.dp))
                    } else {
                        IconButton(onClick = onRescan) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.wifi_picker_rescan))
                        }
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            val networks = scan?.networks.orEmpty()
            when {
                networks.isEmpty() && scanning -> Message(stringResource(R.string.wifi_picker_scanning))
                networks.isEmpty() && scan?.state == WifiScanState.ERROR -> Message(stringResource(R.string.wifi_picker_error))
                networks.isEmpty() && scan?.state == WifiScanState.DONE -> Message(stringResource(R.string.wifi_picker_empty))
            }
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(networks, key = { it.ssid }) { network ->
                    NetworkRow(network) { onPick(network) }
                }
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onManual)
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.wifi_picker_manual), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkRow(network: WifiNetwork, onClick: () -> Unit) {
    val usable = network.usable
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = usable, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Icon(
            signalIcon(network.rssi),
            contentDescription = null,
            tint = if (usable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                network.ssid,
                style = MaterialTheme.typography.bodyLarge,
                color = if (usable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
            )
            val detail = when (network.auth) {
                WifiAuth.OPEN -> stringResource(R.string.wifi_auth_open)
                WifiAuth.ENTERPRISE -> stringResource(R.string.wifi_auth_enterprise)
                else -> null
            }
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        if (network.needsPassword) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(end = 8.dp).size(16.dp)
            )
        }
        Text("${network.rssi} dBm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
    )
}

/** Signal bars, roughly as Android shows them. */
private fun signalIcon(rssi: Int): ImageVector = when {
    rssi >= -60 -> Icons.Filled.Wifi
    rssi >= -72 -> Icons.Filled.Wifi2Bar
    rssi >= -85 -> Icons.Filled.Wifi1Bar
    else -> Icons.Filled.SignalWifi0Bar
}

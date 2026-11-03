package net.agolyakov.firelamp.ui.screen.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import net.agolyakov.firelamp.R
import net.agolyakov.firelamp.data.model.ble.FireLampDevice
import net.agolyakov.firelamp.data.repository.AppUpdateRepository
import net.agolyakov.firelamp.navigation.Screen
import net.agolyakov.firelamp.ui.theme.FireLampTheme

@Composable
fun HomeScreen(
    navController: NavHostController,
    homeViewModel: HomeViewModel
) {
    val devices by homeViewModel.devices.observeAsState(emptyList())
    val appUpdateState by homeViewModel.appUpdateState.collectAsState()
    val autoOpen by homeViewModel.autoOpen.collectAsState()
    LaunchedEffect(autoOpen) {
        autoOpen?.let {
            homeViewModel.onAutoOpened()
            openDevice(navController, it)
        }
    }

    var showRenameDialog by remember { mutableStateOf(false) }
    var deviceToRename by remember { mutableStateOf<FireLampDevice?>(null) }

    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        DeviceList(
            deviceList = devices,
            navController = navController,
            modifier = Modifier.weight(1f),
            onRenameClick = { device ->
                deviceToRename = device
                showRenameDialog = true
            }
        )
        // The version, the language and the rest are on the settings screen behind the gear.
        (appUpdateState as? AppUpdateRepository.UpdateCheckState.UpdateAvailable)?.let {
            AppUpdatePlaque(it)
        }
    }

    if (showRenameDialog && deviceToRename != null) {
        RenameDeviceDialog(
            device = deviceToRename!!,
            onDismiss = {
                showRenameDialog = false
                deviceToRename = null
            },
            onRename = { newName ->
                homeViewModel.renameDevice(deviceToRename!!.macAddress, newName)
            }
        )
    }
}

@Composable
fun DeviceList(
    deviceList: List<FireLampDevice>,
    navController: NavHostController,
    modifier: Modifier = Modifier,
    onRenameClick: (FireLampDevice) -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        if (deviceList.isEmpty()) {
            item { SearchingCard() }
        }
        itemsIndexed(deviceList) { _, item ->
            Device(item, navController, onRenameClick)
        }
    }
}

/** No lamp found yet: say that the app is looking, and what makes a lamp findable. */
@Composable
private fun SearchingCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp, 16.dp, 16.dp, 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.home_searching), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.home_searching_hint), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The Device screen takes the lamp from the back stack, not as a route argument. */
fun openDevice(navController: NavHostController, device: FireLampDevice) {
    navController.currentBackStackEntry?.savedStateHandle?.set(key = "device", value = device)
    navController.navigate(Screen.Device.route)
}

@Composable
fun Device(
    device: FireLampDevice,
    navController: NavHostController,
    onRenameClick: (FireLampDevice) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .padding(16.dp, 16.dp, 16.dp, 0.dp)
            .clickable { openDevice(navController, device) }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(vertical = 16.dp, horizontal = 24.dp)
        ) {
            LampIcon()

            Spacer(Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = device.getDisplayName(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = device.macAddress,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                // Not seen on the air: the phone holds a link to it (the system or another app)
                if (!device.inRange) {
                    Text(
                        text = stringResource(R.string.home_connected_not_advertising),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            IconButton(
                onClick = { onRenameClick(device) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.rename),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

/** The app icon (the lamp), as on the launcher. */
@Composable
private fun LampIcon() {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        Image(painterResource(R.drawable.ic_launcher_background), contentDescription = null, modifier = Modifier.fillMaxSize())
        // Launchers show the middle 72 of the adaptive icon's 108 dp: zoom in the same way
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .scale(1.5f)
        )
    }
}

/** A new version of the app: a plaque at the bottom of the list, a tap opens the release page. */
@Composable
fun AppUpdatePlaque(
    update: AppUpdateRepository.UpdateCheckState.UpdateAvailable,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp, 8.dp, 16.dp, 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { uriHandler.openUri(update.releaseUrl) }
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.app_update_available, update.version),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun RenameDeviceDialog(
    device: FireLampDevice,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var text by remember { mutableStateOf(device.friendlyName ?: device.deviceName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_device)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.dialog_device_name)) },
                supportingText = { Text(stringResource(R.string.rename_device_hint)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onRename(text)
                    } else {
                        onRename("")
                    }
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

private val previewDeviceList = listOf(
    FireLampDevice(
        deviceName = "FireLamp-1A2B",
        macAddress = "11:22:33:44:55:66"
    ),
    FireLampDevice(
        deviceName = "FireLamp-1A2B",
        macAddress = "11:22:33:44:55:77",
        inRange = false,
        friendlyName = "Слайдер в студии"
    )
)

private val previewAppUpdate = AppUpdateRepository.UpdateCheckState.UpdateAvailable(
    version = "v0.12.0",
    releaseUrl = "https://github.com/golyakoff/FireLamp_Android/releases/tag/v0.12.0"
)

@Composable
private fun HomeContentPreview(darkTheme: Boolean, appUpdate: AppUpdateRepository.UpdateCheckState.UpdateAvailable?) {
    FireLampTheme(darkTheme) {
        Column(Modifier.fillMaxSize()) {
            DeviceList(
                deviceList = previewDeviceList,
                navController = rememberNavController(),
                modifier = Modifier.weight(1f),
                onRenameClick = {}
            )
            appUpdate?.let { AppUpdatePlaque(it) }
        }
    }
}

@Composable
@Preview(name = "Light Schema", heightDp = 800, showBackground = false)
fun DeviceListPreview_1() {
    HomeContentPreview(darkTheme = false, appUpdate = null)
}

@Composable
@Preview(name = "Dark Schema, update available", heightDp = 800, showBackground = true)
fun DeviceListPreview_2() {
    HomeContentPreview(darkTheme = true, appUpdate = previewAppUpdate)
}

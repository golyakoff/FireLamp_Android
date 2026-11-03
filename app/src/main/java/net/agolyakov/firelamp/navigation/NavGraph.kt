package net.agolyakov.firelamp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import net.agolyakov.firelamp.data.model.ble.FireLampDevice
import net.agolyakov.firelamp.ui.screen.demo.DemoDeviceScreen
import net.agolyakov.firelamp.ui.screen.device.DeviceScreen
import net.agolyakov.firelamp.ui.screen.firmware.FirmwareScreen
import net.agolyakov.firelamp.ui.screen.firmware.FirmwareUpdateMode
import net.agolyakov.firelamp.ui.screen.home.HomeScreen
import net.agolyakov.firelamp.ui.screen.home.HomeViewModel
import net.agolyakov.firelamp.ui.screen.settings.SettingsScreen

@Composable
fun SetupNavGraph(
    navController: NavHostController,
    onDeviceTitle: (String?) -> Unit = {},
) {
    NavHost(navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(route = Screen.Home.route) {
            val homeViewModel: HomeViewModel = hiltViewModel()
            LaunchedEffect(Unit) {
                homeViewModel.bluetoothService.setShouldMaintainConnection(false)
            }
            // Scan while the list is in the foreground: it restarts after the permission dialog,
            // after Bluetooth was turned on, after returning from a lamp; it does not run (and
            // disturb the link) while a lamp is open
            LifecycleResumeEffect(Unit) {
                homeViewModel.startScan()
                onPauseOrDispose { homeViewModel.stopScan() }
            }
            HomeScreen(navController, homeViewModel)
        }

        composable(route = Screen.Device.route) {
            val device: FireLampDevice? =
                navController.previousBackStackEntry?.savedStateHandle?.get<FireLampDevice>("device")
            val title = device?.getDisplayName()
            DisposableEffect(title) {
                onDeviceTitle(title)
                onDispose { onDeviceTitle(null) }
            }
            DeviceScreen(navController, device)
        }

        composable(route = Screen.Settings.route) {
            SettingsScreen(
                onOpenDemo = { version -> navController.navigate(Screen.Demo.path(version)) },
            )
        }

        composable(route = Screen.Firmware.route) { entry ->
            val mode = FirmwareUpdateMode.valueOf(
                entry.arguments?.getString(Screen.Firmware.ARG_MODE)?.uppercase() ?: "PHONE"
            )
            FirmwareScreen(mode = mode, onBack = { navController.popBackStack() })
        }

        composable(route = Screen.Demo.route) { entry ->
            DemoDeviceScreen()
        }
    }
}

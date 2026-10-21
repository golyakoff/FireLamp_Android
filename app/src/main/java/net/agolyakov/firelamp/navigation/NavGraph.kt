package net.agolyakov.firelamp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import kotlinx.coroutines.delay
import net.agolyakov.firelamp.data.model.ble.FireLampDevice
import net.agolyakov.firelamp.ui.screen.device.DeviceScreen
import net.agolyakov.firelamp.ui.screen.home.HomeScreen
import net.agolyakov.firelamp.ui.screen.home.HomeViewModel

@Composable
fun SetupNavGraph(
    navController: NavHostController
) {
    NavHost(navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(route = Screen.Home.route) {
            val homeViewModel: HomeViewModel = hiltViewModel()
            LaunchedEffect(Unit) {
                delay(50)
                homeViewModel.bluetoothService.setShouldMaintainConnection(false)
                delay(50)
                homeViewModel.startScan()
            }
            HomeScreen(navController, homeViewModel)
        }

        composable(route = Screen.Device.route) {
            val device: FireLampDevice? =
                navController.previousBackStackEntry?.savedStateHandle?.get<FireLampDevice>("device")

            DeviceScreen(navController, device)
        }
    }
}

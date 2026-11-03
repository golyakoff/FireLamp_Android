package net.agolyakov.firelamp.ui.screen.firmware

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.agolyakov.firelamp.service.bluetooth.BluetoothService
import net.agolyakov.firelamp.service.firmware.FirmwareUpdater
import javax.inject.Inject

@HiltViewModel
class FirmwareViewModel @Inject constructor(
    private val updater: FirmwareUpdater,
    private val bluetoothService: BluetoothService,
) : ViewModel() {
    val state = updater.state
    val deviceInfo = bluetoothService.deviceInfo
    val updateInfo = bluetoothService.updateInfo

    init {
        // This screen sits on top of the device screen, whose auto-reconnect is paused while it
        // is not resumed: connect while the lamp reboots into the new firmware.
        viewModelScope.launch {
            updater.state.collectLatest { state ->
                if (state is FirmwareUpdater.State.Rebooting) {
                    while (currentCoroutineContext().isActive) {
                        bluetoothService.tryReconnect()
                        delay(1000)
                    }
                }
            }
        }
    }

    fun check() = updater.checkForUpdate()

    fun installFromPhone(available: FirmwareUpdater.State.Available) =
        updater.installFromPhone(available.version, available.build)

    fun installByLamp(version: String?) = updater.installByLamp(version)

    fun cancel() = updater.cancel()

    fun reset() = updater.reset()
}

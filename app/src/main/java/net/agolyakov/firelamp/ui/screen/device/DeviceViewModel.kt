package net.agolyakov.firelamp.ui.screen.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import net.agolyakov.firelamp.data.model.ble.ConnectionState
import net.agolyakov.firelamp.data.model.ble.FireLampDevice
import net.agolyakov.firelamp.protocol.Command
import net.agolyakov.firelamp.protocol.EffectParamChange
import net.agolyakov.firelamp.service.bluetooth.BluetoothService
import net.agolyakov.firelamp.ui.viewmodel.Throttle
import javax.inject.Inject

@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val bluetoothService: BluetoothService
) : ViewModel() {

    val connectionState = bluetoothService.connectionState
    val deviceInfo = bluetoothService.deviceInfo
    val rebootRequired = bluetoothService.rebootRequired
    val errors = bluetoothService.errors
    val lampState = bluetoothService.lampState
    val effects = bluetoothService.effects

    // ---------- Lamp tab ----------

    private val brightnessWrites = Throttle<Unit, Int>(viewModelScope) { _, value ->
        bluetoothService.setBrightness(value)
    }

    private val paramWrites = Throttle<Pair<String, String>, Int>(viewModelScope) { (effect, param), value ->
        bluetoothService.setEffectParam(EffectParamChange(effect, param, value))
    }

    fun setPower(on: Boolean) {
        viewModelScope.launch { bluetoothService.setPower(on) }
    }

    fun setBrightness(value: Int) = brightnessWrites.submit(Unit, value)

    fun selectEffect(code: String) {
        viewModelScope.launch { bluetoothService.setEffect(code) }
    }

    /** Next/previous respecting the demo mode (it keeps running). */
    fun nextEffect(forward: Boolean) {
        viewModelScope.launch { bluetoothService.sendCommand(if (forward) Command.NEXT_EFFECT else Command.PREVIOUS_EFFECT) }
    }

    fun setDemo(on: Boolean) {
        viewModelScope.launch { bluetoothService.setDemo(on) }
    }

    fun setEffectParam(effectCode: String, paramCode: String, value: Int) =
        paramWrites.submit(effectCode to paramCode, value)

    // ---------- Connection ----------

    /**
     * Automatic reconnection after the link was lost while the screen is in the foreground:
     * the lamp reboots to apply settings, goes out of range, is switched off. A few attempts
     * with growing pauses, then the screen offers a button instead of retrying forever.
     */
    data class Reconnect(val attempt: Int = 0, val exhausted: Boolean = false)

    private val _reconnect = MutableStateFlow(Reconnect())
    val reconnect: StateFlow<Reconnect> = _reconnect.asStateFlow()

    private var device: FireLampDevice? = null
    private val foreground = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            combine(connectionState, foreground) { state, fg -> state to fg }.collectLatest { (state, fg) ->
                when {
                    state == ConnectionState.Ready -> _reconnect.value = Reconnect()
                    // Not in the background: MainActivity disconnects on pause, the lamp serves
                    // one phone at a time. Not after a pairing rejection: that needs the user at
                    // the lamp first.
                    !fg || device == null -> {}
                    state is ConnectionState.Disconnected || state is ConnectionState.Error -> {
                        val attempt = _reconnect.value.attempt
                        if (attempt < RECONNECT_ATTEMPTS) {
                            delay(RECONNECT_DELAY_MS shl attempt)
                            _reconnect.value = Reconnect(attempt + 1)
                            device?.let { bluetoothService.connect(it) }
                        } else {
                            _reconnect.value = _reconnect.value.copy(exhausted = true)
                        }
                    }
                }
            }
        }
    }

    /** The screen is resumed (true) or paused (false). */
    fun setForeground(resumed: Boolean) {
        foreground.value = resumed
    }

    fun connectToDevice(device: FireLampDevice?) {
        device ?: return
        this.device = device
        bluetoothService.setShouldMaintainConnection(true)
        bluetoothService.connect(device)
    }

    /** The user asked for it: a fresh round of automatic attempts follows if this one fails. */
    fun reconnectNow() {
        _reconnect.value = Reconnect()
        connectToDevice(device)
    }

    /** The lamp forgot this phone: drop the phone's half of the bond and pair anew. */
    fun forgetBondAndReconnect() {
        val device = device ?: return
        bluetoothService.forgetBond(device.macAddress)
        viewModelScope.launch {
            // Removing a bond is asynchronous in Android
            delay(BOND_REMOVAL_DELAY_MS)
            _reconnect.value = Reconnect()
            bluetoothService.connect(device)
        }
    }

    fun reboot() {
        viewModelScope.launch { bluetoothService.sendCommand(Command.REBOOT) }
    }

    companion object {
        private const val BOND_REMOVAL_DELAY_MS = 1000L
        const val RECONNECT_ATTEMPTS = 3
        /** Doubled for every next attempt: 2, 4, 8 s. */
        private const val RECONNECT_DELAY_MS = 2000L
    }
}

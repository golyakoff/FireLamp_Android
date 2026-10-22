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
import net.agolyakov.firelamp.protocol.Controls
import net.agolyakov.firelamp.protocol.DemoSettings
import net.agolyakov.firelamp.protocol.EffectParamChange
import net.agolyakov.firelamp.protocol.LedSettings
import net.agolyakov.firelamp.protocol.Schedule
import net.agolyakov.firelamp.protocol.TimeZoneMapper
import net.agolyakov.firelamp.protocol.WifiSettingsUpdate
import net.agolyakov.firelamp.service.bluetooth.BluetoothService
import net.agolyakov.firelamp.service.firmware.FirmwareUpdater
import net.agolyakov.firelamp.ui.viewmodel.Throttle
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val bluetoothService: BluetoothService,
    private val firmwareUpdater: FirmwareUpdater,
) : ViewModel(), SettingsTabActions, FirmwareActions {

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

    // ---------- Schedule tab ----------

    val schedule = bluetoothService.schedule
    val lampTime = bluetoothService.lampTime

    fun saveSchedule(schedule: Schedule) {
        viewModelScope.launch { bluetoothService.setSchedule(schedule) }
    }

    // ---------- Settings tab ----------

    val demoSettings = bluetoothService.demoSettings
    val wifiSettings = bluetoothService.wifiSettings
    val wifiStatus = bluetoothService.wifiStatus
    val deviceName = bluetoothService.deviceName
    val timeZone = bluetoothService.timeZone

    /** The POSIX string the phone's zone maps to, to show a mismatch with the lamp's. */
    val phoneTimeZone: String get() = TimeZoneMapper.toPosix(ZoneId.systemDefault())

    private val fpsWrites = Throttle<Unit, Int>(viewModelScope) { _, value -> bluetoothService.setFps(value) }

    /** The demo flags in the cached effect list may be stale: the demo settings sheet re-reads them. */
    fun refreshEffects() {
        viewModelScope.launch { bluetoothService.refreshEffects() }
    }

    fun saveDemoSettings(settings: DemoSettings) {
        viewModelScope.launch { bluetoothService.setDemoSettings(settings) }
    }

    fun setDemoIncluded(effectCode: String, include: Boolean) {
        viewModelScope.launch { bluetoothService.setDemoIncluded(effectCode, include) }
    }

    val wifiScan = bluetoothService.wifiScan
    val wifiScanSupported = bluetoothService.wifiScanSupported

    override fun startWifiScan() {
        viewModelScope.launch { bluetoothService.startWifiScan() }
    }

    override fun saveWifiSettings(update: WifiSettingsUpdate) {
        viewModelScope.launch { bluetoothService.setWifiSettings(update) }
    }

    override fun saveDeviceName(name: String) {
        viewModelScope.launch { bluetoothService.setDeviceName(name) }
    }

    override fun syncTimeFromPhone() {
        viewModelScope.launch {
            bluetoothService.syncTime()
            bluetoothService.setTimeZone(phoneTimeZone)
        }
    }

    fun setFps(fps: Int) = fpsWrites.submit(Unit, fps)

    fun resetEffects() {
        viewModelScope.launch { bluetoothService.sendCommand(Command.RESET_EFFECTS) }
    }

    fun factoryReset() {
        viewModelScope.launch { bluetoothService.factoryReset() }
    }

    // ---------- Firmware update (System tab) ----------

    val firmwareState = firmwareUpdater.state
    val updateInfo = bluetoothService.updateInfo

    override fun check() = firmwareUpdater.checkForUpdate()

    override fun installFromPhone(available: FirmwareUpdater.State.Available) =
        firmwareUpdater.installFromPhone(available.version, available.build)

    override fun askLampToCheck() = firmwareUpdater.askLampToCheck()

    override fun installByLamp(version: String?) = firmwareUpdater.installByLamp(version)

    override fun cancel() = firmwareUpdater.cancel()

    // ---------- System tab ----------

    val ledSettings = bluetoothService.ledSettings
    val hardwareSettings = bluetoothService.hardwareSettings

    fun saveLedSettings(settings: LedSettings) {
        viewModelScope.launch { bluetoothService.setLedSettings(settings) }
    }

    fun saveHardwareSettings(controls: Controls, buttonsActiveHigh: Boolean) {
        viewModelScope.launch { bluetoothService.setHardwareSettings(controls, buttonsActiveHigh) }
    }

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
        firmwareUpdater.reset()
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
                        // A lamp rebooting into new firmware may take longer: keep trying
                        val maxAttempts = if (firmwareUpdater.state.value is FirmwareUpdater.State.Rebooting) {
                            REBOOT_RECONNECT_ATTEMPTS
                        } else {
                            RECONNECT_ATTEMPTS
                        }
                        if (attempt < maxAttempts) {
                            delay(RECONNECT_DELAY_MS shl attempt.coerceAtMost(2))
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
        private const val REBOOT_RECONNECT_ATTEMPTS = 20
        /** Doubled for every next attempt: 2, 4, 8, 8... s. */
        private const val RECONNECT_DELAY_MS = 2000L
    }
}

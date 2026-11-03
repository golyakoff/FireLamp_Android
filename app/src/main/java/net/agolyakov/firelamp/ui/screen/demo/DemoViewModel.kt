package net.agolyakov.firelamp.ui.screen.demo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.agolyakov.firelamp.data.demo.FirmwareModelsRepository
import net.agolyakov.firelamp.protocol.BondedPhone
import net.agolyakov.firelamp.protocol.CalibrationState
import net.agolyakov.firelamp.protocol.Controls
import net.agolyakov.firelamp.protocol.DemoSettings
import net.agolyakov.firelamp.protocol.HardwareSettings
import net.agolyakov.firelamp.protocol.LampTime
import net.agolyakov.firelamp.protocol.LedSettings
import net.agolyakov.firelamp.protocol.PowerStatus
import net.agolyakov.firelamp.protocol.Schedule
import net.agolyakov.firelamp.protocol.TimeSource
import net.agolyakov.firelamp.protocol.TimeZoneMapper
import net.agolyakov.firelamp.protocol.WifiScan
import net.agolyakov.firelamp.protocol.WifiSettings
import net.agolyakov.firelamp.protocol.WifiSettingsUpdate
import net.agolyakov.firelamp.ui.screen.device.PowerActions
import net.agolyakov.firelamp.ui.screen.device.SettingsTabActions
import net.agolyakov.firelamp.ui.screen.firmware.FirmwareUpdateMode
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/** The simulated update of the developer demo: 10 s of progress, then the standard success. */
sealed interface DemoUpdate {
    data class Running(val mode: FirmwareUpdateMode, val progress: Float) : DemoUpdate
    data class Done(val mode: FirmwareUpdateMode) : DemoUpdate
}

@HiltViewModel
class DemoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: FirmwareModelsRepository,
) : ViewModel(), SettingsTabActions, PowerActions {

    private val firmware = repository.load(savedStateHandle.get<String>("version") ?: "0.11.1")

    private val _state = MutableStateFlow(firmware.demoState())
    val state: StateFlow<DemoLampState> = _state.asStateFlow()

    val phoneTimeZone: String get() = TimeZoneMapper.toPosix(ZoneId.systemDefault())

    private val _update = MutableStateFlow<DemoUpdate?>(null)
    val update: StateFlow<DemoUpdate?> = _update.asStateFlow()

    private var updateJob: Job? = null

    // ---------- Lamp tab ----------

    fun setPower(on: Boolean) = updateLamp { copy(on = on) }

    fun setBrightness(brightness: Int) = updateLamp { copy(brightness = brightness.coerceIn(1, 255)) }

    fun setFps(fps: Int) = updateLamp { copy(fps = fps.coerceIn(1, 255)) }

    fun setDemo(on: Boolean) = updateLamp { copy(demo = on) }

    fun selectEffect(code: String) = updateLamp { copy(effectCode = code) }

    fun nextEffect(forward: Boolean) {
        val current = _state.value
        val index = current.effects.indexOfFirst { it.code == current.lampState.effectCode }
        val next = if (index < 0) 0 else (index + if (forward) 1 else -1 + current.effects.size) % current.effects.size
        selectEffect(current.effects[next].code)
    }

    fun setEffectParam(effectCode: String, paramCode: String, value: Int) {
        _state.update { s ->
            s.copy(effects = s.effects.map { e ->
                if (e.code != effectCode) e else e.copy(params = e.params.map { p ->
                    if (p.code == paramCode) p.copy(value = value.coerceIn(p.min, p.max)) else p
                })
            })
        }
    }

    fun saveDemoSettings(settings: DemoSettings) = _state.update { it.copy(demoSettings = settings) }

    fun setDemoIncluded(changes: Map<String, Boolean>) {
        _state.update { s ->
            s.copy(effects = s.effects.map { e -> changes[e.code]?.let { e.copy(inDemo = it) } ?: e })
        }
    }

    // ---------- Schedule tab ----------

    fun saveSchedule(schedule: Schedule) = _state.update { it.copy(schedule = schedule) }

    // ---------- Settings tab ----------

    override fun saveWifiSettings(update: WifiSettingsUpdate) {
        _state.update { s ->
            s.copy(wifi = WifiSettings(
                mode = update.mode,
                periodHours = update.periodHours,
                ssid = update.ssid,
                passwordStored = update.password?.isNotEmpty() ?: s.wifi.passwordStored,
            ))
        }
    }

    override fun startWifiScan() {
        // A finished scan with a few networks, as a lamp near a phone would report
        _state.update { s -> s.copy(wifiScan = s.wifiScan.copy(state = net.agolyakov.firelamp.protocol.WifiScanState.DONE)) }
    }

    override fun saveDeviceName(name: String) = _state.update { it.copy(deviceName = name) }

    override fun syncTimeFromPhone() {
        _state.update {
            it.copy(
                lampTime = LampTime(Instant.now().epochSecond, valid = true, source = TimeSource.BLE),
                timeZone = phoneTimeZone,
            )
        }
    }

    // ---------- System tab ----------

    fun saveLedSettings(led: LedSettings) = _state.update { it.copy(led = led) }

    fun saveHardwareSettings(controls: Controls, buttonsActiveHigh: Boolean, mainButtonGpio: Int?) {
        _state.update { s ->
            s.copy(hardware = s.hardware.copy(
                controls = controls,
                buttonsActiveHigh = buttonsActiveHigh,
                mainButtonGpio = mainButtonGpio ?: s.hardware.mainButtonGpio,
            ))
        }
    }

    fun resetEffects() {
        _state.update { s ->
            s.copy(effects = s.effects.map { e -> e.copy(params = e.params.map { it.copy(value = it.default) }) })
        }
    }

    fun forgetBond(phone: BondedPhone) {
        _state.update { s ->
            s.copy(bonds = s.bonds.copy(phones = s.bonds.phones.filterNot { it.address == phone.address }))
        }
    }

    fun factoryReset() {
        _state.value = firmware.demoState()
    }

    fun reboot() {
        // A reboot of a demo lamp changes nothing
    }

    override fun restoreFullPower() = updatePower { copy(percent = 100, brownout = false) }

    override fun startCalibration() = updatePower {
        copy(calibration = CalibrationState.RUNNING, step = 1)
    }

    override fun calibrationFails() {
        val limit = _state.value.led.currentLimitMa
        updatePower { copy(calibration = CalibrationState.DONE, step = 5, limitMa = limit) }
    }

    override fun cancelCalibration() = updatePower { copy(calibration = CalibrationState.CANCELLED) }

    // ---------- Demo firmware update ----------

    fun startDemoUpdate(mode: FirmwareUpdateMode) {
        updateJob?.cancel()
        _update.value = DemoUpdate.Running(mode, 0f)
        updateJob = viewModelScope.launch {
            for (step in 1..100) {
                delay(100)
                _update.value = DemoUpdate.Running(mode, step.toFloat())
            }
            _update.value = DemoUpdate.Done(mode)
        }
    }

    fun finishDemoUpdate() {
        updateJob?.cancel()
        updateJob = null
        _update.value = null
    }

    // ---------- helpers ----------

    private fun updateLamp(transform: net.agolyakov.firelamp.protocol.LampState.() -> net.agolyakov.firelamp.protocol.LampState) {
        _state.update { it.copy(lampState = it.lampState.transform()) }
    }

    private fun updatePower(transform: PowerStatus.() -> PowerStatus) {
        _state.update { it.copy(power = it.power.transform()) }
    }
}

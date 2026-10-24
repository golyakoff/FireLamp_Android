package net.agolyakov.firelamp.service.bluetooth

import android.bluetooth.BluetoothDevice
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.agolyakov.firelamp.data.local.EffectCatalogRepository
import net.agolyakov.firelamp.data.local.KnownLamps
import net.agolyakov.firelamp.data.model.ble.ConnectionState
import net.agolyakov.firelamp.data.model.ble.FireLampDevice
import net.agolyakov.firelamp.protocol.CalibrationState
import net.agolyakov.firelamp.protocol.Command
import net.agolyakov.firelamp.protocol.Controls
import net.agolyakov.firelamp.protocol.DemoSettings
import net.agolyakov.firelamp.protocol.DeviceInfo
import net.agolyakov.firelamp.protocol.EffectInfo
import net.agolyakov.firelamp.protocol.EffectParamChange
import net.agolyakov.firelamp.protocol.EffectSelector
import net.agolyakov.firelamp.protocol.FireLampCodec
import net.agolyakov.firelamp.protocol.FireLampGatt
import net.agolyakov.firelamp.protocol.HardwareSettings
import net.agolyakov.firelamp.protocol.PowerCommand
import net.agolyakov.firelamp.protocol.PowerStatus
import net.agolyakov.firelamp.protocol.LampState
import net.agolyakov.firelamp.protocol.LampStateUpdate
import net.agolyakov.firelamp.protocol.LampTime
import net.agolyakov.firelamp.protocol.LedSettings
import net.agolyakov.firelamp.protocol.OtaStatus
import net.agolyakov.firelamp.protocol.Schedule
import net.agolyakov.firelamp.protocol.TimeZoneMapper
import net.agolyakov.firelamp.protocol.UnsupportedProtocolException
import net.agolyakov.firelamp.protocol.UpdateInfo
import net.agolyakov.firelamp.protocol.WifiScan
import net.agolyakov.firelamp.protocol.WifiScanState
import net.agolyakov.firelamp.protocol.WifiSettings
import net.agolyakov.firelamp.protocol.WifiSettingsUpdate
import net.agolyakov.firelamp.protocol.WifiStatus
import no.nordicsemi.android.ble.exception.DeviceDisconnectedException
import no.nordicsemi.android.ble.exception.RequestFailedException
import no.nordicsemi.android.ble.ktx.suspend
import no.nordicsemi.android.ble.observer.BondingObserver
import no.nordicsemi.android.ble.observer.ConnectionObserver
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app-facing façade of the lamp: connection, pairing, the startup sequence of
 * `APP_INTEGRATION.md` and one [StateFlow] per piece of lamp state. ViewModels talk only to
 * this class; bytes are converted by [FireLampCodec].
 *
 * Writes are suspending and return whether the lamp accepted them; a rejected write is also
 * reported on [errors]. After a write the value is read back (or arrives as a notification),
 * so the flows always show what the lamp really has.
 */
@Singleton
class BluetoothService @Inject constructor(
    private val bluetoothAdapterProvider: BluetoothAdapterProvider,
    private val effectCatalog: EffectCatalogRepository,
    private val knownLamps: KnownLamps,
) {
    private val tag = "BluetoothService"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val bleManager = FireLampBleManager(bluetoothAdapterProvider.getContext())

    // ========================== State ==========================

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _currentDevice = MutableStateFlow<FireLampDevice?>(null)
    val currentDevice: StateFlow<FireLampDevice?> = _currentDevice.asStateFlow()

    private val _deviceInfo = MutableStateFlow<DeviceInfo?>(null)
    val deviceInfo: StateFlow<DeviceInfo?> = _deviceInfo.asStateFlow()

    private val _lampState = MutableStateFlow<LampState?>(null)
    val lampState: StateFlow<LampState?> = _lampState.asStateFlow()

    /** All effects in the lamp's list order, with current parameter values and demo flags. */
    private val _effects = MutableStateFlow<List<EffectInfo>>(emptyList())
    val effects: StateFlow<List<EffectInfo>> = _effects.asStateFlow()

    private val _lampTime = MutableStateFlow<LampTime?>(null)
    val lampTime: StateFlow<LampTime?> = _lampTime.asStateFlow()

    private val _timeZone = MutableStateFlow<String?>(null)
    val timeZone: StateFlow<String?> = _timeZone.asStateFlow()

    private val _schedule = MutableStateFlow<Schedule?>(null)
    val schedule: StateFlow<Schedule?> = _schedule.asStateFlow()

    private val _ledSettings = MutableStateFlow<LedSettings?>(null)
    val ledSettings: StateFlow<LedSettings?> = _ledSettings.asStateFlow()

    private val _hardwareSettings = MutableStateFlow<HardwareSettings?>(null)
    val hardwareSettings: StateFlow<HardwareSettings?> = _hardwareSettings.asStateFlow()

    private val _wifiSettings = MutableStateFlow<WifiSettings?>(null)
    val wifiSettings: StateFlow<WifiSettings?> = _wifiSettings.asStateFlow()

    private val _wifiStatus = MutableStateFlow<WifiStatus?>(null)
    val wifiStatus: StateFlow<WifiStatus?> = _wifiStatus.asStateFlow()

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    private val _demoSettings = MutableStateFlow<DemoSettings?>(null)
    val demoSettings: StateFlow<DemoSettings?> = _demoSettings.asStateFlow()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    /** Null when the firmware cannot scan for WiFi networks (no WiFi networks characteristic). */
    private val _wifiScan = MutableStateFlow<WifiScan?>(null)
    val wifiScan: StateFlow<WifiScan?> = _wifiScan.asStateFlow()

    private val _wifiScanSupported = MutableStateFlow(false)
    val wifiScanSupported: StateFlow<Boolean> = _wifiScanSupported.asStateFlow()

    private val _otaStatus = MutableStateFlow<OtaStatus?>(null)
    val otaStatus: StateFlow<OtaStatus?> = _otaStatus.asStateFlow()

    /** Null when the firmware has no Power characteristic (older than 0.9.0). */
    private val _powerStatus = MutableStateFlow<PowerStatus?>(null)
    val powerStatus: StateFlow<PowerStatus?> = _powerStatus.asStateFlow()

    /**
     * Settings stored by the lamp that take effect only after a reboot (LED, hardware, name)
     * were changed in this session.
     */
    private val _rebootRequired = MutableStateFlow(false)
    val rebootRequired: StateFlow<Boolean> = _rebootRequired.asStateFlow()

    /** A write or read the lamp rejected; the UI shows it briefly. */
    data class OperationError(val characteristic: UUID, val message: String)

    private val _errors = MutableSharedFlow<OperationError>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val errors: SharedFlow<OperationError> = _errors.asSharedFlow()

    private fun resetDeviceState() {
        _deviceInfo.value = null
        _lampState.value = null
        _effects.value = emptyList()
        _lampTime.value = null
        _timeZone.value = null
        _schedule.value = null
        _ledSettings.value = null
        _hardwareSettings.value = null
        _wifiSettings.value = null
        _wifiStatus.value = null
        _deviceName.value = null
        _demoSettings.value = null
        _updateInfo.value = null
        _otaStatus.value = null
        _wifiScan.value = null
        _wifiScanSupported.value = false
        _powerStatus.value = null
        _rebootRequired.value = false
    }

    // ========================== Connection ==========================

    private var lastDevice: FireLampDevice? = null
    private var shouldMaintainConnection = false
    private var isOtaUpdateMode = false
    private var connectJob: Job? = null

    /** The phone had no bond with this lamp when the current connection attempt started. */
    private var bondedInThisAttempt = false

    private val connectionObserver = object : ConnectionObserver {
        override fun onDeviceConnecting(device: BluetoothDevice) {
            _connectionState.value = ConnectionState.Connecting
        }

        override fun onDeviceConnected(device: BluetoothDevice) {}

        override fun onDeviceReady(device: BluetoothDevice) {}

        override fun onDeviceDisconnecting(device: BluetoothDevice) {
            _connectionState.value = ConnectionState.Disconnecting
        }

        override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
            Log.w(tag, "Failed to connect, reason=$reason")
            onConnectionLost(device, reason)
        }

        override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
            Log.i(tag, "Disconnected, reason=$reason")
            onConnectionLost(device, reason)
        }
    }

    private val bondingObserver = object : BondingObserver {
        override fun onBondingRequired(device: BluetoothDevice) {
            bondedInThisAttempt = true
            _connectionState.value = ConnectionState.Bonding
        }

        override fun onBonded(device: BluetoothDevice) {
            Log.i(tag, "Bonded")
            _connectionState.value = ConnectionState.Loading
        }

        override fun onBondingFailed(device: BluetoothDevice) {
            Log.w(tag, "Bonding failed")
            _connectionState.value = ConnectionState.PairingRejected(staleBondRemoved = false)
        }
    }

    init {
        bleManager.connectionObserver = connectionObserver
        bleManager.setBondingObserver(bondingObserver)
        scope.launch {
            bleManager.notifications.collect { onNotification(it.uuid, it.value) }
        }
    }

    private fun onConnectionLost(device: BluetoothDevice, reason: Int) {
        val state = _connectionState.value
        connectJob?.cancel()
        when {
            state is ConnectionState.PairingRejected || state is ConnectionState.UnsupportedProtocol -> {
                // keep showing why
            }
            reason == ConnectionObserver.REASON_NOT_SUPPORTED ->
                _connectionState.value = ConnectionState.Error("Not a FireLamp (service not found)")
            state.isInProgress && bondedInThisAttempt -> {
                // A fresh bond and the lamp hung up before we were in: it was outside the pairing
                // window and the lamp dropped the keys. The phone's half of the bond is useless.
                val removed = removeBond(device)
                _connectionState.value = ConnectionState.PairingRejected(staleBondRemoved = removed)
            }
            state.isInProgress && reason != ConnectionObserver.REASON_LINK_LOSS &&
                reason != ConnectionObserver.REASON_TIMEOUT && device.isBonded() -> {
                // Bonded, but the lamp does not let us in: it forgot this phone (factory reset,
                // a 5th phone). Removing the bond is left to the user, it may also be a fluke.
                _connectionState.value = ConnectionState.PairingRejected(staleBondRemoved = false)
            }
            else -> _connectionState.value = ConnectionState.Disconnected
        }
        _currentDevice.value = null
    }

    fun connect(device: FireLampDevice) {
        val state = _connectionState.value
        if (state.isInProgress || state == ConnectionState.Ready) {
            Log.w(tag, "Already connecting/connected, ignoring")
            return
        }
        lastDevice = device
        resetDeviceState()
        // Revoked in the system settings: every Bluetooth call would throw
        if (!BlePermissions.granted(bluetoothAdapterProvider.getContext())) {
            _connectionState.value = ConnectionState.NoPermission
            return
        }
        val btDevice = bluetoothAdapterProvider.getAdapter().getRemoteDevice(device.macAddress)
        bondedInThisAttempt = !btDevice.isBonded()
        _connectionState.value = ConnectionState.Connecting
        connectJob = scope.launch {
            try {
                // Completes when initialize() has run: MTU, bond, notifications
                bleManager.connect(btDevice)
                    .retry(2, 200)
                    .useAutoConnect(false)
                    .suspend()
                _currentDevice.value = device
                _connectionState.value = ConnectionState.Loading
                loadLamp(device)
                knownLamps.add(device.macAddress)
                _connectionState.value = ConnectionState.Ready
                loadSettings()
            } catch (e: UnsupportedProtocolException) {
                Log.e(tag, "Unsupported protocol ${e.version}")
                _connectionState.value = ConnectionState.UnsupportedProtocol(e.version)
                bleManager.disconnectCleanly()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: DeviceDisconnectedException) {
                // onConnectionLost has already decided what this means
                Log.w(tag, "Disconnected while connecting")
            } catch (e: Exception) {
                Log.e(tag, "Connection failed", e)
                if (_connectionState.value.isInProgress) {
                    _connectionState.value =
                        if (e is RequestFailedException && e.status in AUTH_FAILURES) {
                            // Bonded, but the lamp does not know the keys: it forgot this phone
                            ConnectionState.PairingRejected(staleBondRemoved = false)
                        } else {
                            ConnectionState.Error(e.message ?: e.javaClass.simpleName)
                        }
                }
                bleManager.disconnectCleanly()
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        val state = _connectionState.value
        if (state == ConnectionState.Disconnected || state == ConnectionState.Disconnecting) return
        bleManager.disconnectCleanly()
        _currentDevice.value = null
        if (state !is ConnectionState.PairingRejected && state !is ConnectionState.UnsupportedProtocol) {
            _connectionState.value = ConnectionState.Disconnecting
        }
    }

    fun setShouldMaintainConnection(shouldMaintain: Boolean) {
        shouldMaintainConnection = shouldMaintain
        if (!shouldMaintain) disconnect()
    }

    fun tryReconnect() {
        val device = lastDevice ?: return
        if (shouldMaintainConnection && _connectionState.value == ConnectionState.Disconnected) connect(device)
    }

    fun enterOtaUpdateMode() {
        isOtaUpdateMode = true
    }

    fun exitOtaUpdateMode() {
        isOtaUpdateMode = false
    }

    fun shouldPreserveConnection(): Boolean = isOtaUpdateMode

    private var backgroundJob: Job? = null

    /**
     * The app left the screen: the link stays for [BACKGROUND_KEEP_MS], so a quick look at
     * another app does not cost a reconnect, then it is dropped (the lamp serves one phone at a
     * time). An OTA transfer keeps it as long as it runs.
     */
    fun onAppBackground() {
        backgroundJob?.cancel()
        backgroundJob = scope.launch {
            delay(BACKGROUND_KEEP_MS)
            if (!shouldPreserveConnection()) disconnect()
        }
    }

    /** Back on the screen before [onAppBackground] dropped the link: it goes on. */
    fun onAppForeground() {
        backgroundJob?.cancel()
        backgroundJob = null
    }

    /** Forgets the phone's half of the bond (the "Forget" button of the Bluetooth settings). */
    fun forgetBond(macAddress: String): Boolean =
        removeBond(bluetoothAdapterProvider.getAdapter().getRemoteDevice(macAddress))

    @Suppress("DiscouragedPrivateApi")
    private fun removeBond(device: BluetoothDevice): Boolean = try {
        device.javaClass.getMethod("removeBond").invoke(device) as Boolean
    } catch (e: Exception) {
        Log.w(tag, "removeBond failed", e)
        false
    }

    // ========================== Startup sequence ==========================

    /** What the lamp tab needs: info, time, state, effects. Throws if the lamp is unusable. */
    private suspend fun loadLamp(device: FireLampDevice) {
        val info = FireLampCodec.decodeDeviceInfo(bleManager.read(FireLampGatt.DEVICE_INFO))
        // A bonded phone keeps the attribute table across firmware updates: characteristics of
        // the new firmware stay invisible, and neither clearing the cache on disconnect nor the
        // lamp's Service Changed help on some phones. Discover again once per new build.
        if (knownLamps.discoveredBuild(device.macAddress) != info.build) {
            Log.i(tag, "New firmware build ${info.build}, discovering the services again")
            bleManager.rediscover()
            knownLamps.setDiscoveredBuild(device.macAddress, info.build)
        }
        _deviceInfo.value = info
        Log.i(tag, "Lamp: $info, MTU ${bleManager.negotiatedMtu}")

        syncTime()
        _lampState.value = FireLampCodec.decodeLampState(bleManager.read(FireLampGatt.LAMP_STATE))

        val cached = effectCatalog.load(device.macAddress, info)
        if (cached != null) {
            _effects.value = cached
            refreshCurrentEffectParams()
        } else {
            _effects.value = readAllEffects()
            effectCatalog.save(device.macAddress, info, _effects.value)
        }
    }

    /** Everything else; failures here do not break the connection. */
    private suspend fun loadSettings() {
        readInto(FireLampGatt.TIME_ZONE) { _timeZone.value = FireLampCodec.decodeTimeZone(it) }
        syncTimeZone()
        readInto(FireLampGatt.TIME) { _lampTime.value = FireLampCodec.decodeTime(it) }
        readInto(FireLampGatt.SCHEDULE) { _schedule.value = FireLampCodec.decodeSchedule(it) }
        readInto(FireLampGatt.DEMO_SETTINGS) { _demoSettings.value = FireLampCodec.decodeDemoSettings(it) }
        readInto(FireLampGatt.LED_SETTINGS) { _ledSettings.value = FireLampCodec.decodeLedSettings(it) }
        readInto(FireLampGatt.HARDWARE_SETTINGS) { _hardwareSettings.value = FireLampCodec.decodeHardwareSettings(it) }
        readInto(FireLampGatt.DEVICE_NAME) { _deviceName.value = FireLampCodec.decodeDeviceName(it) }
        readInto(FireLampGatt.WIFI_SETTINGS) { _wifiSettings.value = FireLampCodec.decodeWifiSettings(it) }
        readInto(FireLampGatt.WIFI_STATUS) { _wifiStatus.value = FireLampCodec.decodeWifiStatus(it) }
        readInto(FireLampGatt.UPDATE_INFO) { _updateInfo.value = FireLampCodec.decodeUpdateInfo(it) }
        if (bleManager.has(FireLampGatt.POWER)) {
            readInto(FireLampGatt.POWER) { _powerStatus.value = FireLampCodec.decodePowerStatus(it) }
        }
        if (bleManager.has(FireLampGatt.WIFI_NETWORKS)) {
            _wifiScanSupported.value = true
            guarded(FireLampGatt.WIFI_NETWORKS) { _wifiScan.value = readAllWifiNetworks() }
        }
    }

    private val effectInfoMutex = Mutex()

    /** Effect info is a cursor + read pair: no one else may move the cursor in between. */
    private suspend fun readEffectInfo(selector: EffectSelector): EffectInfo = effectInfoMutex.withLock {
        bleManager.write(FireLampGatt.EFFECT_INFO, FireLampCodec.encodeEffectSelector(selector))
        FireLampCodec.decodeEffectInfo(bleManager.read(FireLampGatt.EFFECT_INFO))
    }

    private suspend fun readAllEffects(): List<EffectInfo> {
        val first = readEffectInfo(EffectSelector.ByPosition(0))
        return listOf(first) + (1 until first.count).map { readEffectInfo(EffectSelector.ByPosition(it)) }
    }

    /** Re-reads all descriptions: current values and demo flags of every effect. */
    suspend fun refreshEffects(): Boolean = guarded(FireLampGatt.EFFECT_INFO) {
        _effects.value = readAllEffects()
    }

    private suspend fun refreshCurrentEffectParams() {
        val values = FireLampCodec.decodeEffectParamValues(bleManager.read(FireLampGatt.EFFECT_PARAM))
        updateEffect(values.effectCode) { effect ->
            effect.copy(params = effect.params.map { p -> values.values[p.code]?.let { p.copy(value = it) } ?: p })
        }
    }

    private fun updateEffect(code: String, transform: (EffectInfo) -> EffectInfo) {
        _effects.update { list -> list.map { if (it.code == code) transform(it) else it } }
    }

    // ========================== Notifications ==========================

    private suspend fun onNotification(uuid: UUID, value: ByteArray) {
        try {
            when (uuid) {
                FireLampGatt.LAMP_STATE -> {
                    val previous = _lampState.value
                    val state = FireLampCodec.decodeLampState(value)
                    _lampState.value = state
                    // Values of an effect that was not current may have changed while the app
                    // was away; refresh them when it becomes current
                    if (previous != null && previous.effectCode != state.effectCode &&
                        _connectionState.value == ConnectionState.Ready
                    ) {
                        scope.launch { guarded(FireLampGatt.EFFECT_PARAM) { refreshCurrentEffectParams() } }
                    }
                }
                FireLampGatt.EFFECT_PARAM -> applyParamChange(FireLampCodec.decodeEffectParamChange(value))
                FireLampGatt.WIFI_STATUS -> _wifiStatus.value = FireLampCodec.decodeWifiStatus(value)
                FireLampGatt.OTA_CONTROL -> _otaStatus.value = FireLampCodec.decodeOtaStatus(value)
                FireLampGatt.UPDATE_INFO -> _updateInfo.value = FireLampCodec.decodeUpdateInfo(value)
                FireLampGatt.POWER -> {
                    val previous = _powerStatus.value
                    val status = FireLampCodec.decodePowerStatus(value)
                    _powerStatus.value = status
                    // The calibration has just set the current limit
                    if (status.calibration == CalibrationState.DONE && previous?.calibration != CalibrationState.DONE) {
                        scope.launch { readInto(FireLampGatt.LED_SETTINGS) { _ledSettings.value = FireLampCodec.decodeLedSettings(it) } }
                    }
                }
                FireLampGatt.WIFI_NETWORKS -> {
                    val scan = FireLampCodec.decodeWifiScan(value)
                    // Notifications carry only the header: read every page once the scan is done,
                    // showing "scanning" until then rather than an empty list
                    _wifiScan.value = if (scan.state == WifiScanState.DONE) scan.copy(state = WifiScanState.SCANNING) else scan
                    if (scan.state == WifiScanState.DONE) {
                        scope.launch { guarded(FireLampGatt.WIFI_NETWORKS) { _wifiScan.value = readAllWifiNetworks() } }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Bad notification from $uuid", e)
        }
    }

    private fun applyParamChange(change: EffectParamChange) {
        updateEffect(change.effectCode) { effect ->
            effect.copy(params = effect.params.map { if (it.code == change.paramCode) it.copy(value = change.value) else it })
        }
    }

    // ========================== Lamp control ==========================

    /** Lamp state comes back as a notification, so nothing is read here. */
    suspend fun updateLampState(update: LampStateUpdate): Boolean = guarded(FireLampGatt.LAMP_STATE) {
        bleManager.write(FireLampGatt.LAMP_STATE, FireLampCodec.encodeLampState(update))
    }

    suspend fun setPower(on: Boolean) = updateLampState(LampStateUpdate(on = on))
    suspend fun setBrightness(brightness: Int) = updateLampState(LampStateUpdate(brightness = brightness))
    suspend fun setEffect(code: String) = updateLampState(LampStateUpdate(effectCode = code))
    suspend fun setDemo(on: Boolean) = updateLampState(LampStateUpdate(demo = on))
    suspend fun setFps(fps: Int) = updateLampState(LampStateUpdate(fps = fps))

    /**
     * Writing the current value again gives no notification, so the value is applied
     * locally once the lamp accepted it.
     */
    suspend fun setEffectParam(change: EffectParamChange): Boolean = guarded(FireLampGatt.EFFECT_PARAM) {
        bleManager.write(FireLampGatt.EFFECT_PARAM, FireLampCodec.encodeEffectParamChange(change))
        applyParamChange(change)
    }

    suspend fun sendCommand(command: Command): Boolean = guarded(FireLampGatt.COMMAND) {
        bleManager.write(FireLampGatt.COMMAND, FireLampCodec.encodeCommand(command))
        if (command == Command.REBOOT) _rebootRequired.value = false
        if (command == Command.RESET_EFFECTS) refreshEffects()
    }

    /**
     * Factory reset forgets all bonded phones, this one too: the phone's half of the bond is
     * removed right away, so that after the reboot (which opens the pairing window) the app
     * pairs anew instead of being rejected with stale keys.
     */
    suspend fun factoryReset(): Boolean {
        val mac = _currentDevice.value?.macAddress ?: return false
        if (!sendCommand(Command.FACTORY_RESET)) return false
        forgetBond(mac)
        return true
    }

    // ========================== Time ==========================

    /** The phone's time, written on every connection (the lamp keeps UTC). */
    suspend fun syncTime(): Boolean = guarded(FireLampGatt.TIME) {
        bleManager.write(FireLampGatt.TIME, FireLampCodec.encodeTime(System.currentTimeMillis() / 1000))
        _lampTime.value = FireLampCodec.decodeTime(bleManager.read(FireLampGatt.TIME))
    }

    /** The phone's time zone, written when the lamp has another one (first setup, travel). */
    suspend fun syncTimeZone(): Boolean {
        val posix = TimeZoneMapper.toPosix(ZoneId.systemDefault())
        if (posix == _timeZone.value) return true
        return setTimeZone(posix)
    }

    suspend fun setTimeZone(posix: String): Boolean = guarded(FireLampGatt.TIME_ZONE) {
        bleManager.write(FireLampGatt.TIME_ZONE, FireLampCodec.encodeTimeZone(posix))
        _timeZone.value = FireLampCodec.decodeTimeZone(bleManager.read(FireLampGatt.TIME_ZONE))
    }

    // ========================== Settings ==========================

    suspend fun setSchedule(schedule: Schedule): Boolean = guarded(FireLampGatt.SCHEDULE) {
        bleManager.write(FireLampGatt.SCHEDULE, FireLampCodec.encodeSchedule(schedule))
        _schedule.value = FireLampCodec.decodeSchedule(bleManager.read(FireLampGatt.SCHEDULE))
    }

    suspend fun setDemoSettings(settings: DemoSettings): Boolean = guarded(FireLampGatt.DEMO_SETTINGS) {
        bleManager.write(FireLampGatt.DEMO_SETTINGS, FireLampCodec.encodeDemoSettings(settings))
        _demoSettings.value = FireLampCodec.decodeDemoSettings(bleManager.read(FireLampGatt.DEMO_SETTINGS))
    }

    suspend fun setDemoIncluded(effectCode: String, include: Boolean): Boolean = guarded(FireLampGatt.DEMO_EFFECTS) {
        bleManager.write(FireLampGatt.DEMO_EFFECTS, FireLampCodec.encodeDemoEffect(effectCode, include))
        val info = readEffectInfo(EffectSelector.ByCode(effectCode))
        updateEffect(effectCode) { info }
    }

    suspend fun setLedSettings(settings: LedSettings): Boolean = guarded(FireLampGatt.LED_SETTINGS) {
        bleManager.write(FireLampGatt.LED_SETTINGS, FireLampCodec.encodeLedSettings(settings))
        _ledSettings.value = FireLampCodec.decodeLedSettings(bleManager.read(FireLampGatt.LED_SETTINGS))
        _rebootRequired.value = true
    }

    /** [mainButtonGpio] null keeps the pin (always null for lamps older than firmware 0.4.3). */
    suspend fun setHardwareSettings(controls: Controls, buttonsActiveHigh: Boolean, mainButtonGpio: Int?): Boolean =
        guarded(FireLampGatt.HARDWARE_SETTINGS) {
            bleManager.write(
                FireLampGatt.HARDWARE_SETTINGS,
                FireLampCodec.encodeHardwareSettings(controls, buttonsActiveHigh, mainButtonGpio)
            )
            _hardwareSettings.value = FireLampCodec.decodeHardwareSettings(bleManager.read(FireLampGatt.HARDWARE_SETTINGS))
            _rebootRequired.value = true
        }

    suspend fun setDeviceName(name: String): Boolean = guarded(FireLampGatt.DEVICE_NAME) {
        bleManager.write(FireLampGatt.DEVICE_NAME, FireLampCodec.encodeDeviceName(name))
        _deviceName.value = FireLampCodec.decodeDeviceName(bleManager.read(FireLampGatt.DEVICE_NAME))
        _rebootRequired.value = true
    }

    /** The page cursor is shared lamp state: one reader at a time. */
    private val wifiNetworksMutex = Mutex()

    /**
     * The whole scan result: the first read gives page 0 (the lamp resets the cursor after every
     * scan and on connecting); while first + N < total, write first + N and read the next page.
     */
    private suspend fun readAllWifiNetworks(): WifiScan = wifiNetworksMutex.withLock {
        val head = FireLampCodec.decodeWifiScan(bleManager.read(FireLampGatt.WIFI_NETWORKS))
        if (head.state != WifiScanState.DONE) return@withLock head
        val networks = head.networks.toMutableList()
        var page = head
        while (page.first + page.networks.size < page.total && networks.size < page.total) {
            bleManager.write(FireLampGatt.WIFI_NETWORKS, FireLampCodec.encodeWifiScanCursor(page.first + page.networks.size))
            val next = FireLampCodec.decodeWifiScan(bleManager.read(FireLampGatt.WIFI_NETWORKS))
            // A new scan started meanwhile, or no progress: keep what we have
            if (next.state != WifiScanState.DONE || next.networks.isEmpty() || next.first != page.first + page.networks.size) break
            networks += next.networks
            page = next
        }
        head.copy(first = 0, networks = networks)
    }

    /**
     * Asks the lamp to scan (2-3 s, it hears 2.4 GHz networks only); progress and the result come
     * as WiFi networks notifications. Firmware without the scan has no such characteristic.
     */
    suspend fun startWifiScan(): Boolean {
        if (!bleManager.has(FireLampGatt.WIFI_NETWORKS)) return false
        return sendCommand(Command.SCAN_WIFI)
    }

    suspend fun setWifiSettings(update: WifiSettingsUpdate): Boolean = guarded(FireLampGatt.WIFI_SETTINGS) {
        bleManager.write(FireLampGatt.WIFI_SETTINGS, FireLampCodec.encodeWifiSettings(update))
        _wifiSettings.value = FireLampCodec.decodeWifiSettings(bleManager.read(FireLampGatt.WIFI_SETTINGS))
    }

    /** Brownout brightness and the power supply calibration (firmware 0.9.0+). */
    suspend fun sendPowerCommand(command: PowerCommand): Boolean {
        if (!bleManager.has(FireLampGatt.POWER)) return false
        return guarded(FireLampGatt.POWER) {
            bleManager.write(FireLampGatt.POWER, FireLampCodec.encodePowerCommand(command))
            _powerStatus.value = FireLampCodec.decodePowerStatus(bleManager.read(FireLampGatt.POWER))
        }
    }

    // ========================== OTA ==========================

    val negotiatedMtu: Int get() = bleManager.negotiatedMtu

    suspend fun writeOtaControl(value: ByteArray): Boolean = guarded(FireLampGatt.OTA_CONTROL) {
        bleManager.write(FireLampGatt.OTA_CONTROL, value)
    }

    suspend fun writeOtaData(chunk: ByteArray): Boolean = guarded(FireLampGatt.OTA_DATA) {
        bleManager.write(FireLampGatt.OTA_DATA, chunk)
    }

    fun resetOtaStatus() {
        _otaStatus.value = null
    }

    // ========================== Helpers ==========================

    private suspend fun readInto(uuid: UUID, apply: (ByteArray) -> Unit) {
        guarded(uuid) { apply(bleManager.read(uuid)) }
    }

    /** Runs a GATT operation; a failure is logged and reported on [errors] instead of thrown. */
    private suspend fun guarded(uuid: UUID, block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(tag, "Operation on $uuid failed", e)
        _errors.tryEmit(OperationError(uuid, e.message ?: e.javaClass.simpleName))
        false
    }

    private companion object {
        /**
         * GATT statuses of a link the lamp does not accept: insufficient authentication (the lamp's
         * answer before pairing completed), insufficient encryption, and Android's GATT_AUTH_FAIL.
         */
        val AUTH_FAILURES = setOf(0x05, FireLampGatt.ATT_INSUFFICIENT_ENCRYPTION, 0x89)

        /** How long the link outlives the app leaving the screen. */
        const val BACKGROUND_KEEP_MS = 5 * 60 * 1000L
    }
}

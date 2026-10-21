package net.agolyakov.firelamp.ui.screen.home

import android.Manifest
import android.bluetooth.le.*
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.agolyakov.firelamp.data.model.ble.FireLampDevice
import net.agolyakov.firelamp.data.extensions.UNNAMED
import net.agolyakov.firelamp.data.extensions.toBleDevice
import net.agolyakov.firelamp.data.local.AppLanguage
import net.agolyakov.firelamp.data.local.KnownLamps
import net.agolyakov.firelamp.data.local.LanguagePreferences
import net.agolyakov.firelamp.data.repository.AppUpdateRepository
import net.agolyakov.firelamp.service.bluetooth.BlePermissions
import net.agolyakov.firelamp.service.bluetooth.BluetoothAdapterProvider
import net.agolyakov.firelamp.service.bluetooth.BluetoothService
import net.agolyakov.firelamp.domain.repository.PreferencesRepository
import net.agolyakov.firelamp.domain.usecase.LoadDeviceWithNameUseCase
import net.agolyakov.firelamp.protocol.FireLampGatt
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

@HiltViewModel
class HomeViewModel @Inject constructor(
    val bluetoothService: BluetoothService,
    private val bluetoothAdapterProvider: BluetoothAdapterProvider,
    private val preferencesRepository: PreferencesRepository,
    private val loadDeviceWithNameUseCase: LoadDeviceWithNameUseCase,
    private val languagePreferences: LanguagePreferences,
    private val knownLamps: KnownLamps,
    appUpdateRepository: AppUpdateRepository
) : ViewModel() {
    val language: AppLanguage get() = languagePreferences.language

    /** Stored only — the caller recreates the activity to pick the new language up. */
    fun setLanguage(language: AppLanguage) {
        languagePreferences.language = language
    }

    private val foundDevices = HashMap<String, FireLampDevice>()
    private val _devices: MutableLiveData<List<FireLampDevice>> = MutableLiveData()
    val devices: LiveData<List<FireLampDevice>> get() = _devices

    val appVersion: String = appUpdateRepository.currentVersion

    private val _appUpdate = MutableStateFlow<AppUpdateRepository.AppUpdate?>(null)
    val appUpdate: StateFlow<AppUpdateRepository.AppUpdate?> = _appUpdate.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                _appUpdate.value = appUpdateRepository.checkForUpdate()
                delay(APP_UPDATE_CHECK_INTERVAL)
            }
        }
    }

    private val adapter = bluetoothAdapterProvider.getAdapter()
    private var scanner: BluetoothLeScanner? = null
    private var callback: BleScanCallback? = null

    private val settings: ScanSettings =
        ScanSettings.Builder()
            // Active scanning (the Android default): the lamp's name is in the scan response
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()

    private val filters: List<ScanFilter> = listOf(
        ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(FireLampGatt.SERVICE))
            .build()
    )

    /**
     * Starts scanning unless it already runs. Safe to call on every return to the Home screen:
     * that is also how scanning starts after the user granted the permissions (or turned
     * Bluetooth on) - both bring the screen back to the foreground.
     */
    fun startScan() {
        if (callback != null) return
        if (!BlePermissions.granted(bluetoothAdapterProvider.getContext())) return
        addConnectedLamps()
        // Null while Bluetooth is off
        val scanner = adapter.bluetoothLeScanner ?: return
        val callback = BleScanCallback()
        try {
            scanner.startScan(filters, settings, callback)
        } catch (e: SecurityException) {
            Log.w("BluetoothScanner", "No permission to scan", e)
            return
        }
        this.scanner = scanner
        this.callback = callback
    }

    fun stopScan() {
        val callback = callback ?: return
        try {
            scanner?.stopScan(callback)
        } catch (e: SecurityException) {
            Log.w("BluetoothScanner", "No permission to stop the scan", e)
        }
        scanner = null
        this.callback = null
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
    }

    inner class BleScanCallback : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            addResult(result)
            _devices.postValue(foundDevices.values.toList())
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { addResult(it) }
            _devices.postValue(foundDevices.values.toList())
        }

        // The advertising packet carries only the service UUID, the name comes with the scan
        // response: a lamp may first show up unnamed, so a later named result replaces it
        private fun addResult(result: ScanResult) {
            val known = foundDevices[result.device.address]
            if (known == null || !known.inRange ||
                (known.deviceName == UNNAMED && result.scanRecord?.deviceName != null)
            ) {
                foundDevices[result.device.address] = loadDeviceWithNameUseCase(result.toBleDevice())
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BluetoothScanner", "onScanFailed: scan error $errorCode")
        }
    }

    /**
     * A lamp does not advertise while connected, and Android often keeps the link to a just
     * bonded lamp open by itself (the lamp drops it after 5 idle minutes). Such lamps are taken
     * from the phone's GATT links instead of the scan: connecting to them works anyway, as
     * Android shares the link. A lamp is recognized by the default name, by the service UUID
     * the system cached, or because this app used it before.
     */
    private fun addConnectedLamps() {
        // The names, UUIDs and the list itself all need BLUETOOTH_CONNECT, which can be revoked
        try {
            addConnectedLampsUnchecked()
        } catch (e: SecurityException) {
            Log.w("BluetoothScanner", "No permission to list connected devices", e)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun addConnectedLampsUnchecked() {
        val connected = bluetoothAdapterProvider.getConnectedGattDevices()
        val lamps = connected.filter { device ->
            device.name?.startsWith(DEFAULT_NAME_PREFIX) == true ||
                device.uuids?.any { it.uuid == FireLampGatt.SERVICE } == true ||
                knownLamps.contains(device.address)
        }
        for (device in lamps) {
            if (foundDevices.containsKey(device.address)) continue
            foundDevices[device.address] = loadDeviceWithNameUseCase(
                FireLampDevice(deviceName = device.name ?: UNNAMED, macAddress = device.address, inRange = false)
            )
        }
        _devices.value = foundDevices.values.toList()

        // Straight to the lamp the phone is already connected to - once per app start, so that
        // the user can still get back to this list
        if (!autoOpenDone) {
            autoOpenDone = true
            lamps.singleOrNull()?.let { _autoOpen.value = foundDevices[it.address] }
        }
    }

    /** A lamp to open right away instead of showing the list; consumed by [onAutoOpened]. */
    private val _autoOpen = MutableStateFlow<FireLampDevice?>(null)
    val autoOpen: StateFlow<FireLampDevice?> = _autoOpen.asStateFlow()

    fun onAutoOpened() {
        _autoOpen.value = null
    }

    fun renameDevice(macAddress: String, newName: String?) {
        if (newName.isNullOrBlank()) {
            preferencesRepository.deleteFriendlyName(macAddress)
        } else {
            preferencesRepository.saveFriendlyName(macAddress, newName)
        }
        foundDevices[macAddress]?.let { device ->
            val updatedDevice = device.copy(friendlyName = if (newName.isNullOrBlank()) null else newName)
            foundDevices[macAddress] = updatedDevice
            _devices.value = foundDevices.values.toList()
        }
    }

    companion object {
        private val APP_UPDATE_CHECK_INTERVAL = 10.minutes
        private const val DEFAULT_NAME_PREFIX = "FireLamp-"

        /** The automatic opening happens once per process, not on every return to the list. */
        private var autoOpenDone = false
    }
}

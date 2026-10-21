package net.agolyakov.firelamp.service.firmware

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.agolyakov.firelamp.data.model.ble.ConnectionState
import net.agolyakov.firelamp.data.model.firmware.FirmwareBuild
import net.agolyakov.firelamp.data.model.firmware.FirmwareManifest
import net.agolyakov.firelamp.data.model.firmware.FirmwareVersion
import net.agolyakov.firelamp.protocol.Command
import net.agolyakov.firelamp.protocol.FireLampCodec
import net.agolyakov.firelamp.protocol.OtaError
import net.agolyakov.firelamp.protocol.OtaState
import net.agolyakov.firelamp.protocol.OtaStatus
import net.agolyakov.firelamp.service.bluetooth.BluetoothService
import net.agolyakov.firelamp.utils.HashUtils
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firmware update of the connected lamp, both ways of `APP_INTEGRATION.md`, section 9:
 *
 * - **from the phone over BLE**: the release `manifest.json` names the file for the lamp's chip
 *   and build variant; it is downloaded, checked (size, SHA-256) and written to the lamp in
 *   `MTU - 3` byte chunks, each with response (the response is the flow control);
 * - **by the lamp from the internet** (WiFi configured): Command `0x06` checks, `0x07` installs;
 *   the lamp reports progress in the same OTA status notifications.
 *
 * Either way the lamp reboots into the new firmware; the update is done when the reconnected
 * lamp reports the new version (the Device screen reconnects by itself). If the new firmware
 * does not start, the bootloader rolls back and the old version comes back.
 *
 * A singleton with its own scope: the update goes on while the user switches tabs.
 */
@Singleton
class FirmwareUpdater @Inject constructor(
    private val bluetoothService: BluetoothService,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context,
) {
    sealed class State {
        object Idle : State()
        object Checking : State()
        data class UpToDate(val version: String) : State()
        /** The release has no file for this lamp's chip + variant (custom hardware). */
        data class NoOfficialBuild(val buildKey: String?, val latestVersion: String) : State()
        data class Available(val version: String, val build: FirmwareBuild) : State()
        data class Downloading(val fraction: Float) : State()
        /** Start sent: the lamp erases the OTA partition, that takes several seconds. */
        object Preparing : State()
        data class Uploading(val sent: Long, val total: Long) : State()
        /** All sent, "end" written: the lamp verifies the image; past the point of no return. */
        object Verifying : State()
        /** Lamp's own update from the internet. */
        object InternetChecking : State()
        data class InternetDownloading(val received: Long, val total: Long) : State()
        /** Image accepted, the lamp reboots; waiting for it to come back with the new version. */
        data class Rebooting(val expectedVersion: String?) : State()
        data class Done(val version: String) : State()
        /** The lamp came back with the old version: the new firmware did not start. */
        data class RolledBack(val version: String) : State()
        data class Failed(val reason: Reason, val detail: String? = null) : State()

        val isBusy: Boolean
            get() = this is Checking || this is Downloading || this is Preparing || this is Uploading || this is Verifying ||
                this is InternetChecking || this is InternetDownloading || this is Rebooting
    }

    enum class Reason {
        NO_INTERNET,      // manifest or file could not be downloaded
        BAD_FILE,         // size or SHA-256 mismatch
        NOT_CONNECTED,
        LAMP_ERROR,       // the lamp reported an OTA error, see detail (OtaError)
        TIMEOUT,          // the lamp stopped answering
        NOT_VERIFIED,     // the lamp did not come back after the reboot
        CANCELLED,
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Back to idle (e.g. the user closed the result). Does not stop a running update. */
    fun reset() {
        if (!_state.value.isBusy) _state.value = State.Idle
    }

    // ========================== Check ==========================

    fun checkForUpdate() = launch {
        val info = bluetoothService.deviceInfo.value ?: return@launch fail(Reason.NOT_CONNECTED)
        _state.value = State.Checking
        val manifest = try {
            fetchManifest()
        } catch (e: Exception) {
            Log.w(TAG, "Manifest download failed", e)
            return@launch fail(Reason.NO_INTERNET, e.message)
        }
        val build = manifest.buildFor(info)
        _state.value = when {
            build == null -> State.NoOfficialBuild(info.firmwareBuildKey, manifest.version)
            FirmwareVersion.isNewer(manifest.version, info.firmwareVersion) -> State.Available(manifest.version, build)
            else -> State.UpToDate(info.firmwareVersion)
        }
    }

    private suspend fun fetchManifest(): FirmwareManifest = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(MANIFEST_URL).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            Gson().fromJson(response.body.charStream(), FirmwareManifest::class.java)
                ?: throw IOException("Empty manifest")
        }
    }

    // ========================== From the phone over BLE ==========================

    fun installFromPhone(version: String, build: FirmwareBuild) = launch {
        val file = try {
            download(build)
        } catch (e: CancellationException) {
            throw e
        } catch (e: BadFileException) {
            return@launch fail(Reason.BAD_FILE, e.message)
        } catch (e: Exception) {
            Log.w(TAG, "Firmware download failed", e)
            return@launch fail(Reason.NO_INTERNET, e.message)
        }
        try {
            upload(file, build.sha256)
            awaitNewVersion(version)
        } finally {
            file.delete()
        }
    }

    private class BadFileException(message: String) : IOException(message)

    private suspend fun download(build: FirmwareBuild): File = withContext(Dispatchers.IO) {
        _state.value = State.Downloading(0f)
        val file = File(context.cacheDir, "firmware.bin")
        val request = Request.Builder().url(build.url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val total = response.body.contentLength().takeIf { it > 0 } ?: build.size
            response.body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var done = 0L
                    while (true) {
                        // Blocking reads never notice a cancel by themselves
                        ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        _state.value = State.Downloading((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        if (file.length() != build.size) {
            file.delete()
            throw BadFileException("Size ${file.length()}, expected ${build.size}")
        }
        if (!HashUtils.verifyFileHash(file, build.sha256)) {
            file.delete()
            throw BadFileException("SHA-256 mismatch")
        }
        file
    }

    private suspend fun upload(file: File, sha256Hex: String) {
        if (bluetoothService.connectionState.value != ConnectionState.Ready) throw UpdateFailure(Reason.NOT_CONNECTED)
        bluetoothService.enterOtaUpdateMode()
        try {
            bluetoothService.resetOtaStatus()
            _state.value = State.Preparing
            if (!bluetoothService.writeOtaControl(FireLampCodec.encodeOtaStart(file.length(), hexToBytes(sha256Hex)))) {
                throw UpdateFailure(Reason.LAMP_ERROR)
            }
            // The lamp erases the partition first, then reports "receiving"
            val ready = awaitOtaStatus(PREPARE_TIMEOUT_MS) { it.state == OtaState.RECEIVING || it.state == OtaState.ERROR }
            ready.throwIfError()

            val chunkSize = (bluetoothService.negotiatedMtu - 3).coerceIn(20, 514)
            val total = file.length()
            var sent = 0L
            file.inputStream().use { input ->
                val buffer = ByteArray(chunkSize)
                while (true) {
                    // The BLE write (ble-ktx suspend()) is not cancellable: without this check
                    // Cancel would only take effect once the whole file was sent
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    // The write response arrives once the chunk is queued for the flash: flow control
                    if (!bluetoothService.writeOtaData(buffer.copyOf(n))) {
                        bluetoothService.otaStatus.value?.throwIfError()
                        throw UpdateFailure(Reason.TIMEOUT)
                    }
                    sent += n
                    _state.value = State.Uploading(sent, total)
                }
            }

            // From here on the lamp finishes by itself: no cancel (the UI hides the button)
            _state.value = State.Verifying
            if (!bluetoothService.writeOtaControl(FireLampCodec.encodeOtaEnd())) {
                bluetoothService.otaStatus.value?.throwIfError()
                throw UpdateFailure(Reason.LAMP_ERROR)
            }
            awaitOtaStatus(FINISH_TIMEOUT_MS) { it.state == OtaState.SUCCESS || it.state == OtaState.ERROR }.throwIfError()
        } catch (e: CancellationException) {
            abortQuietly()
            throw e
        } catch (e: UpdateFailure) {
            abortQuietly()
            throw e
        } finally {
            bluetoothService.exitOtaUpdateMode()
        }
    }

    /** Also called from a cancelled coroutine: the abort must still reach the lamp. */
    private suspend fun abortQuietly() = withContext(NonCancellable) {
        try {
            bluetoothService.writeOtaControl(FireLampCodec.encodeOtaAbort())
        } catch (_: Exception) {
        }
    }

    // ========================== By the lamp from the internet ==========================

    /** Asks the lamp to check now; the result comes in Update info (BluetoothService.updateInfo). */
    fun askLampToCheck() {
        scope.launch { bluetoothService.sendCommand(Command.CHECK_UPDATE) }
    }

    /** The lamp downloads and installs the release itself; it goes on without the phone too. */
    fun installByLamp(expectedVersion: String?) = launch {
        bluetoothService.resetOtaStatus()
        _state.value = State.InternetChecking
        if (!bluetoothService.sendCommand(Command.INSTALL_UPDATE)) throw UpdateFailure(Reason.LAMP_ERROR)
        // Follow the lamp's progress until success or an error / "no update". (Not withTimeout:
        // its exception is a CancellationException and would read as "cancelled".)
        withTimeoutOrNull(INTERNET_TIMEOUT_MS) {
            bluetoothService.otaStatus.filterNotNull().first { status ->
                when (status.state) {
                    OtaState.CHECKING -> _state.value = State.InternetChecking
                    OtaState.RECEIVING -> _state.value = State.InternetDownloading(status.received, status.total)
                    else -> {}
                }
                status.state in TERMINAL_STATES
            }
        }.let { last ->
            if (last == null) throw UpdateFailure(Reason.TIMEOUT)
            when (last.state) {
                OtaState.NO_UPDATE -> {
                    _state.value = State.UpToDate(bluetoothService.deviceInfo.value?.firmwareVersion ?: "")
                    return@launch
                }
                else -> last.throwIfError()
            }
        }
        awaitNewVersion(expectedVersion)
    }

    // ========================== Common ==========================

    /** After SUCCESS the lamp reboots in 1 s; wait until it is back and check its version. */
    private suspend fun awaitNewVersion(expected: String?) {
        val oldVersion = bluetoothService.deviceInfo.value?.firmwareVersion
        _state.value = State.Rebooting(expected)
        // First the link goes down...
        withTimeoutOrNull(DISCONNECT_TIMEOUT_MS) {
            bluetoothService.connectionState.first { it != ConnectionState.Ready }
        }
        // ...then the reconnected lamp reports its version
        val version = withTimeoutOrNull(REBOOT_TIMEOUT_MS) {
            bluetoothService.connectionState.first { it == ConnectionState.Ready }
            bluetoothService.deviceInfo.filterNotNull().first().firmwareVersion
        } ?: throw UpdateFailure(Reason.NOT_VERIFIED)
        _state.value = when {
            expected != null && version == expected -> State.Done(version)
            expected == null && version != oldVersion -> State.Done(version)
            else -> State.RolledBack(version)
        }
    }

    private suspend fun awaitOtaStatus(timeoutMs: Long, done: (OtaStatus) -> Boolean): OtaStatus =
        withTimeoutOrNull(timeoutMs) { bluetoothService.otaStatus.filterNotNull().first(done) }
            ?: throw UpdateFailure(Reason.TIMEOUT)

    private fun OtaStatus.throwIfError() {
        if (state == OtaState.ERROR) throw UpdateFailure(Reason.LAMP_ERROR, error.name)
    }

    private class UpdateFailure(val reason: Reason, val detail: String? = null) : Exception(reason.name)

    /** Runs one update action; only one at a time. */
    private fun launch(block: suspend CoroutineScope.() -> Unit) {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                block()
            } catch (e: UpdateFailure) {
                fail(e.reason, e.detail)
            } catch (e: CancellationException) {
                fail(Reason.CANCELLED)
            } catch (e: Exception) {
                Log.e(TAG, "Update failed", e)
                fail(Reason.LAMP_ERROR, e.message)
            }
        }
    }

    /**
     * Stops an upload from the phone (the lamp discards what it received). Only before the "end"
     * command: after it the lamp installs the image anyway, and a cancel would only make the app
     * report "cancelled" for a lamp that updates; the lamp's own update cannot be cancelled.
     */
    fun cancel() {
        val state = _state.value
        if (state is State.Downloading || state is State.Preparing || state is State.Uploading) job?.cancel()
    }

    private fun fail(reason: Reason, detail: String? = null) {
        _state.value = State.Failed(reason, detail)
    }

    private fun hexToBytes(hex: String): ByteArray? =
        hex.takeIf { it.length == 64 }?.chunked(2)?.map { it.toInt(16).toByte() }?.toByteArray()

    private companion object {
        const val TAG = "FirmwareUpdater"
        const val MANIFEST_URL = "https://github.com/golyakoff/FireLamp_ESP32/releases/latest/download/manifest.json"
        const val PREPARE_TIMEOUT_MS = 30_000L
        const val FINISH_TIMEOUT_MS = 30_000L
        const val INTERNET_TIMEOUT_MS = 10 * 60_000L
        const val DISCONNECT_TIMEOUT_MS = 15_000L
        const val REBOOT_TIMEOUT_MS = 90_000L
        val TERMINAL_STATES = setOf(OtaState.SUCCESS, OtaState.ERROR, OtaState.NO_UPDATE)
    }
}

/** For the UI: an [OtaError] name from [FirmwareUpdater.State.Failed.detail], if it is one. */
fun otaErrorOf(detail: String?): OtaError? = OtaError.entries.firstOrNull { it.name == detail }

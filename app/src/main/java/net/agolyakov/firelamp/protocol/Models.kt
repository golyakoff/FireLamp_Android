package net.agolyakov.firelamp.protocol

import java.io.Serializable

/**
 * Plain models of the FireLamp characteristic values, one section per characteristic of
 * `BLE_PROTOCOL.md`. No Android dependencies: [FireLampCodec] converts them to and from bytes.
 */

/** An enum with a wire code. */
interface Coded {
    val code: Int
}

/** The entry with [code], or [fallback] for a code from a newer firmware. */
inline fun <reified E> codedOf(code: Int, fallback: E): E where E : Enum<E>, E : Coded =
    enumValues<E>().firstOrNull { it.code == code } ?: fallback

// ---------- 0x0001 Device info ----------

enum class Chip(override val code: Int, /** Chip part of the manifest build key. */ val manifestName: String) : Coded {
    ESP32(0, "esp32"),
    ESP32_C3(1, "esp32c3"),
    UNKNOWN(-1, "")
}

enum class Controls(override val code: Int) : Coded {
    ONE_BUTTON(0),
    FOUR_BUTTONS(1),
    TOUCH_PAD(2),
    UNKNOWN(-1)
}

data class DeviceInfo(
    val protocolVersion: Int,
    val chip: Chip,
    val flashSizeMb: Int,
    val features: Int,
    val matrixWidth: Int,
    val matrixHeight: Int,
    val effectCount: Int,
    val controls: Controls,
    val firmwareVersion: String,
    val buildDate: String,
    /** `default` for official releases (hardware configuration). */
    val buildVariant: String,
    /**
     * The firmware in the lamp's other OTA slot, which [Command.ROLLBACK] goes back to; empty if
     * there is none or the lamp is older than firmware 0.8.0.
     */
    val previousFirmware: String = "",
) {
    val hasRtc: Boolean get() = features and FEATURE_RTC != 0
    val hasEeprom: Boolean get() = features and FEATURE_EEPROM != 0
    val wifiSupported: Boolean get() = features and FEATURE_WIFI != 0
    val internetUpdateSupported: Boolean get() = features and FEATURE_INTERNET_UPDATE != 0

    /** Key of this lamp's file in the release `manifest.json`, e.g. `esp32c3-default`. */
    val firmwareBuildKey: String? get() = chip.manifestName.takeIf { it.isNotEmpty() }?.let { "$it-$buildVariant" }

    companion object {
        const val FEATURE_RTC = 1 shl 0
        const val FEATURE_EEPROM = 1 shl 1
        const val FEATURE_WIFI = 1 shl 2
        const val FEATURE_INTERNET_UPDATE = 1 shl 3
    }
}

// ---------- 0x0002 Lamp state ----------

data class LampState(
    val on: Boolean,
    val brightness: Int,
    val fps: Int,
    val demo: Boolean,
    val effectCode: String,
)

/** A Lamp state write: only the non-null fields are applied (the mask is built from them). */
data class LampStateUpdate(
    val on: Boolean? = null,
    val effectCode: String? = null,
    val brightness: Int? = null,
    val fps: Int? = null,
    val demo: Boolean? = null,
)

// ---------- 0x0003 Effect info / 0x0004 Effect parameter ----------

enum class ParamType(override val code: Int) : Coded {
    RANGE(0),
    BOOL(1)
}

data class EffectParam(
    val code: String,
    val name: String,
    val type: ParamType,
    val min: Int,
    val max: Int,
    val default: Int,
    val value: Int,
)

data class EffectInfo(
    val position: Int,
    val count: Int,
    val code: String,
    val name: String,
    val inDemo: Boolean,
    val params: List<EffectParam>,
)

/** Effect info cursor written before a read. */
sealed class EffectSelector {
    data class ByPosition(val position: Int) : EffectSelector()
    data class ByCode(val code: String) : EffectSelector()
}

/** Effect parameter read: current values of all parameters of the current effect. */
data class EffectParamValues(
    val effectCode: String,
    val values: Map<String, Int>,
)

/** Effect parameter write, and the notification of a changed value. */
data class EffectParamChange(
    val effectCode: String,
    val paramCode: String,
    val value: Int,
)

// ---------- 0x0005 Time ----------

enum class TimeSource(override val code: Int) : Coded {
    NONE(0),
    RTC(1),
    BLE(2),
    SNTP(3),
    HTTP_API(4),
    UNKNOWN(-1)
}

data class LampTime(
    /** Seconds since 1970-01-01 UTC. */
    val epochSeconds: Long,
    /** The time has been set since boot; schedules work only then. */
    val valid: Boolean,
    val source: TimeSource,
)

// ---------- 0x0007 Schedule ----------

// Values edited as drafts on screen are Serializable: the drafts outlive a tab switch and an
// activity restart in the saved instance state

data class ScheduleTime(
    val enabled: Boolean = false,
    /** Bit 0 - Monday ... bit 6 - Sunday. */
    val days: Int = EVERY_DAY,
    val hour: Int = 0,
    val minute: Int = 0,
) : Serializable {
    companion object {
        const val EVERY_DAY = 0x7F
        const val WORKDAYS = 0x1F
    }
}

data class Schedule(
    val powerOn: ScheduleTime,
    val powerOff: ScheduleTime,
    /** The moment the final dawn brightness is reached. */
    val dawn: ScheduleTime,
    val dawnDurationMinutes: Int,
    val dawnBrightness: Int,
    val hourlyBrightnessEnabled: Boolean,
    /** 24 values `1..255`, for hours 0..23. */
    val hourlyBrightness: List<Int>,
    val dawnEffectCode: String,
) : Serializable

// ---------- 0x0008 LED settings ----------

enum class LedType(override val code: Int) : Coded {
    WS2812(0),
    SK6812(1),
    WS2811(2)
}

enum class ColorOrder(override val code: Int) : Coded {
    GRB(0),
    RGB(1)
}

enum class Rotation(override val code: Int, val degrees: Int) : Coded {
    NONE(0, 0),
    CW_90(1, 90),
    CW_180(2, 180),
    CW_270(3, 270)
}

enum class Wiring(override val code: Int) : Coded {
    /** Rows go back and forth. */
    SERPENTINE(0),
    /** Every row starts at the same side. */
    PROGRESSIVE(1)
}

data class LedSettings(
    val width: Int,
    val height: Int,
    val ledType: LedType,
    val colorOrder: ColorOrder,
    val rotation: Rotation,
    val mirror: Boolean,
    val currentLimitMa: Int,
    val wiring: Wiring,
) : Serializable {
    companion object {
        const val MIN_SIZE = 8
        const val MAX_SIZE = 128
        const val MAX_LEDS = 1024
        const val MAX_CURRENT_LIMIT_MA = 0xFFFF

        /** The firmware's default `CONFIG_FL_SINGLE_LED_CURRENT_MA` (a build option, not readable). */
        const val DEFAULT_SINGLE_LED_CURRENT_MA = 33

        /**
         * The lowest current limit the lamp accepts for a matrix: enough for every LED at 1/50 of
         * its full current. Computed with the default per-LED current, so it is a hint: a lamp
         * built with another value checks its own.
         */
        fun minCurrentLimitMa(width: Int, height: Int): Int =
            width * height * DEFAULT_SINGLE_LED_CURRENT_MA / 50
    }
}

// ---------- 0x0009 WiFi settings / 0x000A WiFi status ----------

enum class WifiMode(override val code: Int) : Coded {
    OFF(0),
    /** Connect every N hours for time sync and update check, then disconnect. */
    PERIODIC(1),
    /** Stay connected, the HTTP API is available. */
    ALWAYS_ON(2)
}

data class WifiSettings(
    val mode: WifiMode,
    val periodHours: Int,
    val ssid: String,
    /** Read: whether a password is stored (it is never returned). */
    val passwordStored: Boolean,
) {
    companion object {
        const val MAX_SSID_BYTES = 32
        const val MAX_PASSWORD_BYTES = 64
    }
}

data class WifiSettingsUpdate(
    val mode: WifiMode,
    val periodHours: Int,
    val ssid: String,
    /** Null keeps the stored password. */
    val password: String?,
)

enum class WifiState(override val code: Int) : Coded {
    OFF(0),
    CONNECTING(1),
    CONNECTED(2),
    ERROR(3),
    UNKNOWN(-1)
}

data class WifiStatus(
    val state: WifiState,
    /** Dotted IPv4, null if not connected. */
    val ip: String?,
    /** dBm, 0 if not connected. */
    val rssi: Int,
)

// ---------- 0x0014 WiFi networks (scan) ----------

enum class WifiScanState(override val code: Int) : Coded {
    /** No scan since boot. */
    NONE(0),
    SCANNING(1),
    DONE(2),
    ERROR(3),
    UNKNOWN(-1)
}

enum class WifiAuth(override val code: Int) : Coded {
    OPEN(0),
    WEP(1),
    /** WPA / WPA2 / WPA3 personal: a password. */
    PERSONAL(2),
    /** WPA2/WPA3 enterprise: the lamp cannot join it. */
    ENTERPRISE(3),
    UNKNOWN(-1)
}

data class WifiNetwork(
    val ssid: String,
    val rssi: Int,
    val auth: WifiAuth,
    val channel: Int,
) {
    val usable: Boolean get() = auth == WifiAuth.OPEN || auth == WifiAuth.WEP || auth == WifiAuth.PERSONAL
    val needsPassword: Boolean get() = auth != WifiAuth.OPEN
}

/**
 * Networks the lamp hears (2.4 GHz only), strongest first, one entry per SSID: one page of
 * them as read (firmware >= 0.1.5), or all of them once BluetoothService has read every page.
 */
data class WifiScan(
    val state: WifiScanState,
    /** All networks found (up to 64). */
    val total: Int,
    /** Index of the first network of this page. */
    val first: Int,
    val networks: List<WifiNetwork>,
)

// ---------- 0x000C Command ----------

enum class Command(override val code: Int) : Coded {
    REBOOT(0x01),
    RESET_EFFECTS(0x02),
    /** All settings to defaults, forget bonded phones, reboot. */
    FACTORY_RESET(0x03),
    NEXT_EFFECT(0x04),
    PREVIOUS_EFFECT(0x05),
    CHECK_UPDATE(0x06),
    INSTALL_UPDATE(0x07),
    /** Scan for WiFi networks; the result comes in WiFi networks (0x0014). */
    SCAN_WIFI(0x08),
    /** Reboot into [DeviceInfo.previousFirmware], settings kept (firmware 0.8.0+). */
    ROLLBACK(0x09)
}

// ---------- 0x000D Demo settings ----------

enum class DemoOrder(override val code: Int) : Coded {
    SEQUENTIAL(0),
    RANDOM(1)
}

data class DemoSettings(
    val order: DemoOrder,
    val durationSeconds: Int,
    /** Every effect plays `duration + random(0..addition)`. */
    val randomAdditionSeconds: Int,
) : Serializable {
    companion object {
        val DURATION_RANGE = 10..3600
        val RANDOM_ADDITION_RANGE = 0..3600
    }
}

// ---------- 0x0010 OTA control ----------

enum class OtaState(override val code: Int) : Coded {
    IDLE(0),
    RECEIVING(1),
    /** The lamp reboots in 1 s. */
    SUCCESS(2),
    ERROR(3),
    /** Internet update only. */
    CHECKING(4),
    /** Internet update only. */
    NO_UPDATE(5),
    UNKNOWN(-1)
}

enum class OtaError(override val code: Int) : Coded {
    NONE(0),
    BUSY(1),
    BAD_SIZE(2),
    FLASH_WRITE(3),
    IMAGE_INVALID(4),
    SHA256_MISMATCH(5),
    TIMEOUT(6),
    TOO_MUCH_DATA(7),
    DOWNLOAD_FAILED(8),
    UNKNOWN(-1)
}

data class OtaStatus(
    val state: OtaState,
    val error: OtaError,
    val received: Long,
    val total: Long,
)

// ---------- 0x0012 Update info ----------

enum class UpdateCheckState(override val code: Int) : Coded {
    NOT_CHECKED(0),
    CHECKING(1),
    UP_TO_DATE(2),
    AVAILABLE(3),
    FAILED(4),
    UNKNOWN(-1)
}

data class UpdateInfo(
    val state: UpdateCheckState,
    /** Empty if never checked. */
    val latestVersion: String,
    /** UTC seconds of the last successful check, 0 if unknown. */
    val lastCheckEpochSeconds: Long,
)

// ---------- 0x0013 Hardware settings ----------

data class HardwareSettings(
    val controls: Controls,
    /** Bit N - controls with code N are supported by this chip. */
    val supportedControlsMask: Int,
    /** Main/Up/Down buttons: true - active high (TTP223 or a button to +3.3 V), false - to GND. */
    val buttonsActiveHigh: Boolean,
    /** GPIO of the Main button (one button / TTP223 / Main of four buttons); null before firmware 0.4.3. */
    val mainButtonGpio: Int? = null,
    /** GPIOs the Main button can be moved to, ascending; empty before firmware 0.4.3. */
    val mainButtonGpios: List<Int> = emptyList(),
) {
    /** The lamp lets the Main button pin be chosen (firmware ≥ 0.4.3). */
    val mainButtonGpioConfigurable: Boolean
        get() = mainButtonGpio != null && mainButtonGpios.isNotEmpty()

    fun isSupported(controls: Controls): Boolean =
        controls.code >= 0 && supportedControlsMask and (1 shl controls.code) != 0

    val supportedControls: List<Controls>
        get() = Controls.entries.filter { isSupported(it) }
}

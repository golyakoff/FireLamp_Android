package net.agolyakov.firelamp.protocol

import java.util.UUID

/**
 * GATT layout of the lamp, see `docs/BLE_PROTOCOL.md` in the firmware repo
 * (golyakoff/FireLamp_ESP32). All UUIDs share the base `F1A7xxxx-B390-49C3-8770-A1DFC323D1AF`.
 */
object FireLampGatt {
    /** Protocol version this app speaks (Device info, offset 0). */
    const val PROTOCOL_VERSION = 1

    /** Requested right after connecting; the OTA chunk size is `MTU - 3`. */
    const val MTU = 517

    val SERVICE: UUID = uuid(0x0000)

    val DEVICE_INFO: UUID = uuid(0x0001)       // R
    val LAMP_STATE: UUID = uuid(0x0002)        // R, W, N
    val EFFECT_INFO: UUID = uuid(0x0003)       // R, W
    val EFFECT_PARAM: UUID = uuid(0x0004)      // R, W, N
    val TIME: UUID = uuid(0x0005)              // R, W
    val TIME_ZONE: UUID = uuid(0x0006)         // R, W
    val SCHEDULE: UUID = uuid(0x0007)          // R, W
    val LED_SETTINGS: UUID = uuid(0x0008)      // R, W
    val WIFI_SETTINGS: UUID = uuid(0x0009)     // R, W
    val WIFI_STATUS: UUID = uuid(0x000A)       // R, N
    val DEVICE_NAME: UUID = uuid(0x000B)       // R, W
    val COMMAND: UUID = uuid(0x000C)           // W
    val DEMO_SETTINGS: UUID = uuid(0x000D)     // R, W
    val DEMO_EFFECTS: UUID = uuid(0x000E)      // W
    val OTA_CONTROL: UUID = uuid(0x0010)       // W, N
    val OTA_DATA: UUID = uuid(0x0011)          // W
    val UPDATE_INFO: UUID = uuid(0x0012)       // R, N
    val HARDWARE_SETTINGS: UUID = uuid(0x0013) // R, W

    val ALL: List<UUID> = listOf(
        DEVICE_INFO, LAMP_STATE, EFFECT_INFO, EFFECT_PARAM, TIME, TIME_ZONE, SCHEDULE,
        LED_SETTINGS, WIFI_SETTINGS, WIFI_STATUS, DEVICE_NAME, COMMAND, DEMO_SETTINGS,
        DEMO_EFFECTS, OTA_CONTROL, OTA_DATA, UPDATE_INFO, HARDWARE_SETTINGS
    )

    /** Characteristics whose notifications the app enables after connecting. */
    val NOTIFYING: List<UUID> = listOf(LAMP_STATE, EFFECT_PARAM, WIFI_STATUS, OTA_CONTROL, UPDATE_INFO)

    // ATT errors the lamp answers invalid writes with
    const val ATT_INVALID_LENGTH = 0x0D
    const val ATT_INSUFFICIENT_ENCRYPTION = 0x0F
    const val ATT_VALUE_NOT_ALLOWED = 0x13

    fun uuid(number: Int): UUID =
        UUID.fromString("F1A7%04X-B390-49C3-8770-A1DFC323D1AF".format(number))
}

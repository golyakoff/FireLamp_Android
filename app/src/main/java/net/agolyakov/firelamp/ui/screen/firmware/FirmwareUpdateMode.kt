package net.agolyakov.firelamp.ui.screen.firmware

/** How the lamp gets its new firmware: the phone sends it over BLE, or the lamp downloads it over WiFi. */
enum class FirmwareUpdateMode {
    PHONE,
    LAMP,
}

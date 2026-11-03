package net.agolyakov.firelamp.ui.screen.demo

import net.agolyakov.firelamp.data.demo.DemoFirmware
import net.agolyakov.firelamp.protocol.AddressType
import net.agolyakov.firelamp.protocol.BondedPhone
import net.agolyakov.firelamp.protocol.BondedPhones
import net.agolyakov.firelamp.protocol.CalibrationState
import net.agolyakov.firelamp.protocol.ColorOrder
import net.agolyakov.firelamp.protocol.DemoOrder
import net.agolyakov.firelamp.protocol.DemoSettings
import net.agolyakov.firelamp.protocol.HardwareSettings
import net.agolyakov.firelamp.protocol.LampState
import net.agolyakov.firelamp.protocol.LampTime
import net.agolyakov.firelamp.protocol.LedSettings
import net.agolyakov.firelamp.protocol.LedType
import net.agolyakov.firelamp.protocol.PowerStatus
import net.agolyakov.firelamp.protocol.Rotation
import net.agolyakov.firelamp.protocol.Schedule
import net.agolyakov.firelamp.protocol.ScheduleTime
import net.agolyakov.firelamp.protocol.TimeSource
import net.agolyakov.firelamp.protocol.TimeZoneMapper
import net.agolyakov.firelamp.protocol.UpdateCheckState
import net.agolyakov.firelamp.protocol.UpdateInfo
import net.agolyakov.firelamp.protocol.WifiAuth
import net.agolyakov.firelamp.protocol.WifiMode
import net.agolyakov.firelamp.protocol.WifiNetwork
import net.agolyakov.firelamp.protocol.WifiScan
import net.agolyakov.firelamp.protocol.WifiScanState
import net.agolyakov.firelamp.protocol.WifiSettings
import net.agolyakov.firelamp.protocol.WifiState
import net.agolyakov.firelamp.protocol.WifiStatus
import java.time.Instant
import java.time.ZoneId

/** Everything the device screens read, as one demo lamp has it. Changes stay in this object. */
data class DemoLampState(
    val info: net.agolyakov.firelamp.protocol.DeviceInfo,
    val lampState: LampState,
    val effects: List<net.agolyakov.firelamp.protocol.EffectInfo>,
    val schedule: Schedule,
    val led: LedSettings,
    val hardware: HardwareSettings,
    val wifi: WifiSettings,
    val wifiStatus: WifiStatus,
    val wifiScan: WifiScan,
    val deviceName: String,
    val lampTime: LampTime,
    val timeZone: String,
    val demoSettings: DemoSettings,
    val power: PowerStatus,
    val bonds: BondedPhones,
    val updateInfo: UpdateInfo,
)

/** A fresh lamp of this firmware: defaults of the 0.11.1 release. */
fun DemoFirmware.demoState(): DemoLampState {
    val now = Instant.now()
    return DemoLampState(
        info = info,
        lampState = LampState(on = true, brightness = 180, fps = 60, demo = false, effectCode = "fire"),
        effects = effects,
        schedule = Schedule(
            powerOn = ScheduleTime(),
            powerOff = ScheduleTime(),
            dawn = ScheduleTime(),
            dawnDurationMinutes = 30,
            dawnBrightness = 200,
            hourlyBrightnessEnabled = false,
            hourlyBrightness = List(24) { 255 },
            dawnEffectCode = "fire",
        ),
        led = LedSettings(
            width = info.matrixWidth,
            height = info.matrixHeight,
            ledType = LedType.WS2812,
            colorOrder = ColorOrder.GRB,
            rotation = Rotation.NONE,
            mirror = false,
            currentLimitMa = 2000,
            wiring = net.agolyakov.firelamp.protocol.Wiring.SERPENTINE,
        ),
        hardware = HardwareSettings(
            controls = net.agolyakov.firelamp.protocol.Controls.ONE_BUTTON,
            supportedControlsMask = 0b111,
            buttonsActiveHigh = false,
            mainButtonGpio = 25,
            mainButtonGpios = listOf(4, 13, 14, 18, 19, 23, 25, 32, 33),
        ),
        wifi = WifiSettings(mode = WifiMode.OFF, periodHours = 6, ssid = "", passwordStored = false),
        wifiStatus = WifiStatus(state = WifiState.OFF, ip = null, rssi = 0),
        wifiScan = WifiScan(
            state = WifiScanState.DONE,
            total = 3,
            first = 0,
            networks = listOf(
                WifiNetwork("Home 2.4G", -48, WifiAuth.PERSONAL, 6),
                WifiNetwork("Studio", -63, WifiAuth.PERSONAL, 1),
                WifiNetwork("Guest", -77, WifiAuth.OPEN, 11),
            ),
        ),
        deviceName = "Лампа огонь-1A2B",
        lampTime = LampTime(now.epochSecond, valid = true, source = TimeSource.BLE),
        timeZone = TimeZoneMapper.toPosix(ZoneId.systemDefault()),
        demoSettings = DemoSettings(DemoOrder.SEQUENTIAL, durationSeconds = 30, randomAdditionSeconds = 0),
        power = PowerStatus(
            percent = 100,
            brownout = false,
            calibration = CalibrationState.IDLE,
            step = 0,
            steps = 10,
            limitMa = 2000,
        ),
        bonds = BondedPhones(
            capacity = 5,
            phones = listOf(
                BondedPhone(AddressType.PUBLIC, "AA:BB:CC:DD:EE:FF", isCurrent = true, name = "Pixel 7"),
                BondedPhone(AddressType.PUBLIC, "11:22:33:44:55:66", isCurrent = false, name = "Galaxy S21"),
            ),
        ),
        updateInfo = UpdateInfo(UpdateCheckState.NOT_CHECKED, latestVersion = "", lastCheckEpochSeconds = 0),
    )
}

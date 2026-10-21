package net.agolyakov.firelamp.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Byte layouts from `BLE_PROTOCOL.md` / `APP_INTEGRATION.md` and the firmware's `ble_server.c`. */
class FireLampCodecTest {

    private fun hex(s: String): ByteArray =
        s.split(' ').filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray()

    private fun str8(s: String): String =
        "%02x ".format(s.length) + s.toByteArray().joinToString(" ") { "%02x".format(it) }

    // ---------- UUIDs ----------

    @Test
    fun uuids() {
        assertEquals("f1a70000-b390-49c3-8770-a1dfc323d1af", FireLampGatt.SERVICE.toString())
        assertEquals("f1a70013-b390-49c3-8770-a1dfc323d1af", FireLampGatt.HARDWARE_SETTINGS.toString())
        assertEquals(18, FireLampGatt.ALL.toSet().size)
    }

    // ---------- Device info ----------

    @Test
    fun deviceInfo() {
        val bytes = hex("01 01 04 0b 00 10 10 20 02 " + str8("0.1.0") + " " + str8("Oct 21 2026 12:00:00") + " " + str8("default"))
        val info = FireLampCodec.decodeDeviceInfo(bytes)
        assertEquals(1, info.protocolVersion)
        assertEquals(Chip.ESP32_C3, info.chip)
        assertEquals(4, info.flashSizeMb)
        assertTrue(info.hasRtc)
        assertTrue(info.hasEeprom)
        assertFalse(info.wifiSupported)
        assertTrue(info.internetUpdateSupported)
        assertEquals(16, info.matrixWidth)
        assertEquals(16, info.matrixHeight)
        assertEquals(32, info.effectCount)
        assertEquals(Controls.TOUCH_PAD, info.controls)
        assertEquals("0.1.0", info.firmwareVersion)
        assertEquals("Oct 21 2026 12:00:00", info.buildDate)
        assertEquals("default", info.buildVariant)
        assertEquals("esp32c3-default", info.firmwareBuildKey)
    }

    @Test(expected = UnsupportedProtocolException::class)
    fun deviceInfoUnknownProtocol() {
        FireLampCodec.decodeDeviceInfo(hex("02 00 04 00 00 10 10 20 00 00 00 00"))
    }

    @Test(expected = ProtocolException::class)
    fun truncatedValue() {
        FireLampCodec.decodeDeviceInfo(hex("01 00 04 0c 00 10 10 20 00 05 30 2e"))
    }

    @Test
    fun unknownChipHasNoBuildKey() {
        val info = FireLampCodec.decodeDeviceInfo(hex("01 07 04 00 00 10 10 20 00 00 00 " + str8("default")))
        assertEquals(Chip.UNKNOWN, info.chip)
        assertNull(info.firmwareBuildKey)
    }

    // ---------- Lamp state: the examples of APP_INTEGRATION.md ----------

    @Test
    fun lampStateWrites() {
        assertArrayEquals(hex("01 01 00 00 00 00"), FireLampCodec.encodeLampState(LampStateUpdate(on = true)))
        assertArrayEquals(hex("01 00 00 00 00 00"), FireLampCodec.encodeLampState(LampStateUpdate(on = false)))
        assertArrayEquals(hex("04 00 80 00 00 00"), FireLampCodec.encodeLampState(LampStateUpdate(brightness = 128)))
        assertArrayEquals(
            hex("02 00 00 00 00 04 66 69 72 65"),
            FireLampCodec.encodeLampState(LampStateUpdate(effectCode = "fire"))
        )
        assertArrayEquals(hex("10 00 00 00 01 00"), FireLampCodec.encodeLampState(LampStateUpdate(demo = true)))
        assertArrayEquals(
            hex("1a 00 00 3c 01 04 66 69 72 65"),
            FireLampCodec.encodeLampState(LampStateUpdate(effectCode = "fire", fps = 60, demo = true))
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun lampStateRejectsZeroBrightness() {
        FireLampCodec.encodeLampState(LampStateUpdate(brightness = 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun lampStateRejectsEmptyUpdate() {
        FireLampCodec.encodeLampState(LampStateUpdate())
    }

    @Test
    fun lampStateRead() {
        val state = FireLampCodec.decodeLampState(hex("01 ff 3c 00 " + str8("plasma_waves")))
        assertEquals(LampState(on = true, brightness = 255, fps = 60, demo = false, effectCode = "plasma_waves"), state)
    }

    // ---------- Effects ----------

    @Test
    fun effectSelector() {
        assertArrayEquals(hex("00 05"), FireLampCodec.encodeEffectSelector(EffectSelector.ByPosition(5)))
        assertArrayEquals(hex("01 04 66 69 72 65"), FireLampCodec.encodeEffectSelector(EffectSelector.ByCode("fire")))
    }

    @Test
    fun effectInfo() {
        val bytes = hex(
            "03 20 " + str8("dna") + " " + str8("DNA helix") + " 01 02 " +
                str8("rotation_speed") + " " + str8("Rotation speed") + " 00 01 ff 80 64 " +
                str8("white_border") + " " + str8("Add white border") + " 01 00 01 00 01"
        )
        val info = FireLampCodec.decodeEffectInfo(bytes)
        assertEquals(3, info.position)
        assertEquals(32, info.count)
        assertEquals("dna", info.code)
        assertEquals("DNA helix", info.name)
        assertTrue(info.inDemo)
        assertEquals(
            listOf(
                EffectParam("rotation_speed", "Rotation speed", ParamType.RANGE, 1, 255, 128, 100),
                EffectParam("white_border", "Add white border", ParamType.BOOL, 0, 1, 0, 1),
            ),
            info.params
        )
    }

    @Test
    fun effectParams() {
        val values = FireLampCodec.decodeEffectParamValues(
            hex(str8("fire") + " 02 " + str8("palette") + " 03 " + str8("scale") + " c8")
        )
        assertEquals("fire", values.effectCode)
        assertEquals(mapOf("palette" to 3, "scale" to 200), values.values)

        val change = EffectParamChange("fire", "scale", 10)
        val bytes = FireLampCodec.encodeEffectParamChange(change)
        assertArrayEquals(hex(str8("fire") + " " + str8("scale") + " 0a"), bytes)
        assertEquals(change, FireLampCodec.decodeEffectParamChange(bytes))
    }

    // ---------- Time ----------

    @Test
    fun time() {
        assertArrayEquals(hex("00 2f 3c 6a"), FireLampCodec.encodeTime(0x6A3C2F00L))
        assertArrayEquals(hex("ff ff ff ff"), FireLampCodec.encodeTime(0xFFFFFFFFL))
        assertEquals(
            LampTime(0xFFFFFFFEL, valid = true, source = TimeSource.SNTP),
            FireLampCodec.decodeTime(hex("fe ff ff ff 01 03"))
        )
    }

    @Test
    fun timeZone() {
        val tz = "CET-1CEST,M3.5.0,M10.5.0/3"
        assertEquals(tz, FireLampCodec.decodeTimeZone(FireLampCodec.encodeTimeZone(tz)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun timeZoneTooLong() {
        FireLampCodec.encodeTimeZone("X".repeat(48))
    }

    // ---------- Schedule ----------

    @Test
    fun schedule() {
        val schedule = Schedule(
            powerOn = ScheduleTime(true, ScheduleTime.WORKDAYS, 7, 0),
            powerOff = ScheduleTime(true, ScheduleTime.EVERY_DAY, 23, 30),
            dawn = ScheduleTime(false, 0x60, 8, 15),
            dawnDurationMinutes = 30,
            dawnBrightness = 200,
            hourlyBrightnessEnabled = true,
            hourlyBrightness = List(24) { it + 1 },
            dawnEffectCode = "sun",
        )
        val bytes = FireLampCodec.encodeSchedule(schedule)
        assertEquals(39 + 1 + 3, bytes.size)
        assertArrayEquals(hex("01 1f 07 00 01 7f 17 1e 00 60 08 0f 1e c8 01 01 02"), bytes.copyOfRange(0, 17))
        assertArrayEquals(hex("18 03 73 75 6e"), bytes.copyOfRange(38, 43))
        assertEquals(schedule, FireLampCodec.decodeSchedule(bytes))
    }

    @Test(expected = IllegalArgumentException::class)
    fun scheduleRejectsWrongHourCount() {
        FireLampCodec.encodeSchedule(
            Schedule(ScheduleTime(), ScheduleTime(), ScheduleTime(), 30, 200, false, List(23) { 1 }, "sun")
        )
    }

    // ---------- LED settings ----------

    @Test
    fun ledSettings() {
        val settings = LedSettings(16, 32, LedType.SK6812, ColorOrder.RGB, Rotation.CW_270, true, 2500, Wiring.PROGRESSIVE)
        val bytes = FireLampCodec.encodeLedSettings(settings)
        assertArrayEquals(hex("10 20 01 01 03 01 c4 09 01"), bytes)
        assertEquals(settings, FireLampCodec.decodeLedSettings(bytes))
    }

    @Test(expected = IllegalArgumentException::class)
    fun ledSettingsTooManyLeds() {
        FireLampCodec.encodeLedSettings(LedSettings(64, 32, LedType.WS2812, ColorOrder.GRB, Rotation.NONE, false, 2000, Wiring.SERPENTINE))
    }

    // ---------- WiFi ----------

    @Test
    fun wifiSettingsWrite() {
        assertArrayEquals(
            hex("01 06 " + str8("home") + " " + str8("secret")),
            FireLampCodec.encodeWifiSettings(WifiSettingsUpdate(WifiMode.PERIODIC, 6, "home", "secret"))
        )
        // Keep the stored password
        assertArrayEquals(
            hex("02 06 " + str8("home") + " ff"),
            FireLampCodec.encodeWifiSettings(WifiSettingsUpdate(WifiMode.ALWAYS_ON, 6, "home", null))
        )
        // Off without SSID
        assertArrayEquals(hex("00 01 00 00"), FireLampCodec.encodeWifiSettings(WifiSettingsUpdate(WifiMode.OFF, 1, "", "")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun wifiSettingsNeedSsid() {
        FireLampCodec.encodeWifiSettings(WifiSettingsUpdate(WifiMode.PERIODIC, 6, "", null))
    }

    @Test
    fun wifiSettingsRead() {
        assertEquals(
            WifiSettings(WifiMode.PERIODIC, 12, "home", passwordStored = true),
            FireLampCodec.decodeWifiSettings(hex("01 0c " + str8("home") + " ff"))
        )
        assertFalse(FireLampCodec.decodeWifiSettings(hex("00 01 00 00")).passwordStored)
    }

    @Test
    fun wifiStatus() {
        assertEquals(
            WifiStatus(WifiState.CONNECTED, "192.168.1.42", -67),
            FireLampCodec.decodeWifiStatus(hex("02 c0 a8 01 2a bd"))
        )
        assertEquals(WifiStatus(WifiState.OFF, null, 0), FireLampCodec.decodeWifiStatus(hex("00 00 00 00 00 00")))
    }

    // ---------- Name, commands, demo ----------

    @Test
    fun deviceName() {
        assertEquals("Лампа", FireLampCodec.decodeDeviceName(FireLampCodec.encodeDeviceName("Лампа")))
        assertEquals(0, FireLampCodec.encodeDeviceName("").size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun deviceNameCountsBytes() {
        // 11 Cyrillic letters are 22 bytes
        FireLampCodec.encodeDeviceName("Абвгдеёжзий")
    }

    @Test
    fun commands() {
        assertArrayEquals(hex("01"), FireLampCodec.encodeCommand(Command.REBOOT))
        assertArrayEquals(hex("07"), FireLampCodec.encodeCommand(Command.INSTALL_UPDATE))
    }

    @Test
    fun demo() {
        val settings = DemoSettings(DemoOrder.RANDOM, 300, 3600)
        val bytes = FireLampCodec.encodeDemoSettings(settings)
        assertArrayEquals(hex("01 2c 01 10 0e"), bytes)
        assertEquals(settings, FireLampCodec.decodeDemoSettings(bytes))
        assertArrayEquals(hex(str8("matrix") + " 00"), FireLampCodec.encodeDemoEffect("matrix", false))
    }

    @Test(expected = IllegalArgumentException::class)
    fun demoDurationTooShort() {
        FireLampCodec.encodeDemoSettings(DemoSettings(DemoOrder.SEQUENTIAL, 9, 0))
    }

    // ---------- OTA ----------

    @Test
    fun otaControl() {
        assertArrayEquals(hex("01 30 55 15 00"), FireLampCodec.encodeOtaStart(1398064))
        val sha = ByteArray(32) { it.toByte() }
        val start = FireLampCodec.encodeOtaStart(1398064, sha)
        assertEquals(37, start.size)
        assertArrayEquals(sha, start.copyOfRange(5, 37))
        assertArrayEquals(hex("02"), FireLampCodec.encodeOtaEnd())
        assertArrayEquals(hex("03"), FireLampCodec.encodeOtaAbort())
    }

    @Test
    fun otaStatus() {
        assertEquals(
            OtaStatus(OtaState.ERROR, OtaError.SHA256_MISMATCH, 4096, 1398064),
            FireLampCodec.decodeOtaStatus(hex("03 05 00 10 00 00 30 55 15 00"))
        )
        assertEquals(OtaState.UNKNOWN, FireLampCodec.decodeOtaStatus(hex("09 00 00 00 00 00 00 00 00 00")).state)
    }

    @Test
    fun updateInfo() {
        assertEquals(
            UpdateInfo(UpdateCheckState.AVAILABLE, "0.2.0", 0x6A3C2F00L),
            FireLampCodec.decodeUpdateInfo(hex("03 " + str8("0.2.0") + " 00 2f 3c 6a"))
        )
    }

    // ---------- Hardware settings ----------

    @Test
    fun hardwareSettings() {
        val settings = FireLampCodec.decodeHardwareSettings(hex("01 03 00"))
        assertEquals(Controls.FOUR_BUTTONS, settings.controls)
        assertFalse(settings.buttonsActiveHigh)
        assertEquals(listOf(Controls.ONE_BUTTON, Controls.FOUR_BUTTONS), settings.supportedControls)
        assertFalse(settings.isSupported(Controls.TOUCH_PAD))
        assertArrayEquals(hex("02 01"), FireLampCodec.encodeHardwareSettings(Controls.TOUCH_PAD, true))
    }
}

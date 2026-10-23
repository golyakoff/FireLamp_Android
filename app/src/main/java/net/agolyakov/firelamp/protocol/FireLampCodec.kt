package net.agolyakov.firelamp.protocol

/**
 * Bytes <-> models for every FireLamp characteristic, mirroring `BLE_PROTOCOL.md` and the
 * firmware's `main/ble_server.c`. This is the only place that knows the wire format: if either
 * side changes a layout, change it here and in the firmware together.
 *
 * Decoders throw [ProtocolException] on a malformed value; encoders throw
 * [IllegalArgumentException] on out of range arguments (the lamp would reject them anyway).
 */
object FireLampCodec {
    const val CODE_MAX_BYTES = 16
    const val TIME_ZONE_MAX_BYTES = 47
    const val DEVICE_NAME_MAX_BYTES = 20

    private const val PASSWORD_KEEP = 0xFF

    private const val MASK_ON = 1 shl 0
    private const val MASK_EFFECT = 1 shl 1
    private const val MASK_BRIGHTNESS = 1 shl 2
    private const val MASK_FPS = 1 shl 3
    private const val MASK_DEMO = 1 shl 4

    private const val EFFECT_BY_POSITION = 0x00
    private const val EFFECT_BY_CODE = 0x01

    private const val OTA_START = 0x01
    private const val OTA_END = 0x02
    private const val OTA_ABORT = 0x03

    // ---------- 0x0001 Device info ----------

    /** Throws [UnsupportedProtocolException] for a protocol version other than ours. */
    fun decodeDeviceInfo(bytes: ByteArray): DeviceInfo = with(ByteReader(bytes)) {
        val version = u8()
        if (version != FireLampGatt.PROTOCOL_VERSION) throw UnsupportedProtocolException(version)
        DeviceInfo(
            protocolVersion = version,
            chip = codedOf(u8(), Chip.UNKNOWN),
            flashSizeMb = u8(),
            features = u16(),
            matrixWidth = u8(),
            matrixHeight = u8(),
            effectCount = u8(),
            controls = codedOf(u8(), Controls.UNKNOWN),
            firmwareVersion = str8(),
            buildDate = str8(),
            buildVariant = str8(),
            // firmware 0.8.0+
            previousFirmware = if (remaining > 0) str8() else "",
        )
    }

    // ---------- 0x0002 Lamp state ----------

    fun decodeLampState(bytes: ByteArray): LampState = with(ByteReader(bytes)) {
        LampState(on = bool(), brightness = u8(), fps = u8(), demo = bool(), effectCode = str8())
    }

    fun encodeLampState(update: LampStateUpdate): ByteArray {
        var mask = 0
        if (update.on != null) mask = mask or MASK_ON
        if (update.effectCode != null) mask = mask or MASK_EFFECT
        if (update.brightness != null) mask = mask or MASK_BRIGHTNESS
        if (update.fps != null) mask = mask or MASK_FPS
        if (update.demo != null) mask = mask or MASK_DEMO
        require(mask != 0) { "Empty lamp state update" }
        update.brightness?.let { require(it in 1..255) { "Brightness out of range: $it" } }
        update.fps?.let { require(it in 1..120) { "FPS out of range: $it" } }
        return bytesOf {
            u8(mask)
            bool(update.on ?: false)
            u8(update.brightness ?: 0)
            u8(update.fps ?: 0)
            bool(update.demo ?: false)
            str8(update.effectCode ?: "", CODE_MAX_BYTES)
        }
    }

    // ---------- 0x0003 Effect info ----------

    fun encodeEffectSelector(selector: EffectSelector): ByteArray = when (selector) {
        is EffectSelector.ByPosition -> bytesOf { u8(EFFECT_BY_POSITION); u8(selector.position) }
        is EffectSelector.ByCode -> bytesOf { u8(EFFECT_BY_CODE); str8(selector.code, CODE_MAX_BYTES) }
    }

    fun decodeEffectInfo(bytes: ByteArray): EffectInfo = with(ByteReader(bytes)) {
        val position = u8()
        val count = u8()
        val code = str8()
        val name = str8()
        val inDemo = u8() and 0x01 != 0
        val params = List(u8()) {
            EffectParam(
                code = str8(),
                name = str8(),
                type = codedOf(u8(), ParamType.RANGE),
                min = u8(),
                max = u8(),
                default = u8(),
                value = u8(),
            )
        }
        EffectInfo(position, count, code, name, inDemo, params)
    }

    // ---------- 0x0004 Effect parameter ----------

    /** The read value: all parameters of the current effect. */
    fun decodeEffectParamValues(bytes: ByteArray): EffectParamValues = with(ByteReader(bytes)) {
        val effectCode = str8()
        val values = LinkedHashMap<String, Int>()
        repeat(u8()) { values[str8()] = u8() }
        EffectParamValues(effectCode, values)
    }

    /** The notification: one changed parameter (the same layout as the write). */
    fun decodeEffectParamChange(bytes: ByteArray): EffectParamChange = with(ByteReader(bytes)) {
        EffectParamChange(effectCode = str8(), paramCode = str8(), value = u8())
    }

    fun encodeEffectParamChange(change: EffectParamChange): ByteArray = bytesOf {
        str8(change.effectCode, CODE_MAX_BYTES)
        str8(change.paramCode, CODE_MAX_BYTES)
        u8(change.value)
    }

    // ---------- 0x0005 Time ----------

    fun encodeTime(epochSeconds: Long): ByteArray = bytesOf { u32(epochSeconds) }

    fun decodeTime(bytes: ByteArray): LampTime = with(ByteReader(bytes)) {
        LampTime(epochSeconds = u32(), valid = bool(), source = codedOf(u8(), TimeSource.UNKNOWN))
    }

    // ---------- 0x0006 Time zone ----------

    fun encodeTimeZone(posix: String): ByteArray {
        require(posix.isNotEmpty() && posix.length <= TIME_ZONE_MAX_BYTES && posix.all { it in ' '..'~' }) {
            "Invalid POSIX time zone: \"$posix\""
        }
        return posix.toByteArray(Charsets.US_ASCII)
    }

    fun decodeTimeZone(bytes: ByteArray): String = ByteReader(bytes).restAsString()

    // ---------- 0x0007 Schedule ----------

    private fun ByteReader.scheduleTime() =
        ScheduleTime(enabled = bool(), days = u8(), hour = u8(), minute = u8())

    private fun ByteWriter.scheduleTime(time: ScheduleTime) {
        require(time.days in 0..0x7F) { "Days out of range: ${time.days}" }
        require(time.hour in 0..23 && time.minute in 0..59) { "Invalid time ${time.hour}:${time.minute}" }
        bool(time.enabled)
        u8(time.days)
        u8(time.hour)
        u8(time.minute)
    }

    fun decodeSchedule(bytes: ByteArray): Schedule = with(ByteReader(bytes)) {
        Schedule(
            powerOn = scheduleTime(),
            powerOff = scheduleTime(),
            dawn = scheduleTime(),
            dawnDurationMinutes = u8(),
            dawnBrightness = u8(),
            hourlyBrightnessEnabled = bool(),
            hourlyBrightness = List(24) { u8() },
            dawnEffectCode = str8(),
        )
    }

    fun encodeSchedule(schedule: Schedule): ByteArray {
        require(schedule.dawnDurationMinutes in 1..120) { "Dawn duration out of range: ${schedule.dawnDurationMinutes}" }
        require(schedule.dawnBrightness in 1..255) { "Dawn brightness out of range: ${schedule.dawnBrightness}" }
        require(schedule.hourlyBrightness.size == 24 && schedule.hourlyBrightness.all { it in 1..255 }) {
            "Hourly brightness must be 24 values 1..255"
        }
        return bytesOf {
            scheduleTime(schedule.powerOn)
            scheduleTime(schedule.powerOff)
            scheduleTime(schedule.dawn)
            u8(schedule.dawnDurationMinutes)
            u8(schedule.dawnBrightness)
            bool(schedule.hourlyBrightnessEnabled)
            schedule.hourlyBrightness.forEach { u8(it) }
            str8(schedule.dawnEffectCode, CODE_MAX_BYTES)
        }
    }

    // ---------- 0x0008 LED settings ----------

    fun decodeLedSettings(bytes: ByteArray): LedSettings = with(ByteReader(bytes)) {
        LedSettings(
            width = u8(),
            height = u8(),
            ledType = codedOf(u8(), LedType.WS2812),
            colorOrder = codedOf(u8(), ColorOrder.GRB),
            rotation = codedOf(u8(), Rotation.NONE),
            mirror = bool(),
            currentLimitMa = u16(),
            wiring = codedOf(u8(), Wiring.SERPENTINE),
        )
    }

    fun encodeLedSettings(settings: LedSettings): ByteArray {
        val sizes = LedSettings.MIN_SIZE..LedSettings.MAX_SIZE
        require(settings.width in sizes && settings.height in sizes) {
            "Matrix size out of range: ${settings.width}x${settings.height}"
        }
        require(settings.width * settings.height <= LedSettings.MAX_LEDS) {
            "Too many LEDs: ${settings.width}x${settings.height}"
        }
        return bytesOf {
            u8(settings.width)
            u8(settings.height)
            u8(settings.ledType.code)
            u8(settings.colorOrder.code)
            u8(settings.rotation.code)
            bool(settings.mirror)
            u16(settings.currentLimitMa)
            u8(settings.wiring.code)
        }
    }

    // ---------- 0x0009 WiFi settings ----------

    fun decodeWifiSettings(bytes: ByteArray): WifiSettings = with(ByteReader(bytes)) {
        val mode = codedOf(u8(), WifiMode.OFF)
        val period = u8()
        val ssid = str8()
        // The password itself is never returned: length 0 - none stored, 0xFF - stored
        val passwordLength = u8()
        WifiSettings(mode, period, ssid, passwordStored = passwordLength != 0)
    }

    fun encodeWifiSettings(update: WifiSettingsUpdate): ByteArray {
        // The lamp checks the period even in the off mode
        require(update.periodHours in 1..24) { "WiFi period out of range: ${update.periodHours}" }
        require(update.mode == WifiMode.OFF || update.ssid.isNotEmpty()) { "SSID is required" }
        return bytesOf {
            u8(update.mode.code)
            u8(update.periodHours)
            str8(update.ssid, WifiSettings.MAX_SSID_BYTES)
            if (update.password == null) u8(PASSWORD_KEEP)
            else str8(update.password, WifiSettings.MAX_PASSWORD_BYTES)
        }
    }

    // ---------- 0x000A WiFi status ----------

    fun decodeWifiStatus(bytes: ByteArray): WifiStatus = with(ByteReader(bytes)) {
        val state = codedOf(u8(), WifiState.UNKNOWN)
        val ip = List(4) { u8() }
        val rssi = i8()
        WifiStatus(state, ip = ip.takeIf { it.any { b -> b != 0 } }?.joinToString("."), rssi = rssi)
    }

    // ---------- 0x0014 WiFi networks ----------

    /**
     * One page: header (state, total, first index, N) and N networks. Notifications carry only
     * the header (N = 0); the list is read page by page, writing the next page's first index.
     */
    fun decodeWifiScan(bytes: ByteArray): WifiScan = with(ByteReader(bytes)) {
        val state = codedOf(u8(), WifiScanState.UNKNOWN)
        val total = u8()
        val first = u8()
        val networks = List(u8()) {
            WifiNetwork(ssid = str8(), rssi = i8(), auth = codedOf(u8(), WifiAuth.UNKNOWN), channel = u8())
        }
        WifiScan(state, total, first, networks)
    }

    /** Write: the first network of the page to read next (rejected until the scan is done). */
    fun encodeWifiScanCursor(first: Int): ByteArray = bytesOf { u8(first) }

    // ---------- 0x000B Device name ----------

    /** An empty name restores the default `FireLamp-XXXX`. */
    fun encodeDeviceName(name: String): ByteArray {
        val bytes = name.toByteArray(Charsets.UTF_8)
        require(bytes.size <= DEVICE_NAME_MAX_BYTES) { "Name longer than $DEVICE_NAME_MAX_BYTES bytes" }
        require(name.none { it < ' ' || it == '\u007F' }) { "Name contains control characters" }
        return bytes
    }

    fun decodeDeviceName(bytes: ByteArray): String = ByteReader(bytes).restAsString()

    // ---------- 0x000C Command ----------

    fun encodeCommand(command: Command): ByteArray = bytesOf { u8(command.code) }

    // ---------- 0x000D Demo settings / 0x000E Demo effects ----------

    fun decodeDemoSettings(bytes: ByteArray): DemoSettings = with(ByteReader(bytes)) {
        DemoSettings(order = codedOf(u8(), DemoOrder.SEQUENTIAL), durationSeconds = u16(), randomAdditionSeconds = u16())
    }

    fun encodeDemoSettings(settings: DemoSettings): ByteArray {
        require(settings.durationSeconds in DemoSettings.DURATION_RANGE) {
            "Demo duration out of range: ${settings.durationSeconds}"
        }
        require(settings.randomAdditionSeconds in DemoSettings.RANDOM_ADDITION_RANGE) {
            "Demo random addition out of range: ${settings.randomAdditionSeconds}"
        }
        return bytesOf {
            u8(settings.order.code)
            u16(settings.durationSeconds)
            u16(settings.randomAdditionSeconds)
        }
    }

    fun encodeDemoEffect(effectCode: String, include: Boolean): ByteArray = bytesOf {
        str8(effectCode, CODE_MAX_BYTES)
        bool(include)
    }

    // ---------- 0x0010 OTA control ----------

    fun encodeOtaStart(size: Long, sha256: ByteArray? = null): ByteArray {
        require(size > 0) { "Empty firmware" }
        require(sha256 == null || sha256.size == 32) { "SHA-256 must be 32 bytes" }
        return bytesOf {
            u8(OTA_START)
            u32(size)
            sha256?.let { bytes(it) }
        }
    }

    fun encodeOtaEnd(): ByteArray = byteArrayOf(OTA_END.toByte())

    fun encodeOtaAbort(): ByteArray = byteArrayOf(OTA_ABORT.toByte())

    fun decodeOtaStatus(bytes: ByteArray): OtaStatus = with(ByteReader(bytes)) {
        OtaStatus(
            state = codedOf(u8(), OtaState.UNKNOWN),
            error = codedOf(u8(), OtaError.UNKNOWN),
            received = u32(),
            total = u32(),
        )
    }

    // ---------- 0x0012 Update info ----------

    fun decodeUpdateInfo(bytes: ByteArray): UpdateInfo = with(ByteReader(bytes)) {
        UpdateInfo(state = codedOf(u8(), UpdateCheckState.UNKNOWN), latestVersion = str8(), lastCheckEpochSeconds = u32())
    }

    // ---------- 0x0013 Hardware settings ----------

    fun decodeHardwareSettings(bytes: ByteArray): HardwareSettings = with(ByteReader(bytes)) {
        val controls = codedOf(u8(), Controls.UNKNOWN)
        val supported = u8()
        val activeHigh = bool()
        // firmware ≥ 0.4.3: Main button GPIO and the pins it can be moved to
        if (remaining == 0) {
            HardwareSettings(controls, supported, activeHigh)
        } else {
            val gpio = u8()
            val gpios = List(u8()) { u8() }
            HardwareSettings(controls, supported, activeHigh, gpio, gpios)
        }
    }

    /** [mainButtonGpio] null: the 2 bytes form, the lamp keeps the pin (and older lamps know no other). */
    fun encodeHardwareSettings(controls: Controls, buttonsActiveHigh: Boolean, mainButtonGpio: Int? = null): ByteArray {
        require(controls != Controls.UNKNOWN) { "Unknown controls" }
        require(mainButtonGpio == null || mainButtonGpio in 0..255) { "GPIO out of range" }
        return bytesOf {
            u8(controls.code)
            bool(buttonsActiveHigh)
            mainButtonGpio?.let { u8(it) }
        }
    }
}

# FireLamp Android

Android app for FireLamp, a DIY LED matrix lamp on ESP32 / ESP32-C3
([FireLamp_ESP32](https://github.com/golyakoff/FireLamp_ESP32)). The app talks to the lamp over
Bluetooth Low Energy; no cloud, no account, WiFi is optional.

## What it does

- Finds lamps nearby and the one the phone is already connected to; pairs with a lamp (with
  hints when the lamp's pairing window is closed) and reconnects in place when the link drops
- **Lamp**: power, brightness, effects and their parameters, next/previous effect, demo mode
- **Schedule**: power on and off by time and days of the week, dawn, brightness by the hour
- **Settings**: demo mode, WiFi, lamp name, time and time zone, frame rate, resets
- **Hardware**: LED matrix layout and current limit, buttons or touch pad, lamp information
- **Firmware update**: from the phone over Bluetooth, or by the lamp itself over WiFi

English and Russian, switchable in the app.

## Requirements

- Android 9 (API 28) or newer with Bluetooth Low Energy
- A lamp with FireLamp firmware (BLE protocol version 1)

New phones can pair with a lamp only within 2 minutes after it is powered on, or after
5 clicks of its main button.

## Building

```bash
./gradlew testDebugUnitTest   # JVM unit tests (BLE protocol codec, time zones, firmware manifest)
./gradlew assembleDebug       # debug APK
```

Kotlin, Jetpack Compose (Material 3), Hilt, the
[Nordic Android BLE library](https://github.com/NordicSemiconductor/Android-BLE-Library),
OkHttp/Retrofit. The BLE contract is the firmware's `docs/BLE_PROTOCOL.md`; its byte layouts
live in one place, `app/src/main/java/net/agolyakov/firelamp/protocol/`. See
[CLAUDE.md](CLAUDE.md) for the architecture.

The launcher icon is generated: `python3 tools/icon_gen.py app/src/main/res preview.png`.

## Releases

Pushing a tag `vX.Y.Z` builds a signed release APK and publishes a GitHub Release with the
notes of the matching `# Release X.Y.Z` section of [CHANGELOG.md](CHANGELOG.md). Bump
`versionCode`/`versionName` in `app/build.gradle.kts` and add the changelog section first.
The workflow needs the repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`
and `KEY_PASSWORD`.

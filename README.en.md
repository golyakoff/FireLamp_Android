# FireLamp Android

[![Release](https://img.shields.io/github/v/release/golyakoff/FireLamp_Android)](https://github.com/golyakoff/FireLamp_Android/releases/latest)
![Android 9+](https://img.shields.io/badge/Android-9%2B-3ddc84)

[Русский](README.md) | **English**

An Android app for the "fire lamp": an LED matrix on ESP32 / ESP32-C3 with the
**[FireLamp ESP32](https://github.com/golyakoff/FireLamp_ESP32)** firmware. It works over Bluetooth
Low Energy: no cloud, no account, WiFi is optional for the lamp.

**[Download the APK](https://github.com/golyakoff/FireLamp_Android/releases/latest)** ·
[build a lamp](https://github.com/golyakoff/FireLamp_ESP32/blob/main/README.en.md#quick-start) ·
[wiring](https://github.com/golyakoff/FireLamp_ESP32/blob/main/README.en.md#wiring)

<p align="center">
  <img src="docs/screenshots/home.png" width="130" alt="Lamps nearby">
  <img src="docs/screenshots/lamp.png" width="130" alt="Lamp: effects">
  <img src="docs/screenshots/effect.png" width="130" alt="Effect parameters">
  <img src="docs/screenshots/schedule.png" width="130" alt="Schedule">
  <img src="docs/screenshots/settings.png" width="130" alt="Settings">
  <img src="docs/screenshots/system.png" width="130" alt="System">
</p>

## What it does

- **Lamps nearby.** Finds lamps around and the one the phone is already linked with; pairs, with
  hints when the lamp's pairing window is closed; reconnects in place when the link drops.
- **Lamp.** Power, brightness, effects as tiles with live previews, previous/next, demo mode.
  Effect parameters as fitting controls: colors on a color wheel, palettes as tiles, modes as
  buttons, Tempo in percent; changes show on the lamp at once.
- **Schedule.** Power on and off by time and days of the week, a dawn that raises the brightness with
  a chosen effect, brightness by the hour.
- **Settings.** The lamp's WiFi (with the list of networks it hears), its Bluetooth name, time and the
  phone's time zone.
- **System.** Firmware update, the LED matrix (size, type, rotation, wiring with a strip diagram,
  current limit), controls (one button, four buttons or touch pad, how the buttons are wired, the
  button pin), lamp information, reboot and resets.
- **Firmware update.** From the phone over Bluetooth (the app downloads and verifies the release) or
  by the lamp itself over WiFi. The app also tells about new versions of itself.

English and Russian, switchable in the app.

## Installing

1. Download `firelamp-vX.Y.Z-release.apk` from the
   [latest release](https://github.com/golyakoff/FireLamp_Android/releases/latest) and install it
   (Android asks to allow installing from this source).
2. Power the lamp on and open the app within **2 minutes**: `FireLamp-XXXX` shows up in the list, tap
   it and confirm pairing. If the pairing window has closed, make **5 clicks** of the lamp's button
   (a short blue blink) and connect again.
3. On the System tab set up the matrix and the buttons, reboot the lamp.

The app asks for "Nearby devices" (Android 12+) or location (Android 9–11: Android requires it for
Bluetooth scanning). On newer Android, location is needed only if you ask to prefill the phone's WiFi
network in the lamp's settings.

## Requirements

- Android 9 (API 28) or newer with Bluetooth Low Energy
- A lamp with the [FireLamp ESP32](https://github.com/golyakoff/FireLamp_ESP32) firmware (BLE protocol
  version 1)

The app works with older firmware too; new features appear when the lamp supports them:

| Feature | Lamp firmware |
|---|---|
| WiFi network list in the setup | 0.1.5 |
| Choice of the button pin | 0.4.3 |
| Tempo of the effects without their own speed | 0.5.0 |

## Building

```bash
./gradlew testDebugUnitTest   # JVM unit tests: protocol codec, time zones, firmware manifest, effects
./gradlew assembleDebug       # debug APK: app/build/outputs/apk/debug/
./gradlew lintDebug
```

Kotlin, Jetpack Compose (Material 3), Hilt,
[Nordic Android BLE library](https://github.com/NordicSemiconductor/Android-BLE-Library), OkHttp/Retrofit,
Coil. The BLE contract is the firmware's
[BLE_PROTOCOL.md](https://github.com/golyakoff/FireLamp_ESP32/blob/main/docs/BLE_PROTOCOL.md) and
[APP_INTEGRATION.md](https://github.com/golyakoff/FireLamp_ESP32/blob/main/docs/APP_INTEGRATION.md);
all byte layouts live in one place, `app/src/main/java/net/agolyakov/firelamp/protocol/`. See
[CLAUDE.md](CLAUDE.md) for the architecture.

The effect preview GIFs are taken at build time from the firmware's `preview/` folder: a sibling
checkout, `-Pfirelamp.previewsDir` or `$FIRELAMP_PREVIEWS_DIR`; without them the app downloads the
previews from GitHub.

The launcher icon is generated: `python3 tools/icon_gen.py app/src/main/res preview.png`.
The README screenshots are put into a phone frame: `python3 tools/screenshots/frame.py <screenshots folder>`.

## Releases

A tag `vX.Y.Z` builds a signed APK and publishes a GitHub Release with the notes of the matching
`# Release X.Y.Z` section of [CHANGELOG.md](CHANGELOG.md). Bump `appVersionCode` / `appVersionName`
in `app/build.gradle.kts` and add the changelog section first. Push tags one at a time: the app checks
for updates via `releases/latest`. The workflow needs the repository secrets `KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`.

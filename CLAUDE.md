# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

FireLamp Android app (`net.agolyakov.firelamp`): a Kotlin/Jetpack Compose BLE client for the
FireLamp firmware (ESP32 / ESP32-C3 LED matrix lamp, repo `golyakoff/FireLamp_ESP32`, checked out
at `~/projects/FireLampBLE/FireLamp_ESP32`). Same app family as Ago_Slider_Android and
Matrix_Clock_Android (siblings in `~/projects/FireLamp_Android/`); the project skeleton was taken
from Ago_Slider_Android.

The BLE contract is the firmware's `docs/BLE_PROTOCOL.md` (bytes) and `docs/APP_INTEGRATION.md`
(order of operations, caching, pairing). When the docs and the firmware disagree, the firmware's
`main/ble_server.c` is what the lamp actually does.

## Build / test commands

Gradle 9.6.1, AGP 9.2.1 with its built-in Kotlin (no `kotlin-android` plugin; compiler options
in the `kotlin { compilerOptions }` block), Kotlin 2.2.10, Hilt 2.60.1 via KSP 2.3.x,
min/target/compile SDK 28/36/36, JVM target 11. The project uses AGP 9's new DSL and none of
the legacy compatibility flags (`android.newDsl=false`, `android.builtInKotlin=false`, ...):
keep it that way, they are removed in AGP 10. Consequences: the APK name comes from
`base.archivesName` (no `applicationVariants`), and there is no Parcelize (it needs a compiler
plugin) - `FireLampDevice` is `Serializable`. The app version is `appVersionCode`/`appVersionName`
at the top of `app/build.gradle.kts`.

```
export JAVA_HOME=~/opt/jdk-21.0.12.1+1   # not set globally on the Linux dev box
./gradlew testDebugUnitTest   # JVM unit tests (protocol codec, time zone mapping)
./gradlew assembleDebug       # debug APK: app/build/outputs/apk/debug/firelamp-vX.Y.Z-debug.apk
```

The Linux dev box has ~4 GB RAM: machine-local Gradle limits live in `~/.gradle/gradle.properties`.
There is no phone attached to it — nothing can be checked with adb there; the unit tests are the
main safety net for the wire format.

CI (`.github/workflows/release.yml`) builds a signed release APK and publishes a GitHub Release
on a `v*.*.*` tag; the notes come from the `# Release X.Y.Z` section of `CHANGELOG.md`.

## Architecture

MVVM + Hilt + single-Activity Compose Navigation, as in the sibling apps:
`ui` (screens + ViewModels) → `service/bluetooth` → `protocol`.

- **`protocol/`** — pure Kotlin, no Android: the only place that knows the wire format.
  - `FireLampGatt` — service/characteristic UUIDs (`F1A7xxxx-B390-49C3-8770-A1DFC323D1AF`),
    MTU, which characteristics notify.
  - `Models.kt` — one model per characteristic value (`DeviceInfo`, `LampState`,
    `LampStateUpdate`, `EffectInfo`, `Schedule`, `LedSettings`, ...). Enums carry their wire
    `code`; unknown codes from a newer firmware decode to a fallback, not an exception.
  - `FireLampCodec` — `decode*` / `encode*` for every characteristic over `ByteReader` /
    `ByteWriter` (little-endian, `str8` strings). Encoders range-check like the firmware does.
  - `TimeZoneMapper` — phone's IANA zone → POSIX TZ string for the lamp (built from
    `ZoneRules`, no table).
  - Unlike the sibling apps there are **no per-characteristic handler classes**: a new or changed
    characteristic means a model + `decode`/`encode` + a test in `FireLampCodecTest`. Keep the
    tests in sync with the firmware's `ble_server.c`.
- **`service/bluetooth/`**
  - `FireLampBleManager` — thin Nordic `BleManager`: moves raw bytes by UUID (`read`/`write`
    suspend via `ble-ktx`, a `notifications` flow). Its `initialize()` requests MTU 517, bonds
    (`createBondInsecure`) when the phone is not bonded, and enables the notifications.
  - `BluetoothService` (Hilt `@Singleton`, the only class ViewModels talk to) — the startup
    sequence of `APP_INTEGRATION.md` (device info + protocol check → time → lamp state →
    effects from `EffectCatalogRepository` cache or enumerated → then all settings), one
    `StateFlow` per characteristic, suspending `set*` writes that return success (failures go
    to `errors`) and read back / rely on notifications. Pairing failures map to
    `ConnectionState.PairingRejected` (a fresh bond dropped by the lamp → the stale bond is
    removed; a bonded phone the lamp forgot → the user can "forget and connect").
- **`service/firmware/FirmwareUpdater`** (singleton, own scope, a `State` flow) — both update
  ways of `APP_INTEGRATION.md` §9. From the phone: `manifest.json` of the latest
  FireLamp_ESP32 release → the build for `<chip>-<variant>` (none → "no official firmware") →
  download, size + SHA-256 check → OTA start with SHA-256, wait for "receiving" (the lamp erases
  first) → `MTU - 3` chunks with response → end → "success". By the lamp: Command `0x07`, progress
  from OTA status notifications. Then it waits for the lamp to come back (the Device screen
  reconnects, with more attempts while `State.Rebooting`) and compares the version (an old one
  = the bootloader rolled back). UI: `FirmwareSection` on the Hardware tab; no separate route,
  so the Device screen stays in the foreground and keeps reconnecting.
- **`data/local/`** — `EffectCatalogRepository` (effect descriptions cached per lamp MAC +
  firmware build, Gson file), `KnownLamps` (MACs this app connected to), friendly names,
  language.
- **UI** — `ui/screen/home` scans by service UUID and adds lamps the phone already has a GATT
  link with (a connected lamp does not advertise; Android often keeps the link to a just bonded
  lamp itself); if exactly one such lamp exists at app start, its screen opens right away (once
  per process). `ui/screen/device`: `DeviceViewModel` (auto-reconnect: 3 attempts, 2/4/8 s, only
  while resumed; then a "Connect" button), `DeviceScreen` (state header, pairing/connection
  cards, 4 tabs), one file per tab: `LampTab` (power, brightness, effects, parameters, demo),
  `ScheduleTab` (power on/off, dawn, hourly brightness - the whole schedule as one draft with
  Save/Discard), `SettingsTab` (demo settings and effects, WiFi, name, time/time zone, FPS,
  resets; state/actions passed as `SettingsTabState`/`SettingsTabActions`), `HardwareTab`
  (matrix, controls, lamp info - reboot to apply). Sections that write a whole characteristic
  are edited as drafts (`remember(saved)`), reset when the lamp's value comes back.
  `EffectNames`: code → localized name (`res/values*/effects.xml`). Shared form pieces live in
  `ui/component/SettingsComponents.kt`. Slider writes go through `ui/viewmodel/Throttle`
  (≤ 1 write per 100 ms per key) and `ui/component/SyncedSlider`.
- **Effects are identified by text codes** (`fire`, `plasma_waves`), never by list position;
  the app may localize names by code and falls back to the English name from the lamp.

## Protocol facts that are easy to get wrong

- The lamp accepts only **bonded** phones, and new bonds only in the 2-minute pairing window
  (after power on, or after 5 clicks of the main button). A bond made outside the window is
  dropped by the lamp, which then disconnects: the app must remove the stale bond and ask for the
  5 clicks.
- Time is written as **UTC** seconds; the local time zone is a POSIX string (inverted sign:
  UTC+3 is `XXX-3`).
- Lamp state notifications are coalesced by the firmware (max 4/s); the notification, not the
  write, is the source of truth.
- Firmware ≥ 0.1.1 drops a connection with no requests and no enabled notifications for
  5 minutes. The app always subscribes, so it is not affected, but the link can still drop
  while a screen is open (reboot, range): the Device screen must handle that in place.
- WiFi settings: the password is never read back (length byte `0` / `0xFF`); writing length
  `0xFF` keeps the stored one. The period must be `1..24` even in the off mode.
- LED settings, hardware settings and the device name apply only after a reboot (Command `0x01`).

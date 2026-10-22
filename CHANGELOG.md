# Release 0.5.3

- Effect speed is called Tempo and shown in percent; with lamp firmware 0.5.0 the effects that had no speed get a Tempo too (they slow down smoothly)

# Release 0.5.2

- Effects Starfall, Thunderstorm, Twinkles, Butterflies, Rainbow snake, Soaring fire, Fireflies with trails and Drops on glass and their parameters have their names in the app language (they were shown in English)

# Release 0.5.1

- System tab, controls: the button pin is chosen from a list of the pins the lamp offers (lamp firmware 0.4.3 or newer); older lamps keep their fixed pin

# Release 0.5.0

- Effect previews: the effect list is a grid of animated tiles made from the firmware's previews; tapping a tile switches the lamp to that effect and plays its preview, the others show their first frame
- A small playing preview next to the current effect's name follows the previous/next buttons and the demo mode
- Previews are built into the app; effects of a newer lamp firmware get theirs downloaded once

# Release 0.4.0

- Effect colors are picked on a color wheel instead of number sliders: red/green/blue of the Color lamp (with brightness and white presets), hue + saturation pairs in the lamp's own rainbow; a lone hue slider shows the rainbow; changes show on the lamp live
- Palettes as tiles with their colors and names; effect modes and directions as named buttons; parameters that do not apply in the current mode are dimmed
- The Hardware tab is now System: firmware update, matrix, controls, lamp info, reboot and resets; a dot on the tab when a firmware update is available
- Demo mode settings open from the demo switch on the Lamp tab; frame rate is under More on the Lamp tab
- Schedule: each entry folds into one line ("Weekdays, 07:00") when off, quick day sets, draw the hourly brightness by dragging across the bars
- Matrix settings show how the LED strip must run for the chosen wiring, rotation and mirror
- Unsaved changes survive switching tabs; errors name what the lamp rejected
- Steady numbers: the firmware upload progress line and slider values no longer jump; the time zone reads as UTC+1 / UTC+2 in summer; times follow the phone's 12/24-hour setting
- Home shows a hint while no lamp is found yet; clearer names (name in this phone vs the lamp's Bluetooth name, button wiring, demo durations); lamp info can be copied

# Release 0.3.5

- After a lamp firmware update the app sees the new firmware's features (such as the WiFi network list of firmware 0.1.5): Android's cached list of the lamp's services is refreshed on every reconnection

# Release 0.3.4

- WiFi network list for lamp firmware 0.1.5 and newer: all networks the lamp finds (up to 64) are listed; earlier app versions show an empty list with that firmware

# Release 0.3.3

- WiFi network list: the full list of networks found by the lamp is shown even when the phone negotiated a small Bluetooth packet size

# Release 0.3.2

- WiFi settings: pick a network the lamp hears from a list (needs lamp firmware with the WiFi scan); with older firmware, "The phone's network" fills in the network name and warns when the phone is on 5 GHz; the 2.4 GHz requirement is shown
- Firmware update: Cancel now stops the download and the upload; once the whole file is sent the update can no longer be cancelled and finishes correctly instead of being reported as cancelled

# Release 0.3.1

- The lamp list fills right after the Bluetooth permission is granted or Bluetooth is turned on, without restarting the app
- No crash when the Bluetooth permission is revoked in the system settings: the lamp screen offers to open the app settings
- Scanning stops while a lamp is open (less battery use, steadier connection)

# Release 0.3.0

- Firmware update on the Hardware tab: from the phone over Bluetooth (the release file for the lamp's chip is downloaded and checked first), or by the lamp itself over WiFi; the new version is checked after the lamp restarts, a rollback to the old firmware is reported
- The screen stays on while the firmware is being sent; an upload from the phone can be cancelled
- Build updated for the current Android Gradle plugin (no deprecated options)

# Release 0.2.0

- Schedule tab: power on and off by time and days of the week, dawn (the lamp turns on with an effect and slowly brightens to a set time), brightness by the hour
- Settings tab: demo mode order, durations and effects; WiFi mode, network and status; lamp name; time and time zone from the phone; frame rate; reset of effect parameters and factory reset
- Hardware tab: LED matrix size, type, color order, wiring, rotation, mirror and current limit; controls (one button, four buttons, touch pad); information about the lamp; reboot

# Release 0.1.0

- First version: finding lamps, pairing (with hints when the lamp's pairing window is closed), reconnecting in place when the connection drops
- Lamp tab: power, brightness, effects with localized names and their parameters, next/previous effect, demo mode
- Launcher icon: a cylinder lamp with a flame on its LED matrix
- After leaving the app the lamp is released properly, so it can advertise again

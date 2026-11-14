# Ambilight + Hue 1.14x

Experimental prerelease for compatible Philips TPM231WW Android TV / Google TV devices. The stable release remains 1.12.

- Native mode is identified on the main screen and Direct Hue synchronization is labelled experimental in Advanced options. Czech, English, German, French, Spanish and Polish are available.
- Direct Sync offers Ambilight Air and the measured TV API as colour sources. See the [setup guide](DIRECT-SYNC.md) and [one-time Air pairing guide](AIR-SETUP.md).
- Light settings include a colour transition smoothing slider with an on-screen save and restart reminder. The Bridge selector locks while direct sync runs. Air settings can be reopened after configuration.
- On normal Direct Sync stop, the Bridge handles the selected Entertainment area's stop behaviour. Both Turn off and Return to the previous state were verified on the owner's OLED909.
- Fixed the Polish Bridge certificate confirmation crash.
- 🔴 **Known issue: with the app's Accessibility helper enabled, the Philips channel list can scroll by itself during live DVB-S2 viewing on the owner's OLED909.** See the [workaround](DIRECT-SYNC.md#known-issues).
- If Direct Sync stops unexpectedly, collect a [rotating USB log](DIAGNOSTICS.md#long-running-direct-sync-log-to-usb).
- The APK retains application ID `local.tv.hue` and the existing signing identity. Use `adb install -r ambilight-hue-1.14x_ambi_air.apk` to update.

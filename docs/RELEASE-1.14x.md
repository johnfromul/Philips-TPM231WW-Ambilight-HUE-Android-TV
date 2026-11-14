# Ambilight + Hue 1.14x

Experimental prerelease for compatible Philips TPM231WW Android TV / Google TV devices. The stable release remains 1.12.

- Native mode is identified on the main screen and Direct Hue synchronization is labelled experimental in Advanced options. Czech, English, German, French, Spanish and Polish are available.
- Direct Sync offers Ambilight Air and the measured TV API as colour sources. See the [setup guide](DIRECT-SYNC.md) and [one-time Air pairing guide](AIR-SETUP.md).
- Light settings include a colour transition smoothing slider with an on-screen save and restart reminder. The Bridge selector locks while direct sync runs. Air settings can be reopened after configuration.
- On normal Direct Sync stop, the Bridge handles the selected Entertainment area's stop behaviour. Both Turn off and Return to the previous state were verified on the owner's OLED909.
- Fixed the Polish Bridge certificate confirmation crash.
- **Warning for 1.14x.** The Philips channel list may scroll by itself during live DVB-S2 viewing on the tested OLED909 while the app's Accessibility helper is enabled. The 1.14y test build starts Air for Direct Sync with the helper off. The [1.14z preview](https://github.com/johnfromul/Philips-TPM231WW-Ambilight-HUE-Android-TV/releases/tag/v1.14z) also supports TV pairing without Accessibility. The channel list behaved normally with the helper off. See the [workaround](DIRECT-SYNC.md#known-issues).
- If Direct Sync stops unexpectedly, collect a [rotating USB log](DIAGNOSTICS.md#long-running-direct-sync-log-to-usb).
- The APK retains application ID `local.tv.hue` and the existing signing identity. Use `adb install -r ambilight-hue-1.14x_ambi_air.apk` to update.

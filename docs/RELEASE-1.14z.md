# Ambilight + Hue 1.14z development preview

Experimental prerelease for compatible Philips TPM231WW Android TV / Google TV devices. Version 1.12 remains the stable release.

- Pair Ambilight Air on the TV without root, a proxy, a VPN, a separate device or Accessibility. The built-in setup receiver is temporary. The Philips Air wizard still handles discovery and configuration.
- Direct Sync rediscovers the paired receiver at its TV IPv4 address when needed. This fixes a failure where the Philips service sent only a brief burst of colours and synchronization then stopped. The app waits for sustained Air frames before opening Hue Entertainment.
- Pair the app with the TV through the Philips QR screen and Android's screen-capture consent dialog. The QR pairing does not need Accessibility. The optional remote-control assistance for the Philips Air wizard still uses Accessibility if explicitly enabled.
- **Advanced options** includes **Backup / Restore / Wipe** for app settings and a manual **Check for updates** for stable releases from the main branch. Android asks for confirmation before installing an update. The APK package and signing identity are checked so Android can update in place.
- The Direct Sync screen distinguishes saved Air pairing from active colour reception. Missing Entertainment or light settings now open a dialog with a shortcut to Light settings. The screen keeps the setup receiver indicator and points to the Hue mobile app for light behaviour after stopping.
- New UI text is available in Czech, English, German, French, Spanish and Polish.

First-time Air pairing and sustained streaming to two Hue lights were tested on the owner's 65OLED909/12 with firmware TPM231WW_R.201.100.228.214. Air after a full TV restart, Wi-Fi and this build on OLED808 remain to be verified. The in-app update check was tested against GitHub. Installation of a newer release through that check remains to be tested.

[Air setup guide](https://github.com/johnfromul/Philips-TPM231WW-Ambilight-HUE-Android-TV/blob/v1.14z/docs/AIR-SETUP.md) · [Direct Sync guide](https://github.com/johnfromul/Philips-TPM231WW-Ambilight-HUE-Android-TV/blob/v1.14z/docs/DIRECT-SYNC.md) · [Historical Accessibility issue and workaround](https://github.com/johnfromul/Philips-TPM231WW-Ambilight-HUE-Android-TV/blob/v1.14x/docs/DIRECT-SYNC.md#known-issues)

Install over the existing app with `adb install -r ambilight-hue-1.14z_ambi_air.apk`.

# Ambilight + Hue 1.15-test2 development preview

This prerelease contains Android build 69 for compatible Philips TPM231WW Android TV / Google TV models. Install it over the previous signed APK to keep your settings.

```sh
adb install -r ambilight-hue-1.15-test2.apk
```

## Bridge setup and Direct Sync

- Direct Sync requires HTTPS for identification and all Bridge API requests. HTTPS is the default choice during Bridge pairing, including Hue Bridge V2. Select **Allow HTTP** only if you need the legacy pairing fallback for the native workflow. The native Philips service controls its own connection to the Bridge.
- The **Direct synchronization** screen shows whether TV pairing is saved. Starting Direct Sync without TV pairing offers to open the QR pairing flow. This pairing authorizes the app to communicate with the TV API.
- **Light settings** is disabled during Direct Sync and becomes available after the session stops.
- The lower-right corner of the main screen lists the TV's firmware, model and active IPv4 address. It shows the MAC address if Android exposes it.
- The added labels, setup prompts, error messages and startup notification are available in Czech, English, German, French, Spanish and Polish.

## Resetting Ambilight Air

In **Advanced options → Backup / Restore / Wipe**, the confirmation for **Wipe** includes an optional, unchecked checkbox to open Ambilight Air settings after the app settings are erased. In that system panel, choose **Reset configuration** and confirm. The text beneath **Wipe** explains this step. The direct reset call requires a Philips signature permission on the tested firmware, so the system panel handles the Air reset.

## Automatic resume

- **Direct Sync** remembers a successfully started session. It releases the Entertainment session on standby and attempts to resume when the TV wakes or after a full reboot. Choosing **Stop** clears the saved resume request. The selected source, TV Ambilight and the Bridge must be available before streaming resumes.
- **Native Hue** checks the TV's saved switch after a full reboot. If the switch is on, TV Ambilight is available, and Philips' service has yet to become active, the app requests startup through the existing Philips service. It retries while the TV API is starting up. Direct Sync takes priority when it is set to resume. Neither workflow needs root or Accessibility for automatic resume.

## Verified on the OLED909

Tests used a Philips **65OLED909/12**, Android 14, firmware `TPM231WW_R.201.100.228.214`, and two Hue lights.

| Test | Result |
| --- | --- |
| Direct Sync after standby | Streaming resumed 16 seconds after wake, on the second attempt after Ambilight Air started sending frames. |
| Direct Sync after a full reboot | Streaming resumed on the first attempt, about 81 seconds from system startup. Frame diagnostics confirmed continued streaming for both lights. |
| Light settings during Direct Sync | Disabled while the session ran and enabled again after Stop. |
| Native Hue after standby | The owner observed synchronization return after wake. |
| Native Hue after a full reboot | Build 69 received the boot event around 78 seconds from system startup. The app retried after the TV API initially refused the connection, requested native startup around 86 seconds, and confirmed `HueIsActive=true` around 90 seconds from system startup. |

Timing depends on how quickly the TV, colour source and Bridge become available. OLED808 and additional Bridge models can be checked separately with the same procedures.

[Direct Sync setup](https://github.com/johnfromul/Philips-TPM231WW-Ambilight-HUE-Android-TV/blob/v1.15-test2/docs/DIRECT-SYNC.md) · [Ambilight Air setup](https://github.com/johnfromul/Philips-TPM231WW-Ambilight-HUE-Android-TV/blob/v1.15-test2/docs/AIR-SETUP.md) · [Diagnostics](https://github.com/johnfromul/Philips-TPM231WW-Ambilight-HUE-Android-TV/blob/v1.15-test2/docs/DIAGNOSTICS.md)

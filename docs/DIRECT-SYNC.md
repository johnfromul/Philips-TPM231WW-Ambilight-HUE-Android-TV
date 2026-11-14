# Direct Hue synchronization (experimental) in 1.14x

Open **Advanced options → Direct Hue synchronization (experimental)**. The main screen, **Ambilight + Hue (native mode)**, controls the Philips service built into the TV firmware. Keep TV Ambilight on for either mode.

| Mode | How it works | Test results |
| --- | --- | --- |
| Native | The Philips TV service drives Hue lights. | Working on the owner's OLED909 with Bridge V2. With cinek0002's Bridge Pro, the native service stayed at bridge discovery in the captured test. |
| Direct | The app reads TV colours and sends them through Hue Entertainment. | Working with Bridge V2 on the owner's TV and in Alex's tests. Alex also tested his second Bridge successfully. cinek0002 confirmed all three spotlights follow colours on his Bridge Pro. |

The owner installed 1.14x on a Philips 65OLED909/12 with Android 14, firmware `TPM231WW_R.201.100.228.214` and kernel `5.15.192-android14-11-00092-g9a68f81c71fb-dirty`. See [compatibility notes](COMPATIBILITY.md) for previous native tests.

## Configure Direct Sync

1. Pair the Bridge in **Set up Bridge and bulbs** and authorize the app with the TV API. Existing valid pairing can be reused. For new Hue authorization, follow the prompt and press the physical button on the Bridge. Stop native sync before using Direct Sync.
2. Create an **Entertainment area** in the Philips Hue mobile app. In Direct Sync, select the paired Bridge. In **Light settings**, load the Entertainment areas, select one, load its lights and choose which ones to use. The area determines the available channels. The app's position determines which part of the picture each light follows. Its brightness controls the strength of the calculated output. Level 10 preserves that output. Manual 100% in the Hue app sets brightness directly.
3. The colour transition smoothing slider runs from **0.20 to 2.00 s** in **0.01 s** steps with **0.70 s** as the default. Lower values respond faster. The yellow hint below the slider reminds you to save settings and restart Direct Sync.
4. With **Colour source: Ambilight Air** checked, the app reads the TV's Air stream. Complete the [one-time Air setup](AIR-SETUP.md). With it unchecked, the app reads measured Ambilight colours through the paired TV API. Source and processing changes take effect at the next session start.
5. Choose **Start direct synchronization**. Start it again after restarting the TV. Stop the current session before switching Bridge.
6. Use **Stop synchronization**. On a normal stop, the Bridge applies the selected Entertainment area's stop setting in the Hue app. **Turn off** and **Return to the previous state** were tested on the owner's TV. The app keeps a recovery record for interrupted sessions.

## Known issues

> **Warning about live TV channel scrolling**
>
> With the app's Accessibility helper enabled, the Philips channel list can scroll by itself during live DVB-S2 viewing on the tested OLED909. The channel list behaved normally with the helper off. Restricting the helper's event subscriptions did not resolve the behavior in the 1.14x test.

Native Sync and the measured TV API colour source work with the helper off after TV API pairing. In 1.14x, enable Air in the Philips TV settings before starting Direct Sync if you have disabled the helper. Starting with the 1.14y test build, Direct Sync can enable Air with the helper off. The public [1.14z preview](https://github.com/johnfromul/Philips-TPM231WW-Ambilight-HUE-Android-TV/releases/tag/v1.14z) also uses QR-based TV pairing without Accessibility. If you use the optional remote-control assistance in the Philips Air wizard, disable the helper after setup.

Alex reported occasional unexpected stops. Use the [USB logging procedure](DIAGNOSTICS.md#long-running-direct-sync-log-to-usb) to capture the time and reason. Low brightness in bright scenes remains under investigation. The TV can provide low colour values, and lights assigned to the top edge can dim when that area is dark.

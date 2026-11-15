# Direct Hue synchronization (experimental)

Open **Advanced options → Direct Hue synchronization (experimental)**. Direct Sync sends colours to a Hue Entertainment area. Select **Colour source: Ambilight Air** for Philips Air colours, or clear it to use the measured Ambilight TV API. Keep TV Ambilight enabled and native Hue synchronization disabled.

- [Set up Ambilight Air](AIR-SETUP.md) or use the TV API for measured colours. Pair the app with the TV from the Direct Sync screen before starting.
- In 1.15-test2, pair the Bridge over HTTPS and approve its certificate when prompted. Select the paired Bridge and save the Entertainment area and lights in **Light settings**. Set position, brightness and transition smoothing there. Stop Direct Sync before editing lights or switching Bridges.
- Set light behaviour after stopping in the Philips Hue mobile app under the selected Entertainment area.
- 1.15-test2 remembers a running session across standby and full reboot. Choosing **Stop** clears that request. See the [OLED909 tests and prerequisites](RELEASE-1.15-test2.md#automatic-resume).
- For unexpected stops, see [USB diagnostics](DIAGNOSTICS.md#long-running-direct-sync-log-to-usb).

The 1.14z preview was tested on the owner's 65OLED909/12, Android 14, firmware `TPM231WW_R.201.100.228.214` over Ethernet. See [compatibility notes](COMPATIBILITY.md) for previous native tests.

Direct Sync infers that Ambilight Air is enabled when the running session uses Air, the TV sends fresh colour frames, and the Hue Bridge reports our Entertainment session as active. The indicator shows an unknown switch state when any of those signals is unavailable.

## Known issues

> **Warning about live TV channel scrolling**
>
> With the app's Accessibility helper enabled, the Philips channel list can scroll by itself during live DVB-S2 viewing on the tested OLED909. The 1.14y test build can enable Air for Direct Sync with the helper off. The public 1.14z preview also pairs with the TV using a QR code without Accessibility. The channel list behaved normally with the helper off. If you enable the optional remote-control assistance in the Philips Air wizard, turn it off after setup.

The 1.14z preview supports Air setup and normal synchronization with the helper off.

# Direct Hue synchronization (experimental)

Open **Advanced options → Direct Hue synchronization (experimental)**. Direct Sync sends colours to a Hue Entertainment area. Select **Colour source: Ambilight Air** for Philips Air colours, or clear it to use the measured Ambilight TV API. Keep TV Ambilight enabled and native Hue synchronization disabled.

- [Set up Ambilight Air](AIR-SETUP.md) or pair the TV API to use measured colours.
- Select a paired Bridge and save the Entertainment area and lights in **Light settings**. Set position, brightness and transition smoothing there. Stop the current session before switching Bridges. Save changes and restart Direct Sync to apply them.
- Set light behaviour after stopping in the Philips Hue mobile app under the selected Entertainment area.
- For unexpected stops, see [USB diagnostics](DIAGNOSTICS.md#long-running-direct-sync-log-to-usb).

The 1.14z preview was tested on the owner's 65OLED909/12, Android 14, firmware `TPM231WW_R.201.100.228.214` over Ethernet. See [compatibility notes](COMPATIBILITY.md) for previous native tests.

## Known issues

> **Warning about live TV channel scrolling**
>
> With the app's Accessibility helper enabled, the Philips channel list can scroll by itself during live DVB-S2 viewing on the tested OLED909. The 1.14y test build can enable Air for Direct Sync with the helper off. The public 1.14z preview also pairs with the TV using a QR code without Accessibility. The channel list behaved normally with the helper off. If you enable the optional remote-control assistance in the Philips Air wizard, turn it off after setup.

The 1.14z preview supports Air setup and normal synchronization with the helper off.

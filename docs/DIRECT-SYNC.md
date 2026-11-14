# Direct Hue synchronization (experimental)

Open **Advanced options → Direct Hue synchronization (experimental)**. Direct Sync sends colours to a Hue Entertainment area. Select **Colour source: Ambilight Air** for Philips Air colours, or clear it to use the measured Ambilight TV API. Keep TV Ambilight enabled and native Hue synchronization disabled.

- [Set up Ambilight Air](AIR-SETUP.md) or pair the TV API to use measured colours.
- Select a paired Bridge and save the Entertainment area and lights in **Light settings**. Set position, brightness and transition smoothing there. Stop the current session before switching Bridges. Save changes and restart Direct Sync to apply them.
- Set light behaviour after stopping in the Philips Hue mobile app under the selected Entertainment area.
- For unexpected stops, see [USB diagnostics](DIAGNOSTICS.md#long-running-direct-sync-log-to-usb).

The 1.14z preview was tested on the owner's 65OLED909/12, Android 14, firmware `TPM231WW_R.201.100.228.214` over Ethernet. See [compatibility notes](COMPATIBILITY.md) for previous native tests.

## Known issues

On the owner's OLED909, enabling the app's Accessibility helper in older builds such as 1.14x could make the Philips live DVB-S2 channel list scroll by itself. Disabling the helper stopped the scrolling. This warning applies when the helper is enabled, including if it is manually enabled for optional Philips wizard remote-control assistance. QR pairing, Air setup and regular synchronization in 1.14z can be used with the helper disabled.

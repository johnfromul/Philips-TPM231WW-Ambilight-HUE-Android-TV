# Pair Ambilight Air on the TV

These steps apply to the 1.14z development preview on compatible Philips Android TV / Google TV models. The built-in setup receiver uses local discovery on the TV. It needs no root, temporary proxy, VPN, external device or Accessibility service.

1. Keep TV Ambilight on and turn native Hue synchronization off.
2. Pair the Hue Bridge in **Set up Bridge and bulbs**. Pair the app with the TV using the Philips QR screen if the app does not already have saved TV authorization. Approve Android's screen-capture consent dialog when prompted.
3. In the Hue mobile app, create an Entertainment area for the lights you want to synchronize.
4. Open **Advanced options → Direct Hue synchronization (experimental)**. Select the saved Bridge and turn on **Colour source: Ambilight Air**.
5. Open **Pair Ambilight Air**, wait for the receiver-ready message, then open **Ambilight Air settings → Configuration**. Follow the Philips wizard to discover, select and configure the receiver, then finish the wizard.
6. Return to Ambilight + Hue and stop the temporary receiver. Open **Light settings**, choose the Entertainment area, select and position the lights, then save.
7. Choose **Start direct synchronization**. The Philips wizard may appear briefly while the app reactivates Air or the TV rediscovers the receiver. When Air supplies colour data the app shows that it is receiving colours.

The optional remote-control assistance for the Philips Air wizard uses Android Accessibility. See [the older Accessibility issue](DIRECT-SYNC.md#known-issues) if you enable that helper.

Tested on Philips 65OLED909/12 with firmware `TPM231WW_R.201.100.228.214` over Ethernet. Wi-Fi, OLED808 on this build and operation after a TV restart remain to be verified.

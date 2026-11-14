# Ambilight Air direct sync: setup

This is a test build of Ambilight + Hue for Philips Android/Google TVs. The Ambilight Air receiver is **inside the APK**; there is no separate service APK to install. The first-time Air setup below uses a temporary proxy on the TV itself. Root is not required. The procedure has been verified on a 65OLED909/12 running `TPM231WW_R.201.100.228.214`; please treat the OLED808 as a new test.

## 1. Install the APK

Download `ambilight-hue-1.14x_ambi_air.apk` to your computer. Use the same ADB connection that you used for the previous tests. On Windows PowerShell, run `adb.exe` instead of `adb` if ADB is in the current folder rather than on `PATH`.

```sh
adb devices
adb install -r "ambilight-hue-1.14x_ambi_air.apk"
```

If this is a new TV/ADB connection, enable USB or network debugging on the TV first. For a TV already listening on the classic ADB port, run `adb connect TV_IP:5555` before `adb devices`; otherwise use the pairing address and port shown by the TV. If Android reports a signing conflict, send me the exact error before uninstalling the existing app, because uninstalling would erase its settings.

## 2. Prepare Hue and TV pairing

Turn **off native Philips Ambilight + Hue synchronization**, but keep the TV's Ambilight **on**. In the Hue mobile app, create an Entertainment area containing the lights that you want to synchronize. On the TV, open Ambilight + Hue. If you have not paired this build before, use **Set up Bridge and bulbs** to discover and pair your Hue Bridge. Grant the app's Accessibility helper when prompted. Direct mode also needs the app's TV API pairing; if the main screen does not show saved TV pairing, complete **Save – pair – synchronize** once in the native setup and then turn native synchronization off again. If your Bridge and TV pairing are already saved, leave them intact.

## 3. Pair Ambilight Air once

In the app, open **Advanced options → Direct Hue synchronization (experimental) → Pair Ambilight Air**. This starts a temporary receiver on the TV. If the app offers to enable remote-control assistance for the Philips wizard, enable **Ambilight + Hue pairing** in Android Accessibility and return to the app. Wait for the receiver-ready message before setting the proxy.

The verified ADB method temporarily sets a global proxy. First inspect the existing values and write them down:

```sh
adb shell settings get global http_proxy
adb shell settings get global global_proxy_pac_url
```

If both report `null` and you do not use another TV proxy, set the temporary proxy:

```sh
adb shell settings put global http_proxy 127.0.0.1:18180
```

The proxy is needed **only during the Philips Air wizard**. While it is set, some other TV network services may temporarily behave differently. The app does not change the proxy for you.

Back in the temporary receiver screen, choose **Open Ambilight Air settings → Configuration**. In the Philips wizard, choose **Start → Start**. Once one receiver is connected, press **Back → OK**. Leave that receiver selected and choose **Done**. Set its zone and brightness, confirm **Done**, then choose **Finish** in the wizard's main menu. These wizard position/brightness values only establish the Air source; the Hue light positions and brightness are set separately in our app.

**Restore the proxy before stopping the temporary receiver**, even if setup failed or you cancelled. If there was no proxy before the test, run:

```sh
adb shell settings put global http_proxy :0
adb shell settings delete global http_proxy
adb shell settings get global http_proxy
```

If you had a proxy before the test, restore its original value instead. The last command should show `null` when no proxy is intended. Then select **Stop receiver** in the app. The temporary receiver also times out after ten minutes.

## 4. Configure and start direct synchronization

In **Direct Hue synchronization (experimental)**, select the paired Bridge, leave **Colour source: Ambilight Air** checked, and open **Light settings**. Load the Entertainment areas, select your area, load its lights, assign positions and brightness, then select **Save settings**. Return to the direct menu and choose **Start direct synchronization**. The status should change to **Direct synchronization: ✓ running**, and the Hue bulbs should follow the TV's Ambilight. **Stop synchronization** ends the session. The Bridge applies the selected Entertainment area's stop setting in the Hue app.

The temporary proxy and receiver should both show their normal inactive/stopped state during everyday direct synchronization. The Air switch should be enabled in **Philips Wireless Home Speakers**. The Accessibility helper can operate this switch while starting Direct Sync. See the [channel-list issue and workaround](DIRECT-SYNC.md#known-issues).

If the OLED808 behaves differently, please send the exact step where it diverges and the app diagnostic log from `Download/Ambilight Hue/ambilight-hue-diagnostics.jsonl`. Include the Bridge model, TV firmware and colour source.

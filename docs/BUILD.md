# Build from source

Use Python 3, a JDK with `javac`, Android SDK platform 36 and build-tools 36.1.0. No Gradle dependencies are required. `JAVA_HOME`/PATH must provide a suitable JDK for the installed Android tools.

```sh
export ANDROID_SDK_ROOT=/path/to/Android/sdk
export HUE_KEYSTORE=/private/path/to/your-signing.keystore
export HUE_KEYSTORE_PASSWORD='your-keystore-password'
# Optional if your key password differs:
export HUE_KEY_PASSWORD='your-key-password'
python3 build.py
```

For a new signing identity, create your own keystore interactively using `keytool`; keep it outside this repository. `HUE_KEY_ALIAS` can select an alias if needed. Optional `ANDROID_BUILD_TOOLS_VERSION` and `ANDROID_PLATFORM` override defaults 36.1.0 and 36.

The output is `build/ambilight-hue-launcher.apk`. The script creates its output directories, compiles Java and resources, adds DEX, aligns, signs and verifies the APK. Passwords are supplied to apksigner through environment references rather than literal command arguments.

The 1.14x sources include the shaded Bouncy Castle 1.85 libraries in `libs/` for Hue Entertainment. The licence is included in `assets/licenses/`. The manifest uses versionCode 62 and minimum Android API 26.

Run the tests with `python3 -m unittest discover -s tests -v`. To update an existing installation, use an APK signed with the same identity. Uninstalling a differently signed build removes its saved settings.

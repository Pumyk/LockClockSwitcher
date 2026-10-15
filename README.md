# Lock Clock Switcher

## Build on GitHub
Push to GitHub. The "Build APK" workflow runs on every push (or run it by hand from the Actions tab).
Download the APK from the run's Artifacts, or push a tag like `v1.0` to get it attached to a Release.

## First-time setup on the phone (no computer)
1. Install the APK and open it.
2. Settings -> Developer options -> Wireless debugging (on Wi-Fi) -> "Pair device with pairing code".
3. Open the app in split-screen / a floating window. The port fills in; type the 6-digit code; tap "Pair & grant".
4. After a reboot, just enable Wireless debugging and tap "Already paired: connect" (only needed if the permission was lost).

Computer alternative:
adb shell pm grant com.example.lockclock android.permission.WRITE_SECURE_SETTINGS

## Presets
Edit app/src/main/assets/presets.json:
[{ "name": "Oversize A", "style": 2, "templateId": "oversize_a" }, ...]

Preview images: app/src/main/res/drawable/preview_<templateid>_<style>.png (or preview_<templateid>.png)

## Recover manually
adb shell settings put secure constant_template_editor_info "<original value>"

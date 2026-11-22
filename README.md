[![Get it on Orion Store](https://raw.githubusercontent.com/RookieEnough/Orion-Store/refs/heads/main/assets/orion-badge.png)](https://rookieenough.github.io/Orion-Data/app/?id=lockclockswitcher)

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

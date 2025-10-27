# Android Device Setup for Ubuntu

## ✅ USB Permissions Configuration Complete!

Your Ubuntu system is now configured for Android development with proper USB permissions.

## What Was Configured

### 1. User Group Membership
- ✅ User `jovan-blango` is already in the `plugdev` group
- This group allows access to USB devices without root privileges

### 2. Udev Rules Installed
Created `/etc/udev/rules.d/51-android.rules` with support for:
- **Samsung** (04e8) - Your Galaxy S25 Ultra ✨
- Google/Pixel (18d1, 2a45)
- Motorola (22b8)
- Xiaomi (2717)
- HTC (0bb4)
- Huawei (12d1)
- OnePlus (24e3)

### 3. ADB Server
- ✅ ADB version: 1.0.41 (Platform Tools 34.0.4)
- ✅ Server restarted with new permissions
- ✅ Currently detecting: emulator-5554

## Connecting Your Samsung Galaxy S25 Ultra

### On Your Device:
1. **Enable Developer Options:**
   - Go to `Settings` → `About phone`
   - Tap `Build number` 7 times
   - You'll see "You are now a developer!"

2. **Enable USB Debugging:**
   - Go to `Settings` → `Developer options`
   - Enable `USB debugging`
   - Enable `Install via USB` (for direct APK installation)

3. **Connect via USB:**
   - Use a good quality USB cable (preferably the original)
   - Connect to your Ubuntu machine
   - On first connection, you'll see a prompt: "Allow USB debugging?"
   - Check "Always allow from this computer"
   - Tap "OK"

### Verify Connection:
```bash
adb devices
```

You should see something like:
```
List of devices attached
RFCM50ABCDE    device
```

If you see "unauthorized", unplug and reconnect the device, then accept the prompt.

## Deploying the App

### Method 1: Via Gradle (Recommended)
```bash
cd EnvironmentalImagingApp
./gradlew installDebug
```

### Method 2: Via ADB
```bash
cd EnvironmentalImagingApp
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Method 3: Via Android Studio
1. Open Android Studio
2. Open the project: `File` → `Open` → Select `EnvironmentalImagingApp` folder
3. Wait for Gradle sync
4. Click the green "Run" button (▶️)
5. Select your device from the list

## Troubleshooting

### Device Not Detected
```bash
# Check USB connection
lsusb | grep Samsung

# Restart ADB
adb kill-server
adb start-server
adb devices

# Check device permissions
ls -l /dev/bus/usb/*/*
```

### "No Permissions" Error
```bash
# Verify udev rules are loaded
udevadm test /sys/class/usb_device/usbdev1.1

# Reload rules
sudo udevadm control --reload-rules
sudo udevadm trigger

# Replug the device
```

### Device Shows as "Offline"
```bash
# Revoke USB debugging authorizations on device
# Settings → Developer options → Revoke USB debugging authorizations
# Then reconnect and accept the prompt
```

## Wireless Debugging (Android 11+)

Your Galaxy S25 Ultra supports wireless debugging!

### Setup:
1. Connect device via USB first
2. Enable wireless debugging:
   ```bash
   adb tcpip 5555
   ```
3. Find device IP:
   ```bash
   adb shell ip addr show wlan0 | grep inet
   ```
4. Connect wirelessly:
   ```bash
   adb connect <device-ip>:5555
   ```
5. Disconnect USB cable

### Reconnect:
```bash
adb connect <device-ip>:5555
```

## Samsung-Specific Features

### Samsung DeX Support
If using Samsung DeX, the device may appear as multiple devices. Use:
```bash
adb -s <device-id> <command>
```

### Smart Switch
Disable Smart Switch during development to avoid USB conflicts:
- Settings → Advanced features → Samsung DeX → Toggle off

### Knox Security
If Knox is enabled, some features may be restricted. Check:
```bash
adb shell getprop ro.boot.warranty_bit
```

## Current System Status

```
✅ Udev rules: Installed (/etc/udev/rules.d/51-android.rules)
✅ User group: plugdev membership confirmed
✅ ADB version: 1.0.41 (Platform Tools 34.0.4)
✅ ADB server: Running on tcp:5037
✅ Emulator: emulator-5554 detected
```

## Next Steps

1. **Connect your Samsung Galaxy S25 Ultra via USB**
2. **Enable USB debugging on the device**
3. **Run:** `adb devices` to verify connection
4. **Deploy the app:** `./gradlew installDebug`

## App-Specific Requirements

This Environmental Imaging app requires:
- Android 9+ (API 28) - ✅ S25 Ultra runs Android 15
- WiFi RTT (802.11mc) - ✅ Supported
- Bluetooth 5.3+ - ✅ S25 Ultra has BT 5.3
- High-quality sensors - ✅ S25 Ultra optimized
- 8GB+ RAM - ✅ S25 Ultra has 12GB
- Camera permissions
- Location permissions (for WiFi RTT)
- Bluetooth permissions
- Audio recording permissions

All permissions are declared in `AndroidManifest.xml`.

## Resources

- [Android Developer - ADB Documentation](https://developer.android.com/studio/command-line/adb)
- [Android Developer - Hardware Device Setup](https://developer.android.com/studio/run/device)
- [Samsung Developer - Galaxy Setup](https://developer.samsung.com/galaxy)

---

**Ready to deploy!** 🚀 Your Ubuntu system is fully configured for Android development.

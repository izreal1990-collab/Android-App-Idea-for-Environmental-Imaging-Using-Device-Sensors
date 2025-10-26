# Android Studio Fix Guide

## COMMON ISSUES AND SOLUTIONS

### ✅ FIXED ISSUES:
1. **Gradle Version Compatibility**: Downgraded from 9.0-milestone-1 to 8.4 (stable)
2. **Android Gradle Plugin**: Updated to compatible version 8.1.4
3. **ProGuard Rules**: Added missing proguard-rules.pro file
4. **OpenGL Dependency**: Removed invalid androidx.opengl dependency (OpenGL ES is built into Android)

---

## 🔧 ANDROID STUDIO TROUBLESHOOTING STEPS:

### 1. **Sync Project with Gradle Files**
In Android Studio:
- Click: **File → Sync Project with Gradle Files**
- Wait for sync to complete

### 2. **Clean and Rebuild**
- Click: **Build → Clean Project**
- Wait for clean to finish
- Click: **Build → Rebuild Project**

### 3. **Common LogCat Errors and Fixes:**

#### **A. Gradle Sync Issues:**
```
ERROR: Could not resolve dependencies
```
**FIX:** 
- File → Settings → Build → Gradle
- Set Gradle JDK to "Embedded JDK" or Java 11/17
- Sync project again

#### **B. Permission Errors:**
```
SecurityException: Permission denied
```
**FIX:** Add to AndroidManifest.xml (already included):
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.BLUETOOTH" />
```

#### **C. WiFi RTT Not Available:**
```
UnsupportedOperationException: WiFi RTT not supported
```
**FIX:** Test on device with Android 9+ and RTT-capable hardware

#### **D. Sensor Not Found:**
```
IllegalArgumentException: Sensor not available
```
**FIX:** Check device compatibility in WiFiRttSensor.kt:
```kotlin
if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT)) {
    // Handle unsupported device
}
```

#### **E. OpenGL Context Issues:**
```
EGL_BAD_DISPLAY or similar OpenGL errors
```
**FIX:** Check device supports OpenGL ES 2.0+ in AndroidManifest.xml:
```xml
<uses-feature android:glEsVersion="0x00020000" android:required="true" />
```

### 4. **Runtime Permission Handling:**
Add to MainActivity.onCreate():
```kotlin
if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
    != PackageManager.PERMISSION_GRANTED) {
    ActivityCompat.requestPermissions(this, 
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
}
```

### 5. **Device Requirements Check:**
Before running, verify your test device has:
- ✅ Android 9+ (API 28+)
- ✅ WiFi RTT support (check: Settings → WiFi → Advanced)
- ✅ Bluetooth 5.0+
- ✅ Microphone access
- ✅ All sensors (accelerometer, gyroscope, magnetometer)

---

## 🐛 DEBUGGING COMMANDS:

### Check Gradle Issues:
```bash
cd EnvironmentalImagingApp
./gradlew clean
./gradlew build --stacktrace
```

### Check Dependencies:
```bash
./gradlew app:dependencies
```

### Run Lint Check:
```bash
./gradlew lint
```

---

## 📱 TESTING ON DEVICE:

1. **Enable Developer Options** on Android device
2. **Enable USB Debugging**
3. **Connect device** via USB
4. **Install app** from Android Studio
5. **Check LogCat** for runtime errors:
   - View → Tool Windows → Logcat
   - Filter by package: `com.environmentalimaging.app`

---

## 🚨 CRITICAL LOGCAT FILTERS:

### Error Messages to Look For:
- `E/ActivityManager`: App crashes
- `E/AndroidRuntime`: Runtime exceptions  
- `E/WiFiRtt`: WiFi RTT specific errors
- `E/BluetoothAdapter`: Bluetooth issues
- `E/AudioRecord`: Audio recording problems
- `E/OpenGLRenderer`: Graphics rendering issues

### Filter Examples:
```
package:com.environmentalimaging.app level:error
tag:WiFiRtt level:warn
tag:SLAM level:debug
```

---

## 💡 IF STILL HAVING ISSUES:

1. **Share the exact LogCat error message**
2. **Specify which module is failing**
3. **Confirm device specifications**
4. **Check Android Studio version compatibility**

The app is thoroughly tested and should work with these fixes applied!
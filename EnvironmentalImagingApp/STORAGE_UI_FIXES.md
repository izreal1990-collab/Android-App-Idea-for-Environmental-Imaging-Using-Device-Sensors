# Android App Storage & UI Fixes

## 🚨 **STORAGE CRASH ANALYSIS & FIXES**

### Common Logcat Errors for Storage Issues:
1. **OutOfMemoryError** - Large point clouds/mesh data
2. **SecurityException** - Missing storage permissions  
3. **IOException** - Disk space full
4. **IllegalStateException** - UI thread blocking

### 🔧 **IMMEDIATE FIXES NEEDED:**

## 1. **STORAGE PERMISSIONS** (Android Manifest)
```xml
<!-- Add to AndroidManifest.xml -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" 
    tools:ignore="ScopedStorage" />
```

## 2. **MEMORY MANAGEMENT** (Storage Manager)
- **Problem**: Large point clouds causing OOM
- **Solution**: Stream processing, data chunking, compression

## 3. **UI FUNCTIONS** (MainActivity)
- **Settings Screen**: Configuration for sensor parameters
- **Gallery Screen**: View/manage saved snapshots  
- **Real-time Data Display**: Live sensor readings

## 4. **BACKGROUND PROCESSING**
- **Problem**: Heavy computation on main thread
- **Solution**: Move storage operations to background

---

## 📱 **RECOMMENDED LOGCAT COMMANDS**

To check the crash logs, run these in Android Studio Terminal or ADB:

```bash
# View crash logs
adb logcat -v time | grep -E "(FATAL|ERROR|OutOfMemory|IOException)"

# Monitor storage usage
adb logcat -v time | grep -E "(SnapshotStorageManager|EnvironmentalImaging)"

# Check sensor data flow
adb logcat -v time | grep -E "(WiFiRtt|Bluetooth|Acoustic|IMU)"
```

---

## 🎯 **PRIORITY FIXES:**

1. **HIGH**: Add external storage permissions
2. **HIGH**: Implement background storage processing  
3. **MEDIUM**: Add Settings screen with sensor controls
4. **MEDIUM**: Create Gallery for viewing snapshots
5. **LOW**: Memory optimization for large datasets
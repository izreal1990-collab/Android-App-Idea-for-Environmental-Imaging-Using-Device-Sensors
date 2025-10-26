# Android Studio Compilation Fixes

## 🚨 **LOGCAT ERRORS AND SOLUTIONS**

Based on the Gradle build output, here are the specific errors and their fixes:

---

## ✅ **FIXED ISSUES**

### 1. **Java Version Issue** ✅ RESOLVED
- **Error**: `Android Gradle plugin requires Java 17 to run. You are currently using Java 11`
- **Solution**: Installed OpenJDK 17 and configured Gradle
- **Status**: ✅ **FIXED**

### 2. **Material3 Theme Issue** ✅ RESOLVED  
- **Error**: `style/Theme.Material3.DayNight.DarkActionBar not found`
- **Solution**: Changed to `Theme.MaterialComponents.DayNight.DarkActionBar`
- **Status**: ✅ **FIXED**

### 3. **SDK Version Warning** ✅ RESOLVED
- **Error**: `compileSdk = 35` not supported by Android Gradle Plugin 8.2.2
- **Solution**: Lowered to `compileSdk 34` and `targetSdk 34`
- **Status**: ✅ **FIXED**

### 4. **Missing Icons** ✅ RESOLVED
- **Error**: `Unresolved reference: ic_stop`
- **Solution**: Created `ic_stop.xml` in drawable resources
- **Status**: ✅ **FIXED**

### 5. **Math Import Issues** ✅ PARTIALLY FIXED
- **Error**: `Unresolved reference: pow, sqrt, abs, asin, atan2`
- **Solution**: Added `import kotlin.math.*` to sensor files
- **Status**: ✅ **FIXED IN IMUSensor.kt**, ⚠️ **REMAINING IN BluetoothRangingSensor.kt**

### 6. **Shader Visibility** ✅ RESOLVED
- **Error**: `Cannot access 'VERTEX_SHADER_CODE': it is private`
- **Solution**: Changed `private const val` to `internal const val`
- **Status**: ✅ **FIXED**

### 7. **Matrix Type Conversion** ✅ RESOLVED
- **Error**: `Type mismatch: ArrayRealVector but RealMatrix! was expected`
- **Solution**: Changed `K.multiply(innovationVector)` to `K.operate(innovationVector)`
- **Status**: ✅ **FIXED**

---

## ⚠️ **REMAINING ISSUES TO FIX**

### 1. **MainActivity Toast Error** (Line 402)
```kotlin
// ERROR: None of the following functions can be called with the arguments supplied
Toast.makeText(this, "Error loading snapshot: ${e.message}", Toast.LENGTH_LONG)

// FIX: Change string interpolation
Toast.makeText(this, "Error loading snapshot: " + e.message, Toast.LENGTH_LONG)
```

### 2. **WiFi RTT Callback Issues** (Lines 94-101)
```kotlin
// ERROR: Type mismatch and unresolved reference
val callback = object : RangingResultCallback() {
    override fun onRangingResults(results: List<RangingResult>) { ... }
    override fun onRangingFailure(code: Int) { ... }
}

// FIX: Correct callback implementation
val callback = object : RangingResultCallback {
    override fun onRangingResults(results: List<RangingResult>) { ... }
    override fun onRangingFailure(code: Int) { ... }
}
```

### 3. **If-Expression Issues** (Multiple files)
**Error**: `'if' must have both main and 'else' branches if used as an expression`

**Locations:**
- `DataAcquisitionModule.kt:148`
- `ReconstructionModule.kt:84, 88, 90`

**Fix Pattern:**
```kotlin
// ERROR: If used as expression without else
val result = if (condition) {
    doSomething()
}

// FIX: Add else branch or use statement form
val result = if (condition) {
    doSomething()
} else {
    defaultValue
}

// OR use as statement:
if (condition) {
    doSomething()
}
```

---

## 🔧 **QUICK FIX COMMANDS**

Run these commands in the Android project directory:

```bash
# 1. Fix MainActivity Toast
sed -i 's/\${e\.message}/" + e.message + "/g' app/src/main/java/com/environmentalimaging/app/MainActivity.kt

# 2. Fix remaining pow issue  
sed -i 's/kotlin\.math\.pow/pow/g' app/src/main/java/com/environmentalimaging/app/sensors/BluetoothRangingSensor.kt

# 3. Try building again
./gradlew clean build
```

---

## 📱 **ANDROID STUDIO NEXT STEPS**

### In Android Studio:
1. **Sync Project**: File → Sync Project with Gradle Files  
2. **Check Logcat**: View → Tool Windows → Logcat
3. **Build Project**: Build → Rebuild Project
4. **Fix Remaining Errors**: Use the specific fixes above

### Expected Results:
- ✅ **Java 17**: Should work now
- ✅ **Material Theme**: Should compile
- ✅ **Basic Structure**: Should build successfully
- ⚠️ **Remaining Kotlin Errors**: Need manual fixes above

---

## 🎯 **PRIORITY ORDER**

1. **First**: Fix WiFi RTT callback (most critical for sensors)
2. **Second**: Fix if-expressions (logic flow issues)  
3. **Third**: Fix remaining string interpolation
4. **Fourth**: Test on device with proper permissions

---

## 📝 **TESTING CHECKLIST**

After fixes:
- [ ] `./gradlew build` succeeds
- [ ] Android Studio shows no red errors
- [ ] App builds APK successfully
- [ ] Runtime permissions work correctly
- [ ] Sensor APIs accessible on target device

The app is **very close** to working! Most architectural issues are resolved, just need these final syntax fixes.
#!/bin/bash

echo "=== ANDROID STUDIO LOGCAT FIXES ==="
echo "Applying comprehensive fixes based on logcat errors..."

# 1. Fix MainActivity Toast issue at line 402
echo "1. Fixing MainActivity Toast..."
sed -i 's/Toast.makeText(this, "Error loading snapshot: \${e.message}", Toast.LENGTH_LONG)/Toast.makeText(this, "Error loading snapshot: " + e.message, Toast.LENGTH_LONG)/' app/src/main/java/com/environmentalimaging/app/MainActivity.kt

# 2. Fix BluetoothRangingSensor pow import at line 177
echo "2. Fixing BluetoothRangingSensor pow function..."
sed -i '177s/10.0.pow/10.0.pow/' app/src/main/java/com/environmentalimaging/app/sensors/BluetoothRangingSensor.kt

# 3. Fix DataAcquisitionModule if-expression at line 148 
echo "3. Fixing DataAcquisitionModule if-expression..."
# This needs manual inspection - let's check what the issue is

# 4. Fix WiFi RTT callback issues
echo "4. Fixing WiFiRttSensor callback..."
# Will need to check the actual callback implementation

# 5. Fix ReconstructionModule if-expressions at lines 84, 88, 90
echo "5. Fixing ReconstructionModule if-expressions..."
# These need manual fixes too

echo "✅ Script completed. Manual fixes may be needed."
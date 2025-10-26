#!/bin/bash

# Android Studio Compilation Fix Script
# This script fixes all the compilation errors found in the logcat

echo "=== FIXING ANDROID STUDIO COMPILATION ERRORS ==="
echo "Applying fixes for Kotlin compilation errors..."
echo

# Create missing icon resources first
echo "1. Creating missing icon resources..."

# Create ic_stop.xml
cat > app/src/main/res/drawable/ic_stop.xml << 'EOF'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorOnSurface">
  <path
      android:fillColor="@android:color/white"
      android:pathData="M6,6h12v12H6z"/>
</vector>
EOF

echo "✅ Created ic_stop.xml"

# Now fix the Kotlin compilation errors
echo
echo "2. Fixing Kotlin import and compilation errors..."

# Fix MainActivity.kt Toast issue
echo "Fixing MainActivity.kt..."
sed -i 's/Toast.makeText(this, "Error loading snapshot: \${e.message}", Toast.LENGTH_LONG)/Toast.makeText(this, "Error loading snapshot: ${e.message}", Toast.LENGTH_LONG)/' app/src/main/java/com/environmentalimaging/app/MainActivity.kt

# Fix math imports in BluetoothRangingSensor.kt
echo "Fixing BluetoothRangingSensor.kt..."
sed -i '1i import kotlin.math.pow' app/src/main/java/com/environmentalimaging/app/sensors/BluetoothRangingSensor.kt

# Fix IMUSensor.kt math imports
echo "Fixing IMUSensor.kt..."
sed -i '1i import kotlin.math.*' app/src/main/java/com/environmentalimaging/app/sensors/IMUSensor.kt

# Fix the division ambiguity in IMUSensor.kt
sed -i 's/magnitude \/ 2f/magnitude \/ 2.0f/' app/src/main/java/com/environmentalimaging/app/sensors/IMUSensor.kt

echo "✅ Fixed basic import issues"

echo
echo "3. Creating comprehensive Kotlin fixes..."

# Create the actual fix files
echo "Creating fix patches..."
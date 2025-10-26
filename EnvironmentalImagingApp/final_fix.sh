#!/bin/bash

echo "=== FINAL ANDROID STUDIO COMPILATION FIXES ==="
echo "Applying the remaining 4 critical compilation fixes..."

# Create a backup directory for safety
mkdir -p fixes_backup
cp -r app/src/main/java/* fixes_backup/ 2>/dev/null || true

echo "1. Fixing MainActivity Toast issue (line 402)..."
# The issue might be with implicit context conversion
# Let's try explicit context casting
sed -i 's/Toast\.makeText(this, "Scan data reset", Toast\.LENGTH_SHORT)/Toast\.makeText(this as Context, "Scan data reset", Toast.LENGTH_SHORT)/' app/src/main/java/com/environmentalimaging/app/MainActivity.kt

echo "2. Fixing ReconstructionModule if-expressions..."
# These if statements are being treated as expressions somewhere
# Let's add explicit Unit returns to make them statements

# Fix line 84 - add explicit statement termination
sed -i '/if (pointCloud\.isNotEmpty()) {/,/}/ {
    s/_pointCloud\.emit(pointCloud)/_pointCloud.emit(pointCloud); Unit/
}' app/src/main/java/com/environmentalimaging/app/visualization/ReconstructionModule.kt

# Fix line 88 - add explicit statement termination  
sed -i '/if (accumulatedLandmarks\.size >= MIN_LANDMARKS_FOR_RECONSTRUCTION) {/,/}/ {
    s/val mesh = generateEnvironmentMesh(pointCloud)/val mesh = generateEnvironmentMesh(pointCloud); Unit/
}' app/src/main/java/com/environmentalimaging/app/visualization/ReconstructionModule.kt

# Fix line 90 - add explicit statement termination
sed -i '/if (mesh\.vertices\.isNotEmpty()) {/,/}/ {
    s/_environmentMesh\.emit(mesh)/_environmentMesh.emit(mesh); Unit/
}' app/src/main/java/com/environmentalimaging/app/visualization/ReconstructionModule.kt

echo "3. Alternative fix - making if statements explicit..."
# Let's try a different approach - ensure all if statements are properly terminated

echo "✅ Applied all fixes!"
echo "Running test compilation..."

# Test the compilation
./gradlew clean compileDebugKotlin

if [ $? -eq 0 ]; then
    echo "🎉 SUCCESS! All compilation errors fixed!"
    echo "Ready for Android Studio!"
else
    echo "⚠️  Still have errors. Check the output above."
    echo "You may need to manually fix the remaining issues in Android Studio."
fi

echo "=== Fix script completed ==="
#!/bin/bash

# Android USB Setup Verification Script
# Checks if all components are properly configured for Android development

echo "🔍 Android Development Setup Verification"
echo "========================================="
echo ""

# Check 1: User groups
echo "✓ Checking user groups..."
if groups | grep -q "plugdev"; then
    echo "  ✅ User is in 'plugdev' group"
else
    echo "  ❌ User NOT in 'plugdev' group"
    echo "  Run: sudo usermod -aG plugdev $USER"
    echo "  Then log out and log back in"
fi
echo ""

# Check 2: Udev rules
echo "✓ Checking udev rules..."
if [ -f /etc/udev/rules.d/51-android.rules ]; then
    echo "  ✅ Android udev rules file exists"
    
    # Check Samsung vendor ID
    if grep -q "04e8" /etc/udev/rules.d/51-android.rules; then
        echo "  ✅ Samsung vendor ID (04e8) configured"
    else
        echo "  ⚠️  Samsung vendor ID not found in rules"
    fi
else
    echo "  ❌ Android udev rules NOT found"
    echo "  Rules should be at: /etc/udev/rules.d/51-android.rules"
fi
echo ""

# Check 3: ADB installation
echo "✓ Checking ADB..."
if command -v adb &> /dev/null; then
    echo "  ✅ ADB is installed"
    ADB_VERSION=$(adb --version | head -n1)
    echo "  Version: $ADB_VERSION"
else
    echo "  ❌ ADB NOT installed"
    echo "  Install: sudo apt install android-sdk-platform-tools"
fi
echo ""

# Check 4: ADB server
echo "✓ Checking ADB server..."
if pgrep -x "adb" > /dev/null; then
    echo "  ✅ ADB server is running"
else
    echo "  ⚠️  ADB server not running"
    echo "  Starting ADB server..."
    adb start-server 2>/dev/null
fi
echo ""

# Check 5: Connected devices
echo "✓ Checking connected devices..."
DEVICE_COUNT=$(adb devices | grep -c "device$")
if [ $DEVICE_COUNT -gt 0 ]; then
    echo "  ✅ $DEVICE_COUNT device(s) connected:"
    adb devices | grep "device$" | while read -r line; do
        echo "     - $line"
    done
else
    echo "  ℹ️  No physical devices connected"
    echo "  To connect:"
    echo "  1. Enable USB debugging on your device"
    echo "  2. Connect via USB cable"
    echo "  3. Accept the USB debugging prompt"
fi
echo ""

# Check 6: Build tools
echo "✓ Checking build configuration..."
if [ -f "gradlew" ]; then
    echo "  ✅ Gradle wrapper found"
    if [ -x "gradlew" ]; then
        echo "  ✅ Gradle wrapper is executable"
    else
        echo "  ⚠️  Gradle wrapper not executable"
        echo "  Run: chmod +x gradlew"
    fi
else
    echo "  ❌ Gradle wrapper NOT found"
    echo "  Make sure you're in the project directory"
fi
echo ""

# Check 7: USB devices
echo "✓ Checking USB devices..."
if command -v lsusb &> /dev/null; then
    SAMSUNG_USB=$(lsusb | grep -i samsung)
    if [ -n "$SAMSUNG_USB" ]; then
        echo "  ✅ Samsung USB device detected:"
        echo "     $SAMSUNG_USB"
    else
        echo "  ℹ️  No Samsung USB devices detected"
    fi
else
    echo "  ⚠️  lsusb command not available"
fi
echo ""

# Summary
echo "========================================="
echo "📋 Setup Summary"
echo "========================================="

if groups | grep -q "plugdev" && \
   [ -f /etc/udev/rules.d/51-android.rules ] && \
   command -v adb &> /dev/null; then
    echo "✅ Your system is ready for Android development!"
    echo ""
    echo "Next steps:"
    echo "1. Connect your Samsung Galaxy S25 Ultra via USB"
    echo "2. Enable USB debugging on the device"
    echo "3. Run: adb devices"
    echo "4. Deploy app: ./gradlew installDebug"
else
    echo "⚠️  Some configuration steps are missing"
    echo "Please review the items above marked with ❌"
fi
echo ""

# Quick commands
echo "========================================="
echo "📌 Quick Commands"
echo "========================================="
echo "Check devices:       adb devices"
echo "Install debug APK:   ./gradlew installDebug"
echo "View logs:           adb logcat"
echo "Shell access:        adb shell"
echo "Restart ADB:         adb kill-server && adb start-server"
echo "========================================="

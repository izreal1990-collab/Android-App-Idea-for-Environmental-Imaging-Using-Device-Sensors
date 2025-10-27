# Environmental Imaging App

**Developer:** Jovan Blango  
**Copyright:** © 2025 Jovan Blango  
**Repository:** [Android-App-Idea-for-Environmental-Imaging-Using-Device-Sensors](https://github.com/izreal1990-collab/Android-App-Idea-for-Environmental-Imaging-Using-Device-Sensors)

## Overview

An advanced Android application for environmental imaging and spatial mapping using multiple device sensors. This app leverages cutting-edge sensor fusion technology to create detailed 3D environmental maps and provide real-time spatial analysis.

## Key Features

### 🌍 Multi-Sensor Fusion
- **WiFi RTT (Round-Trip Time):** Sub-meter precision indoor positioning
- **Bluetooth Ranging:** Distance measurement using RSSI and connection metrics
- **Acoustic Ranging:** Sound-based distance measurements using microphone
- **IMU Sensors:** Accelerometer, gyroscope, and magnetometer integration
- **Camera:** Visual data capture and image-based analysis

### 🤖 AI-Powered Analysis
- **Conversational AI Assistant:** Natural language interface for data queries
- **ML-Enhanced SLAM:** Machine learning-based Simultaneous Localization and Mapping
- **Environmental AI Analysis:** Intelligent interpretation of spatial data
- **System Health Monitoring:** AI-driven performance optimization

### 📊 Advanced Visualization
- **3D Environment Rendering:** Real-time OpenGL-based visualization
- **Enhanced 3D Views:** Multiple visualization modes (wireframe, solid, heatmap)
- **Point Cloud Display:** Detailed spatial point representations
- **Session Analytics Dashboard:** Comprehensive data analysis and insights

### 🔧 Professional Tools
- **Multiple Scanning Modes:** Quick, detailed, and custom scanning options
- **Data Export System:** Export to CSV, JSON, PLY formats
- **Background Processing:** Efficient data handling and storage
- **Performance Monitoring:** Real-time system metrics tracking

## Technical Specifications

- **Minimum SDK:** Android 9.0 (API 28) - Required for WiFi RTT support
- **Target SDK:** Android 14 (API 34)
- **Language:** Kotlin
- **Architecture:** MVVM with Repository Pattern
- **Key Libraries:**
  - TensorFlow Lite 2.14.0 (AI/ML processing)
  - OpenGL ES 3.0 (3D rendering)
  - AndroidX (Jetpack components)
  - Material Design 3
  - Coroutines (Async operations)

## Sensor Requirements

### Required Sensors
- Accelerometer
- Gyroscope
- Magnetometer
- Camera
- Microphone

### Optional (Enhanced Features)
- WiFi RTT capability
- Bluetooth 5.0+ with ranging support

## Installation

### Prerequisites
1. Android device running Android 9.0 or higher
2. USB debugging enabled
3. Android SDK Platform Tools installed

### Build from Source

```bash
# Clone the repository
git clone https://github.com/izreal1990-collab/Android-App-Idea-for-Environmental-Imaging-Using-Device-Sensors.git
cd EnvironmentalImagingApp

# Build the APK
./gradlew assembleDebug

# Install to connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### USB Device Setup (Ubuntu)

```bash
# Add udev rules for Android devices
sudo cp udev-rules/51-android.rules /etc/udev/rules.d/
sudo udevadm control --reload-rules
sudo udevadm trigger

# Verify device connection
adb devices
```

See [DEVICE_SETUP.md](DEVICE_SETUP.md) for detailed setup instructions.

## Usage

### Basic Workflow

1. **Launch App:** Open Environmental Imaging App
2. **Grant Permissions:** Allow camera, location, microphone, and storage access
3. **Select Scanning Mode:** Choose Quick, Detailed, or Custom scan
4. **Start Scanning:** Begin environmental data capture
5. **View Results:** Explore 3D visualization and analytics
6. **Export Data:** Save results in preferred format

### AI Assistant Commands

Talk to the AI assistant for:
- "Show me the scan results"
- "What's the accuracy of the last measurement?"
- "Analyze the environmental data"
- "Export data as CSV"

## Permissions

The app requires the following permissions:

- `CAMERA` - Visual data capture
- `RECORD_AUDIO` - Acoustic ranging measurements
- `ACCESS_FINE_LOCATION` - WiFi RTT and Bluetooth ranging
- `ACCESS_WIFI_STATE` - WiFi sensor access
- `CHANGE_WIFI_STATE` - WiFi RTT initialization
- `BLUETOOTH_SCAN` - Bluetooth device discovery
- `BLUETOOTH_CONNECT` - Bluetooth ranging
- `WRITE_EXTERNAL_STORAGE` - Data export (Android 9-12)
- `READ_EXTERNAL_STORAGE` - File access (Android 9-12)
- `FOREGROUND_SERVICE` - Background scanning
- `FOREGROUND_SERVICE_LOCATION` - Location-based services
- `FOREGROUND_SERVICE_MICROPHONE` - Acoustic ranging

## Architecture

```
app/src/main/java/com/environmentalimaging/app/
├── MainActivity.kt                  # Main entry point
├── ai/                              # AI and ML modules
│   ├── ConversationalAISystem.kt
│   ├── AIAnalysisEngine.kt
│   ├── MLEnhancedSLAM.kt
│   └── EnvironmentalAIAssistant.kt
├── sensors/                         # Sensor interfaces
│   ├── WiFiRttSensor.kt
│   ├── BluetoothRangingSensor.kt
│   ├── AcousticRangingSensor.kt
│   ├── IMUSensor.kt
│   └── DataAcquisitionModule.kt
├── slam/                            # SLAM processing
│   ├── SLAMProcessor.kt
│   └── ExtendedKalmanFilter.kt
├── visualization/                   # 3D rendering
│   ├── Enhanced3DVisualizationEngine.kt
│   ├── EnvironmentalRenderer.kt
│   └── ReconstructionModule.kt
├── storage/                         # Data management
│   ├── SnapshotStorageManager.kt
│   └── BackgroundStorageProcessor.kt
└── export/                          # Export functionality
    └── AdvancedDataExportSystem.kt
```

## Development

### Building

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run tests
./gradlew test

# Generate coverage report
./gradlew testDebugUnitTestCoverage
```

### Known Issues

See [GitHub Issues](https://github.com/izreal1990-collab/Android-App-Idea-for-Environmental-Imaging-Using-Device-Sensors/issues) for current bugs and feature requests.

## Contributing

Contributions are welcome! Please feel free to submit pull requests or open issues for bugs and feature requests.

## License

Copyright © 2025 Jovan Blango. All rights reserved.

## Acknowledgments

- TensorFlow team for ML libraries
- Android Open Source Project
- Material Design team
- OpenGL ES community

## Contact

**Developer:** Jovan Blango  
**GitHub:** [@izreal1990-collab](https://github.com/izreal1990-collab)  
**Repository:** [Environmental Imaging App](https://github.com/izreal1990-collab/Android-App-Idea-for-Environmental-Imaging-Using-Device-Sensors)

---

*Built with ❤️ for environmental sensing and spatial computing*

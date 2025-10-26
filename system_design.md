# System Architecture and Technical Specifications

## 1. High-Level System Architecture

The proposed system will be a self-contained Android application that leverages a suite of onboard sensors to capture, process, and visualize a spatial representation of the surrounding environment at a specific point in time. The architecture is designed around a modular framework, allowing for future expansion and refinement of individual components.

The core components of the system are as follows:

1.  **Data Acquisition Module:** Responsible for interfacing with the device's hardware and collecting raw sensor data.
2.  **Sensor Fusion & Processing Module:** This is the core of the system, where data from multiple sensors is integrated and processed to create a coherent model of the environment.
3.  **3D Reconstruction & Visualization Module:** Takes the processed data and generates a 3D representation that can be rendered and displayed to the user.
4.  **Temporal Snapshot & Data Storage Module:** Manages the storage and retrieval of environmental snapshots.

Below is a high-level diagram illustrating the interaction between these components:

```
+-----------------------------------+
|         Android Application       |
+-----------------------------------+
|                                   |
|      +------------------------+   |
|      | 3D Reconstruction &    |   |
|      | Visualization Module   |   |
|      +-----------+------------+   |
|                  ^                |
|                  | Processed Data |
|                  v                |
|      +-----------+------------+   |
|      | Sensor Fusion &        |   |
|      | Processing Module      |   |
|      | (SLAM, Kalman Filters) |   |
|      +-----------+------------+   |
|                  ^                |
|                  | Raw Sensor Data|
|                  v                |
|      +-----------+------------+   |
|      | Data Acquisition Module|   |
|      | (WiFi, BT, Audio, IMU) |   |
|      +------------------------+   |
|                                   |
|      +------------------------+   |
|      | Temporal Snapshot &    |   |
|      | Data Storage Module    |   |
|      +------------------------+   |
|                                   |
+-----------------------------------+
```

## 2. Technical Specifications

### 2.1. Data Acquisition Module

This module will be responsible for collecting data from the following sensors:

*   **WiFi:** Utilizes the `WifiRttManager` API (for Android 9+) and the new `RangingManager` API (for Android 16+) to perform distance measurements to nearby RTT-capable access points. This will provide a set of distance constraints for localization.
*   **Bluetooth:** Leverages the `RangingManager` API (on supported devices with Bluetooth 6.0+) for high-precision distance measurements using Channel Sounding. This will complement the WiFi ranging data.
*   **Acoustic:** Employs the `AudioRecord` API to capture audio signals. The system will emit a series of high-frequency chirps (within the device's hardware capabilities) and analyze the recorded echoes to estimate distances to nearby surfaces using the FMCW (Frequency Modulated Continuous Wave) technique.
*   **Inertial Measurement Unit (IMU):** Accesses the accelerometer and gyroscope through the `SensorManager` API to track the device's motion and orientation. This is crucial for dead-reckoning and for providing a continuous estimate of the device's position between ranging measurements.

### 2.2. Sensor Fusion & Processing Module

This module will implement a Simultaneous Localization and Mapping (SLAM) algorithm to build a map of the environment while simultaneously tracking the device's location within it. A Graph-based SLAM approach is recommended due to its efficiency and robustness.

*   **State Estimation:** The state of the system will be represented by the device's pose (position and orientation) and the positions of environmental landmarks (e.g., WiFi access points, prominent acoustic reflectors).
*   **Motion Model:** The IMU data will be used to predict the device's motion between time steps. This forms the basis of the dead-reckoning component.
*   **Measurement Model:** The distance measurements from WiFi RTT, Bluetooth Channel Sounding, and acoustic ranging will be used to correct the predicted state and to build the map. These measurements will be incorporated into the SLAM graph as constraints.
*   **Filtering:** Kalman filters or Particle filters will be used to filter the noisy sensor data and to provide a more accurate state estimate.
*   **Loop Closure:** The system will actively look for opportunities to recognize previously visited locations. When a loop closure is detected (e.g., by recognizing a known WiFi AP or an acoustic signature), a new constraint will be added to the SLAM graph, which helps to correct accumulated drift and improve the overall map accuracy.

### 2.3. 3D Reconstruction & Visualization Module

This module will be responsible for generating a 3D representation of the environment from the output of the SLAM algorithm. The environment will be represented as a 3D point cloud or a voxel grid.

*   **Point Cloud Generation:** The estimated positions of acoustic reflectors and other landmarks from the SLAM map will be used to generate a sparse 3D point cloud.
*   **Surface Reconstruction (Advanced):** For a more detailed representation, a surface reconstruction algorithm (e.g., Poisson surface reconstruction) could be applied to the point cloud to generate a 3D mesh. However, this is computationally expensive and may not be feasible for real-time operation on a mobile device.
*   **Visualization:** The 3D model will be rendered using OpenGL ES for Android. A simple user interface will allow the user to rotate, pan, and zoom the 3D view.

### 2.4. Temporal Snapshot & Data Storage Module

This module will allow the user to save and load 

environmental snapshots. Each snapshot will consist of:

*   The generated 3D model (point cloud or mesh).
*   The raw sensor data collected during the capture session.
*   The final SLAM graph.

This data will be stored in a structured format (e.g., a compressed archive containing JSON for metadata and binary files for the 3D model and sensor data) in the application's private storage.


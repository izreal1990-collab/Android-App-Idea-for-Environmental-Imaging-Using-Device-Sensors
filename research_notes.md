# Research Notes: Environmental Reconstruction App

## WiFi RTT (Round-Trip Time) Technology

**Source:** https://medium.com/@darryncampbell_83863/indoor-positioning-with-wifi-rtt-and-google-wifi-a638f1147b84

### Key Capabilities:
- WiFi RTT (802.11mc protocol) provides indoor positioning through time-of-flight measurements
- Introduced in Android Pie (2018) with native support
- Accuracy typically within ±0.5m to 1-2 meters in optimal conditions

### Important Limitations:
1. **Accuracy requires multiple measurements**: Single readings are noisy; requires rapid measurements and filtering (weighted mean or Kalman filter)
2. **2D plane limitation**: Distance measurements are most accurate in a 2D plane; vertical distance measurements are problematic
3. **Wall penetration issues**: Signal propagation through walls significantly impacts accuracy as signals bounce around corners
4. **Best suited for open environments**: Works well in open-plan offices or malls, but poorly in multi-floor residential properties
5. **Requires compatible hardware**: Both device and access points must support 802.11mc protocol

### Android Implementation:
- Available since Android 9 (API level 28)
- Android 15 enhanced WiFi Ranging for sub-1-meter accuracy
- Requires WiFi RTT-capable access points (e.g., Google WiFi)

---



## Acoustic Indoor Space Mapping (SAMS)

**Source:** Pradhan et al., "Smartphone-based Acoustic Indoor Space Mapping" (2018)

### Key Technology - FMCW (Frequency Modulated Continuous Wave):
- Uses smartphone speakers to emit audio signals and microphones to capture reflections
- SAMS (Smartphone Acoustic Mapping System) applies FMCW technology to estimate distances to objects (walls, doors, shelves)
- Existing audio-based tracking works focus on shortest path or one moving path
- SAMS leverages multiple peaks in FMCW profile to get critical structural information like corners and edges

### Capabilities:
- Low-cost solution using only built-in smartphone speakers and microphones
- Robust to ambient lighting conditions and transparent materials (unlike vision/LiDAR)
- Can create indoor contours by user walking around while holding smartphone
- Achieves median error of 1.5 cm for single wall, 6 cm for multiple walls
- 90th percentile error of 30 cm and 1 m respectively

### Applications Demonstrated:
1. **Obstacle detection for people** (e.g., navigation for blind people)
2. **AR/VR enrichment** by detecting obstacles and enriching applications
3. **Construction assistance** by helping in indoor construction work
4. **Wireless signal strength prediction** - RSS prediction within 1.5-2 dB error

### Technical Approach:
- Uses Inertial Measurement Unit (IMU) sensor-based dead-reckoning for position tracking
- Employs customized distance measurement with automatic geometric constraints
- Calibration-free manner for deriving contours
- Generalizes from single wall to multi-wall settings, accounting for clutter and curved surfaces

### Limitations:
- Smartphone speakers/microphones designed mainly for low-frequency human voice and music
- Limited by audio hardware capabilities compared to specialized ultrasonic sensors

---



## WiFi-based Imaging and Object Reconstruction

**Source:** MIT News (July 2025) - "New imaging technique reconstructs the shapes of hidden objects"

### mmNorm System (MIT Research):
- Uses millimeter wave (mmWave) signals (same as WiFi) to create 3D reconstructions of hidden objects
- Can penetrate common obstacles like plastic containers and interior walls
- Collects reflections and estimates surface shapes using surface normal calculations

### Key Innovation - Specularity Property:
- Recognizes that mmWave surfaces act like mirrors, generating specular reflections
- Estimates not just location of reflection, but also the **direction of the surface** at that point
- Surface normal = direction of a surface at a particular point in space
- Combines surface normal estimations to reconstruct 3D object curvature

### Technical Implementation:
- Radar attached to robotic arm that moves around hidden item
- Compares signal strength at different locations to estimate surface curvature
- Multiple antennas "vote" on surface normal direction based on signal strength
- Borrowed computer graphics techniques to choose most representative surface from many possible surfaces

### Applications:
- Warehouse and factory settings for robots to find and manipulate hidden items
- Object detection through occlusions

### Limitations:
- Requires moving radar/antenna system (robotic arm in prototype)
- Needs multiple measurements from different positions
- Computational complexity in surface reconstruction

---



## Bluetooth Distance Measurement Technologies

**Source:** Novel Bits - "Channel Sounding: Secure Fine Ranging using Bluetooth LE" (Dec 2024)

### Bluetooth 6.0 Channel Sounding (Latest):
- Introduced in Bluetooth Core v6.0 specification
- Achieves **centimeter-level accuracy** for distance measurement
- Operates in one-to-one topology between connected BLE devices
- Defines 79 channels in new PHY (vs 40 in standard LE)

### Two Complementary Methods:
1. **Phase-Based Ranging (PBR)**: Measures phase differences of signals to calculate distance
2. **Round-Trip Time (RTT)**: Measures time for signal to travel between devices and return

### Technical Operation:
- One device = Initiator, other = Reflector
- Supports single or multiple antenna paths (up to 4)
- Four operational modes:
  - Mode-0: Synchronization and information exchange
  - Mode-1: RTT packets for secure distance verification
  - Mode-2: Unmodulated tones for PBR
  - Mode-3: Combined RTT and PBR for enhanced precision

### Older Bluetooth Methods (Comparison):
1. **RSSI (Received Signal Strength Indication)**:
   - Inaccuracies of 3-5 meters
   - Highly sensitive to environmental interference
   - Available for all devices regardless of version
   
2. **Direction Finding (AoA/AoD)** - Bluetooth 5.1:
   - Angle of Arrival / Angle of Departure
   - Requires antenna arrays and multiple locators
   - Higher cost and complexity
   - Accuracy degrades in multipath environments

### Applications:
- Remote keyless entry systems (RKE)
- Industrial asset tracking
- Safety-critical operations requiring precise distance awareness

### Limitations:
- **Distance ambiguity** after ~150m with PBR alone
- Requires Bluetooth 6.0 hardware support
- Algorithm for distance calculation not defined in spec (application responsibility)
- One-to-one topology only (must be connected devices)

---



## AI-Enhanced WiFi Imaging

**Source:** Tom's Hardware (Oct 2025) - LatentCSI Research, Institute of Science Tokyo

### LatentCSI Technology:
- Combines latent diffusion models (Stable Diffusion 3) with WiFi CSI (Channel State Information)
- WiFi signals bounce off walls, furniture, and objects, carrying spatial information
- CSI = echo-like data from signal reflections in environment

### How It Works:
- Maps CSI into **latent space** instead of pixel space (compressed internal representation)
- Uses pretrained diffusion model (Stable Diffusion 3) with modified encoder
- WiFi CSI provides real-time information: number of people, positions, object layout
- AI fills in gaps to create photorealistic high-resolution images

### Key Limitation - Requires Pretraining:
- **Model must be pretrained on actual photos of the room**
- AI already knows what room looks like; WiFi provides real-time updates
- Cannot generate images of unknown environments from WiFi alone
- Not a standalone solution - needs baseline understanding

### Advantages Over Traditional CSI:
- Traditional CSI imaging produces crude, low-resolution results
- Too compute-heavy and slow
- LatentCSI is faster and more efficient by using latent space

### Privacy Concerns:
- Modern modems already capable of motion-sensing
- Potential surveillance applications
- Currently just lab demonstration

---



## Android API Capabilities for Environmental Reconstruction

### WiFi RTT API (android.net.wifi.rtt.WifiRttManager):

**Availability:**
- Introduced in Android 9 (API level 28)
- IEEE 802.11az NTB ranging support in Android 15 (API level 35)
- Android 16 introduces unified RangingManager API

**Capabilities:**
- Measure distance to nearby RTT-capable WiFi access points
- Accuracy typically within 1-2 meters with multilateration (3+ APs)
- Does NOT require connection to access points
- Privacy-preserving: only requesting device knows distance
- Can measure to WiFi Aware peer devices

**Requirements:**
- Hardware must implement IEEE 802.11-2016 FTM or 802.11az standard
- Location services enabled, WiFi scanning on
- NEARBY_WIFI_DEVICES permission (API 33+) or ACCESS_FINE_LOCATION (earlier)
- Must be foreground app or foreground service (no background access)
- Access points must support IEEE 802.11mc or 802.11az

**Limitations:**
- Throttled for background apps
- Requires RTT-capable access points (limited deployment)
- Cannot access raw antenna/radio hardware directly
- Pre-determined AP locations needed (Android 9) or LCI/LCR support (Android 10+)

### Bluetooth Ranging API:

**Android 16 RangingManager:**
- Unified interface for WiFi RTT and Bluetooth Channel Sounding
- Precise ranging between devices
- Centimeter-level accuracy potential

**Requirements:**
- Bluetooth 6.0 hardware for Channel Sounding
- Connected devices only (one-to-one topology)
- Limited device support (very new technology)

### Audio/Acoustic Capabilities:

**Android Audio Recording:**
- Standard sample rate: 48 kHz (captures up to 24 kHz frequencies)
- Some devices support "HD" audio (88 kHz+) for ultrasonic up to 44 kHz
- Built-in speakers and microphones designed for human voice range (low frequency)
- No standardized ultrasonic support across devices

**Limitations:**
- Hardware varies significantly between devices
- Most consumer hardware optimized for 20 Hz - 20 kHz (human hearing range)
- Ultrasonic capabilities not guaranteed
- No direct low-level control without root access

### Root Access Implications:

**What Root Access Provides:**
- Ability to modify system files and settings
- Access to restricted APIs and system services
- Potential for deeper hardware control

**What Root Access Does NOT Provide:**
- Direct antenna hardware manipulation (controlled by firmware/drivers)
- Ability to bypass hardware limitations
- Access to proprietary radio firmware
- Raw RF signal generation/capture (requires specialized hardware)

**Security and Compatibility Issues:**
- Many apps detect and block rooted devices
- Banking, payment, and security apps won't function
- SafetyNet/Play Integrity API failures
- OTA updates typically blocked
- Warranty void on most devices

---



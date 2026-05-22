# Location Alarm V2 📍⏰
### *The Smart Commuter Engine*

Location Alarm V2 is a high-precision, route-aware travel assistant designed for commuters. Unlike standard proximity alarms that only check "as the crow flies" distance, this app uses a **Smart Routing Engine** to track your actual progress along roads and highways. 

It ensures you never miss your stop by triggering an alarm based on **real road distance** or **predicted arrival time**, even if the app is in your pocket or the screen is off.

---

## 🌟 Why V2 is Different?
Standard GPS alarms often fail because they use a straight line to measure distance. If your bus is traveling a winding road, a 1km "straight line" alarm might go off 15 minutes too early. 

**V2 solves this by:**
- **Road Snapping:** It "snaps" your GPS position to the actual road path.
- **Route Tracking:** It knows exactly how much road distance is left, not just how close you are to the dot on the map.
- **Segment-Aware Speed:** It tracks the specific speed limit of your current road segment (e.g., city vs. highway) to adjust its predictions dynamically.
- **Predictive Timing:** It calculates your ETA based on your actual real-time speed calibrated against road data.

---

## ✨ Key Features

### 🛣️ Smart Routing & Alarms
- **Road-Aware Distance:** High-precision tracking that follows the curves of the road using the OSRM (Open Source Routing Machine) engine.
- **Segment-Aware ETA:** A stateless correction model that uses segment-specific speed annotations from OSRM to provide highly accurate arrival times on mixed road types.
- **Real-Time Route Slicing:** The map route line dynamically shortens (slices) as you move, providing immediate visual feedback of your progress.
- **Smart ETA Alarms:** Set an alarm to wake you up exactly **10 minutes before you arrive**, regardless of traffic or distance.

### 🔋 Efficiency & Reliability
- **Smart Polling (Battery-Saver):** Dynamically adjusts GPS update frequency based on distance to the destination (e.g., 30s polling when 10km away, 2s polling when within 2km).
- **WakeLock Management:** Ensures the tracking engine stays active even when the phone enters "Deep Sleep" during long commutes.
- **State Recovery:** Automatically resumes tracking if the system kills the app or if the device restarts.

### 🗺️ Advanced Map Experience
- **Center Lock (Auto-Follow):** Toggle a "locked" mode that keeps the user centered on the map. It automatically disengages if you manually pan the map.
- **Interactive MapLibre Interface:** A smooth, battery-efficient map using OpenStreetMap data.
- **Journey Preview:** See your entire route line and total distance before you even start the trip.
- **One-Tap Search:** Instant destination search with history and "smart suggestions."

### 📂 History & Management
- **High-Fidelity Saving:** Automatically captures your **actual path taken**, total distance, and duration.
- **Improved UI/UX:** Data-rich cards for Saved Routes and History, displaying real-world metrics (KM, minutes) and active alarm settings.
- **Route Reuse:** When you reuse a saved journey, the app uses your previously traversed high-precision path instead of the OSRM API, ensuring 100% consistency.
- **Reliable Background Engine:** Enhanced with state recovery and deep-sleep optimization (WakeLocks) to ensure long journeys are recorded without interruption.

### 🛡️ Reliability (The "Guardian" System)
- **Background Tracking:** A dedicated service keeps the app alive and monitoring your location even when you are using other apps or the phone is locked.
- **Hardware-Level Alerts:** Alarms use a continuous loop of sound and vibration that won't be silenced by standard notification filters.

---

## 🛠️ The Tech Stack (Simplified)

- **User Interface:** **Jetpack Compose** (Modern, smooth Android UI).
- **Offline Database:** **Room / SQLite** (Stores your saved routes and travel history safely on your phone).
- **Mapping:** **MapLibre & OSRM** (High-performance, open-source mapping and routing).
- **Spatial Math:** **Turf-Java** (The "brain" that handles complex road calculations).
- **Background Engine:** **Android Foreground Services** (Ensures 100% reliability during travel).

---

## 🚀 Getting Started

### Prerequisites
- An Android device running Android 8.0 (Oreo) or higher.
- GPS/Location permissions (Fine Location).

### Installation
1. Clone the repo: `git clone https://github.com/janak1284/app_dev_v2.git`
2. Open in **Android Studio Jellyfish** or newer.
3. Build and install the `debug` variant.

---

## 📂 Project Logic
The app is built using **Clean Architecture**, which means the code is separated into three simple parts:
1. **The UI (View):** What you see and click (Compose).
2. **The Logic (ViewModel):** The bridge that handles search results and button clicks.
3. **The Engine (Service/Repository):** The background workers that track GPS, talk to the map servers, and save data to your phone.

---

## 🗺️ Roadmap
- [x] **Phase 5:** Home Screen, Saved Routes, and Journey History.
- [x] **Phase 6:** Predictive Routing Engine & Road-Snapping logic.
- [x] **Phase 7:** High-Fidelity Path Saving & Route Slicing.
- [x] **Phase 8:** Refinement Phase (Stability fixes & UX polish).
- [x] **Phase 9:** Battery-saver mode (Smart Polling) & Segment-Aware Speed Correction.
- [ ] **Phase 10:** Multi-Modal Transit Engine (V3).

---

## ⚖️ License
Built for the Location Alarm V2 Project. All rights reserved.

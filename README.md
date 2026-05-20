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
- **Predictive Timing:** It calculates your ETA based on your current speed and road-specific data.

---

## ✨ Key Features

### 🛣️ Smart Routing & Alarms
- **Road-Aware Distance:** High-precision tracking that follows the curves of the road using the OSRM (Open Source Routing Machine) engine.
- **Smart ETA Alarms:** Set an alarm to wake you up exactly **10 minutes before you arrive**, regardless of traffic or distance.
- **Distance Alarms:** Traditional wake-up calls based on road mileage (e.g., "Wake me 500m before the stop").

### 🗺️ Advanced Map Experience
- **Interactive MapLibre Interface:** A smooth, battery-efficient map using OpenStreetMap data.
- **Journey Preview:** See your entire route line and total distance before you even start the trip.
- **One-Tap Search:** Instant destination search with history and "smart suggestions."

### 📂 History & Management
- **Journey Logs:** Automatically saves a "breadcrumb trail" (GPS path) of your completed trips so you can review your route later.
- **Saved Routes:** Bookmark your frequent commutes (Home, Work, Gym) with custom alarm settings for each.
- **Edit Mode:** Easily rename saved routes or adjust alarm distances without starting over.

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
- [x] **Phase 6:** "Edit Mode" for routes and Keyboard optimizations.
- [ ] **Phase 7:** Battery-saver mode (Smart Polling based on distance).
- [ ] **Phase 7:** Real-world field testing and accuracy calibration.

---

## ⚖️ License
Built for the Location Alarm V2 Project. All rights reserved.

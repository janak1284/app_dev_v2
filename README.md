# Location Alarm V4: The Smart Commuter Engine 📍⏰

**Location Alarm** is a next-generation, high-precision travel assistant and location-based alarm system designed for modern commuters. Moving beyond simple "as-the-crow-flies" GPS proximity alerts, this application employs a **Smart Routing Engine** to calculate actual road and railway distances, providing dynamic ETA tracking, predictive alarms, and fail-safe dead reckoning.

Whether navigating complex city streets or resting on a long-distance train, Location Alarm ensures you never miss your stop.

---

## 🌟 The Problem We Solve
Traditional GPS alarms rely on straight-line distance (Haversine formula). If your train or bus is traveling along a winding path, a 2km "straight line" alarm might trigger 15 minutes too early, or worse, fail to trigger if the road curves sharply right before the destination.

**Location Alarm solves this by:**
- **Route-Aware Distance:** It maps your GPS coordinates to the actual road or railway track, calculating the exact distance remaining *along the path*.
- **Predictive ETA:** Rather than relying on static speed, it calculates a real-time Performance Ratio using Exponential Moving Average (EMA) to dynamically scale arrival predictions.
- **Dead Reckoning in Tunnels:** If GPS is lost (e.g., inside a subway or long train), the app's watchdog service uses your last known trajectory and speed to estimate your progress, ensuring the alarm still fires.
- **Force Wake & Max Volume:** Specialized for deep sleepers, utilizing `WakeLocks` to bypass Android's Doze mode and overriding system volume to maximum for critical railway alerts.

---

## ✨ Key Features & How They Are Implemented

### 🛣️ Smart Road & Rail Routing
* **Hybrid Track Snapping:** Utilizes **Turf-Java** spatial math to snap raw GPS points to the nearest segment of the route (GeoJSON LineStrings).
* **Macro & Micro Routing:** Connects distant stations using straight macro-lines for overview, but fetches high-precision micro-routing (via OSRM for roads and OpenRailRouting API for trains) for the final approach.
* **Route Slicing:** As you progress, Turf-Java slices the remaining polyline, dynamically shrinking the visual route on the map in real-time.
* **Auto-Correction Heuristics:** Employs U-turn heuristics and auto-snap coordinates to correct routing failures from bad station data.

### ⏰ Predictive & Distance-Based Alarms
* **Dual-Trigger System:** Users can set a hard distance threshold (e.g., "Wake me at 5km") or a predictive ETA threshold (e.g., "Wake me 10 minutes before arrival").
* **Performance Ratio Engine:** The `RouteDistanceEngine` tracks your sliding average speed and compares it against the OSRM segment's expected speed. This generates an efficiency ratio (clamped to prevent outliers) which accurately scales the remaining time.
* **Bulletproof Railway Trigger:** A hardcoded safety trigger guarantees activation at 2km remaining for trains, bypassing user settings to prevent missed stops.

### 🔋 Battery Optimization & Reliability
* **Dynamic Smart Polling:** Polling intervals scale with distance. The app might check location every 45 minutes when >150km away, scaling down to 1-second tight polling during the final 2km approach.
* **Foreground Service Guardian:** `LocationAlarmService` runs persistently with Android `PowerManager.WakeLock` to guarantee survival against aggressive OEM background killing.
* **State Recovery:** Persists tracking states to `SharedPreferences`. If the device restarts or the system forcibly kills the app, it automatically resurrects the tracking session upon reboot.

### 🚆 Live Railway Telemetry Microservice
* **Headless Node.js Scraper:** A dedicated backend microservice (`scraper-microservice`) intercepts real-time train telemetry using Puppeteer/Playwright.
* **Dynamic Refresh:** The Android client polls the microservice based on live distance, syncing official railway ETA and delay status with the local high-frequency GPS tracking.

### 📂 High-Fidelity Journey History & Route Reuse
* **Real Path Saving:** Instead of just saving the start/end points, the app records high-fidelity breadcrumbs (latitude, longitude, speed, timestamp) into a local **Room SQLite Database**.
* **Route Reuse:** When you replay a saved route, the app bypasses API calls and reloads your previously verified high-precision path, ensuring complete offline consistency.

---

## 🛠️ The Tech Stack

### Android Client
- **UI Framework:** Jetpack Compose (Modern, declarative Android UI)
- **Mapping:** MapLibre GL for Android (High-performance vector mapping)
- **Spatial Math:** Turf-Java (GeoJSON math, slicing, snapping, cross-track deviation)
- **Database:** Room / SQLite (Stores routes, histories, and cloud cache logic)
- **Background Engine:** Android Foreground Services, BroadcastReceivers, WakeLocks
- **Networking:** Retrofit2 + OkHttp (Interfacing with Photon Geocoding, OSRM, ORR)

### Scraper Microservice
- **Runtime:** Node.js + Express
- **Automation:** Puppeteer/Playwright for headless extraction
- **Containerization:** Docker support

---

## 🏗️ Project Architecture (Clean Architecture)
The client is structured to separate concerns entirely:
1. **View (UI Layer):** `ui/` directory containing Compose screens (`HomeScreen`, `MapScreen`, `SettingsScreen`) and Bottom Sheets.
2. **ViewModel (Logic Layer):** `MapViewModel.kt` acts as the bridge. It handles search debouncing, state flow management, and API orchestration.
3. **Domain Layer:** `RouteDistanceEngine.kt` handles pure spatial math and ETA calculations without Android dependencies.
4. **Data/Repository Layer:** DAOs, Entities, and Repositories handle local caching and Room database access.
5. **Service Layer:** `LocationAlarmService.kt` is the independent background worker that takes over once the journey begins.

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Jellyfish (or newer).
- An Android device running Android 8.0 (Oreo) or higher.
- Node.js (for running the scraper microservice locally).

### Client Installation
1. Clone the repository: `git clone https://github.com/janak1284/app_dev_v2.git`
2. Open the `android-client` folder in Android Studio.
3. Sync the Gradle project and ensure your Kotlin/Compose versions map correctly.
4. Build and install the `debug` variant to your device.

### Microservice Setup
1. Navigate to the `scraper-microservice` directory.
2. Run `npm install` to grab dependencies.
3. Create a `.env` file based on environment needs.
4. Run `npm start` (or `node server.js`) to launch the telemetry bridge.

---

## 📜 Recent Milestones (V3 -> V4)

- **V3 "High-Fidelity" Update:** Introduced the Turf-Java spatial engine, real-time route slicing, high-fidelity journey logging, and the dual-trigger Smart ETA system.
- **V4 "Lean Railway" Update:** Streamlined the multi-modal experience into a pure "On-Train" tracking mode. Introduced macro/micro line hybrid routing, bulletproof 2km triggers, and max-volume overrides for critical railway alerts. Implemented centralized `AppLogger` for unified debugging.

---

## ⚖️ License
Built for the Location Alarm Project. All rights reserved.

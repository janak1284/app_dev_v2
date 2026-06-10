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

## 🚗 Core Component: Roadway Routing Engine

The Roadway Routing Engine is optimized for buses, cars, and general road-based commutes. It ensures high-fidelity tracking that adheres exactly to the street network.

### 1. How Roadway Routing Works
When a user selects a destination, the app queries the **OSRM (Open Source Routing Machine) API** via the `PhotonApiService`. 
- **Path Generation:** OSRM returns a GeoJSON LineString representing the exact road path along with the expected distance, duration, and individual segment speeds (e.g., highway vs. local road).
- **Route Slicing (UI Layer):** As the user physically moves, the raw GPS coordinates are injected into **Turf-Java**, a robust spatial math library. The app uses `TurfMisc.nearestPointOnLine` to "snap" the user to the route and `TurfMisc.lineSlice` to dynamically truncate the visual polyline shown on the `MapLibre` map. The line literally shrinks as you approach your destination.
- **Deviation Handshake:** If the user deviates from the OSRM path by more than 100 meters (calculated via `TurfMeasurement`), the `LocationAlarmService` triggers a bidirectional handshake with the `MapViewModel` to request a fresh route, automatically healing the journey.

### 2. How ETA and Distance are Calculated
- **Distance:** Instead of checking a radial distance to the destination point, `RouteDistanceEngine` calculates the precise length of the sliced polyline from the user's snapped location to the destination using `TurfMeasurement.length`.
- **Calibrated ETA (Performance Ratio Engine):** The engine tracks the user's sliding average speed using an Exponential Moving Average (EMA). It compares this against the OSRM expected speed for the *specific road segment* the user is currently traversing. This creates an **Efficiency Ratio**. 
  - *Example:* If you are traveling at 40 km/h on a road where OSRM expects 80 km/h, your ratio is 0.5. The engine dynamically scales the base remaining time, ensuring highly accurate "road-aware" ETAs even in unexpected traffic.

### 3. How the Alarm is Triggered
The `LocationAlarmService` runs as a foreground service with a `WakeLock`. 
- **Dual-Trigger System:** Every time a valid GPS coordinate is received, the service updates the remaining distance and ETA. It evaluates two user-defined thresholds:
  1. **Distance Threshold:** e.g., "Wake me at 2 kilometers."
  2. **Predictive Threshold:** e.g., "Wake me 10 minutes before I arrive."
- If *either* threshold is breached, the service fires an Intent to `RingingActivity`, waking the screen and playing the selected ringtone via the `AlarmEngine`.

### 4. Data Saved for Reuse (High-Fidelity History)
Upon journey completion, the `HistoryRepository` commits the trip to a local Room Database. 
- It saves a high-fidelity **Breadcrumb Trail** of actual GPS locations (including speed and timestamp).
- When a user replays a saved route, the system bypasses the OSRM API entirely. It injects the previously traversed high-precision path directly into the tracking engine, ensuring 100% offline consistency for regular commutes.

---

## 🚆 Core Component: Live Railway Tracking Engine

The Railway Tracking Engine (V4) was built to solve the unique challenges of train travel: massive distances, poor connectivity, and inaccurate straight-line routing. 

### 1. The Scraper Microservice (Backend Server)
Because live train telemetry APIs are often locked or expensive, this project utilizes a custom headless Node.js microservice (`scraper-microservice`).
- **What it Scrapes:** It uses `Puppeteer/Playwright` to intercept real-time telemetry from `confirmtkt.com/train-running-status`.
- **How it Works:** The scraper navigates to the train's specific URL and extracts data using visual DOM indicators (e.g., green checkmarks for departed stations, blinking circles for current stations). 
- **Data Standardization:** The raw station names are mapped to exact standard coordinates via a massive local dictionary (`stations.json`). The server packages this into a clean JSON sequence (containing arrival times, delay statuses, and exact lat/lng coordinates) and returns it to the Android client.

### 2. Passing & Receiving from OpenRailRouting (ORR) API
To draw the track, the Android client uses the OpenRailRouting (ORR) API.
- **Micro vs Macro Routing:** Rendering a 1000km train path point-by-point crashes mobile renderers and causes ORR to timeout. The `MapViewModel` solves this using a **Hybrid Route Line**.
  - **Macro-Routing:** Distant stations are connected using straight lines to provide a general overview.
  - **Micro-Routing (Final Approach):** For the segments closest to the destination, the app queries ORR with the exact station coordinates. ORR returns high-precision track geometry. 
- **U-Turn & Auto-Snap Heuristics:** If station coordinates are slightly off, ORR might fail to find a connection. The app automatically triggers a fallback `getNearestPoint` call to snap the station to the nearest rail node, correcting bad data on the fly. It also filters out bizarre "U-turn" artifacts using straight-line validation thresholds.

### 3. Railway ETA, Distance & Alarms
- **Dynamic Smart Polling:** Because train journeys are long, the `LocationAlarmService` throttles GPS polling to save battery. If the destination is >150km away, it polls only once every 45 minutes. Once within 2km, it enters "Final Approach" and tightens polling to 1 second.
- **ETA Sync:** The client periodically pings the Node.js microservice to sync the official railway ETA and delay status, displaying this to the user instead of relying purely on local speed.
- **Distance Calculation:** As the train moves, Turf-Java snaps the GPS point to the ORR track geometry (if available) or the macro-line, providing highly accurate remaining distance.
- **The Bulletproof Trigger:** Railway alarms are critical. Regardless of user settings, the service implements a hardcoded **Bulletproof 2km Trigger**. If the train breaches the 2km radius of the destination station, the alarm guarantees activation.
- **Force Max Volume:** Because travelers often sleep deeply on trains and use headphones, the `AlarmEngine` bypasses "Do Not Disturb" modes and dynamically overrides the Android system volume to maximum specifically for railway arrivals.

---

## 🏗️ Project Architecture (Clean Architecture)
The client is structured to separate concerns entirely:
1. **View (UI Layer):** `ui/` directory containing Compose screens (`HomeScreen`, `MapScreen`, `SettingsScreen`) and Bottom Sheets.
2. **ViewModel (Logic Layer):** `MapViewModel.kt` acts as the bridge. It handles search debouncing, state flow management, API orchestration, and ORR hybrid stitching.
3. **Domain Layer:** `RouteDistanceEngine.kt` handles pure spatial math, polyline slicing, and ETA calibrations without Android dependencies.
4. **Data/Repository Layer:** DAOs, Entities, and Repositories handle local caching and Room database access.
5. **Service Layer:** `LocationAlarmService.kt` is the independent background worker that takes over once the journey begins, utilizing WakeLocks and Dead Reckoning.

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

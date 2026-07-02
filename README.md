# Location Alarm V4: The Smart Commuter Engine 📍⏰

**Location Alarm** is a next-generation, high-precision travel assistant and location-based alarm system designed for modern commuters. Moving beyond simple "as-the-crow-flies" GPS proximity alerts, this application employs a **Smart Routing Engine** to calculate actual road and railway distances, providing dynamic ETA tracking, predictive alarms, and fail-safe dead reckoning.

Whether navigating complex city streets or resting on a long-distance train, Location Alarm ensures you never miss your stop.

---

## 🌟 The Problem We Solve
Most traditional GPS alarm apps (like Naplarm, Wake Me There, or basic geofencing tools) rely on simple radial perimeters. You set a 1km circle around your destination, and when your phone enters that circle, the alarm rings. For **daily commuters**, this basic approach creates frustrating problems: 
1. A 1km radius might trigger 20 minutes too early if your bus is stuck in gridlock traffic.
2. It might trigger too early if the train tracks curve widely right before the station.
3. It completely fails underground or inside train cars where GPS signals drop.

**Location Alarm V4 is built differently specifically for the daily commuter:**
- **Dynamic Route & Traffic Awareness:** Instead of a dumb circle, the app snaps your location to the *actual road network or railway track*. It calculates an **Efficiency Ratio** based on your real-time speed versus the expected speed, dynamically adjusting your ETA. You can set it to wake you up "10 minutes before arrival," and it will adapt to traffic jams automatically.
- **Live Train Telemetry:** Rather than relying solely on your phone's weak GPS inside a train, the app actively scrapes live railway servers to sync official arrival times and delay statuses.
- **Dead Reckoning & Unstoppable Alarms:** If you lose signal in a tunnel, the app uses your last known speed to estimate progress. For critical train arrivals, it bypasses Android's Doze mode, overrides "Do Not Disturb," and forces the system volume to maximum to guarantee you wake up.

---

## 🚗 Core Feature: Roadway Routing Engine

The Roadway Routing Engine is optimized for buses, cars, and general road-based commutes. It ensures high-fidelity tracking that adheres exactly to the street network.

### 1. High-Fidelity Path Generation & Slicing
When a destination is selected, the app queries the **OSRM (Open Source Routing Machine) API** via `PhotonApiService`. This returns a precise GeoJSON LineString of the road network.
As you move, the app uses **Turf-Java** (a spatial math library) to "snap" your raw GPS coordinates to the route. It then uses `TurfMisc.lineSlice` to dynamically truncate the visual polyline on the map. The route line literally shrinks as you approach your destination, providing immediate visual feedback.

### 2. Smart ETA & Efficiency Ratio
Instead of simple distance checks, the `RouteDistanceEngine` calculates the precise length of the remaining sliced polyline.
To provide a highly accurate **Predictive ETA**, the engine calculates a real-time "Efficiency Ratio." It tracks your sliding average speed (using an Exponential Moving Average) and compares it against the OSRM's expected speed for that specific road segment. If you are stuck in traffic moving at half the expected speed, the ETA dynamically scales to reflect this reality.

### 3. Dual-Trigger Background Service
The `LocationAlarmService` runs as an unstoppable foreground service utilizing a `WakeLock`. It evaluates two user-defined thresholds simultaneously:
1. **Distance Threshold:** "Wake me at 2 kilometers."
2. **Predictive Time Threshold:** "Wake me 10 minutes before I arrive."
If *either* threshold is breached, the service fires an Intent to `RingingActivity`, bypassing Doze mode to wake the screen and play the alarm.

### 4. High-Fidelity Journey Replay
Completed trips are committed to a local Room Database via `HistoryRepository`. The app saves the actual breadcrumb trail of your GPS locations. If you replay a saved commute, the app bypasses the OSRM API entirely and injects your historical high-precision path into the tracking engine, ensuring 100% offline consistency.

---

## 🚆 Core Feature: Lean V4 Railway Tracking Engine

Train travel presents unique challenges: massive distances, poor connectivity, and inaccurate straight-line routing. The V4 Railway Engine was built specifically for the "already on train" commuter experience.

### 1. Scraper Microservice & Automatic Backup Failover
Live train telemetry APIs are often expensive or locked. To solve this, the project utilizes a custom headless **Node.js Microservice** (`server.js` & `scraper.js`):
- **Primary Scraper:** Uses Puppeteer/Playwright to actively scrape real-time telemetry from `confirmtkt.com/train-running-status`. The server maps raw station names to exact coordinates via a local dictionary (`stations.json`) and streams clean JSON data back to the Android client.
- **Backup Scraper & Automatic Failover:** Implemented `scrapeTrainTelemetryFallback(trainNumber)` querying `erail.in`. If the primary scraping engine encounters errors or timeouts (e.g., anti-bot blocks or missing data), the service automatically falls back to erail running status to ensure continuous commuter telemetry without downtime.

### 2. Station-to-Station Train Search & Optional Train Number Entry
The microservice exposes `scrapeTrainsBetweenStations(source, destination)` querying `erail.in` via `GET /api/v4/trains/search?source=X&destination=Y` to reliably retrieve trains operating between any station pair. The Android client connects to this via `RailwayTelemetryApi.searchTrains` and reactive `StateFlows` in `MapViewModel.kt`. 
Inside `RailwaySetupDialog.kt`, commuters have dual search options:
- **By Stations:** Users input Source and Destination station codes/names to scrape available trains, pick one from the results dropdown, and load live telemetry stations.
- **Direct Train Number:** Users can directly input a Train Number (marked as optional if searching) to skip web scraping and directly query live stations.

### 3. Hybrid Route Lines (Macro vs. Micro)
Rendering a 1000km train path point-by-point will crash a mobile map. The app uses a **Hybrid Route Line** system:
- **Macro-Routing:** Distant stations are connected with straight lines to provide a general overview.
- **Micro-Routing (Final Approach):** For segments closest to your destination, the app queries the **OpenRailRouting (ORR) API** to fetch high-precision track geometry, seamlessly stitching it into the macro line.

### 4. The "Bulletproof" Alarm Trigger
Railway alarms are critical. Regardless of user configuration, the service enforces a hardcoded **Bulletproof 2km Trigger**. If the train enters a 2km radius of the destination station, the alarm is guaranteed to fire. 
Additionally, because train travelers often sleep with headphones, the `AlarmEngine` dynamically overrides the Android system volume to maximum and bypasses "Do Not Disturb" specifically for railway arrivals.

---

## 🎮 Core Feature: High-Fidelity Simulation & Isolated Demo Mode

The V4.1 update introduced multi-modal offline simulation, allowing developers to simulate real-world municipal bus routes and high-speed express train routes without needing to physically travel.

### 1. Multi-Modal Simulation (Roadway & Railway)
- **Roadway GPX Simulation:** The app directly ingests native `.gpx` files (like municipal bus routes 555S & 55v), bypassing generic OSRM routing to recreate exact real-world telemetry at 30x time compression.
- **Isolated Railway Demo Mode:** Added `isRailwayDemoEnabled` setting to `SettingsDataStore.kt` and a UI toggle in `SettingsScreen.kt` under "Demo & Simulation Modes". Powered by `RailwayMockLocationRepositoryImpl.kt`, it simulates a high-speed train approaching the chosen destination station at ~400m/sec compressed demonstration speed.

### 2. Complete Isolation & Proxy Architecture
**Crucially, the simulation code is completely isolated and does not affect the production user experience.** 
The app utilizes a Proxy Design Pattern via `ProxyLocationRepositoryImpl.kt`:
- When Demo Mode is disabled in settings, the app seamlessly bypasses simulation logic entirely and delegates location requests directly to the native `LocationTrackingManager` (real GPS). 
- Mock location injection is strictly gated by `transportMode == RAILWAY` and `isRailwayDemoEnabled` (or Roadway demo settings using Kotlin Flow's `flatMapLatest`). Neither mock repository is instantiated or executed in the background unless explicitly toggled on, guaranteeing **zero performance overhead** or interference with real roadway or live GPS user experiences.

---

## 🏗️ Project Architecture (Clean Architecture)
The Android client is structured to separate concerns entirely:
1. **View (UI Layer):** Jetpack Compose screens (`HomeScreen`, `MapScreen`, `SettingsScreen`) with deep dark aesthetics and dynamic routing cards.
2. **ViewModel (Logic Layer):** `MapViewModel.kt` handles state flow management, API orchestration, and ORR hybrid stitching.
3. **Domain Layer:** `RouteDistanceEngine.kt` handles pure spatial math and ETA calibrations, completely decoupled from Android dependencies.
4. **Data Layer:** Room Database (`v2.7.0-rc01` via KSP) handles local caching and journey history.
5. **Service Layer:** `LocationAlarmService.kt` manages background execution, WakeLocks, and dead reckoning algorithms.
6. **Logging:** App-wide logging is standardized through a centralized `AppLogger` utility for clean debugging.

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Jellyfish (or newer).
- An Android device running Android 8.0 (Oreo) or higher.
- Node.js (for running the scraper microservice locally).

### Client Installation
1. Clone the repository: `git clone https://github.com/janak1284/app_dev_v2.git`
2. Open the `android-client` folder in Android Studio.
3. Sync the Gradle project (ensure KSP is configured correctly).
4. Build and install the `debug` variant to your device.

### Microservice Setup
1. Navigate to the `scraper-microservice` directory.
2. Run `npm install` to grab dependencies.
3. Create a `.env` file based on environment needs.
4. Run `npm start` (or `node server.js`) to launch the telemetry bridge.

---

## ⚖️ License
Built for the Location Alarm Project. All rights reserved.

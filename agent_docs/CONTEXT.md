# Project Context: Location Alarm V2 (Smart Commuter Engine)

## 1. Project Overview
The "Location Alarm" is an intelligent, route-based Android commuter application. Moving beyond a simple proximity trigger (MVP), V2 operates as a predictive navigation engine. It calculates actual road distance using open-source routing, snaps real-time GPS coordinates to a designated path, calculates dynamic ETAs based on sliding average speeds, and allows users to save and reuse historical journey breadcrumb trails.

## 2. Knowledge Base & Tech Stack
- **Architecture:** Clean Architecture, MVVM (Model-View-ViewModel) with Jetpack Compose UI.
- **Local Storage Layer:** Room (SQLite) Database for relational, structured route data.
- **Network & Routing:** OSRM (Open Source Routing Machine) public API via Retrofit and Kotlinx Serialization.
- **Spatial Mathematics:** Turf-Java for complex on-device spatial calculations (cross-track distance, polyline slicing).
- **Map Rendering:** MapLibre Native for rendering GeoJSON `LineString` paths and markers.
- **Core Components:**
  - `LocationAlarmService`: An active state machine (Foreground Service) that tracks location, buffers GPS breadcrumbs during the trip, and monitors the 100m route deviation limit.
  - `LocationTrackingManager`: Wraps the `FusedLocationProviderClient`, providing updates via Kotlin `Flow`.
  - `RouteDistanceEngine` (New): Handles Turf-Java calculations to find distance remaining *along* the route and calculates dynamic ETAs using an Exponential Moving Average (EMA) of user speed.
  - `AlarmEngine`: Logic for triggering the hardware-level alarm UI (Sound & Haptics).
  - `RingingActivity`: The high-priority wake-up screen that survives background execution.
- **Terminology Base:**
  - **Distance Alarm:** Replaces "Guard" / Proximity alarm. Alerts user when *X* km/m remaining along the route.
  - **Time Alarm:** Replaces "Backup Alarm". Alerts user when dynamic ETA is less than *X* minutes.

## 3. Current State (V2 Pivot Active)
- **Implemented (from MVP):**
  - Base Foreground Service (`foregroundServiceType="location"`) with `WakeLock` usage to prevent deep sleep deaths.
  - Core MapLibre UI integration with `LocalFocusManager` for smart search bar dismissals.
  - Hardware-level alarm bypassing standard notifications.
  - "Deep Dark" Material 3 UI.
- **Currently in Development (The V2 Pivot):**
  - Shifting from Haversine (point-to-point) distance to Route-based distance.
  - Integrating Turf-Java to snap raw GPS coordinates to OSRM GeoJSON paths.
  - Replacing volatile `SharedPreferences` history with a robust Room SQLite architecture.
  - Refactoring UI terminology (Distance vs. Time alarms).

## 4. Database Architecture (Room)
The data layer treats journeys as first-class relational entities:
- **`SavedRoute` (Metadata Entity):** Contains `routeId`, `destinationName`, `targetTime`, and `dateSaved`. Feeds the Home Screen UI.
- **`RouteBreadcrumb` (GPS Trail Entity):** Contains `pointId`, `routeId` (Foreign Key), `latitude`, `longitude`, `speed`, and `timestamp`. Used to save actual paths taken by the user.

## 5. Team & Branching Strategy
The project is actively developed by two engineers using a lightweight feature-branching workflow (`GitHub Flow` style) against a shared `v2-develop` integration branch:
- **Developer 1 (Backend & Spatial):** Focuses on Room DB Architecture, Turf-Java spatial math, Service state machine buffering, and dynamic ETA algorithms.
- **Developer 2 (Network & UI):** Focuses on OSRM Retrofit integration, MapLibre GeoJSON rendering, Home Screen Compose UI, and Journey Summary sheets.
- **Handshake Protocol:** Parallel development is unblocked by agreeing on Data Classes (`SavedRoute`, `LineString`) and creating mock repositories while backend wires up the actual logic.

## 6. Goals Remaining (Execution Roadmap)
- **Phase 1 (Data):** Establish Room DAOs, Entities, and Repositories for bulk inserting route breadcrumbs.
- **Phase 2 (Network):** Build OSRM Retrofit calls to fetch `/driving/` routes and parse GeoJSON. Render `LineString` in MapLibre.
- **Phase 3 (Spatial Math):** Implement Turf-Java to snap GPS points to the polyline, calculate distance remaining, and trigger route recalculation if the user deviates >100m.
- **Phase 4 (Service Overhaul):** Update `LocationAlarmService` to buffer GPS coordinates into a `MutableList` and keep running post-alarm until the user explicitly hits "End Journey".
- **Phase 5 (UI Refactor):** Build the new Home Screen for saved routes, and the Bottom Sheet prompt to save breadcrumb trails upon journey completion.

## 7. Assumptions & Constraints
- Application logic must rely exclusively on Free and Open Source Software (FOSS) dependencies (OSRM, Turf, MapLibre) to avoid API rate limiting and billing.
- The device must have Google Play Services installed for `FusedLocationProviderClient`.
- Application requires `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `FOREGROUND_SERVICE_LOCATION`, and `POST_NOTIFICATIONS` permissions.
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
  - `RouteDistanceEngine`: Handles Turf-Java calculations to find distance remaining *along* the route and calculates dynamic ETAs using an Exponential Moving Average (EMA) of user speed.
  - `AlarmEngine`: Logic for triggering the hardware-level alarm UI (Sound & Haptics).
  - `RingingActivity`: The high-priority wake-up screen that survives background execution.
- **Terminology Base:**
  - **Distance Alarm:** Alerts user when *X* km/m remaining along the route.
  - **Time Alarm:** Alerts user when dynamic ETA is less than *X* minutes.

## 3. Current State (V2 Pivot Active)
- **Implemented (V2 Pivot Core):**
  - **Room Database:** Relational schema with `SavedRoute` and `RouteBreadcrumb` entities, DAOs, and Repository.
  - **Spatial Engine:** `RouteDistanceEngine` using Turf-Java for snapping, deviation triggers, route slicing, EMA speed, and dynamic ETA.
  - **Service Overhaul:** `LocationAlarmService` now supports route-based tracking, OSRM GeoJSON parsing, GPS buffering, and automatic journey persistence to Room.
  - **Network Handshake:** `MapViewModel` successfully fetches OSRM routes and communicates them to the background service.
  - **Terminology Scrub:** Core logic renamed to "Distance/Time Alarm".
- **Currently in Development:**
  - **Phase 5:** UI Refactoring (Home Screen, History, and Journey Summary).
  - **Handshake Integration:** Ensuring seamless communication between the Service and the new UI layers.

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
- **Phase 5 (UI Refactor):** Build the new Home Screen for saved routes, and the Bottom Sheet prompt to save breadcrumb trails upon journey completion.
- **Verification:** Full field testing and spatial math tuning.

## 7. Assumptions & Constraints
- Application logic must rely exclusively on Free and Open Source Software (FOSS) dependencies (OSRM, Turf, MapLibre) to avoid API rate limiting and billing.
- The device must have Google Play Services installed for `FusedLocationProviderClient`.
- Application requires `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `FOREGROUND_SERVICE_LOCATION`, and `POST_NOTIFICATIONS` permissions.

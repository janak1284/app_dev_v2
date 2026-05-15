# Product Requirements: Location Alarm V2 (Smart Commuter Engine)

## Vision

An intelligent, route-based commuter navigation engine that ensures users never miss their stop. Moving beyond simple point-to-point proximity, V2 calculates actual road distance, predicts dynamic ETAs based on real-time travel speeds, and allows users to save and reuse historical journey paths.

---

## Primary User

Commuters (students and professionals) taking complex or winding transit routes (buses, trains) who need highly reliable, false-positive-free wake-up alerts.

---

## Must-Have Features (V2 Pivot)

1. **Route-Based Distance Tracking:** Fetch driving/transit paths via OSRM and calculate remaining distance *along the route* (resolving the V-shaped junction flaw).
2. **Dynamic ETA Prediction:** Calculate a sliding average speed (Exponential Moving Average) to provide real-time arrival estimates adapted to traffic or delays.
3. **Smart Alarms:** * **Distance Alarm:** Trigger alert when exactly *X* distance remains on the path.
    * **Time Alarm:** Trigger alert when the dynamic ETA drops below *X* minutes.
4. **Active Journey Recording:** Buffer GPS breadcrumbs during the trip and prompt the user to save the actual traveled route upon completion.
5. **Local Database (Room):** Store historical `SavedRoutes` and `RouteBreadcrumbs` for reuse via a new Home Screen interface.
6. **Robust Background Execution:** Maintain a Foreground Service with WakeLocks to track location, run Turf-Java spatial math, and trigger hardware-level alarms even in deep sleep.

---

## Constraints

* **Team:** Two-developer project (Dev 1: Backend/Spatial, Dev 2: Network/UI).
* **Platform:** Android Native (Kotlin & Jetpack Compose).
* **Infrastructure:** Zero external backend/server architecture. All user data must be stored locally on-device.
* **Cost & Dependencies:** 100% Free and Open Source dependencies. Strictly no paid mapping or routing APIs (Must use MapLibre, OSRM, and Turf-Java).
* **Battery Life:** Continuous foreground location polling and spatial math must be optimized to keep battery drain under 10-15%/hour.

---

## Success Metrics

* **Zero False Positives:** Alarms must not trigger early due to winding roads or U-shaped transit loops.
* **Deviation Resilience:** System successfully detects >100m cross-track deviation and automatically recalculates the OSRM route.
* **ETA Accuracy:** Dynamic ETA remains stable and accurately reflects the commuter's actual progression speed.
* **Reliability:** Alarm triggers reliably across all device sleep states. Wake-up success rate > 95%.

---

## Definition of Done

* Room DB schema implemented and successfully persisting batch GPS trails.
* OSRM Retrofit integration successfully fetching and parsing GeoJSON `LineString` data.
* MapLibre dynamically rendering the requested route and user position.
* Turf-Java math verified to correctly snap GPS points and calculate remaining line length.
* App successfully field-tested on a non-linear, real-world commute.
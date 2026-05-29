# Product Requirements: Location Alarm V2 (Smart Commuter Engine)

## Vision

An intelligent, route-based commuter navigation engine that ensures users never miss their stop. Moving beyond simple point-to-point proximity, V2 calculates actual road distance, predicts dynamic ETAs based on real-time travel speeds, and allows users to save and reuse historical journey paths.

---

## Primary User

Commuters (students and professionals) taking complex or winding transit routes (buses, trains) who need highly reliable, false-positive-free wake-up alerts.

---

## Must-Have Features (V2 Pivot)

1. **Route-Based Distance Tracking:** Fetch driving/transit paths via OSRM (Road) or Valhalla/OTP (Transit) and calculate remaining distance *along the route*.
2. **Multi-Modal Transit Support:** Support for railway and bus journeys. When initiating a journey, the user must be asked to select their transport mode (Road vs. Transit).
3. **Leg-Based Tracking & Transfer Alarms:** For transit journeys, break the route into "Legs" (e.g., Walk → Train → Bus). The system must track distance/ETA to the *end of the current leg* and trigger an alarm to wake the user before each transfer point.
4. **Dynamic ETA Prediction:** Calculate a sliding average speed (Exponential Moving Average) to provide real-time arrival estimates adapted to traffic or delays.
5. **Smart Alarms:**
    * **Distance Alarm:** Trigger alert when exactly *X* distance remains on the path (or leg).
    * **Time Alarm:** Trigger alert when the dynamic ETA drops below *X* minutes.
6. **Active Journey Recording:** Buffer GPS breadcrumbs during the trip and prompt the user to save the actual traveled route upon completion.
7. **High-Fidelity Route Reuse:** Automatically store the **Actual Path (GeoJSON)**, **Actual Distance**, and **Actual Duration** for every journey. When a route is reused, the app should leverage this pre-recorded path for maximum precision, bypassing the OSRM API.
8. **Local Database (Room):** Store historical `SavedRoutes` and `RouteBreadcrumbs` for reuse via a new Home Screen interface.
9. **Robust Background Execution:** Maintain a Foreground Service with WakeLocks to track location, run Turf-Java spatial math, and trigger hardware-level alarms even in deep sleep.

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
# Tech Stack & Tools: Location Alarm V2 (Smart Commuter Engine)

## Core Stack

* **Language:** Kotlin
* **Platform:** Android API 26+ (Targeting modern Android)
* **UI Framework:** Jetpack Compose (Material 3)
* **Map Rendering:** MapLibre Native (GeoJSON rendering)
* **Location Tracking:** FusedLocationProviderClient
* **Local Storage (New):** Room (SQLite Database)
* **Networking (New):** Retrofit + OkHttp
* **Serialization (New):** kotlinx.serialization (for OSRM GeoJSON)
* **Spatial Math (New):** Turf-Java (for off-grid routing calculations)
* **Architecture:** Clean Architecture (UI → ViewModel → Repository → Service/Network/DB)

---

## Location Update Pattern (V2)

Location tracking is now a continuous recording engine:
* **Active Journey:** Frequent updates required for accurate Turf-Java math and ETA calculations.
* **Buffering:** Keep a `MutableList<Location>` in memory during the trip. 
* **Persistence:** Only batch-insert the buffered coordinates into Room when the user explicitly triggers "End Journey".

---

## Distance Calculation Pattern (V2)

**DEPRECATED:** `Location.distanceBetween()` (Straight-line Haversine triggers false positives).

**NEW ROUTE-BASED PATTERN (Turf-Java):**
1. **Snap:** Snap the incoming raw GPS coordinate to the nearest point on the OSRM `LineString`.
2. **Check Deviation:** Calculate cross-track distance. If deviation > 100m, trigger Retrofit to fetch a new route.
3. **Calculate Remaining:** Slice the polyline from the snapped point to the destination and calculate the length.
4. **Trigger Alarm:** Trigger when `remaining_route_distance <= threshold`.

---

## Permissions Required

* `ACCESS_FINE_LOCATION` (Required for Turf-Java accuracy)
* `ACCESS_COARSE_LOCATION`
* `ACCESS_BACKGROUND_LOCATION`
* `FOREGROUND_SERVICE_LOCATION` (Required for Android 14+)
* `POST_NOTIFICATIONS` (Required for Android 13+)
* `WAKE_LOCK` (Required to keep the CPU awake for spatial math during deep sleep)

---

## Suggested Project Structure (V2)

    app/
    ├── alarm/        # AlarmEngine, Receivers, RingingActivity
    ├── data/         # Room Database, Entities, DAOs, Repositories
    ├── domain/       # Turf-Java Engine, Dynamic ETA Calculators
    ├── location/     # LocationTrackingManager
    ├── network/      # Retrofit API Interfaces (OSRM)
    ├── service/      # LocationAlarmService (State Machine)
    ├── ui/           # Compose Screens (Home, Map, Configuration)
    └── viewmodel/    # MapViewModel, HomeViewModel

---

## Naming Conventions

* **Database:** `*Entity` (e.g., `SavedRouteEntity`), `*Dao` (e.g., `RouteDao`)
* **Network:** `*Api` or `*Service` (e.g., `OsrmApiService`)
* **Domain:** `*Engine` or `*Calculator` (e.g., `RouteDistanceEngine`)
* **ViewModels:** `*ViewModel`
* **UI Screens:** `*Screen` (e.g., `HomeScreen`, `MapScreen`)

---

## Error Handling Rules

In addition to standard error handling, V2 must explicitly handle:

* **Network Drops:** If the OSRM API fails or connection drops, the `RouteDistanceEngine` MUST gracefully fall back to Haversine (`Location.distanceBetween`) so the alarm doesn't fail.
* **GPS Drift:** Ignore location updates with high inaccuracy radii to prevent Turf-Java from snapping the user to an incorrect parallel road.
* **Database IO:** Never run Room DB inserts or large Turf-Java polyline slicing on the Main UI thread.
# Code Patterns: Location Alarm V2 (Smart Commuter Engine)

## 1. UI Layer (Jetpack Compose)
* **Strict State Observation:** The UI must be a pure function of state. Use `collectAsStateWithLifecycle()` to observe ViewModel `StateFlow`s and Room `Flow`s.
* **No Business Logic:** Composables should only emit user intents (e.g., `viewModel.onActivateAlarmClicked()`). Never perform spatial math, date formatting, or network calls inside a Composable.
* **MapLibre Delegation:** The map UI purely consumes the `LineString` (GeoJSON) provided by the ViewModel. It does not calculate routes.

## 2. ViewModel Layer
* **The Orchestrator:** The ViewModel coordinates between the UI, the Room Repository, and the Service.
* **State Ownership:** Owns the `DestinationState`, `RouteState` (OSRM GeoJSON), `JourneyState` (Dynamic ETA, Remaining Distance), and `ServiceStatus`.
* **Data Contracts:** Exposes mockable interface contracts (e.g., `Flow<List<SavedRoute>>`) so frontend UI development is never blocked by backend database work.

## 3. Storage Layer (Room DB)
* **Relational Entities:** Treat journeys as structured data. Use `SavedRoute` for metadata and `RouteBreadcrumb` for GPS trails. 
* **Repository Pattern:** The `RouteRepository` abstracts the Room DAOs. ViewModels and Services never interact with DAOs directly.
* **Batch Operations:** Always use batch transactions (`@Insert(onConflict = REPLACE)`) when saving the buffered GPS breadcrumb trail to prevent blocking the Main/IO threads. Do not write massive coordinate lists to `SharedPreferences`.

## 4. Network Layer (OSRM & Retrofit)
* **Single Source of Truth for Paths:** Always request paths via Retrofit to the `router.project-osrm.org` API.
* **Serialization:** Use `kotlinx.serialization` to cleanly parse the GeoJSON `LineString` response.
* **Failure Handling:** Implement robust `try/catch` and fallback logic. If the network drops, the app must gracefully revert to local Haversine distance until the connection returns.

## 5. Spatial Engine (Turf-Java)
* **Offload the Math:** Never write raw Haversine or cross-track formulas manually. Rely on `Turf-Java`.
* **The Snapping Rule:** Raw GPS coordinates must always be snapped to the nearest point on the OSRM `LineString` before calculating remaining distance.
* **Deviation Trigger:** If the Turf cross-track distance between the raw GPS point and the polyline exceeds `100m`, the engine must trigger a route recalculation.

## 6. Service State Machine (Foreground Service)
* **Continuous Buffering:** The `LocationAlarmService` must maintain an active `MutableList<Location>` (the breadcrumb buffer) while tracking.
* **The "End Journey" Lifecycle:** The state sequence is `TRACKING` → `ALARMING` → `ARRIVED_PENDING`. The location collectors *must continue running* after the alarm fires so the user's final path is recorded. The service only stops when the user explicitly triggers an "End Journey" intent.
* **WakeLocks:** Ensure CPU WakeLocks are maintained during tracking to prevent Turf-Java calculations from dying during deep sleep.

## 7. Alarm Logic Engine
* **Dynamic Triggers:** * *Distance Alarm:* Triggers when `Turf_Remaining_Distance <= User_Threshold`.
  * *Time Alarm:* Triggers when `(Turf_Remaining_Distance / Sliding_Average_Speed) <= User_Threshold_Minutes`.
* **Hardware Execution:** The `AlarmEngine` bypasses standard notifications to trigger high-priority `RingtoneManager` audio and `Vibrator` haptic loops via `RingingActivity`.
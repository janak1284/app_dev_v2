# Testing Strategy: Location Alarm V2 (Smart Commuter Engine)

Since V2 relies heavily on spatial mathematics, background state machines, and continuous data buffering, testing must be significantly more rigorous than the MVP. 

## 1. Spatial Math & Routing Tests (Turf-Java + OSRM)
Since walking testing is inefficient for route-based deviations, use the **Android Emulator's Route Playback (GPX/KML files)** for these tests.

* **Route Snapping Test:** Feed a GPX route with slight GPS noise. Verify Turf-Java correctly snaps the noisy points to the clean OSRM `LineString`.
* **The 100m Deviation Trigger:** 1. Start a route.
    2. Manually override the emulator's location to be >150m away from the active polyline.
    3. Verify the app automatically drops the old route and triggers a new OSRM Retrofit call.
* **Network Drop Fallback:** Disconnect the device's internet mid-journey. Verify the app smoothly falls back to Haversine (straight-line) distance for the alarm trigger.

## 2. Dynamic ETA & Service Buffering Tests
* **Sliding Speed Test:** Simulate a route where the speed drops from 60km/h to 10km/h (traffic jam). Verify the ETA dynamically increases and the "Time Alarm" does not trigger prematurely.
* **Breadcrumb Buffer Test:** Run the app for a 30-minute simulated commute. Verify the `LocationAlarmService` memory footprint remains stable and the `MutableList` successfully holds the coordinates without throwing OOM (Out of Memory) exceptions.

## 3. Background State Machine Tests (Deep Sleep)
* **WakeLock Verification:** Start tracking, lock the screen, and force the device into deep sleep via adb (`adb shell dumpsys deviceidle force-idle`). Verify Turf-Java calculations continue to run and the alarm still fires.
* **Post-Alarm State:** Trigger the alarm. Verify the location service *does not stop*. It must transition to `ARRIVED_PENDING` and continue buffering coordinates until the user explicitly clicks "End Journey".

## 4. Data Persistence Tests (Room DB)
* **Batch Insert Performance:** Complete a 1-hour simulated journey. Click "Save Route". Verify the UI does not freeze (Main Thread block) while Room batch-inserts the 1,000+ `RouteBreadcrumb` entities.
* **Home Screen Reactivity:** Save a new route. Verify the Jetpack Compose Home Screen automatically updates via the Room `Flow` without requiring a manual refresh.

## 5. The V2 Verification Loop (For Both Developers)
Before merging any feature branch into `v2-develop`, developers must execute this loop:

1. **Build & Mock:** If Dev 2 (UI) is waiting on Dev 1 (DB), run the UI with the mock Repository.
2. **Emulator Playback:** Load a standard `.gpx` commuter file into the Android Emulator.
3. **Logcat Monitoring:** Filter Logcat for `TurfEngine` and `RouteBuffer` to ensure calculations aren't skipping beats.
4. **Fix & Push:** Resolve any crashes or memory leaks before opening a Pull Request.
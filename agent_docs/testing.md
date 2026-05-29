# Testing Strategy: Location Alarm V2 (Smart Commuter Engine)

Since V2 relies heavily on spatial mathematics, background state machines, and continuous data buffering, testing must be significantly more rigorous than the MVP. 

## 1. Spatial Math & Routing Tests (Turf-Java + OSRM)
Since walking testing is inefficient for route-based deviations, use the **Android Emulator's Route Playback (GPX/KML files)** for these tests.

* **Route Snapping Test:** Feed a GPX route with slight GPS noise. Verify Turf-Java correctly snaps the noisy points to the clean OSRM `LineString`.
* **The 100m Deviation Trigger:** 1. Start a route.
    1. Manually override the emulator's location to be >150m away from the active polyline.
    2. Verify the app automatically drops the old route and triggers a new OSRM Retrofit call.
* **Network Drop Fallback:** Disconnect the device's internet mid-journey. Verify the app smoothly falls back to Haversine (straight-line) distance for the alarm trigger.
* **Route Slicing Test:** During GPX playback, verify the blue route line on the map visually shortens from the user's position towards the destination.
* **Center Lock Mode:** Click the "Focus on User" button. Verify the map auto-follows the user during playback. Manually drag the map and verify the lock disengages (button turns white).

## 2. Dynamic ETA & Service Buffering Tests
* **Sliding Speed Test:** Simulate a route where the speed drops from 60km/h to 10km/h (traffic jam). Verify the ETA dynamically increases and the "Time Alarm" does not trigger prematurely.
* **Calibrated ETA Verification:** Compare the ETA displayed in the UI with the actual time remaining in the GPX playback (at 1x speed).
* **Breadcrumb Buffer Test:** Run the app for a 30-minute simulated commute. Verify the `LocationAlarmService` memory footprint remains stable and the `MutableList` successfully holds the coordinates without throwing OOM (Out of Memory) exceptions.

## 3. Background State Machine Tests (Deep Sleep)
* **WakeLock Verification:** Start tracking, lock the screen, and force the device into deep sleep via adb (`adb shell dumpsys deviceidle force-idle`). Verify Turf-Java calculations continue to run and the alarm still fires.
* **Auto-Arrival Save Prompt:** Simulate arrival (<= 50m). Verify the "Save Journey" dialog appears immediately.

## 4. Data Persistence & Reuse Tests (Room DB)
* **High-Fidelity Saving:** Save a completed journey. Use a database inspector (or the History screen) to verify `durationMillis`, `actualDistanceMeters`, and `actualRouteGeoJson` are populated.
* **Path Reuse Test:** 1. Save a journey. 2. Re-activate it from "Saved Routes". 3. Verify the map loads the route instantly and the path matches the previous trip exactly, with no OSRM API call.
* **Batch Insert Performance:** Complete a 1-hour simulated journey. Click "Save Route". Verify the UI does not freeze (Main Thread block) while Room batch-inserts the 1,000+ `RouteBreadcrumb` entities.
* **Home Screen Reactivity:** Save a new route. Verify the Jetpack Compose Home Screen automatically updates via the Room `Flow` without requiring a manual refresh.

## 5. The V2 Verification Loop (For Both Developers)
Before merging any feature branch into `v2-develop`, developers must execute this loop:

1. **Build & Mock:** If Dev 2 (UI) is waiting on Dev 1 (DB), run the UI with the mock Repository.
2. **Emulator Playback:** Load a standard `.gpx` commuter file into the Android Emulator.
3. **Logcat Monitoring:** Filter Logcat for `TurfEngine` and `RouteBuffer` to ensure calculations aren't skipping beats.
4. **Fix & Push:** Resolve any crashes or memory leaks before opening a Pull Request.
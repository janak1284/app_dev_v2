# Pending Tasks (V2 Phase 8: Refinement)

## High Priority Fixes
- [x] **Bug: 5km Saving Limit:** Fixed by implementing service state recovery, copying location objects, and chunking DB insertions.
- [x] **Arrival Prompt Immediacy:** Tightened handshake between Service and ViewModel for instant UI reaction.
- [x] **Saved Journey UI:** Redesigned cards and screens with rich metrics (distance, duration, alarm badges).

## UI Enhancements
- [x] **Search History Preview:** Fix the navigation logic to ensure clicking a search history entry correctly triggers a map route preview.
- [x] **Selection Mode (Long Press):** Implement a long-press selection mode in both `SavedRoutesScreen` and `JourneyHistoryScreen` to allow bulk deletion.
- [x] **Journey Preview State:** Implemented a distinct "PREVIEW" mode where users can see the route and ETA before starting the alarm.
- [x] **One-Click Reactivation:** Users can now click any History or Saved item to instantly start a journey without searching.
- [x] **Edit Mode (Saved Routes):** Implement the edit logic to allow renaming and re-configuring existing `SavedRoute` entries.
- [x] **Keyboard Input Optimization:** Adjust UI layouts for text input fields (e.g., naming routes) using `imePadding` and scrollable containers to prevent obscuring.
- [x] **Real-Time Route Slicing:** Visually update the route line on the map to reflect user progress.
- [x] **Center Lock Mode:** Add a toggleable auto-follow camera mode for the user location.
- [x] **Arrival Save Prompt:** Automatically prompt the user to save the journey when reaching 50m from the destination.

## Bug Fixes & Verification
- [x] **Route Line Preview:** Fixed the issue where route lines failed to render when opened from history/saved routes.
- [x] **Performance Ratio ETA:** Implemented the "Performance Ratio" model to scale OSRM base time by real-time efficiency for humanized arrival estimates.
- [x] **GPX Simulation:** Performed simulated trips to verify ETA accuracy and route snapping.
- [x] **High-Fidelity Path Saving:** Ensure the actual path taken (GeoJSON) is saved and reusable for future journeys.
- [x] **Arrival Stability:** Fixed a fatal crash in the `Turf` library when slicing a route at the exact destination point.
- [x] **Tracking Persistence:** Fixed bug where tracking stopped when the alarm was dismissed; tracking now continues until arrival.
- [x] **Final Field Testing:** Perform real-world verification of the predictive routing engine, spatial calculations, and alarm triggers.

## V3 Multi-Modal Transition
- [x] **Phase 1: Transit Data Foundation:** Implemented `JourneyLeg` entities, OpenRailRouting API client, and Repository integration.
- [x] **Phase 2: The Multi-Modal Engine:** Refactor `RouteDistanceEngine` and `LocationAlarmService` for leg-based tracking.
    - [x] **Optimized Multi-Modal Logic:** Implemented "Road-Rail-Road" stitching using OSRM and OpenRailRouting.
    - [x] **Smart Station Selection:** Algorithm evaluates top 3 candidate stations via actual road distance (OSRM parallel calls).
    - [x] Enhanced `LocationAlarmService` state machine with `ALARM_TRANSFER`, `WAITING_FOR_CONNECTION`, and `RECALCULATING` states.
    - [x] Implemented logic to detect arrival at the end of a non-final leg and transition to the next leg.
    - [x] **Dynamic Alarm Reset:** Implemented logic to reset the alarm state if distance/ETA increases significantly.
- [x] **Phase 3: UI/UX for Multi-Modal Journeys:** Transit mode selection and multi-leg map rendering.
    - [x] **Optimized Loading UI:** Added `CircularProgressIndicator` overlay during route calculations.
    - [x] **Multi-Color Polylines:** Road (Blue), Rail (Black), Walk (Grey).
    - [x] **Transfer Station Markers:** Visualized mode transition points on the map with station markers.
- [x] **Phase 4: Routing Algorithm Refinement:**
    - [x] **Strict Transit Evaluation:** Implemented 1.5x thresholding and directionality bearing checks to penalize illogical transit routes.
    - [x] **Suboptimal Warnings:** Routes exceeding duration thresholds are now allowed but marked with a Toast notification.
    - [x] **Comprehensive Candidate Evaluation:** Increased station search radius and candidate count for improved path linking between origin/destination.

## Future Improvements (Post-V2)
- [ ] **Optimize Rail Multi-Modal Engine:** Evaluate more candidates, NxN matrix options, and walking-to-station logic.
- [ ] **Real-world field-testing and accuracy calibration.**
- [x] **MapViewModel Refactoring & Error Fix:** Resolved compilation errors and type mismatches resulting from the failed UI state consolidation refactor, ensuring stable state management for multi-modal routing engine.

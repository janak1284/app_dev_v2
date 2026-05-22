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
- [x] **Speed-Adjusted ETA:** Implemented the "Speed Ratio ETA" calculation ($\text{User Avg Speed} / \text{OSRM Expected Speed}$) to provide road-aware arrival estimates.
- [x] **GPX Simulation:** Performed simulated trips to verify ETA accuracy and route snapping.
- [x] **High-Fidelity Path Saving:** Ensure the actual path taken (GeoJSON) is saved and reusable for future journeys.
- [x] **Arrival Stability:** Fixed a fatal crash in the `Turf` library when slicing a route at the exact destination point.
- [x] **Tracking Persistence:** Fixed bug where tracking stopped when the alarm was dismissed; tracking now continues until arrival.
- [x] **Final Field Testing:** Perform real-world verification of the predictive routing engine, spatial calculations, and alarm triggers.

## V3 Multi-Modal Transition
- [x] **Phase 1: Transit Data Foundation:** Implemented `JourneyLeg` entities, Valhalla API client, and Repository integration.
- [ ] **Phase 2: The Multi-Modal Engine:** Refactor `RouteDistanceEngine` and `LocationAlarmService` for leg-based tracking.
- [ ] **Phase 3: UI/UX for Multi-Modal Journeys:** Transit mode selection and multi-leg map rendering.

## Future Improvements (Post-V2)
- [x] **Segment-Aware Speed Ratio Correction:** Implemented "Stateless Correction" model using OSRM speed annotations to handle mixed road types accurately.
- [x] **Battery-saver mode (Smart Polling based on distance):** Implemented dynamic location update intervals (30s to 2s) based on proximity to destination.
- [ ] **Real-world field testing and accuracy calibration.**

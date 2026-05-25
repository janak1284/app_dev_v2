# Version 3: Multi-Modal Transit Engine

## Vision
Version 3 (V3) transforms the application from a roadway-centric alarm into a comprehensive **Multi-Modal Transit Engine**. It introduces the capability to handle complex journeys involving trains and road legs, ensuring users are woken up not just for their final destination, but for every critical transfer point. 

Crucially, V3 adapts to the realities of public transit by gracefully handling GPS degradation, optimizing battery life during long journeys, and reacting to real-time delays.

---

## Technical Architecture Changes

### 1. Multi-Modal Routing Engine (Optimized)
- **Primary Rail Provider:** [OpenRailRouting](https://routing.openrailrouting.org/) (GraphHopper-based).
- **Multi-Modal Workflow:** Implements a three-stage "Road -> Rail -> Road" journey:
    1.  **First Mile:** OSRM (Road) from current location to the best start station.
    2.  **Long Haul:** OpenRailRouting (Rail) between optimal start/end stations.
    3.  **Last Mile:** OSRM (Road) from the end station to the final destination.
- **Smart Station Selection:** Concurrently evaluates the top 3 physically closest stations (via Photon) and selects the pair that minimizes actual road commute time (evaluated via OSRM).

### 2. Leg-Based Tracking Model
- **`JourneyLeg` Entity:** Represents a specific segment (ROAD or TRAIN).
- **Properties:** Transport mode, geometry (GeoJSON LineString), start/end coordinates, and estimated duration.
- **State Machine:** Combined into a single seamless route for high-precision tracking while maintaining segment metadata for UI and multi-color rendering.

### 3. Intermediate Transfer Alarms & UI
- **Transfer Markers:** Automatically places markers at transfer stations.
- **Loading State:** Introduced a dedicated `isRouting` state and `CircularProgressIndicator` overlay for complex multi-modal calculations.
- **Color Coding:** Road (Blue), Rail (Black), Walk (Grey).

---

## Branching Strategy (V3)

### The Hierarchy
- **v3-develop:** The main integration branch for all V3 features. **ALL V3 work must merge here.**
- **main:** Production branch (currently V2).

---

## V3 Roadmap & Implementation Checklists

### Phase 1: Transit Data Foundation
*Objective: Establish the data models and network clients required for multi-modal routing.*

- [x] **Define Transit Data Models:**
    - [x] Create `JourneyLeg` domain model (mode, geometry, start/end points, estimated duration).
    - [x] Create `TransitItinerary` model to hold a collection of legs.
- [x] **Room Database Migration:**
    - [x] Update `SavedRouteEntity` to support multi-leg storage.
    - [x] Update `JourneyHistoryEntity` for V3 metadata.
- [x] **Transit API Client:**
    - [x] Implement `OpenRailRoutingApiService` (GraphHopper-compatible).
    - [x] Integrate OSRM for "First/Last Mile" road segments.
- [x] **Repository Expansion:**
    - [x] Update `RouteRepository` to fetch itineraries using the new multi-modal logic.

### Phase 2: The Multi-Modal Engine
*Objective: Refactor the spatial and alarm logic to support leg-by-leg tracking, fallback mechanisms, and dynamic intervals.*

- [x] **Refactor `RouteDistanceEngine`:**
    - [x] Modify `calculateRemainingDistance` to accept combined multi-modal geometries.
- [x] **Enhance `AlarmEngine` State Machine:**
    - [x] Support seamless tracking across combined road/rail polylines.
- [x] **Location Service Updates:**
    - [x] Implement multi-modal route updates via `LocationAlarmService`.
    - [x] Handle high-precision road-snapping across all journey legs.

### Phase 3: UI/UX for Multi-Modal Journeys
*Objective: Surface multi-modal controls and data to the user.*

- [x] **Transport Mode Selection:**
    - [x] Implement mandatory "Roadway" vs "Railway" selector.
- [x] **Loading Feedback:**
    - [x] Implement `CircularProgressIndicator` overlay during route optimization.
- [x] **Map Rendering Enhancements:**
    - [x] Implement multi-color/multi-style polyline rendering for different transport modes.
    - [x] Display transfer station markers on the map.
- [x] **Journey Summary & History:**
    - [x] Update `JourneySummarySheet` to show segment-by-segment breakdowns.

### Phase 4: Verification & Performance
*Objective: Ensure reliability across diverse travel scenarios.*

- [x] **Unit Testing:**
    - [x] Verify distance calculations for multi-leg paths.
- [ ] **Integration Testing:**
    - [ ] Simulate a multi-leg journey using GPX playback.
- [x] **Performance Tuning:**
    - [x] Optimize station evaluation via parallel Coroutines (`async`/`awaitAll`).

---

## Future Considerations & Optimizations
- [ ] **Optimize Rail Multi-Modal:**
    - Evaluate more candidate stations (beyond top 3).
    - Implement optional "Full Matrix Evaluation" (NxN) for absolute shortest total journey.
    - Add logic to handle "Walking to Station" if road distance is very short.
- [ ] **Real-Time GTFS Integration:** Fetch actual train arrival times to adjust predictive alarms.
- [ ] **Battery Benchmarking:** Monitor impact of complex multi-modal tracking on long trips.

---

## Refinement Phase & Debugging History

### ❌ Failed/Deprecated Routing Attempts
- [x] **Valhalla/Transitland:** 401 Unauthorized errors on public mirrors.
- [x] **ORS (Railway Profile):** ORS does not have a native rail profile; pedestrian proxy was insufficient for true rail routing.
- [x] **Mapbox/GraphHopper:** Restricted access or API key issues for transit modes.

### ✅ Success: OpenRailRouting + Multi-Modal Engine
- [x] **OpenRailRouting Integration:** Reliable, free rail routing via `routing.openrailrouting.org`.
- [x] **Road-Rail-Road Logic:** Solved the "last mile" problem by stitching OSRM and ORR segments.
- [x] **Optimized Station Selection:** Replaced "crow-flies" distance with "actual road distance" for station discovery.
- [x] **UI Differentiation:** Clearly separated Road vs Rail via color coding (Blue vs Black) and icons.

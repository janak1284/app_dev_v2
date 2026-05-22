# Version 3: Multi-Modal Transit Engine

## Vision
Version 3 (V3) transforms the application from a roadway-centric alarm into a comprehensive **Multi-Modal Transit Engine**. It introduces the capability to handle complex journeys involving trains, buses, and walking legs, ensuring users are woken up not just for their final destination, but for every critical transfer point. 

Crucially, V3 adapts to the realities of public transit by gracefully handling GPS degradation (e.g., in tunnels), optimizing battery life during long journeys, and reacting to real-time delays and missed connections.

---

## Technical Architecture Changes

### 1. Multi-Modal Routing API
- **Primary Transit Provider:** Valhalla (via Transitland) or OpenTripPlanner (OTP).
- **Functionality:** Fetches itineraries that combine multiple modes of transport, parsing real-time GTFS data for delays when available.
- **Integration:** Exists alongside the OSRM (Road) client, selected based on user preference.

### 2. Leg-Based Tracking Model
- **`JourneyLeg` Entity:** A new domain model representing a single segment of a trip (e.g., a specific train ride or a walk to a bus stop).
- **Properties:** Transport mode, geometry (LineString), start/end coordinates, and estimated arrival time.
- **State Machine:** The `AlarmEngine` tracks progress against the *active leg*. Upon arrival or transfer, it transitions to the next leg.

### 3. Intermediate Transfer Alarms & Recalculation
- **Logic:** Alarms are triggered when approaching the end of a `JourneyLeg` that is not the final destination.
- **Missed Transfers:** The engine actively monitors for deviations after a transfer point to detect missed connections and trigger automatic recalculations.
- **UI:** Distinct "Transfer" vs "Arrival" alarm states and notifications.

### 4. Smart Mode Selector & Adaptive Tracking
- **Implementation:** A new selection UI during journey initiation allowing users to choose granular transit options (e.g., "Road", "Transit", "Minimize Walking").
- **Battery Optimization:** Dynamic polling intervals that rely on network location for long, stable transit legs, switching to high-accuracy GPS only when approaching a transfer.
- **Dead Reckoning Fallback:** Temporary reliance on transit API estimated arrival times when GPS signal is lost (e.g., subway tunnels).

---

## Branching Strategy (V3)

### The Hierarchy
- **v3-develop:** The main integration branch for all V3 features. **ALL V3 work must merge here.**
- **main:** Production branch (currently V2).
- **v2-develop:** Maintenance and stability branch for Version 2.

### Branch Naming Conventions (V3)
Standardized prefixes for Developer 1 (Data/Spatial) and Developer 2 (Network/UI):

**Developer 1 (Data/Spatial):**
- `feat/v3-data-leg-schema`
- `feat/v3-engine-leg-tracking`
- `fix/v3-service-leg-transition`

**Developer 2 (Network/UI):**
- `feat/v3-net-transit-api`
- `feat/v3-ui-mode-selector`
- `refactor/v3-ui-leg-summary`

---

## V3 Roadmap & Implementation Checklists

### Phase 1: Transit Data Foundation
*Objective: Establish the data models and network clients required for multi-modal routing.*

- [x] **Define Transit Data Models:**
    - [x] Create `JourneyLeg` domain model (mode, geometry, start/end points, estimated duration).
    - [x] Create `TransitItinerary` model to hold a collection of legs.
- [x] **Room Database Migration:**
    - [x] Update `SavedRouteEntity` to support multi-leg storage by creating a dedicated `JourneyLegEntity` table with a foreign key to the main route.
    - [x] Update `JourneyHistoryEntity` for V3 metadata.
- [x] **Transit API Client:**
    - [x] Implement `ValhallaApiService` or `OtpApiService` using Retrofit.
    - [x] Ensure the client requests and parses real-time GTFS data (delays/cancellations) if supported by the provider.
    - [x] Implement response parsers for multi-modal GeoJSON outputs.
- [x] **Repository Expansion:**
    - [x] Update `RouteRepository` to fetch transit itineraries from the new API.

### Phase 2: The Multi-Modal Engine
*Objective: Refactor the spatial and alarm logic to support leg-by-leg tracking, fallback mechanisms, and dynamic intervals.*

- [ ] **Refactor `RouteDistanceEngine`:**
    - [ ] Modify `calculateRemainingDistance` to accept a specific `JourneyLeg`.
    - [ ] Update `calculateDeviation` to handle leg-specific geometry.
- [ ] **Enhance `AlarmEngine` State Machine:**
    - [ ] Introduce `ALARM_TRANSFER` state.
    - [ ] Introduce `WAITING_FOR_CONNECTION` and `RECALCULATING` states to provide a buffer for delays and a safety net for missed trains.
    - [ ] Implement logic to detect arrival at the end of a non-final leg.
    - [ ] **Dynamic Alarm Reset:** Implement a "reset" mechanism for time-based alarms. If the alarm triggers but the user slows down (e.g., traffic), and the ETA rises above the threshold, the alarm state should reset to allow a second trigger once the ETA drops again.
    - [ ] Implement missed transfer detection (comparing location post-dismissal against the start of the next leg).
- [ ] **Location Service Updates:**
    - [ ] Update `LocationAlarmService` to manage the active leg index.
    - [ ] Implement dynamic location request intervals based on transport mode (low-frequency for train rides, high-frequency for transfers).
    - [ ] Implement "Dead Reckoning" / Time-Based Fallback mechanism for sudden GPS loss.
    - [ ] Ensure persistence of journey state during app backgrounding or service restarts.

### Phase 3: UI/UX for Multi-Modal Journeys
*Objective: Surface multi-modal controls and data to the user.*

- [ ] **Transport Mode Selection:**
    - [ ] Update `ModernConfigurationSheet` with a toggle/tab for "Road" vs "Transit".
    - [ ] Add granular transit options (e.g., "Minimize Walking", "Wheelchair Accessible").
    - [ ] Implement logic to switch between OSRM and Valhalla/OTP based on selection.
- [ ] **Alarm Configuration Logic:**
    - [ ] **Exclusive Alarm Toggling:** Enforce mutual exclusivity between the "Distance Alarm" and "Smart ETA Alarm". 
        - [ ] If one is toggled ON while the other is already active, the previously active one must automatically toggle OFF.
        - [ ] Update UI state management in `ModernConfigurationSheet` to reflect this logic.
    - [ ] **Advanced Input UI:** Replace the current slider-based inputs for distance and time with specialized text input fields.
        - [ ] Support units for distance (meters, kilometers).
        - [ ] Support minute-based input for the Smart ETA alarm.
    - [ ] **Location Availability Guard:** Implement a system-wide alert or prompt that triggers if the app is opened but location services are disabled on the device.
- [ ] **Map Rendering Enhancements:**
    - [ ] Implement multi-color/multi-style polyline rendering for different transport modes (e.g., Blue for Walking, Orange for Train).
    - [ ] Display transfer station markers on the map.
- [ ] **Journey Summary & History:**
    - [ ] Update `JourneySummarySheet` to show a breakdown of all legs.
    - [ ] Update history views to reflect multi-modal icons and statistics.

### Phase 4: Verification & Performance
*Objective: Ensure reliability across diverse travel scenarios, handling edge cases gracefully.*

- [ ] **Unit Testing:**
    - [ ] Test leg-transition logic with mocked `TransitItinerary`.
    - [ ] Verify distance calculations for multi-leg paths.
    - [ ] Test State Machine transitions including simulated missed connections.
- [ ] **Integration Testing:**
    - [ ] Simulate a multi-leg journey (Walk -> Train -> Walk) using GPX playback.
    - [ ] Verify that the "Transfer Alarm" triggers and correctly advances the state.
    - [ ] Simulate GPS loss during a transit leg to verify the Dead Reckoning fallback.
- [ ] **Performance Tuning:**
    - [ ] Optimize polyline rendering for complex, high-point itineraries.
    - [ ] Benchmark battery impact of dynamic location tracking across long simulated journeys.

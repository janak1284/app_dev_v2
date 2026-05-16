# REFACTOR APP

## Phase 1: The Data Foundation (Room DB)
Before touching the UI or the map, we need to establish where the data lives. Room will act as our single source of truth.

* [x] **Implement Room Dependencies:** Add the required Room Gradle dependencies (room-runtime, room-ktx, room-compiler).
* [x] **Define SavedRoute Entity:** Create a table to store the metadata of the journey. Columns should include routeId (Primary Key), destinationName, targetTime (if applicable), and dateSaved.
* [x] **Define RouteBreadcrumb Entity:** Create a table to hold the actual GPS trail. Columns should include pointId, routeId (Foreign Key linked to SavedRoute), latitude, longitude, speed, and timestamp.
* [x] **Create DAOs:** Write the Data Access Objects to insert breadcrumbs in batches and to fetch a Flow<List<SavedRoute>> for your new Home Screen.
* [x] **Repository Layer:** Abstract the Room DB behind a Repository so your ViewModels can seamlessly observe the saved routes.

## Phase 2: Network & Visuals (OSRM + MapLibre)
Next, we connect to the routing engine and get the path rendering on the screen.

* [x] **OSRM Retrofit Interface:** Build a new API service for http://router.project-osrm.org. Construct the GET request to fetch the driving/walking route between the user's current location and the destination.
* [x] **GeoJSON Parsing:** Set up Kotlinx Serialization to parse the OSRM response, specifically extracting the LineString coordinates and the duration (estimated time).
* [ ] **MapLibre Rendering:** Feed the parsed LineString directly into MapLibre as a GeoJsonSource and draw it using a LineLayer. This gives the user a visual path to follow.

## Phase 3: The Engine Room (Spatial Math & Predictive ETA)
This is where the application becomes intelligent. It leans heavily into data science principles, processing real-time streams to make predictions.

* [x] **Turf-Java Integration:** Add the Turf library. This is crucial for local, on-device spatial calculations without pinging an API.
* [x] **Route Snapping & Distance:** Update the Foreground Service. Every time a new GPS coordinate arrives, use Turf to calculate the distance along the OSRM LineString from the user's snapped position to the final destination.
* [x] **The 100m Deviation Trigger:** Calculate the cross-track distance (how far the raw GPS point is from the OSRM line). If deviation > 100 meters, drop the current polyline and trigger Phase 2 again to fetch a new route.
* [x] **Sliding Window Speed Algorithm:** Implement an Exponential Moving Average (EMA) or a simple queue (e.g., the last 30 location updates) to calculate the user's actual average speed.
* [x] **Dynamic ETA Calculation:** Divide the Turf remaining route distance by your sliding average speed. This yields a highly accurate, real-time ETA that adapts to traffic or train delays.

## Phase 4: Service State Machine Overhaul
The background service must evolve from a simple trigger to a continuous recording engine.

* **Breadcrumb Buffering:** As the FusedLocation Provider emits locations, store them in a local MutableList<Location>.
* **State Redesign:** Change the service states. Instead of terminating when the alarm fires, transition from TRACKING to ALARM_RINGING.
* **Journey Termination:** The location collectors and the breadcrumb buffer must keep running until the user explicitly triggers an End Journey Intent.
* **Save Mechanism:** When the journey ends, pass the buffered coordinate list to the Room Repository via a bulk insert to save the actual path taken.

## Phase 5: UI Refactoring & Terminology
Finally, align the Jetpack Compose layer with the new backend reality.

* **Scrub Old Terminology:** Search and replace all instances of "Guard" and "Backup Alarm". Rename them to Distance Alarm and Time Alarm in the UI strings and ViewModel states.
* **Home Screen Implementation:** Build a new landing screen. It should observe the Room DB via the ViewModel and display a neat list of past SavedRoute cards.
* **Journey Summary Sheet:** When the user clicks "End Journey", pop up a Compose Bottom Sheet asking: "Save this route?". If they say yes, execute the Room database insert from Phase 4.
* **Alarm Configuration:** Update the setup sheet. Users now select either a Distance threshold (e.g., "Wake me 2km away") OR a Time threshold (e.g., "Wake me 10 minutes before arrival", which relies on your dynamic ETA algorithm).

## The Onboarding Brief (Read this first)
**The Big Pivot:**
We are shifting the Location Alarm MVP from a simple "proximity trigger" to a smart, predictive routing engine. Previously, the app drew a straight circle around the destination. This caused false positives if the user was on a winding road or a V-shaped junction.

**The Solution:**
We are integrating Open Source Routing Machine (OSRM) to get actual road paths (GeoJSON). As the user moves, we will snap their GPS coordinates to this path, calculate the actual remaining route distance using a spatial library (Turf-Java), and calculate a dynamic ETA based on their real-time sliding average speed. We are also adding a Room Database so users can save and revisit their actual traveled routes.

## Work Division Strategy

### Developer 1: The Spatial & Data Engineer
**Focus:** Background Services, Spatial Mathematics, and Database Architecture.

**1. The Room Database Foundation:**
* Setup the Room dependencies.
* Create the SavedRoute (metadata) and RouteBreadcrumb (GPS points) Entities.
* Write the DAOs for batch inserting GPS trails and fetching saved routes.
* Expose this data to the ViewModel layer via a Repository.

**2. The Spatial Math Engine (Turf-Java):**
* Integrate the Turf-Java dependency.
* Write the utility to calculate the "cross-track distance" (checking if the user's raw GPS point deviates more than 100m from the OSRM polyline).
* Write the logic to slice the polyline and calculate the remaining distance along the route.

**3. Service State Machine Overhaul:**
* Update LocationAlarmService to buffer incoming GPS coordinates into a list. Implement the "Sliding Average Speed" algorithm to calculate dynamic ETAs.
* Handle the journey lifecycle (saving the buffered list to Room when the user clicks "End Journey").

### Developer 2: The Network & UI Engineer
**Focus:** External APIs, Map Rendering, and Jetpack Compose Interfaces.

**1. The OSRM API Integration:**
* Create a Retrofit interface for the OSRM public API (http://router.project-osrm.org).
* Write the network call to fetch the /driving/route between the user's start and end coordinates.
* Use Kotlinx Serialization to parse the response and extract the LineString (GeoJSON) and estimated duration.

**2. MapLibre GeoJSON Rendering:**
* Take the parsed LineString from the OSRM response and feed it into the MapLibre map. Implement a GeoJsonSource and a LineLayer to visually draw the route line on the screen so the user can see their path.

**3. UI/UX Refactoring:**
* Terminology Cleanup: Scrub the codebase of "Guard" and "Backup Alarm". Rename them to Distance Alarm and Time Alarm. Home Screen Build: Create a new Jetpack Compose screen that observes the Room Database (once Dev 1 finishes it) to display a list of SavedRoute cards. Journey Summary Sheet: Build a Bottom Sheet that appears when a user ends their trip, prompting them: "Save this route?".

## The Handshake (Where your code meets)
To work in parallel without blocking each other, you need to agree on data contracts.

**1. The Route Contract:** Dev 2 will fetch the OSRM route. Dev 1 needs that route to do spatial math.
* **Agreement:** Dev 2 will expose the parsed OSRM LineString via a StateFlow in the ViewModel. Dev 1 will observe this flow in the service to run the Turf-Java calculations.

**2. The Database Contract:** Dev 2 needs to display saved routes on the UI, but Dev 1 is building the database.
* **Agreement:** Dev 1 will quickly define the SavedRoute data class and a mock repository first. Dev 2 can use this mock data to build the Home Screen UI immediately, while Dev 1 wires up the actual SQLite backend.

## Branching Strategy
Here is a tailored, lightweight Feature Branching strategy (inspired by GitHub Flow) designed specifically for a frontend/backend split.

### The Branch Hierarchy
* **main:** The untouchable source of truth. This currently holds your working MVP. Do not commit anything here during the pivot.
* **v2-develop:** Your shared integration branch. This is the new "main" for the duration of this pivot. All completed features merge here.
* **Feature Branches:** Short-lived branches where the actual coding happens.

### Branch Naming Conventions
Use standardized prefixes so it is instantly clear who is working on what and what part of the app is being touched.

**Format:** type/domain-short-description

**For Developer 1 (Spatial/Data):**
* feat/data-room-schema
* feat/engine-turf-distance
* fix/service-breadcrumb-buffer

**For Developer 2 (Network/UI):**
* feat/net-osrm-retrofit
* feat/ui-maplibre-geojson
* refactor/ui-terminology-cleanup

### The Daily Workflow
1. **Branch Off:** Both developers always cut their new feature branches from the latest version of v2-develop.
2. **Commit Often:** Keep commits logical and small.
3. **Pull Request (PR):** When a feature is done, open a PR against v2-develop.
4. **The Rule of Two:** You must review and approve each other's code before merging. This ensures the UI developer understands the database changes, and the Database developer understands the network models.
5. **Merge and Delete:** Once approved, squash-merge into v2-develop and delete the feature branch to keep the repo clean.

### Managing the "Handshake" (Avoiding Blockers)
Since the UI relies on the Database, and the Spatial Math relies on the Network, you need a strategy to prevent one developer from sitting idle while the other finishes a prerequisite.

* **The Interface First Approach:** Developer 1 (Room) and Developer 2 (Retrofit) should agree on the exact data class structures first (e.g., the SavedRoute data class and the LineString model).
* **Create a stub branch:** Push a quick branch to v2-develop that only contains these empty data classes and interface definitions.
* **Work in Parallel:** Now, Developer 2 can build the Home Screen UI using a hardcoded, fake list of SavedRoute objects, while Developer 1 spends the next few days actually wiring up SQLite to output that exact list. When you both merge, the real data flows into the finished UI seamlessly.
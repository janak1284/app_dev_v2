Here is the fully revised and mathematically sound `V4_app.md` architecture specification. All straight-line geometry flaws have been purged, battery-saving optimizations are included, and the Android 14 execution limits are mapped out.

---

# V4 Architecture Specification & Implementation Plan

**File Name:** `V4_app.md`

**Project:** Location Alarm (Strict Geometry & Live Telemetry Engine)

**Constraint Mandate:** 100% Route-Based Distances. Haversine/Straight-Line math is strictly prohibited across all app states.

---

## 1. System Architecture Overview

The application is a multi-modal commuter alarm relying on strict geographical linestrings and real-time data. To maintain a lightweight mobile footprint, the workload is distributed across three distinct layers:

### Layer 1: The Docker Microservice (Railway Telemetry)

* **Where:** Hugging Face Spaces (Node.js + Playwright + Stealth Plugins)
* **Responsibility:** Bypasses the lack of open Railway APIs. Scrapes third-party websites to extract live `ETA` and the `station_sequence` (the ordered array of stops). It handles zero GPS or distance math.

### Layer 2: The Cloud Cache (Anti-Ban Shield)

* **Where:** Supabase PostgreSQL (Free Tier)
* **Responsibility:** A highly transient database acting as a firewall. It stores scraped railway ETAs and routes with a strict 10-minute TTL (Time-to-Live). Collapses concurrent mobile requests to prevent target sites from IP-banning the Docker container.

### Layer 3: The Android Client (The Math & Routing Engine)

* **Where:** User's Mobile Device (Kotlin, Turf-Java, OSRM API, Room DB)
* **Responsibility:** * Manages Roadway routing natively via direct calls to the FOSS OSRM API.
* Reconstructs physical Railway routes by pulling exact track geometries from an embedded OpenStreetMap (Overpass API) database, completely avoiding straight-line station-to-station inaccuracies.
* Uses Turf-Java to snap the hardware GPS to the polylines, executing the exact track-distance remaining.
* Adjusts tracking intensity dynamically to preserve battery and overrides OS-level Do Not Disturb limits to guarantee alarm execution.



---

## 2. Core Methodologies

### A. The "Snap & Slice" Distance Algorithm (Turf-Java & Overpass)

Used for **both** Roadway and Railway active tracking to guarantee perfect route adherence.

1. **Snap:** Takes the user's raw, drifting hardware GPS coordinate and uses `nearestPointOnLine` to mathematically anchor them onto the exact Mapbox/Osmdroid polyline (OSRM for roads, Overpass GeoJSON for railways).
2. **Slice:** Uses `lineSlice` to cut the polyline from the user's snapped position to the final destination.
3. **Measure:** Uses `length` to calculate the exact, curve-accurate distance remaining along the physical road or tracks.

### B. Adaptive Hardware Polling (Battery Protection)

Running matrix math on a 10,000-point polyline drains the battery. The GPS polling interval adapts to the user's physical speed:

* `userSpeed < 20 km/h` (Stuck in traffic / halted): Run Turf-Java every **15 seconds**.
* `userSpeed > 60 km/h` (Highway / Express train): Run Turf-Java every **3 seconds**.

### C. Roadway Dynamic Rerouting

Because buses/cars can detour (unlike trains), the app continuously measures the deviation between the user's raw GPS and the snapped polyline point.

* **The Threshold Gate:** If `deviation > 500 meters`, the app silently pauses Turf-Java, pings the OSRM `/route` API for a fresh polyline from the user's new location to the destination, and resumes tracking.

### D. The Debounced N x 1 Matrix Pipeline (UI Search)

To display route-accurate distances in the Search Dropdown without DDoS'ing OSRM:

* User input is debounced using Kotlin StateFlow (350ms delay).
* Photon Geocoder returns 5 coordinate suggestions.
* The app sends a **single** OSRM `/table` request mapping the user's origin to all 5 destinations simultaneously, returning a matrix array of exact route distances to update the UI instantly.

---

## 3. Implementation Phases & Task Checklists

### Phase 1: Microservice Scraper Development (Railway)

**Objective:** Build the headless Chromium engine to fetch live train states.

* [ ] Initialize Node.js environment with `playwright`, `express`, and `puppeteer-extra-plugin-stealth`.
* [ ] Write text-based fuzzy DOM selectors to extract `ETA` and `station_sequence` arrays.
* [ ] Configure request interception to block images, CSS, and tracker scripts to minimize RAM.
* [ ] Implement the multi-target waterfall try-catch block (fallback to Target Site B if Site A fails).
* [ ] Expose an Express endpoint `/api/track?train=[ID]` returning the JSON payload.

### Phase 2: Cloud Cache Implementation (Railway)

**Objective:** Deploy the Supabase firewall to collapse requests.

* [ ] Deploy a free-tier PostgreSQL instance on Supabase.
* [ ] Create the schema: `train_number` (PK), `eta_time`, `station_sequence` (TEXT Array), and `last_updated`.
* [ ] Code the Node.js Request Collapsing logic: Check Supabase first. If `last_updated` is < 10 mins old, return cache. Otherwise, trigger Playwright, `UPSERT` the DB, and return.

### Phase 3: Infrastructure Deployment

**Objective:** Containerize and host the microservice.

* [ ] Write a `Dockerfile` using `mcr.microsoft.com/playwright` base.
* [ ] Bind the Express API to `0.0.0.0:7860`.
* [ ] Deploy to Hugging Face Spaces (Blank Docker template, 16GB RAM tier).
* [ ] Verify endpoint stability using external API tools.

### Phase 4: Mobile Client - Geometry & Data Prep

**Objective:** Implement strict geographic mapping and local preferences.

* [ ] **Overpass Track Extraction:** Query `overpass-turbo.eu` for the exact geographic rail tracks in the operating region. Convert to GeoJSON/Polylines.
* [ ] **Room DB Setup:** Create entities for `SavedDestinations` (Name, Lat, Lng, Target Distance, Alarm Mode) and store the Overpass railway polylines here.
* [ ] **Debounced Search:** Implement `StateFlow` with a 350ms debounce for the search bar.
* [ ] **Matrix Pipeline:** Connect Photon Geocoder outputs directly to the OSRM `/table` API to render exact route distances in the UI dropdown.

### Phase 5: Mobile Client - The Tracking Engine

**Objective:** Build the dual-mode alarm loop, offline fallbacks, and override OS execution limits.

* [ ] **Android 14 Overrides:** Add `<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>` and `<uses-permission android:name="android.permission.USE_EXACT_ALARM"/>` to the Manifest. Configure `AudioAttributes.USAGE_ALARM` to bypass Do Not Disturb mode.
* [ ] **Dynamic Location Provider:** Instantiate `FusedLocationProviderClient`. Implement the speed-based polling loop (15 seconds when slow, 3 seconds when fast).
* [ ] **Distance Mode Logic:** Implement the Turf-Java **Snap, Slice, and Measure** loop against the physical Overpass/OSRM polylines. Fire the alarm if `remainingTrackLength <= targetDistance`.
* [ ] **Time Mode Logic & Offline Fallback (Train):** * Compare system clock against the scraped API `ETA` minus the user's buffer.
* If the Hugging Face API throws a `SocketTimeoutException` (user lost internet in a rural zone), immediately execute the offline mathematical fallback to keep the alarm alive.
* [ ] **Dynamic Rerouting (Road):** During the snap process, evaluate `deviationDistance`. If > 500m, execute a background OSRM `/route` call to update the active linestring.
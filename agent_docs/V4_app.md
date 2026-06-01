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

* **Where:** Local Development Machine (via Secure Tunnel: Localtunnel)
* **Responsibility:** Bypasses the lack of open Railway APIs. Scrapes third-party websites to extract live `ETA` and the `station_sequence`. It is hosted locally to bypass "bot detection" and IP bans enforced by free-tier cloud providers.

### Layer 2: The Cloud Cache (Anti-Ban Shield)

* **Where:** Supabase PostgreSQL (Free Tier)
* **Responsibility:** A highly transient database acting as a firewall. It stores scraped railway ETAs and routes with a strict 10-minute TTL (Time-to-Live). Collapses concurrent mobile requests to prevent target sites from IP-banning the Docker container.

### Layer 3: The Android Client (The Math & Routing Engine)

* **Where:** User's Mobile Device (Kotlin, Turf-Java, OSRM API, OpenRailRouting API, Room DB)
* **Responsibility:** * Manages Roadway routing natively via direct calls to the FOSS OSRM API.
* Reconstructs physical Railway routes by pulling exact track geometries from the **OpenRailRouting (ORR) API**, completely avoiding straight-line station-to-station inaccuracies. This replaces the resource-heavy local Overpass DB to prevent OOM crashes.
* Maintains a local **Stations Database** (resurrected from scraped data) to quickly identify candidate entry/exit points for rail travel.
* Uses Turf-Java to snap the hardware GPS to the polylines, executing the exact track-distance remaining.
* Adjusts tracking intensity dynamically to preserve battery and overrides OS-level Do Not Disturb limits to guarantee alarm execution.



---

## 2. Core Methodologies

### A. The "Snap & Slice" Distance Algorithm (Turf-Java & ORR)

Used for **both** Roadway and Railway active tracking to guarantee perfect route adherence.

1. **Snap:** Takes the user's raw, drifting hardware GPS coordinate and uses `nearestPointOnLine` to mathematically anchor them onto the exact Mapbox/Osmdroid polyline (OSRM for roads, ORR Polyline for railways).
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

* [x] Initialize Node.js environment with `playwright`, `express`, and `puppeteer-extra-plugin-stealth`.
* [x] Write text-based fuzzy DOM selectors to extract `ETA` and `station_sequence` arrays.
* [x] Configure request interception to block images, CSS, and tracker scripts to minimize RAM.
* [ ] Implement the multi-target waterfall try-catch block (fallback to Target Site B if Site A fails).
* [x] Expose an Express endpoint `/api/track?train=[ID]` returning the JSON payload.

### Phase 2: Cloud Cache Implementation (Railway)

**Objective:** Deploy the Supabase firewall to collapse requests.

* [x] Deploy a free-tier PostgreSQL instance on Supabase.
* [x] Create the schema: `train_cache` table with `train_number` (PK), `payload` (JSONB), and `last_updated`.
* [x] Code the Node.js Request Collapsing logic: Check Supabase first. If `last_updated` is < 10 mins old, return cache. Otherwise, trigger Playwright, `UPSERT` the DB, and return.

### Phase 3: Infrastructure Deployment

**Objective:** Containerize and host the microservice with secure tunneling.

* [x] Write a `Dockerfile` using `mcr.microsoft.com/playwright` base.
* [x] Bind the Express API to `0.0.0.0:7860`.
* [x] **Local Pivot:** Transitioned from Hugging Face Spaces to local hosting due to automated bot-detection bans.
* [x] **Secure Tunneling:** Implemented `localtunnel` with a permanent subdomain (`janak-transit-test.loca.lt`) to provide a stable public endpoint for the Android client.
* [x] **Client Optimization:** Configured Android OkHttp client with 60s timeouts and `bypass-tunnel-reminders` headers.

### Phase 4: Mobile Client - Geometry & Data Prep

**Objective:** Implement strict geographic mapping and local preferences.

* [x] **Stations Database:** Resurrected `stations.json` (1.86MB) into `app/src/main/res/raw/`. Implemented Room entities and `StationRepository` to load and search 8000+ stations.
* [x] **OpenRailRouting (ORR) Client:** Implemented lightweight Retrofit client to fetch physical track geometry via encoded polylines.
* [x] **Polyline Decoder:** Added high-performance utility to convert ORR strings into Mapbox `Point` objects.
* [x] **Debounced Search:** Implement `StateFlow` with a 350ms debounce for the search bar.
* [x] **Matrix Pipeline:** Connect Photon Geocoder outputs directly to the OSRM `/table` API to render exact route distances in the UI dropdown.

### Phase 5: Mobile Client - The Tracking Engine

**Objective:** Build the dual-mode alarm loop, offline fallbacks, and override OS execution limits.

* [ ] **Android 14 Overrides:** Add `<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>` and `<uses-permission android:name="android.permission.USE_EXACT_ALARM"/>` to the Manifest. Configure `AudioAttributes.USAGE_ALARM` to bypass Do Not Disturb mode.
* [ ] **Dynamic Location Provider:** Instantiate `FusedLocationProviderClient`. Implement the speed-based polling loop (15 seconds when slow, 3 seconds when fast).
* [ ] **Distance Mode Logic:** Implement the Turf-Java **Snap, Slice, and Measure** loop against the physical ORR/OSRM polylines. Fire the alarm if `remainingTrackLength <= targetDistance`.
* [ ] **Time Mode Logic & Offline Fallback (Train):** * Compare system clock against the scraped API `ETA` minus the user's buffer.
* If the Hugging Face API throws a `SocketTimeoutException` (user lost internet in a rural zone), immediately execute the offline mathematical fallback to keep the alarm alive.
* [ ] **Dynamic Rerouting (Road):** During the snap process, evaluate `deviationDistance`. If > 500m, execute a background OSRM `/route` call to update the active linestring.
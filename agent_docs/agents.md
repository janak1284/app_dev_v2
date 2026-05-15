# AGENTS.md — Master Plan for Location Alarm V2 (Smart Commuter Engine)

## Project Overview

**App:** Location Alarm V2
**Goal:** An intelligent, route-based commuter navigation engine. It snaps user GPS coordinates to a predefined path, calculates actual remaining road distance, predicts dynamic ETAs based on sliding average speeds, and allows users to save historical route breadcrumbs.
**Stack:** Kotlin, Jetpack Compose, MapLibre Native, FusedLocationProvider, Foreground Service, Room (SQLite), Retrofit, Kotlinx Serialization, Turf-Java, OSRM API.
**Current Phase:** The V2 Pivot (Moving from Proximity MVP to Predictive Routing Engine).

---

## How I Should Think

1. **Understand Intent First** — Identify if the task belongs to **Developer 1 (Backend/Spatial)** or **Developer 2 (Network/UI)** before generating code.
2. **Respect the Data Contracts** — UI components must rely on Room `Flow` emissions or ViewModel `StateFlow` structures. Do not tightly couple UI to backend services.
3. **Ask If Unsure** — Ask ONE clear question if critical info or data models (`SavedRoute`, `LineString`) are missing.
4. **Plan Before Coding** — Propose a short architectural plan and wait for approval.
5. **Verify After Changes** — Ensure spatial math and network calls are robust against edge cases (e.g., GPS drift, network loss).

---

## Plan → Execute → Verify

1. **Plan:** Outline minimal approach targeting the specific Phase we are in.
2. **Execute:** Implement ONE small feature only.
3. **Verify:** Provide steps to test locally (especially for spatial calculations and DB migrations).
4. **Fix issues before continuing.**

---

## Context & Memory

* Treat this file , `CONTEXT.md` and `V2_app.md` as the master source of truth.
* Load details from `agent_docs/` only when needed.
* **CRITICAL:** We use Turf-Java for spatial math and OSRM for routing. Do NOT suggest Google Maps API or paid routing services.

---

## Team Structure & Domain (The "Handshake")

The project is split between two developers:
* **Backend & Spatial (Dev 1):** Room Database, Turf-Java math, `LocationAlarmService` state machine, EMA (Exponential Moving Average) speed algorithms.
* **Network & UI (Dev 2):** Retrofit/OSRM integration, MapLibre GeoJSON rendering, Jetpack Compose UI (Home Screen, Bottom Sheets).
* *Rule:* Always ensure mock data or interface contracts are established first so neither developer is blocked.

---

## Testing & Verification

Follow `agent_docs/testing.md`.

If no automated tests:
* Provide manual verification steps using simulated GPX tracks or Android Studio's location emulator.
* Test edge cases: User goes off-route (>100m deviation), device loses network, GPS signal drops.

---

## Checkpoints

* [x] MVP Proximity Alarm built (MapLibre + FusedLocation)
* [x] Hardware-level alarm bypassing notifications
* [ ] V2 DB Schema established (Room)
* [ ] V2 Routing engine connected (OSRM + Retrofit)
* [ ] V2 Spatial math active (Turf-Java route slicing)
* [ ] V2 Dynamic ETA working
* [ ] V2 UI (Home screen & History) implemented

---

## Current State (Update During Development)

**Last Updated:** May 2026
**Working On:** **Phase 1 & 2 — The V2 Pivot** (Establishing Room DB and OSRM Networking)
**Recently Completed:** MVP Phase (Straight-line distance, LocalFocusManager, Deep Dark Theme)
**Blocked By:** Data model contracts between DB and UI.

---

## Roadmap (The V2 Pivot)

### Phase 1: The Data Foundation (Room DB)
* [ ] Add Room dependencies.
* [ ] Create `SavedRoute` (Metadata) and `RouteBreadcrumb` (GPS Trail) Entities.
* [ ] Build DAOs for bulk inserts and Flow observations.
* [ ] Build Repository layer.

### Phase 2: Network & Visuals (OSRM + MapLibre)
* [ ] Setup Retrofit for OSRM public API.
* [ ] Parse GeoJSON `LineString` using Kotlinx Serialization.
* [ ] Render `LineString` dynamically via MapLibre `GeoJsonSource`.

### Phase 3: The Engine Room (Spatial Math & ETA)
* [ ] Integrate Turf-Java.
* [ ] Snap raw GPS to polyline and calculate remaining route distance.
* [ ] Implement >100m cross-track deviation trigger for route recalculation.
* [ ] Implement sliding average speed (EMA) for dynamic ETA.

### Phase 4: Service State Machine Overhaul
* [ ] Update `LocationAlarmService` to buffer GPS points into a `MutableList`.
* [ ] Shift states to keep tracking post-alarm until "End Journey".
* [ ] Bulk insert buffered route to Room on journey end.

### Phase 5: UI Refactoring & Terminology
* [ ] Change UI terminology to **Distance Alarm** and **Time Alarm**.
* [ ] Build Home Screen (Saved Routes list).
* [ ] Build "Save Journey" Bottom Sheet prompt.

---

## What NOT To Do

* Do NOT revert to Haversine/straight-line distance for proximity triggers.
* Do NOT use paid APIs (Google Directions, Mapbox APIs).
* Do NOT use volatile `SharedPreferences` for complex route coordinate storage.
* Do NOT block the Main Thread with Room DB inserts or Turf-Java calculations.
* Do NOT over-engineer architecture beyond Clean Architecture/MVVM.
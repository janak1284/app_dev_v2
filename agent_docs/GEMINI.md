# GEMINI.md — Gemini CLI Configuration for Location Alarm V2

## Project Context

**App:** Location Alarm V2 (Smart Commuter Engine)
**Stack:** Kotlin, Jetpack Compose, Room (SQLite), Retrofit, OSRM API, Turf-Java, MapLibre, FusedLocationProvider, Foreground Services.
**Team Structure:** Two Developers (Dev 1: Backend/Spatial DB | Dev 2: Network/UI).
**Current Phase:** V2 Pivot (Migrating from Proximity MVP to Predictive Routing Engine).

---

## Directives (CRITICAL)

1. **Read the Master Files:** ALWAYS read `AGENTS.md` and `CONTEXT.md` first to understand the current phase and architectural rules.
2. **Identify the Developer:** Before writing code, explicitly state if the task belongs to **Developer 1 (Data/Spatial)** or **Developer 2 (Network/UI)** to prevent crossing domain boundaries.
3. **Respect the Handshake:** If writing UI code that depends on a backend feature that isn't finished, provide Mock Data/Interfaces so the UI developer is not blocked.
4. **Enforce V2 Paradigms:** * Do NOT use `Location.distanceBetween` (Haversine). Use `Turf-Java` for route-based distance.
    * Do NOT use `SharedPreferences` for large arrays. Use `Room` for `RouteBreadcrumbs`.
    * Do NOT block the Main Thread with spatial math or DB inserts.
5. **Plan Before Coding:** Propose a short architectural plan and wait for the user's approval.
6. **Implement Incrementally:** Deliver ONE feature or file at a time. Do not dump massive multi-file refactors in a single prompt.

---

## Typical Workflow

1. Read `AGENTS.md` ,`CONTEXT.md` and `V2_app.md`.
2. Confirm the current Phase and the assigned Developer Role.
3. Propose a minimal execution plan adhering to the V2 Clean Architecture.
4. Wait for approval.
5. Implement the code using the specified FOSS dependencies (OSRM, Turf-Java, MapLibre, Room).
6. Provide explicit testing steps (e.g., Emulator GPX playback, WakeLock verification) for the generated code.
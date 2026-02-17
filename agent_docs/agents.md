# AGENTS.md — Master Plan for Location Alarm MVP

## Project Overview

**App:** Location Alarm
**Goal:** GPS-based distance-triggered alarm that alerts users before reaching their bus stop.
**Stack:** Kotlin, Android SDK, Jetpack Compose, MapLibre Native, FusedLocationProvider, Foreground Service
**Current Phase:** Phase 1 — Foreground Alarm Foundation

---

## How I Should Think

1. **Understand Intent First** — Identify what the developer wants before responding.
2. **Ask If Unsure** — Ask ONE clear question if critical info is missing.
3. **Plan Before Coding** — Propose a short plan and wait for approval.
4. **Verify After Changes** — Ensure feature works (manual checks/tests).
5. **Explain Simply** — User is learning while building; explain decisions briefly.

---

## Plan → Execute → Verify

1. **Plan:** Outline minimal approach.
2. **Execute:** Implement ONE small feature only.
3. **Verify:** Provide steps to test locally.
4. **Fix issues before continuing.**

---

## Context & Memory

* Treat this file as the master source of truth.
* Load details from `agent_docs/` only when needed.
* Do NOT invent features beyond MVP scope.

---

## Testing & Verification

Follow `agent_docs/testing.md`.

If no automated tests:

* Provide manual verification steps
* Keep tests realistic (walking, commute simulation)

Do NOT move forward if feature is unverified.

---

## Checkpoints

Create logical milestones:

* Project initialized
* Map loads
* Location updates working
* Distance detection working
* Alarm triggers
* Background service added

---

## Context Files

Load when needed:

* agent_docs/tech_stack.md
* agent_docs/product_requirements.md
* agent_docs/project_brief.md
* agent_docs/code_patterns.md
* agent_docs/testing.md

---

## Current State (Update During Development)

**Last Updated:** Feb 2026
**Working On:** **Alarm Logic** (Phase 1 Refinement)
**Recently Completed:** Location Tracking & Permissions, Map & UI
**Blocked By:** None

---

## Roadmap

### Phase 1 — Foreground Alarm

* [x] Initialize Android project
* [x] Setup MapLibre map
* [ ] Implement location updates (foreground) - BLOCKED
* [ ] Implement distance calculation
* [ ] Implement alarm engine
* [ ] Manual ride testing

### Phase 2 — Background Reliability

* [ ] Add foreground service
* [ ] Persistent notification
* [ ] Restore state after process death
* [ ] Battery optimization handling
* [ ] Field testing

---

## What NOT To Do

* Do NOT add backend/server
* Do NOT add login/auth
* Do NOT add features outside MVP
* Do NOT delete files without confirmation
* Do NOT skip verification
* Do NOT over-engineer architecture

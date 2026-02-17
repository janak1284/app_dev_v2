# Code Patterns

## Compose UI Rule

* UI = state only
* Business logic NOT inside composables

---

## ViewModel Rule

ViewModel owns:

* location state
* destination state
* tracking status

---

## Manager Rule

LocationTrackingManager handles:

* requesting updates
* stopping updates
* emitting coordinates

---

## Service Rule (Phase 2)

Foreground service:

* continues tracking when app minimized
* restores destination from storage
* re-registers location updates

---

## Alarm Engine Rule

Alarm module should:

* play sound
* vibrate
* stop tracking afterward

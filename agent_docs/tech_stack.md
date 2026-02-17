# Tech Stack & Tools

## Core Stack

* **Language:** Kotlin
* **Platform:** Android API 26+
* **UI:** Jetpack Compose
* **Map:** MapLibre Native
* **Location:** FusedLocationProviderClient
* **Architecture:** UI → ViewModel → LocationTrackingManager → Service

---

## Location Update Pattern

Use balanced accuracy:

* Foreground: frequent updates
* Later: controlled intervals based on distance

---

## Distance Calculation Pattern

Use Android built-in method:

Location.distanceBetween(
currentLat,
currentLng,
destinationLat,
destinationLng,
results
)

Trigger alarm when:

distance <= radius

---

## Permissions Required

* ACCESS_FINE_LOCATION
* ACCESS_COARSE_LOCATION
* ACCESS_BACKGROUND_LOCATION (Phase 2)
* FOREGROUND_SERVICE (Phase 2)

---

## Suggested Project Structure

app/
├── ui/
├── viewmodel/
├── location/
├── service/
├── alarm/
└── util/

---

## Naming Conventions

* ViewModels → `SomethingViewModel`
* Managers → `SomethingManager`
* Services → `SomethingService`
* UI screens → `SomethingScreen`

---

## Error Handling Rule

Always handle:

* Location unavailable
* Permission denied
* GPS disabled
* Null last-known location

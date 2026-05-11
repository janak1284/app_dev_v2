# Location Alarm MVP 📍⏰

Location Alarm is a smart, GPS-based alarm application designed specifically for commuters. It ensures you never miss your stop by triggering a high-priority alarm as you approach your destination, even if you've fallen asleep or are distracted during your travel.

## 🌟 Key Features

- **Real-Time Destination Search:** Powered by the **Photon API (OpenStreetMap)**, featuring 500ms debouncing, location-biased auto-suggestions, and persistent search history.
- **Interactive MapLibre Integration:** Smooth map interface with **Auto-Zoom** to selected destinations and floating controls for **One-Tap Refocus** and **Location Refresh**.
- **Proximity & Time-Based Alarms:** High-precision distance calculations combined with a **Backup Time-Based Alarm** system to ensure arrival coverage.
- **Background Guardian:** A dedicated **Foreground Service** tracks your location and maintains alarm readiness even when the app is in the background or the screen is off.
- **Hardware-Level Alarm Engine:** High-priority sound and haptic vibration loops that bypass standard notification limitations.
- **Modern Material 3 UI:** Entirely built with Jetpack Compose, featuring a "Deep Dark" mode for OLED efficiency and a seamless "Guardian" status system.
- **Full Customization:** User-configurable wake-up distance, system ringtone selection, vibration toggles, and persistent Theme Management (Light/Dark/System).

---

## 🏗️ Architecture Overview

The project follows a robust **Clean Architecture** pattern with a unidirectional data flow.

### 🗺️ Data & Logic Flow
`UI (Compose)` ↔ `ViewModel` ↔ `Service / API / Engine`

- **UI Layer:** State-only Jetpack Compose screens. `MapScreen` observes state flows and handles navigation to `SettingsScreen`.
- **ViewModel Layer:** `MapViewModel` manages state, persists history/preferences via `SharedPreferences`, and orchestrates search/alarm logic.
- **Service/Engine Layer:**
    - `LocationAlarmService`: Foreground Service for background tracking and distance monitoring.
    - `LocationTrackingManager`: Handles the FusedLocationProviderClient.
    - `AlarmEngine`: Hardware abstraction for Ringtone and Vibrator APIs.
    - `AlarmScheduler`: Manages system-level exact alarms for time-based backups.

---

## 🛠️ Tech Stack

- **Language:** [Kotlin](https://kotlinlang.org/)
- **UI Framework:** [Jetpack Compose](https://developer.android.com/jetpack/compose)
- **Map SDK:** [MapLibre Native for Android](https://maplibre.org/)
- **Networking:** [Retrofit](https://square.github.io/retrofit/) & [OkHttp](https://square.github.io/okhttp/)
- **Serialization:** [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization)
- **Persistence:** SharedPreferences with JSON Serialization
- **Architecture Components:** ViewModel, StateFlow, Coroutines, Foreground Services
- **Location Services:** Google Play Services (FusedLocationProvider)

---

## 📂 Project Structure

```text
app/src/main/java/com/janak/location/alarm/
├── alarm/                  # AlarmEngine, Handler, Receiver, Scheduler
├── api/                    # Retrofit service & Photon API client
├── location/               # Fused Location Provider manager
├── model/                  # Data models (PhotonResponse, AlarmSettings)
├── service/                # LocationAlarmService (Background Tracking)
├── ui/                     # Compose Screens & Components
│   ├── alarm/              # Configuration sheets
│   ├── components/         # Reusable UI elements (SearchField, WheelPicker)
│   ├── map/                # MapScreen & MapContent
│   ├── settings/           # SettingsScreen & Theme Controls
│   └── theme/              # Material 3 Design Tokens & Custom Colors
├── viewmodel/              # MapViewModel & Factory
└── MainActivity.kt         # Entry point & Theme Orchestration
```

---

## 🚀 Getting Started

1. **Clone the repository:**
   ```bash
   git clone https://github.com/janak1284/app_dev_v2.git
   ```
2. **Open in Android Studio:**
   Import the project as a Gradle project.
3. **Permissions:**
   The app requires `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, and `POST_NOTIFICATIONS` (on Android 13+). Ensure these are granted.
4. **Build & Run:**
   Connect an Android device or use an emulator with GPS simulation capabilities.

---

## 🗺️ Roadmap

- [x] **MVP Core:** Map + Search + Proximity Alarm.
- [x] **Phase 2:** Background Service integration for tracking when the screen is off.
- [x] **Phase 2:** Backup Time-Based Alarm (AlarmManager).
- [x] **Phase 3:** Search History and Persistent Preferences.
- [ ] **Phase 3:** Battery optimization and "Smart Polling" based on distance.
- [ ] **Phase 4:** Multiple saved destinations (Bookmarks).
- [ ] **Phase 4:** Advanced "Quiet Hours" and Location-based profiles.

---

## ⚖️ License

Built for the Location Alarm MVP Project. All rights reserved.

# Location Alarm MVP 📍⏰

Location Alarm is a smart, GPS-based alarm application designed specifically for commuters. It ensures you never miss your stop by triggering a high-priority alarm as you approach your destination, even if you've fallen asleep or are distracted during your travel.

## 🌟 Key Features

- **Real-Time Destination Search:** Powered by the **Photon API (OpenStreetMap)**, allowing users to find any location globally with debounced, location-biased auto-suggestions.
- **Interactive MapLibre Integration:** A smooth, reactive map interface for selecting destinations via search or direct map interaction.
- **Proximity-Based Triggers:** Precise distance calculations that trigger the alarm hardware when you enter your user-defined "Wake-up Radius".
- **Hardware-Level Alarm Engine:** High-priority sound and haptic vibration loops that bypass standard notification limitations to ensure you wake up.
- **Customizable Alerts:** User-configurable wake-up distance (100m - 5km), vibration toggles, and system ringtone selection.
- **Modern Material 3 UI:** A clean, intuitive interface built entirely with Jetpack Compose, featuring smooth animations and a "Guardian" status system.

---

## 🏗️ Architecture Overview

The project follows a robust **Clean Architecture** pattern with a unidirectional data flow, ensuring scalability and testability.

### 🗺️ Data & Logic Flow
`UI (Compose)` ↔ `ViewModel` ↔ `Manager / API / Engine`

- **UI Layer:** State-only Jetpack Compose screens. `MapScreen` observes state flows from the ViewModel and sends user events.
- **ViewModel Layer:** `MapViewModel` acts as the single source of truth. It manages search queries, debounces network requests, tracks proximity, and orchestrates the alarm lifecycle.
- **Service/Engine Layer:**
    - `LocationTrackingManager`: Handles the FusedLocationProviderClient for precise GPS updates.
    - `AlarmEngine`: Direct hardware abstraction for Ringtone and Vibrator APIs.
    - `PhotonApiService`: Retrofit-based interface for OpenStreetMap GeoJSON data.

---

## 🛠️ Tech Stack

- **Language:** [Kotlin](https://kotlinlang.org/)
- **UI Framework:** [Jetpack Compose](https://developer.android.com/jetpack/compose)
- **Map SDK:** [MapLibre Native for Android](https://maplibre.org/)
- **Networking:** [Retrofit](https://square.github.io/retrofit/) & [OkHttp](https://square.github.io/okhttp/)
- **Serialization:** [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization)
- **Architecture Components:** ViewModel, StateFlow, Coroutines
- **Location Services:** Google Play Services (FusedLocationProvider)

---

## 📂 Project Structure

```text
app/src/main/java/com/janak/location/alarm/
├── alarm/                  # AlarmEngine hardware logic & Receiver
├── api/                    # Retrofit service & API client
├── location/               # Fused Location Provider manager
├── model/                  # Data models (PhotonResponse, AlarmSettings)
├── ui/                     # Compose Screens & Components
│   ├── alarm/              # Configuration sheets
│   ├── components/         # Reusable UI elements (SearchField, WheelPicker)
│   ├── map/                # MapScreen integration
│   └── theme/              # Material 3 Design Tokens
├── viewmodel/              # MapViewModel & Factory
└── MainActivity.kt         # Entry point & Dependency Injection
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
   The app requires `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` to function. Ensure these are granted upon the first launch.
4. **Build & Run:**
   Connect an Android device or use an emulator with GPS simulation capabilities.

---

## 🗺️ Roadmap

- [x] MVP Core (Map + Search + Proximity Alarm)
- [ ] **Phase 2:** Background Service integration for tracking when the screen is off.
- [ ] **Phase 2:** Backup Time-Based Alarm (AlarmManager).
- [ ] **Phase 3:** Battery optimization and "Smart Polling" based on distance.
- [ ] **Phase 3:** Multiple saved destinations and history.

---

## ⚖️ License

Built for the Location Alarm MVP Project. All rights reserved.

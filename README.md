# Location Alarm V4 📍⏰
### *The Predictive Commuter Engine*

Location Alarm V4 is a multi-modal navigation and alarm system. It is a significant architectural pivot from V3, moving the heavy lifting of live transit tracking to a dedicated microservice while maintaining a high-precision spatial engine on the Android client.

---

## 🏗️ Monorepo Architecture

The project is organized as a monorepo to coordinate development between the backend scraper and the mobile client.

### 📁 `android-client/`
The native Android application (Kotlin, Compose).
- **Spatial Engine:** Uses **Turf-Java** for high-precision route snapping and slicing.
- **Routing:** Roadway routing via **OSRM API**; Railway routing via **Overpass GeoJSON**.
- **Tracking:** Background foreground service with battery-aware polling.

### 📁 `scraper-microservice/`
A Node.js microservice focused on live transit telemetry.
- **Engine:** **Playwright** (Headless Chromium) with stealth plugins.
- **Cache:** **Supabase (PostgreSQL)** with a 10-minute TTL to prevent target site bans.
- **Hosting:** Designed for **Hugging Face Spaces** (Dockerized).

---

## 🌟 V4 Key Features
- **Strict Geometry:** 100% Route-Based math. Haversine/Straight-Line calculations are strictly prohibited.
- **Railway Live Telemetry:** Scrapes live train data to provide real-time ETAs where open APIs do not exist.
- **Adaptive Hardware Polling:** Adjusts GPS frequency based on user speed (Fast = 3s, Slow = 15s) to preserve battery.
- **Android 14 Ready:** Full support for exact alarms and Do Not Disturb overrides.

---

## 🤝 Development Handshake
The frontend and backend communicate via a strictly defined contract:
- See **`API_Contract.md`** for the latest JSON schemas and endpoint definitions.

---

## 🚀 Getting Started

### Backend (Dev 1)
```bash
cd scraper-microservice
npm install
node server.js
```

### Android Client (Dev 2)
1. Open the `android-client` folder in Android Studio.
2. Build and run the `app` module.

---

## ⚖️ License
Built for the Location Alarm V4 Project. All rights reserved.

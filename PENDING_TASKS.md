# Pending Tasks (V4: The Microservice Pivot)

## 🏁 Phase 0: Transition & Restructuring
- [x] **Step 1: Secure V3 Engine:** Archived `v3-develop` with `archive-v3-engine` tag.
- [x] **Step 2: V4 Baseline:** Created `v4-develop` branch and cleaned obsolete local branches.
- [x] **Step 3: Monorepo Surgery:** Restructured repository into `android-client/` and `scraper-microservice/`.
- [x] **Step 4: API Contract:** Defined strict JSON schema in `API_Contract.md` for cross-team alignment.
- [x] **Step 5: Dev 1 Environment Setup:** Initialized Node.js, Express, and Playwright in `scraper-microservice/`. Verified baseline server on port 7860.

## 🚂 Phase 1: Scraper & Backend (Developer 1)
- [x] **Scraper Logic:** Implement Playwright headless navigation and data extraction for train ETA/Sequence.
- [ ] **Supabase Integration:** Set up PostgreSQL schema and implement 10-minute TTL caching logic.
- [ ] **Stealth & Anti-Ban:** Configure `puppeteer-extra-plugin-stealth` and request interception to minimize footprint.
- [ ] **Deployment:** Containerize with Docker and deploy to Hugging Face Spaces.

## 📱 Phase 2: Android Client Scaffolding (Developer 2)
- [ ] **Project Re-init:** Clean up the `android-client/` directory and ensure Gradle builds post-restructuring.
- [ ] **Network Layer:** Implement Retrofit client for the new V4 Microservice API.
- [ ] **Mock Integration:** Wire UI to use mock data from `API_Contract.md` while backend is in development.
- [ ] **Railway Geometry:** Implement Turf-Java "Snap, Slice, and Measure" for Overpass GeoJSON rail tracks.

## 🧪 Phase 3: Integration & Fallbacks
- [ ] **Offline Fallback:** Implement mathematical distance/time fallback for when the API is unreachable.
- [ ] **Android 14 Alarms:** Configure exact alarm permissions and DND bypass.
- [ ] **Field Testing:** Verify dual-mode (Road/Rail) tracking in real-world scenarios.

---
*Legacy V2/V3 tasks have been archived in the `archive-v3-engine` tag.*

# Pending Tasks (V2 Phase 6)

## UI Enhancements
- [x] **Search History Preview:** Fix the navigation logic to ensure clicking a search history entry correctly triggers a map route preview. (Half-baked for now, still needs some work and bug fixes)
- [x] **Selection Mode (Long Press):** Implement a long-press selection mode in both `SavedRoutesScreen` and `JourneyHistoryScreen` to allow bulk deletion. (Half-baked for now, still needs some work and bug fixes)
- [ ] **Edit Mode (Saved Routes):** Replace the "Delete" icon with an "Edit" icon (pen) for `SavedRoute` entries to launch an edit mode.
- [ ] **Keyboard Input Optimization:** Adjust UI layouts for text input fields (e.g., naming routes) so they are automatically scrolled into view or positioned above the software keyboard to prevent obscuring.

## Bug Fixes & Verification
- [ ] **Route Line Preview:** Investigate and fix the issue where route lines fail to render when opened from the search history, ensuring MapLibre camera zooms correctly to the destination.
- [ ] **Final Field Testing:** Perform real-world verification of the predictive routing engine, spatial calculations, and alarm triggers.
- [ ] **Speed-Adjusted ETA:** Implement the "Speed Ratio ETA" calculation ($\text{User Avg Speed} / \text{OSRM Expected Speed}$) to refine the dynamic arrival estimates.
 sends 
# Testing Strategy

## Phase 1 Manual Tests

1. Select destination on map
2. Start tracking
3. Walk toward destination
4. Confirm alarm triggers inside radius

---

## Ride Simulation Test

* Keep app open for 20–30 minutes
* Ensure no crash
* Ensure tracking continues

---

## Phase 2 Tests

* Lock screen → alarm should still fire
* Minimize app → alarm should still fire
* Kill app → service should recover

---

## Verification Loop

After every feature:

1. Build app
2. Run on device/emulator
3. Follow manual test steps
4. Fix issues before continuing

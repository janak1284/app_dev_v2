# Design Plan: MapScreen Redesign (Variant F)

## Summary
The finalized design (Variant F) transforms the "bleak" MapScreen into a modern, interactive "Guardian" interface. It combines a Google Maps-inspired search experience with a contextual, animated bottom card that manages alarm states. The configuration flow is now handled by a dedicated `ModernConfigurationSheet` that prioritizes clarity and interactive feedback.

## Component API / Props Interface

### MapScreen (Updated)
- `viewModel: MapViewModel`: Existing ViewModel managing state.
- `mapInstance: MapLibreMap?`: Current map instance for marker updates.

### ModernConfigurationSheet
- `initialSettings: AlarmSettings`: Initial state for the configuration.
- `onDismissRequest: () -> Unit`: Callback to close the sheet.
- `onSaveSettings: (AlarmSettings) -> Unit`: Callback when "ACTIVATE ALARM" is clicked.

## Implementation Instructions

### Step 1: Layout Restructuring
- Update `MapScreen.kt` to include a `Box` as the root container.
- Place the `AndroidView` (MapLibre) at the bottom of the stack.
- Implement the Top Search Card using `MaterialTheme.colorScheme.surface` and standard elevation.

### Step 2: Animated Status Card
- Use `AnimatedVisibility` with `slideInVertically` and `fadeIn` for the bottom card.
- The card should react to the `destination != null` state from the ViewModel.
- "SET UP ALARM" button triggers the configuration sheet; "TURN OFF" button calls `viewModel.toggleAlarm()`.

### Step 3: Modern Configuration Sheet
- Create `ModernConfigurationSheet.kt` as a replacement for the old bottom sheet.
- Implement a `Slider` for wake-up distance (100m to 5km).
- Use `QuickActionPill` components for Sound and Vibration toggles.
- Add auto-scroll logic using `scrollState.animateScrollTo(scrollState.maxValue)` inside a `LaunchedEffect` triggered by the section expansion.

## Multi-Modal Architecture (Transit Expansion)

### Transit API Integration
- **Engine:** OpenRailRouting (ORR) API for high-fidelity physical track geometry.
- **Routing:** Fetch multi-modal itineraries including walking, trains, and buses.
- **Stations Database:** Local Room-based database of 8000+ stations for candidate entry/exit evaluation.
- **Data Model:** Introduce `JourneyLeg` to encapsulate individual segments.
    - `type`: Walk, Train, Bus, etc.
    - `geometry`: LineString for the leg.
    - `arrivalPoint`: Coordinates for the end of the leg (transfer station or destination).

### Leg-Based Tracking Strategy
1. **Target Leg:** The `AlarmEngine` identifies the current active `JourneyLeg`.
2. **Dynamic Destination:** Instead of tracking the final destination, `RouteDistanceEngine` slices the polyline (ORR or OSRM) against the `arrivalPoint` of the *current* leg.
3. **Transfer Alarms:** When the user arrives at the end of a non-final leg, a unique "Transfer Alert" is triggered.
4. **Leg Transition:** Upon alarm dismissal or arrival, the engine automatically advances to the next `JourneyLeg` in the itinerary.

## Accessibility Checklist
- [ ] **Touch Targets:** Ensure the "Activate Alarm" button and "TURN OFF" buttons are at least 48dp in height.
- [ ] **Contrast:** Ensure `displaySmall` text (distance) has a contrast ratio of 4.5:1 against the card background.
- [ ] **Screen Readers:** Add `contentDescription` to all icons (Lock, Route, MusicNote, Vibration).
- [ ] **State Feedback:** Ensure the bottom card color changes (e.g., to `primaryContainer`) when the alarm is active.

## Testing Guidance
1. **Interaction Test:** Verify that the bottom card appears immediately after clicking a point on the map.
2. **Flow Test:** Confirm that "Activate Alarm" correctly updates the ViewModel and sets the alarm.
3. **Resilience Test:** Test the slider with one-handed operation to ensure "comfortable" density targets are met.


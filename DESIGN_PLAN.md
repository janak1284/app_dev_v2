# Design Plan: MapScreen Redesign (Variant F)

## Summary
The finalized design (Variant F) transforms the "bleak" MapScreen into a modern, interactive "Guardian" interface. It combines a Google Maps-inspired search experience with a contextual, animated bottom card that manages alarm states. The configuration flow is now handled by a dedicated `ModernConfigurationSheet` that prioritizes clarity and interactive feedback.

## Component API / Props Interface

### MapScreen (Updated)
- `viewModel: MapViewModel`: Existing ViewModel managing state.
- `mapInstance: MapLibreMap?`: Current map instance for marker updates.

### BackupTimerPill (New)
- `remainingSeconds: Long`: Time left in seconds.
- `totalSeconds: Long`: Initial duration in seconds for progress calculation.

### ModernConfigurationSheet
- `initialSettings: AlarmSettings`: Initial state for the configuration.
- `onDismissRequest: () -> Unit`: Callback to close the sheet.
- `onSaveSettings: (AlarmSettings) -> Unit`: Callback when "ACTIVATE GUARD" is clicked.

## Implementation Instructions

### Step 1: Layout Restructuring
- Update `MapScreen.kt` to include a `Box` as the root container.
- Place the `AndroidView` (MapLibre) at the bottom of the stack.
- Implement the Top Search Card using `MaterialTheme.colorScheme.surface` and standard elevation.
- **Visual Countdown:** Integrate the `BackupTimerPill` below the search bar, appearing when a backup timer is active.

### Step 2: Animated Status Card
- Use `AnimatedVisibility` with `slideInVertically` and `fadeIn` for the bottom card.
- The card should react to the `destination != null` state from the ViewModel.
- "ARM" button triggers the configuration sheet; "STOP" button calls `viewModel.toggleAlarm()`.

### Step 3: Modern Configuration Sheet
- Create `ModernConfigurationSheet.kt` as a replacement for the old bottom sheet.
- Implement a `Slider` for wake-up distance (100m to 5km).
- Use `QuickSettingCard` components for Sound and Vibration toggles.
- Implement the "Backup Timer" section using `AnimatedVisibility` for the time picker.
- **Time Selection:** Replaced custom wheel pickers with official Material 3 `TimeInput` for improved accessibility and speed.
- Add auto-scroll logic using `scrollState.animateScrollTo(scrollState.maxValue)` inside a `LaunchedEffect` triggered by the timer toggle.

## Accessibility Checklist
- [ ] **Touch Targets:** Ensure the "ACTIVATE GUARD" button and "ARM/STOP" buttons are at least 48dp in height.
- [ ] **Contrast:** Ensure `displaySmall` text (distance) has a contrast ratio of 4.5:1 against the card background.
- [ ] **Screen Readers:** Add `contentDescription` to all icons (Lock, Route, MusicNote, Vibration, Timer).
- [ ] **State Feedback:** Ensure the bottom card color changes (e.g., to `primaryContainer`) when the alarm is active.

## Testing Guidance
1. **Interaction Test:** Verify that the bottom card appears immediately after clicking a point on the map.
2. **Animation Test:** Verify that the "Backup Timer" expands smoothly without jumping the scroll position.
3. **Flow Test:** Confirm that "ACTIVATE GUARD" correctly updates the ViewModel and sets the alarm.
4. **Resilience Test:** Test the slider and time picker with one-handed operation to ensure "comfortable" density targets are met.

# Design Memory: Location Alarm App

## Design Tokens

### Color Palette
- **Primary:** `#6650a4` (Light) / `#D0BCFF` (Dark) - Used for "Active" states and primary buttons.
- **Secondary:** `#625b71` (Light) / `#CCC2DC` (Dark) - Used for secondary actions and background elements.
- **Surface Variant:** Used for card backgrounds in the configuration sheet (0.5f alpha).
- **Error:** Used for "STOP" and "DEACTIVATE" actions.

### Typography Decisions
- **HeadlineMedium:** Used for configuration sheet titles.
- **DisplaySmall:** Used for high-signal data like "Distance" and "Backup Time".
- **LabelLarge:** Used for section headers (e.g., "Wake-up Distance").
- **BodyMedium:** Used for secondary descriptions and labels.

### Spacing & Layout
- **Density:** Comfortable (16dp standard padding, 24dp for large cards).
- **Corner Radius:** 28dp for bottom sheets, 16dp for internal cards/buttons.
- **Elevation:** 8dp for the Top Search bar, 12dp for the Bottom Status card.

## Component Patterns

### Contextual Visibility
- UI elements (Bottom Card, Timer Pill) should only appear when relevant (e.g., destination set or alarm active), reducing cognitive load for the user.

### Progressive Disclosure
- Complex settings (Backup Timer) are hidden behind a toggle and revealed only when needed, keeping the initial view "clean and lean".

### Animated Feedback
- Every major state change (setting destination, arming alarm, expanding settings) uses vertical slide/fade animations (200-300ms) to provide clear visual cues.
- **Visual Countdown:** A progress-aware pill appears dynamically to show backup alarm status, utilizing a background fill to represent remaining time.

### Time Selection
- Transitioned from wheel-based selection to direct `TimeInput` to support the "On-the-go" user persona, prioritizing speed and input accuracy.

# Project Context: Location Alarm

## 1. Project Overview
The "Location Alarm" is an Android application designed to trigger an alarm when the user reaches a specific geographical location. It aims to help users who might fall asleep during transit.

## 2. Knowledge Base
- **Architecture:** MVVM (Model-View-ViewModel) with Jetpack Compose UI.
- **Core Components:**
  - `LocationAlarmService`: A foreground service responsible for continuous location monitoring using `FusedLocationProviderClient`.
  - `LocationTrackingManager`: Wraps the location client, providing updates via Kotlin `Flow`.
  - `AlarmScheduler`: Uses `AlarmManager` for scheduled backup alarms.
  - `AlarmEngine`: Logic for triggering the alarm UI.
  - `RingingActivity`: The alarm's ringing screen.
- **Key Dependencies:** Google Play Services Location API, Kotlin Coroutines, Jetpack Compose.
- **Critical Configuration:** Uses `foregroundServiceType="location"` and requires `WAKE_LOCK`, `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, and `SCHEDULE_EXACT_ALARM` permissions.

## 3. Current State
- **Implemented:**
  - Foreground Service for location monitoring.
  - `WakeLock` usage in `LocationAlarmService` to keep the device awake during tracking.
  - Location permission check within the service loop.
  - Notification channels for alarm triggering and service status.
- **Working:**
  - Basic location tracking and alarm triggering when the app is active/backgrounded (with foreground service).
- **Broken/Under Investigation:**
  - Alarm occasionally fails in deep sleep (mitigated by `WakeLock`, but requires further verification).
  - Permission prompting for "Enable Location" needs explicit UI handling in `MapScreen`.
  - Backup alarm scheduling (need to verify if it correctly handles re-registration after system reboot).

## 4. Goals Remaining
- Implement UI feedback/dialog for missing location permissions in `MapScreen`.
- Verify backup alarm behavior after device reboot (requires implementing `BOOT_COMPLETED` broadcast receiver).
- Ensure consistent `exact` alarm behavior on Android 12+ (API 31+).

## 5. Assumptions & Constraints
- Device must have Google Play Services installed.
- User must grant `ACCESS_BACKGROUND_LOCATION`.
- App is written for Android with Kotlin.

## 6. Next Steps
1. Add `BOOT_COMPLETED` receiver to re-register backup alarms.
2. Implement permission request dialog in `MapScreen` using `Accompanist` or standard `ActivityResultLauncher`.
3. Conduct field tests to verify deep-sleep alarm behavior.

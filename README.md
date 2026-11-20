# Alarm Clock (Android)

A simple alarm clock app written in Kotlin with Jetpack Compose and Material 3.

## Features

- List of alarms, sorted by time, with an on/off switch and delete button
- Add / edit an alarm: time, optional label, and the weekdays it repeats on (none = rings once;
  the list shows "Every day", "Weekdays", "Weekends" or e.g. "Mon, Wed, Fri")
- Settings (gear icon): choose how the time is entered — **Clock dial** (Material 3 time picker)
  or **Keyboard**, where you type the digits on the number keypad (`630` → 6:30, `1845` → 18:45,
  with an AM/PM switch on 12-hour phones). The **Typed times use 24-hour clock** switch makes
  typed digits always 24-hour (no AM/PM), whatever the phone's time format
- Settings → **Alarm sound**: one of the phone's alarm sounds (system sound picker) or any audio
  file (document picker; the app keeps read access to it). Falls back to the default alarm sound
  if the chosen one can't be played
- Exact alarms via `AlarmManager.setAlarmClock()` (ring on time even in Doze; shows the
  alarm icon in the status bar)
- When an alarm rings: looping alarm sound + vibration from a foreground service,
  a full-screen ring screen (also over the lock screen), and a notification with
  **Snooze** (10 min) and **Dismiss**
- Rings stop automatically after 10 minutes
- One-time alarms switch themselves off after ringing; repeating alarms reschedule for the next selected day
- Alarms are re-registered after reboot, app update, and time / time-zone changes

## Opening the project

1. Android Studio → **File › Open…** → select this `alarm` folder.
2. Let Gradle sync (Android Studio downloads Gradle 9.1 from `gradle/wrapper/gradle-wrapper.properties`).
3. Run the `app` configuration on a device or emulator (Android 8.0 / API 26 or newer).

From the command line: `./gradlew assembleDebug` (needs `ANDROID_HOME` or a `local.properties`
with `sdk.dir=...`; Android Studio creates that file on first sync). Gradle needs a JDK it supports — JDK 25
requires Gradle ≥ 9.1; Android Studio's bundled JDK works: `JAVA_HOME=<android-studio>/jbr ./gradlew assembleDebug`.

## Versions

AGP 8.13.0 · Gradle 9.1.0 · Kotlin 2.2.20 · Compose BOM 2024.12.01 · compileSdk/targetSdk 35 · minSdk 26.
All versions live in `gradle/libs.versions.toml`; Android Studio will offer upgrades.

## Project layout

```
app/src/main/java/com/example/alarmclock/
  Alarm.kt            data class + next-trigger calculation + JSON (de)serialisation
  AlarmRepository.kt  SharedPreferences storage, exposes alarms as a StateFlow
  AppSettings.kt      user settings (time entry, 24-hour typing, alarm sound), SharedPreferences + StateFlow
  AlarmSound.kt       alarm sound picker intent, display name, persisting file access
  AlarmScheduler.kt   AlarmManager scheduling, cancel, snooze, stop ringing
  AlarmReceiver.kt    fires when an alarm goes off; handles Snooze/Dismiss actions
  AlarmService.kt     foreground service: sound, vibration, ringing notification
  BootReceiver.kt     reschedules alarms after reboot / time changes
  RingActivity.kt     full-screen "alarm is ringing" activity
  MainActivity.kt     entry point, asks for notification permission
  TimeFormat.kt       time formatting helpers (respects 12/24-hour setting)
  ui/                 Compose screens and theme
```

## Permissions

| Permission | Why |
|---|---|
| `USE_EXACT_ALARM` (API 33+) / `SCHEDULE_EXACT_ALARM` (API 31–32) | exact alarm times |
| `POST_NOTIFICATIONS` | ringing notification (asked at first launch on Android 13+) |
| `USE_FULL_SCREEN_INTENT` | show the ring screen over the lock screen |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | play the alarm sound |
| `RECEIVE_BOOT_COMPLETED` | restore alarms after reboot |
| `VIBRATE`, `WAKE_LOCK` | vibration, keep the device awake while ringing |

Note: Google Play only allows `USE_EXACT_ALARM` and `USE_FULL_SCREEN_INTENT` for apps whose
core function is an alarm clock — which this is — but you'll need to declare that in the Play
Console if you ever publish it.

## Testing tips

- Set an alarm one or two minutes ahead, then lock the screen to see the full-screen ring UI.
- On an emulator, change the system time in Settings to test daily repeats and time-zone handling.
- `adb shell dumpsys alarm | grep alarmclock` shows what's currently scheduled.

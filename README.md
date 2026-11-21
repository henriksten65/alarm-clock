# Alarm Clock (Android)

A simple alarm clock app written in Kotlin with Jetpack Compose and Material 3.

## Features

- "Next alarm: tomorrow 07:30 · in 9 h 20 min" summary at the top (includes snoozes; updates every minute)
- List of alarms, sorted by time, with an on/off switch and delete button (**Undo** for 15 s after deleting)
- Add / edit an alarm: time, optional label, the weekdays it repeats on (none = rings once;
  the list shows "Every day", "Weekdays", "Weekends" or e.g. "Mon, Wed, Fri"), and its **sound**:
  "Default" (the sound from Settings), one of the phone's alarm sounds, or any audio file
- Settings (gear icon): choose how the time is entered — **Clock dial** (Material 3 time picker)
  or **Keyboard**, where you type the digits on the number keypad (`630` → 6:30, `1845` → 18:45,
  with an AM/PM switch on 12-hour phones). The **Typed times use 24-hour clock** switch makes
  typed digits always 24-hour (no AM/PM), whatever the phone's time format
- Settings → **Alarm sound**: one of the phone's alarm sounds (system sound picker) or any audio
  file (document picker; the app keeps read access to it), used by alarms without their own sound.
  Falls back to the default alarm sound if the chosen one can't be played. Read access to files no
  alarm uses any more is given back when the app starts
- Settings → **Ringing**: snooze length and "Stop ringing after" (1, 5, 10, 15, 20 or 30 min;
  default 10) and **Vibrate** on/off
- Settings → **Appearance**: light or dark colours, or **Same as phone** (default)
- Exact alarms via `AlarmManager.setAlarmClock()` (ring on time even in Doze; shows the
  alarm icon in the status bar)
- When an alarm rings: looping alarm sound + vibration from a foreground service,
  a full-screen ring screen (also over the lock screen), and a notification with
  **Snooze** and **Dismiss**
- On the ring screen, Back doesn't hide a ringing alarm; it shows "Tap Dismiss or Snooze to stop the alarm"
- Rings stop automatically after the "Stop ringing after" time, followed by a "Missed alarm: 06:15 · Label" notification
- One-time alarms switch themselves off after ringing; repeating alarms reschedule for the next selected day
- Alarms are re-registered after reboot, app update, and time / time-zone changes — and they
  ring even if the phone restarted and hasn't been unlocked yet (Direct Boot: alarm data lives in
  device-protected storage; a built-in fallback sound plays if the chosen one can't be read yet)
- If the phone was off (or restarting) when an alarm was due: an alarm missed by less than the
  "Stop ringing after" time rings as soon as the phone is back on; one missed by longer shows
  "Missed alarm: 07:30 · Label — The phone was off when it was due", and then a one-time alarm
  switches off and a repeating one waits for its next day. The same goes for snoozes
- **Skip next** on a repeating alarm skips just its next ring ("Skipping Mon 07:30" with
  **Don't skip**); it rings as usual after that. Switching the alarm off or changing its time or
  days cancels the skip. A pending snooze still rings
- Snooze is shown on the alarm ("Snoozed until 06:25") with **Cancel snooze**, and survives a
  reboot, app update or time change
- While an alarm rings, the app shows a **Ringing** card with Dismiss/Snooze, and opening the app
  goes to the ring screen — so an alarm can always be stopped, even with notifications off.
  Switching a ringing alarm off, or deleting it, also stops it
- Warning banners (with a button to fix it) when something would stop alarms from being seen or
  heard: exact alarms not allowed, notifications off, full-screen alerts off (Android 14+),
  alarm volume at its lowest, Do Not Disturb silencing alarms

## Opening the project

1. Android Studio → **File › Open…** → select this `alarm` folder.
2. Let Gradle sync (Android Studio downloads Gradle 9.1 from `gradle/wrapper/gradle-wrapper.properties`).
3. Run the `app` configuration on a device or emulator (Android 8.0 / API 26 or newer).

From the command line: `./gradlew assembleDebug` (needs `ANDROID_HOME` or a `local.properties`
with `sdk.dir=...`; Android Studio creates that file on first sync). Gradle needs a JDK it supports — JDK 25
requires Gradle ≥ 9.1; Android Studio's bundled JDK works: `JAVA_HOME=<android-studio>/jbr ./gradlew assembleDebug`.

## Release build (APK to install directly)

`./gradlew assembleRelease` builds `app/build/outputs/apk/release/AlarmClock-<version>-release.apk`
(minified with R8). It's signed with the key named in `keystore.properties` in the project root,
which is **not in git**:

```
storeFile=/home/<you>/.android/keystores/alarmclock-release.jks
storePassword=…
keyAlias=alarmclock
keyPassword=…
```

**Back up the keystore and `keystore.properties`.** Android installs an update only if it's
signed with the same key. Without the key you'd have to uninstall the app, deleting its alarms.

To install: copy the APK to the phone, open it, and allow "Install unknown apps" for the app you
opened it with. Or use `adb install AlarmClock-2.3-release.apk`. Debug builds have the id
`io.github.henriksten65.alarmclock.debug` and the name "Alarm Clock Debug", so they install next
to the release app.

## Versions

AGP 8.13.0 · Gradle 9.1.0 · Kotlin 2.2.20 · Compose BOM 2024.12.01 · compileSdk/targetSdk 35 · minSdk 26.
All versions live in `gradle/libs.versions.toml`; Android Studio will offer upgrades.

## Project layout

```
app/src/main/java/io/github/henriksten65/alarmclock/
  Alarm.kt            data class + next-trigger calculation + JSON (de)serialisation
  AlarmRepository.kt  SharedPreferences storage, exposes alarms as a StateFlow
  AppSettings.kt      user settings (time entry, 24-hour typing, alarm sound, snooze, auto-stop, vibrate, theme), SharedPreferences + StateFlow
  AlarmSound.kt       alarm sound picker intent, display name, persisting file access
  AlarmHealth.kt      checks for settings that would silence/hide alarms (+ intents to fix them)
  Storage.kt          device-protected SharedPreferences (Direct Boot) + migration
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
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` (Android 14+), `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (older) | play the alarm sound from a foreground service |
| `RECEIVE_BOOT_COMPLETED` | restore alarms after reboot |
| `VIBRATE`, `WAKE_LOCK` | vibration, keep the device awake while ringing |

Note: Google Play only allows `USE_EXACT_ALARM` and `USE_FULL_SCREEN_INTENT` for apps whose
core function is an alarm clock — which this is — but you'll need to declare that in the Play
Console if you ever publish it.

## Testing tips

- Set an alarm one or two minutes ahead, then lock the screen to see the full-screen ring UI.
- On an emulator, change the system time in Settings to test daily repeats and time-zone handling.
- `adb shell dumpsys alarm | grep alarmclock` shows what's currently scheduled.

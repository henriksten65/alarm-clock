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
  with an AM/PM switch on 12-hour phones); a new alarm then opens straight on the typed time with the
  keypad showing. The **Typed times use 24-hour clock** switch makes
  typed digits always 24-hour (no AM/PM), whatever the phone's time format
- Settings → **Number keypad**: **Phone** (1 2 3 on the top row, the default) or **Calculator**
  (7 8 9 on the top row). It sets the Timer keypad. For typed alarm times, Calculator shows the
  app's own keypad in the time dialog instead of the phone's keyboard (whose layout an app can't
  change); a hardware keyboard still works there. In landscape the keypad sits beside the time
- Settings → **Alarm sound**: one of the phone's alarm sounds (system sound picker) or any audio
  file (document picker; the app keeps read access to it), used by alarms without their own sound.
  Falls back to the default alarm sound if the chosen one can't be played. Read access to files no
  alarm uses any more is given back when the app starts
- Settings → **Ringing**: snooze length and "Stop ringing after" (1, 5, 10, 15, 20 or 30 min;
  default 10) and **Vibrate** on/off
- Settings → **Appearance**: light or dark colours, or **Same as phone** (default)
- Exact alarms via `AlarmManager.setAlarmClock()` (ring on time even in Doze; shows the
  alarm icon in the status bar)
- **Upcoming alarm** notification from two hours before an alarm until it rings ("Upcoming alarm
  07:30 · Work", or "Snoozed until 06:25"): an icon in the status bar, no sound. Its **Dismiss**
  button skips just that ring (a one-time alarm switches off, a repeating one skips to its next
  day, a snooze is cancelled)
- When an alarm rings: looping alarm sound + vibration from a foreground service,
  a full-screen ring screen (also over the lock screen), and a notification with
  **Snooze** and **Dismiss**
- On the ring screen, Back doesn't hide a ringing alarm; it shows "Tap Dismiss or Snooze to stop the alarm"
- Rings stop automatically after the "Stop ringing after" time, followed by a "Missed alarm: 06:15 · Label"
  (or "Missed timer: Label") notification
- If another alarm or timer goes off while one rings, the newer one takes over, and the one it stopped
  gets a "Missed alarm" / "Missed timer" notification ("Stopped ringing when a timer went off")
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
- **Timer** tab (bottom tabs; a side rail in landscape) with **several timers**, each with an
  optional **label** ("Eggs"). Type the time on a keypad (digits fill in from the right: 1-3-0 is
  1 min 30 s; the last time used is shown and typing replaces it), add a label if you like, then
  **Start**. Each timer is a card with a progress ring, **Pause/Resume**, **+1 min** and **Reset**;
  **+** adds another timer, tapping the label changes it, and **×** deletes the timer. A finished or
  reset timer stays in the list with its time and a **Start** button. While a timer runs, the status
  bar shows a timer icon and a live countdown notification per timer ("Eggs", or "Eggs paused ·
  4:32") with the same buttons. When one ends it rings like an alarm (sound, vibration, ring screen
  over the lock screen: "Time's up: Eggs") with **Stop** and **+1 min**; if another timer or alarm
  ends while one rings, the newer one takes over the sound and the ring screen (the stopped one gets a
  "Missed" notification). Timers keep running
  through app updates, clock changes and reboots (a timer that ended while the phone was off rings
  if it's only a little late)
- **Stopwatch** tab (one stopwatch): **Start**, then **Lap** and **Pause**; when paused, **Resume** and
  **Reset**. Shows hundredths ("1:05.42"); laps are listed newest first with each lap's time and the
  total so far, the lap in progress on top. While it runs or is paused, a notification shows it
  counting up ("Stopwatch", "Lap 3") with **Pause/Lap** or **Resume/Reset**. It keeps going through
  app updates, clock changes and reboots
- Warning banners (with a button to fix it) when something would stop alarms from being seen or
  heard: exact alarms not allowed, notifications off, full-screen alerts off (Android 14+),
  alarm volume at its lowest, Do Not Disturb silencing alarms

### Languages

English, Swedish, German, French, Spanish and Portuguese (Brazilian wording). The app follows the
phone's language; on Android 13+ it can also be set per app (Settings › Apps › Alarm Clock › Language).
Weekday names and time formats come from the language and region automatically.
Translations live in `app/src/main/res/values-<lang>/strings.xml` (`values/` is English).

### Screens

- Works on small phones, large phones and tablets, in portrait and landscape. Dialogs scroll when
  the window is short; on tablets the list is at most 640 dp wide and centred; in a short window
  (landscape phone) "Add alarm" is in the top bar instead of a floating button.
- Open dialogs, and what was changed in them, survive rotating the phone.
- Large text (accessibility font size) wraps instead of being cut off; day buttons have touch areas
  at least 48 dp tall; screen readers name the alarm on each switch and delete button.

## Opening the project

1. Android Studio → **File › Open…** → select this `alarm` folder.
2. Let Gradle sync (Android Studio downloads Gradle 9.9 from `gradle/wrapper/gradle-wrapper.properties`).
3. Run the `app` configuration on a device or emulator (Android 8.0 / API 26 or newer).

From the command line: `./gradlew assembleDebug` (needs `ANDROID_HOME` or a `local.properties`
with `sdk.dir=...`; Android Studio creates that file on first sync). Gradle needs a JDK it supports — JDK 25
requires Gradle ≥ 9.1 (the project uses 9.9.2); Android Studio's bundled JDK works: `JAVA_HOME=<android-studio>/jbr ./gradlew assembleDebug`.

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
opened it with. Or use `adb install AlarmClock-2.4-release.apk`. Debug builds have the id
`io.github.henriksten65.alarmclock.debug` and the name "Alarm Clock Debug", so they install next
to the release app.

## Versions

AGP 9.5.2 · Gradle 9.9.2 · Kotlin 2.4.21 · Compose BOM 2026.11.01 · compileSdk 37.1 · targetSdk 37 · minSdk 26.
AGP 9 compiles Kotlin itself, so there is no separate `org.jetbrains.kotlin.android` plugin.
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
  Timer.kt            the timers: list + state, their AlarmManager alarms, countdown notifications
  TimerReceiver.kt    timer done + notification buttons (Pause/Resume/+1 min/Reset)
  Stopwatch.kt        the stopwatch: state, laps, count-up notification
  StopwatchReceiver.kt  stopwatch notification buttons (Pause/Resume/Lap/Reset)
  UpcomingAlarm.kt    "Upcoming alarm" notification two hours before (own AlarmManager reminder)
  AlarmReceiver.kt    fires when an alarm goes off; handles Snooze/Dismiss actions
  AlarmService.kt     foreground service: sound, vibration, ringing notification
  BootReceiver.kt     reschedules alarms after reboot / time changes
  RingActivity.kt     full-screen "alarm is ringing" activity
  MainActivity.kt     entry point, asks for notification permission
  TimeFormat.kt       time formatting helpers (respects 12/24-hour setting)
  ui/                 Compose screens and theme
app/src/test/…        automated tests (see "Automated tests")
```

## Permissions

| Permission | Why |
|---|---|
| `USE_EXACT_ALARM` (API 33+) / `SCHEDULE_EXACT_ALARM` (API 31–32) | exact alarm times |
| `POST_NOTIFICATIONS` | ringing, upcoming, missed-alarm, timer and stopwatch notifications (asked at first launch on Android 13+) |
| `USE_FULL_SCREEN_INTENT` | show the ring screen over the lock screen |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` (Android 14+), `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (older) | play the alarm sound from a foreground service |
| `RECEIVE_BOOT_COMPLETED` | restore alarms after reboot |
| `VIBRATE`, `WAKE_LOCK` | vibration, keep the device awake while ringing |

Note: Google Play only allows `USE_EXACT_ALARM` and `USE_FULL_SCREEN_INTENT` for apps whose
core function is an alarm clock — which this is — but you'll need to declare that in the Play
Console if you ever publish it.

## Automated tests

`./gradlew testDebugUnitTest` (with `JAVA_HOME=<android-studio>/jbr`, see above) runs 239 tests on the
JVM in about 15 seconds — no device or emulator needed. Report: `app/build/reports/tests/testDebugUnitTest/index.html`.

| Test class | Covers |
|---|---|
| `AlarmScheduleTest` | next ring time: one-time/weekday alarms, month/year end, DST changes, Skip next, snooze |
| `ui/ParseDigitsTest` | typed time entry ("630" → 6:30, 12/24-hour, invalid input) |
| `AlarmRepositoryTest` | JSON format incl. version 1 data, ordering, ids never reused, Direct Boot storage + migration |
| `AppSettingsTest` | defaults, persistence, unknown stored values (incl. the keypad layout) |
| `TimeFormatTest` | card/summary texts, week order per locale, countdowns |
| `AlarmSchedulerTest` | AlarmManager scheduling, snooze, skip, rescheduling after reboot (missed alarms) / time change |
| `TimerTest` | timers: add/start/pause/resume/+1 min/reset/delete, labels, independence, their alarms and notifications, ringing, reboots, moving 3.0's timer over |
| `ui/TimerScreenTest`, `ui/LabelDialogTest` | Alarms/Timer tabs, the keypad (phone and calculator order) with a label, timer cards (Start/Pause/Resume/Reset/delete), adding a second timer, Time's up, the label dialog |
| `StopwatchTest`, `ui/StopwatchScreenTest` | stopwatch: start/pause/resume/lap/reset, notification and its buttons, app restart, reboot and clock change, the Stopwatch tab |
| `UpcomingAlarmTest` | "Upcoming alarm" notification: reminder two hours before, shown/hidden as alarms change or ring, Dismiss |
| `ReceiversTest` | alarm fired, Snooze/Dismiss actions, boot/update/time-change broadcasts |
| `AlarmServiceTest` | ringing notification, sound fallback chain, vibration, auto-stop + missed-alarm/timer notification, a ring taken over by another |
| `AlarmHealthTest` | warning-banner checks (notifications, volume, Do Not Disturb) |
| `ui/RotationTest` | rotating between tab bar (portrait) and rail (landscape) keeps typed text |
| `RingActivityTest` | ring screen: Dismiss, Snooze, Back while ringing, closing when the alarm stops |
| `ui/AlarmListScreenTest`, `ui/KeyboardTimeDialogTest`, `ui/DaySelectorTest` | the screens, driven like a user (add/edit/delete/undo, skip, banners, settings, the calculator keypad in the time dialog) |
| `TranslationsTest` | every language has every string with the same placeholders and plural forms; all strings format at runtime |
| `ui/ScreenSizesTest` | landscape, small phone, tablet and large-text layouts, touch-target sizes, screen-reader labels |

The Android parts run on [Robolectric](https://robolectric.org/) (a simulated Android 15). Not covered:
real sound output, the lock screen, real reboots and Direct Boot, and rotating the phone with a dialog
open — check those on a device.

## Testing tips

- Set an alarm one or two minutes ahead, then lock the screen to see the full-screen ring UI.
- On an emulator, change the system time in Settings to test daily repeats and time-zone handling.
- `adb shell dumpsys alarm | grep alarmclock` shows what's currently scheduled.

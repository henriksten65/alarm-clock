# Handoff — Android Alarm Clock app

_Written 2026-11-20 at the end of a long session, last updated 2026-11-21 after version 2.4. Read this first, then `README.md`._

## 1. Goal

The user is building a personal **Android alarm clock app** (Kotlin, Jetpack Compose,
Material 3) in `/mnt/data2/dev/Claude/alarm`. It was started in a Claude desktop app
conversation and has been developed in Claude Code since. There's no fixed spec: the user
asks for features one at a time, tests them in the emulator, and then says
**"Save this as version X"**.

## 2. Current state (as of this handoff)

- **Version 2.0** (`versionCode 5`), tagged `v2.0` on `main`. After the tag: this `HANDOFF.md`
  and `tools/emulator/` (see §7).
- **Version 2.1** (`versionCode 6`), tagged `v2.1`: the 2026-11-21 features (see the table below).
- **Version 2.2** (`versionCode 7`), tagged `v2.2`: the release-build setup (app id, signing, minify).
- **Version 2.3** (`versionCode 8`), tagged `v2.3`: "Skip next".
- **Version 2.4** (`versionCode 9`), tagged `v2.4`: Settings → Appearance (same as phone / light / dark).
- **Version 2.5** (`versionCode 10`), tagged `v2.5`: 147 automated tests; two small edit-dialog fixes they found.
- **Version 2.6** (`versionCode 11`), tagged `v2.6`: screen sizes, landscape, rotation, large text, screen readers (156 tests).
- **Version 2.7** (`versionCode 12`), tagged `v2.7`: translations (sv, de, fr, es, pt); 162 tests.
- **Version 2.8** (`versionCode 13`), tagged `v2.8`: library upgrade (AGP 9.5.1, Gradle 9.8.2, Kotlin 2.4.21, Compose BOM 2026.09.00, compileSdk 37).
- **Version 2.9** (`versionCode 14`), tagged `v2.9`: targetSdk 37 (Android 17).
- **Version 2.10** (`versionCode 15`), tagged `v2.10`: "Upcoming alarm" notification 2 h before an alarm (175 tests).
- **Version 3.0** (`versionCode 16`), tagged `v3.0`: Timer tab with a status-bar countdown (197 tests).
- **Version 3.1** (`versionCode 17`), tagged `v3.1`: a new alarm in Keyboard mode opens straight on the typed-time dialog (keypad up).
- **Version 3.2** (`versionCode 18`), tagged `v3.2`: several timers with labels (211 tests).
- **Version 3.3** (`versionCode 19`), tagged `v3.3`: "Missed" notice when a ring is taken over by another alarm/timer (215 tests).
- **Version 3.4** (`versionCode 20`), tagged `v3.4`: lint fixes and patch upgrades (Gradle 9.9.2, AGP 9.5.2, lifecycle 2.11.1, Robolectric 4.18.1); no user-visible changes.
- **Version 3.5** (`versionCode 21`), tagged `v3.5`: Compose BOM 2026.11.01, compileSdk 37.1; lint clean; no user-visible changes.
- **Version 3.6** (`versionCode 22`), tagged `v3.6`: Stopwatch tab (one stopwatch, laps, count-up notification; `Stopwatch.kt`, `StopwatchReceiver.kt`, `ui/StopwatchScreen.kt`); 231 tests.
- **Version 3.7** (`versionCode 23`), tagged `v3.7`: Settings → **Number keypad** (Calculator 7 8 9 on top, **the default**
  (user's choice 2026-11-25) / Phone 1 2 3 on top; `AppSettings.keypadLayout`, `KeypadLayout`; shared `ui/Keypad.kt`).
  Timer keypad follows it; with Calculator the typed-time dialog shows the app keypad instead of the IME (read-only
  display + hardware keys via `onPreviewKeyEvent`; in windows < 480 dp high the keypad sits beside the time, no title,
  platform width off, max 520 dp). Plus the rotation fix (dialogs/typed text kept, see §5); 239 tests.
- **Version 3.8** (`versionCode 24`), tagged `v3.8`: keypad easter egg (three secret sequences, see §5); no other
  changes. Not mentioned in the release notes, on purpose; 246 tests.
- GitHub Releases with the signed APK exist for `v2.3`–`v2.10` and `v3.0`–`v3.8` (latest); see §3 for how they're made.
- It builds and lints cleanly. Everything below was **tested by hand on the emulator**.
  Since 2.5 there are automated tests (now **231** (`./gradlew testDebugUnitTest`, ~15 s, JVM +
  Robolectric; see README "Automated tests" and §7).
- 2.6: screen-size, rotation, large-text and screen-reader fixes (§5 "Screens").
- Git history and tags:

  | Tag / commit | Contents |
  |---|---|
  | `v1.0` | Base app: alarm list, add/edit, label, daily repeat, `setAlarmClock` exact alarms, foreground ringing service, full-screen ring screen over lock screen, snooze/dismiss, reschedule on boot/time change |
  | `v1.1` | Settings dialog (gear icon); time entry by **clock dial** or **typing digits on the number keypad**; "Typed times use 24-hour clock" setting |
  | `v1.2` | **Weekday selection** per alarm (replaces "repeat daily", with migration); **alarm sound setting** (system alarm sounds or any audio file) |
  | `v1.3` | **Direct Boot** (rings after a reboot before unlock); **ringing alarm can always be stopped from the app** (Ringing card, app opens the ring screen); **warning banners** (exact alarms, notifications off, full-screen alerts off, alarm volume at its lowest, Do Not Disturb silencing alarms); **visible, cancellable, persistent snooze** |
  | `cc23d9a` | Undo after deleting an alarm (15 s snackbar; ids never reused) |
  | `a5e7abb` | "Next alarm: tomorrow 07:30 · in 9 h 20 min" summary |
  | `21149e2` | "Missed alarm" notification when an alarm rings 10 min undismissed |
  | `4f8e341` | Back on the ring screen doesn't hide a ringing alarm (shows a hint) |
  | `v2.0` | Version bump only (these four commits) |
  | `v2.1` | Settings → Ringing (snooze length, stop-ringing-after, vibrate); sound per alarm; ringing service type `systemExempted` on 14+ (boot-window crash fix); alarms missed while the phone was off ring late or notify |
  | `v2.2` | Ready for installing directly (no Play upload yet, user's choice): app id `io.github.henriksten65.alarmclock` (GitHub user `henriksten65`), release signing, R8 minify + resource shrinking, debug builds get `.debug` |
  | `v2.3` | "Skip next" for repeating alarms (`Alarm.skippedAt`); cards refresh every minute |
  | `v2.4` | Settings → **Appearance**: same as phone (default), light, dark |
  | `v3.2` | **Several timers with labels** (cards, + to add, tap label to rename, × to delete; one notification each); ring screen follows whatever rings now |
  | `v3.3` | **Missed notice on takeover**: when another alarm or timer rings while one rings, the stopped one gets "Missed alarm/timer … — Stopped ringing when a timer/another alarm went off"; a timer that rings until auto-stop gets "Missed timer" too. User decided: finished timers stay in the list; card text colour stays |
  | `v3.4` | **Maintenance**: lint warnings fixed (toUri, mipmap-anydpi, InlinedApi note); Gradle 9.9.2, AGP 9.5.2, lifecycle 2.11.1, Robolectric 4.18.1. Compose BOM kept (2026.11.01 needs compileSdk 37.1) |
  | `v3.5` | **Compose upgrade**: Compose BOM 2026.11.01, compileSdk 37.1 (`android-37.1` installed by the user); lint shows no warnings |
  | `v3.6` | **Stopwatch tab**: one stopwatch with Start/Lap/Pause/Resume/Reset, hundredths, laps (lap time + total, newest first), count-up notification with buttons; survives updates, clock changes and reboots |
  | `v3.8` | Keypad easter egg (secret; not in the release notes or README) |
  | `v3.7` | **Number keypad** setting (calculator layout by default, app keypad in the typed-time dialog); rotating keeps open dialogs and typed text |
  | `v3.1` | New alarm with Keyboard time entry opens on the typed-time dialog with the keypad up (`mkalarm.sh` no longer taps the time) |
  | `v3.0` | **Timer** tab (keypad entry, Pause/Resume/+1 min/Reset, countdown notification in the status bar, rings like an alarm) |
  | `v2.10` | **Upcoming alarm** notification from 2 h before (status-bar icon, Dismiss skips that ring) |
  | `v2.9` | **targetSdk 37** (Android 16/17 behaviour changes checked: background audio, predictive back) |
  | `v2.8` | **Library upgrade**: AGP 9.5.1, Gradle 9.8.2, Kotlin 2.4.21, Compose BOM 2026.09.00, AndroidX, compileSdk 37 |
  | `v2.7` | **Translations**: Swedish, German, French, Spanish, Portuguese; per-app language on Android 13+; long button texts on cards wrap |
  | `v2.6` | **Screens**: dialogs scroll and survive rotation, landscape top-bar "Add", tablet width cap, large-text wrapping, 48 dp day touch targets, screen-reader names on switch/delete |
  | `v2.5` | **Automated tests** (147, `./gradlew testDebugUnitTest`); day buttons sized explicitly; keyboard focus requested inside the time dialog |

- **Emulator apps** (rebuilt 2026-11-22 after the emulator reset, see §8): `com.example.alarmclock`
  is the old debug build (**v1.0** code now) with the user's alarm. `io.github.henriksten65.alarmclock`
  is the release APK (2.9). `io.github.henriksten65.alarmclock.debug` is the
  current debug build. The two new ones have no alarms; Keyboard time entry + 24-hour typing in both.
  The emulator's own dark theme is **off** (`cmd uimode night no`); system clock set to 24-hour.
- **Emulator state:** the AVD `Medium_Phone_API_37.0` runs as `emulator-5554`. It contains the user's
  **09:54 one-time alarm (switched off)** and a "Test 10:58" alarm in `com.example.alarmclock`; don't
  delete them. Lock screen: swipe; alarm volume 6/7. `test_tone.wav` is no longer on the emulator.
  **Start it with a cold boot (`emulator -avd Medium_Phone_API_37.0 -no-snapshot-load`)**: its only
  snapshot is from 2026-11-18 and a quick boot resets the storage to that day.

## 3. Build & run (environment specifics)

- **JDK:** the system Java and Android Studio's bundled JBR are both **JDK 25**, so Gradle
  must be **≥ 9.1** (now 9.9.2; AGP 8.x doesn't run on Gradle ≥ 9.6). Build with:
  `JAVA_HOME=/mnt/data2/dev/android-studio/jbr ./gradlew installDebug`
- **GitHub Release** (when the user asks for "a release"): after the version commit + tag are
  pushed, `gh release create vX <apk> --repo henriksten65/alarm-clock --title "Alarm Clock X"
  --notes-file <notes> --verify-tag --latest`. Notes follow the earlier ones (`gh release view
  v2.4`): New in X / Recent changes / Features / Install (2 steps, "installs over … keeps your
  alarms") / SHA-256 of the APK. Then `gh release download` it again and compare the checksum.
- **Release APK:** `./gradlew assembleRelease` →
  `app/build/outputs/apk/release/AlarmClock-<version>-release.apk`, signed with
  `~/.android/keystores/alarmclock-release.jks` (alias `alarmclock`). The passwords are in the
  gitignored `keystore.properties` in the repo root. Without that file the release APK is
  unsigned. The user was told to back both up: every update must use the same key.
- Versions (upgraded 2026-11-22, patch upgrades 2026-11-24): AGP 9.5.2 · Gradle 9.9.2 · Kotlin 2.4.21 · Compose BOM 2026.11.01 ·
  core 1.19.1 · lifecycle 2.11.1 · activity 1.13.0 · Robolectric 4.18.1 · compileSdk **37.1** (required by
  these AndroidX versions; platform `android-37.0` is installed, `android-36` was auto-installed too; Compose BOM 2026.11 needs 37.1: `android-37.1` installed 2026-11-24, set with `compileSdk { version = release(37) { minorApiLevel = 1 } }`) ·
  targetSdk **37** (since 2.9) · minSdk 26. AGP 9 has built-in Kotlin: the `kotlin-android` plugin was removed (keep the
  `kotlin-compose` plugin; its version sets the Kotlin version). Versions were picked ≥ ~2 weeks old;
  since 2026-11-24 all are the newest; no lint or Kotlin compiler warnings (check with `--rerun-tasks` to see compiler warnings).
- **targetSdk 37** (Android 16 + 17 behaviour changes), checked on the API 37 emulator: the alarm rings
  with sound with the screen off (`dumpsys audio`: `state:started`, `mutedState:none`); Back on the ring
  screen still only shows the hint (predictive back; we use `OnBackPressedCallback`); "Turn up" still
  sets the volume. Android 17's background-audio hardening allows our case: a non-short foreground
  service playing `USAGE_ALARM` with the exact-alarm permission. `dumpsys audio` → "Hardening
  enforcement" logs "background playback would be muted … level: full … USAGE_ALARM" for every ring
  (also at target 35 and for the old app): that's what the strict test mode
  (`cmd audio set-hardening enable`) would do, not the normal behaviour. Watch this if a future Android
  tightens it; a fix would be to start the service with while-in-use rights.
- **Since Compose 1.12:** Escape on a hardware keyboard closes a dialog (Back still hides the keyboard
  first). `mkalarm.sh` now hides the keyboard with Back. Under Robolectric, opening the typed-time dialog
  on top of the edit dialog hangs, so `AlarmListScreenTest.addAnAlarm` doesn't retype the time. Likewise the timer
  `LabelDialog` hangs over the Timer screen (and under `PHONE_SCREEN`), so `LabelDialogTest` drives it alone.
- SDK: `~/Android/Sdk` (`local.properties`); adb: `~/Android/Sdk/platform-tools/adb`.
- Android Studio lives in `/mnt/data2/dev/android-studio`. "No target device found" in Studio
  just means the emulator isn't running; start it from Device Manager.
- The repo has a **local** git identity: user `henriks`, email = the GitHub no-reply address
  `17674524+henriksten65@users.noreply.github.com`. The history was rewritten to it before the first push,
  so the user's Gmail address isn't public; keep it that way.
- **GitHub:** public repo `github.com/henriksten65/alarm-clock` (remote `origin`). `gh` is logged
  in as `henriksten65`. Push `main` and the tags (`git push --follow-tags`) when the user asks. Commit messages end with the
  `Co-Authored-By` attribution line that the harness specifies.

## 4. Architecture (where things are)

`app/src/main/java/io/github/henriksten65/alarmclock/`

| File | Role |
|---|---|
| `Alarm.kt` | Data class: `id, hour, minute, label, enabled, days` (bitmask, bit `d-1` per `Calendar` day; 0 = one-time), `snoozedUntil` (epoch ms, 0 = none), `soundUri` (null = sound from Settings), `skippedAt` (one skipped occurrence, epoch ms; only counts while it equals the next occurrence, so it lapses by itself). `nextTriggerMillis()`, `nextRingMillis()` (snooze-aware), JSON incl. migration from old `repeatDaily` |
| `AlarmRepository.kt` | Alarms as JSON in SharedPreferences, exposed as `StateFlow`. `nextId()` uses a persisted counter (`last_id`), so ids are never reused |
| `AppSettings.kt` | Settings `StateFlow`s: `timeEntryMode` (DIAL/KEYBOARD), `keyboard24Hour`, `alarmSoundUri`, `snoozeMinutes`, `autoStopMinutes`, `vibrate`, `themeMode` (SYSTEM/LIGHT/DARK) |
| `Storage.kt` | `deviceProtectedPrefs()`: device-protected storage for Direct Boot, plus a one-time move from normal storage |
| `Timer.kt` | The timers (`Timer` object + `TimerState` list as JSON in device-protected prefs `timer`, key `timers`; `last_id` (ids never reused), `duration` = last keypad time). Each: `setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP)` → `TimerReceiver` `TIMER_DONE` (extra `EXTRA_TIMER_ID`, data `timer://id` keeps PendingIntents apart) → `AlarmService` with id `Timer.ringId(id)` = −1000 − id (`timerIdOf` maps back), so it rings like an alarm. Ongoing countdown notification per timer (id 3000 + timer id, channel `timer`). Finished/reset timers stay IDLE in the list. `restore()` from `BootReceiver` (boot/update/time change): same boot → elapsed time; new boot (`Settings.Global.BOOT_COUNT`) → wall clock, rings if < auto-stop late. 3.0's single timer (flat prefs keys) is moved to timer 1 on first load |
| `TimerReceiver.kt` | Timer done + notification actions (Pause/Resume/+1 min/Reset), per timer id |
| `ui/MainScreen.kt` | Alarms / Timer tabs: `NavigationBar`, or `NavigationRail` below 480 dp height; Back on Timer → Alarms. `MainActivity` keeps the tab (saved state; `EXTRA_TAB` from the timer notification) |
| `ui/TimerScreen.kt` | Keypad entry with label (`timerDigitsToMillis`, hhmmss from the right; shown alone when there are no timers, else after +), timer cards (one shared 100 ms tick), `LabelDialog`; keypad side-by-side above 560 dp width |
| `UpcomingAlarm.kt` | "Upcoming alarm" notification 2 h before: a second AlarmManager alarm (`setExactAndAllowWhileIdle`, action `UPCOMING`) per alarm, kept in step by `AlarmScheduler.schedule`/`cancel`; posted right away if the alarm is < 2 h away; hidden when it rings; Dismiss → `AlarmScheduler.dismissUpcoming` |
| `AlarmScheduler.kt` | `setAlarmClock` scheduling, cancel, `rescheduleAll(catchUpMissed)`, `snooze`, `cancelSnooze`, `stopRinging`. Remembers each alarm's due time (`due_times` prefs) to find alarms missed while the phone was off |
| `AlarmReceiver.kt` | Alarm fired: clears the snooze, reschedules repeating alarms / disables one-time alarms, starts the service. Also handles Snooze/Dismiss actions |
| `AlarmService.kt` | Foreground service (`systemExempted` on 14+, mediaPlayback before): sound fallback chain, vibration, ringing notification with full-screen intent, 10-min auto-stop, **missed-alarm notification**, `ringingAlarmId` StateFlow, notification channels (`ringing_alarms`, `missed_alarms`) |
| `AlarmSound.kt` | Sound picker intent, display name, `accept` (persist read access), `releaseUnused` (at app start) |
| `AlarmHealth.kt` | Checks + settings intents behind the warning banners |
| `BootReceiver.kt` | `LOCKED_BOOT_COMPLETED`, `BOOT_COMPLETED`, package replaced, time/timezone change, exact-alarm permission change → `rescheduleAll` |
| `RingActivity.kt` | Full-screen ring UI over the lock screen (directBootAware); Back handling |
| `MainActivity.kt` | Creates channels, asks for POST_NOTIFICATIONS, **auto-opens RingActivity** when an alarm is ringing |
| `TimeFormat.kt` | Time formatting, weekday names/order, "Weekdays/Weekends/Mon, Wed" text, countdown texts |
| `ui/AlarmListScreen.kt` | Main screen: banners, Ringing card, next-alarm summary, alarm cards (snooze row), edit dialog, settings dialog, M3 TimePicker dialog, undo snackbar |
| `ui/KeyboardTimeDialog.kt` | Custom typed-digits time entry (`parseDigits`, visual transformation "630" → "6:30") |
| `ui/DaySelector.kt` | Seven round toggle buttons, in the locale's week order |
| `res/raw/fallback_alarm.wav` | Generated beep, last-resort sound (readable before unlock) |

Manifest: `AlarmReceiver`, `BootReceiver`, `AlarmService` and `RingActivity` are
`directBootAware`. Permissions: `USE_EXACT_ALARM` (33+) / `SCHEDULE_EXACT_ALARM` (≤32),
`POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`, `FOREGROUND_SERVICE(_SYSTEM_EXEMPTED, _MEDIA_PLAYBACK)`,
`RECEIVE_BOOT_COMPLETED`, `VIBRATE`, `WAKE_LOCK`.

## 5. Key decisions and why

- **`AlarmManager.setAlarmClock()`** for every alarm: exact, fires in Doze, shows the
  system alarm icon, and lets the receiver start a foreground service from the background.
- **SharedPreferences + JSON** instead of Room: little data, simple, and easy to move to
  device-protected storage.
- **Direct Boot** (v1.3): without it, a phone that reboots overnight (e.g. an automatic
  update) never rings until it's unlocked. Verified with a real PIN and reboot.
- **Sound fallback chain:** chosen sound → system default alarm → ringtone → notification →
  bundled `fallback_alarm.wav`. Before the first unlock the user's file isn't readable; in
  testing Android's cached default (`/data/system_de/0/ringtones/alarm_alert_cache`) played.
- **Alarm sound**: a global one in Settings, plus an optional sound per alarm ("Default" = the
  Settings sound). File read permissions are released only at a fresh app start, when no alarm
  or setting uses the file, so Undo/cancel never leaves an alarm with an unreadable file.
- **Snooze length, auto-stop and vibration are global settings** (like the alarm sound).
- **Appearance (light/dark)** is applied in Compose only: `AlarmClockTheme` reads `themeMode` and
  re-calls `enableEdgeToEdge` with a matching `SystemBarStyle` so the status-bar icons contrast.
  The window theme (`themes.xml`) and notifications still follow the phone; no AppCompat needed.
- **Missed while off**: after boot / app update (`catchUpMissed`; not on time/zone changes), an
  alarm whose stored due time passed rings now if less than the auto-stop time late, otherwise
  posts "Missed alarm … The phone was off when it was due" (one-time → off, repeating → next day).
- **FGS type `systemExempted`** (14+): `mediaPlayback` can't start during the BOOT_COMPLETED
  allowance on Android 15+ (crash: "FGS type mediaPlayback not allowed to start from BOOT_COMPLETED").
- **Weekdays as a `Calendar`-based bitmask**; `repeatDaily: true` migrates to all seven days.
  Day buttons follow the locale's first day of the week (Monday on the emulator).
- **Custom keyboard time entry**: digits are typed freely; the last two are the minutes
  ("630" → 6:30, "1845" → 18:45, "7" → 7:00). AM/PM switch on 12-hour phones unless
  "Typed times use 24-hour clock" is on. The field starts fully selected so typing replaces it.
- **The ringing state is an in-process `StateFlow`** in `AlarmService`. The app uses it for the
  Ringing card, to open the ring screen when launched, and to stop ringing when the alarm is
  switched off or deleted. This makes an alarm stoppable even with notifications blocked.
- **Banners:** "notifications off" takes precedence over "full-screen off" (FSI needs
  notifications anyway), and "Do Not Disturb silencing alarms" over "volume low". The
  volume check is **"at minimum or muted"**, not "0": on this Android the alarm stream's
  minimum is 1/7. "Turn up" sets about 2/3 of max.
- **Snooze** stored on the alarm (`snoozedUntil`) so it shows in the UI and survives
  reboots. For a repeating alarm, the snooze replaces the pending regular trigger (same
  PendingIntent); the regular schedule is recomputed when the snooze fires or is cancelled.
- **Undo** uses an Indefinite snackbar wrapped in `withTimeoutOrNull(15 s)` (the standard
  "Long" of 10 s was too short), plus a ✕ button.
- **Next-alarm countdown rounds up** (consistent with the "Alarm set for …" toast) and
  recomputes on every minute boundary **and** whenever the alarm list changes.
- **Screens** (2.6): the edit and settings dialogs scroll; dialog state is `rememberSaveable`
  (the edited alarm via `AlarmSaver`, JSON), so rotation keeps open dialogs and changes; content is
  capped at 640 dp (`MAX_CONTENT_WIDTH`); below 480 dp window height "Add alarm" moves to the top bar;
  sound buttons in Settings use `FlowRow`; day buttons: touch area = 1/7 of the row × ≥ 48 dp, circle
  ≤ 56 dp; switch/delete have "06:30 Work alarm" descriptions. Checked on the emulator at 360×640 dp,
  411×914 dp, 800×1280 dp, landscape and font scale 1.5 (`wm size`/`wm density`/`settings put system
  font_scale|user_rotation`; reset afterwards).
- **Translations** (2.7): `values-sv|de|fr|es|pt` (pt = Brazilian wording), plus `app_name` in
  `src/debug/res/values-*`. `androidResources.localeFilters` keeps only these languages (no mixed
  library strings); `generateLocaleConfig` + `res/resources.properties` give Android 13+'s per-app
  language setting. fr/es/pt plurals have `one`/`many`/`other`. `TranslationsTest` checks keys,
  placeholders and plural forms. Card rows (snooze/skip) use `FlowRow` so long button texts
  ("Nicht auslassen") move to their own line. Checked every language on the emulator with
  `cmd locale set-app-locales <pkg> --locales de-SE` (reset with `--locales ""`). Note: a plain
  `en` app locale means en-US, i.e. a 12-hour clock, which breaks `mkalarm.sh`.
- **Upcoming-alarm channel** (`upcoming`) is IMPORTANCE_DEFAULT with no sound/vibration, not LOW:
  Android puts LOW ones under "Silent" and hides their status-bar icon, and the point is the icon.
  (A first build used a LOW channel `upcoming_alarms`; importance can't be raised later, hence the new
  id. It only ever existed in the emulator's debug build.)
- **Timer rings through `AlarmService`/`RingActivity`** (id `Timer.RING_ID`) to reuse the sound
  fallback chain, vibration, auto-stop, full-screen intent and "opening the app goes to the ring screen".
  Not `setAlarmClock` (that would show the timer as the phone's "next alarm"); the timer notification
  is a plain ongoing notification, not a foreground service (Android 14+ lets users swipe it away;
  the timer keeps running).
- **One composition tree for both orientations** (`MainScreen`, fixed 2026-11-25): only the NavigationBar
  (Scaffold `bottomBar`) or the NavigationRail (first child of a `Row`) is switched; the tab's screen is always
  at the same place. Separate `Row`/`Scaffold` trees gave different `rememberSaveable` keys per orientation, so
  rotating closed open dialogs and rotating back resurrected older state. `RotationTest` guards this.
- **Easter egg** (user's request, 2026-11-25, after 3.7): three secret 10-digit sequences on the app's keypads (Timer,
  calculator time dialog; not the phone keyboard or hardware keys) show a "Greetings, Master!" toast. Stored only
  as SHA-256 hashes in `ui/Keypad.kt` (`SecretSequence`) because the repo is public; the user knows the digits.
  Deliberately not in the README.
- **Back on the ring screen** is consumed while ringing (hint toast); Home is still allowed.

## 6. Rejected approaches (don't retry these)

- **Gradle 8.x**: doesn't run on JDK 25 (the build fails with just "25.0.x").
- **Framework `TimePickerDialog`**: old teal Holo look; replaced by M3 `TimePicker`.
- **M3 `TimeInput`** for keyboard entry: it didn't replace existing digits on typing and
  didn't reliably move from hours to minutes. Replaced by `KeyboardTimeDialog`.
- **Renaming `mipmap-anydpi-v26` → `mipmap-anydpi`** (lint suggestion): aapt2 then failed
  with "resource mipmap/ic_launcher not found". Keep `-v26`.
- **Warning at alarm volume 0**: impossible on this Android (min 1).
- **`SnackbarDuration.Long`** for Undo: too short.
- **Testing "phone off" with `am force-stop`** (doesn't clear AlarmManager alarms on this Android)
  or `pm disable` of a component (not allowed for the shell). What worked: switch the alarms off,
  wait past the due time, write the saved `alarms.xml`/`due_times.xml` back with
  `run-as … sh -c 'cat > …'` (app force-stopped), then `adb reboot`.
  But careful: on 2026-11-21 a test alarm set just before `am force-stop` never rang and was gone
  from `dumpsys alarm`, so don't force-stop between creating a test alarm and its ring.

## 7. Testing workflow (how things were verified)

The user wants each change **checked thoroughly on the emulator** before committing.
Tools used:

- `tools/emulator/ui.py`: `dump`, `tap TEXT
  [--near TEXT2]`, `has TEXT` via `uiautomator dump`. `tools/emulator/mkalarm.sh HHMM
  [Monday …]` creates an alarm through the UI (needs Keyboard time entry + 24-hour typing;
  set `LABEL=Wake%sup` for a label, since `%s` is a space in `adb input text`).
- Package names below: debug builds are `io.github.henriksten65.alarmclock.debug`, release
  `io.github.henriksten65.alarmclock`. `run-as` works only on the debug build. Prefs:
  `/data/user_de/0/<package>/shared_prefs/{alarms,settings,due_times}.xml`.
- Check scheduling: `adb shell dumpsys alarm | grep -A2 io.github.henriksten65.alarmclock | grep origWhen`.
- Check ringing: `dumpsys activity services io.github.henriksten65.alarmclock` (ServiceRecord),
  `dumpsys audio | grep USAGE_ALARM | grep state:started`, `dumpsys media.player` (open file).
- Alarm volume: **`adb shell cmd audio set-volume 4 N`** / `get-stream-volume 4`
  (`cmd media_session volume` silently doesn't work here).
- DND: `cmd notification set_dnd none|priority|off`.
- Phone dark theme: `cmd uimode night yes|no` (set back to `no`). To check colours without
  looking, `screencap -p` and sample a background pixel (dark ≈ `(13,14,18)`, light ≈ `(250,248,254)`).
- Ring screen on demand: ``T=$(adb shell date -d @$(( $(adb shell date +%s) + 60 )) +%H%M);
  tools/emulator/mkalarm.sh $T``, then wait until `ui.py has Dismiss`.
- Notifications: after tapping "Don't allow", `pm grant` alone doesn't work; first run
  `pm clear-permission-flags io.github.henriksten65.alarmclock android.permission.POST_NOTIFICATIONS user-set user-fixed`.
  Revoking notifications also reset `USE_FULL_SCREEN_INTENT`; fix with
  `appops set io.github.henriksten65.alarmclock USE_FULL_SCREEN_INTENT allow`.
- Direct Boot test: `locksettings set-pin 1234`, create an alarm, `adb reboot`, don't unlock
  (`dumpsys user` shows `RUNNING_LOCKED`), wait for the ring; afterwards unlock,
  `locksettings clear --old 1234`, then `locksettings set-disabled false` (otherwise the lock
  screen stays fully disabled).
- The screen sleeps during long tests: `svc power stayon true` (set back to `false` afterwards).
- After every test, delete the test alarms and leave the user's 09:54 alarm alone.

### Automated tests (added for 2.5)

- `app/src/test/`: plain JVM tests plus Robolectric 4.16 (`robolectric.properties`: sdk 35) and
  Compose UI tests (`createComposeRule`). They run on JDK 25 (warnings about `System::load` are harmless).
- `TestSupport.resetAppState()` clears the singletons (`AlarmRepository`/`AppSettings` instance,
  `AlarmService._ringingAlarmId`) by reflection before each test; tests fix the zone to Europe/Berlin.
- UI tests use `PHONE_SCREEN` (`w411dp-h891dp-mdpi`); Robolectric's default 320×470 dp screen hid buttons.
  `AlarmListScreenTest` steps the Compose clock by hand (`settle()`), except the settings test.
- **Pitfalls found:** (1) `weight(1f).aspectRatio(1f)` in `DaySelector` made Compose re-measure the
  edit dialog forever under Robolectric (random 60 s hangs); now the buttons get an explicit size from
  `BoxWithConstraints`. (2) `KeyboardTimeDialog` requested focus from outside the dialog's
  composition ("FocusRequester is not initialized"); the `LaunchedEffect` is now inside the dialog.
  (3) Robolectric's `AlarmManager` doesn't throw when exact alarms are disallowed, so `schedule()`'s
  failure path is untested. (4) `StateRestorationTester` with a dialog open sometimes hangs, so
  rotation is tested only on the emulator (plus an `AlarmSaver` round-trip test). (5) Robolectric's
  text measuring differs from a phone's: it did not reproduce the squeezed German button, so that
  is checked on the emulator only. (6) A crash or hang in one Compose test can make later ones in the same
  JVM hang; when that happens, run classes one by one (`--tests …`) to find the culprit.

## 8. Open questions / unresolved issues

- **Not yet done from the improvement list:**
  - *Extra features:* (translations done in 2.7: sv, de, fr, es, pt).
  - *Publishing:* the user only wants an APK to install directly for now (2026-11-21). Still open
    for a later Play release (targetSdk is 37 now, above Play's requirement of 36):
    Console declarations (exact alarms, full-screen intent, systemExempted FGS), privacy policy,
    data-safety form, store graphics; an AAB (`bundleRelease`) instead of an APK.
- **Emulator reset (2026-11-22):** the emulator process exited overnight (cause unknown). Starting it
  again with a normal (quick) boot loaded the AVD's only snapshot, `default_boot` from 2026-11-18, which
  reset its storage to that day: the 2.x installs, the old app's state and settings were lost. Rebuilt:
  `com.example.alarmclock` (v1.0 there now) has a "Test 10:58" alarm and the user's **09:54 one-time
  alarm (off, no label)** written back into its prefs; release 2.7 and the debug build reinstalled with
  Keyboard + 24-hour typing; system 24-hour clock on; region not restorable from the shell (default);
  `test_tone.wav` gone. **Start the emulator with `-no-snapshot-load` (cold boot)** so the current disk
  state is kept, or let Android Studio save quick-boot state on a clean exit.
- **Unexplained emulator restart** at about 10:33 on 2026-11-20 during testing. Dropbox only had
  `SYSTEM_RESTART`, with no app or system_server crash. Probably not the app; watch for a repeat.
- **When the phone is unlocked and in use**, Android shows the alarm as a heads-up
  notification instead of opening the ring screen (platform behaviour; the user was told).
  Also cosmetic: the heads-up notification overlaps the top of the ring screen.
- **Appearance is Compose-only:** the window background before Compose draws, and all
  notifications (incl. the heads-up alarm), still follow the phone's theme. Not noticeable so far.
- Remaining lint (harmless): `UseKtx` (`Uri.parse` in `AlarmService`, `AlarmSound`),
  `InlinedApi` (a constant in `BootReceiver`), `ObsoleteSdkInt` (`mipmap-anydpi-v26` could be
  `mipmap-anydpi`), plus the version-upgrade warnings.

## 9. Immediate next step

Nothing is half-finished. **Ask the user what to do next**, offering the remaining items
from §8 (extra features, or preparing for publishing). Follow the established workflow:
implement → build/install → test thoroughly on the emulator (and fix what you find) →
update `README.md` → commit (one commit per item if asked) → when the user says "save as
version X", bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts`, commit
"Alarm Clock version X", and add an annotated tag `vX`. Push / make a GitHub Release only
when asked (§3).

## 10. Key artifacts

- Repo: `/mnt/data2/dev/Claude/alarm` (branch `main`, tags `v1.0`–`v1.3`, `v2.0`–`v2.4`),
  pushed to `github.com/henriksten65/alarm-clock`
- `README.md`: user-facing feature list, layout, permissions (kept up to date with each change)
- `HANDOFF.md`: this file
- `tools/emulator/ui.py`, `tools/emulator/mkalarm.sh`: emulator UI helpers
- `app/src/main/res/raw/fallback_alarm.wav`: bundled fallback sound
- Session transcript PDFs: `/mnt/data2/dev/Claude/AlarmClock-2.4-session.pdf`,
  `AlarmClock-2.7-session.pdf`, `AlarmClock-2.8-session.pdf` and `AlarmClock-2.9-session.pdf` (the
  2.5–2.9 session: tests, screens, translations, library upgrade, target SDK 37; the 2.7 and 2.8 ones
  are the same session up to those versions), kept **outside
  the repo** on purpose (user's choice; don't commit transcripts). It was rendered from the
  session JSONL with reportlab in a throwaway venv, with the Gmail address redacted.
- Claude memory: `~/.claude/projects/-mnt-data2-dev-Claude-alarm/memory/` (build environment, handoff pointer)

# Handoff — Android Alarm Clock app

_Written 2026-11-20 at the end of a long session, updated 2026-11-21. Read this first, then `README.md`._

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
- It builds and lints cleanly. Everything below was **tested by hand on the emulator**.
  There are **no automated tests**.
- Git history and tags:

  | Tag / commit | Contents |
  |---|---|
  | `v1.0` | Base app: alarm list, add/edit, label, daily repeat, `setAlarmClock` exact alarms, foreground ringing service, full-screen ring screen over lock screen, snooze/dismiss, reschedule on boot/time change |
  | `v1.1` | Settings dialog (gear icon); time entry by **clock dial** or **typing digits on the number keypad**; "Typed times use 24-hour clock" setting |
  | `v1.2` | **Weekday selection** per alarm (replaces "repeat daily", with migration); **alarm sound setting** (system alarm sounds or any audio file) |
  | `v1.3` | **Direct Boot** (rings after a reboot before unlock); **ringing alarm can always be stopped from the app** (Ringing card, app opens the ring screen); **warning banners** (exact alarms, notifications off, full-screen alerts off, alarm volume at its lowest, Do Not Disturb silencing alarms); **visible, cancellable, persistent snooze** |
  | `9c47399` | Undo after deleting an alarm (15 s snackbar; ids never reused) |
  | `4afdc1f` | "Next alarm: tomorrow 07:30 · in 9 h 20 min" summary |
  | `fd4217a` | "Missed alarm" notification when an alarm rings 10 min undismissed |
  | `1a6cf6b` | Back on the ring screen doesn't hide a ringing alarm (shows a hint) |
  | `v2.0` | Version bump only (these four commits) |
  | `v2.1` | Settings → Ringing (snooze length, stop-ringing-after, vibrate); sound per alarm; ringing service type `systemExempted` on 14+ (boot-window crash fix); alarms missed while the phone was off ring late or notify |
  | `v2.3` | "Skip next" for repeating alarms (`Alarm.skippedAt`); cards refresh every minute |
  | `v2.2` | Ready for installing directly (no Play upload yet, user's choice): app id `io.github.henriksten65.alarmclock` (GitHub user `henriksten65`), release signing, R8 minify + resource shrinking, debug builds get `.debug` |

- **Emulator apps:** three alarm apps are installed. `com.example.alarmclock` is the old
  debug build (v2.1 code) with the user's alarm. `io.github.henriksten65.alarmclock` is the
  release APK (2.2). `io.github.henriksten65.alarmclock.debug` is the current debug build. The two
  new ones have no alarms and default settings, apart from Keyboard + 24-hour typing in the release app.
- **Emulator state:** the AVD `Medium_Phone_API_37.0` was running as `emulator-5554`. It
  contains the user's own **09:54 one-time alarm (switched off)** in `com.example.alarmclock`; don't delete it. Settings on
  the emulator: time entry = Keyboard, "Typed times use 24-hour clock" = on, alarm sound =
  `test_tone.wav` (a test file pushed to `/sdcard/Music/`). The PIN used for Direct Boot
  testing was removed again, the lock screen is back to swipe, and the alarm volume is 6/7.

## 3. Build & run (environment specifics)

- **JDK:** the system Java and Android Studio's bundled JBR are both **JDK 25**, so Gradle
  must be **≥ 9.1**. Build with:
  `JAVA_HOME=/mnt/data2/dev/android-studio/jbr ./gradlew installDebug`
- **Release APK:** `./gradlew assembleRelease` →
  `app/build/outputs/apk/release/AlarmClock-<version>-release.apk`, signed with
  `~/.android/keystores/alarmclock-release.jks` (alias `alarmclock`). The passwords are in the
  gitignored `keystore.properties` in the repo root. Without that file the release APK is
  unsigned. The user was told to back both up: every update must use the same key.
- Versions: AGP 8.13.0 · Gradle 9.1.0 · Kotlin 2.2.20 · Compose BOM 2024.12.01 ·
  compile/target SDK 35 · minSdk 26.
- SDK: `~/Android/Sdk` (`local.properties`); adb: `~/Android/Sdk/platform-tools/adb`.
- Android Studio lives in `/mnt/data2/dev/android-studio`. "No target device found" in Studio
  just means the emulator isn't running; start it from Device Manager.
- The repo has a **local** git identity (user `henriks`). Commit messages end with the
  `Co-Authored-By` attribution line that the harness specifies.

## 4. Architecture (where things are)

`app/src/main/java/io/github/henriksten65/alarmclock/`

| File | Role |
|---|---|
| `Alarm.kt` | Data class: `id, hour, minute, label, enabled, days` (bitmask, bit `d-1` per `Calendar` day; 0 = one-time), `snoozedUntil` (epoch ms, 0 = none), `soundUri` (null = sound from Settings), `skippedAt` (one skipped occurrence, epoch ms; only counts while it equals the next occurrence, so it lapses by itself). `nextTriggerMillis()`, `nextRingMillis()` (snooze-aware), JSON incl. migration from old `repeatDaily` |
| `AlarmRepository.kt` | Alarms as JSON in SharedPreferences, exposed as `StateFlow`. `nextId()` uses a persisted counter (`last_id`), so ids are never reused |
| `AppSettings.kt` | Settings `StateFlow`s: `timeEntryMode` (DIAL/KEYBOARD), `keyboard24Hour`, `alarmSoundUri`, `snoozeMinutes`, `autoStopMinutes`, `vibrate` |
| `Storage.kt` | `deviceProtectedPrefs()`: device-protected storage for Direct Boot, plus a one-time move from normal storage |
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

## 8. Open questions / unresolved issues

- **Not yet done from the improvement list:**
  - *Extra features:* translations (all strings are English).
  - *Publishing:* the user only wants an APK to install directly for now (2026-11-21). Still open
    for a later Play release: no automated tests (candidates: `parseDigits`, `nextTriggerMillis`
    with weekdays/DST, `rescheduleAll` catch-up rules); outdated libraries (lint: AGP 9.5.1,
    Kotlin 2.4.21, Compose BOM 2026.09.00, compileSdk 36…); Play's target SDK requirement;
    Console declarations (exact alarms, full-screen intent, systemExempted FGS), privacy policy,
    data-safety form, store graphics; an AAB (`bundleRelease`) instead of an APK.
- **Unexplained emulator restart** at about 10:33 on 2026-11-20 during testing. Dropbox only had
  `SYSTEM_RESTART`, with no app or system_server crash. Probably not the app; watch for a repeat.
- **When the phone is unlocked and in use**, Android shows the alarm as a heads-up
  notification instead of opening the ring screen (platform behaviour; the user was told).
  Also cosmetic: the heads-up notification overlaps the top of the ring screen.
- Remaining lint (harmless): `UseKtx` (`Uri.parse` in `AlarmService`, `AlarmSound`),
  `InlinedApi` (a constant in `BootReceiver`), plus the version-upgrade warnings.

## 9. Immediate next step

Nothing is half-finished. **Ask the user what to do next**, offering the remaining items
from §8 (extra features, or preparing for publishing). Follow the established workflow:
implement → build/install → test thoroughly on the emulator (and fix what you find) →
update `README.md` → commit (one commit per item if asked) → when the user says "save as
version X", bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts`, commit
"Alarm Clock version X", and add an annotated tag `vX`.

## 10. Key artifacts

- Repo: `/mnt/data2/dev/Claude/alarm` (branch `main`, tags `v1.0`–`v1.3`, `v2.0`–`v2.3`)
- `README.md`: user-facing feature list, layout, permissions (kept up to date with each change)
- `HANDOFF.md`: this file
- `tools/emulator/ui.py`, `tools/emulator/mkalarm.sh`: emulator UI helpers
- `app/src/main/res/raw/fallback_alarm.wav`: bundled fallback sound
- Claude memory: `~/.claude/projects/-mnt-data2-dev-Claude-alarm/memory/` (build environment, handoff pointer)

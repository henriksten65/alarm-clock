#!/bin/bash
# mkalarm.sh HHMM [Day ...]  – create an alarm through the UI (keyboard time entry, 24-hour typing)
ADB=~/Android/Sdk/platform-tools/adb; UI="python3 $(dirname "$0")/ui.py"
HHMM=$1; shift
# With keyboard time entry, "Add alarm" opens straight on the typed-time dialog.
$UI tap "Add alarm"; sleep 1.5
for k in $(echo $HHMM | grep -o .); do $ADB shell input keyevent KEYCODE_$k; sleep 0.15; done
$ADB shell input keyevent KEYCODE_ENTER; sleep 0.6
# Hide the keyboard with Back (only when it's showing): Escape closes the whole dialog since Compose 1.12.
if [ -n "$LABEL" ]; then $UI tap Label; sleep 0.4; $ADB shell input text "$LABEL"; sleep 0.4
  $ADB shell dumpsys input_method | grep -q 'mInputShown=true' && $ADB shell input keyevent KEYCODE_BACK; sleep 0.4; fi
for d in "$@"; do $UI tap "$d"; sleep 0.2; done
$UI tap Save; sleep 0.8

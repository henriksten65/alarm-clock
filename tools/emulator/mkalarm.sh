#!/bin/bash
# mkalarm.sh HHMM [Day ...]  – create an alarm through the UI (keyboard time entry, 24-hour typing)
ADB=~/Android/Sdk/platform-tools/adb; UI="python3 $(dirname "$0")/ui.py"
HHMM=$1; shift
$UI tap "Add alarm"; sleep 0.8
T=$($UI dump | grep -oE "^'[0-9]{2}:[0-9]{2}'" | tail -1 | tr -d "'")
$UI tap "$T"; sleep 1
for k in $(echo $HHMM | grep -o .); do $ADB shell input keyevent KEYCODE_$k; sleep 0.15; done
$ADB shell input keyevent KEYCODE_ENTER; sleep 0.6
if [ -n "$LABEL" ]; then $UI tap Label; sleep 0.4; $ADB shell input text "$LABEL"; $ADB shell input keyevent KEYCODE_ESCAPE; sleep 0.4; fi
for d in "$@"; do $UI tap "$d"; sleep 0.2; done
$UI tap Save; sleep 0.8

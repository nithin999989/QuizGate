#!/usr/bin/env bash
# QuizGate native evidence run: executes on the runner while the emulator is up.
set -x
cd "$(dirname "$0")/.."
FAIL=0
mkdir -p shots
shot(){ adb exec-out screencap -p > "shots/$1.png" 2>/dev/null; }
dump(){ adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "shots/$1" >/dev/null 2>&1; }

adb wait-for-device
while [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do sleep 2; done
sleep 8

adb install -r app/build/outputs/apk/debug/app-debug.apk || FAIL=1
adb shell appops set com.quizgate.app SYSTEM_ALERT_WINDOW allow
adb push ci/test-resume.pdf /sdcard/Download/test-resume.pdf
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/test-resume.pdf
adb shell settings put global window_animation_scale 0; adb shell settings put global transition_animation_scale 0

# 1: app home
adb shell am start -n com.quizgate.app/.MainActivity
sleep 6; shot 01-app-home; dump ui-01.xml

# 2: start session (20s debug interval), leave to launcher, wait for popup
adb shell am startservice -n com.quizgate.app/.QuizSessionService -e topic "Spring Boot" --ei intervalMin 5 --ei debugSeconds 20
sleep 2
adb shell input keyevent 3
sleep 26
shot 02-popup-over-launcher; dump ui-02-popup.xml
adb shell dumpsys notification > shots/notification-dump.txt 2>&1

# 3: tap a WRONG answer, expect explanation screen
TAP=$(python3 ci/answer_tap.py shots/ui-02-popup.xml app/src/main/assets/interview-bank.json wrong) || FAIL=1
if [ -n "$TAP" ]; then adb shell input tap $TAP; fi
sleep 3; shot 03-after-wrong-answer; dump ui-03.xml
# 4: popup should dismiss itself after the 10s explanation
sleep 12; shot 04-after-countdown; dump ui-04.xml
if grep -qi quizgate shots/ui-04.xml 2>/dev/null; then echo "WARN: popup node still present after countdown"; fi
# 5: next popup should arrive on the timer
sleep 22; shot 05-second-popup; dump ui-05-popup.xml
python3 ci/answer_tap.py shots/ui-05-popup.xml app/src/main/assets/interview-bank.json correct >/dev/null || echo "WARN: second popup question not recognized"
# 6: notification shade over the popup (interaction check), then back
adb shell input swipe 400 10 400 700 300
sleep 2; shot 06-shade-over-popup
adb shell input keyevent 4
# 7: simulated incoming call while popup is up (call-UI overlap evidence)
adb emu gsm call 5550001111
sleep 5; shot 07-incoming-call-with-popup; dump ui-07-call.xml
adb emu gsm cancel 5550001111
sleep 2
# 8: end session, verify no further popups
adb shell am startservice -n com.quizgate.app/.QuizSessionService -a com.quizgate.app.END_SESSION
sleep 3; shot 08-after-end
sleep 22; shot 09-no-popup-after-end; dump ui-09.xml
if grep -qi quizgate shots/ui-09.xml 2>/dev/null; then echo "FAIL: popup appeared after END_SESSION"; FAIL=1; fi

# 9: resume picker -> OCR -> review (best effort)
adb shell am start -n com.quizgate.app/.MainActivity
sleep 5
dump ui-10-home.xml
T=$(python3 ci/find_tap.py shots/ui-10-home.xml "resume" || true)
if [ -n "$T" ]; then
  adb shell input tap $T; sleep 4; shot 10-picker; dump ui-10-picker.xml
  P=$(python3 ci/find_tap.py shots/ui-10-picker.xml "test-resume" || true)
  if [ -n "$P" ]; then
    adb shell input tap $P; sleep 10; shot 11-review; dump ui-11-review.xml
    grep -io "spring boot\|detected skills\|kafka\|docker" shots/ui-11-review.xml | sort -u > shots/ocr-evidence.txt
  else
    echo "WARN: test-resume.pdf not found in picker"
  fi
else
  echo "WARN: resume entry point not found on home"
fi

ls -la shots/
exit $FAIL

#!/usr/bin/env bash
# QuizGate native evidence run: executes on the runner while the emulator is up.
set -x
cd "$(dirname "$0")/.."
FAIL=0
mkdir -p shots
shot(){ adb exec-out screencap -p > "shots/$1.png" 2>/dev/null; }
dump(){ adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "shots/$1" >/dev/null 2>&1; }
# tap_text NEEDLE: scrolls the app WebView until a node containing NEEDLE is at least
# 24px tall on screen (fully visible), then taps its centre. Returns 1 if never found.
tap_text(){
  local needle="$1" minh="${2:-24}" reqc="${3:-}" x i
  for i in 1 2 3 4 5 6; do
    dump ui-scan.xml
    x=$(python3 ci/find_tap.py shots/ui-scan.xml "$needle" "$minh" "$reqc" || true)
    if [ -n "$x" ]; then adb shell input tap $x; return 0; fi
    adb shell input swipe 160 480 160 220 300; sleep 1
  done
  return 1
}

adb wait-for-device
while [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do sleep 2; done
sleep 8
# AOSP (target=default) images are userdebug: adbd can run as root, which is required
# to start the app's non-exported QuizSessionService from the shell.
adb root && adb wait-for-device

adb install -r app/build/outputs/apk/debug/app-debug.apk || FAIL=1
# NOTE: no appops/pm pregrants - the first-install permission UX below drives the real
# Settings overlay grant (and the Android 13+ notification prompt) through the UI.
adb push ci/test-resume.pdf /sdcard/Download/test-resume.pdf
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/test-resume.pdf
sleep 3
adb shell settings put global window_animation_scale 0; adb shell settings put global transition_animation_scale 0

# 1: app home
adb shell am start -n com.quizgate.app/.MainActivity
sleep 6; shot 01-app-home; dump ui-01.xml

# 1b: first-install permission UX. Overlay is NOT pre-granted: tapping Start must route to the
# app's "Display over other apps" Settings screen; grant via the UI toggle, return, start again.
# On Android 13+ the POST_NOTIFICATIONS runtime dialog follows. This is the unproven-by-pregrant flow.
if tap_text "Start phone-wide"; then
  sleep 3; shot ux-01-overlay-settings; dump ui-ux-01-settings.xml
  if ! grep -qi "display over other apps\|drawing over other apps" shots/ui-ux-01-settings.xml; then
    echo "FAIL: Start on fresh install did not route to the overlay Settings screen"; FAIL=1
  else
    # Settings depth varies by API/ROM: API 28 AOSP lands on the toggle screen directly; API 34
    # AOSP lands on App Info -> "Display over other apps" row -> app list -> app row -> toggle.
    # Drill down until the toggle label exists, then tap it.
    grant=0
    for depth in 1 2 3; do
      if tap_text "Allow display over other apps" 12 || tap_text "Permit drawing over other apps" 12; then grant=1; break; fi
      # clickable-rows-only: App Info has a "Display over other apps" row, the app list has a
      # "QuizGate development" row; non-clickable toolbar titles carry the same texts.
      tap_text "Display over other apps" 24 1 || tap_text "QuizGate development" 24 1 || break
      sleep 2
    done
    [ "$grant" = 1 ] || { echo "FAIL: overlay Settings toggle not found (all known paths)"; FAIL=1; }
    sleep 1; shot ux-01b-after-toggle
    adb shell appops get com.quizgate.app SYSTEM_ALERT_WINDOW | grep -qiE "allow|foreground" || { echo "FAIL: overlay appop not granted after UI toggle"; FAIL=1; }
    # BACK out of however many Settings screens stacked above the app.
    for b in 1 2 3; do
      adb shell input keyevent 4; sleep 2
      dump ui-back.xml; grep -q "com.quizgate" shots/ui-back.xml && break
    done
    if tap_text "Start phone-wide"; then
      sleep 2
      SDKV=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
      if [ "${SDKV:-0}" -ge 33 ]; then tap_text "Allow" || echo "WARN: notification permission dialog not handled"; sleep 2; fi
      sleep 3; shot ux-02-session-started; dump ui-ux-02-started.xml
      adb shell dumpsys activity services com.quizgate.app > shots/service-dump-ux-start.txt 2>&1
      grep -q QuizSessionService shots/service-dump-ux-start.txt || { echo "FAIL: session not running after first-install permission UX"; FAIL=1; }
      grep -qi "End phone-wide" shots/ui-ux-02-started.xml || echo "WARN: home did not switch to End state after UX start"
      if tap_text "End phone-wide"; then
        sleep 3; shot ux-03-session-ended; dump ui-ux-03-ended.xml
        adb shell dumpsys activity services com.quizgate.app > shots/service-dump-ux-end.txt 2>&1
        grep -q QuizSessionService shots/service-dump-ux-end.txt && { echo "FAIL: UI End did not stop the session after the UX flow"; FAIL=1; }
      else
        echo "FAIL: End phone-wide button not found after UX start"; FAIL=1
      fi
    else
      echo "FAIL: Start phone-wide not found after returning from overlay Settings"; FAIL=1
    fi
  fi
else
  echo "FAIL: Start phone-wide button not found on home (first-install UX)"; FAIL=1
fi

# 2: start session (20s debug interval), leave to launcher, wait for popup
adb shell "am startservice -n com.quizgate.app/.QuizSessionService -e topic 'Spring Boot' --ei intervalMin 5 --ei debugSeconds 20"
sleep 2
adb shell dumpsys activity services com.quizgate.app > shots/service-dump-start.txt 2>&1
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
adb shell "am startservice -n com.quizgate.app/.QuizSessionService -a com.quizgate.app.END_SESSION"
sleep 2
adb shell dumpsys activity services com.quizgate.app > shots/service-dump-end.txt 2>&1
sleep 3; shot 08-after-end
sleep 22; shot 09-no-popup-after-end; dump ui-09.xml
if grep -qi quizgate shots/ui-09.xml 2>/dev/null; then echo "FAIL: popup appeared after END_SESSION"; FAIL=1; fi

# 9: unprivileged UI start/stop path (the normal user flow: home button -> service on, end button -> service off)
# --activity-clear-top: Settings screens opened from the app stack into its task; pop them so home is visible.
adb shell am start --activity-clear-top -n com.quizgate.app/.MainActivity
sleep 5
dump ui-11-home.xml
if tap_text "Start phone-wide"; then
  sleep 3; shot 11-ui-session-started; dump ui-11-started.xml
  adb shell dumpsys activity services com.quizgate.app > shots/service-dump-ui-start.txt 2>&1
  grep -q QuizSessionService shots/service-dump-ui-start.txt || { echo "FAIL: UI Start button did not start QuizSessionService"; FAIL=1; }
  grep -qi "End phone-wide" shots/ui-11-started.xml || echo "WARN: home did not switch to End state after UI start"
  if tap_text "End phone-wide"; then
    sleep 3; shot 12-ui-session-ended; dump ui-12-ended.xml
    adb shell dumpsys activity services com.quizgate.app > shots/service-dump-ui-end.txt 2>&1
    grep -q QuizSessionService shots/service-dump-ui-end.txt && { echo "FAIL: UI End button did not stop QuizSessionService"; FAIL=1; }
    grep -qi "Start phone-wide" shots/ui-12-ended.xml || echo "WARN: home did not switch back to Start state after UI end"
  else
    echo "FAIL: End phone-wide button not found after UI start"; FAIL=1
  fi
else
  echo "FAIL: Start phone-wide button not found on home"; FAIL=1
fi

# 10: resume picker -> OCR -> review (best effort)
adb shell am start --activity-clear-top -n com.quizgate.app/.MainActivity
sleep 5
dump ui-10-home.xml
if tap_text "Choose resume"; then
  sleep 8; shot 10-picker; dump ui-10-picker.xml
  if tap_text "test-resume" 12; then
    sleep 10; shot 13-review; dump ui-13-review.xml
    grep -io "spring boot\|detected skills\|kafka\|docker" shots/ui-13-review.xml | sort -u > shots/ocr-evidence.txt
    [ -s shots/ocr-evidence.txt ] || echo "WARN: review screen showed no recognised resume skills"
  else
    shot 10-picker-scrolled
    echo "WARN: test-resume.pdf not found in picker"
  fi
else
  echo "WARN: resume entry point not found on home"
fi

ls -la shots/
exit $FAIL

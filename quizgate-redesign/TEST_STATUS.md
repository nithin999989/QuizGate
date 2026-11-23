# Test status

Version 0.3.0-dev. Read this before making any runtime, safety or release claim.

## Verified

- Android debug build succeeds with the phone-wide session code and launcher icon.
  Unit suite: 10 matcher tests, zero failures (`:app:testDebugUnitTest`).
- The debug manifest declares exactly the voluntary-session permissions:
  SYSTEM_ALERT_WINDOW, FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE and
  POST_NOTIFICATIONS. No internet, accessibility, usage-access, phone-state or
  broad storage permission. The service is non-exported, with
  foregroundServiceType specialUse and a plain-language property.
- Launcher icon mipmaps rendered as real PNGs and visually inspected at
  288/144/96/72/48 px in circular and square masks. Launcher-rendered pixels on
  a device are still outstanding.

## Implemented, not runtime-verified

- Phone-wide voluntary session: QuizSessionService (foreground service) with an
  overlay popup (TYPE_APPLICATION_OVERLAY) hosting the same packaged quiz UI.
  The user starts and ends the session from the app; End session also exists in
  the popup topbar and the persistent notification. Interval uses the saved
  5/10/15-minute choice; the topic is the bank topic selected at session start.
  The popup is sized to leave the status bar uncovered and passes outside
  touches through (FLAG_NOT_TOUCH_MODAL). A correct answer dismisses
  immediately; a wrong answer shows a ten-second explanation, then dismisses.
- Overlay-permission flow: the app routes to the system "Display over other
  apps" screen when the permission is missing. Notification runtime permission
  is requested only on Android 13+.
- Debug test hook: an intent extra shortens the popup interval for on-device
  testing; it is gated to debuggable builds, and the non-exported service means
  only the app itself or a developer shell can pass it.
- Picker -> on-device ML Kit OCR -> review -> matched practice flow.

## Design review notes (code-level, not device evidence)

- Overlay window addition is exception-guarded: on failure the popup is cleaned
  up and the session ends instead of crashing.
- START_NOT_STICKY: a system-killed session stays ended; the user restarts it
  deliberately.
- The popup never covers the status bar by layout and passes outside touches
  through; exit is available from the popup, the notification and the app.
- The overlay type used is not shown over the keyguard.
- Known limitation by design: with no phone-state permission (least privilege),
  the popup does not auto-dismiss when a call arrives; it can visually cover an
  app's UI, including a dialer, until answered or ended. Status bar and system
  UI remain reachable. This behavior needs device verification before any
  claim.

## Not verified

- Everything above at runtime on Android. Calls/emergency usability,
  status-bar reachability with the popup up, and outside-touch pass-through are
  designed for, not grounded by a device test.
- The picker -> OCR -> review end-to-end flow.
- Physical-device behavior on any Android version.

## Native testing

`.github/workflows/native-test.yml` runs the build plus a scripted emulator
evidence pass on a GitHub-hosted runner (API 28 emulator): install, overlay
grant, popup-over-launcher capture, wrong-answer and timer behavior,
notification shade interaction, a simulated incoming call with the popup up,
session-end verification, and the picker -> OCR -> review flow with a small
synthetic PDF (`ci/test-resume.pdf`, no personal data). Screenshots and logs
are uploaded as the `native-test-evidence` artifact. No APK artifact is
published.

## Distribution

Source only. No APK is distributed from this repository.

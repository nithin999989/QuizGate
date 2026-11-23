# QuizGate 0.3.0-dev development checkpoint

This is unfinished development source, not an installable product handoff. No APK is included.

## Not ready for release

The voluntary phone-wide quiz session (overlay popup at the chosen interval, only after the user starts it) is implemented as of this checkpoint but has NOT passed an Android runtime test. Safety behavior (calls reachable, status bar uncovered, outside touches passing through, reliable exit) is designed, not device-verified.

PDF/text selection, local PDF OCR, review and matched-practice code compile, but file selection through OCR and review has not passed an end-to-end Android runtime test. The latest spacing compatibility fix and Downloads picker starting folder also need runtime verification. Do not describe this checkpoint as finished or ready to install.

## Implemented components

- The earlier concept's home, quiz and resume-review layout, with Newsreader and Inter as open-licence font substitutions.
- Thirty sourced draft questions across Spring Boot, Microservices and Redis.
- Immediate return after a correct answer; a configured ten-second explanation before returning after a wrong answer; explicit End/home controls.
- Saved 5, 10 and 15 minute choices, used by both the in-app flow and the phone-wide session.
- Phone-wide session: foreground service plus overlay popup reusing the packaged quiz UI; least-privilege permission set (overlay, foreground service, notifications on 13+). No accessibility or usage access.
- Launcher icon: bundled Newsreader serif Q with a sage check tail on the panel tint, as adaptive and legacy mipmaps.
- Android document picking for PDF/plain text, limited to 2 MB and five PDF pages.
- Bundled on-device ML Kit Latin OCR, editable skills/project review, existing-bank topic matching and local project discussion templates. This is not cloud AI generation or automatic interview grading.
- Temporary PDF cleanup and a generation guard against cancelled/cleared imports repopulating the profile. Reviewed details stay in process memory only.
- No internet, Accessibility, usage-access, phone-state or broad storage permission. The packaged debug manifest adds only the voluntary-session permissions: SYSTEM_ALERT_WINDOW, FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE and POST_NOTIFICATIONS, plus AndroidX's generated app-private dynamic-receiver signature permission.

## Build

Use Java 17, Android SDK 35 and Gradle 8.7. Set your own Android SDK path through ANDROID_HOME or an untracked local.properties file.

```sh
bash ci/restore-binaries.sh   # restores binary assets stored as *.b64
gradle --no-daemon :app:assembleDebug :app:testDebugUnitTest
```

Binary assets (Gradle wrapper jar, bundled fonts, launcher icon PNGs, the CI
test PDF) are stored base64-encoded with a `.b64` suffix next to their real
paths. Run `ci/restore-binaries.sh` once after cloning to decode them.

The app's minimum API level is 26. Compiling for or targeting API 35 does not prove runtime compatibility on those Android versions.

## Fonts and dependencies

Newsreader and Inter are bundled under SIL Open Font License 1.1, subset to Latin-1 and common punctuation to keep the repository text-encodable. Their copyright/licence files are in `app/src/main/assets/fonts/` and readable from Font licences in the app. The concept's original fonts are not bundled.

OCR uses `com.google.mlkit:text-recognition:16.0.1`, the bundled model option. See https://developers.google.com/ml-kit/vision/text-recognition/v2/android .

See TEST_STATUS.md before making any runtime or release claim.

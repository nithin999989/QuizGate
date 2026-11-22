# QuizGate

Study-quiz prototype and interview-content drafts.

> **WITHDRAWN PROTOTYPE. No APK release is provided. Do not install or activate the old prototype. Phone-wide popup safety is unresolved.**

This repository preserves source for review and development. A public source repository is not a safe app release, store approval or a claim of compatibility with a particular phone.

## Repository status

| Area | Included | Status |
| --- | --- | --- |
| `prototype-android/` | Original Kotlin Android prototype and 15-question starter bank | Withdrawn, not a recommended installation |
| `content-drafts/interview-bank.json` | 30 sourced MCQs: Spring Boot, Microservices and Redis, ten each | Draft content, not integrated into the prototype |
| `DESIGN.md` | UI and resume-personalization direction | Proposed, not implemented |

The prototype uses Android Accessibility package-change events, display-over-apps access and call-state access. These are sensitive capabilities. It does not request window-content retrieval, automate clicks, read screen text or declare internet access. These design choices are not a proof of call, emergency or device safety. Do not disable device security controls or work around restricted settings to run it.

No APK, installation walkthrough, signing key, build output, personal resume, personalized project prompts, contact details or provider API key is included. There is no release workflow or automatic APK publication.

## Existing prototype behavior

The source implements voluntary sessions, Java/Python/Data science topic selection, configurable intervals, a correct-answer release, a wrong-answer explanation followed by a ten-second release, and End session. Session state is not restored after process death. The new interview bank and proposed design are separate from this code.

Historical runtime checks were limited to Android 9 emulation. They do not establish Android 11/12 compatibility, physical-device behavior, emergency-call safety, VoIP behavior or production readiness. Minimum API 26 and target/compile API 35 are declarations, not compatibility test results.

## Code map

- `MainActivity.kt`: the original setup UI and session controls.
- `SessionManager.kt`: in-memory session state, timing and score.
- `QuizBank.kt`: starter-bank loading.
- `GateAccessibilityService.kt`: foreground-package observations, overlay lifecycle and call-state checks.
- `app/src/main/assets/questions.json`: the original starter bank.

## Source tooling

The archived project declares Android Gradle Plugin 8.5.2, Kotlin 2.0.20, Java 17 and Android SDK 35. A Gradle wrapper and machine-local SDK configuration are not included. This source handoff is not a newly built or newly tested release.

## Interview content

Each draft MCQ includes four options, a zero-based answer index, explanation, interview follow-up and source IDs. The source map links to primary Spring, AWS, Resilience4j and Redis documentation. Configuration/version assumptions are explicit. These questions are original paraphrased learning material, not copied certification-exam questions or a guarantee of interview coverage.

## Next work, not completed features

Safety feasibility and a supported phone-wide interaction must be resolved independently from the UI. Improved visual design, generic resume parsing, skill review and personalized question selection are not integrated. No cloud AI provider is selected and no resume upload service is provided.

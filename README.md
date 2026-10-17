# Workopolo Face

Testbed for on-device face enrollment and face verification. Everything face-related lives in
`com.banglatrac.workopolo.face.core` so it can be copied into Workopolo; `MainActivity` and the
`harness` package are the test app only.

## Copying the feature into Workopolo

| What | Where |
|---|---|
| Code | `app/src/main/java/com/banglatrac/workopolo/face/core/` (whole folder) |
| Strings | `res/values/face_strings.xml`, `res/values-bn/face_strings.xml` |
| Resources | `res/values/face_colors.xml`, `res/drawable-nodpi/ic_workopolo_ai.png`, style `Theme.WorkopoloFace.FaceCheck` in `res/values/themes.xml` |
| Model | `assets/MobileFaceNet.tflite` and `assets/MobileFaceNet.NOTICE.txt`; keep `noCompress += 'tflite'` |
| Manifest | `CAMERA` permission, the two non-required camera `uses-feature`s, the ML Kit `DEPENDENCIES` meta-data and the `FaceCheckActivity` entry |
| Backup | exclude `datastore/face_templates.preferences_pb` (see `res/xml/*_rules.xml`); the Keystore key can't be restored |
| Dependencies | CameraX 1.4.2 (core, camera2, lifecycle, view), `com.google.mlkit:face-detection:16.1.7`, `com.google.ai.edge.litert:litert:1.4.1` |

Then load `faceModule` in Koin, and register a `FaceReenrollmentGateway` that sends the re-enrollment
request to your backend.

## Public API

- `FaceVerifier`: `isEnrolled()`, `verify()` (opens the camera flow and always returns a `FaceMatchResult`), `clearEnrollment()`.
- `NavGraphBuilder.faceEnrollmentGraph(...)` / `FaceEnrollmentFlow(...)`: enrollment intro → capture → done.
- `NavGraphBuilder.faceReenrollmentGraph(...)`: repeated mismatch → request re-enrollment → requested.
- `FaceDiagnostics`: numbers for a debug panel (optional).

The match threshold is `FaceConfig.matchThreshold`.

## Building

`./gradlew :app:assembleDebug` · unit tests: `./gradlew :app:testDebugUnitTest`

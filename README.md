# Face Verify

On-device face enrollment and identification for Android. Several people can be enrolled on one
phone; a face check tells you which of them is in front of the camera, if anyone. Nothing leaves
the device.

Everything face-related lives in `com.sumonlogs.faceverify.core` so it can be reused in another
app; `MainActivity` and the `harness` package are the test app only.

## Reusing the feature in another app

| What | Where |
|---|---|
| Code | `app/src/main/java/com/sumonlogs/faceverify/core/` (whole folder) |
| Strings | `res/values/face_strings.xml`, `res/values-bn/face_strings.xml` |
| Resources | `res/values/face_colors.xml`, style `Theme.FaceVerify.FaceCheck` in `res/values/themes.xml` |
| Model | `assets/MobileFaceNet.tflite` and `assets/MobileFaceNet.NOTICE.txt`; keep `noCompress += 'tflite'` |
| Manifest | `CAMERA` permission, the two non-required camera `uses-feature`s, the ML Kit `DEPENDENCIES` meta-data and the `FaceCheckActivity` entry |
| Backup | exclude `datastore/face_templates.preferences_pb` (see `res/xml/*_rules.xml`); the Keystore key can't be restored |
| Dependencies | CameraX 1.4.2 (core, camera2, lifecycle, view), `com.google.mlkit:face-detection:16.1.7`, `com.google.ai.edge.litert:litert:1.4.1` |

Then load `faceModule` in Koin.

## Public API

- `FaceVerifier.enrolledFaces`: everyone enrolled (id, name, enrolment time), as a Flow.
- `FaceVerifier.verify()`: opens the camera flow and always returns a `FaceMatchResult` with the recognised person, if any.
- `FaceVerifier.deleteEnrollment(id)`: removes one person.
- `NavGraphBuilder.faceEnrollmentGraph(...)` / `FaceEnrollmentFlow(...)`: name → capture → done; enrolls one new person per run.
- `FaceDiagnostics`: numbers for a debug panel (optional).

Tuning lives in `FaceConfig`: `matchThreshold` decides a match, `identifyMargin` is how far the best person
must be ahead of the runner-up before the result counts (otherwise `AmbiguousMatch`).

## Building

`./gradlew :app:assembleDebug` · unit tests: `./gradlew :app:testDebugUnitTest` · lint: `./gradlew :app:lintDebug`

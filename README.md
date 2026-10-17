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
| Models | `assets/SFace.tflite` + `SFace.NOTICE.txt` (default, Apache-2.0) and/or `assets/MobileFaceNet.tflite` + its NOTICE; keep `noCompress += 'tflite'` |
| Manifest | `CAMERA` permission, the two non-required camera `uses-feature`s, the ML Kit `DEPENDENCIES` meta-data and the `FaceCheckActivity` entry |
| Backup | exclude `datastore/face_templates.preferences_pb` (see `res/xml/*_rules.xml`); the Keystore key can't be restored |
| Dependencies | CameraX 1.4.2 (core, camera2, lifecycle, view), `com.google.mlkit:face-detection:16.1.7`, `com.google.ai.edge.litert:litert:1.4.1` |

Then load `faceModule` in Koin.

## Public API

- `FaceVerifier.enrolledFaces`: everyone enrolled (id, name, enrolment time), as a Flow.
- `FaceVerifier.verify()`: opens the camera flow and always returns a `FaceMatchResult` with the recognised person, if any.
- `FaceVerifier.deleteEnrollment(id)`: removes one person.
- `NavGraphBuilder.faceEnrollmentGraph(...)` / `FaceEnrollmentFlow(...)`: name → capture → done; enrolls one new person per run.
- `FaceModelSettings`: which model is active and its threshold (for testing and calibration).
- `FaceDiagnostics`: numbers for a debug panel (optional).

## How matching works

1. Enrollment captures 5 front-facing frames and stores their embeddings; left, right and up are a
   liveness check only. The same tracked face must stay in view throughout.
2. A face check, after the liveness challenge, embeds 3 front-facing frames and averages them.
3. Every crop is aligned on 5 landmarks (eyes, nose, mouth corners) to the ArcFace template, and
   rejected if too dark, too bright or blurry.
4. The probe is compared with the average of each person's templates. The best person must reach the
   model's threshold and beat the runner-up by `FaceConfig.identifyMargin`, otherwise `AmbiguousMatch`.

Embeddings from different models can't be compared, so each enrollment records its model and only
the active model's people are matched.

## Calibrating the threshold

Starting thresholds are guesses (SFace 0.42, MobileFaceNet 0.65). In the test app, label every check
with who was really in front of the camera; the calibration card then shows how many strangers got in
and how many right people were refused at the current threshold, and suggests one. Test with several
people, different light, glasses on and off, and people who look alike.

## Building

`./gradlew :app:assembleDebug` · unit tests: `./gradlew :app:testDebugUnitTest` · lint: `./gradlew :app:lintDebug`

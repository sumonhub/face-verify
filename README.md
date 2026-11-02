# Face Verify

**On-device face enrollment and identification for Android, with liveness and anti-spoofing.**
Several people can be enrolled on one phone. A face check tells you who is in front of the camera,
if anyone, and refuses photos and screen replays. All processing runs on the device.

[![Platform](https://img.shields.io/badge/platform-Android%208.1%2B-3DDC84?logo=android&logoColor=white)](#getting-started)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)](#tech-stack)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](#tech-stack)
[![On-device ML](https://img.shields.io/badge/ML-on--device%20(LiteRT%20%2B%20ML%20Kit)-FF6F00)](#models)

Built by **SumOn** · [sumonlogs.com](https://sumonlogs.com)

<!--
Screenshots: add images under docs/screenshots/ and show them here, for example:
<p align="center"><img src="docs/screenshots/verify.png" width="240"> <img src="docs/screenshots/enroll.png" width="240"></p>
-->

## What it does

- **Multi-person identification (1:N).** Enroll several people by name; a face check returns who was
  recognised. If two people score too close to call, it says so instead of guessing.
- **Active liveness.** Each check asks for a random challenge: blink, turn left or turn right.
- **Passive anti-spoofing.** Two MiniFASNet models look at the face and its surroundings to reject
  printed photos and screen replays, even when the challenge was performed.
- **Quality gating.** Frames are rejected for a small, off-centre or tilted face, closed eyes, and
  dark, bright or blurry crops, and the user is told why. Hints appear only when a problem lasts
  (no flashing on a single dark frame), and the screen stays on at full brightness while capturing.
- **Private by design.** Only embeddings and names are stored, encrypted with a non-exportable
  Android Keystore key. No face images are kept.
- **Two languages.** English and Bengali.
- **A test app.** Switch models, tune the threshold, label checks to calibrate, and watch live timings.
- **Tester data collection (opt-in).** The test app can send anonymous numbers from each check to Firebase,
  with scripts that turn them into thresholds. See [Collecting tester data](#collecting-tester-data).

## How a face check works

```mermaid
flowchart LR
    A[Camera frame] --> B[ML Kit: face,<br/>landmarks, angles]
    B --> C{Quality gates}
    C --> D[Liveness:<br/>blink / turn L / turn R]
    D --> E[Capture 3<br/>front frames]
    E --> F[Anti-spoof<br/>2 models]
    F --> G[Align to 112×112<br/>+ SFace embedding]
    G --> H[Cosine vs each<br/>person's templates]
    H --> I{Threshold<br/>+ margin}
```

1. **Enrollment** captures 8 front-facing frames, at least 350 ms apart so they cover natural movement,
   and stores their embeddings. A frame that disagrees with the group (a blink, a blur) is dropped, and
   enrollment fails only if fewer than 5 agree. Turning left and right is a liveness check only, and
   those frames are not stored. The same tracked face must stay in view throughout, and a face that is
   already enrolled is refused.
2. **Alignment.** ML Kit runs in its accurate mode. Each crop is aligned with a least-squares fit on 4
   landmarks (eyes and mouth corners) to the ArcFace 112×112 template. The nose is left out: ML Kit
   reports the nose base, the template holds the nose tip. Crops with flat or one-sided lighting are
   asked to be retaken, besides the dark, bright and blurry checks.
   Every template carries a pipeline version; when the pipeline changes, people enrolled under the old
   one are listed as "enroll again" instead of silently scoring lower.
3. **Verification** averages the embeddings of 3 front frames after the liveness challenge and
   compares them with the average of each person's templates.
4. **Decision.** The best person must reach the model's threshold *and* beat the runner-up by
   `FaceConfig.identifyMargin`; otherwise the result is `AmbiguousMatch`.
5. **Anti-spoofing runs first.** A face scored as fake is never embedded.

The slow part (anti-spoofing, embedding, matching) runs on a worker thread, not the camera thread,
after models are warmed up in the background.

## Models

| Model | Role | Licence |
|---|---|---|
| [SFace](https://github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface) (OpenCV Zoo), default | Face embedding (128-d) | Apache-2.0 |
| MobileFaceNet (converted from [sirius-ai](https://github.com/sirius-ai/MobileFaceNet_TF)), optional | Face embedding (192-d) | Code Apache-2.0; **weights are research-only, get a legal review before commercial use** |
| [MiniFASNet](https://github.com/minivision-ai/Silent-Face-Anti-Spoofing) 2.7× and 4.0× | Anti-spoofing | Apache-2.0 |
| ML Kit Face Detection (bundled) | Detection, landmarks, head angles, eye-open probability | Google ML Kit terms |

Embeddings from different models can't be compared, so each enrollment records its model and only
the active model's people are matched. Sources, hashes and licence texts are in the NOTICE files in
`app/src/main/assets/`.

## Tech stack

| Area | Choice |
|---|---|
| Language / UI | Kotlin 2.2, Jetpack Compose, Material 3 |
| Camera | CameraX 1.4 |
| ML runtime | LiteRT 1.4 (TensorFlow Lite) on CPU, ML Kit face detection 16.1 |
| Architecture | MVI view models, Koin dependency injection, a pure Kotlin engine with no Android types |
| Storage | DataStore + AES-256-GCM with an Android Keystore key |
| Tester data (test app only) | Firebase Firestore and Crashlytics, opt-in and anonymous |
| Build | AGP 9.4, R8 in release, baseline profile, `minSdk 27` |

## Architecture

```
app/src/main/java/com/sumonlogs/faceverify/
├── core/
│   ├── api/      public API: FaceVerifier, enrollment graph, settings, diagnostics
│   ├── engine/   pure logic: alignment, matching, quality, liveness, sessions (unit-tested)
│   │   └── android/   CameraX analyzer, ML Kit, LiteRT embedder and spoof detector, Keystore store
│   ├── ui/       Compose screens: enrollment, face check, camera preview, components, theme
│   ├── di/       Koin module (load faceModule to use the feature)
│   └── mvi/      small MVI base
├── harness/      test app: model switch, threshold, calibration, performance, debug panel
│   └── telemetry/ opt-in tester data: consent, check records, Firestore uploader
└── MainActivity.kt

firestore.rules       database rules for the tester data
docs/                 Firebase setup and tester guide
tools/                export and analysis scripts for the collected data
```

The engine takes plain numbers and arrays, so sessions, matching and quality checks are covered by
JVM unit tests without a device. Camera frames are wrapped behind a small `FaceFrame` interface.

## Security and privacy

- Names and embeddings are encrypted at rest (AES-256-GCM, non-exportable Keystore key). No face
  images are stored. The selfie of a check is only returned to the caller and is never written to disk.
- The face feature (`core/`) contains no networking code and sends no face data anywhere. Only the test
  app can send data, and only after the tester taps Agree: anonymous numbers (scores, timings, light, phone
  model) and crash reports, never photos, names or embeddings. It is off by default and hidden entirely in a
  build without a Firebase config. Some Google libraries also declare the `INTERNET` permission in the merged
  manifest.
- Backup is disabled for the template store, because the Keystore key can't be restored.

## Performance and size

- Release builds use R8 and ship a baseline profile. Models are loaded and warmed up in the background
  when the enrollment or camera screen opens.
- A release APK with `arm64-v8a` and `armeabi-v7a` is about **65 MB** (models are about 41 MB of that), including
  the Firebase libraries of the test app.
- The test app has a **Performance** card with per-stage average and p95 timings (detection, frame
  conversion, alignment, each anti-spoof model, embedding) and a thread benchmark. Use a release build
  to read it. Per-device numbers are not published yet.

## Getting started

Requires Android Studio with a recent Android SDK, JDK 17 and a phone or emulator with a camera
(Android 8.1 or newer).

```bash
./gradlew :app:assembleDebug        # debug build
./gradlew :app:testDebugUnitTest    # unit tests
./gradlew :app:lintDebug            # lint
./gradlew :app:assembleRelease      # R8 + baseline profile (unsigned; sign it to install)
```

The launcher activity is the test app: enroll a person, run a check, and read the debug panel.

## Collecting tester data

To improve the thresholds with real people and phones, share the APK with a tester group and let the test app
report back:

1. Each tester agrees to share anonymous data, then sets **Test setup** (who is in front of the camera, light,
   glasses) so every check is saved with the right answer.
2. Each check uploads one record to Firestore: scores, anti-spoof score, timings, light numbers, phone model.
   Offline records wait and send later. Crashes go to Crashlytics.
3. `tools/export_checks.py` downloads the records and `tools/analyze_checks.py` prints false accepts and
   rejects at every threshold, the anti-spoof cut-off, brightness by lighting, and speed per phone.
4. Optionally, testers can also switch on **Face images** (off by default, its own confirmation): the aligned
   112×112 crop of checks where they were the enrolled person goes to a `crops` collection, and
   `tools/export_crops.py` turns it into the folder `face_eval.py` reads.
5. `tools/face_eval.py` is the offline benchmark: give it a folder of aligned 112×112 crops
   (`employee/session/image`) and it runs every model on the same crops, then reports ROC, EER, TAR at a
   chosen FAR, score distributions and open-set identification, with the threshold picked on one half of the
   employees and judged on the other half. It also compares centroid, max and mean template scoring.
   `tools/align_photos.py` aligns ordinary photos first (MediaPipe landmarks, offline only). See
   `docs/face-evaluation.md`.

Setup steps, signing and a tester checklist are in [`docs/firebase-setup.md`](docs/firebase-setup.md). The
database rules are in [`firestore.rules`](firestore.rules); a unit test keeps them in step with what the app sends.

## Calibrating the threshold

Starting thresholds are guesses (both models start at a strict 0.75; SFace's own reference is 0.363), and the anti-spoof cut-off
(`spoofMinScore`, 0.5) is the model's own decision point. For one person on one phone, label every check in
the test app with who was really in front of the camera; the calibration card shows how many strangers got in and how many
right people were refused at the current threshold, and suggests one. Test with several people,
different light, glasses on and off, and people who look alike.

## Reusing the feature in another app

| What | Where |
|---|---|
| Code | `app/src/main/java/com/sumonlogs/faceverify/core/` (whole folder) |
| Strings | `res/values/face_strings.xml`, `res/values-bn/face_strings.xml` |
| Resources | `res/values/face_colors.xml`, style `Theme.FaceVerify.FaceCheck` in `res/values/themes.xml` |
| Models | `assets/SFace.tflite`, `assets/spoof_model_scale_2_7.tflite`, `assets/spoof_model_scale_4_0.tflite` and their NOTICE files; optionally `assets/MobileFaceNet.tflite`. Keep `noCompress += 'tflite'` |
| Manifest | `CAMERA` permission, the two non-required camera `uses-feature`s, the ML Kit `DEPENDENCIES` meta-data and the `FaceCheckActivity` entry |
| Backup | exclude `datastore/face_templates.preferences_pb` (see `res/xml/*_rules.xml`) |
| Dependencies | CameraX 1.4.2 (core, camera2, lifecycle, view), `com.google.mlkit:face-detection:16.1.7`, `com.google.ai.edge.litert:litert:1.4.1` |
| Release | the R8 rules in `src/main/keepRules/rules.keep` (LiteRT must be kept) |

The `harness/` package and the Firebase dependencies belong to the test app; leave them out when reusing the feature.

Then load `faceModule` in Koin.

### Public API

- `FaceVerifier.enrolledFaces`: everyone enrolled (id, name, enrolment time), as a Flow.
- `FaceVerifier.verify()`: opens the camera flow and always returns a `FaceMatchResult` with the recognised person, if any.
- `FaceVerifier.deleteEnrollment(id)`: removes one person.
- `NavGraphBuilder.faceEnrollmentGraph(...)` / `FaceEnrollmentFlow(...)`: name → capture → done; enrolls one new person per run.
- `FaceModelSettings`: which model is active and its threshold (for testing and calibration).
- `FaceDiagnostics`: numbers and timings for a debug panel (optional).

## Status and roadmap

Working today: enrollment, 1:N identification, active liveness, two-model anti-spoofing, encrypted
storage, quality gating, a calibration test app, release optimisation, and an opt-in tester data pipeline.

Next, in order:
1. Run the tester group (Firebase pipeline above) and use `tools/analyze_checks.py` to pick evidence-based
   thresholds for each model and the anti-spoof cut-off.
2. Accuracy work, partly done (accurate mode, no nose, enrollment, lighting gates, template version): measure
   it with the tester data, then decide on a stronger landmark source.
3. UX: overlap capture with the challenge, direction arrows and a countdown on the check screen,
   retry advice after a failure, accessibility.
4. Experiments: a cheaper anti-spoof cascade and a quantized embedding model, kept only if accuracy
   holds.

## Credits and licence

This project builds on SFace and the OpenCV Zoo, MiniFASNet from minivision-ai/Silent-Face-Anti-Spoofing,
MobileFaceNet from sirius-ai, and Google ML Kit, CameraX and LiteRT. Each model's licence is listed in
[Models](#models).

The project has no licence file yet, so it is all rights reserved until one is added.

## About

Made by **SumOn**. More projects and write-ups at **[sumonlogs.com](https://sumonlogs.com)**.

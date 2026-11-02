# Collecting tester data with Firebase

The test app can send anonymous numbers from each face check, and crash reports, to your own Firebase
project. This guide takes you from nothing to a tester APK that reports back, and then to decisions.

**What is sent (only after the tester taps Agree):** match scores, the anti-spoof score, timings, light
and quality numbers, the lighting and scenario the tester chose, phone model, Android and app version, a
random install id, and crash reports. **What is never sent:** photos, names, embeddings or any face data.
The exact list is `CheckRecord.toFields()` and `firestore.rules`; a unit test keeps the two in step.

Everything below is free on Firebase's Spark plan for a tester group (Firestore allows 20,000 writes a
day), and needs no credit card.

## 1. Create the Firebase project

1. Open the [Firebase console](https://console.firebase.google.com) and choose **Add project**.
2. Name it, for example `face-verify-testers`. You can turn **Google Analytics off**: it isn't used.
3. In **Project overview**, add an **Android app** with the package name **`com.sumonlogs.faceverify`**.
4. Download **`google-services.json`** and put it in the **`app/`** folder (next to `app/build.gradle`).
   It is in `.gitignore`, so it won't be committed to your public repo.

Without that file the app still builds and runs, with all data-sharing screens hidden.

## 2. Create the database and lock it down

1. **Build > Firestore Database > Create database.** Pick a region near your testers and **Production mode**.
2. Open the **Rules** tab, replace everything with the contents of [`firestore.rules`](../firestore.rules)
   and press **Publish**.

The rules let a phone **add** a check record that has exactly the expected fields, and nothing else: no
reading, editing or deleting from a phone.

## 3. Crash reports (optional)

**Build > Crashlytics > Enable**. Crashes appear there a few minutes after they happen. Collection stays off
until the tester agrees.

## 4. Build and sign the APK

```bash
./gradlew :app:assembleRelease
```

Release APKs must be signed before they install. Once:

```bash
keytool -genkeypair -v -keystore faceverify-release.jks -alias faceverify -keyalg RSA -keysize 2048 -validity 10000
```

Keep the `.jks` file and its password somewhere safe and **out of git**: with a different key, testers
would have to uninstall before updating. Then sign (the `apksigner` tool is in the Android SDK
`build-tools` folder):

```bash
apksigner sign --ks faceverify-release.jks --out face-verify-tester.apk app/build/outputs/apk/release/app-release-unsigned.apk
```

Send `face-verify-tester.apk` to your testers. Android will ask them to allow installing from that source.

## 5. Check that it works yourself first

1. Install the APK on your phone and open the app. A consent dialog appears. Tap **Agree**.
2. Enroll yourself, then run **Verify** once.
3. In the Firebase console open **Firestore Database > Data**: a `checks` collection with one document
   should appear within seconds. The app's **Share test data** card shows "Sent 1".

If "failed" counts go up instead, the rules aren't published or the field list differs from the app's.

## 6. What to ask testers to do

Each tester enrolls once, then runs about 10 checks in each situation, setting **Test setup** first so the
data knows the right answer:

| Test setup | What to do | Why |
|---|---|---|
| Me, light: Normal / Bright / Dim | Verify as yourself | Real-user scores and "too dark" behaviour |
| Me, light: Window behind me / Outdoors | Verify as yourself | Hard lighting |
| Me, with Glasses, or Cap or mask | Verify as yourself | Real-world false rejects |
| Someone else | A different tester tries to verify as the enrolled person | **Strangers let in** (the security number) |
| Someone else (look-alike) | A relative or look-alike tries | The hardest strangers |
| Photo of someone | Hold a printed or on-screen photo of the enrolled person | Spoof protection |
| Video on a screen | Play a video of the enrolled person on another phone | Spoof protection |

Ask them to use different phones and to turn **Sharing: on**. Several people and phones matter more than
many checks from one person.

## 7. Read the results

1. In **Project settings > Service accounts**, press **Generate new private key** and save the JSON file
   (never commit it; `.gitignore` already skips `*service-account*.json`).
2. Export and analyse:

```bash
pip install google-cloud-firestore
python tools/export_checks.py --key service-account.json --out checks.csv
python tools/analyze_checks.py checks.csv --model sface-2021dec
```

The report shows, per model: strangers let in and right people refused at every threshold, the threshold for
1% and 0.1% false accepts, spoof acceptance and the best spoof cut-off, brightness by lighting (to set
`minBrightness`), the outcome mix, and speed per phone. Change the values in `FaceConfig.kt` and the model's
default threshold in `FaceModel.kt`, then ship a new build and repeat.

## 8. Optional: face crops for the offline model comparison

Numbers from checks say how the app behaves; they cannot compare models or template strategies. For that
`tools/face_eval.py` needs the aligned face pictures. The app can send them, but only when a tester turns on
**Face images** in the Share test data card (off by default, its own confirmation dialog, separate from
the numbers consent):

- Only the aligned 112×112 crop of a check where the tester was the enrolled person ("Me"), the check got as far
  as a comparison, and it was not a spoof. Never someone else, never the camera view, never a name.
- Stored in the `crops` collection (rules are already in `firestore.rules`; re-publish them) as lossless PNG
  with a random person key, the session (day + light) and the phone model.
- Ask each tester to enrol first, then test on **different days and lighting**: the earliest session is the
  enrollment session and the later ones are probed. Aim for 20+ people on several phones.

```bash
python tools/export_crops.py --key service-account.json --out crops/
python tools/face_eval.py embed --crops crops --model sface --out sface.npz
python tools/face_eval.py embed --crops crops --model mobilefacenet --out mfn.npz
python tools/face_eval.py report sface.npz mfn.npz --out report.md
```

Treat the `crops/` folder as biometric data: keep it private and delete it, and the rows, when the evaluation
is over (`export_crops.py --delete-install <id> --yes` removes one tester's rows on request).

## Privacy and housekeeping

- Tell testers what is collected before they start; the in-app consent dialogs say the same.
- Records have a random install id, not a name. To delete one tester's data, delete the documents with
  their install id (shown in the app as "Tester ID", first 8 characters).
- The `google-services.json` inside the APK can be extracted, so anyone could try to add records. The rules
  only accept correctly shaped ones and nobody can read or delete them, but delete the project (or add
  Firebase App Check) when testing is over.
- This is a practical guide, not legal advice. Biometric-related data can have local rules: check them
  before collecting from people outside your own group.

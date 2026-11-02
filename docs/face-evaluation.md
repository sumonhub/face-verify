# Offline face evaluation

`tools/face_eval.py` answers "how well does each model work on our own people?" with one protocol for
every model. Public benchmark numbers do not substitute for this.

## 1. Collect crops

A folder of **aligned 112×112 crops**, the same pixels the app's embedder sees:

```
crops/
  <employee>/
    s1_office_day/   0.png 1.png ...   <- first session sorted = enrollment session
    s2_outdoor/      ...                <- every later session = probe session
```

- One session is one sitting: a day, a device, a lighting condition. Enrollment and test frames never
  share a session, so results are not inflated by near-duplicate frames.
- Use at least 4 employees with 2+ sessions (more is far better) and vary lighting, distance, angle,
  expression and phone. Include look-alikes and relatives if you have them.
- The app already shows the aligned crop in the test screen. A consented export of those crops from
  testers is the intended source. They are face images: store them like biometric data and delete them
  when the evaluation is done.

**Only have ordinary photos?** `tools/align_photos.py` aligns them to the 112×112 template with MediaPipe
Face Landmarker (offline evaluation only; `pip install mediapipe`, plus the 478-point
`face_landmarker.task`, Apache-2.0). Same folder layout in and out:

```
python tools/align_photos.py --photos raw --out crops --landmarker face_landmarker.task
```

Alignment matters a great deal: on the same public photos, centre-cropping gave an EER of 16–18% and
proper alignment gave about 1%.

## 2. Embed with each model

```
python tools/face_eval.py embed --crops crops --model sface         --out sface.npz
python tools/face_eval.py embed --crops crops --model mobilefacenet --out mfn.npz
python tools/face_eval.py embed --crops crops --model-file arcface.onnx --norm signed127 --name arcface --out arc.npz
```

Models do not share preprocessing, so a custom model needs an explicit `--norm`:
`raw` (0..255), `signed128` ((x−127.5)/128) or `signed127` ((x−127.5)/127.5). Check the model card.
Colour is RGB; layout (NHWC/NCHW) is read from the model. Embeddings from different models are never mixed.

Install: `pip install numpy pillow ai-edge-litert` (and `onnxruntime` for `.onnx`).

## 3. Report

```
python tools/face_eval.py report sface.npz mfn.npz arc.npz --out report.md --roc-dir roc/
```

All files must come from the same crops. The report has, per model: EER, TAR/FAR/FRR at target FARs
(threshold picked on the calibration half of the employees, measured on the other half), genuine and
impostor score percentiles, and open-set identification (top-1, right person accepted, wrong person
accepted, stranger accepted) at several best-vs-second-best margins. Template strategies compared:
`centroid` (what the app does), `max` and `mean`. `roc_*.csv` and `hist_*.csv` hold the curve and the
score histograms for plotting.

A FAR target needs roughly 10/FAR impostor pairs to mean anything; the report says when it does not.

## 4. Check the tool itself

`python tools/face_eval.py selftest` verifies the metric code on synthetic scores.

## What this does not measure

Latency, RAM and battery (needs devices), licence (needs legal review), and spoof resistance (needs
attack recordings). The end-to-end pipeline including ML Kit alignment is judged by the Firebase tester
data and `tools/analyze_checks.py`; this tool judges the embedding model and template strategy once the
crop is fixed.

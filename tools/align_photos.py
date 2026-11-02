#!/usr/bin/env python3
"""Align ordinary photos to the 112x112 ArcFace template, ready for face_eval.py.

    python tools/align_photos.py --photos RAW_DIR --out CROPS_DIR --landmarker face_landmarker.task

RAW_DIR uses the same layout as face_eval.py (RAW_DIR/<employee>/<session>/<image>). Landmarks come from
MediaPipe Face Landmarker, which is a stronger source than the app's ML Kit detector; use it to judge the
models on well-aligned crops. Photos with no face are skipped and counted. Offline evaluation only:
nothing here goes into the app.

Needs: pip install numpy pillow mediapipe
Model: https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task
       (Apache-2.0)
"""
import argparse
import sys
from pathlib import Path

import numpy as np
from PIL import Image

SIZE = 112
EXTENSIONS = {".png", ".jpg", ".jpeg", ".bmp", ".webp"}

# Same template as core/engine/FaceAlignment.kt: person's right eye (image left), left eye, nose tip,
# right mouth corner, left mouth corner.
TEMPLATE = np.array([[38.2946, 51.6963], [73.5318, 51.5014], [56.0252, 71.7366],
                     [41.5493, 92.3655], [70.7299, 92.2041]])
# MediaPipe landmark indices for those five: iris centres (478-point model), nose tip, mouth corners.
POINTS = (468, 473, 1, 61, 291)


def similarity(src, dst):
    """Least-squares rotation + uniform scale + translation mapping src onto dst: 2x3 matrix."""
    sm, dm = src.mean(axis=0), dst.mean(axis=0)
    p, q = src - sm, dst - dm
    den = (p ** 2).sum()
    a = (p * q).sum() / den
    b = (p[:, 0] * q[:, 1] - p[:, 1] * q[:, 0]).sum() / den
    tx = dm[0] - (a * sm[0] - b * sm[1])
    ty = dm[1] - (b * sm[0] + a * sm[1])
    return np.array([[a, -b, tx], [b, a, ty]])


def warp(image, forward):
    """Bilinear warp of `image` through the forward 2x3 matrix (source -> output)."""
    full = np.vstack([forward, [0, 0, 1]])
    inv = np.linalg.inv(full)[:2]
    return image.transform((SIZE, SIZE), Image.AFFINE, tuple(inv.flatten()), resample=Image.BILINEAR)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--photos", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--landmarker", required=True, type=Path)
    args = parser.parse_args()

    import mediapipe as mp
    from mediapipe.tasks import python as mp_python
    from mediapipe.tasks.python import vision

    options = vision.FaceLandmarkerOptions(
        base_options=mp_python.BaseOptions(model_asset_path=str(args.landmarker)), num_faces=1)
    done = skipped = 0
    with vision.FaceLandmarker.create_from_options(options) as landmarker:
        for path in sorted(args.photos.rglob("*")):
            if path.suffix.lower() not in EXTENSIONS:
                continue
            image = Image.open(path).convert("RGB")
            result = landmarker.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=np.asarray(image)))
            if not result.face_landmarks:
                skipped += 1
                continue
            lm = result.face_landmarks[0]
            if len(lm) <= max(POINTS):
                sys.exit("This landmarker has no iris points; use the 478-point face_landmarker.task")
            w, h = image.size
            src = np.array([[lm[i].x * w, lm[i].y * h] for i in POINTS])
            crop = warp(image, similarity(src, TEMPLATE))
            target = args.out / path.relative_to(args.photos).with_suffix(".png")
            target.parent.mkdir(parents=True, exist_ok=True)
            crop.save(target)
            done += 1
    print(f"aligned {done} photos, skipped {skipped} with no face -> {args.out}")


if __name__ == "__main__":
    main()

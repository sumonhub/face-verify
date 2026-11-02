#!/usr/bin/env python3
"""Download the testers' aligned face crops from Firestore into the folder layout tools/face_eval.py reads.

    pip install google-cloud-firestore
    python tools/export_crops.py --key service-account.json --out crops/
    python tools/face_eval.py embed --crops crops --model sface --out sface.npz

Writes crops/<person>/<session>/<takenAtMillis>.png. The earliest session of each person is the enrollment
session in the evaluation, so ask testers to enrol first and test on later days or lighting too.

These are face images of people who agreed to share them: keep the folder private and delete it, and the
database rows, when the evaluation is over. To delete a tester's rows on request (their Tester ID is shown
in the app, first 8 characters):

    python tools/export_crops.py --key service-account.json --delete-install 1a2b3c4d --yes
"""
import argparse
import base64
import sys
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--key", required=True, help="service account JSON file")
    parser.add_argument("--out", default="crops", type=Path)
    parser.add_argument("--collection", default="crops")
    parser.add_argument("--delete-install", metavar="ID_PREFIX", help="delete every crop whose installId starts with this")
    parser.add_argument("--yes", action="store_true", help="confirm a delete")
    args = parser.parse_args()

    try:
        from google.cloud import firestore
    except ImportError:
        print("Install the client first:  pip install google-cloud-firestore", file=sys.stderr)
        return 1

    client = firestore.Client.from_service_account_json(args.key)
    docs = list(client.collection(args.collection).stream())

    if args.delete_install:
        mine = [d for d in docs if str((d.to_dict() or {}).get("installId", "")).startswith(args.delete_install)]
        if not args.yes:
            print(f"{len(mine)} crops would be deleted. Add --yes to do it.")
            return 0
        for d in mine:
            d.reference.delete()
        print(f"Deleted {len(mine)} crops.")
        return 0

    written = 0
    for d in docs:
        row = d.to_dict() or {}
        try:
            png = base64.b64decode(row["png"])
            folder = args.out / row["person"] / row["session"]
        except (KeyError, ValueError):
            continue
        folder.mkdir(parents=True, exist_ok=True)
        (folder / f"{row.get('takenAtMillis', d.id)}.png").write_bytes(png)
        written += 1
    print(f"Wrote {written} crops to {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

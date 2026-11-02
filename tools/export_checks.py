#!/usr/bin/env python3
"""Download the testers' check records from Firestore into one CSV.

    pip install google-cloud-firestore
    python tools/export_checks.py --key service-account.json --out checks.csv

The key is a Firebase service account (Project settings > Service accounts > Generate new private key).
Keep it private and never commit it. A service account is not subject to firestore.rules, so this is the
only way to read the data. Then run tools/analyze_checks.py on the CSV.
"""
import argparse
import csv
import sys


def flatten(doc: dict) -> dict:
    """One flat row: nested maps become `parent.child` columns, timestamps become ISO text."""
    row = {}
    for key, value in doc.items():
        if isinstance(value, dict):
            for inner_key, inner_value in value.items():
                row[f"{key}.{inner_key}"] = inner_value
        elif hasattr(value, "isoformat"):
            row[key] = value.isoformat()
        else:
            row[key] = value
    return row


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--key", required=True, help="service account JSON file")
    parser.add_argument("--out", default="checks.csv", help="CSV file to write")
    parser.add_argument("--collection", default="checks")
    args = parser.parse_args()

    try:
        from google.cloud import firestore
    except ImportError:
        print("Install the client first:  pip install google-cloud-firestore", file=sys.stderr)
        return 1

    client = firestore.Client.from_service_account_json(args.key)
    rows = [flatten(doc.to_dict()) for doc in client.collection(args.collection).stream()]
    if not rows:
        print("No records yet.")
        return 0

    columns = sorted({key for row in rows for key in row})
    with open(args.out, "w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=columns)
        writer.writeheader()
        writer.writerows(rows)
    print(f"Wrote {len(rows)} records to {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Turn the testers' check records (CSV from export_checks.py) into decisions: thresholds, spoof cut-off,
brightness limits and timings. Standard library only.

    python tools/analyze_checks.py checks.csv [--model sface-2021dec]

How the labels work: each record says what the tester was doing (`scenario`). A "me" check is genuine,
"other" is an impostor, and "photo" / "replay" are spoof attacks. The `score` column is the similarity of
the best-matching enrolled person, so any threshold can be tried afterwards without new tests.
"""
import argparse
import csv
import statistics
import sys
from collections import defaultdict

THRESHOLDS = [round(0.20 + 0.02 * i, 2) for i in range(36)]  # 0.20 .. 0.90
# A check that ended before a comparison happened says nothing about the match threshold.
SCORED_FAILURES = {"", "AmbiguousMatch"}


def number(row, key):
    try:
        return float(row[key]) if row.get(key, "") != "" else None
    except ValueError:
        return None


def percentile(values, p):
    if not values:
        return None
    ordered = sorted(values)
    return ordered[max(0, min(len(ordered) - 1, round(p / 100 * len(ordered) + 0.5) - 1))]


def load(path, model):
    with open(path, newline="", encoding="utf-8") as handle:
        rows = list(csv.DictReader(handle))
    return [r for r in rows if not model or r.get("modelId") == model]


def is_scored(row):
    return row.get("livenessPassed", "").lower() == "true" and row.get("failure", "") in SCORED_FAILURES


def genuine_scores(rows):
    """Scores of the person the tester really was."""
    out = []
    for r in rows:
        if r.get("scenario") != "me" or not is_scored(r):
            continue
        expected, matched = r.get("expectedFaceId", ""), r.get("matchedFaceId", "")
        # The best person was someone else: a wrong-person error, not a genuine score.
        if expected and matched and expected != matched:
            continue
        score = number(r, "score")
        if score is not None:
            out.append(score)
    return out


def impostor_scores(rows):
    return [s for r in rows if r.get("scenario") == "other" and is_scored(r) and (s := number(r, "score")) is not None]


def blocked_share(rows, scenario):
    """Share of checks of this scenario that never got to a comparison (liveness or spoof stopped them)."""
    subset = [r for r in rows if r.get("scenario") == scenario]
    if not subset:
        return None
    return sum(1 for r in subset if not is_scored(r)) / len(subset)


def section(title):
    print(f"\n=== {title} ===")


def report_thresholds(rows):
    section("Match threshold")
    genuine, impostor = genuine_scores(rows), impostor_scores(rows)
    print(f"genuine checks scored: {len(genuine)}   impostor checks scored: {len(impostor)}")
    if len(genuine) < 20 or len(impostor) < 20:
        print("Too few samples for a trustworthy threshold (aim for 100+ of each, from several people).")
    if not genuine or not impostor:
        return
    print(f"genuine score   min {min(genuine):.2f}  p10 {percentile(genuine, 10):.2f}  median {statistics.median(genuine):.2f}")
    print(f"impostor score  max {max(impostor):.2f}  p90 {percentile(impostor, 90):.2f}  median {statistics.median(impostor):.2f}")
    print("\nthreshold   FAR (strangers let in)   FRR (right person refused)")
    best_eer, eer_threshold, pick1, pick01 = 2.0, None, None, None
    for t in THRESHOLDS:
        far = sum(1 for s in impostor if s >= t) / len(impostor)
        frr = sum(1 for s in genuine if s < t) / len(genuine)
        print(f"  {t:.2f}        {far:6.1%}                 {frr:6.1%}")
        if abs(far - frr) < best_eer:
            best_eer, eer_threshold = abs(far - frr), t
        if far <= 0.01 and pick1 is None:
            pick1 = (t, frr)
        if far <= 0.001 and pick01 is None:
            pick01 = (t, frr)
    print(f"\nequal-error threshold: about {eer_threshold:.2f}")
    for label, pick, need in (("1% FAR", pick1, 100), ("0.1% FAR", pick01, 1000)):
        if pick:
            note = "" if len(impostor) >= need else f"  (only {len(impostor)} impostor checks; {need}+ needed to trust it)"
            print(f"lowest threshold for {label}: {pick[0]:.2f}  (right person refused {pick[1]:.1%}){note}")
        else:
            print(f"no threshold in range reaches {label}")

    wrong = [r for r in rows if r.get("scenario") == "me" and is_scored(r)
             and r.get("expectedFaceId") and r.get("matchedFaceId") and r["expectedFaceId"] != r["matchedFaceId"]]
    if wrong:
        print(f"\nWARNING: {len(wrong)} 'me' checks had a different enrolled person as the best match.")


def report_spoof(rows):
    section("Anti-spoofing")
    attacks = [r for r in rows if r.get("scenario") in ("photo", "replay")]
    real = [r for r in rows if r.get("scenario") == "me"]
    if not attacks:
        print("No photo or video-replay checks yet.")
        return
    for scenario in ("photo", "replay"):
        subset = [r for r in attacks if r["scenario"] == scenario]
        if not subset:
            continue
        passed = sum(1 for r in subset if r.get("matched", "").lower() == "true")
        stopped = sum(1 for r in subset if r.get("failure") == "SpoofSuspected")
        print(f"{scenario:7} n={len(subset):3}  accepted as a person: {passed / len(subset):5.1%}   stopped by anti-spoof: {stopped / len(subset):5.1%}")
    attack_scores = [s for r in attacks if (s := number(r, "spoofScore")) is not None]
    real_scores = [s for r in real if (s := number(r, "spoofScore")) is not None]
    if attack_scores and real_scores:
        print("\nspoof cut-off   attacks passing   real faces refused")
        for cut in [round(0.1 + 0.05 * i, 2) for i in range(18)]:
            print(f"  {cut:.2f}          {sum(1 for s in attack_scores if s >= cut) / len(attack_scores):6.1%}            {sum(1 for s in real_scores if s < cut) / len(real_scores):6.1%}")
    else:
        print("Not enough spoofScore values to suggest a cut-off yet.")


def report_light(rows):
    section("Light and quality hints")
    by_light = defaultdict(list)
    for r in rows:
        by_light[r.get("lighting", "?")].append(r)
    print("lighting     checks  face brightness (p10 / median)   share with a 'too dark' hint")
    for light, subset in sorted(by_light.items()):
        brightness = [b for r in subset if (b := number(r, "faceBrightness")) is not None]
        dark = sum(1 for r in subset if r.get("qualityIssue") == "TooDark") / len(subset)
        b_text = f"{percentile(brightness, 10):5.0f} / {statistics.median(brightness):5.0f}" if brightness else "      -"
        print(f"  {light:10} {len(subset):5}   {b_text}                      {dark:6.1%}")
    print("\nIf normal-light checks sit near the 'too dark' limit (minBrightness in FaceConfig), lower it.")


def report_failures(rows):
    section("Outcomes")
    outcomes = defaultdict(int)
    for r in rows:
        outcomes[r.get("failure") or ("matched" if r.get("matched", "").lower() == "true" else "no match")] += 1
    for name, count in sorted(outcomes.items(), key=lambda kv: -kv[1]):
        print(f"  {name:20} {count:5}  {count / len(rows):6.1%}")
    for scenario in ("me", "other"):
        share = blocked_share(rows, scenario)
        if share is not None:
            print(f"  '{scenario}' checks stopped before a comparison (liveness, spoof, errors): {share:.1%}")


def report_timing(rows):
    section("Speed by phone")
    by_device = defaultdict(list)
    for r in rows:
        by_device[r.get("device", "?")].append(r)
    print("device                         checks   total p50 / p95 (ms)   embed p50 (ms)")
    for device, subset in sorted(by_device.items(), key=lambda kv: -len(kv[1])):
        total = [t for r in subset if (t := number(r, "totalMs")) is not None]
        embed = [e for r in subset if (e := number(r, "embedMs")) is not None]
        t_text = f"{percentile(total, 50):7.0f} / {percentile(total, 95):7.0f}" if total else "      -"
        e_text = f"{percentile(embed, 50):6.0f}" if embed else "     -"
        print(f"  {device[:28]:28} {len(subset):5}   {t_text}        {e_text}")
    stage_cols = sorted({c for r in rows for c in r if c.startswith("timingP95Ms.")})
    if stage_cols:
        print("\nper stage (median of each phone's p95, ms):")
        for col in stage_cols:
            values = [v for r in rows if (v := number(r, col)) is not None]
            print(f"  {col.split('.', 1)[1]:14} {statistics.median(values):7.1f}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("csv")
    parser.add_argument("--model", help="only this model id, e.g. sface-2021dec")
    args = parser.parse_args()

    rows = load(args.csv, args.model)
    if not rows:
        print("No records.")
        return 1
    testers = {r.get("installId") for r in rows}
    print(f"{len(rows)} checks from {len(testers)} tester install(s), {len({r.get('device') for r in rows})} phone model(s)")
    models = sorted({r.get("modelId", "?") for r in rows})
    if len(models) > 1 and not args.model:
        print(f"Several models in the data: {', '.join(models)}. Thresholds differ per model, so run again with --model.")
    report_thresholds(rows)
    report_spoof(rows)
    report_light(rows)
    report_failures(rows)
    report_timing(rows)
    return 0


if __name__ == "__main__":
    sys.exit(main())

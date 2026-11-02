#!/usr/bin/env python3
"""Offline face-recognition evaluation: same crops, same protocol, any model.

    python tools/face_eval.py embed  --crops DIR --model sface          --out sface.npz
    python tools/face_eval.py embed  --crops DIR --model mobilefacenet  --out mfn.npz
    python tools/face_eval.py embed  --crops DIR --model-file arc.onnx --norm signed127 --name arcface --out arc.npz
    python tools/face_eval.py report sface.npz mfn.npz arc.npz --out report.md --roc-dir roc/
    python tools/face_eval.py selftest

DIR holds already ALIGNED 112x112 face crops (what the app's embedder sees), laid out as
    DIR/<employee>/<session>/<image>.png|jpg
A session is one sitting (one day, one device, one lighting). Name sessions so the earliest sorts first
(s1_..., s2_...): the FIRST session is the enrollment session and every other session is a probe session,
so enrollment and test frames never share a sitting (session-disjoint). Thresholds are picked on one half
of the employees and judged on the other half (employee-disjoint).

Needs numpy and Pillow; `embed` also needs ai-edge-litert (.tflite) or onnxruntime (.onnx).
`report` needs only numpy, so embeddings can be made once and compared any number of times.
"""
import argparse
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "assets"
SIZE = 112
IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".bmp", ".webp"}

# Mirrors core/engine/FaceModel.kt. Other models need --model-file and --norm.
MODELS = {
    "sface": (ASSETS / "SFace.tflite", "raw"),
    "mobilefacenet": (ASSETS / "MobileFaceNet.tflite", "signed128"),
}
NORMS = {
    "raw": lambda x: x,
    "signed128": lambda x: (x - 127.5) / 128.0,  # MobileFaceNet
    "signed127": lambda x: (x - 127.5) / 127.5,  # typical ArcFace / AdaFace ONNX
}


# ----------------------------------------------------------------------------- embedding

def load_dataset(root):
    """[(employee, session, path)] sorted, from DIR/<employee>/<session>/<image>."""
    items = []
    for employee in sorted(p for p in Path(root).iterdir() if p.is_dir()):
        for session in sorted(p for p in employee.iterdir() if p.is_dir()):
            for image in sorted(session.iterdir()):
                if image.suffix.lower() in IMAGE_EXTENSIONS:
                    items.append((employee.name, session.name, image))
    if not items:
        sys.exit(f"No images found under {root} (expected DIR/<employee>/<session>/<image>)")
    return items


def read_crop(path):
    from PIL import Image
    image = Image.open(path).convert("RGB")
    if image.size != (SIZE, SIZE):
        sys.exit(f"{path} is {image.size[0]}x{image.size[1]}; this tool expects aligned {SIZE}x{SIZE} crops")
    return np.asarray(image, dtype=np.float32)


class Embedder:
    """One model file behind one call: list of HxWx3 RGB float arrays in, L2-normalised embeddings out."""

    def __init__(self, model_file, norm):
        self.norm = NORMS[norm]
        self.path = str(model_file)
        if self.path.endswith(".onnx"):
            import onnxruntime as ort
            self.session = ort.InferenceSession(self.path, providers=["CPUExecutionProvider"])
            shape = self.session.get_inputs()[0].shape
            self.batch = shape[0] if isinstance(shape[0], int) else 1
            self.channels_last = shape[-1] == 3
            self.name = self.session.get_inputs()[0].name
            self.run = self._run_onnx
        else:
            try:
                from ai_edge_litert.interpreter import Interpreter
            except ImportError:
                from tflite_runtime.interpreter import Interpreter
            self.interp = Interpreter(model_path=self.path, num_threads=2)
            self.interp.allocate_tensors()
            self.input = self.interp.get_input_details()[0]
            self.output = self.interp.get_output_details()[0]
            shape = self.input["shape"]
            self.batch = int(shape[0])
            self.channels_last = shape[-1] == 3
            self.run = self._run_tflite

    def _batch_array(self, chunk):
        x = np.stack([self.norm(c) for c in chunk]).astype(np.float32)
        x = np.concatenate([x, np.repeat(x[-1:], self.batch - len(chunk), axis=0)]) if len(chunk) < self.batch else x
        return x if self.channels_last else x.transpose(0, 3, 1, 2)

    def _run_tflite(self, chunk):
        self.interp.set_tensor(self.input["index"], self._batch_array(chunk))
        self.interp.invoke()
        return self.interp.get_tensor(self.output["index"])[: len(chunk)]

    def _run_onnx(self, chunk):
        return self.session.run(None, {self.name: self._batch_array(chunk)})[0][: len(chunk)]

    def embed(self, crops):
        out = []
        for i in range(0, len(crops), self.batch):
            out.append(self.run(crops[i:i + self.batch]))
        e = np.concatenate(out).astype(np.float64)
        return (e / np.maximum(np.linalg.norm(e, axis=1, keepdims=True), 1e-10)).astype(np.float32)


def cmd_embed(args):
    if args.model:
        model_file, norm = MODELS[args.model]
        name = args.name or args.model
    else:
        if not args.model_file or not args.norm:
            sys.exit("--model-file needs --norm (raw | signed128 | signed127): models do not share preprocessing")
        model_file, norm, name = Path(args.model_file), args.norm, args.name or Path(args.model_file).stem
    items = load_dataset(args.crops)
    embedder = Embedder(model_file, norm)
    crops = [read_crop(p) for _, _, p in items]
    emb = embedder.embed(crops)
    np.savez_compressed(
        args.out, emb=emb, model=name, norm=norm,
        employee=np.array([e for e, _, _ in items]), session=np.array([s for _, s, _ in items]),
        path=np.array([str(p.relative_to(args.crops)) for _, _, p in items]),
    )
    print(f"{name}: {len(items)} crops, {len(set(e for e, _, _ in items))} employees, dim {emb.shape[1]} -> {args.out}")


# ----------------------------------------------------------------------------- metrics

def far_frr(gen, imp):
    """Thresholds and the FAR / FRR at each (accept when score >= threshold)."""
    scores = np.unique(np.concatenate([gen, imp]))
    ts = np.concatenate([[scores[0] - 1e-6], scores, [scores[-1] + 1e-6]])
    g, i = np.sort(gen), np.sort(imp)
    frr = np.searchsorted(g, ts, side="left") / len(g)
    far = (len(i) - np.searchsorted(i, ts, side="left")) / len(i)
    return ts, far, frr


def eer(gen, imp):
    ts, far, frr = far_frr(gen, imp)
    k = int(np.argmin(np.abs(far - frr)))
    return float((far[k] + frr[k]) / 2), float(ts[k])


def threshold_at_far(imp, target):
    """Lowest threshold that lets through at most `target` of these impostor scores."""
    i = np.sort(imp)
    allowed = int(np.floor(target * len(i)))
    return float(np.nextafter(i[len(i) - allowed - 1], np.inf))


def rates(gen, imp, t):
    return float((imp >= t).mean()), float((gen < t).mean())  # FAR, FRR


def pct(values, ps=(1, 5, 25, 50, 75, 95, 99)):
    return {p: float(np.percentile(values, p)) for p in ps}


# ----------------------------------------------------------------------------- protocol

def unit(v):
    return v / max(np.linalg.norm(v), 1e-10)


def build_protocol(data, enroll_frames, group):
    """gallery: employee -> templates (k, D) from the first session. probes: list of (employee, session, vector)."""
    emb, emp, ses = data["emb"], data["employee"], data["session"]
    gallery, probes, skipped = {}, [], []
    for e in sorted(set(emp)):
        sessions = sorted(set(ses[emp == e]))
        if len(sessions) < 2:
            skipped.append(e)
            continue
        first = emb[(emp == e) & (ses == sessions[0])]
        pick = np.unique(np.linspace(0, len(first) - 1, min(enroll_frames, len(first))).round().astype(int))
        gallery[e] = first[pick]
        for s in sessions[1:]:
            frames = emb[(emp == e) & (ses == s)]
            for i in range(0, len(frames) - group + 1, group):
                probes.append((e, s, unit(frames[i:i + group].sum(axis=0))))
    return gallery, probes, skipped


def score_matrix(probe_vecs, gallery, employees, strategy):
    """(probes x employees) similarity of each probe to each person's template set."""
    p = np.stack(probe_vecs)
    out = np.zeros((len(p), len(employees)))
    for j, e in enumerate(employees):
        t = gallery[e]
        if strategy == "centroid":
            out[:, j] = p @ unit(t.sum(axis=0))
        elif strategy == "max":
            out[:, j] = (p @ t.T).max(axis=1)
        elif strategy == "mean":
            out[:, j] = (p @ t.T).mean(axis=1)
        else:
            raise ValueError(strategy)
    return out


def genuine_impostor(scores, probe_emp, employees):
    idx = {e: i for i, e in enumerate(employees)}
    own = np.array([idx[e] for e in probe_emp])
    mask = np.zeros_like(scores, dtype=bool)
    mask[np.arange(len(scores)), own] = True
    return scores[mask], scores[~mask], own


def split_employees(employees, seed):
    order = list(employees)
    np.random.default_rng(seed).shuffle(order)
    half = len(order) // 2
    return sorted(order[:half]), sorted(order[half:])


def open_set(scores, own, in_gallery, threshold, margin):
    """Probes of enrolled people and of unknown people against a gallery of the enrolled ones only."""
    cols = np.array(sorted(in_gallery))
    s = scores[:, cols]
    order = np.argsort(-s, axis=1)
    rows = np.arange(len(s))
    best_col = cols[order[:, 0]]
    best = s[rows, order[:, 0]]
    second = s[rows, order[:, 1]] if s.shape[1] > 1 else np.zeros(len(s))
    accepted = (best >= threshold) & (best - second >= margin)
    enrolled = np.isin(own, cols)
    right = best_col == own
    return {
        "top1": float(right[enrolled].mean()),
        "right_accept": float((accepted & right)[enrolled].mean()),
        "wrong_accept": float((accepted & ~right)[enrolled].mean()),
        "unknown_accept": float(accepted[~enrolled].mean()) if (~enrolled).any() else float("nan"),
        "n_enrolled_probes": int(enrolled.sum()),
        "n_unknown_probes": int((~enrolled).sum()),
    }


# ----------------------------------------------------------------------------- report

STRATEGIES = ("centroid", "max", "mean")


def evaluate(data, args, roc_dir):
    gallery, probes, skipped = build_protocol(data, args.enroll_frames, args.probe_group)
    employees = sorted(gallery)
    probe_emp = [p[0] for p in probes]
    notes = []
    if skipped:
        notes.append(f"Skipped {len(skipped)} employee(s) with fewer than 2 sessions: {', '.join(skipped)}.")
    if len(employees) < 4:
        return None, notes + [f"Only {len(employees)} usable employees; need at least 4 for a calibrate/test split."]
    calib, test = split_employees(employees, args.seed)
    result = {"employees": len(employees), "probes": len(probes), "calib": len(calib), "test": len(test), "strategies": {}}
    for strategy in STRATEGIES:
        scores = score_matrix([p[2] for p in probes], gallery, employees, strategy)
        gen, imp, own = genuine_impostor(scores, probe_emp, employees)
        row_emp = np.array(probe_emp)

        def pairs(group):
            members = np.array([employees.index(e) for e in group])
            rows = np.isin(own, members)
            sub = scores[rows][:, members]
            g, i, _ = genuine_impostor(sub, list(row_emp[rows]), list(group))
            return g, i

        cg, ci = pairs(calib)
        tg, ti = pairs(test)
        entry = {"n_gen": len(gen), "n_imp": len(imp), "all_eer": eer(gen, imp)[0],
                 "test_eer": eer(tg, ti)[0], "gen_pct": pct(gen), "imp_pct": pct(imp), "at_far": {}}
        for target in args.far:
            t = threshold_at_far(ci, target)
            far, frr = rates(tg, ti, t)
            entry["at_far"][target] = {"threshold": t, "test_far": far, "test_frr": frr, "test_tar": 1 - frr,
                                       "reliable": len(ti) >= 10 / target}
        # Open set: half of the test employees are enrolled, the rest are strangers.
        in_test = [employees.index(e) for e in test]
        enrolled = in_test[: max(2, (len(in_test) * 3) // 4)]
        rows = np.isin(own, in_test)
        sub = scores[rows][:, in_test]
        sub_own = np.array([in_test.index(o) for o in own[rows]])
        sub_enrolled = [in_test.index(c) for c in enrolled]
        t1 = threshold_at_far(ci, args.far[0])
        entry["open"] = {m: open_set(sub, sub_own, sub_enrolled, t1, m) for m in args.margins}
        entry["open_threshold"] = t1
        result["strategies"][strategy] = entry
        if roc_dir:
            ts, far, frr = far_frr(gen, imp)
            Path(roc_dir).mkdir(parents=True, exist_ok=True)
            tag = f"{data['model']}_{strategy}"
            np.savetxt(Path(roc_dir) / f"roc_{tag}.csv", np.column_stack([ts, far, 1 - frr]),
                       delimiter=",", header="threshold,far,tar", comments="", fmt="%.6f")
            hist_g, edges = np.histogram(gen, bins=40, range=(-0.2, 1.0))
            hist_i, _ = np.histogram(imp, bins=40, range=(-0.2, 1.0))
            np.savetxt(Path(roc_dir) / f"hist_{tag}.csv", np.column_stack([edges[:-1], hist_g, hist_i]),
                       delimiter=",", header="bin_start,genuine,impostor", comments="", fmt="%.4f")
    return result, notes


def fmt(x, digits=3):
    return "n/a" if x is None or (isinstance(x, float) and x != x) else f"{x:.{digits}f}"


def render(models, results, notes, args):
    out = ["# Face recognition evaluation", ""]
    out += ["Protocol: first session of each employee enrols (up to %d frames); every later session is probed "
            "(%d-frame centroid per probe). Threshold chosen on %s employees, judged on the other half. Seed %d."
            % (args.enroll_frames, args.probe_group, "one half of the", args.seed), ""]
    for n in notes:
        out.append(f"> {n}")
    out.append("")
    out += ["## Models at a glance (centroid scoring, as the app does)", "",
            "| Model | Dim | Employees | Probes | Genuine pairs | Impostor pairs | EER (all) | EER (test half) |",
            "|---|---|---|---|---|---|---|---|"]
    for name, dim in models:
        r = results.get(name)
        if r:
            s = r["strategies"]["centroid"]
            out.append(f"| {name} | {dim} | {r['employees']} | {r['probes']} | {s['n_gen']} | {s['n_imp']} | "
                       f"{fmt(s['all_eer'] * 100, 2)}% | {fmt(s['test_eer'] * 100, 2)}% |")
    for name, _ in models:
        r = results.get(name)
        if not r:
            continue
        out += ["", f"## {name}", ""]
        out += ["### Operating points (threshold from calibration half, FAR/FRR measured on the test half)", "",
                "| Scoring | Target FAR | Threshold | Test FAR | Test FRR | TAR | Reliable? |", "|---|---|---|---|---|---|---|"]
        for strategy, s in r["strategies"].items():
            for target, o in s["at_far"].items():
                out.append(f"| {strategy} | {target:g} | {fmt(o['threshold'])} | {fmt(o['test_far'] * 100, 2)}% | "
                           f"{fmt(o['test_frr'] * 100, 2)}% | {fmt(o['test_tar'] * 100, 2)}% | "
                           f"{'yes' if o['reliable'] else 'no: too few impostor pairs'} |")
        out += ["", "### Score distributions (all pairs, centroid scoring)", "", "| | p1 | p5 | p25 | p50 | p75 | p95 | p99 |",
                "|---|---|---|---|---|---|---|---|"]
        s = r["strategies"]["centroid"]
        for label, d in (("genuine", s["gen_pct"]), ("impostor", s["imp_pct"])):
            out.append(f"| {label} | " + " | ".join(fmt(d[p]) for p in (1, 5, 25, 50, 75, 95, 99)) + " |")
        out += ["", f"### Open-set identification (centroid; threshold {fmt(s['open_threshold'])} = calibrated at "
                f"FAR {args.far[0]:g}; strangers were never enrolled)", "",
                "| Margin | Top-1 | Right person accepted | Wrong person accepted | Stranger accepted |", "|---|---|---|---|---|"]
        for m, o in s["open"].items():
            out.append(f"| {m:g} | {fmt(o['top1'] * 100, 1)}% | {fmt(o['right_accept'] * 100, 1)}% | "
                       f"{fmt(o['wrong_accept'] * 100, 2)}% | {fmt(o['unknown_accept'] * 100, 2)}% |")
        out.append(f"\n({s['open'][args.margins[0]]['n_enrolled_probes']} probes of enrolled people, "
                   f"{s['open'][args.margins[0]]['n_unknown_probes']} of strangers.)")
    out += ["", "## Reading this", "",
            "- FAR = share of impostor comparisons accepted; FRR = share of genuine comparisons rejected; TAR = 1 - FRR.",
            "- A FAR target is only trustworthy with roughly 10 / FAR impostor pairs; the table says when it is not.",
            "- Scores from different models are not comparable numbers; compare error rates at equal FAR, not thresholds.",
            "- Quality of this report is the quality of the crops: collect different days, lighting, distances and devices.",
            "- Latency, RAM and licence are not measured here; they need a device and a legal review."]
    return "\n".join(out) + "\n"


def cmd_report(args):
    models, results, notes = [], {}, []
    first_paths = None
    for f in args.embeddings:
        data = dict(np.load(f, allow_pickle=False))
        data["model"] = str(data["model"])
        if first_paths is None:
            first_paths = data["path"]
        elif not np.array_equal(first_paths, data["path"]):
            sys.exit(f"{f} was made from different crops than the first file; models must share one dataset")
        models.append((data["model"], data["emb"].shape[1]))
        result, n = evaluate(data, args, args.roc_dir)
        notes += n
        if result:
            results[data["model"]] = result
    text = render(models, results, notes, args)
    if args.out:
        Path(args.out).write_text(text, encoding="utf-8")
        print(f"Report written to {args.out}")
    else:
        print(text)


# ----------------------------------------------------------------------------- self-test

def synthetic(employees=20, sessions=3, frames=8, dim=128, noise=0.9, seed=1):
    rng = np.random.default_rng(seed)
    emb, emp, ses, path = [], [], [], []
    for e in range(employees):
        identity = unit(rng.normal(size=dim))
        for s in range(sessions):
            offset = rng.normal(size=dim) * 0.25 / np.sqrt(dim) * np.sqrt(dim) * 0.1
            for f in range(frames):
                v = unit(identity * 2.5 + offset + rng.normal(size=dim) * noise / np.sqrt(dim) * 2)
                emb.append(v)
                emp.append(f"e{e:02d}")
                ses.append(f"s{s + 1}")
                path.append(f"e{e:02d}/s{s + 1}/{f}.png")
    return {"emb": np.array(emb, dtype=np.float32), "employee": np.array(emp), "session": np.array(ses),
            "path": np.array(path), "model": "synthetic"}


def cmd_selftest(args):
    gen, imp = np.array([0.8, 0.9, 0.95]), np.array([0.1, 0.2, 0.3])
    assert eer(gen, imp)[0] == 0.0, "separated scores must give EER 0"
    t = threshold_at_far(imp, 0.0)
    assert rates(gen, imp, t) == (0.0, 0.0), "zero-FAR threshold must reject all impostors and keep these genuines"
    rng = np.random.default_rng(0)
    same = rng.normal(size=5000)
    assert abs(eer(same, rng.normal(size=5000))[0] - 0.5) < 0.03, "identical distributions give EER 0.5"
    assert threshold_at_far(np.arange(100, dtype=float), 0.1) > 89, "10% FAR of 100 scores must let at most 10 through"
    data = synthetic()
    args.far, args.margins = [0.01, 0.001], [0.0, 0.05]
    result, _ = evaluate(data, args, None)
    s = result["strategies"]["centroid"]
    assert s["all_eer"] < 0.2 and s["gen_pct"][50] > s["imp_pct"][99], "synthetic identities must separate"
    print(render([("synthetic", 128)], {"synthetic": result}, [], args))
    print("selftest ok")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="cmd", required=True)

    e = sub.add_parser("embed", help="crops -> embeddings (.npz)")
    e.add_argument("--crops", required=True, type=Path)
    e.add_argument("--model", choices=sorted(MODELS))
    e.add_argument("--model-file", type=Path)
    e.add_argument("--norm", choices=sorted(NORMS))
    e.add_argument("--name")
    e.add_argument("--out", required=True)
    e.set_defaults(run=cmd_embed)

    def protocol(p):
        p.add_argument("--far", type=float, nargs="+", default=[0.01, 0.001], help="target FARs, first one drives open-set")
        p.add_argument("--margins", type=float, nargs="+", default=[0.0, 0.03, 0.05, 0.10])
        p.add_argument("--enroll-frames", type=int, default=5)
        p.add_argument("--probe-group", type=int, default=3, help="frames averaged per probe (the app uses 3)")
        p.add_argument("--seed", type=int, default=7)

    r = sub.add_parser("report", help="embeddings -> evaluation report")
    r.add_argument("embeddings", nargs="+")
    r.add_argument("--out")
    r.add_argument("--roc-dir")
    protocol(r)
    r.set_defaults(run=cmd_report)

    t = sub.add_parser("selftest", help="check the metrics on synthetic data")
    protocol(t)
    t.set_defaults(run=cmd_selftest)

    args = parser.parse_args()
    args.run(args)


if __name__ == "__main__":
    main()

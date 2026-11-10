#!/usr/bin/env python3
"""Summarises benchmark runs of tools/bench-lab*.txt: one table row per window (A1, A2, ..., B3, C3) per run, and
the median and spread of each number over the runs given.

  python tools/bench_summary.py LABEL=RUN_DIR[,RUN_DIR...] [LABEL=...] [--md OUT.md]

RUN_DIR is a smoke-out folder (or a report.txt). Each LABEL is one configuration; its runs are its repeats.
Numbers read from the "bench NAME -> ..." lines: frame average, p99 and worst (ms), fps and 1% low, server tick
average, p50, p95, p99 and worst (ms), packets received per second, client entities; and from "cmdout tick query",
the game's own average and percentiles.
"""
import argparse
import os
import re
import statistics
import sys

BENCH = re.compile(r"^bench (\S+) -> ([\d.]+) s; frames n (\d+) avg ([\d.]+) p50 ([\d.]+) p95 ([\d.]+) p99 ([\d.]+) max ([\d.]+) ms, "
                   r"fps ([\d.]+), 1% low ([\d.]+) fps; server ticks n (\d+) avg ([\d.]+) p50 ([\d.]+) p95 ([\d.]+) p99 ([\d.]+) max ([\d.]+) ms"
                   r"(?:, server 100-tick avg ([\d.]+) ms)?(?:; packets rx ([\d.]+)/s tx ([\d.]+)/s)?(?:; client entities (\d+))?")
FIELDS = ["frame_avg", "frame_p50", "frame_p95", "frame_p99", "frame_max", "fps", "low1", "tick_avg", "tick_p50", "tick_p95", "tick_p99",
          "tick_max", "rx", "entities"]


def read(path):
    if os.path.isdir(path):
        path = os.path.join(path, "report.txt")
    out = {}
    tq = {}
    last = None
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            m = BENCH.match(line.strip())
            if m:
                g = m.groups()
                out[g[0]] = {"frame_avg": float(g[3]), "frame_p50": float(g[4]), "frame_p95": float(g[5]), "frame_p99": float(g[6]),
                             "frame_max": float(g[7]), "fps": float(g[8]), "low1": float(g[9]), "tick_avg": float(g[11]),
                             "tick_p50": float(g[12]), "tick_p95": float(g[13]), "tick_p99": float(g[14]), "tick_max": float(g[15]),
                             "rx": float(g[17]) if g[17] else None, "entities": int(g[19]) if g[19] else None}
                last = g[0]
                continue
            if line.startswith("cmdout tick query") and last:
                out[last]["tick_query"] = line.split("->", 1)[1].strip()
    return out


def spread(vals):
    vals = [v for v in vals if v is not None]
    if not vals:
        return "-"
    med = statistics.median(vals)
    if len(vals) == 1:
        return f"{med:.2f}"
    lo, hi = min(vals), max(vals)
    pct = (hi - lo) / med * 100 if med else 0
    return f"{med:.2f} ({lo:.2f}-{hi:.2f}, {pct:.0f}%)"


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("configs", nargs="+")
    ap.add_argument("--md")
    a = ap.parse_args()
    lines = []
    for spec in a.configs:
        label, _, dirs = spec.partition("=")
        runs = [read(d) for d in dirs.split(",") if d]
        windows = sorted({w for r in runs for w in r}, key=lambda w: (w[0], w))
        lines.append(f"### {label} ({len(runs)} runs)")
        lines.append("")
        lines.append("| window | frame avg ms | frame p99 ms | worst frame ms | fps | 1% low fps | tick avg ms | tick p95 ms | tick p99 ms | worst tick ms | rx pkt/s | entities |")
        lines.append("|---|---|---|---|---|---|---|---|---|---|---|---|")
        for w in windows:
            vals = {k: [r[w][k] for r in runs if w in r] for k in FIELDS}
            lines.append(f"| {w} | " + " | ".join(spread(vals[k]) for k in ("frame_avg", "frame_p99", "frame_max", "fps", "low1", "tick_avg",
                                                                              "tick_p95", "tick_p99", "tick_max", "rx", "entities")) + " |")
        # per-scenario medians over the three windows of each run, then the spread over runs
        for sc in ("A", "B", "C"):
            per_run = []
            for r in runs:
                ws = [r[w] for w in r if re.fullmatch(sc + r"\d", w)]
                if ws:
                    per_run.append({k: statistics.mean(x[k] for x in ws) for k in ("frame_avg", "fps", "low1", "tick_avg", "tick_p95")})
            if per_run:
                lines.append(f"| **{sc} (mean of windows, spread over runs)** | " + spread([p["frame_avg"] for p in per_run]) + " | | | "
                             + spread([p["fps"] for p in per_run]) + " | " + spread([p["low1"] for p in per_run]) + " | "
                             + spread([p["tick_avg"] for p in per_run]) + " | " + spread([p["tick_p95"] for p in per_run]) + " | | | | |")
        lines.append("")
        for i, r in enumerate(runs):
            for w in sorted(r):
                if "tick_query" in r[w]:
                    lines.append(f"- run {i + 1} {w} `/tick query`: {r[w]['tick_query'][:300]}")
        lines.append("")
    text = "\n".join(lines)
    print(text)
    if a.md:
        with open(a.md, "w", encoding="utf-8", newline="\n") as f:
            f.write(text + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())

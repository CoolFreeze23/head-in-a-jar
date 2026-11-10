#!/usr/bin/env python3
"""Compares a world-check.txt report with world-expect.json: for every thing, is it there and does its saved state
still hold what it held when the world was built.

  python tools/world/check_world.py run-smoke/smoke-out/report.txt [--out RESULT.md]

Prints one line per thing that is missing or changed and a count; exit status 1 if anything is missing.
"""
import argparse
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("report")
    ap.add_argument("--expect", default=os.path.join(HERE, "world-expect.json"))
    ap.add_argument("--out")
    a = ap.parse_args()
    with open(a.expect, encoding="utf-8") as f:
        expect = json.load(f)
    with open(a.report, encoding="utf-8", errors="replace") as f:
        lines = [x.rstrip("\n") for x in f]
    got, cur = {}, None
    for line in lines:
        m = re.match(r"CHECK (\S+)$", line)
        if m:
            cur = m.group(1)
            continue
        if cur and line.startswith("inspect "):
            got[cur] = line
            cur = None
    present = changed = missing = 0
    out = ["| check | what | at | result | detail |", "|---|---|---|---|---|"]
    bad = []
    for e in expect:
        line = got.get(e["id"])
        at = " ".join(str(v) for v in e["at"])
        if line is None:
            res, detail = "NOT READ", "no inspect line in the report"
            missing += 1
        elif line.endswith("-> none") or re.search(r"-> minecraft:air\b", line) or (e["kind"] == "block" and e["has"][0].split(" ")[0] not in line):
            res, detail = "MISSING", line[:200]
            missing += 1
        else:
            lacking = [h for h in e["has"] if h not in line]
            if lacking:
                res, detail = "CHANGED", "lacks " + ", ".join(lacking)
                changed += 1
            else:
                res, detail = "present", ""
                present += 1
        out.append(f"| {e['id']} | {e['what']} | {at} | {res} | {detail.replace('|', '/')} |")
        if res != "present":
            bad.append(f"{e['id']} {e['what']} at {at}: {res} {detail}")
    summary = f"{len(expect)} things: {present} present as built, {changed} changed, {missing} missing or not read"
    print(summary)
    for b in bad:
        print("  " + b)
    if a.out:
        with open(a.out, "w", encoding="utf-8", newline="\n") as f:
            f.write(f"# World check\n\n{summary}\n\nReport: `{a.report}`\n\n" + "\n".join(out) + "\n")
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())

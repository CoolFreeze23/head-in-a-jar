#!/usr/bin/env python3
"""Cohesion walker collector: reads smoke-client run reports and writes the filled table.

  python tools/cohesion/collect.py RUN [RUN ...] OUTDIR [--previous OLD/cohesion-results.tsv] [--log smoke.log]

RUN is a smoke-out folder (report.txt, screenshots/) or a report.txt file; give one per walk batch. OUTDIR gets
cohesion-results.tsv (one row per pair), cohesion-results.md (summary, does-nothing list, mismatches, changes since
--previous) and screenshots/ (copies of the shots of every pair that did nothing or differed from its prediction).

Observed outcomes:
  works      a change of world, inventory, form, vehicle or a screen
  refuses    a message (action bar, chat, title) and no change
  feedback   only a swing, sound or particle: something answered, no change and no words
  nothing    the harness's NOTHING
  glitch     an exception, a missing record (crash or time-out), the reset or the pre-check found state left wrong
  unreached  the set-up did not put the player in the expected form, so the click was not the one the row means
"""
import argparse
import csv
import glob
import json
import os
import re
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))

INPUT = re.compile(r"^INPUT (\d+) \[(.*?)\] t=(\d+)\.\.(\d+) -> (.*)$")
STATE = ("block ", "target ", "held ", "offhand ", "inventory[", "container[", "cursor ", "form ", "vehicle ", "screen ",
         "using item start", "moved ", "hotbar ")
MESSAGE = ("actionbar ", "chat ", "title ", "subtitle ")
CLASSES = ["works", "refuses", "feedback", "nothing", "glitch", "unreached", "not run"]


def shown_part(rest):
    """What the client showed: the text after '->' without the {info} and (ignored) parts."""
    rest = re.split(r"   \{|   \(ignored: ", rest, maxsplit=1)[0]
    return rest.strip()


def split_events(shown):
    if shown == "NOTHING":
        return []
    out, cur, quote = [], "", False
    for ch in shown:
        if ch == '"':
            quote = not quote
        if ch == ";" and not quote:
            out.append(cur.strip())
            cur = ""
        else:
            cur += ch
    if cur.strip():
        out.append(cur.strip())
    return out


def classify(shown):
    if shown == "NOTHING":
        return "nothing"
    events = split_events(shown)
    if any(e.startswith(STATE) for e in events):
        return "works"
    if any(e.startswith(MESSAGE) for e in events):
        return "refuses"
    return "feedback"


def messages(shown):
    return [m.group(2) for m in re.finditer(r'(actionbar|chat|title|subtitle) "((?:[^"]|"(?! ?x\d|;|$))*)"', shown)]


# --------------------------------------------------------------------------- lang matching

def load_lang(path):
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8") as f:
        lang = json.load(f)
    pats = []
    for key, value in lang.items():
        if not isinstance(value, str) or len(re.sub(r"%(?:\d+\$)?[sd]", "", value).strip()) < 6:
            continue
        rx = re.escape(value)
        rx = re.sub(r"%(?:\d+\\\$)?[sd]", "(.+?)", rx)
        rx = rx.replace("%%", "%")
        pats.append((key, re.compile("^" + rx + "$", re.S)))
    return pats


def match_key(text, pats):
    hits = [k for k, rx in pats if rx.match(text)]
    if not hits:
        return ""
    hits.sort(key=lambda k: (k.count("."), k))
    return hits[0]


# --------------------------------------------------------------------------- reading reports

def report_paths(runs):
    out = []
    for r in runs:
        if os.path.isdir(r):
            p = os.path.join(r, "report.txt")
            if os.path.exists(p):
                out.append(p)
            else:
                out += sorted(glob.glob(os.path.join(r, "*.txt")))
        elif os.path.exists(r):
            out.append(r)
        else:
            print(f"no such run: {r}", file=sys.stderr)
    return out


def parse_report(path):
    """Returns (pairs, loose, meta). pairs: id -> record; loose: INPUT lines outside any pair."""
    with open(path, encoding="utf-8", errors="replace") as f:
        lines = [x.rstrip("\r\n") for x in f]
    pairs, loose, meta = {}, [], {"path": path, "world": "", "exceptions": [], "lines": len(lines), "finished": False}
    cur = None
    for n, line in enumerate(lines, 1):
        if line.startswith("world ") or line.startswith("kept world "):
            meta["world"] = line
        if line.startswith("EXCEPTION"):
            meta["exceptions"].append(line)
            if cur:
                cur["exceptions"].append(line)
        if re.match(r"WALK \S+ done", line):
            meta["finished"] = True
        m = re.match(r"PAIR (\S+) (begin|act|end)\b ?(.*)$", line)
        if m:
            pid, what, rest = m.groups()
            if what == "begin":
                cur = {"id": pid, "report": path, "begin": n, "phase": "setup", "inputs": [], "setup_inputs": [], "act": "",
                       "pre": [], "post": [], "reset": "", "exceptions": [], "ended": False, "setup_lines": [], "expect": ""}
                e = re.search(r"expect (\S+)", rest)
                if e:
                    cur["expect"] = e.group(1)
                pairs[pid] = cur
            elif cur and cur["id"] == pid and what == "act":
                cur["phase"], cur["act"] = "act", rest
            elif cur and cur["id"] == pid and what == "end":
                cur["phase"], cur["ended"] = "after", True
            continue
        m = INPUT.match(line)
        if m:
            rec = {"line": line, "label": m.group(2), "shown": shown_part(m.group(5))}
            if cur is None or cur["phase"] == "reset":
                loose.append(rec)
            elif cur["phase"] == "act":
                cur["inputs"].append(rec)
            elif cur["phase"] == "setup":
                cur["setup_inputs"].append(rec)
            else:
                cur["inputs"].append(rec) if not cur["inputs"] else cur["setup_inputs"].append(rec)
            continue
        if cur is None:
            continue
        if line.startswith(f"riding PRE-{cur['id']} "):
            cur["pre"].append(line)
        elif line.startswith(f"riding RESET-{cur['id']} "):
            cur["reset"] = line
            cur["phase"] = "reset"
        elif line.startswith("inspect "):
            (cur["post"] if cur["phase"] == "after" else cur["pre"]).append(line)
        elif cur["phase"] == "setup":
            cur["setup_lines"].append(line)
    return pairs, loose, meta


def parse_log(path):
    """Exceptions and errors in a smoke.log, attributed to the pair whose '/say PAIR id' came last."""
    out = {}
    if not path or not os.path.exists(path):
        return out
    cur = None
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            m = re.search(r"\] PAIR (\S+)\s*$", line.strip()) or re.search(r"\[Dev\] PAIR (\S+)", line)
            if m:
                cur = m.group(1)
                continue
            if cur and (re.search(r"Exception|/ERROR\]|\bERROR\b", line) and "SMOKE:" not in line):
                out.setdefault(cur, []).append(line.strip()[:300])
    return out


# --------------------------------------------------------------------------- judging one pair

def form_of(riding_line):
    m = re.search(r"form=(\w+)", riding_line or "")
    return m.group(1) if m else ""


def judge(pid, rec, pats, log_errors):
    """Observed class, text, lang key, the action's record line and notes."""
    notes = []
    if rec is None:
        return "not run", "", "", "", ["the pair is not in any report given"]
    errs = rec["exceptions"] + log_errors.get(pid, [])
    pre_form = form_of(rec["pre"][0]) if rec["pre"] else ""
    if rec["expect"] and pre_form and pre_form != rec["expect"]:
        notes.append(f"pre-check form {pre_form}, expected {rec['expect']}")
    inp = rec["inputs"][0] if rec["inputs"] else None
    if inp is None:
        cls = "glitch"
        notes.append("no record of the action (the run stopped or timed out before its window closed)" if not rec["ended"]
                     else "no INPUT line for the action")
        return cls, "", "", "", notes + errs
    cls = classify(inp["shown"])
    texts = messages(inp["shown"])
    keys = [match_key(t, pats) for t in texts]
    if rec["expect"] and pre_form and pre_form != rec["expect"]:
        cls = "unreached"
    click = cls
    if errs:
        notes += errs
        cls = "glitch"
    reset_form = form_of(rec["reset"])
    if rec["reset"] and reset_form and reset_form != "BODY":
        notes.append(f"after the reset the form was {reset_form} (state left inconsistent)")
        if cls != "unreached":
            cls = "glitch"
    if cls == "glitch" and click != "glitch":
        notes.append(f"the click itself: {click}")
    if not rec["reset"]:
        notes.append("no reset line (the run stopped inside this pair)")
    if len(rec["inputs"]) > 1:
        notes.append(f"{len(rec['inputs'])} INPUT lines after the act marker; the first is used")
    return cls, " / ".join(texts), " ".join(k for k in keys if k), inp["line"], notes


PRED = {"works": "works", "refuses": "refuses", "nothing": "nothing"}


def key_pattern(key):
    """A predicted lang key, where '<a|b>' means one of the alternatives, as a regex."""
    parts = re.split(r"<([^>]*)>", key)
    rx = "".join(re.escape(p) if i % 2 == 0 else "(?:" + "|".join(re.escape(x) for x in p.split("|")) + ")"
                 for i, p in enumerate(parts))
    return re.compile(rx)


def compare(pred, pred_key, cls, obs_key):
    """'match', 'MISMATCH', 'review' (misrouted or unclear predictions), 'new' (unpredicted) or '' (not judged)."""
    if cls in ("not run", "unreached"):
        return ""
    if pred == "unpredicted":
        return "new"
    if pred == "unclear":
        return "review"
    if pred == "misrouted":
        return "review" if cls in ("works", "refuses") else "MISMATCH"
    want = PRED.get(pred)
    if want is None:
        return "review"
    if cls != want:
        return "MISMATCH"
    if want == "refuses" and pred_key and obs_key:
        wanted = [key_pattern(k.strip()) for k in re.split(r"[;,]", pred_key) if k.strip()]
        if not any(w.fullmatch(o) for w in wanted for o in obs_key.split()):
            return "MISMATCH (other message)"
    return "match"


# --------------------------------------------------------------------------- writing

COLS = ["id", "item", "offhand", "sneak", "form", "target", "setup", "predicted", "predicted_lang_key", "observed",
        "observed_text", "observed_lang_key", "verdict", "notes", "input_line", "pre_state", "post_state", "screenshot", "report"]


def read_tsv(path):
    with open(path, encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f, delimiter="\t"))


def clean(s):
    return (s or "").replace("\t", " ").replace("\r", " ").replace("\n", " ")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("paths", nargs="+", help="run folders or report files, then the output folder")
    ap.add_argument("--pairs", default=os.path.join(HERE, "pairs.tsv"))
    ap.add_argument("--index", default=os.path.join(HERE, "walk-index.tsv"))
    ap.add_argument("--lang", default=os.path.join(ROOT, "src", "main", "resources", "assets", "headinajar", "lang", "en_us.json"))
    ap.add_argument("--previous", help="an earlier cohesion-results.tsv to compare with")
    ap.add_argument("--log", action="append", default=[], help="a smoke.log (build/smoke.log) of a run; may repeat")
    ap.add_argument("--all", action="store_true", help="list every pair in the table, also those not in the reports")
    a = ap.parse_args()
    if len(a.paths) < 2:
        ap.error("give at least one run and the output folder")
    runs, outdir = a.paths[:-1], a.paths[-1]
    os.makedirs(outdir, exist_ok=True)
    pats = load_lang(a.lang)
    rows = read_tsv(a.pairs)
    index = {r["id"]: r for r in read_tsv(a.index)} if os.path.exists(a.index) else {}
    log_errors = {}
    for lp in a.log:
        for k, v in parse_log(lp).items():
            log_errors.setdefault(k, []).extend(v)

    records, loose, metas = {}, [], []
    for rp in report_paths(runs):
        pairs, lo, meta = parse_report(rp)
        metas.append(meta)
        for k, v in pairs.items():
            if k in records and records[k]["inputs"] and not v["inputs"]:
                continue
            records[k] = v
        loose += [(rp, x) for x in lo]

    present = set(records)
    out_rows, shots_dir = [], os.path.join(outdir, "screenshots")
    for row in rows:
        pid = row["id"]
        if pid not in present and not a.all:
            continue
        rec = records.get(pid)
        cls, text, key, line, notes = judge(pid, rec, pats, log_errors)
        idx = index.get(pid, {})
        if idx.get("unreachable"):
            cls, notes = "not run", [f"unreachable: {idx['unreachable']}"]
        if idx.get("approx"):
            seen = []
            for n in idx["approx"].split("; "):
                if n not in seen:
                    seen.append(n)
            notes.append("set-up approximate: " + "; ".join(seen))
        verdict = compare(row["outcome"], row["lang_key"], cls, key)
        shot = ""
        if rec and (cls in ("nothing", "glitch", "unreached", "feedback") or verdict.startswith("MISMATCH") or verdict == "new"):
            src = os.path.join(os.path.dirname(rec["report"]), "screenshots", f"cw-{pid}.png")
            if os.path.exists(src):
                os.makedirs(shots_dir, exist_ok=True)
                shutil.copy2(src, os.path.join(shots_dir, f"cw-{pid}.png"))
                shot = f"screenshots/cw-{pid}.png"
        out_rows.append({
            "id": pid, "item": row["item"], "offhand": row["offhand"], "sneak": row["sneak"], "form": row["form"],
            "target": row["target"], "setup": row["setup"], "predicted": row["outcome"], "predicted_lang_key": row["lang_key"],
            "observed": cls, "observed_text": text, "observed_lang_key": key, "verdict": verdict, "notes": "; ".join(notes),
            "input_line": line, "pre_state": " | ".join(rec["pre"]) if rec else "", "post_state": " | ".join(rec["post"]) if rec else "",
            "screenshot": shot, "report": os.path.relpath(rec["report"], outdir) if rec else ""})

    with open(os.path.join(outdir, "cohesion-results.tsv"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\t".join(COLS) + "\n")
        for r in out_rows:
            f.write("\t".join(clean(r[c]) for c in COLS) + "\n")

    prev = {r["id"]: r for r in read_tsv(a.previous)} if a.previous and os.path.exists(a.previous) else None
    write_md(os.path.join(outdir, "cohesion-results.md"), out_rows, loose, metas, prev, pats)
    counts = {c: sum(1 for r in out_rows if r["observed"] == c) for c in CLASSES}
    print(f"{len(out_rows)} pairs from {len(metas)} reports: " + ", ".join(f"{k} {v}" for k, v in counts.items() if v)
          + f"; mismatches {sum(1 for r in out_rows if r['verdict'].startswith('MISMATCH'))}; loose inputs {len(loose)}")
    print(f"wrote {os.path.join(outdir, 'cohesion-results.tsv')} and cohesion-results.md")
    return 0


GOOD = ("works", "refuses")


def write_md(path, rows, loose, metas, prev, pats):
    L = ["# Cohesion walk results", ""]
    L.append(f"{len(rows)} pairs from {len(metas)} run reports. Observed classes: works (a change), refuses (a message only), "
             "feedback (swing, sound or particle only), nothing (the harness's NOTHING), glitch, unreached (set-up failed), not run.")
    L.append("")
    L.append("## Runs")
    L.append("")
    for m in metas:
        L.append(f"- `{m['path']}`: {m['world'] or 'no world line'}; {m['lines']} lines; "
                 + ("finished" if m["finished"] else "**did not reach its last line**")
                 + (f"; {len(m['exceptions'])} EXCEPTION lines" if m["exceptions"] else ""))
    L.append("")
    L.append("## Counts")
    L.append("")
    L.append("| observed | pairs |")
    L.append("|---|---|")
    for c in CLASSES:
        n = sum(1 for r in rows if r["observed"] == c)
        if n:
            L.append(f"| {c} | {n} |")
    vs = {}
    for r in rows:
        vs[r["verdict"] or "-"] = vs.get(r["verdict"] or "-", 0) + 1
    L.append("")
    L.append("Against the prediction: " + ", ".join(f"{k} {v}" for k, v in sorted(vs.items())))
    L.append("")

    def table(title, sel, extra=""):
        L.append(f"## {title} ({len(sel)})")
        L.append("")
        if extra:
            L.append(extra)
            L.append("")
        if not sel:
            L.append("None.")
            L.append("")
            return
        L.append("| id | item | form | target | setup | predicted | observed | what the client showed | notes |")
        L.append("|---|---|---|---|---|---|---|---|---|")
        for r in sel:
            shown = r["input_line"].split(" -> ", 1)[1] if " -> " in r["input_line"] else ""
            shown = re.split(r"   \{|   \(ignored: ", shown)[0]
            cell = lambda s: clean(s).replace("|", "\\|")
            item = r["item"] + (f" (off hand {r['offhand']})" if r["offhand"] else "") + (" sneaking" if r["sneak"].startswith("y") else "")
            L.append(f"| {r['id']} | {cell(item)} | {cell(r['form'])} | {cell(r['target'])} | {cell(r['setup'])} | {r['predicted']} | "
                     f"{r['observed']} | {cell(shown)[:300]} | {cell(r['notes'])[:300]}"
                     + (f" [shot]({r['screenshot']})" if r["screenshot"] else "") + " |")
        L.append("")

    table("Does nothing", [r for r in rows if r["observed"] == "nothing"], "The click produced no response at all.")
    table("Feedback only", [r for r in rows if r["observed"] == "feedback"], "An arm swing, sound or particle, and nothing changed or was said.")
    table("Glitches", [r for r in rows if r["observed"] == "glitch"])
    table("Mismatches with the prediction", [r for r in rows if r["verdict"].startswith("MISMATCH")])
    table("Set-up failed (not judged)", [r for r in rows if r["observed"] == "unreached"])
    table("New pairs (no prediction)", [r for r in rows if r["verdict"] == "new"])
    table("To review (misrouted or unclear predictions)", [r for r in rows if r["verdict"] == "review"])
    if prev is not None:
        fixed, regressed, changed, new = [], [], [], []
        for r in rows:
            p = prev.get(r["id"])
            if p is None:
                new.append(r)
            elif p["observed"] != r["observed"]:
                if r["observed"] in GOOD and p["observed"] not in GOOD:
                    fixed.append(r)
                elif p["observed"] in GOOD and r["observed"] not in GOOD:
                    regressed.append(r)
                else:
                    changed.append(r)
            elif p.get("observed_text", "") != r["observed_text"]:
                changed.append(r)
        L.append("## Since the previous results")
        L.append("")
        for title, sel in (("Fixed", fixed), ("Regressed", regressed), ("Changed", changed), ("New", new)):
            L.append(f"### {title} ({len(sel)})")
            L.append("")
            for r in sel:
                p = prev.get(r["id"], {})
                L.append(f"- {r['id']} {r['item']} on {r['target']} ({r['form']}): {p.get('observed', '-')} -> {r['observed']}"
                         + (f" ({clean(r['observed_text'])})" if r["observed_text"] else ""))
            L.append("")
    if loose:
        L.append(f"## Inputs outside any pair ({len(loose)})")
        L.append("")
        L.append("| report | input | observed | message keys | what the client showed |")
        L.append("|---|---|---|---|---|")
        for rp, x in loose:
            keys = " ".join(k for k in (match_key(t, pats) for t in messages(x["shown"])) if k)
            L.append(f"| {os.path.basename(rp)} | {clean(x['label']).replace('|', '/')} | {classify(x['shown'])} | {keys} | "
                     f"{clean(x['shown']).replace('|', '/')[:240]} |")
        L.append("")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(L) + "\n")


if __name__ == "__main__":
    sys.exit(main())

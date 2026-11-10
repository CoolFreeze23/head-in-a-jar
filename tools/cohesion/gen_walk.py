#!/usr/bin/env python3
"""Cohesion walker: turns pairs.tsv (item-on-target pairs) into smoke-client scripts walk-NN.txt.

Every pair gets its own spot in a flat survival world: the scene is set with set-up verbs and commands from
setup.json, the item goes in the hand, the view is aimed at the target, and then the action is made through real
input only (key use / key attack, sneak by the sneak binding). Each pair is framed in the report by
"PAIR <id> begin|act|end" lines, checked before and after with riding/inspect lines, and reset (the player is killed
and respawns whole, the spot is cleared) so pairs do not depend on each other.

  python tools/cohesion/gen_walk.py                 write walk-00.txt (proof batch) and walk-01.txt ...
  python tools/cohesion/gen_walk.py --only P001,P099  one script with just these pairs (walk-only.txt)
  python tools/cohesion/gen_walk.py --lint          check every generated line against the known verbs
  python tools/cohesion/gen_walk.py --list          print how each pair resolves (or why it cannot)
"""
import argparse
import copy
import csv
import glob
import json
import math
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))

OBSERVE = 30            # @observe window, ticks
SPACING = 24            # blocks between spots (more than the observer's 16-block sound filter)
COLUMNS = 6
GROUND_Y = -60          # flat world: grass at -61, the player stands at -60
BUDGET_TICKS = 9000     # script ticks per batch (7.5 min) + ~30 s start-up, well inside the 15 min client limit
MAX_PAIRS = 40
PROOF = ["P001", "P099", "P072", "P112", "P087", "P036", "P092", "P073", "P080", "P101", "P128", "P024"]

SERUM_DEFAULT = None


# --------------------------------------------------------------------------- reading

def read_pairs(path):
    with open(path, encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f, delimiter="\t"))


def load_spec(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


# --------------------------------------------------------------------------- geometry and tokens

class Spot:
    def __init__(self, index, distance=2):
        col, row = index % COLUMNS, index // COLUMNS
        self.x = (col - COLUMNS // 2) * SPACING
        self.z = (row - 4) * SPACING
        self.y = GROUND_Y
        self.d = distance

    def block(self, name):
        base = {"S": (self.x, self.y, self.z), "T": (self.x, self.y, self.z + self.d), "B": (self.x + 3, self.y, self.z)}
        return base[name]

    def selector(self):
        return f"x={self.x - 6},y={self.y - 2},z={self.z - 6},dx=18,dy=14,dz={self.d + 16}"


def fmt(v):
    if abs(v - round(v)) < 1e-9:
        return str(int(round(v)))
    return f"{v:.2f}".rstrip("0").rstrip(".")


def pack_long(x, y, z):
    v = ((x & 0x3FFFFFF) << 38) | ((z & 0x3FFFFFF) << 12) | (y & 0xFFF)
    return v - (1 << 64) if v >= (1 << 63) else v


TOKEN = re.compile(r"\{(LONG |NEAR_)?([STB])(C)?(?:\+(-?[\d.]+),(-?[\d.]+),(-?[\d.]+))?\}")


def expand(line, spot, ctx):
    def rep(m):
        kind, base, centre = m.group(1), m.group(2), m.group(3)
        bx, by, bz = spot.block(base)
        dx, dy, dz = (float(m.group(i)) if m.group(i) else 0.0 for i in (4, 5, 6))
        if kind == "LONG ":
            return str(pack_long(int(bx + dx), int(by + dy), int(bz + dz)))
        if kind == "NEAR_":
            return f"x={fmt(bx + 0.5 + dx)},y={fmt(by + dy)},z={fmt(bz + 0.5 + dz)},distance=..1.5"
        if centre:
            return f"{fmt(bx + 0.5 + dx)} {fmt(by + dy)} {fmt(bz + 0.5 + dz)}"
        if any(float(m.group(i) or 0) % 1 for i in (4, 5, 6)):
            raise ValueError(f"fractional offset on a block token in: {line}")
        return f"{int(bx + dx)} {int(by + dy)} {int(bz + dz)}"

    for key, value in ctx.items():
        line = line.replace("{" + key + "}", value)
    return TOKEN.sub(rep, line)


# --------------------------------------------------------------------------- resolving a pair

GENERIC = [
    (re.compile(r"^block (headinajar:\w+|minecraft:\w+)"), "block"),
    (re.compile(r"^(?:vanilla mob|boss|pet) (minecraft:\w+)"), "mob"),
    (re.compile(r"^shell (headinajar:\w+)"), "shell"),
    (re.compile(r"^mecha (?:headinajar:)?(\w+)"), "mecha"),
]

LIST_FIELDS = ("steps", "late", "pre", "post")


def merge(into, extra):
    for k, v in extra.items():
        if k in LIST_FIELDS:
            into.setdefault(k, [])
            into[k] = into[k] + list(v)
        elif k == "approx":
            into.setdefault("approx_list", []).append(v)
        else:
            into[k] = v


def target_scene(target, spec):
    """The scene for a target string: an explicit entry, else a generic rule. Returns (scene, error)."""
    if target in spec["targets"]:
        return copy.deepcopy(spec["targets"][target]), None
    action = None
    t = target
    if t.endswith(", left click"):
        t, action = t[: -len(", left click")], "attack"
        if t in spec["targets"]:
            s = copy.deepcopy(spec["targets"][t])
            s["action"] = "attack"
            return s, None
    for rx, kind in GENERIC:
        m = rx.match(t)
        if not m:
            continue
        ident = m.group(1)
        if kind == "block":
            s = {"steps": [f"/setblock {{T}} {ident}"], "aim": "block"}
        elif kind == "mob":
            h = spec["entity_aim"].get(ident, 0.5)
            s = {"steps": [f"/summon {ident} {{TC}} {{NoAI:1b,Rotation:[180f,0f]{{NBTC}}}}"], "aim": [0.5, h, 0.5], "mob": ident}
        elif kind == "shell":
            h = spec["entity_aim"].get(ident, 0.6)
            s = {"steps": [f"/summon {ident} {{TC}} {{NoAI:1b,Rotation:[180f,0f]}}"], "aim": [0.5, h, 0.5]}
        else:
            s = {"steps": [f"mecha {ident} {{T}} {{MODS}}"], "aim": [0.5, spec["entity_aim"]["mecha"], 0.5]}
        if action:
            s["action"] = action
        return s, None
    return None, f"target not mapped in setup.json: {target!r}"


def resolve(row, spec):
    """Everything needed to write one pair, or {'unreachable': why}."""
    pid = row["id"]
    r = {"id": pid, "row": row}
    form = row["form"].strip()
    if form not in spec["forms"]:
        return {"id": pid, "row": row, "unreachable": f"form not mapped in setup.json: {form!r}"}
    scene, err = target_scene(row["target"].strip(), spec)
    if err:
        return {"id": pid, "row": row, "unreachable": err}
    setup = row["setup"].strip()
    if setup not in spec["setups"]:
        return {"id": pid, "row": row, "unreachable": f"setup not mapped in setup.json: {setup!r}"}
    spec = copy.deepcopy(spec)
    merge(r, {k: v for k, v in spec["forms"][form].items() if k != "steps"})
    r["form_name"] = form
    merge(r, scene)
    merge(r, spec["setups"][setup])
    merge(r, spec["pairs"].get(pid, {}))
    if "form" in r and r["form"] != form:
        form = r["form"]
        r["form_name"] = form
        for k, v in spec["forms"][form].items():
            if k != "steps":
                r[k] = v
    if "unreachable" in r:
        return {"id": pid, "row": row, "unreachable": r["unreachable"]}
    r.setdefault("form_steps", list(spec["forms"][form].get("steps", [])))
    if "with arm" in form and "fit manipulator_arm" not in r["form_steps"]:
        r["form_steps"].append("fit manipulator_arm")
    # the item and off-hand item
    item = r.get("item") or row["item"].strip()
    item = spec["items"].get(item, item.split(" ")[0])
    r["item"] = item_token(item, spec)
    off = r.get("offhand") if "offhand" in r else row["offhand"].strip()
    r["offhand"] = item_token(spec["items"].get(off, off.split(" ")[0]), spec) if off else ""
    r["sneak"] = row["sneak"].strip().lower().startswith("y")
    return r


def item_token(item, spec):
    toks = spec["items_tokens"]
    if item == "{SERUM}":
        return toks["SERUM"]
    m = re.fullmatch(r"\{HEAD:([\w:]+)\}", item)
    if m:
        return toks.get("HEAD:" + m.group(1), toks["HEAD"].replace("{0}", m.group(1)))
    return item


# --------------------------------------------------------------------------- writing one pair

def aim_point(r, spot):
    """The point the crosshair should be on (None for the air)."""
    aim = r.get("aim", "block")
    tx, ty, tz = spot.block("T")
    if aim == "air":
        return None
    if aim == "ground":
        return (tx + 0.5, ty - 0.05, tz + 0.5)
    if aim == "block":
        return (tx + 0.5, ty + 0.5, tz + 0.5)
    return (tx + aim[0], ty + aim[1], tz + aim[2])


def aim_pitch(r, spot, eye):
    aim = r.get("aim", "block")
    sx, sy, sz = spot.block("S")
    px, pz = sx + 0.5, sz + 0.5
    tx, ty, tz = spot.block("T")
    if aim == "air":
        return -60.0
    if aim == "ground":
        point = (tx + 0.5, ty - 0.05, tz + 0.5)
    elif aim == "block":
        point = (tx + 0.5, ty + 0.5, tz + 0.5)
    else:
        point = (tx + aim[0], ty + aim[1], tz + aim[2])
    dist = math.hypot(point[0] - px, point[2] - pz)
    return math.degrees(math.atan2((sy + eye) - point[1], dist))


def aim_expect(r, spot):
    """What the crosshair should be on when the click is made: block:X,Y,Z, entity[:ID], miss, or any."""
    if r.get("aim_check"):
        return r["aim_check"]
    aim = r.get("aim", "block")
    tx, ty, tz = spot.block("T")
    if aim == "air":
        return "miss"
    if aim == "block":
        return f"block:{tx},{ty},{tz}"
    if aim == "ground":
        return f"block:{tx},{ty - 1},{tz}"
    return "entity" + (":" + r["mob"] if r.get("mob") else "")


def hold_for(item, spec):
    return spec["hold"].get(item.split("[")[0], 0)


def pair_lines(r, index, spec, shots=True):
    """Script lines for one pair, at spot `index`."""
    dist = r.get("distance", 2)
    spot = Spot(index, int(dist))
    pid = r["id"]
    row = r["row"]
    mods = r.get("mods", "hydraulic_legs")
    nbt = r.get("nbt_replace") or r.get("nbt", "")
    body_at = r.get("body_at", "B")
    head_at = r.get("head_at", "B")
    ctx = {"MODS": mods, "NBTC": ("," + nbt) if nbt else "", "NBT": nbt or "Silent:0b", "SEL": spot.selector()}
    base, _, off = body_at.partition("+")
    ctx["BODYAT"] = expand("{" + base + "C" + ("+" + off if off else "") + "}", spot, {})
    if head_at == "T":
        ctx["HEADFROM"] = expand("{TC+0,0,-1.5}", spot, {}) + " 0 0"
    else:
        ctx["HEADFROM"] = expand("{BC}", spot, {}) + " -90 0"
    E = lambda line: expand(line, spot, ctx)
    eye = r.get("eye", 1.62)
    if r["sneak"] and r.get("form_name") in ("BODY", "any", "any (incl. bare JAR)"):
        eye = spec["eye"]["sneaking"]
    pitch = aim_pitch(r, spot, eye)
    action = r.get("action", "use")
    hold = hold_for(r["item"], spec)
    if action == "use":
        action_line = f"key use hold {hold}" if hold else "key use"
    elif action == "attack":
        action_line = "key attack"
    else:
        action_line = action
    # the input verb itself waits out any hold; its window closes OBSERVE ticks after that
    after = OBSERVE + 4
    label = " | ".join(x for x in (row["item"], ("offhand " + row["offhand"]) if row["offhand"] else "", "sneak" if r["sneak"] else "",
                                    r.get("form_name", ""), row["target"], row["setup"]) if x)
    label = label.replace("\t", " ")
    out = [f"# ---- {pid}: {label}"]
    out.append(f"report PAIR {pid} begin spot {spot.x} {spot.z} expect {r.get('check', 'BODY')} aim {aim_expect(r, spot)} | {label}")
    out.append(f"/say PAIR {pid}")
    out.append("/kill @e[type=minecraft:slime]")
    out.append(E("/tp @s {SC} 0 0"))
    out.append("/effect give @s minecraft:resistance 999 4 true")
    out.append("/effect give @s minecraft:saturation 999 0 true")
    out.append("wait 5")
    steps = list(r.get("steps", []))
    tset = [i for i, x in enumerate(steps) if x.startswith("/setblock {T} ")]
    for i in tset[:-1]:
        steps[i] = None
    out += [E(x) for x in steps if x is not None]
    out.append("/clear @s")
    out.append("wait 5")
    out += [E(x) for x in r.get("form_steps", [])]
    out += [E(x) for x in r.get("late", [])]
    given = 0
    for i, x in enumerate(out):
        m = re.match(r"/give @s (\S+)(?: (\d+))?$", x)
        if m:
            out[i] = f"/item replace entity @s inventory.{given} with {m.group(1)}" + (f" {m.group(2)}" if m.group(2) else "")
            given += 1
    fixed = r.get("riding") or r.get("fixed")
    if fixed:
        out.append(f"look 0 {fmt(round(pitch, 1))}")
    else:
        out.append(E("/tp @s {SC} 0 " + fmt(round(pitch, 1))))
    point = aim_point(r, spot)
    if point:
        # the computed pitch above assumes an eye height; lookpos aims from the real eye (riding, sneaking, jar)
        out.append("wait 3")
        out.append("lookpos " + " ".join(fmt(round(v, 2)) for v in point))
    out.append("wait 10")
    pre = [E(x) for x in r.get("pre", [])]
    if pre:
        out += pre
        if any(is_input(x) for x in pre):
            out.append(f"wait {OBSERVE + 3}")
    out.append(f"/item replace entity @s weapon.mainhand with {r['item']}")
    out.append(f"/item replace entity @s weapon.offhand with {r['offhand'] or 'minecraft:air'}")
    out.append("wait 10")
    out.append(f"riding PRE-{pid}")
    out.append("inspect me")
    if r["sneak"]:
        out.append("sneak down")
        out.append(f"wait {OBSERVE + 3}")
        if point:
            out.append("lookpos " + " ".join(fmt(round(v, 2)) for v in point))
            out.append("wait 2")
    out.append("inspect aim")
    out.append(f"report PAIR {pid} act {action_line}")
    out.append(action_line)
    out.append("wait 12")
    if shots:
        out.append(f"shot cw-{pid}")
        out.append(f"wait {max(1, after - 12 - 5)}")
    else:
        out.append(f"wait {max(1, after - 12)}")
    out.append("inspect me")
    if r.get("aim") == "block":
        out.append(E("inspect block {T}"))
    out.append(f"report PAIR {pid} end")
    if r["sneak"]:
        out.append("sneak up")
    out += [E(x) for x in r.get("post", [])]
    # reset: a real death puts every form back to BODY (the jar shatters, modules and gear drop)
    out.append("/kill @s")
    out.append("wait 15")
    if r.get("bystander"):
        out.append("/headoffice restore Bystander")
        out.append("wait 3")
        out.append("bystander remove")
    out.append(E("/kill @e[type=!minecraft:player,{SEL}]"))
    out.append(E(f"/fill {spot.x - 6} {spot.y} {spot.z - 6} {spot.x + 12} {spot.y + 12} {spot.z + spot.d + 10} minecraft:air"))
    out.append(E(f"/fill {spot.x - 6} {spot.y - 1} {spot.z - 6} {spot.x + 12} {spot.y - 1} {spot.z + spot.d + 10} minecraft:grass_block"))
    out.append("wait 3")
    out.append(E("/kill @e[type=minecraft:item,{SEL}]"))
    out.append("/clear @s")
    out.append(f"riding RESET-{pid}")
    out.append("")
    return out


INPUT_VERBS = {"key", "press", "sneak", "jump", "sprint", "hotbar", "click", "scroll", "turn", "type", "widget", "at", "slot", "rightclick", "walk"}


def is_input(line):
    w = line.split(" ", 1)[0]
    if w == "bystander":
        return len(line.split()) > 1 and line.split()[1] in {"useblock", "useitem", "useentity", "attack", "sneak", "swing", "button", "key", "chair", "operate", "form", "close"}
    return w in INPUT_VERBS and not line.endswith(" up")


HEADER = """# Cohesion walk {name}: {count} item-on-target pairs, each on its own spot, real input only.
# Generated by tools/cohesion/gen_walk.py from tools/cohesion/pairs.tsv and setup.json; do not edit by hand.
# Run: bash tools/smoke.sh tools/cohesion/{name}.txt   then: python tools/cohesion/collect.py run-smoke/smoke-out OUTDIR
@world flat
@gamemode survival
@difficulty easy
@observe {observe}
wait 40
/gamerule doMobSpawning false
/gamerule doDaylightCycle false
/gamerule doWeatherCycle false
/gamerule doImmediateRespawn true
/gamerule keepInventory true
/gamerule showDeathMessages false
/gamerule spawnRadius 0
/gamerule announceAdvancements false
/time set 6000
/weather clear
/setworldspawn 0 -60 -130
/forceload add -96 -112 96 112
wait 40
/kill @e[type=!minecraft:player]
wait 5
report WALK {name} {count} pairs
"""


def tick_cost(line):
    w = line.split(" ")
    if not line or line.startswith("#") or line.startswith("@"):
        return 0
    if w[0] == "wait":
        return int(w[1])
    if w[0] == "shot":
        return 5
    m = re.search(r"hold (\d+)", line)
    if m:
        return int(m.group(1)) + 2
    if w[0] in ("key", "sneak", "press"):
        return 2
    if w[0] in ("widget", "at"):
        return 3
    if w[0] == "report" or w[0] == "riding" or w[0] in ("look", "observe"):
        return 1
    return 2


def write_script(path, name, blocks):
    lines = HEADER.format(name=name, count=len(blocks), observe=OBSERVE).split("\n")
    for b in blocks:
        lines += b
    lines.append(f"report WALK {name} done")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    return lines


# --------------------------------------------------------------------------- lint

def load_ids():
    ids = set()
    lang = os.path.join(ROOT, "src", "main", "resources", "assets", "headinajar", "lang", "en_us.json")
    if os.path.exists(lang):
        with open(lang, encoding="utf-8") as f:
            for k in json.load(f):
                p = k.split(".")
                if len(p) == 3 and p[0] in ("item", "block", "entity") and p[1] == "headinajar":
                    ids.add("headinajar:" + p[2])
    return ids


def java_enum(rel):
    path = os.path.join(ROOT, "src", "main", "java", "com", "coolfreeze23", "headinajar", *rel.split("/"))
    if not os.path.exists(path):
        return None
    with open(path, encoding="utf-8") as f:
        return {m.lower() for m in re.findall(r"^    ([A-Z_]+)\(", f.read(), re.M)}


N = r"-?\d+"
F = r"-?\d+(?:\.\d+)?"
XYZ = rf"{N} {N} {N}"
FXYZ = rf"{F} {F} {F}"
MODE = r"(?: (?:tap|down|up|hold \d+))?"
KEYS = {"use", "attack", "sneak", "crouch", "jump", "sprint", "forward", "back", "inventory", "drop", "chat", "menu", "jar_menu",
        "swap", "secondary", "zoom", "drive", "ability", "wheels", "collar_zoom", "swapoffhand", "toggleperspective"}
VERBS = {
    "key": rf"^key (\S+){MODE}$",
    "sneak": rf"^sneak{MODE}$",
    "press": rf"^press [\w.]+{MODE}$",
    "wait": r"^wait \d+$",
    "shot": r"^shot [\w.-]+$",
    "report": r"^report .+$",
    "riding": r"^riding \S+$",
    "look": rf"^look {F} {F}$",
    "lookpos": rf"^lookpos {F} {F} {F}$",
    "inspect": rf"^inspect (?:me|aim|block {XYZ}|near \S+(?: {F})?)$",
    "widget": r'^widget (?:"[^"]+"|#\d+|\S+)(?: (?:left|right|middle|shift|double|hold \d+))*$',
    "at": rf"^at (?:rel )?{F} {F}(?: (?:left|right|middle|shift|double|hold \d+))*$",
    "jar": r"^jar$", "separate": r"^separate$", "swap": r"^swap$", "pickup": r"^pickup$", "mount": r"^mount$",
    "pilotgenes": r"^pilotgenes$", "lab": r"^lab$",
    "fit": r"^fit [a-z_]+(?:,[a-z_]+)*$",
    "hostify": rf"^hostify [a-z_]+:[a-z_]+ {XYZ}$",
    "jarhead": rf"^jarhead [a-z_]+:[a-z_]+ {XYZ}$",
    "mecha": rf"^mecha [a-z_]+ {XYZ} [a-z_]+(?:,[a-z_]+)*$",
    "headblock": rf"^headblock {XYZ} [a-z_:]+(?:,[a-z_:]+)*$",
    "chair": rf"^chair {XYZ}$",
    "genes": r"^genes (?:me|mecha) [a-z_]+(?:,[a-z_]+)*$",
    "bystander": None,
}
BYSTANDER = {
    "spawn": rf"^bystander spawn {XYZ}(?: {F})?$", "tp": rf"^bystander tp {XYZ}(?: {F}(?: {F})?)?$",
    "look": rf"^bystander look {F} {F}$", "gamemode": r"^bystander gamemode \w+$",
    "give": r"^bystander give [\w:]+(?: \d+)?$", "hold": r"^bystander hold [\w:]+(?: \d+)?(?: offhand)?$",
    "useblock": rf"^bystander useblock {XYZ}(?: \w+)?$", "useitem": r"^bystander useitem$",
    "useentity": r"^bystander useentity (?:me|nearest [\w:]+)$", "attack": r"^bystander attack (?:me|nearest [\w:]+)$",
    "form": r"^bystander form (?:jar|linked|body|compact|wide)$", "remove": r"^bystander remove$",
    "state": r"^bystander state$", "operate": rf"^bystander operate {XYZ}$", "chair": rf"^bystander chair {XYZ}$",
    "mark": r"^bystander mark$", "packets": r"^bystander packets.*$",
}
COMMANDS = {"tp", "setblock", "fill", "summon", "kill", "item", "give", "clear", "effect", "gamerule", "time", "weather",
            "forceload", "setworldspawn", "data", "execute", "damage", "say", "headoffice"}
DIRECTIVES = {"world", "gamemode", "difficulty", "observe", "seed", "keep", "clean"}


def balanced(s):
    depth = {"{": 0, "[": 0}
    pairs = {"}": "{", "]": "["}
    quote = None
    for ch in s:
        if quote:
            if ch == quote:
                quote = None
            continue
        if ch in "\"'":
            quote = ch
        elif ch in depth:
            depth[ch] += 1
        elif ch in pairs:
            depth[pairs[ch]] -= 1
            if depth[pairs[ch]] < 0:
                return False
    return quote is None and all(v == 0 for v in depth.values())


def lint_line(line, ids, enums):
    if not line or line.startswith("#"):
        return None
    if line.startswith("@"):
        return None if line[1:].split()[0] in DIRECTIVES else "unknown directive"
    if re.search(r"\{(?:[STB]C?|NEAR_\w+|SEL|MODS|NBTC?|BODYAT|HEADFROM|SERUM|HEAD:[^}]*)(?:\+[^}]*)?\}", line):
        return "unexpanded token"
    w = line.split(" ", 1)[0]
    if line.startswith("/"):
        cmd = w[1:]
        if cmd not in COMMANDS:
            return f"unknown command /{cmd}"
        if not balanced(line):
            return "unbalanced brackets or quotes"
        for ident in re.findall(r"\bheadinajar:([a-z0-9_]+)", line):
            if "headinajar:" + ident not in ids and ident not in ("host", "cranial_saline", "nutrient_broth"):
                return f"unknown id headinajar:{ident}"
        return None
    if w == "grab":
        return "grab is shadowed by the harness's mouse-grab verb (the Telekinesis grab never runs)"
    if w not in VERBS:
        return f"unknown verb {w} (would run as a server command)"
    if w == "bystander":
        parts = line.split()
        if len(parts) < 2 or parts[1] not in BYSTANDER:
            return "unknown bystander verb"
        if not re.match(BYSTANDER[parts[1]], line):
            return f"bad arguments for bystander {parts[1]}"
        for ident in re.findall(r"\bheadinajar:([a-z0-9_]+)", line):
            if "headinajar:" + ident not in ids:
                return f"unknown id headinajar:{ident}"
        return None
    m = re.match(VERBS[w], line)
    if not m:
        return f"bad arguments for {w}"
    if w == "key" and m.group(1).lower() not in KEYS and not m.group(1).startswith("hotbar."):
        return f"unknown key mapping {m.group(1)}"
    if w == "fit" and enums.get("module"):
        for name in line.split(" ", 1)[1].split(","):
            if name not in enums["module"]:
                return f"unknown jar module {name}"
    if w == "mecha" and enums.get("frame"):
        p = line.split()
        if p[1] not in enums["frame"]:
            return f"unknown mecha frame {p[1]}"
        for name in p[5].split(","):
            if name not in enums["mecha_module"]:
                return f"unknown mecha module {name}"
    if w == "genes" and enums.get("gene"):
        for name in line.split()[2].split(","):
            if name not in enums["gene"]:
                return f"unknown gene {name}"
    if w != "report":
        for ident in re.findall(r"\bheadinajar:([a-z0-9_]+)", line):
            if "headinajar:" + ident not in ids:
                return f"unknown id headinajar:{ident}"
    return None


def lint_files(paths):
    ids = load_ids()
    enums = {"module": java_enum("module/ModuleType.java"), "frame": java_enum("mecha/MechaFrame.java"),
             "mecha_module": java_enum("mecha/MechaModule.java"), "gene": java_enum("genetics/Gene.java")}
    problems, total, counts = [], 0, {}
    for p in paths:
        with open(p, encoding="utf-8", newline="") as f:
            raw = f.read()
        if "\r" in raw:
            problems.append((p, 0, "CRLF line endings (smoke scripts must be LF)"))
        for n, line in enumerate(raw.split("\n"), 1):
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            total += 1
            key = line.split(" ", 1)[0]
            counts[key] = counts.get(key, 0) + 1
            err = lint_line(line, ids, enums)
            if err:
                problems.append((p, n, f"{err}: {line}"))
    return total, counts, problems


# --------------------------------------------------------------------------- main

HEAD_GUARD_NOTE = ("the scene draws a jarred head before the click, and drawing any jarred head crashes the client "
                   "(Pose stack not empty: JarRenderer.drawWith pops one pose more than it pushes); walk with --no-head-guard once fixed")


def head_risk(r, spec):
    """'scene' if the set-up shows a jarred head (placed, racked, on a turret, mount or socket), 'item' if one is in a hand, else ''."""
    lines = pair_lines(r, 0, spec, False)
    act = next(i for i, x in enumerate(lines) if x.startswith(f"report PAIR {r['id']} act"))
    for x in lines[:act]:
        if re.match(r"(headblock|jarhead) ", x) or (x.startswith("/setblock") and "jarred_head" in x and re.search(r'Type:"[a-z]', x)):
            return "scene"
    if "jarred_head" in r["item"] or "jarred_head" in (r["offhand"] or ""):
        return "item"
    return ""


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--pairs", default=os.path.join(HERE, "pairs.tsv"))
    ap.add_argument("--spec", default=os.path.join(HERE, "setup.json"))
    ap.add_argument("--out", default=HERE)
    ap.add_argument("--only", help="comma-separated pair ids: write walk-only.txt with just these")
    ap.add_argument("--no-shots", action="store_true", help="no screenshot per pair")
    ap.add_argument("--no-head-guard", action="store_true",
                    help="walk every pair in the normal batches, also those whose scene draws a jarred head (use once drawing one no longer crashes the client)")
    ap.add_argument("--lint", action="store_true", help="lint the walk-*.txt files in --out (after generating)")
    ap.add_argument("--lint-only", action="store_true", help="lint existing files, do not regenerate")
    ap.add_argument("--list", action="store_true", help="print how each pair resolves")
    a = ap.parse_args()

    if a.lint_only:
        return report_lint(sorted(glob.glob(os.path.join(a.out, "walk-*.txt"))))
    spec = load_spec(a.spec)
    rows = read_pairs(a.pairs)
    resolved = [resolve(r, spec) for r in rows]
    heads = []
    if not a.no_head_guard:
        for r in resolved:
            if "unreachable" in r:
                continue
            risk = head_risk(r, spec)
            if risk == "scene":
                r["unreachable"] = HEAD_GUARD_NOTE
            elif risk == "item":
                heads.append(r)
    ok = [r for r in resolved if "unreachable" not in r and r not in heads]
    bad = [r for r in resolved if "unreachable" in r]
    if a.list:
        for r in resolved:
            print(f"{r['id']}\t{'UNREACHABLE ' + r['unreachable'] if 'unreachable' in r else r.get('form_name') + ' / ' + r['item']}"
                  + ("\t~ " + "; ".join(r.get("approx_list", [])) if r.get("approx_list") else ""))
    written = []
    if a.only:
        want = a.only.split(",")
        sel = [r for r in ok if r["id"] in want]
        missing = set(want) - {r["id"] for r in sel}
        if missing:
            print("not walkable:", ", ".join(sorted(missing)), file=sys.stderr)
        blocks = [pair_lines(r, i, spec, not a.no_shots) for i, r in enumerate(sel)]
        path = os.path.join(a.out, "walk-only.txt")
        write_script(path, "walk-only", blocks)
        written.append(path)
    else:
        for old in glob.glob(os.path.join(a.out, "walk-*.txt")):
            os.remove(old)
        byid = {r["id"]: r for r in ok}
        proof = [byid[p] for p in PROOF if p in byid]
        blocks = [pair_lines(r, i, spec, not a.no_shots) for i, r in enumerate(proof)]
        path = os.path.join(a.out, "walk-00.txt")
        write_script(path, "walk-00", blocks)
        written.append(path)
        batches, cur, ticks = [], [], 0
        for r in ok:
            cost = sum(tick_cost(x) for x in pair_lines(r, 0, spec, not a.no_shots))
            if cur and (ticks + cost > BUDGET_TICKS or len(cur) >= MAX_PAIRS):
                batches.append(cur)
                cur, ticks = [], 0
            cur.append(r)
            ticks += cost
        if cur:
            batches.append(cur)
        for n, batch in enumerate(batches, 1):
            name = f"walk-{n:02d}"
            blocks = [pair_lines(r, i, spec, not a.no_shots) for i, r in enumerate(batch)]
            path = os.path.join(a.out, name + ".txt")
            write_script(path, name, blocks)
            written.append(path)
        # a jarred head in the hand may be placed or racked by the click, and drawing it crashes the client: one pair per run
        for r in heads:
            name = f"walk-h-{r['id']}"
            path = os.path.join(a.out, name + ".txt")
            write_script(path, name, [pair_lines(r, 0, spec, not a.no_shots)])
            written.append(path)
    write_index(os.path.join(a.out, "walk-index.tsv"), resolved, written)
    summary = []
    for p in written:
        with open(p, encoding="utf-8") as f:
            lines = f.read().split("\n")
        ticks = sum(tick_cost(x.strip()) for x in lines)
        pairs = sum(1 for x in lines if x.startswith("report PAIR") and " begin " in x)
        summary.append((os.path.basename(p), pairs, ticks))
    for name, pairs, ticks in summary:
        print(f"{name}: {pairs} pairs, {ticks} script ticks = {ticks / 20 / 60:.1f} min (+ about 0.5 min start-up)")
    print(f"walkable {len(ok)} of {len(resolved)} pairs; unreachable {len(bad)}")
    for r in bad:
        print(f"  {r['id']}: {r['unreachable']}")
    if a.lint:
        return report_lint(written)
    return 0


def write_index(path, resolved, written):
    """One line per pair: where it is walked, what form the pre-check expects, the action, and set-up notes."""
    where = {}
    for p in written:
        with open(p, encoding="utf-8") as f:
            for line in f:
                m = re.match(r"report PAIR (\S+) begin spot (\S+) (\S+) expect (\S+)", line)
                if m:
                    where.setdefault(m.group(1), []).append(f"{os.path.basename(p)[:-4]}@{m.group(2)},{m.group(3)}")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("id\twalks\texpect_form\taction\tapprox\tunreachable\n")
        for r in resolved:
            if "unreachable" in r:
                f.write(f"{r['id']}\t\t\t\t\t{r['unreachable']}\n")
                continue
            notes = "; ".join(dict.fromkeys(r.get("approx_list", []) + ([r["approx_eye"]] if r.get("approx_eye") else [])))
            f.write(f"{r['id']}\t{' '.join(where.get(r['id'], []))}\t{r.get('check', 'BODY')}\t{r.get('action', 'use')}\t{notes}\t\n")


def report_lint(paths):
    total, counts, problems = lint_files(paths)
    print(f"lint: {len(paths)} files, {total} lines, {len(problems)} problems")
    print("  lines by verb or command: " + ", ".join(f"{k} {v}" for k, v in sorted(counts.items(), key=lambda kv: -kv[1])))
    for p, n, msg in problems[:200]:
        print(f"  {os.path.basename(p)}:{n}: {msg}")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())

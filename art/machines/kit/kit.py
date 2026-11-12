"""Noggin & Sons machine kit: the shared parts every machine generator is built from.

One definition of each kit part (K1 feet and plinth, K2 rounded casing with rimmed panels, K3 lamp, K4 gauge, K5 hatch
and drawer, K6b side port, K7 maker's plate, K8 stripe band, K9 collection flag, K10 terminal, K12 lab bench), the
model writer (a GeckoLib-format .bbmodel with a box-UV placeholder sheet grouped by material), the clip writer (sparse
keys with GeckoLib 4.9.3 easings, effects one per tick), the house clips (hatch, drawer, flag, refusal), and the checks
every machine must pass (integer sizes, nothing under 1 px, no two same-facing faces on one plane, flush joints,
height, bone budget, the top cell's shapes covering what is drawn).

Coordinates: pixels, origin at the block's bottom centre, y up, the machine's front faces -Z, the block spans x and z
-8..8 (add 8 to x and z for a Java block model). Static parts go in the group `static` (the baked block model);
bones are everything else; stand-ins go in groups named `standin_*` (never exported).

A machine generator does:

    import sys, os; sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'kit')); import kit
    m = kit.Model('galvanic_pile', budget=3)
    kit.plinth(m, -7, 7, -7, 7)
    ...
    m.write(os.path.join(HERE, 'galvanic_pile.bbmodel'))      # also writes the placeholder PNG and runs the checks
    kit.write_clips(path, CLIPS, code_channels=m.code)        # in the anim script

Read the kit's design notes for each part's size, pivot, material and clips.
"""
import base64
import copy
import json
import math
import os
import sys
import uuid as uuidlib

HERE = os.path.dirname(os.path.abspath(__file__))
T = 20.0  # ticks per second

# ---------------------------------------------------------------------------------------------------------------------
# Placeholder materials: palette D (art/palette.json, set D). Mid-tones, except the darks, which take the step a
# paint will put there (r1, s1): the Hopper's placeholder dark steel was a mid-grey and hid every dark mass in the
# review renders, so here the darks are dark from the start. (hex, alpha).
# ---------------------------------------------------------------------------------------------------------------------
MATERIALS = {
    'enamel':  ('ddd5c3', 255),   # e3, the company's ivory paint
    'enamel2': ('c3bcb1', 255),   # e2, secondary enamel parts one step under the body
    'brass':   ('d99a35', 255),   # b3, the field brass stays on (never darker)
    'steel':   ('7b818b', 255),   # s3
    'dsteel':  ('42464f', 255),   # s1, running gear, plinths, undersides
    'zinc':    ('a1a7af', 255),   # s4, light steel (the pile's zinc discs)
    'copper':  ('b26247', 255),   # vanilla copper mid
    'rubber':  ('27262d', 255),   # r1, feet, felt, linings, recesses
    'yellow':  ('f4bd1e', 255),   # w2
    'stripe':  ('f4bd1e', 255),   # w2 with r1 bands at 45 degrees (drawn on the placeholder so renders show the rule)
    'glass':   ('86cac1', 96),    # teal glass t3 at low alpha: the baked frame-and-streaks glass
    'jglass':  ('8bc987', 110),   # jar glass j3 (lab glassware)
    'saline':  ('4ea9ab', 255),   # l1, opaque behind glass (2.6)
    'sample':  ('7d323a', 255),   # f1, a blood sample's plum
    'gauge':   ('5aa6a4', 255),   # t2, the gauge face (texture swapped by blockstate)
    'lens':    ('77f08a', 255),   # the lamp lens, shown as `ready` in review renders; in game its texture is the state's
    'lens_off': ('27262d', 255),
    'lens_ready': ('77f08a', 255),
    'lens_wait': ('ffc24e', 255),
    'lens_work': ('d47c16', 255),
    'lens_low': ('6e3606', 255),
    'lens_fault': ('ff5a36', 255),
    'stand':   ('eba688', 255),   # stand-ins (items, the player), never exported
    'standi':  ('a44d4b', 255),   # stand-in items (meat and the like)
}
ORDER = ['enamel', 'enamel2', 'brass', 'steel', 'dsteel', 'zinc', 'copper', 'rubber', 'yellow', 'stripe', 'glass',
         'jglass', 'saline', 'sample', 'gauge', 'lens', 'lens_off', 'lens_ready', 'lens_wait', 'lens_work', 'lens_low',
         'lens_fault', 'stand', 'standi']

NS = uuidlib.UUID('7a2d0c11-3b4e-4f60-8a1b-2c3d4e5f6a7b')


def nid(model, kind, name):
    return str(uuidlib.uuid5(NS, '%s/%s/%s' % (model, kind, name)))


# ---------------------------------------------------------------------------------------------------------------------
# The model being built
# ---------------------------------------------------------------------------------------------------------------------
class Model:
    def __init__(self, name, budget, tex_w=128):
        self.name = name
        self.budget = budget
        self.tex_w = tex_w
        self.bones = [('static', [0, 0, 0], None, None, {})]
        self.cubes = []          # (name, from, to, mat, parent, kw)
        self.locators = []       # (name, pos, parent)
        self.anchors = []        # (name, pos, note): BER item anchors (data for the notes, plus stand-ins)
        self.code = set()        # (bone, channel) driven by code: never keyed
        self.shapes = {}         # 'lower'/'upper': [(x0,y0,z0,x1,y1,z1) in Java cell coordinates]
        self.events = []         # (clip, tick, kind, what, locator) for the notes
        self.off = [0, 0, 0]     # placement offset (used by the kit sheet to lay parts side by side)
        self._names = set()

    def _o(self, p):
        return [p[i] + self.off[i] for i in range(3)]

    # -- nodes -------------------------------------------------------------------------------------------------------
    def bone(self, name, pivot, parent=None, rot=None, hidden=False, code=()):
        if any(b[0] == name for b in self.bones):
            sys.exit('%s: bone %s twice' % (self.name, name))
        self.bones.append((name, self._o(pivot), parent, rot, {'hidden': hidden}))
        for ch in code:
            self.code.add((name, ch))
        return name

    def group(self, name, parent='static', hidden=False):
        """A plain group (a sub-group of static for a blockstate part, or a stand-in group)."""
        return self.bone(name, [-v for v in self.off], parent, None, hidden)

    def box(self, name, f, t, mat, parent='static', part=None, off=(), **kw):
        """off: faces not drawn (the second box of a K2 casing drops up and down)."""
        if name in self._names:
            sys.exit('%s: cube %s twice' % (self.name, name))
        self._names.add(name)
        f, t = self._o(f), self._o(t)
        f, t = [min(f[i], t[i]) for i in range(3)], [max(f[i], t[i]) for i in range(3)]
        kw = dict(kw)
        kw['part'] = part or name
        kw['off'] = tuple(off)
        self.cubes.append((name, f, t, mat, parent, kw))
        return name

    def locator(self, name, pos, parent=None):
        self.locators.append((name, self._o(pos), parent))

    def anchor(self, name, pos, note, standin=None):
        """A content anchor drawn by the block entity renderer. standin: (size_xyz, mat) adds a stand-in box."""
        self.anchors.append((name, list(pos), note))
        if standin:
            g = 'standin_contents'
            if not any(b[0] == g for b in self.bones):
                self.group(g, None)
            (sx, sy, sz), mat = standin
            x, y, z = pos
            self.box('si_' + name, [x - sx / 2, y, z - sz / 2], [x + sx / 2, y + sy, z + sz / 2], mat, g)

    def shape(self, cell, box):
        """Collision/selection box in Java cell coordinates (0..16), cell 'lower' or 'upper' (the machine_top cell)."""
        self.shapes.setdefault(cell, []).append(tuple(box))

    def event(self, clip, tick, kind, what, locator=None):
        self.events.append((clip, tick, kind, what, locator))

    # -- queries -----------------------------------------------------------------------------------------------------
    def bone_names(self):
        return [b[0] for b in self.bones if b[0] != 'static' and not is_static(self, b[0])
                and not is_standin(self, b[0])]

    def frame(self, c):
        """The rigid frame a cube moves with: its own bone, or 'static' for the baked model (state groups included)."""
        return 'static' if is_static(self, c[4]) else c[4]

    def drawn(self):
        """Cubes that are drawn in the game at rest (static and bones; not stand-ins, not hidden groups)."""
        return [c for c in self.cubes if not is_standin(self, c[4]) and not is_hidden(self, c[4])]

    # -- output ------------------------------------------------------------------------------------------------------
    def write(self, path, png=None, check=True, keep_animations=True, freeze_uv=None):
        """freeze_uv: a previous .bbmodel of this machine that has been painted against. Every cube found there by
        name keeps its island (a cube that shrank keeps its old corner); new or grown cubes go into free space on the
        same sheet, which grows only if they don't fit. Use it for any geometry change after painting has started."""
        if check:
            self.check()
        cs = self.cubes
        if freeze_uv:
            offs, regions, tex_h = pack_frozen(cs, self.tex_w, freeze_uv)
        else:
            offs, regions, tex_h = pack(cs, self.tex_w)
        png = png or os.path.splitext(path)[0] + '_placeholder.png'
        draw(cs, offs, png, self.tex_w, tex_h)
        build(path, self, offs, png, (self.tex_w, tex_h), keep_animations)
        sys.stderr.write('%s: %d bones (budget %d), %d cubes (%d drawn), %d locators, sheet %dx%d\n' % (
            self.name, len(self.bone_names()), self.budget, len(cs), len(self.drawn()), len(self.locators),
            self.tex_w, tex_h))
        sys.stderr.write('  sheet rows by material: %s\n' % regions)
        return path

    def check(self):
        errors, warns = [], []
        for name, f, t, mat, parent, kw in self.cubes:
            for i in range(3):
                s = t[i] - f[i]
                if abs(s - round(s)) > 1e-6 or s < 1 - 1e-6:
                    errors.append('%s: size %s is not a whole pixel >= 1' % (name, [t[j] - f[j] for j in range(3)]))
                    break
            if mat not in MATERIALS:
                errors.append('%s: unknown material %s' % (name, mat))
        drawn = self.drawn()
        top = max(c[2][1] for c in drawn)
        if top > 32 + 1e-6:
            errors.append('rest height %.1f px over 32' % top)
        nb = len(self.bone_names())
        if nb > self.budget or nb > 12:
            errors.append('%d bones against a budget of %d' % (nb, self.budget))
        cop, flush = faces_check(drawn, [c for c in self.cubes if not is_hidden(self, c[4])], self.frame)
        errors += ['coplanar overlap (z-fight): %s' % x for x in cop]
        warns += ['flush joint (same plane, touching, no gap or step): %s' % x for x in flush]
        if top > 20 and self.shapes:
            warns += cover_check(self, drawn)
        for w in warns:
            sys.stderr.write('  WARN %s\n' % w)
        if errors:
            for e in errors:
                sys.stderr.write('  ERROR %s\n' % e)
            sys.exit('%s: %d errors' % (self.name, len(errors)))
        sys.stderr.write('%s: checks passed (%d warnings); rest height %.0f px\n' % (self.name, len(warns), top))


def ancestors(m, g):
    out = []
    parents = {b[0]: b[2] for b in m.bones}
    while g:
        out.append(g)
        g = parents.get(g)
    return out


def is_standin(m, g):
    return any(a.startswith('standin') for a in ancestors(m, g))


def is_static(m, g):
    return 'static' in ancestors(m, g)


def is_hidden(m, g):
    flags = {b[0]: b[4].get('hidden') for b in m.bones}
    return any(flags.get(a) for a in ancestors(m, g))


# ---------------------------------------------------------------------------------------------------------------------
# Checks
# ---------------------------------------------------------------------------------------------------------------------
def _faces(c):
    name, f, t, mat, parent, kw = c
    out = []
    for ax, (fname_lo, fname_hi) in enumerate([('west', 'east'), ('down', 'up'), ('north', 'south')]):
        o = [i for i in range(3) if i != ax]
        rect = (f[o[0]], t[o[0]], f[o[1]], t[o[1]])
        if fname_lo not in kw['off']:
            out.append((ax, -1, f[ax], rect))
        if fname_hi not in kw['off']:
            out.append((ax, 1, t[ax], rect))
    return out


def _hidden(face, c, occluders):
    """A face pressed against (or buried in) other opaque boxes is never seen. Tested per 1 px cell of the face (each
    cell's centre must lie inside, or on the surface of, some other opaque box), so a face covered by several boxes
    together counts as hidden too."""
    ax, sg, pl, r = face
    o = [i for i in range(3) if i != ax]
    occ = []
    for d in occluders:
        if d is c or d[3] in ('glass', 'jglass'):
            continue
        f, t = d[1], d[2]
        if (f[ax] <= pl < t[ax]) if sg > 0 else (f[ax] < pl <= t[ax]):
            occ.append((f[o[0]], t[o[0]], f[o[1]], t[o[1]]))
    if not occ:
        return False
    u = r[0]
    while u < r[1] - 1e-9:
        uc = min(u + 0.5, (u + r[1]) / 2)
        v = r[2]
        while v < r[3] - 1e-9:
            vc = min(v + 0.5, (v + r[3]) / 2)
            if not any(a0 <= uc <= a1 and b0 <= vc <= b1 for a0, a1, b0, b1 in occ):
                return False
            v += 1
        u += 1
    return True


def faces_check(cubes, occluders=None, frame=None):
    """Same-facing faces on one plane: overlapping = z-fight (error); touching along an edge between two different
    parts = a flush joint (the house rule: butt no two boxes flush; warning). Faces pressed against another box are
    ignored (never seen). Glass is exempt from the flush warning.

    `frame(cube)` names the rigid frame a cube moves with (its bone, or 'static'). For the z-fight test, a face
    counts as hidden only when boxes of its own frame cover it: a moving part that sits inside a static box at rest
    (the Cradle's bellows inside its back rail) carries its faces into the open when it moves, so a static occluder
    proves nothing about it. (The bellows' core and ribs were found sharing their front and back planes; the
    rest-pose test had counted them hidden.)"""
    occ = occluders or cubes
    frame = frame or (lambda c: None)
    cop, flush = [], []
    fs = []
    for c in cubes:
        own = [d for d in occ if frame(d) == frame(c)]
        faces = _faces(c)
        fs.append((c, [f for f in faces if not _hidden(f, c, occ)], [f for f in faces if not _hidden(f, c, own)]))

    def pairs(f1, f2):
        for (ax, sg, pl, r) in f1:
            for (ax2, sg2, pl2, r2) in f2:
                if ax != ax2 or sg != sg2 or abs(pl - pl2) > 1e-6:
                    continue
                yield min(r[1], r2[1]) - max(r[0], r2[0]), min(r[3], r2[3]) - max(r[2], r2[2])

    for i in range(len(fs)):
        ci, fi, gi = fs[i]
        for j in range(i + 1, len(fs)):
            cj, fj, gj = fs[j]
            same = frame(ci) == frame(cj)
            for ou, ov in pairs(gi if same else fi, gj if same else fj):
                if ou > 1e-6 and ov > 1e-6:
                    cop.append('%s / %s' % (ci[0], cj[0]))
            for ou, ov in pairs(fi, fj):
                if (ou > 1e-6 and abs(ov) < 1e-6) or (ov > 1e-6 and abs(ou) < 1e-6):
                    if ci[5]['part'] != cj[5]['part'] and 'glass' not in (ci[3], cj[3]):
                        flush.append('%s / %s (%s %s)' % (ci[0], cj[0], ci[3], cj[3]))
    return sorted(set(cop)), sorted(set(flush))


def cover_check(m, drawn):
    """Every drawn cube at rest, clipped to the block's x/z, should lie within some declared shape box (1 px slack)."""
    out = []
    boxes = []
    for cell, lst in m.shapes.items():
        dy = 16 if cell == 'upper' else 0
        for b in lst:
            boxes.append((b[0] - 8, b[1] + dy, b[2] - 8, b[3] - 8, b[4] + dy, b[5] - 8))
    for c in drawn:
        name, f, t = c[0], c[1], c[2]
        for lo, hi in ((-1e9, 16), (16, 1e9)):          # check the part in each cell separately
            cf = [max(f[0], -8), max(f[1], lo), max(f[2], -8)]
            ct = [min(t[0], 8), min(t[1], hi), min(t[2], 8)]
            if ct[0] <= cf[0] or ct[1] <= cf[1] or ct[2] <= cf[2]:
                continue
            ok = any(cf[0] >= b[0] - 1 and cf[1] >= b[1] - 1 and cf[2] >= b[2] - 1 and ct[0] <= b[3] + 1
                     and ct[1] <= b[4] + 1 and ct[2] <= b[5] + 1 for b in boxes)
            if not ok:
                out.append('shape: %s %s..%s (y %s..%s) not inside any declared collision box' % (
                    name, f, t, cf[1], ct[1]))
    return out


# ---------------------------------------------------------------------------------------------------------------------
# UV packing and the placeholder sheet
# ---------------------------------------------------------------------------------------------------------------------
def size(c):
    return [abs(c[2][i] - c[1][i]) for i in range(3)]


def box_uv_wh(c):
    w, h, d = [max(1, round(s)) for s in size(c)]
    return 2 * (w + d), d + h


def pack(cs, tex_w):
    """Shelf-pack every cube's box-UV island, one band of rows per material (the paint gets one area each)."""
    offsets, regions = {}, {}
    x = y = shelf = 0
    for mat in ORDER:
        items = sorted([c for c in cs if c[3] == mat], key=lambda c: (-box_uv_wh(c)[1], -box_uv_wh(c)[0], c[0]))
        if not items:
            continue
        if x:
            y += shelf
            x = shelf = 0
        top = y
        for c in items:
            w, h = box_uv_wh(c)
            if w > tex_w:
                sys.exit('%s: island %d wide on a %d sheet' % (c[0], w, tex_w))
            if x + w > tex_w:
                y += shelf
                x = shelf = 0
            offsets[c[0]] = [x, y]
            x += w
            shelf = max(shelf, h)
        regions[mat] = (top, y + shelf)
    tex_h = int(math.ceil((y + shelf) / 16.0) * 16)
    return offsets, regions, max(16, tex_h)


def pack_frozen(cs, tex_w, old_path):
    """Keep every island of a previous build; place only new or grown cubes, first fit, in free sheet space."""
    with open(old_path, encoding='utf-8') as fh:
        old = json.load(fh)
    res = old['resolution']
    if res['width'] != tex_w:
        sys.exit('freeze_uv: sheet width %d, previous %d' % (tex_w, res['width']))
    tex_h = res['height']
    prev = {}
    for e in old['elements']:
        if e.get('type', 'cube') == 'cube' and 'uv_offset' in e:
            w, h, d = [max(1, round(abs(e['to'][i] - e['from'][i]))) for i in range(3)]
            prev[e['name']] = (e['uv_offset'], (2 * (w + d), d + h))
    offsets, placed, todo = {}, [], []
    for c in cs:
        w, h = box_uv_wh(c)
        if c[0] in prev and w <= prev[c[0]][1][0] and h <= prev[c[0]][1][1]:
            offsets[c[0]] = list(prev[c[0]][0])
            placed.append((prev[c[0]][0][0], prev[c[0]][0][1], prev[c[0]][1][0], prev[c[0]][1][1]))
        else:
            todo.append(c)
    for name, (off, (w, h)) in prev.items():       # vanished cubes keep their space (the painted pixels stay)
        if name not in offsets:
            placed.append((off[0], off[1], w, h))

    def free(x, y, w, h):
        return x + w <= tex_w and not any(x < px + pw and px < x + w and y < py + ph and py < y + h
                                          for px, py, pw, ph in placed)
    moved = []
    for c in sorted(todo, key=lambda c: -box_uv_wh(c)[1]):
        w, h = box_uv_wh(c)
        y = 0
        while True:
            hit = next((x for x in range(0, tex_w - w + 1) if free(x, y, w, h)), None)
            if hit is not None:
                break
            y += 1
        offsets[c[0]] = [hit, y]
        placed.append((hit, y, w, h))
        tex_h = max(tex_h, int(math.ceil((y + h) / 16.0) * 16))
        moved.append('%s at %s' % (c[0], [hit, y]))
    sys.stderr.write('  freeze_uv: %d islands kept; new or grown: %s\n' % (len(cs) - len(todo), moved or 'none'))
    return offsets, {'frozen': old_path}, tex_h


def draw(cs, offsets, path, tex_w, tex_h):
    from PIL import Image
    img = Image.new('RGBA', (tex_w, tex_h), (0, 0, 0, 0))
    px = img.load()
    for c in cs:
        w, h, d = [max(1, round(s)) for s in size(c)]
        ox, oy = offsets[c[0]]
        hexv, alpha = MATERIALS[c[3]]
        col = tuple(int(hexv[i:i + 2], 16) for i in (0, 2, 4)) + (alpha,)
        dark = tuple(int(MATERIALS['rubber'][0][i:i + 2], 16) for i in (0, 2, 4)) + (255,)
        for rx, ry, rw, rh in [(ox + d, oy, w * 2, d), (ox, oy + d, 2 * (w + d), h)]:
            for yy in range(ry, ry + rh):
                for xx in range(rx, rx + rw):
                    if 0 <= xx < tex_w and 0 <= yy < tex_h:
                        stripe = c[3] == 'stripe' and ((xx + yy) // 2) % 2 == 1
                        px[xx, yy] = dark if stripe else col
    img.save(path)
    return path


# ---------------------------------------------------------------------------------------------------------------------
# .bbmodel writer (GeckoLib format, Blockbench 5.0 layout; UUIDs from names so clips survive a rebuild)
# ---------------------------------------------------------------------------------------------------------------------
def box_faces(f, t, off, disabled):
    w, h, d = [max(1, round(abs(t[i] - f[i]))) for i in range(3)]
    ox, oy = off
    faces = {
        'north': {'uv': [ox + d, oy + d, ox + d + w, oy + d + h], 'texture': 0},
        'east': {'uv': [ox, oy + d, ox + d, oy + d + h], 'texture': 0},
        'south': {'uv': [ox + 2 * d + w, oy + d, ox + 2 * d + 2 * w, oy + d + h], 'texture': 0},
        'west': {'uv': [ox + d + w, oy + d, ox + 2 * d + w, oy + d + h], 'texture': 0},
        'up': {'uv': [ox + d + w, oy + d, ox + d, oy], 'texture': 0},
        'down': {'uv': [ox + d + 2 * w, oy, ox + d + w, oy + d], 'texture': 0},
    }
    for k in disabled:
        faces[k]['texture'] = None
    return faces


def num(v):
    return int(v) if float(v) == int(v) else float(v)


def build(path, m, uv_offsets, png_path, res, keep_animations=True):
    groups, elements = [], []
    children = {None: []}
    gid = {}
    for name, pivot, parent, rot, flags in m.bones:
        u = nid(m.name, 'group', name)
        gid[name] = u
        groups.append({'uuid': u, 'name': name, 'origin': [num(x) for x in pivot],
                       'rotation': list(rot) if rot else [0, 0, 0], 'export': True,
                       'visibility': not flags.get('hidden'), 'mirror_uv': False, 'reset': False, 'color': 0,
                       'autouv': 0, 'shade': True, 'locked': False})
        children.setdefault(name, [])
        children.setdefault(parent, []).append(('g', name))
    for name, f, t, mat, parent, kw in m.cubes:
        u = nid(m.name, 'cube', name)
        e = {'uuid': u, 'name': name, 'origin': [num((f[i] + t[i]) / 2) for i in range(3)], 'color': 0,
             'type': 'cube', 'from': [num(v) for v in f], 'to': [num(v) for v in t], 'box_uv': True,
             'uv_offset': list(uv_offsets[name]), 'faces': box_faces(f, t, uv_offsets[name], kw['off']),
             'rescale': False, 'locked': False, 'light_emission': 0, 'render_order': 'default',
             'allow_mirror_modeling': True, 'autouv': 0, 'visibility': True}
        elements.append(e)
        children.setdefault(parent, []).append(('e', u))
    for name, pos, parent in m.locators:
        u = nid(m.name, 'locator', name)
        elements.append({'uuid': u, 'name': name, 'type': 'locator', 'position': [num(v) for v in pos],
                         'rotation': [0, 0, 0], 'ignore_inherited_scale': False, 'visibility': True,
                         'locked': False})
        children.setdefault(parent, []).append(('e', u))

    def tree(name):
        out = []
        for kind, ref in children.get(name, []):
            if kind == 'e':
                out.append(ref)
            else:
                out.append({'uuid': gid[ref], 'isOpen': False, 'children': tree(ref)})
        return out

    with open(png_path, 'rb') as fh:
        src = 'data:image/png;base64,' + base64.b64encode(fh.read()).decode()
    tex = {'path': '', 'name': os.path.basename(png_path), 'folder': '', 'namespace': '', 'id': '0',
           'width': res[0], 'height': res[1], 'uv_width': res[0], 'uv_height': res[1], 'particle': False,
           'use_as_default': False, 'layers_enabled': False, 'sync_to_project': '', 'render_mode': 'default',
           'render_sides': 'auto', 'frame_time': 1, 'frame_order_type': 'loop', 'frame_order': '',
           'frame_interpolate': False, 'visible': True, 'internal': True, 'saved': False,
           'uuid': nid(m.name, 'texture', 'sheet'), 'relative_path': '', 'source': src}
    anims = []
    if keep_animations and os.path.exists(path):
        with open(path, encoding='utf-8') as fh:
            anims = json.load(fh).get('animations', [])
    model = {'meta': {'format_version': '5.0', 'model_format': 'geckolib_model', 'box_uv': True},
             'name': m.name, 'model_identifier': m.name, 'visible_box': [2, 2.5, 1],
             'resolution': {'width': res[0], 'height': res[1]}, 'elements': elements, 'groups': groups,
             'outliner': tree(None), 'textures': [tex], 'animations': anims, 'variable_placeholders': '',
             'variable_placeholder_buttons': [], 'timeline_setups': [], 'unhandled_root_fields': {}}
    with open(path, 'w', encoding='utf-8') as fh:
        json.dump(model, fh, indent=1)
    return gid


# ---------------------------------------------------------------------------------------------------------------------
# Faces: place a part on a face of the body. u runs along the face (x on north/south, z on east/west), v is y,
# d is the distance out from the face's plane (positive = outward, negative = into the body).
# ---------------------------------------------------------------------------------------------------------------------
def on_face(face, plane, u0, u1, v0, v1, d0, d1):
    if face == 'north':
        return [u0, v0, plane - d1], [u1, v1, plane - d0]
    if face == 'south':
        return [u0, v0, plane + d0], [u1, v1, plane + d1]
    if face == 'west':
        return [plane - d1, v0, u0], [plane - d0, v1, u1]
    if face == 'east':
        return [plane + d0, v0, u0], [plane + d1, v1, u1]
    raise ValueError(face)


def face_point(face, plane, u, v, d):
    f, t = on_face(face, plane, u, u, v, v, d, d)
    return f


# ---------------------------------------------------------------------------------------------------------------------
# K1  feet and plinth
# ---------------------------------------------------------------------------------------------------------------------
def plinth(m, x0, x1, z0, z1, y=2, inset=1, prefix=''):
    """Steel plinth (x0..x1, y..y+1, z0..z1) on four rubber feet 3x2x3 under its corners, inset `inset` px from the
    plinth's edges so the plinth overhangs them: a real shadow gap at the base and no flush corner. Baked."""
    p = prefix
    m.box(p + 'plinth', [x0, y, z0], [x1, y + 1, z1], 'dsteel')
    i = inset
    for tag, fx, fz in [('fl', x0 + i, z0 + i), ('fr', x1 - i - 3, z0 + i), ('bl', x0 + i, z1 - i - 3),
                        ('br', x1 - i - 3, z1 - i - 3)]:
        m.box(p + 'foot_' + tag, [fx, y - 2, fz], [fx + 3, y, fz + 3], 'rubber')


# ---------------------------------------------------------------------------------------------------------------------
# K2  rounded casing with rimmed panels
# ---------------------------------------------------------------------------------------------------------------------
def casing(m, name, x0, x1, z0, z1, y0, y1, step=1, mat='enamel', parent='static', rims=(), rim_mat=None,
           top_rim=False):
    """Two crossed boxes making an appliance with stepped vertical corners: A = (x0+step..x1-step, z0..z1),
    B = (x0..x1, z0+step..z1-step) with no top or bottom faces (they would z-fight A's). `rims` lists faces
    ('north', 'south', 'east', 'west') that get a raised 1 px rim, 1 px proud, round the face's border: the box face
    inside is the panel, set 1 px back from the rim. Returns the outer planes {face: plane of the panel}."""
    a = m.box(name + '_a', [x0 + step, y0, z0], [x1 - step, y1, z1], mat, parent, part=name)
    b = m.box(name + '_b', [x0, y0, z0 + step], [x1, y1, z1 - step], mat, parent, part=name, off=('up', 'down'))
    planes = {'north': z0, 'south': z1, 'west': x0, 'east': x1}
    spans = {'north': (x0 + step, x1 - step), 'south': (x0 + step, x1 - step), 'west': (z0 + step, z1 - step),
             'east': (z0 + step, z1 - step)}
    for face in rims:
        rim(m, '%s_rim_%s' % (name, face[0]), face, planes[face], spans[face][0], spans[face][1], y0, y1,
            rim_mat or mat, parent, part=name)
    return planes


def rim(m, name, face, plane, u0, u1, v0, v1, mat='enamel', parent='static', width=1, depth=1, sides='tblr',
        part=None):
    """A raised frame of four strips round a rectangle on a face (the strips' outer edges are the rectangle's).
    The frame is one part, so its own corner joints are not flagged."""
    w, d = width, depth
    name_part = part or name
    if 't' in sides:
        m.box(name + '_t', *on_face(face, plane, u0, u1, v1 - w, v1, 0, d), mat=mat, parent=parent, part=name_part)
    if 'b' in sides:
        m.box(name + '_b', *on_face(face, plane, u0, u1, v0, v0 + w, 0, d), mat=mat, parent=parent, part=name_part)
    lo = v0 + (w if 'b' in sides else 0)
    hi = v1 - (w if 't' in sides else 0)
    if 'l' in sides:
        m.box(name + '_l', *on_face(face, plane, u0, u0 + w, lo, hi, 0, d), mat=mat, parent=parent, part=name_part)
    if 'r' in sides:
        m.box(name + '_r', *on_face(face, plane, u1 - w, u1, lo, hi, 0, d), mat=mat, parent=parent, part=name_part)


# ---------------------------------------------------------------------------------------------------------------------
# K3  the Noggin Lamp
# ---------------------------------------------------------------------------------------------------------------------
LAMP_STATES = {
    # state: (lens texture, how it is shown)
    'off':   ('lamp_off', 'rubber r0/r1: the lens dark, no glow'),
    'ready': ('lamp_ready', 'green G2 core, G1 rim, steady, full-bright'),
    'wait':  ('lamp_wait', 'amber A2 core, A1 rim, steady, full-bright'),
    'work':  ('lamp_work', 'amber, .mcmeta 20 ticks lit (A2/A3 core) / 20 ticks at A1: a 0.5 Hz pulse'),
    'low':   ('lamp_low', 'amber, .mcmeta 20 ticks at A1 / 20 ticks at A0: a dim pulse (the brown-out)'),
    'fault': ('lamp_fault', 'red R2 core, R1 rim, steady, never blinks (M15), full-bright'),
}


def lamp(m, face, plane, u0, v0, prefix='', parent='static', lens_mat='lens'):
    """Brass bezel 4x4x1 on the face (u0..u0+4, v0..v0+4), lens 2x2x1 proud of the bezel's centre. Baked. The lens is
    its own element in the sub-group `state_lamp` of static, so the game code emits it as the lamp part of the
    multipart blockstate (one tiny model per `lamp` value, all sharing the element, each with its lens texture).
    Locator `lamp` at the lens's face centre (the glow and any lamp particle)."""
    g = prefix + 'state_lamp'
    if not any(b[0] == g for b in m.bones):
        m.group(g, parent)
    m.box(prefix + 'lamp_bezel', *on_face(face, plane, u0, u0 + 4, v0, v0 + 4, 0, 1), mat='brass', parent=parent)
    m.box(prefix + 'lamp_lens', *on_face(face, plane, u0 + 1, u0 + 3, v0 + 1, v0 + 3, 1, 2), mat=lens_mat, parent=g)
    m.locator(prefix + 'lamp', face_point(face, plane, u0 + 2, v0 + 2, 2), None)


# ---------------------------------------------------------------------------------------------------------------------
# K4  gauge
# ---------------------------------------------------------------------------------------------------------------------
def gauge(m, face, plane, u0, v0, size=6, prefix='gauge', parent='static'):
    """A brass bezel frame (four 1 px strips, 2 px deep) round a teal face set 1 px back inside it (the face is itself
    1 px proud of the body, so it never shares the body's plane). 6x6 standard (face 4x4), or size=4 (face 2x2).
    The face element sits in `state_<prefix>` under static: the game code gives it the face texture for the level
    blockstate (five textures, level 0-4; the needle is painted)."""
    s = size
    rim(m, prefix + '_bezel', face, plane, u0, u0 + s, v0, v0 + s, 'brass', parent, depth=2)
    g = 'state_' + prefix
    m.group(g, parent)
    m.box(prefix + '_face', *on_face(face, plane, u0 + 1, u0 + s - 1, v0 + 1, v0 + s - 1, 0, 1), mat='gauge', parent=g)


# ---------------------------------------------------------------------------------------------------------------------
# K5  hatch (hinged) and drawer (sliding): one bone each; open while a player has the screen open
# ---------------------------------------------------------------------------------------------------------------------
def hatch(m, face, plane, u0, v0, w=6, h=6, bone='hatch', hinge='left', parent=None):
    """Enamel hatch w x h x 1 on the face, hinged on its left edge (seen from outside) about the vertical; a brass
    knuckle 2x(h-2)x2 at the hinge is baked (static), the panel and a 2x2x1 brass pull are the bone.
    Opens 100 degrees outward. Returns (bone, axis channel, open value) for hatch_clips."""
    lu = u0 if hinge == 'left' else u0 + w
    piv = face_point(face, plane, lu, v0, 1)
    m.bone(bone, piv, parent)
    m.box(bone + '_panel', *on_face(face, plane, u0, u0 + w, v0, v0 + h, 0, 1), mat='enamel', parent=bone)
    pu = u0 + w - 3 if hinge == 'left' else u0 + 1
    m.box(bone + '_pull', *on_face(face, plane, pu, pu + 2, v0 + h // 2 - 1, v0 + h // 2 + 1, 1, 2),
          mat='brass', parent=bone)
    ku = (lu - 2, lu) if hinge == 'left' else (lu, lu + 2)
    m.box(bone + '_knuckle', *on_face(face, plane, ku[0], ku[1], v0 + 1, v0 + h - 1, 0, 2), mat='brass')
    sign = {'north': 1, 'south': -1, 'west': -1, 'east': 1}[face] * (1 if hinge == 'left' else -1)
    return bone, 'rotation', [0, 100 * sign, 0]


def drawer(m, face, plane, u0, v0, w=6, h=3, depth=5, bone='drawer', recess=1, travel=3, parent=None):
    """A drawer: box w x h x depth whose front sits `recess` px behind the face plane at rest (a step, never flush with
    the frame round it), and a 2x1x1 brass pull 1 px proud of the drawer front. Slides out `travel` px.
    Returns (bone, 'position', open offset) for drawer_clips."""
    piv = face_point(face, plane, u0 + w / 2.0, v0, -recess)
    m.bone(bone, piv, parent)
    m.box(bone + '_box', *on_face(face, plane, u0, u0 + w, v0, v0 + h, -recess - depth, -recess), mat='enamel',
          parent=bone)
    pu = u0 + (w - 2) // 2
    pv = v0 + (h - 1) // 2
    m.box(bone + '_pull', *on_face(face, plane, pu, pu + 2, pv, pv + 1, -recess, -recess + 1), mat='brass',
          parent=bone)
    out = {'north': [0, 0, -travel], 'south': [0, 0, travel], 'west': [-travel, 0, 0], 'east': [travel, 0, 0]}[face]
    return bone, 'position', out


# ---------------------------------------------------------------------------------------------------------------------
# K6b  side intake port
# ---------------------------------------------------------------------------------------------------------------------
def side_port(m, face, plane, uc, v0, depth=2, prefix='port'):
    """Brass frame 6x6 of four strips (1 px wide, `depth` px out from the body's face) round a 4x4 rubber mouth set
    1 px in from the frame's outer face (1 px thick; with depth=1 the mouth sits 1 px into the body, behind the
    frame). uc is the centre along the face; v0 the frame's bottom. Baked. Where a side
    hopper's spout lands (y 4-8) and pipes connect; every side works, this one is drawn."""
    rim(m, prefix + '_frame', face, plane, uc - 3, uc + 3, v0, v0 + 6, 'brass', depth=depth)
    m.box(prefix + '_mouth', *on_face(face, plane, uc - 2, uc + 2, v0 + 1, v0 + 5, depth - 2, depth - 1), mat='rubber')


# ---------------------------------------------------------------------------------------------------------------------
# K7  maker's plate
# ---------------------------------------------------------------------------------------------------------------------
def makers_plate(m, face, plane, u0, v0, w=4, h=2, name='makers_plate', parent='static'):
    """Brass plate w x h, 1 px proud: the N&S monogram is texture only. Front, low."""
    m.box(name, *on_face(face, plane, u0, u0 + w, v0, v0 + h, 0, 1), mat='brass', parent=parent)


# ---------------------------------------------------------------------------------------------------------------------
# K8  stripes: only on parts that move into space, only on faces at least 4 px across
# ---------------------------------------------------------------------------------------------------------------------
def stripe_box(m, name, f, t, parent='static', part=None):
    """A box painted with 45-degree stripes (w2 2 px / r1 2 px). Refused if the band is under 4 px tall: a 2-3 px
    moving edge takes the narrow form (`yellow` with one dark band painted), K8."""
    if t[1] - f[1] < 4:
        sys.exit('%s: a striped band must be at least 4 px tall (use yellow, the narrow form)' % name)
    m.box(name, f, t, 'stripe', parent, part=part)


# ---------------------------------------------------------------------------------------------------------------------
# K9  collection flag
# ---------------------------------------------------------------------------------------------------------------------
def flag(m, x_face, y, z_back, side='east', bone='flag'):
    """The mailbox flag on a side: a steel arm 1x2x6 lying flat against the side, hinged at its back end (pivot on
    the side's face at (x_face, y+1, z_back)); a yellow plate 2x4x3 1 px from its front end, 1 px outside the arm and 1 px
    above and below it (a step all round, never flush). Up = the arm vertical, the plate pointing forward.
    Returns (bone, 'rotation', up value)."""
    sx = 1 if side == 'east' else -1
    xa = (x_face, x_face + sx)
    xp = (x_face + sx, x_face + 3 * sx)
    m.bone(bone, [x_face, y + 1, z_back])
    m.box(bone + '_arm', [min(xa), y, z_back - 6], [max(xa), y + 2, z_back], 'steel', bone)
    m.box(bone + '_plate', [min(xp), y - 1, z_back - 5], [max(xp), y + 3, z_back - 2], 'yellow', bone)
    return bone, 'rotation', [90, 0, 0]


# ---------------------------------------------------------------------------------------------------------------------
# K10  copper terminal
# ---------------------------------------------------------------------------------------------------------------------
def terminal(m, x, z, y, name, parent='static', post=2):
    """Rubber collar 4x1x4, copper post 2 x post x 2, brass cap 4x1x4 (x, z = the post's centre; y = the collar's
    bottom). Where FE visibly goes in or out. Returns the cap's top centre (a spark point)."""
    m.box(name + '_collar', [x - 2, y, z - 2], [x + 2, y + 1, z + 2], 'rubber', parent, part=name)
    m.box(name + '_post', [x - 1, y + 1, z - 1], [x + 1, y + 1 + post, z + 1], 'copper', parent, part=name)
    m.box(name + '_cap', [x - 2, y + 1 + post, z - 2], [x + 2, y + 2 + post, z + 2], 'brass', parent, part=name)
    return [x, y + 2 + post, z]


# ---------------------------------------------------------------------------------------------------------------------
# K12  lab bench
# ---------------------------------------------------------------------------------------------------------------------
def bench(m, with_lamp=True, drawer_bone='drawer'):
    """The black-topped lab bench (16 x 13 x 16): rubber kick inset under enamel cabinet ends, an open shelf (steel
    floor and rubber back both 1 px in from the ends: the line's most reliable dark), a band with the K5 drawer and the K3 lamp, a rubber worktop
    overhanging 1 px all round. Instruments stand on it at y 13. Front planes step back: cabinet ends z -7, lamp bezel
    -6, band -5, drawer front -4 (so nothing meets flush). Returns the drawer's clip spec."""
    m.box('bench_kick', [-6, 0, -6], [6, 1, 6], 'rubber')
    m.box('bench_end_l', [-7, 1, -7], [-5, 11, 7], 'enamel')
    m.box('bench_end_r', [5, 1, -7], [7, 11, 7], 'enamel')
    m.box('bench_back', [-5, 1, 4], [5, 11, 5], 'rubber')
    m.box('bench_shelf', [-5, 1, -5], [5, 2, 4], 'dsteel')
    m.box('bench_band', [-5, 7, -5], [5, 8, 4], 'enamel')
    m.box('bench_worktop', [-8, 11, -8], [8, 13, 8], 'rubber')
    spec = drawer(m, 'north', -5, -5, 8, w=6, h=3, depth=5, bone=drawer_bone, recess=1, travel=3)
    if with_lamp:
        # set into the band's right part: bezel z -6..-5, lens -7..-6
        g = 'state_lamp'
        m.group(g, 'static')
        m.box('lamp_bezel', [1, 7, -6], [5, 11, -5], 'brass')
        m.box('lamp_lens', [2, 8, -7], [4, 10, -6], 'lens', g)
        m.locator('lamp', [3, 9, -7], None)
    return spec


# ---------------------------------------------------------------------------------------------------------------------
# The top cell (2.1): shapes in Java cell coordinates
# ---------------------------------------------------------------------------------------------------------------------
def shapes_java(m):
    """Return {'lower': [...], 'upper': [...]} as VoxelShape box() calls for the game code."""
    out = {}
    for cell, lst in m.shapes.items():
        out[cell] = ['box(%s)' % ','.join(str(num(v)) for v in b) for b in lst]
    return out


# ---------------------------------------------------------------------------------------------------------------------
# Clips
# ---------------------------------------------------------------------------------------------------------------------
def k(t, v, e=None, a=None, interp='linear'):
    """A key: tick, [x,y,z], easing ARRIVING at this key (GeckoLib rule), easing args, interpolation."""
    return (t, list(v), e, a, interp)


def R(x=0, y=0, z=0):
    return [x, y, z]


def scale3(v, s):
    return [c * s for c in v]


def hatch_clips(spec):
    """K5 hinged hatch: open 5 ticks (wind-up 1 tick, easeOutCubic to a 5 % overshoot, settle), shut 6 ticks
    (easeInQuad into contact on 4, a small bounce, settle). Sounds at the start and the contact."""
    bone, ch, v = spec
    over = scale3(v, 1.06)
    wind = scale3(v, -0.04)
    op = {'length': 5, 'loop': 'hold',
          'bones': {bone: {ch: [k(0, R()), k(1, wind, 'easeOutQuad'), k(4, over, 'easeOutCubic'),
                                k(5, v, 'easeInOutSine')]}},
          'effects': [(0, 'sound', 'headinajar:machine.hatch_open|0.5|1.0', None)]}
    sh = {'length': 6, 'loop': 'hold',
          'bones': {bone: {ch: [k(0, v), k(4, R(), 'easeInQuad'), k(5, scale3(v, 0.05), 'easeOutQuad'),
                                k(6, R(), 'easeInQuad')]}},
          'effects': [(4, 'sound', 'headinajar:machine.hatch_shut|0.5|1.0', None)]}
    return op, sh


def drawer_clips(spec):
    """K5 drawer: open 5 ticks (0.25 px wind-up inward, easeOutCubic out to +0.5 px past, settle back), close 6 ticks
    (easeInQuad into contact on 4, a 0.5 px bounce out, back in on 6)."""
    bone, ch, v = spec
    n = max(abs(c) for c in v)
    unit = [c / n for c in v]
    op = {'length': 5, 'loop': 'hold',
          'bones': {bone: {ch: [k(0, R()), k(1, scale3(unit, -0.25), 'easeOutQuad'),
                                k(4, [a + b for a, b in zip(v, scale3(unit, 0.5))], 'easeOutCubic'),
                                k(5, v, 'easeInOutSine')]}},
          'effects': [(0, 'sound', 'headinajar:machine.drawer_out|0.5|1.0', None),
                      (4, 'sound', 'headinajar:machine.drawer_stop|0.4|1.1', None)]}
    sh = {'length': 6, 'loop': 'hold',
          'bones': {bone: {ch: [k(0, v), k(4, R(), 'easeInQuad'), k(5, scale3(unit, 0.5), 'easeOutQuad'),
                                k(6, R(), 'easeInQuad')]}},
          'effects': [(4, 'sound', 'headinajar:machine.drawer_shut|0.5|1.0', None)]}
    return op, sh


def flag_clips(spec):
    """K9: flag_up 6 ticks (a 6-degree dip the wrong way, then easeOutBack up past vertical and back), flag_down
    6 ticks (a 4-degree lift, easeInQuad down into the arm's rest on 4, a 4-degree bounce, rest). Both hold."""
    bone, ch, v = spec
    s = 1 if sum(v) > 0 else -1
    up = {'length': 6, 'loop': 'hold',
          'bones': {bone: {ch: [k(0, R()), k(1, R(x=-6 * s), 'easeOutQuad'), k(6, v, 'easeOutBack', [1])]}},
          'effects': [(2, 'sound', 'headinajar:machine.flag_up|0.6|1.0', None)]}
    down = {'length': 6, 'loop': 'hold',
            'bones': {bone: {ch: [k(0, v), k(1, R(x=v[0] + 4 * s), 'easeOutQuad'), k(4, R(), 'easeInQuad'),
                                  k(5, R(x=4 * s), 'easeOutQuad'), k(6, R(), 'easeInQuad')]}},
            'effects': [(4, 'sound', 'headinajar:machine.flag_down|0.5|1.0', None)]}
    return up, down


def refuse_clip(bone, channel='rotation', axis=1, amount=3.0, sound=True):
    """The house refusal (2.9): the lead part rattles +a, -a, +a, 0 at 2, 4, 6, 8 (easeInOutSine), 8 ticks.
    amount is degrees (rotation) or px (position: use 0.5). The refusal sound comes from the server (Refusals), so
    the clip carries only a small rattle key at 0."""
    def v(s):
        out = [0, 0, 0]
        out[axis] = s * amount
        return out
    clip = {'length': 8, 'loop': 'once',
            'bones': {bone: {channel: [k(0, R()), k(2, v(1), 'easeInOutSine'), k(4, v(-1), 'easeInOutSine'),
                                       k(6, v(1), 'easeInOutSine'), k(8, R(), 'easeInOutSine')]}},
            'effects': [(0, 'sound', 'headinajar:machine.rattle|0.4|1.0', None)] if sound else []}
    return clip


# ---------------------------------------------------------------------------------------------------------------------
# Clip writer (from chair_anim.py): replaces the bbmodel's "animations" array
# ---------------------------------------------------------------------------------------------------------------------
def _base(name, arg):
    n = name.lower()
    fam = n.replace('easeinout', '').replace('easein', '').replace('easeout', '')
    if fam == 'sine':
        f = lambda t: 1 - math.cos(t * math.pi / 2)
    elif fam == 'quad':
        f = lambda t: t * t
    elif fam == 'cubic':
        f = lambda t: t ** 3
    elif fam == 'quart':
        f = lambda t: t ** 4
    elif fam == 'expo':
        f = lambda t: 2 ** (10 * (t - 1))
    elif fam == 'back':
        n2 = 1.70158 if arg is None else arg * 1.70158
        f = lambda t: t * t * ((n2 + 1) * t - n2)
    elif fam == 'elastic':
        n2 = 1 if arg is None else arg
        f = lambda t: 1 - math.cos(t * math.pi / 2) ** 3 * math.cos(t * n2 * math.pi)
    elif fam == 'bounce':
        b = 0.5 if arg is None else arg
        f = lambda x: min(121 / 16 * x * x, 121 / 4 * b * (x - 6 / 11) ** 2 + 1 - b,
                          121 * b * b * (x - 9 / 11) ** 2 + 1 - b * b,
                          484 * b ** 3 * (x - 10.5 / 11) ** 2 + 1 - b ** 3)
    else:
        raise ValueError(name)
    if n.startswith('easeinout'):
        return lambda t: f(t * 2) / 2 if t < 0.5 else 1 - f((1 - t) * 2) / 2
    if n.startswith('easeout'):
        return lambda t: 1 - f(1 - t)
    return f


def ease(name, args, t):
    if not name or name.lower() in ('linear', 'none'):
        return t
    return _base(name, args[0] if args else None)(t)


def _kf(channel, t, v, e, a, interp):
    kf = {'channel': channel, 'data_points': [{'x': v[0], 'y': v[1], 'z': v[2]}], 'uuid': str(uuidlib.uuid4()),
          'time': round(t / T, 4), 'color': -1, 'interpolation': interp}
    if e and interp == 'linear' and e.lower() != 'linear':
        kf['easing'] = e
        if a is not None:
            kf['easingArgs'] = a
    return kf


def bake(keys):
    """Per-tick linear keys reproducing GeckoLib's eased segments (review copies only)."""
    out = []
    for i, (t, v, e, a, interp) in enumerate(keys):
        if i == 0:
            out.append((t, v, None, None, interp))
            continue
        t0, v0 = keys[i - 1][0], keys[i - 1][1]
        if keys[i - 1][4] == 'step' or interp == 'step':
            out.append((t, v, None, None, interp))
            continue
        for tt in range(int(t0) + 1, int(t) + 1):
            f = ease(e, a, (tt - t0) / float(t - t0))
            out.append((tt, [v0[j] + (v[j] - v0[j]) * f for j in range(3)], None, None, 'linear'))
    return out


def clips_json(model, clips, code_channels=(), baked=False):
    groups = {g['name']: g['uuid'] for g in model['groups']}
    anims = []
    for name, clip in clips:
        animators = {}
        for bone, chans in clip['bones'].items():
            if bone not in groups:
                sys.exit('%s: no bone %s' % (name, bone))
            kfs = []
            for channel, keys in chans.items():
                if (bone, channel) in code_channels and not clip.get('review'):
                    sys.exit('%s: %s.%s is driven by code and may not be keyed (2.7 rule 1)' % (name, bone, channel))
                keys = sorted(keys, key=lambda x: x[0])
                times = [x[0] for x in keys]
                if len(set(times)) != len(times):
                    sys.exit('%s/%s/%s: two keys on one tick' % (name, bone, channel))
                if baked:
                    keys = bake(keys)
                for (t, v, e, a, interp) in keys:
                    if t > clip['length'] + 1e-9:
                        sys.exit('%s/%s: key past the end' % (name, bone))
                    kfs.append(_kf(channel, t, v, e, a, interp))
            animators[groups[bone]] = {'name': bone, 'type': 'bone', 'keyframes': kfs}
        effects, seen = [], set()
        for (t, channel, effect, locator) in clip.get('effects', []):
            if t in seen:
                sys.exit('%s: two effects on tick %s' % (name, t))
            if t > clip['length']:
                sys.exit('%s: effect past the end' % name)
            seen.add(t)
            if channel == 'timeline':
                dp = {'script': effect}
            else:
                dp = {'effect': effect, 'file': ''}
                if locator:
                    dp['locator'] = locator
            effects.append({'channel': channel, 'data_points': [dp], 'uuid': str(uuidlib.uuid4()),
                            'time': round(t / T, 4), 'color': -1, 'interpolation': 'linear'})
        if effects:
            animators['effects'] = {'name': 'Effects', 'type': 'effect', 'keyframes': effects}
        anims.append({'uuid': str(uuidlib.uuid5(NS, model['name'] + '/anim/' + name)), 'name': name,
                      'loop': clip['loop'], 'override': False, 'length': clip['length'] / T, 'snapping': 20,
                      'selected': False, 'animators': animators})
    return anims


def write_clips(path, clips, code_channels=(), review=None, review_clips=()):
    """Write `clips` [(name, clip)] into the bbmodel at path (replacing its animations). With review=PATH, also write a
    review copy with every eased segment baked per tick and the review-only clips (code motion simulated as keys,
    e.g. the Pile's compression) added, for renders and sample_pose."""
    with open(path, encoding='utf-8') as f:
        model = json.load(f)
    model['animations'] = clips_json(model, clips, code_channels)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(model, f, indent=1)
    if review:
        prev = copy.deepcopy(model)
        prev['animations'] = clips_json(model, list(clips) + list(review_clips), code_channels, baked=True)
        with open(review, 'w', encoding='utf-8') as f:
            json.dump(prev, f, indent=1)
    return len(clips)


def effects_table(clips):
    """Every sound and particle moment, tick by tick, for the notes."""
    rows = []
    for name, clip in clips:
        for (t, ch, eff, loc) in sorted(clip.get('effects', [])):
            rows.append('| %s | %d | %s | %s | %s |' % (name, t, ch, eff, loc or ''))
    return '\n'.join(rows)

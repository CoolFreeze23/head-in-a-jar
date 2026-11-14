"""Dents for the animated mecha frames: where a frame's sheet buckles as it takes damage on one side.

A frame that has lost a quarter, a half and three quarters of its health shows dent stage 1, 2 and 3 on the side it
was hit from most (front, right, back or left of the machine). This script places the dents, once, for every frame:

- the faces that look out to each side are found from the frame's model (the cube faces' normals with the bones' rest
  rotations; the machine faces north, its left is -x);
- only pixels that belong to that side alone are used (a UV area shared with a face looking elsewhere is left alone),
  and only metal: enamel, steel, brass, warning paint, iron, and the rubber-black paint on broad panels (glass, lamps,
  bone, flesh and copper are never dented);
- each dent is a small placed stamp, lit as everything is from the top left: the upper-left inner wall of the hollow a
  step or two darker in that pixel's own ramp, the lower-right wall a step lighter. Stage 1 puts a few small dents on
  the side's broadest panels; stage 2 adds more and larger ones and a scrape through the paint; stage 3 adds more again
  and a crease across the broadest panel. Placement follows a fixed low-discrepancy sequence over each face, so the
  result is the same every run and nothing is per-pixel noise.

Every dented pixel is a palette colour (a step or a half step of the ramp the pixel already uses). The output is one
PNG per frame, `assets/headinajar/mecha/dents/<frame>.png`: the sheet's width, twelve sheets tall, tile (side * 3 +
stage - 1) holding only the changed pixels of that side at that stage (stages include the ones before). The game lays
the tiles it needs over the frame's own sheet when it first draws a machine in that state.

    python art/mecha/dents.py                     # every frame
    python art/mecha/dents.py --preview OUT_DIR   # also, per frame, the four sides at each stage, flat-projected
"""
import json
import math
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'headinajar')
FRAMES = ['hopper', 'bulwark', 'colossus', 'executive', 'wheelhouse', 'chimera', 'skitter', 'kite']
SIDES = ['front', 'right', 'back', 'left']
# the machine faces north (-z); its right is +x
SIDE_OF_NORMAL = {(0, -1): 0, (1, 0): 1, (0, 1): 2, (-1, 0): 3}
METAL = {'enamel', 'steel', 'brass', 'warning', 'iron'}
PAINT = {'enamel', 'warning', 'rubber'}
PANEL = 6          # rubber-black only on faces at least this wide and tall (panels, not hoses)

# Shades a mark gives a pixel, in that pixel's own ramp: X three steps darker, D two, d one, c half a step (the floor of
# a shallow hollow), l one step lighter, L two. Light comes from the top left (as on everything in the mod): a dent's
# upper-left inner wall faces away from it and is in deep shadow, its lower-right wall faces it and is lit, and the lip
# on its upper-left rim catches the light.
NICK = 'nick'
# dent sizes (w, h) from small to large; a mark is the largest that fits its panel with a pixel of the panel round it
SIZES = [(3, 3), (4, 3), (4, 4), (5, 4), (6, 5), (7, 5), (8, 6), (10, 7)]


def dent(w, h):
    """An oval hollow w x h: cells (dx, dy, shade), its upper-left lip one pixel outside it."""
    out = []
    cx, cy = w / 2.0, h / 2.0
    for y in range(-1, h + 1):
        for x in range(-1, w + 1):
            dx, dy = (x + 0.5 - cx) / cx, (y + 0.5 - cy) / cy
            r = math.hypot(dx, dy)
            d = (dx + dy) / math.sqrt(2)          # toward the light (top left) is negative
            if r > 1.0:
                if r <= 1.4 and d < -0.75 and 0 <= x < w + 1 and 0 <= y < h + 1 and w >= 5:
                    out.append((x, y, 'l'))
                continue
            if x < 0 or y < 0 or x >= w or y >= h:
                continue
            if r > 0.35:
                if d < -0.1:
                    out.append((x, y, 'X' if (r > 0.6 and d < -0.45 and w >= 4) else 'D'))
                elif d > 0.25:
                    out.append((x, y, 'L' if r > 0.6 else 'l'))
                else:
                    out.append((x, y, 'd'))
            else:
                out.append((x, y, 'd' if d < 0.15 else 'c'))
    return out


def palette():
    p = json.load(open(os.path.join(ROOT, 'art', 'palette.json'), encoding='utf-8'))
    ramps = {k: v[p['default']] for k, v in p['house'].items()}
    ramps.update({k: v['ramp'] for k, v in p['shared'].items()})
    rgb = {k: [tuple(int(h[j:j + 2], 16) for j in (0, 2, 4)) for h in r] for k, r in ramps.items()}
    index = {}
    for k, r in rgb.items():
        for i, c in enumerate(r):
            index[c] = (k, float(i))
    for k, r in rgb.items():
        for i in range(len(r) - 1):
            mid = tuple((a + b) // 2 for a, b in zip(r[i], r[i + 1]))
            index.setdefault(mid, (k, i + 0.5))
    return rgb, index


RAMPS, INDEX = palette()


def shade(c, kind):
    """The colour a stamp cell turns pixel c into, in c's own ramp."""
    mat, step = INDEX[c]
    r = RAMPS[mat]
    top = len(r) - 1
    if kind == 's':
        # a scrape: bare steel through paint, a bright scuff on bare metal
        if mat in PAINT:
            return RAMPS['steel'][3]
        return r[min(top, int(math.ceil(step)) + 2)]
    if kind == 'l':
        return r[min(top, int(math.floor(step)) + 1)]
    if kind == 'L':
        return r[min(top, int(math.floor(step)) + 2)]
    lo = int(math.ceil(step))
    # on the pale paints a step is a small change of value: the shadow goes one step further to read as a hollow
    if mat in ('enamel', 'bone') and kind in ('d', 'D', 'X') and lo >= 3:
        lo -= 1
    n = {'d': 1, 'D': 2, 'X': 3}.get(kind)
    if n is not None:
        if lo - n >= 0:
            return r[lo - n]
        # past the darkest step of a metal's ramp the hollow goes on into the rubber-black (the paint's own shadow)
        if mat in METAL:
            dark = RAMPS['rubber']
            return dark[max(0, len(dark) + (lo - n))]
        return r[0]
    if kind == 'c':
        a, b = r[max(0, lo - 1)], r[lo]
        return tuple((x + y) // 2 for x, y in zip(a, b))
    raise ValueError(kind)


# ---- the model: faces looking out to each side ------------------------------------------------------------------
def rot(deg):
    """Blockbench's rotation (degrees, applied z, y, x: the matrix Rz Ry Rx) as a 3x3 matrix."""
    x, y, z = (math.radians(a) for a in deg)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return mul(rz, mul(ry, rx))


def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def apply(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


NORMALS = {'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (1, 0, 0), 'west': (-1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}


def faces(frame):
    """Every cube face of the frame (stand-ins left out): (side or -1, uv rect in sheet px, width, height, element)."""
    model = json.load(open(os.path.join(HERE, frame, frame + '.bbmodel'), encoding='utf-8'))
    groups = {g['uuid']: g for g in model['groups']}
    elements = {e['uuid']: e for e in model['elements']}
    out = []

    def walk(nodes, m, skip):
        for n in nodes:
            if isinstance(n, dict):
                g = groups.get(n['uuid'], {})
                name = g.get('name', '')
                walk(n.get('children', []), mul(m, rot(g.get('rotation', [0, 0, 0]))),
                     skip or name.startswith('standin') or name.startswith('lamp_'))
            elif n in elements and not skip:
                e = elements[n]
                if e.get('type', 'cube') != 'cube' or e['name'].startswith('standin'):
                    continue
                em = mul(m, rot(e.get('rotation', [0, 0, 0])))
                size = [abs(e['to'][i] - e['from'][i]) for i in range(3)]
                for fname, f in e.get('faces', {}).items():
                    if f.get('texture') is None or 'uv' not in f:
                        continue
                    x0, y0, x1, y1 = f['uv']
                    rect = (int(round(min(x0, x1))), int(round(min(y0, y1))), int(round(max(x0, x1))), int(round(max(y0, y1))))
                    if rect[2] <= rect[0] or rect[3] <= rect[1]:
                        continue
                    nx, ny, nz = apply(em, NORMALS[fname])
                    side = -1
                    if abs(ny) < 0.5:
                        key = (int(round(nx)), int(round(nz))) if max(abs(nx), abs(nz)) > 0.7 else None
                        side = SIDE_OF_NORMAL.get(key, -1)
                    out.append((side, rect, e['name'], fname, size))
    walk(model['outliner'], [[1, 0, 0], [0, 1, 0], [0, 0, 1]], False)
    return out


# ---- placing ----------------------------------------------------------------------------------------------------
A1, A2 = 0.7548776662, 0.5698402910     # the plastic-number sequence: well spread, fixed


def seen(frame, side):
    """The sheet's pixels seen from that side of the machine at rest (a depth test over every face, half-pixel samples):
    a dent on a face hidden behind another part (the inside of the far leg) would be wasted."""
    near = {}
    sx, depth = VIEWS[side]
    for corner_at, tf, uv in geometry(frame):
        u1, v1, u2, v2 = uv
        for ty in range(int(min(v1, v2)), int(max(v1, v2))):
            for tx in range(int(min(u1, u2)), int(max(u1, u2))):
                for fu0 in (0.25, 0.75):
                    for fv0 in (0.25, 0.75):
                        fu = (tx + fu0 - u1) / (u2 - u1) if u2 != u1 else 0.5
                        fv = (ty + fv0 - v1) / (v2 - v1) if v2 != v1 else 0.5
                        q = tf(corner_at(fu, fv))
                        key = (round(sx(q) * 2), round(q[1] * 2))
                        d = depth(q)
                        if key not in near or d < near[key][0] - 1e-6:
                            near[key] = (d, (tx, ty))
    return {t for _, t in near.values()}


def dentable(sheet, glow, claims, side, x, y, wide):
    if not (0 <= x < sheet.width and 0 <= y < sheet.height):
        return False
    if claims.get((x, y)) != {side}:
        return False
    if VISIBLE is not None and (x, y) not in VISIBLE[side]:
        return False
    c = sheet.getpixel((x, y))
    if c[3] != 255 or (glow is not None and glow.getpixel((x, y))[3] > 0):
        return False
    mat = INDEX.get(c[:3], (None,))[0]
    return mat in METAL or (mat == 'rubber' and wide)


VISIBLE = None


def plan(frame):
    sheet = Image.open(os.path.join(ASSETS, 'textures', 'entity', 'mecha', frame + '.png')).convert('RGBA')
    gpath = os.path.join(ASSETS, 'textures', 'entity', 'mecha', frame + '_glowmask.png')
    glow = Image.open(gpath).convert('RGBA') if os.path.exists(gpath) else None
    fs = faces(frame)
    claims = {}
    for side, (x0, y0, x1, y1), *_ in fs:
        for y in range(y0, y1):
            for x in range(x0, x1):
                claims.setdefault((x, y), set()).add(side)
    global VISIBLE
    VISIBLE = {s: seen(frame, s) for s in range(4)}
    tiles = {}
    report = []
    for side in range(4):
        rects = {}
        for s, rect, name, fname, size in fs:
            if s == side and rect not in rects:
                rects[rect] = name
        panels = []
        for rect, name in rects.items():
            x0, y0, x1, y1 = rect
            wide = x1 - x0 >= PANEL and y1 - y0 >= PANEL
            n = sum(dentable(sheet, glow, claims, side, x, y, wide) for y in range(y0, y1) for x in range(x0, x1))
            if n >= 6:
                panels.append((n, rect, name, wide))
        panels.sort(key=lambda p: (-p[0], p[1][1], p[1][0]))
        # small parts (bolts, rims, thin bars) get no dents: they would read as noise
        panels = [p for p in panels if min(p[1][2] - p[1][0], p[1][3] - p[1][1]) >= 2 and (p[1][2] - p[1][0]) * (p[1][3] - p[1][1]) >= 12]
        area = sum(p[0] for p in panels)
        # the share of the side's metal each stage leaves dented (dent pixels against metal pixels)
        cover = [0.15, 0.40, 0.70]
        used = set()
        changed = {}
        counts = []
        on = [0] * len(panels)
        for stage in range(3):
            placed, misses = 0, 0
            i = 0
            while len(changed) < cover[stage] * area and misses < 120:
                kind = mark(stage, i)
                i += 1
                order = sorted(range(len(panels)), key=lambda q: (-panels[q][0] / (on[q] + 1.0), q))
                done = False
                for q in order:
                    n, rect, name, wide = panels[q]
                    # a thin rail takes a ding or two, not a row of them (a row reads as studs)
                    thin = min(rect[2] - rect[0], rect[3] - rect[1]) <= 3
                    if on[q] >= max(1, n // ((40, 24, 10) if thin else (20, 12, 4))[stage]):
                        continue
                    if place(sheet, glow, claims, side, rect, wide, kind, used, changed, seed=on[q] * 5 + q * 3 + stage * 17, gap=1 if stage < 2 else 0, last=stage == 2):
                        on[q] += 1
                        placed += 1
                        done = True
                        break
                misses = 0 if done else misses + 1
            if stage == 2:
                # by the last stage the earlier dents have been hit again: their shadowed walls go a step deeper
                for (x, y), c in list(changed.items()):
                    base = sheet.getpixel((x, y))[:3]
                    if lum(c[:3]) < lum(base) - 4 and c[:3] in INDEX:
                        changed[(x, y)] = shade(c[:3], 'd') + (255,)
            counts.append(placed)
            tiles[(side, stage + 1)] = dict(changed)
        report.append('%s %-5s: %4d px of metal on %2d panels; dents placed %s' % (frame, SIDES[side], area, len(panels), counts))
    return sheet, tiles, report


def mark(stage, i):
    """The i-th mark a stage adds: dents (sized to their panel), from the third stage a crease first; a scrape now and then."""
    if stage == 2 and i == 0:
        return 'crease'
    if stage >= 1 and i % 5 == 4:
        return 'scrape'
    return 'dent'


def cells(kind, w, h):
    """The ways a mark can go on a panel w x h, each a list of cells (dx, dy, shade), the preferred first: a dent as large as fits (a third of the panel at most, two
    thirds on a small one), a scrape, a crease across, or a nick."""
    if kind == 'scrape':
        # a scuff down to the right, steps of two (T13), bare metal with its shadowed lower edge
        return [[(0, 0, 's'), (1, 0, 's'), (2, 1, 's'), (3, 1, 's'), (4, 2, 's'), (5, 2, 's'),
                 (0, 1, 'd'), (1, 1, 'd'), (2, 2, 'd'), (3, 2, 'd')]]
    if kind == 'crease':
        # a fold across the panel: the upper lip deep in shadow, the lower lit, one step in its middle
        n = max(5, min(14, w - 2))
        out = []
        for j in range(n):
            dy = 0 if j < n // 2 else 1
            out += [(j, dy, 'D'), (j, dy + 1, 'L' if j % 3 else 'l')]
        return [out]
    if kind == NICK:
        return [[(0, 0, 'D'), (0, 1, 'L')]]
    lim_w, lim_h = max(3, int(w * (0.8 if w < 10 else 0.6))), max(3, int(h * (0.8 if h < 10 else 0.7)))
    fit = [sz for sz in SIZES if sz[0] <= min(lim_w, w - 2) and sz[1] <= min(lim_h, h - 2)]
    # the largest first; where it will not go, the next size down
    return [dent(*sz) for sz in reversed(fit)] + [[(0, 0, 'D'), (1, 0, 'd'), (0, 1, 'd'), (1, 1, 'L')]]


def place(sheet, glow, claims, side, rect, wide, kind, used, changed, seed, gap=1, last=False):
    x0, y0, x1, y1 = rect
    for cs in cells(kind, x1 - x0, y1 - y0):
        if put(sheet, glow, claims, side, rect, wide, cs, used, changed, seed, gap, last):
            return True
    return False


def put(sheet, glow, claims, side, rect, wide, cs, used, changed, seed, gap, last=False):
    x0, y0, x1, y1 = rect
    w = max(c[0] for c in cs) + 1
    h = max(c[1] for c in cs) + 1
    margin = 1 if (x1 - x0 >= w + 2 and y1 - y0 >= h + 2) else 0
    span_x, span_y = (x1 - x0) - w - 2 * margin, (y1 - y0) - h - 2 * margin
    if span_x < 0 or span_y < 0:
        return False
    # of the places the sequence offers, the one where the mark shows most (a pale flat field, not a dark band that
    # cannot go darker); none if even that would barely show
    best, best_score = None, 0.0
    for j in range(48):
        u = (0.5 + A1 * (seed + j)) % 1.0
        v = (0.5 + A2 * (seed + j)) % 1.0
        ox = x0 + margin + int(round(u * span_x))
        oy = y0 + margin + int(round(v * span_y))
        pts = [(ox + dx, oy + dy, s) for dx, dy, s in cs]
        # most of the mark must land on this side's visible metal; the rest of it is left out
        ok = [p for p in pts if dentable(sheet, glow, claims, side, p[0], p[1], wide)]
        if len(ok) < (0.5 if last else 0.75) * len(pts):
            continue
        pts = ok
        if any((x + ax, y + ay) in used for x, y, _ in pts for ax in range(-gap, gap + 1) for ay in range(-gap, gap + 1)):
            continue
        score = sum(abs(lum(shade(sheet.getpixel((x, y))[:3], s)) - lum(sheet.getpixel((x, y))[:3])) for x, y, s in pts) / len(pts)
        if score > best_score + 0.5:
            best, best_score = pts, score
    # the last stage takes what room is left, where a mark shows less
    if best is None or best_score < (5 if last else 10):
        return False
    for x, y, s in best:
        changed[(x, y)] = shade(sheet.getpixel((x, y))[:3], s) + (255,)
        used.add((x, y))
    return True


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def write(frame, sheet, tiles):
    w, h = sheet.size
    out = Image.new('RGBA', (w, h * 12), (0, 0, 0, 0))
    for (side, stage), px in tiles.items():
        oy = (side * 3 + stage - 1) * h
        for (x, y), c in px.items():
            out.putpixel((x, y + oy), c)
    folder = os.path.join(ASSETS, 'mecha', 'dents')
    os.makedirs(folder, exist_ok=True)
    out.save(os.path.join(folder, frame + '.png'), optimize=True)


# ---- review ------------------------------------------------------------------------------------------------------
def geometry(frame):
    """Every cube of the frame at rest, in model px: (corners of each face by name, its uv, element)."""
    model = json.load(open(os.path.join(HERE, frame, frame + '.bbmodel'), encoding='utf-8'))
    groups = {g['uuid']: g for g in model['groups']}
    elements = {e['uuid']: e for e in model['elements']}
    out = []

    def walk(nodes, chain, skip):
        for n in nodes:
            if isinstance(n, dict):
                g = groups.get(n['uuid'], {})
                name = g.get('name', '')
                walk(n.get('children', []), chain + [(g.get('origin', [0, 0, 0]), rot(g.get('rotation', [0, 0, 0])))],
                     skip or name.startswith('standin'))
            elif n in elements and not skip:
                e = elements[n]
                if e.get('type', 'cube') != 'cube':
                    continue
                full = chain + [(e.get('origin', [0, 0, 0]), rot(e.get('rotation', [0, 0, 0])))]

                def tf(p, full=full):
                    for o, m in reversed(full):
                        q = apply(m, [p[i] - o[i] for i in range(3)])
                        p = [q[i] + o[i] for i in range(3)]
                    return p
                (x0, y0, z0), (x1, y1, z1) = e['from'], e['to']
                for fname, f in e.get('faces', {}).items():
                    if f.get('texture') is None or 'uv' not in f:
                        continue
                    out.append((corner(fname, (x0, y0, z0), (x1, y1, z1)), tf, f['uv']))
    walk(model['outliner'], [], False)
    return out


def corner(face, lo, hi):
    """A point on a cube face from its texture's (u, v), each 0 to 1 across the face's UV area."""
    (x0, y0, z0), (x1, y1, z1) = lo, hi
    dx, dy, dz = x1 - x0, y1 - y0, z1 - z0
    return {
        'north': lambda u, v: (x1 - u * dx, y1 - v * dy, z0), 'south': lambda u, v: (x0 + u * dx, y1 - v * dy, z1),
        'east': lambda u, v: (x1, y1 - v * dy, z1 - u * dz), 'west': lambda u, v: (x0, y1 - v * dy, z0 + u * dz),
        'up': lambda u, v: (x0 + u * dx, y1, z0 + v * dz), 'down': lambda u, v: (x0 + u * dx, y0, z1 - v * dz)}[face]


VIEWS = {  # screen x, depth (smaller is nearer), from the machine's own front, right, back and left
    0: (lambda p: -p[0], lambda p: p[2]), 1: (lambda p: -p[2], lambda p: -p[0]),
    2: (lambda p: p[0], lambda p: -p[2]), 3: (lambda p: p[2], lambda p: p[0])}


def render(geo, img, view, scale=6):
    sx, depth = VIEWS[view]
    pts = []
    for corner, tf, uv in geo:
        u1, v1, u2, v2 = uv
        for ty in range(int(min(v1, v2)), int(max(v1, v2))):
            for tx in range(int(min(u1, u2)), int(max(u1, u2))):
                c = img.getpixel((tx, ty))
                if c[3] == 0:
                    continue
                fu = (tx + 0.5 - u1) / (u2 - u1) if u2 != u1 else 0.5
                fv = (ty + 0.5 - v1) / (v2 - v1) if v2 != v1 else 0.5
                p = tf(corner(fu, fv))
                pts.append((depth(p), sx(p), -p[1], c))
    xs = [p[1] for p in pts]
    ys = [p[2] for p in pts]
    w, h = int((max(xs) - min(xs) + 2) * scale), int((max(ys) - min(ys) + 2) * scale)
    out = Image.new('RGBA', (w, h), (58, 62, 70, 255))
    zbuf = {}
    for d, x, y, c in sorted(pts, key=lambda q: -q[0]):
        X, Y = int((x - min(xs) + 0.5) * scale), int((y - min(ys) + 0.5) * scale)
        out.paste(c, (X - scale // 2, Y - scale // 2, X - scale // 2 + scale, Y - scale // 2 + scale))
    return out


def preview(frame, sheet, tiles, out_dir):
    """The machine seen from each side (rest pose, flat light), undamaged and at stages 1-3 on that side."""
    geo = geometry(frame)
    rows = []
    for side in range(4):
        row = []
        for stage in range(4):
            img = sheet.copy()
            for (x, y), c in (tiles.get((side, stage), {}) if stage else {}).items():
                img.putpixel((x, y), c)
            row.append(render(geo, img, side))
        rows.append(row)
    w = sum(r.width for r in rows[0]) + 30
    sheet_w = max(sum(i.width for i in r) + 30 for r in rows)
    total = Image.new('RGBA', (sheet_w, sum(r[0].height + 10 for r in rows)), (20, 20, 24, 255))
    y = 0
    for r in rows:
        x = 0
        for i in r:
            total.alpha_composite(i, (x, y))
            x += i.width + 10
        y += r[0].height + 10
    total.save(os.path.join(out_dir, frame + '_dents.png'))


def main():
    out_dir = sys.argv[sys.argv.index('--preview') + 1] if '--preview' in sys.argv else None
    only = [a for a in sys.argv[1:] if a in FRAMES]
    for frame in only or FRAMES:
        sheet, tiles, report = plan(frame)
        write(frame, sheet, tiles)
        if out_dir:
            os.makedirs(out_dir, exist_ok=True)
            preview(frame, sheet, tiles, out_dir)
        print('\n'.join(report))


if __name__ == '__main__':
    main()

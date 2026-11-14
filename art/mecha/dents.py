"""Dents for the animated mecha frames and the Utility Shells: where a sheet buckles as it takes damage on one side.

A frame that has lost a quarter, a half and three quarters of its health shows dent stage 1, 2 and 3 on the side it
was hit from most (front, right, back or left of the machine). This script places the dents, once, for every frame:

- the faces that look out to each side are found from the frame's model (the cube faces' normals with the bones' rest
  rotations; the machine faces north, its left is -x), and only pixels seen from that side standing are used (a depth
  test: the inside of the far leg is never seen);
- only pixels that belong to that side alone are used (a UV area shared with a face looking elsewhere is left alone),
  and only metal: enamel, steel, brass, warning paint, iron, and the rubber-black paint on broad panels (glass, lamps
  and copper are never dented; flesh and bone only on a frame partly alive, the Chimera, and they take their own wear:
  see living());
- each dent is a small placed stamp, lit as everything is from the top left: the upper-left inner wall of the hollow a
  step or two darker in that pixel's own ramp, the lower-right wall a step lighter (on black paint, where a dent six
  pixels across or more cracks the paint off its lit wall, bare steel: a lighter black reads only as mottling). Stage 1 puts a few small dents on
  the side's broadest panels; stage 2 adds more and larger ones and a scrape through the paint; stage 3 adds more again
  and a crease across the broadest panel. Placement follows a fixed low-discrepancy sequence over each face, so the
  result is the same every run and nothing is per-pixel noise.
- a side that still reads less than the Bulwark's left flank (the frame that reads best: at stages 1, 2 and 3, 6, 12
  and 14 in a hundred of the texels seen of that side parked change by more than 24 in luminance) takes more marks
  until it does or has no room left: on smaller parts and narrower black panels too, on faces that lean toward the side
  (a leg splayed at forty-five degrees belongs to both sides it shows to, a part turned for parking counts by its
  parked normal), on pixels only the parked pose shows, packed closer; still short, the hollows' faint floors and
  rims go a step deeper. A side with room never needs any of it (the Bulwark's left flank is as it was), so this adds
  only to the Hopper, the Kite, the Skitter, the Chimera and the fainter sides of the others.
- since 0.5.3 that bar is judged as the game draws the machine, not on the sheet: each side seen by the game's camera
  from 5 and 8 blocks, parked and standing, lit as vanilla lights an entity (a flank at half light), and a stage must
  change as large a share of the machine's pixels on screen plainly as the Bulwark's left flank does in that view
  (see view() and TARGET). The spare marks go where they change the most pixels there, and a side still short has its
  faint hollows deepened until they show at that light.

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

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'headinajar')
FRAMES = ['hopper', 'bulwark', 'colossus', 'executive', 'wheelhouse', 'chimera', 'skitter', 'kite']
# the three Utility Shells, dented the same way (their models in art/shell, their sheets in textures/entity/shell)
SHELLS = ['lunk', 'rover', 'spider_walker']


def model_file(frame):
    if frame in SHELLS:
        return os.path.join(os.path.dirname(HERE), 'shell', frame, frame + '.bbmodel')
    return os.path.join(HERE, frame, frame + '.bbmodel')


def sheet_file(frame, suffix=''):
    return os.path.join(ASSETS, 'textures', 'entity', 'shell' if frame in SHELLS else 'mecha', frame + suffix + '.png')


SIDES = ['front', 'right', 'back', 'left']
# the machine faces north (-z); its right is +x
SIDE_OF_NORMAL = {(0, -1): 0, (1, 0): 1, (0, 1): 2, (-1, 0): 3}
METAL = {'enamel', 'steel', 'brass', 'warning', 'iron'}
PAINT = {'enamel', 'warning', 'rubber'}
# the frames built partly of a living thing: their flesh bruises and tears and their bone cracks
ORGANIC = {'chimera'}
LIVING = {'flesh', 'bone'}
PANEL = 6          # rubber-black only on faces at least this wide and tall (panels, not hoses)
PANEL_SPARE = 4    # the same on a side short of metal

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
                    out.append((x, y, 'p'))
                continue
            if x < 0 or y < 0 or x >= w or y >= h:
                continue
            if r > 0.35:
                if d < -0.1:
                    out.append((x, y, 'X' if (r > 0.6 and d < -0.45 and w >= 4) else 'D'))
                elif d > 0.25:
                    # (a dent six pixels across or more has cracked black paint off its lit wall; a smaller one has not)
                    lit = 'L' if r > 0.6 else 'l'
                    out.append((x, y, {'L': 'K', 'l': 'k'}[lit] if w >= 6 else lit))
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
    if kind == 'p':
        # the lip on a dent's upper-left rim, a step lighter (never chipped: a chipped lip and wall outline a diamond)
        kind = 'l'
    if kind in ('k', 'K'):
        # the lit wall and lip of a dent; on black paint the paint has cracked off there and the bare steel catches the
        # light (a lighter black would only read as mottling)
        if mat == 'rubber':
            return RAMPS['steel'][2 if kind == 'k' else 3]
        kind = 'l' if kind == 'k' else 'L'
    if kind == 'v':
        # the heart of a bruise: purple (the mod's violet), not red, so it reads as a bruise and not as blood
        if mat == 'flesh':
            return RAMPS['violet'][1]
        kind = 'D'
    if kind == 'r':
        # torn flesh: the raw depth of the tear
        return RAMPS['flesh'][0] if mat == 'flesh' else r[max(0, int(math.ceil(step)) - 2)]
    if kind in ('b', 'B'):
        if mat not in LIVING:
            kind = 'd'
        else:
            # bone showing through torn flesh (on bone itself, a chip of its paler inside)
            return RAMPS['bone'][3 if kind == 'b' else 2]
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


def faces(frame, leaning=False):
    """Every cube face of the frame (stand-ins left out): (side or -1, uv rect in sheet px, width, height, element).

    A face looks out to a side when its normal at rest is within forty-five degrees of it. Leaning (for a side short of
    metal): to the side its normal leans to most, standing or else parked, unless it looks within about twenty-five
    degrees of straight up or down (a leg splayed at forty-five degrees, a wing folded down the flank for parking: both
    are seen from the side), and a corner face turned near forty-five degrees to both sides it shows to."""
    model = json.load(open(model_file(frame), encoding='utf-8'))
    groups = {g['uuid']: g for g in model['groups']}
    elements = {e['uuid']: e for e in model['elements']}
    parked = pose(frame, 'parked')
    out = []

    def side_of(n):
        nx, ny, nz = n
        if abs(ny) >= 0.9:
            return -1
        ex, ez = (1 if nx > 0 else 3), (2 if nz > 0 else 0)
        if min(abs(nx), abs(nz)) > 0.5 * math.hypot(nx, nz):
            # a corner face, turned near forty-five degrees: seen as much from either side, dented by blows from both
            return tuple(sorted((ex, ez)))
        return ex if abs(nx) > abs(nz) else ez

    def walk(nodes, m, mp, skip):
        for n in nodes:
            if isinstance(n, dict):
                g = groups.get(n['uuid'], {})
                name = g.get('name', '')
                r = g.get('rotation', [0, 0, 0])
                extra = parked.get(name, ([0, 0, 0], None, 1))[0]
                walk(n.get('children', []), mul(m, rot(r)), mul(mp, rot([a + b for a, b in zip(r, extra)])),
                     skip or name.startswith('standin') or name.startswith('lamp_'))
            elif n in elements and not skip:
                e = elements[n]
                if e.get('type', 'cube') != 'cube' or e['name'].startswith('standin'):
                    continue
                em = mul(m, rot(e.get('rotation', [0, 0, 0])))
                emp = mul(mp, rot(e.get('rotation', [0, 0, 0])))
                size = [abs(e['to'][i] - e['from'][i]) for i in range(3)]
                for fname, f in e.get('faces', {}).items():
                    if f.get('texture') is None or 'uv' not in f:
                        continue
                    x0, y0, x1, y1 = f['uv']
                    rect = (int(round(min(x0, x1))), int(round(min(y0, y1))), int(round(max(x0, x1))), int(round(max(y0, y1))))
                    if rect[2] <= rect[0] or rect[3] <= rect[1]:
                        continue
                    if leaning:
                        side = side_of(apply(em, NORMALS[fname]))
                        if side == -1:
                            side = side_of(apply(emp, NORMALS[fname]))
                    else:
                        nx, ny, nz = apply(em, NORMALS[fname])
                        side = -1
                        if abs(ny) < 0.5:
                            key = (int(round(nx)), int(round(nz))) if max(abs(nx), abs(nz)) > 0.7 else None
                            side = SIDE_OF_NORMAL.get(key, -1)
                    out.append((side, rect, e['name'], fname, size))
    one = [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
    walk(model['outliner'], one, one, False)
    return out


# ---- what a bystander sees: each side as the game draws it --------------------------------------------------------
# The game's camera 5 and 8 blocks off each side (eye 1.62 blocks up, aimed at the machine's middle, field of view 70,
# 1280x720), the machine parked and standing, lit as vanilla lights an entity (two fixed lights, 0.4 ambient and 0.6
# diffuse: a flank is drawn at half brightness, so a change of value there shows at half). A stage's reading is the
# share of the machine's screen pixels it changes by more than 24 in luminance; measured this way the frames' 0.5.2
# readings came out within about a point of the same measure on game screenshots, in the same order.
SCREEN_W, SCREEN_H, FOV, EYE = 1280, 720, 70.0, 1.62 * 16
AIM = {'hopper': 1.2, 'bulwark': 1.6, 'colossus': 2.2, 'executive': 2.2, 'wheelhouse': 0.8, 'chimera': 1.4,
       'skitter': 0.8, 'kite': 1.0, 'lunk': 1.3, 'rover': 0.45, 'spider_walker': 0.45}
OUTWARD = {0: (0, 0, -1), 1: (1, 0, 0), 2: (0, 0, 1), 3: (-1, 0, 0)}
LIGHT0 = np.array([0.2, 1.0, -0.7]) / np.linalg.norm([0.2, 1.0, -0.7])
LIGHT1 = np.array([-0.2, 1.0, 0.7]) / np.linalg.norm([-0.2, 1.0, 0.7])
# the bar every side of every frame is brought up to, per view (pose, blocks off): the Bulwark's left flank (the frame
# that read best in the game in 0.5.1 and 0.5.2) at stages 1, 2 and 3, in percent
TARGET = {('parked', 5): (5.5, 11.0, 16.0), ('parked', 8): (5.2, 10.6, 15.6),
          ('standing', 5): (5.4, 10.9, 15.7), ('standing', 8): (5.2, 10.6, 15.4)}


def light(n):
    return min(1.0, 0.4 + 0.6 * (max(0.0, float(np.dot(LIGHT0, n))) + max(0.0, float(np.dot(LIGHT1, n)))))


def view(frame, geo, alpha, side, dist):
    """The sheet texel (x, y) and the light of every screen pixel the machine covers, seen from that side."""
    cam = np.array(OUTWARD[side], float) * dist * 16.0 + np.array([0, EYE, 0])
    f = np.array([0, AIM.get(frame, 1.0) * 16, 0]) - cam
    f /= np.linalg.norm(f)
    r = np.cross(f, [0, 1, 0])
    r /= np.linalg.norm(r)
    u = np.cross(r, f)
    focal = (SCREEN_H / 2) / math.tan(math.radians(FOV / 2))
    parts = []
    for cfn, tf, uv, fname in geo:
        u1, v1, u2, v2 = uv
        if u1 == u2 or v1 == v2:
            continue
        p0 = np.array(tf(cfn(0, 0)), float)
        du = np.array(tf(cfn(1, 0)), float) - p0
        dv = np.array(tf(cfn(0, 1)), float) - p0
        c = cfn(0.5, 0.5)
        nn = np.array(tf([c[i] + NORMALS[fname][i] for i in range(3)]), float) - np.array(tf(c), float)
        nn /= max(1e-9, np.linalg.norm(nn))
        nu, nv = abs(u2 - u1), abs(v2 - v1)
        zc = max(1.0, float(np.dot(p0 + 0.5 * du + 0.5 * dv - cam, f)))
        ku = max(2, int(math.ceil(1.6 * focal * np.linalg.norm(du) / nu / zc)))
        kv = max(2, int(math.ceil(1.6 * focal * np.linalg.norm(dv) / nv / zc)))
        su, sv = np.meshgrid((np.arange(int(round(nu * ku))) + 0.5) / ku, (np.arange(int(round(nv * kv))) + 0.5) / kv)
        su, sv = su.ravel(), sv.ravel()
        tx = np.floor(min(u1, u2) + su).astype(int)
        ty = np.floor(min(v1, v2) + sv).astype(int)
        fu = (tx + (su - np.floor(su)) - u1) / (u2 - u1)
        fv = (ty + (sv - np.floor(sv)) - v1) / (v2 - v1)
        ok = (tx >= 0) & (ty >= 0) & (tx < alpha.shape[1]) & (ty < alpha.shape[0])
        tx, ty, fu, fv = tx[ok], ty[ok], fu[ok], fv[ok]
        ok = alpha[ty, tx] > 0
        tx, ty, fu, fv = tx[ok], ty[ok], fu[ok], fv[ok]
        d = p0[None, :] + fu[:, None] * du[None, :] + fv[:, None] * dv[None, :] - cam
        z = d @ f
        ok = z > 1
        d, z, tx, ty = d[ok], z[ok], tx[ok], ty[ok]
        ix = np.floor(SCREEN_W / 2 + focal * (d @ r) / z).astype(int)
        iy = np.floor(SCREEN_H / 2 - focal * (d @ u) / z).astype(int)
        parts.append((ix, iy, z, tx, ty, np.full(len(tx), light(nn))))
    ix, iy, z, tx, ty, lt = (np.concatenate([p[i] for p in parts]) for i in range(6))
    ok = (ix >= 0) & (iy >= 0) & (ix < SCREEN_W) & (iy < SCREEN_H)
    pid, z, tx, ty, lt = (iy * SCREEN_W + ix)[ok], z[ok], tx[ok], ty[ok], lt[ok]
    order = np.lexsort((z, pid))
    pid, tx, ty, lt = pid[order], tx[order], ty[order], lt[order]
    first = np.ones(len(pid), bool)
    first[1:] = pid[1:] != pid[:-1]
    return tx[first], ty[first], lt[first]


def views(frame, sheet):
    """Per side: the views a stage is judged in, {(pose, blocks): (texel x, texel y, light)}."""
    alpha = np.asarray(sheet)[..., 3]
    geos = {'parked': geometry(frame, pose(frame, 'parked')), 'standing': geometry(frame)}
    return {side: {(p, d): view(frame, g, alpha, side, d) for p, g in geos.items() for d in (5, 8)} for side in range(4)}


BASE_LUM = {}


def reading(sheet, changed, vs):
    """A stage's reading in each view: the share (percent) of the machine's screen pixels it changes plainly."""
    if BASE_LUM.get('sheet') is not sheet:
        a = np.asarray(sheet.convert('RGB')).astype(float)
        BASE_LUM['sheet'], BASE_LUM['lum'] = sheet, 0.299 * a[..., 0] + 0.587 * a[..., 1] + 0.114 * a[..., 2]
    dl = np.zeros((sheet.height, sheet.width))
    if changed:
        xy = np.array(list(changed.keys()))
        c = np.array([v[:3] for v in changed.values()], float)
        dl[xy[:, 1], xy[:, 0]] = np.abs(0.299 * c[:, 0] + 0.587 * c[:, 1] + 0.114 * c[:, 2] - BASE_LUM['lum'][xy[:, 1], xy[:, 0]])
    return {k: 100.0 * float(np.mean(dl[ty, tx] * lt > 24)) for k, (tx, ty, lt) in vs.items()}


def short(sheet, changed, vs, stage, before=None):
    """Whether a stage still reads less than the bar in any view, or adds less to the stage before it (its reading in
    before) than four fifths of what the bar adds there: a side that reached the bar early must still worsen plainly."""
    if not TARGET:
        return False
    now = reading(sheet, changed, vs)
    need = {k: TARGET[k][stage] if not before else
            max(TARGET[k][stage], before[k] + 0.8 * (TARGET[k][stage] - TARGET[k][stage - 1])) for k in vs}
    return any(now[k] < need[k] - 0.05 for k in vs)


SCREEN = {}       # the side being planned: each texel's share of its views' pixels and the most light it shows with


# ---- placing ----------------------------------------------------------------------------------------------------
A1, A2 = 0.7548776662, 0.5698402910     # the plastic-number sequence: well spread, fixed


def seen_in(geo, side):
    """The sheet's pixels seen from that side of the machine posed as geo is (a depth test over every face, half-pixel
    samples): a dent on a face hidden behind another part (the inside of the far leg) would be wasted."""
    near = {}
    sx, depth = VIEWS[side]
    for corner_at, tf, uv, _ in geo:
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


DENTABLE = {}


def dentable(sheet, glow, claims, side, x, y, wide, spare=False):
    """Whether a dent may change this pixel for this side: metal (or broad black paint), not glowing, seen from that
    side and on no face that looks elsewhere. The first rule is the one every frame had: faces by their rest normal,
    pixels seen standing. Spare (for a side short of metal): faces leaning to the side, pixels seen standing or parked."""
    key = (side, x, y, wide, spare)
    if key not in DENTABLE:
        DENTABLE[key] = dentable_now(sheet, glow, claims[spare], side, x, y, wide, VISIBLE[spare])
    return DENTABLE[key]


def dentable_now(sheet, glow, claims, side, x, y, wide, visible):
    if not (0 <= x < sheet.width and 0 <= y < sheet.height):
        return False
    # every face that shows this pixel looks out to this side (or is a corner face between it and the next)
    if not all(side in (c if isinstance(c, tuple) else (c,)) for c in claims.get((x, y), {-1})):
        return False
    if (x, y) not in visible[side]:
        return False
    c = sheet.getpixel((x, y))
    if c[3] != 255 or (glow is not None and glow.getpixel((x, y))[3] > 0):
        return False
    mat = INDEX.get(c[:3], (None,))[0]
    return mat in METAL or (mat == 'rubber' and wide) or (mat in LIVING and LIVING_NOW[0])


LIVING_NOW = [False]     # whether the frame being planned is partly alive (its flesh and bone take wear)


VISIBLE = {}


def panels_of(sheet, glow, claims, fs, side, spare, shown, strips=False):
    """The side's panels: (dentable pixels, rect, element, broad enough for black paint, of them seen parked). Strips:
    the side's rails and lips one pixel thick instead (a frame drawn mostly in them, as the Rover's front is, has no
    other room for a mark)."""
    least, area_least = (PANEL_SPARE, 8) if spare else (PANEL, 12)
    rects = {}
    for s, rect, name, fname, size in fs:
        if (s == side or isinstance(s, tuple) and side in s) and rect not in rects:
            rects[rect] = name
    out = []
    for rect, name in rects.items():
        x0, y0, x1, y1 = rect
        wide = x1 - x0 >= least and y1 - y0 >= least
        n = sum(dentable(sheet, glow, claims, side, x, y, wide, spare) for y in range(y0, y1) for x in range(x0, x1))
        if n >= 6:
            parked_n = sum((x, y) in shown and dentable(sheet, glow, claims, side, x, y, wide, spare)
                           for y in range(y0, y1) for x in range(x0, x1))
            out.append((n, rect, name, wide, parked_n))
    out.sort(key=lambda p: (-p[0], p[1][1], p[1][0]))
    if strips:
        # (not black ones: a nick chipped to the steel along a black rail reads as a stud)
        return [p for p in out if min(p[1][2] - p[1][0], p[1][3] - p[1][1]) == 1 and max(p[1][2] - p[1][0], p[1][3] - p[1][1]) >= 5
                and material(sheet, p[1]) != 'rubber']
    # small parts (bolts, rims, thin bars) get no dents: they would read as noise
    return [p for p in out if min(p[1][2] - p[1][0], p[1][3] - p[1][1]) >= 2
            and (p[1][2] - p[1][0]) * (p[1][3] - p[1][1]) >= area_least]


def plan(frame):
    sheet = Image.open(sheet_file(frame)).convert('RGBA')
    LIVING_NOW[0] = frame in ORGANIC
    MATERIAL.clear()
    DARK.clear()
    gpath = sheet_file(frame, '_glowmask')
    glow = Image.open(gpath).convert('RGBA') if os.path.exists(gpath) else None
    fs = {False: faces(frame), True: faces(frame, leaning=True)}
    claims = {}
    for spare in (False, True):
        claims[spare] = {}
        for side, (x0, y0, x1, y1), *_ in fs[spare]:
            for y in range(y0, y1):
                for x in range(x0, x1):
                    claims[spare].setdefault((x, y), set()).add(side)
    DENTABLE.clear()
    rest = geometry(frame)
    parked = geometry(frame, pose(frame, 'parked'))
    VISIBLE[False] = {s: seen_in(rest, s) for s in range(4)}
    # what a bystander sees of each side of a parked machine (where a stage is judged)
    shown = {s: {t for t in seen_in(parked, s) if sheet.getpixel(t)[3] > 0} for s in range(4)}
    VISIBLE[True] = {s: VISIBLE[False][s] | shown[s] for s in range(4)}
    seen = views(frame, sheet)
    tiles = {}
    report = []
    for side in range(4):
        vs = seen[side]
        # each texel's share of the pixels of this side's views, and the most light it is drawn with there
        weight, bright = np.zeros((sheet.height, sheet.width)), np.zeros((sheet.height, sheet.width))
        for tx, ty, lt in vs.values():
            np.add.at(weight, (ty, tx), 1.0 / max(1, len(tx)))
            np.maximum.at(bright, (ty, tx), lt)
        SCREEN['weight'], SCREEN['light'] = weight, bright
        panels = panels_of(sheet, glow, claims, fs[False], side, False, shown[side])
        spare = panels_of(sheet, glow, claims, fs[True], side, True, shown[side])
        spare += panels_of(sheet, glow, claims, fs[True], side, True, shown[side], strips=True)
        # the spare panels by how much of this side's views they fill
        sweight = {p[1]: float(weight[p[1][1]:p[1][3], p[1][0]:p[1][2]].sum()) for p in spare}
        area = sum(p[0] for p in panels)
        # the share of the side's metal each stage leaves dented (dent pixels against metal pixels)
        cover = [0.15, 0.40, 0.70]
        # and, at the least, the share of what is seen of that side parked (every material) that a stage changes plainly
        # (by more than 24 in luminance): the Bulwark's left side, the frame that reads best, gives 6, 12 and 14
        plain = [0.06, 0.12, 0.14]
        used = set()
        changed = {}
        counts = []
        on = {}           # marks on each panel (by its rect), in either pass
        tried = {}        # failed tries on each spare panel, so the next looks at other places
        for stage in range(3):
            placed, misses = 0, 0
            i = 0
            before = reading(sheet, tiles[(side, stage)], vs) if stage else None
            while len(changed) < cover[stage] * area and misses < 120:
                kind = mark(stage, i)
                i += 1
                order = sorted(range(len(panels)), key=lambda q: (-panels[q][0] / (on.get(panels[q][1], 0) + 1.0), q))
                done = False
                for q in order:
                    n, rect, name, wide, _ = panels[q]
                    k = on.get(rect, 0)
                    # a thin rail takes a ding or two, not a row of them (a row reads as studs)
                    thin = min(rect[2] - rect[0], rect[3] - rect[1]) <= 3
                    # (flesh and bone take a few bruises, tears and cracks, not a crowd of them)
                    alive = material(sheet, rect) in LIVING
                    if k >= max(1, n // ((40, 24, 10) if thin else (40, 24, 12) if alive else (20, 12, 4))[stage]):
                        continue
                    if place(sheet, glow, claims, side, rect, wide, kind, used, changed, seed=k * 5 + q * 3 + stage * 17,
                             gap=1 if stage < 2 else 0, last=stage == 2):
                        on[rect] = k + 1
                        placed += 1
                        done = True
                        break
                misses = 0 if done else misses + 1
            if stage == 2:
                # by the last stage the earlier dents have been hit again: their shadowed walls go a step deeper
                # (before any spare marks, so a side is judged with it)
                for (x, y), c in list(changed.items()):
                    base = sheet.getpixel((x, y))[:3]
                    if (lum(c[:3]) < lum(base) - 4 and c[:3] in INDEX and lum(shade(c[:3], 'd')) < lum(c[:3])
                            and INDEX.get(base, (None,))[0] not in LIVING):
                        changed[(x, y)] = shade(c[:3], 'd') + (255,)
            # a side with too little open metal to read from where the others do: marks on its smaller parts, on faces
            # turned toward it and on what shows of it parked, packed closer and more of them to a part, the parts seen
            # parked first
            # (since 0.5.3 also until it reads as the Bulwark's left flank does in every view: the parts that fill most of
            # the side's views first, each mark where it changes the most pixels plainly)
            misses = 0
            while (strong(sheet, changed, shown[side]) < plain[stage] * len(shown[side])
                   or short(sheet, changed, vs, stage, before)) and misses < 120:
                kind = mark(stage, i)
                i += 1
                order = sorted(range(len(spare)), key=lambda q: (-sweight[spare[q][1]] / (on.get(spare[q][1], 0) + 1.0), q))
                done = False
                for q in order:
                    n, rect, name, wide, _ = spare[q]
                    k = on.get(rect, 0)
                    thin = min(rect[2] - rect[0], rect[3] - rect[1]) <= 3
                    # (a rail one pixel thick takes a nick or two along its length, well apart: more read as a dashed line)
                    hair = min(rect[2] - rect[0], rect[3] - rect[1]) == 1
                    alive = material(sheet, rect) in LIVING
                    if thin and dark(sheet, rect):
                        # a row of lit dings along a dark rail reads as studs
                        continue
                    if min(rect[2] - rect[0], rect[3] - rect[1]) <= 5 and dark(sheet, rect) and                             k >= max(1, max(rect[2] - rect[0], rect[3] - rect[1]) // (16, 12, 8)[stage]):
                        # and so does more than a chip or two along a narrow black panel
                        continue
                    if k >= max(1, n // ((20, 10, 6) if hair else (30, 18, 10) if alive else (20, 10, 4) if thin else (10, 6, 2))[stage]):
                        continue
                    if place(sheet, glow, claims, side, rect, wide, kind, used, changed,
                             seed=k * 5 + q * 3 + stage * 17 + tried.get(rect, 0) * 53,
                             gap=3 if hair else 1 if stage == 0 else 0, last=stage >= 1, spare=True):
                        on[rect] = k + 1
                        placed += 1
                        done = True
                        break
                    tried[rect] = tried.get(rect, 0) + 1
                misses = 0 if done else misses + 1
            # still short: the hollows' faint floors and rims go a step further, those that fill most of the side's views
            # first, a few at a time until it reads (first those that change 1 to 24 in luminance on the sheet, then, since
            # 0.5.3, those that do not yet show plainly as drawn: a flank is drawn at half light)
            if stage >= 1:
                deepen(sheet, changed, weight, lambda diff, x, y: abs(diff) <= 24,
                       lambda: strong(sheet, changed, shown[side]) < plain[stage] * len(shown[side]))
            for step in range(2):
                deepen(sheet, changed, weight, lambda diff, x, y: abs(diff) * (bright[y, x] or 1.0) <= 24,
                       lambda: short(sheet, changed, vs, stage, before))
            counts.append(placed)
            tiles[(side, stage + 1)] = dict(changed)
        report.append('%s %-5s: %4d px of metal on %2d panels (%d spare); dents placed %s; plainly changed %d of %d seen parked; reads %s'
                      % (frame, SIDES[side], area, len(panels), len(spare), counts, strong(sheet, changed, shown[side]), len(shown[side]),
                         ' '.join('%s%d %s' % (k[0][0], k[1], '/'.join('%.1f' % reading(sheet, tiles[(side, st)], vs)[k] for st in (1, 2, 3)))
                                  for k in sorted(vs))))
    return sheet, tiles, report


def deepen(sheet, changed, weight, faint, still_short):
    """Takes faint pixels of the marks a step further (darker in a hollow, lighter on a rim), the ones that fill most of
    the side's views first, eight at a time, while still_short()."""
    todo = []
    for (x, y), c in changed.items():
        diff = lum(c[:3]) - lum(sheet.getpixel((x, y))[:3])
        # (a bruise or a tear is as deep as it goes)
        if c[:3] in INDEX and diff != 0 and faint(diff, x, y) and INDEX.get(sheet.getpixel((x, y))[:3], (None,))[0] not in LIVING:
            todo.append((-weight[y, x], y, x, diff < 0))
    todo.sort()
    for i in range(0, len(todo), 8):
        if not still_short():
            return
        for _, y, x, dark in todo[i:i + 8]:
            # (a rim on black paint chips to the steel: a lighter black hardly shows)
            changed[(x, y)] = shade(changed[(x, y)][:3], 'd' if dark else 'k') + (255,)


def strong(sheet, changed, shown):
    """How many of the texels seen parked a stage changes plainly (by more than 24 in luminance)."""
    return sum(1 for t, c in changed.items() if t in shown and abs(lum(c[:3]) - lum(sheet.getpixel(t)[:3])) > 24)


def mark(stage, i):
    """The i-th mark a stage adds: dents (sized to their panel), from the third stage a crease first; a scrape now and then."""
    if stage == 2 and i == 0:
        return 'crease'
    if stage >= 1 and i % 5 == 4:
        return 'scrape'
    return 'dent'


def living(kind, mat, w, h):
    """Wear on a living part. Flesh bruises (a purple heart in a ring a step darker than the skin, no lit rim: it is not
    a hollow), tears (a raw line, the flesh ramp's darkest, with a pale lip below) and, at the last, tears to the bone
    (pale bone showing in the middle of the tear). Bone cracks: a dark hairline stepping down across it, at the last
    with a chip broken off its start. No bright red anywhere: it should read as wear, not as blood."""
    if mat == 'bone':
        n = max(3, min(7 if kind == 'crease' else 5, w - 1))
        out, y = [], 0
        for j in range(n):
            out.append((j, y, 'D'))
            if j % 2 == 1 and y < h - 1:
                y += 1
                out.append((j, y, 'D'))
        if kind in ('crease', 'scrape'):
            # a chip broken off the crack's start: the paler inside of the bone
            out += [(0, 1, 'b'), (1, 1, 'B')]
        return [out]
    if kind in ('scrape', 'crease'):
        n = max(3, min(6 if kind == 'crease' else 4, w - 1))
        out = []
        for j in range(n):
            dy = j // 2
            core = 'b' if kind == 'crease' and 0 < j < n - 1 else 'r'
            out += [(j, dy, core), (j, dy + 1, 'l')]
            if j == 0 or j == n - 1:
                out[-2] = (j, dy, 'r')
        return [out]
    bw, bh = min(6, max(3, w - 2)), min(4, max(2, h - 2))
    out = []
    for sz in [(bw, bh), (3, 2)]:
        cx, cy = sz[0] / 2.0, sz[1] / 2.0
        cs = []
        for y in range(sz[1]):
            for x in range(sz[0]):
                r = math.hypot((x + 0.5 - cx) / cx, (y + 0.5 - cy) / cy)
                if r <= 1.05:
                    cs.append((x, y, 'v' if r < 0.6 else 'd'))
        out.append(cs)
    return out


def cells(kind, w, h, mat=None):
    """The ways a mark can go on a panel w x h, each a list of cells (dx, dy, shade), the preferred first: a dent as large as fits (a third of the panel at most, two
    thirds on a small one), a scrape, a crease across, or a nick."""
    if mat in LIVING:
        return living(kind, mat, w, h)
    if min(w, h) == 1:
        # a rail or lip one pixel thick takes nicks along it: a notch in shadow, its edge toward the light caught (on
        # black paint, chipped to the steel)
        along = [(0, 'k'), (1, 'D'), (2, 'D')] if kind != 'dent' else [(0, 'k'), (1, 'D')]
        return [[(i, 0, sh) if w > h else (0, i, sh) for i, sh in along]]
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


def place(sheet, glow, claims, side, rect, wide, kind, used, changed, seed, gap=1, last=False, spare=False):
    x0, y0, x1, y1 = rect
    for cs in cells(kind, x1 - x0, y1 - y0, material(sheet, rect)):
        if spare:
            # a spare mark on black paint chips it to the steel whatever its size (a side short of the bar is one whose
            # black panels hardly show their hollows)
            cs = [(dx, dy, {'l': 'k', 'L': 'K'}.get(sh, sh)) for dx, dy, sh in cs]
        if put(sheet, glow, claims, side, rect, wide, cs, used, changed, seed, gap, last, spare):
            return True
    return False


def put(sheet, glow, claims, side, rect, wide, cs, used, changed, seed, gap, last=False, spare=False):
    x0, y0, x1, y1 = rect
    w = max(c[0] for c in cs) + 1
    h = max(c[1] for c in cs) + 1
    margin = 1 if (x1 - x0 >= w + 2 and y1 - y0 >= h + 2) else 0
    span_x, span_y = (x1 - x0) - w - 2 * margin, (y1 - y0) - h - 2 * margin
    if span_x < 0 or span_y < 0:
        return False
    # of the places the sequence offers, the one where the mark shows most (a pale flat field, not a dark band that
    # cannot go darker); none if even that would barely show
    best, best_score, best_seen = None, 0.0, 0.0
    for j in range(96 if spare else 48):
        u = (0.5 + A1 * (seed + j)) % 1.0
        v = (0.5 + A2 * (seed + j)) % 1.0
        ox = x0 + margin + int(round(u * span_x))
        oy = y0 + margin + int(round(v * span_y))
        pts = [(ox + dx, oy + dy, s) for dx, dy, s in cs]
        # most of the mark must land on this side's visible metal; the rest of it is left out
        ok = [p for p in pts if dentable(sheet, glow, claims, side, p[0], p[1], wide, spare)]
        if len(ok) < (0.5 if last else 0.75) * len(pts):
            continue
        pts = ok
        if any((x + ax, y + ay) in used for x, y, _ in pts for ax in range(-gap, gap + 1) for ay in range(-gap, gap + 1)):
            continue
        score = sum(abs(lum(shade(sheet.getpixel((x, y))[:3], s)) - lum(sheet.getpixel((x, y))[:3])) for x, y, s in pts) / len(pts)
        if spare and SCREEN:
            # a spare mark: where it changes the most of the side's pixels plainly as the game draws them
            seen = sum(SCREEN['weight'][y, x] for x, y, s in pts
                       if abs(lum(shade(sheet.getpixel((x, y))[:3], s)) - lum(sheet.getpixel((x, y))[:3])) * SCREEN['light'][y, x] > 24)
            if score >= (5 if last else 10) and (best is None or seen > best_seen + 1e-9 or seen >= best_seen - 1e-9 and score > best_score + 0.5):
                best, best_score, best_seen = pts, score, seen
            continue
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


MATERIAL = {}
DARK = {}


def dark(sheet, rect):
    """Whether a panel is drawn mostly in the darkest steps (where a dent can only show as a lit pixel)."""
    if rect not in DARK:
        x0, y0, x1, y1 = rect
        ls = [lum(sheet.getpixel((x, y))[:3]) for y in range(y0, y1) for x in range(x0, x1)
              if 0 <= x < sheet.width and 0 <= y < sheet.height and sheet.getpixel((x, y))[3]]
        DARK[rect] = bool(ls) and sorted(ls)[len(ls) // 2] < 60
    return DARK[rect]


def material(sheet, rect):
    """The material most of a panel is (flesh and bone take their own kind of wear)."""
    if rect in MATERIAL:
        return MATERIAL[rect]
    x0, y0, x1, y1 = rect
    n = {}
    for y in range(y0, y1):
        for x in range(x0, x1):
            if 0 <= x < sheet.width and 0 <= y < sheet.height:
                m = INDEX.get(sheet.getpixel((x, y))[:3], (None,))[0]
                n[m] = n.get(m, 0) + 1
    MATERIAL[rect] = max(n, key=n.get) if n else None
    return MATERIAL[rect]


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
def pose(frame, clip):
    """A clip's first pose: {bone: (rotation added to the bone's own, move, scale)} from its keys at time 0."""
    model = json.load(open(model_file(frame), encoding='utf-8'))
    anim = next(a for a in model.get('animations', []) if a['name'] == clip)
    out = {}
    for a in anim.get('animators', {}).values():
        r, t, sc = [0, 0, 0], [0, 0, 0], 1
        for k in a.get('keyframes', []):
            if k['time'] != 0:
                continue
            d = k['data_points'][0]
            v = [float(d.get(c) or 0) for c in 'xyz']
            if k['channel'] == 'rotation':
                r = v
            elif k['channel'] == 'position':
                # Blockbench moves a bone by -x, +y, +z of its key
                t = [-v[0], v[1], v[2]]
            elif k['channel'] == 'scale':
                sc = v[0]
        out[a['name']] = (r, t, sc)
    return out


def geometry(frame, posed=None):
    """Every cube of the frame at rest (or in a pose from pose()), in model px: (corners of each face by name, its uv,
    element). A key's rotation adds to the bone's own (checked against Blockbench's own sampling of the Hopper's
    parked knee)."""
    model = json.load(open(model_file(frame), encoding='utf-8'))
    groups = {g['uuid']: g for g in model['groups']}
    elements = {e['uuid']: e for e in model['elements']}
    posed = posed or {}
    out = []

    def walk(nodes, chain, skip):
        for n in nodes:
            if isinstance(n, dict):
                g = groups.get(n['uuid'], {})
                name = g.get('name', '')
                r, t, sc = posed.get(name, ([0, 0, 0], [0, 0, 0], 1))
                angles = [a + b for a, b in zip(g.get('rotation', [0, 0, 0]), r)]
                walk(n.get('children', []), chain + [(g.get('origin', [0, 0, 0]), rot(angles), t)],
                     skip or name.startswith('standin') or sc == 0)
            elif n in elements and not skip:
                e = elements[n]
                if e.get('type', 'cube') != 'cube':
                    continue
                full = chain + [(e.get('origin', [0, 0, 0]), rot(e.get('rotation', [0, 0, 0])), [0, 0, 0])]

                def tf(p, full=full):
                    for o, m, t in reversed(full):
                        q = apply(m, [p[i] - o[i] for i in range(3)])
                        p = [q[i] + o[i] + t[i] for i in range(3)]
                    return p
                (x0, y0, z0), (x1, y1, z1) = e['from'], e['to']
                for fname, f in e.get('faces', {}).items():
                    if f.get('texture') is None or 'uv' not in f:
                        continue
                    out.append((corner(fname, (x0, y0, z0), (x1, y1, z1)), tf, f['uv'], fname))
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
    for corner, tf, uv, _ in geo:
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
    only = [a for a in sys.argv[1:] if a in FRAMES + SHELLS]
    for frame in only or FRAMES + SHELLS:
        sheet, tiles, report = plan(frame)
        write(frame, sheet, tiles)
        if out_dir:
            os.makedirs(out_dir, exist_ok=True)
            preview(frame, sheet, tiles, out_dir)
        print('\n'.join(report))


if __name__ == '__main__':
    main()

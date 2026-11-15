"""The Jarred Head plinth's lunch bowl and spoon: geometry, texture and the spoon's tap, all from this file.

    python art/heads/plinth_bowl.py              # writes plinth_bowl.json (beside this file) and the texture
    python art/heads/plinth_bowl.py --print      # prints the numbers the renderer needs
    python art/heads/plinth_bowl.py --review DIR # also writes review models (the plinth, its jar and the bowl at each
                                                 # broth step, and the spoon at the top of its tap) into DIR

Writes
    art/heads/plinth_bowl.json                                    read by the data generator, which writes the block
                                                                  models plinth_bowl, plinth_broth_0..4, plinth_spoon
    src/main/resources/assets/headinajar/textures/block/plinth_bowl.png   (16x16, every pixel a palette key)

Where it goes, and why there (numbers from art/machines/jarred_head/jarred_geo.py):
- The jar at 1:1 stands on the plinth's top (y 7) with a 14.3 px square base: it covers the top to 0.85 px from each
  side and overhangs the plinth's front by 1.15 px, so nothing on the top can be seen. The plinth's front carries the
  plaque (x -7..2) and the push bell (x 3..7, its ding at (5, 4, -8)).
- So the bowl stands on a 1 px steel shelf bolted to the front at the viewer's left corner, over the bell plate, in
  front of the jar's base; its rim is 1 px above the plinth's top, and it reaches 2 px past the block's front.
- A 3x1x3 enamel canteen bowl, drawn as one model per broth step (0 empty, 1-4 a quarter to full): its top face is
  the inside seen from above, where the broth's surface grows from the middle out (1, 3, 6 and 9 texels), and when
  full a drip runs down its front corner. The shelf is a model of its own, drawn always.
- A 1x1x3 steel spoon, its head stained with broth, stands in it and leans back on the jar's glass (block-model
  rotation -45 degrees about X). On each tap (block event 5) it swings about the point where it rests on the glass: its lower end lifts out of the bowl
  and drops back in.

Coordinates in the JSON: the plinth block model's own px (0..16, unrotated; front = north = -Z; +X is the viewer's left
seen from the front). Kit coordinates below are the same with x and z shifted by -8 (the block's bottom centre).
"""
import argparse
import base64
import copy
import io
import json
import math
import os
import sys
import uuid as uuidlib

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
OUT_JSON = os.path.join(HERE, 'plinth_bowl.json')
OUT_PNG = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'headinajar', 'textures', 'block', 'plinth_bowl.png')
PLINTH = os.path.join(ROOT, 'art', 'machines', 'jarred_head', 'jarred_head.bbmodel')
TEXTURE = 'headinajar:block/plinth_bowl'


# ---------------------------------------------------------------------------------------------------------------------
# Palette: art/palette.json, its default set. Every opaque pixel below is a key here.
# ---------------------------------------------------------------------------------------------------------------------
def palette():
    with open(os.path.join(ROOT, 'art', 'palette.json'), encoding='utf-8') as fh:
        data = json.load(fh)
    s = data['default']
    out = {}
    for name, m in data['house'].items():
        alias = m.get('alias')
        ramp = m.get(s) or m.get('A')
        for i, h in enumerate(ramp):
            out['%s.%d' % (name, i)] = h
            if alias:
                out['%s%d' % (alias, i)] = h
    return out


PAL = palette()
KEY = {'e': 'enamel', 's': 'steel', 'f': 'flesh'}


def rgba(k):
    """'e3' -> enamel step 3 (the house ramps by their first letter here: e enamel, s steel, f flesh)."""
    h = PAL['%s.%s' % (KEY[k[0]], k[1:])]
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


# ---------------------------------------------------------------------------------------------------------------------
# Geometry (block px). The texture region of every face is given as (u0, v0, w, h) on the 16x16 sheet.
# ---------------------------------------------------------------------------------------------------------------------
SHELF = ((13, 6, -2), (16, 7, 2))          # steel shelf: on the bell plate's top (y 6), against the plinth's front
BOWL = ((13, 7, -2), (16, 8, 1))           # enamel bowl on the shelf, its back face against the jar's base (z 0.85)
# the spoon before its rotation, lying along +Z on the bowl's top, its head (the near end) in the broth; kept to the
# bowl's right-hand column so the middle (where the broth starts) stays in view. (A 2x2 head was tried: it hid the
# broth from the front.)
SPOON = ((13, 8, -0.5), (14, 9, 2.5))
SPOON_ROT = {'origin': [13.5, 8, -0.5], 'axis': 'x', 'angle': -45}   # its far end rises onto the jar's glass (z 1.5)


def spoon_rest_point():
    """The spoon's far lower edge after its rotation: where it touches the glass (its pivot for the tap)."""
    a = math.radians(SPOON_ROT['angle'])
    oy, oz = SPOON_ROT['origin'][1], SPOON_ROT['origin'][2]
    y, z = SPOON[0][1] - oy, SPOON[1][2] - oz           # the bottom edge of the far end, relative to the origin
    return [SPOON_ROT['origin'][0], round(oy + y * math.cos(a) - z * math.sin(a), 3), round(oz + y * math.sin(a) + z * math.cos(a), 3)]


# The tap, 6 ticks from block event 5: degrees about the pivot (+ lifts the lower end up and toward the front).
# Tiny part (style guide 2.4): eased out over 2 ticks, a half-tick hold, the drop eased in to the contact (1.5 ticks),
# one small bounce. 30 degrees lifts the head about 1.5 px.
TAP = [[0, 0.0, 'linear'], [2, 30.0, 'easeInOutSine'], [2.5, 30.0, 'linear'], [4, 0.0, 'easeInQuad'],
       [5, 6.0, 'easeOutQuad'], [6, 0.0, 'easeInQuad']]


def ease(name, t):
    if name == 'easeOutCubic':
        return 1 - (1 - t) ** 3
    if name == 'easeOutQuad':
        return 1 - (1 - t) ** 2
    if name == 'easeInQuad':
        return t * t
    if name == 'easeInOutSine':
        return -(math.cos(math.pi * t) - 1) / 2
    return t


def tap_at(tick):
    """The tap's angle at a (fractional) tick; the easing of a segment is on its arriving key."""
    if tick <= TAP[0][0]:
        return TAP[0][1]
    for (t0, v0, _), (t1, v1, e) in zip(TAP, TAP[1:]):
        if tick <= t1:
            return v0 + (v1 - v0) * ease(e, (tick - t0) / (t1 - t0))
    return TAP[-1][1]


# ---------------------------------------------------------------------------------------------------------------------
# The sheet. Each face is a little pixel map: rows top to bottom, palette keys separated by spaces.
# Face UV directions (vanilla block models): up u=+x v=+z; down u=+x v=-z; north u=-x; south u=+x; west u=+z;
# east u=-z; side faces v=-y. So on an up face the first row is the front (north) and the last column the viewer's
# left; the light comes from the viewer's top left, i.e. the back row and the last column.
# ---------------------------------------------------------------------------------------------------------------------
BROTH = {
    # empty: clean enamel; the near rim catches the light, the bottom a step down, the far wall lit
    0: ['e4 e4 e4',
        'e3 e2 e3',
        'e4 e4 e5'],
    # a quarter: a dark puddle in the middle
    1: ['e4 e4 e4',
        'e3 f1 e3',
        'e4 e4 e5'],
    # a half: still dark (shallow), the surface reaches the side walls
    2: ['e4 e4 e4',
        'f1 f1 f1',
        'e4 e4 e5'],
    # three quarters: deeper and lighter, up the far wall; only the near rim still shows
    3: ['e4 e4 e4',
        'f2 f2 f2',
        'f3 f3 f3'],
    # full to the brim: the rim is gone, one wet glint, and a drip down the front corner (the bowl's front, below)
    4: ['f2 f2 f2',
        'f2 f2 f2',
        'f3 f3 f4'],
}

FACES = {}   # (part, face) -> (u0, v0, rows)


def face(part, side, u0, v0, rows):
    FACES[(part, side)] = (u0, v0, [r.split() for r in rows])


for step, rows in BROTH.items():
    face('broth%d' % step, 'up', 3 * step, 0, rows)
face('bowl', 'north', 0, 3, ['e3 e3 e3'])
face('bowl', 'east', 3, 3, ['e3 e3 e3'])
face('bowl', 'west', 9, 3, ['e2 e2 e2'])
face('spill', 'north', 12, 3, ['f1 e3 e3'])                   # full to the brim: one drip down the front corner
face('shelf', 'north', 0, 4, ['s2 s1 s1'])                    # a rivet head at the viewer's left end (u0)
face('shelf', 'east', 6, 4, ['s1 s1 s1 s1'])
face('shelf', 'west', 6, 5, ['s1 s1 s1 s1'])
face('shelf', 'up', 0, 6, ['s2 s2 s2', 's2 s2 s2', 's2 s2 s2', 's2 s2 s2'])
face('shelf', 'down', 3, 6, ['s1 s1 s1', 's1 s1 s1', 's1 s1 s1', 's1 s1 s1'])
# the spoon, before its rotation lying along +Z: its 'up' face turns to face up and toward the front. Its near end is
# the head, stained with broth from lunches past; the handle runs back to the glass, lit at the top.
face('spoon', 'up', 6, 6, ['f1', 's4', 's5'])
face('spoon', 'down', 7, 6, ['f1', 's2', 's2'])
face('spoon', 'east', 8, 6, ['s4 s3 f1'])                     # u runs -z: the far (top) end first
face('spoon', 'west', 8, 7, ['f1 s2 s3'])
face('spoon', 'north', 11, 6, ['s2'])
face('spoon', 'south', 12, 6, ['s4'])


def sheet():
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    used = {}
    for (part, side), (u0, v0, rows) in FACES.items():
        for r, row in enumerate(rows):
            for c, k in enumerate(row):
                xy = (u0 + c, v0 + r)
                if xy in used and used[xy] != k:
                    raise SystemExit('sheet overlap at %s: %s and %s' % (xy, used[xy], k))
                used[xy] = k
                im.putpixel(xy, rgba(k))
    return im


def uv(part, side):
    u0, v0, rows = FACES[(part, side)]
    return [u0, v0, u0 + len(rows[0]), v0 + len(rows)]


def element(name, box, part, sides, rotation=None):
    e = {'name': name, 'from': list(box[0]), 'to': list(box[1]),
         'faces': {s: {'uv': uv(part, s), 'texture': '#bowl'} for s in sides}}
    if rotation:
        e['rotation'] = dict(rotation)
    return e


ALL = ('north', 'east', 'south', 'west', 'up', 'down')


def bowl_at(step):
    e = element('bowl_%d' % step, BOWL, 'bowl', ('north', 'east', 'west'))
    e['faces']['up'] = {'uv': uv('broth%d' % step, 'up'), 'texture': '#bowl'}
    if step == max(BROTH):
        e['faces']['north'] = {'uv': uv('spill', 'north'), 'texture': '#bowl'}
    return e


def data():
    pivot = spoon_rest_point()
    return {
        'about': 'The Jarred Head plinth\'s lunch bowl and spoon, from art/heads/plinth_bowl.py (do not edit). Block-model '
                 'elements in the plinth model\'s own px space; draw "bowl" always and exactly one "broth" step '
                 '(BowlStep 0-4); the spoon turns about "pivot" by the "tap" curve (degrees about +X, right-handed, '
                 'applied after the facing turn; + lifts its lower end up and toward the front).',
        'texture': TEXTURE,
        # faces that lie on another part (the shelf's back on the plinth, the bowl's back in the jar's base, its
        # bottom on the shelf) are left out. The bowl itself is drawn by its step: its top is the inside at that step
        # and its front shows a drip when full.
        'bowl': [element('bowl_shelf', SHELF, 'shelf', ('north', 'east', 'west', 'up', 'down'))],
        'broth': {str(s): [bowl_at(s)] for s in BROTH},
        'spoon': {'elements': [element('spoon', SPOON, 'spoon', ALL, SPOON_ROT)],
                  'pivot': pivot,
                  'tap': TAP,
                  'tap_ticks': TAP[-1][0],
                  'tap_sampled': [round(tap_at(i / 4), 3) for i in range(4 * TAP[-1][0] + 1)]},
        'shape_box': [13, 6, -2, 16, 8, 2],
        'render_reach': {'front_px': 2},
    }


# ---------------------------------------------------------------------------------------------------------------------
# Review models: the plinth's own review model (with its jar stand-in) plus these parts, one file per step and pose.
# ---------------------------------------------------------------------------------------------------------------------
NS = uuidlib.UUID('5d1c9a40-2b77-4e0c-9f3a-61b2c7d8e9f0')


def review(out_dir, png_bytes, d):
    os.makedirs(out_dir, exist_ok=True)
    with open(PLINTH, encoding='utf-8') as fh:
        base = json.load(fh)
    tex = copy.deepcopy(base['textures'][0])
    tex.update({'uuid': str(uuidlib.uuid5(NS, 'tex')), 'name': 'plinth_bowl', 'id': '1', 'path': '', 'relative_path': '',
                'width': 16, 'height': 16, 'uv_width': 16, 'uv_height': 16,
                'source': 'data:image/png;base64,' + base64.b64encode(png_bytes).decode()})
    written = []
    poses = [(s, 0.0) for s in range(5)] + [(4, max(v for _, v, _ in TAP))]
    for step, lift in poses:
        m = copy.deepcopy(base)
        m['textures'].append(copy.deepcopy(tex))
        els = d['bowl'] + d['broth'][str(step)] + d['spoon']['elements']
        for e in els:
            f = [e['from'][0] - 8, e['from'][1], e['from'][2] - 8]
            t = [e['to'][0] - 8, e['to'][1], e['to'][2] - 8]
            cube = {'uuid': str(uuidlib.uuid5(NS, '%s/%d/%s' % (e['name'], step, lift))), 'name': e['name'],
                    'origin': [0, 0, 0], 'color': 0, 'type': 'cube', 'from': f, 'to': t, 'box_uv': False,
                    'rescale': False, 'locked': False, 'visibility': True, 'autouv': 0,
                    'faces': {s: {'uv': fc['uv'], 'texture': 1} for s, fc in e['faces'].items()}}
            for s in ALL:
                cube['faces'].setdefault(s, {'uv': [0, 0, 0, 0], 'texture': None})
            if 'rotation' in e:
                r = e['rotation']
                o = [r['origin'][0] - 8, r['origin'][1], r['origin'][2] - 8]
                if lift:
                    # the tap: the rest pose turned about the pivot; as one rotation about the pivot, the box moves
                    p = d['spoon']['pivot']
                    p = [p[0] - 8, p[1], p[2] - 8]
                    a = math.radians(r['angle'])
                    by, bz = o[1] - p[1], o[2] - p[2]
                    # X' = X + (P - B) + R^-1 (B - P), so that turning X' about P equals turning X about B
                    ry = by * math.cos(-a) - bz * math.sin(-a)
                    rz = by * math.sin(-a) + bz * math.cos(-a)
                    dy, dz = (p[1] - o[1]) + ry, (p[2] - o[2]) + rz
                    cube['from'] = [f[0], f[1] + dy, f[2] + dz]
                    cube['to'] = [t[0], t[1] + dy, t[2] + dz]
                    o = p
                cube['origin'] = o
                cube['rotation'] = [r['angle'] + lift, 0, 0]
            m['elements'].append(cube)
            m['outliner'].append(cube['uuid'])
        name = 'bowl_step%d%s.bbmodel' % (step, '_tap' if lift else '')
        path = os.path.join(out_dir, name)
        with open(path, 'w', encoding='utf-8') as fh:
            json.dump(m, fh, indent=1)
        written.append(path)
    return written


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--print', action='store_true')
    ap.add_argument('--review')
    a = ap.parse_args()
    d = data()
    im = sheet()
    buf = io.BytesIO()
    im.save(buf, 'PNG', optimize=False)
    png = buf.getvalue()
    with open(OUT_PNG, 'wb') as fh:
        fh.write(png)
    with open(OUT_JSON, 'w', encoding='utf-8', newline='\n') as fh:
        json.dump(d, fh, indent=1)
        fh.write('\n')
    if a.print:
        print('pivot', d['spoon']['pivot'])
        print('tap', d['spoon']['tap'])
        print('tap_sampled', d['spoon']['tap_sampled'])
        print('shape_box', d['shape_box'])
    if a.review:
        for p in review(a.review, png, d):
            print(p)


if __name__ == '__main__':
    sys.exit(main())

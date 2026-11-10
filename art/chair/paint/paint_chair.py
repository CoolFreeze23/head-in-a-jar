"""Separation Chair (DOLORES): the painted texture sheet, placed pixel by pixel from house palette keys.

This script is the source of `textures/block/separation_chair.png` and its `_glowmask.png` (both adopted in the
texture manifest as `painted`). No randomness: every pixel comes from a rule written below, part by part.

    python -I art/chair/paint/paint_chair.py                 # writes the two PNGs into the mod assets
    python -I art/chair/paint/paint_chair.py --preview DIR   # also writes DIR/preview.png with the stand-ins in grey
    python -I art/chair/paint/paint_chair.py --debug DIR     # face-orientation test sheet (not for the mod)

It reads the cubes, sizes and box-UV offsets from `art/chair/separation_chair.bbmodel` (read only) and the colours
from `art/palette.json` (set A, the default; B and C come from `tex.py swap`).

Box-UV conventions used here (checked against the baked block model the integrator exported, and on renders):
side faces (east, north, west, south) are seen from outside with column 0 on the viewer's left and row 0 at the top;
the up face has column 0 at the cube's +X edge and row 0 at its +Z (south) edge, so painting by world position goes
through `top()` below.
"""
import argparse
import json
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..'))
BBMODEL = os.path.join(ROOT, 'art', 'chair', 'separation_chair.bbmodel')
PALETTE = os.path.join(ROOT, 'art', 'palette.json')
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'headinajar', 'textures', 'block')
W, H = 256, 128

# ------------------------------------------------------------------------------------------ palette

with open(PALETTE, encoding='utf-8') as fh:
    _pal = json.load(fh)
COL = {}
for _name, _m in _pal['house'].items():
    for _i, _h in enumerate(_m[_pal['default']]):
        COL['%s%d' % (_m['alias'], _i)] = _h
for _name, _m in _pal['shared'].items():
    for _i, _h in enumerate(_m['ramp']):
        COL['%s%d' % (_m['alias'], _i)] = _h


def rgba(key):
    """'e3' or 'e3@110' -> (r, g, b, a)."""
    a = 255
    if '@' in key:
        key, a = key.split('@')
        a = int(a)
    h = COL[key]
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


# ------------------------------------------------------------------------------------------ cubes and faces

with open(BBMODEL, encoding='utf-8') as fh:
    _bb = json.load(fh)
CUBES = {}
for _e in _bb['elements']:
    if _e.get('type', 'cube') != 'cube':
        continue
    _f, _t = _e['from'], _e['to']
    _sz = [int(round(abs(_t[i] - _f[i]))) for i in range(3)]
    _uv = _e.get('uv_offset') or [0, 0]
    CUBES[_e['name']] = dict(frm=[min(_f[i], _t[i]) for i in range(3)], w=_sz[0], h=_sz[1], d=_sz[2],
                             u=int(_uv[0]), v=int(_uv[1]))

img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
glow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
PX, GX = img.load(), glow.load()


class Face:
    """One face of one cube. f(c, r) addresses it as seen from outside: c to the viewer's right, r downward."""

    def __init__(self, cube, side):
        c = CUBES[cube]
        w, h, d, u, v = c['w'], c['h'], c['d'], c['u'], c['v']
        self.cube, self.side = cube, side
        self.x0, self.y0, self.W, self.H = {
            'east': (u, v + d, d, h),
            'north': (u + d, v + d, w, h),
            'west': (u + d + w, v + d, d, h),
            'south': (u + 2 * d + w, v + d, w, h),
            'up': (u + d, v, w, d),
            'down': (u + d + w, v, w, d),
        }[side]

    def put(self, c, r, key, lit=False):
        if not (0 <= c < self.W and 0 <= r < self.H):
            return
        x, y = self.x0 + c, self.y0 + r
        if 0 <= x < W and 0 <= y < H:
            PX[x, y] = rgba(key)
            GX[x, y] = rgba(key) if lit else (0, 0, 0, 0)

    def fill(self, key, lit=False):
        for r in range(self.H):
            for c in range(self.W):
                self.put(c, r, key, lit)

    def row(self, r, key, c0=0, c1=None):
        for c in range(c0, self.W if c1 is None else c1):
            self.put(c, r, key)

    def col(self, c, key, r0=0, r1=None):
        for r in range(r0, self.H if r1 is None else r1):
            self.put(c, r, key)

    def rows(self, keys):
        """keys: list of strings, one per row; each string is space-separated keys, one per column."""
        for r, line in enumerate(keys):
            for c, k in enumerate(line.split()):
                if k != '-':
                    self.put(c, r, k)


def faces(cube, sides=('east', 'north', 'west', 'south', 'up', 'down')):
    return [Face(cube, s) for s in sides]


def top(cube, side='up'):
    """The up (or down) face addressed by world position: t(x, z, key) with x, z the integer pixel coordinates of the
    texel's low corner in model space (Blockbench, chair facing -Z). Returns (put, xs, zs)."""
    c = CUBES[cube]
    f = Face(cube, side)
    x0, z0 = int(c['frm'][0]), int(c['frm'][2])
    xmax, zmax = x0 + c['w'] - 1, z0 + c['d'] - 1

    def put(x, z, key, lit=False):
        if side == 'up':
            f.put(xmax - x, zmax - z, key, lit)
        else:
            f.put(xmax - x, z - z0, key, lit)

    return put, range(x0, xmax + 1), range(z0, zmax + 1)


# ------------------------------------------------------------------------------------------ shared recipes

def shade_block(cube, mat, hi, mid, lo, glint=None, sides=('east', 'north', 'west', 'south')):
    """Small parts: top row hi, bottom row lo, the rest mid; on faces 2+ px wide the left column is hi down to the
    bottom row (light from the top left), and one glint pixel at the top-left corner of the north and west faces."""
    for f in faces(cube, sides):
        for r in range(f.H):
            for c in range(f.W):
                k = mid
                if r == 0:
                    k = hi
                elif r == f.H - 1 and f.H > 1:
                    k = lo
                elif c == 0 and f.W > 1:
                    k = hi
                f.put(c, r, mat + str(k))
        if glint is not None and f.side in ('north', 'west') and f.W > 1:
            f.put(0, 0, mat + str(glint))


def shade_top(cube, mat, field, nw, se, glint=None):
    """An up face: field, the north and west edges one step up, the south and east edges one step down."""
    put, xs, zs = top(cube)
    for x in xs:
        for z in zs:
            k = field
            if z == zs[0] or x == xs[0]:
                k = nw
            if z == zs[-1] or x == xs[-1]:
                k = se
            put(x, z, mat + str(k))
    if glint is not None:
        put(xs[0], zs[0], mat + str(glint))
        if len(xs) > 2:
            put(xs[1], zs[0], mat + str(glint))


def plain(cube, key, sides=('down',)):
    for f in faces(cube, sides):
        f.fill(key)


def brushed_column(f, light, dark, streaks=(), bands=()):
    """Tall 2 px faces (mast, rails, rods): column 0 `light`, column 1 `dark`; streaks are (col, r0, r1, key) runs of
    an adjacent step; bands are (r0, key0, key1) from that row down (darkening toward the foot)."""
    for r in range(f.H):
        k0, k1 = light, dark
        for b in bands:
            if r >= b[0]:
                k0, k1 = b[1], b[2]
        f.put(0, r, k0)
        if f.W > 1:
            f.put(1, r, k1)
        for c in range(2, f.W):
            f.put(c, r, k1)
    for c, r0, r1, k in streaks:
        for r in range(r0, r1):
            f.put(c, r, k)


# ------------------------------------------------------------------------------------------ the parts

def paint_base():
    # rubber feet: matte, the top step only along the upper edge
    for n in ('foot_fl', 'foot_fr', 'foot_bl', 'foot_br'):
        for f in faces(n, ('east', 'north', 'west', 'south')):
            f.fill('r1')
        plain(n, 'r2', ('up',))
        plain(n, 'r0', ('down',))

    # plinth: brushed steel slab; the 1 px edge lit at its left end, dark at its right; top lit on the north and west
    for f in faces('plinth', ('east', 'north', 'west', 'south')):
        f.fill('s3')
        f.put(0, 0, 's4')
        f.put(1, 0, 's4')
        f.put(f.W - 1, 0, 's2')
    put, xs, zs = top('plinth')
    for x in xs:
        for z in zs:
            k = 's3'
            if z == -7 or x == -7:
                k = 's4'
            if z == 6 or x == 6:
                k = 's2'
            put(x, z, k)
    for x in range(-5, 0):          # one long streak along the front margin (brushed along x)
        put(x, -6, 's4')
    for z in range(-4, 1):          # and along the left margin (brushed along z)
        put(-6, z, 's4')
    plain('plinth', 's1')

    # pedestal: in shadow under the seat
    for f in faces('pedestal'):
        f.fill('s1')
    for f in faces('pedestal', ('north', 'west')):
        f.put(0, 0, 's2')

    # seat trim: a brass band; polish on its left third, darker toward the right end
    for f in faces('seat_trim', ('east', 'north', 'west', 'south')):
        f.fill('b4')
        f.put(1, 0, 'b5')
        f.put(2, 0, 'b5')
        f.put(f.W - 2, 0, 'b3')
        f.put(f.W - 1, 0, 'b2')
    plain('seat_trim', 'b2', ('down', 'up'))

    # seat cushion: rubber-black vinyl in three channels running front to back, piped along the front edge
    put, xs, zs = top('seat')
    seams = (-3, 2)                 # seam columns (x); the channel to the right of each seam is lit on its left
    for x in xs:
        for z in zs:
            k = 'r2'
            if x in seams:
                k = 'r1'
            elif x - 1 in seams or x == -7:
                k = 'r3'
            if z == -7:
                k = 'r3'            # piping along the front
            if z == 1:
                k = 'r1'            # tucked under the backrest
            put(x, z, k)
    for f in faces('seat', ('east', 'north', 'west', 'south')):
        f.row(0, 'r3')
        f.row(1, 'r2')
        f.put(f.W - 1, 1, 'r1')
    plain('seat', 'r1')

    # footrest: a ribbed rubber footplate, ribs across the foot, stopping 1 px short of the ends
    put, xs, zs = top('footrest')
    prof = {-8: 'r2', -9: 'r1', -10: 'r3', -11: 'r1', -12: 'r3'}   # back (in the seat's shadow) to the front nosing
    for x in xs:
        for z in zs:
            k = prof[z]
            if x in (-7, 6) and z not in (-12,):
                k = 'r2'
            put(x, z, k)
    for f in faces('footrest', ('east', 'north', 'west', 'south')):
        f.fill('r2')
        f.put(0, 0, 'r3')
        f.put(f.W - 1, 0, 'r1')
    plain('footrest', 'r0')

    for f in faces('footrest_strut', ('east', 'north', 'west', 'south')):
        f.rows(['s3 s3 s3 s2', 's2 s1 s1 s1'])
    plain('footrest_strut', 's2', ('up',))
    plain('footrest_strut', 's1')


def paint_backrest():
    # front: an enamel shell framing a channel-tufted ivory pad (three channels, rounded, lit from the left)
    f = Face('backrest', 'north')          # 14 x 12, col 0 at the patient's right (+X)
    chan = ['e4', 'e3', 'e3', 'e2']         # one channel across: lit left lip, field, shadowed right lip
    low = ['e3', 'e3', 'e2', 'e1']          # the lower band, a step darker (faces darken downward)
    for r in range(12):
        for c in range(14):
            if r == 0:
                k = 'e4'
            elif r == 11:
                k = 'e2'
            elif c == 0:
                k = 'e4'
            elif c == 13:
                k = 'e2'
            elif r == 1:
                k = 'e2'                    # recessed: the pad's top row in the frame's shadow
            else:
                k = (chan if r < 7 else low)[(c - 1) % 4]
                if r == 10:
                    k = 'e2' if k != 'e2' else 'e1'   # the tuck at the foot of the pad
            f.put(c, r, k)
    f.put(0, 0, 'e5')
    f.put(1, 0, 'e5')
    f.put(1, 2, 'e5')                       # one glint on the first channel's lit lip

    # back: the enamel shell with the maker's plate (Noggin & Sons, monogram N.S)
    b = Face('backrest', 'south')           # 14 x 12, col 0 at -X; the blade spine covers cols 6-7, rows 0-3
    for r in range(12):
        for c in range(14):
            k = 'e3'
            if r == 0 or c == 0:
                k = 'e4'
            if r == 11 or c == 13:
                k = 'e2'
            b.put(c, r, k)
    b.put(0, 0, 'e5')
    # the plate: brass rim (lit top and left), black enamel field, cream monogram "N.S" (Noggin & Sons; an
    # ampersand cannot be drawn in the 1 px between the letters at 16 px to a block, so it is a brass interpunct).
    # The plate's top rim runs under the blade spine (cols 6-7), as if the spine bracket were bolted through it.
    for r in range(3, 12):
        for c in range(1, 13):
            if r == 3 or c == 1:
                k = 'b4'
            elif r == 11 or c == 12:
                k = 'b1'
            else:
                k = 'r1'
            b.put(c, r, k)
    b.put(1, 3, 'b5')
    b.put(2, 3, 'b5')
    b.put(12, 3, 'b3')
    b.put(1, 11, 'b2')
    N = ['x..x', 'xx.x', 'x.xx', 'x..x', 'x..x']
    S = ['xxx', 'x..', 'xxx', '..x', 'xxx']
    for r in range(5):
        for c in range(4):
            if N[r][c] == 'x':
                b.put(2 + c, 5 + r, 'e5' if r < 3 else 'e4')
        for c in range(3):
            if S[r][c] == 'x':
                b.put(9 + c, 5 + r, 'e5' if r < 3 else 'e4')
    b.put(7, 9, 'b4')                      # the interpunct, on the baseline
    # the field's own shading: its top row in the rim's shadow
    for c in range(2, 12):
        if PX[b.x0 + c, b.y0 + 4][:3] == rgba('r1')[:3]:
            b.put(c, 4, 'r0')

    # sides: enamel, three bands darkening downward, lit front edge
    for side in ('east', 'west'):
        s = Face('backrest', side)          # 3 x 12
        for r in range(12):
            band = 'e4' if r < 4 else ('e3' if r < 9 else 'e2')
            for c in range(3):
                s.put(c, r, band)
        s.put(0, 0, 'e5')
        s.col(0 if side == 'west' else 2, 'e4', 4, 9)   # the front edge (toward -Z) one step up in the middle band
        s.row(11, 'e1')
    plain('backrest', 'e3', ('up',))
    plain('backrest', 'e1')

    # brass cap along the top of the backrest
    put, xs, zs = top('backrest_cap')
    for x in xs:
        for z in zs:
            put(x, z, {2: 'b5', 3: 'b4', 4: 'b3'}[z])
    for (x, z) in ((-5, 3), (-4, 3), (-6, 4), (-5, 4)):
        put(x, z, 'b5')                      # one short diagonal polish band near the left end
    put(6, 2, 'b4')
    put(6, 3, 'b3')
    put(6, 4, 'b2')
    for f in faces('backrest_cap', ('east', 'north', 'west', 'south')):
        f.fill('b3')
        f.put(0, 0, 'b5')
        f.put(1, 0, 'b4')
        f.put(2, 0, 'b4')
        f.put(f.W - 1, 0, 'b2')
    plain('backrest_cap', 'b1')

    # the spine behind the backrest (carries the blade hub)
    for f in faces('spine', ('east', 'north', 'west', 'south')):
        brushed_column(f, 's4', 's3', bands=((3, 's3', 's2'),))
    shade_top('spine', 's', 3, 4, 2)
    plain('spine', 's1')


def paint_mast():
    # brass foot and bracket
    shade_block('mast_foot', 'b', 5, 4, 2)
    shade_top('mast_foot', 'b', 4, 5, 3, glint=5)
    plain('mast_foot', 'b1')
    for n in ('mast_bracket', 'post_bracket', 'rail_cap'):
        shade_block(n, 'b', 5, 4, 2)
        shade_top(n, 'b', 4, 5, 3)
        plain(n, 'b1')

    # the fixed mast: brushed steel, 27 px, three bands darkening toward the foot, placed streaks
    for f in faces('mast', ('east', 'north', 'west', 'south')):
        brushed_column(f, 's4', 's3',
                       streaks=((1, 3, 8, 's4'), (0, 12, 16, 's3'), (1, 17, 21, 's2')),
                       bands=((10, 's4', 's3'), (22, 's3', 's2')))
        f.put(0, 0, 's5')
        f.row(f.H - 1, 's1')
    shade_top('mast', 's', 4, 5, 3)
    plain('mast', 's1')

    # the sliding rail: the same steel with a rack (a dark notch every third row on the right column)
    for f in faces('lift_rail', ('east', 'north', 'west', 'south')):
        brushed_column(f, 's4', 's3', bands=((9, 's3', 's2'),))
        if f.side in ('north', 'south'):
            for r in range(1, f.H - 1, 3):
                f.put(1, r, 's1')
        f.put(0, 0, 's5')
    shade_top('lift_rail', 's', 4, 5, 3)
    plain('lift_rail', 's1')

    # pivot pin: a steel boss
    for f in faces('pivot_pin', ('east', 'north', 'west', 'south')):
        f.rows(['s4 s3', 's3 s1'])
    shade_top('pivot_pin', 's', 4, 5, 3)
    plain('pivot_pin', 's1')


def paint_hood():
    # beam: ivory enamel
    shade_block('beam', 'e', 4, 3, 2, glint=5)
    shade_top('beam', 'e', 4, 5, 3)
    plain('beam', 'e1')

    # the dryer dome. Sides: a lit bevel, the enamel field, a polished steel rim along the bottom (a 1950s dryer's
    # chrome band). The front carries the maker's badge, the one brass thing on the hood's face.
    for side in ('east', 'north', 'west', 'south'):
        f = Face('dome', side)              # 12 x 3
        f.row(0, 'e4')
        f.row(1, 'e3')
        f.row(2, 's3')
        f.put(f.W - 1, 1, 'e2')
        f.put(f.W - 1, 2, 's2')
        if side in ('north', 'west'):
            f.put(0, 0, 'e5')
            f.put(1, 0, 'e5')
            for c in range(0, 4):
                f.put(c, 2, 's4')
            f.put(1, 2, 's5')
        if side == 'north':
            # the badge: a 4 x 2 brass plate over the band, lit top-left, shadowed bottom-right
            f.put(4, 0, 'e3')               # the badge's shadow on the bevel above it
            f.rows(['-', '- - - - b5 b4 b4 b3', '- - - - b4 b3 b3 b2'])
    put, xs, zs = top('dome')
    for x in xs:
        for z in zs:
            k = 'e4'
            if z == -6 or x == -6:
                k = 'e5' if (z == -6 and x < -2) or (x == -6 and z < -2) else 'e4'
            if z == 5 or x == 5:
                k = 'e3'
            put(x, z, k)
    put, xs, zs = top('dome', 'down')
    for x in xs:
        for z in zs:
            k = 'r2'
            if z in (-6, 5) or x in (-6, 5):
                k = 'e1'
            put(x, z, k)
    for x in range(-1, 1):                  # four radial vent slots in the hood's throat
        for z in (-5, -4, 3, 4):
            put(x, z, 'r0')
    for z in range(-1, 1):
        for x in (-5, -4, 3, 4):
            put(x, z, 'r0')

    # the brass cap: rim lit north and west, dark south and east, one hard polish band (b5 then b4) across the
    # upper-left, a steel terminal in the middle (where the handshake sparks come from)
    put, xs, zs = top('dome_cap')
    for x in xs:
        for z in zs:
            k = 'b4'
            if z == -4 or x == -4:
                k = 'b5'
            if z == 3 or x == 3:
                k = 'b2'
            put(x, z, k)
    for x in xs:
        for z in zs:
            s_ = x + z
            if s_ in (-5, -4) and -4 < x < 3 and -4 < z < 3:
                put(x, z, 'b5')
            if s_ == -3 and -4 < x < 3 and -4 < z < 3:
                put(x, z, 'b3')
    put(-4, -4, 'b5')
    put(3, -4, 'b3')
    put(-4, 3, 'b3')
    for (x, z, k) in ((-1, -1, 's4'), (0, -1, 's3'), (-1, 0, 's3'), (0, 0, 's1')):
        put(x, z, k)
    for f in faces('dome_cap', ('east', 'north', 'west', 'south')):
        f.fill('b4')
        f.put(0, 0, 'b5')
        f.put(1, 0, 'b5' if f.side in ('north', 'west') else 'b4')
        f.put(f.W - 2, 0, 'b3')
        f.put(f.W - 1, 0, 'b2')
    plain('dome_cap', 'b1')

    # rods behind the head
    for n in ('rod_l', 'rod_r'):
        for f in faces(n, ('east', 'north', 'west', 'south')):
            brushed_column(f, 's4', 's3', bands=((5, 's3', 's2'),))
            f.put(0, 0, 's5')
        shade_top(n, 's', 3, 4, 2)
        plain(n, 's1')

    # the scan ring: teal glass, opaque frame; top lightest, outer faces with a short shine, inner faces darker
    ring = {'ring_f': 'north', 'ring_b': 'south', 'ring_l': 'west', 'ring_r': 'east'}
    for n, outer in ring.items():
        for f in faces(n, ('east', 'north', 'west', 'south')):
            if f.W * f.H == 1:
                f.fill('t3')
                continue
            if f.side == outer:
                f.fill('t3')
                for c in range(1, 4):
                    f.put(c, 0, 't4')       # shine streak at the left end
                f.put(f.W - 3, 0, 't4')
                f.put(f.W - 2, 0, 't4')     # and a short one at the right (opposite corner)
            else:
                f.fill('t2')
        plain(n, 't4', ('up',))
        plain(n, 't1', ('down',))
    for f in faces('scan_sheet', ('up', 'down')):
        for r in range(f.H):
            for c in range(f.W):
                k = 't3@170' if r in (0, f.H - 1) or c in (0, f.W - 1) else 't1@110'
                f.put(c, r, k)
        for c in range(1, f.W - 1):
            f.put(c, 4, 't2@170')           # one bright scan line across the middle

    # the claw plate and its yellow temple jaws, rubber pads on the inner faces
    shade_block('claw_plate', 's', 4, 3, 2)
    shade_top('claw_plate', 's', 3, 4, 2)
    for f in faces('claw_plate', ('down',)):
        f.fill('s2')
        f.row(0, 's1')
        f.col(f.W - 1, 's1')
    for n in ('jaw_l_top', 'jaw_r_top'):
        shade_block(n, 'w', 3, 2, 1)
        shade_top(n, 'w', 2, 3, 1)
        plain(n, 'w1')
    for n, inner in (('jaw_l_tip', 'east'), ('jaw_r_tip', 'west')):
        for f in faces(n, ('east', 'north', 'west', 'south')):
            for r in range(f.H):
                k = 'w3' if r == 0 else ('w2' if r < 4 else 'w1')
                for c in range(f.W):
                    f.put(c, r, k)
            if f.side == inner:
                f.rows(['w3 w3', 'w2 w2', 'r3 r3', 'r2 r2', 'r2 r1', 'r1 r1'])
        plain(n, 'w1', ('down', 'up'))


def paint_barrier():
    # post: warning yellow over a black kick band at the foot
    for f in faces('barrier_post', ('east', 'north', 'west', 'south')):
        for r in range(f.H):
            if r < 3:
                ks = ('w3', 'w2')
            elif r < 9:
                ks = ('w2', 'w2')
            elif r == 9:
                ks = ('w1', 'w1')
            else:
                ks = ('r2', 'r1') if r == 10 else ('r1', 'r1')
            f.put(0, r, ks[0])
            f.put(1, r, ks[1])
    shade_top('barrier_post', 'w', 2, 3, 1)
    plain('barrier_post', 'r0')

    shade_block('post_foot', 's', 3, 2, 1)
    shade_top('post_foot', 's', 3, 4, 2)
    plain('post_foot', 's1')

    # lamp plate: matte black housing; the lit seams between lenses one step up
    for f in faces('lamp_plate', ('east', 'north', 'west', 'south')):
        f.fill('r1')
        f.row(0, 'r3')
        f.col(0, 'r2', 1)
    shade_top('lamp_plate', 'r', 2, 3, 1)
    plain('lamp_plate', 'r0')

    # dark lenses: the indicator ramp's two darkest steps, a 1 px glint top-left
    for n, m in (('lens_green', 'G'), ('lens_amber', 'A'), ('lens_red', 'R')):
        for f in faces(n, ('north',)):
            f.rows(['%s1 %s0' % (m, m), '%s0 %s0' % (m, m)])
        for f in faces(n, ('east', 'west', 'south', 'up', 'down')):
            f.fill('r0')
        # lit caps: a pale hued core top-left, rim steps 2 and 1; every pixel glows
        cap = n.replace('lens_', 'lamp_') + '_lit'
        for f in faces(cap, ('north',)):
            for (c, r, k) in ((0, 0, 3), (1, 0, 2), (0, 1, 2), (1, 1, 1)):
                f.put(c, r, '%s%d' % (m, k), lit=True)
        for f in faces(cap, ('east', 'west', 'up', 'down', 'south')):
            f.fill(m + '1', lit=True)

    # hub and counterweight: steel; the weight cast and darker, one bolt head
    for f in faces('barrier_hub', ('east', 'north', 'west', 'south')):
        f.rows(['s4 s3', 's3 s1'])
    shade_top('barrier_hub', 's', 3, 4, 2)
    plain('barrier_hub', 's1')
    for f in faces('counterweight', ('east', 'north', 'west', 'south')):
        f.rows(['s3 s3 s2', 's2 s2 s1', 's2 s2 s1', 's1 s1 s0'])
        f.put(1, 1, 's4')
        f.put(2, 2, 's0')
    shade_top('counterweight', 's', 2, 3, 1)
    plain('counterweight', 's0')

    # the arm: a level-crossing boom, banded across its length (yellow 4, black 3), each band lit along its upper
    # row; the bands run unbroken round all four faces. Diagonals were tried first: on a 2 px pole they broke into
    # a staircase of blotches.
    bands = ['w3', 'w2', 'w2', 'w1', 'r2', 'r1', 'r1',
             'w3', 'w2', 'w2', 'w1', 'r2', 'r1', 'r1',
             'w3', 'w2', 'w2']
    for f in faces('barrier_arm', ('east', 'north', 'west', 'south')):
        for r in range(f.H):
            for c in range(f.W):
                k = bands[r]
                if c == f.W - 1 and k in ('w2', 'w3'):
                    k = 'w2' if k == 'w3' else 'w2'
                f.put(c, r, k)
    shade_top('barrier_arm', 'w', 2, 3, 1)
    plain('barrier_arm', 'w1')

    # the rubber pad along the arm: ribs every third px with a lit line
    for f in faces('barrier_pad', ('east', 'north', 'west', 'south')):
        for r in range(f.H):
            f.put(0, r, ('r1', 'r3', 'r2')[r % 3])
    plain('barrier_pad', 'r2', ('up', 'down'))


def paint_curtain():
    """The privacy curtain hangs from its rail (the z = -3 edge) toward a hem (z = -9); pleats run along the hang,
    so on its big faces they are rows. Deployed, the rows become vertical folds."""
    # Two broad folds across the curtain's width (rows: crest 2, flank 1, trough 1), deepening toward the hem, under a
    # heading tape. (A version whose folds drifted a row partway down read as mottled stone, so the folds run straight.)
    upper = 'cc' + 'ft' + 'cc' + 'ft'
    lower = 't' + 'cc' + 'ft' + 'cc' + 'f'
    tone = {
        0: {'c': 'e4', 'f': 'e4', 't': 'e3'},    # heading tape under the rail
        1: {'c': 'e4', 'f': 'e3', 't': 'e2'},
        2: {'c': 'e4', 'f': 'e3', 't': 'e2'},
        3: {'c': 'e4', 'f': 'e3', 't': 'e2'},
        4: {'c': 'e3', 'f': 'e3', 't': 'e2'},
        5: {'c': 'e3', 'f': 'e2', 't': 'e1'},    # the hem
    }
    prof = upper
    for side in ('east', 'west'):
        f = Face('curtain', side)           # 6 x 8; east: col 0 at the rail; west: col 5 at the rail
        for r in range(8):
            for c in range(6):
                d = c if side == 'east' else 5 - c        # 0 at the rail, 5 at the hem
                f.put(c, r, tone[d][upper[r]])
    for side in ('north', 'south'):
        f = Face('curtain', side)           # 1 x 8
        for r in range(8):
            f.put(0, r, {'c': 'e3', 'f': 'e2', 't': 'e1'}[prof[r]])
    for f in faces('curtain', ('up', 'down')):
        for r in range(f.H):
            f.put(0, r, 'e1' if r in (0, f.H - 1) else 'e2')

    # brass rail: a rod, lit on the north and west, with three ring glints
    for f in faces('curtain_rail', ('east', 'north', 'west', 'south')):
        lit = f.side in ('north', 'west')
        f.fill('b4' if lit else 'b3')
        f.put(0, 0, 'b3' if lit else 'b2')
        f.put(0, f.H - 1, 'b3' if lit else 'b2')
        if lit:
            for r in (2, 5, 8):
                f.put(0, r, 'b5')
    plain('curtain_rail', 'b3', ('up', 'down'))


def paint_blade():
    # the hub is the yellow guard
    for f in faces('blade_hub', ('east', 'north', 'west', 'south')):
        f.rows(['w3 w2', 'w2 w2', 'w1 w1'])
    shade_top('blade_hub', 'w', 2, 3, 1)
    plain('blade_hub', 'w1')
    shade_block('blade_shank', 's', 3, 2, 1)
    shade_top('blade_shank', 's', 3, 4, 2)
    plain('blade_shank', 's1')

    # the blade: polished steel. A hard bright cutting edge (north, z = 3), a lit bevel behind it, a darker body
    # with one brushed streak and a 2 px glint, the back darkest, and a cleaver's hanging hole near the tip. The
    # light edge against the darker body is what says "blade" rather than "shelf".
    for side in ('up', 'down'):
        put, xs, zs = top('blade', side)
        for x in xs:
            for z in zs:
                put(x, z, {3: 's5', 4: 's4', 5: 's2', 6: 's2', 7: 's2', 8: 's1'}[z])
        for x in (5, 6, 7, 8):
            put(x, 6, 's3')                 # brushed streak along the length
        if side == 'up':
            put(5, 5, 's4')
            put(6, 5, 's5')                 # the glint, where the bevel meets the body
        for (x, z, k) in ((9, 6, 'r0'), (10, 6, 'r0'), (9, 7, 'r0'), (10, 7, 's3')):
            put(x, z, k)                    # the hole, its far lower lip catching the light
    f = Face('blade', 'north')              # the edge itself, 8 x 1
    f.fill('s5')
    f.put(f.W - 1, 0, 's4')
    f = Face('blade', 'south')              # the back
    f.fill('s1')
    plain('blade', 's2', ('east',))
    plain('blade', 's1', ('west',))

    for f in faces('blade_spine', ('east', 'north', 'west', 'south')):
        f.fill('s2')
        f.put(0, 0, 's3')
    plain('blade_spine', 's4', ('up',))
    plain('blade_spine', 's1', ('down',))


def paint_tray():
    # the dock tray: rubber, raised rim, yellow corner marks where the jar goes, a brass pull on its leading edge
    put, xs, zs = top('tray')
    for x in xs:
        for z in zs:
            k = 'r2'
            if z == -5 or x == -5:
                k = 'r3'
            if z == 4 or x == 4:
                k = 'r1'
            put(x, z, k)
    for (cx, cz, dx, dz) in ((-4, -4, 1, 1), (3, -4, -1, 1), (-4, 3, 1, -1), (3, 3, -1, -1)):
        put(cx, cz, 'w2')
        put(cx + dx, cz, 'w2')
        put(cx, cz + dz, 'w2')
    for f in faces('tray', ('east', 'north', 'west', 'south')):
        f.fill('r2')
        f.put(0, 0, 'r3')
        f.put(f.W - 1, 0, 'r1')
    f = Face('tray', 'west')
    f.rows(['- - - b4 b3 b3 b2'])
    plain('tray', 'r0')


def paint_debug():
    """Orientation test: every face a flat tone per side, its top-left texel red, its top row lighter."""
    tone = {'north': 'e4', 'south': 'e2', 'east': 's3', 'west': 's1', 'up': 'w3', 'down': 'r2'}
    for n in CUBES:
        if n.startswith('standin'):
            continue
        for f in faces(n):
            f.fill(tone[f.side])
            f.put(0, 0, 'R2')
    for n in ('dome', 'blade', 'seat', 'tray'):
        put, xs, zs = top(n)
        for x in xs:
            put(x, zs[0], 'G2')             # north edge of the up face green
        for z in zs:
            put(xs[0], z, 'A2')             # west edge amber


def paint_all():
    paint_base()
    paint_backrest()
    paint_mast()
    paint_hood()
    paint_barrier()
    paint_curtain()
    paint_blade()
    paint_tray()


def check():
    """Every painted cube face is covered; stand-ins left clear."""
    missing = []
    for n in CUBES:
        if n.startswith('standin'):
            continue
        for f in faces(n):
            for r in range(f.H):
                for c in range(f.W):
                    if PX[f.x0 + c, f.y0 + r][3] == 0:
                        missing.append((n, f.side))
                        break
                else:
                    continue
                break
    return sorted(set(missing))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview', help='also write DIR/preview.png with the stand-ins filled grey')
    ap.add_argument('--debug', help='write DIR/debug.png (orientation test) and nothing else')
    ap.add_argument('--out', default=OUT)
    a = ap.parse_args()
    if a.debug:
        paint_debug()
        img.save(os.path.join(a.debug, 'debug.png'))
        return 0
    paint_all()
    miss = check()
    if miss:
        sys.stderr.write('unpainted faces: %s\n' % miss)
        return 1
    img.save(os.path.join(a.out, 'separation_chair.png'))
    glow.save(os.path.join(a.out, 'separation_chair_glowmask.png'))
    if a.preview:
        pv = img.copy()
        p = pv.load()
        for n in CUBES:
            if n.startswith('standin'):
                for f in faces(n):
                    for r in range(f.H):
                        for c in range(f.W):
                            # the patient in flat grey; the jar stand-ins stay clear (in a single-clip render the
                            # tray is never out, so the jar would stand under the seat)
                            p[f.x0 + c, f.y0 + r] = (150, 150, 150, 255) if 'jar' not in n else (0, 0, 0, 0)
        pv.save(os.path.join(a.preview, 'preview.png'))
    print('wrote separation_chair.png and separation_chair_glowmask.png to %s' % a.out)
    return 0


if __name__ == '__main__':
    sys.exit(main())

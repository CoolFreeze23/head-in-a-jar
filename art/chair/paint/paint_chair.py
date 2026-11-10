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

SIDES = ('east', 'north', 'west', 'south')


def block(cube, mat, field, hi, lo, glint=None, sides=SIDES):
    """Small parts: the top row one step lit, the bottom row and (on faces 3+ px wide) the right column dark, the field
    between; one glint pixel at the top-left of the north and west faces when they are 3+ px wide. No lit patches."""
    for f in faces(cube, sides):
        for r in range(f.H):
            for c in range(f.W):
                k = field
                if f.H > 1 and r == f.H - 1:
                    k = lo
                elif f.W >= 3 and c == f.W - 1:
                    k = lo
                elif r == 0:
                    k = hi
                f.put(c, r, mat + str(k))
        if glint is not None and f.side in ('north', 'west') and f.W >= 3:
            f.put(0, 0, mat + str(glint))


def shade_top(cube, mat, field, nw, se, glint=None):
    """An up face: field, the north and west edges one step up, the south and east edges darker."""
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


def plain(cube, key, sides=('down',)):
    for f in faces(cube, sides):
        f.fill(key)


def brass(cube, sides=SIDES, top_too=True, down='b1'):
    """The house brass recipe (style guide 1.4): field b3, lit top row b4, a 1 px b1 rim along the bottom and right,
    on faces 6+ px wide one diagonal polish band (2 px, b4) with b5 at its upper end."""
    for f in faces(cube, sides):
        for r in range(f.H):
            for c in range(f.W):
                k = 'b3'
                dark = 'b1' if f.H >= 3 else 'b2'   # on 1-2 px faces a b1 row would split the part in half
                if f.H > 1 and r == f.H - 1:
                    k = dark
                elif f.W >= 3 and c == f.W - 1:
                    k = dark if f.H > 1 else 'b2'
                elif r == 0:
                    k = 'b5' if f.H > 1 else 'b3'
                f.put(c, r, k)
        if f.W >= 6:
            c0 = 2
            if f.H == 1:
                f.put(c0, 0, 'b5')
                f.put(c0 + 1, 0, 'b4')
            else:
                for r in range(f.H - 1):
                    f.put(c0 + (f.H - 2 - r), r, 'b4')
                    f.put(c0 + (f.H - 2 - r) + 1, r, 'b4')
                f.put(c0 + f.H - 2, 0, 'b5')
        elif f.side in ('north', 'west') and f.W >= 2:
            f.put(0, 0, 'b5')
    if top_too:
        shade_top(cube, 'b', 3, 5, 1)
    plain(cube, down)


def brushed_column(f, light, dark, streaks=(), bands=()):
    """Tall 2 px faces (mast, rails, rods): one field tone across the face (a lit-left/dark-right column pair was
    tried and read as pinstripes once the four faces sat side by side); streaks are (col, r0, r1, key) runs of an
    adjacent step, 3-8 px long; bands are (r0, key0, key1) from that row down (darkening toward the foot; key1 is
    kept for the call signature and ignored)."""
    for r in range(f.H):
        k0, k1 = light, dark
        for b in bands:
            if r >= b[0]:
                k0, k1 = b[1], b[2]
        for c in range(f.W):
            f.put(c, r, k0)
    for c, r0, r1, k in streaks:
        for r in range(r0, r1):
            f.put(c, r, k)


# ------------------------------------------------------------------------------------------ the parts

def paint_base():
    # rubber feet: matte, the top step only along the upper edge
    for n in ('foot_fl', 'foot_fr', 'foot_bl', 'foot_br'):
        for f in faces(n, SIDES):
            f.fill('r1')
        plain(n, 'r2', ('up',))
        plain(n, 'r0', ('down',))

    # plinth: company steel (darker and bluer than iron); the 1 px edge lit at its left end, dark at its right
    for f in faces('plinth', SIDES):
        f.fill('s2')
        f.put(0, 0, 's3')
        f.put(1, 0, 's3')
        f.put(f.W - 1, 0, 's1')
    put, xs, zs = top('plinth')
    for x in xs:
        for z in zs:
            k = 's2'
            if z == -7 or x == -7:
                k = 's3'
            if z == 6 or x == 6:
                k = 's1'
            put(x, z, k)
    for x in range(-5, 0):          # brushed streaks along the exposed margins
        put(x, -6, 's3')
    for z in range(-4, 1):
        put(-6, z, 's3')
    for (x, z) in ((-6, -6), (4, -6)):     # a pair of rivets at the front corners: lit head, shadow below-right
        put(x, z, 's4')
        put(x + 1, z + 1, 's1')
    plain('plinth', 's0')

    for f in faces('pedestal'):     # in shadow under the seat
        f.fill('s1')

    # seat trim: a 1 px brass band
    brass('seat_trim')
    plain('seat_trim', 'b1', ('up',))

    # seat cushion: rubber-black vinyl in three channels running front to back, piped along the front edge
    put, xs, zs = top('seat')
    seams = (-3, 2)
    for x in xs:
        for z in zs:
            k = 'r2'
            if x in seams:
                k = 'r1'
            elif x - 1 in seams or x == -7:
                k = 'r3'
            if z == -7:
                k = 'r3'
            if z == 1:
                k = 'r1'
            put(x, z, k)
    for f in faces('seat', SIDES):
        f.row(0, 'r3')
        f.row(1, 'r2')
        f.put(f.W - 1, 1, 'r1')
    plain('seat', 'r1')

    # footrest: a ribbed rubber footplate, ribs across the foot, stopping 1 px short of the ends
    put, xs, zs = top('footrest')
    prof = {-8: 'r2', -9: 'r1', -10: 'r3', -11: 'r1', -12: 'r3'}
    for x in xs:
        for z in zs:
            k = prof[z]
            if x in (-7, 6) and z != -12:
                k = 'r2'
            put(x, z, k)
    for f in faces('footrest', SIDES):
        f.fill('r2')
        f.put(0, 0, 'r3')
        f.put(f.W - 1, 0, 'r1')
    plain('footrest', 'r0')

    block('footrest_strut', 's', 2, 3, 1)
    plain('footrest_strut', 's2', ('up',))
    plain('footrest_strut', 's1')


def paint_backrest():
    # front: an ivory enamel shell holding a black tuck-and-roll pad (horizontal rolls: lit top line, body, groove),
    # set in: the pad's left column one step into shadow
    f = Face('backrest', 'north')           # 14 x 12, col 0 at the patient's right (+X)
    roll = {0: 'r3', 1: 'r2', 2: 'r1'}   # a lit roll top, the roll, its shadow seam
    for r in range(12):
        for c in range(14):
            if r == 0:
                k = 'e4'
            elif r == 11:
                k = 'e1'
            elif c == 0:
                k = 'e4'
            elif c == 13:
                k = 'e2'
            elif r in (1, 10):
                k = 'r1'                    # the pad tucked into the frame at top and bottom
            else:
                k = roll[(r - 2) % 3]
            f.put(c, r, k)
    f.put(0, 0, 'e5')
    f.put(1, 0, 'e5')

    # back: the enamel shell (house casing: lit top and left, dark bottom and right) with the maker's plate
    b = Face('backrest', 'south')           # 14 x 12, col 0 at -X; the blade spine covers cols 6-7, rows 0-3
    for r in range(12):
        for c in range(14):
            k = 'e3'
            if r == 0 or c == 0:
                k = 'e4'
            if c == 13:
                k = 'e2'
            if r == 11:
                k = 'e1'
            b.put(c, r, k)
    b.put(0, 0, 'e5')
    b.put(1, 0, 'e5')
    # Noggin & Sons plate: 10 x 5 brass, set in an e1 seam, two rivets, two engraved lines (the name and the small
    # print). Letters were tried at 3x5 and read as a pixel-font sticker; at 16 px to a block a plate says "maker's
    # plate" by its shape, and the name goes in the tooltip.
    for r in range(4, 11):
        for c in range(1, 13):
            b.put(c, r, 'e1' if (r == 10 or c == 12) else 'e4')      # a lit panel the plate sits on
    for r in range(5, 10):
        for c in range(2, 12):
            k = 'b3'
            if r == 9 or c == 11:
                k = 'b0'
            elif r == 5 or c == 2:
                k = 'b5'
            b.put(c, r, k)
    b.put(2, 9, 'b1')
    b.put(11, 5, 'b1')
    for c in (3, 10):                       # rivets: lit head, its shadow below-right (T18)
        b.put(c, 6, 'b5')
    b.put(4, 7, 'b1')
    b.put(10, 7, 'b1')
    for c in range(5, 9):
        b.put(c, 7, 'b1')                   # the name, engraved
    for c in range(6, 9):
        b.put(c, 8, 'b2')                   # the small print

    # sides: enamel, three bands darkening downward
    for side in ('east', 'west'):
        s = Face('backrest', side)          # 3 x 12
        for r in range(12):
            band = 'e4' if r < 4 else ('e3' if r < 9 else 'e2')
            for c in range(3):
                s.put(c, r, band)
        s.put(0 if side == 'west' else 2, 0, 'e5')
        s.row(11, 'e1')
    plain('backrest', 'e3', ('up',))
    plain('backrest', 'e1')

    # brass cap along the top of the backrest
    brass('backrest_cap', top_too=False)
    put, xs, zs = top('backrest_cap')
    for x in xs:
        for z in zs:
            put(x, z, {2: 'b4', 3: 'b3', 4: 'b1'}[z])
    put(6, 2, 'b2')
    put(6, 3, 'b1')
    for (x, z, k) in ((-4, 2, 'b5'), (-3, 2, 'b5'), (-5, 3, 'b4'), (-4, 3, 'b4')):
        put(x, z, k)                        # one short diagonal polish band near the left end

    # the spine behind the backrest (carries the blade hub)
    for f in faces('spine', SIDES):
        brushed_column(f, 's3', 's3', bands=((3, 's2', 's2'),))
        f.row(0, 's4')
    shade_top('spine', 's', 2, 3, 1)
    plain('spine', 's1')


def paint_mast():
    for n in ('mast_foot', 'mast_bracket', 'post_bracket', 'rail_cap'):
        brass(n)

    # the fixed mast: company steel, 27 px; lit left column, dark right column, placed streaks, darkening downward
    for f in faces('mast', SIDES):
        brushed_column(f, 's3', 's3',
                       streaks=((0, 3, 9, 's4'), (1, 11, 16, 's2'), (0, 17, 21, 's2')),
                       bands=((22, 's2', 's2'),))
        f.row(0, 's4')
        f.row(f.H - 1, 's1')
    shade_top('mast', 's', 3, 4, 2)
    plain('mast', 's1')

    # the sliding rail: the same steel; on its front and back a rack set in a channel (right column): a steel.1
    # floor with a lit tooth every third row
    for f in faces('lift_rail', SIDES):
        brushed_column(f, 's3', 's3', streaks=((0, 2, 6, 's4'),), bands=((9, 's2', 's2'),))
        f.row(0, 's4')
        if f.side in ('north', 'south'):
            for r in range(1, f.H):
                f.put(1, r, 's3' if r % 3 == 1 else 's1')
    shade_top('lift_rail', 's', 3, 4, 2)
    plain('lift_rail', 's1')

    for f in faces('pivot_pin', SIDES):
        f.rows(['s3 s2', 's2 s1'])
    shade_top('pivot_pin', 's', 3, 4, 2)
    plain('pivot_pin', 's1')


def paint_hood():
    block('beam', 'e', 3, 4, 1, glint=5)
    shade_top('beam', 'e', 4, 5, 3)
    plain('beam', 'e1')

    # the dryer dome: one pressed enamel shell. Each side is a single plane with one feature, a row of vents, and a
    # seam under the brim. (Bands, a chrome row and a front badge were tried; together they read as brickwork.)
    for side in SIDES:
        f = Face('dome', side)              # 12 x 3: one enamel plane, a vent row, a seam under the brim
        f.row(0, 'e3')
        f.row(1, 'e3')
        f.row(2, 'e1')
        f.put(0, 0, 'e4')
        f.put(1, 0, 'e4')
        for c0 in (2, 5, 8):                # three 2 px vent slots
            f.put(c0, 1, 'e0')
            f.put(c0 + 1, 1, 'e0')
    put, xs, zs = top('dome')
    for x in xs:
        for z in zs:
            k = 'e4'
            if z == -6 or x == -6:
                k = 'e5'
            if z == 5 or x == 5:
                k = 'e3'
            put(x, z, k)
    put, xs, zs = top('dome', 'down')
    for x in xs:
        for z in zs:
            put(x, z, 'e1' if (z in (-6, 5) or x in (-6, 5)) else 'r2')
    for x in range(-1, 1):                  # four radial vent slots in the hood's throat
        for z in (-5, -4, 3, 4):
            put(x, z, 'r0')
    for z in range(-1, 1):
        for x in (-5, -4, 3, 4):
            put(x, z, 'r0')

    # the brass cap: the brass recipe, one diagonal polish band, a steel terminal (sparks) in a b1 ring
    brass('dome_cap', top_too=False)
    put, xs, zs = top('dome_cap')
    for x in xs:
        for z in zs:
            k = 'b3'
            if z == -4 or x == -4:
                k = 'b5'
            if z == 3 or x == 3:
                k = 'b1'
            put(x, z, k)
    for (x, z) in ((-3, 0), (-2, -1), (-1, -2), (0, -3), (-3, 1), (-2, 0), (-1, -1), (0, -2), (1, -3)):
        put(x, z, 'b4')                     # the polish band, 2 px wide, across the upper left
    put(0, 0, 's4')                         # the spark terminal: a small steel boss
    put(1, 0, 's3')
    put(0, 1, 's3')
    put(1, 1, 's1')

    # rods behind the head: lit left column, dark right column
    for n in ('rod_l', 'rod_r'):
        for f in faces(n, SIDES):
            brushed_column(f, 's3', 's3', streaks=((0, 1, 4, 's4'),), bands=((6, 's2', 's2'),))
            f.row(0, 's4')
            f.row(f.H - 1, 's1')
        shade_top(n, 's', 3, 4, 2)
        plain(n, 's1')

    # the scan ring: teal glass, opaque frame. Top lightest, bottom darkest; outer faces body t2 with two short
    # shine streaks at opposite ends; inner faces one step darker
    ring = {'ring_f': 'north', 'ring_b': 'south', 'ring_l': 'west', 'ring_r': 'east'}
    for n, outer in ring.items():
        for f in faces(n, SIDES):
            if f.W * f.H == 1:
                f.fill('t3')
                continue
            if f.side == outer:
                f.fill('t2')
                f.put(1, 0, 't4')
                f.put(2, 0, 't4')
                f.put(f.W - 3, 0, 't3')
                f.put(f.W - 2, 0, 't4')
            else:
                f.fill('t1')
        plain(n, 't4', ('up',))
        plain(n, 't0', ('down',))
    for f in faces('scan_sheet', ('up', 'down')):
        for r in range(f.H):
            for c in range(f.W):
                k = 't3@170' if r in (0, f.H - 1) or c in (0, f.W - 1) else 't1@110'
                f.put(c, r, k)
        for c in range(1, f.W - 1):
            f.put(c, 4, 't2@170')           # one bright scan line across the middle

    # claw plate (steel) and its yellow temple jaws, rubber pads on the inner faces
    block('claw_plate', 's', 3, 4, 1)
    shade_top('claw_plate', 's', 3, 4, 2)
    for f in faces('claw_plate', ('down',)):
        f.fill('s2')
        f.row(f.H - 1, 's1')
        f.col(f.W - 1, 's1')
    for n in ('jaw_l_top', 'jaw_r_top'):
        for f in faces(n, SIDES):
            f.rows(['w2 w2'])
        shade_top(n, 'w', 3, 3, 2)
        plain(n, 'w2')
    for n, inner in (('jaw_l_tip', 'east'), ('jaw_r_tip', 'west')):
        for f in faces(n, SIDES):
            for r in range(f.H):
                for c in range(f.W):
                    k = 'w2'
                    if r == 0:
                        k = 'w3'
                    elif r == f.H - 1:
                        k = 'w1'
                    f.put(c, r, k)
            if f.side == inner:
                f.rows(['w3 w3', 'w2 w2', 'r3 r3', 'r2 r2', 'r2 r2', 'r1 r1'])
        plain(n, 'w2', ('up',))
        plain(n, 'w1', ('down',))


def paint_barrier():
    # post: company steel over a black kick band, so the yellow-and-black arm is the one striped thing on this side
    for f in faces('barrier_post', SIDES):
        brushed_column(f, 's3', 's3', streaks=((0, 2, 6, 's4'),), bands=((7, 's2', 's2'),))
        f.row(0, 's4')
        f.rows(['-'] * 10 + ['r2 r2', 'r1 r1'])
    shade_top('barrier_post', 's', 3, 4, 2)
    plain('barrier_post', 'r0')

    block('post_foot', 's', 2, 3, 1)
    shade_top('post_foot', 's', 2, 3, 1)
    plain('post_foot', 's1')

    # the signal backboard: matte black, lit along its top
    for f in faces('lamp_plate', SIDES):
        f.fill('r0')
        if f.side == 'north':
            for r in (0, 3, 6):
                f.row(r, 'r3')              # a lit visor lip over each lens
            f.row(9, 'r1')
        else:
            f.row(0, 'r3')
    shade_top('lamp_plate', 'r', 2, 3, 1)
    plain('lamp_plate', 'r0')

    # lenses: colour only on the front face. Off = the ramp's two darkest steps (step 1 field, step 0 lower right),
    # so red, amber and green stay legible by day. Lit cap = steps 2 and 3 on its front, every front pixel glowing;
    # the cap's sides are housing (rubber), not lit.
    for n, m in (('lens_green', 'G'), ('lens_amber', 'A'), ('lens_red', 'R')):
        for f in faces(n, ('north',)):
            f.rows(['%s1 %s1' % (m, m), '%s1 %s0' % (m, m)])
        for f in faces(n, ('east', 'west', 'south', 'up', 'down')):
            f.fill('r1')
        cap = n.replace('lens_', 'lamp_') + '_lit'
        for f in faces(cap, ('north',)):
            for (c, r, k) in ((0, 0, 3), (1, 0, 2), (0, 1, 2), (1, 1, 2)):
                f.put(c, r, '%s%d' % (m, k), lit=True)
        for f in faces(cap, ('east', 'west', 'up', 'down', 'south')):
            f.fill('r1')
        plain(cap, 'r2', ('up',))

    # hub and counterweight: steel, the weight with a lit top edge
    for f in faces('barrier_hub', SIDES):
        f.rows(['s3 s2', 's2 s1'])
    shade_top('barrier_hub', 's', 3, 4, 2)
    plain('barrier_hub', 's1')
    for f in faces('counterweight', SIDES):
        f.rows(['s4 s4 s3', 's2 s2 s1', 's2 s2 s1', 's1 s1 s0'])
    shade_top('counterweight', 's', 2, 3, 1)
    plain('counterweight', 's0')

    # the arm: a level-crossing boom, banded across its length (yellow 4: yellow.2 field, yellow.3 top row, yellow.1
    # bottom row; black 3: rubber.1 with a rubber.3 top row), unbroken round all four faces
    Y, K = ['w3', 'w2', 'w2', 'w1'], ['r3', 'r1', 'r1']
    bands = Y + K + Y + K + ['w3', 'w2', 'w1']
    for f in faces('barrier_arm', SIDES):
        for r in range(f.H):
            for c in range(f.W):
                f.put(c, r, bands[r])
    plain('barrier_arm', 'w2', ('up',))
    plain('barrier_arm', 'w1', ('down',))

    # the rubber pad along the arm: ribs every third px with a lit line
    for f in faces('barrier_pad', SIDES):
        for r in range(f.H):
            f.put(0, r, ('r1', 'r3', 'r2')[r % 3])
    plain('barrier_pad', 'r2', ('up', 'down'))


def paint_curtain():
    """The privacy curtain hangs from its rail (the z = -3 edge) toward a hem (z = -9); its folds run along the hang,
    so on its big faces they are rows. Deployed, the rows become vertical folds."""
    # Two broad folds across the width (rows: crest 2, flank 1, trough 1), deepening toward the hem, under a heading
    # tape. (A version whose folds drifted a row partway down read as mottled stone, so the folds run straight.)
    upper = 'ccftccft'
    tone = {
        0: {'c': 'e4', 'f': 'e3', 't': 'e2'},
        1: {'c': 'e4', 'f': 'e3', 't': 'e2'},
        2: {'c': 'e4', 'f': 'e3', 't': 'e2'},
        3: {'c': 'e4', 'f': 'e3', 't': 'e2'},
        4: {'c': 'e4', 'f': 'e3', 't': 'e2'},
        5: {'c': 'e3', 'f': 'e2', 't': 'e1'},    # the hem
    }
    for side in ('east', 'west'):
        f = Face('curtain', side)           # 6 x 8; east: col 0 at the rail; west: col 5 at the rail
        for r in range(8):
            for c in range(6):
                d = c if side == 'east' else 5 - c
                f.put(c, r, tone[d][upper[r]])
    for side in ('north', 'south'):
        f = Face('curtain', side)
        for r in range(8):
            f.put(0, r, {'c': 'e3', 'f': 'e2', 't': 'e1'}[upper[r]])
    for f in faces('curtain', ('up', 'down')):
        for r in range(f.H):
            f.put(0, r, 'e2')

    # brass rail: a rod, lit on its north and west faces, one glint
    for f in faces('curtain_rail', SIDES):
        lit = f.side in ('north', 'west')
        f.fill('b4' if lit else 'b3')
        f.put(0, 0, 'b5')
        f.put(0, f.H - 1, 'b1')
        if lit:
            f.put(0, 3, 'b5')
    plain('curtain_rail', 'b3', ('up', 'down'))


def paint_blade():
    # the hub is the yellow guard
    for f in faces('blade_hub', SIDES):
        f.rows(['w3 w3', 'w2 w2', 'w1 w1'])
    shade_top('blade_hub', 'w', 3, 3, 2)
    plain('blade_hub', 'w1')
    block('blade_shank', 's', 2, 3, 1)
    shade_top('blade_shank', 's', 3, 4, 2)
    plain('blade_shank', 's1')

    # the blade, the polished-steel recipe: a flat lit top plane, a hard bright cutting edge (north, z = 3) with a
    # darker grind line behind it, one 2 px glint, the back a step down; the edge face itself brightest
    for side in ('up', 'down'):
        put, xs, zs = top('blade', side)
        for x in xs:
            for z in zs:
                put(x, z, {3: 's5', 4: 's3', 5: 's4', 6: 's4', 7: 's4', 8: 's3'}[z])
        if side == 'up':
            put(6, 6, 's5')
            put(7, 6, 's5')
    f = Face('blade', 'north')
    f.fill('s5')
    f = Face('blade', 'south')
    f.fill('s2')
    plain('blade', 's3', ('east',))
    plain('blade', 's2', ('west',))
    for f in faces('blade_spine', SIDES):
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
        put(cx, cz, 'w3')
        put(cx + dx, cz, 'w3')
        put(cx, cz + dz, 'w3')
    for f in faces('tray', SIDES):
        f.fill('r2')
        f.put(0, 0, 'r3')
        f.put(f.W - 1, 0, 'r1')
    f = Face('tray', 'west')
    f.rows(['- - - b5 b4 b3 b1'])
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

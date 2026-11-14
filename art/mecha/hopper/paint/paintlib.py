"""Shared painting helpers for the mecha sheets (the Hopper frame and its modules).

Every pixel is placed by a rule from a palette key; nothing is random. A sheet is built from a .bbmodel (read only):
cube sizes, positions and box-UV offsets come from it, colours from `art/palette.json` (its default set).

Box-UV layout (bbbuild.py, the same as Blockbench and GeckoLib): for a cube of size w x h x d at uv (u, v)
    up    (u+d,     v)      w x d        down (u+d+w, v)   w x d
    east  (u,       v+d)    d x h        north (u+d, v+d)  w x h
    west  (u+d+w,   v+d)    d x h        south (u+2d+w, v+d) w x h
The side faces are seen from outside with column 0 on the viewer's left and row 0 at the top, so the side strip
east | north | west | south runs continuously round the cube (column 0 of each face meets the last column of the face
before it): a stripe painted along the strip wraps round the corners. `Face.world(c, r)` gives a texel's centre in
model space, so up and down faces (whose UVs are mirrored) can be painted by world position.
"""
import json
import os

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', '..'))
PALETTE = os.path.join(ROOT, 'art', 'palette.json')
SIDES = ('east', 'north', 'west', 'south')

with open(PALETTE, encoding='utf-8') as _fh:
    _pal = json.load(_fh)
COL = {}
for _name, _m in _pal['house'].items():
    for _i, _h in enumerate(_m[_pal['default']]):
        COL['%s%d' % (_m['alias'], _i)] = _h
for _name, _m in _pal['shared'].items():
    for _i, _h in enumerate(_m['ramp']):
        COL['%s%d' % (_m['alias'], _i)] = _h


def rgba(key):
    a = 255
    if '@' in key:
        key, a = key.split('@')
        a = int(a)
    h = COL[key]
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def step(key, d):
    """'b3', +1 -> 'b4' (clamped to the ramp)."""
    m, i = key[0], int(key[1:])
    j = i + d
    while '%s%d' % (m, j) not in COL:
        j += -1 if d > 0 else 1
    return '%s%d' % (m, max(0, j))


class Sheet:
    def __init__(self, bbmodel):
        with open(bbmodel, encoding='utf-8') as fh:
            bb = json.load(fh)
        self.W, self.H = bb['resolution']['width'], bb['resolution']['height']
        self.cubes = {}
        for e in bb['elements']:
            if e.get('type', 'cube') != 'cube':
                continue
            f, t = e['from'], e['to']
            sz = [max(1, int(round(abs(t[i] - f[i])))) for i in range(3)]
            self.cubes[e['name']] = dict(frm=[min(f[i], t[i]) for i in range(3)], w=sz[0], h=sz[1], d=sz[2],
                                         u=int(e['uv_offset'][0]), v=int(e['uv_offset'][1]))
        self.img = Image.new('RGBA', (self.W, self.H), (0, 0, 0, 0))
        self.glow = Image.new('RGBA', (self.W, self.H), (0, 0, 0, 0))
        self.px, self.gx = self.img.load(), self.glow.load()

    def face(self, cube, side):
        return Face(self, cube, side)

    def faces(self, cube, sides=SIDES + ('up', 'down')):
        return [Face(self, cube, s) for s in sides]

    def missing(self, skip=('standin',)):
        out = []
        for n in self.cubes:
            if n.startswith(skip):
                continue
            for f in self.faces(n):
                if any(self.px[f.x0 + c, f.y0 + r][3] == 0 for r in range(f.H) for c in range(f.W)):
                    out.append((n, f.side))
        return out


class Face:
    """One face of one cube; f.put(c, r, key) addresses it as seen from outside (c rightward, r downward)."""

    def __init__(self, sheet, cube, side):
        c = sheet.cubes[cube]
        self.s, self.cube, self.side, self.c = sheet, cube, side, c
        w, h, d, u, v = c['w'], c['h'], c['d'], c['u'], c['v']
        self.x0, self.y0, self.W, self.H = {
            'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h), 'west': (u + d + w, v + d, d, h),
            'south': (u + 2 * d + w, v + d, w, h), 'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d),
        }[side]
        # column of this face's col 0 along the side strip (east | north | west | south)
        self.strip0 = {'east': 0, 'north': d, 'west': d + w, 'south': 2 * d + w}.get(side)
        self.perim = 2 * (w + d)

    def put(self, c, r, key, lit=False):
        if key is None or not (0 <= c < self.W and 0 <= r < self.H):
            return
        x, y = self.x0 + c, self.y0 + r
        if 0 <= x < self.s.W and 0 <= y < self.s.H:
            self.s.px[x, y] = rgba(key)
            self.s.gx[x, y] = rgba(key) if lit else (0, 0, 0, 0)

    def get(self, c, r):
        return self.s.px[self.x0 + c, self.y0 + r]

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

    def rows(self, lines, c0=0, r0=0):
        for r, line in enumerate(lines):
            for c, k in enumerate(line.split()):
                if k not in ('-', '.'):
                    self.put(c0 + c, r0 + r, k)

    def world(self, c, r):
        """Centre of texel (c, r) in model space."""
        x0, y0, z0 = self.c['frm']
        w, h, d = self.c['w'], self.c['h'], self.c['d']
        cx, cy = c + 0.5, r + 0.5
        if self.side == 'north':
            return (x0 + w - cx, y0 + h - cy, z0)
        if self.side == 'south':
            return (x0 + cx, y0 + h - cy, z0 + d)
        if self.side == 'east':
            return (x0 + w, y0 + h - cy, z0 + d - cx)
        if self.side == 'west':
            return (x0, y0 + h - cy, z0 + cx)
        if self.side == 'up':
            return (x0 + w - cx, y0 + h, z0 + d - cy)
        return (x0 + w - cx, y0, z0 + cy)          # down

    def each(self):
        for r in range(self.H):
            for c in range(self.W):
                yield c, r

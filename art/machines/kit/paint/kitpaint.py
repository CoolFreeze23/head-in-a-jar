"""The machine kit's paint: every shared part's construction, written once, imported by every machine's paint script.

Every pixel is placed by a rule from a palette key (art/palette.json, its default set, D); nothing is random. A sheet is
built from a machine's .bbmodel (read only): cube sizes, positions and box-UV offsets come from it.

    import sys, os
    sys.path.insert(0, os.path.join(HERE, '..', '..', 'kit', 'paint'))
    import kitpaint as K
    S = K.Sheet(BBMODEL)
    K.plinth(S); K.feet(S); K.lamp_bezel(S, 'lamp_bezel'); ...
    S.save(K.OUT / 'name.png')

The value plan every machine follows (the Hopper's and the chair's lessons, now policy):
  * three masses, planned on a render about 110 px tall before any detail: LIGHT (the enamel body or the brass frame),
    DARK (plinth, feet, running gear, open interiors: steel s0-s1, rubber r0-r1), ACCENT (brass, copper, yellow);
  * brass fields stay at b3 with b4 on the lit edge and b5 only as a glint; darks only ROUND brass (b1 undersides, b2
    end columns, r0 or s0 rings where it meets something), never in its field (a darkened brass field reads as copper
    or wood);
  * enamel fields e2 (one step under the e3 lip; the e4 glint only on top faces' lit edges), secondary enamel e1-e2;
    steel fields s3 for parts, s1 for running gear; a raised rim is lighter than the field it frames;
  * every recess (a hole, a mouth, a niche, a gutter, a groove) takes its material's darkest step, or r0;
  * rails and bars: one continuous highlight row, never dashes;
  * small parts (faces under 3 px, or about 4 px overall): one tone a face, top +1, underside -1, one accent;
  * hazard stripes only at 45 degrees on faces at least 4 px wide and only on parts that move; elsewhere a plain band;
  * no noise, no random fill; story marks placed once each.

Box-UV layout (the kit's writer, Blockbench and GeckoLib agree): for a cube w x h x d at uv (u, v)
    up (u+d, v) w x d   down (u+d+w, v) w x d   east (u, v+d) d x h   north (u+d, v+d) w x h
    west (u+d+w, v+d) d x h   south (u+2d+w, v+d) w x h
Side faces are seen from outside, column 0 at the viewer's left, row 0 at the top; `Face.world(c, r)` gives the
texel's centre in model space (pixels, origin at the block's bottom centre, front -Z, +X the machine's right).
"""
import base64
import io
import json
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
PALETTE = os.path.join(ROOT, 'art', 'palette.json')
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'headinajar', 'textures', 'block', 'machine')
OUT_KIT = os.path.join(OUT, 'kit')
SIDES = ('east', 'north', 'west', 'south')
ALL = SIDES + ('up', 'down')

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
    if key in ('.', '..', None):
        return (0, 0, 0, 0)
    a = 255
    if '@' in key:
        key, a = key.split('@')
        a = int(a)
    h = COL[key]
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def st(key, d):
    """'b3', +1 -> 'b4' (clamped to the ramp)."""
    m, i = key[0], int(key[1:])
    j = max(0, i + d)
    while '%s%d' % (m, j) not in COL:
        j -= 1
    return '%s%d' % (m, j)


# ===================================================================================================== sheet and faces
class Sheet:
    def __init__(self, bbmodel, size=None):
        with open(bbmodel, encoding='utf-8') as fh:
            bb = json.load(fh)
        self.bb = bb
        self.W, self.H = bb['resolution']['width'], bb['resolution']['height']
        if size:
            self.W, self.H = size
        self.cubes = {}
        self.loose = []
        for e in bb['elements']:
            if e.get('type', 'cube') != 'cube':
                continue
            if 'uv_offset' not in e:          # per-face UV stand-ins (a jar, a frame): never painted
                self.loose.append(e)
                continue
            f, t = e['from'], e['to']
            sz = [max(1, int(round(abs(t[i] - f[i])))) for i in range(3)]
            self.cubes[e['name']] = dict(frm=[min(f[i], t[i]) for i in range(3)], w=sz[0], h=sz[1], d=sz[2],
                                         u=int(e['uv_offset'][0]), v=int(e['uv_offset'][1]))
        self.img = Image.new('RGBA', (self.W, self.H), (0, 0, 0, 0))
        self.px = self.img.load()

    def face(self, cube, side):
        return Face(self, cube, side)

    def faces(self, cube, sides=ALL):
        return [Face(self, cube, s) for s in sides]

    def names(self, prefix):
        return sorted(n for n in self.cubes if n.startswith(prefix))

    def missing(self, skip=('si_',), clear_ok=()):
        out = []
        for n in self.cubes:
            if n.startswith(skip) or n in clear_ok:
                continue
            for f in self.faces(n):
                if any(self.px[f.x0 + c, f.y0 + r][3] == 0 for c, r in f.each()):
                    out.append((n, f.side))
        return out

    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        self.img.save(path)
        return path


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
        self.strip0 = {'east': 0, 'north': d, 'west': d + w, 'south': 2 * d + w}.get(side)

    def put(self, c, r, key):
        if key is None or not (0 <= c < self.W and 0 <= r < self.H):
            return
        x, y = self.x0 + c, self.y0 + r
        if 0 <= x < self.s.W and 0 <= y < self.s.H:
            self.s.px[x, y] = rgba(key)

    def get(self, c, r):
        return self.s.px[self.x0 + c, self.y0 + r]

    def fill(self, key):
        for c, r in self.each():
            self.put(c, r, key)

    def row(self, r, key, c0=0, c1=None):
        for c in range(c0, self.W if c1 is None else c1):
            self.put(c, r, key)

    def col(self, c, key, r0=0, r1=None):
        for r in range(r0, self.H if r1 is None else r1):
            self.put(c, r, key)

    def rows(self, lines, c0=0, r0=0):
        for r, line in enumerate(lines):
            for c, k in enumerate(line.split()):
                if k not in ('-',):
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

    def by_world(self, fn):
        """Paint every texel from fn(c, r, x, y, z) -> key (None leaves it)."""
        for c, r in self.each():
            x, y, z = self.world(c, r)
            self.put(c, r, fn(c, r, x, y, z))


def fill(S, cube, key, sides=ALL):
    for f in S.faces(cube, sides):
        f.fill(key)


def rivet(f, c, r, lit, dark):
    """T18: a lit head and its shadow below-right."""
    f.put(c, r, lit)
    f.put(c + 1, r + 1, dark)


# ===================================================================================================== small parts
def small(S, cube, m, field, bolt=False, top=None, bottom=None, sides=SIDES):
    """A small part: each side face ONE tone (never a lit half and a dark half); top +1, underside -1; optionally one
    bolt (field + 2 head, field - 1 shadow) at (0, 1) on side faces 2+ wide and 3+ tall."""
    k = '%s%d' % (m, field)
    up = top or st(k, 1)
    dn = bottom or st(k, -1)
    for f in S.faces(cube, sides):
        f.fill(k)
        if bolt and f.W >= 2 and f.H >= 3:
            rivet(f, 0, 1, st(k, 2), dn)
    for f in S.faces(cube, ('up',)):
        f.fill(up)
    for f in S.faces(cube, ('down',)):
        f.fill(dn)


def recess(S, cube, key='r0'):
    """A part that exists to be a hole, a mouth or a gap: every face the darkest step."""
    fill(S, cube, key)


# ===================================================================================================== brass
def brass_rail(f, field='b3', lit='b4', under='b1', ends='b2'):
    """A brass bar or plate edge seen from the side: row 0 one continuous lit row (b4), the field b3, with 3+ rows the
    last row b1; the two end columns a step down (they turn away). Never dashes."""
    for c, r in f.each():
        end = (c in (0, f.W - 1)) and f.W >= 4
        if r == 0:
            k = field if end else lit
        elif r == f.H - 1 and f.H >= 3:
            k = st(under, -1) if end else under
        else:
            k = ends if end else field
        f.put(c, r, k)


def brass_post(S, cube, foot=True):
    """A vertical brass rod or post (2 px square): every side one b3 field (the renderer's face shading does the
    turning), top b4, the last row b1 where it meets what it stands on, underside b1."""
    for f in S.faces(cube, SIDES):
        f.fill('b3')
        if foot and f.H >= 4:
            f.row(f.H - 1, 'b1')
    S.face(cube, 'up').fill('b4')
    S.face(cube, 'down').fill('b1')


def polish(f, c0, r0, r1, key='b4', glint='b5'):
    """The brass polish band: 2 px wide, 45 degrees, rising to the right from column c0 on row r1-1 up to row r0;
    its top pixel the glint. Once per face, faces 6+ wide, in the left half."""
    for i, r in enumerate(range(r1 - 1, r0 - 1, -1)):
        f.put(c0 + i, r, key)
        f.put(c0 + i + 1, r, key)
    f.put(c0 + (r1 - r0), r0, glint)


def brass_top(f, edge='b4', field='b3', band=True):
    """A brass top face: one b3 field, a b4 edge along its north and west (lit) sides, nothing else; one polish band
    on faces 6+ by 6+."""
    x0, _, z0 = f.c['frm']
    xmax = x0 + f.c['w']
    for c, r in f.each():
        x, y, z = f.world(c, r)
        f.put(c, r, edge if (z < z0 + 1 or x > xmax - 1) else field)
    if band and f.W >= 6 and f.H >= 6:
        polish(f, 2, 2, min(f.H - 1, 6))


def brass_plate(S, cube, band_faces=()):
    """A brass slab (press plate, lid, tray): sides as rails, top as brass_top, underside b1."""
    for f in S.faces(cube, SIDES):
        brass_rail(f)
    brass_top(S.face(cube, 'up'))
    S.face(cube, 'down').fill('b1')


def brass_housing(S, cube):
    """A cast brass housing (tap housing, hub): row 0 b4, b3 field, end columns b2, last row b1; top b3 with b4 lit
    edges; underside b1."""
    for f in S.faces(cube, SIDES):
        for c, r in f.each():
            end = c in (0, f.W - 1) and f.W >= 4
            if r == 0:
                k = 'b3' if end else 'b4'
            elif r == f.H - 1:
                k = 'b1'
            else:
                k = 'b2' if end else 'b3'
            f.put(c, r, k)
    brass_top(S.face(cube, 'up'), band=False)
    S.face(cube, 'down').fill('b1')


def bushing(f, rects, key='s1'):
    """On a top face, a 1 px ring round each rectangle (x0, z0, x1, z1) in world pixels where a rod or screw passes
    through: a steel bushing, so the joint reads as made, not butted."""
    for c, r in f.each():
        x, y, z = f.world(c, r)
        for (a0, b0, a1, b1) in rects:
            if a0 - 1 < x < a1 + 1 and b0 - 1 < z < b1 + 1 and not (a0 < x < a1 and b0 < z < b1):
                f.put(c, r, key)


# ===================================================================================================== steel
def steel_part(S, cube, field='s3', lit='s4', under='s2', streaks=()):
    """A machined steel part (yoke, drum, paddle, links): sides one field with a continuous lit top row, underside a
    step down; the top face the lit step with brushed streaks (c, r, length, key) placed along its long axis."""
    for f in S.faces(cube, SIDES):
        f.fill(field)
        if f.H >= 2:
            f.row(0, lit)
    S.face(cube, 'up').fill(lit if field != 's1' else 's2')
    S.face(cube, 'down').fill(under)
    up = S.face(cube, 'up')
    for (c, r, n, k) in streaks:
        if up.W >= up.H:
            for i in range(n):
                up.put(c + i, r, k)
        else:
            for i in range(n):
                up.put(c, r + i, k)


def dark_steel(S, cube, lit_row=True):
    """Running gear and plinths (the dark mass): sides s1 with an s2 lit top row, top s2, underside s0."""
    for f in S.faces(cube, SIDES):
        f.fill('s1')
        if lit_row and f.H >= 2:
            f.row(0, 's2')
    S.face(cube, 'up').fill('s2')
    S.face(cube, 'down').fill('s0')


def pole(S, cube, streaks=()):
    """A dark vertical post 4+ px square: s1 field, an s2 lit arris column on the north and west faces only (a dark
    column too would give a barcode), placed s2 streaks 3-5 px along the length, an s0 foot row."""
    for f in S.faces(cube, SIDES):
        f.fill('s1')
        if f.side in ('north', 'west') and f.W >= 4:
            f.col(0, 's2')
        f.row(f.H - 1, 's0')
        for (side, c, r0, n) in streaks:
            if side == f.side:
                for i in range(n):
                    f.put(c, r0 + i, 's2')
    S.face(cube, 'up').fill('s2')
    S.face(cube, 'down').fill('s0')


# ===================================================================================================== K1 plinth, feet
def plinth(S, cube='plinth'):
    """K1: the steel plinth, the base of the dark mass. Sides s1 with no lit row (it is 1 px: the lip is the top face),
    top s2 with an s3 lit front-left edge, underside s0 (over the shadow gap)."""
    for f in S.faces(cube, SIDES):
        f.fill('s1')
    up = S.face(cube, 'up')
    x0, _, z0 = up.c['frm']
    xmax = x0 + up.c['w']
    up.by_world(lambda c, r, x, y, z: 's3' if (z < z0 + 1 or x > xmax - 1) else 's2')
    S.face(cube, 'down').fill('s0')


def feet(S, prefix='foot_'):
    """K1 feet, rubber 3x2x3 inset under the plinth: the shadow gap's dark. Sides r1, the top row r2 (the one lit
    edge rubber gets), top r2, underside r0."""
    for n in S.names(prefix):
        for f in S.faces(n, SIDES):
            f.fill('r1')
        S.face(n, 'up').fill('r2')
        S.face(n, 'down').fill('r0')


# ===================================================================================================== K2 enamel body
def enamel_body(f, y0, y1, field='e2', lip='e3', bead=True):
    """K2 casing face, painted by world height over a body standing y0..y1 (so pieces of one counter agree):
      top row (y1-1)      the lit lip, e3
      field               e2, flat (Oritech flatness: the detail sits in the parts on it)
      row y0+2            e3, the bead of the lapped belt seam   (with bead=True and a body 7+ tall)
      row y0+1            e1, the skirt in the lap's shadow
      row y0 (bottom)     e0, the base shadow line over the plinth gap
    Faces 1 px wide (the K2 corner steps, reveals) take the field one step down."""
    narrow = f.W == 1
    tall = (y1 - y0) >= 7

    def k(c, r, x, y, z):
        if y > y1 - 1:
            return lip
        if tall and bead and y0 + 2 < y < y0 + 3:
            return lip
        if tall and y0 + 1 < y < y0 + 2:
            return 'e1'
        if y < y0 + 1:
            return 'e0' if tall else 'e1'
        return st(field, -1) if narrow else field
    f.by_world(k)


def enamel_inset(f, c0, r0, c1, r1, field='e2'):
    """T9's recessed inset on a big enamel face (a pressed panel): inside the rectangle, its top and left rows in
    shadow (e1), its bottom and right rows lit (e3), the field e2. Faces at least 10 x 8."""
    for c in range(c0, c1):
        for r in range(r0, r1):
            if r == r0 or c == c0:
                k = 'e1'
            elif r == r1 - 1 or c == c1 - 1:
                k = 'e3'
            else:
                k = field
            f.put(c, r, k)


def enamel_top(f, field='e3', edge='e4'):
    """An enamel top face: one step over the sides' field, e4 along the lit north and west edges."""
    x0, _, z0 = f.c['frm']
    xmax = x0 + f.c['w']
    f.by_world(lambda c, r, x, y, z: edge if (z < z0 + 1 or x > xmax - 1) else field)


# ===================================================================================================== K3 lamp
def lamp_bezel(S, cube='lamp_bezel', face='north'):
    """K3 bezel, brass 4x4x1: the front a b3 ring with a b4 top row and a b2 bottom row (the lens covers the centre,
    painted r0 in case an angle shows it); top b4, sides b2, underside b1."""
    f = S.face(cube, face)
    for c, r in f.each():
        if r == 0:
            k = 'b4'
        elif r == f.H - 1:
            k = 'b2'
        elif 1 <= c <= f.W - 2 and 1 <= r <= f.H - 2:
            k = 'r0'
        else:
            k = 'b3'
        f.put(c, r, k)
    for s in SIDES:
        if s != face:
            S.face(cube, s).fill('b2')
    S.face(cube, 'up').fill('b4')
    S.face(cube, 'down').fill('b1')


# Lens state textures: the lens element's own box-UV layout (2x2x1) at offset (0, 0) of a 16x16 sprite; the rest of
# the sprite is filled with the rim step so mipmaps never pull in clear pixels. Front 2x2 rows; sides one tone.
LENS = {
    #         front rows             sides  bottom-side  fill
    'off':   (('r3 r1', 'r1 r0'),    'r1',  'r0',        'r1'),   # dark glass, one glass glint, no glow
    'ready': (('G3 G2', 'G2 G1'),    'G1',  'G0',        'G1'),
    'wait':  (('A3 A2', 'A2 A1'),    'A1',  'A0',        'A1'),
    'fault': (('R3 R2', 'R2 R1'),    'R1',  'R0',        'R1'),   # steady, never blinks (M15)
}
LENS_ANIM = {
    # state: (frames, frametime); work: 20 ticks lit / 20 at A1 (0.5 Hz); low: 20 at A1 / 20 at A0 (the brown-out)
    'work': ([(('A3 A2', 'A2 A1'), 'A1', 'A0', 'A1'), (('A1 A1', 'A1 A0'), 'A0', 'A0', 'A0')], 20),
    'low':  ([(('A1 A1', 'A1 A0'), 'A0', 'A0', 'A0'), (('A0 A0', 'A0 A0'), 'A0', 'A0', 'A0')], 20),
}


def _boxuv(w, h, d):
    return {'up': (d, 0, w, d), 'down': (d + w, 0, w, d), 'east': (0, d, d, h), 'north': (d, d, w, h),
            'west': (d + w, d, d, h), 'south': (2 * d + w, d, w, h)}


def _lens_frame(img, oy, spec):
    front, side, bot, fillk = spec
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, oy + y] = rgba(fillk)
    lay = _boxuv(2, 2, 1)
    for s, (x0, y0, w, h) in lay.items():
        for r in range(h):
            for c in range(w):
                if s == 'north':
                    k = front[r].split()[c]
                elif s == 'down':
                    k = bot
                else:
                    k = side
                px[x0 + c, oy + y0 + r] = rgba(k)


def write_mcmeta(path, frametime=None, frames=None, interpolate=False):
    anim = {}
    if frametime:
        anim['frametime'] = frametime
    if frames:
        anim['frames'] = frames
    if interpolate:
        anim['interpolate'] = True
    with open(path + '.mcmeta', 'w', encoding='utf-8') as fh:
        json.dump({'animation': anim}, fh, indent=1)
        fh.write('\n')


def lens_textures(out=OUT_KIT):
    """Writes machine/kit/lamp_<state>.png (and .mcmeta for work and low). Returns the paths."""
    os.makedirs(out, exist_ok=True)
    paths = []
    for state, spec in LENS.items():
        img = Image.new('RGBA', (16, 16))
        _lens_frame(img, 0, spec)
        p = os.path.join(out, 'lamp_%s.png' % state)
        img.save(p)
        paths.append(p)
    for state, (frames, ft) in LENS_ANIM.items():
        img = Image.new('RGBA', (16, 16 * len(frames)))
        for i, spec in enumerate(frames):
            _lens_frame(img, 16 * i, spec)
        p = os.path.join(out, 'lamp_%s.png' % state)
        img.save(p)
        write_mcmeta(p, frametime=ft)
        paths += [p, p + '.mcmeta']
    return paths


def lens_on_sheet(S, cube='lamp_lens', state='ready'):
    """Paints the lens island of a machine sheet as one state (the game swaps the lens texture by blockstate; the
    sheet's island is seen only in review renders)."""
    spec = LENS[state] if state in LENS else LENS_ANIM[state][0][0]
    front, side, bot, _ = spec
    f = S.face(cube, 'north')
    f.rows(list(front))
    for s in ('east', 'west', 'up', 'south'):
        S.face(cube, s).fill(side)
    S.face(cube, 'down').fill(bot)


# ===================================================================================================== K4 gauge
# The gauge face element (4x4x1, 1 px proud inside a 2 px deep brass frame) as its own box-UV layout at offset (0, 0)
# of a 16x16 sprite, one sprite per `level` 0-4. A pale dial (t3, a t4 glint top-left), a dark hub (s1, 2 px at the
# bottom centre), a rubber needle tip; a red mark at the full end. Levels 1-4 tremble: the tip steps one pixel and back
# twice in 44 ticks (2 ticks each), the idle life of a machine under pressure. Level 0 lies dead.
GAUGE_NEEDLE = {0: [(1, 2), (0, 2)], 1: [(1, 2), (0, 1)], 2: [(1, 2), (1, 1)], 3: [(2, 2), (2, 1)],
                4: [(2, 2), (3, 1)]}
GAUGE_TREMBLE = {1: [(1, 2), (0, 2)], 2: [(1, 2), (0, 1)], 3: [(2, 2), (1, 1)], 4: [(2, 2), (3, 2)]}


def _gauge_frame(img, oy, needle):
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, oy + y] = rgba('t1')
    lay = _boxuv(4, 4, 1)
    for s, (x0, y0, w, h) in lay.items():
        for r in range(h):
            for c in range(w):
                px[x0 + c, oy + y0 + r] = rgba('t1' if s != 'up' else 't2')
    x0, y0 = lay['north'][0], lay['north'][1]
    dial = [['t4', 't3', 't3', 'R2'],
            ['t3', 't3', 't3', 't3'],
            ['t3', 't3', 't3', 't3'],
            ['t2', 's1', 's1', 't2']]
    for (c, r) in needle:
        dial[r][c] = 'r1'
    for r in range(4):
        for c in range(4):
            px[x0 + c, oy + y0 + r] = rgba(dial[r][c])


def gauge_textures(out=OUT_KIT):
    os.makedirs(out, exist_ok=True)
    paths = []
    for lv in range(5):
        p = os.path.join(out, 'gauge_%d.png' % lv)
        if lv == 0:
            img = Image.new('RGBA', (16, 16))
            _gauge_frame(img, 0, GAUGE_NEEDLE[0])
            img.save(p)
            paths.append(p)
            continue
        img = Image.new('RGBA', (16, 32))
        _gauge_frame(img, 0, GAUGE_NEEDLE[lv])
        _gauge_frame(img, 16, GAUGE_TREMBLE[lv])
        img.save(p)
        write_mcmeta(p, frames=[{'index': 0, 'time': 36}, {'index': 1, 'time': 2}, {'index': 0, 'time': 4},
                                {'index': 1, 'time': 2}])
        paths += [p, p + '.mcmeta']
    return paths


def gauge_frame(S, prefix='gauge_bezel'):
    """K4 brass bezel strips (2 px deep): outer faces as rails (lit top strip b4, sides b3, bottom strip b2); the inner
    faces round the dial b1 (darks round brass); top faces b4, undersides b1."""
    for n in S.names(prefix):
        tag = n[-1]
        for f in S.faces(n, SIDES):
            f.fill({'t': 'b4', 'b': 'b2'}.get(tag, 'b3'))
        S.face(n, 'up').fill('b4')
        S.face(n, 'down').fill('b1')


# ===================================================================================================== K5 hatch, drawer
def drawer(S, box='drawer_box', pulls=('drawer_pull',), face='north'):
    """K5 drawer: the front one enamel tone (e2), its top edge e3, sides e1 (seen when it slides out), underside e0;
    the brass pull one tone b4 with a b5 glint at its left end (worn bright by hands: the drawer's one story mark),
    top b4, sides b2, underside b1."""
    for f in S.faces(box, SIDES):
        f.fill('e2' if f.side == face else 'e1')
    S.face(box, 'up').fill('e3')
    S.face(box, 'down').fill('e0')
    for i, p in enumerate(pulls):
        for f in S.faces(p, SIDES):
            f.fill('b2')
        fr = S.face(p, face)
        fr.fill('b4')
        if i == len(pulls) - 1:
            fr.put(0, 0, 'b5')
        S.face(p, 'up').fill('b4')
        S.face(p, 'down').fill('b1')


def hatch(S, bone='hatch', face='north'):
    """K5 hatch: an enamel panel e2 with an e3 lip row and an e1 foot row on its front, edges e1; a 2x2 brass pull
    (b4 b3 / b3 b2); the baked knuckle brass b3 with a b4 top and an r0 seam where it meets the panel."""
    p = bone + '_panel'
    for f in S.faces(p, SIDES):
        f.fill('e1')
    fr = S.face(p, face)
    fr.fill('e2')
    fr.row(0, 'e3')
    fr.row(fr.H - 1, 'e1')
    S.face(p, 'up').fill('e3')
    S.face(p, 'down').fill('e0')
    small(S, bone + '_pull', 'b', 3, top='b4', bottom='b1')
    S.face(bone + '_pull', face).rows(['b4 b3', 'b3 b2'])
    small(S, bone + '_knuckle', 'b', 3, top='b4', bottom='b1')


# ===================================================================================================== K6b side port
def side_port(S, prefix, out_side):
    """K6b: the brass frame strips as rails (outer faces b3 with the top strip's outer face b4, the bottom strip's
    b2), their faces round the mouth b1 (darks round brass), tops b4, undersides b1; the rubber mouth r0, its whole
    face the hole's darkest step."""
    for n in S.names(prefix + '_frame'):
        tag = n[-1]
        for f in S.faces(n, SIDES):
            if f.side == out_side:
                f.fill({'t': 'b4', 'b': 'b2'}.get(tag, 'b3'))
            else:
                f.fill('b1')
        S.face(n, 'up').fill('b4' if tag != 'b' else 'b3')
        S.face(n, 'down').fill('b1')
    recess(S, prefix + '_mouth', 'r0')


# ===================================================================================================== K7 maker's plate
def makers_plate(S, cube='makers_plate', face='north'):
    """K7, brass 4x2x1, 1 px proud. The N&S monogram does not fit 4x2; the plate reads as a riveted brass plate with an
    engraved name: rivet heads b5 at both top corners, the name b1 between them, the lower row the b3 field with a b2
    right end. Edges: top b4, sides b2, underside b1."""
    f = S.face(cube, face)
    rows = [['b5'] + ['b1'] * (f.W - 2) + ['b4'], ['b3'] * (f.W - 1) + ['b2']]
    for r in range(f.H):
        for c in range(f.W):
            f.put(c, r, rows[min(r, 1)][c])
    for s in SIDES:
        if s != face:
            S.face(cube, s).fill('b2')
    S.face(cube, 'up').fill('b4')
    S.face(cube, 'down').fill('b1')


# ===================================================================================================== K8 stripes
def stripes(S, cube, sides=SIDES, phase=0):
    """45 degrees, 2 px w2 and 2 px r1, period 4 along the side strip (wraps the corners when the perimeter is a
    multiple of 4); the band's top row a step lighter (w3 / r2). Moving parts only, faces 4+ wide."""
    for f in S.faces(cube, sides):
        for c, r in f.each():
            y = (f.strip0 + c + r + phase) % 4 < 2
            f.put(c, r, ('w3' if y else 'r2') if r == 0 else ('w2' if y else 'r1'))


def plain_band(f, field='w2', lit='w3', foot='w1'):
    """A painted band on a part that does not move (or is too narrow to stripe): top row a step lighter, bottom row a
    step darker, the field flat."""
    for c, r in f.each():
        f.put(c, r, lit if r == 0 else (foot if r == f.H - 1 and f.H >= 3 else field))


# ===================================================================================================== K9 flag
def flag(S, bone='flag', out_side='east'):
    """K9: the steel arm small-part (s3, top s4, underside s2); the yellow plate in the narrow form (rows w3, w2, one
    r1 band, w1 foot) on its two broad faces, edges w2, top w3, underside w1."""
    small(S, bone + '_arm', 's', 3)
    p = bone + '_plate'
    for f in S.faces(p, SIDES):
        if f.W >= 3 or f.side in ('east', 'west'):
            for c, r in f.each():
                f.put(c, r, ['w3', 'w2', 'r1', 'w1'][min(r, 3)] if f.H == 4 else 'w2')
        else:
            f.fill('w2')
    S.face(p, 'up').fill('w3')
    S.face(p, 'down').fill('w1')


# ===================================================================================================== glass, liquid
def clear_glass(f, frame=('g3', 'g2'), streaks=True, edges='tblr'):
    """Clear glass (cutout): a 1 px frame in the two lightest steps (g3 along the top and left, g2 along the bottom and
    right), the rest clear; two short shine streaks in opposite corners on faces 6+ by 4+."""
    lit, dim = frame
    for c, r in f.each():
        k = '.'
        if r == 0 and 't' in edges or c == 0 and 'l' in edges:
            k = lit
        elif r == f.H - 1 and 'b' in edges or c == f.W - 1 and 'r' in edges:
            k = dim
        f.put(c, r, k)
    if streaks and f.W >= 6 and f.H >= 4:
        f.put(2, 2, lit)
        f.put(3, 1, lit)
        f.put(f.W - 3, f.H - 2, dim)
        f.put(f.W - 2, f.H - 3, dim)


def glass_window(f, lit='g3', dim='g2'):
    """Clear glass with contents to show (the game showed it: a frame and streaks hid the Pile's meat behind 34 of 60
    pixels): only the top row (the lit rim, g3) and the two bottom corners (g2); everything else clear, no streaks."""
    for c, r in f.each():
        if r == 0:
            k = lit
        elif r == f.H - 1 and c in (0, f.W - 1):
            k = dim
        else:
            k = '.'
        f.put(c, r, k)


def liquid_side(f, ramp='l', top_y=None, bottom_y=None, bubbles=()):
    """A liquid behind glass, opaque (the kit's glass rule): the top row lightest (the meniscus, step 3), the body step 1 with
    one horizontal 1 px wavy band of step 2 two rows under the meniscus, the bottom row step 0; bubbles (c, r) step 3."""
    for c, r in f.each():
        if r == 0:
            k = ramp + '3'
        elif r == f.H - 1 and f.H >= 3:
            k = ramp + '0'
        elif r == 2 and f.H >= 5 and (c % 4) in (0, 1):
            k = ramp + '2'
        elif r == 3 and f.H >= 6 and (c % 4) in (2, 3):
            k = ramp + '2'
        else:
            k = ramp + '1'
        f.put(c, r, k)
    for (c, r) in bubbles:
        if 0 < r < f.H - 1:
            f.put(c, r, ramp + '3')


# ===================================================================================================== K10, K12
def terminal(S, name):
    """K10: rubber collar (r1 sides, r2 top), copper post (c4 sides, c6 top), brass cap (small brass part)."""
    small(S, name + '_collar', 'r', 1)
    small(S, name + '_post', 'c', 4, top='c6', bottom='c2')
    small(S, name + '_cap', 'b', 3, top='b4', bottom='b1')


def worktop(S, cube='bench_worktop'):
    """K12 black worktop (rubber): sides a continuous r3 lip row over r1, the top r1 with an r2 lit front-left edge,
    the underside r0 (over the cabinet)."""
    for f in S.faces(cube, SIDES):
        f.fill('r1')
        f.row(0, 'r3')
    up = S.face(cube, 'up')
    x0, _, z0 = up.c['frm']
    xmax = x0 + up.c['w']
    up.by_world(lambda c, r, x, y, z: 'r2' if (z < z0 + 1 or x > xmax - 1) else 'r1')
    S.face(cube, 'down').fill('r0')


def bench(S):
    """K12 lab bench: enamel cabinet ends (K2 body faces, a T9 inset on the 14x10 outer sides), rubber kick r0 (the
    base shadow), the open shelf one dark hole (steel floor s0 on top, s1 front; rubber back r0 inside, r1 outside),
    the enamel band e2 with its underside e0 (the shelf's ceiling), the black worktop, the K5 drawer."""
    recess(S, 'bench_kick', 'r0')
    for n in ('bench_end_l', 'bench_end_r'):
        for f in S.faces(n, SIDES):
            enamel_body(f, 1, 11, bead=False)
            if f.W >= 10 and f.H >= 8:
                enamel_inset(f, 2, 2, f.W - 2, f.H - 2)
        S.face(n, 'up').fill('e1')       # under the worktop, seen only at the overhang's edge
        S.face(n, 'down').fill('e0')
    b = 'bench_back'
    fill(S, b, 'r1')
    S.face(b, 'north').fill('r0')
    S.face(b, 'south').row(0, 'r2')
    s = 'bench_shelf'
    fill(S, s, 's1')
    S.face(s, 'up').fill('s0')
    S.face(s, 'down').fill('s0')
    band = 'bench_band'
    fill(S, band, 'e2')
    S.face(band, 'down').fill('e0')
    S.face(band, 'up').fill('e1')
    worktop(S)


# ===================================================================================================== previews
def embed(bbmodel_in, png, bbmodel_out, hide_groups=(), show_groups=()):
    """A working copy of a .bbmodel with `png` as its texture (for renders only)."""
    with open(bbmodel_in, encoding='utf-8') as fh:
        d = json.load(fh)
    buf = io.BytesIO()
    Image.open(png).save(buf, 'PNG')
    d['textures'][0]['source'] = 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()
    for g in d['groups']:
        if g['name'] in hide_groups:
            g['visibility'] = False
        if g['name'] in show_groups:
            g['visibility'] = True
    with open(bbmodel_out, 'w', encoding='utf-8') as fh:
        json.dump(d, fh)
    return bbmodel_out


def standins_from(S, placeholder, prefix='si_', loose=True):
    """Copies the stand-in islands from the placeholder sheet (renders only: the game draws real items), and the
    pixels under every per-face-UV stand-in's faces (a stand-in jar, a stand-in frame)."""
    pim = Image.open(placeholder).convert('RGBA')
    ph = pim.load()
    for n in S.names(prefix):
        for f in S.faces(n):
            for c, r in f.each():
                S.px[f.x0 + c, f.y0 + r] = ph[f.x0 + c, f.y0 + r]
    if loose:
        for e in S.loose:
            for face in e.get('faces', {}).values():
                if not face or face.get('texture') is None:
                    continue
                u0, v0, u1, v1 = face['uv']
                for y in range(int(min(v0, v1)), int(max(v0, v1))):
                    for x in range(int(min(u0, u1)), int(max(u0, u1))):
                        if 0 <= x < min(S.W, pim.width) and 0 <= y < min(S.H, pim.height):
                            S.px[x, y] = ph[x, y]


# ===================================================================================================== batch two
def state_sprite(path, w, h, d, faces, fill='.', frames=1, frametime=None, mcmeta_frames=None):
    """A state texture for one element: its own box-UV layout at (0, 0) of a sprite whose sides are multiples of 16
    (so it never lowers the block atlas's mip level), one such block per frame stacked downward.
    faces(frame, side, c, r, W, H) -> key (None leaves the fill). The rest is filled with `fill` so mipmaps never pull
    in clear pixels. Writes the .mcmeta when frames > 1."""
    lay = _boxuv(w, h, d)
    fw = 16 * ((2 * (w + d) + 15) // 16)
    fh = 16 * ((h + d + 15) // 16)
    img = Image.new('RGBA', (fw, fh * frames), rgba(fill))
    px = img.load()
    for fr in range(frames):
        for side, (x0, y0, W, H) in lay.items():
            for r in range(H):
                for c in range(W):
                    k = faces(fr, side, c, r, W, H)
                    if k is not None:
                        px[x0 + c, fr * fh + y0 + r] = rgba(k)
    img.save(path)
    if frames > 1:
        write_mcmeta(path, frametime=frametime, frames=mcmeta_frames)
    return path


def boxuv_table(w, h, d):
    """The UVs of an element whose texture is its own box-UV layout at (0, 0), in sprite pixels (for the notes)."""
    lay = _boxuv(w, h, d)
    return {s: [x0, y0, x0 + W, y0 + H] for s, (x0, y0, W, H) in lay.items()}


def leather(f, field='e3', piping='e4', shade='e2', buttons=True, pitch=6):
    """Upholstery (the Cradle's cream leather), not sheet metal: a piped edge (top row and left column e4, the cord
    catching the light; bottom row and right column e2, the cord's shadow), a soft e3 field, and on faces 8x8 or
    larger deep-buttoned (Chesterfield) tufting: diagonal creases (e2) on a diamond lattice `pitch` px apart, a
    button (e1) where two creases cross, an e4 puff above-left of each button. Faces 1-2 px wide: the field with a
    piped top row."""
    W, H = f.W, f.H
    for c, r in f.each():
        if W <= 2:
            k = piping if r == 0 else field
        elif r == 0 or c == 0:
            k = piping
        elif r == H - 1 or c == W - 1:
            k = shade
        else:
            k = field
        f.put(c, r, k)
    if buttons and W >= 8 and H >= 8:
        o = 3
        for c in range(1, W - 1):
            for r in range(1, H - 1):
                a, b = (c - o - r + o) % pitch, (c - o + r - o) % pitch
                if a == 0 and b == 0:
                    f.put(c, r, 'e1')
                    f.put(c - 1, r - 1, piping)
                elif a == 0 or b == 0:
                    f.put(c, r, shade)


def japanned(f, pinstripe=True):
    """Black japanned enamel (the Gene Splicer's sewing machine): rubber black worn as a gloss paint. Row 0 the gloss
    lip (r3); on faces 5+ wide and 4+ tall a 1 px brass pinstripe (b3) under it; the field r1; the last row r0.
    Narrow faces: the r3 lip over r1."""
    for c, r in f.each():
        if r == 0:
            k = 'r3'
        elif r == 1 and pinstripe and f.W >= 5 and f.H >= 4:
            k = 'b3'
        elif r == f.H - 1 and f.H >= 3:
            k = 'r0'
        else:
            k = 'r1'
        f.put(c, r, k)


def pale_link(S, cube, field='e3', lit='e4', ends='e1', end_rows=1):
    """A pale arm member (the Gantry's links): every side one field (e3) with a lit arris column (e4) on the north and
    west faces when 3+ wide; the end rows e1 where it goes into a dark joint; caps e2."""
    for f in S.faces(cube, SIDES):
        f.fill(field)
        if f.side in ('north', 'west') and f.W >= 3:
            f.col(0, lit)
        for i in range(end_rows):
            f.row(i, ends)
            f.row(f.H - 1 - i, ends)
    S.face(cube, 'up').fill('e2')
    S.face(cube, 'down').fill('e2')


def coil(S, cube, lit='c5', gap='c1', top='c4'):
    """A coil spring seen from the side: turns and gaps alternating every row (c5 turn, c1 gap), so it reads as a
    spring and not a bar; its ends c4."""
    for f in S.faces(cube, SIDES):
        for c, r in f.each():
            f.put(c, r, lit if r % 2 == 0 else gap)
    S.face(cube, 'up').fill(top)
    S.face(cube, 'down').fill(top)


def tartan(f, field='t1', check='t2', pin='w1', shade='t0'):
    """A quiet company tartan (the Cradle's blanket): teal t1, a lighter check every 4 columns and every 4 rows (t2),
    a thin yellow pinline every 8 columns (w1), the last row t0 (the fold's shadow) on faces 3+ tall."""
    for c, r in f.each():
        k = field
        if c % 4 == 1 or (f.H >= 4 and r % 4 == 1):
            k = check
        if c % 8 == 5:
            k = pin
        if r == f.H - 1 and f.H >= 3:
            k = shade
        f.put(c, r, k)


# A 3x4 face for lettering on signs (one texel per stroke; '#' ink). Only the letters the signs need so far.
FONT34 = {
    'P': ['###', '#.#', '###', '#..'], 'H': ['#.#', '###', '#.#', '#.#'], 'O': ['###', '#.#', '#.#', '###'],
    'T': ['###', '.#.', '.#.', '.#.'], 'S': ['.##', '#..', '..#', '##.'], 'N': ['#.#', '###', '###', '#.#'],
    'A': ['.#.', '#.#', '###', '#.#'], 'E': ['###', '##.', '#..', '###'], 'R': ['##.', '#.#', '##.', '#.#'],
    'C': ['###', '#..', '#..', '###'], 'F': ['###', '##.', '#..', '#..'],
}


def letters_vertical(f, text, c0, r0, ink, gap=1):
    """Letters stacked top to bottom (a vertical sign), each 3 wide and 4 tall, `gap` rows between them."""
    r = r0
    for ch in text:
        for rr, line in enumerate(FONT34[ch]):
            for cc, k in enumerate(line):
                if k == '#':
                    f.put(c0 + cc, r + rr, ink)
        r += 4 + gap


def glowmask(S, keys_at):
    """A GeckoLib glowmask: a copy of the sheet with every pixel cleared except those listed [(cube, side, c, r)]."""
    g = Image.new('RGBA', (S.W, S.H), (0, 0, 0, 0))
    gp = g.load()
    for cube, side, c, r in keys_at:
        f = S.face(cube, side)
        gp[f.x0 + c, f.y0 + r] = S.px[f.x0 + c, f.y0 + r]
    return g


# ===================================================================================================== batch three
def wood(f, field='b1', lit='b2', under='b0', grain=True):
    """Dark polished wood (a courtroom rail, a chairman's desk, a gavel head, an office desk): the brass ramp's two
    darkest steps, which read as wood rather than metal (the Hopper's lesson turned round: a darkened brass field
    reads as wood, so use it where wood is meant). Row 0 the lit edge (b2), the field b1, the last row on faces 3+
    tall b0; grain: one b2 streak 3-5 px along the long axis on faces 6+ long, placed at a third of the way in."""
    for c, r in f.each():
        if r == 0 and f.H >= 2:
            k = lit
        elif r == f.H - 1 and f.H >= 3:
            k = under
        else:
            k = field
        f.put(c, r, k)
    if grain and f.W >= 6 and f.H >= 3:
        r = f.H // 2
        c0 = f.W // 3
        for c in range(c0, min(f.W - 1, c0 + 4)):
            f.put(c, r, lit)


def wood_top(f, field='b1', edge='b2'):
    """A wood top face: b1 with a b2 front-and-left edge and one b2 grain streak along its length."""
    x0, _, z0 = f.c['frm']
    xmax = x0 + f.c['w']
    f.by_world(lambda c, r, x, y, z: edge if (z < z0 + 1 or x > xmax - 1) else field)
    if f.W >= 6 and f.H >= 3:
        r = f.H // 2
        for c in range(f.W // 3, min(f.W - 1, f.W // 3 + 4)):
            f.put(c, r, edge)


def weave(f, a='t0', b='r2'):
    """A quiet woven felt (the Mount's partition): 2x2 cells alternating a horizontal pair (a over b) and a vertical
    pair (a beside b), two dark steps of near equal value (teal t0 and rubber r2), so it reads as cloth up close and
    as one dark field at range."""
    for c, r in f.each():
        cell = (c // 2 + r // 2) % 2
        if cell == 0:
            k = a if r % 2 == 0 else b
        else:
            k = a if c % 2 == 0 else b
        f.put(c, r, k)


def roundel(f, c0, r0):
    """The company roundel, 4x4: a brass ring (b4 lit, b2 shaded) round a teal field (t2) crossed by a yellow bar
    (w2). Corners left as they were."""
    rows = ['- b4 b4 -', 'b4 t2 t2 b3', 'b3 w2 w2 b2', '- b2 b2 -']
    f.rows(rows, c0=c0, r0=r0)


def tint_base(f, field='e5', edge='e4'):
    """A neutral light base for a part the code tints (multiplied): e5 with an e4 last row and right column, so the
    tint shows true with one step of shape."""
    for c, r in f.each():
        f.put(c, r, edge if (r == f.H - 1 and f.H >= 2) or (c == f.W - 1 and f.W >= 3) else field)


def shade_lamp_textures(prefix, out=OUT):
    """A lamp whose lens is a shade 4x2x4 (the Mount's banker's lamp): six state sprites, the shade's own box-UV at
    (0, 0) of a 16x16 sprite. Per state: sides (field, top row), top face, underside (the light spill under the shade).
    off: the green glass unlit (j1, j2, j2 top, r1 under). ready: lit green (G1, G2, top G1, under G3). wait: amber.
    work: 2 frames x 20 ticks (lit / dim). low: 2 frames x 20 (dim / off). fault: red, steady."""
    states = {
        'off': [('j1', 'j2', 'j2', 'r1')],
        'ready': [('G1', 'G2', 'G1', 'G3')],
        'wait': [('A1', 'A2', 'A1', 'A3')],
        'fault': [('R1', 'R2', 'R1', 'R3')],
        'work': [('A1', 'A2', 'A1', 'A3'), ('A0', 'A1', 'A0', 'A1')],
        'low': [('A0', 'A1', 'A0', 'A1'), ('A0', 'A0', 'A0', 'A0')],
    }
    paths = []
    for state, frames in states.items():
        def face(fr, side, c, r, W, H, frames=frames):
            fld, top_row, top, under = frames[fr]
            if side == 'up':
                return top_row if (r == 0 or c == 0) else top
            if side == 'down':
                return under
            return top_row if r == 0 else fld
        p = os.path.join(out, '%s_%s.png' % (prefix, state))
        state_sprite(p, 4, 2, 4, face, fill=frames[0][0], frames=len(frames), frametime=20 if len(frames) > 1 else None)
        paths.append(p)
        if len(frames) > 1:
            paths.append(p + '.mcmeta')
    return paths


def sheet_strip(S, path, y0, y1, painter):
    """A state texture that is a horizontal strip of the sheet (rows y0..y1, a multiple of 16 tall, the sheet's width):
    the elements keep their sheet u and take v - y0. painter(S) repaints the elements inside the strip first."""
    painter(S)
    S.img.crop((0, y0, S.W, y1)).save(path)
    return path


if __name__ == '__main__':
    # python -I art/machines/kit/paint/kitpaint.py : writes the kit's shared textures (lamp lenses, gauge faces)
    for _p in lens_textures() + gauge_textures():
        print('wrote', _p)

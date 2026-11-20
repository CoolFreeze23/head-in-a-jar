"""Corporate Wellness Treadmill: the painted sheet and the belt, every pixel a palette key, by the kit's constructions.

    python -I art/machines/treadmill/paint/paint_treadmill.py           # writes the textures into the mod assets
    python -I art/machines/treadmill/paint/paint_treadmill.py --check   # no writes: fails if a PNG would differ
Then: python -I tools/tex/tex.py adopt block/machine/treadmill.png block/machine/treadmill_belt.png
      block/machine/treadmill_belt_running.png block/machine/treadmill_belt_running.png.mcmeta

Writes block/machine/treadmill.png (the box-UV sheet of treadmill.bbmodel), block/machine/treadmill_belt.png (16x16,
the belt standing still) and block/machine/treadmill_belt_running.png (16 frames of 16x16, frametime 1: the pattern
moves 1 px a tick toward the foot; one whole lap of the 16 px pattern every 16 ticks).

The value plan (planned on a render about 110 px tall first):
  LIGHT  the ivory motor hood (e2 field, e3 lip and rim), the dial's face (its needle dark)
  DARK   the belt (r2, ribs r3 over an r1 shadow), the chassis under the rails (s1, its top row s0: the rails' shadow),
         the instrument box (s1)
  MID    the steel rails, posts and legs (s3 fields, s4 lit rows)
  ACCENT brass: the dial's bezel and hub, the handlebar, the axle caps, the maker's plate, the rails' end caps
Story marks, once each: the belt's lacing (a steel splice across it, once a lap: what the eye follows when it runs);
the handlebar's rubber grips; the dial's red mark at the top of its scale (the reading the company wants).
"""
import argparse
import io
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'kit', 'paint'))
import kitpaint as K  # noqa: E402

MDIR = os.path.join(HERE, '..')
BBMODEL = os.path.join(MDIR, 'treadmill.bbmodel')
SHEET = os.path.join(K.OUT, 'treadmill.png')
BELT = os.path.join(K.OUT, 'treadmill_belt.png')
RUNNING = os.path.join(K.OUT, 'treadmill_belt_running.png')
FRAMES = 16
# Belt speed in px a tick (frametime 1, each frame the pattern moved SPEED px toward the foot). The walk-in-place stride
# is matched to it in the game (a vanilla leg L blocks long swinging cos(p * 0.6662) * 1.4 * s sweeps its foot back at
# about L * 0.933 * s * s blocks a tick at mid-stance, s being the stride the client sets): 1 px a tick (0.0625) suits
# s 0.30 for a cow (L 0.75) and 0.46 for a chicken (L 0.31); s 0.55 on a cow wants 3 px a tick.
SPEED = 1


# ---------------------------------------------------------------------------------------------------- the sheet
def hood(S):
    for n in ('hood_a', 'hood_b'):
        for f in S.faces(n, K.SIDES):
            K.enamel_body(f, 0, 8)
        K.enamel_top(S.face(n, 'up'))
        S.face(n, 'down').fill('e0')
    for n in S.names('hood_rim_'):
        K.fill(S, n, 'e3')
        S.face(n, 'up').fill('e4')
        S.face(n, 'down').fill('e1')
    for n in ('hood_rim_n_l', 'hood_rim_n_r'):   # a rivet at each end of the rim's side strips (T18: in pairs)
        f = S.face(n, 'north')
        f.put(0, 0, 'e5')
        f.put(0, f.H - 1, 'e1')
    # the front panel inside the rim: two louvres each side of the maker's plate (r0 slot over its e3 lit lip)
    f = S.face('hood_a', 'north')

    def louvre(c, r, x, y, z):
        if 2.5 < abs(x) < 6 and (3 < y < 4 or 5 < y < 6):
            return 'e0'                     # a slot in enamel: the enamel's darkest step (r0 read as hard black)
        if 2.5 < abs(x) < 6 and (2 < y < 3 or 4 < y < 5):
            return 'e3'
        return None
    f.by_world(louvre)
    K.makers_plate(S, 'makers_plate', 'north')


def console(S):
    for n in ('post_l', 'post_r'):
        K.steel_part(S, n)
        for f in S.faces(n, K.SIDES):
            f.row(f.H - 1, 's1')          # where it stands on the hood
    K.dark_steel(S, 'console')
    S.face('console', 'up').fill('s1')    # seen only through the gap under the bar: keep it dark
    # the console's front: the dial face inside the bezel (pale enamel under a dark needle: a teal face and a pale
    # needle did not read at 5 blocks), the scale's start tick and the red mark at its top, two texels each; two rubber
    # push buttons either side of the dial. On a north face world +x is the viewer's left.
    f = S.face('console', 'north')

    def front(c, r, x, y, z):
        if -2 < x < 2 and 9 < y < 13:
            if x < -1 and y > 11:
                return 'R1'                 # the company's target: the top right of the scale, as seen
            if x > 1 and y > 11:
                return 'z2'                 # the scale's start, top left as seen: green (a grey start did not read)
            return 'e4'
        if 3.5 < abs(x) < 5 and 9.5 < y < 11:
            return 'r2' if y > 10 else 'r0'
        return None
    f.by_world(front)
    for n in ('bezel_t', 'bezel_b', 'bezel_l', 'bezel_r'):
        K.small(S, n, 'b', 3)
    S.face('bezel_t', 'north').fill('b4')
    S.face('bezel_r', 'north').fill('b3')     # bezel_r (+x) is the viewer's left on a north face: the lit side
    S.face('bezel_l', 'north').fill('b2')
    S.face('bezel_b', 'north').fill('b2')
    # the needle: pale (it is the reading), its underside a step down
    for n in S.names('needle_'):
        K.small(S, n, 'r', 1, top='r2', bottom='r0')
    K.small(S, 'hub', 'b', 4, top='b5', bottom='b2')
    # the handlebar: brass, rubber grips at both ends (2 px)
    K.brass_plate(S, 'handlebar')
    S.face('handlebar', 'up').fill('b3')      # 1 px wide: all lit edge read lemon
    for f in S.faces('handlebar', ('north', 'south', 'up', 'down')):
        for c in range(f.W):
            x, y, z = f.world(c, 0)
            if abs(x) > 4:
                f.col(c, 'r2' if f.side == 'up' else 'r1')


def deck(S):
    for n in ('chassis_h', 'chassis_f'):
        for f in S.faces(n, K.SIDES):
            f.fill('s1')
            f.row(0, 's0')                   # the rails' shadow on the band under them
        S.face(n, 'up').fill('s0')
        S.face(n, 'down').fill('s0')
    S.face('chassis_f', 'south').fill('s0')  # under the foot roller: a hollow
    for n in ('belt_h', 'belt_f', 'belt_wrap'):
        K.fill(S, n, 'r1')
        S.face(n, 'down').fill('r0')
    for n in S.names('rail_'):
        for f in S.faces(n, K.SIDES):
            f.fill('s3')
            f.row(0, 's4')
            if f.H >= 3:
                f.row(f.H - 1, 's2')
        # the faces toward the belt stand over it: in shadow
        for f in S.faces(n, ('east', 'west')):
            x, y, z = f.world(0, 0)
            if abs(x) < 7:
                f.fill('s2')
                f.row(0, 's3')
        # rivets at both ends of each outer face: a splice plate at the seam, a stop at each end
        for f in S.faces(n, ('east', 'west')):
            x, y, z = f.world(0, 0)
            if abs(x) > 7:
                K.rivet(f, 1, 1, 's5', 's2')
                K.rivet(f, f.W - 3, 1, 's5', 's2')
        up = S.face(n, 'up')
        up.fill('s4')
        for c, r in up.each():                # the outer edge a step down: the rail's top reads as a rounded bar
            x, y, z = up.world(c, r)
            if abs(x) > 7:
                up.put(c, r, 's3')
        S.face(n, 'down').fill('s1')
    for n in S.names('leg_'):
        K.small(S, n, 's', 2)
    for n in S.names('cap_'):
        K.small(S, n, 'b', 3, top='b4', bottom='b1')
        for f in S.faces(n, ('east', 'west')):
            x, y, z = f.world(0, 0)
            if abs(x) > 7.5:                  # the outer face: an axle boss, lit top-left
                f.fill('b3')
                f.put(0, 0, 'b4')
                f.put(1, 1, 'b2')
    for n in S.names('foot_'):
        for f in S.faces(n, K.SIDES):
            f.fill('r1')
        S.face(n, 'up').fill('r2')
        S.face(n, 'down').fill('r0')
    for n in S.names('endcap_'):              # brass (warning yellow read as toy plastic beside the brass, twice)
        for f in S.faces(n, K.SIDES):
            K.brass_rail(f)
        S.face(n, 'up').fill('b3')
        S.face(n, 'down').fill('b1')


def paint(S):
    hood(S)
    console(S)
    deck(S)


# ---------------------------------------------------------------------------------------------------- the belt
# One lap of the belt's pattern, 16 rows (v runs toward the foot), 16 columns (the belt shows columns 2-13; 0-1 and
# 14-15 lie under the rails). Ribs every 4 rows: the rib's lit row r3 over its shadow r1, the field r2 (r1 read as a
# hole in the floor at 8 blocks); the two edge
# columns r0 (the gap at the rails); the lacing once a lap on rows 8-9: steel staples (s3) with their shadows (s1);
# no scuffs (critics read placed scuffs as noise or stitching).
def belt_rows():
    rows = []
    for v in range(16):
        row = []
        for u in range(16):
            if u in (2, 13):
                k = 'r0'
            elif v in (8, 9):
                if u % 2 == 1 and 3 <= u <= 11:
                    k = 's3' if v == 8 else 's1'
                else:
                    k = 'r2' if v == 8 else 'r1'
            elif v % 4 == 0:
                k = 'r3'
            elif v % 4 == 1:
                k = 'r1'
            else:
                k = 'r2'
            row.append(k)
        rows.append(row)
    return rows


def belt_image(shift=0):
    im = Image.new('RGBA', (16, 16))
    px = im.load()
    rows = belt_rows()
    for v in range(16):
        for u in range(16):
            px[u, v] = K.rgba(rows[(v - shift) % 16][u])
    return im


def running_image():
    im = Image.new('RGBA', (16, 16 * FRAMES))
    for k in range(FRAMES):
        im.paste(belt_image(k * SPEED), (0, 16 * k))
    return im


def _png(im):
    b = io.BytesIO()
    im.save(b, format='PNG', optimize=True)
    return b.getvalue()


def outputs():
    S = K.Sheet(BBMODEL)
    paint(S)
    miss = S.missing()
    if miss:
        sys.exit('unpainted faces: %s' % miss)
    return {SHEET: _png(S.img), BELT: _png(belt_image(0)), RUNNING: _png(running_image())}


MCMETA = '{\n "animation": {\n  "frametime": 1\n }\n}\n'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--check', action='store_true')
    a = ap.parse_args()
    out = outputs()
    out[RUNNING + '.mcmeta'] = MCMETA.encode()
    bad = []
    for path, data in out.items():
        if a.check:
            if not os.path.exists(path) or open(path, 'rb').read() != data:
                bad.append(os.path.basename(path))
        else:
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, 'wb') as fh:
                fh.write(data)
            print('wrote', path)
    if bad:
        sys.exit('differ: %s' % bad)


if __name__ == '__main__':
    main()

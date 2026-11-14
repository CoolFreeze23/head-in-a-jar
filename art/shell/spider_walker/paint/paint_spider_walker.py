"""The Spider Walker (the tea trolley's biscuit tin on eight bent-wire legs): the painted sheet, every pixel placed from
palette keys.

Source of `textures/entity/shell/spider_walker.png` and `spider_walker_glowmask.png` (adopted as `painted`). No
randomness. The Rover's sibling, cheaper goods: cream enamel and bare tin, one brass part (the lid rim, with the key and
the thighs' wire), a little wear placed on purpose.

    python -I art/shell/spider_walker/paint/paint_spider_walker.py                # writes the two PNGs into the assets
    python -I art/shell/spider_walker/paint/paint_spider_walker.py --preview DIR  # also DIR/spider_walker_preview.png

Value plan:
- the tin: the Rover's cream a step up (e4 field, e5 lit row under the rim, e2 foot: the tin sits in the lid's shadow
  and must still read cream at 20 blocks) with a PRINTED BAND low down in the Rover's teal (t3, a deterministic dot
  print in e5: low contrast, so it reads as print, not as a panel); bare tin (s4) showing
  at the octagon's corners under the rim and in a 1 px ring where each leg's hip meets the tin;
- the lid rim brass b3 (b4 outer ring on top, b1 under); the seat and collar dark (the darks round the brass and round
  the jar's foot);
- the legs light: brass thighs (b3, b4 top), ivory knee caps (e4, e5 top), steel shins (s3, s4 top), and only the
  1 px points dark (rubber), so eight legs stay eight thin strokes and never a dark fringe;
- the claws hazard yellow and small (one tone a face); the key brass with one lobe's back face marked bright so its
  winding shows; four green eyes on a dark visor and two amber reflectors in the glowmask.
"""
import argparse
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
sys.path.insert(0, os.path.join(ROOT, 'art', 'mecha', 'hopper', 'paint'))
from paintlib import COL, SIDES, Sheet  # noqa: E402

BBMODEL = os.path.join(ROOT, 'art', 'shell', 'spider_walker', 'spider_walker.bbmodel')
PLACEHOLDER = os.path.join(ROOT, 'art', 'shell', 'spider_walker', 'spider_walker_placeholder.png')
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'headinajar', 'textures', 'entity', 'shell')

S = Sheet(BBMODEL)
F = S.face
LEGS = ['fl', 'l2', 'l3', 'rl', 'fr', 'r2', 'r3', 'rr']
# the hips' centres on the tin (x, z) at y 8, from spider_walker_geo.HIPS (read off the model's hip cubes here)
HIPS = {}
for _n in LEGS:
    _c = S.cubes['hip_' + _n]
    HIPS[_n] = (_c['frm'][0] + _c['w'] / 2.0, _c['frm'][2] + _c['d'] / 2.0)
HIP_Y = S.cubes['hip_fl']['frm'][1] + S.cubes['hip_fl']['h'] / 2.0
BAND = (5.0, 7.0)          # the printed band's height on the tin (world y), under the hips


def fill(cube, key, sides=SIDES + ('up', 'down')):
    for f in S.faces(cube, sides):
        f.fill(key)


def small(cube, m, field, sides=SIDES):
    """A small part: one tone a face (field on the sides, a step up on top, a step down underneath)."""
    up, dn = m + str(field + 1), m + str(max(0, field - 1))
    for f in S.faces(cube, sides):
        f.fill(m + str(field))
    for f in S.faces(cube, ('up',)):
        f.fill(up if up in COL else m + str(field))
    for f in S.faces(cube, ('down',)):
        f.fill(dn)


def lamp(lens, cap, m, front):
    for f in S.faces(lens):
        f.fill('r0')
    f = F(lens, front)
    for c, r in f.each():
        f.put(c, r, m + ('0' if (c == f.W - 1 and r == f.H - 1) else '1'))
    for f in S.faces(cap):
        f.fill('r0')
    f = F(cap, front)
    for c, r in f.each():
        f.put(c, r, m + ('3' if (c, r) == (0, 0) else '2'), lit=True)


def near_hip(x, y, z):
    """A 1 px ring of bare tin round each hip boss (the boss is 3 x 3), on the tin's faces."""
    return abs(y - HIP_Y) < 2.1 and any(max(abs(x - hx), abs(z - hz)) < 2.1 for hx, hz in HIPS.values())


def tin():
    """The biscuit tin: four boxes making an octagon. Their side faces carry the cream, the printed band and the wear;
    which part of each face shows is decided by the geometry, so every face gets the same rule by world height."""
    for n in ('tin_a', 'tin_b', 'tin_c', 'tin_d'):
        top = S.cubes[n]['frm'][1] + S.cubes[n]['h']
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                x, y, z = f.world(c, r)
                if near_hip(x, y, z):
                    k = 's4'                            # bare tin where the legs meet it
                elif y > top - 1:
                    k = 's4' if c in (0, f.W - 1) else 'e5'     # the lit row under the rim; worn at the corners
                elif r == f.H - 1:
                    k = 'e2'                            # the foot
                elif BAND[0] <= y < BAND[1]:
                    # the printed band: the Rover's teal with a cream dot print, offset a row (a biscuit-tin pattern)
                    k = 'e5' if (c + 2 * int(y)) % 4 == 0 else 't3'
                else:
                    k = 'e4'
                f.put(c, r, k)
        F(n, 'up').fill('e4')
        F(n, 'down').fill('e2')
    small('belly', 's', 2)


def lid():
    for n in ('lid_a', 'lid_b', 'lid_c', 'lid_d'):
        for f in S.faces(n, SIDES):
            f.fill('b3')
        f = F(n, 'up')
        for c, r in f.each():
            f.put(c, r, 'b4' if (c in (0, f.W - 1) or r in (0, f.H - 1)) else 'b3')
        F(n, 'down').fill('b1')


def setting():
    """The dark seat and collar round the jar's foot; the four claws hazard yellow, one tone a face."""
    for f in S.faces('seat', SIDES + ('up',)):
        f.fill('s1')
    F('seat', 'down').fill('s0')
    for n in ('collar_f', 'collar_b', 'collar_l', 'collar_r'):
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, 's2' if r == 0 else 's1')
        F(n, 'up').fill('s2')
        F(n, 'down').fill('s0')
    for end in ('f', 'b'):
        for side in ('l', 'r'):
            for part in ('claw', 'claw_tip', 'claw_hook'):
                small('%s_%s%s' % (part, end, side), 'w', 2)


def key_and_tail():
    small('key_stem', 's', 3)
    for n in ('key_bow_l', 'key_bow_r'):
        for f in S.faces(n, SIDES):
            f.fill('b3')
        F(n, 'up').fill('b4')
        F(n, 'down').fill('b1')
    # the mark: the left lobe's back face (seen from behind) bright with a b5 outer column; the right lobe's plain
    # b3 with a b2 inner column. Half bright, half plain: a quarter turn shows.
    f = F('key_bow_l', 'south')
    for c, r in f.each():
        f.put(c, r, 'b4')
    f.col(f.W - 1 if f.world(f.W - 1, 0)[0] < f.world(0, 0)[0] else 0, 'b5')
    f = F('key_bow_r', 'south')
    f.fill('b3')
    f.col(f.W - 1 if f.world(f.W - 1, 0)[0] < f.world(0, 0)[0] else 0, 'b2')
    for s in ('l', 'r'):
        lamp('tail_lens_' + s, 'tail_lit_' + s, 'A', 'south')


def face():
    for f in S.faces('visor', SIDES):
        for c, r in f.each():
            f.put(c, r, 's2' if r == 0 else 's1')
    F('visor', 'up').fill('s2')
    F('visor', 'down').fill('s0')
    for e in 'abcd':
        lamp('eye_lens_' + e, 'eye_lit_' + e, 'G', 'north')
    small('gland', 's', 1)
    small('nozzle', 'b', 3)


def legs():
    for n in LEGS:
        small('hip_' + n, 's', 2)
        small('femur_' + n, 'b', 3)
        small('knee_' + n, 'e', 4)
        small('tibia_' + n, 's', 3)
        small('point_' + n, 'r', 1)


def paint_all():
    tin()
    lid()
    setting()
    key_and_tail()
    face()
    legs()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview')
    ap.add_argument('--out', default=OUT)
    a = ap.parse_args()
    paint_all()
    miss = S.missing()
    if miss:
        sys.stderr.write('unpainted faces: %s\n' % miss)
        return 1
    os.makedirs(a.out, exist_ok=True)
    S.img.save(os.path.join(a.out, 'spider_walker.png'))
    S.glow.save(os.path.join(a.out, 'spider_walker_glowmask.png'))
    if a.preview:
        from PIL import Image
        ph = Image.open(PLACEHOLDER).convert('RGBA').load()
        pv = S.img.copy()
        p = pv.load()
        for n in S.cubes:
            if n.startswith('standin'):
                for f in S.faces(n):
                    for c, r in f.each():
                        p[f.x0 + c, f.y0 + r] = ph[f.x0 + c, f.y0 + r]
        pv.save(os.path.join(a.preview, 'spider_walker_preview.png'))
    print('wrote spider_walker.png and spider_walker_glowmask.png to %s' % a.out)
    return 0


if __name__ == '__main__':
    sys.exit(main())

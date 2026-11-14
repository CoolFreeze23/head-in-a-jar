"""The Kite (the company's air courier): the painted sheet, every pixel placed from palette keys.

Source of `textures/entity/mecha/kite.png` and `kite_glowmask.png` (adopted as `painted` in the texture manifest).
No randomness. Same rules as the other frames.

    python -I art/mecha/kite/paint/paint_kite.py                # writes the two PNGs into the mod assets
    python -I art/mecha/kite/paint/paint_kite.py --preview DIR  # also DIR/kite_preview.png

The value plan (decided at about 110 px before detail):
- WINGS read light-dark-light through a beat: the upper surface of each 1 px membrane a calm pale enamel (e3, e4 along
  the spar) with feather breaks (e1 lines across it every 4 px) so the folded wing reads as feathers; the UNDER surface
  a mid tone (e1) crossed by the dark ribs (s0), so a wing seen from below or edge-on in the downstroke goes dark. The
  brass spar is the strong leading line (b4 top row, b3, b1 under).
- BODY: ivory enamel fuselage with a courier's livery on each flank: a teal post stripe under a pale pinline and a
  white envelope mark (a V on white), the company post, not a crest.
- LANTERN quieter than the jar: steel posts (s2), a dark band (s1), a 2 px brass lip lit on its outer row only, the
  brass collar as its floor rim; the belly plate above it the darkest field on the machine (r0/s0), so the glass is
  the bright thing under a dark body.
- LEGS and talons dark and thin with a lit edge: shins s1 with an s3 lit column and top, toes rubber with a lit r3
  top row, claws dark steel with a lit tip, so they hold against grass.
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
sys.path.insert(0, os.path.join(ROOT, 'art', 'mecha', 'hopper', 'paint'))
from paintlib import COL, SIDES, Sheet  # noqa: E402

BBMODEL = os.path.join(ROOT, 'art', 'mecha', 'kite', 'kite.bbmodel')
PLACEHOLDER = os.path.join(ROOT, 'art', 'mecha', 'kite', 'kite_placeholder.png')
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'headinajar', 'textures', 'entity', 'mecha')

S = Sheet(BBMODEL)
F = S.face


def pair(name):
    return (name + '_l', name + '_r')


def outer(name):
    return 'west' if name.endswith('_l') else 'east'


def fill(cube, key, sides=SIDES + ('up', 'down')):
    for f in S.faces(cube, sides):
        f.fill(key)


def rivet(f, c, r, lit, dark):
    f.put(c, r, lit)
    f.put(c + 1, r + 1, dark)


def small(cube, m, field, bolt=True, sides=SIDES):
    up, dn = m + str(field + 1), m + str(max(0, field - 1))
    hi = m + str(field + 2) if (m + str(field + 2)) in COL else up
    for f in S.faces(cube, sides):
        f.fill(m + str(field))
        if bolt and f.W >= 2 and f.H >= 3:
            rivet(f, 0, 1, hi, dn)
    for f in S.faces(cube, ('up',)):
        f.fill(up)
    for f in S.faces(cube, ('down',)):
        f.fill(dn)


def brass_bar(f):
    for c, r in f.each():
        end = f.W >= 4 and c in (0, f.W - 1)
        if r == 0:
            k = 'b3' if end else 'b4'
        elif r == f.H - 1 and f.H >= 3:
            k = 'b1'
        else:
            k = 'b2' if end else 'b3'
        f.put(c, r, k)


def brass_top(f):
    x0, _, z0 = S.cubes[f.cube]['frm']
    for c, r in f.each():
        x, y, z = f.world(c, r)
        f.put(c, r, 'b4' if (z < z0 + 1 or x < x0 + 1) else 'b3')


def brass_housing(cube):
    for f in S.faces(cube, SIDES):
        for c, r in f.each():
            end = c in (0, f.W - 1)
            k = ('b3' if end else 'b4') if r == 0 else ('b1' if r == f.H - 1 else ('b2' if end else 'b3'))
            f.put(c, r, k)
    brass_top(F(cube, 'up'))
    F(cube, 'down').fill('b1')


def lamp(lens, cap, m, faces_lit):
    for f in S.faces(lens):
        f.fill(m + '0' if f.side != 'down' else 'r0')
    for f in S.faces(cap):
        if f.side in faces_lit:
            for c, r in f.each():
                f.put(c, r, m + ('3' if (c, r) == (0, 0) else '2'), lit=True)
        else:
            f.fill('r0')


# ===================================================================================================== body
def paint_body():
    for n in ('fuselage_a', 'fuselage_b'):
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, 'e4' if r == 0 else ('e1' if r == f.H - 1 else 'e2'))
        f = F(n, 'up')
        x0, _, z0 = S.cubes[n]['frm']
        for c, r in f.each():
            x, y, z = f.world(c, r)
            f.put(c, r, 'e4' if (z < z0 + 1 or x < x0 + 1) else 'e3')
        F(n, 'down').fill('e0')
    # the courier's livery on each flank (fuselage_a's east and west faces, 18 wide x 8 tall): a teal post stripe
    # (2 rows) under a pale pinline, running the length, and the envelope mark ahead of the wing root
    for side in ('east', 'west'):
        f = F('fuselage_a', side)
        for c, r in f.each():
            if r == 4:
                f.put(c, r, 'e4')
            elif r in (5, 6):
                f.put(c, r, 't2' if r == 5 else 't1')
        env = ['e5 e5 e5 e5 e5', 'r1 e5 e5 e5 r1', 'e5 r1 e5 r1 e5', 'e5 e5 r1 e5 e5']
        c0 = 1 if side == 'west' else f.W - 6           # toward the nose on both sides
        f.rows(env, c0=c0, r0=0)
    fill('belly_plate', 's0')                           # the darkest field: the lantern's ceiling
    F('belly_plate', 'down').fill('r0')
    F('belly_plate', 'up').fill('s1')
    F('chest_plate', 'north').rows(['b4 b4 b4 b4 b4 b4 b4 b3',
                                    'b3 b5 b3 b3 b3 b3 b5 b2',
                                    'b3 b3 b1 b1 b1 b1 b3 b2',
                                    'b3 b3 b3 b3 b3 b3 b3 b2',
                                    'b2 b1 b1 b1 b1 b1 b1 b1'])
    for side in ('east', 'west', 'south', 'down'):
        F('chest_plate', side).fill('b2')
    F('chest_plate', 'up').fill('b4')
    small('neck', 's', 1, bolt=False)
    for f in S.faces('head', SIDES):
        for c, r in f.each():
            f.put(c, r, 'e4' if r == 0 else ('e1' if r == f.H - 1 else 'e2'))
    F('head', 'up').fill('e3')
    F('head', 'down').fill('e0')
    small('head_brow', 's', 1, bolt=False)
    F('head_brow', 'down').fill('r0')
    lamp('visor_lens', 'visor_lit', 'A', ('north',))
    small('beak', 'b', 3, bolt=False)
    for n in ('tail_plane', 'tail_fan_l', 'tail_fan_r'):
        for f in S.faces(n, SIDES):
            f.fill('e2')
        f = F(n, 'up')
        for c, r in f.each():
            x, y, z = f.world(c, r)
            f.put(c, r, 'e1' if int(abs(x) + 0.5) % 3 == 2 and n == 'tail_plane' else 'e3')   # tail feather breaks
        F(n, 'down').fill('e1')
    for f in S.faces('rudder', SIDES):
        brass_bar(f)
    F('rudder', 'up').fill('b4')
    F('rudder', 'down').fill('b1')
    for n in pair('wing_root'):
        brass_housing(n)


# ===================================================================================================== wings
def membrane(n, spar_edge_z):
    """A 1 px wing membrane. Upper surface e3 with an e4 run along the spar and e1 feather breaks across it every
    4 px (by world x, so the inner and outer wing line up); under surface a mid e1 (the ribs below are separate dark
    cubes); 1 px edges e2."""
    f = F(n, 'up')
    for c, r in f.each():
        x, y, z = f.world(c, r)
        k = 'e3'
        if z < spar_edge_z + 1:
            k = 'e4'
        elif int(abs(x)) % 4 == 3:
            k = 'e1'
        f.put(c, r, k)
    F(n, 'down').fill('e1')
    for f in S.faces(n, SIDES):
        f.fill('e2')


def paint_wings():
    for s in ('l', 'r'):
        for n, zedge in (('membrane_in_' + s, -2), ('membrane_out_' + s, -3)):
            membrane(n, zedge)
        for n in ('spar_in_' + s, 'spar_out_' + s):
            for f in S.faces(n, SIDES):
                brass_bar(f)
            f = F(n, 'up')
            for c, r in f.each():
                f.put(c, r, 'b4')
            F(n, 'down').fill('b1')
        for n in ('rib_in0_' + s, 'rib_in1_' + s, 'rib_in2_' + s, 'rib_out_' + s):
            fill(n, 's0')
            F(n, 'down').fill('s1')
        for i in ('0', '1', '2'):                       # finger primaries: the feather tips
            n = 'primary_%s_%s' % (i, s)
            f = F(n, 'up')
            for c, r in f.each():
                x, y, z = f.world(c, r)
                f.put(c, r, 'e2' if abs(x) > 33.5 - int(i) else 'e3')
            F(n, 'down').fill('e1')
            for f in S.faces(n, SIDES):
                f.fill('e2')
        lamp('nav_lens_' + s, 'nav_lit_' + s, 'R' if s == 'l' else 'G', ('up', 'north', outer('nav_lit_' + s)))


# ===================================================================================================== lantern
def paint_lantern():
    f = F('seat', 'up')
    for c, r in f.each():
        x, y, z = f.world(c, r)
        f.put(c, r, 'w2' if (abs(x), abs(z)) in ((4.5, 4.5), (3.5, 4.5), (4.5, 3.5)) else 's1')
    for f in S.faces('seat', SIDES):
        f.fill('s2')
    F('seat', 'down').fill('s0')
    for n in ('collar_base_f', 'collar_base_b', 'collar_base_l', 'collar_base_r'):
        fill(n, 'r0')
    for n in ('collar_f', 'collar_b', 'collar_l', 'collar_r'):
        for f in S.faces(n, SIDES):
            brass_bar(f)
        f = F(n, 'up')
        for c, r in f.each():
            x, y, z = f.world(c, r)
            f.put(c, r, 'b4' if max(abs(x), abs(z)) > 8.5 else 'b3')
        F(n, 'down').fill('b1')                         # the floor rim, seen from below
    small('hinge', 's', 1)
    for n in ('ring_f', 'ring_b', 'ring_l', 'ring_r'):
        small(n, 's', 1)
    for n in ('lip_f', 'lip_b', 'lip_l', 'lip_r'):
        f = F(n, 'up')
        for c, r in f.each():
            x, y, z = f.world(c, r)
            f.put(c, r, 'b4' if max(abs(x), abs(z)) > 7.5 else 'b3')
        for f in S.faces(n, SIDES):
            f.fill('b3')
        F(n, 'down').fill('b1')
    for n in ('post_f_l', 'post_f_r', 'post_b_l', 'post_b_r'):
        for f in S.faces(n, SIDES):
            f.fill('s2')
            f.row(0, 's3')
            f.row(f.H - 1, 's1')
        F(n, 'up').fill('s3')
        F(n, 'down').fill('s1')


# ===================================================================================================== arms, legs
def paint_limbs():
    for s in ('l', 'r'):
        small('outrigger_' + s, 's', 1)
        small('shoulder_' + s, 's', 2)
        for n in ('upper_' + s, 'fore_' + s):
            for f in S.faces(n, SIDES):
                f.fill('s2')
                f.row(0, 's3')
            F(n, 'up').fill('s3')
            F(n, 'down').fill('s1')
        small('elbow_' + s, 's', 0, bolt=False)
        small('cuff_' + s, 'r', 1, bolt=False)
        small('hip_drum_' + s, 's', 1)
        n = 'thigh_' + s
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, 'e4' if r == 0 else ('e1' if r == f.H - 1 else 'e3'))
        F(n, 'up').fill('e4')
        F(n, 'down').fill('e1')
        brass_housing('knee_' + s)
        n = 'shin_' + s                                 # dark and thin, with a lit edge so it holds against grass
        for f in S.faces(n, SIDES):
            f.fill('s1')
            f.row(0, 's3')
            if f.side in ('north', 'west', 'east'):
                f.col(0, 's3')
            f.row(f.H - 1, 's0')
        F(n, 'up').fill('s3')
        F(n, 'down').fill('s0')
        small('ankle_' + s, 's', 0, bolt=False)
        F('ankle_' + s, 'up').fill('s2')
        for n in ['toe_heel_' + s] + ['toe_%d_%s' % (i, s) for i in range(3)]:
            for f in S.faces(n):
                f.fill('r1')
            F(n, 'up').fill('r3')                       # the lit top line of each toe
            for f in S.faces(n, SIDES):
                f.row(0, 'r3')
            F(n, 'down').fill('r0')
        for i in range(3):
            n = 'claw_%d_%s' % (i, s)
            fill(n, 's0')
            F(n, 'north').put(0, 0, 's3')               # the claw's lit tip
            F(n, 'up').fill('s2')


def paint_all():
    paint_body()
    paint_wings()
    paint_lantern()
    paint_limbs()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview', help='also write DIR/kite_preview.png with the stand-ins filled')
    ap.add_argument('--out', default=OUT)
    a = ap.parse_args()
    paint_all()
    miss = S.missing()
    if miss:
        sys.stderr.write('unpainted faces: %s\n' % miss)
        return 1
    os.makedirs(a.out, exist_ok=True)
    S.img.save(os.path.join(a.out, 'kite.png'))
    S.glow.save(os.path.join(a.out, 'kite_glowmask.png'))
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
        pv.save(os.path.join(a.preview, 'kite_preview.png'))
    print('wrote kite.png and kite_glowmask.png to %s' % a.out)
    return 0


if __name__ == '__main__':
    sys.exit(main())

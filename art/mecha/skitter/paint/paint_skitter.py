"""The Skitter (Facilities' steeplejack): the painted sheet, every pixel placed from palette keys.

Source of `textures/entity/mecha/skitter.png` and `skitter_glowmask.png` (adopted as `painted` in the texture
manifest). No randomness. Same rules as the Hopper's final pass and the later frames.

    python -I art/mecha/skitter/paint/paint_skitter.py                # writes the two PNGs into the mod assets
    python -I art/mecha/skitter/paint/paint_skitter.py --preview DIR  # also DIR/skitter_preview.png
Reads `art/mecha/skitter/skitter.bbmodel` (sizes, positions, box-UV offsets; read only), `art/palette.json` and the
shared helpers in `art/mecha/hopper/paint/paintlib.py`.

The value plan (decided at about 110 px before detail):
- The LEGS are four pale strokes at 20 blocks: ivory femurs (e3 field, e4 top), light steel tibias (s3, s4 top), only
  the small knees and cuffs dark, rubber pads (r1, r2 rim, r0 tread) at the tips.
- The CAGE is quieter than the jar: steel posts (s2, s3 top), a dark 1 px band (s1), a 2 px brass lip lit only on its
  outer row (the Wheelhouse's lip).
- The REEL is the second read from behind: brass flanges (b3, a lit upper arc, a dark lower arc, a dark hub), the
  wound black line in turns (r2 and r1 columns round the drum, r3 on top), and the hi-vis tag the one accent (w3/w2
  with a pale retroreflective band).
- Hazard stripes only on the line gun's barrel (moving, 8 px faces) and the flank flaps' outer faces (5 px).
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
sys.path.insert(0, os.path.join(ROOT, 'art', 'mecha', 'hopper', 'paint'))
from paintlib import COL, SIDES, Sheet  # noqa: E402

BBMODEL = os.path.join(ROOT, 'art', 'mecha', 'skitter', 'skitter.bbmodel')
PLACEHOLDER = os.path.join(ROOT, 'art', 'mecha', 'skitter', 'skitter_placeholder.png')
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'headinajar', 'textures', 'entity', 'mecha')

S = Sheet(BBMODEL)
F = S.face
CORNERS = ('fl', 'fr', 'rl', 'rr')


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


def stripes_face(f):
    for c, r in f.each():
        y = (f.strip0 + c + r) % 4 < 2
        f.put(c, r, ('w3' if y else 'r2') if r == 0 else ('w2' if y else 'r1'))


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
            if r == 0:
                k = 'b3' if end else 'b4'
            elif r == f.H - 1:
                k = 'b1'
            else:
                k = 'b2' if end else 'b3'
            f.put(c, r, k)
    brass_top(F(cube, 'up'))
    F(cube, 'down').fill('b1')


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


# ===================================================================================================== body
def paint_body():
    # the pod: two crossed enamel boxes, the house belt at their foot; notch strips a step darker
    for n, notch_sides in (('pod_a', ('east', 'west')), ('pod_b', ('north', 'south'))):
        for f in S.faces(n, SIDES):
            notch = f.side in notch_sides
            for c, r in f.each():
                if r == 0:
                    k = 'e3' if notch else 'e4'
                elif r == f.H - 1:
                    k = 'e0'
                elif r == f.H - 2:
                    k = 'e1'
                elif r == f.H - 3 and f.H >= 7:
                    k = 'e3'
                else:
                    k = 'e1' if notch else 'e2'
                f.put(c, r, k)
        f = F(n, 'up')
        for c, r in f.each():
            x, y, z = f.world(c, r)
            m = max(abs(x), abs(z))
            f.put(c, r, 'e4' if m > 9.5 else ('e3' if m > 8.5 else 'r0'))
        F(n, 'down').fill('e0')
    for f in S.faces('belly', SIDES):
        f.fill('s0')
        f.row(0, 's1')
    F('belly', 'down').fill('r0')
    F('belly', 'up').fill('s0')
    # seat, collar
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
        F(n, 'down').fill('b1')
    # flank vents: rubber mouths, striped flaps (5 px outer faces)
    for s in ('l', 'r'):
        out_side = 'west' if s == 'l' else 'east'
        n = 'vent_mouth_' + s
        fill(n, 'r0')
        f = F(n, out_side)
        for c, r in f.each():
            f.put(c, r, 'r2' if r % 2 == 0 else 'r0')
        n = 'vent_flap_' + s
        for f in S.faces(n, SIDES):
            f.fill('w1')
        stripes_face(F(n, out_side))
        F(n, 'east' if s == 'l' else 'west').fill('s1')
        F(n, 'up').fill('w3')
        F(n, 'down').fill('w0')


# ===================================================================================================== cage
def paint_cage():
    for n in ('post_fl', 'post_fr', 'post_rl', 'post_rr'):
        for f in S.faces(n, SIDES):
            f.fill('s2')
            f.row(0, 's3')
            f.row(f.H - 1, 's1')
        F(n, 'up').fill('s3')
        F(n, 'down').fill('s1')
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
    for n in ('clamp_pad_l', 'clamp_pad_r'):
        fill(n, 'r2')
        F(n, 'up').fill('r3')
        F(n, 'down').fill('r0')


# ===================================================================================================== head, mandible, gun
def paint_front():
    small('neck', 's', 1, bolt=False)
    for f in S.faces('head', ('east', 'west', 'south')):
        for c, r in f.each():
            f.put(c, r, 'e3' if r == 0 else ('e0' if r == f.H - 1 else 'e2'))
    f = F('head', 'north')
    for c, r in f.each():
        f.put(c, r, 'e1' if r < 2 else ('e0' if r == f.H - 1 else 'e2'))
    F('head', 'up').fill('e3')
    F('head', 'down').fill('e0')
    for f in S.faces('brow', SIDES):
        f.fill('s1')
        f.row(0, 's2')
    F('brow', 'up').fill('s2')
    F('brow', 'down').fill('r0')                        # the brow's shadow over the eyes
    for s in ('l', 'r'):
        lamp('eye_lens_' + s, 'eye_lit_' + s, 'G', 'north')
    for f in S.faces('boom', SIDES):
        f.fill('s3')
        f.row(0, 's4')
    F('boom', 'up').fill('s4')
    F('boom', 'down').fill('s1')
    brass_housing('jaw_head')
    small('mand_lower', 's', 1, bolt=False)
    for f in S.faces('mand_upper'):
        f.fill('w2')
    F('mand_upper', 'up').fill('w3')
    F('mand_upper', 'down').fill('w1')
    # the line gun: dark bracket and breech, a striped steel barrel (it moves; 8 px faces), a brass muzzle ring
    small('launcher_bracket', 's', 1)
    small('launcher_breech', 's', 1, bolt=False)
    for f in S.faces('launcher_barrel', SIDES):
        if f.W >= 4:
            stripes_face(f)
        else:
            f.fill('s3')
            f.row(0, 's4')
    F('launcher_barrel', 'up').fill('s4')
    F('launcher_barrel', 'down').fill('s1')
    f = F('launcher_muzzle', 'north')
    f.rows(['b4 b4 b4 b3', 'b4 r0 r0 b2', 'b3 r0 r0 b2', 'b3 b2 b2 b1'])
    for side in ('east', 'west', 'south'):
        F('launcher_muzzle', side).fill('b3')
    F('launcher_muzzle', 'up').fill('b4')
    F('launcher_muzzle', 'down').fill('b1')


# ===================================================================================================== reel
def paint_reel():
    for s in ('l', 'r'):
        n = 'flange_' + s
        fill(n, 'b2')
        f = F(n, 'west' if s == 'l' else 'east')
        for c, r in f.each():
            x, y, z = f.world(c, r)
            dy, dz = y - 13, z - 14
            m = max(abs(dy), abs(dz))
            k = 'b3'
            if m > 4.4:
                k = 'b4' if (dy > 0 and dz < 2) else ('b1' if (dy < 0 and dz > -2) else 'b3')
            if m < 1.5:
                k = 's1'                                # the hub
            f.put(c, r, k)
        F(n, 'up').fill('b4')
        F(n, 'down').fill('b1')
    # the wound line: turns round the drum, as columns of r2 and r1 across x; lit on top
    for f in S.faces('cable'):
        for c, r in f.each():
            x, y, z = f.world(c, r)
            k = 'r2' if int(x + 10) % 2 == 0 else 'r1'
            if f.side == 'up':
                k = 'r3' if k == 'r2' else 'r2'
            elif f.side == 'down':
                k = 'r0'
            elif f.side in ('east', 'west'):
                k = 'r1'
            f.put(c, r, k)
    # the hi-vis tag: the one accent; a pale retroreflective band across its middle
    for f in S.faces('cable_tag'):
        f.fill('w2')
    f = F('cable_tag', 'up')
    for c, r in f.each():
        x, y, z = f.world(c, r)
        k = 'w3'
        if abs(x) < 1:
            k = 'e5'
        f.put(c, r, k)
    F('cable_tag', 'down').fill('w1')
    small('axle', 's', 1, bolt=False)
    small('stay', 's', 1, bolt=False)
    for s in ('l', 'r'):
        n = 'bracket_' + s
        for f in S.faces(n, SIDES):
            f.fill('s1')
            f.row(0, 's2')
        F(n, 'up').fill('s2')
        F(n, 'down').fill('s0')
        lamp('tail_lens_' + s, 'tail_lit_' + s, 'A', 'south')


# ===================================================================================================== legs
def paint_legs():
    for cn in CORNERS:
        brass_housing('hip_drum_' + cn)
        n = 'femur_' + cn                               # the pale stroke
        for f in S.faces(n):
            f.fill('e3')
        for f in S.faces(n, SIDES):
            f.row(0, 'e4')
            f.row(f.H - 1, 'e2')
        F(n, 'up').fill('e4')
        F(n, 'down').fill('e1')
        small('knee_' + cn, 's', 0, bolt=False)
        n = 'tibia_' + cn
        for f in S.faces(n):
            f.fill('s3')
        for f in S.faces(n, SIDES):
            f.row(0, 's4')
        F(n, 'up').fill('s4')
        F(n, 'down').fill('s2')
        small('cuff_' + cn, 's', 1, bolt=False)
        n = 'pad_' + cn                                 # rubber grip pads: a lit rim, tread grooves on the sole
        for f in S.faces(n):
            f.fill('r1')
        for f in S.faces(n, SIDES):
            f.row(0, 'r2')
        F(n, 'up').fill('r2')
        f = F(n, 'down')
        for c, r in f.each():
            f.put(c, r, 'r0' if r % 2 == 1 else 'r1')
        # the sole face (the pad's far end along its leg, which meets the ground and the wall): grip lugs
        f = F(n, 'north')
        for c, r in f.each():
            f.put(c, r, 'r0' if r in (1, 3) else 'r2')   # two lug grooves across the sole


def paint_all():
    paint_body()
    paint_cage()
    paint_front()
    paint_reel()
    paint_legs()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview', help='also write DIR/skitter_preview.png with the stand-ins filled')
    ap.add_argument('--out', default=OUT)
    a = ap.parse_args()
    paint_all()
    miss = S.missing()
    if miss:
        sys.stderr.write('unpainted faces: %s\n' % miss)
        return 1
    os.makedirs(a.out, exist_ok=True)
    S.img.save(os.path.join(a.out, 'skitter.png'))
    S.glow.save(os.path.join(a.out, 'skitter_glowmask.png'))
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
        pv.save(os.path.join(a.preview, 'skitter_preview.png'))
    print('wrote skitter.png and skitter_glowmask.png to %s' % a.out)
    return 0


if __name__ == '__main__':
    sys.exit(main())

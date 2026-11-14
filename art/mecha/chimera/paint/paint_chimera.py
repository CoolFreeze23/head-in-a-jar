"""The Chimera ("the lab result that was issued a hard hat"): the painted sheet, every pixel placed from palette keys.

Writes three files beside the other frames' sheets, all adopted as `painted` in the texture manifest:
- `textures/entity/mecha/chimera.png`: the sheet;
- `chimera_glowmask.png`: the lamps (helm lamp, eye, tail lamps) and the gill lights (the serum faces);
- `chimera_veins.png`: the Bio-Gel veins alone, in a tintable neutral, everything else clear. It is the game's
  vein overlay: drawn emissive and tinted with the grafted creature's colour for sixty seconds after a Graft.
No randomness. Same rules as the other frames.

    python -I art/mecha/chimera/paint/paint_chimera.py                # writes the three PNGs into the mod assets
    python -I art/mecha/chimera/paint/paint_chimera.py --preview DIR  # also DIR/chimera_preview.png

The value plan (decided at about 110 px before detail):
- LIGHT: the growth, the one material outside the house metals. Dry and soft, never wet: a matte peach-to-cream mass,
  bread dough or a mushroom, not flesh. The core mass, stalk and grown limbs cream (bone 2, `d8d4bb`), the lobes and
  bulges peach (flesh 4, `eba688`), upper surfaces bone 3 (`fcf9e8`), folds where lobes meet and where it presses through the bars bone 1 (`b1ad92`, a warm grey, not
  red), the deepest creases bone 0. No glints, no white, no red, no magenta: the flesh ramp's darker steps are not
  used at all.
- ACCENT: the teal veins (teal glass 2 with a teal 3 lit line), the single saturated note; the hard hat in hi-vis.
- DARK: the cage and running gear, darker than the growth so the growth is the light mass: tray rubber, posts and
  brackets dark steel, the cage ring enamel at its heavy step (e1), limbs steel.
"""
import argparse
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
sys.path.insert(0, os.path.join(ROOT, 'art', 'mecha', 'hopper', 'paint'))
from paintlib import COL, SIDES, Sheet, rgba  # noqa: E402

BBMODEL = os.path.join(ROOT, 'art', 'mecha', 'chimera', 'chimera.bbmodel')
PLACEHOLDER = os.path.join(ROOT, 'art', 'mecha', 'chimera', 'chimera_placeholder.png')
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'headinajar', 'textures', 'entity', 'mecha')

S = Sheet(BBMODEL)
F = S.face
VEINS = Image.new('RGBA', (S.W, S.H), (0, 0, 0, 0))
VX = VEINS.load()
VEIN_CUBES = ('vein_f1', 'vein_f3', 'vein_rim', 'vein_belly', 'vein_shoulder', 'stalk_vein', 'thigh_vein_l',
              'thigh_vein_r', 'vein_s_l', 'vein_s_r')
GROWTH_CUBES = ('mass', 'bulge_f', 'bulge_b', 'lobe_rim', 'lobe_rim_top', 'lobe_belly', 'lobe_shoulder', 'bulge_s_l',
                'bulge_s_r', 'stalk', 'upper_arm_l', 'upper_arm_r', 'thigh_l', 'thigh_r')


def pair(name):
    return (name + '_l', name + '_r')


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


def glass_vial(cube):
    for f in S.faces(cube, SIDES):
        for c, r in f.each():
            if r == 0 or c == 0:
                f.put(c, r, 'g3')
            elif r == f.H - 1 or c == f.W - 1:
                f.put(c, r, 'g2')
            else:
                S.px[f.x0 + c, f.y0 + r] = (0, 0, 0, 0)
    for side in ('up', 'down'):
        F(cube, side).fill('g2')


# ===================================================================================================== the growth
def dough(cube):
    """The growth: a soft matte mass by world height over the growth's own extent (y 15.5 to 36): cream (o3) on top
    faces and the upper rows, peach (f4) through the middle, a bone-1 fold along the foot of each face (where the
    lobe tucks under or meets the bars), bone 0 only on undersides. Every face is calm: no glints, no stipple."""
    for f in S.faces(cube):
        top = S.cubes[cube]['frm'][1] + S.cubes[cube]['h']
        for c, r in f.each():
            x, y, z = f.world(c, r)
            if f.side == 'up':
                k = 'o3'
            elif f.side == 'down':
                k = 'o0'
            elif r == f.H - 1 and f.H >= 3:
                k = 'o1'                                # the fold where it tucks under
            elif r == 0 and f.H >= 3:
                k = 'o3'                                # the rounded top catching the light
            else:
                k = 'f4' if cube.startswith(('lobe', 'bulge')) else 'o2'   # peach on the lobes, cream elsewhere
            f.put(c, r, k)


def paint_growth():
    for n in GROWTH_CUBES:
        dough(n)
    # where the mass presses out between the cage's posts and under the ring: deeper folds along the bars
    for side in ('east', 'west', 'north', 'south'):
        f = F('mass', side)
        for c, r in f.each():
            x, y, z = f.world(c, r)
            if y > 27.5:
                f.put(c, r, 'o1')                       # pressed up under the cage ring
    # lobe tops that the light finds, and a fold where the shoulder lobe meets the body
    f = F('lobe_shoulder', 'east')
    f.fill('o1')
    f = F('lobe_rim', 'south')
    f.col(0, 'o1')
    # the thighs and upper arms grow out of the metal: their foot rows fold dark where they enter it
    for n in pair('thigh') + pair('upper_arm'):
        f = F(n, 'north')
        f.row(0, 'o1')
    # veins: teal glass 2 field with a teal 3 lit top row; teal 1 at the foot. Their islands are their own cubes, so
    # the vein overlay is exactly these pixels
    for n in VEIN_CUBES:
        for f in S.faces(n):
            for c, r in f.each():
                k = 't2'
                if r == 0 or f.side == 'up':
                    k = 't3'
                elif r == f.H - 1 or f.side == 'down':
                    k = 't1'
                f.put(c, r, k)
                # the overlay: the same pixels in a light neutral the game tints (steel 5, its foot steel 4)
                nk = 's4' if k == 't1' else ('e5' if k == 't3' else 's5')
                VX[f.x0 + c, f.y0 + r] = rgba(nk)


# ===================================================================================================== cage and metal
def paint_cage():
    for f in S.faces('tray', SIDES):
        f.fill('r1')
        f.row(0, 'r2')
    F('tray', 'up').fill('r2')
    F('tray', 'down').fill('r0')
    for n in ('ring_f', 'ring_b', 'ring_l', 'ring_r'):
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, 'e2' if r == 0 else 'e1')
        f = F(n, 'up')
        for c, r in f.each():
            x, y, z = f.world(c, r)
            f.put(c, r, 'e2' if max(abs(x), abs(z)) > 11 else 'e1')
        F(n, 'down').fill('e0')
    lamp('lens_core', 'lamp_core_lit', 'G', 'north')
    for s in ('l', 'r'):
        for b in ('post_f', 'post_b'):
            n = '%s_%s' % (b, s)
            for f in S.faces(n, SIDES):
                f.fill('s1')
                f.row(0, 's2')
            F(n, 'up').fill('s2')
            F(n, 'down').fill('s0')
        n = 'gill_cover_' + s
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, 's3' if r == 0 else ('s1' if r == f.H - 1 else 's2'))
        f = F(n, 'west' if s == 'l' else 'east')
        for r in (1, 3):
            f.row(r, 's0', 1, f.W - 1)                  # gill slots
        F(n, 'up').fill('s3')
        F(n, 'down').fill('s0')
        lamp('tail_lens_' + s, 'tail_lit_' + s, 'A', 'south')
        for c, r in F('tail_lit_' + s, 'up').each():     # lit on top too: it is seen from above
            F('tail_lit_' + s, 'up').put(c, r, 'A2', lit=True)
    # the gills: a dark backing, black socket rings, clear glass vials, brass caps; the serum a neutral tint base
    for f in S.faces('gill_backing', SIDES):
        f.fill('s0')
        f.row(0, 's1')
    F('gill_backing', 'up').fill('s1')
    F('gill_backing', 'down').fill('s0')
    for i in ('0', '1', '2'):
        small('socket_ring_' + i, 'r', 0, bolt=False)
        glass_vial('vial_' + i)
        n = 'vial_cap_' + i
        for f in S.faces(n, SIDES):
            f.fill('b3')
        F(n, 'up').rows(['b4 b4 b4 b3', 'b4 b5 b3 b2', 'b3 b3 b2 b2'])
        F(n, 'down').fill('b1')
        n = 'serum_' + i
        for f in S.faces(n):
            for c, r in f.each():
                f.put(c, r, 'e5' if r == 0 else ('s4' if r >= f.H - 2 else 's5'), lit=(f.side == 'south'))
    # seat and collar
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


# ===================================================================================================== hat, eye
def paint_hat_eye():
    small('hat_strut', 's', 1)
    for n in ('hat_shell', 'hat_dome'):
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, 'w2' if r == 0 else 'w1')
        f = F(n, 'up')
        x0, _, z0 = S.cubes[n]['frm']
        for c, r in f.each():
            x, y, z = f.world(c, r)
            f.put(c, r, 'w3' if (z < z0 + 1 and x < x0 + 3) else 'w2')   # one lit corner, a saturated field
        F(n, 'down').fill('w0')
    # the company's mark on the dome's front: a white roundel with a dark dot (a sticker; no lettering fits)
    F('hat_dome', 'north').rows(['- - - - e5 e5 - - - -', '- - - - e5 r1 - - - -'])
    for n in ('hat_brim', 'hat_peak'):
        for f in S.faces(n, SIDES):
            f.fill('w1')
        F(n, 'up').fill('w2')
        F(n, 'down').fill('w0')                         # the brim's shadowed underside
    small('hat_ridge', 'w', 2, bolt=False)
    F('hat_ridge', 'up').fill('w3')                     # the ridge carries the only light run
    fill('hat_band', 'r1')
    for f in S.faces('hat_band', SIDES):
        f.fill('r1')
    for n in pair('clamp_arm'):
        small(n, 's', 1, bolt=False)
    for n in pair('pad'):
        fill(n, 'r2')
        F(n, 'up').fill('r3')
        F(n, 'down').fill('r0')
    # the eye: a pale ball (e4, e5 top, e2 underside) with a dark ring and a dark-pupilled lens
    for f in S.faces('eye'):
        for c, r in f.each():
            f.put(c, r, 'e4' if r == 0 else ('e2' if r == f.H - 1 else 'e3'))
    F('eye', 'up').fill('e4')
    F('eye', 'down').fill('e1')
    small('eye_rim_t', 's', 1, bolt=False)
    small('eye_rim_b', 's', 1, bolt=False)
    for f in S.faces('eye_lens'):
        f.fill('r0')
    F('eye_lens', 'north').rows(['P1 P1 P1 P1', 'P1 r0 r0 P0', 'P1 r0 r0 P0', 'P0 P0 P0 P0'])
    for f in S.faces('eye_lens_lit'):
        f.fill('r0')
    f = F('eye_lens_lit', 'north')
    for c, r in f.each():
        if 1 <= c <= 2 and 1 <= r <= 2:
            f.put(c, r, 'r0')                           # the pupil stays dark, even lit
        else:
            f.put(c, r, 'P3' if (c, r) == (0, 0) else 'P2', lit=True)


# ===================================================================================================== limbs
def paint_limbs():
    for s in ('l', 'r'):
        for b in ('shoulder', 'forearm'):
            n = '%s_%s' % (b, s)
            for f in S.faces(n, SIDES):
                f.fill('s2')
                f.row(0, 's3')
                f.row(f.H - 1, 's1')
            F(n, 'up').fill('s3')
            F(n, 'down').fill('s1')
        small('arm_band_' + s, 's', 3, bolt=False)
        n = 'cuff_' + s
        fill(n, 'r1')
        for f in S.faces(n, SIDES):
            f.row(0, 'r2')
        F(n, 'down').fill('r0')
        small('hip_bracket_' + s, 's', 1)
        small('thigh_brace_' + s, 's', 3, bolt=False)
        small('knee_axle_' + s, 's', 0, bolt=False)
        n = 'knee_' + s
        for f in S.faces(n, SIDES):
            brass_bar(f)
        F(n, 'up').fill('b4')
        F(n, 'down').fill('b1')
        n = 'shin_' + s
        for f in S.faces(n, SIDES):
            f.fill('s2')
            f.row(0, 's3')
            if f.side in ('north', 'west'):
                f.col(0, 's3')
            f.row(f.H - 1, 's1')
        F(n, 'up').fill('s3')
        F(n, 'down').fill('s0')
        small('ankle_' + s, 's', 1, bolt=False)
        n = 'sole_' + s
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, 'r2' if r == 0 else 'r0')
        F(n, 'up').fill('r1')
        F(n, 'down').fill('r0')
        small('heel_' + s, 's', 1, bolt=False)


def paint_all():
    paint_growth()
    paint_cage()
    paint_hat_eye()
    paint_limbs()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview', help='also write DIR/chimera_preview.png with the stand-ins filled')
    ap.add_argument('--out', default=OUT)
    a = ap.parse_args()
    paint_all()
    miss = [m for m in S.missing() if not m[0].startswith('vial_')]
    if miss:
        sys.stderr.write('unpainted faces: %s\n' % miss)
        return 1
    os.makedirs(a.out, exist_ok=True)
    S.img.save(os.path.join(a.out, 'chimera.png'))
    S.glow.save(os.path.join(a.out, 'chimera_glowmask.png'))
    VEINS.save(os.path.join(a.out, 'chimera_veins.png'))
    if a.preview:
        ph = Image.open(PLACEHOLDER).convert('RGBA').load()
        pv = S.img.copy()
        p = pv.load()
        for n in S.cubes:
            if n.startswith('standin'):
                for f in S.faces(n):
                    for c, r in f.each():
                        p[f.x0 + c, f.y0 + r] = ph[f.x0 + c, f.y0 + r]
        pv.save(os.path.join(a.preview, 'chimera_preview.png'))
    print('wrote chimera.png, chimera_glowmask.png and chimera_veins.png to %s' % a.out)
    return 0


if __name__ == '__main__':
    sys.exit(main())

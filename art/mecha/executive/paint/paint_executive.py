"""The Executive (the board's company car): the painted sheet, every pixel placed from palette keys.

Source of `textures/entity/mecha/executive.png` and `executive_glowmask.png` (adopted as `painted` in the texture
manifest). No randomness. Same rules as the Hopper's final pass, the Bulwark, the Wheelhouse and the Colossus.

    python -I art/mecha/executive/paint/paint_executive.py                # writes the two PNGs into the assets
    python -I art/mecha/executive/paint/paint_executive.py --preview DIR  # also DIR/executive_preview.png
Reads `art/mecha/executive/executive.bbmodel` (sizes, positions, box-UV offsets; read only), `art/palette.json` and the
shared helpers in `art/mecha/hopper/paint/paintlib.py`.

The value plan (decided at about 110 px before detail): a DARK FIGURE UNDER A PALE ROOF (the Colossus is the
reverse: pale masonry on steel legs).
- The suit is cloth, not plate: charcoal from the rubber ramp, which is matte and warm (field r2, seams and shadow r1,
  lit edges r3), never the steel ramp. Tailoring is carried by shapes that hold at range: the white shirt V (e4)
  between lapels faced lighter (lapel field r3 with a pale e1 edge along the V), a brass tie, a pressed crease (r3)
  down the front and back of each trouser leg. No pinstripe: a 1 px stripe on r2 does not hold at 20 blocks and reads
  as plate seams at 6.
- The canopy is the grandest thing and the calmest: roof enamel e2 with e3 lips, brass only on the valance and the
  column caps and crest, the ceiling the darkest step (r0) so the jar glows under it.
- Small bright notes: white gloves and spats (e3-e4), shirt cuffs, polished black shoes with one r3 glint, the tie.
- Vials: clear cutout glass (a 1 px frame in vanilla glass's two lightest steps, the panes alpha 0) with brass caps.
  The serum cubes (`gene_port_N`) are a neutral light base the game tints: s5 body, an e5 meniscus row, s4 at the
  foot.
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
sys.path.insert(0, os.path.join(ROOT, 'art', 'mecha', 'hopper', 'paint'))
from paintlib import COL, SIDES, Sheet  # noqa: E402

BBMODEL = os.path.join(ROOT, 'art', 'mecha', 'executive', 'executive.bbmodel')
PLACEHOLDER = os.path.join(ROOT, 'art', 'mecha', 'executive', 'executive_placeholder.png')
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'headinajar', 'textures', 'entity', 'mecha')

S = Sheet(BBMODEL)
F = S.face


def pair(name):
    return (name + '_l', name + '_r')


def sgn(name):
    return -1 if name.endswith('_l') else 1


def outer(name):
    return 'west' if sgn(name) < 0 else 'east'


def inner(name):
    return 'east' if sgn(name) < 0 else 'west'


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


def cloth(cube, field='r2', lit='r3', dark='r1', crease=False):
    """Suit cloth: one matte field, the top row lit (the shoulder of the fold), the last row the shadow step; with
    `crease`, a pressed r3 line down the middle of the front and back faces."""
    for f in S.faces(cube, SIDES):
        for c, r in f.each():
            k = field
            if r == 0:
                k = lit
            elif r == f.H - 1:
                k = dark
            if crease and f.side in ('north', 'south') and c == f.W // 2 and 0 < r < f.H - 1:
                k = lit
            f.put(c, r, k)
    F(cube, 'up').fill(lit)
    F(cube, 'down').fill(dark)


def white(cube, field='e3', lit='e4', dark='e1'):
    """Gloves, spats, shirt cuffs: the bright notes. e3 field, e4 top row and top face, e1 underside row."""
    for f in S.faces(cube, SIDES):
        for c, r in f.each():
            f.put(c, r, lit if r == 0 else (dark if r == f.H - 1 and f.H >= 3 else field))
    F(cube, 'up').fill(lit)
    F(cube, 'down').fill(dark)


def glass_vial(cube):
    """Clear cutout glass: a 1 px frame in vanilla glass's two lightest steps (g3 on the top and left, g2 on the
    bottom and right), the panes clear (alpha 0) so the serum shows; one g3 shine pixel in the pane's upper left."""
    for f in S.faces(cube, SIDES):
        for c, r in f.each():
            if r == 0 or c == 0:
                k = 'g3'
            elif r == f.H - 1 or c == f.W - 1:
                k = 'g2'
            else:
                k = None
            if k:
                f.put(c, r, k)
            else:
                x, y = f.x0 + c, f.y0 + r
                S.px[x, y] = (0, 0, 0, 0)
        if f.W >= 3 and f.H >= 4:
            f.put(1, 1, 'g3')
    for side in ('up', 'down'):
        F(cube, side).fill('g2')


def lamp_box(lens, cap, m):
    """A corner lantern: the lens box dark (off) in the ramp's two darkest steps on its sides, the lit cap's four
    sides and top glowing (step 2, the top step 3)."""
    for f in S.faces(lens):
        f.fill(m + '1' if f.side != 'down' else 'r0')
    for f in S.faces(cap):
        if f.side == 'down':
            f.fill('r0')
        else:
            for c, r in f.each():
                f.put(c, r, m + ('3' if f.side == 'up' else '2'), lit=True)


# ===================================================================================================== the suit
def paint_suit():
    for n in ('chest', 'chest_low', 'girder'):
        cloth(n)
    # the jacket's back: a centre vent seam (r1) on the chest's back face
    f = F('chest', 'south')
    f.col(f.W // 2, 'r1', 1, f.H)
    fill('belt', 'r0')
    for f in S.faces('belt', SIDES):
        f.row(0, 'r1')
    small('buckle', 'b', 3, bolt=False)
    F('buckle', 'north').put(0, 0, 'b5')
    # the shirt V and the tie
    white('shirt', field='e4', lit='e5', dark='e3')
    for n in ('lapel_hi_l', 'lapel_lo_l', 'lapel_hi_r', 'lapel_lo_r'):
        fill(n, 'r3')
        f = F(n, 'north')
        edge = f.W - 1 if n.endswith('_l') else 0       # the edge along the V
        f.col(edge, 'e1')
        f.row(0, 'r3')
        F(n, inner(n)).fill('e1')
    f = F('tie', 'north')
    for c, r in f.each():
        f.put(c, r, 'b4' if c == 0 else ('b2' if c == f.W - 1 else 'b3'))
    f.put(1, f.H - 1, 'b2')
    for side in ('east', 'west', 'south', 'up', 'down'):
        F('tie', side).fill('b2')
    small('tie_knot', 'b', 3, bolt=False)
    F('tie_knot', 'north').put(0, 0, 'b5')
    for f in S.faces('tie_pin'):
        f.fill('P0')
    F('tie_pin', 'north').rows(['P1 P0'])
    for f in S.faces('tie_pin_lit'):
        f.fill('r0')
    F('tie_pin_lit', 'north').put(0, 0, 'P3', lit=True)
    F('tie_pin_lit', 'north').put(1, 0, 'P2', lit=True)
    # back grille and vent flaps (plain warning yellow with louvre slots)
    fill('grille_b', 'r0')
    f = F('grille_b', 'south')
    for c, r in f.each():
        f.put(c, r, 'r2' if r == 1 else 'r0')
    for n in pair('vent_flap'):
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, 'w3' if r == 0 else ('w1' if r == f.H - 1 else 'w2'))
        F(n, 'south').row(2, 'r1', 1, F(n, 'south').W - 1)
        F(n, 'up').fill('w3')
        F(n, 'down').fill('w0')
    # shoulders: the yoke and the padded shoulders are suit; the brass racks on top
    for s in ('l', 'r'):
        cloth('yoke_' + s)
        cloth('shoulder_pad_' + s)
        n = 'rack_' + s
        for f in S.faces(n, SIDES):
            brass_bar(f)
        brass_top(F(n, 'up'))
        F(n, 'down').fill('b1')
    # hips: dark drums with small brass hub caps
    for s in ('l', 'r'):
        n = 'hip_drum_' + s
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, 's2' if r == 0 else ('s0' if r == f.H - 1 else 's1'))
        F(n, 'up').fill('s1')
        F(n, 'down').fill('s0')
        n = 'hip_cap_' + s
        fill(n, 'b2')
        F(n, outer(n)).rows(['b4 b4 b3 b3', 'b4 b5 b3 b2', 'b3 b3 b3 b2', 'b3 b2 b2 b1'])


# ===================================================================================================== canopy, jar
def paint_canopy():
    for n in ('roof_f', 'roof_b', 'roof_l', 'roof_r'):
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, 'e3' if r == 0 else 'e2')
        f = F(n, 'up')
        for c, r in f.each():
            x, y, z = f.world(c, r)
            f.put(c, r, 'e3' if (abs(x) > 15 or abs(z) > 14) else 'e2')
        F(n, 'down').fill('r0')
    # the sunroof: a hatch in the roof, its seam dark and its lit edge along the front and left
    f = F('sunroof_panel', 'up')
    for c, r in f.each():
        x, y, z = f.world(c, r)
        k = 'e2'
        if abs(x) > 8 or abs(z) > 8:
            k = 'r1'
        elif x < -7 or z < -7:
            k = 'e3'
        f.put(c, r, k)
    for f in S.faces('sunroof_panel', SIDES):
        f.fill('e1')
    F('sunroof_panel', 'down').fill('r0')
    fill('ceiling', 'r0')                               # the darkest field, behind and above the jar's head
    for n in ('valance_f', 'valance_b', 'valance_l', 'valance_r'):
        for f in S.faces(n, SIDES):
            brass_bar(f)
        F(n, 'up').fill('b3')
        F(n, 'down').fill('b1')
    # the crest: a brass plate with a lit top, two rivets, the company's engraved line
    F('crest', 'north').rows(['b4 b4 b4 b4 b4 b4 b4 b4 b4 b4 b4 b3',
                              'b3 b5 b3 b1 b1 b1 b1 b1 b1 b3 b5 b2',
                              'b2 b2 b2 b2 b2 b2 b2 b2 b2 b2 b2 b1'])
    for side in ('east', 'west', 'south', 'down'):
        F('crest', side).fill('b2')
    brass_top(F('crest', 'up'))
    # the columns: brass at the rule's field, inner faces a step down, a lit cap row at the top, the foot dark
    for s in ('l', 'r'):
        for b in ('column_f', 'column_b'):
            n = '%s_%s' % (b, s)
            for f in S.faces(n, SIDES):
                f.fill('b2' if f.side == inner(n) else 'b3')
                f.row(0, 'b4')
                f.row(f.H - 1, 'b1')
            F(n, 'up').fill('b4')
            F(n, 'down').fill('b1')
        lamp_box('lamp_f_' + s, 'lamp_lit_f_' + s, 'A')
        lamp_box('lamp_b_' + s, 'lamp_lit_b_' + s, 'A')
    # the jar's seat, collar and clamps (quiet)
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
    for s in ('l', 'r'):
        n = 'clamp_post_' + s
        for f in S.faces(n, SIDES):
            f.fill('b2' if f.side == inner(n) else 'b3')
            f.row(f.H - 1, 'b1')
        F(n, 'up').fill('b3')
        F(n, 'down').fill('b1')
        small('clamp_knuckle_' + s, 's', 1, bolt=False)
        n = 'clamp_pad_' + s
        fill(n, 'r2')
        F(n, 'up').fill('r3')
        F(n, 'down').fill('r0')


# ===================================================================================================== vials
def paint_vials():
    for s in ('l', 'r'):
        for b in ('vial_o', 'vial_i'):
            glass_vial('%s_%s' % (b, s))
        for b in ('vial_cap_o', 'vial_cap_i'):
            n = '%s_%s' % (b, s)
            for f in S.faces(n, SIDES):
                f.fill('b3')
            brass_top(F(n, 'up'))
            F(n, 'up').put(1, 1, 'b5')
            F(n, 'down').fill('b1')
        # the serum: a neutral light base the game tints. Meniscus e5 on top, body s5, the last two rows s4
        for b in ('serum_o', 'serum_i'):
            n = '%s_%s' % (b, s)
            for f in S.faces(n, SIDES):
                for c, r in f.each():
                    f.put(c, r, 'e5' if r == 0 else ('s4' if r >= f.H - 2 else 's5'))
            F(n, 'up').fill('e5')
            F(n, 'down').fill('s4')


# ===================================================================================================== arms, legs
def paint_limbs():
    for s in ('l', 'r'):
        cloth('shoulder_joint_' + s, field='r1', lit='r2', dark='r0')     # the armhole, in shadow
        cloth('upper_arm_' + s)
        cloth('elbow_' + s, field='r1', lit='r2', dark='r0')
        cloth('forearm_' + s)
        white('cuff_' + s, field='e4', lit='e5', dark='e2')
        white('palm_' + s)
        white('fingers_' + s)
        n = 'knuckles_' + s
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, 'e4' if r == 0 else 'e2')   # the glove's knuckle seam
        F(n, 'up').fill('e4')
        F(n, 'down').fill('e1')
        cloth('thigh_' + s, crease=True)
        cloth('knee_' + s, field='r1', lit='r2', dark='r0')
        cloth('shin_' + s, crease=True)
        white('spat_' + s)
        F('spat_' + s, outer('spat_' + s)).put(2, 2, 'e1')   # the spat's button
        F('spat_' + s, outer('spat_' + s)).put(2, 4, 'e1')
        cloth('ankle_' + s, field='r1', lit='r2', dark='r0')
        # shoes: polished black, a lit welt, one glint on the toe
        n = 'shoe_' + s
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, 'r2' if r == 0 else ('r0' if r == f.H - 1 else 'r1'))
        f = F(n, 'north')
        f.put(f.W // 2, 1, 'r3')
        f = F(n, 'up')
        z0 = S.cubes[n]['frm'][2]
        for c, r in f.each():
            x, y, z = f.world(c, r)
            f.put(c, r, 'r3' if z < z0 + 2 and abs(x - (-10 if s == 'l' else 10)) < 1.6 else 'r1')
        F(n, 'down').fill('r0')
    small('watch_l', 'b', 3, bolt=False)
    F('watch_l', 'west').rows(['b3 s4 s5 b3', 'b3 s4 s4 b2'])   # the watch face


def paint_all():
    paint_suit()
    paint_canopy()
    paint_vials()
    paint_limbs()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview', help='also write DIR/executive_preview.png with the stand-ins filled')
    ap.add_argument('--out', default=OUT)
    a = ap.parse_args()
    paint_all()
    miss = [m for m in S.missing() if not m[0].startswith(('vial_o', 'vial_i'))]
    if miss:
        sys.stderr.write('unpainted faces: %s\n' % miss)
        return 1
    os.makedirs(a.out, exist_ok=True)
    S.img.save(os.path.join(a.out, 'executive.png'))
    S.glow.save(os.path.join(a.out, 'executive_glowmask.png'))
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
        pv.save(os.path.join(a.preview, 'executive_preview.png'))
    print('wrote executive.png and executive_glowmask.png to %s' % a.out)
    return 0


if __name__ == '__main__':
    sys.exit(main())

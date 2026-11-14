"""The Rover (a mail-room tea trolley the firm calls a vehicle): the painted sheet, every pixel placed from palette keys.

Source of `textures/entity/shell/rover.png` and `rover_glowmask.png` (adopted as `painted` in the texture manifest).
No randomness. The house rules, cut down for cheaper goods: cream enamel and bare tin, one brass part (the collar,
plus a buckle and a bell), a little wear placed on purpose, an asset tag.

    python -I art/shell/rover/paint/paint_rover.py                # writes the two PNGs into the mod assets
    python -I art/shell/rover/paint/paint_rover.py --preview DIR  # also DIR/rover_preview.png

Value plan: a pale cream shelf and drawer front (e3, e4 lip) on bare tin (steel s3/s4 for the tray, lip, posts and
handle), dark castors (rubber) on dark rails; the teal pennant the one accent; the strap quiet dark webbing (bone 0 field, rubber 2
stitched edge, bone 1 top) so it frames the jar without competing.
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
sys.path.insert(0, os.path.join(ROOT, 'art', 'mecha', 'hopper', 'paint'))
from paintlib import COL, SIDES, Sheet  # noqa: E402

BBMODEL = os.path.join(ROOT, 'art', 'shell', 'rover', 'rover.bbmodel')
PLACEHOLDER = os.path.join(ROOT, 'art', 'shell', 'rover', 'rover_placeholder.png')
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'headinajar', 'textures', 'entity', 'shell')

S = Sheet(BBMODEL)
F = S.face
CORNERS = ('fl', 'fr', 'rl', 'rr')


def fill(cube, key, sides=SIDES + ('up', 'down')):
    for f in S.faces(cube, sides):
        f.fill(key)


def small(cube, m, field, sides=SIDES):
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


def paint_all():
    # ---- castors: rubber tyres (which never spin) and a steel hub with one bright bolt (which does), on dark forks
    for cn in CORNERS:
        for n in ('tyre_a_' + cn, 'tyre_b_' + cn):
            for f in S.faces(n):
                f.fill('r1')
            for f in S.faces(n, SIDES):
                f.row(0, 'r2')
            F(n, 'down').fill('r0')
        small('hub_' + cn, 's', 3)
        fill('bolt_' + cn, 's5')                        # the marked bolt: its turning shows the spin
        small('fork_' + cn, 's', 2)
        small('stem_' + cn, 's', 1)
    for n in ('rail_l', 'rail_r'):
        for f in S.faces(n, SIDES):
            f.fill('s0')
            f.row(0, 's1')
        F(n, 'up').fill('s1')
        F(n, 'down').fill('s0')
    # ---- the shelf: cream enamel box, its tin lip; chips where the bumper and the corners take knocks
    for f in S.faces('shelf', SIDES):
        for c, r in f.each():
            f.put(c, r, 'e4' if r == 0 else ('e1' if r == f.H - 1 else 'e3'))
        f.put(0, f.H - 1, 's2')                         # a chip at each lower corner: tin showing through
        f.put(f.W - 1, f.H - 1, 's2')
    F('shelf', 'up').fill('e3')
    F('shelf', 'down').fill('e1')
    for f in S.faces('shelf_lip', SIDES):
        f.fill('s4')
        f.put(0, 0, 's2')                               # the lip's corners dinged
        f.put(f.W - 1, 0, 's2')
    f = F('shelf_lip', 'up')
    x0, _, z0 = S.cubes['shelf_lip']['frm']
    for c, r in f.each():
        x, y, z = f.world(c, r)
        f.put(c, r, 's4' if (z < z0 + 1 or x < x0 + 1) else 's3')
    F('shelf_lip', 'down').fill('s2')
    # ---- the rubber bumper, scuffed at its ends
    for f in S.faces('bumper', SIDES):
        f.fill('r1')
        f.row(0, 'r2')
    f = F('bumper', 'north')
    f.put(0, 1, 'r3')
    f.put(f.W - 1, 1, 'r3')
    F('bumper', 'up').fill('r2')
    F('bumper', 'down').fill('r0')
    # ---- the drawer: cream front with a lit top row, a tin knob; its box tin
    for f in S.faces('drawer_front', SIDES):
        for c, r in f.each():
            f.put(c, r, 'e4' if r == 0 else 'e2')
    F('drawer_front', 'up').fill('e3')
    F('drawer_front', 'down').fill('e1')
    small('drawer_box', 's', 2)
    small('knob', 's', 4)
    for s in ('l', 'r'):
        lamp('reflector_lens_' + s, 'reflector_lit_' + s, 'R', 'south')
    # ---- the tray: pressed tin with a lit rim; the seat; the brass collar (the one brass part)
    f = F('tray', 'up')
    x0, _, z0 = S.cubes['tray']['frm']
    x1, z1 = x0 + S.cubes['tray']['w'], z0 + S.cubes['tray']['d']
    for c, r in f.each():
        x, y, z = f.world(c, r)
        k = 's3'
        if z < z0 + 1 or x < x0 + 1:
            k = 's4'
        elif z > z1 - 1 or x > x1 - 1:
            k = 's2'
        f.put(c, r, k)
    for f in S.faces('tray', SIDES):
        f.fill('s3')
    F('tray', 'down').fill('s1')
    f = F('seat', 'up')
    for c, r in f.each():
        f.put(c, r, 's2')
    for f in S.faces('seat', SIDES):
        f.fill('s2')
    F('seat', 'down').fill('s0')
    for n in ('collar_f', 'collar_b', 'collar_l', 'collar_r'):
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, 'b4' if r == 0 else 'b2')
        F(n, 'up').fill('b3')
        F(n, 'down').fill('b1')
    for n in ('post_f_l', 'post_f_r', 'post_b_l', 'post_b_r'):
        small(n, 's', 3)
    # ---- the luggage strap: quiet webbing (bone 1 with a bone 0 stitched edge), a brass buckle
    for n in ('strap_l', 'strap_r', 'strap_top'):
        for f in S.faces(n):
            f.fill('o0')
        for f in S.faces(n, ('north', 'south') if n != 'strap_top' else ('north', 'south')):
            for c, r in f.each():
                if f.W >= 2 and c == f.W - 1:
                    f.put(c, r, 'r2')
        F(n, 'up').fill('o1')
    for f in S.faces('buckle'):
        f.fill('b3')
    F('buckle', 'east').rows(['b4 b4 b3', 'b4 r0 b2', 'b3 b2 b2'])
    # ---- the push handle (tin), the asset tag on the drawer's side (a white card with a black bar code), the bell
    small('handle_bar', 's', 3)
    small('handle_post_l', 's', 3)
    small('handle_post_r', 's', 3)
    fill('plate', 'e5')
    F('plate', 'south').rows(['r1 e5 r1 r1 e5 r1'])
    for f in S.faces('bell'):
        f.fill('b3')
    F('bell', 'up').rows(['b4 b4 b3', 'b4 b5 b3', 'b3 b3 b2'])
    F('bell', 'down').fill('b1')
    # ---- the bicycle lamp: a cream housing, an amber lens
    for f in S.faces('lamp_housing'):
        f.fill('e3')
    F('lamp_housing', 'up').fill('e4')
    F('lamp_housing', 'down').fill('e1')
    lamp('lamp_lens', 'lamp_lit', 'A', 'north')
    # ---- the aerial and the pennant in the company's colour (teal, as the Kite's livery): the one accent
    small('aerial_base', 's', 1)
    fill('aerial', 's3')
    F('aerial', 'up').fill('s5')
    for f in S.faces('pennant'):
        for c, r in f.each():
            f.put(c, r, 't3' if r == 0 else ('t1' if r == f.H - 1 else 't2'))
    for side in ('north', 'south'):
        f = F('pennant', side)
        tip = 0 if side == 'north' else f.W - 1          # the outer end (toward -x) frays a step darker
        f.col(tip, 't1')


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
    S.img.save(os.path.join(a.out, 'rover.png'))
    S.glow.save(os.path.join(a.out, 'rover_glowmask.png'))
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
        pv.save(os.path.join(a.preview, 'rover_preview.png'))
    print('wrote rover.png and rover_glowmask.png to %s' % a.out)
    return 0


if __name__ == '__main__':
    sys.exit(main())

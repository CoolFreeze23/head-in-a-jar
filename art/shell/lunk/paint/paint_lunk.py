"""The Lunk (a filing cabinet with gorilla arms): the painted sheet, every pixel placed from palette keys.

Source of `textures/entity/shell/lunk.png` and `lunk_glowmask.png` (adopted as `painted` in the texture manifest).
No randomness. The house rules, cut down for cheaper goods: office putty sheet steel (the enamel ramp at its plain
step, e2, no belt seam, no polish), three drawer shadows and three small brass label holders as the whole front read,
scuffs low on the corners, an asset tag on the back.

    python -I art/shell/lunk/paint/paint_lunk.py                # writes the two PNGs into the mod assets
    python -I art/shell/lunk/paint/paint_lunk.py --preview DIR  # also DIR/lunk_preview.png

Value plan: a putty cabinet (e2 field, e3 lit edges) whose drawer gaps are black (e0/r0 shadows), on a dark plinth;
the arms, shoulders and gloves the darkest masses (steel 0-1, rubber 0-1); the bulldog clips black with bright
sprung-steel levers (s5/s4); the webcam's lens the one light.
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
sys.path.insert(0, os.path.join(ROOT, 'art', 'mecha', 'hopper', 'paint'))
from paintlib import COL, SIDES, Sheet  # noqa: E402

BBMODEL = os.path.join(ROOT, 'art', 'shell', 'lunk', 'lunk.bbmodel')
PLACEHOLDER = os.path.join(ROOT, 'art', 'shell', 'lunk', 'lunk_placeholder.png')
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'headinajar', 'textures', 'entity', 'shell')

S = Sheet(BBMODEL)
F = S.face


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


def dark_limb(cube, field='s1', lit='s2', dark='s0'):
    for f in S.faces(cube, SIDES):
        f.fill(field)
        f.row(0, lit)
        f.row(f.H - 1, dark)
    F(cube, 'up').fill(lit)
    F(cube, 'down').fill(dark)


def paint_all():
    # ---- the cabinet: putty sheet steel. Its front (behind the drawers) black, so the gaps read as shadows; sides
    # plain e2 with a lit top row; scuffs low on the front corners (steel showing)
    for f in S.faces('carcass', SIDES):
        for c, r in f.each():
            f.put(c, r, 'e3' if r == 0 else ('e1' if r == f.H - 1 else 'e2'))
        if f.side in ('east', 'west'):
            front = 0 if f.side == 'west' else f.W - 1
            f.put(front, f.H - 2, 's2')
            f.put(front, f.H - 3, 's3')
    F('carcass', 'north').fill('r0')
    # the back: a recessed panel inside the rims (the carcass's own back face), darker, with the cable slot
    f = F('carcass', 'south')
    for c, r in f.each():
        x, y, z = f.world(c, r)
        if -6 < x < 6 and 18 < y < 27:
            f.put(c, r, 'r1' if y > 26 else 'e0')
    F('carcass', 'up').fill('e2')
    F('carcass', 'down').fill('e0')
    for n in ('back_rim_t', 'back_rim_b', 'back_rim_l', 'back_rim_r'):
        for f in S.faces(n):
            f.fill('e2')
        F(n, 'south').fill('e3')
        F(n, 'up').fill('e3')
    for f in S.faces('cable'):
        f.fill('r1')
    F('cable', 'south').col(0, 'r2')
    # the asset tag: a white card, a black bar code, a brass rivet
    F('asset_tag', 'south').rows(['e5 e5 b4', 'r1 e5 r1', 'r1 r1 e5'])
    for side in ('east', 'west', 'north', 'up', 'down'):
        F('asset_tag', side).fill('e4')
    lamp('led', 'led_lit', 'G', 'south')
    # ---- lid and plinth
    for f in S.faces('lid', SIDES):
        f.fill('e3')
    f = F('lid', 'up')
    for c, r in f.each():
        x, y, z = f.world(c, r)
        f.put(c, r, 'e3' if max(abs(x), abs(z)) > 8 else 'e2')
    F('lid', 'down').fill('e1')
    for f in S.faces('plinth', SIDES):
        f.fill('s0')
        f.row(0, 's1')
    F('plinth', 'down').fill('s0')
    F('plinth', 'up').fill('s1')
    # ---- the three drawers: putty fronts (lit top row, dark bottom row), chrome pulls, brass label holders with a
    # white card
    for i in ('1', '2', '3'):
        n = 'drawer_' + i
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, 'e3' if r == 0 else ('e1' if r == f.H - 1 else 'e2'))
        F(n, 'up').fill('e3')
        F(n, 'down').fill('e0')
        small('pull_' + i, 's', 4)
        F('label_' + i, 'north').rows(['b3 e5 e5 e5 b2'])
        for side in ('east', 'west', 'south', 'up', 'down'):
            F('label_' + i, side).fill('b3')
    small('drawer_3_box', 's', 2)
    # ---- the ring under the jar and the bulldog clips: black jaws, bright sprung-steel levers
    for n in ('ring_f', 'ring_b', 'ring_l', 'ring_r'):
        small(n, 's', 1)
    for s in ('l', 'r'):
        for f in S.faces('clip_jaw_' + s):
            f.fill('r1')
        F('clip_jaw_' + s, 'up').fill('r2')
        for f in S.faces('clip_jaw_' + s, SIDES):
            f.row(0, 'r2')
        fill('clip_bite_' + s, 'r0')
        for f in S.faces('clip_lever_' + s):
            f.fill('s4')
        for f in S.faces('clip_lever_' + s, SIDES):
            f.row(0, 's5')
        F('clip_lever_' + s, 'up').fill('s5')
    # ---- the webcam: a black body on a black clip, its lens the one light
    small('cam_clip', 'r', 1)
    for f in S.faces('cam'):
        f.fill('r1')
    F('cam', 'up').fill('r2')
    lamp('cam_lens', 'cam_lens_lit', 'P', 'north')
    # ---- arms and gloves: the darkest masses
    for s in ('l', 'r'):
        dark_limb('shoulder_block_' + s)
        dark_limb('upper_arm_' + s)
        dark_limb('elbow_' + s, 's0', 's1', 's0')
        dark_limb('forearm_' + s)
        for n in ('fist_' + s, 'knuckle_' + s, 'fingers_' + s):
            for f in S.faces(n):
                f.fill('r1')
            for f in S.faces(n, SIDES):
                f.row(0, 'r2')
            F(n, 'up').fill('r2')
            F(n, 'down').fill('r0')
        f = F('fist_' + s, 'north')
        f.row(2, 'r0')                                  # the glove's seam across the back of the hand
        dark_limb('hip_block_' + s)
        dark_limb('thigh_' + s, 's2', 's3', 's1')
        dark_limb('knee_' + s)
        dark_limb('shin_' + s, 's2', 's3', 's1')
        n = 'foot_' + s
        for f in S.faces(n):
            f.fill('r1')
        for f in S.faces(n, SIDES):
            f.row(0, 'r2')
        F(n, 'down').fill('r0')


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
    S.img.save(os.path.join(a.out, 'lunk.png'))
    S.glow.save(os.path.join(a.out, 'lunk_glowmask.png'))
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
        pv.save(os.path.join(a.preview, 'lunk_preview.png'))
    print('wrote lunk.png and lunk_glowmask.png to %s' % a.out)
    return 0


if __name__ == '__main__':
    sys.exit(main())

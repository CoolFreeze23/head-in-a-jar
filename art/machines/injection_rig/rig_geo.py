"""Injection Rig (the petrol pump with a syringe for a nozzle): geometry.

    python art/machines/injection_rig/rig_geo.py      # writes injection_rig.bbmodel and its placeholder PNG
    python art/machines/injection_rig/rig_anim.py     # then writes the clips into it (and the review copies)

A 1950s petrol pump: a rounded enamel body (12 wide, 8 deep, 16 tall, its vertical corners stepped, the top 2 px stepped
in at the sides) on a dark island base, a dial in a brass bezel on the upper front (a cream face with a dark 6x3
counter window), a dark belt round the waist, the lamp and a small cassette ledge on the lower front, recessed panels
on both sides; on top, a round globe (a disc facing the street) on a dark neck. The "nozzle" is a syringe standing
beside a steel guide and rail at the end of a short steel arm whose hub turns round the globe's neck; its needle is
parked in a brass sheath on the machine's right side (+X, the viewer's left from the front).

Faces -Z. +X is the machine's right. Vertical stack (px): island 0-2; plinth 2-3; body 3-19 (belt 9-10, shoulders
17-19); bearing plate 19-20; hub ring and guide 20-22, rail 22-32 (the arm, 1 px over the body); neck 20-23; globe
23-30. Syringe at rest: needle 9-12 (2 px in the sheath, 8-11), barrel 12-22 beside the guide, finger flange 22-23 on
the guide, plunger thumb 24-25 (empty) or 27-28 (loaded, drawn 3 px). The syringe's hinge (its bone pivot) is on the
rail's face at the drawn thumb's top, (7, 27.5): it slides up and down the rail with the lift and the jab.

The swing (the ten-block motion): `nozzle_arm` turns about the vertical axis through the globe's neck (code yaw, 0 =
the holster on the right, 90 = straight out of the front, 180 = the left side). The syringe's centre is 8.5 px from the
axis, its inner face 7.0: everything static between y 10 and 33 stays within 6.71 px of the axis (the body's stepped
corners, the dial bezel 6.40), so the syringe clears the body at every angle with 0.29 px to spare. That is why the
body is 8 deep, not 10: a 10-deep body's corners stand at 7.1-7.8 px, in the syringe's path.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
for _p in (os.path.join(HERE, '..', 'kit'), 'build/machines-review/scratch-injection_rig/kit'):
    if os.path.exists(os.path.join(_p, 'kit.py')):
        sys.path.insert(0, _p)
        break
import kit  # noqa: E402

PATH = os.path.join(HERE, 'injection_rig.bbmodel')
REVIEW = 'build/machines-review/injection_rig_review.bbmodel'

ARM_PIVOT = [0, 20, 0]       # nozzle_arm: the vertical axis through the neck, at the arm's underside
SYR_C = (8.5, 0.5)           # the syringe's centre (x, z) at rest: the needle at x 8..9, z 0..1
DRAW = 3                     # the plunger drawn (loaded) height, px
LIFT = 4                     # the lift out of the sheath, px
WIND = 1                     # the jab's wind-up above the lift, px


def B(m, name, f, t, mat='enamel', parent='static', part=None, off=()):
    return m.box(name, f, t, mat, parent, part=part, off=off)


def body(m):
    """The pump: a core and a 2 px front skin (with the dial's recess cut in it), stepped corners made by two side
    strips 1 px out with recessed panels (the strips are frames: a post front and back, bars at the bottom, the waist
    and the top). One part, so its own joints are not flagged."""
    P = 'body'
    # core and front skin (A: x -5..5, z -4..4)
    B(m, 'core', [-5, 3, -2], [5, 19, 4], part=P)
    B(m, 'skin_low', [-5, 3, -4], [5, 11, -2], part=P)           # under the dial
    B(m, 'skin_top', [-5, 18, -4], [5, 19, -2], part=P)          # over the dial
    B(m, 'skin_l', [-5, 11, -4], [-4, 18, -2], part=P)           # the dial recess's walls
    B(m, 'skin_r', [4, 11, -4], [5, 18, -2], part=P)
    # the dial's face: a cream field 1 px behind the skin's face, 2 behind the bezel's, with the dark counter window in it
    B(m, 'dial_field', [-4, 11, -3], [4, 18, -2], 'enamel2')
    # side strips (B: x 5..6 and -6..-5, z -3..3) as frames round two recessed panels each (lower y 4-8, upper 11-16),
    # the panel being the core's side face, 1 px back. The strips stop at y 17, so the top 2 px of the
    # body step in 1 px at the sides: a rounded shoulder under the arm
    for s, (x0, x1) in (('r', (5, 6)), ('l', (-6, -5))):
        B(m, 'side_%s_post_f' % s, [x0, 3, -3], [x1, 17, -2], part=P)
        B(m, 'side_%s_post_b' % s, [x0, 3, 2], [x1, 17, 3], part=P)
        B(m, 'side_%s_bar_lo' % s, [x0, 3, -2], [x1, 4, 2], part=P)
        B(m, 'side_%s_bar_mid' % s, [x0, 8, -2], [x1, 11, 2], part=P)
        B(m, 'side_%s_bar_top' % s, [x0, 16, -2], [x1, 17, 2], part=P)
    # the belt: a dark band 1 px proud all round at the waist (y 9-10), carrying the holster on the right
    # (four pieces outside the body, so none shares a face plane with the side strips under it)
    B(m, 'belt_f', [-6, 9, -5], [6, 10, -4], 'dsteel', part='belt')
    B(m, 'belt_b', [-6, 9, 4], [6, 10, 5], 'dsteel', part='belt')
    B(m, 'belt_r', [6, 9, -4], [7, 10, 4], 'dsteel', part='belt')
    B(m, 'belt_l', [-7, 9, -4], [-6, 10, 4], 'dsteel', part='belt')


def front(m, loaded=True):
    # the dial: a brass bezel frame 8x7, 1 px proud, round the recess (opening x -3..3, y 12-17); in the cream face
    # the units counter, a dark 6x3 window of drums standing 1 px off the face, the full width of the opening (v2: the
    # a dark meter; blockstate `counter`: still / rolling)
    kit.rim(m, 'dial_bezel', 'north', -4, -4, 4, 11, 18, 'brass')
    m.group('state_counter', 'static')
    B(m, 'counter', [-3, 13, -4], [3, 16, -3], 'rubber', 'state_counter')
    # lower front: the K3 lamp on the left of the panel (the machine's left, the viewer's right) and the cassette
    # ledge on the right, where the loaded cassette stands face-on (BER item)
    kit.lamp(m, 'north', -4, -4, 4)
    B(m, 'cassette_ledge', [1, 4, -7], [4, 5, -4], 'brass')
    m.anchor('cassette', [2.5, 5, -5.5], 'the serum cassette while loaded: FIXED, upright facing -Z, scale 0.25, '
             'bottom centre on the ledge', standin=((4, 4, 1), 'standi') if loaded else None)


def back(m):
    kit.rim(m, 'back_rim', 'south', 4, -4, 4, 11, 18, 'enamel')
    kit.makers_plate(m, 'south', 4, -2, 5)


def base(m):
    # the pump stands on a dark island 14x2x12, the machine's dark mass at the foot, with the plinth
    # (1 px inside the body all round) on it
    B(m, 'island', [-7, 0, -6], [7, 2, 6], 'dsteel')
    B(m, 'plinth', [-4, 2, -3], [4, 3, 3], 'dsteel')


def holster(m):
    # the needle's sheath: a brass cup 3x3x3 on the belt's right side, its top 1 px under the barrel at rest
    B(m, 'sheath', [7, 8, -1], [10, 11, 2], 'brass')
    m.locator('holster', [8.5, 11, 0.5], None)


def top(m, globe_mat='sample'):
    B(m, 'bearing', [-2, 19, -2], [2, 20, 2], 'brass')
    # the neck: a dark 2x2 post from the bearing plate into the globe (2 px of it bare between the hub and the globe)
    B(m, 'neck', [-1, 20, -1], [1, 23, 1], 'dsteel')
    # the globe: a round disc facing the street, 8 wide, 7 tall, 4 deep, in rows (4, 6, 8x3, 6, 4); painted glass
    # with the serum behind it (T22: liquid behind glass is opaque), its own blockstate part `serum`: 0 the empty
    # milk-glass texture (unlit), 1-7 the lit serum texture tinted by the first gene's category (the Splicer's table)
    m.group('state_serum', 'static')
    g = globe_mat
    for n, y0, y1, hw in (('globe_0', 23, 24, 2), ('globe_1', 24, 25, 3), ('globe_2', 25, 28, 4),
                          ('globe_3', 28, 29, 3), ('globe_4', 29, 30, 2)):
        B(m, n, [-hw, y0, -2], [hw, y1, 2], g, 'state_serum', part='globe')
    m.locator('globe', [0, 26.5, -2.5], None)


def arm(m, loaded=True):
    """nozzle_arm (code rotY), and in it the syringe (keyed posY) and its plunger (keyed posY)."""
    m.bone('nozzle_arm', ARM_PIVOT, code=('rotation',))
    A = 'nozzle_arm'
    # one machined steel arm: a hub ring round the neck (hole 4x4 round the 2x2 neck: clear at every angle) and a bar
    # out to the guide, the block on the syringe's inner side that the barrel slides along (from the front at the aim
    # it is hidden behind the barrel, so the syringe shows no crossbar but its own finger grips)
    for n, f, t in (('hub_f', [-3, 20, -3], [3, 22, -2]), ('hub_b', [-3, 20, 2], [3, 22, 3]),
                    ('hub_l', [-3, 20, -2], [-2, 22, 2]), ('hub_r', [2, 20, -2], [3, 22, 2]),
                    ('guide', [3, 20, -1], [7, 22, 2]),
                    # v2: the rail, a 2x3 steel post on the guide's end up to y 32: the syringe slides up and down
                    # it (the lift, the jab) and hinges at its top on the drawn thumb when it tips out at the aim
                    ('rail', [5, 22, -1], [7, 32, 2])):
        B(m, n, f, t, 'steel', A, part='arm')

    cx, cz = SYR_C
    # v2: the syringe tips out about its top (the drawn thumb, y 27.5), a pendulum: the needle swings out toward the
    # player's upper arm while the thumb stays clear of the globe (about the finger grips it swung into the globe)
    m.bone('syringe', [7, 27.5, cz], A)      # the hinge on the rail's face, at the drawn thumb's top
    S = 'syringe'
    B(m, 'needle', [8, 9, 0], [9, 12, 1], 'zinc', S)              # v2: 3 long (2 in the sheath at rest)
    # the barrel: glass painted opaque, tinted by the serum colour; the finger grips moulded on its top, seated on the
    # tip ring (one part: the barrel runs into its flange)
    B(m, 'barrel', [7, 12, -1], [10, 22, 2], 'sample' if loaded else 'enamel2', S, part='syringe')
    B(m, 'flange', [7, 22, -2], [10, 23, 3], 'zinc', S, part='syringe')      # the finger grips, on the guide
    m.bone('plunger', [cx, 24, cz], S)
    P = 'plunger'
    B(m, 'rod', [8, 17, 0], [9, 24, 1], 'zinc', P)                 # 5 px inside the barrel at rest, 2 when drawn
    B(m, 'thumb', [7, 24, -2], [10, 25, 3], 'brass', P)              # v2: 3x5, the thumb pad wider than the barrel


def model(loaded=True):
    m = kit.Model('injection_rig', budget=3)
    base(m)
    body(m)
    front(m, loaded)
    back(m)
    holster(m)
    top(m, 'sample' if loaded else 'enamel2')
    arm(m, loaded)
    m.locator('dial', [0, 15, -5], None)
    # the cell shapes (Java cell coordinates; upper = the machine_top cell)
    m.shape('lower', (1, 0, 1, 16, 16, 14))      # island, body, belt, ledge, lamp, sheath and the parked syringe
    m.shape('upper', (2, 0, 4, 14, 3, 12))       # the body's top 3 px
    m.shape('upper', (5, 3, 5, 16, 7, 11))       # bearing, hub, hose, guide, flange
    m.shape('upper', (14, 0, 7, 16, 7, 10))      # the parked barrel's top
    m.shape('upper', (4, 6, 6, 12, 14, 10))      # neck and globe
    m.shape('upper', (15, 7, 6, 16, 12, 11))     # the parked plunger (drawn: thumb to y 28)
    m.shape('upper', (13, 6, 7, 15, 16, 10))     # the rail
    return m


# ---------------------------------------------------------------------------------------------------------------------
# Clear glass placeholder (the Mixer's method): frame and streaks opaque, the rest clear (cutout), so the review
# renders show the globe as the house's clear glass instead of the kit's tinted fill.
# ---------------------------------------------------------------------------------------------------------------------
GLASS_FRAME = (0xa8, 0xd0, 0xd9, 255)
GLASS_STREAK = (0xd0, 0xea, 0xe9, 255)


def clear_glass(path, m):
    import base64
    import json
    from PIL import Image
    with open(path, encoding='utf-8') as fh:
        d = json.load(fh)
    png = os.path.splitext(path)[0] + '_placeholder.png'
    im = Image.open(png).convert('RGBA')
    px = im.load()
    names = {c[0] for c in m.cubes if c[3] == 'glass'}
    for e in d['elements']:
        if e.get('type') != 'cube' or e['name'] not in names:
            continue
        for fname, face in e['faces'].items():
            if face.get('texture') is None:
                continue
            u0, v0, u1, v1 = face['uv']
            xa, xb = sorted((int(u0), int(u1)))
            ya, yb = sorted((int(v0), int(v1)))
            for y in range(ya, yb):
                for x in range(xa, xb):
                    edge = x in (xa, xb - 1) or y in (ya, yb - 1)
                    px[x, y] = GLASS_FRAME if edge else (0, 0, 0, 0)
            if xb - xa >= 6 and yb - ya >= 6:
                for (x, y) in [(xa + 2, ya + 1), (xa + 1, ya + 2), (xb - 3, yb - 2), (xb - 2, yb - 3)]:
                    px[x, y] = GLASS_STREAK
    im.save(png)
    with open(png, 'rb') as fh:
        d['textures'][0]['source'] = 'data:image/png;base64,' + base64.b64encode(fh.read()).decode()
    with open(path, 'w', encoding='utf-8') as fh:
        json.dump(d, fh, indent=1)
    return sorted(names)


if __name__ == '__main__':
    m = model()
    m.write(PATH)
    clear_glass(PATH, m)
    print(kit.shapes_java(m))

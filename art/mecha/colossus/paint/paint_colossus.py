"""The Colossus ("the gatehouse"): the painted sheet, every pixel placed from palette keys.

Source of `textures/entity/mecha/colossus.png` and `colossus_glowmask.png` (adopted as `painted` in the texture
manifest). No randomness: each part is painted by a named construction, the same rules as the Hopper's final pass, the
Bulwark, the Wheelhouse and the machine kit.

    python -I art/mecha/colossus/paint/paint_colossus.py                # writes the two PNGs into the mod assets
    python -I art/mecha/colossus/paint/paint_colossus.py --preview DIR  # also DIR/colossus_preview.png with the
                                                                        # stand-ins filled (renders only)
Reads `art/mecha/colossus/colossus.bbmodel` (sizes, positions, box-UV offsets; read only), `art/palette.json` and the
shared helpers in `art/mecha/hopper/paint/paintlib.py`.

The value plan (decided at about 110 px before detail):
- ARCHITECTURE, a step heavier and calmer than the Bulwark: enamel fields e1 (the Bulwark's are e2), lips e2-e3.
  The towers are panelled masonry: a course joint (e0) every 5 rows with the next course's lit lip (e2) under it,
  one staggered vertical joint per course; the slits are black (r0) in a dark steel reveal.
- THE SLOT, the setting for the jar: everything facing the slot is the lightest enamel on the machine (e3-e4 reveals
  on the jambs' and frame strips' inner faces, an e4 lip round the cornice next to the collar); the niches behind
  the clamp posts are the darkest (r0/s0). Light edge, dark niche, and the jar's glass between.
- BRASS only where it belongs: hip drums, the tower caps (trim), the cradles' tops, collar, clamp posts, knee pads,
  toe caps, the nameplate. Field b3 always.
- LEGS from behind, by value: thigh light steel (s4) / the ram down its back dark (s0, an s3 rod glint) / knee axle
  s0 / shin mid steel (s2) / ankle s0 / boot rubber r1 with an r3 welt and a light s3 sole line / brass toe cap.
- Hazard stripes only on the cradles' front and back faces and the knee spades (moving, 4+ px), nowhere else.
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
sys.path.insert(0, os.path.join(ROOT, 'art', 'mecha', 'hopper', 'paint'))
from paintlib import COL, SIDES, Sheet  # noqa: E402

BBMODEL = os.path.join(ROOT, 'art', 'mecha', 'colossus', 'colossus.bbmodel')
PLACEHOLDER = os.path.join(ROOT, 'art', 'mecha', 'colossus', 'colossus_placeholder.png')
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


# ===================================================================================================== constructions
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


def stripes_face(f, top=0, bottom=0):
    for c, r in f.each():
        if r < top:
            k = 's3'
        elif r >= f.H - bottom:
            k = 's0'
        else:
            y = (f.strip0 + c + r) % 4 < 2
            k = ('w3' if y else 'r2') if r == top else ('w2' if y else 'r1')
        f.put(c, r, k)


def polish(f, c0, rows, key='b4', glint='b5'):
    r0, r1 = rows
    for i, r in enumerate(range(r1 - 1, r0 - 1, -1)):
        f.put(c0 + i, r, key)
        f.put(c0 + i + 1, r, key)
    f.put(c0 + (r1 - r0), r0, glint)


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


def brass_top(f, band=True):
    x0, _, z0 = S.cubes[f.cube]['frm']
    for c, r in f.each():
        x, y, z = f.world(c, r)
        f.put(c, r, 'b4' if (z < z0 + 1 or x < x0 + 1) else 'b3')
    if band and f.W >= 6 and f.H >= 6:
        polish(f, 2, (2, min(f.H - 1, 6)))


def brass_housing(cube, boss_side=None, band=True):
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
        if band and f.side != boss_side and f.W >= 5 and f.H >= 4:
            polish(f, 1, (1, min(f.H - 1, 6)))
    if boss_side:
        o = F(cube, boss_side)
        o.rows(['s4 s2', 's2 s0'], c0=(o.W - 2) // 2, r0=max(1, (o.H - 2) // 2))
    brass_top(F(cube, 'up'), band=False)
    F(cube, 'down').fill('b1')


def steel_post(f, field, lit, dark, streaks=(), foot=0):
    f.fill(field)
    f.row(0, lit)
    if f.side in ('north', 'west') and f.W >= 4:
        f.col(0, lit)
    for c, r0, r1, k in streaks:
        for r in range(r0, r1):
            f.put(c, r, k)
    if foot:
        for c in range(f.W):
            for r in range(f.H - foot - (1 if c == 1 else 0), f.H):
                f.put(c, r, dark)
    f.row(f.H - 1, dark)


def masonry(f, y_base, field='e1', joint='e0', bead='e2', course=5, vjoint=True):
    """Panelled masonry on an enamel face: courses `course` rows tall counted from world height y_base, a joint row (e0)
    at the top of each course with the course's lit lip (e2) under it; on faces 6+ wide one vertical joint per course,
    staggered (a third of the way along on even courses, two thirds on odd)."""
    for c, r in f.each():
        x, y, z = f.world(c, r)
        k = (int(y - y_base)) % course
        n = int((y - y_base) // course)
        v = field
        if k == course - 1:
            v = joint
        elif k == course - 2:
            v = bead
        elif vjoint and f.W >= 6 and c == (f.W // 3 if n % 2 == 0 else (2 * f.W) // 3):
            v = joint
        f.put(c, r, v)


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
        k = m + '2'
        if f.W >= 3 and f.H >= 3 and (r in (0, f.H - 1)):
            k = m + '1'
        if (c, r) == (1, 1 if f.H >= 3 else 0):
            k = m + '3'
        f.put(c, r, k, lit=True)


# ===================================================================================================== deck and hips
def paint_deck():
    # the deck: e1 field with the belt (e2 bead, e0 base row); the cornice over it, lit lip e3 with an e4 edge; its
    # top: e2 field, an e4 lip next to the collar (the slot's floor edge), e3 outer rim
    for f in S.faces('deck', SIDES):
        for c, r in f.each():
            f.put(c, r, ('e2', 'e1', 'e2', 'e0')[r])
    F('deck', 'up').fill('e1')
    F('deck', 'down').fill('e0')
    for f in S.faces('cornice', SIDES):
        for c, r in f.each():
            f.put(c, r, 'e4' if r == 0 else 'e2')
    f = F('cornice', 'up')
    for c, r in f.each():
        x, y, z = f.world(c, r)
        mz = z + 2                                      # the cornice is centred on z -2
        m = max(abs(x), abs(z))
        if m < 10.5 and m > 9.5:
            k = 'e4'                                    # the lit lip right round the jar's collar
        elif m <= 9.5:
            k = 'r0'
        elif abs(x) > 16 or mz < -11 or mz > 11:
            k = 'e3'
        else:
            k = 'e2'
        f.put(c, r, k)
    F('cornice', 'down').fill('e0')
    # the course under the deck and the front field: dark steel bands
    for n in ('course', 'front_field'):
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, 's1' if r == 0 else 's0')
        F(n, 'up').fill('s1')
        F(n, 'down').fill('s0')
    # the girder (the hips' beam): dark steel with rivet rows; the nameplate on its back
    for f in S.faces('girder', SIDES):
        steel_post(f, 's1', 's2', 's0')
        for c in range(1, f.W - 2, 4):
            rivet(f, c, 1, 's3', 's0')
    F('girder', 'up').fill('s1')
    F('girder', 'down').fill('s0')
    F('nameplate', 'south').rows(['b4 b4 b4 b4 b4 b4 b4 b4 b4 b3',
                                  'b3 b5 b3 b3 b3 b3 b3 b3 b5 b2',
                                  'b3 b3 b1 b1 b1 b1 b1 b1 b3 b2',
                                  'b3 b3 b3 b1 b1 b1 b1 b3 b3 b2',
                                  'b2 b1 b1 b1 b1 b1 b1 b1 b1 b1'])
    for side in ('east', 'west', 'north', 'down'):
        F('nameplate', side).fill('b2')
    F('nameplate', 'up').fill('b4')
    # the seat, the collar
    f = F('seat', 'up')
    for c, r in f.each():
        x, y, z = f.world(c, r)
        k = 's1'
        if (abs(x), abs(z)) in ((4.5, 4.5), (3.5, 4.5), (4.5, 3.5)):
            k = 'w2'
        f.put(c, r, k)
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
    # hip drums: the big brass; hubs dark with a lit ring
    for n in pair('hip_drum'):
        brass_housing(n)
    for n in pair('hip_hub'):
        fill(n, 's0')
        F(n, outer(n)).rows(['s1 s2 s2 s2 s2 s1', 's2 s0 s0 s0 s0 s1', 's2 s0 s3 s1 s0 s1',
                             's2 s0 s1 s0 s0 s1', 's2 s0 s0 s0 s0 s1', 's1 s1 s1 s1 s1 s0'])
    for n in pair('pier'):
        small(n, 'e', 2, bolt=False)


# ===================================================================================================== towers
def paint_towers():
    for s in ('l', 'r'):
        name = lambda b: '%s_%s' % (b, s)              # noqa: E731
        sg = -1 if s == 'l' else 1
        out_side, in_side = ('west', 'east') if s == 'l' else ('east', 'west')
        # pylon: masonry on its outer and back faces; the face toward the niche dark
        p = name('pylon')
        for f in S.faces(p, SIDES):
            masonry(f, 55)
        F(p, in_side).fill('s0')
        # weathering: one rain streak down the outer face under the cap, placed once
        f = F(p, out_side)
        for c, r in f.each():
            x, y, z = f.world(c, r)
            if abs(z - (1.5 if s == 'l' else -1.5)) < 0.6 and 66 < y < 75:
                f.put(c, r, 'e0')
        F(p, 'down').fill('e0')
        F(p, 'up').fill('e1')
        # niche: the darkest on the machine
        fill(name('niche'), 'r0')
        # jambs: masonry on their front/back; the faces toward the slot are the lit reveal (e4 top row, e3), the faces
        # toward the niche dark
        for b in ('jamb_f', 'jamb_b'):
            n = name(b)
            for f in S.faces(n, SIDES):
                masonry(f, 55)
            f = F(n, in_side)
            for c, r in f.each():
                f.put(c, r, 'e4' if c == (0 if s == 'r' else f.W - 1) or r == 0 else 'e3')
            F(n, 'south' if b == 'jamb_f' else 'north').fill('e0')
            F(n, 'up').fill('e3')
            F(n, 'down').fill('e0')
        # the front frame round the slit: raised, e2 with an e3 top; its inner strip's slot-facing side lit
        for b in ('face_o', 'face_i', 'face_t', 'face_b'):
            n = name(b)
            for f in S.faces(n, SIDES):
                for c, r in f.each():
                    f.put(c, r, 'e3' if r == 0 else 'e2')
            F(n, 'up').fill('e3')
            F(n, 'down').fill('e0')
        F(name('face_i'), in_side).fill('e4')
        f = F(name('face_i'), 'north')
        for c, r in f.each():
            if c == (f.W - 1 if s == 'l' else 0):
                f.put(c, r, 'e4')                        # the reveal's front edge
        # slit: black, a 1 px steel sill catching light at the bottom
        n = name('slit')
        fill(n, 'r0')
        f = F(n, 'north')
        f.row(f.H - 1, 's2')
        # a streak under the slit (the gatehouse is old): one column, three rows, on the frame's bottom piece
        f = F(name('face_b'), 'north')
        f.put(1, 1, 'e1')
        f.put(1, 2, 'e1')
        # cap: brass trim; merlons: enamel blocks with lit tops
        brass_housing(name('cap'), band=True)
        for b in ('merlon_f', 'merlon_b'):
            n = name(b)
            for f in S.faces(n, SIDES):
                for c, r in f.each():
                    f.put(c, r, 'e3' if r == 0 else 'e1')
            F(n, 'up').fill('e3')
            F(n, 'down').fill('e0')
        # the back: grille, louvred vent flap (plain warning yellow with two black louvre slots), the tail lamp
        n = name('grille')
        fill(n, 'r0')
        f = F(n, 'south')
        for c, r in f.each():
            f.put(c, r, 'r2' if r % 2 == 0 else 'r0')
        n = name('vent_flap')
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, 'w3' if r == 0 else ('w1' if r == f.H - 1 else 'w2'))
        f = F(n, 'south')
        for r in (2, 4):
            f.row(r, 'r1', 1, f.W - 1)
        F(n, 'up').fill('w3')
        F(n, 'down').fill('w0')
        F(n, 'north').fill('s1')
        lamp(name('tail_lens'), name('tail_lit'), 'A', 'south')
        # the cradle (moves; 7 px faces): dark steel, hazard stripes on its front and back faces only, a brass top
        # plate, dark hinge
        n = name('cradle')
        for f in S.faces(n, SIDES):
            f.fill('s1')
            f.row(0, 's2')
        for side in ('north', 'south'):
            stripes_face(F(n, side), top=1, bottom=1)
        F(n, 'up').fill('s2')
        F(n, 'down').fill('s0')
        n = name('cradle_top')
        for f in S.faces(n, SIDES):
            f.fill('b3')
        brass_top(F(n, 'up'), band=False)
        F(n, 'down').fill('b1')
        small(name('cradle_hinge'), 's', 1)
        # clamp: quiet brass post, steel knuckle, rubber pad
        n = name('clamp_post')
        for f in S.faces(n, SIDES):
            f.fill('b2' if f.side == in_side else 'b3')
            f.row(f.H - 1, 'b1')
        F(n, 'up').fill('b3')
        F(n, 'down').fill('b1')
        small(name('clamp_knuckle'), 's', 1, bolt=False)
        n = name('clamp_pad')
        fill(n, 'r2')
        F(n, 'up').fill('r3')
        F(n, 'down').fill('r0')


# ===================================================================================================== gate lamp
def paint_gate_lamp():
    small('sensor_neck', 'b', 3, bolt=False)
    h = 'sensor_head'
    for f in S.faces(h, ('east', 'west', 'south')):
        for c, r in f.each():
            f.put(c, r, 'e3' if r == 0 else ('e0' if r == f.H - 1 else 'e1'))
    F(h, 'north').fill('r0')
    F(h, 'up').fill('e2')
    F(h, 'down').fill('e0')
    # the visor rim: raised, lit; the walls toward the sunk lens black
    for n in ('sensor_rim_t', 'sensor_rim_b', 'sensor_rim_l', 'sensor_rim_r'):
        fill(n, 'e2')
        F(n, 'north').fill('e3')
    F('sensor_rim_t', 'north').fill('e4')
    F('sensor_rim_t', 'up').fill('e3')
    F('sensor_rim_t', 'down').fill('r0')
    F('sensor_rim_b', 'up').fill('r0')
    F('sensor_rim_b', 'down').fill('e0')
    F('sensor_rim_l', 'east').fill('r0')
    F('sensor_rim_r', 'west').fill('r0')
    lamp('sensor_lens', 'sensor_lens_lit', 'P', 'north')


# ===================================================================================================== arms
def paint_arms():
    for s in ('l', 'r'):
        n = 'shoulder_' + s
        for f in S.faces(n, SIDES):
            steel_post(f, 's1', 's2', 's0')
        F(n, outer(n)).rows(['-', '- s3 s2', '- s2 s0'], c0=2, r0=1)     # the shoulder pin
        F(n, 'up').fill('s2')
        F(n, 'down').fill('s0')
        n = 'upper_arm_' + s
        for f in S.faces(n, SIDES):
            steel_post(f, 's3', 's4', 's2')
        F(n, 'up').fill('s4')
        F(n, 'down').fill('s1')
        n = 'elbow_' + s
        fill(n, 's0')
        F(n, 'up').fill('s1')
        n = 'forearm_' + s                             # the arm's light block: enamel, panelled
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, 'e3' if r == 0 else ('e0' if r == f.H - 1 else ('e0' if r == 4 else 'e1')))
        rivet(F(n, outer(n)), 1, 2, 'e3', 'e0')
        F(n, 'up').fill('e2')
        F(n, 'down').fill('e0')
        n = 'cuff_' + s
        fill(n, 'r1')
        for f in S.faces(n, SIDES):
            f.row(0, 'r3')
        F(n, 'down').fill('r0')
        n = 'palm_' + s
        for f in S.faces(n, SIDES):
            steel_post(f, 's1', 's2', 's0')
        F(n, 'up').fill('s2')
        F(n, 'down').fill('s0')
        n = 'knuckles_' + s
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, 's4' if r == 0 else 's2')
        F(n, 'up').fill('s4')
        F(n, 'down').fill('s1')
        n = 'fingers_' + s
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, 'w3' if r == 0 else ('w1' if r == f.H - 1 else 'w2'))
        F(n, 'up').fill('w3')
        F(n, 'down').fill('w0')


# ===================================================================================================== legs
def paint_legs():
    for s in ('l', 'r'):
        n = 'thigh_' + s
        for f in S.faces(n, SIDES):
            steel_post(f, 's4', 's5', 's3', streaks=((3, 3, 9, 's5'), (6, 12, 18, 's3')))
            if f.side == outer(n):
                rivet(f, 2, 4, 's5', 's2')
                rivet(f, 2, 20, 's5', 's2')
        F(n, 'up').fill('s4')
        F(n, 'down').fill('s2')
        n = 'thigh_ram_' + s                           # the dark ram down the thigh's back: s0 body, polished rod top
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, ('s3' if c == 1 else 's2') if r < 5 else 's0')
            f.row(5, 'r0')                              # the gland where the rod enters the cylinder
        F(n, 'up').fill('s3')
        F(n, 'down').fill('s0')
        fill('knee_axle_' + s, 's0')
        n = 'knee_pad_' + s
        brass_housing(n)
        f = F(n, 'north')
        f.put(f.W // 2, 3, 'b5')
        for b in ('spade_o', 'spade_i'):               # the knee spades (move; 4 px faces): hazard stripes
            n = '%s_%s' % (b, s)
            for f in S.faces(n, SIDES):
                stripes_face(f)
            F(n, 'up').fill('w3')
            F(n, 'down').fill('s3')                     # the worn steel teeth
        n = 'shin_' + s
        for f in S.faces(n, SIDES):
            steel_post(f, 's2', 's3', 's1', streaks=((2, 3, 9, 's3'), (6, 11, 16, 's1')), foot=2)
        F(n, 'up').fill('s2')
        F(n, 'down').fill('s0')
        n = 'ankle_' + s
        for f in S.faces(n, SIDES):
            f.fill('s0')
            f.row(0, 's1')
        F(n, 'up').fill('s1')
        F(n, 'down').fill('s0')
        n = 'boot_' + s
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, ('r3', 'r1', 'r1', 's3')[r])
        f = F(n, 'up')
        x0, _, z0 = S.cubes[n]['frm']
        x1, z1 = x0 + S.cubes[n]['w'], z0 + S.cubes[n]['d']
        for c, r in f.each():
            x, y, z = f.world(c, r)
            edge = x < x0 + 1 or x > x1 - 1 or z < z0 + 1 or z > z1 - 1
            f.put(c, r, 'r3' if edge else 'r2')
        F(n, 'down').fill('r0')
        n = 'toe_cap_' + s
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, ('b4', 'b3', 'b1')[r])
        F(n, 'north').put(5, 0, 'b5')
        F(n, 'north').put(6, 0, 'b5')
        F(n, 'up').fill('b4')
        F(n, 'down').fill('b1')


def paint_all():
    paint_deck()
    paint_towers()
    paint_gate_lamp()
    paint_arms()
    paint_legs()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview', help='also write DIR/colossus_preview.png with the stand-ins filled')
    ap.add_argument('--out', default=OUT)
    a = ap.parse_args()
    paint_all()
    miss = S.missing()
    if miss:
        sys.stderr.write('unpainted faces: %s\n' % miss)
        return 1
    os.makedirs(a.out, exist_ok=True)
    S.img.save(os.path.join(a.out, 'colossus.png'))
    S.glow.save(os.path.join(a.out, 'colossus_glowmask.png'))
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
        pv.save(os.path.join(a.preview, 'colossus_preview.png'))
    print('wrote colossus.png and colossus_glowmask.png to %s' % a.out)
    return 0


if __name__ == '__main__':
    sys.exit(main())

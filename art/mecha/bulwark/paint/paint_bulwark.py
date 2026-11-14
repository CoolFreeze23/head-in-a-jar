"""The Bulwark (Noggin & Sons heavy loader): the painted sheet, every pixel placed from palette keys.

Source of `textures/entity/mecha/bulwark.png` and `bulwark_glowmask.png` (adopted as `painted` in the texture
manifest). No randomness: each part is painted by a named construction, the same rules as the Hopper's final pass and
the machine kit.

    python -I art/mecha/bulwark/paint/paint_bulwark.py                # writes the two PNGs into the mod assets
    python -I art/mecha/bulwark/paint/paint_bulwark.py --preview DIR  # also DIR/bulwark_preview.png with the
                                                                      # stand-ins filled from the placeholder
Reads `art/mecha/bulwark/bulwark.bbmodel` (cube sizes, positions, box-UV offsets; read only), `art/palette.json`, and
the shared helpers in `art/mecha/hopper/paint/paintlib.py`.

The value plan, decided on a render about 110 px tall before any detail:
- LIGHT: the enamel body (fields e2, lips e3-e4); the arms' enamel upper arms.
- ACCENT: brass (field b3, never darkened): collar, clamp posts, shoulder caps, forearms, hip housings, knee pads,
  toe caps. Warm and light, so it separates the arms and the joints from the cool steel.
- The legs are a value LADDER, not one grey: brass hip housing (light) / thigh s4 (light steel) / dark knee axle
  / brass knee pad / shin s1 (dark steel) / ankle hazard band (yellow and black) / boot r1 with a lit r3 welt and a
  light s3 sole line / brass toe cap. Each step changes value, not only hue.
- DARK: belly, collar ring, elbows, knee axles, the fists (s1), the recessed back field and flank panels (e0), vent
  mouths and lamp hoods (r0).
- The arms stay apart from the legs from behind: light enamel and brass blocks outboard, ending in dark fists with a
  yellow finger block; the legs inboard are cool steel.
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
sys.path.insert(0, os.path.join(ROOT, 'art', 'mecha', 'hopper', 'paint'))
from paintlib import COL, SIDES, Sheet  # noqa: E402

BBMODEL = os.path.join(ROOT, 'art', 'mecha', 'bulwark', 'bulwark.bbmodel')
PLACEHOLDER = os.path.join(ROOT, 'art', 'mecha', 'bulwark', 'bulwark_placeholder.png')
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'headinajar', 'textures', 'entity', 'mecha')

S = Sheet(BBMODEL)
F = S.face


def both(name):
    return (name + '_l', name + '_r')


def outer_side(name):
    return 'west' if name.endswith('_l') else 'east'


def inner_side(name):
    return 'east' if name.endswith('_l') else 'west'


# ===================================================================================================== constructions
def fill(cube, key, sides=SIDES + ('up', 'down')):
    for f in S.faces(cube, sides):
        f.fill(key)


def rivet(f, c, r, lit, dark):
    f.put(c, r, lit)
    f.put(c + 1, r + 1, dark)


def recess(cube, key='r0'):
    fill(cube, key)


def small(cube, m, field, bolt=True, sides=SIDES):
    """Small parts: one tone per side face, top field + 1, underside field - 1, one bolt accent."""
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


def stripes_face(f, top=0, bottom=0, phase=0):
    """45-degree hazard stripes on one face (4+ px wide): 2 px w2 / 2 px r1, period 4 along the side strip so they
    continue round corners; the band's first row a step lighter (w3 / r2); optional steel frame rows."""
    for c, r in f.each():
        if r < top:
            k = 's3'
        elif r >= f.H - bottom:
            k = 's0'
        else:
            y = (f.strip0 + c + r + phase) % 4 < 2
            k = ('w3' if y else 'r2') if r == top else ('w2' if y else 'r1')
        f.put(c, r, k)


def polish(f, c0, rows, key='b4', glint='b5'):
    """The brass polish band: 2 px, 45 degrees, rising right from column c0; its top pixel a glint. Once per face."""
    r0, r1 = rows
    for i, r in enumerate(range(r1 - 1, r0 - 1, -1)):
        f.put(c0 + i, r, key)
        f.put(c0 + i + 1, r, key)
    f.put(c0 + (r1 - r0), r0, glint)


def brass_bar(f):
    """Brass rail seen from the side: row 0 a continuous b4, field b3, a b1 last row on faces 3+ tall, b2 end columns
    on faces 4+ wide."""
    for c, r in f.each():
        end = f.W >= 4 and c in (0, f.W - 1)
        if r == 0:
            k = 'b3' if end else 'b4'
        elif r == f.H - 1 and f.H >= 3:
            k = 'b1'
        else:
            k = 'b2' if end else 'b3'
        f.put(c, r, k)


def brass_top(f, lit_edge=True, band=True):
    """Brass top face: a b3 field, b4 along the lit north and west edges; on faces 6x6+ one polish band at (2, 2)."""
    x0, _, z0 = S.cubes[f.cube]['frm']
    for c, r in f.each():
        x, y, z = f.world(c, r)
        f.put(c, r, 'b4' if lit_edge and (z < z0 + 1 or x < x0 + 1) else 'b3')
    if band and f.W >= 6 and f.H >= 6:
        polish(f, 2, (2, min(f.H - 1, 6)))


def brass_housing(cube, outer=None, boss=True):
    """A cast brass housing: row 0 b4 (b3 at the corners), field b3, end columns b2, last row b1; one polish band on
    faces 5+ wide and 4+ tall (not on the outer face, which carries a 2x2 steel pivot boss instead)."""
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
        if f.side != outer and f.W >= 5 and f.H >= 4:
            polish(f, 1, (1, f.H - 1))
    if outer and boss:
        o = F(cube, outer)
        o.rows(['s4 s2', 's2 s0'], c0=(o.W - 2) // 2, r0=max(1, (o.H - 2) // 2))
    brass_top(F(cube, 'up'), band=False)
    F(cube, 'down').fill('b1')


def steel_post(f, field, lit, dark, streaks=(), foot=0):
    """Steel limb face: one field, top row lit, an s-lit arris column on north and west faces only, placed streaks,
    the last `foot` rows a step darker with one placed step, the last row the dark step."""
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


def enamel_face(f, notch=False, belt=True):
    """Enamel body face painted by world height over the body (y 22-36 here): the top row the e3 lip (e4 on the main
    run), a flat e2 field, and on faces 7+ tall the lapped belt seam at the foot: an e3 bead, an e1 skirt and an e0
    base row. The two end columns e1 (a 1 px frame against the stepped corner). Notch strips one step darker."""
    for c, r in f.each():
        x, y, z = f.world(c, r)
        top = S.cubes[f.cube]['frm'][1] + S.cubes[f.cube]['h']
        bot = S.cubes[f.cube]['frm'][1]
        if y > top - 1:
            k = 'e4' if c not in (0, f.W - 1) else 'e3'
        elif belt and f.H >= 7 and y < bot + 1:
            k = 'e0'
        elif belt and f.H >= 7 and y < bot + 2:
            k = 'e1'
        elif belt and f.H >= 7 and y < bot + 3:
            k = 'e3'
        else:
            k = 'e1' if c in (0, f.W - 1) else 'e2'
        if notch:
            k = {'e4': 'e3', 'e3': 'e2', 'e2': 'e1', 'e1': 'e1', 'e0': 'e0'}[k]
        f.put(c, r, k)


# ===================================================================================================== body
def paint_body():
    # the two crossed boxes: body carries the flanks (and the deck), body_cross the front and back
    for side in ('east', 'west'):
        enamel_face(F('body', side))
        enamel_face(F('body_cross', side), notch=True)
    for side in ('north', 'south'):
        enamel_face(F('body', side), notch=True)
        enamel_face(F('body_cross', side))
    # front: the kit's T9 inset (a pressed panel inset 2 px: top row and left column e1 in shadow, bottom row and
    # right column e3 lit, the field inside e2), and a rivet pair under the lip. The torso sockets mount on it.
    fr = F('body_cross', 'north')
    for c, r in fr.each():
        if 2 <= c <= fr.W - 3 and 2 <= r <= 8:
            k = 'e2'
            if r == 2 or c == 2:
                k = 'e1'
            elif r == 8 or c == fr.W - 3:
                k = 'e3'
            fr.put(c, r, k)
    rivet(fr, 3, 3, 'e4', 'e0')
    rivet(fr, fr.W - 5, 3, 'e4', 'e0')
    # back: the recessed field inside the lamp bar and the rims, the darkest enamel; shadow under the bar and down the
    # left wall
    bk = F('body_cross', 'south')
    for c, r in bk.each():
        x, y, z = bk.world(c, r)
        if -10 < x < 10 and 23 < y < 31:
            k = 'e0'
            if y > 30 or x < -9:
                k = 'r1'
            bk.put(c, r, k)
    # flanks: the recessed panel (z -8..-5, y 24..29) and the hinge strip above the vent flap
    for side in ('east', 'west'):
        f = F('body', side)
        for c, r in f.each():
            x, y, z = f.world(c, r)
            if -8 < z < -5 and 24 < y < 29:
                f.put(c, r, 'r1' if y > 28 else 'e0')
            if 2 < z < 9 and 31 < y < 32:
                f.put(c, r, 's2' if 2.5 < z < 8.5 else 's3')
        # a rivet pair over the vent, and one chip at the lower rear corner (where a load scrapes)
        cs = [c for c in range(f.W) if 0.4 < f.world(c, 0)[2] < 1.6]
        rivet(f, cs[0], 3, 'e4', 'e0')
        rc = [c for c in range(f.W) if f.world(c, 0)[2] > 8][0 if side == 'west' else -1]
        f.put(rc, f.H - 3, 's2')
    for side in ('l', 'r'):
        for part in ('t', 'b', 'f', 'k'):
            n = 'flank_rim_%s_%s' % (part, side)
            fill(n, 'e3')
            F(n, 'up').fill('e4')
            F(n, 'down').fill('e1')
    # deck: the ring outside the collar, the dark well inside it, the seat plate
    for cube in ('body', 'body_cross'):
        f = F(cube, 'up')
        for c, r in f.each():
            x, y, z = f.world(c, r)
            m = max(abs(x), abs(z))
            if m > 9.5:
                edge = (z < -8.5 and cube == 'body') or (x < -13 and cube == 'body') or \
                       (z < -10 and cube == 'body_cross')
                k = 'e4' if edge else 'e3'
            elif m > 6:
                k = 'r0'
            else:
                k = 's0'
            f.put(c, r, k)
        F(cube, 'down').fill('e0')
    f = F('seat', 'up')
    for c, r in f.each():
        x, y, z = f.world(c, r)
        k = 's1'
        if x < -5 or z < -5:
            k = 's2'
        if x > 5 or z > 5:
            k = 's0'
        if (abs(x), abs(z)) in ((4.5, 4.5), (3.5, 4.5), (4.5, 3.5)):
            k = 'w2'
        f.put(c, r, k)
    for f in S.faces('seat', SIDES):
        f.fill('s2')
    F('seat', 'down').fill('s0')
    # belly: the dark mass between the thighs; steel, its front and back faces with a lit top row
    for f in S.faces('belly', SIDES):
        for c, r in f.each():
            f.put(c, r, 's1' if r == 0 else 's0')
    F('belly', 'down').fill('r0')
    F('belly', 'up').fill('s0')
    # hip housings: brass, light, the top of the leg ladder
    for n in both('hip_housing'):
        brass_housing(n, outer_side(n))


# ===================================================================================================== jar frame
def paint_frame():
    for n in ('collar_base_f', 'collar_base_b', 'collar_base_l', 'collar_base_r'):
        recess(n, 'r0')
    for n in ('collar_f', 'collar_b', 'collar_l', 'collar_r'):
        for f in S.faces(n, SIDES):
            brass_bar(f)
        f = F(n, 'up')
        for c, r in f.each():
            x, y, z = f.world(c, r)
            f.put(c, r, 'b4' if max(abs(x), abs(z)) > 8.5 else 'b3')
        F(n, 'down').fill('b1')
    # the clamp posts: quiet brass (the jar's frame): one b3 field per side, b4 top, b1 foot; steel knuckles; pads
    for n in both('clamp_post'):
        for f in S.faces(n, SIDES):
            f.fill('b3')
            f.row(f.H - 1, 'b1')
        F(n, 'up').fill('b4')
        F(n, 'down').fill('b1')
    for n in both('clamp_knuckle'):
        small(n, 's', 2)
    for n in both('clamp_pad'):
        fill(n, 'r2')
        F(n, inner_side(n)).rows(['r3 r1 r3 r1', 'r2 r1 r2 r1'])    # grip ribs on the face against the glass
        F(n, 'up').fill('r3')
        F(n, 'down').fill('r0')


# ===================================================================================================== back, lamps, sensor
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
        if f.W >= 3 and f.H >= 3 and (c in (0, f.W - 1) or r in (0, f.H - 1)):
            k = m + '1'
        if (c, r) == ((1, 1) if (f.W >= 3 and f.H >= 3) else (1, 0)):
            k = m + '3'
        f.put(c, r, k, lit=True)


def paint_back_and_lamps():
    for n in ('lampbar_l', 'lampbar_r'):
        f = F(n, 'south')
        for c, r in f.each():
            f.put(c, r, 'e4' if r == 0 else ('e2' if r == f.H - 1 else 'e3'))
        F(n, 'up').fill('e3')
        F(n, 'down').fill('e1')
        for side in ('east', 'west'):
            F(n, side).fill('e2')
        F(n, 'north').fill('e1')
    # the sinks the tail lamps sit in: dark walls
    F('lampbar_l', 'east').fill('r0')
    F('nameplate', 'west').fill('r0')
    F('nameplate', 'east').fill('r0')
    F('lampbar_r', 'west').fill('r0')
    # the brass nameplate: lit top edge, b3 field, two rivets, the company line engraved (two b1 runs)
    F('nameplate', 'south').rows(['b4 b4 b4 b4 b4 b4 b4 b3',
                                  'b3 b5 b1 b1 b1 b2 b5 b2',
                                  'b2 b1 b1 b1 b1 b1 b1 b1'])
    brass_top(F('nameplate', 'up'), band=False)
    F('nameplate', 'north').fill('b1')
    F('nameplate', 'down').fill('b1')
    for n in ('rim_bl', 'rim_br'):
        f = F(n, 'south')
        for c, r in f.each():
            f.put(c, r, 'e4' if c == 0 else 'e3')
        F(n, 'up').fill('e3')
        F(n, 'down').fill('e1')
        F(n, 'north').fill('e1')
    F('rim_bl', 'east').fill('e2')
    F('rim_bl', 'west').fill('e2')
    F('rim_br', 'west').fill('e0')
    F('rim_br', 'east').fill('e1')
    F('rim_bb', 'south').fill('e3')
    F('rim_bb', 'up').fill('e4')
    F('rim_bb', 'down').fill('e1')
    for side in ('east', 'west', 'north'):
        F('rim_bb', side).fill('e2')
    lamp('lens_tl', 'lamp_tl_lit', 'A', 'south')
    lamp('lens_tr', 'lamp_tr_lit', 'A', 'south')
    lamp('sensor_lens', 'sensor_lens_lit', 'P', 'north')
    # cab-lamp turret: brass neck (quiet), enamel head, a raised enamel rim round the sunk lens (inner walls black)
    small('sensor_neck', 'b', 3, bolt=False)
    h = 'sensor_head'
    for f in S.faces(h, ('east', 'west', 'south')):
        for c, r in f.each():
            f.put(c, r, 'e4' if r == 0 else ('e1' if r == f.H - 1 else 'e2'))
    F(h, 'north').fill('r0')
    F(h, 'up').fill('e3')
    F(h, 'down').fill('e0')
    for n in ('sensor_rim_t', 'sensor_rim_b', 'sensor_rim_l', 'sensor_rim_r'):
        fill(n, 'e2')
        F(n, 'north').fill('e3')
    F('sensor_rim_t', 'north').fill('e4')
    F('sensor_rim_t', 'up').fill('e4')
    F('sensor_rim_t', 'down').fill('r0')
    F('sensor_rim_b', 'up').fill('r0')
    F('sensor_rim_b', 'north').fill('e2')
    F('sensor_rim_b', 'down').fill('e0')
    F('sensor_rim_l', 'east').fill('r0')
    F('sensor_rim_r', 'west').fill('r0')
    F('sensor_rim_r', 'north').fill('e2')
    # vents: rubber mouths with louvres, striped flaps (7 px faces: the stripe rule, framed top and bottom)
    for n in both('grille'):
        fill(n, 'r0')
        f = F(n, outer_side(n))
        for c, r in f.each():
            f.put(c, r, 'r2' if r % 2 == 0 else 'r0')
    for n in both('vent_flap'):
        stripes_face(F(n, outer_side(n)), top=1, bottom=1)
        F(n, inner_side(n)).fill('s1')
        for side in ('north', 'south'):
            f = F(n, side)
            for c, r in f.each():
                f.put(c, r, 'w2' if r == 0 else 'w1')
        F(n, 'up').fill('w3')
        F(n, 'down').fill('w0')


# ===================================================================================================== arms
def paint_arms():
    for n in both('shoulder_boss'):
        recess(n, 's0')
    for n in both('shoulder'):
        brass_housing(n, outer_side(n))
        brass_top(F(n, 'up'))                           # 6x7 top: with the polish band
    # upper arm: light enamel (the arm's light block from behind)
    for n in both('upper_arm'):
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, 'e4' if r == 0 else ('e1' if r == f.H - 1 else 'e2'))
        F(n, 'up').fill('e3')
        F(n, 'down').fill('e0')
    for n in both('elbow'):
        recess(n, 's0')
    # forearm: brass casing with the polish band; cuff: rubber, lit top row
    for n in both('forearm'):
        brass_housing(n, outer_side(n), boss=False)
        rivet(F(n, outer_side(n)), 1, 2, 'b5', 'b1')
    for n in both('cuff'):
        fill(n, 'r1')
        for f in S.faces(n, SIDES):
            f.row(0, 'r3')
        F(n, 'up').fill('r2')
        F(n, 'down').fill('r0')
    # fist: dark steel palm (the dark end of the arm), lit knuckle bar, yellow finger block with hazard stripes on its
    # two 7 px faces (it moves), plain yellow on its 3 px faces
    for n in both('palm'):
        for f in S.faces(n, SIDES):
            steel_post(f, 's1', 's2', 's0')
            rivet(f, 1, 2, 's3', 's0') if f.side == outer_side(n) else None
        F(n, 'up').fill('s2')
        F(n, 'down').fill('s0')
    for n in both('knuckles'):
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, 's4' if r == 0 else 's2')
        F(n, 'up').fill('s4')
        F(n, 'down').fill('s1')
    for n in both('fingers'):
        for side in ('north', 'south'):
            stripes_face(F(n, side))
        for side in ('east', 'west'):
            f = F(n, side)
            for c, r in f.each():
                f.put(c, r, 'w3' if r == 0 else ('w1' if r == f.H - 1 else 'w2'))
        F(n, 'up').fill('w3')
        F(n, 'down').fill('w0')


# ===================================================================================================== legs
def paint_legs():
    for n in both('thigh'):
        for f in S.faces(n, SIDES):
            steel_post(f, 's4', 's5', 's3', streaks=((3, 2, 7, 's5'), (5, 8, 12, 's3')))   # light steel
            if f.side == outer_side(n):
                rivet(f, 2, 3, 's5', 's2')
                rivet(f, 2, 12, 's5', 's2')
        F(n, 'up').fill('s4')
        F(n, 'down').fill('s2')
    for n in both('knee_axle'):
        recess(n, 's0')
    for n in both('knee_pad'):
        brass_housing(n, None)
        f = F(n, 'north')
        f.put(f.W // 2, f.H // 2, 'b5')                 # one polished boss where the knee takes knocks
        f.put(f.W // 2 + 1, f.H // 2 + 1, 'b1')
    for n in both('shin'):
        for f in S.faces(n, SIDES):
            steel_post(f, 's1', 's2', 's0', streaks=((2, 2, 7, 's2'), (5, 9, 13, 's2')), foot=2)
        F(n, 'up').fill('s1')
        F(n, 'down').fill('s0')
    for n in both('ankle'):
        for f in S.faces(n, SIDES):
            stripes_face(f)                             # the crush zone: a hazard band (9 px faces) between shin and boot
        F(n, 'up').fill('s2')
        F(n, 'down').fill('s0')
    # boots: rubber, a lit welt along the top edge, a light steel sole line along the bottom (the boot's outline at
    # distance), a lighter rim on top; brass toe caps with a scuff
    for n in both('boot'):
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, ('r3', 'r1', 's3')[r])
        f = F(n, 'up')
        x0, _, z0 = S.cubes[n]['frm']
        x1, z1 = x0 + S.cubes[n]['w'], z0 + S.cubes[n]['d']
        for c, r in f.each():
            x, y, z = f.world(c, r)
            edge = x < x0 + 1 or x > x1 - 1 or z < z0 + 1 or z > z1 - 1
            f.put(c, r, 'r3' if edge else 'r2')
        F(n, 'down').fill('r0')
    for n in both('toe_cap'):
        f = F(n, 'north')
        for c, r in f.each():
            f.put(c, r, ('b4', 'b2')[r] if c not in (0, f.W - 1) else ('b3', 'b1')[r])
        f.put(f.W // 2, 0, 'b5')                        # scuffed bright where it kicks
        f.put(f.W // 2 + 1, 0, 'b5')
        for side in ('east', 'west', 'south'):
            F(n, side).fill('b2')
        F(n, 'up').fill('b4')
        F(n, 'down').fill('b1')


def paint_all():
    paint_body()
    paint_frame()
    paint_back_and_lamps()
    paint_arms()
    paint_legs()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview', help='also write DIR/bulwark_preview.png with the stand-ins filled')
    ap.add_argument('--out', default=OUT)
    a = ap.parse_args()
    paint_all()
    miss = S.missing()
    if miss:
        sys.stderr.write('unpainted faces: %s\n' % miss)
        return 1
    os.makedirs(a.out, exist_ok=True)
    S.img.save(os.path.join(a.out, 'bulwark.png'))
    S.glow.save(os.path.join(a.out, 'bulwark_glowmask.png'))
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
        pv.save(os.path.join(a.preview, 'bulwark_preview.png'))
    print('wrote bulwark.png and bulwark_glowmask.png to %s' % a.out)
    return 0


if __name__ == '__main__':
    sys.exit(main())

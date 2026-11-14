"""The Wheelhouse (Noggin & Sons site tug): the painted sheet, every pixel placed from palette keys.

Source of `textures/entity/mecha/wheelhouse.png` and `wheelhouse_glowmask.png` (adopted as `painted` in the texture
manifest). No randomness: each part is painted by a named construction, the same rules as the Hopper's, the
Bulwark's and the machine kit's sheets.

    python -I art/mecha/wheelhouse/paint/paint_wheelhouse.py                # writes the two PNGs into the assets
    python -I art/mecha/wheelhouse/paint/paint_wheelhouse.py --preview DIR  # also DIR/wheelhouse_preview.png with
                                                                            # the stand-ins filled (renders only)
Reads `art/mecha/wheelhouse/wheelhouse.bbmodel` (sizes, positions, box-UV offsets; read only), `art/palette.json`
and the shared helpers in `art/mecha/hopper/paint/paintlib.py`.

The value plan (decided at about 110 px before detail):
- LIGHT: the enamel body (fields e2, lips e3-e4; mudguard tops e3 with e4 edges).
- ACCENT: brass at b3 (spider hubs bright, the ship's wheel, the turret drum, lamp housings); the binnacle CALMED by
  its surrounds: its posts' inner faces a step down (b2), dark steel feet (s1), a quiet ring (b3 field, b4 only on
  the outer top edge, no glints) so the jar, not the frame, is the first read.
- DARK, in three separate values so the skirt does not merge: chassis s0 (rails, axles, stubs, stays: the darkest),
  tyres r1-r3 (sidewall r2 with an r3 highlight on its upper front arc, a running surface of r2 ribs and r0 grooves
  that run round the circumference, one centre groove), the body's underside e1 (light against both).
- Hazard yellow only on moving parts 4 px or wider: the buffer beam, the vent flaps, the exhaust caps, the flag.
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
sys.path.insert(0, os.path.join(ROOT, 'art', 'mecha', 'hopper', 'paint'))
from paintlib import COL, SIDES, Sheet  # noqa: E402

BBMODEL = os.path.join(ROOT, 'art', 'mecha', 'wheelhouse', 'wheelhouse.bbmodel')
PLACEHOLDER = os.path.join(ROOT, 'art', 'mecha', 'wheelhouse', 'wheelhouse_placeholder.png')
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'headinajar', 'textures', 'entity', 'mecha')

S = Sheet(BBMODEL)
F = S.face
CORNERS = ('fl', 'fr', 'rl', 'rr')
WHEEL_Z = {'fl': -13.0, 'fr': -13.0, 'rl': 11.0, 'rr': 11.0}
AXLE_Y = 7.0


def pair(name):
    return (name + '_l', name + '_r')


def fpair(name):
    return (name + '_fl', name + '_fr')


def side_of(name):
    return -1 if name.endswith(('_l', '_fl', '_rl')) else 1


def outer(name):
    return 'west' if side_of(name) < 0 else 'east'


def inner(name):
    return 'east' if side_of(name) < 0 else 'west'


# ===================================================================================================== constructions
def fill(cube, key, sides=SIDES + ('up', 'down')):
    for f in S.faces(cube, sides):
        f.fill(key)


def rivet(f, c, r, lit, dark):
    f.put(c, r, lit)
    f.put(c + 1, r + 1, dark)


def small(cube, m, field, bolt=True, sides=SIDES):
    """Small parts: one tone per side, top + 1, underside - 1, one bolt."""
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


def stripes_face(f, phase=0):
    """45-degree hazard stripes (2 px w2 / 2 px r1, period 4 along the side strip); first row a step lighter."""
    for c, r in f.each():
        y = (f.strip0 + c + r + phase) % 4 < 2
        f.put(c, r, ('w3' if y else 'r2') if r == 0 else ('w2' if y else 'r1'))


def polish(f, c0, rows, key='b4', glint='b5'):
    r0, r1 = rows
    for i, r in enumerate(range(r1 - 1, r0 - 1, -1)):
        f.put(c0 + i, r, key)
        f.put(c0 + i + 1, r, key)
    f.put(c0 + (r1 - r0), r0, glint)


def brass_bar(f, quiet=False):
    """Brass rail from the side: row 0 a continuous b4 (b3 when quiet), field b3, b1 last row on 3+ rows, b2 end
    columns on faces 4+ wide."""
    for c, r in f.each():
        end = f.W >= 4 and c in (0, f.W - 1)
        if r == 0 and not quiet:
            k = 'b3' if end else 'b4'
        elif r == f.H - 1 and f.H >= 3:
            k = 'b1'
        elif quiet and r == f.H - 1:
            k = 'b2'
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
            polish(f, 1, (1, f.H - 1))
    if boss_side:
        o = F(cube, boss_side)
        o.rows(['s4 s2', 's2 s0'], c0=(o.W - 2) // 2, r0=max(1, (o.H - 2) // 2))
    brass_top(F(cube, 'up'), band=False)
    F(cube, 'down').fill('b1')


def enamel_face(f, notch=False):
    """Enamel face by world height over its cube: top row the e4 lip (e3 at the ends), flat e2 field, and on faces
    5+ tall the lapped belt at the foot (e3 bead, e1 skirt, e0 base row on 7+; e3 bead and e1 base on 5-6)."""
    top = S.cubes[f.cube]['frm'][1] + S.cubes[f.cube]['h']
    bot = S.cubes[f.cube]['frm'][1]
    for c, r in f.each():
        x, y, z = f.world(c, r)
        if y > top - 1:
            k = 'e4' if c not in (0, f.W - 1) else 'e3'
        elif f.H >= 7 and y < bot + 1:
            k = 'e0'
        elif f.H >= 5 and y < bot + (2 if f.H >= 7 else 1):
            k = 'e1'
        elif f.H >= 5 and y < bot + (3 if f.H >= 7 else 2):
            k = 'e3'
        else:
            k = 'e2'
        if notch:
            k = {'e4': 'e3', 'e3': 'e2', 'e2': 'e1', 'e1': 'e1', 'e0': 'e0'}[k]
        f.put(c, r, k)


def enamel_top(f):
    """Enamel top face: e3 field with e4 along the lit north and west edges."""
    x0, _, z0 = S.cubes[f.cube]['frm']
    for c, r in f.each():
        x, y, z = f.world(c, r)
        f.put(c, r, 'e4' if (z < z0 + 1 or x < x0 + 1) else 'e3')


def raised_rim(cube, field='e3', lip='e4'):
    """A raised enamel frame piece: its outward face the field with a lit top row, its top lip lit, underside e1."""
    for f in S.faces(cube, SIDES):
        for c, r in f.each():
            f.put(c, r, lip if r == 0 else field)
    F(cube, 'up').fill(lip)
    F(cube, 'down').fill('e1')


def dark_steel(cube):
    """Chassis and running gear: the darkest steel. Sides s0 with an s1 top row, top s1, underside s0."""
    for f in S.faces(cube, SIDES):
        f.fill('s0')
        if f.H >= 2:
            f.row(0, 's1')
    F(cube, 'up').fill('s1')
    F(cube, 'down').fill('s0')


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
        if (c, r) == (0, 0):
            k = m + '3'                                 # the pale core, top left
        f.put(c, r, k, lit=True)


# ===================================================================================================== wheels
def paint_wheels():
    for cn in CORNERS:
        zc = WHEEL_Z[cn]
        # the tyre (never spins): sidewall shaded as a round thing (r3 on its upper front arc, r2 field, r1 on the
        # lower back arc), a 1 px r1 bead next to the disc; the running surface r2 ribs and r0 grooves running ROUND
        # the circumference, so a still tyre on a turning wheel shows nothing that should be moving
        for part in ('tyre_top', 'tyre_bot', 'tyre_f', 'tyre_k', 'tyre_c1', 'tyre_c2', 'tyre_c3', 'tyre_c4'):
            n = '%s_%s' % (part, cn)
            for f in S.faces(n):
                for c, r in f.each():
                    x, y, z = f.world(c, r)
                    dy, dz = y - AXLE_Y, z - zc
                    if f.side in ('east', 'west'):
                        if f.side == outer(n):
                            rr = max(abs(dy), abs(dz))
                            if rr < 4.6:
                                k = 'r1'                # the bead round the disc
                            elif dy > 2 and dz < 1:
                                k = 'r3'                # the arc catching the light
                            elif dy < -2 and dz > -1:
                                k = 'r1'
                            else:
                                k = 'r2'
                        else:
                            k = 'r1'
                    else:
                        # running surface: one centre groove running round the tyre, ribs either side
                        xo = abs(x) - 12
                        k = 'r0' if 2 < xo < 3 else 'r2'        # one centre groove, running round
                        if f.side == 'up' and dy > 0:
                            k = 'r3' if k == 'r2' else 'r1'
                        if f.side == 'down':
                            k = 'r0'
                    f.put(c, r, k)
        # the steel disc (spins), recessed 2 px: s2 field, s1 rim, four s4 lug nuts that turn with it
        n = 'rim_' + cn
        fill(n, 's1')
        f = F(n, outer(n))
        for c, r in f.each():
            x, y, z = f.world(c, r)
            dy, dz = y - AXLE_Y, z - zc
            k = 's1' if max(abs(dy), abs(dz)) > 3.4 else 's2'
            if (abs(dy), abs(dz)) in ((2.5, 2.5),):
                k = 's4'
            f.put(c, r, k)
        # the brass spider (spins), bright: b3 arms with a b4 lit top row, b5 on the outer face's centre; ONE arm
        # (the upper end of the vertical bar) marked with a dark index band so its turning is seen
        for part in ('spider_v', 'spider_h'):
            n = '%s_%s' % (part, cn)
            for f in S.faces(n):
                for c, r in f.each():
                    f.put(c, r, 'b3')
                if f.side in SIDES:
                    f.row(0, 'b4')
            F(n, 'down').fill('b2')
            f = F(n, outer(n))
            for c, r in f.each():
                x, y, z = f.world(c, r)
                dy, dz = y - AXLE_Y, z - zc
                k = 'b4'
                if abs(dy) < 1 and abs(dz) < 1:
                    k = 'b5'
                if part == 'spider_v' and dy > 1.9:
                    k = 'r1'                            # the index mark: one dark arm tip
                f.put(c, r, k)
            if part == 'spider_v':
                for f in S.faces(n, ('north', 'south', 'up')):
                    for c, r in f.each():
                        x, y, z = f.world(c, r)
                        if y - AXLE_Y > 1.9:
                            f.put(c, r, 'r1')
        for part in ('stub', 'wing_stay'):
            n = '%s_%s' % (part, cn)
            if n in S.cubes:
                dark_steel(n)
        n = 'damper_' + cn
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, 's3' if r < 2 else 's1')     # the polished rod above, the dark body below
        F(n, 'up').fill('s4')
        F(n, 'down').fill('s0')
    for n in ('wing_stay_fl', 'wing_stay_fr'):
        dark_steel(n)
    for n in pair('rail') + ('axle_f', 'axle_r'):
        dark_steel(n)


# ===================================================================================================== body
def paint_body():
    # tub and bonnet: light enamel with the belt seam; tops by world; the underside e1 (lighter than the chassis
    # and the tyres, so the skirt separates)
    for n in ('tub', 'bonnet'):
        for f in S.faces(n, SIDES):
            enamel_face(f)
        enamel_top(F(n, 'up'))
        F(n, 'down').fill('e1')
    # the stern's recessed field (inside its frame, the tub's back face): the darkest enamel, shadow under the top
    f = F('tub', 'south')
    for c, r in f.each():
        x, y, z = f.world(c, r)
        if -8 < x < 8 and 10 < y < 14:
            f.put(c, r, 'r1' if y > 13 else 'e0')
    for n in ('stern_t', 'stern_b', 'stern_l', 'stern_r', 'nose_t', 'nose_b', 'nose_l', 'nose_r',
              'ped_rim_t', 'ped_rim_l', 'ped_rim_r'):
        raised_rim(n)
    F('stern_b', 'up').fill('e3')
    F('nose_b', 'up').fill('e3')
    small('helm_boss', 's', 1, bolt=False)
    # radiator: the grille sunk behind the nose frame, rubber slats
    f = F('grille', 'north')
    for c, r in f.each():
        f.put(c, r, 'r2' if c % 2 == 0 else 'r0')
    for side in ('east', 'west', 'south', 'up', 'down'):
        F('grille', side).fill('r0')
    # bench for two: rubber, a seam between the two seats, a lit front roll; back and a brass rail
    f = F('bench', 'up')
    for c, r in f.each():
        x, y, z = f.world(c, r)
        k = 'r2'
        if z < -4:
            k = 'r3'
        if abs(x) < 0.6:
            k = 'r0'
        f.put(c, r, k)
    for f in S.faces('bench', SIDES):
        for c, r in f.each():
            f.put(c, r, 'r3' if r == 0 else 'r1')
    F('bench', 'down').fill('r0')
    for f in S.faces('bench_back', SIDES):
        for c, r in f.each():
            f.put(c, r, 'r2' if r == 0 else 'r1')
    F('bench_back', 'up').fill('r2')
    F('bench_back', 'down').fill('r0')
    for f in S.faces('bench_rail', SIDES):
        brass_bar(f)
    brass_top(F('bench_rail', 'up'), band=False)
    F('bench_rail', 'down').fill('b1')
    # helm pedestal: enamel; its back plate dark steel with two bolts (where the jar's services go in)
    for f in S.faces('pedestal', SIDES):
        enamel_face(f)
    enamel_top(F('pedestal', 'up'))
    F('pedestal', 'down').fill('e1')
    fill('ped_plate', 's0')
    f = F('ped_plate', 'south')
    rivet(f, 1, 0, 's2', 's0')
    rivet(f, f.W - 3, 0, 's2', 's0')
    # mudguards: tops e3 with an e4 outer edge (the light mass seen from above), sides lip and field, undersides e0
    for n in fpair('fender_f') + pair('fender_r'):
        f = F(n, 'up')
        for c, r in f.each():
            x, y, z = f.world(c, r)
            f.put(c, r, 'e4' if abs(x) > 17 else 'e3')
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, 'e4' if r == 0 else 'e2')
        F(n, 'down').fill('e0')
    for n in fpair('guard_f_front') + fpair('guard_f_back') + pair('guard_r_front') + pair('guard_r_back'):
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, 'e3' if r == 0 else ('e1' if r == f.H - 1 else 'e2'))
        F(n, 'up').fill('e3')
        F(n, 'down').fill('e0')
    # running boards: enamel with a rubber tread mat (three r2 ribs on r1)
    for n in pair('step'):
        f = F(n, 'up')
        for c, r in f.each():
            x, y, z = f.world(c, r)
            k = 'r1' if 11 < abs(x) < 16 else 'e3'
            if 11 < abs(x) < 16 and int(z + 10) % 3 == 0:
                k = 'r2'
            f.put(c, r, k)
        for f in S.faces(n, SIDES):
            f.fill('e2')
        F(n, 'down').fill('e0')
    # the seat plate, collar base and collar (as on the other frames); the helm lamp
    f = F('seat', 'up')
    for c, r in f.each():
        x, y, z = f.world(c, r)
        k = 's1'
        if (abs(x), abs(z - 9.5)) in ((4.5, 4.5), (3.5, 4.5), (4.5, 3.5)):
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
            f.put(c, r, 'b4' if max(abs(x), abs(z - 9.5)) > 8.5 else 'b3')
        F(n, 'down').fill('b1')
    lamp('core_lens', 'core_lit', 'G', 'south')


# ===================================================================================================== binnacle
def paint_binnacle():
    """Calmed: posts' outer faces b3 field, inner faces b2, top b3, the foot row b1; steel knuckle feet dark (s1);
    the ring (v2: dark steel, 1 px) painted like the hinge; the 2 px brass lip a b3 top with b4 only along its outer
    edge; hinge and catch dark steel."""
    for n in pair('post'):
        for f in S.faces(n, SIDES):
            f.fill('b2' if f.side == inner(n) else 'b3')
            f.row(f.H - 1, 'b1')
        F(n, 'up').fill('b3')
        F(n, 'down').fill('b1')
    for n in pair('post_knuckle'):
        small(n, 's', 1, bolt=False)
    for n in ('ring_f', 'ring_b', 'ring_l', 'ring_r'):
        small(n, 's', 1)                                # v2: the ring is a 1 px band of dark steel, like the hinge
    for n in ('lip_f', 'lip_b', 'lip_l', 'lip_r'):
        f = F(n, 'up')
        for c, r in f.each():
            x, y, z = f.world(c, r)
            f.put(c, r, 'b4' if max(abs(x), abs(z - 9.5)) > 7.5 else 'b3')   # the outer edge row lit
        for f in S.faces(n, SIDES):
            f.fill('b3')
        F(n, 'down').fill('b1')
    small('hinge', 's', 1)
    small('catch', 's', 1)


# ===================================================================================================== turret, buffer
def paint_front():
    fill('turret_base', 's0')
    F('turret_base', 'up').fill('s1')
    brass_housing('drum', band=True)
    for n in ('cheek_l', 'cheek_r'):
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, 'e4' if r == 0 else 'e2')
        F(n, 'up').fill('e3')
        F(n, 'down').fill('e1')
    for f in S.faces('cradle', SIDES):
        for c, r in f.each():
            f.put(c, r, 's4' if r == 0 else 's3')
    F('cradle', 'up').fill('s4')
    F('cradle', 'down').fill('s1')
    for f in S.faces('ram', SIDES):
        for c, r in f.each():
            f.put(c, r, 's2' if r == 0 else 's0')
    F('ram', 'up').fill('s2')
    F('ram', 'down').fill('s0')
    for f in S.faces('tool_plate', SIDES):
        for c, r in f.each():
            f.put(c, r, 'b4' if r == 0 else ('b1' if r == f.H - 1 else 'b3'))
    f = F('tool_plate', 'north')
    rivet(f, 0, 1, 'b5', 'b1')
    rivet(f, 2, 1, 'b5', 'b1')
    F('tool_plate', 'up').fill('b4')
    F('tool_plate', 'down').fill('b1')
    # v3: the tool boom's new bracket at the bumper's left end (dark steel, a small part)
    small('tool_bracket', 's', 1)
    # buffer beam: hazard stripes right round it (perimeter 44, divisible by 4); top lit, underside dark
    for f in S.faces('buffer_beam', SIDES):
        stripes_face(f)
    f = F('buffer_beam', 'up')
    for c, r in f.each():
        x, y, z = f.world(c, r)
        f.put(c, r, 'w3' if (int(x + 20) + int(z + 30)) % 4 < 2 else 'r2')
    F('buffer_beam', 'down').fill('r0')
    for n in pair('buffer_plunger'):
        small(n, 's', 3, bolt=False)
    # v2: the rods the beam slides out on (seen moving): polished steel, a lit top row; their dark sleeves
    for n in pair('buffer_rod'):
        for f in S.faces(n, SIDES):
            f.fill('s3')
            f.row(0, 's5')
        F(n, 'up').fill('s5')
        F(n, 'down').fill('s1')
    for n in pair('buffer_sleeve'):
        small(n, 's', 1)
    for n in pair('buffer_head'):
        brass_housing(n, band=False)
        F(n, 'north').put(1, 1, 'b5')                   # the polished striking face's glint


# ===================================================================================================== stern
def paint_stern():
    # ship's wheel: brass rim (b3, lit b4 along each segment's outer edge), b3 spokes, a dark cap with a lit nut,
    # rubber handle pegs
    for n in ('wheel_rim_t', 'wheel_rim_b', 'wheel_rim_l', 'wheel_rim_r', 'wheel_rim_tl', 'wheel_rim_tr',
              'wheel_rim_bl', 'wheel_rim_br'):
        for f in S.faces(n):
            f.fill('b3')
        F(n, 'up').fill('b4')
        F(n, 'down').fill('b1')
        f = F(n, 'south')
        for c, r in f.each():
            x, y, z = f.world(c, r)
            k = 'b3'
            if (y - 14) * (1 if n.endswith(('_t', '_tl', '_tr')) else 0) > 0 and r == 0:
                k = 'b4'
            if n.endswith(('_l', '_tl', '_bl')) and c == 0:
                k = 'b4'
            if n.endswith(('_b', '_br', '_r')) and (r == f.H - 1 or c == f.W - 1):
                k = 'b2'
            f.put(c, r, k)
    for n in ('wheel_spoke_v', 'wheel_spoke_h'):
        fill(n, 'b3')
        F(n, 'up').fill('b4')
        F(n, 'down').fill('b2')
    fill('wheel_cap', 's1')
    F('wheel_cap', 'south').rows(['s1 s2 s1', 's2 s4 s1', 's1 s1 s0'])
    for n in ('grip_t', 'grip_b', 'grip_l', 'grip_r', 'grip_tl', 'grip_tr', 'grip_bl', 'grip_br'):
        small(n, 'r', 1, bolt=False)
        F(n, 'south').fill('r2')
    # exhausts: steel pipes, sooted toward the mouth; striped caps that blow open
    for n in pair('exhaust_pipe'):
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                x, y, z = f.world(c, r)
                k = 's3' if r == 0 else 's2'
                if z > 19:
                    k = 's1' if r == 0 else 's0'        # soot at the mouth
                f.put(c, r, k)
        F(n, 'up').fill('s3')
        F(n, 'down').fill('s1')
    for n in pair('exhaust_cap'):
        for f in S.faces(n, SIDES):
            if f.W >= 4:
                stripes_face(f)
            else:
                f.fill('w1')
        F(n, 'up').fill('w3')
        F(n, 'down').fill('w0')
    # vents on the flanks: rubber mouths with louvres, striped flaps
    for n in pair('vent_mouth'):
        fill(n, 'r0')
        f = F(n, outer(n))
        for c, r in f.each():
            f.put(c, r, 'r2' if r % 2 == 0 else 'r0')
    for n in pair('vent_flap'):
        stripes_face(F(n, outer(n)))
        F(n, inner(n)).fill('s1')
        for side in ('north', 'south'):
            F(n, side).fill('w1')
        F(n, 'up').fill('w3')
        F(n, 'down').fill('w0')


# ===================================================================================================== lamps, flag
def paint_lamps_flag():
    for n in fpair('head_housing'):
        brass_housing(n, band=False)
    for n in fpair('head_hood_t') + fpair('head_hood_b') + fpair('head_hood_i') + fpair('head_hood_o'):
        fill(n, 's1')
        F(n, 'north').fill('s2')
    for n in fpair('head_hood_t'):
        F(n, 'north').fill('s3')
        F(n, 'down').fill('r0')
    for n in fpair('head_hood_b'):
        F(n, 'up').fill('r0')
    for n in fpair('head_hood_i'):
        F(n, outer(n)).fill('r0')
    for n in fpair('head_hood_o'):
        F(n, inner(n)).fill('r0')
    for cn in ('fl', 'fr'):
        lamp('head_lens_' + cn, 'head_lit_' + cn, 'A', 'north')
    for n in pair('tail_housing'):
        brass_housing(n, band=False)
    for n in pair('tail_rim_t') + pair('tail_rim_b') + pair('tail_rim_i') + pair('tail_rim_o'):
        fill(n, 's1')
        F(n, 'south').fill('s2')
    for n in pair('tail_rim_t'):
        F(n, 'south').fill('s3')
        F(n, 'down').fill('r0')
    for n in pair('tail_rim_b'):
        F(n, 'up').fill('r0')
    for n in pair('tail_rim_i'):
        F(n, outer(n)).fill('r0')
    for n in pair('tail_rim_o'):
        F(n, inner(n)).fill('r0')
    for s in ('l', 'r'):
        lamp('tail_lens_' + s, 'tail_lit_' + s, 'A', 'south')
        lamp('brake_lens_' + s, 'brake_lit_' + s, 'R', 'south')
    # the RIGHT OF WAY flag: a 6x6 plate cannot hold lettering, so it carries the device that means right of way, the
    # priority-road diamond (a yellow diamond in a white border on black), on both faces; its 1 px edges carry the
    # hazard dashes; the stalk is plain steel
    small('flag_stalk', 's', 3, bolt=False)
    sign = ['r1 r1 e5 e5 r1 r1',
            'r1 e5 w3 w3 e5 r1',
            'e5 w3 w2 w2 w2 e5',
            'e5 w2 w2 w2 w1 e5',
            'r1 e5 w2 w1 e5 r1',
            'r1 r1 e5 e5 r1 r1']
    for side in ('north', 'south'):
        F('flag_plate', side).rows(sign)
    for side in ('east', 'west', 'up', 'down'):
        f = F('flag_plate', side)
        for c, r in f.each():
            idx = c + r
            f.put(c, r, 'w2' if idx % 4 < 2 else 'r1')


def paint_all():
    paint_wheels()
    paint_body()
    paint_binnacle()
    paint_front()
    paint_stern()
    paint_lamps_flag()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview', help='also write DIR/wheelhouse_preview.png with the stand-ins filled')
    ap.add_argument('--out', default=OUT)
    a = ap.parse_args()
    paint_all()
    miss = S.missing()
    if miss:
        sys.stderr.write('unpainted faces: %s\n' % miss)
        return 1
    os.makedirs(a.out, exist_ok=True)
    S.img.save(os.path.join(a.out, 'wheelhouse.png'))
    S.glow.save(os.path.join(a.out, 'wheelhouse_glowmask.png'))
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
        pv.save(os.path.join(a.preview, 'wheelhouse_preview.png'))
    print('wrote wheelhouse.png and wheelhouse_glowmask.png to %s' % a.out)
    return 0


if __name__ == '__main__':
    sys.exit(main())

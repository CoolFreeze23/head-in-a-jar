"""The Hopper (Noggin & Sons entry-level field vehicle): the painted sheet, every pixel placed from palette keys.

Source of `textures/entity/mecha/hopper.png` and `hopper_glowmask.png` (adopted as `painted` in the texture manifest).
No randomness: each part is painted by a named construction (the painter's rules for recesses and value masses).

    python -I art/mecha/hopper/paint/paint_hopper.py                # writes the two PNGs into the mod assets
    python -I art/mecha/hopper/paint/paint_hopper.py --preview DIR  # also DIR/hopper_preview.png, stand-ins filled
                                                                    # from the placeholder sheet (renders only)
Reads `art/mecha/hopper/hopper.bbmodel` (v5, the recess pass: cube sizes, positions, box-UV offsets; read only) and
`art/palette.json`.

The value plan (pass 5, final; model v6). Two big masses carry the read at twenty blocks: a LIGHT enamel pod
(fields e2, lit edges e3-e4) on DARK running gear: side rails, beams, tow bracket, arch ceilings, keel and waist in
the darkest steel steps (s0-s1). Legs and upper arms are mid-dark steel (s1-s2). Brass keeps its light field (b3),
with b4 on its lit edges and b5 only as glints, and its darks only round it (b1 undersides, b0 bottom corners, the r0
ring under the collar): a darkened brass field reads as copper or varnished wood. Every
recessed field (back, flanks, vent mouths, lamp hoods and sinks, sensor rim walls) takes the darkest step of its
material; a raised rim is lighter than the field it frames.
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from paintlib import COL, ROOT, SIDES, Sheet  # noqa: E402

BBMODEL = os.path.join(ROOT, 'art', 'mecha', 'hopper', 'hopper.bbmodel')
PLACEHOLDER = os.path.join(ROOT, 'art', 'mecha', 'hopper', 'hopper_placeholder.png')
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
    """T18: a lit head and its shadow below-right."""
    f.put(c, r, lit)
    f.put(c + 1, r + 1, dark)


def recess(cube, key='r0'):
    """A part that exists to be a hole or a gap (arch ceiling, collar ring, waist, axle, boss): every face the
    darkest step, so it reads as shadow from any side."""
    fill(cube, key)


def small(cube, m, field, bolt=True, sides=SIDES):
    """Small parts: each side face ONE field tone (never a lit half and a dark half); top face field + 1, bottom face
    field - 1; one accent bolt (field + 2 head, field - 1 shadow) at (0, 1) on side faces 2+ wide and 3+ tall."""
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


def stripes(cube, sides=SIDES, top=0, bottom=0, phase=0):
    """Warning stripes: 45 degrees, 2 px yellow (w2) and 2 px black (r1), period 4 along the side strip (wraps the
    corners; every striped part has a perimeter divisible by 4). Only on faces 4+ px wide. `top`/`bottom` rows are a
    steel recess frame (s3 lit lip above, s0 shadow below); the band's first row a step lighter (w3 / r2)."""
    for f in S.faces(cube, sides):
        for c, r in f.each():
            if r < top:
                k = 's3'
            elif r >= f.H - bottom:
                k = 's0'
            else:
                y = (f.strip0 + c + r + phase) % 4 < 2
                k = ('w3' if y else 'r2') if r == top else ('w2' if y else 'r1')
            f.put(c, r, k)


def polish(f, c0, rows, key='b3', glint='b4'):
    """The brass polish band: 2 px wide, 45 degrees, rising to the right from column c0 on the bottom row of `rows`;
    its top pixel the glint. Once per face, on faces 5+ px wide, in the left half."""
    r0, r1 = rows
    for i, r in enumerate(range(r1 - 1, r0 - 1, -1)):
        f.put(c0 + i, r, key)
        f.put(c0 + i + 1, r, key)
    f.put(c0 + (r1 - r0), r0, glint)


def brass_bar(f, grip=None):
    """A brass bar seen from the side (2 px tall in this model): the top row the lit edge (b4; b5 along the grip), the
    row under it the field (b3; b4 on the grip). End columns one step darker (they turn away). With 3+ rows, the last
    row b1 (the underside)."""
    for c, r in f.each():
        end = c in (0, f.W - 1)
        g = bool(grip) and grip[0] <= c < grip[1]
        if r == 0:
            k = 'b3' if end else ('b5' if g else 'b4')
        elif r == f.H - 1 and f.H >= 3:
            k = 'b0' if end else 'b1'
        else:
            k = 'b2' if end else ('b4' if g else 'b3')
        f.put(c, r, k)


def brass_housing(cube, outer=None):
    """A cast brass housing (shoulder, knee, hip drum): the top row a b4 lit edge (b3 at the corners), a b3 field,
    a 1 px b1 edge down both sides, a b0 bottom row; one polish band (b4, b5 glint) on faces 5+ wide and 4+ tall; on
    the outer face a steel pivot boss (s4 s2 / s2 s0) instead. Top face: one b3 field with a b4 edge along its north
    and west sides, nothing else."""
    for f in S.faces(cube, SIDES):
        for c, r in f.each():
            end = c in (0, f.W - 1)
            if r == 0:
                k = 'b3' if end else 'b4'
            elif r == f.H - 1:
                k = 'b0'
            else:
                k = 'b1' if end else 'b3'
            f.put(c, r, k)
        if f.side != outer and f.W >= 5 and f.H >= 4:
            polish(f, 1, (1, f.H - 1), key='b4', glint='b5')
    if outer:
        o = F(cube, outer)
        o.rows(['s4 s2', 's2 s0'], c0=(o.W - 2) // 2, r0=max(1, (o.H - 2) // 2))
    fu = F(cube, 'up')
    x0, _, z0 = S.cubes[cube]['frm']
    for c, r in fu.each():
        x, y, z = fu.world(c, r)
        fu.put(c, r, 'b4' if (z < z0 + 1 or x < x0 + 1) else 'b3')
    F(cube, 'down').fill('b0')


def steel_bar(f, field='s2', lit='s3', dark='s1', streaks=(), foot=0, arris=True):
    """Brushed steel limb face: one field tone, the top row lit, a lit arris column on the light-facing faces only
    (north, west), streaks (c, r0, r1, key) 3-5 px along the length, the last `foot` rows one step darker with one
    placed step, the last row darker again."""
    f.fill(field)
    f.row(0, lit)
    if arris and f.side in ('north', 'west') and f.W >= 4:
        f.col(0, lit)
    for c, r0, r1, k in streaks:
        for r in range(r0, r1):
            f.put(c, r, k)
    if foot:
        for c in range(f.W):
            for r in range(f.H - foot - (1 if c == 1 else 0), f.H):
                f.put(c, r, dark)
        f.row(f.H - 1, 's0')
    else:
        f.row(f.H - 1, dark)


# ===================================================================================================== the pod
def enamel_panel(f, lip='e4', field='e2', foot='e1', rim=True):
    """A pressed enamel panel: row 0 the lit lip (e4; e3 at the two corners so the edge reads rounded), a flat e2
    field, the bottom row e1 (turning under into the shadow); with `rim`, the field's two side columns e1 (a 1 px frame
    against the corner)."""
    for c, r in f.each():
        end = c in (0, f.W - 1)
        if r == 0:
            k = 'e3' if end else lip
        elif r == f.H - 1:
            k = foot
        else:
            k = 'e1' if (rim and end) else field
        f.put(c, r, k)


def paint_pod():
    # ---- the hull (deck box): flanks are 16x6 panels; its front and back show only a 1 px lip above the slabs and the
    # two outer columns beside them
    for side in ('east', 'west'):
        enamel_panel(F('hull', side))
    for side in ('north', 'south'):
        f = F('hull', side)
        for c, r in f.each():
            f.put(c, r, 'e4' if r == 0 else 'e2')
    # flank story: a steel hinge strip above each vent flap (knuckles lit at its ends), a chip at the lower front
    # corner, one rivet ahead of the vent
    for n_side in ('east', 'west'):
        f = F('hull', n_side)
        cols = [c for c in range(f.W) if 1 <= f.world(c, 0)[2] <= 7]
        for c in cols:
            f.put(c, 0, 's2')
        f.put(cols[0], 0, 's3')
        f.put(cols[-1], 0, 's3')
        # the recessed field framed by the flank rims (z -6..-2, y 21..25): the darkest enamel, its top row in the
        # rim's shadow
        for c, r in f.each():
            x, y, z = f.world(c, r)
            if -6 < z < -2 and 21 < y < 25:
                f.put(c, r, 'r1' if y > 24 else 'e0')
    for side_l in ('l', 'r'):
        for part in ('t', 'b', 'f', 'k'):
            n = 'flank_rim_%s_%s' % (part, side_l)
            fill(n, 'e3')
            F(n, 'up').fill('e4')
            F(n, 'down').fill('e1')
            F(n, 'west' if side_l == 'l' else 'east').fill('e4' if part == 't' else 'e3')
        # one story mark: a steel chip worn into the front rim's lower corner, where a boot would kick it
        f = F('flank_rim_f_' + side_l, 'west' if side_l == 'l' else 'east')
        f.put(0 if side_l == 'l' else f.W - 1, f.H - 1, 's2')

    # deck: only a ring shows outside the collar (1.5 px) and a well inside it; paint by world position
    f = F('hull', 'up')
    for c, r in f.each():
        x, y, z = f.world(c, r)
        m = max(abs(x), abs(z))
        if m > 9.5:
            k = 'e4'                                    # the deck's rolled outer edge
        elif m > 8.5:
            k = 'e3'
        elif m > 6:
            k = 'r0'                                    # the well round the seat plate
        else:
            k = 's0'
        f.put(c, r, k)
    F('hull', 'down').fill('e0')                        # fender undersides beyond the arches

    # ---- the slabs (front and back faces, fenders over the hips)
    for n in ('slab_f', 'slab_b'):
        for side in ('east', 'west'):
            enamel_panel(F(n, side), rim=False)
        F(n, 'up').fill('e3')                           # the step below the deck, lit
        F(n, 'down').fill('e0')                         # fender underside: in shadow
    F('slab_f', 'south').fill('e0')                     # the hidden inner faces (against the hull and skirt)
    F('slab_b', 'north').fill('e0')
    fr = F('slab_f', 'north')
    enamel_panel(fr)
    for c0 in (1, 11):                                  # louvred intakes: dark slots under lit louvres
        fr.rows(['e0 e0 e0 e0', 'e4 e4 e4 e3', 'e0 e0 e0 e0', 'e4 e4 e4 e3'], c0=c0, r0=1)
    # back: the slab's own face is the recessed field inside the lamp bar and the rims: darker than any lit enamel,
    # its top row (under the lamp bar) and left column in shadow
    bk = F('slab_b', 'south')
    for c, r in bk.each():
        x, y, z = bk.world(c, r)
        k = 'e0'                                        # the recessed field: the darkest enamel
        if y > 22:
            k = 'r1'                                    # shadow under the lamp bar
        if -6 < x < -5:
            k = 'r1'                                    # shadow down the left rim's wall
        bk.put(c, r, k)
    # ---- the skirt between the thighs, the arch ceilings, the keel waist: shadow
    for side in ('east', 'west'):
        f = F('skirt', side)
        for c, r in f.each():
            f.put(c, r, 'e1' if r < 1 else 'e0')
    for side in ('north', 'south'):
        F('skirt', side).fill('e1')
    F('skirt', 'down').fill('e0')
    F('skirt', 'up').fill('e1')
    recess('arch_l', 's0')
    recess('arch_r', 's0')
    recess('keel_neck', 'r0')
    # keel: the darkest steel, a lit top edge, the maker's plate on its back face (seen from behind, between the
    # thighs)
    for f in S.faces('keel', SIDES):
        for c, r in f.each():
            f.put(c, r, 's1' if r == 0 else 's0')
    F('keel', 'down').fill('s0')
    F('keel', 'up').fill('s1')
    # ---- the running gear: side rails, front and back beams, tow bracket. The dark mass under the light pod: top
    # faces s1 with one s2 lit edge along the outside, side faces s0, undersides r0
    for n in ('rail_side_l', 'rail_side_r', 'beam_f', 'beam_b'):
        for f in S.faces(n, SIDES):
            f.fill('s0')
            if f.H >= 2:
                f.row(0, 's1')
        F(n, 'down').fill('r0')
        fu = F(n, 'up')
        x0, _, z0 = S.cubes[n]['frm']
        x1, z1 = x0 + S.cubes[n]['w'], z0 + S.cubes[n]['d']
        for c, r in fu.each():
            x, y, z = fu.world(c, r)
            edge = (n == 'rail_side_l' and x < x0 + 1) or (n == 'rail_side_r' and x > x1 - 1) or \
                   (n == 'beam_f' and z < z0 + 1) or (n == 'beam_b' and z > z1 - 1)
            fu.put(c, r, 's2' if edge else 's1')
    for f in S.faces('tow_bracket', SIDES):
        for c, r in f.each():
            f.put(c, r, 's1' if r == 0 else 's0')
    F('tow_bracket', 'south').rows(['s1 s1 s1 s1 s1 s1',
                                    's0 s2 s0 s0 s2 s0',
                                    's0 s0 r0 r0 s0 s0',
                                    's0 s0 r0 r0 s0 s0'])       # two bolts and the socket's bore
    F('tow_bracket', 'up').fill('s1')
    F('tow_bracket', 'down').fill('r0')
    F('keel', 'south').rows(['s2 b4 b4 b4 b4 b4 b3 s2',
                             's1 b3 b1 b2 b1 b2 b1 s1'])

    # ---- the back's raised frame: lamp bar, rims, plate. Raised = lighter than the field it frames
    for n in ('lampbar_l', 'lampbar_r'):
        f = F(n, 'south')
        for c, r in f.each():
            f.put(c, r, 'e4' if r == 0 else 'e3')
        F(n, 'up').fill('e3')
        F(n, 'down').fill('e2')
        for side in ('east', 'west'):
            F(n, side).fill('e2')
        F(n, 'north').fill('e1')
    # the walls of the two lamp sinks (the gaps in the bar) are dark: the lamps sit 1 px back in a hood
    F('lampbar_l', 'east').fill('r0')
    F('number_plate', 'west').fill('r0')
    F('number_plate', 'east').fill('r0')
    F('lampbar_r', 'west').fill('r0')
    for n in ('rim_bl', 'rim_br'):
        f = F(n, 'south')
        for c, r in f.each():
            f.put(c, r, 'e4' if c == 0 else 'e3')
        F(n, 'up').fill('e3')
        F(n, 'down').fill('e1')
        F(n, 'north').fill('e1')
    F('rim_bl', 'east').fill('e2')                      # the left rim's inner wall faces the light: lit
    F('rim_bl', 'west').fill('e2')
    F('rim_br', 'west').fill('e0')                      # the right rim's inner wall: shadow
    F('rim_br', 'east').fill('e1')
    F('rim_bb', 'south').fill('e3')
    F('rim_bb', 'up').fill('e4')                        # the sill catches the light
    F('rim_bb', 'down').fill('e1')
    for side in ('east', 'west', 'north'):
        F('rim_bb', side).fill('e2')
    # the brass number plate between the tail lamps: lit top edge, b3 field, two rivets, an engraved line (the fleet
    # number's place; at 8x2 no digit reads)
    F('number_plate', 'south').rows(['b4 b5 b4 b4 b4 b4 b4 b3',
                                     'b3 b5 b1 b2 b1 b1 b5 b2'])
    F('number_plate', 'north').fill('b1')
    F('number_plate', 'down').fill('b0')
    fu = F('number_plate', 'up')
    for c, r in fu.each():
        x, y, z = fu.world(c, r)
        fu.put(c, r, 'b4' if z > 11 else 'b3')
    # the hatch in the recessed field: raised, so lighter than the field, its lit top edge; a brass handle
    f = F('hatch', 'south')
    for c, r in f.each():
        f.put(c, r, 'e3' if r == 0 else 'e2')
    f.put(0, 0, 'e4')
    F('hatch', 'up').fill('e3')
    F('hatch', 'down').fill('e0')
    for side in ('east', 'west', 'north'):
        F('hatch', side).fill('e1')
    small('hatch_handle', 'b', 3, bolt=False)

    # ---- seat plate: steel deck with corner marks where the jar stands
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


# ===================================================================================================== collar, hoop
def paint_collar():
    for n in ('collar_base_f', 'collar_base_b', 'collar_base_l', 'collar_base_r'):
        recess(n, 'r0')                                 # the shadow line right round under the collar
    for n in ('collar_f', 'collar_b', 'collar_l', 'collar_r'):
        for f in S.faces(n, SIDES):
            brass_bar(f)
        f = F(n, 'up')
        for c, r in f.each():
            x, y, z = f.world(c, r)
            f.put(c, r, 'b4' if max(abs(x), abs(z)) > 8.5 else 'b3')   # the outer edge carries the light
        F(n, 'down').fill('b0')
    for f in S.faces('filler_cap', SIDES):
        f.fill('b3')
    F('filler_cap', 'up').rows(['b5 b4', 'b4 b1'])      # a cap with its vent hole
    F('filler_cap', 'down').fill('b0')
    for n in both('bracket'):
        small(n, 's', 2)
    for n in both('knuckle'):
        for f in S.faces(n, ('north', 'south')):
            for c, r in f.each():
                f.put(c, r, ('s3', 's2', 's0')[r])
        F(n, 'up').fill('s2')
        F(n, 'down').fill('s0')
        for side in ('east', 'west'):
            F(n, side).rows(['s2 s2 s1', 's2 s4 s1', 's1 s1 s0'])     # the hinge pin's head

    # the hoop: uprights, rails over the lid, the crossbar with its hand-polished grip, hooks, clamp arms, pads
    for n in both('upright'):
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                k = 'b3' if r < f.H - 1 else 'b1'
                if r >= f.H - 3 and r < f.H - 1:
                    k = 'b2'                            # darker toward the foot
                if r == 0:
                    k = 'b4'
                if f.side in ('north', 'west') and c == 0 and 0 < r < f.H - 3:
                    k = 'b4'                            # the edge light down the lit side
                f.put(c, r, k)
        F(n, 'up').fill('b4')
        F(n, 'down').fill('b0')
    for n in both('rail'):
        for f in S.faces(n, ('east', 'west')):
            brass_bar(f)
        for f in S.faces(n, ('north', 'south')):
            f.fill('b2')
        f = F(n, 'up')
        for c, r in f.each():
            x, y, z = f.world(c, r)
            f.put(c, r, 'b4' if abs(x) > 8 else 'b3')   # the outer edge carries the light
        F(n, 'down').fill('b0')
    for f in S.faces('crossbar', ('north', 'south')):
        brass_bar(f, grip=(6, 13))
    for f in S.faces('crossbar', ('east', 'west')):
        f.fill('b2')
    f = F('crossbar', 'up')
    for c, r in f.each():
        x, y, z = f.world(c, r)
        g = abs(x) < 3.5
        f.put(c, r, ('b5' if g else 'b4') if z < -8.5 else ('b4' if g else 'b3'))
    F('crossbar', 'down').fill('b0')
    # the back bar: closes the hoop; the outer (back) edge carries the light
    for f in S.faces('backbar', ('north', 'south')):
        brass_bar(f)
    for f in S.faces('backbar', ('east', 'west')):
        f.fill('b2')
    f = F('backbar', 'up')
    for c, r in f.each():
        x, y, z = f.world(c, r)
        f.put(c, r, 'b4' if z > 8 else 'b3')
    F('backbar', 'down').fill('b0')
    for n in both('hook'):
        small(n, 'b', 3, bolt=False)
    for n in both('clamp_arm'):
        small(n, 's', 2, bolt=False)
    for n in both('pad'):
        for f in S.faces(n):
            for c, r in f.each():
                f.put(c, r, ('r3', 'r2', 'r1')[min(r, 2)] if f.H == 3 else 'r2')
        f = F(n, inner_side(n))                         # the face that grips the glass: ribs
        for c, r in f.each():
            f.put(c, r, 'r3' if c % 2 == 0 else 'r1')


# ===================================================================================================== vents, lamps
def paint_vents():
    for n in both('grille'):
        fill(n, 'r0')
        f = F(n, outer_side(n))                         # the vent mouth: louvres inside, mostly behind the flap
        for c, r in f.each():
            f.put(c, r, 'r2' if r % 2 == 0 else 'r0')
    for n in both('vent_flap'):
        f = F(n, outer_side(n))                         # the flap face between and round its two louvres
        for c, r in f.each():
            f.put(c, r, ('w3', 'w1', 'r0', 'w1', 'w1')[r])   # r0: the groove between the louvres
        F(n, inner_side(n)).fill('s1')
        for side in ('north', 'south'):
            f = F(n, side)
            for c, r in f.each():
                f.put(c, r, 'w2' if r == 0 else 'w1')
        F(n, 'up').fill('w3')
        F(n, 'down').fill('w0')
    for i, base in enumerate(('louvre_1', 'louvre_2')):
        for n in both(base):
            # each louvre is a 1 px slat: hazard dashes along it, 2 yellow 2 black, offset by one between the two slats
            # so together they step at 45 degrees
            for side in (outer_side(n), 'up', 'down'):
                f = F(n, side)
                for c, r in f.each():
                    x, y, z = f.world(c, r)
                    yellow = (int(z - 1) + i) % 4 < 2
                    if side == 'up':
                        k = 'w3' if yellow else 'r2'
                    elif side == 'down':
                        k = 'w1' if yellow else 'r0'
                    else:
                        k = 'w2' if yellow else 'r1'
                    f.put(c, r, k)
            for side in ('north', 'south', inner_side(n)):
                F(n, side).fill('w1')


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
        if f.W >= 3 and (c in (0, f.W - 1) or r in (0, f.H - 1)):
            k = m + '1'
        if (c, r) == ((1, 1) if f.W >= 3 else (0, 0)):
            k = m + '3'                                 # the pale core, top left
        f.put(c, r, k, lit=True)


def paint_lamps_badge():
    f = F('badge', 'north')                             # brass bezel round the hood
    for c, r in f.each():
        k = 'b3'
        if r == 0:
            k = 'b4'
        elif r == f.H - 1:
            k = 'b0'
        elif c == 0:
            k = 'b3'
        elif c == f.W - 1:
            k = 'b1'
        f.put(c, r, k)
    for side in ('east', 'west', 'south'):
        F('badge', side).fill('b2')
    F('badge', 'up').fill('b4')
    F('badge', 'down').fill('b0')
    # the hood: dark steel outside, black inside, a lit lip along its top front edge
    for n in ('hood_t', 'hood_b', 'hood_ls', 'hood_rs'):
        fill(n, 's1')
        F(n, 'north').fill('s2')
    F('hood_t', 'north').fill('s3')
    F('hood_t', 'up').fill('s2')
    F('hood_t', 'down').fill('r0')
    F('hood_b', 'up').fill('r0')
    F('hood_ls', 'east').fill('r0')
    F('hood_rs', 'west').fill('r0')
    lamp('lens_core', 'lamp_core_lit', 'G', 'north')
    lamp('lens_l', 'lamp_l_lit', 'A', 'south')
    lamp('lens_r', 'lamp_r_lit', 'A', 'south')
    lamp('sensor_lens', 'sensor_lens_lit', 'P', 'north')


# ===================================================================================================== sensor
def paint_sensor():
    small('sensor_neck', 'b', 3, bolt=False)
    h = 'sensor_head'
    for f in S.faces(h, ('east', 'west', 'south')):
        enamel_panel(f, rim=False)
    F(h, 'north').fill('r0')
    F(h, 'up').fill('e3')
    F(h, 'down').fill('e0')
    # the rim: raised, lighter than the head; its inner walls (toward the sunk lens) black
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


# ===================================================================================================== arms
def paint_arms():
    for n in both('shoulder_boss'):
        recess(n, 's0')
    for n in both('shoulder'):
        brass_housing(n, outer_side(n))
    for n in both('upper_arm'):
        for f in S.faces(n, SIDES):
            steel_bar(f, streaks=((2, 1, 3, 's3'),))
        F(n, 'up').fill('s3')
        F(n, 'down').fill('s0')
    # forearm: carried level (rest x 90): local north is the top, local -z is "up". Enamel, one flat field, a lit top,
    # an e0 seam round it at mid length, a dark underside
    for n in both('forearm'):
        for f in S.faces(n):
            for c, r in f.each():
                x, y, z = f.world(c, r)
                k = 'e1'                                 # secondary enamel: a step under the pod
                if f.side in ('east', 'west'):
                    if z < -4:
                        k = 'e3'
                    elif z > 0:
                        k = 'e0'
                    if abs(y - 17.5) < 0.6:
                        k = 'e0'
                elif f.side == 'north':
                    k = 'e2' if abs(y - 17.5) > 0.6 else 'e0'
                elif f.side == 'south':
                    k = 'e0'
                else:
                    k = 'e1'
                f.put(c, r, k)
            if f.side == 'north':
                f.put(0, 0, 'e4')
    # cuff: rubber boot; its forward face (local down) a steel plate with one bore
    for n in both('cuff'):
        fill(n, 'r1')
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, 'r2' if r == 0 else 'r1')
        F(n, 'down').rows(['s3 s3 s3 s3 s3 s2',
                           's3 s2 s2 s2 s2 s1',
                           's3 s2 r0 r0 s2 s1',
                           's3 s2 r0 r0 s2 s1',
                           's2 s2 s2 s2 s2 s1',
                           's1 s1 s1 s1 s1 s0'])
        F(n, 'up').fill('r0')


# ===================================================================================================== legs
def paint_legs():
    for n in both('hip_joint'):
        brass_housing(n, outer_side(n))                 # the drum seen in the dark arch
    for n in both('thigh'):
        for f in S.faces(n, SIDES):
            steel_bar(f, streaks=((2, 1, 5, 's3'), (3, 5, 8, 's1')))
            if f.side == outer_side(n):
                rivet(f, 1, 6, 's3', 's0')
        F(n, 'up').fill('s2')
        F(n, 'down').fill('s0')
    for n in both('knee_axle'):
        recess(n, 's0')
    for n in both('knee'):
        brass_housing(n, outer_side(n))                 # the knee pin on the outer face
    for n in both('shin'):
        for f in S.faces(n, SIDES):
            steel_bar(f, field='s1', lit='s2', dark='s0', streaks=((1, 1, 5, 's2'),), foot=2)
        F(n, 'up').fill('s1')
        F(n, 'down').fill('s0')
    for n in both('ankle'):
        stripes(n)                                      # the crush zone: a hazard band round the ankle (5 px faces)
        F(n, 'up').fill('s2')
        F(n, 'down').fill('s0')
    for n in both('heel'):
        small(n, 's', 1)
    for n in both('sole'):
        cx = -7 if n.endswith('_l') else 7
        f = F(n, 'up')
        for c, r in f.each():
            x, y, z = f.world(c, r)
            k = 'r1'
            if z < -5:
                k = 's2' if z < -6 else 's0'            # steel toe cap, its seam behind it
                if z < -6 and abs(x - cx) < 2:
                    k = 's4'                            # scuffed bright where it strikes
            elif abs(x - cx) > 2.5 or z > 2:
                k = 'r2'                                # the lit rim of the tread
            f.put(c, r, k)
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                x, y, z = f.world(c, r)
                if z < -5:
                    k = 's2' if r == 0 else 's1'
                else:
                    k = 'r2' if r == 0 else 'r0'
                f.put(c, r, k)
        F(n, 'down').fill('r0')


def paint_all():
    paint_pod()
    paint_collar()
    paint_vents()
    paint_lamps_badge()
    paint_sensor()
    paint_arms()
    paint_legs()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview', help='also write DIR/hopper_preview.png with the stand-ins filled')
    ap.add_argument('--out', default=OUT)
    a = ap.parse_args()
    paint_all()
    miss = S.missing()
    if miss:
        sys.stderr.write('unpainted faces: %s\n' % miss)
        return 1
    os.makedirs(a.out, exist_ok=True)
    S.img.save(os.path.join(a.out, 'hopper.png'))
    S.glow.save(os.path.join(a.out, 'hopper_glowmask.png'))
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
        pv.save(os.path.join(a.preview, 'hopper_preview.png'))
    print('wrote hopper.png and hopper_glowmask.png to %s' % a.out)
    return 0


if __name__ == '__main__':
    sys.exit(main())

"""Galvanic Pile (the Volta Press): the painted sheet, every pixel placed from palette keys by the kit's constructions.

    python -I art/machines/galvanic_pile/paint/paint_pile.py                # writes the textures into the mod assets
    python -I art/machines/galvanic_pile/paint/paint_pile.py --preview DIR  # also DIR/pile_rest.png, pile_work.png:
                                                                           # stand-ins filled, lens shown ready / work
Then: python -I tools/tex/tex.py adopt block/machine/galvanic_pile.png block/machine/galvanic_pile_felt.png
      block/machine/galvanic_pile_felt.png.mcmeta

Writes block/machine/galvanic_pile.png (128x128: the baked model and the GeckoLib bones share it) and
block/machine/galvanic_pile_felt.png (+ .mcmeta), the felt grooves' glisten for felt_1 and felt_2's side faces.

The value plan (planned on a 110 px render first):
  LIGHT  the brass frame: four rods, the press plate, the T-bar (b3 fields, b4 lit rows, darks only round them)
  DARK   the plinth, the base plate (s1-s2), the pan's rubber floor (r0) under clear glass, the felt grooves (r0), grips
  ACCENT the column: copper (c3 field, c6 lit row) and zinc (s4 field, s5 lit row) discs in alternation; the steel
         yoke a mid tone (s2 field, s3 top) between the brass and the dark base
Story marks, once each: the scorch between the terminals on the press plate; the spark-bright terminal tops; the
maker's plate behind the pan; the nuts on the yoke over each rod.
"""
import argparse
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'kit', 'paint'))
import kitpaint as K  # noqa: E402

MDIR = os.path.join(HERE, '..')
BBMODEL = os.path.join(MDIR, 'galvanic_pile.bbmodel')
PLACEHOLDER = os.path.join(MDIR, 'galvanic_pile_placeholder.png')
SHEET = os.path.join(K.OUT, 'galvanic_pile.png')
FELT = os.path.join(K.OUT, 'galvanic_pile_felt.png')

RODS = [(-7, -7, -5, -5), (5, -7, 7, -5), (-7, 5, -5, 7), (5, 5, 7, 7)]
SCREW = [(-1, -1, 1, 1)]


def disc(S, n, metal):
    """A pile disc 8x2x8. Copper: sides c6 lit row over a c3 field, top c5, underside c1 (c2 read as chocolate on the shaded side). Zinc (pale, matte): sides s5
    over s4, top s4, underside s2."""
    lit, field, top, dn = {'cu': ('c6', 'c3', 'c5', 'c1'), 'zn': ('s5', 's4', 's4', 's2')}[metal]
    for f in S.faces(n, K.SIDES):
        f.fill(field)
        f.row(0, lit)
    S.face(n, 'up').fill(top)
    S.face(n, 'down').fill(dn)


def felt(S, n, glint):
    """A felt groove 6x1x6 set 1 px into the column: the recess's darkest step r0; one r2 bead of electrolyte on the
    front face (on the sheet; the baked felts glisten through the animated texture)."""
    K.recess(S, n, 'r0')
    if glint is not None:
        S.face(n, 'north').put(glint, 0, 'r2')


def paint(S):
    # ---- base: the dark mass
    K.plinth(S)
    K.feet(S)
    # ---- the pan: clear glass (rim row and corners only) round a rubber floor (the strong dark seen through it), steel posts
    for n in ('pan_front', 'pan_back', 'pan_left', 'pan_right'):
        for f in S.faces(n, K.SIDES):
            if f.W >= 4:
                K.glass_window(f)       # top row and corners only: the meat must show (game, second pass)
            else:
                f.fill('g2')
        S.face(n, 'up').fill('g3')
        S.face(n, 'down').fill('g1')
    K.fill(S, 'pan_floor', 'r1')
    S.face('pan_floor', 'up').fill('r0')
    for n in S.names('pan_post_'):
        K.small(S, n, 's', 2)
    # ---- feed ports both sides, the maker's plate behind the pan
    K.side_port(S, 'port_l', 'west')
    K.side_port(S, 'port_r', 'east')
    K.makers_plate(S, 'makers_plate', 'south')
    # ---- the base plate: running gear dark; bushings where the rods stand, and the stack's shadow on it
    K.dark_steel(S, 'base_plate')
    up = S.face('base_plate', 'up')
    K.bushing(up, RODS, 's1')
    # ---- the brass frame: four rods
    for n in S.names('rod_'):
        K.brass_post(S, n)
    # ---- the yoke: mid steel, brushed along its length, a nut over each rod, a bushing round the screw
    for n in ('yoke_l', 'yoke_r', 'yoke_c'):
        K.steel_part(S, n, field='s2', lit='s3', under='s1')
        S.face(n, 'up').fill('s3')
    for n, streak in (('yoke_l', (1, 3, 5)), ('yoke_r', (2, 9, 4))):
        f = S.face(n, 'up')
        c, r0, ln = streak
        for i in range(ln):
            f.put(c, r0 + i, 's4' if i == 0 else 's2')
    for n in ('yoke_l', 'yoke_r'):
        f = S.face(n, 'up')
        for (x0, z0, x1, z1) in RODS:
            for cc, rr in f.each():
                x, y, z = f.world(cc, rr)
                if x0 < x < x1 and z0 < z < z1:
                    # a 2x2 nut head: lit corner top-left as seen, shadow bottom-right
                    lx = x > (x0 + x1) / 2.0      # the face's left column is +x
                    tz = z < (z0 + z1) / 2.0      # the face's top row is -z
                    f.put(cc, rr, 's5' if (lx and tz) else ('s1' if not (lx or tz) else 's3'))
    K.bushing(S.face('yoke_c', 'up'), SCREW, 's1')
    # ---- the lamp on the front-right rod
    K.lamp_bezel(S)
    K.lens_on_sheet(S, 'lamp_lens', 'ready')
    # ---- the column: copper, felt, zinc, felt | copper, felt, zinc (the accent)
    disc(S, 'disc_cu_1', 'cu')
    disc(S, 'disc_zn_1', 'zn')
    disc(S, 'disc_cu_2', 'cu')
    disc(S, 'disc_zn_2', 'zn')
    felt(S, 'felt_1', None)
    felt(S, 'felt_2', None)
    felt(S, 'felt_3', 2)
    # ---- the press: brass plate with bushings for the rods and the screw, the scorch between the terminals
    K.brass_plate(S, 'press_plate')
    pu = S.face('press_plate', 'up')
    K.bushing(pu, RODS + SCREW, 's1')
    for cc, rr in pu.each():
        x, y, z = pu.world(cc, rr)
        if -1 < x < 1 and -6 < z < -4:
            pu.put(cc, rr, 'b1' if (x < 0) == (z < -5) else 'b2')    # the scorch: two b1 in a 2x2 of b2
    for n in ('term_l', 'term_r'):
        K.small(S, n, 'c', 4, top='c7', bottom='c2')
    # ---- the handle: a threaded steel screw, the brass T-bar, rubber grips with one rib
    for f in S.faces('screw', K.SIDES):
        for cc, rr in f.each():
            f.put(cc, rr, 's4' if rr % 2 == 0 else 's2')
    S.face('screw', 'up').fill('s4')
    S.face('screw', 'down').fill('s1')
    for f in S.faces('bar', K.SIDES):
        K.brass_rail(f)
    S.face('bar', 'up').fill('b4')
    S.face('bar', 'down').fill('b1')
    for n in ('grip_l', 'grip_r'):
        K.small(S, n, 'r', 2, top='r3', bottom='r1')
        for f in S.faces(n, K.SIDES):
            f.row(1, 'r1')


def felt_texture(path):
    """16x16 frames, 8 frames of 10 ticks (an 80-tick loop). Row 0 cols 0-5: felt_1's side faces; row 2 cols 0-5:
    felt_2's. Everything else r0. A bead of electrolyte catches the light and runs 3 px along felt_1 (frames 1-3),
    then along felt_2 the other way (frames 5-7): slow, one pixel, never both at once."""
    seq = {1: (0, 1, 'r2'), 2: (0, 2, 'r3'), 3: (0, 3, 'r2'), 5: (2, 4, 'r2'), 6: (2, 3, 'r3'), 7: (2, 2, 'r2')}
    img = Image.new('RGBA', (16, 16 * 8), K.rgba('r0'))
    px = img.load()
    for fr, (row, col, key) in seq.items():
        px[col, 16 * fr + row] = K.rgba(key)
    img.save(path)
    K.write_mcmeta(path, frametime=10)
    return path


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview')
    a = ap.parse_args()
    S = K.Sheet(BBMODEL)
    paint(S)
    miss = S.missing(clear_ok=('pan_front', 'pan_back', 'pan_left', 'pan_right'))
    if miss:
        sys.exit('unpainted faces: %s' % miss)
    S.save(SHEET)
    felt_texture(FELT)
    print('wrote', SHEET, FELT)
    if a.preview:
        os.makedirs(a.preview, exist_ok=True)
        for state, name in (('ready', 'pile_rest.png'), ('work', 'pile_work.png')):
            K.standins_from(S, PLACEHOLDER)
            K.lens_on_sheet(S, 'lamp_lens', state)
            S.save(os.path.join(a.preview, name))
        print('previews in', a.preview)


if __name__ == '__main__':
    main()

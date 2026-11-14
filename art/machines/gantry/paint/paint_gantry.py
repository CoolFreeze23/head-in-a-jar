"""Assembly Gantry (the turntable and the Anglepoise arm): the painted sheet, every pixel placed from palette keys.

    python -I art/machines/gantry/paint/paint_gantry.py                # writes block/machine/gantry.png
    python -I art/machines/gantry/paint/paint_gantry.py --preview DIR  # also DIR/gantry_rest.png (stand-ins filled)
Then: python -I tools/tex/tex.py adopt block/machine/gantry.png

One sheet for the baked pedestal and the thirteen GeckoLib bones.

The value plan (110 px first). It stands under a Hopper or a Bulwark (ivory, brass, dark steel) and must not fight
them, so the pedestal is quiet and the mecha stays the subject:
  LIGHT  the louvred enamel pedestal (K2 body, e2 field, e3 lip, belt seam) and the arm's two pale links (e3 field,
         an e4 arris on the lit faces, e1 ends where they enter a joint)
  DARK   the pedestal's core seen through the slits and the gap under the turntable (s0), the turntable's deck plate
         (s1, worn), every joint of the arm (shoulder cheeks, swivel, elbow: dark steel s1 with a brass pin boss)
  ACCENT the turntable's band: 45-degree hazard stripes (it turns, so it may: K8); the copper spring (turns c5, gaps
         c1: it must read as a spring); the brass head with its dark tool mouth and green lens at the arm's tip
Story marks, once each: two boot-worn tracks across the deck plate and its four bolts; a sooty welder tip; the
stamp's inked face; the head's cradle cup polished at its lip.
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'kit', 'paint'))
import kitpaint as K  # noqa: E402

MDIR = os.path.join(HERE, '..')
BBMODEL = os.path.join(MDIR, 'gantry.bbmodel')
PLACEHOLDER = os.path.join(MDIR, 'gantry_placeholder.png')
SHEET = os.path.join(K.OUT, 'gantry.png')
OUTER = {'n': 'north', 's': 'south', 'w': 'west', 'e': 'east'}


def pedestal(S):
    K.recess(S, 'core', 's0')                     # seen only through the slits and the gap: one dark
    for n in S.names('slab_'):
        side = OUTER[n.split('_')[1]]
        if 'rail' in n:
            # a louvre bar, set 1 px back: its face one field, a lit top; its sides (the slit walls) in shadow
            for f in S.faces(n, K.SIDES):
                f.fill('e2' if f.side == side else 'e1')
            S.face(n, side).row(0, 'e3')
            S.face(n, 'up').fill('e3')
            S.face(n, 'down').fill('e1')
            continue
        for f in S.faces(n, K.SIDES):
            K.enamel_body(f, 3, 14)
        S.face(n, 'up').by_world(lambda c, r, x, y, z: 'e3' if y < 13 else ('e4' if (z < -6 or x > 6) else 'e3'))
        S.face(n, 'down').fill('e0')
    # the hub in the 2 px dark gap under the turntable
    K.small(S, 'hub', 's', 0, top='s0', bottom='s0')
    K.dark_steel(S, 'mount')
    K.brass_plate(S, 'tray')
    # the head's cradle cup: small brass, its lip polished
    for n in S.names('cradle_'):
        K.small(S, n, 'b', 3, top='b4', bottom='b1')
    S.face('cradle_n', 'up').put(0, 0, 'b5')
    K.small(S, 'reel_boss', 's', 1, top='s2', bottom='s0')
    K.lamp_bezel(S)
    K.lens_on_sheet(S, 'lamp_lens', 'ready')


def reel(S):
    """The cable reel: copper windings across the drum (c5 turns over c3, every other row), darker flanges (c2)."""
    for f in S.faces('reel_drum'):
        for c, r in f.each():
            f.put(c, r, 'c5' if r % 2 == 0 else 'c3')
    for n in ('reel_cap_t', 'reel_cap_b'):
        K.small(S, n, 'c', 2, top='c3', bottom='c1')
    K.small(S, 'reel_crank', 'b', 3, top='b4', bottom='b1')


def turntable(S):
    """The hazard band round the octagon (K8 stripes on every side face), the deck plate on top: dark (s1), two
    boot-worn tracks (s2, 3-4 px, front to back where a frame's feet stand and turn), four bolts (s3 head, s0 shadow)
    on the core's top, the underside s0."""
    for n in ('tt_core', 'tt_e', 'tt_w', 'tt_s', 'tt_n'):
        K.stripes(S, n)
        S.face(n, 'down').fill('s0')

        def deck(c, r, x, y, z):
            if abs(x) == 2.5 and -1.5 <= z <= 2.5:
                return 's2'                         # the worn tracks
            if abs(x) == 3.5 and abs(z) == 3.5:
                return 's3'                         # bolt heads
            return 's1'
        S.face(n, 'up').by_world(deck)


def joint(S, cube, boss_sides=()):
    """A dark joint of the arm: dark steel s1 with an s2 lit top row, and on its pin faces a 2x2 brass boss (b4 b3 /
    b3 b2) at the centre: the pale links meet in dark knuckles, the pins catch the light."""
    K.dark_steel(S, cube)
    for side in boss_sides:
        f = S.face(cube, side)
        c0, r0 = (f.W - 2) // 2, (f.H - 2) // 2
        f.rows(['b4 b3', 'b3 b2'], c0=c0, r0=r0)


def arm(S):
    K.small(S, 'swivel_drum', 's', 1, top='s2', bottom='s0')
    joint(S, 'cheek_l', ('west', 'east'))
    joint(S, 'cheek_r', ('west', 'east'))
    K.pale_link(S, 'upper_bar')
    K.pale_link(S, 'fore_bar')
    # the telescopes' steel rods (out only for the tall frames): polished, a lit arris on the north and west faces
    for n in ('upper_rod', 'fore_rod_lo', 'fore_rod_hi'):
        for f in S.faces(n, K.SIDES):
            f.fill('s3')
            if f.side in ('north', 'west'):
                f.col(0, 's4')
        S.face(n, 'up').fill('s4')
        S.face(n, 'down').fill('s2')
    # the sleeves the client draws over a rod once it is out (3x3, the bar's section, scaled along their length: every
    # column one value from end to end, so the scale stretches nothing): polished steel, a lit arris on the north and
    # west faces, the far edge a step down; the ends as the rods'
    for n in ('upper_sleeve', 'fore_sleeve_lo', 'fore_sleeve_hi'):
        for f in S.faces(n, K.SIDES):
            f.fill('s3')
            if f.side in ('north', 'west'):
                f.col(0, 's4')
            f.col(f.W - 1, 's2')
        S.face(n, 'up').fill('s4')
        S.face(n, 'down').fill('s2')
    joint(S, 'elbow', ('west', 'east'))
    K.coil(S, 'spring')
    for n in ('standoff_1', 'standoff_2'):
        K.small(S, n, 's', 1, top='s2', bottom='s0')
    # the cable down the forearm: rubber with a rib every 3 rows
    for f in S.faces('fore_cable', K.SIDES):
        for c, r in f.each():
            f.put(c, r, 'r2' if r % 3 == 0 else 'r1')
    S.face('fore_cable', 'up').fill('r2')
    S.face('fore_cable', 'down').fill('r0')


def head(S):
    """The tool head at the arm's tip: a brass housing, its front a dark mouth (r0, 3 wide) where the tools come out
    under a b4 brow; the back cap dark steel; the green inspection lens (G3 G2 G1 across its 3 px front)."""
    K.brass_housing(S, 'shade')
    f = S.face('shade', 'north')
    for c, r in f.each():
        if 1 <= c <= f.W - 2 and 1 <= r <= f.H - 1:
            f.put(c, r, 'r0')
    K.small(S, 'shade_back', 's', 1, top='s2', bottom='s0')
    lens = 'head_lens'
    K.fill(S, lens, 'G1')
    S.face(lens, 'north').rows(['G3 G2 G1'])
    S.face(lens, 'down').fill('G0')
    # the tools
    K.small(S, 'weld_nozzle', 'c', 4, top='c5', bottom='c2')
    S.face('weld_nozzle', 'north').fill('c1')               # the sooty tip
    S.face('weld_nozzle', 'north').put(0, 0, 'c2')
    for n in ('wrench_shank', 'wrench_jaw', 'grip_l', 'grip_r', 'stamp_stem'):
        K.small(S, n, 's', 3)
    K.small(S, 'stamp_pad', 'r', 1)
    S.face('stamp_pad', 'north').rows(['w2 w2 w2', 'w2 r1 w2', 'w2 w2 w1'])   # the inked company square


def paint(S):
    K.plinth(S)
    K.feet(S)
    pedestal(S)
    reel(S)
    turntable(S)
    arm(S)
    head(S)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview')
    a = ap.parse_args()
    S = K.Sheet(BBMODEL)
    paint(S)
    miss = S.missing()
    if miss:
        sys.exit('unpainted faces: %s' % miss)
    print('wrote', S.save(SHEET))
    if a.preview:
        os.makedirs(a.preview, exist_ok=True)
        K.standins_from(S, PLACEHOLDER)
        S.save(os.path.join(a.preview, 'gantry_rest.png'))
        K.lens_on_sheet(S, 'lamp_lens', 'work')
        S.save(os.path.join(a.preview, 'gantry_work.png'))
        print('previews in', a.preview)


if __name__ == '__main__':
    main()

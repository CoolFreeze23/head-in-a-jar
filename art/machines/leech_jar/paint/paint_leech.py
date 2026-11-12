"""Leech Jar (a gumball machine): the painted sheet, every pixel placed from palette keys.

    python -I art/machines/leech_jar/paint/paint_leech.py                # writes block/machine/leech_jar.png
    python -I art/machines/leech_jar/paint/paint_leech.py --preview DIR  # also DIR/leech_rest.png, leech_work.png
Then: python -I tools/tex/tex.py adopt block/machine/leech_jar.png

One sheet, 128x144, for the baked machine (the vial groups state_samples_1..3 included) and the four bones (carrier,
leech, nozzle, crank). Dry and funny, not horror.

The value plan (110 px first):
  DARK   the water in the globe (t0 over r1 lower rows, its surface t0 with a t1 meniscus ring): the darkest mass; the window's back (r0)
  LIGHT  the ivory pedestal (K2 faces with the belt), the clear stepped globe (cutout: a g3 rim row and corners
         only, a highlight arc at its upper left and darker g1 corners on the big faces, so the box rounds into a
         ball and the water shows)
  ACCENT the leech: a plump pale comma (bone o2 body, o3 back, o1 belly and tail tip, no detail) light on the dark
         water; the red sample vials (f1, an f2 sheen column, a g2 glass neck, an f0 foot) like gumballs; the brass
         foot, collar, cap, rack and crank; the Butcher's paper hat (e5, its band e4)
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'kit', 'paint'))
import kitpaint as K  # noqa: E402

MDIR = os.path.join(HERE, '..')
BBMODEL = os.path.join(MDIR, 'leech_jar.bbmodel')
PLACEHOLDER = os.path.join(MDIR, 'leech_jar_placeholder.png')
SHEET = os.path.join(K.OUT, 'leech_jar.png')
GLASS = ('globe_lo', 'globe_a', 'globe_b', 'globe_hi')


def pedestal(S):
    K.recess(S, 'kick', 'r0')
    K.brass_plate(S, 'base')
    for n in S.names('ped_'):
        if n == 'ped_window_back':
            continue
        for f in S.faces(n, K.SIDES):
            K.enamel_body(f, 3, 12)
        K.enamel_top(S.face(n, 'up'))
        S.face(n, 'down').fill('e0')
    K.recess(S, 'ped_window_back', 'r0')
    for f in S.faces('rack', K.SIDES):
        K.brass_rail(f)
    S.face('rack', 'up').fill('b4')
    S.face('rack', 'down').fill('b1')
    for n in S.names('vial'):
        for f in S.faces(n, K.SIDES):
            for c, r in f.each():
                f.put(c, r, 'g2' if r == 0 else ('f0' if r == f.H - 1 else ('f2' if c == 0 else 'f1')))
        S.face(n, 'up').fill('g3')
        S.face(n, 'down').fill('f0')
    K.lamp_bezel(S, face='west')
    K.lens_on_sheet(S, 'lamp_lens', 'ready')
    S.face('lamp_lens', 'west').rows(['G3 G2', 'G2 G1'])
    K.brass_plate(S, 'collar')


def globe(S):
    for n in GLASS:
        for f in S.faces(n):
            if f.H <= 3 and f.side in K.SIDES:
                K.glass_window(f)
                continue
            if f.side in K.SIDES:
                K.glass_window(f)                                 # a rim row and corners: the water shows
            else:
                K.clear_glass(f, streaks=False)
            if f.W >= 8 and f.H >= 6 and f.side in K.SIDES:
                for (c, r) in ((2, 3), (2, 2), (3, 1), (4, 1)):     # the highlight arc, upper left
                    f.put(c, r, 'g3')
                for (c, r) in ((1, f.H - 2), (f.W - 2, f.H - 2), (f.W - 2, 1)):
                    f.put(c, r, 'g1')                             # darker corners: the ball turning away
    for n in ('water_lo', 'water_a', 'water_b'):
        for f in S.faces(n, K.SIDES):
            for c, r in f.each():
                x, y, z = f.world(c, r)
                f.put(c, r, 't1' if y > 18 else ('r1' if y < 17 else 't0'))
        up = S.face(n, 'up')
        up.fill('t0')
        up.by_world(lambda c, r, x, y, z: 't1' if (abs(x) > 4.5 or abs(z) > 4.5) else None)
        S.face(n, 'down').fill('r1')
    K.brass_housing(S, 'cap')
    K.small(S, 'knob', 'b', 4, top='b5', bottom='b2')


def creatures(S):
    # the leech: a plump pale comma, no detail
    for f in S.faces('leech_body', K.SIDES):
        f.fill('o2')
        f.row(0, 'o3')
        f.row(f.H - 1, 'o1')
    S.face('leech_body', 'up').fill('o3')
    S.face('leech_body', 'down').fill('o1')
    K.small(S, 'leech_tail', 'o', 1, top='o2', bottom='o0')
    # the Butcher's paper hat
    K.small(S, 'leech_hat', 'e', 5, top='e5', bottom='e3')
    for f in S.faces('leech_hat', K.SIDES):
        f.row(f.H - 1, 'e4')
    # the sampler: a dark flange with a lit rim, the brass cup, the steel rod
    K.small(S, 'flange', 's', 1, top='s2', bottom='s0')
    S.face('flange', 'north').rows(['s2 s2 s2 s2', 's2 s0 s0 s1', 's2 s0 s0 s1', 's1 s1 s1 s1'])
    K.small(S, 'nozzle_cup', 'b', 3, top='b4', bottom='b1')
    S.face('nozzle_cup', 'north').rows(['b4 b3', 'b3 r0'])
    K.small(S, 'nozzle_rod', 's', 4, top='s5', bottom='s3')
    # the crank: a brass hub and arm, a rubber knob
    K.small(S, 'crank_hub', 'b', 3, top='b4', bottom='b1')
    for f in S.faces('crank_arm', K.SIDES):
        f.fill('b3')
        f.col(0, 'b4')
    S.face('crank_arm', 'up').fill('b4')
    S.face('crank_arm', 'down').fill('b1')
    K.small(S, 'crank_knob', 'r', 2, top='r3', bottom='r1')


def paint(S):
    pedestal(S)
    globe(S)
    creatures(S)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview')
    a = ap.parse_args()
    S = K.Sheet(BBMODEL)
    paint(S)
    miss = S.missing(clear_ok=GLASS)
    if miss:
        sys.exit('unpainted faces: %s' % miss)
    print('wrote', S.save(SHEET))
    if a.preview:
        os.makedirs(a.preview, exist_ok=True)
        K.standins_from(S, PLACEHOLDER)
        S.save(os.path.join(a.preview, 'leech_rest.png'))
        K.lens_on_sheet(S, 'lamp_lens', 'work')
        S.face('lamp_lens', 'west').rows(['A3 A2', 'A2 A1'])
        S.save(os.path.join(a.preview, 'leech_work.png'))
        print('previews in', a.preview)


if __name__ == '__main__':
    main()

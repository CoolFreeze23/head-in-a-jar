"""Leyden Jar (a glass battery jar in a carrying lantern cage): the painted sheet and the knob's spark.

    python -I art/machines/leyden_jar/paint/paint_leyden.py                # writes the textures into the assets
    python -I art/machines/leyden_jar/paint/paint_leyden.py --preview DIR  # also DIR/leyden_rest.png, _work.png
Then adopt what it prints with tools/tex/tex.py adopt.

Writes, under textures/block/machine/:
  leyden_jar.png             128x144, the baked jar (no bones): every element, the four charge-cell groups included
  leyden_jar_knob_spark.png  the knob (state_knob, 4x4x4) at level 4: brass with sparks crawling over it, 8 frames x
                             10 ticks (80 a cycle, .mcmeta); box-UV at (0, 0) of a 16x16 sprite

The value plan (110 px first; the five levels must count apart at ten blocks):
  DARK   the battery windows: the core behind them r0, with the four cell outlines barely there (r1), so an empty
         window still reads as a battery icon; the dark glass band and shoulder (t0, a t1 sheen, one t2 glint); the
         kick and base
  LIGHT  the lit cells: warning amber, full-bright in game (A2 field, A1 foot row; full-bright in game): bright against the dark window
  ACCENT the copper foil (c4 field, c6 lit top row, one c6 sheen streak on the broad back, c2 foot); the steel lantern
         cage (s3, an s4 arris, s4 tops) closed over a brass knob; the K3 lamp on the glass band; the maker's plate
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'kit', 'paint'))
import kitpaint as K  # noqa: E402

MDIR = os.path.join(HERE, '..')
BBMODEL = os.path.join(MDIR, 'leyden_jar.bbmodel')
PLACEHOLDER = os.path.join(MDIR, 'leyden_jar_placeholder.png')
SHEET = os.path.join(K.OUT, 'leyden_jar.png')
SPARK = os.path.join(K.OUT, 'leyden_jar_knob_spark.png')
CELLS_Y = ((3, 5), (6, 8), (9, 11), (12, 14))


def core(S):
    """The core: r0, and on its three window faces the four cell outlines (r1) where the cells stand when lit."""
    K.fill(S, 'core', 'r0')
    for side in ('north', 'east', 'west'):
        f = S.face('core', side)

        def k(c, r, x, y, z):
            u = x if side == 'north' else z
            if -2 < u < 2 and any(a < y < b for a, b in CELLS_Y):
                return 'r1'
            return None
        f.by_world(k)


def foil(S):
    for n in [n for n in S.cubes if n.startswith(('jar_', 'step_')) and 'glass' not in n]:
        for f in S.faces(n, K.SIDES):
            for c, r in f.each():
                x, y, z = f.world(c, r)
                f.put(c, r, 'c6' if y > 13 else ('c2' if y < 3 else 'c4'))
        S.face(n, 'up').fill('c5')
        S.face(n, 'down').fill('c2')
    f = S.face('jar_south_all', 'south')                    # one sheen streak across the broad back
    for i in range(4):
        f.put(2 + i, 8 - i, 'c6')


def glass(S):
    for n in [n for n in S.cubes if 'glass' in n] + ['shoulder']:
        for f in S.faces(n, K.SIDES):
            f.fill('t0')
            if f.W >= 4:
                f.col(1, 't1')
            f.put(0, 0, 't2')
        S.face(n, 'up').fill('t1')
        S.face(n, 'down').fill('t0')


def cage(S):
    for n in S.names('strap_') + ['ring_e', 'ring_w', 'bail_up_e', 'bail_up_w', 'bail_top']:
        for f in S.faces(n, K.SIDES):
            for c, r in f.each():
                f.put(c, r, 's4' if ((f.side in ('north', 'west') and c == 0) or r == 0) else 's3')
        S.face(n, 'up').fill('s4')
        S.face(n, 'down').fill('s2')


def paint(S):
    K.recess(S, 'kick', 'r0')
    K.dark_steel(S, 'base')
    core(S)
    foil(S)
    glass(S)
    cage(S)
    K.small(S, 'stopper', 'r', 1, top='r2', bottom='r0')
    K.small(S, 'rod', 'b', 3, top='b4', bottom='b1')
    knob_plain(S)
    K.lamp_bezel(S)
    K.lens_on_sheet(S, 'lamp_lens', 'ready')
    K.makers_plate(S, 'makers_plate', 'south')
    for n in [n for n in S.cubes if n.startswith('fill')]:
        for f in S.faces(n):
            f.fill('A2')
            if f.side in K.SIDES and f.H >= 2:
                f.row(f.H - 1, 'A1')


def knob_plain(S):
    k = 'knob'
    for f in S.faces(k, K.SIDES):
        f.fill('b3')
        f.row(0, 'b4')
        f.row(f.H - 1, 'b2')
    S.face(k, 'up').fill('b4')
    S.face(k, 'up').put(0, 0, 'b5')
    S.face(k, 'down').fill('b1')


# the sparks: per frame, which (side, c, r) carry a spark (e5 core) and its tail (A3); placed, a few a frame, crawling
SPARKS = [
    [('north', 1, 0)], [('north', 2, 1), ('up', 1, 2)], [('east', 1, 1)], [],
    [('west', 2, 0), ('up', 2, 1)], [('south', 1, 2)], [('north', 3, 2), ('west', 1, 1)], [],
]


def spark_texture():
    def face(fr, side, c, r, W, H):
        for (s, cc, rr) in SPARKS[fr]:
            if s == side and (c, r) == (cc, rr):
                return 'e5'
            if s == side and abs(c - cc) + abs(r - rr) == 1:
                return 'A3'
        if side == 'up':
            return 'b5' if (c, r) == (0, 0) else 'b4'
        if side == 'down':
            return 'b1'
        return 'b4' if r == 0 else ('b2' if r == H - 1 else 'b3')
    return [K.state_sprite(SPARK, 4, 4, 4, face, fill='b3', frames=8, frametime=10), SPARK + '.mcmeta']


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview')
    a = ap.parse_args()
    S = K.Sheet(BBMODEL)
    paint(S)
    miss = S.missing()
    if miss:
        sys.exit('unpainted faces: %s' % miss)
    out = [S.save(SHEET)] + spark_texture()
    for p in out:
        print('wrote', p)
    if a.preview:
        os.makedirs(a.preview, exist_ok=True)
        K.standins_from(S, PLACEHOLDER)
        S.save(os.path.join(a.preview, 'leyden_rest.png'))
        K.lens_on_sheet(S, 'lamp_lens', 'work')
        S.save(os.path.join(a.preview, 'leyden_work.png'))
        print('previews in', a.preview)


if __name__ == '__main__':
    main()

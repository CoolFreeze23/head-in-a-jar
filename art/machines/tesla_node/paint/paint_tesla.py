"""Tesla Node (a small coil with a floating steel ring and a brass ball on its rim): the painted sheet.

    python -I art/machines/tesla_node/paint/paint_tesla.py                # writes block/machine/tesla_node.png
    python -I art/machines/tesla_node/paint/paint_tesla.py --preview DIR  # also DIR/tesla_rest.png, tesla_work.png
Then: python -I tools/tex/tex.py adopt block/machine/tesla_node.png

One sheet, 128x96, for the baked base and coil and the two bones (torus, pointer). No glow is painted on: the arcs
are drawn by the game.

The value plan (110 px first):
  DARK   the plinth (s1-s2), the coil's former between the turns (s0), the rubber insulator
  LIGHT  the floating ring: bright steel (s4 field, s5 lit rows and tops, s3 undersides), with ONE segment marked (a
         yellow band on the front of its middle course, torus_mid_n) so its spin reads; the ivory drum base below
  ACCENT the copper windings (c4 sides, c6 tops, c2 undersides: three 1 px turns apart over the dark former), the brass
         collar, the polished brass ball (b3 field, b4 lit faces, a b5 glint at its front upper left, b1 underside)
Story marks, once each: the ball's glint; the marked ring segment; the maker's plate on the right of the base.
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'kit', 'paint'))
import kitpaint as K  # noqa: E402

MDIR = os.path.join(HERE, '..')
BBMODEL = os.path.join(MDIR, 'tesla_node.bbmodel')
PLACEHOLDER = os.path.join(MDIR, 'tesla_node_placeholder.png')
SHEET = os.path.join(K.OUT, 'tesla_node.png')


def base(S):
    K.dark_steel(S, 'plinth')
    for n in ('base_a', 'base_b'):
        for f in S.faces(n, K.SIDES):
            K.enamel_body(f, 3, 8, bead=False)
        K.enamel_top(S.face(n, 'up'))
        S.face(n, 'down').fill('e0')
    K.makers_plate(S, 'makers_plate', 'east')
    K.lamp_bezel(S)
    K.lens_on_sheet(S, 'lamp_lens', 'ready')
    K.brass_plate(S, 'collar_lo')


def coil(S):
    K.recess(S, 'former', 's0')
    for n in S.names('turn_'):
        for f in S.faces(n, K.SIDES):
            f.fill('c4')
        S.face(n, 'up').fill('c6')
        S.face(n, 'down').fill('c2')
    ci = 'collar_hi'
    for f in S.faces(ci, K.SIDES):
        f.fill('r1')
        f.row(0, 'r2')
    S.face(ci, 'up').fill('r2')
    S.face(ci, 'down').fill('r0')
    for n in ('term_core', 'term_w', 'term_e'):
        K.small(S, n, 's', 4, top='s5', bottom='s3')


def ring(S):
    for n in S.names('torus_'):
        for f in S.faces(n, K.SIDES):
            f.fill('s4')
            if f.H >= 2:
                f.row(0, 's5')
        S.face(n, 'up').fill('s5')
        S.face(n, 'down').fill('s3')
        if n.endswith(('_wi', '_ei')):              # the hole's walls: in the ring's own shade
            for f in S.faces(n, K.SIDES):
                f.fill('s2')
    # the mark: the front of the middle course carries a yellow band (w2, w3 top row), so the spin reads
    f = S.face('torus_mid_n', 'north')
    for c, r in f.each():
        if 3 <= c <= 6:
            f.put(c, r, 'w3' if r == 0 else 'w2')


def pointer(S):
    K.small(S, 'shoe', 's', 1, top='s2', bottom='s0')
    K.small(S, 'post', 'b', 3, top='b4', bottom='b1')
    for n in ('ball_core', 'ball_w', 'ball_e', 'ball_n', 'ball_s'):
        for f in S.faces(n, K.SIDES):
            f.fill('b3')
            f.row(0, 'b4')
            if f.H >= 4:
                f.row(f.H - 1, 'b2')
        S.face(n, 'up').fill('b4')
        S.face(n, 'down').fill('b1')
    S.face('ball_n', 'north').put(0, 0, 'b5')
    S.face('ball_n', 'north').put(1, 0, 'b5')
    S.face('ball_core', 'up').put(1, 1, 'b5')


def paint(S):
    base(S)
    coil(S)
    ring(S)
    pointer(S)


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
        S.save(os.path.join(a.preview, 'tesla_rest.png'))
        K.lens_on_sheet(S, 'lamp_lens', 'work')
        S.save(os.path.join(a.preview, 'tesla_work.png'))
        print('previews in', a.preview)


if __name__ == '__main__':
    main()

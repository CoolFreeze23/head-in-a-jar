"""Jarred Head block (a reception-desk exhibit): the painted sheet, every pixel placed from palette keys.

    python -I art/machines/jarred_head/paint/paint_jarred.py                # writes block/machine/jarred_head.png
    python -I art/machines/jarred_head/paint/paint_jarred.py --preview DIR  # also DIR/jarred_rest.png
Then: python -I tools/tex/tex.py adopt block/machine/jarred_head.png

One sheet, 128x80, for the baked plinth (no bones). The jar is the game's, at 1:1.

The value plan: the jar is the subject; under it one brass band on one dark line.
  The plinth's sides are painted as ONE COUNTER: its front, back and both ends carry the same rows by world height
  (a b4 lit lip at the top, the b3 field, a b2 row and a b1 foot over the dark kick), with no darker end columns and
  no end caps, so two or more side by side join into one continuous reception counter. Its top: b3 with a b4 front
  edge only (no edge along the ends, which would draw a seam between neighbours).
  The plaque: a dark steel backing (s1, a lit s2 top row) round a pale nameplate (e4, one engraved e1 line: the
  monogram does not fit 7x2); the push bell: a brass dome (b4, a b5 glint) on a dark plate.
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'kit', 'paint'))
import kitpaint as K  # noqa: E402

MDIR = os.path.join(HERE, '..')
BBMODEL = os.path.join(MDIR, 'jarred_head.bbmodel')
PLACEHOLDER = os.path.join(MDIR, 'jarred_head_placeholder.png')
SHEET = os.path.join(K.OUT, 'jarred_head.png')


def paint(S):
    K.recess(S, 'kick', 'r0')
    p = 'plinth'
    for f in S.faces(p, K.SIDES):
        f.by_world(lambda c, r, x, y, z: 'b4' if y > 6 else ('b1' if y < 2 else ('b2' if y < 3 else 'b3')))
    S.face(p, 'up').by_world(lambda c, r, x, y, z: 'b4' if z < -5 else 'b3')
    S.face(p, 'down').fill('b1')
    for n in ('plaque_back', 'bell_plate'):
        for f in S.faces(n, K.SIDES):
            f.fill('s1')
            f.row(0, 's2')
        S.face(n, 'up').fill('s2')
        S.face(n, 'down').fill('s0')
    np_ = 'nameplate'
    K.fill(S, np_, 'e3')
    S.face(np_, 'north').rows(['e5 e4 e4 e4 e4 e4 e4', 'e4 e1 e1 e1 e1 e1 e3'])   # one engraved line, not dark blocks (they read as teeth)
    S.face(np_, 'up').fill('e5')
    K.small(S, 'bell_dome', 'b', 4, top='b5', bottom='b2')
    S.face('bell_dome', 'north').rows(['b5 b4', 'b3 b2'])


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
        S.save(os.path.join(a.preview, 'jarred_rest.png'))
        S.save(os.path.join(a.preview, 'jarred_work.png'))
        print('previews in', a.preview)


if __name__ == '__main__':
    main()

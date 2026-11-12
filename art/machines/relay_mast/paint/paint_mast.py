"""Relay Mast (Mk I: a radio dish on a lamp post): the painted sheet and the aviation lamp's two sprites.

    python -I art/machines/relay_mast/paint/paint_mast.py                # writes the textures into the assets
    python -I art/machines/relay_mast/paint/paint_mast.py --preview DIR  # also DIR/mast_rest.png, mast_work.png
Then adopt what it prints with tools/tex/tex.py adopt.

Writes, under textures/block/machine/:
  relay_mast.png              128x96, the baked mast and the `dish` bone
  relay_mast_beacon_lit.png   the aviation lamp (beacon_lens, 2x2x2, static), lit: box-UV at (0, 0) of a 16x16 sprite
  relay_mast_beacon_off.png   the same, unpowered

The value plan (110 px first, and 96 blocks): the dish's light face (e5 bowl, b4 rim) against its s0 back (see
mastpaint.py); a steel pole (s3, an s4 arris on its lit faces, placed s4/s2 streaks) with a copper cable up its back;
a short ivory base (K2 faces) on a dark plinth; the copper cable drum (c5 windings over c3) between dark flanges;
the red aviation lamp on a brass cap at the top.
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', '..', 'kit', 'paint'))
import kitpaint as K  # noqa: E402
import mastpaint as MP  # noqa: E402

MDIR = os.path.join(HERE, '..')
BBMODEL = os.path.join(MDIR, 'relay_mast.bbmodel')
PLACEHOLDER = os.path.join(MDIR, 'relay_mast_placeholder.png')
SHEET = os.path.join(K.OUT, 'relay_mast.png')


def paint(S):
    K.plinth(S)
    K.feet(S)
    for n in ('base_a', 'base_b'):
        for f in S.faces(n, K.SIDES):
            K.enamel_body(f, 3, 8, bead=False)
        K.enamel_top(S.face(n, 'up'))
        S.face(n, 'down').fill('e0')
    K.lamp_bezel(S)
    K.lens_on_sheet(S, 'lamp_lens', 'ready')
    K.makers_plate(S, 'makers_plate', 'east')
    for n in ('flange_lo', 'flange_hi'):
        K.dark_steel(S, n)
    for f in S.faces('coil'):
        for c, r in f.each():
            f.put(c, r, 'c5' if r % 2 == 0 else 'c3')
    K.small(S, 'cable', 'c', 4, top='c5', bottom='c2')
    for f in S.faces('pole', K.SIDES):            # a steel pole, mid (not the dark pole): s3 with an s4 arris
        for c, r in f.each():
            f.put(c, r, 's4' if (f.side in ('north', 'west') and c == 0) else 's3')
        for r in (3, 4, 5):                       # two placed streaks, 3 px each
            f.put(2, r, 's4')
            f.put(1, r + 7, 's2')
    S.face('pole', 'up').fill('s4')
    S.face('pole', 'down').fill('s2')
    K.small(S, 'beacon_base', 'b', 3, top='b4', bottom='b1')
    MP.beacon(S)
    MP.dish(S)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview')
    a = ap.parse_args()
    S = K.Sheet(BBMODEL)
    paint(S)
    miss = S.missing()
    if miss:
        sys.exit('unpainted faces: %s' % miss)
    out = [S.save(SHEET)] + MP.beacon_textures('relay_mast')
    for p in out:
        print('wrote', p)
    if a.preview:
        os.makedirs(a.preview, exist_ok=True)
        K.standins_from(S, PLACEHOLDER)
        S.save(os.path.join(a.preview, 'mast_rest.png'))
        K.lens_on_sheet(S, 'lamp_lens', 'work')
        S.save(os.path.join(a.preview, 'mast_work.png'))
        print('previews in', a.preview)


if __name__ == '__main__':
    main()

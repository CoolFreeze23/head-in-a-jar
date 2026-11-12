"""Relay Mast Mk II (a radar dish on a four-legged tripod with a telescoping lamp stage): the painted sheet.

    python -I art/machines/relay_mast_mk2/paint/paint_mk2.py                # writes the textures into the assets
    python -I art/machines/relay_mast_mk2/paint/paint_mk2.py --preview DIR  # also DIR/mk2_rest.png, mk2_work.png
Then adopt what it prints with tools/tex/tex.py adopt.

Writes, under textures/block/machine/:
  relay_mast_mk2.png            128x80, the baked tripod and the two bones (dish, stage2)
  relay_mast_mk2_glowmask.png   GeckoLib glow companion: the aviation lamp's lens only (it rides the `stage2` bone,
                                so a blockstate cannot swap it; it glows whenever the geo is drawn with the glow layer)

The same materials as the Mk I so the two are a family (the dish from ../relay_mast/paint/mastpaint.py): told apart
by shape. Steel legs (s3, an s4 arris) on rubber pads into a dark collar; a brass pole (b3, b4 lit column); the dish's
light face against its s0 back; a small steel receiver; the lamp stack (a dark rod, a brass cap, the red lens) is the
moving read.
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'relay_mast', 'paint'))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'kit', 'paint'))
import kitpaint as K  # noqa: E402
import mastpaint as MP  # noqa: E402

MDIR = os.path.join(HERE, '..')
BBMODEL = os.path.join(MDIR, 'relay_mast_mk2.bbmodel')
PLACEHOLDER = os.path.join(MDIR, 'relay_mast_mk2_placeholder.png')
SHEET = os.path.join(K.OUT, 'relay_mast_mk2.png')
GLOW = os.path.join(K.OUT, 'relay_mast_mk2_glowmask.png')


def paint(S):
    for n in S.names('leg_'):
        for f in S.faces(n, K.SIDES):
            for c, r in f.each():
                f.put(c, r, 's4' if (f.side in ('north', 'west') and c == 0) else ('s2' if r == f.H - 1 else 's3'))
        S.face(n, 'up').fill('s4')
        S.face(n, 'down').fill('s2')
    for n in S.names('pad_'):
        K.small(S, n, 'r', 1, top='r2', bottom='r0')
    K.dark_steel(S, 'leg_collar')
    K.lamp_bezel(S)
    K.lens_on_sheet(S, 'lamp_lens', 'ready')
    for f in S.faces('pole', K.SIDES):
        for c, r in f.each():
            f.put(c, r, 'b4' if (f.side in ('north', 'west') and c == 0) else 'b3')
    S.face('pole', 'up').fill('b4')
    S.face('pole', 'down').fill('b1')
    MP.dish(S)
    K.small(S, 'recv_stalk', 's', 0, top='s1', bottom='s0')
    K.small(S, 'recv_dish', 's', 3)
    K.small(S, 'stage_rod', 's', 1, top='s2', bottom='s0')
    K.small(S, 'beacon_base', 'b', 3, top='b4', bottom='b1')
    MP.beacon(S)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview')
    a = ap.parse_args()
    S = K.Sheet(BBMODEL)
    paint(S)
    miss = S.missing()
    if miss:
        sys.exit('unpainted faces: %s' % miss)
    S.save(SHEET)
    keys = [('beacon_lens', f.side, c, r) for f in S.faces('beacon_lens') for c, r in f.each()]
    K.glowmask(S, keys).save(GLOW)
    print('wrote', SHEET, GLOW)
    if a.preview:
        os.makedirs(a.preview, exist_ok=True)
        K.standins_from(S, PLACEHOLDER)
        S.save(os.path.join(a.preview, 'mk2_rest.png'))
        K.lens_on_sheet(S, 'lamp_lens', 'work')
        S.save(os.path.join(a.preview, 'mk2_work.png'))
        print('previews in', a.preview)


if __name__ == '__main__':
    main()

"""Charging Dock (a doctor's dial scale with a knife switch): the painted sheet, every pixel placed from palette keys.

    python -I art/machines/charging_dock/paint/paint_dock.py                # writes block/machine/charging_dock.png
    python -I art/machines/charging_dock/paint/paint_dock.py --preview DIR  # also DIR/dock_rest.png, dock_work.png
Then: python -I tools/tex/tex.py adopt block/machine/charging_dock.png

One sheet, 128x112, for the baked scale and the two bones (contacts = the knife switch, needle).

The value plan (110 px first):
  DARK   the mat (r1, ribbed r0 every other row) recessed in the plate, the kick under it (r0), the slate board behind
         the dial and the switch (r1)
  LIGHT  the dial face (e4, a printed scale), the enamel column and riser
  ACCENT the yellow target ring on the mat (w3 tops, w2 sides): it must read as a place to stand, so the plate's
         rim is dark steel (s2, s3 front edge) round it, not brass; the brass dial frame and spindle, the brass blade with its yellow T-grip, the copper jaws
Story marks, once each: the dial's red E mark and green F mark at the ends of its scale; the scuffed centre of the
mat where jars are set down; the polished grip top.
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'kit', 'paint'))
import kitpaint as K  # noqa: E402

MDIR = os.path.join(HERE, '..')
BBMODEL = os.path.join(MDIR, 'charging_dock.bbmodel')
PLACEHOLDER = os.path.join(MDIR, 'charging_dock_placeholder.png')
SHEET = os.path.join(K.OUT, 'charging_dock.png')

# the dial's printed face (6x6, from the viewer's left = E): ticks round the upper half, E red, F green, the scale
# between them s3; the field e4; the lower half plain with a maker's line
DIAL = ['e4 e4 s3 s3 e4 e4',
        'e4 s3 e4 e4 s3 e4',
        'R1 e4 e4 e4 e4 j2',
        'R1 e4 e4 e4 e4 j2',
        'e4 e4 e4 e4 e4 e4',
        'e4 e3 s3 s3 e3 e4']


def plate(S):
    K.recess(S, 'kick', 'r0')
    for n in S.names('riser_'):
        for f in S.faces(n, K.SIDES):
            f.fill('e2')
            f.row(0, 'e3')
        S.face(n, 'up').fill('e3')
        S.face(n, 'down').fill('e0')
    m = 'mat'
    K.fill(S, m, 'r1')
    up = S.face(m, 'up')
    up.by_world(lambda c, r, x, y, z: 'r2' if (-1 < x < 1 and -1 < z < 1) else ('r0' if r % 2 == 1 else 'r1'))
    # the plate's rim in dark steel, not brass: a brass rim and a yellow ring read as one tan square in light; a dark
    # rim round a dark mat leaves the yellow ring as the one bright shape on the plate
    for n in S.names('rim_'):
        for f in S.faces(n, K.SIDES):
            f.fill('s1')
            f.row(0, 's2')
        up = S.face(n, 'up')
        up.by_world(lambda c, r, x, y, z: 's3' if (z < -6 or x > 6) else 's2')
        S.face(n, 'down').fill('s0')
    for n in S.names('target_'):
        for f in S.faces(n, K.SIDES):
            f.fill('w2')
        S.face(n, 'up').fill('w3')
        S.face(n, 'down').fill('w1')


def column(S):
    col = 'column'
    for f in S.faces(col, K.SIDES):
        K.enamel_body(f, 0, 31, bead=False)
        if f.side in ('north', 'west') and f.W >= 4:
            f.col(0, 'e3')
    K.enamel_top(S.face(col, 'up'))
    S.face(col, 'down').fill('e0')
    b = 'board'
    for f in S.faces(b, K.SIDES):
        f.fill('r1')
        f.row(0, 'r2')
    S.face(b, 'up').fill('r2')
    S.face(b, 'down').fill('r0')
    for n in ('dial_b', 'dial_t', 'dial_l', 'dial_r', 'dial_cbl', 'dial_cbr', 'dial_ctl', 'dial_ctr'):
        K.small(S, n, 'b', 3, top='b4', bottom='b1')
    S.face('dial_t', 'north').fill('b4')
    S.face('dial_b', 'north').fill('b2')
    df = 'dial_face'
    K.fill(S, df, 'e3')
    S.face(df, 'north').rows(DIAL)
    K.small(S, 'dial_spindle', 'b', 3, top='b4', bottom='b1')
    for n in ('cheek_l', 'cheek_r', 'jaw_l', 'jaw_r'):
        K.small(S, n, 'c', 4, top='c6', bottom='c2')
    K.lamp_bezel(S)
    K.lens_on_sheet(S, 'lamp_lens', 'ready')


def switch(S):
    bl = 'blade'
    for f in S.faces(bl, K.SIDES):
        f.fill('b3')
        f.row(0, 'b4')
        if f.side in ('north', 'west'):
            f.col(0, 'b4')
    S.face(bl, 'up').fill('b4')
    S.face(bl, 'down').fill('b1')
    g = 'grip'
    for f in S.faces(g, K.SIDES):
        K.plain_band(f)
    S.face(g, 'up').fill('w3')
    S.face(g, 'up').put(1, 0, 'e5')            # polished by hands
    S.face(g, 'down').fill('w1')
    # the needle: a dark one-ended pointer on a brass hub
    K.small(S, 'needle_hub', 'b', 3, top='b4', bottom='b1')
    K.small(S, 'needle_bar', 'r', 0, top='r1', bottom='r0')


def paint(S):
    plate(S)
    column(S)
    switch(S)


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
        S.save(os.path.join(a.preview, 'dock_rest.png'))
        K.lens_on_sheet(S, 'lamp_lens', 'work')
        S.save(os.path.join(a.preview, 'dock_work.png'))
        print('previews in', a.preview)


if __name__ == '__main__':
    main()

"""Cerebral Array (the jury box): the painted sheet and the broth level, every pixel placed from palette keys.

    python -I art/machines/cerebral_array/paint/paint_array.py                # writes the textures into the assets
    python -I art/machines/cerebral_array/paint/paint_array.py --preview DIR  # also DIR/array_rest.png, _work.png
Then adopt what it prints with tools/tex/tex.py adopt.

Writes, under textures/block/machine/:
  cerebral_array.png             128x160, the baked stand and the ten bones (nine cards, the gavel)
  cerebral_array_broth_0..4.png  the urn's window (element 2x3x1), one per `broth` level, box-UV at (0, 0)

The value plan (110 px first). It must stop reading as a display shelf: it is a courtroom, so the rails, the
chairman's bench and the gavel are polished dark WOOD (the brass ramp's darkest steps b0-b2, which read as wood: the
Hopper's lesson turned round), the jars' rings and lids are dark steel, and the brass stays on the pilasters, the urn and the gavel's bands:
  DARK   the nine recessed rubber bays (r0) behind the jars and their dark steel rings and lids: each jar a bright thing in a dark seat; the wood rails
         and the bench; the gavel's dark wood head (it holds against the sky at ten blocks; its handle pale)
  LIGHT  the jar glass (a g3 frame bright against the bay), the ivory stand's raked sides and the back panel
  ACCENT the brass pilasters and urn, the nine vote cards (a neutral light base, e5 with an e4 edge, for the code's
         green, red and buff tints), the broth window
Story marks, once each: the gavel's sound block worn pale at its centre; the polished brass bands on the gavel head;
the maker's plate on the back.
"""
import argparse
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'kit', 'paint'))
import kitpaint as K  # noqa: E402

MDIR = os.path.join(HERE, '..')
BBMODEL = os.path.join(MDIR, 'cerebral_array.bbmodel')
PLACEHOLDER = os.path.join(MDIR, 'cerebral_array_placeholder.png')
SHEET = os.path.join(K.OUT, 'cerebral_array.png')


def stand(S):
    K.recess(S, 'kick', 'r0')
    for n in S.names('floor_') + S.names('fill_') + S.names('slab_'):
        for f in S.faces(n, K.SIDES):
            for c, r in f.each():
                f.put(c, r, 'e3' if r == 0 else ('e1' if r == f.H - 1 and f.H >= 3 else 'e2'))
        K.enamel_top(S.face(n, 'up'))
        S.face(n, 'down').fill('e0')
    # the back panel inside the raised rim: recessed (e1, its top row and left column e0), the rim e3
    for n in ('fill_0', 'fill_1', 'fill_2'):
        f = S.face(n, 'south')

        def back(c, r, x, y, z):
            if -5 < x < 5 and 5 < y < 21:
                return 'e0' if (y > 20 or x < -4) else 'e1'
            return None
        f.by_world(back)
    for n in S.names('back_rim_'):
        K.fill(S, n, 'e3')
        S.face(n, 'up').fill('e4')
        S.face(n, 'down').fill('e1')
    K.makers_plate(S, 'makers_plate', 'south')
    # the bays: the darkest step, every seat a dark recess
    for n in S.names('bay_'):
        K.recess(S, n, 'r0')
    # the jury-box rails: a wood rail (b1, its lit top b2) under a wood cap (b2 top, continuous)
    for n in S.names('rail_'):
        for f in S.faces(n, K.SIDES):
            K.wood(f)
        K.wood_top(S.face(n, 'up'))
        S.face(n, 'down').fill('b0')
    for n in S.names('railcap_'):
        for f in S.faces(n, K.SIDES):
            f.fill('b1')
        S.face(n, 'up').fill('b2')
        S.face(n, 'down').fill('b0')
    for n in S.names('stringer_'):
        K.brass_post(S, n)


def jars(S):
    for n in S.names('jar_'):
        if n.endswith('_glass'):
            # bright against the bay: a g3 top row and side columns, clear between them (the head shows)
            for f in S.faces(n):
                K.clear_glass(f, frame=('g3', 'g3'), streaks=False, edges='tlr' if f.side in K.SIDES else 'tblr')
        else:
            # rings and lids in dark steel (s1, top s2), not brass: nine light brass lids and rings stacked in three rows
            # read as shelves; dark caps leave the glass as the one bright thing in each dark seat
            K.small(S, n, 's', 1, top='s2', bottom='s0')
    for i in range(1, 10):
        n = 'card_%d_card' % i
        for f in S.faces(n):
            K.tint_base(f)


def bench(S):
    """The chairman's bench: a wood desk top and front edge, the dark wood sound block (worn pale at its centre), the
    gavel (a dark wood head with polished brass bands at its ends, a pale ivory handle), the brass urn and cheeks."""
    for n in ('bench', 'desk'):
        for f in S.faces(n, K.SIDES):
            K.wood(f)
        K.wood_top(S.face(n, 'up'))
        S.face(n, 'down').fill('b0')
    sb = 'sound_block'
    for f in S.faces(sb, K.SIDES):
        f.fill('b0')
    up = S.face(sb, 'up')
    up.fill('b1')
    up.put(1, 0, 'b2')
    up.put(2, 0, 'b2')
    S.face(sb, 'down').fill('b0')
    h = 'gavel_head'
    for f in S.faces(h, K.SIDES):
        K.wood(f, grain=False)
        if f.W >= 4:
            f.col(0, 'b4')
            f.col(f.W - 1, 'b3')
    S.face(h, 'up').fill('b1')
    S.face(h, 'down').fill('b0')
    K.small(S, 'gavel_handle', 'e', 4, top='e5', bottom='e3')
    for n in ('urn_body', 'urn_l', 'urn_r'):
        K.brass_housing(S, n)
    for n in ('urn_foot', 'urn_top', 'cheek_f', 'cheek_b'):
        K.small(S, n, 'b', 3, top='b4', bottom='b1')
    K.lamp_bezel(S, face='north')
    K.lens_on_sheet(S, 'lamp_lens', 'ready')
    rows = broth_rows(2)
    for f in S.faces('broth_face'):
        for c, r in f.each():
            f.put(c, r, rows[r] if f.H == 3 else 't1')


def broth_rows(level):
    """The urn's 2x3 window, top to bottom: empty rows dark glass (t0, a t4 glint at the top left), the broth from the
    bottom, its top row the meniscus (f3), the body f2. Levels 0-4 fill 0, 1, 2, 3 rows; level 4 is full to the brim,
    its top row the froth (f4)."""
    fill = {0: 0, 1: 1, 2: 2, 3: 3, 4: 3}[level]
    out = []
    for r in range(3):
        if r >= 3 - fill:
            out.append(('f4' if level == 4 else 'f3') if r == 3 - fill else 'f2')
        else:
            out.append('t4' if r == 0 else 't0')
    return out


def broth_textures():
    paths = []
    for lv in range(5):
        rows = broth_rows(lv)

        def face(fr, side, c, r, W, H, rows=rows):
            if side == 'north':
                return 't0' if (rows[r] == 't4' and c == 1) else rows[r]
            return 't1'
        p = os.path.join(K.OUT, 'cerebral_array_broth_%d.png' % lv)
        paths.append(K.state_sprite(p, 2, 3, 1, face, fill='t1'))
    return paths


def paint(S):
    stand(S)
    jars(S)
    bench(S)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview')
    a = ap.parse_args()
    S = K.Sheet(BBMODEL)
    paint(S)
    glass = [n for n in S.cubes if n.endswith('_glass')]
    miss = S.missing(clear_ok=glass)
    if miss:
        sys.exit('unpainted faces: %s' % miss)
    out = [S.save(SHEET)] + broth_textures()
    for p in out:
        print('wrote', p)
    if a.preview:
        os.makedirs(a.preview, exist_ok=True)
        K.standins_from(S, PLACEHOLDER)
        S.save(os.path.join(a.preview, 'array_rest.png'))
        K.lens_on_sheet(S, 'lamp_lens', 'work')
        S.save(os.path.join(a.preview, 'array_work.png'))
        print('previews in', a.preview)


if __name__ == '__main__':
    main()

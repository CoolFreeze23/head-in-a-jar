"""Neural Socket (an ivory pillar under a dark steel cap; four brass corner clamps grip the real jar): the sheet.

    python -I art/machines/neural_socket/paint/paint_socket.py                # writes the textures into the assets
    python -I art/machines/neural_socket/paint/paint_socket.py --preview DIR  # also DIR/socket_rest.png, _work.png
Then adopt what it prints with tools/tex/tex.py adopt.

Writes, under textures/block/machine/:
  neural_socket.png        128x112, the baked pillar and the two bones (claws, plug)
  neural_socket_level.png  16x16: the emissive level quads the renderer draws in the slots and bands (redstone red:
                           an R2 field, an R3 core column and row so a bar of any size has a lit middle, an R1 rim)

The value plan (110 px first; empty it must not lean "kiosk"):
  LIGHT  the pale skins (e3 field, e4 lip, belt-less: the foot band is its own dark), the posts
  DARK   the slots' backs (the core, r0: black), the ring under the cap (r0), the foot band (r1), the dark steel cap
         with its receptacle well (s1 deck, s2 lit edges, the well's floor r0)
  ACCENT the four brass clamps (b3 field, b4 lit arris and top, b1 foot; the bite shows brass against the dark cap),
         the brass jack in the well, the K3 lamp
"""
import argparse
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'kit', 'paint'))
import kitpaint as K  # noqa: E402

MDIR = os.path.join(HERE, '..')
BBMODEL = os.path.join(MDIR, 'neural_socket.bbmodel')
PLACEHOLDER = os.path.join(MDIR, 'neural_socket_placeholder.png')
SHEET = os.path.join(K.OUT, 'neural_socket.png')
LEVEL = os.path.join(K.OUT, 'neural_socket_level.png')


def paint(S):
    p = 'plinth'
    for f in S.faces(p, K.SIDES):
        f.fill('r1')
        f.row(0, 'r2')
    S.face(p, 'up').fill('r1')
    S.face(p, 'down').fill('r0')
    K.recess(S, 'core', 'r0')
    for n in S.names('core_ring_'):
        K.recess(S, n, 'r0')
    for n in S.names('skin_') + S.names('post_'):
        for f in S.faces(n, K.SIDES):
            for c, r in f.each():
                x, y, z = f.world(c, r)
                f.put(c, r, 'e4' if y > 12 else ('e2' if y < 4 else 'e3'))
        S.face(n, 'up').fill('e4')
        S.face(n, 'down').fill('e1')
    for n in S.names('deck_') + S.names('nose_'):
        for f in S.faces(n, K.SIDES):
            f.fill('s1')
        up = S.face(n, 'up')
        up.by_world(lambda c, r, x, y, z: 's2' if (abs(x) > 7 or abs(z) > 7 or z < -5) else 's1')
        S.face(n, 'down').fill('s0')
    K.lamp_bezel(S)
    K.lens_on_sheet(S, 'lamp_lens', 'ready')
    for n in S.names('claw_'):
        for f in S.faces(n, K.SIDES):
            for c, r in f.each():
                f.put(c, r, 'b4' if ((f.side in ('north', 'west') and c == 0) or r == 0)
                      else ('b1' if r == f.H - 1 else 'b3'))
        S.face(n, 'up').fill('b4')
        S.face(n, 'down').fill('b1')
    K.small(S, 'plug', 'b', 3, top='b4', bottom='b1')
    S.face('plug', 'up').rows(['b4 b4 b4 b4', 'b4 r0 r0 b3', 'b4 r0 r0 b3', 'b3 b3 b3 b2'])   # the jack's socket


def level_texture():
    img = Image.new('RGBA', (16, 16), K.rgba('R2'))
    px = img.load()
    for i in range(16):
        px[i, 0] = px[i, 15] = px[0, i] = px[15, i] = K.rgba('R1')
        px[7, i] = px[8, i] = px[i, 7] = px[i, 8] = K.rgba('R3') if 0 < i < 15 else K.rgba('R1')
    img.save(LEVEL)
    return LEVEL


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview')
    a = ap.parse_args()
    S = K.Sheet(BBMODEL)
    paint(S)
    miss = S.missing()
    if miss:
        sys.exit('unpainted faces: %s' % miss)
    print('wrote', S.save(SHEET), level_texture())
    if a.preview:
        os.makedirs(a.preview, exist_ok=True)
        K.standins_from(S, PLACEHOLDER)
        S.save(os.path.join(a.preview, 'socket_rest.png'))
        K.lens_on_sheet(S, 'lamp_lens', 'work')
        S.save(os.path.join(a.preview, 'socket_work.png'))
        print('previews in', a.preview)


if __name__ == '__main__':
    main()

"""Safety Placard (a framed enamel notice on the wall): the painted sheet for the frame, the backplate and the board.

    python -I art/machines/safety_placard/paint/paint_placard.py                # writes block/machine/safety_placard.png
    python -I art/machines/safety_placard/paint/paint_placard.py --preview DIR  # also DIR/placard_rest.png
Then: python -I tools/tex/tex.py adopt block/machine/safety_placard.png

One sheet, 128x48. The board's front face takes `#notice` (the 48x48 notices drawn by ../placard_text.py, whose colours
are now palette D: ink r1, paper e3, header w2, screws s3/s5/s1); every other face is this sheet.

The value plan: a quiet brass frame (b3 field; the top strip's front b4, the bottom strip's b2; its returns to the wall
b2; its faces round the board b1: darks round the brass), the dark gap (the backplate, s1) between frame and board, the
board's edges in enamel e3 (the notice's paper), its front on the sheet e3 with a w2 header band (seen only where the
notice is not applied). The header's warning yellow (w2, hue about 45) stays apart from the
frame's brass (b3, hue about 36, a step darker) by hue and by the dark gap between them.
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'kit', 'paint'))
import kitpaint as K  # noqa: E402

MDIR = os.path.join(HERE, '..')
BBMODEL = os.path.join(MDIR, 'safety_placard.bbmodel')
PLACEHOLDER = os.path.join(MDIR, 'safety_placard_placeholder.png')
SHEET = os.path.join(K.OUT, 'safety_placard.png')


def paint(S):
    for n in S.names('frame_'):
        tag = n[-1]
        K.fill(S, n, 'b2')
        S.face(n, 'north').fill({'t': 'b4', 'b': 'b2'}.get(tag, 'b3'))
        inner = {'t': 'down', 'b': 'up', 'l': 'east', 'r': 'west'}[tag]
        S.face(n, inner).fill('b1')
        outer = {'t': 'up', 'b': 'down', 'l': 'west', 'r': 'east'}[tag]
        S.face(n, outer).fill('b3' if tag == 't' else 'b2')
    S.face('frame_t', 'north').put(0, 0, 'b5')
    K.fill(S, 'backplate', 's1')
    S.face('backplate', 'north').fill('s1')
    b = 'board'
    K.fill(S, b, 'e2')
    f = S.face(b, 'north')
    for c, r in f.each():
        f.put(c, r, 'w2' if r < 2 else ('r1' if r == 2 else 'e3'))
    S.face(b, 'up').fill('e3')


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
        S.save(os.path.join(a.preview, 'placard_rest.png'))
        print('previews in', a.preview)


if __name__ == '__main__':
    main()

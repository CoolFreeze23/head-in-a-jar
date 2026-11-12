"""Body Locker, the Cloakroom (a theatre cloakroom kiosk): the painted sheet, every pixel placed from palette keys.

    python -I art/machines/body_locker/paint/paint_locker.py                # writes block/machine/body_locker.png
    python -I art/machines/body_locker/paint/paint_locker.py --preview DIR  # also DIR/locker_rest.png, _work.png
Then: python -I tools/tex/tex.py adopt block/machine/body_locker.png

One sheet, 128x128, for the baked kiosk and the two bones (shutter, ticket). The portrait is drawn by the game.

The value plan (110 px first):
  LIGHT  the ivory counter (K2 faces, its rimmed front panel) and booth (cheeks, header, back), the staff door
  DARK   the back room through the 8x8 window (r0 walls and floor) with one brass coat-hanger on a brass rail, light
         against it; the empty portrait field (r1) inside its brass frame; the plinth and feet; the shutter box (s2)
  ACCENT the brass countertop and desk bell; the brass slatted shutter (slats b4 over a b2 back: light against the
         dark window when it rolls down); the teal awning (t2, a t3 lit edge, t1 underside row); the ticket: a cream
         card (e5) with a red number band, on a brass ring, light against the brass hook and the ivory cheek
The ticket's "No. 27": a 2x4 face cannot hold two digits (the narrowest legible pair is 7x4); it carries a red
number band (R1 marks) instead, and the number belongs in the tooltip or the line the player gets.
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'kit', 'paint'))
import kitpaint as K  # noqa: E402

MDIR = os.path.join(HERE, '..')
BBMODEL = os.path.join(MDIR, 'body_locker.bbmodel')
PLACEHOLDER = os.path.join(MDIR, 'body_locker_placeholder.png')
SHEET = os.path.join(K.OUT, 'body_locker.png')


def counter(S):
    K.plinth(S)
    K.feet(S)
    c = 'counter'
    for f in S.faces(c, K.SIDES):
        K.enamel_body(f, 3, 12)
    S.face(c, 'up').fill('e2')
    S.face(c, 'down').fill('e0')
    for n in S.names('counter_rim_'):
        K.fill(S, n, 'e3')
        S.face(n, 'up').fill('e4')
        S.face(n, 'down').fill('e1')
    K.lamp_bezel(S)
    K.lens_on_sheet(S, 'lamp_lens', 'ready')
    K.makers_plate(S, 'makers_plate', 'north')
    K.brass_plate(S, 'countertop')
    K.small(S, 'bell_base', 's', 3)
    K.small(S, 'bell_dome', 'b', 4, top='b5', bottom='b2')


def booth(S):
    for n in ('booth_back', 'cheek_l', 'cheek_r', 'header'):
        for f in S.faces(n, K.SIDES):
            K.enamel_body(f, 13, 32, bead=False)
        K.enamel_top(S.face(n, 'up'))
        S.face(n, 'down').fill('e1')
    # the window's reveal: the left-hand wall (cheek_r's west face, as seen) in shadow, the right-hand one lit
    S.face('cheek_r', 'west').fill('e1')
    S.face('cheek_l', 'east').fill('e3')
    S.face('header', 'down').fill('e0')
    # the empty portrait field inside the frame: dark until the game draws the card
    f = S.face('header', 'north')
    f.by_world(lambda c, r, x, y, z: 'r1' if (-3 < x < 3 and 26 < y < 30) else None)
    for n in S.names('portrait_'):
        tag = n[-1]
        for g in S.faces(n, K.SIDES):
            g.fill({'t': 'b4', 'b': 'b2'}.get(tag, 'b3'))
        S.face(n, 'up').fill('b4')
        S.face(n, 'down').fill('b1')
    # the staff door on the back: e2, a lit top row, a hinge seam (e1), the brass knob
    d = 'staff_door'
    K.fill(S, d, 'e1')
    g = S.face(d, 'south')
    for c, r in g.each():
        g.put(c, r, 'e3' if r == 0 else ('e1' if c == 0 else 'e2'))
    K.small(S, 'door_knob', 'b', 4, top='b5', bottom='b2')
    # the back room: the darkest, a brass rail and one coat-hanger light against it
    for n in ('room_floor', 'room_back'):
        K.recess(S, n, 'r0')
    K.small(S, 'rail', 'b', 3, top='b4', bottom='b1')
    K.small(S, 'hanger_neck', 'b', 4, top='b5', bottom='b2')
    hs = 'hanger_shoulders'
    K.small(S, hs, 'b', 3, top='b4', bottom='b1')
    S.face(hs, 'north').rows(['b2 b3 b4 b4 b3 b2', 'b1 b1 b1 b1 b1 b1'])     # the hanger's arms sloping down
    K.small(S, 'hook', 'b', 3, top='b4', bottom='b1')
    # the shutter box and the teal awning
    K.steel_part(S, 'shutter_box', field='s2', lit='s3', under='s1')
    a = 'awning'
    for f in S.faces(a, K.SIDES):
        for c, r in f.each():
            f.put(c, r, 't3' if r == 0 else 't1')
    up = S.face(a, 'up')
    up.by_world(lambda c, r, x, y, z: 't3' if z < -5 else 't2')
    S.face(a, 'down').fill('t0')


def moving(S):
    # the shutter: slats lit (b4, b5 top, b1 underside) over a darker back (b2): light against the dark window
    sb = 'shutter_back'
    K.fill(S, sb, 'b2')
    S.face(sb, 'up').fill('b3')
    for n in S.names('slat_'):
        K.small(S, n, 'b', 4, top='b5', bottom='b1')
    # the ticket: a cream card with a red number band on a brass ring
    K.small(S, 'ticket_ring', 'b', 3, top='b4', bottom='b1')
    t = 'ticket_tag'
    K.fill(S, t, 'e4')
    for side in ('north', 'south'):
        S.face(t, side).rows(['e5 e5', 'R1 e5', 'R1 R1', 'e5 e4'])
    S.face(t, 'up').fill('e5')


def paint(S):
    counter(S)
    booth(S)
    moving(S)


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
        S.save(os.path.join(a.preview, 'locker_rest.png'))
        K.lens_on_sheet(S, 'lamp_lens', 'work')
        S.save(os.path.join(a.preview, 'locker_work.png'))
        print('previews in', a.preview)


if __name__ == '__main__':
    main()

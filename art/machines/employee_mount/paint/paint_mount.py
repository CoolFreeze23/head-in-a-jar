"""Employee Mount (the office cubicle): the painted sheet and the banker's lamp's six shade sprites.

    python -I art/machines/employee_mount/paint/paint_mount.py                # writes the textures into the assets
    python -I art/machines/employee_mount/paint/paint_mount.py --preview DIR  # also DIR/mount_rest.png, _work.png
Then adopt what it prints with tools/tex/tex.py adopt.

Writes, under textures/block/machine/:
  employee_mount.png                 128x112, the baked cubicle and the two bones (receiver, stamp)
  employee_mount_lamp_<state>.png    the banker's lamp shade (the lamp lens element, 4x2x4), one per `lamp` state:
                                     off, ready, wait, fault; work and low animated (.mcmeta, 2 frames x 20 ticks);
                                     the shade's box-UV at (0, 0) of a 16x16 sprite

The value plan (110 px first):
  DARK   the felt partition: a quiet woven texture of two dark steps of near equal value (teal t0 and rubber r2), so
         the ivory handset reads against it when it jumps to the ear; the black phone; the jar's swivel foot
  LIGHT  the ivory partition frame (e2 field, e3 lip) and its back (a T9 pressed panel), the ivory handset, the memo
  ACCENT the brass cap rail, nameplate and jar ring and lid; the polished wood desk (b1, b2 edge: an office desk);
         the green banker's lamp shade; the copper pay tray
Story marks, once each: the memo pinned with a red pin; the rotary dial on the phone; the inked stamp face; the
nameplate's engraved name.
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'kit', 'paint'))
import kitpaint as K  # noqa: E402

MDIR = os.path.join(HERE, '..')
BBMODEL = os.path.join(MDIR, 'employee_mount.bbmodel')
PLACEHOLDER = os.path.join(MDIR, 'employee_mount_placeholder.png')
SHEET = os.path.join(K.OUT, 'employee_mount.png')


def partition(S):
    for n in ('frame_l', 'frame_r', 'frame_b', 'frame_t'):
        for f in S.faces(n, K.SIDES):
            K.enamel_body(f, 1, 18, bead=False)
        K.enamel_top(S.face(n, 'up'))
        S.face(n, 'down').fill('e1')
    bb = 'back_board'
    for f in S.faces(bb, K.SIDES):
        f.fill('e2')
    f = S.face(bb, 'south')
    K.enamel_inset(f, 1, 1, f.W - 1, f.H - 1)
    S.face(bb, 'up').fill('e3')
    S.face(bb, 'down').fill('e1')
    felt = 'felt'
    K.fill(S, felt, 'r2')
    K.weave(S.face(felt, 'north'))
    K.recess(S, 'kick', 'r0')
    # the brass cap rail
    for f in S.faces('rail', K.SIDES):
        K.brass_rail(f)
    K.brass_top(S.face('rail', 'up'), band=False)
    S.face('rail', 'down').fill('b1')
    # the memo: pale paper (e5) with two written lines (s3) and a red pin (R1) at its top centre
    m = 'memo'
    K.fill(S, m, 'e4')
    S.face(m, 'north').rows(['e5 R1 e5', 's3 s3 e5', 'e5 s3 s3'])


def desk(S):
    d = 'desk'
    for f in S.faces(d, K.SIDES):
        K.wood(f)
    K.wood_top(S.face(d, 'up'))
    S.face(d, 'down').fill('b0')
    for n in ('bracket_l', 'bracket_r'):
        K.small(S, n, 's', 3)
    K.makers_plate(S, 'nameplate', 'north')
    # the jar (K13 size 8): a dark swivel foot, brass ring and lid, clear glass (a frame and one glint)
    K.small(S, 'jar_swivel', 's', 1, top='s2', bottom='s0')
    K.small(S, 'jar_ring', 'b', 3, top='b4', bottom='b1')
    K.brass_plate(S, 'jar_lid')
    for f in S.faces('jar_glass'):
        K.clear_glass(f, streaks=False)
        if f.side == 'north':
            f.put(1, 2, 'g3')
            f.put(2, 1, 'g3')
    # the phone: black, a pale 2x2 rotary dial on its front (e3, its lower right e2), lit top row
    ph = 'phone'
    for f in S.faces(ph, K.SIDES):
        f.fill('r1')
        f.row(0, 'r2')
    S.face(ph, 'north').rows(['r2 r2 r2 r2', 'r1 e3 e3 r1', 'r1 e3 e2 r0'])
    S.face(ph, 'up').fill('r2')
    S.face(ph, 'down').fill('r0')
    # the pay tray: copper, a lit rim (c6) round a darker well (c3)
    t = 'tray'
    for f in S.faces(t, K.SIDES):
        f.fill('c4')
    up = S.face(t, 'up')
    for c, r in up.each():
        up.put(c, r, 'c6' if (r == 0 or c == 0) else ('c2' if r == up.H - 1 or c == up.W - 1 else 'c3'))
    S.face(t, 'down').fill('c1')
    # the banker's lamp: a brass stem, the shade on the sheet shown `ready`
    K.small(S, 'lamp_stem', 'b', 3, top='b4', bottom='b1')
    shade_on_sheet(S, 'ready')


def shade_on_sheet(S, state):
    keys = {'ready': ('G1', 'G2', 'G1', 'G3'), 'work': ('A1', 'A2', 'A1', 'A3'), 'off': ('j1', 'j2', 'j2', 'r1')}[state]
    fld, top_row, top, under = keys
    for f in S.faces('lamp_lens', K.SIDES):
        for c, r in f.each():
            f.put(c, r, top_row if r == 0 else fld)
    up = S.face('lamp_lens', 'up')
    for c, r in up.each():
        up.put(c, r, top_row if (r == 0 or c == 0) else top)
    S.face('lamp_lens', 'down').fill(under)


def moving(S):
    # the handset: ivory, its two cups with a dark centre, the grip one tone
    for n in ('receiver_ear', 'receiver_mouth'):
        K.small(S, n, 'e', 4, top='e5', bottom='e3')
        S.face(n, 'north').rows(['e4 e3', 'e3 r1'])
    K.small(S, 'receiver_grip', 'e', 4, top='e5', bottom='e3')
    # the stamp: a rubber die inked yellow underneath, a copper neck, a brass knob
    K.small(S, 'stamp_die', 'r', 1)
    S.face('stamp_die', 'down').fill('w1')
    K.small(S, 'stamp_neck', 'c', 4, top='c6', bottom='c2')
    K.brass_housing(S, 'stamp_knob')


def paint(S):
    partition(S)
    desk(S)
    moving(S)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview')
    a = ap.parse_args()
    S = K.Sheet(BBMODEL)
    paint(S)
    miss = S.missing(clear_ok=('jar_glass',))
    if miss:
        sys.exit('unpainted faces: %s' % miss)
    out = [S.save(SHEET)] + K.shade_lamp_textures('employee_mount_lamp')
    for p in out:
        print('wrote', p)
    if a.preview:
        os.makedirs(a.preview, exist_ok=True)
        K.standins_from(S, PLACEHOLDER)
        S.save(os.path.join(a.preview, 'mount_rest.png'))
        shade_on_sheet(S, 'work')
        S.save(os.path.join(a.preview, 'mount_work.png'))
        print('previews in', a.preview)


if __name__ == '__main__':
    main()

"""Turret Pedestal (a searchlight-style gun mount carrying the real jar at 1:1): the painted sheet.

    python -I art/machines/turret_pedestal/paint/paint_turret.py                # writes the textures into the assets
    python -I art/machines/turret_pedestal/paint/paint_turret.py --preview DIR  # also DIR/turret_rest.png, _work.png
Then: python -I tools/tex/tex.py adopt block/machine/turret_pedestal.png

One sheet, 128x144, for the baked base and the three bones (yaw, pitch, recoil). The jar is the game's (JarRenderer
at 1:1); the stand-in jar is never exported.

The value plan (110 px first). The jar is the subject; the mount frames it quietly:
  DARK   the turntable and the fork cheeks (s1, s2 lit rows), the plinth and feet, the muzzle
  MID    the steel base drum (s3 field, s4 lit row: a K2 crossed drum in steel), the nameplate on its back, the lamp on
         its right flank
  ACCENT the brass: the tray and the foot band (the jar's frame: plain b3 with b4 lit rows, no polish band), the pin
         caps (b4 with a b5 glint), the lugs, the barrel (b3, b4 top row, b2 underside row)
The muzzle, not an eye: the collar round the bore is blackened steel. From the front it is a 1 px rim (s2 top, s1)
round a 4x4 sooty mouth (s0) with the 2x2 bore (r0) deep in it: one big dark hole, not a ring round a pupil. A dark
port slit (r0) on each side face reads as a muzzle brake.
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'kit', 'paint'))
import kitpaint as K  # noqa: E402

MDIR = os.path.join(HERE, '..')
BBMODEL = os.path.join(MDIR, 'turret_pedestal.bbmodel')
PLACEHOLDER = os.path.join(MDIR, 'turret_pedestal_placeholder.png')
SHEET = os.path.join(K.OUT, 'turret_pedestal.png')


def paint(S):
    K.plinth(S)
    K.feet(S)
    for n in ('base_a', 'base_b'):
        K.steel_part(S, n, field='s3', lit='s4', under='s1')
        for f in S.faces(n, K.SIDES):
            f.row(f.H - 1, 's2')
    K.lamp_bezel(S, face='east')
    K.lens_on_sheet(S, 'lamp_lens', 'ready')
    # the kit lens painter is written for a north-facing 2x2x1 lens; this one faces east (1x2x2): paint its east face
    S.face('lamp_lens', 'east').rows(['G3 G2', 'G2 G1'])
    K.makers_plate(S, 'nameplate', 'south')
    for n in ('turn_a', 'turn_b', 'cheek_l', 'cheek_r'):
        K.dark_steel(S, n)
    for n in ('pin_l', 'pin_r'):
        K.small(S, n, 'b', 4, top='b5', bottom='b2')
        S.face(n, 'west' if n == 'pin_l' else 'east').rows(['b5 b4', 'b4 b3'])
    for n in ('lug_l', 'lug_r'):
        K.small(S, n, 'b', 3, top='b4', bottom='b1')
    # the jar's frame: quiet brass
    for f in S.faces('tray', K.SIDES):
        f.fill('b3')
    K.brass_top(S.face('tray', 'up'), band=False)
    S.face('tray', 'down').fill('b1')
    for n in S.names('band_'):
        for f in S.faces(n, K.SIDES):
            K.brass_rail(f)
        S.face(n, 'up').fill('b4')
        S.face(n, 'down').fill('b1')
    # the barrel
    for f in S.faces('barrel', K.SIDES):
        for c, r in f.each():
            f.put(c, r, 'b4' if r == 0 else ('b2' if r == f.H - 1 else 'b3'))
    S.face('barrel', 'up').fill('b4')
    S.face('barrel', 'down').fill('b1')
    # the muzzle: blackened steel, sooty round the bore, port slits on its sides
    for n in S.names('collar_'):
        for f in S.faces(n):
            f.fill('s1')
        S.face(n, 'up').fill('s2')
        S.face(n, 'down').fill('s0')
        S.face(n, 'north').fill('s1')
    # seen from the front: a 1 px rim (s2 on top, s1 round the rest) round a 4x4 sooty mouth (s0) with the 2x2 bore
    # (r0) deep in it: one dark hole, not a ring round a pupil
    S.face('collar_t', 'north').rows(['s2 s2 s2 s2 s2 s2', 's1 s0 s0 s0 s0 s1'])
    S.face('collar_b', 'north').rows(['s1 s0 s0 s0 s0 s1', 's1 s1 s1 s1 s1 s1'])
    S.face('collar_l', 'north').rows(['s0 s1', 's0 s1'])
    S.face('collar_r', 'north').rows(['s1 s0', 's1 s0'])
    for n, side in (('collar_l', 'west'), ('collar_r', 'east')):
        S.face(n, side).put(0, 0, 'r0')
        S.face(n, side).put(1, 0, 'r0')
    K.recess(S, 'bore', 'r0')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview')
    a = ap.parse_args()
    S = K.Sheet(BBMODEL)
    paint(S)
    miss = S.missing(skip=('si_', 'sj_'))
    if miss:
        sys.exit('unpainted faces: %s' % miss)
    print('wrote', S.save(SHEET))
    if a.preview:
        os.makedirs(a.preview, exist_ok=True)
        K.standins_from(S, PLACEHOLDER)
        K.standins_from(S, PLACEHOLDER, prefix='sj_')
        S.save(os.path.join(a.preview, 'turret_rest.png'))
        K.lens_on_sheet(S, 'lamp_lens', 'work')
        S.save(os.path.join(a.preview, 'turret_work.png'))
        print('previews in', a.preview)


if __name__ == '__main__':
    main()

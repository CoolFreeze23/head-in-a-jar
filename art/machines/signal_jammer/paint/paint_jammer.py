"""Signal Jammer (a rooftop whirlybird ventilator): the painted sheet and the grille's two state sprites.

    python -I art/machines/signal_jammer/paint/paint_jammer.py                # writes the textures into the assets
    python -I art/machines/signal_jammer/paint/paint_jammer.py --preview DIR  # also DIR/jammer_rest.png, _work.png
Then adopt what it prints with tools/tex/tex.py adopt.

Writes, under textures/block/machine/:
  signal_jammer.png              128x80, the baked drum, band and whips and the `rotor` bone
  signal_jammer_grille_idle.png  the grille (element 3x4x1): dark louvre slots, still
  signal_jammer_grille_jam.png   the same while jamming: a few placed flecks of static (e4, A2) in the slots, a
                                 different few each frame, 4 frames x 3 ticks (.mcmeta); never random, never a flash
                                 (6 of 12 front pixels at most change, all small)

The value plan (110 px first):
  LIGHT  the squat ivory drum (K2 faces), its raised front rim and the louvre slats
  DARK   the plinth and feet, the grille's slots (r0), the turbine's core and ring (s0-s1)
  ACCENT the plain warning band (w2, a w3 lit top row, an r1 dark foot edge: it does not move, so no stripes);
         the turbine's eight blades: seven quiet and alike (s3, s4 tops), ONE plain light warning yellow (w3, both
         steps), the only thing that shows the true spin; the brass whips with bright tips; the brass cap
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'kit', 'paint'))
import kitpaint as K  # noqa: E402

MDIR = os.path.join(HERE, '..')
BBMODEL = os.path.join(MDIR, 'signal_jammer.bbmodel')
PLACEHOLDER = os.path.join(MDIR, 'signal_jammer_placeholder.png')
SHEET = os.path.join(K.OUT, 'signal_jammer.png')

GRILLE = ['r1 r1 r1', 'r0 r0 r0', 'r1 r1 r1', 'r0 r0 r0']          # the slots behind the two slats
STATIC = [[(0, 1), (2, 3)], [(1, 1)], [(2, 1), (0, 3)], [(1, 3)]]  # placed flecks, a frame each
FLECK = ['e4', 'A2', 'e4', 'A2']


def paint(S):
    K.plinth(S)
    K.feet(S)
    for n in ('drum_a', 'drum_b'):
        for f in S.faces(n, K.SIDES):
            K.enamel_body(f, 3, 9, bead=False)
        K.enamel_top(S.face(n, 'up'))
        S.face(n, 'down').fill('e0')
    for n in S.names('drum_rim_'):
        K.fill(S, n, 'e3')
        S.face(n, 'up').fill('e4')
        S.face(n, 'down').fill('e1')
    g = 'grille'
    K.fill(S, g, 'r0')
    S.face(g, 'north').rows(GRILLE)
    for n in ('slat_1', 'slat_2'):
        K.small(S, n, 'e', 3, top='e4', bottom='e1')
    K.lamp_bezel(S)
    K.lens_on_sheet(S, 'lamp_lens', 'ready')
    K.makers_plate(S, 'makers_plate', 'east')
    h = 'back_hatch'
    K.fill(S, h, 'e1')
    f = S.face(h, 'south')
    f.fill('e2')
    f.row(0, 'e3')
    f.put(f.W - 2, 2, 'b4')                      # its brass catch
    # the warning band does not move, so it is a plain band, not stripes (the house rule keeps stripes for moving
    # faces; a static striped band shouted over the whole row): warning yellow, its top row lit, its foot a dark edge
    for f in S.faces('band', K.SIDES):
        for c, r in f.each():
            f.put(c, r, 'w3' if r == 0 else ('r1' if r == f.H - 1 else 'w2'))
    S.face('band', 'up').fill('s1')
    S.face('band', 'down').fill('s0')
    for n in ('antenna_l', 'antenna_r', 'antenna_r_top'):
        K.brass_post(S, n, foot=False)
    K.small(S, 'antenna_l_tip', 'b', 4, top='b5', bottom='b2')
    S.face('antenna_r_top', 'up').fill('b5')
    # the turbine
    K.small(S, 'rotor_ring', 's', 1, top='s1', bottom='s0')
    K.small(S, 'core', 's', 0, top='s1', bottom='s0')
    for n in S.names('fin_'):
        if n in ('fin_e', 'fin_e_up'):
            # the marker blade: plain light warning yellow, the one pale thing on the dark turbine, so the turn reads as
            # a part going round from ten blocks (stripes average to a mid tone there and vanish)
            K.small(S, n, 'w', 3, top='w3', bottom='w2')
        else:
            K.small(S, n, 's', 3, top='s4', bottom='s2')
    K.small(S, 'cap', 'b', 3, top='b4', bottom='b1')
    S.face('cap', 'up').put(0, 0, 'b5')


def grille_textures():
    def still(fr, side, c, r, W, H):
        return GRILLE[r].split()[c] if side == 'north' else 'r0'

    def jam(fr, side, c, r, W, H):
        if side != 'north':
            return 'r0'
        if (c, r) in STATIC[fr]:
            return FLECK[fr]
        return GRILLE[r].split()[c]
    out = [K.state_sprite(os.path.join(K.OUT, 'signal_jammer_grille_idle.png'), 3, 4, 1, still, fill='r0')]
    p = os.path.join(K.OUT, 'signal_jammer_grille_jam.png')
    out += [K.state_sprite(p, 3, 4, 1, jam, fill='r0', frames=4, frametime=3), p + '.mcmeta']
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview')
    a = ap.parse_args()
    S = K.Sheet(BBMODEL)
    paint(S)
    miss = S.missing()
    if miss:
        sys.exit('unpainted faces: %s' % miss)
    out = [S.save(SHEET)] + grille_textures()
    for p in out:
        print('wrote', p)
    if a.preview:
        os.makedirs(a.preview, exist_ok=True)
        K.standins_from(S, PLACEHOLDER)
        S.save(os.path.join(a.preview, 'jammer_rest.png'))
        K.lens_on_sheet(S, 'lamp_lens', 'work')
        S.save(os.path.join(a.preview, 'jammer_work.png'))
        print('previews in', a.preview)


if __name__ == '__main__':
    main()

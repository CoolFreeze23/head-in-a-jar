"""Centrifuge (the Chair-o-plane): the painted sheet and the balance sign, every pixel placed from palette keys.

    python -I art/machines/centrifuge/paint/paint_centrifuge.py                # writes the textures into the mod assets
    python -I art/machines/centrifuge/paint/paint_centrifuge.py --preview DIR  # also DIR/centrifuge_rest.png, _work.png
Then: python -I tools/tex/tex.py adopt block/machine/centrifuge.png block/machine/centrifuge_balance.png

Writes, under textures/block/machine/:
  centrifuge.png          128x144, the baked model (bench, drum, rack, post, canopy) and the GeckoLib bones (rotor,
                          hangers, drawer, flag)
  centrifuge_balance.png  16x16: the "PLEASE BALANCE ROTOR" plate the renderer draws as a 6x2 quad; the plate is the
                          sprite's top-left 6x2 (uv 0,0 to 6,2 in a 16-unit sprite), the rest filled with its field

The value plan (110 px first):
  LIGHT  the canopy (the one big shape): 2 px vertical bands of warning yellow (w2 field, w3 lip, w1 foot) and enamel
         (e3 field, e4 lip, e2 foot) round an e3 deck; the enamel cabinet ends of the bench (e2 with a T9 inset)
  DARK   the black worktop (r1, r3 lip), the open shelf (s0-s1, r0 back), the dark pole (s1 with an s2 arris), the
         canopy's rubber ceiling and gutter (r0), the kick (r0)
  ACCENT the plum samples (f1, f2 meniscus) under pale glass necks (g2, opaque); the brass spider and rack; one copper ring
         (the marked hanger, so the turn reads at low speed)
Story marks, once each: the hand-polished glint on the drawer pull; a scuffed lip on the worktop's front edge where
people lean; the feed throat in the canopy deck.
"""
import argparse
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'kit', 'paint'))
import kitpaint as K  # noqa: E402

MDIR = os.path.join(HERE, '..')
BBMODEL = os.path.join(MDIR, 'centrifuge.bbmodel')
PLACEHOLDER = os.path.join(MDIR, 'centrifuge_placeholder.png')
SHEET = os.path.join(K.OUT, 'centrifuge.png')
SIGN = os.path.join(K.OUT, 'centrifuge_balance.png')

# the sign: a yellow plate with three engraved words (r1 marks); 6x2 cannot hold letters, it reads as lettering
SIGN_ROWS = ['w3 r1 w3 r1 r1 w3',
             'w2 r1 r1 w2 r1 w2']


def band_box(S, n, yellow, inner):
    """One 2 px band of the valance (a box 4 tall). Its outer face: yellow w3 lip, w2 field, w1 foot (enamel e4, e3,
    e2): the plain band, since the canopy does not move. The inner face (towards the pole) in shadow, step 1; top
    the lip step, underside step 1."""
    m, lip, field, foot, shade = ('w', 'w3', 'w2', 'w1', 'w1') if yellow else ('e', 'e4', 'e3', 'e2', 'e1')
    for f in S.faces(n, K.SIDES):
        if f.side == inner:
            f.fill(shade)
        else:
            K.plain_band(f, field, lip, foot)
    S.face(n, 'up').fill(lip)
    S.face(n, 'down').fill(shade)


def canopy(S):
    for n in S.names('canopy_'):
        if n.startswith(('canopy_ceiling', 'canopy_deck')):
            continue
        side = n.split('_')[1]
        inner = {'n': 'south', 's': 'north', 'w': 'east', 'e': 'west'}.get(side[0], None)
        if side[0] == 'c':          # the stepped corner pieces: yellow, no inner face to speak of
            inner = None
            yellow = True
        else:
            yellow = int(n.split('_')[2]) % 2 == 0
        band_box(S, n, yellow, inner)
    for n in S.names('canopy_ceiling'):
        K.recess(S, n, 'r0')
    # the deck: e3 with e4 lit edges; in its centre the feed throat, a 2x2 r0 hole in a 4x4 brass ring
    for n in S.names('canopy_deck'):
        for f in S.faces(n, K.SIDES):
            f.fill('e2')
        S.face(n, 'down').fill('e1')

        def deck(c, r, x, y, z):
            if -1 < x < 1 and -1 < z < 1:
                return 'r0'
            if -2 < x < 2 and -2 < z < 2:
                return 'b4' if (z < -1 or x > 1) else 'b3'
            return 'e4' if (z < -4 or x > 4 or (z < -2 and abs(x) > 2) or (x > 2 and abs(z) > 2)) else 'e3'
        S.face(n, 'up').by_world(deck)


def rotor(S):
    K.brass_housing(S, 'hub')
    for n in ('arm_n', 'arm_s', 'arm_e', 'arm_w'):
        K.small(S, n, 'b', 3, top='b4', bottom='b1')
    for h in ('n', 'e', 's', 'w'):
        b = 'hanger_' + h
        K.small(S, b + '_link', 's', 3)
        if h == 'n':
            K.small(S, b + '_ring', 'c', 4, top='c6', bottom='c1')
        else:
            K.small(S, b + '_ring', 's', 3)
        # the tube: a pale clear-glass neck (opaque, so the renderer can skip it per instance) over the plum sample
        K.small(S, b + '_glass', 'g', 2, top='g3', bottom='g1')
        K.small(S, b + '_sample', 'f', 1, top='f2', bottom='f0')
        for f in S.faces(b + '_sample', K.SIDES):
            f.row(0, 'f2')            # the sample's meniscus under the glass
        S.face(b + '_glass', 'north').put(0, 0, 'g3')


def paint(S):
    K.bench(S)
    # the black worktop's story mark: a scuffed front lip where people lean (2 px r3 -> r2 worn on the top's front edge)
    up = S.face('bench_worktop', 'up')
    for c, r in up.each():
        x, y, z = up.world(c, r)
        if z < -7 and -2 < x < 1:
            up.put(c, r, 'r3')
    K.drawer(S, 'drawer_box', ('drawer_pull', 'drawer_pull_2'))
    K.lamp_bezel(S)
    K.lens_on_sheet(S, 'lamp_lens', 'ready')
    K.flag(S, 'flag')
    # on the worktop: the steel drum (four bolts on its ring), the motor housing, the brass rack with four holes
    for n in ('drum_a', 'drum_b'):
        K.steel_part(S, n, field='s2', lit='s3', under='s1')
        u = S.face(n, 'up')

        def drum(c, r, x, y, z):
            if abs(x) == 3.5 and abs(z) == 3.5:
                return 's4'                      # four bolt heads on the ring round the housing
            return 's3' if (z < -4 or x > 4) else 's2'
        u.by_world(drum)
    K.steel_part(S, 'housing', field='s2', lit='s3', under='s1')
    S.face('housing', 'up').fill('s2')
    r = 'rack'
    for f in S.faces(r, K.SIDES):
        f.fill('b3')
    S.face(r, 'down').fill('b1')

    def rack(c, rr, x, y, z):
        if z < -6:
            return 'b4'
        if any(a < x < a + 2 for a in (-6, -3, 1, 4)):
            return 'r0'
        return 'b3'
    S.face(r, 'up').by_world(rack)
    K.pole(S, 'post', streaks=[('north', 2, 3, 4), ('west', 1, 6, 3)])
    canopy(S)
    rotor(S)


def sign(path):
    img = Image.new('RGBA', (16, 16), K.rgba('w2'))
    px = img.load()
    for r, row in enumerate(SIGN_ROWS):
        for c, k in enumerate(row.split()):
            px[c, r] = K.rgba(k)
    img.save(path)
    return path


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview')
    a = ap.parse_args()
    S = K.Sheet(BBMODEL)
    paint(S)
    miss = S.missing()
    if miss:
        sys.exit('unpainted faces: %s' % miss)
    print('wrote', S.save(SHEET), sign(SIGN))
    if a.preview:
        os.makedirs(a.preview, exist_ok=True)
        K.standins_from(S, PLACEHOLDER)
        # the balance plates' stand-ins carry the sign in review renders
        for n in ('si_balance_front', 'si_balance_back'):
            f = S.face(n, 'north' if n.endswith('front') else 'south')
            f.rows(SIGN_ROWS)
        S.save(os.path.join(a.preview, 'centrifuge_rest.png'))
        K.lens_on_sheet(S, 'lamp_lens', 'work')
        S.save(os.path.join(a.preview, 'centrifuge_work.png'))
        print('previews in', a.preview)


if __name__ == '__main__':
    main()

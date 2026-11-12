"""The two relay masts' shared paint (a family: the same materials, told apart by shape). Imported by
paint_mast.py (Mk I) and ../relay_mast_mk2/paint/paint_mk2.py (Mk II).

The dish's turn is read at up to 96 blocks as its face flipping light to dark, so the front and the back are as far
apart in value as the palette allows:
  FRONT  the bowl e5 (the lightest enamel, L about 248) with one e4 ring inside the rim (the concave edge), the brass
         rim's front faces b4 (b5 at the top course), the feed horn s5 on an s4 arm
  BACK   s0 (the darkest steel, L about 45): the back plate, the can, the collar and yoke, the bowl's and the rim's
         back faces (the rim's back b1, the brass in its own shade)
The aviation lamp: red, steady (M15), a 2x2x2 lens on a brass cap; lit R2 with an R3 glint and an R1 foot.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'kit', 'paint'))
import kitpaint as K  # noqa: E402

BEACON = {'lit': ('R2', 'R3', 'R1'), 'off': ('R0', 'R1', 'R0')}


def dish(S):
    for n in S.names('rim_'):
        for f in S.faces(n, K.SIDES):
            f.fill('b3')
        S.face(n, 'north').fill('b5' if n == 'rim_t' else 'b4')
        S.face(n, 'south').fill('b1')
        S.face(n, 'up').fill('b4')
        S.face(n, 'down').fill('b2')
    b = 'bowl'
    for f in S.faces(b, K.SIDES):
        f.fill('s0')
    f = S.face(b, 'north')
    for c, r in f.each():
        edge = c in (0, f.W - 1) or r in (0, f.H - 1)
        f.put(c, r, 'e4' if edge else 'e5')
    S.face(b, 'south').fill('s0')
    S.face(b, 'up').fill('s0')
    S.face(b, 'down').fill('s0')
    for n in ('back_plate', 'back_can', 'collar', 'yoke'):
        for f in S.faces(n):
            f.fill('s0')
        S.face(n, 'up').fill('s1')
    K.small(S, 'feed_arm', 's', 4, top='s5', bottom='s3')
    K.small(S, 'feed_horn', 's', 5, top='s5', bottom='s4')
    S.face('feed_horn', 'north').rows(['s4 s4 s4', 's4 s1 s4', 's4 s4 s3'])     # the horn's mouth


def beacon(S, cube='beacon_lens', state='lit'):
    fld, glint, foot = BEACON[state]
    for f in S.faces(cube):
        f.fill(fld)
        f.put(0, 0, glint)
        if f.H >= 2:
            f.row(f.H - 1, foot)
    S.face(cube, 'up').fill(glint)
    S.face(cube, 'down').fill(foot)


def beacon_textures(prefix):
    """machine/<prefix>_beacon_lit.png and _off.png: the 2x2x2 lens's own box-UV at (0, 0) of a 16x16 sprite."""
    out = []
    for state, (fld, glint, foot) in BEACON.items():
        def face(fr, side, c, r, W, H, fld=fld, glint=glint, foot=foot):
            if side == 'up':
                return glint
            if side == 'down':
                return foot
            if c == 0 and r == 0:
                return glint
            return foot if r == H - 1 else fld
        out.append(K.state_sprite(os.path.join(K.OUT, '%s_beacon_%s.png' % (prefix, state)), 2, 2, 2, face, fill=fld))
    return out

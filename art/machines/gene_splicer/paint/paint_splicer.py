"""Gene Splicer (a vintage hand-crank sewing machine on the kit bench): the painted sheet and its state textures.

    python -I art/machines/gene_splicer/paint/paint_splicer.py                # writes the textures into the assets
    python -I art/machines/gene_splicer/paint/paint_splicer.py --preview DIR  # also DIR/splicer_rest.png, _work.png
Then adopt what it prints with tools/tex/tex.py adopt.

Writes, under textures/block/machine/:
  gene_splicer.png             128x160, the baked bench and machine and the eight bones
  gene_splicer_stitch_0..3.png the strand (element 8x1x2), one per `stitch` step, its box-UV at (0, 0) of a 32x16
                               sprite (the layout is 20 wide)
  gene_splicer_stab_0..2.png   the stability glass (element 4x2x1), one per `stab` band, a spirit-level bubble: band
                               0 holds still, band 1 wobbles a pixel each side (4 frames x 10 ticks), band 2 runs
                               across an amber field (4 frames x 5 ticks); box-UV at (0, 0) of a 16x16 sprite

The value plan (110 px first). Ivory said "white box", so the machine itself is black japanned enamel, the colour
every hand-crank sewing machine is remembered in, on the ivory bench:
  DARK   the machine: japanned black (r1 field, an r3 gloss lip, r0 foot) with a 1 px brass pinstripe under the lip
         and a gold decal (b4/b3) once on the arm and once on the head's side; the bed and deck (s1), the shelf
  LIGHT  the K12 bench below (e2 enamel, recessed rim panels on its sides), the pale strand on the dark bed
  ACCENT the copper handwheel (c4 field, c6 lit outer faces, one rim segment marked brass so the turn reads) with its
         brass grip; the brass take-up lever; the three spools' thread (cube 1: a neutral light base, e5 with e4
         winding rows, for the code's tint); the plum serum in the cassette window
Story marks, once each: the polished needle plate under the needle; the marked rim segment; the two decals.
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'kit', 'paint'))
import kitpaint as K  # noqa: E402

MDIR = os.path.join(HERE, '..')
BBMODEL = os.path.join(MDIR, 'gene_splicer.bbmodel')
PLACEHOLDER = os.path.join(MDIR, 'gene_splicer_placeholder.png')
SHEET = os.path.join(K.OUT, 'gene_splicer.png')

DECAL = ['b4 b3 b4', 'b3 b5 b3']          # a 3x2 gold scroll


def bench_sides(S):
    """The splicer's own recessed rim panels on the bench's sides: the rim raised (e3, top e4), the end face inside it
    recessed (e1, its top row and its left column as seen in the rim's shadow, e0)."""
    for n in S.names('bench_side_'):
        K.fill(S, n, 'e3')
        S.face(n, 'up').fill('e4')
        S.face(n, 'down').fill('e1')
    for n, side in (('bench_end_l', 'west'), ('bench_end_r', 'east')):
        f = S.face(n, side)

        def k(c, r, x, y, z, side=side):
            if not (-4 < z < 4 and 3 < y < 8):
                return None
            left = z < -3 if side == 'west' else z > 3       # the viewer's left column on each side
            return 'e0' if (y > 7 or left) else 'e1'
        f.by_world(k)


def machine(S):
    for n in ('pillar', 'arm', 'head'):
        for f in S.faces(n, K.SIDES):
            K.japanned(f)
        up = S.face(n, 'up')
        x0, _, z0 = up.c['frm']
        up.by_world(lambda c, r, x, y, z, z0=z0: 'r3' if z < z0 + 1 else 'r2')
        S.face(n, 'down').fill('r0')
    # the decals, once each: on the arm's front left of the sight glass, and on the head's outer side
    S.face('arm', 'north').rows(DECAL, c0=4, r0=2)
    S.face('head', 'east').rows(DECAL, c0=2, r0=3)
    # the bed: dark steel, the polished needle plate (s4, an s5 corner) under the needle
    K.dark_steel(S, 'bed')
    up = S.face('bed', 'up')
    up.by_world(lambda c, r, x, y, z: ('s5' if (x > 5 and z < 0) else 's4') if (4 < x < 6 and -1 < z < 1) else None)
    K.dark_steel(S, 'deck')
    for n in ('deck_post_l', 'deck_post_r'):
        K.brass_post(S, n)
    # the needle bar, its neck, the take-up lever
    for n in ('needle_bar', 'needle_neck'):
        K.small(S, n, 's', 4, top='s5', bottom='s3')
    K.small(S, 'needle_lever', 'b', 3, top='b4', bottom='b1')
    S.face('needle_lever', 'north').row(0, 'b4')
    # the cassette: steel with the plum serum window
    K.small(S, 'cassette_body', 's', 3)
    K.small(S, 'cassette_window', 'f', 1, top='f2', bottom='f0')
    S.face('cassette_window', 'north').rows(['f3 f1', 'f1 f0'])


def wheel(S):
    """The handwheel: copper rim segments (c4, the outer faces c6, the inner faces c2), one segment marked brass so the
    turn reads at a slow crank; copper spokes c3; the dark hub; the steel axle; a light crank arm (e3); the brass
    grip."""
    for n in S.names('wheel_rim_'):
        K.small(S, n, 'c', 4, top='c6', bottom='c2')
        S.face(n, 'west').fill('c6')
        S.face(n, 'east').fill('c2')
    for f in S.faces('wheel_rim_t', ('west', 'up')):
        f.fill('b4')
    for n in S.names('wheel_spoke_'):
        K.small(S, n, 'c', 3, top='c5', bottom='c1')
    K.small(S, 'wheel_hub', 's', 1, top='s2', bottom='s0')
    S.face('wheel_hub', 'west').rows(['s1 s2 s2 s1', 's2 s4 s3 s1', 's2 s3 s1 s1', 's1 s1 s1 s0'])
    K.small(S, 'wheel_axle', 's', 3)
    K.small(S, 'wheel_crank', 'e', 3, top='e4', bottom='e2')
    K.brass_housing(S, 'wheel_grip')


def spools(S):
    """Brass flanges (small parts); the thread (cube 1, tinted by gene category in code) a neutral light base: e5
    with e4 winding rows every other row, top e5, so the tint shows true and the winding stays."""
    for i in (1, 2, 3):
        for part in ('flange_lo', 'flange_hi'):
            K.small(S, 'spool_%d_%s' % (i, part), 'b', 3, top='b4', bottom='b1')
        t = 'spool_%d_thread' % i
        for f in S.faces(t, K.SIDES):
            for c, r in f.each():
                f.put(c, r, 'e5' if r % 2 == 0 else 'e4')
        S.face(t, 'up').fill('e5')
        S.face(t, 'down').fill('e4')


# ===================================================================================================== state textures
def stitch_face(level):
    """The strand seen from above (8x2) and its sides. Stitched from the needle's end (+x, the face's left as seen
    from the front... the up face's column 0) for n of 3 steps: 0, 3, 5, 8 px of gold stitches (b4 on s5, every
    other pixel) on the pale strand (s4 field, its back row s3)."""
    n = {0: 0, 1: 3, 2: 5, 3: 8}[level]

    def face(fr, side, c, r, W, H):
        if side == 'up':
            if c < n:
                return 'b4' if c % 2 == 0 else 's5'
            return 's4' if r == 0 else 's3'
        if side == 'north':
            return ('b3' if c % 2 == 0 else 's4') if c < n else 's3'
        return 's3' if side != 'down' else 's2'
    return face


def stab_frames(band):
    """The spirit-level glass, front 4x2: field t2 (band 2 amber A1), the bubble t4 (A3) on the top row; frames give
    its column."""
    field, bub = ('t2', 't4') if band < 2 else ('A1', 'A3')
    cols = {0: [1], 1: [1, 0, 1, 2], 2: [0, 2, 3, 1]}[band]

    def face(fr, side, c, r, W, H):
        if side != 'north':
            return 't1' if side != 'up' else 't3'
        b = cols[fr]
        if r == 0 and c in (b, b + 1):
            return bub
        if r == 1:
            return K.st(field, -1) if field.startswith('t') else 'A0'
        return field
    return face, len(cols)


def state_textures():
    out = []
    for lv in range(4):
        out.append(K.state_sprite(os.path.join(K.OUT, 'gene_splicer_stitch_%d.png' % lv), 8, 1, 2, stitch_face(lv),
                                  fill='s3'))
    for band in range(3):
        face, frames = stab_frames(band)
        p = os.path.join(K.OUT, 'gene_splicer_stab_%d.png' % band)
        out.append(K.state_sprite(p, 4, 2, 1, face, fill='t1', frames=frames, frametime=10 if band == 1 else 5))
        if frames > 1:
            out.append(p + '.mcmeta')
    return out


def paint(S):
    K.bench(S)
    K.drawer(S, 'drawer_box', ('drawer_pull',))
    K.lamp_bezel(S)
    K.lens_on_sheet(S, 'lamp_lens', 'ready')
    K.flag(S, 'flag')
    bench_sides(S)
    machine(S)
    wheel(S)
    spools(S)
    # the sheet's own islands for the two state elements (review renders): stitch 1, stab 0
    sf = stitch_face(1)
    for side in K.ALL:
        f = S.face('strand', side)
        for c, r in f.each():
            f.put(c, r, sf(0, side, c, r, f.W, f.H))
    face, _ = stab_frames(0)
    for side in K.ALL:
        f = S.face('stab_glass', side)
        for c, r in f.each():
            f.put(c, r, face(0, side, c, r, f.W, f.H))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview')
    a = ap.parse_args()
    S = K.Sheet(BBMODEL)
    paint(S)
    miss = S.missing()
    if miss:
        sys.exit('unpainted faces: %s' % miss)
    out = [S.save(SHEET)] + state_textures()
    for p in out:
        print('wrote', p)
    if a.preview:
        os.makedirs(a.preview, exist_ok=True)
        K.standins_from(S, PLACEHOLDER)
        S.save(os.path.join(a.preview, 'splicer_rest.png'))
        K.lens_on_sheet(S, 'lamp_lens', 'work')
        S.save(os.path.join(a.preview, 'splicer_work.png'))
        print('previews in', a.preview)


if __name__ == '__main__':
    main()

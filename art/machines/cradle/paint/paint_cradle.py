"""Life Support Cradle (the convalescent's wingback armchair): the painted sheet, every pixel placed from palette keys.

    python -I art/machines/cradle/paint/paint_cradle.py                # writes block/machine/cradle.png
    python -I art/machines/cradle/paint/paint_cradle.py --preview DIR  # also DIR/cradle_rest.png (stand-ins filled)
Then: python -I tools/tex/tex.py adopt block/machine/cradle.png

One sheet, 128x176, for the baked chair and the three GeckoLib bones (bellows, hose_end, blanket). The saline bag's
five levels are alternative elements on this sheet (state_saline_0..4), so they need no texture of their own.

The value plan (110 px first):
  LIGHT  the upholstery: cream leather (e3 field, e4 piping on the lit edges, e2 on the shaded ones, deep buttons e1
         on faces 8x8 and over), never sheet metal; the brass crest rail on top (b3, b4 lit row, polish band)
  DARK   the seat and the back cushion behind the jar (r0-r1: the jar's glass is the bright thing in front of
         them), the headrest pad, the plinth and feet
  ACCENT the bellows column: yellow ribs (w2, w3 tops) between black folds (r1), the moving read at ten blocks;
         the teal saline bag on its brass hook; the teal tartan blanket
Story marks, once each: the polished crest; the saline drip in the drip line; the maker's buttons only where a
convalescent leans (the back and the outer arm panels).
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'kit', 'paint'))
import kitpaint as K  # noqa: E402

MDIR = os.path.join(HERE, '..')
BBMODEL = os.path.join(MDIR, 'cradle.bbmodel')
PLACEHOLDER = os.path.join(MDIR, 'cradle_placeholder.png')
SHEET = os.path.join(K.OUT, 'cradle.png')

LEATHER = ['front_rail', 'arm_core_r', 'arm_top_r', 'arm_front_r', 'arm_back_r', 'wing_r', 'stile_r',
           'arm_core_l', 'arm_top_l', 'arm_front_l', 'arm_back_l', 'wing_l', 'stile_l', 'back_rail']


def upholstery(S):
    for n in LEATHER:
        for f in S.faces(n, K.SIDES + ('up',)):
            K.leather(f, buttons=False)
        S.face(n, 'down').fill('e1')
    # the arms' outer panels are recessed between the arm's front and back posts: one step down, buttoned
    for n, side in (('arm_core_r', 'east'), ('arm_core_l', 'west')):
        K.leather(S.face(n, side), field='e2', piping='e3', shade='e1')
    # the inner arm faces (towards the jar) buttoned too: what a sitter's elbows lean on
    for n, side in (('arm_core_r', 'west'), ('arm_core_l', 'east')):
        K.leather(S.face(n, side))
    # the chair's back seen from behind: a buttoned panel, a step down from the frame round it
    for f in S.faces('rear_board', K.SIDES + ('up',)):
        K.leather(f, field='e2', piping='e3', shade='e1')
    S.face('rear_board', 'down').fill('e1')
    # the seat: dark, a piped front edge
    seat = 'seat'
    for f in S.faces(seat, K.SIDES):
        for c, r in f.each():
            f.put(c, r, 'r2' if r == 0 else ('r0' if r == f.H - 1 else 'r1'))
    up = S.face(seat, 'up')
    up.by_world(lambda c, r, x, y, z: 'r2' if z < -7 else 'r1')
    S.face(seat, 'down').fill('r0')
    # the back cushion behind the jar: the darkest field (r0), channel-tufted (r1 crests every 4 px), top piping r2
    bc = 'back_cushion'
    K.fill(S, bc, 'r1')
    f = S.face(bc, 'north')
    for c, r in f.each():
        f.put(c, r, 'r2' if r == 0 else ('r1' if c % 4 == 1 else 'r0'))
    # the headrest pad: r1 with an r2 piped top
    for f in S.faces('headrest', K.SIDES):
        f.fill('r1')
        f.row(0, 'r2')
    S.face('headrest', 'up').fill('r2')
    S.face('headrest', 'down').fill('r0')


def bag(S):
    """The saline bag on its hook: frosted plastic above the saline (opaque g2, a g3 sheen column on its 4-wide
    faces, g1 on the far column; seam top g3), the saline (l3 meniscus, l1 body, l0 foot)."""
    for n in S.names('bag_skin_'):
        for f in S.faces(n, K.SIDES):
            for c, r in f.each():
                if r == 0 and n.endswith('_0'):
                    k = 'g3'
                elif f.W >= 4 and c == 0:
                    k = 'g3'
                elif f.W >= 4 and c == f.W - 1:
                    k = 'g1'
                else:
                    k = 'g2'
                f.put(c, r, k)
        S.face(n, 'up').fill('g3')
        S.face(n, 'down').fill('g1')
    for n in S.names('bag_saline_'):
        for f in S.faces(n, K.SIDES):
            K.liquid_side(f, 'l')
        S.face(n, 'up').fill('l2')
        S.face(n, 'down').fill('l0')
    K.small(S, 'bag_hook', 'b', 3, top='b4', bottom='b1')
    # the drip line: a pale tube with one saline drop in it
    K.small(S, 'drip_line', 'g', 2, top='g3', bottom='g1')
    S.face('drip_line', 'north').put(0, 2, 'l1')


def bellows(S):
    """The breathing column: the brass crest rail on top, yellow ribs over black folds (horizontal bands: the folds
    show 1 px each between the ribs, too thin for 45-degree stripes; it moves, so it may be striped)."""
    K.brass_plate(S, 'crest')
    # the core: three 1 px folds between the ribs (pleat_core, _1, _2), black rubber, their tops and undersides r0
    for n in ('pleat_core', 'pleat_core_1', 'pleat_core_2'):
        K.fill(S, n, 'r1')
        S.face(n, 'up').fill('r0')
        S.face(n, 'down').fill('r0')
    for n in S.names('pleat_rib_'):
        for f in S.faces(n, K.SIDES):
            f.fill('w2')
        S.face(n, 'up').fill('w3')
        S.face(n, 'down').fill('w1')


def paint(S):
    K.plinth(S)
    K.feet(S)
    upholstery(S)
    bag(S)
    bellows(S)
    K.lamp_bezel(S)
    K.lens_on_sheet(S, 'lamp_lens', 'ready')
    # the hose: a pale steel tube from a dark collar on the headrest, a brass nozzle
    K.small(S, 'hose_collar', 's', 1, top='s2', bottom='s0')
    K.small(S, 'hose', 's', 4, top='s5', bottom='s3')
    K.small(S, 'hose_nozzle', 'b', 3, top='b4', bottom='b1')
    # the K10 terminal lying in the left arm's panel
    K.small(S, 'term_collar', 'r', 1)
    K.small(S, 'term_post', 'c', 4, top='c6', bottom='c2')
    K.small(S, 'term_cap', 'b', 3, top='b4', bottom='b1')
    # the blanket: a quiet teal company tartan
    for n in ('blanket_hang', 'blanket_lip'):
        for f in S.faces(n):
            K.tartan(f)


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
        S.save(os.path.join(a.preview, 'cradle_rest.png'))
        K.lens_on_sheet(S, 'lamp_lens', 'work')
        S.save(os.path.join(a.preview, 'cradle_work.png'))
        print('previews in', a.preview)


if __name__ == '__main__':
    main()

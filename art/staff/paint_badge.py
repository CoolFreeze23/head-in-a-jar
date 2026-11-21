"""The staff badge sheet: textures/block/staff_badge.png (16x16), painted from the palette.

    python art/staff/paint_badge.py

Everything the renderers draw for the lanyard look on blocks, at 16 px to a block. Two quads per badge, on one plane:

    FRAME [0, 0, 5, 5)   UNTINTED (vertex colour white): the steel clip on top (3 px, centred, its tongue biting 1 px
                         into the badge's top edge) and the badge's dark rubber edge (lit top and left one step
                         lighter), with a clear 3x2 hole where the face shows.
                         The edge stays dark whatever the team colour, so a gold badge still stands off brass and wood
                         and a teal one off a dark drum; the clip's bump on top makes the ID-badge outline.
    FACE  [6, 0, 9, 2)   TINTED with the badge colour: the 3x2 face, flat and matte like card (one shaded pixel at the
                         bottom right), so it reads as a card and not as a lamp.
                         Drawn into FRAME's hole: frame px (1..4, 2..4) = face px (0..3, 0..2).
    CORD  [14, 0, 16, 16) the lanyard cord, 2 px wide, TINTED, flat (lit left side, shaded right), any length. Not used
                         by the blocks in 0.6.0 (the badges are clipped on); it is for the worn lanyard later.

Grades (0.7.2): a badge for staff of grade 1-3 draws FRAME_G<n> in place of FRAME (the FACE is unchanged; an Intern
draws today's FRAME). Brass stripes on the dark edge: Associate the foot, Senior Associate the foot and the top beside
the clip, Team Lead the whole edge. The Team Lead's TIE and the Union Rep's ROSETTE are here too, at the same regions as
on the worn sheet (art/entity/lanyard/paint_lanyard.py), for the secondee's collar badge:

    FRAME_G1 [0, 10, 5, 15)   FRAME_G2 [5, 10, 10, 15)   FRAME_G3 [7, 5, 12, 10)   UNTINTED
    ROSETTE  [9, 0, 12, 5)    TIE      [13, 0, 14, 16)   TIE_TIP  [10, 13, 13, 15)  UNTINTED

The tinted pixels are neutral greys (staff_ramps 'tint'): the game multiplies them by the team colour, so the face
(tint.5, 0.93; one pixel tint.4) shows the team colour nearly true.
"""
import pix

LEGEND = {'3': 'tint.3', '4': 'tint.4', '5': 'tint.5',
          'h': 'rubber.2', 'k': 'rubber.1', 'm': 'steel.3', 's': 'steel.4'}

FRAME = [
    '.msm.',
    'hhshk',
    'h...k',
    'h...k',
    'hkkkk',
]
FACE = [
    '555',
    '554',
]
CORD = ['43'] * 16

REGIONS = {'FRAME': (0, 0, 5, 5), 'FACE': (6, 0, 9, 2), 'CORD': (14, 0, 16, 16),
           'FRAME_G1': (0, 10, 5, 15), 'FRAME_G2': (5, 10, 10, 15), 'FRAME_G3': (7, 5, 12, 10),
           'ROSETTE': (9, 0, 12, 5), 'TIE': (13, 0, 14, 16), 'TIE_TIP': (10, 13, 13, 15)}
UNTINTED = ['FRAME', 'FRAME_G1', 'FRAME_G2', 'FRAME_G3', 'TIE', 'ROSETTE', 'TIE_TIP']

# grade stripes on the rubber edge (brass: E lit top run, D lit left, B foot, A shaded right); the clip stays steel
STRIPE_LEGEND = {'A': 'brass.1', 'B': 'brass.2', 'D': 'brass.3', 'E': 'brass.4'}
FOOT = ['.....', '.....', '.....', '.....', 'BBBBA']
TOP = ['.....', 'EE.EB', '.....', '.....', '.....']
SIDES = ['.....', '.....', 'D...A', 'D...A', '.....']
GRADES = [[FOOT], [FOOT, TOP], [FOOT, TOP, SIDES]]
GRADE_AT = [(0, 10), (5, 10), (7, 5)]


def paint():
    im = pix.blank(16, 16)
    pix.stamp(im, 0, 0, FRAME, LEGEND)
    pix.stamp(im, 6, 0, FACE, LEGEND)
    pix.stamp(im, 14, 0, CORD, LEGEND)
    import os
    import sys
    sys.path.insert(0, os.path.join(pix.ROOT, 'art', 'entity', 'lanyard'))
    import paint_lanyard as worn
    for g, (x0, y0) in enumerate(GRADE_AT):
        pix.stamp(im, x0, y0, FRAME, LEGEND)
        for layer in GRADES[g]:
            pix.stamp(im, x0, y0, layer, STRIPE_LEGEND)
    pix.stamp(im, 13, 0, worn.TIE, worn.TIE_LEGEND)
    pix.stamp(im, 10, 13, worn.TIE_TIP, worn.TIE_LEGEND)
    pix.stamp(im, 9, 0, worn.ROSETTE, worn.ROSETTE_LEGEND)
    return im


if __name__ == '__main__':
    print(pix.save(paint(), 'block/staff_badge.png'))

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

REGIONS = {'FRAME': (0, 0, 5, 5), 'FACE': (6, 0, 9, 2), 'CORD': (14, 0, 16, 16)}
UNTINTED = ['FRAME']


def paint():
    im = pix.blank(16, 16)
    pix.stamp(im, 0, 0, FRAME, LEGEND)
    pix.stamp(im, 6, 0, FACE, LEGEND)
    pix.stamp(im, 14, 0, CORD, LEGEND)
    return im


if __name__ == '__main__':
    print(pix.save(paint(), 'block/staff_badge.png'))

"""The Staff Lanyard's textures, painted from the palette (art/palette.json through art/staff/staff_palette.py).

    python art/entity/lanyard/paint_lanyard.py            # writes both PNGs
    python art/entity/lanyard/paint_lanyard.py --check    # repaints in memory and compares with the files

    textures/item/staff_lanyard.png      16 x 16  the item: a teal cord loop, a brass clip and an ID card
    textures/entity/staff_lanyard.png    16 x 16  what StaffLayer draws on a member of staff (regions below)

The worn sheet (drawn with `RenderType.entityCutoutNoCull`; TINTED regions are neutral greys the game multiplies by
the badge colour, the employer's team colour or company teal; UNTINTED regions are drawn with a white vertex colour):

    FRAME       [0, 0, 5, 5)    UNTINTED  the full badge: the steel clip on an ivory card round a clear 3 x 2 window
                                          (lit top and left, shaded bottom and right)
    FACE        [6, 0, 9, 2)    TINTED    drawn over frame px (1..4, 2..4): its top row is the header stripe in the
                                          team colour, its second row clear (the card's photo and line show)
    MINI_FRAME  [0, 6, 3, 9)    UNTINTED  the mini badge for creatures whose chest is under 5 px: a 1 px clip and an
                                          ivory card round a 1 x 1 window
    MINI_FACE   [6, 6, 7, 7)    TINTED    its face, drawn in the hole (frame px 1, 1)
    CORD        [14, 0, 16, 16) TINTED    the cord: column 14 lit (faces up, north, west), column 15 shaded (down,
                                          south, east); any length (v wraps)

Grades (0.7.2): staff of grade 1-3 draw FRAME_G<n> / MINI_G<n> in place of FRAME / MINI_FRAME (the FACE quads are
unchanged; an Intern, grade 0, draws today's FRAME). The grade stripes are brass across the card, so they read the same
whatever the team colour: Associate one stripe across the card's foot, Senior Associate a second across its top under
the clip, Team Lead the whole card edged in brass (a gold card). The mini badge the same way round its 1 x 1 window.

    FRAME_G1    [0, 10, 5, 15)  UNTINTED  Associate                     MINI_G1  [6, 2, 9, 5)      UNTINTED
    FRAME_G2    [5, 10, 10, 15) UNTINTED  Senior Associate              MINI_G2  [10, 10, 13, 13)  UNTINTED
    FRAME_G3    [7, 5, 12, 10)  UNTINTED  Team Lead                     MINI_G3  [3, 6, 6, 9)      UNTINTED

Worn pieces of the promotion and the strike (0.7.2), UNTINTED flat quads facing -z like the badge:

    ROSETTE     [9, 0, 12, 5)   the Union Rep's rosette, 3 x 5: a 3 x 3 red rosette round a brass button, on two 1 px
                                tails (drawn 3 x 5 px)
    TIE         [13, 0, 14, 16) the Team Lead's tie, one column top to bottom: the knot (row 0), the blade lit where it
                                leaves the knot (1), the blade (2-15). Drawn 1 px wide and as long as the fit says, v
                                running 0..length from the top (one texel a pixel)
    TIE_TIP     [10, 13, 13, 15) the tie's broad end, 3 x 2, its point below: drawn just under the card, so the tie
                                shows its wide end below the badge as a real one does (a 1 px blade alone read as a
                                stick: a lollipop)
A picket sign's straps are lanyard cord: drawn exactly as the cord (CORD, tinted).
"""
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, '..', '..', '..'))
sys.path.insert(0, os.path.join(ROOT, 'art', 'staff'))
from staff_palette import C  # noqa: E402
import paint_badge  # noqa: E402  (rel60's badge pixel maps: the worn badge is the same badge)

TEX = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'headinajar', 'textures')

REGIONS = {'FRAME': (0, 0, 5, 5), 'FACE': (6, 0, 9, 2), 'MINI_FRAME': (0, 6, 3, 9), 'MINI_FACE': (6, 6, 7, 7),
           'CORD': (14, 0, 16, 16),
           'FRAME_G1': (0, 10, 5, 15), 'FRAME_G2': (5, 10, 10, 15), 'FRAME_G3': (7, 5, 12, 10),
           'MINI_G1': (6, 2, 9, 5), 'MINI_G2': (10, 10, 13, 13), 'MINI_G3': (3, 6, 6, 9),
           'ROSETTE': (9, 0, 12, 5), 'TIE': (13, 0, 14, 16), 'TIE_TIP': (10, 13, 13, 15)}
UNTINTED = ('FRAME', 'MINI_FRAME', 'FRAME_G1', 'FRAME_G2', 'FRAME_G3', 'MINI_G1', 'MINI_G2', 'MINI_G3', 'TIE', 'ROSETTE',
            'TIE_TIP')

# The worn badge is the item's ID card at worn size: ivory, a header stripe in the team colour (the FACE quad's top
# row; its second row is clear, so the card's photo and ruled line show through), the steel clip on top. A dark
# rubber frame like the blocks' badges read as a hole in the shirt on a creature.
FRAME = [
    '.msm.',
    'geeef',
    'e...G',
    'fpiiG',
    'fGGGH',
]
FACE = [
    '555',
    '...',
]
MINI_FRAME = [
    '.s.',
    'f.G',
    'GGH',
]
MINI_FACE = ['5']

# The grade stripes, in brass (lit top and left, shaded bottom and right, as the card is): A..E brass.1..brass.4 (E the
# lit top run), drawn over the card's own edge pixels. Associate: the foot; Senior: the foot and the top under the clip;
# Team Lead: the whole edge.
FOOT = ['....', '....', '....', '....', 'BBBBA']
TOP = ['.....', 'EDDDB', '.....', '.....', '.....']
SIDES = ['.....', '.....', 'D...A', 'D...A', '.....']
GRADES = [[FOOT], [FOOT, TOP], [FOOT, TOP, SIDES]]
MINI_FOOT = ['...', '...', 'BBA']
MINI_SIDES = ['...', 'D.A', '...']
MINI_TOP = ['D.B', '...', '...']
MINI_GRADES = [[MINI_FOOT], [MINI_FOOT, MINI_SIDES], [MINI_FOOT, MINI_SIDES, MINI_TOP]]
GRADE_AT = [(0, 10), (5, 10), (7, 5)]
MINI_AT = [(6, 2), (10, 10), (3, 6)]
STRIPE_LEGEND = {'A': 'brass.1', 'B': 'brass.2', 'D': 'brass.3', 'E': 'brass.4'}

# The Team Lead's tie, 1 px wide (top to bottom): the knot in the tie's darkest navy, the blade lit where it leaves the
# knot, then the blade. Management navy: a red tie read as blood dripping from the badge at 8 blocks (critic round
# 2), and the Rep's rosette is the red one. (A brass tie clip read as a yellow tip below the card: no clip.)
TIE = ['k', 'l'] + ['m'] * 14
TIE_LEGEND = {'k': 'ink.0', 'l': 'ink.2', 'm': 'ink.1'}
# its broad end, lit on the left, shaded on the right, coming to a point
TIE_TIP = ['lmk', '.k.']
# The Union Rep's rosette: a red ribbon rosette (lit top left, shaded bottom right) round a brass button, on two tails.
ROSETTE = [
    'lrm',
    'rbm',
    'mmk',
    'r.m',
    'm.k',
]
ROSETTE_LEGEND = {'l': 'red_ink.2', 'r': 'red_ink.2', 'm': 'red_ink.1', 'k': 'red_ink.0', 'b': 'brass.4'}


def stamp(im, x0, y0, rows, legend):
    px = im.load()
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            if ch not in '. ':
                px[x0 + dx, y0 + dy] = C(legend[ch])


def paint_worn():
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    L = paint_badge.LEGEND
    L = dict(L, e='enamel.4', f='enamel.3', g='enamel.5', G='enamel.2', H='enamel.1', p='flesh.2', i='ink.1')
    stamp(im, 0, 0, FRAME, L)
    stamp(im, 6, 0, FACE, L)
    stamp(im, 0, 6, MINI_FRAME, L)
    stamp(im, 6, 6, MINI_FACE, L)
    stamp(im, 14, 0, paint_badge.CORD, L)
    for g, (x0, y0) in enumerate(GRADE_AT):
        stamp(im, x0, y0, FRAME, L)
        for layer in GRADES[g]:
            stamp(im, x0, y0, layer, STRIPE_LEGEND)
    for g, (x0, y0) in enumerate(MINI_AT):
        stamp(im, x0, y0, MINI_FRAME, L)
        for layer in MINI_GRADES[g]:
            stamp(im, x0, y0, layer, STRIPE_LEGEND)
    stamp(im, 13, 0, TIE, TIE_LEGEND)
    stamp(im, 10, 13, TIE_TIP, TIE_LEGEND)
    stamp(im, 9, 0, ROSETTE, ROSETTE_LEGEND)
    return im


# The item: the cord loop (company teal, 2 px: lit outside, shaded inside, its own darkest step as the outline where
# it meets the background), the brass swivel clip where the two ends meet, and an ivory ID card hanging from it with a
# teal band, a little photograph and two ruled lines. Lit from the top left.
ITEM_LEGEND = {
    'o': 'teal_glass.0', 'a': 'teal_glass.2', 'b': 'teal_glass.1', 'A': 'teal_glass.3',
    'B': 'brass.4', 'C': 'brass.3', 'D': 'brass.2', 'E': 'brass.1',
    'e': 'enamel.5', 'f': 'enamel.4', 'g': 'enamel.3', 'G': 'enamel.2', 'H': 'enamel.0',
    't': 'teal_glass.2', 'T': 'teal_glass.1',
    'p': 'flesh.4', 'q': 'flesh.2', 'k': 'rubber.1', 'i': 'ink.1',
}
ITEM = [
    '................',
    '....oooooooo....',
    '...oAaaaaaabo...',
    '..oAo......obo..',
    '..oao.......ob..',
    '..oao.......ob..',
    '...oao.....obo..',
    '....oao...obo...',
    '.....oaBCbo.....',
    '......EBCE......',
    '...HHHHDDHHHH...',
    '...HttttttTTH...',
    '...Hpqegfff gH...',
    '...Hqqeiiii gH...',
    '...HfggggggGGH...',
    '...HHHHHHHHHH...',
]


def paint_item():
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    rows = [r.replace(' ', 'g') for r in ITEM]
    stamp(im, 0, 0, rows, ITEM_LEGEND)
    return im


OUTPUTS = {'item/staff_lanyard.png': paint_item, 'entity/staff_lanyard.png': paint_worn}


def main():
    check = '--check' in sys.argv
    bad = 0
    for rel, fn in OUTPUTS.items():
        im = fn()
        path = os.path.join(TEX, rel)
        if check:
            old = Image.open(path).convert('RGBA')
            same = old.size == im.size and old.tobytes() == im.tobytes()
            print(('same ' if same else 'DIFF ') + rel)
            bad += not same
        else:
            os.makedirs(os.path.dirname(path), exist_ok=True)
            im.save(path, 'PNG', optimize=False)
            print(path)
    sys.exit(1 if bad else 0)


if __name__ == '__main__':
    main()

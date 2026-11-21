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
           'CORD': (14, 0, 16, 16)}
UNTINTED = ('FRAME', 'MINI_FRAME')

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

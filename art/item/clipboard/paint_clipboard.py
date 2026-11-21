"""The Clipboard item icon: textures/item/clipboard.png (16x16), from the pixel map below.

    python art/item/clipboard/paint_clipboard.py            # writes the PNG
    python art/item/clipboard/paint_clipboard.py --check    # no writes: fails if the PNG differs from what this draws

What a stranger should say: "a clipboard with a pen". An upright brown hardboard (the recipe's wooden slab) with a
steel spring clip at the top centre (a hanging ring above it), a white sheet under the clip with a red title rule and
ruled lines of blue ink, the first two lines ticked; a black pen with a brass cap and clip lying across the lower right
corner, nib on the sheet at the bottom left (long things run bottom left to top right, T20). It must not read as vanilla's book and
quill (no binding, no feather) or as a map (the board's brown edge shows round the sheet on three sides).
Light from the top left; outlines selective (T11): the board's own darkest step on the bottom and right, its middle
step along the lit top and left; the sheet's right and foot in the enamel's shade step.
"""
import argparse
import io
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'staff'))
import pix  # noqa: E402  (art/staff/pix.py: palette keys onto an image, saving into the assets)

LEGEND = {
    'O': 'manila.0', 'o': 'manila.2', 'B': 'manila.1', 'b': 'manila.2',     # the board: outline dark/lit, field, lit run
    'S': 'steel.1', 's': 'steel.3', 'T': 'steel.4', 'G': 'steel.5',         # the clip: shadow, body, lit, glint
    'W': 'enamel.5', 'w': 'enamel.4', 'e': 'enamel.3',                      # the sheet: lit corner, field, shade
    'r': 'red_ink.1', 'l': 'ink.2', 'k': 'rubber.0',                          # title rule, ruled lines, the ticks
    'P': 'rubber.0', 'p': 'rubber.2', 'Y': 'brass.4', 'y': 'brass.2',       # the pen: body dark, body lit, cap
    'n': 'steel.4',                                                         # the nib
}

#        0123456789abcdef
BOARD = [
    '......sTs.......',  # 0  hanging ring
    '..ooooS.SoooO...',  # 1
    '..obbTGTTsbbBO..',  # 2  the clip
    '..obsTTTTTsBBO..',  # 3
    '..oBWSSSSSSwBO..',  # 4  the sheet comes out from under it
    '..oBWwrrrrrwBO..',  # 5  title rule
    '..oBwwwwwwweBO..',  # 6
    '..oBwkllllweBO..',  # 7  ticked
    '..oBkwwwwwweBYy.',  # 8                     pen cap
    '..oBwkllllwYyO..',  # 9  ticked
    '..oBkwwwwwwpPO..',  # a
    '..oBwwllllpPBO..',  # b
    '..oBwwwwwpPeBO..',  # c
    '..oBeeeenPeeBO..',  # d  the nib, on the sheet
    '..oBBBBBBBBBBO..',  # e
    '..OOOOOOOOOOOO..',  # f
]


def paint():
    im = pix.blank(16, 16)
    pix.stamp(im, 0, 0, BOARD, LEGEND)
    return im


def models():
    """The item model for the generator (path under assets/headinajar/models/item/): flat, as vanilla's paper."""
    return {'clipboard.json': {'parent': 'minecraft:item/generated',
                               'textures': {'layer0': 'headinajar:item/clipboard'}}}


def outputs():
    yield 'item/clipboard.png', paint()


def _bytes(im):
    buf = io.BytesIO()
    im.save(buf, 'PNG', optimize=False)
    return buf.getvalue()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--check', action='store_true')
    a = ap.parse_args()
    bad = []
    for rel, im in outputs():
        path = os.path.join(pix.TEX, rel)
        if a.check:
            if not os.path.exists(path) or open(path, 'rb').read() != _bytes(im):
                bad.append(rel)
        else:
            print(pix.save(im, rel))
    if bad:
        print('differs: %r' % bad)
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main())

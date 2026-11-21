"""The Notice of Separation (Form 45-S) item icon: textures/item/separation_notice.png (16x16), from the pixel map below.

    python art/item/separation_notice/paint_separation_notice.py            # writes the PNG
    python art/item/separation_notice/paint_separation_notice.py --check    # no writes: fails if the PNG differs

What a stranger should say: "a form in duplicate: a pink slip". A white printed form with its pink carbon copy behind
it, offset up and to the right (the pink slip is the employee's copy; the company keeps the white one, and hands over
both anyway). On the white sheet: a title in blue-black ink with a light rule beside it, two typed lines, one bold
red rubber stamp across the lower half, and a signature. It must not read as
the Job Card (no buff card, no coloured band, no pictogram) or as the Condolence Card (no black edge, no flower).
Light from the top left; outlines selective (T11): each sheet's own darkest step on the bottom and right, a lighter one
along the lit top and left.
"""
import argparse
import io
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'staff'))
import pix  # noqa: E402  (art/staff/pix.py: palette keys onto an image, saving into the assets)

LEGEND = {
    'Q': 'flesh.1', 'q': 'flesh.2', 'P': 'flesh.4', 'p': 'flesh.3',      # the pink copy: outline dark/lit, field, shade
    'O': 'enamel.0', 'o': 'enamel.2',                                     # the white form's outline: dark, lit
    'W': 'enamel.5', 'w': 'enamel.4', 'e': 'enamel.3',                   # the white form: lit corner, field, shade
    'k': 'ink.0', 'l': 'ink.2',                                          # title and signature, typed lines
    'R': 'red_ink.1', 'r': 'red_ink.2',                                  # the stamp
}

#        0123456789abcdef
FORM = [
    '....qqqqqqqqqq..',  # 0
    '....qPPPPPPPPPQ.',  # 1  the pink copy behind
    '....qPPPPPPPPpQ.',  # 2
    '.oooooooooooPpQ.',  # 3
    '.oWWwwwwwwwOPpQ.',  # 4  the white form
    '.oWkkkkwlllOPpQ.',  # 5  the title and its rule
    '.owwwwwwwwwOPpQ.',  # 6
    '.owlllllllwOPpQ.',  # 7  typed lines
    '.owwwwwwwwwOPpQ.',  # 8
    '.owllllwwwwOPpQ.',  # 9
    '.owRRRRRRRwOPpQ.',  # a  the stamp, one bold red block
    '.owRrrrrrRwOppQ.',  # b
    '.owRRRRRRRwOQQQ.',  # c
    '.owwwwwwwwwO....',  # d
    '.oeeekkkeeeO....',  # e  the signature on the foot shade
    '.OOOOOOOOOOO....',  # f
]


def paint():
    im = pix.blank(16, 16)
    pix.stamp(im, 0, 0, FORM, LEGEND)
    return im


def models():
    """The item model for the generator (path under assets/headinajar/models/item/): flat, as vanilla's paper."""
    return {'separation_notice.json': {'parent': 'minecraft:item/generated',
                                       'textures': {'layer0': 'headinajar:item/separation_notice'}}}


def outputs():
    yield 'item/separation_notice.png', paint()


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

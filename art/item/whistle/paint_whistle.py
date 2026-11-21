"""The Supervisor's Whistle item icon: textures/item/supervisors_whistle.png (16x16), from the pixel map below, and its
two item models (held, and blowing).

    python art/item/whistle/paint_whistle.py            # writes the PNG
    python art/item/whistle/paint_whistle.py --check    # no writes: fails if the PNG differs from what this draws

What a stranger should say: "a referee's whistle". A nickel-plated pea whistle seen from the side (the recipe's
copper and iron, plated; a brass one read as a bulb horn or a toucan): the round barrel at the left with a glint on its
top left, the flat mouthpiece running right from the barrel's top, the air slot dark where the two meet, the mouth a dark
opening at the right end, a small ring at the back of the barrel (a cord there read as legs). The mouth is at the right,
near where vanilla's goat horn has its mouth end (lower right), so the horn's blowing transforms bring it to the lips
(see `models()`; to be seen in the game).
Light from the top left; outlines selective (T11): steel step 0 on the bottom and right, step 2 along the lit top and
left.
"""
import argparse
import io
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'staff'))
import pix  # noqa: E402  (art/staff/pix.py: palette keys onto an image, saving into the assets)

LEGEND = {
    'O': 'steel.0', 'o': 'steel.2',                                         # outline: dark bottom and right, lit top and left
    'G': 'enamel.5', 'Y': 'steel.5', 'b': 'steel.4', 'm': 'steel.3', 'd': 'steel.2',   # nickel plate; G the glint
    'k': 'rubber.0',                                                        # the air slot and the mouth
}

#        0123456789abcdef
WHISTLE = [
    '................',  # 0
    '................',  # 1
    '................',  # 2
    '...oooookooooo..',  # 3  the air slot behind the mouthpiece
    '..oGGYbkkYYYYYO.',  # 4  polish on the barrel's top left; the mouthpiece's lit top
    '.oGYYbbbmbbbbbkO',  # 5  the mouth, a dark opening at the end
    '.oYYbbbmmddddddO',  # 6
    '.oYbbbmmdOOOOOO.',  # 7  the mouthpiece's underside
    '.obbbmmddO......',  # 8
    '.ObbmmdddO......',  # 9
    'om.OmdddO.......',  # a  the ring, at the back of the barrel
    'O.OOOOOO........',  # b
    '.OO.............',  # c
    '................',  # d
    '................',  # e
    '................',  # f
]


def paint():
    im = pix.blank(16, 16)
    pix.stamp(im, 0, 0, WHISTLE, LEGEND)
    return im


def models():
    """Item models for the generator, keyed by path under assets/headinajar/models/item/. The held model takes vanilla's
    goat horn transforms and the blowing one its tooting_goat_horn transforms (1.21.1), since the whistle's mouth
    sits where the horn's does; the generator's item property must be named as in `overrides` and be 1 while the
    whistle is in use (vanilla's "tooting": `entity.isUsingItem() && entity.getUseItem() == stack`)."""
    tex = {'layer0': 'headinajar:item/supervisors_whistle'}
    held = {
        'parent': 'minecraft:item/generated',
        'textures': tex,
        'display': {
            'thirdperson_righthand': {'rotation': [0, 180, 0], 'translation': [0, 3, 1], 'scale': [0.55, 0.55, 0.55]},
            'thirdperson_lefthand': {'rotation': [0, 0, 0], 'translation': [0, 3, 1], 'scale': [0.55, 0.55, 0.55]},
            'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
            'firstperson_lefthand': {'rotation': [0, 90, -25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
        },
        'overrides': [{'predicate': {'headinajar:tooting': 1}, 'model': 'headinajar:item/supervisors_whistle_tooting'}],
    }
    tooting = {
        'parent': 'minecraft:item/generated',
        'textures': tex,
        'display': {
            'thirdperson_righthand': {'rotation': [0, -125, 0], 'translation': [-1, 2, 2], 'scale': [0.5, 0.5, 0.5]},
            'thirdperson_lefthand': {'rotation': [0, 55, 0], 'translation': [-1, 2, 2], 'scale': [0.5, 0.5, 0.5]},
            'firstperson_righthand': {'rotation': [0, -55, -5], 'translation': [-1, -2.5, -7.5]},
            'firstperson_lefthand': {'rotation': [0, 115, 5], 'translation': [0, -2.5, -7.5]},
        },
    }
    return {'supervisors_whistle.json': held, 'supervisors_whistle_tooting.json': tooting}


def outputs():
    yield 'item/supervisors_whistle.png', paint()


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

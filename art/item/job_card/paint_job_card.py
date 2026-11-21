"""The Job Card item icons: textures/item/job_card.png and one per job (16x16 each), from the pixel maps below.

    python art/item/job_card/paint_job_card.py            # writes the five PNGs
    python art/item/job_card/paint_job_card.py --check    # no writes: fails if a PNG differs from what this draws

What a stranger should say: "a time card with a picture of the job on it". An upright buff card (the punch-clock card
the Time Clock's rack holds), its top-right corner clipped as time cards are, a printed band across the top in the
job's colour with a pale title dash, the job's pictogram below, and two ruled rows at the foot where the hours go. The
four printed looks differ by band and pictogram, in dark steps, so they tell apart at GUI 1 without the tooltip:
    hauling   a black and yellow hazard band (forklift country), a crate seen from above and in front
    sorting   blue office-ink band, two arrows passing each other
    farming   leaf-green band, a gold ear of wheat on a green stem
    security  red office-ink band, a shield lit on its left half
    (blank)   job_card.png: the card before printing, a grey band, grey ruled rows, no pictogram; the model's default
Light from the top left; outlines selective (T11): the card's own darkest step on the bottom and right, two steps
lighter along the lit top and left. Printing is flat ink: the pictogram has a dark and a mid step and one lit step on
its upper left, no shading round its edge.
"""
import argparse
import io
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'staff'))
import pix  # noqa: E402  (art/staff/pix.py: palette keys onto an image, saving into the assets)

CARD_KEYS = {
    'o': 'manila.3', 'O': 'manila.1',                           # outline: lit top and left, dark bottom and right
    'C': 'manila.6', 'c': 'manila.5', 'p': 'manila.4',          # the card: lit corner, field, shade on the foot and right
    'l': 'ink.3',                                               # the ruled rows for the hours
}

# ink by job: band light, band, band dark (its foot row), pictogram dark, mid, lit
INKS = {
    'hauling': ('brass.3', 'brass.2', 'brass.1', 'brass.1', 'brass.2', 'brass.3'),
    'sorting': ('ink.2', 'ink.1', 'ink.0', 'ink.0', 'ink.1', 'ink.2'),
    'farming': ('leaf.3', 'leaf.2', 'leaf.1', 'leaf.1', 'brass.2', 'brass.3'),   # a green band, gold wheat on a green stem
    'security': ('red_ink.2', 'red_ink.1', 'red_ink.0', 'red_ink.0', 'red_ink.1', 'red_ink.2'),
    None: ('tint.3', 'tint.2', 'tint.1', 'tint.2', 'tint.3', 'tint.4'),
}

#        0123456789abcdef
CARD = [
    '................',  # 0
    '...oooooooO.....',  # 1  the clipped corner, two texels
    '...oHhhhhhhO....',  # 2  the printed band
    '...ohCCChhhhO...',  # 3  its title dash
    '...ogggggggpO...',  # 4  the band's foot
    '...oCccccccpO...',  # 5
    '...occcccccpO...',  # 6
    '...occcccccpO...',  # 7
    '...occcccccpO...',  # 8
    '...occcccccpO...',  # 9
    '...occcccccpO...',  # a
    '...occcccccpO...',  # b
    '...ollllllcpO...',  # c  ruled rows for the hours
    '...occcccccpO...',  # d
    '...ollllllppO...',  # e
    '...OOOOOOOOOO...',  # f
]

# pictograms: I dark, i mid, j lit; '.' leaves the card; placed with their top-left at PICTO_AT
PICTOS = {
    'hauling': [          # a crate seen from above and in front: lit lid, front with one plank seam, dark end
        '.jjjjI',
        'jjjjII',
        'iiiiII',
        'IIIIiI',
        'iiiiII',
        'IIIII.',
    ],
    'sorting': [          # two arrows passing each other
        '....I.',
        'jiiiiI',
        '....I.',
        '.I....',
        'Iiiiij',
        '.I....',
    ],
    'farming': [          # an ear of wheat: two awns, gold grains in pairs, a green stem
        'j.j...',
        '.j....',
        'iji...',
        'jij...',
        'iji...',
        '.I....',
    ],
    'security': [         # a shield, lit on its left half
        'IIIIII',
        'IjjiiI',
        'IjjiiI',
        'IjiiiI',
        '.IjiI.',
        '..II..',
    ],
}
HAZARD = [              # the hauling band: 45-degree stripes, 2 px, yellow over black; its top row a step lighter
    'WWrrWWr',
    'wrrwwrrw',
    'rrwwrrww',
]
BLANK = [               # the blank card: ruled for the hours all the way down
    '......',
    'llllll',
    '......',
    'llllll',
]
PICTO_AT = {'hauling': (5, 6), 'sorting': (5, 6), 'farming': (6, 6), 'security': (5, 6)}


def paint(job=None):
    hl, h, g, dk, mid, lit = INKS[job]
    keys = dict(CARD_KEYS, H=hl, h=h, g=g)
    im = pix.blank(16, 16)
    pix.stamp(im, 0, 0, CARD, keys)
    if job is None:
        pix.stamp(im, 5, 6, BLANK, {'l': 'tint.3'})
    else:
        if job == 'hauling':
            pix.stamp(im, 4, 2, HAZARD, {'w': 'warning.2', 'W': 'warning.3', 'r': 'rubber.1'})
        x0, y0 = PICTO_AT[job]
        pix.stamp(im, x0, y0, PICTOS[job], {'I': dk, 'i': mid, 'j': lit})
    return im


JOBS = ('hauling', 'sorting', 'farming', 'security')


def models():
    """Item models for the generator, keyed by path under assets/headinajar/models/item/. The card's look is chosen by
    the item property named in `overrides`, clamped to 0..1 as vanilla clamps them: 0 none (the blank card),
    0.25 hauling, 0.5 sorting, 0.75 farming, 1 security (overrides are read in order, the last that matches wins)."""
    def flat(tex):
        return {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'headinajar:item/' + tex}}
    out = {}
    card = flat('job_card')
    card['overrides'] = [{'predicate': {'headinajar:job': (i + 1) / 4.0}, 'model': 'headinajar:item/job_card_' + job}
                         for i, job in enumerate(JOBS)]
    out['job_card.json'] = card
    for job in JOBS:
        out['job_card_%s.json' % job] = flat('job_card_' + job)
    return out


def outputs():
    yield 'item/job_card.png', paint(None)
    for job in JOBS:
        yield 'item/job_card_%s.png' % job, paint(job)


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

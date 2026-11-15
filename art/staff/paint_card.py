"""The Condolence Card item icon: textures/item/condolence_card.png (16x16), from the pixel map below.

    python art/staff/paint_card.py

What a stranger should say: "a sympathy card": a folded cream card edged in black (mourning stationery), a white lily
(a trumpet opening upward, a yellow stamen) on its front, standing a little open: its back leaf recedes up and to the
right behind the front, cream edged in the enamel's own darkest step, so the black edge belongs to the front. It must not read
as vanilla paper (a loose white curled sheet): the black edge, the lily and the fold carry that.
Light from the top left; outlines selective (T11): rubber step 0 on the bottom and right, step 2 along the lit top
and left; the black edge band is the card's printed border, not an outline.
"""
import pix

LEGEND = {
    'K': 'rubber.0', 'k': 'rubber.1', 'h': 'rubber.2',          # the black mourning edge of the front
    'C': 'enamel.4', 'c': 'enamel.3', 'p': 'enamel.2',          # the card's cream face: lit corner, field, bottom row
    'W': 'enamel.5', 'q': 'enamel.1',                           # the lily: light, shadow
    'L': 'enamel.3', 'l': 'enamel.4', 'E': 'enamel.0',          # the back leaf, receding: body, lit top run, its edge
    'Y': 'brass.4',
    'G': 'leaf.3', 'g': 'leaf.2', 'z': 'leaf.1',
}

#        0123456789abcdef
CARD = [
    '.............EE.',  # 0
    '..hkkkkkkkkkElE.',  # 1
    '..hCCcccccckllE.',  # 2
    '..hCWccYccWkLLE.',  # 3
    '..kcWWcWcWqkLLE.',  # 4
    '..kcqWWWWWqkLLE.',  # 5
    '..kccqWWWqckLLE.',  # 6
    '..kcccqgqcckLLE.',  # 7
    '..kccccgccckLLE.',  # 8
    '..kcGGcgccckLLE.',  # 9
    '..kccGzgccckLLE.',  # a
    '..kccccgccckLLE.',  # b
    '..kccccgccckLLE.',  # c
    '..kppppgpppkLE..',  # d
    '..kKKKKKKKKKE...',  # e
    '................',  # f
]


def paint():
    im = pix.blank(16, 16)
    pix.stamp(im, 0, 0, CARD, LEGEND)
    return im


if __name__ == '__main__':
    print(pix.save(paint(), 'item/condolence_card.png'))

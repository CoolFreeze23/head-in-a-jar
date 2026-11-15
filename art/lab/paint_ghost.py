"""The Body Printer's empty Template slot: textures/gui/template_ghost.png (16 x 16).

    python art/lab/paint_ghost.py

Vanilla's empty-slot icons are a thin outline of the item they want, in one flat grey (#555555, the screen's own
shadow grey) on the slot's #8B8B8B; this one is a DNA Fraction: a test tube with a wide lip and a round foot, three of the
helix's rungs and the strand twisting between them, on the
item's own silhouette (textures/item/dna_fraction.png) so the two line up. Drawn at 100% alpha.
"""
from pix import blank, save, stamp

GHOST = ['...##########...',
         '...#........#...',
         '....#......#....',
         '....#......#....',
         '....#.####.#....',
         '....#....#.#....',
         '....#...#..#....',
         '....#..#...#....',
         '....#.####.#....',
         '....#.#....#....',
         '....#..#...#....',
         '....#...#..#....',
         '....#.####.#....',
         '.....#....#.....',
         '......####......',
         '................']


def paint():
    im = blank(16, 16)
    stamp(im, 0, 0, GHOST, {'#': 'gui.2'})
    return im


if __name__ == '__main__':
    print(save(paint(), 'gui/template_ghost.png'))

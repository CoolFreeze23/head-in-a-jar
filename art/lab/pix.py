"""Small shared helpers for the lab paint scripts: char maps onto an image, rectangles, the house 3x5 capitals, saving."""
import os
import sys

from PIL import Image

from lab_palette import C

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
TEX = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'headinajar', 'textures')
sys.path.insert(0, os.path.join(ROOT, 'art', 'machines', 'safety_placard'))
import placard_text  # noqa: E402  (the house 3x5 capitals: the Safety Placard's lettering)


def blank(w, h):
    return Image.new('RGBA', (w, h), (0, 0, 0, 0))


def stamp(im, x0, y0, rows, legend):
    """rows: strings, one char per pixel; legend: char -> palette key ('.' or ' ' = leave as is)."""
    px = im.load()
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            if ch in '. ':
                continue
            px[x0 + dx, y0 + dy] = C(legend[ch])


def rect(im, x0, y0, x1, y1, key):
    """Fill [x0, x1) x [y0, y1) with one key."""
    px = im.load()
    c = C(key)
    for y in range(y0, y1):
        for x in range(x0, x1):
            px[x, y] = c


def put(im, x, y, key):
    im.load()[x, y] = C(key)


def text_width(s):
    return placard_text.width(s)


def text(im, x0, y0, s, key):
    """The house 3x5 capitals (M, N and W 4 wide), 1 px apart, top-left at (x0, y0)."""
    x = x0
    for ch in s:
        g = placard_text.glyph(ch)
        for r, row in enumerate(g):
            for c, v in enumerate(row):
                if v == '#':
                    put(im, x + c, y0 + r, key)
        x += len(g[0]) + 1


def save(im, rel):
    """rel: path under textures/, e.g. 'gui/lab_chips.png'. Deterministic bytes (no metadata)."""
    path = os.path.join(TEX, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path, 'PNG', optimize=False)
    return path

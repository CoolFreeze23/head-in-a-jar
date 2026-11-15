"""Small shared helpers for the staff paint scripts: char maps onto an image, rectangles, saving into the assets."""
import os

from PIL import Image

from staff_palette import C

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
TEX = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'headinajar', 'textures')


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


def save(im, rel):
    """rel: path under textures/, e.g. 'block/staff_badge.png'. Deterministic bytes (no metadata)."""
    path = os.path.join(TEX, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path, 'PNG', optimize=False)
    return path

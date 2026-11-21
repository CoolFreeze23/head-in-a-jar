"""The Hard Hat's textures, painted from the palette (art/palette.json through art/staff/staff_palette.py).

    python art/item/hard_hat/paint_hard_hat.py            # writes the three PNGs
    python art/item/hard_hat/paint_hard_hat.py --check    # repaints in memory and compares with the files

    textures/item/hard_hat.png                    16 x 16   the inventory icon (pixel map below)
    textures/item/hard_hat_model.png              128 x 128 the baked hats (five sizes) and the 3D item model
    textures/models/armor/hard_hat_layer_1.png    64 x 64   the armour model on players and humanoid mobs

The two sheets are box-UV islands laid out by hard_hat_geo.py and painted by the same rules, so a hat on a player and
a hat on a cow show the same pixels. Vanilla box-UV orientation (checked against ModelPart.Cube): an island's top
square is the box's top seen from above with the front (-z) along its bottom row; the side strip runs west (the
creature's right), north (front), east, south, each with its top row at the box's top.

Paint (warning yellow is the company's safety paint; plastic, so large quiet fields and a glossy glint):
- every vertical face darkens downward in bands: the top row one step light, the field, a darker last row where it
  meets the step below (the dark is built by the geometry: brim under dome, crown on dome, ridge on crown);
- tops are the lightest; the ridge's sides are a step darker than the crown's so the rib reads by its shadow; one
  glint pixel at the ridge's front end;
- the dome's front carries the brass company plate (4 x 2 on l and xl, 2 x 1 below) with a teal emblem;
- the brim's edge is a step darker than the dome; its underside is the darkest yellow.
"""
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, '..', '..', '..'))
sys.path.insert(0, os.path.join(ROOT, 'art', 'staff'))
sys.path.insert(0, HERE)
from staff_palette import C  # noqa: E402
import hard_hat_geo as G  # noqa: E402

TEX = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'headinajar', 'textures')

Y0, Y1, Y2, Y3 = 'warning.0', 'warning.1', 'warning.2', 'warning.3'
GLINT = 'enamel.5'


def _rect(px, x0, y0, w, h, key):
    c = C(key)
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            px[x, y] = c


def _side(px, x0, y0, w, h, rows):
    """A vertical face: rows = keys from top to bottom (the last key repeats for the field)."""
    for j in range(h):
        if j == 0:
            k = rows[0]
        elif j == h - 1 and h > 1:
            k = rows[-1]
        else:
            k = rows[1] if len(rows) > 2 else rows[0]
        _rect(px, x0, y0 + j, w, 1, k)


def paint_box(px, u, v, dims, role, size, name=''):
    w, h, d = dims
    top = (u + d, v)            # w x d, front along the bottom row
    bot = (u + d + w, v)        # w x d
    west = (u, v + d)           # d x h, front at the right column
    north = (u + d, v + d)      # w x h
    east = (u + d + w, v + d)   # d x h, front at the left column
    south = (u + 2 * d + w, v + d)
    if role == 'brim':
        _rect(px, top[0], top[1], w, d, Y2)
        # the lit lip: the front row and the creature's right edge (the light's side) a step up
        _rect(px, top[0], top[1] + d - 1, w, 1, Y3)
        _rect(px, bot[0], bot[1], w, d, Y0)
        for (x, y), ww in ((west, d), (north, w), (east, d), (south, w)):
            _rect(px, x, y, ww, h, Y2)
    elif role == 'dome':
        _rect(px, top[0], top[1], w, d, Y2)
        _rect(px, top[0], top[1] + d - 1, w, 1, Y3)
        _rect(px, bot[0], bot[1], w, d, Y0)
        for (x, y), ww in ((west, d), (north, w), (east, d), (south, w)):
            _side(px, x, y, ww, h, [Y2, Y2, Y1])
        # the brass company plate on the front, its teal emblem
        plate = name in ('dome_front', 'dome') and not (name == 'dome' and G.SPEC[size]['crossed'])
        if w >= 8:
            pw, ph = 4, 2
        else:
            pw, ph = 2, 1
        px0 = north[0] + (w - pw) // 2
        py0 = north[1] + (1 if h >= 3 else 0)
        if plate and h >= 2:
            _rect(px, px0, py0, pw, ph, 'brass.4')
            if ph == 2:
                # a 2 x 2 teal emblem in a brass plate (brass at its ends)
                _rect(px, px0, py0 + 1, pw, 1, 'brass.3')
                px[px0 + 1, py0] = C('teal_glass.2')
                px[px0 + 2, py0] = C('teal_glass.2')
                px[px0 + 1, py0 + 1] = C('teal_glass.1')
                px[px0 + 2, py0 + 1] = C('teal_glass.1')
        # one placed scuff on the back, low (a hat that has been on a site)
        if name in ('dome_back', 'dome') and w >= 6:
            px[south[0] + 2, south[1] + h - 2] = C(Y1)
            px[south[0] + 3, south[1] + h - 2] = C(Y1)
    elif role == 'crown':
        _rect(px, top[0], top[1], w, d, 'warning.2h')
        _rect(px, top[0], top[1] + d - 1, w, 1, Y3)
        _rect(px, bot[0], bot[1], w, d, Y1)
        for (x, y), ww in ((west, d), (north, w), (east, d), (south, w)):
            _rect(px, x, y, ww, h, Y3)
    elif role == 'ridge':
        _rect(px, top[0], top[1], w, d, Y3)
        px[top[0], top[1] + d - 1] = C(GLINT)
        _rect(px, bot[0], bot[1], w, d, Y1)
        for (x, y), ww in ((west, d), (north, w), (east, d), (south, w)):
            _rect(px, x, y, ww, h, Y2)


def paint_sheet():
    im = Image.new('RGBA', G.SHEET_TEX, (0, 0, 0, 0))
    px = im.load()
    for (size, name), (u, v, dims, role) in G.sheet_layout().items():
        paint_box(px, u, v, dims, role, size, name)
    return im


def paint_armor():
    im = Image.new('RGBA', G.ARMOR_TEX, (0, 0, 0, 0))
    px = im.load()
    for name, (u, v, dims, role) in G.armor_layout().items():
        paint_box(px, u, v, dims, role, G.ARMOR_SIZE, name)
    return im


# The icon: three-quarter view from the front left, lit from the top left; outline selective (the darkest step of the
# yellow round the hat, rubber only under the brim where the harness shadow is).
ICON_LEGEND = {'0': Y0, '1': Y1, '2': Y2, '3': Y3, 'g': GLINT, 'b': 'brass.4', 'c': 'brass.3', 't': 'teal_glass.2', 'T': 'teal_glass.1',
               'k': 'rubber.1', 'r': 'rubber.2'}
ICON = [
    '................',
    '................',
    '................',
    '................',
    '................',
    '......oooo......',
    '....oo3g32oo....',
    '...o33332221o...',
    '...o33bttb21o...',
    '...o32bTTb11o...',
    '...o32222211o...',
    '..o3333332221o..',
    '..o2111111111o..',
    '...kkkkkkkkkk...',
    '................',
    '................',
]


def paint_icon():
    leg = dict(ICON_LEGEND)
    leg['o'] = Y0
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y, row in enumerate(ICON):
        for x, ch in enumerate(row):
            if ch != '.':
                px[x, y] = C(leg[ch])
    return im


OUTPUTS = {
    'item/hard_hat.png': paint_icon,
    'item/hard_hat_model.png': paint_sheet,
    'models/armor/hard_hat_layer_1.png': paint_armor,
}


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

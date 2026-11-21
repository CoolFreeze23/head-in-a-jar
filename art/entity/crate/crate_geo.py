"""The carried crate: what a hauling member of staff carries (both hands for bipeds, on the back of quadrupeds, under a
ghast). A small slatted shipping crate, 6 x 5 x 6.

    python art/entity/crate/crate_geo.py          # prints the elements and checks them

The data generator imports this file and writes what `java_models()` returns (`models/entity/staff_crate.json`; it never
edits it). StaffLayer draws the baked model at the fit's `crate.at`/`rot` on a bone, rotating 180 degrees about Z
(bone space is y down) and translating (-0.5, 0, -0.5) first, so the model's origin is the centre of its underside,
(8, 0, 8) in Java block space.

Made of vanilla wood by name (it ships no texture and follows the player's resource pack, as the style guide allows for
a plain vanilla material): four stripped spruce corner posts, two oak boards a side with a 1 px gap between them, a
dark oak core behind the gap (the dark is in the geometry: the gap is a real 1 px recess), and an oak lid set 1 px
below the rim. Every face's UV is the face's own size in pixels (one texel size), cut from rows of the plank texture
that hold no seam (oak and spruce planks have a dark seam on rows 3, 7, 11 and 15); the lid takes one seam on purpose,
so it reads as two planks.

Crate frame (px, y up, origin at the underside's centre): x and z -3..3, y 0..5.
"""
import json
import sys

POST = 'minecraft:block/stripped_spruce_log'
BOARD = 'minecraft:block/oak_planks'
CORE = 'minecraft:block/dark_oak_planks'


def _el(name, frm, to, tex, uv_at, faces=('north', 'south', 'east', 'west', 'up', 'down')):
    """An element in crate px (centred); uv_at: {face: (u, v)} top-left of a crop the face's own size (default 0, 0)."""
    x0, y0, z0 = frm
    x1, y1, z1 = to
    size = {'north': (x1 - x0, y1 - y0), 'south': (x1 - x0, y1 - y0), 'east': (z1 - z0, y1 - y0),
            'west': (z1 - z0, y1 - y0), 'up': (x1 - x0, z1 - z0), 'down': (x1 - x0, z1 - z0)}
    out = {}
    for f in faces:
        u, v = uv_at.get(f, uv_at.get('*', (0, 0)))
        w, h = size[f]
        out[f] = {'uv': [u, v, u + w, v + h], 'texture': '#' + tex}
    return {'name': name, 'from': [x0 + 8, y0, z0 + 8], 'to': [x1 + 8, y1, z1 + 8], 'faces': out}


def elements():
    els = []
    # corner posts: a 1 px column of stripped spruce (even grain, no seam)
    for i, (x, z) in enumerate(((-3, -3), (2, -3), (-3, 2), (2, 2))):
        els.append(_el('post_%d' % i, (x, 0, z), (x + 1, 5, z + 1), 'post', {'*': (3 + 2 * i, 4)}))
    # boards: two a side, rows 0-1 (bottom board) and rows 4-5 (top board) of the oak planks, no seam in either
    sides = {
        'n': ((-2, -3), (2, -2), 'north'), 's': ((-2, 2), (2, 3), 'south'),
        'w': ((-3, -2), (-2, 2), 'west'), 'e': ((2, -2), (3, 2), 'east'),
    }
    for k, ((ax, az), (bx, bz), face) in sides.items():
        off = {'n': 1, 's': 9, 'w': 5, 'e': 11}[k]
        for j, (y0, y1, row) in enumerate(((0, 2, 0), (3, 5, 4))):
            uv = {'*': (off, row), 'up': (off, row), 'down': (off, row + 1)}
            els.append(_el('board_%s%d' % (k, j), (ax, y0, az), (bx, y1, bz), 'board', uv))
    # the core: dark oak behind the gaps, its top the lid (oak, rows 1-4: two planks and the seam between them)
    core = _el('core', (-2, 0, -2), (2, 4, 2), 'core', {'*': (2, 8), 'up': (6, 1)},
               faces=('north', 'south', 'east', 'west', 'up'))
    core['faces']['up']['texture'] = '#board'
    els.append(core)
    return els


def model():
    return {'textures': {'post': POST, 'board': BOARD, 'core': CORE, 'particle': BOARD}, 'elements': elements()}


def java_models():
    return {'assets/headinajar/models/entity/staff_crate.json': model()}


def check():
    els = elements()
    for e in els:
        for i in range(3):
            assert e['to'][i] > e['from'][i], e['name']
    names = [e['name'] for e in els]
    assert len(set(names)) == len(names)
    return len(els)


if __name__ == '__main__':
    print(check(), 'elements')
    if '--json' in sys.argv:
        print(json.dumps(model(), indent=1))

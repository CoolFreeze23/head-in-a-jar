"""Corporate Wellness Treadmill: geometry, and the block and item models the game loads (a plain Java block model on
two cells, like a bed; no block entity renderer, no GeckoLib).

    python art/machines/treadmill/treadmill_geo.py            # writes treadmill.bbmodel (+ placeholder) and checks
    python art/machines/treadmill/paint/paint_treadmill.py    # then paints the sheet and the belt

The data generator imports this file and writes what `java_models()` and `blockstate()` return (it never edits them).

A low belt deck two blocks long that a headless host walks on in place. The head end carries an ivory enamel motor
hood with the maker's plate and louvres, and above it a console: two steel posts, a brass handlebar with rubber grips,
and the Wellness Meter, a round brass dial on a dark instrument box, facing forward, away from the walker (the reading
is for the supervisor). Between 2 px steel side rails runs the rubber belt, 1 px under the rails' tops; under the rails
the dark chassis sits 2 px in (the deck's shadow band, 3 px tall). The belt wraps round the foot roller in sight;
brass axle caps show under the rails at both ends, brass end caps finish the rails where a body steps off.

Numbers (what the game's code and the plan fix):
- Footprint 2 x 1 cells: the foot (where the player clicked) and the head, `foot.relative(facing)`. Model frame:
  facing north, rotation 0; the head is the -Z cell. Here L is the distance from the head end (0) to the foot end
  (32) in px, x the width (-8..8), y up; kit z = L - 24 (the foot cell is the kit's own cell).
- The walker stands on the belt at the seam (L 16), facing the head. The belt's top is y 6: the collision deck is
  6 px high on both cells (plan 3, TreadmillBlock), so feet sit on the belt. The rails rise 1 px above it (y 7).
- Nothing over y 16 (no part in a cell above; a cow's head, the lowest, is at y 16-24 and L 2-8 at the seam).
- The dial's needle: five elements, one per `dial` 0-4, turned -45, -22.5, 0, 22.5, 45 degrees about Z (the five
  angles a Java block model allows): 0 FE/t points up-left, 19+ up-right, as the viewer in front sees it.
- Belt texture: `#belt` on the belt's top and on its wrap round the foot roller, v = the cell's z (and 16 - y on the
  roller's end), so the pattern is continuous over the seam and the running texture's scroll (+v a frame) moves the
  top toward the foot and the end down.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
import kit  # noqa: E402

NAME = 'treadmill'
PATH = os.path.join(HERE, NAME + '.bbmodel')
TEX = 'headinajar:block/machine/'
FACINGS = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
DIAL = [-45, -22.5, 0, 22.5, 45]
DIAL_CENTRE = (0, 11)            # x, y of the dial face's centre (the bezel is round it)
PIVOT_Y = 10.5                   # the needle turns about (0, 10.5): its last half pixel, under the hub
CELL_SHIFT = {'head': 16, 'foot': 0}   # kit z + this = the cell's own kit z (-8..8)

# Collision and outline, Java cell coordinates (0..16), facing north (turn by facing). The deck is 6 px on both cells;
# the head adds the hood and the console with its posts.
SHAPES = {
    'foot': [(0, 0, 0, 16, 6, 16)],
    'head': [(0, 0, 0, 16, 6, 16), (0, 0, 0, 16, 8, 6), (0, 8, 2, 16, 16, 5)],
}


def Z(L):
    return L - 24


def model():
    m = kit.Model(NAME, budget=0)
    for g in ('head', 'foot', 'belt_head', 'belt_foot'):
        m.group(g, 'static')
    for n in range(5):   # one needle shows at a time: the four not at rest are hidden groups (left out of the checks)
        m.group('state_dial_%d' % n, 'static', hidden=n != 2)

    def B(name, x0, x1, y0, y1, L0, L1, mat, grp, part=None, off=()):
        m.box(name, [x0, y0, Z(L0)], [x1, y1, Z(L1)], mat, grp, part=part, off=off)

    # ---- head end: the motor hood (K2 casing, a raised rim round its front panel, 1 px proud) -----------------------
    planes = kit.casing(m, 'hood', -8, 8, Z(1), Z(6), 0, 8, rims=('north',), parent='head')
    kit.makers_plate(m, 'north', planes['north'], -2, 4, 4, 2, parent='head')
    # the console, in bands (a blockout critic read one cream box with a ring as a padlock): two steel posts on the
    # hood, a dark steel instrument box between them set 1 px back, the brass handlebar across the posts' tops with a
    # 1 px gap over the box, and the Wellness Meter: a round brass bezel 1 px proud of the box, its top poking above it
    for s_, (x0, x1) in (('l', (-8, -6)), ('r', (6, 8))):
        B('post_' + s_, x0, x1, 8, 16, 2, 5, 'steel', 'head', part='posts')
    B('console', -6, 6, 8, 13, 3, 5, 'dsteel', 'head')
    B('handlebar', -6, 6, 14, 16, 3, 4, 'brass', 'head')
    cx, cy = DIAL_CENTRE
    B('bezel_t', cx - 2, cx + 2, cy + 2, cy + 3, 2, 3, 'brass', 'head', part='bezel')
    B('bezel_b', cx - 2, cx + 2, cy - 3, cy - 2, 2, 3, 'brass', 'head', part='bezel')
    B('bezel_l', cx - 3, cx - 2, cy - 2, cy + 2, 2, 3, 'brass', 'head', part='bezel')
    B('bezel_r', cx + 2, cx + 3, cy - 2, cy + 2, 2, 3, 'brass', 'head', part='bezel')
    for n in range(5):   # the needle: 1 x 3, level with the bezel's front, pivoting half a pixel from its tail
        # (under the hub; a longer tail past the pivot read as a spinning bar)
        B('needle_%d' % n, cx - 0.5, cx + 0.5, cy - 1, cy + 2, 2, 3, 'steel', 'state_dial_%d' % n, part='needle')
    # the hub: a brass cap 1 px in front of the needle's pivot (a critic read a needle without one as a loose stick)
    B('hub', cx - 0.5, cx + 0.5, cy - 1, cy, 1, 2, 'brass', 'head')

    # ---- the deck, cut at the seam (L 16) into its two cells ----------------------------------------------------
    for cell, (L0, L1) in (('head', (6, 16)), ('foot', (16, 31))):
        c = cell[0]
        B('chassis_' + c, -6, 6, 1, 4, L0, min(L1, 30), 'dsteel', cell, part='chassis')
        B('belt_' + c, -6, 6, 4, 6, L0, L1, 'rubber', 'belt_' + cell, part='belt')
        for s, (x0, x1) in (('l', (-8, -6)), ('r', (6, 8))):
            B('rail_%s_%s' % (s, c), x0, x1, 4, 7, L0, L1, 'steel', cell, part='rail_' + s)
    # the belt round the foot roller (one px lower than the top, then down: a rounded end)
    B('belt_wrap', -6, 6, 2, 5, 31, 32, 'rubber', 'belt_foot', part='belt')
    # brass end caps on the rails at the foot end (where a body steps off), level with the rail's top and 1 px below
    # it (a critic read taller caps as pasted on)
    for s_, (x0, x1) in (('l', (-8, -6)), ('r', (6, 8))):
        B('endcap_' + s_, x0, x1, 3, 7, 31, 32, 'brass', 'foot', part='endcaps')
    # rail legs (1 px in from the rails' outer faces) and the rollers' brass axle caps outside them, at both ends
    for c, (L0, L1) in (('h', (7, 9)), ('f', (28, 30))):
        cell = {'h': 'head', 'f': 'foot'}[c]
        for s, (lx, cxx) in (('l', ((-7, -6), (-8, -7))), ('r', ((6, 7), (7, 8)))):
            B('leg_%s_%s' % (s, c), lx[0], lx[1], 0, 4, L0, L1, 'steel', cell, part='legs')
            B('cap_%s_%s' % (s, c), cxx[0], cxx[1], 1, 3, L0, L1, 'brass', cell, part='caps')
    # rubber feet under the chassis, inside the legs (the hood stands on the floor)
    for c, (L0, L1) in (('h', (10, 12)), ('f', (20, 22))):
        cell = {'h': 'head', 'f': 'foot'}[c]
        for s, (x0, x1) in (('l', (-5, -3)), ('r', (3, 5))):
            B('foot_%s_%s' % (s, c), x0, x1, 0, 1, L0, L1, 'rubber', cell, part='feet')

    m.locator('walker', [0, 6, Z(16)])          # the walker's feet: the seam, on the belt
    m.locator('dial', [cx, cy, Z(2)])
    return m


def _num(v):
    v = round(v, 6)
    return int(v) if v == int(v) else v


def _load():
    with open(PATH, encoding='utf-8') as fh:
        return json.load(fh)


def _groups(d):
    """{element uuid: name of its group} for the groups directly holding cubes."""
    names = {g['uuid']: g['name'] for g in d['groups']}
    out = {}

    def walk(nodes, owner):
        for n in nodes:
            if isinstance(n, str):
                out[n] = owner
            else:
                walk(n['children'], names[n['uuid']])
    walk(d['outliner'], None)
    return out


def _cells(d):
    """Elements by drawing group: head, foot, belt_head, belt_foot, dial_0..4."""
    owner = _groups(d)
    out = {}
    for e in d['elements']:
        if e.get('type', 'cube') != 'cube':
            continue
        g = owner[e['uuid']]
        out.setdefault(g.replace('state_', ''), []).append(e)
    return out


def _box(e):
    return (e['name'], list(e['from']), list(e['to']), None, None, {'off': tuple(k for k, f in e['faces'].items()
                                                                              if f.get('texture') is None)})


def _hidden_faces(e, occluders):
    """Faces of e pressed against other boxes that are always drawn (never seen): left out of the game model."""
    me = _box(e)
    occ = [_box(o) for o in occluders if o is not e]
    occ_k = [(o[0], o[1], o[2], 'x', None, o[5]) for o in occ]
    out = set()
    names = {(0, -1): 'west', (0, 1): 'east', (1, -1): 'down', (1, 1): 'up', (2, -1): 'north', (2, 1): 'south'}
    for face in kit._faces(me):
        if kit._hidden(face, me, occ_k):
            out.add(names[(face[0], face[1])])
    return out


def _element(d, e, shift, own_tex=None, always=()):
    """A Java element from a kit cube: kit z + shift into its cell, + 8 on x and z; UVs on the sheet, or for the
    belt's top and ends on `#belt` by the cell's position."""
    su, sv = 16 / d['resolution']['width'], 16 / d['resolution']['height']
    f = [e['from'][0] + 8, e['from'][1], e['from'][2] + shift + 8]
    t = [e['to'][0] + 8, e['to'][1], e['to'][2] + shift + 8]
    hidden = _hidden_faces(e, always)
    faces = {}
    for face, data in e['faces'].items():
        if data.get('texture') is None or face in hidden:
            continue
        if own_tex and face == 'up':
            tex, uv = own_tex, [f[0], f[2], t[0], t[2]]
        elif own_tex and face == 'south':
            tex, uv = own_tex, [f[0], 16 - t[1], t[0], 16 - f[1]]
        else:
            u1, v1, u2, v2 = data['uv']
            tex, uv = '#0', [u1 * su, v1 * sv, u2 * su, v2 * sv]
        out = {'uv': [_num(x) for x in uv], 'texture': tex}
        axis, plane = {'west': (0, f[0]), 'east': (0, t[0]), 'down': (1, f[1]), 'up': (1, t[1]),
                       'north': (2, f[2]), 'south': (2, t[2])}[face]
        if plane in (0, 16):
            out['cullface'] = face
        faces[face] = out
    el = {'from': [_num(v) for v in f], 'to': [_num(v) for v in t], 'faces': faces}
    rot = e.get('rotation') or [0, 0, 0]
    if any(rot):
        o = e['origin']
        el['rotation'] = {'angle': rot[2], 'axis': 'z', 'origin': [_num(o[0] + 8), _num(o[1]), _num(o[2] + shift + 8)]}
    return el


def _model(elements, textures, ao=True):
    m = {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout', 'textures': textures,
         'elements': elements}
    if not ao:
        m['ambientocclusion'] = False
    return m


SHEET = {'0': TEX + NAME, 'particle': TEX + NAME}


def java_models():
    """{resource path: model} for the block and the item, built from treadmill.bbmodel."""
    d = _load()
    cells = _cells(d)
    always = cells['head'] + cells['foot'] + cells['belt_head'] + cells['belt_foot']
    # occluders in one frame: the head's kit z is 16 lower; test hiding in the kit's two-cell frame
    out = {}
    B = 'assets/headinajar/models/block/'
    for cell in ('head', 'foot'):
        shift = CELL_SHIFT[cell]
        out[B + 'treadmill_%s.json' % cell] = _model([_element(d, e, shift, always=always) for e in cells[cell]],
                                                     dict(SHEET))
        belt = [_element(d, e, shift, own_tex='#belt', always=always) for e in cells['belt_' + cell]]
        out[B + 'treadmill_%s_belt.json' % cell] = _model(belt, dict(SHEET, belt=TEX + 'treadmill_belt'))
        out[B + 'treadmill_%s_belt_running.json' % cell] = {
            'parent': 'headinajar:block/treadmill_%s_belt' % cell, 'textures': {'belt': TEX + 'treadmill_belt_running'}}
    for n in range(5):
        out[B + 'treadmill_dial_%d.json' % n] = _model([_element(d, e, 16) for e in cells['dial_%d' % n]], dict(SHEET))
    out['assets/headinajar/models/item/treadmill.json'] = item_model(d, cells, always)
    return out


def item_model(d, cells, always):
    """The whole treadmill on one model for the item: both cells (the head 16 px further -Z), the belt still, the
    needle at dial 2 (upright). Centred on the item's origin along its length (z -8..24)."""
    els = []
    sh = 8      # kit z + 8: the head at Java z -8..8, the foot at 8..24
    for cell in ('head', 'foot'):
        for e in cells[cell]:
            els.append(_element(d, e, sh, always=always))
        for e in cells['belt_' + cell]:
            els.append(_element(d, e, sh, own_tex='#belt', always=always))
    els += [_element(d, e, sh) for e in cells['dial_2']]
    for el in els:
        for f in el['faces'].values():
            f.pop('cullface', None)
    disp = {
        'gui': {'rotation': [30, 245, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.2, 0.2, 0.2]},
        'fixed': {'rotation': [0, 90, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.3, 0.3, 0.3]},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.3, 0.3, 0.3]},
        'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [0.3, 0.3, 0.3]},
    }
    return {'parent': 'minecraft:block/block', 'textures': dict(SHEET, belt=TEX + 'treadmill_belt'), 'elements': els,
            'display': disp}


def blockstate():
    """Multipart: the body per part, the belt per part and running, the needle per dial on the head; all turned by
    facing (facing = from the foot to the head)."""
    parts = []
    for face, y in FACINGS.items():
        def apply(model):
            return dict({'model': 'headinajar:block/' + model}, **({'y': y} if y else {}))
        for cell in ('foot', 'head'):
            parts.append({'when': {'facing': face, 'part': cell}, 'apply': apply('treadmill_' + cell)})
            for run in ('false', 'true'):
                parts.append({'when': {'facing': face, 'part': cell, 'running': run},
                              'apply': apply('treadmill_%s_belt%s' % (cell, '_running' if run == 'true' else ''))})
        for n in range(5):
            parts.append({'when': {'facing': face, 'part': 'head', 'dial': str(n)},
                          'apply': apply('treadmill_dial_%d' % n)})
    return {'multipart': parts}


def _turn_needles(path):
    """Kit cubes cannot turn; the needles are turned here (about Z, through the dial's centre)."""
    with open(path, encoding='utf-8') as fh:
        d = json.load(fh)
    cx, cy = DIAL_CENTRE
    for e in d['elements']:
        if e['name'].startswith('needle_'):
            n = int(e['name'].rsplit('_', 1)[1])
            e['rotation'] = [0, 0, DIAL[n]]
            e['origin'] = [cx, PIVOT_Y, (e['from'][2] + e['to'][2]) / 2]
            # in review renders only dial 2 shows (the others overlap it)
            e['visibility'] = n == 2
    with open(path, 'w', encoding='utf-8') as fh:
        json.dump(d, fh, indent=1)


if __name__ == '__main__':
    mm = model()
    mm.write(PATH)
    _turn_needles(PATH)
    ms = java_models()
    n = sum(len(v.get('elements', [])) for k, v in ms.items() if 'item' not in k)
    print('%d models, %d block elements' % (len(ms), n))

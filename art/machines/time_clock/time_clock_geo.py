"""Time Clock: geometry.

    python art/machines/time_clock/time_clock_geo.py    # writes time_clock.bbmodel and its placeholder PNG
    python art/machines/time_clock/time_clock_anim.py   # then the clips (and the review copy)

The site's punch clock on its stand, one block (the bell on top stands 4 px over it, as the Signal Jammer's turbine
does). From the floor up: a dark steel flange; a steel post with the Noggin lamp on its front; either side of the post
the Job Rack's two wings, each with two tiers of pocket lips holding up to four card slips (IN on the viewer's left,
OUT on the right; the upper tier 1 px further back, so no two faces meet flush); on top the clock cabinet in gunmetal
japanning. Its front is the dial alone: an 8 px round brass bezel 1 px proud round a 6 px cream face (painted), the
hour and minute hands. On its right flank (x -6) the punch station: the card slot in a brass throat, the lever above
it (yellow grip forward). Over the cabinet a 1 px riveted awning, 3 px out over the dial (the shade undead staff
stand in); on the awning, over the punch station, the front desk's service bell: a brass dome on a dark base with a
plunger. Two blind readers' first guess was a punch clock; the first one read the dial and the slot side by side as
two eyes, so the slot moved to the flank.

Bones (7; everything else is baked):
    hour_hand    pivot (0, 11, -3);       rotation Z, keyed in `dial` (seeked to the day; 24-hour dial, noon up)
    minute_hand  pivot (0, 11, -4);       rotation Z, keyed in `minute` (seeked to the hour; jumps once a minute)
    card         pivot (-6, 9.5, 1);      position X, keyed in `punch` (the time card out, in, shown and kept)
    lever        pivot (-7.5, 13.5, 3);   rotation X, keyed in `punch` (-26 = down; at -30 the grip would touch the
                                          throat)
    bell         pivot (-4, 17, 1);       rotation X and Z, keyed in `punch` (a shiver) and `ding` (a ring)
    plunger      pivot (-4, 19, 1);       child of bell; position Y, keyed in `punch` and `ding`
    strike_card  pivot (-4, 17, -6);      child of bell; HIDDEN in 0.7.0 (the renderer hides it; 0.7.2 shows it),
                                          a manila card tied to the bell, hung over the awning's front edge, 5 degrees
                                          askew, in front of the punch station's corner and the dial's right edge
Blockstate parts (baked, in `static`): `state_lamp` (the kit lamp), `state_card_1`..`state_card_8` (slip n is drawn
while the rack holds n cards or more; 1-4 the upper tier from the viewer's left, 5-8 the lower).
The hands are 1 px wide about the dial's centre, a pixel corner (an even dial is centred on the block), so they sit
half a pixel off the grid at rest; they are never at rest in the game.
Faces -Z; +X is the machine's right (the viewer's left when facing it).
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
import kit  # noqa: E402

NAME = 'time_clock'
PATH = os.path.join(HERE, NAME + '.bbmodel')
REVIEW = os.path.join(HERE, '..', '..', '..', 'build', 'machines-review', NAME + '_review.bbmodel')

DIAL = (0, 11)              # the dial's centre (x, y)
FRONT = -2                  # the cabinet's front plane
# the slips, in the order they fill: the upper row from the viewer's left, then the lower row
SLIPS = [(x, row) for row in (1, 0) for x in (5, 2, -4, -7)]


def model():
    m = kit.Model(NAME, budget=7)
    cab = 'brass'                     # placeholder colours only; the paint decides (japanned black)
    # ---- the stand: flange and post ----
    m.box('flange', [-5, 0, -3], [5, 1, 5], 'dsteel')
    m.box('post', [-2, 1, -1], [2, 7, 3], 'steel')
    kit.lamp(m, 'north', -1, -2, 2)                                   # on the post: bezel x -2..2, y 2..6
    # ---- the Job Rack: a wing each side of the post, two tiers (the upper row 1 px further back), eight slips ----
    for side, (x0, x1) in (('l', (2, 7)), ('r', (-7, -2))):
        m.box('wing_' + side, [x0, 1, 0], [x1, 7, 1], 'steel', part='wings')
        m.box('step_' + side, [x0, 1, -1], [x1, 4, 0], 'steel', part='wings')
        m.box('lip_%s0' % side, [x0, 1, -3], [x1, 2, -2], 'dsteel', part='lips')
        m.box('lip_%s1' % side, [x0, 4, -2], [x1, 5, -1], 'dsteel', part='lips')
    for n, (x, row) in enumerate(SLIPS, 1):
        g = m.group('state_card_%d' % n)
        y, z = (4, -1) if row else (1, -2)
        m.box('slip_%d' % n, [x, y, z], [x + 2, y + 3, z + 1], 'enamel', g)

    # ---- the cabinet and the awning ----
    m.box('cabinet', [-6, 7, FRONT], [6, 15, 4], cab)
    # the awning: a 1 px roof, 3 px out over the dial (the shade the undead staff stand in)
    m.box('awning', [-7, 15, -5], [7, 16, 5], 'dsteel')
    # the dial, centred on the front: an 8 px round bezel 1 px wide and 1 px proud; the face is painted on the
    # cabinet's front inside it
    ring = [(-2, 2, 7, 8), (-3, -2, 8, 9), (2, 3, 8, 9), (-4, -3, 9, 13), (3, 4, 9, 13), (-3, -2, 13, 14),
            (2, 3, 13, 14), (-2, 2, 14, 15)]
    for i, (x0, x1, y0, y1) in enumerate(ring):
        m.box('bezel_%d' % i, [x0, y0, FRONT - 1], [x1, y1, FRONT], 'dsteel' if cab == 'brass' else 'brass',
              part='bezel')
    cx, cy = DIAL
    # the hands (bones; rest = both straight up), 1 px wide about the dial's centre: the hour hand level with the
    # bezel, the minute hand (with a 1 px tail) 1 px in front of it
    m.bone('hour_hand', [cx, cy, FRONT - 1])
    m.box('hour', [cx - 0.5, cy - 0.5, FRONT - 1], [cx + 0.5, cy + 2.5, FRONT], 'rubber', 'hour_hand')
    m.bone('minute_hand', [cx, cy, FRONT - 2])
    m.box('minute', [cx - 0.5, cy - 1.5, FRONT - 2], [cx + 0.5, cy + 2.5, FRONT - 1], 'rubber', 'minute_hand')

    # ---- the punch station on the right flank (x -6): the card slot in a brass throat, the lever above it ----
    kit.rim(m, 'throat', 'west', -6, -1, 3, 8, 11, mat='brass', depth=2)       # z -1..3, y 8..11, x -8..-6
    m.box('slit', [-7, 9, 0], [-6, 10, 2], 'rubber')
    # the time card: at rest inside the cabinet (out of sight); `punch` pushes it out 3 px and takes it back in
    m.bone('card', [-6, 9.5, 1])
    m.box('time_card', [-5, 9, 0], [0, 10, 2], 'enamel', 'card')
    m.box('lever_boss', [-7, 12, 2], [-6, 15, 4], 'brass')
    m.bone('lever', [-7.5, 13.5, 3])
    m.box('lever_arm', [-8, 13, -2], [-7, 14, 4], 'steel', 'lever')
    m.box('lever_grip', [-8, 12, -4], [-7, 15, -2], 'rubber', 'lever')

    # ---- the bell on top, over the punch station: the front desk's service bell (a brass dome on a dark base, rung
    # by its plunger), set off to the right so it never reads as a hat on the awning ----
    BX, BZ = -4, 1
    m.box('bell_base', [BX - 2, 16, BZ - 2], [BX + 2, 17, BZ + 2], 'dsteel')
    m.bone('bell', [BX, 17, BZ])
    m.box('bell_dome', [BX - 2, 17, BZ - 2], [BX + 2, 18, BZ + 2], 'brass', 'bell')
    m.box('bell_top', [BX - 1, 18, BZ - 1], [BX + 1, 19, BZ + 1], 'brass', 'bell')
    m.bone('plunger', [BX, 19, BZ], 'bell')
    m.box('plunger_cap', [BX - 0.5, 19, BZ - 0.5], [BX + 0.5, 20, BZ + 0.5], 'rubber', 'plunger')
    # the ON STRIKE card, tied to the bell's knob and hung over the awning's front edge (hidden in 0.7.0)
    m.bone('strike_card', [BX, 17, -6], 'bell', rot=[0, 0, 5], hidden=True)
    m.box('strike_string', [BX - 0.5, 14, -6], [BX + 0.5, 17, -5], 'rubber', 'strike_card')
    m.box('strike_tie', [BX - 0.5, 17, -5], [BX + 0.5, 18, BZ - 2], 'rubber', 'strike_card')
    m.box('strike_card_card', [BX - 3, 10, -7], [BX + 3, 14, -6], 'enamel', 'strike_card')
    m.locator('bell', [BX, 19, BZ], 'bell')
    m.locator('slot', [-9, 9.5, 1], None)

    # ---- collision and selection (Java cell coordinates) ----
    m.shape('lower', (1, 0, 5, 15, 7, 13))           # flange, post and rack
    m.shape('lower', (1, 7, 4, 15, 15, 12))          # cabinet, dial, throat, lever
    m.shape('lower', (1, 15, 3, 15, 16, 13))         # awning
    return m


# ---------------------------------------------------------------------------------------------------------------------
# What the game loads, built from time_clock.bbmodel with the machine generator's own converters (for the data
# generator: it writes these; nothing here writes into the assets)
# ---------------------------------------------------------------------------------------------------------------------
TEX = 'headinajar:block/machine/'
LAMPS = ['off', 'ready', 'wait', 'work', 'low', 'fault']
LIT = {'ready', 'wait', 'work', 'low', 'fault'}
FACINGS = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
CARDS = 8
# the item: the hands at a still pose a Java model can turn (Java cell coordinates for the origin)
ITEM_POSE = {'hour': ('z', 45.0, [8, 11, 5.5]), 'minute': ('z', 0.0, [8, 11, 5.5])}
ITEM_BONES = ('hour_hand', 'minute_hand', 'lever', 'bell', 'plunger')


def _gen():
    sys.path.insert(0, os.path.join(HERE, '..', '..', '..', 'tools'))
    import gen_p18_machines as G
    return G


def _load():
    with open(PATH, encoding='utf-8') as fh:
        return json.load(fh)


def java_models():
    """{resource path: model} for the block and the item. Block: the body (every static cube; one block, not cut),
    the lamp's lens as the kit machines have it (an unlit and a lit parent, one child per lamp value naming the kit's
    lens sprite), and one model per rack slip (time_clock_card_<n>). Item: the whole clock at rest with the lamp
    green and the rack full."""
    G = _gen()
    d = _load()
    sheet = G._sheet_uv(d)
    B = 'assets/headinajar/models/block/'
    out = {}
    body = [G._element(d, e, sheet) for e in G._cubes(d, G._find(d, 'static'))]
    out[B + NAME + '.json'] = G._model(NAME, body, G._sheet(NAME))
    lens = G._cubes(d, G._find(d, 'state_lamp'))[0]
    unlit = G._element(d, lens, G._own_uv(lens, '#lens'))
    lit = dict(unlit, **{'neoforge_data': {'block_light': 15, 'sky_light': 15}})
    lit['shade'] = False
    out[B + NAME + '_lamp.json'] = G._model(NAME, [unlit], {'particle': TEX + NAME})
    out[B + NAME + '_lamp_lit.json'] = G._model(NAME, [lit], {'particle': TEX + NAME}, ao=False)
    for st in LAMPS:
        out[B + '%s_lamp_%s.json' % (NAME, st)] = {
            'parent': 'headinajar:block/%s_lamp%s' % (NAME, '_lit' if st in LIT else ''),
            'textures': {'lens': TEX + 'kit/lamp_' + st}}
    cards = []
    for n in range(1, CARDS + 1):
        els = [G._element(d, e, sheet) for e in G._cubes(d, G._find(d, 'state_card_%d' % n))]
        out[B + '%s_card_%d.json' % (NAME, n)] = G._model(NAME, els, G._sheet(NAME))
        cards += els
    out['assets/headinajar/models/item/%s.json' % NAME] = item_model(d, G, body, cards, lens)
    return out


def item_model(d, G, body, cards, lens):
    """The whole clock: body, full rack, the bones at rest (hands posed by ITEM_POSE), the lens green."""
    sheet = G._sheet_uv(d)
    bones = []
    for name in ITEM_BONES:
        for e in G._cubes(d, G._find(d, name)):
            el = G._element(d, e, sheet)
            if e['name'] in ITEM_POSE and ITEM_POSE[e['name']][1]:
                ax, ang, origin = ITEM_POSE[e['name']]
                el['rotation'] = {'angle': ang, 'axis': ax, 'origin': origin}
            bones.append(el)
    lamp = G._element(d, lens, G._own_uv(lens, '#lens'))
    return {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout',
            'textures': dict(G._sheet(NAME), lens=TEX + 'kit/lamp_ready'), 'elements': body + cards + bones + [lamp],
            'display': {
                'gui': {'rotation': [20, 290, 0], 'translation': [0, -1, 0], 'scale': [0.55, 0.55, 0.55]},
                'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.25, 0.25, 0.25]},
                'fixed': {'rotation': [0, 0, 0], 'translation': [0, -1, 0], 'scale': [0.5, 0.5, 0.5]},
                'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0],
                                          'scale': [0.375, 0.375, 0.375]},
                'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
                'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]}}}


def blockstate():
    """Multipart, turned by `facing` (horizontal, north = rotation 0): the body; the lens by `lamp` (the kit's
    MachineLamp values); slip n while `cards` (0-8) is n or more."""
    parts = []
    for face, y in FACINGS.items():
        def apply(model, y=y):
            return dict({'model': 'headinajar:block/' + model}, **({'y': y} if y else {}))
        parts.append({'when': {'facing': face}, 'apply': apply(NAME)})
        for st in LAMPS:
            parts.append({'when': {'facing': face, 'lamp': st}, 'apply': apply('%s_lamp_%s' % (NAME, st))})
        for n in range(1, CARDS + 1):
            parts.append({'when': {'facing': face, 'cards': '|'.join(str(i) for i in range(n, CARDS + 1))},
                          'apply': apply('%s_card_%d' % (NAME, n))})
    return {'multipart': parts}


def geo_and_animations():
    """(geo.json, animation.json) as dicts, through the machine generator's Bedrock writer and the clip exporter."""
    import tempfile
    G = _gen()
    g = G.geo(_load(), NAME)
    with tempfile.TemporaryDirectory() as tmp:
        p = os.path.join(tmp, NAME + '.geo.json')
        with open(p, 'w', encoding='utf-8') as fh:
            json.dump(g, fh)
        return g, G.animations(NAME, p)


if __name__ == '__main__':
    mm = model()
    mm.write(PATH)
    print(kit.shapes_java(mm))
    ms = java_models()
    print('%d models' % len(ms))

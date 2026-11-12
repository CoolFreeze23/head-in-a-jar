"""Relay Mast Mk II: geometry.

    python art/machines/relay_mast_mk2/mk2_geo.py      # writes relay_mast_mk2.bbmodel and its placeholder PNG
    python art/machines/relay_mast_mk2/mk2_anim.py     # then the clips (and the review copy)

A tripod radar station: four splayed steel legs (baked, 22.5 degrees: the front and back legs turned
about X, the left and right about Z) on rubber feet, a dark hub with the lamp in its front, a brass pole, and on the
pole a 16 x 16 dish (the Mk I's construction, a size up, with a stepped second ring and a small receiver dish on its
back). Above the pole the telescoping top stage carries the red aviation lamp: it rests at 30 px and rises 2 px while
the mast carries a link. Told from the Mk I at range by its open tripod (you see the floor through it), its bigger dish
and the stage. 30 px at rest (32 with the stage up), top block; collision is the centre column.

Bones (2):
    dish     pivot (0, 20, 0) on the pole's axis; rotation CODE (Y the aim, X the droop and flinch), as the Mk I's
    stage2   pivot (0, 24, 0); position KEYED (stage_up / stage_down: +2 px)
Faces -Z; +X is the machine's right.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
sys.path.insert(0, os.path.join(HERE, '..', 'relay_mast'))
import kit  # noqa: E402
import mast_geo  # noqa: E402

PATH = os.path.join(HERE, 'relay_mast_mk2.bbmodel')
REVIEW = 'build/machines-review/relay_mast_mk2_review.bbmodel'
DISH_Y = 20
LEG_TILT = 22.5
# the four legs: (name, centre x, centre z, element rotation [x, y, z]); a leg is a 2x12x2 box turned about its centre
# (the feet splay out onto the pads, the tops meet at the collar)
LEGS = [('leg_f', 0, -5, [LEG_TILT, 0, 0]), ('leg_b', 0, 5, [-LEG_TILT, 0, 0]),
        ('leg_l', -5, 0, [0, 0, -LEG_TILT]), ('leg_r', 5, 0, [0, 0, LEG_TILT])]
LEG_Y = (1, 13)          # the unturned box's height (turned about its centre, the foot lands about 0.5 px up)


def model():
    m = kit.Model('relay_mast_mk2', budget=2)

    # ---- the tripod: four legs (turned in the bbmodel after writing: see turn_legs), each on a rubber foot at the
    # block's edge; the hub where they meet ----
    for name, cx, cz, rot in LEGS:
        m.box(name, [cx - 1, LEG_Y[0], cz - 1], [cx + 1, LEG_Y[1], cz + 1], 'steel', part='legs')
    # the feet: rubber pads 3x2x3 under each leg's foot (the turned leg's foot lands about 2.3 px further out)
    for name, cx, cz, rot in LEGS:
        fx = cx + (-2 if cx < 0 else 2 if cx > 0 else 0)
        fz = cz + (-2 if cz < 0 else 2 if cz > 0 else 0)
        m.box('pad_' + name[-1], [fx - 1.5, 0, fz - 1.5], [fx + 1.5, 2, fz + 1.5], 'rubber', part='pad_' + name[-1])
    # v2: no torso box: a dark collar where the legs meet (the lamp on its front), and the brass pole rising out of it
    m.box('leg_collar', [-3, 9, -3], [3, 17, 3], 'dsteel')
    kit.lamp(m, 'north', -3, -2, 12.5)                         # bezel y 12.5-16.5 on the collar's front, over the legs' tops

    # ---- the brass pole, a visible mast from the collar to the stage ----
    m.box('pole', [-2, 17, -2], [2, 24, 2], 'brass', part='pole')

    # ---- the dish (bone): a collar, a yoke, the 16 px dish with its second ring and the receiver on its back ----
    m.bone('dish', [0, DISH_Y, 0], code=('rotation',))
    m.box('collar', [-3, DISH_Y - 2, -3], [3, DISH_Y + 2, 3], 'dsteel', 'dish')
    m.box('yoke', [-2, DISH_Y - 1, -7], [2, DISH_Y + 1, -3], 'dsteel', 'dish')       # v2: 4 wide, into the can
    mast_geo.dish(m, 'dish', DISH_Y, 8, -11, bowl_back=3, receiver=True)                # the bowl 3 deep (the Mk I's 2)
    m.locator('horn', [0, DISH_Y, -16], 'dish')

    # ---- the top stage (bone): a dark telescoping rod out of the pole's top, the aviation lamp on it ----
    # v2: at rest the lamp sits behind the dish's top (rim top y 28); carrying a link it rises 4 px over the outline
    m.bone('stage2', [0, 24, 0])
    m.box('stage_rod', [-1, 19, -1], [1, 24, 1], 'dsteel', 'stage2', part='stage')
    m.box('beacon_base', [-2, 24, -2], [2, 26, 2], 'brass', 'stage2', part='beacon_b')
    m.box('beacon_lens', [-1, 26, -1], [1, 28, 1], 'lens_fault', 'stage2', part='beacon_l')
    m.locator('beacon', [0, 28, 0], 'stage2')

    # ---- cell shapes: the centre column only ----
    m.shape('lower', (5, 0, 5, 11, 16, 11))
    m.shape('upper', (6, 0, 6, 10, 12, 10))
    return m


def turn_legs(path):
    """Give the four leg elements their baked 22.5-degree turns (about their centres; one axis each, as a block
    model allows). The kit writes no element rotation, so it is set here; legs end at y 13, under the cell line."""
    with open(path, encoding='utf-8') as f:
        d = json.load(f)
    rots = {name: rot for name, cx, cz, rot in LEGS}
    for e in d['elements']:
        if e.get('name') in rots:
            e['rotation'] = rots[e['name']]
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(d, f, indent=1)


if __name__ == '__main__':
    m = model()
    m.write(PATH)
    turn_legs(PATH)
    print(kit.shapes_java(m))

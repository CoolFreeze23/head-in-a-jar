"""Signal Jammer: geometry.

    python art/machines/signal_jammer/jammer_geo.py     # writes signal_jammer.bbmodel and its placeholder PNG
    python art/machines/signal_jammer/jammer_anim.py    # then the review copy (its one bone is all code)

A rooftop whirlybird ventilator that "scrambles the air": K1 feet and plinth, a squat enamel drum (K2)
with a louvred grille and the lamp in its front panel, a 14x4x12 warning band (striped: the turbine above it moves into
space), two brass whip antennas (one upright with a cross-tip, one crooked at 22.5 degrees), and on top the turbine: steel blades radiating from a
dark core (eight blades at 45-degree steps, the diagonal four turned in the bone), one blade hazard yellow so the spin reads from across
a room, a brass cap. 20 px, one block (no top cell).

Bones (1):
    rotor   pivot (0, 13, 0); rotation CODE (Y: the spin, speed chased; never keyed)
Faces -Z; +X is the machine's right.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
import kit  # noqa: E402

PATH = os.path.join(HERE, 'signal_jammer.bbmodel')
REVIEW = 'build/machines-review/signal_jammer_review.bbmodel'
TURNED = {'fin_ne': [0, 45, 0], 'fin_sw': [0, 45, 0], 'fin_nw': [0, -45, 0], 'fin_se': [0, -45, 0],
          'antenna_r_top': [0, 0, -22.5]}


def model():
    m = kit.Model('signal_jammer', budget=1)
    kit.plinth(m, -5, 5, -5, 5)
    kit.casing(m, 'drum', -6, 6, -6, 6, 3, 9, rims=('north',))          # v2: 6 px (the turbine is the big shape)
    # the grille: a rubber louvre box in the panel's left part with two enamel slats across it (the front dark), the
    # lamp in the right part
    m.box('grille', [-4, 4, -7], [-1, 8, -6], 'rubber')
    m.box('slat_1', [-4, 5, -8], [-1, 6, -7], 'enamel2', part='slats')
    m.box('slat_2', [-4, 7, -8], [-1, 8, -7], 'enamel2', part='slats')
    kit.lamp(m, 'north', -6, 0, 4)
    kit.makers_plate(m, 'east', 6, -2, 5)
    # the service hatch on the back (the back's one feature)
    m.box('back_hatch', [-3, 4, 6], [3, 8, 7], 'enamel2', part='back_hatch')
    # the warning band under the turbine (it moves into space above it)
    kit.stripe_box(m, 'band', [-7, 9, -6], [7, 13, 6])
    # v2: the antennas stand on the band's top at opposite corners, inside the block and clear of the turbine's sweep:
    # an upright whip with a cross tip at the back left, a crooked whip at the front right (its upper half turned
    # 22.5 degrees, after writing)
    m.box('antenna_l', [-7, 13, 4], [-5, 19, 6], 'brass', part='antenna_l')
    m.box('antenna_l_tip', [-8, 19, 4], [-5, 20, 7], 'brass', part='antenna_l_tip')
    m.box('antenna_r', [5, 13, -6], [7, 17, -4], 'brass', part='antenna_r')
    m.box('antenna_r_top', [5.5, 17, -5.5], [6.5, 20, -4.5], 'brass', part='antenna_r_top')

    # ---- the turbine (bone), v2: a stepped dome. Lower tier: eight blades at 45-degree steps (r 2..6, the four on the
    # axes 3 tall, the diagonal four 2 tall and turned 45 degrees about their own centres); upper tier: the four axis
    # blades step in to r 5 for 2 px more; a 4x4 cap over the dark core. The east blade is the hazard-yellow marker:
    # 2 px thick, and its upper step rises 1 px over the others, so it rides along the top edge as it turns ----
    m.bone('rotor', [0, 13, 0], code=('rotation',))
    m.box('rotor_ring', [-4, 13, -4], [4, 14, 4], 'dsteel', 'rotor')
    m.box('core', [-2, 14, -2], [2, 19, 2], 'dsteel', 'rotor')
    for tag, (x0, x1, z0, z1), (u0, u1, w0, w1), mat, top in (
            ('e', (2, 6, -1, 1), (2, 5, -1, 1), 'yellow', 20), ('w', (-6, -2, -0.5, 0.5), (-5, -2, -0.5, 0.5), 'steel', 19),
            ('n', (-0.5, 0.5, -6, -2), (-0.5, 0.5, -5, -2), 'steel', 19), ('s', (-0.5, 0.5, 2, 6), (-0.5, 0.5, 2, 5), 'steel', 19)):
        m.box('fin_' + tag, [x0, 14, z0], [x1, 17, z1], mat, 'rotor', part='fin_' + tag)
        m.box('fin_%s_up' % tag, [u0, 17, w0], [u1, top, w1], mat, 'rotor', part='fin_' + tag)
    c = 4 / 2 ** 0.5
    for tag, (cx, cz) in (('ne', (c, -c)), ('nw', (-c, -c)), ('se', (c, c)), ('sw', (-c, c))):
        m.box('fin_' + tag, [cx - 2, 14.5, cz - 0.5], [cx + 2, 16.5, cz + 0.5], 'steel', 'rotor', part='fin_' + tag)
    m.box('cap', [-2, 19, -2], [2, 20, 2], 'brass', 'rotor')
    m.locator('crown', [0, 20, 0], 'rotor')
    m.locator('grille', [-2.5, 7, -8])

    m.shape('lower', (2, 0, 2, 14, 9, 14))
    m.shape('lower', (1, 9, 2, 15, 16, 14))
    return m


def turn(path):
    with open(path, encoding='utf-8') as f:
        d = json.load(f)
    for e in d['elements']:
        if e.get('name') in TURNED:
            e['rotation'] = TURNED[e['name']]
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(d, f, indent=1)


if __name__ == '__main__':
    m = model()
    m.write(PATH)
    turn(PATH)
    print(kit.shapes_java(m))

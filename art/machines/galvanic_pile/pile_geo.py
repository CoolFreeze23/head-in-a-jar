"""Galvanic Pile (the Volta Press): geometry, from the machine's design with the changes listed in the model notes.

    python art/machines/galvanic_pile/pile_geo.py     # writes galvanic_pile.bbmodel and its placeholder PNG
    python art/machines/galvanic_pile/pile_anim.py    # then writes the clips into it

A Volta pile crossed with a cheese press: a glass pan of meat on a plinth, a steel base plate on four posts, a striped
column of copper, felt and zinc discs between four brass rods, a brass press plate with two copper terminals, and a
T-handle on a screw. Faces -Z. 32 px tall: the shared hidden top cell above it.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
import kit  # noqa: E402

PATH = os.path.join(HERE, 'galvanic_pile.bbmodel')


def model():
    m = kit.Model('galvanic_pile', budget=3)

    # ---- base: K1 plinth and feet (feet inset 1 px under the plinth) -------------------------------------------
    kit.plinth(m, -7, 7, -7, 7)

    # ---- the electrolyte pan: open-topped glass on the plinth, a rubber floor (the machine's strong dark) -------
    m.box('pan_front', [-6, 3, -5], [6, 8, -4], 'glass', part='pan')
    m.box('pan_back', [-6, 3, 4], [6, 8, 5], 'glass', part='pan')
    m.box('pan_left', [-6, 3, -4], [-5, 8, 4], 'glass', part='pan')
    m.box('pan_right', [5, 3, -4], [6, 8, 4], 'glass', part='pan')
    m.box('pan_floor', [-5, 3, -4], [5, 4, 4], 'rubber')
    for tag, x, z in [('fl', -5, -4), ('fr', 4, -4), ('bl', -5, 3), ('br', 4, 3)]:
        m.box('pan_post_' + tag, [x, 4, z], [x + 1, 8, z + 1], 'steel', part='pan_posts')

    # ---- feed chutes: K6b side ports, both sides (frame 1 px out from the plinth line, mouth 1 px in) ----------
    kit.side_port(m, 'west', -7, 0, 2, depth=1, prefix='port_l')
    kit.side_port(m, 'east', 7, 0, 2, depth=1, prefix='port_r')

    # ---- the maker's plate (K7) stands on the plinth behind the pan; the front glass is kept clear for the meat --
    kit.makers_plate(m, 'south', 5, -2, 3, 4, 2)

    # ---- the frame: a full-width base plate on the pan posts (overhanging the glass 2 px: the pan's shadow), four
    # brass rods standing 1 px clear of the column, and a steel yoke across their tops that the screw passes through
    m.box('base_plate', [-8, 8, -8], [8, 10, 8], 'dsteel')
    for tag, x, z in [('fl', -7, -7), ('fr', 5, -7), ('bl', -7, 5), ('br', 5, 5)]:
        m.box('rod_' + tag, [x, 10, z], [x + 2, 26, z + 2], 'brass', part='rods')
    m.box('yoke_l', [-8, 26, -8], [-4, 28, 8], 'steel', part='yoke')
    m.box('yoke_r', [4, 26, -8], [8, 28, 8], 'steel', part='yoke')
    m.box('yoke_c', [-4, 26, -2], [4, 28, 2], 'steel', part='yoke')

    # ---- the lamp (K3) on the front-right rod at the column's height: the pan's front glass stays clear ---------
    kit.lamp(m, 'north', -7, 4, 12)

    # ---- the lower stack (baked): copper, felt, zinc, felt; felt set 1 px in: real grooves ----------------------
    m.box('disc_cu_1', [-4, 10, -4], [4, 12, 4], 'copper', part='stack')
    m.box('felt_1', [-3, 12, -3], [3, 13, 3], 'rubber', part='stack')
    m.box('disc_zn_1', [-4, 13, -4], [4, 15, 4], 'zinc', part='stack')
    m.box('felt_2', [-3, 15, -3], [3, 16, 3], 'rubber', part='stack')

    # ---- bones -------------------------------------------------------------------------------------------------
    # stack_top: the upper three discs; a 1 px gap under it at rest. Code posY 0..-1 over a burn.
    m.bone('stack_top', [0, 17, 0], code=('position',))
    m.box('disc_cu_2', [-4, 17, -4], [4, 19, 4], 'copper', 'stack_top', part='stack_top')
    m.box('felt_3', [-3, 19, -3], [3, 20, 3], 'rubber', 'stack_top', part='stack_top')
    m.box('disc_zn_2', [-4, 20, -4], [4, 22, 4], 'zinc', 'stack_top', part='stack_top')

    # press: the brass plate between the rods (1 px over the stack and 1 px under the yoke at rest; over a burn it
    # sinks 2 px and the gap under the yoke opens from 1 to 3 px: the fuel gauge), two copper terminal posts on its
    # front half with a 2 px spark gap between them, in front of the screw. Code posY 0..-2.
    m.bone('press', [0, 23, 0], code=('position',))
    m.box('press_plate', [-8, 23, -8], [8, 25, 8], 'brass', 'press')
    m.box('term_l', [-3, 25, -6], [-1, 28, -4], 'copper', 'press', part='terms')
    m.box('term_r', [1, 25, -6], [3, 28, -4], 'copper', 'press', part='terms')

    # handle (root, fixed height): the screw through the yoke (its foot inside the press plate at rest, still
    # 1 px above it when the plate has sunk 2 px), the brass bar, rubber grips 2x3x2 (bar ends turned down). Keyed rotY.
    m.bone('handle', [0, 29, 0])
    m.box('screw', [-1, 24, -1], [1, 29, 1], 'steel', 'handle')
    m.box('bar', [-6, 29, -1], [6, 31, 1], 'brass', 'handle')
    m.box('grip_l', [-8, 29, -1], [-6, 32, 1], 'rubber', 'handle', part='grips')
    m.box('grip_r', [6, 29, -1], [8, 32, 1], 'rubber', 'handle', part='grips')

    # ---- locators ----------------------------------------------------------------------------------------------
    m.locator('term_a', [-1, 27, -5], 'press')       # left terminal's inner face: the spark starts here
    m.locator('term_b', [1, 27, -5], 'press')        # right terminal's inner face: and lands here (2 px gap)
    m.locator('screw_top', [0, 28, -2], None)        # where the screw leaves the yoke: the finish puff
    m.locator('pan', [0, 5, 0], None)                # over the meat: the twitch's bzzt, the plop

    # ---- contents (BER item anchors, stand-ins never exported) -------------------------------------------------
    # Standing upright and facing the front, scale 0.25 (4 px): lying flat they would be seen edge-on through the
    # 4 px glass from any normal eye height, i.e. not at all. The anchor is the item's bottom centre.
    note = 'the fuel item, ItemDisplayContext.FIXED, standing upright facing -Z, scale 0.25; shown for lumps >= n'
    m.anchor('lump_1', [0, 4, -2], note + ' (1)', standin=((4, 4, 1), 'standi'))
    m.anchor('lump_2', [-3, 4, 1], note + ' (2)', standin=((4, 4, 1), 'standi'))
    m.anchor('lump_3', [3, 4, 1], note + ' (3)', standin=((4, 4, 1), 'standi'))

    # ---- the cell shapes (Java cell coordinates; upper = the machine_top cell) ---------------------------------
    m.shape('lower', (0, 0, 0, 16, 16, 16))          # plinth, ports, base plate, rods, stack: the block's body
    m.shape('upper', (0, 0, 0, 16, 12, 16))          # stack top, press, rods, terminals, yoke
    m.shape('upper', (0, 12, 7, 16, 16, 9))          # the screw, bar and grips at rest
    return m


if __name__ == '__main__':
    m = model()
    m.write(PATH)
    print(kit.shapes_java(m))

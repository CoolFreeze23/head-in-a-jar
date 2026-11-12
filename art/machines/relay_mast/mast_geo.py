"""Relay Mast (Mk I): geometry.

    python art/machines/relay_mast/mast_geo.py      # writes relay_mast.bbmodel and its placeholder PNG
    python art/machines/relay_mast/mast_anim.py     # then the clips (and the review copy)

A radio dish crossed with a street-lamp post. An enamel base on K1 feet with the lamp in its front, a
copper cable spool on dark flanges, a steel pole, and high on the pole a 12 x 12 brass dish on a yoke: its front a
rounded rim (a frame of four boxes 2 px wide, the corners stepped) round a bowl set 1 px back, a steel feed horn on its
axis; its BACK dark steel (a plate and a can), so a dish turned away from you is dark and a dish turned toward you is
brass: the light/dark flip is what reads at 96 blocks when a route changes. The red aviation lamp on the pole's top.
32 px, top block.

Bones (1):
    dish   pivot (0, 23, 0) on the pole's axis
           rotation CODE: Y = the aim (next hop, chased), X = the unpowered droop and the storm flinch. Never keyed.
Faces -Z; +X is the machine's right.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
import kit  # noqa: E402

PATH = os.path.join(HERE, 'relay_mast.bbmodel')
REVIEW = 'build/machines-review/relay_mast_review.bbmodel'
DISH_Y = 23


def dish(m, bone, cy, half, z_front, bowl_back=2, receiver=False):
    """A dish on bone `bone`, centred at height cy on the pole's axis line, facing -Z. half = 6 (12 px) or 8 (16).
    Front: a rim 2 px wide, 2 px deep, whose outline is a stepped octagon (the straight runs
    2h-6 long, a 2x2 step block at each corner), so the face reads as a disc and not a frame; the white bowl set
    `bowl_back` px behind the rim's front (a lip and a hollow); on the axis a light feed: a 2x2 steel arm and a 3x3
    zinc horn, so the face is light from edge to edge and the turn reads as light becoming dark. Back: a dark plate
    behind the bowl and a dark can, which the yoke runs into."""
    h = half
    zf = z_front
    P = 'dish_rim'
    s = h - 3
    m.box('rim_t', [-s, cy + h - 2, zf], [s, cy + h, zf + 2], 'brass', bone, part=P)
    m.box('rim_b', [-s, cy - h, zf], [s, cy - h + 2, zf + 2], 'brass', bone, part=P)
    m.box('rim_l', [-h, cy - s, zf], [-h + 2, cy + s, zf + 2], 'brass', bone, part=P)
    m.box('rim_r', [h - 2, cy - s, zf], [h, cy + s, zf + 2], 'brass', bone, part=P)
    for tag, sx, sy in (('tl', -1, 1), ('tr', 1, 1), ('bl', -1, -1), ('br', 1, -1)):
        x0, x1 = sorted((sx * s, sx * (h - 1)))
        y0, y1 = sorted((cy + sy * s, cy + sy * (h - 1)))
        m.box('rim_' + tag, [x0, y0, zf], [x1, y1, zf + 2], 'brass', bone, part=P)
    bi, bz = h - 1, zf + bowl_back           # the bowl runs 1 px under the rim, which seats on it
    m.box('bowl', [-bi, cy - bi, bz], [bi, cy + bi, bz + 1], 'enamel', bone)     # the white bowl inside the brass rim
    pz = bz + 1                                # the plate sits behind the bowl
    m.box('back_plate', [-(h - 2), cy - (h - 2), pz], [h - 2, cy + (h - 2), pz + 1], 'dsteel', bone)
    m.box('back_can', [-(h - 3), cy - (h - 3), pz + 1], [h - 3, cy + (h - 3), pz + 2], 'dsteel', bone)
    # the feed: a steel arm from the bowl's centre and a light horn 3 px forward of the rim
    m.box('feed_arm', [-1, cy - 1, zf - 3], [1, cy + 1, bz], 'steel', bone)
    m.box('feed_horn', [-1.5, cy - 1.5, zf - 5], [1.5, cy + 1.5, zf - 3], 'zinc', bone, part='feed_horn')
    if receiver:
        # a small steel receiver on the back, facing backwards
        m.box('recv_stalk', [-1, cy + 3, pz + 2], [1, cy + 5, pz + 3], 'dsteel', bone)
        m.box('recv_dish', [-2, cy + 2, pz + 3], [2, cy + 5, pz + 4], 'steel', bone)
    return pz + 2


def model():
    m = kit.Model('relay_mast', budget=1)

    # ---- K1 feet and plinth; an enamel base (K2, 12 x 5 x 12) with the lamp on its front panel and the maker's plate
    # on its right side ----
    kit.plinth(m, -5, 5, -5, 5)
    kit.casing(m, 'base', -7, 7, -7, 7, 3, 8)        # v2: 5 px, so the pole and the dish carry the shape
    kit.lamp(m, 'north', -7, -2, 3.5)            # bezel y 3.5-7.5
    kit.makers_plate(m, 'east', 7, -2, 4.5)

    # ---- the cable spool: dark flanges and a copper coil (the Utilities' copper), on the base ----
    m.box('flange_lo', [-5, 8, -5], [5, 9, 5], 'dsteel', part='spool')
    m.box('coil', [-4, 9, -4], [4, 12, 4], 'copper', part='spool_coil')       # a real drum, 8x3x8
    m.box('flange_hi', [-5, 12, -5], [5, 13, 5], 'dsteel', part='spool')
    # the copper cable from the drum up the back of the pole (it feeds the dish)
    m.box('cable', [-1, 13, 2], [1, DISH_Y - 2, 3], 'copper', part='cable')

    # ---- the pole and the aviation lamp ----
    m.box('pole', [-2, 13, -2], [2, 29, 2], 'steel')
    m.box('beacon_base', [-3, 29, -3], [3, 30, 3], 'brass', part='beacon_b')
    m.box('beacon_lens', [-1, 30, -1], [1, 32, 1], 'lens_fault', part='beacon_l')     # steady red (placeholder)
    m.locator('beacon', [0, 32, 0])

    # ---- the dish (bone): a collar round the pole, a yoke arm, the dish ----
    m.bone('dish', [0, DISH_Y, 0], code=('rotation',))
    m.box('collar', [-3, DISH_Y - 2, -3], [3, DISH_Y + 2, 3], 'dsteel', 'dish')
    m.box('yoke', [-2, DISH_Y - 1, -7], [2, DISH_Y + 1, -3], 'dsteel', 'dish')      # v2: 4 wide, runs into the can
    dish(m, 'dish', DISH_Y, 6, -10)

    m.locator('horn', [0, DISH_Y, -15], 'dish')           # the link spark (the horn's tip; moves with the dish)
    m.locator('signal', [0, 32, 0])                       # the signal point (centre +1 block up) for reference

    # ---- cell shapes: the base, spool and pole (the dish is drawn, not solid: it swings) ----
    m.shape('lower', (1, 0, 1, 15, 8, 15))
    m.shape('lower', (3, 8, 3, 13, 13, 13))
    m.shape('lower', (6, 13, 6, 10, 16, 10))
    m.shape('upper', (6, 0, 6, 10, 16, 10))
    return m


if __name__ == '__main__':
    m = model()
    m.write(PATH)
    print(kit.shapes_java(m))

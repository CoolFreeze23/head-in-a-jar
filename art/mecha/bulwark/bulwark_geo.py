"""Bulwark (heavy biped skeleton "biped_heavy"): geometry, rig numbers, box-UV packing by material, placeholder sheet.

Source of truth for the Bulwark's cubes, bones, sockets and locators. Blockbench/GeckoLib entity space in pixels:
origin on the ground between the feet, y up, the machine faces -Z, its left is -X, 16 px to a block.

    python art/mecha/bulwark/bulwark_geo.py                        # writes bulwark_placeholder.png and bulwark.bbmodel
    python art/mecha/bulwark/bulwark_geo.py --scale --out X.bbmodel  # review copy with the Hopper, an iron-golem
                                                                     # block-out, a player and the chair beside it

The bbmodel is written directly by art/mecha/bbbuild.py (UUIDs from names; the clips survive a rebuild). Clips come
from bulwark_anim.py, which imports the rig numbers below.

Numbers taken from the game:
  hitbox 1.6 x 3.1 blocks (25.6 x 49.6 px)             MechaFrame.BULWARK
  seat: host neck (0, 2.875, 0) blocks; a PILOT keeps   data/headinajar/headinajar/host/bulwark.json and the Hopper's
  the full player size, so the jar's base sits 0.6     reading of HostSeatMixin / FormEvents.size
  lower: (0, 2.275, 0) blocks = (0, 36.4, 0) px
  speed 0.11 -> about 0.2376 blocks (3.80 px) a tick   MechaFrame.BULWARK, x 2.16 (vanilla ground movement)
  slots legs 1, back 2, core 1, torso 3, arm 2, sensor 2

Socket names on the heavy-biped skeleton: frames with more slots add socket_core_1, socket_torso_3 (left) and _4
(right), and the shoulder hardpoints socket_arm_2 (left) and socket_arm_3 (right) (the Colossus, colossus_geo.py).
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
TEX_W, TEX_H = 256, 256

# ---------------------------------------------------------------------------------------------------------------------
# Rig numbers (shared with bulwark_anim.py). Sagittal plane: z forward-negative, y up.
# ---------------------------------------------------------------------------------------------------------------------
HIP_X = 10.0            # hips at x = +-10, under the hull's corners
HIP_Y = 29.0            # hip joint height at rest (inside the hull; the legs show from its underside)
THIGH = 17.0            # hip to knee
SHIN = 18.0             # knee to ankle
ANKLE_H = 3.0           # ankle above the sole (big boots)
SEAT_Y = 36.4           # the jar's base (code)
REST_ANKLE = (0.0, ANKLE_H)
REST_ARM = 0.0          # arm_* rest x: upper arms hang
REST_FOREARM = 15.0     # forearm_* rest x: forearms a little forward, hands carried before the thighs
REST_SPLAY = 6.0        # arm_* rest z: arms hang 6 degrees out (left -6, right +6)


def ik(hip, ankle):
    """Two-bone plantigrade leg, knee forward. Absolute (thigh, shin) angles in degrees from straight down, positive
    forward (-Z); a Blockbench x rotation of +a turns a down-pointing bone to that angle. Unreachable targets give a
    straight leg pointing at them."""
    dz, dy = ankle[0] - hip[0], ankle[1] - hip[1]
    d = math.hypot(dz, dy)
    dc = min(max(d, abs(THIGH - SHIN) + 0.01), THIGH + SHIN - 0.001)
    phi = math.atan2(-dz, -dy)
    alpha = math.acos((THIGH * THIGH + dc * dc - SHIN * SHIN) / (2 * THIGH * dc))
    t1 = phi + alpha
    kz, ky = hip[0] - THIGH * math.sin(t1), hip[1] - THIGH * math.cos(t1)
    ez, ey = hip[0] + dz * dc / d, hip[1] + dy * dc / d
    t2 = math.atan2(-(ez - kz), -(ey - ky))
    return math.degrees(t1), math.degrees(t2)


_t1, _t2 = ik((0.0, HIP_Y), REST_ANKLE)
REST_THIGH = round(_t1, 2)
REST_SHIN = round(_t2 - _t1, 2)
REST_FOOT = round(-_t2, 2)

# placeholder flat mid-tones, house palette D (art/palette.json)
MATERIALS = {
    'enamel': ('ddd5c3', 255),   # e3 ivory enamel: the body, forearms, sensor housing
    'brass':  ('d99a35', 255),   # b3: collar, clamp posts, shoulders, knee pads, hip drums, nameplate
    'steel':  ('7b818b', 255),   # s3: thighs, shins, upper arms, knuckles
    'dsteel': ('5c616b', 255),   # s2 (paint s0-s1): the darks: collar base, arches, belly, hoods, axles, ankles, palms
    'rubber': ('27262d', 255),   # r1: boots, cuffs, vent mouths, jaw pads
    'yellow': ('f4bd1e', 255),   # w2: hazard yellow on moving parts 4 px or wider: vent flaps, fingers
    'lens':   ('141318', 255),   # lamps off
    'lamp_a': ('ffc24e', 255),   # tail lamps lit
    'lamp_p': ('8cf0a8', 255),   # cab lamp lit
    'stand':  ('c87767', 255),   # stand-ins (never exported)
    'standj': ('5ba364', 120),
    'standm': ('8bc987', 255),
    'golem':  ('c9c2b5', 255),
    'player': ('4a6fa5', 255),
    'cow':    ('6b4a3a', 255),
}
ORDER = ['enamel', 'brass', 'steel', 'dsteel', 'rubber', 'yellow', 'lens', 'lamp_a', 'lamp_p',
         'stand', 'standj', 'standm', 'golem', 'player', 'cow']


# ---------------------------------------------------------------------------------------------------------------------
# Bones (39 exported): (name, pivot, parent, rest rotation)
# ---------------------------------------------------------------------------------------------------------------------
def bones():
    b = [
        ('root', [0, 0, 0], None, None),
        ('hips', [0, HIP_Y, 0], 'root', None),
        ('socket_core', [0, 18, 0], 'hips', [-90, 0, 0]),
        ('torso', [0, HIP_Y, 0], 'hips', None),
        ('jar_anchor', [0, SEAT_Y, 0], 'torso', None),
        ('clamp_l', [-10, 38.5, 0], 'torso', None),
        ('clamp_r', [10, 38.5, 0], 'torso', None),
        ('sensor', [0, 24, -11.5], 'torso', None),
        ('lamp_core', [0, 23, -17.5], 'sensor', None),
        ('socket_sensor', [-2.5, 26, -14], 'sensor', [90, 0, 0]),
        ('socket_sensor_1', [2.5, 26, -14], 'sensor', [90, 0, 0]),
        ('lamp_tail', [0, 32.5, 12.5], 'torso', None),
        ('vent_l', [-15.5, 31, 5.5], 'torso', None),
        ('vent_r', [15.5, 31, 5.5], 'torso', None),
        ('socket_back', [-5, 27, 11], 'torso', [0, 180, 0]),
        ('socket_back_1', [5, 27, 11], 'torso', [0, 180, 0]),
        ('socket_torso_0', [-11, 32, -11], 'torso', None),   # clear of the cab lamp's rim and the name plate
        ('socket_torso_1', [11, 32, -11], 'torso', None),
        ('socket_torso_2', [0, 20, -8], 'torso', None),     # on the belly, under the cab lamp
    ]
    for side, s in (('l', -1), ('r', 1)):
        b += [
            ('arm_' + side, [s * 17.5, 34, -1.5], 'torso', [REST_ARM, 0, s * REST_SPLAY]),
            ('forearm_' + side, [s * 17.5, 26, -1.5], 'arm_' + side, [REST_FOREARM, 0, 0]),
            ('socket_arm_' + side, [s * 17.5, 21, -5], 'forearm_' + side, None),
            ('hand_' + side, [s * 17.5, 17, -1.5], 'forearm_' + side, None),
            ('grip_' + side, [s * 17.5, 12, 2.5], 'hand_' + side, None),
            ('leg_' + side, [s * HIP_X, HIP_Y, 0], 'hips', [REST_THIGH, 0, 0]),
            ('socket_thigh_' + side, [s * 14.5, 20, 0], 'leg_' + side, [0, -s * 90, 0]),
            ('shin_' + side, [s * HIP_X, HIP_Y - THIGH, 0], 'leg_' + side, [REST_SHIN, 0, 0]),
            ('socket_shin_' + side, [s * 14, 2, 0], 'shin_' + side, [0, -s * 90, 0]),
            ('foot_' + side, [s * HIP_X, HIP_Y - THIGH - SHIN, 0], 'shin_' + side, [REST_FOOT, 0, 0]),
        ]
    b += [('standin_jar', [0, SEAT_Y, 0], 'jar_anchor', None)]
    return b


def C(name, f, t, mat, parent, **kw):
    return (name, list(f), list(t), mat, parent, kw)


def cubes():
    J = SEAT_Y
    c = [
        # ---- the body: the one big light mass, 28 px wide and 14 deep in height. A wide box and a deep box crossed
        # (chamfered corners, no shared planes); a dark belly between the thighs; brass hip housings under the
        # hull's corners that the thighs swing from ----
        C('body', [-14, 23, -9], [14, 36, 9], 'enamel', 'torso'),
        C('body_cross', [-12, 22, -11], [12, 35, 11], 'enamel', 'torso'),
        C('belly', [-5, 18, -8], [5, 22, 8], 'dsteel', 'torso'),
        C('hip_housing_l', [-14, 18, -5], [-6, 22, 5], 'brass', 'hips'),
        C('hip_housing_r', [6, 18, -5], [14, 22, 5], 'brass', 'hips'),
        # the jar's seat, the collar's dark base and the brass collar
        C('seat', [-6, 35.4, -6], [6, 36.4, 6], 'dsteel', 'torso'),
        C('collar_base_f', [-8.5, 35.5, -8.5], [8.5, 36.5, -7.5], 'dsteel', 'torso'),
        C('collar_base_b', [-8.5, 35.5, 7.5], [8.5, 36.5, 8.5], 'dsteel', 'torso'),
        C('collar_base_l', [-8.5, 35.5, -7.5], [-7.5, 36.5, 7.5], 'dsteel', 'torso'),
        C('collar_base_r', [7.5, 35.5, -7.5], [8.5, 36.5, 7.5], 'dsteel', 'torso'),
        C('collar_f', [-9.5, 36.5, -9.5], [9.5, 38.5, -7.5], 'brass', 'torso'),
        C('collar_b', [-9.5, 36.5, 7.5], [9.5, 38.5, 9.5], 'brass', 'torso'),
        C('collar_l', [-9.5, 36.5, -7.5], [-7.5, 38.5, 7.5], 'brass', 'torso'),
        C('collar_r', [7.5, 36.5, -7.5], [9.5, 38.5, 7.5], 'brass', 'torso'),
        # back: a framed panel. A lamp bar on top (tail lamps sunk a pixel, a brass nameplate between them), side and
        # bottom rims, a 20 x 7 field one pixel deep where the two back modules mount
        C('lampbar_l', [-12, 31, 11], [-8, 34, 13], 'enamel', 'torso'),
        C('nameplate', [-4, 31, 11], [4, 34, 13], 'brass', 'torso'),
        C('lampbar_r', [8, 31, 11], [12, 34, 13], 'enamel', 'torso'),
        C('lens_tl', [-8, 31.5, 11], [-4, 33.5, 12], 'lens', 'torso'),
        C('lens_tr', [4, 31.5, 11], [8, 33.5, 12], 'lens', 'torso'),
        C('lamp_tl_lit', [-8, 31.5, 11], [-4, 33.5, 12], 'lamp_a', 'lamp_tail', inflate=0.25),
        C('lamp_tr_lit', [4, 31.5, 11], [8, 33.5, 12], 'lamp_a', 'lamp_tail', inflate=0.25),
        C('rim_bl', [-12, 22, 11], [-10, 31, 12], 'enamel', 'torso'),
        C('rim_br', [10, 22, 11], [12, 31, 12], 'enamel', 'torso'),
        C('rim_bb', [-10, 22, 11], [10, 23, 12], 'enamel', 'torso'),
        # front: the cab-lamp turret hangs under the front edge (the look bone), its lens sunk in a two-pixel rim; the
        # chest above it is clear for a load held against it
        C('sensor_neck', [-3, 21.5, -13], [3, 25.5, -10], 'brass', 'sensor'),
        C('sensor_head', [-4.5, 20, -16], [4.5, 26, -12], 'enamel', 'sensor'),
        C('sensor_rim_t', [-4.5, 25, -18], [4.5, 26, -16], 'enamel', 'sensor'),
        C('sensor_rim_b', [-4.5, 20, -18], [4.5, 21, -16], 'enamel', 'sensor'),
        C('sensor_rim_l', [-4.5, 21, -18], [-2.5, 25, -16], 'enamel', 'sensor'),
        C('sensor_rim_r', [2.5, 21, -18], [4.5, 25, -16], 'enamel', 'sensor'),
        C('sensor_lens', [-2.5, 21, -17], [2.5, 25, -16], 'lens', 'sensor'),
        C('sensor_lens_lit', [-2.5, 21, -17], [2.5, 25, -16], 'lamp_p', 'lamp_core', inflate=0.25),
        # flanks: a framed panel ahead (a 3 x 5 field one pixel deep), then a flush vent: a rubber mouth with one
        # striped flap over it, together standing two pixels off the side
        C('flank_rim_t_l', [-15, 29, -9], [-14, 30, -4], 'enamel', 'torso'),
        C('flank_rim_b_l', [-15, 23, -9], [-14, 24, -4], 'enamel', 'torso'),
        C('flank_rim_f_l', [-15, 24, -9], [-14, 29, -8], 'enamel', 'torso'),
        C('flank_rim_k_l', [-15, 24, -5], [-14, 29, -4], 'enamel', 'torso'),
        C('flank_rim_t_r', [14, 29, -9], [15, 30, -4], 'enamel', 'torso'),
        C('flank_rim_b_r', [14, 23, -9], [15, 24, -4], 'enamel', 'torso'),
        C('flank_rim_f_r', [14, 24, -9], [15, 29, -8], 'enamel', 'torso'),
        C('flank_rim_k_r', [14, 24, -5], [15, 29, -4], 'enamel', 'torso'),
        C('grille_l', [-15, 24, 2], [-14, 31, 9], 'rubber', 'torso'),
        C('grille_r', [14, 24, 2], [15, 31, 9], 'rubber', 'torso'),
        C('vent_flap_l', [-16, 24, 2], [-15, 31, 9], 'yellow', 'vent_l'),
        C('vent_flap_r', [15, 24, 2], [16, 31, 9], 'yellow', 'vent_r'),
        # ---- the jar's clamps: two 3 px brass posts from knuckles on the collar's sides, at the jar's mid-depth,
        # rubber pads pressing the glass just under the lid (above the jar's side pipes). Nothing behind the jar ----
        C('clamp_knuckle_l', [-12, 37.5, -2], [-8, 40.5, 2], 'steel', 'clamp_l'),
        C('clamp_post_l', [-11, 40.5, -1.5], [-8, 48.5, 1.5], 'brass', 'clamp_l'),
        C('clamp_pad_l', [-8, 46.5, -2], [-6, 48.5, 2], 'rubber', 'clamp_l'),
        C('clamp_knuckle_r', [8, 37.5, -2], [12, 40.5, 2], 'steel', 'clamp_r'),
        C('clamp_post_r', [8, 40.5, -1.5], [11, 48.5, 1.5], 'brass', 'clamp_r'),
        C('clamp_pad_r', [6, 46.5, -2], [8, 48.5, 2], 'rubber', 'clamp_r'),
        # ---- stand-in jar (never exported) ----
        C('standin_jar_base', [-7, J, -7], [7, J + 1, 7], 'standm', 'standin_jar'),
        C('standin_jar_glass', [-6.5, J + 1, -6.5], [6.5, J + 13, 6.5], 'standj', 'standin_jar'),
        C('standin_jar_lid', [-7, J + 13, -7], [7, J + 14, 7], 'standm', 'standin_jar'),
        C('standin_jar_valve', [-2, J + 14, -2], [2, J + 16, 2], 'standm', 'standin_jar'),
        C('standin_head', [-4, J + 1.5, -4], [4, J + 9.5, 4], 'stand', 'standin_jar'),
    ]
    for side, s in (('l', -1), ('r', 1)):
        def bx(x0, x1):
            return (x0, x1) if s < 0 else (-x1, -x0)

        def cube(name, xs, ys, zs, mat, parent, **kw):
            a, b2 = bx(*xs)
            c.append(C(name + '_' + side, [a, ys[0], zs[0]], [b2, ys[1], zs[1]], mat, parent + '_' + side, **kw))
        # arm (shorter: the fist hangs at knee height): a dark boss holds the brass shoulder a pixel off the flank; an
        # enamel upper arm; a brass forearm (so arms part from the steel legs by value); a rubber cuff; a big dark
        # fist with steel knuckles and a hazard-yellow finger block that closes (the grip)
        cube('shoulder_boss', (-15, -14), (32, 35), (-3, 0), 'dsteel', 'arm')
        cube('shoulder', (-21, -15), (31, 37), (-5, 2), 'brass', 'arm')
        cube('upper_arm', (-20, -15), (26, 31), (-4, 1), 'enamel', 'arm')
        cube('elbow', (-20.5, -14.5), (25, 27), (-3.5, 0.5), 'dsteel', 'forearm')
        cube('forearm', (-21, -14), (19, 25), (-5, 2), 'brass', 'forearm')
        cube('cuff', (-21.5, -13.5), (17, 19), (-5.5, 2.5), 'rubber', 'forearm')
        cube('palm', (-21.5, -13.5), (11, 17), (-5.5, 2.5), 'dsteel', 'hand')
        cube('knuckles', (-21.5, -13.5), (9, 11), (-5.5, -3.5), 'steel', 'hand')
        cube('fingers', (-21, -14), (7, 12), (-1, 2), 'yellow', 'grip')
        # leg (thicker, longer): 9 px steel thigh from the hip housing, a dark knee axle in the gap, a brass knee pad
        # on the front of the shin, 8 px steel shin, a dark ankle, a big rubber boot with a brass toe cap
        cube('thigh', (-14.5, -5.5), (13, 29), (-4.5, 4.5), 'steel', 'leg')
        cube('knee_axle', (-14, -6), (11, 13), (-3, 3), 'dsteel', 'shin')
        cube('knee_pad', (-15, -5), (7, 13), (-7, -4), 'brass', 'shin')
        cube('shin', (-14, -6), (-5, 11), (-4, 4), 'steel', 'shin')
        cube('ankle', (-14.5, -5.5), (-7.5, -4.5), (-4.5, 4.5), 'dsteel', 'foot')
        cube('boot', (-15.5, -4.5), (-9, -6), (-10, 7), 'rubber', 'foot')
        cube('toe_cap', (-15, -5), (-9, -7), (-11, -10), 'brass', 'foot')
    return c


def scale_standins():
    """Beside it, for scale (review copies only): the Hopper (its own bones and cubes, 34 px to the right), an iron
    golem block-out (40 px to the left), a player, and the Separation Chair's block (70 px right). Also a cow block-out
    for the grab clips, parented to nothing (placed by the review script)."""
    sys.path.insert(0, os.path.join(os.path.dirname(HERE), 'hopper'))
    import hopper_geo as H
    hb, hc = [], []
    dx = 36.0
    for name, piv, parent, rot in H.bones():
        if name == 'standin_jar':
            continue
        hb.append(('h_' + name, [piv[0] + dx, piv[1], piv[2]], ('h_' + parent) if parent else None, rot))
    for name, f, t, mat, parent, kw in H.cubes():
        if name.startswith('standin') or parent == 'standin_jar':
            continue
        if mat not in MATERIALS:
            mat = {'lamp_g': 'lamp_p'}.get(mat, 'enamel')
        hc.append(('h_' + name, [f[0] + dx, f[1], f[2]], [t[0] + dx, t[1], t[2]], mat, 'h_' + parent, kw))
    gx, px = -42, -64
    sb = [('standin_golem', [gx, 0, 0], None, None), ('standin_player', [px, 0, 0], None, None),
          ('standin_chair', [72, 0, 0], None, None)]
    sc = [
        C('sg_leg_l', [gx - 7.5, 0, -2.5], [gx - 1.5, 16, 2.5], 'golem', 'standin_golem'),
        C('sg_leg_r', [gx + 1.5, 0, -2.5], [gx + 7.5, 16, 2.5], 'golem', 'standin_golem'),
        C('sg_waist', [gx - 4.5, 16, -3], [gx + 4.5, 21, 3], 'golem', 'standin_golem'),
        C('sg_chest', [gx - 9, 21, -6], [gx + 9, 33, 5], 'golem', 'standin_golem'),
        C('sg_head', [gx - 4, 33, -7], [gx + 4, 43, 1], 'golem', 'standin_golem'),
        C('sg_arm_l', [gx - 13, 3, -3], [gx - 9, 33, 3], 'golem', 'standin_golem'),
        C('sg_arm_r', [gx + 9, 3, -3], [gx + 13, 33, 3], 'golem', 'standin_golem'),
        C('sp_legs', [px - 4, 0, -2], [px + 4, 12, 2], 'player', 'standin_player'),
        C('sp_body', [px - 4, 12, -2], [px + 4, 24, 2], 'player', 'standin_player'),
        C('sp_head', [px - 4, 24, -4], [px + 4, 32, 4], 'stand', 'standin_player'),
        # the Separation Chair as a block-out: its plinth, seat, backrest and its 32 px mast and hood
        C('sc_plinth', [65, 0, -7], [79, 3, 7], 'steel', 'standin_chair'),
        C('sc_seat', [65, 3, -7], [79, 6, 2], 'rubber', 'standin_chair'),
        C('sc_back', [65, 4, 2], [79, 17, 5], 'enamel', 'standin_chair'),
        C('sc_mast', [62, 2, 1], [64, 32, 3], 'steel', 'standin_chair'),
        C('sc_hood', [66, 28, -6], [78, 32, 6], 'enamel', 'standin_chair'),
    ]
    return hb + sb, hc + sc


def cow_standin(origin):
    """A cow-sized block held crosswise (vanilla cow: about 0.9 x 1.4 x 1.3 blocks; body along x so the two hands
    grip its ends) centred on origin (review only)."""
    x, y, z = origin
    return [C('cow_body', [x - 11, y - 5, z - 6], [x + 11, y + 5, z + 6], 'cow', 'standin_cow'),
            C('cow_head', [x + 11, y - 1, z - 4], [x + 16, y + 7, z + 4], 'cow', 'standin_cow')]


def locators():
    # positions in the modelling (unrotated) frame, like cube corners
    return [
        ('fx_step_l', [-HIP_X, -9, -1], 'foot_l'),
        ('fx_step_r', [HIP_X, -9, -1], 'foot_r'),
        ('fx_hand_l', [-13.5, 10, -1.5], 'hand_l'),     # the inner face of each fist; the grab point is their midpoint
        ('fx_hand_r', [13.5, 10, -1.5], 'hand_r'),
        ('fx_vent_l', [-17, 27, 5.5], 'vent_l'),
        ('fx_vent_r', [17, 27, 5.5], 'vent_r'),
        ('fx_jet_l', [-5, 22, 13], 'torso'),           # below the back sockets: jet wash and exhaust
        ('fx_jet_r', [5, 22, 13], 'torso'),
        ('fx_lamp_tail', [0, 32.5, 13], 'torso'),
        ('fx_core', [0, 23, -18.5], 'sensor'),
        ('fx_jar_exit', [0, 54, 0], 'torso'),
        ('fx_hull', [0, 38.5, 0], 'torso'),
        ('fx_wrist_l', [-17.5, 21, -5], 'socket_arm_l'),
        ('fx_wrist_r', [17.5, 21, -5], 'socket_arm_r'),
    ]


def size(c):
    return [abs(c[2][i] - c[1][i]) for i in range(3)]


def box_uv_wh(c):
    w, h, d = [max(1, round(s)) for s in size(c)]
    return 2 * (w + d), d + h


def pack(cs, tex_w=TEX_W, tex_h=TEX_H):
    offsets, regions = {}, {}
    x = y = shelf = 0
    for mat in ORDER:
        items = sorted([c for c in cs if c[3] == mat], key=lambda c: (-box_uv_wh(c)[1], -box_uv_wh(c)[0]))
        if not items:
            continue
        if x:
            y += shelf
            x = shelf = 0
        top = y
        for c in items:
            w, h = box_uv_wh(c)
            if x + w > tex_w:
                y += shelf
                x = shelf = 0
            offsets[c[0]] = [x, y]
            x += w
            shelf = max(shelf, h)
        regions[mat] = (top, y + shelf)
    if y + shelf > tex_h:
        sys.exit('texture overflow: %d rows' % (y + shelf))
    return offsets, regions


def draw(cs, offsets, path, tex_w=TEX_W, tex_h=TEX_H):
    from PIL import Image
    img = Image.new('RGBA', (tex_w, tex_h), (0, 0, 0, 0))
    px = img.load()
    for c in cs:
        w, h, d = [max(1, round(s)) for s in size(c)]
        ox, oy = offsets[c[0]]
        hexv, alpha = MATERIALS[c[3]]
        col = tuple(int(hexv[i:i + 2], 16) for i in (0, 2, 4)) + (alpha,)
        for rx, ry, rw, rh in [(ox + d, oy, w * 2, d), (ox, oy + d, 2 * (w + d), h)]:
            for yy in range(ry, ry + rh):
                for xx in range(rx, rx + rw):
                    if 0 <= xx < tex_w and 0 <= yy < tex_h:
                        px[xx, yy] = col
    img.save(path)


def check(cs):
    bad = [c[0] for c in cs if any(abs(s - round(s)) > 1e-6 or s < 1 - 1e-6 for s in size(c))]
    if bad:
        sys.exit('non-integer or sub-pixel cube sizes: %s' % bad)


def coplanar(cs, rest_frames):
    """Same-facing faces in one plane that overlap, between cubes in the same rest frame."""
    out = []
    for i, a in enumerate(cs):
        for b in cs[i + 1:]:
            if rest_frames.get(a[4]) != rest_frames.get(b[4]):
                continue
            for ax in range(3):
                for sa, va in ((0, a[1][ax]), (1, a[2][ax])):
                    for sb, vb in ((0, b[1][ax]), (1, b[2][ax])):
                        if abs(va - vb) < 1e-6 and sa == sb:
                            o = [j for j in range(3) if j != ax]
                            if all(min(a[2][j], b[2][j]) - max(a[1][j], b[1][j]) > 1e-6 for j in o):
                                out.append((a[0], b[0], 'xyz'[ax], va))
    return out


def rest_frame_of(bs):
    """Bones sharing an unrotated frame map to the same key (the nearest ancestor with a rest rotation, or root)."""
    parent = {n: p for n, _, p, _ in bs}
    rot = {n: r for n, _, _, r in bs}
    out = {}
    for n in parent:
        m = n
        while m and not rot.get(m):
            m = parent[m]
        out[n] = m or 'root'
    return out


def main():
    sys.path.insert(0, os.path.dirname(HERE))
    import bbbuild
    scale = '--scale' in sys.argv
    bs, cs, locs = bones(), cubes(), locators()
    cows = '--cows' in sys.argv
    if scale:
        b2, c2 = scale_standins()
        bs, cs = bs + b2, cs + c2
    if scale or cows:
        bs = bs + [('standin_cow_ground', [0, 0, -23], 'root', None), ('standin_cow_held', [0, 31, -21], 'torso', None),
                   ('standin_cow_over', [0, 51, -13], 'torso', None), ('standin_cow_high', [0, 57.2, 3.1], 'torso', None)]
        # standin_cow_high: the overhead hold (bulwark_anim.HIGH['point']): the cow's underside on the fists
        for tag, bone, o in (('_g', 'standin_cow_ground', (0, 5, -23)), ('_h', 'standin_cow_held', (0, 31, -21)),
                             ('_o', 'standin_cow_over', (0, 51, -13)), ('_hi', 'standin_cow_high', (0, 62.2, 3.1))):
            cs = cs + [(n + tag, f, t, m, bone, kw) for n, f, t, m, _, kw in cow_standin(o)]
    scale = scale or cows
    check(cs)
    if not scale:
        cp = coplanar([c for c in cs if not c[0].startswith('standin') and not c[0].endswith('_lit')],
                      rest_frame_of(bs))
        if cp:
            sys.stderr.write('coplanar faces: %s\n' % cp)
    tex = (TEX_W, TEX_H) if not scale else (512, 512)
    offs, regions = pack(cs, *tex)
    png = os.path.join(HERE, 'bulwark_placeholder.png' if not scale else '_review_scale.png')
    draw(cs, offs, png, *tex)
    out = sys.argv[sys.argv.index('--out') + 1] if '--out' in sys.argv else os.path.join(HERE, 'bulwark.bbmodel')
    bbbuild.build(out, 'bulwark', bs, cs, locs, offs, png, tex)
    sys.stderr.write('wrote %s; rows by material %s\n' % (out, regions))
    sys.stderr.write('rest angles: thigh %.2f shin %.2f foot %.2f\n' % (REST_THIGH, REST_SHIN, REST_FOOT))
    sys.stderr.write('%d bones (%d exported), %d cubes, %d locators\n'
                     % (len(bs), len([b for b in bs if not b[0].startswith(('standin', 'h_'))]), len(cs), len(locs)))


if __name__ == '__main__':
    main()

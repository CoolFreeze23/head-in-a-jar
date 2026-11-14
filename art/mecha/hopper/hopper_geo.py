"""Hopper (light biped skeleton "biped_light"): geometry, rig numbers, box-UV packing by material, placeholder sheet.

Source of truth for the Hopper's cubes, bones, sockets and locators. Coordinates are Blockbench/GeckoLib entity space in
pixels: origin on the ground between the feet, y up, the machine faces -Z (its front, as Blockbench shows it), its
left is -X. 16 px to a block.

    python art/mecha/hopper/hopper_geo.py                       # writes hopper_placeholder.png and hopper.bbmodel
    python art/mecha/hopper/hopper_geo.py --scale --out X.bbmodel  # a review copy with player and golem stand-ins

hopper.bbmodel is written directly (art/mecha/bbbuild.py, the file layout Blockbench writes; UUIDs come
from the node names, so clips stay bound across rebuilds) and keeps its clips; then run hopper_anim.py, which imports
the rig numbers below (leg lengths, rest angles), and check the model with a validator.

Numbers taken from the game:
  hitbox 1.2 x 2.5 blocks (19.2 x 40 px)              MechaFrame.HOPPER
  seat: host neck (0, 2.25, 0) blocks; a PILOT keeps  data/headinajar/headinajar/host/hopper.json, HostSeatMixin,
  the full player size, so its feet (the jar's base)  FormEvents.size (PILOT not resized), Player vehicle
  sit 0.6 lower: (0, 1.65, 0) = (0, 26.4, 0) px       attachment 0.6
  eye 1.62 above that: 52.3 px                         player standing eye height
  chase camera 2.0 x height = 5.0 blocks back          PilotClient.cameraDistance
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
TEX_W, TEX_H = 256, 256

# ---------------------------------------------------------------------------------------------------------------------
# Rig numbers (shared with hopper_anim.py). Sagittal plane: z forward-negative, y up.
# ---------------------------------------------------------------------------------------------------------------------
HIP_X = 7.0             # hips at x = +-7, under the pod's flanks
HIP_Y = 21.0            # hip joint height at rest: the legs are 46 % of the standing height
THIGH = 12.0            # hip to knee
SHIN = 13.0             # knee to ankle
ANKLE_H = 2.0           # ankle above the sole
SEAT_Y = 26.4           # the jar's base (code)
REST_ANKLE = (-1.0, ANKLE_H)  # (z, y) of each ankle at rest: a pixel ahead of the hip


def ik(hip, ankle):
    """Two-bone reverse-jointed leg (the knee points back, as a bird's or a grasshopper's). Returns absolute (thigh,
    shin) angles in degrees from straight down, positive forward (-Z); a Blockbench x rotation of +a turns a
    down-pointing bone to that angle. An unreachable target gives a straight leg pointing at it."""
    dz, dy = ankle[0] - hip[0], ankle[1] - hip[1]
    d = math.hypot(dz, dy)
    dc = min(max(d, abs(THIGH - SHIN) + 0.01), THIGH + SHIN - 0.001)
    phi = math.atan2(-dz, -dy)
    alpha = math.acos((THIGH * THIGH + dc * dc - SHIN * SHIN) / (2 * THIGH * dc))
    t1 = phi - alpha
    kz, ky = hip[0] - THIGH * math.sin(t1), hip[1] - THIGH * math.cos(t1)
    ez, ey = hip[0] + dz * dc / d, hip[1] + dy * dc / d
    t2 = math.atan2(-(ez - kz), -(ey - ky))
    return math.degrees(t1), math.degrees(t2)


_t1, _t2 = ik((0.0, HIP_Y), REST_ANKLE)
REST_THIGH = round(_t1, 2)               # leg_* rest rotation x
REST_SHIN = round(_t2 - _t1, 2)          # shin_* rest rotation x (relative to the thigh)
REST_FOOT = round(-_t2, 2)               # foot_* rest rotation x (keeps the sole flat)
REST_ARM = 0.0                           # arm_* rest x: upper arm hangs straight down
REST_FOREARM = 90.0                      # forearm_* rest x: forearm carried level, the tool pointing forward
REST_SPLAY = 12.0                        # arm_* rest z: arms carried 12 degrees out, so a working arm shows past
                                         # the pod from behind (arm_l -12, arm_r +12)

# material -> placeholder flat mid-tone, house palette D (art/palette.json; teal glass from A)
MATERIALS = {
    'enamel': ('ddd5c3', 255),   # e3 ivory enamel: hull, forearms, sensor
    'brass':  ('d99a35', 255),   # b3: collar lip, glass band, joints, knee caps, latch, hinge barrels, plates
    'steel':  ('7b818b', 255),   # s3: legs, upper arms
    'dsteel': ('5c616b', 255),   # s2: the darks: collar base, arch ceilings, keel, hoods, knee axles, ankles, heel spurs
    'rubber': ('27262d', 255),   # r1: feet, cuffs, vent grilles
    'yellow': ('f4bd1e', 255),   # w2: warning-striped vent flaps and louvres (stripes are the painter's)
    'lens':   ('141318', 255),   # lamps when off (rubber 0)
    'lamp_g': ('77f08a', 255),   # core lamp lit (indicator green 2; glowmask later)
    'lamp_a': ('ffc24e', 255),   # tail lamps lit (amber 2)
    'lamp_p': ('8cf0a8', 255),   # sensor lens lit (phosphor 3)
    'stand':  ('c87767', 255),   # stand-ins: the head
    'standj': ('5ba364', 120),   # stand-in jar glass
    'standm': ('8bc987', 255),   # stand-in jar frame
    'golem':  ('c9c2b5', 255),   # scale stand-in: iron golem (never exported)
    'player': ('4a6fa5', 255),   # scale stand-in: player (never exported)
}


# ---------------------------------------------------------------------------------------------------------------------
# Bones: (name, pivot, parent, rest rotation). Order matters only for the outliner.
# ---------------------------------------------------------------------------------------------------------------------
def bones():
    b = [
        ('root', [0, 0, 0], None, None),
        ('hips', [0, HIP_Y, 0], 'root', None),
        ('socket_core', [0, 13, 0], 'hips', [-90, 0, 0]),
        ('torso', [0, 21, 0], 'hips', None),
        ('jar_anchor', [0, SEAT_Y, 0], 'torso', None),
        ('restraint', [0, 29, 9], 'torso', None),
        ('clamp_l', [-7.5, 40.7, 0], 'restraint', None),
        ('clamp_r', [7.5, 40.7, 0], 'restraint', None),
        ('sensor', [0, 17, -10.5], 'torso', None),
        ('socket_sensor', [0, 17, -12], 'sensor', [90, 0, 0]),
        ('lamp_sensor', [0, 14.5, -14.5], 'sensor', None),
        ('lamp_core', [0, 23, -10.5], 'torso', None),
        ('lamp_l', [-5, 24, 10.5], 'torso', None),
        ('lamp_r', [5, 24, 10.5], 'torso', None),
        ('vent_l', [-11.5, 25, 4], 'torso', None),
        ('vent_r', [11.5, 25, 4], 'torso', None),
        ('socket_back', [0, 15, 15], 'torso', [0, 180, 0]),
        ('socket_torso_0', [-5.5, 21, -10], 'torso', None),
        ('socket_torso_1', [5.5, 21, -10], 'torso', None),
    ]
    for side, s in (('l', -1), ('r', 1)):
        b += [
            ('arm_' + side, [s * 12.5, 27, -2], 'torso', [REST_ARM, 0, s * REST_SPLAY]),
            ('forearm_' + side, [s * 12.5, 21, -2], 'arm_' + side, [REST_FOREARM, 0, 0]),
            ('socket_arm_' + side, [s * 12.5, 13, -2], 'forearm_' + side, [-90, 0, 0]),
            ('leg_' + side, [s * HIP_X, HIP_Y, 0], 'hips', [REST_THIGH, 0, 0]),
            ('socket_thigh_' + side, [s * 9.5, 16, 0], 'leg_' + side, [0, -s * 90, 0]),
            ('shin_' + side, [s * HIP_X, HIP_Y - THIGH, 0], 'leg_' + side, [REST_SHIN, 0, 0]),
            ('socket_shin_' + side, [s * 9, 2.5, 0], 'shin_' + side, [0, -s * 90, 0]),
            ('foot_' + side, [s * HIP_X, HIP_Y - THIGH - SHIN, 0], 'shin_' + side, [REST_FOOT, 0, 0]),
        ]
    b += [('standin_jar', [0, SEAT_Y, 0], 'jar_anchor', None)]
    return b


def C(name, f, t, mat, parent, **kw):
    return (name, list(f), list(t), mat, parent, kw)


def cubes():
    J = SEAT_Y
    L = 40.7    # the jar's lid top (base 26.4 + 14.3): the restraint's hoop rests on it
    c = [
        # ---- the pod: an upper hull carrying the collar, a front and a back slab (stepped a pixel below the deck and
        # two above the hull's underside, so no two faces share a plane), a centre skirt between the thighs, dark
        # wheel-arch ceilings over the hips, a dark neck and a steel keel under it ----
        C('hull', [-10, 20, -8], [10, 26, 8], 'enamel', 'torso'),
        C('slab_f', [-8, 18, -10], [8, 25, -8], 'enamel', 'torso'),
        C('slab_b', [-8, 18, 8], [8, 25, 10], 'enamel', 'torso'),
        C('skirt', [-3, 16, -8], [3, 20, 8], 'enamel', 'torso'),
        C('arch_l', [-10, 19, -7], [-3, 20, 7], 'dsteel', 'torso'),
        C('arch_r', [3, 19, -7], [10, 20, 7], 'dsteel', 'torso'),
        C('keel_neck', [-2, 15, -5], [2, 16, 5], 'dsteel', 'torso'),
        C('keel', [-4, 13, -6], [4, 15, 6], 'dsteel', 'torso'),
        C('rail_side_l', [-12, 18, -1], [-10, 20, 9], 'dsteel', 'torso'),
        C('rail_side_r', [10, 18, -1], [12, 20, 9], 'dsteel', 'torso'),
        C('beam_f', [-10, 17, -11], [10, 18, -9], 'dsteel', 'torso'),
        C('beam_b', [-12, 17, 9], [12, 18, 11], 'dsteel', 'torso'),
        C('tow_bracket', [-3, 13, 9], [3, 17, 15], 'dsteel', 'torso'),
        # the jar's seat, the collar's dark base (the shadow line under the brass lip) and the brass collar
        C('seat', [-6, 25.4, -6], [6, 26.4, 6], 'dsteel', 'torso'),
        C('collar_base_f', [-8.5, 25.5, -8.5], [8.5, 26.5, -7.5], 'dsteel', 'torso'),
        C('collar_base_b', [-8.5, 25.5, 7.5], [8.5, 26.5, 8.5], 'dsteel', 'torso'),
        C('collar_base_l', [-8.5, 25.5, -7.5], [-7.5, 26.5, 7.5], 'dsteel', 'torso'),
        C('collar_base_r', [7.5, 25.5, -7.5], [8.5, 26.5, 7.5], 'dsteel', 'torso'),
        C('collar_f', [-9.5, 26.5, -9.5], [9.5, 28.5, -7.5], 'brass', 'torso'),
        C('collar_b', [-9.5, 26.5, 7.5], [9.5, 28.5, 9.5], 'brass', 'torso'),
        C('collar_l', [-9.5, 26.5, -7.5], [-7.5, 28.5, 7.5], 'brass', 'torso'),
        C('collar_r', [7.5, 26.5, -7.5], [9.5, 28.5, 7.5], 'brass', 'torso'),
        C('filler_cap', [7.5, 28.5, -9.5], [9.5, 29.5, -7.5], 'brass', 'torso'),
        C('bracket_l', [-11.5, 27, 8], [-9.5, 31, 10], 'dsteel', 'torso'),
        C('bracket_r', [9.5, 27, 8], [11.5, 31, 10], 'dsteel', 'torso'),
        # front: a brass badge with the chest lamp sunk a pixel in a dark hood
        C('badge', [-3, 20, -11], [3, 26, -10], 'brass', 'torso'),
        C('hood_t', [-2, 24, -13], [2, 25, -11], 'dsteel', 'torso'),
        C('hood_b', [-2, 21, -13], [2, 22, -11], 'dsteel', 'torso'),
        C('hood_ls', [-2, 22, -13], [-1, 24, -11], 'dsteel', 'torso'),
        C('hood_rs', [1, 22, -13], [2, 24, -11], 'dsteel', 'torso'),
        C('lens_core', [-1, 22, -12], [1, 24, -11], 'lens', 'torso'),
        C('lamp_core_lit', [-1, 22, -12], [1, 24, -11], 'lamp_g', 'lamp_core', inflate=0.25),
        # back: a framed panel. A two-pixel lamp bar on top with the tail lamps sunk in its gaps, one-pixel side and
        # bottom rims, a recessed field holding the brass number plate (the back module mounts on this field)
        C('lampbar_l', [-8, 23, 10], [-6, 25, 12], 'enamel', 'torso'),
        C('number_plate', [-4, 23, 10], [4, 25, 12], 'brass', 'torso'),
        C('lampbar_r', [6, 23, 10], [8, 25, 12], 'enamel', 'torso'),
        C('rim_bl', [-8, 18, 10], [-6, 23, 11], 'enamel', 'torso'),
        C('rim_br', [6, 18, 10], [8, 23, 11], 'enamel', 'torso'),
        C('rim_bb', [-6, 18, 10], [6, 19, 11], 'enamel', 'torso'),
        C('hatch', [-3, 19.5, 10], [3, 21.5, 11], 'enamel', 'torso'),
        C('hatch_handle', [-1, 20, 11], [1, 21, 12], 'brass', 'torso'),
        C('lens_l', [-6, 23, 10], [-4, 25, 11], 'lens', 'torso'),
        C('lens_r', [4, 23, 10], [6, 25, 11], 'lens', 'torso'),
        C('lamp_l_lit', [-6, 23, 10], [-4, 25, 11], 'lamp_a', 'lamp_l', inflate=0.25),
        C('lamp_r_lit', [4, 23, 10], [6, 25, 11], 'lamp_a', 'lamp_r', inflate=0.25),
        # flanks: a framed panel ahead of the vent (a 4 x 4 field one pixel deep inside a one-pixel rim, two wide at
        # its front), then a rubber vent mouth a pixel proud, covered by a striped flap with two real louvre slats
        C('flank_rim_t_l', [-11, 25, -8], [-10, 26, 0], 'enamel', 'torso'),
        C('flank_rim_b_l', [-11, 20, -8], [-10, 21, 0], 'enamel', 'torso'),
        C('flank_rim_f_l', [-11, 21, -8], [-10, 25, -6], 'enamel', 'torso'),
        C('flank_rim_k_l', [-11, 21, -2], [-10, 25, 0], 'enamel', 'torso'),
        C('flank_rim_t_r', [10, 25, -8], [11, 26, 0], 'enamel', 'torso'),
        C('flank_rim_b_r', [10, 20, -8], [11, 21, 0], 'enamel', 'torso'),
        C('flank_rim_f_r', [10, 21, -8], [11, 25, -6], 'enamel', 'torso'),
        C('flank_rim_k_r', [10, 21, -2], [11, 25, 0], 'enamel', 'torso'),
        C('grille_l', [-11, 20, 1], [-10, 25, 7], 'rubber', 'torso'),
        C('grille_r', [10, 20, 1], [11, 25, 7], 'rubber', 'torso'),
        C('vent_flap_l', [-12, 20, 1], [-11, 25, 7], 'yellow', 'vent_l'),
        C('louvre_1_l', [-13, 21, 1], [-12, 22, 7], 'yellow', 'vent_l'),
        C('louvre_2_l', [-13, 23, 1], [-12, 24, 7], 'yellow', 'vent_l'),
        C('vent_flap_r', [11, 20, 1], [12, 25, 7], 'yellow', 'vent_r'),
        C('louvre_1_r', [12, 21, 1], [13, 22, 7], 'yellow', 'vent_r'),
        C('louvre_2_r', [12, 23, 1], [13, 24, 7], 'yellow', 'vent_r'),
        # chin sensor: the lens sunk a pixel inside a two-pixel rim (the look bone)
        C('sensor_neck', [-2, 15, -11.5], [2, 19, -9.5], 'brass', 'sensor'),
        C('sensor_head', [-3, 12, -14], [3, 17, -10], 'enamel', 'sensor'),
        C('sensor_rim_t', [-3, 16, -16], [3, 17, -14], 'enamel', 'sensor'),
        C('sensor_rim_b', [-3, 12, -16], [3, 13, -14], 'enamel', 'sensor'),
        C('sensor_rim_l', [-3, 13, -16], [-2, 16, -14], 'enamel', 'sensor'),
        C('sensor_rim_r', [2, 13, -16], [3, 16, -14], 'enamel', 'sensor'),
        C('sensor_lens', [-2, 13, -15], [2, 16, -14], 'lens', 'sensor'),
        C('sensor_lens_lit', [-2, 13, -15], [2, 16, -14], 'lamp_p', 'lamp_sensor', inflate=0.25),
        # ---- restraint: one brass hoop hinged at the back. Two uprights rise behind the jar, two rails run over
        # the lid's edges, a crossbar lies across the lid's front, two hooks drop at the front corners outside the
        # glass and catch the lid's rim. Below the lid, from the front, nothing crosses the jar. Two clamp pads on
        # short arms press the glass at the shoulders ----
        C('upright_l', [-9, 28.7, 7], [-7, L, 9], 'brass', 'restraint'),
        C('upright_r', [7, 28.7, 7], [9, L, 9], 'brass', 'restraint'),
        C('rail_l', [-9, L, -7.5], [-7, L + 2, 8.5], 'brass', 'restraint'),
        C('rail_r', [7, L, -7.5], [9, L + 2, 8.5], 'brass', 'restraint'),
        C('crossbar', [-9.5, L, -9.5], [9.5, L + 2, -7.5], 'brass', 'restraint'),
        C('backbar', [-7, L, 7], [7, L + 2, 9], 'brass', 'restraint'),
        C('hook_l', [-9.5, L - 3, -9.5], [-7.5, L, -7.5], 'brass', 'restraint'),
        C('hook_r', [7.5, L - 3, -9.5], [9.5, L, -7.5], 'brass', 'restraint'),
        C('knuckle_l', [-10.5, 27.5, 8.5], [-7.5, 30.5, 11.5], 'steel', 'restraint'),
        C('knuckle_r', [7.5, 27.5, 8.5], [10.5, 30.5, 11.5], 'steel', 'restraint'),
        C('clamp_arm_l', [-8, L - 4, -1], [-7, L, 1], 'steel', 'clamp_l'),
        C('clamp_arm_r', [7, L - 4, -1], [8, L, 1], 'steel', 'clamp_r'),
        C('pad_l', [-7, L - 4, -2], [-6, L - 1, 2], 'rubber', 'clamp_l'),
        C('pad_r', [6, L - 4, -2], [7, L - 1, 2], 'rubber', 'clamp_r'),
        # ---- stand-in jar (never exported): the WIDE Life Support Jar at its anchor, head inside ----
        C('standin_jar_base', [-7, J, -7], [7, J + 1, 7], 'standm', 'standin_jar'),
        C('standin_jar_glass', [-6.5, J + 1, -6.5], [6.5, J + 13, 6.5], 'standj', 'standin_jar'),
        C('standin_jar_lid', [-7, J + 13, -7], [7, J + 14, 7], 'standm', 'standin_jar'),
        C('standin_jar_valve', [-2, J + 14, -2], [2, J + 16, 2], 'standm', 'standin_jar'),
        C('standin_head', [-4, J + 1.5, -4], [4, J + 9.5, 4], 'stand', 'standin_jar'),
    ]
    for side, s in (('l', -1), ('r', 1)):
        def bx(x0, x1):
            # x0..x1 are given for the left side (negative x); mirrored for the right
            return (x0, x1) if s < 0 else (-x1, -x0)

        def cube(name, xs, ys, zs, mat, parent, **kw):
            a, b2 = bx(*xs)
            c.append(C(name + '_' + side, [a, ys[0], zs[0]], [b2, ys[1], zs[1]], mat, parent + '_' + side, **kw))
        # arm: a dark boss holds the brass shoulder a pixel off the flank (a shadow gap), steel upper arm, ivory
        # forearm, rubber cuff (the wrist socket)
        cube('shoulder_boss', (-11, -10), (26, 28), (-3, -1), 'dsteel', 'arm')
        cube('shoulder', (-15, -11), (25, 29), (-4.5, 0.5), 'brass', 'arm')
        cube('upper_arm', (-14.5, -10.5), (21, 25), (-4, 0), 'steel', 'arm')
        cube('forearm', (-15, -10), (14, 21), (-4.5, 0.5), 'enamel', 'forearm')
        cube('cuff', (-15.5, -9.5), (13, 15), (-5, 1), 'rubber', 'forearm')
        # leg (unchanged rig): brass hip housing in the dark wheel arch (clear of the flank), steel thigh, a dark
        # knee axle (the gap between segments), a wider brass knee drum, steel shin, steel ankle, rubber foot with a
        # heel spur. Modelled straight down; the rest rotations fold it (knee back)
        cube('hip_joint', (-9, -5), (18, 24), (-3, 3), 'brass', 'leg')
        cube('thigh', (-9.5, -4.5), (12, 21), (-2.5, 2.5), 'steel', 'leg')
        cube('knee_axle', (-9, -5), (11, 12), (-1.5, 1.5), 'dsteel', 'shin')
        cube('knee', (-10, -4), (7, 11), (-2.5, 2.5), 'brass', 'shin')
        cube('shin', (-9, -5), (-4, 7), (-2, 2), 'steel', 'shin')
        cube('ankle', (-9.5, -4.5), (-5.5, -2.5), (-2.5, 2.5), 'dsteel', 'foot')
        cube('sole', (-10.5, -3.5), (-6, -4), (-7, 3), 'rubber', 'foot')
        cube('heel', (-8, -6), (-6, -4), (3, 5), 'dsteel', 'foot')
    return c


def scale_standins():
    """A player (facing -Z) and an iron golem beside the Hopper, for scale renders only."""
    out_b = [('standin_player', [26, 0, 0], None, None), ('standin_golem', [-34, 0, 0], None, None)]
    px, gx = 26, -34
    out_c = [
        C('sp_leg_l', [px - 4, 0, -2], [px, 12, 2], 'player', 'standin_player'),
        C('sp_leg_r', [px, 0, -2], [px + 4, 12, 2], 'player', 'standin_player'),
        C('sp_body', [px - 4, 12, -2], [px + 4, 24, 2], 'player', 'standin_player'),
        C('sp_arm_l', [px - 8, 12, -2], [px - 4, 24, 2], 'stand', 'standin_player'),
        C('sp_arm_r', [px + 4, 12, -2], [px + 8, 24, 2], 'stand', 'standin_player'),
        C('sp_head', [px - 4, 24, -4], [px + 4, 32, 4], 'stand', 'standin_player'),
        C('sg_leg_l', [gx - 7.5, 0, -2.5], [gx - 1.5, 16, 2.5], 'golem', 'standin_golem'),
        C('sg_leg_r', [gx + 1.5, 0, -2.5], [gx + 7.5, 16, 2.5], 'golem', 'standin_golem'),
        C('sg_waist', [gx - 4.5, 16, -3], [gx + 4.5, 21, 3], 'golem', 'standin_golem'),
        C('sg_chest', [gx - 9, 21, -6], [gx + 9, 33, 5], 'golem', 'standin_golem'),
        C('sg_head', [gx - 4, 33, -7], [gx + 4, 43, 1], 'golem', 'standin_golem'),
        C('sg_arm_l', [gx - 13, 3, -3], [gx - 9, 33, 3], 'golem', 'standin_golem'),
        C('sg_arm_r', [gx + 9, 3, -3], [gx + 13, 33, 3], 'golem', 'standin_golem'),
    ]
    return out_b, out_c


def locators():
    # positions are in the unrotated (modelling) frame, like cube corners; the legs are modelled straight down, so the
    # sole's underside is y -4 there and y 0 once the rest rotations bend the leg
    return [
        ('fx_step_l', [-HIP_X, -6, -2], 'foot_l'),     # under the left sole: dust and the footfall
        ('fx_step_r', [HIP_X, -6, -2], 'foot_r'),
        ('fx_vent_l', [-14, 22, 4], 'vent_l'),         # outside the left vent flap
        ('fx_vent_r', [14, 22, 4], 'vent_r'),
        ('fx_latch', [0, 41.7, -8.5], 'restraint'),    # the hoop's crossbar over the lid
        ('fx_jar_exit', [0, 44, 0], 'torso'),          # above the collar, where the ejected jar leaves
        ('fx_hull', [0, 28.5, 0], 'torso'),            # smoke and sparks from the collar
        ('fx_core', [0, 23, -13], 'torso'),            # the chest lamp
        ('fx_wrist_l', [-12.5, 13, -2], 'socket_arm_l'),  # module muzzle base (a module adds its own tip)
        ('fx_wrist_r', [12.5, 13, -2], 'socket_arm_r'),
        ('fx_knee_l', [-HIP_X, 9, 2.5], 'shin_l'),
        ('fx_knee_r', [HIP_X, 9, 2.5], 'shin_r'),
    ]


def size(c):
    return [abs(c[2][i] - c[1][i]) for i in range(3)]


def box_uv_wh(c):
    w, h, d = [max(1, round(s)) for s in size(c)]
    return 2 * (w + d), d + h


ORDER = ['enamel', 'brass', 'steel', 'dsteel', 'rubber', 'yellow', 'lens', 'lamp_g', 'lamp_a', 'lamp_p',
         'stand', 'standj', 'standm', 'golem', 'player']


def pack(cs, tex_w=TEX_W, tex_h=TEX_H):
    """Shelf-pack every cube's box-UV island, grouped by material (one area per material for the painter). Mirrored
    left/right pairs share one island (the right side is drawn with mirror_uv off on its own island to keep it
    paintable separately; it costs little at this size)."""
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
    return path


def ops(bs, cs, locs, offsets):
    out = []
    for name, pivot, parent, rot in bs:
        g = {'op': 'add_group', 'name': name, 'origin': pivot}
        if parent:
            g['parent'] = parent
        if rot:
            g['rotation'] = rot
        out.append(g)
    for name, f, t, mat, parent, kw in cs:
        o = {'op': 'add_cube', 'name': name, 'from': f, 'to': t, 'uv_offset': offsets[name]}
        if parent:
            o['parent'] = parent
        o.update(kw)
        out.append(o)
    for name, pos, parent in locs:
        out.append({'op': 'add_locator', 'name': name, 'position': pos, 'parent': parent})
    return out


def check(cs):
    bad = [c[0] for c in cs if any(abs(s - round(s)) > 1e-6 or s < 1 - 1e-6 for s in size(c))]
    if bad:
        sys.exit('non-integer or sub-pixel cube sizes: %s' % bad)


def main():
    sys.path.insert(0, os.path.dirname(HERE))
    import bbbuild
    scale = '--scale' in sys.argv
    bs, cs, locs = bones(), cubes(), locators()
    if scale:
        b2, c2 = scale_standins()
        bs, cs = bs + b2, cs + c2
    check(cs)
    offs, regions = pack(cs)
    png = os.path.join(HERE, 'hopper_placeholder.png' if not scale else '_review_scale.png')
    draw(cs, offs, png)
    out = sys.argv[sys.argv.index('--out') + 1] if '--out' in sys.argv else os.path.join(HERE, 'hopper.bbmodel')
    bbbuild.build(out, 'hopper', bs, cs, locs, offs, png, (TEX_W, TEX_H))
    sys.stderr.write('wrote %s; rows by material %s\n' % (out, regions))
    sys.stderr.write('rest angles: thigh %.2f shin %.2f foot %.2f\n' % (REST_THIGH, REST_SHIN, REST_FOOT))
    sys.stderr.write('%d bones, %d cubes, %d locators\n' % (len(bs), len(cs), len(locs)))


if __name__ == '__main__':
    main()

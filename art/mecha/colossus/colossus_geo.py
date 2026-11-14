"""Colossus (heavy biped skeleton "biped_heavy", the Bulwark's bones at its own sizes): geometry, rig numbers, frame
parameters for the shared clip script, box-UV packing by material, placeholder sheet.

The company's gatehouse: two towers on long legs with the director parked in the slot between them. On Siege it
kneels, digs its knee spades in and becomes a building (planning has been informed).

Blockbench/GeckoLib entity space in pixels: origin on the ground between the feet, y up, the machine faces -Z, its left
is -X, 16 px to a block. Nothing is scaled: every cube is new, at 16 px to the block.

    python art/mecha/colossus/colossus_geo.py                        # colossus_placeholder.png and colossus.bbmodel
    python art/mecha/colossus/colossus_geo.py --scale --out X.bbmodel  # review copy: a player, a golem block-out, the
                                                                       # Colossus, the Bulwark and the Hopper in a row
    python art/mecha/colossus/colossus_geo.py --cows --out X.bbmodel   # review copy with cow stand-ins

Clips come from art/mecha/bulwark/bulwark_anim.py (the heavy-biped clip script) run for this frame by
colossus_anim.py, which adds the Siege clips and the Colossus's own build.

Numbers taken from the game:
  hitbox 2.4 x 4.4 blocks (38.4 x 70.4 px)             MechaFrame.COLOSSUS
  seat: host neck (0, 4.08, 0) blocks; a PILOT keeps    data/headinajar/headinajar/host/colossus.json, read as for the
  the full player size, so the jar's base sits 0.6      Bulwark (HostSeatMixin / FormEvents.size)
  lower: (0, 3.48, 0) blocks = (0, 55.68, 0) px
  speed 0.09 -> about 0.1944 blocks (3.11 px) a tick    MechaFrame.COLOSSUS, x 2.16 (vanilla ground movement)
  slots legs 1, back 2, core 2, torso 5, arm 4, sensor 2
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MECHA = os.path.dirname(HERE)
TEX_W, TEX_H = 512, 256
NAME = 'colossus'

# ---------------------------------------------------------------------------------------------------------------------
# Rig numbers (read by the clip script). Sagittal plane: z forward-negative, y up.
# ---------------------------------------------------------------------------------------------------------------------
HIP_X = 14.0            # hips at x = +-14, at the ends of the hip girder, under nothing: seen from behind
HIP_Y = 46.0            # hip joint height at rest (the girder hangs under the deck, not inside it)
THIGH = 24.0            # hip to knee
SHIN = 25.0             # knee to ankle
ANKLE_H = 4.0           # ankle above the sole
SEAT_Y = 55.68          # the jar's base (code: neck 4.08 blocks - 0.6)
REST_ANKLE = (0.0, ANKLE_H)
REST_ARM = 0.0          # arm_* rest x: upper arms hang
REST_FOREARM = 15.0     # forearm_* rest x
REST_SPLAY = 6.0        # arm_* rest z: arms hang 6 degrees out (left -6, right +6)
SHOULDER = (23.0, 61.0, -1.0)   # arm_* pivot (left at -x)
HARD_STOW = 180.0       # hardpoint_* x when folded back over the pylon's back (parked); 0 = deployed (the bind pose)

# Frame parameters for the heavy-biped clip script (art/mecha/bulwark/bulwark_anim.py). Anything not given here keeps
# the Bulwark's value. Position keys authored in Bulwark pixels are multiplied by `scale`; angles are kept.
ANIM = dict(
    model=os.path.join(HERE, 'colossus.bbmodel'),
    speed=0.09,                     # 0.1944 blocks a tick
    scale=1.42,                     # 4.4 / 3.1: the Bulwark's position keys (bob, squat, the shrug, socket arrivals)
    toe=16.0, heel=10.0,            # ankle to toe tip / heel
    step='quake',                   # footfall weight in the step timeline keys: step:l_quake / step:r_quake
    # the long walk: 12 ticks a step (2.33 blocks), duty 0.5, a delayed passing pose (the swing boot hangs back, then
    # comes through late), the hull's drop and catch resampled from the Bulwark's and scaled
    walk=dict(n=24, stance=12, lift=8.0, swing_delay=1.5, roll=1.6, torso_roll=2.4, arm=8.0, fore=4.0, clamp_tick=3),
    limp_up=2.0,                    # the limp's hips over rest (Bulwark px, scaled): the leg check
    # walk_c: 13 ticks a step (40.4 px = 0.82 of the 49 px leg, 2.53 blocks), each boot down 15 (two ticks of double
    # support), the hips 4 px up, the heel planted 18 px ahead, on the toe from 11 to 14 degrees
    walk_c=dict(n=26, stance=15, plant_z=-18.0, h_up=4.0, to=14.0, ho=11, lift=8.0, surge=0.5, roll=1.6, torso_roll=2.4,
                arm=8.0, fore=4.0, clamp_tick=3),
    # walk_b: 9 ticks a step (1.75 blocks), each boot down 11 (duty 0.61: two ticks of double support after each plant)
    walk_b=dict(n=18, stance=11, lift=10.0, swing_delay=1.0, roll=2.0, torso_roll=2.8, arm=8.0, fore=5.0, clamp_tick=3),
    # arms at this frame's proportions (FK-solved): the load is carried lower and
    # further out than the Bulwark's, under the jar's base so the director still sees over it
    hold=(50.0, -16.0, 36.0), hold_torso=4.0,       # fists at (+-11.5, 50, -20): the load under the jar's base
    reach=dict(arm=50.0, fore=-16.0, inward=36.0),  # with the deeper squat: fists at (+-11.5, 20, -19)
    grab=dict(hips=(-12.2, -12.7), torso=(-29, -30)),   # Bulwark px: an 18 px squat at this size
    clamp_open=0.4,                 # the clamp posts swing in the pylons' niches: 42 degrees becomes 17
    vent_axis='x',                  # back flaps hinged along x (the Bulwark's flank flaps are stored as z)
)


def ik(hip, ankle):
    """Two-bone plantigrade leg, knee forward. Absolute (thigh, shin) angles in degrees from straight down, positive
    forward (-Z). Unreachable targets give a straight leg pointing at them."""
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
    'enamel': ('ddd5c3', 255),   # e3 ivory enamel: plinth, pylons, upper arms, sensor housing
    'brass':  ('d99a35', 255),   # b3: collar, pylon caps, hip drums, knee pads, forearms, shoulders, nameplate
    'steel':  ('7b818b', 255),   # s3: thighs, shins, knuckles, clamp knuckles
    'dsteel': ('5c616b', 255),   # s2: the darks: girder, plinth course, niches, rams, axles, ankles, palms, hardpoints
    'rubber': ('27262d', 255),   # r1: boots, cuffs, grilles, jaw pads
    'yellow': ('f4bd1e', 255),   # w2: hazard yellow on moving parts 4 px or wider: knee spades, vent flaps, fingers
    'lens':   ('141318', 255),
    'lamp_a': ('ffc24e', 255),
    'lamp_p': ('8cf0a8', 255),
    'stand':  ('c87767', 255),
    'standj': ('5ba364', 120),
    'standm': ('8bc987', 255),
    'golem':  ('c9c2b5', 255),
    'player': ('4a6fa5', 255),
    'cow':    ('6b4a3a', 255),
}
ORDER = ['enamel', 'brass', 'steel', 'dsteel', 'rubber', 'yellow', 'lens', 'lamp_a', 'lamp_p',
         'stand', 'standj', 'standm', 'golem', 'player', 'cow']

LEG_BONES = ('leg', 'shin', 'foot')


# ---------------------------------------------------------------------------------------------------------------------
# Bones (46 exported): (name, pivot, parent, rest rotation). The Bulwark's 39 by the same names and hierarchy, plus
# socket_core_1, socket_torso_3/4, hardpoint_l/r and socket_arm_2/3.
# ---------------------------------------------------------------------------------------------------------------------
def bones():
    J = SEAT_Y
    b = [
        ('root', [0, 0, 0], None, None),
        ('hips', [0, HIP_Y, 0], 'root', None),
        ('socket_core', [0, 41, -3.5], 'hips', [-90, 0, 0]),
        ('socket_core_1', [0, 41, 3.5], 'hips', [-90, 0, 0]),
        ('torso', [0, HIP_Y, 0], 'hips', None),
        ('jar_anchor', [0, J, 0], 'torso', None),
        ('clamp_l', [-9.5, J + 2.6, 0], 'torso', None),
        ('clamp_r', [9.5, J + 2.6, 0], 'torso', None),
        ('sensor', [0, 47, -13], 'torso', None),
        ('lamp_core', [0, 46.5, -17.5], 'sensor', None),
        ('socket_sensor', [-4, 49, -16], 'sensor', [90, 0, 0]),
        ('socket_sensor_1', [4, 49, -16], 'sensor', [90, 0, 0]),
        ('lamp_tail', [0, 58, 5.5], 'torso', None),
        ('vent_l', [-14.5, 70, 6.5], 'torso', None),
        ('vent_r', [14.5, 70, 6.5], 'torso', None),
        ('socket_back', [-7, 51.5, 9], 'torso', [0, 180, 0]),
        ('socket_back_1', [7, 51.5, 9], 'torso', [0, 180, 0]),
        ('socket_torso_0', [-12.5, 51.5, -14], 'torso', None),
        ('socket_torso_1', [12.5, 51.5, -14], 'torso', None),
        ('socket_torso_2', [0, 43, -6], 'hips', None),            # under the gate lamp, on the girder
        ('socket_torso_3', [-14.5, 65.5, -6], 'torso', None),
        ('socket_torso_4', [14.5, 65.5, -6], 'torso', None),
        ('hardpoint_l', [-14.5, 77, 6], 'torso', None),
        ('socket_arm_2', [-14.5, 82, 0], 'hardpoint_l', None),
        ('hardpoint_r', [14.5, 77, 6], 'torso', None),
        ('socket_arm_3', [14.5, 82, 0], 'hardpoint_r', None),
    ]
    ax, ay, az = SHOULDER
    K = HIP_Y - THIGH
    A = K - SHIN
    for side, s in (('l', -1), ('r', 1)):
        b += [
            ('arm_' + side, [s * ax, ay, az], 'torso', [REST_ARM, 0, s * REST_SPLAY]),
            ('forearm_' + side, [s * ax, 52, az], 'arm_' + side, [REST_FOREARM, 0, 0]),
            ('socket_arm_' + side, [s * ax, 46, -5], 'forearm_' + side, None),
            ('hand_' + side, [s * ax, 41, az], 'forearm_' + side, None),
            ('grip_' + side, [s * ax, 36, 3.5], 'hand_' + side, None),
            ('leg_' + side, [s * HIP_X, HIP_Y, 0], 'hips', [REST_THIGH, 0, 0]),
            ('socket_thigh_' + side, [s * 19, HIP_Y - 12, 0], 'leg_' + side, [0, -s * 90, 0]),
            ('shin_' + side, [s * HIP_X, K, 0], 'leg_' + side, [REST_SHIN, 0, 0]),
            ('socket_shin_' + side, [s * 18.5, K - 14, 0], 'shin_' + side, [0, -s * 90, 0]),
            ('foot_' + side, [s * HIP_X, A, 0], 'shin_' + side, [REST_FOOT, 0, 0]),
        ]
    b += [('standin_jar', [0, J, 0], 'jar_anchor', None)]
    return b


def C(name, f, t, mat, parent, **kw):
    return (name, list(f), list(t), mat, parent, kw)


def cubes():
    J = SEAT_Y
    K = HIP_Y - THIGH          # knee
    A = K - SHIN               # ankle
    c = [
        # ---- the foundation: a dark hip girder under the deck, brass hip drums at its ends that the thighs hang from,
        # under nothing but the deck's thin back edge, so the hips and thighs read from behind ----
        C('girder', [-10, 41, -6], [10, 49, 6], 'dsteel', 'hips'),
        C('nameplate', [-5, 42, 6], [5, 47, 7], 'brass', 'hips'),
        # ---- the deck: thin and deep (it reaches forward, not back), a dark course under it, a projecting cornice
        # the towers stand on, a dark band across its front between two piers ----
        C('course', [-13, 49, -11], [13, 51, 7], 'dsteel', 'torso'),
        C('deck', [-16, 50, -13], [16, 54, 9], 'enamel', 'torso'),
        C('cornice', [-17, 53, -14], [17, 55, 10], 'enamel', 'torso'),
        C('front_field', [-12, 51, -14], [12, 53, -13], 'dsteel', 'torso'),
        # the jar's seat, the collar's dark base and the brass collar, on the cornice between the towers
        C('seat', [-6, J - 1, -6], [6, J, 6], 'dsteel', 'torso'),
        C('collar_base_f', [-8.5, J - 0.9, -8.5], [8.5, J + 0.1, -7.5], 'dsteel', 'torso'),
        C('collar_base_b', [-8.5, J - 0.9, 7.5], [8.5, J + 0.1, 8.5], 'dsteel', 'torso'),
        C('collar_base_l', [-8.5, J - 0.9, -7.5], [-7.5, J + 0.1, 7.5], 'dsteel', 'torso'),
        C('collar_base_r', [7.5, J - 0.9, -7.5], [8.5, J + 0.1, 7.5], 'dsteel', 'torso'),
        C('collar_f', [-9.5, J + 0.1, -9.5], [9.5, J + 2.1, -7.5], 'brass', 'torso'),
        C('collar_b', [-9.5, J + 0.1, 7.5], [9.5, J + 2.1, 9.5], 'brass', 'torso'),
        C('collar_l', [-9.5, J + 0.1, -7.5], [-7.5, J + 2.1, 7.5], 'brass', 'torso'),
        C('collar_r', [7.5, J + 0.1, -7.5], [9.5, J + 2.1, 7.5], 'brass', 'torso'),
        # ---- the gate lamp (the look bone): a wide visor under the deck's front edge, a long lens slit in a rim ----
        C('sensor_neck', [-4, 44, -13], [4, 48, -6], 'brass', 'sensor'),
        C('sensor_head', [-9, 44, -16], [9, 49, -13], 'enamel', 'sensor'),
        C('sensor_rim_t', [-9, 48, -18], [9, 49, -16], 'enamel', 'sensor'),
        C('sensor_rim_b', [-9, 44, -18], [9, 45, -16], 'enamel', 'sensor'),
        C('sensor_rim_l', [-9, 45, -18], [-6, 48, -16], 'enamel', 'sensor'),
        C('sensor_rim_r', [6, 45, -18], [9, 48, -16], 'enamel', 'sensor'),
        C('sensor_lens', [-6, 45, -17], [6, 48, -16], 'lens', 'sensor'),
        C('sensor_lens_lit', [-6, 45, -17], [6, 48, -16], 'lamp_p', 'lamp_core', inflate=0.25),
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
            c.append(C(name + '_' + side, [a, ys[0], zs[0]], [b2, ys[1], zs[1]], mat,
                       parent if parent in ('torso', 'hips', 'lamp_tail') else parent + '_' + side, **kw))
        # hips: the brass drum with a dark axle hub on its outer face
        cube('hip_drum', (-20, -8), (41.5, 50.5), (-7.5, 7.5), 'brass', 'hips')
        cube('hip_hub', (-21, -20), (43, 49), (-3, 3), 'dsteel', 'hips')
        cube('pier', (-16, -12), (50, 53), (-14, -13), 'enamel', 'torso')
        # ---- the towers: enamel, 7 x 10, the inner face a dark niche 3 px deep between two jambs (the clamp post lives
        # in it), a framed dark slit down the front, a brass cap, two merlons and the weapon cradle in the crenel
        # between them; a small vent and a tail lamp on the back ----
        cube('pylon', (-18, -15), (55, 76), (-5, 5), 'enamel', 'torso')
        cube('niche', (-15, -13), (55, 76), (-3, 3), 'dsteel', 'torso')
        cube('jamb_f', (-15, -10), (55, 75), (-5, -3), 'enamel', 'torso')
        cube('jamb_b', (-15, -10), (55, 75), (3, 5), 'enamel', 'torso')
        cube('face_o', (-18, -16), (56, 74), (-6, -5), 'enamel', 'torso')
        cube('face_i', (-13, -10), (56, 74), (-6, -5), 'enamel', 'torso')
        cube('face_t', (-16, -13), (71, 74), (-6, -5), 'enamel', 'torso')
        cube('face_b', (-16, -13), (56, 60), (-6, -5), 'enamel', 'torso')
        cube('slit', (-16, -13), (60, 71), (-5.5, -4.5), 'dsteel', 'torso')
        cube('cap', (-19, -10), (75, 77), (-6, 6), 'brass', 'torso')
        cube('merlon_f', (-19, -10), (77, 79), (-6, -3), 'enamel', 'torso')
        cube('merlon_b', (-19, -10), (77, 79), (3, 6), 'enamel', 'torso')
        cube('grille', (-17, -12), (63, 71), (5, 6), 'rubber', 'torso')
        cube('vent_flap', (-17, -12), (63, 70), (6, 7), 'yellow', 'vent')
        cube('tail_lens', (-16, -13), (57, 59), (5, 6), 'lens', 'torso')
        cube('tail_lit', (-16, -13), (57, 59), (5, 6), 'lamp_a', 'lamp_tail', inflate=0.25)
        # the shoulder weapon's cradle: a dark box in the crenel, hinged at the back, folded back over the tower's
        # back when parked, raised in Siege; its top is the hardpoint socket
        cube('cradle', (-18, -11), (77, 82), (-3, 3), 'dsteel', 'hardpoint')
        cube('cradle_top', (-17, -12), (82, 83), (-2, 2), 'brass', 'hardpoint')
        cube('cradle_hinge', (-17, -12), (77.5, 80.5), (3.5, 7.5), 'dsteel', 'hardpoint')
        # the jar's clamp: a steel knuckle at the niche's foot, a 3 px brass post up the niche, a rubber pad under the lid
        cube('clamp_knuckle', (-12, -8), (J + 1.1, J + 4.1), (-2, 2), 'steel', 'clamp')
        cube('clamp_post', (-11, -8), (J + 4.1, J + 12.1), (-1.5, 1.5), 'brass', 'clamp')
        cube('clamp_pad', (-8, -6), (J + 10.1, J + 12.1), (-2, 2), 'rubber', 'clamp')
        # ---- arm, short and slim, hung off the tower's outer face: a dark bearing, a steel upper arm, a dark elbow,
        # an enamel forearm, a rubber cuff, a dark fist with steel knuckles and a hazard-yellow finger block ----
        cube('shoulder', (-26, -18), (57, 64), (-5, 3), 'dsteel', 'arm')
        cube('upper_arm', (-25.5, -20.5), (53, 58), (-4, 2), 'steel', 'arm')
        cube('elbow', (-26.5, -19.5), (49, 54), (-4.5, 1.5), 'dsteel', 'forearm')
        cube('forearm', (-27, -19), (42, 50), (-5, 3), 'enamel', 'forearm')
        cube('cuff', (-27.5, -18.5), (40, 42), (-5.5, 3.5), 'rubber', 'forearm')
        cube('palm', (-27.5, -18.5), (34, 40), (-5.5, 3.5), 'dsteel', 'hand')
        cube('knuckles', (-27.5, -18.5), (32, 34), (-5.5, -2.5), 'steel', 'hand')
        cube('fingers', (-27, -19), (29, 35), (-1.5, 2.5), 'yellow', 'grip')
        # ---- leg, long and nearly straight at rest: a steel thigh with a dark ram down its back (it shows from
        # behind), a dark knee axle, the brass knee pad with two hazard spade teeth 4 px proud (they bite on Siege),
        # a steel shin, dark ankle, a big rubber boot with a brass toe cap ----
        cube('thigh', (-19, -9), (K - 1, HIP_Y + 1), (-5, 5), 'steel', 'leg')
        cube('thigh_ram', (-16, -12), (K + 3, HIP_Y - 3), (5, 8), 'dsteel', 'leg')
        cube('knee_axle', (-18, -10), (K - 3, K + 3), (-4, 4), 'dsteel', 'shin')
        cube('knee_pad', (-20, -8), (K - 8, K + 2), (-9, -5), 'brass', 'shin')
        cube('spade_o', (-19, -15), (K - 8, K - 2), (-13, -9), 'yellow', 'shin')
        cube('spade_i', (-13, -9), (K - 8, K - 2), (-13, -9), 'yellow', 'shin')
        cube('shin', (-18.5, -9.5), (A + 2, K - 2), (-4.5, 4.5), 'steel', 'shin')
        cube('ankle', (-19, -9), (A - 2, A + 2), (-5, 5), 'dsteel', 'foot')
        cube('boot', (-20.5, -7.5), (A - 4, A), (-14, 10), 'rubber', 'foot')
        cube('toe_cap', (-20, -8), (A - 4, A - 1), (-16, -14), 'brass', 'foot')
    return c


def locators():
    J = SEAT_Y
    K = HIP_Y - THIGH
    A = K - SHIN
    return [
        ('fx_step_l', [-HIP_X, A - 4, -1], 'foot_l'),
        ('fx_step_r', [HIP_X, A - 4, -1], 'foot_r'),
        ('fx_hand_l', [-18.5, 36, -1], 'hand_l'),       # the inner face of each fist; the grab point is their midpoint
        ('fx_hand_r', [18.5, 36, -1], 'hand_r'),
        ('fx_vent_l', [-14.5, 66, 8], 'vent_l'),
        ('fx_vent_r', [14.5, 66, 8], 'vent_r'),
        ('fx_jet_l', [-7, 49, 10], 'torso'),           # below the back sockets: jet wash and exhaust
        ('fx_jet_r', [7, 49, 10], 'torso'),
        ('fx_lamp_tail', [0, 58, 6.5], 'torso'),
        ('fx_core', [0, 46.5, -18.5], 'sensor'),
        ('fx_jar_exit', [0, J + 18, 0], 'torso'),
        ('fx_hull', [0, J + 2, 0], 'torso'),
        ('fx_wrist_l', [-23, 46, -5], 'socket_arm_l'),
        ('fx_wrist_r', [23, 46, -5], 'socket_arm_r'),
        ('fx_spade_l', [-14, K - 5, -13], 'shin_l'),     # the knee spades' teeth: Siege's block-crack and dust
        ('fx_spade_r', [14, K - 5, -13], 'shin_r'),
        ('fx_shoulder_l', [-14.5, 83, -3], 'socket_arm_2'),
        ('fx_shoulder_r', [14.5, 83, -3], 'socket_arm_3'),
    ]


# ---------------------------------------------------------------------------------------------------------------------
# Review stand-ins: the Bulwark and the Hopper (their own bones and cubes), a golem block-out and a player, in a row
# ---------------------------------------------------------------------------------------------------------------------
def _borrow(geo, prefix, dx):
    bs, cs = [], []
    for name, piv, parent, rot in geo.bones():
        if name == 'standin_jar':
            continue
        bs.append((prefix + name, [piv[0] + dx, piv[1], piv[2]], (prefix + parent) if parent else None, rot))
    for name, f, t, mat, parent, kw in geo.cubes():
        if name.startswith('standin') or parent == 'standin_jar':
            continue
        if mat not in MATERIALS:
            mat = {'lamp_g': 'lamp_p'}.get(mat, 'enamel')
        cs.append((prefix + name, [f[0] + dx, f[1], f[2]], [t[0] + dx, t[1], t[2]], mat, prefix + parent, kw))
    # a stand-in jar on each, at its own seat
    j = [b for b in geo.bones() if b[0] == 'jar_anchor'][0][1][1]
    for n, f, t, mat in (('jb', (-7, 0, -7), (7, 1, 7), 'standm'), ('jg', (-6.5, 1, -6.5), (6.5, 13, 6.5), 'standj'),
                         ('jl', (-7, 13, -7), (7, 14, 7), 'standm'), ('jh', (-4, 1.5, -4), (4, 9.5, 4), 'stand')):
        cs.append((prefix + 'standin_' + n, [f[0] + dx, f[1] + j, f[2]], [t[0] + dx, t[1] + j, t[2]], mat,
                   prefix + 'jar_anchor', {}))
    return bs, cs


def scale_standins():
    sys.path.insert(0, os.path.join(MECHA, 'hopper'))
    sys.path.insert(0, os.path.join(MECHA, 'bulwark'))
    import hopper_geo as H
    import bulwark_geo as B
    bb, bc = _borrow(B, 'b_', 56.0)
    hb, hc = _borrow(H, 'h_', 96.0)
    gx, px = -48, -70
    sb = [('standin_golem', [gx, 0, 0], None, None), ('standin_player', [px, 0, 0], None, None)]
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
    ]
    return bb + hb + sb, bc + hc + sc


def cow_standin(origin):
    """A cow-sized block held crosswise (vanilla cow: about 0.9 x 1.4 x 1.3 blocks; body along x so the two hands
    grip its ends) centred on origin (review only)."""
    x, y, z = origin
    return [C('cow_body', [x - 11, y - 5, z - 6], [x + 11, y + 5, z + 6], 'cow', 'standin_cow'),
            C('cow_head', [x + 11, y - 1, z - 4], [x + 16, y + 7, z + 4], 'cow', 'standin_cow')]


COW_GROUND = (0, 17, -24)      # review cow positions (FK-solved fist midpoints)
COW_HELD = (0, 48, -24)
COW_OVER = (0, 80, -4)


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


def coplanar(cs, rest_frames, leg_frames=()):
    """Same-facing faces in one plane that overlap, between cubes in the same rest frame. x faces are also compared
    between the hips frame and the leg bones' frames: a leg rotates about x, so its x faces stay in their planes."""
    out = []
    for i, a in enumerate(cs):
        for b in cs[i + 1:]:
            fa, fb = rest_frames.get(a[4]), rest_frames.get(b[4])
            for ax in range(3):
                if fa != fb and not (ax == 0 and fa in leg_frames and fb in leg_frames):
                    continue
                for sa, va in ((0, a[1][ax]), (1, a[2][ax])):
                    for sb, vb in ((0, b[1][ax]), (1, b[2][ax])):
                        if abs(va - vb) < 1e-6 and sa == sb:
                            o = [j for j in range(3) if j != ax]
                            if all(min(a[2][j], b[2][j]) - max(a[1][j], b[1][j]) > 1e-6 for j in o):
                                out.append((a[0], b[0], 'xyz'[ax], va))
    return out


def rest_frame_of(bs):
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
    sys.path.insert(0, MECHA)
    import bbbuild
    scale = '--scale' in sys.argv
    cows = '--cows' in sys.argv
    bs, cs, locs = bones(), cubes(), locators()
    if scale:
        b2, c2 = scale_standins()
        bs, cs = bs + b2, cs + c2
    if cows:
        bs = bs + [('standin_cow_ground', list(COW_GROUND), 'root', None),
                   ('standin_cow_held', list(COW_HELD), 'torso', None),
                   ('standin_cow_over', list(COW_OVER), 'torso', None)]
        for tag, bone, o in (('_g', 'standin_cow_ground', COW_GROUND), ('_h', 'standin_cow_held', COW_HELD),
                             ('_o', 'standin_cow_over', COW_OVER)):
            cs = cs + [(n + tag, f, t, m, bone, kw) for n, f, t, m, _, kw in cow_standin(o)]
    review = scale or cows
    check(cs)
    if not review:
        frames = rest_frame_of(bs)
        legs = {'root'} | {frames[n] for n in frames if n.split('_')[0] in LEG_BONES}
        cp = coplanar([c for c in cs if not c[0].startswith('standin') and '_lit' not in c[0]], frames, legs)
        if cp:
            sys.stderr.write('coplanar faces: %s\n' % cp)
    tex = (TEX_W, TEX_H) if not review else ((1024, 1024) if scale else (512, 512))
    offs, regions = pack(cs, *tex)
    png = os.path.join(HERE, 'colossus_placeholder.png' if not review else '_review_scale.png')
    draw(cs, offs, png, *tex)
    out = sys.argv[sys.argv.index('--out') + 1] if '--out' in sys.argv else os.path.join(HERE, 'colossus.bbmodel')
    bbbuild.build(out, NAME, bs, cs, locs, offs, png, tex)
    sys.stderr.write('wrote %s; rows by material %s\n' % (out, regions))
    sys.stderr.write('rest angles: thigh %.2f shin %.2f foot %.2f\n' % (REST_THIGH, REST_SHIN, REST_FOOT))
    sys.stderr.write('%d bones (%d exported), %d cubes, %d locators\n'
                     % (len(bs), len([b for b in bs if not b[0].startswith(('standin', 'h_', 'b_'))]), len(cs),
                        len(locs)))


if __name__ == '__main__':
    main()

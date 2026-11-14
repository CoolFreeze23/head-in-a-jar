"""Executive (heavy biped skeleton "biped_heavy", the Bulwark's bones at its own sizes): geometry, rig numbers, frame
parameters for the shared clip script, box-UV packing by material, placeholder sheet.

The board's company car: a dark suit on narrow legs with the director riding under a canopy of state, four vials of
the good stuff racked on its shoulders. It does not fight; it delegates. Drive plays the premium jingle.

Blockbench/GeckoLib entity space in pixels: origin on the ground between the feet, y up, the machine faces -Z, its left
is -X, 16 px to a block. Nothing is scaled: every cube is new, at 16 px to the block.

    python art/mecha/executive/executive_geo.py                        # executive_placeholder.png, executive.bbmodel
    python art/mecha/executive/executive_geo.py --scale --out X.bbmodel  # review copy: a player, a golem block-out, the
                                                                         # Executive, the Colossus and the Bulwark

Clips come from art/mecha/bulwark/bulwark_anim.py (the heavy-biped clip script) run for this frame by
executive_anim.py, which adds its boot, idle tic, drive jingle and build.

Numbers taken from the game:
  hitbox 2.4 x 4.4 blocks (38.4 x 70.4 px)             MechaFrame.EXECUTIVE ("Colossus Mk II")
  seat: host neck (0, 4.08, 0) blocks -> jar base       data/headinajar/headinajar/host/executive.json
  (0, 3.48, 0) blocks = (0, 55.68, 0) px
  speed 0.10 -> about 0.216 blocks (3.46 px) a tick     MechaFrame.EXECUTIVE, x 2.16
  slots legs 1, back 2, core 2, torso 5, arm 4, sensor 2; four Gene Ports
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MECHA = os.path.dirname(HERE)
TEX_W, TEX_H = 512, 256
NAME = 'executive'

# ---------------------------------------------------------------------------------------------------------------------
# Rig numbers (read by the clip script). Sagittal plane: z forward-negative, y up.
# ---------------------------------------------------------------------------------------------------------------------
HIP_X = 10.0            # hips at x = +-10: close-set legs under a narrow waist
HIP_Y = 43.0            # hip joint height at rest (the girder hangs under the waist, under nothing)
THIGH = 21.0            # hip to knee
SHIN = 22.0             # knee to ankle
ANKLE_H = 4.0           # ankle above the sole
SEAT_Y = 55.68          # the jar's base (code: neck 4.08 blocks - 0.6)
REST_ANKLE = (0.0, ANKLE_H)
REST_ARM = 0.0
REST_FOREARM = 15.0
REST_SPLAY = 4.0        # arms hang 4 degrees out
SHOULDER = (21.0, 54.0, -1.0)   # arm_* pivot (left at -x), under the padded shoulders
GENE_PORTS = 4
HANDS_BACK = ((0.0, 56.0), -55.0)   # arm (x, inward z), forearm x: the gloves meet behind the pelvis

# Frame parameters for the heavy-biped clip script (art/mecha/bulwark/bulwark_anim.py). Anything not given here keeps
# the Bulwark's value. Position keys authored in Bulwark pixels are multiplied by `scale`; angles are kept.
ANIM = dict(
    model=os.path.join(HERE, 'executive.bbmodel'),
    speed=0.10,                     # 0.216 blocks a tick
    scale=1.3,                      # a smaller bob and squat than the Colossus's 1.42: the expensive one is smooth
    toe=13.0, heel=8.0,
    step='quiet',                   # footfalls: step:l_quiet / step:r_quiet (the Colossus step through felt)
    # the walk: 10 ticks a step (2.16 blocks) on nearly straight legs, a straight back, the hands locked behind the
    # back (a bump in the outline from behind), a 1.5 degree swing about it
    walk=dict(n=20, stance=10, lift=6.0, swing_delay=1.2, roll=1.0, torso_roll=1.2, lean=0, torso_bob=0.2,
              arm=1.5, fore=1.0, arm_hold=HANDS_BACK[0], fore_hold=HANDS_BACK[1], clamp_tick=3),
    limp_up=0.0,                    # the limp's hips over rest (Bulwark px, scaled): the leg check
    # walk_c: 11 ticks a step (38.0 px = 0.88 of the 43 px leg, 2.38 blocks), each shoe down 12 (one tick of double
    # support), the hips 2 px up, the heel planted 18 px ahead, on the toe from 8 to 14 degrees, the hands behind the back
    walk_c=dict(n=22, stance=12, plant_z=-18.0, h_up=2.0, to=14.0, ho=8, lift=6.0, surge=0.3, roll=1.0, torso_roll=1.2,
                lean=0, torso_bob=0.2, arm=1.5, fore=1.0, arm_hold=HANDS_BACK[0], fore_hold=HANDS_BACK[1], clamp_tick=3),
    # walk_b: 8 ticks a step (1.73 blocks), each shoe down 10 (duty 0.63)
    walk_b=dict(n=16, stance=10, lift=7.0, swing_delay=1.0, roll=1.4, torso_roll=1.6, lean=0, torso_bob=0.3,
                arm=1.5, fore=1.0, arm_hold=HANDS_BACK[0], fore_hold=HANDS_BACK[1], clamp_tick=3),
    clamp_open=1.0,
    vent_axis='x',                  # back flaps hinged along x
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
    'enamel': ('ddd5c3', 255),   # e3: the canopy roof, padded shoulders, shirt front, cuffs and gloves
    'brass':  ('d99a35', 255),   # b3: canopy columns, collar, vial rack and caps, tie, buckle, hub caps
    'steel':  ('7b818b', 255),   # s3: shins, knuckles
    'dsteel': ('5c616b', 255),   # s2: the suit: chest, waist, sleeves, thighs, drums, girder
    'rubber': ('27262d', 255),   # r1: belt, shoes, grilles
    'yellow': ('f4bd1e', 255),   # w2: vent flaps only
    'lens':   ('141318', 255),
    'lamp_a': ('ffc24e', 255),
    'lamp_p': ('8cf0a8', 255),
    'vial':   ('86cac1', 110),   # teal glass: the Gene Port vials
    'serum':  ('c060d0', 255),   # a placeholder serum (the game tints it with the loaded serum's colour)
    'stand':  ('c87767', 255),
    'standj': ('5ba364', 120),
    'standm': ('8bc987', 255),
    'golem':  ('c9c2b5', 255),
    'player': ('4a6fa5', 255),
    'cow':    ('6b4a3a', 255),
}
ORDER = ['enamel', 'brass', 'steel', 'dsteel', 'rubber', 'yellow', 'lens', 'lamp_a', 'lamp_p', 'vial', 'serum',
         'stand', 'standj', 'standm', 'golem', 'player', 'cow']

LEG_BONES = ('leg', 'shin', 'foot')


# ---------------------------------------------------------------------------------------------------------------------
# Bones (46 exported): (name, pivot, parent, rest rotation). The Bulwark's 39 by the same names and hierarchy, plus
# socket_core_1, socket_torso_3/4, hardpoint_l/r and socket_arm_2/3.
# ---------------------------------------------------------------------------------------------------------------------
def bones():
    """49 exported: the Bulwark's 39, socket_core_1, socket_torso_3/4, socket_arm_2/3, gene_port_0..3, sunroof."""
    J = SEAT_Y
    b = [
        ('root', [0, 0, 0], None, None),
        ('hips', [0, HIP_Y, 0], 'root', None),
        ('socket_core', [0, 38, -3], 'hips', [-90, 0, 0]),
        ('socket_core_1', [0, 38, 3], 'hips', [-90, 0, 0]),
        ('torso', [0, HIP_Y, 0], 'hips', None),
        ('jar_anchor', [0, J, 0], 'torso', None),
        ('clamp_l', [-9.5, J + 2.6, 0], 'torso', None),
        ('clamp_r', [9.5, J + 2.6, 0], 'torso', None),
        ('sensor', [0, 54.5, -10], 'torso', None),            # the tie: knot at the collar, the cab lamp its pin
        ('lamp_core', [0, 51, -11], 'sensor', None),
        ('socket_sensor', [-6, 76, -12], 'torso', [90, 0, 0]),   # on the roof's front edge
        ('socket_sensor_1', [6, 76, -12], 'torso', [90, 0, 0]),
        ('lamp_tail', [0, 77, 0], 'torso', None),            # the canopy's four corner lamps
        ('vent_l', [-7, 55, 8.5], 'torso', None),
        ('vent_r', [7, 55, 8.5], 'torso', None),
        ('socket_back', [-6, 49, 8], 'torso', [0, 180, 0]),
        ('socket_back_1', [6, 49, 8], 'torso', [0, 180, 0]),
        ('socket_torso_0', [-11, 52, -9], 'torso', None),
        ('socket_torso_1', [11, 52, -9], 'torso', None),
        ('socket_torso_2', [0, 44, -4], 'hips', None),            # the girder front, under the belt
        ('socket_torso_3', [-21, 56.5, -7], 'torso', None),
        ('socket_torso_4', [21, 56.5, -7], 'torso', None),
        ('socket_arm_2', [-25, 57, 0], 'torso', [0, 0, 0]),
        ('socket_arm_3', [25, 57, 0], 'torso', [0, 0, 0]),
        ('gene_port_0', [-23.5, 61.5, 0], 'torso', None),    # the vials left to right: left outer
        ('gene_port_1', [-18.5, 61.5, 0], 'torso', None),    # left inner
        ('gene_port_2', [18.5, 61.5, 0], 'torso', None),     # right inner
        ('gene_port_3', [23.5, 61.5, 0], 'torso', None),     # right outer
        ('sunroof', [0, 76, 9], 'torso', None),              # the canopy's centre panel, hinged at the back
    ]
    ax, ay, az = SHOULDER
    K = HIP_Y - THIGH
    A = K - SHIN
    for side, s in (('l', -1), ('r', 1)):
        b += [
            ('arm_' + side, [s * ax, ay, az], 'torso', [REST_ARM, 0, s * REST_SPLAY]),
            ('forearm_' + side, [s * ax, 46, az], 'arm_' + side, [REST_FOREARM, 0, 0]),
            ('socket_arm_' + side, [s * ax, 41, -4], 'forearm_' + side, None),
            ('hand_' + side, [s * ax, 37, az], 'forearm_' + side, None),
            ('grip_' + side, [s * ax, 33, 2], 'hand_' + side, None),
            ('leg_' + side, [s * HIP_X, HIP_Y, 0], 'hips', [REST_THIGH, 0, 0]),
            ('socket_thigh_' + side, [s * 14, HIP_Y - 10, 0], 'leg_' + side, [0, -s * 90, 0]),
            ('shin_' + side, [s * HIP_X, K, 0], 'leg_' + side, [REST_SHIN, 0, 0]),
            ('socket_shin_' + side, [s * 13.5, K - 12, 0], 'shin_' + side, [0, -s * 90, 0]),
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
        # ---- hips under the waist: a dark girder, dark drums with brass hub caps, out past the waist (seen behind) ----
        C('girder', [-7, 39, -4], [7, 47, 4], 'dsteel', 'hips'),
        # ---- the suit: a black jacket in a V from a belted waist to the shoulder line, stepped lapels either side of a
        # white shirt front, a long brass tie (the look bone: its pin is the cab lamp) ----
        C('belt', [-9, 47, -7.5], [9, 49, 7.5], 'rubber', 'torso'),
        C('buckle', [-2, 47, -8.5], [2, 49, -7.5], 'brass', 'torso'),
        C('chest_low', [-11, 48.5, -8], [11, 52.5, 7], 'rubber', 'torso'),
        C('chest', [-14, 51, -9], [14, 55, 8], 'rubber', 'torso'),
        C('shirt', [-4, 49, -9.5], [4, 54, -8.5], 'enamel', 'torso'),
        C('tie_knot', [-2, 52, -11.5], [2, 55, -9.5], 'brass', 'sensor'),
        C('tie', [-1.5, 45, -11], [1.5, 52, -9], 'brass', 'sensor'),
        C('tie_pin', [-1, 49, -11.5], [1, 50, -10.5], 'lens', 'sensor'),
        C('tie_pin_lit', [-1, 49, -11.5], [1, 50, -10.5], 'lamp_p', 'lamp_core', inflate=0.25),
        C('grille_b', [-10, 51.5, 7.5], [10, 54.5, 8.5], 'rubber', 'torso'),
        # ---- the jar's seat and brass collar on the shoulder line ----
        C('seat', [-6, J - 1, -6], [6, J, 6], 'dsteel', 'torso'),
        C('collar_base_f', [-8.5, J - 0.9, -8.5], [8.5, J + 0.1, -7.5], 'dsteel', 'torso'),
        C('collar_base_b', [-8.5, J - 0.9, 7.5], [8.5, J + 0.1, 8.5], 'dsteel', 'torso'),
        C('collar_base_l', [-8.5, J - 0.9, -7.5], [-7.5, J + 0.1, 7.5], 'dsteel', 'torso'),
        C('collar_base_r', [7.5, J - 0.9, -7.5], [8.5, J + 0.1, 7.5], 'dsteel', 'torso'),
        C('collar_f', [-9.5, J + 0.1, -9.5], [9.5, J + 2.1, -7.5], 'brass', 'torso'),
        C('collar_b', [-9.5, J + 0.1, 7.5], [9.5, J + 2.1, 9.5], 'brass', 'torso'),
        C('collar_l', [-9.5, J + 0.1, -7.5], [-7.5, J + 2.1, 7.5], 'brass', 'torso'),
        C('collar_r', [7.5, J + 0.1, -7.5], [9.5, J + 2.1, 7.5], 'brass', 'torso'),
        # ---- the canopy of state: four slim brass columns, a closed roof (its centre a sunroof the jar leaves by on
        # eject), a dark ceiling 3 px up inside a brass valance that hangs round the edge, a lamp at each corner ----
        C('roof_f', [-16, 74, -15], [16, 76, -9], 'enamel', 'torso'),
        C('roof_b', [-16, 74, 9], [16, 76, 15], 'enamel', 'torso'),
        C('roof_l', [-16, 74, -9], [-9, 76, 9], 'enamel', 'torso'),
        C('roof_r', [9, 74, -9], [16, 76, 9], 'enamel', 'torso'),
        C('sunroof_panel', [-9, 74.5, -9], [9, 75.5, 9], 'enamel', 'sunroof'),
        C('ceiling', [-15, 73, -14], [15, 74, 14], 'dsteel', 'torso'),
        C('valance_f', [-16.5, 71, -15.5], [16.5, 74, -14.5], 'brass', 'torso'),
        C('valance_b', [-16.5, 71, 14.5], [16.5, 74, 15.5], 'brass', 'torso'),
        C('valance_l', [-16.5, 71, -14.5], [-15.5, 74, 14.5], 'brass', 'torso'),
        C('valance_r', [15.5, 71, -14.5], [16.5, 74, 14.5], 'brass', 'torso'),
        C('crest', [-6, 75, -16], [6, 78, -14], 'brass', 'torso'),
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
            if parent in ('torso', 'hips', 'lamp_tail', 'sensor'):
                p = parent
            elif parent in ('port_o', 'port_i'):
                p = 'gene_port_%d' % ({('l', 'port_o'): 0, ('l', 'port_i'): 1, ('r', 'port_i'): 2, ('r', 'port_o'): 3}[(side, parent)])
            else:
                p = parent + '_' + side
            c.append(C(name + '_' + side, [a, ys[0], zs[0]], [b2, ys[1], zs[1]], mat, p, **kw))
        cube('hip_drum', (-14, -6), (38.5, 47.5), (-5, 5), 'dsteel', 'hips')
        cube('hip_cap', (-15, -14), (41, 45), (-2, 2), 'brass', 'hips')
        cube('lapel_hi', (-9, -4), (52, 55), (-10, -9), 'dsteel', 'torso')
        cube('lapel_lo', (-7, -4), (49, 52), (-10, -9), 'dsteel', 'torso')
        cube('vent_flap', (-10, -4), (51, 55), (8.5, 9.5), 'yellow', 'vent')
        # the shoulder line: a dark yoke out to big padded enamel shoulders, the vial rack on each
        cube('yoke', (-18, -13), (52, 56), (-6, 6), 'rubber', 'torso')
        cube('shoulder_pad', (-25, -17), (53, 60), (-7, 7), 'enamel', 'torso')
        # the Gene Port rack: two tall vials a shoulder, side by side, standing above the shoulder pads
        cube('rack', (-26, -16), (60, 61), (-2.5, 2.5), 'brass', 'torso')
        cube('vial_o', (-25, -22), (61, 71), (-1.5, 1.5), 'vial', 'torso')
        cube('vial_i', (-20, -17), (61, 71), (-1.5, 1.5), 'vial', 'torso')
        cube('vial_cap_o', (-25.5, -21.5), (71, 72), (-2, 2), 'brass', 'torso')
        cube('vial_cap_i', (-20.5, -16.5), (71, 72), (-2, 2), 'brass', 'torso')
        cube('serum_o', (-24.5, -22.5), (61.5, 69.5), (-1, 1), 'serum', 'port_o')
        cube('serum_i', (-19.5, -17.5), (61.5, 69.5), (-1, 1), 'serum', 'port_i')
        # canopy columns and corner lamps
        cube('column_f', (-13, -11), (55, 73), (-12, -10), 'brass', 'torso')
        cube('column_b', (-13, -11), (55, 73), (10, 12), 'brass', 'torso')
        cube('lamp_f', (-16, -13), (76, 78), (-15, -12), 'lens', 'torso')
        cube('lamp_b', (-16, -13), (76, 78), (12, 15), 'lens', 'torso')
        cube('lamp_lit_f', (-16, -13), (76, 78), (-15, -12), 'lamp_a', 'lamp_tail', inflate=0.25)
        cube('lamp_lit_b', (-16, -13), (76, 78), (12, 15), 'lamp_a', 'lamp_tail', inflate=0.25)
        # the jar's clamp: a steel knuckle on the collar, a 3 px brass post, a rubber pad under the lid
        cube('clamp_knuckle', (-12, -8), (J + 1.1, J + 4.1), (-2, 2), 'steel', 'clamp')
        cube('clamp_post', (-11, -8), (J + 4.1, J + 12.1), (-1.5, 1.5), 'brass', 'clamp')
        cube('clamp_pad', (-8, -6), (J + 10.1, J + 12.1), (-2, 2), 'rubber', 'clamp')
        # ---- arm: a dark joint under the shoulder pad, dark sleeves, a white shirt cuff and a white-gloved hand ----
        cube('shoulder_joint', (-24, -18), (50, 54), (-4, 2), 'dsteel', 'arm')
        cube('upper_arm', (-23.5, -18.5), (46, 51), (-3.5, 1.5), 'rubber', 'arm')
        cube('elbow', (-23, -19), (44, 47), (-3, 1), 'steel', 'forearm')
        cube('forearm', (-24, -18), (38, 45), (-4, 2), 'rubber', 'forearm')
        cube('cuff', (-24.5, -17.5), (36, 38), (-4.5, 2.5), 'enamel', 'forearm')
        cube('palm', (-24, -18), (31, 36), (-4, 2), 'enamel', 'hand')
        cube('knuckles', (-24, -18), (29, 31), (-4, -2), 'steel', 'hand')
        cube('fingers', (-23.5, -18.5), (28, 33), (-1.5, 1.5), 'enamel', 'grip')
        # ---- leg: dark trousers (thigh), a steel shin with a white spat at the ankle, a black shoe ----
        cube('thigh', (-13, -7), (K - 1, HIP_Y + 1), (-3, 3), 'dsteel', 'leg')
        cube('knee', (-13.5, -6.5), (K - 2, K + 2), (-3, 3), 'steel', 'shin')
        cube('shin', (-12.5, -7.5), (A + 6, K - 1), (-2.5, 2.5), 'dsteel', 'shin')
        cube('spat', (-14, -6), (A + 1, A + 7), (-4, 4), 'enamel', 'shin')
        cube('ankle', (-12.5, -7.5), (A - 2, A + 2), (-2.5, 2.5), 'steel', 'foot')
        cube('shoe', (-14, -6), (A - 4, A), (-12, 6), 'rubber', 'foot')
    # the watch, left wrist only
    c.append(C('watch_l', [-25, 39, -3], [-24, 41, 1], 'brass', 'forearm_l'))
    return c


def locators():
    J = SEAT_Y
    A = HIP_Y - THIGH - SHIN
    return [
        ('fx_step_l', [-HIP_X, A - 4, -1], 'foot_l'),
        ('fx_step_r', [HIP_X, A - 4, -1], 'foot_r'),
        ('fx_hand_l', [-18, 32, -1], 'hand_l'),
        ('fx_hand_r', [18, 32, -1], 'hand_r'),
        ('fx_vent_l', [-7, 53, 10], 'vent_l'),
        ('fx_vent_r', [7, 53, 10], 'vent_r'),
        ('fx_jet_l', [-6, 46, 9], 'torso'),
        ('fx_jet_r', [6, 46, 9], 'torso'),
        ('fx_lamp_tail', [0, 78, 13], 'torso'),
        ('fx_core', [0, 50.5, -12], 'sensor'),
        ('fx_jar_exit', [0, J + 24, 0], 'torso'),
        ('fx_hull', [0, J + 2, 0], 'torso'),
        ('fx_wrist_l', [-21, 41, -4], 'socket_arm_l'),
        ('fx_wrist_r', [21, 41, -4], 'socket_arm_r'),
        ('fx_gene_0', [-23.5, 72, 0], 'gene_port_0'),     # bubbles and the serum's glow, one at each vial's cap
        ('fx_gene_1', [-18.5, 72, 0], 'gene_port_1'),
        ('fx_gene_2', [18.5, 72, 0], 'gene_port_2'),
        ('fx_gene_3', [23.5, 72, 0], 'gene_port_3'),
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
    sys.path.insert(0, os.path.join(MECHA, 'colossus'))
    sys.path.insert(0, os.path.join(MECHA, 'bulwark'))
    import colossus_geo as CG
    import bulwark_geo as B
    cb, cc = _borrow(CG, 'c_', 62.0)
    bb, bc = _borrow(B, 'b_', 118.0)
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
    return cb + bb + sb, cc + bc + sc


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
    png = os.path.join(HERE, 'executive_placeholder.png' if not review else '_review_scale.png')
    draw(cs, offs, png, *tex)
    out = sys.argv[sys.argv.index('--out') + 1] if '--out' in sys.argv else os.path.join(HERE, 'executive.bbmodel')
    bbbuild.build(out, NAME, bs, cs, locs, offs, png, tex)
    sys.stderr.write('wrote %s; rows by material %s\n' % (out, regions))
    sys.stderr.write('rest angles: thigh %.2f shin %.2f foot %.2f\n' % (REST_THIGH, REST_SHIN, REST_FOOT))
    sys.stderr.write('%d bones (%d exported), %d cubes, %d locators\n'
                     % (len(bs), len([b for b in bs if not b[0].startswith(('standin', 'h_', 'b_', 'c_'))]), len(cs),
                        len(locs)))


if __name__ == '__main__':
    main()

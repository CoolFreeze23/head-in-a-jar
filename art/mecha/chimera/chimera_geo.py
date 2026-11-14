"""Chimera (light biped skeleton "biped_light", the Hopper's bones at its own sizes): geometry, rig numbers, frame
parameters for the shared clip script, box-UV packing by material, placeholder sheet.

The lab result that was issued a hard hat: a company specimen cage with something pale and pleased with itself grown
through the bars, Bio-Gel running teal in its veins, three serum vials for gills, and a hard hat on the director. On
Drive it Grafts: it tastes what it just hit, gulps, and swells.

Blockbench/GeckoLib entity space in pixels: origin on the ground between the feet, y up, the machine faces -Z, its left
is -X, 16 px to a block. Nothing is scaled: every cube is new, at 16 px to the block.

    python art/mecha/chimera/chimera_geo.py                        # chimera_placeholder.png and chimera.bbmodel
    python art/mecha/chimera/chimera_geo.py --scale --out X.bbmodel  # review copy: a player, a golem block-out, the
                                                                     # Chimera, the Hopper and the Bulwark in a row

Clips come from art/mecha/hopper/hopper_anim.py (the light-biped clip script) run for this frame by chimera_anim.py,
which adds the Graft and the Chimera's own boot, idle tic and build.

Numbers taken from the game:
  hitbox 1.6 x 2.8 blocks (25.6 x 44.8 px)             MechaFrame.CHIMERA ("grown more than built")
  seat: host neck (0, 2.52, 0) blocks -> jar base       data/headinajar/headinajar/host/chimera.json
  (0, 1.92, 0) blocks = (0, 30.72, 0) px
  speed 0.12 -> about 0.259 blocks (4.15 px) a tick     MechaFrame.CHIMERA, x 2.16
  slots legs 1, back 1, core 1, torso 3, arm 2, sensor 1; three Gene Ports
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MECHA = os.path.dirname(HERE)
TEX_W, TEX_H = 256, 256
NAME = 'chimera'

# ---------------------------------------------------------------------------------------------------------------------
# Rig numbers (read by the clip script). Sagittal plane: z forward-negative, y up.
# ---------------------------------------------------------------------------------------------------------------------
HIP_X = 8.0             # hips at x = +-8, under the cage's flanks
HIP_Y = 24.0            # hip joint height at rest
THIGH = 14.0            # hip to knee
SHIN = 15.0             # knee to ankle
ANKLE_H = 3.0           # ankle above the sole
SEAT_Y = 30.72          # the jar's base (code: neck 2.52 blocks - 0.6)
LID = SEAT_Y + 14.0     # the jar's lid top: the hard hat sits on it
REST_ANKLE = (-1.0, ANKLE_H)   # a pixel ahead of the hip, as the Hopper
REST_ARM = 0.0
REST_FOREARM = 90.0     # forearm carried level, the tool pointing forward (the Hopper's arm)
REST_SPLAY = 12.0
GENE_PORTS = 3

# Frame parameters for the light-biped clip script (art/mecha/hopper/hopper_anim.py). Anything not given keeps the
# Hopper's value. Position keys authored in Hopper pixels are multiplied by `scale`; angles are kept.
ANIM = dict(
    model=os.path.join(HERE, 'chimera.bbmodel'),
    speed=0.12,                     # 0.259 blocks a tick
    scale=1.12,                     # the Hopper's bob, crouches and socket arrivals at this frame's size
    toe=9.0,
    # the walk: the Hopper's hop, 15 % slower in cadence: 7 ticks a step (1.81 blocks), each foot down 5
    walk=dict(n=14, stance=5, lift=7.3),
)


def ik(hip, ankle):
    """Two-bone reverse-jointed leg (the knee points back, as the Hopper's). Absolute (thigh, shin) angles in degrees
    from straight down, positive forward (-Z). An unreachable target gives a straight leg pointing at it."""
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
REST_THIGH = round(_t1, 2)
REST_SHIN = round(_t2 - _t1, 2)
REST_FOOT = round(-_t2, 2)

# placeholder flat mid-tones, house palette D (art/palette.json)
MATERIALS = {
    'enamel': ('ddd5c3', 255),   # e3: the cage's top ring (the company's paint), the eye
    'brass':  ('d99a35', 255),   # b3: collar, hat struts, knee caps
    'steel':  ('7b818b', 255),   # s3: cage posts, shoulder collars, forearm tubes, shins, braces, gill covers
    'dsteel': ('5c616b', 255),   # s2: tray, hip brackets, axles, ankles, the gill backing
    'rubber': ('27262d', 255),   # r1: soles, cuffs
    'yellow': ('f4bd1e', 255),   # w2: the hard hat
    'growth': ('eba688', 255),   # flesh 4 (peach): what grew through the cage, the thighs, the upper arms, the stalk
    'vein':   ('39787e', 255),   # teal 1: Bio-Gel veins (the game tints them with a grafted creature's blood)
    'lens':   ('141318', 255),
    'lamp_g': ('77f08a', 255),
    'lamp_a': ('ffc24e', 255),
    'lamp_p': ('8cf0a8', 255),
    'vial':   ('86cac1', 110),   # teal glass: the gill vials
    'serum':  ('c060d0', 255),   # a placeholder serum (the game tints it)
    'stand':  ('c87767', 255),
    'standj': ('5ba364', 120),
    'standm': ('8bc987', 255),
    'golem':  ('c9c2b5', 255),
    'player': ('4a6fa5', 255),
    'cow':    ('6b4a3a', 255),
}
ORDER = ['enamel', 'brass', 'steel', 'dsteel', 'rubber', 'yellow', 'growth', 'vein', 'lens', 'lamp_g', 'lamp_a',
         'lamp_p', 'vial', 'serum',
         'stand', 'standj', 'standm', 'golem', 'player', 'cow']

LEG_BONES = ('leg', 'shin', 'foot')


# ---------------------------------------------------------------------------------------------------------------------
# Bones (46 exported): (name, pivot, parent, rest rotation). The Bulwark's 39 by the same names and hierarchy, plus
# socket_core_1, socket_torso_3/4, hardpoint_l/r and socket_arm_2/3.
# ---------------------------------------------------------------------------------------------------------------------
def bones():
    """40 exported: the Hopper's 35, socket_torso_2, gene_port_0..2 and growth."""
    J = SEAT_Y
    b = [
        ('root', [0, 0, 0], None, None),
        ('hips', [0, HIP_Y, 0], 'root', None),
        ('socket_core', [0, 20, 0], 'hips', [-90, 0, 0]),
        ('torso', [0, HIP_Y, 0], 'hips', None),
        ('growth', [0, 25, 0], 'torso', None),                 # what grew through the cage: breathes, swells
        ('jar_anchor', [0, J, 0], 'torso', None),
        ('restraint', [0, 30, 8], 'torso', None),              # the hard hat on its strut, hinged at the back
        ('clamp_l', [-7.5, LID, 0], 'restraint', None),
        ('clamp_r', [7.5, LID, 0], 'restraint', None),
        ('sensor', [12, 29, -6], 'torso', None),               # the eye: on a tall stalk at the right shoulder,
                                                               # beside the jar, never in front of it
        ('socket_sensor', [12, 39, -11.5], 'sensor', None),    # forward from the eye
        ('lamp_sensor', [12, 39, -11], 'sensor', None),
        ('lamp_core', [0, 29, -10.5], 'torso', None),
        ('lamp_l', [-11, 30.5, 9], 'torso', None),
        ('lamp_r', [11, 30.5, 9], 'torso', None),
        ('vent_l', [-14, 28, -1], 'torso', None),              # gill covers on the flanks, hinged at the top
        ('vent_r', [14, 28, -1], 'torso', None),
        ('socket_back', [0, 20, 11], 'torso', [0, 180, 0]),
        ('socket_torso_0', [-11, 25, -10], 'torso', None),
        ('socket_torso_1', [11, 25, -10], 'torso', None),
        ('socket_torso_2', [0, 21, -10], 'torso', None),
        ('gene_port_0', [-5.5, 24, 12.9], 'torso', None),     # the gills, left to right from behind-left
        ('gene_port_1', [0, 24, 12.9], 'torso', None),
        ('gene_port_2', [5.5, 24, 12.9], 'torso', None),
    ]
    K = HIP_Y - THIGH
    A = K - SHIN
    for side, s in (('l', -1), ('r', 1)):
        b += [
            ('arm_' + side, [s * 14, 28, -2], 'torso', [REST_ARM, 0, s * REST_SPLAY]),
            ('forearm_' + side, [s * 14, 21, -2], 'arm_' + side, [REST_FOREARM, 0, 0]),
            ('socket_arm_' + side, [s * 14, 13, -2], 'forearm_' + side, [-90, 0, 0]),
            ('leg_' + side, [s * HIP_X, HIP_Y, 0], 'hips', [REST_THIGH, 0, 0]),
            ('socket_thigh_' + side, [s * 11, HIP_Y - 7, 0], 'leg_' + side, [0, -s * 90, 0]),
            ('shin_' + side, [s * HIP_X, K, 0], 'leg_' + side, [REST_SHIN, 0, 0]),
            ('socket_shin_' + side, [s * 10.5, K - 6, 0], 'shin_' + side, [0, -s * 90, 0]),
            ('foot_' + side, [s * HIP_X, A, 0], 'shin_' + side, [REST_FOOT, 0, 0]),
        ]
    b += [('standin_jar', [0, J, 0], 'jar_anchor', None)]
    return b


def C(name, f, t, mat, parent, **kw):
    return (name, list(f), list(t), mat, parent, kw)


def cubes():
    J = SEAT_Y
    L = LID
    K = HIP_Y - THIGH          # knee
    A = K - SHIN               # ankle
    c = [
        # ---- the specimen cage: a black tray, dark corner posts, an enamel top ring that carries the collar ----
        C('tray', [-11, 20, -9], [11, 22, 9], 'rubber', 'torso'),
        C('ring_f', [-12, 28, -10], [12, 30, -8], 'enamel', 'torso'),
        C('ring_b', [-12, 28, 8], [12, 30, 10], 'enamel', 'torso'),
        C('ring_l', [-12, 28, -8], [-10, 30, 8], 'enamel', 'torso'),
        C('ring_r', [10, 28, -8], [12, 30, 8], 'enamel', 'torso'),
        C('lens_core', [-1, 28.5, -10.5], [1, 29.5, -9.5], 'lens', 'torso'),
        C('lamp_core_lit', [-1, 28.5, -10.5], [1, 29.5, -9.5], 'lamp_g', 'lamp_core', inflate=0.25),
        # ---- what grew through it (the growth bone): a pale mass filling the cage, bulging out between the posts,
        # and lobes that break the box: one welling up over the rim beside the jar, one sagging under the belly,
        # one slumped over the left shoulder; Bio-Gel veins on the parts that stick out ----
        C('mass', [-9, 22, -8], [9, 30, 8], 'growth', 'growth'),
        C('bulge_f', [-7, 22.5, -11], [7, 27.5, -8], 'growth', 'growth'),
        C('bulge_b', [-8, 21.5, 8.5], [8, 28.5, 11.5], 'growth', 'growth'),
        C('lobe_rim', [-13, 29, -9], [-8, 34, -2], 'growth', 'growth'),
        C('lobe_rim_top', [-12, 34, -8], [-9, 36, -4], 'growth', 'growth'),
        C('lobe_belly', [-6, 16.5, -6], [6, 20.5, 5], 'growth', 'growth'),
        C('lobe_shoulder', [-18, 28.5, -4], [-12, 32.5, 3], 'growth', 'growth'),
        C('vein_f1', [-4, 23, -11.5], [-3, 27, -10.5], 'vein', 'growth'),
        C('vein_f3', [-3, 25, -11.5], [2, 26, -10.5], 'vein', 'growth'),
        C('vein_rim', [-13.5, 30, -7], [-12.5, 35, -6], 'vein', 'growth'),
        C('vein_belly', [-4, 15.5, -4], [3, 16.5, -3], 'vein', 'growth'),
        C('vein_shoulder', [-17, 32, -2], [-14, 33, -1], 'vein', 'growth'),
        # the gills: a dark backing, three teal glass vials in black socket rings, two thirds full, brass caps
        C('gill_backing', [-8.5, 22.5, 11], [8.5, 31.5, 12]
          , 'dsteel', 'torso'),
        C('vial_0', [-7, 23, 11.5], [-4, 31, 13.5], 'vial', 'torso'),
        C('vial_1', [-1.5, 23, 11.5], [1.5, 31, 13.5], 'vial', 'torso'),
        C('vial_2', [4, 23, 11.5], [7, 31, 13.5], 'vial', 'torso'),
        C('socket_ring_0', [-7.5, 22, 11.2], [-3.5, 24, 14.2], 'rubber', 'torso'),
        C('socket_ring_1', [-2, 22, 11.2], [2, 24, 14.2], 'rubber', 'torso'),
        C('socket_ring_2', [3.5, 22, 11.2], [7.5, 24, 14.2], 'rubber', 'torso'),
        C('serum_0', [-6.5, 24, 12.4], [-4.5, 29, 13.4], 'serum', 'gene_port_0'),
        C('serum_1', [-1, 24, 12.4], [1, 29, 13.4], 'serum', 'gene_port_1'),
        C('serum_2', [4.5, 24, 12.4], [6.5, 29, 13.4], 'serum', 'gene_port_2'),
        C('vial_cap_0', [-7.5, 31, 11.5], [-3.5, 32, 14.5], 'brass', 'torso'),
        C('vial_cap_1', [-2, 31, 11.5], [2, 32, 14.5], 'brass', 'torso'),
        C('vial_cap_2', [3.5, 31, 11.5], [7.5, 32, 14.5], 'brass', 'torso'),
        # ---- the jar's seat, the collar's dark base, the brass collar ----
        C('seat', [-6, J - 1, -6], [6, J, 6], 'dsteel', 'torso'),
        C('collar_base_f', [-8.5, J - 0.9, -8.5], [8.5, J + 0.1, -7.5], 'dsteel', 'torso'),
        C('collar_base_b', [-8.5, J - 0.9, 7.5], [8.5, J + 0.1, 8.5], 'dsteel', 'torso'),
        C('collar_base_l', [-8.5, J - 0.9, -7.5], [-7.5, J + 0.1, 7.5], 'dsteel', 'torso'),
        C('collar_base_r', [7.5, J - 0.9, -7.5], [8.5, J + 0.1, 7.5], 'dsteel', 'torso'),
        C('collar_f', [-9.5, J + 0.1, -9.5], [9.5, J + 2.1, -7.5], 'brass', 'torso'),
        C('collar_b', [-9.5, J + 0.1, 7.5], [9.5, J + 2.1, 9.5], 'brass', 'torso'),
        C('collar_l', [-9.5, J + 0.1, -7.5], [-7.5, J + 2.1, 7.5], 'brass', 'torso'),
        C('collar_r', [7.5, J + 0.1, -7.5], [9.5, J + 2.1, 7.5], 'brass', 'torso'),
        # ---- the eye (the look bone): a pale stalk rising beside the jar, a steel housing, the lens in a rim ----
        C('stalk', [11, 29, -7], [13, 37, -5], 'growth', 'sensor'),
        C('stalk_vein', [12.5, 30, -7.5], [13.5, 36, -6.5], 'vein', 'sensor'),
        C('eye', [9, 36, -11], [15, 42, -6], 'steel', 'sensor'),
        C('eye_rim_t', [9, 41, -12], [15, 42, -11], 'dsteel', 'sensor'),
        C('eye_rim_b', [9, 36, -12], [15, 37, -11], 'dsteel', 'sensor'),
        C('eye_lens', [10, 37, -11.5], [14, 41, -10.5], 'lens', 'sensor'),
        C('eye_lens_lit', [10, 37, -11.5], [14, 41, -10.5], 'lamp_p', 'lamp_sensor', inflate=0.25),
        # ---- the restraint: the director's hard hat, a domed shell with a ridge and a short peak, sitting on the lid
        # (on a thin dark strut at the back-right corner, clear of the jar from behind); two clamp pads press the glass at the shoulders ----
        C('hat_strut', [8, J + 1, 8], [10, L, 10], 'dsteel', 'restraint'),   # at the back-right corner, off the jar
        C('hat_brim', [-7.5, L, -7.5], [7.5, L + 1, 8.5], 'yellow', 'restraint'),
        C('hat_peak', [-5, L, -10.5], [5, L + 1, -7.5], 'yellow', 'restraint'),
        C('hat_band', [-7, L + 1, -7], [7, L + 2, 8], 'rubber', 'restraint'),
        C('hat_shell', [-6.5, L + 1.5, -6.5], [6.5, L + 3.5, 7.5], 'yellow', 'restraint'),
        C('hat_dome', [-5, L + 3.5, -5], [5, L + 5.5, 6], 'yellow', 'restraint'),
        C('hat_ridge', [-1, L + 5.5, -6], [1, L + 6.5, 7], 'yellow', 'restraint'),
        C('clamp_arm_l', [-8, L - 4, -1], [-7, L, 1], 'steel', 'clamp_l'),
        C('clamp_arm_r', [7, L - 4, -1], [8, L, 1], 'steel', 'clamp_r'),
        C('pad_l', [-7, L - 4, -2], [-6, L - 1, 2], 'rubber', 'clamp_l'),
        C('pad_r', [6, L - 4, -2], [7, L - 1, 2], 'rubber', 'clamp_r'),
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
            p = parent if parent in ('torso', 'hips', 'growth') else parent + '_' + side
            c.append(C(name + '_' + side, [a, ys[0], zs[0]], [b2, ys[1], zs[1]], mat, p, **kw))
        cube('post_f', (-12.5, -10.5), (22, 28), (-10.5, -8.5), 'dsteel', 'torso')
        cube('post_b', (-12.5, -10.5), (22, 28), (8.5, 10.5), 'dsteel', 'torso')
        cube('bulge_s', (-12, -9), (23, 27), (-6, 6), 'growth', 'growth')
        cube('vein_s', (-12.5, -11.5), (23.5, 26.5), (-3, -2), 'vein', 'growth')
        cube('gill_cover', (-14, -13), (23, 28), (-5, 3), 'steel', 'vent')
        cube('tail_lens', (-12.5, -10.5), (30, 31), (8.5, 10.5), 'lens', 'torso')
        cube('tail_lit', (-12.5, -10.5), (30, 31), (8.5, 10.5), 'lamp_a', 'lamp')
        # arm: a steel collar at the shoulder clamped round a grown upper arm (a steel band on it), the company's
        # steel tool tube for a forearm, a rubber cuff (the wrist socket)
        cube('shoulder', (-17, -13), (25, 29), (-4.5, 0.5), 'steel', 'arm')
        cube('upper_arm', (-16.5, -13.5), (21, 25), (-4, 0), 'growth', 'arm')
        cube('arm_band', (-17, -13), (22, 23), (-4.5, 0.5), 'steel', 'arm')
        cube('forearm', (-16.5, -11.5), (14, 21), (-4.5, 0.5), 'steel', 'forearm')
        cube('cuff', (-17, -11), (13, 15), (-5, 1), 'rubber', 'forearm')
        # leg (knee back): a dark bracket at the hip round a grown thigh with a steel brace, a dark knee axle, a brass
        # knee cap, steel shin, dark ankle, rubber sole with a heel spur
        cube('hip_bracket', (-10, -6), (HIP_Y - 3, HIP_Y + 3), (-3.5, 3.5), 'dsteel', 'leg')
        cube('thigh', (-10.5, -5.5), (K + 1, HIP_Y), (-3, 3), 'growth', 'leg')
        cube('thigh_vein', (-8.5, -7.5), (K + 3, HIP_Y - 4), (3, 4), 'vein', 'leg')
        cube('thigh_brace', (-11, -5), (K + 6, K + 8), (-3.5, 3.5), 'steel', 'leg')
        cube('knee_axle', (-10, -6), (K - 1, K + 2), (-1.5, 1.5), 'dsteel', 'shin')
        cube('knee', (-11, -5), (K - 5, K - 1), (-2.5, 2.5), 'brass', 'shin')
        cube('shin', (-10, -6), (A + 1, K - 4), (-2, 2), 'steel', 'shin')
        cube('ankle', (-10.5, -5.5), (A - 1.5, A + 1.5), (-2.5, 2.5), 'dsteel', 'foot')
        cube('sole', (-11.5, -4.5), (A - 3, A - 1), (-9, 3), 'rubber', 'foot')
        cube('heel', (-9, -7), (A - 3, A - 1), (3, 5), 'dsteel', 'foot')
    return c


def locators():
    J = SEAT_Y
    K = HIP_Y - THIGH
    A = K - SHIN
    return [
        ('fx_step_l', [-HIP_X, A - 3, -2], 'foot_l'),
        ('fx_step_r', [HIP_X, A - 3, -2], 'foot_r'),
        ('fx_vent_l', [-15, 25, -1], 'vent_l'),
        ('fx_vent_r', [15, 25, -1], 'vent_r'),
        ('fx_latch', [0, LID + 6, 0], 'restraint'),
        ('fx_jar_exit', [0, J + 20, 0], 'torso'),
        ('fx_hull', [0, J + 2, 0], 'torso'),
        ('fx_core', [0, 29, -11], 'torso'),
        ('fx_wrist_l', [-14, 13, -2], 'socket_arm_l'),
        ('fx_wrist_r', [14, 13, -2], 'socket_arm_r'),
        ('fx_knee_l', [-HIP_X, K - 3, 2.5], 'shin_l'),
        ('fx_knee_r', [HIP_X, K - 3, 2.5], 'shin_r'),
        ('fx_gene_0', [-5.5, 32, 12.5], 'gene_port_0'),
        ('fx_gene_1', [0, 32, 12.5], 'gene_port_1'),
        ('fx_gene_2', [5.5, 32, 12.5], 'gene_port_2'),
        ('fx_graft', [0, 25, -12], 'growth'),         # the gulp: the Graft's squelch and the creature-coloured puff
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
    hb, hc = _borrow(H, 'h_', 36.0)
    bb, bc = _borrow(B, 'b_', 72.0)
    gx, px = -40, -60
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
    return hb + bb + sb, hc + bc + sc


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
    png = os.path.join(HERE, 'chimera_placeholder.png' if not review else '_review_scale.png')
    draw(cs, offs, png, *tex)
    out = sys.argv[sys.argv.index('--out') + 1] if '--out' in sys.argv else os.path.join(HERE, 'chimera.bbmodel')
    bbbuild.build(out, NAME, bs, cs, locs, offs, png, tex)
    sys.stderr.write('wrote %s; rows by material %s\n' % (out, regions))
    sys.stderr.write('rest angles: thigh %.2f shin %.2f foot %.2f\n' % (REST_THIGH, REST_SHIN, REST_FOOT))
    sys.stderr.write('%d bones (%d exported), %d cubes, %d locators\n'
                     % (len(bs), len([b for b in bs if not b[0].startswith(('standin', 'h_', 'b_', 'c_'))]), len(cs),
                        len(locs)))


if __name__ == '__main__':
    main()

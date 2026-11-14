"""Wheelhouse (the first frame on the shared "wheeled" skeleton): geometry, rig numbers, box-UV packing by material,
placeholder sheet.

Source of truth for the Wheelhouse's cubes, bones, sockets and locators. Blockbench/GeckoLib entity space in pixels:
origin on the ground at the middle of the wheelbase, y up, the machine faces -Z, its left is -X, 16 px to a block.

    python art/mecha/wheelhouse/wheelhouse_geo.py                          # placeholder sheet + wheelhouse.bbmodel
    python art/mecha/wheelhouse/wheelhouse_geo.py --scale --out X.bbmodel  # comparison copy: the Hopper, the Bulwark, an
                                                                           # iron-golem block-out and a player beside it

The bbmodel is written by art/mecha/bbbuild.py (UUIDs from names; the clips survive a rebuild). Clips come from
wheelhouse_anim.py, which imports the rig numbers below.

What it is: the company's site tug. A low ivory works runabout on four big rubber wheels with brass hubs, four-wheel
steering, a sprung body, a striped buffer beam for shoving, a bench for two staff, a tool turret on the bonnet, and at
the back a raised helm where the pilot's jar rides in a brass binnacle ring, steered (so it says) by a brass ship's
wheel on the stern.

Numbers taken from the game:
  hitbox 2.2 x 1.5 blocks (35.2 x 24 px)                MechaFrame.WHEELHOUSE
  speed 0.24 -> 0.5184 blocks (8.29 px) a tick on       MechaFrame.WHEELHOUSE x 2.16 (vanilla ground movement, inferred);
  built ground, 0.08 base off-road                      MechaLogic.riddenSpeed / offRoad
  slots legs 0, back 2, core 1, torso 2, arm 1, sensor 1; two passenger seats; locomotion TREADS (no jump)
  seat today: host neck (0, 1.125, 0): the jar's base at 8.4 px, inside the body. This model puts the jar's base at
  (0, 22, +9.5) px; the host file must move the neck to match (section 7 of the notes).
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
TEX_W, TEX_H = 512, 256

# ---------------------------------------------------------------------------------------------------------------------
# Rig numbers (shared with wheelhouse_anim.py). These are what each frame on the wheeled skeleton supplies.
# ---------------------------------------------------------------------------------------------------------------------
WHEEL_R = 7.0           # tyre radius: the wheel spin divisor comes from this
AXLE_Y = 7.0            # axle height at rest = WHEEL_R (the tyre touches the ground)
TRACK_X = 14.5          # wheel centre, left -X / right +X
Z_FRONT = -13.0         # front axle (the machine faces -Z)
Z_REAR = 11.0           # rear axle: wheelbase 24
BODY_PIVOT = (0.0, 9.0, -1.0)   # the sprung body pitches and rolls about the middle of the wheelbase at its underside
JAR_Y = 22.0            # the jar's base (the collar bone's height)
JAR_Z = 9.5             # the jar's centre, behind the middle (the helm is at the back)
MAX_STEER_F = 30.0      # front lock, degrees
MAX_STEER_R = 30.0      # rear lock in the in-place pivot (four-wheel steer); 8 degrees in ordinary steering

CORNERS = {'fl': (-1, Z_FRONT), 'fr': (1, Z_FRONT), 'rl': (-1, Z_REAR), 'rr': (1, Z_REAR)}

# placeholder flat mid-tones, house palette D (art/palette.json)
MATERIALS = {
    'enamel': ('ddd5c3', 255),   # e3 ivory enamel: tub, bonnet, fenders, pedestal, running boards
    'brass':  ('d99a35', 255),   # b3: collar, binnacle ring and posts, hubs, ship's wheel, lamp housings, turret drum
    'steel':  ('7b818b', 255),   # s3: wheel rims, dampers, turret cradle, exhaust pipes, knuckles
    'dsteel': ('42464f', 255),   # s1 (paint s0-s1): the darks: chassis, axles, collar base, lamp hoods, recess plates
    'rubber': ('27262d', 255),   # r1: tyres, bench, grille, vent mouths, wheel grips
    'yellow': ('f4bd1e', 255),   # w2: hazard yellow on moving parts 4 px or wider: the buffer beam, the vent flaps
    'lens':   ('141318', 255),   # lamps off
    'lamp_w': ('fff2c4', 255),   # headlamps lit (amber 3)
    'lamp_a': ('ffc24e', 255),   # tail lamps lit (amber 2)
    'lamp_r': ('ff5a36', 255),   # brake lamps lit (red 2, steady, never blinking)
    'lamp_g': ('77f08a', 255),   # the helm lamp lit (green 2)
    'stand':  ('c87767', 255),   # stand-ins (never exported)
    'standj': ('5ba364', 120),
    'standm': ('8bc987', 255),
    'golem':  ('c9c2b5', 255),
    'player': ('4a6fa5', 255),
    'lamp_p': ('8cf0a8', 255),   # (only for the Hopper and Bulwark stand-ins in the scale copy)
}
ORDER = ['enamel', 'brass', 'steel', 'dsteel', 'rubber', 'yellow', 'lens', 'lamp_w', 'lamp_a', 'lamp_r', 'lamp_g',
         'lamp_p', 'stand', 'standj', 'standm', 'golem', 'player']


# ---------------------------------------------------------------------------------------------------------------------
# Bones (39 exported): (name, pivot, parent, rest rotation)
# ---------------------------------------------------------------------------------------------------------------------
def bones():
    b = [
        ('root', [0, 0, 0], None, None),
        ('chassis', [0, AXLE_Y, BODY_PIVOT[2]], 'root', None),
    ]
    for c, (s, z) in CORNERS.items():
        p = [s * TRACK_X, AXLE_Y, z]
        b += [('strut_' + c, p, 'chassis', None), ('steer_' + c, p, 'strut_' + c, None),
              ('wheel_' + c, p, 'steer_' + c, None)]
    b += [
        ('body', list(BODY_PIVOT), 'chassis', None),
        ('jar_anchor', [0, JAR_Y, JAR_Z], 'body', None),
        ('restraint', [-11, 35.5, JAR_Z], 'body', None),
        ('helm', [0, 14, 21.5], 'body', None),
        ('sensor', [-3, 15, -17], 'body', None),
        # the tool boom (v3, after the module fit): moved off the turret to the bumper's left end, low and outboard,
        # so a fitted tool never stands in front of the jar or the radiator from the front. The cradle (arm_l)
        # elevates, the ram (forearm_l) slides; the turret keeps the look and the sensor socket
        ('arm_l', [-14, 11, -23], 'body', None),
        ('forearm_l', [-14, 11, -23], 'arm_l', None),
        ('socket_arm_l', [-14, 11, -31], 'forearm_l', None),
        ('socket_sensor', [-3, 19, -14.5], 'sensor', [90, 0, 0]),
        ('buffer', [0, 7.5, -22], 'body', None),
        ('exhaust_l', [-9, 9, 21.5], 'body', None),
        ('exhaust_r', [9, 9, 21.5], 'body', None),
        ('vent_l', [-11, 15, -1.5], 'body', None),
        ('vent_r', [11, 15, -1.5], 'body', None),
        ('lamp_head_fl', [-15, 20, -20.5], 'steer_fl', None),
        ('lamp_head_fr', [15, 20, -20.5], 'steer_fr', None),
        ('flag', [14.5, 18, 4], 'body', [-90, 0, 0]),
        ('lamp_tail', [0, 20, 18.5], 'body', None),
        ('lamp_brake', [0, 20, 18.5], 'body', None),
        ('lamp_core', [0, 23, 19.5], 'body', None),
        ('socket_back', [-7.5, 11.5, 20], 'body', [0, 180, 0]),
        ('socket_back_1', [7.5, 11.5, 20], 'body', [0, 180, 0]),
        ('socket_core', [0, 9, -1], 'body', [-90, 0, 0]),
        # torso modules lie on the front mudguards' tops (riding the steering), behind the headlamps, like cans on a
        # jeep's wings: hung from the nose they reached the ground (moved after the module fit boards)
        ('socket_torso_0', [-14.5, 18, -3.5], 'steer_fl', [90, 0, 0]),
        ('socket_torso_1', [14.5, 18, -3.5], 'steer_fr', [90, 0, 0]),
        ('standin_jar', [0, JAR_Y, JAR_Z], 'jar_anchor', None),
    ]
    return b


def C(name, f, t, mat, parent, **kw):
    return (name, list(f), list(t), mat, parent, kw)


def cubes():
    J, JZ = JAR_Y, JAR_Z
    c = []

    def pair(name, xs, ys, zs, mat, parent, **kw):
        """Authored on the right (+X); the left is its mirror. parent may contain {s} for the side letter."""
        for side, sg in (('l', -1), ('r', 1)):
            x0, x1 = (xs[0], xs[1]) if sg > 0 else (-xs[1], -xs[0])
            suffix = ('f' + side) if parent.startswith(('steer_f', 'lamp_head_f')) else side    # parts riding a front wheel: _fl/_fr
            c.append(C('%s_%s' % (name, suffix), [x0, ys[0], zs[0]], [x1, ys[1], zs[1]], mat, parent.format(s=side), **kw))

    # ---- chassis (unsprung, dark): two rails and two axle beams. They show in the shadow gap under the body ----
    pair('rail', (3, 6), (3, 7), (-21, 18), 'dsteel', 'chassis')
    c.append(C('axle_f', [-10, 6, Z_FRONT - 1], [10, 8, Z_FRONT + 1], 'dsteel', 'chassis'))
    c.append(C('axle_r', [-10, 6, Z_REAR - 1], [10, 8, Z_REAR + 1], 'dsteel', 'chassis'))

    # ---- the four wheel stations: a stub axle and a damper on the strut. The tyre (a rubber ring stepped 2-1-1-2
    # like a circle) hangs on the steer bone and never spins, so its outline never turns into a diamond; inside it, on
    # the wheel bone, a steel disc recessed 2 px and a brass four-armed spider standing proud of the tyre spin, so the
    # turning shows as a bright cross in a dark ring ----
    for cn, (s, z) in CORNERS.items():
        def w(name, xs, ys, zs, mat, parent):
            x0, x1 = (xs[0], xs[1]) if s > 0 else (-xs[1], -xs[0])
            c.append(C('%s_%s' % (name, cn), [x0, ys[0], z + zs[0]], [x1, ys[1], z + zs[1]], mat, parent + cn))
        w('stub', (10, 12), (6, 8), (-1, 1), 'dsteel', 'strut_')
        w('damper', (8.5, 10.5), (8, 14), (-1, 1), 'steel', 'strut_')
        w('tyre_top', (12, 17), (11, 14), (-4, 4), 'rubber', 'steer_')
        w('tyre_bot', (12, 17), (0, 3), (-4, 4), 'rubber', 'steer_')
        w('tyre_f', (12, 17), (3, 11), (-7, -4), 'rubber', 'steer_')
        w('tyre_k', (12, 17), (3, 11), (4, 7), 'rubber', 'steer_')
        w('rim', (12, 15), (3, 11), (-4, 4), 'steel', 'wheel_')
        # one-pixel shoulders in the four corners, so the outline steps 2-1-1-2 like a circle instead of a cross
        w('tyre_c1', (12, 17), (11, 12), (-5, -4), 'rubber', 'steer_')
        w('tyre_c2', (12, 17), (11, 12), (4, 5), 'rubber', 'steer_')
        w('tyre_c3', (12, 17), (2, 3), (-5, -4), 'rubber', 'steer_')
        w('tyre_c4', (12, 17), (2, 3), (4, 5), 'rubber', 'steer_')
        w('spider_v', (15, 18), (4, 10), (-1, 1), 'brass', 'wheel_')
        w('spider_h', (14.5, 17.5), (6, 8), (-3, 3), 'brass', 'wheel_')

    # ---- the body (sprung): a 20-wide tub, a narrower bonnet between the front wheels (room for full lock), the
    # scuttle step between them; mudguards over every wheel 2 px above the tyre; running boards between them ----
    c += [
        C('tub', [-10, 9, -6], [10, 16, 19], 'enamel', 'body'),
        C('bonnet', [-8, 9, -20], [8, 14, -6], 'enamel', 'body'),
        # the radiator: a frame on the bonnet's nose round a rubber grille sunk 0.5 px behind it
        C('nose_t', [-8, 13, -21], [8, 14, -20], 'enamel', 'body'),
        C('nose_b', [-8, 9, -21], [8, 10, -20], 'enamel', 'body'),
        C('nose_l', [-8, 10, -21], [-6, 13, -20], 'enamel', 'body'),
        C('nose_r', [6, 10, -21], [8, 13, -20], 'enamel', 'body'),
        C('grille', [-6, 10, -20.5], [6, 13, -19.5], 'rubber', 'body'),
        # the bench for two staff (fx_seat_l/r), backed onto the helm
        C('bench', [-9, 16, -5], [9, 18, 0], 'rubber', 'body'),
        C('bench_back', [-9, 18, 0], [9, 20, 2], 'rubber', 'body'),
        C('bench_rail', [-9, 20, 0], [9, 21, 2], 'brass', 'body'),
        # the helm pedestal: its back face is a frame round a dark plate, set 2 px forward of the stern
        C('pedestal', [-8, 16, 2], [8, 21, 16], 'enamel', 'body'),
        C('ped_rim_t', [-8, 20, 16], [8, 21, 17], 'enamel', 'body'),
        C('ped_rim_l', [-8, 16, 16], [-6, 20, 17], 'enamel', 'body'),
        C('ped_rim_r', [6, 16, 16], [8, 20, 17], 'enamel', 'body'),
        C('ped_plate', [-6, 16.5, 15.5], [6, 19.5, 16.5], 'dsteel', 'body'),
        # the stern: a frame round a 16 x 4 field sunk a pixel, where the two back modules mount; the ship's wheel's
        # boss stands out of the field's middle
        C('stern_t', [-10, 14, 19], [10, 16, 20], 'enamel', 'body'),
        C('stern_b', [-10, 9, 19], [10, 10, 20], 'enamel', 'body'),
        C('stern_l', [-10, 10, 19], [-8, 14, 20], 'enamel', 'body'),
        C('stern_r', [8, 10, 19], [10, 14, 20], 'enamel', 'body'),
        C('helm_boss', [-1, 12, 19], [1, 14, 21], 'dsteel', 'body'),
        # ---- the jar's seat, the collar's dark base and the brass collar on top of the pedestal ----
        C('seat', [-6, J - 1, JZ - 6], [6, J, JZ + 6], 'dsteel', 'body'),
        C('collar_base_f', [-8.5, J - 0.9, JZ - 8.5], [8.5, J + 0.1, JZ - 7.5], 'dsteel', 'body'),
        C('collar_base_b', [-8.5, J - 0.9, JZ + 7.5], [8.5, J + 0.1, JZ + 8.5], 'dsteel', 'body'),
        C('collar_base_l', [-8.5, J - 0.9, JZ - 7.5], [-7.5, J + 0.1, JZ + 7.5], 'dsteel', 'body'),
        C('collar_base_r', [7.5, J - 0.9, JZ - 7.5], [8.5, J + 0.1, JZ + 7.5], 'dsteel', 'body'),
        C('collar_f', [-9.5, J + 0.1, JZ - 9.5], [9.5, J + 2.1, JZ - 7.5], 'brass', 'body'),
        C('collar_b', [-9.5, J + 0.1, JZ + 7.5], [9.5, J + 2.1, JZ + 9.5], 'brass', 'body'),
        C('collar_l', [-9.5, J + 0.1, JZ - 7.5], [-7.5, J + 2.1, JZ + 7.5], 'brass', 'body'),
        C('collar_r', [7.5, J + 0.1, JZ - 7.5], [9.5, J + 2.1, JZ + 7.5], 'brass', 'body'),
        # the helm lamp (green "on duty") on the back of the collar, under the jar, facing the pilot's camera
        C('core_lens', [-1.5, J, JZ + 9.5], [1.5, J + 2, JZ + 10.5], 'lens', 'body'),
        C('core_lit', [-1.5, J, JZ + 9.5], [1.5, J + 2, JZ + 10.5], 'lamp_g', 'lamp_core', inflate=0.25),
        # ---- the binnacle: two 2 px brass posts on steel knuckles at the jar's sides; a closed ring round the lid,
        # hinged on the left post and dropping onto a catch on the right. The ring (v2, "light"): a dark-steel band
        # one pixel thick standing round the lid (0.85 px clear of it), and on top of it a brass lip 2 px wide whose
        # inner edge overlaps the lid's edge by 0.65 px, which is what holds the jar down. From above: a 2 px brass
        # line with a half-pixel dark edge outside it, instead of 3.5 px of brass ----
        C('ring_f', [-9, J + 12.5, JZ - 9], [9, J + 14.5, JZ - 8], 'dsteel', 'restraint'),
        C('ring_b', [-9, J + 12.5, JZ + 8], [9, J + 14.5, JZ + 9], 'dsteel', 'restraint'),
        C('ring_l', [-9, J + 12.5, JZ - 8], [-8, J + 14.5, JZ + 8], 'dsteel', 'restraint'),
        C('ring_r', [8, J + 12.5, JZ - 8], [9, J + 14.5, JZ + 8], 'dsteel', 'restraint'),
        C('lip_f', [-8.5, J + 14.5, JZ - 8.5], [8.5, J + 15.5, JZ - 6.5], 'brass', 'restraint'),
        C('lip_b', [-8.5, J + 14.5, JZ + 6.5], [8.5, J + 15.5, JZ + 8.5], 'brass', 'restraint'),
        C('lip_l', [-8.5, J + 14.5, JZ - 6.5], [-6.5, J + 15.5, JZ + 6.5], 'brass', 'restraint'),
        C('lip_r', [6.5, J + 14.5, JZ - 6.5], [8.5, J + 15.5, JZ + 6.5], 'brass', 'restraint'),
        C('hinge', [-12, J + 12.5, JZ - 1.5], [-9, J + 14.5, JZ + 1.5], 'steel', 'restraint'),
        C('catch', [9, J + 12.5, JZ - 1.5], [12, J + 14.5, JZ + 1.5], 'steel', 'body'),
        # ---- the tool turret on the bonnet, left of the middle (the one arm): a dark base ring, a brass drum that
        # turns (the look), enamel cheeks, a steel cradle that elevates, a dark ram that slides and a brass tool plate
        C('turret_base', [-7, 14, -21], [1, 15, -13], 'dsteel', 'body'),
        C('drum', [-6, 15, -20], [0, 19, -14], 'brass', 'sensor'),
        C('cheek_l', [-6, 19, -19], [-5, 23, -15], 'enamel', 'sensor'),
        C('cheek_r', [-1, 19, -19], [0, 23, -15], 'enamel', 'sensor'),
        # ---- the tool boom at the bumper's left end (v3): a dark bracket from the nose's lower left corner, the steel
        # cradle that elevates, the dark ram that slides, the brass tool plate the arm module mounts on ----
        C('tool_bracket', [-12, 10.5, -22.5], [-8, 12.5, -20.5], 'dsteel', 'body'),
        C('cradle', [-16, 9, -25], [-12, 13, -21], 'steel', 'arm_l'),
        C('ram', [-15, 10, -30], [-13, 12, -22], 'dsteel', 'forearm_l'),
        C('tool_plate', [-16, 9, -31], [-12, 13, -30], 'brass', 'forearm_l'),
        # ---- the buffer beam (striped, it moves) on two brass-headed spring buffers ----
        C('buffer_beam', [-10, 6, -23], [10, 9, -21], 'yellow', 'buffer'),
        # ---- the ship's wheel on the stern (turns with the steering), 2 px proud of the stern: a 12 px octagonal
        # brass rim, thin spokes, a dark hub cap and eight rubber handle pegs (four square, four on the diagonals) ----
        C('wheel_rim_t', [-3, 18, 21], [3, 20, 23], 'brass', 'helm'),
        C('wheel_rim_b', [-3, 8, 21], [3, 10, 23], 'brass', 'helm'),
        C('wheel_rim_l', [-6, 11, 21], [-4, 17, 23], 'brass', 'helm'),
        C('wheel_rim_r', [4, 11, 21], [6, 17, 23], 'brass', 'helm'),
        C('wheel_rim_tl', [-5, 17, 21], [-3, 19, 23], 'brass', 'helm'),
        C('wheel_rim_tr', [3, 17, 21], [5, 19, 23], 'brass', 'helm'),
        C('wheel_rim_bl', [-5, 9, 21], [-3, 11, 23], 'brass', 'helm'),
        C('wheel_rim_br', [3, 9, 21], [5, 11, 23], 'brass', 'helm'),
        C('wheel_spoke_v', [-0.5, 10, 21.5], [0.5, 18, 22.5], 'brass', 'helm'),
        C('wheel_spoke_h', [-4, 13.5, 21], [4, 14.5, 22], 'brass', 'helm'),
        C('wheel_cap', [-1.5, 12.5, 22.5], [1.5, 15.5, 23.5], 'dsteel', 'helm'),
        C('grip_t', [-1, 20, 21.5], [1, 22, 22.5], 'rubber', 'helm'),
        C('grip_b', [-1, 6, 21.5], [1, 8, 22.5], 'rubber', 'helm'),
        C('grip_l', [-8, 13, 21.5], [-6, 15, 22.5], 'rubber', 'helm'),
        C('grip_r', [6, 13, 21.5], [8, 15, 22.5], 'rubber', 'helm'),
        C('grip_tl', [-6.5, 18.5, 21.5], [-4.5, 20.5, 22.5], 'rubber', 'helm'),
        C('grip_tr', [4.5, 18.5, 21.5], [6.5, 20.5, 22.5], 'rubber', 'helm'),
        C('grip_bl', [-6.5, 7.5, 21.5], [-4.5, 9.5, 22.5], 'rubber', 'helm'),
        C('grip_br', [4.5, 7.5, 21.5], [6.5, 9.5, 22.5], 'rubber', 'helm'),
        # ---- the RIGHT OF WAY flag (Rush Hour): a striped plate on a stalk, stowed lying forward on the right rear
        # mudguard (the bone's rest rotation), stood up on the wind-up ----
        C('flag_stalk', [14, 18, 3.5], [15, 30, 4.5], 'steel', 'flag'),
        C('flag_plate', [15, 24, 3.5], [21, 30, 4.5], 'yellow', 'flag'),
        # ---- stand-in jar (never exported) ----
        C('standin_jar_base', [-7, J, JZ - 7], [7, J + 1, JZ + 7], 'standm', 'standin_jar'),
        C('standin_jar_glass', [-6.5, J + 1, JZ - 6.5], [6.5, J + 13, JZ + 6.5], 'standj', 'standin_jar'),
        C('standin_jar_lid', [-7, J + 13, JZ - 7], [7, J + 14, JZ + 7], 'standm', 'standin_jar'),
        C('standin_jar_valve', [-2, J + 14, JZ - 2], [2, J + 16, JZ + 2], 'standm', 'standin_jar'),
        C('standin_head', [-4, J + 1.5, JZ - 4], [4, J + 9.5, JZ + 4], 'stand', 'standin_jar'),
    ]
    # mirrored pairs on the body
    # front mudguards are cycle wings: they ride on the steer bones and turn with the wheels (so does the headlamp
    # on each), on a dark stay from the stub axle
    pair('fender_f', (11, 18), (16, 18), (-21, -5), 'enamel', 'steer_f{s}')
    pair('wing_stay', (11, 12), (8, 16), (-14, -12), 'dsteel', 'steer_f{s}')
    pair('fender_r', (10, 18), (16, 18), (3, 19), 'enamel', 'body')
    pair('step', (10, 17), (9, 10), (-5, 3), 'enamel', 'body')
    # mudguard ends dropping 3 px in front of and behind each tyre, so each guard reads as an arch, not a shelf
    pair('guard_f_front', (11, 18), (13, 16), (-21, -20), 'enamel', 'steer_f{s}')
    pair('guard_f_back', (11, 18), (13, 16), (-6, -5), 'enamel', 'steer_f{s}')
    pair('guard_r_front', (10, 18), (13, 16), (3, 4), 'enamel', 'body')
    pair('guard_r_back', (10, 18), (13, 16), (18, 19), 'enamel', 'body')
    pair('post_knuckle', (9.5, 13.5), (J - 0.5, J + 2.5), (JZ - 2, JZ + 2), 'steel', 'body')
    pair('post', (10, 12), (J + 2.5, J + 12.5), (JZ - 1, JZ + 1), 'brass', 'body')
    # headlamps on the front mudguards: a brass housing, a dark hood, the lens sunk a pixel
    pair('head_housing', (13, 17), (18, 22), (-19, -16), 'brass', 'steer_f{s}')
    pair('head_hood_t', (13, 17), (21, 22), (-21, -19), 'dsteel', 'steer_f{s}')
    pair('head_hood_b', (13, 17), (18, 19), (-21, -19), 'dsteel', 'steer_f{s}')
    pair('head_hood_i', (13, 14), (19, 21), (-21, -19), 'dsteel', 'steer_f{s}')
    pair('head_hood_o', (16, 17), (19, 21), (-21, -19), 'dsteel', 'steer_f{s}')
    pair('head_lens', (14, 16), (19, 21), (-20, -19), 'lens', 'steer_f{s}')
    pair('head_lit', (14, 16), (19, 21), (-20, -19), 'lamp_w', 'lamp_head_f{s}', inflate=0.25)
    # tail lamps on the rear mudguards: a brass housing, a dark rim, an amber tail lens outboard and a red brake lens
    # inboard, both sunk a pixel
    pair('tail_housing', (12, 18), (18, 22), (15, 17), 'brass', 'body')
    pair('tail_rim_t', (12, 18), (21, 22), (17, 19), 'dsteel', 'body')
    pair('tail_rim_b', (12, 18), (18, 19), (17, 19), 'dsteel', 'body')
    pair('tail_rim_i', (12, 13), (19, 21), (17, 19), 'dsteel', 'body')
    pair('tail_rim_o', (17, 18), (19, 21), (17, 19), 'dsteel', 'body')
    pair('tail_lens', (15, 17), (19, 21), (17, 18), 'lens', 'body')
    pair('brake_lens', (13, 15), (19, 21), (17, 18), 'lens', 'body')
    pair('tail_lit', (15, 17), (19, 21), (17, 18), 'lamp_a', 'lamp_tail', inflate=0.25)
    pair('brake_lit', (13, 15), (19, 21), (17, 18), 'lamp_r', 'lamp_brake', inflate=0.25)
    # vents on the flanks ahead of the rear wheels: a rubber mouth, a striped flap hinged along its top
    pair('vent_mouth', (10, 11), (11, 15), (-5, 2), 'rubber', 'body')
    pair('vent_flap', (11, 12), (11, 15), (-5, 2), 'yellow', 'vent_{s}')
    # exhaust pipes poking out of the shadow gap under the stern, each capped by a striped flap that blows open
    pair('exhaust_pipe', (7, 11), (5, 9), (12, 21), 'steel', 'body')
    pair('exhaust_cap', (7, 11), (5, 9), (21, 22), 'yellow', 'exhaust_{s}')
    # buffers: steel plungers and brass heads
    pair('buffer_plunger', (7, 9), (6.5, 8.5), (-24, -23), 'steel', 'buffer')
    pair('buffer_head', (6, 10), (5.5, 9.5), (-25, -24), 'brass', 'buffer')
    # push rods (v2, joined): two steel rods behind the beam running back into the bonnet through dark sleeves on the
    # nose, 10 px long (0.5 px of it anchored in the beam), so at the beam's full travel (7 px out, 1 down) each rod is
    # still 1.5 px inside the bonnet. At rest
    # the rods are hidden in the bonnet; the sleeves show either side of the grille
    pair('buffer_rod', (5.5, 7.5), (8, 11), (-21.5, -11.5), 'steel', 'buffer')
    pair('buffer_sleeve', (4.5, 8.5), (7, 12), (-21.25, -20.25), 'dsteel', 'body')
    return c


def scale_standins():
    """Beside it, for scale (comparison copies only): the Hopper, the Bulwark (each its own bones and cubes), an iron golem
    block-out and a player."""
    sys.path.insert(0, os.path.join(os.path.dirname(HERE), 'hopper'))
    sys.path.insert(0, os.path.join(os.path.dirname(HERE), 'bulwark'))
    import hopper_geo as H
    import bulwark_geo as B
    bs, cs = [], []
    for tag, mod, dx in (('h_', H, 42.0), ('k_', B, 84.0)):
        for name, piv, parent, rot in mod.bones():
            if name == 'standin_jar':
                continue
            bs.append((tag + name, [piv[0] + dx, piv[1], piv[2]], (tag + parent) if parent else None, rot))
        for name, f, t, mat, parent, kw in mod.cubes():
            if name.startswith('standin') or parent == 'standin_jar' or name.endswith('_lit'):
                continue
            if mat not in MATERIALS:
                mat = 'enamel'
            cs.append((tag + name, [f[0] + dx, f[1], f[2]], [t[0] + dx, t[1], t[2]], mat, tag + parent, kw))
    gx, px = -42, -64
    bs += [('standin_golem', [gx, 0, 0], None, None), ('standin_player', [px, 0, 0], None, None)]
    cs += [
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
    return bs, cs


def seated_standins():
    """Two seated staff on the bench (comparison copies only): body upright over fx_seat, thighs forward over the bonnet."""
    bs, cs = [], []
    for side, x in (('l', -5), ('r', 5)):
        n = 'standin_rider_' + side
        bs.append((n, [x, 18, -2.5], 'body', None))
        cs += [C(n + '_body', [x - 4, 18, -4.5], [x + 4, 30, -0.5], 'player', n),
               C(n + '_head', [x - 4, 30, -6.5], [x + 4, 38, 1.5], 'stand', n),
               C(n + '_legs', [x - 4, 18, -16.5], [x + 4, 22, -4.5], 'player', n)]
    return bs, cs


def locators():
    out = []
    for cn, (s, z) in CORNERS.items():
        out.append(('fx_contact_' + cn, [s * TRACK_X, 0, z], 'strut_' + cn))   # the tyre's ground contact
    out += [
        ('fx_head_l', [-15, 20, -21.5], 'lamp_head_fl'), ('fx_head_r', [15, 20, -21.5], 'lamp_head_fr'),
        ('fx_tail_l', [-15, 20, 19.5], 'lamp_tail'), ('fx_tail_r', [15, 20, 19.5], 'lamp_tail'),
        ('fx_exhaust_l', [-9, 7, 22.5], 'exhaust_l'), ('fx_exhaust_r', [9, 7, 22.5], 'exhaust_r'),
        ('fx_vent_l', [-12.5, 12, -1.5], 'vent_l'), ('fx_vent_r', [12.5, 12, -1.5], 'vent_r'),
        ('fx_buffer', [0, 7.5, -25.5], 'buffer'),                  # the ram's contact point
        ('fx_wrist_l', [-14, 11, -31], 'socket_arm_l'),             # the tool mount; the module adds its own tip
        ('fx_jar_exit', [0, 40, JAR_Z], 'body'),
        ('fx_hull', [0, 24.5, JAR_Z], 'body'),                     # the collar
        ('fx_core', [0, 23, 20.5], 'lamp_core'),
        ('fx_seat_l', [-5, 18, -2.5], 'body'), ('fx_seat_r', [5, 18, -2.5], 'body'),   # passenger attachment points
    ]
    return out


def size(c):
    return [abs(c[2][i] - c[1][i]) for i in range(3)]


def box_uv_wh(c):
    w, h, d = [max(1, round(s)) for s in size(c)]
    return 2 * (w + d), d + h


PARTS = [('wheel', ('tyre', 'rim', 'spider', 'stub', 'damper')), ('chassis', ('rail', 'axle')),
         ('stern', ('stern', 'helm_boss', 'wheel_', 'grip', 'exhaust')), ('helm', ('pedestal', 'ped_', 'seat', 'collar', 'core_')),
         ('binnacle', ('post', 'ring', 'lip', 'hinge', 'catch')), ('turret', ('turret', 'drum', 'cheek', 'cradle', 'ram', 'tool')),
         ('front', ('bonnet', 'nose', 'grille', 'buffer', 'head_', 'brace')), ('flanks', ('fender', 'guard', 'step', 'vent', 'tail', 'brake', 'wing', 'flag')),
         ('cab', ('tub', 'bench'))]


def part(c):
    """The part an island belongs to, so the islands of one part lie together on the sheet."""
    for i, (name, prefixes) in enumerate(PARTS):
        if c[0].startswith(prefixes):
            return '%d_%s' % (i, name)
    return '9_' + c[0]


def uvmap(cs, offsets, path, regions, scale=3):
    """The paint map: every island outlined and named, on the placeholder colours, at x scale."""
    from PIL import Image, ImageDraw
    base = Image.open(path.replace('_uv.png', '_placeholder.png')).convert('RGBA')
    big = base.resize((base.width * scale, base.height * scale), Image.NEAREST)
    bg = Image.new('RGBA', big.size, (40, 42, 48, 255))
    bg.alpha_composite(big)
    d = ImageDraw.Draw(bg)
    for c in cs:
        w, h, dd = [max(1, round(s)) for s in size(c)]
        ox, oy = offsets[c[0]]
        d.rectangle([ox * scale, oy * scale, (ox + 2 * (w + dd)) * scale - 1, (oy + dd + h) * scale - 1], outline=(255, 60, 200))
        d.text((ox * scale + 2, (oy + dd) * scale + 1), c[0], fill=(255, 255, 255), stroke_width=1, stroke_fill=(0, 0, 0))
    for mat, (top, bot) in regions.items():
        d.line([0, top * scale, bg.width, top * scale], fill=(80, 220, 255))
        d.text((bg.width - 70, top * scale + 1), mat, fill=(80, 220, 255))
    bg.save(path)


def pin(cs, model, packed, tex_w=TEX_W, tex_h=TEX_H):
    """Keep the painted layout: every cube whose name and size are unchanged keeps its island where the existing
    model has it; a changed cube goes back to its old spot if its new island fits there among the others, else to the
    first free space (rows top to bottom). --repack ignores the old layout."""
    import json
    with open(model, encoding='utf-8') as fh:
        old = {e['name']: e for e in json.load(fh)['elements'] if e.get('type') == 'cube'}
    occ = [[False] * tex_w for _ in range(tex_h)]

    def rect(c, o):
        w, h = box_uv_wh(c)
        return o[0], o[1], w, h

    def free(x, y, w, h):
        if x < 0 or y < 0 or x + w > tex_w or y + h > tex_h:
            return False
        return not any(occ[yy][xx] for yy in range(y, y + h) for xx in range(x, x + w))

    def mark(x, y, w, h):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                occ[yy][xx] = True
    offs, moved = {}, []
    for c in cs:
        e = old.get(c[0])
        if e and [abs(e['to'][i] - e['from'][i]) for i in range(3)] == size(c):
            offs[c[0]] = list(e['uv_offset'])
            mark(*rect(c, offs[c[0]]))
        else:
            moved.append(c)
    for c in moved:
        w, h = box_uv_wh(c)
        e = old.get(c[0])
        spot = list(e['uv_offset']) if e and free(e['uv_offset'][0], e['uv_offset'][1], w, h) else None
        if spot is None:
            spot = next(([x, y] for y in range(tex_h) for x in range(tex_w) if free(x, y, w, h)), None)
        if spot is None:
            sys.exit('no free space for %s' % c[0])
        offs[c[0]] = spot
        mark(spot[0], spot[1], w, h)
        sys.stderr.write('island %s: %s at %s\n' % (c[0], 'old spot' if e and spot == e['uv_offset'] else 'moved', spot))
    regions = {}
    for c in cs:
        y0, y1 = offs[c[0]][1], offs[c[0]][1] + box_uv_wh(c)[1]
        r = regions.get(c[3], (y0, y1))
        regions[c[3]] = (min(r[0], y0), max(r[1], y1))
    return offs, regions


def pack(cs, tex_w=TEX_W, tex_h=TEX_H):
    """Islands grouped by material in bands of rows, and inside a material by part (the bone), so whoever paints it finds
    each part together and no island is shared between materials."""
    offsets, regions = {}, {}
    x = y = shelf = 0
    for mat in ORDER:
        items = sorted([c for c in cs if c[3] == mat], key=lambda c: (part(c), -box_uv_wh(c)[1], -box_uv_wh(c)[0], c[0]))
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
    riders = '--riders' in sys.argv
    bs, cs, locs = bones(), cubes(), locators()
    if scale:
        b2, c2 = scale_standins()
        bs, cs = bs + b2, cs + c2
    if riders:
        b2, c2 = seated_standins()
        bs, cs = bs + b2, cs + c2
    if '--envelope' in sys.argv:
        # comparison copies only: the light class's largest arm tool (12 across, 12 high, 25 long) on the arm socket
        cs = cs + [C('standin_tool', [-20, 5, -56], [-8, 17, -31], 'stand', 'socket_arm_l')]
    check(cs)
    if not (scale or riders):
        cp = coplanar([c for c in cs if not c[0].startswith('standin') and '_lit' not in c[0]
                       and c[3] != 'lamp_g'], rest_frame_of(bs))
        sys.stderr.write('coplanar faces: %s\n' % (cp or 'none'))
    review = scale or riders or '--envelope' in sys.argv
    tex = (TEX_W, TEX_H) if not review else (512, 512)
    model = os.path.join(HERE, 'wheelhouse.bbmodel')
    if not review and os.path.exists(model) and '--repack' not in sys.argv:
        offs, regions = pin(cs, model, None, *tex)
    else:
        offs, regions = pack(cs, *tex)
    png = os.path.join(HERE, 'wheelhouse_placeholder.png' if not review else '_review_scale.png')
    draw(cs, offs, png, *tex)
    if not review:
        uvmap([c for c in cs if not c[0].startswith('standin')], offs, os.path.join(HERE, 'wheelhouse_uv.png'), regions)
    out = sys.argv[sys.argv.index('--out') + 1] if '--out' in sys.argv else os.path.join(HERE, 'wheelhouse.bbmodel')
    bbbuild.build(out, 'wheelhouse', bs, cs, locs, offs, png, tex)
    sys.stderr.write('wrote %s; rows by material %s\n' % (out, regions))
    sys.stderr.write('%d bones (%d exported), %d cubes (%d exported), %d locators\n'
                     % (len(bs), len([b for b in bs if not b[0].startswith(('standin', 'h_', 'k_'))]), len(cs),
                        len([c for c in cs if not c[4].startswith(('standin', 'h_', 'k_'))]), len(locs)))


if __name__ == '__main__':
    main()

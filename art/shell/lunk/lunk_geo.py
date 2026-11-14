"""Lunk (a Utility Shell on the heavy biped skeleton "biped_heavy", the Bulwark's bones at its own sizes): geometry,
rig numbers, frame parameters for the heavy clip script, box-UV packing by material, placeholder sheet.

A filing cabinet with arms: three drawers on stubby legs, gorilla arms that knuckle the floor, the director held on top
by two bulldog clips. Strong, cheap and from the office. A shell is a body, not a machine with a job title: no
sockets, no cockpit, no lamps to speak of.

Blockbench/GeckoLib entity space in pixels: origin on the ground between the feet, y up, the body faces -Z, its left
is -X, 16 px to a block.

    python art/shell/lunk/lunk_geo.py                         # lunk_placeholder.png and lunk.bbmodel
    python art/shell/lunk/lunk_geo.py --scale --out X.bbmodel   # review copy: a player, a golem block-out, the Lunk,
                                                                # the Bulwark and the Hopper in a row

Clips come from art/mecha/bulwark/bulwark_anim.py (the heavy clip script) run for this body by lunk_anim.py.

Numbers taken from the game:
  size 1.4 x 2.7 blocks (22.4 x 43.2 px)                ModRegistry.LUNK (EntityType sized 1.4, 2.7)
  seat: host neck (0, 2.5, 0) blocks -> jar base        data/headinajar/headinajar/host/lunk.json
  (0, 1.9, 0) blocks = (0, 30.4, 0) px
  speed 0.18 x RIDDEN_SCALE 0.5 = 0.09 ridden ->       ShellEntity.lunk(), HostControl.riddenSpeed on the release
  about 0.194 blocks (3.11 px) a tick                  line, x 2.16 (vanilla ground movement)
  max health 60, attack 9, knockback immune, step 1     ShellEntity.lunk()
  primary ability: toss (range 4, power 1.4, cooldown 30)  host/lunk.json, HostAbilities "toss"
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MECHA = os.path.join(os.path.dirname(os.path.dirname(HERE)), 'mecha')
TEX_W, TEX_H = 256, 256
NAME = 'lunk'

# ---------------------------------------------------------------------------------------------------------------------
# Rig numbers (read by the clip script). Sagittal plane: z forward-negative, y up.
# ---------------------------------------------------------------------------------------------------------------------
HIP_X = 5.0             # stubby legs close under the cabinet
HIP_Y = 14.0            # hip joint height at rest: the stubby legs tucked under the bottom drawer
THIGH = 7.0             # hip to knee
SHIN = 7.0              # knee to ankle
ANKLE_H = 2.0           # ankle above the sole
SEAT_Y = 30.4           # the jar's base (code: neck 2.5 blocks - 0.6)
REST_ANKLE = (0.0, ANKLE_H)
REST_ARM = 0.0
REST_FOREARM = 22.0     # forearms angled forward: the knuckles rest ahead of the feet
REST_SPLAY = 5.0
SHOULDER = (12.5, 30.0, 0.0)

# Parameters for the heavy clip script (art/mecha/bulwark/bulwark_anim.py). The Lunk's clips are its own (native);
# the shared ones it borrows (hurt, destroyed) are scaled from the Bulwark's 3.1 blocks to 2.7.
ANIM = dict(
    model=os.path.join(HERE, 'lunk.bbmodel'),
    speed=0.09,                     # ridden: 0.18 x ShellEntity.RIDDEN_SCALE 0.5 (release branch) -> 0.194 blocks a tick
    scale=0.87,
    toe=6.0, heel=4.0,
    step='lunk',
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
    'enamel': ('ddd5c3', 255),   # the drawer fronts' card labels only
    'brass':  ('d99a35', 255),   # the label holders and the asset tag: the only brass on it
    'steel':  ('7b818b', 255),   # s3: drawer fronts, lid, arms, shins: office-grey sheet steel
    'dsteel': ('5c616b', 255),   # s2: the carcass (the dark gaps between drawers), thighs, shoulder bolts, the webcam
    'rubber': ('27262d', 255),   # r1: drawer pulls, work-glove fists, feet, the bulldog clips
    'lens':   ('141318', 255),
    'lamp_p': ('8cf0a8', 255),   # the webcam's light
    'lamp_a': ('ffc24e', 255),   # the status LED on the back
    'stand':  ('c87767', 255),
    'standj': ('5ba364', 120),
    'standm': ('8bc987', 255),
    'golem':  ('c9c2b5', 255),
    'player': ('4a6fa5', 255),
    'cow':    ('6b4a3a', 255),
    'yellow': ('f4bd1e', 255),
}
ORDER = ['enamel', 'brass', 'steel', 'dsteel', 'rubber', 'yellow', 'lens', 'lamp_a', 'lamp_p',
         'stand', 'standj', 'standm', 'golem', 'player', 'cow']

LEG_BONES = ('leg', 'shin', 'foot')


# ---------------------------------------------------------------------------------------------------------------------
# Bones (46 exported): (name, pivot, parent, rest rotation). The Bulwark's 39 by the same names and hierarchy, plus
# socket_core_1, socket_torso_3/4, hardpoint_l/r and socket_arm_2/3.
# ---------------------------------------------------------------------------------------------------------------------
EMPTY = ('socket_core', 'socket_sensor', 'socket_sensor_1', 'vent_l', 'vent_r', 'socket_back', 'socket_back_1',
         'socket_torso_0', 'socket_torso_1', 'socket_torso_2', 'socket_arm_l', 'socket_arm_r', 'socket_thigh_l',
         'socket_thigh_r', 'socket_shin_l', 'socket_shin_r')


def bones():
    """The Bulwark's 39 groups by the same names and hierarchy (the 16 in EMPTY carry nothing: no sockets, no vents),
    plus `drawer` (the top drawer, which slides): 40."""
    J = SEAT_Y
    b = [
        ('root', [0, 0, 0], None, None),
        ('hips', [0, HIP_Y, 0], 'root', None),
        ('socket_core', [0, 15, 0], 'hips', [-90, 0, 0]),
        ('torso', [0, HIP_Y, 0], 'hips', None),
        ('jar_anchor', [0, J, 0], 'torso', None),
        ('clamp_l', [-8.5, J, 0], 'torso', None),            # the bulldog clips, biting the jar's base rim
        ('clamp_r', [8.5, J, 0], 'torso', None),
        ('sensor', [5, 29.5, -8.5], 'torso', None),          # the webcam clipped to the lid's front edge
        ('lamp_core', [5, 31, -11], 'sensor', None),
        ('socket_sensor', [3, 32, -9], 'sensor', [90, 0, 0]),
        ('socket_sensor_1', [7, 32, -9], 'sensor', [90, 0, 0]),
        ('lamp_tail', [4, 24.5, 8.5], 'torso', None),        # the status LED on the back
        ('vent_l', [-9.5, 22, 0], 'torso', None),
        ('vent_r', [9.5, 22, 0], 'torso', None),
        ('socket_back', [-4, 22, 8], 'torso', [0, 180, 0]),
        ('socket_back_1', [4, 22, 8], 'torso', [0, 180, 0]),
        ('socket_torso_0', [-4, 23, -8], 'torso', None),
        ('socket_torso_1', [4, 23, -8], 'torso', None),
        ('socket_torso_2', [0, 26, -8], 'torso', None),
        ('drawer', [0, 26.5, -7.5], 'torso', None),
    ]
    ax, ay, az = SHOULDER
    K = HIP_Y - THIGH
    A = K - SHIN
    for side, s in (('l', -1), ('r', 1)):
        b += [
            ('arm_' + side, [s * ax, ay, az], 'torso', [REST_ARM, 0, s * REST_SPLAY]),
            ('forearm_' + side, [s * ax, 20, az], 'arm_' + side, [REST_FOREARM, 0, 0]),
            ('socket_arm_' + side, [s * ax, 13, -2], 'forearm_' + side, None),
            ('hand_' + side, [s * ax, 9, az], 'forearm_' + side, None),
            ('grip_' + side, [s * ax, 5, 2.5], 'hand_' + side, None),
            ('leg_' + side, [s * HIP_X, HIP_Y, 0], 'hips', [REST_THIGH, 0, 0]),
            ('socket_thigh_' + side, [s * 8, HIP_Y - 4, 0], 'leg_' + side, [0, -s * 90, 0]),
            ('shin_' + side, [s * HIP_X, K, 0], 'leg_' + side, [REST_SHIN, 0, 0]),
            ('socket_shin_' + side, [s * 7.5, K - 4, 0], 'shin_' + side, [0, -s * 90, 0]),
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
        # ---- hips: a dark plinth under the cabinet the stubby legs bolt to (seen behind) ----
        C('plinth', [-8, 12.5, -6], [8, 16.5, 6], 'dsteel', 'hips'),
        # ---- the cabinet: a dark carcass, three grey drawer fronts standing a pixel proud (the dark gaps between them
        # are the carcass), each with a black pull and a brass label holder; a grey lid ----
        C('carcass', [-9, 16, -7], [9, 29, 8], 'dsteel', 'torso'),
        C('lid', [-9.5, 29, -8], [9.5, 30, 8], 'steel', 'torso'),
        C('drawer_1', [-8, 17, -8], [8, 20, -7], 'steel', 'torso'),
        C('pull_1', [-2, 17.5, -9], [2, 18.5, -8], 'rubber', 'torso'),
        C('label_1', [-2.5, 18.75, -8.5], [2.5, 19.75, -7.5], 'brass', 'torso'),
        C('drawer_2', [-8, 21, -8], [8, 24, -7], 'steel', 'torso'),
        C('pull_2', [-2, 21.5, -9], [2, 22.5, -8], 'rubber', 'torso'),
        C('label_2', [-2.5, 22.75, -8.5], [2.5, 23.75, -7.5], 'brass', 'torso'),
        C('drawer_3', [-8, 25, -8], [8, 28, -7], 'steel', 'drawer'),
        C('pull_3', [-2, 25.5, -9], [2, 26.5, -8], 'rubber', 'drawer'),
        C('label_3', [-2.5, 26.75, -8.5], [2.5, 27.75, -7.5], 'brass', 'drawer'),
        C('drawer_3_box', [-7, 25.5, -6.5], [7, 27.5, 5.5], 'dsteel', 'drawer'),
        # back: a steel rim round a recessed dark field, a rubber cable in a slot down it, the asset tag and the LED
        C('back_rim_t', [-9, 27, 8], [9, 29, 9], 'steel', 'torso'),
        C('back_rim_b', [-9, 16, 8], [9, 18, 9], 'steel', 'torso'),
        C('back_rim_l', [-9, 18, 8], [-6, 27, 9], 'steel', 'torso'),
        C('back_rim_r', [6, 18, 8], [9, 27, 9], 'steel', 'torso'),
        C('cable', [-1, 16.5, 8.2], [1, 30.5, 9.2], 'rubber', 'torso'),
        # a brass asset tag and the status LED
        C('asset_tag', [-5, 21, 8], [-2, 24, 9], 'brass', 'torso'),
        C('led', [3, 24, 8], [5, 25, 9], 'lens', 'torso'),
        C('led_lit', [3, 24, 8], [5, 25, 9], 'lamp_a', 'lamp_tail', inflate=0.25),
        # ---- the jar on the lid in a plain steel ring ----
        C('ring_f', [-8.5, J - 0.5, -8.5], [8.5, J + 0.5, -7.5], 'steel', 'torso'),
        C('ring_b', [-8.5, J - 0.5, 7.5], [8.5, J + 0.5, 8.5], 'steel', 'torso'),
        C('ring_l', [-8.5, J - 0.5, -7.5], [-7.5, J + 0.5, 7.5], 'steel', 'torso'),
        C('ring_r', [7.5, J - 0.5, -7.5], [8.5, J + 0.5, 7.5], 'steel', 'torso'),
        # ---- the webcam (the look bone), clipped to the lid's front edge off-centre ----
        C('cam_clip', [4, 28, -9.5], [6, 30, -8.5], 'dsteel', 'sensor'),
        C('cam', [3, 30, -11], [7, 33, -8], 'dsteel', 'sensor'),
        C('cam_lens', [4, 30.5, -11.5], [6, 32.5, -10.5], 'lens', 'sensor'),
        C('cam_lens_lit', [4, 30.5, -11.5], [6, 32.5, -10.5], 'lamp_p', 'lamp_core', inflate=0.25),
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
            p = parent if parent in ('torso', 'hips') else parent + '_' + side
            c.append(C(name + '_' + side, [a, ys[0], zs[0]], [b2, ys[1], zs[1]], mat, p, **kw))
        # the bulldog clip: a black jaw biting the jar's base rim, two steel wire handles folded up the glass
        cube('clip_jaw', (-11.5, -6.5), (J - 1.5, J + 2.5), (-2.5, 2.5), 'rubber', 'clamp')
        cube('clip_bite', (-7, -6), (J, J + 2), (-2, 2), 'dsteel', 'clamp')
        cube('clip_lever', (-11, -9), (J + 2.5, J + 9.5), (-2, 2), 'dsteel', 'clamp')
        # arm: a dark shoulder block over the cabinet's top corner, long dark-steel arms (a step darker than the legs),
        # a steel elbow, a black work-glove fist with a steel knuckle plate
        cube('shoulder_block', (-15.5, -9.5), (27.5, 31.5), (-3, 3), 'dsteel', 'arm')
        cube('upper_arm', (-14.5, -10.5), (20.5, 27.5), (-2, 2), 'dsteel', 'arm')
        cube('elbow', (-15, -10), (18, 21), (-2.5, 2.5), 'steel', 'forearm')
        cube('forearm', (-14.5, -10.5), (9.5, 18.5), (-2, 2), 'dsteel', 'forearm')
        cube('fist', (-15.5, -9.5), (3, 9), (-3, 3), 'rubber', 'hand')
        cube('knuckle', (-15.5, -9.5), (1, 3), (-3, 0), 'steel', 'hand')
        cube('fingers', (-15, -10), (1, 5), (0.5, 3.5), 'rubber', 'grip')
        # leg: stubby and tucked: a dark hip block, a steel thigh and shin, a black foot pad
        cube('hip_block', (-8.5, -1.5), (HIP_Y - 2.5, HIP_Y + 2.5), (-3, 3), 'dsteel', 'leg')
        cube('thigh', (-7, -3), (K - 0.5, HIP_Y - 1.5), (-2, 2), 'steel', 'leg')
        cube('knee', (-7.5, -2.5), (K - 1.5, K + 1.5), (-2.5, 2.5), 'dsteel', 'shin')
        cube('shin', (-6.5, -3.5), (A + 1, K - 1), (-1.5, 1.5), 'steel', 'shin')
        cube('foot', (-8, -2), (A - 2, A + 1), (-6, 4), 'rubber', 'foot')
    return c


def locators():
    J = SEAT_Y
    A = HIP_Y - THIGH - SHIN
    return [
        ('fx_step_l', [-HIP_X, A - 2, -1], 'foot_l'),
        ('fx_step_r', [HIP_X, A - 2, -1], 'foot_r'),
        ('fx_hand_l', [-9.5, 6, 0], 'hand_l'),           # the inner faces of the fists
        ('fx_hand_r', [9.5, 6, 0], 'hand_r'),
        ('fx_knuckle_l', [-12.5, 1, -1.5], 'hand_l'),    # knuckle-walk dust
        ('fx_knuckle_r', [12.5, 1, -1.5], 'hand_r'),
        ('fx_lamp_tail', [6, 27, 9], 'torso'),
        ('fx_core', [5, 31.5, -12], 'sensor'),
        ('fx_jar_exit', [0, J + 18, 0], 'torso'),
        ('fx_hull', [0, J, 0], 'torso'),
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
    bb, bc = _borrow(B, 'b_', 38.0)
    hb, hc = _borrow(H, 'h_', 76.0)
    gx, px = -34, -54
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
    png = os.path.join(HERE, 'lunk_placeholder.png' if not review else '_review_scale.png')
    draw(cs, offs, png, *tex)
    out = sys.argv[sys.argv.index('--out') + 1] if '--out' in sys.argv else os.path.join(HERE, 'lunk.bbmodel')
    bbbuild.build(out, NAME, bs, cs, locs, offs, png, tex)
    sys.stderr.write('wrote %s; rows by material %s\n' % (out, regions))
    sys.stderr.write('rest angles: thigh %.2f shin %.2f foot %.2f\n' % (REST_THIGH, REST_SHIN, REST_FOOT))
    sys.stderr.write('%d bones (%d exported), %d cubes, %d locators\n'
                     % (len(bs), len([b for b in bs if not b[0].startswith(('standin', 'h_', 'b_', 'c_'))]), len(cs),
                        len(locs)))


if __name__ == '__main__':
    main()

"""Skitter (the first frame on the shared "multi-leg" skeleton): geometry, rig numbers, UV layout, placeholder sheet.

Source of truth for the Skitter's cubes, bones, sockets and locators. Blockbench/GeckoLib entity space in pixels:
origin on the ground under the body's middle, y up, the machine faces -Z, its left is -X, 16 px to a block.

    python art/mecha/skitter/skitter_geo.py                          # placeholder sheet, UV map, skitter.bbmodel
    python art/mecha/skitter/skitter_geo.py --scale --out X.bbmodel  # preview copy with the Hopper, the Bulwark, an
                                                                     # iron-golem block-out and a player beside it

The packing, sheet, UV-map, coplanar and UV-pinning helpers are the Wheelhouse's (art/mecha/wheelhouse/
wheelhouse_geo.py), imported with this frame's materials and parts. Once skitter.bbmodel exists, every unchanged cube
keeps its UV island (a painted layout survives a rebuild); --repack lays the sheet out afresh.

What it is: the Facilities Department's steeplejack. A four-legged wall-walking survey and rigging unit that goes up
the outside of buildings, with a cable reel on its back that fires and winds the company's hi-vis safety line (the
Dragline), a lamp-eyed survey head, one mandible gripper under its chin, and the pilot's jar on top in a four-post
climbing cage that clamps tighter when it leaves the ground.

Numbers taken from the game:
  hitbox 2.0 x 1.6 blocks (32 x 25.6 px)                MechaFrame.SKITTER
  speed 0.12 -> 0.2592 blocks (4.15 px) a tick          x 2.16 (vanilla ground movement, inferred)
  slots legs 0, back 1, core 1, torso 2, arm 1, sensor 2; Locomotion.SPIDER (walks up walls: MechaLogic line 196)
  seat today: host neck (0, 1.25, 0): the jar's base at 10.4 px, inside the body. This model puts it at (0, 18, 0)
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(os.path.dirname(HERE), 'wheelhouse'))
import wheelhouse_geo as W  # noqa: E402  (shared helpers)

TEX_W, TEX_H = 512, 256

# ---------------------------------------------------------------------------------------------------------------------
# Rig numbers (shared with skitter_anim.py; these are what each frame on the multi-leg skeleton supplies)
# ---------------------------------------------------------------------------------------------------------------------
BODY_Y = 13.0           # the body's pivot and the hips' height at rest
HIP = 9.5               # hips at (+-9.5, 13, +-9.5), the body's chamfered corners
FEMUR = 12.0            # hip to knee
TIBIA = 20.0            # knee to the foot's contact point (pad included)
FOOT = 18.0             # rest foot position (+-18, 0, +-18)
JAR_Y = 18.0            # the jar's base
CORNERS = {'fl': (-1, -1), 'fr': (1, -1), 'rl': (-1, 1), 'rr': (1, 1)}       # (x sign, z sign)
REST_YAW = {'fl': 45.0, 'fr': -45.0, 'rl': 135.0, 'rr': -135.0}             # hip rest yaw: local -Z points outward


def leg_ik(r, dy):
    """Two-bone leg in its own vertical plane, knee UP (spider). r = horizontal distance from hip to foot (outward),
    dy = foot height minus hip height. Returns (femur, tibia) absolute angles above horizontal, degrees; a Blockbench
    rot x of +a lifts a bone modelled along -Z by a."""
    d = math.hypot(r, dy)
    dc = min(max(d, abs(FEMUR - TIBIA) + 0.01), FEMUR + TIBIA - 0.001)
    phi = math.atan2(dy, r)
    alpha = math.acos((FEMUR * FEMUR + dc * dc - TIBIA * TIBIA) / (2 * FEMUR * dc))
    a1 = phi + alpha
    kx, ky = FEMUR * math.cos(a1), FEMUR * math.sin(a1)
    ex, ey = r * dc / d, dy * dc / d
    a2 = math.atan2(ey - ky, ex - kx)
    return math.degrees(a1), math.degrees(a2)


_REST_R = (FOOT - HIP) * math.sqrt(2)
_a1, _a2 = leg_ik(_REST_R, -BODY_Y)
REST_FEMUR = round(_a1, 2)
REST_TIBIA = round(_a2 - _a1, 2)

MATERIALS = dict(W.MATERIALS)
ORDER = list(W.ORDER)
PARTS = [('legs', ('hip_drum', 'femur', 'knee', 'tibia', 'pad', 'cuff')), ('cage', ('post', 'ring', 'lip', 'clamp')),
         ('collar', ('seat', 'collar')), ('reel', ('flange', 'cable', 'bracket', 'stay', 'tail', 'axle')),
         ('head', ('head', 'eye', 'brow', 'neck', 'launcher')), ('mandible', ('boom', 'jaw', 'mand')),
         ('pod', ('pod', 'belly', 'deck', 'vent', 'rim', 'band'))]


# ---------------------------------------------------------------------------------------------------------------------
# Bones (35 exported): (name, pivot, parent, rest rotation)
# ---------------------------------------------------------------------------------------------------------------------
def bones():
    J = JAR_Y
    b = [
        ('root', [0, 0, 0], None, None),
        ('body', [0, BODY_Y, 0], 'root', None),
        ('jar_anchor', [0, J, 0], 'body', None),
        ('restraint', [0, J + 13.5, 9], 'body', None),          # the cage: lifts straight up (board) or flips back (eject)
        ('clamp_l', [-8.5, J + 12.5, 0], 'restraint', None),
        ('clamp_r', [8.5, J + 12.5, 0], 'restraint', None),
        ('sensor', [0, 13, -10], 'body', None),                 # the survey head: the look
        ('lamp_core', [0, 14, -14.5], 'sensor', None),
        ('socket_sensor', [-2.5, 16, -12], 'sensor', [90, 0, 0]),
        ('socket_sensor_1', [2.5, 16, -12], 'sensor', [90, 0, 0]),
        ('arm_l', [0, 10, -12], 'body', None),                  # the mandible boom
        ('forearm_l', [0, 9.5, -20], 'arm_l', None),            # the jaw head
        ('grip_l', [0, 11, -24], 'forearm_l', None),            # the upper jaw: + opens
        ('socket_arm_l', [0, 9.5, -25], 'forearm_l', None),
        ('reel', [0, 13, 14], 'body', None),                    # the cable drum: rot x - pays out, + winds in
        ('launcher', [11.5, 19.5, -8.5], 'body', None),         # the line gun on its bracket: aimed by code, recoil
        ('lamp_tail', [0, 13, 17.5], 'body', None),
        ('vent_l', [-10.5, 15, 3.5], 'body', None),
        ('vent_r', [10.5, 15, 3.5], 'body', None),
        ('socket_back', [0, 7, 18.5], 'body', [0, 180, 0]),
        ('socket_core', [0, 7, 0], 'body', [-90, 0, 0]),
        # raised to the pod's top edge, between the front hip drum and the flank vent (moved after the module fit boards:
        # at mid-height they sat behind the front legs)
        ('socket_torso_0', [-10, 17, -3], 'body', [0, 90, 0]),
        ('socket_torso_1', [10, 17, -3], 'body', [0, -90, 0]),
    ]
    for c, (sx, sz) in CORNERS.items():
        p = [sx * HIP, BODY_Y, sz * HIP]
        b += [('hip_' + c, p, 'body', [0, REST_YAW[c], 0]),
              ('leg_' + c, p, 'hip_' + c, [REST_FEMUR, 0, 0]),
              ('shin_' + c, [p[0], p[1], p[2] - FEMUR], 'leg_' + c, [REST_TIBIA, 0, 0])]
    b += [('standin_jar', [0, J, 0], 'jar_anchor', None)]
    return b


C = W.C


def cubes():
    J = JAR_Y
    c = []

    def pair(name, xs, ys, zs, mat, parent, **kw):
        for side, sg in (('l', -1), ('r', 1)):
            x0, x1 = (xs[0], xs[1]) if sg > 0 else (-xs[1], -xs[0])
            c.append(C('%s_%s' % (name, side), [x0, ys[0], zs[0]], [x1, ys[1], zs[1]], mat, parent.format(s=side), **kw))

    # ---- the pod: two crossed enamel boxes (chamfered corners for the hips), a band of dark steel round its waist
    # (the shadow line between the deck and the skirt), a dark belly under it ----
    c += [
        C('pod_a', [-9, 9, -10], [9, 17, 10], 'enamel', 'body'),
        C('pod_b', [-10, 10, -8], [10, 16, 8], 'enamel', 'body'),
        C('belly', [-6, 7, -7], [6, 9, 7], 'dsteel', 'body'),
        # the jar's seat, the collar's dark base and the brass collar
        C('seat', [-6, J - 1, -6], [6, J, 6], 'dsteel', 'body'),
        C('collar_base_f', [-8.5, J - 1.5, -8.5], [8.5, J + 0.5, -7.5], 'dsteel', 'body'),
        C('collar_base_b', [-8.5, J - 1.5, 7.5], [8.5, J + 0.5, 8.5], 'dsteel', 'body'),
        C('collar_base_l', [-8.5, J - 1.5, -7.5], [-7.5, J + 0.5, 7.5], 'dsteel', 'body'),
        C('collar_base_r', [7.5, J - 1.5, -7.5], [8.5, J + 0.5, 7.5], 'dsteel', 'body'),
        C('collar_f', [-9.5, J + 0.5, -9.5], [9.5, J + 2.5, -7.5], 'brass', 'body'),
        C('collar_b', [-9.5, J + 0.5, 7.5], [9.5, J + 2.5, 9.5], 'brass', 'body'),
        C('collar_l', [-9.5, J + 0.5, -7.5], [-7.5, J + 2.5, 7.5], 'brass', 'body'),
        C('collar_r', [7.5, J + 0.5, -7.5], [9.5, J + 2.5, 7.5], 'brass', 'body'),
        # ---- the climbing cage (one bone): four 2 px steel posts standing on the collar's corners, a dark-steel band
        # round the lid with a 2 px brass lip over the lid's edge, and two rubber pads on the sides that press the
        # glass's shoulders (tighter on a wall). Nothing crosses the glass; upside down the lip and pads hold it ----
        C('ring_f', [-9, J + 12.5, -9], [9, J + 14.5, -8], 'dsteel', 'restraint'),
        C('ring_b', [-9, J + 12.5, 8], [9, J + 14.5, 9], 'dsteel', 'restraint'),
        C('ring_l', [-9, J + 12.5, -8], [-8, J + 14.5, 8], 'dsteel', 'restraint'),
        C('ring_r', [8, J + 12.5, -8], [9, J + 14.5, 8], 'dsteel', 'restraint'),
        C('lip_f', [-8.5, J + 14.5, -8.5], [8.5, J + 15.5, -6.5], 'brass', 'restraint'),
        C('lip_b', [-8.5, J + 14.5, 6.5], [8.5, J + 15.5, 8.5], 'brass', 'restraint'),
        C('lip_l', [-8.5, J + 14.5, -6.5], [-6.5, J + 15.5, 6.5], 'brass', 'restraint'),
        C('lip_r', [6.5, J + 14.5, -6.5], [8.5, J + 15.5, 6.5], 'brass', 'restraint'),
        C('post_fl', [-9.5, J + 2.5, -9.5], [-7.5, J + 12.5, -7.5], 'steel', 'restraint'),
        C('post_fr', [7.5, J + 2.5, -9.5], [9.5, J + 12.5, -7.5], 'steel', 'restraint'),
        C('post_rl', [-9.5, J + 2.5, 7.5], [-7.5, J + 12.5, 9.5], 'steel', 'restraint'),
        C('post_rr', [7.5, J + 2.5, 7.5], [9.5, J + 12.5, 9.5], 'steel', 'restraint'),
        C('clamp_pad_l', [-8, J + 8.5, -2], [-7, J + 12.5, 2], 'rubber', 'clamp_l'),
        C('clamp_pad_r', [7, J + 8.5, -2], [8, J + 12.5, 2], 'rubber', 'clamp_r'),
        # ---- the survey head at the front (the look): an enamel box, a dark brow, two lamp eyes sunk a pixel ----
        C('neck', [-3, 11, -11], [3, 15, -9], 'dsteel', 'sensor'),
        C('head', [-4.5, 10, -15], [4.5, 16, -11], 'enamel', 'sensor'),
        C('brow', [-4.5, 16, -15.5], [4.5, 17, -11.5], 'dsteel', 'sensor'),
        C('eye_lens_l', [-3.5, 12.5, -15.5], [-1.5, 14.5, -14.5], 'lens', 'sensor'),
        C('eye_lens_r', [1.5, 12.5, -15.5], [3.5, 14.5, -14.5], 'lens', 'sensor'),
        C('eye_lit_l', [-3.5, 12.5, -15.5], [-1.5, 14.5, -14.5], 'lamp_g', 'lamp_core', inflate=0.25),
        C('eye_lit_r', [1.5, 12.5, -15.5], [3.5, 14.5, -14.5], 'lamp_g', 'lamp_core', inflate=0.25),
        # ---- the mandible under the chin (the one arm): a steel boom, a brass jaw head, a striped upper jaw that
        # opens and a fixed dark lower jaw ----
        C('boom', [-1.5, 8.5, -21], [1.5, 11.5, -12], 'steel', 'arm_l'),
        C('jaw_head', [-3, 7, -24], [3, 12, -20], 'brass', 'forearm_l'),
        C('mand_lower', [-2.5, 7, -28], [2.5, 8, -24], 'dsteel', 'forearm_l'),
        C('mand_upper', [-2.5, 11, -28], [2.5, 12, -24], 'yellow', 'grip_l'),
        # ---- the cable reel behind the pod (spins): brass flanges, the wound line (black, the painted hi-vis tag on it), an axle; dark steel
        # brackets and a stay hold it; amber tail lamps on the brackets ----
        C('flange_l', [-6, 8, 9], [-5, 18, 19], 'brass', 'reel'),
        C('flange_r', [5, 8, 9], [6, 18, 19], 'brass', 'reel'),
        C('cable', [-5, 9, 10], [5, 17, 18], 'rubber', 'reel'),
        C('cable_tag', [-4, 16.5, 12], [4, 17.5, 16], 'yellow', 'reel'),     # a hi-vis tag on the drum: its turning shows
        C('axle', [-7, 12, 13], [7, 14, 15], 'dsteel', 'reel'),
        C('stay', [-6, 6, 16], [6, 8, 18], 'dsteel', 'body'),
        # ---- the line gun: on a dark bracket over the front-right hip, outside the jar's bounds, beside the cage:
        # a 3 x 3 x 8 steel barrel, a dark breech, a brass muzzle ring ----
        C('launcher_bracket', [8, 16, -9.5], [13, 18, -6.5], 'dsteel', 'body'),
        C('launcher_breech', [10.5, 18.5, -9], [12.5, 20.5, -7], 'dsteel', 'launcher'),
        C('launcher_barrel', [10, 18, -17], [13, 21, -9], 'steel', 'launcher'),
        C('launcher_muzzle', [9.5, 17.5, -18], [13.5, 21.5, -17], 'brass', 'launcher'),
        # ---- stand-in jar (never exported) ----
        C('standin_jar_base', [-7, J, -7], [7, J + 1, 7], 'standm', 'standin_jar'),
        C('standin_jar_glass', [-6.5, J + 1, -6.5], [6.5, J + 13, 6.5], 'standj', 'standin_jar'),
        C('standin_jar_lid', [-7, J + 13, -7], [7, J + 14, 7], 'standm', 'standin_jar'),
        C('standin_jar_valve', [-2, J + 14, -2], [2, J + 16, 2], 'standm', 'standin_jar'),
        C('standin_head', [-4, J + 1.5, -4], [4, J + 9.5, 4], 'stand', 'standin_jar'),
    ]
    pair('bracket', (6, 8), (6, 15), (10, 17), 'dsteel', 'body')
    pair('tail_lens', (6, 8), (12, 14), (17, 18), 'lens', 'body')
    pair('tail_lit', (6, 8), (12, 14), (17, 18), 'lamp_a', 'lamp_tail', inflate=0.25)
    # vents on the flanks behind the side sockets: a rubber mouth under a striped flap hinged along its top
    pair('vent_mouth', (9.5, 10.5), (11, 15), (1, 6), 'rubber', 'body')
    pair('vent_flap', (10.5, 11.5), (11, 15), (1, 6), 'yellow', 'vent_{s}')
    # ---- the four legs, each modelled straight along -Z from its hip (the hip bone's rest yaw turns it to its
    # corner; the femur and shin rest pitches fold it): a brass hip drum, an enamel femur (the light part of the leg,
    # so legs don't merge into a dark fringe), a brass knee drum, a steel tibia, a dark cuff and a rubber pad ----
    for cn, (sx, sz) in CORNERS.items():
        px, pz = sx * HIP, sz * HIP
        k = pz - FEMUR

        def leg(name, xs, ys, zs, mat, parent):
            c.append(C('%s_%s' % (name, cn), [px + xs[0], ys[0], zs[0]], [px + xs[1], ys[1], zs[1]], mat, parent + cn))
        leg('hip_drum', (-2.5, 2.5), (10, 16), (pz - 2.5, pz + 2.5), 'brass', 'hip_')
        leg('femur', (-2, 2), (11, 15), (k + 2, pz - 2), 'enamel', 'leg_')
        leg('knee', (-2, 2), (11, 15), (k - 2, k + 2), 'dsteel', 'shin_')
        leg('tibia', (-1.5, 1.5), (11.5, 14.5), (k - TIBIA + 4, k - 2), 'steel', 'shin_')
        leg('cuff', (-2, 2), (11, 15), (k - TIBIA + 2, k - TIBIA + 4), 'dsteel', 'shin_')
        leg('pad', (-2.5, 2.5), (10.5, 15.5), (k - TIBIA, k - TIBIA + 2), 'rubber', 'shin_')
    return c


def scale_standins():
    W.MATERIALS = MATERIALS
    bs, cs = W.scale_standins()
    return bs, cs


def locators():
    out = []
    for cn, (sx, sz) in CORNERS.items():
        out.append(('fx_foot_' + cn, [sx * HIP, BODY_Y, sz * HIP - FEMUR - TIBIA], 'shin_' + cn))   # the pad's contact
    out += [
        ('fx_eye_l', [-2.5, 13.5, -16], 'lamp_core'), ('fx_eye_r', [2.5, 13.5, -16], 'lamp_core'),
        ('fx_core', [0, 13.5, -16], 'lamp_core'),
        ('fx_line', [11.5, 19.5, -18.5], 'launcher'),         # the line leaves the muzzle
        ('fx_anchor', [0, 18, 14], 'body'),                   # where the machine hangs from its line (physics point)
        ('fx_jaw', [0, 9.5, -28.5], 'forearm_l'),
        ('fx_wrist_l', [0, 9.5, -25], 'socket_arm_l'),
        ('fx_vent_l', [-12, 12, 3.5], 'vent_l'), ('fx_vent_r', [12, 12, 3.5], 'vent_r'),
        ('fx_tail_l', [-7, 13, 18.5], 'lamp_tail'), ('fx_tail_r', [7, 13, 18.5], 'lamp_tail'),
        ('fx_jar_exit', [0, 36, 0], 'body'),
        ('fx_hull', [0, 20.5, 0], 'body'),
    ]
    return out


def main():
    sys.path.insert(0, os.path.dirname(HERE))
    import bbbuild
    W.MATERIALS, W.ORDER, W.PARTS = MATERIALS, ORDER, PARTS
    scale = '--scale' in sys.argv
    bs, cs, locs = bones(), cubes(), locators()
    if scale:
        b2, c2 = scale_standins()
        bs, cs = bs + b2, cs + c2
    W.check(cs)
    if not scale:
        cp = W.coplanar([c for c in cs if not c[0].startswith('standin') and '_lit' not in c[0]], W.rest_frame_of(bs))
        sys.stderr.write('coplanar faces: %s\n' % (cp or 'none'))
    tex = (TEX_W, TEX_H) if not scale else (512, 512)
    model = os.path.join(HERE, 'skitter.bbmodel')
    if not scale and os.path.exists(model) and '--repack' not in sys.argv:
        offs, regions = W.pin(cs, model, None, *tex)
    else:
        offs, regions = W.pack(cs, *tex)
    png = os.path.join(HERE, 'skitter_placeholder.png' if not scale else '_preview_scale.png')
    W.draw(cs, offs, png, *tex)
    if not scale:
        W.uvmap([c for c in cs if not c[0].startswith('standin')], offs, os.path.join(HERE, 'skitter_uv.png'), regions)
    out = sys.argv[sys.argv.index('--out') + 1] if '--out' in sys.argv else model
    bbbuild.build(out, 'skitter', bs, cs, locs, offs, png, tex)
    sys.stderr.write('rest angles: femur %.2f tibia %.2f\n' % (REST_FEMUR, REST_TIBIA))
    sys.stderr.write('%d bones (%d exported), %d cubes (%d exported), %d locators\n'
                     % (len(bs), len([b for b in bs if not b[0].startswith(('standin', 'h_', 'k_'))]), len(cs),
                        len([c for c in cs if not c[4].startswith(('standin', 'h_', 'k_'))]), len(locs)))


if __name__ == '__main__':
    main()

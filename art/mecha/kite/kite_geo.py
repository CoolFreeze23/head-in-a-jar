"""Kite (the first frame on the shared "winged" skeleton): geometry, rig numbers, UV layout, placeholder sheet.

Source of truth for the Kite's cubes, bones, sockets and locators. Blockbench/GeckoLib entity space in pixels: origin
on the ground between the feet, y up, the machine faces -Z, its left is -X, 16 px to a block. Helpers (packing, sheet,
UV map, coplanar check, UV pinning) are the Wheelhouse's, imported.

    python art/mecha/kite/kite_geo.py                          # placeholder sheet, UV map, kite.bbmodel
    python art/mecha/kite/kite_geo.py --scale --out X.bbmodel  # preview copy with the family beside it

What it is: the company's air courier. A long-winged ornithopter on bird legs that carries the pilot's jar slung
under its body in a brass lantern, the way a stork carries a parcel: the jar is in the open, seen from behind and
below, caged on every side for flight. Its talons are the Talon Lift (creatures only).

Numbers taken from the game:
  hitbox 1.8 x 2.0 blocks (28.8 x 32 px)                MechaFrame.KITE
  slots legs 1, back 0, core 1, torso 1, arm 2, sensor 1; Locomotion.WINGS (no Legs module needed to walk)
  speed 0.12 on the ground; FLIGHT_SPEED 0.6 aloft      MechaLogic.riddenSpeed
  seat today: host neck (0, 1.875, 0): the jar's base at 20.4 px. This model hangs it at (0, 7, 0), under the body
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(os.path.dirname(HERE), 'wheelhouse'))
import wheelhouse_geo as W  # noqa: E402

TEX_W, TEX_H = 512, 256

# ---------------------------------------------------------------------------------------------------------------------
# Rig numbers (shared with kite_anim.py; what each winged frame supplies)
# ---------------------------------------------------------------------------------------------------------------------
HIP_X = 12.5            # hips at x = +-12.5: the legs clear the lantern's collar by 1 px
HIP_Y = 24.0            # hip height at rest
THIGH = 12.0
SHIN = 13.0
ANKLE_H = 2.0
REST_ANKLE = (-1.0, ANKLE_H)
JAR_Y = 7.0             # the jar's base, in the lantern under the body
SHOULDER = (7.5, 32.0, -2.0)   # wing roots (x +-)
INNER = 12.0            # shoulder to wrist (the wingtip bone's pivot)
OUTER = 12.0            # wrist to the end of the membrane; the primaries reach 5 px further
REST_ARM, REST_FOREARM, REST_SPLAY = 0.0, 90.0, 0.0
ARM_X = 16.0            # arms on outriggers below the wing roots, outboard of the legs: seen from behind


def ik(hip, ankle):
    """Two-bone reverse-jointed leg, knee back (the Hopper's). Absolute (thigh, shin) angles from straight down,
    positive forward (-Z)."""
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

MATERIALS = dict(W.MATERIALS)
ORDER = list(W.ORDER)
PARTS = [('wings', ('spar', 'membrane', 'rib', 'primary', 'nav', 'wing_root')), ('legs', ('hip_drum', 'thigh', 'knee', 'shin', 'ankle', 'toe', 'claw')),
         ('lantern', ('seat', 'collar', 'post', 'ring', 'lip', 'hinge')), ('arms', ('shoulder', 'upper', 'elbow', 'fore', 'cuff', 'outrigger')),
         ('body', ('fuselage', 'belly', 'chest')), ('head', ('head', 'neck', 'visor', 'beak')), ('tail', ('tail', 'rudder'))]


def bones():
    J = JAR_Y
    sx, sy, sz = SHOULDER
    b = [
        ('root', [0, 0, 0], None, None),
        ('hips', [0, HIP_Y, 0], 'root', None),
        ('socket_core', [0, 24, 6], 'hips', [-90, 0, 0]),
        ('torso', [0, HIP_Y, 0], 'hips', None),
        ('jar_anchor', [0, J, 0], 'torso', None),
        ('restraint', [0, J - 1, 9.5], 'torso', None),          # the lantern's floor: lowers on cables (y), tips back (rot x)
        ('sensor', [0, 30, -10], 'torso', None),
        ('lamp_core', [0, 31, -16], 'sensor', None),
        ('socket_sensor', [0, 34, -13.5], 'sensor', [90, 0, 0]),
        # on the fuselage's back, lying forward from the tail root (moved after the module fit boards: hung off the chest
        # a module crossed the top of the jar seen from the front)
        ('socket_torso_0', [0, 34, 7], 'torso', [90, 0, 0]),
        ('tail', [0, 28, 9], 'torso', None),
    ]
    for side, s in (('l', -1), ('r', 1)):
        b += [
            ('wing_' + side, [s * sx, sy, sz], 'torso', None),
            ('wingtip_' + side, [s * (sx + INNER), sy, sz - 1], 'wing_' + side, None),
            ('lamp_nav_' + side, [s * (sx + INNER + OUTER + 1), sy + 1, sz - 2], 'wingtip_' + side, None),
            ('arm_' + side, [s * ARM_X, 28, -3], 'torso', [REST_ARM, 0, s * REST_SPLAY] if REST_SPLAY else None),
            ('forearm_' + side, [s * ARM_X, 22, -3], 'arm_' + side, [REST_FOREARM, 0, 0]),
            ('socket_arm_' + side, [s * ARM_X, 16, -3], 'forearm_' + side, [-90, 0, 0]),
            ('leg_' + side, [s * HIP_X, HIP_Y, 0], 'hips', [REST_THIGH, 0, 0]),
            ('socket_thigh_' + side, [s * (HIP_X + 2), 18, 0], 'leg_' + side, [0, -s * 90, 0]),
            ('shin_' + side, [s * HIP_X, HIP_Y - THIGH, 0], 'leg_' + side, [REST_SHIN, 0, 0]),
            ('socket_shin_' + side, [s * (HIP_X + 1.5), 5, 0], 'shin_' + side, [0, -s * 90, 0]),
            ('foot_' + side, [s * HIP_X, HIP_Y - THIGH - SHIN, 0], 'shin_' + side, [REST_FOOT, 0, 0]),
            ('talon_' + side, [s * HIP_X, HIP_Y - THIGH - SHIN - 1.5, -1], 'foot_' + side, None),
        ]
    b += [('standin_jar', [0, J, 0], 'jar_anchor', None)]
    return b


C = W.C


def cubes():
    J = JAR_Y
    sx, sy, sz = SHOULDER
    c = []

    def pair(name, xs, ys, zs, mat, parent, **kw):
        for side, sg in (('l', -1), ('r', 1)):
            x0, x1 = (xs[0], xs[1]) if sg > 0 else (-xs[1], -xs[0])
            c.append(C('%s_%s' % (name, side), [x0, ys[0], zs[0]], [x1, ys[1], zs[1]], mat, parent.format(s=side), **kw))

    c += [
        # ---- the fuselage: two crossed enamel boxes, a dark keel strip under them, a brass chest plate ----
        C('fuselage_a', [-7, 25, -9], [7, 33, 9], 'enamel', 'torso'),
        C('fuselage_b', [-6, 24, -10], [6, 34, 8], 'enamel', 'torso'),
        C('belly_plate', [-10, 23, -10], [10, 24, 10], 'dsteel', 'torso'),     # the dark mass under the body
        C('chest_plate', [-4, 26, -11], [4, 31, -10], 'brass', 'torso'),
        # ---- the head (the look): a dark neck, an enamel head, a phosphor visor sunk under a dark brow, a brass beak ----
        C('neck', [-2, 28, -12], [2, 32, -9], 'dsteel', 'sensor'),
        C('head', [-3, 28, -16], [3, 34, -12], 'enamel', 'sensor'),
        C('head_brow', [-3.5, 33.5, -16.5], [3.5, 34.5, -15.5], 'dsteel', 'sensor'),
        C('visor_lens', [-2.5, 30, -16.5], [2.5, 32, -15.5], 'lens', 'sensor'),
        C('visor_lit', [-2.5, 30, -16.5], [2.5, 32, -15.5], 'lamp_p', 'lamp_core', inflate=0.25),
        C('beak', [-1, 29, -19], [1, 30, -16], 'brass', 'sensor'),
        # ---- the tail: a fan for flare and bank, a brass rudder, a white tail lamp is the paint's ----
        C('tail_plane', [-5, 27.5, 9], [5, 28.5, 18], 'enamel', 'tail'),
        C('tail_fan_l', [-7, 27.5, 14], [-5, 28.5, 18], 'enamel', 'tail'),
        C('tail_fan_r', [5, 27.5, 14], [7, 28.5, 18], 'enamel', 'tail'),
        C('rudder', [-0.5, 28.5, 12], [0.5, 32.5, 18], 'brass', 'tail'),
        # ---- the lantern the jar hangs in, under the body. Static: two steel posts at the jar's sides from the collar up
        # into the fuselage, a dark band round the lid with a 2 px brass lip over its edge. Its floor (the restraint):
        # a dark seat plate, the collar's dark base and the brass collar; it is the lift, lowered on cables to load ----
        C('seat', [-6, J - 1, -6], [6, J, 6], 'dsteel', 'restraint'),
        C('collar_base_f', [-8.5, J - 0.9, -8.5], [8.5, J + 0.1, -7.5], 'dsteel', 'restraint'),
        C('collar_base_b', [-8.5, J - 0.9, 7.5], [8.5, J + 0.1, 8.5], 'dsteel', 'restraint'),
        C('collar_base_l', [-8.5, J - 0.9, -7.5], [-7.5, J + 0.1, 7.5], 'dsteel', 'restraint'),
        C('collar_base_r', [7.5, J - 0.9, -7.5], [8.5, J + 0.1, 7.5], 'dsteel', 'restraint'),
        C('collar_f', [-9.5, J + 0.1, -9.5], [9.5, J + 2.1, -7.5], 'brass', 'restraint'),
        C('collar_b', [-9.5, J + 0.1, 7.5], [9.5, J + 2.1, 9.5], 'brass', 'restraint'),
        C('collar_l', [-9.5, J + 0.1, -7.5], [-7.5, J + 2.1, 7.5], 'brass', 'restraint'),
        C('collar_r', [7.5, J + 0.1, -7.5], [9.5, J + 2.1, 7.5], 'brass', 'restraint'),
        C('hinge', [-3, J - 1.5, 9.5], [3, J + 0.5, 11.5], 'dsteel', 'restraint'),
        C('ring_f', [-9, J + 12.5, -9], [9, J + 14.5, -8], 'dsteel', 'torso'),
        C('ring_b', [-9, J + 12.5, 8], [9, J + 14.5, 9], 'dsteel', 'torso'),
        C('ring_l', [-9, J + 12.5, -8], [-8, J + 14.5, 8], 'dsteel', 'torso'),
        C('ring_r', [8, J + 12.5, -8], [9, J + 14.5, 8], 'dsteel', 'torso'),
        C('lip_f', [-8.5, J + 14.5, -8.5], [8.5, J + 15.5, -6.5], 'brass', 'torso'),
        C('lip_b', [-8.5, J + 14.5, 6.5], [8.5, J + 15.5, 8.5], 'brass', 'torso'),
        C('lip_l', [-8.5, J + 14.5, -6.5], [-6.5, J + 15.5, 6.5], 'brass', 'torso'),
        C('lip_r', [6.5, J + 14.5, -6.5], [8.5, J + 15.5, 6.5], 'brass', 'torso'),
        # ---- stand-in jar (never exported) ----
        C('standin_jar_base', [-7, J, -7], [7, J + 1, 7], 'standm', 'standin_jar'),
        C('standin_jar_glass', [-6.5, J + 1, -6.5], [6.5, J + 13, 6.5], 'standj', 'standin_jar'),
        C('standin_jar_lid', [-7, J + 13, -7], [7, J + 14, 7], 'standm', 'standin_jar'),
        C('standin_jar_valve', [-2, J + 14, -2], [2, J + 16, 2], 'standm', 'standin_jar'),
        C('standin_head', [-4, J + 1.5, -4], [4, J + 9.5, 4], 'stand', 'standin_jar'),
    ]
    # four corner posts from the collar up to the belly plate: a cage on every side, nothing across the glass
    pair('post_f', (7.5, 9.5), (J + 2.1, J + 16.1), (-9.5, -7.5), 'steel', 'torso')
    pair('post_b', (7.5, 9.5), (J + 2.1, J + 16.1), (7.5, 9.5), 'steel', 'torso')
    # ---- the wings (modelled spread out flat): a brass leading spar, a thin enamel membrane, dark ribs under it (the
    # darks from below), and at the wingtip three primaries like fingers and a nav lamp (red left, green right) ----
    pair('wing_root', (5.5, 9.5), (29.5, 33.5), (-4.5, 0.5), 'brass', 'torso')
    pair('spar_in', (sx + 0.5, sx + INNER + 0.5), (31, 33), (-4, -2), 'brass', 'wing_{s}')
    pair('membrane_in', (sx + 0.5, sx + INNER + 0.5), (31.5, 32.5), (-2, 8), 'enamel', 'wing_{s}')
    for i, x in enumerate((sx + 3.5, sx + 7.5, sx + 11)):
        pair('rib_in%d' % i, (x - 0.5, x + 0.5), (30.5, 31.5), (-2, 7), 'dsteel', 'wing_{s}')
    xo = sx + INNER
    pair('spar_out', (xo, xo + OUTER), (30.5, 31.5), (-5, -3), 'brass', 'wingtip_{s}')
    pair('membrane_out', (xo, xo + OUTER), (30.5, 31.5), (-3, 4), 'enamel', 'wingtip_{s}')
    pair('rib_out', (xo + 5.5, xo + 6.5), (29.5, 30.5), (-3, 3), 'dsteel', 'wingtip_{s}')
    for i, (z0, z1) in enumerate(((-3, -1), (0, 2), (3, 5))):
        pair('primary_%d' % i, (xo + OUTER, xo + OUTER + 5 - i), (30.5, 31.5), (z0, z1), 'enamel', 'wingtip_{s}')
    pair('nav_lens', (xo + OUTER - 1, xo + OUTER + 1), (31.5, 32.5), (-5, -3), 'lens', 'wingtip_{s}')
    c += [C('nav_lit_l', [-(xo + OUTER + 1), 31.5, -5], [-(xo + OUTER - 1), 32.5, -3], 'lamp_r', 'lamp_nav_l', inflate=0.25),
          C('nav_lit_r', [xo + OUTER - 1, 31.5, -5], [xo + OUTER + 1, 32.5, -3], 'lamp_g', 'lamp_nav_r', inflate=0.25)]
    # ---- arms (small, under the chest; the module on the forearm) ----
    pair('outrigger', (7, 14), (27, 29), (-4, -2), 'dsteel', 'torso')
    pair('shoulder', (14, 18), (26, 30), (-5, -1), 'brass', 'arm_{s}')
    pair('upper', (15, 17), (22, 26), (-4, -2), 'steel', 'arm_{s}')
    pair('elbow', (14.5, 17.5), (20, 23), (-4.5, -1.5), 'dsteel', 'forearm_{s}')
    pair('fore', (15, 17), (17, 20), (-4, -2), 'enamel', 'forearm_{s}')
    pair('cuff', (14.5, 17.5), (16, 17), (-4.5, -1.5), 'rubber', 'forearm_{s}')
    # ---- legs (bird legs, knee back), modelled straight down: brass hip drum, enamel thigh, brass knee, steel shin, dark
    # ankle; on the foot a dark heel toe; on the talon bone two front toes with dark claws that close ----
    hx = HIP_X
    pair('hip_drum', (hx - 2.5, hx + 2.5), (22, 26), (-2.5, 2.5), 'dsteel', 'hips')
    pair('thigh', (hx - 1.5, hx + 1.5), (14, 22), (-1.5, 1.5), 'enamel', 'leg_{s}')
    pair('knee', (hx - 2, hx + 2), (10, 14), (-2, 2), 'brass', 'shin_{s}')
    pair('shin', (hx - 1, hx + 1), (0, 10), (-1, 1), 'steel', 'shin_{s}')
    pair('ankle', (hx - 1.5, hx + 1.5), (-2, 1), (-1.5, 1.5), 'dsteel', 'foot_{s}')
    pair('toe_heel', (hx - 1, hx + 1), (-3, -2), (0, 3), 'dsteel', 'foot_{s}')
    # the talons: three long toes, a pixel apart, each with a dark claw tip; they spread and clutch (talon bone)
    for i, dx in enumerate((-2, -0.5, 1)):
        pair('toe_%d' % i, (hx + dx, hx + dx + 1), (-3, -2), (-7, -1), 'rubber', 'talon_{s}')
        pair('claw_%d' % i, (hx + dx, hx + dx + 1), (-3.5, -1.5), (-8, -7), 'dsteel', 'talon_{s}')
    return c


def scale_standins():
    W.MATERIALS = MATERIALS
    return W.scale_standins()


def locators():
    J = JAR_Y
    xo = SHOULDER[0] + INNER + OUTER
    return [
        ('fx_step_l', [-HIP_X, -3, -2], 'foot_l'), ('fx_step_r', [HIP_X, -3, -2], 'foot_r'),
        ('fx_talon_l', [-HIP_X, -3, -8], 'talon_l'), ('fx_talon_r', [HIP_X, -3, -8], 'talon_r'),
        ('fx_tip_l', [-(xo + 5), 32, 0], 'wingtip_l'), ('fx_tip_r', [xo + 5, 32, 0], 'wingtip_r'),
        ('fx_wing_l', [-(SHOULDER[0] + 8), 31, 3], 'wing_l'), ('fx_wing_r', [SHOULDER[0] + 8, 31, 3], 'wing_r'),
        ('fx_core', [0, 31, -17], 'lamp_core'),
        ('fx_tail', [0, 28, 18.5], 'tail'),
        ('fx_jar_exit', [0, J + 2, 16], 'torso'),           # the jar leaves backward, past the tail
        ('fx_hull', [0, J + 2, 0], 'torso'),
        ('fx_cargo', [0, -6, -3], 'hips'),                  # where a carried creature hangs (below the talons)
        ('fx_wrist_l', [-ARM_X, 16, -3], 'socket_arm_l'), ('fx_wrist_r', [ARM_X, 16, -3], 'socket_arm_r'),
    ]


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
    model = os.path.join(HERE, 'kite.bbmodel')
    if not scale and os.path.exists(model) and '--repack' not in sys.argv:
        offs, regions = W.pin(cs, model, None, *tex)
    else:
        offs, regions = W.pack(cs, *tex)
    png = os.path.join(HERE, 'kite_placeholder.png' if not scale else '_preview_scale.png')
    W.draw(cs, offs, png, *tex)
    if not scale:
        W.uvmap([c for c in cs if not c[0].startswith('standin')], offs, os.path.join(HERE, 'kite_uv.png'), regions)
    out = sys.argv[sys.argv.index('--out') + 1] if '--out' in sys.argv else model
    bbbuild.build(out, 'kite', bs, cs, locs, offs, png, tex)
    sys.stderr.write('rest angles: thigh %.2f shin %.2f foot %.2f\n' % (REST_THIGH, REST_SHIN, REST_FOOT))
    sys.stderr.write('%d bones (%d exported), %d cubes (%d exported), %d locators\n'
                     % (len(bs), len([b for b in bs if not b[0].startswith(('standin', 'h_', 'k_'))]), len(cs),
                        len([c for c in cs if not c[4].startswith(('standin', 'h_', 'k_'))]), len(locs)))


if __name__ == '__main__':
    main()

"""Rover (a Utility Shell on the shared "wheeled" skeleton): geometry, rig numbers, UV layout, placeholder sheet.

Source of truth for the Rover's cubes, bones and locators. Entity space in pixels: origin on the ground at the middle,
y up, the machine faces -Z, its left is -X, 16 px to a block. The helpers (packing, sheet, UV map, coplanar check, UV
pinning) are the Wheelhouse's (art/mecha/wheelhouse/wheelhouse_geo.py), imported.

    python art/shell/rover/rover_geo.py                          # placeholder sheet, UV map, rover.bbmodel
    python art/shell/rover/rover_geo.py --scale --out X.bbmodel  # comparison copy with the family beside it

What it is: the company's tea trolley, promoted. A new hire's first vehicle: a little ivory trolley on four swivelling
castors with a cargo drawer under its shelf (the 9-slot hold), a tea tray on top where the jar rides in a one-pixel
brass ring, held down by a rubber luggage strap with a brass buckle, a push handle at the back with a bell on it, a bicycle
lamp, and a whip aerial with a pennant. The Wheelhouse's poorest cousin: the same enamel and brass, a quarter of the dignity.

Numbers taken from the game:
  hitbox 1.2 x 0.9 blocks (19.2 x 14.4 px)                ModRegistry.ROVER (sized 1.2, 0.9)
  speed attribute 0.38 -> about 0.90 blocks a tick ridden  ShellEntity.rover(); x 1.1 (full sync) x 2.16 (vanilla
                                                           ground factor); the playtest measured 17.6-20.8 blocks in 20 ticks
  30 HP, step height 1.5, a 9-slot cargo hold, ability "sprint" (100 ticks, Speed III, cooldown 200)
  seat today: host neck (0, 0.94, 0.38): the jar at the front-top. This model seats it at (0, 14, 0) px on the tray
No module sockets and no cockpit (a shell).
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MECHA = os.path.join(os.path.dirname(os.path.dirname(HERE)), 'mecha')
sys.path.insert(0, os.path.join(MECHA, 'wheelhouse'))
import wheelhouse_geo as W  # noqa: E402

TEX_W, TEX_H = 256, 256

# rig numbers (the wheeled skeleton's, at the Rover's size)
WHEEL_R = 3.0           # castor radius: the spin divisor is 16 / 3 x 57.3 = 305.6 degrees a block
AXLE_Y = 3.0
TRACK_X = 6.0           # castors under the shelf's corners
Z_FRONT, Z_REAR = -7.0, 7.0
BODY_PIVOT = (0.0, 7.0, 0.0)
JAR_Y = 14.0            # the jar's base, on the tray
JAR_Z = 0.0
CORNERS = {'fl': (-1, Z_FRONT), 'fr': (1, Z_FRONT), 'rl': (-1, Z_REAR), 'rr': (1, Z_REAR)}

MATERIALS = dict(W.MATERIALS)
ORDER = list(W.ORDER)
PARTS = [('castors', ('tyre', 'hub', 'bolt', 'fork', 'stem')), ('chassis', ('rail',)),
         ('shelf', ('shelf', 'bumper', 'reflector', 'drawer', 'knob', 'post')),
         ('tray', ('tray', 'collar', 'seat', 'strap', 'buckle', 'bell', 'lamp', 'lens', 'handle', 'plate')), ('aerial', ('aerial', 'pennant'))]


def bones():
    """The wheeled skeleton's bones (empty groups where the Rover has no such part, so shared clips find them), no
    sockets, plus one Rover extra: the drawer."""
    b = [('root', [0, 0, 0], None, None), ('chassis', [0, AXLE_Y, 0], 'root', None)]
    for c, (s, z) in CORNERS.items():
        p = [s * TRACK_X, AXLE_Y, z]
        b += [('strut_' + c, p, 'chassis', None), ('steer_' + c, p, 'strut_' + c, None), ('wheel_' + c, p, 'steer_' + c, None)]
    b += [
        ('body', list(BODY_PIVOT), 'chassis', None),
        ('jar_anchor', [0, JAR_Y, JAR_Z], 'body', None),
        ('restraint', [-8, JAR_Y + 1, -3], 'body', None),     # the luggage strap: rot z + flips it over to the left
        ('helm', [7, 16, 9], 'body', None),                   # the bell on the push handle (wobbles when rung)
        ('flag', [-9.5, 13, -9.5], 'body', None),                  # the whip aerial and its pennant
        ('sensor', [0, 12, -9], 'body', None),                # (empty)
        ('arm_l', [0, 12, -9], 'sensor', None),               # (empty)
        ('forearm_l', [0, 12, -9], 'arm_l', None),            # (empty)
        ('buffer', [0, 9, -9.5], 'body', None),               # the rubber bumper
        ('exhaust_l', [-3, 7, 9], 'body', None), ('exhaust_r', [3, 7, 9], 'body', None),     # (empty: it is electric)
        ('vent_l', [-7, 9, 0], 'body', None), ('vent_r', [7, 9, 0], 'body', None),           # (empty)
        ('lamp_head_fl', [-6, 6, -7], 'steer_fl', None), ('lamp_head_fr', [6, 6, -7], 'steer_fr', None),   # (empty)
        ('lamp_tail', [0, 9, 9.5], 'body', None),
        ('lamp_brake', [0, 9, 9.5], 'body', None),            # (empty)
        ('lamp_core', [0, 15.5, -12], 'body', None),           # the bicycle lamp
        ('drawer', [0, 9, 9], 'body', None),                  # Rover extra: the cargo drawer, + z slides it out
        ('standin_jar', [0, JAR_Y, JAR_Z], 'jar_anchor', None),
    ]
    return b


C = W.C


def cubes():
    J, JZ = JAR_Y, JAR_Z
    c = []

    def pair(name, xs, ys, zs, mat, parent, **kw):
        for side, sg in (('l', -1), ('r', 1)):
            x0, x1 = (xs[0], xs[1]) if sg > 0 else (-xs[1], -xs[0])
            c.append(C('%s_%s' % (name, side), [x0, ys[0], zs[0]], [x1, ys[1], zs[1]], mat, parent.format(s=side), **kw))

    pair('rail', (2, 4), (6, 8), (-9, 9), 'dsteel', 'chassis')
    # ---- castors: a rubber tyre (crossed boxes, on the steer bone: it swivels but never spins), a brass hub with one
    # dark bolt off-centre (on the wheel bone: it spins, and a single bolt shows the turning at any speed), a dark fork
    # that swivels, a steel stem in the shelf's socket ----
    for cn, (s, z) in CORNERS.items():
        def w(name, xs, ys, zs, mat, parent):
            x0, x1 = (xs[0], xs[1]) if s > 0 else (-xs[1], -xs[0])
            c.append(C('%s_%s' % (name, cn), [x0, ys[0], z + zs[0]], [x1, ys[1], z + zs[1]], mat, parent + cn))
        w('tyre_a', (5, 7), (0, 6), (-2, 2), 'rubber', 'steer_')
        w('tyre_b', (5.5, 6.5), (1, 5), (-3, 3), 'rubber', 'steer_')
        w('hub', (7, 8), (2, 4), (-1, 1), 'brass', 'wheel_')
        w('bolt', (8, 9), (3, 4), (0, 1), 'dsteel', 'wheel_')
        w('fork', (4.5, 7.5), (6, 7), (-1, 1), 'dsteel', 'steer_')
        w('stem', (5.5, 6.5), (7, 8), (-0.5, 0.5), 'steel', 'strut_')
    c += [
        # ---- the shelf: the cargo drawer's housing; a rubber bumper on its nose; amber reflectors on its back ----
        C('shelf', [-7, 8, -9], [7, 11, 9], 'enamel', 'body'),
        C('shelf_lip', [-7.5, 10.5, -9.5], [7.5, 11.5, 9.5], 'enamel', 'body'),
        C('bumper', [-6, 8, -10], [6, 10, -9], 'rubber', 'buffer'),
        C('drawer_front', [-5, 8.5, 8.5], [5, 10.5, 9.5], 'enamel', 'drawer'),
        C('drawer_box', [-5, 8.5, 1.5], [5, 10.5, 8.5], 'dsteel', 'drawer'),
        C('knob', [-1, 9, 9.5], [1, 10, 10.5], 'brass', 'drawer'),
        C('reflector_lens_l', [-7, 8.5, 9], [-6, 10.5, 10], 'lens', 'body'),
        C('reflector_lens_r', [6, 8.5, 9], [7, 10.5, 10], 'lens', 'body'),
        C('reflector_lit_l', [-7, 8.5, 9], [-6, 10.5, 10], 'lamp_a', 'lamp_tail', inflate=0.25),
        C('reflector_lit_r', [6, 8.5, 9], [7, 10.5, 10], 'lamp_a', 'lamp_tail', inflate=0.25),
        # ---- the tray on four corner posts, 1 px of air under it; the jar's seat and a 1 px brass ring for a collar --
        C('tray', [-9, 13, -12], [9, 14, 8], 'enamel', 'body'),
        C('seat', [-6, J - 0.5, JZ - 6], [6, J + 0.5, JZ + 6], 'dsteel', 'body'),
        C('collar_f', [-8.5, J, JZ - 8.5], [8.5, J + 2, JZ - 7.5], 'brass', 'body'),
        C('collar_b', [-8.5, J, JZ + 7.5], [8.5, J + 2, JZ + 8.5], 'brass', 'body'),
        C('collar_l', [-8.5, J, JZ - 7.5], [-7.5, J + 2, JZ + 7.5], 'brass', 'body'),
        C('collar_r', [7.5, J, JZ - 7.5], [8.5, J + 2, JZ + 7.5], 'brass', 'body'),
        # ---- the luggage strap (the restraint): up the left side, over the lid in front of the valve, down the right,
        # a brass buckle on the right; it flips over to the left on its hinge at the left foot ----
        C('strap_l', [-8.5, J + 2, JZ - 4], [-7.5, J + 15, JZ - 2], 'rubber', 'restraint'),
        C('strap_r', [7.5, J + 2, JZ - 4], [8.5, J + 15, JZ - 2], 'rubber', 'restraint'),
        C('strap_top', [-8.5, J + 15, JZ - 4], [8.5, J + 16, JZ - 2], 'rubber', 'restraint'),
        C('buckle', [8.5, J + 6, JZ - 4.5], [9.5, J + 9, JZ - 1.5], 'brass', 'restraint'),
        # ---- at the back, facing the pilot's camera: a tea trolley's push handle (a steel bar on two uprights, at the collar's
        # height so it never crosses the glass), the bell on its right end, a brass plate under it ----
        C('handle_bar', [-5.5, 15, 9], [5.5, 16, 10], 'steel', 'body'),
        C('handle_post_l', [-5.5, 11, 9], [-4.5, 15, 10], 'steel', 'body'),
        C('handle_post_r', [4.5, 11, 9], [5.5, 15, 10], 'steel', 'body'),
        C('plate', [-3, 10.5, 9.5], [3, 11.5, 10.5], 'brass', 'body'),
        C('bell', [5.5, 16, 7.5], [8.5, 18, 10.5], 'brass', 'helm'),
        # the bicycle lamp (front middle)
        C('lamp_housing', [-1.5, 14, -12], [1.5, 17, -10], 'brass', 'body'),
        C('lamp_lens', [-1, 14.5, -12.5], [1, 16.5, -11.5], 'lens', 'body'),
        C('lamp_lit', [-1, 14.5, -12.5], [1, 16.5, -11.5], 'lamp_w', 'lamp_core', inflate=0.25),
        # ---- the whip aerial on a bracket outboard of the tray's front-left corner: from behind it stands beside the
        # jar, never over the head; the pennant flies 3 px above the lid ----
        C('aerial_base', [-10.5, 12, -10.5], [-8.5, 13, -8.5], 'dsteel', 'body'),
        C('aerial', [-10, 13, -10], [-9, 33, -9], 'steel', 'flag'),
        C('pennant', [-14, 30, -10], [-10, 33, -9], 'yellow', 'flag'),
        # ---- stand-in jar (never exported) ----
        C('standin_jar_base', [-7, J, JZ - 7], [7, J + 1, JZ + 7], 'standm', 'standin_jar'),
        C('standin_jar_glass', [-6.5, J + 1, JZ - 6.5], [6.5, J + 13, JZ + 6.5], 'standj', 'standin_jar'),
        C('standin_jar_lid', [-7, J + 13, JZ - 7], [7, J + 14, JZ + 7], 'standm', 'standin_jar'),
        C('standin_jar_valve', [-2, J + 14, JZ - 2], [2, J + 16, JZ + 2], 'standm', 'standin_jar'),
        C('standin_head', [-4, J + 1.5, JZ - 4], [4, J + 9.5, JZ + 4], 'stand', 'standin_jar'),
    ]
    pair('post_f', (6, 7), (11, 13), (-8.5, -7.5), 'steel', 'body')
    pair('post_b', (6, 7), (11, 13), (7.5, 8.5), 'steel', 'body')
    return c


def locators():
    out = []
    for cn, (s, z) in CORNERS.items():
        out.append(('fx_contact_' + cn, [s * TRACK_X, 0, z], 'strut_' + cn))
    out += [('fx_head', [0, 15.5, -13], 'lamp_core'), ('fx_tail_l', [-6.5, 9.5, 10.5], 'lamp_tail'),
            ('fx_tail_r', [6.5, 9.5, 10.5], 'lamp_tail'), ('fx_bumper', [0, 9, -10.5], 'buffer'),
            ('fx_bell', [7, 17, 9], 'helm'), ('fx_pennant', [-12, 31.5, -9.5], 'flag'),
            ('fx_drawer', [0, 9.5, 10.5], 'drawer'), ('fx_jar_exit', [0, 0, 18], 'body'), ('fx_hull', [0, 16, 0], 'body')]
    return out


def main():
    sys.path.insert(0, MECHA)
    import bbbuild
    W.MATERIALS, W.ORDER, W.PARTS = MATERIALS, ORDER, PARTS
    scale = '--scale' in sys.argv
    bs, cs, locs = bones(), cubes(), locators()
    if scale:
        b2, c2 = W.scale_standins()
        bs, cs = bs + b2, cs + c2
    W.check(cs)
    if not scale:
        cp = W.coplanar([c for c in cs if not c[0].startswith('standin') and '_lit' not in c[0]], W.rest_frame_of(bs))
        sys.stderr.write('coplanar faces: %s\n' % (cp or 'none'))
    tex = (TEX_W, TEX_H) if not scale else (512, 512)
    model = os.path.join(HERE, 'rover.bbmodel')
    if not scale and os.path.exists(model) and '--repack' not in sys.argv:
        offs, regions = W.pin(cs, model, None, *tex)
    else:
        offs, regions = W.pack(cs, *tex)
    png = os.path.join(HERE, 'rover_placeholder.png' if not scale else '_review_scale.png')
    W.draw(cs, offs, png, *tex)
    if not scale:
        W.uvmap([c for c in cs if not c[0].startswith('standin')], offs, os.path.join(HERE, 'rover_uv.png'), regions)
    out = sys.argv[sys.argv.index('--out') + 1] if '--out' in sys.argv else model
    bbbuild.build(out, 'rover', bs, cs, locs, offs, png, tex)
    sys.stderr.write('%d bones (%d exported), %d cubes (%d exported), %d locators\n'
                     % (len(bs), len([b for b in bs if not b[0].startswith(('standin', 'h_', 'k_'))]), len(cs),
                        len([c for c in cs if not c[4].startswith(('standin', 'h_', 'k_'))]), len(locs)))


if __name__ == '__main__':
    main()

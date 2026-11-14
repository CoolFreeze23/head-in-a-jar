"""Spider Walker (a Utility Shell on the shared "multi-leg" skeleton): geometry, rig numbers, UV layout, placeholder
sheet.

Source of truth for the Spider Walker's cubes, bones and locators. Entity space in pixels: origin on the ground under
the middle, y up, the machine faces -Z, its left is -X, 16 px to a block. The helpers (packing, sheet, UV map, coplanar
check, UV pinning) are the Wheelhouse's (art/mecha/wheelhouse/wheelhouse_geo.py), imported.

    python art/shell/spider_walker/spider_walker_geo.py                          # placeholder, UV map, .bbmodel
    python art/shell/spider_walker/spider_walker_geo.py --scale --out X.bbmodel  # preview copy: the Skitter, the
                                                                                 # Rover and a player beside it

What it is: the tea trolley's biscuit tin, on eight bent-wire legs. A round tin in the company's ivory enamel (the
"Assorted" label is painted) with a brass lid rim, eight thin legs (brass wire thighs, ivory knee caps, steel shins, dark
points), the jar on the lid with its foot gripped at the four corners by hazard-yellow claws, as a spider holds its egg, a clockwork key in its back that turns
as it walks (the motor is electric; the key is there for reassurance), four lamp eyes in a row on a visor, and a glue
nozzle under its chin that spits the web.

Eight legs, not four: the present model (ShellRenderer.spiderWalker) has eight, the renderer swings leg_0..leg_7, the
name says spider, the ability is a cobweb; and eight thin legs round a low tin is the one silhouette the four-legged,
tall, enamel Skitter can never be mistaken for at 20 blocks. The shared four chains (hip/leg/shin _fl _fr _rl _rr) are
the first and fourth pairs; the second and third pairs are extra chains of the same build (_l2 _r2 _l3 _r3).

Numbers taken from the game:
  hitbox 1.4 x 0.9 blocks (22.4 x 14.4 px)                ModRegistry.SPIDER_WALKER (sized 1.4, 0.9)
  ridden about 0.34 blocks a tick (release branch)        ShellEntity.spiderWalker() 0.28 x 1.1 x RIDDEN_SCALE 0.5
  30 HP, step 1.0, no fall damage, climbs (onClimbable while against a wall), ability "web" (range 16, cooldown 60)
  neck (0, 0.81, 0): the jar's base at 12.96 px. This model seats it at (0, 13, 0)
No module sockets and no cockpit (a shell).
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MECHA = os.path.join(os.path.dirname(os.path.dirname(HERE)), 'mecha')
sys.path.insert(0, os.path.join(MECHA, 'wheelhouse'))
import wheelhouse_geo as W  # noqa: E402

TEX_W, TEX_H = 256, 256

# ---------------------------------------------------------------------------------------------------------------------
# Rig numbers (shared with spider_walker_anim.py)
# ---------------------------------------------------------------------------------------------------------------------
BODY_Y = 8.0            # the body's pivot and the hips' height
FEMUR = 10.0            # hip to knee (steep: the knees stand above the lid's rim round the jar)
TIBIA = 16.0            # knee to the point (long, coming down steeply)
JAR_Y = 13.0            # the jar's base, on the lid
FOOT_LIFT = 0.3         # the point's contact sits this far above the axis end
LEGS = ['fl', 'l2', 'l3', 'rl', 'fr', 'r2', 'r3', 'rr']      # numbered from the front; fl/fr/rl/rr are the shared chains
HIPS = {'fl': (-7.5, -7.5), 'l2': (-9.5, -2.5), 'l3': (-9.5, 2.5), 'rl': (-7.5, 7.5)}
FEET = {'fl': (-14.5, -15.5), 'l2': (-18.0, -5.5), 'l3': (-18.0, 5.5), 'rl': (-14.5, 15.5)}
for _l, _r in (('fl', 'fr'), ('l2', 'r2'), ('l3', 'r3'), ('rl', 'rr')):
    HIPS[_r] = (-HIPS[_l][0], HIPS[_l][1])
    FEET[_r] = (-FEET[_l][0], FEET[_l][1])
SIDE = {c: (-1 if c in ('fl', 'l2', 'l3', 'rl') else 1) for c in LEGS}


def leg_ik(r, dy):
    """Two-bone leg in its own vertical plane, knee UP. r = horizontal distance hip to point (outward), dy = point
    height minus hip height. Returns (femur, tibia) absolute angles above horizontal, degrees."""
    d = math.hypot(r, dy)
    dc = min(max(d, abs(FEMUR - TIBIA) + 0.01), FEMUR + TIBIA - 0.001)
    phi = math.atan2(dy, r)
    alpha = math.acos((FEMUR * FEMUR + dc * dc - TIBIA * TIBIA) / (2 * FEMUR * dc))
    a1 = phi + alpha
    kx, ky = FEMUR * math.cos(a1), FEMUR * math.sin(a1)
    ex, ey = r * dc / d, dy * dc / d
    a2 = math.atan2(ey - ky, ex - kx)
    return math.degrees(a1), math.degrees(a2)


REST_YAW, REST_FEMUR, REST_TIBIA = {}, {}, {}
for _c in LEGS:
    _dx, _dz = FEET[_c][0] - HIPS[_c][0], FEET[_c][1] - HIPS[_c][1]
    REST_YAW[_c] = round(math.degrees(math.atan2(-_dx, -_dz)), 2)
    _a1, _a2 = leg_ik(math.hypot(_dx, _dz), -BODY_Y + FOOT_LIFT)
    REST_FEMUR[_c] = round(_a1, 2)
    REST_TIBIA[_c] = round(_a2 - _a1, 2)

MATERIALS = dict(W.MATERIALS)
ORDER = list(W.ORDER)
PARTS = [('legs', ('hip', 'femur', 'knee', 'tibia', 'point')), ('tin', ('tin', 'lid', 'belly')),
         ('setting', ('seat', 'collar', 'claw')), ('key', ('key',)),
         ('face', ('visor', 'eye', 'nozzle', 'gland')), ('tail', ('tail',))]


def bones():
    """The multi-leg skeleton's bones (empty groups where the Spider Walker has no such part, so shared clips find
    them), no sockets, plus the second and third leg pairs."""
    J = JAR_Y
    b = [
        ('root', [0, 0, 0], None, None),
        ('body', [0, BODY_Y, 0], 'root', None),
        ('jar_anchor', [0, J, 0], 'body', None),
        ('restraint', [0, J, 0], 'body', None),                 # the claw setting
        ('clamp_l', [-9.5, J - 1, 0], 'restraint', None),      # the two left claws: rot z + swings them open
        ('clamp_r', [9.5, J - 1, 0], 'restraint', None),       # the two right claws: rot z - swings them open
        ('sensor', [0, 8.5, -9], 'body', None),                 # the visor with its four eyes: the look
        ('lamp_core', [0, 8.5, -10], 'sensor', None),
        ('arm_l', [0, 6, -9], 'body', None),                    # (empty)
        ('forearm_l', [0, 6, -9], 'arm_l', None),               # (empty)
        ('grip_l', [0, 6, -9], 'forearm_l', None),              # (empty)
        ('reel', [0, 8, 9], 'body', None),                    # the clockwork key: rot z turns it (- as it walks)
        ('launcher', [0, 5.5, -9], 'body', None),               # the glue nozzle: aimed by code, recoil + z
        ('lamp_tail', [0, 9.25, 9], 'body', None),
        ('vent_l', [-9, 8, 0], 'body', None), ('vent_r', [9, 8, 0], 'body', None),     # (empty)
    ]
    for c in LEGS:
        px, pz = HIPS[c]
        p = [px, BODY_Y, pz]
        b += [('hip_' + c, p, 'body', [0, REST_YAW[c], 0]),
              ('leg_' + c, p, 'hip_' + c, [REST_FEMUR[c], 0, 0]),
              ('shin_' + c, [px, BODY_Y, pz - FEMUR], 'leg_' + c, [REST_TIBIA[c], 0, 0])]
    b += [('standin_jar', [0, J, 0], 'jar_anchor', None)]
    return b


C = W.C


def cubes():
    J = JAR_Y
    c = []
    # ---- the tin: four stacked boxes per layer (an octagon that reads round), ivory enamel; a brass lid rim of the
    # same four, 1 px proud; a steel belly plate under it ----
    c += [
        C('tin_a', [-9, 4, -4], [9, 11, 4], 'enamel', 'body'),
        C('tin_b', [-4, 5, -9], [4, 10, 9], 'enamel', 'body'),
        C('tin_c', [-8, 4.5, -6], [8, 10.5, 6], 'enamel', 'body'),
        C('tin_d', [-6, 3.75, -8], [6, 10.75, 8], 'enamel', 'body'),
        C('lid_a', [-9.5, 11, -4.5], [9.5, 12, 4.5], 'brass', 'body'),
        C('lid_b', [-4.5, 10.5, -9.5], [4.5, 11.5, 9.5], 'brass', 'body'),
        C('lid_c', [-8.5, 10.25, -6.5], [8.5, 11.25, 6.5], 'brass', 'body'),
        C('lid_d', [-6.5, 10.75, -8.5], [6.5, 11.75, 8.5], 'brass', 'body'),
        C('belly', [-5, 2.75, -5], [5, 3.75, 5], 'steel', 'body'),
        # ---- the setting: a dark seat on the lid, a dark collar round the jar's foot and four brass claws gripping
        # the foot's corners, as a spider holds its egg (never over the glass); the left pair and the right pair
        # swing open on hinges at the collar ----
        C('seat', [-6, J - 1, -6], [6, J, 6], 'dsteel', 'body'),
        C('collar_f', [-8.5, J - 1, -8.5], [8.5, J + 1, -7.5], 'dsteel', 'restraint'),
        C('collar_b', [-8.5, J - 1, 7.5], [8.5, J + 1, 8.5], 'dsteel', 'restraint'),
        C('collar_l', [-8.5, J - 1, -7.5], [-7.5, J + 1, 7.5], 'dsteel', 'restraint'),
        C('collar_r', [7.5, J - 1, -7.5], [8.5, J + 1, 7.5], 'dsteel', 'restraint'),
    ]
    for side, sx in (('l', -1), ('r', 1)):
        for end, sz in (('f', -1), ('b', 1)):
            # a claw at each corner of the jar's foot: a brass knuckle standing outside the collar's corner and a
            # plate bent in over the foot's corner (inside the foot's rim, outside the glass)
            x0, x1 = sorted((sx * 8.5, sx * 10.5))
            z0, z1 = sorted((sz * 8.5, sz * 10.5))
            c.append(C('claw_%s%s' % (end, side), [x0, J - 1, z0], [x1, J + 5, z1], 'yellow', 'clamp_' + side))
            tx0, tx1 = sorted((sx * 6.5, sx * 9.5))
            tz0, tz1 = sorted((sz * 6.5, sz * 9.5))
            c.append(C('claw_tip_%s%s' % (end, side), [tx0, J + 1, tz0], [tx1, J + 2, tz1], 'yellow', 'clamp_' + side))
            c.append(C('claw_hook_%s%s' % (end, side), [tx0, J + 3.5, tz0], [tx1, J + 4.5, tz1], 'yellow', 'clamp_' + side))
    c += [
        # ---- the clockwork key in its back: a dark stem, a brass bow of two lobes (turns about z, facing the rider's camera)
        C('key_stem', [-1, 7, 9], [1, 9, 13], 'steel', 'reel'),
        C('key_bow_l', [-5, 6, 12.5], [-1, 10, 13.5], 'brass', 'reel'),
        C('key_bow_r', [1, 6, 12.5], [5, 10, 13.5], 'brass', 'reel'),
        # two amber reflectors on the tin's back, above the key
        C('tail_lens_l', [-4.5, 8.75, 8.5], [-2.5, 9.75, 9.5], 'lens', 'body'),
        C('tail_lens_r', [2.5, 8.75, 8.5], [4.5, 9.75, 9.5], 'lens', 'body'),
        C('tail_lit_l', [-4.5, 8.75, 8.5], [-2.5, 9.75, 9.5], 'lamp_a', 'lamp_tail', inflate=0.25),
        C('tail_lit_r', [2.5, 8.75, 8.5], [4.5, 9.75, 9.5], 'lamp_a', 'lamp_tail', inflate=0.25),
        # ---- the face: a dark visor across the front with four lamp eyes in a row (two big, two small) ----
        C('visor', [-4.5, 7, -10], [4.5, 10, -9], 'dsteel', 'sensor'),
        C('eye_lens_a', [-3.5, 8, -10.5], [-2.5, 9, -9.5], 'lens', 'sensor'),
        C('eye_lens_b', [-2, 7.5, -10.5], [0, 9.5, -9.5], 'lens', 'sensor'),
        C('eye_lens_c', [0.5, 7.5, -10.5], [2.5, 9.5, -9.5], 'lens', 'sensor'),
        C('eye_lens_d', [3, 8, -10.5], [4, 9, -9.5], 'lens', 'sensor'),
        C('eye_lit_a', [-3.5, 8, -10.5], [-2.5, 9, -9.5], 'lamp_g', 'lamp_core', inflate=0.25),
        C('eye_lit_b', [-2, 7.5, -10.5], [0, 9.5, -9.5], 'lamp_g', 'lamp_core', inflate=0.25),
        C('eye_lit_c', [0.5, 7.5, -10.5], [2.5, 9.5, -9.5], 'lamp_g', 'lamp_core', inflate=0.25),
        C('eye_lit_d', [3, 8, -10.5], [4, 9, -9.5], 'lamp_g', 'lamp_core', inflate=0.25),
        # ---- the glue nozzle under the chin: a dark gland, a brass nozzle ----
        C('gland', [-1.5, 4.5, -10], [1.5, 6.5, -8], 'dsteel', 'launcher'),
        C('nozzle', [-0.5, 5, -11.5], [0.5, 6, -9.5], 'brass', 'launcher'),
        # ---- stand-in jar (never exported) ----
        C('standin_jar_base', [-7, J, -7], [7, J + 1, 7], 'standm', 'standin_jar'),
        C('standin_jar_glass', [-6.5, J + 1, -6.5], [6.5, J + 13, 6.5], 'standj', 'standin_jar'),
        C('standin_jar_lid', [-7, J + 13, -7], [7, J + 14, 7], 'standm', 'standin_jar'),
        C('standin_jar_valve', [-2, J + 14, -2], [2, J + 16, 2], 'standm', 'standin_jar'),
        C('standin_head', [-4, J + 1.5, -4], [4, J + 9.5, 4], 'stand', 'standin_jar'),
    ]
    # ---- eight legs, each modelled straight along -Z from its hip (the hip's rest yaw turns it outward, the femur
    # and shin rest pitches fold it, knee up): a dark hip boss, a brass wire thigh, an ivory knee cap (the light ring
    # the knees make round the jar's base), a steel shin and a dark point ----
    Y = BODY_Y
    for cn in LEGS:
        px, pz = HIPS[cn]
        kz = pz - FEMUR

        def leg(name, xs, ys, zs, mat, parent):
            c.append(C('%s_%s' % (name, cn), [px + xs[0], Y + ys[0], zs[0]], [px + xs[1], Y + ys[1], zs[1]], mat,
                       parent + cn))
        leg('hip', (-1.5, 1.5), (-1.5, 1.5), (pz - 1.5, pz + 1.5), 'dsteel', 'hip_')
        leg('femur', (-1, 1), (-1, 1), (kz + 1, pz - 1), 'brass', 'leg_')
        leg('knee', (-1.5, 1.5), (-1, 1), (kz - 1, kz + 1), 'enamel', 'shin_')
        leg('tibia', (-1, 1), (-1, 1), (kz - TIBIA + 2, kz - 1), 'steel', 'shin_')
        leg('point', (-0.5, 0.5), (-0.5, 0.5), (kz - TIBIA, kz - TIBIA + 2), 'rubber', 'shin_')
    return c


def locators():
    out = []
    for cn in LEGS:
        px, pz = HIPS[cn]
        out.append(('fx_foot_' + cn, [px, BODY_Y, pz - FEMUR - TIBIA], 'shin_' + cn))
    out += [('fx_eye', [0, 8.5, -11], 'lamp_core'), ('fx_nozzle', [0, 5.5, -12], 'launcher'),
            ('fx_key', [0, 8, 14], 'reel'), ('fx_tail_l', [-3.5, 9.25, 10], 'lamp_tail'),
            ('fx_tail_r', [3.5, 9.25, 10], 'lamp_tail'), ('fx_jar_exit', [0, 0, 20], 'body'), ('fx_hull', [0, 14, 0], 'body'),
            ('fx_belly', [0, 2.75, 0], 'body')]
    return out


def scale_standins():
    """Beside it, for scale and for telling them apart (preview copies only): the Skitter and the Rover (each its own
    bones and cubes, its stand-in jar kept) and a player."""
    sys.path.insert(0, os.path.join(MECHA, 'skitter'))
    sys.path.insert(0, os.path.join(os.path.dirname(HERE), 'rover'))
    import skitter_geo as SK
    import rover_geo as RV
    bs, cs = [], []
    for tag, mod, dx in (('h_', SK, 46.0), ('k_', RV, -34.0)):
        for name, piv, parent, rot in mod.bones():
            bs.append((tag + name, [piv[0] + dx, piv[1], piv[2]], (tag + parent) if parent else None, rot))
        for name, f, t, mat, parent, kw in mod.cubes():
            if name.endswith('_lit'):
                continue
            if mat not in MATERIALS:
                mat = 'enamel'
            cs.append((tag + name, [f[0] + dx, f[1], f[2]], [t[0] + dx, t[1], t[2]], mat, tag + parent, kw))
    px = -58
    bs += [('standin_player', [px, 0, 0], None, None)]
    cs += [
        C('sp_legs', [px - 4, 0, -2], [px + 4, 12, 2], 'player', 'standin_player'),
        C('sp_body', [px - 4, 12, -2], [px + 4, 24, 2], 'player', 'standin_player'),
        C('sp_head', [px - 4, 24, -4], [px + 4, 32, 4], 'stand', 'standin_player'),
    ]
    return bs, cs


def main():
    sys.path.insert(0, MECHA)
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
    model = os.path.join(HERE, 'spider_walker.bbmodel')
    if not scale and os.path.exists(model) and '--repack' not in sys.argv:
        offs, regions = W.pin(cs, model, None, *tex)
    else:
        offs, regions = W.pack(cs, *tex)
    png = os.path.join(HERE, 'spider_walker_placeholder.png' if not scale else '_preview_scale.png')
    W.draw(cs, offs, png, *tex)
    if not scale:
        W.uvmap([c for c in cs if not c[0].startswith('standin')], offs, os.path.join(HERE, 'spider_walker_uv.png'),
                regions)
    out = sys.argv[sys.argv.index('--out') + 1] if '--out' in sys.argv else model
    bbbuild.build(out, 'spider_walker', bs, cs, locs, offs, png, tex)
    sys.stderr.write('rest: %s\n' % ', '.join('%s yaw %.1f femur %.1f tibia %.1f' % (c, REST_YAW[c], REST_FEMUR[c],
                                                                                    REST_TIBIA[c]) for c in LEGS[:4]))
    sys.stderr.write('%d bones (%d exported), %d cubes (%d exported), %d locators\n'
                     % (len(bs), len([b for b in bs if not b[0].startswith(('standin', 'h_', 'k_'))]), len(cs),
                        len([c for c in cs if not c[4].startswith(('standin', 'h_', 'k_'))]), len(locs)))


if __name__ == '__main__':
    main()

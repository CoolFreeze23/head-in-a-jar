"""Assembly Gantry (design B, the Showroom Turntable and the Anglepoise Arm): geometry.

    python art/machines/gantry/gantry_geo.py     # writes gantry.bbmodel and gantry_placeholder.png
    python art/machines/gantry/gantry_anim.py    # then the clips and the turntable plan

A one-block enamel pedestal with louvred sides on a dark plinth; on top, behind a 2 px dark gap round a hub, a striped
turntable whose rubber deck stands at y 20 (the mecha stands on it); at the back-right corner a big spring-balanced
Anglepoise arm (swivel, upper arm 18, forearm 25, a brass head with four retracting tools and a lamp lens); a copper
cable reel on the left side; the lamp on the front; a brass tray for the modules on the front edge; a brass parking
cradle on the back-left corner where the head rests face-up.

Why the deck is at 20 and not 16: a Hopper on the pad sweeps a circle of radius 20,
a Bulwark 26, and both stand on the pad from their feet up; any arm rooted on a 16 px block is inside that circle at
the feet. With the deck 4 px up, the arm's hinge (y 18) and its stood-off upper arm (y 16.5-19.5) pass under the
mecha's feet, and the forearm stands outside the circle, so the turntable can turn the mecha without the arm in it.

Coordinates: pixels, origin at the block's bottom centre, y up, the front faces -Z, +X is the machine's right.
The arm is built straight up in the plane x = 6.5 (its "in" towards -Z, "out" towards +Z) and posed by rest rotations:
the swivel's yaw (90: the arm's plane along the back edge, "in" = -X) and each joint's pitch about X.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
import kit  # noqa: E402

PATH = os.path.join(HERE, 'gantry.bbmodel')

# ---- the arm's numbers (the anim script's IK uses the same) ----------------------------------------------------------
AXIS = (6.5, 6.5)          # the swivel's vertical axis (x, z): the back-right corner
SWIVEL_Y = 15.0            # swivel pivot height (the drum's bottom)
HINGE_Y = 18.0             # the shoulder pin (upper arm pivot)
LU = 18.0                  # shoulder to elbow
LF = 25.0                  # elbow to head pivot
TOOL = 6.5                 # head pivot to the extended tool's tip, along the head's face (local -Z)
DECK = 20.0                # the turntable's deck (the mecha's feet)
REST_YAW = 90.0            # swivel rest yaw: the arm's plane along the back edge, "in" = -X
LAT = -3.0                 # the forearm and head run 3 px beside the upper arm (local -X), like an Anglepoise's
                           # paired rods, so the folded arm never passes through itself
CRADLE = (-6.5, 9.5)       # the head's parking place (x, z); the head pivot rests at y 19.5
HEAD_REST_Y = 19.5


def ik(q, h, elbow_out=True):
    """Planar two-link IK in the arm's plane. q: distance from the shoulder axis towards IN, h: height above the
    shoulder pin. Returns (a, b): the upper arm's and the forearm's absolute angles from vertical, positive OUT
    (outliner rotX convention: +X tips +Y towards +Z, and +Z is OUT)."""
    d = math.hypot(q, h)
    d = min(d, LU + LF - 1e-6)
    c = (LU * LU + d * d - LF * LF) / (2 * LU * d)
    c = max(-1.0, min(1.0, c))
    alpha = math.degrees(math.acos(c))
    # direction to target, measured from vertical towards IN
    phi_in = math.degrees(math.atan2(q, h))
    a_in = phi_in - alpha if elbow_out else phi_in + alpha     # the upper arm, towards IN
    ex, ey = LU * math.sin(math.radians(a_in)), LU * math.cos(math.radians(a_in))
    b_in = math.degrees(math.atan2(q - ex, h - ey))
    return -a_in, -b_in


def norm(a):
    return (a + 180.0) % 360.0 - 180.0


def rest_pose():
    """The put-away pose: the upper arm leaning back (away from the head) about 22 degrees,
    the forearm folded back down at about 30 degrees to it so the elbow stands proud with the spring in the gap, the
    head face-up in the brass cradle on the back-left corner. Plane along the back edge (yaw 90)."""
    q = AXIS[0] - CRADLE[0]                # in = -X at yaw 90
    h = HEAD_REST_Y - HINGE_Y
    a, b = ik(q, h)
    face_up = 90.0                          # absolute head angle (outliner X) with the face (local -Z) pointing up
    return {'swivel': REST_YAW, 'upper': a, 'fore': norm(b - a), 'head': norm(face_up - b)}


def model():
    m = kit.Model('gantry', budget=10)

    # ---- plinth and feet (the plinth 1 px inside both crossed boxes of the body above it) ------------------------
    kit.plinth(m, -5, 5, -5, 5)

    # ---- the pedestal: louvred on all four sides, built from pieces round a dark core (a body with
    # holes). Outline: crossed 12x14 / 14x12 (x -6..6 z -7..7, x -7..7 z -6..6) with the corners notched 1 px deeper.
    # Every louvre: an opening y 5-12 cut 2 px deep to the dark core, one enamel slat y 8-9 set 1 px back: two dark
    # bars 3 px tall each.
    m.box('core', [-5, 4, -5], [5, 14, 5], 'dsteel', part='body')   # from 4: clear of the plinth's sides
    Y0, Y1, OY0, OY1, SL = 3, 14, 5, 12, (8, 9)

    def slab(face, u_lo, u_hi, o_lo, o_hi, plane_out, plane_in):
        """One side's slab (u along the face, the face's outer plane, its inner plane) with an opening o_lo..o_hi."""
        def box(name, u0, u1, y0, y1, d0, d1, mat='enamel'):
            # d measured inward from the outer plane, 0..2
            if face in ('north', 'south'):
                s = -1 if face == 'north' else 1
                z0, z1 = plane_out - s * d0, plane_out - s * d1
                m.box(name, [u0, y0, min(z0, z1)], [u1, y1, max(z0, z1)], mat, part='body')
            else:
                s = -1 if face == 'west' else 1
                x0, x1 = plane_out - s * d0, plane_out - s * d1
                m.box(name, [min(x0, x1), y0, u0], [max(x0, x1), y1, u1], mat, part='body')
        t = face[0]
        box('slab_%s_bot' % t, u_lo, u_hi, Y0, OY0, 0, 2)
        box('slab_%s_top' % t, u_lo, u_hi, OY1, Y1, 0, 2)
        box('slab_%s_jl' % t, u_lo, o_lo, OY0, OY1, 0, 2)
        box('slab_%s_jr' % t, o_hi, u_hi, OY0, OY1, 0, 2)
        # vertical louvres (two wide horizontal slots would read as a toaster): 2 px slots, 1 px rails set
        # 1 px back, so each face shows three (the front two) tall dark slits
        u = o_lo + 2
        i = 0
        while u + 1 <= o_hi - 1:
            box('slab_%s_rail%d' % (t, i), u, u + 1, OY0, OY1, 1, 2)
            u += 3
            i += 1

    slab('north', -6, 6, -5, 0, -7, -5)       # front: louvre x -5..0 (two slits), the lamp on the right jamb
    slab('south', -6, 6, -4, 4, 7, 5)
    slab('west', -5, 5, -4, 4, -7, -5)
    slab('east', -5, 5, -4, 4, 7, 5)

    # ---- the state lamp on the front's right jamb (x 1..5, y 7-11; 1 px clear of the louvre and the slab's edge) ---------
    kit.lamp(m, 'north', -7, 1, 7)

    # ---- the hub under the turntable: dark, 6x6, y 14-16; with the turntable's underside at 16 and the body's top at
    # 14 it leaves a 2 px dark gap all round (it reads as "this turns")
    m.box('hub', [-3, 14, -3], [3, 16, 3], 'dsteel')

    # ---- the module tray on the front edge (up to six modules, 0.25, standing; drawn by the block entity renderer)
    m.box('tray', [-5, 14, -8], [5, 15, -6], 'brass')

    # ---- the arm's mount at the back-right corner: a dark collar on the body's top corner
    m.box('mount', [3, 14, 3], [8, 15, 8], 'dsteel')

    # ---- the parking cradle on the back-left corner: a brass cup 4x2x4, a 2x2 recess 1 px deep with a rubber floor
    cx, cz = CRADLE
    m.box('cradle_floor', [cx - 2, 14, cz - 2], [cx + 2, 15, cz + 2], 'brass', part='cradle')
    m.box('cradle_w', [cx - 2, 15, cz - 2], [cx - 1, 16, cz + 2], 'brass', part='cradle')
    m.box('cradle_e', [cx + 1, 15, cz - 2], [cx + 2, 16, cz + 2], 'brass', part='cradle')
    m.box('cradle_n', [cx - 1, 15, cz - 2], [cx + 1, 16, cz - 1], 'brass', part='cradle')
    m.box('cradle_s', [cx - 1, 15, cz + 1], [cx + 1, 16, cz + 2], 'brass', part='cradle')

    # ---- the reel's axle boss (static) on the left side
    m.box('reel_boss', [-8, 7, -1], [-7, 9, 1], 'dsteel')

    # =================================================================================================================
    # Bones (10): turntable, swivel, upper, fore, head, tool_weld, tool_wrench, tool_grip, stamp, reel
    # =================================================================================================================
    pose = rest_pose()

    # turntable (code: rotY, a pure function of the build clock and the frame's slot table). An octagon 10 across
    # (an 8x8 square with 6-wide 1 px bumps on each side: radius 5.83), 4 px of hazard stripes (it moves into
    # space), its top faces painted as the rubber deck at y 20.
    m.bone('turntable', [0, 16, 0], code=('rotation',))
    kit.stripe_box(m, 'tt_core', [-4, 16, -4], [4, 20, 4], 'turntable', part='turntable')
    kit.stripe_box(m, 'tt_e', [4, 16, -3], [5, 20, 3], 'turntable', part='turntable')
    kit.stripe_box(m, 'tt_w', [-5, 16, -3], [-4, 20, 3], 'turntable', part='turntable')
    kit.stripe_box(m, 'tt_s', [-3, 16, 4], [3, 20, 5], 'turntable', part='turntable')
    kit.stripe_box(m, 'tt_n', [-3, 16, -5], [3, 20, -4], 'turntable', part='turntable')

    ax, az = AXIS
    # swivel (keyed yaw). A brass drum 5x5x1 on the mount and two cheeks either side of the shoulder pin.
    m.bone('swivel', [ax, SWIVEL_Y, az], rot=[0, pose['swivel'], 0])
    m.box('swivel_drum', [4, 15, 4], [9, 16, 9], 'brass', 'swivel', part='swivel')
    m.box('cheek_l', [4, 16, 4], [5, 20, 9], 'brass', 'swivel', part='swivel')
    m.box('cheek_r', [8, 16, 4], [9, 20, 9], 'brass', 'swivel', part='swivel')

    # upper arm (keyed pitch). Enamel bar 3x3, the shoulder pin at y 18, the elbow pin at y 36 in a brass knuckle; the
    # copper spring 2x8x2 on two 1 px stand-offs on the arm's side that faces the front at rest (local +X): in the
    # stood-off pose it lies beside the bar at the bar's height, under the mecha's feet (on the IN side it stood up
    # into a Bulwark's boots).
    m.bone('upper', [ax, HINGE_Y, az], 'swivel', rot=[pose['upper'], 0, 0])
    m.box('upper_bar', [5, 17, 5], [8, 35, 8], 'enamel', 'upper', part='upper')
    # the elbow: a slim brass knuckle round the elbow pin, kept small so that the brass focal point is the head (a big
    # brass block at the top of the zig-zag reads as the lamp's head)
    m.box('elbow', [1.5, 34.5, 4.5], [8.5, 37.5, 8.5], 'brass', 'upper', part='upper_elbow')     # the elbow pin, 6 wide
    m.box('spring', [9, 19, 6], [11, 27, 8], 'copper', 'upper', part='spring')
    m.box('standoff_1', [8, 21, 6], [9, 22, 7], 'dsteel', 'upper', part='spring')
    m.box('standoff_2', [8, 25, 6], [9, 26, 7], 'dsteel', 'upper', part='spring')

    # forearm (keyed pitch). Enamel bar 3x3 from the elbow to the head; a rubber cable along its OUT side.
    ey = HINGE_Y + LU
    m.bone('fore', [ax, ey, az], 'upper', rot=[pose['fore'], 0, 0])
    m.box('fore_bar', [2, 35, 5], [5, 59, 8], 'enamel', 'fore', part='fore')
    m.box('fore_cable', [3, 40, 8], [4, 57, 9], 'rubber', 'fore', part='fore_cable')

    # head (keyed pitch). A brass shade 5x5x4 round the head pivot (y 61), its face on local -Z with the lamp lens
    # (3x1x1) above the tool port; the tools retract inside the shade and slide out 4 px along -Z.
    hy = ey + LF
    hx = ax + LAT
    m.bone('head', [hx, hy, az], 'fore', rot=[pose['head'], 0, 0])
    m.box('shade', [1, hy - 2.5, 4], [6, hy + 2.5, 9], 'brass', 'head', part='shade')
    m.box('shade_back', [2, hy - 1.5, 9], [5, hy + 1.5, 10], 'dsteel', 'head', part='shade_back')
    m.box('head_lens', [2, hy + 1, 3], [5, hy + 2, 4], 'lens', 'head', part='head_lens')

    # the four tools, retracted (inside the shade, fronts 1 px in at z 5); each clip slides the one in use out by
    # posZ -5 (its tip to z 0: TOOL px from the head pivot)
    def tool(bone, boxes):
        m.bone(bone, [hx, hy, az], 'head')
        for name, f, t, mat in boxes:
            m.box(name, f, t, mat, bone, part=bone)
    tool('tool_weld', [('weld_nozzle', [2.5, hy - 1, 5], [4.5, hy + 1, 8], 'copper')])
    tool('tool_wrench', [('wrench_shank', [3, hy - 1, 6], [4, hy + 1, 8], 'steel'),
                         ('wrench_jaw', [2, hy - 1.5, 5], [5, hy + 0.5, 6], 'steel')])
    tool('tool_grip', [('grip_l', [1.5, hy - 1, 5], [2.5, hy + 1, 8], 'steel'),
                       ('grip_r', [4.5, hy - 1, 5], [5.5, hy + 1, 8], 'steel')])
    tool('stamp', [('stamp_pad', [2, hy - 1.5, 5], [5, hy + 1.5, 6], 'rubber'),
                   ('stamp_stem', [3, hy - 0.5, 6], [4, hy + 0.5, 8], 'steel')])

    # reel (code: rotX while charging, by the charge rate). Copper drum, 6 across, on its axle boss, 2 px proud.
    m.bone('reel', [-9, 8, 0], code=('rotation',))
    m.box('reel_drum', [-10, 6, -3], [-8, 10, 3], 'copper', 'reel', part='reel')
    m.box('reel_cap_t', [-10, 10, -2], [-8, 11, 2], 'copper', 'reel', part='reel')
    m.box('reel_cap_b', [-10, 5, -2], [-8, 6, 2], 'copper', 'reel', part='reel')
    m.box('reel_crank', [-12, 8, 1], [-10, 9, 2], 'brass', 'reel', part='reel_crank')

    # ---- locators (static points unless noted) -------------------------------------------------------------------
    m.locator('klaxon', [0, 10, -8], None)
    m.locator('deck', [0, 20, 0], None)
    m.locator('reel', [-10, 8, 0], None)
    m.locator('cradle', [cx, 16, cz], None)
    m.locator('swivel', [ax, 16, az], None)
    m.locator('tool_tip', [hx, hy, az - TOOL], 'head')      # the extended tool's tip, on the head (for renders)

    # ---- contents (block entity renderer) -----------------------------------------------------------------------
    m.anchor('frame', [0, 20, 0], 'the frame item before Assemble: FIXED, lying flat face-up on the deck, scale 0.5 '
             '(8 px; standing up it would read as a slice of toast in a toaster)', standin=((8, 1, 8), 'stand'))
    for i, x in enumerate((-4, 0, 4)):
        m.anchor('module_%d' % (i + 1), [x, 15, -7], 'module slot %d (the first three module stacks): FIXED, upright '
                 'facing -Z, scale 0.25 (4 px), on the tray' % (i + 1), standin=((3, 3, 1), 'standi'))

    # ---- the cell's shape (no top cell: the mecha stands in the cell above). The deck box reaches 4 px into the cell
    # above (a large collision shape, as a fence's): the mecha stands on it at y + 1.25.
    m.shape('lower', (1, 0, 1, 15, 14, 15))      # the pedestal
    m.shape('lower', (5, 14, 5, 11, 16, 11))     # the hub
    m.shape('lower', (3, 16, 3, 13, 20, 13))     # the turntable (into the cell above)
    m.shape('lower', (2, 14, 0, 14, 15, 2))      # the tray
    return m


def check(m):
    """The kit's checks without its 32 px rest-height limit (the Gantry has no top cell: everything above 16 is bones,
    and the arm's bones are built straight up and posed by rest rotations, so their unrotated boxes reach y 63)."""
    errors = []
    for name, f, t, mat, parent, kw in m.cubes:
        for i in range(3):
            s = t[i] - f[i]
            if abs(s - round(s)) > 1e-6 or s < 1 - 1e-6:
                errors.append('%s: size %s not a whole pixel >= 1' % (name, [t[j] - f[j] for j in range(3)]))
                break
        if mat not in kit.MATERIALS:
            errors.append('%s: material %s' % (name, mat))
    nb = len(m.bone_names())
    if nb > m.budget:
        errors.append('%d bones against %d' % (nb, m.budget))
    drawn = m.drawn()
    cop, flush = kit.faces_check(drawn, [c for c in m.cubes if not kit.is_hidden(m, c[4])])
    errors += ['coplanar overlap (z-fight): %s' % x for x in cop]
    for w in flush:
        sys.stderr.write('  WARN flush joint: %s\n' % w)
    static_top = max(c[2][1] for c in drawn if kit.is_static(m, c[4]))
    sys.stderr.write('gantry: %d bones, static top %.0f px, %d flush warnings\n' % (nb, static_top, len(flush)))
    if static_top > 16:
        errors.append('static part above 16 px (no top cell)')
    if errors:
        for e in errors:
            sys.stderr.write('  ERROR %s\n' % e)
        sys.exit('gantry: %d errors' % len(errors))


if __name__ == '__main__':
    m = model()
    check(m)
    m.write(PATH, check=False)
    print(kit.shapes_java(m))
    print('rest pose', {k: round(v, 2) for k, v in rest_pose().items()})

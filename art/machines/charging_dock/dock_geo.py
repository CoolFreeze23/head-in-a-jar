"""Charging Dock (a doctor's dial scale with a knife switch): geometry.

    python art/machines/charging_dock/dock_geo.py      # writes charging_dock.bbmodel and its placeholder PNG
    python art/machines/charging_dock/dock_anim.py     # then the clips (--review: the review copy and variants)

A low weighing platform you step onto, a slim column at the back carrying a big round-faced dial, and on the column's
right a brass knife switch on a dark slate panel. The jar steps on the scale and the switch is thrown shut (the
contacts close: the dock is charging it); the needle climbs with the jar's charge. Empty, the switch lies open,
thrown forward at 65 degrees. Faces -Z; +X is the machine's right (the viewer's left).

The platform (K1 inset 1 px): 14x14 at -7..7, its top at y 4 (the game's collision box(1,0,1,15,4,15)
and ChargingDockBlock.TOP = 4/16: the jar's feet land at y 4 wherever they stand on it). Nothing inside that
footprint rises above y 4, because a jar may stand on any part of the plate and would sink into anything higher:
the markings are geometry at or below y 4. A brass rim (the highest thing, y 3-4) overhangs an enamel riser 1 px
all round (the step up, a shadow line under its nosing), a dark rubber mat sits recessed 1 px inside the rim, and a
yellow target ring stands up out of the mat to y 4 (stand here). A dark kick under the riser.

The column (a 4-wide column at the back, the dial 8x8 at y 24-32 with its face 6x6 1 px back): x -2..2,
z 7..9, 1 px of it inside the cell (cell z 15-16) and 1 px behind, not z 14-16: the real jar's base and
lid are 14.3 px square (7.15 from its axis), so a centred jar reaches z 7.15 and would sit 1.15 px into a column
starting at z 6 (and a head set down by the code lands exactly centred). At z 7 the overlap is 0.15 px.

The jar (JarModel, JarRenderer.WIDE = 1.3 wide x 1.1 tall about the base; COMPACT 1.0), in px above its base
(measured for the Cradle and checked against JarRenderer):
    WIDE     base 14.3 sq, 0-1.1; glass 13 sq, 1.1-13.2; lid 14.3 sq, 13.2-14.3; valve 3.9 sq, 12.65-15.95
    COMPACT  base 11 sq, 0-1; glass 10 sq, 1-12; lid 11 sq, 12-13; valve 3 sq, 12.5-14.5
Standing on the plate (y 4): WIDE lid top 18.3, valve top 19.95; the jar player's collision box 13.76 x 15.2 px
(FormEvents.JAR_SIZE), top 19.2. Everything of the dock above the pad starts at y 19 or higher at the back
(slate panel, hinge) and y 20 at the front (the lamp), and the open switch rides at y 21-26.
"""
import base64
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
try:
    import kit  # noqa: E402
except ImportError:  # the worktree moved under us: a `git show` copy in the scratch folder
    sys.path.insert(0, 'build/machines-review/scratch-charging_dock')
    import kit  # noqa: E402

PATH = os.path.join(HERE, 'charging_dock.bbmodel')

PLATE = 4                    # the plate's top (collision)
JAR_AT = (0, PLATE, 0)       # where the code sets a head down, and a jar standing square in the middle
OPEN = 65                    # the switch's rest: thrown open this many degrees forward (top toward -Z)
PIVOT_SW = (6, 21, 5)        # the switch's hinge: blade root centre
PIVOT_NEEDLE = (-1, 28, 2.5)  # the dial's centre, in the needle's plane


def P(m, name, f, t, mat, part, parent='static', **kw):
    return m.box(name, f, t, mat, parent, part=part, **kw)


def ring(m, name, o, i, y0, y1, mat, part):
    """A square frame of four strips, outer half-size o, inner half-size i, y0..y1 (one part)."""
    P(m, name + '_n', [-o, y0, -o], [o, y1, -i], mat, part)
    P(m, name + '_s', [-o, y0, i], [o, y1, o], mat, part)
    P(m, name + '_w', [-o, y0, -i], [-i, y1, i], mat, part)
    P(m, name + '_e', [i, y0, -i], [o, y1, i], mat, part)


def model():
    m = kit.Model('charging_dock', budget=2)

    # ---- the platform ------------------------------------------------------------------------------------------
    m.box('kick', [-5, 0, -5], [5, 1, 5], 'rubber')                       # dark kick, 1 px in under the riser
    ring(m, 'riser', 6, 5, 1, 3, 'enamel', 'riser')                        # the riser, 12x12, y 1-3
    m.box('mat', [-5, 1, -5], [5, 3, 5], 'rubber', part='mat')             # the mat, recessed 1 px in the rim
    ring(m, 'rim', 7, 5, 3, 4, 'brass', 'rim')                             # brass rim 2 wide, 1 px nosing
    ring(m, 'target', 4, 2, 3, 4, 'yellow', 'target')                      # yellow target ring up to y 4

    # ---- the column, dial and lamp moved 1 px to the left (-X, the viewer's right) to give the
    # switch's T-grip 4 px of width; a dark slate board 15 wide behind the dial and switch (the instrument stands
    # visibly on its own board behind the jar, not on the jar's lid; the machine's dark mass); the needle a one-ended
    # pointer with a hub, so empty and full differ.
    # ---- the column (x -3..1, z 7..9, up behind the dial to y 31) ----------------------------------------------
    m.box('column', [-3, 0, 7], [1, 31, 9], 'enamel', part='column')

    # ---- the board: dark slate x -7..8, y 19-27, z 6..7, on the column's front (square corners) -----------------
    P(m, 'board', [-7, 19, 6], [8, 27, 7], 'rubber', 'panel')

    # ---- the dial: a brass bezel 8x8 with its corners cut (a round gauge, not a screen), x -5..3, y 24-32, z 3..6
    # (its back on the board); the cream face 6x6 set 1 px back (front z 4), its corners filled with brass -----------
    P(m, 'dial_b', [-4, 24, 3], [2, 25, 6], 'brass', 'dial')
    P(m, 'dial_t', [-4, 31, 3], [2, 32, 6], 'brass', 'dial')
    P(m, 'dial_l', [-5, 25, 3], [-4, 31, 6], 'brass', 'dial')
    P(m, 'dial_r', [2, 25, 3], [3, 31, 6], 'brass', 'dial')
    for tag, x0, y0 in (('bl', -4, 25), ('br', 1, 25), ('tl', -4, 30), ('tr', 1, 30)):
        P(m, 'dial_c' + tag, [x0, y0, 3], [x0 + 1, y0 + 1, 4], 'brass', 'dial')
    P(m, 'dial_face', [-4, 25, 4], [2, 31, 6], 'enamel', 'dial')
    P(m, 'dial_spindle', [-2, 27, 3], [0, 29, 4], 'brass', 'dial')     # the needle's spindle (holds the hub)

    # ---- the knife switch on the board's right end: copper hinge cheeks low and copper jaws at the board's top
    # either side of a brass blade (the `contacts` bone) with a yellow T-grip: shut, blade and grip stand 5 px above
    # the board (y 27-32), a bright bar in the outline; open, they are gone from it ------------------------------
    P(m, 'cheek_l', [4, 19, 5], [5, 22, 6], 'copper', 'panel')
    P(m, 'cheek_r', [7, 19, 5], [8, 22, 6], 'copper', 'panel')
    P(m, 'jaw_l', [4, 24, 5], [5, 27, 6], 'copper', 'panel')
    P(m, 'jaw_r', [7, 24, 5], [8, 27, 6], 'copper', 'panel')

    # ---- K3 lamp under the dial, on the board (the meter's pilot lamp) ------------------------------------------
    kit.lamp(m, 'north', 6, -3, 20)          # bezel x -3..1, y 20-24, z 5..6 (its top under the dial); lens z 4..5

    # ---- bones -------------------------------------------------------------------------------------------------
    # contacts: the switch blade, modelled shut (standing in the jaws); its rest is thrown open 65 degrees forward:
    # outliner rotation X -65 (checked by render: here the outliner's X has the keyframes' sign, +a turning +y toward
    # +z). Code: none. Clips: all of them.
    m.bone('contacts', list(PIVOT_SW), rot=[-OPEN, 0, 0])
    m.box('blade', [5, 21, 4], [7, 29, 6], 'brass', 'contacts', part='blade')
    m.box('grip', [4, 29, 4], [8, 32, 6], 'yellow', 'contacts', part='blade')      # the T: 4 wide

    # needle: one-ended: a dark 2x2 pointer from a brass hub (2x2, 2 deep, on the pivot) out 3 px to E (the viewer's
    # left, +X) at rest, in front of the face (z 2..3, the hub z 1..3), so it passes over the bezel; turned by code
    # about Z to F (the viewer's right) through the top.
    m.bone('needle', list(PIVOT_NEEDLE), code=('rotation',))
    m.box('needle_hub', [-2, 27, 1], [0, 29, 3], 'brass', 'needle', part='needle')
    m.box('needle_bar', [0, 27, 2], [2, 29, 3], 'rubber', 'needle', part='needle')

    # ---- locators ----------------------------------------------------------------------------------------------
    m.locator('jaws', [6, 26, 3.5])          # the spark when the blade goes home; the charging sparks
    m.locator('plate', [0, 4.5, 0])          # the plate's middle (the server's sparks, the ding)
    m.locator('dial', [-1, 28, 0.5])          # the tap's tick

    # ---- stand-in material swatches (the injected real-size jars map their faces onto these) --------------------
    m.group('standin_mats', None, hidden=True)
    for i, (mat, n) in enumerate((('steel', 2), ('stand', 2), ('glass', 8), ('brass', 2), ('saline', 2))):
        m.box('si_mat_%s' % mat, [20 + 10 * i, 0, 20], [20 + 10 * i + n, n, 20 + n], mat, 'standin_mats')

    # ---- cell shapes (Java cell coordinates; the plate is the game's own) -----------------------------------------
    m.shape('lower', (1, 0, 1, 15, 4, 15))         # the plate (ChargingDockBlock.SHAPE, unchanged)
    m.shape('lower', (5, 0, 15, 9, 16, 16))        # the column
    m.shape('upper', (5, 0, 15, 9, 16, 16))        # the column on up (the jar's box tops out at 3.2 here)
    m.shape('upper', (3, 8, 9, 11, 16, 16))        # the dial and the needle's hub
    m.shape('upper', (12, 4, 12, 16, 16, 16))      # the switch (panel, cheeks, jaws, the shut blade)
    m.shape('upper', (1, 4, 13, 16, 11, 16))       # the board, the lamp, the switch's fittings
    return m


# ---------------------------------------------------------------------------------------------------------------------
# Per-pixel face check (the Mixer's and the Cradle's): a face pixel is hidden when any opaque box covers it.
# ---------------------------------------------------------------------------------------------------------------------
def precise_check(m):
    drawn = m.drawn()
    occ = [c for c in drawn if c[3] not in ('glass', 'jglass')]
    vis = []
    for c in drawn:
        for face in kit._faces(c):
            ax, sg, pl, r = face
            o = [i for i in range(3) if i != ax]
            cells = set()
            for u in range(int(round(r[0])), int(round(r[1]))):
                for v in range(int(round(r[2])), int(round(r[3]))):
                    cu, cv = u + 0.5, v + 0.5
                    hidden = False
                    for d in occ:
                        if d is c:
                            continue
                        f, t = d[1], d[2]
                        inside = (f[ax] <= pl < t[ax]) if sg > 0 else (f[ax] < pl <= t[ax])
                        if inside and f[o[0]] < cu < t[o[0]] and f[o[1]] < cv < t[o[1]]:
                            hidden = True
                            break
                    if not hidden:
                        cells.add((u, v))
            if cells:
                vis.append((c, ax, sg, pl, cells))
    cop, flush = set(), set()
    for i in range(len(vis)):
        ci, ax, sg, pl, cells = vis[i]
        for j in range(i + 1, len(vis)):
            cj, ax2, sg2, pl2, cells2 = vis[j]
            if ax != ax2 or sg != sg2 or abs(pl - pl2) > 1e-6:
                continue
            if cells & cells2:
                cop.add('%s / %s' % (ci[0], cj[0]))
                continue
            if ci[5]['part'] == cj[5]['part'] or 'glass' in (ci[3], cj[3]):
                continue
            for (u, v) in cells:
                if {(u + 1, v), (u - 1, v), (u, v + 1), (u, v - 1)} & cells2:
                    flush.add('%s / %s (%s %s)' % (ci[0], cj[0], ci[3], cj[3]))
                    break
    return sorted(cop), sorted(flush)


# ---------------------------------------------------------------------------------------------------------------------
# The stand-in jars at their real (fractional) sizes, injected after the kit's checks (which want whole pixels).
# Each face maps onto a material swatch cube in `standin_mats`. Never exported.
#   standin_jar         the jar player (WIDE) standing square in the middle, which is also where the code sets a head
#   standin_jar_45      the same turned 45 degrees (its corners reach 10.1 px from its axis)
#   standin_compact     a head set down in a Compact Jar
#   standin_jar_front   a jar standing at the plate's front edge (it charges anywhere on the plate)
# ---------------------------------------------------------------------------------------------------------------------
GLASS_FRAME = (0xa8, 0xd0, 0xd9, 255)
GLASS_STREAK = (0xd0, 0xea, 0xe9, 255)


def jar_boxes(compact, at):
    ax, ay, az = at
    if compact:
        w, base, glass, gw, lid, valve, vw, pipe, pipey, head = (5.5, (0, 1), (1, 12), 5, (12, 13), (12.5, 14.5),
                                                                 1.5, (5, 6), (3, 9), (1.2, 9.2))
        pz = 0.5
        light = (5, 6, 1, 2)
    else:
        w, base, glass, gw, lid, valve, vw, pipe, pipey, head = (7.15, (0, 1.1), (1.1, 13.2), 6.5, (13.2, 14.3),
                                                                 (12.65, 15.95), 1.95, (6.5, 7.8), (3.3, 9.9),
                                                                 (1.5, 9.5))
        pz = 0.65
        light = (6.5, 7.8, 1.1, 2.2)
    B = [('base', [-w, base[0], -w], [w, base[1], w], 'steel'),
         ('lid', [-w, lid[0], -w], [w, lid[1], w], 'steel'),
         ('valve', [-vw, valve[0], -vw], [vw, valve[1], vw], 'brass'),
         ('pipe_r', [pipe[0], pipey[0], -pz], [pipe[1], pipey[1], pz], 'steel'),
         ('pipe_l', [-pipe[1], pipey[0], -pz], [-pipe[0], pipey[1], pz], 'steel'),
         ('light', [-1.3, light[2], light[0]], [1.3, light[3], light[1]], 'saline'),
         ('head', [-4, head[0], -4], [4, head[1], 4], 'stand'),
         ('glass', [-gw, glass[0], -gw], [gw, glass[1], gw], 'glass')]
    return [(n, [f[0] + ax, f[1] + ay, f[2] + az], [t[0] + ax, t[1] + ay, t[2] + az], mat) for n, f, t, mat in B]


STANDINS = [  # group, compact, base centre, yaw, shown in the main model
    ('standin_jar', False, JAR_AT, 0, True),
    ('standin_jar_45', False, JAR_AT, 45, False),
    ('standin_compact', True, JAR_AT, 0, False),
    ('standin_jar_front', False, (0, PLATE, -11), 0, False),
]


def inject_jars(d, m):
    els = {e['name']: e for e in d['elements'] if e.get('type') == 'cube'}
    swatch = {mat: els['si_mat_' + mat]['faces'] for mat in ('steel', 'stand', 'glass', 'brass', 'saline')}
    for gname, compact, at, yaw, shown in STANDINS:
        gu = kit.nid(m.name, 'group', gname)
        d['groups'].append({'uuid': gu, 'name': gname, 'origin': list(at), 'rotation': [0, yaw, 0], 'export': True,
                            'visibility': shown, 'mirror_uv': False, 'reset': False, 'color': 0, 'autouv': 0,
                            'shade': True, 'locked': False})
        kids = []
        for n, f, t, mat in jar_boxes(compact, at):
            name = 'si_%s_%s' % (gname[8:], n)
            u = kit.nid(m.name, 'cube', name)
            faces = {k: {'uv': list(swatch[mat][k]['uv']), 'texture': 0} for k in swatch[mat]}
            d['elements'].append({'uuid': u, 'name': name, 'origin': [(f[i] + t[i]) / 2 for i in range(3)], 'color': 0,
                                  'type': 'cube', 'from': [round(v, 3) for v in f], 'to': [round(v, 3) for v in t],
                                  'box_uv': False, 'faces': faces, 'rescale': False, 'locked': False,
                                  'light_emission': 0, 'render_order': 'default', 'allow_mirror_modeling': True,
                                  'autouv': 0, 'visibility': True})
            kids.append(u)
        d['outliner'].append({'uuid': gu, 'isOpen': False, 'children': kids})


def clear_glass(d, png):
    """Clear glass placeholder (cutout): the stand-in jar's glass swatch as a light frame, the rest clear."""
    from PIL import Image
    im = Image.open(png).convert('RGBA')
    px = im.load()
    for e in d['elements']:
        if e.get('type') != 'cube' or e['name'] != 'si_mat_glass':
            continue
        for face in e['faces'].values():
            u0, v0, u1, v1 = face['uv']
            xa, xb = sorted((int(u0), int(u1)))
            ya, yb = sorted((int(v0), int(v1)))
            for y in range(ya, yb):
                for x in range(xa, xb):
                    edge = x in (xa, xb - 1) or y in (ya, yb - 1)
                    px[x, y] = GLASS_FRAME if edge else (0, 0, 0, 0)
            for (x, y) in [(xa + 2, ya + 1), (xa + 1, ya + 2), (xb - 3, yb - 2), (xb - 2, yb - 3)]:
                px[x, y] = GLASS_STREAK
    im.save(png)


def post(path, m):
    with open(path, encoding='utf-8') as fh:
        d = json.load(fh)
    png = os.path.splitext(path)[0] + '_placeholder.png'
    clear_glass(d, png)
    with open(png, 'rb') as fh:
        d['textures'][0]['source'] = 'data:image/png;base64,' + base64.b64encode(fh.read()).decode()
    inject_jars(d, m)
    with open(path, 'w', encoding='utf-8') as fh:
        json.dump(d, fh, indent=1)


if __name__ == '__main__':
    m = model()
    cop, flush = precise_check(m)
    sys.stderr.write('precise check: %d z-fights, %d flush joints\n' % (len(cop), len(flush)))
    for x in cop:
        sys.stderr.write('  PRECISE Z-FIGHT %s\n' % x)
    for x in flush:
        sys.stderr.write('  PRECISE FLUSH %s\n' % x)
    if cop:
        sys.exit('precise z-fights')
    m.write(PATH)
    post(PATH, m)
    print(kit.shapes_java(m))

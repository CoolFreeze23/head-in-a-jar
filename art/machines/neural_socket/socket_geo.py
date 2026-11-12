"""Neural Socket (a pillar box crossed with a plinth): geometry.

    python art/machines/neural_socket/socket_geo.py      # writes neural_socket.bbmodel and its placeholder PNG
    python art/machines/neural_socket/socket_anim.py     # then the clips (--review: review copies; --sweep)

A squat ivory pillar with a brass cap (the deck) that a jar stands on. Four brass corner clamps (the `claws` bone, all
four together) slide up the pillar's corners and close round the jar's base plate when a jar plugs in or a head is set
in; they drop 6 px down the corners when it leaves. Each side has a dark 4x8 slot where that face's redstone level is
drawn as a lit bar; the UP channel lights the dark band under the cap and the DOWN channel the rubber foot band (both
drawn by the renderer as emissive quads; rectangles in LEVEL_QUADS below). A brass plug (the `plug` bone) sits flush in
a dark 6x6 socket in the middle of the deck: empty, it reads as the socket; occupied, it rises 1 px into the jar's
base (hidden, heard). Faces -Z; +X is the machine's right (the viewer's left).

The jar is the game's own, at 1:1 (never modelled): JarRenderer.WIDE (1.3 wide x 1.1 tall about the base) or COMPACT,
in px above its base (measured for the Cradle and the Dock, checked against JarRenderer):
    WIDE     base 14.3 sq, 0-1.1; glass 13 sq, 1.1-13.2; lid 14.3 sq, 13.2-14.3; valve 3.9 sq, 12.65-15.95
    COMPACT  base 11 sq, 0-1; glass 10 sq, 1-12; lid 11 sq, 12-13; valve 3 sq, 12.5-14.5
THE ANCHOR: the jar's base centre (the underside of its base plate) at model (0, 16, 0), facing -Z (the socket's front),
scale 1. That is the top centre of the block: NeuralSocketBlockEntity.plugIn teleports the jar player to
Vec3.atBottomCenterOf(pos.above()), and the placed head is drawn there (HeadWorkRenderers.Socket translates (0.5, 1, 0.5)).
One anchor serves both. The deck's top is y 16 (the full-cube collision the jar stands on).

Clamps: 1 px brass plates hugging the pillar's corners, inside the block (x and z within -8..8, so two sockets side by
side never meet): a 2-wide return on the front and back (x +-6..8) and a 4-deep plate on each side (z +-4..8). Closed
they reach y 18 (2 px above the deck, gripping the WIDE base's 1.1 px edge: they bite 0.15 px into the 14.3 px base,
which reaches 7.15 from the axis, as the Dock's column does); the glass (6.5 from the axis) stays 0.5 px clear.
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
    sys.path.insert(0, 'build/machines-review/scratch-neural_socket')
    import kit  # noqa: E402

PATH = os.path.join(HERE, 'neural_socket.bbmodel')

DECK = 16                    # the deck's top: the jar's base (collision: the full cube)
JAR_AT = (0, DECK, 0)        # THE anchor: the jar's base centre, facing -Z, scale 1
CLAW_TRAVEL = 6              # the clamps' travel: down (open, the rest) -> up (closed)
CLAW_OPEN = (2, 12)          # y span of the clamps at rest (open, modelled here); closed = +6 -> 8..18
PIVOT_CLAWS = (0, 2, 0)
PIVOT_PLUG = (0, 16, 0)

# The lit level bars and rings, for the renderer (model px, facing north; turn with the block's facing). Each is a
# rectangle on a plane; the renderer draws an emissive quad 0.02 px in front of it (outward), lit to the level.
#   kind 'bar':  lit from the bottom up: lit height = 8 * level / 15 (y0 + that)
#   kind 'ring': lit outward from the face's centre: lit half-width = 6 * level / 15
# face: which model face the quad faces (north = the front, -Z).
LEVEL_QUADS = {
    # channel     face     plane           u range (x or z)   y range   kind
    'front': ('north', ('z', -6), ('x', -5, -1), (5, 12), 'bar'),    # the front's slot is off-centre (lamp beside it)
    'back':  ('south', ('z', 6), ('x', -2, 2), (5, 12), 'bar'),
    'right': ('east', ('x', 6), ('z', -2, 2), (5, 12), 'bar'),       # +X, the machine's right (the viewer's left)
    'left':  ('west', ('x', -6), ('z', -2, 2), (5, 12), 'bar'),
    'up':    ('all four', ('core', 6), ('u', -5, 5), (13, 15), 'ring'),   # the band under the cap, between its posts
    'down':  ('all four', ('plinth', 7), ('u', -7, 7), (0, 2), 'ring'),   # the foot band, lit to its corners at 15
}


def P(m, name, f, t, mat, part, parent='static', **kw):
    return m.box(name, f, t, mat, parent, part=part, **kw)


def skin(m, face, slot_u, slot_y=(5, 12)):
    """One side's enamel skin, 1 px over the core (plane +-6..7), spanning u -6..6, y 3..13, with a slot cut out."""
    u0, u1 = slot_u
    y0, y1 = slot_y
    pieces = [(-6, u0, 3, 13), (u1, 6, 3, 13), (u0, u1, 3, y0), (u0, u1, y1, 13)]
    for i, (a, b, c, d) in enumerate(pieces):
        if b <= a or d <= c:
            continue
        P(m, 'skin_%s_%d' % (face[0], i), *kit.on_face(face, -7 if face in ('north', 'west') else 7, a, b, c, d, -1, 0),
          mat='enamel', part='skin')
    # posts at the face's ends carry the cap: the band under it is a recessed window, not a gap under a floating roof
    for j, (a, b) in enumerate(((-6, -5), (5, 6))):
        P(m, 'post_%s_%d' % (face[0], j), *kit.on_face(face, -7 if face in ('north', 'west') else 7, a, b, 13, 15, -1, 0),
          mat='enamel', part='skin')


def model():
    m = kit.Model('neural_socket', budget=2)

    # ---- the foot band (a 14x2x14 rubber plinth inset 1): the DOWN ring is drawn on its four faces ---------
    m.box('plinth', [-7, 0, -7], [7, 2, 7], 'rubber')

    # ---- the core: dark, 12x13x12; seen only as the 1 px neck line over the plinth (y 2-3), the 2 px band under the
    # cap (y 13-15, the UP ring), the slots' backs and the socket ring round the plug -----------------------------------
    m.box('core', [-6, 2, -6], [6, 14, 6], 'rubber')                   # its top in the well: y 14, 2 px below the deck
    # under the cap, 1 px in from it (a step, the band's back): round the well
    P(m, 'core_ring_n', [-5, 14, -5], [5, 15, -3], 'rubber', 'core_ring')   # the well steps in 1 px under the deck
    P(m, 'core_ring_s', [-5, 14, 3], [5, 15, 5], 'rubber', 'core_ring')
    P(m, 'core_ring_w', [-5, 14, -3], [-3, 15, 3], 'rubber', 'core_ring')
    P(m, 'core_ring_e', [3, 14, -3], [5, 15, 3], 'rubber', 'core_ring')

    # ---- the ivory skins (y 3..13, 1 px over the core on each side, the corners stepped: the K2 look) --------------
    skin(m, 'north', (-5, -1))           # the front: the slot at the viewer's right of centre, the lamp at the left
    skin(m, 'south', (-2, 2))
    skin(m, 'east', (-2, 2))
    skin(m, 'west', (-2, 2))

    # ---- K3 lamp on the front, at the viewer's left of the slot (bezel x 1..5, y 8..12; lens 1 px out of the block)
    kit.lamp(m, 'north', -7, 1, 8)

    # ---- the deck: a brass cap, 14x14 at y 15..16 (the jar's floor), with 8-wide nosings to the block's edge between
    # the clamps (a 1 px overhang at the middle of each side: the pillar box's cap), and a 6x6 socket in the middle
    # (its floor the dark core, y 15) holding the plug ---------------------------------------------------------------
    y0, y1 = DECK - 1, DECK
    # dark steel, not brass: the brass clamps close against it (brass on brass would hide the bite). An 8x8 well, 2 deep,
    # in the middle: the receptacle, readable from any raised view; the plug stands 1 px proud of its floor
    # the cap sits 1 px inside the pillar's posts (a lid on the pillar, not a roof hovering over it), with 2-deep
    # nosings to the block's edge in the middle of each side
    P(m, 'deck_n', [-6, y0, -6], [6, y1, -4], 'dsteel', 'deck')
    P(m, 'deck_s', [-6, y0, 4], [6, y1, 6], 'dsteel', 'deck')
    P(m, 'deck_w', [-6, y0, -4], [-4, y1, 4], 'dsteel', 'deck')
    P(m, 'deck_e', [4, y0, -4], [6, y1, 4], 'dsteel', 'deck')
    P(m, 'nose_n', [-4, y0, -8], [4, y1, -6], 'dsteel', 'deck')
    P(m, 'nose_s', [-4, y0, 6], [4, y1, 8], 'dsteel', 'deck')
    P(m, 'nose_w', [-8, y0, -4], [-6, y1, 4], 'dsteel', 'deck')
    P(m, 'nose_e', [6, y0, -4], [8, y1, 4], 'dsteel', 'deck')

    # ---- bones ---------------------------------------------------------------------------------------------------
    # claws: the four corner clamps, modelled OPEN (down, y 2..12: the rest, an empty socket asleep); keyed posY +6 to
    # close (y 8..18). Clips only.
    m.bone('claws', list(PIVOT_CLAWS))
    a, b = CLAW_OPEN
    for tag, sx, sz in (('fl', 1, -1), ('fr', -1, -1), ('bl', 1, 1), ('br', -1, 1)):
        # the return across the front or back face (2 wide, x +-6..8) and the side plate (4 deep, z +-4..8)
        xr = sorted((6 * sx, 8 * sx))
        zr = sorted((7 * sz, 8 * sz))
        P(m, 'claw_%s_ret' % tag, [xr[0], a, zr[0]], [xr[1], b, zr[1]], 'brass', 'claw_' + tag, 'claws')
        xs = sorted((7 * sx, 8 * sx))
        zs = sorted((4 * sz, 7 * sz))
        P(m, 'claw_%s_side' % tag, [xs[0], a, zs[0]], [xs[1], b, zs[1]], 'brass', 'claw_' + tag, 'claws')

    # plug: the brass jack in the deck's socket, its top flush with the deck (y 12..16; a 1 px dark ring round it);
    # keyed posY +1 when a jar is clamped (into the jar's base plate, hidden). Clips only.
    m.bone('plug', list(PIVOT_PLUG))
    P(m, 'plug', [-2, 11, -2], [2, 15, 2], 'brass', 'plug', 'plug')      # 1 px proud of the well's floor (y 14)

    # ---- locators ------------------------------------------------------------------------------------------------
    m.locator('deck', [0, 16.5, 0])          # the deck's centre (under the jar's base): the clamp's puff, the plug
    m.locator('claw_fl', [7, 16.5, -7])      # a front clamp's top (closed): the clamp's chunk
    m.locator('slot_front', [-3, 8, -7])     # the front bar (a pulse's spark, if wanted)

    # ---- stand-in swatches (the injected real-size jars and the lit bars map their faces onto these) ----------------
    m.group('standin_mats', None, hidden=True)
    for i, (mat, n) in enumerate((('steel', 2), ('stand', 2), ('glass', 8), ('brass', 2), ('saline', 2),
                                  ('lens_wait', 2))):
        m.box('si_mat_%s' % mat, [20 + 10 * i, 0, 20], [20 + 10 * i + n, n, 20 + n], mat, 'standin_mats')

    # ---- cell shape: the full cube, as today (the jar stands on its top) -------------------------------------------
    m.shape('lower', (0, 0, 0, 16, 16, 16))
    return m


# ---------------------------------------------------------------------------------------------------------------------
# Per-pixel face check (the Dock's): a face pixel is hidden when any opaque box covers it.
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
# Stand-ins at their real (fractional) sizes, injected after the kit's checks (which want whole pixels). Never exported.
#   standin_jar       the WIDE jar (a jar player, or a head set in), base centre at the anchor, square to the front
#   standin_compact   a Compact Jar player at the same anchor
#   standin_jar_45    a WIDE jar turned 45 degrees (why the jar's yaw must be held square in SOCKET form)
#   standin_bars      the renderer's lit bars and rings at a sample of levels (front 15, right 8, up 15, down 4)
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


def bar_boxes(levels):
    """The lit quads as thin stand-in boxes (0.2 px, in front of their planes) for review renders."""
    out = []
    e = 0.2
    for ch, lv in levels.items():
        face, (_k, pl), (_a, u0, u1), (y0, y1), kind = LEVEL_QUADS[ch]
        if kind == 'bar':
            ya, yb = y0, y0 + (y1 - y0) * lv / 15.0
            if face == 'north':
                out.append(('bar_' + ch, [u0, ya, pl - e], [u1, yb, pl], 'lens_wait'))
            elif face == 'south':
                out.append(('bar_' + ch, [u0, ya, pl], [u1, yb, pl + e], 'lens_wait'))
            elif face == 'east':
                out.append(('bar_' + ch, [pl, ya, u0], [pl + e, yb, u1], 'lens_wait'))
            else:
                out.append(('bar_' + ch, [pl - e, ya, u0], [pl, yb, u1], 'lens_wait'))
        else:
            hw = u1 * lv / 15.0
            for f in ('n', 's', 'e', 'w'):
                if f == 'n':
                    out.append(('ring_%s_%s' % (ch, f), [-hw, y0, -pl - e], [hw, y1, -pl], 'lens_wait'))
                elif f == 's':
                    out.append(('ring_%s_%s' % (ch, f), [-hw, y0, pl], [hw, y1, pl + e], 'lens_wait'))
                elif f == 'e':
                    out.append(('ring_%s_%s' % (ch, f), [pl, y0, -hw], [pl + e, y1, hw], 'lens_wait'))
                else:
                    out.append(('ring_%s_%s' % (ch, f), [-pl - e, y0, -hw], [-pl, y1, hw], 'lens_wait'))
    return out


STANDINS = [  # group, boxes, yaw, centre, shown in the main model
    ('standin_jar', lambda: jar_boxes(False, JAR_AT), 0, JAR_AT, True),
    ('standin_compact', lambda: jar_boxes(True, JAR_AT), 0, JAR_AT, False),
    ('standin_jar_45', lambda: jar_boxes(False, JAR_AT), 45, JAR_AT, False),
    ('standin_bars', lambda: bar_boxes({'front': 15, 'right': 8, 'back': 11, 'left': 5, 'up': 15, 'down': 6}), 0,
     (0, 0, 0), False),
]


def inject(d, m):
    els = {e['name']: e for e in d['elements'] if e.get('type') == 'cube'}
    swatch = {mat: els['si_mat_' + mat]['faces'] for mat in ('steel', 'stand', 'glass', 'brass', 'saline',
                                                              'lens_wait')}
    for gname, boxes, yaw, at, shown in STANDINS:
        gu = kit.nid(m.name, 'group', gname)
        d['groups'].append({'uuid': gu, 'name': gname, 'origin': list(at), 'rotation': [0, yaw, 0], 'export': True,
                            'visibility': shown, 'mirror_uv': False, 'reset': False, 'color': 0, 'autouv': 0,
                            'shade': True, 'locked': False})
        kids = []
        for n, f, t, mat in boxes():
            name = 'si_%s_%s' % (gname[8:], n)
            u = kit.nid(m.name, 'cube', name)
            faces = {k: {'uv': list(swatch[mat][k]['uv']), 'texture': 0} for k in swatch[mat]}
            d['elements'].append({'uuid': u, 'name': name, 'origin': [(f[i] + t[i]) / 2 for i in range(3)], 'color': 0,
                                  'type': 'cube', 'from': [round(v, 3) for v in f], 'to': [round(v, 3) for v in t],
                                  'box_uv': False, 'faces': faces, 'rescale': False, 'locked': False,
                                  'light_emission': 15 if mat == 'lens_wait' else 0, 'render_order': 'default',
                                  'allow_mirror_modeling': True, 'autouv': 0, 'visibility': True})
            kids.append(u)
        d['outliner'].append({'uuid': gu, 'isOpen': False, 'children': kids})


def clear_glass(d, png):
    """Clear glass placeholder (cutout): the stand-in jar's glass swatch as a light frame with streaks, clear inside."""
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
    inject(d, m)
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

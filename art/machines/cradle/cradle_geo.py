"""Life Support Cradle (design C, the Convalescent's Armchair): geometry.

    python art/machines/cradle/cradle_geo.py      # writes cradle.bbmodel and its placeholder PNG
    python art/machines/cradle/cradle_anim.py     # then writes the clips into it (--review: the review copies)

A wingback armchair for a jar. The player's docked head sits in the mod's real Life Support Jar at 1:1, drawn by the
game (`JarRenderer`) at the anchor `jar`, never modelled: the seat is built round the jar's measured size. A dark rubber
seat and back cushion inside an enamel frame (the jar's glass is seen against the dark back), arms with recessed outer
panels, ear wings, a brass crest rail on top of the back that is the board of a bellows hidden in the back: while the
Cradle keeps a head alive the crest rises 6 px on a dark pleated column and sinks again, once every 3 seconds (the
breathing). A feed hose from a collar on the headrest swings down onto the jar's valve; the saline bag hangs from a
brass hook on the right wing (its level is the `saline` blockstate, the bag itself shrinking); a cream lap blanket
waits folded on the left arm and is laid over the jar's base when a head docks. Lamp low on the front right; the
charge terminal (K10, lying on its side) in the left arm's recessed panel. Faces -Z; +X is the machine's right.
Rest height 28 (32-34 at the top of a breath). Overhangs its block: arms 2 px a side (accepted), the bag
4 px right, the terminal 5 px left, the back 2 px behind, the blanket 3 px in front when laid.

The jar (JarModel, JarRenderer.WIDE = 1.3 wide x 1.1 tall about the base; COMPACT 1.0), in px above its base:
    WIDE     base 14.3 sq, 0-1.1; glass 13 sq, 1.1-13.2; lid 14.3 sq, 13.2-14.3; valve 3.9 sq, 12.65-15.95;
             side pipes x +-6.5..7.8, 3.3-9.9, z +-0.65; status light on the back, z +6.5..7.8, 1.1-2.2;
             head 8 cube 1.5-9.5 (hat 1.1-9.9)
    COMPACT  base 11 sq, 0-1; glass 10 sq, 1-12; lid 11 sq, 12-13; valve 3 sq, 12.5-14.5; pipes x +-5..6, 3-9;
             status light z +5..6, 1-2; head 1.2-9.2
Anchor (0, 6, -1): the base on the cushion (y 6), centred in x, z -1 (not -2: at -2 the WIDE base stands
1.15 px out of the block's front and off the seat's front edge; at -1 it spans z -8.15..6.15 and its status light ends
at 6.8, 1.2 px clear of the back cushion at z 8). The arms' inner faces at x +-8 clear the pipes (7.8) by 0.2.
"""
import base64
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
import kit  # noqa: E402

PATH = os.path.join(HERE, 'cradle.bbmodel')

ANCHOR = (0, 6, -1)
SHOWN_LEVEL = 3                              # the bag level drawn in the model (others are hidden alternatives)
BAG = (9, 13, 17, 24, -3, 0)                 # x0, x1, y0, y1, z0, z1 (v2: raised 4 and out 1, clear of the jar)
LEVELS = {1: 2, 2: 3, 3: 5, 4: 7}            # saline height in the bag, px from its bottom; 0 = an empty bag


def F(m, name, f, t, mat='enamel'):
    """A piece of the chair's enamel frame (arms, wings, stiles, back rail: one part, painted as one body)."""
    return m.box(name, f, t, mat, part='frame')


def model():
    m = kit.Model('life_support_cradle', budget=3)

    # ---- K1: dark plinth 16x1x16 inside the arms, feet inset 1 (the arms and back overhang them: a shadow gap) --
    kit.plinth(m, -8, 8, -8, 7)

    # ---- the seat: an enamel front rail and a dark rubber seat block; 1 px slots between seat and arms go down to
    # the plinth (3 px deep: the "gap between arms and cushion" dark). The jar's base stands on the seat at y 6.
    F(m, 'front_rail', [-8, 3, -9], [8, 5, -8])
    m.box('seat', [-7, 3, -8], [7, 6, 8], 'rubber', part='cushion')

    # ---- arms (x +-8..10, y 1..12): a 1 px inner slab and an outer frame of strips round a recessed panel (the
    # 16x8 outer face split by a 1 px step); the recess is open at the bottom, over the feet ----
    for s, tag in ((1, 'r'), (-1, 'l')):
        def X(a, b):
            return sorted([s * a, s * b])
        xa = X(8, 9)
        xb = X(9, 10)
        F(m, 'arm_core_' + tag, [xa[0], 1, -9], [xa[1], 10, 6])
        F(m, 'arm_top_' + tag, [X(8, 10)[0], 10, -9], [X(8, 10)[1], 12, 6])
        F(m, 'arm_front_' + tag, [xb[0], 1, -9], [xb[1], 10, -7])
        F(m, 'arm_back_' + tag, [xb[0], 1, 4], [xb[1], 10, 6])
        # ear wing on the arm, standing 2 px above the jar's lid-and-valve (22)
        F(m, 'wing_' + tag, [X(8, 10)[0], 12, 1], [X(8, 10)[1], 24, 6])
        # back stile
        F(m, 'stile_' + tag, [X(8, 10)[0], 1, 6], [X(8, 10)[1], 26, 10])

    # ---- the back: a rubber cushion recessed 2 px behind the frame's front (the jar's glass is seen against it),
    # the top rail above it (it hides the bellows), an enamel2 rear board inside a 1 px groove (the rear split) ----
    m.box('back_cushion', [-8, 3, 8], [8, 19, 9], 'rubber', part='cushion')
    F(m, 'back_rail', [-8, 19, 6], [8, 26, 10])
    m.box('rear_board', [-7, 4, 9], [7, 18, 10], 'enamel2')

    # ---- the feed collar on the headrest, right of centre, where the hose leaves the chair (dark steel, so it is
    # not a second brass square beside the lamp)
    m.box('hose_collar', [1, 21, 3], [5, 25, 5], 'dsteel')
    # a dark rubber headrest pad on the rail above the jar: with the back cushion, the chair's one dark mass reads
    # round the jar's lid at ten blocks (the cushion itself is mostly behind the jar's glass)
    m.box('headrest', [-7, 20, 5], [7, 26, 6], 'rubber')

    # ---- the saline bag on the right wing: a brass hook lying on the wing's top and reaching forward over the bag,
    # the bag hanging from it beside the wing's upper half (ivory wing between bag and jar), a pale drip line from
    # the bag's foot down into the arm top (the feed goes through the chair to the hose). Levels 0-4 are the
    # `saline` blockstate: opaque saline from the bag's bottom, the rest of the bag the house clear glass (an empty
    # bag is a clear outline). Big and in the open.
    x0, x1, y0, y1, z0, z1 = BAG
    m.box('bag_hook', [9, y1, -2], [11, y1 + 2, 3], 'brass')
    m.box('drip_line', [10, 12, -1], [12, y0, 1], 'zinc')
    for n in range(0, 5):
        g = 'state_saline_%d' % n
        m.group(g, 'static', hidden=(n != SHOWN_LEVEL))
        h = LEVELS.get(n, 0)
        if h:
            m.box('bag_saline_%d' % n, [x0, y0, z0], [x1, y0 + h, z1], 'saline', g, part='bag_%d' % n)
        if y0 + h < y1:
            m.box('bag_skin_%d' % n, [x0, y0 + h, z0], [x1, y1, z1], 'glass', g, part='bag_%d' % n)

    # ---- K3 lamp on the headrest, upper left, above the jar's lid corner: seen from the front at any range ----
    kit.lamp(m, 'north', 5, -6, 21)

    # ---- K10 charge terminal lying on its side in the left arm's recessed panel (where FE goes in; low, off the
    # seat, so nothing says "electric chair") ----
    m.box('term_collar', [-10, 4, -2], [-9, 8, 2], 'rubber', part='terminal')
    m.box('term_post', [-12, 5, -1], [-10, 7, 1], 'copper', part='terminal')
    m.box('term_cap', [-13, 4, -2], [-12, 8, 2], 'brass', part='terminal')

    # ---- bones ------------------------------------------------------------------------------------------------
    # bellows: the brass crest rail (18x2x5, 2 px proud of the back's front) and, hanging under it inside the back
    # rail, a pleated column (a 12x6x2 rubber core filling it and three 14x1x2 steel ribs: a corrugated outline). Keyed posY 0..+6
    # (`breathe`); keyed rotZ +-6 (`refuse`). At rest the column is wholly inside the rail. The ribs are steel (the
    # folds' lit edges) on the dark core: a striped column reads as a bellows against sky or a dark wall alike.
    m.bone('bellows', [0, 26, 7])
    m.box('crest', [-9, 26, 4], [9, 28, 9], 'brass', 'bellows', part='bellows')
    # the core is three 12x1x2 segments between the ribs, not one box through them: a 2 px core inside 2 px ribs
    # shared their front and back planes at y 21, 23, 25 (seen on the painted sheet; the kit check now tests a moving
    # part's faces against its own bone only). `pleat_core` keeps its painted island (UVs frozen).
    for i, y in enumerate((20, 22, 24)):
        m.box('pleat_core' if i == 0 else 'pleat_core_%d' % i, [-6, y, 7], [6, y + 1, 9], 'rubber', 'bellows',
              part='bellows')
    for i, y in enumerate((21, 23, 25)):
        m.box('pleat_rib_%d' % i, [-7, y, 7], [7, y + 1, 9], 'steel', 'bellows', part='bellows')

    # hose_end: the feed hose (pale ribbed steel hose, `zinc`, 2x2) with a brass nozzle, modelled parked: standing up
    # in front of the collar, right of centre (x 2..4, y 23..28, 1 px clear of the crest's front). Docked = rotX -90
    # then rotY 45 (GeckoLib applies X first): lying forward and inward at y 22..24, the nozzle on the valve's top.
    m.bone('hose_end', [3, 23, 2])
    m.box('hose', [2, 23, 1], [4, 26, 3], 'zinc', 'hose_end', part='hose')
    m.box('hose_nozzle', [2, 26, 1], [4, 28, 3], 'brass', 'hose_end', part='hose')

    # blanket: cream wool, two boxes: a 12x3x1 hang and a 12x1x2 rolled lip on top of it, 1 px proud (an L: a drape, not a
    # slab). Folded on the left arm: the hang standing on the arm top (x -10..-9, y 12..15), the lip over it
    # (x -11..-9, y 15..16), z -12..0 (3 px over the arm's front end, 1 px clear of the wing). Laid (rotY -90 and offset LAID): the lip lies along the seat's front rail under
    # the jar's base (x -6..6, y 5..6, z -11..-9), the hang drops in front of the rail to y 2, the feet showing.
    m.bone('blanket', [-9, 14, -6])
    m.box('blanket_hang', [-10, 12, -12], [-9, 15, 0], 'enamel2', 'blanket', part='blanket')
    m.box('blanket_lip', [-11, 15, -12], [-9, 16, 0], 'enamel2', 'blanket', part='blanket')

    # ---- locators -----------------------------------------------------------------------------------------------
    m.locator('valve', [0, 22, -1])          # the hose's click, the upkeep drip (WIDE valve top; Compact 20.5)
    m.locator('crest', [0, 28, 5])           # the breath's sounds (front of the crest at rest)
    m.locator('lap', [0, 7, -10])            # the blanket's flump
    m.locator('bag', [11, 17, -1.5])         # under the bag (the refill gurgle)
    m.locator('terminal', [-13, 6, 0])       # the charge spark (if any)

    # ---- the jar: BER anchor; and stand-ins (review only, materials for the injected real-size jar) ----------
    m.anchor('jar', list(ANCHOR), 'JarRenderer: the docked head in its jar, base centre here, facing -Z, scale 1.0; '
             'WIDE or COMPACT by the synced compact flag')
    m.group('standin_mats', None, hidden=True)
    for i, (mat, n) in enumerate((('steel', 2), ('stand', 2), ('glass', 8), ('brass', 2), ('saline', 2))):
        m.box('si_mat_%s' % mat, [20 + 10 * i, 0, 20], [20 + 10 * i + n, n, 20 + n], mat, 'standin_mats')

    # ---- cell shapes (Java cell coordinates, kept inside the cell in x and z) ----------------------------------
    m.shape('lower', (0, 0, 0, 16, 16, 16))       # seat, arms, wings, the jar body: a click anywhere answers
    m.shape('upper', (0, 0, 13, 16, 12, 16))      # back rail, stiles' tops, crest (z 5..10 -> 13..16)
    m.shape('upper', (1, 0, 1, 15, 8, 15))        # the jar's lid, valve and the laid hose
    m.shape('upper', (9, 0, 9, 13, 12, 13))       # collar and the parked hose
    return m


# ---------------------------------------------------------------------------------------------------------------------
# Per-pixel face check (the Mixer's): a face pixel is hidden when any opaque box covers it.
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
# Clear glass placeholder (the Mixer's): frame and streaks opaque, the rest clear (cutout).
# ---------------------------------------------------------------------------------------------------------------------
GLASS_FRAME = (0xa8, 0xd0, 0xd9, 255)
GLASS_STREAK = (0xd0, 0xea, 0xe9, 255)


def clear_glass(d, png, names):
    from PIL import Image
    im = Image.open(png).convert('RGBA')
    px = im.load()
    for e in d['elements']:
        if e.get('type') != 'cube' or e['name'] not in names:
            continue
        for fname, face in e['faces'].items():
            u0, v0, u1, v1 = face['uv']
            xa, xb = sorted((int(u0), int(u1)))
            ya, yb = sorted((int(v0), int(v1)))
            for y in range(ya, yb):
                for x in range(xa, xb):
                    edge = x in (xa, xb - 1) or y in (ya, yb - 1)
                    px[x, y] = GLASS_FRAME if edge else (0, 0, 0, 0)
            if xb - xa >= 6 and yb - ya >= 6 and not e['name'].startswith('si_'):
                for (x, y) in [(xa + 2, ya + 1), (xa + 1, ya + 2), (xa + 3, ya + 1), (xb - 3, yb - 2), (xb - 2, yb - 3)]:
                    px[x, y] = GLASS_STREAK
    im.save(png)


# ---------------------------------------------------------------------------------------------------------------------
# The stand-in jar at its real (fractional) size, injected after the kit's checks (which want whole pixels). Each
# face maps onto one face of a material swatch cube in `standin_mats`. Never exported.
# ---------------------------------------------------------------------------------------------------------------------
def jar_boxes(compact):
    ax, ay, az = ANCHOR
    if compact:
        w, base, glass, gw, lid, valve, vw, pipe, pipey, light, head = (5.5, (0, 1), (1, 12), 5, (12, 13), (12.5, 14.5),
                                                                       1.5, (5, 6), (3, 9), (5, 6, 1, 2), (1.2, 9.2))
    else:
        w, base, glass, gw, lid, valve, vw, pipe, pipey, light, head = (7.15, (0, 1.1), (1.1, 13.2), 6.5, (13.2, 14.3),
                                                                        (12.65, 15.95), 1.95, (6.5, 7.8), (3.3, 9.9),
                                                                        (6.5, 7.8, 1.1, 2.2), (1.5, 9.5))
    pz = 0.65 if not compact else 0.5
    B = []
    B.append(('base', [-w, base[0], -w], [w, base[1], w], 'steel'))
    B.append(('lid', [-w, lid[0], -w], [w, lid[1], w], 'steel'))
    B.append(('valve', [-vw, valve[0], -vw], [vw, valve[1], vw], 'brass'))
    B.append(('pipe_r', [pipe[0], pipey[0], -pz], [pipe[1], pipey[1], pz], 'steel'))
    B.append(('pipe_l', [-pipe[1], pipey[0], -pz], [-pipe[0], pipey[1], pz], 'steel'))
    B.append(('light', [-1.3 if not compact else -1, light[2], light[0]], [1.3 if not compact else 1, light[3], light[1]],
              'saline'))
    B.append(('head', [-4, head[0], -4], [4, head[1], 4], 'stand'))
    B.append(('glass', [-gw, glass[0], -gw], [gw, glass[1], gw], 'glass'))
    out = []
    for n, f, t, mat in B:
        out.append((n, [f[0] + ax, f[1] + ay, f[2] + az], [t[0] + ax, t[1] + ay, t[2] + az], mat))
    return out


def inject_jar(d, m):
    els = {e['name']: e for e in d['elements'] if e.get('type') == 'cube'}
    swatch = {}
    for mat in ('steel', 'stand', 'glass', 'brass', 'saline'):
        swatch[mat] = els['si_mat_' + mat]['faces']
    for compact, gname in ((False, 'standin_jar'), (True, 'standin_jar_compact')):
        gu = kit.nid(m.name, 'group', gname)
        d['groups'].append({'uuid': gu, 'name': gname, 'origin': list(ANCHOR), 'rotation': [0, 0, 0], 'export': True,
                            'visibility': not compact, 'mirror_uv': False, 'reset': False, 'color': 0, 'autouv': 0,
                            'shade': True, 'locked': False})
        kids = []
        for n, f, t, mat in jar_boxes(compact):
            name = 'si_%s%s' % ('c_' if compact else '', n)
            u = kit.nid(m.name, 'cube', name)
            faces = {k: {'uv': list(swatch[mat][k]['uv']), 'texture': 0} for k in swatch[mat]}
            d['elements'].append({'uuid': u, 'name': name, 'origin': [(f[i] + t[i]) / 2 for i in range(3)], 'color': 0,
                                  'type': 'cube', 'from': [round(v, 3) for v in f], 'to': [round(v, 3) for v in t],
                                  'box_uv': False, 'faces': faces, 'rescale': False, 'locked': False,
                                  'light_emission': 0, 'render_order': 'default', 'allow_mirror_modeling': True,
                                  'autouv': 0, 'visibility': True})
            kids.append(u)
        d['outliner'].append({'uuid': gu, 'isOpen': False, 'children': kids})


def post(path, m):
    with open(path, encoding='utf-8') as fh:
        d = json.load(fh)
    png = os.path.splitext(path)[0] + '_placeholder.png'
    clear_glass(d, png, {c[0] for c in m.cubes if c[3] == 'glass'})
    with open(png, 'rb') as fh:
        d['textures'][0]['source'] = 'data:image/png;base64,' + base64.b64encode(fh.read()).decode()
    inject_jar(d, m)
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
    # UVs frozen against the painted build (the rule for any change after paint)
    m.write(PATH, freeze_uv=PATH if '--repack' not in sys.argv else None)
    post(PATH, m)
    print(kit.shapes_java(m))

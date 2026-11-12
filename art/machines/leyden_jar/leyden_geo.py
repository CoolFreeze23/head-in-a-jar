"""Leyden Jar (a Leyden jar crossed with a milk churn in a carrying cage): geometry. No bones, no renderer.

    python art/machines/leyden_jar/leyden_geo.py            # writes leyden_jar.bbmodel (level 4 shown) and the checks
    python art/machines/leyden_jar/leyden_geo.py --review   # also the review copies leyden_L0..L4 under machines-review

A fat jar foil-lined in copper, its upper band dark glass, a glass churn shoulder, a rubber stopper, a short brass rod
and a 4 px ball knob; a steel carrying cage of four corner straps (1 px clear of the jar's stepped corners), a collar
ring round the shoulder and a bail handle in two halves either side of the rod. Faces -Z; +X is the machine's right
(the viewer's left). 32 px: a top cell. Nothing is rotated, so the generator's cut at y 16 is clean.

The charge (blockstate `level` 0-4, hysteresis 3 %) shows as an amber fill standing in three recessed windows (front,
left, right), 4 px wide and 8 px tall, 1 px deep, over a dark back: level n fills 2n px. The fills are the groups
`static/state_level_1..4` (level 0 has no part: the windows are dark). The knob is its own element in
`static/state_knob` (one element, the texture swapped by `level`: plain brass for 0-3, the animated sparks for 4).
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
    sys.path.insert(0, 'build/machines-review/scratch-leyden_jar')
    import kit  # noqa: E402

PATH = os.path.join(HERE, 'leyden_jar.bbmodel')
REVIEW = 'build/machines-review'

# dark glass: the jar's glass above the foil, painted OPAQUE (the dark inside seen through it: frame and one streak,
# no clear pixels; cutout, nothing translucent). A placeholder material of this script only.
kit.MATERIALS['dglass'] = ('2b4247', 255)
if 'dglass' not in kit.ORDER:
    kit.ORDER.insert(kit.ORDER.index('glass'), 'dglass')
kit.MATERIALS['amber'] = ('f2a530', 255)       # the charge fill (A2; full-bright in game)
if 'amber' not in kit.ORDER:
    kit.ORDER.insert(kit.ORDER.index('lens'), 'amber')

FOIL = (2, 14)        # copper foil y
GLASS = (14, 20)      # dark glass band: the darkest mass, the lamp centred on its front
WIN = (3, 14)         # charge windows y (11 tall: four 2 px cells with 1 px dark gaps, a battery icon)
SEG = [(3, 5), (6, 8), (9, 11), (12, 14)]   # the four cells, bottom up; level n lights the bottom n
WIN_U = {'north': (-3, 3), 'east': (-3, 3), 'west': (-3, 3)}  # along the face (x on north, z on east/west): centred
# the lit cells are 1 px narrower each side than the window, so every cell sits in a dark frame


def P(m, name, f, t, mat, part, parent='static', **kw):
    return m.box(name, f, t, mat, parent, part=part, **kw)


def skin_with_window(m, face, y0, y1, mat, part, tag0=''):
    """The jar's 2 px outer skin on one face, built round a window hole (WIN_U x WIN) when the face has one."""
    lo, hi = WIN
    if face == 'north':
        span, plane = (-5, 5), (-6, -4)
    elif face == 'south':
        span, plane = (-5, 5), (4, 6)
    elif face == 'east':
        span, plane = (-4, 4), (4, 6)
    else:
        span, plane = (-4, 4), (-6, -4)

    def box(tag, u0, u1, v0, v1):
        if u1 <= u0 or v1 <= v0:
            return
        if face in ('north', 'south'):
            P(m, '%s%s_%s_%s' % (part, tag0, face, tag), [u0, v0, plane[0]], [u1, v1, plane[1]], mat, part)
        else:
            P(m, '%s%s_%s_%s' % (part, tag0, face, tag), [plane[0], v0, u0], [plane[1], v1, u1], mat, part)

    if face in WIN_U and mat == 'copper':
        u0, u1 = WIN_U[face]
        box('b', span[0], span[1], y0, lo)
        box('t', span[0], span[1], hi, y1)
        box('l', span[0], u0, lo, hi)
        box('r', u1, span[1], lo, hi)
    else:
        box('all', span[0], span[1], y0, y1)


def model(level_shown=4):
    m = kit.Model('leyden_jar', budget=0)

    # ---- base: a dark kick inset under a dark steel plate (the shadow line it stands on) ---------------------
    m.box('kick', [-6, 0, -6], [6, 1, 6], 'rubber')
    m.box('base', [-7, 1, -7], [7, 2, 7], 'dsteel')                    # 14x1x14 between the straps' feet

    # ---- the jar: a K2 plan (12x10 crossed with 10x12) built as a dark core and 2 px skins, so the windows are holes
    # 1 px deep with the dark core behind them. Copper foil y 2-12, dark glass y 12-14 ---------------------------
    m.box('core', [-4, FOIL[0], -4], [4, GLASS[1], 4], 'rubber', part='jar')
    for face in ('north', 'south', 'east', 'west'):
        skin_with_window(m, face, FOIL[0], FOIL[1], 'copper', 'jar')
        skin_with_window(m, face, GLASS[0], GLASS[1], 'dglass', 'jar', '_glass')
    # the K2 corner steps (B's ends beyond the side skins): 1x1 posts at x +-5..6, z +-4..5
    for tag, sx, sz in (('fl', 1, -1), ('fr', -1, -1), ('bl', 1, 1), ('br', -1, 1)):
        x0, x1 = sorted((5 * sx, 6 * sx))
        z0, z1 = sorted((4 * sz, 5 * sz))
        P(m, 'step_' + tag, [x0, FOIL[0], z0], [x1, FOIL[1], z1], 'copper', 'jar')
        P(m, 'step_glass_' + tag, [x0, GLASS[0], z0], [x1, GLASS[1], z1], 'dglass', 'jar')

    # ---- shoulder, stopper, rod, knob -------------------------------------------------------------------------
    m.box('shoulder', [-5, 20, -5], [5, 22, 5], 'dglass', part='jar')    # the churn shoulder, 1 px in
    m.box('stopper', [-3, 22, -3], [3, 24, 3], 'rubber')                 # a 6x2x6 neck
    m.box('rod', [-1, 24, -1], [1, 25, 1], 'brass')                      # 1 px above the stopper
    m.group('state_knob', 'static')
    # one 4x4x4 cube on the rod, nothing above it (rounded rows would make a cross on a stick from the front)
    m.box('knob', [-2, 25, -2], [2, 29, 2], 'brass', 'state_knob', part='knob')

    # ---- the cage: four corner straps 2x14x2 at 6..8 (1 px clear of the jar's corner steps), a collar ring round
    # the shoulder at y 14-16 (1 px clear of it), a bail handle: two uprights and two halves either side of the rod
    for tag, sx, sz in (('fl', 1, -1), ('fr', -1, -1), ('bl', 1, 1), ('br', -1, 1)):
        x0, x1 = sorted((6 * sx, 8 * sx))
        z0, z1 = sorted((6 * sz, 8 * sz))
        P(m, 'strap_' + tag, [x0, 0, z0], [x1, 20, z1], 'steel', 'cage')   # down to the floor: the cage's feet
    # side yokes only (no front or back bar: the shoulder and stopper stay in view from the front)
    P(m, 'ring_e', [6, 20, -8], [8, 22, 8], 'steel', 'cage')
    P(m, 'ring_w', [-8, 20, -8], [-6, 22, 8], 'steel', 'cage')
    # the bail closed OVER the knob (a handled lantern; a bar behind the rod would make the cross again)
    P(m, 'bail_up_e', [6, 22, -1], [8, 30, 1], 'steel', 'cage')
    P(m, 'bail_up_w', [-8, 22, -1], [-6, 30, 1], 'steel', 'cage')
    P(m, 'bail_top', [-8, 30, -1], [8, 32, 1], 'steel', 'cage')

    # ---- K3 lamp centred on the dark glass band's front, above the window -----------------------------------
    kit.lamp(m, 'north', -6, -2, 15)         # bezel x -2..2, y 15-19, z -7..-6; lens z -8..-7
    kit.makers_plate(m, 'south', 6, -2, 7)   # the back's one feature (a blank back reads as a slab)

    # ---- the charge: fills in the three windows, 1 px back from the skin's face over the dark core ------------
    for n in (1, 2, 3, 4):
        g = 'state_level_%d' % n
        m.group(g, 'static', hidden=(n != level_shown))
        for c, (y0, y1) in enumerate(SEG[:n]):
            u0, u1 = WIN_U['north']
            m.box('fill%d_n%d' % (n, c), [u0 + 1, y0, -5], [u1 - 1, y1, -4], 'amber', g, part='fill%d' % n)
            u0, u1 = WIN_U['east']
            m.box('fill%d_e%d' % (n, c), [4, y0, u0 + 1], [5, y1, u1 - 1], 'amber', g, part='fill%d' % n)
            m.box('fill%d_w%d' % (n, c), [-5, y0, u0 + 1], [-4, y1, u1 - 1], 'amber', g, part='fill%d' % n)

    # ---- locators ------------------------------------------------------------------------------------------
    m.locator('knob', [0, 29.5, 0])          # the full tick's spark (server particle) and the arc to the handle
    m.locator('handle', [0, 30, 0])
    m.locator('window', [0, 8, -6.5])

    # ---- cell shapes (Java cell coordinates) ----------------------------------------------------------------
    m.shape('lower', (0, 0, 0, 16, 16, 16))     # the straps and corner steps fill the cell's corners
    m.shape('upper', (2, 0, 2, 14, 4, 14))       # the glass band's top
    m.shape('upper', (6, 0, 0, 10, 3, 2))        # the lamp on the band's front
    m.shape('upper', (3, 4, 3, 13, 6, 13))       # shoulder
    m.shape('upper', (5, 6, 5, 11, 8, 11))       # stopper
    m.shape('upper', (6, 8, 6, 10, 13, 10))      # rod and knob
    m.shape('upper', (0, 0, 0, 2, 6, 16))        # the straps' tops and the collar's side yokes
    m.shape('upper', (14, 0, 0, 16, 6, 16))
    m.shape('upper', (0, 6, 7, 2, 16, 9))        # the bail
    m.shape('upper', (14, 6, 7, 16, 16, 9))
    m.shape('upper', (0, 14, 7, 16, 16, 9))
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


def write_review(level):
    """A review copy with one charge level shown (level 0: none)."""
    m = model(level_shown=level if level else 99)
    out = '%s/leyden_L%d.bbmodel' % (REVIEW, level)
    m.write(out, png='%s/scratch-leyden_jar/leyden_L%d_placeholder.png' % (REVIEW, level), check=False)
    return out


if __name__ == '__main__':
    bad = False
    for lv in (0, 1, 2, 3, 4):       # every level checked as shown
        mm = model(level_shown=lv if lv else 99)
        cop, flush = precise_check(mm)
        sys.stderr.write('level %d precise check: %d z-fights, %d flush\n' % (lv, len(cop), len(flush)))
        for x in cop:
            sys.stderr.write('  PRECISE Z-FIGHT %s\n' % x)
            bad = True
        for x in flush:
            sys.stderr.write('  PRECISE FLUSH %s\n' % x)
        mm.check()
    if bad:
        sys.exit('precise z-fights')
    m = model()
    m.write(PATH)
    print(kit.shapes_java(m))
    if '--review' in sys.argv:
        for lv in range(5):
            print(write_review(lv))

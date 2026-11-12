"""Tesla Node: geometry.

    python art/machines/tesla_node/tesla_geo.py      # writes tesla_node.bbmodel and its placeholder PNG
    python art/machines/tesla_node/tesla_anim.py     # then writes the clips into it (and the review copy)

A Tesla coil from the company catalogue: a dark plinth, an enamel drum base with the lamp in its front, a brass
collar, a short copper coil wound on a dark former, a rubber insulator collar, and floating 1 px above it the light
steel doughnut (the torus, crossed 14x4x10 / 10x4x14 round a 6x6 hole). On the doughnut rides the pointer: a dark
steel shoe on the ring with a short brass stalk standing straight up from it and a brass discharge ball 6 px across on top
(ball y 24-30, 5 px from the axis, never over the hole). At rest (unlinked) the pointer stands at the far side (+Z);
linked, code turns it about the centre so the ball stands on the side facing the partner node. 30 px, top block.

Bones (2):
    torus    pivot (0, 20, 0)  rotation CODE (rotY: the spin while sending, chased; never keyed)
                               position keyed (perk lift +2, link hop, send float +1.5, stop, refusal shake)
    pointer  pivot (0, 22, 0), child of torus
                               rotation CODE (rotY: the aim, chased; written as aim - spin since it rides the torus)
Faces -Z; +X is the machine's right.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
if not os.path.exists(os.path.join(HERE, '..', 'kit', 'kit.py')):          # worktree changed under us: scratch copy
    sys.path.insert(0, 'build/machines-review/scratch-tesla_node')
import kit  # noqa: E402

PATH = os.path.join(HERE, 'tesla_node.bbmodel')
REVIEW = 'build/machines-review/tesla_node_review.bbmodel'

TORUS_Y = (18, 22)       # the ring, 1 px above the rubber collar (16-17)
BALL_R = 5               # the ball's centre, px from the axis
BALL_Y = 27              # the ball's centre height at rest


def ring(m, name, y0, y1, big, small, h=3, parent='torus'):
    """A ring of six boxes: outline crossed big x small / small x big round a (2h)x(2h) hole; one part."""
    B, s_ = big // 2, small // 2
    for n, f, t in (('n', [-s_, y0, -B], [s_, y1, -h]), ('s', [-s_, y0, h], [s_, y1, B]),
                    ('w', [-B, y0, -s_], [-s_, y1, s_]), ('e', [s_, y0, -s_], [B, y1, s_]),
                    ('wi', [-s_, y0, -h], [-h, y1, h]), ('ei', [h, y0, -h], [s_, y1, h])):
        m.box(name + '_' + n, f, t, 'zinc', parent, part='torus')


def model():
    m = kit.Model('tesla_node', budget=2)

    # ---- a solid dark plinth 10x3x9 (a grounding dark mass, so it does not read as a box on two stubs), 1 px inside
    # both crossed boxes of the base ----
    m.box('plinth', [-5, 0, -4], [5, 3, 5], 'dsteel')

    # ---- the drum base: K2 casing 14 wide, 13 deep, 5 tall (front plane z -6 so the lamp's lens stays inside the
    # block). The lamp's bezel stands on the front face and rises 1 px above the base's top (a hood, never flush) ---
    kit.casing(m, 'base', -7, 7, -6, 7, 3, 8)
    kit.lamp(m, 'north', -6, -2, 5)                       # bezel y 5-9, lens y 6-8
    kit.makers_plate(m, 'east', 7, -2, 4)                  # on the right side, low

    # ---- the column (v2: 9 px from collar to ring): one brass collar 10x2x10, a copper coil of three turns 6x1x6 on
    # a 4x4 dark former, the rubber insulator collar 8x2x8, then the 1 px gap; a light terminal post (a rounded 4x4
    # cross) rises through the gap into the torus's hole to 1 px under its top, so the doughnut reads as a collar round
    # the coil's top and not a basin ----
    m.box('collar_lo', [-5, 8, -5], [5, 10, 5], 'brass')
    m.box('former', [-2, 10, -2], [2, 15, 2], 'dsteel', part='coil')
    for i, y in enumerate((10, 12, 14)):
        m.box('turn_%d' % i, [-3, y, -3], [3, y + 1, 3], 'copper', part='coil')
    # the rubber insulator collar, 2 px, and the 1 px gap above it: the machine's dark (8 wide, 3 tall, y 15-18)
    # seen from the front under the light doughnut
    m.box('collar_hi', [-4, 15, -4], [4, 17, 4], 'rubber')
    m.box('term_core', [-1, 17, -2], [1, 21, 2], 'zinc', part='terminal')
    m.box('term_w', [-2, 17, -1], [-1, 21, 1], 'zinc', part='terminal')
    m.box('term_e', [1, 17, -1], [2, 21, 1], 'zinc', part='terminal')

    # ---- the torus (bone): a doughnut in three bands round a 6x6 hole: the middle band (y 19-21) the crossed
    # 14x10 / 10x14 outline, the top and bottom bands (18-19, 21-22) the crossed 12x8 / 8x12 (a rounded section) ------
    m.bone('torus', [0, 20, 0], code=('rotation',))
    for band, (ya, yb, big, small) in (('lo', (18, 19, 12, 8)), ('mid', (19, 21, 14, 10)), ('hi', (21, 22, 12, 8))):
        ring(m, 'torus_' + band, ya, yb, big, small)

    # ---- the pointer (bone, child of the torus): a shoe riding the ring, the post, the ball ---------------------
    m.bone('pointer', [0, 22, 0], parent='torus', code=('rotation',))
    m.box('shoe', [-2, 22, 4], [2, 23, 7], 'dsteel', 'pointer', part='pointer')
    m.box('post', [-1, 23, 4], [1, 24, 6], 'brass', 'pointer', part='pointer')
    # the ball (v2: a symmetric voxel sphere 6 px across, centre (0, 27, 5)): a 4x6x4 core and four 1x4x4 slabs
    P = lambda n, f, t: m.box('ball_' + n, f, t, 'brass', 'pointer', part='pointer')  # noqa: E731
    P('core', [-2, 24, 3], [2, 30, 7])
    P('w', [-3, 25, 3], [-2, 29, 7])
    P('e', [2, 25, 3], [3, 29, 7])
    P('n', [-2, 25, 2], [2, 29, 3])
    P('s', [-2, 25, 7], [2, 29, 8])

    # ---- locators (static points; the ball's own position is computed from the yaw, see the notes) --------------
    m.locator('crown', [0, 22, 0])          # the torus's top centre over the hole: the link spark
    m.locator('ball_rest', [0, BALL_Y, BALL_R])   # the ball's centre at rest (yaw 0), for reference only
    m.locator('coil', [0, 13, -3])          # the coil's front (a hum, a brown-out fizz)

    # ---- cell shapes (Java cell coordinates) ------------------------------------------------------------------
    m.shape('lower', (1, 0, 1, 15, 9, 15))        # plinth, base, the lamp
    m.shape('lower', (3, 8, 3, 13, 16, 13))       # collar, coil
    m.shape('upper', (4, 0, 4, 12, 2, 12))        # the rubber collar and the gap
    m.shape('upper', (1, 2, 1, 15, 14, 15))       # the torus and the ball's whole circle (a big target for the wrench)
    return m


# ---------------------------------------------------------------------------------------------------------------------
# Per-pixel face check (the Mixer's, as the Cradle uses it): a face pixel is hidden when any opaque box covers it.
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


if __name__ == '__main__':
    m = model()
    cop, flush = precise_check(m)
    sys.stderr.write('precise check: %d z-fights, %d flush joints\n' % (len(cop), len(flush)))
    for x in cop + flush:
        sys.stderr.write('  PRECISE %s\n' % x)
    if cop:
        sys.exit('precise z-fights')
    m.write(PATH)
    print(kit.shapes_java(m))

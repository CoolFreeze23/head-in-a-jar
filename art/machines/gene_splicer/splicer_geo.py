"""Gene Splicer (the sewing machine that stitches genes): geometry.

    python art/machines/gene_splicer/splicer_geo.py     # writes gene_splicer.bbmodel and its placeholder PNG
    python art/machines/gene_splicer/splicer_anim.py    # then writes the clips into it (and the review copy)

A vintage hand-crank sewing machine on the black-topped K12 lab bench. Faces -Z. Seen from the front, as its operator
sees it: the needle head on the viewer's LEFT (+X, the machine's right) and the pillar with the big spoked handwheel on
the viewer's RIGHT (-X). The inverted L (pillar, arm, head) is the one big light shape; the throat under the arm is a
real hole; the bed and worktop under it are the dark. Three thread spools stand on the arm (one per gene; the thread
cube is tinted by gene category in game); a ribbon strand runs along the bed under the needle (`static/state_stitch`,
blockstate `stitch` 0-3); a stability sight glass sits on the arm's front (`static/state_stab`, blockstate `stab`
0-2); the serum cassette stands in a deck in front of the pillar and pops up when the splice is done.

Ten-block motion: the handwheel, 12 px across, open-spoked, with a crank handle whose grip sticks 2 px out of the
wheel's outer face: one turn per stitch (36 degrees a tick at full speed). From the side and the three-quarters the
spokes turn; from the front the grip circles up and down 8 px beyond the machine's right edge (the outline changes).

Vertical stack (px): worktop top 13; bed 13-14; strand 14-15; throat 14-20 (x -2..3); needle bar rest bottom 16; head 18-24;
arm 20-24; spool pin collars 24-25; spools 24-29 (flange 1, thread 3, flange 1). Handwheel centre (x -7, y 22, z 0),
radius 6 (y 16-28, z -5..7), rim x -9..-7; axle x -7..-5 into the pillar; crank grip out to x -11.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
import kit  # noqa: E402

PATH = os.path.join(HERE, 'gene_splicer.bbmodel')
REVIEW = 'build/machines-review/splicer_review.bbmodel'

WHEEL_C = (-8, 22, 1)       # handwheel centre: x the rim's middle, y, z
SPOOL_X = (-4, 1, 6)        # spool centres along x (spool_1 nearest the wheel, spool_3 over the head)
SPOOL_Z = 1                 # spool centre z (on the arm, z -2..3)
THREAD_MAT = ('copper', 'saline', 'yellow')   # review placeholders only: in game the thread is painted pale and tinted


def wheel(m):
    """The handwheel: an octagonal rim (2 px ring, 12 across), four recessed spokes, a hub, and a crank arm with a
    brass grip standing 3 px out of the outer face. All one part (no flush flags inside the wheel)."""
    cx, cy, cz = WHEEL_C
    m.bone('wheel', [cx, cy, cz])
    P = 'wheel'

    def b(name, z0, z1, y0, y1, x0=cx - 1, x1=cx + 1, mat='copper'):
        m.box('wheel_' + name, [x0, cy + y0, cz + z0], [x1, cy + y1, cz + z1], mat, P, part='wheel')
    # rim (x -9..-7: 1 px outside the block, clear of a turning spool_1's flange by 0.76 px)
    b('rim_t', -3, 3, 4, 6)
    b('rim_b', -3, 3, -6, -4)
    b('rim_f', -6, -4, -3, 3)
    b('rim_k', 4, 6, -3, 3)
    for tag, z0, y0 in (('ft', -5, 3), ('kt', 3, 3), ('fb', -5, -5), ('kb', 3, -5)):
        b('rim_' + tag, z0, z0 + 2, y0, y0 + 2)
    # spokes: the inner half of the rim's thickness (x -8..-7), so the rim stands 1 px proud of them outside
    b('spoke_h', -4, 4, -1, 1, cx, cx + 1, 'copper')
    b('spoke_u', -1, 1, 1, 4, cx, cx + 1, 'copper')
    b('spoke_d', -1, 1, -4, -1, cx, cx + 1, 'copper')
    # hub: 4x4 dark steel over the spokes' outer side, its face in the rim's outer plane (x -9..-8): v2 dark, so the
    # light crank arm is the one asymmetric stroke on the wheel's face (v1's big brass hub made it a porthole)
    b('hub', -2, 2, -2, 2, cx - 1, cx, 'dsteel')
    # axle into the pillar (x -7..-5)
    b('axle', -1, 1, -1, 1, cx + 1, cx + 3, 'steel')
    # crank (v2): a light 2 px arm 1 px proud of the outer face from the hub to radius 4, and
    # a brass grip 3x3 in section standing 3 px out (x -12..-9), its centre at radius 5.5: at rest its top is at y 29,
    # level with the spool tops, so it crosses the machine's top outline from the front and back on every turn
    b('crank', -1, 1, 2, 4, cx - 2, cx - 1, 'enamel')
    b('grip', -1, 2, 4, 7, cx - 4, cx - 1, 'brass')
    m.locator('wheel', [cx - 1, cy, cz], None)


def spools(m):
    """Three spools on the arm, each on a static steel collar: flange 4x1x4 brass, thread 2x3x2, flange 4x1x4.
    Bones spool_1..3, pivot at the bottom centre; rotY is code (thread paying out), posY is keyed (pick, refusal).
    Cube order in each bone (for the game's tint): 0 flange_lo, 1 thread, 2 flange_hi."""
    for i, (x, tm) in enumerate(zip(SPOOL_X, THREAD_MAT), 1):
        n = 'spool_%d' % i
        z = SPOOL_Z
        m.bone(n, [x, 24, z], code=('rotation',))
        # v2: plain square flanges (v1's cut corners read as a frilly crown). A turning flange's corner reaches 2.83 px
        # from the pin: spool_1 reaches x -6.83, still clear of the handwheel (x <= -7); only one spool turns at a time
        m.box(n + '_flange_lo', [x - 2, 24, z - 2], [x + 2, 25, z + 2], 'brass', n, part=n)
        m.box(n + '_thread', [x - 1, 25, z - 1], [x + 1, 28, z + 1], tm, n, part=n)
        m.box(n + '_flange_hi', [x - 2, 28, z - 2], [x + 2, 29, z + 2], 'brass', n, part=n)
        m.locator('spool_%d' % i, [x, 27, z - 2], None)


def model():
    m = kit.Model('gene_splicer', budget=8)

    # ---- the lower half: the K12 bench (open shelf, K5 drawer, K3 lamp) ------------------------------------------
    drawer_spec = kit.bench(m)

    # ---- the collection flag (K9's idea, longer: v2). A steel arm 1x2x11 lies along the
    # bench's +X side against the worktop's edge (x 8..9, y 10-12, z -7..4), hinged at its back end; a yellow plate 1x4x4
    # 1 px proud at its front end. Up (rotX +90, the kit's sign) the arm stands 11 px tall beside the head and the
    # plate's top reaches y 22, beside the machine's arm, seen from the front and the side.
    m.bone('flag', [8, 11, 4])
    m.box('flag_arm', [8, 10, -7], [9, 12, 4], 'steel', 'flag', part='flag')
    m.box('flag_plate', [9, 9, -7], [10, 13, -3], 'yellow', 'flag', part='flag')
    flag_spec = ('flag', 'rotation', [90, 0, 0])
    # recessed panels on the bench's two plain sides (v2): a 1 px frame, 1 px proud, round
    # the cabinet end's outer face (z -5..5, y 2-9; under the flag's arm); the end's face inside it is the panel, 1 px back
    for face, plane in (('west', -7), ('east', 7)):
        kit.rim(m, 'bench_side_%s' % face[0], face, plane, -5, 5, 2, 9)

    # ---- the bed: a dark cast plate on the worktop (the throat's floor) ------------------------------------------
    m.box('bed', [-6, 13, -5], [7, 14, 5], 'dsteel')

    # ---- the body: pillar, arm and head (one enamel part: the inverted L) ------------------------------------------
    m.box('pillar', [-5, 14, -2], [-2, 20, 4], 'enamel', part='body')
    m.box('arm', [-5, 20, -2], [3, 24, 4], 'enamel', part='body')
    m.box('head', [3, 18, -4], [7, 24, 4], 'enamel', part='body')

    # ---- the stability sight glass on the arm's front, over the throat (blockstate stab 0-2: the bubbles) -------
    m.group('state_stab', 'static')
    m.box('stab_glass', [-1, 21, -3], [3, 23, -2], 'gauge', 'state_stab')

    # ---- the strand: a ribbon along the bed under the needle (blockstate stitch 0-3: the stitched stages) --------
    m.group('state_stitch', 'static')
    m.box('strand', [-2, 14, -1], [6, 15, 1], 'zinc', 'state_stitch')

    # ---- the needle bar and the take-up lever, one bone (v2). On a real machine both ride
    # the main shaft: the lever rises as the needle rises. The stroke is 6 px. At rest (top of the stroke) the 2x2x2
    # needle bar is hidden in the head (y 20-22) and the lever's tab, 4 wide and 2 tall on a 2 px neck 2 px proud of
    # the head's front, stands at y 23-25, its top 1 px over the head: it breaks the top outline. At the bottom of the
    # stroke the bar reaches through the strand to the bed (y 14-16, 2 px of it under the head, the needle point) and
    # the tab is at y 17-19, in the open in front of the head.
    m.bone('needle', [5, 20, -1])
    m.box('needle_bar', [4, 20, -1], [6, 22, 1], 'steel', 'needle', part='needle')
    m.box('needle_neck', [4, 23, -5], [6, 25, -4], 'steel', 'needle', part='needle')
    m.box('needle_lever', [3, 23, -6], [7, 25, -5], 'brass', 'needle', part='needle')
    m.locator('needle', [5, 15, 0], None)

    # ---- the cassette deck on the worktop's front edge, in front of the pillar (v2: lowered, the third
    # item): a dark slab 2 tall with a slot and two brass guide posts at the slot's ends (y 15-21); the cassette
    # (4x5x2) stands in the slot, its top at y 19 at rest (the pillar shows above it), and pops up 6 px when done,
    # its top to y 25, over the arm's top line, still held between the posts ---------------------------------------
    m.box('deck', [-7, 13, -7], [1, 15, -2], 'dsteel')
    for tag, x0 in (('l', -6), ('r', -1)):
        m.box('deck_post_' + tag, [x0, 15, -6], [x0 + 1, 21, -4], 'brass', part='deck_posts')
    m.bone('cassette', [-3, 15, -4])
    m.box('cassette_body', [-5, 14, -5], [-1, 19, -3], 'steel', 'cassette', part='cassette')
    m.box('cassette_window', [-4, 16, -6], [-2, 18, -5], 'sample', 'cassette', part='cassette')
    m.locator('cassette', [-3, 20, -6], None)

    wheel(m)
    spools(m)

    # ---- locators ----------------------------------------------------------------------------------------------
    m.locator('strand', [1, 15, 0], None)

    # ---- contents (BER item anchors; stand-ins never exported) -------------------------------------------------
    note = 'FIXED, standing upright facing -Z, scale 0.25, bottom centre on the worktop'
    # the supplies stand on the worktop's back strip (z 5..8, behind the bed), out of the throat's sightline: the
    # catalyst behind the head, the Bio-Gel behind the pillar. The bottle is not drawn: it becomes the cassette.
    m.anchor('catalyst', [6, 13, 6], 'catalyst (slot 0), ' + note, standin=((3, 4, 1), 'standi'))
    m.anchor('gel', [-4, 13, 6], 'Bio-Gel (slot 1), ' + note, standin=((3, 4, 1), 'stand'))

    # ---- the cell shapes (Java cell coordinates; upper = the machine_top cell) -------------------------------
    m.shape('lower', (0, 0, 0, 16, 13, 16))      # the bench
    m.shape('lower', (1, 13, 1, 15, 16, 13))     # bed, deck, strand, supplies
    m.shape('upper', (2, 0, 3, 15, 8, 12))       # pillar, arm, head, deck, cassette, needle
    m.shape('upper', (0, 0, 2, 3, 12, 14))       # the handwheel (inside the cell; the grip overhangs)
    m.shape('upper', (2, 8, 6, 16, 13, 12))      # spools
    m.shape('upper', (2, 0, 1, 8, 9, 4))         # the cassette and its posts (popped up to 25)
    m.shape('upper', (11, 7, 2, 15, 9, 4))       # the take-up lever at rest
    return m, drawer_spec, flag_spec


if __name__ == '__main__':
    m, _, _ = model()
    m.write(PATH)
    print(kit.shapes_java(m))

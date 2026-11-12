"""Cerebral Array (design B, the Board Room Stand): geometry.

    python art/machines/cerebral_array/array_geo.py      # writes cerebral_array.bbmodel and its placeholder PNG
    python art/machines/cerebral_array/array_anim.py     # then writes the clips into it (and the review copies)

A jury box of three raked rows, three seats a row, each seat a size-6 specimen jar (K13: ring 6x1x6, glass 4x4x4, lid
6x1x6) standing in the box's dark bay; the board's bench across the top with the chairman's gavel, the tea urn of broth
and the lamp. Faces -Z. +X is the machine's right (the viewer's left).

Rows (front to back): jar front z A[r] = -8, -4, 0; floor top y H[r] = 3, 11, 19. The rows are 4 px apart in depth and
8 px in height, so each row's floor slab runs out over the back half of the row below (2 px over the lid, 1 px clear):
the lower seat sits in a recessed bay whose back is rubber, and only the front 3 px of each lid is in the open (that is
where the voting card lies). In front of each row an enamel front (the jury box's rail, 3 px tall, hiding the ring and
the slab's edge) with two posts between the seats; the jars' rings meet side by side (18 wide, 1 px proud of the 16-wide
stand each side, O6).

The seats are numbered 1-9 as the ripple runs: front row first, from the viewer's left (+X) to the right, then the
middle and back rows. Seat i: row (i-1)//3, x = 6, 0, -6.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
_kit = os.path.join(HERE, '..', 'kit')
if not os.path.exists(os.path.join(_kit, 'kit.py')):       # the worktree checkout fallback (a scratch copy)
    _kit = 'build/machines-review/scratch-cerebral_array/kitcopy'
sys.path.insert(0, _kit)
import kit  # noqa: E402

PATH = os.path.join(HERE, 'cerebral_array.bbmodel')
REVIEW = 'build/machines-review/cerebral_array_review.bbmodel'
REVIEW_VOTE = 'build/machines-review/cerebral_array_review_vote.bbmodel'

A = [-8, -4, 0]          # jar front z, rows 0-2
H = [3, 11, 19]          # floor top y (ring bottom), rows 0-2
SEAT_X = [6, 0, -6]      # left to right as the viewer sees it
BENCH_Y = 26             # the bench slab y 26-27 (1 px over the back row's lids)
TOP = 27                 # the bench top
HEAD_PX = 2.8            # the head's drawn size (largest horizontal extent), px
NAYS_REVIEW = (2, 6, 7)  # seats shown red in the vote review copy (the rest green)

GAVEL_PIVOT = (6, 28.5, 5.5)
MAT = {'rail': 'rubber', 'carcass': 'enamel'}
kit.MATERIALS.setdefault('paper', ('efe6d2', 255))     # the voting cards' pale card (tinted green or red in game)
if 'paper' not in kit.ORDER:
    kit.ORDER.insert(kit.ORDER.index('glass'), 'paper')


def seats():
    for i in range(9):
        r, c = divmod(i, 3)
        yield i + 1, r, SEAT_X[c]


def jar(m, r, cx):
    """K13 size 6: brass ring 6x1x6, glass 4x4x4 inset 1, brass lid 6x1x6 (6 tall, no taller than wide)."""
    h, a = H[r], A[r]
    p = 'jar_%d%s' % (r, {6: 'l', 0: 'm', -6: 'r'}[cx])
    m.box(p + '_ring', [cx - 3, h, a], [cx + 3, h + 1, a + 6], 'brass', part=p)
    m.box(p + '_glass', [cx - 2, h + 1, a + 1], [cx + 2, h + 5, a + 5], 'glass', part=p)
    m.box(p + '_lid', [cx - 3, h + 5, a], [cx + 3, h + 6, a + 6], 'brass', part=p)


def model(card_mats=None):
    m = kit.Model('cerebral_array', budget=10)

    # ---- the stand ------------------------------------------------------------------------------------------------
    # recessed rubber kick under everything (the base's dark), the front row's floor on it
    m.box('kick', [-7, 0, -6], [7, 2, 7], 'rubber')
    m.box('floor_0', [-8, 2, A[0] + 1], [8, 3, 8], MAT['carcass'], part='carcass')
    for r in range(3):
        h, a = H[r], A[r]
        top = H[r + 1] - 1 if r < 2 else BENCH_Y          # the ceiling of this row's bay
        # the jury box front: rail 3 px tall (covers the slab edge, the rings and the glass's foot row), two posts
        # between the seats up to 1 px over the lids (so the lid band is broken into three)
        m.box('rail_%d' % r, [-8, h - 1, a - 1], [8, h + 1, a], MAT['rail'], part='front_%d' % r)
        m.box('railcap_%d' % r, [-8, h + 1, a - 1], [8, h + 2, a], 'brass', part='front_%d' % r)

        # the bay: rubber back 1 px behind the jars, the carcass filler behind it, the next row's slab over it
        m.box('bay_%d' % r, [-8, h, a + 6], [8, top, a + 7], 'rubber', part='carcass')
        m.box('fill_%d' % r, [-8, h, a + 7], [8, top, 8], MAT['carcass'], part='carcass')
        if r < 2:
            m.box('slab_%d' % (r + 1), [-8, H[r + 1] - 1, A[r + 1] + 1], [8, H[r + 1], 8], MAT['carcass'], part='carcass')
        for cx in SEAT_X:
            jar(m, r, cx)

    # the stringers: a brass pilaster up each side at the front of the first two rows' risers (1 px behind the jar,
    # 1 px proud), so the sides step up with the seats
    for r in (0, 1):
        for sx in (1, -1):
            m.box('stringer_%d%s' % (r, 'l' if sx > 0 else 'r'), [8 * sx, H[r], A[r] + 7], [9 * sx, H[r] + 7, A[r] + 8],
                  'brass')

    # ---- the bench across the top (the board's table): a slab over the back row's bays ------------------------------
    m.box('bench', [-8, BENCH_Y, 3], [8, TOP, 8], MAT['carcass'], part='carcass')
    # the desk's front, 1 px over the bench top in front of the gavel (y 26-28 with the slab), so the
    # top reads as the chairman's desk and the gavel strikes behind a real edge
    m.box('desk', [-3, TOP, 3], [3, TOP + 1, 4], MAT['carcass'], part='carcass')

    # the tea urn of broth (viewer's right), solid: a brass body 4x5x3 with a 2x3 window recessed 1 px in
    # its front; the window's back is the broth face, one element in state_broth (five textures, levels 0-4, as the
    # K4 gauge's face)
    m.box('urn_body', [-7, TOP, 6], [-3, 32, 7], 'brass', part='urn')
    m.box('urn_l', [-7, TOP, 4], [-6, 32, 6], 'brass', part='urn')
    m.box('urn_r', [-4, TOP, 4], [-3, 32, 6], 'brass', part='urn')
    m.box('urn_foot', [-6, TOP, 4], [-4, 28, 6], 'brass', part='urn')
    m.box('urn_top', [-6, 31, 4], [-4, 32, 6], 'brass', part='urn')
    m.group('state_broth', 'static')
    m.box('broth_face', [-6, 28, 5], [-4, 31, 6], 'saline', 'state_broth')

    # the gavel: a brass bracket of two cheeks on the bench (viewer's left), the lamp on its front cheek, the gavel
    # (handle and a 4x2x3 head, v2) pivoting between the cheeks; at rest the head lies on a dark sound block at the
    # centre, its whole height over the desk's edge
    m.box('cheek_f', [5, TOP, 4], [7, 31, 5], 'brass', part='bracket')
    m.box('cheek_b', [5, TOP, 6], [7, 31, 7], 'brass', part='bracket')
    kit.lamp(m, 'north', 4, 4, 28)                 # bezel x 4..8, y 28-32, z 3..4 (hung on the cheek); lens z 2..3
    m.box('sound_block', [-3, TOP, 5], [1, TOP + 1, 6], 'dsteel')
    m.bone('gavel', list(GAVEL_PIVOT))
    m.box('gavel_handle', [2, 28, 5], [6, 29, 6], 'steel', 'gavel', part='gavel')
    m.box('gavel_head', [-2, 28, 4], [2, 30, 7], 'dsteel', 'gavel', part='gavel')
    m.locator('gavel', [0, 28, 3.5], None)         # the strike (a puff of dust off the desk's edge)
    m.locator('urn', [-5, 32, 5.5], None)          # steam while fed

    # ---- the voting cards: one bone per seat, a 4x1x3 card lying on the lid's open front, hinged at its front edge --
    for i, r, cx in seats():
        b = 'card_%d' % i
        lid = H[r] + 6
        m.bone(b, [cx, lid, A[r]])
        mat = (card_mats or {}).get(i, 'paper')
        m.box(b + '_card', [cx - 2, lid, A[r]], [cx + 2, lid + 1, A[r] + 3], mat, b, part=b)
        m.locator('seat_%d' % i, [cx, H[r] + 5, A[r] + 3], None)      # above the head (eureka glyph, speaker)

    # ---- the maker's plate on the back, low ----------------------------------------------------------------------
    # the back gets one framed field (a 1 px rim, 1 px proud, x -6..6, y 4-22; the field inside is
    # painted rubber dark), the maker's plate in its lower part
    kit.rim(m, 'back_rim', 'south', 8, -6, 6, 4, 22, 'enamel2', part='back_rim')
    kit.makers_plate(m, 'south', 8, -2, 6)

    # ---- the heads: drawn by the head code at each seat (stand-ins, never exported) --------------------------------
    for i, r, cx in seats():
        m.anchor('head_%d' % i, [cx, H[r] + 1.25, A[r] + 3],
                 'head slot %d: the mob head part only (no jar), bottom centre here, facing -Z, %.1f px across' % (
                     i - 1, HEAD_PX), standin=((3, 3, 3), 'stand'))

    # ---- the cell shapes (Java cell coordinates; upper = the machine_top cell) ------------------------------------
    m.shape('lower', (0, 0, 0, 16, 10, 16))       # the front row and everything behind it to y 10
    m.shape('lower', (0, 10, 3, 16, 16, 16))      # the middle row (rail at z -5) and behind
    m.shape('upper', (0, 0, 3, 16, 2, 16))        # the middle row's posts and lids (to y 18)
    m.shape('upper', (0, 2, 7, 16, 10, 16))       # the back row (rail at z -1) to the bench
    m.shape('upper', (0, 10, 10, 16, 16, 16))     # the bench, urn, gavel and lamp (lens at z 2)
    return m


def paint_glass(m, offs, png):
    """Cutout clear glass on the placeholder (the palette's rule): a jar face keeps its two side columns and its foot
    row (a 2 px window, PAINT 'small canisters'); a face 2 px wide or less keeps one glint pixel; up/down faces a frame."""
    from PIL import Image
    im = Image.open(png).convert('RGBA')
    px = im.load()
    lit, shade = (196, 232, 226, 255), (150, 200, 194, 255)
    for name, f, t, mat, parent, kw in m.cubes:
        if mat != 'glass':
            continue
        w, h, d = [max(1, round(abs(t[i] - f[i]))) for i in range(3)]
        ox, oy = offs[name]
        sides = [(ox + d, oy + d, w, h), (ox, oy + d, d, h), (ox + 2 * d + w, oy + d, w, h), (ox + d + w, oy + d, d, h)]
        caps = [(ox + d, oy, w, d), (ox + d + w, oy, w, d)]
        for (x0, y0, fw, fh) in sides + caps:
            for yy in range(y0, y0 + fh):
                for xx in range(x0, x0 + fw):
                    px[xx, yy] = (0, 0, 0, 0)
        for (x0, y0, fw, fh) in sides:
            if fw <= 2:
                px[x0, y0] = lit
                continue
            for yy in range(y0, y0 + fh):
                px[x0, yy] = lit
                px[x0 + fw - 1, yy] = shade
            for xx in range(x0, x0 + fw):
                px[xx, y0 + fh - 1] = shade
        for (x0, y0, fw, fh) in caps:
            for xx in range(x0, x0 + fw):
                px[xx, y0] = lit
                px[xx, y0 + fh - 1] = shade
            for yy in range(y0, y0 + fh):
                px[x0, yy] = lit
                px[x0 + fw - 1, yy] = shade
    im.save(png)


def write(m, path, check=True):
    """kit.Model.write, with the clear-glass placeholder painted in before the texture is embedded."""
    if check:
        m.check()
    offs, regions, tex_h = kit.pack(m.cubes, m.tex_w)
    png = os.path.splitext(path)[0] + '_placeholder.png'
    kit.draw(m.cubes, offs, png, m.tex_w, tex_h)
    paint_glass(m, offs, png)
    kit.build(path, m, offs, png, (m.tex_w, tex_h), True)
    sys.stderr.write('%s: %d bones (budget %d), %d cubes (%d drawn), %d locators, sheet %dx%d\n' % (
        m.name, len(m.bone_names()), m.budget, len(m.cubes), len(m.drawn()), len(m.locators), m.tex_w, tex_h))
    return path


if __name__ == '__main__':
    m = model()
    write(m, PATH)
    print(kit.shapes_java(m))

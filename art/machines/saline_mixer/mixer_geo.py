"""Saline Mixer (the Soda Fountain): geometry, from the machine's design with the departures listed in the model notes.

    python art/machines/saline_mixer/mixer_geo.py     # writes saline_mixer.bbmodel and its placeholder PNG
    python art/machines/saline_mixer/mixer_anim.py    # then writes the clips into it

A drugstore soda fountain crossed with a lab mixing tank: an enamel counter on a dark plinth with a rubber-lined bottle
niche and a brass drip tray at the front, the lamp and the water gauge beside it; a tall glass tank of saline on a
brass ring with a steel paddle in it; a brass lid with three glass canisters (sugar, kelp, redstone) standing on it like
a cruet set, their brass caps the bones that bow as each ingredient is dosed; a tap lever in a brass housing on the
right side, the collection flag under it, the intake port on the left side. Faces -Z; +X is the machine's right.
32 px tall: the shared hidden top cell above it.

The kit's flush check treats a face as hidden only when one box covers all of it, so it reports joints along edges
that other boxes cover (the niche, the port, the counter pieces round them). `precise_check` below looks per pixel:
a face pixel is hidden when any opaque box covers it, and a joint is flush only where two visible pixels of different
parts meet. Both run; the report lists the kit's extra warnings and why each is covered.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
import kit  # noqa: E402

PATH = os.path.join(HERE, 'saline_mixer.bbmodel')

SHOWN_LEVEL = 2          # the saline level drawn in the model (the others are hidden alternative groups)
LEVELS = {1: 3, 2: 5, 3: 7, 4: 9}   # saline heights in px above the tank floor (y 14); the glass holds 11
TAP_LEAN = 10            # the tap's rest lean, degrees, top back (away from the pull)


def C(m, name, f, t, mat='enamel'):
    """A piece of the counter (one part, so its own seams are never flagged)."""
    return m.box(name, f, t, mat, part='counter')


def model():
    m = kit.Model('saline_mixer', budget=6)

    # ---- K1 plinth and feet: plinth 10x1x10, 1 px inside the counter all round; feet inset 1 px ----------------
    kit.plinth(m, -5, 5, -5, 5)

    # ---- the counter, y 3-14 (K2 construction at the back: box A x -6..6 z -6..6 crossed with box B x -7..7
    # z -5..5; the FRONT corners are square, x -7..7 from z -6, so the front holds a 7 px niche and the lamp with
    # 1 px clear each side). Built from pieces because the niche and the side port are real holes.
    C(m, 'fs_l', [-7, 3, -6], [-6, 14, -5])            # front skin, left of the niche
    C(m, 'fs_r', [1, 3, -6], [7, 14, -5])              # front skin carrying the lamp and the sight glass
    C(m, 'fs_sill', [-6, 3, -6], [1, 4, -5])
    C(m, 'fs_head', [-6, 12, -6], [1, 14, -5])
    C(m, 'k_l', [-7, 3, -5], [-6, 14, -2])             # core beside the niche
    C(m, 'k_r', [1, 3, -5], [7, 14, -2])
    C(m, 'k_sill', [-6, 3, -5], [1, 4, -2])
    C(m, 'k_head', [-6, 12, -5], [1, 14, -2])
    C(m, 'k_back', [-6, 3, -2], [6, 14, 5])            # core behind the niche
    C(m, 'wing_r', [6, 3, -2], [7, 14, 5])             # B's right wing behind the front block
    # B's left wing, cut round the side port's mouth (z -2..2, y 5..9)
    C(m, 'ws_lo', [-7, 3, -2], [-6, 5, 5])
    C(m, 'ws_hi', [-7, 9, -2], [-6, 14, 5])
    C(m, 'ws_back', [-7, 5, 2], [-6, 9, 5])
    C(m, 'bs', [-6, 3, 5], [6, 14, 6])                 # back skin (A's back strip)

    # ---- the bottle niche: the front's one focal feature and strong dark. Opening 7 wide (x -6..1), y 4-12;
    # rubber back, sides and ceiling set 1 px in from the front (a 1 px enamel reveal), 3 px deep; a brass drip
    # tray for the floor standing 1 px proud of the counter front; the spout from the ceiling.
    m.box('niche_back', [-6, 4, -3], [1, 12, -2], 'rubber', part='niche')
    m.box('niche_l', [-6, 5, -5], [-5, 11, -3], 'rubber', part='niche')
    m.box('niche_r', [0, 5, -5], [1, 11, -3], 'rubber', part='niche')
    m.box('niche_ceil', [-6, 11, -5], [1, 12, -3], 'rubber', part='niche')
    m.box('tray', [-6, 4, -7], [1, 5, -3], 'brass')
    m.box('spout', [-3, 9, -4], [-1, 11, -2], 'brass')

    # ---- the front's right: the K3 lamp, and under it the water level as a frameless 2x4 sight glass (the face is
    # the `water` blockstate part: rows show level 0-4); the K4 frame dropped so nothing competes with the niche
    kit.lamp(m, 'north', -6, 2, 9)
    m.group('state_water', 'static')
    m.box('water_face', [3, 4, -7], [5, 8, -6], 'gauge', 'state_water')

    # ---- the back: a raised 1 px enamel frame inset 1 px from the edges, the maker's plate (K7) in it ----------
    kit.rim(m, 'back_rim', 'south', 6, -5, 5, 4, 13, 'enamel')
    kit.makers_plate(m, 'south', 6, -2, 6, 4, 2)

    # ---- K6b side port, left side (frame 1 px out, mouth 1 px into the body at the counter's plane) ------------
    kit.side_port(m, 'west', -7, 0, 4, depth=1, prefix='port')

    # ---- the tank, the hero: glass 10x11x8 standing on the counter, four 2x2 brass posts at its corners (1 px
    # outside the glass on both faces, wrapping its corners) carrying the lid, which overhangs the posts 1 px: a
    # closed frame. The saline as alternative opaque levels, 3/5/7/9 px (level 1 already a third full).
    m.box('tank', [-5, 14, -4], [5, 25, 4], 'glass')
    for tag, x, z in [('fl', -6, -5), ('fr', 4, -5), ('bl', -6, 3), ('br', 4, 3)]:
        m.box('post_' + tag, [x, 14, z], [x + 2, 25, z + 2], 'brass', part='posts')
    for n in range(1, 5):
        g = 'state_saline_%d' % n
        m.group(g, 'static', hidden=(n != SHOWN_LEVEL))
        m.box('saline_%d' % n, [-4, 14, -3], [4, 14 + LEVELS[n], 3], 'saline', g)
    m.box('lid', [-7, 25, -6], [7, 27, 6], 'brass')

    # ---- the cruet set: one tall canister front-centre (sugar), two behind stepping down (kelp 3, redstone 2) ----
    CAN = {'s': (-2, -5, 4), 'k': (-5, 1, 3), 'r': (1, 1, 2)}     # (x0, z0, height): 4, 3, 2 px, no matched pair
    for tag, (x0, z0, h) in CAN.items():
        m.box('can_' + tag, [x0, 27, z0], [x0 + 4, 27 + h, z0 + 4], 'glass', part='can_' + tag)

    # ---- bones ------------------------------------------------------------------------------------------------
    # paddle: code rotY (accumulated). A 4x5x2 blade low, a 2 px shaft, a 6x2x2 crossbar at y 22-24 that turns
    # above every level a batch can run at (level 3 tops at 21; level 4 at 23 leaves 1 px), radius 3.16, clear of
    # the glass (4) and the posts (5).
    m.bone('paddle', [0, 25, 0], code=('rotation',))
    m.box('paddle_blade', [-2, 15, -1], [2, 20, 1], 'steel', 'paddle', part='paddle')
    m.box('paddle_shaft', [-1, 20, -1], [1, 22, 1], 'steel', 'paddle', part='paddle')
    m.box('paddle_bar', [-3, 22, -1], [3, 24, 1], 'steel', 'paddle', part='paddle')
    m.box('paddle_top', [-1, 24, -1], [1, 25, 1], 'steel', 'paddle', part='paddle')

    # caps: 4x1x4 brass on each canister, pivot at the cap's back edge, bottom. posY: the screen-open lift. rotX:
    # the dose (front edge down 20 degrees, the pepper-shaker flap) and the refusal (front edge chattering up).
    for tag, (x0, z0, h) in CAN.items():
        b = 'cap_' + tag
        m.bone(b, [x0 + 2, 27 + h, z0 + 4])
        m.box(b + '_lid', [x0, 27 + h, z0], [x0 + 4, 28 + h, z0 + 4], 'brass', b)

    # tap: an upright brass housing 4x6x4 on the right side at the front (x 7..11), a dark steel collar band on
    # top; the bone is a 2x2 stick (its root 1 px down in the collar) and a 4x2x4 rubber ball knob. The stick is
    # 1 px clear of the counter's side. Rest pose leans 10 degrees back (rotX +10), so the pull (forward, rotX
    # negative) starts from a lean.
    m.box('tap_housing', [7, 6, -7], [11, 12, -3], 'brass', part='tap_housing')
    m.box('tap_collar', [7, 12, -7], [11, 13, -3], 'dsteel', part='tap_housing')
    m.bone('tap', [9, 13, -5], rot=[-TAP_LEAN, 0, 0])   # outliner rotation: -X leans the top back (+Z); keyframes are the other sign
    m.box('tap_stick', [8, 12, -6], [10, 18, -4], 'brass', 'tap', part='tap')
    m.box('tap_knob', [7, 18, -7], [11, 20, -3], 'rubber', 'tap', part='tap')

    # flag (K9): on the rear half of the right side, low, apart from the tap. Up = rotX +90.
    flag_spec = kit.flag(m, 7, 4, 4, side='east')

    # ---- locators ---------------------------------------------------------------------------------------------
    # under each canister, below the crossbar's layer (y 22-24) and above level 3 (21); only the 2 px shaft turns
    # at this height, so the powder falls clear of the paddle
    m.locator('dose_s', [0, 21.5, -3])
    m.locator('dose_k', [-3, 21.5, 3])
    m.locator('dose_r', [3, 21.5, 3])
    m.locator('spout', [-2, 9, -3.5])          # the spout's mouth
    m.locator('tank', [0, 18, 0])              # the finish bubble and the hum

    # ---- contents (BER item anchors, stand-ins never exported) ------------------------------------------------
    ing = 'the slot item, FIXED, standing upright facing -Z, scale 0.25, while the slot has any'
    m.anchor('ing_s', [0, 27.5, -3], ing + ' (slot 0, sugar)', standin=((2, 2, 1), 'standi'))
    m.anchor('ing_k', [-3, 27.5, 3], ing + ' (slot 1, kelp or dried kelp)', standin=((2, 2, 1), 'standi'))
    m.anchor('ing_r', [3, 27.5, 3], ing.replace('0.25', '0.125') + ' (slot 2, redstone; its canister is 2 px)',
             standin=((2, 1, 1), 'standi'))
    bot = 'FIXED, upright facing -Z, scale 0.25 (the niche is 6 px tall)'
    m.group('standin_out', None, hidden=True)
    m.anchor('bottle_in', [-2.5, 5, -3.5], 'an empty glass bottle while slot 3 has any (the last filled bottle '
             'while bottling); ' + bot)
    m.box('si_bottle_in', [-3.5, 5, -4], [-1.5, 9, -3], 'stand', 'standin_out')
    for i, (pos, n) in enumerate([([-4.5, 5, -6], 1), ([-0.5, 5, -6], 2), ([-2.5, 5, -6.5], 9)]):
        m.anchor('out_%d' % (i + 1), pos, 'a saline bottle while slot 4 has >= %d; %s' % (n, bot))
        x, y, z = pos
        m.box('si_out_%d' % (i + 1), [x - 1, y, z - 0.5], [x + 1, y + 4, z + 0.5], 'standi', 'standin_out')

    # ---- the cell shapes (Java cell coordinates; upper = the machine_top cell) --------------------------------
    m.shape('lower', (1, 0, 1, 15, 16, 15))            # counter, plinth; port, tray and housing within 1 px
    m.shape('upper', (1, 0, 2, 15, 11, 14))            # tank, posts and lid
    m.shape('upper', (3, 11, 3, 13, 16, 13))           # the three canisters and caps
    m.shape('upper', (15, 0, 1, 16, 5, 7))             # the tap's stick and knob above the counter (rest lean)
    m.flag_spec = flag_spec
    return m


# ---------------------------------------------------------------------------------------------------------------------
# Per-pixel face check (the kit's check counts a face as hidden only when a single box covers it whole)
# ---------------------------------------------------------------------------------------------------------------------
def _cells(face):
    ax, sg, pl, r = face
    return [(u, v) for u in range(int(round(r[0])), int(round(r[1]))) for v in range(int(round(r[2])), int(round(r[3])))]


def precise_check(m):
    drawn = m.drawn()
    occ = [c for c in drawn if c[3] not in ('glass', 'jglass')]
    vis = []
    for c in drawn:
        for face in kit._faces(c):
            ax, sg, pl, r = face
            o = [i for i in range(3) if i != ax]
            cells = set()
            for (u, v) in _cells(face):
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
# Clear glass placeholder (palette D shared `glass`: "frame and streaks opaque, the rest clear (cutout)"). The kit
# fills glass with t3 at alpha 96; in the headless renderer that hides every bone drawn behind it (the paddle), and
# the house rule for clear glass is cutout anyway. Applied to the sheet after the kit writes it.
# ---------------------------------------------------------------------------------------------------------------------
GLASS_FRAME = (0xa8, 0xd0, 0xd9, 255)    # g2
GLASS_STREAK = (0xd0, 0xea, 0xe9, 255)   # g3


def clear_glass(path, m):
    import base64
    import json
    from PIL import Image
    with open(path, encoding='utf-8') as fh:
        d = json.load(fh)
    png = os.path.splitext(path)[0] + '_placeholder.png'
    im = Image.open(png).convert('RGBA')
    px = im.load()
    names = {c[0] for c in m.cubes if c[3] == 'glass'}
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
            if xb - xa >= 6 and yb - ya >= 6:       # two short streaks in opposite corners
                for (x, y) in [(xa + 2, ya + 1), (xa + 1, ya + 2), (xa + 3, ya + 1),
                               (xb - 3, yb - 2), (xb - 2, yb - 3)]:
                    px[x, y] = GLASS_STREAK
    im.save(png)
    with open(png, 'rb') as fh:
        d['textures'][0]['source'] = 'data:image/png;base64,' + base64.b64encode(fh.read()).decode()
    with open(path, 'w', encoding='utf-8') as fh:
        json.dump(d, fh, indent=1)
    return sorted(names)


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
    print('clear glass placeholder on', clear_glass(PATH, m))
    print(kit.shapes_java(m))

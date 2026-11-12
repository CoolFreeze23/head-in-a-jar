"""Body Printer (the Photo Booth): geometry.

    python art/machines/body_printer/printer_geo.py     # writes body_printer.bbmodel and its placeholder PNG
    python art/machines/body_printer/printer_anim.py    # then writes the clips into it (and the review copies)

A coin-operated photo booth crossed with a 3D printer. An enamel booth the full width of the block on a dark plinth,
its whole front a glass door in a brass frame; a yellow marquee across the roof front with the lamp in its centre;
inside, a dark booth with a plum backdrop curtain bunched in the back corner. In front of the glass, outside, the print
bar (bone `ring`): a brass beam cantilevered from a sleeve that rides a dark steel column at the front-right corner,
lying on the ground at rest (it is the booth's doorstep); on it the print head (bone `carriage`), a copper block with
an amber lens that shuttles across the door. The bar climbs the door one pixel a squish while the head shuttles, and
the body is drawn by the renderer up to the bar's top; then the bar comes back down as the develop line; then the door
(bone `door`, hinged at the machine's left, -X) swings out and the body steps out over the bar.

The body (a PlayerModel at the entity's own 0.9375 scale, 15 px wide with its arms) does not fit square in a 14 px
interior: it stands turned 45 degrees (13.3 px across), chest to the front-right corner. Stand-in: `standin_body`.

Faces -Z; +X is the machine's right. 32 px tall: the shared hidden top cell above it.
"""
import base64
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
import kit  # noqa: E402

PATH = os.path.join(HERE, 'body_printer.bbmodel')
REVIEW_DIR = 'build/machines-review'

FLOOR = 3            # the booth floor (the plinth's top): the body's feet
BODY_SCALE = 0.9375  # HeadlessBodyRenderer.scale: the entity that spawns at 1,200
BODY_YAW = -45       # the stand-in group's outliner rotation (checked by render: chest to the front-right corner)
SHOWN_SALINE = 2     # the saline level drawn in the model's own face (the face texture is the blockstate's)


def C(m, name, f, t, mat='enamel'):
    """A piece of the booth shell (one part, so its own seams are never flagged)."""
    return m.box(name, f, t, mat, part='casing')


def panel(m, prefix, axis, plane0, plane1, u0, u1, v0, v1, holes=(), mat='enamel'):
    """A wall slab with rectangular holes, cut into boxes. axis 'x': the slab spans x plane0..plane1, u = z, v = y;
    axis 'z': spans z plane0..plane1, u = x, v = y. Holes are (u0, u1, v0, v1). Cells are merged into row strips."""
    us = sorted({u0, u1} | {h[0] for h in holes} | {h[1] for h in holes})
    vs = sorted({v0, v1} | {h[2] for h in holes} | {h[3] for h in holes})
    us = [u for u in us if u0 <= u <= u1]
    vs = [v for v in vs if v0 <= v <= v1]
    rows = []
    for j in range(len(vs) - 1):
        a, b = vs[j], vs[j + 1]
        runs, cur = [], None
        for i in range(len(us) - 1):
            c, d = us[i], us[i + 1]
            hole = any(h[0] <= c and d <= h[1] and h[2] <= a and b <= h[3] for h in holes)
            if hole:
                if cur:
                    runs.append(cur)
                cur = None
            else:
                cur = [cur[0], d] if cur else [c, d]
        if cur:
            runs.append(cur)
        rows.append((a, b, runs))
    # merge identical consecutive rows
    merged = []
    for a, b, runs in rows:
        if merged and merged[-1][2] == runs and merged[-1][1] == a:
            merged[-1] = (merged[-1][0], b, runs)
        else:
            merged.append((a, b, runs))
    n = 0
    for a, b, runs in merged:
        for (c, d) in runs:
            n += 1
            if axis == 'x':
                C(m, '%s_%d' % (prefix, n), [plane0, a, c], [plane1, b, d], mat)
            else:
                C(m, '%s_%d' % (prefix, n), [c, a, plane0], [d, b, plane1], mat)
    return n


# ---------------------------------------------------------------------------------------------------------------------
# The stand-in body: the vanilla PlayerModel (wide arms) without its head, at the entity's 0.9375, feet on the floor,
# in a group turned 45 degrees about its feet. Optional clipping: print_h = the print plane (nothing above it is drawn),
# dev_h = the develop plane (skin above it, base material below). Boxes are fractional (stand-ins are never exported).
# ---------------------------------------------------------------------------------------------------------------------
PARTS = [  # name, x0, x1, y0 (from the shoulders down, model px), y1, z0, z1, skin material
    ('torso', -4, 4, 0, 12, -2, 2, 'saline'),
    ('arm_r', -8, -4, 0, 12, -2, 2, 'stand'),
    ('arm_l', 4, 8, 0, 12, -2, 2, 'stand'),
    ('leg_r', -3.9, 0.1, 12, 24, -2, 2, 'zinc'),
    ('leg_l', -0.1, 3.9, 12, 24, -2, 2, 'zinc'),
]
COLLAR = [  # the Collar Cam (JarModel.COLLAR), above the shoulders: x0,x1, y0,y1 (up from the shoulders), z0,z1
    ('collar', -3, 3, -0.4, 1.6, -2.5, 2.5),
    ('collar_cam', -1, 1, 0.8, 2.8, -3.4, -2.4),
    ('collar_ant', 1.6, 2.6, 1.8, 3.8, 0.5, 1.5),
]
BASE_MAT = 'enamel'      # the unpainted print (bone ramp) in review renders


def body_boxes(print_h=None, dev_h=None, collar=True, at=None, yaw=None):
    """Boxes in the body's own frame, feet centre at (0, FLOOR, 0); `at`/`yaw` are applied by the group (model())."""
    s = BODY_SCALE
    top = FLOOR + 24 * s                     # the shoulders, 25.5
    out = []
    for name, x0, x1, y0, y1, z0, z1, skin in PARTS:
        lo, hi = top - y1 * s, top - y0 * s
        segs = [(lo, hi)]
        if print_h is not None:
            segs = [(a, min(b, print_h)) for a, b in segs if a < print_h]
        for a, b in segs:
            if dev_h is None:
                pieces = [(a, b, skin)]
            else:
                pieces = []
                if a < dev_h:
                    pieces.append((a, min(b, dev_h), BASE_MAT))
                if b > dev_h:
                    pieces.append((max(a, dev_h), b, skin))
            for i, (pa, pb, mat) in enumerate(pieces):
                if pb - pa < 0.05:
                    continue
                out.append(('sb_%s_%d' % (name, i), [x0 * s, pa, z0 * s], [x1 * s, pb, z1 * s], mat))
    if collar:
        for name, x0, x1, y0, y1, z0, z1 in COLLAR:
            out.append(('sb_' + name, [x0 * s, top + y0 * s, z0 * s], [x1 * s, top + y1 * s, z1 * s], 'rubber'))
    return out


def model(body=None, materials=False, door_glass=True):
    """body: None (no stand-in), or a dict for body_boxes(...) (print_h, dev_h, collar)."""
    m = kit.Model('body_printer', budget=3)

    # ---- K1 plinth and feet: the plinth is the booth's floor (its top at y 3), 1 px inside the walls ---------------
    kit.plinth(m, -7, 7, -7, 7)

    # ---- the booth shell (K2 corners: the side walls stop 1 px short of the front and back planes, so every vertical
    # corner is a 1 px step). Walls 1 px thick: the body needs the whole 14 px inside. y 3..31 (the plinth shows 1 px under them); roof slab y 30..31.
    # Left wall (-X): holes for the K6b port mouth and the saline sight face (both filled by their own boxes).
    panel(m, 'wall_l', 'x', -8, -7, -7, 7, 3, 31, holes=[(1, 5, 5, 9), (2, 4, 13, 23)])
    # Right wall (+X): the photo-strip slot (a real hole, dark behind).
    panel(m, 'wall_r', 'x', 7, 8, -7, 7, 3, 31, holes=[(-2, 4, 11, 15)])
    C(m, 'wall_back', [-7, 3, 7], [7, 31, 8])
    C(m, 'header', [-8, 28, -8], [8, 31, -7])
    C(m, 'header_in', [-7, 28, -7], [7, 31, -6])     # 1 px deeper inside, so the door's inner face steps under it        # the front wall above the door (behind the marquee)
    C(m, 'roof', [-7, 30, -6], [7, 31, 7])

    # ---- the marquee: the photo booth's sign across the roof front, 14x4, 3 px proud (the header's ends show 1 px
    # either side, so nothing meets it flush); the K3 lamp in its centre ----
    # v2: the marquee runs out to x 9 and caps the column (post, sign and booth one frame, no gallows); a
    # 1 px raised rim round a recessed 16x2 sign field on its front, the lamp in the field's centre.
    # v3 (after paint, for lettering): the sign field is the marquee's whole 4 px face, x -6..4 (10x4), framed by
    # a 1 px raised strip at the left end and by the lamp at the right; the top and bottom rim strips are gone (a
    # 4 px marquee cannot hold a rim and a 4 px field; it cannot grow: 32 px cap above, the door's swing below).
    # The K3 lamp moved from the centre to the field's right end, x 4..8, and 1 px back onto the marquee's face.
    m.box('marquee', [-7, 28, -10], [9, 32, -8], 'yellow', part='marquee')
    kit.rim(m, 'marquee_rim', 'north', -10, -7, 9, 28, 32, 'yellow', part='marquee', sides='l')
    kit.lamp(m, 'north', -10, 4, 28)

    # ---- inside: the photographer's backdrop, a pleated curtain on a brass rail along the back wall's top, drawn
    # across the back from the left corner to x 3 (v2: it was a 3 px bunch). Pleats 2 px wide alternating 1 and 2 px
    # deep; all of it stays behind the turned body's back face (z >= x + 2.65).
    m.box('curtain_rail', [-7, 28, 3], [7, 29, 4], 'brass')   # 1 px in front of the pleats' tops
    for i, (x0, z0, y0) in enumerate([(-7, 4, 4), (-5, 6, 4), (-3, 5, 4), (-1, 6, 4), (1, 6, 5)]):
        m.box('curtain_%d' % i, [x0, y0, z0], [x0 + 2, 28, 7], 'sample', part='curtain')

    # ---- right side: the photo-strip slot (4x2, through the 1 px wall into the dark booth: nothing may stand inside
    # the right wall, the body's arm sweeps it on the way out), a brass hood over it, and a strip
    # of four photos hanging out of it (the booth's sample strip).
    # v2: the strip is a 4 px card lying flat on the wall under the slot (the 2 px tongue standing off read as a handle);
    # the frame has no bottom strip, the card comes out of the slot's lower edge.
    kit.rim(m, 'slot_frame', 'east', 8, -2, 4, 12, 15, 'brass', depth=1, sides='tlr')
    m.box('photo_strip', [8, 6, -1], [9, 12, 3], 'enamel2')

    # ---- left side: K6b intake port (depth 1: the mouth fills the wall's hole), and above it the saline sight glass:
    # a 2x10 face in the wall's hole (the `saline` blockstate part: its texture shows level 0-4) in a 1 px brass frame.
    kit.rim(m, 'port_frame', 'west', -8, 0, 6, 4, 10, 'brass', depth=1)
    m.box('port_mouth', [-8, 5, 1], [-7, 9, 5], 'rubber', part='casing')
    kit.rim(m, 'saline_frame', 'west', -8, 1, 5, 12, 24, 'brass', depth=1)
    m.group('state_saline', 'static')
    m.box('saline_face', [-8, 13, 2], [-7, 23, 4], 'saline', 'state_saline', part='casing')

    # ---- back: the maker's plate, low --------------------------------------------------------------------------
    kit.makers_plate(m, 'south', 8, -2, 4)

    # ---- the column at the front-right corner (the print bar's guide), dark steel, from the ground to the marquee --
    m.box('column', [7, 0, -10], [9, 28, -8], 'dsteel')       # v2: up to the marquee, which caps it

    # ---- bones -------------------------------------------------------------------------------------------------
    # door: the whole front, a brass frame round clear glass, hinged on the machine's left (-X).
    m.bone('door', [-8, FLOOR, -8])   # the hinge in the front-left corner step: open, the slab stands clear of the doorway
    m.box('door_stile_h', [-7, 3, -8], [-6, 28, -7], 'brass', 'door', part='door')
    m.box('door_stile_f', [6, 3, -8], [7, 28, -7], 'brass', 'door', part='door')
    m.box('door_rail_b', [-6, 3, -8], [6, 4, -7], 'brass', 'door', part='door')
    m.box('door_rail_t', [-6, 27, -8], [6, 28, -7], 'brass', 'door', part='door')
    if door_glass:
        m.box('door_glass', [-6, 4, -8], [6, 27, -7], 'glass', 'door', part='door')

    # ring: the print bar. A brass beam 15x2x2 lying on the ground in front of the door at rest (the doorstep),
    # cantilevered from a sleeve on the column's front (v2: no part beyond x 9, where a neighbour's bar ends). Keyed posY 0..23 (the bar's top from y 2 to 25;
    # the print plane is 1 px above it, on the head's top: y 3..26). Rotation is the refusal's.
    m.bone('ring', [8, 1, -11])
    m.box('bar', [-8, 0, -12], [7, 2, -10], 'brass', 'ring', part='ring')      # wholly in front of the column
    m.box('sleeve', [7, 1, -13], [9, 4, -10], 'brass', 'ring', part='ring_sleeve')   # top 27 at the top, under the marquee

    # carriage: the print head, a copper block 4x3x2 in front of the beam with a lip over its top (an L clamp riding
    # the beam), an amber lens 2x1 on its face. Rest (v2): parked at the bar's free end (x -8..-4, clear of the delivered body's legs); posX +6 =
    # centre, +9 = the sleeve end (x 1..5). Its top is 1 px above the beam's (y 3 at rest: the floor's level; the body steps
    # over it).
    m.bone('carriage', [-6, 0, -12], parent='ring')
    m.box('head', [-8, 0, -14], [-4, 3, -12], 'copper', 'carriage', part='carriage')
    m.box('head_lip', [-8, 2, -12], [-4, 3, -10], 'copper', 'carriage', part='carriage')
    m.box('head_lens', [-7, 1, -15], [-5, 2, -14], 'lens_work', 'carriage', part='head_lens')

    # ---- locators (static points, model px) ---------------------------------------------------------------------
    m.locator('doorway', [0, 12, -10])        # the deliver puff (the server's poof is at the front cell)
    m.locator('slot', [8.5, 13, 1])           # the photo strip slot
    m.locator('booth', [0, 14, 0])            # inside the booth: the develop hum, the print dust's centre
    m.locator('column_top', [8, 27, -9])      # the servo at the top of the column

    # ---- contents: the four materials on the booth floor, shown while `materials` has the bit (gone at Print) -----
    note = 'FIXED, upright facing -Z, scale 0.375, while its materials bit is set'
    for name, pos, what in [('mat_flesh', [-3.5, FLOOR, -3], 'rotten flesh'), ('mat_bone', [3.5, FLOOR, -3], 'bone block'),
                            ('mat_redstone', [-3.5, FLOOR, 2], 'redstone block'),
                            ('mat_slime', [3.5, FLOOR, 2], 'slime ball')]:
        m.anchor(name, pos, '%s; %s' % (what, note))
    m.anchor('body', [0, FLOOR, 0], 'the printed body (PlayerModel at 0.9375, no head or hat, chest to the front-right '
                                     'corner: yaw 45 degrees off the facing), clipped by the print and develop planes')

    # ---- the cell shapes (Java cell coordinates; upper = the machine_top cell) ----------------------------------
    m.shape('lower', (0, 0, 0, 16, 16, 16))
    m.shape('upper', (0, 0, 0, 16, 16, 16))

    m.check()   # stand-ins below are fractional (the entity's 0.9375), so they are added after the kit's checks

    if materials:
        m.group('standin_materials', None)
        for name, pos, _ in m.anchors:
            if name.startswith('mat_'):
                x, y, z = pos
                m.box('si_' + name, [x - 2, y, z - 1], [x + 2, y + 4, z], 'standi', 'standin_materials')
    if body is not None:
        at = body.get('at') or (0, FLOOR, 0)
        yaw = BODY_YAW if body.get('yaw') is None else body['yaw']
        m.bone('standin_body', list(at), rot=[0, yaw, 0])
        d = [at[0], at[1] - FLOOR, at[2]]
        for name, f, t, mat in body_boxes(**body):
            m.box(name, [f[i] + d[i] for i in range(3)], [t[i] + d[i] for i in range(3)], mat, 'standin_body')
    return m


# ---------------------------------------------------------------------------------------------------------------------
# Placeholder post-process: (1) clear glass (palette D: frame and streaks opaque, the rest clear, cutout); (2) the
# booth's inside faces dark (the paint's recess rule: the interior is rubber r0/r1; the walls are 1 px, so the inside
# is the same box as the outside, painted per face).
# ---------------------------------------------------------------------------------------------------------------------
GLASS_FRAME = (0xa8, 0xd0, 0xd9, 255)
GLASS_STREAK = (0xd0, 0xea, 0xe9, 255)
DARK = (0x27, 0x26, 0x2d, 255)
INSIDE = {'wall_l_': 'east', 'wall_r_': 'west', 'wall_back': 'north', 'header': 'south', 'roof': 'down',
          'port_mouth': 'east', 'saline_face': 'east'}


def post(path):
    from PIL import Image
    with open(path, encoding='utf-8') as fh:
        d = json.load(fh)
    png = os.path.splitext(path)[0] + '_placeholder.png'
    im = Image.open(png).convert('RGBA')
    px = im.load()

    def region(face):
        u0, v0, u1, v1 = face['uv']
        xa, xb = sorted((int(u0), int(u1)))
        ya, yb = sorted((int(v0), int(v1)))
        return xa, xb, ya, yb

    for e in d['elements']:
        if e.get('type') != 'cube':
            continue
        if e['name'] == 'door_glass':
            for fname, face in e['faces'].items():
                xa, xb, ya, yb = region(face)
                for y in range(ya, yb):
                    for x in range(xa, xb):
                        edge = x in (xa, xb - 1) or y in (ya, yb - 1)
                        px[x, y] = GLASS_FRAME if edge else (0, 0, 0, 0)
                if xb - xa >= 6 and yb - ya >= 6:
                    for (x, y) in [(xa + 2, ya + 1), (xa + 1, ya + 2), (xa + 3, ya + 1), (xb - 3, yb - 2), (xb - 2, yb - 3)]:
                        px[x, y] = GLASS_STREAK
        for pre, fname in INSIDE.items():
            if e['name'].startswith(pre):
                xa, xb, ya, yb = region(e['faces'][fname])
                for y in range(ya, yb):
                    for x in range(xa, xb):
                        px[x, y] = DARK
    im.save(png)
    with open(png, 'rb') as fh:
        d['textures'][0]['source'] = 'data:image/png;base64,' + base64.b64encode(fh.read()).decode()
    with open(path, 'w', encoding='utf-8') as fh:
        json.dump(d, fh, indent=1)


def build(path, **kw):
    m = model(**kw)
    # UVs frozen against the painted build: every kept cube keeps its island (the rule after paint)
    m.write(path, check=False, freeze_uv=PATH if os.path.exists(PATH) else None)
    post(path)
    return m


if __name__ == '__main__':
    m = build(PATH)
    print(kit.shapes_java(m))
    for a in m.anchors:
        print('anchor', a)

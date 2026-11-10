"""Separation Chair (DOLORES): geometry spec, box-UV packing by material, placeholder texture.

Source of truth for the chair's cubes. Coordinates are Blockbench/GeckoLib block space in pixels: origin at the
block's bottom centre, x -8..8, z -8..8, y up; the chair faces -Z (north at rest), the patient's right is +X.

    python art/chair/chair_geo.py            # writes chair_placeholder.png and prints the bbmodel_edit operations

The operations are applied to separation_chair.bbmodel with the Blockbench tool server (bbmodel_edit). Animations are
written by chair_anim.py. Placeholder colours are flat mid-tones from house palette A (art/palette.json).
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
TEX_W, TEX_H = 256, 128

# material -> placeholder mid-tone (palette A) and the texture rows it packs into
MATERIALS = {
    'ivory':  'c6bdaa',   # ivory enamel
    'brass':  'c29954',
    'steel':  '989ea4',   # brushed steel
    'blade':  'd5d9db',   # brushed steel, the light end of the ramp (the blade edge)
    'rubber': '333137',   # rubber black
    'yellow': 'daa936',   # warning yellow
    'teal':   '5aa6a4',   # teal glass
    'lamp_g': '8bc987',   # lamp lenses (glowmask later)
    'lamp_a': 'fad85f',
    'lamp_r': 'b5473a',   # steady red, never blinks
    'stand':  'eba688',   # stand-ins only (patient), never exported
    'standj': '8bc987',   # stand-in jar glass
}

# bones: name -> (pivot, parent, rest rotation)
BONES = [
    ('static', [0, 0, 0], None, None),
    ('barrier', [9, 14, -8], None, None),
    ('privacy_panel', [10, 23, -9], 'barrier', None),
    ('gas_nozzle', [10, 16, -6.5], 'barrier', None),
    ('boom_lift', [-10, 28, 0], None, None),
    ('boom', [-10, 28, 0], 'boom_lift', None),
    ('scan_ring', [0, 27.5, 0], 'boom', None),
    ('scan_plane', [0, 27, 0], 'scan_ring', None),
    ('claw', [0, 28, 0], 'boom', None),
    ('claw_finger_f', [0, 27.5, -2], 'claw', [35, 0, 0]),
    ('claw_finger_b', [0, 27.5, 2], 'claw', None),
    ('head_carry', [0, 22.25, 0], 'claw', None),
    ('blade_arm', [0, 18.5, 6], None, None),
    ('antenna', [-5.5, 17, 3.5], None, None),
    ('needle', [-10, 10, 2], None, None),
    ('lamp_green', [9, 6, -10.5], None, None),
    ('lamp_amber', [9, 9, -10.5], None, None),
    ('lamp_red', [9, 12, -10.5], None, None),
    ('head_anchor', [0, 22.25, 0], None, None),
    ('neck_anchor', [0, 18.5, 0], None, None),
    ('jar_anchor', [-16, 0, -8], None, None),
    ('jar_lid', [-23.15, 13.2, -8], 'jar_anchor', None),
    ('standin_patient', [0, 0, 0], None, None),
    ('standin_jar', [-16, 0, -8], 'jar_anchor', None),
    ('standin_lid', [-23.15, 13.2, -8], 'jar_lid', None),
    ('standin_head', [0, 22.25, 0], 'head_carry', None),
]

# cubes: (name, from, to, material, parent bone, extra) ; extra may hold origin/rotation for stand-ins
CUBES = [
    # ---- static: stays in the baked block model ----
    ('plinth', [-7, 0, -7], [7, 1, 6], 'rubber', 'static'),
    ('pedestal', [-5, 1, -5], [5, 3, 3], 'steel', 'static'),
    ('seat_trim', [-7, 3, -7], [7, 4, 2], 'brass', 'static'),
    ('seat', [-7, 4, -7], [7, 6, 2], 'rubber', 'static'),
    ('backrest', [-7, 4, 2], [7, 16, 5], 'ivory', 'static'),
    ('backrest_cap', [-7, 16, 2], [7, 17, 5], 'brass', 'static'),
    ('pad_post', [-1, 17, 7], [1, 20, 8], 'steel', 'static'),
    ('spine', [-1, 12, 5], [1, 17, 8], 'steel', 'static'),
    ('headrest', [-4, 20, 6], [4, 24, 8], 'rubber', 'static'),
    ('footrest_strut', [-2, 0, -11], [2, 2, -7], 'steel', 'static'),
    ('footrest', [-7, 2, -12], [7, 3, -7], 'rubber', 'static'),
    ('mast_foot', [-12, 0, -1], [-8, 2, 3], 'steel', 'static'),
    ('mast', [-11, 2, -1], [-9, 31, 1], 'steel', 'static'),
    ('mast_cap', [-11, 31, -1], [-9, 32, 1], 'brass', 'static'),
    ('mast_bracket', [-9, 4, -1], [-7, 6, 1], 'brass', 'static'),
    ('gauge', [-12, 8, 1], [-8, 12, 2], 'ivory', 'static'),
    ('post_foot', [7, 0, -10], [11, 1, -6], 'steel', 'static'),
    ('barrier_post', [8, 1, -9], [10, 13, -7], 'yellow', 'static'),
    ('lamp_plate', [8, 4, -10], [10, 14, -9], 'rubber', 'static'),
    ('post_bracket', [7, 4, -7], [8, 6, -5], 'brass', 'static'),
    # ---- barrier (the level-crossing restraint) ----
    ('barrier_hub', [8, 13, -9], [10, 15, -7], 'steel', 'barrier'),
    ('barrier_arm', [8, 15, -9], [10, 32, -7], 'yellow', 'barrier'),
    ('barrier_pad', [8, 17, -7], [9, 31, -6], 'rubber', 'barrier'),
    ('privacy_plate', [10, 19, -9], [11, 27, -3], 'ivory', 'privacy_panel'),
    ('gas_horn', [10, 16, -7], [11, 19, -6], 'brass', 'gas_nozzle'),
    # ---- boom (mast-mounted arm over the head) ----
    ('collar', [-12, 27, -2], [-8, 30, 2], 'steel', 'boom'),
    ('beam', [-8, 28, -1], [7, 30, 1], 'ivory', 'boom'),
    ('hood', [-5, 30, -4], [5, 31, 4], 'ivory', 'boom'),
    ('rod_l', [-8, 19, -1], [-7, 28, 1], 'steel', 'boom'),
    ('rod_r', [7, 19, -1], [8, 28, 1], 'steel', 'boom'),
    ('pour_spout', [-4, 27, -1], [-2, 28, 1], 'brass', 'boom'),
    ('ring_f', [-8, 27, -8], [8, 28, -7], 'teal', 'scan_ring'),
    ('ring_b', [-8, 27, 5], [8, 28, 6], 'teal', 'scan_ring'),
    ('ring_l', [-8, 27, -7], [-7, 28, 5], 'teal', 'scan_ring'),
    ('ring_r', [7, 27, -7], [8, 28, 5], 'teal', 'scan_ring'),
    ('scan_sheet', [-7, 27, -7], [7, 27, 5], 'teal', 'scan_plane'),
    ('claw_body', [-2, 27, -2], [2, 28, 2], 'steel', 'claw'),
    ('finger_f_top', [-1, 27, -4], [1, 28, -2], 'yellow', 'claw_finger_f'),
    ('finger_f_tip', [-1, 23, -5], [1, 28, -4], 'yellow', 'claw_finger_f'),
    ('finger_b_top', [-1, 27, 2], [1, 28, 4], 'yellow', 'claw_finger_b'),
    ('finger_b_tip', [-1, 23, 4], [1, 28, 5], 'yellow', 'claw_finger_b'),
    # ---- blade (behind the neck) ----
    ('blade_hub', [-1, 17, 5], [1, 20, 7], 'yellow', 'blade_arm'),
    ('blade_shank', [1, 18, 5], [4, 19, 7], 'steel', 'blade_arm'),
    ('blade', [4, 18, 4], [10, 19, 8], 'blade', 'blade_arm'),
    # ---- small things ----
    ('antenna_rod', [-6, 11, 3], [-5, 17, 4], 'steel', 'antenna'),
    ('antenna_tip', [-6, 17, 3], [-5, 18, 4], 'brass', 'antenna'),
    ('needle_bar', [-11, 10, 2], [-9, 12, 3], 'rubber', 'needle'),
    ('lamp_green_lens', [8, 5, -11], [10, 7, -10], 'lamp_g', 'lamp_green'),
    ('lamp_amber_lens', [8, 8, -11], [10, 10, -10], 'lamp_a', 'lamp_amber'),
    ('lamp_red_lens', [8, 11, -11], [10, 13, -10], 'lamp_r', 'lamp_red'),
    # ---- stand-ins (never exported): seated vanilla player and the Life Support Jar (wide) ----
    ('standin_body', [-4, 7, -2], [4, 19, 2], 'stand', 'standin_patient'),
    ('standin_arm_r', [4, 7, -2], [8, 19, 2], 'stand', 'standin_patient', {'origin': [5.6, 16.6, 0], 'rotation': [36, 0, 0]}),
    ('standin_arm_l', [-8, 7, -2], [-4, 19, 2], 'stand', 'standin_patient', {'origin': [-5.6, 16.6, 0], 'rotation': [36, 0, 0]}),
    ('standin_leg_r', [0, -4, -2], [4, 8, 2], 'stand', 'standin_patient', {'origin': [1.8, 7.25, 0], 'rotation': [81, -18, 0]}),
    ('standin_leg_l', [-4, -4, -2], [0, 8, 2], 'stand', 'standin_patient', {'origin': [-1.8, 7.25, 0], 'rotation': [81, 18, 0]}),
    ('standin_head_box', [-4, 18, -4], [4, 26, 4], 'stand', 'standin_head'),
    ('standin_jar_base', [-23, 0, -15], [-9, 1, -1], 'standj', 'standin_jar'),
    ('standin_jar_glass', [-22, 1, -14], [-10, 13, -2], 'standj', 'standin_jar'),
    ('standin_jar_lid', [-23, 13, -15], [-9, 14, -1], 'standj', 'standin_lid'),
]

# locators: name -> (absolute position, parent)
LOCATORS = [
    ('gas', [10.5, 19, -6.5], 'gas_nozzle'),      # tip of the horn when the barrier is down and the horn up (see notes)
    ('blade_contact', [0, 18.5, 0], 'neck_anchor'),
    ('pour', [-3, 27, 0], 'boom'),
    ('spark', [-5.5, 18, 3.5], 'antenna'),
    ('lamp', [9, 9, -11], None),
    ('vent_l', [-5, 2, -1], None),
    ('vent_r', [5, 2, -1], None),
]


# cubes added after the first layout keep a fixed island so the earlier islands never move
FIXED_UV = {'spine': [0, 100], 'blade': [12, 100]}


def size(c):
    return [abs(c[2][i] - c[1][i]) for i in range(3)]


def box_uv_wh(c):
    w, h, d = [max(1, round(s)) if s > 0 else 0 for s in size(c)]
    return 2 * (w + d), d + h


def pack():
    """Shelf-pack every cube's box-UV island; islands grouped by material so the painter gets one area per material."""
    order = ['ivory', 'yellow', 'steel', 'blade', 'brass', 'rubber', 'teal', 'lamp_g', 'lamp_a', 'lamp_r', 'stand', 'standj']
    offsets, regions = {}, {}
    x = y = shelf = 0
    for mat in order:
        items = [c for c in CUBES if c[3] == mat and c[0] not in FIXED_UV]
        items.sort(key=lambda c: -box_uv_wh(c)[1])
        # each material starts on a new shelf
        top = y
        for c in items:
            w, h = box_uv_wh(c)
            if x + w > TEX_W:
                y += shelf
                x = shelf = 0
            offsets[c[0]] = [x, y]
            x += w
            shelf = max(shelf, h)
        regions[mat] = (top, y + shelf)
    offsets.update(FIXED_UV)
    if y + shelf > TEX_H:
        sys.exit('texture overflow: %d rows' % (y + shelf))
    return offsets, regions


def draw(offsets):
    from PIL import Image
    img = Image.new('RGBA', (TEX_W, TEX_H), (0, 0, 0, 0))
    px = img.load()
    for c in CUBES:
        w, h, d = [max(1, round(s)) if s > 0 else 0 for s in size(c)]
        ox, oy = offsets[c[0]]
        col = tuple(int(MATERIALS[c[3]][i:i + 2], 16) for i in (0, 2, 4)) + (255,)
        # box UV island: top strip (up/down faces) and the side strip
        rects = [(ox + d, oy, w * 2, d), (ox, oy + d, 2 * (w + d), h)]
        for rx, ry, rw, rh in rects:
            for yy in range(ry, ry + rh):
                for xx in range(rx, rx + rw):
                    if 0 <= xx < TEX_W and 0 <= yy < TEX_H:
                        px[xx, yy] = col
    out = os.path.join(HERE, 'chair_placeholder.png')
    img.save(out)
    return out


def ops(offsets):
    out = []
    for name, pivot, parent, rot in BONES:
        g = {'op': 'add_group', 'name': name, 'origin': pivot}
        if parent:
            g['parent'] = parent
        if rot:
            g['rotation'] = rot
        out.append(g)
    for c in CUBES:
        o = {'op': 'add_cube', 'name': c[0], 'from': c[1], 'to': c[2], 'parent': c[4], 'uv_offset': offsets[c[0]]}
        if len(c) > 5:
            o.update(c[5])
        out.append(o)
    for name, pos, parent in LOCATORS:
        l = {'op': 'add_locator', 'name': name, 'position': pos}
        if parent:
            l['parent'] = parent
        out.append(l)
    return out


def uv_ops(offsets):
    return [{'op': 'update_node', 'target': c[0], 'uv_offset': offsets[c[0]]} for c in CUBES]


if __name__ == '__main__':
    if '--uv' in sys.argv:
        offs, regions = pack()
        draw(offs)
        print(json.dumps(uv_ops(offs), separators=(',', ':')))
        sys.exit(0)
    offs, regions = pack()
    path = draw(offs)
    sys.stderr.write('texture %s; material rows %s\n' % (path, regions))
    print(json.dumps(ops(offs), separators=(',', ':')))

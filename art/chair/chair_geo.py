"""Separation Chair (DOLORES): geometry spec, box-UV packing by material, placeholder texture.

Source of truth for the chair's cubes. Coordinates are
Blockbench/GeckoLib block space in pixels: origin at the block's bottom centre, x -8..8, z -8..8, y up; the chair faces
-Z (north at rest), the patient's right is +X.

    python art/chair/chair_geo.py            # writes chair_placeholder.png and prints the bbmodel_edit operations

The operations are applied to a freshly created separation_chair.bbmodel with the Blockbench tool server
(bbmodel_create + bbmodel_edit), and the PNG is added with bbmodel_add_texture. Animations are written by
chair_anim.py. Placeholder colours are flat mid-tones from house palette A (art/palette.json).
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
TEX_W, TEX_H = 256, 128

# material -> placeholder mid-tone (palette A)
MATERIALS = {
    'ivory':  'c6bdaa',   # ivory enamel
    'brass':  'c29954',
    'steel':  '989ea4',   # brushed steel
    'blade':  'd5d9db',   # brushed steel, the light end of the ramp (the blade)
    'rubber': '333137',   # rubber black
    'yellow': 'daa936',   # warning yellow
    'teal':   '5aa6a4',   # teal glass
    'curtain': 'aba293',  # privacy curtain (ivory enamel's mid-dark step; the painter makes it fabric)
    'lens':   '252329',   # lamp lenses when off (rubber's darkest steps)
    'lamp_g': '8bc987',   # lit lens caps (glowmask later)
    'lamp_a': 'fad85f',
    'lamp_r': 'b5473a',   # steady red, never blinks
    'stand':  'eba688',   # stand-ins only (patient), never exported
    'standj': '8bc987',   # stand-in jar
}

# bones: (name, pivot, parent, rest rotation)
BONES = [
    ('static', [0, 0, 0], None, None),
    ('barrier', [9, 14, -8], None, None),
    ('privacy_panel', [10, 23, -9], 'barrier', None),
    ('boom_lift', [-9, 29, 0], None, None),
    ('boom', [-9, 29, 0], 'boom_lift', None),
    ('scan_ring', [0, 27.5, 0], 'boom', None),
    ('scan_plane', [0, 27, 0], 'scan_ring', None),
    ('claw', [0, 28, 0], 'boom', None),
    ('claw_jaw_l', [-3, 27.5, 0], 'claw', None),
    ('claw_jaw_r', [3, 27.5, 0], 'claw', None),
    ('head_carry', [0, 22.25, 0], 'claw', None),
    ('blade_arm', [0, 18.5, 6], None, None),
    ('lamp_green', [9, 6, -10.5], None, None),
    ('lamp_amber', [9, 9, -10.5], None, None),
    ('lamp_red', [9, 12, -10.5], None, None),
    ('head_anchor', [0, 22.25, 0], None, None),
    ('neck_anchor', [0, 18.5, 0], None, None),
    ('jar_tray', [0, 0, 0], None, None),
    ('jar_anchor', [0, 1, 0], 'jar_tray', None),
    ('jar_lid', [-7.15, 14.2, 0], 'jar_anchor', None),
    ('standin_patient', [0, 0, 0], None, None),
    ('standin_jar', [0, 1, 0], 'jar_anchor', None),
    ('standin_lid', [-7.15, 14.2, 0], 'jar_lid', None),
    ('standin_head', [0, 22.25, 0], 'head_carry', None),
]

# cubes: (name, from, to, material, parent bone[, extra])
CUBES = [
    # ---- static: stays in the baked block model ----
    ('foot_fl', [-7, 0, -7], [-5, 1, -5], 'rubber', 'static'),
    ('foot_fr', [5, 0, -7], [7, 1, -5], 'rubber', 'static'),
    ('foot_bl', [-7, 0, 5], [-5, 1, 7], 'rubber', 'static'),
    ('foot_br', [5, 0, 5], [7, 1, 7], 'rubber', 'static'),
    ('plinth', [-7, 1, -7], [7, 2, 7], 'steel', 'static'),
    ('pedestal', [-5, 2, -5], [5, 3, 3], 'steel', 'static'),
    ('seat_trim', [-7, 3, -7], [7, 4, 2], 'brass', 'static'),
    ('seat', [-7, 4, -7], [7, 6, 2], 'rubber', 'static'),
    ('backrest', [-7, 4, 2], [7, 16, 5], 'ivory', 'static'),
    ('backrest_cap', [-7, 16, 2], [7, 17, 5], 'brass', 'static'),
    ('spine', [-1, 12, 5], [1, 17, 8], 'steel', 'static'),
    ('footrest_strut', [-2, 0, -11], [2, 2, -7], 'steel', 'static'),
    ('footrest', [-7, 2, -12], [7, 3, -7], 'rubber', 'static'),
    ('mast_foot', [-11, 1, 1], [-7, 2, 4], 'brass', 'static'),
    ('mast', [-10, 2, 1], [-8, 29, 3], 'steel', 'static'),
    ('mast_bracket', [-8, 4, 1], [-7, 6, 3], 'brass', 'static'),
    ('post_foot', [7, 0, -10], [11, 1, -6], 'steel', 'static'),
    ('barrier_post', [8, 1, -9], [10, 13, -7], 'yellow', 'static'),
    ('lamp_plate', [8, 4, -10], [10, 14, -9], 'rubber', 'static'),
    ('post_bracket', [7, 4, -7], [8, 6, -5], 'brass', 'static'),
    ('lens_green', [8, 5, -11], [10, 7, -10], 'lens', 'static'),
    ('lens_amber', [8, 8, -11], [10, 10, -10], 'lens', 'static'),
    ('lens_red', [8, 11, -11], [10, 13, -10], 'lens', 'static'),
    # ---- barrier (the level-crossing restraint) ----
    ('barrier_hub', [8, 13, -9], [10, 15, -7], 'steel', 'barrier'),
    ('barrier_arm', [8, 15, -9], [10, 32, -7], 'yellow', 'barrier'),
    ('barrier_pad', [8, 17, -7], [9, 31, -6], 'rubber', 'barrier'),
    ('counterweight', [8, 10, -7], [11, 14, -4], 'steel', 'barrier'),
    ('curtain', [10, 19, -9], [11, 27, -3], 'curtain', 'privacy_panel'),
    ('curtain_rail', [10, 18, -3], [11, 28, -2], 'brass', 'privacy_panel'),
    # ---- the hood: a sliding rail behind the mast carries the dryer dome ----
    ('lift_rail', [-10, 15, -1], [-8, 27, 1], 'steel', 'boom_lift'),
    ('rail_cap', [-10, 27, -2], [-8, 29, 1], 'brass', 'boom_lift'),
    ('pivot_pin', [-10, 29, -1], [-8, 31, 1], 'steel', 'boom'),
    ('beam', [-9, 30, -1], [-6, 32, 1], 'ivory', 'boom'),
    ('dome', [-6, 28, -6], [6, 31, 6], 'ivory', 'boom'),
    ('dome_cap', [-4, 31, -4], [4, 32, 4], 'brass', 'boom'),
    ('rod_l', [-6, 19, 4], [-4, 28, 6], 'steel', 'boom'),
    ('rod_r', [4, 19, 4], [6, 28, 6], 'steel', 'boom'),
    ('ring_f', [-7, 27, -6], [7, 28, -5], 'teal', 'scan_ring'),
    ('ring_b', [-7, 27, 5], [7, 28, 6], 'teal', 'scan_ring'),
    ('ring_l', [-7, 27, -5], [-6, 28, 5], 'teal', 'scan_ring'),
    ('ring_r', [6, 27, -5], [7, 28, 5], 'teal', 'scan_ring'),
    ('scan_sheet', [-6, 27, -5], [6, 27, 5], 'teal', 'scan_plane'),
    ('claw_plate', [-3, 27, -2], [3, 28, 2], 'steel', 'claw'),
    ('jaw_l_top', [-5, 27, -1], [-3, 28, 1], 'yellow', 'claw_jaw_l'),
    ('jaw_l_tip', [-6, 22, -1], [-5, 28, 1], 'yellow', 'claw_jaw_l'),
    ('jaw_r_top', [3, 27, -1], [5, 28, 1], 'yellow', 'claw_jaw_r'),
    ('jaw_r_tip', [5, 22, -1], [6, 28, 1], 'yellow', 'claw_jaw_r'),
    # ---- blade (behind the neck) ----
    ('blade_hub', [-1, 17, 5], [1, 20, 7], 'yellow', 'blade_arm'),
    ('blade_shank', [1, 18, 5], [4, 19, 7], 'steel', 'blade_arm'),
    ('blade', [4, 18, 3], [12, 19, 9], 'blade', 'blade_arm'),
    ('blade_spine', [11, 19, 3], [12, 20, 9], 'steel', 'blade_arm'),
    # ---- small things ----
    ('lamp_green_lit', [8, 5, -11], [10, 7, -10], 'lamp_g', 'lamp_green', {'inflate': 0.25}),
    ('lamp_amber_lit', [8, 8, -11], [10, 10, -10], 'lamp_a', 'lamp_amber', {'inflate': 0.25}),
    ('lamp_red_lit', [8, 11, -11], [10, 13, -10], 'lamp_r', 'lamp_red', {'inflate': 0.25}),
    # ---- the jar dock tray: slides out from under the chair to the patient's left ----
    ('tray', [-5, 0, -5], [5, 1, 5], 'rubber', 'jar_tray'),
    # ---- stand-ins (never exported): seated vanilla player and the Life Support Jar (wide) ----
    ('standin_body', [-4, 7, -2], [4, 19, 2], 'stand', 'standin_patient'),
    ('standin_arm_r', [4, 7, -2], [8, 19, 2], 'stand', 'standin_patient', {'origin': [5.6, 16.6, 0], 'rotation': [36, 0, 0]}),
    ('standin_arm_l', [-8, 7, -2], [-4, 19, 2], 'stand', 'standin_patient', {'origin': [-5.6, 16.6, 0], 'rotation': [36, 0, 0]}),
    ('standin_leg_r', [0, -4, -2], [4, 8, 2], 'stand', 'standin_patient', {'origin': [1.8, 7.25, 0], 'rotation': [81, -18, 0]}),
    ('standin_leg_l', [-4, -4, -2], [0, 8, 2], 'stand', 'standin_patient', {'origin': [-1.8, 7.25, 0], 'rotation': [81, 18, 0]}),
    ('standin_head_box', [-4, 18, -4], [4, 26, 4], 'stand', 'standin_head'),
    ('standin_jar_base', [-7, 1, -7], [7, 2, 7], 'standj', 'standin_jar'),
    ('standin_jar_glass', [-6, 2, -6], [6, 14, 6], 'standj', 'standin_jar'),
    ('standin_jar_lid', [-7, 14, -7], [7, 15, 7], 'standj', 'standin_lid'),
]

# locators: (name, absolute rest position, parent)
LOCATORS = [
    ('gas', [0, 27, -5], 'boom'),                 # under the dome's front edge
    ('blade_contact', [0, 18.5, 0], 'neck_anchor'),
    ('pour', [2, 27, 0], 'boom'),                 # under the dome, over the jar mouth when the hood is at the jar
    ('spark', [0, 32, 0], 'boom'),                # on the dome cap
    ('lamp', [9, 9, -11.5], None),
    ('vent_l', [-5, 2, -1], None),
    ('vent_r', [5, 2, -1], None),
]

# cubes added after a layout keep a fixed island so the earlier islands never move (none in this version)
FIXED_UV = {}


def size(c):
    return [abs(c[2][i] - c[1][i]) for i in range(3)]


def box_uv_wh(c):
    w, h, d = [max(1, round(s)) if s > 0 else 0 for s in size(c)]
    return 2 * (w + d), d + h


def pack():
    """Shelf-pack every cube's box-UV island; islands grouped by material so the painter gets one area per material."""
    order = ['ivory', 'curtain', 'yellow', 'steel', 'blade', 'brass', 'rubber', 'teal', 'lens', 'lamp_g', 'lamp_a',
             'lamp_r', 'stand', 'standj']
    offsets, regions = {}, {}
    x = y = shelf = 0
    for mat in order:
        items = [c for c in CUBES if c[3] == mat and c[0] not in FIXED_UV]
        items.sort(key=lambda c: -box_uv_wh(c)[1])
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


if __name__ == '__main__':
    offs, regions = pack()
    path = draw(offs)
    sys.stderr.write('texture %s; material rows %s\n' % (path, regions))
    print(json.dumps(ops(offs), separators=(',', ':')))

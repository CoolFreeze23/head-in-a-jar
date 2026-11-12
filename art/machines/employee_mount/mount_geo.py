"""Employee Mount (design C, the Cubicle): geometry.

    python art/machines/employee_mount/mount_geo.py      # writes employee_mount.bbmodel and its placeholder PNG
    python art/machines/employee_mount/mount_anim.py     # then the clips (--review: the review copy and variants)

An office cubicle in miniature, bolted to the machine it staffs (the machine is behind it, at +Z). A partition panel
14 wide and 18 tall stands against the machine: an ivory frame round a dark felt field 12x14 set 1 px back (a pale
memo pinned to it), a brass cap rail at y 18-19 overhanging 1 px. A desk shelf 16x2x12 hangs off it on two steel
brackets with a 1 px shadow gap behind it; a brass nameplate hangs on its front edge. At the desk's back centre the
villager's specimen jar (K13 size 8: ring 8x1x8, glass 6x7x6 inset 1, lid 8x2x8) stands on a dark swivel
foot, seen against the felt. On the desk: the telephone at the front left (+X, the viewer's
left: a dark body with an ivory handset, bone `receiver`), a copper pay tray at the front right where the day's
emerald stands, a low banker's lamp at the back left whose 4x2x4 shade is the lamp lens (green paid, dark unpaid,
amber nitwit), and a rubber stamp at the back right (bone `stamp`). No mug.

Ten-block motions: paid, the stamp lifts 6 px and thumps at every profession tic; unpaid, every 2,400 ticks the
handset jumps off the phone and stands upright beside the jar at the head's ear (6.7 px), held while the head shakes
"no", then is slammed down.

The head (code, never modelled): a villager head drawn at scale 7/16 (head 3.5x4.375x3.5 px, nose 0.875x1.75x0.875) inside the glass,
neck at HEAD_NECK, facing -Z; its tics are code offsets about HEAD_PIVOT. The stand-in `standin_head` is that size.

Coordinates: px, origin at the block's bottom centre, y up, front -Z, +X is the mount's right (the viewer's left).
Rest height 19 (one block, draws 3 px into the cell above like a vanilla head on a block; no top cell).
"""
import base64
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
KIT = os.path.join(HERE, '..', 'kit')
if not os.path.exists(os.path.join(KIT, 'kit.py')):          # worktree disturbed: the scratch copy
    KIT = 'build/machines-review/scratch-employee_mount/kit'
sys.path.insert(0, KIT)
import kit  # noqa: E402

PATH = os.path.join(HERE, 'employee_mount.bbmodel')

JAR = (0, 6, 0)               # the swivel foot's bottom centre on the desk top; ring 7-8, glass 8-15, lid 15-17
HEAD_NECK = (0, 9.5, 0)       # the head's bottom centre (the villager head part's origin), facing -Z
HEAD_PIVOT = (0, 11, 0)       # the head's centre: every code tic turns about this point
HEAD_SCALE = 0.4375           # 7/16 of the vanilla villager head (8x10x8 + nose 2x4x2): 3.5x4.375x3.5 + nose
RECEIVER_PIVOT = (5, 10, -5.5)
STAMP_PIVOT = (-6, 6, -0.5)


def model():
    m = kit.Model('employee_mount', budget=2)

    # ---- the partition: frame strips 3 deep (z 5..8) round a felt field set 1 px back (face at z 6), a back board
    # behind the felt, a rubber kick under it (a shadow gap at the floor), a brass cap rail. One part (the frame).
    F = lambda n, f, t, mat='enamel': m.box(n, f, t, mat, part='frame')   # noqa: E731
    F('frame_l', [-7, 1, 5], [-6, 18, 8])
    F('frame_r', [6, 1, 5], [7, 18, 8])
    F('frame_b', [-6, 1, 5], [6, 2, 8])
    F('frame_t', [-6, 16, 5], [6, 18, 8])
    F('back_board', [-6, 2, 7], [6, 16, 8])
    m.box('felt', [-6, 2, 6], [6, 16, 7], 'rubber')
    m.box('kick', [-6, 0, 6], [6, 1, 7], 'rubber')
    m.box('rail', [-8, 18, 4], [8, 19, 7], 'brass')

    # ---- the desk shelf on two brackets (into the felt), 1 px clear of the frame (the shadow line behind it);
    # the nameplate on its front edge, hanging 1 px below it
    m.box('desk', [-8, 4, -8], [8, 6, 4], 'enamel2')
    m.box('bracket_l', [-5, 2, 2], [-3, 4, 6], 'steel')
    m.box('bracket_r', [3, 2, 2], [5, 4, 6], 'steel')
    m.box('nameplate', [-3, 3, -9], [3, 5, -8], 'brass')

    # ---- the specimen jar (K13 size 8) on a dark swivel foot; glass is the house clear glass (frame and streaks)
    x, y, z = JAR
    m.box('jar_swivel', [x - 2, y, z - 2], [x + 2, y + 1, z + 2], 'dsteel')
    m.box('jar_ring', [x - 4, y + 1, z - 4], [x + 4, y + 2, z + 4], 'brass')
    m.box('jar_glass', [x - 3, y + 2, z - 3], [x + 3, y + 9, z + 3], 'glass')
    m.box('jar_lid', [x - 4, y + 9, z - 4], [x + 4, y + 11, z + 4], 'brass')

    # ---- the telephone at the front left (+X): a dark body 4x3x3; its handset is the bone `receiver`
    m.box('phone', [3, 6, -7], [7, 9, -4], 'rubber')

    # ---- the pay tray at the front right (-X): the emerald stands in it when paid today
    m.box('tray', [-7, 6, -7], [-3, 7, -4], 'copper')

    # ---- a banker's lamp at the back left (+X), low under where the lifted receiver is held: a brass stem and a
    # 4x2x4 shade that IS the lamp lens (the `state_lamp` element: green paid, dark unpaid, amber nitwit)
    m.box('lamp_stem', [5, 6, 1], [7, 9, 3], 'brass')
    m.group('state_lamp', 'static')
    m.box('lamp_lens', [4, 9, 0], [8, 11, 4], 'lens', 'state_lamp')
    m.locator('lamp', [6, 10, 0])

    # ---- a memo pinned to the felt, upper right (-X): makes the dark field a cubicle wall, not a doorway
    m.box('memo', [-5, 12, 5], [-2, 15, 6], 'zinc')

    # ---- bone 1, the receiver: a handset (two 2x2x1 cups joined by a 2x1x1 grip along their tops, 6 long), ivory:
    # light on the dark phone at rest, light on the dark felt when lifted to the ear
    m.bone('receiver', list(RECEIVER_PIVOT))
    m.box('receiver_ear', [6, 9, -6], [8, 11, -5], 'enamel', 'receiver', part='receiver')
    m.box('receiver_mouth', [2, 9, -6], [4, 11, -5], 'enamel', 'receiver', part='receiver')
    m.box('receiver_grip', [4, 10, -6], [6, 11, -5], 'enamel', 'receiver', part='receiver')

    # ---- bone 2, the rubber stamp at the back right (-X), the paid employee's work: a dark rubber die 4x1x3, a
    # copper neck 2x3x1, a brass knob 4x2x3; lifts 6 px and thumps down at every profession tic while paid
    m.bone('stamp', list(STAMP_PIVOT))
    m.box('stamp_die', [-8, 6, -2], [-4, 7, 1], 'rubber', 'stamp', part='stamp')
    m.box('stamp_neck', [-7, 7, -1], [-5, 10, 0], 'copper', 'stamp', part='stamp')
    m.box('stamp_knob', [-8, 10, -2], [-4, 12, 1], 'brass', 'stamp', part='stamp')

    # ---- locators (static points: particles and sounds)
    m.locator('jar_top', [0, 17.5, 0])       # payday sparkles over the lid
    m.locator('ear', [5, 14.5, -0.5])        # where the lifted receiver is held
    m.locator('phone', [5, 9, -5.5])       # the cradle (lift, clack, refusal rattle)
    m.locator('tray', [-5, 7, -5.5])       # the coin's clink
    m.locator('stamp', [-6, 6.5, -2.5])     # the stamp's thump (an ink puff, if any)

    # ---- contents drawn by the game (stand-ins never exported)
    m.anchor('pay', [-5, 7, -5.5], 'the emerald (FIXED, upright facing -Z, scale 0.25) while paid for today',
             standin=((3, 3, 1), 'lens_ready'))
    # the head: its own stand-in group pivoted at the head's centre (review clips turn it to show the tics)
    m.bone('standin_head', list(HEAD_PIVOT))
    # (its boxes are fractional: injected after the checks by post(); head_boxes() gives them)
    m.group('standin_mats', None, hidden=True)
    for i, mat in enumerate(('stand', 'standi')):
        m.box('si_mat_' + mat, [20 + 4 * i, 0, 20], [22 + 4 * i, 2, 22], mat, 'standin_mats')

    # ---- shapes (Java cell coordinates; one block, the parts over 16 px are outline only)
    m.shape('lower', (1, 0, 13, 15, 18, 16))      # partition
    m.shape('lower', (0, 18, 12, 16, 19, 15))     # cap rail
    m.shape('lower', (0, 4, 0, 16, 6, 12))        # desk (the nameplate overhangs 1 px in front: drawn, not solid)
    m.shape('lower', (4, 6, 4, 12, 17, 12))       # jar
    m.shape('lower', (10, 6, 1, 16, 11, 4))       # phone and receiver
    m.shape('lower', (1, 6, 1, 5, 7, 4))          # tray
    m.shape('lower', (12, 6, 8, 16, 11, 12))      # lamp
    m.shape('lower', (0, 6, 6, 4, 12, 9))         # stamp at rest
    return m


# ---------------------------------------------------------------------------------------------------------------------
# Clear glass placeholder (the Mixer's and Cradle's): frame and streaks opaque, the rest clear (cutout)
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
            for yy in range(ya, yb):
                for xx in range(xa, xb):
                    edge = xx in (xa, xb - 1) or yy in (ya, yb - 1)
                    px[xx, yy] = GLASS_FRAME if edge else (0, 0, 0, 0)
            if xb - xa >= 6 and yb - ya >= 6:
                for (sx, sy) in [(xa + 2, ya + 1), (xa + 1, ya + 2), (xb - 2, yb - 3)]:
                    px[sx, sy] = GLASS_STREAK
    im.save(png)


def head_boxes():
    """The villager head at HEAD_SCALE: (name, from, to, material) in model px, facing -Z. The head part's box is
    (-4, 0, -4)..(4, 10, 4) above the neck; the nose (-1, -1, -6)..(1, 3, -4) (vanilla: offset (0, -2, 0), box
    (-1, -1, -6) 2x4x2, model y down)."""
    s = HEAD_SCALE
    x, y, z = HEAD_NECK
    return [('si_head', [x - 4 * s, y, z - 4 * s], [x + 4 * s, y + 10 * s, z + 4 * s], 'stand'),
            ('si_nose', [x - 1 * s, y - 1 * s, z - 6 * s], [x + 1 * s, y + 3 * s, z - 4 * s], 'standi')]


def inject_head(d, m):
    els = {e['name']: e for e in d['elements'] if e.get('type') == 'cube'}
    gu = kit.nid(m.name, 'group', 'standin_head')
    node = next(n for n in d['outliner'] if isinstance(n, dict) and n['uuid'] == gu)
    for name, f, t, mat in head_boxes():
        sw = els['si_mat_' + mat]['faces']
        u = kit.nid(m.name, 'cube', name)
        d['elements'].append({'uuid': u, 'name': name, 'origin': [(f[i] + t[i]) / 2 for i in range(3)], 'color': 0,
                              'type': 'cube', 'from': [round(v, 4) for v in f], 'to': [round(v, 4) for v in t],
                              'box_uv': False, 'faces': {kk: {'uv': list(sw[kk]['uv']), 'texture': 0} for kk in sw},
                              'rescale': False, 'locked': False, 'light_emission': 0, 'render_order': 'default',
                              'allow_mirror_modeling': True, 'autouv': 0, 'visibility': True})
        node['children'].append(u)


def post(path, m):
    with open(path, encoding='utf-8') as fh:
        d = json.load(fh)
    png = os.path.splitext(path)[0] + '_placeholder.png'
    clear_glass(d, png, {c[0] for c in m.cubes if c[3] == 'glass'})
    with open(png, 'rb') as fh:
        d['textures'][0]['source'] = 'data:image/png;base64,' + base64.b64encode(fh.read()).decode()
    inject_head(d, m)
    with open(path, 'w', encoding='utf-8') as fh:
        json.dump(d, fh, indent=1)


if __name__ == '__main__':
    m = model()
    m.write(PATH)
    post(PATH, m)
    print(kit.shapes_java(m))

"""The Hard Hat: geometry for every place it is drawn, and the models the game loads.

    python art/item/hard_hat/hard_hat_geo.py           # prints the sizes, the armour part and checks the layout
    python art/item/hard_hat/paint_hard_hat.py         # paints the sheets and the icon from this layout

The data generator imports this file and writes what `java_models()` returns (it never edits them); the armour
model's Java code is built from `armor_parts()`.

What it is: a company safety helmet in warning yellow. A dome with a stepped crown and a raised centre ridge from
front to back, over a flat brim that sticks out 2 px at the front (the peak) and 1 px elsewhere. A small brass
company plate on the dome's front. The underside of the brim is the dark harness. No straps, no lamp.

Sizes (one per head family; nothing is scaled, a smaller head gets a smaller hat with the same construction):

    size  dome (w x h x d)   brim (w x d)  crown  ridge  heads it fits (head width)
    xs    4 x 2 x 4          6 x 7          2      -      2-3 px: parrot, armadillo, silverfish
    s     6 x 3 x 6          8 x 9          4      2x4    4-5 px: chicken, cat, ocelot, rabbit, goat, allay, endermite
    m     8 x 3 x 8          10 x 11        6      2x6    6-7 px: horses, llamas, wolf, sheep, polar bear, camel
    l     10 x 4 x 10        12 x 13        8      2x8    8 px: humanoids (the armour model), cow, pig, spiders...
    xl    14 x 5 x 14        16 x 17        12     2x12   12-16 px: hoglin, ravager, sniffer, panda, strider...

Frames.
- Hat space (this script): px, origin at the centre of the brim's underside, y UP, the front is -z (north).
- Baked models (`models/entity/hard_hat_<size>.json`, for StaffLayer): Java block model space, the hat's origin at
  (8, 0, 8). StaffLayer, at the fit's `at`/`rot` on a head bone, rotates 180 degrees about Z (bone space is y down)
  and translates (-0.5, 0, -0.5) before drawing it, so north stays the creature's front.
- The item model (`models/item/hard_hat.json`): the size-l hat with its origin at (8, 4, 8), so it sits near the middle
  of the item cube; the GUI shows the flat icon (`item/hard_hat`), every other view the 3D hat. The `head` view is the
  Hat Mount on a jar's lid (`JarRenderer.helmet`: up 0.98, scale 0.62, then the HEAD transform): brim on the lid.
- The armour model (players and the 12 humanoid species whose renderers carry `HumanoidArmorLayer`): the size-l cubes
  on the `head` part of a `HumanoidModel`, texture 64 x 64 (`textures/models/armor/hard_hat_layer_1.png`), box UV.
  A vanilla head is (-4, -8, -4) + (8, 8, 8) with y down; the brim's underside sits at y -5, two rows above the eyes,
  the dome clears the head by 1 px on every side.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))

SIZES = ('xs', 's', 'm', 'l', 'xl')
# dome w, dome h, crown inset (0 = none), ridge (bool), brim front overhang
SPEC = {
    'xs': dict(dw=4, dh=2, front=1, crown=True, ridge=False, crossed=False),
    's': dict(dw=6, dh=2, front=1, crown=True, ridge=True, crossed=True),
    'm': dict(dw=8, dh=3, front=1, crown=True, ridge=True, crossed=True),
    'l': dict(dw=10, dh=3, front=1, crown=True, ridge=True, crossed=True),
    'xl': dict(dw=14, dh=4, front=1, crown=True, ridge=True, crossed=True),
}
ARMOR_SIZE = 'l'
ARMOR_TEX = (64, 64)
SHEET_TEX = (128, 128)
ITEM_YOFF = 4
TEX_NS = 'headinajar:item/'


def boxes(size):
    """The hat's boxes in hat space (y up): [(name, role, from, to, faces)]. Roles name what the painter does; faces
    None = all six. The dome and the brim have stepped corners (they read round from above, like the house casing):
    a main box 1 px short at the front and back and a strip 1 px narrower on each end, so no two tops share an area."""
    s = SPEC[size]
    h = s['dw'] // 2
    b = h + 1
    f = s['front']
    out = []
    if s['crossed']:
        # brim: the sides overhang 1, the peak 1 more at the front; dome: 1 px clear of the head all round
        out.append(('brim', 'brim', (-b, 0, -b), (b, 1, b - 1), None))
        out.append(('brim_front', 'brim', (-b + 1, 0, -(b + f)), (b - 1, 1, -b), None))
        out.append(('brim_back', 'brim', (-b + 1, 0, b - 1), (b - 1, 1, b), None))
        out.append(('dome', 'dome', (-h, 1, -h + 1), (h, 1 + s['dh'], h - 1), None))
        out.append(('dome_front', 'dome', (-h + 1, 1, -h), (h - 1, 1 + s['dh'], -h + 1), None))
        out.append(('dome_back', 'dome', (-h + 1, 1, h - 1), (h - 1, 1 + s['dh'], h), None))
    else:
        out.append(('brim', 'brim', (-b, 0, -(b + f)), (b, 1, b), None))
        out.append(('dome', 'dome', (-h, 1, -h), (h, 1 + s['dh'], h), None))
    top = 1 + s['dh']
    if s['crown']:
        c = h - 1
        out.append(('crown', 'crown', (-c, top, -c), (c, top + 1, c), None))
        top += 1
        if s['ridge']:
            out.append(('ridge', 'ridge', (-1, top, -c), (1, top + 1, c), None))
    return out


def box_uv_size(frm, to):
    w, hh, d = (to[i] - frm[i] for i in range(3))
    return 2 * (w + d), d + hh, (w, hh, d)


def pack(size_list, tex_w):
    """Shelf-pack box-UV islands. size_list: [(key, (W, H))] -> {key: (u, v)}; tallest first."""
    order = sorted(size_list, key=lambda kv: (-kv[1][1], -kv[1][0], kv[0]))
    out = {}
    x = y = shelf = 0
    for key, (W, H) in order:
        if x + W > tex_w:
            x, y = 0, y + shelf
            shelf = 0
        out[key] = (x, y)
        x += W
        shelf = max(shelf, H)
    return out, y + shelf


def sheet_layout():
    """Box-UV islands of every size's boxes on the baked-model sheet: {(size, name): (u, v, (w, h, d), role)}."""
    items = []
    meta = {}
    for size in SIZES:
        for name, role, f, t, _ in boxes(size):
            W, H, dims = box_uv_size(f, t)
            items.append(((size, name), (W, H)))
            meta[(size, name)] = (dims, role)
    at, used = pack(items, SHEET_TEX[0])
    assert used <= SHEET_TEX[1], 'sheet too small: %d rows' % used
    return {k: (at[k][0], at[k][1], meta[k][0], meta[k][1]) for k in at}


def armor_layout():
    """Box-UV islands of the size-l boxes on the armour sheet: {name: (u, v, (w, h, d), role)}."""
    items = []
    meta = {}
    for name, role, f, t, _ in boxes(ARMOR_SIZE):
        W, H, dims = box_uv_size(f, t)
        items.append((name, (W, H)))
        meta[name] = (dims, role)
    at, used = pack(items, ARMOR_TEX[0])
    assert used <= ARMOR_TEX[1], 'armour sheet too small: %d rows' % used
    return {k: (at[k][0], at[k][1], meta[k][0], meta[k][1]) for k in at}


def armor_parts():
    """The armour model's cubes on the vanilla head part (y down): [(name, texOffs, origin, size, faces)], for
    `CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, h, d)` (or, when faces is 'sides', the overload with
    the visible faces `EnumSet.of(NORTH, SOUTH, EAST, WEST)`). Brim underside at head y -5."""
    lay = armor_layout()
    out = []
    for name, role, f, t, faces in boxes(ARMOR_SIZE):
        u, v, (w, hh, d), _ = lay[name]
        # hat y up -> head y down, brim underside (hat y 0) at head y -5
        origin = (f[0], -5 - t[1], f[2])
        out.append((name, (u, v), origin, (w, hh, d), faces))
    return out


def _face_uvs(u, v, w, h, d, tw):
    """Java face UVs (0..16 of the sheet) for a box-UV island, matching vanilla's entity box layout, so the baked
    model and the armour model show the same painted pixels. Block faces: up = the visual top, east = the creature's
    right (entity WEST island), west = the creature's left (entity EAST island)."""
    k = 16.0 / tw

    def r(a, b, c, e):
        return [round(a * k, 4), round(b * k * KV, 4), round(c * k, 4), round(e * k * KV, 4)]
    return {
        'up': r(u + d + w, v + d, u + d, v),
        'down': r(u + d + 2 * w, v, u + d + w, v + d),
        'north': r(u + d, v + d, u + d + w, v + d + h),
        'south': r(u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
        'east': r(u, v + d, u + d, v + d + h),
        'west': r(u + d + w, v + d, u + 2 * d + w, v + d + h),
    }


# a Java model's UV is 0..16 across the texture on both axes (the sheet is square)
KV = SHEET_TEX[0] / SHEET_TEX[1]


def _elements(size, yoff=0):
    lay = sheet_layout()
    els = []
    for name, role, f, t, vis in boxes(size):
        u, v, (w, h, d), _ = lay[(size, name)]
        uvs = _face_uvs(u, v, w, h, d, SHEET_TEX[0])
        faces = {}
        for face, uv in uvs.items():
            fd = {'uv': uv, 'texture': '#hat'}
            if face == 'down' and role != 'brim':
                continue  # resting on the box below: never seen
            if vis == ('sides',) and face in ('up', 'down'):
                continue
            faces[face] = fd
        els.append({'name': name, 'from': [f[0] + 8, f[1] + yoff, f[2] + 8], 'to': [t[0] + 8, t[1] + yoff, t[2] + 8],
                    'faces': faces})
    return els


DISPLAY = {
    'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]},
    'thirdperson_lefthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]},
    'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
    'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.5, 0.5, 0.5]},
    'fixed': {'rotation': [-90, 0, 0], 'translation': [0, 0, -2], 'scale': [0.75, 0.75, 0.75]},
    # the Hat Mount: JarRenderer.helmet moves up 0.98 and scales 0.62; this puts the brim's underside on the jar's
    # lid (y 14 px) at about the lid's width (brim 12 x 0.62 x 1.6 = 11.9 px)
    'head': {'rotation': [0, 0, 0], 'translation': [0, 3.7, 0], 'scale': [1.6, 1.6, 1.6]},
}


def java_models():
    """{resource path: model}: the five baked hats for StaffLayer and the item model."""
    out = {}
    tex = {'hat': TEX_NS + 'hard_hat_model', 'particle': TEX_NS + 'hard_hat_model'}
    for size in SIZES:
        out['assets/headinajar/models/entity/hard_hat_%s.json' % size] = {
            'textures': dict(tex), 'elements': _elements(size)}
    out['assets/headinajar/models/item/hard_hat_3d.json'] = {
        'textures': dict(tex), 'elements': _elements('l', ITEM_YOFF), 'display': DISPLAY}
    out['assets/headinajar/models/item/hard_hat.json'] = {
        'loader': 'neoforge:separate_transforms',
        'base': {'parent': 'headinajar:item/hard_hat_3d'},
        'perspectives': {'gui': {'parent': 'minecraft:item/generated', 'textures': {'layer0': TEX_NS + 'hard_hat'}}},
    }
    return out


def armor_java():
    """The armour model's part as Java lines (for the integrator's notes; the generator does not write Java)."""
    lines = ['PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()']
    for name, (u, v), (x, y, z), (w, h, d), faces in armor_parts():
        extra = ', SIDES' if faces == ('sides',) else ''
        lines.append('        .texOffs(%d, %d).addBox(%gF, %gF, %gF, %gF, %gF, %gF%s)   // %s' % (u, v, x, y, z, w, h, d, extra, name))
    lines.append('        , PartPose.ZERO);   // texture %d x %d; SIDES = EnumSet.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)' % ARMOR_TEX)
    return '\n'.join(lines)


if __name__ == '__main__':
    for size in SIZES:
        print(size, [(n, f, t) for n, _, f, t, _ in boxes(size)])
    print(armor_java())
    lay = sheet_layout()
    print('sheet islands:', len(lay))
    if '--json' in sys.argv:
        print(json.dumps(java_models(), indent=1))

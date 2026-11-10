"""Writes a GeckoLib-format .bbmodel (Blockbench 5.0 file layout) from a generator's bones, cubes and locators.

Shared by the frame and module generators under art/mecha/. The file is the same shape the Blockbench tool server
writes (bbmodel_create + bbmodel_edit add_group/add_cube/add_locator + bbmodel_add_texture); the result is checked
with bbmodel_validate afterwards. UUIDs are derived from the node names, so a rebuild keeps every animation's bone
references valid. Existing animations in the target file are kept unless replaced by the caller.

bones:    [(name, pivot, parent or None, rest rotation or None)]
cubes:    [(name, from, to, material, parent bone, {inflate?})]  box UV at uv_offsets[name]
locators: [(name, position in the modelling frame, parent bone)]
"""
import base64
import json
import os
import uuid as uuidlib

NS = uuidlib.UUID('6f1c1d2e-5a8b-4c3d-9e7f-0a1b2c3d4e5f')


def nid(kind, name, model):
    return str(uuidlib.uuid5(NS, '%s/%s/%s' % (model, kind, name)))


def box_faces(f, t, off):
    w, h, d = [max(1, round(abs(t[i] - f[i]))) for i in range(3)]
    ox, oy = off
    return {
        'north': {'uv': [ox + d, oy + d, ox + d + w, oy + d + h], 'texture': 0},
        'east': {'uv': [ox, oy + d, ox + d, oy + d + h], 'texture': 0},
        'south': {'uv': [ox + 2 * d + w, oy + d, ox + 2 * d + 2 * w, oy + d + h], 'texture': 0},
        'west': {'uv': [ox + d + w, oy + d, ox + 2 * d + w, oy + d + h], 'texture': 0},
        'up': {'uv': [ox + d + w, oy + d, ox + d, oy], 'texture': 0},
        'down': {'uv': [ox + d + 2 * w, oy, ox + d + w, oy + d], 'texture': 0},
    }


def build(path, model_name, bones, cubes, locators, uv_offsets, png_path, res, keep_animations=True):
    groups, elements = [], []
    children = {None: []}
    gid = {}
    for name, pivot, parent, rot in bones:
        u = nid('group', name, model_name)
        gid[name] = u
        groups.append({'uuid': u, 'name': name, 'origin': [float(x) if x != int(x) else int(x) for x in pivot],
                       'rotation': list(rot) if rot else [0, 0, 0], 'export': True, 'visibility': True,
                       'mirror_uv': False, 'reset': False, 'color': 0, 'autouv': 0, 'shade': True, 'locked': False})
        children.setdefault(name, [])
        children.setdefault(parent, []).append(('g', name))
    for name, f, t, mat, parent, kw in cubes:
        u = nid('cube', name, model_name)
        e = {'uuid': u, 'name': name, 'origin': [(f[i] + t[i]) / 2 for i in range(3)], 'color': 0, 'type': 'cube',
             'from': list(f), 'to': list(t), 'box_uv': True, 'uv_offset': list(uv_offsets[name]),
             'faces': box_faces(f, t, uv_offsets[name]), 'rescale': False, 'locked': False, 'light_emission': 0,
             'render_order': 'default', 'allow_mirror_modeling': True, 'autouv': 0}
        if kw.get('inflate'):
            e['inflate'] = kw['inflate']
        elements.append(e)
        children.setdefault(parent, []).append(('e', u))
    for name, pos, parent in locators:
        u = nid('locator', name, model_name)
        elements.append({'uuid': u, 'name': name, 'type': 'locator', 'position': list(pos), 'rotation': [0, 0, 0],
                         'ignore_inherited_scale': False, 'visibility': True, 'locked': False})
        children.setdefault(parent, []).append(('e', u))

    def tree(name):
        out = []
        for kind, ref in children.get(name, []):
            if kind == 'e':
                out.append(ref)
            else:
                out.append({'uuid': gid[ref], 'isOpen': False, 'children': tree(ref)})
        return out

    with open(png_path, 'rb') as fh:
        src = 'data:image/png;base64,' + base64.b64encode(fh.read()).decode()
    tex = {'path': '', 'name': os.path.basename(png_path), 'folder': '', 'namespace': '', 'id': '0',
           'width': res[0], 'height': res[1], 'uv_width': res[0], 'uv_height': res[1], 'particle': False,
           'use_as_default': False, 'layers_enabled': False, 'sync_to_project': '', 'render_mode': 'default',
           'render_sides': 'auto', 'frame_time': 1, 'frame_order_type': 'loop', 'frame_order': '',
           'frame_interpolate': False, 'visible': True, 'internal': True, 'saved': False,
           'uuid': nid('texture', 'sheet', model_name), 'relative_path': '', 'source': src}
    anims = []
    if keep_animations and os.path.exists(path):
        with open(path, encoding='utf-8') as fh:
            anims = json.load(fh).get('animations', [])
    model = {'meta': {'format_version': '5.0', 'model_format': 'geckolib_model', 'box_uv': True},
             'name': model_name, 'model_identifier': model_name, 'visible_box': [1, 1, 0],
             'resolution': {'width': res[0], 'height': res[1]}, 'elements': elements, 'groups': groups,
             'outliner': tree(None), 'textures': [tex], 'animations': anims, 'variable_placeholders': '',
             'variable_placeholder_buttons': [], 'timeline_setups': [], 'unhandled_root_fields': {}}
    with open(path, 'w', encoding='utf-8') as fh:
        json.dump(model, fh, indent=1)
    return gid

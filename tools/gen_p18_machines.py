"""The machine line, first three (Galvanic Pile, Saline Mixer, Centrifuge) on the shared machine kit: models,
blockstates, GeckoLib geometry and clips, sounds and lines. Imported by gen_data.py.

Each machine's source is art/machines/<id>/<id>.bbmodel (geometry from its *_geo.py, clips from its *_anim.py; never
edit an export, regenerate). This file turns it into what the game loads:

- the group `static` (minus its `state_*` groups) becomes the baked block model models/block/<id>.json, drawn by the
  chunk renderer, on the painted sheet textures/block/machine/<id>.png;
- each `state_*` group becomes blockstate parts: the lamp lens (one model per lamp value, the lit ones full-bright),
  the Mixer's sight glass per water level and its saline per level (the surface ripples, or swirls while it works);
- the blockstate is a multipart: the body, then a part per property, all turned by facing;
- every other group but `standin_*` becomes the GeckoLib geometry geo/block/<id>.geo.json (the moving parts), and the
  clips animations/block/<id>.animation.json through tools/bb_export_anim.py;
- sounds.json gets the kit's and the machines' events from tools/snd/events.json.
"""
import json
import os
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..')
RES = os.path.join(ROOT, 'src', 'main', 'resources')
ART = os.path.join(ROOT, 'art', 'machines')
SND_EVENTS = os.path.join(HERE, 'snd', 'events.json')

MACHINES = ['galvanic_pile', 'saline_mixer', 'centrifuge', 'cradle', 'body_printer', 'gene_splicer',
            'charging_dock', 'tesla_node', 'injection_rig', 'dna_sequencer', 'cerebral_array', 'employee_mount',
            'relay_mast', 'relay_mast_mk2', 'signal_jammer', 'turret_pedestal', 'body_locker']
# batch three, added to MACHINES as each is wired in the game: 'cerebral_array', 'employee_mount', 'dna_sequencer',
#
# one block tall: no top cell, the whole body on the machine's own blockstate
ONE_BLOCK = {'employee_mount', 'signal_jammer'}
# static elements drawn as blockstate parts of their own (on their own sprites), not in the body
OWN_PART = {'relay_mast': {'beacon_lens'}, 'signal_jammer': {'grille'}}
# the block each machine's art is for (the art folder and the model files keep the short name)
BLOCK = {'galvanic_pile': 'galvanic_pile', 'saline_mixer': 'saline_mixer', 'centrifuge': 'centrifuge',
         'cradle': 'life_support_cradle', 'body_printer': 'body_printer', 'gene_splicer': 'gene_splicer',
         'cerebral_array': 'cerebral_array', 'employee_mount': 'employee_mount', 'dna_sequencer': 'dna_sequencer',
         'injection_rig': 'injection_rig', 'tesla_node': 'tesla_node', 'charging_dock': 'charging_dock',
         'relay_mast': 'relay_mast', 'relay_mast_mk2': 'relay_mast_mk2', 'signal_jammer': 'signal_jammer',
         'turret_pedestal': 'turret_pedestal', 'body_locker': 'body_locker'}
# the top cell carries two generic levels: a machine's own property is named there by these (`saline` 0-7, `stab` 0-2)
TOP_ALIAS = {'broth': 'saline', 'serum': 'saline', 'counter': 'stab'}
TOP_VALUE = {}
FACINGS = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
LAMPS = ['off', 'ready', 'wait', 'work', 'low', 'fault']
LIT = {'ready', 'wait', 'work', 'low', 'fault'}
TEX = 'headinajar:block/machine/'
SOUND_FAMILIES = ('machine.', 'pile.', 'mixer.', 'centrifuge.', 'cradle.', 'printer.', 'splicer.',
                  'array.', 'mount.', 'sequencer.', 'rig.', 'tesla.', 'dock.', 'mast.', 'jammer.', 'turret.', 'locker.')

LINES = {
    # an old two-cell machine under a see-through block: what is wrong, then what to do
    'message.headinajar.machine.cut_off': ('%1$s stands cut off: %2$s above it is in the way. Clear the space above it and it stands at full height.',
                                           '%1$s: a máquina está cortada, %2$s acima está no caminho. Libere o espaço acima e ela fica inteira.'),
    'refusal.headinajar.machine.no_headroom': ('No headroom. Needs two blocks of height.',
                                               'Sem espaço acima. Precisa de dois blocos de altura.'),
    'refusal.headinajar.dock.empty': ('The Charging Dock is empty. Feed it from a %3$s or a cable.',
                                      'A Base de Recarga está vazia. Alimente com uma %3$s ou um cabo.'),
    'refusal.headinajar.lab.rig.hold_still': ('Hold still: the shot was called off. Nothing was used.',
                                              'Fique parado: a injeção foi cancelada. Nada foi gasto.'),
    'refusal.headinajar.lab.rig.busy': ('The rig is giving a shot. Wait for it.', 'O injetor está aplicando uma injeção. Espere.'),
    'refusal.headinajar.pile.not_fuel': ('The Galvanic Pile burns %2$s and %3$s, not %1$s.',
                                         'A Pilha Galvânica queima %2$s e %3$s, não %1$s.'),
    'refusal.headinajar.mixer.nothing_to_fill': ('Not enough %2$s in the Saline Mixer to fill a %1$s.',
                                                 'Não há %2$s suficiente no Misturador de Soro para encher um %1$s.'),
    'block.headinajar.machine_top': ('Machine (upper half)', 'Máquina (metade de cima)'),
}


def _num(v, places=6):
    v = round(v, places)
    return int(v) if v == int(v) else v


def _load(name):
    with open(os.path.join(ART, name, name + '.bbmodel'), encoding='utf-8') as f:
        return json.load(f)


def _tree(model):
    return {g['uuid']: g for g in model['groups']}, {e['uuid']: e for e in model['elements']}


def _find(model, name):
    groups, _ = _tree(model)

    def walk(nodes):
        for n in nodes:
            if isinstance(n, str):
                continue
            if groups[n['uuid']]['name'] == name:
                return n
            r = walk(n['children'])
            if r:
                return r
        return None
    return walk(model['outliner'])


def _cubes(model, node):
    _, elements = _tree(model)
    return [elements[c] for c in node['children'] if isinstance(c, str) and elements[c]['type'] == 'cube']


def _element(model, e, texture_for):
    """A Java block-model element from a Blockbench cube: x and z + 8, UVs from `texture_for(face, data)`."""
    faces = {}
    for face, data in e['faces'].items():
        if data.get('texture') is None:
            continue
        tex, uv = texture_for(face, data)
        faces[face] = {'uv': [_num(x) for x in uv], 'texture': tex}
        if data.get('rotation'):
            faces[face]['rotation'] = data['rotation']
    el = {'name': e['name'], 'from': [_num(e['from'][0] + 8), _num(e['from'][1]), _num(e['from'][2] + 8)],
          'to': [_num(e['to'][0] + 8), _num(e['to'][1]), _num(e['to'][2] + 8)], 'faces': faces}
    rot = e.get('rotation') or [0, 0, 0]
    if any(rot):
        axis = 'xyz'[[i for i in range(3) if rot[i]][0]]
        el['rotation'] = {'angle': rot['xyz'.index(axis)], 'axis': axis,
                          'origin': [_num(e['origin'][0] + 8), _num(e['origin'][1]), _num(e['origin'][2] + 8)]}
    return el


def _sheet_uv(model):
    res = model['resolution']
    su, sv = 16 / res['width'], 16 / res['height']

    def f(face, data):
        u1, v1, u2, v2 = data['uv']
        return '#0', [u1 * su, v1 * sv, u2 * su, v2 * sv]
    return f


def _own_uv(e, tex):
    """A cube on its own 16x16 sprite, its box UV laid out at (0, 0) of it (the lens, the sight glass)."""
    ou, ov = e.get('uv_offset', [0, 0])

    def f(face, data):
        u1, v1, u2, v2 = data['uv']
        return tex, [u1 - ou, v1 - ov, u2 - ou, v2 - ov]
    return f


def _model(name, elements, textures, parent='minecraft:block/block', ao=True):
    m = {'parent': parent, 'render_type': 'minecraft:cutout', 'textures': textures, 'elements': elements}
    if not ao:
        m['ambientocclusion'] = False
    return m


def _sheet(name):
    return {'0': TEX + name, 'particle': TEX + name}


# Painted faces that are not on the sheet (the paint's notes, section 4)
FELT = {'felt_1': [0, 0, 6, 1], 'felt_2': [0, 2, 6, 3]}


CUT = 16


def split(elements, name=None):
    if name in ONE_BLOCK:
        return list(elements), []
    return _split(elements)


def _split(elements):
    """Cuts every element at the top of the machine's own cell: (lower, upper), the upper moved down a cell. A
    quad that lies outside its block is lit by vanilla's renderer from the block's own cell and its ambient occlusion
    extrapolated (drawn near black), so the hidden top cell draws the upper half of the body itself, lit as its own."""
    lower, upper = [], []
    for el in elements:
        y0, y1 = el['from'][1], el['to'][1]
        if y1 <= CUT:
            lower.append(el)
            continue
        if y0 >= CUT:
            upper.append(_shift(el))
            continue
        assert 'rotation' not in el, el['name'] + ': a turned element across the cell line'
        k = (y1 - CUT) / (y1 - y0)
        lo = {'name': el['name'] + '_lo', 'from': list(el['from']), 'to': [el['to'][0], CUT, el['to'][2]], 'faces': {}}
        hi = {'name': el['name'] + '_hi', 'from': [el['from'][0], CUT, el['from'][2]], 'to': list(el['to']), 'faces': {}}
        for face, d in el['faces'].items():
            if face == 'up':
                hi['faces'][face] = d
            elif face == 'down':
                lo['faces'][face] = d
            else:
                u1, v1, u2, v2 = d['uv']
                vs = _num(v1 + (v2 - v1) * k)
                hi['faces'][face] = dict(d, uv=[u1, v1, u2, vs])
                lo['faces'][face] = dict(d, uv=[u1, vs, u2, v2])
        lower.append(lo)
        upper.append(_shift(hi))
    return lower, upper


def _shift(el):
    out = dict(el, **{'from': [el['from'][0], _num(el['from'][1] - CUT), el['from'][2]],
                      'to': [el['to'][0], _num(el['to'][1] - CUT), el['to'][2]]})
    return out


def body(model, name):
    """The whole baked body (every cube of `static`, not its state groups), uncut."""
    static = _find(model, 'static')
    sheet = _sheet_uv(model)
    els = []
    textures = _sheet(name)
    for e in _cubes(model, static):
        if e['name'] in OWN_PART.get(name, ()):
            continue
        if name == 'galvanic_pile' and e['name'] in FELT:
            uv = FELT[e['name']]
            textures['felt'] = TEX + 'galvanic_pile_felt'

            def f(face, data, uv=uv):
                return ('#felt', uv) if face in ('north', 'east', 'south', 'west') else sheet(face, data)
            els.append(_element(model, e, f))
        else:
            els.append(_element(model, e, sheet))
    return _model(name, els, textures)


def _part(name, kind, elements, textures, when, write, ao=True, parent=None):
    """One blockstate part of a machine, cut at the cell line: the lower half on the machine's own blockstate, the
    upper half on the top cell's (which carries the same property). With a parent, the part is a child that only names
    textures (a lamp state), and its two halves are children of the parent's two halves."""
    if parent is not None:
        lo_parent, hi_parent = PARENTS[(name, parent)]
        if lo_parent:
            write(f'assets/headinajar/models/block/{name}_{kind}.json', {'parent': f'headinajar:block/{name}_{parent}', 'textures': textures})
            LOWER_PARTS.append((when, f'headinajar:block/{name}_{kind}'))
        if hi_parent:
            write(f'assets/headinajar/models/block/{name}_top_{kind}.json', {'parent': f'headinajar:block/{name}_top_{parent}', 'textures': textures})
            TOP_PARTS.append((dict({'machine': BLOCK[name]}, **_top(when)), f'headinajar:block/{name}_top_{kind}'))
        return
    lo, hi = split(elements, name)
    if lo:
        write(f'assets/headinajar/models/block/{name}_{kind}.json', _model(name, lo, textures, ao=ao))
        LOWER_PARTS.append((when, f'headinajar:block/{name}_{kind}'))
    if hi:
        write(f'assets/headinajar/models/block/{name}_top_{kind}.json', _model(name, hi, textures, ao=ao))
        TOP_PARTS.append((dict({'machine': BLOCK[name]}, **_top(when)), f'headinajar:block/{name}_top_{kind}'))
    PARENTS[(name, kind)] = (bool(lo), bool(hi))


def _top(when):
    '''A condition as the top cell names it (its generic levels).'''
    return {TOP_ALIAS.get(k, k): TOP_VALUE.get((k, v), v) for k, v in when.items()}


# which halves a parent part has, by (machine, kind)
PARENTS = {}
LOWER_PARTS = []


def _sprite(e, tex, scale_u=1.0):
    """A cube on its own sprite, its box UV laid out at (0, 0) of it; scale_u for a sprite wider than 16 px."""
    ou, ov = e.get('uv_offset', [0, 0])

    def f(face, data):
        u1, v1, u2, v2 = data['uv']
        return tex, [(u1 - ou) * scale_u, v1 - ov, (u2 - ou) * scale_u, v2 - ov]
    return f


def state_models(model, name, write):
    """Every blockstate part of a machine (both halves); returns the lower parts as (when, model id)."""
    LOWER_PARTS.clear()
    sheet = _sheet_uv(model)
    lens = _cubes(model, _find(model, 'state_lamp'))[0]
    own_lamp = name == 'employee_mount'
    if own_lamp:
        lens_uv = _sprite(lens, '#lens')
    elif name == 'turret_pedestal':
        # its lens faces east (a 1x2x2 element): the east face takes the kit sprite's front
        base = _own_uv(lens, '#lens')

        def lens_uv(face, data):
            return ('#lens', [1, 1, 3, 3]) if face == 'east' else base(face, data)
    else:
        lens_uv = _own_uv(lens, '#lens')
    unlit = _element(model, lens, lens_uv)
    lit = dict(unlit, **{'neoforge_data': {'block_light': 15, 'sky_light': 15}})
    lit['shade'] = False
    lo, hi = split([unlit], name)
    for half, els in (('', lo), ('_top', hi)):
        if els:
            write(f'assets/headinajar/models/block/{name}{half}_lamp.json', _model(name, els, {'particle': TEX + name}))
    lo, hi = split([lit], name)
    for half, els in (('', lo), ('_top', hi)):
        if els:
            write(f'assets/headinajar/models/block/{name}{half}_lamp_lit.json', _model(name, els, {'particle': TEX + name}, ao=False))
    PARENTS[(name, 'lamp')] = (bool(lo), bool(hi))
    PARENTS[(name, 'lamp_lit')] = (bool(lo), bool(hi))
    for st in LAMPS:
        sprite = TEX + (f'employee_mount_lamp_{st}' if own_lamp else f'kit/lamp_{st}')
        _part(name, f'lamp_{st}', None, {'lens': sprite}, {'lamp': st}, write,
              parent='lamp_lit' if st in LIT else 'lamp')
    if name == 'saline_mixer':
        face = _cubes(model, _find(model, 'state_water'))[0]
        for n in range(5):
            _part(name, f'water_{n}', [_element(model, face, _own_uv(face, '#water'))],
                  {'water': TEX + f'saline_mixer_water_{n}', 'particle': TEX + name}, {'water': str(n)}, write)
        for n in range(1, 5):
            # the surface stays on the sheet: under the lid the animated ripple is never seen (looked for at two blocks)
            e = _cubes(model, _find(model, f'state_saline_{n}'))[0]
            _part(name, f'saline_{n}', [_element(model, e, sheet)], _sheet(name), {'saline': str(n)}, write)
    if name == 'cradle':
        # the saline bag: level 0 draws the empty bag, every level its own elements on the sheet
        for n in range(5):
            els = [_element(model, e, sheet) for e in _cubes(model, _find(model, f'state_saline_{n}'))]
            _part(name, f'saline_{n}', els, _sheet(name), {'saline': str(n)}, write)
    if name == 'body_printer':
        face = _cubes(model, _find(model, 'state_saline'))[0]
        for n in range(5):
            _part(name, f'saline_{n}', [_element(model, face, _sprite(face, '#saline'))],
                  {'saline': TEX + f'body_printer_saline_{n}', 'particle': TEX + name}, {'saline': str(n)}, write)
    if name == 'gene_splicer':
        glass = _cubes(model, _find(model, 'state_stab'))[0]
        for n in range(3):
            _part(name, f'stab_{n}', [_element(model, glass, _sprite(glass, '#stab'))],
                  {'stab': TEX + f'gene_splicer_stab_{n}', 'particle': TEX + name}, {'stab': str(n)}, write)
        strand = _cubes(model, _find(model, 'state_stitch'))[0]
        for n in range(4):
            # the stitch sprite is 32 px wide: a block model's u runs 0-16 across it
            _part(name, f'stitch_{n}', [_element(model, strand, _sprite(strand, '#stitch', 0.5))],
                  {'stitch': TEX + f'gene_splicer_stitch_{n}', 'particle': TEX + name}, {'stitch': str(n)}, write)
    if name in OWN_PART:
        # the Mk I's aviation lamp (lit while powered, the lamp not off) and the Jammer's grille (static while jamming)
        part = [e for e in _cubes(model, _find(model, 'static')) if e['name'] in OWN_PART[name]][0]
        for st in LAMPS:
            if name == 'relay_mast':
                sprite, lit = ('relay_mast_beacon_lit', True) if st != 'off' else ('relay_mast_beacon_off', False)
            else:
                sprite, lit = ('signal_jammer_grille_jam' if st == 'work' else 'signal_jammer_grille_idle'), False
            el = _element(model, part, _sprite(part, '#own'))
            if lit:
                el = dict(el, **{'neoforge_data': {'block_light': 15, 'sky_light': 15}})
                el['shade'] = False
            _part(name, f'own_{st}', [el], {'own': TEX + sprite, 'particle': TEX + name}, {'lamp': st}, write, ao=not lit)
    if name == 'cerebral_array':
        face = _cubes(model, _find(model, 'state_broth'))[0]
        for n in range(5):
            _part(name, f'broth_{n}', [_element(model, face, _sprite(face, '#broth'))],
                  {'broth': TEX + f'cerebral_array_broth_{n}', 'particle': TEX + name}, {'broth': str(n)}, write)
    if name == 'injection_rig':
        face = _cubes(model, _find(model, 'state_counter'))[0]
        for st in ('still', 'rolling'):
            _part(name, f'counter_{st}', [_element(model, face, _sprite(face, '#counter'))],
                  {'counter': TEX + f'injection_rig_counter_{st}', 'particle': TEX + name}, {'counter': '1' if st == 'rolling' else '0'}, write)
        su = 16 / model['resolution']['width']

        def strip(face, data):
            # the globe's sprite is the sheet's rows 80-96: u kept (in sheet px x 16/128), v moved up by 80 (px)
            u1, v1, u2, v2 = data['uv']
            return '#globe', [_num(u1 * su), _num(v1 - 80), _num(u2 * su), _num(v2 - 80)]
        for n in range(8):
            els = []
            for e in _cubes(model, _find(model, 'state_serum')):
                el = _element(model, e, strip)
                if n > 0:
                    for d in el['faces'].values():
                        d['tintindex'] = 0
                els.append(el)
            _part(name, f'serum_{n}', els, {'globe': TEX + ('injection_rig_globe_lit' if n else 'injection_rig_globe_off'),
                                            'particle': TEX + name}, {'serum': str(n)}, write)
    return list(LOWER_PARTS)


# the top cell's parts, gathered over the machines: (when, model)
TOP_PARTS = []


def top_blockstate():
    multipart = []
    for facing, y in FACINGS.items():
        rot = {'y': y} if y else {}
        for when, model in TOP_PARTS:
            multipart.append({'when': dict({'facing': facing}, **when), 'apply': dict({'model': model}, **rot)})
    return {'multipart': multipart}


def blockstate(name, parts):
    multipart = []
    for facing, y in FACINGS.items():
        rot = {'y': y} if y else {}
        multipart.append({'when': {'facing': facing}, 'apply': dict({'model': f'headinajar:block/{name}'}, **rot)})
        for when, model in parts:
            multipart.append({'when': dict({'facing': facing}, **when), 'apply': dict({'model': model}, **rot)})
    return {'multipart': multipart}


def item_model(name):
    # two blocks tall: smaller in a slot, standing on the hotbar line; the whole body, uncut, lamp green
    return {'parent': f'headinajar:block/{name}_whole', 'display': {
        'gui': {'rotation': [30, 225, 0], 'translation': [0, -3.25, 0], 'scale': [0.4, 0.4, 0.4]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.2, 0.2, 0.2]},
        'fixed': {'rotation': [0, 0, 0], 'translation': [0, -4, 0], 'scale': [0.4, 0.4, 0.4]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 1.5, 0], 'scale': [0.25, 0.25, 0.25]},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.3, 0.3, 0.3]},
        'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [0.3, 0.3, 0.3]}}}


def _neg_x(v):
    return [_num(-v[0], 4), _num(v[1], 4), _num(v[2], 4)]


def geo(model, name):
    """Bedrock geometry of every group but `static` and the stand-ins (x mirrored, rotation x and y negated, as
    Blockbench's Bedrock codec writes it and GeckoLib reads it back). Root locators are left out (no empty bb_main)."""
    groups, elements = _tree(model)
    bones = []

    def cube(e):
        size = [e['to'][i] - e['from'][i] for i in range(3)]
        c = {'origin': [_num(-e['to'][0], 4), _num(e['from'][1], 4), _num(e['from'][2], 4)], 'size': [_num(s, 4) for s in size],
             'uv': e['uv_offset']}
        if e.get('inflate'):
            c['inflate'] = e['inflate']
        rot = e.get('rotation') or [0, 0, 0]
        if any(rot):
            c['pivot'] = _neg_x(e['origin'])
            c['rotation'] = [_num(-rot[0], 4), _num(-rot[1], 4), _num(rot[2], 4)]
        if e.get('mirror_uv'):
            c['mirror'] = True
        return c

    def walk(nodes, parent):
        for node in nodes:
            if isinstance(node, str):
                continue
            g = groups[node['uuid']]
            if g['name'] == 'static' or g['name'].startswith('standin'):
                continue
            bone = {'name': g['name']}
            if parent:
                bone['parent'] = parent
            bone['pivot'] = _neg_x(g['origin'])
            rot = g.get('rotation') or [0, 0, 0]
            if any(rot):
                bone['rotation'] = [_num(-rot[0], 4), _num(-rot[1], 4), _num(rot[2], 4)]
            cubes, locators = [], {}
            for child in node['children']:
                if isinstance(child, str):
                    e = elements[child]
                    if e['type'] == 'cube':
                        cubes.append(cube(e))
                    elif e['type'] == 'locator':
                        locators[e['name']] = _neg_x(e['position'])
            if cubes:
                bone['cubes'] = cubes
            if locators:
                bone['locators'] = locators
            bones.append(bone)
            walk(node['children'], g['name'])

    walk(model['outliner'], None)
    res = model['resolution']
    return {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': f'geometry.{name}', 'texture_width': res['width'], 'texture_height': res['height'],
                        'visible_bounds_width': 3, 'visible_bounds_height': 3, 'visible_bounds_offset': [0, 1, 0]},
        'bones': bones}]}


def animations(name, geo_path):
    src = os.path.join(ART, name, name + '.bbmodel')
    with tempfile.TemporaryDirectory() as tmp:
        out = os.path.join(tmp, 'a.json')
        r = subprocess.run([sys.executable, os.path.join(HERE, 'bb_export_anim.py'), src, '-o', out, '--geo', geo_path],
                           capture_output=True, text=True)
        if r.returncode != 0:
            raise SystemExit(f'bb_export_anim failed for {name}:\n' + r.stderr)
        with open(out, encoding='utf-8') as f:
            return json.load(f)


def sounds(EN, PT, write):
    """The kit's and the machines' events into sounds.json (files, attenuation, subtitles from the sound hand-off)."""
    with open(SND_EVENTS, encoding='utf-8') as f:
        events = json.load(f)['events']
    path = os.path.join(RES, 'assets', 'headinajar', 'sounds.json')
    with open(path, encoding='utf-8') as f:
        table = json.load(f)
    for e in events:
        name = e['id'].split(':', 1)[1]
        if not name.startswith(SOUND_FAMILIES):
            continue
        files = e.get('files') or e.get('vanilla_files')
        entry = {'sounds': [{'name': f, 'attenuation_distance': e['attenuation']} for f in files]}
        if e.get('subtitle'):
            entry['subtitle'] = f'subtitles.headinajar.{name}'
            EN[entry['subtitle']], PT[entry['subtitle']] = e['subtitle']['en_us'], e['subtitle']['pt_br']
        table[name] = entry
    write('assets/headinajar/sounds.json', table)


def run(EN, PT, write, shaped):
    for key, (en, pt) in LINES.items():
        EN[key], PT[key] = en, pt
    for name in MACHINES:
        model = _load(name)
        whole = body(model, name)
        lower, upper = split(whole['elements'], name)
        write(f'assets/headinajar/models/block/{name}.json', dict(whole, elements=lower))
        if name not in ONE_BLOCK:
            write(f'assets/headinajar/models/block/{name}_top.json', dict(whole, elements=upper))
            TOP_PARTS.append(({'machine': BLOCK[name]}, f'headinajar:block/{name}_top'))
        lens = _cubes(model, _find(model, 'state_lamp'))[0]
        own = name == 'employee_mount'
        lens_uv = _sprite(lens, '#lens') if own else _own_uv(lens, '#lens')
        write(f'assets/headinajar/models/block/{name}_whole.json',
              dict(whole, elements=whole['elements'] + [_element(model, lens, lens_uv)],
                   textures=dict(whole['textures'], lens=TEX + ('employee_mount_lamp_ready' if own else 'kit/lamp_ready'))))
        parts = state_models(model, name, write)
        write(f'assets/headinajar/blockstates/{BLOCK[name]}.json', blockstate(name, parts))
        write(f'assets/headinajar/models/item/{BLOCK[name]}.json', item_model(name))
        write(f'assets/headinajar/geo/block/{name}.geo.json', geo(model, name))
        write(f'assets/headinajar/animations/block/{name}.animation.json',
              animations(name, os.path.join(RES, 'assets', 'headinajar', 'geo', 'block', f'{name}.geo.json')))
    write('assets/headinajar/blockstates/machine_top.json', top_blockstate())
    sounds(EN, PT, write)

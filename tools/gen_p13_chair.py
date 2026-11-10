"""The Separation Chair (DOLORES): its models, animations, texture, sounds and lines. Imported by gen_data.py.

The model's source is art/chair/separation_chair.bbmodel (geometry) with its clips from art/chair/chair_anim.py. This
file turns it into what the game loads, so a new revision of the model drops in by running gen_data.py again:

- the group `static` becomes the baked block model (models/block/separation_chair.json), drawn by the chunk renderer;
- every other group except `standin_*` becomes the GeckoLib geometry (geo/block/separation_chair.geo.json), drawn
  by the chair's block entity renderer;
- the clips become animations/block/separation_chair.animation.json through tools/bb_export_anim.py, with the sound
  keys patched to the registered chair sound events (EFFECT_PATCHES below);
- textures/block/separation_chair.png (shared by both models) is the painted sheet; the placeholder only stands in
  while there is none.
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..')
RES = os.path.join(ROOT, 'src', 'main', 'resources')
ART = os.path.join(ROOT, 'art', 'chair')
MODEL = os.path.join(ART, 'separation_chair.bbmodel')
NAME = 'separation_chair'

# --- lines -----------------------------------------------------------------------------------------------------------

REFUSALS = {
    'chair.blocked': ('The chair needs room to work. Clear %s.',
                      'A cadeira precisa de espaço. Libere %s.'),
    'chair.in_use': ('Operation in progress. Kindly wait behind the line.',
                     'Operação em andamento. Aguarde atrás da faixa.'),
    'chair.blade_busy': ('Not mid-operation. Change the blade afterwards.',
                         'Não durante a operação. Troque a lâmina depois.'),
}
SPACE = {
    'chair.space.left': ('the block to its left', 'o bloco à esquerda'),
    'chair.space.front_left': ('the block in front and to the left', 'o bloco à frente e à esquerda'),
    'chair.space.front': ('the block in front', 'o bloco da frente'),
    'chair.space.above': ('the space above it', 'o espaço acima dela'),
}
DOLORES = {
    'privacy': ('Privacy, please.', 'Privacidade, por favor.'),
}

# --- sounds ----------------------------------------------------------------------------------------------------------
# From the sound hand-off (tools/snd/events.json of the sound batch): files, attenuation and subtitles. Volume and
# pitch ranges per play are in the code (block/ChairSounds.java). The files are in sounds/chair/.
# name: (files, attenuation, subtitle en, subtitle pt); subtitle None = no subtitle (small life, or part of another)
CHAIR_SOUNDS = {
    'servo_barrier': (3, 16, 'Barrier motor whirs', 'Motor da cancela zune'),
    'barrier_drop': (3, 16, 'Barrier lowers', 'Cancela desce'),
    'barrier_latch': (3, 16, 'Barrier clamps shut', 'Cancela trava'),
    'barrier_settle': (3, 16, None, None),
    'servo_horn': (3, 16, 'Gas horn swings', 'Corneta de gás gira'),
    'gas_hiss': (3, 16, 'Anaesthetic hisses', 'Anestésico chia'),
    'beep': (1, 16, 'DOLORES counts down', 'DOLORES faz a contagem'),
    'servo_tick': (4, 16, None, None),
    'scan_chirp': (1, 16, 'Scanner chirps', 'Scanner apita'),
    'latch': (2, 16, 'Something latches', 'Algo trava'),
    'whine_rise': (3, 16, 'Blade winds up', 'Lâmina toma impulso'),
    'placard': (3, 16, 'Curtain: privacy, please', 'Cortina: privacidade, por favor'),
    'cut': (2, 24, 'Blade separates', 'Lâmina separa'),
    'ding': (1, 16, 'DOLORES dings politely', 'DOLORES toca a campainha'),
    'lid_pop': (2, 16, 'Jar lid pops open', 'Tampa do pote abre'),
    'saline_glug': ([f'minecraft:item/bottle/fill{i}' for i in (1, 2, 3, 4)], 16, 'Saline glugs', 'Soro gorgoleja'),
    'clamp': (3, 16, 'Pincer grips', 'Pinça segura'),
    'hydraulic': (3, 16, 'Hood hydraulics hiss', 'Hidráulica do capô chia'),
    'servo': (3, 16, 'Hood swings', 'Capô gira'),
    'plop': ([f'minecraft:block/bubble_column/bubble{i}' for i in (1, 2, 3)], 12, 'Head plops in', 'Cabeça cai no soro'),
    'antenna': (2, 16, 'Antenna extends', 'Antena se estende'),
    'ratchet': (3, 16, 'Wrench ratchets', 'Chave aperta o parafuso'),
    'spark': (3, 16, 'Antenna sparks', 'Antena solta faíscas'),
    'clink': (3, 16, 'Jar lid clinks shut', 'Tampa do pote fecha'),
    'unlatch': (3, 16, 'Barrier unlatches', 'Cancela destrava'),
    'barrier_lift': (3, 16, 'Barrier rises', 'Cancela sobe'),
}

# --- keyframe sound patches -----------------------------------------------------------------------------------------
# The clips name some sounds the sound batch renamed, merged or moved to the server. Until chair_anim.py uses the
# registered names, the exported keys are corrected here: tick -> new effect string, or None to drop the key.
# Volumes are 1.0: the files carry their loudness.
OP_PATCH = {
    2: None,                                            # the server plays chair.servo_barrier at 0
    21: 'headinajar:chair.servo_tick|0.5|1.5',          # the hood nods toward the face (the gas horn is gone)
    40: 'headinajar:chair.beep|1.0|1.0', 47: 'headinajar:chair.beep|1.0|0.8909', 54: 'headinajar:chair.beep|1.0|0.7937',
    64: None,                                           # the scan chirp (server, 60) covers the ring's start
    68: 'headinajar:chair.servo_tick|0.6|1.3', 74: 'headinajar:chair.servo_tick|0.6|1.3',
    80: 'headinajar:chair.servo_tick|0.6|1.3', 86: 'headinajar:chair.servo_tick|0.6|1.3',
    94: 'headinajar:chair.beep|1.0|1.1892',
    100: 'headinajar:chair.latch|1.0|1.0', 104: 'headinajar:chair.whine_rise|1.0|1.0',
    112: 'headinajar:chair.placard|1.0|1.0',            # the curtain whips up (the placard file)
    128: 'headinajar:chair.ding|1.0|1.0', 141: 'headinajar:chair.lid_pop|1.0|1.0', 148: 'headinajar:chair.clamp|1.0|1.0',
    152: 'headinajar:chair.hydraulic|1.0|1.0', 166: 'headinajar:chair.servo|1.0|1.0',
    184: 'headinajar:chair.hydraulic|1.0|1.0', 198: 'headinajar:chair.plop|1.0|1.0',
    204: 'headinajar:chair.hydraulic|1.0|1.1429', 212: 'headinajar:chair.servo_tick|0.5|1.0',
    215: 'headinajar:chair.clink|1.0|1.0', 218: 'headinajar:chair.servo|1.0|1.0',
    222: 'headinajar:chair.placard|1.0|1.0', 230: 'headinajar:chair.ding|1.0|1.2599',
    234: 'headinajar:chair.unlatch|1.0|1.0',
}
OP_ADD = {}
EFFECT_PATCHES = {
    'operation': (OP_PATCH, OP_ADD),
    'botch': (OP_PATCH, OP_ADD),
    'idle': ({52: 'headinajar:chair.servo_tick|0.5|1.4'}, {}),
}


def _tick(key):
    return round(float(key) * 20)


def _time(tick):
    s = repr(round(tick / 20, 4))
    return s


def patch_effects(anims):
    for name, (patch, add) in EFFECT_PATCHES.items():
        anim = anims['animations'].get(name)
        if anim is None:
            continue
        sounds = anim.get('sound_effects', {})
        out = {}
        for key, value in sounds.items():
            t = _tick(key)
            if t in patch:
                if patch[t] is None:
                    continue
                value = {'effect': patch[t]}
            out[key] = value
        taken = {_tick(k) for k in out}
        for t, effect in add.items():
            if t not in taken:
                out[_time(t)] = {'effect': effect}
        anim['sound_effects'] = dict(sorted(out.items(), key=lambda kv: float(kv[0])))


# --- geometry --------------------------------------------------------------------------------------------------------

def _load():
    with open(MODEL, encoding='utf-8') as f:
        return json.load(f)


def _tree(model):
    groups = {g['uuid']: g for g in model['groups']}
    elements = {e['uuid']: e for e in model['elements']}
    return groups, elements


def _num(v):
    v = round(v, 4)
    return int(v) if v == int(v) else v


def _neg_x(v):
    return [_num(-v[0]), _num(v[1]), _num(v[2])]


def geo(model):
    """Bedrock geometry of every group but `static` and the stand-ins, as Blockbench's Bedrock codec writes it
    (x mirrored, rotation x and y negated), which is what GeckoLib reads back."""
    groups, elements = _tree(model)
    bones = []
    root_locators = {}

    def cube(e):
        size = [e['to'][i] - e['from'][i] for i in range(3)]
        c = {'origin': [_num(-e['to'][0]), _num(e['from'][1]), _num(e['from'][2])], 'size': [_num(s) for s in size],
             'uv': e['uv_offset']}
        if e.get('inflate'):
            c['inflate'] = e['inflate']
        rot = e.get('rotation') or [0, 0, 0]
        if any(rot):
            c['pivot'] = _neg_x(e['origin'])
            c['rotation'] = [_num(-rot[0]), _num(-rot[1]), _num(rot[2])]
        if e.get('mirror_uv'):
            c['mirror'] = True
        return c

    def walk(nodes, parent):
        for node in nodes:
            if isinstance(node, str):
                e = elements[node]
                if e['type'] == 'locator' and parent is None:
                    root_locators[e['name']] = _neg_x(e['position'])
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
                bone['rotation'] = [_num(-rot[0]), _num(-rot[1]), _num(rot[2])]
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
    if root_locators:
        bones.insert(0, {'name': 'bb_main', 'pivot': [0, 0, 0], 'locators': root_locators})
    res = model['resolution']
    return {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': f'geometry.{NAME}', 'texture_width': res['width'],
                        'texture_height': res['height'], 'visible_bounds_width': 5, 'visible_bounds_height': 3,
                        'visible_bounds_offset': [0, 1.25, 0]},
        'bones': bones}]}


def block_model(model):
    """The `static` group as a Java block model: Blockbench x and z + 8, UVs scaled to 0-16 on each axis."""
    groups, elements = _tree(model)
    res = model['resolution']
    su, sv = 16 / res['width'], 16 / res['height']
    static = next(n for n in model['outliner'] if not isinstance(n, str) and groups[n['uuid']]['name'] == 'static')
    els = []
    for child in static['children']:
        if not isinstance(child, str):
            continue
        e = elements[child]
        if e['type'] != 'cube':
            continue
        faces = {}
        for face, data in e['faces'].items():
            if data.get('texture') is None:
                continue
            u1, v1, u2, v2 = data['uv']
            faces[face] = {'uv': [_num(u1 * su), _num(v1 * sv), _num(u2 * su), _num(v2 * sv)], 'texture': '#0'}
            if data.get('rotation'):
                faces[face]['rotation'] = data['rotation']
        el = {'name': e['name'], 'from': [_num(e['from'][0] + 8), _num(e['from'][1]), _num(e['from'][2] + 8)],
              'to': [_num(e['to'][0] + 8), _num(e['to'][1]), _num(e['to'][2] + 8)], 'faces': faces}
        rot = e.get('rotation') or [0, 0, 0]
        if any(rot):
            axis = 'xyz'[[i for i in range(3) if rot[i]][0]]
            el['rotation'] = {'angle': rot['xyz'.index(axis)], 'axis': axis,
                              'origin': [_num(e['origin'][0] + 8), _num(e['origin'][1]), _num(e['origin'][2] + 8)]}
        els.append(el)
    return {'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout',
            'texture_size': [res['width'], res['height']],
            'textures': {'0': f'headinajar:block/{NAME}', 'particle': f'headinajar:block/{NAME}'},
            'elements': els}


def animations(geo_path):
    with tempfile.TemporaryDirectory() as tmp:
        out = os.path.join(tmp, 'a.json')
        r = subprocess.run([sys.executable, os.path.join(HERE, 'bb_export_anim.py'), MODEL, '-o', out, '--geo', geo_path],
                           capture_output=True, text=True)
        if r.returncode != 0:
            raise SystemExit('bb_export_anim failed:\n' + r.stderr)
        with open(out, encoding='utf-8') as f:
            anims = json.load(f)
    patch_effects(anims)
    return anims


def run(EN, PT, write, shaped):
    for key, (en, pt) in REFUSALS.items():
        EN[f'refusal.headinajar.{key}'], PT[f'refusal.headinajar.{key}'] = en, pt
    for key, (en, pt) in SPACE.items():
        EN[f'gui.headinajar.{key}'], PT[f'gui.headinajar.{key}'] = en, pt
    for key, (en, pt) in DOLORES.items():
        EN[f'dolores.headinajar.{key}'], PT[f'dolores.headinajar.{key}'] = en, pt

    model = _load()
    g = geo(model)
    write(f'assets/headinajar/geo/block/{NAME}.geo.json', g)
    write(f'assets/headinajar/models/block/{NAME}.json', block_model(model))
    write(f'assets/headinajar/animations/block/{NAME}.animation.json',
          animations(os.path.join(RES, 'assets', 'headinajar', 'geo', 'block', f'{NAME}.geo.json')))
    tex = os.path.join(RES, 'assets', 'headinajar', 'textures', 'block', f'{NAME}.png')
    os.makedirs(os.path.dirname(tex), exist_ok=True)
    # the painted sheet, once there is one, is the painter's file: only a missing sheet gets the placeholder
    if not os.path.exists(tex):
        shutil.copyfile(os.path.join(ART, 'chair_placeholder.png'), tex)

    # sounds.json: gen_p9 wrote it; add the chair's events
    path = os.path.join(RES, 'assets', 'headinajar', 'sounds.json')
    with open(path, encoding='utf-8') as f:
        table = json.load(f)
    for name, (files, att, en, pt) in CHAIR_SOUNDS.items():
        if isinstance(files, int):
            files = [f'headinajar:chair/{name}_{i}' for i in range(1, files + 1)]
        entry = {'sounds': [{'name': f, 'attenuation_distance': att} for f in files]}
        if en:
            entry['subtitle'] = f'subtitles.headinajar.chair.{name}'
            EN[entry['subtitle']], PT[entry['subtitle']] = en, pt
        table[f'chair.{name}'] = entry
    write('assets/headinajar/sounds.json', table)

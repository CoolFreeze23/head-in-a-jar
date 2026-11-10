"""The Separation Chair (DOLORES): its models, animations, texture, sounds and lines. Imported by gen_data.py.

The model's source is art/chair/separation_chair.bbmodel (geometry) with its clips from art/chair/chair_anim.py. This
file turns it into what the game loads, so a new revision of the model drops in by running gen_data.py again:

- the group `static` becomes the baked block model (models/block/separation_chair.json), drawn by the chunk renderer;
- every other group except `standin_*` becomes the GeckoLib geometry (geo/block/separation_chair.geo.json), drawn
  by the chair's block entity renderer;
- the clips become animations/block/separation_chair.animation.json through tools/bb_export_anim.py (the clips' sound
  keys name the registered chair events: change them in chair_anim.py, never in the exported file);
- sounds.json gets the chair's events from tools/snd/events.json and DOLORES's lines from tools/voice/lines.json;
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
    'chair.tray_blocked': ("The jar tray can't come out. Clear %s.", 'A bandeja do pote não sai. Libere %s.'),
    'chair.no_break': ('Not mid-operation. It comes apart once it is done.', 'Não durante a operação. Ela se desmonta depois.'),
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
# The chair's sound events come from the sound hand-off, tools/snd/events.json (files, attenuation, subtitles); how each
# is played (volume, pitch range) is in block/ChairSounds.java. DOLORES's spoken lines come from tools/voice/lines.json
# (one event per line and language, and a muffled event for the lines the patient hears during the operation).
SND_EVENTS = os.path.join(HERE, 'snd', 'events.json')
VOICE_LINES = os.path.join(HERE, 'voice', 'lines.json')


def chair_events():
    with open(SND_EVENTS, encoding='utf-8') as f:
        events = json.load(f)['events']
    return [e for e in events if e['id'].startswith('headinajar:chair.')]


def voice_lines():
    with open(VOICE_LINES, encoding='utf-8') as f:
        return json.load(f)


# What DOLORES says during the operation, and when (the rulings): the greeting at the signature, the gas line, the scan
# line (only if it ends before the latch at 100: the silence before the cut is the joke), the jar line during the head's
# transfer (ending by about 205); the cut and privacy lines are text only. After the operation: the closing line, or
# "Oopsie." on a botch, at the delays the voice hand-off measured.
LATCH, JAR_END, TRANSFER_START = 100, 205, 150


def voice_schedule(voice):
    lines = {l['id']: l for l in voice['lines']}
    out = {}
    for lang in ('en', 'pt'):
        ticks = lambda i: lines[i][lang]['heard_ticks']
        sched, notes = {}, []
        end = ticks('start')
        gas = max(lines['gas']['trigger_tick'], end)
        sched[gas] = 'gas'
        end = gas + ticks('gas')
        scan = max(lines['scan']['trigger_tick'], end)
        if scan + ticks('scan') <= LATCH:
            sched[scan] = 'scan'
            end = scan + ticks('scan')
        else:
            notes.append('scan stays text: it would end at %d, after the latch at %d' % (scan + ticks('scan'), LATCH))
        jar = max(TRANSFER_START, end, JAR_END - ticks('jar'))
        sched[jar] = 'jar'
        out[lang] = {'operation': {str(t): l for t, l in sorted(sched.items())},
                     'done_delay': lines['done'][lang]['start_tick'] - 240,
                     'oopsie_delay': lines['oopsie'][lang]['start_tick'] - 240,
                     'notes': notes}
    return out


def voice_table(voice):
    """The table the game reads (assets/headinajar/dolores_voice.json): every line's length per language, which are
    muffled for the patient, which ducking the chair's own sounds get, and the schedule above."""
    return {
        'lines': {l['id']: {'muffled': l['muffled'], 'en': l['en']['heard_ticks'], 'pt': l['pt']['heard_ticks']}
                  for l in voice['lines']},
        'schedule': voice_schedule(voice),
        'duck_db': -6,
        'duck': ['chair.servo_barrier', 'chair.barrier_drop', 'chair.hood_nod', 'chair.gas_hiss', 'chair.servo_tick',
                 'chair.scan_chirp', 'chair.hydraulic', 'chair.hydraulic_short', 'chair.servo', 'chair.servo_home',
                 'chair.saline_glug', 'chair.spark', 'chair.drawer_out', 'chair.drawer_in', 'chair.drawer_stop',
                 'chair.handshake', 'chair.whine_rise', 'chair.tap'],
    }


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

    # sounds.json: gen_p9 wrote it; add the chair's events and DOLORES's lines
    path = os.path.join(RES, 'assets', 'headinajar', 'sounds.json')
    with open(path, encoding='utf-8') as f:
        table = json.load(f)
    for e in chair_events():
        name = e['id'].split(':', 1)[1]
        files = e.get('files') or e.get('vanilla_files')
        entry = {'sounds': [{'name': f, 'attenuation_distance': e['attenuation']} for f in files]}
        if e.get('subtitle'):
            entry['subtitle'] = f'subtitles.headinajar.{name}'
            EN[entry['subtitle']], PT[entry['subtitle']] = e['subtitle']['en_us'], e['subtitle']['pt_br']
        table[name] = entry
    voice = voice_lines()
    EN['subtitles.headinajar.dolores'], PT['subtitles.headinajar.dolores'] = 'DOLORES speaks', 'DOLORES fala'
    for line in voice['lines']:
        for lang in ('en', 'pt'):
            v = line[lang]
            for event, sound in ((v['event'], v['sound_id']), (v.get('event_muffled'), v.get('sound_id_muffled'))):
                if not event:
                    continue
                table[event.split(':', 1)[1]] = {
                    'sounds': [{'name': sound, 'attenuation_distance': voice['range_blocks'], 'stream': voice['stream']}],
                    'subtitle': 'subtitles.headinajar.dolores'}
    write('assets/headinajar/sounds.json', table)
    write('assets/headinajar/dolores_voice.json', voice_table(voice))

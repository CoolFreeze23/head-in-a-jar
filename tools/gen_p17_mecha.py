"""The Hopper, the first mecha drawn by GeckoLib: its model, clips, module models, sounds and lines. Imported by
gen_data.py.

Sources (never hand-edit what this writes):
- art/mecha/hopper/hopper.bbmodel (geometry from hopper_geo.py, clips from hopper_anim.py) becomes
  geo/entity/mecha/hopper.geo.json and animations/entity/mecha/hopper.animation.json (through tools/bb_export_anim.py,
  which fails the generator if a clip names a bone the geometry lacks);
- art/mecha/modules/<module>.bbmodel becomes geo/entity/mecha/module/<module>.geo.json (and its clips, if it has any).
  A module with a model here is drawn by it at its socket; every other module is drawn as its simple shape;
- the textures are the painter's sheets under textures/entity/mecha/ (tools/tex), never written here;
- sounds.json gets the mecha's events from tools/snd/events.json.
"""
import json
import os
import subprocess
import sys
import tempfile

import gen_p13_chair as chair

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..')
RES = os.path.join(ROOT, 'src', 'main', 'resources')
ART = os.path.join(ROOT, 'art', 'mecha')
MODULES = ('drill', 'jump_jets', 'hydraulic_legs')

REFUSALS = {
    'mecha.pogo.no_power': ('Not enough charge to jump. Recharge at the %s.',
                            'Carga insuficiente para saltar. Recarregue no %s.'),
    'mecha.pogo.airborne': ('Already airborne. Land first, then jump.', 'Já está no ar. Pouse antes de saltar.'),
    'mecha.pogo.no_room': ('No headroom for a jump. Find open sky.', 'Sem espaço para saltar. Procure céu aberto.'),
    'mecha.pogo.venting': ('Venting heat. The springs wait for it to cool.',
                           'Expelindo calor. As molas esperam esfriar.'),
    'mecha.pogo.no_legs': ('Nothing to jump with. Fit legs at the %s.', 'Nada com que saltar. Instale pernas no %s.'),
}
LINES = {
    'hud.headinajar.pogo': ('POGO PROTOCOL %s%%', 'PROTOCOLO POGO %s%%'),
    'hud.headinajar.pogo_hint': ('Hold %s to crouch, release to jump', 'Segure %s para agachar, solte para saltar'),
    'hud.headinajar.self_test': ('SELF-TEST ... OK. %s modules answered.', 'AUTOTESTE ... OK. %s módulos responderam.'),
    'message.headinajar.severance': ('Noggin & Sons regrets the loss of your Hopper at %s, %s, %s. '
                                     'Your Severance Form is in the post.',
                                     'A Noggin & Sons lamenta a perda do seu Hopper em %s, %s, %s. '
                                     'Seu Termo de Desligamento segue pelo correio.'),
}


def _load(path):
    with open(path, encoding='utf-8') as f:
        return json.load(f)


def geo(model, identifier):
    """Bedrock geometry as Blockbench writes it (the chair generator's exporter: x mirrored, rot x/y negated), with
    the stand-ins left out."""
    g = chair.geo(model)
    desc = g['minecraft:geometry'][0]['description']
    desc['identifier'] = f'geometry.{identifier}'
    # the culling box is the entity's own (MechaEntity); this only bounds the model in tools
    desc['visible_bounds_width'], desc['visible_bounds_height'], desc['visible_bounds_offset'] = 3, 3.5, [0, 1.5, 0]
    return g


def _key_volume(value):
    """Clip sound keys name `headinajar:mecha.<id>|volume|pitch`. The files carry their own loudness, so every key is
    played at volume 1.0 (the sound hand-off's rule); the pitch is the key's. The parked blip plays at its own pitch."""
    parts = value.split('|')
    if len(parts) == 3 and parts[0].startswith('headinajar:mecha.'):
        pitch = '1.0' if parts[0].endswith('standby_blip') else parts[2]
        return '|'.join([parts[0], '1.0', pitch])
    return value


def animations(model_path, geo_path):
    with tempfile.TemporaryDirectory() as tmp:
        out = os.path.join(tmp, 'a.json')
        r = subprocess.run([sys.executable, os.path.join(HERE, 'bb_export_anim.py'), model_path, '-o', out, '--geo', geo_path],
                           capture_output=True, text=True)
        if r.returncode != 0:
            raise SystemExit('bb_export_anim failed on %s:\n%s' % (model_path, r.stderr))
        anims = _load(out)
    for clip in anims.get('animations', {}).values():
        for key in clip.get('sound_effects', {}).values():
            if isinstance(key, dict) and 'effect' in key:
                key['effect'] = _key_volume(key['effect'])
    return anims


def mecha_events():
    events = _load(os.path.join(HERE, 'snd', 'events.json'))['events']
    return [e for e in events if e['id'].startswith('headinajar:mecha.')]


def run(EN, PT, write, shaped):
    for key, (en, pt) in REFUSALS.items():
        EN[f'refusal.headinajar.{key}'], PT[f'refusal.headinajar.{key}'] = en, pt
    for key, (en, pt) in LINES.items():
        EN[key], PT[key] = en, pt

    base = os.path.join('assets', 'headinajar')
    hopper = os.path.join(ART, 'hopper', 'hopper.bbmodel')
    geo_rel = f'{base}/geo/entity/mecha/hopper.geo.json'
    write(geo_rel, geo(_load(hopper), 'hopper'))
    write(f'{base}/animations/entity/mecha/hopper.animation.json', animations(hopper, os.path.join(RES, geo_rel)))
    for name in MODULES:
        path = os.path.join(ART, 'modules', f'{name}.bbmodel')
        model = _load(path)
        rel = f'{base}/geo/entity/mecha/module/{name}.geo.json'
        write(rel, geo(model, f'mecha_module_{name}'))
        if model.get('animations'):
            write(f'{base}/animations/entity/mecha/module/{name}.animation.json', animations(path, os.path.join(RES, rel)))

    path = os.path.join(RES, base, 'sounds.json')
    table = _load(path)
    for e in mecha_events():
        name = e['id'].split(':', 1)[1]
        entry = {'sounds': [{'name': f, 'attenuation_distance': e['attenuation']} for f in e['files']]}
        if e.get('subtitle'):
            entry['subtitle'] = f'subtitles.headinajar.{name}'
            EN[entry['subtitle']], PT[entry['subtitle']] = e['subtitle']['en_us'], e['subtitle']['pt_br']
        table[name] = entry
    write(f'{base}/sounds.json', table)

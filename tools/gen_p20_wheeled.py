"""The wheeled skeleton drawn by GeckoLib: the Wheelhouse (a mecha frame, Rush Hour) and the Rover (a Utility Shell).
Imported by gen_data.py.

Sources (never hand-edit what this writes):
- art/mecha/wheelhouse/wheelhouse.bbmodel (geometry from wheelhouse_geo.py, clips from wheelhouse_anim.py) becomes
  geo/entity/mecha/wheelhouse.geo.json and animations/entity/mecha/wheelhouse.animation.json;
- art/shell/rover/rover.bbmodel (rover_geo.py, rover_anim.py) becomes geo/entity/shell/rover.geo.json and
  animations/entity/shell/rover.animation.json;
  both through the Hopper's exporter (gen_p17_mecha), which fails the generator if a clip names a bone the geometry
  lacks;
- the sheets and glow masks are painted (textures/entity/mecha/wheelhouse*.png, textures/entity/shell/rover*.png);
- the Wheelhouse's sounds are the mecha.wheelhouse.* events of tools/snd/events.json, which gen_p17_mecha already
  writes into sounds.json. A clip key naming a sound it has its own take of is played at volume 1.0 and pitch 1.0
  (the files carry their loudness and pitch); the Rover borrows from the others' sets (RoverSounds).
"""
import json
import os

import gen_p17_mecha as mecha

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')

REFUSALS = {
    'mecha.rush.off_road': ('OFF-ROAD: no right of way on dirt. Find a road.',
                            'FORA DE ESTRADA: sem preferência na terra. Ache uma estrada.'),
    'mecha.rush.no_power': ('NO POWER for Rush Hour. Recharge at the %s.', 'SEM ENERGIA para a Hora do Rush. Recarregue no %s.'),
    'mecha.rush.venting': ('VENTING: right of way suspended. Wait for it to cool.',
                           'EXPELINDO CALOR: preferência suspensa. Espere esfriar.'),
    'mecha.rush.cooldown': ('COOLING DOWN: right of way again in %s s.', 'ESFRIANDO: preferência de novo em %s s.'),
    'mecha.rush.busy': ('Right of way already asserted. Mind the road.', 'Preferência já afirmada. Olho na estrada.'),
}
LINES = {
    'hud.headinajar.rush_hint': ('%s asserts right of way', '%s afirma a preferência'),
    'hud.headinajar.rush_on': ('RUSH HOUR: RIGHT OF WAY', 'HORA DO RUSH: PREFERÊNCIA'),
    'hud.headinajar.rush_go': ('RIGHT OF WAY ASSERTED', 'PREFERÊNCIA AFIRMADA'),
}


def own_sounds(frame):
    prefix = f'headinajar:mecha.{frame}.'
    return {e['id'][len(prefix):] for e in mecha.mecha_events() if e['id'].startswith(prefix)}


def export(write, model_path, kind, name, bounds):
    base = os.path.join('assets', 'headinajar')
    with open(model_path, encoding='utf-8') as f:
        model = json.load(f)
    g = mecha.geo(model, name if kind == 'mecha' else f'{kind}_{name}')
    desc = g['minecraft:geometry'][0]['description']
    desc['visible_bounds_width'], desc['visible_bounds_height'], desc['visible_bounds_offset'] = bounds
    geo_rel = f'{base}/geo/entity/{kind}/{name}.geo.json'
    write(geo_rel, g)
    anims = mecha.animations(model_path, os.path.join(mecha.RES, geo_rel))
    own = own_sounds(name)
    for clip in anims.get('animations', {}).values():
        for key in clip.get('sound_effects', {}).values():
            if isinstance(key, dict) and 'effect' in key:
                parts = key['effect'].split('|')
                sound = parts[0].split('mecha.', 1)[-1]
                if len(parts) == 3 and sound in own:
                    key['effect'] = '|'.join([parts[0], '1.0', '1.0'])
    write(f'{base}/animations/entity/{kind}/{name}.animation.json', anims)


def run(EN, PT, write, shaped):
    for key, (en, pt) in REFUSALS.items():
        EN[f'refusal.headinajar.{key}'], PT[f'refusal.headinajar.{key}'] = en, pt
    for key, (en, pt) in LINES.items():
        EN[key], PT[key] = en, pt
    export(write, os.path.join(mecha.ART, 'wheelhouse', 'wheelhouse.bbmodel'), 'mecha', 'wheelhouse', (3, 3.5, [0, 1.5, 0]))
    export(write, os.path.join(ROOT, 'art', 'shell', 'rover', 'rover.bbmodel'), 'shell', 'rover', (2, 2.5, [0, 1, 0]))

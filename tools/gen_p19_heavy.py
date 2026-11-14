"""The Colossus and the Executive, the two big heavy bipeds drawn by GeckoLib: their models, clips and lines (Siege, the
premium jingle). Imported by gen_data.py.

Sources (never hand-edit what this writes):
- art/mecha/<frame>/<frame>.bbmodel (geometry from <frame>_geo.py, clips from <frame>_anim.py over the shared heavy-biped
  script art/mecha/bulwark/bulwark_anim.py) becomes geo/entity/mecha/<frame>.geo.json and
  animations/entity/mecha/<frame>.animation.json, through the Hopper's exporter (gen_p17_mecha);
- the sheets and glow masks are painted (textures/entity/mecha/<frame>*.png, tools/tex), never written here;
- their own sounds are the mecha.colossus.* and mecha.executive.* events of tools/snd/events.json, which gen_p17_mecha
  writes into sounds.json.

Only the clips the game plays are exported: the heel-and-toe walk (walk_c) and not the two older walks, and on the
Colossus, whose drive key is Siege, none of the lifting clips. A clip key naming a sound the frame (or the Bulwark, whose
set it borrows) has its own take of plays at volume and pitch 1.0: the takes carry their own size. The Siege hum is not
a clip key: the game plays it as one loop while the machine is sieged.
"""
import json
import os

import gen_p17_mecha as mecha

FRAMES = ('colossus', 'executive')
LEGACY = {'walk', 'walk_b', 'carry_walk', 'carry_walk_b'}
DROP = {
    'colossus': LEGACY | {'grab', 'hold', 'throw', 'grab_refused', 'carry_walk_c'},
    'executive': LEGACY,
}
LOOPED = {'siege_hum'}
# played by the server when the drive key is pressed, not by the clip (so everyone near hears it, a late arrival too)
SERVER = {('executive', 'drive_jingle'): {'premium_jingle'}}

REFUSALS = {
    'mecha.siege.no_clamps': ('Siege needs Anchor Clamps. Fit them at the %s.', 'O Cerco exige Grampos de Ancoragem. Instale no %s.'),
    'mecha.siege.moving': ('Siege starts from a standstill. Stop first.', 'O Cerco começa parado. Pare primeiro.'),
    'mecha.siege.airborne': ('No footing for Siege. Land first.', 'Sem apoio para o Cerco. Pouse primeiro.'),
    'mecha.siege.fluid': ('Planning refuses a site under water. Find dry ground.',
                          'O Planejamento recusa obra na água. Ache chão seco.'),
    'mecha.siege.ground': ('The ground won\'t take the spades. Find solid ground.',
                           'O chão não segura as pás. Ache chão firme.'),
    'mecha.siege.no_power': ('Not enough charge for Siege. Recharge at the %s.', 'Carga insuficiente para o Cerco. Recarregue no %s.'),
    'mecha.siege.venting': ('Venting heat. Siege waits for it to cool.', 'Expelindo calor. O Cerco espera esfriar.'),
    'mecha.siege.room': ('No room behind for the outriggers. Find open ground behind.',
                         'Sem espaço atrás para os apoios. Procure terreno aberto atrás.'),
    'mecha.siege.arms_planted': ('The arms are planted for Siege. Stand it up to use them.',
                                 'Os braços estão firmados no Cerco. Levante-o para usá-los.'),
    'mecha.siege.busy': ('Planning is processing. Wait for it to finish.', 'O Planejamento está analisando. Espere terminar.'),
    'mecha.jingle.busy': ('The jingle is still playing. Let it finish.', 'O jingle ainda está tocando. Deixe terminar.'),
}
LINES = {
    'hud.headinajar.siege_hint': ('%s kneels for Siege', '%s ajoelha para o Cerco'),
    'hud.headinajar.siege_held': ('Sieged. %s stands it up', 'Em Cerco. %s levanta'),
    'hud.headinajar.siege_flat': ('Siege lifted: no charge left to hold it.', 'Cerco desfeito: sem carga para mantê-lo.'),
    'hud.headinajar.shoulders': ('Shoulders  L: %s   R: %s', 'Ombros  E: %s   D: %s'),
    'hud.headinajar.jingle_hint': ('%s plays the premium jingle', '%s toca o jingle premium'),
}


def own_sounds(frame):
    prefix = f'headinajar:mecha.{frame}.'
    return {e['id'][len(prefix):] for e in mecha.mecha_events() if e['id'].startswith(prefix)}


def run(EN, PT, write, shaped):
    for key, (en, pt) in REFUSALS.items():
        EN[f'refusal.headinajar.{key}'], PT[f'refusal.headinajar.{key}'] = en, pt
    for key, (en, pt) in LINES.items():
        EN[key], PT[key] = en, pt

    base = os.path.join('assets', 'headinajar')
    for frame in FRAMES:
        model_path = os.path.join(mecha.ART, frame, f'{frame}.bbmodel')
        with open(model_path, encoding='utf-8') as f:
            model = json.load(f)
        g = mecha.geo(model, frame)
        desc = g['minecraft:geometry'][0]['description']
        desc['visible_bounds_width'], desc['visible_bounds_height'], desc['visible_bounds_offset'] = 5, 6, [0, 2.5, 0]
        geo_rel = f'{base}/geo/entity/mecha/{frame}.geo.json'
        write(geo_rel, g)
        anims = mecha.animations(model_path, os.path.join(mecha.RES, geo_rel))
        clips = anims.get('animations', {})
        for name in DROP[frame]:
            clips.pop(name, None)
        own = own_sounds(frame) | own_sounds('bulwark')
        for clip_name, clip in clips.items():
            sounds = clip.get('sound_effects', {})
            for t in list(sounds):
                key = sounds[t]
                if not (isinstance(key, dict) and 'effect' in key):
                    continue
                parts = key['effect'].split('|')
                name = parts[0].split('mecha.', 1)[-1]
                if name in LOOPED or name in SERVER.get((frame, clip_name), ()):
                    del sounds[t]
                elif len(parts) == 3 and name in own:
                    key['effect'] = '|'.join([parts[0], '1.0', '1.0'])
            if 'sound_effects' in clip and not sounds:
                del clip['sound_effects']
        write(f'{base}/animations/entity/mecha/{frame}.animation.json', anims)

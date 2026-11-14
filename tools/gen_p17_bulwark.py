"""The Bulwark, the heavy biped drawn by GeckoLib: its model, clips and lines (Heavy Lifting). Imported by gen_data.py.

Sources (never hand-edit what this writes):
- art/mecha/bulwark/bulwark.bbmodel (geometry from bulwark_geo.py, clips from bulwark_anim.py) becomes
  geo/entity/mecha/bulwark.geo.json and animations/entity/mecha/bulwark.animation.json, through the Hopper's exporter
  (gen_p17_mecha), which fails the generator if a clip names a bone the geometry lacks;
- the sheet and glow mask are painted (textures/entity/mecha/bulwark*.png, tools/tex), never written here;
- its sounds are the mecha.bulwark.* events of tools/snd/events.json, which gen_p17_mecha already writes into
  sounds.json. A clip key that names a sound the Bulwark has its own take of is played at pitch 1.0: the clips were keyed
  low to stand in with the Hopper's files.
"""
import json
import os

import gen_p17_mecha as mecha

FRAME = 'bulwark'
# Clips the art script makes for the heavy frames that the Bulwark does not ship: its walk is walk_c and its carry the
# overhead one (grab_high, hold_high, carry_walk_high, throw_high). The script keeps making them for the other frames.
NOT_SHIPPED = ('walk', 'walk_b', 'carry_walk', 'carry_walk_b', 'carry_walk_c', 'grab', 'hold', 'throw')

REFUSALS = {
    'mecha.lift.nothing': ('Nothing within reach to lift. Walk right up to it.',
                           'Nada ao alcance para erguer. Chegue bem perto.'),
    'mecha.lift.too_heavy': ('%s is over the lifting limit. Try something cow-sized.',
                             '%s passa do limite de carga. Tente algo do tamanho de uma vaca.'),
    'mecha.lift.boss': ('%s is not freight. Find another load.', '%s não é carga. Procure outra carga.'),
    'mecha.lift.untrusted': ('%s has not authorised handling. Ask them to trust you.',
                             '%s não autorizou o manuseio. Peça que confie em você.'),
    'mecha.lift.exempt': ('%s is exempt from handling. Find another load.', '%s está isento de manuseio. Procure outra carga.'),
    'mecha.lift.occupied': ('%s is riding or ridden. Unload it first.', '%s está montado ou com carona. Desembarque antes.'),
    'mecha.lift.no_power': ('Not enough charge to lift. Recharge at the %s.', 'Carga insuficiente para erguer. Recarregue no %s.'),
    'mecha.lift.venting': ('Venting heat. The fists wait for it to cool.', 'Expelindo calor. Os punhos esperam esfriar.'),
    'mecha.lift.busy': ('The fists are busy. Wait for them to finish.', 'Os punhos estão ocupados. Espere terminarem.'),
    'mecha.lift.no_headroom': ('No room overhead to carry %s. Find open sky.', 'Sem espaço acima para carregar %s. Procure céu aberto.'),
    'mecha.lift.slipped': ('It got away. Try again when it holds still.', 'Escapou. Tente de novo quando parar quieto.'),
    'mecha.arm.hands_full': ('Hands full. Throw the load or set it down first.', 'Mãos ocupadas. Arremesse ou baixe a carga antes.'),
}
LINES = {
    'hud.headinajar.lift_hint': ('%s lifts what stands in front', '%s ergue o que está à frente'),
    'hud.headinajar.lift_hold': ('%s throws. Look down first to set it down', '%s arremessa. Olhe para baixo antes para baixar'),
    'hud.headinajar.lift_flat': ('Load released: no charge left to hold it.', 'Carga solta: sem energia para segurar.'),
    'hud.headinajar.lift_ceiling': ('Load released: the ceiling is too low to carry it.', 'Carga solta: o teto é baixo demais para carregá-la.'),
    'hud.headinajar.lift_vented': ('Load released: the fists vent with the rest.', 'Carga solta: os punhos expelem calor com o resto.'),
    'message.headinajar.severance_frame': ('Noggin & Sons regrets the loss of your %s at %s, %s, %s. '
                                           'Your Severance Form is in the post.',
                                           'A Noggin & Sons lamenta a perda do seu %s em %s, %s, %s. '
                                           'Seu Termo de Desligamento segue pelo correio.'),
}


def own_sounds():
    """Names the Bulwark has its own takes of (mecha.bulwark.<name>)."""
    prefix = f'headinajar:mecha.{FRAME}.'
    return {e['id'][len(prefix):] for e in mecha.mecha_events() if e['id'].startswith(prefix)}


def run(EN, PT, write, shaped):
    for key, (en, pt) in REFUSALS.items():
        EN[f'refusal.headinajar.{key}'], PT[f'refusal.headinajar.{key}'] = en, pt
    for key, (en, pt) in LINES.items():
        EN[key], PT[key] = en, pt

    base = os.path.join('assets', 'headinajar')
    model_path = os.path.join(mecha.ART, FRAME, f'{FRAME}.bbmodel')
    with open(model_path, encoding='utf-8') as f:
        model = json.load(f)
    g = mecha.geo(model, FRAME)
    desc = g['minecraft:geometry'][0]['description']
    desc['visible_bounds_width'], desc['visible_bounds_height'], desc['visible_bounds_offset'] = 4, 4.5, [0, 2, 0]
    geo_rel = f'{base}/geo/entity/mecha/{FRAME}.geo.json'
    write(geo_rel, g)
    anims = mecha.animations(model_path, os.path.join(mecha.RES, geo_rel))
    for name in NOT_SHIPPED:
        anims['animations'].pop(name, None)
    own = own_sounds()
    for clip in anims.get('animations', {}).values():
        for key in clip.get('sound_effects', {}).values():
            if isinstance(key, dict) and 'effect' in key:
                parts = key['effect'].split('|')
                name = parts[0].split('mecha.', 1)[-1]
                if len(parts) == 3 and name in own:
                    key['effect'] = '|'.join([parts[0], '1.0', '1.0'])
    write(f'{base}/animations/entity/mecha/{FRAME}.animation.json', anims)

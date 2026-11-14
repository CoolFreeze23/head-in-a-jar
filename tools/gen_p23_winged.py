"""The Kite, the winged frame drawn by GeckoLib, and the Golden Parachute every frame's eject opens: their models, clips
and lines (the Talon Lift, the parachute's tangle). Imported by gen_data.py.

Sources (never hand-edit what this writes):
- art/mecha/kite/kite.bbmodel (geometry from kite_geo.py, clips from kite_anim.py) becomes
  geo/entity/mecha/kite.geo.json and animations/entity/mecha/kite.animation.json, through the Hopper's exporter
  (gen_p17_mecha), which fails the generator if a clip names a bone the geometry lacks;
- art/entity/golden_parachute/golden_parachute.bbmodel becomes geo/entity/golden_parachute.geo.json and
  animations/entity/golden_parachute.animation.json the same way (the stand-in jar is left out: the game draws the
  real one);
- the sheets and glow mask are painted (textures/entity/mecha/kite*.png, textures/entity/golden_parachute.png), never
  written here;
- the sounds are the mecha.kite.* and parachute events of tools/snd/events.json, which gen_p17_mecha already writes
  into sounds.json. A clip key that names a sound the Kite (or the parachute) has its own take of is played at volume
  1.0 and pitch 1.0: the files carry their loudness and the event its pitch range; the keys' numbers stood in for them.
"""
import json
import os

import gen_p17_mecha as mecha

FRAME = 'kite'
CHUTE = 'golden_parachute'
CHUTE_ART = os.path.join(mecha.ROOT, 'art', 'entity', CHUTE, f'{CHUTE}.bbmodel')

REFUSALS = {
    'mecha.talon.nothing': ('Nothing below to pick up. Fly low over it.', 'Nada embaixo para apanhar. Voe baixo sobre ele.'),
    'mecha.talon.grounded': ('Talons work from the air. Take off first.', 'As garras trabalham no ar. Decole antes.'),
    'mecha.talon.too_heavy': ('%s is over the airmail limit. Try something sheep-sized.',
                              '%s passa do limite aéreo. Tente algo do porte de uma ovelha.'),
    'mecha.talon.boss': ('%s is not airmail. Find another parcel.', '%s não é correio aéreo. Procure outra encomenda.'),
    'mecha.talon.exempt': ('%s is exempt from handling. Find another parcel.', '%s está isento de manuseio. Procure outra encomenda.'),
    'mecha.talon.occupied': ('%s is riding or ridden. Unload it first.', '%s está montado ou com carona. Desembarque antes.'),
    'mecha.talon.no_power': ('Not enough charge to grab. Recharge at the %s.', 'Carga insuficiente para agarrar. Recarregue no %s.'),
    'mecha.talon.venting': ('Venting heat. The talons wait for it to cool.', 'Expelindo calor. As garras esperam esfriar.'),
    'mecha.talon.busy': ('The talons are busy. Wait for them to finish.', 'As garras estão ocupadas. Espere terminarem.'),
    'mecha.talon.slipped': ('It got away. Try again when it holds still.', 'Escapou. Tente de novo quando parar quieto.'),
}
LINES = {
    'hud.headinajar.talon_hint': ('%s grabs what is below (in flight)', '%s agarra o que está embaixo (em voo)'),
    'hud.headinajar.talon_hold': ('%s lets go', '%s solta'),
    'hud.headinajar.talon_flat': ('Parcel released: no charge left to carry it.', 'Encomenda solta: sem energia para carregá-la.'),
    'hud.headinajar.talon_vented': ('Parcel released: the talons vent with the rest.', 'Encomenda solta: as garras expelem calor com o resto.'),
    'hud.headinajar.talon_snagged': ('Parcel released: it caught on something.', 'Encomenda solta: ela enganchou em algo.'),
    'message.headinajar.parachute_tangled': ('Golden Parachute tangled: no room overhead to open. Brace for landing.',
                                             'Paraquedas Dourado embolado: sem espaço acima para abrir. Prepare-se para o pouso.'),
    'message.headinajar.parachute_short': ('Golden Parachute tangled: too little air to open it. Brace for landing.',
                                           'Paraquedas Dourado embolado: pouco ar para abrir. Prepare-se para o pouso.'),
}


def _own_names(prefix):
    return {e['id'][len(prefix):] for e in mecha.mecha_events() if e['id'].startswith(prefix)}


def _flat_keys(anims, own):
    """Clip sound keys for names with their own takes play at volume 1.0 and pitch 1.0."""
    for clip in anims.get('animations', {}).values():
        for key in clip.get('sound_effects', {}).values():
            if isinstance(key, dict) and 'effect' in key:
                parts = key['effect'].split('|')
                name = parts[0].split('mecha.', 1)[-1]
                if len(parts) == 3 and name in own:
                    key['effect'] = '|'.join([parts[0], '1.0', '1.0'])


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
    desc['visible_bounds_width'], desc['visible_bounds_height'], desc['visible_bounds_offset'] = 5, 4, [0, 1.9, 0]
    geo_rel = f'{base}/geo/entity/mecha/{FRAME}.geo.json'
    write(geo_rel, g)
    anims = mecha.animations(model_path, os.path.join(mecha.RES, geo_rel))
    _flat_keys(anims, _own_names(f'headinajar:mecha.{FRAME}.'))
    write(f'{base}/animations/entity/mecha/{FRAME}.animation.json', anims)

    with open(CHUTE_ART, encoding='utf-8') as f:
        chute = json.load(f)
    g = mecha.geo(chute, CHUTE)
    desc = g['minecraft:geometry'][0]['description']
    desc['visible_bounds_width'], desc['visible_bounds_height'], desc['visible_bounds_offset'] = 3, 4.5, [0, 1.8, 0]
    geo_rel = f'{base}/geo/entity/{CHUTE}.geo.json'
    write(geo_rel, g)
    anims = mecha.animations(CHUTE_ART, os.path.join(mecha.RES, geo_rel))
    chute_names = {'envelope_pop', 'pilot_whoosh', 'brocade_stream', 'brocade_breathe', 'brocade_spill', 'brocade_flare',
                   'brocade_flump', 'brocade_rustle', 'brocade_slide', 'brocade_slap', 'brocade_flap', 'tassel_jingle',
                   'cords_creak', 'cords_slack', 'gold_sink'}
    _flat_keys(anims, chute_names & _own_names('headinajar:mecha.'))
    write(f'{base}/animations/entity/{CHUTE}.animation.json', anims)

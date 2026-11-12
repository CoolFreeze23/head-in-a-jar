"""The machine line, fifth batch, on the shared machine kit: the Leyden Jar, the Neural Socket, the Leech Jar, the
Jarred Head block and the Safety Placard. Imported by gen_data.py after gen_p18_machines (whose helpers it uses).

- Leyden Jar (two cells, no moving parts): the baked body, the lamp, the charge cells per `level` (full-bright), the
  knob's spark at level 4 (on the top cell, which carries the level as its generic `saline`).
- Neural Socket (one cell): the baked body and lamp; the clamps and jack as GeckoLib bones with their clips.
- Leech Jar (two cells): the baked body, lamp (on its left side) and vials per `samples`; the carrier, leech, nozzle
  and crank as bones with their clips.
- Jarred Head block (one cell): the baked plinth, turned so its front faces the way the head looks; the jar is drawn
  by the block entity renderer.
- Safety Placard (one cell, static): one model per notice, the board's front on its own notice texture. The notices
  are drawn here from the language lines by art/machines/safety_placard/placard_text.py, one set per language
  (textures/placard/<lang>/notice_<n>.png); the client stitches the set for the language in use.
"""
import json
import os
import sys

import gen_p18_machines as K

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources')
sys.path.insert(0, os.path.join(HERE, '..', 'art', 'machines', 'safety_placard'))

KIT = ['leyden_jar', 'neural_socket', 'leech_jar']
FAMILIES = ('leech.', 'socket.', 'leyden.')
# the jarred head looks the way the player who placed it looked: its front is the model's -Z turned by facing
HEAD_TURN = {'north': 180, 'east': 270, 'south': 0, 'west': 90}
PLACARD_TURN = {'north': 0, 'east': 90, 'south': 180, 'west': 270}

LINES = {}


def _lit(el):
    el = dict(el, **{'neoforge_data': {'block_light': 15, 'sky_light': 15}})
    el['shade'] = False
    return el


def _own_parts(model, name, write):
    sheet = K._sheet_uv(model)
    if name == 'leyden_jar':
        for n in range(1, 5):
            els = [_lit(K._element(model, e, sheet)) for e in K._cubes(model, K._find(model, f'state_level_{n}'))]
            K._part(name, f'level_{n}', els, K._sheet(name), {'level': str(n)}, write, ao=False)
        knob = K._cubes(model, K._find(model, 'state_knob'))[0]
        K._part(name, 'knob', [K._element(model, knob, sheet)], K._sheet(name), {'level': '0|1|2|3'}, write)
        K._part(name, 'knob_spark', [K._element(model, knob, K._own_uv(knob, '#spark'))],
                {'spark': K.TEX + 'leyden_jar_knob_spark', 'particle': K.TEX + name}, {'level': '4'}, write)
    if name == 'leech_jar':
        for n in range(1, 4):
            els = [K._element(model, e, sheet) for e in K._cubes(model, K._find(model, f'state_samples_{n}'))]
            K._part(name, f'samples_{n}', els, K._sheet(name), {'samples': str(n)}, write)


def _one_block_item(name):
    return {'parent': f'headinajar:block/{name}_whole'}


def kit_machines(write):
    K.ONE_BLOCK.add('neural_socket')
    K.TOP_ALIAS['level'] = 'saline'
    for name in KIT:
        K.BLOCK[name] = name
        model = K._load(name)
        if name == 'leech_jar':
            # the lens faces west (the window has the front): its west face takes the kit sprite's front
            lens = K._cubes(model, K._find(model, 'state_lamp'))[0]
            ou, ov = lens.get('uv_offset', [0, 0])
            lens['faces']['west']['uv'] = [ou + 1, ov + 1, ou + 3, ov + 3]
        whole = K.body(model, name)
        lower, upper = K.split(whole['elements'], name)
        write(f'assets/headinajar/models/block/{name}.json', dict(whole, elements=lower))
        if name not in K.ONE_BLOCK:
            write(f'assets/headinajar/models/block/{name}_top.json', dict(whole, elements=upper))
            K.TOP_PARTS.append(({'machine': K.BLOCK[name]}, f'headinajar:block/{name}_top'))
        lens = K._cubes(model, K._find(model, 'state_lamp'))[0]
        extra = [K._element(model, lens, K._own_uv(lens, '#lens'))]
        textures = dict(whole['textures'], lens=K.TEX + 'kit/lamp_ready')
        sheet = K._sheet_uv(model)
        if name == 'leyden_jar':
            # the item: two cells lit, the knob plain
            extra += [_lit(K._element(model, e, sheet)) for e in K._cubes(model, K._find(model, 'state_level_2'))]
            extra += [K._element(model, e, sheet) for e in K._cubes(model, K._find(model, 'state_knob'))]
        if name == 'leech_jar':
            extra += [K._element(model, e, sheet) for e in K._cubes(model, K._find(model, 'state_samples_3'))]
        write(f'assets/headinajar/models/block/{name}_whole.json', dict(whole, elements=whole['elements'] + extra, textures=textures))
        K.state_models(model, name, write)
        _own_parts(model, name, write)
        parts = list(K.LOWER_PARTS)
        write(f'assets/headinajar/blockstates/{name}.json', K.blockstate(name, parts))
        write(f'assets/headinajar/models/item/{name}.json', _one_block_item(name) if name in K.ONE_BLOCK else K.item_model(name))
        if name != 'leyden_jar':
            write(f'assets/headinajar/geo/block/{name}.geo.json', K.geo(model, name))
            write(f'assets/headinajar/animations/block/{name}.animation.json',
                  K.animations(name, os.path.join(RES, 'assets', 'headinajar', 'geo', 'block', f'{name}.geo.json')))


def jarred_head(write):
    model = K._load('jarred_head')
    whole = K.body(model, 'jarred_head')
    write('assets/headinajar/models/block/jarred_head.json', whole)
    write('assets/headinajar/blockstates/jarred_head.json', {'variants': {
        f'facing={d}': dict({'model': 'headinajar:block/jarred_head'}, **({'y': y} if y else {})) for d, y in HEAD_TURN.items()}})


def safety_placard(write):
    model = K._load('safety_placard')
    sheet = K._sheet_uv(model)
    els = []
    for e in K._cubes(model, K._find(model, 'static')):
        def uv(face, data, e=e):
            if e['name'] == 'board' and face == 'north':
                return '#notice', [0, 0, 16, 16]
            return sheet(face, data)
        els.append(K._element(model, e, uv))
    variants = {}
    for n in range(12):
        write(f'assets/headinajar/models/block/safety_placard_{n}.json',
              K._model('safety_placard', els, dict(K._sheet('safety_placard'), notice=f'headinajar:block/placard/notice_{n}')))
        for d, y in PLACARD_TURN.items():
            variants[f'facing={d},notice={n}'] = dict({'model': f'headinajar:block/safety_placard_{n}'}, **({'y': y} if y else {}))
    write('assets/headinajar/blockstates/safety_placard.json', {'variants': variants})
    write('assets/headinajar/models/item/safety_placard.json', {'parent': 'headinajar:block/safety_placard_0'})


def notices(EN, PT, write):
    """The 48 x 48 notice textures, one set per language, from the language lines; every one must fit its field."""
    import placard_text
    bad = []
    for lang, table in (('en_us', EN), ('pt_br', PT)):
        folder = os.path.join(RES, 'assets', 'headinajar', 'textures', 'placard', lang)
        os.makedirs(folder, exist_ok=True)
        for n, text in placard_text.notices(lang, table):
            ok, _ = placard_text.fits(text, n, lang)
            if not ok:
                bad.append((lang, n, text))
            placard_text.draw(text, scale=1, n=n, lang=lang).save(os.path.join(folder, f'notice_{n}.png'), optimize=True)
    assert not bad, 'notices that do not fit the placard: %r' % bad
    # the block atlas takes the set for the language in use: the client's sprite source `headinajar:placard_notices`,
    # named in assets/minecraft/atlases/blocks.json (kept by hand)


def run(EN, PT, write, shaped):
    for key, (en, pt) in LINES.items():
        EN[key], PT[key] = en, pt
    kit_machines(write)
    jarred_head(write)
    safety_placard(write)
    write('assets/headinajar/blockstates/machine_top.json', K.top_blockstate())
    K.SOUND_FAMILIES = K.SOUND_FAMILIES + FAMILIES
    K.sounds(EN, PT, write)
    notices(EN, PT, write)

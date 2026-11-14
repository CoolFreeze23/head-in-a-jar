"""The Assembly Gantry on the machine kit: its baked pedestal and lamp, the GeckoLib arm and turntable, the clips, the
build plan the server and the renderer share, its sounds and lines. Imported by gen_data.py (after the machine line).

The source is art/machines/gantry/gantry.bbmodel (geometry from gantry_geo.py, clips and gantry_plan.json from
gantry_anim.py); never edit an export, regenerate. The block is one cell: the arm and the turntable above it are bones.
"""
import json
import os

import gen_p18_machines as kit

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.join(HERE, '..', 'art', 'machines', 'gantry')
NAME, BLOCK = 'gantry', 'assembly_gantry'

LINES = {
    'refusal.headinajar.gantry.busy': ('The Gantry is at work. Wait for its arm to stand off.',
                                       'O pórtico está trabalhando. Espere o braço se afastar.'),
    'refusal.headinajar.gantry.pad_blocked': ('Something blocks the space over the pad. Clear it for the frame.',
                                              'Algo ocupa o espaço sobre a plataforma. Libere para a estrutura.'),
    'refusal.headinajar.gantry.nothing_to_repair': ('Nothing to repair: the hull and the battery are full.',
                                                    'Nada a reparar: casco e bateria estão cheios.'),
    'refusal.headinajar.gantry.no_plating': ('No Mecha Plating to mend the hull. Put some in its slot.',
                                             'Sem Placa de Mecha para o casco. Ponha algumas no espaço.'),
}


def item_model():
    """One block tall, as any block in a slot; the whole pedestal with the lamp green."""
    return {'parent': f'headinajar:block/{NAME}_whole', 'display': {
        'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.625, 0.625, 0.625]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.25, 0.25, 0.25]},
        'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
        'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]}}}


def run(EN, PT, write, shaped):
    for key, (en, pt) in LINES.items():
        EN[key], PT[key] = en, pt
    kit.ONE_BLOCK.add(NAME)
    kit.BLOCK[NAME] = BLOCK
    model = kit._load(NAME)
    whole = kit.body(model, NAME)
    write(f'assets/headinajar/models/block/{NAME}.json', whole)
    lens = kit._cubes(model, kit._find(model, 'state_lamp'))[0]
    write(f'assets/headinajar/models/block/{NAME}_whole.json',
          dict(whole, elements=whole['elements'] + [kit._element(model, lens, kit._own_uv(lens, '#lens'))],
               textures=dict(whole['textures'], lens=kit.TEX + 'kit/lamp_ready')))
    parts = kit.state_models(model, NAME, write)
    write(f'assets/headinajar/blockstates/{BLOCK}.json', kit.blockstate(NAME, parts))
    write(f'assets/headinajar/models/item/{BLOCK}.json', item_model())
    geo_rel = f'assets/headinajar/geo/block/{NAME}.geo.json'
    write(geo_rel, kit.geo(model, NAME))
    write(f'assets/headinajar/animations/block/{NAME}.animation.json',
          kit.animations(NAME, os.path.join(kit.RES, *geo_rel.split('/'))))
    # the build plan: the turntable's angle per frame, the contacts and the arc points, read by the server and the client
    with open(os.path.join(ART, 'gantry_plan.json'), encoding='utf-8') as f:
        write(f'assets/headinajar/{NAME}_plan.json', json.load(f))
    families = kit.SOUND_FAMILIES
    kit.SOUND_FAMILIES = ('gantry.',)
    try:
        kit.sounds(EN, PT, write)
    finally:
        kit.SOUND_FAMILIES = families

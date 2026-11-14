"""The multi-leg skeleton drawn by GeckoLib: the Skitter (a mecha frame, the Dragline) and the Spider Walker (a Utility
Shell). Imported by gen_data.py.

Sources (never hand-edit what this writes):
- art/mecha/skitter/skitter.bbmodel (geometry from skitter_geo.py, clips from skitter_anim.py, whose leg solver writes
  every leg key) becomes geo/entity/mecha/skitter.geo.json and animations/entity/mecha/skitter.animation.json;
- art/shell/spider_walker/spider_walker.bbmodel (spider_walker_geo.py, spider_walker_anim.py) becomes
  geo/entity/shell/spider_walker.geo.json and animations/entity/shell/spider_walker.animation.json;
  both through the Hopper's exporter (gen_p17_mecha), which fails the generator if a clip names a bone the geometry
  lacks;
- the sheets and glow masks are painted (textures/entity/mecha/skitter*.png, textures/entity/shell/spider_walker*.png).
Neither machine has a sound set of its own: their clip sounds borrow the other machines' (MechaRig.SKITTER's borrowed
table, RoverClient.BORROWED for the shells).
"""
import os

import gen_p20_wheeled as wheeled
import gen_p17_mecha as mecha

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')

REFUSALS = {
    'mecha.dragline.nothing': ('Nothing to fix the line to within 24 blocks. Aim at a wall.',
                               'Nada onde prender a linha a 24 blocos. Mire numa parede.'),
    'mecha.dragline.no_power': ('Not enough charge for the Dragline. Recharge at the %s.',
                                'Carga insuficiente para a Linha de Içar. Recarregue no %s.'),
    'mecha.dragline.venting': ('Venting heat. The line gun waits for it to cool.',
                               'Expelindo calor. O lançador espera esfriar.'),
    'mecha.cling.no_power': ('No power to hold the wall. Recharge at the %s.', 'Sem energia para se segurar na parede. Recarregue no %s.'),
    'mecha.cling.venting': ('Venting heat: the legs let go of the wall.', 'Expelindo calor: as pernas soltam a parede.'),
    'mecha.dragline.busy': ('The line gun is cycling. Wait a moment.', 'O lançador está recarregando. Espere um instante.'),
}
LINES = {
    'hud.headinajar.dragline_hint': ('%s fires the Dragline', '%s dispara a Linha de Içar'),
    'hud.headinajar.dragline_out': ('Line out %s m: hold %s to reel in, tap to cut', 'Linha a %s m: segure %s para recolher, toque para cortar'),
    'hud.headinajar.dragline_hang': ('%s pays the line out', '%s solta mais linha'),
    'hud.headinajar.dragline_lost': ('Line lost: the anchor gave way.', 'Linha perdida: a ancoragem cedeu.'),
    'hud.headinajar.dragline_snapped': ('Line parted: too far from the anchor.', 'Linha rompida: longe demais da ancoragem.'),
}


def run(EN, PT, write, shaped):
    for key, (en, pt) in REFUSALS.items():
        EN[f'refusal.headinajar.{key}'], PT[f'refusal.headinajar.{key}'] = en, pt
    for key, (en, pt) in LINES.items():
        EN[key], PT[key] = en, pt
    # their clip sounds are borrowed takes made for other moments: each key keeps the volume and pitch it was authored
    # at (the volume-1.0 rule is for a frame's own files, which carry their loudness)
    rule = mecha._key_volume
    mecha._key_volume = lambda value: value
    try:
        wheeled.export(write, os.path.join(mecha.ART, 'skitter', 'skitter.bbmodel'), 'mecha', 'skitter', (4, 3, [0, 1.25, 0]))
        wheeled.export(write, os.path.join(ROOT, 'art', 'shell', 'spider_walker', 'spider_walker.bbmodel'), 'shell', 'spider_walker',
                       (3, 2.5, [0, 1, 0]))
    finally:
        mecha._key_volume = rule

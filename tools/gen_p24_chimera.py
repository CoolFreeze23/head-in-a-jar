"""The Chimera (a mecha frame on the light-biped skeleton, the Graft) and the Lunk (a Utility Shell on the heavy-biped
skeleton) drawn by GeckoLib: their models, clips and lines. Imported by gen_data.py.

Sources (never hand-edit what this writes):
- art/mecha/chimera/chimera.bbmodel (geometry from chimera_geo.py, clips from chimera_anim.py through the shared
  light-biped script art/mecha/hopper/hopper_anim.py) becomes geo/entity/mecha/chimera.geo.json and
  animations/entity/mecha/chimera.animation.json;
- art/shell/lunk/lunk.bbmodel (lunk_geo.py, lunk_anim.py through the heavy-biped script art/mecha/bulwark/bulwark_anim.py)
  becomes geo/entity/shell/lunk.geo.json and animations/entity/shell/lunk.animation.json;
  both through the Hopper's exporter (gen_p17_mecha), which fails the generator if a clip names a bone the geometry
  lacks;
- the sheets, glow masks and the Chimera's vein overlay are painted (textures/entity/mecha/chimera*.png,
  textures/entity/shell/lunk*.png), never written here;
- their sounds are the mecha.chimera.* and mecha.lunk.* events of tools/snd/events.json, which gen_p17_mecha already
  writes into sounds.json. A clip key naming a sound the machine has its own take of plays at volume 1.0 and pitch 1.0,
  except the parked Lunk's drawer breath (kept quiet) and the build's four gulps (their falling pitch kept).

The Chimera's idle tic is authored on the left forearm; its mirror, idle_tic_r, is made here (every bone's left and
right swapped, rotation y and z and position x negated), so either forearm can twitch.
"""
import copy
import os

import gen_p17_mecha as mecha
import gen_p20_wheeled as wheeled

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')

REFUSALS = {
    'mecha.graft.nothing': ('Nothing tasted lately. Hit something, then graft.',
                            'Nada provado ultimamente. Acerte algo e enxerte.'),
    'mecha.graft.legendary': ('%s is above our pay grade. Graft something ordinary.',
                              '%s está acima do nosso salário. Enxerte algo comum.'),
    'mecha.graft.no_genes': ('%s tastes of nothing useful. Try another creature.',
                             '%s não tem gosto de nada útil. Tente outra criatura.'),
    'mecha.graft.no_gel': ('No %s aboard. Carry some in the hold or on you.',
                           'Sem %s a bordo. Leve no compartimento ou com você.'),
    'mecha.graft.running': ('A graft is still taking (%s s). Let it wear off.',
                            'Um enxerto ainda está pegando (%s s). Espere passar.'),
    'mecha.graft.busy': ('Still swallowing. Wait for it to finish.', 'Ainda engolindo. Espere terminar.'),
    'mecha.graft.venting': ('Venting heat. The growth waits for it to cool.', 'Expelindo calor. O crescimento espera esfriar.'),
    'mecha.graft.no_power': ('Not enough charge to graft. Recharge at the %s.', 'Carga insuficiente para enxertar. Recarregue no %s.'),
    'mecha.graft.ran_out': ('The Bio-Gel ran out mid-swallow. Restock and try again.',
                            'O Bio-Gel acabou no meio do gole. Reabasteça e tente de novo.'),
}
LINES = {
    'hud.headinajar.graft_hint': ('%s grafts what you last hit', '%s enxerta o que você acertou por último'),
    'hud.headinajar.graft_on': ('Grafted: %s (%s s)', 'Enxertado: %s (%s s)'),
    'hud.headinajar.graft_taken': ('Graft taken: %s, for 60 s', 'Enxerto aceito: %s, por 60 s'),
    'hud.headinajar.graft_off': ('The %s graft has worn off.', 'O enxerto de %s passou.'),
}

SIDES = (('_l', '_r'), ('_r', '_l'))


def _mirror_name(name):
    for a, b in SIDES:
        if name.endswith(a):
            return name[:-len(a)] + b
    return name


def _mirror_vec(value, signs):
    """A key's value (a vector, or a dict with pre/post vectors) with its components multiplied by signs."""
    if isinstance(value, list) and len(value) == 3:
        return [v * s if isinstance(v, (int, float)) else ('-(%s)' % v if s < 0 else v) for v, s in zip(value, signs)]
    if isinstance(value, dict):
        out = dict(value)
        for k in ('pre', 'post', 'vector'):
            if k in out:
                out[k] = _mirror_vec(out[k], signs)
        return out
    return value


def _mirror_channel(channel, signs):
    if isinstance(channel, dict):
        return {t: _mirror_vec(v, signs) for t, v in channel.items()}
    return _mirror_vec(channel, signs)


def mirrored(clip):
    """The clip for the other side: bones swapped left for right, mirrored across the machine's middle."""
    out = copy.deepcopy(clip)
    bones = {}
    for name, channels in clip.get('bones', {}).items():
        m = {}
        for ch, value in channels.items():
            signs = (1, -1, -1) if ch == 'rotation' else (-1, 1, 1) if ch == 'position' else (1, 1, 1)
            m[ch] = _mirror_channel(copy.deepcopy(value), signs)
        bones[_mirror_name(name)] = m
    out['bones'] = bones
    for key in ('timeline',):
        if key in out:
            out[key] = {t: (v.replace(':l', ':r') if isinstance(v, str) else v) for t, v in out[key].items()}
    return out


def _own_keys(anims, frame, keep):
    """Clip sound keys for names this machine has its own take of: volume and pitch 1.0, unless keep says otherwise."""
    own = wheeled.own_sounds(frame)
    for clip_name, clip in anims.get('animations', {}).items():
        for key in clip.get('sound_effects', {}).values():
            if isinstance(key, dict) and 'effect' in key:
                parts = key['effect'].split('|')
                sound = parts[0].split('mecha.', 1)[-1]
                if len(parts) == 3 and sound in own:
                    volume, pitch = keep.get((clip_name, sound), ('1.0', '1.0'))
                    key['effect'] = '|'.join([parts[0], volume if volume else parts[1], pitch if pitch else parts[2]])


def export(write, model_path, kind, name, bounds, keep, extra=None):
    base = os.path.join('assets', 'headinajar')
    with open(model_path, encoding='utf-8') as f:
        model = __import__('json').load(f)
    # the clips' own volumes before the exporter sets them to 1.0 (some are kept)
    raw = {}
    for a in model.get('animations', []):
        for an in (a.get('animators') or {}).values():
            for kf in an.get('keyframes', []):
                if kf.get('channel') == 'sound':
                    for dp in kf.get('data_points', []):
                        p = str(dp.get('effect', '')).split('|')
                        if len(p) == 3:
                            raw[(a['name'], p[0].split('mecha.', 1)[-1])] = p[1]
    g = mecha.geo(model, name if kind == 'mecha' else f'{kind}_{name}')
    desc = g['minecraft:geometry'][0]['description']
    desc['visible_bounds_width'], desc['visible_bounds_height'], desc['visible_bounds_offset'] = bounds
    geo_rel = f'{base}/geo/entity/{kind}/{name}.geo.json'
    write(geo_rel, g)
    anims = mecha.animations(model_path, os.path.join(mecha.RES, geo_rel))
    keep = {k: (raw.get(k, '1.0') if v[0] == 'raw' else v[0], v[1]) for k, v in keep.items()}
    _own_keys(anims, name, keep)
    if extra:
        extra(anims['animations'])
    write(f'{base}/animations/entity/{kind}/{name}.animation.json', anims)


def _chimera_extra(clips):
    clips['idle_tic_r'] = mirrored(clips['idle_tic'])


def run(EN, PT, write, shaped):
    for key, (en, pt) in REFUSALS.items():
        EN[f'refusal.headinajar.{key}'], PT[f'refusal.headinajar.{key}'] = en, pt
    for key, (en, pt) in LINES.items():
        EN[key], PT[key] = en, pt
    # the build's four gulps keep their falling pitch (volume 1.0); the parked Lunk's drawer breath keeps its quiet volume
    export(write, os.path.join(mecha.ART, 'chimera', 'chimera.bbmodel'), 'mecha', 'chimera', (3, 3.5, [0, 1.5, 0]),
           {('build', 'squelch_gulp'): ('1.0', None)}, _chimera_extra)
    export(write, os.path.join(ROOT, 'art', 'shell', 'lunk', 'lunk.bbmodel'), 'shell', 'lunk', (3, 3.5, [0, 1.5, 0]),
           {('parked', 'drawer_slide'): ('raw', '1.0')})

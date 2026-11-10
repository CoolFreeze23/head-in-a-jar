"""Mecha module models: one small GeckoLib .bbmodel per module, hung at a frame's socket bones.

    python art/mecha/modules/modules_geo.py        # writes <module>.bbmodel and <module>_placeholder.png here

The convention every frame and every module keeps:
- A module is modelled at 16 px a block with its mount point at the origin and its working end toward -Z (the front,
  as Blockbench shows it). +Y is the module's "up". Nothing of it lies on the +Z side of z = 0 except what sinks into
  the part it is fixed to.
- Its top-level bones name the socket they hang on: `mount` (the slot's single socket), or `mount_<part>` for a
  module drawn on both sides of a frame (a legs module: `mount_thigh` goes on socket_thigh_l and socket_thigh_r).
- A frame's socket bone sits on the surface with its own -Z pointing out of that surface (its rest rotation does the
  turning), so the module is drawn at the socket's pivot with the socket's full transform and nothing else. No module
  is ever scaled; adding one touches no frame file.
- A module's own motion (a bit spinning, a nozzle gimballing) is a clip in its own file on its own bones.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import bbbuild  # noqa: E402

TEX = (64, 64)
MATERIALS = {
    'enamel': 'ddd5c3', 'brass': 'd99a35', 'steel': '7b818b',  # also the jets' heat-stained bells
    'dsteel': '5c616b', 'bright': 'ced3d8',
    'rubber': '27262d', 'copper': 'c26b4c', 'lens': '141318', 'lamp_a': 'ffc24e', 'yellow': 'f4bd1e',
}


def C(name, f, t, mat, parent, **kw):
    return (name, list(f), list(t), mat, parent, kw)


MODULES = {
    # ---- Drill (ARM slot): collar, motor housing with a brass band, a four-stage auger bit that spins ----
    'drill': {
        'bones': [('mount', [0, 0, 0], None, None), ('bit', [0, 0, -8], 'mount', None)],
        'cubes': [
            C('neck', [-2, -2, -1], [2, 2, 0], 'dsteel', 'mount'),          # a step where it meets the cuff
            C('collar', [-3, -3, -3], [3, 3, -1], 'rubber', 'mount'),
            C('motor', [-3.5, -3.5, -8], [3.5, 3.5, -3], 'enamel', 'mount'),
            C('band', [-4, -4, -6], [4, 4, -4], 'brass', 'mount'),
            C('vent', [-1.5, 3.5, -7], [1.5, 4.5, -3], 'dsteel', 'mount'),
            C('chuck', [-2, -2, -10], [2, 2, -8], 'dsteel', 'bit'),
            C('auger_1', [-3, -3, -13], [3, 3, -10], 'bright', 'bit'),
            C('auger_2', [-2, -2, -16], [2, 2, -13], 'bright', 'bit'),
            C('auger_tip', [-1, -1, -18], [1, 1, -16], 'bright', 'bit'),
        ],
        'locators': [('fx_tip', [0, 0, -18], 'bit')],
        'clips': {
            # held: 8 ticks a turn, linear, whole turns (M7, table 2.3)
            'spin': {'length': 8, 'loop': 'loop', 'bones': {'bit': {'rotation': [(0, [0, 0, 0]), (4, [0, 0, 180]),
                                                                                  (8, [0, 0, 360])]}}},
        },
    },
    # ---- Jump Jets (BACK slot): a manifold with three pilot lamps (one per burst) and two gimballed jet pods ----
    'jump_jets': {
        'bones': [('mount', [0, 0, 0], None, None), ('nozzle_0', [-3, -2, -4], 'mount', None),
                  ('nozzle_1', [3, -2, -4], 'mount', None), ('pilot_0', [-2, 3, -1.5], 'mount', None),
                  ('pilot_1', [0, 3, -1.5], 'mount', None), ('pilot_2', [2, 3, -1.5], 'mount', None)],
        'cubes': [
            C('flange', [-3, -1, -1], [3, 2, 0], 'dsteel', 'mount'),          # a step where it meets the socket
            C('manifold', [-4, -2, -3], [4, 3, -1], 'dsteel', 'mount'),
            C('lamp_rail', [-3.5, 3, -3], [3.5, 4, -1], 'brass', 'mount'),
            C('pod_top_0', [-5, 4, -6], [-1, 7, -2], 'enamel', 'nozzle_0'),
            C('pod_band_0', [-4, 1, -5], [-2, 4, -3], 'dsteel', 'nozzle_0'),     # a 3 px recessed band
            C('pod_low_0', [-5, -2, -6], [-1, 1, -2], 'enamel', 'nozzle_0'),
            C('ring_0', [-5.5, -3, -6.5], [-0.5, -2, -1.5], 'brass', 'nozzle_0'),
            C('bell_0', [-5, -6, -6], [-1, -3, -2], 'steel', 'nozzle_0'),       # heat-stained steel
            C('pod_top_1', [1, 4, -6], [5, 7, -2], 'enamel', 'nozzle_1'),
            C('pod_band_1', [2, 1, -5], [4, 4, -3], 'dsteel', 'nozzle_1'),
            C('pod_low_1', [1, -2, -6], [5, 1, -2], 'enamel', 'nozzle_1'),
            C('ring_1', [0.5, -3, -6.5], [5.5, -2, -1.5], 'brass', 'nozzle_1'),
            C('bell_1', [1, -6, -6], [5, -3, -2], 'steel', 'nozzle_1'),
            C('pilot_lit_0', [-2.5, 4, -2], [-1.5, 5, -1], 'lamp_a', 'pilot_0'),
            C('pilot_lit_1', [-0.5, 4, -2], [0.5, 5, -1], 'lamp_a', 'pilot_1'),
            C('pilot_lit_2', [1.5, 4, -2], [2.5, 5, -1], 'lamp_a', 'pilot_2'),
        ],
        'locators': [('fx_jet_0', [-3, -6, -4], 'nozzle_0'), ('fx_jet_1', [3, -6, -4], 'nozzle_1')],
        'clips': {
            # one burst (6 ticks): the pods kick back 15 degrees and recover; the integrator snuffs a pilot lamp
            'burst': {'length': 6, 'loop': 'once', 'bones': {
                'nozzle_0': {'rotation': [(0, [0, 0, 0]), (1, [-15, 0, 0], 'easeOutExpo'), (6, [0, 0, 0], 'easeInOutSine')]},
                'nozzle_1': {'rotation': [(0, [0, 0, 0]), (2, [-15, 0, 0], 'easeOutExpo'), (6, [0, 0, 0], 'easeInOutSine')]}},
                'effects': [(0, 'timeline', 'jets:burst')]},
        },
    },
    # ---- Hydraulic Legs (LEGS slot): a brass ram clamped to each thigh and a damper on each shin. The frame's legs
    # stay the frame's; this module adds to them. ----
    'hydraulic_legs': {
        'bones': [('mount_thigh', [0, 0, 0], None, None), ('mount_shin', [0, 0, 0], None, None)],
        'cubes': [
            C('ram', [-1.5, -4, -3], [1.5, 4, -1], 'brass', 'mount_thigh'),    # held a pixel off the thigh by its clamps
            C('ram_clamp_top', [-2, 3, -2], [2, 5, 0], 'dsteel', 'mount_thigh'),
            C('ram_clamp_low', [-2, -5, -2], [2, -3, 0], 'dsteel', 'mount_thigh'),
            C('hose', [1.5, -3, -2], [2.5, 3, -1], 'rubber', 'mount_thigh'),
            C('damper', [-1, -5, -2], [1, 3, 0], 'bright', 'mount_shin'),
            C('damper_can', [-1.5, 1, -3], [1.5, 5, -1], 'copper', 'mount_shin'),
            C('damper_boot', [-1.5, -5, -3], [1.5, -3, -1], 'rubber', 'mount_shin'),
        ],
        'locators': [],
        'clips': {},
    },
}


def pack(cs):
    offs = {}
    x = y = shelf = 0
    for c in sorted(cs, key=lambda c: (c[3], -(abs(c[2][1] - c[1][1]) + abs(c[2][2] - c[1][2])))):
        w, h, d = [max(1, round(abs(c[2][i] - c[1][i]))) for i in range(3)]
        uw, uh = 2 * (w + d), d + h
        if x + uw > TEX[0]:
            y += shelf
            x = shelf = 0
        offs[c[0]] = [x, y]
        x += uw
        shelf = max(shelf, uh)
    if y + shelf > TEX[1]:
        sys.exit('texture overflow')
    return offs


def draw(cs, offs, path):
    from PIL import Image
    img = Image.new('RGBA', TEX, (0, 0, 0, 0))
    px = img.load()
    for c in cs:
        w, h, d = [max(1, round(abs(c[2][i] - c[1][i]))) for i in range(3)]
        ox, oy = offs[c[0]]
        col = tuple(int(MATERIALS[c[3]][i:i + 2], 16) for i in (0, 2, 4)) + (255,)
        for rx, ry, rw, rh in [(ox + d, oy, w * 2, d), (ox, oy + d, 2 * (w + d), h)]:
            for yy in range(ry, ry + rh):
                for xx in range(rx, rx + rw):
                    px[xx, yy] = col
    img.save(path)


def clip_json(model, clips):
    import uuid
    groups = {g['name']: g['uuid'] for g in model['groups']}
    out = []
    for name, c in clips.items():
        animators = {}
        for bone, chans in c['bones'].items():
            kfs = []
            for ch, keys in chans.items():
                for key in keys:
                    t, v = key[0], key[1]
                    kf = {'channel': ch, 'data_points': [{'x': v[0], 'y': v[1], 'z': v[2]}], 'uuid': str(uuid.uuid4()),
                          'time': round(t / 20.0, 4), 'color': -1, 'interpolation': 'linear'}
                    if len(key) > 2:
                        kf['easing'] = key[2]
                    kfs.append(kf)
            animators[groups[bone]] = {'name': bone, 'type': 'bone', 'keyframes': kfs}
        effs = [{'channel': kind, 'data_points': [{'script': s}], 'uuid': str(uuid.uuid4()), 'time': round(t / 20.0, 4),
                 'color': -1, 'interpolation': 'linear'} for (t, kind, s) in c.get('effects', [])]
        if effs:
            animators['effects'] = {'name': 'Effects', 'type': 'effect', 'keyframes': effs}
        out.append({'uuid': str(uuid.uuid4()), 'name': name, 'loop': c['loop'], 'override': False,
                    'length': c['length'] / 20.0, 'snapping': 20, 'selected': False, 'animators': animators})
    return out


def main():
    for name, m in MODULES.items():
        offs = pack(m['cubes'])
        png = os.path.join(HERE, '%s_placeholder.png' % name)
        draw(m['cubes'], offs, png)
        path = os.path.join(HERE, '%s.bbmodel' % name)
        bbbuild.build(path, name, m['bones'], m['cubes'], m['locators'], offs, png, TEX, keep_animations=False)
        with open(path, encoding='utf-8') as f:
            model = json.load(f)
        model['animations'] = clip_json(model, m['clips'])
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(model, f, indent=1)
        print('wrote', path)


if __name__ == '__main__':
    main()

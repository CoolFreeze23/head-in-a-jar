"""Every mecha module drawn from its own model: geometry, clips and the table the module layer reads. Imported by
gen_data.py.

Sources (never hand-edit what this writes):
- art/mecha/modules/<module>/<module>.bbmodel (the light size) and <module>_heavy.bbmodel (the heavy size, where the
  module has one); the first three (drill, jump_jets, hydraulic_legs) sit at art/mecha/modules/<module>.bbmodel and have
  one size. Each becomes geo/entity/mecha/module/<file>.geo.json and, when it has clips,
  animations/entity/mecha/module/<file>.animation.json (through tools/bb_export_anim.py, which fails the generator if a
  clip names a bone the geometry lacks);
- art/mecha/modules/size_classes.json: frame -> module -> light or heavy, the one table of which file a frame takes;
- the painted sheets and masks under textures/entity/mecha/module/ (tools/tex), never written here. They are read to
  find which cubes carry lit pixels, so the module layer draws only those again:
  `<file>_glowmask.png` always lit, `<file>_firing.png` lit while the module works, `<file>_charge.png` the battery's
  level lamps (lit one by one with the charge).

What it writes besides the models: assets/headinajar/mecha/module_art.json, read by the game (common code, so the
server and the tests read it too):
  {"modules": {id: {"slot", "two_sizes" (has a heavy file), "clips": {name: [ticks, loop]},
                    "light"/"heavy": {"glow"|"firing"|"charge": [[bone, cube index], ...], "tint": [[bone, cube, rgb]]}}},
   "heavy": {frame: [ids that frame takes in the heavy size]}}
"""
import json
import os

from PIL import Image

import gen_p17_mecha as mecha

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..')
RES = os.path.join(ROOT, 'src', 'main', 'resources')
ART = os.path.join(ROOT, 'art', 'mecha', 'modules')
BASE = os.path.join('assets', 'headinajar')
SHEETS = os.path.join(RES, BASE, 'textures', 'entity', 'mecha', 'module')
JAVA_MODULES = os.path.join(ROOT, 'src', 'main', 'java', 'com', 'coolfreeze23', 'headinajar', 'mecha', 'MechaModule.java')

# fluids painted as a pale base for a tint: the cube's name and the colour it is tinted
TINTS = {'blood': '7d323a', 'serum_fill': 'c060d0'}
MASKS = ('glowmask', 'firing', 'charge')


def _load(path):
    with open(path, encoding='utf-8') as f:
        return json.load(f)


def module_slots():
    """id -> slot, read from MechaModule.java (the one list of modules)."""
    out, slot = {}, None
    import re
    for line in open(JAVA_MODULES, encoding='utf-8'):
        m = re.match(r'\s+([A-Z_]+)\(Slot\.([A-Z]+),', line)
        if m:
            out[m.group(1).lower()] = m.group(2)
    return out


def source(module, heavy=False):
    name = module + ('_heavy' if heavy else '')
    nested = os.path.join(ART, module, name + '.bbmodel')
    if os.path.exists(nested):
        return nested
    flat = os.path.join(ART, name + '.bbmodel')
    return flat if os.path.exists(flat) else None


def named_cubes(model):
    """(bone, cube index, cube name) in the order the exporter writes cubes (static and stand-in groups left out)."""
    groups = {g['uuid']: g for g in model['groups']}
    elements = {e['uuid']: e for e in model['elements']}
    out = []

    def walk(nodes):
        for node in nodes:
            if isinstance(node, str):
                continue
            g = groups[node['uuid']]
            if g['name'] == 'static' or g['name'].startswith('standin'):
                continue
            i = 0
            for child in node['children']:
                if isinstance(child, str) and elements[child]['type'] == 'cube':
                    out.append((g['name'], i, elements[child]))
                    i += 1
            walk(node['children'])

    walk(model['outliner'])
    return out


def lit_cubes(model, mask_path):
    """The cubes whose box-UV area holds a lit pixel of the mask."""
    if not os.path.exists(mask_path):
        return None
    img = Image.open(mask_path).convert('RGBA')
    sx = img.width / model['resolution']['width']
    sy = img.height / model['resolution']['height']
    alpha = img.getchannel('A').load()
    out = []
    for bone, index, e in named_cubes(model):
        w, h, d = [abs(e['to'][i] - e['from'][i]) for i in range(3)]
        u, v = e['uv_offset']
        x0, y0 = int(u * sx), int(v * sy)
        x1, y1 = int((u + 2 * (w + d)) * sx + 0.999), int((v + d + h) * sy + 0.999)
        if any(alpha[x, y] for x in range(max(0, x0), min(img.width, x1)) for y in range(max(0, y0), min(img.height, y1))):
            out.append([bone, index, e['name']])
    return out


def run(EN, PT, write, shaped):
    slots = module_slots()
    classes = _load(os.path.join(ART, 'size_classes.json'))
    table = {'modules': {}, 'heavy': {}}
    for frame, modules in sorted(classes.items()):
        for mod, size in modules.items():
            if mod not in slots:
                raise SystemExit(f'size_classes.json names {mod}, which is not a module')
            if size not in ('light', 'heavy'):
                raise SystemExit(f'size_classes.json: {frame}/{mod} is {size}')
        table['heavy'][frame] = sorted(m for m, s in modules.items() if s == 'heavy')
    for mod in sorted(slots):
        light = source(mod)
        if light is None:
            raise SystemExit(f'no model for the module {mod}')
        heavy = source(mod, True)
        entry = {'slot': slots[mod], 'two_sizes': heavy is not None, 'clips': {}}
        for path, size in ((light, 'light'), (heavy, 'heavy')):
            if path is None:
                continue
            file = mod + ('_heavy' if size == 'heavy' else '')
            model = _load(path)
            rel = f'{BASE}/geo/entity/mecha/module/{file}.geo.json'
            write(rel, mecha.geo(model, f'mecha_module_{file}'))
            if model.get('animations'):
                write(f'{BASE}/animations/entity/mecha/module/{file}.animation.json',
                      mecha.animations(path, os.path.join(RES, rel)))
            clips = {a['name']: [round(a.get('length', 0) * 20, 2), a.get('loop', 'once')] for a in model.get('animations', [])}
            if size == 'light':
                entry['clips'] = clips
            elif clips != entry['clips']:
                raise SystemExit(f'{mod}: the heavy file has other clips than the light one')
            if not os.path.exists(os.path.join(SHEETS, file + '.png')):
                raise SystemExit(f'{mod}: no painted sheet {file}.png')
            parts = {}
            for kind in MASKS:
                lit = lit_cubes(model, os.path.join(SHEETS, f'{file}_{kind}.png'))
                if lit is None:
                    continue
                if not lit:
                    raise SystemExit(f'{file}_{kind}.png lights no cube')
                if kind == 'charge':
                    lit.sort(key=lambda c: c[2])          # level_0, level_1, level_2: lit in that order
                parts['glow' if kind == 'glowmask' else kind] = [[b, i] for b, i, _ in lit]
            tint = [[b, i, TINTS[e['name']]] for b, i, e in named_cubes(model) if e['name'] in TINTS]
            if tint:
                parts['tint'] = tint
            entry[size] = parts
        table['modules'][mod] = entry
    for frame, heavy in table['heavy'].items():
        for mod in heavy:
            if not table['modules'][mod]['two_sizes']:
                raise SystemExit(f'{frame} takes {mod} heavy, which has no heavy file')
    write(f'{BASE}/mecha/module_art.json', table)

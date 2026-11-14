"""The effect kit (particles, beams, camera, loops) and the moments built on it: Telekinesis, the swap, the Lacrimal
Laser, Tesla arcs, the guardian turret's lock. Lang lines, the particle definitions and the kit's sound events.
Imported by gen_data.py."""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources')

REFUSALS = {
    'tk.boss': ('Far too important to lift. Find something smaller.', 'Importante demais para erguer. Procure algo menor.'),
    'tk.untouchable': ("That one can't be lifted. Try a creature or a block.", 'Esse aí não pode ser erguido. Tente uma criatura ou um bloco.'),
    'tk.not_trusted': ("%s hasn't trusted you. Ask them: /headoffice trust add.", '%s não confia em você. Peça: /headoffice trust add.'),
}

# id: (frames in the sheet)
PARTICLES = {'mote': 4, 'spark': 3, 'bubble': 7, 'puff': 8, 'ring': 4, 'static': 4}

# The kit's sound events: (files, attenuation, subtitle en, subtitle pt). The files and their measurements come from
# tools/snd/recipes_fx.py (events.json). Played at volume 1.0 by the code.
FX_SOUNDS = {
    'fx.tk.grab': (3, 16, 'Telekinesis takes hold', 'Telecinese agarra'),
    'fx.tk.hold': (1, 10, 'Telekinesis hums', 'Telecinese zumbe'),
    'fx.tk.throw': (3, 16, 'Telekinesis throws', 'Telecinese arremessa'),
    'fx.tk.set_down': (3, 8, 'Telekinesis lets go', 'Telecinese solta'),
    'fx.tk.fizzle': (3, 16, 'Telekinesis fizzles', 'Telecinese pifa'),
    'fx.swap.up': (2, 16, 'Mind leaves the jar', 'Mente sai do pote'),
    'fx.swap.down': (2, 16, 'Mind returns to jar', 'Mente volta ao pote'),
    'fx.laser.fire': (3, 24, 'Lacrimal Laser fires', 'Laser Lacrimal dispara'),
    'fx.tesla.arc': (1, 16, 'Tesla Node arcs', 'Nó de Tesla faísca'),
}
# streamed loops: the two loop files are played by a tickable instance for as long as the thing lasts
LOOPS = {'fx.tk.hold', 'fx.tesla.arc'}


def run(EN, PT, write, shaped):
    for key, (en, pt) in REFUSALS.items():
        EN[f'refusal.headinajar.{key}'], PT[f'refusal.headinajar.{key}'] = en, pt
    for name, frames in PARTICLES.items():
        write(f'assets/headinajar/particles/{name}.json', {'textures': [f'headinajar:{name}_{i}' for i in range(frames)]})

    path = os.path.join(RES, 'assets', 'headinajar', 'sounds.json')
    with open(path, encoding='utf-8') as f:
        table = json.load(f)
    for event, (takes, att, en, pt) in FX_SOUNDS.items():
        stem = event[3:].replace('.', '_')  # fx.tk.grab -> tk_grab
        sounds = []
        for i in range(1, takes + 1):
            s = {'name': f'headinajar:fx/{stem}_{i}', 'attenuation_distance': att}
            if event in LOOPS:
                s['stream'] = True
            sounds.append(s)
        entry = {'sounds': sounds}
        if en:
            entry['subtitle'] = f'subtitles.headinajar.{event}'
            EN[entry['subtitle']], PT[entry['subtitle']] = en, pt
        table[event] = entry
    write('assets/headinajar/sounds.json', table)

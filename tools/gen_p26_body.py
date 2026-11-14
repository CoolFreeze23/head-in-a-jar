"""The head and the body: the saw's cork, a jar seating on a host, the handshake's notes, the body you leave behind,
the corpse lying down, tools in the hand and the jar's glass. Lang lines and the sound events. Imported by
gen_data.py."""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources')

LINES = {
    'message.headinajar.body.hr_card': ('Your body has died. HR has sent a card.',
                                        'Seu corpo faleceu. O RH mandou um cartão.'),
}

# event: (folder/name, takes, attenuation, subtitle en, subtitle pt). Files from tools/snd/recipes_body.py; played at
# volume 1.0 by the code. None for a subtitle: small life the player needn't be told about.
SOUNDS = {
    'body.cork': ('body/cork', 3, 16, 'Head pops off', 'Cabeça salta fora'),
    'body.bayonet': ('body/bayonet', 3, 12, 'Jar locks on', 'Pote se encaixa'),
    'body.unseat': ('body/unseat', 3, 12, 'Jar unlocks', 'Pote se solta'),
    'body.note': ('body/note', 1, 8, 'Handshake note', 'Nota do aperto'),
    'body.miss': ('body/miss', 2, 8, 'Handshake misses', 'Aperto falha'),
    'body.power_down': ('body/power_down', 2, 8, 'Body powers down', 'Corpo desliga'),
    'body.power_up': ('body/power_up', 2, 8, 'Body powers up', 'Corpo liga'),
    'body.snick': ('body/snick', 4, 6, None, None),
    'body.plip': ('body/plip', 3, 6, 'Syringe draws', 'Seringa aspira'),
    'jar.crack': ('jar/crack', 3, 12, 'Jar glass cracks', 'Vidro do pote racha'),
    'jar.crack_long': ('jar/crack_long', 2, 16, 'Jar glass splits', 'Vidro do pote trinca'),
    'jar.drip': ('jar/drip', 3, 6, 'Jar drips', 'Pote pinga'),
}


def run(EN, PT, write, shaped):
    for key, (en, pt) in LINES.items():
        EN[key], PT[key] = en, pt
    path = os.path.join(RES, 'assets', 'headinajar', 'sounds.json')
    with open(path, encoding='utf-8') as f:
        table = json.load(f)
    for event, (stem, takes, att, en, pt) in SOUNDS.items():
        entry = {'sounds': [{'name': f'headinajar:{stem}_{i}', 'attenuation_distance': att} for i in range(1, takes + 1)]}
        if en:
            entry['subtitle'] = f'subtitles.headinajar.{event}'
            EN[entry['subtitle']], PT[entry['subtitle']] = en, pt
        table[event] = entry
    write('assets/headinajar/sounds.json', table)

"""The arm tools at work: what their rays, streams and blows sound like, a hot frame cooling and a damaged one
sparking. Imported by gen_data.py.

- sounds.json gets the arm events from tools/snd/events.json (recipes in tools/snd/recipes_arms.py): the loops are
  streamed and played by a tickable instance for as long as the tool works;
- their subtitles in both languages.
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources')


def arm_events():
    with open(os.path.join(HERE, 'snd', 'events.json'), encoding='utf-8') as f:
        events = json.load(f)['events']
    return [e for e in events if e['id'].startswith('headinajar:arms.')]


def run(EN, PT, write, shaped):
    path = os.path.join(RES, 'assets', 'headinajar', 'sounds.json')
    with open(path, encoding='utf-8') as f:
        table = json.load(f)
    for e in arm_events():
        name = e['id'].split(':', 1)[1]
        sounds = []
        for file in e['files']:
            s = {'name': file, 'attenuation_distance': e['attenuation']}
            if e.get('loop'):
                s['stream'] = True
            sounds.append(s)
        entry = {'sounds': sounds}
        if e.get('subtitle'):
            en, pt = e['subtitle']['en_us'], e['subtitle']['pt_br']
            assert len(en) <= 24 and len(pt) <= 30, name
            entry['subtitle'] = f'subtitles.headinajar.{name}'
            EN[entry['subtitle']], PT[entry['subtitle']] = en, pt
        table[name] = entry
    write('assets/headinajar/sounds.json', table)

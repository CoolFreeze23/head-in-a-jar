"""The palette the staff art paints from: art/palette.json (its default set) plus art/staff/staff_ramps.json.

    from staff_palette import C        # C('brass.3') -> (r, g, b, 255); C('clear') -> (0, 0, 0, 0)
    python art/staff/staff_palette.py --merged OUT.json   # the merged palette file, for tools/tex/measure --palette

Nothing outside these two files: every opaque pixel the staff scripts write is a key here (a ramp step or a half step,
written 'steel.3h' for the midpoint of steps 3 and 4).
"""
import argparse
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))


def merged():
    with open(os.path.join(ROOT, 'art', 'palette.json'), encoding='utf-8') as fh:
        data = json.load(fh)
    with open(os.path.join(HERE, 'staff_ramps.json'), encoding='utf-8') as fh:
        extra = json.load(fh)
    for k, v in extra['shared'].items():
        if k in data['shared'] or k in data['house']:
            continue  # once moved into the house file, the house file wins
        data['shared'][k] = v
    return data


def _keys(data):
    s = data['default']
    out = {}
    for name, m in data['house'].items():
        for i, h in enumerate(m[s]):
            out['%s.%d' % (name, i)] = h
    for name, m in data['shared'].items():
        for i, h in enumerate(m['ramp']):
            out['%s.%d' % (name, i)] = h
    return out


KEYS = _keys(merged())


def C(key):
    if key == 'clear':
        return (0, 0, 0, 0)
    if key.endswith('h'):
        name, step = key[:-1].rsplit('.', 1)
        a = C('%s.%s' % (name, step))
        b = C('%s.%d' % (name, int(step) + 1))
        return tuple((x + y) // 2 for x, y in zip(a[:3], b[:3])) + (255,)
    h = KEYS[key]
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


# team colours for previews only (vanilla ChatFormatting colours; teal is the company colour, art/palette.json teal_glass.1)
TEAM = {'red': (0xFF, 0x55, 0x55), 'blue': (0x55, 0x55, 0xFF), 'gold': (0xFF, 0xAA, 0x00), 'teal': (0x39, 0x78, 0x7E),
        'teal2': (0x5A, 0xA6, 0xA4)}  # teal2: teal_glass.2, the lighter company teal suggested for the badge


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--merged')
    a = ap.parse_args()
    if a.merged:
        with open(a.merged, 'w', encoding='utf-8') as fh:
            json.dump(merged(), fh, indent=1)
        print(a.merged)

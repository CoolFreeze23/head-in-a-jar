"""Neural Socket: every clip, keyed by game tick, written into neural_socket.bbmodel.

Source of truth for the Socket's animations; hand edits in Blockbench are overwritten by the next run.

    python art/machines/neural_socket/socket_anim.py [--review] [--sweep]

--review also writes build/machines-review/neural_socket_review.bbmodel (eased segments baked per tick, plus the
`_review_*` hold poses) and the variants neural_socket_empty (no jar), _bars (the lit bars shown), _compact (a Compact
Jar), _45 (a WIDE jar turned 45 degrees: why its yaw must be held square).
--sweep samples both bones through every clip at quarter ticks and reports any point of a moving box inside a static
box or a stand-in jar (the clamps' 0.15 px bite into the WIDE base, closed, is the grip: reported apart).

Two bones, both keyed by clips; no code channels:
  claws  the four corner clamps, pivot (0, 2, 0). Rest (modelled): open, down (y 2..12). Keyed posY 0 open .. +6 shut
         (y 8..18, gripping the jar's base). The motion that reads at ten blocks: four brass plates (the side plates 4
         wide) travel 6 px up and down the pillar's corners, and closed they stand 2 px above the deck round the jar.
  plug   the jack in the deck's socket, pivot (0, 16, 0). Rest: flush with the deck (y 12..16). Keyed posY 0 .. +1
         (into the jar's base plate: hidden; heard).
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
try:
    import kit  # noqa: E402
except ImportError:
    sys.path.insert(0, 'build/machines-review/scratch-neural_socket')
    import kit  # noqa: E402
from kit import k, R  # noqa: E402
import socket_geo  # noqa: E402

PATH = socket_geo.PATH
REVIEW_DIR = 'build/machines-review'
SHUT = socket_geo.CLAW_TRAVEL      # +6: the clamps up round the jar's base
Y = lambda y: R(y=y)               # noqa: E731
S = 'headinajar:socket.'


def snd(t, name, vol=0.5, pitch=1.0):
    return (t, 'sound', '%s%s|%s|%s' % (S, name, vol, pitch), None)


CLIPS = []

# ---- one controller, "clamp", keyed on the synced `occupied` bit (a jar player plugged in, or a head in the slot):
# KitController.toggle(c, occupied, now, "clamp", 12, "release", 12); a late joiner sees the arriving clip's end.
# Clamps: a medium part (10 px): wind-up 2, action 6, settle 3 (STYLE 2.4); contacts easeInQuad (M2).

# clamp (empty -> occupied), 12: the jar is already standing on the deck (the game teleports it); the clamps sink
# 0.5 px (the wind-up), are driven up the corners into the base (contact on 8, the chunk: easeInQuad), bounce 0.5 and
# seat on 11; the plug then rises 1 px into the jar's base (12, heard). The renderer's six channels chase once from
# tick 12 (code, 1 tick apart).
CLIPS.append(('clamp', {'length': 12, 'loop': 'hold',
    'bones': {'claws': {'position': [k(0, Y(0)), k(2, Y(-1), 'easeOutQuad'), k(8, Y(SHUT), 'easeInQuad'),
                                     k(9, Y(SHUT - 0.5), 'easeOutQuad'), k(11, Y(SHUT), 'easeInQuad'),
                                     k(12, Y(SHUT))]},
              'plug': {'position': [k(0, Y(0)), k(9, Y(0)), k(12, Y(1), 'easeInQuad')]}},
    'effects': [snd(1, 'servo', 0.4), snd(8, 'clamp', 0.6), (9, 'particle', 'minecraft:poof', 'claw_fl'),
                snd(12, 'plug', 0.5)]}))

# release (occupied -> empty), 12: the plug drops out of the base (the pneumatic sigh), the clamps lift 0.5 px off
# their seat, then fall down the corners onto their stops (contact on 8), bounce, rest.
CLIPS.append(('release', {'length': 12, 'loop': 'hold',
    'bones': {'claws': {'position': [k(0, Y(SHUT)), k(2, Y(SHUT)), k(3, Y(SHUT + 0.5), 'easeOutQuad'),
                                     k(8, Y(0), 'easeInQuad'), k(9, Y(0.75), 'easeOutQuad'), k(11, Y(0), 'easeInQuad'),
                                     k(12, Y(0))]},
              'plug': {'position': [k(0, Y(1)), k(2, Y(0), 'easeInQuad')]}},
    'effects': [snd(0, 'sigh', 0.5), snd(8, 'drop', 0.5)]}))

# refuse (block event 1, empty), 8: the open clamps hop twice on their stops (+1.5, down, +1, down), seen from every side.
CLIPS.append(('refuse', {'length': 8, 'loop': 'hold',
    'bones': {'claws': {'position': [k(0, Y(0)), k(2, Y(2), 'easeOutQuad'), k(4, Y(0), 'easeInQuad'),
                                     k(6, Y(1.5), 'easeOutQuad'), k(8, Y(0), 'easeInQuad')]}},
    'effects': [(0, 'sound', 'headinajar:machine.rattle|0.4|1.0', None)]}))

# refuse_on (block event 1, occupied: "socket.occupied", "socket.has_head"), 8: the shut clamps chatter 1 px down
# off the base and back, twice (they let go a little and grab again).
CLIPS.append(('refuse_on', {'length': 8, 'loop': 'hold',
    'bones': {'claws': {'position': [k(0, Y(SHUT)), k(2, Y(SHUT - 1), 'easeOutQuad'), k(4, Y(SHUT), 'easeInQuad'),
                                     k(6, Y(SHUT - 0.75), 'easeOutQuad'), k(8, Y(SHUT), 'easeInQuad')]}},
    'effects': [(0, 'sound', 'headinajar:machine.rattle|0.4|1.0', None)]}))


def review_clips():
    rc = []
    rc.append(('_review_shut', {'length': 1, 'loop': 'hold', 'review': True, 'effects': [], 'bones': {
        'claws': {'position': [k(0, Y(SHUT))]}, 'plug': {'position': [k(0, Y(1))]}}}))
    return rc


def variant(src, dst, show=(), hide=()):
    with open(src, encoding='utf-8') as fh:
        d = json.load(fh)
    for g in d['groups']:
        if g['name'] in show:
            g['visibility'] = True
        if g['name'] in hide:
            g['visibility'] = False
    with open(dst, 'w', encoding='utf-8') as fh:
        json.dump(d, fh, indent=1)


# ---------------------------------------------------------------------------------------------------------------------
# Sweep: both bones are translations (posY), so a pose is the rest box moved by the key's value.
# ---------------------------------------------------------------------------------------------------------------------
def channel_at(keys, t):
    keys = sorted(keys, key=lambda x: x[0])
    if t <= keys[0][0]:
        return keys[0][1]
    for i in range(1, len(keys)):
        t0, v0 = keys[i - 1][0], keys[i - 1][1]
        t1, v1, e, a, _ = keys[i]
        if t <= t1:
            f = kit.ease(e, a, (t - t0) / float(t1 - t0))
            return [v0[j] + (v1[j] - v0[j]) * f for j in range(3)]
    return keys[-1][1]


def rot_y(v, deg):
    a = math.radians(deg)
    x, y, z = v
    return [x * math.cos(a) + z * math.sin(a), y, -x * math.sin(a) + z * math.cos(a)]


def points(f, t, step=0.25):
    n = [max(1, int(round((t[i] - f[i]) / step))) for i in range(3)]
    return [[f[0] + (t[0] - f[0]) * i / n[0], f[1] + (t[1] - f[1]) * j / n[1], f[2] + (t[2] - f[2]) * kk / n[2]]
            for i in range(n[0] + 1) for j in range(n[1] + 1) for kk in range(n[2] + 1)]


def inside(p, f, t, eps=0.02):
    return all(f[i] + eps < p[i] < t[i] - eps for i in range(3))


def sweep(m):
    static = [c for c in m.drawn() if kit.is_static(m, c[4])]
    moving = {b: [c for c in m.drawn() if c[4] == b] for b in ('claws', 'plug')}
    jars = {'wide': (socket_geo.jar_boxes(False, socket_geo.JAR_AT), 0),
            'compact': (socket_geo.jar_boxes(True, socket_geo.JAR_AT), 0),
            'wide_45': (socket_geo.jar_boxes(False, socket_geo.JAR_AT), 45)}
    hits, depth = {}, {}
    lo, hi = [99] * 3, [-99] * 3

    def test(bone, dy, label, with_jar):
        for c in moving[bone]:
            for p in points(c[1], c[2]):
                q = [p[0], p[1] + dy, p[2]]
                for i in range(3):
                    lo[i] = min(lo[i], q[i])
                    hi[i] = max(hi[i], q[i])
                for s in static:
                    if inside(q, s[1], s[2]):
                        key = '%s %s in %s' % (label, c[0], s[0])
                        hits[key] = hits.get(key, 0) + 1
                if not with_jar:
                    continue
                for g, (boxes, yaw) in jars.items():
                    r = rot_y([q[0], q[1] - 16, q[2]], -yaw)
                    r = [r[0], r[1] + 16, r[2]]
                    for n, f, t, mat in boxes:
                        if inside(r, f, t):
                            key = '%s %s in jar %s.%s' % (label, c[0], g, n)
                            hits[key] = hits.get(key, 0) + 1
                            d = min(r[0] - f[0], t[0] - r[0], r[2] - f[2], t[2] - r[2])
                            depth[key] = max(depth.get(key, 0), d)
    for name, clip in CLIPS:
        # the clamps meet a jar only in clips played with one standing there (all but `refuse`)
        with_jar = name != 'refuse'
        for bone in ('claws', 'plug'):
            keys = clip['bones'].get(bone, {}).get('position')
            if not keys:
                continue
            for qt in range(clip['length'] * 4 + 1):
                test(bone, channel_at(keys, qt / 4.0)[1], name, with_jar)
    return hits, depth, lo, hi


def main():
    kit.write_clips(PATH, CLIPS, code_channels=socket_geo.model().code)
    sys.stderr.write('wrote %d clips into %s\n' % (len(CLIPS), PATH))
    if '--review' in sys.argv:
        rv = REVIEW_DIR + '/neural_socket_review.bbmodel'
        kit.write_clips(PATH, CLIPS, code_channels=socket_geo.model().code, review=rv, review_clips=review_clips())
        base = REVIEW_DIR + '/neural_socket_%s.bbmodel'
        variant(rv, base % 'empty', hide=('standin_jar',))
        variant(rv, base % 'bars', show=('standin_bars',))
        variant(rv, base % 'compact', show=('standin_compact',), hide=('standin_jar',))
        variant(rv, base % '45', show=('standin_jar_45',), hide=('standin_jar',))
        sys.stderr.write('review copy %s and variants written\n' % rv)
    if '--sweep' in sys.argv:
        hits, depth, lo, hi = sweep(socket_geo.model())
        for h, n in sorted(hits.items()):
            print('HIT %s (%d points%s)' % (h, n, ', %.2f px deep' % depth[h] if h in depth else ''))
        print('sweep: %d hit kinds; moving parts span x %.2f..%.2f, y %.2f..%.2f, z %.2f..%.2f' % (
            len(hits), lo[0], hi[0], lo[1], hi[1], lo[2], hi[2]))
    print(kit.effects_table(CLIPS))


if __name__ == '__main__':
    main()

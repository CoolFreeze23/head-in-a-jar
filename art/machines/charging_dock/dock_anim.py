"""Charging Dock: every clip, keyed by game tick, written into charging_dock.bbmodel; the needle's code written out.

Source of truth for the Dock's animations; hand edits in Blockbench are overwritten by the next run.

    python art/machines/charging_dock/dock_anim.py [--review] [--sweep]

--review also writes build/machines-review/dock_review.bbmodel (eased segments baked per tick, plus the
`_review_*` clips that bake the needle's code motion) and the variants dock_empty (no jar), dock_45 (the jar turned 45
degrees), dock_compact (a head set down in a Compact Jar), dock_front (a jar at the plate's front edge).
--sweep samples the switch through every clip at quarter ticks and the needle through its whole range, and reports
any point of a moving box inside a static box or a stand-in jar.

Two bones:
  contacts  the knife switch's blade and grip, pivot (6, 21, 6). Rest (the outliner): thrown open 65 degrees forward.
            Keyed rotX: 0 open, +65 shut in the jaws (keyframe +a turns +y toward +z, so + closes; the outliner's rest
            rotation is X -65, the same sign convention here, checked by render). Clips only.
  needle    the dial's pointer, pivot (0, 28, 5.5). rotZ by CODE only (never keyed): 0 = E (the viewer's left), +180 =
            F (the viewer's right), through the top; +a turns +X toward +Y. See NEEDLE below.
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
    sys.path.insert(0, 'build/machines-review/scratch-charging_dock')
    import kit  # noqa: E402
from kit import k, R  # noqa: E402
import dock_geo  # noqa: E402

PATH = dock_geo.PATH
REVIEW_DIR = 'build/machines-review'
C = dock_geo.OPEN          # +65: shut (in the jaws); 0: open (the rest)
LOW = C - 45               # the brown-out droop: 45 degrees out of the jaws (v2; 37 did not read from the front)
X = lambda x: R(x=x)       # noqa: E731
S = 'headinajar:dock.'


def snd(t, name, vol=0.5, pitch=1.0):
    return (t, 'sound', '%s%s|%s|%s' % (S, name, vol, pitch), None)


def spark(t, loc='jaws'):
    return (t, 'particle', 'minecraft:electric_spark', loc)


# ---- the state clips (one controller, "switch", rotation of `contacts`). Three states: OPEN (nobody on the plate),
# SHUT (a jar or head on it and the buffer has FE), LOW (on it, buffer empty: the brown-out). Each transition has its
# clip, played from the moment the client sees the state change (v2 adds FULL: on it, jar full, switch tripped open); a late joiner seeks the arriving clip to its end.
# Medium part (11 px): wind-up 3, action 5-6, settle 3 (STYLE 2.4). Contacts arrive easeInQuad (M2).
CLIPS = []

# occupy (OPEN -> SHUT), 12: the blade dips 6 degrees further open (wind-up), holds a tick, is thrown up into the
# jaws (contact on 9: the clack and the make-spark), bounces 4 degrees out and seats on 12.
CLIPS.append(('occupy', {'length': 12, 'loop': 'hold',
    'bones': {'contacts': {'rotation': [k(0, X(0)), k(3, X(-6), 'easeOutQuad'), k(4, X(-6)), k(9, X(C), 'easeInQuad'),
                                        k(10, X(C - 4), 'easeOutQuad'), k(12, X(C), 'easeInQuad')]}},
    'effects': [snd(0, 'lever', 0.4), snd(9, 'clack', 0.6), spark(10)]}))

# vacate (SHUT -> OPEN), 12: the jaws hold it two ticks, the break-spark as it leaves them, it falls open under its own
# weight onto the stop 4 degrees past rest (contact on 8), bounces, settles on 12.
CLIPS.append(('vacate', {'length': 12, 'loop': 'hold',
    'bones': {'contacts': {'rotation': [k(0, X(C)), k(2, X(C)), k(8, X(-4), 'easeInQuad'), k(10, X(2), 'easeOutQuad'),
                                        k(12, X(0), 'easeInQuad')]}},
    'effects': [snd(2, 'unclack', 0.5), spark(3), snd(8, 'thunk', 0.4)]}))

# brownout (SHUT -> LOW), 16: a shiver in the jaws, then the blade sags half out of them (nothing to give).
CLIPS.append(('brownout', {'length': 16, 'loop': 'hold',
    'bones': {'contacts': {'rotation': [k(0, X(C)), k(3, X(C - 3), 'easeOutQuad'), k(5, X(C), 'easeInQuad'),
                                        k(13, X(LOW - 3), 'easeInOutSine'), k(16, X(LOW), 'easeInOutSine')]}},
    'effects': [snd(0, 'fizzle', 0.4), (8, 'particle', 'minecraft:smoke', 'jaws')]}))

# brownout_out (LOW -> SHUT), 10: power again: a 3-degree wind-up, home into the jaws on 6, bounce, seated.
CLIPS.append(('brownout_out', {'length': 10, 'loop': 'hold',
    'bones': {'contacts': {'rotation': [k(0, X(LOW)), k(2, X(LOW - 3), 'easeOutQuad'), k(6, X(C), 'easeInQuad'),
                                        k(7, X(C - 3), 'easeOutQuad'), k(9, X(C), 'easeInQuad'), k(10, X(C))]}},
    'effects': [snd(6, 'clack', 0.6), spark(7)]}))

# occupy_low (OPEN -> LOW), 18: thrown home as in `occupy`, then it sags out (a jar on an empty dock).
CLIPS.append(('occupy_low', {'length': 18, 'loop': 'hold',
    'bones': {'contacts': {'rotation': [k(0, X(0)), k(3, X(-6), 'easeOutQuad'), k(4, X(-6)), k(9, X(C), 'easeInQuad'),
                                        k(10, X(C - 4), 'easeOutQuad'), k(11, X(C), 'easeInQuad'),
                                        k(16, X(LOW - 3), 'easeInOutSine'), k(18, X(LOW), 'easeInOutSine')]}},
    'effects': [snd(0, 'lever', 0.4), snd(9, 'clack', 0.6), snd(12, 'fizzle', 0.4)]}))

# vacate_low (LOW -> OPEN), 10: falls open from the droop onto its stop (contact on 6), bounce, rest.
CLIPS.append(('vacate_low', {'length': 10, 'loop': 'hold',
    'bones': {'contacts': {'rotation': [k(0, X(LOW)), k(6, X(-4), 'easeInQuad'), k(8, X(2), 'easeOutQuad'),
                                        k(10, X(0), 'easeInQuad')]}},
    'effects': [snd(6, 'thunk', 0.4)]}))

# full (SHUT -> FULL), 14: the jar is full while still standing there: the switch trips open
# (the vacate fall) and a ding. Lever down with a jar on the plate = done, step off. The meter loop stops.
CLIPS.append(('full', {'length': 14, 'loop': 'hold',
    'bones': {'contacts': {'rotation': [k(0, X(C)), k(2, X(C)), k(8, X(-4), 'easeInQuad'), k(10, X(2), 'easeOutQuad'),
                                        k(12, X(0), 'easeInQuad')]}},
    'effects': [snd(2, 'unclack', 0.5), spark(3), snd(8, 'thunk', 0.4), snd(10, 'ding', 0.6)]}))

# topup (FULL -> SHUT), 12: the jar has used some (below 95 %): thrown home again (the occupy throw).
CLIPS.append(('topup', {'length': 12, 'loop': 'hold',
    'bones': {'contacts': {'rotation': [k(0, X(0)), k(3, X(-6), 'easeOutQuad'), k(4, X(-6)), k(9, X(C), 'easeInQuad'),
                                        k(10, X(C - 4), 'easeOutQuad'), k(12, X(C), 'easeInQuad')]}},
    'effects': [snd(0, 'lever', 0.4), snd(9, 'clack', 0.6), spark(10)]}))

# ---- the moments (same controller: they key the same channel, so the chooser plays them over the state clip and then
# returns to the state clip's last frame). Block event `tap` (use): the lever jumps; the needle's tap is code.
# tap (OPEN), 10: a 3-degree dip, flicked 40 degrees toward shut (easeOutCubic, the grip's end travels 7 px), falls
# back onto its stop (contact 7), bounce, rest.
CLIPS.append(('tap', {'length': 10, 'loop': 'hold',
    'bones': {'contacts': {'rotation': [k(0, X(0)), k(1, X(-3), 'easeOutQuad'), k(3, X(38), 'easeOutCubic'),
                                        k(4, X(40), 'easeInOutSine'), k(7, X(-3), 'easeInQuad'),
                                        k(8, X(1.5), 'easeOutQuad'), k(10, X(0), 'easeInQuad')]}},
    'effects': [snd(0, 'tap', 0.5), snd(7, 'thunk', 0.35)]}))

# tap_on (SHUT), 10: knocked 36 degrees out of the jaws (the break-spark), slams home (contact 7, the make-spark).
CLIPS.append(('tap_on', {'length': 10, 'loop': 'hold',
    'bones': {'contacts': {'rotation': [k(0, X(C)), k(3, X(C - 34), 'easeOutCubic'), k(4, X(C - 36), 'easeInOutSine'),
                                        k(7, X(C), 'easeInQuad'), k(8, X(C - 3), 'easeOutQuad'),
                                        k(10, X(C), 'easeInQuad')]}},
    'effects': [snd(0, 'tap', 0.5), spark(3), snd(7, 'clack', 0.6), spark(8)]}))

# tap_low (LOW), 10: knocked up into the jaws, it will not stay (no FE): back to the droop.
CLIPS.append(('tap_low', {'length': 10, 'loop': 'hold',
    'bones': {'contacts': {'rotation': [k(0, X(LOW)), k(3, X(C), 'easeOutCubic'), k(4, X(C)),
                                        k(8, X(LOW - 3), 'easeInQuad'), k(10, X(LOW), 'easeInOutSine')]}},
    'effects': [snd(0, 'tap', 0.5), snd(3, 'clack', 0.4)]}))

# refuse (block event 1, OPEN), 8: the open lever hops twice on its stop, rotX +8, -3 (onto the stop, easeInQuad),
# +6, 0 (the house rhythm, its downward half cut short by the stop). Seen from the front: the grip's end bobs 1.4 px
# up and down. (A sideways wag would twist the blade in its hinge cheeks.)
CLIPS.append(('refuse', {'length': 8, 'loop': 'hold',
    'bones': {'contacts': {'rotation': [k(0, R()), k(2, X(8), 'easeOutQuad'), k(4, X(-3), 'easeInQuad'),
                                        k(6, X(6), 'easeOutQuad'), k(8, R(), 'easeInQuad')]}},
    'effects': [(0, 'sound', 'headinajar:machine.rattle|0.4|1.0', None)]}))

# refuse_on (SHUT or LOW), 8: the blade chatters in the jaws, 5 degrees out and home twice (the needle's wag carries
# the refusal from the front).
CLIPS.append(('refuse_on', {'length': 8, 'loop': 'hold',
    'bones': {'contacts': {'rotation': [k(0, X(C)), k(2, X(C - 5), 'easeInOutSine'), k(4, X(C), 'easeInQuad'),
                                        k(6, X(C - 4), 'easeInOutSine'), k(8, X(C), 'easeInQuad')]}},
    'effects': [(0, 'sound', 'headinajar:machine.rattle|0.4|1.0', None)]}))

# charging (a second controller, "meter": effects only, no bone keys), 40, loop while SHUT and the jar is not full:
# a spark at the jaws every second, the meter's soft tick between.
CLIPS.append(('charging', {'length': 40, 'loop': 'loop', 'bones': {},
    'effects': [spark(0), snd(10, 'tick', 0.2), spark(20), snd(30, 'tick', 0.2, 1.1)]}))


# ---------------------------------------------------------------------------------------------------------------------
# NEEDLE: the block entity's code, written out (and simulated for the review clips). Per client tick, per dock:
#   target  = 180 * f                     f: the occupant's charge fraction (the jar player's JarState via FormSync, or
#                                           the dock's synced `charge` byte for a head set down); 0 when vacant
#   target -= 8 while LOW (the sag), floor -8
#   tap (block event, param p = the buffer's percent):  vacant: target = 180 * p / 100 for 40 ticks (the needle shows
#            what the dock holds while the line says it); occupied: vel += 30 (a knock, it wobbles back)
#   refusal (block event 1): target += 14 * sin(pi * t / 2) for t in 0..8 (a wag, twice)
#   prev = theta; vel = vel * 0.72 + (target - theta) * 0.25; theta += vel
#   pins: theta > 190 -> theta = 190, vel = -0.3 * vel;  theta < -10 -> theta = -10, vel = -0.3 * vel
# Drawn every frame for every instance (2.7 rule 1): needle.rotZ = initial + rad(lerp(partial, prev, theta)).
# Step-on: the target jumps from 0 to 180 f; the spring overshoots about 45 degrees and wobbles back twice over
# 20 ticks (the bathroom scale). FormSync's 10-tick steps give a small tick-up wobble each time: a meter.
# ---------------------------------------------------------------------------------------------------------------------
def needle_sim(n, target_fn, kicks=()):
    th = v = 0.0
    out = []
    for t in range(n + 1):
        out.append(th)
        if t in kicks:
            v += kicks[t]
        v = v * 0.72 + (target_fn(t) - th) * 0.25
        th += v
        if th > 190:
            th, v = 190, -0.3 * v
        if th < -10:
            th, v = -10, -0.3 * v
    return out


def needle_keys(vals):
    return [k(t, R(z=round(a, 2))) for t, a in enumerate(vals)]


def contacts_keys(name, t0=0, n=None):
    """The clip's contacts keys, shifted by t0 and padded (for review clips that combine with the needle)."""
    clip = dict(CLIPS)[name]
    keys = clip['bones']['contacts']['rotation']
    return [k(t + t0, v, e, a) for (t, v, e, a, _i) in keys]


def review_clips():
    F = 0.6 * 180
    rc = []

    def hold(name, contacts_x, needle):
        rc.append((name, {'length': 1, 'loop': 'hold', 'review': True, 'effects': [], 'bones': {
            'contacts': {'rotation': [k(0, contacts_x)]}, 'needle': {'rotation': [k(0, R(z=needle))]}}}))

    hold('_review_shut', X(C), F)             # charging, 60 %
    hold('_review_full', X(C), 180)
    hold('_review_done', X(0), 180)          # v2: full, the switch tripped open, the jar still on
    hold('_review_low', X(LOW), F - 8)        # the brown-out
    hold('_review_open_tapped', X(0), 0.55 * 180)   # an empty dock tapped: the needle shows the buffer (55 %)
    # step-on: the switch throws (occupy, 0-12) while the needle swings to 60 % with the scale's wobble
    vals = needle_sim(40, lambda t: 0 if t < 2 else F)
    rc.append(('_review_stepon', {'length': 40, 'loop': 'hold', 'review': True, 'effects': [], 'bones': {
        'contacts': {'rotation': contacts_keys('occupy') + [k(40, X(C))]}, 'needle': {'rotation': needle_keys(vals)}}}))
    # a tap while charging: the lever knocked out and home, the needle knocked and wobbling back
    vals = needle_sim(30, lambda t: F, kicks={0: 30})
    vals = [F] + vals[:-1]
    rc.append(('_review_tap_on', {'length': 30, 'loop': 'hold', 'review': True, 'effects': [], 'bones': {
        'contacts': {'rotation': contacts_keys('tap_on') + [k(30, X(C))]}, 'needle': {'rotation': needle_keys(vals)}}}))
    # a tap on an empty dock: the needle swings to the buffer's 55 % and back after 40 ticks
    vals = needle_sim(70, lambda t: 0.55 * 180 if t < 40 else 0)
    rc.append(('_review_tap', {'length': 70, 'loop': 'hold', 'review': True, 'effects': [], 'bones': {
        'contacts': {'rotation': contacts_keys('tap') + [k(70, X(0))]}, 'needle': {'rotation': needle_keys(vals)}}}))
    # the refusal on an empty dock: the lever wags, the needle wags
    vals = needle_sim(16, lambda t: 14 * math.sin(math.pi * t / 2) if t <= 8 else 0)
    rc.append(('_review_refuse', {'length': 16, 'loop': 'hold', 'review': True, 'effects': [], 'bones': {
        'contacts': {'rotation': dict(CLIPS)['refuse']['bones']['contacts']['rotation'] + [k(16, R())]},
        'needle': {'rotation': needle_keys(vals)}}}))
    # charging for 200 ticks from 20 % to 100 %: the needle ticking up in FormSync's 10-tick steps
    def tgt(t):
        return 180 * min(1.0, 0.2 + 0.004 * (t - t % 10))
    th = [36.0]
    v = 0.0
    for t in range(200):
        v = v * 0.72 + (tgt(t) - th[-1]) * 0.25
        th.append(min(190, th[-1] + v))
    rc.append(('_review_charging', {'length': 200, 'loop': 'hold', 'review': True, 'effects': [], 'bones': {
        'contacts': {'rotation': [k(0, X(C)), k(200, X(C))]}, 'needle': {'rotation': needle_keys(th)}}}))
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
# Sweep. Pose: p' = pivot + Rz Ry Rx (p - pivot), keyframe-sign degrees (+x turns +y toward +z; +z turns +x toward +y).
# The switch's outliner rest is -65 (forward), the keyframes' own sign.
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


def rot(v, r):
    x, y, z = v
    ax, ay, az = [math.radians(a) for a in r]
    y, z = y * math.cos(ax) - z * math.sin(ax), y * math.sin(ax) + z * math.cos(ax)
    x, z = x * math.cos(ay) + z * math.sin(ay), -x * math.sin(ay) + z * math.cos(ay)
    x, y = x * math.cos(az) - y * math.sin(az), x * math.sin(az) + y * math.cos(az)
    return [x, y, z]


def points(f, t, step=0.5):
    n = [max(1, int(round((t[i] - f[i]) / step))) for i in range(3)]
    return [[f[0] + (t[0] - f[0]) * i / n[0], f[1] + (t[1] - f[1]) * j / n[1], f[2] + (t[2] - f[2]) * kk / n[2]]
            for i in range(n[0] + 1) for j in range(n[1] + 1) for kk in range(n[2] + 1)]


def inside(p, f, t, eps=0.05):
    return all(f[i] + eps < p[i] < t[i] - eps for i in range(3))


def jar_solids(group):
    """The stand-in jar's solid boxes (glass included) for a STANDINS entry, as (name, f, t, yaw, centre)."""
    for gname, compact, at, yaw, _shown in dock_geo.STANDINS:
        if gname == group:
            return [(n, f, t, yaw, at) for n, f, t, mat in dock_geo.jar_boxes(compact, at)], yaw, at
    raise KeyError(group)


def in_jar(p, jar):
    boxes, yaw, at = jar
    q = rot([p[0] - at[0], p[1] - at[1], p[2] - at[2]], (0, -yaw, 0))
    q = [q[0] + at[0], q[1] + at[1], q[2] + at[2]]
    return [n for n, f, t, _y, _a in boxes if inside(q, f, t)]


def sweep(m):
    static = [c for c in m.drawn() if kit.is_static(m, c[4])]
    moving = {b: [c for c in m.drawn() if c[4] == b] for b in ('contacts', 'needle')}
    pivots = {b[0]: b[1] for b in m.bones}
    jars = {g: jar_solids(g) for g in ('standin_jar', 'standin_jar_45', 'standin_compact', 'standin_jar_front')}
    hits = {}
    lo, hi = [99] * 3, [-99] * 3

    def test(bone, r, label):
        for c in moving[bone]:
            for p in points(c[1], c[2]):
                q = rot([p[i] - pivots[bone][i] for i in range(3)], r)
                q = [pivots[bone][i] + q[i] for i in range(3)]
                for i in range(3):
                    lo[i] = min(lo[i], q[i])
                    hi[i] = max(hi[i], q[i])
                for s in static:
                    if s[0] == c[0]:
                        continue
                    if inside(q, s[1], s[2]):
                        hits.setdefault('%s %s in %s' % (label, c[0], s[0]), 0)
                        hits['%s %s in %s' % (label, c[0], s[0])] += 1
                for g, jar in jars.items():
                    for n in in_jar(q, jar):
                        key = '%s %s in %s.%s' % (label, c[0], g, n)
                        hits[key] = hits.get(key, 0) + 1
    for name, clip in CLIPS:
        keys = clip['bones'].get('contacts', {}).get('rotation')
        if not keys:
            continue
        for qt in range(clip['length'] * 4 + 1):
            v = channel_at(keys, qt / 4.0)
            test('contacts', [v[0] - C, v[1], v[2]], name)
    for a in range(-10, 191, 2):
        test('needle', [0, 0, a], 'needle')
    return hits, lo, hi


def main():
    kit.write_clips(PATH, CLIPS, code_channels=dock_geo.model().code)
    sys.stderr.write('wrote %d clips into %s\n' % (len(CLIPS), PATH))
    if '--review' in sys.argv:
        rv = REVIEW_DIR + '/dock_review.bbmodel'
        kit.write_clips(PATH, CLIPS, code_channels=dock_geo.model().code, review=rv, review_clips=review_clips())
        base = REVIEW_DIR + '/dock_%s.bbmodel'
        variant(rv, base % 'empty', hide=('standin_jar',))
        variant(rv, base % '45', show=('standin_jar_45',), hide=('standin_jar',))
        variant(rv, base % 'compact', show=('standin_compact',), hide=('standin_jar',))
        variant(rv, base % 'front', show=('standin_jar_front',), hide=('standin_jar',))
        sys.stderr.write('review copy %s and variants written\n' % rv)
    if '--sweep' in sys.argv:
        hits, lo, hi = sweep(dock_geo.model())
        for h, n in sorted(hits.items()):
            print('HIT %s (%d points)' % (h, n))
        print('sweep: %d hit kinds; moving parts span x %.2f..%.2f, y %.2f..%.2f, z %.2f..%.2f' % (
            len(hits), lo[0], hi[0], lo[1], hi[1], lo[2], hi[2]))
    print(kit.effects_table(CLIPS))


if __name__ == '__main__':
    main()

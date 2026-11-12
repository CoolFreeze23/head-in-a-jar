"""Life Support Cradle: every clip, keyed by game tick, written into cradle.bbmodel.

Source of truth for the Cradle's animations; hand edits in Blockbench are overwritten by the next run.

    python art/machines/cradle/cradle_anim.py [--review] [--sweep]

--review also writes build/machines-review/cradle_review.bbmodel (eased segments baked per tick, plus the
`_review_*` stills) and the variants cradle_empty (no jar), cradle_compact (the Compact jar), cradle_bag0 / cradle_bag4.
--sweep samples every bone of every clip at quarter ticks and reports any point of a moving box inside a static box or
the stand-in jar (WIDE, or COMPACT for the *_c clips), and the posed bounds (the render box).

Signs (keyframes, Blockbench = GeckoLib, checked by render): rotX +a turns +y towards +z, so the hose's lay-down
(its top coming forward to -Z) is rotX NEGATIVE; rotY -90 turns the blanket's front end (-Z) to +X; the hose lies down by rotX -90 and turns inward by
rotY +45 (GeckoLib and Blockbench apply X first, then Y, then Z). Positions are
Blockbench pixels as displayed.

Nothing here is driven by code: the breath is a clip seeked by game time (see the notes).
"""
import copy
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
import kit  # noqa: E402
from kit import k, R  # noqa: E402
import cradle_geo  # noqa: E402

REVIEW_DIR = 'build/machines-review'
P = lambda y: R(y=y)  # noqa: E731

HOSE_DOWN = -90          # WIDE: the nozzle on the valve's top (y 22)
HOSE_DOWN_C = -105       # COMPACT: its valve top is 1.5 px lower (20.5)
HOSE_IN = 45             # rotY after the lay-down: from the collar right of centre, inward onto the valve
LAID = [9, -10, -3]      # the blanket's offset from its rest centre (-9, 14, -6) to the lap (0, 4, -9)


def H(x, y=HOSE_IN):
    return R(x=x, y=y)


# ---- dock: 24 ticks, hold; played when `dockedAt` changes (the seeking controller: a late joiner lands on 24).
# The jar is drawn from tick 0 (the server docks it at once). 2-10 the hose: lifted 4 degrees off its hook (2: it
# cannot wind up backwards, the headrest is there), held a tick, laid forward and turned inward onto the valve,
# easeInQuad into the contact on 8 (the click), a 4-degree bounce, back on 10. 11-24 the blanket: lifted half a
# pixel with a 4-degree twist the other way (13), pulled forward off the arm's front end and turning (16), carried
# low across the front of the jar, never above y 16 (19), dropped onto the seat's front rail (22, easeInQuad: the
# flump), a 0.5 px bounce, settled on 24.
def hose_dock(down):
    return [k(0, R()), k(2, R(x=-4), 'easeOutQuad'), k(3, R(x=-4)), k(8, H(down), 'easeInQuad'),
            k(9, H(down + 4), 'easeOutQuad'), k(10, H(down), 'easeInQuad')]


def hose_undock(down):
    # 0-6: the seal lets go (a 4-degree press first), the hose springs back up to its hook (contact on 4), bounces 5
    return [k(0, H(down)), k(1, H(down - 4), 'easeOutQuad'), k(4, R(), 'easeInQuad'), k(5, R(x=-5), 'easeOutQuad'),
            k(6, R(), 'easeInQuad')]


BLANKET_DOCK = {
    'rotation': [k(0, R()), k(11, R()), k(13, R(y=4), 'easeOutQuad'), k(16, R(y=-30), 'easeInOutSine'),
                 k(19, R(y=-90), 'easeInOutSine')],
    'position': [k(0, R()), k(11, R()), k(13, R(y=0.5), 'easeOutQuad'), k(16, [1, 2, -4], 'easeInOutSine'),
                 k(19, [8, 0, -4], 'easeInOutSine'), k(22, LAID, 'easeInQuad'),
                 k(23, [LAID[0], LAID[1] + 0.5, LAID[2]], 'easeOutQuad'), k(24, LAID, 'easeInQuad')],
}
# undock 6-18: lifted half a pixel off the rail (7), carried back low in front of the seat (10), turned and pushed
# back along the arm (13), set down on the arm (16, easeInQuad: a soft flump), a 0.5 px bounce, settled on 18
BLANKET_UNDOCK = {
    'rotation': [k(0, R(y=-90)), k(10, R(y=-90)), k(13, R(y=-30), 'easeInOutSine'), k(16, R(), 'easeInOutSine')],
    'position': [k(0, LAID), k(6, LAID), k(7, [LAID[0], LAID[1] + 0.5, LAID[2]], 'easeOutQuad'),
                 k(10, [8, 0, -4], 'easeOutCubic'), k(13, [1, 2, -4], 'easeInOutSine'), k(16, R(), 'easeInQuad'),
                 k(17, R(y=0.5), 'easeOutQuad'), k(18, R(), 'easeInQuad')],
}


def dock(down):
    return {'length': 24, 'loop': 'hold',
            'bones': {'hose_end': {'rotation': hose_dock(down)}, 'blanket': copy.deepcopy(BLANKET_DOCK)},
            'effects': [(2, 'sound', 'headinajar:cradle.hose_swing|0.3|1.0', None),
                        (8, 'sound', 'headinajar:cradle.hose_click|0.5|1.0', None),
                        (9, 'particle', 'headinajar:cradle_drip', 'valve'),
                        (13, 'sound', 'headinajar:cradle.blanket_whoosh|0.4|1.0', None),
                        (22, 'sound', 'headinajar:cradle.flump|0.5|1.0', None),
                        (23, 'particle', 'headinajar:cradle_fluff', 'lap')]}


# ---- undock: 20 ticks, hold, played after the server has taken the head. The hose reacts first (it held the
# thing that left): the seal's pfff (0), the hose springs up onto its hook (contact 4); then the blanket goes back
# onto the left arm (6-18).
def undock(down):
    return {'length': 20, 'loop': 'hold',
            'bones': {'hose_end': {'rotation': hose_undock(down)}, 'blanket': copy.deepcopy(BLANKET_UNDOCK)},
            'effects': [(0, 'sound', 'headinajar:cradle.seal_pfff|0.5|1.0', None),
                        (1, 'particle', 'headinajar:cradle_puff', 'valve'),
                        (4, 'sound', 'headinajar:cradle.hose_hang|0.4|1.0', None),
                        (7, 'sound', 'headinajar:cradle.blanket_whoosh|0.4|1.1', None),
                        (16, 'sound', 'headinajar:cradle.flump|0.4|1.2', None)]}


# ---- breathe: 60 ticks, loop, seeked at (gameTime mod 60) while a head is docked and the buffer has FE. The upkeep
# runs on gameTime % 20 == 0, so loop ticks 0, 20 and 40 are upkeep ticks (a saline drop into the valve on each).
# The crest (the bellows' board, 18 px wide) rises 6 px out of the back on its dark pleats and sinks again: a
# quarter-pixel press (2), easeInOutSine up past the top by a quarter (22), settled at 6 (26), held to 32, down
# easeInOutSine to half a pixel (48), contact easeInQuad (50), a 0.4 px bounce (52), down (54), still to 60.
BREATHE = {
    'length': 60, 'loop': 'loop',
    'bones': {'bellows': {'position': [k(0, P(0)), k(2, P(-0.25), 'easeOutQuad'), k(22, P(6.25), 'easeInOutSine'),
                                       k(26, P(6), 'easeInOutSine'), k(32, P(6)), k(48, P(0.5), 'easeInOutSine'),
                                       k(50, P(0), 'easeInQuad'), k(52, P(0.4), 'easeOutQuad'),
                                       k(54, P(0), 'easeInQuad'), k(60, P(0))]}},
    'effects': [(0, 'particle', 'headinajar:cradle_drip', 'valve'),
                (3, 'sound', 'headinajar:cradle.inhale|0.3|1.0', None),
                (20, 'particle', 'headinajar:cradle_drip', 'valve'),
                (40, 'particle', 'headinajar:cradle_drip', 'valve'),
                (50, 'sound', 'headinajar:cradle.exhale|0.35|1.0', None)]}

# ---- refusal (block event 1): the crest shakes its head, rotZ +6 -6 +6 0 at 2, 4, 6, 8 (the kit's house rhythm at
# twice the kit's 3 degrees: 3 degrees moved the crest's ends 0.4 px, unseen; 6 moves them 0.95), seen from the front
# and from everywhere; it keys rotation only, so it plays over a breath (which keys position).
REFUSE = kit.refuse_clip('bellows', 'rotation', axis=2, amount=6)

CLIPS = [('dock', dock(HOSE_DOWN)), ('undock', undock(HOSE_DOWN)), ('dock_c', dock(HOSE_DOWN_C)),
         ('undock_c', undock(HOSE_DOWN_C)), ('breathe', BREATHE), ('refuse', REFUSE)]


def review_clips():
    return [
        ('_review_docked', {'length': 1, 'loop': 'hold', 'review': True, 'effects': [], 'bones': {
            'hose_end': {'rotation': [k(0, H(HOSE_DOWN))]},
            'blanket': {'rotation': [k(0, R(y=-90))], 'position': [k(0, LAID)]}}}),
        ('_review_docked_c', {'length': 1, 'loop': 'hold', 'review': True, 'effects': [], 'bones': {
            'hose_end': {'rotation': [k(0, H(HOSE_DOWN_C))]},
            'blanket': {'rotation': [k(0, R(y=-90))], 'position': [k(0, LAID)]}}}),
        # docked and at the top of a breath (the working moment)
        ('_review_breath', {'length': 1, 'loop': 'hold', 'review': True, 'effects': [], 'bones': {
            'hose_end': {'rotation': [k(0, H(HOSE_DOWN))]},
            'blanket': {'rotation': [k(0, R(y=-90))], 'position': [k(0, LAID)]},
            'bellows': {'position': [k(0, P(6))]}}}),
        # the breathing as a bystander sees it: docked pose held, the breath clip's bellows keys
        ('_review_breathe', {'length': 60, 'loop': 'loop', 'review': True, 'effects': [], 'bones': {
            'hose_end': {'rotation': [k(0, H(HOSE_DOWN))]},
            'blanket': {'rotation': [k(0, R(y=-90))], 'position': [k(0, LAID)]},
            'bellows': copy.deepcopy(BREATHE['bones']['bellows'])}}),
        ('_review_refuse_docked', {'length': 8, 'loop': 'once', 'review': True, 'effects': [], 'bones': {
            'hose_end': {'rotation': [k(0, H(HOSE_DOWN))]},
            'blanket': {'rotation': [k(0, R(y=-90))], 'position': [k(0, LAID)]},
            'bellows': copy.deepcopy(REFUSE['bones']['bellows'])}}),
    ]


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
# Sweep: pose evaluation (GeckoLib / Blockbench: p' = pivot + offset + Rz Ry Rx (p - pivot), degrees, right-handed)
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


def posed_points(c, pivot, r, off, step=0.5):
    f, t = c[1], c[2]
    pts = []
    n = [max(1, int(round((t[i] - f[i]) / step))) for i in range(3)]
    for i in range(n[0] + 1):
        for j in range(n[1] + 1):
            for kk in range(n[2] + 1):
                p = [f[0] + (t[0] - f[0]) * i / n[0], f[1] + (t[1] - f[1]) * j / n[1], f[2] + (t[2] - f[2]) * kk / n[2]]
                q = rot([p[a] - pivot[a] for a in range(3)], r)
                pts.append([pivot[a] + off[a] + q[a] for a in range(3)])
    return pts


def inside(p, f, t, eps=0.05):
    return all(f[a] + eps < p[a] < t[a] - eps for a in range(3))


# intended overlaps: the pleated column lives inside the back rail and slides out of it; the hose turns in its
# collar (a hinge); the crest's quarter-pixel press at the start of a breath and its 3-degree refusal rock sink its
# edges into the rail, stile and headrest tops it sits on.
ALLOWED = {('pleat_core', 'back_rail'), ('pleat_core_1', 'back_rail'), ('pleat_core_2', 'back_rail'), ('pleat_rib_0', 'back_rail'), ('pleat_rib_1', 'back_rail'),
           ('pleat_rib_2', 'back_rail'), ('hose', 'hose_collar'), ('crest', 'back_rail'), ('crest', 'stile_l'),
           ('crest', 'stile_r'), ('crest', 'headrest')}


def sweep(m):
    pivots = {b[0]: b[1] for b in m.bones}
    statics = [(c[0], c[1], c[2]) for c in m.drawn() if kit.is_static(m, c[4])]
    jars = {False: [('jar_' + n, f, t) for n, f, t, mat in cradle_geo.jar_boxes(False)],
            True: [('jarC_' + n, f, t) for n, f, t, mat in cradle_geo.jar_boxes(True)]}
    lo, hi = [1e9] * 3, [-1e9] * 3
    hits = {}
    for name, clip in CLIPS + review_clips():
        compact = name.endswith('_c')
        obstacles = statics + jars[compact] if name not in ('undock', 'undock_c') else statics
        # undock: the server has taken the head before the clip plays, so no jar is drawn
        for q in range(int(clip['length'] * 4) + 1):
            t = q / 4.0
            for bone in m.bone_names():
                ch = clip['bones'].get(bone, {})
                r = channel_at(ch['rotation'], t) if 'rotation' in ch else [0, 0, 0]
                o = channel_at(ch['position'], t) if 'position' in ch else [0, 0, 0]
                for c in [c for c in m.drawn() if c[4] == bone]:
                    for p in posed_points(c, pivots[bone], r, o):
                        for a in range(3):
                            lo[a], hi[a] = min(lo[a], p[a]), max(hi[a], p[a])
                        for on, f, tt in obstacles:
                            if (c[0], on) not in ALLOWED and inside(p, f, tt):
                                key = (name, c[0], on)
                                hits.setdefault(key, []).append(t)
    for (name, cube, on), ts in sorted(hits.items()):
        print('  HIT %s: %s in %s at ticks %s..%s' % (name, cube, on, min(ts), max(ts)))
    print('sweep: %d hit pairs; bones reach x %.2f..%.2f, y %.2f..%.2f, z %.2f..%.2f' % (
        len(hits), lo[0], hi[0], lo[1], hi[1], lo[2], hi[2]))
    return hits


if __name__ == '__main__':
    m = cradle_geo.model()
    review = REVIEW_DIR + '/cradle_review.bbmodel' if '--review' in sys.argv else None
    n = kit.write_clips(cradle_geo.PATH, CLIPS, code_channels=m.code, review=review,
                        review_clips=review_clips() if review else ())
    print('wrote %d clips' % n)
    if review:
        variant(review, REVIEW_DIR + '/cradle_empty.bbmodel', hide=['standin_jar'])
        variant(review, REVIEW_DIR + '/cradle_compact.bbmodel', show=['standin_jar_compact'], hide=['standin_jar'])
        variant(review, REVIEW_DIR + '/cradle_bag0.bbmodel', show=['state_saline_0'], hide=['state_saline_3'])
        variant(review, REVIEW_DIR + '/cradle_bag4.bbmodel', show=['state_saline_4'], hide=['state_saline_3'])
        variant(review, REVIEW_DIR + '/cradle_empty_bag1.bbmodel', show=['state_saline_1'],
                hide=['state_saline_3', 'standin_jar'])
        print('review copy and variants written')
    if '--sweep' in sys.argv:
        sweep(m)
    print(kit.effects_table(CLIPS))

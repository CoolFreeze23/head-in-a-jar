"""Spider Walker ("multi-leg" skeleton, eight legs): every clip, keyed in ticks, written into spider_walker.bbmodel.

Source of truth for the clips; hand edits to the bbmodel's animations are overwritten. Rig numbers come from
spider_walker_geo.py; the keyframe helpers and GeckoLib 4.9.3 easing curves are the Hopper's (hopper_anim.py).

    python art/shell/spider_walker/spider_walker_anim.py                         # write the clips into the bbmodel
    python art/shell/spider_walker/spider_walker_anim.py --model X --preview OUT # baked preview copy

Legs are never keyed by hand. Each clip gives point targets (on the ground or a wall in model space, or relative to the
hip in the body's frame); every tick the generator evaluates the body's eased position and rotation and solves each
of the eight legs: the hip's yaw toward the point, then the two-bone thigh and shin in that plane, knee up.

Speeds are the release line's:
  ridden pace 0.28 (entity/ShellEntity.java:60) x 1.1 full sync (host/HostControl.java:67) x RIDDEN_SCALE 0.5
  (entity/ShellEntity.java:68, applied host/HostControl.java:68) x 2.16 vanilla ground (inferred) = 0.333 blocks a
  tick; the measured 6.8 blocks a second is 0.34, which the walk is built for
  backing x 0.25 (host/HostControl.java:24); climbing 0.2 blocks a tick straight up (host/HostControl.java:49-53)

Signs (Blockbench, as displayed): body rot x + = nose up, rot z + = left side down; clamp_l rot z + / clamp_r rot z -
= the claws swung open; reel (the key) rot z - = winding as it walks; launcher position z + = recoil; sensor rot x - =
the eyes look down.
"""
import copy
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MECHA = os.path.join(os.path.dirname(os.path.dirname(HERE)), 'mecha')
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(MECHA, 'hopper'))
import spider_walker_geo as G  # noqa: E402
from hopper_anim import k, R, P, S, lamp, const, value_at, keyframe, bake, snd, nid, thin  # noqa: E402,F401

MODEL = os.path.join(HERE, 'spider_walker.bbmodel')
T = 20.0
V = 0.34 * 16                  # 5.44 px a tick: the release branch's ridden pace (see above)
V_BACK = V * 0.25              # 1.36 px a tick
V_LIMP = V * 0.25              # the low-charge crawl (a proposal: see the notes)
V_CLIMB = 0.2 * 16             # 3.2 px a tick, up a wall
LEGS = G.LEGS
SIDE = G.SIDE
FOOT0 = {c: (G.FEET[c][0], 0.0, G.FEET[c][1]) for c in LEGS}
GROUP_A = ('fl', 'r2', 'l3', 'rr')          # the alternating tetrapod: these four step together...
GROUP_B = ('fr', 'l2', 'r3', 'rl')          # ...then these
FRONT = ('fl', 'fr', 'l2', 'r2')
REAR = ('l3', 'r3', 'rl', 'rr')
PARK_Y = -2.5
WALL = -11.5                                # the wall's face ahead, just past the hitbox's front (-11.2)


def foot(c, s=1.0, dz=0.0, y=0.0):
    return (G.FEET[c][0] * s, y, G.FEET[c][1] * s + dz)


def rest_rel(c, s=1.0, body_y=0.0):
    """The point's offset from its hip (body frame) for a foot on the ground at s x its rest spread."""
    hx, hz = G.HIPS[c]
    return (G.FEET[c][0] * s - hx, -(G.BODY_Y + body_y) + G.FOOT_LIFT, G.FEET[c][1] * s - hz)


# ------------------------------------------------------------------------------------------------------------------
# Leg solver (the Skitter's, for eight legs at their own hips)
# ------------------------------------------------------------------------------------------------------------------
def rot3(v, x, y, z, inverse=False):
    def rx_(p, a):
        return (p[0], p[1] * math.cos(a) - p[2] * math.sin(a), p[1] * math.sin(a) + p[2] * math.cos(a))

    def ry_(p, a):
        return (p[0] * math.cos(a) + p[2] * math.sin(a), p[1], -p[0] * math.sin(a) + p[2] * math.cos(a))

    def rz_(p, a):
        return (p[0] * math.cos(a) - p[1] * math.sin(a), p[0] * math.sin(a) + p[1] * math.cos(a), p[2])
    if inverse:
        return rx_(ry_(rz_(v, z), y), x)
    return rz_(ry_(rx_(v, x), y), z)


def solve_legs(clip):
    feet = clip.get('feet')
    if feet is None:
        return
    bd = clip['bones'].get('body', {})
    bpos = sorted(bd.get('position', [k(0, P())]), key=lambda x: x[0])
    brot = sorted(bd.get('rotation', [k(0, R())]), key=lambda x: x[0])
    out = {c: {'hip': [], 'leg': [], 'shin': []} for c in LEGS}
    for t in range(0, int(clip['length']) + 1):
        b = value_at(bpos, t)
        rx, ry, rz = [math.radians(a) for a in value_at(brot, t)]
        for c in LEGS:
            hx0, hz0 = G.HIPS[c]
            off = rot3((hx0, 0.0, hz0), rx, ry, rz)
            hx, hy, hz = off[0] + b[0], G.BODY_Y + off[1] + b[1], off[2] + b[2]
            f = feet(c, t)
            if len(f) == 4:
                dx, dy, dz = f[0], f[1], f[2]
            else:
                dx, dy, dz = f[0] - hx, f[1] + G.FOOT_LIFT - hy, f[2] - hz
                dx, dy, dz = rot3((dx, dy, dz), -rx, -ry, -rz, inverse=True)
            r = math.hypot(dx, dz)
            yaw = math.degrees(math.atan2(-dx, -dz)) - G.REST_YAW[c]
            yaw = (yaw + 180) % 360 - 180
            a1, a2 = G.leg_ik(r, dy)
            out[c]['hip'].append((t, yaw))
            out[c]['leg'].append((t, a1 - G.REST_FEMUR[c]))
            out[c]['shin'].append((t, (a2 - a1) - G.REST_TIBIA[c]))
    for c in LEGS:
        for part, axis in (('hip', 1), ('leg', 0), ('shin', 0)):
            keys = thin(out[c][part], clip.get('loop') == 'loop')
            vals = []
            for t, v in keys:
                vec = [0, 0, 0]
                vec[axis] = round(v, 3)
                vals.append(k(t, vec))
            clip['bones'].setdefault('%s_%s' % (part, c), {})['rotation'] = vals


def static(fn):
    return lambda c, t: fn(c)


def keyed(tables, rel=()):
    """Per-leg key tables -> a feet function; legs named in rel are hip-relative (body frame)."""
    srt = {c: sorted(v, key=lambda x: x[0]) for c, v in tables.items()}
    return lambda c, t: tuple(value_at(srt[c], t)) + (('rel',) if c in rel else ())


def body(pos=None, rot=None):
    d = {}
    if pos:
        d['position'] = pos
    if rot:
        d['rotation'] = rot
    return {'body': d}


def merge(*ds):
    out = {}
    for d in ds:
        for b, ch in d.items():
            out.setdefault(b, {}).update(ch)
    return out


def lamps(core=None, tail=None):
    d = {}
    if core is not None:
        d['lamp_core'] = {'scale': lamp(core)}
    if tail is not None:
        d['lamp_tail'] = {'scale': lamp(tail)}
    return d


def claws(keys):
    """Degrees the two claw pairs stand open (0 = shut on the jar foot's corners)."""
    return {'clamp_l': {'rotation': [(t, [0, 0, o[0]], e, a, i) for (t, o, e, a, i) in keys]},
            'clamp_r': {'rotation': [(t, [0, 0, -o[0]], e, a, i) for (t, o, e, a, i) in keys]}}


def key_turn(keys):
    """The clockwork key: degrees turned (written as rot z, negative = winding as it walks)."""
    return {'reel': {'rotation': [(t, [0, 0, -o[0]], e, a, i) for (t, o, e, a, i) in keys]}}


def step_foot(base, s, n, stance, reach, lift, v):
    """One foot through a cycle: planted and moving back under the body at v, then a quick lifted swing forward."""
    s %= n
    x, y, z = base
    if s <= stance:
        return (x, 0.0, z - reach + v * s)
    u = (s - stance) / float(n - stance)
    zz = z + reach - 2 * reach * (1 - math.cos(math.pi * u)) / 2
    return (x, lift * math.sin(math.pi * min(1.0, u ** 0.75)), zz)


# ==================================================================================================================
# Life cycle
# ==================================================================================================================
def parked():
    """Unpiloted: sat down on its belly with its legs drawn in round it (a resting spider), the claws shut on the
    empty collar, the eyes out, the key still. Alive: a slow settle, one leg that shifts at 50, the key ticking back
    a notch at 70 (a spring settling)."""
    tables = {c: [k(0, foot(c, 0.82))] for c in LEGS}
    tables['l3'] = [k(0, foot('l3', 0.82)), k(50, foot('l3', 0.82)), k(52, foot('l3', 0.85, y=1.5), 'easeOutQuad'),
                    k(54, foot('l3', 0.86), 'easeInQuad')]
    return {'length': 80, 'loop': 'loop', 'pilot': False, 'feet': keyed(tables), 'bones': merge(
        body([k(0, P(y=PARK_Y)), k(40, P(y=PARK_Y - 0.2), 'easeInOutSine'), k(80, P(y=PARK_Y), 'easeInOutSine')],
             [k(0, R(x=-1.5)), k(44, R(x=-1.8), 'easeInOutSine'), k(80, R(x=-1.5), 'easeInOutSine')]),
        {'sensor': {'rotation': const(R(x=-6))}},
        key_turn([k(0, [0]), k(70, [0]), k(71, [-6], 'easeOutQuad'), k(74, [-4], 'easeInOutSine'), k(80, [0], 'easeInOutSine')]),
        claws([k(0, [0])]), lamps([(0, 0)], [(0, 0)])),
        'effects': [(52, 'sound', snd('leg_tick', 0.15, 1.2), None), (71, 'sound', snd('key_tick', 0.15, 0.9), None)]}


def board():
    """Controller "cockpit": the claws spring open (contact 3), the tin rises off its belly onto its legs, the jar
    hops on from behind (it comes from the rider's side) and lands on 10: the tin squats 1.5 px under it and springs
    back; the claws snap shut on the jar's foot (15) with an overshoot; the eyes light (18); the key gives a
    proud half turn."""
    tables = {c: [k(0, foot(c, 0.82)), k(2, foot(c, 0.82)), k(7, FOOT0[c], 'easeInOutCubic')] for c in LEGS}
    b = merge(
        body([k(0, P(y=PARK_Y)), k(2, P(y=PARK_Y - 0.4), 'easeOutQuad'), k(7, P(), 'easeInOutCubic'), k(10, P()),
              k(11, P(y=-1.5), 'easeOutQuad'), k(14, P(y=0.3), 'easeInOutSine'), k(17, P(), 'easeInOutSine')],
             [k(0, R(x=-1.5)), k(7, R(), 'easeInOutCubic'), k(10, R()), k(11, R(x=-1.5), 'easeOutQuad'),
              k(15, R(), 'easeInOutSine')]),
        {'jar_anchor': {'position': [k(0, P(y=-10, z=20)), k(3, P(y=-10, z=20)), k(5, P(y=-11, z=20), 'easeOutQuad'),
                                     k(8, P(y=5, z=7), 'easeOutCubic'), k(10, P(), 'easeInQuad'),
                                     k(11, P(y=-0.3), 'easeOutQuad'), k(13, P(), 'easeInOutSine')]},
         'sensor': {'rotation': [k(0, R(x=-6)), k(7, R(), 'easeInOutSine'), k(16, R()), k(18, R(x=4), 'easeOutQuad'),
                                 k(20, R(), 'easeInOutSine')]}},
        claws([k(0, [0]), k(1, [-3], 'easeOutQuad'), k(3, [45], 'easeInExpo'), k(5, [40], 'easeInOutSine'), k(12, [40]),
                k(15, [-4], 'easeInQuad'), k(17, [0], 'easeInElastic', [3])]),
        key_turn([k(0, [0]), k(18, [0]), k(19, [10], 'easeOutQuad'), k(22, [-180], 'easeInOutCubic')]),
        lamps([(0, 0), (18, 1)], [(0, 0), (18, 1)]))
    return {'length': 22, 'loop': 'hold', 'feet': keyed(tables), 'bones': b,
            'effects': [(1, 'sound', snd('claw_release', 0.5, 1.2), None), (3, 'sound', snd('claw_open', 0.4, 1.1), None),
                        (6, 'sound', snd('jar_hop', 0.5, 1.0), None), (10, 'sound', snd('jar_seat', 0.6, 1.0), None),
                        (11, 'sound', snd('tin_creak', 0.4, 1.1), None), (15, 'sound', snd('claw_click', 0.6, 1.0), None),
                        (18, 'sound', snd('relay_click', 0.3, 1.3), None), (20, 'sound', snd('key_wind', 0.4, 1.1), None)]}


def get_off():
    """Controller "cockpit": the claws spring open, the tin squats (the wind-up), the jar hops off the back and
    lands behind it (where ShellEntity.clearOf puts it first) on 9; hidden from 10, a player again there. Rid of the
    weight, the tin bobs up 1 px; the claws shut on the empty collar (15); the eyes go out."""
    b = merge(
        body([k(0, P()), k(4, P()), k(6, P(y=-1.5), 'easeOutQuad'), k(7, P(y=-1.5)), k(8, P(y=1), 'easeOutCubic'),
              k(12, P(), 'easeInElastic', [3])],
             [k(0, R()), k(6, R(x=-2), 'easeOutQuad'), k(8, R(x=1.5), 'easeOutCubic'), k(12, R(), 'easeInElastic', [3])]),
        {'jar_anchor': {'position': [k(0, P()), k(6, P(y=-1.5), 'easeOutQuad'), k(7, P(y=-1.5)),
                                     k(8, P(y=6, z=12), 'easeOutCubic'), k(10, P(y=-13, z=22), 'easeInQuad')]}},
        claws([k(0, [0]), k(1, [-3], 'easeOutQuad'), k(3, [45], 'easeInExpo'), k(5, [40], 'easeInOutSine'), k(12, [40]),
                k(15, [-4], 'easeInQuad'), k(17, [0], 'easeInElastic', [3])]),
        lamps([(0, 1), (16, 0)], [(0, 1), (16, 0)]))
    return {'length': 20, 'loop': 'hold', 'feet': static(lambda c: FOOT0[c]), 'bones': b, 'pilot_until': 8,
            'effects': [(1, 'sound', snd('claw_release', 0.5, 1.2), None), (3, 'sound', snd('claw_open', 0.4, 1.1), None),
                        (8, 'sound', snd('jar_hop', 0.5, 1.0), None), (10, 'sound', snd('jar_land', 0.5, 1.0), None),
                        (15, 'sound', snd('claw_click', 0.5, 0.9), None)]}


def idle():
    """Standing: a 0.4 px breath, the eyes looking slowly left and back, the key ticking round a half turn in 40
    ticks (a clock), and one leg on each side re-setting its point (lift 1.5 px) a quarter cycle apart."""
    tables = {c: [k(0, FOOT0[c])] for c in LEGS}
    for c, t0 in (('l2', 6), ('r3', 16), ('fl', 26), ('rr', 34)):
        a = FOOT0[c]
        tables[c] = [k(0, a), k(t0, a), k(t0 + 2, (a[0], 1.5, a[2]), 'easeOutQuad'), k(t0 + 3, a, 'easeInQuad'), k(40, a)]
    return {'length': 40, 'loop': 'loop', 'feet': keyed(tables), 'bones': merge(
        body([k(0, P()), k(20, P(y=-0.4), 'easeInOutSine'), k(40, P(), 'easeInOutSine')]),
        {'sensor': {'rotation': [k(0, R()), k(10, R(y=10), 'easeInOutSine'), k(18, R(y=10)), k(28, R(), 'easeInOutSine')]}},
        key_turn([k(0, [0]), k(10, [45], interp='step'), k(20, [90], interp='step'), k(30, [135], interp='step'),
                  k(40, [180], interp='step')])),
        'effects': [(10, 'sound', snd('key_tick', 0.12, 1.0), None), (20, 'sound', snd('key_tick', 0.12, 1.0), None),
                    (30, 'sound', snd('key_tick', 0.12, 1.0), None)]}


def idle_tic():
    """Every 200-300 idle ticks it washes its hands: it sits back 1 px, lifts its two front legs and rubs their
    points together, twice, like a fly (or a manager), sets them down, and the key gives a satisfied half turn."""
    rel = ('fl', 'fr')
    tables = {c: [k(0, FOOT0[c])] for c in LEGS}
    eff = []
    for c in rel:
        s = SIDE[c]
        a = rest_rel(c)
        up = (-s * 4.0, 5.0, -8.0)
        rub_in, rub_out = (-s * 7.0, 5.5, -8.5), (-s * 5.0, 4.5, -7.5)
        tables[c] = [k(0, a), k(3, a), k(7, up, 'easeInOutCubic'), k(10, rub_in, 'easeInOutSine'),
                     k(12, rub_out, 'easeInOutSine'), k(14, rub_in, 'easeInOutSine'), k(16, rub_out, 'easeInOutSine'),
                     k(18, rub_in, 'easeInOutSine'), k(22, up, 'easeInOutSine'), k(27, a, 'easeInOutCubic')]
        # the right leg rubs a beat behind the left
        if s > 0:
            tables[c] = [(t + (1 if 9 < t < 20 else 0), v, e, aa, i) for (t, v, e, aa, i) in tables[c]]
    eff += [(10, 'sound', snd('leg_rub', 0.25, 1.0), None), (14, 'sound', snd('leg_rub', 0.25, 1.05), None),
            (18, 'sound', snd('leg_rub', 0.25, 0.95), None), (27, 'sound', snd('leg_tick', 0.2, 1.0), None),
            (30, 'sound', snd('key_wind', 0.3, 1.1), None)]
    return {'length': 36, 'loop': 'once', 'feet': keyed(tables, rel), 'bones': merge(
        body([k(0, P()), k(3, P(y=0.3), 'easeOutQuad'), k(7, P(y=-1, z=1), 'easeInOutCubic'), k(22, P(y=-1, z=1)),
              k(27, P(), 'easeInOutCubic')],
             [k(0, R()), k(7, R(x=5), 'easeInOutCubic'), k(22, R(x=5)), k(27, R(), 'easeInOutCubic')]),
        {'sensor': {'rotation': [k(0, R()), k(7, R(x=-10), 'easeInOutSine'), k(22, R(x=-10)), k(27, R(), 'easeInOutSine')]}},
        key_turn([k(0, [0]), k(29, [0]), k(30, [-15], 'easeOutQuad'), k(34, [180], 'easeInOutCubic')])),
        'effects': eff}


# ---- the scuttle (design speed) ---------------------------------------------------------------------------------------
WALK_N, WALK_STANCE = 6, 3          # alternating tetrapod: A (fl r2 l3 rr) then B (fr l2 r3 rl)
WALK_REACH = V * WALK_STANCE / 2.0  # 8.16 px either side of the rest point


def walk(back=False):
    """Loop at the release pace (playback = measured / 0.34 blocks a tick): the eight legs in two alternating sets
    of four, 5.5 px lifts, a 0.6 px drop on each plant, a fine roll, the eyes level, the key winding a quarter turn a stride (stepped, with a tick of overshoot),
    loop. Backing (x 0.25): a slower 12-tick loop, lower lifts."""
    v = -V_BACK if back else V
    n = 12 if back else WALK_N
    stance = n // 2
    reach = abs(v) * stance / 2.0
    half = n // 2

    def feet(c, t):
        base = FOOT0[c]
        ph = 0 if c in GROUP_A else half
        f = step_foot(base, t - ph, n, stance, reach, 3.5 if back else 5.5, abs(v))
        return f if not back else (f[0], f[1], 2 * base[2] - f[2])
    drop = lambda t: -0.6 * abs(math.cos(math.pi * t / half))
    bones = merge(body([k(t, P(y=round(drop(t), 3))) for t in range(n + 1)],
                       [k(t, R(z=round(0.7 * math.sin(2 * math.pi * t / n), 3), x=(0.5 if back else -1))) for t in range(n + 1)]),
                  key_turn([k(0, [0]), k(1, [0]), k(half - 1, [100 if not back else -50], 'easeInOutCubic'),
                            k(half, [90 if not back else -45], 'easeInOutSine'), k(half + 1, [90 if not back else -45]),
                            k(n - 1, [190 if not back else -95], 'easeInOutCubic'), k(n, [180 if not back else -90], 'easeInOutSine')]))
    return {'length': n, 'loop': 'loop', 'feet': feet, 'bones': bones,
            'effects': [(0, 'timeline', 'step:a', None), (half, 'timeline', 'step:b', None)]}


def turn(direction):
    """Turning on the spot: the two sets step in place in turn, each point set down 6 degrees round from where it
    rose; the body leads by 3."""
    s = 1 if direction == 'l' else -1
    tables = {}
    for c in LEGS:
        a = FOOT0[c]
        t0 = 0 if c in GROUP_A else 4
        ang = math.radians(6 * s)
        rot = (a[0] * math.cos(ang) + a[2] * math.sin(ang), 0, -a[0] * math.sin(ang) + a[2] * math.cos(ang))
        tables[c] = [k(0, a), k(t0, a), k(t0 + 2, ((a[0] + rot[0]) / 2, 2.5, (a[2] + rot[2]) / 2), 'easeOutCubic'),
                     k(t0 + 4, rot, 'easeInQuad')]
        if t0 == 0:
            tables[c] += [k(8, a, 'easeInOutSine')]
        else:
            tables[c] = [k(0, a), k(4, a), k(6, ((a[0] + rot[0]) / 2, 2.5, (a[2] + rot[2]) / 2), 'easeOutCubic'),
                         k(8, rot, 'easeInQuad')]
    return {'length': 8, 'loop': 'loop', 'feet': keyed(tables), 'bones': merge(
        body([k(0, P()), k(2, P(y=0.3), 'easeOutQuad'), k(4, P(y=-0.4), 'easeInQuad'), k(6, P(y=0.3), 'easeOutQuad'),
              k(8, P(), 'easeInQuad')], const(R(y=3 * s))),
        key_turn([k(0, [0]), k(8, [90 * s])])),
        'effects': [(0, 'timeline', 'step:light', None), (4, 'timeline', 'step:light', None)]}


# ---- walls ------------------------------------------------------------------------------------------------------------
CLIMB_N, CLIMB_STANCE = 8, 4
CLIMB_REACH = V_CLIMB * CLIMB_STANCE / 2.0     # 6.4 px either side
WALL_FEET = {'fl': (-8.0, 15.0), 'fr': (8.0, 15.0), 'l2': (-13.0, 10.0), 'r2': (13.0, 10.0)}   # (x, mid height) on the wall
CLIMB_A = ('fl', 'r2')
CLIMB_PITCH = 3.0              # kept small: the jar is drawn upright at its seat


def climb():
    """Loop while it goes up a wall (0.2 blocks a tick, HostControl.java:49-53; the body stays upright, because the
    jar is drawn upright at its seat): nose up only 3 degrees (the legs tell it), the four front points walking up the wall's
    face in two pairs, the four rear legs scrabbling at it below in the counter-beat; the key whirring."""
    half = CLIMB_N // 2

    def front(c, t):
        x, ym = WALL_FEET[c]
        s = (t - (0 if c in CLIMB_A else half)) % CLIMB_N
        if s <= CLIMB_STANCE:
            return (x, ym + CLIMB_REACH - V_CLIMB * s, WALL)
        u = (s - CLIMB_STANCE) / float(CLIMB_N - CLIMB_STANCE)
        yy = ym - CLIMB_REACH + 2 * CLIMB_REACH * (1 - math.cos(math.pi * u)) / 2
        return (x, yy, WALL + 3.0 * math.sin(math.pi * u))

    def rear(c, t):
        a = rest_rel(c, 0.9)
        ph = 0 if c in ('l3', 'rr') else half
        w = 2 * math.pi * (t - ph) / CLIMB_N
        return (a[0], a[1] + 3.5 + 1.5 * math.sin(w), a[2] - 4.0 + 3.0 * math.cos(w), 'rel')

    feet = lambda c, t: front(c, t) if c in FRONT else rear(c, t)
    return {'length': CLIMB_N, 'loop': 'loop', 'feet': feet, 'bones': merge(
        body([k(t, P(y=round(0.4 * math.cos(2 * math.pi * t / half), 3))) for t in range(CLIMB_N + 1)],
             const(R(x=CLIMB_PITCH))),
        {'sensor': {'rotation': const(R(x=8))}},
        key_turn([k(0, [0]), k(CLIMB_N, [180])])),
        'effects': [(0, 'timeline', 'step:wall', None), (half, 'timeline', 'step:wall', None)]}


def climb_top():
    """Once, as it comes over the top of a wall onto its edge: the front points hook down onto the new ground ahead
    (2), it hauls up and over, pitching forward (wind-up 10 degrees back, then -4 past level), the rear legs come up
    from the scrabble one pair at a time and plant (8, 10), and it settles level on 14."""
    tables = {}
    for c in FRONT:
        x, ym = WALL_FEET[c]
        tables[c] = [k(0, (x, ym, WALL)), k(2, (G.FEET[c][0], 0, G.FEET[c][1] - 4), 'easeOutCubic'),
                     k(9, FOOT0[c], 'easeInOutSine')]
    for c in REAR:
        t0 = 8 if c in ('l3', 'r3') else 10
        a = rest_rel(c, 0.9)
        tables[c] = [k(0, (a[0], a[1] + 3.5, a[2] - 4.0)), k(t0 - 4, (a[0], a[1] + 4, a[2] - 2))]
    g = keyed({c: tables[c] for c in FRONT})
    rel = {c: sorted(tables[c], key=lambda x: x[0]) for c in REAR}

    def feet(c, t):
        if c in FRONT:
            return g(c, t)
        t0 = 8 if c in ('l3', 'r3') else 10
        if t < t0:
            return tuple(value_at(rel[c], t)) + ('rel',)
        return FOOT0[c]
    return {'length': 14, 'loop': 'once', 'feet': feet, 'bones': merge(
        body([k(0, P(z=-1)), k(2, P(z=-1, y=-0.5), 'easeOutQuad'), k(6, P(y=1.5, z=-2), 'easeInOutCubic'),
              k(10, P(y=-0.6), 'easeInOutSine'), k(14, P(), 'easeInElastic', [3])],
             [k(0, R(x=CLIMB_PITCH)), k(2, R(x=CLIMB_PITCH + 3), 'easeOutQuad'), k(7, R(x=-4), 'easeInOutCubic'),
              k(14, R(), 'easeInElastic', [3])]),
        {'sensor': {'rotation': [k(0, R(x=8)), k(7, R(), 'easeInOutSine')]}}),
        'effects': [(2, 'sound', snd('leg_hook', 0.4, 1.0), None), (6, 'sound', snd('tin_scrape', 0.4, 1.0), None),
                    (8, 'timeline', 'step:light', None), (10, 'timeline', 'step:light', None)]}


# ---- air ----------------------------------------------------------------------------------------------------------------
def jump():
    """A crouch on all eight (3 ticks; the impulse on 3), the legs shove down as it leaves, then draw in."""
    tables = {c: [k(0, FOOT0[c]), k(3, FOOT0[c])] for c in LEGS}
    rel = {c: [k(3, rest_rel(c, 1.0, -2.5)), k(5, rest_rel(c, 0.8, 2.0), 'easeInExpo'),
               k(10, rest_rel(c, 0.75, -3.0), 'easeOutCubic')] for c in LEGS}
    g, r = keyed(tables), keyed(rel, LEGS)
    return {'length': 10, 'loop': 'hold', 'feet': lambda c, t: g(c, t) if t <= 3 else r(c, t), 'bones': body(
        [k(0, P()), k(1, P(y=0.4), 'easeOutQuad'), k(3, P(y=-2.5), 'easeOutQuad'), k(4, P(y=1), 'easeInExpo'), k(10, P(y=0.5))],
        [k(0, R()), k(3, R(x=-2), 'easeOutQuad'), k(6, R(x=2), 'easeOutCubic'), k(10, R(), 'easeInOutSine')]),
        'effects': [(3, 'timeline', 'jump:push', None)]}


def fall():
    """Loop in a fall (it takes no fall damage): all eight legs flung out wide like a dropped spider, their points
    fluttering, the claws pressed tight, the eyes up."""
    rel = {c: [k(0, rest_rel(c, 1.35, -5.0)),
               k(3 + (2 if c in GROUP_B else 0), tuple(v + (1.2 if i == 1 else 0) for i, v in enumerate(rest_rel(c, 1.4, -5.0))), 'easeInOutSine'),
               k(12, rest_rel(c, 1.35, -5.0), 'easeInOutSine')] for c in LEGS}
    return {'length': 12, 'loop': 'loop', 'feet': keyed(rel, LEGS), 'bones': merge(
        claws([k(0, [-3])]), {'sensor': {'rotation': const(R(x=10))}},
        key_turn([k(0, [0]), k(12, [180])])), 'effects': []}


def land():
    """It lands on all eight, the points splaying 2 px, the tin dropping 3 px onto them (contact 0), and springs back
    with a bounce: no damage, a tinny boing."""
    tables = {c: [k(0, FOOT0[c]), k(2, foot(c, 1.12), 'easeOutQuad'), k(10, FOOT0[c], 'easeInOutSine')] for c in LEGS}
    return {'length': 12, 'loop': 'once', 'feet': keyed(tables), 'bones': merge(
        body([k(0, P()), k(2, P(y=-2.5), 'easeOutCubic'), k(3, P(y=-2.5)), k(11, P(), 'easeInBounce', [0.4])],
             [k(0, R()), k(2, R(x=-2), 'easeOutCubic'), k(9, R(), 'easeInOutSine')]),
        {'jar_anchor': {'position': [k(0, P()), k(3, P(y=-0.4), 'easeOutQuad'), k(5, P(), 'easeInOutSine')]}},
        claws([k(0, [0]), k(3, [3], 'easeOutQuad'), k(7, [0], 'easeInElastic', [3])])),
        'effects': [(0, 'timeline', 'land:light', None), (2, 'sound', snd('tin_boing', 0.6, 1.1), None)]}


# ---- the web ------------------------------------------------------------------------------------------------------------
def rear_up(fire, length, after):
    """The web's posture: it rears on its back six, the front pair raised high and wide, the second pair half up;
    the tin pitches nose-up 14 degrees and the nozzle points at the target (the code adds its aim on the launcher)."""
    raised = {'fl': (-6.0, 14.0, -6.0), 'fr': (6.0, 14.0, -6.0), 'l2': (-11.0, 5.0, -5.0), 'r2': (11.0, 5.0, -5.0)}
    tables = {}
    for c in LEGS:
        if c in raised:
            a = rest_rel(c)
            tables[c] = [k(0, a), k(1, (a[0], a[1] + 1.4, a[2] + 0.5), 'easeOutQuad'), k(fire - 1, raised[c], 'easeOutCubic')]
            tables[c] += after(c, a, raised[c])
        else:
            tables[c] = [k(0, FOOT0[c])]
    return keyed({c: tables[c] for c in FRONT}, FRONT), (lambda c, t: FOOT0[c])


def web():
    """The ability (range 16, cooldown 60). Wind-up: a 1-tick dip, then it rears (3 ticks); FIRE on 4 (the strand
    leaves the nozzle: see the notes on when the web block should appear): the nozzle kicks 1.5 px back, the tin jolts
    back 1 px and 3 more degrees, a puff; it holds, then comes down on its front points (contact 12) with a bounce
    and the tin settles on an elastic."""
    def after(c, a, up):
        return [k(8, up), k(12, a, 'easeInQuad')]
    front, rear = rear_up(4, 18, after)
    feet = lambda c, t: front(c, t) if c in FRONT and t < 12 else rear(c, t)
    return {'length': 18, 'loop': 'hold', 'feet': feet, 'bones': merge(
        body([k(0, P()), k(1, P(y=-1), 'easeOutQuad'), k(3, P(y=2.5, z=1), 'easeOutCubic'), k(4, P(y=2.5, z=1)),
              k(5, P(y=2.2, z=2.5), 'easeInExpo'), k(8, P(y=2.5, z=1.5), 'easeInOutSine'), k(12, P(y=-1.2), 'easeInQuad'),
              k(14, P(y=0.4), 'easeOutQuad'), k(17, P(), 'easeInOutSine')],
             [k(0, R()), k(1, R(x=-1.5), 'easeOutQuad'), k(3, R(x=5), 'easeOutCubic'), k(4, R(x=5)),
              k(5, R(x=6.5), 'easeInExpo'), k(8, R(x=5), 'easeInOutSine'), k(12, R(x=-1.5), 'easeInQuad'),
              k(14, R(x=0.5), 'easeOutQuad'), k(17, R(), 'easeInOutSine')]),
        {'launcher': {'position': [k(0, P()), k(4, P()), k(5, P(z=2.5), 'easeInExpo'), k(10, P(), 'easeInElastic', [3])]},
         'sensor': {'rotation': [k(0, R()), k(3, R(x=-6), 'easeOutCubic'), k(9, R(x=-6)), k(13, R(), 'easeInOutSine')]}},
        lamps([(0, 1), (4, 0), (5, 1)], None)),
        'effects': [(1, 'sound', snd('tin_creak', 0.3, 1.2), None), (3, 'sound', snd('gland_squeeze', 0.4, 1.0), None),
                    (4, 'timeline', 'web:spit', None), (5, 'particle', 'web_strand', 'fx_nozzle'),
                    (12, 'timeline', 'step:light', None), (14, 'sound', snd('tin_rattle', 0.3, 1.0), None)]}


def web_dud():
    """The web refused (no target, or something in the way: host.web_blocked / no_target): it rears and squeezes,
    but the nozzle only coughs a dry puff (4). It looks down its own nose at the nozzle, taps it twice with its
    front-left point, and comes down."""
    def after(c, a, up):
        if c == 'fl':
            tap = (7.0, -2.5, -4.5)
            return [k(5, up), k(7, (up[0] - 1.0, up[1] + 2.0, up[2] + 2.0), 'easeOutQuad'), k(8, tap, 'easeInExpo'), k(9, (5.5, 0.0, -5.0), 'easeOutQuad'), k(10, tap, 'easeInQuad'),
                    k(11, (5.5, 0.0, -5.0), 'easeOutQuad'), k(12, tap, 'easeInQuad'), k(14, up, 'easeInOutSine'),
                    k(18, a, 'easeInQuad')]
        return [k(14, up), k(18, a, 'easeInQuad')]
    front, rear = rear_up(4, 22, after)
    feet = lambda c, t: front(c, t) if c in FRONT and t < 18 else rear(c, t)
    return {'length': 22, 'loop': 'once', 'feet': feet, 'bones': merge(
        body([k(0, P()), k(1, P(y=-1), 'easeOutQuad'), k(3, P(y=2.5, z=1), 'easeOutCubic'), k(14, P(y=2, z=1)),
              k(18, P(y=-0.8), 'easeInQuad'), k(20, P(y=0.3), 'easeOutQuad'), k(22, P(), 'easeInOutSine')],
             [k(0, R()), k(1, R(x=-1.5), 'easeOutQuad'), k(3, R(x=5), 'easeOutCubic'), k(5, R(x=5)),
              k(7, R(x=2), 'easeInOutSine'), k(14, R(x=2)), k(18, R(x=-1), 'easeInQuad'), k(22, R(), 'easeInOutSine')]),
        {'launcher': {'position': [k(0, P()), k(4, P()), k(5, P(z=0.5), 'easeOutQuad'), k(7, P(), 'easeInOutSine'),
                                   k(10, P(z=0.4), 'easeOutQuad'), k(11, P()), k(12, P(z=0.4), 'easeOutQuad'), k(13, P())]},
         'sensor': {'rotation': [k(0, R()), k(3, R(x=-6), 'easeOutCubic'), k(5, R(x=-6)), k(7, R(x=-25), 'easeInOutCubic'),
                                 k(14, R(x=-25)), k(17, R(y=6), 'easeInOutSine'), k(21, R(), 'easeInOutSine')]}}),
        'effects': [(3, 'sound', snd('gland_squeeze', 0.4, 1.0), None), (4, 'sound', snd('nozzle_cough', 0.5, 1.0), None),
                    (5, 'particle', 'dry_puff', 'fx_nozzle'), (8, 'sound', snd('nozzle_tap', 0.3, 1.1), None),
                    (10, 'sound', snd('nozzle_tap', 0.3, 1.1), None), (12, 'sound', snd('nozzle_tap', 0.3, 1.0), None),
                    (18, 'timeline', 'step:light', None)]}


# ==================================================================================================================
# States
# ==================================================================================================================
def hurt():
    """Hit: it flinches down and away from the front-left (mirror by side), the claws rattle, a spark."""
    return {'length': 10, 'loop': 'once', 'feet': static(lambda c: FOOT0[c]), 'bones': merge(
        body([k(0, P()), k(1, P(y=-2, x=1), 'easeOutExpo'), k(3, P(y=-2, x=1)), k(7, P(y=0.3), 'easeInOutSine'),
              k(10, P(), 'easeInOutSine')],
             [k(0, R()), k(1, R(x=-3, z=-7), 'easeOutExpo'), k(3, R(x=-3, z=-7)), k(7, R(x=0.5, z=1), 'easeInOutSine'),
              k(10, R(), 'easeInOutSine')]),
        claws([k(0, [0]), k(1, [4], 'easeOutQuad'), k(3, [0], 'easeInQuad'), k(4, [2], 'easeOutQuad'), k(6, [0], 'easeInQuad')]),
        lamps([(0, 1), (1, 0), (3, 1)], None)),
        'effects': [(1, 'particle', 'electric_spark', 'fx_hull'), (3, 'sound', snd('claw_rattle', 0.4, 1.1), None)]}


def limp():
    """Loop, the jar's charge nearly gone (built at x 0.25, playback = measured / 0.085 blocks a tick): a crawl in
    four pairs, the front-left leg dragged straight with no lift, the tin sagging 2 px onto that corner (a hitch every
    second stride), the eyes dim (one out), and the
    key running down: a turn, a stop, a jerk, a stop."""
    n, stance = 16, 12
    reach = V_LIMP * stance / 2.0
    ph = {'fl': 0, 'r3': 0, 'fr': 4, 'l3': 4, 'l2': 8, 'rr': 8, 'r2': 12, 'rl': 12}

    def feet(c, t):
        return step_foot(FOOT0[c], t - ph[c], n, stance, reach, 0.0 if c == 'fl' else 2.5, V_LIMP)
    return {'length': n, 'loop': 'loop', 'feet': feet, 'bones': merge(
        body([k(t, P(y=-1.5 + (-0.4 if t in (1, 9) else 0) + (-0.3 if t == 9 else 0))) for t in range(n + 1)], const(R(x=-4, z=4))),
        {'sensor': {'rotation': const(R(x=-12))}},
        key_turn([k(0, [0]), k(3, [40], 'easeOutQuad'), k(8, [40]), k(9, [50], 'easeOutQuad'), k(16, [50])]),
        lamps([(0, 0)], [(0, 1)])),
        'effects': [(0, 'timeline', 'step:light', None), (1, 'sound', snd('point_drag', 0.25, 0.9), None),
                    (8, 'timeline', 'step:light', None), (9, 'sound', snd('key_tick', 0.2, 0.7), None),
                    (12, 'timeline', 'step:light', None)]}


def destroyed():
    """Collapse (pilotless; the jar is out first): sparks, the legs buckle and the tin drops 3 px (4-8); the claws
    spring wide open (9); it kicks itself over and lands on its back (rolled 180 degrees, contact 20,
    destroyed:thud) with a bounce, and dies like a spider: the eight legs curl up and in over its belly, rear to
    front, and one twitches; the key spins on, runs down and stops; smoke."""
    tables, rel = {}, {}
    for c in LEGS:
        a = FOOT0[c]
        tables[c] = [k(0, a), k(8, a)]
    order = ['rr', 'rl', 'r3', 'l3', 'r2', 'l2', 'fr', 'fl']
    for i, c in enumerate(order):
        a8 = rest_rel(c, 1.0, -3.0)
        t0 = 20 + i
        hx, hz = G.HIPS[c]
        curl = (-hx * 0.3, -14.0, -hz * 0.3)
        tuck = (a8[0] * 0.5, -5.0, a8[2] * 0.5)
        rel[c] = [k(10, a8), k(12, (a8[0] * 1.15, a8[1] - 1, a8[2] * 1.15), 'easeOutQuad'), k(16, tuck, 'easeInOutCubic'),
                  k(t0, tuck), k(t0 + 6, curl, 'easeInOutCubic')]
        if c == 'l2':
            rel[c] += [k(34, (curl[0] - 2, curl[1] + 3, curl[2]), 'easeOutQuad'), k(36, curl, 'easeInQuad')]
    g, r = keyed(tables), keyed(rel, LEGS)
    return {'length': 40, 'loop': 'hold', 'pilot': False, 'feet': lambda c, t: g(c, t) if t <= 9 else r(c, t),
            'bones': merge(
                body([k(0, P()), k(4, P()), k(8, P(y=-3), 'easeInQuad'), k(10, P(y=-3.5), 'easeOutQuad'),
                      k(15, P(y=8, x=-1), 'easeOutCubic'), k(20, P(y=-1, x=-2), 'easeInQuad'),
                      k(23, P(y=0.5, x=-2), 'easeOutQuad'), k(26, P(y=-1, x=-2), 'easeInBounce', [0.3])],
                     [k(0, R()), k(4, R(z=3), 'easeInQuad'), k(8, R(x=-2, z=-2), 'easeInQuad'), k(10, R(x=-2, z=-4)),
                      k(20, R(x=0, z=180), 'easeInQuad'), k(26, R(x=2, z=180), 'easeInBounce', [0.3])]),
                claws([k(0, [0]), k(8, [0]), k(9, [50], 'easeOutExpo'), k(12, [44], 'easeInOutSine'), k(18, [8], 'easeInQuad')]),
                key_turn([k(0, [0]), k(10, [360], 'easeInQuad'), k(24, [900], 'linear'), k(32, [1000], 'easeOutQuad'),
                          k(34, [990], 'easeInOutSine')]),
                lamps([(0, 1), (3, 0), (5, 1), (9, 0), (12, 1), (13, 0)], [(0, 1), (20, 0)])),
            'effects': [(0, 'timeline', 'fx:sparks:fx_hull', None), (6, 'particle', 'electric_spark', 'fx_eye'),
                        (9, 'sound', snd('spring_twang', 0.6, 1.0), None), (11, 'sound', snd('tin_clatter', 0.5, 1.0), None),
                        (20, 'timeline', 'destroyed:thud', None), (28, 'sound', snd('leg_curl', 0.4, 1.0), None),
                        (32, 'sound', snd('key_rundown', 0.5, 0.8), None), (35, 'particle', 'large_smoke', 'fx_belly')]}


def clips():
    return [('parked', parked()), ('board', board()), ('get_off', get_off()), ('idle', idle()), ('idle_tic', idle_tic()),
            ('walk', walk()), ('walk_back', walk(back=True)), ('turn_l', turn('l')), ('turn_r', turn('r')),
            ('climb', climb()), ('climb_top', climb_top()), ('jump', jump()), ('fall', fall()), ('land', land()),
            ('web', web()), ('web_dud', web_dud()), ('hurt', hurt()), ('limp', limp()), ('destroyed', destroyed())]


def build_anims(model, baked=False):
    groups = {g['name']: g['uuid'] for g in model['groups']}
    anims = []
    for name, clip in clips():
        clip = copy.deepcopy(clip)
        solve_legs(clip)
        animators = {}
        for bone, chans in clip['bones'].items():
            if bone not in groups:
                sys.exit('%s: no bone %s' % (name, bone))
            kfs = []
            for channel, keys in chans.items():
                keys = sorted(keys, key=lambda x: x[0])
                times = [x[0] for x in keys]
                if len(set(times)) != len(times):
                    sys.exit('%s/%s/%s: two keys on one tick' % (name, bone, channel))
                if baked:
                    keys = bake(keys)
                for (t, v, e, a, interp) in keys:
                    if t > clip['length'] or t < 0:
                        sys.exit('%s/%s: key out of range at %s' % (name, bone, t))
                    kfs.append(keyframe(channel, t, v, e, a, interp))
            animators[groups[bone]] = {'name': bone, 'type': 'bone', 'keyframes': kfs}
        effects, seen = [], set()
        for (t, channel, effect, locator) in sorted(clip['effects'], key=lambda x: x[0]):
            if t in seen:
                sys.exit('%s: two effects on tick %s' % (name, t))
            if t > clip['length']:
                sys.exit('%s: effect out of range at %s' % (name, t))
            seen.add(t)
            dp = {'script': effect} if channel == 'timeline' else dict({'effect': effect, 'file': ''},
                                                                         **({'locator': locator} if locator else {}))
            effects.append({'channel': channel, 'data_points': [dp], 'uuid': nid(), 'time': round(t / T, 4),
                            'color': -1, 'interpolation': 'linear'})
        if baked and 'standin_jar' in groups:
            vis = None
            if clip.get('pilot') is False:
                vis = [keyframe('scale', 0, S(0), None, None, 'step')]
            elif 'pilot_until' in clip:
                vis = [keyframe('scale', 0, S(1), None, None, 'step'),
                       keyframe('scale', clip['pilot_until'] + 2, S(0), None, None, 'step')]
            if vis:
                animators[groups['standin_jar']] = {'name': 'standin_jar', 'type': 'bone', 'keyframes': vis}
        if effects:
            animators['effects'] = {'name': 'Effects', 'type': 'effect', 'keyframes': effects}
        anims.append({'uuid': nid(), 'name': name, 'loop': clip['loop'], 'override': False,
                      'length': clip['length'] / T, 'snapping': 20, 'selected': False, 'animators': animators})
    return anims


def main():
    path = sys.argv[sys.argv.index('--model') + 1] if '--model' in sys.argv else MODEL
    with open(path, encoding='utf-8') as f:
        model = json.load(f)
    model['animations'] = build_anims(model)
    if '--model' not in sys.argv:
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(model, f, indent=1)
    if '--preview' in sys.argv:
        out = sys.argv[sys.argv.index('--preview') + 1]
        prev = copy.deepcopy(model)
        prev['animations'] = build_anims(model, baked=True)
        with open(out, 'w', encoding='utf-8') as f:
            json.dump(prev, f, indent=1)
    print('wrote %d clips' % len(model['animations']))


if __name__ == '__main__':
    main()

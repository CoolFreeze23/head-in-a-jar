"""Skitter ("multi-leg" skeleton): every clip, keyed in ticks, written into skitter.bbmodel.

Source of truth for the clips; hand edits to the bbmodel's animations are overwritten. Rig numbers come from
skitter_geo.py; the keyframe helpers and GeckoLib 4.9.3 easing curves are the Hopper's (hopper_anim.py), imported.

    python art/mecha/skitter/skitter_anim.py                       # write the clips into the bbmodel
    python art/mecha/skitter/skitter_anim.py --model X --preview OUT   # baked preview copy (stand-in jar hidden when
                                                                       # nobody is aboard; the reel's code spin baked)

Legs are never keyed by hand. Each clip gives foot targets (on the ground in model space, or relative to the hip in the
air); every tick the generator evaluates the body's eased position and solves each leg: the hip's yaw toward the foot,
then the two-bone femur and tibia in that vertical plane with the knee up. A planted pad stays planted.

Signs (Blockbench, as displayed): body rot x + = nose up, rot z + = left side down; restraint position y + = the cage
lifted, rot x + = the cage flipped up and back over its rear edge; clamp_l rot z + / clamp_r rot z - = pads pressed
in; reel rot x + = winding in; launcher rot x + = aim up, position z + = recoil; arm_l rot x + = mandible raised;
grip_l rot x + = jaw open; vent_* (written as rot x +) = flank flap open; hip_* rot y = leg swung, leg_* / shin_* rot x + = raised.
"""
import copy
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(os.path.dirname(HERE), 'hopper'))
import skitter_geo as G  # noqa: E402
import hopper_anim as HA  # noqa: E402
from hopper_anim import k, R, P, S, lamp, const, value_at, keyframe, bake, snd, nid, thin  # noqa: E402,F401

MODEL = os.path.join(HERE, 'skitter.bbmodel')
T = 20.0
V = 0.12 * 2.16 * 16           # 4.15 px a tick (0.2592 blocks): design speed
V_BACK = V * 0.4               # backing: MechaLogic.riddenInput x 0.4
V_LIMP = V * 0.3
CORNERS = ['fl', 'fr', 'rl', 'rr']
SIGN = {c: G.CORNERS[c] for c in CORNERS}
FOOT0 = {c: (SIGN[c][0] * G.FOOT, 0.0, SIGN[c][1] * G.FOOT) for c in CORNERS}
PARK_Y = -5.0
FOOT_LIFT = 0.6               # the pad's contact point sits this far above its tilted bottom corner
PARK_FOOT = 14.0


def park_foot(c):
    return (SIGN[c][0] * PARK_FOOT, 0.0, SIGN[c][1] * PARK_FOOT)


def wide_foot(c, w=22.0):
    return (SIGN[c][0] * w, 0.0, SIGN[c][1] * w)


# ------------------------------------------------------------------------------------------------------------------
# Leg solver
# ------------------------------------------------------------------------------------------------------------------
def rot3(v, x, y, z, inverse=False):
    """Rotate v by Rz(z) Ry(y) Rx(x) (x applied first); with inverse=True and negated angles, undo it (z first)."""
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
    """feet(c, t) -> (x, y, z) in model space on the ground, or (dx, dy, dz, 'rel') relative to the hip."""
    feet = clip.get('feet')
    if feet is None:
        return
    body = clip['bones'].get('body', {})
    bpos = sorted(body.get('position', [k(0, P())]), key=lambda x: x[0])
    brot = sorted(body.get('rotation', [k(0, R())]), key=lambda x: x[0])
    out = {c: {'hip': [], 'leg': [], 'shin': []} for c in CORNERS}
    for t in range(0, int(clip.get('ik_until', clip['length'])) + 1):
        b = value_at(bpos, t)
        rx, ry, rz = [math.radians(a) for a in value_at(brot, t)]
        for c in CORNERS:
            # the hip in the world, the body's pitch and roll included (Blockbench order: x, then y, then z)
            off = rot3((SIGN[c][0] * G.HIP, 0.0, SIGN[c][1] * G.HIP), rx, ry, rz)
            hx, hy, hz = off[0] + b[0], G.BODY_Y + off[1] + b[1], off[2] + b[2]
            f = feet(c, t)
            if len(f) == 4:
                dx, dy, dz = f[0], f[1], f[2]
            else:
                dx, dy, dz = f[0] - hx, f[1] + FOOT_LIFT - hy, f[2] - hz
                # into the body's own frame, where the leg's bones are posed
                dx, dy, dz = rot3((dx, dy, dz), -rx, -ry, -rz, inverse=True)
            r = math.hypot(dx, dz)
            yaw = math.degrees(math.atan2(-dx, -dz)) - G.REST_YAW[c]
            yaw = (yaw + 180) % 360 - 180
            a1, a2 = G.leg_ik(r, dy)
            out[c]['hip'].append((t, yaw))
            out[c]['leg'].append((t, a1 - G.REST_FEMUR))
            out[c]['shin'].append((t, (a2 - a1) - G.REST_TIBIA))
    for c in CORNERS:
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


def keyed(tables):
    """Per-corner foot key tables -> a feet function (values (x, y, z) or (dx, dy, dz) with rel=True on the clip)."""
    srt = {c: sorted(v, key=lambda x: x[0]) for c, v in tables.items()}
    return lambda c, t: tuple(value_at(srt[c], t))


def keyed_rel(tables):
    srt = {c: sorted(v, key=lambda x: x[0]) for c, v in tables.items()}
    return lambda c, t: tuple(value_at(srt[c], t)) + ('rel',)


def vents_to_z(clip):
    """Flank flaps hinge along z: written as +x (opening), stored as z (left -, right +)."""
    for side, sign in (('vent_l', -1), ('vent_r', 1)):
        ch = clip['bones'].get(side)
        if ch and 'rotation' in ch:
            ch['rotation'] = [(t, [0, 0, sign * v[0]], e, a, i) for (t, v, e, a, i) in ch['rotation']]


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


def clamps(keys):
    """Pads on the cage's sides: degrees pressed in (negative = swung open)."""
    return {'clamp_l': {'rotation': [(t, [0, 0, o[0]], e, a, i) for (t, o, e, a, i) in keys]},
            'clamp_r': {'rotation': [(t, [0, 0, -o[0]], e, a, i) for (t, o, e, a, i) in keys]}}


def step_foot(base, s, n, stance, reach, lift, v):
    """One foot through a cycle: s = phase tick; stance: planted, moving back under the body at v; swing: forward,
    lifted, with a fast rise and a falling plant."""
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
    """Unpiloted: sat down on its belly, the legs folded high round it like a resting spider's, the head bowed, the
    mandible tucked, the cage down over the empty collar, lamps off. Alive: a slow settle, a foot that shifts once,
    the eyes' standby blip."""
    feet = keyed({c: [k(0, park_foot(c))] for c in CORNERS})
    feet_fn = lambda c, t: (feet(c, t) if not (c == 'rl' and 50 <= t <= 56) else
                            (park_foot(c)[0], 1.5 * math.sin(math.pi * (t - 50) / 6), park_foot(c)[2] + 0.5 * (t - 50) / 6))
    return {'length': 80, 'loop': 'loop', 'pilot': False, 'feet': feet_fn, 'bones': merge(
        body([k(0, P(y=PARK_Y)), k(40, P(y=PARK_Y - 0.3), 'easeInOutSine'), k(80, P(y=PARK_Y), 'easeInOutSine')],
             [k(0, R(x=-1)), k(44, R(x=-1.3), 'easeInOutSine'), k(80, R(x=-1), 'easeInOutSine')]),
        {'sensor': {'rotation': [k(0, R(x=-18)), k(40, R(x=-18, y=4), 'easeInOutSine'), k(80, R(x=-18), 'easeInOutSine')]},
         'arm_l': {'rotation': const(R(x=12))}, 'restraint': {'position': const(P())}},
        clamps([k(0, [0])]), lamps([(0, 0), (1, 1), (3, 0)], [(0, 0)])),
        'effects': [(1, 'sound', snd('standby_blip', 0.15, 1.4), None), (50, 'sound', snd('leg_tick', 0.15, 1.0), None)]}


def board():
    """Controller "cockpit": the clamps swing open, the cage lifts straight up 20 px on its own, the jar slides in
    under it from behind (where the pilot comes from) and is seated on 11; the cage drops back onto the collar
    (contact 17) and the clamps close on the glass (19)."""
    b = merge(
        {'jar_anchor': {'position': [k(0, P(y=3, z=18)), k(5, P(y=3, z=18)), k(9, P(y=3), 'easeInOutSine'),
                                     k(11, P(), 'easeInQuad'), k(12, P(y=0.4), 'easeOutQuad'), k(13, P(), 'easeInQuad')]},
         'restraint': {'position': [k(0, P()), k(1, P(y=-0.5), 'easeOutQuad'), k(6, P(y=21), 'easeOutCubic'),
                                    k(8, P(y=20), 'easeInOutSine'), k(13, P(y=20)), k(14, P(y=21), 'easeOutQuad'),
                                    k(17, P(), 'easeInQuad'), k(18, P(y=0.8), 'easeOutQuad'), k(20, P(), 'easeInQuad')]}},
        clamps([k(0, [0]), k(2, [-28], 'easeOutCubic'), k(17, [-28]), k(19, [2], 'easeInQuad'), k(20, [-1], 'easeOutQuad'),
                k(22, [0], 'easeInQuad')]))
    return {'length': 22, 'loop': 'hold', 'bones': b,
            'effects': [(1, 'sound', snd('clamp_release', 0.5, 1.0), None), (3, 'sound', snd('cage_lift', 0.5, 1.0), None),
                        (11, 'sound', snd('jar_seat', 0.6, 1.0), None), (14, 'sound', snd('servo', 0.4, 1.0), None),
                        (17, 'sound', snd('cage_drop', 0.7, 0.9), None), (19, 'sound', snd('clamp_close', 0.6, 1.0), None),
                        (20, 'particle', 'cloud', 'fx_hull')]}


def boot():
    """Controller "cockpit": the eyes light (2) and the tail lamps (5); the head looks left, right and up; the mandible
    rises and snaps twice; the reel ticks a quarter turn; the line gun swivels up and back; the deck louvres cough."""
    b = merge(lamps([(0, 0), (2, 1)], [(0, 0), (5, 1)]), {
        'sensor': {'rotation': [k(0, R(x=-18)), k(7, R(), 'easeInOutCubic'), k(9, R(y=-3), 'easeOutQuad'),
                                k(13, R(y=30), 'easeInOutCubic'), k(15, R(y=28), 'easeInOutSine'), k(20, R(y=-30), 'easeInOutCubic'),
                                k(22, R(y=-28), 'easeInOutSine'), k(25, R(x=12), 'easeInOutCubic'), k(29, R(), 'easeInOutSine')]},
        'arm_l': {'rotation': [k(0, R(x=12)), k(10, R(x=12)), k(14, R(x=-4), 'easeInOutCubic'), k(16, R(x=-2), 'easeInOutSine'),
                               k(24, R(x=-2)), k(28, R(), 'easeInOutSine')]},
        'grip_l': {'rotation': [k(0, R()), k(16, R()), k(18, R(x=30), 'easeOutCubic'), k(19, R(), 'easeInQuad'),
                                k(21, R(x=30), 'easeOutCubic'), k(22, R(), 'easeInQuad')]},
        'reel': {'rotation': [k(0, R()), k(26, R()), k(28, R(x=95), 'easeOutCubic'), k(30, R(x=90), 'easeInOutSine')]},
        'launcher': {'rotation': [k(0, R()), k(29, R()), k(32, R(x=20), 'easeInOutCubic'), k(34, R(x=18), 'easeInOutSine'),
                                  k(37, R(), 'easeInOutCubic')]},
        'vent_l': {'rotation': [k(0, R()), k(34, R()), k(35, R(x=30), 'easeOutQuad'), k(38, R(), 'easeInQuad')]},
        'vent_r': {'rotation': [k(0, R()), k(35, R()), k(36, R(x=30), 'easeOutQuad'), k(39, R(), 'easeInQuad')]}})
    return {'length': 40, 'loop': 'hold', 'bones': b,
            'effects': [(0, 'sound', snd('servo_chord', 0.7, 0.9), None), (2, 'sound', snd('relay_click', 0.4, 1.0), None),
                        (5, 'sound', snd('relay_click', 0.4, 1.1), None), (13, 'sound', snd('servo_tick', 0.3, 1.0), None),
                        (18, 'sound', snd('jaw_snap', 0.5, 1.1), None), (21, 'sound', snd('jaw_snap', 0.5, 1.0), None),
                        (28, 'sound', snd('reel_tick', 0.4, 1.0), None), (32, 'sound', snd('servo', 0.4, 1.1), None),
                        (35, 'particle', 'cloud', 'fx_vent_l'), (36, 'particle', 'cloud', 'fx_vent_r'),
                        (39, 'timeline', 'boot:done', None)]}


RIPPLE = [('fl', 18), ('fr', 22), ('rl', 26), ('rr', 30)]


def boot_legs():
    """Controller "main" while still: it rises off its belly on its folded legs (one long groan), then all four legs
    step out to their stance one by one, front left to rear right, 4 ticks apart: the ripple."""
    tables = {}
    for c, t0 in RIPPLE:
        a, b = park_foot(c), FOOT0[c]
        tables[c] = [k(0, a), k(t0, a), k(t0 + 2, ((a[0] + b[0]) / 2, 4, (a[2] + b[2]) / 2), 'easeOutCubic'),
                     k(t0 + 4, b, 'easeInQuad')]
    return {'length': 40, 'loop': 'hold', 'feet': keyed(tables), 'bones': body(
        [k(0, P(y=PARK_Y)), k(3, P(y=PARK_Y - 0.4), 'easeOutQuad'), k(14, P(y=0.8), 'easeInOutCubic'), k(17, P(), 'easeInOutSine'),
         k(22, P(y=-0.4), 'easeInQuad'), k(26, P(y=-0.2)), k(30, P(y=-0.4)), k(34, P(y=-0.3)), k(38, P(), 'easeInOutSine')],
        [k(0, R(x=-1)), k(14, R(), 'easeInOutCubic')]),
        'effects': [(3, 'sound', snd('hydraulic_stand', 0.6, 1.2), None)] +
                   [(t0 + 4, 'timeline', 'step:%s' % c, None) for c, t0 in RIPPLE]}


def shutdown():
    """The legs draw in one by one (rear right first), then it sinks onto its belly; lamps out."""
    tables = {}
    for i, (c, _) in enumerate(reversed(RIPPLE)):
        t0 = 1 + 3 * i
        a, b = FOOT0[c], park_foot(c)
        tables[c] = [k(0, a), k(t0, a), k(t0 + 2, ((a[0] + b[0]) / 2, 3, (a[2] + b[2]) / 2), 'easeOutCubic'),
                     k(t0 + 3, b, 'easeInQuad')]
    return {'length': 24, 'loop': 'hold', 'feet': keyed(tables), 'bones': merge(
        body([k(0, P()), k(13, P()), k(14, P(y=0.4), 'easeOutQuad'), k(21, P(y=PARK_Y - 0.3), 'easeInQuad'),
              k(24, P(y=PARK_Y), 'easeInOutSine')], [k(0, R()), k(24, R(x=-1), 'easeInOutSine')]),
        {'sensor': {'rotation': [k(0, R()), k(10, R()), k(20, R(x=-18), 'easeInOutSine')]},
         'arm_l': {'rotation': [k(0, R()), k(8, R()), k(18, R(x=12), 'easeInOutSine')]}},
        lamps([(0, 1), (16, 0)], [(0, 1), (12, 0)])),
        'effects': [(0, 'sound', snd('power_down', 0.6, 0.9), None), (21, 'sound', snd('belly_settle', 0.5, 0.9), None)]}


def idle():
    """Standing: a 0.5 px breath, the body a beat behind on pitch, and one leg at a time re-setting its grip (lift
    1.5 px, set down 0.5 px further out), a quarter cycle apart."""
    tables = {}
    for c, t0 in (('fl', 8), ('rr', 23), ('fr', 38), ('rl', 53)):
        a = FOOT0[c]
        tables[c] = [k(0, a), k(t0, a), k(t0 + 2, (a[0], 1.5, a[2]), 'easeOutQuad'), k(t0 + 4, a, 'easeInQuad'), k(60, a)]
    return {'length': 60, 'loop': 'loop', 'feet': keyed(tables), 'bones': body(
        [k(0, P()), k(30, P(y=-0.5), 'easeInOutSine'), k(60, P(), 'easeInOutSine')],
        [k(0, R(x=0.3)), k(36, R(x=-0.5), 'easeInOutSine'), k(60, R(x=0.3), 'easeInOutSine')]),
        'effects': [(12, 'sound', snd('leg_tick', 0.15, 1.0), None), (42, 'sound', snd('leg_tick', 0.15, 0.95), None)]}


def idle_tic():
    """Every 200-300 idle ticks: it drums its feet like fingers on a desk, front left to rear right, twice."""
    order = ['fl', 'fr', 'rl', 'rr']
    tables = {c: [k(0, FOOT0[c])] for c in order}
    eff = []
    for rep, base in ((0, 4), (1, 20)):
        for i, c in enumerate(order):
            t0 = base + 3 * i
            a = FOOT0[c]
            tables[c] += [k(t0, a), k(t0 + 1, (a[0], 3, a[2]), 'easeOutQuad'), k(t0 + 2, a, 'easeInQuad')]
            eff.append((t0 + 2, 'sound', snd('foot_tap', 0.3, 1.0 + 0.05 * i - 0.05 * rep), None))
    for c in order:
        tables[c].append(k(40, FOOT0[c]))
    return {'length': 40, 'loop': 'once', 'feet': keyed(tables), 'bones': merge(
        body(None, [k(0, R()), k(4, R(z=-0.6), 'easeOutQuad'), k(16, R(z=0.6), 'easeInOutSine'), k(34, R(), 'easeInOutSine')]),
        {'sensor': {'rotation': [k(0, R()), k(4, R(z=-5), 'easeInOutSine'), k(32, R(z=-5)), k(38, R(), 'easeInOutSine')]}}),
        'effects': eff}


# ---- the trot (design speed) ------------------------------------------------------------------------------------------
WALK_N, WALK_STANCE = 8, 4          # diagonal pairs: front left with rear right, then front right with rear left
WALK_REACH = V * WALK_STANCE / 2.0  # 8.29 px either side of the rest point
PHASE = {'fl': 0, 'rr': 0, 'fr': 4, 'rl': 4}
BOB = [-1.2, -0.9, -0.4, 0.2]          # a 1.2 px drop on each plant


def walk(lower=0.0, w=G.FOOT, back=False):
    v = -V_BACK if back else V
    n = 20 if back else WALK_N
    stance = n // 2
    reach = abs(v) * stance / 2.0

    def feet(c, t):
        base = (SIGN[c][0] * w, 0.0, SIGN[c][1] * w)
        ph = (PHASE[c] * n) // WALK_N
        f = step_foot(base if not back else (base[0], 0, base[2]), t - ph, n, stance, reach, 5.0 if not back else 3.5, abs(v))
        return f if not back else (f[0], f[1], 2 * base[2] - f[2])
    step = n // 2
    bob = lambda t: BOB[int(4 * (t % step) / step)]
    bones = body([k(t, P(y=lower + bob(t))) for t in range(n + 1)],
                 [k(t, R(z=round(0.8 * math.cos(2 * math.pi * (t - 1) / n), 3), x=(-1 if lower else 0))) for t in range(n + 1)])
    bones.update({'restraint': {'position': const(P(y=-0.5 if lower else 0))}})
    bones.update(clamps([k(0, [5 if lower else 0])]))
    bones['arm_l'] = {'rotation': [k(t, R(x=round(-1.5 * math.sin(2 * math.pi * t / step), 3))) for t in range(0, n + 1, 1)]}
    return {'length': n, 'loop': 'loop', 'feet': feet, 'bones': bones,
            'effects': [(0, 'timeline', 'step:fl_rr', None), (step, 'timeline', 'step:fr_rl', None)]}


def turn(direction):
    """Turning on the spot: the diagonal pairs step in place in turn, each foot set down 4 degrees round from where it
    rose; the body leads the turn by 3 degrees."""
    s = 1 if direction == 'l' else -1
    tables = {}
    for c in CORNERS:
        a = FOOT0[c]
        t0 = 0 if PHASE[c] == 0 else 6
        ang = math.radians(4 * s)
        rot = (a[0] * math.cos(ang) + a[2] * math.sin(ang), 0, -a[0] * math.sin(ang) + a[2] * math.cos(ang))
        tables[c] = [k(0, a), k(t0, a), k(t0 + 2, ((a[0] + rot[0]) / 2, 3, (a[2] + rot[2]) / 2), 'easeOutCubic'),
                     k(t0 + 4, rot, 'easeInQuad'), k(t0 + 6, a, 'easeInOutSine'), k(12, a)]
    return {'length': 12, 'loop': 'loop', 'feet': keyed(tables), 'bones': body(
        [k(0, P()), k(2, P(y=0.3), 'easeOutQuad'), k(4, P(y=-0.5), 'easeInQuad'), k(8, P(y=0.3), 'easeOutQuad'),
         k(10, P(y=-0.5), 'easeInQuad'), k(12, P(), 'easeOutQuad')], const(R(y=3 * s))),
        'effects': [(4, 'timeline', 'step:light', None), (10, 'timeline', 'step:light', None)]}


def climb():
    """Loop: the trot on a wall (the renderer turns the root so the model's ground is the wall): the body hugs 3 px
    closer, the feet 3 px wider, the cage pulled down snug and the clamps squeezed."""
    c = walk(lower=-5.0, w=22.0)
    return c


def cling_on():
    """Once, as it takes to a wall or ceiling (or leaves the ground on the line): the clamps squeeze the glass with a
    clack (contact 3), the cage pulls down 0.5 px, the body drops 3 px and the feet spread to grip."""
    tables = {c: [k(0, FOOT0[c]), k(2, (FOOT0[c][0] * 1.08, 2, FOOT0[c][2] * 1.08), 'easeOutCubic'),
                  k(5, wide_foot(c), 'easeInQuad')] for c in CORNERS}
    return {'length': 8, 'loop': 'hold', 'feet': keyed(tables), 'bones': merge(
        body([k(0, P()), k(5, P(y=-5), 'easeInQuad'), k(6, P(y=-4.6), 'easeOutQuad'), k(8, P(y=-5), 'easeInOutSine')],
             [k(0, R()), k(5, R(x=-1), 'easeInQuad')]),
        {'restraint': {'position': [k(0, P()), k(3, P(y=-0.5), 'easeInQuad')]}},
        clamps([k(0, [0]), k(1, [-4], 'easeOutQuad'), k(3, [6], 'easeInQuad'), k(4, [4], 'easeOutQuad'), k(5, [5], 'easeInOutSine')])),
        'effects': [(3, 'sound', snd('clamp_clack', 0.6, 1.0), None), (5, 'timeline', 'step:grip', None)]}


def cling():
    """Loop, holding on a wall or ceiling: hugging low, clamps squeezed, a fine shiver, one foot at a time re-gripping
    (lift 1 px, set down)."""
    tables = {}
    for c, t0 in (('fl', 4), ('rr', 14), ('fr', 24), ('rl', 34)):
        a = wide_foot(c)
        tables[c] = [k(0, a), k(t0, a), k(t0 + 2, (a[0], 1, a[2]), 'easeOutQuad'), k(t0 + 3, a, 'easeInQuad'), k(40, a)]
    return {'length': 40, 'loop': 'loop', 'feet': keyed(tables), 'bones': merge(
        body([k(t, P(y=-5 + (0.12 if t % 2 else 0))) for t in range(0, 41)], const(R(x=-1))),
        {'restraint': {'position': const(P(y=-0.5))}}, clamps([k(0, [5])])),
        'effects': [(7, 'sound', snd('leg_tick', 0.2, 1.1), None), (27, 'sound', snd('leg_tick', 0.2, 1.0), None)]}


def jump():
    """A crouch on all four (4 ticks; the impulse on 4, applied by the pilot's client), the legs shove down as it
    leaves, then tuck."""
    tables = {c: [k(0, FOOT0[c]), k(4, FOOT0[c])] for c in CORNERS}
    rel = {c: [k(4, (SIGN[c][0] * 7, -13, SIGN[c][1] * 7)), k(6, (SIGN[c][0] * 6, -16, SIGN[c][1] * 6), 'easeInExpo'),
               k(10, (SIGN[c][0] * 6, -7, SIGN[c][1] * 6), 'easeOutCubic'), k(12, (SIGN[c][0] * 6, -7, SIGN[c][1] * 6))]
           for c in CORNERS}
    g, r = keyed(tables), keyed_rel(rel)
    return {'length': 12, 'loop': 'hold', 'feet': lambda c, t: g(c, t) if t <= 4 else r(c, t), 'bones': body(
        [k(0, P()), k(1, P(y=0.5), 'easeOutQuad'), k(4, P(y=-4), 'easeOutQuad'), k(5, P(y=1), 'easeInExpo'), k(12, P(y=0.5))],
        [k(0, R()), k(4, R(x=-3), 'easeOutQuad'), k(7, R(x=2), 'easeOutCubic'), k(12, R(), 'easeInOutSine')]),
        'effects': [(4, 'timeline', 'jump:push', None)]}


def fall():
    rel = {c: [k(0, (SIGN[c][0] * 9, -10, SIGN[c][1] * 9)), k(8, (SIGN[c][0] * 10, -11, SIGN[c][1] * 10), 'easeInOutSine'),
               k(16, (SIGN[c][0] * 9, -10, SIGN[c][1] * 9), 'easeInOutSine')] for c in CORNERS}
    return {'length': 16, 'loop': 'loop', 'feet': keyed_rel(rel), 'bones': merge(
        clamps([k(0, [5])]), {'restraint': {'position': const(P(y=-0.5))}}), 'effects': []}


def land(heavy=False):
    d, L = (5.0, 20) if heavy else (3.0, 14)
    pos = [k(0, P()), k(3, P(y=-d), 'easeOutCubic'), k(5, P(y=-d + 0.3)), k(L - 6, P(y=0.6), 'easeInOutSine'),
           k(L, P(), 'easeInOutSine')]
    b = merge(body(pos, [k(0, R()), k(3, R(x=-2 if heavy else -1), 'easeOutCubic'), k(L - 4, R(), 'easeInOutSine')]),
              {'restraint': {'position': [k(0, P()), k(2, P(y=-0.5), 'easeOutQuad'), k(5, P(y=0.4), 'easeInOutSine'),
                                          k(8, P(), 'easeInOutSine')]}})
    eff = [(0, 'timeline', 'land:heavy' if heavy else 'land:light', None)]
    if heavy:
        b['vent_l'] = {'rotation': [k(0, R()), k(2, R(x=35), 'easeOutExpo'), k(11, R(), 'easeInElastic', [4])]}
        b['vent_r'] = {'rotation': [k(0, R()), k(3, R(x=35), 'easeOutExpo'), k(12, R(), 'easeInElastic', [4])]}
        eff += [(2, 'particle', 'cloud', 'fx_vent_l'), (3, 'particle', 'cloud', 'fx_vent_r'), (11, 'sound', snd('vent_clank', 0.5, 1.0), None)]
    return {'length': L, 'loop': 'once', 'feet': static(lambda c: FOOT0[c]), 'bones': b, 'effects': eff}


# ==================================================================================================================
# The Dragline (whole-body moments that read from behind; the line itself is drawn by the game as a ribbon)
# ==================================================================================================================
def dragline_fire():
    """Drive at a block within 24. Wind-up: the line gun dips 4, then rises to its aim (the code adds the yaw and pitch
    to the anchor on top) while the body squats 2 px and pitches 6 degrees back over 4 ticks, braced on its rear legs;
    a 2-tick hold; FIRE on 9 (the server shoots the line): the gun kicks 3 px back (easeInExpo, 2 ticks), the body
    jolts 1 px and 3 degrees further back, the reel pays out fast with its hi-vis tag whirling; everything settles on
    an elastic over 6 ticks."""
    return {'length': 18, 'loop': 'hold', 'feet': static(lambda c: FOOT0[c]), 'bones': merge(
        body([k(0, P()), k(3, P(y=0.5), 'easeOutQuad'), k(7, P(y=-2, z=1), 'easeInOutCubic'), k(9, P(y=-2, z=1)),
              k(10, P(y=-3, z=2), 'easeInExpo'), k(16, P(), 'easeInElastic', [4])],
             [k(0, R()), k(3, R(x=-1), 'easeOutQuad'), k(7, R(x=6), 'easeInOutCubic'), k(9, R(x=6)),
              k(10, R(x=9), 'easeInExpo'), k(16, R(), 'easeInElastic', [4])]),
        {'launcher': {'rotation': [k(0, R()), k(2, R(x=-4), 'easeOutQuad'), k(7, R(x=22), 'easeInOutCubic'), k(9, R(x=22)),
                                   k(11, R(x=30), 'easeInExpo'), k(17, R(x=22), 'easeInElastic', [4])],
                      'position': [k(0, P()), k(9, P()), k(11, P(z=3), 'easeInExpo'), k(17, P(), 'easeInElastic', [4])]},
         'reel': {'rotation': [k(0, R()), k(9, R()), k(18, R(x=-900), 'easeOutCubic')]},
         'restraint': {'position': [k(0, P()), k(10, P()), k(11, P(y=0.6), 'easeOutQuad'), k(13, P(), 'easeInQuad')]}},
        clamps([k(0, [0]), k(6, [5], 'easeInQuad')])),
        'effects': [(2, 'sound', snd('servo', 0.4, 1.2), None), (7, 'sound', snd('line_charge', 0.5, 1.0), None),
                    (9, 'timeline', 'dragline:fire', None), (10, 'particle', 'cloud', 'fx_line'),
                    (11, 'particle', 'smoke', 'fx_tail_r'), (12, 'sound', snd('reel_payout', 0.5, 1.0), None)]}


def dragline_reel():
    """Loop, being wound in through the air (the renderer turns the root along the line): the reel winds a turn
    every 10 ticks (the tag whirls), the legs tucked 35 degrees and paddling, the gun holding its aim."""
    rel = {c: [k(0, (SIGN[c][0] * 5, -7, SIGN[c][1] * 5)), k(5, (SIGN[c][0] * 6, -8.5, SIGN[c][1] * 4), 'easeInOutSine'),
               k(10, (SIGN[c][0] * 5, -7, SIGN[c][1] * 5), 'easeInOutSine')] for c in CORNERS}
    return {'length': 10, 'loop': 'loop', 'feet': keyed_rel(rel), 'bones': merge(
        {'reel': {'rotation': [k(0, R()), k(10, R(x=360))]}, 'launcher': {'rotation': const(R(x=22))},
         'restraint': {'position': const(P(y=-0.5))}}, clamps([k(0, [5])])),
        'effects': [(0, 'sound', snd('reel_wind', 0.4, 1.0), None)]}


def dragline_swing():
    """Loop, hanging under the anchor and swinging (the renderer turns the root to the pendulum): the body trails the
    swing by up to 12 degrees, 3 ticks behind; the legs tucked and trailing further."""
    n = 30
    rel = {c: [k(t, (SIGN[c][0] * 5, -8, SIGN[c][1] * 5 + 4 * math.sin(2 * math.pi * (t - 5) / n))) for t in range(0, n + 1, 3)]
           for c in CORNERS}
    return {'length': n, 'loop': 'loop', 'feet': keyed_rel(rel), 'bones': merge(
        {'launcher': {'rotation': const(R(x=22))}, 'restraint': {'position': const(P(y=-0.5))}}, clamps([k(0, [5])]),
        body(None, [k(t, R(x=round(-12 * math.sin(2 * math.pi * (t - 3) / n), 3))) for t in range(0, n + 1, 3)])),
        'effects': [(0, 'sound', snd('line_creak', 0.25, 1.0), None)]}


def dragline_rappel():
    """Loop, lowering on the line down a drop: the legs splayed out flat and wide, feeling for the ground; the reel
    pays out with a catch of its brake (and a 0.6 px bob) every 8 ticks."""
    rel = {c: [k(0, (SIGN[c][0] * 13, -5, SIGN[c][1] * 13)), k(8, (SIGN[c][0] * 13, -6, SIGN[c][1] * 13), 'easeInOutSine'),
               k(16, (SIGN[c][0] * 13, -5, SIGN[c][1] * 13), 'easeInOutSine')] for c in CORNERS}
    return {'length': 16, 'loop': 'loop', 'feet': keyed_rel(rel), 'bones': merge(
        {'reel': {'rotation': [k(0, R()), k(16, R(x=-180))]}, 'launcher': {'rotation': const(R(x=22))},
         'restraint': {'position': const(P(y=-0.5))}}, clamps([k(0, [5])]),
        body([k(0, P()), k(7, P()), k(8, P(y=-0.6), 'easeOutQuad'), k(11, P(), 'easeInOutSine'), k(16, P())])),
        'effects': [(8, 'sound', snd('reel_brake', 0.3, 1.0), None)]}


def dragline_cut():
    """Once, Drive again: a 4-tick wind-up (the gun lifts 8 more, the body draws up), the cutter SNAPS on 4 (the server
    lets go), the gun flicks down and stows, the reel freewheels half a turn, the legs spread to catch."""
    rel = {c: [k(0, (SIGN[c][0] * 5, -8, SIGN[c][1] * 5)), k(4, (SIGN[c][0] * 4, -7, SIGN[c][1] * 4), 'easeInOutSine'),
               k(10, (SIGN[c][0] * 10, -12, SIGN[c][1] * 10), 'easeOutCubic'),
               k(16, (SIGN[c][0] * 9, -12, SIGN[c][1] * 9), 'easeInOutSine')] for c in CORNERS}
    return {'length': 16, 'loop': 'hold', 'feet': keyed_rel(rel), 'bones': merge(
        {'launcher': {'rotation': [k(0, R(x=22)), k(4, R(x=30), 'easeInOutCubic'), k(6, R(x=-15), 'easeInExpo'),
                                   k(9, R(x=-5), 'easeOutQuad'), k(14, R(), 'easeInOutSine')]},
         'reel': {'rotation': [k(0, R()), k(4, R()), k(12, R(x=-180), 'easeOutCubic')]}},
        body([k(0, P()), k(4, P(y=0.8), 'easeInOutSine'), k(6, P(y=-0.5), 'easeInExpo'), k(12, P(), 'easeInOutSine')])),
        'effects': [(4, 'timeline', 'dragline:cut', None), (6, 'sound', snd('reel_freewheel', 0.4, 1.0), None)]}


def dragline_refused():
    """Once, Drive with nothing within 24 blocks to fix to (or no power, or venting). The same wind-up as the fire, then
    on 7 the whole machine answers: it drops 1 px and rolls 4 degrees as the gun coughs (a smoke puff at the muzzle and
    one from the back), both flank flaps pop 30 degrees, the gun droops 25 degrees over 6 ticks and hangs there 10, the
    tail lamps dip once, the head tilts. The HUD says why (dragline:refused). Never nothing."""
    return {'length': 30, 'loop': 'once', 'feet': static(lambda c: FOOT0[c]), 'bones': merge(
        body([k(0, P()), k(3, P(y=0.5), 'easeOutQuad'), k(6, P(y=-2, z=1), 'easeInOutCubic'), k(7, P(y=-2, z=1)),
              k(9, P(y=-3), 'easeInQuad'), k(16, P(y=-1), 'easeInOutSine'), k(24, P(), 'easeInOutSine')],
             [k(0, R()), k(3, R(x=-1), 'easeOutQuad'), k(6, R(x=5), 'easeInOutCubic'), k(7, R(x=5)),
              k(9, R(x=-1, z=4), 'easeInQuad'), k(12, R(x=-0.5, z=3.4), 'easeOutQuad'), k(24, R(), 'easeInOutSine')]),
        {'launcher': {'rotation': [k(0, R()), k(2, R(x=-4), 'easeOutQuad'), k(6, R(x=22), 'easeInOutCubic'), k(7, R(x=22)),
                                   k(8, R(x=25), 'easeOutQuad'), k(14, R(x=-3), 'easeInQuad'), k(15, R(x=-1), 'easeOutQuad'),
                                   k(24, R(x=-3)), k(30, R(), 'easeInOutSine')]},
         'reel': {'rotation': [k(0, R()), k(7, R()), k(8, R(x=-95), 'easeOutExpo'), k(11, R(x=-90), 'easeInOutSine')]},
         'vent_l': {'rotation': [k(0, R()), k(7, R()), k(8, R(x=30), 'easeOutExpo'), k(14, R(x=8), 'easeInOutSine'), k(18, R(), 'easeInQuad')]},
         'vent_r': {'rotation': [k(0, R()), k(7, R()), k(9, R(x=30), 'easeOutExpo'), k(15, R(x=8), 'easeInOutSine'), k(19, R(), 'easeInQuad')]},
         'sensor': {'rotation': [k(0, R()), k(10, R()), k(14, R(z=14), 'easeInOutSine'), k(22, R(z=14)), k(28, R(), 'easeInOutSine')]}},
        lamps(None, [(0, 1), (8, 0), (14, 1)])),
        'effects': [(2, 'sound', snd('servo', 0.4, 1.2), None), (6, 'sound', snd('line_charge', 0.4, 0.9), None),
                    (7, 'sound', snd('line_cough', 0.5, 0.9), None), (8, 'particle', 'smoke', 'fx_line'),
                    (9, 'particle', 'large_smoke', 'fx_tail_l'), (10, 'timeline', 'dragline:refused', None),
                    (14, 'sound', snd('servo_sag', 0.3, 0.8), None)]}


# ==================================================================================================================
# The mandible's verbs (controller "arms"; one arm slot, so _l only)
# ==================================================================================================================
def mand(boom=None, head=None, jaw=None):
    d = {}
    if boom is not None:
        d['arm_l'] = {'rotation': boom}
    if head is not None:
        d['forearm_l'] = {'rotation': head}
    if jaw is not None:
        d['grip_l'] = {'rotation': jaw}
    return d


def verbs():
    v = {}
    v['spin'] = {'length': 8, 'loop': 'hold', 'bones': mand(
        [k(0, R()), k(1, R(x=-3), 'easeOutQuad'), k(5, R(x=8), 'easeOutCubic'), k(8, R(x=6), 'easeInOutSine')],
        [k(0, R()), k(2, R()), k(6, R(x=-8), 'easeOutCubic'), k(8, R(x=-6), 'easeInOutSine')]),
        'effects': [(1, 'sound', snd('servo', 0.4, 1.2), None)]}
    jit = [0, 0.5, -0.4, 0.5, -0.5, 0.4, -0.4, 0.5, -0.3, 0.4, 0]
    v['spin_loop'] = {'length': 10, 'loop': 'loop', 'bones': mand([k(t, R(x=6 + j)) for t, j in enumerate(jit)],
                                                                   const(R(x=-6))), 'effects': []}
    v['spin_end'] = {'length': 14, 'loop': 'once', 'bones': mand(
        [k(0, R(x=6)), k(2, R(x=9), 'easeOutQuad'), k(11, R(x=-2), 'easeInOutCubic'), k(14, R(), 'easeInOutSine')],
        [k(0, R(x=-6)), k(4, R(x=-6)), k(12, R(), 'easeInOutCubic')]), 'effects': [(2, 'sound', snd('servo', 0.4, 0.9), None)]}
    v['recoil'] = {'length': 20, 'loop': 'once', 'bones': mand(
        [k(0, R()), k(3, R(x=6), 'easeOutCubic'), k(4, R(x=5), 'easeInOutSine'), k(7, R(x=5)), k(8, R(x=13), 'easeOutQuad'),
         k(12, R(x=5), 'easeOutCubic'), k(14, R(x=5)), k(18, R(x=-1), 'easeInOutSine'), k(20, R(), 'easeInOutSine')],
        [k(0, R()), k(6, R()), k(7, R(x=10), 'easeInExpo'), k(11, R(), 'easeOutCubic')]),
        'effects': [(1, 'sound', snd('servo', 0.4, 1.3), None), (6, 'timeline', 'arm:fire:l', None)]}
    v['sustain'] = {'length': 8, 'loop': 'hold', 'bones': mand(
        [k(0, R()), k(1, R(x=-2), 'easeOutQuad'), k(6, R(x=5), 'easeOutCubic'), k(8, R(x=4), 'easeInOutSine')]),
        'effects': [(1, 'sound', snd('servo', 0.4, 1.1), None)]}
    v['sustain_loop'] = {'length': 40, 'loop': 'loop', 'bones': mand(
        [k(0, R(x=4)), k(10, R(x=4.8), 'easeInOutSine'), k(20, R(x=4), 'easeInOutSine'), k(30, R(x=3.2), 'easeInOutSine'),
         k(40, R(x=4), 'easeInOutSine')]), 'effects': []}
    v['sustain_end'] = {'length': 14, 'loop': 'once', 'bones': mand(
        [k(0, R(x=4)), k(2, R(x=7), 'easeOutQuad'), k(11, R(x=-2), 'easeInOutCubic'), k(14, R(), 'easeInOutSine')]),
        'effects': [(1, 'particle', 'smoke', 'fx_wrist_l')]}
    v['strike'] = {'length': 18, 'loop': 'once', 'bones': mand(
        [k(0, R()), k(4, R(x=14), 'easeOutCubic'), k(6, R(x=15)), k(8, R(x=-10), 'easeInExpo'), k(10, R(x=-12), 'easeOutQuad'),
         k(17, R(), 'easeOutBack', [0.5])],
        [k(0, R()), k(4, R(x=10), 'easeOutCubic'), k(7, R(x=11)), k(8, R(x=-6), 'easeInExpo'), k(18, R(), 'easeOutBack', [0.5])],
        [k(0, R()), k(4, R(x=35), 'easeOutCubic'), k(7, R(x=36)), k(8, R(x=-3), 'easeInExpo'), k(10, R(), 'easeOutQuad')]),
        'effects': [(1, 'sound', snd('servo', 0.4, 1.2), None), (8, 'timeline', 'arm:hit:l', None)]}
    g = R(x=28)
    v['guard'] = {'length': 10, 'loop': 'hold', 'bones': mand(
        [k(0, R()), k(1, R(x=-3), 'easeOutQuad'), k(8, R(x=30), 'easeInOutCubic'), k(10, g, 'easeInOutSine')],
        [k(0, R()), k(8, R(x=-20), 'easeInOutCubic')]), 'effects': [(1, 'sound', snd('servo', 0.4, 1.0), None)]}
    v['guard_loop'] = {'length': 20, 'loop': 'loop', 'bones': mand(
        [k(0, g), k(10, R(x=29), 'easeInOutSine'), k(20, g, 'easeInOutSine')], const(R(x=-20))), 'effects': []}
    v['guard_hit'] = {'length': 8, 'loop': 'once', 'bones': mand(
        [k(0, g), k(1, R(x=24), 'easeOutExpo'), k(2, R(x=24)), k(6, g, 'easeOutCubic')], const(R(x=-20))),
        'effects': [(0, 'timeline', 'arm:block:l', None)]}
    v['guard_end'] = {'length': 12, 'loop': 'once', 'bones': mand(
        [k(0, g), k(2, R(x=31), 'easeOutQuad'), k(10, R(x=-2), 'easeInOutCubic'), k(12, R(), 'easeInOutSine')],
        [k(0, R(x=-20)), k(10, R(), 'easeInOutCubic')]), 'effects': [(1, 'sound', snd('servo', 0.4, 0.9), None)]}
    v['dry_fire'] = {'length': 18, 'loop': 'once', 'bones': mand(
        [k(0, R()), k(1, R(x=-3), 'easeOutQuad'), k(5, R(x=12), 'easeOutCubic'), k(7, R(x=12)), k(10, R(x=-6), 'easeInQuad'),
         k(13, R(x=-6)), k(18, R(), 'easeInOutSine')], None,
        [k(0, R()), k(4, R(x=30), 'easeOutCubic'), k(6, R(x=30)), k(7, R(x=-4), 'easeInQuad'), k(9, R(), 'easeOutQuad')]),
        'effects': [(1, 'sound', snd('servo', 0.4, 1.2), None), (7, 'timeline', 'arm:dry:l', None),
                    (10, 'sound', snd('servo_sag', 0.3, 0.8), None)]}
    return v


# ==================================================================================================================
# States
# ==================================================================================================================
def hurt():
    return {'length': 10, 'loop': 'once', 'bones': {
        'root': {'rotation': [k(0, R()), k(1, R(x=1.5, z=-3), 'easeOutExpo'), k(3, R(x=1.5, z=-3)),
                              k(6, R(x=-0.5, z=1), 'easeInOutSine'), k(10, R(), 'easeInOutSine')],
                 'position': [k(0, P()), k(1, P(y=0.5), 'easeOutExpo'), k(3, P(y=0.5)), k(8, P(), 'easeInOutSine')]},
        'restraint': {'position': [k(0, P()), k(2, P(y=0.6), 'easeOutQuad'), k(4, P(), 'easeInQuad')]}},
        'effects': [(1, 'particle', 'electric_spark', 'fx_hull')]}


def limp():
    """Flat battery (speed x 0.3; playback = measured / 0.0778 blocks a tick): a slow walk one leg at a time (front
    left, rear right, front right, rear left), the front left dragging its pad; the body sags and the head hangs."""
    n, stance = 16, 12
    reach = V_LIMP * stance / 2.0
    ph = {'fl': 0, 'rr': 4, 'fr': 8, 'rl': 12}

    def feet(c, t):
        return step_foot(FOOT0[c], t - ph[c], n, stance, reach, 0.6 if c == 'fl' else 2.5, V_LIMP)
    return {'length': n, 'loop': 'loop', 'feet': feet, 'bones': merge(
        body([k(t, P(y=-2 + (-0.6 if t % 4 == 1 else 0))) for t in range(n + 1)], const(R(x=-2, z=1.5))),
        {'sensor': {'rotation': const(R(x=-14))}, 'arm_l': {'rotation': const(R(x=10))}},
        lamps([(0, 0)], [(0, 1)])),
        'effects': [(0, 'timeline', 'step:fl_drag', None), (4, 'timeline', 'step:light', None),
                    (8, 'timeline', 'step:light', None), (12, 'timeline', 'step:light', None)]}


def vent():
    heave = [k(0, P()), k(6, P()), k(18, P(y=-1.6), 'easeInOutSine'), k(33, P(y=-2.4), 'easeInOutSine'),
             k(48, P(y=-1.6), 'easeInOutSine'), k(63, P(y=-2.4), 'easeInOutSine'), k(78, P(y=-1.8), 'easeInOutSine'),
             k(84, P(y=-1.8)), k(96, P(y=0.3), 'easeInOutCubic'), k(100, P(), 'easeInOutSine')]
    b = merge(body(heave, [k(0, R()), k(3, R(x=2), 'easeOutQuad'), k(8, R()), k(18, R(x=-2), 'easeInOutSine'), k(84, R(x=-2)),
                           k(96, R(), 'easeInOutCubic')]), {
        'vent_l': {'rotation': [k(0, R()), k(1, R(x=-4), 'easeOutQuad'), k(3, R(x=80), 'easeInExpo'), k(5, R(x=74), 'easeInOutSine'),
                                k(78, R(x=74)), k(82, R(), 'easeInQuad')]},
        'vent_r': {'rotation': [k(0, R()), k(1, R(x=-4), 'easeOutQuad'), k(3, R(x=80), 'easeInExpo'), k(6, R(x=74), 'easeInOutSine'),
                                k(82, R(x=74)), k(86, R(), 'easeInQuad')]},
        'sensor': {'rotation': [k(0, R()), k(8, R()), k(18, R(x=-14), 'easeInOutSine'), k(84, R(x=-14)), k(94, R(), 'easeInOutSine')]}})
    return {'length': 100, 'loop': 'once', 'feet': static(lambda c: FOOT0[c]), 'bones': b,
            'effects': [(3, 'timeline', 'vent:blast', None),
                        (20, 'particle', 'cloud', 'fx_vent_l'), (26, 'particle', 'cloud', 'fx_vent_r'),
                        (36, 'particle', 'cloud', 'fx_vent_l'), (42, 'particle', 'cloud', 'fx_vent_r'),
                        (52, 'particle', 'cloud', 'fx_vent_l'), (58, 'particle', 'cloud', 'fx_vent_r'),
                        (68, 'particle', 'cloud', 'fx_vent_l'), (76, 'sound', snd('vent_whistle', 0.6, 0.9), None),
                        (82, 'sound', snd('vent_clank', 0.5, 1.0), None), (86, 'sound', snd('vent_clank', 0.5, 0.9), None)]}


def eject():
    """The Golden Parachute: klaxon; the clamps fly open (1); the cage is blown up and flipped back over its rear edge
    (110 by 4), hits its stop and stays open; the jar is fired up 18 px (the server's eject on 4); the body kicks down
    and sags; lamps out."""
    b = merge(
        {'jar_anchor': {'position': [k(0, P()), k(4, P()), k(6, P(y=18), 'easeOutCubic')],
                        'scale': [k(0, S(1), interp='step'), k(6, S(0), interp='step')]},
         'restraint': {'position': [k(0, P()), k(1, P(y=-0.5), 'easeOutQuad'), k(4, P(y=4), 'easeOutExpo')],
                       'rotation': [k(0, R()), k(1, R()), k(4, R(x=110), 'easeOutExpo'), k(7, R(x=95), 'easeInQuad'),
                                    k(10, R(x=101), 'easeOutQuad'), k(13, R(x=100), 'easeInOutSine')]}},
        clamps([k(0, [0]), k(1, [-30], 'easeOutExpo')]),
        body([k(0, P()), k(4, P()), k(5, P(y=-2), 'easeOutQuad'), k(8, P(y=-1), 'easeInOutSine'), k(28, P(y=-2.5), 'easeInQuad')],
             [k(0, R()), k(28, R(x=-1.5), 'easeInOutSine')]),
        lamps([(0, 1), (16, 0)], [(0, 1), (12, 0)]))
    return {'length': 30, 'loop': 'hold', 'feet': static(lambda c: FOOT0[c]), 'bones': b, 'pilot_until': 6,
            'effects': [(0, 'sound', snd('klaxon', 0.8, 1.0), None), (2, 'sound', snd('cage_blow', 0.8, 1.0), None),
                        (4, 'timeline', 'eject:jar', None), (7, 'sound', snd('cage_clank', 0.6, 1.0), None),
                        (14, 'particle', 'large_smoke', 'fx_hull'), (24, 'sound', snd('sigh', 0.5, 1.0), None)]}


def destroyed():
    """Collapse (40 ticks before the explosion; pilotless): sparks, the lamps die in steps, the body drops onto its
    belly (contact 18), then the legs curl in under it like a dead insect's, rear to front, 2 ticks apart, and one
    twitches; smoke."""
    tables = {}
    for i, c in enumerate(['rr', 'rl', 'fr', 'fl']):
        t0 = 20 + 2 * i
        a = FOOT0[c]
        tables[c] = [k(0, a), k(12, a), k(18, park_foot(c), 'easeInQuad'), k(t0, park_foot(c)),
                     k(t0 + 6, (SIGN[c][0] * 6, 3, SIGN[c][1] * 6), 'easeInOutCubic')]
        if c == 'fr':
            tables[c] += [k(33, (SIGN[c][0] * 8, 5, SIGN[c][1] * 8), 'easeOutQuad'), k(35, (SIGN[c][0] * 6, 3, SIGN[c][1] * 6), 'easeInQuad')]
    return {'length': 40, 'loop': 'hold', 'pilot': False, 'feet': keyed(tables), 'bones': merge(
        body([k(0, P()), k(8, P()), k(12, P(y=-1.5), 'easeInQuad'), k(18, P(y=PARK_Y - 0.5), 'easeInQuad'),
              k(20, P(y=PARK_Y), 'easeOutQuad'), k(22, P(y=PARK_Y - 0.5), 'easeInQuad')],
             [k(0, R()), k(10, R(z=3), 'easeInQuad'), k(18, R(x=-3, z=-2), 'easeInQuad'), k(22, R(x=-2.5, z=-1.5))]),
        {'sensor': {'rotation': [k(0, R()), k(6, R()), k(18, R(x=-12, z=10), 'easeInQuad')]},
         'arm_l': {'rotation': [k(0, R()), k(4, R()), k(16, R(x=18), 'easeInQuad')]}},
        lamps([(0, 1), (4, 0), (6, 1), (11, 0)], [(0, 1), (18, 0)])),
        'effects': [(0, 'timeline', 'fx:sparks:fx_hull', None), (7, 'particle', 'electric_spark', 'fx_core'),
                    (12, 'sound', snd('servo_fail', 0.6, 0.8), None), (14, 'particle', 'electric_spark', 'fx_hull'),
                    (18, 'timeline', 'destroyed:thud', None), (26, 'sound', snd('leg_curl', 0.5, 0.9), None),
                    (33, 'particle', 'large_smoke', 'fx_hull')]}


BUILD_SLOTS = [('back', 62, ['socket_back']), ('core', 70, ['socket_core']), ('torso_0', 78, ['socket_torso_0']),
               ('torso_1', 86, ['socket_torso_1']), ('arm_l', 94, ['socket_arm_l']), ('sensor', 102, ['socket_sensor']),
               ('sensor_1', 110, ['socket_sensor_1'])]


def outward(bone):
    rot = [b for b in G.bones() if b[0] == bone][0][3] or [0, 0, 0]
    x, y, z = [math.radians(a) for a in rot]
    vx, vy, vz = 0.0, 0.0, -1.0
    vy, vz = vy * math.cos(x) - vz * math.sin(x), vy * math.sin(x) + vz * math.cos(x)
    vx, vz = vx * math.cos(y) + vz * math.sin(y), -vx * math.sin(y) + vz * math.cos(y)
    vx, vy = vx * math.cos(z) - vy * math.sin(z), vx * math.sin(z) + vy * math.cos(z)
    return [round(vx, 6), round(vy, 6), round(vz, 6)]


def build():
    """The Gantry's assembly: delivered folded on its belly, the cage up; it rises and steps its legs out in the ripple
    (44-60); the modules arrive by slot (62-115); a shrug; inspection; the cage comes down (193); parked."""
    tables = {}
    for c, t0 in RIPPLE:
        a, b = park_foot(c), FOOT0[c]
        s = 30 + t0
        tables[c] = [k(0, a), k(s, a), k(s + 2, ((a[0] + b[0]) / 2, 4, (a[2] + b[2]) / 2), 'easeOutCubic'), k(s + 4, b, 'easeInQuad'),
                     k(194, b), k(196, ((a[0] + b[0]) / 2, 3, (a[2] + b[2]) / 2), 'easeOutCubic'), k(198, a, 'easeInQuad')]
    b = merge(
        body([k(0, P(y=PARK_Y)), k(42, P(y=PARK_Y)), k(44, P(y=PARK_Y - 0.4), 'easeOutQuad'), k(54, P(y=0.6), 'easeInOutCubic'),
              k(57, P(), 'easeInOutSine'), k(150, P()), k(153, P(y=-0.8), 'easeOutQuad'), k(158, P(), 'easeInOutSine'),
              k(194, P()), k(199, P(y=PARK_Y), 'easeInQuad'), k(200, P(y=PARK_Y))]),
        {'restraint': {'position': [k(0, P(y=20)), k(189, P(y=20)), k(190, P(y=21), 'easeOutQuad'), k(193, P(), 'easeInQuad'),
                                    k(194, P(y=0.6), 'easeOutQuad'), k(196, P(), 'easeInQuad')]},
         'sensor': {'rotation': [k(0, R(x=-18)), k(46, R(x=-18)), k(58, R(), 'easeInOutCubic'), k(172, R()),
                                 k(176, R(y=25), 'easeInOutCubic'), k(180, R(y=-25), 'easeInOutCubic'), k(184, R(), 'easeInOutSine'),
                                 k(194, R()), k(200, R(x=-18), 'easeInOutSine')]},
         'arm_l': {'rotation': [k(0, R(x=12)), k(50, R(x=12)), k(60, R(), 'easeInOutCubic'), k(194, R()), k(200, R(x=12), 'easeInOutSine')]}},
        lamps([(0, 0), (170, 1), (186, 0), (190, 1), (194, 0)], [(0, 0)]))
    effects = [(44, 'sound', snd('hydraulic_stand', 0.6, 1.2), None), (56, 'timeline', 'build:stand', None),
               (174, 'sound', snd('scan_chirp', 0.4, 1.0), None), (191, 'sound', snd('ding', 0.4, 1.0), None),
               (193, 'sound', snd('cage_drop', 0.6, 1.0), None), (199, 'sound', snd('belly_settle', 0.4, 1.0), None)]
    for slot, T0, socks in BUILD_SLOTS:
        for sb in socks:
            o = outward(sb)
            b[sb] = {'scale': [k(0, S(0), interp='step'), k(T0, S(1), interp='step')],
                     'position': [k(0, P(*[6 * c for c in o])), k(T0, P(*[6 * c for c in o])), k(T0 + 3, P(), 'easeInQuad'),
                                  k(T0 + 4, P(*[1.0 * c for c in o]), 'easeOutQuad'), k(T0 + 5, P(), 'easeInQuad')]}
        effects.append((T0 + 3, 'timeline', 'build:seat:%s' % slot, None))
    return {'length': 200, 'loop': 'hold', 'feet': keyed(tables), 'bones': b, 'effects': effects, 'pilot': False}


def clips():
    out = [('parked', parked()), ('board', board()), ('boot', boot()), ('boot_legs', boot_legs()), ('shutdown', shutdown()),
           ('idle', idle()), ('idle_tic', idle_tic()), ('walk', walk()), ('walk_back', walk(back=True)),
           ('turn_l', turn('l')), ('turn_r', turn('r')), ('climb', climb()), ('cling_on', cling_on()), ('cling', cling()),
           ('jump', jump()), ('fall', fall()), ('land', land(False)), ('land_heavy', land(True)),
           ('dragline_fire', dragline_fire()), ('dragline_reel', dragline_reel()), ('dragline_swing', dragline_swing()),
           ('dragline_rappel', dragline_rappel()), ('dragline_cut', dragline_cut()), ('dragline_refused', dragline_refused())]
    for name, c in verbs().items():
        out.append(('%s_l' % name, c))
    out += [('hurt', hurt()), ('limp', limp()), ('vent', vent()), ('eject', eject()), ('destroyed', destroyed()),
            ('build', build())]
    return out


def build_anims(model, baked=False):
    groups = {g['name']: g['uuid'] for g in model['groups']}
    anims = []
    for name, clip in clips():
        clip = copy.deepcopy(clip)
        solve_legs(clip)
        vents_to_z(clip)
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

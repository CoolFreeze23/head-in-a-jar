"""Wheelhouse ("wheeled" skeleton): every clip, keyed in ticks, written into wheelhouse.bbmodel.

Source of truth for the clips; hand edits to the bbmodel's animations are overwritten. Geometry and rig numbers come
from wheelhouse_geo.py. The keyframe helpers and GeckoLib 4.9.3 easing curves are the Hopper's
(art/mecha/hopper/hopper_anim.py), imported, so all three frames key alike.

    python art/mecha/wheelhouse/wheelhouse_anim.py                 # write the clips into the bbmodel
    python art/mecha/wheelhouse/wheelhouse_anim.py --model X --preview OUT
                                       # preview copy of model X: eased segments baked per tick, the stand-in jar hidden
                                       # when nobody is aboard, the wheels spun from the clip's design distance and the
                                       # brake lamps lit where the code would light them (both are code's job in game)

Key tuples: (tick, value, easing, easing_args, interpolation); the easing shapes the move ARRIVING at the key. In
GeckoLib 4.9.3 a settle is easeInElastic / easeInBounce (the names run the other way from easings.net).

Signs (Blockbench, as displayed; the game shows the same): body rot x + = nose up, rot z + = left side down; steer_* rot
y + = wheel turned left; wheel_* rot x - = rolling forward; helm rot z + = turned anticlockwise as the pilot sees it
from behind (= steering left); restraint rot z + = the ring swings up and over to the left; flag rot x + = stood up from stowed (90 = upright); 
arm_l (the tool boom's cradle, at the bumper's left end) rot x + = tool up; forearm_l (the ram) position z - = out; exhaust_* rot x - = flap open; strut_*
position y + = wheel pushed up into its arch, rot z = camber.

Wheel spin, steering lean and the brake lamps are NOT keyed in the game clips: code drives them (notes, section 7).
"""
import copy
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(os.path.dirname(HERE), 'hopper'))
import wheelhouse_geo as G  # noqa: E402
import hopper_anim as HA  # noqa: E402
from hopper_anim import k, R, P, S, lamp, const, value_at, keyframe, bake, snd, nid  # noqa: E402,F401

MODEL = os.path.join(HERE, 'wheelhouse.bbmodel')
T = 20.0
V = 0.24 * 2.16 * 16           # 8.29 px a tick (0.5184 blocks): design speed on built ground
V_REV = V * 0.4                # reverse: MechaLogic.riddenInput scales backward input by 0.4
V_RUSH = V * 1.6               # Rush Hour
V_LIMP = V * 0.3               # flat battery
CORNERS = ['fl', 'fr', 'rl', 'rr']
PARK_Y, PARK_X, PARK_ARM = -1.0, -0.6, -8.0


def lamps(head=None, tail=None, core=None):
    d = {}
    for bs, v in ((('lamp_head_fl', 'lamp_head_fr'), head), (('lamp_tail',), tail), (('lamp_core',), core)):
        if v is not None:
            for b in bs:
                d[b] = {'scale': lamp(v)}
    return d


OFF = lamps([(0, 0)], [(0, 0)], [(0, 0)])


def ring(keys):
    """The binnacle ring: degrees open (it swings up and over to the left on its hinge)."""
    return {'restraint': {'rotation': [(t, [0, 0, o[0]], e, a, i) for (t, o, e, a, i) in keys]}}


def vents_to_z(clip):
    """Flank flaps hinge along z: written as negative x (opening), stored as z (left -, right +)."""
    for side, sign in (('vent_l', 1), ('vent_r', -1)):
        ch = clip['bones'].get(side)
        if ch and 'rotation' in ch:
            ch['rotation'] = [(t, [0, 0, sign * v[0]], e, a, i) for (t, v, e, a, i) in ch['rotation']]


def merge(*ds):
    out = {}
    for d in ds:
        for b, ch in d.items():
            out.setdefault(b, {}).update(ch)
    return out


def body(pos=None, rot=None):
    d = {}
    if pos:
        d['position'] = pos
    if rot:
        d['rotation'] = rot
    return {'body': d}


def exhausts(keys_l, keys_r=None):
    return {'exhaust_l': {'rotation': keys_l}, 'exhaust_r': {'rotation': keys_r if keys_r is not None else keys_l}}


def tick_keys(fn, n, step=1):
    return [k(t, fn(t)) for t in range(0, n + 1, step)]


# ==================================================================================================================
# Life cycle
# ==================================================================================================================
def parked():
    """Unpiloted: sat down on its springs, nose a little low, the tool lowered, the ring closed over the empty collar,
    lamps off. Alive: a slow settle of the springs, the helm lamp's standby blip, the cooling engine ticking, a vent
    flap tick."""
    return {'length': 80, 'loop': 'loop', 'pilot': False, 'bones': merge(
        body([k(0, P(y=PARK_Y)), k(40, P(y=PARK_Y - 0.3), 'easeInOutSine'), k(80, P(y=PARK_Y), 'easeInOutSine')],
             [k(0, R(x=PARK_X)), k(44, R(x=PARK_X - 0.3), 'easeInOutSine'), k(80, R(x=PARK_X), 'easeInOutSine')]),
        {'arm_l': {'rotation': const(R(x=PARK_ARM))},
         'sensor': {'rotation': [k(0, R()), k(30, R(y=2), 'easeInOutSine'), k(60, R(y=-1.5), 'easeInOutSine'),
                                 k(80, R(), 'easeInOutSine')]},
         'restraint': {'rotation': const(R())},
         'vent_l': {'rotation': [k(0, R()), k(50, R()), k(52, R(x=-4), 'easeOutQuad'), k(55, R(), 'easeInQuad')]}},
        lamps([(0, 0)], [(0, 0)], [(0, 0), (1, 1), (3, 0)])),
        'effects': [(1, 'sound', snd('standby_blip', 0.15, 1.3), None), (30, 'sound', snd('cooling_tick', 0.12, 1.0), None),
                    (52, 'sound', snd('relay_tick', 0.15, 1.0), None), (66, 'sound', snd('cooling_tick', 0.1, 0.9), None)]}


def board():
    """Controller "cockpit" (control is live from tick 0): the ring is pressed down off its catch, swings up and over
    to the left, the jar is lowered into the collar (seated on 11), the ring comes down onto the lid and its catch
    (contact 18)."""
    b = merge(
        {'jar_anchor': {'position': [k(0, P(y=18, z=8)), k(5, P(y=18, z=8)), k(10, P(y=1.5), 'easeInOutSine'),
                                     k(11, P(), 'easeInQuad'), k(12, P(y=0.4), 'easeOutQuad'), k(13, P(), 'easeInQuad')]}},
        ring([k(0, [0]), k(2, [-2], 'easeOutQuad'), k(8, [115], 'easeOutCubic'), k(10, [108], 'easeInOutSine'),
              k(13, [108]), k(14, [111], 'easeOutQuad'), k(18, [0], 'easeInQuad'), k(19, [-2], 'easeOutQuad'),
              k(21, [0], 'easeInQuad')]))
    return {'length': 22, 'loop': 'hold', 'bones': b,
            'effects': [(1, 'sound', snd('catch_release', 0.5, 1.0), None), (3, 'sound', snd('servo', 0.5, 0.9), None),
                        (11, 'sound', snd('jar_seat', 0.6, 1.0), None), (14, 'sound', snd('servo', 0.5, 0.9), None),
                        (18, 'sound', snd('ring_latch', 0.7, 0.9), None),
                        (21, 'particle', 'cloud', 'fx_hull')]}


def boot():
    """Controller "cockpit" (over live controls): the headlamps blink twice at 1 Hz and stay on, the tail lamps, the
    helm lamp; the turret sweeps left and right with a proud little overshoot; the tool lifts and the ram strokes; the
    helm wheel and the front wheels test their lock; the exhausts cough; the horn says beep-beep."""
    b = merge(
        lamps([(0, 0), (2, 1), (12, 0), (22, 1)], [(0, 0), (4, 1)], [(0, 0), (6, 1)]),
        {'sensor': {'rotation': [k(0, R()), k(9, R(y=-3), 'easeOutQuad'), k(14, R(y=37), 'easeOutCubic'),
                                 k(16, R(y=35), 'easeInOutSine'), k(21, R(y=-37), 'easeInOutCubic'),
                                 k(23, R(y=-35), 'easeInOutSine'), k(28, R(), 'easeInOutCubic')]},
         'arm_l': {'rotation': [k(0, R()), k(14, R()), k(15, R(x=-2), 'easeOutQuad'), k(19, R(x=15), 'easeOutCubic'),
                                k(21, R(x=13), 'easeInOutSine'), k(26, R(), 'easeInOutCubic')]},
         'forearm_l': {'position': [k(0, P()), k(26, P()), k(28, P(z=-2), 'easeOutCubic'), k(31, P(), 'easeInQuad')]},
         'helm': {'rotation': [k(0, R()), k(29, R()), k(30, R(z=48), 'easeInOutSine'), k(33, R(z=-48), 'easeInOutSine'),
                               k(36, R(), 'easeInOutSine')]}},
        exhausts([k(0, R()), k(34, R()), k(35, R(x=-30), 'easeOutQuad'), k(38, R(), 'easeInQuad')],
                 [k(0, R()), k(35, R()), k(36, R(x=-30), 'easeOutQuad'), k(39, R(), 'easeInQuad')]))
    for c in ('fl', 'fr'):
        b['steer_' + c] = {'rotation': [k(0, R()), k(30, R()), k(31, R(y=12), 'easeInOutSine'),
                                        k(34, R(y=-12), 'easeInOutSine'), k(37, R(), 'easeInOutSine')]}
    return {'length': 40, 'loop': 'hold', 'bones': b,
            'effects': [(0, 'sound', snd('servo_chord', 0.7, 0.8), None), (2, 'sound', snd('relay_click', 0.4, 0.9), None),
                        (4, 'sound', snd('relay_click', 0.4, 1.0), None), (6, 'sound', snd('relay_click', 0.4, 1.1), None),
                        (14, 'sound', snd('servo_tick', 0.3, 1.0), None), (19, 'sound', snd('servo', 0.4, 1.1), None),
                        (21, 'sound', snd('servo_tick', 0.3, 0.9), None), (28, 'sound', snd('ram_stroke', 0.4, 1.0), None),
                        (30, 'sound', snd('steer_servo', 0.4, 1.0), None), (35, 'particle', 'cloud', 'fx_exhaust_l'),
                        (36, 'particle', 'cloud', 'fx_exhaust_r'), (37, 'timeline', 'horn', None),
                        (39, 'timeline', 'boot:done', None)]}


def boot_engine():
    """Controller "main" while the pilot is still (where the legged frames play boot_legs): the starter cranks (the
    body shudders 3-7), the engine catches on 9 (a torque rock to one side, a cough from both pipes), the springs lift
    the body off its parked sag with a small overshoot."""
    shud = [k(0, R(x=PARK_X)), k(2, R(x=PARK_X))]
    for i, t in enumerate(range(3, 8)):
        shud.append(k(t, R(x=PARK_X + (0.5 if i % 2 == 0 else -0.3))))
    shud += [k(9, R(x=PARK_X, z=0.3)), k(10, R(x=-0.3, z=-1.5), 'easeOutQuad'), k(13, R(x=0.2, z=0.6), 'easeInOutSine'),
             k(16, R(), 'easeInOutSine')]
    return {'length': 40, 'loop': 'hold', 'bones': merge(
        body([k(0, P(y=PARK_Y)), k(8, P(y=PARK_Y)), k(9, P(y=PARK_Y - 0.3), 'easeOutQuad'),
              k(16, P(y=0.5), 'easeInOutCubic'), k(20, P(), 'easeInOutSine')], shud),
        exhausts([k(0, R()), k(8, R()), k(9, R(x=-40), 'easeOutExpo'), k(13, R(), 'easeInQuad')],
                 [k(0, R()), k(9, R()), k(10, R(x=-40), 'easeOutExpo'), k(14, R(), 'easeInQuad')]),
        {'arm_l': {'rotation': [k(0, R(x=PARK_ARM)), k(10, R(x=PARK_ARM)), k(18, R(x=1), 'easeInOutCubic'),
                                k(21, R(), 'easeInOutSine')]}}),
        'effects': [(2, 'sound', snd('starter_whirr', 0.6, 1.0), None), (9, 'sound', snd('engine_catch', 0.7, 1.0), None),
                    (10, 'particle', 'large_smoke', 'fx_exhaust_l'), (11, 'particle', 'large_smoke', 'fx_exhaust_r'),
                    (17, 'sound', snd('suspension_creak', 0.3, 1.0), None)]}


def shutdown():
    return {'length': 20, 'loop': 'hold', 'bones': merge(
        body([k(0, P()), k(4, P(y=0.3), 'easeOutQuad'), k(14, P(y=PARK_Y - 0.3), 'easeInQuad'),
              k(18, P(y=PARK_Y), 'easeInOutSine')],
             [k(0, R()), k(4, R(z=1.2), 'easeOutQuad'), k(7, R(z=-0.4), 'easeInOutSine'), k(9, R()),
              k(16, R(x=PARK_X), 'easeInOutSine')]),
        exhausts([k(0, R()), k(3, R(x=-25), 'easeOutQuad'), k(7, R(), 'easeInQuad')], [k(0, R())]),
        {'arm_l': {'rotation': [k(0, R()), k(3, R()), k(12, R(x=PARK_ARM), 'easeInOutSine')]},
         'sensor': {'rotation': [k(0, R()), k(2, R()), k(12, R(), 'easeInOutSine')]}},
        lamps([(0, 1), (8, 0)], [(0, 1), (5, 0)], [(0, 1), (2, 0)])),
        'effects': [(0, 'sound', snd('power_down', 0.7, 0.8), None), (3, 'particle', 'smoke', 'fx_exhaust_l'),
                    (4, 'sound', snd('engine_stop', 0.6, 1.0), None), (14, 'sound', snd('suspension_creak', 0.4, 0.8), None)]}


# ==================================================================================================================
# Standing and driving
# ==================================================================================================================
def idle():
    """Engine running, standing: a fine 0.15 px shake every tick, a slow pitch drift, the exhaust flaps puffing in turn
    every 4 ticks, the tool nodding a hair."""
    def y(t):
        return 0.15 if t % 2 else 0.0
    pitch = lambda t: 0.3 * math.sin(2 * math.pi * t / 40)
    return {'length': 40, 'loop': 'loop', 'bones': merge(
        body([k(t, P(y=y(t))) for t in range(0, 41)], [k(t, R(x=round(pitch(t), 3))) for t in range(0, 41, 4)]),
        exhausts([k(t, R(x=-8 if t % 8 == 2 else 0)) for t in range(0, 41, 2)],
                 [k(t, R(x=-8 if t % 8 == 6 else 0)) for t in range(0, 41, 2)]),
        {'arm_l': {'rotation': [k(0, R()), k(20, R(x=0.6), 'easeInOutSine'), k(40, R(), 'easeInOutSine')]}}),
        'effects': []}


def idle_tic():
    """Every 200-300 idle ticks: it revs once, pleased with itself (a torque rock and a puff), then the turret has a
    look round."""
    return {'length': 40, 'loop': 'once', 'bones': merge(
        body(None, [k(0, R()), k(2, R(z=0.5), 'easeOutQuad'), k(6, R(x=0.8, z=-2), 'easeOutQuad'),
                    k(10, R(z=0.8), 'easeInOutSine'), k(14, R(), 'easeInOutSine')]),
        exhausts([k(0, R()), k(4, R()), k(5, R(x=-40), 'easeOutExpo'), k(10, R(), 'easeInQuad')],
                 [k(0, R()), k(5, R()), k(6, R(x=-40), 'easeOutExpo'), k(11, R(), 'easeInQuad')]),
        {'sensor': {'rotation': [k(0, R()), k(16, R()), k(17, R(y=-2), 'easeOutQuad'), k(22, R(y=28), 'easeInOutCubic'),
                                 k(26, R(y=28)), k(32, R(y=-10), 'easeInOutCubic'), k(38, R(), 'easeInOutSine')]}}),
        'effects': [(4, 'sound', snd('engine_rev', 0.5, 1.1), None), (5, 'particle', 'smoke', 'fx_exhaust_l'),
                    (6, 'particle', 'smoke', 'fx_exhaust_r'), (22, 'sound', snd('servo_tick', 0.3, 1.0), None),
                    (32, 'sound', snd('servo_tick', 0.3, 0.9), None)]}


def road(n, bumps, base_pitch, amp=0.8, buzz=0.15, base_y=0.0):
    """Struts riding small bumps: bumps = [(corner, tick)], each 'amp' px up over two ticks and down over two; the body
    answers a tick later (nose up for a front bump, down for a rear one, rolled away from the side) on top of a base
    pitch, a fine buzz, the ring and the tool rattling."""
    bones = {}
    for c in CORNERS:
        keys = [k(0, P())]
        for cc, t0 in bumps:
            if cc == c:
                keys += [k(t0, P()), k(t0 + 1, P(y=amp), 'easeOutQuad'), k(t0 + 3, P(), 'easeInQuad')]
        keys.append(k(n, P()))
        ks = {}
        for kk in keys:
            ks[kk[0]] = kk
        bones['strut_' + c] = {'position': [ks[t] for t in sorted(ks)]}

    def resp(t):
        x = base_pitch
        z = 0.0
        y = base_y + (buzz if t % 2 else 0.0)
        for cc, t0 in bumps:
            d = (t - t0 - 1) % n
            w = {0: 0.0, 1: 1.0, 2: 0.7, 3: 0.3}.get(d, 0.0)
            x += (0.5 if cc[0] == 'f' else -0.5) * w
            z += (-0.6 if cc[1] == 'l' else 0.6) * w
            y += 0.3 * w
        return x, y, z
    bones['body'] = {'position': [k(t, P(y=round(resp(t)[1], 3))) for t in range(n + 1)],
                     'rotation': [k(t, R(x=round(resp(t)[0], 3), z=round(resp(t)[2], 3))) for t in range(n + 1)]}
    rat = {}
    for cc, t0 in bumps:
        rat[(t0 + 2) % n] = 0.8
    bones['restraint'] = {'rotation': [k(t, R(z=rat.get(t % n, 0.0))) for t in range(n + 1)]}
    bones['arm_l'] = {'rotation': [k(t, R(x=round(-1.0 * sum({1: 0.6, 2: 1.0, 3: 0.4}.get((t - t0 - 1) % n, 0)
                                                            for cc, t0 in bumps if cc[0] == 'f'), 3)))
                                   for t in range(n + 1)]}
    return bones


def drive():
    """Loop, authored at the design speed (8.29 px a tick; playback = measured / design). The wheels' spin is code's.
    Each station rides a small bump, the rear wheel on a side three ticks after the front (24 px of wheelbase at 8.29 px
    a tick); the body sits 0.6 degrees nose-up under power and answers each bump; the flaps flutter."""
    n = 20
    bones = road(n, [('fl', 2), ('rl', 5), ('fr', 11), ('rr', 14)], 0.6)
    bones.update(exhausts([k(t, R(x=-12 if t % 4 == 0 else -18)) for t in range(0, n + 1, 2)],
                          [k(t, R(x=-18 if t % 4 == 0 else -12)) for t in range(0, n + 1, 2)]))
    return {'length': n, 'loop': 'loop', 'bones': bones, 'effects': []}


def drive_start():
    """Once, from standing into drive: the nose dips (wind-up), the rear squats and the nose lifts as the torque comes
    on, it settles into drive's 0.6 degrees; the ring rattles; the tool lags."""
    return {'length': 12, 'loop': 'once', 'bones': merge(
        body([k(0, P()), k(6, P(y=-0.6), 'easeOutCubic'), k(12, P(), 'easeInOutSine')],
             [k(0, R()), k(2, R(x=-0.8), 'easeOutQuad'), k(6, R(x=3.5), 'easeOutCubic'), k(12, R(x=0.6), 'easeInOutSine')]),
        exhausts([k(0, R()), k(3, R(x=-30), 'easeOutExpo'), k(12, R(x=-15), 'easeInOutSine')]),
        {'restraint': {'rotation': [k(0, R()), k(7, R(z=1.5), 'easeOutQuad'), k(10, R(), 'easeInQuad')]},
         'arm_l': {'rotation': [k(0, R()), k(6, R(x=-3), 'easeOutQuad'), k(11, R(), 'easeInOutSine')]}}),
        'effects': [(0, 'sound', snd('engine_rev', 0.6, 0.9), None), (3, 'particle', 'smoke', 'fx_exhaust_l')]}


def reverse():
    """Loop at the reverse design speed (3.32 px a tick: playback = measured backward speed / 0.2074 blocks a tick):
    the nose dips a little, bumps come rear-first."""
    n = 20
    bones = road(n, [('rl', 2), ('fl', 9), ('rr', 11), ('fr', 16)], -0.6, amp=0.6)
    bones.update(exhausts([k(0, R(x=-8)), k(10, R(x=-12), 'easeInOutSine'), k(20, R(x=-8), 'easeInOutSine')]))
    return {'length': n, 'loop': 'loop', 'bones': bones, 'effects': []}


def brake():
    """Once, from driving to a stop: the nose dives 4 degrees in 3 ticks, holds, rebounds past level and settles; the
    tool nods; the ring rattles; the buffer lurches; a squeal, dust at the front tyres, the hiss when it has stopped."""
    return {'length': 16, 'loop': 'once', 'bones': merge(
        body([k(0, P()), k(3, P(y=-0.8), 'easeOutQuad'), k(5, P(y=-0.7)), k(10, P(y=0.3), 'easeInOutSine'),
              k(16, P(), 'easeInOutSine')],
             [k(0, R(x=0.6)), k(3, R(x=-4), 'easeOutQuad'), k(5, R(x=-3.6), 'easeInOutSine'), k(10, R(x=1.2), 'easeInOutSine'),
              k(16, R(), 'easeInOutSine')]),
        {'arm_l': {'rotation': [k(0, R()), k(4, R(x=-5), 'easeOutQuad'), k(9, R(x=2), 'easeInOutSine'),
                                k(13, R(), 'easeInOutSine')]},
         'restraint': {'rotation': [k(0, R()), k(4, R(z=1.5), 'easeOutQuad'), k(6, R(z=-0.5), 'easeInOutSine'),
                                    k(8, R(), 'easeInOutSine')]},
         'buffer': {'position': [k(0, P()), k(3, P(z=-0.5), 'easeOutQuad'), k(8, P(), 'easeInOutSine')]}},
        exhausts([k(0, R(x=-15)), k(5, R(), 'easeInQuad')])),
        'effects': [(0, 'sound', snd('brake_squeal', 0.5, 1.0), None), (2, 'particle', 'cloud', 'fx_contact_fl'),
                    (3, 'particle', 'cloud', 'fx_contact_fr'), (12, 'sound', snd('brake_hiss', 0.5, 1.0), None)]}


# ---- steering (controller "steer": steer_* and helm only; the body's lean into the turn is code's) ----------------
def steer(direction):
    """Hold: the helm wheel turns (one tick ahead of the road wheels), the front wheels lock 20 degrees with a small
    wind-up and settle, the rear wheels 6 degrees the other way (four-wheel steer)."""
    s = 1 if direction == 'l' else -1
    b = {'helm': {'rotation': [k(0, R()), k(1, R(z=-6 * s), 'easeOutQuad'), k(4, R(z=84 * s), 'easeOutCubic'),
                               k(6, R(z=80 * s), 'easeInOutSine')]}}
    for c in ('fl', 'fr'):
        b['steer_' + c] = {'rotation': [k(0, R()), k(2, R(y=-2 * s), 'easeOutQuad'), k(5, R(y=21 * s), 'easeOutCubic'),
                                        k(7, R(y=20 * s), 'easeInOutSine')]}
    for c in ('rl', 'rr'):
        b['steer_' + c] = {'rotation': [k(0, R()), k(3, R()), k(6, R(y=-6 * s), 'easeOutCubic'), k(7, R(y=-6 * s))]}
    return {'length': 7, 'loop': 'hold', 'bones': b,
            'effects': [(1, 'sound', snd('steer_servo', 0.3, 1.0 if s > 0 else 0.95), None)]}


def turn(direction):
    """Loop, turning on the spot (the body chases the look while standing): four-wheel lock, front 30 one way and rear 18
    the other, so it pivots close to its middle; the wheels hunt 2 degrees; a tyre scrub every 6 ticks. The wheels' creep
    is code's (notes, section 7)."""
    s = 1 if direction == 'l' else -1
    b = {'helm': {'rotation': const(R(z=120 * s))}}
    # the rear lock is 18, not 30: at 30 the rear tyres' inner corners would sweep into the tub and the exhaust pipes
    for c, lock, ph in (('fl', 30, 0), ('fr', 30, 1), ('rl', -18, 3), ('rr', -18, 4)):
        sg = 1 if lock > 0 else -1
        b['steer_' + c] = {'rotation': [k(0, R(y=lock * s)), k(3 + ph % 3, R(y=(lock + 2 * sg) * s), 'easeInOutSine'),
                                        k(9 + ph % 3, R(y=(lock - sg) * s), 'easeInOutSine'),
                                        k(12, R(y=lock * s), 'easeInOutSine')]}
    return {'length': 12, 'loop': 'loop', 'bones': b,
            'effects': [(0, 'sound', snd('tyre_scrub', 0.3, 1.0), None), (6, 'sound', snd('tyre_scrub', 0.3, 0.9), None)]}


def drift(direction):
    """Loop, the handbrake drift (Sneak while turning at speed): the front wheels counter-steer 15 degrees and hunt,
    the rear wheels straight and locked (code stops their spin), the helm wheel wound the other way; tyre smoke from
    the rear wheels alternately. The model's yaw against the direction of travel is code's."""
    s = 1 if direction == 'l' else -1
    b = {'helm': {'rotation': [k(0, R(z=-60 * s)), k(4, R(z=-70 * s), 'easeInOutSine'), k(8, R(z=-60 * s), 'easeInOutSine')]}}
    for c in ('fl', 'fr'):
        b['steer_' + c] = {'rotation': [k(0, R(y=-15 * s)), k(4, R(y=-18 * s), 'easeInOutSine'),
                                        k(8, R(y=-15 * s), 'easeInOutSine')]}
    for c in ('rl', 'rr'):
        b['steer_' + c] = {'rotation': const(R())}
    return {'length': 8, 'loop': 'loop', 'bones': b,
            'effects': [(0, 'particle', 'cloud', 'fx_contact_rl'), (2, 'particle', 'cloud', 'fx_contact_rr'),
                        (3, 'sound', snd('tyre_skid', 0.5, 1.0), None),
                        (4, 'particle', 'cloud', 'fx_contact_rl'), (6, 'particle', 'cloud', 'fx_contact_rr')]}


# ---- Rush Hour (controller "main"; the ram on "hit") ---------------------------------------------------------------
# What reads from behind (the pilot's camera): the RIGHT OF WAY flag standing up on the right, the two striped exhaust
# flaps blown fully open (and the flames at them), the body squatting 1 px nose-up for the whole sprint. From the side
# and front: the striped buffer beam run out 6 px and dropped 1 (the ram deployed).
FLAG_UP = 90.0


def flag(keys):
    return {'flag': {'rotation': keys}}


def rush_windup():
    """Drive pressed: the nose dips (wind-up), the flag is pressed down a hair; then the rear squats and the nose lifts
    4.5 degrees as the revs climb, the flag snaps up past upright and settles, the exhaust flaps are blown fully open
    with a smoke cough each side, the buffer beam runs out 6 px (the ram is deployed); a two-tick tremble; on 12 the
    server starts the boost (rush:go)."""
    return {'length': 12, 'loop': 'hold', 'bones': merge(
        body([k(0, P()), k(8, P(y=-1.2), 'easeInOutCubic'), k(9, P(y=-1.1)), k(10, P(y=-1.25)), k(12, P(y=-1), 'easeInExpo')],
             [k(0, R()), k(2, R(x=-1), 'easeOutQuad'), k(8, R(x=4.5), 'easeInOutCubic'), k(9, R(x=4.8)), k(10, R(x=4.4)),
              k(12, R(x=3), 'easeInExpo')]),
        exhausts([k(0, R()), k(2, R(x=5), 'easeOutQuad'), k(8, R(x=-95), 'easeInExpo'), k(10, R(x=-87), 'easeInOutSine'),
                  k(12, R(x=-90), 'easeInOutSine')],
                 [k(0, R()), k(3, R(x=5), 'easeOutQuad'), k(9, R(x=-95), 'easeInExpo'), k(11, R(x=-87), 'easeInOutSine'),
                  k(12, R(x=-90), 'easeInOutSine')]),
        flag([k(0, R()), k(3, R(x=-5), 'easeOutQuad'), k(8, R(x=FLAG_UP + 10), 'easeOutCubic'),
              k(11, R(x=FLAG_UP), 'easeInElastic', [3]), k(12, R(x=FLAG_UP))]),
        {'buffer': {'position': [k(0, P()), k(2, P(z=0.5), 'easeOutQuad'), k(8, P(y=-1, z=-6), 'easeOutCubic'),
                                 k(10, P(y=-1, z=-5.5), 'easeInOutSine'), k(12, P(y=-1, z=-6), 'easeInOutSine')]},
         'restraint': {'rotation': [k(0, R()), k(9, R(z=1), 'easeOutQuad'), k(10, R(z=-0.6)), k(12, R(), 'easeInOutSine')]},
         'arm_l': {'rotation': [k(0, R()), k(9, R(x=-3), 'easeInOutSine'), k(12, R(x=-4), 'easeInExpo')]}}),
        'effects': [(0, 'sound', snd('engine_rev', 0.7, 0.8), None), (3, 'sound', snd('relay_click', 0.4, 1.2), None),
                    (8, 'sound', snd('flag_snap', 0.5, 1.0), None), (9, 'particle', 'large_smoke', 'fx_exhaust_l'),
                    (10, 'particle', 'large_smoke', 'fx_exhaust_r'), (12, 'timeline', 'rush:go', None)]}


def rush():
    """Loop, the boost (80 ticks of it): squatting 1 px, nose up 3 degrees with a tremble, the road coming faster, the
    ram out and buzzing, the flag fluttering, both flaps held wide open, a flame from one pipe or the other every tick.
    Authored at 1.6 x the design speed: playback = measured / 0.8294 blocks a tick."""
    n = 10
    bones = road(n, [('fl', 1), ('rl', 3), ('fr', 5), ('rr', 7)], 3.0, amp=0.9, buzz=0.25, base_y=-1.0)
    bones['body']['rotation'] = [(t, [v[0] + (0.3 if t % 2 else -0.3) * (0 < t < n), v[1], v[2]], e, a, i)
                                 for (t, v, e, a, i) in bones['body']['rotation']]
    bones['buffer'] = {'position': [k(t, P(y=-1, z=-6 + (0.25 if t % 2 else 0))) for t in range(n + 1)]}
    bones.update(flag([k(t, R(x=FLAG_UP, y=(4 if t % 4 == 1 else -4 if t % 4 == 3 else 0))) for t in range(n + 1)]))
    bones.update(exhausts([k(t, R(x=-90 if t % 4 == 0 else -84)) for t in range(0, n + 1, 2)],
                          [k(t, R(x=-84 if t % 4 == 0 else -90)) for t in range(0, n + 1, 2)]))
    return {'length': n, 'loop': 'loop', 'bones': bones,
            'effects': [(t, 'particle', 'flame', 'fx_exhaust_l' if t % 2 == 0 else 'fx_exhaust_r') for t in range(n)]}


def rush_ram():
    """Controller "hit", once, from the server's ram (contact on 0): the deployed buffer beam is shoved in 3 px, holds,
    springs out 1 px past its stop and settles on an elastic; the whole machine pitches 2 degrees on its tyres and
    rocks back past level; the ring rattles."""
    return {'length': 14, 'loop': 'once', 'bones': {
        'buffer': {'position': [k(0, P(y=-1, z=-6)), k(1, P(y=-1, z=-3), 'easeInExpo'), k(2, P(y=-1, z=-3)),
                                k(5, P(y=-1, z=-7), 'easeOutCubic'), k(10, P(y=-1, z=-6), 'easeInElastic', [3])]},
        'root': {'rotation': [k(0, R()), k(1, R(x=-2), 'easeOutExpo'), k(5, R(x=0.8), 'easeInOutSine'),
                              k(8, R(x=-0.3), 'easeInOutSine'), k(12, R(), 'easeInOutSine')]},
        'restraint': {'rotation': [k(0, R()), k(2, R(z=2.5), 'easeOutQuad'), k(4, R(z=-0.8), 'easeInOutSine'),
                                   k(7, R(), 'easeInOutSine')]}},
        'effects': [(0, 'timeline', 'rush:ram', None), (2, 'sound', snd('buffer_spring', 0.5, 1.0), None),
                    (3, 'particle', 'cloud', 'fx_buffer')]}


def rush_end():
    """Once, the boost runs out: the flag folds down (it lands on 7 with a bounce), the ram winds back in, the nose dips
    as it sheds speed, rebounds and settles; the flaps clank shut, left (9) then right (11); one last puff from the left
    pipe a few ticks after everything has stopped."""
    return {'length': 24, 'loop': 'once', 'bones': merge(
        body([k(0, P(y=-1)), k(6, P(y=-0.8), 'easeOutQuad'), k(16, P(), 'easeInOutSine')],
             [k(0, R(x=3)), k(5, R(x=-2), 'easeInOutCubic'), k(11, R(x=0.6), 'easeInOutSine'), k(16, R(), 'easeInOutSine')]),
        exhausts([k(0, R(x=-90)), k(2, R(x=-97), 'easeOutQuad'), k(9, R(), 'easeInQuad'), k(14, R()),
                  k(15, R(x=-25), 'easeOutExpo'), k(18, R(), 'easeInQuad')],
                 [k(0, R(x=-90)), k(3, R(x=-97), 'easeOutQuad'), k(11, R(), 'easeInQuad')]),
        flag([k(0, R(x=FLAG_UP)), k(2, R(x=FLAG_UP + 5), 'easeOutQuad'), k(7, R(), 'easeInQuad'), k(8, R(x=-4), 'easeOutQuad'),
              k(10, R(), 'easeInQuad')]),
        {'arm_l': {'rotation': [k(0, R(x=-4)), k(5, R(x=2), 'easeInOutSine'), k(10, R(), 'easeInOutSine')]},
         'buffer': {'position': [k(0, P(y=-1, z=-6)), k(3, P(y=-1, z=-6.5), 'easeOutQuad'), k(10, P(), 'easeInOutCubic'),
                                 k(11, P(z=0.4), 'easeOutQuad'), k(13, P(), 'easeInQuad')]}}),
        'effects': [(0, 'sound', snd('rush_wind_down', 0.6, 1.0), None), (7, 'sound', snd('flag_fold', 0.5, 1.0), None),
                    (9, 'sound', snd('flap_clank', 0.5, 1.0), None), (11, 'sound', snd('flap_clank', 0.5, 0.9), None),
                    (15, 'particle', 'smoke', 'fx_exhaust_l'), (16, 'sound', snd('exhaust_pop', 0.25, 1.3), None)]}


def rush_refused():
    """Once, Drive pressed when Rush Hour cannot run (off-road, flat battery, still cooling, venting). The first six
    ticks are the wind-up's (nose dip, squat, the flag starting up, the ram starting out), so the press is recognised;
    then the engine coughs on 8: one black puff, the flaps pop fully open and then hang half-open, loose, for 20 ticks;
    the body lurches 3 degrees forward in 2 ticks and sags 1.8 px, recovering over 10; the flag gets to 45 degrees and
    flops back down; the ram slides back; the ship's wheel slips back 30 degrees; the tail lamps dip once; the tool
    sags. The HUD says why (rush:refused). Never nothing."""
    return {'length': 34, 'loop': 'once', 'bones': merge(
        body([k(0, P()), k(6, P(y=-0.8), 'easeInOutCubic'), k(8, P(y=-0.8)), k(10, P(y=-1.8), 'easeInQuad'),
              k(20, P(), 'easeInOutSine')],
             [k(0, R()), k(2, R(x=-1), 'easeOutQuad'), k(6, R(x=2.8), 'easeInOutCubic'), k(8, R(x=2.9)),
              k(10, R(x=-3), 'easeInQuad'), k(14, R(x=0.6), 'easeInOutSine'), k(18, R(), 'easeInOutSine')]),
        exhausts([k(0, R()), k(2, R(x=5), 'easeOutQuad'), k(8, R(x=-20), 'easeInQuad'), k(9, R(x=-90), 'easeOutExpo'),
                  k(13, R(x=-45), 'easeInOutSine'), k(18, R(x=-52), 'easeInOutSine'), k(24, R(x=-45), 'easeInOutSine'),
                  k(28, R(x=-45)), k(32, R(), 'easeInQuad')],
                 [k(0, R()), k(3, R(x=5), 'easeOutQuad'), k(9, R(x=-20), 'easeInQuad'), k(10, R(x=-90), 'easeOutExpo'),
                  k(14, R(x=-45), 'easeInOutSine'), k(20, R(x=-50), 'easeInOutSine'), k(26, R(x=-45), 'easeInOutSine'),
                  k(29, R(x=-45)), k(33, R(), 'easeInQuad')]),
        flag([k(0, R()), k(3, R(x=-5), 'easeOutQuad'), k(7, R(x=45), 'easeOutCubic'), k(9, R(x=47)),
              k(13, R(), 'easeInQuad'), k(14, R(x=-4), 'easeOutQuad'), k(16, R(), 'easeInQuad')]),
        {'arm_l': {'rotation': [k(0, R()), k(10, R()), k(13, R(x=-6), 'easeInQuad'), k(22, R(x=-6)),
                                k(28, R(), 'easeInOutSine')]},
         'buffer': {'position': [k(0, P()), k(2, P(z=0.5), 'easeOutQuad'), k(6, P(y=-0.5, z=-2.5), 'easeOutCubic'),
                                 k(8, P(y=-0.5, z=-2.5)), k(12, P(), 'easeInQuad'), k(13, P(z=0.3), 'easeOutQuad'),
                                 k(15, P(), 'easeInQuad')]},
         'helm': {'rotation': [k(0, R()), k(9, R()), k(16, R(z=-30), 'easeInOutSine'), k(28, R(), 'easeInOutSine')]}},
        lamps(None, [(0, 1), (9, 0), (15, 1)], None)),
        'effects': [(0, 'sound', snd('engine_rev', 0.5, 0.8), None), (8, 'sound', snd('backfire_pop', 0.6, 0.9), None),
                    (9, 'particle', 'large_smoke', 'fx_exhaust_l'), (10, 'timeline', 'rush:refused', None),
                    (11, 'sound', snd('suspension_clunk', 0.5, 0.9), None), (13, 'sound', snd('flag_flop', 0.4, 0.9), None),
                    (32, 'sound', snd('flap_clank', 0.4, 0.8), None)]}


# ---- bumps and landings ----------------------------------------------------------------------------------------------
def bump():
    """Once, over a block step at speed: the front wheels kick up into their arches, the rear three ticks later; the
    nose lifts, then falls as the rear goes over; the ring rattles."""
    b = merge(body([k(0, P()), k(2, P(y=0.6), 'easeOutQuad'), k(4, P(y=0.2)), k(5, P(y=0.6), 'easeOutQuad'),
                    k(9, P(), 'easeInOutSine')],
                   [k(0, R()), k(2, R(x=1.8), 'easeOutQuad'), k(5, R(x=-1.4), 'easeInOutSine'), k(8, R(x=0.4), 'easeInOutSine'),
                    k(12, R(), 'easeInOutSine')]),
              {'restraint': {'rotation': [k(0, R()), k(3, R(z=2), 'easeOutQuad'), k(5, R(z=-0.6), 'easeInOutSine'),
                                          k(7, R(), 'easeInOutSine')]}})
    for c, t0 in (('fl', 0), ('fr', 0), ('rl', 3), ('rr', 3)):
        keys = [k(0, P())] + ([k(t0, P())] if t0 else []) + [k(t0 + 1, P(y=2), 'easeOutExpo'), k(t0 + 4, P(), 'easeInQuad')]
        b['strut_' + c] = {'position': keys}
    return {'length': 12, 'loop': 'once', 'bones': b,
            'effects': [(1, 'sound', snd('suspension_clunk', 0.5, 1.0), None), (4, 'sound', snd('suspension_clunk', 0.5, 0.9), None)]}


def camber(keys_deg):
    """Wheels splayed on landing: the tops lean in (left strut rot z -, right +)."""
    d = {}
    for c in CORNERS:
        sg = -1 if c[1] == 'l' else 1
        d['strut_' + c] = {'rotation': [(t, [0, 0, sg * v[0]], e, a, i) for (t, v, e, a, i) in keys_deg]}
    return d


def land(heavy=False):
    """Contact on 0. The body drops onto its springs (1.6 px light, 1.9 heavy: the mudguards stop 2 px over the tyres),
    the wheels splay and come back, the ring jolts; heavy: the flank vents blow and close on an elastic settle."""
    if heavy:
        L = 24
        bd = body([k(0, P()), k(3, P(y=-1.9), 'easeOutCubic'), k(6, P(y=-1.8)), k(12, P(y=0.8), 'easeInOutCubic'),
                   k(16, P(y=-0.3), 'easeInOutSine'), k(22, P(), 'easeInOutSine')],
                  [k(0, R()), k(3, R(x=-1.5), 'easeOutCubic'), k(12, R(x=0.5), 'easeInOutSine'), k(20, R(), 'easeInOutSine')])
        cam = camber([k(0, [0]), k(3, [7], 'easeOutCubic'), k(12, [0], 'easeInElastic', [3])])
        rin = [k(0, R()), k(2, R(z=4), 'easeOutQuad'), k(5, R(z=-1.2), 'easeInOutSine'), k(8, R(), 'easeInOutSine')]
    else:
        L = 16
        bd = body([k(0, P()), k(2, P(y=-1.6), 'easeOutCubic'), k(3, P(y=-1.5)), k(8, P(y=0.5), 'easeInOutSine'),
                   k(13, P(), 'easeInOutSine')],
                  [k(0, R()), k(2, R(x=-1), 'easeOutCubic'), k(8, R(x=0.3), 'easeInOutSine'), k(13, R(), 'easeInOutSine')])
        cam = camber([k(0, [0]), k(2, [3], 'easeOutCubic'), k(8, [0], 'easeInOutSine')])
        rin = [k(0, R()), k(2, R(z=2.5), 'easeOutQuad'), k(5, R(), 'easeInOutSine')]
    b = merge(bd, cam, {'restraint': {'rotation': rin}},
              exhausts([k(0, R()), k(1, R(x=-40), 'easeOutExpo'), k(7, R(), 'easeInQuad')]))
    eff = [(0, 'timeline', 'land:heavy' if heavy else 'land:light', None)]
    if heavy:
        b['vent_l'] = {'rotation': [k(0, R()), k(2, R(x=-35), 'easeOutExpo'), k(12, R(), 'easeInElastic', [4])]}
        b['vent_r'] = {'rotation': [k(0, R()), k(3, R(x=-35), 'easeOutExpo'), k(13, R(), 'easeInElastic', [4])]}
        eff += [(2, 'particle', 'cloud', 'fx_vent_l'), (3, 'particle', 'cloud', 'fx_vent_r'),
                (12, 'sound', snd('vent_clank', 0.6, 0.9), None)]
    return {'length': L, 'loop': 'once', 'bones': b, 'effects': eff}


# ==================================================================================================================
# The turret's verbs (controller "arms"; one arm slot, so _l only): arm_l = the cradle's elevation, forearm_l = the ram
# ==================================================================================================================
def tur(cradle=None, ram=None):
    d = {}
    if cradle is not None:
        d['arm_l'] = {'rotation': cradle}
    if ram is not None:
        d['forearm_l'] = {'position': ram}
    return d


def verbs():
    v = {}
    v['spin'] = {'length': 8, 'loop': 'hold', 'bones': tur(
        [k(0, R()), k(1, R(x=-3), 'easeOutQuad'), k(5, R(x=6), 'easeOutCubic'), k(8, R(x=4), 'easeInOutSine')],
        [k(0, P()), k(2, P()), k(7, P(z=-1.5), 'easeOutCubic'), k(8, P(z=-1), 'easeInOutSine')]),
        'effects': [(1, 'sound', snd('servo', 0.4, 1.2), None)]}
    jit = [0, 0.5, -0.4, 0.5, -0.5, 0.4, -0.4, 0.5, -0.3, 0.4, 0]
    v['spin_loop'] = {'length': 10, 'loop': 'loop', 'bones': tur(
        [k(t, R(x=4 + j)) for t, j in enumerate(jit)], [k(t, P(z=-1 + 0.3 * jit[(t + 1) % 10])) for t in range(11)]),
        'effects': []}
    v['spin_end'] = {'length': 14, 'loop': 'once', 'bones': tur(
        [k(0, R(x=4)), k(2, R(x=7), 'easeOutQuad'), k(11, R(x=-2), 'easeInOutCubic'), k(14, R(), 'easeInOutSine')],
        [k(0, P(z=-1)), k(6, P(), 'easeInOutSine')]),
        'effects': [(2, 'sound', snd('servo', 0.4, 0.9), None)]}
    v['recoil'] = {'length': 20, 'loop': 'once', 'bones': tur(
        [k(0, R()), k(3, R(x=5), 'easeOutCubic'), k(4, R(x=4), 'easeInOutSine'), k(7, R(x=4)), k(8, R(x=10), 'easeOutQuad'),
         k(12, R(x=4), 'easeOutCubic'), k(14, R(x=4)), k(18, R(x=-1), 'easeInOutSine'), k(20, R(), 'easeInOutSine')],
        [k(0, P()), k(6, P()), k(7, P(z=3), 'easeInExpo'), k(11, P(), 'easeOutCubic')]),
        'effects': [(1, 'sound', snd('servo', 0.4, 1.3), None), (6, 'timeline', 'arm:fire:l', None)]}
    v['sustain'] = {'length': 8, 'loop': 'hold', 'bones': tur(
        [k(0, R()), k(1, R(x=-2), 'easeOutQuad'), k(6, R(x=4), 'easeOutCubic'), k(8, R(x=3), 'easeInOutSine')],
        [k(0, P()), k(5, P()), k(8, P(z=0.5), 'easeInQuad')]),
        'effects': [(1, 'sound', snd('servo', 0.4, 1.1), None)]}
    v['sustain_loop'] = {'length': 40, 'loop': 'loop', 'bones': tur(
        [k(0, R(x=3)), k(10, R(x=3.8), 'easeInOutSine'), k(20, R(x=3), 'easeInOutSine'), k(30, R(x=2.2), 'easeInOutSine'),
         k(40, R(x=3), 'easeInOutSine')], const(P(z=0.5))), 'effects': []}
    v['sustain_end'] = {'length': 14, 'loop': 'once', 'bones': tur(
        [k(0, R(x=3)), k(2, R(x=6), 'easeOutQuad'), k(11, R(x=-2), 'easeInOutCubic'), k(14, R(), 'easeInOutSine')],
        [k(0, P(z=0.5)), k(3, P(), 'easeOutQuad')]),
        'effects': [(1, 'particle', 'smoke', 'fx_wrist_l')]}
    v['strike'] = {'length': 18, 'loop': 'once', 'bones': tur(
        [k(0, R()), k(4, R(x=8), 'easeOutCubic'), k(6, R(x=8.5)), k(8, R(x=-6), 'easeInExpo'), k(10, R(x=-7), 'easeOutQuad'),
         k(17, R(), 'easeOutBack', [0.5])],
        [k(0, P()), k(4, P(z=3), 'easeOutCubic'), k(7, P(z=3.2)), k(8, P(z=-4), 'easeInExpo'), k(10, P(z=-4.5), 'easeOutQuad'),
         k(18, P(), 'easeOutBack', [0.5])]),
        'effects': [(1, 'sound', snd('servo', 0.4, 1.2), None), (8, 'timeline', 'arm:hit:l', None)]}
    g = R(x=25)
    v['guard'] = {'length': 10, 'loop': 'hold', 'bones': tur(
        [k(0, R()), k(1, R(x=-3), 'easeOutQuad'), k(8, R(x=27), 'easeInOutCubic'), k(10, g, 'easeInOutSine')],
        [k(0, P()), k(8, P(z=1), 'easeInOutSine')]),
        'effects': [(1, 'sound', snd('servo', 0.4, 1.0), None)]}
    v['guard_loop'] = {'length': 20, 'loop': 'loop', 'bones': tur(
        [k(0, g), k(10, R(x=26), 'easeInOutSine'), k(20, g, 'easeInOutSine')], const(P(z=1))), 'effects': []}
    v['guard_hit'] = {'length': 8, 'loop': 'once', 'bones': tur(
        [k(0, g), k(1, R(x=22), 'easeOutExpo'), k(2, R(x=22)), k(6, g, 'easeOutCubic')],
        [k(0, P(z=1)), k(1, P(z=3.5), 'easeOutExpo'), k(2, P(z=3.5)), k(6, P(z=1), 'easeOutCubic')]),
        'effects': [(0, 'timeline', 'arm:block:l', None)]}
    v['guard_end'] = {'length': 12, 'loop': 'once', 'bones': tur(
        [k(0, g), k(2, R(x=28), 'easeOutQuad'), k(10, R(x=-2), 'easeInOutCubic'), k(12, R(), 'easeInOutSine')],
        [k(0, P(z=1)), k(4, P(), 'easeInOutSine')]),
        'effects': [(1, 'sound', snd('servo', 0.4, 0.9), None)]}
    v['dry_fire'] = {'length': 18, 'loop': 'once', 'bones': tur(
        [k(0, R()), k(1, R(x=-3), 'easeOutQuad'), k(5, R(x=10), 'easeOutCubic'), k(7, R(x=10)), k(10, R(x=-6), 'easeInQuad'),
         k(13, R(x=-6)), k(18, R(), 'easeInOutSine')],
        [k(0, P()), k(6, P(z=-1.5), 'easeOutCubic'), k(7, P(z=-1.5)), k(10, P(z=0.5), 'easeInQuad'), k(18, P(), 'easeInOutSine')]),
        'effects': [(1, 'sound', snd('servo', 0.4, 1.2), None), (7, 'timeline', 'arm:dry:l', None),
                    (10, 'sound', snd('servo_sag', 0.3, 0.8), None)]}
    return v


# ==================================================================================================================
# States
# ==================================================================================================================
def hurt():
    """Controller "hit": the whole machine rocks on its tyres from a blow on the front left (mirror z for the right)."""
    return {'length': 10, 'loop': 'once', 'bones': {
        'root': {'rotation': [k(0, R()), k(1, R(x=1.5, z=-3), 'easeOutExpo'), k(3, R(x=1.5, z=-3)),
                              k(6, R(x=-0.5, z=1), 'easeInOutSine'), k(10, R(), 'easeInOutSine')],
                 'position': [k(0, P()), k(1, P(y=0.5), 'easeOutExpo'), k(3, P(y=0.5)), k(8, P(), 'easeInOutSine')]},
        'restraint': {'rotation': [k(0, R()), k(2, R(z=2.5), 'easeOutQuad'), k(4, R(z=-0.8), 'easeInOutSine'),
                                   k(6, R(), 'easeInOutSine')]}},
        'effects': [(1, 'particle', 'electric_spark', 'fx_hull')]}


def limp():
    """Loop, flat battery (speed x 0.3; playback = measured / 0.1555 blocks a tick): sagging on tired springs, nose
    down, the tool hanging; the engine misfires twice a cycle, each a jerk and a puff from one pipe; only the tail lamps
    are lit."""
    n = 32

    def pit(t):
        x = -1.0
        for t0 in (0, 16):
            d = (t - t0) % n
            x += {1: -1.2, 2: -0.9, 3: -0.5, 4: -0.2}.get(d, 0)
        return x
    return {'length': n, 'loop': 'loop', 'bones': merge(
        body([k(0, P(y=-1)), k(16, P(y=-1.2), 'easeInOutSine'), k(32, P(y=-1), 'easeInOutSine')],
             [k(t, R(x=round(pit(t), 3))) for t in range(n + 1)]),
        exhausts([k(0, R()), k(1, R(x=-45), 'easeOutExpo'), k(6, R(), 'easeInQuad'), k(32, R())],
                 [k(0, R()), k(16, R()), k(17, R(x=-45), 'easeOutExpo'), k(22, R(), 'easeInQuad'), k(32, R())]),
        {'arm_l': {'rotation': const(R(x=-12))}},
        lamps([(0, 0)], [(0, 1)], [(0, 0)])),
        'effects': [(0, 'sound', snd('engine_misfire', 0.5, 1.0), None), (1, 'particle', 'smoke', 'fx_exhaust_l'),
                    (16, 'sound', snd('engine_misfire', 0.5, 0.9), None), (17, 'particle', 'smoke', 'fx_exhaust_r'),
                    (24, 'sound', snd('servo_catch', 0.4, 0.7), None)]}


def vent():
    """Overheat (100 ticks): the flank flaps blow open (3), the body jolts, slumps on its springs and pants, the tool
    hangs; steam from each vent in turn; the kettle whistle; the flaps clank shut (82, 86); it straightens and shakes
    itself out."""
    heave = [k(0, P()), k(6, P()), k(18, P(y=-1.3), 'easeInOutSine'), k(33, P(y=-1.9), 'easeInOutSine'),
             k(48, P(y=-1.3), 'easeInOutSine'), k(63, P(y=-1.9), 'easeInOutSine'), k(78, P(y=-1.4), 'easeInOutSine'),
             k(84, P(y=-1.4)), k(96, P(y=0.3), 'easeInOutCubic'), k(100, P(), 'easeInOutSine')]
    rot = [k(0, R()), k(3, R(x=2), 'easeOutQuad'), k(7, R(), 'easeInOutSine'), k(8, R()), k(18, R(x=-1.5), 'easeInOutSine'),
           k(84, R(x=-1.5)), k(94, R(x=0.3), 'easeInOutCubic'), k(96, R(z=-1), 'easeOutQuad'), k(98, R(z=0.8), 'easeInOutSine'),
           k(100, R(), 'easeInOutSine')]
    b = merge(body(heave, rot), {
        'vent_l': {'rotation': [k(0, R()), k(1, R(x=4), 'easeOutQuad'), k(3, R(x=-80), 'easeInExpo'),
                                k(5, R(x=-74), 'easeInOutSine'), k(78, R(x=-74)), k(82, R(), 'easeInQuad')]},
        'vent_r': {'rotation': [k(0, R()), k(1, R(x=4), 'easeOutQuad'), k(3, R(x=-80), 'easeInExpo'),
                                k(6, R(x=-74), 'easeInOutSine'), k(82, R(x=-74)), k(86, R(), 'easeInQuad')]},
        'arm_l': {'rotation': [k(0, R()), k(8, R()), k(18, R(x=-10), 'easeInQuad'), k(84, R(x=-10)),
                               k(94, R(), 'easeInOutCubic')]}})
    return {'length': 100, 'loop': 'once', 'bones': b,
            'effects': [(3, 'timeline', 'vent:blast', None),
                        (20, 'particle', 'cloud', 'fx_vent_l'), (26, 'particle', 'cloud', 'fx_vent_r'),
                        (36, 'particle', 'cloud', 'fx_vent_l'), (42, 'particle', 'cloud', 'fx_vent_r'),
                        (52, 'particle', 'cloud', 'fx_vent_l'), (58, 'particle', 'cloud', 'fx_vent_r'),
                        (68, 'particle', 'cloud', 'fx_vent_l'), (76, 'sound', snd('vent_whistle', 0.6, 0.8), None),
                        (82, 'sound', snd('vent_clank', 0.6, 0.9), None), (86, 'sound', snd('vent_clank', 0.6, 0.8), None),
                        (95, 'sound', snd('rattle', 0.5, 0.8), None)]}


def eject():
    """The Golden Parachute: klaxon; the ring is blown up and over to the left (150 by 4),
    falls back to its stop (132 at 7) and stays open; the jar is fired up (server dismount and launch on 4); the body
    kicks down on its springs and sags into parked; lamps out in steps; smoke."""
    b = merge(
        {'jar_anchor': {'position': [k(0, P()), k(4, P()), k(6, P(y=18), 'easeOutCubic')],
                        'scale': [k(0, S(1), interp='step'), k(6, S(0), interp='step')]},
         'arm_l': {'rotation': [k(0, R()), k(10, R()), k(24, R(x=PARK_ARM), 'easeInOutSine')]}},
        body([k(0, P()), k(4, P()), k(5, P(y=-1.5), 'easeOutQuad'), k(8, P(y=-0.8), 'easeInOutSine'), k(10, P(y=-0.8)),
              k(24, P(y=PARK_Y - 0.3), 'easeInQuad'), k(28, P(y=PARK_Y), 'easeInOutSine')],
             [k(0, R()), k(4, R()), k(5, R(x=-1), 'easeOutQuad'), k(10, R(), 'easeInOutSine'),
              k(26, R(x=PARK_X), 'easeInOutSine')]),
        ring([k(0, [0]), k(1, [-2], 'easeOutQuad'), k(4, [150], 'easeOutExpo'), k(7, [132], 'easeInQuad'),
              k(10, [138], 'easeOutQuad'), k(13, [136], 'easeInOutSine')]),
        lamps([(0, 1), (20, 0)], [(0, 1), (16, 0)], [(0, 1), (4, 0)]))
    return {'length': 30, 'loop': 'hold', 'bones': b, 'pilot_until': 6,
            'effects': [(0, 'sound', snd('klaxon', 0.8, 0.9), None), (2, 'sound', snd('ring_blow', 0.8, 0.9), None),
                        (4, 'timeline', 'eject:jar', None), (7, 'sound', snd('ring_clank', 0.6, 0.9), None),
                        (14, 'particle', 'large_smoke', 'fx_hull'), (22, 'particle', 'large_smoke', 'fx_hull'),
                        (26, 'sound', snd('sigh', 0.6, 0.8), None)]}


def destroyed():
    """Collapse (40 ticks before the server's explosion; pilotless): sparks; the lamps die in three steps; the tool
    drops; the front-left wheel splays out (8-12) and the corner sags; the rear-right follows (14-20); on 24 the
    front-left wheel comes off, rolls out ahead and falls flat (contact 36); the bare corner drops to the ground
    (contact 27), a bounce, smoke."""
    b = merge(
        body([k(0, P()), k(8, P()), k(12, P(y=-2), 'easeInQuad'), k(14, P(y=-1.7), 'easeOutQuad'), k(20, P(y=-2.8), 'easeInQuad'),
              k(24, P(y=-2.8)), k(27, P(y=-2.8), 'easeInQuad'), k(29, P(y=-2.3), 'easeOutQuad'), k(31, P(y=-2.8), 'easeInQuad')],
             [k(0, R()), k(8, R()), k(12, R(x=-3, z=6), 'easeInQuad'), k(14, R(x=-2.6, z=5.4), 'easeOutQuad'),
              k(20, R(x=-2, z=3), 'easeInQuad'), k(24, R(x=-2, z=3)), k(27, R(x=-5, z=7), 'easeInQuad'),
              k(29, R(x=-4.4, z=6.2), 'easeOutQuad'), k(31, R(x=-5, z=7), 'easeInQuad')]),
        {'strut_fl': {'rotation': [k(0, R()), k(8, R()), k(12, R(z=25), 'easeInQuad'), k(14, R(z=22), 'easeOutQuad')],
                      'position': [k(0, P()), k(8, P()), k(12, P(y=0.4), 'easeInQuad')]},
         'strut_rr': {'rotation': [k(0, R()), k(14, R()), k(20, R(z=-18), 'easeInQuad'), k(22, R(z=-16), 'easeOutQuad')],
                      'position': [k(0, P()), k(14, P()), k(20, P(y=0.5), 'easeInQuad')]},
         'steer_fl': {'position': [k(0, P()), k(24, P()), k(26, P(x=-4, y=1.6), 'easeOutQuad'), k(36, P(x=-10, y=3, z=-16), 'easeInQuad')],
                      'rotation': [k(0, R()), k(24, R()), k(36, R(z=65), 'easeInQuad'), k(38, R(z=60), 'easeOutQuad'),
                                   k(40, R(z=65), 'easeInQuad')]},
         'wheel_fl': {'rotation': [k(0, R()), k(24, R()), k(36, R(x=-400), 'easeInQuad'), k(40, R(x=-410), 'easeOutQuad')]},
         'arm_l': {'rotation': [k(0, R()), k(2, R()), k(10, R(x=-15), 'easeInQuad'), k(27, R(x=-12), 'easeInOutSine')]},
         'sensor': {'rotation': [k(0, R()), k(6, R()), k(16, R(y=12), 'easeInQuad')]}},
        exhausts([k(0, R()), k(26, R()), k(28, R(x=-50), 'easeOutExpo'), k(33, R(x=-30), 'easeInOutSine')]),
        lamps([(0, 1), (4, 0)], [(0, 1), (11, 0)], [(0, 1), (18, 0)]))
    return {'length': 40, 'loop': 'hold', 'bones': b, 'pilot': False,
            'effects': [(0, 'timeline', 'fx:sparks:fx_hull', None), (7, 'particle', 'electric_spark', 'fx_core'),
                        (12, 'sound', snd('servo_fail', 0.7, 0.7), None), (14, 'particle', 'electric_spark', 'fx_hull'),
                        (24, 'sound', snd('wheel_pop', 0.7, 0.9), None), (27, 'timeline', 'destroyed:thud', None),
                        (33, 'particle', 'large_smoke', 'fx_hull'), (36, 'sound', snd('wheel_clatter', 0.6, 1.0), None)]}


BUILD_SLOTS = [('back', 62, ['socket_back']), ('back_1', 70, ['socket_back_1']), ('core', 78, ['socket_core']),
               ('torso_0', 86, ['socket_torso_0']), ('torso_1', 94, ['socket_torso_1']), ('arm_l', 102, ['socket_arm_l']),
               ('sensor', 110, ['socket_sensor'])]


def outward(bone):
    rot = [b for b in G.bones() if b[0] == bone][0][3] or [0, 0, 0]
    x, y, z = [math.radians(a) for a in rot]
    vx, vy, vz = 0.0, 0.0, -1.0
    vy, vz = vy * math.cos(x) - vz * math.sin(x), vy * math.sin(x) + vz * math.cos(x)
    vx, vz = vx * math.cos(y) + vz * math.sin(y), -vx * math.sin(y) + vz * math.cos(y)
    vx, vy = vx * math.cos(z) - vy * math.sin(z), vx * math.sin(z) + vy * math.cos(z)
    return [round(vx, 6), round(vy, 6), round(vz, 6)]


def build():
    """The Gantry's assembly (200 ticks): delivered down on its bump stops, the turret stowed facing backwards, the
    ring open; rises on its springs (44-60) and swings the turret round; the modules arrive by slot (62-115); a shrug
    when the plates go on; inspection (lamps, a turret scan, the helm tested); the ring comes down; parked."""
    b = merge(
        body([k(0, P(y=-2)), k(42, P(y=-2)), k(44, P(y=-2.4), 'easeOutQuad'), k(54, P(y=0.6), 'easeInOutCubic'),
              k(56, P(y=-0.6), 'easeInQuad'), k(60, P(), 'easeInOutSine'), k(150, P()), k(153, P(y=-0.8), 'easeOutQuad'),
              k(158, P(), 'easeInOutSine'), k(194, P()), k(199, P(y=PARK_Y), 'easeInQuad'), k(200, P(y=PARK_Y))],
             [k(0, R(x=-1)), k(44, R(x=-1)), k(54, R(x=0.8), 'easeInOutCubic'), k(60, R(), 'easeInOutSine'), k(194, R()),
              k(199, R(x=PARK_X), 'easeInOutSine')]),
        {'sensor': {'rotation': [k(0, R(y=180)), k(46, R(y=180)), k(57, R(y=-6), 'easeInOutCubic'), k(60, R(), 'easeInOutSine'),
                                 k(174, R()), k(178, R(y=25), 'easeInOutCubic'), k(182, R(y=-25), 'easeInOutCubic'),
                                 k(185, R(), 'easeInOutSine')]},
         'arm_l': {'rotation': [k(0, R(x=-10)), k(48, R(x=-10)), k(59, R(x=2), 'easeInOutCubic'), k(62, R(), 'easeInOutSine'),
                                k(190, R()), k(198, R(x=PARK_ARM), 'easeInOutSine')]},
         'helm': {'rotation': [k(0, R()), k(176, R()), k(178, R(z=60), 'easeInOutSine'), k(181, R(z=-60), 'easeInOutSine'),
                               k(184, R(), 'easeInOutSine')]}},
        ring([k(0, [100]), k(188, [100]), k(189, [104], 'easeOutQuad'), k(193, [0], 'easeInQuad'), k(194, [-2], 'easeOutQuad'),
              k(196, [0], 'easeInQuad')]),
        lamps([(0, 0), (170, 1), (186, 0)], [(0, 0), (172, 1), (186, 0)], [(0, 0), (190, 1), (194, 0)]))
    effects = [(44, 'sound', snd('suspension_rise', 0.6, 0.9), None), (50, 'sound', snd('servo', 0.4, 0.9), None),
               (56, 'timeline', 'build:stand', None), (174, 'sound', snd('scan_chirp', 0.4, 0.9), None),
               (191, 'sound', snd('ding', 0.4, 0.9), None), (193, 'sound', snd('ring_latch', 0.6, 0.9), None),
               (198, 'sound', snd('sigh', 0.5, 0.8), None)]
    for slot, T0, socks in BUILD_SLOTS:
        for sb in socks:
            o = outward(sb)
            b[sb] = {'scale': [k(0, S(0), interp='step'), k(T0, S(1), interp='step')],
                     'position': [k(0, P(*[6 * c for c in o])), k(T0, P(*[6 * c for c in o])), k(T0 + 3, P(), 'easeInQuad'),
                                  k(T0 + 4, P(*[1.0 * c for c in o]), 'easeOutQuad'), k(T0 + 5, P(), 'easeInQuad')]}
        effects.append((T0 + 3, 'timeline', 'build:seat:%s' % slot, None))
    return {'length': 200, 'loop': 'hold', 'bones': b, 'effects': effects, 'pilot': False}


def clips():
    out = [('parked', parked()), ('board', board()), ('boot', boot()), ('boot_engine', boot_engine()),
           ('shutdown', shutdown()), ('idle', idle()), ('idle_tic', idle_tic()),
           ('drive_start', drive_start()), ('drive', drive()), ('reverse', reverse()), ('brake', brake()),
           ('steer_l', steer('l')), ('steer_r', steer('r')), ('turn_l', turn('l')), ('turn_r', turn('r')),
           ('drift_l', drift('l')), ('drift_r', drift('r')),
           ('rush_windup', rush_windup()), ('rush', rush()), ('rush_ram', rush_ram()), ('rush_end', rush_end()),
           ('rush_refused', rush_refused()),
           ('bump', bump()), ('land', land(False)), ('land_heavy', land(True))]
    for name, c in verbs().items():
        out.append(('%s_l' % name, c))
    out += [('hurt', hurt()), ('limp', limp()), ('vent', vent()), ('eject', eject()), ('destroyed', destroyed()),
            ('build', build())]
    return out


# preview only: what the code does in game (wheel spin from distance; brake lamps)
SPIN = {'drive': lambda t: V * t, 'rush': lambda t: V_RUSH * t, 'reverse': lambda t: -V_REV * t,
        'limp': lambda t: V_LIMP * t, 'rush_windup': lambda t: V * t, 'rush_ram': lambda t: V_RUSH * t,
        'rush_end': lambda t: V * t + 0.6 * V * min(t, 12) * (1 - min(t, 12) / 24.0), 'bump': lambda t: V * t,
        'drive_start': lambda t: V * min(t, 12) ** 2 / 24.0 + V * max(0, t - 12),
        'brake': lambda t: V * (min(t, 6) - min(t, 6) ** 2 / 12.0)}
BRAKE_LIT = {'brake': 16}


def build_anims(model, baked=False):
    groups = {g['name']: g['uuid'] for g in model['groups']}
    anims = []
    for name, clip in clips():
        clip = copy.deepcopy(clip)
        vents_to_z(clip)
        if baked and name in SPIN:
            for c in CORNERS:
                ch = clip['bones'].setdefault('wheel_' + c, {})
                if 'rotation' not in ch:
                    ch['rotation'] = [k(t, R(x=round(-math.degrees(SPIN[name](t) / G.WHEEL_R), 2)))
                                      for t in range(0, clip['length'] + 1)]
        if baked and BRAKE_LIT.get(name):
            clip['bones']['lamp_brake'] = {'scale': lamp([(0, 1), (BRAKE_LIT[name], 0)])}
        elif baked:
            clip['bones'].setdefault('lamp_brake', {'scale': lamp([(0, 0)])})
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

"""Kite ("winged" skeleton): every clip, keyed in ticks, written into kite.bbmodel.

Source of truth for the clips; hand edits to the bbmodel's animations are overwritten. Rig numbers come from
kite_geo.py; the keyframe helpers, easings, arm-verb tables and right-arm mirroring are the Hopper's (hopper_anim.py).

    python art/mecha/kite/kite_anim.py                           # write the clips into the bbmodel
    python art/mecha/kite/kite_anim.py --model X --preview OUT   # baked preview copy

Legs are solved every tick (knee back, the Hopper's solver with the Kite's numbers): on the ground the feet are
planted; in the air they are given relative to the hip.

Signs (Blockbench, as displayed). Wings are keyed for the LEFT wing and mirrored (rot y and z negated) for the right:
wing_l rot z + = down (the down-stroke), z - = up; rot y + = swept back (folding), y - = swept forward; rot x + = the
leading edge up. wingtip_l the same about the wrist. tail rot x + = tail down (flare), rot z = tail rolled, rot y =
rudder. restraint (the lantern's floor) position y - = lowered on its cables, rot x - = tipped back. talon_* rot x - = toes closed (clutch), + = spread.
body: hips position y + = up; torso rot x + = nose up, rot z + = left side down.
"""
import copy
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(os.path.dirname(HERE), 'hopper'))
import kite_geo as G  # noqa: E402
import hopper_anim as HA  # noqa: E402
from hopper_anim import k, R, P, S, lamp, const, value_at, keyframe, bake, snd, nid, thin, mirror_keys, side_clip  # noqa

MODEL = os.path.join(HERE, 'kite.bbmodel')
T = 20.0
V = 0.12 * 2.16 * 16           # 4.15 px a tick on the ground
V_LIMP = V * 0.3
REST_FOOT_T = (G.REST_ANKLE[0], G.REST_ANKLE[1], 0.0)
TOE = 6.0
PARK_HIPS = -3.0               # the lantern's floor stays 3 px off the ground
FOLD = dict(wing=R(y=85, z=-28), tip=R(y=168, z=-4))        # folded: swept back along the back, tilted up outward
                                                            # (a V from behind), the outer wing doubled back on top
SPREAD = dict(wing=R(), tip=R())
FLY_FEET = (6.0, -19.0, 30.0)  # legs trailing in flight: ankle (z, y) relative to the hip, toes pointed back


# ------------------------------------------------------------------------------------------------------------------
# Legs (the Hopper's solver, the Kite's rig)
# ------------------------------------------------------------------------------------------------------------------
def solve_legs(clip):
    feet = clip.get('feet')
    if feet is None:
        return
    hips = clip['bones'].get('hips', {})
    hpos = sorted(hips.get('position', [k(0, P())]), key=lambda x: x[0])
    hrot = sorted(hips.get('rotation', [k(0, R())]), key=lambda x: x[0])
    out = {s: {'leg': [], 'shin': [], 'foot': []} for s in 'lr'}
    for t in range(0, int(clip.get('ik_until', clip['length'])) + 1):
        hp = value_at(hpos, t)
        pitch = value_at(hrot, t)[0]
        hip = (hp[2], G.HIP_Y + hp[1])
        for s in 'lr':
            f = feet(s, t)
            rel = len(f) == 4
            z, y, ang = f[0], f[1], f[2]
            ank = (hip[0] + z, hip[1] + y) if rel else (z, y)
            t1, t2 = G.ik(hip, ank)
            out[s]['leg'].append((t, t1 - pitch - G.REST_THIGH))
            out[s]['shin'].append((t, t2 - t1 - G.REST_SHIN))
            out[s]['foot'].append((t, ang - t2 - G.REST_FOOT))
    splay = clip.get('splay')
    for s in 'lr':
        for part in ('leg', 'shin', 'foot'):
            keys = thin(out[s][part], clip.get('loop') == 'loop')
            if part == 'leg' and splay:
                sg = -1 if s == 'l' else 1
                keys = [(t, v) for t, v in out[s][part]]
                clip['bones'].setdefault('leg_' + s, {})['rotation'] = [
                    k(t, R(x=round(v, 3), z=round(sg * value_at(splay, t)[0], 3))) for t, v in keys]
                continue
            clip['bones'].setdefault('%s_%s' % (part, s), {})['rotation'] = [k(t, R(x=round(v, 3))) for t, v in keys]


def planted(ang=0.0):
    return lambda s, t: (REST_FOOT_T[0], REST_FOOT_T[1], ang)


def keyed(tables, rel=False):
    srt = {s: sorted(v, key=lambda x: x[0]) for s, v in tables.items()}
    return lambda s, t: tuple(value_at(srt[s], t)) + (('rel',) if rel else ())


def fly_feet(extra=None):
    """Legs trailing back in flight (relative to the hip)."""
    return keyed({s: [k(0, FLY_FEET)] for s in 'lr'}, rel=True) if extra is None else extra


def on_toe(z, ang):
    lift = TOE * math.sin(math.radians(-ang)) if ang < 0 else 0.0
    return (z, G.ANKLE_H + lift, ang)


# ------------------------------------------------------------------------------------------------------------------
# Helpers
# ------------------------------------------------------------------------------------------------------------------
def merge(*ds):
    out = {}
    for d in ds:
        for b, ch in d.items():
            out.setdefault(b, {}).update(ch)
    return out


def wings(wing_keys, tip_keys=None, lag=0):
    """Left keys as given (rotation), the right mirrored, optionally a tick or two later."""
    d = {'wing_l': {'rotation': wing_keys}}
    if tip_keys is not None:
        d['wingtip_l'] = {'rotation': tip_keys}
    for b in list(d):
        keys = d[b]['rotation']
        last = max(kk[0] for kk in keys)
        sh = [((kk[0] + lag) if 0 < kk[0] < last else kk[0],) + kk[1:] for kk in keys
              if not (0 < kk[0] < last and kk[0] + lag >= last)]
        d[b.replace('_l', '_r')] = {'rotation': mirror_keys(sh, 'rotation')}
    return d


def folded():
    return wings(const(FOLD['wing']), const(FOLD['tip']))


def hips(pos=None, rot=None):
    d = {}
    if pos:
        d['position'] = pos
    if rot:
        d['rotation'] = rot
    return {'hips': d}


def torso(rot):
    return {'torso': {'rotation': rot}}


def lamps(core=None, nav=None):
    d = {}
    if core is not None:
        d['lamp_core'] = {'scale': lamp(core)}
    if nav is not None:
        d['lamp_nav_l'] = {'scale': lamp(nav)}
        d['lamp_nav_r'] = {'scale': lamp(nav)}
    return d


def talons(keys):
    return {'talon_l': {'rotation': keys}, 'talon_r': {'rotation': keys}}


def arms(arm_keys, fore_keys=None):
    d = {'arm_l': {'rotation': arm_keys}}
    if fore_keys:
        d['forearm_l'] = {'rotation': fore_keys}
    for b in list(d):
        d[b.replace('_l', '_r')] = {'rotation': mirror_keys(d[b]['rotation'], 'rotation')}
    return d


def flap_keys(n, up, down, down_t, tip_up=-15, tip_down=12, ease_down='easeInQuad'):
    """One wingbeat of n ticks: from the top (up) a down-stroke of down_t ticks (accelerating) to the bottom (down),
    then the recovery back up over the rest (easeInOutSine). The wingtip lags: it trails up on the down-stroke and whips
    down at the bottom; on the recovery it sweeps back and trails down."""
    w = [k(0, R(z=up)), k(down_t, R(z=down), ease_down), k(n, R(z=up), 'easeInOutSine')]
    tip = [k(0, R(z=tip_down * 0.3)), k(2, R(z=tip_up), 'easeOutQuad'), k(down_t + 1, R(z=tip_down), 'easeInQuad'),
           k(down_t + 4, R(y=30, z=18), 'easeInOutSine'), k(n, R(z=tip_down * 0.3), 'easeInOutSine')]
    return w, tip


# ==================================================================================================================
# Ground life cycle
# ==================================================================================================================
def parked():
    """Unpiloted: crouched 3 px (the lantern's floor 3 px off the ground), wings folded along its sides, the head
    tucked down and round, the floor closed under the empty collar, lamps off. Alive: a slow breath, a tail flick at
    40-46, a feather twitch of the left wingtip at 60, the visor's standby blip."""
    return {'length': 80, 'loop': 'loop', 'pilot': False, 'feet': planted(), 'bones': merge(
        hips([k(0, P(y=PARK_HIPS)), k(40, P(y=PARK_HIPS - 0.4), 'easeInOutSine'), k(80, P(y=PARK_HIPS), 'easeInOutSine')]),
        torso([k(0, R(x=-4)), k(44, R(x=-4.5), 'easeInOutSine'), k(80, R(x=-4), 'easeInOutSine')]),
        {'sensor': {'rotation': [k(0, R(x=-25, y=20)), k(40, R(x=-26, y=22), 'easeInOutSine'), k(80, R(x=-25, y=20), 'easeInOutSine')]},
         'tail': {'rotation': [k(0, R(x=-4)), k(40, R(x=-4)), k(42, R(x=-4, y=8), 'easeOutQuad'), k(44, R(x=-4, y=-5), 'easeInOutSine'),
                               k(46, R(x=-4), 'easeInOutSine'), k(80, R(x=-4))]},
         'restraint': {'rotation': const(R())}},
        wings(const(FOLD['wing']), [k(0, FOLD['tip']), k(60, FOLD['tip']), k(62, R(y=150, z=-12), 'easeOutQuad'),
                                    k(65, FOLD['tip'], 'easeInQuad'), k(80, FOLD['tip'])]),
        lamps([(0, 0), (1, 1), (3, 0)], [(0, 0)])),
        'effects': [(1, 'sound', snd('standby_blip', 0.15, 1.4), None), (42, 'sound', snd('feather_ruffle', 0.15, 1.1), None)]}


def board():
    """Controller "cockpit": the lantern's floor lowers on its cables to the ground (it is the lift), the jar slides
    onto it from behind (where the pilot comes from), the floor hoists it up into the lantern until the lid meets the
    brass lip (seated, contact 15), a 0.4 px bounce, the latch."""
    b = {'jar_anchor': {'position': [k(0, P(y=-6, z=16)), k(4, P(y=-6, z=16)), k(9, P(y=-6), 'easeInOutSine'),
                                     k(15, P(), 'easeInOutCubic'), k(16, P(y=0.4), 'easeOutQuad'), k(17, P(), 'easeInQuad')]},
         'restraint': {'position': [k(0, P()), k(1, P(y=0.4), 'easeOutQuad'), k(5, P(y=-6), 'easeInOutCubic'), k(9, P(y=-6)),
                                    k(15, P(), 'easeInOutCubic'), k(16, P(y=0.4), 'easeOutQuad'), k(17, P(), 'easeInQuad')]}}
    return {'length': 22, 'loop': 'hold', 'bones': b,
            'effects': [(1, 'sound', snd('floor_release', 0.5, 1.0), None), (2, 'sound', snd('cable_winch', 0.4, 0.9), None),
                        (9, 'sound', snd('jar_seat', 0.5, 1.0), None), (10, 'sound', snd('cable_winch', 0.5, 1.1), None),
                        (15, 'sound', snd('floor_latch', 0.7, 1.0), None), (16, 'particle', 'cloud', 'fx_hull')]}


def boot():
    """Controller "cockpit": the visor lights (2), the nav lamps (4: red left, green right, steady); the head sweeps;
    then the wings spread full (6-14, a 6-degree overshoot), hold with a shiver (14-24) and fold (24-34); the tail fans."""
    w = [k(0, FOLD['wing']), k(5, R(y=88, z=-32), 'easeOutQuad'), k(12, R(y=-6, z=-8), 'easeOutCubic'), k(14, R(z=-4), 'easeInOutSine'),
         k(18, R(z=-5)), k(20, R(z=-3)), k(22, R(z=-5)), k(24, R(z=-4)), k(33, FOLD['wing'], 'easeInOutCubic')]
    tip = [k(0, FOLD['tip']), k(7, FOLD['tip']), k(14, R(z=-6), 'easeOutCubic'), k(16, R(z=-2), 'easeInOutSine'),
           k(24, R(z=-2)), k(30, FOLD['tip'], 'easeInOutCubic')]
    b = merge(wings(w, tip, lag=1), lamps([(0, 0), (2, 1)], [(0, 0), (4, 1)]), {
        'sensor': {'rotation': [k(0, R(x=-25, y=20)), k(6, R(), 'easeInOutCubic'), k(9, R(y=-3), 'easeOutQuad'),
                                k(13, R(y=32), 'easeInOutCubic'), k(15, R(y=28), 'easeInOutSine'), k(20, R(y=-32), 'easeInOutCubic'),
                                k(22, R(y=-28), 'easeInOutSine'), k(27, R(), 'easeInOutSine')]},
        'tail': {'rotation': [k(0, R(x=-4)), k(12, R(x=6), 'easeOutCubic'), k(16, R(x=4), 'easeInOutSine'), k(30, R(), 'easeInOutSine')]}})
    return {'length': 40, 'loop': 'hold', 'bones': b,
            'effects': [(0, 'sound', snd('servo_chord', 0.7, 0.9), None), (2, 'sound', snd('relay_click', 0.4, 1.0), None),
                        (4, 'sound', snd('relay_click', 0.4, 1.1), None), (10, 'sound', snd('wing_unfurl', 0.6, 1.0), None),
                        (13, 'sound', snd('servo_tick', 0.3, 1.0), None), (20, 'sound', snd('servo_tick', 0.3, 0.9), None),
                        (28, 'sound', snd('wing_fold', 0.5, 1.0), None), (39, 'timeline', 'boot:done', None)]}


def boot_legs():
    return {'length': 30, 'loop': 'hold', 'feet': planted(), 'bones': merge(
        hips([k(0, P(y=PARK_HIPS)), k(3, P(y=PARK_HIPS - 0.4), 'easeOutQuad'), k(12, P(y=0.6), 'easeInOutCubic'),
              k(15, P(), 'easeInOutSine'), k(22, P()), k(24, P(y=-0.6), 'easeInQuad'), k(28, P(), 'easeOutQuad')]),
        torso([k(0, R(x=-4)), k(14, R(x=1), 'easeInOutCubic'), k(17, R(), 'easeInOutSine')]), folded()),
        'effects': [(3, 'sound', snd('hydraulic_stand', 0.5, 1.2), None), (24, 'timeline', 'tap:both', None)]}


def shutdown():
    return {'length': 20, 'loop': 'hold', 'feet': planted(), 'bones': merge(
        hips([k(0, P()), k(3, P(y=0.4), 'easeOutQuad'), k(14, P(y=PARK_HIPS - 0.3), 'easeInQuad'), k(18, P(y=PARK_HIPS), 'easeInOutSine')]),
        torso([k(0, R()), k(16, R(x=-4), 'easeInOutSine')]),
        {'sensor': {'rotation': [k(0, R()), k(6, R()), k(18, R(x=-25, y=20), 'easeInOutSine')]}},
        wings([k(0, FOLD['wing']), k(8, R(y=85, z=-33), 'easeInOutSine'), k(14, FOLD['wing'], 'easeInOutSine')], const(FOLD['tip'])),
        lamps([(0, 1), (10, 0)], [(0, 1), (6, 0)])),
        'effects': [(0, 'sound', snd('power_down', 0.6, 0.9), None), (14, 'sound', snd('sigh', 0.5, 1.1), None)]}


def idle():
    """Standing, wings folded: a 0.6 px breath, the folded wings lifting 1 degree with it, the tail swaying."""
    return {'length': 60, 'loop': 'loop', 'feet': planted(), 'bones': merge(
        hips([k(0, P()), k(30, P(y=-0.6), 'easeInOutSine'), k(60, P(), 'easeInOutSine')]),
        torso([k(0, R(x=0.3)), k(36, R(x=-0.6), 'easeInOutSine'), k(60, R(x=0.3), 'easeInOutSine')]),
        wings([k(0, FOLD['wing']), k(32, R(y=85, z=-27), 'easeInOutSine'), k(60, FOLD['wing'], 'easeInOutSine')], const(FOLD['tip'])),
        {'tail': {'rotation': [k(0, R()), k(20, R(y=3), 'easeInOutSine'), k(40, R(y=-3), 'easeInOutSine'), k(60, R(), 'easeInOutSine')]}}),
        'effects': []}


def idle_tic():
    """Every 200-300 idle ticks: it preens. The left wing half-opens, shakes three times, folds; the head turns to it."""
    w = [k(0, FOLD['wing']), k(3, R(y=88, z=-32), 'easeOutQuad'), k(9, R(y=30, z=-10), 'easeInOutCubic'),
         k(11, R(y=30, z=-2), 'easeInOutSine'), k(13, R(y=30, z=-12), 'easeInOutSine'), k(15, R(y=30, z=-2), 'easeInOutSine'),
         k(17, R(y=30, z=-12), 'easeInOutSine'), k(19, R(y=30, z=-6), 'easeInOutSine'), k(28, FOLD['wing'], 'easeInOutCubic')]
    tip = [k(0, FOLD['tip']), k(9, R(y=40), 'easeInOutCubic'), k(19, R(y=40)), k(27, FOLD['tip'], 'easeInOutCubic')]
    b = merge({'wing_l': {'rotation': w}, 'wingtip_l': {'rotation': tip},
               'wing_r': {'rotation': mirror_keys(const(FOLD['wing']), 'rotation')},
               'wingtip_r': {'rotation': mirror_keys(const(FOLD['tip']), 'rotation')},
               'sensor': {'rotation': [k(0, R()), k(6, R(y=50, x=-10), 'easeInOutSine'), k(20, R(y=50, x=-10)), k(30, R(), 'easeInOutSine')]}},
              torso([k(0, R()), k(8, R(z=-3), 'easeInOutSine'), k(20, R(z=-3)), k(28, R(), 'easeInOutSine')]))
    return {'length': 36, 'loop': 'once', 'feet': planted(), 'bones': b,
            'effects': [(9, 'sound', snd('wing_unfurl', 0.3, 1.2), None), (11, 'sound', snd('feather_ruffle', 0.4, 1.0), None),
                        (15, 'sound', snd('feather_ruffle', 0.4, 1.05), None), (24, 'sound', snd('wing_fold', 0.3, 1.1), None)]}


WALK_N, WALK_STANCE = 16, 9
WALK_REACH = V * WALK_STANCE / 2.0
BOB = [-0.8, -1.4, -1.2, -0.6, 0.0, 0.4, 0.5, 0.2]


def walk_foot(s):
    s %= WALK_N
    if s <= WALK_STANCE:
        z = G.REST_ANKLE[0] - WALK_REACH + V * s
        ang = {0: 6}.get(int(s), 0)
        if s >= WALK_STANCE - 1:
            ang = -12 * (s - (WALK_STANCE - 1))
        return on_toe(z, ang)
    u = (s - WALK_STANCE) / float(WALK_N - WALK_STANCE)
    z = G.REST_ANKLE[0] + WALK_REACH - 2 * WALK_REACH * (1 - math.cos(math.pi * u)) / 2
    up = 5.0 * math.sin(math.pi * min(1.0, u ** 0.8))
    ang = -14 * (1 - u) ** 2
    zz, yy, aa = on_toe(z, ang)
    return (zz, yy + up, aa)


def walk():
    """A long-legged stalk at the design speed (8 ticks a step; playback = measured / 0.2592 blocks a tick), the head
    bobbing like a heron's, the folded wings bouncing a beat behind each plant."""
    feet = lambda s, t: walk_foot(t if s == 'l' else t - WALK_N // 2)
    ticks = range(WALK_N + 1)
    head = lambda t: [-0.0, -1.5, -1.0, 0.5, 1.0, 1.0, 0.5, 0.0][t % 8]
    return {'length': WALK_N, 'loop': 'loop', 'feet': feet, 'bones': merge(
        hips([k(t, P(y=BOB[t % 8])) for t in ticks], [k(t, R(z=round(1.2 * math.cos(2 * math.pi * (t - 2) / WALK_N), 3))) for t in ticks]),
        torso([k(t, R(x=-2 + 0.4 * BOB[(t - 1) % 8], z=round(1.5 * math.cos(2 * math.pi * (t - 3) / WALK_N), 3))) for t in ticks]),
        {'sensor': {'position': [k(t, P(z=head(t))) for t in ticks]}},
        wings([k(t, R(y=85, z=-28 + 2.5 * (1 if t % 8 in (2, 3) else 0))) for t in ticks], const(FOLD['tip']))),
        'effects': [(0, 'timeline', 'step:l', None), (8, 'timeline', 'step:r', None)]}


def turn(direction):
    a = REST_FOOT_T
    up = (a[0] - 1, a[1] + 4, 4)
    yaw = 6 if direction == 'l' else -6
    return {'length': 16, 'loop': 'loop',
            'feet': keyed({'l': [k(0, a), k(3, up, 'easeOutCubic'), k(6, a, 'easeInQuad'), k(16, a)],
                           'r': [k(0, a), k(8, a), k(11, up, 'easeOutCubic'), k(14, a, 'easeInQuad'), k(16, a)]}),
            'bones': merge(hips([k(0, P()), k(3, P(y=0.4), 'easeInOutSine'), k(6, P(y=-0.6), 'easeInQuad'), k(11, P(y=0.4), 'easeInOutSine'),
                                 k(14, P(y=-0.6), 'easeInQuad'), k(16, P(), 'easeOutQuad')], const(R(y=yaw * 0.4))),
                           torso(const(R(y=yaw * 0.6))), folded()),
            'effects': [(6, 'timeline', 'step:l', None), (14, 'timeline', 'step:r', None)]}


# ==================================================================================================================
# Flight
# ==================================================================================================================
def takeoff():
    """Jump: it crouches 5 px and raises its wings high (the wind-up, 0-6), holds 2 ticks, then the first great
    down-stroke and the leap together on 8 (the impulse on 8, applied by the pilot's client); the legs shove, then
    trail back; the tail lifts."""
    a = REST_FOOT_T
    g = keyed({s: [k(0, a), k(8, a)] for s in 'lr'})
    r = keyed({s: [k(8, (a[0], -G.HIP_Y + 2, 0)), k(10, (2, -21, 20), 'easeOutCubic'), k(16, FLY_FEET, 'easeInOutSine')] for s in 'lr'}, rel=True)
    w = [k(0, FOLD['wing']), k(4, R(y=20, z=-30), 'easeOutCubic'), k(6, R(z=-55), 'easeInOutSine'), k(8, R(z=-56)),
         k(10, R(z=35), 'easeInExpo'), k(16, R(z=-30), 'easeInOutSine')]
    tip = [k(0, FOLD['tip']), k(5, R(y=40, z=-10), 'easeOutCubic'), k(8, R(z=-20), 'easeInOutSine'), k(11, R(z=15), 'easeInQuad'),
           k(16, R(z=-5), 'easeInOutSine')]
    return {'length': 16, 'loop': 'hold', 'feet': lambda s, t: g(s, t) if t <= 8 else r(s, t), 'bones': merge(
        hips([k(0, P()), k(2, P(y=0.5), 'easeOutQuad'), k(6, P(y=-6), 'easeInOutCubic'), k(8, P(y=-6.2)), k(10, P(y=2), 'easeInExpo'),
              k(16, P(), 'easeInOutSine')]),
        torso([k(0, R()), k(6, R(x=-6), 'easeInOutCubic'), k(10, R(x=6), 'easeInExpo'), k(16, R(x=2), 'easeInOutSine')]),
        wings(w, tip), {'tail': {'rotation': [k(0, R()), k(8, R(x=8), 'easeInOutSine'), k(12, R(x=-6), 'easeOutCubic'), k(16, R(), 'easeInOutSine')]}}),
        'effects': [(2, 'sound', snd('wing_unfurl', 0.5, 1.0), None), (8, 'timeline', 'takeoff:push', None),
                    (10, 'sound', snd('wingbeat', 0.8, 0.9), None)]}


def flap(n=16, down_t=7, up=-40, down=32, name_snd='wingbeat'):
    """Loop, powered flight: one wingbeat of 16 ticks, a 7-tick down-stroke (accelerating, easeInQuad) and a 9-tick
    recovery, the wingtips lagging (trailing up on the stroke, whipping down at the bottom, swept back on the
    recovery). The body rises 1.5 px on the down-stroke and sinks on the recovery, the tail pitching against it; the
    legs trail. The game ties playback to climb rate and airspeed."""
    w, tip = flap_keys(n, up, down, down_t)
    return {'length': n, 'loop': 'loop', 'feet': fly_feet(), 'bones': merge(
        wings(w, tip),
        hips([k(0, P(y=-0.6)), k(3, P(y=-0.2), 'easeInOutSine'), k(down_t, P(y=2), 'easeInQuad'), k(down_t + 2, P(y=1.6), 'easeOutQuad'),
              k(min(n - 2, down_t + 6), P(y=-1.5), 'easeInOutSine'), k(n, P(y=-0.6), 'easeInOutSine')]),
        torso([k(0, R(x=-3)), k(down_t + 1, R(x=-1.5), 'easeInOutSine'), k(n, R(x=-3), 'easeInOutSine')]),
        {'tail': {'rotation': [k(0, R(x=2)), k(down_t + 1, R(x=-3), 'easeInOutSine'), k(n, R(x=2), 'easeInOutSine')]}},
        talons(const(R(x=-20)))),
        'effects': [(down_t - 1, 'sound', snd(name_snd, 0.6, 1.0), None)]}


def hover():
    """Loop, hanging in the air (climbing straight up or holding station): faster, shallower beats (12 ticks), the
    body upright and the tail down and fanned, the legs dangling."""
    c = flap(12, 5, -30, 25, 'wingbeat_light')
    c['feet'] = keyed({s: [k(0, (-2, -21, 10)), k(6, (-1, -21.5, 12), 'easeInOutSine'), k(12, (-2, -21, 10), 'easeInOutSine')] for s in 'lr'}, rel=True)
    c['bones']['torso'] = {'rotation': [k(0, R(x=12)), k(6, R(x=13), 'easeInOutSine'), k(12, R(x=12), 'easeInOutSine')]}
    c['bones']['tail'] = {'rotation': const(R(x=18))}
    return c


def glide():
    """Loop, gliding: wings spread with 6 degrees of dihedral, the tips flexing 2 degrees, a slow 40-tick sway, the
    tail trimming, the legs trailing."""
    return {'length': 40, 'loop': 'loop', 'feet': fly_feet(), 'bones': merge(
        wings([k(0, R(z=-6)), k(20, R(z=-7, x=1), 'easeInOutSine'), k(40, R(z=-6), 'easeInOutSine')],
              [k(0, R(z=-2)), k(10, R(z=-4), 'easeInOutSine'), k(30, R(z=0), 'easeInOutSine'), k(40, R(z=-2), 'easeInOutSine')]),
        torso([k(0, R(x=-4)), k(20, R(x=-3, z=1), 'easeInOutSine'), k(40, R(x=-4), 'easeInOutSine')]),
        {'tail': {'rotation': [k(0, R()), k(20, R(x=2), 'easeInOutSine'), k(40, R(), 'easeInOutSine')]}}, talons(const(R(x=-20)))),
        'effects': [(0, 'sound', snd('wind_rush', 0.25, 1.0), None)]}


def bank(direction):
    """Controller "bank" (tail only; the body's roll into the turn is code's): the tail rolls into the turn and the
    rudder swings, with a small wind-up and settle."""
    s = 1 if direction == 'l' else -1
    return {'length': 8, 'loop': 'hold', 'bones': {'tail': {'rotation': [
        k(0, R()), k(2, R(y=-3 * s), 'easeOutQuad'), k(6, R(y=16 * s, z=10 * s), 'easeOutCubic'), k(8, R(y=14 * s, z=9 * s), 'easeInOutSine')]}},
        'effects': []}


def flare():
    """Once, before touching down: the body pitches up 25 degrees, the wings sweep forward and cup (one big braking
    stroke, held up and forward), the tail drops and fans, the legs reach forward for the ground."""
    feet = keyed({s: [k(0, FLY_FEET), k(6, (-7, -20, -10), 'easeInOutCubic'), k(14, (-6, -21, -6), 'easeInOutSine')] for s in 'lr'}, rel=True)
    return {'length': 14, 'loop': 'hold', 'feet': feet, 'bones': merge(
        wings([k(0, R(z=-6)), k(3, R(z=-40, y=-10), 'easeOutCubic'), k(7, R(z=10, y=-20, x=-15), 'easeInQuad'),
               k(10, R(z=-25, y=-18, x=-15), 'easeInOutSine'), k(14, R(z=-20, y=-18, x=-15), 'easeInOutSine')],
              [k(0, R(z=-2)), k(4, R(z=-12), 'easeOutCubic'), k(8, R(z=14), 'easeInQuad'), k(14, R(z=8), 'easeInOutSine')]),
        torso([k(0, R(x=-4)), k(8, R(x=25), 'easeInOutCubic'), k(14, R(x=22), 'easeInOutSine')]),
        {'tail': {'rotation': [k(0, R()), k(6, R(x=28), 'easeOutCubic'), k(14, R(x=24), 'easeInOutSine')]}},
        talons([k(0, R(x=-20)), k(8, R(x=25), 'easeOutCubic')])),
        'effects': [(7, 'sound', snd('wing_brake', 0.7, 0.9), None)]}


def land(heavy=False):
    """Contact 0: the legs take it (hips -4 / -6: the floor stops 1 px off the ground), the wings flick up and fold
    over 8-16, the body pitches down out of the flare; heavy throws the wings open again with an elastic settle."""
    d, L = (6.0, 22) if heavy else (4.0, 18)
    w = ([k(0, R(z=-20, y=-18)), k(3, R(z=-40, y=-5), 'easeOutCubic'), k(6, R(z=-30), 'easeInOutSine'),
          k(16, FOLD['wing'], 'easeInOutCubic'), k(L, FOLD['wing'], 'easeInElastic', [3])] if heavy else
         [k(0, R(z=-20, y=-18)), k(3, R(z=-30), 'easeOutCubic'), k(14, FOLD['wing'], 'easeInOutCubic'), k(L, FOLD['wing'])])
    tip = [k(0, R(z=8)), k(4, R(z=-10), 'easeOutCubic'), k(14, FOLD['tip'], 'easeInOutCubic'), k(L, FOLD['tip'])]
    return {'length': L, 'loop': 'once', 'feet': planted(), 'bones': merge(
        hips([k(0, P()), k(3, P(y=-d), 'easeOutCubic'), k(5, P(y=-d + 0.4)), k(L - 6, P(y=0.6), 'easeInOutSine'), k(L, P(), 'easeInOutSine')]),
        torso([k(0, R(x=20)), k(4, R(x=-6), 'easeOutCubic'), k(L - 4, R(), 'easeInOutSine')]),
        {'tail': {'rotation': [k(0, R(x=24)), k(5, R(x=-4), 'easeOutCubic'), k(L - 4, R(), 'easeInOutSine')]}},
        wings(w, tip, lag=1), talons([k(0, R(x=25)), k(1, R(), 'easeInQuad')])),
        'effects': [(0, 'timeline', 'land:heavy' if heavy else 'land:light', None), (10, 'sound', snd('wing_fold', 0.5, 1.0), None)]}


def stall():
    """Once, the battery goes flat in the air: two weak half-beats (15 degrees, slower each time), then the wings droop
    to 45 degrees below level with the tips trailing up, the nose drops 25 degrees with a wobble, the legs dangle, the
    visor goes dark. It holds that broken shape; the game lets it sink (and plays stall_loop)."""
    w = [k(0, R(z=-20)), k(4, R(z=-5), 'easeInQuad'), k(9, R(z=-15), 'easeInOutSine'), k(14, R(z=0), 'easeInQuad'),
         k(20, R(z=-6), 'easeInOutSine'), k(30, R(z=45), 'easeInQuad'), k(32, R(z=40), 'easeOutQuad'), k(34, R(z=45), 'easeInOutSine')]
    tip = [k(0, R()), k(5, R(z=10), 'easeInQuad'), k(14, R(z=12), 'easeInQuad'), k(30, R(y=20, z=-30), 'easeInQuad'),
           k(34, R(y=20, z=-25), 'easeInOutSine')]
    feet = keyed({s: [k(0, FLY_FEET), k(20, (-1, -21, 5), 'easeInOutSine'), k(34, (-2, -22, -10), 'easeInOutSine')] for s in 'lr'}, rel=True)
    return {'length': 34, 'loop': 'hold', 'feet': feet, 'bones': merge(
        wings(w, tip),
        torso([k(0, R(x=-3)), k(20, R(x=-8), 'easeInOutSine'), k(28, R(x=-25, z=6), 'easeInQuad'), k(31, R(x=-22, z=-4), 'easeInOutSine'),
               k(34, R(x=-25, z=3), 'easeInOutSine')]),
        {'tail': {'rotation': [k(0, R()), k(26, R(y=10, x=-10), 'easeInOutSine'), k(30, R(y=-8, x=-10), 'easeInOutSine'), k(34, R(y=6, x=-10), 'easeInOutSine')]}},
        lamps([(0, 1), (18, 0)], [(0, 1)])),
        'effects': [(4, 'sound', snd('wingbeat_weak', 0.4, 0.8), None), (14, 'sound', snd('wingbeat_weak', 0.3, 0.7), None),
                    (18, 'sound', snd('power_down', 0.6, 0.8), None), (22, 'timeline', 'stall', None)]}


def stall_loop():
    """Loop after the stall: wings drooped and fluttering 6 degrees in the fall, the nose down, a slow wobble."""
    n = 16
    return {'length': n, 'loop': 'loop', 'feet': keyed({s: [k(0, (-2, -22, -10))] for s in 'lr'}, rel=True), 'bones': merge(
        wings([k(0, R(z=45)), k(4, R(z=39), 'easeInOutSine'), k(8, R(z=45), 'easeInOutSine'), k(12, R(z=40), 'easeInOutSine'), k(n, R(z=45), 'easeInOutSine')],
              [k(0, R(y=20, z=-25)), k(8, R(y=20, z=-32), 'easeInOutSine'), k(n, R(y=20, z=-25), 'easeInOutSine')], lag=2),
        torso([k(0, R(x=-25, z=3)), k(8, R(x=-23, z=-3), 'easeInOutSine'), k(n, R(x=-25, z=3), 'easeInOutSine')]),
        lamps([(0, 0)], [(0, 1)])), 'effects': [(0, 'sound', snd('wind_rush', 0.3, 0.8), None)]}


def fall():
    return {'length': 16, 'loop': 'loop', 'feet': keyed({s: [k(0, (-3, -20, 0)), k(8, (-4, -21, -5), 'easeInOutSine'), k(16, (-3, -20, 0), 'easeInOutSine')] for s in 'lr'}, rel=True),
            'bones': merge(wings([k(0, R(y=30, z=-20)), k(8, R(y=30, z=-28), 'easeInOutSine'), k(16, R(y=30, z=-20), 'easeInOutSine')],
                                 [k(0, R(y=40)), k(4, R(y=40, z=-8), 'easeInOutSine'), k(12, R(y=40, z=6), 'easeInOutSine'), k(16, R(y=40), 'easeInOutSine')])),
            'effects': []}


# ---- the Talon Lift (creatures only) ---------------------------------------------------------------------------------
def talon_grab():
    """Drive while flying low over a creature. Wind-up: the body dips 3 px and the legs draw back (3-5), the talons
    spread (5-7), a 2-tick hold with the legs swung down, forward and 12 degrees outward (so the feet clear the lantern
    from behind); CLOSE on 8 (easeInExpo over 2 ticks; the server takes hold: the creature rides at fx_cargo); then the
    heave: the next down-stroke at full amplitude and the body up 4 px."""
    feet = keyed({s: [k(0, FLY_FEET), k(4, (9, -17, 40), 'easeOutQuad'), k(6, (-6, -23, -15), 'easeInOutCubic'), k(8, (-6, -23, -15)),
                      k(10, (-4, -22, -8), 'easeInExpo'), k(16, (-3, -22, -5), 'easeInOutSine')] for s in 'lr'}, rel=True)
    w = [k(0, R(z=-40)), k(5, R(z=-50), 'easeInOutSine'), k(8, R(z=-52)), k(12, R(z=40), 'easeInQuad'), k(16, R(z=-40), 'easeInOutSine')]
    c = {'length': 16, 'loop': 'hold', 'feet': feet, 'bones': merge(
        wings(w, [k(0, R()), k(6, R(z=-15), 'easeInOutSine'), k(13, R(z=18), 'easeInQuad'), k(16, R(), 'easeInOutSine')]),
        hips([k(0, P()), k(3, P(y=-3), 'easeInOutCubic'), k(8, P(y=-3)), k(12, P(y=4), 'easeInQuad'), k(16, P(y=1), 'easeInOutSine')]),
        torso([k(0, R(x=-3)), k(6, R(x=8), 'easeInOutCubic'), k(12, R(x=-4), 'easeOutQuad'), k(16, R(x=-2), 'easeInOutSine')]),
        {'tail': {'rotation': [k(0, R()), k(6, R(x=15), 'easeInOutSine'), k(9, R(x=-8), 'easeOutQuad'), k(14, R(), 'easeInOutSine')]}},
        talons([k(0, R(x=-20)), k(3, R(x=-20)), k(6, R(x=35), 'easeOutCubic'), k(7, R(x=36)), k(8, R(x=36)), k(10, R(x=-45), 'easeInExpo'),
                k(12, R(x=-40), 'easeOutQuad')])),
        'effects': [(3, 'sound', snd('servo', 0.4, 1.1), None), (10, 'timeline', 'talon:close', None),
                    (12, 'sound', snd('wingbeat_heavy', 0.8, 0.85), None)]}
    c['splay'] = [k(0, 0), k(5, 12, 'easeInOutCubic'), k(12, 12), k(16, 6, 'easeInOutSine')]
    return c


def talon_carry():
    """Loop, flying with a creature in the talons: bigger, slower beats (20 ticks: a 9-tick down-stroke), the body hung
    lower and nose-up against the load, the legs held down, forward and 6 degrees out, a strain shiver at the bottom of
    each beat."""
    w, tip = flap_keys(20, -45, 38, 9, -20, 15)
    feet = keyed({s: [k(0, (-3, -22, -5)), k(10, (-3, -22.5, -5), 'easeInOutSine'), k(20, (-3, -22, -5), 'easeInOutSine')] for s in 'lr'}, rel=True)
    c = {'length': 20, 'loop': 'loop', 'feet': feet, 'bones': merge(
        wings(w, tip),
        hips([k(0, P(y=-1)), k(4, P(y=-0.6), 'easeInOutSine'), k(9, P(y=1.5), 'easeInQuad'), k(10, P(y=1.2)), k(11, P(y=1.5)),
              k(16, P(y=-1.5), 'easeInOutSine'), k(20, P(y=-1), 'easeInOutSine')]),
        torso([k(0, R(x=6)), k(10, R(x=8), 'easeInOutSine'), k(20, R(x=6), 'easeInOutSine')]),
        {'tail': {'rotation': const(R(x=8))}}, talons(const(R(x=-40)))),
        'effects': [(8, 'sound', snd('wingbeat_heavy', 0.7, 0.85), None), (11, 'sound', snd('hydraulic_strain', 0.3, 1.0), None)]}
    c['splay'] = const(6)
    return c


def talon_drop():
    """Once, Drive again: the talons spring open (1, the server lets go), the legs kick back, one relieved light beat
    and a 4 px bob up as the weight goes."""
    feet = keyed({s: [k(0, (-3, -22, -5)), k(4, (8, -18, 35), 'easeOutCubic'), k(12, FLY_FEET, 'easeInOutSine')] for s in 'lr'}, rel=True)
    c = {'length': 12, 'loop': 'hold', 'feet': feet, 'bones': merge(
        talons([k(0, R(x=-40)), k(1, R(x=35), 'easeOutExpo'), k(6, R(x=-20), 'easeInOutSine')]),
        wings([k(0, R(z=-30)), k(4, R(z=25), 'easeInQuad'), k(12, R(z=-35), 'easeInOutSine')]),
        hips([k(0, P(y=-1)), k(3, P(y=4), 'easeOutQuad'), k(12, P(), 'easeInOutSine')])),
        'effects': [(1, 'timeline', 'talon:open', None), (3, 'sound', snd('wingbeat_light', 0.6, 1.1), None)]}
    c['splay'] = [k(0, 6), k(8, 0, 'easeInOutSine')]
    return c


def talon_refused():
    """Once, Drive with nothing to lift (nothing under it, too heavy, a player, no power). The same wind-up (the dip,
    the spread, the legs swung out), the talons SNAP shut on air on 8 (overshooting), a 4-tick hold, then the feet open
    and hang limp 20 degrees lower; an asymmetric shrug (the left wing up 25, the right 10, held 6 ticks) and a tail
    flick; the head tilts. The HUD says why (talon:refused). It never passes through the flap's up pose."""
    feet = keyed({s: [k(0, FLY_FEET), k(4, (9, -17, 40), 'easeOutQuad'), k(6, (-6, -23, -15), 'easeInOutCubic'), k(12, (-6, -23, -15)),
                      k(15, (-3, -24, 20), 'easeInQuad'), k(24, (-3, -24, 20)), k(30, FLY_FEET, 'easeInOutSine')] for s in 'lr'}, rel=True)
    wl = [k(0, R(z=-6)), k(12, R(z=-6)), k(15, R(z=-25), 'easeOutCubic'), k(21, R(z=-24)), k(28, R(z=-6), 'easeInOutSine')]
    wr = [k(0, R(z=-6)), k(12, R(z=-6)), k(15, R(z=-10), 'easeOutCubic'), k(21, R(z=-9)), k(28, R(z=-6), 'easeInOutSine')]
    c = {'length': 30, 'loop': 'once', 'feet': feet, 'bones': merge(
        {'wing_l': {'rotation': wl}, 'wing_r': {'rotation': mirror_keys(wr, 'rotation')}},
        hips([k(0, P()), k(3, P(y=-3), 'easeInOutCubic'), k(8, P(y=-3)), k(10, P(y=-2), 'easeOutQuad'), k(16, P(), 'easeInOutSine')]),
        talons([k(0, R(x=-20)), k(3, R(x=-20)), k(6, R(x=35), 'easeOutCubic'), k(7, R(x=36)), k(8, R(x=-55), 'easeInExpo'),
                k(9, R(x=-45), 'easeOutQuad'), k(12, R(x=-45)), k(15, R(x=20), 'easeOutCubic'), k(24, R(x=20)), k(30, R(x=-20), 'easeInOutSine')]),
        {'sensor': {'rotation': [k(0, R()), k(12, R()), k(15, R(z=16), 'easeInOutSine'), k(21, R(z=16)), k(27, R(), 'easeInOutSine')]},
         'tail': {'rotation': [k(0, R()), k(15, R()), k(16, R(y=14), 'easeOutQuad'), k(18, R(y=-8), 'easeInOutSine'), k(21, R(), 'easeInOutSine')]}}),
        'effects': [(3, 'sound', snd('servo', 0.4, 1.1), None), (8, 'sound', snd('talon_snap', 0.6, 1.1), None),
                    (9, 'timeline', 'talon:refused', None), (15, 'sound', snd('wing_shrug', 0.4, 1.0), None)]}
    c['splay'] = [k(0, 0), k(5, 12, 'easeInOutCubic'), k(15, 12), k(24, 0, 'easeInOutSine')]
    return c


# ---- arm verbs (the Hopper's tables at the Kite's aim) ---------------------------------------------------------------
def verbs():
    """The Hopper's tables at the Kite's aim, each with a body answer the pilot sees from behind: on the action tick
    (fire, hit, block, dry click) the tail fans down 15 degrees and settles over 4 ticks."""
    HA.AIM_ARM, HA.AIM_FORE = 55.0, -45.0
    v = HA.verbs_left()
    for name, c in v.items():
        acts = [t for (t, ch, eff, loc) in c['effects'] if ch == 'timeline' and eff.startswith('arm:')]
        if acts:
            t0 = acts[0]
            keys = [k(0, R())] + ([k(t0, R())] if t0 > 0 else []) + [k(t0 + 1, R(x=15), 'easeOutQuad'), k(min(c['length'], t0 + 5), R(), 'easeInElastic', [3])]
            c['bones']['tail'] = {'rotation': keys}
    return v


# ==================================================================================================================
# States
# ==================================================================================================================
def hurt():
    return {'length': 10, 'loop': 'once', 'bones': {
        'root': {'rotation': [k(0, R()), k(1, R(x=1.5, z=-3), 'easeOutExpo'), k(3, R(x=1.5, z=-3)),
                              k(6, R(x=-0.5, z=1), 'easeInOutSine'), k(10, R(), 'easeInOutSine')]},
        'tail': {'rotation': [k(0, R()), k(2, R(y=10), 'easeOutQuad'), k(5, R(y=-4), 'easeInOutSine'), k(8, R(), 'easeInOutSine')]}},
        'effects': [(1, 'particle', 'electric_spark', 'fx_hull')]}


def limp():
    """Flat battery on the ground (speed x 0.3; playback = measured / 0.0778): a slow, uneven walk, the left wing half
    unfolded and dragging its tip, the head low, only the nav lamps lit."""
    n, stance = 24, 16
    reach = V_LIMP * stance / 2.0

    def foot(s, t):
        p = (t - (0 if s == 'l' else n // 2)) % n
        if p <= stance:
            return on_toe(G.REST_ANKLE[0] - reach + V_LIMP * p, 0)
        u = (p - stance) / float(n - stance)
        z = G.REST_ANKLE[0] + reach - 2 * reach * (1 - math.cos(math.pi * u)) / 2
        return (z, G.ANKLE_H + (1.0 if s == 'l' else 3.5) * math.sin(math.pi * u), 0)
    b = merge(hips([k(t, P(y=-1.5 - (1.2 if t % (n // 2) == 2 else 0))) for t in range(n + 1)]),
              torso(const(R(x=-5, z=3))),
              {'wing_l': {'rotation': [k(0, R(y=40, z=40)), k(12, R(y=42, z=42), 'easeInOutSine'), k(n, R(y=40, z=40), 'easeInOutSine')]},
               'wingtip_l': {'rotation': const(R(y=20, z=10))},
               'wing_r': {'rotation': mirror_keys(const(FOLD['wing']), 'rotation')},
               'wingtip_r': {'rotation': mirror_keys(const(FOLD['tip']), 'rotation')},
               'sensor': {'rotation': const(R(x=-15))}}, lamps([(0, 0)], [(0, 1)]))
    return {'length': n, 'loop': 'loop', 'feet': foot, 'bones': b,
            'effects': [(0, 'timeline', 'step:l_drag', None), (12, 'timeline', 'step:r', None), (16, 'sound', snd('wing_scrape', 0.3, 0.9), None)]}


def vent():
    """Overheat: the wings fan open as radiators (3), the body slumps and pants, the wings beating slowly to shed
    heat (one 20-tick fan stroke at a time), steam off the wing roots, the kettle whistle; the wings fold back (82-88)."""
    w = [k(0, FOLD['wing']), k(1, R(y=88, z=-32), 'easeOutQuad'), k(5, R(z=-10), 'easeOutExpo')]
    for i, t in enumerate(range(15, 76, 20)):
        w += [k(t, R(z=10)), k(t + 10, R(z=-12), 'easeInOutSine')] if t + 10 <= 78 else []
    w = sorted({kk[0]: kk for kk in w}.values(), key=lambda x: x[0])
    w += [k(80, R(z=-10), 'easeInOutSine'), k(88, FOLD['wing'], 'easeInOutCubic')]
    b = merge(wings(w, [k(0, FOLD['tip']), k(5, R(z=-4), 'easeOutExpo'), k(80, R(z=-4)), k(86, FOLD['tip'], 'easeInOutCubic')]),
              hips([k(0, P()), k(6, P()), k(18, P(y=-1.5), 'easeInOutSine'), k(33, P(y=-2.4), 'easeInOutSine'), k(48, P(y=-1.5), 'easeInOutSine'),
                    k(63, P(y=-2.4), 'easeInOutSine'), k(78, P(y=-1.8), 'easeInOutSine'), k(90, P(y=0.3), 'easeInOutCubic'), k(100, P(), 'easeInOutSine')]),
              torso([k(0, R()), k(3, R(x=3), 'easeOutQuad'), k(18, R(x=-4), 'easeInOutSine'), k(84, R(x=-4)), k(96, R(), 'easeInOutCubic')]))
    return {'length': 100, 'loop': 'once', 'feet': planted(), 'bones': b,
            'effects': [(3, 'timeline', 'vent:blast', None),
                        (20, 'particle', 'cloud', 'fx_wing_l'), (26, 'particle', 'cloud', 'fx_wing_r'),
                        (40, 'particle', 'cloud', 'fx_wing_l'), (46, 'particle', 'cloud', 'fx_wing_r'),
                        (60, 'particle', 'cloud', 'fx_wing_l'), (66, 'particle', 'cloud', 'fx_wing_r'),
                        (76, 'sound', snd('vent_whistle', 0.6, 1.0), None), (86, 'sound', snd('wing_fold', 0.5, 1.0), None)]}


def eject():
    """The Golden Parachute: klaxon; the lantern's floor is blown down on its cables (to the ground when standing) and
    tips back; the jar is thrown out backward past the tail (the server's eject on 4, from fx_jar_exit: in the air it
    falls clear into its parachute, on the ground the parachute launch starts behind the tail); the machine lurches up
    as the weight goes, the wings flaring, then droops."""
    b = merge(
        {'jar_anchor': {'position': [k(0, P()), k(3, P()), k(4, P(y=-5), 'easeInQuad'), k(6, P(y=-3, z=14), 'easeOutCubic')],
                        'scale': [k(0, S(1), interp='step'), k(6, S(0), interp='step')]},
         'restraint': {'position': [k(0, P()), k(1, P(y=0.4), 'easeOutQuad'), k(3, P(y=-5.5), 'easeInExpo'), k(5, P(y=-5), 'easeOutQuad')],
                       'rotation': [k(0, R()), k(3, R()), k(6, R(x=16), 'easeOutCubic'), k(9, R(x=12), 'easeInOutSine'), k(12, R(x=14), 'easeInOutSine')]}},
        hips([k(0, P()), k(4, P()), k(6, P(y=1.5), 'easeOutQuad'), k(14, P(y=-1), 'easeInOutSine'), k(28, P(y=-1), 'easeInQuad')]),
        wings([k(0, FOLD['wing']), k(4, FOLD['wing']), k(8, R(y=40, z=-20), 'easeOutCubic'), k(20, R(y=60, z=10), 'easeInOutSine'),
               k(28, FOLD['wing'], 'easeInOutSine')], const(FOLD['tip'])),
        lamps([(0, 1), (16, 0)], [(0, 1), (12, 0)]))
    return {'length': 30, 'loop': 'hold', 'feet': planted(), 'bones': b, 'pilot_until': 6,
            'effects': [(0, 'sound', snd('klaxon', 0.8, 1.0), None), (3, 'sound', snd('floor_blow', 0.8, 1.0), None),
                        (4, 'timeline', 'eject:jar', None), (14, 'particle', 'large_smoke', 'fx_hull'), (24, 'sound', snd('sigh', 0.5, 1.1), None)]}


def destroyed():
    """Collapse (40 ticks before the explosion): the wings go first, dropping limp to the ground on either side (4-14),
    the lamps die in steps, the knees buckle, and it falls forward like a kite in a dead calm onto its chest and wings
    (contact 26), the lantern's floor just clear of the ground; smoke."""
    b = merge(
        wings([k(0, FOLD['wing']), k(3, R(y=88, z=-32), 'easeOutQuad'), k(12, R(y=20, z=55), 'easeInQuad'), k(14, R(y=20, z=50), 'easeOutQuad'),
               k(16, R(y=20, z=55), 'easeInQuad')],
              [k(0, FOLD['tip']), k(8, R(y=60), 'easeInOutSine'), k(14, R(y=10, z=25), 'easeInQuad')], lag=1),
        hips([k(0, P()), k(14, P()), k(20, P(y=-4), 'easeInQuad'), k(26, P(y=-6), 'easeInQuad'), k(28, P(y=-5.4), 'easeOutQuad'), k(30, P(y=-6), 'easeInQuad')]),
        {'root': {'rotation': [k(0, R()), k(16, R()), k(26, R(x=-32), 'easeInQuad'), k(28, R(x=-29), 'easeOutQuad'), k(30, R(x=-32), 'easeInQuad')]},
         'sensor': {'rotation': [k(0, R()), k(10, R()), k(24, R(x=-30, y=15), 'easeInQuad')]},
         'tail': {'rotation': [k(0, R()), k(20, R(x=-15), 'easeInQuad'), k(26, R(x=10), 'easeOutQuad')]}},
        lamps([(0, 1), (6, 0), (8, 1), (13, 0)], [(0, 1), (20, 0)]))
    return {'length': 40, 'loop': 'hold', 'feet': planted(), 'bones': b, 'pilot': False,
            'effects': [(0, 'timeline', 'fx:sparks:fx_hull', None), (7, 'particle', 'electric_spark', 'fx_core'),
                        (12, 'sound', snd('wing_slump', 0.6, 0.9), None), (14, 'particle', 'electric_spark', 'fx_hull'),
                        (26, 'timeline', 'destroyed:thud', None), (33, 'particle', 'large_smoke', 'fx_hull')]}


BUILD_SLOTS = [('legs', 62, ['socket_thigh_l', 'socket_thigh_r', 'socket_shin_l', 'socket_shin_r']), ('core', 70, ['socket_core']),
               ('torso_0', 78, ['socket_torso_0']), ('arm_l', 86, ['socket_arm_l']), ('arm_r', 94, ['socket_arm_r']),
               ('sensor', 102, ['socket_sensor'])]


def outward(bone):
    rot = [b for b in G.bones() if b[0] == bone][0][3] or [0, 0, 0]
    x, y, z = [math.radians(a) for a in rot]
    vx, vy, vz = 0.0, 0.0, -1.0
    vy, vz = vy * math.cos(x) - vz * math.sin(x), vy * math.sin(x) + vz * math.cos(x)
    vx, vz = vx * math.cos(y) + vz * math.sin(y), -vx * math.sin(y) + vz * math.cos(y)
    vx, vy = vx * math.cos(z) - vy * math.sin(z), vx * math.sin(z) + vy * math.cos(z)
    return [round(vx, 6), round(vy, 6), round(vz, 6)]


def build():
    """Delivered crouched with the wings folded tight and the floor lowered; stands (44-60); the modules arrive by slot
    (62-107); the wings are tested: spread, held, folded (170-188); the floor swings shut (193); parked."""
    w = [k(0, R(y=86, z=-33)), k(160, R(y=86, z=-33)), k(170, R(z=-6), 'easeOutCubic'), k(172, R(z=-3), 'easeInOutSine'), k(180, R(z=-3)),
         k(188, FOLD['wing'], 'easeInOutCubic'), k(200, FOLD['wing'])]
    b = merge(
        hips([k(0, P(y=PARK_HIPS - 1)), k(42, P(y=PARK_HIPS - 1)), k(44, P(y=PARK_HIPS - 1.4), 'easeOutQuad'), k(54, P(y=0.6), 'easeInOutCubic'),
              k(57, P(), 'easeInOutSine'), k(150, P()), k(153, P(y=-0.8), 'easeOutQuad'), k(158, P(), 'easeInOutSine'),
              k(194, P()), k(199, P(y=PARK_HIPS), 'easeInQuad'), k(200, P(y=PARK_HIPS))]),
        wings(w, [k(0, FOLD['tip']), k(162, FOLD['tip']), k(171, R(z=-4), 'easeOutCubic'), k(180, R(z=-4)), k(186, FOLD['tip'], 'easeInOutCubic'),
                  k(200, FOLD['tip'])], lag=1),
        {'restraint': {'position': [k(0, P(y=-2)), k(189, P(y=-2)), k(190, P(y=-2.4), 'easeOutQuad'), k(193, P(), 'easeInQuad'),
                                    k(194, P(y=0.4), 'easeOutQuad'), k(196, P(), 'easeInQuad')]},
         'sensor': {'rotation': [k(0, R(x=-25, y=20)), k(46, R(x=-25, y=20)), k(58, R(), 'easeInOutCubic'), k(190, R()), k(198, R(x=-25, y=20), 'easeInOutSine')]}},
        lamps([(0, 0), (168, 1), (190, 0)], [(0, 0), (168, 1), (190, 0)]))
    effects = [(44, 'sound', snd('hydraulic_stand', 0.5, 1.2), None), (56, 'timeline', 'build:stand', None),
               (168, 'sound', snd('wing_unfurl', 0.6, 1.0), None), (186, 'sound', snd('wing_fold', 0.5, 1.0), None),
               (193, 'sound', snd('floor_latch', 0.6, 1.0), None), (197, 'sound', snd('ding', 0.4, 1.0), None)]
    for slot, T0, socks in BUILD_SLOTS:
        for sb in socks:
            o = outward(sb)
            b[sb] = {'scale': [k(0, S(0), interp='step'), k(T0, S(1), interp='step')],
                     'position': [k(0, P(*[6 * c for c in o])), k(T0, P(*[6 * c for c in o])), k(T0 + 3, P(), 'easeInQuad'),
                                  k(T0 + 4, P(*[1.0 * c for c in o]), 'easeOutQuad'), k(T0 + 5, P(), 'easeInQuad')]}
        effects.append((T0 + 3, 'timeline', 'build:seat:%s' % slot, None))
    return {'length': 200, 'loop': 'hold', 'feet': planted(), 'bones': b, 'effects': effects, 'pilot': False}


def clips():
    out = [('parked', parked()), ('board', board()), ('boot', boot()), ('boot_legs', boot_legs()), ('shutdown', shutdown()),
           ('idle', idle()), ('idle_tic', idle_tic()), ('walk', walk()), ('turn_l', turn('l')), ('turn_r', turn('r')),
           ('takeoff', takeoff()), ('flap', flap()), ('hover', hover()), ('glide', glide()), ('bank_l', bank('l')),
           ('bank_r', bank('r')), ('flare', flare()), ('land', land(False)), ('land_heavy', land(True)), ('stall', stall()),
           ('stall_loop', stall_loop()),
           ('fall', fall()), ('talon_grab', talon_grab()), ('talon_carry', talon_carry()), ('talon_drop', talon_drop()),
           ('talon_refused', talon_refused())]
    for name, c in verbs().items():
        out.append(('%s_l' % name, c))
        out.append(('%s_r' % name, side_clip(c)))
    out += [('hurt', hurt()), ('limp', limp()), ('vent', vent()), ('eject', eject()), ('destroyed', destroyed()), ('build', build())]
    return out


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

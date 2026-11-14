"""The heavy-biped clip script ("biped_heavy" skeleton): every clip, keyed in ticks, written into a frame's bbmodel.
Run as it is for the Bulwark; art/mecha/colossus/colossus_anim.py runs it for the Colossus (use_frame), adding Siege.

Source of truth for the clips; hand edits to the bbmodel's animations are overwritten. Geometry and rig numbers come
from the frame's geo module (bulwark_geo.py here), and the frame's ANIM dict overrides the Bulwark's frame parameters
(BULWARK_FRAME below): speed, the position scale, the two walks' timing, the hold and reach angles. Position keys in
the shared clips are authored in Bulwark pixels and multiplied by the frame's `scale` before the legs are solved, so a
bigger frame squats, bobs and shrugs in proportion while its own leg lengths are re-solved every tick. The keyframe helpers, GeckoLib easing curves, the arm-verb tables and the right-arm mirroring are
the Hopper's (art/mecha/hopper/hopper_anim.py), imported, so both frames key the same way.

    python art/mecha/bulwark/bulwark_anim.py                 # write the clips into the bbmodel
    python art/mecha/bulwark/bulwark_anim.py --preview OUT   # review copy: eased segments baked per tick, the stand-in
                                                             # jar hidden when nobody is aboard
    python art/mecha/colossus/colossus_anim.py [--preview OUT] # the same clips at the Colossus's sizes, plus Siege

Key tuples: (tick, value, easing, easing_args, interpolation); the easing shapes the move ARRIVING at the key. In
GeckoLib 4.9.3 a settle is easeInElastic / easeInBounce (the names run the other way from easings.net).

Legs are never keyed by hand: each clip gives ankle targets and the generator solves the two-bone leg (knee forward)
every tick from the eased hips curve, so a planted boot stays planted.
"""
import copy
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(os.path.dirname(HERE), 'hopper'))
import bulwark_geo as G  # noqa: E402
import hopper_anim as HA  # noqa: E402  (helpers and the shared arm-verb tables)
from hopper_anim import k, R, P, S, lamp, const, value_at, thin, keyframe, bake, mirror_keys, side_clip, snd, nid  # noqa

MODEL = os.path.join(HERE, 'bulwark.bbmodel')
T = 20.0
V_WALK = 0.11 * 2.16 * 16      # 3.80 px a tick (0.2376 blocks): Bulwark speed 0.11, vanilla ridden ground factor 2.16
V_LIMP = V_WALK * 0.3
REST_FOOT_T = (G.REST_ANKLE[0], G.REST_ANKLE[1], 0.0)
TOE = 10.0                     # ankle to toe tip
HEEL = 7.0                     # ankle to heel


def planted(ang=0.0):
    a = REST_FOOT_T
    return {'l': [k(0, (a[0], a[1], ang))], 'r': [k(0, (a[0], a[1], ang))]}


def on_toe(z, ang):
    lift = TOE * math.sin(math.radians(-ang)) if ang < 0 else HEEL * math.sin(math.radians(ang))
    return (z, G.ANKLE_H + lift, ang)


def solve_legs(clip):
    feet = clip.get('feet')
    if feet is None:
        return
    until = clip.get('ik_until', clip['length'])
    hips = clip['bones'].get('hips', {})
    hpos = sorted(hips.get('position', [k(0, P())]), key=lambda x: x[0])
    hrot = sorted(hips.get('rotation', [k(0, R())]), key=lambda x: x[0])
    rel = clip.get('feet_rel', False)
    splay = clip.get('splay')        # optional {'l': keys, 'r': keys}: each leg rolled out by that many degrees
    out = {s: {'leg': [], 'shin': [], 'foot': [], 'roll': []} for s in 'lr'}
    for t in range(0, int(until) + 1):
        hp = value_at(hpos, t)
        pitch = value_at(hrot, t)[0]
        hip = (hp[2], G.HIP_Y + hp[1])
        for s in 'lr':
            z, y, ang = feet(s, t) if callable(feet) else value_at(sorted(feet[s], key=lambda x: x[0]), t)
            ank = (hip[0] + z, hip[1] + y) if rel else (z, y)
            if splay:
                # the leg's plane leans out: the same drop to the ankle is a longer reach along that plane
                sg = value_at(sorted(splay[s], key=lambda x: x[0]), t)[0]
                ank = (ank[0], hip[1] + (ank[1] - hip[1]) / math.cos(math.radians(sg)))
                out[s]['roll'].append((t, sg if s == 'r' else -sg))
            t1, t2 = G.ik(hip, ank)
            out[s]['leg'].append((t, t1 - pitch - G.REST_THIGH))
            out[s]['shin'].append((t, t2 - t1 - G.REST_SHIN))
            out[s]['foot'].append((t, ang - t2 - G.REST_FOOT))
    loop = clip.get('loop') == 'loop'
    for s in 'lr':
        for part in ('leg', 'shin', 'foot'):
            if splay and part == 'leg':
                xs, zs = dict(out[s]['leg']), dict(out[s]['roll'])
                ts = sorted(set(t for t, _ in thin(out[s]['leg'], loop)) | set(t for t, _ in thin(out[s]['roll'], loop)))
                clip['bones'].setdefault('leg_' + s, {})['rotation'] = [
                    k(t, R(x=round(xs[t], 3), z=round(zs[t], 3))) for t in ts]
                continue
            keys = thin(out[s][part], loop)
            clip['bones'].setdefault('%s_%s' % (part, s), {})['rotation'] = [k(t, R(x=round(v, 3))) for t, v in keys]


def lamps(core, tail):
    return {'lamp_core': {'scale': lamp(core)}, 'lamp_tail': {'scale': lamp(tail)}}


def clamps(keys):
    """The jar clamps: o = degrees open (each post's top swings out, away from the jar), times the frame's clamp_open."""
    if CLAMP_OPEN != 1.0:
        keys = [(t, [round(o[0] * CLAMP_OPEN, 3)], e, a, i) for (t, o, e, a, i) in keys]
    return {'clamp_l': {'rotation': [(t, [0, 0, o[0]], e, a, i) for (t, o, e, a, i) in keys]},
            'clamp_r': {'rotation': [(t, [0, 0, -o[0]], e, a, i) for (t, o, e, a, i) in keys]}}


def vents_to_z(clip):
    """Flaps on the flanks hinge along z: written as negative x (opening), stored as z (left -, right +). A frame
    whose flaps hinge along x (vent_axis 'x', the Colossus's back flaps) keeps them as written."""
    if VENT_AXIS != 'z':
        return
    for side, sign in (('vent_l', 1), ('vent_r', -1)):
        ch = clip['bones'].get(side)
        if ch and 'rotation' in ch:
            ch['rotation'] = [(t, [0, 0, sign * v[0]], e, a, i) for (t, v, e, a, i) in ch['rotation']]


def both_arms(arm_keys, fore_keys=None, hand_keys=None, grip_keys=None, lag=0):
    """Left keys as given; the right arm mirrored (rot y, z negated), optionally a few ticks later."""
    d = {'arm_l': {'rotation': arm_keys}}
    if fore_keys:
        d['forearm_l'] = {'rotation': fore_keys}
    if hand_keys:
        d['hand_l'] = {'rotation': hand_keys}
    if grip_keys:
        d['grip_l'] = {'rotation': grip_keys}
    for b in list(d):
        keys = d[b]['rotation']
        last = max(kk[0] for kk in keys)
        sh = [((kk[0] + lag) if 0 < kk[0] < last else kk[0],) + kk[1:] for kk in keys
              if not (0 < kk[0] < last and kk[0] + lag >= last)]
        d[b.replace('_l', '_r')] = {'rotation': mirror_keys(sh, 'rotation')}
    return d


# ==================================================================================================================
# Poses
# ==================================================================================================================
PARK_HIPS, PARK_TORSO, PARK_SENSOR, PARK_FORE = -6.0, -5.0, -20.0, -10.0
HOLD_ARM, HOLD_FORE, HOLD_IN = 100.0, -50.0, 20.0        # fists at (+-12.6, 31, -21): before the chest, a cow held
                                                          # crosswise between them, its top 2 px under the collar (FK)
HOLD_TORSO = 4.0                                          # leaning back against the load


def hold_arms():
    return both_arms([k(0, R(x=HOLD_ARM, z=HOLD_IN))], [k(0, R(x=HOLD_FORE))], [k(0, R(z=-10))],
                     [k(0, R(x=-35))])


# ==================================================================================================================
# Clips
# ==================================================================================================================
def parked():
    return {
        'length': 80, 'loop': 'loop', 'feet': planted(), 'pilot': False,
        'bones': dict({
            'hips': {'position': [k(0, P(y=PARK_HIPS)), k(40, P(y=PARK_HIPS - 0.5), 'easeInOutSine'),
                                  k(80, P(y=PARK_HIPS), 'easeInOutSine')]},
            'torso': {'rotation': [k(0, R(x=PARK_TORSO)), k(44, R(x=PARK_TORSO - 0.6), 'easeInOutSine'),
                                   k(80, R(x=PARK_TORSO), 'easeInOutSine')]},
            'sensor': {'rotation': [k(0, R(x=PARK_SENSOR)), k(30, R(x=PARK_SENSOR, y=3), 'easeInOutSine'),
                                    k(60, R(x=PARK_SENSOR + 2, y=-2), 'easeInOutSine'), k(80, R(x=PARK_SENSOR), 'easeInOutSine')]},
            'clamp_l': {'rotation': const(R())}, 'clamp_r': {'rotation': const(R())},
            'vent_l': {'rotation': [k(0, R()), k(50, R()), k(52, R(x=-4), 'easeOutQuad'), k(55, R(), 'easeInQuad')]},
        }, **dict(both_arms([k(0, R())], [k(0, R(x=PARK_FORE))]), **lamps([(0, 1), (3, 0)], [(0, 0)]))),
        'effects': [(1, 'sound', snd('standby_blip', 0.15, 1.2), None), (52, 'sound', snd('relay_tick', 0.15, 1.0), None)],
    }


def board():
    """Controller "cockpit": the clamps swing open, the jar is lowered into the collar (seated on 11), the clamps
    close on the glass under the lid (contact 18). Control is live from tick 0."""
    bones = {'jar_anchor': {'position': [k(0, P(y=18, z=-12)), k(5, P(y=18, z=-12)), k(10, P(y=1.5), 'easeInOutSine'),
                                         k(11, P(), 'easeInQuad'), k(12, P(y=0.4), 'easeOutQuad'), k(13, P(), 'easeInQuad')]}}
    bones.update(clamps([k(0, [0]), k(1, [-3], 'easeOutQuad'), k(6, [42], 'easeOutCubic'), k(8, [38], 'easeInOutSine'),
                         k(13, [38]), k(14, [40], 'easeOutQuad'), k(18, [0], 'easeInQuad'), k(19, [-2], 'easeOutQuad'),
                         k(21, [0], 'easeInQuad')]))
    return {'length': 22, 'loop': 'hold', 'bones': bones,
            'effects': [(1, 'sound', snd('clamp_release', 0.5, 0.9), None), (3, 'sound', snd('servo', 0.5, 0.8), None),
                        (11, 'sound', snd('jar_seat', 0.6, 1.0), None), (14, 'sound', snd('servo', 0.5, 0.8), None),
                        (18, 'sound', snd('clamp_close', 0.7, 0.9), None), (19, 'particle', 'cloud', 'fx_hull')]}


def boot():
    """Controller "cockpit" (over live controls): lamps from the cab lamp out to the tail lamps, a deliberate sensor
    sweep (the foreman does not overshoot), each arm lifts and works its grip in turn, the vents cough, the jingle."""
    arm = [k(0, R()), k(12, R()), k(13, R(x=-4), 'easeOutQuad'), k(18, R(x=30), 'easeInOutCubic'),
           k(21, R(x=28), 'easeInOutSine'), k(27, R(), 'easeInOutCubic')]
    fore = [k(0, R()), k(14, R()), k(19, R(x=40), 'easeInOutCubic'), k(22, R(x=37), 'easeInOutSine'), k(28, R(), 'easeInOutCubic')]
    grip = [k(0, R()), k(19, R()), k(20, R(x=-40), 'easeInQuad'), k(22, R(), 'easeOutCubic'), k(23, R(x=-40), 'easeInQuad'),
            k(25, R(), 'easeOutCubic')]
    bones = dict(both_arms(arm, fore, None, grip, lag=4))
    bones.update({
        'sensor': {'rotation': [k(0, R(x=PARK_SENSOR)), k(7, R(), 'easeInOutCubic'), k(13, R(y=30), 'easeInOutCubic'),
                                k(15, R(y=30)), k(21, R(y=-30), 'easeInOutCubic'), k(23, R(y=-30)),
                                k(28, R(), 'easeInOutCubic')]},
        'vent_l': {'rotation': [k(0, R()), k(33, R()), k(35, R(x=-30), 'easeOutQuad'), k(38, R(), 'easeInQuad')]},
        'vent_r': {'rotation': [k(0, R()), k(34, R()), k(36, R(x=-30), 'easeOutQuad'), k(39, R(), 'easeInQuad')]},
    })
    bones.update(lamps([(0, 0), (2, 1)], [(0, 0), (6, 1)]))
    return {'length': 40, 'loop': 'hold', 'bones': bones,
            'effects': [(0, 'sound', snd('servo_chord', 0.7, 0.8), None), (2, 'sound', snd('relay_click', 0.4, 0.9), None),
                        (6, 'sound', snd('relay_click', 0.4, 1.0), None), (13, 'sound', snd('servo_tick', 0.3, 1.0), None),
                        (16, 'sound', snd('servo', 0.5, 0.8), None), (20, 'sound', snd('grip_clack', 0.5, 1.0), None),
                        (21, 'sound', snd('servo_tick', 0.3, 0.9), None), (24, 'sound', snd('grip_clack', 0.5, 0.95), None),
                        (35, 'particle', 'cloud', 'fx_vent_l'), (36, 'particle', 'cloud', 'fx_vent_r'),
                        (38, 'timeline', 'boot:done', None)]}


def boot_legs():
    """Controller "main", while standing during the boot: rises off its parked sag with one long hydraulic groan, then
    stamps its right boot once, satisfied."""
    a = REST_FOOT_T
    feet = {'l': [k(0, a)],
            'r': [k(0, a), k(26, a), k(29, (a[0] - 2, a[1] + 6, 6), 'easeOutCubic'), k(30, (a[0] - 2, a[1] + 6, 6)),
                  k(32, a, 'easeInQuad')]}
    return {'length': 40, 'loop': 'hold', 'feet': feet,
            'bones': {
                'hips': {'position': [k(0, P(y=PARK_HIPS)), k(5, P(y=PARK_HIPS - 0.6), 'easeOutQuad'),
                                      k(17, P(y=0.6), 'easeInOutCubic'), k(21, P(), 'easeInOutSine'),
                                      k(26, P()), k(28, P(y=0.4), 'easeOutQuad'), k(32, P(y=-1.2), 'easeInQuad'),
                                      k(36, P(y=0.2), 'easeOutCubic'), k(40, P(), 'easeInOutSine')]},
                'torso': {'rotation': [k(0, R(x=PARK_TORSO)), k(8, R(x=PARK_TORSO)), k(19, R(x=1), 'easeInOutCubic'),
                                       k(22, R(), 'easeInOutSine'), k(27, R()), k(29, R(z=-2), 'easeOutQuad'),
                                       k(33, R(z=0.6), 'easeInQuad'), k(37, R(), 'easeInOutSine')]},
            },
            'effects': [(5, 'sound', snd('hydraulic_stand', 0.7, 0.8), None), (32, 'timeline', 'stomp:r', None)]}


def shutdown():
    return {'length': 24, 'loop': 'hold', 'feet': planted(),
            'bones': dict({
                'hips': {'position': [k(0, P()), k(3, P(y=0.5), 'easeOutQuad'), k(17, P(y=PARK_HIPS - 0.6), 'easeInQuad'),
                                      k(22, P(y=PARK_HIPS), 'easeInOutSine')]},
                'torso': {'rotation': [k(0, R()), k(5, R()), k(19, R(x=PARK_TORSO - 0.5), 'easeInOutSine'),
                                       k(24, R(x=PARK_TORSO), 'easeInOutSine')]},
                'sensor': {'rotation': [k(0, R()), k(4, R()), k(18, R(x=PARK_SENSOR), 'easeInOutSine')]},
            }, **dict(both_arms([k(0, R())], [k(0, R()), k(10, R(x=PARK_FORE), 'easeInOutSine')], lag=1),
                      **lamps([(0, 1), (8, 0)], [(0, 1), (4, 0)]))),
            'effects': [(0, 'sound', snd('power_down', 0.7, 0.8), None), (17, 'sound', snd('sigh', 0.6, 0.8), None),
                        (18, 'particle', 'cloud', 'fx_vent_l')]}


def idle():
    return {'length': 60, 'loop': 'loop', 'feet': planted(),
            'bones': {'hips': {'position': [k(0, P()), k(30, P(y=-0.8), 'easeInOutSine'), k(60, P(), 'easeInOutSine')]},
                      'torso': {'rotation': [k(0, R(x=0.3)), k(8, R(x=0.4), 'easeInOutSine'), k(38, R(x=-0.9), 'easeInOutSine'),
                                             k(60, R(x=0.3), 'easeInOutSine')]},
                      'arm_l': {'rotation': [k(0, R()), k(34, R(x=-1.5), 'easeInOutSine'), k(60, R(), 'easeInOutSine')]},
                      'arm_r': {'rotation': [k(0, R()), k(36, R(x=-1.5), 'easeInOutSine'), k(60, R(), 'easeInOutSine')]}},
            'effects': []}


def idle_tic():
    """Every 200-300 idle ticks: the foreman cracks its neck, rolling the whole torso one way, then the other."""
    return {'length': 40, 'loop': 'once', 'feet': planted(),
            'bones': {'torso': {'rotation': [k(0, R()), k(3, R(z=-1), 'easeOutQuad'), k(9, R(z=6), 'easeInOutCubic'),
                                             k(11, R(z=5), 'easeInOutSine'), k(13, R(z=5)), k(21, R(z=-6), 'easeInOutCubic'),
                                             k(23, R(z=-5), 'easeInOutSine'), k(25, R(z=-5)), k(33, R(z=0.6), 'easeInOutCubic'),
                                             k(37, R(), 'easeInOutSine')]},
                      'sensor': {'rotation': [k(0, R()), k(10, R(z=-4), 'easeInOutSine'), k(22, R(z=4), 'easeInOutSine'),
                                              k(34, R(), 'easeInOutSine')]}},
            'effects': [(10, 'sound', snd('neck_crack', 0.5, 0.9), None), (22, 'sound', snd('neck_crack', 0.5, 0.8), None)]}


# ---- the long, heavy walk ------------------------------------------------------------------------------------------
WALK_N = 24        # two steps of 12 ticks: a step of 12 x 3.80 px = 45.6 px = 2.85 blocks
WALK_STANCE = 12   # each boot down 12 ticks (duty 0.5); the heel-toe roll gives the overlap
WALK_REACH = V_WALK * WALK_STANCE / 2.0
# the hips per tick of each step: the drop and catch on the plant (lowest 2 ticks after it), the slow recovery
BOB = [-1.5, -2.6, -3.0, -2.4, -1.6, -0.8, -0.2, 0.2, 0.3, 0.1, -0.3, -0.8]


def stride_foot(s, W, n, stance, reach):
    """One boot through a cycle of n ticks: down for `stance` ticks sweeping back at the walk speed (a heel strike, the
    roll onto the toe at the end), then the swing: lifted `lift` px (frame px), the passing pose delayed by
    `swing_delay` (1 = even)."""
    s = s % n
    if s <= stance:
        z = G.REST_ANKLE[0] - reach + V_WALK * s
        ang = {0: W['plant_ang'][0], 1: W['plant_ang'][1]}.get(int(s), 0)
        if s >= stance - 2:
            ang = -10 * (s - (stance - 2))
        return on_toe(z, ang)
    u = (s - stance) / float(n - stance)
    ud = u ** W['swing_delay']
    z = G.REST_ANKLE[0] + reach - 2 * reach * (1 - math.cos(math.pi * ud)) / 2
    up = W['lift'] * math.sin(math.pi * min(1.0, u ** W['up_exp']))
    ang = W['swing_ang'][0] * (1 - u) ** 2 + W['swing_ang'][1] * u ** 3
    zz, yy, aa = on_toe(z, ang) if ang < 0 else (z, G.ANKLE_H, ang)
    return (zz, yy + up, aa)


def stride(W, bob, carry=False):
    """A walk cycle from a frame's walk parameters: the hull drops into each plant and is caught (bob, one entry a
    tick of a step), rolls over the planted leg (the torso a few ticks later), the arms swing behind it."""
    n, stance = W['n'], W['stance']
    reach = V_WALK * stance / 2.0
    half = len(bob)
    feet = lambda side, t: stride_foot(t if side == 'l' else t - n // 2, W, n, stance, reach)
    c = lambda t, ph: math.cos(2 * math.pi * (t - ph) / n)
    ticks = range(0, n + 1)
    bones = {
        'hips': {'position': [k(t, P(y=bob[t % half])) for t in ticks],
                 'rotation': [k(t, R(z=round(W['roll'] * c(t, W['roll_ph']), 3))) for t in ticks]},
        'torso': {'rotation': [k(t, R(x=(HOLD_TORSO if carry else W['lean']) + W['torso_bob'] * bob[(t - W['bob_lag']) % half],
                                      z=round(W['torso_roll'] * c(t, W['torso_ph']), 3))) for t in ticks]},
        'clamp_l': {'rotation': [k(t, R(z=W['clamp'] if t % half == W['clamp_tick'] else 0)) for t in ticks]},
    }
    if carry:
        bones.update(hold_arms())
        cb, cl = W['carry_bob'], W['carry_lag']
        bones['arm_l']['rotation'] = [k(t, R(x=HOLD_ARM + cb * bob[(t - cl) % half], z=HOLD_IN)) for t in ticks]
        bones['arm_r']['rotation'] = [k(t, R(x=HOLD_ARM + cb * bob[(t - cl) % half], z=-HOLD_IN)) for t in ticks]
    else:
        a, f = W['arm'], W['fore']
        if W.get('arm_hold'):
            # the arms held in a pose (the Executive's hands behind its back), swinging a little about it
            (ax, az), fx = W['arm_hold'], W['fore_hold']
            bones['arm_l'] = {'rotation': [k(t, R(x=round(ax - a * c(t, W['arm_ph']), 3), z=az)) for t in ticks]}
            bones['arm_r'] = {'rotation': [k(t, R(x=round(ax + a * c(t, W['arm_ph']), 3), z=-az)) for t in ticks]}
            bones['forearm_l'] = {'rotation': [k(t, R(x=round(fx - f * c(t, W['fore_ph']), 3))) for t in ticks]}
            bones['forearm_r'] = {'rotation': [k(t, R(x=round(fx + f * c(t, W['fore_ph']), 3))) for t in ticks]}
        else:
            bones['arm_l'] = {'rotation': [k(t, R(x=round(-a * c(t, W['arm_ph']), 3))) for t in ticks]}
            bones['arm_r'] = {'rotation': [k(t, R(x=round(a * c(t, W['arm_ph']), 3))) for t in ticks]}
            bones['forearm_l'] = {'rotation': [k(t, R(x=round(-f * c(t, W['fore_ph']), 3))) for t in ticks]}
            bones['forearm_r'] = {'rotation': [k(t, R(x=round(f * c(t, W['fore_ph']), 3))) for t in ticks]}
    return {'length': n, 'loop': 'loop', 'feet': feet, 'bones': bones,
            'effects': [(0, 'timeline', 'step:l_%s' % STEP, None), (n // 2, 'timeline', 'step:r_%s' % STEP, None)]}


def walk(carry=False):
    """The long walk (Bulwark: 12 ticks a step, 2.85 blocks; the hull drops 3 px on each plant and is caught)."""
    return stride(F['walk'], BOB, carry)


# ---- walk_b: the alternative, filmed beside `walk` and chosen in the game ------------------------------------------
# 9 ticks a step (2.14 blocks at the same 0.2376 blocks a tick, feet still not sliding). Each boot is down 11 ticks
# (duty 0.61), so both boots are down for 2 ticks after every plant and the hull settles into that double support. The
# swing boot lifts higher (7 px) over its 7 ticks.
WALKB_N = 18
WALKB_STANCE = 11
WALKB_REACH = V_WALK * WALKB_STANCE / 2.0
# the hips per tick of each step: plant at 0, sinking through the double support (lowest at 2), the catch, the rise
BOB_B = [-2.0, -3.3, -3.8, -3.2, -2.0, -0.8, 0.1, 0.4, -0.6]


def walk_b(carry=False):
    """The shorter, heavier step (Bulwark: 9 ticks, double support after each plant; the hull drops 3.8 px into it
    and is caught, the body rolls 2.5 degrees over the planted leg, the torso 3.5 two ticks later)."""
    return stride(F['walk_b'], BOB_B, carry)


# ---- walk_c: a real foot cycle (heel strike, flat, heel-off, toe-off, swing), the step from the leg ----------------
# Filmed in game, walk and walk_b crawled: through the late stance the trailing boot stayed flat while the low hip moved
# on, so the knee folded forward until the shin lay along the ground. walk_c walks on a higher hip (h_up, about 0.86 of
# the leg's length above the ankle), plants the heel well forward, rolls the boot onto its toe before the shin gets
# flat and lifts the toe off early; the step is ~0.85 of the leg (Bulwark: 8 ticks, 30 px, 1.9 blocks). Every stance
# tick of every walk-like clip is checked (check_legs): the shin at least SHIN_MIN degrees off the ground and the knee
# at least KNEE_MIN px (Bulwark px, times the frame's scale) above the ankle, or the build fails.
SHIN_MIN = 40.0
KNEE_MIN = 3.0
LEGACY_WALKS = ('walk', 'carry_walk', 'walk_b', 'carry_walk_b')   # kept for the in-game comparison; checked, reported


def foot_c(s, W):
    """One boot through a walk_c cycle of n ticks (frame px, z forward-negative). Contact from 0 (heel strike) to
    `stance` (toe-off): the heel lands toe-up (hs degrees) and the boot rolls flat about the heel by `flat`; flat; from
    `ho` the heel rises and the boot rolls about its toe tip to `to` degrees at toe-off; the planted heel or toe moves
    back at exactly the walk speed (no sliding). Then the swing: the boot swings through, lifted `lift` px, and turns
    from toe-down to toe-up for the next strike."""
    n, st = W['n'], W['stance']
    s = s % n
    v = V_WALK
    z0 = W['plant_z']
    hs, to = float(W['hs']), float(W['to'])
    flat, ho = W['flat'], W['ho']

    def contact(t):
        if t < flat:
            a = hs * (1 - t / float(flat)) ** 1.5
            heel = z0 + HEEL + v * t
            return (heel - HEEL * math.cos(math.radians(a)), G.ANKLE_H + HEEL * math.sin(math.radians(a)), a)
        if t <= ho:
            return (z0 + v * t, G.ANKLE_H, 0.0)
        u = (t - ho) / float(st - ho)
        a = to * u ** W['roll_exp']
        toe = z0 + v * ho - TOE + v * (t - ho)
        return (toe + TOE * math.cos(math.radians(a)), G.ANKLE_H + TOE * math.sin(math.radians(a)), -a)
    if s <= st:
        return contact(s)
    u = (s - st) / float(n - st)
    a0, a1 = contact(st), contact(0)
    e = (1 - math.cos(math.pi * u ** W['swing_delay'])) / 2
    z = a0[0] + (a1[0] - a0[0]) * e
    y = a0[1] + (a1[1] - a0[1]) * u + W['lift'] * math.sin(math.pi * u ** W['up_exp'])
    sm = u * u * (3 - 2 * u)
    ang = a0[2] + (a1[2] - a0[2]) * sm
    return (z, y, ang)


def walk_c(carry=False, high=False):
    """The re-authored heavy walk (`walk_c`, `carry_walk_c`, `carry_walk_high`). Short double support after each
    plant, the hull dropping a little into it and caught, the body rolling over the planted leg, the arms lagging, the
    rear boot visibly pushing off its toe. Native: authored in frame px, not scaled."""
    W = dict(F['walk_c'])
    n = W['n']
    half = n // 2
    bob = [round(b * SC, 3) for b in resample(W['bob'], half)]
    W2 = dict(W, torso_bob=W['torso_bob'] / SC, carry_bob=W['carry_bob'] / SC)
    c = stride(W2, bob, carry and not high)
    c['feet'] = lambda side, t: foot_c(t if side == 'l' else t - half, W)
    c['native'] = True
    up = W['h_up']
    # the hips: higher than at rest (h_up), the drop and catch on each plant, a small surge forward off the rear toe
    c['bones']['hips']['position'] = [k(t, P(y=round(up + bob[t % half], 3),
                                             z=round(-W['surge'] * math.sin(2 * math.pi * ((t - W['surge_ph']) % half) / half), 3)))
                                      for t in range(0, n + 1)]
    if high:
        hb = high_arms()
        cb, cl = W['carry_bob'] / SC, W['carry_lag']
        ax, ai = HIGH['arm'], HIGH['inward']
        hb['arm_l']['rotation'] = [k(t, R(x=round(ax - cb * bob[(t - cl) % half], 3), z=ai)) for t in range(0, n + 1)]
        hb['arm_r']['rotation'] = [k(t, R(x=round(ax - cb * bob[(t - cl) % half], 3), z=-ai)) for t in range(0, n + 1)]
        c['bones'].update(hb)
        c['bones']['torso']['rotation'] = [(t, [HIGH['torso'] + v[0] - W['lean'], v[1], v[2]], e, a, i)
                                           for (t, v, e, a, i) in c['bones']['torso']['rotation']]
    return c


def contact_of(z, y, ang):
    """How far the boot's lowest point (heel when toe-up, toe tip when on the toe) is above the ground."""
    if ang > 0:
        return y - HEEL * math.sin(math.radians(ang)) - G.ANKLE_H
    if ang < 0:
        return y - TOE * math.sin(math.radians(-ang)) - G.ANKLE_H
    return y - G.ANKLE_H


def check_legs(name, clip):
    """Every tick with a boot on the ground: the shin's angle off the ground and the knee's height over the ankle.
    Returns a list of failures (tick, side, shin degrees, knee px)."""
    feet = clip.get('feet')
    if feet is None:
        return []
    hips = clip['bones'].get('hips', {})
    hpos = sorted(hips.get('position', [k(0, P())]), key=lambda x: x[0])
    bad, worst = [], (90.0, 99.0)
    for t in range(0, int(clip.get('ik_until', clip['length'])) + 1):
        hp = value_at(hpos, t)
        hip = (hp[2], G.HIP_Y + hp[1])
        for side in 'lr':
            z, y, ang = feet(side, t) if callable(feet) else value_at(sorted(feet[side], key=lambda x: x[0]), t)
            if clip.get('feet_rel'):
                continue
            if contact_of(z, y, ang) > 0.3:
                continue
            t1, t2 = G.ik(hip, (z, y))
            a1 = math.radians(t1)
            kz, ky = hip[0] - G.THIGH * math.sin(a1), hip[1] - G.THIGH * math.cos(a1)
            shin = math.degrees(math.atan2(ky - y, abs(z - kz) + 1e-9))
            knee = ky - y
            reach = math.hypot(z - hip[0], y - hip[1]) / (G.THIGH + G.SHIN)
            worst = (min(worst[0], shin), min(worst[1], knee))
            if shin < SHIN_MIN or knee < KNEE_MIN * SC or reach > 0.995:     # (past full reach the boot would float)
                bad.append((t, side, round(shin, 1), round(knee, 1)))
    return bad, worst


# ---- the overhead carry (Bulwark): the load above and a little behind the jar's lid, clear of the pilot's view ------
# FK (art/mecha/bulwark/bulwark_geo.py rig): arm x 190 (just past straight up), 4 in, forearm -8, the torso leaning 3
# forward against the load behind: the fists' inner faces at (+-12.7, 57.2, +3.1). The load's underside sits on them:
# hold point (0, 57.2, 3.1) px = (0, 3.58, 0.19 behind) blocks, 5 px over the jar's valve (52.4) and, seen from the
# pilot's camera behind and 15 degrees up, 1.6 px above the valve's top edge.
HIGH = dict(arm=190.0, inward=4.0, fore=-8.0, torso=-3.0, point=(0.0, 57.2, 3.1))


def high_arms():
    return both_arms([k(0, R(x=HIGH['arm'], z=HIGH['inward']))], [k(0, R(x=HIGH['fore']))], [k(0, R())],
                     [k(0, R(x=-35))])


def grab_high():
    """The grab, going straight up: squat and reach as `grab`, close the grips (10), then drive up with the legs and
    press the load straight up past the chest to the overhead hold (arms locked at 21), leaning into it."""
    c = grab()
    arm = [k(0, R()), k(2, R(x=-6), 'easeOutQuad'), k(8, R(x=REACH['arm'], z=REACH['inward']), 'easeInOutCubic'),
           k(10, R(x=REACH['arm'] + 3, z=REACH['inward'] + 2), 'easeInQuad'), k(12, R(x=REACH['arm'] + 3, z=REACH['inward'] + 2)),
           k(17, R(x=HOLD_ARM, z=HOLD_IN * 0.5), 'easeInOutCubic'),
           k(21, R(x=HIGH['arm'] + 4, z=HIGH['inward']), 'easeOutCubic'), k(24, R(x=HIGH['arm'], z=HIGH['inward']), 'easeInOutSine')]
    fore = [k(0, R()), k(3, R()), k(9, R(x=REACH['fore']), 'easeInOutCubic'), k(12, R(x=REACH['fore'])),
            k(17, R(x=-40), 'easeInOutCubic'), k(21, R(x=HIGH['fore'] + 3), 'easeOutCubic'), k(24, R(x=HIGH['fore']), 'easeInOutSine')]
    hand = [k(0, R()), k(6, R(z=-10), 'easeInOutSine'), k(17, R(z=-10)), k(21, R(), 'easeInOutSine')]
    grip = [k(0, R()), k(4, R(x=25), 'easeOutCubic'), k(9, R(x=25)), k(10, R(x=-35), 'easeInQuad')]
    c['bones'].update(both_arms(arm, fore, hand, grip))
    c['bones']['hips']['position'] = [k(0, P()), k(2, P(y=0.5), 'easeOutQuad'), k(9, P(y=GRAB['hips'][0]), 'easeInOutCubic'),
                                      k(12, P(y=GRAB['hips'][1]), 'easeInQuad'), k(18, P(y=1.0), 'easeOutCubic'),
                                      k(21, P(y=-1.2), 'easeInQuad'), k(24, P(y=-0.6), 'easeInOutSine')]
    c['bones']['torso']['rotation'] = [k(0, R()), k(3, R(x=1), 'easeOutQuad'), k(9, R(x=GRAB['torso'][0]), 'easeInOutCubic'),
                                       k(12, R(x=GRAB['torso'][1])), k(18, R(x=4), 'easeInOutCubic'),
                                       k(21, R(x=HIGH['torso'] - 1), 'easeInOutSine'), k(24, R(x=HIGH['torso']), 'easeInOutSine')]
    c['effects'] = [(2, 'sound', snd('servo', 0.6, 0.8), None), (10, 'timeline', 'grab:close', None),
                    (13, 'sound', snd('hydraulic_lift', 0.7, 0.8), None), (21, 'timeline', 'grab:held_high', None)]
    return c


def hold_high():
    """Holding it overhead, arms locked: a slow heave, a small sway of the load, the torso balancing it."""
    bones = high_arms()
    jit = [0, 0.5, -0.4, 0.4, -0.5, 0.3, -0.3, 0.5, 0]
    bones['arm_l']['rotation'] = [k(t * 5, R(x=HIGH['arm'] + j, z=HIGH['inward'])) for t, j in enumerate(jit)]
    bones['arm_r']['rotation'] = [k(t * 5, R(x=HIGH['arm'] - j, z=-HIGH['inward'])) for t, j in enumerate(jit)]
    bones.update({'hips': {'position': [k(0, P(y=-0.6)), k(20, P(y=-1.4), 'easeInOutSine'), k(40, P(y=-0.6), 'easeInOutSine')]},
                  'torso': {'rotation': [k(0, R(x=HIGH['torso'])), k(20, R(x=HIGH['torso'] - 1, z=0.8), 'easeInOutSine'),
                                         k(40, R(x=HIGH['torso']), 'easeInOutSine')]}})
    return {'length': 40, 'loop': 'loop', 'feet': planted(), 'bones': bones,
            'effects': [(20, 'sound', snd('hydraulic_strain', 0.3, 0.8), None)]}


def throw_high():
    """Drive again from the overhead hold: a short wind-up back (the torso leans back, the arms go further over),
    then the heave forward (release on tick 6, the same server release as `throw` at its tick 11), follow through,
    recover."""
    arm = [k(0, R(x=HIGH['arm'], z=HIGH['inward'])), k(4, R(x=HIGH['arm'] + 12, z=-6), 'easeInOutCubic'),
           k(6, R(x=110), 'easeInExpo'), k(9, R(x=55), 'easeOutQuad'), k(16, R(x=-3), 'easeInOutCubic'), k(19, R(), 'easeInOutSine')]
    fore = [k(0, R(x=HIGH['fore'])), k(4, R(x=-14), 'easeInOutCubic'), k(6, R(x=-20), 'easeInExpo'),
            k(10, R(x=-15), 'easeOutQuad'), k(19, R(), 'easeInOutSine')]
    grip = [k(0, R(x=-35)), k(5, R(x=-35)), k(6, R(x=30), 'easeOutExpo'), k(13, R(), 'easeInOutSine')]
    bones = dict(both_arms(arm, fore, None, grip))
    bones.update({
        'hips': {'position': [k(0, P(y=-0.6)), k(4, P(y=-2.4), 'easeInOutCubic'), k(6, P(y=-0.8), 'easeInExpo'),
                              k(9, P(y=-3.5), 'easeOutQuad'), k(17, P(), 'easeInOutCubic')]},
        'torso': {'rotation': [k(0, R(x=HIGH['torso'])), k(4, R(x=10), 'easeInOutCubic'), k(6, R(x=-12), 'easeInExpo'),
                               k(9, R(x=-16), 'easeOutQuad'), k(17, R(x=1), 'easeInOutCubic'), k(19, R(), 'easeInOutSine')]},
    })
    return {'length': 19, 'loop': 'once', 'feet': planted(), 'bones': bones,
            'effects': [(1, 'sound', snd('hydraulic_lift', 0.6, 0.9), None), (6, 'timeline', 'throw:release', None),
                        (7, 'sound', snd('whoosh', 0.7, 0.8), None), (9, 'sound', snd('servo_settle', 0.5, 0.8), None)]}


LIMP_N, LIMP_STANCE = 32, 20


def limp():
    """Flat battery (speed x 0.3): a separate dragging walk; the left boot scrapes through and plants flat and late."""
    reach = V_LIMP * LIMP_STANCE / 2.0

    def foot(s, drag):
        s = s % LIMP_N
        if s <= LIMP_STANCE:
            return on_toe(-reach + V_LIMP * s, -6 if (s >= LIMP_STANCE - 1) else 0)
        u = (s - LIMP_STANCE) / float(LIMP_N - LIMP_STANCE)
        if drag:
            z = reach - 2 * reach * min(1, u * 1.2) ** 1.6
            zz, yy, aa = on_toe(z, -18 + 12 * u)
            return (zz, yy + SC * 1.0 * math.sin(math.pi * u), aa)
        z = reach - 2 * reach * (1 - math.cos(math.pi * u)) / 2
        return (z, G.ANKLE_H + SC * 3.5 * math.sin(math.pi * u), 4 * u)
    feet = lambda side, t: foot(t, True) if side == 'l' else foot(t - LIMP_N // 2, False)
    c = lambda t, ph: math.cos(2 * math.pi * (t - ph) / LIMP_N)
    bob = lambda t: -2.5 * math.exp(-(((t % LIMP_N) - 3) ** 2) / 8.0) - 1.0 * math.exp(-((((t - 16) % LIMP_N) - 3) ** 2) / 8.0)
    ticks = range(0, LIMP_N + 1, 2)
    return {'length': LIMP_N, 'loop': 'loop', 'feet': feet,
            'bones': dict({
                # carried F['limp_up'] Bulwark px higher than at rest (scaled): on the Bulwark's rest crouch the trailing shin lay down
                'hips': {'position': [k(t, P(y=round(F['limp_up'] + bob(t), 3))) for t in range(0, LIMP_N + 1)],
                         'rotation': [k(t, R(z=round(3 + 3 * c(t, 9), 3))) for t in ticks]},
                'torso': {'rotation': [k(t, R(x=-6, z=round(2 * c(t, 11), 3))) for t in ticks]},
                'arm_l': {'rotation': const(R(x=-4, z=-5))}, 'forearm_l': {'rotation': const(R(x=-14))},
                'arm_r': {'rotation': [k(t, R(x=round(5 * c(t, 3), 3))) for t in ticks]},
                'sensor': {'rotation': const(R(x=-12))},
            }, **lamps([(0, 0)], [(0, 1)])),
            'effects': [(0, 'timeline', 'step:l_drag', None), (16, 'timeline', 'step:r_%s' % STEP, None),
                        (22, 'sound', snd('servo_catch', 0.5, 0.7), None)]}


def turn(direction):
    a = REST_FOOT_T
    up = (a[0] - 1, a[1] + 4, 4)
    feet = {'l': [k(0, a), k(3, up, 'easeOutCubic'), k(6, a, 'easeInQuad'), k(16, a)],
            'r': [k(0, a), k(8, a), k(11, up, 'easeOutCubic'), k(14, a, 'easeInQuad'), k(16, a)]}
    yaw = 6 if direction == 'l' else -6
    return {'length': 16, 'loop': 'loop', 'feet': feet,
            'bones': {'hips': {'position': [k(0, P(y=-0.6)), k(3, P(y=0.2), 'easeInOutSine'), k(7, P(y=-1.2), 'easeInQuad'),
                                            k(11, P(y=0.2), 'easeInOutSine'), k(15, P(y=-1.2), 'easeInQuad'),
                                            k(16, P(y=-0.6), 'easeOutQuad')],
                               'rotation': const(R(y=yaw * 0.4))},
                      'torso': {'rotation': [k(0, R(y=yaw * 0.6)), k(8, R(y=yaw * 0.6 + 1), 'easeInOutSine'),
                                             k(16, R(y=yaw * 0.6), 'easeInOutSine')]}},
            'effects': [(6, 'timeline', 'step:l', None), (14, 'timeline', 'step:r', None)]}


def jump():
    """A heavy hop: a four-tick squat (server impulse on tick 4), the boots shove down as it leaves, a slight tuck."""
    a = REST_FOOT_T
    feet = {s: [k(0, a), k(4, a), k(5, (0, -2, -20), 'easeInExpo'), k(10, (2, 8, 6), 'easeOutCubic'), k(12, (2, 8, 6))]
            for s in 'lr'}
    return {'length': 12, 'loop': 'hold', 'feet': feet,
            'bones': dict({'hips': {'position': [k(0, P()), k(1, P(y=0.5), 'easeOutQuad'), k(4, P(y=-5), 'easeOutQuad'),
                                                 k(5, P(y=1), 'easeInExpo'), k(12, P(y=0.5))]},
                           'torso': {'rotation': [k(0, R()), k(4, R(x=-8), 'easeOutQuad'), k(6, R(x=3), 'easeOutCubic'),
                                                  k(12, R(), 'easeInOutSine')]}},
                          **both_arms([k(0, R()), k(4, R(x=-20), 'easeOutQuad'), k(7, R(x=25), 'easeOutCubic'),
                                       k(12, R(x=18), 'easeInOutSine')], lag=1)),
            'effects': [(4, 'timeline', 'jump:push', None)]}


def fall():
    feet = {'l': [k(0, (-3, -22, 6)), k(8, (-3, -23, 3), 'easeInOutSine'), k(16, (-3, -22, 6), 'easeInOutSine')],
            'r': [k(0, (-2, -23, 4)), k(8, (-2, -22, 6), 'easeInOutSine'), k(16, (-2, -23, 4), 'easeInOutSine')]}
    return {'length': 16, 'loop': 'loop', 'feet': feet, 'feet_rel': True,
            'bones': dict({'torso': {'rotation': const(R(x=3))}},
                          **both_arms([k(0, R(x=25)), k(8, R(x=29), 'easeInOutSine'), k(16, R(x=25), 'easeInOutSine')], lag=2)),
            'effects': []}


def land(heavy=False):
    d, L = (9.0, 24) if heavy else (5.0, 16)
    hips = ([k(0, P()), k(4, P(y=-d), 'easeOutCubic'), k(8, P(y=-d + 0.6), 'easeInOutSine'),
             k(17, P(y=0.8), 'easeInOutCubic'), k(24, P(), 'easeInOutSine')] if heavy else
            [k(0, P()), k(3, P(y=-d), 'easeOutCubic'), k(6, P(y=-d + 0.4), 'easeInOutSine'),
             k(12, P(y=0.5), 'easeInOutSine'), k(16, P(), 'easeInOutSine')])
    torso = [k(0, R(x=3)), k(4 if heavy else 3, R(x=-10 if heavy else -6), 'easeOutCubic'), k(L - 4, R(), 'easeInOutSine')]
    bones = dict({'hips': {'position': hips}, 'torso': {'rotation': torso},
                  'sensor': {'rotation': [k(0, R()), k(3, R(x=18), 'easeOutCubic'), k(L - 6, R(), 'easeInOutSine')]}},
                 **both_arms([k(0, R(x=25)), k(2, R(x=25)), k(6, R(x=-4, z=-16), 'easeInQuad'), k(11, R(x=-2, z=-10), 'easeInOutSine'),
                              k(L, R(), 'easeInOutSine')],
                             [k(0, R()), k(3, R()), k(6, R(x=35), 'easeInQuad'), k(L - 4, R(), 'easeInOutSine')],
                             None, [k(0, R()), k(3, R(x=60), 'easeOutQuad'), k(L - 4, R(), 'easeInOutSine')], lag=1))
    eff = [(0, 'timeline', 'land:heavy' if heavy else 'land:light', None)]
    if heavy:
        bones['vent_l'] = {'rotation': [k(0, R()), k(2, R(x=-35), 'easeOutExpo'), k(12, R(), 'easeInElastic', [4])]}
        bones['vent_r'] = {'rotation': [k(0, R()), k(3, R(x=-35), 'easeOutExpo'), k(13, R(), 'easeInElastic', [4])]}
        eff += [(2, 'particle', 'cloud', 'fx_vent_l'), (3, 'particle', 'cloud', 'fx_vent_r')]
    return {'length': L, 'loop': 'once', 'feet': planted(), 'bones': bones, 'effects': eff}


# ---- Heavy Lifting: grab, hold, carry, throw, the refused grab ----------------------------------------------------
REACH = dict(arm=50.0, fore=-15.0, inward=4.0)            # squatting, fists low and 18 px ahead
GRAB = dict(hips=(-7, -7.5), torso=(-14, -15))           # the grab's squat (Bulwark px) and lean at ticks 9 and 12


def grab():
    """Drive with a cow (or a block, or a small mecha) in front: squat with the legs, reach both hands forward and
    down, open the grips, close them on it (contact 10, the server takes hold), hold a beat, lift with the legs back
    to standing and draw it to the chest, leaning back against the weight."""
    arm = [k(0, R()), k(2, R(x=-6), 'easeOutQuad'), k(8, R(x=REACH['arm'], z=REACH['inward']), 'easeInOutCubic'),
           k(10, R(x=REACH['arm'] + 3, z=REACH['inward'] + 2), 'easeInQuad'), k(12, R(x=REACH['arm'] + 3, z=REACH['inward'] + 2)),
           k(19, R(x=HOLD_ARM + 3, z=HOLD_IN), 'easeInOutCubic'), k(22, R(x=HOLD_ARM, z=HOLD_IN), 'easeInOutSine')]
    fore = [k(0, R()), k(3, R()), k(9, R(x=REACH['fore']), 'easeInOutCubic'), k(12, R(x=REACH['fore'])),
            k(20, R(x=HOLD_FORE + 4), 'easeInOutCubic'), k(23, R(x=HOLD_FORE), 'easeInOutSine')]
    hand = [k(0, R()), k(6, R(z=-10), 'easeInOutSine')]
    grip = [k(0, R()), k(4, R(x=25), 'easeOutCubic'), k(9, R(x=25)), k(10, R(x=-35), 'easeInQuad')]
    bones = dict(both_arms(arm, fore, hand, grip))
    bones.update({
        'hips': {'position': [k(0, P()), k(2, P(y=0.5), 'easeOutQuad'), k(9, P(y=GRAB['hips'][0]), 'easeInOutCubic'),
                              k(12, P(y=GRAB['hips'][1]), 'easeInQuad'), k(20, P(y=0.5), 'easeInOutCubic'), k(24, P(), 'easeInOutSine')]},
        'torso': {'rotation': [k(0, R()), k(3, R(x=1), 'easeOutQuad'), k(9, R(x=GRAB['torso'][0]), 'easeInOutCubic'),
                               k(12, R(x=GRAB['torso'][1])),
                               k(20, R(x=HOLD_TORSO + 1), 'easeInOutCubic'), k(24, R(x=HOLD_TORSO), 'easeInOutSine')]},
        'sensor': {'rotation': [k(0, R()), k(8, R(x=-20), 'easeInOutSine'), k(20, R(x=5), 'easeInOutSine'), k(24, R())]},
    })
    return {'length': 24, 'loop': 'hold', 'feet': planted(), 'bones': bones,
            'effects': [(2, 'sound', snd('servo', 0.6, 0.8), None), (10, 'timeline', 'grab:close', None),
                        (13, 'sound', snd('hydraulic_lift', 0.7, 0.8), None), (20, 'timeline', 'grab:held', None)]}


def hold():
    """Holding it at the chest: the strain shows as a slow heave and a tremble in the arms."""
    bones = hold_arms()
    jit = [0, 0.4, -0.3, 0.3, -0.4, 0.3, -0.2, 0.4, 0]
    bones['arm_l']['rotation'] = [k(t * 5, R(x=HOLD_ARM + j, z=HOLD_IN)) for t, j in enumerate(jit)]
    bones['arm_r']['rotation'] = [k(t * 5, R(x=HOLD_ARM - j, z=-HOLD_IN)) for t, j in enumerate(jit)]
    bones.update({'hips': {'position': [k(0, P(y=-0.8)), k(20, P(y=-1.6), 'easeInOutSine'), k(40, P(y=-0.8), 'easeInOutSine')]},
                  'torso': {'rotation': [k(0, R(x=HOLD_TORSO)), k(20, R(x=HOLD_TORSO + 1), 'easeInOutSine'),
                                         k(40, R(x=HOLD_TORSO), 'easeInOutSine')]}})
    return {'length': 40, 'loop': 'loop', 'feet': planted(), 'bones': bones,
            'effects': [(20, 'sound', snd('hydraulic_strain', 0.3, 0.8), None)]}


def throw():
    """Drive again: hoist it overhead (wind-up, the torso leans back), hold two ticks, heave it forward over the head
    (release on tick 11: the server lets go with the velocity), follow through bent over, recover."""
    arm = [k(0, R(x=HOLD_ARM, z=HOLD_IN)), k(2, R(x=HOLD_ARM - 5, z=HOLD_IN), 'easeOutQuad'),
           k(7, R(x=145, z=-8), 'easeInOutCubic'), k(9, R(x=148, z=-8)),       # overhead, shoulders splayed out
           k(11, R(x=90), 'easeInExpo'), k(14, R(x=55), 'easeOutQuad'),
           k(21, R(x=-3), 'easeInOutCubic'), k(24, R(), 'easeInOutSine')]
    fore = [k(0, R(x=HOLD_FORE)), k(3, R(x=HOLD_FORE)), k(8, R(x=-10), 'easeInOutCubic'), k(9, R(x=-8)),
            k(11, R(x=-20), 'easeInExpo'), k(15, R(x=-15), 'easeOutQuad'), k(24, R(), 'easeInOutSine')]
    grip = [k(0, R(x=-35)), k(10, R(x=-35)), k(11, R(x=30), 'easeOutExpo'), k(18, R(), 'easeInOutSine')]
    bones = dict(both_arms(arm, fore, [k(0, R(z=-10)), k(12, R(z=-10)), k(20, R(), 'easeInOutSine')], grip))
    bones.update({
        'hips': {'position': [k(0, P(y=-0.8)), k(7, P(y=-3), 'easeInOutCubic'), k(9, P(y=-3.4)),
                              k(11, P(y=-1), 'easeInExpo'), k(14, P(y=-3.5), 'easeOutQuad'), k(22, P(), 'easeInOutCubic')]},
        'torso': {'rotation': [k(0, R(x=HOLD_TORSO)), k(7, R(x=12), 'easeInOutCubic'), k(9, R(x=13)),
                               k(11, R(x=-12), 'easeInExpo'), k(14, R(x=-16), 'easeOutQuad'),
                               k(22, R(x=1), 'easeInOutCubic'), k(24, R(), 'easeInOutSine')]},
    })
    return {'length': 24, 'loop': 'once', 'feet': planted(), 'bones': bones,
            'effects': [(3, 'sound', snd('hydraulic_lift', 0.7, 0.9), None), (11, 'timeline', 'throw:release', None),
                        (12, 'sound', snd('whoosh', 0.7, 0.8), None), (14, 'sound', snd('servo_settle', 0.5, 0.8), None)]}


def grab_refused():
    """A refused Drive (nothing to lift, too heavy, no power): it squats and reaches as for a grab, the grips snap shut
    on air (tick 10, overshooting the close), then it straightens into a shrug held 7 ticks: shoulders up 2.5 px,
    palms turned up, the hull bobbing once, the cab lamp tilting."""
    arm = [k(0, R()), k(2, R(x=-6), 'easeOutQuad'), k(8, R(x=REACH['arm'], z=REACH['inward']), 'easeInOutCubic'),
           k(10, R(x=REACH['arm'], z=REACH['inward'])), k(14, R(x=25, z=-12), 'easeInOutCubic'),
           k(21, R(x=25, z=-12)), k(26, R(), 'easeInOutSine')]
    fore = [k(0, R()), k(3, R()), k(9, R(x=REACH['fore']), 'easeInOutCubic'), k(10, R(x=REACH['fore'])),
            k(14, R(x=60), 'easeInOutCubic'), k(21, R(x=60)), k(26, R(), 'easeInOutSine')]
    grip = [k(0, R()), k(4, R(x=25), 'easeOutCubic'), k(9, R(x=25)), k(10, R(x=-50), 'easeInQuad'),
            k(12, R(x=-38), 'easeOutQuad'), k(16, R(x=20), 'easeInOutSine'), k(26, R(), 'easeInOutSine')]
    hand = [k(0, R()), k(13, R()), k(15, R(z=30), 'easeInOutSine'), k(21, R(z=30)), k(25, R(), 'easeInOutSine')]
    bones = dict(both_arms(arm, fore, hand, grip))
    bones['arm_l']['position'] = [k(0, P()), k(12, P()), k(15, P(y=2.5), 'easeOutCubic'), k(21, P(y=2.5)),
                                  k(25, P(), 'easeInOutSine')]
    bones['arm_r']['position'] = [k(0, P()), k(13, P()), k(16, P(y=2.5), 'easeOutCubic'), k(21, P(y=2.5)),
                                  k(25, P(), 'easeInOutSine')]
    bones.update({
        'hips': {'position': [k(0, P()), k(9, P(y=-5), 'easeInOutCubic'), k(10, P(y=-5)), k(14, P(y=0.5), 'easeInOutCubic'),
                              k(16, P(y=1.2), 'easeOutQuad'), k(19, P(), 'easeInOutSine')]},
        'torso': {'rotation': [k(0, R()), k(9, R(x=-10), 'easeInOutCubic'), k(10, R(x=-10)), k(14, R(x=2), 'easeInOutCubic'),
                               k(21, R(x=2)), k(26, R(), 'easeInOutSine')]},
        'sensor': {'rotation': [k(0, R()), k(13, R()), k(16, R(z=14), 'easeInOutSine'), k(21, R(z=14)),
                                k(26, R(), 'easeInOutSine')]},
    })
    return {'length': 26, 'loop': 'once', 'feet': planted(), 'bones': bones,
            'effects': [(2, 'sound', snd('servo', 0.6, 0.8), None), (10, 'timeline', 'grab:dry', None),
                        (15, 'sound', snd('shrug_hiss', 0.5, 1.0), None)]}


# ---- states ---------------------------------------------------------------------------------------------------------
def vent():
    heave = [k(0, P()), k(6, P()), k(18, P(y=-4), 'easeInOutSine'), k(33, P(y=-5.4), 'easeInOutSine'),
             k(48, P(y=-4), 'easeInOutSine'), k(63, P(y=-5.4), 'easeInOutSine'), k(78, P(y=-4.2), 'easeInOutSine'),
             k(84, P(y=-4.2)), k(96, P(y=0.5), 'easeInOutCubic'), k(100, P(), 'easeInOutSine')]
    bones = dict(both_arms([k(0, R()), k(6, R()), k(16, R(x=-6, z=-3), 'easeInQuad'), k(84, R(x=-6, z=-3)),
                            k(94, R(), 'easeInOutCubic'), k(96, R(z=-6), 'easeOutQuad'), k(98, R(z=3), 'easeInOutSine'),
                            k(100, R(), 'easeInOutSine')],
                           [k(0, R()), k(8, R()), k(18, R(x=-18), 'easeInQuad'), k(84, R(x=-18)), k(95, R(), 'easeInOutCubic')], lag=1))
    bones.update({
        'vent_l': {'rotation': [k(0, R()), k(1, R(x=4), 'easeOutQuad'), k(3, R(x=-80), 'easeInExpo'),
                                k(5, R(x=-74), 'easeInOutSine'), k(78, R(x=-74)), k(82, R(), 'easeInQuad')]},
        'vent_r': {'rotation': [k(0, R()), k(1, R(x=4), 'easeOutQuad'), k(3, R(x=-80), 'easeInExpo'),
                                k(6, R(x=-74), 'easeInOutSine'), k(82, R(x=-74)), k(86, R(), 'easeInQuad')]},
        'torso': {'rotation': [k(0, R()), k(3, R(x=3), 'easeOutQuad'), k(7, R(), 'easeInOutSine'), k(8, R()),
                               k(18, R(x=-7), 'easeInOutSine'), k(84, R(x=-7)), k(96, R(x=0.8), 'easeInOutCubic'),
                               k(100, R(), 'easeInOutSine')]},
        'hips': {'position': heave},
        'sensor': {'rotation': [k(0, R()), k(6, R()), k(18, R(x=-18), 'easeInOutSine'), k(84, R(x=-18)),
                                k(94, R(), 'easeInOutSine')]},
    })
    return {'length': 100, 'loop': 'once', 'feet': planted(), 'bones': bones,
            'effects': [(3, 'timeline', 'vent:blast', None),
                        (20, 'particle', 'cloud', 'fx_vent_l'), (26, 'particle', 'cloud', 'fx_vent_r'),
                        (36, 'particle', 'cloud', 'fx_vent_l'), (42, 'particle', 'cloud', 'fx_vent_r'),
                        (52, 'particle', 'cloud', 'fx_vent_l'), (58, 'particle', 'cloud', 'fx_vent_r'),
                        (68, 'particle', 'cloud', 'fx_vent_l'), (76, 'sound', snd('vent_whistle', 0.6, 0.8), None),
                        (82, 'sound', snd('vent_clank', 0.6, 0.9), None), (86, 'sound', snd('vent_clank', 0.6, 0.8), None),
                        (95, 'sound', snd('rattle', 0.5, 0.8), None)]}


def hurt():
    return {'length': 10, 'loop': 'once',
            'bones': {'root': {'rotation': [k(0, R()), k(1, R(x=1.5, z=-3), 'easeOutExpo'), k(3, R(x=1.5, z=-3)),
                                            k(6, R(x=-0.5, z=1), 'easeInOutSine'), k(10, R(), 'easeInOutSine')],
                               'position': [k(0, P()), k(1, P(y=0.5), 'easeOutExpo'), k(3, P(y=0.5)), k(8, P(), 'easeInOutSine')]},
                      'clamp_l': {'rotation': [k(0, R()), k(2, R(z=2), 'easeOutQuad'), k(5, R(), 'easeInQuad')]},
                      'clamp_r': {'rotation': [k(0, R()), k(2, R(z=-2), 'easeOutQuad'), k(5, R(), 'easeInQuad')]}},
            'effects': [(1, 'particle', 'electric_spark', 'fx_hull')]}


def eject():
    """The Golden Parachute: klaxon, the clamps fly open, the jar is fired up out of the collar (the server hands it to
    the parachute on tick 4), the machine jolts and sags into its parked pose, lamps out in steps, smoking."""
    bones = dict({
        'jar_anchor': {'position': [k(0, P()), k(4, P()), k(6, P(y=18), 'easeOutCubic')],
                       'scale': [k(0, S(1), interp='step'), k(6, S(0), interp='step')]},
        'hips': {'position': [k(0, P()), k(4, P()), k(5, P(y=-3), 'easeOutQuad'), k(8, P(y=-2), 'easeInOutSine'), k(10, P(y=-2)),
                              k(24, P(y=PARK_HIPS - 0.6), 'easeInQuad'), k(28, P(y=PARK_HIPS), 'easeInOutSine')]},
        'torso': {'rotation': [k(0, R()), k(4, R()), k(5, R(x=2), 'easeOutQuad'), k(10, R(), 'easeInOutSine'),
                               k(24, R(x=PARK_TORSO - 0.5), 'easeInOutSine'), k(28, R(x=PARK_TORSO), 'easeInOutSine')]},
        'sensor': {'rotation': [k(0, R()), k(12, R()), k(24, R(x=PARK_SENSOR), 'easeInOutSine')]},
    }, **dict(both_arms([k(0, R())], [k(0, R()), k(10, R()), k(22, R(x=PARK_FORE), 'easeInOutSine')], lag=1),
              **lamps([(0, 1), (18, 0)], [(0, 1), (12, 0)])))
    bones.update(clamps([k(0, [0]), k(1, [-2], 'easeOutQuad'), k(3, [70], 'easeOutExpo'), k(6, [58], 'easeInOutSine'),
                         k(9, [64], 'easeInOutSine'), k(26, [64]), k(30, [0], 'easeInQuad')]))
    return {'length': 30, 'loop': 'hold', 'feet': planted(), 'bones': bones, 'pilot_until': 6,
            'effects': [(0, 'sound', snd('klaxon', 0.8, 0.9), None), (3, 'sound', snd('clamp_blow', 0.8, 0.9), None),
                        (4, 'timeline', 'eject:jar', None), (14, 'particle', 'large_smoke', 'fx_hull'),
                        (22, 'particle', 'large_smoke', 'fx_hull'), (26, 'sound', snd('sigh', 0.6, 0.8), None),
                        (30, 'sound', snd('clamp_close', 0.5, 0.8), None)]}


def destroyed():
    """Collapse (40 ticks before the server's explosion): sparks, lamps die, the arms drop, the left knee gives then
    the right, and it slumps forward onto its front and fists (contact 27), the boots staying planted, a 1 px settle,
    then smoking."""
    bones = dict({
        'hips': {'position': [k(0, P()), k(8, P()), k(12, P(y=-6), 'easeInQuad'), k(14, P(y=-5.5), 'easeOutQuad'),
                              k(20, P(y=-9), 'easeInQuad'), k(27, P(y=-13), 'easeInQuad'), k(30, P(y=-12), 'easeOutQuad'),
                              k(33, P(y=-13), 'easeInOutSine')],
                 'rotation': [k(0, R()), k(8, R()), k(12, R(z=5), 'easeInQuad'), k(20, R(z=2), 'easeInOutSine'),
                              k(27, R()), k(40, R())]},
        'torso': {'rotation': [k(0, R()), k(10, R()), k(20, R(x=-12), 'easeInQuad'), k(27, R(x=-30), 'easeInQuad'),
                               k(30, R(x=-28), 'easeOutQuad'), k(33, R(x=-30), 'easeInOutSine')]},
        'sensor': {'rotation': [k(0, R()), k(6, R()), k(14, R(x=-25), 'easeInQuad'), k(27, R(x=30), 'easeInOutSine')]},
    }, **dict(both_arms([k(0, R()), k(2, R()), k(8, R(x=-8), 'easeInQuad'), k(20, R(x=-8)), k(27, R(x=5, z=-38), 'easeInQuad'),
                         k(30, R(x=4, z=-35), 'easeOutQuad'), k(33, R(x=5, z=-37), 'easeInOutSine')],
                        [k(0, R()), k(3, R()), k(9, R(x=-15), 'easeInQuad'), k(20, R(x=-15)), k(27, R(x=35), 'easeInQuad')],
                        None, [k(0, R()), k(20, R()), k(27, R(x=70), 'easeInQuad')], lag=1),
              **lamps([(0, 1), (11, 0)], [(0, 1), (18, 0)])))
    return {'length': 40, 'loop': 'hold', 'feet': planted(), 'bones': bones, 'pilot': False,
            'effects': [(0, 'timeline', 'fx:sparks:fx_hull', None), (7, 'particle', 'electric_spark', 'fx_core'),
                        (12, 'sound', snd('servo_fail', 0.7, 0.7), None), (14, 'particle', 'electric_spark', 'fx_hull'),
                        (27, 'timeline', 'destroyed:thud', None), (34, 'particle', 'large_smoke', 'fx_hull')]}


BUILD_SLOTS = [('legs', 62, ['socket_thigh_l', 'socket_thigh_r', 'socket_shin_l', 'socket_shin_r']),
               ('back', 70, ['socket_back']), ('back_1', 77, ['socket_back_1']), ('core', 84, ['socket_core']),
               ('torso_0', 91, ['socket_torso_0']), ('torso_1', 98, ['socket_torso_1']), ('torso_2', 105, ['socket_torso_2']),
               ('arm_l', 112, ['socket_arm_l']), ('arm_r', 119, ['socket_arm_r']),
               ('sensor', 126, ['socket_sensor']), ('sensor_1', 133, ['socket_sensor_1'])]


def outward(bone):
    rot = [b for b in G.bones() if b[0] == bone][0][3] or [0, 0, 0]
    x, y, z = [math.radians(a) for a in rot]
    vx, vy, vz = 0.0, 0.0, -1.0
    vy, vz = vy * math.cos(x) - vz * math.sin(x), vy * math.sin(x) + vz * math.cos(x)
    vx, vz = vx * math.cos(y) + vz * math.sin(y), -vx * math.sin(y) + vz * math.cos(y)
    vx, vy = vx * math.cos(z) - vy * math.sin(z), vx * math.sin(z) + vy * math.cos(z)
    return [round(vx, 6), round(vy, 6), round(vz, 6)]


def build():
    """The Gantry's assembly (200 ticks): delivered folded on its haunches, arms crossed, clamps open; stands 42-60;
    the modules arrive by slot (62-140, each from 6 px out, seating with a 1 px bounce); inspection; it settles parked
    with the clamps closed over the empty collar."""
    bones = dict({
        'hips': {'position': [k(0, P(y=-11)), k(42, P(y=-11)), k(44, P(y=-11.6), 'easeOutQuad'), k(56, P(y=0.6), 'easeInOutCubic'),
                              k(58, P(y=-1.4), 'easeInQuad'), k(61, P(), 'easeInOutSine'),
                              k(150, P()), k(153, P(y=-1.2), 'easeOutQuad'), k(158, P(), 'easeInOutSine'),
                              k(186, P()), k(198, P(y=PARK_HIPS), 'easeInQuad'), k(200, P(y=PARK_HIPS))]},
        'torso': {'rotation': [k(0, R(x=-12)), k(46, R(x=-12)), k(58, R(x=1), 'easeInOutCubic'), k(61, R(), 'easeInOutSine'),
                               k(188, R()), k(198, R(x=PARK_TORSO), 'easeInOutSine')]},
        'sensor': {'rotation': [k(0, R(x=25)), k(52, R(x=25)), k(58, R(x=-2), 'easeOutCubic'), k(60, R(), 'easeInOutSine'),
                                k(170, R()), k(174, R(x=12), 'easeOutCubic'), k(178, R(y=22), 'easeInOutSine'),
                                k(182, R(y=-22), 'easeInOutSine'), k(185, R(), 'easeInOutSine'),
                                k(188, R()), k(198, R(x=PARK_SENSOR), 'easeInOutSine')]},
    }, **both_arms([k(0, R(x=40, z=20)), k(50, R(x=40, z=20)), k(58, R(x=-3), 'easeInOutCubic'), k(61, R(), 'easeInOutSine')],
                   [k(0, R(x=80)), k(52, R(x=80)), k(59, R(x=-3), 'easeInOutCubic'), k(62, R(), 'easeInOutSine'),
                    k(190, R()), k(198, R(x=PARK_FORE), 'easeInOutSine')], lag=1))
    bones.update(clamps([k(0, [40]), k(190, [40]), k(194, [0], 'easeInQuad'), k(195, [-2], 'easeOutQuad'), k(197, [0], 'easeInQuad')]))
    bones.update(lamps([(0, 0), (170, 1), (186, 0), (190, 1), (194, 0)], [(0, 0)]))
    effects = [(44, 'sound', snd('hydraulic_stand', 0.7, 0.8), None), (58, 'timeline', 'build:stand', None),
               (174, 'sound', snd('scan_chirp', 0.4, 0.9), None), (191, 'sound', snd('ding', 0.4, 0.9), None),
               (194, 'sound', snd('clamp_close', 0.6, 0.9), None), (197, 'sound', snd('sigh', 0.5, 0.8), None)]
    for slot, T0, socks in BUILD_SLOTS:
        for sb in socks:
            o = outward(sb)
            bones[sb] = {'scale': [k(0, S(0), interp='step'), k(T0, S(1), interp='step')],
                         'position': [k(0, P(*[6 * c for c in o])), k(T0, P(*[6 * c for c in o])), k(T0 + 3, P(), 'easeInQuad'),
                                      k(T0 + 4, P(*[1.0 * c for c in o]), 'easeOutQuad'), k(T0 + 5, P(), 'easeInQuad')]}
        effects.append((T0 + 3, 'timeline', 'build:seat:%s' % slot, None))
    return {'length': 200, 'loop': 'hold', 'feet': planted(), 'bones': bones, 'effects': effects, 'pilot': False}


def verbs():
    """The Hopper's arm-verb tables at the Bulwark's aim (upper arm 45 forward, forearm hanging, so a module on the
    forearm's front points ahead): one source for both frames."""
    HA.AIM_ARM, HA.AIM_FORE = 70.0, -70.0
    return HA.verbs_left()


# ==================================================================================================================
# Frames
# ==================================================================================================================
BULWARK_FRAME = dict(
    model=MODEL, speed=0.11, scale=1.0, toe=10.0, heel=7.0, step='heavy',
    walk=dict(n=24, stance=12, lift=4.5, swing_delay=1.0, up_exp=0.8, plant_ang=(8, 3), swing_ang=(-20, 10),
              roll=2.0, roll_ph=3, torso_roll=3.0, torso_ph=5, torso_bob=0.5, bob_lag=2, arm=10.0, arm_ph=3,
              fore=5.0, fore_ph=6, carry_bob=1.5, carry_lag=3, clamp=0.6, clamp_tick=2, lean=-3, bob=BOB),
    walk_b=dict(n=18, stance=11, lift=7.0, swing_delay=1.0, up_exp=0.7, plant_ang=(6, 2), swing_ang=(-20, 8),
                roll=2.5, roll_ph=4, torso_roll=3.5, torso_ph=6, torso_bob=0.6, bob_lag=2, arm=10.0, arm_ph=3,
                fore=6.0, fore_ph=6, carry_bob=2.0, carry_lag=3, clamp=0.8, clamp_tick=2, lean=-3, bob=BOB_B),
    # walk_c (plant_z, lift, h_up, surge in the frame's own px, not scaled; bob in Bulwark px): 8 ticks a step (30.4 px
    # = 0.87 of the 35 px leg, 1.90 blocks), each boot on the ground 10 ticks (two ticks of double support after each
    # plant), the hips 7 px over their rest height (33 of the leg's 35 above the ankle), the heel planted 17.5 px
    # ahead, the heel off at 8 and the boot on its toe to 20 degrees at toe-off
    walk_c=dict(n=16, stance=10, plant_z=-17.5, hs=14.0, flat=2, ho=8, to=20.0, roll_exp=1.4, lift=5.0, swing_delay=1.0,
                up_exp=0.8, h_up=7.0, surge=0.4, surge_ph=6, bob=[-1.0, -1.6, -1.2, -0.6, 0.0, 0.3, 0.2, -0.4],
                roll=2.4, roll_ph=3, torso_roll=3.0, torso_ph=5, torso_bob=0.6, bob_lag=2, arm=12.0, arm_ph=4,
                fore=7.0, fore_ph=6, carry_bob=1.5, carry_lag=3, clamp=0.6, clamp_tick=2, lean=-3),
    limp_up=7.0,                    # the limp's hips over their rest height (Bulwark px): the leg check
    hold=(HOLD_ARM, HOLD_FORE, HOLD_IN), hold_torso=HOLD_TORSO, reach=dict(REACH), grab=dict(GRAB), clamp_open=1.0, vent_axis='z',
)


def resample(seq, m):
    """A periodic per-tick curve stretched to m entries (linear)."""
    n = len(seq)
    if m == n:
        return list(seq)
    out = []
    for i in range(m):
        x = i * n / float(m)
        j = int(x)
        f = x - j
        out.append(round(seq[j % n] * (1 - f) + seq[(j + 1) % n] * f, 3))
    return out


def use_frame(geo):
    """Point every clip at a frame: its geo module (rig numbers, bones) and its ANIM parameters over the Bulwark's.
    A walk whose timing differs from the Bulwark's gets the Bulwark's drop-and-catch curve resampled to its step
    (in Bulwark pixels; scale_positions sizes it with the rest)."""
    g = globals()
    own = getattr(geo, 'ANIM', {})
    fr = dict(BULWARK_FRAME, **{kk: v for kk, v in own.items() if kk not in ('walk', 'walk_b', 'walk_c')})
    for wk in ('walk', 'walk_b', 'walk_c'):
        w = dict(BULWARK_FRAME[wk], **own.get(wk, {}))
        if 'bob' not in own.get(wk, {}):
            w['bob'] = resample(BULWARK_FRAME[wk]['bob'], w['n'] // 2)
        fr[wk] = w
    g.update(G=geo, F=fr, MODEL=fr['model'], SC=fr['scale'], TOE=fr['toe'], HEEL=fr['heel'], STEP=fr['step'],
             CLAMP_OPEN=fr['clamp_open'], VENT_AXIS=fr['vent_axis'], HOLD_TORSO=fr['hold_torso'],
             REACH=dict(fr['reach']), GRAB=dict(fr['grab']))
    g['HOLD_ARM'], g['HOLD_FORE'], g['HOLD_IN'] = fr['hold']
    g['V_WALK'] = fr['speed'] * 2.16 * 16
    g['V_LIMP'] = g['V_WALK'] * 0.3
    g['REST_FOOT_T'] = (geo.REST_ANKLE[0], geo.REST_ANKLE[1], 0.0)
    g['BOB'], g['BOB_B'] = fr['walk']['bob'], fr['walk_b']['bob']
    g['WALK_N'], g['WALK_STANCE'] = fr['walk']['n'], fr['walk']['stance']
    g['WALKB_N'], g['WALKB_STANCE'] = fr['walk_b']['n'], fr['walk_b']['stance']
    return fr


def scale_positions(clip):
    """Bulwark-pixel position keys to the frame's size: every bone's position channel except the jar's travel (the
    jar is the jar at any size), and authored ankle targets as offsets from the frame's rest ankle. A clip marked
    native (authored for its own frame) is left alone."""
    if SC == 1.0 or clip.get('native'):
        return
    for bone, chans in clip['bones'].items():
        if bone == 'jar_anchor' or 'position' not in chans:
            continue
        chans['position'] = [(t, [round(c * SC, 3) for c in v], e, a, i) for (t, v, e, a, i) in chans['position']]
    feet = clip.get('feet')
    if isinstance(feet, dict):
        rz, ry = G.REST_ANKLE
        for side in feet:
            if clip.get('feet_rel'):
                feet[side] = [(t, (v[0] * SC, v[1] * SC, v[2]), e, a, i) for (t, v, e, a, i) in feet[side]]
            else:
                feet[side] = [(t, (rz + (v[0] - rz) * SC, ry + (v[1] - ry) * SC, v[2]), e, a, i)
                              for (t, v, e, a, i) in feet[side]]


def clips():
    out = [('parked', parked()), ('board', board()), ('boot', boot()), ('boot_legs', boot_legs()), ('shutdown', shutdown()),
           ('idle', idle()), ('idle_tic', idle_tic()), ('walk', walk()), ('carry_walk', walk(carry=True)), ('walk_b', walk_b()),
           ('carry_walk_b', walk_b(carry=True)), ('walk_c', walk_c()), ('carry_walk_c', walk_c(carry=True)),
           ('limp', limp()),
           ('turn_l', turn('l')), ('turn_r', turn('r')), ('jump', jump()), ('fall', fall()), ('land', land(False)),
           ('land_heavy', land(True)), ('grab', grab()), ('hold', hold()), ('throw', throw()), ('grab_refused', grab_refused())]
    if MODEL == BULWARK_FRAME['model']:
        out += [('grab_high', grab_high()), ('hold_high', hold_high()), ('carry_walk_high', walk_c(carry=True, high=True)),
                ('throw_high', throw_high())]
    for name, c in verbs().items():
        out.append(('%s_l' % name, c))
        out.append(('%s_r' % name, side_clip(c)))
    out += [('vent', vent()), ('hurt', hurt()), ('eject', eject()), ('destroyed', destroyed()), ('build', build())]
    return out


CHECKED = {}


def report_checks():
    """Print the leg check for every walk-like clip; fail (exit 1) if any clip other than the legacy walks fails."""
    fail = False
    for name, (bad, worst) in CHECKED.items():
        tag = 'ok' if not bad else ('LEGACY, %d bad ticks' % len(bad) if name in LEGACY_WALKS else 'FAIL, %d bad ticks' % len(bad))
        sys.stderr.write('legs %-16s shin min %5.1f deg, knee min %5.1f px: %s %s\n'
                         % (name, worst[0], worst[1], tag, bad[:4] if bad else ''))
        fail = fail or (bool(bad) and name not in LEGACY_WALKS)
    if fail:
        sys.exit('leg check failed (SHIN_MIN %.0f deg, KNEE_MIN %.1f px x scale)' % (SHIN_MIN, KNEE_MIN))


def build_anims(model, baked=False):
    groups = {g['name']: g['uuid'] for g in model['groups']}
    anims = []
    for name, clip in clips():
        clip = copy.deepcopy(clip)
        scale_positions(clip)
        if clip.get('feet') is not None and any(w in name for w in ('walk', 'limp', 'turn')):
            bad, worst = check_legs(name, clip)
            CHECKED[name] = (bad, worst)
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
        if baked and 'standin_cow_held' in groups:
            # review copy only: a cow on the ground in front until the grip closes, then in the hands until thrown
            held = {'grab': [(0, 0), (19, 1)], 'hold': [(0, 1)], 'carry_walk': [(0, 1)], 'carry_walk_b': [(0, 1)],
                    'carry_walk_c': [(0, 1)], 'throw': [(0, 1), (4, 0)], 'grab_high': [(0, 0), (11, 1), (17, 0)]}.get(name, [(0, 0)])
            ground = {'grab': [(0, 1), (12, 0)]}.get(name, [(0, 0)])
            over = {'throw': [(0, 0), (6, 1), (11, 0)]}.get(name, [(0, 0)])
            high = {'grab_high': [(0, 0), (17, 1)], 'hold_high': [(0, 1)], 'carry_walk_high': [(0, 1)],
                    'throw_high': [(0, 1), (7, 0)]}.get(name, [(0, 0)])
            tabs = [('standin_cow_held', held), ('standin_cow_ground', ground), ('standin_cow_over', over)]
            if 'standin_cow_high' in groups:
                tabs.append(('standin_cow_high', high))
            for bone, tab in tabs:
                animators[groups[bone]] = {'name': bone, 'type': 'bone',
                                           'keyframes': [keyframe('scale', t, S(v), None, None, 'step') for t, v in tab]}
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
    report_checks()
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


use_frame(G)

if __name__ == '__main__':
    main()

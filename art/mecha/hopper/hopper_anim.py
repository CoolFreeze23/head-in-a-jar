"""The light-biped clip script ("biped_light" skeleton): every clip, keyed in ticks, written into a frame's bbmodel.
Run as it is for the Hopper; art/mecha/chimera/chimera_anim.py runs it for the Chimera (use_frame), adding the Graft.

Source of truth for the clips. Edit the tables and run it again; hand edits to the bbmodel's animations are
overwritten. Geometry and rig numbers come from the frame's geo module (hopper_geo.py here); the frame's ANIM dict
overrides the Hopper's parameters (HOPPER_FRAME below): speed, the position scale, the walk's timing and lift, toe
length. Position keys are authored in Hopper pixels and multiplied by the frame's `scale` before the legs are solved;
the legs are re-solved every tick for the frame's own thigh, shin and hip height.

    python art/mecha/hopper/hopper_anim.py                 # write the clips into the bbmodel
    python art/mecha/hopper/hopper_anim.py --preview OUT   # also a review copy: every eased segment baked into
                                                           # per-tick linear keys (GeckoLib 4.9.3's curves), the
                                                           # stand-in jar hidden when no pilot is aboard

Key tuples: (tick, value, easing, easing_args, interpolation). The easing shapes the move ARRIVING at that key
(GeckoLib rule). GeckoLib 4.9.3 names elastic and bounce the other way round from easings.net: a settle is
easeInElastic / easeInBounce (style guide D6, confirmed in the game on the chair).

Legs are never keyed by hand. Each clip gives foot targets (ankle position and sole angle, on the ground or relative
to the hip); the generator evaluates the body's eased curve at every tick, solves the two-bone leg (knee back) and
writes per-tick linear keys for leg_*, shin_* and foot_*, so a planted foot stays planted under any body motion.
"""
import copy
import json
import math
import os
import sys
import uuid as uuidlib

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import hopper_geo as G  # noqa: E402

MODEL = os.path.join(HERE, 'hopper.bbmodel')
T = 20.0

# design walking speed: Hopper speed attribute 0.13 x 2.16 (vanilla ground movement) blocks a
# tick, in pixels a tick. The walk is authored at this speed; the game scales playback by measured / design.
V_WALK = 0.13 * 2.16 * 16           # 4.49 px a tick (5.6 blocks a second)
V_LIMP = V_WALK * 0.3               # flat battery: MechaLogic.riddenSpeed x 0.3
REST_FOOT_T = (G.REST_ANKLE[0], G.REST_ANKLE[1], 0.0)
TOE = 6.0                           # ankle to toe tip along the sole


def k(t, v, e=None, a=None, interp='linear'):
    return (t, list(v) if isinstance(v, (list, tuple)) else [v, v, v], e, a, interp)


def R(x=0, y=0, z=0):
    return [x, y, z]


def P(x=0, y=0, z=0):
    return [x, y, z]


def S(s):
    return [s, s, s]


def lamp(on_ticks):
    return [k(t, S(1 if on else 0), interp='step') for t, on in on_ticks]


def const(v):
    return [k(0, v)]


# ------------------------------------------------------------------------------------------------------------------
# GeckoLib 4.9.3 easing curves (EasingType.java), as in art/chair/chair_anim.py
# ------------------------------------------------------------------------------------------------------------------
def _base(name, arg):
    n = name.lower()
    fam = n.replace('easeinout', '').replace('easein', '').replace('easeout', '')
    if fam == 'sine':
        f = lambda t: 1 - math.cos(t * math.pi / 2)
    elif fam == 'quad':
        f = lambda t: t * t
    elif fam == 'cubic':
        f = lambda t: t ** 3
    elif fam == 'quart':
        f = lambda t: t ** 4
    elif fam == 'expo':
        f = lambda t: 2 ** (10 * (t - 1))
    elif fam == 'back':
        n2 = 1.70158 if arg is None else arg * 1.70158
        f = lambda t: t * t * ((n2 + 1) * t - n2)
    elif fam == 'elastic':
        n2 = 1 if arg is None else arg
        f = lambda t: 1 - math.cos(t * math.pi / 2) ** 3 * math.cos(t * n2 * math.pi)
    elif fam == 'bounce':
        b = 0.5 if arg is None else arg
        f = lambda x: min(121 / 16 * x * x, 121 / 4 * b * (x - 6 / 11) ** 2 + 1 - b,
                          121 * b * b * (x - 9 / 11) ** 2 + 1 - b * b, 484 * b ** 3 * (x - 10.5 / 11) ** 2 + 1 - b ** 3)
    else:
        raise ValueError(name)
    if n.startswith('easeinout'):
        return lambda t: f(t * 2) / 2 if t < 0.5 else 1 - f((1 - t) * 2) / 2
    if n.startswith('easeout'):
        return lambda t: 1 - f(1 - t)
    return f


def ease(name, args, t):
    if not name or name.lower() in ('linear', 'none'):
        return t
    return _base(name, args[0] if args else None)(t)


def value_at(keys, t):
    """GeckoLib evaluation of one channel at tick t (keys sorted)."""
    if t <= keys[0][0]:
        return list(keys[0][1])
    for i in range(1, len(keys)):
        t0, v0, _, _, i0 = keys[i - 1]
        t1, v1, e, a, i1 = keys[i]
        if t <= t1:
            if i0 == 'step' or i1 == 'step':
                return list(v0) if t < t1 else list(v1)
            f = ease(e, a, (t - t0) / float(t1 - t0))
            return [v0[j] + (v1[j] - v0[j]) * f for j in range(3)]
    return list(keys[-1][1])


# ------------------------------------------------------------------------------------------------------------------
# Leg solver
# ------------------------------------------------------------------------------------------------------------------
def solve_legs(clip):
    """Adds per-tick keys for leg_*, shin_* and foot_* from the clip's foot targets and its eased hips curve."""
    feet = clip.get('feet')
    if feet is None:
        return
    L = clip['length']
    until = clip.get('ik_until', L)
    hips = clip['bones'].get('hips', {})
    hpos = sorted(hips.get('position', [k(0, P())]), key=lambda x: x[0])
    hrot = sorted(hips.get('rotation', [k(0, R())]), key=lambda x: x[0])
    rel = clip.get('feet_rel', False)
    level = clip.get('level_legs', False)
    out = {s: {'leg': [], 'shin': [], 'foot': [], 'roll': []} for s in 'lr'}
    for t in range(0, int(until) + 1):
        hp = value_at(hpos, t)
        pitch = value_at(hrot, t)[0]
        hip = (hp[2], G.HIP_Y + hp[1])
        for s in 'lr':
            if callable(feet):
                z, y, ang = feet(s, t)
            else:
                z, y, ang = value_at(sorted(feet[s], key=lambda x: x[0]), t)
            ank = (hip[0] + z, hip[1] + y) if rel else (z, y)
            if level:
                ank, hip_s, roll = level_hip(hip, ank, s, value_at(hrot, t)[2], rel)
                out[s]['roll'].append((t, -roll))
                t1, t2 = G.ik(hip_s, ank)
            else:
                t1, t2 = G.ik(hip, ank)
            leg = t1 - pitch
            shin = t2 - t1
            foot = ang - t2
            out[s]['leg'].append((t, leg - G.REST_THIGH))
            out[s]['shin'].append((t, shin - G.REST_SHIN))
            out[s]['foot'].append((t, foot - G.REST_FOOT))
    for s in 'lr':
        for part in ('leg', 'shin', 'foot'):
            keys = thin(out[s][part], clip.get('loop') == 'loop')
            if level and part == 'leg':
                clip['bones'].setdefault('leg_' + s, {})['rotation'] = level_keys(out[s]['leg'], out[s]['roll'],
                                                                                  clip.get('loop') == 'loop')
                continue
            clip['bones'].setdefault('%s_%s' % (part, s), {})['rotation'] = [k(t, R(x=round(v, 3))) for t, v in keys]


def level_hip(hip, ank, s, roll, rel, geo=None):
    """For a clip whose hips roll (rotation z) with the legs kept level under them (level_legs): the hip joint of side
    s rises or drops with the roll (it sits HIP_X out from the hips' pivot) and the thigh is counter-rolled by the same
    angle, so the leg swings in a vertical plane and a planted foot does not slide sideways or lift. Returns the ankle
    target, that side's hip joint and the roll (geo: the frame's rig module, this script's by default)."""
    hx = (geo or G).HIP_X
    dy = (-hx if s == 'l' else hx) * math.sin(math.radians(roll))
    hip_s = (hip[0], hip[1] + dy)
    if rel:
        ank = (ank[0], ank[1] + dy)
    return ank, hip_s, roll


def level_keys(leg, roll, loop):
    """A thigh's x keys (the solve) and z keys (the counter-roll) on the ticks either needs."""
    xs, zs = dict(leg), dict(roll)
    ts = sorted(set(t for t, _ in thin(leg, loop)) | set(t for t, _ in thin(roll, loop)))
    return [k(t, R(x=round(xs[t], 3), z=round(zs[t], 3))) for t in ts]


def thin(keys, loop):
    """Drops keys that linear interpolation between their neighbours reproduces within 0.05 degrees."""
    if len(keys) <= 2:
        return keys
    out = [keys[0]]
    for i in range(1, len(keys) - 1):
        t0, v0 = out[-1]
        t1, v1 = keys[i]
        t2, v2 = keys[i + 1]
        lin = v0 + (v2 - v0) * (t1 - t0) / float(t2 - t0)
        if abs(lin - v1) > 0.05:
            out.append(keys[i])
        else:
            # check every dropped key since the last kept one still fits
            ok = all(abs(v0 + (v2 - v0) * (tt - t0) / float(t2 - t0) - vv) <= 0.05
                     for tt, vv in keys if t0 < tt <= t1)
            if not ok:
                out.append(keys[i])
    out.append(keys[-1])
    return out


def planted(zl=None, zr=None, ang=0.0):
    a = REST_FOOT_T
    return {'l': [k(0, (a[0] if zl is None else zl, a[1], ang))], 'r': [k(0, (a[0] if zr is None else zr, a[1], ang))]}


def on_toe(z, ang):
    """Ankle position when the foot pivots on its toe (ang < 0, toe down) or heel (ang > 0)."""
    lift = TOE * math.sin(math.radians(-ang)) if ang < 0 else 0.0
    return (z, G.ANKLE_H + lift, ang)


# ------------------------------------------------------------------------------------------------------------------
# Arms: authored for the left arm; mirrored for the right (rotation y and z negated, position x negated)
# ------------------------------------------------------------------------------------------------------------------
def mirror_keys(keys, channel):
    out = []
    for (t, v, e, a, i) in keys:
        if channel == 'rotation':
            v = [v[0], -v[1], -v[2]]
        elif channel == 'position':
            v = [-v[0], v[1], v[2]]
        out.append((t, v, e, a, i))
    return out


def side_clip(clip_l):
    """The right arm's version of a left-arm verb clip."""
    c = copy.deepcopy(clip_l)
    bones = {}
    for bone, chans in c['bones'].items():
        nb = bone.replace('_l', '_r') if bone.endswith('_l') else bone
        bones[nb] = {ch: mirror_keys(keys, ch) for ch, keys in chans.items()}
    c['bones'] = bones
    c['effects'] = [(t, kind, eff.replace(':l', ':r').replace('fx_wrist_l', 'fx_wrist_r'),
                     loc.replace('_l', '_r') if loc else loc) for (t, kind, eff, loc) in c['effects']]
    return c


def snd(name, vol=0.5, pitch=1.0):
    return 'headinajar:mecha.%s|%s|%s' % (name, vol, pitch)


# ==================================================================================================================
# Poses
# ==================================================================================================================
PARK_HIPS = -8.0
PARK_TORSO = -6.0
PARK_SENSOR = -20.0
PARK_FOREARM = -20.0
LAMPS_OFF = {'lamp_core': lamp([(0, 0)]), 'lamp_l': lamp([(0, 0)]), 'lamp_r': lamp([(0, 0)]),
             'lamp_sensor': lamp([(0, 0)])}


def lamps(core, side, sensor):
    """Step tables for the four lamps."""
    return {'lamp_core': {'scale': lamp(core)}, 'lamp_l': {'scale': lamp(side)}, 'lamp_r': {'scale': lamp(side)},
            'lamp_sensor': {'scale': lamp(sensor)}}


def clamps(keys):
    """Clamp pads hanging on short arms from the hoop's rails: o = degrees open (the pad swings out, away from the
    jar's shoulder)."""
    left = [(t, [0, 0, -o[0]], e, a, i) for (t, o, e, a, i) in keys]
    right = [(t, [0, 0, o[0]], e, a, i) for (t, o, e, a, i) in keys]
    return {'clamp_l': {'rotation': left}, 'clamp_r': {'rotation': right}}


def vents_to_z(clip):
    """The vent flaps hang on the pod's rear flanks, hinged along their top edge (the z axis). Clips are written with
    the opening as a negative x value, as for a flap on the back; here that becomes z (left negative, right
    positive), so the bottom of each flap swings outward."""
    for side, sign in (('vent_l', 1), ('vent_r', -1)):
        ch = clip['bones'].get(side)
        if ch and 'rotation' in ch:
            ch['rotation'] = [(t, [0, 0, sign * v[0]], e, a, i) for (t, v, e, a, i) in ch['rotation']]


# ==================================================================================================================
# Clips
# ==================================================================================================================
def parked():
    """Unpiloted: sagged on its hydraulics, restraint shut, the chin lamp bowed. Alive: the sensor drifts a few
    degrees, the standby lamp blips once, a vent flap ticks."""
    return {
        'length': 80, 'loop': 'loop', 'feet': planted(),
        'bones': dict({
            'hips': {'position': [k(0, P(y=PARK_HIPS)), k(40, P(y=PARK_HIPS - 0.5), 'easeInOutSine'),
                                  k(80, P(y=PARK_HIPS), 'easeInOutSine')]},
            'torso': {'rotation': [k(0, R(x=PARK_TORSO)), k(44, R(x=PARK_TORSO - 0.6), 'easeInOutSine'),
                                   k(80, R(x=PARK_TORSO), 'easeInOutSine')]},
            'sensor': {'rotation': [k(0, R(x=PARK_SENSOR)), k(30, R(x=PARK_SENSOR, y=4), 'easeInOutSine'),
                                    k(60, R(x=PARK_SENSOR + 2, y=-3), 'easeInOutSine'),
                                    k(80, R(x=PARK_SENSOR), 'easeInOutSine')]},
            'arm_l': {'rotation': const(R())}, 'arm_r': {'rotation': const(R())},
            'forearm_l': {'rotation': const(R(x=PARK_FOREARM))}, 'forearm_r': {'rotation': const(R(x=PARK_FOREARM))},
            'restraint': {'rotation': const(R())},
            'vent_l': {'rotation': [k(0, R()), k(50, R()), k(52, R(x=-4), 'easeOutQuad'), k(55, R(), 'easeInQuad')]},
        }, **lamps([(0, 1), (3, 0)], [(0, 0)], [(0, 0)])),
        'effects': [(1, 'sound', snd('standby_blip', 0.15, 1.4), None),
                    (52, 'sound', snd('relay_tick', 0.15, 1.2), None)],
        'pilot': False,
    }


def board():
    """Controller "cockpit": the clamps snap open, the restraint (the hoop) swings up and back on its hinge, the jar
    is set down into the collar (held high and forward until the hoop has passed), the hoop comes down onto the lid
    and its hooks catch the lid's front corners, and the clamp pads close on the jar's shoulders.
    Control is the pilot's from tick 0 (the legs controller keeps playing idle or walk)."""
    bones = {
        'restraint': {'rotation': [k(0, R()), k(1, R(x=-2), 'easeOutQuad'), k(2, R(x=-2)),
                                   k(8, R(x=115), 'easeOutCubic'), k(10, R(x=108), 'easeInOutSine'),
                                   k(13, R(x=108)), k(14, R(x=111), 'easeOutQuad'),
                                   k(18, R(), 'easeInQuad'),                       # the hoop lands on the lid
                                   k(19, R(x=2), 'easeOutQuad'), k(21, R(), 'easeInQuad')]},
        'jar_anchor': {'position': [k(0, P(y=20, z=-12)), k(6, P(y=20, z=-12)),
                                    k(10, P(y=1.5), 'easeInOutSine'), k(11, P(), 'easeInQuad'),  # seated
                                    k(12, P(y=0.4), 'easeOutQuad'), k(13, P(), 'easeInQuad')]},
    }
    bones.update(clamps([k(0, [0]), k(1, [0]), k(3, [30], 'easeOutQuad'), k(18, [30]),
                         k(20, [0], 'easeInQuad'), k(21, [-2], 'easeOutQuad'), k(22, [0], 'easeInQuad')]))
    return {
        'length': 22, 'loop': 'hold', 'bones': bones,
        'effects': [(1, 'sound', snd('bar_unlatch', 0.5, 1.0), None),
                    (3, 'sound', snd('servo', 0.4, 1.2), None),
                    (11, 'sound', snd('jar_seat', 0.6, 1.0), None),
                    (14, 'sound', snd('servo', 0.4, 1.0), None),
                    (18, 'sound', snd('bar_latch', 0.6, 1.0), None),
                    (20, 'sound', snd('clamp_close', 0.6, 1.0), None),
                    (21, 'particle', 'cloud', 'fx_latch')],
    }


def boot():
    """Controller "cockpit" (over live controls): lamps light from the core outward, the chin sensor sweeps (too
    fast, overshoots, corrects: the eager intern), each arm flexes in turn, the vents cough, the jingle."""
    arm_l = {'rotation': [k(0, R()), k(12, R()), k(13, R(x=-5), 'easeOutQuad'), k(17, R(x=42), 'easeOutCubic'),
                          k(19, R(x=38), 'easeInOutSine'), k(24, R(), 'easeInOutSine')]}
    fore_l = {'rotation': [k(0, R()), k(14, R()), k(18, R(x=35), 'easeOutCubic'), k(21, R(x=30), 'easeInOutSine'),
                           k(25, R(), 'easeInOutSine')]}

    def later(ch, d):
        return {'rotation': [(t + d if t else 0, mirror_keys([(t, v, e, a, i)], 'rotation')[0][1], e, a, i)
                             for (t, v, e, a, i) in ch['rotation']]}
    return {
        'length': 40, 'loop': 'hold',
        'bones': dict({
            'sensor': {'rotation': [k(0, R(x=PARK_SENSOR)), k(6, R(x=2), 'easeOutCubic'), k(8, R(), 'easeInOutSine'),
                                    k(11, R(y=38), 'easeOutCubic'), k(13, R(y=30), 'easeInOutSine'),
                                    k(17, R(y=-38), 'easeInOutCubic'), k(19, R(y=-30), 'easeInOutSine'),
                                    k(23, R(x=15), 'easeInOutCubic'), k(25, R(x=12), 'easeInOutSine'),
                                    k(29, R(), 'easeInOutSine')]},
            'arm_l': arm_l, 'forearm_l': fore_l, 'arm_r': later(arm_l, 4), 'forearm_r': later(fore_l, 4),
            'vent_l': {'rotation': [k(0, R()), k(33, R()), k(35, R(x=-25), 'easeOutQuad'), k(38, R(), 'easeInQuad')]},
            'vent_r': {'rotation': [k(0, R()), k(34, R()), k(36, R(x=-25), 'easeOutQuad'), k(39, R(), 'easeInQuad')]},
        }, **lamps([(0, 0), (2, 1)], [(0, 0), (5, 1)], [(0, 0), (8, 1)])),
        'effects': [(0, 'sound', snd('servo_chord', 0.6, 1.0), None),
                    (2, 'sound', snd('relay_click', 0.4, 1.0), None),
                    (5, 'sound', snd('relay_click', 0.4, 1.1), None),
                    (8, 'sound', snd('relay_click', 0.4, 1.2), None),
                    (11, 'sound', snd('servo_tick', 0.3, 1.3), None),
                    (13, 'sound', snd('servo', 0.4, 1.1), None),
                    (17, 'sound', snd('servo_tick', 0.3, 1.2), None),
                    (18, 'sound', snd('servo', 0.4, 1.0), None),   # arm_r starts at 17-18
                    (23, 'sound', snd('servo_tick', 0.3, 1.4), None),
                    (35, 'particle', 'cloud', 'fx_vent_l'),
                    (36, 'particle', 'cloud', 'fx_vent_r'),
                    (38, 'timeline', 'boot:done', None)],
    }


def boot_legs():
    """Controller "main", only while the pilot stands still during the boot: stands up from the parked sag with one
    hydraulic groan, shudders, then taps each foot in turn. Any movement input switches main to walk."""
    a = REST_FOOT_T
    feet = {'l': [k(0, a), k(26, a), k(28, (a[0] - 2, a[1] + 4, 12), 'easeOutCubic'), k(30, a, 'easeInQuad')],
            'r': [k(0, a), k(31, a), k(33, (a[0] - 2, a[1] + 4, 12), 'easeOutCubic'), k(35, a, 'easeInQuad')]}
    return {
        'length': 40, 'loop': 'hold', 'feet': feet,
        'bones': {
            'hips': {'position': [k(0, P(y=PARK_HIPS)), k(4, P(y=PARK_HIPS - 0.4), 'easeOutQuad'),
                                  k(14, P(y=0.8), 'easeInOutCubic'), k(17, P(), 'easeInOutSine'),
                                  k(29, P()), k(30, P(y=-0.6), 'easeOutQuad'), k(32, P(), 'easeInOutSine'),
                                  k(35, P(y=-0.6), 'easeOutQuad'), k(37, P(), 'easeInOutSine')]},
            'torso': {'rotation': [k(0, R(x=PARK_TORSO)), k(6, R(x=PARK_TORSO)), k(16, R(x=1), 'easeInOutCubic'),
                                   k(19, R(), 'easeInOutSine')],
                      'position': [k(0, P()), k(17, P()), k(18, P(x=0.4), 'easeInOutSine'),
                                   k(19, P(x=-0.4), 'easeInOutSine'), k(20, P(x=0.3), 'easeInOutSine'),
                                   k(21, P(), 'easeInOutSine')]},
            'forearm_l': {'rotation': [k(0, R(x=PARK_FOREARM)), k(10, R(), 'easeInOutSine')]},
            'forearm_r': {'rotation': [k(0, R(x=PARK_FOREARM)), k(11, R(), 'easeInOutSine')]},
        },
        'effects': [(4, 'sound', snd('hydraulic_stand', 0.6, 1.0), None),
                    (18, 'sound', snd('rattle', 0.3, 1.0), None),
                    (30, 'timeline', 'tap:l', None),
                    (35, 'timeline', 'tap:r', None)],
    }


def shutdown():
    """Main: lamps out in reverse (sensor, sides, core), the arms lower, the hull sags onto its hydraulics with a sigh
    into the parked pose."""
    return {
        'length': 20, 'loop': 'hold', 'feet': planted(),
        'bones': dict({
            'hips': {'position': [k(0, P()), k(2, P(y=0.5), 'easeOutQuad'), k(14, P(y=PARK_HIPS - 0.5), 'easeInQuad'),
                                  k(18, P(y=PARK_HIPS), 'easeInOutSine')]},
            'torso': {'rotation': [k(0, R()), k(4, R()), k(16, R(x=PARK_TORSO - 0.5), 'easeInOutSine'),
                                   k(20, R(x=PARK_TORSO), 'easeInOutSine')]},
            'sensor': {'rotation': [k(0, R()), k(3, R()), k(15, R(x=PARK_SENSOR), 'easeInOutSine')]},
            'forearm_l': {'rotation': [k(0, R()), k(8, R(x=PARK_FOREARM), 'easeInOutSine')]},
            'forearm_r': {'rotation': [k(0, R()), k(9, R(x=PARK_FOREARM), 'easeInOutSine')]},
            'arm_l': {'rotation': const(R())}, 'arm_r': {'rotation': const(R())},
        }, **lamps([(0, 1), (10, 0)], [(0, 1), (6, 0)], [(0, 1), (2, 0)])),
        'effects': [(0, 'sound', snd('power_down', 0.6, 1.0), None),
                    (14, 'sound', snd('sigh', 0.5, 1.0), None),
                    (15, 'particle', 'cloud', 'fx_vent_l')],
    }


def idle():
    """Piloted, standing: breathes on its hydraulics (0.6 px over 60 ticks, the torso a beat behind), the forearms
    flex a hair. The sensor is free for the look (driven by the game code, not keyed here)."""
    return {
        'length': 60, 'loop': 'loop', 'feet': planted(),
        'bones': {
            'hips': {'position': [k(0, P()), k(30, P(y=-0.6), 'easeInOutSine'), k(60, P(), 'easeInOutSine')]},
            'torso': {'rotation': [k(0, R(x=0.3)), k(6, R(x=0.4), 'easeInOutSine'), k(36, R(x=-0.8), 'easeInOutSine'),
                                   k(60, R(x=0.3), 'easeInOutSine')]},
            'forearm_l': {'rotation': [k(0, R()), k(38, R(x=-2), 'easeInOutSine'), k(60, R(), 'easeInOutSine')]},
            'forearm_r': {'rotation': [k(0, R()), k(40, R(x=-2), 'easeInOutSine'), k(60, R(), 'easeInOutSine')]},
        },
        'effects': [],
    }


def idle_tic():
    """Played by the game every 200-300 ticks of idle: bounces on its toes once."""
    a = REST_FOOT_T
    toe = on_toe(a[0], -20)
    feet = {s: [k(0, a), k(3, a), k(7, toe, 'easeOutCubic'), k(9, toe), k(12, a, 'easeInQuad')] for s in 'lr'}
    return {
        'length': 20, 'loop': 'once', 'feet': feet,
        'bones': {
            'hips': {'position': [k(0, P()), k(2, P(y=-1.2), 'easeOutQuad'), k(3, P(y=-1.2)),
                                  k(7, P(y=2.2), 'easeOutCubic'), k(9, P(y=2.2)), k(12, P(), 'easeInQuad'),
                                  k(14, P(y=-0.8), 'easeOutQuad'), k(17, P(), 'easeInOutSine')]},
            'torso': {'rotation': [k(0, R()), k(4, R(x=-2), 'easeOutQuad'), k(8, R(x=1.5), 'easeOutCubic'),
                                   k(13, R(x=-1), 'easeInOutSine'), k(18, R(), 'easeInOutSine')]},
            'arm_l': {'rotation': [k(0, R()), k(4, R(x=-6), 'easeOutQuad'), k(9, R(x=12), 'easeOutCubic'),
                                   k(14, R(x=-3), 'easeInOutSine'), k(19, R(), 'easeInOutSine')]},
            'arm_r': {'rotation': [k(0, R()), k(4, R(x=-6), 'easeOutQuad'), k(10, R(x=12), 'easeOutCubic'),
                                   k(15, R(x=-3), 'easeInOutSine'), k(20, R(), 'easeInOutSine')]},
        },
        'effects': [(3, 'sound', snd('servo_tick', 0.3, 1.4), None), (12, 'timeline', 'tap:both', None)],
    }


# ---- walking -------------------------------------------------------------------------------------------------------
WALK_N = 12          # ticks a cycle (two steps); one step 6 ticks
WALK_STANCE = 4      # ticks a foot is down: a running hop with a 2-tick flight between steps
WALK_REACH = V_WALK * WALK_STANCE / 2.0
BOB = [-0.8, -2.0, -2.2, -1.4, 0.0, 1.0]


def walk_foot(s):
    """(z, y, sole angle) of one ankle at phase s (0 = this foot plants), ground frame."""
    s = s % WALK_N
    zf = G.REST_ANKLE[0]
    if s <= WALK_STANCE:
        z = zf - WALK_REACH + V_WALK * s
        i = int(s)
        ang = 6 if i == 0 else (-18 if i == WALK_STANCE else (-6 if i == WALK_STANCE - 1 else 0))
        return on_toe(z, ang)
    u = (s - WALK_STANCE) / float(WALK_N - WALK_STANCE)
    z = zf + WALK_REACH - 2 * WALK_REACH * (1 - math.cos(math.pi * u)) / 2
    lift = WALK_LIFT * math.sin(math.pi * u)
    ang = -18 * math.sin(math.pi * min(1, u / 0.35)) if u < 0.35 else (10 * (u - 0.35) / 0.55 if u < 0.9 else 6 + 4 * (1 - u) / 0.1)
    zz, yy, aa = on_toe(z, ang)
    return (zz, yy + lift, aa)


def walk():
    feet = lambda side, t: walk_foot(t if side == 'l' else t - WALK_N // 2)
    c = lambda t, ph=0, n=WALK_N: math.cos(2 * math.pi * (t - ph) / n)
    ticks = range(0, WALK_N + 1)
    h = WALK_N // 2
    return {
        'length': WALK_N, 'loop': 'loop', 'feet': feet,
        'bones': {
            'hips': {'position': [k(t, P(y=BOB[t % h])) for t in ticks],
                     'rotation': [k(t, R(z=1.5 * c(t, 2))) for t in ticks]},
            'torso': {'rotation': [k(t, R(x=-4 + 0.6 * BOB[(t - 1) % h], z=2.5 * c(t, 3))) for t in ticks]},
            'restraint': {'rotation': [k(t, R(x=0.6 if t % h == 1 else (0.2 if t % h == 2 else 0))) for t in ticks]},
            'arm_l': {'rotation': [k(t, R(x=-10 * c(t, 1))) for t in ticks]},
            'arm_r': {'rotation': [k(t, R(x=10 * c(t, 1))) for t in ticks]},
            'forearm_l': {'rotation': [k(t, R(x=-6 * c(t, 3))) for t in ticks]},
            'forearm_r': {'rotation': [k(t, R(x=6 * c(t, 3))) for t in ticks]},
        },
        'effects': [(0, 'timeline', 'step:l', None), (h, 'timeline', 'step:r', None)],
    }


LIMP_N = 24
LIMP_STANCE = 14


def limp_foot(s, drag):
    s = s % LIMP_N
    reach = V_LIMP * LIMP_STANCE / 2.0
    zf = G.REST_ANKLE[0]
    if s <= LIMP_STANCE:
        ang = -10 if (drag and s < 2) else (-8 if s >= LIMP_STANCE - 1 else 0)
        return on_toe(zf - reach + V_LIMP * s, ang)
    u = (s - LIMP_STANCE) / float(LIMP_N - LIMP_STANCE)
    if drag:
        # the bad leg: lifted 1 px, toe dragging, swung through late and planted flat
        uu = min(1, u * 1.25) ** 1.6
        z = zf + reach - 2 * reach * uu
        zz, yy, aa = on_toe(z, -22 + 12 * u)
        return (zz, yy + SC * 1.0 * math.sin(math.pi * u), aa)
    z = zf + reach - 2 * reach * (1 - math.cos(math.pi * u)) / 2
    zz, yy, aa = on_toe(z, -15 * math.sin(math.pi * u) + 4 * u)
    return (zz, yy + SC * 4 * math.sin(math.pi * u), aa)


def limp():
    """Out of power (battery 0, speed x 0.3): a dragging walk that replaces walk. The left leg drags its toe and
    plants late, the hips roll 5 degrees onto it, the left arm hangs, only the running lamps are lit."""
    feet = lambda side, t: limp_foot(t, True) if side == 'l' else limp_foot(t - LIMP_N // 2, False)
    ticks = range(0, LIMP_N + 1, 2)
    c = lambda t, ph: math.cos(2 * math.pi * (t - ph) / LIMP_N)
    bob = lambda t: -2.0 * math.exp(-((t % LIMP_N) - 3) ** 2 / 6.0) - 0.8 * math.exp(-(((t - 12) % LIMP_N) - 3) ** 2 / 6.0)
    return {
        'length': LIMP_N, 'loop': 'loop', 'feet': feet,
        'bones': dict({
            'hips': {'position': [k(t, P(y=round(bob(t) + bob(t + LIMP_N) * 0, 3))) for t in range(0, LIMP_N + 1)],
                     'rotation': [k(t, R(z=round(2.5 + 2.5 * c(t, 7), 3))) for t in ticks]},
            'torso': {'rotation': [k(t, R(x=-5, z=round(1.5 * c(t, 9), 3))) for t in ticks]},
            'arm_l': {'rotation': const(R(x=-5, z=-4))},
            'forearm_l': {'rotation': const(R(x=-55))},
            'arm_r': {'rotation': [k(t, R(x=round(6 * c(t, 2), 3))) for t in ticks]},
            'forearm_r': {'rotation': [k(t, R(x=round(4 * c(t, 4), 3))) for t in ticks]},
            'sensor': {'rotation': const(R(x=-10))},
        }, **lamps([(0, 0)], [(0, 1)], [(0, 0)])),
        'effects': [(0, 'timeline', 'step:l_drag', None), (12, 'timeline', 'step:r', None),
                    (16, 'sound', snd('servo_catch', 0.4, 0.8), None)],
    }


# ---- the damage limp: limp_l and limp_r (the side whose leg drags) ---------------------------------------------------
# Powered but badly damaged (the game plays it below a quarter of health in place of walk): authored at the walk's
# design speed on the walk's own cycle, so it plays through the walk's phase at the walk's playback rate and the feet
# do not slide. The bad leg is rolled onto its toe and dragged through late, the toe scraping, and planted flat; the
# good leg does the work. The hips dip and roll onto the bad leg as it takes the weight and are caught, and hitch up
# on that side before the drag; the torso follows a tick or two later; the bad arm hangs and hardly swings. The legs
# are counter-rolled under the rolling hips (level_legs). limp_r is limp_l mirrored and half a cycle on, so each
# foot plants on its own beat of the walk (the bad left foot on tick 0, the bad right foot on n / 2).
#
# Ticks are fractions of the cycle; lengths are Hopper px (times the frame's scale).
LIMP_GAIT = dict(
    sb=0.33, ho=0.17,               # the bad foot: down from 0 to sb, the heel lifting from ho (rolled onto the toe)
    pg=0.25, sg=0.58,               # the good foot: heel strike at pg (hurried off the bad leg), down for sg
    drag_lift=1.4, clear=0.5,       # the dragged foot: the ankle 1.4 px up on the toe, the toe tip at most 0.5 px up
    late=1.8,                       # how late the dragged foot swings through (1: even)
    hs=5.0, to=16.0, heel=4.0,      # the good foot's heel strike and toe-off (degrees), ankle to heel (px)
    lift=5.0,                       # the good foot's swing height
    crouch=2.5,                     # the hips carried this much lower than at rest
    dip_bad=2.4, dip_good=1.2,      # the drop into each plant (the bad one deeper), caught
    vault=0.8, hike=0.6,            # the rise over the good leg; the hitch up before the drag
    roll=5.0, hitch=1.2, lag=1,     # the roll onto the bad leg (degrees), the wind-up the other way, the torso's lag
    list=3.0, roll_w=2.6,           # leaning onto the bad leg all the while (degrees); how long the roll is held (ticks)
    surge=0.5,                      # the body slows over the bad leg and is pushed on by the good one (px)
    fwd_bad=1.0, fwd_good=-1.5,     # where each foot's stride is centred (px ahead of the rest ankle)
    lean=-5.0,                      # the torso's forward lean (the walk's is -4)
    arm_hang=-10.0, arm_in=3.0,     # the bad arm: hanging 10 degrees lower than the walk's, 3 in
    arm_bad=2.5, arm_good=12.0,     # swing amplitudes (the walk's 10)
)


def bump(t, c, w, n):
    """A periodic bump of height 1 centred on tick c, about w ticks wide, on a cycle of n ticks."""
    return sum(math.exp(-((t - c + j * n) / float(w)) ** 2) for j in (-1, 0, 1))


def smooth(u):
    return u * u * (3 - 2 * u)


def limp_ticks(n, g):
    """The gait's fractions of the cycle as whole ticks."""
    return dict(g, **{kk: int(n * g[kk] + 0.5) for kk in ('sb', 'ho', 'pg', 'sg')})


def drag_foot(s, n, v, z0, sb, ho, drag, clear, late, toe, ankle_h):
    """The damaged leg's ankle (z, y, sole angle) through a cycle of n ticks, planted flat at s = 0 with the ankle at
    z0 and carried back at v a tick. Flat until ho; then the heel lifts and the foot rolls about its toe tip, which
    stays put on the ground, to `drag` degrees toe-down at sb. Then it is dragged: held back and swung through late
    (late > 1), the toe tip scraping no more than `clear` px off the ground, the sole flattening, and it lands flat on
    the next s = 0 (no heel strike)."""
    s %= n
    if s <= ho:
        return (z0 + v * s, ankle_h, 0.0)
    if s <= sb:
        a = drag * ((s - ho) / float(sb - ho)) ** 1.3
        tip = z0 - toe + v * s
    else:
        u = (s - sb) / float(n - sb)
        tip0 = z0 - toe + v * sb
        tip = tip0 + (z0 - toe - tip0) * smooth(u ** late)
        a = drag * (1 - u) ** 1.5
        ankle_h = ankle_h + clear * math.sin(math.pi * u)
    r = math.radians(a)
    return (tip + toe * math.cos(r), ankle_h + toe * math.sin(r), -a)


def step_foot(s, n, v, z0, st, hs, flat, ho, to, lift, toe, heel, ankle_h):
    """The good leg's ankle (z, y, sole angle) through a cycle of n ticks, a heel-and-toe step: the heel strikes at
    s = 0 toe-up hs degrees with the flat-foot ankle at z0, the foot rolls flat about the heel by `flat`, stays flat,
    from ho rolls about its toe tip to `to` degrees, leaves the ground at st; the planted heel or toe moves back at v a
    tick (no sliding). Then the swing, lifted `lift` px, from toe-down to toe-up."""
    s %= n

    def contact(t):
        if t < flat:
            a = hs * (1 - t / float(flat)) ** 1.5
            hz = z0 + heel + v * t
            return (hz - heel * math.cos(math.radians(a)), ankle_h + heel * math.sin(math.radians(a)), a)
        if t <= ho:
            return (z0 + v * t, ankle_h, 0.0)
        a = to * ((t - ho) / float(st - ho)) ** 1.4
        tip = z0 - toe + v * t
        return (tip + toe * math.cos(math.radians(a)), ankle_h + toe * math.sin(math.radians(a)), -a)
    if s <= st:
        return contact(s)
    u = (s - st) / float(n - st)
    a0, a1 = contact(st), contact(0)
    e = (1 - math.cos(math.pi * u)) / 2
    sm = smooth(u)
    return (a0[0] + (a1[0] - a0[0]) * e, a0[1] + (a1[1] - a0[1]) * u + lift * math.sin(math.pi * u ** 0.8),
            a0[2] + (a1[2] - a0[2]) * sm)


def limp_motion(n, g, sc):
    """The body's curves for a limp of n ticks (g: limp_ticks), per tick: the hips' height (dropping into each plant,
    the bad one deeper, caught with a small rebound; rising over the good leg; the hitch before the drag), their roll
    (onto the bad leg 2 ticks after it lands, a small overshoot back as it is caught, the hitch the other way before
    the drag), the surge (slowed over the bad leg), the torso's roll a beat behind the hips, and a time warp that puts
    the good foot's plant at half the cycle (for swings written as the walk's)."""
    pg, sb = g['pg'], g['sb']

    def hy(t):
        return sc * (-g['crouch'] - g['dip_bad'] * bump(t, 1.5, 1.2, n) + 0.3 * g['dip_bad'] * bump(t, 3.6, 1.0, n)
                     - g['dip_good'] * bump(t, pg + 1.2, 1.1, n) + g['vault'] * bump(t, pg + g['sg'] * 0.45, 1.6, n)
                     + g['hike'] * bump(t, sb + 0.5, 1.2, n))

    def roll(t):
        # a standing list onto the bad leg, the roll onto it as it lands held a while, the catch, the hitch before the drag
        return (g.get('list', 0.0) + g['roll'] * bump(t, 2.0, g.get('roll_w', 1.5), n) - 0.12 * g['roll'] * bump(t, 4.2, 1.0, n)
                - g['hitch'] * bump(t, sb + 0.5, 1.2, n))

    def surge(t):
        return sc * g['surge'] * math.sin(2 * math.pi * (t - (0.8 * sb - n / 4.0)) / n)

    def warp(t):
        t %= n
        return t * (n / 2.0) / pg if t <= pg else n / 2.0 + (t - pg) * (n / 2.0) / (n - pg)
    return dict(hy=hy, roll=roll, surge=surge, warp=warp,
                torso_roll=lambda t: roll(t - g['lag']) - roll(t), torso_world=lambda t: roll(t - g['lag']))


def mirror_clip(clip, shift=0):
    """The other side's version of a whole-body loop: bones _l and _r swapped, rotation y and z and position x negated,
    the feet's sides swapped, step:l and step:r swapped, and everything moved on by `shift` ticks (every channel keyed
    on every tick of the cycle)."""
    n = clip['length']
    swap = lambda b: b[:-2] + ('_r' if b.endswith('_l') else '_l') if b[-2:] in ('_l', '_r') else b
    bones = {}
    for bone, chans in clip['bones'].items():
        nb = {}
        for ch, keys in chans.items():
            keys = mirror_keys(sorted(keys, key=lambda x: x[0]), ch)
            if shift:
                assert [kk[0] for kk in keys] == list(range(n + 1)), (bone, ch, 'not keyed every tick')
                keys = [(t,) + keys[(t - shift) % n][1:] for t in range(n + 1)]
            nb[ch] = keys
        bones[swap(bone)] = nb
    c = dict(clip, bones=bones)
    feet = clip['feet']
    c['feet'] = lambda s, t: feet('r' if s == 'l' else 'l', t - shift)

    def side(e):
        if e.startswith('step:') and e[5:6] in 'lr':
            return 'step:' + ('r' if e[5] == 'l' else 'l') + e[6:]
        return e
    c['effects'] = [((t + shift) % n, kind, side(eff) if kind == 'timeline' else eff, loc)
                    for (t, kind, eff, loc) in clip['effects']]
    return c


def check_reach(name, clip, geo=None):
    """A limp's legs stay within reach on every tick (the solver straightens a leg that cannot reach, and the foot
    would float or slide). geo: the frame's rig module, this script's by default."""
    G = geo or globals()['G']
    hips = clip['bones'].get('hips', {})
    hpos = sorted(hips.get('position', [k(0, P())]), key=lambda x: x[0])
    hrot = sorted(hips.get('rotation', [k(0, R())]), key=lambda x: x[0])
    worst = 0.0
    for t in range(clip['length'] + 1):
        hp = value_at(hpos, t)
        for s in 'lr':
            z, y, _ = clip['feet'](s, t)
            ank, hip_s, _ = level_hip((hp[2], G.HIP_Y + hp[1]), (z, y), s, value_at(hrot, t)[2], False, G)
            worst = max(worst, math.hypot(ank[0] - hip_s[0], ank[1] - hip_s[1]) / (G.THIGH + G.SHIN))
    if worst > 0.995:
        sys.exit('%s: a leg is asked to reach %.3f of its length' % (name, worst))
    return worst


def limp_l():
    """The damage limp, left leg dragging (limp_r is its mirror). Lamps and sensor as in the walk (powered)."""
    n = WALK_N
    g = limp_ticks(n, LIMP_GAIT)
    v, sc = V_WALK, SC
    rz = REST_FOOT_T[0]
    z0b = rz - v * g['sb'] / 2.0 - g['fwd_bad'] * sc
    z0g = rz - v * g['sg'] / 2.0 - g['fwd_good'] * sc
    drag = math.degrees(math.asin(min(0.9, g['drag_lift'] * sc / TOE)))
    gf = dict(st=g['sg'], hs=g['hs'], flat=2, ho=g['sg'] - 2, to=g['to'], lift=g['lift'] * sc, toe=TOE,
              heel=g['heel'] * sc, ankle_h=G.ANKLE_H)

    def feet(side, t):
        if side == 'l':
            return drag_foot(t, n, v, z0b, g['sb'], g['ho'], drag, g['clear'] * sc, g['late'], TOE, G.ANKLE_H)
        return step_foot(t - g['pg'], n, v, z0g, **gf)
    m = limp_motion(n, g, sc)
    w = m['warp']
    c = lambda t, ph: math.cos(2 * math.pi * (w(t) - ph) / n)
    ticks = range(0, n + 1)
    r3 = lambda x: round(x, 3)
    pg = g['pg']
    return {
        'length': n, 'loop': 'loop', 'feet': feet, 'native': True, 'level_legs': True,
        'bones': {
            'hips': {'position': [k(t, P(y=r3(m['hy'](t)), z=r3(m['surge'](t)))) for t in ticks],
                     'rotation': [k(t, R(z=r3(m['roll'](t)))) for t in ticks]},
            'torso': {'rotation': [k(t, R(x=r3(g['lean'] + 0.5 * (m['hy'](t - g['lag']) / sc + g['crouch'])),
                                          z=r3(m['torso_roll'](t)))) for t in ticks]},
            'restraint': {'rotation': [k(t, R(x={1: 0.8, 2: 0.3, pg + 1: 0.4}.get(t, 0))) for t in ticks]},
            'arm_l': {'rotation': [k(t, R(x=r3(g['arm_hang'] - g['arm_bad'] * c(t, 2)),
                                          z=r3(g['arm_in'] - 0.6 * m['torso_world'](t - 1)))) for t in ticks]},
            'forearm_l': {'rotation': [k(t, R(x=r3(-3 - 2 * c(t, 4)))) for t in ticks]},
            'arm_r': {'rotation': [k(t, R(x=r3(g['arm_good'] * c(t, 1)))) for t in ticks]},
            'forearm_r': {'rotation': [k(t, R(x=r3(6 * c(t, 3)))) for t in ticks]},
        },
        'effects': [(0, 'timeline', 'step:l_drag', None), (2, 'sound', snd('servo_catch', 0.4, 0.8), None),
                    (pg, 'timeline', 'step:r', None)],
    }


def turn(direction):
    """Turning on the spot: marching in place, the torso leading the turn by 6 degrees."""
    a = REST_FOOT_T
    up = (a[0] - 1, a[1] + 3, 4)
    feet = {'l': [k(0, a), k(2, up, 'easeOutCubic'), k(4, a, 'easeInQuad'), k(12, a)],
            'r': [k(0, a), k(6, a), k(8, up, 'easeOutCubic'), k(10, a, 'easeInQuad'), k(12, a)]}
    yaw = 6 if direction == 'l' else -6
    return {
        'length': 12, 'loop': 'loop', 'feet': feet,
        'bones': {
            'hips': {'position': [k(0, P(y=-0.4)), k(2, P(y=0.2), 'easeInOutSine'), k(5, P(y=-0.6), 'easeInOutSine'),
                                  k(8, P(y=0.2), 'easeInOutSine'), k(11, P(y=-0.6), 'easeInOutSine'),
                                  k(12, P(y=-0.4), 'easeInOutSine')],
                     'rotation': const(R(y=yaw * 0.4))},
            'torso': {'rotation': [k(0, R(y=yaw * 0.6)), k(6, R(y=yaw * 0.6 + 1), 'easeInOutSine'),
                                   k(12, R(y=yaw * 0.6), 'easeInOutSine')]},
        },
        'effects': [(4, 'timeline', 'step:l_light', None), (10, 'timeline', 'step:r_light', None)],
    }


# ---- jumping and landing --------------------------------------------------------------------------------------------
def jump():
    """Ground jump: 2-tick crouch, the push (server impulse on tick 2), legs snap straight down as it leaves, then
    tuck. Holds the tuck; fall takes over."""
    a = REST_FOOT_T
    feet = {s: [k(0, a), k(2, a), k(3, (0, -3, -25), 'easeInExpo'), k(7, (2, 10, 10), 'easeOutCubic'),
                k(10, (2, 10, 10))] for s in 'lr'}
    return {
        'length': 10, 'loop': 'hold', 'feet': feet,
        'bones': {
            'hips': {'position': [k(0, P()), k(2, P(y=-3.5), 'easeOutQuad'), k(3, P(y=1.5), 'easeInExpo'),
                                  k(10, P(y=1))]},
            'torso': {'rotation': [k(0, R()), k(2, R(x=-6), 'easeOutQuad'), k(4, R(x=4), 'easeOutCubic'),
                                   k(10, R(), 'easeInOutSine')]},
            'arm_l': {'rotation': [k(0, R()), k(2, R(x=-25), 'easeOutQuad'), k(5, R(x=35), 'easeOutCubic'),
                                   k(10, R(x=25), 'easeInOutSine')]},
            'arm_r': {'rotation': [k(0, R()), k(2, R(x=-25), 'easeOutQuad'), k(5, R(x=35), 'easeOutCubic'),
                                   k(10, R(x=25), 'easeInOutSine')]},
            'forearm_l': {'rotation': [k(0, R()), k(3, R(x=12), 'easeOutQuad'), k(6, R(x=-15), 'easeOutCubic'),
                                       k(10, R(x=-10))]},
            'forearm_r': {'rotation': [k(0, R()), k(3, R(x=12), 'easeOutQuad'), k(6, R(x=-15), 'easeOutCubic'),
                                       k(10, R(x=-10))]},
        },
        'effects': [(2, 'timeline', 'jump:push', None)],
    }


def fall():
    """In the air, falling: legs reach down and forward to meet the ground, arms up and fluttering."""
    feet = {'l': [k(0, (-3, -17, 8)), k(8, (-3, -18, 4), 'easeInOutSine'), k(16, (-3, -17, 8), 'easeInOutSine')],
            'r': [k(0, (-2, -18, 5)), k(8, (-2, -17, 8), 'easeInOutSine'), k(16, (-2, -18, 5), 'easeInOutSine')]}
    return {
        'length': 16, 'loop': 'loop', 'feet': feet, 'feet_rel': True,
        'bones': {
            'torso': {'rotation': const(R(x=3))},
            'arm_l': {'rotation': [k(0, R(x=30)), k(4, R(x=34), 'easeInOutSine'), k(8, R(x=28), 'easeInOutSine'),
                                   k(12, R(x=33), 'easeInOutSine'), k(16, R(x=30), 'easeInOutSine')]},
            'arm_r': {'rotation': [k(0, R(x=32)), k(4, R(x=28), 'easeInOutSine'), k(8, R(x=34), 'easeInOutSine'),
                                   k(12, R(x=29), 'easeInOutSine'), k(16, R(x=32), 'easeInOutSine')]},
            'forearm_l': {'rotation': const(R(x=-10))}, 'forearm_r': {'rotation': const(R(x=-10))},
            'restraint': {'rotation': [k(0, R()), k(2, R(x=0.6), 'easeInOutSine'), k(4, R(), 'easeInOutSine'),
                                    k(6, R(x=0.4), 'easeInOutSine'), k(8, R(), 'easeInOutSine'), k(16, R())]},
        },
        'effects': [],
    }


def land(heavy=False):
    """Landing: the feet take it on tick 0 (contact), the hips absorb and recover. Light under 4 blocks of fall
    (3.5 px), heavy above (8 px, vents puff). The game picks by fall distance."""
    d = 10.0 if heavy else 4.0
    L = 20 if heavy else 12
    if heavy:
        hips = [k(0, P()), k(4, P(y=-d), 'easeOutCubic'), k(7, P(y=-d + 0.5), 'easeInOutSine'),
                k(15, P(y=1), 'easeInOutCubic'), k(20, P(), 'easeInOutSine')]
        torso = [k(0, R(x=3)), k(1, R(x=4), 'easeOutQuad'), k(5, R(x=-11), 'easeOutCubic'), k(9, R(x=-10), 'easeInOutSine'),
                 k(16, R(x=0.5), 'easeInOutCubic'), k(20, R(), 'easeInOutSine')]
        arms = [k(0, R(x=30)), k(3, R(x=30)), k(6, R(x=-20), 'easeInQuad'), k(10, R(x=-15), 'easeInOutSine'),
                k(18, R(), 'easeInOutSine')]
    else:
        hips = [k(0, P()), k(3, P(y=-d), 'easeOutCubic'), k(5, P(y=-d + 0.3), 'easeInOutSine'),
                k(9, P(y=0.6), 'easeInOutSine'), k(12, P(), 'easeInOutSine')]
        torso = [k(0, R(x=3)), k(4, R(x=-5), 'easeOutCubic'), k(10, R(), 'easeInOutSine')]
        arms = [k(0, R(x=30)), k(2, R(x=30)), k(5, R(x=-8), 'easeInQuad'), k(9, R(x=3), 'easeInOutSine'),
                k(12, R(), 'easeInOutSine')]
    bones = {'hips': {'position': hips}, 'torso': {'rotation': torso},
             'arm_l': {'rotation': arms}, 'arm_r': {'rotation': [(kk[0] + (1 if 0 < kk[0] < L else 0),) + kk[1:] for kk in arms]},
             'forearm_l': {'rotation': [k(0, R(x=-10)), k(4, R(x=-10)), k(8, R(x=10), 'easeInOutSine'),
                                        k(L, R(), 'easeInOutSine')]},
             'forearm_r': {'rotation': [k(0, R(x=-10)), k(5, R(x=-10)), k(9, R(x=10), 'easeInOutSine'),
                                        k(L, R(), 'easeInOutSine')]}}
    effects = [(0, 'timeline', 'land:heavy' if heavy else 'land:light', None)]
    if heavy:
        bones['vent_l'] = {'rotation': [k(0, R()), k(2, R(x=-35), 'easeOutExpo'), k(12, R(), 'easeInElastic', [4])]}
        bones['vent_r'] = {'rotation': [k(0, R()), k(3, R(x=-35), 'easeOutExpo'), k(13, R(), 'easeInElastic', [4])]}
        bones['sensor'] = {'rotation': [k(0, R()), k(3, R(x=22), 'easeOutCubic'), k(10, R(x=18), 'easeInOutSine'),
                                        k(18, R(), 'easeInOutSine')]}
        bones['restraint'] = {'rotation': [k(0, R()), k(1, R(x=3), 'easeOutQuad'), k(3, R(), 'easeInQuad'),
                                        k(5, R(x=1.2), 'easeOutQuad'), k(7, R(), 'easeInQuad')]}
        effects += [(2, 'particle', 'cloud', 'fx_vent_l'), (3, 'particle', 'cloud', 'fx_vent_r'),
                    (12, 'sound', snd('vent_clank', 0.4, 1.1), None)]
    return {'length': L, 'loop': 'once', 'feet': planted(), 'bones': bones, 'effects': effects}


# ---- the Pogo Protocol ----------------------------------------------------------------------------------------------
POGO_DEEP = -12.5


def pogo_crouch():
    """Drive held: the hull sinks in four ratchet stages over 1.5 s (12.5 px in all), the vent flaps rise one notch a
    stage, the arms swing back, the torso leans in and then pitches back to aim the jump, and for the last 8 ticks
    the whole machine trembles. Holds its last frame; pogo_hold loops on from there."""
    trem = [k(0, P()), k(22, P())] + [k(t, P(x=(0.5 if t % 2 else -0.5))) for t in range(23, 30)] + [k(30, P())]
    return {
        'length': 30, 'loop': 'hold', 'feet': planted(),
        'bones': {
            'hips': {'position': [k(0, P()), k(2, P(y=0.8), 'easeOutQuad'), k(7, P(y=-5.4), 'easeInOutCubic'),
                                  k(9, P(y=-5), 'easeInOutSine'), k(14, P(y=-8.9), 'easeInOutCubic'),
                                  k(16, P(y=-8.5), 'easeInOutSine'), k(21, P(y=-11.4), 'easeInOutCubic'),
                                  k(23, P(y=-11), 'easeInOutSine'), k(28, P(y=-12.9), 'easeInOutCubic'),
                                  k(30, P(y=POGO_DEEP), 'easeInOutSine')]},
            'torso': {'rotation': [k(0, R()), k(3, R(x=1), 'easeOutQuad'), k(10, R(x=-4), 'easeInOutSine'),
                                   k(17, R(x=-6), 'easeInOutSine'), k(26, R(x=5), 'easeInOutCubic'),
                                   k(30, R(x=4), 'easeInOutSine')],
                      'position': trem},
            'arm_l': {'rotation': [k(0, R()), k(4, R(x=-8), 'easeInOutSine'), k(11, R(x=-15), 'easeInOutSine'),
                                   k(18, R(x=-25), 'easeInOutSine'), k(25, R(x=-35), 'easeInOutSine'), k(30, R(x=-35))]},
            'arm_r': {'rotation': [k(0, R()), k(5, R(x=-8), 'easeInOutSine'), k(12, R(x=-15), 'easeInOutSine'),
                                   k(19, R(x=-25), 'easeInOutSine'), k(26, R(x=-35), 'easeInOutSine'), k(30, R(x=-35))]},
            'forearm_l': {'rotation': [k(0, R()), k(12, R(x=12), 'easeInOutSine'), k(27, R(x=25), 'easeInOutSine'),
                                       k(30, R(x=25))]},
            'forearm_r': {'rotation': [k(0, R()), k(13, R(x=12), 'easeInOutSine'), k(28, R(x=25), 'easeInOutSine'),
                                       k(30, R(x=25))]},
            'vent_l': {'rotation': [k(0, R()), k(7, R()), k(9, R(x=-10), 'easeOutQuad'), k(14, R(x=-10)),
                                    k(16, R(x=-20), 'easeOutQuad'), k(21, R(x=-20)), k(23, R(x=-30), 'easeOutQuad'),
                                    k(28, R(x=-30)), k(30, R(x=-40), 'easeOutQuad')]},
            'vent_r': {'rotation': [k(0, R()), k(8, R()), k(10, R(x=-10), 'easeOutQuad'), k(15, R(x=-10)),
                                    k(17, R(x=-20), 'easeOutQuad'), k(22, R(x=-20)), k(24, R(x=-30), 'easeOutQuad'),
                                    k(29, R(x=-30)), k(30, R(x=-40), 'easeOutQuad')]},
            'sensor': {'rotation': [k(0, R()), k(18, R()), k(26, R(x=12), 'easeInOutSine'), k(30, R(x=10), 'easeInOutSine')]},
        },
        'effects': [(1, 'sound', snd('pogo_charge', 0.6, 1.0), None),
                    (7, 'sound', snd('pogo_ratchet', 0.5, 1.0), None),
                    (14, 'sound', snd('pogo_ratchet', 0.5, 1.1), None),
                    (21, 'sound', snd('pogo_ratchet', 0.5, 1.2), None),
                    (24, 'particle', 'cloud', 'fx_vent_l'),
                    (25, 'particle', 'cloud', 'fx_vent_r'),
                    (28, 'sound', snd('pogo_ratchet', 0.5, 1.3), None)],
    }


def pogo_hold():
    """Fully charged and held: the deep crouch shudders, the vents flutter and puff."""
    return {
        'length': 10, 'loop': 'loop', 'feet': planted(),
        'bones': {
            'hips': {'position': [k(0, P(y=POGO_DEEP)), k(2, P(y=POGO_DEEP - 0.4), 'easeInOutSine'),
                                  k(5, P(y=POGO_DEEP + 0.2), 'easeInOutSine'), k(7, P(y=POGO_DEEP - 0.3), 'easeInOutSine'),
                                  k(10, P(y=POGO_DEEP), 'easeInOutSine')]},
            'torso': {'rotation': [k(0, R(x=4)), k(3, R(x=4, z=0.6), 'easeInOutSine'), k(8, R(x=4, z=-0.6), 'easeInOutSine'),
                                   k(10, R(x=4), 'easeInOutSine')]},
            'arm_l': {'rotation': const(R(x=-35))}, 'arm_r': {'rotation': const(R(x=-35))},
            'forearm_l': {'rotation': const(R(x=25))}, 'forearm_r': {'rotation': const(R(x=25))},
            'vent_l': {'rotation': [k(0, R(x=-40)), k(4, R(x=-46), 'easeInOutSine'), k(10, R(x=-40), 'easeInOutSine')]},
            'vent_r': {'rotation': [k(0, R(x=-40)), k(6, R(x=-35), 'easeInOutSine'), k(10, R(x=-40), 'easeInOutSine')]},
            'sensor': {'rotation': const(R(x=10))},
        },
        'effects': [(3, 'particle', 'cloud', 'fx_vent_l'), (8, 'particle', 'cloud', 'fx_vent_r')],
    }


def pogo_leap():
    """Drive released: one more tick of load, then everything fires at once (server impulse on tick 1): legs snap
    straight down as it leaves, arms throw forward, vents slam shut, then the legs tuck."""
    a = REST_FOOT_T
    feet = {s: [k(0, a), k(1, a), k(3, (1, -5, -30), 'easeInExpo'), k(8, (3, 11, 12), 'easeOutCubic'),
                k(12, (3, 11, 12))] for s in 'lr'}
    return {
        'length': 12, 'loop': 'hold', 'feet': feet,
        'bones': {
            'hips': {'position': [k(0, P(y=POGO_DEEP)), k(1, P(y=POGO_DEEP - 0.6), 'easeOutQuad'),
                                  k(3, P(y=2), 'easeInExpo'), k(12, P(y=1.5), 'easeInOutSine')]},
            'torso': {'rotation': [k(0, R(x=4)), k(1, R(x=5), 'easeOutQuad'), k(4, R(x=-3), 'easeInExpo'),
                                   k(9, R(x=-2), 'easeInOutSine'), k(12, R(), 'easeInOutSine')]},
            'arm_l': {'rotation': [k(0, R(x=-35)), k(1, R(x=-38), 'easeOutQuad'), k(3, R(x=20), 'easeInExpo'),
                                   k(5, R(x=45), 'easeOutCubic'), k(12, R(x=30), 'easeInOutSine')]},
            'arm_r': {'rotation': [k(0, R(x=-35)), k(1, R(x=-38), 'easeOutQuad'), k(3, R(x=20), 'easeInExpo'),
                                   k(5, R(x=45), 'easeOutCubic'), k(12, R(x=30), 'easeInOutSine')]},
            'forearm_l': {'rotation': [k(0, R(x=25)), k(4, R(), 'easeInOutSine'), k(7, R(x=-20), 'easeOutCubic'),
                                       k(12, R(x=-10), 'easeInOutSine')]},
            'forearm_r': {'rotation': [k(0, R(x=25)), k(4, R(), 'easeInOutSine'), k(7, R(x=-20), 'easeOutCubic'),
                                       k(12, R(x=-10), 'easeInOutSine')]},
            'vent_l': {'rotation': [k(0, R(x=-40)), k(1, R(), 'easeInQuad')]},
            'vent_r': {'rotation': [k(0, R(x=-40)), k(2, R(), 'easeInQuad')]},
            'sensor': {'rotation': [k(0, R(x=10)), k(6, R(), 'easeInOutSine')]},
        },
        'effects': [(1, 'timeline', 'pogo:launch', None),
                    (2, 'sound', snd('vent_clank', 0.5, 1.2), None),
                    (5, 'sound', snd('whoosh', 0.5, 1.0), None)],
    }


def pogo_land():
    """The pogo landing (server: dust ring, knockback ring, no damage, on tick 0): the hull bottoms out 9 px, holds,
    springs back past standing and settles on an elastic; the vents blow the charge off."""
    return {
        'length': 24, 'loop': 'once', 'feet': planted(),
        'bones': {
            'hips': {'position': [k(0, P()), k(3, P(y=-12), 'easeOutCubic'), k(5, P(y=-11.5), 'easeInOutSine'),
                                  k(9, P(y=2.5), 'easeInOutCubic'), k(12, P(y=-1), 'easeInOutSine'),
                                  k(15, P(y=0.4), 'easeInOutSine'), k(18, P(), 'easeInOutSine')]},
            'torso': {'rotation': [k(0, R(x=2)), k(1, R(x=2)), k(4, R(x=-7), 'easeOutCubic'),
                                   k(6, R(x=-6), 'easeInOutSine'), k(13, R(x=2), 'easeInOutCubic'),
                                   k(21, R(), 'easeInOutSine')]},
            'arm_l': {'rotation': [k(0, R(x=30)), k(2, R(x=30)), k(5, R(x=-25), 'easeInQuad'),
                                   k(8, R(x=-20), 'easeInOutSine'), k(16, R(x=3), 'easeInOutSine'),
                                   k(22, R(), 'easeInOutSine')]},
            'arm_r': {'rotation': [k(0, R(x=30)), k(3, R(x=30)), k(6, R(x=-25), 'easeInQuad'),
                                   k(9, R(x=-20), 'easeInOutSine'), k(17, R(x=3), 'easeInOutSine'),
                                   k(23, R(), 'easeInOutSine')]},
            'forearm_l': {'rotation': [k(0, R(x=-10)), k(3, R(x=-10)), k(6, R(x=20), 'easeInQuad'),
                                       k(16, R(), 'easeInOutSine')]},
            'forearm_r': {'rotation': [k(0, R(x=-10)), k(4, R(x=-10)), k(7, R(x=20), 'easeInQuad'),
                                       k(17, R(), 'easeInOutSine')]},
            'vent_l': {'rotation': [k(0, R()), k(1, R(x=-40), 'easeInExpo'), k(3, R(x=-35), 'easeInOutSine'),
                                    k(11, R(), 'easeInQuad')]},
            'vent_r': {'rotation': [k(0, R()), k(2, R(x=-40), 'easeInExpo'), k(4, R(x=-35), 'easeInOutSine'),
                                    k(12, R(), 'easeInQuad')]},
            'sensor': {'rotation': [k(0, R()), k(2, R(x=45), 'easeOutCubic'), k(8, R(x=35), 'easeInOutSine'),
                                    k(16, R(), 'easeInOutSine')]},
            'restraint': {'rotation': [k(0, R()), k(2, R(x=2), 'easeOutQuad'), k(4, R(), 'easeInQuad'),
                                    k(6, R(x=0.8), 'easeOutQuad'), k(8, R(), 'easeInQuad')]},
        },
        'effects': [(0, 'timeline', 'pogo:land', None),
                    (1, 'particle', 'cloud', 'fx_vent_l'),
                    (2, 'particle', 'cloud', 'fx_vent_r'),
                    (11, 'sound', snd('vent_clank', 0.4, 1.0), None),
                    (12, 'sound', snd('servo_settle', 0.4, 1.0), None)],
    }


# ---- arm verbs (controller "arms"); left versions, the right ones are mirrored ---------------------------------------
AIM_ARM, AIM_FORE = 70.0, -60.0


def arm(ar, fa=None, pos=None):
    d = {'arm_l': {'rotation': ar}}
    if fa is not None:
        d['forearm_l'] = {'rotation': fa}
    if pos is not None:
        d['arm_l']['position'] = pos
    return d


def verbs_left():
    v = {}
    v['spin'] = {'length': 8, 'loop': 'hold', 'bones': arm(
        [k(0, R()), k(1, R(x=-6), 'easeOutQuad'), k(6, R(x=AIM_ARM + 3), 'easeOutCubic'), k(8, R(x=AIM_ARM), 'easeInOutSine')],
        [k(0, R()), k(2, R(x=4), 'easeOutQuad'), k(7, R(x=AIM_FORE - 3), 'easeOutCubic'), k(8, R(x=AIM_FORE), 'easeInOutSine')]),
        'effects': [(1, 'sound', snd('servo', 0.4, 1.2), None)]}
    jit = [0, 0.6, -0.4, 0.5, -0.5, 0.6, -0.4, 0.4, -0.3, 0.5, 0]
    v['spin_loop'] = {'length': 10, 'loop': 'loop', 'bones': arm(
        [k(t, R(x=AIM_ARM + j)) for t, j in enumerate(jit)],
        [k(t, R(x=AIM_FORE - jit[(t + 1) % 10])) for t in range(11)],
        const(P(z=-1))), 'effects': []}
    v['spin_end'] = {'length': 14, 'loop': 'once', 'bones': arm(
        [k(0, R(x=AIM_ARM)), k(2, R(x=AIM_ARM + 4), 'easeOutQuad'), k(11, R(x=-3), 'easeInOutCubic'), k(14, R(), 'easeInOutSine')],
        [k(0, R(x=AIM_FORE)), k(3, R(x=AIM_FORE)), k(12, R(x=2), 'easeInOutCubic'), k(14, R(), 'easeInOutSine')],
        [k(0, P(z=-1)), k(4, P(), 'easeInOutSine')]),
        'effects': [(2, 'sound', snd('servo', 0.4, 0.9), None)]}
    v['recoil'] = {'length': 20, 'loop': 'once', 'bones': arm(
        [k(0, R()), k(3, R(x=AIM_ARM + 3), 'easeOutCubic'), k(4, R(x=AIM_ARM), 'easeInOutSine'), k(7, R(x=AIM_ARM)),
         k(8, R(x=AIM_ARM + 6), 'easeOutQuad'), k(12, R(x=AIM_ARM), 'easeOutCubic'), k(14, R(x=AIM_ARM)),
         k(18, R(x=-2), 'easeInOutSine'), k(20, R(), 'easeInOutSine')],
        [k(0, R()), k(1, R()), k(4, R(x=AIM_FORE), 'easeOutCubic'), k(6, R(x=AIM_FORE)),
         k(7, R(x=AIM_FORE + 8), 'easeInExpo'), k(11, R(x=AIM_FORE), 'easeOutCubic'), k(14, R(x=AIM_FORE)),
         k(20, R(), 'easeInOutSine')],
        [k(0, P()), k(6, P()), k(7, P(z=2), 'easeInExpo'), k(11, P(), 'easeOutCubic')]),
        'effects': [(1, 'sound', snd('servo', 0.4, 1.3), None), (6, 'timeline', 'arm:fire:l', None)]}
    v['sustain'] = {'length': 8, 'loop': 'hold', 'bones': arm(
        [k(0, R()), k(1, R(x=-4), 'easeOutQuad'), k(6, R(x=68), 'easeOutCubic'), k(8, R(x=66), 'easeInOutSine')],
        [k(0, R()), k(2, R(x=3), 'easeOutQuad'), k(7, R(x=-58), 'easeOutCubic'), k(8, R(x=-56), 'easeInOutSine')],
        [k(0, P()), k(5, P()), k(8, P(y=-1), 'easeInQuad')]),
        'effects': [(1, 'sound', snd('servo', 0.4, 1.1), None)]}
    v['sustain_loop'] = {'length': 40, 'loop': 'loop', 'bones': arm(
        [k(0, R(x=66)), k(10, R(x=67, y=1), 'easeInOutSine'), k(20, R(x=66), 'easeInOutSine'),
         k(30, R(x=65, y=-1), 'easeInOutSine'), k(40, R(x=66), 'easeInOutSine')],
        const(R(x=-56)), const(P(y=-1))), 'effects': []}
    v['sustain_end'] = {'length': 14, 'loop': 'once', 'bones': arm(
        [k(0, R(x=66)), k(2, R(x=70), 'easeOutQuad'), k(11, R(x=-3), 'easeInOutCubic'), k(14, R(), 'easeInOutSine')],
        [k(0, R(x=-56)), k(3, R(x=-56)), k(12, R(x=2), 'easeInOutCubic'), k(14, R(), 'easeInOutSine')],
        [k(0, P(y=-1)), k(2, P(), 'easeOutQuad')]),
        'effects': [(1, 'particle', 'smoke', 'fx_wrist_l')]}
    v['strike'] = {'length': 18, 'loop': 'once', 'bones': arm(
        [k(0, R()), k(4, R(x=-25), 'easeOutCubic'), k(6, R(x=-26)), k(8, R(x=80), 'easeInExpo'),
         k(10, R(x=86), 'easeOutQuad'), k(17, R(), 'easeOutBack', [0.5])],
        [k(0, R()), k(4, R(x=25), 'easeOutCubic'), k(7, R(x=26)), k(8, R(x=-80), 'easeInExpo'),
         k(10, R(x=-84), 'easeOutQuad'), k(18, R(), 'easeOutBack', [0.5])],
        [k(0, P()), k(7, P()), k(8, P(z=-2), 'easeInExpo'), k(12, P(), 'easeInOutSine')]),
        'effects': [(1, 'sound', snd('servo', 0.4, 1.2), None), (8, 'timeline', 'arm:hit:l', None)]}
    guard = R(x=45, z=30)
    v['guard'] = {'length': 10, 'loop': 'hold', 'bones': arm(
        [k(0, R()), k(1, R(x=-4, z=-3), 'easeOutQuad'), k(8, R(x=48, z=32), 'easeInOutCubic'), k(10, guard, 'easeInOutSine')],
        [k(0, R()), k(2, R()), k(9, R(x=48), 'easeInOutCubic'), k(10, R(x=45), 'easeInOutSine')]),
        'effects': [(1, 'sound', snd('servo', 0.4, 1.0), None)]}
    v['guard_loop'] = {'length': 20, 'loop': 'loop', 'bones': arm(
        [k(0, guard), k(10, R(x=46, z=31), 'easeInOutSine'), k(20, guard, 'easeInOutSine')], const(R(x=45))),
        'effects': []}
    v['guard_hit'] = {'length': 8, 'loop': 'once', 'bones': arm(
        [k(0, guard), k(1, R(x=41, z=30), 'easeOutExpo'), k(2, R(x=41, z=30)), k(6, guard, 'easeOutCubic')],
        const(R(x=45)),
        [k(0, P()), k(1, P(z=2), 'easeOutExpo'), k(2, P(z=2)), k(6, P(), 'easeOutCubic')]),
        'effects': [(0, 'timeline', 'arm:block:l', None)]}
    v['guard_end'] = {'length': 12, 'loop': 'once', 'bones': arm(
        [k(0, guard), k(2, R(x=48, z=32), 'easeOutQuad'), k(10, R(x=-2), 'easeInOutCubic'), k(12, R(), 'easeInOutSine')],
        [k(0, R(x=45)), k(3, R(x=45)), k(11, R(x=-2), 'easeInOutCubic'), k(12, R(), 'easeInOutSine')]),
        'effects': [(1, 'sound', snd('servo', 0.4, 0.9), None)]}
    v['dry_fire'] = {'length': 18, 'loop': 'once', 'bones': arm(
        [k(0, R()), k(1, R(x=-4), 'easeOutQuad'), k(5, R(x=50), 'easeOutCubic'), k(7, R(x=50)),
         k(9, R(x=38), 'easeInQuad'), k(12, R(x=38)), k(16, R(x=-2), 'easeInOutSine'), k(18, R(), 'easeInOutSine')],
        [k(0, R()), k(2, R()), k(6, R(x=-42), 'easeOutCubic'), k(7, R(x=-42)), k(10, R(x=-58), 'easeInQuad'),
         k(13, R(x=-58)), k(18, R(), 'easeInOutSine')]),
        'effects': [(1, 'sound', snd('servo', 0.4, 1.2), None), (7, 'timeline', 'arm:dry:l', None),
                    (10, 'sound', snd('servo_sag', 0.3, 0.8), None)]}
    return v


# ---- states -------------------------------------------------------------------------------------------------------
def vent():
    """Overheat (100 ticks, MechaLogic.VENT_TICKS): the striped flaps blow open, the machine jolts, slumps and heaves
    for breath with steam curling off, whistles like a kettle, shuts the flaps one then the other, straightens and
    shakes its arms out."""
    heave = [k(0, P()), k(6, P()), k(18, P(y=-3), 'easeInOutSine'), k(33, P(y=-4.2), 'easeInOutSine'),
             k(48, P(y=-3), 'easeInOutSine'), k(63, P(y=-4.2), 'easeInOutSine'), k(78, P(y=-3.2), 'easeInOutSine'),
             k(84, P(y=-3.2)), k(96, P(y=0.4), 'easeInOutCubic'), k(100, P(), 'easeInOutSine')]
    shake_l = [k(0, R()), k(6, R()), k(16, R(x=-8), 'easeInQuad'), k(84, R(x=-8)), k(94, R(), 'easeInOutCubic'),
               k(96, R(z=6), 'easeOutQuad'), k(98, R(z=-4), 'easeInOutSine'), k(100, R(), 'easeInOutSine')]
    shake_r = mirror_keys([(kk[0] + (1 if 90 < kk[0] < 100 else 0),) + kk[1:] for kk in shake_l], 'rotation')
    return {
        'length': 100, 'loop': 'once', 'feet': planted(),
        'bones': {
            'vent_l': {'rotation': [k(0, R()), k(1, R(x=4), 'easeOutQuad'), k(3, R(x=-80), 'easeInExpo'),
                                    k(5, R(x=-74), 'easeInOutSine'), k(78, R(x=-74)), k(82, R(), 'easeInQuad')]},
            'vent_r': {'rotation': [k(0, R()), k(1, R(x=4), 'easeOutQuad'), k(3, R(x=-80), 'easeInExpo'),
                                    k(6, R(x=-74), 'easeInOutSine'), k(82, R(x=-74)), k(86, R(), 'easeInQuad')]},
            'torso': {'rotation': [k(0, R()), k(3, R(x=3), 'easeOutQuad'), k(7, R(), 'easeInOutSine'),
                                   k(8, R()), k(18, R(x=-6), 'easeInOutSine'), k(84, R(x=-6)),
                                   k(96, R(x=0.8), 'easeInOutCubic'), k(100, R(), 'easeInOutSine')]},
            'hips': {'position': heave},
            'arm_l': {'rotation': shake_l}, 'arm_r': {'rotation': shake_r},
            'forearm_l': {'rotation': [k(0, R()), k(8, R()), k(18, R(x=-50), 'easeInQuad'), k(84, R(x=-50)),
                                       k(94, R(), 'easeInOutCubic')]},
            'forearm_r': {'rotation': [k(0, R()), k(9, R()), k(19, R(x=-50), 'easeInQuad'), k(85, R(x=-50)),
                                       k(95, R(), 'easeInOutCubic')]},
            'sensor': {'rotation': [k(0, R()), k(6, R()), k(18, R(x=-18), 'easeInOutSine'), k(84, R(x=-18)),
                                    k(94, R(), 'easeInOutSine')]},
        },
        'effects': [(3, 'timeline', 'vent:blast', None),
                    (20, 'particle', 'cloud', 'fx_vent_l'), (26, 'particle', 'cloud', 'fx_vent_r'),
                    (36, 'particle', 'cloud', 'fx_vent_l'), (42, 'particle', 'cloud', 'fx_vent_r'),
                    (52, 'particle', 'cloud', 'fx_vent_l'), (58, 'particle', 'cloud', 'fx_vent_r'),
                    (68, 'particle', 'cloud', 'fx_vent_l'),
                    (76, 'sound', snd('vent_whistle', 0.6, 1.0), None),
                    (82, 'sound', snd('vent_clank', 0.5, 1.0), None),
                    (86, 'sound', snd('vent_clank', 0.5, 0.9), None),
                    (95, 'sound', snd('rattle', 0.4, 1.0), None)],
    }


def hurt():
    """Controller "hit" (short, over anything but destroyed): the whole machine rocks back from its feet, the restraint
    rattles on its hinge, a spark at the collar."""
    return {
        'length': 8, 'loop': 'once',
        'bones': {
            'root': {'rotation': [k(0, R()), k(1, R(x=2, z=-4), 'easeOutExpo'), k(2, R(x=2, z=-4)),
                                  k(5, R(x=-0.6, z=1.2), 'easeInOutSine'), k(8, R(), 'easeInOutSine')],
                     'position': [k(0, P()), k(1, P(y=0.6), 'easeOutExpo'), k(2, P(y=0.6)), k(7, P(), 'easeInOutSine')]},
            'restraint': {'rotation': [k(0, R()), k(2, R(x=2.5), 'easeOutQuad'), k(4, R(), 'easeInQuad'),
                                    k(6, R(x=0.8), 'easeOutQuad'), k(8, R(), 'easeInQuad')]},
        },
        'effects': [(1, 'particle', 'electric_spark', 'fx_hull')],
    }


def eject():
    """The Golden Parachute (health under 10 %): klaxon, the clamps snap open, the restraint is blown open and back on
    its hinge (it hits its stop at 8 and stays open), the jar is fired up out of the collar (the server dismounts the pilot at tick 4
    and the jar flies as the player; its parachute is the jar renderer's), the mecha jolts with the recoil, its lamps
    go out in steps and it sags into the parked pose, smoking."""
    return {
        'length': 30, 'loop': 'hold', 'feet': planted(),
        'bones': dict({
            'restraint': {'rotation': [k(0, R()), k(1, R(x=-2), 'easeOutQuad'), k(2, R(x=-2)),
                                       k(5, R(x=150), 'easeOutExpo'), k(8, R(x=132), 'easeInOutSine'),
                                       k(11, R(x=140), 'easeInOutSine'), k(14, R(x=136), 'easeInOutSine')]},
            'clamp_l': {'rotation': [k(0, R()), k(1, R(z=-30), 'easeOutExpo')]},
            'clamp_r': {'rotation': [k(0, R()), k(1, R(z=30), 'easeOutExpo')]},
            'jar_anchor': {'position': [k(0, P()), k(4, P()), k(6, P(y=18), 'easeOutCubic')],
                           'scale': [k(0, S(1), interp='step'), k(6, S(0), interp='step')]},
            'hips': {'position': [k(0, P()), k(4, P()), k(5, P(y=-2.5), 'easeOutQuad'), k(8, P(y=-1.5), 'easeInOutSine'),
                                  k(10, P(y=-1.5)), k(24, P(y=PARK_HIPS - 0.5), 'easeInQuad'),
                                  k(28, P(y=PARK_HIPS), 'easeInOutSine')]},
            'torso': {'rotation': [k(0, R()), k(4, R()), k(5, R(x=1.5), 'easeOutQuad'), k(10, R(), 'easeInOutSine'),
                                   k(24, R(x=PARK_TORSO - 0.5), 'easeInOutSine'), k(28, R(x=PARK_TORSO), 'easeInOutSine')]},
            'arm_l': {'rotation': const(R())}, 'arm_r': {'rotation': const(R())},
            'forearm_l': {'rotation': [k(0, R()), k(10, R()), k(22, R(x=PARK_FOREARM), 'easeInOutSine')]},
            'forearm_r': {'rotation': [k(0, R()), k(11, R()), k(23, R(x=PARK_FOREARM), 'easeInOutSine')]},
            'sensor': {'rotation': [k(0, R()), k(12, R()), k(24, R(x=PARK_SENSOR), 'easeInOutSine')]},
        }, **lamps([(0, 1), (20, 0)], [(0, 1), (16, 0)], [(0, 1), (12, 0)])),
        'effects': [(0, 'sound', snd('klaxon', 0.8, 1.0), None),
                    (2, 'sound', snd('bar_blow', 0.8, 1.0), None),
                    (4, 'timeline', 'eject:jar', None),
                    (8, 'sound', snd('bar_clank', 0.6, 0.9), None),
                    (14, 'particle', 'large_smoke', 'fx_hull'),
                    (22, 'particle', 'large_smoke', 'fx_hull'),
                    (26, 'sound', snd('sigh', 0.5, 0.9), None)],
        'pilot_until': 6,
    }


def destroyed():
    """Death (40 ticks before the server's explosion): sparks, lamps die in three steps, arms drop, the left knee
    gives then the right, the machine topples forward onto its face (contact tick 27), bounces once and lies there
    smoking."""
    return {
        'length': 40, 'loop': 'hold', 'feet': planted(), 'ik_until': 18,
        'bones': dict({
            'hips': {'position': [k(0, P()), k(8, P()), k(12, P(y=-5), 'easeInQuad'), k(14, P(y=-4.5), 'easeOutQuad'),
                                  k(18, P(y=-8), 'easeInQuad')],
                     'rotation': [k(0, R()), k(8, R()), k(12, R(z=6), 'easeInQuad'), k(18, R(z=3), 'easeInOutSine')]},
            'torso': {'rotation': [k(0, R()), k(10, R()), k(18, R(x=-10), 'easeInQuad')]},
            'root': {'rotation': [k(0, R()), k(18, R()), k(27, R(x=-74), 'easeInQuad'), k(29, R(x=-69), 'easeOutQuad'),
                                  k(31, R(x=-74), 'easeInQuad')],
                     'position': [k(0, P()), k(18, P()), k(27, P(y=12, z=-2), 'easeInQuad')]},
            'arm_l': {'rotation': [k(0, R()), k(2, R()), k(8, R(x=-10), 'easeInQuad'), k(27, R(x=-10)),
                                   k(29, R(x=-30), 'easeOutQuad'), k(32, R(x=-25), 'easeInOutSine')]},
            'arm_r': {'rotation': [k(0, R()), k(3, R()), k(9, R(x=-10), 'easeInQuad'), k(27, R(x=-10)),
                                   k(29, R(x=-28), 'easeOutQuad'), k(32, R(x=-24), 'easeInOutSine')]},
            'forearm_l': {'rotation': [k(0, R()), k(3, R()), k(9, R(x=-60), 'easeInQuad')]},
            'forearm_r': {'rotation': [k(0, R()), k(4, R()), k(10, R(x=-60), 'easeInQuad')]},
            'sensor': {'rotation': [k(0, R()), k(6, R()), k(14, R(x=-25), 'easeInQuad')]},
            'restraint': {'rotation': [k(0, R()), k(27, R()), k(28, R(x=6), 'easeOutQuad'), k(31, R(), 'easeInQuad')]},
        }, **lamps([(0, 1), (18, 0)], [(0, 1), (11, 0)], [(0, 1), (4, 0)])),
        'effects': [(0, 'timeline', 'fx:sparks:fx_hull', None),
                    (7, 'particle', 'electric_spark', 'fx_core'),
                    (12, 'sound', snd('servo_fail', 0.6, 0.8), None),
                    (14, 'particle', 'electric_spark', 'fx_hull'),
                    (27, 'timeline', 'destroyed:thud', None),
                    (33, 'particle', 'large_smoke', 'fx_hull')],
        'pilot': False,
    }


# Build: each slot's socket bone is hidden (scale 0) until its module arrives, appears 4 px out along its own -Z and
# seats with easeInQuad over 3 ticks. Slot order from MechaFrame (legs, back, core, torso, arm, sensor).
BUILD_SLOTS = [('legs', 62, ['socket_thigh_l', 'socket_thigh_r', 'socket_shin_l', 'socket_shin_r']),
               ('back', 73, ['socket_back']), ('core', 84, ['socket_core']), ('torso_0', 95, ['socket_torso_0']),
               ('torso_1', 106, ['socket_torso_1']), ('arm_l', 117, ['socket_arm_l']), ('arm_r', 128, ['socket_arm_r']),
               ('sensor', 139, ['socket_sensor'])]


def outward(bone):
    """The socket's outward direction (its local -Z) in its parent's frame, from its rest rotation."""
    rot = [b for b in G.bones() if b[0] == bone][0][3] or [0, 0, 0]
    x, y, z = [math.radians(a) for a in rot]
    v = [0.0, 0.0, -1.0]
    # three.js Euler ZYX order as Blockbench applies it: R = Rz * Ry * Rx
    vx, vy, vz = v
    vy, vz = vy * math.cos(x) - vz * math.sin(x), vy * math.sin(x) + vz * math.cos(x)
    vx, vz = vx * math.cos(y) + vz * math.sin(y), -vx * math.sin(y) + vz * math.cos(y)
    vx, vy = vx * math.cos(z) - vy * math.sin(z), vx * math.sin(z) + vy * math.cos(z)
    return [round(vx, 6), round(vy, 6), round(vz, 6)]


def build():
    """The Gantry's assembly as the mecha's own clip (200 ticks). The frame arrives folded in its shipping pose, restraint
    open; unfolds and stands (40-60); the modules arrive one slot at a time (60-150); inspection sweep (170-185);
    the restraint shuts and it sags into the parked pose to wait for a pilot."""
    bones = {
        'hips': {'position': [k(0, P(y=-10)), k(42, P(y=-10)), k(44, P(y=-10.6), 'easeOutQuad'),
                              k(55, P(y=0.6), 'easeInOutCubic'), k(57, P(y=-1), 'easeInQuad'), k(60, P(), 'easeInOutSine'),
                              k(62, P()), k(65, P(y=-0.8), 'easeOutQuad'), k(70, P(), 'easeInOutSine'),
                              k(150, P()), k(153, P(y=-1), 'easeOutQuad'), k(158, P(), 'easeInOutSine'),
                              k(186, P()), k(198, P(y=PARK_HIPS), 'easeInQuad'), k(200, P(y=PARK_HIPS))]},
        'torso': {'rotation': [k(0, R(x=-10)), k(46, R(x=-10)), k(57, R(x=1), 'easeInOutCubic'), k(60, R(), 'easeInOutSine'),
                               k(188, R()), k(198, R(x=PARK_TORSO), 'easeInOutSine')]},
        'arm_l': {'rotation': [k(0, R(x=-15)), k(50, R(x=-15)), k(58, R(x=3), 'easeInOutCubic'), k(61, R(), 'easeInOutSine'),
                               k(117, R()), k(119, R(x=15), 'easeOutQuad'), k(124, R(), 'easeInOutSine')]},
        'arm_r': {'rotation': [k(0, R(x=-15)), k(51, R(x=-15)), k(59, R(x=3), 'easeInOutCubic'), k(62, R(), 'easeInOutSine'),
                               k(128, R()), k(130, R(x=15), 'easeOutQuad'), k(135, R(), 'easeInOutSine')]},
        'forearm_l': {'rotation': [k(0, R(x=40)), k(52, R(x=40)), k(59, R(x=-3), 'easeInOutCubic'), k(62, R(), 'easeInOutSine'),
                                   k(190, R()), k(198, R(x=PARK_FOREARM), 'easeInOutSine')]},
        'forearm_r': {'rotation': [k(0, R(x=40)), k(53, R(x=40)), k(60, R(x=-3), 'easeInOutCubic'), k(63, R(), 'easeInOutSine'),
                                   k(191, R()), k(199, R(x=PARK_FOREARM), 'easeInOutSine')]},
        'sensor': {'rotation': [k(0, R(x=30)), k(52, R(x=30)), k(58, R(x=-2), 'easeOutCubic'), k(60, R(), 'easeInOutSine'),
                                k(170, R()), k(174, R(x=15), 'easeOutCubic'), k(178, R(y=25), 'easeInOutSine'),
                                k(182, R(y=-25), 'easeInOutSine'), k(185, R(), 'easeInOutSine'),
                                k(188, R()), k(198, R(x=PARK_SENSOR), 'easeInOutSine')]},
        'restraint': {'rotation': [k(0, R(x=100)), k(185, R(x=100)), k(186, R(x=104), 'easeOutQuad'),
                                k(193, R(), 'easeInQuad'), k(195, R(x=2), 'easeOutQuad'), k(197, R(), 'easeInQuad')]},
    }
    bones.update(lamps([(0, 0), (190, 1), (194, 0)], [(0, 0)], [(0, 0), (170, 1), (186, 0)]))
    bones.update(clamps([k(0, [30]), k(193, [30]), k(195, [0], 'easeInQuad')]))
    effects = [(44, 'sound', snd('hydraulic_stand', 0.6, 1.0), None), (57, 'timeline', 'build:stand', None),
               (174, 'sound', snd('scan_chirp', 0.4, 1.0), None), (190, 'sound', snd('ding', 0.4, 1.0), None),
               (193, 'sound', snd('bar_latch', 0.6, 1.0), None), (196, 'sound', snd('sigh', 0.4, 1.0), None)]
    for slot, T0, socks in BUILD_SLOTS:
        for sb in socks:
            o = outward(sb)
            out = [6 * c for c in o]
            bones[sb] = {'scale': [k(0, S(0), interp='step'), k(T0, S(1), interp='step')],
                         'position': [k(0, P(*out)), k(T0, P(*out)), k(T0 + 3, P(), 'easeInQuad'),
                                      k(T0 + 4, P(*[1.0 * c for c in o]), 'easeOutQuad'), k(T0 + 5, P(), 'easeInQuad')]}
        effects.append((T0 + 3, 'timeline', 'build:seat:%s' % slot, None))
    return {'length': 200, 'loop': 'hold', 'feet': planted(), 'bones': bones, 'effects': effects, 'pilot': False}


def clips():
    out = [('parked', parked()), ('board', board()), ('boot', boot()), ('boot_legs', boot_legs()),
           ('shutdown', shutdown()), ('idle', idle()), ('idle_tic', idle_tic()), ('walk', walk()), ('limp', limp()),
           ('limp_l', limp_l()), ('limp_r', mirror_clip(limp_l(), WALK_N // 2)),
           ('turn_l', turn('l')), ('turn_r', turn('r')), ('jump', jump()), ('fall', fall()), ('land', land(False)),
           ('land_heavy', land(True)), ('pogo_crouch', pogo_crouch()), ('pogo_hold', pogo_hold()),
           ('pogo_leap', pogo_leap()), ('pogo_land', pogo_land())]
    for name, c in verbs_left().items():
        out.append(('%s_l' % name, c))
        out.append(('%s_r' % name, side_clip(c)))
    out += [('vent', vent()), ('hurt', hurt()), ('eject', eject()), ('destroyed', destroyed()), ('build', build())]
    return out


# ------------------------------------------------------------------------------------------------------------------
# bbmodel writing
# ------------------------------------------------------------------------------------------------------------------
def nid():
    return str(uuidlib.uuid4())


def keyframe(channel, t, v, e, a, interp):
    kf = {'channel': channel, 'data_points': [{'x': round(v[0], 4), 'y': round(v[1], 4), 'z': round(v[2], 4)}],
          'uuid': nid(), 'time': round(t / T, 4), 'color': -1, 'interpolation': interp}
    if e and interp == 'linear' and e.lower() != 'linear':
        kf['easing'] = e
        if a is not None:
            kf['easingArgs'] = a
    return kf


def bake(keys):
    out = []
    for i, (t, v, e, a, interp) in enumerate(keys):
        if i == 0:
            out.append((t, v, None, None, interp))
            continue
        t0, v0 = keys[i - 1][0], keys[i - 1][1]
        if keys[i - 1][4] == 'step' or interp == 'step':
            out.append((t, v, None, None, interp))
            continue
        for tt in range(int(t0) + 1, int(t) + 1):
            f = ease(e, a, (tt - t0) / float(t - t0))
            out.append((tt, [v0[j] + (v[j] - v0[j]) * f for j in range(3)], None, None, 'linear'))
    return out


def build_anims(model, baked=False):
    groups = {g['name']: g['uuid'] for g in model['groups']}
    anims = []
    for name, clip in clips():
        clip = copy.deepcopy(clip)
        scale_positions(clip)
        if clip.get('level_legs'):
            check_reach(name, clip)
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
        if baked:
            # review copy only: the stand-in jar is drawn while a pilot is aboard
            if clip.get('pilot') is False:
                vis = [keyframe('scale', 0, S(0), None, None, 'step')]
            elif 'pilot_until' in clip:
                vis = [keyframe('scale', 0, S(1), None, None, 'step'),
                       keyframe('scale', clip['pilot_until'] + 2, S(0), None, None, 'step')]
            else:
                vis = None
            if vis:
                animators[groups['standin_jar']] = {'name': 'standin_jar', 'type': 'bone', 'keyframes': vis}
        if effects:
            animators['effects'] = {'name': 'Effects', 'type': 'effect', 'keyframes': effects}
        anims.append({'uuid': nid(), 'name': name, 'loop': clip['loop'], 'override': False,
                      'length': clip['length'] / T, 'snapping': 20, 'selected': False, 'animators': animators})
    return anims


# ------------------------------------------------------------------------------------------------------------------
# Frames
# ------------------------------------------------------------------------------------------------------------------
HOPPER_FRAME = dict(model=MODEL, speed=0.13, scale=1, toe=6.0, walk=dict(n=12, stance=4, lift=6.5, bob=BOB))


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
    """Point every clip at a frame: its geo module and its ANIM parameters over the Hopper's. A walk whose timing
    differs gets the Hopper's bob resampled to its step (in Hopper pixels; scale_positions sizes it)."""
    g = globals()
    own = getattr(geo, 'ANIM', {})
    fr = dict(HOPPER_FRAME, **{kk: v for kk, v in own.items() if kk != 'walk'})
    w = dict(HOPPER_FRAME['walk'], **own.get('walk', {}))
    if 'bob' not in own.get('walk', {}):
        w['bob'] = resample(HOPPER_FRAME['walk']['bob'], w['n'] // 2)
    fr['walk'] = w
    g.update(G=geo, F=fr, MODEL=fr['model'], SC=fr['scale'], TOE=fr['toe'], WALK_N=w['n'], WALK_STANCE=w['stance'],
             WALK_LIFT=w['lift'], BOB=w['bob'])
    g['V_WALK'] = fr['speed'] * 2.16 * 16
    g['V_LIMP'] = g['V_WALK'] * 0.3
    g['WALK_REACH'] = g['V_WALK'] * g['WALK_STANCE'] / 2.0
    g['REST_FOOT_T'] = (geo.REST_ANKLE[0], geo.REST_ANKLE[1], 0.0)
    return fr


def scale_positions(clip):
    """Hopper-pixel position keys to the frame's size (not the jar's travel), and authored ankle targets as offsets
    from the frame's rest ankle. A clip marked native is left alone."""
    if SC == 1 or clip.get('native'):
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


WALK_LIFT = 6.5
SC = 1
use_frame(G)

if __name__ == '__main__':
    main()

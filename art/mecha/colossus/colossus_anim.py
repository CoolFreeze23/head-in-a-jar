"""Colossus: every clip, keyed in ticks, written into colossus.bbmodel.

The heavy-biped clip script (art/mecha/bulwark/bulwark_anim.py) run at the Colossus's sizes (use_frame: its rig numbers,
speed, walk timings and arm angles from colossus_geo.ANIM; the shared clips' position keys scaled 1.42; the legs
re-solved every tick for its own thigh and shin), then:
  - the hardpoints (shoulder weapons) folded back when parked, unfolded on boot, scanning on the idle tic;
  - Siege, the drive verb: siege_in (wind-up and the act), siege (held), siege_out (recovery), siege_refused;
  - its own build.

    python art/mecha/colossus/colossus_anim.py                 # write the clips into colossus.bbmodel
    python art/mecha/colossus/colossus_anim.py --preview OUT   # review copy (baked, stand-in jar hidden when empty)

Siege clips are authored in Colossus pixels (native: not scaled).
"""
import copy
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(os.path.dirname(HERE), 'bulwark'))
sys.path.insert(0, os.path.join(os.path.dirname(HERE), 'hopper'))
import colossus_geo as G  # noqa: E402
import bulwark_anim as BA  # noqa: E402
from hopper_anim import k, R, P, S, const, snd, value_at, ease  # noqa: E402

BA.use_frame(G)
STOW = G.HARD_STOW
REST = BA.REST_FOOT_T


def hardpoints(keys_l, lag=0, swivel=None):
    """Both shoulder hardpoints: x keys for the left, the right the same `lag` ticks later (dropped past the end)."""
    last = max(kk[0] for kk in keys_l)
    keys_r = [((kk[0] + lag) if 0 < kk[0] < last else kk[0],) + kk[1:] for kk in keys_l
              if not (0 < kk[0] < last and kk[0] + lag >= last)]
    d = {'hardpoint_l': {'rotation': keys_l}, 'hardpoint_r': {'rotation': keys_r}}
    if swivel:
        d['socket_arm_2'] = {'rotation': swivel}
        d['socket_arm_3'] = {'rotation': [(t, [v[0], -v[1], -v[2]], e, a, i) for (t, v, e, a, i) in swivel]}
    return d


def add(clip, bones, effects=()):
    clip['bones'].update(bones)
    taken = {e[0] for e in clip['effects']}
    for e in effects:
        assert e[0] not in taken, (e, 'tick taken')
        clip['effects'].append(e)
        taken.add(e[0])
    return clip


# ==================================================================================================================
# Shared clips, patched for the hardpoints
# ==================================================================================================================
def parked():
    return add(BA.parked(), hardpoints([k(0, R(x=STOW))]))


def boot():
    """The boot tic (animation.md): the shoulder weapons unfold from its back, one then the other, with a hiss."""
    hp = [k(0, R(x=STOW)), k(7, R(x=STOW)), k(8, R(x=STOW + 4), 'easeOutQuad'), k(17, R(x=-8), 'easeInOutCubic'),
          k(19, R(x=3), 'easeInOutSine'), k(22, R(), 'easeInOutSine')]
    return add(BA.boot(), hardpoints(hp, lag=5), [(8, 'sound', snd('hydraulic_hiss', 0.6, 0.8), None),
                                                  (17, 'sound', snd('latch_clunk', 0.6, 0.8), None),
                                                  (22, 'sound', snd('latch_clunk', 0.6, 0.75), None)])


def shutdown():
    hp = [k(0, R()), k(4, R()), k(14, R(x=STOW), 'easeInOutCubic')]
    return add(BA.shutdown(), hardpoints(hp, lag=3), [(5, 'sound', snd('hydraulic_hiss', 0.5, 0.7), None)])


def eject():
    hp = [k(0, R()), k(12, R()), k(24, R(x=STOW), 'easeInOutCubic')]
    return add(BA.eject(), hardpoints(hp, lag=2))


def destroyed():
    hp = [k(0, R()), k(10, R()), k(16, R(x=25), 'easeInQuad'), k(19, R(x=18), 'easeOutQuad'), k(27, R(x=40), 'easeInQuad')]
    return add(BA.destroyed(), hardpoints(hp, lag=3))


def idle_tic():
    """Every 200-300 idle ticks: the shoulder weapons swivel to scan (animation.md), the left out then across, the
    right following two ticks behind, the cab lamp looking where they look."""
    sw = [k(0, R()), k(8, R(y=35), 'easeInOutCubic'), k(14, R(y=35)), k(26, R(y=-30), 'easeInOutCubic'),
          k(32, R(y=-30)), k(42, R(), 'easeInOutCubic'), k(44, R(y=2), 'easeInOutSine'), k(46, R(), 'easeInOutSine')]
    right = [k(0, R()), k(10, R(y=30), 'easeInOutCubic'), k(16, R(y=30)), k(28, R(y=-35), 'easeInOutCubic'),
             k(34, R(y=-35)), k(44, R(), 'easeInOutCubic'), k(48, R(), 'easeInOutSine')]
    return {'length': 48, 'loop': 'once', 'feet': BA.planted(),
            'bones': {'socket_arm_2': {'rotation': sw}, 'socket_arm_3': {'rotation': right},
                      'sensor': {'rotation': [k(0, R()), k(9, R(y=20), 'easeInOutSine'), k(15, R(y=20)),
                                              k(27, R(y=-18), 'easeInOutSine'), k(33, R(y=-18)),
                                              k(44, R(), 'easeInOutSine')]},
                      'torso': {'rotation': [k(0, R()), k(10, R(y=2), 'easeInOutSine'), k(28, R(y=-2), 'easeInOutSine'),
                                             k(44, R(), 'easeInOutSine')]}},
            'effects': [(1, 'sound', snd('turret_whir', 0.4, 0.9), None), (14, 'sound', snd('servo_tick', 0.3, 1.0), None),
                        (19, 'sound', snd('turret_whir', 0.4, 0.85), None), (32, 'sound', snd('servo_tick', 0.3, 0.9), None),
                        (43, 'sound', snd('servo_tick', 0.3, 0.8), None)]}


# ==================================================================================================================
# The build's delivery kneel (the Gantry hands it over kneeling; it stands in two heaves)
# ==================================================================================================================
SPLAY = 9.0                     # each leg rolled out
SIEGE_HIPS = -16.0              # the deck (and the jar) 16 px lower and 3 forward: a level kneel, knees down
SIEGE_Z = -3.0


def SP(y):
    return P(y=y, z=SIEGE_Z)

SIEGE_TORSO = 3.0               # leaning back over its haunches
STEP_OUT = (10.1, G.ANKLE_H, 0.0)   # each boot set down 10 px back (and out, by the splay)
TOE_PIVOT = STEP_OUT[0] - BA.TOE   # the toe tip the foot rolls up on: z = -2


def rolled(a):
    """The planted boot rolled up onto its toe tip by a degrees (the heel rising as the knee goes down)."""
    r = math.radians(a)
    return (TOE_PIVOT + BA.TOE * math.cos(r), G.ANKLE_H + BA.TOE * math.sin(r), -a)


SIEGED = rolled(24)             # ankle (8.7, 10.5): the shin lies nearly flat behind the knee, the boot tipped onto its
                                # toe, the knee pad face down and the spade teeth in the ground
LIFT = (5.0, 16.0, 8.0)         # a boot in the air between rest and the step out


def roll_keys(t0, t1, a0, a1, steps=3):
    out = []
    for i in range(steps + 1):
        f = i / float(steps)
        out.append(k(round(t0 + (t1 - t0) * f), rolled(a0 + (a1 - a0) * f)))
    return out


# ==================================================================================================================
# Siege: the drive verb. "In Siege Mode the Colossus becomes a building."
#
# Held, it is a gatehouse on the ground: the deck comes down 26 px, level, until the hip drums are a block off the
# ground; the legs run out behind it as two outriggers (each thigh down and back to a knee whose spade teeth are in the
# ground, the shin on along the ground, the boot at the end tipped onto its toe), the arms run down rails on the tower
# faces and the forearms come down and out to the ground ahead as buttresses, fists planted; the shoulder cradles
# stand up on their hinges behind the merlons as parapets, the shoulder weapons kept level on top of them.
#
# Legs, arms, hands, feet and the weapon sockets are solved every tick from world targets (planted spots and knee or
# elbow hints) against the eased hips and torso curves, so whatever is planted stays planted while the deck moves.
# ==================================================================================================================
def _mm(a, b):
    return [[sum(a[i][n] * b[n][j] for n in range(3)) for j in range(3)] for i in range(3)]


def _mv(a, v):
    return [sum(a[i][n] * v[n] for n in range(3)) for i in range(3)]


def _tr(a):
    return [[a[j][i] for j in range(3)] for i in range(3)]


def _add(a, b):
    return [a[i] + b[i] for i in range(3)]


def _sub(a, b):
    return [a[i] - b[i] for i in range(3)]


def _scl(a, s):
    return [c * s for c in a]


def _dot(a, b):
    return sum(a[i] * b[i] for i in range(3))


def _cross(a, b):
    return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]


def _unit(a):
    n = math.sqrt(_dot(a, a))
    return [c / n for c in a]


def _axis(ax, deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return {'x': [[1, 0, 0], [0, c, -s], [0, s, c]], 'y': [[c, 0, s], [0, 1, 0], [-s, 0, c]],
            'z': [[c, -s, 0], [s, c, 0], [0, 0, 1]]}[ax]


def euler(r):
    """A bone rotation (x, y, z) in degrees as Blockbench and GeckoLib apply it: Rz Ry Rx."""
    return _mm(_mm(_axis('z', r[2]), _axis('y', r[1])), _axis('x', r[0]))


def to_euler(m):
    """The (x, y, z) degrees whose Rz Ry Rx is m."""
    b = math.asin(max(-1.0, min(1.0, -m[2][0])))
    return [math.degrees(math.atan2(m[2][1], m[2][2])), math.degrees(b), math.degrees(math.atan2(m[1][0], m[0][0]))]


def _cols(x, y, z):
    return [[x[i], y[i], z[i]] for i in range(3)]


RIG = {n: (piv, par, rot or [0, 0, 0]) for n, piv, par, rot in G.bones()}
LEG_UPPER = G.THIGH                                     # hip to knee
LEG_LOWER = G.SHIN                                      # knee to ankle
ARM_UPPER = RIG['arm_l'][0][1] - RIG['forearm_l'][0][1]     # shoulder to elbow (9)
ARM_LOWER = RIG['forearm_l'][0][1] - RIG['hand_l'][0][1]    # elbow to wrist (11)
FIST = RIG['hand_l'][0][1] - 29.0                       # wrist to the bottom of the fist (12: the finger block's sole)
BOOT_TOE = (0.0, -G.ANKLE_H, -BA.TOE)                   # the toe cap's tip under the ankle, in the foot's frame
BOOT_HEEL = (0.0, -G.ANKLE_H, BA.HEEL)
SPADE_BOX = [(0.0, a, f) for a in (-2.0, -8.0) for f in (-9.0, -13.0)]   # the spade teeth's edges, from the knee (shin)
SPADE_FX = (0.0, -5.0, -13.0)                           # fx_spade_l/r, from the knee


def fist_points(s):
    """The corners of the fist (palm, knuckles, finger block) in the bind pose."""
    pts = []
    for name, a, b, mat, par, kw in G.cubes():
        if par in ('hand_' + s, 'grip_' + s):
            pts += [[(a, b)[i][0], (a, b)[j][1], (a, b)[kk][2]] for i in (0, 1) for j in (0, 1) for kk in (0, 1)]
    return pts


FIST_PTS = {s: fist_points(s) for s in 'lr'}


def spade_low(knee, w_shin):
    """The lowest edge of the spade teeth."""
    return min((_add(knee, _mv(w_shin, c)) for c in SPADE_BOX), key=lambda p: p[1])


class Frames(object):
    """World transforms of hips and torso at one tick, from the clip's eased keys."""

    def __init__(self, bones, t):
        def chan(b, c):
            keys = bones.get(b, {}).get(c)
            return value_at(sorted(keys, key=lambda x: x[0]), t) if keys else [0.0, 0.0, 0.0]
        self.hp, self.hr = chan('hips', 'position'), euler(chan('hips', 'rotation'))
        self.tp, self.tr = chan('torso', 'position'), euler(chan('torso', 'rotation'))
        self.torso_r = _mm(self.hr, self.tr)

    def hips(self, p):
        piv = RIG['hips'][0]
        return _add(_add(piv, self.hp), _mv(self.hr, _sub(p, piv)))

    def torso(self, p):
        piv = RIG['torso'][0]
        return self.hips(_add(_add(piv, self.tp), _mv(self.tr, _sub(p, piv))))


def two_bone(root, target, l1, l2, hint, bend_front):
    """World frames of a two-bone hinge chain hanging along -y in its bind pose: the middle joint is pushed toward
    `hint`; bend_front puts the bones' fronts (-z) on that side (a knee), otherwise their backs (+z: an elbow)."""
    d = _sub(target, root)
    dist = math.sqrt(_dot(d, d))
    reach = min(max(dist, abs(l1 - l2) + 0.01), l1 + l2 - 0.001)
    u = _unit(d)
    v = _unit(_sub(hint, _scl(u, _dot(hint, u))))
    a = (l1 * l1 - l2 * l2 + reach * reach) / (2 * reach)
    mid = _add(root, _add(_scl(u, a), _scl(v, math.sqrt(max(0.0, l1 * l1 - a * a)))))
    end = _add(root, _scl(u, reach))
    y1 = _unit(_sub(root, mid))
    side = _unit(_sub(v, _scl(y1, _dot(v, y1))))
    z1 = _scl(side, -1.0) if bend_front else side
    x = _cross(y1, z1)
    y2 = _unit(_sub(mid, end))
    z2 = _cross(x, y2)
    return _cols(x, y1, z1), _cols(x, y2, z2), mid, end, dist - reach


def _unwrap(prev, cur):
    if prev is None:
        return cur
    return [c + 360.0 * round((p - c) / 360.0) for p, c in zip(prev, cur)]


def _thin3(seq, loop):
    """Per-tick (t, [x, y, z]) to keys: a tick is kept when any component needs it."""
    ts = set()
    for i in range(3):
        ts |= {t for t, _ in BA.thin([(t, v[i]) for t, v in seq], loop)}
    d = dict(seq)
    return [k(t, [round(c, 3) for c in d[t]]) for t in sorted(ts)]


def solve_siege(clip, legs, arms, cradles=None):
    """Adds every tick's leg, shin, foot, arm, forearm, hand (and weapon socket) keys to a Siege clip.

    legs[s]: keys of 'toe' (the boot's toe tip, world px), 'foot' ([yaw, tilt, 0]: the boot's heading, out positive,
    and its toe-down tilt) and 'knee' (the knee hint); arms[s]: keys of 'fist' (the fist's sole), 'plant' (0: 'fist' is
    in the torso's bind frame and rides the deck, 1: it is a world spot), 'elbow' (the elbow hint, in the torso's
    frame), 'slide' (the arm's own position: down its rail on the tower face) and 'level' (0: the hand hangs in line,
    1: the fist stands level on the ground); cradles: keys of the hardpoints' x, the sockets' swivel and 'lift' (the
    socket's position on the cradle), the weapon socket kept level whatever the cradle's angle."""
    bones, n, loop = clip['bones'], clip['length'], clip['loop'] == 'loop'
    seqs, prev, report = {}, {}, clip.setdefault('report', {})

    def put(bone, t, rot):
        e = _unwrap(prev.get(bone), rot)
        prev[bone] = e
        seqs.setdefault(bone, []).append((t, e))

    for t in range(n + 1):
        f = Frames(bones, t)
        for s, sx in (('l', -1.0), ('r', 1.0)):
            lg = legs[s]
            yaw, tilt, _ = value_at(lg['foot'], t)
            wf = _mm(_axis('y', -sx * yaw), _axis('x', -tilt))
            ankle = _sub(value_at(lg['toe'], t), _mv(wf, BOOT_TOE))
            hip = f.hips(RIG['leg_' + s][0])
            w1, w2, knee, end, short = two_bone(hip, ankle, LEG_UPPER, LEG_LOWER, value_at(lg['knee'], t), True)
            rest = RIG['leg_' + s][2]
            l1 = to_euler(_mm(_tr(f.hr), w1))
            put('leg_' + s, t, [l1[0] - rest[0], l1[1], l1[2]])
            put('shin_' + s, t, [to_euler(_mm(_tr(w1), w2))[0] - RIG['shin_' + s][2][0], 0.0, 0.0])
            fo = to_euler(_mm(_tr(w2), wf))
            put('foot_' + s, t, [fo[0] - RIG['foot_' + s][2][0], fo[1], fo[2]])
            spade = spade_low(knee, w2)
            report.setdefault('spade_' + s, []).append((t, spade, _add(knee, _mv(w2, SPADE_FX)), short, l1[1]))
            report.setdefault('boot_' + s, []).append((t, _add(end, _mv(wf, BOOT_TOE)), _add(end, _mv(wf, BOOT_HEEL))))

            am = arms[s]
            slide = value_at(am['slide'], t)
            sh = f.torso(_add(RIG['arm_' + s][0], slide))
            lev = value_at(am['level'], t)[0]
            w = value_at(am['plant'], t)[0]
            wrist = _add(value_at(am['fist'], t), [0.0, FIST, 0.0])
            wrist = _add(_scl(f.torso(wrist), 1.0 - w), _scl(wrist, w))
            sole = wrist[1] - FIST
            hint = _mv(f.torso_r, value_at(am['elbow'], t))
            for _ in range(8):
                # the wrist is raised or lowered until the fist's lowest edge is on its spot (by the planted share)
                u1, u2, elbow, _, short = two_bone(sh, wrist, ARM_UPPER, ARM_LOWER, hint, False)
                hd = [c * lev for c in to_euler(_tr(u2))]
                wh = _mm(u2, euler(hd))
                low = min(_add(wrist, _mv(wh, _sub(p, RIG['hand_' + s][0])))[1] for p in FIST_PTS[s])
                wrist = _add(wrist, [0.0, (sole - low) * w, 0.0])
            u1, u2, elbow, _, short = two_bone(sh, wrist, ARM_UPPER, ARM_LOWER, hint, False)
            hd = [c * lev for c in to_euler(_tr(u2))]
            rest = RIG['arm_' + s][2]
            a1 = to_euler(_mm(_tr(f.torso_r), u1))
            put('arm_' + s, t, [a1[0] - rest[0], a1[1] - rest[1], a1[2] - rest[2]])
            put('forearm_' + s, t, [to_euler(_mm(_tr(u1), u2))[0] - RIG['forearm_' + s][2][0], 0.0, 0.0])
            put('hand_' + s, t, hd)
            wh = _mm(u2, euler(hd))
            low = min(_add(wrist, _mv(wh, _sub(p, RIG['hand_' + s][0])))[1] for p in FIST_PTS[s])
            report.setdefault('wrist_' + s, []).append((t, short, a1[1], low, wrist))
            bones.setdefault('arm_' + s, {})['position'] = am['slide']
        if cradles:
            for s, sx in (('l', -1.0), ('r', 1.0)):
                th = value_at(cradles['x'], t)[0]
                sw = value_at(cradles['swivel'], t)[1] * (1 if s == 'l' else -1)
                put('socket_arm_%d' % (2 if s == 'l' else 3), t, to_euler(_mm(_axis('x', -th), _axis('y', sw))))
    for bone, seq in seqs.items():
        bones.setdefault(bone, {})['rotation'] = _thin3(seq, loop)
    if cradles:
        for s in 'lr':
            bones['hardpoint_' + s] = {'rotation': cradles['x']}
            bones['socket_arm_%d' % (2 if s == 'l' else 3)]['position'] = cradles['lift']
    return clip


# ---- the held pose --------------------------------------------------------------------------------------------------
SG_DROP = 26.0                  # the deck comes down 26 px (1.625 blocks), level, neither forward nor back
SG_AZ = 18.0                    # each leg runs out behind, 18 degrees out from straight back (wider, and the thigh
                                # would leave its hip drum through the axle hub)
SG_RISE = 8.0                   # the shin rises 8 degrees from the knee to the ankle
SG_BITE = 3.6                   # the spade teeth this far into the ground (the knee pad then just meets it)
SG_MID = 30.0                   # the step out: each boot set down this far from under its hip, along the leg's line
SG_SLIDE = 4.0                  # each arm runs down its rail on the tower's outer face, clear of the hip drum
SG_FIST = (30.0, 0.0)           # the planted fist's sole (x out, z), on the ground, out from under the shoulder
SG_LEVEL = 0.0                  # the fist stays in line with the forearm (the palm sits flush under the cuff: any turn at
                                # the wrist would push it into the cuff); 1 would stand the fist level on the ground
SG_ELBOW = (0.0, 0.0, 1.0)      # the elbows back: the forearms' fronts (and a fitted arm module) face forward, 10
                                # degrees up, not in at the hull
SG_CRADLE = 72.0                # the cradles stand up on their hinges, leaning back onto the rear merlons
CRADLE_TOP = (0.0, -2.5, -3.0)  # the weapon socket's spot on a raised cradle: the middle of what was its front face
KNEE_HINT = (0.0, -1.0, -1.0)   # a standing knee: forward (a leg run out behind takes line_hint: knee down, pad and
                                # spades to the ground)


def leg_dir(sx):
    a = math.radians(SG_AZ)
    return [sx * math.sin(a), 0.0, math.cos(a)]


def line_hint(sx):
    """A knee pushed down and toward the hip along the leg's line: the leg stays in its upright plane."""
    d = leg_dir(sx)
    return [-d[0], -1.0, -d[2]]


def boot_rot(yaw, tilt):
    return _mm(_axis('y', yaw), _axis('x', -tilt))


def flat_toe(sx, dist, yaw):
    """The toe tip of a boot set flat with its ankle `dist` out along the leg's line, heading yaw (in negative)."""
    d = leg_dir(sx)
    ankle = [sx * G.HIP_X + d[0] * dist, G.ANKLE_H, d[2] * dist]
    return _add(ankle, _mv(boot_rot(-sx * yaw, 0.0), BOOT_TOE))


def outrigger(sx):
    """The held leg: thigh down to a knee low enough for the spade teeth to bite SG_BITE, the shin rising SG_RISE on to
    the ankle, the boot heading back along the leg toward the hip and tipped until its toe tip is on the ground.
    Returns the toe tip, the foot's [yaw, tilt, 0] and the knee."""
    hip = [sx * G.HIP_X, G.HIP_Y - SG_DROP, 0.0]
    d, e = leg_dir(sx), math.radians(SG_RISE)
    lo, hi = 0.0, hip[1]
    for _ in range(60):
        ky = (lo + hi) / 2
        dk = math.sqrt(LEG_UPPER ** 2 - (hip[1] - ky) ** 2)
        knee = [hip[0] + d[0] * dk, ky, d[2] * dk]
        dl = LEG_LOWER * math.cos(e)
        ankle = [knee[0] + d[0] * dl, ky + LEG_LOWER * math.sin(e), knee[2] + d[2] * dl]
        _, w2, kn, _, _ = two_bone(hip, ankle, LEG_UPPER, LEG_LOWER, line_hint(sx), True)
        if spade_low(kn, w2)[1] > -SG_BITE:
            hi = ky
        else:
            lo = ky
    yaw = -SG_AZ
    lo, hi = 0.0, 89.0
    for _ in range(60):
        tilt = (lo + hi) / 2
        if (ankle[1] + _mv(boot_rot(-sx * yaw, tilt), BOOT_TOE)[1]) > 0:
            lo = tilt
        else:
            hi = tilt
    return _add(ankle, _mv(boot_rot(-sx * yaw, tilt), BOOT_TOE)), [yaw, tilt, 0.0], knee


def rest_fist(sx):
    """Where the hanging fist's sole is in the torso's bind frame (arms at rest)."""
    s = 'l' if sx < 0 else 'r'
    sh, wu = RIG['arm_' + s][0], euler(RIG['arm_' + s][2])
    elbow = _add(sh, _mv(wu, [0.0, -ARM_UPPER, 0.0]))
    wrist = _add(elbow, _mv(_mm(wu, euler(RIG['forearm_' + s][2])), [0.0, -ARM_LOWER, 0.0]))
    return _sub(wrist, [0.0, FIST, 0.0])


HELD = {sx: outrigger(sx) for sx in (-1.0, 1.0)}
REST_TOE = {sx: [sx * G.HIP_X, 0.0, -BA.TOE] for sx in (-1.0, 1.0)}
MID_TOE = {sx: flat_toe(sx, SG_MID, -SG_AZ / 2) for sx in (-1.0, 1.0)}
FIST_HELD = {sx: [sx * SG_FIST[0], 0.0, SG_FIST[1]] for sx in (-1.0, 1.0)}
ELBOW_HELD = {sx: [sx * SG_ELBOW[0], SG_ELBOW[1], SG_ELBOW[2]] for sx in (-1.0, 1.0)}


def held_legs():
    return {s: {'toe': [k(0, HELD[sx][0])], 'foot': [k(0, HELD[sx][1])], 'knee': [k(0, line_hint(sx))]}
            for s, sx in (('l', -1.0), ('r', 1.0))}


def held_arms():
    return {s: {'fist': [k(0, FIST_HELD[sx])], 'plant': [k(0, [1, 1, 1])], 'elbow': [k(0, ELBOW_HELD[sx])],
                'slide': [k(0, P(y=-SG_SLIDE))], 'level': [k(0, [SG_LEVEL] * 3)]}
           for s, sx in (('l', -1.0), ('r', 1.0))}


def cradle_keys(x_keys, swivel=None):
    """The hardpoints' x keys, the weapon sockets' swivel and their spot on the cradle (CRADLE_TOP at SG_CRADLE)."""
    lift = [(t, [c * v[0] / SG_CRADLE for c in CRADLE_TOP], e, a, i) for (t, v, e, a, i) in x_keys]
    return {'x': x_keys, 'swivel': swivel or [k(0, R())], 'lift': lift}


VENTS_HELD = {
    'vent_l': {'rotation': [k(0, R(x=-74)), k(10, R(x=-78), 'easeInOutSine'), k(20, R(x=-72), 'easeInOutSine'),
                            k(30, R(x=-77), 'easeInOutSine'), k(45, R(x=-73), 'easeInOutSine'),
                            k(60, R(x=-74), 'easeInOutSine')]},
    'vent_r': {'rotation': [k(0, R(x=-74)), k(12, R(x=-77), 'easeInOutSine'), k(24, R(x=-72), 'easeInOutSine'),
                            k(38, R(x=-78), 'easeInOutSine'), k(50, R(x=-73), 'easeInOutSine'),
                            k(60, R(x=-74), 'easeInOutSine')]},
}


def siege():
    """Sieged (loop, 60 ticks): a building that breathes. Everything that touches the ground stays put; the deck lifts
    off its girder by a pixel and settles back, the vents flutter, the shoulder weapons swivel 6 degrees either side of
    dead ahead on their parapets, heat off the vents twice a loop."""
    bones = copy.deepcopy(VENTS_HELD)
    bones.update({
        'hips': {'position': const(P(y=-SG_DROP))},
        'torso': {'position': [k(0, P()), k(30, P(y=1.0), 'easeInOutSine'), k(60, P(), 'easeInOutSine')]},
        'sensor': {'rotation': [k(0, R(x=-2)), k(25, R(x=-5), 'easeInOutSine'), k(60, R(x=-2), 'easeInOutSine')]},
    })
    sw = [k(0, R()), k(15, R(y=6), 'easeInOutSine'), k(45, R(y=-6), 'easeInOutSine'), k(60, R(), 'easeInOutSine')]
    clip = {'length': 60, 'loop': 'loop', 'native': True, 'bones': bones,
            'effects': [(5, 'particle', 'cloud', 'fx_vent_l'), (20, 'sound', snd('siege_hum', 0.3, 0.7), None),
                        (35, 'particle', 'cloud', 'fx_vent_r')]}
    return solve_siege(clip, held_legs(), held_arms(), cradle_keys([k(0, R(x=SG_CRADLE))], sw))


def line_toe(sx, dist, foot):
    """The toe tip on the ground of a boot whose ankle is `dist` out along the leg's line, at [yaw, tilt]."""
    d, wf = leg_dir(sx), boot_rot(-sx * foot[0], foot[1])
    toe = _mv(wf, BOOT_TOE)
    return [sx * G.HIP_X + d[0] * dist + toe[0], 0.0, d[2] * dist + toe[2]]


def line_dist(sx, toe, foot):
    ankle = _sub(toe, _mv(boot_rot(-sx * foot[0], foot[1]), BOOT_TOE))
    d = leg_dir(sx)
    return (ankle[0] - sx * G.HIP_X) * d[0] + ankle[2] * d[2]


MID_FOOT = [-SG_AZ / 2, 0.0, 0.0]
AIR_FOOT = [-SG_AZ / 4, 15.0, 0.0]     # a boot in the air hangs toe down


def _leg_at(bones, sx, t, dist, foot):
    """The spade teeth's lowest edge and how far short the leg falls, a boot `dist` out along the line at tick t."""
    s = 'l' if sx < 0 else 'r'
    ankle = _sub(line_toe(sx, dist, foot), _mv(boot_rot(-sx * foot[0], foot[1]), BOOT_TOE))
    hip = Frames(bones, t).hips(RIG['leg_' + s][0])
    _, w2, kn, _, short = two_bone(hip, ankle, LEG_UPPER, LEG_LOWER, line_hint(sx), True)
    return spade_low(kn, w2)[1], short


def skid(bones, sx, t0, t1, foot0, foot1, toe0, toe1, clear=0.5, sweep='easeInOutSine'):
    """The planted boot pushed along the leg's line (the outrigger run out, or drawn in), a key every tick: an eased
    sweep from toe0 to toe1, held out far enough that the spade teeth stay `clear` of the ground and never past the
    leg's reach. Only the last tick may put the teeth in."""
    a, b = line_dist(sx, toe0, foot0), line_dist(sx, toe1, foot1)
    toes, feet = [], []
    for t in range(t0, t1 + 1):
        f = ease(sweep, None, (t - t0) / float(t1 - t0))
        foot = [foot0[i] + (foot1[i] - foot0[i]) * f for i in range(3)]
        dist = a + (b - a) * f
        if t < t1:
            lo, hi = min(a, b), max(a, b) + 6.0
            if _leg_at(bones, sx, t, hi, foot)[1] > 0.05:      # past reach: the longest the leg allows
                top, bot = hi, lo
                for _ in range(40):
                    mid = (top + bot) / 2
                    if _leg_at(bones, sx, t, mid, foot)[1] > 0.05:
                        top = mid
                    else:
                        bot = mid
                hi = bot
            if _leg_at(bones, sx, t, dist, foot)[0] < clear:   # out until the teeth clear the ground
                top, bot = hi, dist
                for _ in range(40):
                    mid = (top + bot) / 2
                    if _leg_at(bones, sx, t, mid, foot)[0] < clear:
                        bot = mid
                    else:
                        top = mid
                dist = top
            dist = min(dist, hi)
        toes.append(k(t, line_toe(sx, dist, foot)))
        feet.append(k(t, foot))
    return toes, feet


def step(sx, t0, toe0, toe1, foot0, foot1, lift=8.0):
    """A boot lifted at t0, through the air (past the halfway point toward the back, so the knee folds less), set
    down at t0 + 8."""
    back = (toe0, toe1) if toe1[2] > toe0[2] else (toe1, toe0)
    air = _add(_add(_scl(back[0], 0.2), _scl(back[1], 0.8)), [0.0, lift, 0.0])
    return ([k(t0, toe0), k(t0 + 4, air, 'easeOutCubic'), k(t0 + 8, toe1, 'easeInQuad')],
            [k(t0, foot0), k(t0 + 4, AIR_FOOT, 'easeOutCubic'), k(t0 + 8, foot1, 'easeInQuad')])


def siege_in():
    """Drive (40 ticks, 2 s, the server holds it still from tick 0). It surveys the site (the deck yaws 12 degrees each
    way), steps each boot back along its leg's line (left lands 13, right 22), then lowers the deck while both boots
    are run out behind it as outriggers, the spade teeth kept off the ground; the deck drops the last few pixels onto
    its knees and the teeth bite (siege:anchor on 31), overshooting 1.5 px and settling. The arms run down their rails,
    swing out and plant the fists on the ground (32), the back vents blast open, the cradles stand up on the tower
    tops: sieged on 38."""
    D = SG_DROP
    hips = [k(0, P()), k(3, P(y=1.5), 'easeOutQuad'), k(8, P(), 'easeInOutSine'), k(11, P(x=2, y=-4), 'easeInOutSine'),
            k(13, P(y=-6), 'easeInQuad'), k(15, P(y=-4.5), 'easeOutQuad'), k(19, P(x=-2, y=-4.5), 'easeInOutSine'),
            k(22, P(y=-6), 'easeInQuad'), k(24, P(y=-5), 'easeOutQuad'), k(28, P(y=-14), 'easeInOutSine'),
            k(30, P(y=-21), 'easeInQuad'), k(31, P(y=-D - 1.5), 'easeInQuad'), k(33, P(y=-D + 0.6), 'easeOutQuad'),
            k(36, P(y=-D - 0.4), 'easeInOutSine'), k(39, P(y=-D), 'easeInOutSine'), k(40, P(y=-D))]
    hb = {'hips': {'position': hips,
                   'rotation': [k(0, R()), k(8, R(z=-2.5), 'easeInOutSine'), k(13, R(z=1.5), 'easeInOutSine'),
                                k(18, R(z=2.5), 'easeInOutSine'), k(22, R(z=-1), 'easeInOutSine'),
                                k(26, R(), 'easeInOutSine'), k(40, R())]}}
    legs = {}
    for s, sx, t0 in (('l', -1.0, 5), ('r', 1.0, 14)):
        held_toe, held_foot = HELD[sx][0], HELD[sx][1]
        st, sf = step(sx, t0, REST_TOE[sx], MID_TOE[sx], R(), MID_FOOT)
        kt, kf = skid(hb, sx, 23, 31, MID_FOOT, held_foot, MID_TOE[sx], held_toe)
        legs[s] = {'toe': [k(0, REST_TOE[sx])] + st + kt + [k(40, held_toe)],
                   'foot': [k(0, R())] + sf + kf + [k(40, held_foot)],
                   'knee': [k(0, KNEE_HINT), k(t0, KNEE_HINT), k(t0 + 8, line_hint(sx), 'easeInOutSine'),
                            k(40, line_hint(sx))]}
    arms = {}
    for s, sx in (('l', -1.0), ('r', 1.0)):
        wind = [sx * 40.0, 44.0, -12.0]
        clear = _add(rest_fist(sx), [sx * 2.5, 0.0, 0.0])      # out from the hip drums while the deck yaws
        arms[s] = {'fist': [k(0, rest_fist(sx)), k(2, clear, 'easeInOutSine'), k(9, clear), k(12, rest_fist(sx), 'easeInOutSine'),
                            k(24, rest_fist(sx)), k(28, wind, 'easeOutCubic'),
                            k(32, FIST_HELD[sx], 'easeInQuad'), k(40, FIST_HELD[sx])],
                   'plant': [k(0, [0, 0, 0]), k(28, [0, 0, 0]), k(32, [1, 1, 1], 'easeInQuad'), k(40, [1, 1, 1])],
                   'elbow': [k(0, [0, 0, 1]), k(24, [0, 0, 1]), k(29, ELBOW_HELD[sx], 'easeInOutSine'),
                             k(40, ELBOW_HELD[sx])],
                   'slide': [k(0, P()), k(25, P()), k(33, P(y=-SG_SLIDE), 'easeInOutSine'), k(40, P(y=-SG_SLIDE))],
                   'level': [k(0, [0, 0, 0]), k(28, [0, 0, 0]), k(32, [SG_LEVEL] * 3, 'easeInOutSine'), k(40, [SG_LEVEL] * 3)]}
    bones = dict(hb)
    bones.update({
        'torso': {'rotation': [k(0, R()), k(3, R(x=-2, y=12), 'easeInOutSine'), k(7, R(x=-2, y=-12), 'easeInOutSine'),
                               k(10, R(), 'easeInOutSine'), k(24, R()), k(31, R(x=2), 'easeInQuad'),
                               k(34, R(x=-0.8), 'easeOutQuad'), k(37, R(), 'easeInOutSine')]},
        'sensor': {'rotation': [k(0, R()), k(3, R(x=4, y=20), 'easeInOutSine'), k(7, R(x=4, y=-20), 'easeInOutSine'),
                                k(10, R(x=4), 'easeInOutSine'), k(29, R(x=4)), k(32, R(x=-4), 'easeOutQuad'),
                                k(36, R(x=-2), 'easeInOutSine')]},
        'vent_l': {'rotation': [k(0, R()), k(32, R()), k(33, R(x=2), 'easeOutQuad'), k(35, R(x=-80), 'easeInExpo'),
                                k(37, R(x=-74), 'easeInOutSine')]},
        'vent_r': {'rotation': [k(0, R()), k(33, R()), k(34, R(x=2), 'easeOutQuad'), k(36, R(x=-80), 'easeInExpo'),
                                k(38, R(x=-74), 'easeInOutSine')]},
        'clamp_l': {'rotation': [k(0, R()), k(31, R()), k(32, R(z=1.5), 'easeOutQuad'), k(34, R(), 'easeInQuad')]},
        'clamp_r': {'rotation': [k(0, R()), k(31, R()), k(32, R(z=-1.5), 'easeOutQuad'), k(34, R(), 'easeInQuad')]},
    })
    cr = cradle_keys([k(0, R()), k(31, R()), k(32, R(x=-1.5), 'easeOutQuad'), k(36, R(x=SG_CRADLE + 6), 'easeInOutCubic'),
                      k(38, R(x=SG_CRADLE - 2), 'easeInOutSine'), k(40, R(x=SG_CRADLE), 'easeInOutSine')])
    clip = {'length': 40, 'loop': 'hold', 'native': True, 'bones': bones,
            'effects': [(0, 'timeline', 'siege:begin', None), (2, 'sound', snd('planning_chime', 0.7, 0.8), None),
                        (13, 'timeline', 'step:l_quake', None), (22, 'timeline', 'step:r_quake', None),
                        (24, 'sound', snd('hydraulic_sink', 0.8, 0.7), None), (31, 'timeline', 'siege:anchor', None),
                        (35, 'particle', 'cloud', 'fx_vent_l'), (36, 'particle', 'cloud', 'fx_vent_r'),
                        (37, 'sound', snd('hydraulic_hiss', 0.6, 0.8), None), (38, 'timeline', 'siege:locked', None)]}
    return solve_siege(clip, legs, arms, cr)


def siege_out():
    """Drive again (40 ticks, 2 s, vulnerable): the vents shut with a clank, the cradles fold down, the fists come up
    off the ground and the arms run back up their rails. It heaves up in two goes while the boots are drawn in along
    their lines (the spades leave the ground on 12, siege:unanchor), then steps the right boot back under it (lands 30)
    and the left (lands 39), and stands (siege:done on 40)."""
    D = SG_DROP
    hips = [k(0, P(y=-D)), k(4, P(y=-D)), k(6, P(y=-D - 1.0), 'easeOutQuad'), k(12, P(y=-21), 'easeInCubic'),
            k(15, P(y=-20), 'easeOutQuad'), k(22, P(y=-6), 'easeInOutCubic'), k(24, P(y=-5), 'easeOutQuad'),
            k(27, P(x=-2, y=-4.5), 'easeInOutSine'), k(30, P(y=-6), 'easeInQuad'), k(32, P(y=-4.5), 'easeOutQuad'),
            k(36, P(x=2, y=-3.5), 'easeInOutSine'), k(39, P(y=-2.0), 'easeInQuad'), k(40, P(y=-1.6), 'easeOutQuad')]
    hb = {'hips': {'position': hips,
                   'rotation': [k(0, R()), k(22, R()), k(26, R(z=1.5), 'easeInOutSine'), k(32, R(z=-1.5), 'easeInOutSine'),
                                k(37, R(), 'easeInOutSine'), k(40, R())]}}
    legs = {}
    for s, sx, t0 in (('r', 1.0, 22), ('l', -1.0, 31)):
        held_toe, held_foot = HELD[sx][0], HELD[sx][1]
        kt, kf = skid(hb, sx, 13, 21, held_foot, MID_FOOT, held_toe, MID_TOE[sx])
        st, sf = step(sx, t0, MID_TOE[sx], REST_TOE[sx], MID_FOOT, R())
        legs[s] = {'toe': [k(0, held_toe), k(12, held_toe)] + kt + st + [k(40, REST_TOE[sx])],
                   'foot': [k(0, held_foot), k(12, held_foot)] + kf + sf + [k(40, R())],
                   'knee': [k(0, line_hint(sx)), k(t0, line_hint(sx)), k(t0 + 8, KNEE_HINT, 'easeInOutSine'),
                            k(40, KNEE_HINT)]}
    arms = {}
    for s, sx in (('l', -1.0), ('r', 1.0)):
        arms[s] = {'fist': [k(0, FIST_HELD[sx]), k(6, FIST_HELD[sx]), k(14, rest_fist(sx), 'easeInOutSine'),
                            k(40, rest_fist(sx))],
                   'plant': [k(0, [1, 1, 1]), k(6, [1, 1, 1]), k(12, [0, 0, 0], 'easeInOutSine'), k(40, [0, 0, 0])],
                   'elbow': [k(0, ELBOW_HELD[sx]), k(8, ELBOW_HELD[sx]), k(14, [0, 0, 1], 'easeInOutSine'),
                             k(40, [0, 0, 1])],
                   'slide': [k(0, P(y=-SG_SLIDE)), k(5, P(y=-SG_SLIDE)), k(15, P(), 'easeInOutCubic'), k(40, P())],
                   'level': [k(0, [SG_LEVEL] * 3), k(6, [SG_LEVEL] * 3), k(11, [0, 0, 0], 'easeInOutSine'), k(40, [0, 0, 0])]}
    bones = dict(hb)
    bones.update({
        'torso': {'rotation': [k(0, R()), k(6, R(x=1), 'easeOutQuad'), k(12, R(x=-2), 'easeOutCubic'),
                               k(22, R(x=-1), 'easeInOutSine'), k(30, R(), 'easeInOutSine')]},
        'sensor': {'rotation': [k(0, R(x=-2)), k(10, R(x=-8), 'easeInOutSine'), k(24, R(), 'easeInOutSine')]},
        'vent_l': {'rotation': [k(0, R(x=-74)), k(2, R(x=-78), 'easeOutQuad'), k(5, R(), 'easeInQuad'),
                                k(6, R(x=2), 'easeOutQuad'), k(7, R(), 'easeInQuad')]},
        'vent_r': {'rotation': [k(0, R(x=-74)), k(3, R(x=-78), 'easeOutQuad'), k(6, R(), 'easeInQuad'),
                                k(7, R(x=2), 'easeOutQuad'), k(8, R(), 'easeInQuad')]},
    })
    cr = cradle_keys([k(0, R(x=SG_CRADLE)), k(2, R(x=SG_CRADLE)), k(4, R(x=SG_CRADLE + 4), 'easeOutQuad'),
                      k(10, R(x=-1.5), 'easeInOutCubic'), k(12, R(), 'easeInOutSine'), k(40, R())])
    clip = {'length': 40, 'loop': 'hold', 'native': True, 'bones': bones,
            'effects': [(0, 'timeline', 'siege:release', None), (5, 'sound', snd('vent_clank', 0.6, 0.8), None),
                        (7, 'sound', snd('hydraulic_lift', 0.8, 0.7), None), (12, 'timeline', 'siege:unanchor', None),
                        (30, 'timeline', 'step:r_quake', None), (39, 'timeline', 'step:l_quake', None),
                        (40, 'timeline', 'siege:done', None)]}
    return solve_siege(clip, legs, arms, cr)


def siege_refused():
    """Drive refused (no Anchor Clamps fitted, moving, airborne, or the ground will not take it; 26 ticks): it
    surveys, lifts the left boot to step out, a buzzer, it puts the boot back where it was with a stamp, and shrugs
    at the planning office: shoulders up 3.5 px, palms up, the cab lamp tilted, the hardpoints dipping."""
    feet = {'l': [k(0, REST), k(4, REST), k(8, (3.0, 15.0, 6.0), 'easeOutCubic'), k(10, (3.0, 15.0, 6.0)),
                  k(12, REST, 'easeInQuad'), k(26, REST)],
            'r': [k(0, REST)]}
    arm = [k(0, R()), k(12, R()), k(15, R(x=15, z=-6), 'easeInOutCubic'), k(21, R(x=15, z=-6)), k(26, R(), 'easeInOutSine')]
    fore = [k(0, R()), k(12, R()), k(15, R(x=45), 'easeInOutCubic'), k(21, R(x=45)), k(26, R(), 'easeInOutSine')]
    hand = [k(0, R()), k(13, R()), k(16, R(z=30), 'easeInOutSine'), k(21, R(z=30)), k(25, R(), 'easeInOutSine')]
    grip = [k(0, R()), k(14, R()), k(16, R(x=20), 'easeInOutSine'), k(21, R(x=20)), k(26, R(), 'easeInOutSine')]
    bones = dict(BA.both_arms(arm, fore, hand, grip))
    bones['arm_l']['position'] = [k(0, P()), k(13, P()), k(16, P(y=3.5), 'easeOutCubic'), k(21, P(y=3.5)),
                                  k(25, P(), 'easeInOutSine')]
    bones['arm_r']['position'] = [k(0, P()), k(14, P()), k(17, P(y=3.5), 'easeOutCubic'), k(21, P(y=3.5)),
                                  k(25, P(), 'easeInOutSine')]
    bones.update({
        'hips': {'position': [k(0, P()), k(3, P(y=1.5), 'easeOutQuad'), k(8, P(y=0.5), 'easeInOutSine'),
                              k(12, P(y=-2), 'easeInQuad'), k(14, P(y=-1.2), 'easeOutQuad'), k(16, P(y=0.8), 'easeOutQuad'),
                              k(20, P(), 'easeInOutSine')],
                 'rotation': [k(0, R()), k(6, R(z=-2), 'easeInOutSine'), k(12, R(z=0.6), 'easeInQuad'),
                              k(16, R(), 'easeInOutSine')]},
        'torso': {'rotation': [k(0, R()), k(3, R(x=-3), 'easeOutQuad'), k(12, R(x=1), 'easeInQuad'), k(15, R(x=2), 'easeOutQuad'),
                               k(21, R(x=2)), k(26, R(), 'easeInOutSine')]},
        'sensor': {'rotation': [k(0, R()), k(3, R(x=8, y=20), 'easeInOutSine'), k(7, R(x=8, y=-15), 'easeInOutSine'),
                                k(10, R(), 'easeInOutSine'), k(13, R()), k(16, R(z=14), 'easeInOutSine'), k(21, R(z=14)),
                                k(26, R(), 'easeInOutSine')]},
    })
    bones.update(hardpoints([k(0, R()), k(13, R()), k(16, R(x=-10), 'easeInOutSine'), k(21, R(x=-10)),
                             k(26, R(), 'easeInOutSine')], lag=1))
    return {'length': 26, 'loop': 'once', 'native': True, 'feet': feet, 'bones': bones,
            'effects': [(0, 'timeline', 'siege:refused', None), (9, 'sound', snd('permit_denied', 0.7, 0.9), None),
                        (12, 'timeline', 'stomp:l', None), (16, 'sound', snd('shrug_hiss', 0.5, 0.9), None)]}


# ==================================================================================================================
# Build: the Gantry commissions a building
# ==================================================================================================================
BUILD_SLOTS = [('legs', 124, ['socket_thigh_l', 'socket_thigh_r', 'socket_shin_l', 'socket_shin_r']),
               ('back', 132, ['socket_back']), ('back_1', 138, ['socket_back_1']),
               ('core', 144, ['socket_core']), ('core_1', 150, ['socket_core_1']),
               ('torso_0', 156, ['socket_torso_0']), ('torso_1', 162, ['socket_torso_1']),
               ('torso_2', 168, ['socket_torso_2']), ('torso_3', 174, ['socket_torso_3']),
               ('torso_4', 180, ['socket_torso_4']),
               ('arm_l', 186, ['socket_arm_l']), ('arm_r', 192, ['socket_arm_r']),
               ('arm_2', 198, ['socket_arm_2']), ('arm_3', 204, ['socket_arm_3']),
               ('sensor', 210, ['socket_sensor']), ('sensor_1', 216, ['socket_sensor_1'])]


def outward(bone):
    """A socket's -Z in the model frame (its parent chain's rest rotations; hardpoints deployed)."""
    chain = {n: (p, r) for n, _, p, r in G.bones()}
    v = [0.0, 0.0, -1.0]
    b = bone
    while b:
        r = chain[b][1] or [0, 0, 0]
        x, y, z = [math.radians(a) for a in r]
        vx, vy, vz = v
        vy, vz = vy * math.cos(x) - vz * math.sin(x), vy * math.sin(x) + vz * math.cos(x)
        vx, vz = vx * math.cos(y) + vz * math.sin(y), -vx * math.sin(y) + vz * math.cos(y)
        vx, vy = vx * math.cos(z) - vy * math.sin(z), vx * math.sin(z) + vy * math.cos(z)
        v = [vx, vy, vz]
        b = chain[b][0]
    return [round(c, 6) for c in v]


def build():
    """The Gantry's commissioning (320 ticks): delivered kneeling in its Siege crouch, arms folded, weapons folded
    back, lamps dark. It stands in two heaves with a groan between (40-80), brings its boots in (82-96), unfolds the
    shoulder weapons one then the other (100-118); sixteen modules arrive by slot (124-216, each from 8 px out with a
    1.4 px bounce); inspection: lamps, a vent test, a look at the empty seat; then the ribbon: the left fist rises and
    the grip snips twice (258, 262); clamps close over the empty collar, weapons fold, it settles parked."""
    feet = {s: [k(0, SIEGED), k(40, SIEGED)] + roll_keys(40, 74, 24, 0, 2)[1:] +
            [k(t0, STEP_OUT), k(t0 + 4, LIFT, 'easeOutCubic'), k(t0 + 8, REST, 'easeInQuad'), k(320, REST)]
            for s, t0 in (('r', 82), ('l', 89))}
    splay = {'r': [k(0, [SPLAY, 0, 0]), k(83, [SPLAY, 0, 0]), k(89, [0, 0, 0], 'easeInOutSine'), k(320, [0, 0, 0])],
             'l': [k(0, [SPLAY, 0, 0]), k(90, [SPLAY, 0, 0]), k(96, [0, 0, 0], 'easeInOutSine'), k(320, [0, 0, 0])]}
    PH = BA.PARK_HIPS * BA.SC
    hips = [k(0, SP(SIEGE_HIPS)), k(40, SP(SIEGE_HIPS)), k(43, SP(SIEGE_HIPS - 0.8), 'easeOutQuad'),
            k(56, P(y=-7), 'easeInOutCubic'), k(66, P(y=-7.4), 'easeInOutSine'), k(76, P(y=0.8), 'easeInOutCubic'),
            k(79, P(y=-1.6), 'easeInQuad'), k(82, P(), 'easeInOutSine'), k(90, P(y=-1.2), 'easeInQuad'),
            k(93, P(), 'easeOutQuad'), k(97, P(y=-1.2), 'easeInQuad'), k(100, P(), 'easeOutQuad'),
            k(296, P()), k(312, P(y=PH), 'easeInQuad'), k(320, P(y=PH))]
    bones = {
        'hips': {'position': hips},
        'torso': {'rotation': [k(0, R(x=SIEGE_TORSO)), k(44, R(x=SIEGE_TORSO + 1)), k(56, R(x=-4), 'easeInOutCubic'),
                               k(66, R(x=-4)), k(78, R(x=1), 'easeInOutCubic'), k(82, R(), 'easeInOutSine'),
                               k(298, R()), k(312, R(x=BA.PARK_TORSO), 'easeInOutSine')]},
        'sensor': {'rotation': [k(0, R(x=25)), k(70, R(x=25)), k(78, R(x=-2), 'easeOutCubic'), k(80, R(), 'easeInOutSine'),
                                k(226, R()), k(230, R(x=12), 'easeOutCubic'), k(234, R(y=25), 'easeInOutSine'),
                                k(238, R(y=-25), 'easeInOutSine'), k(242, R(), 'easeInOutSine'),
                                k(248, R(x=-25), 'easeInOutSine'), k(253, R(x=-25)), k(256, R(), 'easeInOutSine'),
                                k(300, R()), k(312, R(x=BA.PARK_SENSOR), 'easeInOutSine')]},
        'vent_l': {'rotation': [k(0, R()), k(242, R()), k(243, R(x=4), 'easeOutQuad'), k(245, R(x=-60), 'easeInExpo'),
                                k(250, R(), 'easeInElastic', [4])]},
        'vent_r': {'rotation': [k(0, R()), k(244, R()), k(245, R(x=4), 'easeOutQuad'), k(247, R(x=-60), 'easeInExpo'),
                                k(252, R(), 'easeInElastic', [4])]},
    }
    # arms: folded across the chest while kneeling, dropped as it stands; the ribbon: the left fist up, two snips
    arm_l = [k(0, R(x=40, z=22)), k(60, R(x=40, z=22)), k(76, R(x=-3), 'easeInOutCubic'), k(80, R(), 'easeInOutSine'),
             k(252, R()), k(258, R(x=95, z=12), 'easeInOutCubic'), k(266, R(x=95, z=12)), k(274, R(), 'easeInOutCubic')]
    fore_l = [k(0, R(x=80)), k(62, R(x=80)), k(77, R(x=-3), 'easeInOutCubic'), k(81, R(), 'easeInOutSine'),
              k(252, R()), k(258, R(x=-40), 'easeInOutCubic'), k(266, R(x=-40)), k(274, R(), 'easeInOutCubic'),
              k(300, R()), k(312, R(x=BA.PARK_FORE), 'easeInOutSine')]
    grip_l = [k(0, R()), k(258, R()), k(259, R(x=30), 'easeOutQuad'), k(260, R(x=-40), 'easeInQuad'),
              k(262, R(x=30), 'easeOutQuad'), k(263, R(x=-40), 'easeInQuad'), k(266, R(), 'easeInOutSine')]
    arms = BA.both_arms([k(0, R(x=40, z=22)), k(60, R(x=40, z=22)), k(76, R(x=-3), 'easeInOutCubic'), k(80, R(), 'easeInOutSine')],
                        [k(0, R(x=80)), k(62, R(x=80)), k(77, R(x=-3), 'easeInOutCubic'), k(81, R(), 'easeInOutSine'),
                         k(300, R()), k(312, R(x=BA.PARK_FORE), 'easeInOutSine')], lag=1)
    bones.update(arms)
    bones['arm_l'] = {'rotation': arm_l}
    bones['forearm_l'] = {'rotation': fore_l}
    bones['grip_l'] = {'rotation': grip_l}
    bones.update(BA.clamps([k(0, [40]), k(280, [40]), k(284, [0], 'easeInQuad'), k(285, [-2], 'easeOutQuad'),
                            k(287, [0], 'easeInQuad')]))
    bones.update(hardpoints([k(0, R(x=STOW)), k(100, R(x=STOW)), k(101, R(x=STOW + 4), 'easeOutQuad'),
                             k(110, R(x=-8), 'easeInOutCubic'), k(112, R(x=3), 'easeInOutSine'), k(114, R(), 'easeInOutSine'),
                             k(288, R()), k(300, R(x=STOW), 'easeInOutCubic')], lag=5))
    bones.update(BA.lamps([(0, 0), (226, 1), (240, 0), (244, 1), (250, 0)], [(0, 0), (230, 1), (240, 0)]))
    effects = [(43, 'sound', snd('hydraulic_stand', 0.8, 0.7), None), (60, 'sound', snd('hydraulic_groan', 0.6, 0.6), None),
               (66, 'sound', snd('hydraulic_stand', 0.8, 0.75), None), (79, 'timeline', 'build:stand', None),
               (90, 'timeline', 'step:r_quake', None), (97, 'timeline', 'step:l_quake', None),
               (101, 'sound', snd('hydraulic_hiss', 0.6, 0.8), None), (110, 'sound', snd('latch_clunk', 0.6, 0.8), None),
               (115, 'sound', snd('latch_clunk', 0.6, 0.75), None),
               (230, 'sound', snd('scan_chirp', 0.4, 0.9), None), (245, 'particle', 'cloud', 'fx_vent_l'),
               (247, 'particle', 'cloud', 'fx_vent_r'), (260, 'sound', snd('snip', 0.6, 1.0), None),
               (263, 'sound', snd('snip', 0.6, 0.95), None), (264, 'timeline', 'build:ribbon', None),
               (284, 'sound', snd('clamp_close', 0.6, 0.9), None), (289, 'sound', snd('hydraulic_hiss', 0.5, 0.7), None),
               (312, 'sound', snd('sigh', 0.6, 0.7), None)]
    for slot, T0, socks in BUILD_SLOTS:
        for sb in socks:
            o = outward(sb)
            ch = bones.setdefault(sb, {})
            ch['scale'] = [k(0, S(0), interp='step'), k(T0, S(1), interp='step')]
            ch['position'] = [k(0, P(*[8 * c for c in o])), k(T0, P(*[8 * c for c in o])), k(T0 + 3, P(), 'easeInQuad'),
                              k(T0 + 4, P(*[1.4 * c for c in o]), 'easeOutQuad'), k(T0 + 5, P(), 'easeInQuad')]
        effects.append((T0 + 3, 'timeline', 'build:seat:%s' % slot, None))
    return {'length': 320, 'loop': 'hold', 'native': True, 'feet': feet, 'splay': splay, 'bones': bones,
            'effects': effects, 'pilot': False}


# ==================================================================================================================
def clips():
    out = []
    own = {'parked': parked, 'boot': boot, 'shutdown': shutdown, 'eject': eject, 'destroyed': destroyed,
           'idle_tic': idle_tic, 'build': build}
    for name, c in SHARED():
        out.append((name, own[name]() if name in own else c))
    sieges = [('siege_in', siege_in()), ('siege', siege()), ('siege_out', siege_out())]
    check_siege(dict(sieges))
    for _, c in sieges:
        c.pop('report', None)
    out += sieges + [('siege_refused', siege_refused())]
    return out


# What the game keys on (Siege.java, HopperAnimation.siege, the timeline handlers): lengths, loop modes, event ticks.
SIEGE_CONTRACT = {
    'siege_in': (40, 'hold', [(0, 'siege:begin'), (2, 'planning_chime'), (13, 'step:l_quake'), (22, 'step:r_quake'),
                              (24, 'hydraulic_sink'), (31, 'siege:anchor'), (35, 'fx_vent_l'), (36, 'fx_vent_r'),
                              (37, 'hydraulic_hiss'), (38, 'siege:locked')]),
    'siege': (60, 'loop', [(5, 'fx_vent_l'), (20, 'siege_hum'), (35, 'fx_vent_r')]),
    'siege_out': (40, 'hold', [(0, 'siege:release'), (5, 'vent_clank'), (7, 'hydraulic_lift'), (12, 'siege:unanchor'),
                               (30, 'step:r_quake'), (39, 'step:l_quake'), (40, 'siege:done')]),
}


def check_siege(clips_):
    """Fails the script when a Siege clip breaks what the game keys on or what the pose promises: lengths, loop modes
    and keyed events; the spade teeth out of the ground until siege:anchor (31) and in it from then, out of it again
    from siege:unanchor (12); boots, fists and spades that stay put while they are planted; a held loop that ends
    where it starts."""
    bad = []
    for name, (n, loop, events) in SIEGE_CONTRACT.items():
        c = clips_[name]
        got = sorted((e[0], e[2] if e[1] == 'timeline' else (e[3] if e[1] == 'particle' else e[2].split('|')[0].split('.')[-1]))
                     for e in c['effects'])
        if (c['length'], c['loop'], got) != (n, loop, events):
            bad.append('%s: length, loop or events changed: %s' % (name, (c['length'], c['loop'], got)))
    for name, c in clips_.items():
        r = c['report']
        for s in 'lr':
            for t, spade, fx, short, _ in r['spade_' + s]:
                y = spade[1]
                if name == 'siege_in' and ((t < 31 and y < -0.05) or (t >= 31 and y > -SG_BITE + 0.6)):
                    bad.append('%s %d: spade %s at %.2f' % (name, t, s, y))
                if name == 'siege' and abs(y + SG_BITE) > 0.05:
                    bad.append('%s %d: spade %s at %.2f' % (name, t, s, y))
                if name == 'siege_out' and ((t < 12 and y > -0.05) or (t >= 12 and y < 0.05)):
                    bad.append('%s %d: spade %s at %.2f' % (name, t, s, y))
            planted = {'siege_in': range(31, 41), 'siege': range(61), 'siege_out': range(0, 12)}[name]
            toes = [toe for t, toe, heel in r['boot_' + s] if t in planted]
            fists = [(low, w) for t, short, yaw, low, w in r['wrist_' + s]
                     if t in {'siege_in': range(32, 41), 'siege': range(61), 'siege_out': range(0, 7)}[name]]
            if any(abs(a - b) > 0.05 for toe in toes for a, b in zip(toe, toes[0])) or any(abs(t_[1]) > 0.05 for t_ in toes):
                bad.append('%s: boot %s moves or lifts while planted' % (name, s))
            if any(abs(lw[0]) > 0.1 for lw in fists) or                     any(abs(lw[1][i] - fists[0][1][i]) > 0.1 for lw in fists for i in (0, 2)):
                bad.append('%s: fist %s moves or lifts while planted' % (name, s))
            if any(short > 0.25 for t, _, _, short, _ in r['spade_' + s] if t in planted):
                bad.append('%s: leg %s cannot reach its boot' % (name, s))
    for bone, chans in clips_['siege']['bones'].items():
        for ch, keys in chans.items():
            ks = sorted(keys, key=lambda x: x[0])
            if ks[-1][0] == 60 and any(abs(a - b) > 1e-6 for a, b in zip(ks[0][1], ks[-1][1])):
                bad.append('siege: %s %s does not loop' % (bone, ch))
    if bad:
        sys.exit('Siege check failed:\n  ' + '\n  '.join(bad[:20]))
    sys.stderr.write('siege ok: deck down %.1f px, spades %.1f px in, boots and fists planted, loop closed\n'
                     % (SG_DROP, SG_BITE))


SHARED = BA.clips
BA.clips = clips

if __name__ == '__main__':
    BA.main()

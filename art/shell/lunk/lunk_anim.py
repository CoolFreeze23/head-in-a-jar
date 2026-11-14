"""Lunk (a Utility Shell): every clip, keyed in ticks, written into lunk.bbmodel.

The heavy-biped clip script (art/mecha/bulwark/bulwark_anim.py) run for the Lunk's body (use_frame: its rig numbers and
speed from lunk_geo.ANIM; legs re-solved every tick for its own stubby legs). A shell is a body, not a machine: it has
no boot, no modules, no build and no Heavy Lifting. Its clips are its own (native), except hurt and destroyed, which
are the Bulwark's scaled 0.87 (destroyed with the drawers springing open):

  parked      empty, slumped like a gorilla at rest, knuckles on the floor, a drawer "breathing" every ~150 ticks
  board       the jar gets on: it bends, cups its fists, lifts the jar to the lid, the bulldog clips bite
  alight      the jar gets off: the clips let go, it lifts the jar down in front of it, and pats the lid once
  idle        piloted: standing, the cabinet breathing 0.5 px, the webcam scanning
  idle_tic    the top drawer slides open on its own; it shoves it shut with a fist
  walk        the heavy knuckle-walk at its ridden speed (0.194 blocks a tick): fists planted, limbs in order
  walk_fast   the knuckle-gallop at the unhalved speed (0.389 blocks a tick), kept in case a sprint is wanted
  toss        the ability: crouch, scoop, heave overhead, a strain hold, the throw (toss:release on 19)
  toss_refused  flat battery or nothing in reach: it starts to scoop and the arms drop, a servo whine
  hurt        the Bulwark's
  flat        the jar goes flat: it winds down into the parked slump, the lights go out
  destroyed   the Bulwark's collapse, the drawers springing open

    python art/shell/lunk/lunk_anim.py                 # write the clips into lunk.bbmodel
    python art/shell/lunk/lunk_anim.py --preview OUT   # review copy
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MECHA = os.path.join(os.path.dirname(os.path.dirname(HERE)), 'mecha')
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(MECHA, 'bulwark'))
sys.path.insert(0, os.path.join(MECHA, 'hopper'))
import lunk_geo as G  # noqa: E402
import bulwark_anim as BA  # noqa: E402
from hopper_anim import k, R, P, S, snd, const  # noqa: E402

BA.use_frame(G)
J = G.SEAT_Y
REST = BA.REST_FOOT_T

# poses
SLUMP = dict(hips=-3.0, torso=-16.0, arm=30.0, fore=4.0, cam=30.0)     # empty: knuckles on the floor
GROUND_JAR = (0.0, -J, -15.0)        # jar_anchor offset that puts the jar on the floor in front of it
HELD_JAR = (0.0, -J + 14.0, -16.0)   # jar_anchor offset with the jar cupped at waist height in front
SCOOP = dict(arm=62.0, fore=-8.0, inward=26.0)
OVER = dict(arm=150.0, fore=-14.0, inward=8.0)


def lamps(core, tail):
    return {'lamp_core': {'scale': BA.lamp(core)}, 'lamp_tail': {'scale': BA.lamp(tail)}}


def clips_bite(keys):
    """The bulldog clips: o = degrees open (each jaw swings out from the jar)."""
    return {'clamp_l': {'rotation': [(t, [0, 0, o[0]], e, a, i) for (t, o, e, a, i) in keys]},
            'clamp_r': {'rotation': [(t, [0, 0, -o[0]], e, a, i) for (t, o, e, a, i) in keys]}}


def jar_at(target, hips_y, torso_x):
    """The jar_anchor offset (torso space) that puts the jar's base at a world point, given the hips' drop and the
    torso's pitch at that tick: the jar is held by world position, whatever the body does."""
    tp = (0.0, G.HIP_Y + hips_y, 0.0)
    a = math.radians(-torso_x)
    dy, dz = target[1] - tp[1], target[2] - tp[2]
    ly, lz = dy * math.cos(a) - dz * math.sin(a), dy * math.sin(a) + dz * math.cos(a)
    return P(target[0], round(ly + G.HIP_Y - J, 3), round(lz, 3))


FLOOR = (0.0, 0.0, -15.0)                  # where the jar stands on the floor in front of it
CUPPED = (0.0, 14.0, -16.0)                # held in both fists at waist height


def slumped(t=0):
    return {'hips': [k(t, P(y=SLUMP['hips']))], 'torso': [k(t, R(x=SLUMP['torso']))]}


# ==================================================================================================================
def parked():
    """Empty: slumped like a gorilla at rest, knuckles on the floor, the webcam drooping, lights out. Every 160 ticks
    the top drawer slides open a pixel and shuts (it is breathing)."""
    bones = dict(BA.both_arms([k(0, R(x=SLUMP['arm']))], [k(0, R(x=SLUMP['fore']))], None, [k(0, R(x=20))]))
    bones.update({
        'hips': {'position': [k(0, P(y=SLUMP['hips'])), k(80, P(y=SLUMP['hips'] - 0.4), 'easeInOutSine'),
                              k(160, P(y=SLUMP['hips']), 'easeInOutSine')]},
        'torso': {'rotation': const(R(x=SLUMP['torso']))},
        'sensor': {'rotation': const(R(x=SLUMP['cam']))},
        'drawer': {'position': [k(0, P()), k(140, P()), k(145, P(z=-1), 'easeInOutSine'), k(150, P(), 'easeInOutSine'),
                                k(160, P())]},
        'clamp_l': {'rotation': const(R(z=25))}, 'clamp_r': {'rotation': const(R(z=-25))},
    })
    bones.update(lamps([(0, 0)], [(0, 0)]))
    return {'length': 160, 'loop': 'loop', 'native': True, 'feet': BA.planted(), 'bones': bones, 'pilot': False,
            'effects': [(145, 'sound', snd('drawer_slide', 0.15, 1.2), None)]}


def board():
    """The jar gets on (26 ticks, controller main; control is live after 22). From the slump it bends, its fists come
    together round the jar on the floor in front (contact 6), it straightens and lifts the jar up to the lid (8-16),
    sets it in the ring (18), the bulldog clips bite (20), the webcam looks at the jar, the lights come on."""
    arm = [k(0, R(x=SLUMP['arm'])), k(6, R(x=SCOOP['arm'], z=SCOOP['inward']), 'easeInOutCubic'),
           k(9, R(x=SCOOP['arm'] + 2, z=SCOOP['inward'])), k(15, R(x=150, z=14), 'easeInOutCubic'),
           k(18, R(x=158, z=16), 'easeInOutSine'), k(20, R(x=150, z=14), 'easeOutQuad'), k(23, R(x=50, z=6), 'easeInOutCubic'),
           k(26, R(), 'easeInOutSine')]
    fore = [k(0, R(x=SLUMP['fore'])), k(6, R(x=SCOOP['fore']), 'easeInOutCubic'), k(15, R(x=-40), 'easeInOutCubic'),
            k(18, R(x=-48), 'easeInOutSine'), k(26, R(), 'easeInOutCubic')]
    grip = [k(0, R(x=20)), k(5, R(x=25)), k(6, R(x=-30), 'easeInQuad'), k(18, R(x=-30)), k(19, R(x=20), 'easeOutQuad'),
            k(26, R(), 'easeInOutSine')]
    bones = dict(BA.both_arms(arm, fore, None, grip))
    bones.update({
        'jar_anchor': {'position': [k(0, jar_at(FLOOR, SLUMP['hips'], SLUMP['torso'])), k(3, jar_at(FLOOR, -3.8, -20)),
                                    k(6, jar_at(FLOOR, -4.5, -24)), k(10, jar_at(CUPPED, -1.5, -10), 'easeInOutCubic'),
                                    k(13, jar_at((0, J + 3, -12), 0.5, 2), 'easeInOutCubic'),
                                    k(16, P(y=3, z=-3), 'easeInOutCubic'), k(18, P(), 'easeInQuad')],
                       'rotation': [k(0, R(x=-SLUMP['torso'])), k(3, R(x=20)), k(6, R(x=24)), k(10, R(x=10), 'easeInOutCubic'),
                                    k(16, R(), 'easeInOutCubic')]},
        'hips': {'position': [k(0, P(y=SLUMP['hips'])), k(6, P(y=-4.5), 'easeInOutCubic'), k(12, P(y=0.5), 'easeInOutCubic'),
                              k(16, P(), 'easeInOutSine'), k(19, P(y=-0.8), 'easeOutQuad'), k(22, P(), 'easeInOutSine')]},
        'torso': {'rotation': [k(0, R(x=SLUMP['torso'])), k(6, R(x=-24), 'easeInOutCubic'), k(14, R(x=2), 'easeInOutCubic'),
                               k(18, R(), 'easeInOutSine')]},
        'sensor': {'rotation': [k(0, R(x=SLUMP['cam'])), k(6, R(x=35), 'easeInOutSine'), k(16, R(x=-30, y=-25), 'easeInOutSine'),
                                k(20, R(x=-30, y=-25)), k(24, R(), 'easeInOutSine')]},
    })
    bones.update(clips_bite([k(0, [25]), k(17, [25]), k(20, [-3], 'easeInExpo'), k(22, [0], 'easeOutQuad')]))
    bones.update(lamps([(0, 0), (21, 1)], [(0, 0), (22, 1)]))
    return {'length': 26, 'loop': 'hold', 'native': True, 'feet': BA.planted(),
            'bones': bones,
            'effects': [(1, 'sound', snd('servo', 0.5, 0.9), None), (6, 'timeline', 'board:cup', None),
                        (12, 'sound', snd('hydraulic_lift', 0.4, 1.1), None), (18, 'timeline', 'board:seated', None),
                        (20, 'sound', snd('bulldog_clip', 0.7, 1.0), None), (22, 'timeline', 'board:done', None)]}


def alight():
    """The jar gets off (30 ticks, the jar leaves on 18): the clips open (2), it lifts the jar off the lid in both
    fists (4-8), lowers it gently to the floor in front (8-16), lets go (17), pats the lid once (20-23), and slumps."""
    arm = [k(0, R()), k(4, R(x=150, z=14), 'easeInOutCubic'), k(7, R(x=156, z=16), 'easeInOutSine'),
           k(16, R(x=SCOOP['arm'], z=SCOOP['inward']), 'easeInOutCubic'), k(18, R(x=SCOOP['arm'] - 6, z=8), 'easeOutQuad'),
           k(30, R(x=SLUMP['arm']), 'easeInOutCubic')]
    fore = [k(0, R()), k(4, R(x=-40), 'easeInOutCubic'), k(7, R(x=-48)), k(16, R(x=SCOOP['fore']), 'easeInOutCubic'),
            k(30, R(x=SLUMP['fore']), 'easeInOutCubic')]
    grip = [k(0, R()), k(3, R(x=20), 'easeOutQuad'), k(5, R(x=-30), 'easeInQuad'), k(16, R(x=-30)), k(17, R(x=25), 'easeOutQuad'),
            k(30, R(x=20), 'easeInOutSine')]
    bones = dict(BA.both_arms(arm, fore, None, grip))
    # the pat: the right arm alone comes down on the jar's lid once
    bones['arm_r']['rotation'] = [kk for kk in bones['arm_r']['rotation'] if kk[0] <= 18] + [
        k(20, R(x=78, z=-30), 'easeInOutCubic'), k(22, R(x=72, z=-30), 'easeInQuad'), k(23, R(x=76, z=-30), 'easeOutQuad'),
        k(30, R(x=SLUMP['arm']), 'easeInOutCubic')]
    bones['forearm_r']['rotation'] = [kk for kk in bones['forearm_r']['rotation'] if kk[0] <= 16] + [
        k(20, R(x=-30), 'easeInOutCubic'), k(23, R(x=-30)), k(30, R(x=SLUMP['fore']), 'easeInOutCubic')]
    bones.update({
        'jar_anchor': {'position': [k(0, P()), k(4, P(y=3, z=-3), 'easeInOutCubic'), k(10, jar_at(CUPPED, -2, -8), 'easeInOutCubic'),
                                    k(13, jar_at((0, 4, -15.5), -3.8, -19), 'easeInOutSine'),
                                    k(16, jar_at(FLOOR, -4.5, -24), 'easeInOutCubic')],
                       'rotation': [k(0, R()), k(4, R()), k(10, R(x=8), 'easeInOutCubic'), k(13, R(x=19), 'easeInOutSine'),
                                    k(16, R(x=24), 'easeInOutCubic')]},
        'hips': {'position': [k(0, P()), k(4, P(y=0.5), 'easeOutQuad'), k(16, P(y=-4.5), 'easeInOutCubic'),
                              k(24, P(y=-3.5), 'easeInOutSine'), k(30, P(y=SLUMP['hips']), 'easeInOutSine')]},
        'torso': {'rotation': [k(0, R()), k(6, R(x=2), 'easeOutQuad'), k(16, R(x=-24), 'easeInOutCubic'),
                               k(30, R(x=SLUMP['torso']), 'easeInOutSine')]},
        'sensor': {'rotation': [k(0, R()), k(10, R(x=35), 'easeInOutSine'), k(22, R(x=35, y=-10)), k(30, R(x=SLUMP['cam']), 'easeInOutSine')]},
    })
    bones.update(clips_bite([k(0, [0]), k(2, [25], 'easeOutCubic')]))
    bones.update(lamps([(0, 1), (24, 0)], [(0, 1), (24, 0)]))
    return {'length': 30, 'loop': 'hold', 'native': True, 'feet': BA.planted(), 'bones': bones, 'pilot_until': 16,
            'effects': [(2, 'sound', snd('bulldog_clip', 0.6, 1.1), None), (16, 'timeline', 'alight:down', None),
                        (17, 'timeline', 'alight:release', None), (22, 'sound', snd('pat', 0.5, 1.0), None),
                        (26, 'sound', snd('servo_down', 0.4, 0.9), None)]}


def idle():
    """Piloted, standing: the cabinet breathes 0.5 px over 40 ticks, the arms sway a hair, the webcam scans 15 degrees
    each way over the loop."""
    bones = {
        'hips': {'position': [k(0, P()), k(20, P(y=0.5), 'easeInOutSine'), k(40, P(), 'easeInOutSine'),
                              k(60, P(y=0.5), 'easeInOutSine'), k(80, P(), 'easeInOutSine'), k(100, P(y=0.5), 'easeInOutSine'),
                              k(120, P(), 'easeInOutSine')]},
        'sensor': {'rotation': [k(0, R()), k(30, R(y=15), 'easeInOutSine'), k(90, R(y=-15), 'easeInOutSine'),
                                k(120, R(), 'easeInOutSine')]},
        'arm_l': {'rotation': [k(0, R()), k(60, R(x=2), 'easeInOutSine'), k(120, R(), 'easeInOutSine')]},
        'arm_r': {'rotation': [k(0, R()), k(64, R(x=2), 'easeInOutSine'), k(120, R(), 'easeInOutSine')]},
    }
    return {'length': 120, 'loop': 'loop', 'native': True, 'feet': BA.planted(), 'bones': bones, 'effects': []}


def idle_tic():
    """Every 200-300 idle ticks: the top drawer slides open on its own (4-8); the webcam turns to look down at it; the
    right fist comes round and shoves it shut (14-16), the cabinet rocks; the webcam looks back up."""
    arm = [k(0, R()), k(10, R()), k(14, R(x=40, z=-34), 'easeInOutCubic'), k(16, R(x=46, z=-38), 'easeInExpo'),
           k(18, R(x=44, z=-36), 'easeOutQuad'), k(26, R(), 'easeInOutCubic')]
    fore = [k(0, R()), k(10, R()), k(14, R(x=-30), 'easeInOutCubic'), k(16, R(x=-20), 'easeInExpo'), k(26, R(), 'easeInOutCubic')]
    return {'length': 30, 'loop': 'once', 'native': True, 'feet': BA.planted(),
            'bones': {'drawer': {'position': [k(0, P()), k(4, P()), k(8, P(z=-5), 'easeOutCubic'), k(15, P(z=-5)),
                                              k(16, P(), 'easeInExpo'), k(17, P(z=-0.5), 'easeOutQuad'), k(18, P(), 'easeInQuad')]},
                      'arm_r': {'rotation': arm}, 'forearm_r': {'rotation': fore},
                      'sensor': {'rotation': [k(0, R()), k(6, R()), k(9, R(x=40), 'easeInOutSine'), k(17, R(x=40)),
                                              k(18, R(x=30, z=8), 'easeOutQuad'), k(24, R(), 'easeInOutSine')]},
                      'torso': {'rotation': [k(0, R()), k(16, R()), k(17, R(x=-3, z=2), 'easeOutQuad'), k(22, R(), 'easeInOutSine')]}},
            'effects': [(5, 'sound', snd('drawer_slide', 0.4, 1.0), None), (16, 'sound', snd('drawer_slam', 0.6, 1.0), None)]}


# ---- the knuckle-gallop ------------------------------------------------------------------------------------------
GALLOP = dict(n=12, stance=3, lift=4.0, swing_delay=1.0, up_exp=0.7, plant_ang=(6, 2), swing_ang=(-20, 8))
BOB6 = [-3.0, -2.2, -0.6, 0.8, 0.4, -1.6]     # each half cycle: a leg plants on 0 (the drop), both fists on 3 (the vault)


V_FAST = 0.18 * 2.16 * 16           # the unhalved speed: 6.22 px a tick
KNUCKLE = dict(n=12, stance=5, lift=3.5, swing_delay=1.0, up_exp=0.8, plant_ang=(6, 2), swing_ang=(-16, 6))
FIST_STANCE = 6


def walk():
    """The ridden pace (0.194 blocks a tick, 3.1 px; release branch). A heavy knuckle-walk, 12 ticks a cycle, each
    limb in turn: the left leg plants on 0, the left fist on 3, the right leg on 6, the right fist on 9 (a lateral
    sequence), so two or three limbs are always down. The cabinet pitches 22 degrees forward with its weight on the
    fists: it sinks 1.5 px onto each fist as it plants and rises over it, rolling 2 degrees toward the planted fist."""
    n, stance = KNUCKLE['n'], KNUCKLE['stance']
    reach = BA.V_WALK * stance / 2.0
    feet = lambda side, t: BA.stride_foot(t if side == 'l' else t - n // 2, KNUCKLE, n, stance, reach)
    ticks = range(0, n + 1)
    sweep = math.degrees(BA.V_WALK * FIST_STANCE / 24.0)     # the fist's sweep at about 24 px from the shoulder

    def arm_x(t, ph):
        s = (t - ph) % n
        if s <= FIST_STANCE:
            return 30 - sweep * s / FIST_STANCE
        u = (s - FIST_STANCE) / float(n - FIST_STANCE)
        return 30 - sweep + sweep * (1 - math.cos(math.pi * u)) / 2

    def fore_x(t, ph):
        s = (t - ph) % n
        if s <= FIST_STANCE:
            return 0.0
        u = (s - FIST_STANCE) / float(n - FIST_STANCE)
        return 26 * math.sin(math.pi * u)

    def bob(t):
        s = t % 6
        return [-1.8, -1.2, -0.6, -1.5, -1.0, -0.8][s]       # down onto the leg (0) and onto the fist (3)

    bones = {
        'hips': {'position': [k(t, P(y=bob(t))) for t in ticks],
                 'rotation': [k(t, R(z=round(2.0 * math.cos(2 * math.pi * (t - 3) / n), 3))) for t in ticks]},
        'torso': {'rotation': [k(t, R(x=round(-22 + 0.8 * bob((t - 1) % n), 3), z=round(-2.0 * math.cos(2 * math.pi * (t - 4) / n), 3)))
                               for t in ticks]},
        'arm_l': {'rotation': [k(t, R(x=round(arm_x(t, 3), 3), z=4)) for t in ticks]},
        'arm_r': {'rotation': [k(t, R(x=round(arm_x(t, 9), 3), z=-4)) for t in ticks]},
        'forearm_l': {'rotation': [k(t, R(x=round(fore_x(t, 3), 3))) for t in ticks]},
        'forearm_r': {'rotation': [k(t, R(x=round(fore_x(t, 9), 3))) for t in ticks]},
        'sensor': {'rotation': const(R(x=-16))},
        'clamp_l': {'rotation': [k(t, R(z=0.6 if t % 6 == 4 else 0)) for t in ticks]},
    }
    return {'length': n, 'loop': 'loop', 'native': True, 'feet': feet, 'bones': bones,
            'effects': [(0, 'timeline', 'step:l_lunk', None), (3, 'timeline', 'knuckle:l', None),
                        (6, 'timeline', 'step:r_lunk', None), (9, 'timeline', 'knuckle:r', None)]}


GALLOP = dict(n=12, stance=3, lift=4.0, swing_delay=1.0, up_exp=0.7, plant_ang=(6, 2), swing_ang=(-20, 8))
BOB6 = [-3.0, -2.2, -0.6, 0.8, 0.4, -1.6]     # each half cycle: a leg plants on 0 (the drop), both fists on 3 (the vault)


def walk_fast():
    """The gallop, kept for a sprint: at the unhalved speed (0.389 blocks a tick, 6.2 px) it bounds on its knuckles,
    6 ticks a step (2.33 blocks). The cabinet pitches 25 degrees forward; both fists swing out in front and plant
    together on 3 and 9 (the body vaults up over them), the legs plant alternately on 0 and 6 (the body drops)."""
    n, stance = GALLOP['n'], GALLOP['stance']
    reach = V_FAST * stance / 2.0
    vw = BA.V_WALK
    BA.V_WALK = V_FAST
    feet_pts = {(side, t): BA.stride_foot(t if side == 'l' else t - n // 2, GALLOP, n, stance, reach)
                for side in 'lr' for t in range(0, n + 1)}
    BA.V_WALK = vw
    feet = lambda side, t: feet_pts[(side, int(t) % n if int(t) != n else n)]
    ticks = range(0, n + 1)

    def arm_x(t):
        s = (t - 3) % 6
        if s <= 2:
            return 44 - 40 * s / 2.0
        u = (s - 2) / 4.0
        return 4 + 40 * (1 - math.cos(math.pi * u)) / 2

    def fore_x(t):
        s = (t - 3) % 6
        if s <= 2:
            return -6.0
        u = (s - 2) / 4.0
        return -6 + 55 * math.sin(math.pi * min(1.0, u * 1.15))

    bones = {
        'hips': {'position': [k(t, P(y=BOB6[t % 6])) for t in ticks],
                 'rotation': [k(t, R(z=round(2.0 * math.cos(2 * math.pi * (t - 2) / n), 3))) for t in ticks]},
        'torso': {'rotation': [k(t, R(x=round(-25 + 1.2 * BOB6[(t - 1) % 6], 3), z=round(2.0 * math.cos(2 * math.pi * (t - 3) / n), 3)))
                               for t in ticks]},
        'arm_l': {'rotation': [k(t, R(x=round(arm_x(t), 3), z=4)) for t in ticks]},
        'arm_r': {'rotation': [k(t, R(x=round(arm_x(t), 3), z=-4)) for t in ticks]},
        'forearm_l': {'rotation': [k(t, R(x=round(fore_x(t), 3))) for t in ticks]},
        'forearm_r': {'rotation': [k(t, R(x=round(fore_x(t), 3))) for t in ticks]},
        'sensor': {'rotation': const(R(x=-20))},
        'drawer': {'position': [k(t, P(z=-0.8 if t % 6 == 1 else 0)) for t in ticks]},
        'clamp_l': {'rotation': [k(t, R(z=0.8 if t % 6 == 2 else 0)) for t in ticks]},
    }
    return {'length': n, 'loop': 'loop', 'native': True, 'feet': feet, 'bones': bones,
            'effects': [(0, 'timeline', 'step:l_lunk', None), (3, 'timeline', 'knuckles:both', None),
                        (6, 'timeline', 'step:r_lunk', None), (9, 'timeline', 'knuckles:both', None)]}


# ---- the toss ------------------------------------------------------------------------------------------------------
def toss():
    """The ability (30 ticks, once): crouch, the fists scoop under the target (0-8), heave it up over the lid
    (8-14, easeInOutCubic), a strain hold with the knees trembling (14-18), throw (18-19, easeInExpo; toss:release on
    19), follow through and recover."""
    arm = [k(0, R()), k(8, R(x=SCOOP['arm'], z=SCOOP['inward']), 'easeInOutCubic'), k(14, R(x=OVER['arm'], z=OVER['inward']),
                                                                                       'easeInOutCubic'),
           k(18, R(x=OVER['arm'] + 6, z=OVER['inward'])), k(19, R(x=95), 'easeInExpo'), k(22, R(x=60), 'easeOutQuad'),
           k(30, R(), 'easeInOutCubic')]
    fore = [k(0, R()), k(8, R(x=SCOOP['fore']), 'easeInOutCubic'), k(14, R(x=OVER['fore']), 'easeInOutCubic'), k(18, R(x=-18)),
            k(19, R(x=-6), 'easeInExpo'), k(30, R(), 'easeInOutCubic')]
    grip = [k(0, R()), k(6, R(x=25), 'easeOutQuad'), k(8, R(x=-30), 'easeInQuad'), k(18, R(x=-30)),
            k(19, R(x=30), 'easeOutExpo'), k(26, R(), 'easeInOutSine')]
    bones = dict(BA.both_arms(arm, fore, None, grip))
    hips = [k(0, P()), k(7, P(y=-4.5), 'easeInOutCubic'), k(12, P(y=0.5), 'easeInOutCubic'), k(14, P(y=-1))]
    for i, t in enumerate(range(15, 19)):
        hips.append(k(t, P(y=-1.4 if i % 2 == 0 else -0.6)))
    hips += [k(19, P(y=0.8), 'easeInExpo'), k(22, P(y=-2), 'easeOutQuad'), k(28, P(), 'easeInOutSine')]
    bones.update({
        'hips': {'position': hips},
        'torso': {'rotation': [k(0, R()), k(7, R(x=-22), 'easeInOutCubic'), k(14, R(x=14), 'easeInOutCubic'),
                               k(15, R(x=16, z=1.5)), k(16, R(x=13, z=-1.5)), k(17, R(x=16, z=1.5)), k(18, R(x=14)),
                               k(19, R(x=-14), 'easeInExpo'), k(22, R(x=-18), 'easeOutQuad'), k(30, R(), 'easeInOutSine')]},
        'sensor': {'rotation': [k(0, R()), k(6, R(x=30), 'easeInOutSine'), k(14, R(x=-40), 'easeInOutSine'), k(19, R(x=-30)),
                                k(24, R(x=-10), 'easeInOutSine'), k(30, R(), 'easeInOutSine')]},
        'drawer': {'position': [k(0, P()), k(19, P()), k(20, P(z=-3), 'easeOutExpo'), k(23, P(), 'easeInQuad')]},
    })
    return {'length': 30, 'loop': 'once', 'native': True, 'feet': BA.planted(), 'bones': bones,
            'effects': [(1, 'sound', snd('servo', 0.6, 0.8), None), (8, 'timeline', 'toss:grab', None),
                        (10, 'sound', snd('hydraulic_lift', 0.7, 0.9), None), (15, 'sound', snd('strain', 0.5, 0.9), None),
                        (19, 'timeline', 'toss:release', None), (20, 'sound', snd('whoosh', 0.7, 0.9), None),
                        (21, 'sound', snd('drawer_slam', 0.4, 1.1), None)]}


def toss_refused():
    """Toss refused (flat battery, or nothing in reach; 22 ticks): the arms come up to scoop (chest height by 6), a
    falling servo whine, and they drop below rest (easeInQuad) with the shoulders sagging 1.5 px; the webcam droops."""
    arm = [k(0, R()), k(6, R(x=70, z=16), 'easeInOutCubic'), k(9, R(x=66, z=14)), k(13, R(x=-4, z=-6), 'easeInQuad'),
           k(15, R(x=0, z=-4), 'easeOutQuad'), k(22, R(), 'easeInOutSine')]
    bones = dict(BA.both_arms(arm, [k(0, R()), k(6, R(x=-30), 'easeInOutCubic'), k(13, R(x=-10), 'easeInQuad'),
                                    k(22, R(), 'easeInOutSine')]))
    bones['arm_l']['position'] = [k(0, P()), k(9, P()), k(13, P(y=-1.5), 'easeInQuad'), k(19, P(y=-1.5)), k(22, P(), 'easeInOutSine')]
    bones['arm_r']['position'] = [k(0, P()), k(9, P()), k(13, P(y=-1.5), 'easeInQuad'), k(19, P(y=-1.5)), k(22, P(), 'easeInOutSine')]
    bones.update({
        'hips': {'position': [k(0, P()), k(6, P(y=-2.5), 'easeInOutCubic'), k(10, P(y=-2.5)), k(13, P(y=1.2), 'easeInQuad'),
                              k(22, P(), 'easeInOutSine')]},
        'torso': {'rotation': [k(0, R()), k(6, R(x=-10), 'easeInOutCubic'), k(10, R(x=-10)), k(13, R(x=-4), 'easeInQuad'),
                               k(22, R(), 'easeInOutSine')]},
        'sensor': {'rotation': [k(0, R()), k(10, R()), k(13, R(x=35), 'easeInQuad'), k(22, R(), 'easeInOutSine')]},
    })
    return {'length': 22, 'loop': 'once', 'native': True, 'feet': BA.planted(), 'bones': bones,
            'effects': [(0, 'timeline', 'toss:refused', None), (9, 'sound', snd('servo_down', 0.5, 0.9), None)]}


def flat():
    """The jar goes flat (40 ticks, hold): a winding-down servo, the arms sag to the floor, the cabinet slumps forward
    into the parked pose, the webcam droops, the lights go out (the webcam last, after a flicker)."""
    bones = dict(BA.both_arms([k(0, R()), k(6, R(x=-3), 'easeOutQuad'), k(30, R(x=SLUMP['arm']), 'easeInOutCubic')],
                              [k(0, R()), k(10, R()), k(30, R(x=SLUMP['fore']), 'easeInOutCubic')], None,
                              [k(0, R()), k(26, R()), k(32, R(x=20), 'easeInOutSine')], lag=2))
    bones.update({
        'hips': {'position': [k(0, P()), k(4, P(y=0.4), 'easeOutQuad'), k(32, P(y=SLUMP['hips'] - 0.5), 'easeInQuad'),
                              k(36, P(y=SLUMP['hips']), 'easeInOutSine')]},
        'torso': {'rotation': [k(0, R()), k(8, R()), k(32, R(x=SLUMP['torso'] - 1), 'easeInQuad'),
                               k(36, R(x=SLUMP['torso']), 'easeInOutSine')]},
        'sensor': {'rotation': [k(0, R()), k(14, R()), k(30, R(x=SLUMP['cam']), 'easeInQuad')]},
        'drawer': {'position': [k(0, P()), k(33, P()), k(34, P(z=-1.5), 'easeOutQuad'), k(36, P(z=-1))]},
    })
    bones.update(lamps([(0, 1), (24, 0), (26, 1), (28, 0)], [(0, 1), (16, 0)]))
    return {'length': 40, 'loop': 'hold', 'native': True, 'feet': BA.planted(), 'bones': bones,
            'effects': [(0, 'timeline', 'flat:begin', None), (2, 'sound', snd('servo_wind_down', 0.7, 0.8), None),
                        (34, 'sound', snd('clunk', 0.4, 0.8), None)]}


def destroyed():
    """Collapse (40 ticks before the server's explosion): the Bulwark's sparks and dying lamps, but a cabinet does not
    fall on its face: the knees give, it sits down hard on its plinth tilted back (27, a thud), the arms splay out to
    the floor either side, and every drawer springs open (14)."""
    c = BA.destroyed()
    c['bones']['hips'] = {'position': [k(0, P()), k(8, P()), k(12, P(y=-3), 'easeInQuad'), k(14, P(y=-2.5), 'easeOutQuad'),
                                       k(22, P(y=-5), 'easeInQuad'), k(27, P(y=-7.5), 'easeInQuad'), k(30, P(y=-7), 'easeOutQuad'),
                                       k(33, P(y=-7.5), 'easeInOutSine')],
                          'rotation': [k(0, R()), k(8, R()), k(12, R(z=4), 'easeInQuad'), k(22, R(z=1), 'easeInOutSine'),
                                       k(27, R()), k(40, R())]}
    c['bones']['torso'] = {'rotation': [k(0, R()), k(10, R()), k(20, R(x=6), 'easeInQuad'), k(27, R(x=14), 'easeInQuad'),
                                        k(30, R(x=12), 'easeOutQuad'), k(33, R(x=14), 'easeInOutSine')]}
    c['bones'].update(BA.both_arms([k(0, R()), k(2, R()), k(8, R(x=-18, z=-18), 'easeInQuad'), k(20, R(x=-6, z=-24)),
                                    k(27, R(x=10, z=-62), 'easeInQuad'), k(30, R(x=8, z=-58), 'easeOutQuad'),
                                    k(33, R(x=10, z=-60), 'easeInOutSine')],
                                   [k(0, R()), k(3, R()), k(9, R(x=-15), 'easeInQuad'), k(20, R(x=-10)),
                                    k(27, R(x=20), 'easeInQuad')], None,
                                   [k(0, R()), k(20, R()), k(27, R(x=60), 'easeInQuad')], lag=1))
    c['bones']['drawer'] = {'position': [k(0, P()), k(12, P()), k(14, P(y=-1, z=-8), 'easeOutExpo'), k(40, P(y=-1, z=-8))],
                            'rotation': [k(0, R()), k(12, R()), k(14, R(x=-18, z=6), 'easeOutExpo'), k(40, R(x=-18, z=6))]}
    c['native'] = True
    c['effects'].append((13, 'sound', snd('drawers_burst', 0.7, 1.0), None))
    return c


# ==================================================================================================================
def clips():
    return [('parked', parked()), ('board', board()), ('alight', alight()), ('idle', idle()), ('idle_tic', idle_tic()),
            ('walk', walk()), ('walk_fast', walk_fast()), ('toss', toss()), ('toss_refused', toss_refused()), ('hurt', BA.hurt()), ('flat', flat()),
            ('destroyed', destroyed())]


BA.clips = clips
BA.MODEL = G.ANIM['model']

if __name__ == '__main__':
    BA.main()

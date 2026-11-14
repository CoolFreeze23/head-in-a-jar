"""Executive: every clip, keyed in ticks, written into executive.bbmodel.

The heavy-biped clip script (art/mecha/bulwark/bulwark_anim.py) run at the Executive's sizes (use_frame: its rig
numbers, speed, walk timings from executive_geo.ANIM; the shared clips' position keys scaled 1.3; the legs re-solved
every tick for its own thigh and shin; the walks with a straight back and the hands held behind its back), then:
  - no Heavy Lifting: the Executive does not lift, it delegates (grab, hold, throw, grab_refused and the carry walks
    are left out);
  - its own boot (the premium jingle, the four Gene Ports light in turn, it adjusts its tie), idle tic (it checks its
    watch), drive_jingle (Drive: the premium jingle and a tug of the lapels) and build (the fitting).

    python art/mecha/executive/executive_anim.py                 # write the clips into executive.bbmodel
    python art/mecha/executive/executive_anim.py --preview OUT   # review copy

The Gene Ports (gene_port_0..3, the serum in each vial) are never keyed: the game sets each one's fill and colour.
Clips ask the game to make them glow with timeline keys gene:pulse:<n>.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(os.path.dirname(HERE), 'bulwark'))
sys.path.insert(0, os.path.join(os.path.dirname(HERE), 'hopper'))
import executive_geo as G  # noqa: E402
import bulwark_anim as BA  # noqa: E402
from hopper_anim import k, R, P, S, snd  # noqa: E402

BA.use_frame(G)
NO_LIFT = ('grab', 'hold', 'throw', 'grab_refused', 'carry_walk', 'carry_walk_b', 'carry_walk_c')

# arm poses (FK-solved on executive_geo, the right arm mirrored by BA.both_arms)
WATCH = dict(arm=R(x=78, y=-60, z=24), fore=R(x=-12), hand=R(z=-25))  # left forearm raised across the chest, the
                                                                         # watch face at (-11, 55, -11)
TIE_R = dict(arm=R(x=66, z=-90), fore=R(x=-60))                          # the right glove at the knot, elbow out
                                                                         # (mirror of the left-arm solve 66, 0, 90, -60)
LAPEL = dict(arm=R(x=58, z=72), fore=R(x=-48))                          # both gloves at the lapels


def add(clip, bones, effects=()):
    clip['bones'].update(bones)
    taken = {e[0] for e in clip['effects']}
    for e in effects:
        assert e[0] not in taken, (e, 'tick taken')
        clip['effects'].append(e)
        taken.add(e[0])
    return clip


def pulses(t0, step=3):
    """gene:pulse:<n> in turn, one tick apart by `step`: the game brightens that vial's serum for a moment."""
    return [(t0 + i * step, 'timeline', 'gene:pulse:%d' % i, None) for i in range(G.GENE_PORTS)]


def tie_adjust(t0):
    """The right glove comes up to the knot, two small tugs (the tie wiggles), back down: 22 ticks from t0."""
    arm = [k(0, R()), k(t0, R()), k(t0 + 6, TIE_R['arm'], 'easeInOutCubic'), k(t0 + 15, TIE_R['arm']),
           k(t0 + 22, R(), 'easeInOutCubic')]
    fore = [k(0, R()), k(t0, R()), k(t0 + 6, TIE_R['fore'], 'easeInOutCubic'), k(t0 + 15, TIE_R['fore']),
            k(t0 + 22, R(), 'easeInOutCubic')]
    tie = [k(0, R()), k(t0 + 7, R()), k(t0 + 9, R(z=8), 'easeOutQuad'), k(t0 + 11, R(z=-3), 'easeInOutSine'),
           k(t0 + 13, R(z=5), 'easeInOutSine'), k(t0 + 16, R(), 'easeInOutSine')]
    return {'arm_r': {'rotation': arm}, 'forearm_r': {'rotation': fore}, 'sensor': {'rotation': tie}}


# ==================================================================================================================
def boot():
    """Controller "cockpit": the premium jingle (in tune, five notes), the canopy lamps and the tie pin light, the four
    Gene Ports light in turn (5, 9, 13, 17), then it adjusts its tie (18-40)."""
    bones = tie_adjust(18)
    bones.update(BA.lamps([(0, 0), (2, 1)], [(0, 0), (4, 1)]))
    bones['torso'] = {'rotation': [k(0, R()), k(5, R(x=2), 'easeInOutSine'), k(16, R(x=-1), 'easeInOutSine'),
                                   k(22, R(), 'easeInOutSine')]}
    eff = [(0, 'sound', snd('premium_jingle', 0.8, 1.0), None), (2, 'sound', snd('relay_click', 0.3, 1.1), None)]
    eff += pulses(5, 4)
    eff += [(25, 'sound', snd('cloth_tug', 0.4, 1.0), None), (29, 'sound', snd('cloth_tug', 0.4, 1.05), None),
            (38, 'timeline', 'boot:done', None)]
    return {'length': 42, 'loop': 'hold', 'native': True, 'bones': bones, 'effects': eff}


def idle_tic():
    """Every 200-300 idle ticks: it checks its watch. The left forearm comes up across the chest, the tie-pin lamp tips
    down to look, a 10-tick hold, a small impatient tap of the right foot, and down."""
    arm = [k(0, R()), k(6, WATCH['arm'], 'easeInOutCubic'), k(18, WATCH['arm']), k(25, R(), 'easeInOutCubic')]
    fore = [k(0, R()), k(6, WATCH['fore'], 'easeInOutCubic'), k(18, WATCH['fore']), k(25, R(), 'easeInOutCubic')]
    hand = [k(0, R()), k(6, WATCH['hand'], 'easeInOutSine'), k(18, WATCH['hand']), k(24, R(), 'easeInOutSine')]
    a = BA.REST_FOOT_T
    feet = {'l': [k(0, a)],
            'r': [k(0, a), k(10, a), k(12, (a[0], a[1] + 2.5, -14), 'easeOutQuad'), k(14, a, 'easeInQuad'), k(30, a)]}
    return {'length': 30, 'loop': 'once', 'native': True, 'feet': feet,
            'bones': {'arm_l': {'rotation': arm}, 'forearm_l': {'rotation': fore}, 'hand_l': {'rotation': hand},
                      'sensor': {'rotation': [k(0, R()), k(6, R(x=-10, y=12), 'easeInOutSine'), k(18, R(x=-10, y=12)),
                                              k(24, R(), 'easeInOutSine')]},
                      'torso': {'rotation': [k(0, R()), k(7, R(y=6, x=-2), 'easeInOutSine'), k(18, R(y=6, x=-2)),
                                             k(25, R(), 'easeInOutSine')]}},
            'effects': [(7, 'sound', snd('watch_tick', 0.4, 1.0), None), (14, 'sound', snd('shoe_tap', 0.4, 1.0), None),
                        (19, 'sound', snd('sigh', 0.3, 1.1), None)]}


def drive_jingle():
    """Drive (30 ticks, any time it is standing): no verb, the premium jingle. Both gloves tug the lapels, the canopy
    lamps run round the corners, the vials pulse in turn, a small nod."""
    arm = [k(0, R()), k(5, LAPEL['arm'], 'easeInOutCubic'), k(9, LAPEL['arm']), k(10, R(x=LAPEL['arm'][0] - 6,
                                                                                         z=LAPEL['arm'][2]), 'easeOutQuad'),
           k(13, LAPEL['arm'], 'easeInOutSine'), k(20, R(), 'easeInOutCubic')]
    fore = [k(0, R()), k(5, LAPEL['fore'], 'easeInOutCubic'), k(13, LAPEL['fore']), k(20, R(), 'easeInOutCubic')]
    bones = dict(BA.both_arms(arm, fore))
    bones.update({
        'torso': {'rotation': [k(0, R()), k(6, R(x=2.5), 'easeInOutSine'), k(11, R(x=-7), 'easeOutQuad'),
                               k(15, R(x=1), 'easeInOutSine'), k(19, R(), 'easeInElastic', [3])]},
        'hips': {'position': [k(0, P()), k(6, P(y=0.6), 'easeOutQuad'), k(11, P(y=-1.2), 'easeInQuad'),
                              k(17, P(), 'easeInOutSine')]},
        'sensor': {'rotation': [k(0, R()), k(9, R()), k(11, R(z=4), 'easeOutQuad'), k(14, R(), 'easeInOutSine')]},
    })
    bones.update(BA.lamps([(0, 1)], [(0, 1), (14, 0), (16, 1), (18, 0), (20, 1)]))
    eff = [(0, 'timeline', 'drive:jingle', None), (1, 'sound', snd('premium_jingle', 0.8, 1.0), None),
           (10, 'sound', snd('cloth_tug', 0.4, 1.0), None)]
    eff += pulses(16, 4)
    return {'length': 30, 'loop': 'once', 'native': True, 'feet': BA.planted(), 'bones': bones, 'effects': eff}


def eject():
    """The Golden Parachute, executive class: the sunroof flips open (ticks 1-3) before the jar is fired up through it
    (the shared eject: tick 4), and closes once it is gone (16-22)."""
    c = BA.eject()
    c['bones']['sunroof'] = {'rotation': [k(0, R()), k(1, R(x=-4), 'easeOutQuad'), k(3, R(x=105), 'easeOutExpo'),
                                          k(5, R(x=96), 'easeInOutSine'), k(16, R(x=96)), k(22, R(), 'easeInQuad'),
                                          k(23, R(x=3), 'easeOutQuad'), k(25, R(), 'easeInQuad')]}
    taken = {e[0] for e in c['effects']}
    for e in [(1, 'sound', snd('sunroof_motor', 0.6, 1.0), None), (23, 'sound', snd('sunroof_shut', 0.5, 1.0), None)]:
        assert e[0] not in taken
        c['effects'].append(e)
    return c


BUILD_SLOTS = [('legs', 44, ['socket_thigh_l', 'socket_thigh_r', 'socket_shin_l', 'socket_shin_r']),
               ('back', 52, ['socket_back']), ('back_1', 57, ['socket_back_1']),
               ('core', 62, ['socket_core']), ('core_1', 67, ['socket_core_1']),
               ('torso_0', 74, ['socket_torso_0']), ('torso_1', 79, ['socket_torso_1']),
               ('torso_2', 84, ['socket_torso_2']), ('torso_3', 89, ['socket_torso_3']),
               ('torso_4', 94, ['socket_torso_4']),
               ('arm_l', 101, ['socket_arm_l']), ('arm_r', 106, ['socket_arm_r']),
               ('arm_2', 111, ['socket_arm_2']), ('arm_3', 116, ['socket_arm_3']),
               ('sensor', 123, ['socket_sensor']), ('sensor_1', 128, ['socket_sensor_1'])]


def build():
    """The fitting (240 ticks): delivered standing, arms held out level like a client being measured (the tailor's
    pose), lamps dark; the modules are fitted by slot (44-133, each from 8 px out with a 1 px bounce, a tailor's
    'tuck' sound); the vials are filled one by one (150-168, gene:pulse); the arms come down (172-184); it checks
    itself (a turn left and right, 186-204), adjusts its tie (205-227), the premium jingle (230), and parks."""
    arm = [k(0, R(z=-82)), k(140, R(z=-82)), k(146, R(z=-84), 'easeOutQuad'), k(150, R(z=-82), 'easeInOutSine'),
           k(172, R(z=-82)), k(184, R(), 'easeInOutCubic')]
    fore = [k(0, R(x=-15)), k(172, R(x=-15)), k(184, R(), 'easeInOutCubic'), k(228, R()),
            k(238, R(x=BA.PARK_FORE), 'easeInOutSine')]
    bones = dict(BA.both_arms(arm, fore))
    tie = tie_adjust(205)
    bones['arm_r']['rotation'] = [kk for kk in bones['arm_r']['rotation'] if kk[0] <= 184] + \
        [kk for kk in tie['arm_r']['rotation'] if kk[0] >= 205]
    bones['forearm_r']['rotation'] = [kk for kk in bones['forearm_r']['rotation'] if kk[0] <= 184] + \
        [kk for kk in tie['forearm_r']['rotation'] if kk[0] >= 205] + [k(238, R(x=BA.PARK_FORE), 'easeInOutSine')]
    PH = BA.PARK_HIPS * BA.SC
    bones.update({
        'sensor': {'rotation': [k(0, R(x=10)), k(30, R(x=10)), k(36, R(), 'easeInOutSine')] +
                   [kk for kk in tie['sensor']['rotation'] if kk[0] >= 212]},
        'torso': {'rotation': [k(0, R()), k(186, R()), k(193, R(y=18), 'easeInOutSine'), k(197, R(y=18)),
                               k(203, R(y=-14), 'easeInOutSine'), k(207, R(), 'easeInOutSine'),
                               k(230, R()), k(238, R(x=BA.PARK_TORSO), 'easeInOutSine')]},
        'hips': {'position': [k(0, P()), k(228, P()), k(238, P(y=PH), 'easeInQuad'), k(240, P(y=PH))]},
    })
    bones.update(BA.lamps([(0, 0), (140, 1), (232, 0)], [(0, 0), (140, 1), (232, 0)]))
    bones.update(BA.clamps([k(0, [40]), k(224, [40]), k(228, [0], 'easeInQuad'), k(229, [-2], 'easeOutQuad'),
                            k(231, [0], 'easeInQuad')]))
    effects = [(140, 'sound', snd('relay_click', 0.4, 1.0), None)]
    effects += pulses(150, 6)
    effects += [(186, 'sound', snd('servo', 0.3, 1.1), None), (210, 'sound', snd('cloth_tug', 0.4, 1.0), None),
                (226, 'timeline', 'build:fitted', None), (228, 'sound', snd('clamp_close', 0.5, 1.0), None),
                (230, 'sound', snd('premium_jingle', 0.8, 1.0), None)]
    for slot, T0, socks in BUILD_SLOTS:
        for sb in socks:
            o = outward(sb)
            ch = bones.setdefault(sb, {})
            ch['scale'] = [k(0, S(0), interp='step'), k(T0, S(1), interp='step')]
            ch['position'] = [k(0, P(*[8 * c for c in o])), k(T0, P(*[8 * c for c in o])), k(T0 + 3, P(), 'easeInQuad'),
                              k(T0 + 4, P(*[1.0 * c for c in o]), 'easeOutQuad'), k(T0 + 5, P(), 'easeInQuad')]
        effects.append((T0 + 3, 'timeline', 'build:seat:%s' % slot, None))
    return {'length': 240, 'loop': 'hold', 'native': True, 'feet': BA.planted(), 'bones': bones, 'effects': effects,
            'pilot': False}


def outward(bone):
    """A socket's -Z in the model frame (its parent chain's rest rotations)."""
    import math
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


# ==================================================================================================================
def clips():
    own = {'boot': boot, 'idle_tic': idle_tic, 'build': build, 'eject': eject}
    out = [(name, own[name]() if name in own else c) for name, c in SHARED() if name not in NO_LIFT]
    out.append(('drive_jingle', drive_jingle()))
    return out


SHARED = BA.clips
BA.clips = clips

if __name__ == '__main__':
    BA.main()

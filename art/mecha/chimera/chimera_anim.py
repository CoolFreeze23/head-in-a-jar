"""Chimera: every clip, keyed in ticks, written into chimera.bbmodel.

The light-biped clip script (art/mecha/hopper/hopper_anim.py) run at the Chimera's sizes (use_frame: its rig numbers,
speed and walk timing from chimera_geo.ANIM; the shared clips' position keys scaled 1.12; the legs re-solved every
tick for its own thigh and shin), then:
  - no Pogo (the Hopper's verb): pogo_crouch, pogo_hold, pogo_leap and pogo_land are left out;
  - the growth breathes: in idle, parked and the walk;
  - its own boot (a yawn), idle tic (a forearm twitches on its own, as if the genes are arguing), the Graft
    (graft_l / graft_r and graft_refused) and build (grown, then issued a hard hat).

    python art/mecha/chimera/chimera_anim.py                 # write the clips into chimera.bbmodel
    python art/mecha/chimera/chimera_anim.py --preview OUT   # review copy

The Gene Ports (gene_port_0..2, the serum in each gill vial) are never keyed: the game sets their fill and colour.
Clips ask for a glow with timeline keys gene:pulse:<n>. The veins' colour is the game's too (graft:take).
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(os.path.dirname(HERE), 'hopper'))
import chimera_geo as G  # noqa: E402
import hopper_anim as HA  # noqa: E402
from hopper_anim import k, R, P, S, snd, planted, lamps, const  # noqa: E402

HA.use_frame(G)
HA.PARK_SENSOR = 35.0     # parked, the eye's stalk droops forward (the Hopper's chin sensor looks down at -20)
DROP = ('pogo_crouch', 'pogo_hold', 'pogo_leap', 'pogo_land')
HAT_OFF = 100.0            # restraint x: the hard hat lifted up and back off the lid


def add(clip, bones, effects=()):
    clip['bones'].update(bones)
    taken = {e[0] for e in clip['effects']}
    for e in effects:
        assert e[0] not in taken, (e, 'tick taken')
        clip['effects'].append(e)
        taken.add(e[0])
    return clip


def breathe(n, amp, offset=0):
    """The growth's breath: one swell over n ticks (scale 1 -> 1 + amp -> 1), starting `offset` ticks in."""
    return {'growth': {'scale': [k(0, S(1)), k(offset + n // 2, S(1 + amp), 'easeInOutSine'),
                                 k(offset + n, S(1), 'easeInOutSine')]}}


def pulses(t0, step=4):
    return [(t0 + i * step, 'timeline', 'gene:pulse:%d' % i, None) for i in range(G.GENE_PORTS)]


# ==================================================================================================================
def idle():
    return add(HA.idle(), breathe(60, 0.05))


def parked():
    c = HA.parked()
    return add(c, breathe(c['length'], 0.03))


def walk():
    c = HA.walk()
    n = c['length']
    c['bones']['growth'] = {'scale': [k(0, S(1)), k(n // 4, S(1.03), 'easeInOutSine'), k(n // 2, S(1), 'easeInOutSine'),
                                      k(3 * n // 4, S(1.03), 'easeInOutSine'), k(n, S(1), 'easeInOutSine')]}
    return c


def limp(side):
    """The damage limp (the shared limp_l, or its mirror for limp_r): the growth jolts on each landing of the bad leg
    and breathes again over the good one."""
    c = HA.limp_l()
    n = c['length']
    shift = 0 if side == 'l' else n // 2
    if shift:
        c = HA.mirror_clip(c, shift)
    pg = HA.limp_ticks(n, HA.LIMP_GAIT)['pg']
    swell = lambda t: 1 + 0.04 * HA.bump(t, 2.5, 1.6, n) + 0.015 * HA.bump(t, pg + 2, 1.5, n)
    c['bones']['growth'] = {'scale': [k(t, S(round(swell(t - shift), 4))) for t in range(n + 1)]}
    return c


def boot():
    """Controller "cockpit": it yawns. The cockpit tilts back, the growth expands, the right arm stretches up and out,
    a shiver, the lamps come on (the eye last, blinking), the gills light in turn."""
    bones = {
        'torso': {'rotation': [k(0, R()), k(6, R(x=-3), 'easeInOutSine'), k(14, R(x=9), 'easeInOutCubic'), k(22, R(x=9)),
                               k(28, R(x=-1.5), 'easeInOutCubic'), k(32, R(), 'easeInOutSine')]},
        'growth': {'scale': [k(0, S(1)), k(6, S(0.97), 'easeInOutSine'), k(14, S(1.09), 'easeInOutCubic'), k(22, S(1.09)),
                             k(28, S(0.99), 'easeInOutCubic'), k(30, S(1.015), 'easeInOutSine'), k(32, S(1), 'easeInOutSine')]},
        'arm_r': {'rotation': [k(0, R()), k(8, R()), k(15, R(x=60, z=40), 'easeInOutCubic'), k(21, R(x=62, z=42)),
                               k(28, R(), 'easeInOutCubic')]},
        'forearm_r': {'rotation': [k(0, R()), k(8, R()), k(15, R(x=-70), 'easeInOutCubic'), k(21, R(x=-70)),
                                   k(28, R(), 'easeInOutCubic')]},
        'sensor': {'rotation': [k(0, R(x=HA.PARK_SENSOR)), k(14, R(x=-25), 'easeInOutSine'), k(22, R(x=-25)),
                                k(28, R(), 'easeInOutSine'), k(30, R(y=10), 'easeInOutSine'), k(33, R(), 'easeInOutSine')]},
        'restraint': {'rotation': [k(0, R()), k(14, R()), k(16, R(x=4), 'easeOutQuad'), k(19, R(), 'easeInBounce')]},
    }
    bones.update(lamps([(0, 0), (4, 1)], [(0, 0), (6, 1)], [(0, 0), (30, 1), (31, 0), (32, 1)]))
    eff = [(1, 'sound', snd('boot_chime', 0.6, 0.9), None), (8, 'sound', snd('yawn', 0.6, 0.8), None),
           (24, 'sound', snd('joint_crack', 0.4, 1.0), None), (27, 'sound', snd('shiver', 0.4, 1.0), None)]
    eff += pulses(33, 2)
    eff += [(40, 'timeline', 'boot:done', None)]
    return {'length': 42, 'loop': 'hold', 'native': True, 'bones': bones, 'effects': eff}


def idle_tic():
    """Every 200-300 idle ticks: the left forearm jerks up on its own for 4 ticks, as if the genes are arguing; the eye
    snaps round to look at it; the forearm is put back, slowly, with a look of disapproval (the eye tilts)."""
    return {'length': 30, 'loop': 'once', 'native': True, 'feet': planted(),
            'bones': {'forearm_l': {'rotation': [k(0, R()), k(4, R()), k(5, R(x=-38, z=-8), 'easeOutExpo'),
                                                 k(8, R(x=-34, z=-6)), k(9, R(x=-40, z=-9), 'easeOutQuad'),
                                                 k(20, R(), 'easeInOutSine')]},
                      'arm_l': {'rotation': [k(0, R()), k(4, R()), k(5, R(x=-12, z=-6), 'easeOutExpo'),
                                             k(9, R(x=-12, z=-6)), k(20, R(), 'easeInOutSine')]},
                      'sensor': {'rotation': [k(0, R()), k(5, R()), k(7, R(x=25, z=30), 'easeOutCubic'), k(14, R(x=25, z=30)),
                                              k(17, R(x=25, z=30, y=20), 'easeInOutSine'), k(22, R(x=25, z=30, y=20)),
                                              k(27, R(), 'easeInOutSine')]},
                      'growth': {'scale': [k(0, S(1)), k(5, S(1)), k(6, S(1.04), 'easeOutQuad'), k(9, S(1), 'easeInBounce')]}},
            'effects': [(5, 'sound', snd('twitch', 0.5, 1.0), None), (15, 'sound', snd('tut', 0.3, 1.0), None)]}


# ---- the Graft ----------------------------------------------------------------------------------------------------
def graft(side):
    """Drive within 5 s of hitting a creature with the `side` arm (40 ticks, once). Wind-up (0-8): it brings the
    hitting forearm to its eye and sniffs, hunching, the growth drawing in. The act (8-24): the taste travels up the
    arm (forearm 9, upper arm 11), the growth gulps and swells 24 % (13), the hat jumps (14, graft:take: the server
    grants the gene and the veins take the creature's colour), the gill covers flare and the arms spread. Recovery
    (24-40): a satisfied shiver, back to standing, the gills pulsing in turn."""
    s = -1 if side == 'l' else 1
    me, other = side, ('r' if side == 'l' else 'l')
    bones = {
        'arm_' + me: {'rotation': [k(0, R()), k(6, R(x=60, z=s * 32), 'easeInOutCubic'), k(9, R(x=60, z=s * 32)),
                                   k(11, R(x=54, z=s * 28), 'easeOutQuad'), k(13, R(x=60, z=s * 32), 'easeInQuad'),
                                   k(18, R(x=10, z=-s * -40), 'easeOutCubic'), k(26, R(x=10, z=-s * -40)),
                                   k(34, R(), 'easeInOutCubic')]},
        'forearm_' + me: {'rotation': [k(0, R()), k(6, R(x=-4), 'easeInOutCubic'), k(9, R(x=-8), 'easeOutQuad'),
                                       k(10, R(x=-2), 'easeInQuad'), k(18, R(x=-30), 'easeOutCubic'),
                                       k(26, R(x=-30)), k(34, R(), 'easeInOutCubic')]},
        'arm_' + other: {'rotation': [k(0, R()), k(12, R()), k(18, R(x=10, z=-s * 40), 'easeOutCubic'), k(26, R(x=10, z=-s * 40)),
                                      k(34, R(), 'easeInOutCubic')]},
        'forearm_' + other: {'rotation': [k(0, R()), k(12, R()), k(18, R(x=-30), 'easeOutCubic'), k(26, R(x=-30)),
                                          k(34, R(), 'easeInOutCubic')]},
        'sensor': {'rotation': [k(0, R()), k(6, R(x=25, z=30 if side == 'l' else -15), 'easeInOutCubic'),
                                k(9, R(x=25, z=30 if side == 'l' else -15)), k(14, R(x=-20), 'easeOutCubic'), k(22, R(x=-12)),
                                k(32, R(), 'easeInOutSine')]},
        'growth': {'scale': [k(0, S(1)), k(8, S(0.92), 'easeInOutSine'), k(12, S(0.92)), k(14, S(1.24), 'easeOutExpo'),
                             k(18, S(1.18), 'easeInOutSine'), k(24, S(1.2)), k(28, S(1.0), 'easeInOutCubic'),
                             k(30, S(1.03), 'easeOutQuad'), k(32, S(0.99), 'easeInOutSine'), k(34, S(1), 'easeInOutSine')]},
        'torso': {'rotation': [k(0, R()), k(8, R(x=-8, y=s * -8), 'easeInOutCubic'), k(12, R(x=-8, y=s * -8)),
                               k(14, R(x=5), 'easeOutExpo'), k(24, R(x=3)), k(32, R(), 'easeInOutSine')]},
        'hips': {'position': [k(0, P()), k(8, P(y=-2.5), 'easeInOutCubic'), k(12, P(y=-2.5)), k(14, P(y=1.5), 'easeOutExpo'),
                              k(20, P(y=0.5), 'easeInOutSine'), k(30, P(), 'easeInOutSine')]},
        'restraint': {'rotation': [k(0, R()), k(13, R()), k(14, R(x=14), 'easeOutExpo'), k(17, R(), 'easeInBounce')]},
        'vent_l': {'rotation': [k(0, R()), k(14, R()), k(16, R(x=-60), 'easeOutExpo'), k(24, R(x=-55)), k(30, R(), 'easeInQuad')]},
        'vent_r': {'rotation': [k(0, R()), k(14, R()), k(16, R(x=-60), 'easeOutExpo'), k(24, R(x=-55)), k(30, R(), 'easeInQuad')]},
    }
    eff = [(0, 'timeline', 'graft:begin', None), (5, 'sound', snd('sniff', 0.5, 1.0), None),
           (9, 'timeline', 'graft:pulse:%s' % side, None), (12, 'sound', snd('squelch_gulp', 0.7, 1.0), None),
           (13, 'particle', 'cloud', 'fx_graft'), (14, 'timeline', 'graft:take', None),
           (16, 'particle', 'cloud', 'fx_vent_l'), (17, 'particle', 'cloud', 'fx_vent_r'),
           (28, 'sound', snd('shiver', 0.5, 1.0), None)]
    eff += pulses(30, 3)
    eff += [(39, 'timeline', 'graft:done', None)]
    return {'length': 40, 'loop': 'once', 'native': True, 'feet': planted(), 'bones': bones, 'effects': eff}


def graft_refused():
    """Drive refused (nothing hit in the last 5 s, a Legendary gene, no Bio-Gel; 26 ticks): it raises the left forearm
    to its eye, sniffs, recoils (the growth shrinks, the eye shakes no twice), coughs a puff from both gill covers and
    wipes the forearm down its side."""
    bones = {
        'arm_l': {'rotation': [k(0, R()), k(6, R(x=60, z=-32), 'easeInOutCubic'), k(10, R(x=60, z=-32)),
                               k(13, R(x=-4, z=-10), 'easeOutCubic'), k(18, R(x=-4, z=-14), 'easeInOutSine'),
                               k(22, R(x=-2, z=-6), 'easeInOutSine'), k(26, R(), 'easeInOutSine')]},
        'forearm_l': {'rotation': [k(0, R()), k(6, R(x=-4), 'easeInOutCubic'), k(10, R(x=-4)),
                                   k(13, R(x=-70), 'easeOutCubic'), k(22, R(x=-60), 'easeInOutSine'), k(26, R(), 'easeInOutSine')]},
        'sensor': {'rotation': [k(0, R()), k(6, R(x=25, z=30), 'easeInOutCubic'), k(10, R(x=25, z=30)),
                                k(12, R(x=-25, z=-25), 'easeOutExpo'), k(14, R(x=-20, z=25), 'easeInOutSine'),
                                k(16, R(x=-20, z=-25), 'easeInOutSine'), k(18, R(x=-15, z=20), 'easeInOutSine'),
                                k(20, R(x=-10, z=-10), 'easeInOutSine'), k(24, R(), 'easeInOutSine')]},
        'growth': {'scale': [k(0, S(1)), k(10, S(1)), k(12, S(0.9), 'easeOutExpo'), k(16, S(0.92)),
                             k(18, S(1.04), 'easeOutQuad'), k(20, S(0.97), 'easeInOutSine'), k(24, S(1), 'easeInOutSine')]},
        'torso': {'rotation': [k(0, R()), k(8, R(x=-6, y=8), 'easeInOutCubic'), k(10, R(x=-6, y=8)),
                               k(12, R(x=14), 'easeOutExpo'), k(16, R(x=12)), k(18, R(x=-5), 'easeInOutSine'),
                               k(20, R(x=2), 'easeOutQuad'), k(24, R(), 'easeInOutSine')]},
        'hips': {'position': [k(0, P()), k(10, P()), k(12, P(y=-1.5, z=2.5), 'easeOutExpo'), k(16, P(y=-1.5, z=2.5)),
                              k(22, P(), 'easeInOutSine')]},
        'vent_l': {'rotation': [k(0, R()), k(17, R()), k(18, R(x=-45), 'easeOutExpo'), k(22, R(), 'easeInQuad')]},
        'vent_r': {'rotation': [k(0, R()), k(17, R()), k(18, R(x=-45), 'easeOutExpo'), k(22, R(), 'easeInQuad')]},
    }
    return {'length': 26, 'loop': 'once', 'native': True, 'feet': planted(), 'bones': bones,
            'effects': [(0, 'timeline', 'graft:refused', None), (7, 'sound', snd('sniff', 0.5, 1.0), None),
                        (12, 'sound', snd('retch_dry', 0.5, 1.0), None), (18, 'sound', snd('cough', 0.5, 1.1), None),
                        (19, 'particle', 'cloud', 'fx_vent_l'), (20, 'particle', 'cloud', 'fx_vent_r')]}


# ---- build: grown, then issued a hard hat ---------------------------------------------------------------------------
BUILD_SLOTS = [('legs', 104, ['socket_thigh_l', 'socket_thigh_r', 'socket_shin_l', 'socket_shin_r']),
               ('back', 112, ['socket_back']), ('core', 119, ['socket_core']),
               ('torso_0', 126, ['socket_torso_0']), ('torso_1', 133, ['socket_torso_1']),
               ('torso_2', 140, ['socket_torso_2']), ('arm_l', 147, ['socket_arm_l']), ('arm_r', 154, ['socket_arm_r']),
               ('sensor', 161, ['socket_sensor'])]


def build():
    """Grown more than built (220 ticks): delivered as an empty cage on folded legs, the growth a seed inside it, the
    forearms hanging limp, the hard hat held up and back. The Gantry feeds it Bio-Gel and it grows in four gulps
    (10-80, each an overshoot and a settle); it stands and lifts its forearms (82-100); the modules arrive by slot
    (104-166); the gills fill one by one (180-188); then health and safety: the hard hat comes down onto the empty
    collar (196-206, build:hat), it looks up at it, and parks."""
    PH = HA.PARK_HIPS * HA.SC
    grow = [k(0, S(0.15))]
    for i, (t, v) in enumerate([(10, 0.35), (30, 0.6), (50, 0.85), (70, 1.0)]):
        grow += [k(t - 4, S(grow[-1][1][0])), k(t, S(v + 0.06), 'easeOutCubic'), k(t + 4, S(v), 'easeInElastic', [3])]
    grow += [k(212, S(1)), k(216, S(1.04), 'easeInOutSine'), k(220, S(1), 'easeInOutSine')]
    bones = {
        'growth': {'scale': grow},
        'hips': {'position': [k(0, P(y=-9)), k(80, P(y=-9)), k(92, P(y=0.6), 'easeInOutCubic'), k(96, P(), 'easeInOutSine'),
                              k(210, P()), k(220, P(y=PH), 'easeInQuad')]},
        'torso': {'rotation': [k(0, R(x=-10)), k(80, R(x=-10)), k(94, R(), 'easeInOutCubic'),
                               k(212, R()), k(220, R(x=HA.PARK_TORSO), 'easeInOutSine')]},
        'forearm_l': {'rotation': [k(0, R(x=-90)), k(84, R(x=-90)), k(96, R(x=4), 'easeInOutCubic'), k(100, R(), 'easeInOutSine'),
                                   k(212, R()), k(220, R(x=HA.PARK_FOREARM), 'easeInOutSine')]},
        'forearm_r': {'rotation': [k(0, R(x=-90)), k(86, R(x=-90)), k(98, R(x=4), 'easeInOutCubic'), k(102, R(), 'easeInOutSine'),
                                   k(212, R()), k(220, R(x=HA.PARK_FOREARM), 'easeInOutSine')]},
        'sensor': {'rotation': [k(0, R(x=30)), k(70, R(x=30)), k(80, R(), 'easeInOutSine'),
                                k(204, R()), k(208, R(x=-35), 'easeOutCubic'), k(212, R(x=-35)), k(216, R(), 'easeInOutSine'),
                                k(217, R()), k(220, R(x=HA.PARK_SENSOR), 'easeInOutSine')]},
        'restraint': {'rotation': [k(0, R(x=HAT_OFF)), k(196, R(x=HAT_OFF)), k(205, R(x=-2), 'easeInQuad'),
                                   k(207, R(), 'easeInBounce')]},
    }
    bones.update(lamps([(0, 0), (90, 1), (212, 0)], [(0, 0), (90, 1), (212, 0)], [(0, 0), (80, 1), (212, 0)]))
    effects = [(0, 'timeline', 'build:feed', None)]
    for t in (10, 30, 50, 70):
        effects.append((t, 'sound', snd('squelch_gulp', 0.5, 1.1 - t / 200.0), None))
    effects += [(84, 'sound', snd('stand_creak', 0.5, 1.0), None), (92, 'timeline', 'build:stand', None)]
    for slot, T0, socks in BUILD_SLOTS:
        for sb in socks:
            o = HA.outward(sb)
            ch = bones.setdefault(sb, {})
            ch['scale'] = [k(0, S(0), interp='step'), k(T0, S(1), interp='step')]
            ch['position'] = [k(0, P(*[6 * c for c in o])), k(T0, P(*[6 * c for c in o])), k(T0 + 3, P(), 'easeInQuad'),
                              k(T0 + 4, P(*[1.0 * c for c in o]), 'easeOutQuad'), k(T0 + 5, P(), 'easeInQuad')]
        effects.append((T0 + 3, 'timeline', 'build:seat:%s' % slot, None))
    effects += pulses(180, 4)
    effects += [(197, 'sound', snd('servo', 0.4, 1.0), None), (206, 'timeline', 'build:hat', None),
                (207, 'sound', snd('hard_hat_clonk', 0.6, 1.0), None), (218, 'sound', snd('sigh', 0.4, 1.1), None)]
    return {'length': 220, 'loop': 'hold', 'native': True, 'feet': planted(), 'bones': bones, 'effects': effects,
            'pilot': False}


# ==================================================================================================================
def clips():
    own = {'idle': idle, 'parked': parked, 'walk': walk, 'boot': boot, 'idle_tic': idle_tic, 'build': build,
           'limp_l': lambda: limp('l'), 'limp_r': lambda: limp('r')}
    out = [(name, own[name]() if name in own else c) for name, c in SHARED() if name not in DROP]
    out += [('graft_l', graft('l')), ('graft_r', graft('r')), ('graft_refused', graft_refused())]
    return out


SHARED = HA.clips
HA.clips = clips

if __name__ == '__main__':
    HA.main()

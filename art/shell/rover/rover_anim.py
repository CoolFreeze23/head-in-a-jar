"""Rover (a Utility Shell on the wheeled skeleton): every clip, keyed in ticks, written into rover.bbmodel.

Source of truth for the clips. The keyframe helpers and GeckoLib easings are the Hopper's; road() and the build/bake
loop are the Wheelhouse's (art/mecha/wheelhouse/wheelhouse_anim.py), imported, so the wheeled frames key alike.

    python art/shell/rover/rover_anim.py                          # write the clips into the bbmodel
    python art/shell/rover/rover_anim.py --model X --preview OUT  # baked preview copy (the castors' code spin baked in)

Signs: as the Wheelhouse's. Rover specifics: restraint (the luggage strap) rot z + = flipped over to the left
(unbuckled); helm (the bell, on the push handle) rot z = its wobble; flag (the aerial) rot x + = streaming back, z = swaying; drawer
position z + = slid out of the back; steer_* rot y + = castor turned left.
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
sys.path.insert(0, os.path.join(MECHA, 'wheelhouse'))
import rover_geo as G  # noqa: E402
import wheelhouse_anim as WA  # noqa: E402
from hopper_anim import k, R, P, S, lamp, const, value_at, keyframe, bake, snd, nid  # noqa: E402,F401
from wheelhouse_anim import merge, body, road  # noqa: E402

MODEL = os.path.join(HERE, 'rover.bbmodel')
T = 20.0
# ridden pace from the release line: speed 0.38 (ShellEntity.java:54), full sync x 1.1
# (HostControl.java:67), RIDDEN_SCALE 0.5 (ShellEntity.java:68, applied HostControl.java:68), vanilla ground x 2.16
# = 0.45 blocks a tick; a measured 9.2 blocks a second is 0.46, which is what the loop is built for
V = 0.46 * 16                  # 7.36 px a tick
V_REV = V * 0.4
V_LIMP = V * 0.3
CORNERS = ['fl', 'fr', 'rl', 'rr']
PARK_Y = -0.6
PARK_STEER = {'fl': 20, 'fr': -12, 'rl': 6, 'rr': 32}       # a parked trolley's castors point any old way


def steer(vals, lead=None):
    d = {}
    for c in CORNERS:
        v = vals[c]
        d['steer_' + c] = {'rotation': v if isinstance(v, list) else const(R(y=v))}
    return d


def strap(keys):
    return {'restraint': {'rotation': [(t, [0, 0, o[0]], e, a, i) for (t, o, e, a, i) in keys]}}


def lamps(core=None, tail=None):
    d = {}
    if core is not None:
        d['lamp_core'] = {'scale': lamp(core)}
    if tail is not None:
        d['lamp_tail'] = {'scale': lamp(tail)}
    return d


def bell(t0, amp=15, n=10):
    """A bell rung on t0: a wobble decaying over n ticks."""
    keys = [k(0, R())] + ([k(t0, R())] if t0 > 0 else [])
    for i in range(1, n + 1):
        keys.append(k(t0 + i, R(z=round(amp * (1 if i % 2 else -1) * (1 - i / float(n + 1)), 2)), 'easeInOutSine'))
    return {'helm': {'rotation': keys}}


def aerial(keys):
    return {'flag': {'rotation': keys}}


# ==================================================================================================================
def parked():
    """Unpiloted: sat down 0.6 px on its springs, the castors pointing any old way, the strap buckled over the empty
    ring, lamps off. Alive: the pennant stirring, a castor that swivels a few degrees at 50 as if nudged."""
    st = {c: PARK_STEER[c] for c in CORNERS}
    st['rr'] = [k(0, R(y=32)), k(50, R(y=32)), k(53, R(y=26), 'easeOutQuad'), k(56, R(y=28), 'easeInOutSine'), k(80, R(y=32), 'easeInOutSine')]
    return {'length': 80, 'loop': 'loop', 'pilot': False, 'bones': merge(
        body([k(0, P(y=PARK_Y))], [k(0, R(x=-0.5))]), steer(st), strap([k(0, [0])]),
        aerial([k(0, R()), k(20, R(z=3), 'easeInOutSine'), k(45, R(z=-2), 'easeInOutSine'), k(80, R(), 'easeInOutSine')]),
        lamps([(0, 0)], [(0, 0)])),
        'effects': [(53, 'sound', snd('castor_squeak', 0.2, 1.2), None)]}


def board():
    """Controller "cockpit": the strap's buckle pops and it flips over to the left (1-6); the jar hops up onto the
    tray from behind (landing on 9); the tray dips 2 px under the weight and springs back; the strap swings back over
    the lid (contact 14) and the buckle clicks (15)."""
    b = merge({'jar_anchor': {'position': [k(0, P(y=-14, z=16)), k(3, P(y=-14, z=16)), k(7, P(y=5, z=5), 'easeOutQuad'),
                                           k(9, P(), 'easeInQuad'), k(10, P(y=0.3), 'easeOutQuad'), k(11, P(), 'easeInQuad')]}},
              strap([k(0, [0]), k(1, [-4], 'easeOutQuad'), k(6, [110], 'easeOutCubic'), k(8, [104], 'easeInOutSine'), k(10, [104]),
                     k(11, [108], 'easeOutQuad'), k(14, [0], 'easeInQuad'), k(15, [-3], 'easeOutQuad'), k(17, [0], 'easeInQuad')]),
              body([k(0, P()), k(9, P()), k(10, P(y=-2), 'easeInQuad'), k(13, P(y=0.6), 'easeOutQuad'), k(16, P(), 'easeInOutSine')]))
    return {'length': 22, 'loop': 'hold', 'bones': b,
            'effects': [(1, 'sound', snd('buckle_pop', 0.5, 1.2), None), (9, 'sound', snd('jar_seat', 0.5, 1.1), None),
                        (10, 'sound', snd('tray_spring', 0.4, 1.0), None), (14, 'sound', snd('strap_slap', 0.5, 1.0), None),
                        (15, 'sound', snd('buckle_click', 0.5, 1.1), None)]}


# Where the jar lands: ShellEntity.clearOf (release branch, entity/ShellEntity.java:112-132) tries behind the body
# first, then its right (Direction.getClockWise of its facing), then its left, then in front, each 1.13 blocks out
# (half the Rover's width + half the jar + 0.1) = 18 px.
EXIT = 18.0
EXITS = {'behind': (0.0, EXIT), 'r': (EXIT, 0.0), 'l': (-EXIT, 0.0), 'front': (0.0, -EXIT)}


def get_off(where='behind'):
    """Controller "cockpit" (getting off, sneak), one clip per place clearOf can put the jar: get_off (behind, the
    usual), get_off_r, get_off_l, get_off_front. The strap flips open; the tray squats (the wind-up); the jar hops up
    clear of the ring and of anything on that side (the push handle and bell behind, the open strap on the left: it
    rises 6 px), arcs out, tipping that way, and drops to the ground 18 px out (hidden from 12: the jar is a player
    again there, where the code put it). The tray tips 8 degrees toward the exit under the push and springs back on an
    elastic; the strap swings closed over the empty ring."""
    dx, dz = EXITS[where]
    ux, uz = dx / EXIT, dz / EXIT
    hop = 6.0 if where in ('behind', 'l') else 4.0
    jar_rot = R(x=20 * uz, z=-20 * ux)
    jar_rot2 = R(x=40 * uz, z=-40 * ux)
    tip = R(x=8 * uz, z=-8 * ux)        # the tray tips toward the exit (x + lifts the nose: the back goes down)
    b = merge({'jar_anchor': {'position': [k(0, P()), k(3, P()), k(4, P(y=-0.5), 'easeOutQuad'),
                                           k(6, P(x=ux * 4, y=hop, z=uz * 4), 'easeOutCubic'),
                                           k(9, P(x=dx, y=hop - 1, z=dz), 'easeInOutSine'),
                                           k(12, P(x=dx, y=-14, z=dz), 'easeInQuad')],
                              'rotation': [k(0, R()), k(5, R()), k(9, jar_rot, 'easeInOutSine'), k(12, jar_rot2, 'easeInQuad')],
                              'scale': [k(0, S(1), interp='step'), k(12, S(0), interp='step')]}},
              strap([k(0, [0]), k(1, [-4], 'easeOutQuad'), k(5, [108], 'easeOutCubic'), k(14, [104]), k(18, [0], 'easeInQuad'),
                     k(19, [-3], 'easeOutQuad'), k(20, [0], 'easeInQuad')]),
              body([k(0, P()), k(4, P(y=-0.8), 'easeOutQuad'), k(6, P(y=0.3), 'easeOutCubic'), k(14, P(), 'easeInOutSine')],
                   [k(0, R()), k(4, R(x=-1.5 * uz, z=1.5 * ux), 'easeOutQuad'), k(7, tip, 'easeInOutCubic'), k(9, tip),
                    k(15, R(), 'easeInElastic', [4])]))
    return {'length': 20, 'loop': 'hold', 'bones': b,
            'effects': [(1, 'sound', snd('buckle_pop', 0.5, 1.2), None), (5, 'sound', snd('jar_hop', 0.4, 1.1), None),
                        (7, 'sound', snd('tray_tip', 0.4, 1.0), None), (12, 'sound', snd('jar_land', 0.4, 1.0), None),
                        (15, 'sound', snd('tray_spring', 0.4, 1.0), None), (18, 'sound', snd('strap_slap', 0.4, 1.0), None)]}


def idle():
    """Its little motor running: a fine buzz, the pennant swaying, the castors straightening from parked."""
    return {'length': 40, 'loop': 'loop', 'bones': merge(
        body([k(t, P(y=0.1 if t % 2 else 0)) for t in range(41)]),
        aerial([k(0, R()), k(20, R(z=4), 'easeInOutSine'), k(40, R(), 'easeInOutSine')])),
        'effects': []}


def idle_tic():
    """Every 200-300 idle ticks: it rings its own bell, twice, and the aerial jiggles."""
    b = merge(bell(4, 16, 8), aerial([k(0, R()), k(5, R(z=6), 'easeOutQuad'), k(9, R(z=-4), 'easeInOutSine'), k(14, R(), 'easeInOutSine')]),
              body(None, [k(0, R()), k(4, R(z=1), 'easeOutQuad'), k(8, R(), 'easeInOutSine')]))
    b2 = bell(16, 12, 8)['helm']['rotation']
    b['helm']['rotation'] = sorted({kk[0]: kk for kk in b['helm']['rotation'] + b2[2:]}.values(), key=lambda x: x[0])
    return {'length': 30, 'loop': 'once', 'bones': b,
            'effects': [(4, 'sound', snd('bell_ding', 0.5, 1.2), None), (16, 'sound', snd('bell_ding', 0.45, 1.25), None)]}


def drive():
    """Loop at the design speed (7.36 px a tick: playback = measured / 0.46 blocks a tick). The castors ride small
    bumps, the rear a tick after the front; on top a 4-tick chatter of 1.5 degrees in pitch, the drawer rattling 0.5 px,
    the bell flicking, the strap rattling; the aerial streams back 20 degrees and flutters; the castors' spin is code's."""
    n = 8
    bones = road(n, [('fl', 1), ('rl', 2), ('fr', 4), ('rr', 5)], 0.5, amp=0.5, buzz=0.15)
    bones.pop('arm_l', None)
    chat = [0, 1.5, 0, -1.5]
    bones['body']['rotation'] = [(t, [v[0] + chat[t % 4], v[1], v[2]], e, a, i) for (t, v, e, a, i) in bones['body']['rotation']]
    bones.update(aerial([k(t, R(x=20 + (3 if t % 2 else -3) * (0 < t < n), z=(1 if t % 4 < 2 else -1) * (0 < t < n))) for t in range(n + 1)]))
    bones['drawer'] = {'position': [k(t, P(z=0.5 if t % 4 == 1 else 0)) for t in range(n + 1)]}
    bones['helm'] = {'rotation': [k(t, R(z=10 if t == 2 else (-6 if t == 3 else 0))) for t in range(n + 1)]}
    return {'length': n, 'loop': 'loop', 'bones': bones, 'effects': [(2, 'sound', snd('bell_tick', 0.15, 1.4), None)]}


def reverse():
    """Loop backing (assumed 0.4 x: playback = measured backward / 0.18 blocks a tick); the aerial leans forward."""
    n = 10
    bones = road(n, [('rl', 1), ('fl', 2), ('rr', 6), ('fr', 7)], -0.5, amp=0.4)
    bones.pop('arm_l', None)
    bones.update(aerial(const(R(x=-8))))
    return {'length': n, 'loop': 'loop', 'bones': bones, 'effects': [(0, 'sound', snd('reverse_beep', 0.2, 1.4), None)]}


def drive_start():
    return {'length': 8, 'loop': 'once', 'bones': merge(
        body([k(0, P()), k(4, P(y=-0.5), 'easeOutCubic'), k(8, P(), 'easeInOutSine')],
             [k(0, R()), k(1, R(x=-0.6), 'easeOutQuad'), k(4, R(x=3), 'easeOutCubic'), k(8, R(x=0.5), 'easeInOutSine')]),
        aerial([k(0, R()), k(2, R(x=-5), 'easeOutQuad'), k(6, R(x=24), 'easeOutCubic'), k(8, R(x=20), 'easeInOutSine')]),
        {'drawer': {'position': [k(0, P()), k(3, P(z=0.6), 'easeOutQuad'), k(6, P(), 'easeInQuad')]}}),
        'effects': [(0, 'sound', snd('motor_whine', 0.4, 1.2), None)]}


def brake():
    """Once, stopping: the nose dives 5 degrees in 2 ticks, the drawer slides out 2 px with the inertia and back, the
    aerial whips forward and wobbles."""
    return {'length': 14, 'loop': 'once', 'bones': merge(
        body([k(0, P()), k(2, P(y=-0.6), 'easeOutQuad'), k(7, P(y=0.2), 'easeInOutSine'), k(12, P(), 'easeInOutSine')],
             [k(0, R(x=0.5)), k(2, R(x=-5), 'easeOutQuad'), k(4, R(x=-4.5)), k(8, R(x=1.5), 'easeInOutSine'), k(12, R(), 'easeInOutSine')]),
        {'drawer': {'position': [k(0, P()), k(3, P(z=-2), 'easeOutQuad'), k(6, P(), 'easeInQuad'), k(7, P(z=-0.4), 'easeOutQuad'), k(9, P(), 'easeInQuad')]}},
        aerial([k(0, R(x=20)), k(2, R(x=-25), 'easeOutQuad'), k(12, R(), 'easeInElastic', [4])])),
        'effects': [(0, 'sound', snd('castor_squeal', 0.4, 1.3), None), (6, 'sound', snd('drawer_knock', 0.3, 1.1), None)]}


def steer_clip(direction):
    """Controller "steer" (steer_*, helm, flag, root and jar_anchor): the castors swivel 35 degrees into the turn and
    trail (the rear 12 the other way); the whole trolley rolls 5 degrees to the outside of the turn on its outer
    castors; the aerial swings 15 degrees outward; the jar follows a tick late against its strap. Each comes in over 3
    ticks (easeOutCubic) and settles over 2 (easeOutBack [0.5])."""
    s = 1 if direction == 'l' else -1
    d = {}
    for c, a in (('fl', 35), ('fr', 35), ('rl', -12), ('rr', -12)):
        d['steer_' + c] = {'rotation': [k(0, R()), k(3, R(y=(a + 4) * s), 'easeOutCubic'), k(5, R(y=a * s), 'easeOutBack', [0.5])]}
    d['root'] = {'rotation': [k(0, R()), k(3, R(z=-5.5 * s), 'easeOutCubic'), k(5, R(z=-5 * s), 'easeOutBack', [0.5])],
                 'position': [k(0, P()), k(3, P(y=0.55), 'easeOutCubic'), k(5, P(y=0.5), 'easeOutBack', [0.5])]}
    d['flag'] = {'rotation': [k(0, R()), k(4, R(z=-17 * s), 'easeOutCubic'), k(6, R(z=-15 * s), 'easeOutBack', [0.5])]}
    d['jar_anchor'] = {'rotation': [k(0, R()), k(1, R()), k(4, R(z=-3 * s), 'easeOutCubic'), k(6, R(z=-2 * s), 'easeOutBack', [0.5])]}
    return {'length': 6, 'loop': 'hold', 'bones': d, 'effects': [(1, 'sound', snd('castor_squeak', 0.2, 1.3), None)]}


def turn(direction):
    s = 1 if direction == 'l' else -1
    d = {}
    for c, a in (('fl', 35), ('fr', 35), ('rl', -35), ('rr', -35)):
        d['steer_' + c] = {'rotation': [k(0, R(y=a * s)), k(5, R(y=(a + 3) * s), 'easeInOutSine'), k(10, R(y=a * s), 'easeInOutSine')]}
    return {'length': 10, 'loop': 'loop', 'bones': d, 'effects': [(0, 'sound', snd('castor_squeak', 0.2, 1.2), None)]}


def crash():
    """Once, into a wall at speed (contact on 0). The whole trolley pitches 8 degrees nose-down about its front castors
    (the rear castors lift 2 px and land on 4, easeInQuad) and bounces 1.5 px back off the wall; the rubber bumper is
    shoved in 1.5 px; the jar lags a tick and leans 3 degrees against its strap; on the rebound the drawer flies out of
    the back 4 px and slams home with a bounce; the bell rings; the aerial whips forward and wobbles on an elastic; the
    castors shimmy and settle."""
    jit = lambda c, ph: [k(0, R())] + [k(t, R(y=round((14 if (t + ph) % 2 else -14) * (1 - t / 15.0), 2)), 'easeInOutSine') for t in range(4, 15, 2)] + [k(16, R(), 'easeInOutSine')]
    b = merge(
        {'root': {'rotation': [k(0, R()), k(1, R(x=-8), 'easeOutExpo'), k(2, R(x=-7)), k(4, R(), 'easeInQuad'), k(6, R(x=-1), 'easeOutQuad'),
                               k(9, R(), 'easeInOutSine')],
                  'position': [k(0, P()), k(1, P(y=1, z=0.5), 'easeOutExpo'), k(3, P(y=0.8, z=1.5), 'easeOutQuad'), k(4, P(z=1.5), 'easeInQuad'),
                               k(8, P(z=1.5))]},
         'jar_anchor': {'rotation': [k(0, R()), k(1, R()), k(2, R(x=-3), 'easeOutQuad'), k(5, R(x=1), 'easeInOutSine'), k(8, R(), 'easeInOutSine')]},
         'buffer': {'position': [k(0, P()), k(1, P(z=1.5), 'easeInExpo'), k(3, P(z=1.5)), k(6, P(z=-0.4), 'easeOutCubic'), k(10, P(), 'easeInElastic', [3])]},
         'drawer': {'position': [k(0, P()), k(3, P()), k(4, P(z=4), 'easeOutExpo'), k(5, P(z=4.3)), k(8, P(), 'easeInQuad'), k(9, P(z=1), 'easeOutQuad'),
                                 k(11, P(), 'easeInQuad')]},
         'restraint': {'rotation': [k(0, R()), k(2, R(z=3), 'easeOutQuad'), k(4, R(z=-1.5), 'easeInOutSine'), k(7, R(), 'easeInOutSine')]}},
        aerial([k(0, R(x=20)), k(2, R(x=-40), 'easeOutExpo'), k(16, R(), 'easeInElastic', [4])]),
        bell(1, 18, 10),
        {'steer_' + c: {'rotation': jit(c, i)} for i, c in enumerate(CORNERS)})
    return {'length': 22, 'loop': 'once', 'bones': b,
            'effects': [(0, 'timeline', 'crash:bonk', None), (1, 'sound', snd('bell_ding', 0.6, 1.0), None),
                        (4, 'sound', snd('drawer_slide', 0.4, 1.2), None), (8, 'sound', snd('drawer_slam', 0.5, 1.0), None),
                        (10, 'sound', snd('castor_rattle', 0.4, 1.1), None)]}


def sprint_start():
    """The ability (Speed III for 100 ticks): it squats on its back castors, the nose lifts 4 degrees, the aerial lies
    back flat and the bell gives one proud ding; then the drive plays faster (code's playback)."""
    return {'length': 14, 'loop': 'once', 'bones': merge(
        body([k(0, P()), k(2, P(y=0.3), 'easeOutQuad'), k(6, P(y=-0.8), 'easeInOutCubic'), k(10, P(y=-0.3), 'easeInOutSine')],
             [k(0, R()), k(2, R(x=-1), 'easeOutQuad'), k(6, R(x=4), 'easeInOutCubic'), k(10, R(x=2), 'easeInOutSine')]),
        aerial([k(0, R(x=20)), k(3, R(x=10), 'easeOutQuad'), k(7, R(x=55), 'easeOutCubic'), k(10, R(x=50), 'easeInOutSine')]),
        bell(6, 12, 6)),
        'effects': [(2, 'sound', snd('motor_rev', 0.5, 1.3), None), (6, 'sound', snd('bell_ding', 0.5, 1.3), None),
                    (7, 'timeline', 'sprint:go', None)]}


def hurt():
    return {'length': 10, 'loop': 'once', 'bones': merge(
        {'root': {'rotation': [k(0, R()), k(1, R(x=2, z=-4), 'easeOutExpo'), k(3, R(x=2, z=-4)), k(6, R(x=-0.6, z=1.2), 'easeInOutSine'),
                               k(10, R(), 'easeInOutSine')]}}, bell(1, 10, 6)),
        'effects': [(1, 'particle', 'electric_spark', 'fx_hull')]}


def limp():
    """Loop, the jar's charge nearly gone (playback = measured / 0.14): a crawl, the front-left castor shimmying 30
    degrees, the tray sagging a pixel to that corner, the lamp out, the aerial drooping."""
    n = 16
    b = merge(body([k(0, P(y=-1)), k(8, P(y=-1.3), 'easeInOutSine'), k(16, P(y=-1), 'easeInOutSine')], const(R(x=-1, z=2))),
              {'steer_fl': {'rotation': [k(0, R(y=-20)), k(4, R(y=25), 'easeInOutSine'), k(8, R(y=-25), 'easeInOutSine'),
                                         k(12, R(y=20), 'easeInOutSine'), k(16, R(y=-20), 'easeInOutSine')]}},
              aerial(const(R(x=5, z=25))), lamps([(0, 0)], [(0, 1)]))
    return {'length': n, 'loop': 'loop', 'bones': b, 'effects': [(4, 'sound', snd('castor_squeak', 0.3, 0.8), None),
                                                               (12, 'sound', snd('motor_stutter', 0.3, 0.8), None)]}


def destroyed():
    """Collapse (30 HP gone): sparks; the lamp dies; the rear-left castor snaps off and rolls away (8-20), the tray
    drops onto that corner (contact 12); the strap's buckle bursts and it flops open; the drawer falls half out; the
    aerial droops; smoke. The jar has already been thrown clear by the host code (it is a death of the body)."""
    b = merge(
        {'steer_rl': {'position': [k(0, P()), k(8, P()), k(10, P(x=-3), 'easeOutQuad'), k(20, P(x=-6, y=-1.5, z=10), 'easeInQuad')],
                      'rotation': [k(0, R()), k(8, R()), k(20, R(z=70, y=20), 'easeInQuad'), k(22, R(z=64, y=20), 'easeOutQuad'),
                                   k(24, R(z=70, y=20), 'easeInQuad')]},
         'wheel_rl': {'rotation': [k(0, R()), k(8, R()), k(20, R(x=-300), 'easeOutCubic')]},
         'drawer': {'position': [k(0, P()), k(12, P()), k(16, P(z=5, y=-1), 'easeOutCubic')]},
         'helm': {'rotation': [k(0, R()), k(12, R()), k(14, R(z=25), 'easeOutQuad'), k(20, R(z=40), 'easeInQuad')]}},
        strap([k(0, [0]), k(10, [0]), k(13, [60], 'easeOutExpo'), k(17, [100], 'easeInQuad'), k(21, [100], 'easeInBounce', [0.3])]),
        body([k(0, P()), k(8, P()), k(12, P(y=-2.5), 'easeInQuad'), k(14, P(y=-2), 'easeOutQuad'), k(16, P(y=-2.5), 'easeInQuad')],
             [k(0, R()), k(8, R()), k(12, R(x=4, z=8), 'easeInQuad'), k(14, R(x=3.5, z=7), 'easeOutQuad'), k(16, R(x=4, z=8), 'easeInQuad')]),
        aerial([k(0, R()), k(4, R()), k(16, R(z=50, x=10), 'easeInQuad')]),
        lamps([(0, 1), (3, 0), (5, 1), (9, 0)], [(0, 1), (14, 0)]))
    return {'length': 30, 'loop': 'hold', 'bones': b, 'pilot': False,
            'effects': [(0, 'particle', 'electric_spark', 'fx_hull'), (8, 'sound', snd('castor_snap', 0.6, 1.1), None),
                        (12, 'timeline', 'destroyed:thud', None), (13, 'sound', snd('buckle_burst', 0.5, 1.0), None),
                        (20, 'sound', snd('castor_clatter', 0.4, 1.2), None), (24, 'particle', 'large_smoke', 'fx_hull')]}


def clips():
    return [('parked', parked()), ('board', board()), ('get_off', get_off()), ('get_off_r', get_off('r')), ('get_off_l', get_off('l')),
            ('get_off_front', get_off('front')), ('idle', idle()), ('idle_tic', idle_tic()),
            ('drive_start', drive_start()), ('drive', drive()), ('reverse', reverse()), ('brake', brake()),
            ('steer_l', steer_clip('l')), ('steer_r', steer_clip('r')), ('turn_l', turn('l')), ('turn_r', turn('r')),
            ('crash', crash()), ('sprint_start', sprint_start()), ('hurt', hurt()), ('limp', limp()), ('destroyed', destroyed())]


SPIN = {'drive': lambda t: V * t, 'reverse': lambda t: -V_REV * t, 'limp': lambda t: V_LIMP * t,
        'drive_start': lambda t: V * min(t, 8) ** 2 / 16.0 + V * max(0, t - 8), 'brake': lambda t: V * (min(t, 4) - min(t, 4) ** 2 / 8.0),
        'sprint_start': lambda t: V * t}


def build_anims(model, baked=False):
    WA.clips = clips
    saved = WA.SPIN, WA.BRAKE_LIT, WA.G
    WA.SPIN, WA.BRAKE_LIT, WA.G = SPIN, {}, G
    try:
        anims = WA.build_anims(model, baked)
    finally:
        WA.SPIN, WA.BRAKE_LIT, WA.G = saved
    if baked:
        for a in anims:
            a['animators'] = {u: an for u, an in a['animators'].items() if an.get('name') != 'lamp_brake' or True}
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

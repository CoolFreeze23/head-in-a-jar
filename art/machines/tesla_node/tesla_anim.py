"""Tesla Node: every clip, keyed by game tick, written into tesla_node.bbmodel (and the review copy).

Source of truth for the Tesla Node's animations; hand edits in Blockbench are overwritten by the next run.

    python art/machines/tesla_node/tesla_anim.py            # clips + review copy + the code-motion report

Keyed: the torus's POSITION only (the perk lift, the link hop, the float while sending, the stop, the idle hover, the
refusal shake). CODE, never keyed: the torus's rotation (the spin while sending, chased) and the pointer's rotation
(the aim, chased with a spring; the look-around sweep after a perk; the head-shake after a refusal). `simulate_*`
below is that code written out in Python, per game tick, exactly as the block entity's client tick must run it; the
review copy gets `_review_*` clips baked from it, one key per tick, for renders only.

Signs (Blockbench = GeckoLib, as displayed): rotY +a turns +Z toward +X, so the pointer at yaw 0 holds the ball at +Z
(the back) and at yaw +90 at +X (the machine's right, the viewer's left from the front).
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
if not os.path.exists(os.path.join(HERE, '..', 'kit', 'kit.py')):
    sys.path.insert(0, 'build/machines-review/scratch-tesla_node')
import kit  # noqa: E402
from kit import k, R  # noqa: E402
import tesla_geo as geo  # noqa: E402

m = geo.model()
P = lambda y=0, x=0: R(x=x, y=y)  # noqa: E731
T = 'torus'

# ---------------------------------------------------------------------------------------------------------------------
# Keyed clips (torus position). The torus floats on its coil's field: 1 px over the collar at rest, so it can dip
# half a pixel (never touches), lift 2 px when it perks up and ride 1.5 px higher while it sends.
# ---------------------------------------------------------------------------------------------------------------------
# perk 36 (hold): the sender's first wrench click (block event `perk`). A half-pixel crouch (2), up 3 px with a 10 %
# overshoot (7, easeOutBack: ears up; v2 3 px, showing the terminal post under it), held while the pointer looks round (code sweep, ticks 6-32), down onto its field
# (31, easeInQuad), a 0.3 px bounce (33), settled (35).
PERK = {'length': 36, 'loop': 'hold',
        'bones': {T: {'position': [k(0, P()), k(2, P(-0.5), 'easeOutQuad'), k(7, P(3), 'easeOutBack', [1]),
                                   k(26, P(3)), k(31, P(0), 'easeInQuad'), k(33, P(0.3), 'easeOutQuad'),
                                   k(35, P(0), 'easeInQuad')]}},
        'effects': [(0, 'sound', 'headinajar:tesla.perk|0.5|1.0', None),
                    (7, 'sound', 'headinajar:tesla.look|0.3|1.0', None),
                    (31, 'sound', 'headinajar:tesla.settle|0.3|1.0', None)]}

# link 24 (hold): the link is made (the sender's `target`, or the receiver's `source`, changes to a node). Ticks 0-15
# the pointer swings onto its partner (code, the spring); the torus waits, then crouches half a pixel (14, held 15),
# and jumps 1.5 px as the first arc strikes (17, easeOutCubic), drops back onto its field (20, easeInQuad), bounces a
# quarter pixel (22) and settles (24). The arc should first be drawn at tick 16 (the zap).
LINK = {'length': 24, 'loop': 'hold',
        'bones': {T: {'position': [k(0, P()), k(13, P()), k(14, P(-0.5), 'easeOutQuad'), k(15, P(-0.5)),
                                   k(17, P(1.5), 'easeOutCubic'), k(20, P(0), 'easeInQuad'),
                                   k(22, P(0.25), 'easeOutQuad'), k(24, P(0), 'easeInQuad')]}},
        'effects': [(0, 'sound', 'headinajar:tesla.swing|0.4|1.0', None),
                    (16, 'sound', 'headinajar:tesla.link_zap|0.6|1.0', None),
                    (17, 'particle', 'minecraft:electric_spark', 'crown')]}

# unlink 16 (hold): the link is broken (sneak-use with the wrench, or the partner gone). The torus lets go upward a
# little (3), sinks half a pixel (8, easeInQuad), settles (13); the pointer swings back to rest (code).
UNLINK = {'length': 16, 'loop': 'hold',
          'bones': {T: {'position': [k(0, P()), k(3, P(0.75), 'easeOutQuad'), k(8, P(-0.5), 'easeInQuad'),
                                     k(13, P(0), 'easeInOutSine')]}},
          'effects': [(0, 'sound', 'headinajar:tesla.unlink|0.4|1.0', None)]}

# send 40 (loop) while `sending` and rate >= 3: the torus rides 1.5 px high (the gap under it 2.5 px) and hovers a
# quarter pixel on a 20-tick breath; the spin is code. Entered with a 5-tick controller blend from rest.
SEND = {'length': 40, 'loop': 'loop',
        'bones': {T: {'position': [k(0, P(1.5)), k(10, P(1.75), 'easeInOutSine'), k(20, P(1.5), 'easeInOutSine'),
                                   k(30, P(1.75), 'easeInOutSine'), k(40, P(1.5), 'easeInOutSine')]}},
        'effects': []}

# send_low 40 (loop) while `sending` and rate 1-2 (the brown-out): it rides only half a pixel up and judders, a
# quarter pixel up then a sudden half-pixel drop twice a loop, with a spark at the coil on each drop.
SEND_LOW = {'length': 40, 'loop': 'loop',
            'bones': {T: {'position': [k(0, P(0.5)), k(6, P(0.75), 'easeInOutSine'), k(8, P(0.25), 'easeInQuad'),
                                       k(14, P(0.5), 'easeInOutSine'), k(20, P(0.5)), k(26, P(0.75), 'easeInOutSine'),
                                       k(28, P(0.25), 'easeInQuad'), k(34, P(0.5), 'easeInOutSine'),
                                       k(40, P(0.5))]}},
            'effects': [(8, 'particle', 'minecraft:electric_spark', 'coil'),
                        (28, 'particle', 'minecraft:electric_spark', 'coil')]}

# stop 12 (hold): sending ends (target full, buffer empty, link lost). The field collapses: the torus falls from its
# float (5, easeInQuad) half a pixel below rest, bounces (8), settles (11); the spin coasts down in code (about 50 ticks).
STOP = {'length': 12, 'loop': 'hold',
        'bones': {T: {'position': [k(0, P(1.5)), k(5, P(-0.5), 'easeInQuad'), k(8, P(0.25), 'easeOutQuad'),
                                   k(11, P(0), 'easeInOutSine')]}},
        'effects': [(0, 'sound', 'headinajar:tesla.wind_down|0.4|1.0', None)]}

# idle 80 (loop): linked, charged, not sending, within 16 blocks: the torus hovers half a pixel on a 4-second breath.
IDLE = {'length': 80, 'loop': 'loop',
        'bones': {T: {'position': [k(0, P()), k(40, P(0.5), 'easeInOutSine'), k(80, P(0), 'easeInOutSine')]}},
        'effects': []}

# refuse 8 (block event 1, any wrench refusal at this node): the house rhythm on the torus, side to side as seen from
# the front, half a pixel (v2: the terminal post in the hole leaves 0.26 px at the worst angle for more); the read is
# the pointer's head-shake in code (head_shake below: two shakes of 40 degrees, the ball 3.5 px each way).
REFUSE = {'length': 8, 'loop': 'once',
          'bones': {T: {'position': [k(0, P()), k(2, P(x=0.5), 'easeInOutSine'), k(4, P(x=-0.5), 'easeInOutSine'),
                                     k(6, P(x=0.5), 'easeInOutSine'), k(8, P(), 'easeInOutSine')]}},
          'effects': [(0, 'sound', 'headinajar:machine.rattle|0.4|1.0', None)]}

CLIPS = [('perk', PERK), ('link', LINK), ('unlink', UNLINK), ('send', SEND), ('send_low', SEND_LOW),
         ('stop', STOP), ('idle', IDLE), ('refuse', REFUSE)]

# ---------------------------------------------------------------------------------------------------------------------
# Code motion, per game tick (the block entity's client tick runs exactly this; draw with lerp(partial, prev, now))
# ---------------------------------------------------------------------------------------------------------------------
REST_YAW = 45.0          # unlinked aim: the ball at the back right (+X, +Z): off the axis, so the node is no pawn
SPRING_K, SPRING_D = 0.15, 0.5   # vel = vel * SPRING_D + SPRING_K * err; about 7 % overshoot, settled in ~15 ticks
MAX_VEL = 20.0           # degrees a tick
SWEEP = (6, 32, 100.0)   # perk look-round: from tick 6 to 32 the aim is base + 100 sin(2 pi (t - 6) / 26)
SPIN_FULL = 9.0          # torus degrees a tick at rate 4 (a turn per 40 ticks)
SPIN_CHASE = 0.0625      # M9


def wrap(a):
    return (a + 180.0) % 360.0 - 180.0


def sweep_offset(t):
    a, b, amp = SWEEP
    return amp * math.sin(2 * math.pi * (t - a) / (b - a)) if a <= t < b else 0.0


def head_shake(t):
    """Degrees added to the pointer's drawn yaw t ticks after a refusal (not through the spring): two clear shakes,
    40 degrees then about 27, settled by tick 12."""
    return 40.0 * math.sin(2 * math.pi * t / 6.0) * (1 - t / 12.0) if 0 <= t < 12 else 0.0


def simulate_aim(aims, yaw, vel=0.0):
    out = [yaw]
    for a in aims:
        err = wrap(a - yaw)
        vel = max(-MAX_VEL, min(MAX_VEL, vel * SPRING_D + SPRING_K * err))
        yaw += vel
        out.append(yaw)
    return out


def simulate_spin(targets, speed=0.0, angle=0.0):
    out = [(speed, angle)]
    for tg in targets:
        speed += (tg - speed) * SPIN_CHASE
        angle += speed
        out.append((speed, angle))
    return out


def clip_track(clip, n):
    """The clip's torus position, eased, per tick 0..n (looped clips wrap)."""
    keys = clip['bones'][T]['position']
    per = {t: v for (t, v, e, a, i) in kit.bake(keys)}
    L = clip['length']
    out = []
    for t in range(n + 1):
        tt = t % L if clip['loop'] == 'loop' and t >= L else min(t, max(per))
        out.append(per.get(tt, per[max(x for x in per if x <= tt)]))
    return out


def review(name, n, pos, yaw, spin=None):
    """A review clip: torus position per tick, torus spin, pointer local yaw = aim - spin (it rides the torus)."""
    spin = spin or [0.0] * (n + 1)
    tk = [k(t, [round(c, 4) for c in pos[t]]) for t in range(n + 1)]
    tr = [k(t, R(y=round(spin[t], 3))) for t in range(n + 1)]
    pr = [k(t, R(y=round(yaw[t] - spin[t], 3))) for t in range(n + 1)]
    return (name, {'length': n, 'loop': 'hold', 'review': True,
                   'bones': {T: {'position': tk, 'rotation': tr}, 'pointer': {'rotation': pr}}, 'effects': []})


LINK_AIM = -120.0        # the review's partner: in front, to the machine's left (the viewer's right)
zero = lambda n: [[0, 0, 0]] * (n + 1)  # noqa: E731

perk_yaw = simulate_aim([REST_YAW + sweep_offset(t) for t in range(1, 37)], REST_YAW)
link_yaw = simulate_aim([LINK_AIM] * 24, REST_YAW)
unlink_yaw = simulate_aim([REST_YAW] * 16, LINK_AIM)
spinup = simulate_spin([SPIN_FULL] * 48)
coast = simulate_spin([0.0] * 60, speed=SPIN_FULL)
brown = simulate_spin([SPIN_FULL / 4] * 40, speed=SPIN_FULL / 4)
full = [SPIN_FULL * t for t in range(41)]

send_pos = clip_track(SEND, 48)
blend = [[0, min(1.0, t / 5.0) * send_pos[t][1], 0] for t in range(49)]   # the 5-tick controller blend in

REVIEW = [
    review('_review_rest', 1, zero(1), [REST_YAW] * 2),
    review('_review_perk', 36, clip_track(PERK, 36), perk_yaw),
    review('_review_link', 24, clip_track(LINK, 24), link_yaw),
    review('_review_unlink', 16, clip_track(UNLINK, 16), unlink_yaw),
    review('_review_spinup', 48, blend, [LINK_AIM] * 49, [a for s, a in spinup]),
    review('_review_send', 40, clip_track(SEND, 40), [LINK_AIM] * 41, full),
    review('_review_brownout', 40, clip_track(SEND_LOW, 40), [LINK_AIM] * 41, [a for s, a in brown]),
    review('_review_stop', 60, clip_track(STOP, 60), [LINK_AIM] * 61, [a for s, a in coast]),
    review('_review_idle', 80, clip_track(IDLE, 80), [LINK_AIM] * 81),
    review('_review_refuse', 12, clip_track(REFUSE, 12), [REST_YAW + head_shake(t) for t in range(13)]),
    # the yaw's sign: +90 puts the ball at +X (the machine's right)
    review('_review_yaw90', 1, zero(1), [90.0] * 2),
]


def ball(yaw, lift=0.0):
    r = math.radians(yaw)
    return (geo.BALL_R * math.sin(r), geo.BALL_Y + lift, geo.BALL_R * math.cos(r))


def report():
    xs = [ball(y)[0] for y in perk_yaw]
    print('perk: yaw %s' % ', '.join('t%d %.0f' % (t, perk_yaw[t]) for t in range(0, 37, 3)))
    print('  ball x from %.2f to %.2f px (travel %.2f px across the front), ball lifted 3 px' % (
        min(xs), max(xs), max(xs) - min(xs)))
    print('link to %.0f: yaw %s' % (LINK_AIM, ', '.join('t%d %.0f' % (t, link_yaw[t]) for t in range(0, 25, 2))))
    print('  peak overshoot %.1f deg' % (min(link_yaw) - LINK_AIM))
    b0, b1 = ball(REST_YAW), ball(LINK_AIM)
    print('  ball centre %s -> %s: chord %.1f px' % (tuple(round(v, 2) for v in b0), tuple(round(v, 2) for v in b1),
                                                     math.dist(b0, b1)))
    print('spin-up speeds: %s' % ', '.join('t%d %.1f' % (t, spinup[t][0]) for t in (0, 8, 16, 24, 32, 48)))
    print('coast speeds: %s' % ', '.join('t%d %.2f' % (t, coast[t][0]) for t in (0, 10, 20, 30, 40, 50, 60)))
    print('head shake: %s' % ', '.join('%.1f' % head_shake(t) for t in range(13)))


if __name__ == '__main__':
    n = kit.write_clips(geo.PATH, CLIPS, code_channels=m.code, review=geo.REVIEW, review_clips=REVIEW)
    print('wrote %d clips' % n)
    print(kit.effects_table(CLIPS))
    report()

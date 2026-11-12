"""Turret Pedestal: the keyed clips (the muzzle) and the code motion of the turntable and cradle.

    python art/machines/turret_pedestal/turret_anim.py

Keyed: `recoil` position only (z + = back toward the jar, the machine faces -Z). One clip per kind of shot, played on
the server's `shot` block event (param = the head's kind), and the house refusal. CODE, never keyed: `yaw` rotation Y and
`pitch` rotation X (the patrol sweep, the snap onto a target and the chase, the unpowered droop, the guardian's
tremble, the evoker's nod). `patrol`, `snap` and `droop` below are that code per game tick; the review copy gets
`_review_*` clips baked from them.

Code, exactly (degrees; written absolutely every frame on every instance):
    yaw: no target, powered: the patrol sweep (below), awake within 32 blocks.
         target (synced targetId): on a new target, snap: easeOutCubic from the current yaw to the bearing over
         4 ticks to 4 % past it, then easeInOutSine back onto it over 3; after that chase: yaw += (bearing - yaw) * 0.3.
    pitch: toward the target's eye height from the muzzle, clamped -10..+25, chased * 0.25; patrol: 0 with a 2-degree
         nod (down and back over 6 ticks) at each end of the sweep; unpowered: droop to -10 over 16 ticks (easeInQuad)
         with a 1-degree bounce, and the yaw stops where it is; guardian lock: + 0.5 * sin(t * 1.9) for its 40 ticks;
         evoker: + 3 * sin(t * pi / 5) for 10 ticks (it chants).
    refusal ("no"): yaw + 15 * sin(t * pi / 2) for 8 ticks (the jar shakes its head twice; v2, was 6), with the
         `refuse` clip's rattle on the muzzle.
    zombie lunge (the muzzle's 2.5 px alone does not read): the cradle headbutts: pitch -20 (face down,
         forward) in 3 ticks (easeInQuad), held 1, back over 6 (easeInOutSine); the `lunge` clip's thrust on top.
    creeper (v2): pitch shudders +-4 for 6 ticks, then slumps -10 and holds through the cooldown (the head is gone).
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
import kit  # noqa: E402
from kit import k, R  # noqa: E402
import turret_geo as geo  # noqa: E402

M = 'recoil'


def P(x=0, y=0, z=0):
    return [x, y, z]


# fire 10 (once): skeleton, snow golem. A 0.3 px creep forward (the wind-up), the kick 3 px back (v2; easeOutExpo, 2
# ticks), back out over 6 (easeInOutSine), a 0.2 px overshoot, rest. The pitch's 3-degree kick is code (above).
FIRE = {'length': 10, 'loop': 'once',
        'bones': {M: {'position': [k(0, P()), k(1, P(z=-0.3), 'easeOutQuad'), k(3, P(z=3), 'easeOutExpo'),
                                   k(8, P(z=-0.3), 'easeInOutSine'), k(10, P(), 'easeInOutSine')]}},
        'effects': [(3, 'particle', 'minecraft:smoke', 'muzzle')]}
# charge 20 (once): blaze. A 10-tick wind-up: the muzzle trembles 0.3 px side to side, a tick each way; the fireball
# leaves on 10 (the server's shot tick is the clip's 10: start it 10 ticks early, or accept the wind-up after): a 2 px
# kick, back over 8.
CHARGE = {'length': 20, 'loop': 'once',
          'bones': {M: {'position': [k(0, P())] + [k(t, P(x=0.3 if t % 2 else -0.3)) for t in range(1, 10)] +
                        [k(10, P(), 'easeOutQuad'), k(12, P(z=2), 'easeOutExpo'), k(19, P(z=-0.2), 'easeInOutSine'),
                         k(20, P(), 'easeInOutSine')]}},
          'effects': [(1, 'sound', 'headinajar:turret.windup|0.5|1.0', None), (12, 'particle', 'minecraft:flame', 'muzzle')]}
# lunge 12 (once): zombie. Draws back 0.5 px (3 ticks), thrusts 2.5 px forward (easeOutCubic, 3), holds a tick,
# returns (easeInOutSine) with a 0.2 px settle.
LUNGE = {'length': 12, 'loop': 'once',
         'bones': {M: {'position': [k(0, P()), k(3, P(z=0.5), 'easeOutQuad'), k(6, P(z=-2.5), 'easeOutCubic'),
                                    k(7, P(z=-2.5)), k(11, P(z=0.2), 'easeInOutSine'), k(12, P(), 'easeInOutSine')]}},
         'effects': [(6, 'sound', 'headinajar:turret.lunge|0.5|1.0', None)]}
# pulse 10 (once): creeper. The flare swells out 1 px (easeOutQuad, 3 ticks) and back (easeInQuad), twice, smaller
# the second time; then the head is gone (the server; the cradle stays, lamp `wait`).
PULSE = {'length': 10, 'loop': 'once',
         'bones': {M: {'position': [k(0, P()), k(3, P(z=-1), 'easeOutQuad'), k(5, P(), 'easeInQuad'),
                                    k(7, P(z=-0.6), 'easeOutQuad'), k(9, P(), 'easeInQuad'), k(10, P())]}},
         'effects': [(0, 'sound', 'headinajar:turret.hiss|0.6|1.0', None)]}
# lob 12 (once): witch. The muzzle dips 0.5 px, then throws up 1 px and back 0.5 (the potion lobbed), returns.
LOB = {'length': 12, 'loop': 'once',
       'bones': {M: {'position': [k(0, P()), k(2, P(y=-0.5), 'easeOutQuad'), k(5, P(y=1, z=0.5), 'easeOutCubic'),
                                  k(10, P(y=-0.1), 'easeInOutSine'), k(12, P(), 'easeInOutSine')]}},
       'effects': [(5, 'particle', 'minecraft:witch', 'muzzle')]}
REFUSE = kit.refuse_clip(M, 'position', axis=0, amount=0.5)
CLIPS = [('fire', FIRE), ('charge', CHARGE), ('lunge', LUNGE), ('pulse', PULSE), ('lob', LOB), ('refuse', REFUSE)]


def eio(t):
    return 0.5 - 0.5 * math.cos(math.pi * max(0.0, min(1.0, t)))


def patrol():
    """+-60 over 120 ticks: 10 ticks still at each end (the nod is on pitch), 50 ticks easeInOutSine across."""
    yaw, pitch = [], []
    for t in range(121):
        if t < 10:
            y, p = 60, -2 * math.sin(math.pi * t / 6) if t <= 6 else 0
        elif t < 60:
            y, p = 60 - 120 * eio((t - 10) / 50.0), 0
        elif t < 70:
            y, p = -60, -2 * math.sin(math.pi * (t - 60) / 6) if t - 60 <= 6 else 0
        else:
            y, p = -60 + 120 * eio((t - 70) / 50.0), 0
        yaw.append(y)
        pitch.append(p)
    return yaw, pitch


def snap(frm, to):
    out = []
    for t in range(5):
        f = 1 - (1 - t / 4.0) ** 3
        out.append(frm + (to + 0.04 * (to - frm) - frm) * f)
    a = out[-1]
    for t in range(1, 4):
        out.append(a + (to - a) * eio(t / 3.0))
    return out


def droop():
    out = [-10 * (t / 16.0) ** 2 for t in range(17)]
    out += [-10 + math.sin(math.pi * t / 4.0) for t in range(1, 5)]
    return out


def review_clips():
    yaw, pitch = patrol()
    sn = snap(0, 90)
    return [('_review_patrol', {'length': 120, 'loop': 'loop', 'review': True,
                                'bones': {'yaw': {'rotation': [k(t, R(y=round(v, 3))) for t, v in enumerate(yaw)]},
                                          'pitch': {'rotation': [k(t, R(x=round(v, 3))) for t, v in enumerate(pitch)]}}}),
            ('_review_snap', {'length': len(sn) - 1, 'loop': 'hold', 'review': True,
                              'bones': {'yaw': {'rotation': [k(t, R(y=round(v, 3))) for t, v in enumerate(sn)]},
                                        'pitch': {'rotation': [k(0, R()), k(4, R(x=12), 'easeOutCubic')]}}}),
            ('_review_lunge', {'length': 12, 'loop': 'once', 'review': True,
                               'bones': {'pitch': {'rotation': [k(0, R()), k(3, R(x=-20), 'easeInQuad'), k(4, R(x=-20)),
                                                                k(10, R(), 'easeInOutSine')]},
                                         M: LUNGE['bones'][M]}}),
            ('_review_no', {'length': 8, 'loop': 'once', 'review': True,
                            'bones': {'yaw': {'rotation': [k(t, R(y=round(15 * math.sin(t * math.pi / 2), 3))) for t in range(9)]}}}),
            ('_review_droop', {'length': 20, 'loop': 'hold', 'review': True,
                               'bones': {'pitch': {'rotation': [k(t, R(x=round(v, 3))) for t, v in enumerate(droop())]}}})]


if __name__ == '__main__':
    m = geo.model()
    n = kit.write_clips(geo.PATH, CLIPS, code_channels=m.code, review=geo.REVIEW, review_clips=review_clips())
    print('wrote %d clips (+%d review)' % (n, len(review_clips())))
    print(kit.effects_table(CLIPS))

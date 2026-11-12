"""Relay Mast (Mk I): the dish's code motion, written out per tick, and the review clips that show it.

    python art/machines/relay_mast/mast_anim.py

The Mk I has no keyed clip: its one bone's rotation is all code (a code-driven channel is never
keyed). `aim_turn`, `droop` and `flinch` below are that code in Python, per game tick, exactly as the block entity's
client tick should run it; the review copy gets `_review_*` clips baked from them for renders only. The Mk II imports
this file (its dish is driven the same way).

Signs (Blockbench = GeckoLib): rotY +a turns the dish's face (-Z) toward -X... checked by render: +90 points the horn at
the machine's left (-X); rotX + tips the dish's face up (the horn rises).
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
import kit  # noqa: E402
from kit import k, R  # noqa: E402


def ease_io_sine(t):
    return 0.5 - 0.5 * math.cos(math.pi * max(0.0, min(1.0, t)))


def aim_turn(frm, to, over=2.0):
    """The dish turns from yaw `frm` to `to` (degrees, the short way): a 9-degree wind-up the wrong way over 5 ticks
    (easeInOutSine) and a 1-tick hold, then easeInOutSine at about 6 degrees a tick to `over` degrees past the target,
    and back onto it over 4 ticks (easeInOutSine). Returns the per-tick yaw list (tick 0 = the new next hop arrives)."""
    d = (to - frm + 180) % 360 - 180
    s = 1 if d >= 0 else -1
    n = max(8, int(math.ceil(abs(d) / 6.0)))
    out = []
    for t in range(0, 6):                      # wind-up: 0 -> -9 s over 5 ticks
        out.append(frm - 9 * s * ease_io_sine(t / 5.0))
    out.append(out[-1])                        # the hold
    a0 = out[-1]
    for t in range(1, n + 1):                  # the swing past the target
        out.append(a0 + (frm + d + over * s - a0) * ease_io_sine(t / float(n)))
    a1 = out[-1]
    for t in range(1, 5):                      # the settle back onto it
        out.append(a1 + (frm + d - a1) * ease_io_sine(t / 4.0))
    return out


def droop(down=True):
    """Power lost: the dish tips 20 degrees face-down over 16 ticks (easeInQuad) and bounces 2 degrees back (4 ticks).
    Power back: it lifts with a 2-degree overshoot over 12 ticks and settles (4). Returns per-tick pitch."""
    out = []
    if down:
        for t in range(0, 17):
            out.append(-20 * (t / 16.0) ** 2)
        for t in range(1, 5):
            out.append(-20 + 2 * math.sin(math.pi * t / 4.0))
    else:
        for t in range(0, 13):
            out.append(-20 + 22 * ease_io_sine(t / 12.0))
        for t in range(1, 5):
            out.append(2 - 2 * ease_io_sine(t / 4.0))
    return out


def flinch():
    """After a lightning strike (a storm): the dish flinches 18 degrees face-up in 2 ticks and creeps back over 30."""
    out = [0.0, 11.0, 18.0]
    for t in range(1, 31):
        out.append(18 * (1 - ease_io_sine(t / 30.0)))
    return out


# rest 1 (hold): the one keyed clip, a position key at zero on the bone (the code drives its rotation): it gives the
# controller a default clip and the exporter an animation file. Nothing moves.
REST = {'length': 1, 'loop': 'hold', 'bones': {'dish': {'position': [k(0, R())]}}, 'effects': []}


def review_clips(bone='dish'):
    yaw = aim_turn(0, 180)
    yaw2 = aim_turn(0, 90)
    clips = [
        ('_review_route_180', {'length': len(yaw) - 1, 'loop': 'hold', 'review': True,
                                'bones': {bone: {'rotation': [k(t, R(y=round(v, 3))) for t, v in enumerate(yaw)]}}}),
        ('_review_route_90', {'length': len(yaw2) - 1, 'loop': 'hold', 'review': True,
                               'bones': {bone: {'rotation': [k(t, R(y=round(v, 3))) for t, v in enumerate(yaw2)]}}}),
        ('_review_droop', {'length': 20, 'loop': 'hold', 'review': True,
                           'bones': {bone: {'rotation': [k(t, R(x=round(v, 3))) for t, v in enumerate(droop())]}}}),
        ('_review_flinch', {'length': 32, 'loop': 'hold', 'review': True,
                            'bones': {bone: {'rotation': [k(t, R(x=round(v, 3))) for t, v in enumerate(flinch())]}}}),
    ]
    return clips


if __name__ == '__main__':
    import mast_geo as geo
    m = geo.model()
    n = kit.write_clips(geo.PATH, [('rest', REST)], code_channels=m.code, review=geo.REVIEW, review_clips=review_clips())
    print('wrote %d clips (+%d review)' % (n, len(review_clips())))
    print('route 180: %d ticks; route 90: %d ticks' % (len(aim_turn(0, 180)) - 1, len(aim_turn(0, 90)) - 1))

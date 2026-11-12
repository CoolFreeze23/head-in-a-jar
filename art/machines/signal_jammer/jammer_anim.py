"""Signal Jammer: the turbine's code motion, written out per tick, and the review clips that show it.

    python art/machines/signal_jammer/jammer_anim.py

No keyed clip: the rotor's rotation is all code. The spin is an accumulated angle in the
block entity's client tick, drawn lerped between ticks:

    target = jamming ? 36 : powered ? 4.5 : 0          # degrees a tick: 1 turn per 10 ticks jamming, per 80 idle
    k      = target > speed ? 0.08 : 0.04              # spins up in about 2 s, coasts down in about 4 s (40 ticks)
    speed += (target - speed) * k;  prev = angle;  angle += speed
    rotor.setRotY(initial + Mth.lerp(partial, prev, angle))  (radians), every frame, every instance

Awake (drawn turning) while powered and within 24 blocks; asleep it draws at its last angle within 32.
The review copy gets `_review_spin_up` (rest to jamming over 60 ticks) and `_review_jam` (one jamming turn).
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
import kit  # noqa: E402
from kit import k, R  # noqa: E402
import jammer_geo as geo  # noqa: E402


def spin(n, start_speed, target, k_up=0.08, k_down=0.04):
    angle, speed, out = 0.0, start_speed, []
    for t in range(n + 1):
        out.append(angle)
        kk = k_up if target > speed else k_down
        speed += (target - speed) * kk
        angle += speed
    return out


# rest 1 (hold): the one keyed clip, a position key at zero on the bone (the code drives its rotation): it gives the
# controller a default clip and the exporter an animation file. Nothing moves.
REST = {'length': 1, 'loop': 'hold', 'bones': {'rotor': {'position': [k(0, R())]}}, 'effects': []}


def review_clips():
    up = spin(60, 4.5, 36)
    jam = [36.0 * t for t in range(11)]
    return [('_review_spin_up', {'length': 60, 'loop': 'hold', 'review': True,
                                 'bones': {'rotor': {'rotation': [k(t, R(y=round(v, 2))) for t, v in enumerate(up)]}}}),
            ('_review_jam', {'length': 10, 'loop': 'loop', 'review': True,
                             'bones': {'rotor': {'rotation': [k(t, R(y=v)) for t, v in enumerate(jam)]}}})]


if __name__ == '__main__':
    m = geo.model()
    n = kit.write_clips(geo.PATH, [('rest', REST)], code_channels=m.code, review=geo.REVIEW, review_clips=review_clips())
    print('wrote %d clips (+%d review)' % (n, len(review_clips())))

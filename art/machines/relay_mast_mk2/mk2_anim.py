"""Relay Mast Mk II: the keyed clips (the top stage) and the dish's code motion (the Mk I's, imported).

    python art/machines/relay_mast_mk2/mk2_anim.py

Keyed: `stage2` position only. stage_up 10 (hold): a 0.5 px dip (the wind-up), up 4 px with a 0.5 px overshoot (v2: was 2)
(easeOutCubic), settled (easeInOutSine). stage_down 10 (hold): a 0.4 px lift, down 4 px into the pole (easeInQuad, the
contact), a 0.3 px bounce, rest. Driven by the synced `carrying` bit with the seeking controller (a late joiner sees the
stage up). CODE: the dish's rotation (aim, droop, flinch), exactly as the Mk I's (relay_mast/mast_anim.py).
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
sys.path.insert(0, os.path.join(HERE, '..', 'relay_mast'))
import kit  # noqa: E402
from kit import k, R  # noqa: E402
import mast_anim  # noqa: E402
import mk2_geo as geo  # noqa: E402

S = 'stage2'
STAGE_UP = {'length': 10, 'loop': 'hold',
            'bones': {S: {'position': [k(0, R()), k(2, R(y=-0.5), 'easeOutQuad'), k(7, R(y=4.5), 'easeOutCubic'),
                                       k(10, R(y=4), 'easeInOutSine')]}},
            'effects': [(1, 'sound', 'headinajar:mast.stage_up|0.4|1.0', None)]}
STAGE_DOWN = {'length': 10, 'loop': 'hold',
              'bones': {S: {'position': [k(0, R(y=4)), k(2, R(y=4.4), 'easeOutQuad'), k(7, R(), 'easeInQuad'),
                                         k(8, R(y=0.4), 'easeOutQuad'), k(10, R(), 'easeInQuad')]}},
              'effects': [(7, 'sound', 'headinajar:mast.stage_down|0.4|1.0', None)]}
CLIPS = [('stage_up', STAGE_UP), ('stage_down', STAGE_DOWN)]

if __name__ == '__main__':
    m = geo.model()
    review = mast_anim.review_clips('dish')
    # a review clip with the stage up and the dish turned at once: the Mk II's "carrying" pose at range
    yaw = mast_anim.aim_turn(0, 180)
    review.append(('_review_carry', {'length': len(yaw) - 1, 'loop': 'hold', 'review': True,
                                     'bones': {'dish': {'rotation': [k(t, R(y=round(v, 3))) for t, v in enumerate(yaw)]},
                                               S: {'position': [k(0, R()), k(2, R(y=-0.5), 'easeOutQuad'),
                                                                k(7, R(y=4.5), 'easeOutCubic'),
                                                                k(10, R(y=4), 'easeInOutSine')]}}}))
    n = kit.write_clips(geo.PATH, CLIPS, code_channels=m.code, review=geo.REVIEW, review_clips=review)
    print('wrote %d clips (+%d review)' % (n, len(review)))
    print(kit.effects_table(CLIPS))

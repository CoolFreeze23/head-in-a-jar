"""Galvanic Pile: every clip, keyed by game tick, written into galvanic_pile.bbmodel.

Source of truth for the Pile's animations; hand edits in Blockbench are overwritten by the next run.

    python art/machines/galvanic_pile/pile_anim.py [--review OUT.bbmodel]

The compression (stack_top and press posY) is CODE, never keyed (the kit's rule for code channels): spent = 1 - burnStep/8,
chased v += (target - v) / 12 a tick; stack_top.posY = -1 * spent, press.posY = -2 * spent, written absolutely every
frame. The review copy adds `_review_*` clips that simulate it with keys, for renders only.

Handle direction: + rotY screws the press down (the work loop), - unwinds it (refuel, finish). The bar with its two
grips looks the same every 180 degrees, so the work loop turns 180 and loops seamlessly.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
import kit  # noqa: E402
from kit import k, R  # noqa: E402
import pile_geo  # noqa: E402

# work: 50 ticks, loop while lamp=work (50 divides 150, 200 and 300: every burn ends on a loop boundary).
# 0-26 the handle holds (the refuel clip owns ticks 0-24 of a new burn and ends on the same pose); 6 the spark jumps
# the 2 px gap between the terminals; then the screw is cranked down twice, a quarter turn each (a 16 px bar: medium,
# 3-tick wind-up of 9 degrees, 6-tick turn, 4-degree overshoot, settle), ratchet clicks on the arrivals. The press's
# code chase advances only during these two turns (the model notes), so turning visibly goes with going down.
WORK = {
    'length': 50, 'loop': 'loop',
    'bones': {'handle': {'rotation': [
        k(0, R()), k(26, R()), k(29, R(y=-9), 'easeOutQuad'), k(35, R(y=94), 'easeInOutSine'),
        k(37, R(y=90), 'easeInOutSine'),
        k(38, R(y=90)), k(41, R(y=81), 'easeOutQuad'), k(47, R(y=184), 'easeInOutSine'),
        k(50, R(y=180), 'easeInOutSine')]}},
    'effects': [
        (6, 'particle', 'headinajar:pile_spark', 'term_a'),      # skipped under the photosensitive option
        (7, 'sound', 'headinajar:pile.crackle|0.4|1.0', None),
        (35, 'sound', 'headinajar:pile.ratchet|0.35|1.0', None),
        (47, 'sound', 'headinajar:pile.ratchet|0.35|1.1', None),
    ],
}

# refuel: 24 ticks, from burnStart when a new item is pulled. The T-handle winds back up a full turn (the press rises
# by code as burnStep resets to 8): 15-degree wind-up the other way, the turn, a 2-degree overshoot, settle.
REFUEL = {
    'length': 24, 'loop': 'once',
    'bones': {'handle': {'rotation': [
        k(0, R()), k(3, R(y=15), 'easeOutQuad'), k(17, R(y=-360), 'easeInOutCubic'),
        k(20, R(y=-362), 'easeOutQuad'), k(24, R(y=-360), 'easeInOutSine')]}},
    'effects': [
        (5, 'sound', 'headinajar:pile.ratchet|0.4|0.9', None),
        (8, 'sound', 'headinajar:pile.ratchet|0.4|0.95', None),
        (11, 'sound', 'headinajar:pile.ratchet|0.4|1.0', None),
        (14, 'sound', 'headinajar:pile.ratchet|0.4|1.05', None),
    ],
}

# finish: 10 ticks, when the last fuel runs out (on a loop boundary). The handle unwinds half a turn and sighs; the
# press's code target goes to 0 (released) and is chased over ticks 2-8, so loosening visibly lifts the plate.
FINISH = {
    'length': 10, 'loop': 'hold',
    'bones': {'handle': {'rotation': [
        k(0, R()), k(2, R(y=6), 'easeOutQuad'), k(8, R(y=-182), 'easeInOutCubic'), k(10, R(y=-180), 'easeInOutSine')]}},
    'effects': [
        (2, 'sound', 'headinajar:pile.sigh|0.5|1.0', None),
        (3, 'particle', 'minecraft:poof', 'screw_top'),
    ],
}

# refuse: the house refusal on the handle (8 ticks, +-3 degrees), on pile.full.
REFUSE = kit.refuse_clip('handle', 'rotation', 1, 3.0)

CLIPS = [('work', WORK), ('refuel', REFUEL), ('finish', FINISH), ('refuse', REFUSE)]

# ---- review only: the code-driven compression simulated with keys ----------------------------------------------
REVIEW = [
    ('_review_spent', {'length': 40, 'loop': 'hold', 'review': True, 'bones': {
        'press': {'position': [k(0, R()), k(40, R(y=-2), 'easeInOutSine')]},
        'stack_top': {'position': [k(0, R()), k(40, R(y=-1), 'easeInOutSine')]},
        'handle': {'rotation': [k(0, R()), k(40, R(y=45))]}}, 'effects': []}),
    # refuel as a bystander sees it: the handle's clip plus the code lifting the spent press over ticks 3-17
    ('_review_refuel', {'length': 24, 'loop': 'hold', 'review': True, 'bones': {
        'press': {'position': [k(0, R(y=-2)), k(3, R(y=-2)), k(17, R(), 'easeInOutSine')]},
        'stack_top': {'position': [k(0, R(y=-1)), k(3, R(y=-1)), k(17, R(), 'easeInOutSine')]},
        'handle': REFUEL['bones']['handle']}, 'effects': []}),
]

if __name__ == '__main__':
    review = sys.argv[sys.argv.index('--review') + 1] if '--review' in sys.argv else None
    n = kit.write_clips(pile_geo.PATH, CLIPS, code_channels=pile_geo.model().code, review=review, review_clips=REVIEW)
    print('wrote %d clips' % n)
    print(kit.effects_table(CLIPS))

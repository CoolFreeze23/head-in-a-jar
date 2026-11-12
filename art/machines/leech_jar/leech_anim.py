"""Leech Jar: every clip, keyed by game tick, written into leech_jar.bbmodel (hand edits are overwritten).

    python art/machines/leech_jar/leech_anim.py [--review]

--review writes build/machines-review/leech_jar_review.bbmodel (eased segments baked per tick, plus `_review_*`
clips that simulate the code-driven carrier turning to a side) and leech_jar_s0 / _s1 (no vials / one vial shown).

Four bones:
  carrier  CODE rotY about the globe's axis (0, 13, 0): turns the leech and the nozzle to the side the creature stands
           on (synced `side`: none, or 0 front / 90 left (+X... see the notes) / 180 back / 270), chased
           `a += (target - a) * 0.15` a tick, drawn with the partial tick; held where it was when `side` goes to none.
  leech    keyed (wake, press, settle, sulk; and the bite inside `sample`)
  nozzle   keyed posZ (local to the carrier: -Z is toward the creature)
  crank    keyed rotX about (6, 8, 0): a full turn per sample, two on emptying (the ten-block motion)
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
try:
    import kit  # noqa: E402
except ImportError:
    sys.path.insert(0, 'build/machines-review/scratch-batch5')
    import kit  # noqa: E402
from kit import k, R  # noqa: E402
import leech_geo  # noqa: E402

PATH = leech_geo.PATH
REVIEW = 'build/machines-review'
P = lambda x=0, y=0, z=0: [x, y, z]  # noqa: E731
L = 'headinajar:leech.'


def snd(t, name, vol=0.5, pitch=1.0):
    return (t, 'sound', '%s%s|%s|%s' % (L, name, vol, pitch), None)


CLIPS = []

# wake (side: none -> a side), 10, hold: the leech swims from the middle of the water's surface to the glass on that
# side (4.5 px), rearing its front end 25 degrees up against the glass (contact 8: easeInQuad), a small settle.
CLIPS.append(('wake', {'length': 10, 'loop': 'hold', 'bones': {
    'leech': {'position': [k(0, P()), k(2, P(z=0.5), 'easeOutQuad'), k(8, P(z=-4.5), 'easeInQuad'),
                           k(10, P(z=-4.25), 'easeOutQuad')],
              'rotation': [k(0, R()), k(4, R(z=-10), 'easeInOutSine'), k(8, R(x=28), 'easeInOutCubic'),
                           k(10, R(x=25), 'easeInOutSine')]}},
    'effects': [snd(1, 'stir', 0.3)]}))

# press, 24, loop (after wake, while a creature stays): reared against the glass, the leech sways its tail 12 degrees
# each way and nods 10 degrees, pressing 0.25 px further into the glass at each nod.
CLIPS.append(('press', {'length': 24, 'loop': 'loop', 'bones': {
    'leech': {'position': [k(0, P(z=-4.25)), k(6, P(z=-4.5), 'easeInOutSine'), k(12, P(z=-4.25), 'easeInOutSine'),
                           k(18, P(z=-4.5), 'easeInOutSine'), k(24, P(z=-4.25), 'easeInOutSine')],
              'rotation': [k(0, R(x=25)), k(6, R(x=35, z=-12), 'easeInOutSine'), k(12, R(x=25), 'easeInOutSine'),
                           k(18, R(x=35, z=12), 'easeInOutSine'), k(24, R(x=25), 'easeInOutSine')]}},
    'effects': []}))

# settle (a side -> none), 12, hold: the leech lets go of the glass and drifts back to the middle (contact 10).
CLIPS.append(('settle', {'length': 12, 'loop': 'hold', 'bones': {
    'leech': {'position': [k(0, P(z=-4.25)), k(3, P(z=-4.75), 'easeOutQuad'), k(10, P(), 'easeInOutCubic'),
                           k(12, P(), 'easeInQuad')],
              'rotation': [k(0, R(x=25)), k(6, R(), 'easeInOutSine'), k(12, R())]}},
    'effects': [snd(10, 'flop', 0.3)]}))

# sample (block event `feed`, on the server's sampling tick), 20: the nozzle draws back 0.5 px, pops out 2 px toward
# the creature (easeOutBack), taps it on 6 (the server's hurt and honey-drink sounds), holds, withdraws by 12; the
# leech jolts 0.5 px into the glass at the bite; then the crank turns once by itself (11-19, a 10-degree wind-up
# back first) and a vial drops into the rack on 19 (the blockstate `samples` changes on the server's tick).
CLIPS.append(('sample', {'length': 20, 'loop': 'once', 'bones': {
    'nozzle': {'position': [k(0, P()), k(2, P(z=0.5), 'easeOutQuad'), k(5, P(z=-2), 'easeOutBack', [1]),
                            k(8, P(z=-2)), k(12, P(), 'easeInQuad')]},
    'crank': {'rotation': [k(0, R()), k(10, R()), k(11, R(x=10), 'easeOutQuad'),
                           k(18, R(x=-362), 'easeInOutCubic'), k(19, R(x=-360), 'easeOutQuad'), k(20, R(x=-360))]}},
    'effects': [snd(5, 'nozzle_pop', 0.4), (6, 'timeline', 'leech:bite', None), snd(11, 'crank_ratchet', 0.5),
                snd(19, 'plink', 0.6)]}))

# empty (block event, the player takes the samples), 24: the crank turns twice (a wind-up, two turns, a settle);
# the rack's vials clear on the server's tick (blockstate).
CLIPS.append(('empty', {'length': 24, 'loop': 'once', 'bones': {
    'crank': {'rotation': [k(0, R()), k(2, R(x=12), 'easeOutQuad'), k(21, R(x=-724), 'easeInOutCubic'),
                           k(24, R(x=-720), 'easeInOutSine')]}},
    'effects': [snd(1, 'crank_ratchet', 0.5), snd(11, 'crank_ratchet', 0.5), snd(21, 'vials_clatter', 0.5)]}))

# sulk (full: nine samples, lamp `wait`), 10, hold: the leech curls on the collar, its tail lifted 25 degrees.
CLIPS.append(('sulk', {'length': 10, 'loop': 'hold', 'bones': {
    'leech': {'position': [k(0, P()), k(10, P(), 'easeInOutSine')],
              'rotation': [k(0, R()), k(8, R(z=28), 'easeInOutCubic'), k(10, R(z=25), 'easeInOutSine')]}},
    'effects': []}))


def review_clips():
    """The carrier turned by code, simulated: to the right side (+X, the machine's right) over 10 ticks, and a sample
    there."""
    turn = {'length': 30, 'loop': 'hold', 'review': True, 'bones': {
        'carrier': {'rotation': [k(0, R()), k(10, R(y=-90), 'easeInOutSine'), k(30, R(y=-90))]},
        'leech': {'position': [k(0, P()), k(10, P()), k(18, P(z=-4.5), 'easeInQuad'), k(30, P(z=-4.25))],
                  'rotation': [k(0, R()), k(10, R()), k(18, R(x=25), 'easeInOutSine')]},
        'nozzle': {'position': [k(0, P()), k(20, P()), k(25, P(z=-2), 'easeOutBack', [1]), k(30, P(z=-2))]}},
        'effects': []}
    left = {'length': 1, 'loop': 'hold', 'review': True, 'bones': {
        'carrier': {'rotation': [k(0, R(y=90))]}, 'leech': {'position': [k(0, P(z=-4.25))], 'rotation': [k(0, R(x=25))]},
        'nozzle': {'position': [k(0, P(z=-2))]}}, 'effects': []}
    return [('_review_turn_right', turn), ('_review_left_sampling', left)]


def variant(src, dst, show=(), hide=()):
    with open(src, encoding='utf-8') as fh:
        d = json.load(fh)
    for g in d['groups']:
        if g['name'] in show:
            g['visibility'] = True
        if g['name'] in hide:
            g['visibility'] = False
    with open(dst, 'w', encoding='utf-8') as fh:
        json.dump(d, fh, indent=1)


def main():
    code = leech_geo.model().code
    rv = REVIEW + '/leech_jar_review.bbmodel'
    kit.write_clips(PATH, CLIPS, code_channels=code, review=rv if '--review' in sys.argv else None,
                    review_clips=review_clips())
    if '--review' in sys.argv:
        variant(rv, REVIEW + '/leech_jar_s0.bbmodel', hide=('state_samples_3',))
        variant(rv, REVIEW + '/leech_jar_s1.bbmodel', show=('state_samples_1',), hide=('state_samples_3',))
    print(kit.effects_table(CLIPS))


if __name__ == '__main__':
    main()

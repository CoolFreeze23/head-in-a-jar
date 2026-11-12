"""Cerebral Array: every clip, keyed by game tick, written into cerebral_array.bbmodel and the review copies.

Source of truth for the Array's animations; hand edits in Blockbench are overwritten by the next run.

    python art/machines/cerebral_array/array_anim.py

The vote. The server votes on tick T (`gameTime % 6000 == floorMod(pos.asLong(), 6000)`
once the modulo bug is fixed); the client computes T-10 itself and starts the clip there, so clip tick c = T - 10 + c:
  c 0-8    the chairman lifts the gavel 55 degrees and holds it over the bench (the heads snap to face front: code)
  c 10     = T: the gavel strikes (contact; the server plays the gavel and the murmur and says the line)
  c 14-42  the cards flip up in a ripple, seat 1 to 9 (front row first, viewer's left to right), 3 ticks apart, each
           in 4 ticks with a small overshoot; the code tints each card green (aye) or red (nay) from its own tick
  c 50     = T+40: the cards drop together onto the lids (contact at 53), a little bounce
  c 60     end, at rest.
vote_anyway is the same clip with two more bangs (contacts c 43 and c 50: the second knocks the cards down), played
when the event says the nays outnumber the ayes ("Carried anyway."); the two clips are identical to c 36, so the
controller may switch from `vote` to `vote_anyway` at the same tick when the event arrives.
refuse: the gavel taps twice (house rhythm, 30 degrees). No idle or loaded clip: asleep nothing moves; the heads' bob,
turns and eurekas are code on the drawn heads.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import array_geo as geo  # noqa: E402
kit = geo.kit
from kit import k, R  # noqa: E402

m = geo.model()

CARD_UP = -90      # card rotX: lying back on the lid -> standing (checked by render: + would fold it into the jar)
GAVEL_UP = -1      # gavel rotZ sign that lifts the head (checked by render)
S = 'headinajar:array.'
RISE0, GAP, DROP = 14, 3, 50


def G(deg):
    return R(z=GAVEL_UP * deg)


def bang(t_contact, lift=55, t_up=None, hold=1, rise=4):
    """One gavel bang ending in a contact at t_contact: lift (easeOutCubic), a 1-tick extra lift (the wind-up before
    the strike), the strike in 2 ticks (easeInExpo), a 6-degree bounce and settle."""
    t_up = t_contact - 2 - hold if t_up is None else t_up
    return [k(t_up - rise, G(0)), k(t_up, G(lift), 'easeOutCubic'), k(t_up + hold, G(lift + 3), 'easeOutQuad'),
            k(t_contact, G(0), 'easeInExpo'), k(t_contact + 1, G(6), 'easeOutQuad'), k(t_contact + 2, G(0), 'easeInQuad')]


def vote(anyway=False):
    # the opening bang is slower: the lift over 6 ticks (easeInOutSine), held 2, the strike on c 10
    gav = [k(0, G(0)), k(6, G(55), 'easeInOutSine'), k(8, G(58), 'easeOutQuad'), k(10, G(0), 'easeInExpo'),
           k(11, G(6), 'easeOutQuad'), k(13, G(0), 'easeInQuad')]
    fx = [(10, 'particle', 'minecraft:poof', 'gavel')]
    if anyway:
        gav += bang(45)[:-1]         # v2: contact 45 (was 43: it crowded the ripple's last card); no settle:
        gav += bang(50, rise=1)[1:]   # the third lift starts from the bounce (46) and strikes on 50
        fx += [(45, 'sound', S + 'gavel|1.0|0.95', None), (46, 'particle', 'minecraft:poof', 'gavel'),
               (50, 'sound', S + 'gavel|1.0|1.08', None), (51, 'particle', 'minecraft:poof', 'gavel')]
    bones = {'gavel': {'rotation': gav}}
    for i in range(1, 10):
        t = RISE0 + GAP * (i - 1)
        d = DROP + (2 - (i - 1) // 3)      # v2: the drop cascades down the steps, back row first (50, 51, 52)
        bones['card_%d' % i] = {'rotation': [
            k(0, R()), k(t, R()), k(t + 4, R(x=CARD_UP), 'easeOutBack', [0.5]), k(d, R(x=CARD_UP)),
            k(d + 3, R(), 'easeInQuad'), k(d + 4, R(x=CARD_UP / 15.0), 'easeOutQuad'),
            k(d + 5, R(), 'easeInQuad')]}
        fx.append((t, 'sound', S + 'card|0.3|%.2f' % (0.9 + 0.025 * i), None))
    fx += [(DROP + 3, 'sound', S + 'cards_down|0.5|1.05', None), (DROP + 5, 'sound', S + 'cards_down|0.5|0.95', None)]
    return {'length': 60, 'loop': 'hold', 'bones': bones, 'effects': fx}


VOTE, ANYWAY = vote(), vote(True)

REFUSE = {'length': 8, 'loop': 'once',
          'bones': {'gavel': {'rotation': [k(0, G(0)), k(2, G(30), 'easeOutCubic'), k(4, G(0), 'easeInQuad'),
                                           k(6, G(30), 'easeOutCubic'), k(8, G(0), 'easeInQuad')]}},
          'effects': [(0, 'sound', 'headinajar:machine.rattle|0.4|1.0', None), (4, 'sound', S + 'gavel|0.4|1.4', None),
                      (8, 'sound', S + 'gavel|0.4|1.5', None)]}

CLIPS = [('vote', VOTE), ('vote_anyway', ANYWAY), ('refuse', REFUSE)]

# review only: the poses held (for boards), the axes, the sweep
REVIEW = [
    ('_review_axes', {'length': 1, 'loop': 'hold', 'review': True, 'effects': [],
                      'bones': dict({'gavel': {'rotation': [k(0, G(45))]}},
                                    **{'card_%d' % i: {'rotation': [k(0, R(x=CARD_UP / 2))]} for i in range(1, 10)})}),
    ('_review_sweep', {'length': 30, 'loop': 'hold', 'review': True, 'effects': [],
                       'bones': dict({'gavel': {'rotation': [k(t, G(2 * t)) for t in range(31)]}},
                                     **{'card_%d' % i: {'rotation': [k(t, R(x=CARD_UP * t / 30.0 * 1.1))
                                                                     for t in range(31)]} for i in range(1, 10)})}),
]


def write_copy(path, mats):
    """A review copy with the cards in the given placeholder materials, every clip baked."""
    mv = geo.model(card_mats=mats)
    geo.write(mv, path, check=False)
    with open(path, encoding='utf-8') as f:
        model = json.load(f)
    model['animations'] = kit.clips_json(model, CLIPS + REVIEW, (), baked=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(model, f, indent=1)


def write_vote_copy():
    """The vote copy: the cards coloured as a vote tints them (seats in geo.NAYS_REVIEW red, the rest green). The
    plain review copy (v2) shows the cards with the rest tint (a buff one step under the enamel, enamel2), so the
    jars do not read as capped in white."""
    write_copy(geo.REVIEW_VOTE, {i: ('lens_fault' if i in geo.NAYS_REVIEW else 'lens_ready') for i in range(1, 10)})
    write_copy(geo.REVIEW, {i: 'enamel2' for i in range(1, 10)})


if __name__ == '__main__':
    n = kit.write_clips(geo.PATH, CLIPS, code_channels=m.code, review=geo.REVIEW, review_clips=REVIEW)
    write_vote_copy()
    print('wrote %d clips' % n)
    print(kit.effects_table(CLIPS))

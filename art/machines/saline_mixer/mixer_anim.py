"""Saline Mixer: every clip, keyed by game tick, written into saline_mixer.bbmodel.

Source of truth for the Mixer's animations; hand edits in Blockbench are overwritten by the next run.

    python art/machines/saline_mixer/mixer_anim.py [--review]

--review also writes build/machines-review/mixer_review.bbmodel (every eased segment baked per tick, plus the
`_review_*` clips that simulate the code-driven paddle) and the review variants `mixer_bottles` (the three filled
bottles shown), `mixer_level4` and `mixer_level0` (the saline at full and empty).

Rotation signs (keyframes, Blockbench = GeckoLib): rotX +a turns +y towards +z. So the tap's pull (its top coming
forward, to -Z) is rotX NEGATIVE; a cap's dose flap (front edge down) is NEGATIVE and its refusal chatter (front edge
up) POSITIVE, both about the cap's back edge; the flag's up is +90 (kit). (The outliner's rest rotation has the other
X sign: the tap's 10-degree lean back is stored as -10 on the group.)

The caps' dose (batch) and refusal both key rotX; they never play together: the game code does not play a refusal
on a cap while a batch runs (the lamp's fault and the server's sound carry it then).

The paddle's rotY is CODE, never keyed (the kit's rule for code channels): see the module notes in the model notes and PADDLE below.
"""
import copy
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
import kit  # noqa: E402
from kit import k, R  # noqa: E402
import mixer_geo  # noqa: E402

REVIEW_DIR = 'build/machines-review'
P = lambda y: R(y=y)  # noqa: E731  (a position key: posY only)


# ---- batch: authored 200, seeked at elapsed * 200 / batchTicks. The three doses, the pepper-shaker flap: each cap
# tips on its back-edge pivot, its front edge dropping 20 degrees into the canister's mouth (about 1.4 px, the
# canister's dark opening shows behind it): a 4-degree lift first (the breath), easeInQuad onto the contact tick
# (12, 26, 40), held 2 ticks, back up past rest by 3 degrees (easeOutCubic) and settled. Tink on the contact, the
# ingredient's powder falling into the tank one tick later (one effect per tick). rotX only; no paddle keys (code).
def dose(t0):
    """t0 = the tick the cap starts (8, 22, 36); contact at t0 + 4."""
    return [k(0, R()), k(t0, R()), k(t0 + 2, R(x=4), 'easeOutQuad'), k(t0 + 4, R(x=-20), 'easeInQuad'),
            k(t0 + 6, R(x=-20)), k(t0 + 9, R(x=3), 'easeOutCubic'), k(t0 + 10, R(), 'easeInOutSine'), k(200, R())]


BATCH = {
    'length': 200, 'loop': 'hold',
    'bones': {'cap_s': {'rotation': dose(8)}, 'cap_k': {'rotation': dose(22)}, 'cap_r': {'rotation': dose(36)}},
    'effects': [
        (12, 'sound', 'headinajar:mixer.tink|0.4|1.25', None),
        (13, 'particle', 'headinajar:mixer_dose_sugar', 'dose_s'),
        (26, 'sound', 'headinajar:mixer.tink|0.4|1.1', None),
        (27, 'particle', 'headinajar:mixer_dose_kelp', 'dose_k'),
        (40, 'sound', 'headinajar:mixer.tink|0.4|0.95', None),
        (41, 'particle', 'headinajar:mixer_dose_redstone', 'dose_r'),
    ],
}


# ---- open / close: the screen-open role of K5 (K5): the three caps lift 2 px while a player has the
# screen open, one tick apart (sugar, kelp, redstone: M4 overlap), each with a 0.25 px dip, easeOutCubic to 2.5 and
# settled to 2. Close: back down easeInQuad into the contact, a 0.5 px bounce, down. One tink per contact.
def lift(t0):
    return ([k(0, P(0))] if t0 else []) + [k(t0, P(0)), k(t0 + 1, P(-0.25), 'easeOutQuad'),
                                           k(t0 + 3, P(2.5), 'easeOutCubic'), k(t0 + 4, P(2), 'easeInOutSine')]


def drop(t0, end):
    keys = ([k(0, P(2))] if t0 else []) + [k(t0, P(2)), k(t0 + 3, P(0), 'easeInQuad'), k(t0 + 4, P(0.5), 'easeOutQuad'),
            k(t0 + 5, P(0), 'easeInQuad')]
    return keys + ([k(end, P(0))] if t0 + 5 < end else [])


OPEN = {'length': 6, 'loop': 'hold',
        'bones': {'cap_s': {'position': lift(0)}, 'cap_k': {'position': lift(1)}, 'cap_r': {'position': lift(2)}},
        'effects': [(0, 'sound', 'headinajar:mixer.caps_up|0.4|1.0', None)]}
CLOSE = {'length': 7, 'loop': 'hold',
         'bones': {'cap_s': {'position': drop(0, 7)}, 'cap_k': {'position': drop(1, 7)},
                   'cap_r': {'position': drop(2, 7)}},
         'effects': [(3, 'sound', 'headinajar:mixer.cap_down|0.3|1.2', None),
                     (4, 'sound', 'headinajar:mixer.cap_down|0.3|1.1', None),
                     (5, 'sound', 'headinajar:mixer.cap_down|0.3|1.0', None)]}

# ---- bottle: 10, loop while `bottling`. The tap handle (small): from its rest lean (10 degrees back), a 3-degree
# wind-up further back, pulled 45 degrees forward (35 past upright) into its stop on 4 (easeInQuad, the contact), a
# 3-degree bounce off the stop, held, back to the lean by 10. Keys are relative to the rest lean (GeckoLib adds the
# clip's rotation to the bone's rest rotation). Glug on the stop; the stream from the spout 5-7 (one key a tick).
BOTTLE = {'length': 10, 'loop': 'loop',
          'bones': {'tap': {'rotation': [k(0, R()), k(1, R(x=3), 'easeOutQuad'), k(4, R(x=-45), 'easeInQuad'),
                                         k(5, R(x=-42), 'easeOutQuad'), k(6, R(x=-45), 'easeInQuad'),
                                         k(7, R(x=-45)), k(10, R(), 'easeInOutSine')]}},
          'effects': [(4, 'sound', 'headinajar:mixer.glug|0.5|1.0', None),
                      (5, 'particle', 'headinajar:mixer_stream', 'spout'),
                      (6, 'particle', 'headinajar:mixer_stream', 'spout'),
                      (7, 'particle', 'headinajar:mixer_stream', 'spout')]}

# ---- flag (K9): the house clips, chosen by hasOutput (slot 4 non-empty)
FLAG_UP, FLAG_DOWN = kit.flag_clips(('flag', 'rotation', [90, 0, 0]))


# ---- refusal: the named cap chatters (block event id 1, param 0 sugar / 1 kelp / 2 redstone). Its front edge lifts
# about the back edge (a cruet's flip-top): 12, 0, 8, 0, 5, 0 degrees, the drops easeInQuad (contacts). 8 ticks,
# rotation only, so it plays over a held open or batch pose (those key position). The rattle sound at 0.
def refuse(cap):
    return {'length': 8, 'loop': 'once',
            'bones': {cap: {'rotation': [k(0, R()), k(1, R(x=12), 'easeOutQuad'), k(2, R(), 'easeInQuad'),
                                         k(3, R(x=8), 'easeOutQuad'), k(4, R(), 'easeInQuad'),
                                         k(5, R(x=5), 'easeOutQuad'), k(6, R(), 'easeInQuad'), k(8, R())]}},
            'effects': [(0, 'sound', 'headinajar:machine.rattle|0.4|1.2', None)]}


CLIPS = [('batch', BATCH), ('open', OPEN), ('close', CLOSE), ('bottle', BOTTLE), ('flag_up', FLAG_UP),
         ('flag_down', FLAG_DOWN), ('refuse_s', refuse('cap_s')), ('refuse_k', refuse('cap_k')),
         ('refuse_r', refuse('cap_r'))]


# ---- the paddle, simulated for review (code in the game) -------------------------------------------------------
# speed (0..1) chases target: speed += (target - speed) * 0.0625 a tick; angle += 18 * speed degrees a tick.
# target = rate/4 while 8 <= authored tick < 180 (and through the end if another batch can start at once), else 0;
# at least 0.25 while lamp=low. On coasting to rest, a 10-degree settle back over 10 ticks (easeInOutSine).
def paddle_angles(n, target_fn, speed0=0.0, angle0=0.0):
    out, speed, angle = [], speed0, angle0
    for t in range(n + 1):
        out.append(angle)
        speed += (target_fn(t) - speed) * 0.0625
        angle += 18 * speed
    return out, speed


def keys_from(angles, step=2, settle=None):
    ks = [k(t, R(y=round(-angles[t], 2))) for t in range(0, len(angles), step)]
    if (len(angles) - 1) % step:
        ks.append(k(len(angles) - 1, R(y=round(-angles[-1], 2))))
    return ks


def review_clips():
    # a whole batch at full rate, the doses and the paddle together (seek it at any tick)
    batch_ang, _ = paddle_angles(200, lambda t: 1.0 if 8 <= t < 180 else 0.0)
    rb = copy.deepcopy(BATCH)
    rb['review'] = True
    rb['effects'] = []
    end = batch_ang[-1]
    rb['bones']['paddle'] = {'rotation': keys_from(batch_ang, 2)}
    # the settle: 10 degrees back after it has coasted (the paddle turns towards -rotY, so back is +)
    # (the batch's authored length ends at 200; the settle is shown in _review_coast)
    # brown-out: rate 0 but lamp=low, so the paddle crawls at 0.25 while progress (and the caps' clip) holds
    low_ang, _ = paddle_angles(60, lambda t: 0.25, speed0=1.0)
    # coast: from full speed to rest and the 10-degree settle
    coast_ang, sp = paddle_angles(60, lambda t: 0.0, speed0=1.0)
    ck = keys_from(coast_ang, 2)
    ck.append(k(70, R(y=round(-coast_ang[-1] + 10, 2)), 'easeInOutSine'))
    return [
        ('_review_batch', rb),
        ('_review_brownout', {'length': 60, 'loop': 'hold', 'review': True, 'effects': [],
                              'bones': {'paddle': {'rotation': keys_from(low_ang, 2)}}}),
        ('_review_coast', {'length': 70, 'loop': 'hold', 'review': True, 'effects': [],
                           'bones': {'paddle': {'rotation': ck}}}),
        # bottling as a bystander sees it: the lever pulled (for a still of the pose)
        ('_review_pulled', {'length': 4, 'loop': 'hold', 'review': True, 'effects': [],
                            'bones': {'tap': {'rotation': [k(0, R()), k(4, R(x=-45), 'easeInQuad')]}}}),
    ]


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


if __name__ == '__main__':
    review = REVIEW_DIR + '/mixer_review.bbmodel' if '--review' in sys.argv else None
    n = kit.write_clips(mixer_geo.PATH, CLIPS, code_channels=mixer_geo.model().code, review=review,
                        review_clips=review_clips() if review else ())
    print('wrote %d clips' % n)
    if review:
        variant(review, REVIEW_DIR + '/mixer_bottles.bbmodel', show=['standin_out'])
        variant(review, REVIEW_DIR + '/mixer_level4.bbmodel', show=['state_saline_4'], hide=['state_saline_2'])
        variant(review, REVIEW_DIR + '/mixer_level0.bbmodel', hide=['state_saline_2'])
        print('review copy and variants written')
    print(kit.effects_table(CLIPS))

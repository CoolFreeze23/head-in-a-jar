"""Body Locker (the Cloakroom): every clip, keyed by game tick, written into body_locker.bbmodel (and the review copy).

    python art/machines/body_locker/locker_anim.py

Both bones are keyed (no code channel). The rule: **release is instant, the show after** (the server releases
the host on the click, tick 0 of `checkout`, and the clip plays after it). Check-in stores on tick 0 as today.

Signs: shutter position y - = down (rest is rolled up in the box; -9 covers the window). ticket rotation z + swings
its foot toward -X (checked by render); ticket scale 0/1 (step) = hidden/shown.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
import kit  # noqa: E402
from kit import k, R  # noqa: E402
import locker_geo as geo  # noqa: E402

D = -geo.SHUTTER_DROP
SH, TK = 'shutter', 'ticket'


def P(y=0, x=0, z=0):
    return [x, y, z]


def S(v):
    return [v, v, v]


SWING = [k(16, R(z=22), 'easeOutQuad'), k(18, R(z=-14), 'easeInOutSine'), k(21, R(z=8), 'easeInOutSine'),
         k(24, R(z=-4), 'easeInOutSine'), k(27, R(z=1.5), 'easeInOutSine'), k(30, R(), 'easeInOutSine')]

# checkin 30 (hold): the host is stored on 0 (server: the desk bell, a cloud where it stood; the client runs the puff
# trail to `window` over 0-6 from the event's position). 6-8 the shutter's wind-up: it dips 1 px out of its box and
# holds; 8-14 it rolls down (easeInQuad into the sill), ratchet ticks 9, 11, 13, a thunk at 14; 14-16 a 0.5 px bounce;
# 16 the ticket appears on the hook and swings 22 degrees, settling by 30; 18 the portrait is drawn; 24 the done chime.
CHECKIN = {'length': 30, 'loop': 'hold',
           'bones': {SH: {'position': [k(0, P()), k(6, P(-1), 'easeOutQuad'), k(8, P(-1)), k(14, P(D), 'easeInQuad'),
                                       k(15, P(D + 0.5), 'easeOutQuad'), k(16, P(D), 'easeInQuad')]},
                     TK: {'scale': [k(0, S(0), interp='step'), k(16, S(1), interp='step')],
                          'rotation': [k(0, R()), k(15, R())] + SWING}},
           'effects': [(0, 'timeline', 'locker:checkin', None),
                       (9, 'sound', 'headinajar:locker.ratchet|0.4|1.0', None),
                       (11, 'sound', 'headinajar:locker.ratchet|0.4|1.05', None),
                       (13, 'sound', 'headinajar:locker.ratchet|0.4|1.1', None),
                       (14, 'sound', 'headinajar:locker.thunk|0.6|1.0', None),
                       (16, 'sound', 'headinajar:locker.ticket_clink|0.4|1.2', None),
                       (18, 'timeline', 'locker:portrait_on', None),
                       (24, 'sound', 'headinajar:machine.done|0.5|1.0', None)]}

# checkout 24 (hold): the host is released on 0 by the server (instant) and the show follows:
# the bell twice (0, 2: the server's first, the clip's second); 2 the ticket lifts (swings 15 degrees up off the hook)
# and is hidden at 4 with a clink; 4-6 the unlatch: the shutter lifts 1 px off the sill (v2); 6-16 it rolls up into its box
# (easeInOutSine), ratchet ticks 8, 11, 14; 16-18 a 0.3 px overshoot and settle; 18 the card returns to VACANT.
CHECKOUT = {'length': 24, 'loop': 'hold',
            'bones': {SH: {'position': [k(0, P(D)), k(4, P(D)), k(6, P(D + 1), 'easeOutQuad'), k(16, P(0.3), 'easeInOutSine'),
                                        k(18, P(), 'easeInQuad')]},
                      TK: {'scale': [k(0, S(1), interp='step'), k(4, S(0), interp='step')],
                           'rotation': [k(0, R()), k(2, R(z=-15), 'easeOutCubic'), k(4, R(z=-20), 'easeInQuad')]}},
            'effects': [(2, 'sound', 'headinajar:locker.bell|0.4|2.0', None),
                        (4, 'sound', 'headinajar:locker.ticket_clink|0.4|1.2', None),
                        (8, 'sound', 'headinajar:locker.ratchet|0.4|1.1', None),
                        (11, 'sound', 'headinajar:locker.ratchet|0.4|1.05', None),
                        (14, 'sound', 'headinajar:locker.ratchet|0.4|1.0', None),
                        (18, 'timeline', 'locker:portrait_off', None)]}

# The house refusal, three forms by what the booth shows:
#   refuse_empty 8 (nothing to check in, cannot store, unknown, front blocked while empty): the rolled-up shutter
#     the shutter's visible bottom bar flicks 2 px down and back, twice
#     (v2; the ticket stays hidden).
#   refuse_full 8 (front blocked, cannot release, while something is in): the closed shutter lifts 1 px off the sill and drops, twice (v2).
#   refuse_ticket 8 (not yours): the ticket rattles +-8 degrees on its hook.
REFUSE_EMPTY = {'length': 8, 'loop': 'once',
                'bones': {SH: {'position': [k(0, P()), k(2, P(-2), 'easeInOutSine'), k(4, P(), 'easeInOutSine'),
                                            k(6, P(-2), 'easeInOutSine'), k(8, P(), 'easeInOutSine')]},
                          TK: {'scale': [k(0, S(0), interp='step')]}},
                'effects': [(0, 'sound', 'headinajar:machine.rattle|0.4|1.0', None)]}
REFUSE_FULL = {'length': 8, 'loop': 'once',
               'bones': {SH: {'position': [k(0, P(D)), k(2, P(D + 1), 'easeOutQuad'), k(4, P(D), 'easeInQuad'),
                                           k(6, P(D + 1), 'easeOutQuad'), k(8, P(D), 'easeInQuad')]},
                         TK: {'scale': [k(0, S(1), interp='step')]}},
               'effects': [(0, 'sound', 'headinajar:machine.rattle|0.4|1.0', None)]}
REFUSE_TICKET = {'length': 8, 'loop': 'once',
                 'bones': {SH: {'position': [k(0, P(D))]},
                           TK: {'scale': [k(0, S(1), interp='step')],
                                'rotation': [k(0, R()), k(2, R(z=8), 'easeInOutSine'), k(4, R(z=-8), 'easeInOutSine'),
                                             k(6, R(z=8), 'easeInOutSine'), k(8, R(), 'easeInOutSine')]}},
                 'effects': [(0, 'sound', 'headinajar:locker.ticket_rattle|0.4|1.0', None)]}
# the two held states a late joiner seeks to (the last frames of checkin / checkout): written as 1-tick holds so the
# renderer can pose them without playing
HELD_FULL = {'length': 1, 'loop': 'hold', 'bones': {SH: {'position': [k(0, P(D))]}, TK: {'scale': [k(0, S(1), interp='step')]}},
             'effects': []}
HELD_EMPTY = {'length': 1, 'loop': 'hold', 'bones': {SH: {'position': [k(0, P())]}, TK: {'scale': [k(0, S(0), interp='step')]}},
              'effects': []}
CLIPS = [('checkin', CHECKIN), ('checkout', CHECKOUT), ('refuse_empty', REFUSE_EMPTY), ('refuse_full', REFUSE_FULL),
         ('refuse_ticket', REFUSE_TICKET), ('held_full', HELD_FULL), ('held_empty', HELD_EMPTY)]

if __name__ == '__main__':
    m = geo.model()
    n = kit.write_clips(geo.PATH, CLIPS, code_channels=m.code, review=geo.REVIEW)
    print('wrote %d clips' % n)
    print(kit.effects_table(CLIPS))

"""Time Clock: the clips.

    python art/machines/time_clock/time_clock_anim.py

Three controllers, all GeckoLib `SeekingController`s fed from synced state, so a player who arrives late sees the same
pose as everyone else on the first frame:

    dial    `dial` (24000 ticks, loop): the hour hand, one turn a day. Seek: the Overworld's day time % 24000 plus the
            partial tick. A 24-hour dial, noon at the top: day time 0 (06:00) puts the hand at the viewer's left,
            6000 (noon) up, 12000 (18:00) right, 18000 (midnight) down. Linear (a constant turn, M2).
    minute  `minute` (1000 ticks, loop): the minute hand, one turn an in-game hour. Seek: day time % 1000 plus the
            partial tick. A jump-minute hand, as factory time recorders have: it stands for each in-game minute
            (1000 / 60 ticks) and steps 6 degrees in 2 ticks, easeOutBack [0.6] (a small overshoot and settle), so the
            clock visibly ticks about once a second. This is the clock's idle life (M7).
    main    `idle` (40, loop: every keyed bone at rest), `punch` (20, once), `ding` (8, once). Seek: ticks since the
            synced start stamp of the punch (or of the ding). A late punch plays `ding` straight after `punch` (ticks
            20-28 after the stamp) on the same controller.

`punch` (20 ticks), a worker punching in:
    0      the time card stands out of the slot (3 px out of the throat: the worker holds it there)
    0-3    the card goes in (easeInQuad, it meets its stop on 3), a 0.3 px bounce back on 4, home on 5
    2-3    the lever lifts 4 degrees (the wind-up)
    3-6    the lever comes down 26 degrees (easeInExpo): it strikes on tick 6, the stamp of the punch-in sound
           (`time_clock/punch_in.ogg`: its stamp is 303 ms in, so the server starts the sound on the clip's tick 0)
    6-9    the bell's plunger dips 0.5 px and comes up: the clock's own tap (round 2: a shiver of the whole bell read
           as the roof jumping, so the bell stays still in `punch`)
    7      the lever rebounds 3 degrees, back down on 8, held to 9 (at -30 the grip would touch the throat)
    9-14   the lever goes home (easeInOutSine) with a 1 degree settle
    10-13  the stamped card comes back out 4 px (easeOutCubic), 1 px past the throat: the worker sees it
    16-20  the card goes back in (easeInQuad): the company keeps it on file
`ding` (8 ticks), a late arrival: the plunger goes down 1 px (easeInQuad, 2 ticks: the strike, the bell sound's hit on
tick 2) and up by 4; the bell rocks +-5, -4, +3, -1.5, 0 degrees about its base, each beat 1-2 ticks (easeInOutSine).

Keyed bones: card (position X), lever (rotation X), bell (rotation X, Z; `ding` only), plunger (position Y); the hands (rotation
Z). `strike_card` is never keyed (it hangs from the bell and swings with it; hidden until 0.7.2).
"""
import copy
import json
import os
import sys
import uuid as uuidlib

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
import kit  # noqa: E402
from kit import k, R  # noqa: E402
import time_clock_geo as geo  # noqa: E402

# The viewer stands at -Z looking +Z: a positive Blockbench Z rotation turns a hand clockwise as the viewer sees it
# (checked on the review renders: noon up, 18:00 to the viewer's right).
CW = 1
DAY = 24000
HOUR = 1000
OUT = -6          # the card's travel out of the right flank (x -), px: its end 3 px out of the throat


def P(x=0, y=0, z=0):
    return [x, y, z]


# ---- dial: the hour hand, 15 degrees an in-game hour; day time 0 is 06:00, which is -90 degrees from noon ----
DIAL = {'length': DAY, 'loop': 'loop',
        'bones': {'hour_hand': {'rotation': [k(t, R(z=CW * (-90 + 90 * i))) for i, t in enumerate(range(0, DAY + 1, 6000))]}}}

# ---- minute: a jump each in-game minute ----
def minute_keys():
    keys = [k(0, R())]
    per = HOUR / 60.0
    for i in range(60):
        t = per * (i + 1)
        hold = t - 2
        if i:
            keys.append(k(round(hold, 4), R(z=CW * 6 * i)))
        else:
            keys.append(k(round(hold, 4), R()))
        keys.append(k(round(t, 4) if i < 59 else HOUR, R(z=CW * 6 * (i + 1)), 'easeOutBack', [0.6]))
    return keys


MINUTE = {'length': HOUR, 'loop': 'loop', 'bones': {'minute_hand': {'rotation': minute_keys()}}}

IDLE = {'length': 40, 'loop': 'loop',
        'bones': {'card': {'position': [k(0, P()), k(40, P())]}, 'lever': {'rotation': [k(0, R()), k(40, R())]},
                  'bell': {'rotation': [k(0, R()), k(40, R())]}, 'plunger': {'position': [k(0, P()), k(40, P())]}}}

PUNCH = {'length': 20, 'loop': 'once',
         'bones': {
             'card': {'position': [k(0, P(x=OUT)), k(3, P(), 'easeInQuad'), k(4, P(x=-0.3), 'easeOutQuad'),
                                   k(5, P(), 'easeInQuad'), k(10, P()), k(13, P(x=-4), 'easeOutCubic'), k(16, P(x=-4)),
                                   k(20, P(), 'easeInQuad')]},
             'lever': {'rotation': [k(0, R()), k(2, R()), k(3, R(x=4), 'easeOutQuad'), k(6, R(x=-26), 'easeInExpo'),
                                    k(7, R(x=-23), 'easeOutQuad'), k(8, R(x=-26), 'easeInQuad'), k(9, R(x=-26)),
                                    k(13, R(x=1), 'easeInOutSine'), k(14, R(), 'easeInOutSine')]},
             'plunger': {'position': [k(0, P()), k(6, P()), k(7, P(y=-0.5), 'easeInQuad'), k(9, P(), 'easeOutQuad')]}},
         'effects': [(0, 'sound', 'headinajar:time_clock.punch_in|0.0|1.0', None)]}

DING = {'length': 8, 'loop': 'once',
        'bones': {
            'plunger': {'position': [k(0, P()), k(2, P(y=-1), 'easeInQuad'), k(4, P(), 'easeOutQuad')]},
            'bell': {'rotation': [k(0, R()), k(2, R()), k(3, R(x=2, z=5), 'easeOutQuad'), k(4, R(x=-1.5, z=-4), 'easeInOutSine'),
                                  k(5, R(x=1, z=3), 'easeInOutSine'), k(7, R(z=-1.5), 'easeInOutSine'),
                                  k(8, R(), 'easeInOutSine')]}}}

CLIPS = [('idle', IDLE), ('punch', PUNCH), ('ding', DING), ('dial', DIAL), ('minute', MINUTE)]


def stable_uuids(anims, model_name):
    """The kit writes random keyframe UUIDs; replace them with ones derived from the clip, channel and time, so a
    rebuild writes the same bytes."""
    for a in anims:
        for an_uuid, an in a['animators'].items():
            for i, kf in enumerate(an['keyframes']):
                kf['uuid'] = str(uuidlib.uuid5(kit.NS, '%s/%s/%s/%s/%d' % (model_name, a['name'], an['name'],
                                                                           kf['channel'], i)))
    return anims


def write():
    with open(geo.PATH, encoding='utf-8') as fh:
        model = json.load(fh)
    # the effects channel carries the sound for the review only: the server plays the punch-in sound (M12), so the
    # shipped clip's sound key is dropped below
    shipped = [(n, dict(c, effects=[])) for n, c in CLIPS]
    model['animations'] = stable_uuids(kit.clips_json(model, shipped), model['name'])
    with open(geo.PATH, 'w', encoding='utf-8') as fh:
        json.dump(model, fh, indent=1)
    # review copy: eased segments baked per tick (the long linear dial is left as two-key segments)
    rev = copy.deepcopy(model)
    baked = kit.clips_json(model, [c for c in shipped if c[0] != 'dial'], baked=True)
    rev['animations'] = stable_uuids(baked + kit.clips_json(model, [c for c in shipped if c[0] == 'dial']), model['name'])
    os.makedirs(os.path.dirname(geo.REVIEW), exist_ok=True)
    with open(geo.REVIEW, 'w', encoding='utf-8') as fh:
        json.dump(rev, fh, indent=1)
    return len(CLIPS)


if __name__ == '__main__':
    print('wrote %d clips' % write())

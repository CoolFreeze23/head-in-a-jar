"""DNA Sequencer: every clip, keyed by game tick, written into dna_sequencer.bbmodel (and the review copy).

Source of truth for the Sequencer's animations; hand edits in Blockbench are overwritten by the next run.

    python art/machines/dna_sequencer/sequencer_anim.py

One accepted call (a base rung up) is a BEAT: the key is struck (`press_<base>` on the keys controller), the crank is
pulled one full turn and the call flag drops into the window and pops back up out of the display's top showing that
base (`ring` on the work controller), and the receipt feeds 1 px every second call (code). The auto-read is twelve
beats fired from the job clock; the minigame is one beat per accepted-call block event. The end of a read: the flag
replays the twelve calls (`tally`: the plate tinted with the base for a right call, the NO SALE banner for a wrong one,
chosen per slot by hiddenCubes and tint from the end event's mask), the bell rings and the drawer pops (`ding`), the
receipt is torn off (`tear`).

Controllers and their clips (no two controllers key the same bone channel):
  keys     press_a/c/g/t 8 (key position), refuse 8 (the four keys chatter, seen from the front)
  work     ring 16 (crank rotation, flag position), tally 96 / tally_full 110 (flag), load 6 / unload 6 (flag)
  receipt  tear 10 (receipt rotation; its position is CODE)
  drawer   drawer_open 5 / drawer_close 6 (kit), ding 36 / ding2 44 (closed drawer), ding_open 10 / ding2_open 18
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import sequencer_geo as geo  # noqa: E402  (imports the kit, with its fallback)
kit = geo.kit
from kit import k, R  # noqa: E402

m, DRAWER = geo.model()
DRAWER_OPEN, DRAWER_CLOSE = kit.drawer_clips(DRAWER)
S = 'headinajar:sequencer.'
FWD = -1            # crank rotX sign that pulls the top of the crank toward the operator (-Z); checked by render
UP = 11.0           # the call flag up: from y 16-21 (hidden in the body) to 27-32 (in the open over the display)
MID = 5.0           # the flag in the window (y 21-26): where it waits while the shown cube is switched
BASES = 'acgt'
PITCH = {'a': 0.8, 'c': 0.9, 'g': 1.0, 't': 1.1}   # key clacks: A lowest
BEAT = 16           # auto-read: a call every 16 ticks of progress, at 8, 24, ..., 184; the read ends at 200
CALLS = [8 + BEAT * i for i in range(12)]


def C(deg):
    return R(x=FWD * deg)


# ---- keys ---------------------------------------------------------------------------------------------------------
def press(b):
    """A key struck: a hair up (the finger lands), down 1 px into the well on tick 2 (easeInQuad, the clack), held,
    sprung back with a 0.25 px overshoot. Tiny part: 2-tick action, 2-tick settle."""
    bone = 'key_' + b
    return {'length': 8, 'loop': 'once',
            'bones': {bone: {'position': [k(0, R()), k(1, R(y=0.25), 'easeOutQuad'), k(2, R(y=-1), 'easeInQuad'),
                                          k(5, R(y=-1)), k(7, R(y=0.25), 'easeOutCubic'),
                                          k(8, R(), 'easeInOutSine')]}},
            'effects': [(2, 'sound', S + 'key|0.5|%s' % PITCH[b], None)]}


def refuse():
    """The house refusal, seen from the front: the keys are locked; all four jump 1 px and drop back in a ripple from
    the viewer's left (A) to the right (T), each a tick after the last, in the house rhythm (up 2/6, down 4/8)."""
    seq = {'a': [(2, 1), (4, 0), (6, 1), (8, 0)],
           'c': [(3, 1), (5, 0), (7, 1), (8, 0)],
           'g': [(4, 1), (6, 0), (7, 0.6), (8, 0)],
           't': [(5, 1), (7, 0), (8, 0)]}
    bones = {}
    for b, lst in seq.items():
        bones['key_' + b] = {'position': [k(0, R())] + [
            k(t, R(y=v), 'easeInQuad' if v == 0 else 'easeOutQuad') for t, v in lst if not (b == 't' and t == 7)]}
    return {'length': 8, 'loop': 'once', 'bones': bones,
            'effects': [(0, 'sound', 'headinajar:machine.rattle|0.4|1.0', None)]}


# ---- work: the crank and the tabs ---------------------------------------------------------------------------------
def ring():
    """One call rung up, started on the same tick as the key's press clip (the key strikes at 2).
    crank (v2: done by 9 so a quick player's next call, 9 ticks on, starts from rest): wind-up 15 degrees back by 2
    (easeOutQuad), a tick's hold, one full turn forward to 370 by 8 (easeInOutSine: a pull), settle to 360 by 9. 360
    is the rest pose, so the next ring starts where this one ends.
    flag: up (the previous call's plate, or the NO SALE banner of `load`) drops 6 px into the window by 2 (easeInQuad);
    the renderer switches the shown cube and its tint while it is there (ticks 2-4); it pops 6 px back up into the
    open by 7 (easeOutCubic, 0.5 px over) and settles by 8; it stays up until the next call."""
    crank = [k(0, C(0)), k(2, C(-15), 'easeOutQuad'), k(3, C(-15)), k(8, C(370), 'easeInOutSine'),
             k(9, C(360), 'easeInOutSine')]
    flag = [k(0, R(y=UP)), k(2, R(y=MID), 'easeInQuad'), k(4, R(y=MID)), k(7, R(y=UP + 0.5), 'easeOutCubic'),
            k(8, R(y=UP), 'easeInOutSine')]
    return {'length': 16, 'loop': 'hold', 'bones': {'crank': {'rotation': crank}, 'flag': {'position': flag}},
            'effects': [(4, 'sound', S + 'crank|0.4|1.0', None), (7, 'sound', S + 'tab|0.4|1.0', None)]}


SLOT = 7            # v2: 7 ticks a slot, so the colour changes stay 7+ ticks apart (M15)


def tally(full=False):
    """The end of a minigame read: the call flag replays the twelve calls, one slot every 7 ticks (it drops 6 px into
    the window by +2; the renderer shows slot i's cube there: the plate tinted with call i's base if it was right,
    the NO SALE banner if wrong; it pops back up by +5 and settles by +6). Then it holds the last one to 90 and goes
    down into the body (93, easeInQuad, a bounce). With a full house (all twelve right) a thirteenth slot at 84 shows
    the plate cycling through the four colours (code, 3 ticks each), held to 104."""
    keys = [k(0, R(y=UP))]
    fx = []
    n = 13 if full else 12
    for s in range(n):
        t = SLOT * s
        keys += [k(t + 2, R(y=MID), 'easeInQuad'), k(t + 5, R(y=UP + 0.5), 'easeOutCubic'),
                 k(t + 6, R(y=UP), 'easeInOutSine')]
        fx.append((t + 5, 'sound', S + 'tab|0.4|%.2f' % (1.0 + 0.02 * s), None))
    hold = 104 if full else 90
    keys += [k(hold, R(y=UP)), k(hold + 3, R(), 'easeInQuad'), k(hold + 4, R(y=0.3), 'easeOutQuad'),
             k(hold + 6, R(), 'easeInQuad')]
    fx.append((hold + 3, 'sound', S + 'tab_down|0.4|1.0', None))
    return {'length': hold + 6, 'loop': 'hold', 'bones': {'flag': {'position': keys}}, 'effects': fx}


# load / unload: the NO SALE banner pops into the window when a fraction is put in (the register is ready: no sale
# yet) and drops when it is taken out (or consumed at the end of a read, after the tally).
LOAD = {'length': 6, 'loop': 'hold',
        'bones': {'flag': {'position': [k(0, R()), k(1, R(y=-0.25), 'easeOutQuad'), k(5, R(y=UP + 0.5), 'easeOutCubic'),
                                        k(6, R(y=UP), 'easeInOutSine')]}},
        'effects': [(1, 'sound', S + 'tab|0.4|0.9', None)]}
UNLOAD = {'length': 6, 'loop': 'hold',
          'bones': {'flag': {'position': [k(0, R(y=UP)), k(1, R(y=UP + 0.5), 'easeOutQuad'), k(4, R(), 'easeInQuad'),
                                          k(5, R(y=0.5), 'easeOutQuad'), k(6, R(), 'easeInQuad')]}},
          'effects': [(4, 'sound', S + 'tab_down|0.4|1.0', None)]}


# ---- receipt ------------------------------------------------------------------------------------------------------
# tear: the full tape (posY 6, its foot at the slot) is snatched sideways toward -X: a 4-degree wind-up the other way,
# a 3-tick easeInExpo flick to 35 degrees (the tear), held a tick (a step key), and at tick 6 the code drops the tape
# back to its stub as the rotation returns to 0 on the same tick (a scrap of paper falls from the locator to cover it).
TEAR = {'length': 10, 'loop': 'hold',
        'bones': {'receipt': {'rotation': [k(0, R()), k(1, R(z=-4), 'easeOutQuad'), k(4, R(z=35), 'easeInExpo'),
                                           k(5, R(z=35), interp='step'), k(6, R()), k(10, R())]}},
        'effects': [(4, 'sound', S + 'tear|0.5|1.0', None), (6, 'particle', 'headinajar:sequencer_paper', 'receipt')]}


# ---- drawer: the bench's K5 drawer is the cash drawer -------------------------------------------------------------
def ding(double=False):
    """The bell: the latch lets go (0.25 px in, easeOutQuad), the spring shoots the drawer out to 5 by 3
    (easeOutCubic), settle at 4.5 by 5 (v2: 1.5 px past the screen's 3, its front 0.5 px out from under the worktop); held open, pushed shut by 30 (easeInQuad, contact), 0.5 px bounce, shut by 32.
    Double (a full house): a second bell 6 ticks after the first; the drawer the same, 8 ticks later to close."""
    d = 8 if double else 0
    keys = [k(0, R()), k(1, R(z=0.25), 'easeOutQuad'), k(3, R(z=-5), 'easeOutCubic'), k(5, R(z=-4.5), 'easeInOutSine'),
            k(26 + d, R(z=-4.5)), k(30 + d, R(), 'easeInQuad'), k(31 + d, R(z=-0.5), 'easeOutQuad'),
            k(32 + d, R(), 'easeInQuad')]
    fx = [(1, 'sound', S + 'bell|0.7|1.0', None), (3, 'sound', 'headinajar:machine.drawer_stop|0.4|1.1', None)]
    if double:
        fx.append((7, 'sound', S + 'bell|0.7|1.12', None))
    fx.append((30 + d, 'sound', 'headinajar:machine.drawer_shut|0.5|1.0', None))
    return {'length': 36 + d, 'loop': 'hold', 'bones': {'drawer': {'position': keys}}, 'effects': fx}


def ding_open(double=False):
    """The bell with the screen open (the drawer already out at 3): a jolt in and out, settle."""
    keys = [k(0, R(z=-3)), k(1, R(z=-2.75), 'easeOutQuad'), k(3, R(z=-3.75), 'easeOutCubic'),
            k(5, R(z=-3), 'easeInOutSine')]
    fx = [(1, 'sound', S + 'bell|0.7|1.0', None)]
    if double:
        keys += [k(7, R(z=-3)), k(8, R(z=-2.75), 'easeOutQuad'), k(10, R(z=-3.75), 'easeOutCubic'),
                 k(12, R(z=-3), 'easeInOutSine')]
        fx.append((8, 'sound', S + 'bell|0.7|1.12', None))
    return {'length': 18 if double else 10, 'loop': 'hold', 'bones': {'drawer': {'position': keys}}, 'effects': fx}


CLIPS = ([('press_' + b, press(b)) for b in BASES]
         + [('ring', ring()), ('tally', tally()), ('tally_full', tally(True)), ('load', LOAD), ('unload', UNLOAD),
            ('tear', TEAR), ('ding', ding()), ('ding2', ding(True)), ('ding_open', ding_open()),
            ('ding2_open', ding_open(True)), ('drawer_open', DRAWER_OPEN), ('drawer_close', DRAWER_CLOSE),
            ('refuse', refuse())])


# ---- the code: the receipt's length, and how the code fires the beats -----------------------------------------------
def receipt_len(count, since):
    """posY of `receipt` (code): 1 px per two accepted calls (v2: rests on whole pixels, M6), fed on every second
    call over beat ticks 3-8 (while the crank turns), easeInOutSine. count = accepted calls so far (including the
    newest), since = ticks since the newest call's beat started."""
    if count <= 0:
        return 0.0
    base = (count - 1) // 2
    if count % 2:
        return geo.RECEIPT_STEP * base
    f = min(1.0, max(0.0, (since - 3) / 5.0))
    return geo.RECEIPT_STEP * (base + kit.ease('easeInOutSine', None, f))


def auto_beats(rate_of_tick, start=0):
    """The auto-read under a given power supply. rate_of_tick(t) is the share of ticks that advance (1 full, 0.5 a
    brown-out, 0 holding). Progress advances by it each tick; a beat fires on the tick progress crosses CALLS[i], and
    each beat then plays at full speed on the wall clock: in a brown-out the ringing comes further apart, it never
    slows down (M5). Returns (tick, call index) for each beat and the tick the read ends."""
    p, t, beats, end = 0.0, start, [], None
    while p < 200 and t < start + 2000:
        q = p + rate_of_tick(t)
        for i, c in enumerate(CALLS):
            if p < c <= q:
                beats.append((t, i))
        p, t = q, t + 1
    return beats, t


GATTACA = 'gattacagatta'     # the keys the auto-read strikes when the client has no seed (decorative)


def review_read(name, beats, end, mask=None, length=None):
    """Bake a whole read: each beat's press/ring/receipt, then the ending (auto: unload, ding, tear; minigame: tally,
    ding, tear, unload)."""
    keys = {b: [k(0, R())] for b in BASES}
    crank = [k(0, C(0))]
    flag = [k(0, R(y=UP))]                      # loaded: the NO SALE banner up
    rec = [k(0, R())]
    recrot = [k(0, R())]
    drw = [k(0, R())]
    rclip = ring()
    for n, (t0, i) in enumerate(beats):
        b = GATTACA[i]
        for kk in press(b)['bones']['key_' + b]['position'][1:]:
            keys[b].append(k(t0 + kk[0], kk[1], kk[2], kk[3]))
        for kk in rclip['bones']['crank']['rotation']:
            crank.append(k(t0 + kk[0], kk[1], kk[2], kk[3]))
        for kk in rclip['bones']['flag']['position']:
            flag.append(k(t0 + kk[0], kk[1], kk[2], kk[3]))
        for s in range(0, 16):
            rec.append(k(t0 + s, R(y=receipt_len(n + 1, s))))
    for b in BASES:      # one key per tick
        seen = {}
        for kk in keys[b]:
            seen[kk[0]] = kk
        keys[b] = [seen[t] for t in sorted(seen)]
    e = end
    if mask is None:
        unl = UNLOAD['bones']['flag']['position']
        flag += [k(e + kk[0], kk[1], kk[2], kk[3]) for kk in unl]
        dstart, tstart = e, e + 2
    else:
        tl = tally(mask == 0xfff)
        flag += [k(e + kk[0], kk[1], kk[2], kk[3]) for kk in tl['bones']['flag']['position'][1:]]
        dstart, tstart = e + 84, e + 86
    dk = ding(mask == 0xfff)['bones']['drawer']['position']
    drw += [k(dstart + kk[0], kk[1], kk[2], kk[3]) for kk in dk]
    for kk in TEAR['bones']['receipt']['rotation']:
        recrot.append(k(tstart + kk[0], kk[1], kk[2], kk[3], kk[4]))
    rec.append(k(tstart + 5, R(y=6)))
    rec.append(k(tstart + 6, R()))
    L = length or max(tstart + 12, dstart + 46)

    def clean(lst):
        seen = {}
        for kk in lst:
            seen[kk[0]] = kk
        return [seen[t] for t in sorted(seen) if t <= L]
    bones = {'key_' + b: {'position': clean(keys[b])} for b in BASES}
    bones.update({'crank': {'rotation': clean(crank)}, 'flag': {'position': clean(flag)},
                  'receipt': {'position': clean(rec), 'rotation': clean(recrot)}, 'drawer': {'position': clean(drw)}})
    return (name, {'length': L, 'loop': 'hold', 'review': True, 'bones': bones, 'effects': []})


full_beats, full_end = auto_beats(lambda t: 1.0)
brown_beats, brown_end = auto_beats(lambda t: 0.5 if t >= 40 else 1.0)
mini_beats = [(6 + 9 * i, i) for i in range(12)]          # a quick player: a call every 9 ticks
mini_end = mini_beats[-1][0] + 16

REVIEW = [
    review_read('_review_read', full_beats, full_end),
    review_read('_review_brownout', brown_beats, brown_end),
    review_read('_review_minigame', mini_beats, mini_end, mask=0b111011011101),
    # the receipt at full length with the crank a quarter turn on and the tabs up: the working pose for the boards
    ('_review_working', {'length': 1, 'loop': 'hold', 'review': True, 'bones': {
        'receipt': {'position': [k(0, R(y=6))]}, 'crank': {'rotation': [k(0, C(90))]},
        'flag': {'position': [k(0, R(y=UP))]}}, 'effects': []}),
    ('_review_half', {'length': 1, 'loop': 'hold', 'review': True, 'bones': {
        'receipt': {'position': [k(0, R(y=3))]}, 'crank': {'rotation': [k(0, C(180))]},
        'flag': {'position': [k(0, R(y=MID))]}}, 'effects': []}),
    # sweep: the crank through a whole turn in 15-degree steps, the receipt to full and through the tear angle
    ('_review_sweep', {'length': 24, 'loop': 'hold', 'review': True, 'bones': {
        'crank': {'rotation': [k(t, C(15 * t)) for t in range(25)]},
        'receipt': {'position': [k(0, R()), k(24, R(y=6))]}}, 'effects': []}),
    ('_review_axes', {'length': 1, 'loop': 'hold', 'review': True, 'bones': {
        'crank': {'rotation': [k(0, C(45))]}, 'receipt': {'position': [k(0, R(y=6))], 'rotation': [k(0, R(z=35))]}},
        'effects': []}),
]

if __name__ == '__main__':
    n = kit.write_clips(geo.PATH, CLIPS, code_channels=m.code, review=geo.REVIEW, review_clips=REVIEW)
    print('wrote %d clips' % n)
    print(kit.effects_table(CLIPS))
    print('auto-read at full power: beats at', [b[0] for b in full_beats], 'end', full_end)
    print('brown-out (half power from tick 40): beats at', [b[0] for b in brown_beats], 'end', brown_end)

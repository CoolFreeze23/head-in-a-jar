"""Gene Splicer: every clip, keyed by game tick, written into gene_splicer.bbmodel (and the review copy).

Source of truth for the Splicer's animations; hand edits in Blockbench are overwritten by the next run.

    python art/machines/gene_splicer/splicer_anim.py

One stitch = one turn of the handwheel = 10 ticks at full speed (36 degrees a tick). The needle bar and its take-up lever
(one bone, a 6 px stroke) are at the top of their
stroke on every stitch boundary and at the bottom (the contact, the patter) half a turn in, as on a real machine.

Clips (the main controller: needle, wheel, cassette):
  start      10, once   the handwheel is rocked back 30 degrees and returned (the clutch), needle lifts 0.5 px
  work_0..2  80, loop   eight stitches; seeked at the game's work PHASE (below), chosen by `stab` 0-2:
                        0 even, 1 the eighth stitch stutters, 2 the fourth and the eighth stutter
  stop       26, hold   the coast: one last stitch while the wheel runs down one turn (easeOutQuad: it starts at
                        exactly full speed), a 3-degree rock back and settle. Played from a stitch boundary.
  finish_0..2           stop, then 0/1/2 hiccups (the mutation-risk band: the wheel kicks back 30 degrees, the lever
                        jolts 3 px, the three spools hop 2 px together), the lever lifts, the cassette pops up 6 px.
                        Hold on the last frame (cassette up) while the output waits.
Spools (rotation is CODE, never keyed; position keyed):
  pick_1..3  10, hold   the spool hops 2.5 px and drops back into its seat (the thread check) when its gene is set
  refuse      8, once   the three spools chatter 1 px in a ripple, left to right (seen from the front)
Kit: drawer_open 5 / drawer_close 6 (the `open` bit), flag_up 6 / flag_down 6 (`hasOutput`).

The work PHASE (the game's code, in the block entity's client tick; `simulate()` below writes it out):
  target = running && advancing ? rate / 4 : 0          (rate 0-4 from the kit clock; 0 while `start` plays)
  speed += (target - speed) * 0.0625                    (M9: chased, never multiplied)
  prevPhase = phase; phase += speed
  main controller: Choice("work_" + stab, lerp(partial, prevPhase, phase) mod 80)
At full speed the phase advances one tick a tick, so the 1,200-tick splice is exactly 15 loops of 80 (120 stitches,
40 per gene); in a brown-out (rate 1-3) it slows to rate/4 with the chase; with no power at all it runs down over about
a second and stops wherever it is (needle maybe down in the strand), progress kept, and picks up again from there.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
import kit  # noqa: E402
from kit import k, R  # noqa: E402
import splicer_geo as geo  # noqa: E402

m, DRAWER, FLAG = geo.model()
DRAWER_OPEN, DRAWER_CLOSE = kit.drawer_clips(DRAWER)
FLAG_UP, FLAG_DOWN = kit.flag_clips(FLAG)

FWD = -1           # sign of the wheel's forward turn on rotX: the top of the wheel runs toward the operator (-Z)
STITCH = 10        # ticks per stitch at full speed (one wheel turn)
LOOP = 80          # eight stitches
DROP = 6.0         # the stroke of the needle bar and its take-up lever, px (v2; v1 moved the needle 2)
S = 'headinajar:splicer.'


def W(deg):
    return R(x=FWD * deg)


def work(stutters=()):
    """Eight stitches. A stuttering stitch hangs 2 ticks a third of the way down (needle and wheel together), then
    punches late and comes up fast."""
    needle, wheel, fx = [k(0, R())], [k(0, W(0))], []
    for s in range(LOOP // STITCH):
        t0 = s * STITCH
        a0 = 360 * s
        if s in stutters:
            needle += [k(t0 + 3, R(y=-2.25), 'easeInQuad'), k(t0 + 5, R(y=-2.25)),
                       k(t0 + 8, R(y=-DROP), 'easeInQuad'), k(t0 + 10, R(), 'easeOutCubic')]
            wheel += [k(t0 + 3, W(a0 + 108)), k(t0 + 5, W(a0 + 108)), k(t0 + 10, W(a0 + 360))]
            fx += [(t0 + 4, 'sound', S + 'stutter|0.4|1.0', 'needle'), (t0 + 8, 'sound', S + 'stitch|0.3|0.9', 'needle')]
        else:
            needle += [k(t0 + 5, R(y=-DROP), 'easeInQuad'), k(t0 + 10, R(), 'easeOutCubic')]
            wheel += [k(t0 + 10, W(a0 + 360))]
            fx += [(t0 + 5, 'sound', S + 'stitch|0.3|%s' % (1.0, 0.95, 1.05, 0.98)[s % 4], 'needle')]
    return {'length': LOOP, 'loop': 'loop',
            'bones': {'needle': {'position': needle}, 'wheel': {'rotation': wheel}}, 'effects': fx}


WORK = [work(), work(stutters=(7,)), work(stutters=(3, 7))]

# start: 10 ticks from the job's start. The operator's rock: the wheel back 30 degrees (wind-up, easeOutQuad), a
# 2-tick hold, and back to rest (easeInOutSine); the needle rises 0.5 px with it. Then the phase starts from speed 0.
START = {'length': 10, 'loop': 'once',
         'bones': {'wheel': {'rotation': [k(0, W(0)), k(3, W(-30), 'easeOutQuad'), k(5, W(-30)),
                                          k(10, W(0), 'easeInOutSine')]},
                   'needle': {'position': [k(0, R()), k(3, R(y=0.5), 'easeOutQuad'), k(5, R(y=0.5)),
                                           k(10, R(), 'easeInOutSine')]}},
         'effects': [(3, 'sound', S + 'clutch|0.5|1.0', 'wheel')]}


def stop_keys():
    """The coast from a stitch boundary at full speed: one turn in 20 ticks, easeOutQuad (its first-tick speed is
    2 x 360 / 20 = 36 degrees, the work loop's), so the needle's last contact is at tick 6 (half a turn), the top at
    20; then a 3-degree rock back and settle."""
    wheel = [k(0, W(0)), k(20, W(360), 'easeOutQuad'), k(23, W(357), 'easeInOutSine'), k(26, W(360), 'easeInOutSine')]
    needle = [k(0, R()), k(6, R(y=-DROP), 'easeInQuad'), k(20, R(), 'easeOutCubic')]
    fx = [(0, 'sound', S + 'coast|0.5|1.0', 'wheel'), (6, 'sound', S + 'stitch|0.3|0.85', 'needle')]
    return wheel, needle, fx


wh, nd, fx = stop_keys()
STOP = {'length': 26, 'loop': 'hold', 'bones': {'wheel': {'rotation': wh}, 'needle': {'position': nd}},
        'effects': fx}


def finish(band):
    """stop, then `band` hiccups 10 ticks apart from 30 (v2: they must read from across the
    room): the wheel kicks BACK 30 degrees (easeInExpo, a strike; the crank grip jumps about 3 px in the open), runs on
    2 degrees past and settles; the needle and its take-up lever jolt 3 px down and kick 1 px up; all three spools hop
    2 px together (the top outline twitches), each with the hic. Then the lever lifts 1.5 px and settles (done), and
    the cassette pops up 6 px out of its deck (easeOutCubic to 6.5, settle), its top over the arm's top line."""
    wheel, needle, fx = stop_keys()
    spools = {b: [k(0, R())] for b in ('spool_1', 'spool_2', 'spool_3')}
    t = 30
    for i in range(band):
        needle += [k(t, R()), k(t + 1, R(y=-3), 'easeInExpo'), k(t + 3, R(y=1), 'easeOutQuad'),
                   k(t + 5, R(), 'easeInOutSine')]
        wheel += [k(t, W(360)), k(t + 1, W(330), 'easeInExpo'), k(t + 3, W(362), 'easeOutQuad'),
                  k(t + 5, W(360), 'easeInOutSine')]
        for b in spools:
            spools[b] += [k(t, R()), k(t + 2, R(y=2), 'easeOutCubic'), k(t + 5, R(), 'easeInQuad')]
        fx += [(t + 1, 'sound', S + 'hic|0.6|%s' % (1.0, 1.12)[i], 'needle')]
        t += 10
    # the lift (the lever up and parked) and the cassette, 1 tick behind it (M4)
    needle += [k(t, R()), k(t + 3, R(y=1.5), 'easeOutCubic'), k(t + 6, R(y=1.5)), k(t + 10, R(), 'easeInOutSine')]
    cas = [k(0, R()), k(t + 1, R()), k(t + 2, R(y=-0.5), 'easeOutQuad'), k(t + 6, R(y=6.5), 'easeOutCubic'),
           k(t + 9, R(y=6), 'easeInOutSine')]
    fx += [(t + 2, 'sound', S + 'eject|0.6|1.0', 'cassette'), (t + 6, 'particle', 'minecraft:wax_on', 'cassette')]
    bones = {'wheel': {'rotation': wheel}, 'needle': {'position': needle}, 'cassette': {'position': cas}}
    if band:
        bones.update({b: {'position': v} for b, v in spools.items()})
    return {'length': t + 10, 'loop': 'hold', 'bones': bones, 'effects': fx}


FINISH = [finish(b) for b in range(3)]


def pick(i):
    b = 'spool_%d' % i
    return {'length': 10, 'loop': 'hold',
            'bones': {b: {'position': [k(0, R()), k(1, R(y=-0.25), 'easeOutQuad'), k(4, R(y=2.5), 'easeOutCubic'),
                                       k(5, R(y=2.5)), k(8, R(), 'easeInQuad'), k(9, R(y=0.5), 'easeOutQuad'),
                                       k(10, R(), 'easeInQuad')]}},
            'effects': [(1, 'sound', S + 'thread|0.4|%s' % (0.95, 1.0, 1.05)[i - 1], 'spool_%d' % i),
                        (8, 'sound', S + 'seat|0.4|1.0', 'spool_%d' % i)]}


PICK = [pick(i) for i in (1, 2, 3)]


def refuse():
    """The house rhythm (up 1 px at 2 and 6, down at 4 and 8, easeInOutSine) on the three spools, each a tick after
    the one on its right (as seen from the front: spool_1 first)."""
    E = 'easeInOutSine'
    seq = {1: [(2, 1), (4, 0), (6, 1), (8, 0)],
           2: [(3, 1), (5, 0), (7, 1), (8, 0)],
           3: [(4, 1), (6, 0), (7, 0.6), (8, 0)]}
    bones = {}
    for i, keys in seq.items():
        bones['spool_%d' % i] = {'position': [k(0, R())] + [
            k(t, R(y=v), 'easeInQuad' if v == 0 else E) for t, v in keys]}
    return {'length': 8, 'loop': 'once', 'bones': bones,
            'effects': [(0, 'sound', 'headinajar:machine.rattle|0.4|1.0', None)]}


REFUSE = refuse()

CLIPS = ([('start', START)] + [('work_%d' % i, c) for i, c in enumerate(WORK)] + [('stop', STOP)]
         + [('finish_%d' % i, c) for i, c in enumerate(FINISH)]
         + [('pick_%d' % i, c) for i, c in zip((1, 2, 3), PICK)] + [('refuse', REFUSE)]
         + [('drawer_open', DRAWER_OPEN), ('drawer_close', DRAWER_CLOSE), ('flag_up', FLAG_UP),
            ('flag_down', FLAG_DOWN)])

# sounds carry no locator in GeckoLib 4.9.3 (it reads only "effect"); the mod's handler plays them at the machine.
# The locator named in the source above is where the sound belongs (for the notes); it is dropped here.
for _n, _c in CLIPS:
    _c['effects'] = [(t, ch, e, None if ch == 'sound' else loc) for (t, ch, e, loc) in _c.get('effects', [])]

# ---- the code: the work phase and the spools (what the game's client tick does) -----------------------------
CHASE = 0.0625
SPOOL_DEG = 9.0    # spool degrees per work tick: 400 ticks a gene = 3,600 degrees = 10 whole turns


def simulate(targets, speed=0.0, phase=0.0):
    out = [(speed, phase)]
    for tg in targets:
        speed += (tg - speed) * CHASE
        phase += speed
        out.append((speed, phase))
    return out


def review_work(name, frames, clip, spool_on=1, length=None):
    """Bake a stretch of work (phase from the simulation) into keys: needle and wheel sampled from the clip's own
    keys at the phase, the active spool turned by code."""
    def sample(keys, t):
        keys = sorted(keys, key=lambda x: x[0])
        t = t % LOOP
        for (t0, v0, *_), (t1, v1, e, a, _i) in zip(keys, keys[1:]):
            if t0 <= t <= t1:
                f = kit.ease(e, a, (t - t0) / float(t1 - t0)) if t1 > t0 else 1
                return [v0[j] + (v1[j] - v0[j]) * f for j in range(3)]
        return keys[-1][1]
    nk, wk, sk = [], [], []
    for t, (sp, ph) in enumerate(frames):
        nk.append(k(t, sample(clip['bones']['needle']['position'], ph)))
        wk.append(k(t, sample(clip['bones']['wheel']['rotation'], ph)))
        sk.append(k(t, R(y=SPOOL_DEG * ph)))
    return (name, {'length': len(frames) - 1, 'loop': 'hold', 'review': True,
                   'bones': {'needle': {'position': nk}, 'wheel': {'rotation': wk},
                             'spool_%d' % spool_on: {'rotation': sk}}, 'effects': []})


spinup = simulate([1.0] * 60)
brown = simulate([0.5] * 60, speed=1.0, phase=0.0)
REVIEW = [review_work('_review_spinup', spinup, WORK[0]), review_work('_review_brownout', brown, WORK[0]),
          ('_review_axes', {'length': 1, 'loop': 'hold', 'review': True,
                            'bones': {'wheel': {'rotation': [k(0, W(45))]}}, 'effects': []}),
          ('_review_full', {'length': 80, 'loop': 'hold', 'review': True, 'bones': {
              'wheel': WORK[0]['bones']['wheel'], 'needle': WORK[0]['bones']['needle'],
              'spool_2': {'rotation': [k(0, R()), k(80, R(y=720))]}}, 'effects': []}),
          # every hanger of the sweep: the wheel at 15-degree steps (check against the bench, bed, pillar, spools)
          ('_review_sweep', {'length': 24, 'loop': 'hold', 'review': True, 'bones': {
              'wheel': {'rotation': [k(t, W(15 * t)) for t in range(25)]},
              'cassette': {'position': [k(0, R(y=-0.5)), k(24, R(y=6.5))]},
              'needle': {'position': [k(0, R(y=1.5)), k(24, R(y=-DROP))]},
              'spool_1': {'position': [k(0, R()), k(24, R(y=2.5))]}}, 'effects': []}),
          ]

if __name__ == '__main__':
    n = kit.write_clips(geo.PATH, CLIPS, code_channels=m.code, review=geo.REVIEW, review_clips=REVIEW)
    print('wrote %d clips' % n)
    print(kit.effects_table(CLIPS))
    print('spin-up: speed at 10/20/40 ticks %.2f %.2f %.2f; phase %.1f %.1f %.1f' % (
        spinup[10][0], spinup[20][0], spinup[40][0], spinup[10][1], spinup[20][1], spinup[40][1]))

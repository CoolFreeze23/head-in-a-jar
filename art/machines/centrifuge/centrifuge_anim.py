"""Centrifuge: every clip, keyed by game tick, written into centrifuge.bbmodel (and the review copy).

Source of truth for the Centrifuge's animations; hand edits in Blockbench are overwritten by the next run.

    python art/machines/centrifuge/centrifuge_anim.py

Clips (house kit): drawer_open 5, drawer_close 6 (by the synced `open` bit), flag_up 6, flag_down 6 (by `hasOutput`),
refuse 8 (block event 1; the drawer rattles 0.5 px). Everything speed-driven is CODE, never keyed (the kit's rule for code channels):
the rotor's spin and tilt and the four hangers' swing. `simulate()` below is the game's code written out in
Python (the same per-tick update the block entity's client tick must run); the review copy gets `_review_*` clips
baked from it, one key per tick, for renders only.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
import kit  # noqa: E402
from kit import k, R  # noqa: E402
import centrifuge_geo as geo  # noqa: E402

m, DRAWER, FLAG = geo.model()
DRAWER_OPEN, DRAWER_CLOSE = kit.drawer_clips(DRAWER)
FLAG_UP, FLAG_DOWN = kit.flag_clips(FLAG)
# refuse: the house rhythm (2, 4, 6, 8 ticks, easeInOutSine) as a knock to the drawer's RIGHT along +X, read from the
# front: +1 px, back to 0, +1 px, 0. One-sided, because -X would drive the drawer 1 px into the left cabinet end
# (x -7..-5); to the right it slides behind the lamp bezel (z -6..-5, in front of the drawer at z -4): no contact.
REFUSE = {'length': 8, 'loop': 'once',
          'bones': {'drawer': {'position': [k(0, R()), k(2, R(x=1), 'easeInOutSine'), k(4, R(), 'easeInOutSine'),
                                            k(6, R(x=1), 'easeInOutSine'), k(8, R(), 'easeInOutSine')]}},
          'effects': [(0, 'sound', 'headinajar:machine.rattle|0.4|1.0', None)]}
# refuse_open: the same refusal while the drawer is out (screen open). Out, the drawer's +X knock would strike the lamp
# bezel (x 1..5, z -6..-5), so it rattles in and out along Z round its open pose instead: -2.5, -3.5, -2.5, -3; out,
# its front is at the cabinet ends' plane (z -7), so the rattle shows as the front jumping in and out of that line.
REFUSE_OPEN = {'length': 8, 'loop': 'once',
               'bones': {'drawer': {'position': [k(0, R(z=-3)), k(2, R(z=-2.5), 'easeInOutSine'),
                                                 k(4, R(z=-3.5), 'easeInOutSine'), k(6, R(z=-2.5), 'easeInOutSine'),
                                                 k(8, R(z=-3), 'easeInOutSine')]}},
               'effects': [(0, 'sound', 'headinajar:machine.rattle|0.4|1.0', None)]}

CLIPS = [('drawer_open', DRAWER_OPEN), ('drawer_close', DRAWER_CLOSE), ('flag_up', FLAG_UP),
         ('flag_down', FLAG_DOWN), ('refuse', REFUSE), ('refuse_open', REFUSE_OPEN)]

# ---- the code motion (the design), per game tick ------------------------------------------------------------------
CHASE = 0.0625        # speed chase per tick
DEG_PER_TICK = 45.0   # rotor degrees per tick at s = 1 (a turn per 8 ticks)
SWING_MAX = 60.0      # hanger outward degrees at s = 1 (target 60 s^2)
STIFF, DAMP = 0.08, 0.12   # a damping of 0.35: measured, that never passes vertical (see notes)
TILT_MAX = 3.5        # imbalance tilt, degrees at s = 1 (v1 1.5: invisible)


def simulate(targets, s=0.0, angle=0.0, theta=0.0, vel=0.0):
    """targets: s* per tick. Returns per-tick (s, angle, theta) from tick 0 (the start state) on."""
    out = [(s, angle, theta)]
    for st in targets:
        s += (st - s) * CHASE
        angle += DEG_PER_TICK * s
        vel = vel * (1 - DAMP) + STIFF * (SWING_MAX * s * s - theta)
        theta += vel
        out.append((s, angle, theta))
    return out


def pose_keys(frames, tilt=False, a0=None):
    """Per-tick keys for rotor and hangers from simulated frames."""
    rot, hang = [], {h[0]: [] for h in geo.HANGERS}
    base = frames[0][1] if a0 is None else a0
    for t, (s, a, th) in enumerate(frames):
        rot.append(k(t, R(x=TILT_MAX * s if tilt else 0, y=round(a - base, 3))))
        for bone, _, ax, sg in geo.HANGERS:
            v = [0, 0, 0]
            v[ax] = round(sg * th, 3)
            hang[bone].append(k(t, v))
    bones = {'rotor': {'rotation': rot}}
    for b, keys in hang.items():
        bones[b] = {'rotation': keys}
    return bones


def review(name, frames, tilt=False):
    return (name, {'length': len(frames) - 1, 'loop': 'hold', 'review': True,
                   'bones': pose_keys(frames, tilt), 'effects': []})


# steady state at full speed (s = 1, hangers at 60 after the spring settles)
FULL = (1.0, 0.0, SWING_MAX)
spinup = simulate([1.0] * 40)
coast = simulate([0.0] * 60, s=1.0, theta=SWING_MAX)
brown = simulate([0.25] * 40, s=1.0, theta=SWING_MAX)
full = [(1.0, DEG_PER_TICK * t, SWING_MAX) for t in range(17)]

REVIEW = [review('_review_full', full), review('_review_unbalanced', full, tilt=True),
          review('_review_spinup', spinup), review('_review_coast', coast), review('_review_brownout', brown),
          # the tilt exaggerated x3 (10.5 degrees) only to show its axis and direction in a still; never in game
          ('_review_tilt_axis', {'length': 8, 'loop': 'hold', 'review': True, 'bones': {
              'rotor': {'rotation': [k(t, R(x=10.5, y=45 * t)) for t in range(9)]},
              **{b: {'rotation': [k(0, [60 * sg if ax == 0 else 0, 0, 60 * sg if ax == 2 else 0])]}
                 for b, _, ax, sg in geo.HANGERS}}, 'effects': []}),
          # the hangers alone, each pushed +30 on its outward sign: the axis check
          ('_review_axes', {'length': 1, 'loop': 'hold', 'review': True, 'bones': {
              b: {'rotation': [k(0, [30 * sg if ax == 0 else 0, 0, 30 * sg if ax == 2 else 0])]}
              for b, _, ax, sg in geo.HANGERS}, 'effects': []})]


def report():
    def row(name, frames, ticks):
        return name + ': ' + ', '.join('t%d s=%.2f th=%.1f' % (t, frames[t][0], frames[t][2]) for t in ticks)
    print(row('spin-up', spinup, [0, 5, 10, 15, 20, 25, 30, 40]))
    print('  spin-up peak theta %.1f at t%d' % max((f[2], i) for i, f in enumerate(spinup)))
    print(row('coast', coast, [0, 5, 10, 15, 20, 25, 30, 40, 50, 60]))
    print('  coast min theta %.2f at t%d' % min((f[2], i) for i, f in enumerate(coast)))
    print(row('brown-out', brown, [0, 10, 20, 30, 40]))
    # where the swung tube's outer foot lands (radius from the axis), at the spring's peak
    peak = max(f[2] for f in spinup)
    for th in (60.0, peak):
        r = math.radians(th)
        rad = 5 + 1 * math.cos(r) + 6 * math.sin(r)  # pivot radius 5, foot 6 below
        print('  tube foot radius at %.1f deg: %.2f px (with the 1 px half width across: %.2f)' % (
            th, rad, math.hypot(rad, 1)))


if __name__ == '__main__':
    n = kit.write_clips(geo.PATH, CLIPS, code_channels=m.code, review=geo.REVIEW, review_clips=REVIEW)
    print('wrote %d clips' % n)
    print(kit.effects_table(CLIPS))
    report()

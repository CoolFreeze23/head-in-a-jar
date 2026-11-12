"""Injection Rig: every clip, keyed by game tick, written into injection_rig.bbmodel (and the review copies).

Source of truth for the Rig's animations; hand edits in Blockbench are overwritten by the next run.

    python art/machines/injection_rig/rig_anim.py

THE INJECT (the wait is 1.5 s). The server starts a pending inject on the Inject press and applies it at
its tick 30; the client plays one show on that clock (t = ticks since the `inject` block event, partial included):

  in the 30 (the wait)    0     the latch lets go (`rig.unlatch`); the syringe ticks up 0.5 px
                          2-8   the lift: 4.5 px up out of the sheath (easeOutCubic), settles at +4 (the needle clear)
                          7-22  the swing toward the player (CODE yaw on nozzle_arm, a tick before the lift settles):
                                7-9 a wind-up back 8 % of the angle, 9-18 out to 104 % (easeInOutSine), 18-21 settle
                          19-25 the syringe tips out about its hinge (the drawn thumb's top, on the arm's rail): keyed
                                rotZ 38 at 23, 35.2 at 25; the needle now points out and down toward the player
                          25-27 the jab's wind-up: 20 % of the jab backwards (easeOutQuad); 27-28 hold
                          28-30 THE JAB (easeInExpo): the syringe tips a further 14.8 degrees to 50 while its hinge slides
                                6.2 px down the rail, so the needle tip travels 4 px along its own line; it lands on
                                tick 30, the server's tick (the server plays the hiss, the 1-damage flinch and the
                                plaster); the needle tip then stands about 22 px from the neck, y about 13
  the tail (17 ticks, chosen by the `result` event: accepted / rejected / mutated)
                          30-32 the plunger driven home by the jab's momentum (accepted, mutated); rejected: half way,
                                then kicked back up with a burp, the needle jolts back 37 % of the jab and the arm
                                recoils 18 degrees (CODE); mutated: the needle startles back half the jab and the arm
                                shudders 8 degrees (CODE)
                          32-34 withdraw (the jab reversed); 34-37 the syringe hangs upright; 34-43 the swing home (CODE);
                          43-45 the drop into the sheath (contact 45, `rig.holster`), 46 bounce, 47 settled
Cut from 54: its 6-tick hold before the jab (22-28 -> the tip-out 19-25, which is itself the aim, then the
3-tick wind-up and hold), its 10-tick plunger (32-42 -> 2 ticks of momentum at 30-32), its 12-tick return (42-54 ->
34-45, overlapping the withdrawal). The show before the needle lands is 30 ticks; the tail is 17.

Clips (main controller: syringe position and rotation, plunger position; the arm's rotation is code, never keyed):
  inject 30 hold; accepted / rejected / mutated 17 hold (seeked at t - 30); cancel 14 hold (from the cancel moment);
  prime 8 / unprime 6 hold (the `loaded` bit: the plunger drawn 3 px while a cassette is in).
Refusal controller (syringe position only): refuse 8 once.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import rig_geo as geo  # noqa: E402
import kit  # noqa: E402  (rig_geo put the kit on the path)
from kit import k, R  # noqa: E402

S = 'headinajar:rig.'
DRAW, LIFT, WIND = geo.DRAW, geo.LIFT, geo.WIND
TAIL0 = 30                     # the server's tick: the jab lands, the tails start
TILT = 50.0                    # the syringe's tip-out at the jab, degrees, about its top (+rotZ swings the needle out)
L_TIP = 18.5                   # the needle's tip below the hinge (the thumb's top, y 27.5 at rest)
JAB = 4.0                      # the needle's travel along itself at the jab, px
_s, _c = math.sin(math.radians(TILT)), math.cos(math.radians(TILT))
# The jab moves the needle 4 px along its own line with the hinge kept on the rail: the syringe tips a further D_TILT
# degrees while the hinge slides D_DROP px down the rail (tip motion L*d*(cos, sin) + (0, -h) = JAB*(sin, -cos)).
D_TILT = math.degrees(JAB * _s / (L_TIP * _c))
D_DROP = L_TIP * math.radians(D_TILT) / _s
AIM_TILT = TILT - D_TILT       # the tip-out at the hover


def jab(f):
    """(syringe position, rotation) f of the way through the jab: 0 the hover, 1 struck, negative = wind-up."""
    return R(y=round(LIFT - D_DROP * f, 3)), R(z=round(AIM_TILT + D_TILT * f, 3))


def Y(v):
    return R(y=v)


HOVER = R(y=LIFT)
WOUND = jab(-0.2)
STRUCK = jab(1.0)

# ---- the show before the needle lands ----------------------------------------------------------------------------
INJECT = {'length': 30, 'loop': 'hold',
          'bones': {'syringe': {'position': [k(0, Y(0)), k(1, Y(0.5), 'easeOutQuad'), k(2, Y(0.5)),
                                             k(6, Y(LIFT + 0.5), 'easeOutCubic'), k(8, HOVER, 'easeInOutSine'),
                                             k(25, HOVER), k(27, WOUND[0], 'easeOutQuad'), k(28, WOUND[0]),
                                             k(30, STRUCK[0], 'easeInExpo')],
                                'rotation': [k(0, R()), k(19, R()), k(23, R(z=AIM_TILT + 3), 'easeInOutSine'),
                                             k(25, R(z=AIM_TILT), 'easeInOutSine'), k(27, WOUND[1], 'easeOutQuad'),
                                             k(28, WOUND[1]), k(30, STRUCK[1], 'easeInExpo')]},
                    # the thumb lags the lift a tick (M4) and overshoots as the syringe stops
                    'plunger': {'position': [k(0, Y(DRAW)), k(3, Y(DRAW)), k(5, Y(DRAW - 0.5), 'easeOutQuad'),
                                             k(8, Y(DRAW + 0.25), 'easeInOutSine'), k(10, Y(DRAW), 'easeInOutSine'),
                                             k(30, Y(DRAW))]}},
          'effects': [(0, 'sound', S + 'unlatch|0.5|1.0', None), (7, 'sound', S + 'servo|0.4|1.0', None),
                      (25, 'sound', S + 'prime|0.4|1.1', None)]}


def tail(result):
    """The 17 ticks after the server's tick, chosen by the `result` event. Tick 0 here is inject tick 30."""
    home = [k(13, HOVER), k(15, Y(0), 'easeInQuad'), k(16, Y(0.5), 'easeOutQuad'), k(17, Y(0), 'easeInQuad')]
    fx = [(4, 'sound', S + 'servo|0.4|0.9', None), (15, 'sound', S + 'holster|0.5|1.0', None)]
    if result == 'rejected':
        back = [(0, 1.0, None), (1, 1.0, None), (2, 0.625, 'easeOutQuad'), (3, 0.75, 'easeInOutSine')]
        plu = [k(0, Y(DRAW)), k(1, Y(1.5), 'easeInQuad'), k(3, Y(DRAW + 0.5), 'easeOutQuad'),
               k(4, Y(DRAW), 'easeInOutSine'), k(6, Y(DRAW)), k(12, Y(0), 'easeInOutSine')]
        fx += [(1, 'sound', S + 'burp|0.6|0.8', None), (3, 'sound', S + 'counter|0.4|0.9', None)]
        up = 5
    else:
        plu = [k(0, Y(DRAW)), k(2, Y(0), 'easeInQuad'), k(3, Y(0.25), 'easeOutQuad'), k(4, Y(0), 'easeInQuad')]
        if result == 'mutated':
            back = [(0, 1.0, None), (2, 1.0, None), (3, 0.5, 'easeOutQuad')]
            fx += [(2, 'sound', S + 'plunge|0.4|1.0', None), (3, 'sound', S + 'sproing|0.5|1.0', None)]
            up = 5
        else:
            back = [(0, 1.0, None), (2, 1.0, None)]
            fx += [(2, 'sound', S + 'plunge|0.4|1.0', None), (3, 'sound', S + 'ding|0.5|1.2', None)]
            up = 4
    syr = [k(t, jab(f)[0], e) for t, f, e in back] + [k(up, HOVER, 'easeOutCubic')] + home
    rot = [k(t, jab(f)[1], e) for t, f, e in back] + [k(up, R(z=AIM_TILT), 'easeOutCubic'),
                                                       k(up + 3, R(), 'easeInOutSine'), k(17, R())]
    return {'length': 17, 'loop': 'hold',
            'bones': {'syringe': {'position': syr, 'rotation': rot}, 'plunger': {'position': plu}},
            'effects': sorted(fx)}


TAILS = {r: tail(r) for r in ('accepted', 'rejected', 'mutated')}

# cancel (lab.rig.hold_still): from wherever the show is (a 2-tick controller transition into the first key), the
# syringe stands upright and gives a small start up, waits while the arm swings home (code, cancel ticks 2-10), drops
# into the sheath. The plunger stays drawn: nothing was spent.
CANCEL = {'length': 14, 'loop': 'hold',
          'bones': {'syringe': {'position': [k(0, HOVER), k(1, Y(LIFT + 0.5), 'easeOutQuad'),
                                             k(3, HOVER, 'easeInOutSine'), k(10, HOVER),
                                             k(12, Y(0), 'easeInQuad'), k(13, Y(0.5), 'easeOutQuad'),
                                             k(14, Y(0), 'easeInQuad')],
                                'rotation': [k(0, R()), k(14, R())]},
                    'plunger': {'position': [k(0, Y(DRAW)), k(14, Y(DRAW))]}},
          'effects': [(0, 'sound', S + 'cancel|0.5|1.0', None), (2, 'sound', S + 'servo|0.4|0.9', None),
                      (12, 'sound', S + 'holster|0.5|1.0', None)]}

# the refusal (block event 1: not in a body, no power, no room for the serum): the syringe hops in its sheath twice,
# seen from the front at the machine's right edge (a hop, not the house's +a/-a: the flange sits on its stop)
REFUSE = {'length': 8, 'loop': 'once',
          'bones': {'syringe': {'position': [k(0, Y(0)), k(2, Y(1.5), 'easeOutQuad'), k(4, Y(0), 'easeInQuad'),
                                             k(6, Y(1), 'easeOutQuad'), k(8, Y(0), 'easeInQuad')]}},
          'effects': [(0, 'sound', 'headinajar:machine.rattle|0.4|1.0', None)]}

# loaded: the plunger draws 3 px (the syringe fills from the globe) / unloaded by hand: it sinks back
PRIME = {'length': 8, 'loop': 'hold',
         'bones': {'plunger': {'position': [k(0, Y(0)), k(1, Y(-0.25), 'easeOutQuad'),
                                            k(5, Y(DRAW + 0.25), 'easeOutCubic'), k(7, Y(DRAW), 'easeInOutSine'),
                                            k(8, Y(DRAW))]}},
         'effects': [(1, 'sound', S + 'fill|0.4|1.0', None)]}
UNPRIME = {'length': 6, 'loop': 'hold',
           'bones': {'plunger': {'position': [k(0, Y(DRAW)), k(4, Y(0), 'easeInQuad'), k(5, Y(0.25), 'easeOutQuad'),
                                              k(6, Y(0), 'easeInQuad')]}},
           'effects': [(4, 'sound', S + 'drain|0.4|1.0', None)]}

CLIPS = [('inject', INJECT), ('accepted', TAILS['accepted']), ('rejected', TAILS['rejected']),
         ('mutated', TAILS['mutated']), ('cancel', CANCEL), ('refuse', REFUSE), ('prime', PRIME),
         ('unprime', UNPRIME)]


# ---- the code: the arm's yaw (what the renderer's `pose` writes, every frame, absolutely) ------------------------
def eio_sine(x):
    return 0.5 - 0.5 * math.cos(math.pi * max(0.0, min(1.0, x)))


def eo_quad(x):
    x = max(0.0, min(1.0, x))
    return 1 - (1 - x) * (1 - x)


def aim(dx, dz):
    """The aim angle in degrees from the player's position relative to the machine, in the machine's own frame
    (dx toward its right, dz toward its back; the front is -z): 0 = the holster side, 90 = straight out of the front,
    180 = its left. Clamped to 30..180 (the front half, less the holster's own 30 degrees); behind it: the nearer end."""
    a = math.degrees(math.atan2(-dz, dx))           # -180..180
    if a < -90:
        a += 360                                    # behind-left reads as past 180
    return max(30.0, min(180.0, a))


RECOIL = 18.0       # rejected: the arm flinches back toward the holster, degrees
SHUDDER = 8.0       # mutated: the arm shudders, degrees


def arm_yaw(t, phi, tc=None, result='accepted'):
    """Degrees on nozzle_arm.rotY (Blockbench's sense; + swings the syringe from the right side toward the front).
    t: ticks since the inject event (with the partial tick); phi: aim(); tc: the cancel moment, if cancelled;
    result: the `result` event's answer (it changes only ticks 30-34)."""
    if tc is not None and t >= tc:
        y0 = arm_yaw(tc, phi)
        return y0 * (1 - eio_sine((t - tc - 2) / 8.0))
    if t < 7:
        return 0.0
    if t < 9:
        return -0.08 * phi * eo_quad((t - 7) / 2.0)
    if t < 18:
        return -0.08 * phi + (1.12 * phi) * eio_sine((t - 9) / 9.0)
    if t < 21:
        return 1.04 * phi - 0.04 * phi * eio_sine((t - 18) / 3.0)
    if t < 30:
        return phi
    if t < 34:                                       # the result's answer on the arm (an outline change)
        if result == 'rejected':
            return phi - RECOIL * (eo_quad((t - 30) / 2.0) if t < 32 else 1.0)
        if result == 'mutated':
            return phi + SHUDDER * math.sin(math.pi * (t - 30) * 1.5) * (1 - (t - 30) / 4.0)
        return phi
    start = phi - RECOIL if result == 'rejected' else phi
    if t < 41:
        return start - (start + 0.03 * phi) * eio_sine((t - 34) / 7.0)
    if t < 43:
        return -0.03 * phi * (1 - eio_sine((t - 41) / 2.0))
    return 0.0


# ---- review clips (the code motion baked in; only in the review copies) ------------------------------------------
def sample(keys, t):
    keys = sorted(keys, key=lambda x: x[0])
    if t <= keys[0][0]:
        return list(keys[0][1])
    for (t0, v0, *_), (t1, v1, e, a, _i) in zip(keys, keys[1:]):
        if t0 <= t <= t1:
            f = kit.ease(e, a, (t - t0) / float(t1 - t0)) if t1 > t0 else 1
            return [v0[j] + (v1[j] - v0[j]) * f for j in range(3)]
    return list(keys[-1][1])


def _clip_pose(clip, t):
    b = clip['bones']
    sp = sample(b['syringe']['position'], t) if 'position' in b.get('syringe', {}) else [0, 0, 0]
    sr = sample(b['syringe']['rotation'], t)[2] if 'rotation' in b.get('syringe', {}) else 0.0
    py = sample(b['plunger']['position'], t)[1] if 'plunger' in b else 0.0
    return sp, sr, py


def pose_at(t, phi, result='accepted', tc=None):
    """(yaw, syringe position [x, y, z] in the arm's frame, syringe tilt rotZ, plunger y) at show tick t."""
    if tc is not None and t >= tc:
        s = t - tc
        sp, sr, py = _clip_pose(CANCEL, s)
        if s < 2:     # the controller's 2-tick transition from the show's pose into the cancel clip
            f = s / 2.0
            _, sp0, sr0, py0 = pose_at(tc, phi, result)
            sp = [sp0[j] + (sp[j] - sp0[j]) * f for j in range(3)]
            sr = sr0 + (sr - sr0) * f
        return arm_yaw(t, phi, tc), sp, sr, py
    if t <= TAIL0:
        sp, sr, py = _clip_pose(INJECT, t)
    else:
        sp, sr, py = _clip_pose(TAILS[result], t - TAIL0)
    return arm_yaw(t, phi, result=result), sp, sr, py


def review_show(name, phi, result='accepted', tc=None, length=None):
    n = length or (TAIL0 + 17 if tc is None else tc + 14)
    yk, sk, rk, pk = [], [], [], []
    for i in range(0, n + 1):
        yaw, sp, sr, py = pose_at(i, phi, result, tc)
        yk.append(k(i, R(y=yaw)))
        sk.append(k(i, sp))
        rk.append(k(i, R(z=sr)))
        pk.append(k(i, Y(py)))
    return (name, {'length': n, 'loop': 'hold', 'review': True,
                   'bones': {'nozzle_arm': {'rotation': yk}, 'syringe': {'position': sk, 'rotation': rk},
                             'plunger': {'position': pk}}, 'effects': []})


REVIEW = [
    review_show('_review_ok90', 90),
    review_show('_review_rej135', 135, 'rejected'),
    review_show('_review_rej90', 90, 'rejected'),
    review_show('_review_mut45', 45, 'mutated'),
    review_show('_review_ok30', 30),
    review_show('_review_ok180', 180),
    review_show('_review_cancel20', 90, tc=20),
    review_show('_review_cancel5', 90, tc=5),
    ('_review_loaded', {'length': 1, 'loop': 'hold', 'review': True,
                        'bones': {'plunger': {'position': [k(0, Y(DRAW)), k(1, Y(DRAW))]}}, 'effects': []}),
    # the sweep: the arm through 0..180 (one tick per 7.5 degrees) with the syringe lifted and wound up, plunger drawn
    ('_review_sweep', {'length': 24, 'loop': 'hold', 'review': True,
                       'bones': {'nozzle_arm': {'rotation': [k(i, R(y=7.5 * i)) for i in range(25)]},
                                 'syringe': {'position': [k(0, Y(LIFT + WIND)), k(24, Y(LIFT + WIND))]},
                                 'plunger': {'position': [k(0, Y(DRAW)), k(24, Y(DRAW))]}}, 'effects': []}),
]

EMPTY_GEO = 'build/machines-review/injection_rig_empty.bbmodel'
EMPTY_REVIEW = 'build/machines-review/injection_rig_empty_review.bbmodel'

if __name__ == '__main__':
    m = geo.model()
    n = kit.write_clips(geo.PATH, CLIPS, code_channels=m.code, review=geo.REVIEW, review_clips=REVIEW)
    # the empty-globe review copy (geometry with the unlit milk-glass globe and an empty barrel; same clips)
    me = geo.model(loaded=False)
    me.write(EMPTY_GEO, check=False, keep_animations=False)
    kit.write_clips(EMPTY_GEO, CLIPS, code_channels=me.code, review=EMPTY_REVIEW, review_clips=REVIEW)
    print('wrote %d clips (+%d review)' % (n, len(REVIEW)))
    print(kit.effects_table(CLIPS))
    print('aim tilt %.1f, jab: tilt +%.1f, hinge down %.2f px; hover %s, wound %s, struck %s' % (
        AIM_TILT, D_TILT, D_DROP, HOVER, WOUND, STRUCK))
    for phi in (30, 90, 180):
        for res in ('accepted', 'rejected', 'mutated'):
            print('phi %3d %-8s: yaw at 9/14/18/21/30/31/32/33/34/38/41/43 = %s' % (
                phi, res, ' '.join('%.1f' % arm_yaw(t, phi, result=res)
                                   for t in (9, 14, 18, 21, 30, 31, 32, 33, 34, 38, 41, 43))))

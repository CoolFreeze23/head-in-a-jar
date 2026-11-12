"""Body Printer: every clip, keyed by game tick, written into body_printer.bbmodel.

Source of truth for the Printer's animations; hand edits in Blockbench are overwritten by the next run.

    python art/machines/body_printer/printer_anim.py [--review]

--review also writes build/machines-review/printer_review.bbmodel (every eased segment baked per tick, plus the
`_review_*` clips) and the review variants used for the boards (stand-in body clipped at a beat's print and develop
planes, materials shown, door glass removed): see VARIANTS.

Controllers (the game's; one bone channel never belongs to two of them):
  work    ring.position, carriage.position: `print` (the job, seeked at the print clock), else `loaded`/`unloaded`
          by the materials bits (KitController.toggle).
  door    door.rotation: `deliver` from print tick 1160 (seeked at t - 1160), held on its last frame while the body
          stands in front (`held`), then `shut`; with no job and nothing held, `open`/`close` by the `open` bit.
  refuse  ring.rotation, carriage.rotation: `refuse` on block event 1 with no job, `refuse_busy` with one (8 ticks
          each); otherwise, while a job runs stalled (workRate 0 for 20 ticks) between print ticks 160 and 1120,
          `stall` (the bar's free end sags), then `unstall` when power returns.

Signs (keyframes = Blockbench display = GeckoLib): door rotY +a swings the free edge (+X) out to -Z (checked by
render); ring rotZ -a lifts the bar's free end (pivot at the sleeve, +X); carriage posX - goes left (toward -X).
"""
import copy
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
import kit  # noqa: E402
from kit import k, R  # noqa: E402
import printer_geo as geo  # noqa: E402

REVIEW_DIR = geo.REVIEW_DIR
REVIEW = REVIEW_DIR + '/printer_review.bbmodel'

TOP = 23            # ring posY at the top: the bar's top at y 25, the head's top (the print plane) at y 26
CL, CC, CR = 0, 6, 9     # carriage posX: the bar's free end = rest (x -8..-4), centre (x -2..2), sleeve end (x 1..5)
STEP = 40           # the server's squish period: one 1 px step per squish
STEPS = 23          # steps at 40, 80, ... 920
DEV0, DEV1 = 1008, 1140   # the develop pass (the bar back down)
DELIVER0 = 1160     # the door controller's `deliver` starts here (print tick)


def P(x=0, y=0):
    return R(x=x, y=y)


# ---- print (1,200, hold; seeked at the print clock) ---------------------------------------------------------------
def ring_keys():
    ks = [k(0, P())]
    for n in range(1, STEPS + 1):
        t = STEP * n
        ks += [k(t - 3, P(y=n - 1)), k(t, P(y=n + 0.15), 'easeOutCubic'), k(t + 2, P(y=n), 'easeInOutSine')]
    ks += [k(DEV0 - 4, P(y=TOP)), k(DEV0, P(y=TOP + 0.5), 'easeOutQuad'),          # wind-up: 0.5 px up (the sleeve is 0.5 under the sign)
           k(DEV1, P(), 'easeInOutSine'),                                            # the develop pass, down
           k(DEV1 + 2, P(y=0.4), 'easeOutQuad'), k(DEV1 + 5, P(), 'easeInQuad'),     # lands on the ground, a bounce
           k(1200, P())]
    return ks


def carriage_keys():
    ks = [k(0, P(CC)), k(6, P(CC)), k(8, P(CC + 0.5), 'easeOutQuad'), k(16, P(CL - 0.4), 'easeOutCubic'),
          k(19, P(CL), 'easeInOutSine')]
    for n in range(1, STEPS + 1):
        t = STEP * n
        ks += [k(t + 2, P(CL)), k(t + 18, P(CR), 'easeInOutSine'), k(t + 21, P(CR)),
               k(t + 37, P(CL), 'easeInOutSine')]

    def taps(x, t0):
        return [k(t0, P(x)), k(t0 + 3, P(x, -0.5), 'easeInQuad'), k(t0 + 5, P(x), 'easeOutQuad'),
                k(t0 + 8, P(x, -0.5), 'easeInQuad'), k(t0 + 10, P(x), 'easeOutQuad')]
    # the measuring pause at the shoulders: two taps at the left end, the tape-measure zip across, two taps at the right
    ks += taps(CL, 960)
    ks += [k(978, P(CR), 'easeInOutCubic')]
    ks += taps(CR, 978)[1:]
    ks += [k(990, P(CR)), k(996, P(CC), 'easeInOutSine'), k(999, P(CC))]
    # the Collar Cam: three ratchet clicks
    for i, t in enumerate((1000, 1002, 1004)):
        ks += [k(t, P(CC, -0.5), 'easeInQuad'), k(t + 1, P(CC), 'easeOutQuad')]
    # after the develop the head goes home to the bar's free end (clear of where the body will stand, and the rest
    # pose, so nothing jumps when the job ends with the materials used up)
    ks += [k(1144, P(CC)), k(1146, P(CC + 0.4), 'easeOutQuad'), k(1154, P(CL), 'easeInQuad'),
           k(1155, P(CL + 0.4), 'easeOutQuad'), k(1156, P(CL), 'easeInQuad'), k(1200, P(CL))]
    return ks


PRINT = {
    'length': 1200, 'loop': 'hold',
    'bones': {'ring': {'position': ring_keys()}, 'carriage': {'position': carriage_keys()}},
    'effects': [
        (0, 'sound', 'headinajar:printer.start|0.6|1.0', None),
        (963, 'sound', 'headinajar:printer.tap|0.4|1.2', None),
        (968, 'sound', 'headinajar:printer.tap|0.4|1.2', None),
        (972, 'sound', 'headinajar:printer.zip|0.5|1.0', None),
        (981, 'sound', 'headinajar:printer.tap|0.4|1.1', None),
        (986, 'sound', 'headinajar:printer.tap|0.4|1.1', None),
        (1000, 'sound', 'headinajar:printer.ratchet|0.5|1.0', None),
        (1002, 'sound', 'headinajar:printer.ratchet|0.5|1.06', None),
        (1004, 'sound', 'headinajar:printer.ratchet|0.5|1.12', None),
        (1008, 'sound', 'headinajar:printer.develop|0.6|1.0', None),
        (DEV1, 'sound', 'headinajar:printer.thunk|0.5|0.9', None),
        (1154, 'sound', 'headinajar:printer.tap|0.3|0.9', None),
    ],
}

# ---- deliver (40, hold; door controller, seeked at print tick - 1160): the heavy door swings out ---------------------
DELIVER = {
    'length': 40, 'loop': 'hold',
    'bones': {'door': {'rotation': [k(0, R()), k(2, R()), k(5, R(y=-3), 'easeOutQuad'),
                                    k(15, R(y=104), 'easeInOutCubic'), k(19, R(y=100), 'easeInOutSine'),
                                    k(40, R(y=100))]}},
    'effects': [(2, 'sound', 'headinajar:printer.unlatch|0.5|1.0', None),
                (15, 'sound', 'headinajar:printer.door_stop|0.6|0.9', None),
                (24, 'sound', 'headinajar:printer.step|0.4|1.0', None),
                (30, 'sound', 'headinajar:printer.step|0.4|0.95', None),
                (35, 'sound', 'headinajar:printer.step|0.5|0.85', None)],
}
# ---- shut (20, hold): the door closes behind the body once the front is clear ---------------------------------------
SHUT = {'length': 20, 'loop': 'hold',
        'bones': {'door': {'rotation': [k(0, R(y=100)), k(3, R(y=103), 'easeOutQuad'), k(15, R(), 'easeInQuad'),
                                        k(17, R(y=2), 'easeOutQuad'), k(20, R(), 'easeInQuad')]}},
        'effects': [(0, 'sound', 'headinajar:printer.door_swing|0.4|1.0', None),
                    (15, 'sound', 'headinajar:printer.door_shut|0.6|1.0', None)]}
# ---- open / close (the screen, K5): the door cracks 22 degrees (v2: 12 moved the free edge 3 px) -------------------
OPEN = {'length': 6, 'loop': 'hold',
        'bones': {'door': {'rotation': [k(0, R()), k(1, R(y=-1.5), 'easeOutQuad'), k(4, R(y=24), 'easeOutCubic'),
                                        k(6, R(y=22), 'easeInOutSine')]}},
        'effects': [(0, 'sound', 'headinajar:machine.hatch_open|0.5|0.8', None)]}
CLOSE = {'length': 6, 'loop': 'hold',
         'bones': {'door': {'rotation': [k(0, R(y=22)), k(4, R(), 'easeInQuad'), k(5, R(y=1.2), 'easeOutQuad'),
                                         k(6, R(), 'easeInQuad')]}},
         'effects': [(4, 'sound', 'headinajar:machine.hatch_shut|0.5|0.8', None)]}
# ---- loaded / unloaded (10, hold; the materials bits all set / not): the head glides from its park at the bar's free
# end to the centre (5 px), ready; and back, bumping the end ------------------------------------------------------------
LOADED = {'length': 10, 'loop': 'hold',
          'bones': {'carriage': {'position': [k(0, P(CL)), k(1, P(CL - 0.4), 'easeOutQuad'),
                                              k(7, P(CC + 0.4), 'easeOutCubic'), k(10, P(CC), 'easeInOutSine')]}},
          'effects': [(7, 'sound', 'headinajar:printer.ready|0.4|1.0', None)]}
UNLOADED = {'length': 10, 'loop': 'hold',
            'bones': {'carriage': {'position': [k(0, P(CC)), k(1, P(CC + 0.4), 'easeOutQuad'),
                                                k(8, P(CL), 'easeInQuad'), k(9, P(CL + 0.4), 'easeOutQuad'),
                                                k(10, P(CL), 'easeInQuad')]}},
            'effects': [(8, 'sound', 'headinajar:printer.tap|0.3|0.9', None)]}
# ---- refuse (8, once; block event 1, no job running): the bar's free end kicks up 6 px on its post and slams back
# onto the ground, the head rocking two ticks behind; seen from the front (v2: the old bounce moved 1 px) ---------------
HEAD_ROCK = [k(0, R()), k(2, R()), k(4, R(z=10), 'easeInOutSine'), k(6, R(z=-10), 'easeInOutSine'),
             k(8, R(), 'easeInOutSine')]
REFUSE = {'length': 8, 'loop': 'once',
          'bones': {'ring': {'rotation': [k(0, R()), k(3, R(z=-24), 'easeOutCubic'), k(4, R(z=-24)),
                                          k(6, R(), 'easeInQuad'), k(7, R(z=-3), 'easeOutQuad'),
                                          k(8, R(), 'easeInQuad')]},
                    'carriage': {'rotation': HEAD_ROCK}},
          'effects': [(0, 'sound', 'headinajar:machine.rattle|0.4|1.0', None),
                      (6, 'sound', 'headinajar:printer.thunk|0.4|1.1', None)]}
# ---- refuse_busy (8, once; block event 1 while printing, `printer.busy`): the bar is up the door, so it only bobs ----
REFUSE_BUSY = {'length': 8, 'loop': 'once',
               'bones': {'ring': {'rotation': [k(0, R()), k(2, R(z=-4), 'easeOutQuad'), k(4, R(), 'easeInQuad'),
                                               k(6, R(z=-3), 'easeOutQuad'), k(8, R(), 'easeInQuad')]},
                         'carriage': {'rotation': HEAD_ROCK}},
               'effects': [(0, 'sound', 'headinajar:machine.rattle|0.4|1.0', None)]}
# ---- stall / unstall (the job running but no power: workRate 0 for 20 ticks): the bar's free end sags 4 px, an outline
# change that reads "out of power" at range; it lifts back when power returns. Only between print ticks 160 and 1120
# (lower, the free end would sag into the ground). ----------------------------------------------------------------------
STALL = {'length': 16, 'loop': 'hold',
         'bones': {'ring': {'rotation': [k(0, R()), k(16, R(z=15), 'easeInOutSine')]}},
         'effects': [(0, 'sound', 'headinajar:printer.wind_down|0.4|1.0', None)]}
UNSTALL = {'length': 10, 'loop': 'hold',
           'bones': {'ring': {'rotation': [k(0, R(z=15)), k(1, R(z=16), 'easeOutQuad'), k(8, R(z=-1), 'easeOutCubic'),
                                           k(10, R(), 'easeInOutSine')]}},
           'effects': [(1, 'sound', 'headinajar:printer.start|0.4|1.1', None)]}

CLIPS = [('print', PRINT), ('deliver', DELIVER), ('shut', SHUT), ('open', OPEN), ('close', CLOSE),
         ('loaded', LOADED), ('unloaded', UNLOADED), ('refuse', REFUSE), ('refuse_busy', REFUSE_BUSY),
         ('stall', STALL), ('unstall', UNSTALL)]


# ---------------------------------------------------------------------------------------------------------------------
# Evaluation of a key list at a tick (GeckoLib: easing of the arriving key) - for the planes and the review clips
# ---------------------------------------------------------------------------------------------------------------------
def at(keys, t):
    keys = sorted(keys, key=lambda x: x[0])
    if t <= keys[0][0]:
        return list(keys[0][1])
    for i in range(1, len(keys)):
        t1, v1, e, a, _ = keys[i]
        t0, v0 = keys[i - 1][0], keys[i - 1][1]
        if t <= t1:
            f = kit.ease(e, a, (t - t0) / float(t1 - t0))
            return [v0[j] + (v1[j] - v0[j]) * f for j in range(3)]
    return list(keys[-1][1])


RING = ring_keys()


def print_plane(t):
    """The print plane's height (model px) at print tick t: the head's top, 1 px above the bar's top."""
    return geo.FLOOR + at(RING, t)[1]


def body_state(t):
    """What the renderer draws at print tick t: (print_h or None, dev_h or None, collar). dev_h: skin above it."""
    if t < 1000:
        return print_plane(t), 99, False
    if t < DEV0:
        return None, 99, True
    if t < DEV1:
        return None, print_plane(t), True
    return None, None, True


import math  # noqa: E402

WALK0, WALK1 = 1178, 1200


def _clamp(x):
    return max(0.0, min(1.0, x))


def walk(t):
    """The body's step out (code, print ticks 1178-1200). Returns the feet centre (x, y, z) in model px, the yaw in the
    stand-in's outliner degrees (-45 in the booth, 0 facing out), how far the upper body is lowered by the crouch (the
    vanilla crouch pose, `model.crouching`: it ducks under the marquee, out of habit, for the head it hasn't got), and
    the limb swing. It glides at floor level over the bar and the head (their tops are at the floor's height), turns to
    face out once its arm is past the column, and steps down on 1200, where the server spawns the entity: the front
    cell's bottom centre, (0, 0, -16), facing out."""
    s = _clamp((t - WALK0) / float(WALK1 - WALK0))
    z = -16 * kit.ease('easeInOutSine', None, s)
    yaw = -45 * (1 - kit.ease('easeInOutSine', None, _clamp((s - 0.72) / 0.2)))
    crouch = min(kit.ease('easeInOutSine', None, _clamp((s - 0.28) / 0.1)),
                 1 - kit.ease('easeInOutSine', None, _clamp((s - 0.84) / 0.08)))
    y = geo.FLOOR - 3 * kit.ease('easeInQuad', None, _clamp((s - 0.88) / 0.12))
    swing = 4.0 * s, 0.8 * math.sin(math.pi * s)                                  # limbSwing, limbSwingAmount
    return (0.0, y, z), yaw, crouch, swing


# ---- review clips ----------------------------------------------------------------------------------------------------
def brownout():
    """Print ticks 40-120 played at half rate (a brown-out at workRate 2): 160 real ticks."""
    ck = carriage_keys()
    return {'length': 160, 'loop': 'hold', 'review': True, 'effects': [],
            'bones': {'ring': {'position': [k(t, at(RING, 40 + t / 2.0)) for t in range(0, 161, 2)]},
                      'carriage': {'position': [k(t, at(ck, 40 + t / 2.0)) for t in range(0, 161, 2)]}}}


def pose(name, ring_y=0, car_x=CR, door=0):
    return (name, {'length': 1, 'loop': 'hold', 'review': True, 'effects': [],
                   'bones': {'ring': {'position': [k(0, P(y=ring_y))]}, 'carriage': {'position': [k(0, P(car_x))]},
                             'door': {'rotation': [k(0, R(y=door))]}}})


def review_clips():
    return [('_review_brownout', brownout()),
            pose('_review_sweep_top_left', TOP + 0.5, CL - 0.4),          # the bar's highest, the head at its far end
            pose('_review_sweep_top_right', TOP + 0.5, CR + 0.4),
            pose('_review_door_104', 0, CC, 104),                       # the door's overshoot, bar parked
            pose('_review_door_open_ring_mid', 12, CC, 104),
            ('_review_stall_610', {'length': 1, 'loop': 'hold', 'review': True, 'effects': [],
                                   'bones': {'ring': {'position': [k(0, at(RING, 610))], 'rotation': [k(0, R(z=15))]},
                                             'carriage': {'position': [k(0, at(carriage_keys(), 610))]}}})]           # (impossible in game: the sweep check only)


# ---------------------------------------------------------------------------------------------------------------------
# Variants for the boards: a geometry build with a stand-in body, then the review copy's (baked) clips copied in
# ---------------------------------------------------------------------------------------------------------------------
def variant(name, **geo_kw):
    path = '%s/printer_%s.bbmodel' % (REVIEW_DIR, name)
    geo.build(path, **geo_kw)
    with open(REVIEW, encoding='utf-8') as fh:
        anims = json.load(fh)['animations']
    with open(path, encoding='utf-8') as fh:
        d = json.load(fh)
    d['animations'] = anims
    with open(path, 'w', encoding='utf-8') as fh:
        json.dump(d, fh, indent=1)
    return path


def beat(t, glass=True):
    ph, dh, collar = body_state(t)
    return variant('t%d%s' % (t, '' if glass else '_ng'), body={'print_h': ph, 'dev_h': dh, 'collar': collar},
                   door_glass=glass)


# ---------------------------------------------------------------------------------------------------------------------
# The sweep: every bone through its clips (and the body in the booth and on its walk) against everything else.
# Boxes are oriented boxes; a hit is a sample point of one box more than EPS inside another.
# ---------------------------------------------------------------------------------------------------------------------
EPS = 0.06


def rot(axis, deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    if axis == 'x':
        return [[1, 0, 0], [0, c, -s], [0, s, c]]
    if axis == 'y':
        return [[c, 0, s], [0, 1, 0], [-s, 0, c]]
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def mm(a, b):
    return [[sum(a[i][k_] * b[k_][j] for k_ in range(3)) for j in range(3)] for i in range(3)]


def mv(a, v):
    return [sum(a[i][j] * v[j] for j in range(3)) for i in range(3)]


class Frame:
    """A rigid transform p -> R (p - pivot) + pivot + pos, chained to a parent."""
    def __init__(self, pivot, rotdeg=(0, 0, 0), pos=(0, 0, 0), parent=None):
        Rm = mm(rot('z', rotdeg[2]), mm(rot('y', rotdeg[1]), rot('x', rotdeg[0])))
        self.f = lambda p: [a + b + c for a, b, c in zip(mv(Rm, [p[i] - pivot[i] for i in range(3)]), pivot, pos)]
        self.parent = parent

    def __call__(self, p):
        q = self.f(p)
        return self.parent(q) if self.parent else q


def obb(f, t, frame):
    """Oriented box from an axis-aligned box in a frame: (centre, axes, half sizes)."""
    c = [(f[i] + t[i]) / 2 for i in range(3)]
    h = [(t[i] - f[i]) / 2 for i in range(3)]
    o = frame(c)
    axes = []
    for i in range(3):
        e = [0, 0, 0]
        e[i] = 1
        p = frame([c[j] + e[j] for j in range(3)])
        axes.append([p[j] - o[j] for j in range(3)])
    return o, axes, h


def samples(box, step=0.5):
    o, axes, h = box
    out = []
    n = [max(1, int(math.ceil(2 * h[i] / step))) for i in range(3)]
    for a in range(n[0] + 1):
        for b in range(n[1] + 1):
            for c in range(n[2] + 1):
                u = [-h[0] + 2 * h[0] * a / n[0], -h[1] + 2 * h[1] * b / n[1], -h[2] + 2 * h[2] * c / n[2]]
                out.append([o[j] + sum(u[i] * axes[i][j] for i in range(3)) for j in range(3)])
    return out


def inside(p, box):
    o, axes, h = box
    d = [p[j] - o[j] for j in range(3)]
    return all(abs(sum(d[j] * axes[i][j] for j in range(3))) < h[i] - EPS for i in range(3))


def hits(group_a, group_b):
    out = set()
    for na, ba in group_a:
        pts = samples(ba)
        for nb, bb in group_b:
            if any(inside(p, bb) for p in pts):
                out.add('%s/%s' % (na, nb))
    return out


def posed(m, door=(0, 0, 0), ring_pos=(0, 0, 0), ring_rot=(0, 0, 0), car_pos=(0, 0, 0), car_rot=(0, 0, 0)):
    piv = {b[0]: b[1] for b in m.bones}
    frames = {'door': Frame(piv['door'], door), 'ring': Frame(piv['ring'], ring_rot, ring_pos)}
    frames['carriage'] = Frame(piv['carriage'], car_rot, car_pos, frames['ring'])
    out = {'static': [], 'door': [], 'ring': [], 'carriage': []}
    for name, f, t, mat, parent, kw in m.drawn():
        key = parent if parent in frames else 'static'
        out[key].append((name, obb(f, t, frames.get(key, Frame([0, 0, 0])))))
    return out


CROUCH = 2.0   # px the shoulders drop in the vanilla crouch (approximated as a straight drop for the sweep)


def body_obbs(state=(None, 99, True), at=(0, geo.FLOOR, 0), yaw=geo.BODY_YAW, crouch=0.0):
    ph, dh, collar = state
    fr = Frame(list(at), (0, yaw, 0))
    out = []
    for n, f, t, mat in geo.body_boxes(print_h=ph, dev_h=dh, collar=collar):
        dy = at[1] - geo.FLOOR - (CROUCH * crouch if 'leg' not in n else 0)
        d = [at[0], dy, at[2]]
        out.append((n, obb([f[i] + d[i] for i in range(3)], [t[i] + d[i] for i in range(3)], fr)))
    return out


def sweep():
    m = geo.model()
    ring_k, car_k = ring_keys(), carriage_keys()
    found = {}

    def check(tag, p, body=None):
        static = p['static']
        groups = [('door', p['door']), ('ring', p['ring']), ('carriage', p['carriage'])]
        res = set()
        for gname, g in groups:
            res |= hits(g, static)
        res |= hits(p['door'], p['ring'] + p['carriage'])
        if body:
            res |= hits(body, static + p['door'] + p['ring'] + p['carriage'])
        for r in res:
            found.setdefault(r, []).append(tag)

    full = body_obbs()
    for t in range(0, 1201, 1):                         # the print, the full body standing in the booth throughout
        ry, cx = at(ring_k, t), at(car_k, t)
        if t % 2 and t not in range(958, 1010):
            continue
        check('print %d' % t, posed(m, ring_pos=ry, car_pos=cx), full if t % 10 == 0 else None)
    for t in range(0, 41):                              # deliver: the door, and the walk from print tick 1178
        door = at(DELIVER['bones']['door']['rotation'], t)
        pt = DELIVER0 + t
        (x, y, z), yaw, crouch, _ = walk(pt)
        body = body_obbs(at=(x, y, z), yaw=yaw, crouch=crouch)
        check('deliver %d' % t, posed(m, door=door, car_pos=P(CL)), body)
    for name, clip in (('shut', SHUT), ('open', OPEN), ('close', CLOSE)):
        for t in range(clip['length'] + 1):
            check('%s %d' % (name, t), posed(m, door=at(clip['bones']['door']['rotation'], t), car_pos=P(CC)))
    for name, clip in (('loaded', LOADED), ('unloaded', UNLOADED)):
        for t in range(clip['length'] + 1):
            check('%s %d' % (name, t), posed(m, car_pos=at(clip['bones']['carriage']['position'], t)))
    for cx in (CL, CC):                                  # the refusal at rest, the head parked or loaded
        for t in range(9):
            check('refuse %d head %s' % (t, cx), posed(m, car_pos=P(cx), ring_rot=at(REFUSE['bones']['ring']['rotation'], t),
                                                          car_rot=at(REFUSE['bones']['carriage']['rotation'], t)))
    for pt in (0, 40, 500, 940, 960, 978, 1004, 1008, 1074, 1200):    # the busy refusal over print poses
        for t in range(9):
            check('refuse_busy %d at print %d' % (t, pt), posed(
                m, ring_pos=at(ring_k, pt), car_pos=at(car_k, pt),
                ring_rot=at(REFUSE_BUSY['bones']['ring']['rotation'], t),
                car_rot=at(REFUSE_BUSY['bones']['carriage']['rotation'], t)))
    for pt in range(160, 1121, 20):                      # the stall sag over print poses
        for t in (8, 16):
            check('stall %d at print %d' % (t, pt), posed(m, ring_pos=at(ring_k, pt), car_pos=at(car_k, pt),
                                                          ring_rot=at(STALL['bones']['ring']['rotation'], t)))
        check('unstall 8 at print %d' % pt, posed(m, ring_pos=at(ring_k, pt), car_pos=at(car_k, pt),
                                                  ring_rot=at(UNSTALL['bones']['ring']['rotation'], 8)))
    # the body after delivery, standing where the entity spawns, against the door held open and shutting
    for t in range(0, 21):
        check('shut %d with the body in front' % t,
              posed(m, door=at(SHUT['bones']['door']['rotation'], t)), body_obbs(at=(0, 0, -16), yaw=0))
    found.pop('sleeve/column', None)    # the sleeve rides round the column (intended)
    for k_, v in sorted(found.items()):
        print('HIT %-40s %d frames, e.g. %s' % (k_, len(v), ', '.join(v[:4])))
    print('sweep done: %d pairs hit' % len(found))


if __name__ == '__main__':
    if '--sweep' in sys.argv:
        sweep()
        sys.exit()
    m = geo.model()
    review = REVIEW if '--review' in sys.argv else None
    n = kit.write_clips(geo.PATH, CLIPS, code_channels=m.code, review=review,
                        review_clips=review_clips() if review else ())
    print('wrote %d clips' % n)
    print(kit.effects_table(CLIPS))
    for t in (0, 40, 42, 300, 480, 600, 920, 960, 1000, 1008, 1040, 1074, 1100, 1140, 1160):
        print('t %4d  plane %.2f  state %s' % (t, print_plane(t), body_state(t)))
    if review:
        variant('rest')
        variant('loaded', materials=True)
        variant('body', body={})
        variant('body_ng', body={}, door_glass=False)
        print('review copy and variants written')

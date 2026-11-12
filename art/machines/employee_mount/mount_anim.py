"""Employee Mount: every clip, keyed by tick, written into employee_mount.bbmodel; and the head's code table.

Source of truth for the Mount's animations; hand edits in Blockbench are overwritten by the next run.

    python art/machines/employee_mount/mount_anim.py [--review] [--sweep] [--table]

--review also writes build/machines-review/mount_review.bbmodel (eased segments baked per tick, plus the
`_review_*` clips that act out the head's code motion on the stand-in head) and the variants mount_empty (no head, no
emerald, lamp off), mount_unpaid (no emerald, lamp off) and mount_nitwit (lamp amber).
--sweep samples the receiver through every clip and the stand-in head through every code pose at quarter ticks and
reports any point inside a static box, and the head against the glass's inside.
--table prints the head's code table (for the Java renderer).

Two bones, `receiver` and `stamp` (keyed position and rotation; no code channel). Everything the head does is code (HEAD_* below):
those tables are ported to Java and applies them in the renderer's `contents` when it draws the head.

Signs (keyframes, Blockbench = GeckoLib; the sweep uses the same maths, p' = pivot + offset + Rz Ry Rx (p - pivot)):
rotZ +90 stands the lying receiver upright; rotX turns +y towards +z. For the head the tables speak plainly:
yaw +a = the face turns toward +X (the head's own right, the viewer's left), i.e. a turn about Y by -a degrees in the
model's right-handed frame; pitch +a = nods down (the face drops), a turn about X by -a; roll +a = the top tilts toward
+X, a turn about Z by -a; dy in px. The review clips key the stand-in with exactly those turns.
"""
import copy
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
KIT = os.path.join(HERE, '..', 'kit')
if not os.path.exists(os.path.join(KIT, 'kit.py')):
    KIT = 'build/machines-review/scratch-employee_mount/kit'
sys.path.insert(0, KIT)
sys.path.insert(0, HERE)
import kit  # noqa: E402
from kit import k, R  # noqa: E402
import mount_geo  # noqa: E402

REVIEW_DIR = 'build/machines-review'

# ---------------------------------------------------------------------------------------------------------------------
# The receiver (v2: a handset, 6 long). Rest: lying across the phone (x 2..8, y 9..11, z -6..-5), pivot at its centre
# (5, 10, -5.5). AT_EAR: stood upright beside the jar at the head's ear (about x 4.5..6.5, y 11.5..17.5, z -1..0), the
# earpiece up, its face rolled 20 degrees toward the head: centre moved (0.5, 4.5, 5) = 6.7 px, in the open, ivory
# against the dark felt, above the banker's lamp.
# ---------------------------------------------------------------------------------------------------------------------
AT_EAR_P = [1, 4.5, 5]
AT_EAR_R = [20, 0, 90]


def P(x=0, y=0, z=0):
    return [x, y, z]


# ---- complaint: 68 ticks, hold. Block event `complaint` (every 2,400 ticks while unpaid); played from the event on
# the wall clock (a late joiner misses it, M11 allows). 0-3 the hand takes it: pressed into the cradle a quarter pixel
# (2, easeOutQuad), held a tick; 3-8 lifted off and swung up and back to the ear, 0.5 px and 5 degrees past (8,
# easeOutCubic), settled on 11 (easeInOutSine). 16-28 three jabbers of 12 degrees as the line is said (the head
# talking into it), then held still to 58. 58-64 hung up: a 1 px lift off the ear (60, easeOutQuad, 10-20 % of the
# way back), held a tick, slammed into the cradle in 3 ticks (64, easeInQuad: the clack), a 0.5 px bounce with a
# 3-degree kick (65), settled (67, easeInQuad).
def complaint():
    ep, er = AT_EAR_P, AT_EAR_R
    up = [ep[0], ep[1] + 0.5, ep[2] + 0.25]
    pos = [k(0, P()), k(2, P(y=-0.25), 'easeOutQuad'), k(3, P(y=-0.25)), k(5, P(1, 3, 0.5), 'easeOutCubic'),
           k(8, up, 'easeOutCubic'), k(11, list(ep), 'easeInOutSine')]
    rot = [k(0, R()), k(3, R()), k(5, R(5, 0, 35), 'easeOutCubic'), k(8, R(20, 0, 95), 'easeOutCubic'),
           k(11, list(er), 'easeInOutSine')]
    for t, d in ((16, 12), (19, -6), (22, 12), (25, -6), (28, 0)):
        rot.append(k(t, [er[0], er[1], er[2] + d], 'easeInOutSine'))
    lift = [ep[0], ep[1] + 1, ep[2]]
    pos += [k(58, list(ep)), k(60, lift, 'easeOutQuad'), k(61, list(lift)), k(62, P(1.75, 4, 2.5), 'easeInQuad'), k(64, P(), 'easeInQuad'),
            k(65, P(y=0.5), 'easeOutQuad'), k(67, P(), 'easeInQuad')]
    rot += [k(58, list(er)), k(61, list(er)), k(62, R(12, 0, 60), 'easeInQuad'), k(64, R(), 'easeInQuad'), k(65, R(0, 0, -3), 'easeOutQuad'),
            k(67, R(), 'easeInQuad')]
    return {'length': 68, 'loop': 'hold', 'bones': {'receiver': {'position': pos, 'rotation': rot}},
            'effects': [(2, 'sound', 'headinajar:mount.receiver_lift|0.5|1.0', None),
                        (12, 'sound', 'headinajar:mount.hr_ringback|0.3|1.0', None),
                        (16, 'sound', 'headinajar:mount.hr_mumble|0.35|1.0', None),
                        (64, 'sound', 'headinajar:mount.receiver_clack|0.6|1.0', None)]}


# ---- stamp: the paid employee's work, played by code at the start of every profession tic while paid (never
# unpaid: on strike; never a nitwit). One thump, 14 ticks, hold: lifted 6 px with a 0.5 px overshoot and an 8-degree
# outward lean (5, easeOutCubic), settled (7), held 2 ticks, struck down in 2 ticks (11, easeInQuad: the thump on the
# contact), a 0.5 px bounce (12), down (14). stamp_3 (the smiths' three taps): strikes on 11, 21 and 31; 34 ticks.
STAMP_UP = 6
LEAN = 8                       # rotZ + tips the knob toward -X, away from the jar


def stamp_clip(n=1):
    pos, rot, eff = [k(0, P())], [k(0, R())], []
    for i in range(n):
        t0 = 10 * i
        pos += [k(t0 + 5, P(y=STAMP_UP + 0.5), 'easeOutCubic'), k(t0 + 7, P(y=STAMP_UP), 'easeInOutSine'),
                k(t0 + 9, P(y=STAMP_UP)), k(t0 + 11, P(), 'easeInQuad')]
        rot += [k(t0 + 5, R(z=LEAN), 'easeOutCubic'), k(t0 + 9, R(z=LEAN)), k(t0 + 11, R(), 'easeInQuad')]
        eff.append((t0 + 2, 'sound', 'headinajar:mount.stamp_lift|0.25|1.0', None))
        eff.append((t0 + 11, 'sound', 'headinajar:mount.stamp_thump|0.5|%s' % round(1.0 + 0.05 * i, 2), None))
    last = 10 * (n - 1) + 11
    pos += [k(last + 1, P(y=0.5), 'easeOutQuad'), k(last + 3, P(), 'easeInQuad')]
    return {'length': last + 3, 'loop': 'hold', 'bones': {'stamp': {'position': pos, 'rotation': rot}},
            'effects': eff}


# ---- payday: 30 ticks, hold (block event `payday`, param = the days that were missed before this pay). The receiver
# stays put; the moment is the coin's clink, the emerald dropping into the tray (code) and the head's wiggle (code),
# with happy_villager sparkles over the lid. payday_back (70): the same 40 ticks later, for a head that was facing
# the wall and must turn round first.
def payday(delay=0):
    return {'length': 30 + delay, 'loop': 'hold', 'bones': {'receiver': {'position': [k(0, P())]}},
            'effects': [(0, 'sound', 'headinajar:mount.clink|0.6|1.2', None),
                        (delay + 4, 'particle', 'minecraft:happy_villager', 'jar_top'),
                        (delay + 10, 'particle', 'minecraft:happy_villager', 'jar_top'),
                        (delay + 16, 'particle', 'minecraft:happy_villager', 'jar_top')]
            + ([(delay, 'sound', 'headinajar:mount.cheer|0.4|1.0', None)] if delay else [])}


# ---- refuse: 8 ticks (block event 1, `mount.needs_villager`): the receiver jangles in its cradle as if the phone
# rang once, seen from the front: hops 1.5 px with a 10-degree roll, down (easeInQuad), again smaller, settles.
REFUSE = {'length': 8, 'loop': 'once', 'bones': {'receiver': {
    'position': [k(0, P()), k(1, P(y=1.5), 'easeOutQuad'), k(2, P(), 'easeInQuad'), k(3, P(y=1.25), 'easeOutQuad'),
                 k(4, P(), 'easeInQuad'), k(5, P(y=0.75), 'easeOutQuad'), k(6, P(), 'easeInQuad')],
    'rotation': [k(0, R()), k(1, R(z=10), 'easeOutQuad'), k(2, R(), 'easeInQuad'), k(3, R(z=-10), 'easeOutQuad'),
                 k(4, R(), 'easeInQuad'), k(5, R(z=6), 'easeOutQuad'), k(6, R(), 'easeInQuad')]}},
    'effects': [(0, 'sound', 'headinajar:machine.rattle|0.4|1.0', None),
                (1, 'sound', 'headinajar:mount.bell_ting|0.4|1.0', None)]}

CLIPS = [('complaint', complaint()), ('stamp', stamp_clip(1)), ('stamp_3', stamp_clip(3)), ('payday', payday()),
         ('payday_back', payday(40)), ('refuse', REFUSE)]

# ---------------------------------------------------------------------------------------------------------------------
# The head: code. Keys are (tick, yaw, pitch, roll, dy, easing arriving at the key); between keys ease as named
# (GeckoLib's curves: kit.ease). A tic starts every `period` ticks while paid (twice that while unpaid), phase
# `floorMod(pos.asLong() * 31, period)` so a row of mounts doesn't tic together; the glance back every 600 (phase
# likewise, never during a tic). All offsets add to the base pose of the state (below).
# ---------------------------------------------------------------------------------------------------------------------
S = 'easeInOutSine'
HEAD_TICS = {
    # librarian: reads down a page: eyes along a line, back, the next line lower, then looks up
    'librarian': (140, [(0, 0, 0, 0, 0, None), (6, -8, 10, 0, 0, S), (14, 8, 10, 0, 0, S), (18, -8, 13, 0, 0, S),
                        (26, 8, 13, 0, 0, S), (34, 0, 0, 0, 0, S)]),
    # cleric: bows a little and sways side to side, as at prayers
    'cleric': (160, [(0, 0, 0, 0, 0, None), (10, 0, 8, 6, 0, S), (30, 0, 8, -6, 0, S), (50, 0, 8, 6, 0, S),
                     (60, 0, 0, 0, 0, S)]),
    # mason: a sharp duck, as from a falling brick (wind-up up, the duck easeInQuad, held, back up)
    'mason': (120, [(0, 0, 0, 0, 0, None), (2, 0, -4, 0, 0.25, 'easeOutQuad'), (5, 0, 15, 0, -0.25, 'easeInQuad'),
                    (9, 0, 15, 0, -0.25, None), (15, 0, 0, 0, 0, S)]),
    # farmer: looks up at the sky, a little to one side, and back down
    'farmer': (200, [(0, 0, 0, 0, 0, None), (3, 0, 3, 0, 0, 'easeOutQuad'), (12, 0, -12, 0, 0, S),
                     (32, 6, -12, 0, 0, S), (42, 0, 0, 0, 0, S)]),
    # fletcher: sights along an arrow: turns a little, squints (a quick dip of the brow), tracks, back
    'fletcher': (150, [(0, 0, 0, 0, 0, None), (6, 10, -4, 0, 0, S), (10, 10, -4, 0, 0, None),
                       (12, 10, 2, 0, 0, 'easeOutQuad'), (20, -6, 0, 0, 0, S), (28, 0, 0, 0, 0, S)]),
    # cartographer: turns slowly one way, then the other, surveying
    'cartographer': (200, [(0, 0, 0, 0, 0, None), (25, -40, 0, 0, 0, S), (35, -40, 0, 0, 0, None),
                           (70, 40, 0, 0, 0, S), (80, 40, 0, 0, 0, None), (100, 0, 0, 0, 0, S)]),
    # butcher: a quick double nod, chopping
    'butcher': (100, [(0, 0, 0, 0, 0, None), (2, 0, -3, 0, 0, 'easeOutQuad'), (4, 0, 14, 0, 0, 'easeInQuad'),
                      (6, 0, 2, 0, 0, 'easeOutQuad'), (8, 0, 14, 0, 0, 'easeInQuad'), (10, 0, 2, 0, 0, 'easeOutQuad'),
                      (14, 0, 0, 0, 0, S)]),
    # fisherman: sways as on a boat (roll and pitch out of step)
    'fisherman': (180, [(0, 0, 0, 0, 0, None), (15, 0, 3, 5, 0, S), (35, 0, -2, -5, 0, S), (55, 0, 3, 4, 0, S),
                        (70, 0, 0, 0, 0, S)]),
    # the smiths: three taps of a hammer (armorer straight on, weaponsmith and toolsmith a little to each side)
    'armorer': (110, [(0, 0, 0, 0, 0, None), (2, 0, -3, 0, 0, 'easeOutQuad'), (4, 0, 8, 0, 0, 'easeInQuad'),
                      (7, 0, 0, 0, 0, 'easeOutQuad'), (9, 0, 8, 0, 0, 'easeInQuad'), (12, 0, 0, 0, 0, 'easeOutQuad'),
                      (14, 0, 8, 0, 0, 'easeInQuad'), (18, 0, 0, 0, 0, S)]),
    # no bonus (leatherworker, shepherd, unemployed): looks about
    'other': (200, [(0, 0, 0, 0, 0, None), (15, 20, 0, 0, 0, S), (25, 20, 0, 0, 0, None), (45, -20, 0, 0, 0, S),
                    (55, -20, 0, 0, 0, None), (70, 0, 0, 0, 0, S)]),
}
HEAD_TICS['weaponsmith'] = (110, [(t, y + (10 if t else 0) * (0 < t < 18), p, r, d, e)
                                  for (t, y, p, r, d, e) in HEAD_TICS['armorer'][1]])
HEAD_TICS['toolsmith'] = (110, [(t, y - (10 if t else 0) * (0 < t < 18), p, r, d, e)
                                for (t, y, p, r, d, e) in HEAD_TICS['armorer'][1]])

# the glance back at the machine it staffs ("checking the dials"): every 600 ticks while paid, turning by its right
GLANCE = (600, [(0, 0, 0, 0, 0, None), (2, -6, 0, 0, 0, 'easeOutQuad'), (10, 150, 0, 0, 0, S),
                (30, 150, 0, 0, 0, None), (40, 0, 0, 0, 0, S)])

# base poses (yaw, pitch, roll, dy) by state; a state change eases between them over 20 ticks (easeInOutSine), except
# the turn to the wall, 40 ticks with a 3-tick wind-up, by the head's right
BASE = {
    'paid': (0, 0, 0, 0),
    'unpaid': (0, 10, 0, 0),              # 1-2 days missed: the head sags; tics half as often; no glance back
    'wall': (180, 6, 0, 0),               # 3+ days missed: facing the partition; no tics; a sigh every 400 ticks
    'nitwit': (30, 12, 0, 0),             # stares at the telephone; no tics, no glance
}
TURN_TO_WALL = [(0, 0, 10, 0, 0, None), (3, -8, 10, 0, 0, 'easeOutQuad'), (40, 180, 6, 0, 0, 'easeInOutCubic')]
SIGH = (400, [(0, 0, 0, 0, 0, None), (10, 0, 6, 0, -0.25, S), (16, 0, 6, 0, -0.25, None), (30, 0, 0, 0, 0, S)])


def complaint_head(bearing=35, elev=-4, wall=False):
    """The head during a complaint (code, from the event tick): turns to the nearest player within 12 blocks
    (bearing relative to the mount's facing, clamped to +-70; elev the pitch to the player's eyes, clamped +-10), shakes
    "no" slowly while the receiver is at its ear, turns back sulking as it is hung up. Facing the wall: it stays
    turned away and shakes at the wall. Keys as absolute (yaw, pitch, roll, dy) over the base pose's yaw 0."""
    b, e = (180, 6) if wall else (max(-70, min(70, bearing)), max(-10, min(10, elev)))
    if wall:
        keys = [(0, b, e, 0, 0, None), (16, b, e, 0, 0, None)]
    else:
        keys = [(0, 0, 0, 0, 0, None), (4, 0, 0, 0, 0, None), (14, b, e, 0, 0, S)]
    keys += [(20, b + 12, e, 0, 0, S), (28, b - 12, e, 0, 0, S), (36, b + 12, e, 0, 0, S), (44, b - 12, e, 0, 0, S),
             (48, b, e, 0, 0, S), (62, b, e, 0, 0, None)]
    if wall:
        keys += [(72, b, e + 8, 0, 0, S), (88, b, e, 0, 0, S)]
    else:
        keys += [(72, 0, 12, 0, -0.25, S), (80, 0, 12, 0, -0.25, None), (88, 0, 0, 0, 0, S)]
    return keys


WIGGLE = [(0, 0, 0, 0, 0, None), (4, 0, 0, 0, 0, None), (7, 5, -4, 0, 0.25, S), (10, -5, -4, 0, 0.25, S),
          (13, 5, -4, 0, 0.25, S), (16, -5, -4, 0, 0.25, S), (19, 5, -4, 0, 0.25, S), (22, 0, 0, 0, 0, S)]


# ---------------------------------------------------------------------------------------------------------------------
# Review clips: the head's code acted out on `standin_head` (rotation = (-pitch, -yaw, -roll), position dy).
# ---------------------------------------------------------------------------------------------------------------------
def head_keys(keys, base=(0, 0, 0, 0), t0=0):
    rot, pos = [], []
    for (t, y, p, r, d, e) in keys:
        rot.append(k(t + t0, R(-(p + base[1]), -(y + base[0]), -(r + base[2])), e))
        pos.append(k(t + t0, P(y=d + base[3]), e))
    return {'rotation': rot, 'position': pos}


def held(**extra):
    return {'receiver': {'position': [k(0, list(AT_EAR_P))], 'rotation': [k(0, list(AT_EAR_R))]}, **extra}


def review_clips():
    out = [('_review_held', {'length': 1, 'loop': 'hold', 'review': True, 'effects': [], 'bones': held(
        standin_head=head_keys([(0, 35, -4, 0, 0, None)]))})]
    c = complaint()
    out.append(('_review_complaint', {'length': 88, 'loop': 'hold', 'review': True, 'effects': [], 'bones': {
        'receiver': copy.deepcopy(c['bones']['receiver']), 'standin_head': head_keys(complaint_head())}}))
    out.append(('_review_complaint_wall', {'length': 88, 'loop': 'hold', 'review': True, 'effects': [], 'bones': {
        'receiver': copy.deepcopy(c['bones']['receiver']), 'standin_head': head_keys(complaint_head(wall=True))}}))
    for name, (period, keys) in sorted(HEAD_TICS.items()):
        out.append(('_review_tic_' + name, {'length': keys[-1][0], 'loop': 'hold', 'review': True, 'effects': [],
                                            'bones': {'standin_head': head_keys(keys)}}))
    out.append(('_review_glance', {'length': GLANCE[1][-1][0], 'loop': 'hold', 'review': True, 'effects': [],
                                   'bones': {'standin_head': head_keys(GLANCE[1])}}))
    out.append(('_review_payday', {'length': 22, 'loop': 'hold', 'review': True, 'effects': [],
                                   'bones': {'standin_head': head_keys(WIGGLE)}}))
    for st in ('unpaid', 'wall', 'nitwit'):
        out.append(('_review_' + st, {'length': 1, 'loop': 'hold', 'review': True, 'effects': [],
                                      'bones': {'standin_head': head_keys([(0, 0, 0, 0, 0, None)], BASE[st])}}))
    out.append(('_review_turn_to_wall', {'length': 40, 'loop': 'hold', 'review': True, 'effects': [],
                                         'bones': {'standin_head': head_keys(TURN_TO_WALL)}}))
    return out


# ---------------------------------------------------------------------------------------------------------------------
# Review variants
# ---------------------------------------------------------------------------------------------------------------------
LENS_COLOURS = {'off': (0x27, 0x26, 0x2d, 255), 'wait': (0xff, 0xc2, 0x4e, 255)}


def variant(src, dst, hide=(), lens=None):
    import base64
    import io
    from PIL import Image
    with open(src, encoding='utf-8') as fh:
        d = json.load(fh)
    for g in d['groups']:
        if g['name'] in hide:
            g['visibility'] = False
    if lens:
        tex = d['textures'][0]
        im = Image.open(io.BytesIO(base64.b64decode(tex['source'].split(',', 1)[1]))).convert('RGBA')
        px = im.load()
        for e in d['elements']:
            if e.get('name') == 'lamp_lens':
                for f in e['faces'].values():
                    u0, v0, u1, v1 = f['uv']
                    for yy in range(int(min(v0, v1)), int(max(v0, v1))):
                        for xx in range(int(min(u0, u1)), int(max(u0, u1))):
                            px[xx, yy] = LENS_COLOURS[lens]
        buf = io.BytesIO()
        im.save(buf, 'PNG')
        tex['source'] = 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()
    with open(dst, 'w', encoding='utf-8') as fh:
        json.dump(d, fh, indent=1)


# ---------------------------------------------------------------------------------------------------------------------
# Sweep
# ---------------------------------------------------------------------------------------------------------------------
def channel_at(keys, t):
    keys = sorted(keys, key=lambda x: x[0])
    if t <= keys[0][0]:
        return keys[0][1]
    for i in range(1, len(keys)):
        t0, v0 = keys[i - 1][0], keys[i - 1][1]
        t1, v1, e, a, _ = keys[i]
        if t <= t1:
            f = kit.ease(e, a, (t - t0) / float(t1 - t0))
            return [v0[j] + (v1[j] - v0[j]) * f for j in range(3)]
    return keys[-1][1]


def rot(v, r):
    x, y, z = v
    ax, ay, az = [math.radians(a) for a in r]
    y, z = y * math.cos(ax) - z * math.sin(ax), y * math.sin(ax) + z * math.cos(ax)
    x, z = x * math.cos(ay) + z * math.sin(ay), -x * math.sin(ay) + z * math.cos(ay)
    x, y = x * math.cos(az) - y * math.sin(az), x * math.sin(az) + y * math.cos(az)
    return [x, y, z]


def posed_points(c, pivot, r, off, step=0.5):
    f, t = c[1], c[2]
    pts = []
    n = [max(1, int(round((t[i] - f[i]) / step))) for i in range(3)]
    for i in range(n[0] + 1):
        for j in range(n[1] + 1):
            for kk in range(n[2] + 1):
                p = [f[0] + (t[0] - f[0]) * i / n[0], f[1] + (t[1] - f[1]) * j / n[1], f[2] + (t[2] - f[2]) * kk / n[2]]
                q = rot([p[a] - pivot[a] for a in range(3)], r)
                pts.append([pivot[a] + off[a] + q[a] for a in range(3)])
    return pts


def inside(p, f, t, eps=0.05):
    return all(f[a] + eps < p[a] < t[a] - eps for a in range(3))


# the quarter-pixel press into the cradle that winds up the lift (complaint ticks 0-3) is meant
PRESS = {'complaint', '_review_complaint', '_review_complaint_wall'}


def sweep(m):
    pivots = {b[0]: b[1] for b in m.bones}
    statics = [(c[0], c[1], c[2]) for c in m.drawn() if kit.is_static(m, c[4])]
    head = [(n, f, t, mat, 'standin_head', {}) for n, f, t, mat in mount_geo.head_boxes()]
    emerald = [(c[0], c[1], c[2]) for c in m.cubes if c[0] == 'si_pay']
    hits, lo, hi = {}, [1e9] * 3, [-1e9] * 3
    for name, clip in CLIPS + review_clips():
        for q in range(int(clip['length'] * 4) + 1):
            t = q / 4.0
            for bone in ['receiver', 'stamp', 'standin_head']:
                ch = clip['bones'].get(bone, {})
                r = channel_at(ch['rotation'], t) if 'rotation' in ch else [0, 0, 0]
                o = channel_at(ch['position'], t) if 'position' in ch else [0, 0, 0]
                cubes = head if bone == 'standin_head' else [c for c in m.drawn() if c[4] == bone]
                obstacles = statics + emerald
                for c in cubes:
                    for p in posed_points(c, pivots[bone], r, o, 0.5 if bone == 'receiver' else 0.25):
                        if bone in ('receiver', 'stamp'):
                            for a in range(3):
                                lo[a], hi[a] = min(lo[a], p[a]), max(hi[a], p[a])
                        else:
                            # the head must stay inside the glass (x, z within +-3, y 8..15)
                            if not (-3.0 <= p[0] <= 3.0 and 8 <= p[1] <= 15 and -3.0 <= p[2] <= 3.0):
                                worst = max(abs(p[0]) - 3, abs(p[2]) - 3, 8 - p[1], p[1] - 15)
                                key = (name, c[0], 'outside the glass')
                                hits.setdefault(key, []).append((t, round(worst, 2)))
                            continue
                        for on, f, tt in obstacles:
                            if inside(p, f, tt) and not (name in PRESS and c[0].startswith('receiver')
                                                         and on == 'phone' and p[1] >= 8.7):
                                hits.setdefault((name, c[0], on), []).append((t, 0))
    for (name, cube, on), ts in sorted(hits.items()):
        print('  HIT %s: %s in %s at ticks %s..%s (worst %.2f px)' % (
            name, cube, on, min(x[0] for x in ts), max(x[0] for x in ts), max(x[1] for x in ts)))
    print('sweep: %d hit pairs; the bones reach x %.2f..%.2f, y %.2f..%.2f, z %.2f..%.2f' % (
        len(hits), lo[0], hi[0], lo[1], hi[1], lo[2], hi[2]))
    return hits


def table():
    print('| Profession | Period (paid) | Keys: tick (yaw, pitch, roll, dy) easing |')
    print('|---|---|---|')
    for name, (period, keys) in HEAD_TICS.items():
        print('| %s | %d | %s |' % (name, period, '; '.join(
            '%d (%g, %g, %g, %g)%s' % (t, y, p, r, d, (' ' + e) if e else '') for t, y, p, r, d, e in keys)))
    print('| glance back | %d | %s |' % (GLANCE[0], '; '.join(
        '%d (%g, %g, %g, %g)%s' % (t, y, p, r, d, (' ' + e) if e else '') for t, y, p, r, d, e in GLANCE[1])))
    print('complaint head:', complaint_head())
    print('complaint head, wall:', complaint_head(wall=True))
    print('payday wiggle:', WIGGLE)
    print('turn to wall:', TURN_TO_WALL, 'sigh:', SIGH)
    print('base poses:', BASE)


if __name__ == '__main__':
    m = mount_geo.model()
    review = REVIEW_DIR + '/mount_review.bbmodel' if '--review' in sys.argv else None
    n = kit.write_clips(mount_geo.PATH, CLIPS, code_channels=m.code, review=review,
                        review_clips=review_clips() if review else ())
    print('wrote %d clips' % n)
    if review:
        variant(review, REVIEW_DIR + '/mount_empty.bbmodel', hide=['standin_head', 'standin_contents'], lens='off')
        variant(review, REVIEW_DIR + '/mount_unpaid.bbmodel', hide=['standin_contents'], lens='off')
        variant(review, REVIEW_DIR + '/mount_nitwit.bbmodel', hide=['standin_contents'], lens='wait')
        print('review copy and variants written')
    if '--sweep' in sys.argv:
        sweep(m)
    if '--table' in sys.argv:
        table()
    print(kit.effects_table(CLIPS))

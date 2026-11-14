"""Assembly Gantry: the arm's clips for all eight frames, written into gantry.bbmodel, and the plan the game's
turntable code reads (gantry_plan.json beside the model).

    python art/machines/gantry/gantry_anim.py                  # the clips, the locators and the plan
    python art/machines/gantry/gantry_anim.py --review DIR     # also review copies in DIR (turntable keyed, the frame
                                                               # stood on the pad as stand-in boxes)

Source of truth for the Gantry's animations; hand edits in Blockbench are overwritten by the next run.

What this script does, in order:
 1. reads each frame's own model (its `build` clip, read only): the seat tick of every slot (the timeline keys
    `build:seat:<slot>`) and each socket's position and outward direction at that tick;
 2. plans the arm for every slot (inverse kinematics in the arm's plane: swivel yaw, upper and forearm pitch, head
    pitch and wrist twist) so that the extended tool's tip meets the socket on its seat tick, with the turntable
    turning the frame so the socket faces the arm. Where no clean pose reaches a socket (the tall frames), the arm takes
    the nearest clean pose with the welder aimed straight at the socket and strikes an arc across the gap;
 3. sweeps every clip at half ticks: the arm's boxes against the frame's own boxes in their build pose at that tick,
    turned by the turntable, and against the pedestal; any part of the arm inside either is reported;
 4. writes the clips (the turntable is driven by code: never keyed in the game clips), the weld locators, and the plan
    (the turntable function per frame, the contacts, the arcs and every locator's position).

Clip ticks: a build clip runs from the Assemble press (tau); the frame is spawned LEAD ticks later, so t = tau - LEAD
is the frame's own build clip time (its `builtAt` clock). Everything is a function of tau, so a late joiner lands
right.
"""
import argparse
import copy
import json
import math
import os
import pickle
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
import kit  # noqa: E402
from kit import k, R  # noqa: E402
import gantry_geo as geo  # noqa: E402

MECHA = os.path.join(HERE, '..', '..', 'mecha')
PLAN_PATH = os.path.join(HERE, 'gantry_plan.json')
FRAME_NAMES = ('hopper', 'bulwark', 'colossus', 'executive', 'wheelhouse', 'skitter', 'kite', 'chimera')
LEAD = 10                      # ticks from the Assemble press to the frame's spawn (the arm stands back first)
EXT = -5.0                     # a tool's slide out (posZ) from inside the shade
TOOLS = ('tool_weld', 'tool_wrench', 'tool_grip', 'stamp')
WELD = {'tool_weld': EXT}
TWIST_MAX = 80.0               # the wrist twists up to 80 degrees either way
PRESENT = 9                    # ticks the turntable takes to present the frame to its owner
STATIC_LOCATORS = ('lamp', 'klaxon', 'deck', 'reel', 'cradle', 'swivel', 'tool_tip')
# the arm's extensions tried for a reach, (upper, forearm), the shortest first: the forearm telescopes before the upper
E_STEPS = ((0.0, 0.0), (0.0, 8.0), (0.0, 16.0), (0.0, 24.0), (0.0, 32.0), (0.0, geo.EMAX), (8.0, 32.0),
           (8.0, geo.EMAX), (geo.UMAX, 32.0), (geo.UMAX, geo.EMAX))
ESPEED = 10.0                  # px a telescope runs out or in in one tick

# ---------------------------------------------------------------------------------------------------------------------
# Forward kinematics of a .bbmodel (keyframe rotations add to the rest rotation on every axis; positions direct; Euler
# order R = Rz Ry Rx)
# ---------------------------------------------------------------------------------------------------------------------


def rmat(rx, ry_, rz):
    rx, ry_, rz = [math.radians(a) for a in (rx, ry_, rz)]
    X = np.array([[1, 0, 0], [0, math.cos(rx), -math.sin(rx)], [0, math.sin(rx), math.cos(rx)]])
    Y = np.array([[math.cos(ry_), 0, math.sin(ry_)], [0, 1, 0], [-math.sin(ry_), 0, math.cos(ry_)]])
    Z = np.array([[math.cos(rz), -math.sin(rz), 0], [math.sin(rz), math.cos(rz), 0], [0, 0, 1]])
    return Z @ Y @ X


def _sample(kfs, ch, t):
    ks = sorted([q for q in kfs if q['channel'] == ch], key=lambda q: q['time'])
    if not ks:
        return None

    def v(q):
        d = q['data_points'][0]
        return np.array([float(d['x']), float(d['y']), float(d['z'])])
    if t <= ks[0]['time']:
        return v(ks[0])
    for a, b in zip(ks, ks[1:]):
        if a['time'] <= t <= b['time']:
            f = 0.0 if b['time'] == a['time'] else (t - a['time']) / (b['time'] - a['time'])
            if b.get('interpolation') == 'step' or a.get('interpolation') == 'step':
                return v(a) if f < 1 else v(b)
            f = kit.ease(b.get('easing'), b.get('easingArgs'), f)
            return v(a) + (v(b) - v(a)) * f
    return v(ks[-1])


class FK:
    def __init__(self, path):
        with open(path, encoding='utf-8') as fh:
            self.d = json.load(fh)
        self.groups = {g['uuid']: g for g in self.d['groups']}
        self.byname = {g['name']: g['uuid'] for g in self.d['groups']}
        self.els = {e['uuid']: e for e in self.d['elements']}
        self.parent = {}

        def walk(nodes, par):
            for n in nodes:
                if isinstance(n, dict):
                    self.parent[n['uuid']] = par
                    walk(n['children'], n['uuid'])
                else:
                    self.parent[n] = par
        walk(self.d['outliner'], None)
        self.anims = {a['name']: a for a in self.d['animations']}

    def anc(self, u):
        out = []
        u = self.parent.get(u)
        while u:
            out.append(self.groups[u]['name'])
            u = self.parent.get(u)
        return out

    def mats(self, anim=None, tick=0.0, keys=None):
        """World (R, T) per group uuid. keys: {bone: {'rotation': [x,y,z], 'position': [x,y,z]}} (keyframe values)."""
        an = self.anims.get(anim) if anim else None
        t = tick / 20.0
        out = {}

        def get(u):
            if u in out:
                return out[u]
            g = self.groups[u]
            o = np.array(g['origin'], float)
            r = np.array(g.get('rotation', [0, 0, 0]), float)
            p = np.zeros(3)
            if an and u in an['animators']:
                kf = an['animators'][u]['keyframes']
                ar, ap = _sample(kf, 'rotation', t), _sample(kf, 'position', t)
                if ar is not None:
                    r = r + ar
                if ap is not None:
                    p = ap
            if keys and g['name'] in keys:
                r = r + np.array(keys[g['name']].get('rotation', [0, 0, 0]), float)
                p = p + np.array(keys[g['name']].get('position', [0, 0, 0]), float)
            Rl = rmat(*r)
            Tl = o + p - Rl @ o
            par = self.parent.get(u)
            if par:
                Rp, Tp = get(par)
                res = (Rp @ Rl, Rp @ Tl + Tp)
            else:
                res = (Rl, Tl)
            out[u] = res
            return res
        for u in self.groups:
            get(u)
        return out

    def boxes(self, M, skip=('standin',), only=None):
        """[(name, bone, from, to, Rw, Tw, Re, oe)]: world = Rw (Re (p - oe) + oe) + Tw for p in from..to."""
        out = []
        for u, e in self.els.items():
            if e.get('type', 'cube') != 'cube':
                continue
            anc = self.anc(u)
            if any(any(a.startswith(s) for s in skip) for a in anc):
                continue
            if only is not None and (not anc or anc[0] not in only):
                continue
            f, to = np.array(e['from'], float), np.array(e['to'], float)
            r = e.get('rotation') or [0, 0, 0]
            Re, oe = rmat(*r), np.array(e.get('origin', [0, 0, 0]), float)
            par = self.parent.get(u)
            Rw, Tw = M[par] if par else (np.eye(3), np.zeros(3))
            out.append((e['name'], anc[0] if anc else None, f, to, Rw, Tw, Re, oe))
        return out

    def group_point(self, M, name, local=None, d=None):
        u = self.byname[name]
        Rw, Tw = M[u]
        p = np.array(self.groups[u]['origin'] if local is None else local, float)
        out = Rw @ p + Tw
        if d is not None:
            return out, Rw @ np.array(d, float)
        return out


# ---------------------------------------------------------------------------------------------------------------------
# Collision: the arm's boxes as surface points, every other model's boxes as solids (point-in-box tests)
# ---------------------------------------------------------------------------------------------------------------------
def surface_points(f, t, step=0.5):
    """Points on a box's surface (every `step` px)."""
    xs = [np.arange(f[i], t[i] + 1e-6, step) for i in range(3)]
    pts = []
    for i in range(3):
        j, l = [a for a in range(3) if a != i]
        for v in (f[i], t[i]):
            A, B = np.meshgrid(xs[j], xs[l], indexing='ij')
            P = np.zeros((A.size, 3))
            P[:, i] = v
            P[:, j] = A.ravel()
            P[:, l] = B.ravel()
            pts.append(P)
    return np.vstack(pts)


class Solid:
    """A set of boxes ready for point-in-box tests: each box's inverse transform and its bounding sphere. A point is
    inside when it is more than `tol` px inside every face."""

    def __init__(self, boxes, tol=0.25):
        names, A, c, f_, t_, cen, rad = [], [], [], [], [], [], []
        for (name, bone, f, t, Rw, Tw, Re, oe) in boxes:
            if np.any(t - f <= 2 * tol):
                continue
            M = Re.T @ Rw.T
            names.append(name)
            A.append(M)
            c.append(oe - Re.T @ oe - M @ Tw)
            f_.append(f)
            t_.append(t)
            mid = (f + t) / 2.0
            cen.append(Rw @ (Re @ (mid - oe) + oe) + Tw)
            rad.append(np.linalg.norm(t - f) / 2.0)
        self.names = names
        self.n = len(names)
        if self.n:
            self.A, self.c = np.array(A), np.array(c)
            self.f, self.t = np.array(f_), np.array(t_)
            self.lo, self.hi = self.f + tol, self.t - tol
            self.cen, self.rad = np.array(cen), np.array(rad)

    def hits(self, parts, Rt=None, off=None):
        """parts: [(name, points, centre, radius)] in world space; the solid stands at world = Rt @ p + off.
        Returns [(part, box, depth)]."""
        res = []
        if not self.n:
            return res
        for name, P, cn, rd in parts:
            if Rt is not None:
                P = (P - off) @ Rt
                cn = (cn - off) @ Rt
            near = np.nonzero(np.linalg.norm(self.cen - cn, axis=1) < self.rad + rd)[0]
            for i in near:
                L = P @ self.A[i].T + self.c[i]
                inside = np.all((L > self.lo[i]) & (L < self.hi[i]), axis=1)
                if inside.any():
                    Q = L[inside]
                    depth = np.min(np.minimum(Q - self.f[i], self.t[i] - Q), axis=1).max()
                    res.append((name, self.names[i], round(float(depth), 2)))
        return res

    def touch(self, parts, Rt=None, off=None):
        """True as soon as any part has a point inside any box."""
        if not self.n:
            return False
        for name, P, cn, rd in parts:
            if Rt is not None:
                P = (P - off) @ Rt
                cn = (cn - off) @ Rt
            for i in np.nonzero(np.linalg.norm(self.cen - cn, axis=1) < self.rad + rd)[0]:
                L = P @ self.A[i].T + self.c[i]
                if np.all((L > self.lo[i]) & (L < self.hi[i]), axis=1).any():
                    return True
        return False

    def contains_any(self, P, Rt=None, off=None):
        if not self.n:
            return False
        if Rt is not None:
            P = (P - off) @ Rt
        lo, hi = P.min(0), P.max(0)
        cn, rd = (lo + hi) / 2.0, np.linalg.norm(hi - lo) / 2.0
        for i in np.nonzero(np.linalg.norm(self.cen - cn, axis=1) < self.rad + rd)[0]:
            L = P @ self.A[i].T + self.c[i]
            if np.all((L > self.lo[i]) & (L < self.hi[i]), axis=1).any():
                return True
        return False


# ---------------------------------------------------------------------------------------------------------------------
# The arm: absolute pose <-> keys, inverse kinematics
# ---------------------------------------------------------------------------------------------------------------------
REST = geo.rest_pose()
REST_ABS = {'yaw': REST['swivel'], 'a': REST['upper'], 'b': REST['upper'] + REST['fore'],
            'c': REST['upper'] + REST['fore'] + REST['head'], 'hy': 0.0, 'e': 0.0, 'u': 0.0}
AX = np.array([geo.AXIS[0], geo.HINGE_Y, geo.AXIS[1]])
CENTRE_DIR = np.array([-1.0, 0.0, -1.0]) / math.sqrt(2)     # from the arm's axis towards the pad's centre
DECKV = np.array([0.0, geo.DECK, 0.0])


def unwrap(a, ref):
    while a - ref > 180:
        a -= 360
    while a - ref < -180:
        a += 360
    return a


def to_keys(p, prev=None):
    """Absolute pose {'yaw','a','b','c','hy'} -> keyframe values per bone (rest + key = absolute; continuous with prev)."""
    kv = {'swivel': p['yaw'] - REST['swivel'], 'upper': p['a'] - REST['upper'],
          'fore': (p['b'] - p['a']) - REST['fore'], 'head': (p['c'] - p['b']) - REST['head']}
    for b in kv:
        kv[b] = unwrap(kv[b], prev[b] if prev else 0.0)
    return kv


def key_rot(kv, hy=0.0, e=0.0, u=0.0):
    t1, t2 = stages(e)
    return {'swivel': {'rotation': [0, kv['swivel'], 0]}, 'upper': {'rotation': [kv['upper'], 0, 0]},
            'fore': {'rotation': [kv['fore'], 0, 0]}, 'head': {'rotation': [kv['head'], hy, 0]},
            'upper_t': {'position': [0, max(0.0, min(geo.UMAX, u)), 0]},
            'fore_t1': {'position': [0, t1, 0]}, 'fore_t2': {'position': [0, t2, 0]}}


def stages(e):
    """The forearm's extension shared by its two telescoping stages (the bar up its rod, the head's rod out of the
    bar), in proportion to their strokes."""
    e = max(0.0, min(geo.EMAX, float(e)))
    return e * geo.EXT1 / geo.EMAX, e * geo.EXT2 / geo.EMAX


def in_dir(yaw):
    """The arm plane's IN direction for a swivel yaw (local -Z turned by yaw)."""
    s = math.radians(yaw)
    return np.array([-math.sin(s), 0.0, -math.cos(s)])


def solve(tip, d, prev_yaw=45.0, elbow_out=True, e=0.0, u=0.0):
    """Pose whose extended tool tip lands at `tip` pointing exactly along `d`: the head pivot is TOOL px back along d;
    the swivel turns the arm's plane through it; the shoulder and elbow reach it (planar IK, the forearm telescoped out
    by e and the upper arm by u); the head pitches (X) and twists about the forearm (Y) to aim the tool. Returns
    (pose, twist in degrees, reach shortfall)."""
    d = np.array(d, float) / np.linalg.norm(d)
    piv = np.array(tip, float) - geo.TOOL * d
    v = np.array([piv[0] - AX[0], 0, piv[2] - AX[2]])
    yaw = prev_yaw
    if np.linalg.norm(v) > 0.5:
        w = v / np.linalg.norm(v)
        if w @ CENTRE_DIR < 0:
            w = -w
        yaw = math.degrees(math.atan2(-w[0], -w[2]))
        # the head runs LAT px beside the arm's plane (local +X): turn the plane until the pivot sits that far off it
        for _ in range(8):
            s_, c_ = math.sin(math.radians(yaw)), math.cos(math.radians(yaw))
            lat = np.array([c_, 0, -s_])
            err = float(v @ lat) - geo.LAT
            dlat = np.array([-s_, 0, -c_]) * math.radians(1)
            der = float(v @ dlat)
            if abs(der) < 1e-9:
                break
            yaw -= err / der
    q = float(v @ in_dir(yaw))
    h = float(piv[1] - AX[1])
    a, b = geo.ik(q, h, elbow_out, e, u)
    # the tool's direction in the forearm's frame: w = Rx(-b) Ry(-yaw) d; face = Ry(hy) Rx(g) (0,0,-1)
    wv = rmat(-b, 0, 0) @ rmat(0, -yaw, 0) @ d
    g = math.degrees(math.asin(max(-1.0, min(1.0, float(wv[1])))))
    hy = math.degrees(math.atan2(-float(wv[0]), -float(wv[2])))
    if abs(hy) > 90:                        # the same aim with the head pitched over instead of twisted round
        hy = hy - 180 if hy > 0 else hy + 180
        g = 180 - g
    short = max(0.0, math.hypot(q, h) - (geo.LU + geo.LF + e + u))
    return {'yaw': yaw, 'a': a, 'b': b, 'c': b + g, 'hy': hy, 'e': float(e), 'u': float(u)}, abs(hy), short


def standoff_pose(b=8.0, a=90.0, yaw=45.0):
    """Stood off: the plane on the diagonal, the upper arm flat out over the cell behind (y 16.5-19.5, under the
    frame's feet at 20), the forearm `b` degrees out from upright, the head looking in and down. The default is the
    loaded pose (and every small frame's)."""
    return {'yaw': yaw, 'a': a, 'b': b, 'c': b - 33.0, 'hy': 0.0, 'e': 0.0, 'u': 0.0}


STANDOFF = standoff_pose()
LOADED = standoff_pose(56.0)       # the loaded pose: the forearm lowered 56 degrees out, clear of every frame parked
                                   # on the pad at any angle (the upright one meets the tall frames' arms and noses)


def blend(p, q, f):
    return {kk: p[kk] + (unwrap(q.get(kk, 0.0), p[kk]) - p[kk]) * f for kk in p}


G = FK(geo.PATH)


def _arm_elements():
    out = []
    for u, e in G.els.items():
        if e.get('type', 'cube') != 'cube':
            continue
        anc = G.anc(u)
        if not anc or anc[0] not in ('upper', 'upper_t', 'fore', 'fore_t1', 'fore_t2', 'head') + TOOLS:
            continue
        f, t = np.array(e['from'], float), np.array(e['to'], float)
        Re, oe = rmat(*(e.get('rotation') or [0, 0, 0])), np.array(e.get('origin', [0, 0, 0]), float)
        P = (Re @ (surface_points(f, t) - oe).T).T + oe
        out.append((e['name'], G.parent[u], P))
    return out


ARM_ELS = _arm_elements()
STATIC = Solid([b for b in G.boxes(G.mats()) if b[1] in ('static', 'turntable', 'state_lamp')])


def pose_keys(pose, tools=None):
    keys = key_rot(to_keys(pose), pose.get('hy', 0.0), pose.get('e', 0.0), pose.get('u', 0.0))
    for tl in TOOLS:
        keys[tl] = {'position': [0, 0, (tools or {}).get(tl, 0.0)]}
    return keys


def arm_parts(pose, tools=None):
    """The arm's boxes in a pose as [(name, surface points, centre, radius)] in Gantry model space."""
    M = G.mats(keys=pose_keys(pose, tools))
    out = []
    for name, par, P in ARM_ELS:
        Rw, Tw = M[par]
        W = P @ Rw.T + Tw
        lo, hi = W.min(0), W.max(0)
        out.append((name, W, (lo + hi) / 2.0, np.linalg.norm(hi - lo) / 2.0))
    return out


def tip_of(pose, tools=None):
    M = G.mats(keys=pose_keys(pose, tools))
    Rw, Tw = M[G.byname['head']]
    return Rw @ np.array([geo.AXIS[0] + geo.LAT, geo.HINGE_Y + geo.LU + geo.LF, geo.AXIS[1] - geo.TOOL]) + Tw


# ---------------------------------------------------------------------------------------------------------------------
# The frames: slots and seat ticks from each model's own build timeline, sockets at their seat ticks
# ---------------------------------------------------------------------------------------------------------------------
LEG_SOCKETS = ('socket_thigh_r', 'socket_shin_r', 'socket_thigh_l', 'socket_shin_l')


def ry(theta):
    return rmat(0, theta, 0)


def to_world(theta, p):
    return ry(theta) @ np.array(p, float) + DECKV


class Frame:
    def __init__(self, name):
        self.name = name
        self.fk = FK(os.path.join(MECHA, name, name + '.bbmodel'))
        clip = self.fk.anims['build']
        self.length = int(round(clip['length'] * 20))
        seats = []
        for kf in clip['animators'].get('effects', {}).get('keyframes', []):
            for dp in kf['data_points']:
                for line in (dp.get('script') or '').replace(';', '\n').split('\n'):
                    line = line.strip()
                    if line.startswith('build:seat:'):
                        seats.append((int(round(kf['time'] * 20)), line[len('build:seat:'):]))
        seats.sort()
        self.slots = []
        for S, slot in seats:
            socks = LEG_SOCKETS if slot == 'legs' else ('socket_' + slot,)
            socks = tuple(s for s in socks if s in self.fk.byname)
            if not socks:
                sys.exit('%s: no socket group for slot %s' % (name, slot))
            self.slots.append((slot, socks, S))
        self._solids = {}
        self._mats = {}

    def clamp(self, t):
        return max(0.0, min(float(self.length), t))

    def mats(self, t):
        key = round(self.clamp(t) * 2) / 2
        if key not in self._mats:
            self._mats[key] = self.fk.mats('build', key)
        return self._mats[key]

    def solid(self, t):
        key = round(self.clamp(t) * 2) / 2
        if key not in self._solids:
            self._solids[key] = Solid(self.fk.boxes(self.mats(key)))
        return self._solids[key]

    def socket(self, sock, t):
        p, n = self.fk.group_point(self.mats(t), sock, d=[0, 0, -1])
        return p, n / np.linalg.norm(n)

    def hits(self, parts, t, theta):
        return self.solid(t).hits(parts, ry(theta), DECKV)


def collide(fr, pose, t, theta, tools=WELD):
    parts = arm_parts(pose, tools)
    hs = STATIC.hits(parts)
    if fr is not None and t >= 0:
        hs += fr.hits(parts, t, theta)
    return hs


# ---------------------------------------------------------------------------------------------------------------------
# Planning one contact: a reach (the tool's tip on the socket) or an arc (aimed at it from the nearest clean pose)
# ---------------------------------------------------------------------------------------------------------------------
TILTS = (25.0, 40.0, 10.0, 55.0, 70.0, -15.0)
OFFS = (2.0, 3.5, 5.0)
BACK = 4.0                      # the approach pose stands this far back along the tool


def approach_dir(n, tilt=25.0, lean=0.0):
    """The tool's direction onto a socket with outward normal n. Side sockets: in and `tilt` degrees down (pressing the
    module's plate from above its working end), turned `lean` degrees round the upright from straight in (to come at
    a socket beside a part that stands in front of it). Up/down sockets: straight down/up, leaning `tilt` degrees
    towards the pad's centre (turned `lean` degrees round from it), so the head stays outside the frame's outline."""
    n = np.array(n, float)
    up = np.array([0, 1.0, 0])
    s = math.radians(tilt)
    if abs(n[1]) > 0.7:
        d = -np.sign(n[1]) * up * math.cos(s) + (ry(lean) @ CENTRE_DIR) * math.sin(s)
        return d / np.linalg.norm(d)
    h = np.array([n[0], 0, n[2]])
    h = ry(lean) @ (h / np.linalg.norm(h))
    d = -h * math.cos(s) - up * math.sin(s)
    return d / np.linalg.norm(d)


def pose_clean(fr, pose, app, t, theta, tools, tip=None, d=None):
    """The contact pose clear of the frame on its tick and the next, the approach pose two ticks before, and (given
    the tool's tip and line) a way out: the arm backed off further along the tool's line, clear too."""
    Rt = ry(theta)
    pc = arm_parts(pose, tools)
    if STATIC.touch(pc) or fr.solid(t).touch(pc, Rt, DECKV):
        return False
    if fr.solid(t + 1) is not fr.solid(t) and fr.solid(t + 1).touch(pc, Rt, DECKV):
        return False
    for f in (0.25, 0.6):                         # on the way in: the press home, eased in
        pm = arm_parts(blend(app, pose, f), tools)
        if STATIC.touch(pm) or fr.solid(t).touch(pm, Rt, DECKV):
            return False
    pa = arm_parts(app, tools)
    if STATIC.touch(pa) or fr.solid(t - 2).touch(pa, Rt, DECKV):
        return False
    if tip is not None:
        for r in (BACK + 4.0, BACK + 9.0):
            q, err, short = solve(tip - d * r, d, pose['yaw'], e=pose.get('e', 0.0), u=pose.get('u', 0.0))
            if short > 0.2 or not natural(q):
                break
            pq = arm_parts(q, tools)
            if STATIC.touch(pq) or fr.solid(t - 2).touch(pq, Rt, DECKV):
                return False
    return True


def path_ok(fr, so, app, tip, d, t, theta, tools):
    """A clean way in from the stood-off pose to the approach: straight in joint space, or onto the tool's line
    further back and in along it."""
    Rt, sol = ry(theta), fr.solid(t)

    def clean(poses):
        for q in poses:
            pq = arm_parts(q, tools)
            if STATIC.touch(pq) or sol.touch(pq, Rt, DECKV):
                return False
        return True
    if clean([blend(so, app, f) for f in (0.2, 0.4, 0.6, 0.8)]):
        return True
    for r in (8.0, 14.0):
        q, err, short = solve(tip - d * (BACK + r), d, app['yaw'], e=app.get('e', 0.0), u=app.get('u', 0.0))
        if short > 0.2 or not natural(q):
            continue
        line = [solve(tip - d * (BACK + r * f), d, app['yaw'], e=app.get('e', 0.0), u=app.get('u', 0.0))[0]
                for f in (0.33, 0.66)]
        if clean([blend(so, q, f) for f in (0.25, 0.5, 0.75)] + [q] + line):
            return True
    return False


def natural(pose):
    """The upper arm between 30 degrees in and 100 degrees out (its elbow above the pedestal's top, never folded back
    over the turntable)."""
    return -30.0 <= pose['a'] <= 100.0


def plan_point(fr, p, n, t, theta, tools=WELD, prev_yaw=45.0, label=None, so=None):
    """The cleanest reach onto frame point p (outward normal n) at frame time t and turntable angle theta: tool tilts,
    offsets off the point and both elbows; the least twisted head wins. None if nothing reaches cleanly."""
    pw, nw = to_world(theta, p), ry(theta) @ np.array(n, float)
    leans = (0.0, 45.0, -45.0, 90.0, -90.0) if abs(nw[1]) > 0.7 else (0.0, 30.0, -30.0, 55.0, -55.0, 80.0, -80.0)
    # first with a way out straight back along the tool; then, at full stretch (a high socket met side on under the
    # top of a tall frame), leaving the way it came in, to the approach pose
    for way_out, (u, e) in [(True, ue) for ue in E_STEPS] + [(False, ue) for ue in E_STEPS if ue[1] >= 32.0]:
        if e + u > 0 and np.linalg.norm(pw - AX) - geo.TOOL > geo.LU + geo.LF + e + u + 4.0:
            continue
        clean = []
        for tilt, lean in [(tl, ln) for ln in leans for tl in TILTS]:
            d = approach_dir(nw, tilt, lean)
            for off in OFFS:
                tip = pw - d * off
                pose, err, short = solve(tip, d, prev_yaw, True, e, u)
                if short > 0.2 or err > TWIST_MAX or not natural(pose):
                    continue
                app = solve(tip - d * BACK, d, pose['yaw'], True, e, u)[0]
                if not pose_clean(fr, pose, app, t, theta, tools, tip if way_out else None, d):
                    continue
                if so is not None and not path_ok(fr, so, app, tip, d, t, theta, tools):
                    continue
                clean.append({'tip': tip, 'dir': d, 'pose': pose, 'approach': app, 'err': err, 'tilt': tilt,
                              'off': off, 'reach': True, 'theta': theta, 'point': pw, 'label': label,
                              'way_out': way_out})
            if any(q['err'] < 20 for q in clean):
                break
        if clean:
            clean.sort(key=lambda q: (q['err'] > 45, q['off'], q['err']))
            best = dict(clean[0])
            best['alts'] = clean[1:8]           # other clean reaches, for a quicker way from where the arm is
            return best
    return None


def _arc_dirs():
    out = []
    for el in (-75, -60, -45, -30, -15, 0, 15, 30, 45, 60, 75):
        for az in range(0, 360, 15):
            e, a = math.radians(el), math.radians(az)
            out.append(np.array([math.cos(e) * math.cos(a), math.sin(e), math.cos(e) * math.sin(a)]))
    return np.array(out)


ARC_DIRS = _arc_dirs()
ARC_DISTS = np.arange(6.0, 58.0, 2.0)


def plan_arc(fr, p, n, t, theta, tools=WELD, prev_yaw=45.0, label=None, so=None, los=True):
    """The nearest clean pose with the welder aimed straight at frame point p, and a clear line from the tool's tip to
    the point (the arc). Tried first from in front of the socket's face, then from wider round."""
    for u, e in ((0.0, 0.0), (0.0, geo.EMAX / 2.0), (0.0, geo.EMAX), (geo.UMAX, geo.EMAX)):
        pl = _plan_arc(fr, p, n, t, theta, tools, prev_yaw, label, so, los, e, u)
        if pl is not None:
            return pl
    return None


def _plan_arc(fr, p, n, t, theta, tools, prev_yaw, label, so, los, e, u):
    pw, nw = to_world(theta, p), ry(theta) @ np.array(n, float)
    solid = fr.solid(t)
    Rt = ry(theta)
    for face_min in (0.2, -0.6):
        U = ARC_DIRS[ARC_DIRS @ nw >= face_min]
        if not len(U):
            continue
        # the head pivot (TOOL px behind the tip) must be within the arm's reach of the shoulder
        tips = pw[None, None, :] + U[:, None, :] * ARC_DISTS[None, :, None]
        piv = tips + U[:, None, :] * geo.TOOL
        dist = np.linalg.norm(piv - AX[None, None, :], axis=2)
        ok = (dist < geo.LU + geo.LF + e + u + 0.1) & (dist > abs(geo.LU + u - geo.LF - e) + 1) & \
            (tips[:, :, 1] > 15)
        order = sorted(zip(*np.nonzero(ok)), key=lambda ij: ARC_DISTS[ij[1]])
        tried = 0
        for i, j in order:
            w, dd = U[i], ARC_DISTS[j]
            tip, d = pw + w * dd, -w
            pose, err, short = solve(tip, d, prev_yaw, True, e, u)
            if short > 0.2 or err > TWIST_MAX or not natural(pose):
                continue
            tried += 1
            if tried > 200:
                break
            # the line of the arc, short of its last 1.5 px (the socket sits in the frame's surface)
            seg = tip[None, :] + d[None, :] * np.arange(0.0, max(0.0, dd - 1.5), 0.5)[:, None]
            if los and len(seg) and solid.contains_any(seg, Rt, DECKV):
                continue
            app = solve(tip - d * 3.0, d, pose['yaw'], True, e, u)[0]
            if not pose_clean(fr, pose, app, t, theta, tools, tip, d):
                continue
            if so is not None and not path_ok(fr, so, app, tip, d, t, theta, tools):
                continue
            return {'tip': tip, 'dir': d, 'pose': pose, 'approach': app, 'err': err, 'tilt': None, 'off': float(dd),
                    'reach': False, 'theta': theta, 'point': pw, 'label': label}
    return None


def plan_aim(fr, p, t, theta, so, tools=WELD, label=None):
    """The last resort for a socket the arm can neither reach nor aim at from close by: the head near its stood-off
    place, turned to aim the welder straight at the socket, the arc crossing whatever lies between."""
    pw = to_world(theta, p)
    base = tip_of(so)
    grid = np.arange(-12.0, 12.1, 3.0)
    offs = [np.array([dx, dy, dz]) for dx in grid for dy in np.arange(-9.0, 15.1, 3.0) for dz in grid]
    for off in sorted(offs, key=lambda o: np.linalg.norm(base + o - pw)):
        tip = base + off
        d = pw - tip
        dd = float(np.linalg.norm(d))
        d = d / dd
        pose, err, short = solve(tip, d, so['yaw'])
        if short > 0.2 or err > TWIST_MAX or not natural(pose):
            continue
        app = solve(tip - d * 3.0, d, pose['yaw'])[0]
        if not pose_clean(fr, pose, app, t, theta, tools):
            continue
        return {'tip': tip, 'dir': d, 'pose': pose, 'approach': app, 'err': err, 'tilt': None, 'off': dd,
                'reach': False, 'theta': theta, 'point': pw, 'label': label}
    return None


def plan_slot(fr, slot, socks, t, theta, tools=WELD, arcs=True, so=None, los=True):
    """A reach onto the slot's socket (any of the leg sockets for the legs) or, failing that, an arc; with `so`, only
    plans the arm can come into cleanly from that stood-off pose."""
    for sk in socks:
        p, n = fr.socket(sk, t)
        pl = plan_point(fr, p, n, t, theta, tools, label=sk, so=so)
        if pl:
            for q in [pl] + pl['alts']:
                q['sock'] = sk
            return pl
    if not arcs:
        return None
    best = None
    for sk in socks:
        p, n = fr.socket(sk, t)
        pl = plan_arc(fr, p, n, t, theta, tools, label=sk, so=so, los=los)
        if pl and (best is None or pl['off'] < best['off']):
            pl['sock'] = sk
            best = pl
    return best


def plan_score(pl):
    if pl is None:
        return None
    extra = (6.0 if pl.get('no_way_in') else 0.0) + (4.0 if pl.get('hidden') else 0.0)
    if pl['reach']:
        return extra + pl['err'] / 40.0 + (pl['pose'].get('e', 0.0) + pl['pose'].get('u', 0.0)) / 40.0
    return extra + 40.0 + 0.25 * pl['off']


# ---------------------------------------------------------------------------------------------------------------------
# Timeline: keys per channel built from a list of (tau, pose, easing) and tool states
# ---------------------------------------------------------------------------------------------------------------------
class Track:
    def __init__(self):
        self.poses = []          # (tau, pose, easing)
        self.tools = {tl: [(0, 0.0, None)] for tl in TOOLS}
        self.effects = []
        self.turn = []           # (t, theta, easing): the turntable function (t = frame clock)
        self.clock = LEAD        # tau - t

    def at(self, tau, pose, ease='easeInOutSine'):
        if self.poses and tau <= self.poses[-1][0]:
            raise ValueError('pose key at %s not after %s' % (tau, self.poses[-1][0]))
        self.poses.append((tau, dict(pose), ease))

    def tool(self, tl, tau, val, ease='easeOutCubic'):
        self.tools[tl].append((tau, val, ease))

    def fx(self, tau, kind, what, loc=None):
        """An effect key; one per tick in a clip, so a clash moves it a tick later. Returns the tick used."""
        if kind == 'sound':
            loc = None          # GeckoLib 4.9.3 reads only the effect for sounds; the handler plays them at the block
        taken = {e[0] for e in self.effects}
        while tau in taken:
            tau += 1
        self.effects.append((tau, kind, what, loc))
        return tau

    def pose_at(self, tau):
        ps = self.poses
        if tau <= ps[0][0]:
            return ps[0][1]
        for (t0, p0, _), (t1, p1, e) in zip(ps, ps[1:]):
            if t0 <= tau <= t1:
                f = kit.ease(e, None, (tau - t0) / float(t1 - t0))
                return blend(p0, p1, f)
        return ps[-1][1]

    def tools_at(self, tau):
        out = {}
        for tl, ks in self.tools.items():
            v = ks[0][1]
            for (t0, v0, _), (t1, v1, e) in zip(ks, ks[1:]):
                if t0 <= tau <= t1:
                    v = v0 + (v1 - v0) * kit.ease(e, None, (tau - t0) / float(t1 - t0))
                    break
                if tau > t1:
                    v = v1
            out[tl] = v
        return out

    def theta_at(self, t):
        return turn_angle(self.turn, t)

    def clip(self, length, loop='hold', review=False):
        bones = {}
        prev = None
        rot = {b: [] for b in ('swivel', 'upper', 'fore', 'head')}
        for tau, p, e in self.poses:
            kv = to_keys(p, prev)
            prev = kv
            rot['swivel'].append(k(tau, R(y=round(kv['swivel'], 3)), e))
            rot['upper'].append(k(tau, R(x=round(kv['upper'], 3)), e))
            rot['fore'].append(k(tau, R(x=round(kv['fore'], 3)), e))
            rot['head'].append(k(tau, R(x=round(kv['head'], 3), y=round(p.get('hy', 0.0), 3)), e))
        for b, ks in rot.items():
            bones[b] = {'rotation': ks}
        if any(p.get('e', 0.0) > 1e-6 for _, p, _ in self.poses):
            for i, b in enumerate(('fore_t1', 'fore_t2')):
                bones[b] = {'position': [k(tau, R(y=round(stages(p.get('e', 0.0))[i], 3)), e)
                                         for tau, p, e in self.poses]}
        if any(p.get('u', 0.0) > 1e-6 for _, p, _ in self.poses):
            bones['upper_t'] = {'position': [k(tau, R(y=round(p.get('u', 0.0), 3)), e) for tau, p, e in self.poses]}
        for tl, ks in self.tools.items():
            if len(ks) > 1 or ks[0][1] != 0:
                bones[tl] = {'position': [k(t, R(z=v), e) for (t, v, e) in ks]}
        out = {'length': length, 'loop': loop, 'bones': bones, 'effects': sorted(self.effects)}
        if review:
            out['review'] = True
            if self.turn:
                bones['turntable'] = {'rotation': [k(tau, R(y=round(self.theta_at(tau - self.clock), 3)))
                                                   for tau in range(0, length + 1)]}
        return out


def turn_angle(keys, t):
    """The turntable function: piecewise easeInOutSine (or the easing named) between (t, theta) keys; held outside."""
    if not keys:
        return 0.0
    if t <= keys[0][0]:
        return keys[0][1]
    for (t0, a0, _), (t1, a1, e) in zip(keys, keys[1:]):
        if t0 <= t <= t1:
            if t1 == t0:
                return a1
            return a0 + (a1 - a0) * kit.ease(e, None, (t - t0) / float(t1 - t0))
    return keys[-1][1]


def turn_ticks(d_theta):
    """Ticks the turntable takes for a turn: 2 plus one per 30 degrees, at least 3."""
    return max(3, int(round(2 + abs(d_theta) / 30.0)))


SND = lambda s, v=0.5, p=1.0: 'headinajar:gantry.%s|%s|%s' % (s, v, p)   # noqa: E731


def sweep(fr, tr, tau0, tau1, step=0.5):
    """Collision sweep of the arm over [tau0, tau1]. Returns [(tau, hits)]."""
    bad = []
    tau = tau0
    while tau <= tau1 + 1e-9:
        t = tau - tr.clock
        parts = arm_parts(tr.pose_at(tau), tr.tools_at(tau))
        hs = STATIC.hits(parts)
        if fr is not None and t >= 0:
            hs += fr.hits(parts, t, tr.theta_at(t))
        if hs:
            bad.append((tau, hs))
        tau += step
    return bad


SPEED = 30.0                     # degrees a joint may turn in one tick
SPEED_FAST = 60.0                # ... and in a hurry (a heavy frame's parts landing five ticks apart, side to side)
ESPEED_FAST = 14.0               # px a telescope runs in one tick in a hurry


def travel_ticks(p, q, fast=False):
    """Ticks the arm needs from pose p to pose q at its joint speeds (or its hurried ones)."""
    ang = max(abs(unwrap(q[kk], p[kk]) - p[kk]) for kk in ('yaw', 'a', 'b', 'c', 'hy'))
    lin = max(abs(q.get('e', 0.0) - p.get('e', 0.0)), abs(q.get('u', 0.0) - p.get('u', 0.0)))
    return int(math.ceil(max(ang / (SPEED_FAST if fast else SPEED), lin / (ESPEED_FAST if fast else ESPEED)) - 1e-9))


def joint_travel(p, q):
    return max([abs(unwrap(q[kk], p[kk]) - p[kk]) for kk in ('yaw', 'a', 'b', 'c', 'hy')] +
               [abs(q.get('e', 0.0) - p.get('e', 0.0)) * SPEED / ESPEED,
                abs(q.get('u', 0.0) - p.get('u', 0.0)) * SPEED / ESPEED])


def tool_dir(pose):
    M = G.mats(keys=pose_keys(pose))
    d = M[G.byname['head']][0] @ np.array([0, 0, -1.0])
    return d / np.linalg.norm(d)


LIFTS = [(0.0, 0.0), (4.0, 0.0), (0.0, 4.0), (8.0, 0.0), (0.0, 8.0), (6.0, 6.0), (0.0, -4.0), (12.0, 4.0)]


def cart_path(tr, t0, p0, t1, p1, lift, ease):
    """Keys a path from p0 (at t0) to p1 (at t1): the tool's tip on a line between the two tips, bowed `lift` =
    (out from the pad's centre, up) px at its middle, the tool's direction turning evenly; one key a tick, the ticks
    spaced along the line by `ease`. False if a point on the way is out of reach."""
    a, b = tip_of(p0), tip_of(p1)
    da, db = tool_dir(p0), tool_dir(p1)
    mid = (a + b) / 2.0
    out = np.array([mid[0], 0.0, mid[2]])
    out = out / max(1e-6, np.linalg.norm(out))
    keys = []
    prev = p0
    for tt in range(t0 + 1, t1):
        f = kit.ease(ease, None, (tt - t0) / float(t1 - t0))
        bow = 4 * f * (1 - f)
        tip = a + (b - a) * f + bow * (lift[0] * out + np.array([0, lift[1], 0]))
        d = da + (db - da) * f
        if np.linalg.norm(d) < 1e-3:
            return False
        pose, err, short = solve(tip, d, prev['yaw'], e=p0.get('e', 0.0) + (p1.get('e', 0.0) - p0.get('e', 0.0)) * f,
                                 u=p0.get('u', 0.0) + (p1.get('u', 0.0) - p0.get('u', 0.0)) * f)
        if short > 0.2 or err > TWIST_MAX + 10:
            return False
        if any(abs(unwrap(pose[kk], prev[kk]) - prev[kk]) > 40 for kk in ('yaw', 'a', 'b', 'c', 'hy')):
            return False                 # the arm would flip over between two ticks
        keys.append((tt, pose))
        prev = pose
    if any(abs(unwrap(p1[kk], prev[kk]) - prev[kk]) > 40 for kk in ('yaw', 'a', 'b', 'c', 'hy')):
        return False
    for tt, pose in keys:
        tr.at(tt, pose, None)
    tr.at(t1, p1, None)
    return True


def move(fr, tr, t0, p0, t1, p1, so, ease='easeInOutSine', cart=None, fast=False):
    """Key p1 at t1 (the arm being at p0 at t0) by the first path whose sweep is clean: straight in joint space; the
    tool's tip along a line (straight or bowed); through a via pose (pulled back out along the arm, or carried out from
    the pad along the tool's line `cart` = (tip, dir)); through two vias out by the stood-off pose; out to the stood-off
    pose and in along a line. If none is clean, the least bad is kept. Returns (t1 used, clean)."""
    n0 = len(tr.poses)
    t1 = max(t1, t0 + travel_ticks(p0, p1, fast))

    def direct(t_end):
        tr.at(t_end, p1, ease)
        return True

    def line(lift, t_end):
        return cart_path(tr, t0, p0, t_end, p1, lift, ease)

    def via(mid, t_end):
        tr.at(t0 + (t_end - t0) // 2, mid, 'easeInOutSine')
        tr.at(t_end, p1, ease)
        return True

    def two(f, t_end):
        if t_end - t0 < 5:
            return False
        tr.at(t0 + 2, blend(p0, so, f), 'easeInOutSine')
        tr.at(t_end - 2, blend(p1, so, f), 'easeInOutSine')
        tr.at(t_end, p1, ease)
        return True

    def retreat(r, lift, t_end):
        """Back out along the tool, across, and in along the next tool's line."""
        tip0, d0, tip1, d1 = tip_of(p0), tool_dir(p0), tip_of(p1), tool_dir(p1)
        r0, e0, s0 = solve(tip0 - d0 * r, d0, p0['yaw'], e=p0.get('e', 0.0), u=p0.get('u', 0.0))
        r1, e1, s1 = solve(tip1 - d1 * r, d1, p1['yaw'], e=p1.get('e', 0.0), u=p1.get('u', 0.0))
        if s0 > 0.2 or s1 > 0.2 or not natural(r0) or not natural(r1):
            return False
        ta = t0 + max(1, (t_end - t0) // 4)
        tb = t_end - max(1, (t_end - t0) // 4)
        if tb <= ta:
            return False
        if not cart_path(tr, t0, p0, ta, r0, (0.0, 0.0), 'easeOutQuad'):
            tr.at(ta, r0, 'easeOutQuad')
        if not cart_path(tr, ta, r0, tb, r1, lift, 'easeInOutSine'):
            tr.at(tb, r1, 'easeInOutSine')
        if not cart_path(tr, tb, r1, t_end, p1, (0.0, 0.0), ease):
            tr.at(t_end, p1, ease)
        return True

    def line_in(r, t_end):
        """Onto the next tool's line r px further back, then in along it."""
        tip1, d1 = tip_of(p1), tool_dir(p1)
        q, e, sh = solve(tip1 - d1 * r, d1, p1['yaw'], e=p1.get('e', 0.0), u=p1.get('u', 0.0))
        if sh > 0.2 or not natural(q):
            return False
        tq = t_end - max(1, (t_end - t0) // 3)
        if tq <= t0:
            return False
        tr.at(tq, q, 'easeInOutSine')
        if not cart_path(tr, tq, q, t_end, p1, (0.0, 0.0), ease):
            tr.at(t_end, p1, ease)
        return True

    def out_in(lift, t_end):
        t_mid = t0 + max(2, (t_end - t0) // 3)
        if not cart_path(tr, t0, p0, t_mid, so, (0.0, 0.0), 'easeInOutSine'):
            tr.at(t_mid, so, 'easeInOutSine')
        return cart_path(tr, t_mid, so, t_end, p1, lift, ease)

    t4, t6 = (max(t1, t0 + 3), max(t1, t0 + 4)) if fast else (max(t1, t0 + 4), max(t1, t0 + 6))
    attempts = [(direct, (t1,))] + [(line, (lift, t1)) for lift in LIFTS]
    mids = [blend(blend(p0, p1, 0.5), so, f) for f in (0.35, 0.6, 0.85)]
    for da, db, dc in ((10, 0, 0), (0, 12, 0), (12, 12, 0), (-10, 0, 0), (0, -12, 0), (18, 18, 0),
                       (10, 0, -25), (0, 12, 25), (20, 5, -30), (-15, 10, 0), (-20, 20, 10)):
        mm = blend(p0, p1, 0.5)
        mm['a'] += da
        mm['b'] += db
        mm['c'] += db + dc
        mids.append(mm)
    if cart is not None:
        tip, d = cart
        outv = -in_dir(p1['yaw'])
        for kk in (8.0, 12.0, 16.0):
            for hh in (0.0, -4.0, 4.0):
                mids.append(solve(tip - d * 4.0 + outv * kk + np.array([0, hh, 0]), d, p1['yaw'],
                                  e=p1.get('e', 0.0), u=p1.get('u', 0.0))[0])
    attempts += [(line_in, (r, t4)) for r in (8.0, 14.0)]
    attempts += [(retreat, (r, lift, t4)) for r in (5.0, 9.0, 13.0) for lift in ((0.0, 0.0), (6.0, 0.0), (0.0, 6.0))]
    attempts += [(via, (mm, t4)) for mm in mids if mm['a'] <= 95]
    attempts += [(retreat, (r, lift, t6)) for r in (9.0, 15.0) for lift in ((0.0, 0.0), (10.0, 0.0), (0.0, 10.0),
                                                                            (8.0, 8.0))]
    attempts += [(two, (f, t6)) for f in (0.6, 0.8, 1.0)]
    attempts += [(out_in, (lift, t6)) for lift in ((0.0, 0.0), (0.0, 4.0), (4.0, 0.0), (0.0, -4.0))]
    best = None
    for fn, a in attempts:
        del tr.poses[n0:]
        if not fn(*a):
            continue
        t_end = tr.poses[-1][0]
        bad = sweep(fr, tr, t0, t_end)
        if not bad:
            return t_end, True
        score = (len(bad), worst(bad))
        if best is None or score < best[0]:
            best = (score, fn, a)
    # last: via poses scattered round the straight path (a fixed seed: the same clips every run), one or two of them,
    # each clear of the frame where it stands
    rng = np.random.default_rng(7)
    t8 = max(t1, t0 + (5 if fast else 8))

    def clear_at(tau, q):
        parts = arm_parts(q, tr.tools_at(tau))
        t = tau - tr.clock
        return not STATIC.touch(parts) and not (fr is not None and t >= 0 and
                                                 fr.solid(t).touch(parts, ry(tr.theta_at(t)), DECKV))

    def scatter(base):
        q = dict(base)
        for kk, sp in (('yaw', 30.0), ('a', 25.0), ('b', 30.0), ('c', 40.0), ('hy', 25.0)):
            q[kk] += rng.uniform(-sp, sp)
        return q

    for n in range(160):
        del tr.poses[n0:]
        if n < 80:
            tm = t0 + (t8 - t0) // 2
            q = scatter(blend(p0, p1, 0.5))
            if not natural(q) or not clear_at(tm, q):
                continue
            tr.at(tm, q, 'easeInOutSine')
        else:
            ta, tb = t0 + (t8 - t0) // 3, t0 + 2 * (t8 - t0) // 3
            qa, qb = scatter(blend(p0, p1, 0.3)), scatter(blend(p0, p1, 0.7))
            if not (natural(qa) and natural(qb) and clear_at(ta, qa) and clear_at(tb, qb)):
                continue
            tr.at(ta, qa, 'easeInOutSine')
            tr.at(tb, qb, 'easeInOutSine')
        tr.at(t8, p1, ease)
        if not sweep(fr, tr, t0, t8):
            return t8, True
    del tr.poses[n0:]
    best[1](*best[2])
    return tr.poses[-1][0], False


def worst(bad):
    return max((h[2] for _, hs in bad for h in hs), default=0.0)


# ---------------------------------------------------------------------------------------------------------------------
# The stood-off pose per frame: the upright one unless a frame turning on the pad would meet it
# ---------------------------------------------------------------------------------------------------------------------
STANDOFF_CANDIDATES = [standoff_pose(b) for b in (8.0, 20.0, 32.0, 44.0, 56.0)] + \
    [standoff_pose(b, a=100.0) for b in (20.0, 40.0, 60.0)]


def standoff_hits(fr, so, times, thetas, tools=None):
    parts = arm_parts(so, tools if tools is not None else WELD)
    bad = []
    for t in times:
        for th in thetas:
            h = fr.hits(parts, t, th)
            if h:
                bad.append((t, th, max(x[2] for x in h), h[0][:2]))
    return bad


def choose_standoff(fr):
    """The first stood-off pose (most upright first) clear of the frame: as delivered (the turntable still) until 30
    ticks before its first seat, then at every turntable angle; else the least bad one."""
    t_turn = max(0, fr.slots[0][2] - 30)
    thetas = list(range(0, 360, 15))
    best = None
    for so in STANDOFF_CANDIDATES:
        bad = standoff_hits(fr, so, range(0, t_turn, 4), [0]) + \
            standoff_hits(fr, so, range(t_turn, fr.length + 1, 4), thetas)
        if not bad:
            return so, []
        score = (len(bad), max(b[2] for b in bad))
        if best is None or score < best[0]:
            best = (score, so, bad)
    return best[1], best[2]


# ---------------------------------------------------------------------------------------------------------------------
# The turntable's angle per slot: every slot planned at every angle, then the sequence with the least lateness
# ---------------------------------------------------------------------------------------------------------------------
THETAS = list(range(-180, 180, 15))
T_READY = 36 - LEAD              # frame clock when the arm is free after the look-over
CYCLE_SAME = 5                   # contact to next contact at one angle: hold 1, approach 2, press 2
CYCLE_TURN = 10                  # hold 1, stand off 3, (the turn, from 2 ticks early), approach 4, press 2, and spare
CYCLE_TURN_SMALL = 4             # a turn of 30 degrees or less with the arm only backed off along its tool


_FRAMES = {}


def get_frame(name):
    if name not in _FRAMES:
        _FRAMES[name] = Frame(name)
    return _FRAMES[name]


def _standoff_task(name):
    return name, choose_standoff(get_frame(name))


def _table_task(job):
    name, i, th, arcs, so, los = job
    fr = get_frame(name)
    slot, socks, S = fr.slots[i]
    if arcs == 'aim':
        pl = None
        for sk in socks:
            p, n = fr.socket(sk, S)
            pl = plan_aim(fr, p, S, float(th), so, label=sk)
            if pl:
                pl['sock'] = sk
                break
        if pl:
            pl['no_way_in'], pl['hidden'] = False, True
        return (name, i, th), pl
    pl = plan_slot(fr, slot, socks, S, float(th), arcs=arcs, so=so, los=los)
    if pl is not None and arcs and pl['reach'] and so is not None:
        pl = None                       # (the reach pass already looked)
    if pl is not None:
        pl['no_way_in'] = so is None    # clean where it stands, but not reached cleanly from the stood-off pose
        pl['hidden'] = not pl['reach'] and not los   # the arc may cross a part of the frame on its way
    return (name, i, th), pl


def angle_tables(names, pool, sos):
    """Every slot of every frame planned at every turntable angle: reaches first (from the stood-off pose, then from
    anywhere); then, where an angle has no reach, arcs (the sequencing may need one at any angle to stay on time). A
    slot left with no plan at all
    is planned again with less asked of it: first an arc whose line may cross a part of the frame, then plans clean
    where they stand but with no clean way in from the stood-off pose (the moves between slots find their own), and
    last the welder aimed at the socket from close to the stood-off pose."""
    tables = {n: {} for n in names}
    slots = [(n, i) for n in names for i in range(len(get_frame(n).slots))]

    def run(jobs, chunk=1):
        for key, pl in pool.imap_unordered(_table_task, jobs, chunksize=chunk):
            if pl is not None:
                tables[key[0]][key[1:]] = pl

    def empty(n, i):
        return not any(tables[n].get((i, th)) for th in THETAS)
    run([(n, i, th, False, sos[n], True) for n, i in slots for th in THETAS], 4)
    # a reach the arm cannot come into straight from the stood-off pose is still a reach: the builder comes into it from
    # the contact before
    run([(n, i, th, False, None, True) for n, i in slots for th in THETAS if tables[n].get((i, th)) is None], 4)
    jobs = []
    for n, i in slots:
        jobs += [(n, i, th, True, sos[n], True) for th in THETAS if tables[n].get((i, th)) is None]
    run(jobs)
    for so_of, los in ((lambda n: sos[n], False), (lambda n: None, True), (lambda n: None, False)):
        run([(n, i, th, True, so_of(n), los) for n, i in slots if empty(n, i) for th in THETAS if th % 30 == 0])
    run([(n, i, th, 'aim', sos[n], False) for n, i in slots if empty(n, i) for th in THETAS if th % 30 == 0])
    return tables


def choose_angles(fr, table):
    """Dynamic programme over the slots: cost = 5 per tick late + each plan's score (an arc 40) + 2 per turn."""
    states = {0: [(0.0, T_READY - CYCLE_SAME + 2, None)]}     # theta -> [(cost, contact t, back-pointer)]
    hist = []
    for i, (slot, socks, S) in enumerate(fr.slots):
        new = {}
        for th in THETAS:
            sc = plan_score(table.get((i, th)))
            if sc is None:
                continue
            cands = []
            for pth, lst in states.items():
                for j, (cost, c, bp) in enumerate(lst):
                    if abs(pth - th) < 1e-6:
                        cnew = max(S, c + CYCLE_SAME) if i else max(S, T_READY + 6)
                        turns = 0
                    else:
                        dth = abs(unwrap(th, pth) - pth)
                        cyc = CYCLE_TURN_SMALL if dth <= 30 else CYCLE_TURN
                        cnew = max(S, (c + cyc if i else T_READY + 6) + turn_ticks(dth))
                        turns = 1
                    cands.append((cost + 5 * (cnew - S) + sc + 2 * turns, cnew, (pth, j)))
            cands.sort()
            keep = []
            for cd in cands:
                if not any(o[0] <= cd[0] and o[1] <= cd[1] for o in keep):
                    keep.append(cd)
                if len(keep) >= 4:
                    break
            new[th] = keep
        if not new:
            raise RuntimeError('%s: slot %s has no plan at any angle' % (fr.name, slot))
        hist.append(new)
        states = new
    # trace back
    th = min(states, key=lambda x: states[x][0][0])
    j = 0
    seq = []
    for i in range(len(fr.slots) - 1, -1, -1):
        cost, c, bp = hist[i][th][j]
        seq.append(th)
        th, j = bp
    return list(reversed(seq))


# ---------------------------------------------------------------------------------------------------------------------
# The build, per frame
# ---------------------------------------------------------------------------------------------------------------------
LOCATORS = {}
ARCS = {}


def rnd(v):
    return [round(float(x), 2) for x in v]


class Builder:
    def __init__(self, name, table, so):
        self.fr = get_frame(name)
        self.table = table
        self.name = name
        self.tr = Track()
        self.so, self.so_bad = so
        self.contacts = []
        self.notes = []

    # -- one contact: approach, press home, the weld (or the arc) ---------------------------------------------------
    def weld(self, pl, tau, cur, t_target, locname, arcname, after_turn=False, press=2):
        """Come to the approach pose, press home on t_target (or as soon after as the arm can), weld on the next tick,
        the sound on the one after. Returns (tau, pose, contact tau)."""
        tr, fr = self.tr, self.fr
        n0 = len(tr.poses)
        due = t_target + LEAD
        best = None
        # at the joints' ordinary speed with a two-tick press; if that is late, a one-tick press; then in a hurry
        for pr, fast in ((press, False), (1, False), (1, True)):
            for reach in ((2, 3, 4) if after_turn else (2,)):
                del tr.poses[n0:]
                t_app = max(due - pr, tau + reach)
                t_app, ok = move(fr, tr, tau, cur, t_app, pl['approach'], self.so, cart=(pl['tip'], pl['dir']),
                                 fast=fast)
                if ok:
                    break
            key = (not ok, max(0, t_app + pr - due))
            if best is None or key < best[0]:
                best = (key, list(tr.poses[n0:]), t_app, ok, pr)
            if ok and t_app + pr <= due:
                break
        del tr.poses[n0:]
        tr.poses.extend(best[1])
        t_app, ok, press = best[2], best[3], best[4]
        if not ok and cur is not self.so:
            # out to the stood-off pose and in from there (the way in every plan was checked for)
            kept = list(tr.poses[n0:])
            del tr.poses[n0:]
            t_so, ok1 = move(fr, tr, tau, cur, tau + 3, self.so, self.so)
            t_app2, ok2 = move(fr, tr, t_so, self.so, max(t_target + LEAD - press, t_so + 3), pl['approach'], self.so,
                               cart=(pl['tip'], pl['dir']))
            if ok1 and ok2:
                t_app, ok = t_app2, True
            else:
                del tr.poses[n0:]
                tr.poses.extend(kept)
        self.last_ok = ok
        if not ok:
            self.notes.append('%s %s: the move in still touches the frame' % (self.name, locname))
        tc = t_app + press
        self.press(t_app, pl['approach'], tc, pl['pose'], 'easeInQuad')
        if pl['reach']:
            LOCATORS[locname] = rnd(pl['tip'])
            fx_tick = tr.fx(tc + 1, 'particle', 'headinajar:gantry_weld', locname)
        else:
            LOCATORS[locname] = rnd(pl['point'])
            LOCATORS[arcname] = rnd(pl['tip'])
            ARCS[arcname] = {'from': rnd(pl['tip']), 'to': rnd(pl['point'])}
            tr.fx(tc, 'particle', 'headinajar:gantry_arc', arcname)
            fx_tick = tr.fx(tc + 1, 'particle', 'headinajar:gantry_weld', locname)
        if fx_tick != tc + 1:
            self.notes.append('%s %s: weld particle moved to %d (effect clash)' % (self.name, locname, fx_tick))
        tr.fx(tc + 2, 'sound', SND('weld', 0.5, 1.0), locname)
        tr.at(tc + 1, pl['pose'], None)                     # held through the weld
        if self.last_ok and sweep(fr, tr, t_app, tc + 1):
            self.last_ok = False                # met late, the frame has moved on into the tool's way
        return tc + 1, pl['pose'], tc

    def press(self, t0, p0, t1, p1, ease):
        """The press home (or the strike): the tool's tip straight along its line, else a plain joint-space key."""
        tr = self.tr
        n0 = len(tr.poses)
        if cart_path(tr, t0, p0, t1, p1, (0.0, 0.0), ease) and not sweep(self.fr, tr, t0, t1):
            return
        del tr.poses[n0:]
        tr.at(t1, p1, ease)

    def turn_to(self, new, tau, cur, last_pl):
        """Stand off (straight from the contact, or backing off along the tool first) and turn, the turn starting up
        to 2 ticks before the arm is stood off if the sweep stays clean. Returns (tau, pose)."""
        tr, so, fr = self.tr, self.so, self.fr
        theta = tr.turn[-1][1]
        new = unwrap(new, theta)
        dur = turn_ticks(new - theta)
        n_p, n_t, n_e = len(tr.poses), len(tr.turn), len(tr.effects)
        if cur is not so and last_pl is not None:
            # quickest: the arm backs off along its tool and holds there while the frame turns past it
            tip, d = last_pl['tip'], last_pl['dir']
            for r in (8.0, 12.0, 16.0):
                del tr.poses[n_p:], tr.turn[n_t:], tr.effects[n_e:]
                q, err, short = solve(tip - d * r, d, cur['yaw'], e=cur.get('e', 0.0), u=cur.get('u', 0.0))
                if short > 0.2 or err > TWIST_MAX or not natural(q):
                    continue
                t_q, ok = move(fr, tr, tau, cur, tau + 2, q, so, 'easeOutQuad')
                if not ok:
                    continue
                t_start = max(t_q - 1, T_READY + LEAD)
                t0 = t_start - LEAD
                if tr.turn[-1][0] < t0:
                    tr.turn.append((t0, theta, None))
                tr.turn.append((t0 + dur, new, 'easeInOutSine'))
                tr.fx(t_start, 'sound', SND('turntable', 0.6, 1.0), 'deck')
                t_end = t_start + dur
                if tr.poses[-1][0] < t_end:
                    tr.at(t_end, q, None)
                if not sweep(fr, tr, tau, t_end):
                    return t_end, q
        for back, early in ((False, 2), (False, 1), (False, 0), (True, 1), (True, 0)):
            del tr.poses[n_p:], tr.turn[n_t:], tr.effects[n_e:]
            t = tau
            if cur is not so:
                if back and last_pl is not None:
                    t, _ = move(fr, tr, t, cur, t + 2, last_pl['approach'], so, 'easeOutQuad')
                    p = last_pl['approach']
                else:
                    p = cur
                t_so = t + 3
            else:
                t_so = tau
            t_start = max(t_so - early, T_READY + LEAD)
            t0 = t_start - LEAD
            if tr.turn[-1][0] < t0:
                tr.turn.append((t0, theta, None))
            tr.turn.append((t0 + dur, new, 'easeInOutSine'))
            if cur is not so:
                t_so, ok = move(fr, tr, t, p, t_so, so, so)
                tr.fx(max(t_so - 2, tau + 1), 'sound', SND('servo', 0.5, 1.0), 'swivel')
            tr.fx(t_start, 'sound', SND('turntable', 0.6, 1.0), 'deck')
            t_end = max(t_start + dur, t_so)
            if tr.poses[-1][0] < t_end:
                tr.at(t_end, so, None)
            if not sweep(fr, tr, tau, t_end):
                break
        return t_end, so

    def build(self):
        fr, tr, so, name = self.fr, self.tr, self.so, self.name
        L = fr.length
        rest = dict(REST_ABS)
        # ---- tau 0-15: the klaxon, the head lifts out of its cradle, the arm swings back and out to stand off -------
        tr.fx(0, 'sound', SND('klaxon', 0.8, 1.0), 'klaxon')
        tr.at(0, rest)
        lift = dict(rest)
        lift['a'] -= 3          # wind-up: the upper arm leans 3 degrees in
        lift['b'] += 8          # the forearm lifts, the head comes up out of the cradle (about 3 px)
        tr.at(3, lift, 'easeOutQuad')
        tr.fx(3, 'sound', SND('servo', 0.5, 0.8), 'swivel')
        over = dict(so)
        over['a'] += 3
        over['b'] -= 3
        tr.at(11, over, 'easeInOutCubic')
        tr.at(15, so, 'easeInOutSine')
        tr.fx(11, 'sound', SND('servo_stop', 0.4, 0.8), 'swivel')
        tr.turn = [(-LEAD, 0.0, None)]
        # ---- tau 10-36: the frame delivered folded; the arm looks it over (a nod); the welder slides out ------------
        look = dict(so)
        look['c'] -= 12
        look['b'] -= 4
        tr.at(24, look, 'easeInOutSine')
        tr.at(36, so, 'easeInOutSine')

        table = self.table
        seq = choose_angles(fr, table)
        self.seq = seq
        t_out = min(50, fr.slots[0][2] + LEAD - 8)
        tr.tool('tool_weld', t_out, 0.0, None)
        tr.tool('tool_weld', t_out + 3, EXT, 'easeOutCubic')
        tr.fx(t_out, 'sound', SND('tool_out', 0.4, 1.2), 'swivel')

        # ---- the slots in the frame's order, the turntable turning between groups with the arm stood off ------------
        tau, cur, last_pl, after_turn = 36, so, None, True
        plans = []
        for i, (slot, socks, S) in enumerate(fr.slots):
            th = float(seq[i])
            if abs(unwrap(th, tr.turn[-1][1]) - tr.turn[-1][1]) > 1e-6:
                if cur is so:
                    t_late = S + LEAD - 2 - 4 - turn_ticks(unwrap(th, tr.turn[-1][1]) - tr.turn[-1][1])
                    if t_late > tau:
                        tr.at(t_late, so, None)
                        tau = t_late
                tau, cur = self.turn_to(th, tau, cur, last_pl)
                after_turn = True
            th = tr.turn[-1][1]
            if cur is so and S + LEAD - tau > 14:
                # stood off with time in hand: wait there (the frame unfolds meanwhile) and come in shortly before
                tr.at(S + LEAD - 12, so, None)
                tau = S + LEAD - 12
            pl = table[(i, int(seq[i]))]
            cands = [pl] + list(pl.get('alts', ()))
            # planned on the seat tick; if the arm can only be there later, planned again for that tick
            t_est = max(S + LEAD - 2, tau + (4 if after_turn else 2)) + 2 - LEAD
            if t_est > S:
                pl2 = plan_slot(fr, slot, socks, t_est, th, so=self.so)
                if pl2 is not None and (pl2['reach'] or not pl['reach']):
                    cands = [pl2] + list(pl2.get('alts', ())) + cands
            if not any(c['reach'] for c in cands):
                # the table has only an arc here (no reach it could come into from the stood-off pose): a reach the
                # arm comes into from where it is, if there is one
                for sk in socks:
                    p_, n_ = fr.socket(sk, S)
                    q = plan_point(fr, p_, n_, S, th, label=sk)
                    if q is not None:
                        for c in [q] + q['alts']:
                            c['sock'] = sk
                        cands = [q] + q['alts'] + cands
                        break
            # the reaches first, the nearest to where the arm is first; the first met on time with a clean way in is
            # taken (a reach before an arc), else the least late
            cands = sorted(cands, key=lambda c: (not c['reach'], joint_travel(cur, c['approach'])))[:5]
            arcs_tried = any(not c['reach'] for c in cands)
            saved = (copy.deepcopy(tr), list(self.notes), dict(LOCATORS), dict(ARCS))
            best = None
            kc = -1
            while kc + 1 < len(cands):
                kc += 1
                cand = cands[kc]
                if kc:
                    self.tr, self.notes = copy.deepcopy(saved[0]), list(saved[1])
                    tr = self.tr
                    LOCATORS.clear()
                    LOCATORS.update(saved[2])
                    ARCS.clear()
                    ARCS.update(saved[3])
                cand = self.at_theta(cand, th)
                res = self.weld(cand, tau, cur, S, 'w_%s_%s' % (name, slot), 'arc_%s_%s' % (name, slot), after_turn)
                key = (not self.last_ok, res[2] - LEAD > S, not cand['reach'], max(0, res[2] - LEAD - S), kc)
                if best is None or key < best[0]:
                    best = (key, (copy.deepcopy(tr), list(self.notes), dict(LOCATORS), dict(ARCS)), cand, res,
                            self.last_ok)
                if self.last_ok and res[2] - LEAD <= S:
                    break
                if kc == len(cands) - 1 and not arcs_tried:
                    # no reach can be met on time from where the arm is: an arc struck on time, the nearest first
                    arcs_tried = True
                    for sk in socks:
                        p_, n_ = fr.socket(sk, S)
                        a_ = plan_arc(fr, p_, n_, S, th, label=sk)
                        if a_ is not None:
                            a_['sock'] = sk
                            cands.append(a_)
            self.tr, self.notes = best[1][0], best[1][1]
            tr = self.tr
            LOCATORS.clear()
            LOCATORS.update(best[1][2])
            ARCS.clear()
            ARCS.update(best[1][3])
            pl, res, self.last_ok = best[2], best[3], best[4]
            if not self.last_ok and pl['reach']:
                # no clean way onto the socket: aim at it from close to the stood-off pose instead, if that is clean
                aim = None
                for sk in socks:
                    p_, n_ = fr.socket(sk, S)
                    aim = plan_aim(fr, p_, S, th, so, label=sk)
                    if aim:
                        aim['sock'] = sk
                        break
                if aim is not None:
                    kept = (tr, list(self.notes), dict(LOCATORS), dict(ARCS), res)
                    self.tr, self.notes = copy.deepcopy(saved[0]), list(saved[1])
                    tr = self.tr
                    LOCATORS.clear()
                    LOCATORS.update(saved[2])
                    ARCS.clear()
                    ARCS.update(saved[3])
                    aim['hidden'] = True
                    res2 = self.weld(aim, tau, cur, S, 'w_%s_%s' % (name, slot), 'arc_%s_%s' % (name, slot),
                                     after_turn)
                    if self.last_ok:
                        pl, res = aim, res2
                    else:
                        self.tr, self.notes = kept[0], kept[1]
                        tr = self.tr
                        LOCATORS.clear()
                        LOCATORS.update(kept[2])
                        ARCS.clear()
                        ARCS.update(kept[3])
            tau, cur, tc = res
            after_turn = False
            self.contacts.append({'slot': slot, 'seat': S, 'contact': tc - LEAD, 'late': tc - LEAD - S,
                                  'locator': 'w_%s_%s' % (name, slot), 'reach': bool(pl['reach']),
                                  'socket': pl.get('sock'), 'theta': round(th, 1),
                                  'arc': None if pl['reach'] else 'arc_%s_%s' % (name, slot),
                                  'arc_length': None if pl['reach'] else round(pl['off'], 1),
                                  'arc_crosses_frame': bool(pl.get('hidden')),
                                  'no_clean_way_in': bool(pl.get('no_way_in'))})
            plans.append(pl)
            last_pl = pl
        self.plans = plans
        tau, cur = self.finish(tau, cur, last_pl)
        return self

    def at_theta(self, pl, th):
        """A plan made at a turntable angle equal to th modulo 360 carries th itself."""
        pl = dict(pl)
        pl['theta'] = th
        return pl

    # -- after the slots: plating welds, inspection, the stamp, stand off, the presentation -------------------------
    # the closing sequence, fullest first; a frame whose last seat leaves too little time gets a shorter one so the
    # presentation still ends inside its build: (plating welds, inspection ticks, stamp wind-up hold)
    LEVELS = ((2, 10, 2), (1, 10, 2), (1, 7, 2), (1, 6, 1), (0, 6, 1), (0, 4, 1))

    def finish(self, tau, cur, last_pl):
        t_from = self.fr.length - 1 - PRESENT          # the presentation turn starts by here (frame clock)
        saved = (copy.deepcopy(self.tr), list(self.notes), dict(LOCATORS), dict(ARCS))
        for n, level in enumerate(self.LEVELS):
            if n:
                self.tr, self.notes = copy.deepcopy(saved[0]), list(saved[1])
                LOCATORS.clear()
                LOCATORS.update(saved[2])
                ARCS.clear()
                ARCS.update(saved[3])
            t_so = self._finish(tau, cur, level, t_from)
            if t_so - LEAD + 1 <= t_from:
                break
        self.level = level
        fr, tr, so, name, L = self.fr, self.tr, self.so, self.name, self.fr.length
        theta = tr.turn[-1][1]
        tf = max(t_so - LEAD + 1, t_from)
        if tf > t_from:
            self.notes.append('%s: presentation starts at t %d, %d ticks after %d' % (name, tf, tf - t_from, t_from))
        self.present = [tf, tf + PRESENT]
        if tr.turn[-1][0] < tf:
            tr.turn.append((tf, theta, None))
        self.turn_keys = list(tr.turn)                 # the plan's turn: the presentation is the code's
        face = 360.0 * round(theta / 360.0)
        tr.turn.append((tf + PRESENT, face, 'easeInOutSine'))   # review copies: the owner straight in front
        tr.fx(tf + LEAD, 'sound', SND('turntable', 0.6, 1.1), 'deck')
        self.length = LEAD + L + 2
        if so != LOADED and tf + PRESENT + LEAD < self.length:
            # the turntable has stopped at the owner's bearing: the arm settles into the loaded pose
            tr.at(tf + PRESENT + LEAD, so, None)
            tr.at(self.length, LOADED, 'easeInOutSine')
            self.settle = standoff_hits(fr, blend(so, LOADED, 0.5), [L], range(0, 360, 10), {})
        else:
            tr.at(self.length, so, None)
            self.settle = []
        return t_so, so

    def _finish(self, tau, cur, level, t_from):
        """Plating welds, inspection, the stamp, stand off. Returns the tau the arm is stood off."""
        fr, tr, so, name = self.fr, self.tr, self.so, self.name
        n_plate, insp_len, hold = level
        theta = tr.turn[-1][1]
        spare = t_from + LEAD - tau - 52
        # ---- the plating pass: the welder goes back over chest sockets --------------------------------------------
        t_plate = tau + 3 + max(0, min(spare, 150 + LEAD - tau - 3))
        pls = self.plate_targets(t_plate - LEAD + 3, theta) if n_plate else []
        plated = 0
        for pl in pls[:n_plate]:
            locname = 'plate_%s_%d' % (name, plated + 1)
            target = t_plate - LEAD + 3 if plated == 0 else 0
            snap = (copy.deepcopy(tr.__dict__), list(self.notes), dict(LOCATORS), dict(ARCS))
            tau2, cur2, tc = self.weld(pl, tau, cur, target, locname, 'arc_%s_plate_%d' % (name, plated + 1), press=3)
            if not self.last_ok:
                # no clean way onto it from where the arm is: that plate is left out
                tr.__dict__.update(snap[0])
                self.notes[:] = snap[1]
                LOCATORS.clear()
                LOCATORS.update(snap[2])
                ARCS.clear()
                ARCS.update(snap[3])
                continue
            tau, cur = tau2, cur2
            self.press(tau, pl['pose'], tau + 3, pl['approach'], 'easeOutQuad')
            tau += 3
            cur = pl['approach']
            plated += 1
        # ---- inspection: the arm stands back and up, the head lamp sweeps the frame bottom to top ------------------
        # the head cranes in and down to look at the feet, then up and out to look at the top (the forearm swings 30
        # degrees: the head moves about 12 px, outside the frame's outline)
        insp = dict(so)
        insp['b'] = so['b'] - 3.0
        insp['c'] = so['b'] - 78.0
        tau, _ = move(fr, tr, tau, cur, tau + 5, insp, so)
        tr.fx(tau, 'sound', SND('scan', 0.4, 1.0), 'swivel')
        self.scan = tau
        up = dict(so)
        up['b'] = so['b'] + 27.0
        up['c'] = so['b'] + 12.0
        tau, _ = move(fr, tr, tau, insp, tau + insp_len, up, so)
        cur = up
        # ---- the stamp: welder in, stamp out, aim, a beat, the strike on the chest (contact, thock), back off ----------
        tr.tool('tool_weld', tau, EXT, None)
        tr.tool('tool_weld', tau + 2, 0.0, 'easeInQuad')
        tr.tool('stamp', tau + 2, 0.0, None)
        tr.tool('stamp', tau + 4, EXT, 'easeOutCubic')
        tgt, sa = self.stamp_target(tau + 5 + hold + 2 - LEAD, theta)
        self.stamp_tick = None
        if tgt is not None:
            t_sa, _ = move(fr, tr, tau, cur, tau + 5, sa, so)
            tau = max(t_sa, tau + 4)
            tr.at(tau + hold, sa, None)                 # hold: a beat before the strike
            tau += hold
            self.press(tau, sa, tau + 2, tgt['pose'], 'easeInExpo')   # the strike
            tr.fx(tau + 2, 'sound', SND('stamp', 0.7, 1.0), 'stamp_' + name)
            LOCATORS['stamp_' + name] = rnd(tgt['tip'])
            self.stamp_tick = tau + 2
            tr.at(tau + 3, tgt['pose'], None)
            self.press(tau + 3, tgt['pose'], tau + 6, sa, 'easeOutQuad')
            tau += 6
            cur = sa
        else:
            self.notes.append('%s: no clean stamp target' % name)
        tr.tool('stamp', tau, EXT, None)
        tr.tool('stamp', tau + 3, 0.0, 'easeInQuad')
        # ---- stand off; the turntable then presents the frame to its owner (code) -------------------------------
        t_so, _ = move(fr, tr, tau, cur, tau + 4, so, so)
        tr.fx(t_so, 'sound', SND('servo_stop', 0.4, 0.9), 'swivel')
        return t_so

    def plate_targets(self, t, theta):
        """Two chest sockets to weld the plating on, planned at frame time t and the current angle (reach preferred)."""
        fr = self.fr
        order = [s for s in fr.slots if s[0].startswith('torso')] + \
            [s for s in fr.slots if not s[0].startswith('torso')]
        out, arcs = [], []
        for slot, socks, S in order:
            pl = plan_slot(fr, slot, socks, t, theta, arcs=False, so=self.so)
            if pl and self.holds(pl, t, theta):
                out.append(pl)
            if len(out) >= 2:
                break
        if len(out) < 2:
            for slot, socks, S in order:
                if len(out) + len(arcs) >= 2:
                    break
                pl = plan_slot(fr, slot, socks, t, theta, so=self.so)
                if pl and not pl['reach'] and self.holds(pl, t, theta):
                    arcs.append(pl)
        return out + arcs

    def holds(self, pl, t, theta, tools=WELD, span=16):
        """The contact and approach poses still clear of the frame up to `span` ticks later (a weld that comes late
        meets a frame that may have moved on)."""
        return not any(collide(self.fr, pl['pose'], t + k, theta, tools) or
                       collide(self.fr, pl['approach'], t + k, theta, tools) for k in range(2, span + 1, 2))

    def stamp_target(self, t, theta):
        """The stamp's strike: a reach onto the chest at the current angle (the stamp out); its wind-up pulls the head
        up to 9 px back level along the tool, out past the frame's outline."""
        fr = self.fr
        names = ['torso_2', 'torso_0', 'torso_1', 'core', 'torso_3', 'torso_4', 'sensor']
        order = [s for n in names for s in fr.slots if s[0] == n] + [s for s in fr.slots if s[0] not in names]
        tools = {'stamp': EXT}
        for slot, socks, S in order:
            pl = plan_slot(fr, slot, socks, t, theta, tools, arcs=False, so=self.so)
            if not pl or not self.holds(pl, t, theta, tools, 8):
                continue
            dh = np.array([pl['dir'][0], 0.0, pl['dir'][2]])
            dh = dh / max(1e-6, np.linalg.norm(dh))
            for back in (9.0, 7.0, 5.0):
                sa, err, short = solve(pl['tip'] - dh * back - np.array([0, 1.0, 0]), pl['dir'], pl['pose']['yaw'],
                                       e=pl['pose'].get('e', 0.0), u=pl['pose'].get('u', 0.0))
                if short < 0.2 and not collide(fr, sa, t - 3, theta, tools) and not collide(fr, sa, t - 6, theta, tools):
                    return pl, sa
        return None, None


# ---------------------------------------------------------------------------------------------------------------------
# Short clips (generic, from rest or from stand-off)
# ---------------------------------------------------------------------------------------------------------------------
def simple(poses, length, effects=(), loop='hold', tools=None):
    tr = Track()
    for tau, p, e in poses:
        tr.at(tau, p, e)
    for tl, ks in (tools or {}).items():
        tr.tools[tl] = [(0, ks[0][1], None)] + list(ks[1:]) if ks[0][0] == 0 else [(0, 0.0, None)] + list(ks)
    for e in effects:
        tr.fx(*e)
    return tr, tr.clip(length, loop)


def short_clips():
    so, rest = LOADED, dict(REST_ABS)
    out = {}
    # loaded: the stand-off pose held (a mecha on the pad). Also the skip: boarding during the build starts `loaded`
    # with a 10-tick controller transition from wherever the arm is.
    out['loaded'] = simple([(0, so, None), (1, so, None)], 1)
    # load: rest -> stand off when a mecha walks onto the pad (13 ticks: wind-up, swing, settle)
    lift = dict(rest)
    lift['a'] -= 3
    lift['b'] += 8
    over = dict(so)
    over['a'] += 3
    over['b'] -= 3
    out['load'] = simple([(0, rest, None), (3, lift, 'easeOutQuad'), (10, over, 'easeInOutCubic'),
                          (13, so, 'easeInOutSine')], 13,
                         [(0, 'sound', SND('servo', 0.5, 0.8), 'swivel'), (10, 'sound', SND('servo_stop', 0.4, 0.8),
                                                                           'swivel')])
    # unload: stand off -> rest when the pad empties (16 ticks; the head into the cradle on 13, easeInQuad, a 1 px
    # bounce, settle)
    above = dict(rest)
    above['b'] += 6
    bounce = dict(rest)
    bounce['b'] += 1.5
    out['unload'] = simple([(0, so, None), (3, blend(so, above, 0.1), 'easeOutQuad'), (11, above, 'easeInOutSine'),
                            (13, rest, 'easeInQuad'), (14, bounce, 'easeOutQuad'), (16, rest, 'easeInQuad')], 16,
                           [(0, 'sound', SND('servo', 0.5, 0.8), 'swivel'), (13, 'sound', SND('park', 0.5, 1.0),
                                                                             'cradle')])
    # open / close: at rest the head lifts out of the cradle and looks at the player (face out of the plane's IN side,
    # towards the front-left and up); the lens is lit while open
    look = dict(rest)
    look['b'] += 14
    look['c'] = -150.0
    look_o = dict(look)
    look_o['b'] += 2
    out['open'] = simple([(0, rest, None), (5, look_o, 'easeOutCubic'),
                          (7, look, 'easeInOutSine')], 7, [(0, 'sound', SND('servo', 0.4, 1.2), 'swivel')])
    out['close'] = simple([(0, look, None), (5, rest, 'easeInQuad'), (6, bounce, 'easeOutQuad'),
                           (8, rest, 'easeInQuad')], 8, [(5, 'sound', SND('park', 0.5, 1.0), 'cradle')])
    so_look = dict(so)
    so_look['c'] = 0.0
    so_look['b'] -= 6
    out['open_loaded'] = simple([(0, so, None), (5, so_look, 'easeOutCubic'), (7, so_look, None)], 7,
                                [(0, 'sound', SND('servo', 0.4, 1.2), 'swivel')])
    out['close_loaded'] = simple([(0, so_look, None), (6, so, 'easeInOutSine')], 6)
    # refuse, at rest: the head lifts out of the cradle and shakes "no" (head yaw +-15), the whole arm rocks 4 degrees
    # in its plane (the elbow, the top of the silhouette, moves 1.3 px), down again on 10
    r1 = dict(rest)
    r1['b'] += 7
    r1['a'] += 4
    shake = []
    for tau, s in ((3, 15), (5, -15), (7, 12)):
        p = dict(r1)
        p['hy'] = s
        p['a'] = rest['a'] + (4 if s > 0 else -2)
        shake.append((tau, p, 'easeInOutSine'))
    out['refuse'] = simple([(0, rest, None), (2, r1, 'easeOutQuad')] + shake +
                           [(8, r1, 'easeInOutSine'), (10, rest, 'easeInQuad')], 10,
                           [(0, 'sound', 'headinajar:machine.rattle|0.4|1.0', 'swivel')])
    # refuse_loaded: from stand-off, the head shakes and the forearm rocks 4 degrees
    sh2 = []
    for tau, s in ((2, 15), (4, -15), (6, 12), (8, 0)):
        p = dict(so)
        p['hy'] = s
        p['b'] = so['b'] + (4 if s > 0 else (-4 if s < 0 else 0))
        sh2.append((tau, p, 'easeInOutSine'))
    out['refuse_loaded'] = simple([(0, so, None)] + sh2, 8,
                                  [(0, 'sound', 'headinajar:machine.rattle|0.4|1.0', 'swivel')])
    # charge (loop 40): stood off holding the charge lead (the gripper out), the forearm rocking 15 degrees out and
    # back with the pulse (the head travels about 6.5 px); the reel turns by code
    n1 = dict(so)
    n1['b'] += 15
    n1['c'] += 10
    out['charge'] = simple([(0, so, None), (20, n1, 'easeInOutSine'), (40, so, 'easeInOutSine')], 40,
                           [(0, 'sound', SND('charge_tick', 0.25, 1.0), 'reel')], loop='loop',
                           tools={'tool_grip': [(0, EXT, None)]})
    return out


# ---------------------------------------------------------------------------------------------------------------------
# Disassembly and repair, per frame (the frame parked in its build clip's last pose)
# ---------------------------------------------------------------------------------------------------------------------
FRONT = -135.0                   # the turntable angle that shows the frame's front to the arm


def front_row(fr, t, theta, tools, so=None):
    """A run of clean reach points across the frame's front (its -Z face) at one height: candidates every 2 px across
    the frame at heights the arm reaches, the longest run of neighbours that are all clean. [(point, plan)]."""
    bx = fr.fk.boxes(fr.mats(t))
    pts = []
    for (n, b, f, to, Rw, Tw, Re, oe) in bx:
        P = surface_points(f, to, 1.0)
        P = (Re @ (P - oe).T).T + oe
        pts.append((Rw @ P.T).T + Tw)
    P = np.vstack(pts)
    best = []
    for y in np.arange(30.0, 8.0, -2.0):          # frame px above the deck; world y 50 down to 28
        band = P[np.abs(P[:, 1] - y) < 0.6]
        if not len(band):
            continue
        xs = np.arange(math.ceil(band[:, 0].min()) + 1, band[:, 0].max() - 1, 2.0)
        run, cur_run = [], []
        prev_z = None
        for x in xs:
            col = band[np.abs(band[:, 0] - x) < 0.6]
            pl = None
            if len(col):
                z = col[:, 2].min()
                p = np.array([x, y, z])
                if prev_z is None or abs(z - prev_z) <= 2.0:
                    pl = plan_point(fr, p, [0, 0, -1], t, theta, tools, so=so)
                if pl is not None and cur_run:
                    # the seam from the last point to this one: the tool's tip along the line, clear at its middle
                    q = cur_run[-1][1]
                    dm = q['dir'] + pl['dir']
                    dm = dm / np.linalg.norm(dm)
                    for f in (0.25, 0.5, 0.75):
                        mid, err, short = solve(q['tip'] + (pl['tip'] - q['tip']) * f, dm, q['pose']['yaw'],
                                                e=q['pose'].get('e', 0.0), u=q['pose'].get('u', 0.0))
                        if short > 0.2 or collide(fr, mid, t, theta, tools) or \
                                collide(fr, blend(q['pose'], pl['pose'], f), t, theta, tools):
                            pl = None
                            break
            if pl is None:
                if len(cur_run) > len(run):
                    run = cur_run
                cur_run, prev_z = [], None
                continue
            cur_run.append((p, pl))
            prev_z = z
        if len(cur_run) > len(run):
            run = cur_run
        if len(run) > len(best):
            best = run
        if len(best) >= 8:
            break
    return best


REPAIR_THETAS = (FRONT, FRONT + 20, FRONT - 20, FRONT + 40, FRONT - 40, FRONT + 60)


def weld_pass(fr, t, tools, so):
    """The repair's weld pass: the longest run of front points over the turntable angles that show the front."""
    best = (FRONT, [])
    for th in REPAIR_THETAS:
        row = front_row(fr, t, th, tools, so)
        if len(row) > len(best[1]):
            best = (th, row)
        if len(best[1]) >= 8:
            break
    return best


def frame_clip(b, kind):
    """disassemble (60 ticks): the front turned to the arm, three wrench visits in reverse slot order, stand off, the
    frame taken away in a puff; repair: the front turned to the arm, the welder run along the frame's front (sparks
    every three ticks), stand off, turned back. Both start and end in the loaded pose (a frame parked on the pad)."""
    fr, name = b.fr, b.name
    so = LOADED
    t_park = fr.length
    tr = Track()
    tr.clock = 0                 # the frame stands parked: its pose is the build clip's last one at every tick
    tr.at(0, so, None)
    notes = []
    frp = ParkedFrame(fr)
    if kind == 'disassemble':
        length = 60
        tools = {'tool_wrench': EXT}
        theta = FRONT
        tr.turn = [(0, 0.0, None), (2, 0.0, None), (10, theta, 'easeInOutSine')]
        tr.fx(2, 'sound', SND('turntable', 0.6, 1.0), 'deck')
        tr.at(10, so, None)
        tr.tool('tool_wrench', 8, 0.0, None)
        tr.tool('tool_wrench', 10, EXT, 'easeOutCubic')
        visits = []
        for slot, socks, S in reversed(fr.slots):
            pl = plan_slot(fr, slot, socks, t_park, theta, tools, arcs=False, so=so)
            if pl:
                visits.append(pl)
            if len(visits) >= 3:
                break
        if len(visits) < 3:
            for p, pl in front_row(fr, t_park, theta, tools, so)[::3]:
                if len(visits) >= 3:
                    break
                visits.append(pl)
        if not visits:
            notes.append('%s: no clean wrench visit' % name)
        last, cur = 10, so
        for i, (pl, tc) in enumerate(zip(visits, (16, 28, 40))):
            t_app, ok = move(frp, tr, last, cur, max(last + 3, tc - 3), pl['approach'], so,
                             cart=(pl['tip'], pl['dir']))
            if not ok:
                notes.append('%s visit %d: the move in touches the frame' % (name, i + 1))
            tc = max(tc, t_app + 2)
            press_line(frp, tr, t_app, pl['approach'], tc, pl['pose'], 'easeInQuad')
            tr.fx(tc, 'sound', SND('ratchet', 0.5, 1.0), 'swivel')
            tr.fx(tc + 3, 'sound', SND('ratchet', 0.5, 1.1), 'swivel')
            tr.at(tc + 4, pl['pose'], None)
            press_line(frp, tr, tc + 4, pl['pose'], tc + 7, pl['approach'], 'easeOutQuad')
            last, cur = tc + 7, pl['approach']
        t_so, _ = move(frp, tr, last, cur, max(last + 6, 50), so, so)
        tr.tool('tool_wrench', t_so, EXT, None)
        tr.tool('tool_wrench', t_so + 3, 0.0, 'easeInQuad')
        tr.fx(length, 'particle', 'minecraft:poof', 'deck')
        if t_so < length:
            tr.at(length, so, None)
        return tr, length, notes, None
    # ---- repair: a weld pass along the front -------------------------------------------------------------------------
    tools = WELD
    theta, row = weld_pass(fr, t_park, tools, so)
    tr.turn = [(0, 0.0, None), (2, 0.0, None), (10, theta, 'easeInOutSine')]
    tr.fx(2, 'sound', SND('turntable', 0.6, 1.0), 'deck')
    tr.at(10, so, None)
    tr.tool('tool_weld', 8, 0.0, None)
    tr.tool('tool_weld', 11, EXT, 'easeOutCubic')
    tr.fx(8, 'sound', SND('tool_out', 0.4, 1.2), 'swivel')
    if len(row) < 4:
        notes.append('%s: only %d clean points on the front for the weld pass' % (name, len(row)))
    if len(row) > 9:                               # nine points at most, spread along the run
        idx = np.round(np.linspace(0, len(row) - 1, 9)).astype(int)
        row = [row[i] for i in idx]
    pts = []
    first = row[0][1]
    t_app, ok = move(frp, tr, 10, so, 13, first['approach'], so, cart=(first['tip'], first['dir']))
    if not ok:
        notes.append('%s repair: the move in touches the frame' % name)
    t_c = max(16, t_app + 3)
    press_line(frp, tr, t_app, first['approach'], t_c, first['pose'], 'easeInQuad')
    for n, (p, pl) in enumerate(row):
        tick = t_c + 3 * n
        if n:
            if not press_line(frp, tr, tick - 3, row[n - 1][1]['pose'], tick, pl['pose'], 'easeInOutSine'):
                notes.append('%s repair: the pass touches the frame between points %d and %d' % (name, n, n + 1))
        loc = 'r_%s_%d' % (name, n + 1)
        LOCATORS[loc] = rnd(pl['tip'])
        tr.fx(tick, 'particle', 'headinajar:gantry_weld', loc)
        if n % 2 == 0:
            tr.fx(tick + 1, 'sound', SND('weld', 0.5, 0.95 + 0.03 * n), loc)
        pts.append(tick)
    tick = pts[-1]
    last_pl = row[-1][1]
    tr.at(tick + 1, last_pl['pose'], None)
    press_line(frp, tr, tick + 1, last_pl['pose'], tick + 4, last_pl['approach'], 'easeOutQuad')
    tick += 4
    t_so, _ = move(frp, tr, tick, last_pl['approach'], tick + 4, so, so)
    tr.tool('tool_weld', t_so, EXT, None)
    tr.tool('tool_weld', t_so + 3, 0.0, 'easeInQuad')
    t_back = t_so + 1
    tr.turn.append((t_back, theta, None))
    tr.turn.append((t_back + turn_ticks(theta), 0.0, 'easeInOutSine'))
    tr.fx(t_back, 'sound', SND('turntable', 0.6, 1.0), 'deck')
    length = max(44, t_back + turn_ticks(theta))
    tr.at(length, so, None)
    return tr, length, notes, [t_c, pts[-1]]


def press_line(fr, tr, t0, p0, t1, p1, ease):
    """The tool's tip straight along a line from p0's tip to p1's (a press, a strike or a run along a seam), else with
    the middle backed off 1.5 px along the tool, else a plain key. True if the sweep is clean."""
    n0 = len(tr.poses)
    if cart_path(tr, t0, p0, t1, p1, (0.0, 0.0), ease) and not sweep(fr, tr, t0, t1):
        return True
    del tr.poses[n0:]
    if t1 - t0 >= 2:
        tip = (tip_of(p0) + tip_of(p1)) / 2.0
        d = tool_dir(p0) + tool_dir(p1)
        d = d / np.linalg.norm(d)
        mid, err, short = solve(tip - d * 1.5, d, p0['yaw'], e=(p0.get('e', 0.0) + p1.get('e', 0.0)) / 2.0,
                                u=(p0.get('u', 0.0) + p1.get('u', 0.0)) / 2.0)
        if short < 0.2:
            tr.at(t0 + (t1 - t0) // 2, mid, 'easeInOutSine')
            tr.at(t1, p1, 'easeInOutSine')
            if not sweep(fr, tr, t0, t1):
                return True
            del tr.poses[n0:]
    tr.at(t1, p1, ease)
    return not sweep(fr, tr, t0, t1)


class ParkedFrame:
    """The frame parked on the pad: its build clip's last pose at every tick (for the sweep's frame clock)."""

    def __init__(self, fr):
        self.fr = fr

    def hits(self, parts, t, theta):
        return self.fr.hits(parts, self.fr.length, theta)

    def solid(self, t):
        return self.fr.solid(self.fr.length)


# ---------------------------------------------------------------------------------------------------------------------
# Review copies: the frame stood on the turntable (its boxes as axis-aligned stand-ins, rounded out to whole pixels)
# ---------------------------------------------------------------------------------------------------------------------
def standin_elements(fr, t):
    """The frame's boxes at build time t (unturned, on the deck) as whole-pixel axis-aligned boxes."""
    els = []
    for (n, b, f, to, Rw, Tw, Re, oe) in fr.fk.boxes(fr.mats(t)):
        cs = np.array([[x, y, z] for x in (f[0], to[0]) for y in (f[1], to[1]) for z in (f[2], to[2])])
        cs = (Re @ (cs - oe).T).T + oe
        cs = (Rw @ cs.T).T + Tw + DECKV
        lo, hi = np.floor(cs.min(0) + 0.3), np.ceil(cs.max(0) - 0.3)
        hi = np.maximum(hi, lo + 1)
        els.append((n, lo.tolist(), hi.tolist()))
    return els


def add_standin(model, fr, t, gname, visible=True):
    """Adds a group `gname` under the turntable with the frame's stand-in boxes (review copies only)."""
    import uuid as uuidlib
    tt = [g for g in model['groups'] if g['name'] == 'turntable'][0]
    gu = str(uuidlib.uuid5(kit.NS, 'gantry-review/' + gname))
    model['groups'].append({'uuid': gu, 'name': gname, 'origin': [0, 0, 0], 'rotation': [0, 0, 0], 'export': True,
                            'visibility': visible, 'mirror_uv': False, 'reset': False, 'color': 3, 'autouv': 0,
                            'shade': True, 'locked': False})
    kids = []
    fe = [e for e in model['elements'] if e.get('name') == 'wrench_shank'][0]
    ox, oy = fe['uv_offset']
    uv = (ox + 2, oy + 2, ox + 3, oy + 3)          # one pixel of the steel: the frame reads grey-blue
    for i, (n, lo, hi) in enumerate(standin_elements(fr, t)):
        eu = str(uuidlib.uuid5(kit.NS, 'gantry-review/%s/%d' % (gname, i)))
        faces = {f: {'uv': list(uv), 'texture': 0} for f in ('north', 'east', 'south', 'west', 'up', 'down')}
        model['elements'].append({'uuid': eu, 'name': 'si_%s_%d' % (n, i), 'from': lo, 'to': hi, 'origin': lo,
                                  'color': 3, 'type': 'cube', 'box_uv': False, 'faces': faces, 'rescale': False,
                                  'locked': False, 'light_emission': 0, 'render_order': 'default',
                                  'allow_mirror_modeling': True, 'autouv': 0, 'visibility': True})
        kids.append(eu)

    def find(nodes):
        for nd in nodes:
            if isinstance(nd, dict):
                if nd['uuid'] == tt['uuid']:
                    nd['children'].append({'uuid': gu, 'isOpen': False, 'children': kids})
                    return True
                if find(nd['children']):
                    return True
        return False
    find(model['outliner'])
    return gu


def set_locators(model):
    """Every locator this script places (weld, plating, stamp, arc and repair points: root static points), replacing
    any it placed before; the geometry's own locators stay."""
    keep = set(STATIC_LOCATORS)
    gone = {e['uuid'] for e in model['elements'] if e.get('type') == 'locator' and e.get('name') not in keep}
    model['elements'] = [e for e in model['elements'] if e['uuid'] not in gone]
    model['outliner'] = [c for c in model['outliner'] if not (isinstance(c, str) and c in gone)]
    for name, pos in sorted(LOCATORS.items()):
        u = kit.nid('gantry', 'locator', name)
        model['elements'].append({'uuid': u, 'name': name, 'type': 'locator', 'position': pos, 'rotation': [0, 0, 0],
                                  'ignore_inherited_scale': False, 'visibility': True, 'locked': False})
        model['outliner'].append(u)


def write_review(path, clips, extra, standins):
    """A review copy: every clip baked per tick, the review clips added, stand-ins on the turntable."""
    with open(geo.PATH, encoding='utf-8') as fh:
        model = json.load(fh)
    model['animations'] = kit.clips_json(model, list(clips) + list(extra), set(), baked=True)
    for fr, t, gname, vis in standins:
        add_standin(model, fr, t, gname, vis)
    if standins:
        for g in model['groups']:
            if g['name'] == 'standin_contents':
                g['visibility'] = False          # the frame item is gone once the frame stands on the pad
    with open(path, 'w', encoding='utf-8') as fh:
        json.dump(model, fh, indent=1)
    return path


# ---------------------------------------------------------------------------------------------------------------------
def report_sweep(label, bad, theta=None, limit=40):
    print('%s: %d bad half-ticks%s' % (label, len(bad), ', worst %.2f px' % worst(bad) if bad else ''))
    for tau, hs in bad[:limit]:
        print('   tau %5.1f%s: %s' % (tau, '' if theta is None else ' (theta %6.1f)' % theta(tau), hs[:4]))


class Result:
    """What one frame's worker hands back: its clips' tracks, its plan and its sweeps."""


def _frame_task(job):
    name, table, so = job
    LOCATORS.clear()
    ARCS.clear()
    b = Builder(name, table, so).build()
    r = Result()
    for kk in ('name', 'tr', 'length', 'contacts', 'notes', 'so', 'so_bad', 'turn_keys', 'present', 'scan',
               'stamp_tick', 'seq', 'settle'):
        setattr(r, kk, getattr(b, kk))
    r.build_length = b.fr.length
    r.fclips = {kind: frame_clip(b, kind) for kind in ('disassemble', 'repair')}
    r.sweeps = {'build_' + name: (sweep(b.fr, b.tr, 0, b.length),
                                  [b.tr.theta_at(x / 2.0 - LEAD) for x in range(0, 2 * b.length + 1)])}
    for kind, (t2, ln, notes, span) in r.fclips.items():
        r.sweeps['%s_%s' % (kind, name)] = (sweep(ParkedFrame(b.fr), t2, 0, ln),
                                            [turn_angle(t2.turn, x / 2.0) for x in range(0, 2 * ln + 1)])
    # the loaded and charge poses against the frame parked at every 10 degrees (a mecha parked at any yaw)
    r.parked = {label: standoff_hits(b.fr, LOADED, [b.fr.length], range(0, 360, 10), tools)
                for label, tools in (('loaded', {}), ('charge', {'tool_grip': EXT}))}
    r.locators, r.arcs = dict(LOCATORS), dict(ARCS)
    return r


def main():
    import multiprocessing
    ap = argparse.ArgumentParser()
    ap.add_argument('--review', metavar='DIR', help='also write review copies (turntable keyed, frame stand-ins) here')
    ap.add_argument('--tables', metavar='FILE', help='keep the stood-off poses and angle tables in FILE, and read them '
                    'from it when it exists (for trying the build sequencing only: delete it after any change to the '
                    'geometry or the planning of a contact)')
    args = ap.parse_args()

    with multiprocessing.Pool() as pool:
        if args.tables and os.path.exists(args.tables):
            with open(args.tables, 'rb') as fh:
                sos, tables = pickle.load(fh)
        else:
            sos = dict(pool.imap_unordered(_standoff_task, FRAME_NAMES))
            tables = angle_tables(FRAME_NAMES, pool, {n: sos[n][0] for n in FRAME_NAMES})
            if args.tables:
                with open(args.tables, 'wb') as fh:
                    pickle.dump((sos, tables), fh)
        print('angle tables done', flush=True)
        res = {r.name: r for r in pool.imap_unordered(_frame_task, [(n, tables[n], sos[n]) for n in FRAME_NAMES])}
    res = {n: res[n] for n in FRAME_NAMES}
    for r in res.values():
        LOCATORS.update(r.locators)
        ARCS.update(r.arcs)

    clips, review_extra = [], []
    for name, r in res.items():
        clips.append(('build_' + name, r.tr.clip(r.length)))
        review_extra.append(('_review_build_' + name, r.tr.clip(r.length, review=True)))
    shorts = short_clips()
    for nm in SHORT_NAMES:
        clips.append((nm, shorts[nm][1]))
    for name, r in res.items():
        for kind in ('disassemble', 'repair'):
            t2, ln, notes, span = r.fclips[kind]
            clips.append(('%s_%s' % (kind, name), t2.clip(ln)))
            review_extra.append(('_review_%s_%s' % (kind, name), t2.clip(ln, review=True)))

    # ---- the game file: clips and locators ----------------------------------------------------------------------
    kit.write_clips(geo.PATH, clips, code_channels={('turntable', 'rotation'), ('reel', 'rotation')})
    with open(geo.PATH, encoding='utf-8') as fh:
        model = json.load(fh)
    set_locators(model)
    with open(geo.PATH, 'w', encoding='utf-8') as fh:
        json.dump(model, fh, indent=1)

    # ---- the plan -----------------------------------------------------------------------------------------------
    static_locs = {e['name']: rnd(e['position']) for e in model['elements']
                   if e.get('type') == 'locator' and e['name'] in STATIC_LOCATORS and e['name'] != 'tool_tip'}
    plan = {'lead': LEAD, 'frames': {}, 'locators': dict(static_locs, **LOCATORS), 'arcs': ARCS}
    for name, r in res.items():
        td = r.fclips['disassemble'][0]
        trp, lnr = r.fclips['repair'][:2]
        plan['frames'][name] = {
            'length': r.length, 'build_length': r.build_length,
            'turn': [[t, round(a, 3), e] for (t, a, e) in r.turn_keys],
            'present': r.present,
            'contacts': [dict({kk: c[kk] for kk in ('slot', 'seat', 'contact', 'late', 'locator', 'reach')},
                              **({} if c['reach'] else {'arc': c['arc']})) for c in r.contacts],
            'disassemble': 60, 'repair': lnr,
            'repair_turn': [[t, round(a, 3), e] for (t, a, e) in trp.turn],
            'disassemble_turn': [[t, round(a, 3), e] for (t, a, e) in td.turn]}
    with open(PLAN_PATH, 'w', encoding='utf-8', newline='\n') as fh:
        json.dump(plan, fh, indent=1, sort_keys=True)
        fh.write('\n')

    # ---- review copies ------------------------------------------------------------------------------------------
    if args.review:
        os.makedirs(args.review, exist_ok=True)
        for name in res:
            fr = get_frame(name)
            write_review(os.path.join(args.review, 'gantry_review_%s.bbmodel' % name), clips, review_extra,
                         [(fr, fr.slots[-1][2], 'standin_' + name, True)])
        write_review(os.path.join(args.review, 'gantry_review.bbmodel'), clips, review_extra, [])

    # ---- the sweep: every clip, half-tick steps -----------------------------------------------------------------
    print('== collision sweep (arm against the frame and the pedestal, every half tick) ==')
    for name, r in res.items():
        for clip, (bad, thetas) in r.sweeps.items():
            report_sweep(clip, bad, lambda tau, th=thetas: th[min(len(th) - 1, int(round(tau * 2)))])
    for nm in SHORT_NAMES:
        tr = shorts[nm][0]
        bad = sweep(None, tr, 0, max(tr.poses[-1][0], 1))
        report_sweep('%s (against the pedestal)' % nm, bad, None, 3)
    for name, r in res.items():
        for label, bad in r.parked.items():
            print('%s vs parked %s at every 10 degrees: %d angles touch%s' % (
                label, name, len(bad), ', worst %.2f px %s' % (max(x[2] for x in bad), bad[:3]) if bad else ''))

    # ---- the plan, printed ----------------------------------------------------------------------------------------
    print('== plan ==')
    for name, r in res.items():
        print('%s: clip %d, build %d, stood off %s%s' % (
            name, r.length, r.build_length, {kk: round(v, 1) for kk, v in r.so.items()},
            '' if not r.so_bad else ' (touches at %d (t, angle) pairs, worst %.2f px)' % (
                len(r.so_bad), max(x[2] for x in r.so_bad))))
        print('   turn %s; present %s' % ([(t, round(a, 1)) for (t, a, e) in r.turn_keys], r.present))
        for c in r.contacts:
            print('   %-9s seat %3d contact %3d late %2d theta %6.1f %s%s' % (
                c['slot'], c['seat'], c['contact'], c['late'], c['theta'], 'reach' if c['reach'] else 'ARC',
                '' if c['reach'] else ' (%s, %.1f px)' % (c['arc'], c['arc_length'])))
        print('   scan at tau %s, stamp at tau %s' % (r.scan, r.stamp_tick))
        for nt in r.notes:
            print('   note:', nt)
        for kind in ('disassemble', 'repair'):
            t2, ln, notes, span = r.fclips[kind]
            print('   %s: %d ticks, turn %s%s' % (kind, ln, [(t, round(a, 1)) for (t, a, e) in t2.turn],
                                                '' if span is None else ', weld pass tau %d-%d' % tuple(span)))
            for nt in notes:
                print('   note:', nt)
    print('== locators ==')
    for kk, v in sorted(plan['locators'].items()):
        print('  ', kk, v)
    print('== arcs ==')
    for kk, v in sorted(ARCS.items()):
        print('  ', kk, v)


SHORT_NAMES = ('loaded', 'load', 'unload', 'open', 'close', 'open_loaded', 'close_loaded', 'refuse', 'refuse_loaded',
               'charge')

if __name__ == '__main__':
    main()

"""Sweep every bone of the Cerebral Array through its whole range (clip keys plus overshoot) and report any moving cube
passing through another (static cubes, the other bones at rest, the stand-in heads).

    python art/machines/cerebral_array/array_sweep.py

Rotations are right-handed about the bone's pivot (checked against renders: card rotX -90 stands the card up,
gavel rotZ -55 lifts the head). Points are sampled inside each moving cube on a 0.25 px grid with a 0.05 px margin.
Also prints the highest point the gavel reaches (the bone passes the 32 px line while it bangs).
"""
import itertools
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import array_geo as geo  # noqa: E402
kit = geo.kit

m = geo.model()
pivots = {b[0]: b[1] for b in m.bones}
solid = [c for c in m.cubes if not kit.is_hidden(m, c[4]) or kit.is_standin(m, c[4])]
bones = m.bone_names()
ALLOW = {('gavel_handle', 'cheek_f'), ('gavel_handle', 'cheek_b')}     # the handle runs between the cheeks (axle)


def owner(c):
    for a in kit.ancestors(m, c[4]):
        if a in bones:
            return a
    return None


def rot(p, piv, ax, deg):
    r = math.radians(deg)
    q = [p[i] - piv[i] for i in range(3)]
    i, j = [(1, 2), (2, 0), (0, 1)][ax]
    c, s = math.cos(r), math.sin(r)
    q[i], q[j] = c * q[i] - s * q[j], s * q[i] + c * q[j]
    return [q[n] + piv[n] for n in range(3)]


def inside(p, c, eps=0.05):
    f, t = c[1], c[2]
    return all(f[i] + eps < p[i] < t[i] - eps for i in range(3))


def samples(c, step=0.25):
    f, t = c[1], c[2]
    axes = []
    for i in range(3):
        n = max(1, int(round((t[i] - f[i]) / step)))
        axes.append([f[i] + (k + 0.5) * (t[i] - f[i]) / n for k in range(n)])
    return list(itertools.product(*axes))


POSES = {'gavel': [(2, -d) for d in range(0, 62)]}             # 0..61 up (the bounces lift too)
for i in range(1, 10):
    POSES['card_%d' % i] = [(0, -d) for d in range(0, 98)]       # 0..97 up (easeOutBack overshoot; the bounce lifts)

hits, top = {}, -1e9
for bone, poses in POSES.items():
    mine = [c for c in solid if owner(c) == bone]
    others = [c for c in solid if owner(c) != bone]
    piv = pivots[bone]
    pts = [(c[0], p) for c in mine for p in samples(c)]
    corners = [list(v) for c in mine for v in itertools.product(*[(c[1][n], c[2][n]) for n in range(3)])]
    for ax, val in poses:
        if bone == 'gavel' and val <= 0:
            top = max([top] + [rot(v, piv, ax, val)[1] for v in corners])
        for name, p in pts:
            q = rot(p, piv, ax, val)
            for o in others:
                if (name, o[0]) in ALLOW:
                    continue
                if inside(q, o):
                    hits.setdefault((bone, name, o[0]), []).append(val)
for (bone, a, b), vals in sorted(hits.items()):
    print('HIT %s: %s passes through %s at %s' % (bone, a, b, sorted(set(vals))[:6]))
print('sweep: %d bones, %d hits; the gavel reaches y %.2f' % (len(POSES), len(hits), top))

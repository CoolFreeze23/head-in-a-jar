"""Sweep every bone of the Gene Splicer through its whole range and report any moving cube passing through another.

    python art/machines/gene_splicer/splicer_sweep.py

Samples points inside each moving cube (0.25 px grid, 0.05 px margin) at every pose step and tests them against every
static cube and every other bone's cubes at rest. Intended overlaps are listed in ALLOW (the needle bar runs inside
the head and stabs the strand; the axle enters the pillar; the cassette stands in its deck and slot).
"""
import itertools
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
import kit  # noqa: E402
import splicer_geo as geo  # noqa: E402

m, _, _ = geo.model()
pivots = {b[0]: b[1] for b in m.bones}
drawn = [c for c in m.cubes if not kit.is_standin(m, c[4]) and not kit.is_hidden(m, c[4])]
bones = m.bone_names()
ALLOW = {('needle_bar', 'head'), ('needle_bar', 'strand'), ('wheel_axle', 'pillar'),
         ('cassette_body', 'deck'), ('cassette_body', 'bed')}


def owner(c):
    for a in kit.ancestors(m, c[4]):
        if a in bones:
            return a
    return None


def rot(p, piv, ax, deg):
    r = math.radians(deg)
    q = [p[i] - piv[i] for i in range(3)]
    i, j = [(1, 2), (0, 2), (0, 1)][ax]
    c, s = math.cos(r), math.sin(r)
    a, b = q[i], q[j]
    if ax == 1:
        q[i], q[j] = c * a + s * b, -s * a + c * b
    else:
        q[i], q[j] = c * a - s * b, s * a + c * b
    return [q[k] + piv[k] for k in range(3)]


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


POSES = {
    'wheel': [('rot', 0, d) for d in range(0, 360, 5)],
    'needle': [('pos', 1, v / 4.0) for v in range(-24, 7)],
    'cassette': [('pos', 1, v / 4.0) for v in range(-2, 27)],
    'drawer': [('pos', 2, -v / 4.0) for v in range(0, 15)],
    'flag': [('rot', 0, d) for d in range(-6, 97, 3)],
}
for i in (1, 2, 3):
    POSES['spool_%d' % i] = [('rotpos', 1, (d, h / 2.0)) for d in range(0, 91, 5) for h in range(0, 6)]

hits = {}
for bone, poses in POSES.items():
    mine = [c for c in drawn if owner(c) == bone]
    others = [c for c in drawn if owner(c) != bone]
    piv = pivots[bone]
    pts = [(c[0], p) for c in mine for p in samples(c)]
    for kind, ax, val in poses:
        for name, p in pts:
            if kind == 'rot':
                q = rot(p, piv, ax, val)
            elif kind == 'pos':
                q = list(p)
                q[ax] += val
            else:
                q = rot(p, piv, 1, val[0])
                q[1] += val[1]
            for o in others:
                if (name, o[0]) in ALLOW:
                    continue
                if inside(q, o):
                    hits.setdefault((bone, name, o[0]), []).append(val)
for (bone, a, b), vals in sorted(hits.items()):
    print('HIT %s: %s passes through %s at %s' % (bone, a, b, sorted(set(map(str, vals)))[:8]))
print('sweep: %d bones, %d hits' % (len(POSES), len(hits)))

"""Sweep every bone of the DNA Sequencer through its whole range (clips and code) and report any moving cube passing
through another (static cubes, the other bones at rest, the fraction's stand-in).

    python art/machines/dna_sequencer/sequencer_sweep.py

Samples points inside each moving cube (0.25 px grid, 0.05 px margin) at every pose step. Intended overlaps (ALLOW):
the call flag lives in a slot down in the body and passes up through the display's floor and top; the receipt tape's hidden
length runs inside the display; a pressed key goes 1 px into the ledge; the drawer slides in the bench band.
"""
import itertools
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import sequencer_geo as geo  # noqa: E402
kit = geo.kit

m, _ = geo.model()
pivots = {b[0]: b[1] for b in m.bones}
drawn = [c for c in m.cubes if not kit.is_hidden(m, c[4])]   # stand-ins included as obstacles
bones = m.bone_names()
ALLOW = set()
for tab in ('flag_plate', 'flag_banner'):
    ALLOW |= {(tab, 'body'), (tab, 'disp_bottom'), (tab, 'disp_top')}
for o in ('disp_bottom', 'disp_back', 'disp_top', 'body'):
    ALLOW.add(('receipt_tape', o))
for b, x0, mat in geo.KEYS:
    ALLOW |= {(b + '_cap', 'keybed'), (b + '_stem', 'keybed')}


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


def pose(kind, val, p, piv):
    if kind == 'rot':
        ax, deg = val
        return rot(p, piv, ax, deg)
    if kind == 'pos':
        ax, d = val
        q = list(p)
        q[ax] += d
        return q
    if kind == 'tear':      # receipt: posY first (code), then rotZ about the pivot carried up with it
        dy, deg = val
        q = [p[0], p[1] + dy, p[2]]
        return rot(q, [piv[0], piv[1] + dy, piv[2]], 2, deg)
    raise ValueError(kind)


POSES = {
    'crank': [('rot', (0, d)) for d in range(-20, 380, 3)],
    'flag': [('pos', (1, v / 4.0)) for v in range(-2, 47)],
    'receipt': [('pos', (1, v / 4.0)) for v in range(0, 25)] + [('tear', (6, d)) for d in range(-6, 38, 2)],
    'drawer': [('pos', (2, v / 4.0)) for v in range(-20, 2)],
}
for b, x0, mat in geo.KEYS:
    POSES[b] = [('pos', (1, v / 4.0)) for v in range(-4, 5)]

hits = {}
for bone, poses in POSES.items():
    mine = [c for c in drawn if owner(c) == bone]
    others = [c for c in drawn if owner(c) != bone]
    piv = pivots[bone]
    pts = [(c[0], p) for c in mine for p in samples(c)]
    for kind, val in poses:
        for name, p in pts:
            q = pose(kind, val, p, piv)
            for o in others:
                if (name, o[0]) in ALLOW:
                    continue
                if inside(q, o):
                    hits.setdefault((bone, name, o[0]), []).append(val)
            if bone in ('crank', 'receipt') and (abs(q[0]) > 8.001 or abs(q[2]) > 8.001):
                hits.setdefault((bone, name, 'outside the block'), []).append(val)
for (bone, a, b), vals in sorted(hits.items()):
    print('HIT %s: %s passes through %s at %s' % (bone, a, b, sorted(set(map(str, vals)))[:8]))
print('sweep: %d bones, %d hits' % (len(POSES), len(hits)))
# the crank's reach (for the render box and the ten-block note)
pts = [p for c in drawn if owner(c) == 'crank' for p in samples(c)]
ys = [rot(p, pivots['crank'], 0, d)[1] for d in range(0, 360, 5) for p in pts]
zs = [rot(p, pivots['crank'], 0, d)[2] for d in range(0, 360, 5) for p in pts]
print('crank reach: y %.2f..%.2f, z %.2f..%.2f' % (min(ys), max(ys), min(zs), max(zs)))

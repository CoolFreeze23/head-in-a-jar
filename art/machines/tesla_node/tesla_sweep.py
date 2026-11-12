"""Sweep the Tesla Node's two bones through their whole ranges and report any moving cube passing through another.

    python art/machines/tesla_node/tesla_sweep.py

Torus: rotY every 5 degrees, posY -0.5..3.5 by 0.25, posX -0.5..0.5 by 0.25 (every key value and its overshoot). Pointer
(rides the torus): local rotY every 5 degrees, at every torus pose. Points on a 0.25 px grid inside each moving cube
(0.05 px margin) are tested against every static cube and, for the pointer, against the torus's own cubes. Also prints
the posed bounds (the model part of the render box).
"""
import itertools
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
import kit  # noqa: E402
import tesla_geo as geo  # noqa: E402

m = geo.model()
drawn = [c for c in m.cubes if not kit.is_standin(m, c[4]) and not kit.is_hidden(m, c[4])]
static = [c for c in drawn if kit.is_static(m, c[4])]
torus = [c for c in drawn if c[4] == 'torus']
pointer = [c for c in drawn if c[4] == 'pointer']


def roty(p, piv, deg):
    r = math.radians(deg)
    x, z = p[0] - piv[0], p[2] - piv[2]
    return [x * math.cos(r) + z * math.sin(r) + piv[0], p[1], -x * math.sin(r) + z * math.cos(r) + piv[2]]


def inside(p, c, eps=0.05):
    f, t = c[1], c[2]
    return all(f[i] + eps < p[i] < t[i] - eps for i in range(3))


def samples(c, step=0.5):
    f, t = c[1], c[2]
    axes = []
    for i in range(3):
        n = max(1, int(round((t[i] - f[i]) / step)))
        axes.append([f[i] + (j + 0.5) * (t[i] - f[i]) / n for j in range(n)])
    return list(itertools.product(*axes))


TP, PP = [0, 20, 0], [0, 22, 0]
hits = {}
lo, hi = [1e9] * 3, [-1e9] * 3
tpts = [(c[0], p) for c in torus for p in samples(c)]
ppts = [(c[0], p) for c in pointer for p in samples(c)]
corners = [(c[0], [c[1][0] if i & 1 else c[2][0], c[1][1] if i & 2 else c[2][1], c[1][2] if i & 4 else c[2][2]])
           for c in pointer + torus for i in range(8)]


def near(q, o, pad=0.1):
    return all(o[1][i] - pad < q[i] < o[2][i] + pad for i in range(3))


LIFTS = [x / 4.0 for x in range(-2, 15)]
SHAKES = (-0.5, -0.25, 0, 0.25, 0.5)
# the pointer's world yaw is the torus's yaw plus its own, so each moving part is swept by its world yaw
for name_set, pts, piv0 in (('torus', tpts, None), ('pointer', ppts, PP)):
    for yaw in range(0, 360, 5):
        rot = [(n, roty(p, PP if piv0 else TP, yaw)) for n, p in pts]
        for dy in LIFTS:
            for dx in SHAKES:
                for n, q in rot:
                    q2 = [q[0] + dx, q[1] + dy, q[2]]
                    for o in static:
                        if inside(q2, o):
                            hits.setdefault((name_set, n, o[0]), set()).add((yaw, dy, dx))
for yaw in range(0, 360, 5):
    for n, p in corners:
        q = roty(p, PP, yaw)
        for dy in (LIFTS[0], LIFTS[-1]):
            for dx in (SHAKES[0], SHAKES[-1]):
                q2 = [q[0] + dx, q[1] + dy, q[2]]
                for i in range(3):
                    lo[i], hi[i] = min(lo[i], q2[i]), max(hi[i], q2[i])
# the pointer against its own carrier: in the torus's frame the pointer only yaws
for py in range(0, 360, 5):
    for name, p in ppts:
        q = roty(p, PP, py)
        for o in torus:
            if inside(q, o):
                hits.setdefault(('pointer', name, o[0]), set()).add(('local', py))
for (bone, a, b), vals in sorted(hits.items()):
    print('HIT %s: %s passes through %s at %s' % (bone, a, b, sorted(map(str, vals))[:6]))
print('sweep: torus and pointer, 72 yaws x 17 lifts x 5 shakes each, pointer on the torus 72 yaws: %d hits' % len(hits))
print('moving parts bounds (cube corners, model px): x %.2f..%.2f, y %.2f..%.2f, z %.2f..%.2f' % (
    lo[0], hi[0], lo[1], hi[1], lo[2], hi[2]))

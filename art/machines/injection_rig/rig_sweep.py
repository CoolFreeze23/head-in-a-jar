"""Sweep the Injection Rig's three bones through every clip (at quarter ticks) and the code yaw at several aims, and
report any moving cube passing into a static cube (or the cassette stand-in).

    python art/machines/injection_rig/rig_sweep.py

Poses come from rig_anim.pose_at (the show and its tails with the code yaw, and the cancel from several moments) and
from the clips' own keys (refuse, prime, unprime at rest yaw). Points are sampled inside each moving cube (0.25 px grid,
0.05 px margin). Intended contact: the rod inside the barrel and flange (one syringe), the needle into the sheath at
rest, the barrel sliding along the guide.
"""
import itertools
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import rig_anim as A  # noqa: E402
import kit  # noqa: E402

m = A.geo.model()
drawn = [c for c in m.cubes if not kit.is_hidden(m, c[4])]           # stand-ins included (the cassette)
owner = {}
for c in drawn:
    for a in kit.ancestors(m, c[4]):
        if a in ('nozzle_arm', 'syringe', 'plunger'):
            owner[c[0]] = a
            break
moving = [c for c in drawn if c[0] in owner]
static = [c for c in drawn if c[0] not in owner]
ALLOW = {('needle', 'sheath')}
PIV = A.geo.ARM_PIVOT


def samples(c, step=0.25):
    f, t = c[1], c[2]
    axes = []
    for i in range(3):
        n = max(1, int(round((t[i] - f[i]) / step)))
        axes.append([f[i] + (j + 0.5) * (t[i] - f[i]) / n for j in range(n)])
    return list(itertools.product(*axes))


def inside(p, c, eps=0.05):
    return all(c[1][i] + eps < p[i] < c[2][i] - eps for i in range(3))


PTS = [(c[0], owner[c[0]], samples(c, 0.5), c) for c in moving]


def corners(c):
    return list(itertools.product(*[(c[1][i], c[2][i]) for i in range(3)]))


def near(qs, o, pad=0.1):
    lo = [min(q[i] for q in qs) for i in range(3)]
    hi = [max(q[i] for q in qs) for i in range(3)]
    return all(lo[i] < o[2][i] - pad and hi[i] > o[1][i] + pad for i in range(3))


SPIV = [b[1] for b in m.bones if b[0] == 'syringe'][0]


def place(p, bone, yaw, sp, sr, py):
    """Arm frame: the plunger's offset, then the syringe's position and its rotZ about its pivot; then the arm's yaw
    about the neck (GeckoLib: v -> pos + pivot + R (v - pivot), children inside their parents)."""
    x, y, z = p
    if bone == 'plunger':
        y += py
    if bone in ('syringe', 'plunger'):
        a = math.radians(sr)
        dx, dy = x - SPIV[0], y - SPIV[1]
        x = sp[0] + SPIV[0] + math.cos(a) * dx - math.sin(a) * dy
        y = sp[1] + SPIV[1] + math.sin(a) * dx + math.cos(a) * dy
        z = z + sp[2]
    r = math.radians(yaw)
    dx, dz = x - PIV[0], z - PIV[2]
    return (PIV[0] + math.cos(r) * dx + math.sin(r) * dz, y, PIV[2] - math.sin(r) * dx + math.cos(r) * dz)


def poses():
    for phi in (30, 45, 60, 90, 120, 150, 180):
        for res in ('accepted', 'rejected', 'mutated'):
            for q in range(0, 4 * 47 + 1):
                t = q / 4.0
                yield ('%s phi %d' % (res, phi), t) + A.pose_at(t, phi, res)
        for tc in (2, 5, 9, 14, 20, 24, 26, 29):
            for q in range(4 * tc, 4 * (tc + 14) + 1):
                t = q / 4.0
                yield ('cancel@%d phi %d' % (tc, phi), t) + A.pose_at(t, phi, tc=tc)
    for name in ('refuse', 'prime', 'unprime'):
        clip = dict(A.CLIPS)[name]
        for q in range(0, 4 * clip['length'] + 1):
            t = q / 4.0
            sp, sr, py = A._clip_pose(clip, t)
            yield (name, t, 0.0, sp, sr, py)
            if name == 'refuse':
                yield (name + ' loaded', t, 0.0, sp, sr, A.DRAW)


hits = {}
count = 0
ext = [0, 0, 99, -99]   # max radius, max y, min y of any moving point
for tag, t, yaw, sp, sr, py in poses():
    count += 1
    for name, bone, pts, c in PTS:
        cq = [place(p, bone, yaw, sp, sr, py) for p in corners(c)]
        for q in cq:
            ext[0] = max(ext[0], math.hypot(q[0], q[2])); ext[1] = max(ext[1], q[1]); ext[2] = min(ext[2], q[1])
        cand = [o for o in static if (name, o[0]) not in ALLOW and near(cq, o)]
        if not cand:
            continue
        for p in pts:
            q = place(p, bone, yaw, sp, sr, py)
            for o in cand:
                if inside(q, o):
                    hits.setdefault((tag, name, o[0]), []).append(t)
                    break
for (tag, a, b), ts in sorted(hits.items()):
    print('HIT %-22s %s in %s at t %s' % (tag, a, b, sorted(set(ts))[:6]))
print('sweep: %d poses, %d moving cubes, %d static, %d hits' % (count, len(moving), len(static), len(hits)))
# the closest approach of the syringe to the body's corners while it swings (clearance)
best = 99
for phi in range(0, 181, 3):
    for name, bone, pts, c in PTS:
        if bone == 'nozzle_arm':
            continue
        for p in pts:
            q = place(p, bone, phi, [0, A.LIFT, 0], 0, A.DRAW)
            if q[1] < 19:
                best = min(best, math.hypot(q[0], q[2]))
print('lifted syringe below the body top: nearest sample point to the axis %.2f px (body corners at 6.71)' % best)
print('moving parts reach: radius %.2f px from the neck, y %.2f..%.2f' % (ext[0], ext[2], ext[1]))

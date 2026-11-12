"""Jarred Head block (a reception-desk exhibit): geometry. No bones; the head turns by code.

    python art/machines/jarred_head/jarred_geo.py      # writes jarred_head.bbmodel (+ review copy with the jar)

The full Life Support Jar at 1:1 (heads at work are true to scale), drawn by the game's own jar code at an anchor, standing on
a brass display base with a dark kick under it. The base's front carries an enamel nameplate and a brass push bell (the
bell rings when trading opens). Faces -Z; +X is the machine's right (the viewer's left).

What 1:1 does to the block (the jar's sizes from JarRenderer.WIDE, measured for the Cradle and the Dock):
    base 14.3 sq, 0-1.1; glass 13 sq, 1.1-13.2; lid 14.3 sq, 13.2-14.3; valve 3.9 sq, 12.65-15.95 above its base.
- The jar's base and lid are 14.3 px square: 0.85 px from each side of the cell. Nothing can stand beside it on top of
  the base, so the bell and the nameplate are on the base's FRONT face, not on its top.
- On a 6 px plinth (y 1-7) the jar's lid tops out at 21.3 and its valve at 22.95: one block, drawn 7 px over its cell
  (one block, no top cell), like a vanilla head on a block.
- The plinth is 16 wide and 14 deep (z -6..8): the plaque and the bell are mounted on its front in two 1 px layers and
  end exactly at the cell's front (z -8); the jar overhangs the plinth's front by 1.15 px (a shadow line under it).
"""
import base64
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
sys.path.insert(0, os.path.join(HERE, '..', 'neural_socket'))
try:
    import kit  # noqa: E402
except ImportError:
    sys.path.insert(0, 'build/machines-review/scratch-batch5')
    import kit  # noqa: E402
import socket_geo as SG  # noqa: E402  (the real-size jar stand-in, the clear-glass swatch, the injector)

PATH = os.path.join(HERE, 'jarred_head.bbmodel')
REVIEW = 'build/machines-review/jarred_head_review.bbmodel'
BASE_TOP = 7
JAR_AT = (0, BASE_TOP, 0)     # THE anchor: the jar's base centre, facing -Z, scale 1
HEAD_PIVOT = (0, BASE_TOP + 5.5, 0)   # the head's centre inside the WIDE jar: the code's yaw (and nod) pivot


def P(m, name, f, t, mat, part, parent='static', **kw):
    return m.box(name, f, t, mat, parent, part=part, **kw)


def model():
    m = kit.Model('jarred_head', budget=0)
    # one dark kick inset 1 px under one brass plinth (a slot through the base would make a pallet of it)
    m.box('kick', [-7, 0, -5], [7, 1, 7], 'rubber')
    P(m, 'plinth', [-8, 1, -6], [8, BASE_TOP, 8], 'brass', 'plinth')
    # the front: a mounted plaque (enamel nameplate on a dark backing that shows a 1 px rim) at the viewer's right,
    # and the push bell (a brass dome on a dark plate) at the viewer's left; both inside the cell (front at z -8)
    P(m, 'plaque_back', [-7, 2, -7], [2, 6, -6], 'dsteel', 'plaque')
    P(m, 'nameplate', [-6, 3, -8], [1, 5, -7], 'enamel', 'nameplate')
    P(m, 'bell_plate', [3, 2, -7], [7, 6, -6], 'dsteel', 'bell')
    P(m, 'bell_dome', [4, 3, -8], [6, 5, -7], 'brass', 'bell_dome')
    # locators: the bell's ding, the head's eyes (a particle point for the nod)
    m.locator('bell', [5, 4, -8])
    m.locator('head', list(HEAD_PIVOT))
    # the stand-in swatches the injected jar maps onto (hidden, never exported)
    m.group('standin_mats', None, hidden=True)
    for i, (mat, n) in enumerate((('steel', 2), ('stand', 2), ('glass', 8), ('brass', 2), ('saline', 2),
                                  ('lens_wait', 2))):
        m.box('si_mat_%s' % mat, [20 + 10 * i, 0, 20], [20 + 10 * i + n, n, 20 + n], mat, 'standin_mats')
    m.shape('lower', (0, 0, 2, 16, 7, 16))
    m.shape('lower', (0.85, 7, 0.85, 15.15, 16, 15.15))   # the jar's bounds (the shape grows to the jar)
    return m


def write(path, with_jar):
    m = model()
    m.write(path)
    with open(path, encoding='utf-8') as fh:
        d = json.load(fh)
    png = os.path.splitext(path)[0] + '_placeholder.png'
    SG.clear_glass(d, png)
    with open(png, 'rb') as fh:
        d['textures'][0]['source'] = 'data:image/png;base64,' + base64.b64encode(fh.read()).decode()
    if with_jar:
        saved = SG.STANDINS
        SG.STANDINS = [('standin_jar', lambda: SG.jar_boxes(False, JAR_AT), 0, JAR_AT, True)]
        SG.inject(d, m)
        SG.STANDINS = saved
    with open(path, 'w', encoding='utf-8') as fh:
        json.dump(d, fh, indent=1)
    return m


if __name__ == '__main__':
    mm = write(PATH, with_jar=True)
    write(REVIEW, with_jar=True)
    print(kit.shapes_java(mm))

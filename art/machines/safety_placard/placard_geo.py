"""Safety Placard (a framed enamel notice on the wall): geometry. Static: no bones, no block entity.

    python art/machines/safety_placard/placard_geo.py           # writes safety_placard.bbmodel and the review copies
    python art/machines/safety_placard/placard_text.py OUT.png  # the twelve notices as they set in the text field

A wall piece: its back against the wall at z 8, its face -Z. A brass frame the size of the block's face, 1 px wide and
2 px deep, on a dark steel backplate set 1 px inside its outline, and inside the frame, 1 px clear of it all round, the enamel board,
1 px back from the frame's front. The board's front face carries the notice: a texture `#notice` of its own at 4x
density, 48 x 48 texels on 12 x 12 block px (placard_text.py has the layout: the yellow NOTICE header with the N&S
monogram, a black rule, and the text field, 44 x 35 texels: 11 characters a line, 6 lines, the house 3x5 capitals).
The twelve notices are twelve `#notice` textures; the blockstate `notice` 0-11 picks one (with `facing`).
"""
import base64
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
try:
    import kit  # noqa: E402
except ImportError:
    sys.path.insert(0, 'build/machines-review/scratch-batch5')
    import kit  # noqa: E402
import placard_text as PT  # noqa: E402

PATH = os.path.join(HERE, 'safety_placard.bbmodel')
REVIEW = 'build/machines-review/safety_placard_n%d.bbmodel'
BOARD = (-6, 2, 6, 14)        # x0, y0, x1, y1: the notice face (12 x 12 px)


def P(m, name, f, t, mat, part, parent='static', **kw):
    return m.box(name, f, t, mat, parent, part=part, **kw)


def model():
    m = kit.Model('safety_placard', budget=0)
    P(m, 'backplate', [-7, 1, 7], [7, 15, 8], 'dsteel', 'backplate')   # 1 px inside the frame's outline: no flush edge
    # the frame: four brass strips, 1 px wide, z 5..8 (from the wall, 2 px proud of the plate: a shallow box)
    P(m, 'frame_t', [-8, 15, 5], [8, 16, 8], 'brass', 'frame')
    P(m, 'frame_b', [-8, 0, 5], [8, 1, 8], 'brass', 'frame')
    P(m, 'frame_l', [-8, 1, 5], [-7, 15, 8], 'brass', 'frame')
    P(m, 'frame_r', [7, 1, 5], [8, 15, 8], 'brass', 'frame')
    # the board, 1 px clear of the frame all round (the dark gap), its face 1 px back from the frame's front
    x0, y0, x1, y1 = BOARD
    P(m, 'board', [x0, y0, 6], [x1, y1, 7], 'enamel', 'board')
    m.locator('notice', [0, 8, 6])
    m.shape('lower', (0, 0, 13, 16, 16, 16))
    return m


def notice_review(path, n):
    """A review copy whose board face shows notice n at its 4x density (a second texture on the board's front)."""
    with open(path, encoding='utf-8') as fh:
        d = json.load(fh)
    text = dict(PT.notices())[n]
    tmp = os.path.join('build/machines-review/scratch-batch5', 'notice_%d.png' % n)
    PT.draw(text, scale=1, n=n).save(tmp)
    with open(tmp, 'rb') as fh:
        src = 'data:image/png;base64,' + base64.b64encode(fh.read()).decode()
    t0 = d['textures'][0]
    t1 = dict(t0)
    t1.update({'uuid': kit.nid('safety_placard', 'texture', 'notice'), 'name': 'notice_%d.png' % n, 'id': '1',
               'source': src, 'width': PT.W_TEX, 'height': PT.H_TEX, 'uv_width': PT.W_TEX, 'uv_height': PT.H_TEX,
               'path': '', 'relative_path': ''})
    d['textures'].append(t1)
    for e in d['elements']:
        if e['name'] == 'board':
            e['box_uv'] = False
            for k_, f in e['faces'].items():
                if k_ == 'north':
                    e['faces'][k_] = {'uv': [0, 0, PT.W_TEX, PT.H_TEX], 'texture': 1}
    out = REVIEW % n
    with open(out, 'w', encoding='utf-8') as fh:
        json.dump(d, fh, indent=1)
    return out


if __name__ == '__main__':
    mm = model()
    mm.write(PATH)
    print(kit.shapes_java(mm))
    for n in (2, 6, 11):
        print(notice_review(PATH, n))

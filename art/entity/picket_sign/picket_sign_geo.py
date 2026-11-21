"""Picket signs: what striking staff carry. Three baked models a slogan, written by the data generator from
`java_models()` (it never edits them); drawn by the strike's client layer at the `sign` frame of the species' staff fit
(art/entity/lanyard/fit_table.py) or, for a secondee, taped to its jar.

    python art/entity/picket_sign/picket_sign_geo.py          # prints the models' names and checks them

    picket_sign_<n>    the held sign: a 12 x 8 cardboard board on a 1 x 1 stick 20 px long. The stick is the origin
                       (the centre of its foot): the hand closes round it 2 px up; the board stands on it from 12 px up
                       to 20, 1 px in front of it (stapled to the stick's front), so the stick shows above the hand
                       for 10 px and runs up behind the board.
    picket_board_<n>   the same board without the stick, standing on its bottom edge (the origin the centre of that
                       edge): strapped upright on a back, its faces to the creature's sides.
    picket_card_<n>    a small card, 6 x 4, standing on its bottom edge: strapped on small creatures, and taped to a
                       secondee's jar.

Both faces of the board and the card show the slogan (`headinajar:block/picket/sign_<n>`, the 48 x 32 lettering of
picket_text.py stitched into the block atlas for the language in use), so it reads from either side, the right way
round from outside (vanilla face UVs). The board's slogan is at 4 texels a pixel, as the Safety Placard's notice is; the
card's at 8 (it is the same picture, smaller: a note, not a sign to read across a field). Edges take the cardboard's cut
rim from the slogan picture's own border; the stick is stripped spruce by name (it ships no texture and follows the
player's resource pack, as the crate's posts do).

Java block space: x, z 0..16 centred on 8 (the origin is (8, 0, 8)), y up from the origin. Faces of the board: north
(-z) is the front, as a creature's model front is -z. Every element is a whole number of pixels in size (the 1 px stick
is centred on the origin, so it sits on the half pixel).
"""
import json
import sys

SLOGANS = 6
STICK_TEX = 'minecraft:block/stripped_spruce_log'
# the slogan sprite's rim (texel row/column 0 of 48 x 32, in block UV units 0..16): the cut edge of the card
RIM_U = 16 / 48
RIM_V = 16 / 32


def _faces(w, h, d, slogan_faces=('north', 'south')):
    """Faces of a w x h x d board: the slogan whole on north and south, the rim on the edges."""
    out = {}
    for f in ('north', 'south', 'east', 'west', 'up', 'down'):
        if f in slogan_faces:
            out[f] = {'uv': [0, 0, 16, 16], 'texture': '#slogan'}
        elif f in ('east', 'west'):
            out[f] = {'uv': [0, 0, RIM_U * d, 16], 'texture': '#slogan'}
        else:
            out[f] = {'uv': [0, 0, 16, RIM_V * d], 'texture': '#slogan'}
    return out


def _el(name, frm, to, faces):
    return {'name': name, 'from': [frm[0] + 8, frm[1], frm[2] + 8], 'to': [to[0] + 8, to[1], to[2] + 8], 'faces': faces}


def stick(y0, y1):
    faces = {f: {'uv': [7, 0, 8, y1 - y0], 'texture': '#stick'} for f in ('north', 'south', 'east', 'west')}
    faces['up'] = {'uv': [7, 7, 8, 8], 'texture': '#stick'}
    faces['down'] = {'uv': [7, 7, 8, 8], 'texture': '#stick'}
    return _el('stick', (-0.5, y0, -0.5), (0.5, y1, 0.5), faces)


def board(w, h, y0, z0):
    return _el('board', (-w / 2, y0, z0), (w / 2, y0 + h, z0 + 1), _faces(w, h, 1))


def model(elements, n, with_stick):
    tex = {'slogan': 'headinajar:block/picket/sign_%d' % n, 'particle': 'headinajar:block/picket/sign_%d' % n}
    if with_stick:
        tex['stick'] = STICK_TEX
    return {'textures': tex, 'elements': elements}


SIGN = {'stick': (0, 20), 'board': (12, 8, 12, -1.5)}     # stick y0..y1; board w, h, bottom y, front z
BOARD = (12, 8, 0, -0.5)
CARD = (6, 4, 0, -0.5)


def java_models():
    out = {}
    base = 'assets/headinajar/models/entity/'
    for n in range(SLOGANS):
        w, h, y0, z0 = SIGN['board']
        out[base + 'picket_sign_%d.json' % n] = model([stick(*SIGN['stick']), board(w, h, y0, z0)], n, True)
        out[base + 'picket_board_%d.json' % n] = model([board(*BOARD)], n, False)
        out[base + 'picket_card_%d.json' % n] = model([board(*CARD)], n, False)
    return out


def check():
    bad = 0
    for path, m in java_models().items():
        for el in m['elements']:
            size = [b - a for a, b in zip(el['from'], el['to'])]
            if any(v != int(v) or v <= 0 for v in size) or not all(-16 <= v <= 32 for v in el['from'] + el['to']):
                print('not a whole-pixel size, or out of range:', path, el['name'], el['from'], el['to'])
                bad += 1
    return bad


if __name__ == '__main__':
    bad = check()
    for p in sorted(java_models()):
        print(p)
    if '--json' in sys.argv:
        print(json.dumps(java_models()['assets/headinajar/models/entity/picket_sign_0.json'], indent=1))
    sys.exit(1 if bad else 0)

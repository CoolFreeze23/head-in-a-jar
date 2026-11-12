"""Body Printer (the coin-operated photo booth): the painted sheet and its saline gauge, every pixel from palette keys.

    python -I art/machines/body_printer/paint/paint_printer.py                # writes the textures into the assets
    python -I art/machines/body_printer/paint/paint_printer.py --preview DIR  # also DIR/printer_rest.png, _work.png
Then: python -I tools/tex/tex.py adopt block/machine/body_printer.png block/machine/body_printer_saline_0.png ...

Writes, under textures/block/machine/:
  body_printer.png            128x240, the baked booth and the three bones (door, ring = the print bar, carriage)
  body_printer_saline_0..4    the saline sight face (element 1x10x2), one per `saline` level, box-UV at (0, 0)

The value plan (110 px first):
  LIGHT  the enamel booth (K2 body faces, e2 field, e3 lip, belt seam), the roof
  DARK   the booth's inside behind the clear door (the plum curtain f0-f2 and the r0 back wall), the dark corner post,
         the vertical sign band on the right side
  ACCENT the sign: the yellow marquee with a black camera pictogram across its 10x4 front field (4 rows hold no
         word that reads: PHOTO needs 19 px, BODY 15), and down the right side a dark band lettered PHOTO in yellow
         3x4 capitals; the brass print bar and the copper print head
         (the moving read); the brass door frame round cutout clear glass (a frame and one glint, nothing else)
Story marks, once each: the coin plate with its slot above the photo slot; a strip of four tiny portraits on the
photo card; the maker's plate on the back.
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'kit', 'paint'))
import kitpaint as K  # noqa: E402

MDIR = os.path.join(HERE, '..')
BBMODEL = os.path.join(MDIR, 'body_printer.bbmodel')
PLACEHOLDER = os.path.join(MDIR, 'body_printer_placeholder.png')
SHEET = os.path.join(K.OUT, 'body_printer.png')
Y0, Y1 = 3, 31

# the side sign: a dark band z -6..-1, y 5..30 on the right wall's outer face; PHOTO in 3x4 capitals at z -5..-2
BAND = (-6, -1, 5, 30)
WORD = 'PHOTO'
# the marquee's sign field: its whole 4 px face, x -6..4 (10 wide), between the rim strip (viewer's right) and the lamp
# (viewer's left). 4 rows cannot hold PHOTO (19 px in the 3x4 face) or BODY (15); the sign is a pictogram: a camera,
# black (r1) on the yellow field: a 2x2 lens (w3, a white e5 glint) high in an 8x3 body, the viewfinder bump at its
# left, a white flash pixel at its right. Read from the viewer's left (col 0 = x 3.5). '.' is the field (the plain band: w3 top row, w2, w1 bottom row).
CAMERA = ['. . K K . . . W . .',
          '. K K K Y W K K K .',
          '. K K K Y Y K K K .',
          '. K K K K K K K K .']
CAMERA_KEYS = {'K': 'r1', 'Y': 'w3', 'W': 'e5'}


def side_sign(f):
    """Paint the band and the word on a right-wall piece's east face, by world position."""
    z0, z1, ya, yb = BAND
    for c, r in f.each():
        x, y, z = f.world(c, r)
        if not (z0 < z < z1 and ya < y < yb):
            continue
        k = 'r1'
        ci = int(-2 - z) if -5 < z < -2 else None             # glyph column 0..2, viewer's left (back) first
        ri = int(29.5 - y)                                      # rows from the top of the band's text
        if ci is not None and 0 <= ri:
            li, lr = divmod(ri, 5)
            if li < len(WORD) and lr < 4 and K.FONT34[WORD[li]][lr][ci] == '#':
                k = 'w3'
        f.put(c, r, k)


def coin_plate(f):
    """A brass coin plate with its slot (3x3 at z 1..4, y 17..20 on the right wall): b4 top row, b3 field, an r0
    slit down its middle, a b2 corner."""
    for c, r in f.each():
        x, y, z = f.world(c, r)
        if 1 < z < 4 and 17 < y < 20:
            col = 0 if z > 3 else (1 if z > 2 else 2)          # viewer's left (the back, +z) first
            row = 0 if y > 19 else (1 if y > 18 else 2)
            f.put(c, r, [['b4', 'b4', 'b4'], ['b3', 'r0', 'b3'], ['b3', 'r0', 'b2']][row][col])


def booth(S):
    for n in S.names('wall_l_') + S.names('wall_r_'):
        outer = 'west' if n.startswith('wall_l') else 'east'
        inner = 'east' if outer == 'west' else 'west'
        for f in S.faces(n, K.SIDES):
            if f.side == inner:
                f.fill('e1')                          # the booth's inside walls, seen through the glass
            else:
                K.enamel_body(f, Y0, Y1)
        S.face(n, 'up').fill('e3')
        S.face(n, 'down').fill('e0')
        if outer == 'east':
            side_sign(S.face(n, 'east'))
            coin_plate(S.face(n, 'east'))
    # the back wall: the dark inside (r0), the outside a rubber back panel (r1, r2 top row)
    bw = 'wall_back'
    K.fill(S, bw, 'r1')
    S.face(bw, 'north').fill('r0')
    S.face(bw, 'south').row(0, 'r2')
    for n in ('header', 'header_in'):
        for f in S.faces(n, K.SIDES):
            K.enamel_body(f, Y0, Y1)
        S.face(n, 'up').fill('e3')
        S.face(n, 'down').fill('e0')
    K.enamel_top(S.face('roof', 'up'))
    for f in S.faces('roof', K.SIDES):
        f.fill('e3')
    S.face('roof', 'down').fill('e0')
    K.makers_plate(S, 'makers_plate', 'south')


def marquee(S):
    m = 'marquee'
    for f in S.faces(m, K.SIDES):
        K.plain_band(f)
    S.face(m, 'up').fill('w3')
    S.face(m, 'down').fill('w1')
    f = S.face(m, 'north')
    for c, r in f.each():
        x, y, z = f.world(c, r)
        if -6 < x < 4:
            k = CAMERA[int(31.5 - y)].split()[int(3.5 - x)]
            if k != '.':
                f.put(c, r, CAMERA_KEYS[k])
    rim = 'marquee_rim_l'
    for g in S.faces(rim, K.SIDES):
        K.plain_band(g)
    S.face(rim, 'up').fill('w3')
    S.face(rim, 'down').fill('w1')
    K.lamp_bezel(S)
    K.lens_on_sheet(S, 'lamp_lens', 'ready')


def curtain(S):
    """The plum backdrop: pleats in depth (the forward pleat f2, the ones behind f1), their sides in shade (f0), the
    hem a step darker, the top row under the rail in its shadow; the brass rail."""
    front = {'curtain_0': 'f2', 'curtain_2': 'f2'}
    for n in S.names('curtain_'):
        for f in S.faces(n, K.SIDES):
            if f.side == 'north':
                fld = front.get(n, 'f1')
                for c, r in f.each():
                    f.put(c, r, 'f0' if r == 0 else (K.st(fld, -1) if r == f.H - 1 else fld))
            else:
                f.fill('f0')
        S.face(n, 'up').fill('f0')
        S.face(n, 'down').fill('f0')
    K.brass_plate(S, 'curtain_rail')


def slot_and_photos(S):
    for n in S.names('slot_frame_'):
        K.small(S, n, 'b', 3, top='b4', bottom='b1')
    # the photo card: a white border (e4), two tiny portraits (a peach face f3 on r1, its shoulders r2), as a strip
    p = 'photo_strip'
    K.fill(S, p, 'e3')
    f = S.face(p, 'east')
    rows = ['e4 e4 e4 e4',
            'e4 f3 r1 e4',
            'e4 r2 r2 e4',
            'e4 e4 e4 e4',
            'e4 f3 r1 e4',
            'e4 r2 r2 e3']
    f.rows(rows)


def saline_rows(level, H=10):
    """The sight face, top to bottom: empty rows dark glass (t0, a t4 glint top left), saline from the bottom: its top
    row the meniscus l3, the body l1, the foot l0."""
    fill = {0: 0, 1: 2, 2: 5, 3: 7, 4: 10}[level]
    out = []
    for r in range(H):
        if r >= H - fill:
            out.append('l3' if r == H - fill else ('l0' if r == H - 1 and fill > 2 else 'l1'))
        else:
            out.append('t4' if r == 0 else 't0')
    return out


def saline_textures():
    paths = []
    for lv in range(5):
        rows = saline_rows(lv)

        def face(fr, side, c, r, W, H, rows=rows):
            if side in ('up',):
                return 't1'
            if side == 'down':
                return 't0'
            return rows[r] if H == 10 else 't1'
        p = os.path.join(K.OUT, 'body_printer_saline_%d.png' % lv)
        paths.append(K.state_sprite(p, 1, 10, 2, face, fill='t1'))
    return paths


def saline_window(S):
    for n in S.names('saline_frame_'):
        tag = n[-1]
        for f in S.faces(n, K.SIDES):
            f.fill({'t': 'b4', 'b': 'b2'}.get(tag, 'b3') if f.side == 'west' else 'b1')
        S.face(n, 'up').fill('b4')
        S.face(n, 'down').fill('b1')
    rows = saline_rows(2)
    for f in S.faces('saline_face', K.SIDES):
        for c, r in f.each():
            f.put(c, r, rows[r])
    S.face('saline_face', 'up').fill('t1')
    S.face('saline_face', 'down').fill('t0')


def door_and_bar(S):
    for n in ('door_stile_h', 'door_stile_f'):
        K.brass_post(S, n)
    for n in ('door_rail_b', 'door_rail_t'):
        for f in S.faces(n, K.SIDES):
            K.brass_rail(f)
        S.face(n, 'up').fill('b4')
        S.face(n, 'down').fill('b1')
    # the door glass: cutout clear; a 1 px frame inside the brass (g3 top and left, g2 bottom and right) and one 2 px
    # glint at the upper left: nothing else, so the body inside shows
    for f in S.faces('door_glass'):
        K.clear_glass(f, streaks=False)
        if f.W >= 6 and f.H >= 6:
            f.put(2, 3, 'g3')
            f.put(3, 2, 'g3')
    # the print bar (brass rail), its sleeve on the post, the copper print head with its amber working light
    for f in S.faces('bar', K.SIDES):
        K.brass_rail(f)
    K.brass_top(S.face('bar', 'up'), band=False)
    S.face('bar', 'down').fill('b1')
    K.brass_housing(S, 'sleeve')
    K.small(S, 'head', 'c', 4, top='c6', bottom='c2')
    S.face('head', 'north').row(0, 'c6')
    K.small(S, 'head_lip', 'c', 5, top='c6', bottom='c2')
    K.fill(S, 'head_lens', 'A1')
    S.face('head_lens', 'north').rows(['A3 A2'])
    S.face('head_lens', 'down').fill('A0')
    # the corner post the bar rides: dark steel, one tone a face, a lit top
    K.small(S, 'column', 's', 1, top='s2', bottom='s0')


def paint(S):
    K.plinth(S)
    K.feet(S)
    booth(S)
    marquee(S)
    curtain(S)
    slot_and_photos(S)
    K.side_port(S, 'port', 'west')
    saline_window(S)
    door_and_bar(S)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview')
    a = ap.parse_args()
    S = K.Sheet(BBMODEL)
    paint(S)
    miss = S.missing(clear_ok=('door_glass',))
    if miss:
        sys.exit('unpainted faces: %s' % miss)
    out = [S.save(SHEET)] + saline_textures()
    for p in out:
        print('wrote', p)
    if a.preview:
        os.makedirs(a.preview, exist_ok=True)
        K.standins_from(S, PLACEHOLDER)
        S.save(os.path.join(a.preview, 'printer_rest.png'))
        K.lens_on_sheet(S, 'lamp_lens', 'work')
        S.save(os.path.join(a.preview, 'printer_work.png'))
        print('previews in', a.preview)


if __name__ == '__main__':
    main()

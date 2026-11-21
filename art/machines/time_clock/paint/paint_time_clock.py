"""Time Clock: the painted sheet.

    python -I art/machines/time_clock/paint/paint_time_clock.py                # writes the sheet into the assets
    python -I art/machines/time_clock/paint/paint_time_clock.py --check        # writes nothing; fails if it differs
    python -I art/machines/time_clock/paint/paint_time_clock.py --preview DIR  # also review copies of the model
Then: python -I tools/tex/tex.py adopt block/machine/time_clock.png

One sheet for the baked body and the bones. Every pixel is placed by a rule from the palette (art/palette.json, its
default set); nothing is random.

The value plan (planned at 110 px). The dial is the subject, read from across a yard:
  LIGHT  the dial's flat cream face (e4) and the manila card slips
  DARK   the cabinet in gunmetal japanning (s1 field, an s3 lip, a dark b1 pinstripe), the awning (a riveted s2
         plate, s0 under it: the shade), the flange
  MID    the steel post (s3); the rack is dark (wings s1, pocket lips s2 with an s3 lit edge): only its cards are light
  ACCENT bright brass only on the dial's bezel (lit top and left, b2 low right), the bell and the lamp's bezel; the
         slot's throat dark with a brass rim; the lever's grip warning yellow (it moves into space)
A 24-hour dial, noon at the top (the hour hand turns once a day; the minute hand once an hour): four ink ticks at
noon (red), 18:00, midnight and 06:00 (quiet, e1) and a brass hub. The slips are plain manila.
"""
import argparse
import hashlib
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'kit', 'paint'))
import kitpaint as K  # noqa: E402

MDIR = os.path.join(HERE, '..')
BBMODEL = os.path.join(MDIR, 'time_clock.bbmodel')
SHEET = os.path.join(K.OUT, 'time_clock.png')
CAB = 'japanned'


# ---------------------------------------------------------------------------------------------------------- the dial
def inside_ring(x, y):
    """The dial's face: the texels of the cabinet's front inside the bezel (rows 4, 6, 6, 6, 6, 4 wide)."""
    if 8 < y < 9 or 13 < y < 14:
        return abs(x) < 2
    return 9 < y < 13 and abs(x) < 3


def dial_key(x, y):
    ax = abs(x)
    if ax < 1 and 10 < y < 12:
        return 'b4' if (x > 0 and y > 11) else 'b3'                 # the hub (2x2), lit top-left
    if ax < 1 and y > 13:
        return 'd1'                                                # noon, the red mark
    if ax < 1 and y < 9:
        return 'e1'                                                # midnight: a quiet tick
    if ax > 2 and 10 < y < 12:
        return 'e1'                                                # 06:00 (viewer's left) and 18:00 (right)
    if y > 13 and x > 1:
        return 'e5'                                                # a glint at the top left
    return 'e4'                                                    # one flat cream face (round 1: no halves)


# ------------------------------------------------------------------------------------------------------- the cabinet
def japanned_face(f, pin_rect=True):
    """Gunmetal japanning by face rows (round 1: rubber black read darker than anything vanilla shows at this
    size): a lit lip on top (s3), field s1, last row s0; a dark brass pinstripe rectangle (b1) 1 px in from the edges on faces 5+ wide and 5+ tall."""
    W, H = f.W, f.H
    for c, r in f.each():
        if r == 0:
            k = 's3'
        elif r == H - 1:
            k = 's0'
        else:
            k = 's1'
        if pin_rect and W >= 5 and H >= 5 and 1 <= r <= H - 2 and 1 <= c <= W - 2 and \
                (r in (1, H - 2) or c in (1, W - 2)):
            k = 'b1'
        f.put(c, r, k)


def cabinet(S, kind):
    if kind == 'japanned':
        for f in S.faces('cabinet', K.SIDES):
            japanned_face(f, pin_rect=f.side != 'north')          # the front is the dial's: no pinstripe round it
        S.face('cabinet', 'up').fill('s2')
        S.face('cabinet', 'down').fill('s0')
    elif kind == 'brass':
        for f in S.faces('cabinet', K.SIDES):
            K.brass_rail(f)
        S.face('cabinet', 'up').fill('b3')
        S.face('cabinet', 'down').fill('b1')
    else:
        for f in S.faces('cabinet', K.SIDES):
            K.enamel_body(f, 7, 15, bead=False)
        S.face('cabinet', 'up').fill('e3')
        S.face('cabinet', 'down').fill('e1')
    front = S.face('cabinet', 'north')
    if kind == 'japanned':
        # the gloss catches the light down the front's left edge (the viewer's left: +x)
        front.by_world(lambda c, r, x, y, z: 's2' if (x > 5 and 7 < y < 14) else None)
    front.by_world(lambda c, r, x, y, z: dial_key(x, y) if inside_ring(x, y) else None)
    # the maker's plate on the back: a 4x2 brass plate painted on the japanning, centred, high
    back = S.face('cabinet', 'south')
    c0 = back.W // 2 - 2
    back.rows(['b5 b1 b1 b4', 'b3 b3 b3 b2'], c0, 2)


def bezel(S):
    for n in S.names('bezel_'):
        f = S.face(n, 'north')
        x0, y0, _ = S.cubes[n]['frm']
        cx = x0 + S.cubes[n]['w'] / 2.0
        cy = y0 + S.cubes[n]['h'] / 2.0
        # light from the top left (the viewer's left is +x): the upper and +x pieces lit, the lower and -x dark
        if cy > 13 or (cx > 1 and cy > 9):
            k = 'b4'                                              # the top and the viewer's left
        elif cy < 9:
            k = 'b2'                                              # the bottom bar only (round 2: a b2 side read as
        else:                                                     # a brown bar beside the face)
            k = 'b3'
        f.fill(k)
        for s_ in ('east', 'west', 'south'):
            S.face(n, s_).fill('b3')
        S.face(n, 'up').fill('b4')
        S.face(n, 'down').fill('b2')
    # the glint: one b5 texel on the upper-left step
    S.face('bezel_2', 'north').put(0, 0, 'b5')


def hands(S):
    for n in ('hour', 'minute'):
        for f in S.faces(n):
            f.fill('r0')
        S.face(n, 'north').fill('r1')
        S.face(n, 'north').put(0, 0, 'r2')                         # the tip catches the light


# ------------------------------------------------------------------------------------------------------- the stand
def stand(S):
    K.dark_steel(S, 'flange')
    K.steel_part(S, 'post', field='s3', lit='s4', under='s1')
    for f in S.faces('post', K.SIDES):
        f.row(f.H - 1, 's1')                                       # where it meets the flange
    K.lamp_bezel(S)
    K.lens_on_sheet(S, 'lamp_lens', 'ready')
    # the rack is part of the dark stand (round 3: a pale rack read as cladding from another block): wings and
    # steps dark steel, the pocket lips one step up with a lit edge, so only the cards are light
    for n in S.names('wing_') + S.names('step_'):
        K.dark_steel(S, n)
    for n in S.names('lip_'):
        for f in S.faces(n, K.SIDES):
            f.fill('s2')
        S.face(n, 'up').fill('s3')                                 # the pocket's lit edge
        S.face(n, 'down').fill('s0')
    for n in range(1, 9):
        slip(S, 'slip_%d' % n, n in (1, 2, 5, 6))


def slip(S, n, red):
    """A Job Card standing in its pocket (2x3x1; the lowest row is behind the lip): plain manila, lit at its top left
    (round 2: red and ink marks on the slips read as confetti at 5 blocks); its top edge m6."""
    f = S.face(n, 'north')
    f.rows(['m6 m5', 'm5 m4', 'm4 m3'])
    for s in ('east', 'west', 'south'):
        S.face(n, s).fill('m3')
    S.face(n, 'up').fill('m6')
    S.face(n, 'down').fill('m2')


# --------------------------------------------------------------------------------------------- the punch station
def punch_station(S):
    # the throat: a dark steel box with a brass rim on its outer face only (round 3: an all-brass box read as an
    # orange cube on the flank)
    for n in S.names('throat_'):
        K.dark_steel(S, n, lit_row=False)
        S.face(n, 'west').fill('b3')
    S.face('throat_t', 'west').fill('b4')
    S.face('throat_b', 'west').fill('b2')
    K.recess(S, 'slit', 'r0')
    # the time card (seen only while it is out): manila, its end a step darker, two punched stamps on top
    for f in S.faces('time_card'):
        f.fill('m4')
    S.face('time_card', 'up').fill('m5')
    up = S.face('time_card', 'up')
    up.put(1, 0, 'd1')
    up.put(3, 1, 'k1')
    S.face('time_card', 'west').fill('m3')
    K.dark_steel(S, 'lever_boss')
    S.face('lever_boss', 'west').rows(['s2 s2', 's2 b4', 's1 s1'])    # a brass pin head in the boss
    K.steel_part(S, 'lever_arm', field='s3', lit='s4', under='s2')
    # the grip in warning yellow (it moves into space; round 1 could not tell the lever from a roof arm)
    for f in S.faces('lever_grip', K.SIDES):
        f.fill('w2')
        f.row(0, 'w3')
        f.row(f.H - 1, 'w1')
    S.face('lever_grip', 'up').fill('w3')
    S.face('lever_grip', 'down').fill('w1')


# --------------------------------------------------------------------------------------------------- awning, bell
def top(S):
    K.plinth(S, 'awning')                                          # s1 sides, s2 top with an s3 lit edge, s0 under
    up = S.face('awning', 'up')                                    # a riveted plate: a rivet 1 px in at each corner
    for c, r in ((1, 1), (up.W - 3, 1), (1, up.H - 3), (up.W - 3, up.H - 3)):
        K.rivet(up, c, r, 's4', 's1')
    for f in S.faces('awning', ('north', 'east', 'west')):
        f.fill('s2')                                               # its lit front edge
    K.dark_steel(S, 'bell_base', lit_row=False)
    for f in S.faces('bell_dome', K.SIDES):
        f.fill('b3')
    S.face('bell_dome', 'east').fill('b4')                         # the lit flank (+x, the viewer's left)
    S.face('bell_dome', 'west').fill('b2')
    K.brass_top(S.face('bell_dome', 'up'), band=False)
    S.face('bell_dome', 'down').fill('b1')
    for f in S.faces('bell_top', K.SIDES):
        f.fill('b4')
    S.face('bell_top', 'up').rows(['b4 b5', 'b3 b4'])
    S.face('bell_top', 'down').fill('b2')
    for f in S.faces('plunger_cap'):
        f.fill('r1')
    S.face('plunger_cap', 'up').fill('r3')
    # the ON STRIKE card (hidden until 0.7.2): manila, the slogan scrawled in red ink by hand (two lines of scrawl:
    # at 6x4 the words cannot be lettered; 0.7.2 may repaint it), string and tie in rubber black
    card = S.face('strike_card_card', 'north')
    card.rows(['m5 m5 m5 m5 m5 m4',
               'm5 d1 d2 m5 d1 m4',
               'm4 d2 m4 d1 d2 d1',
               'm4 m4 m3 m4 m3 m3'])
    for s in ('east', 'west', 'south', 'up', 'down'):
        S.face('strike_card_card', s).fill('m3')
    for n in ('strike_string', 'strike_tie'):
        for f in S.faces(n):
            f.fill('r1')


def paint(S, kind=CAB):
    stand(S)
    cabinet(S, kind)
    bezel(S)
    hands(S)
    punch_station(S)
    top(S)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview')
    ap.add_argument('--check', action='store_true')
    ap.add_argument('--cab', default=CAB)
    a = ap.parse_args()
    S = K.Sheet(BBMODEL)
    paint(S, a.cab)
    miss = S.missing()
    if miss:
        sys.exit('unpainted faces: %s' % miss)
    if a.check:
        import io
        buf = io.BytesIO()
        S.img.save(buf, 'PNG')
        with open(SHEET, 'rb') as fh:
            same = hashlib.sha1(fh.read()).hexdigest() == hashlib.sha1(buf.getvalue()).hexdigest()
        print('time_clock.png', 'matches' if same else 'DIFFERS')
        sys.exit(0 if same else 1)
    if a.preview:
        os.makedirs(a.preview, exist_ok=True)
        png = S.save(os.path.join(a.preview, 'time_clock_%s.png' % a.cab))
        K.embed(BBMODEL, png, os.path.join(a.preview, 'time_clock_%s.bbmodel' % a.cab))
        print('preview in', a.preview)
        if a.cab != CAB:
            return
    print('wrote', S.save(SHEET))


if __name__ == '__main__':
    main()

"""Injection Rig (the 1950s petrol pump with a syringe for a nozzle): the sheet, the globe strips and the counter.

    python -I art/machines/injection_rig/paint/paint_rig.py                # writes the textures into the assets
    python -I art/machines/injection_rig/paint/paint_rig.py --preview DIR  # also DIR/rig_rest.png, rig_work.png
Then adopt what it prints with tools/tex/tex.py adopt.

Writes, under textures/block/machine/:
  injection_rig.png                 128x112, the baked pump and the three bones (nozzle_arm, syringe, plunger)
  injection_rig_globe_off.png       128x16: the sheet's rows 80-96 with the globe painted unlit milk glass (serum 0)
  injection_rig_globe_lit.png       128x16: the same rows, the globe a neutral light base (e5) for BlockColors' tint
                                    per serum category (serum 1-7); the five globe elements keep their sheet u and
                                    take v - 80
  injection_rig_counter_still.png   the counter window (element 6x3x1), box-UV at (0, 0) of a 16x16 sprite
  injection_rig_counter_rolling.png the same, rolling: 3 frames x 2 ticks (.mcmeta)

The value plan (110 px first). Kind, not sinister: a fairground petrol pump, not a clinic:
  LIGHT  the ivory body (K2 faces, e2 field, e3 lip, belt seam), the milk-glass globe on top (e4-e5), the pale dial
  DARK   the island base (s1-s2), the waist belt, the recessed side panels (e1)
  ACCENT the company roundel on both side panels (a brass ring round teal crossed with yellow), the white counter drums with dark digits in the brass dial bezel,
         sheath, bearing and thumb, the syringe barrel (a neutral light base, e5 with e3 graduations, for the code's
         serum tint) on its bright steel rail
Story marks, once each: the roundels; the price ticks on the dial; the maker's plate on the back.
"""
import argparse
import os
import sys


HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'kit', 'paint'))
import kitpaint as K  # noqa: E402

MDIR = os.path.join(HERE, '..')
BBMODEL = os.path.join(MDIR, 'injection_rig.bbmodel')
PLACEHOLDER = os.path.join(MDIR, 'injection_rig_placeholder.png')
SHEET = os.path.join(K.OUT, 'injection_rig.png')
STRIP = (80, 96)
BODY = ['core', 'skin_low', 'skin_top', 'skin_l', 'skin_r'] + ['side_%s_%s' % (s, p) for s in 'rl'
                                                                for p in ('post_f', 'post_b', 'bar_lo', 'bar_mid',
                                                                          'bar_top')]


def body(S):
    K.dark_steel(S, 'island')
    K.dark_steel(S, 'plinth')
    for n in BODY:
        for f in S.faces(n, K.SIDES):
            K.enamel_body(f, 3, 19)
        K.enamel_top(S.face(n, 'up'))
        S.face(n, 'down').fill('e0')
    # the recessed side panels (the core's east and west faces between the side frames), the roundel on the upper one
    for side in ('east', 'west'):
        f = S.face('core', side)

        def panel(c, r, x, y, z):
            if -2 < z < 2 and (3 < y < 8 or 11 < y < 16):
                return 'e1'
            return None
        f.by_world(panel)
        cols = [c for c in range(f.W) if -2 < f.world(c, 0)[2] < 2]
        rows = [r for r in range(f.H) if 12 < f.world(0, r)[1] < 16]
        K.roundel(f, min(cols), min(rows))
    # the back: the panel inside the raised rim recessed (e1, e0 top row and left column), the rim e3, the plate
    f = S.face('core', 'south')
    f.by_world(lambda c, r, x, y, z: ('e0' if (y > 16 or x < -2) else 'e1') if (-3 < x < 3 and 12 < y < 17) else None)
    for n in S.names('back_rim_'):
        K.fill(S, n, 'e3')
        S.face(n, 'up').fill('e4')
        S.face(n, 'down').fill('e1')
    K.makers_plate(S, 'makers_plate', 'south')
    # the dial: pale (e4), faint price ticks (s4) on its top visible row, set in a brass bezel
    d = 'dial_field'
    K.fill(S, d, 'e3')
    f = S.face(d, 'north')
    f.by_world(lambda c, r, x, y, z: ('s4' if (16 < y < 17 and c % 2 == 1) else 'e4'))
    for n in S.names('dial_bezel_'):
        tag = n[-1]
        K.fill(S, n, {'t': 'b4', 'b': 'b2'}.get(tag, 'b3'))
        S.face(n, 'up').fill('b4')
        S.face(n, 'down').fill('b1')
    for n in S.names('belt_'):
        K.dark_steel(S, n)
    K.small(S, 'cassette_ledge', 'b', 3, top='b4', bottom='b1')
    K.brass_housing(S, 'sheath')
    S.face('sheath', 'up').put(1, 1, 'r0')                  # the needle's socket
    K.brass_plate(S, 'bearing')
    K.small(S, 'neck', 's', 1, top='s2', bottom='s0')
    K.lamp_bezel(S)
    K.lens_on_sheet(S, 'lamp_lens', 'ready')
    counter_on_sheet(S)


def globe(S, mode):
    """The globe's five slabs (a voxel sphere). off: milk glass (e4 field, e5 top row and top faces, e3 bottom rows, an
    e5 glint at the front upper left). lit: a neutral light base for the tint (e5 field, e4 lower rows)."""
    hi, fld, lo = ('e5', 'e4', 'e3') if mode == 'off' else ('e5', 'e5', 'e4')
    for n in S.names('globe_'):
        for f in S.faces(n, K.SIDES):
            for c, r in f.each():
                f.put(c, r, hi if r == 0 and f.H >= 2 else (lo if r == f.H - 1 and f.H >= 3 else fld))
        S.face(n, 'up').fill(hi)
        S.face(n, 'down').fill(lo)
    if mode == 'off':
        S.face('globe_2', 'north').put(1, 0, 'g3')


def counter_rows(frame):
    """The counter, as a petrol pump's: white number drums (e4) with dark digits (r1), the drums' top row in shade
    (e3) and their bottom row e2. A dark window with white marks read as a mouth of teeth under the globe; white
    drums with three dark digits read as a counter. Rolling: the digits step down a row a frame."""
    shade = {0: 'e3', 1: 'e4', 2: 'e2'}
    digits = [None, 'r1', None, 'r1', None, 'r1']
    at = 1 if frame is None else frame
    return [[(digits[c] if (r == at and digits[c]) else shade[r]) for c in range(6)] for r in range(3)]


def counter_on_sheet(S):
    f = S.face('counter', 'north')
    for r, row in enumerate(counter_rows(None)):
        for c, k in enumerate(row):
            f.put(c, r, k)
    for s in ('east', 'west', 'up', 'down', 'south'):
        S.face('counter', s).fill('r1')


def counter_textures():
    out = []

    def make(rows_of):
        def face(fr, side, c, r, W, H):
            if side == 'north':
                return rows_of(fr)[r][c]
            return 'r1'
        return face
    out.append(K.state_sprite(os.path.join(K.OUT, 'injection_rig_counter_still.png'), 6, 3, 1,
                              make(lambda fr: counter_rows(None)), fill='r1'))
    p = os.path.join(K.OUT, 'injection_rig_counter_rolling.png')
    out.append(K.state_sprite(p, 6, 3, 1, make(lambda fr: counter_rows(fr)), fill='r1', frames=3, frametime=2))
    out.append(p + '.mcmeta')
    return out


def arm(S):
    for n in ('hub_f', 'hub_b', 'hub_l', 'hub_r', 'guide'):
        K.steel_part(S, n, field='s3', lit='s4', under='s2')
    K.steel_part(S, 'rail', field='s3', lit='s4', under='s2')
    for f in S.faces('rail', ('north', 'west')):
        f.col(0, 's4')
    # the syringe: a bright needle, the barrel a neutral base for the serum tint with graduations, steel flange and
    # rod, the brass thumb pad
    K.small(S, 'needle', 's', 5, top='s5', bottom='s4')
    for f in S.faces('barrel', K.SIDES):
        for c, r in f.each():
            f.put(c, r, 'e3' if (c == f.W - 1 and r % 2 == 1) else ('e4' if c == f.W - 1 else 'e5'))
    S.face('barrel', 'up').fill('e4')
    S.face('barrel', 'down').fill('e3')
    for n in ('flange', 'rod'):
        K.small(S, n, 's', 4, top='s5', bottom='s3')
    K.small(S, 'thumb', 'b', 3, top='b4', bottom='b1')


def paint(S):
    body(S)
    globe(S, 'off')
    arm(S)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview')
    a = ap.parse_args()
    S = K.Sheet(BBMODEL)
    paint(S)
    miss = S.missing()
    if miss:
        sys.exit('unpainted faces: %s' % miss)
    out = [S.save(SHEET)]
    y0, y1 = STRIP
    out.append(K.sheet_strip(S, os.path.join(K.OUT, 'injection_rig_globe_off.png'), y0, y1, lambda s: globe(s, 'off')))
    out.append(K.sheet_strip(S, os.path.join(K.OUT, 'injection_rig_globe_lit.png'), y0, y1, lambda s: globe(s, 'lit')))
    globe(S, 'off')
    out += counter_textures()
    for p in out:
        print('wrote', p)
    if a.preview:
        os.makedirs(a.preview, exist_ok=True)
        K.standins_from(S, PLACEHOLDER)
        S.save(os.path.join(a.preview, 'rig_rest.png'))
        K.lens_on_sheet(S, 'lamp_lens', 'work')
        # working: the globe lit, tinted as a serum would be (preview only: multiply by flesh f3 for the board)
        globe(S, 'lit')
        tint = K.rgba('f3')
        for n in S.names('globe_') + ['barrel']:
            for f in S.faces(n):
                for c, r in f.each():
                    p = S.px[f.x0 + c, f.y0 + r]
                    S.px[f.x0 + c, f.y0 + r] = tuple(p[i] * tint[i] // 255 for i in range(3)) + (p[3],)
        S.save(os.path.join(a.preview, 'rig_work.png'))
        print('previews in', a.preview)


if __name__ == '__main__':
    main()

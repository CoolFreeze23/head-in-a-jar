"""DNA Sequencer (the brass cash register on the bench): the painted sheet, every pixel placed from palette keys.

    python -I art/machines/dna_sequencer/paint/paint_sequencer.py                # writes block/machine/dna_sequencer.png
    python -I art/machines/dna_sequencer/paint/paint_sequencer.py --preview DIR  # also DIR/sequencer_rest.png, _work.png
Then: python -I tools/tex/tex.py adopt block/machine/dna_sequencer.png

One sheet, 128x144, for the baked bench and register and the eight bones.

The value plan (110 px first). The one machine that is mostly brass, so the brass rule is kept strictly: every brass
field b3, lit rows b4, b5 only as glints, the darks only ROUND the brass (b1 undersides, b2 end columns, the dark
key panel and display well behind it). The colour is carried by the keys, the window and the flag:
  LIGHT  the brass register (b3) on the ivory K12 bench
  DARK   the key panel (r1) behind the keys, the display well (r0) behind clear glass, the dark plinth line, the
         worktop and the shelf
  ACCENT the four key caps, one colour per base, from the palette: A green (j2, top j3), C teal (t2, top t3: the
         palette has no blue; the game's C is blue in the screen), G orange (A1 all over: its lighter step reads as
         brass, so the top stays A1, pushed away from brass), T red (R1, top R2); the call plate (a neutral light base, e5, for the code's tint per base);
         the NO SALE banner (white e5 with a red prohibition sign: four pixels cannot hold the words); the receipt
         paper (o3 with printed lines)
Story marks, once each: the polish band on the register's back; the crank knob's rubber; the receipt's tear line.
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'kit', 'paint'))
import kitpaint as K  # noqa: E402

MDIR = os.path.join(HERE, '..')
BBMODEL = os.path.join(MDIR, 'dna_sequencer.bbmodel')
PLACEHOLDER = os.path.join(MDIR, 'dna_sequencer_placeholder.png')
SHEET = os.path.join(K.OUT, 'dna_sequencer.png')

KEYS = {'a': ('j', 2, 'j3'), 'c': ('t', 2, 't3'), 'g': ('A', 1, 'A1'), 't': ('R', 1, 'R2')}
NO_SALE = ['e5 R1 R1 e5',
           'R1 e5 R1 R1',
           'R1 R1 e5 R1',
           'e5 R1 R1 e5',
           'e4 e4 e4 e4']


def register(S):
    K.small(S, 'reg_plinth', 's', 1, top='s1', bottom='s0')
    K.brass_plate(S, 'keybed')
    K.brass_housing(S, 'body')
    f = S.face('body', 'south')                  # one polish band, on the back (the front is covered)
    K.polish(f, 1, 1, f.H - 1)
    for n in ('body_f_bottom', 'body_f_top', 'body_f_r', 'body_f_l', 'disp_bottom', 'disp_side_r', 'disp_side_l',
              'disp_back'):
        K.small(S, n, 'b', 3, top='b4', bottom='b1')
    K.brass_plate(S, 'disp_top')
    # the key panel behind the keys: dark, a lit top row
    kp = 'key_panel'
    K.fill(S, kp, 'r1')
    S.face(kp, 'north').row(0, 'r2')
    K.recess(S, 'disp_well', 'r0')
    for f in S.faces('disp_glass'):
        K.glass_window(f)
    K.small(S, 'reader', 's', 1, top='s0', bottom='s0')
    S.face('reader', 'up').put(1, 1, 's0')


def keys(S):
    for k, (m, i, top) in KEYS.items():
        K.small(S, 'key_%s_stem' % k, 's', 1, top='s2', bottom='s0')
        cap = 'key_%s_cap' % k
        K.small(S, cap, m, i, top=top, bottom='%s%d' % (m, max(0, i - 1)))
        S.face(cap, 'north').row(0, top)


def flag_and_paper(S):
    for f in S.faces('flag_plate'):
        K.tint_base(f)
    b = 'flag_banner'
    K.fill(S, b, 'e4')
    S.face(b, 'north').rows(NO_SALE)
    S.face(b, 'south').rows(NO_SALE)
    # the receipt: paper (o3), printed lines (s2) on alternate rows, the torn top edge o2
    for n in ('receipt_tape', 'receipt_curl'):
        for f in S.faces(n):
            for c, r in f.each():
                k = 'o3'
                if n == 'receipt_tape' and f.side in ('north', 'south') and r % 2 == 1 and 0 < c < f.W - 1:
                    k = 's2' if (c + r // 2) % 3 else 'o2'
                f.put(c, r, k)
        S.face(n, 'up').fill('o2')


def crank(S):
    K.small(S, 'crank_axle', 's', 3)
    K.small(S, 'crank_hub', 'b', 3, top='b4', bottom='b1')
    K.small(S, 'crank_arm', 's', 1, top='s2', bottom='s0')
    K.small(S, 'crank_knob', 'r', 1, top='r2', bottom='r0')


def paint(S):
    K.bench(S)
    K.drawer(S, 'drawer_box', ('drawer_pull',))
    K.lamp_bezel(S)
    K.lens_on_sheet(S, 'lamp_lens', 'ready')
    register(S)
    keys(S)
    flag_and_paper(S)
    crank(S)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview')
    a = ap.parse_args()
    S = K.Sheet(BBMODEL)
    paint(S)
    miss = S.missing(clear_ok=('disp_glass',))
    if miss:
        sys.exit('unpainted faces: %s' % miss)
    print('wrote', S.save(SHEET))
    if a.preview:
        os.makedirs(a.preview, exist_ok=True)
        K.standins_from(S, PLACEHOLDER)
        S.save(os.path.join(a.preview, 'sequencer_rest.png'))
        K.lens_on_sheet(S, 'lamp_lens', 'work')
        S.save(os.path.join(a.preview, 'sequencer_work.png'))
        print('previews in', a.preview)


if __name__ == '__main__':
    main()

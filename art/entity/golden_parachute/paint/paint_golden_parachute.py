"""The Golden Parachute: the painted sheet, every pixel placed from palette keys.

Source of `textures/entity/golden_parachute.png` (adopted as `painted`). No randomness and no glow.

    python -I art/entity/golden_parachute/paint/paint_golden_parachute.py                # writes the sheet
    python -I art/entity/golden_parachute/paint/paint_golden_parachute.py --preview DIR  # also DIR/..._preview.png

Value plan (it must read at thirty blocks from the ground as a gold canopy with a dark band under it, never as a brass
lid):
- the canopy is CLOTH: a gold brocade, b3 field with a quiet b4 diamond lattice (6 px lozenges) laid in world x/z,
  so the four panels and the crown share one weave (two steps of the brass ramp, no more); a b2 seam row where each panel
  hinges on the crown; the under-surface darker (b2 field, b1 weave and seam) so the dome's shape reads from below;
  the 2 px edges b4 over b2. No rivets, no plate shading, no glint;
- the valance, rosette, pilot rim, straps and seal in the palette's existing deep red: the FLESH ramp (f0 4c1a27,
  f1 7d323a, f2 a44d4b: the model's placeholder crimson is f1). Valance f1 with an f0 hem, f0
  inside and under, and no lit row, so it stays the dark band under the gold;
- tassels gold (b4, b5 top, b2 under), one tone a face;
- cords white and thin with one dark edge (e5 north and west, e4 south, s2 east) so they hold against sky;
- the envelope ivory (e4, e5 top row) with the flap's V in e2, a torn edge on the flap (e5/e2 teeth), and a deep-red
  wax seal with a gold stamp pixel; the open mouth (seen once the flap is torn away) e2 with an e4 lip;
- the brass ring round the lid and the swivel b3 with b4 tops and darks under; the cradle a bright steel platter;
- the tag manila (bone o2, o1 foot and edges) lettered "P45" in r1 in a 3 x 5 font on both faces, with a dark punched
  hole where the cord goes through.
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
sys.path.insert(0, os.path.join(ROOT, 'art', 'mecha', 'hopper', 'paint'))
from paintlib import SIDES, Sheet  # noqa: E402

BBMODEL = os.path.join(ROOT, 'art', 'entity', 'golden_parachute', 'golden_parachute.bbmodel')
PLACEHOLDER = os.path.join(ROOT, 'art', 'entity', 'golden_parachute', 'golden_parachute_placeholder.png')
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'headinajar', 'textures', 'entity')

S = Sheet(BBMODEL)
F = S.face
CROWN = 8.0
FONT = {'P': ['111', '101', '111', '100', '100'], '4': ['101', '101', '111', '001', '001'],
        '5': ['111', '100', '111', '001', '111']}


def weave(x, z):
    """The brocade's quiet repeat: a diamond lattice of single threads, 6 px a lozenge, laid in world x/z (so the
    four panels, the crown and the pilot chute share one weave)."""
    ix, iz = int(x + 96), int(z + 96)
    return (ix + iz) % 6 == 0 or (ix - iz) % 6 == 0


def seam(name, x, z):
    """The row of a panel nearest its hinge on the crown."""
    if name in ('panel_f', 'panel_b'):
        return abs(z) < CROWN + 1
    if name in ('panel_l', 'panel_r'):
        return abs(x) < CROWN + 1
    return False


def cloth(name):
    f = F(name, 'up')
    for c, r in f.each():
        x, y, z = f.world(c, r)
        f.put(c, r, 'b2' if seam(name, x, z) else ('b4' if weave(x, z) else 'b3'))
    f = F(name, 'down')                                  # the under-surface: a step darker, the weave in b1
    for c, r in f.each():
        x, y, z = f.world(c, r)
        f.put(c, r, 'b1' if (seam(name, x, z) or weave(x, z)) else 'b2')
    for f in S.faces(name, SIDES):
        for c, r in f.each():
            f.put(c, r, 'b4' if r == 0 else 'b2')


def red(name, top='f2', field='f1', foot='f0'):
    for f in S.faces(name, SIDES):
        for c, r in f.each():
            k = field
            if f.H >= 2 and r == 0:
                k = top
            elif f.H >= 3 and r == f.H - 1:
                k = foot
            f.put(c, r, k)
    F(name, 'up').fill(top)
    F(name, 'down').fill(foot)


def small(name, m, field, top=None, under=None):
    for f in S.faces(name, SIDES):
        f.fill(m + str(field))
    F(name, 'up').fill(top or m + str(min(field + 1, 5)))
    F(name, 'down').fill(under or m + str(max(field - 1, 0)))


def cord(name):
    """White and thin, one dark edge (east) so it holds against a pale sky."""
    for side, k in (('north', 'e5'), ('west', 'e5'), ('south', 'e4'), ('east', 's2')):
        F(name, side).fill(k)
    F(name, 'up').fill('e5')
    F(name, 'down').fill('e4')


def letter_tag():
    for f in S.faces('tag', SIDES + ('up', 'down')):
        f.fill('o1')
    for side in ('north', 'south'):
        f = F('tag', side)
        for c, r in f.each():
            f.put(c, r, 'o1' if r == f.H - 1 else 'o2')
        for i, ch in enumerate('P45'):                  # "P45" in a 3 x 5 font, rows 1-5, from the viewer's left
            for rr, line in enumerate(FONT[ch]):
                for cc, bit in enumerate(line):
                    if bit == '1':
                        f.put(i * 4 + cc, 1 + rr, 'r1')
        mid = f.W // 2
        f.put(mid - 1, 0, 'r0')                         # the punched hole the cord goes through
        f.put(mid, 0, 'r0')
    small('tag_cord', 'e', 4)


def envelope():
    for f in S.faces('pack', SIDES):
        for c, r in f.each():
            f.put(c, r, 'e5' if r == 0 else ('e3' if r == f.H - 1 else 'e4'))
    f = F('pack', 'north')                               # the envelope's flap folds: a V in e2
    for c in range(f.W):
        r = min(c, f.W - 1 - c) * (f.H - 1) // max(1, (f.W - 1) // 2)
        if 0 < r < f.H:
            f.put(c, r, 'e2')
    f = F('pack', 'up')                                  # the open mouth, seen once the flap is gone
    for c, r in f.each():
        f.put(c, r, 'e4' if (c in (0, f.W - 1) or r in (0, f.H - 1)) else 'e2')
    F('pack', 'down').fill('e2')
    for f in S.faces('flap', SIDES):                     # the flap: ivory, its front edge torn (teeth)
        f.fill('e4')
    f = F('flap', 'north')
    for c, r in f.each():
        f.put(c, r, 'e5' if c % 2 == 0 else 'e2')
    F('flap', 'up').fill('e5')
    F('flap', 'down').fill('e3')
    red('seal')                                          # the company seal: deep-red wax, a gold stamp pixel
    f = F('seal', 'north')
    f.rows(['f2 f1', 'f1 f0'])
    f.put(0, 0, 'b4')


def paint_all():
    for n in ('panel_f', 'panel_b', 'panel_l', 'panel_r', 'crown_f', 'crown_b', 'crown_l', 'crown_r'):
        cloth(n)
    for n in ('hem_f', 'hem_b', 'hem_l', 'hem_r'):     # the valance: the dark band under the gold (no lit row)
        red(n, top='f1')
        F(n, 'down').fill('f0')
    for n in ('rosette_f', 'rosette_b', 'rosette_l', 'rosette_r', 'pilot_rim'):
        red(n)
    for s in ('l', 'r'):
        for e in ('f', 'b'):
            small('tassel_%s_%s' % (s, e), 'b', 4, 'b5', 'b2')
    cloth_small = ('pilot_dome',)
    for n in cloth_small:                                # the pilot chute: the same brocade, small
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, 'b4' if r == 0 else 'b3')
        f = F(n, 'up')
        for c, r in f.each():
            x, y, z = f.world(c, r)
            f.put(c, r, 'b4' if weave(x, z) else 'b3')
        F(n, 'down').fill('b2')
    for cn in ('fl', 'fr', 'rl', 'rr'):
        cord('cord_' + cn)
        for f in S.faces('strap_' + cn, ('north', 'west')):
            f.fill('f1')
        for f in S.faces('strap_' + cn, ('south', 'east')):
            f.fill('f0')
        F('strap_' + cn, 'up').fill('f2')
        F('strap_' + cn, 'down').fill('f0')
    cord('bridle')
    for n in ('ring_f', 'ring_b', 'ring_l', 'ring_r'):
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, 'b4' if r == 0 else 'b3')
        F(n, 'up').fill('b4')
        F(n, 'down').fill('b1')
    small('swivel', 'b', 3, 'b4', 'b1')
    for f in S.faces('cradle', SIDES):                   # the silver platter
        f.fill('s4')
    f = F('cradle', 'up')
    for c, r in f.each():
        f.put(c, r, 's5' if (c in (0, f.W - 1) or r in (0, f.H - 1)) else 's4')
    F('cradle', 'down').fill('s2')
    envelope()
    letter_tag()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview')
    ap.add_argument('--out', default=OUT)
    a = ap.parse_args()
    paint_all()
    miss = S.missing()
    if miss:
        sys.stderr.write('unpainted faces: %s\n' % miss)
        return 1
    os.makedirs(a.out, exist_ok=True)
    S.img.save(os.path.join(a.out, 'golden_parachute.png'))
    if a.preview:
        from PIL import Image
        ph = Image.open(PLACEHOLDER).convert('RGBA').load()
        pv = S.img.copy()
        p = pv.load()
        for n in S.cubes:
            if n.startswith('standin'):
                for f in S.faces(n):
                    for c, r in f.each():
                        p[f.x0 + c, f.y0 + r] = ph[f.x0 + c, f.y0 + r]
        pv.save(os.path.join(a.preview, 'golden_parachute_preview.png'))
    print('wrote golden_parachute.png to %s (no glow)' % a.out)
    return 0


if __name__ == '__main__':
    sys.exit(main())

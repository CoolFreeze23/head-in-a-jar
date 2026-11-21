"""The Redundancy Package: the painted sheet, every pixel placed from palette keys.

Source of `textures/entity/redundancy_package.png` (to be adopted as `painted`), the one sheet both models use (the
Compact copy keeps every island the wide one has and adds its own envelope, flap and ties in free space). No randomness
and no glow.

    python -I art/entity/redundancy_package/paint/paint_redundancy_package.py                # writes the sheet
    python -I art/entity/redundancy_package/paint/paint_redundancy_package.py --check        # no writes: compares
    python -I art/entity/redundancy_package/paint/paint_redundancy_package.py --out DIR      # writes elsewhere

Value plan: the Golden Parachute's (paint_golden_parachute.py), at half size, so the two read as one company's issue:
- the canopy is gold brocade: b3 field with a quiet b4 diamond lattice, 6 px lozenges as on the parachute (4 px ones
  read as a checkerboard at this size), laid in world x/z so the panels and the crown share one weave; a b2 seam row where a
  panel hinges on the crown; the under-surface a step darker (b2, b1 weave and seam); the 1 px edges b4;
- the valance, rosette, pilot rim, ties, straps and seal in the flesh ramp's deep red, the valance f1 with an f0 foot,
  no lit row, so it stays the dark band under the gold;
- tassels gold; cords off-white (a step duller than the parachute's) with one dark edge; the envelope ivory with its flap's V in e2, the flap's front edge torn
  (e5/e2 teeth), the open mouth e2 with an e4 lip; the wax seal deep red with a gold stamp pixel;
- the lid's lugs brass (b3, b4 tops, b1 under), as the collar's bayonet lugs; the swivel brass;
- the address label manila (m4 field, m2 edges) with a red postmark (two pixels) and one ruled ink line.
"""
import argparse
import io
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
sys.path.insert(0, os.path.join(ROOT, 'art', 'mecha', 'hopper', 'paint'))
from paintlib import SIDES, Sheet  # noqa: E402

DIR = os.path.join(ROOT, 'art', 'entity', 'redundancy_package')
WIDE = os.path.join(DIR, 'redundancy_package.bbmodel')
COMPACT = os.path.join(DIR, 'redundancy_package_compact.bbmodel')
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'headinajar', 'textures', 'entity')
NAME = 'redundancy_package.png'
CROWN = 4.0


def weave(x, z):
    """The brocade's quiet repeat: a diamond lattice of single threads, 6 px a lozenge, in world x/z (the parachute's)."""
    ix, iz = int(x + 96), int(z + 96)
    return (ix + iz) % 6 == 0 or (ix - iz) % 6 == 0


def seam(name, x, z):
    """The row of a panel nearest its hinge on the crown."""
    if name in ('panel_f', 'panel_b'):
        return abs(z) < CROWN + 1
    if name in ('panel_l', 'panel_r'):
        return abs(x) < CROWN + 1
    return False


class Painter:
    def __init__(self, sheet):
        self.S = sheet
        self.F = sheet.face

    def cloth(self, name):
        S, F = self.S, self.F
        f = F(name, 'up')
        for c, r in f.each():
            x, y, z = f.world(c, r)
            f.put(c, r, 'b2' if seam(name, x, z) else ('b4' if weave(x, z) else 'b3'))
        f = F(name, 'down')
        for c, r in f.each():
            x, y, z = f.world(c, r)
            f.put(c, r, 'b1' if (seam(name, x, z) or weave(x, z)) else 'b2')
        for f in S.faces(name, SIDES):
            f.fill('b4')

    def red(self, name, top='f2', field='f1', foot='f0'):
        S, F = self.S, self.F
        for f in S.faces(name, SIDES):
            for c, r in f.each():
                k = field
                if f.H >= 2 and r == 0:
                    k = top
                elif f.H >= 2 and r == f.H - 1:
                    k = foot
                f.put(c, r, k)
        F(name, 'up').fill(top)
        F(name, 'down').fill(foot)

    def small(self, name, m, field, top=None, under=None):
        S, F = self.S, self.F
        for f in S.faces(name, SIDES):
            f.fill(m + str(field))
        F(name, 'up').fill(top or m + str(min(field + 1, 5)))
        F(name, 'down').fill(under or m + str(max(field - 1, 0)))

    def cord(self, name):
        """A step duller than the parachute's (e4 and e3, not e5 and e4): at half size the 1 px cords are twice as thick
        beside the canopy, and in white they read as struts."""
        F = self.F
        for side, k in (('north', 'e4'), ('west', 'e4'), ('south', 'e3'), ('east', 's2')):
            F(name, side).fill(k)
        F(name, 'up').fill('e4')
        F(name, 'down').fill('e3')

    def strap(self, name):
        """Crimson ribbon: lit on the north and west, a step down on the south and east."""
        S, F = self.S, self.F
        for f in S.faces(name, ('north', 'west')):
            f.fill('f1')
        for f in S.faces(name, ('south', 'east')):
            f.fill('f0')
        F(name, 'up').fill('f2')
        F(name, 'down').fill('f0')

    def envelope(self):
        S, F = self.S, self.F
        for f in S.faces('pack', SIDES):
            for c, r in f.each():
                f.put(c, r, 'e5' if r == 0 else ('e3' if r == f.H - 1 else 'e4'))
        f = F('pack', 'north')                           # the envelope's flap folds: a V in e2 under its top row
        mid = (f.W - 1) / 2.0
        for c in range(f.W):
            if abs(c - mid) <= 1:
                f.put(c, 1, 'e2')
            elif abs(c - mid) <= 2:
                f.put(c, 0, 'e3')
        f = F('pack', 'up')                              # the open mouth, seen once the flap is gone
        for c, r in f.each():
            f.put(c, r, 'e4' if (c in (0, f.W - 1) or r in (0, f.H - 1)) else 'e2')
        F('pack', 'down').fill('e2')
        for f in S.faces('flap', SIDES):                 # the flap: ivory, its front edge torn (teeth)
            f.fill('e4')
        f = F('flap', 'north')
        for c, r in f.each():
            f.put(c, r, 'e5' if c % 2 == 0 else 'e2')
        f = F('flap', 'up')
        for c, r in f.each():
            f.put(c, r, 'e4' if r == f.H - 1 else 'e5')  # a shadow along the hinge at the back
        F('flap', 'down').fill('e3')

    def seal(self):
        self.red('seal')                                 # the company seal: deep-red wax, a gold stamp pixel
        f = self.F('seal', 'north')
        f.rows(['f2 f2 f1', 'f1 f1 f0'])
        f.put(1, 0, 'b4')

    def label(self):
        S, F = self.S, self.F
        for f in S.faces('tag', SIDES + ('up', 'down')):
            f.fill('m2')
        for side in ('north', 'south'):
            f = F('tag', side)
            f.rows(['m4 m4 m5 m4',
                    'm4 d1 d2 m4',
                    'k1 k1 m4 m3'])
        self.small('tag_cord', 'e', 4)

    def paint_wide(self):
        S, F = self.S, self.F
        for n in ('panel_f', 'panel_b', 'panel_l', 'panel_r', 'crown_f', 'crown_b', 'crown_l', 'crown_r'):
            self.cloth(n)
        for n in ('hem_f', 'hem_b', 'hem_l', 'hem_r'):
            self.red(n, top='f1')
            F(n, 'down').fill('f0')
        for n in ('rosette_f', 'rosette_b', 'rosette_l', 'rosette_r', 'pilot_rim'):
            self.red(n)
        for s in ('l', 'r'):
            for e in ('f', 'b'):
                self.small('tassel_%s_%s' % (s, e), 'b', 4, 'b5', 'b2')
        for f in S.faces('pilot_dome', SIDES):
            f.fill('b3')
        f = F('pilot_dome', 'up')
        for c, r in f.each():
            x, y, z = f.world(c, r)
            f.put(c, r, 'b4' if weave(x, z) else 'b3')
        F('pilot_dome', 'down').fill('b2')
        for cn in ('fl', 'fr', 'rl', 'rr'):
            self.cord('cord_' + cn)
        self.cord('bridle')
        self.harness()
        self.small('swivel', 'b', 3, 'b4', 'b1')
        self.envelope()
        self.seal()
        self.label()

    def harness(self):
        S, F = self.S, self.F
        for side in 'fblr':
            n = 'lug_' + side
            for f in S.faces(n, SIDES):
                for c, r in f.each():
                    f.put(c, r, 'b4' if r == 0 else 'b2')
            F(n, 'up').fill('b4')
            F(n, 'down').fill('b1')
            self.strap('tie_' + side)
            self.strap('strap_' + side)

    def paint_compact_extra(self):
        """The Compact copy's own islands (its envelope and ties are a pixel narrower)."""
        for side in 'fblr':
            self.strap('tie_' + side)
        self.envelope()


def paint():
    wide = Sheet(WIDE)
    Painter(wide).paint_wide()
    comp = Sheet(COMPACT)
    comp.img, comp.px = wide.img, wide.px
    comp.glow, comp.gx = wide.glow, wide.gx
    Painter(comp).paint_compact_extra()
    miss = wide.missing() + comp.missing()
    if miss:
        sys.exit('unpainted faces: %s' % miss)
    return wide.img


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--out', default=OUT)
    ap.add_argument('--check', action='store_true')
    a = ap.parse_args()
    img = paint()
    path = os.path.join(a.out, NAME)
    if a.check:
        buf = io.BytesIO()
        img.save(buf, 'PNG')
        with open(path, 'rb') as fh:
            same = fh.read() == buf.getvalue()
        print('%s %s' % (path, 'matches' if same else 'DIFFERS'))
        return 0 if same else 1
    os.makedirs(a.out, exist_ok=True)
    img.save(path)
    print('wrote %s (no glow)' % path)
    return 0


if __name__ == '__main__':
    sys.exit(main())

"""The three proof modules (drill, jump jets, hydraulic legs): painted sheets, every pixel placed from palette keys.

Source of `textures/entity/mecha/module/<name>.png` (and `jump_jets_glowmask.png`), adopted as `painted` in the
texture manifest. Same constructions and texel size (16 px to a block) as the Hopper's sheet; one 64x64 sheet per
module, because each module's .bbmodel packs its own islands from (0, 0) and the three layouts overlap (a shared
sheet would need a repack of all three).

    python -I art/mecha/modules/paint/paint_modules.py [--out DIR]
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'hopper', 'paint'))
from paintlib import ROOT, SIDES, Sheet  # noqa: E402

MOD = os.path.join(ROOT, 'art', 'mecha', 'modules')
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'headinajar', 'textures', 'entity', 'mecha', 'module')
AROUND_Z = ('east', 'west', 'up', 'down')       # the faces round a part whose axis runs along z


def rivet(f, c, r, lit, dark):
    f.put(c, r, lit)
    f.put(c + 1, r + 1, dark)


# ===================================================================================================== drill
def drill(S):
    F = S.face
    for f in S.faces('neck'):                           # the dark step where the drill meets the cuff
        f.fill('r0')
    # rubber mounting collar
    for f in S.faces('collar'):
        f.fill('r1')
    for f in S.faces('collar', AROUND_Z):
        for c, r in f.each():
            x, y, z = f.world(c, r)
            f.put(c, r, 'r2' if f.side == 'up' else 'r1')
    # motor housing: an enamel can in the house frame: one flat field, the top arris lit, a 1 px dark rim where it
    # meets the collar and the nose (its two ends along the axis), the underside dark
    for f in S.faces('motor', AROUND_Z):
        for c, r in f.each():
            x, y, z = f.world(c, r)
            k = 'e1'                                    # secondary enamel: a step under the pod
            if f.side == 'up':
                k = 'e2'
            elif f.side == 'down':
                k = 'e0'
            elif y > 2.9:
                k = 'e3'                                # the lit top edge
            if z < -7.4 or z > -3.6:
                k = 'e0' if f.side != 'up' else 'e1'    # dark rims at both ends of the can
            f.put(c, r, k)
    for f in S.faces('motor', ('north', 'south')):
        for c, r in f.each():
            x, y, z = f.world(c, r)
            f.put(c, r, 'e2' if max(abs(x), abs(y)) > 2.6 else 's2')
    # brass clamp band
    for f in S.faces('band'):
        for c, r in f.each():
            x, y, z = f.world(c, r)
            if f.side == 'up':
                k = 'b4' if z < -5 else 'b3'            # the leading edge carries the light
            elif f.side == 'down':
                k = 'b0'
            elif f.side in ('east', 'west'):
                k = 'b4' if y > 2.9 else ('b1' if y < -2.9 else 'b3')
                if abs(y - (-z - 4.5) + 1) < 0.6 or abs(y - (-z - 4.5)) < 0.6:
                    k = 'b4' if y <= 2.9 else k         # the diagonal polish band
            else:
                k = 'b2'
            f.put(c, r, k)
    F('band', 'east').put(0, 3, 's4')                   # the band's clamp bolt, on the right
    F('band', 'east').put(1, 4, 's1')
    # cooling vent on top of the can: three slots across
    for f in S.faces('vent'):
        f.fill('s1')
    f = F('vent', 'up')
    for c, r in f.each():
        f.put(c, r, 'r0' if r in (0, 2) else 's3')
    # chuck and auger
    for f in S.faces('chuck'):                          # the chuck is the recessed waist between motor and bit
        f.fill('s1')
    F('chuck', 'up').fill('s2')
    F('chuck', 'down').fill('s0')
    for f in S.faces('chuck', ('east', 'west')):
        f.put(0, 1, 's4')                               # chuck key holes
        f.put(1, 2, 's0')
    # the bit: polished steel stages; each has a hard bright cutting lip on its tip-side edge (s5) and a field of s4
    # (s3 underneath), crossed by one 1 px flute (s1) that runs diagonally across every face and on round the next, so
    # the stages carry a continuous spiral that turns when the bit spins
    order = ('up', 'west', 'down', 'east')
    for n in ('auger_1', 'auger_2', 'auger_tip'):
        z0 = S.cubes[n]['frm'][2]
        for i, side in enumerate(order):
            f = F(n, side)
            span = f.W if side in ('up', 'down') else f.H
            for c, r in f.each():
                x, y, z = f.world(c, r)
                dz = int(z - z0)                       # 0 at the tip-side edge
                t = {'up': span - 1 - c, 'down': c, 'west': r, 'east': span - 1 - r}[side]
                k = 's2' if side == 'down' else 's3'
                if dz == 0:
                    k = 's5' if side != 'down' else 's4'
                ph = (t - dz) % span
                if span >= 3 and dz > 0 and ph == 0:
                    k = 's0'                            # the flute
                elif span >= 3 and dz > 0 and ph == 1:
                    k = 's1'                            # its far wall: a 2 px groove
                elif span >= 3 and dz > 0 and ph == span - 1 and side != 'down':
                    k = 's5'                            # the land's lit edge ahead of it
                f.put(c, r, k)
        for f in S.faces(n, ('north', 'south')):
            f.fill('s3')
    F('auger_tip', 'north').rows(['s5 s4', 's4 s2'])


# ===================================================================================================== jump jets
def jump_jets(S):
    F = S.face
    for f in S.faces('flange'):                         # the dark step at the socket
        f.fill('r0')
    # manifold: dark steel, four bolts, a black feed in the middle
    for f in S.faces('manifold'):
        f.fill('s1')
    F('manifold', 'up').fill('s2')
    F('manifold', 'down').fill('s0')
    F('manifold', 'north').rows(['s2 s2 s2 s2 s2 s2 s2 s2',
                                 's2 s3 s1 s1 s1 s1 s3 s1',
                                 's1 s0 s1 r0 r0 s1 s0 s1',
                                 's1 s3 s1 r0 r0 s1 s3 s1',
                                 's0 s0 s0 s0 s0 s0 s0 s0'])
    # lamp rail: brass, its edge lit
    for f in S.faces('lamp_rail'):
        f.fill('b3')
    f = F('lamp_rail', 'up')
    for c, r in f.each():
        f.put(c, r, 'b4' if r == 1 else 'b3')
    F('lamp_rail', 'down').fill('b0')
    # the canisters: two enamel drums split by a black groove. Each drum: lit lip, e2 field, a dark side rim, e1 foot;
    # the upper drum carries a steel filler cap on top; the groove is the darkest step
    for i in ('0', '1'):
        for n in ('pod_top_' + i, 'pod_low_' + i):
            for f in S.faces(n, SIDES):
                for c, r in f.each():
                    k = 'e3' if r == 0 else ('e0' if r == f.H - 1 else 'e1')   # secondary enamel
                    if c == f.W - 1 and 0 < r < f.H - 1:
                        k = 'e0'
                    f.put(c, r, k)
            F(n, 'down').fill('e0')
        F('pod_top_' + i, 'up').rows(['e4 e4 e4 e3', 'e4 s3 s2 e2', 'e4 s2 r0 e2', 'e3 e2 e2 e1'])
        F('pod_low_' + i, 'up').fill('e1')
        rivet(F('pod_low_' + i, 'north'), 1, 0, 'e4', 'e0')   # the strap rivet
        # the recessed band between the drums: the darkest steel, a lit row along its top under the overhang's shadow
        for f in S.faces('pod_band_' + i):
            f.fill('s0')
        for f in S.faces('pod_band_' + i, SIDES):
            f.row(0, 'r0')
    for n in ('ring_0', 'ring_1'):
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, 'b4' if c == 1 and f.side == 'north' else 'b3')
        F(n, 'up').fill('b4')
        F(n, 'down').fill('b0')
    # nozzle bells: heat-stained steel, a bronze heat tint under the ring, a sooty throat
    for n in ('bell_0', 'bell_1'):
        for f in S.faces(n, SIDES):
            for c, r in f.each():
                f.put(c, r, ('b1', 's1', 's0')[r] if not (r == 0 and c == 0) else 'b2')   # heat-straw ring
        f = F(n, 'down')
        f.rows(['s1 s1 s1 s1', 's1 r0 r0 s0', 's1 r0 r0 s0', 's0 s0 s0 s0'])
        F(n, 'up').fill('s1')
    # pilot lamps: amber bulbs, lit (glowmask); the game hides one per spent burst
    for n in ('pilot_lit_0', 'pilot_lit_1', 'pilot_lit_2'):
        for f in S.faces(n):
            f.put(0, 0, 'A3' if f.side in ('north', 'up') else 'A2', lit=True)


# ===================================================================================================== hydraulic legs
def hydraulic_legs(S):
    F = S.face
    # the ram: a brass cylinder clamped to the thigh, one highlight run on its outward face, darker at its foot
    for f in S.faces('ram', SIDES):
        for c, r in f.each():
            k = 'b3'
            if r == 0:
                k = 'b4'
            elif r >= f.H - 2:
                k = 'b2' if r < f.H - 1 else 'b1'
            f.put(c, r, k)
    f = F('ram', 'north')
    f.col(0, 'b4', 1, 5)                                # the edge light down the cylinder
    f.put(0, 1, 'b5')
    F('ram', 'up').rows(['b3 b4 b3', 'b3 s4 b2'])       # the rod's gland on top
    F('ram', 'down').fill('b0')
    for n in ('ram_clamp_top', 'ram_clamp_low'):
        for f in S.faces(n):
            f.fill('s1')
        F(n, 'up').fill('s2')
        F(n, 'down').fill('s0')
        F(n, 'north').put(0, 0, 's3')                  # the clamp bolt's head
    # hose: ribs every 2 px, a lit line
    for f in S.faces('hose'):
        for c, r in f.each():
            f.put(c, r, 'r3' if r % 2 == 0 else 'r1')
    # damper: polished piston rod, copper can over it, rubber bellows boot below
    for f in S.faces('damper', SIDES):
        f.fill('s3')
        if f.side in ('north', 'west'):
            f.col(0, 's5', 0, f.H)                      # the hard polished band of the rod
            f.put(0, 2, 's4')
        f.row(f.H - 1, 's1')
    F('damper', 'up').fill('s5')
    F('damper', 'down').fill('s2')
    for f in S.faces('damper_can', SIDES):
        for c, r in f.each():
            k = 'c2'
            if r == 0:
                k = 'c5'
            elif r == f.H - 1:
                k = 'c0'
            elif c == f.W - 1:
                k = 'c1'
            f.put(c, r, k)
        if f.side == 'north':
            f.put(0, 1, 'c6')
            f.put(0, 2, 'c4')
        if f.H >= 4:
            f.row(1, 's2')                              # a steel clamp ring round the can
    F('damper_can', 'up').rows(['c6 c6 c5', 'c5 s1 c3'])   # the rod passes through the cap
    F('damper_can', 'down').fill('c1')
    for f in S.faces('damper_boot', SIDES):
        for c, r in f.each():
            f.put(c, r, 'r3' if r == 0 else 'r1')
    F('damper_boot', 'up').fill('r2')
    F('damper_boot', 'down').fill('r0')


PAINTERS = {'drill': drill, 'jump_jets': jump_jets, 'hydraulic_legs': hydraulic_legs}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--out', default=OUT)
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    for name, paint in PAINTERS.items():
        S = Sheet(os.path.join(MOD, name + '.bbmodel'))
        paint(S)
        miss = S.missing()
        if miss:
            sys.stderr.write('%s: unpainted faces: %s\n' % (name, miss))
            return 1
        S.img.save(os.path.join(a.out, name + '.png'))
        if S.glow.getbbox():
            S.glow.save(os.path.join(a.out, name + '_glowmask.png'))
        print('wrote', name)
    return 0


if __name__ == '__main__':
    sys.exit(main())

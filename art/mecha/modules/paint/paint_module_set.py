"""The twenty second-batch modules, light and heavy: forty sheets painted as one set by one script.

Every pixel is placed by a rule from a palette key: a rule per MATERIAL (the house rules) and a rule per WORKING END
(the module's identity accent), nothing random. Cube names, sizes, materials and UVs come from each module's
`.bbmodel` and from `art/mecha/modules/modules_more.py` (read only: imported for its MODULES table, never run).

    python -I art/mecha/modules/paint/paint_module_set.py                 # writes all sheets into the mod assets
    python -I art/mecha/modules/paint/paint_module_set.py --out DIR --preview   # elsewhere, to look at first

Writes, for each module M (and M_heavy), into `textures/entity/mecha/module/`:
- `M.png`: the sheet;
- `M_glowmask.png`: pixels lit ALWAYS (only the flamethrower's pilot flame);
- `M_firing.png`: pixels lit ONLY WHILE FIRING (flames, emitters, the lance's core, the arc cannon's charged balls).
  A mask file is written only when the module has such pixels.

House rules: palette D; brass fields at b3 with darks round them; enamel and steel a step down (e2 / s2); recesses
the darkest step; hazard stripes only on named moving parts 4 px or wider; small parts one tone a face.
Every module shares one MOUNT PLATE construction (dark steel, four lit bolt heads), so it looks fitted.
Heavy sheets get one more level of detail where a face is big enough: a seam and a rivet pair (see `heavy_detail`).
"""
import argparse
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.abspath(os.path.join(HERE, '..'))
ROOT = os.path.abspath(os.path.join(MOD, '..', '..', '..'))
sys.path.insert(0, os.path.join(ROOT, 'art', 'mecha', 'hopper', 'paint'))
sys.path.insert(0, MOD)
from paintlib import COL, SIDES, Sheet, rgba  # noqa: E402
import modules_more  # noqa: E402  (its MODULES table only)
import modules_batch2  # noqa: E402  (its MODULES table only: the third batch)
import modules_batch3  # noqa: E402  (its MODULES table only: the fourth batch)

OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'headinajar', 'textures', 'entity', 'mecha', 'module')
NAMES = list(modules_more.MODULES.keys())
NAMES2 = list(modules_batch2.MODULES.keys())
NAMES3 = list(modules_batch3.MODULES.keys())
TABLE = dict(modules_more.MODULES)
TABLE.update(modules_batch2.MODULES)
TABLE.update(modules_batch3.MODULES)
ONE_SIZE = tuple(modules_batch2.NO_HEAVY) + tuple(modules_batch3.NO_HEAVY)   # sensors, solar skin: no heavy file


class SheetBrush:
    def __init__(self, name, heavy):
        self.name, self.heavy = name, heavy
        path = os.path.join(MOD, name, '%s%s.bbmodel' % (name, '_heavy' if heavy else ''))
        self.S = Sheet(path)
        self.mat = {c[0]: c[3] for c in TABLE[name]['cubes']}
        self.parent = {c[0]: c[4] for c in TABLE[name]['cubes']}
        self.firing = Image.new('RGBA', (self.S.W, self.S.H), (0, 0, 0, 0))
        self.FX = self.firing.load()
        self.charge = Image.new('RGBA', (self.S.W, self.S.H), (0, 0, 0, 0))    # lit by code with the charge level
        self.CX = self.charge.load()

    def F(self, cube, side):
        return self.S.face(cube, side)

    def faces(self, cube, sides=SIDES + ('up', 'down')):
        return self.S.faces(cube, sides)

    def fire(self, f, c, r, key):
        """Paint and mark as lit only while firing."""
        f.put(c, r, key)
        self.FX[f.x0 + c, f.y0 + r] = rgba(key)

    def always(self, f, c, r, key):
        f.put(c, r, key, lit=True)

    def charged(self, f, c, r, key):
        """Paint and mark for the charge mask (the code lights it by the charge level)."""
        f.put(c, r, key)
        self.CX[f.x0 + c, f.y0 + r] = rgba(key)

    def lamp(self, cube, ramp, mode='always', lo=1):
        """A lens or lamp of one colour: field step lo + 1, a step lo + 2 glint in the upper-left pixel of its front
        face, a step lo foot on faces 3+ tall and underneath (lo=2 for the bright five-step violet and cyan glows).
        mode: 'always' (glowmask), 'firing', 'charge', or None."""
        put = {'always': self.always, 'firing': self.fire, 'charge': self.charged,
               None: lambda f, c, r, k: f.put(c, r, k)}[mode]
        for f in self.faces(cube):
            for c, r in f.each():
                k = '%s%d' % (ramp, lo + 1)
                if f.side == 'north' and (c, r) == (0, 0):
                    k = '%s%d' % (ramp, lo + 2)
                elif (f.side in SIDES and f.H >= 3 and r == f.H - 1) or f.side == 'down':
                    k = '%s%d' % (ramp, lo)
                put(f, c, r, k)

    # ------------------------------------------------------------------------------------------- material rules
    def body(self, cube, field, lit, dark, top=None, under=None):
        """The generic part: one field per side face, a lit top row, a dark last row on faces 3+ tall; top face lit
        (or `top`), underside dark. Faces under 3 px tall stay one tone (small-part rule)."""
        for f in self.faces(cube, SIDES):
            for c, r in f.each():
                k = field
                if f.H >= 2 and r == 0:
                    k = lit
                elif f.H >= 3 and r == f.H - 1:
                    k = dark
                f.put(c, r, k)
        for f in self.faces(cube, ('up',)):
            f.fill(top or lit)
        for f in self.faces(cube, ('down',)):
            f.fill(under or dark)

    def enamel(self, cube):
        self.body(cube, 'e2', 'e3', 'e1', 'e3', 'e1')

    def brass(self, cube):
        self.body(cube, 'b3', 'b4', 'b1', 'b3', 'b1')
        for f in self.faces(cube, ('up',)):            # top: b3 field, b4 along the lit north and west edges
            x0, _, z0 = self.S.cubes[cube]['frm']
            for c, r in f.each():
                x, y, z = f.world(c, r)
                f.put(c, r, 'b4' if (z < z0 + 1 or x < x0 + 1) else 'b3')
        for f in self.faces(cube, SIDES):              # end columns a step darker on faces 4+ wide
            if f.W >= 4 and f.H >= 2:
                for r in range(1, f.H - (1 if f.H >= 3 else 0)):
                    f.put(0, r, 'b2')
                    f.put(f.W - 1, r, 'b2')

    def steel(self, cube):
        self.body(cube, 's2', 's3', 's1', 's3', 's1')

    def dsteel(self, cube):
        self.body(cube, 's1', 's2', 's0', 's2', 's0')

    def bright(self, cube):
        self.body(cube, 's4', 's5', 's3', 's5', 's2')

    def rubber(self, cube):
        self.body(cube, 'r1', 'r2', 'r0', 'r2', 'r0')

    def copper(self, cube):
        """Copper (vanilla's ramp): c3 field, c6 lit row, c1 foot (coils are drawn by the working-end rules)."""
        self.body(cube, 'c3', 'c6', 'c1', 'c5', 'c1')

    def yellow(self, cube):
        self.body(cube, 'w2', 'w3', 'w1', 'w3', 'w0')

    def lens(self, cube):
        for f in self.faces(cube):
            f.fill('r0')

    def stripes(self, cube):
        """Hazard stripes, the house rule: 45 degrees, 2 px w2 / 2 px r1 along the side strip, first row lighter."""
        for f in self.faces(cube, SIDES):
            if f.W < 4:
                f.fill('w1')
                continue
            for c, r in f.each():
                y = (f.strip0 + c + r) % 4 < 2
                f.put(c, r, ('w3' if y else 'r2') if r == 0 else ('w2' if y else 'r1'))
        for f in self.faces(cube, ('up',)):
            for c, r in f.each():
                f.put(c, r, 'w3' if (c + r) % 4 < 2 else 'r2')
        for f in self.faces(cube, ('down',)):
            f.fill('r0')

    def glass(self, cube):
        """Clear cutout glass: a 1 px frame in vanilla glass's two lightest steps, the panes clear."""
        for f in self.faces(cube, SIDES):
            for c, r in f.each():
                if r == 0 or c == 0:
                    f.put(c, r, 'g3')
                elif r == f.H - 1 or c == f.W - 1:
                    f.put(c, r, 'g2')
                else:
                    self.S.px[f.x0 + c, f.y0 + r] = (0, 0, 0, 0)
        for f in self.faces(cube, ('up', 'down')):
            f.fill('g2')

    def fill_tint(self, cube):
        """A tintable fluid: neutral light (the Executive's serum base): s5 body, e5 meniscus row, s4 foot."""
        for f in self.faces(cube):
            for c, r in f.each():
                f.put(c, r, 'e5' if (r == 0 or f.side == 'up') else ('s4' if r >= f.H - 1 else 's5'))

    def flame(self, cube, core, body, edge, z_core=None, z_edge=None):
        """A flame (geometry): opaque, flat colour bands by distance along the flame, no frames and no gradient mush:
        the root (nearest the nozzle, z above `z_core`) the bright core colour, the middle the body colour, the far
        tip (z below `z_edge`) the hot outer colour. Lit only while firing."""
        for f in self.faces(cube):
            for c, r in f.each():
                x, y, z = f.world(c, r)
                k = body
                if z_core is not None and z > z_core:
                    k = core
                if z_edge is not None and z < z_edge:
                    k = edge
                self.fire(f, c, r, k)

    def emitter(self, cube, always=False):
        """A cyan-white emitter (teal 4 core, glass 3 rim, teal 3 between): lit while firing (or always)."""
        for f in self.faces(cube):
            for c, r in f.each():
                k = 't3'
                if c in (0, f.W - 1) or r in (0, f.H - 1):
                    k = 'g3'
                if f.W >= 3 and f.H >= 3 and 0 < c < f.W - 1 and 0 < r < f.H - 1:
                    k = 't4'
                if always:
                    self.always(f, c, r, k)
                else:
                    self.fire(f, c, r, k)

    def plate(self):
        """THE MOUNT PLATE, the same on every module: dark steel, its exposed faces s1 with an s2 top row and an s0
        foot; on its front face four bolt heads (lit s3, shadow s0) one in from each corner."""
        cube = 'plate'
        if cube not in self.S.cubes:                    # a legs dressing has no plate: it sits on the leg
            return
        self.dsteel(cube)
        f = self.F(cube, 'north')
        i = 1 if f.W >= 6 else 0
        for (c, r) in ((i, i), (f.W - 1 - i, i), (i, f.H - 1 - i), (f.W - 1 - i, f.H - 1 - i)):
            f.put(c, r, 's3')
            if self.heavy and c + 1 < f.W and r + 1 < f.H:
                f.put(c + 1, r + 1, 's0')

    def heavy_detail(self, cube):
        """Heavy sheets only: on a side face 10+ wide and 6+ tall (not glass, flame or plate), one seam a step darker
        across the middle row and a rivet pair (field + 2, shadow) under the lit row at both ends."""
        if not self.heavy:
            return
        m = self.mat.get(cube)
        ramp = {'enamel': ('e', 2), 'steel': ('s', 2), 'dsteel': ('s', 1), 'yellow': ('w', 2), 'brass': ('b', 3)}
        if m not in ramp:
            return
        a, fld = ramp[m]
        for f in self.faces(cube, SIDES):
            if f.W >= 10 and f.H >= 6:
                f.row(f.H // 2, '%s%d' % (a, max(0, fld - 1)), 1, f.W - 1)
                for c in (2, f.W - 3):
                    f.put(c, 1 if f.H > 6 else 1, '%s%d' % (a, min(fld + 2, 5 if a in 'esb' else 3)))

    # ------------------------------------------------------------------------------------------- one module
    def paint(self):
        rule = {'enamel': self.enamel, 'brass': self.brass, 'steel': self.steel, 'dsteel': self.dsteel,
                'bright': self.bright, 'rubber': self.rubber, 'copper': self.copper, 'yellow': self.yellow,
                'lens': self.lens, 'vial': self.glass, 'blood': self.fill_tint,
                'lamp_a': lambda n: None, 'lamp_b': lambda n: None, 'lamp_g': lambda n: None,
                'lamp_r': lambda n: None, 'lamp_e': lambda n: None, 'lamp_v': lambda n: None,
                'lamp_o': lambda n: None,
                # fourth batch paints. Palette D has no blue: blue is the glow-cyan ramp (y)
                'red': lambda n: self.body(n, 'R1', 'R2', 'R0', 'R1', 'R0'),
                'white': lambda n: self.body(n, 'e5', 'e5', 'e4', 'e5', 'e3'),
                'green': lambda n: self.body(n, 'j2', 'j3', 'j1', 'j3', 'j1'),
                'blue': lambda n: self.body(n, 'y2', 'y3', 'y1', 'y3', 'y1'),
                'cell': lambda n: self.body(n, 't0', 't1', 't0', 't1', 'r1'),
                'serum': self.fill_tint}
        for cube in self.S.cubes:
            rule[self.mat[cube]](cube)
            self.heavy_detail(cube)
        self.plate()
        getattr(self, 'end_' + self.name)()

    # ------------------------------------------------------------------------------------------- working ends
    def end_chainsaw(self):
        # bright teeth on a dark bar; the engine is the yellow identity block
        for n in ('bar', 'nose'):
            self.body(n, 's1', 's2', 's0', 's2', 's0')
        for i in range(5):
            self.body('tooth_%d' % i, 's5', 's5', 's4', 's5', 's4')
        F = self.F
        F('engine', 'east').put(1, 1, 'r1')            # the pull-cord's slot

    def end_hydraulic_fist(self):
        self.body('knuckles', 'b4', 'b5', 'b2', 'b5', 'b1')      # the brass knuckle plate, the bright accent
        self.body('fist', 's2', 's3', 's1', 's3', 's1')

    def end_grapple_hook(self):
        for n in ('tine_u', 'tine_u_tip', 'tine_l', 'tine_l_tip', 'tine_r', 'tine_r_tip'):
            self.body(n, 'b4', 'b5', 'b2', 'b5', 'b2')        # the grapnel: bright brass tines
        for f in self.faces('reel', SIDES):             # the wound line on the copper reel
            for c, r in f.each():
                f.put(c, r, 'c6' if r % 2 == 0 else 'c2')

    def end_tractor_beam(self):
        self.emitter('emitter')
        self.body('dish_back', 's0', 's1', 's0', 's1', 's0')     # the dish's dark bowl round the emitter
        for f in self.faces('coil', SIDES):
            for c, r in f.each():
                f.put(c, r, 'c6' if r % 2 == 0 else 'c2')

    def end_rivet_gun(self):
        for side in ('east', 'west'):                  # rivets showing in the drum's ends
            f = self.F('drum_cap', side)
            for c, r in f.each():
                if (c + r) % 2 == 0 and 0 < c < f.W - 1 and 0 < r < f.H - 1:
                    f.put(c, r, 's4')
        self.body('muzzle', 's0', 's1', 's0', 's1', 's0')
        f = self.F('muzzle', 'north')
        f.fill('s1')
        f.put(f.W // 2, f.H // 2, 'r0')                 # the bore

    def end_riot_shield(self):
        F = self.F
        f = F('face', 'north')                          # the big enamel face, lighter than the house field
        for c, r in f.each():
            k = 'e3'
            if r == 0:
                k = 'e4'
            elif r == f.H - 1:
                k = 'e1'
            elif c in (0, f.W - 1):
                k = 'e2'
            f.put(c, r, k)
        # the company chevron (teal, the Kite's livery colour), pointing up, between the window and the stripe
        cx = f.W // 2
        top = f.H * 7 // 16
        thick = 2 if self.heavy else 1
        for i in range(f.W // 2 - 1):
            for t in range(thick):
                r = top + i + t
                if r < f.H * 11 // 16:
                    f.put(cx - 1 - i, r, 't2')
                    f.put(cx + i, r, 't2')
        self.stripes('stripe')
        for n in ('rim_t', 'rim_b'):
            self.steel(n)

    def end_manipulator_hand(self):
        for n in ('tip_tl', 'tip_tr', 'tip_b'):
            self.body(n, 'r1', 'r2', 'r0', 'r2', 'r0')    # rubber grip pads
        for n in ('finger_tl', 'finger_tr', 'thumb'):
            self.body(n, 's3', 's4', 's2', 's4', 's2')

    def end_excavator_scoop(self):
        self.body('teeth', 's5', 's5', 's3', 's5', 's3')         # the lip worn bright
        f = self.F('floor', 'north')
        f.row(0, 's4')                                  # the floor's leading edge worn to bare steel
        for n in ('back', 'side_l', 'side_r'):
            for f in self.faces(n, ('north',)):
                f.row(f.H - 1, 'w0')                    # dirt line inside the bucket

    def end_flamethrower(self):
        self.body('bell', 's0', 'b1', 'r0', 's1', 'r0')          # scorched dark, a heat-tint ring
        f = self.F('bell', 'north')
        f.fill('r0')                                    # the sooty throat
        for f in self.faces('pilot_flame'):
            f.fill('A2')
            for c, r in f.each():
                self.always(f, c, r, 'A3' if f.side in ('north', 'up') else 'A2')
        h = 2 if self.heavy else 1                      # coordinates double on the heavy class
        self.flame('flame_a', 'A3', 'A3', 'A2', z_core=-20 * h, z_edge=-21 * h)
        self.flame('flame_b', 'A2', 'A2', 'R2', z_core=-23 * h, z_edge=-25 * h)

    def end_arc_cannon(self):
        for n in ('prong_lc', 'prong_rc', 'ring_a', 'ring_b'):
            for f in self.faces(n, SIDES):
                for c, r in f.each():
                    if n.startswith('prong'):
                        x, y, z = f.world(c, r)
                        f.put(c, r, 'c6' if int(abs(z)) % 2 == 0 else 'c2')   # coil turns along the prong
                    else:
                        f.put(c, r, 'c6' if r == 0 else 'c3')
        for n in ('ball_l', 'ball_r'):                  # the charged terminals: lit while firing
            for f in self.faces(n):
                for c, r in f.each():
                    self.fire(f, c, r, 't4' if (c, r) == (0, 0) else 't3')

    def end_hemo_syringe(self):
        self.body('needle_pin', 's5', 's5', 's4', 's5', 's4')
        self.body('plunger_head', 'r1', 'r2', 'r0', 'r2', 'r0')

    def end_harvester(self):
        for n in ('tine_a', 'tine_c'):
            self.body(n, 'b4', 'b5', 'b2', 'b5', 'b2')
        for n in ('bat_a', 'bat_b', 'bat_c'):
            self.body(n, 's3', 's4', 's1', 's4', 's1')

    def end_welding_torch(self):
        self.body('tip', 'b2', 'b3', 'r0', 'b3', 'r0')
        f_lo = self.S.cubes['flame_core']['frm'][1]
        for f in self.faces('flame_core'):              # a blue flame: pale core at the nozzle, teal toward the tip
            for c, r in f.each():
                x, y, z = f.world(c, r)
                self.fire(f, c, r, 't4' if y > f_lo + self.S.cubes['flame_core']['h'] * 0.6 else 't3')

    def end_plasma_lance(self):
        self.emitter('emitter')
        self.emitter('core_glow')
        for n in ('head_fins', 'head_back', 'guard', 'ring_a'):
            self.brass(n)

    def end_rocket_pack(self):
        for n in ('flare_l', 'flare_r'):
            self.body(n, 's2', 'b1', 's1', 'b1', 'r0')        # heat-straw lip, dark inside
            for f in self.faces(n, ('down',)):
                for c, r in f.each():
                    f.put(c, r, 's1' if c in (0, f.W - 1) or r in (0, f.H - 1) else 'r0')
        for n in ('throat_l', 'throat_r'):
            self.body(n, 'r0', 's0', 'r0', 's0', 'r0')

    def end_glider_wings(self):
        for n in ('web_l_root', 'web_l_mid', 'web_l_tip', 'web_r_root', 'web_r_mid', 'web_r_tip'):
            for f in self.faces(n, ('north',)):
                f.fill('e3')                            # the outer (seen) face pale
            for f in self.faces(n, ('south',)):
                f.fill('e1')
        for n in ('spar_l', 'spar_r'):
            self.body(n, 'b3', 'b4', 'b2', 'b4', 'b1')

    def end_cargo_hold(self):
        f = self.F('crate', 'north')                    # a stencilled company mark on the crate's face (teal)
        cx, cy = f.W // 2, f.H // 2
        for (dc, dr) in ((-1, 0), (0, 0), (1, 0), (0, -1), (0, 1)) if not self.heavy else \
                ((-2, 0), (-1, 0), (0, 0), (1, 0), (0, -2), (0, -1), (0, 1), (-1, -1), (-1, 1)):
            f.put(cx + dc, cy + dr, 't2')
        self.enamel('lid_plate')

    def end_missile_pod(self):
        for i in range(4):
            f = self.F('tube_%d' % i, 'north')
            for c, r in f.each():
                f.put(c, r, 'r0')
            f.put(f.W // 2, f.H // 2, 'R1')             # the red nose in the dark tube
            if f.W >= 4:
                f.put(f.W // 2 - 1, f.H // 2, 'R1')
        self.stripes('hatch_stripe')

    def end_smoke_mortar(self):
        self.stripes('band')
        f = self.F('muzzle', 'north')
        for c, r in f.each():
            if 0 < c < f.W - 1 and 0 < r < f.H - 1:
                f.put(c, r, 'r0')                       # the bore

    def end_radiator_fins(self):
        for i in range(5):                              # pale fins
            self.body('fin_c%d' % i, 's4', 's5', 's3', 's5', 's3')
        for f in self.faces('core'):                    # dark between them, copper tubes showing
            for c, r in f.each():
                f.put(c, r, 'c1' if c % 3 == 1 else 'r0')


    # ------------------------------------------------------------------------------------------- third batch
    # TORSO plating reads as fitted armour without recolouring the frame: enamel a step darker than a frame's body
    # (e1 field, e2 lit row, e0 foot), bright rivets, the gaps between plates the darkest step.
    def end_armor_plating(self):
        for i in range(3):
            self.body('plate_%d' % i, 'e1', 'e2', 'e0', 'e2', 'e0')
            for s in 'ab':
                self.body('rivet_%d%s' % (i, s), 'b4', 'b4', 'b4', 'b5', 'b3')
                self.F('rivet_%d%s' % (i, s), 'north').fill('b5')
        self.body('hanger', 'r0', 'r0', 'r0', 'r1', 'r0')       # seen only in the gaps: the darkest step

    def end_thorn_plating(self):
        self.body('slab', 's1', 's2', 's0', 's2', 's0')
        for t in 'abcde':
            self.body('thorn_' + t, 's4', 's5', 's3', 's5', 's3')
            self.F('thorn_' + t, 'north').fill('s5')            # the points catch the light

    def end_reactive_armour(self):
        self.body('backing', 'r0', 'r1', 'r0', 'r1', 'r0')       # the black seams between the tiles
        for n in ('tile_0c', 'tile_1', 'tile_2', 'tile_3'):
            self.body(n, 'w1', 'w2', 'w1', 'w3', 'w0')          # small parts: one tone a face
            self.F(n, 'north').fill('w2')

    def end_energy_shield(self):
        self.lamp('lens', 'l', 'firing')                        # the projector's lens, lit while the shield is up

    # BACK
    def end_passenger_pod(self):
        self.body('seat', 't2', 't3', 't1', 't3', 't1')         # the company's teal upholstery under the bubble
        self.body('step', 's2', 's3', 's1', 's3', 's1')

    def end_battery_rack(self):
        for n in ('cell_0', 'cell_1c', 'cell_2'):
            for f in self.faces(n, SIDES):                     # copper cells: lit top row, a dark band at a third
                for c, r in f.each():
                    k = 'c3'
                    if r == 0:
                        k = 'c6'
                    elif r == f.H // 3:
                        k = 'c0'
                    elif r == f.H - 1:
                        k = 'c1'
                    f.put(c, r, k)
        for i in range(3):                                      # the charge tell: painted dim, lit by code
            self.lamp('level_%d' % i, 'G', 'charge')

    def end_ender_satchel(self):
        # a black bag; the one unearthly accent is the eye (and the inside when open), in the violet ramp (v)
        self.body('bag', 'r1', 'r2', 'r0', 'r2', 'r0')
        self.body('flap_plate', 'r1', 'r2', 'r0', 'r2', 'r0')
        for f in self.faces('eye'):
            for c, r in f.each():
                self.always(f, c, r, 'v4' if (f.side == 'north' and (c, r) == (0, 0)) else 'v3')
        for f in self.faces('inside'):
            for c, r in f.each():
                self.always(f, c, r, 'v1' if (c + r) % 3 else 'v2')
        self.body('pupil', 'r0', 'r0', 'r0', 'r0', 'r0')

    # SENSORS: small; one lens colour each (always lit), everything else quiet
    def end_night_optics(self):
        for n in ('tube_l', 'tube_r'):
            self.body(n, 's1', 's2', 's0', 's2', 's0')
        self.lamp('lens_l', 'G')
        self.lamp('lens_r', 'G')

    def end_bio_scanner(self):
        for f in self.faces('face'):                            # a phosphor dial: dark green, a bright trace row
            for c, r in f.each():
                self.always(f, c, r, 'P3' if (f.side == 'north' and r == f.H // 2) else 'P1')
        self.body('needle_bar', 'r0', 'r0', 'r0', 'r0', 'r0')

    def end_thermal_imaging(self):
        self.lamp('lens', 'A')                                  # the hot amber-orange lens
        self.body('shutter_plate', 'w2', 'w3', 'w1', 'w3', 'w1')

    def end_ore_scanner(self):
        for n in ('crystal_l', 'crystal_r'):
            for f in self.faces(n):
                for c, r in f.each():
                    self.always(f, c, r, 't4' if (c + r) % 2 == 0 else 't3')

    def end_target_lock(self):
        self.lamp('reticle', 'R')

    # LEGS dressings: mid or light, one accent each, never a dark mass on the leg (dark-steel parts painted s3)
    def mid(self, *cubes):
        for n in cubes:
            self.body(n, 's3', 's4', 's2', 's4', 's2')

    def end_spring_heels(self):
        self.mid('brace', 'clamp')
        self.body('pad', 'r2', 'r3', 'r1', 'r3', 'r1')

    def end_anchor_clamps(self):
        self.mid('line_clip')
        self.body('line', 'r2', 'r3', 'r2', 'r3', 'r2')
        self.stripes('sleeve')                                  # the striped sleeve: on the leg, which moves

    def end_aqua_fins(self):
        self.mid('thigh_root', 'fin_root')
        for n in ('thigh_fin', 'fin_blade'):
            self.body(n, 't2', 't3', 't1', 't3', 't1')

    def end_sprint_servos(self):
        self.mid('motor_t', 'motor_s')
        for n in ('spoke_t', 'spoke_s'):
            self.body(n, 'r0', 'r1', 'r0', 'r1', 'r0')          # the black spoke: the spin shows on it

    def end_stilts(self):
        self.mid('housing')
        self.body('strut_foot', 'r2', 'r3', 'r1', 'r3', 'r1')

    def end_arachnid_legs(self):
        self.mid('hub', 'shin_guard')
        for n in ('upper_seg', 'lower_seg'):
            self.body(n, 's3', 's4', 's2', 's4', 's2')
        self.body('claw', 'r1', 'r2', 'r0', 'r2', 'r0')

    def end_hover_skirt(self):
        self.mid('hinge', 'duct')
        self.body('skirt_panel', 'e3', 'e4', 'e2', 'e4', 'e2')
        self.lamp('skirt_hem', 'l', 'firing')                   # the blue hems and fan glow: lit while hovering
        self.lamp('glow', 'l', 'firing')

    def end_lava_treads(self):
        self.mid('frame')
        for n in ('track_t', 'track_b', 'track_f'):            # tread links along the loop
            for f in self.faces(n):
                for c, r in f.each():
                    f.put(c, r, 'c5' if c % 2 == 0 else 'c1')


    # ------------------------------------------------------------------------------------------- fourth batch
    # The canister (four faces on one model): the band is its paint rule (red, green, blue = teal glass, white), the
    # symbol white or red by its material; only the recycler's lamp needs a working-end rule.
    def end_fire_suppression(self):
        self.F('horn_mouth', 'down').fill('r0')          # the horn's bell is open underneath

    def end_life_support_recycler(self):
        self.lamp('lamp', 'G')

    def end_coolant_loop(self):
        pass

    def end_medical_injector(self):
        pass

    def end_signal_booster(self):
        self.body('dish_bowl', 's0', 's1', 's0', 's1', 's0')     # the dark bowl inside the bright rim
        self.F('dish_bowl', 'north').fill('r0')
        self.lamp('whip_tip', 'R')                       # the blinking tip (it blinks by its bone's scale)

    def end_solar_skin(self):
        self.body('glint', 'g3', 'g3', 'g2', 'g3', 'g2')  # a glint of sky on the cells, unlit

    def end_tesla_coil(self):
        for i in range(3):                               # the windings: copper, the lit face up
            self.body('coil_%d' % i, 'c5', 'c6', 'c2', 'c7', 'c1')
        for n in ('arc_a', 'arc_b', 'arc_c', 'arc_d'):   # the arcs: hidden at rest, lit while charged
            for f in self.faces(n):
                for c, r in f.each():
                    self.fire(f, c, r, 'g3' if (c + r) % 2 == 0 else 'l3')

    def end_workbench(self):
        pass

    def end_stealth_field(self):
        # the energy shield's projector, a black lens; the pupil in the violet ramp (v), as the satchel's eye
        self.lamp('pupil_c', 'v', lo=2)

    def end_bio_integration_port(self):
        self.lamp('status', 'G')

    # The core tell: one shared hatch, a face each with one unmistakable colour; the lit parts always lit
    def core_common(self):
        self.lamp('lamp', 'A')

    def end_galvanic_stack(self):
        self.core_common()                               # white voltmeter face (white rule), black needle (lens)

    def end_combustion_kernel(self):
        self.core_common()
        self.lamp('face', 'R')                           # the hot window

    def end_bio_reactor_core(self):
        self.core_common()
        self.lamp('face', 'G')
        for n in ('bubble_a', 'bubble_b'):
            self.body(n, 'G3', 'G3', 'G3', 'G3', 'G3')
            for f in self.faces(n):
                for c, r in f.each():
                    self.always(f, c, r, 'G3')

    def end_capacitor_bank(self):
        self.core_common()
        for i in range(3):
            self.lamp('bar_%dc' % i, 'y', lo=2)

    def end_fusion_bottle(self):
        self.core_common()
        for n in ('ring_b', 'ring_t', 'ring_l', 'ring_r'):   # deep orange (A1), never the brass-like A2/A3
            for f in self.faces(n):
                for c, r in f.each():
                    self.always(f, c, r, 'A0' if f.side == 'down' else 'A1')
        for f in self.faces('plasma'):                   # the spark: glow cyan, its seen faces the palest step
            for c, r in f.each():
                self.always(f, c, r, 'y4' if f.side in ('north', 'south', 'up') else 'y3')

    def save(self, out, preview=False):
        os.makedirs(out, exist_ok=True)
        base = self.name + ('_heavy' if self.heavy else '')
        miss = [m for m in self.S.missing() if self.mat.get(m[0]) != 'vial']
        if miss:
            sys.stderr.write('%s: unpainted %s\n' % (base, miss))
            return False
        self.S.img.save(os.path.join(out, base + '.png'))
        written = [base + '.png']
        if self.S.glow.getbbox():
            self.S.glow.save(os.path.join(out, base + '_glowmask.png'))
            written.append(base + '_glowmask.png')
        if self.firing.getbbox():
            self.firing.save(os.path.join(out, base + '_firing.png'))
            written.append(base + '_firing.png')
        if self.charge.getbbox():
            self.charge.save(os.path.join(out, base + '_charge.png'))
            written.append(base + '_charge.png')
        return written


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--out', default=OUT)
    ap.add_argument('--only', nargs='*', help='paint only these modules (both classes)')
    a = ap.parse_args()
    allw = []
    for name in NAMES + NAMES2 + NAMES3:
        if a.only and name not in a.only:
            continue
        for heavy in ((False,) if name in ONE_SIZE else (False, True)):
            p = SheetBrush(name, heavy)
            p.paint()
            w = p.save(a.out)
            if not w:
                return 1
            allw += w
    print('wrote %d files to %s' % (len(allw), a.out))
    with open(os.path.join(a.out, '_written.txt') if a.out != OUT else os.devnull, 'w') as fh:
        fh.write('\n'.join(allw))
    return 0


if __name__ == '__main__':
    sys.exit(main())

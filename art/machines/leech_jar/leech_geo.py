"""Leech Jar (a gumball machine): geometry.

    python art/machines/leech_jar/leech_geo.py      # writes leech_jar.bbmodel and its placeholder PNG
    python art/machines/leech_jar/leech_anim.py     # then the clips (--review: review copies; --sweep)

A glass globe of water on a brass collar, on a squat ivory pedestal with a brass base; the leech lives in the globe.
The pedestal's front window shows the samples racked like gumballs waiting (one vial per three samples, by the
blockstate `samples`); a brass crank on the right side turns by itself when a vial drops, and twice when the player
empties it. A brass suction nozzle outside the globe's glass turns (code) to face the creature standing against the
jar and pops out to sample it; the leech, on the same turning carrier, lies on the water's surface and, awake, swims to
the glass on that side and rears against it. Faces -Z; +X is the machine's right (the viewer's left).

The motion that reads at ten blocks is the crank: an arm and knob 6 px long standing 4 px off the pedestal's right
side, turns a full circle (the knob's path is 12 px across) at every sample and twice on emptying: it changes the
machine's outline from the front and the back. The leech and the nozzle are close-up life (behind glass, and 2 px).

Glass is cutout clear glass (frame and streaks, clear inside), in three tiers (12, 14 crossed, 12) so the globe steps
round like a ball; the water is opaque and fills it to 1 px from the glass up to y 19, the darkest mass; the leech lives
on its surface in the 6 px of air under the top tier, light against it.
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

PATH = os.path.join(HERE, 'leech_jar.bbmodel')

PED = (3, 12)            # pedestal y
GLOBE = (13, 25)         # globe glass y (three tiers)
CARRIER = (0, 13, 0)     # the turning carrier: the globe's axis at its floor
CRANK = (7, 9, 0)        # the crank's axle (on the right side), about X

kit.MATERIALS['water'] = ('1f3a2e', 255)        # the globe's water: jar-green, darkest, opaque (2.6)
kit.MATERIALS['leech'] = ('b89a6a', 255)        # the leech: light against the water (a value, not a hue, choice)
for _m in ('water', 'leech'):
    if _m not in kit.ORDER:
        kit.ORDER.insert(kit.ORDER.index('glass'), _m)


def P(m, name, f, t, mat, part, parent='static', **kw):
    return m.box(name, f, t, mat, parent, part=part, **kw)


def model(samples_shown=3):
    m = kit.Model('leech_jar', budget=4)

    # ---- base: a rubber kick inset under a brass foot 14x2x14 --------------------------------------------------
    m.box('kick', [-5, 0, -5], [5, 1, 5], 'rubber')
    m.box('base', [-7, 1, -7], [7, 3, 7], 'brass')

    # ---- the pedestal, 12 wide (K2: B x -5..5 z -6..6 carries the front; A's ends x +-5..6 are the corner steps).
    # The window cut through B's front 2 px deep to a dark back: x -4..4, y 5..11 -----------------------------------
    y0, y1 = PED
    P(m, 'ped_b_back', [-5, y0, -3], [5, y1, 6], 'enamel', 'ped')
    P(m, 'ped_b_fl', [-5, y0, -6], [-4, y1, -3], 'enamel', 'ped')
    P(m, 'ped_b_fr', [4, y0, -6], [5, y1, -3], 'enamel', 'ped')
    P(m, 'ped_b_fb', [-4, y0, -6], [4, 5, -3], 'enamel', 'ped')
    P(m, 'ped_b_ft', [-4, 11, -6], [4, y1, -3], 'enamel', 'ped')
    P(m, 'ped_a_l', [-6, y0, -5], [-5, y1, 5], 'enamel', 'ped')
    P(m, 'ped_a_r', [5, y0, -5], [6, y1, 5], 'enamel', 'ped')
    m.box('ped_window_back', [-4, 5, -4], [4, 11, -3], 'rubber', part='ped')   # the window's dark back, 2 px in
    # the rack: a brass shelf; vials 2x4 with 1 px dark gaps, a dark band above them (one vial per three samples)
    P(m, 'rack', [-4, 5, -5], [4, 6, -4], 'brass', 'rack')
    for n in (1, 2, 3):
        g = 'state_samples_%d' % n
        m.group(g, 'static', hidden=(n != samples_shown))
        for i in range(n):
            x = -4 + 3 * i
            P(m, 'vial%d_%d' % (n, i), [x, 6, -5], [x + 2, 10, -4], 'sample', 'vials%d_%d' % (n, i), g)

    # ---- K3 lamp on the pedestal's left side (the window takes the front) --------------------------------------
    kit.lamp(m, 'west', -6, -2, 6)          # bezel z -2..2, y 6..10, x -7..-6; lens x -8..-7

    # ---- collar; the globe in three tiers (12, 14 crossed, 12) so it steps round like a ball; the water filling
    # it to 1 px from the glass up to y 19; the cap and finial ------------------------------------------------------
    P(m, 'collar', [-7, 12, -7], [7, 13, 7], 'brass', 'collar')
    P(m, 'globe_lo', [-6, 13, -6], [6, 15, 6], 'glass', 'globe', off=('up', 'down'))
    P(m, 'globe_a', [-7, 15, -6], [7, 23, 6], 'glass', 'globe', off=('north', 'south'))
    P(m, 'globe_b', [-6, 15, -7], [6, 23, 7], 'glass', 'globe', off=('east', 'west', 'up', 'down'))
    P(m, 'globe_hi', [-6, 23, -6], [6, 25, 6], 'glass', 'globe', off=('down',))
    P(m, 'water_lo', [-5, 13, -5], [5, 15, 5], 'water', 'water', off=('up',))
    P(m, 'water_a', [-6, 15, -5], [6, 19, 5], 'water', 'water', off=('down',))
    P(m, 'water_b', [-5, 15, -6], [5, 19, 6], 'water', 'water', off=('up', 'down'))
    P(m, 'cap', [-3, 25, -3], [3, 27, 3], 'brass', 'cap')
    P(m, 'knob', [-1, 27, -1], [1, 29, 1], 'brass', 'cap')

    # ---- bones --------------------------------------------------------------------------------------------------
    # carrier: code rotY about the globe's axis: turns the leech, the nozzle's flange and the nozzle to the
    # creature's side. Its own cubes: the brass flange on the glass's outside (4x4x1) the nozzle runs through
    m.bone('carrier', list(CARRIER), code=('rotation',))
    P(m, 'flange', [-2, 15, -8], [2, 19, -7], 'dsteel', 'flange', 'carrier')
    # leech: lying on the water's surface (y 19), light against the dark water; keyed (wake, press, settle, sulk).
    # The Butcher's paper hat is a cube the renderer hides (hiddenCubes)
    m.bone('leech', [0, 19, -1], 'carrier')
    P(m, 'leech_body', [-2, 19, -2], [2, 21, 0], 'leech', 'leech', 'leech')
    P(m, 'leech_tail', [2, 19, -2], [4, 20, 0], 'leech', 'leech', 'leech')
    P(m, 'leech_hat', [-2, 21, -2], [0, 22, 0], 'enamel', 'leech_hat', 'leech')
    # nozzle: a brass cup in the flange; keyed posZ (local to the carrier: -Z toward the creature)
    m.bone('nozzle', [0, 17, -8], 'carrier')
    P(m, 'nozzle_cup', [-1, 16, -10], [1, 18, -8], 'brass', 'nozzle', 'nozzle')
    # its rod, hidden in the flange at rest, still in it with the cup popped 2 px out (never detached)
    P(m, 'nozzle_rod', [-0.5, 16.5, -8], [0.5, 17.5, -6], 'steel', 'nozzle', 'nozzle')
    # crank: a 2x2 hub 1 px proud of the right side, an arm 4 px up from the axle, a 2x2 knob 3 px off the side
    m.bone('crank', list(CRANK))
    P(m, 'crank_hub', [6, 8, -1], [7, 10, 1], 'brass', 'crank', 'crank')
    P(m, 'crank_arm', [7, 9, -1], [8, 13, 1], 'brass', 'crank', 'crank')
    P(m, 'crank_knob', [7, 13, -1], [9, 15, 1], 'rubber', 'crank', 'crank')

    # ---- locators ---------------------------------------------------------------------------------------------
    m.locator('nozzle', [0, 17, -10], 'nozzle')
    m.locator('rack', [0, 10, -5], None)
    m.locator('globe_top', [0, 22, 0], None)

    # ---- cell shapes ------------------------------------------------------------------------------------------
    m.shape('lower', (1, 0, 1, 15, 12, 15))
    m.shape('lower', (1, 12, 1, 15, 13, 15))
    m.shape('lower', (2, 13, 2, 14, 15, 14))
    m.shape('lower', (1, 15, 1, 15, 16, 15))
    m.shape('lower', (6, 15, 0, 10, 16, 1))          # the nozzle's flange at the front
    m.shape('lower', (14, 8, 7, 15, 10, 9))          # the crank's hub
    m.shape('upper', (1, 0, 1, 15, 7, 15))           # the globe's main tier
    m.shape('upper', (2, 7, 2, 14, 9, 14))           # the top tier
    m.shape('upper', (5, 9, 5, 11, 11, 11))          # the cap
    m.shape('upper', (7, 11, 7, 9, 13, 9))           # the finial
    m.shape('upper', (6, 0, 0, 10, 3, 1))            # the flange
    return m


GLASS_FRAME = (0xa8, 0xd0, 0xd9, 255)
GLASS_STREAK = (0xd0, 0xea, 0xe9, 255)


def clear_glass(path):
    """Cutout clear glass on the placeholder: every glass face a light 1 px frame with two streaks, clear inside."""
    from PIL import Image
    with open(path, encoding='utf-8') as fh:
        d = json.load(fh)
    png = os.path.splitext(path)[0] + '_placeholder.png'
    im = Image.open(png).convert('RGBA')
    px = im.load()
    W, H = im.size
    for e in d['elements']:
        if e.get('type') != 'cube' or not e['name'].startswith('globe'):
            continue
        rects = []
        for fc in e['faces'].values():
            if fc.get('texture') is None:
                continue
            u0, v0, u1, v1 = fc['uv']
            xa, xb = sorted((int(u0), int(u1)))
            ya, yb = sorted((int(v0), int(v1)))
            rects.append((xa, ya, xb - xa, yb - ya))
        for (x0, y0, rw, rh) in rects:
            thin = min(rw, rh) < 4          # a 2 px tier's side: clear, with one line along its long edge
            for y in range(y0, y0 + rh):
                for x in range(x0, x0 + rw):
                    if 0 <= x < W and 0 <= y < H:
                        if thin:
                            edge = (y == y0) if rw >= rh else (x == x0)
                        else:
                            edge = x in (x0, x0 + rw - 1) or y in (y0, y0 + rh - 1)
                        px[x, y] = GLASS_FRAME if edge else (0, 0, 0, 0)
            for (x, y) in [(x0 + 2, y0 + 1), (x0 + 1, y0 + 2), (x0 + 3, y0 + 1)]:
                if 0 <= x < x0 + rw - 1 and 0 <= y < y0 + rh - 1:
                    px[x, y] = GLASS_STREAK
    im.save(png)
    with open(png, 'rb') as fh:
        d['textures'][0]['source'] = 'data:image/png;base64,' + base64.b64encode(fh.read()).decode()
    with open(path, 'w', encoding='utf-8') as fh:
        json.dump(d, fh, indent=1)


if __name__ == '__main__':
    mm = model()
    mm.write(PATH)
    clear_glass(PATH)
    print(kit.shapes_java(mm))

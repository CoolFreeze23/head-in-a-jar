"""DNA Sequencer (the cash register that reads DNA): geometry.

    python art/machines/dna_sequencer/sequencer_geo.py     # writes dna_sequencer.bbmodel and its placeholder PNG
    python art/machines/dna_sequencer/sequencer_anim.py    # then writes the clips into it (and the review copy)

A brass cash register on the black-topped K12 lab bench. Faces -Z. Seen from the front, as its operator sees it:
four coloured keys in a dark key well along the low front (A green, C blue, G amber, T red, A on the viewer's left),
the stepped brass body behind them, the display box on top with its dark window where the letter tabs pop up (and the
NO SALE banner), the DNA Fraction standing face-on in a dark reader slot on the display's top (viewer's left), the
receipt tape coming out of the display's top beside it (viewer's right) and the crank on the register's right side
(the viewer's right, -X), inside the block. The bench drawer is the cash drawer: it opens for the screen and pops on
the bell.

Vertical stack (px): worktop top 13; dark plinth line 13-14; key bed 14-16; keys 16-19 against the dark key panel 15-20; upper body
14-21; display box 21-27 (window opening 22-26); reader slot 27-28 with the fraction 28-32; receipt stub top 28,
rising 1 px every second call to 34 at the twelfth (code); the call flag up at 27-32. Crank pivot (-6, 22, 2), knob
centre 5 px out.
"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SCRATCH = 'build/machines-review/scratch-dna_sequencer'
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
try:
    import kit  # noqa: E402
except ImportError:                       # the worktree changed under us: use a copy of the kit from the branch
    sys.path.insert(0, SCRATCH)
    if not os.path.exists(os.path.join(SCRATCH, 'kit.py')):
        with open(os.path.join(SCRATCH, 'kit.py'), 'wb') as fh:
            fh.write(subprocess.check_output(['git', '-C', '.', 'show',
                                              'fo-mach:art/machines/kit/kit.py']))
    import kit  # noqa: E402

PATH = os.path.join(HERE, 'dna_sequencer.bbmodel')
REVIEW = 'build/machines-review/dna_sequencer_review.bbmodel'

# own placeholder materials (review only; the sheet is painted on its own): the four key colours (the C key is blue,
# 0x3F9BE4) and the receipt paper. Added to the kit's tables in memory, not in kit.py.
OWN = {'key_a': ('4fa84a', 255), 'key_c': ('3f9be4', 255), 'key_g': ('e9741c', 255), 'key_t': ('cf4032', 255),
       'paper': ('f1ead8', 255)}
for _k, _v in OWN.items():
    kit.MATERIALS[_k] = _v
    if _k not in kit.ORDER:
        kit.ORDER.append(_k)

# keys, as seen from the front left to right (A on the viewer's left = +X): (bone, x0, material, sound pitch)
KEYS = [('key_a', 5, 'key_a'), ('key_c', 1, 'key_c'), ('key_g', -3, 'key_g'), ('key_t', -7, 'key_t')]
CRANK_PIV = (-6, 22, 2)
RECEIPT_STEP = 1.0        # px of tape per two accepted calls (12 calls: 6 px)


def model():
    m = kit.Model('dna_sequencer', budget=8)

    # ---- the lower half: the K12 bench (open shelf, the K5 drawer = the cash drawer, the K3 lamp) ---------------
    drawer_spec = kit.bench(m)

    # ---- a dark plinth line under the register (inset 1 px under the key bed and the upper body) -----------------
    m.box('reg_plinth', [-7, 13, -7], [7, 14, 6], 'dsteel')

    # ---- the key bed: a low brass ledge (2 px) the keys stand on ----------------------------------------------------
    m.box('keybed', [-8, 14, -8], [8, 16, -3], 'brass')

    # ---- the four keys (so they read as products on a shelf): a cap 2 wide, 2 tall, 3 deep
    # on a dark 1 px stem, 2 px gaps, in front of the dark key panel; each a bone pressed 1 px -----------------------
    for bone, x0, mat in KEYS:
        m.bone(bone, [x0 + 1, 16, -5.5])
        m.box(bone + '_stem', [x0, 16, -6], [x0 + 2, 17, -5], 'dsteel', bone, part=bone)
        m.box(bone + '_cap', [x0, 17, -7], [x0 + 2, 19, -4], mat, bone, part=bone)

    # ---- the upper body: brass, 12 wide, 1 px gap behind the key bed. Its front is a brass frame round a dark key
    # panel set 1 px back (the keys read against it); built from pieces -------------------------------------------
    B = 'body'
    m.box('body', [-6, 14, 0], [6, 21, 7], 'brass', part=B)
    m.box('body_f_bottom', [-6, 14, -2], [6, 15, 0], 'brass', part=B)
    m.box('body_f_top', [-6, 20, -2], [6, 21, 0], 'brass', part=B)
    m.box('body_f_r', [-6, 15, -2], [-5, 20, 0], 'brass', part=B)
    m.box('body_f_l', [5, 15, -2], [6, 20, 0], 'brass', part=B)
    m.box('key_panel', [-5, 15, -1], [5, 20, 0], 'rubber')

    # ---- the display box: brass, a 8x4 window opening 3 deep with clear glass 1 px back, built round the hole ----
    D = 'display'
    m.box('disp_bottom', [-5, 21, -1], [5, 22, 6], 'brass', part=D)
    m.box('disp_top', [-5, 26, -1], [5, 27, 6], 'brass', part=D)
    m.box('disp_side_r', [-5, 22, -1], [-4, 26, 6], 'brass', part=D)
    m.box('disp_side_l', [4, 22, -1], [5, 26, 6], 'brass', part=D)
    m.box('disp_back', [-4, 22, 4], [4, 26, 6], 'brass', part=D)
    m.box('disp_well', [-4, 22, 3], [4, 26, 4], 'rubber')          # the window's dark back wall
    m.box('disp_glass', [-4, 22, 0], [4, 26, 1], 'glass')           # 1 px back from the box's face

    # ---- the call flag (clear of the glass, where a tab would not read): the register's amount tab, a
    # plate 4 wide and 5 tall that rises from the body, through the window, out of a slot in the display's top into the
    # open (y 27-32), as a real register's tabs pop up on top. Cube 0 the call plate (painted white with its letter,
    # TINTED by the base's key colour in game; blue here for review), cube 1 the NO SALE banner
    # behind it (hiddenCubes picks one). Positions: 0 hidden in the body (y 16-21), +5 in the window (y 21-26, where
    # the shown cube is switched), +11 up.
    m.bone('flag', [-2, 16, 2])
    m.box('flag_plate', [-4, 16, 1], [0, 21, 2], 'key_c', 'flag', part='flag')
    m.box('flag_banner', [-4, 16, 2], [0, 21, 3], 'paper', 'flag', part='flag')

    # ---- the fraction reader on the display's top (viewer's left): a dark slot block --------------------------
    m.box('reader', [1, 27, 1], [4, 28, 4], 'dsteel')
    m.anchor('fraction', [2.5, 28, 2.5], 'the DNA Fraction (slot 0) while loaded: FIXED, upright, facing -Z, scale 0.25,'
             ' bottom centre in the reader slot', standin=((4, 4, 1), 'standi'))

    # ---- the receipt: a paper tape 4 wide rising out of a slot in the display's top (viewer's right); posY is code
    # (1 px per two accepted calls: whole pixels, M6). Bone-space strip y 21-28 (the part under y 27 stays inside the display); the pivot is
    # at the strip's foot, which reaches the slot (y 27) at full length: the tear flick turns about the slot there.
    m.bone('receipt', [-2, 21, 4.5], code=('position',))
    m.box('receipt_tape', [-4, 21, 4], [0, 28, 5], 'paper', 'receipt', part='receipt')
    m.box('receipt_curl', [-4, 27, 3], [0, 28, 4], 'paper', 'receipt', part='receipt')

    # ---- the crank (machine's right side = viewer's right, -X). the pivot raised to the
    # body's top on a steel axle stub against the display's side, the arm 2x5 and the knob 2 px proud and 5 px out, so
    # at rest it stands 1 px over the display's shoulder and on every turn it circles 10 px in the open beside it ---
    cx, cy, cz = CRANK_PIV
    m.box('crank_axle', [-6, cy - 1, cz - 1], [-5, cy + 1, cz + 1], 'steel')
    m.bone('crank', [cx, cy, cz])
    m.box('crank_hub', [-7, cy - 1, cz - 1], [-6, cy + 1, cz + 1], 'brass', 'crank', part='crank')
    m.box('crank_arm', [-7, cy + 1, cz - 1], [-6, cy + 4, cz + 1], 'dsteel', 'crank', part='crank')
    m.box('crank_knob', [-8, cy + 4, cz - 1], [-6, cy + 6, cz + 1], 'rubber', 'crank', part='crank')

    # ---- locators (static points) ----------------------------------------------------------------------------
    m.locator('keys', [0, 18, -6], None)
    m.locator('window', [0, 24, -1], None)
    m.locator('receipt', [-2, 34, 4.5], None)          # the tape's top at full length: the tear's scrap
    m.locator('crank', [-8, 22, 2], None)
    m.locator('reader', [3, 30, 1], None)

    # ---- the cell shapes (Java cell coordinates; upper = the machine_top cell) -------------------------------
    m.shape('lower', (0, 0, 0, 16, 13, 16))      # the bench
    m.shape('lower', (0, 13, 0, 16, 16, 15))     # plinth, key bed, upper body, crank
    m.shape('upper', (0, 0, 0, 16, 3, 5))        # key bed and keys
    m.shape('upper', (0, 0, 6, 14, 5, 15))       # upper body and the crank's lower sweep
    m.shape('upper', (3, 5, 7, 13, 11, 14))      # display box
    m.shape('upper', (4, 11, 8, 13, 16, 13))     # reader, fraction, receipt stub, the call flag up
    m.shape('upper', (0, 5, 8, 3, 12, 12))       # the crank at rest (axle, arm and knob)
    return m, drawer_spec


# ---------------------------------------------------------------------------------------------------------------------
# Clear glass placeholder (after the Mixer's): a lit top row and left column and one streak opaque, the rest clear
# (cutout), so the review renders show the tabs behind the 8x4 window (the kit's glass is a tinted fill; a full frame
# on a 4 px pane hides half of it). Applied after the kit writes; the real pane is painted on the sheet.
# ---------------------------------------------------------------------------------------------------------------------
GLASS_FRAME = (0xa8, 0xd0, 0xd9, 255)    # g2
GLASS_STREAK = (0xd0, 0xea, 0xe9, 255)   # g3


def clear_glass(path, m):
    import base64
    import json
    from PIL import Image
    with open(path, encoding='utf-8') as fh:
        d = json.load(fh)
    png = os.path.splitext(path)[0] + '_placeholder.png'
    im = Image.open(png).convert('RGBA')
    px = im.load()
    names = {c[0] for c in m.cubes if c[3] == 'glass'}
    for e in d['elements']:
        if e.get('type') != 'cube' or e['name'] not in names:
            continue
        for fname, face in e['faces'].items():
            u0, v0, u1, v1 = face['uv']
            xa, xb = sorted((int(u0), int(u1)))
            ya, yb = sorted((int(v0), int(v1)))
            for y in range(ya, yb):
                for x in range(xa, xb):
                    edge = x == xa or y == ya
                    px[x, y] = GLASS_FRAME if edge else (0, 0, 0, 0)
            if xb - xa >= 6 and yb - ya >= 4:       # one short streak in the opposite corner
                for (x, y) in [(xb - 2, yb - 2), (xb - 3, yb - 2)]:
                    px[x, y] = GLASS_STREAK
    im.save(png)
    with open(png, 'rb') as fh:
        d['textures'][0]['source'] = 'data:image/png;base64,' + base64.b64encode(fh.read()).decode()
    with open(path, 'w', encoding='utf-8') as fh:
        json.dump(d, fh, indent=1)
    return sorted(names)


if __name__ == '__main__':
    m, _ = model()
    m.write(PATH)
    clear_glass(PATH, m)
    print(kit.shapes_java(m))

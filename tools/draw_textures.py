"""Draws the mod's pixel-art textures. Run: python tools/draw_textures.py (from the project root)."""
import os
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'headinajar', 'textures')

def hexc(s, a=255):
    s = s.lstrip('#')
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)

T = (0, 0, 0, 0)
OUT = hexc('1c1f24')
IRON = [hexc('5d646c'), hexc('8a939c'), hexc('b9c2ca'), hexc('e2e8ec')]
NETH = [hexc('2b2629'), hexc('443b3f'), hexc('5f5357'), hexc('7d6f72')]
GLASS = [hexc('8fd6e8', 110), hexc('c8f1fa', 150), hexc('ffffff', 200)]
FLUID = [hexc('2f9c5a', 110), hexc('48c578', 105), hexc('7ce8a0', 150)]
COPPER = [hexc('7a3b23'), hexc('b4613c'), hexc('e0905c'), hexc('f6c08e')]
GOLD = [hexc('9c6b10'), hexc('d9a21c'), hexc('f8d548'), hexc('fff3a0')]
RED = [hexc('6b1414'), hexc('b02626'), hexc('e84848'), hexc('ff9a9a')]
CYAN = [hexc('136b80'), hexc('22a8c4'), hexc('59e0f4'), hexc('c8fbff')]
SKIN = [hexc('8a5a3c'), hexc('b07a52'), hexc('d9a67a')]
PURPLE = [hexc('3c1c5c'), hexc('7a3cb0'), hexc('b47ae8'), hexc('e6c8ff')]
GREEN = [hexc('1f5c2a'), hexc('2f9c4a'), hexc('6ad884'), hexc('c8ffd6')]
ORANGE = [hexc('7a2e08'), hexc('d0601a'), hexc('f8a040'), hexc('ffe08a')]


def img(w, h):
    return Image.new('RGBA', (w, h), T)


def rect(im, x0, y0, x1, y1, c):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            if 0 <= x < im.width and 0 <= y < im.height:
                im.putpixel((x, y), c)


def px(im, pts, c):
    for x, y in pts:
        im.putpixel((x, y), c)


def outline(im):
    """Dark outline around every opaque pixel, like vanilla items."""
    src = im.copy()
    for x in range(im.width):
        for y in range(im.height):
            if src.getpixel((x, y))[3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < im.width and 0 <= ny < im.height and src.getpixel((nx, ny))[3] > 100:
                    im.putpixel((x, y), OUT)
                    break


def save(im, *path):
    p = os.path.join(ROOT, *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    im.save(p)


# --- items -------------------------------------------------------------------------------

def jar_item(metal, light):
    im = img(16, 16)
    rect(im, 4, 2, 11, 2, metal[2]); rect(im, 3, 3, 12, 3, metal[1])          # lid
    rect(im, 7, 1, 8, 1, metal[3])                                            # valve
    rect(im, 4, 4, 11, 12, GLASS[0])                                          # glass body
    rect(im, 5, 8, 10, 12, FLUID[1]); rect(im, 5, 8, 10, 8, FLUID[2])         # fluid
    rect(im, 4, 4, 4, 12, GLASS[1]); px(im, [(5, 5), (5, 6)], GLASS[2])       # glint
    rect(im, 11, 4, 11, 12, hexc('6fb8cc', 150))
    rect(im, 3, 13, 12, 13, metal[1]); rect(im, 3, 14, 12, 14, metal[0])      # base
    px(im, [(10, 13)], light)
    rect(im, 1, 6, 2, 6, metal[1]); rect(im, 2, 7, 2, 10, metal[0])           # pipe
    rect(im, 13, 6, 14, 6, metal[1]); rect(im, 13, 7, 13, 10, metal[0])
    outline(im)
    return im


save(jar_item(IRON, RED[2]), 'item', 'life_support_jar.png')
save(jar_item(NETH, CYAN[2]), 'item', 'reinforced_life_support_jar.png')


def gear_base(m, im=None):
    """A round module housing with a socket pin, in metal ramp m."""
    im = im or img(16, 16)
    for x in range(16):
        for y in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d <= 6.2:
                im.putpixel((x, y), m[1] if d > 5.0 else m[2])
            if d <= 2.2:
                im.putpixel((x, y), m[0])
    for x, y in ((7, 0), (8, 0), (7, 15), (8, 15), (0, 7), (0, 8), (15, 7), (15, 8), (2, 2), (13, 13), (2, 13), (13, 2)):
        im.putpixel((x, y), m[1])
    return im


def module(name, draw):
    im = gear_base(IRON)
    draw(im)
    outline(im)
    save(im, 'item', name + '.png')


def hop(im):
    for i, y in enumerate(range(4, 12, 2)):
        rect(im, 5, y, 10, y, GREEN[2] if i % 2 == 0 else GREEN[1])
    rect(im, 4, 12, 11, 12, IRON[0])


def propeller(im):
    rect(im, 2, 7, 13, 8, CYAN[2]); rect(im, 7, 2, 8, 13, CYAN[1])
    px(im, [(2, 7), (13, 8)], CYAN[3]); rect(im, 7, 7, 8, 8, IRON[3])


def arm(im):
    rect(im, 4, 10, 9, 11, ORANGE[1]); rect(im, 8, 5, 9, 10, ORANGE[2])
    px(im, [(10, 4), (11, 5), (7, 4), (6, 5)], IRON[3]); px(im, [(10, 5), (7, 5)], IRON[2])


def telekinesis(im):
    for x in range(16):
        for y in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if 2.5 <= d <= 4.0:
                im.putpixel((x, y), PURPLE[2])
    rect(im, 6, 6, 9, 9, PURPLE[3]); rect(im, 7, 7, 8, 8, hexc('ffffff'))


def drill(im):
    for i in range(6):
        rect(im, 5 + i // 2, 3 + i, 10 - i // 2, 3 + i, GOLD[2] if i % 2 else GOLD[1])
    rect(im, 7, 9, 8, 12, GOLD[0])


def plating(im):
    rect(im, 4, 4, 11, 11, IRON[2]); rect(im, 4, 4, 11, 4, IRON[3]); rect(im, 4, 11, 11, 11, IRON[0])
    px(im, [(5, 5), (10, 5), (5, 10), (10, 10)], IRON[0])


def heat(im):
    rect(im, 5, 5, 10, 11, ORANGE[1]); rect(im, 6, 4, 9, 4, ORANGE[2])
    px(im, [(7, 7), (8, 6), (8, 8), (7, 9)], ORANGE[3]); rect(im, 6, 11, 9, 11, RED[1])


def lens(im):
    for x in range(16):
        for y in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d <= 3.6:
                im.putpixel((x, y), GREEN[2] if d > 1.5 else GREEN[3])
    px(im, [(6, 6)], hexc('ffffff'))


def magnet(im):
    rect(im, 4, 4, 5, 10, RED[2]); rect(im, 10, 4, 11, 10, RED[2])
    rect(im, 4, 10, 11, 11, RED[1]); rect(im, 4, 4, 5, 5, IRON[3]); rect(im, 10, 4, 11, 5, IRON[3])


def jet(im):
    rect(im, 6, 4, 9, 9, CYAN[1]); rect(im, 5, 9, 10, 10, IRON[1])
    px(im, [(6, 11), (8, 12), (9, 11), (7, 13)], CYAN[3])


for name, fn in (('hop_springs', hop), ('propeller', propeller), ('manipulator_arm', arm), ('telekinesis', telekinesis),
                 ('drill_bit', drill), ('armor_plating', plating), ('heat_shield', heat), ('night_lens', lens),
                 ('magnet', magnet), ('water_jet', jet)):
    module(name, fn)

# --- charging dock --------------------------------------------------------------------------

top = img(16, 16)
rect(top, 0, 0, 15, 15, IRON[1]); rect(top, 1, 1, 14, 14, IRON[2])
rect(top, 3, 3, 12, 12, IRON[0]); rect(top, 4, 4, 11, 11, CYAN[0])
for i in range(4, 12, 2):
    rect(top, i, 4, i, 11, CYAN[1])
rect(top, 6, 6, 9, 9, CYAN[2]); rect(top, 7, 7, 8, 8, CYAN[3])
save(top, 'block', 'charging_dock_top.png')
side = img(16, 16)
rect(side, 0, 0, 15, 15, IRON[1]); rect(side, 0, 12, 15, 12, IRON[3]); rect(side, 0, 15, 15, 15, IRON[0])
for x in (3, 7, 11):
    rect(side, x, 13, x + 1, 13, CYAN[2])
save(side, 'block', 'charging_dock_side.png')
bottom = img(16, 16)
rect(bottom, 0, 0, 15, 15, IRON[0])
save(bottom, 'block', 'charging_dock_bottom.png')

# --- jar entity (64x64; matches JarModel) ------------------------------------------------------


def box_uv(im, u, v, w, h, d, fill):
    """Fills the six faces of a model box at texOffs(u, v) with fill(face, x, y, fw, fh)."""
    faces = {
        'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
        'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
        'west': (u + d + w, v + d, d, h), 'south': (u + d + w + d, v + d, w, h),
    }
    for face, (fx, fy, fw, fh) in faces.items():
        for x in range(int(fw)):
            for y in range(int(round(fh))):
                c = fill(face, x, y, int(fw), int(round(fh)))
                if c is not None and 0 <= fx + x < im.width and 0 <= fy + y < im.height:
                    im.putpixel((int(fx + x), int(fy + y)), c)


jar = img(64, 64)


def metal_fill(m):
    def f(face, x, y, w, h):
        if face in ('top', 'bottom'):
            edge = x in (0, w - 1) or y in (0, h - 1)
            rivet = (x, y) in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2))
            return m[0] if rivet else (m[1] if edge else m[2])
        return m[3] if y == 0 else m[1]
    return f


box_uv(jar, 0, 0, 11, 1, 11, metal_fill(IRON))      # base
box_uv(jar, 0, 12, 11, 1, 11, metal_fill(IRON))     # lid
box_uv(jar, 44, 0, 3, 2, 3, lambda f, x, y, w, h: COPPER[2] if y == 0 else COPPER[1])
box_uv(jar, 44, 5, 1, 6, 1, lambda f, x, y, w, h: COPPER[1] if y % 3 else COPPER[2])
box_uv(jar, 52, 5, 1, 6, 1, lambda f, x, y, w, h: COPPER[1] if y % 3 else COPPER[2])
box_uv(jar, 44, 14, 2, 1, 1, lambda f, x, y, w, h: GREEN[2])


def glass_fill(face, x, y, w, h):
    if face == 'bottom':
        return None
    if x in (0, w - 1) or y in (0, h - 1):
        return GLASS[1]
    if face != 'top' and x == 1 and 1 <= y <= 3:
        return GLASS[2]
    return hexc('bfeaf5', 38)


box_uv(jar, 0, 24, 10, 11, 10, glass_fill)


def fluid_fill(face, x, y, w, h):
    if face == 'top':
        return FLUID[2] if (x + y) % 5 == 0 else FLUID[1]
    return FLUID[1] if y > 0 else FLUID[2]


box_uv(jar, 0, 46, 9, 6.5, 9, fluid_fill)
save(jar, 'entity', 'jar.png')

stump = img(16, 16)
box_uv(stump, 0, 0, 5, 1, 4, lambda f, x, y, w, h: RED[1] if f == 'top' and 0 < x < w - 1 else SKIN[1])
box_uv(stump, 0, 5, 3, 1, 2, lambda f, x, y, w, h: hexc('d8d0c0') if f == 'top' else RED[2])
save(stump, 'entity', 'neck_stump.png')

# --- Jar menu (256x256, the 176x166 panel at 0,0, a shut-slot cover at 176,0) ------------------

gui = img(256, 256)
PANEL, LIGHT, DARK, SLOT = hexc('c6c6c6'), hexc('ffffff'), hexc('555555'), hexc('8b8b8b')
rect(gui, 0, 0, 175, 165, PANEL)
rect(gui, 0, 0, 175, 0, hexc('000000')); rect(gui, 0, 165, 175, 165, hexc('000000'))
rect(gui, 0, 0, 0, 165, hexc('000000')); rect(gui, 175, 0, 175, 165, hexc('000000'))
rect(gui, 1, 1, 174, 2, LIGHT); rect(gui, 1, 1, 2, 164, LIGHT)
rect(gui, 173, 1, 174, 164, DARK); rect(gui, 1, 163, 174, 164, DARK)


def slot(x, y):
    rect(gui, x - 1, y - 1, x + 16, y - 1, DARK); rect(gui, x - 1, y - 1, x - 1, y + 16, DARK)
    rect(gui, x, y + 16, x + 16, y + 16, LIGHT); rect(gui, x + 16, y, x + 16, y + 16, LIGHT)
    rect(gui, x, y, x + 15, y + 15, SLOT)


for i in range(5):
    slot(44 + i * 18, 28)
slot(152, 56)
# redstone hint in the feed slot
px(gui, [(158, 62), (160, 61), (159, 64), (161, 63), (157, 65)], hexc('9a2020', 160))
for row in range(3):
    for col in range(9):
        slot(8 + col * 18, 84 + row * 18)
for col in range(9):
    slot(8 + col * 18, 142)
# shut-slot cover
rect(gui, 176, 0, 193, 17, hexc('6a6a6a'))
for i in range(18):
    px(gui, [(176 + i, i), (193 - i, i)], hexc('4a4a4a'))
save(gui, 'gui', 'jar.png')
print('textures written')

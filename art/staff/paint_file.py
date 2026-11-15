"""The Personnel File card: textures/gui/personnel_file.png (256x256), painted from the palette.

    python art/staff/paint_file.py

What a stranger should say: "an office file card": a manila folder card with a tab, an ID photo held by a paperclip
and stamped by the company seal, ruled in blue with red header rules. No words are painted: every label and value is
drawn from lang by PersonnelFileScreen (so Portuguese fits), on the rules below.

The layout the screen draws to (sheet px; the card is blitted from (0, 0), CARD_W x CARD_H):
- CARD 208 x 180: the tab (x 8..160, y 0..12) and the body (y 10..180). Transparent outside the card. (The brief said
  "about 208x160"; 20 px more holds every row the plan lists at the font's full size, in English and Portuguese.)
- TAB: the title and the staff number, one row: text at x 14, y 3 (e.g. "PERSONNEL FILE  No. 0042"; room 144 px).
- PHOTO print x 10..54, y 20..64; its WINDOW x 12..52, y 22..62 (40 x 40) is where the live head is drawn.
- CLIP: the paperclip crosses the print's top edge at x 45..52, y 14..34; part of it lies over the window, so the screen
  should blit the CLIP sprite (sheet x 216..223, y 0..20: the clip alone, transparent elsewhere) after the head, at
  the card's (45, 14).
- SEAL: a red company seal (a lidded jar inside a double ring), centre (187, 26), radius 11.
- Rules, pitch 9 (the font's line height). A row's text sits on its rule: draw it at y = rule - 8 (7 px caps end 1 px
  above the rule; descenders touch it), 2 px in from the rule's left end.
  identity rows, right of the photo, x 60..200 (room 138 px): 29 (red: the name), 38, 47, 56, 65: name, species,
  department (and its line), employer, joined;
  lower rows, x 10..200 (room 188 px): 74, 83, 92, 101: morale, wage, jar, genes (101 is red: it closes the record);
  then the eight history lines on 110, 119, 128, 137, 146, 155, 164, 173.
  Long values (wage "none": 59 characters) need wrapping or a tooltip.
- A dog-eared corner at the bottom right (x 198..208, y 170..180); the last rule runs under the flap.
- The rules at y 29 and 38 stop at x 174 so the seal stands clear.
- Text colour: dark ink on manila: ink.0 (0x34405E) for values, rubber.2 (0x3A3941) for labels; red_ink.1 (0x96362F)
  for "Trophy" or "Left the company".
"""
import math

import pix

CARD_W, CARD_H = 208, 180
BODY_Y = 10
TAB = (8, 0, 160, 12)
PRINT = (10, 20, 54, 64)
WINDOW = (12, 22, 52, 62)
CLIP_AT = (45, 14)
CLIP_SPRITE = (216, 0)
SEAL_C, SEAL_R = (187, 26), 11
ID_RULES = (60, 200, [29, 38, 47, 56, 65], {29})          # x0, x1, ys, the red ones
LOW_RULES = (10, 200, [74, 83, 92, 101, 110, 119, 128, 137, 146, 155, 164, 173], {101})
DOGEAR = 10
SEAL_CLEAR = (174, [29, 38])                                 # rules that stop short of the seal at x 174

FIELD = 'manila.4'

# the paperclip, 7 x 20: L lit wire, M mid wire, D shaded wire; the outer loop closed at both ends, the inner loop
# hanging from its top bend, its right end free
CLIP = [
    '.LLLLL.',
    'L.....D',
    'L.MMM.D',
    'L.M.M.D',
    'L.M.M.D',
    'L.M.M.D',
    'L.M.M.D',
    'L.M.M.D',
    'L.M.M.D',
    'L.M.M.D',
    'L.M.M.D',
    'L.M.M.D',
    'L.M.M.D',
    'L.M.M.D',
    'L.M.M.D',
    'L.M.M.D',
    'L.M.M.D',
    'L.M...D',
    'L.M...D',
    '.DDDDD.',
]
CLIP_KEYS = {'L': 'steel.5', 'M': 'steel.4', 'D': 'steel.3'}

# the jar on the seal, 7 x 9 (lid, a solid neck, body with a 3x3 head inside), in red ink
SEAL_JAR = [
    '.xxxxx.',
    '..xxx..',
    '.xxxxx.',
    'x.....x',
    'x.xxx.x',
    'x.xxx.x',
    'x.xxx.x',
    'x.....x',
    '.xxxxx.',
]


def card(im):
    """The manila body and tab: flat card stock, a 1 px outline (step 1 top and left, step 0 bottom and right) and a
    1 px bevel inside it (step 5 top and left, step 3 bottom and right)."""
    W, H = CARD_W, CARD_H
    # tab first (its bottom is hidden under the body's field)
    tx0, ty0, tx1, ty1 = TAB
    pix.rect(im, tx0 + 2, ty0, tx1 - 2, ty1, 'manila.1')
    pix.rect(im, tx0 + 1, ty0 + 1, tx1 - 1, ty1, 'manila.1')
    pix.rect(im, tx0, ty0 + 2, tx1, ty1, 'manila.1')
    pix.rect(im, tx0 + 2, ty0 + 1, tx1 - 2, ty1, 'manila.5')
    pix.rect(im, tx0 + 1, ty0 + 2, tx1 - 1, ty1, 'manila.5')
    pix.rect(im, tx0 + 2, ty0 + 2, tx1 - 2, ty1, FIELD)
    pix.rect(im, tx1 - 2, ty0 + 2, tx1 - 1, ty1, 'manila.3')
    pix.put(im, tx1 - 1, ty0 + 2, 'manila.0')
    for y in range(ty0 + 3, ty1):
        pix.put(im, tx1 - 1, y, 'manila.0')
    # body
    pix.rect(im, 0, BODY_Y, W, H, 'manila.1')
    pix.rect(im, 1, BODY_Y + 1, W - 1, H - 1, 'manila.5')
    pix.rect(im, 2, BODY_Y + 2, W - 1, H - 1, 'manila.3')
    pix.rect(im, 2, BODY_Y + 2, W - 2, H - 2, FIELD)
    pix.rect(im, W - 1, BODY_Y, W, H, 'manila.0')
    pix.rect(im, 0, H - 1, W, H, 'manila.0')
    # the tab runs into the body: no outline or bevel across the join
    pix.rect(im, tx0 + 2, BODY_Y, tx1 - 2, BODY_Y + 2, FIELD)
    pix.put(im, tx0 + 1, BODY_Y, 'manila.5')
    pix.put(im, tx0 + 1, BODY_Y + 1, 'manila.5')
    pix.put(im, tx1 - 2, BODY_Y, 'manila.3')
    pix.put(im, tx1 - 2, BODY_Y + 1, FIELD)
    # card-stock fibres: a few placed streaks a half step lighter than the field, long and horizontal (no noise)
    for x, y, n in FIBRES:
        for i in range(n):
            pix.put(im, x + i, y, 'manila.4h')


# hand-placed fibre streaks (x, y, length); none since round 2 of review (they read as dust at GUI 2)
FIBRES = []


def photo(im):
    x0, y0, x1, y1 = PRINT
    # cast shadow of the print on the card (down and right, 1 px)
    pix.rect(im, x0 + 1, y1, x1 + 1, y1 + 1, 'manila.2')
    pix.rect(im, x1, y0 + 1, x1 + 1, y1 + 1, 'manila.2')
    # the print's white border: lit top and left, a step down at bottom and right
    pix.rect(im, x0, y0, x1, y1, 'enamel.5')
    pix.rect(im, x0 + 1, y1 - 1, x1, y1, 'enamel.3')
    pix.rect(im, x1 - 1, y0 + 1, x1, y1, 'enamel.3')
    pix.rect(im, x0 + 1, y0 + 1, x1 - 1, y1 - 1, 'enamel.4')
    # the window: a studio backdrop, recessed (1 px shadow top and left), a lighter band where the head stands
    wx0, wy0, wx1, wy1 = WINDOW
    pix.rect(im, wx0, wy0, wx1, wy1, 'ink.3')
    pix.rect(im, wx0, wy0, wx1, wy0 + 1, 'ink.1')
    pix.rect(im, wx0, wy0, wx0 + 1, wy1, 'ink.1')
    pix.rect(im, wx0 + 1, wy0 + 1, wx1, wy0 + 2, 'ink.2')
    pix.rect(im, wx0 + 1, wy0 + 1, wx0 + 2, wy1, 'ink.2')
    # the floor of the studio: the bottom 6 rows a step darker, so the jar has something to stand on
    pix.rect(im, wx0 + 2, wy1 - 6, wx1, wy1, 'ink.2')
    pix.rect(im, wx0 + 2, wy1 - 6, wx1, wy1 - 5, 'ink.3')


def clip_sprite(im, ox, oy, shadow_on):
    """The paperclip at (ox, oy) with its cast shadow (1 px down and right) painted with shadow_on(x, y) -> key."""
    for dy, row in enumerate(CLIP):
        for dx, ch in enumerate(row):
            if ch != '.':
                sx, sy = ox + dx + 1, oy + dy + 1
                inside = 0 <= sx - ox < 7 and 0 <= sy - oy < 20 and CLIP[sy - oy][sx - ox] != '.'
                if not inside:
                    k = shadow_on(sx, sy)
                    if k:
                        pix.put(im, sx, sy, k)
    for dy, row in enumerate(CLIP):
        for dx, ch in enumerate(row):
            if ch != '.':
                pix.put(im, ox + dx, oy + dy, CLIP_KEYS[ch])


def shadow_under(x, y):
    """What the clip's shadow darkens to, by what lies under it on the card."""
    wx0, wy0, wx1, wy1 = WINDOW
    px0, py0, px1, py1 = PRINT
    if wx0 <= x < wx1 and wy0 <= y < wy1:
        return 'ink.1'
    if px0 <= x < px1 and py0 <= y < py1:
        return 'enamel.2'
    return 'manila.2'


def rules(im):
    for x0, x1, ys, red in (ID_RULES, LOW_RULES):
        for y in ys:
            end = SEAL_CLEAR[0] if y in SEAL_CLEAR[1] else x1
            pix.rect(im, x0, y, end, y + 1, 'red_ink.2' if y in red else 'ink.3')


def ring(cx, cy, r):
    """Integer pixels of a 1 px circle of radius r (midpoint), as a sorted list."""
    pts = set()
    x, y, err = r, 0, 1 - r
    while x >= y:
        for a, b in ((x, y), (y, x), (-y, x), (-x, y), (-x, -y), (-y, -x), (y, -x), (x, -y)):
            pts.add((cx + a, cy + b))
        y += 1
        if err < 0:
            err += 2 * y + 1
        else:
            x -= 1
            err += 2 * (y - x) + 1
    return sorted(pts)


# where the stamp took less ink: angles in degrees (0 = right, counter-clockwise), the ring pixels nearest them are drawn
# in the lighter step. Placed by hand, not random.
SEAL_THIN = [20, 25, 110, 200, 205, 210, 300]


def seal(im):
    cx, cy = SEAL_C
    for r, key in ((SEAL_R, 'red_ink.1'), (SEAL_R - 2, 'red_ink.1')):
        for x, y in ring(cx, cy, r):
            ang = math.degrees(math.atan2(cy - y, x - cx)) % 360
            thin = any(min(abs(ang - t), 360 - abs(ang - t)) < 6 for t in SEAL_THIN)
            pix.put(im, x, y, 'red_ink.2' if thin else key)
    jx, jy = cx - 3, cy - 4
    for dy, row in enumerate(SEAL_JAR):
        for dx, ch in enumerate(row):
            if ch == 'x':
                pix.put(im, jx + dx, jy + dy, 'red_ink.1')


def paint():
    im = pix.blank(256, 256)
    card(im)
    rules(im)
    dogear_simple(im)
    photo(im)
    seal(im)
    clip_sprite(im, CLIP_AT[0], CLIP_AT[1], shadow_under)
    # the overdraw sprite: the same clip alone (no shadow, so nothing is painted over the head between the wires)
    clip_sprite(im, CLIP_SPRITE[0], CLIP_SPRITE[1], lambda x, y: None)
    return im


def dogear_simple(im):
    """The bottom-right corner folded over. In the n x n corner square (u, v from its top left): the corner beyond the
    fold line u + v = n - 1 is cut away; the flap (that corner mirrored over the fold) lies on the card as the triangle
    u + v < n - 1: the card's back (the same stock as the face) shaded in step 3 along the fold, its two free edges
    (u = 0, v = 0) lit in step 5 as the card's own top and left edges are, the fold line (the card's new corner) in
    step 1. Drawn after the rules: the last rule runs under the flap."""
    W, H, n = CARD_W, CARD_H, DOGEAR
    px = im.load()
    x0, y0 = W - n, H - n
    for v in range(n):
        for u in range(n):
            x, y = x0 + u, y0 + v
            if u + v > n - 1:
                px[x, y] = (0, 0, 0, 0)
            elif u + v == n - 1:
                pix.put(im, x, y, 'manila.1')
            elif u == 0 or v == 0:
                pix.put(im, x, y, 'manila.5')
            elif u + v == n - 2:
                pix.put(im, x, y, 'manila.3')
            else:
                pix.put(im, x, y, FIELD)


if __name__ == '__main__':
    print(pix.save(paint(), 'gui/personnel_file.png'))

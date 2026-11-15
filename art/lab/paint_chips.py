"""The Gene Splicer's department chips: textures/gui/lab_chips.png (256 x 32).

    python art/lab/paint_chips.py

Each Splicer row starts with a 12 x 12 chip in its department's colour with a two-letter mark; pressing it steps the
row to the next department that has a usable gene. Chip i sits at (16 i, 0) with the English marks and at (16 i, 16)
with the Portuguese ones (the screen picks the row by language). Order: Staff.DEPARTMENTS, then the Board, the Temp
Agency and General Staff.

A chip is a small enamelled tag: a rubber-black rim with its corners clipped, a lit top row and a shaded bottom row
(no side bevel, so a five-wide letter keeps a clear face beside it), and the mark in the house 3x5 capitals (the Safety Placard's lettering, with M, N and V
widened)
in black on the light faces, and light with a one-texel drop shadow in the bevel's shadow step on the dark faces
(as vanilla's own screen text is shadowed); every mark has a contrast of 3.8:1 or more against its face. Hover and press are drawn
by the screen's code. Every pixel is a palette key; nothing is random.
"""
from pix import blank, placard_text, put, rect, save

# (department id, English mark, Portuguese mark, face, light, shadow, mark colour)
DEPARTMENTS = [
    ('livestock', 'LV', 'PE', 'flesh.2', 'flesh.3', 'flesh.1', 'enamel.5'),
    ('field_office', 'FO', 'TC', 'manila.3', 'manila.4', 'manila.2', 'rubber.1'),
    ('logistics', 'LG', 'LO', 'leaf.3', 'jar_glass.4', 'leaf.2', 'rubber.1'),
    ('security', 'SE', 'SE', 'ink.1', 'ink.2', 'ink.0', 'enamel.5'),
    ('night_shift', 'NS', 'TN', 'violet.2', 'violet.3', 'violet.1', 'enamel.5'),
    ('facilities', 'FA', 'IN', 'steel.3', 'steel.4', 'steel.1', 'enamel.5'),
    ('accounts', 'AC', 'CT', 'indicator_amber.1', 'indicator_amber.2', 'indicator_amber.0', 'rubber.1'),
    ('legal', 'LE', 'JU', 'brass.1', 'brass.2', 'brass.0', 'brass.4'),
    ('sales', 'SA', 'VE', 'jar_glass.1', 'jar_glass.2', 'jar_glass.0', 'enamel.5'),
    ('marine', 'MD', 'DM', 'teal_glass.1', 'teal_glass.2', 'teal_glass.0', 'enamel.5'),
    ('demolitions', 'DE', 'DE', 'indicator_red.2', 'indicator_red.3', 'indicator_red.1', 'rubber.1'),
    ('senior_management', 'SM', 'AG', 'cyan.0', 'cyan.1', 'rubber.1', 'cyan.4'),
    ('board', 'BD', 'CS', 'rubber.2', 'rubber.3', 'rubber.1', 'brass.4'),
    ('temp_agency', 'TA', 'AT', 'warning.3', 'bone.3', 'warning.2', 'rubber.1'),
    ('general', 'GS', 'PG', 'enamel.3', 'enamel.4', 'enamel.2', 'rubber.1'),
]
# The faces: a set of house steps whose closest pair is far apart (CIELAB distance 21 or more between any two),
# with Accounts burnt orange and Demolitions vermilion so neither meets Livestock's rose or the Temps' yellow; a mark set five wide (M, N) is always light on a dark face, so it can fill the face without meeting the rim.
# The chips' own capitals: the house 3x5 letters, but M, N and V drawn 5 wide so they cannot be taken for H and U at
# GUI scale 2.
WIDE = {'M': ['#...#', '##.##', '#.#.#', '#...#', '#...#'], 'N': ['#...#', '##..#', '#.#.#', '#..##', '#...#'],
        'V': ['#...#', '#...#', '.#.#.', '.#.#.', '..#..']}
SIZE, PITCH = 12, 16
LIGHT_MARKS = ('enamel.5', 'violet.4', 'brass.4', 'warning.3', 'cyan.4')
RIM = 'rubber.0'


def glyph(ch):
    return WIDE.get(ch) or placard_text.glyph(ch)


def text_width(s):
    return sum(len(glyph(ch)[0]) + 1 for ch in s) - 1


def text(im, x0, y0, s, key):
    x = x0
    for ch in s:
        g = glyph(ch)
        for r, row in enumerate(g):
            for c, v in enumerate(row):
                if v == '#':
                    put(im, x + c, y0 + r, key)
        x += len(g[0]) + 1


def chip(im, x0, y0, mark, face, light, shadow, ink):
    rect(im, x0 + 1, y0, x0 + SIZE - 1, y0 + SIZE, RIM)          # the rim, corners clipped
    rect(im, x0, y0 + 1, x0 + SIZE, y0 + SIZE - 1, RIM)
    rect(im, x0 + 1, y0 + 1, x0 + SIZE - 1, y0 + SIZE - 1, face)
    for i in range(1, SIZE - 1):
        put(im, x0 + i, y0 + 1, light)                              # lit top row, shaded bottom row
        put(im, x0 + i, y0 + SIZE - 2, shadow)
    shadowed = ink in LIGHT_MARKS
    w = text_width(mark) + (1 if shadowed else 0)
    tx = x0 + 1 + (SIZE - 2 - w + 1) // 2
    ty = y0 + 3
    if shadowed:
        text(im, tx + 1, ty + 1, mark, shadow)                      # light marks get a drop shadow; dark ones none
    text(im, tx, ty, mark, ink)


def paint():
    im = blank(256, 32)
    for i, (_, en, pt, face, light, shadow, ink) in enumerate(DEPARTMENTS):
        chip(im, i * PITCH, 0, en, face, light, shadow, ink)
        chip(im, i * PITCH, 16, pt, face, light, shadow, ink)
    return im


if __name__ == '__main__':
    print(save(paint(), 'gui/lab_chips.png'))

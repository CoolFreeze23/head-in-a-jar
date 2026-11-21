"""Safety Placard: the notice layout. Checks that every notice in the game's lang file sets in the board's text field
in the house 3x5 pixel capitals, and draws the twelve fields (the reference sheet).

    python art/machines/safety_placard/placard_text.py [OUT.png]

The board's front face is one element, 12 x 12 block px, with its own texture `#notice` at 4x density (48 x 48
texels): the only finer-than-texel texture on the block, as vanilla's sign text is finer than its wood. Layout in
those texels (x right, y down from the board's top-left):
    header band   x 0..48, y 0..7     yellow, the black word NOTICE centred, letters at y 1..6
    rule          y 7..8              black
    text field    x 2..46, y 8..48    black capitals 5 texels tall: 3 wide, but N and the 0 are 4 wide, and M, V
                  and W 5 (N's diagonal runs to the foot: one that stopped half way read as M, as a 4-wide M did; a 3-wide V
                  read as U, a 4-wide W as H); 1 texel between letters, 6 a line; up to 6 lines,
                  44 texels a line, words never split; the lines and the block of lines centred (2-3 texels of
                  margin above and below a full block)
    screws        four 3x3 steel heads in the board's corners (the fixing, painted)
The field is 44 x 40 texels. Accented capitals (Brazilian Portuguese) carry their mark in two rows above the letter, so
a notice with any is set at 8 texels a line, at most 5 lines.
The longest notice, 2 ("THIS FACILITY HAS GONE 0 DAYS WITHOUT AN INCIDENT"), takes all six lines, broken by hand so
"0 DAYS" stays together like a day counter.
"""
import json
import os
import sys

FONT = {  # 3x5 capitals, rows top to bottom, '#' = ink
    'A': ['.#.', '#.#', '###', '#.#', '#.#'], 'B': ['##.', '#.#', '##.', '#.#', '##.'], 'C': ['.##', '#..', '#..', '#..', '.##'],
    'D': ['##.', '#.#', '#.#', '#.#', '##.'], 'E': ['###', '#..', '##.', '#..', '###'], 'F': ['###', '#..', '##.', '#..', '#..'],
    'G': ['.##', '#..', '#.#', '#.#', '.##'], 'H': ['#.#', '#.#', '###', '#.#', '#.#'], 'I': ['###', '.#.', '.#.', '.#.', '###'],
    'J': ['..#', '..#', '..#', '#.#', '.#.'], 'K': ['#.#', '#.#', '##.', '#.#', '#.#'], 'L': ['#..', '#..', '#..', '#..', '###'],
    'M': ['#...#', '##.##', '#.#.#', '#...#', '#...#'], 'N': ['#..#', '##.#', '##.#', '#.##', '#.##'], 'O': ['.#.', '#.#', '#.#', '#.#', '.#.'],
    'P': ['##.', '#.#', '##.', '#..', '#..'], 'Q': ['.#.', '#.#', '#.#', '##.', '.##'], 'R': ['##.', '#.#', '##.', '#.#', '#.#'],
    'S': ['.##', '#..', '.#.', '..#', '##.'], 'T': ['###', '.#.', '.#.', '.#.', '.#.'], 'U': ['#.#', '#.#', '#.#', '#.#', '###'],
    'V': ['#...#', '#...#', '.#.#.', '.#.#.', '..#..'], 'W': ['#...#', '#...#', '#.#.#', '##.##', '#...#'], 'X': ['#.#', '#.#', '.#.', '#.#', '#.#'],
    'Y': ['#.#', '#.#', '.#.', '.#.', '.#.'], 'Z': ['###', '..#', '.#.', '#..', '###'],
    '0': ['.##.', '#..#', '#..#', '#..#', '.##.'], '1': ['.#.', '##.', '.#.', '.#.', '###'], '2': ['##.', '..#', '.#.', '#..', '###'],
    '3': ['##.', '..#', '.#.', '..#', '##.'], '4': ['#.#', '#.#', '###', '..#', '..#'], '5': ['###', '#..', '##.', '..#', '##.'],
    '6': ['.##', '#..', '###', '#.#', '###'], '7': ['###', '..#', '.#.', '.#.', '.#.'], '8': ['###', '#.#', '###', '#.#', '###'],
    '9': ['###', '#.#', '###', '..#', '##.'], ' ': ['...'] * 5, ',': ['...', '...', '...', '.#.', '#..'],
    '.': ['...', '...', '...', '...', '.#.'], "'": ['.#.', '.#.', '...', '...', '...'], '!': ['.#.', '.#.', '.#.', '...', '.#.'],
    '&': ['.#.', '#.#', '.#.', '#.#', '.##'], '-': ['...', '...', '###', '...', '...'], '?': ['##.', '..#', '.#.', '...', '.#.'],
    ':': ['...', '.#.', '...', '.#.', '...'],
}
W_TEX, H_TEX = 48, 48
FIELD = (2, 8, 46, 48)           # x0, y0, x1, y1 in texels
CHARS, LINES = 11, 6          # (a guide: lines are measured in texels, M N W 0 being 4 wide)
FIELD_W = FIELD[2] - FIELD[0]  # 44 texels
# line breaks set by hand where the automatic ones would split a phrase (the day counter keeps "0 DAYS" together;
# the quiet floor keeps "THE WARDEN" together)
BREAKS = {('en_us', 2): ['THIS', 'FACILITY', 'HAS GONE', '0 DAYS', 'WITHOUT AN', 'INCIDENT'],
          ('en_us', 16): ['QUIET', 'FLOOR:', 'THE WARDEN', 'IS WORKING'],
          ('en_us', 18): ['PLEASE', 'DO NOT TAP', 'THE GLASS'],
          ('en_us', 12): ['A HEALTHY', 'BODY', 'IS A', 'PRODUCTIVE', 'BODY'],
          ('en_us', 13): ['HARD HATS', 'MUST BE', 'WORN', 'BEYOND', 'THIS POINT'],
          ('pt_br', 14): ['TURNO', 'DA NOITE', 'SÓ APÓS', 'ESCURECER'],
          ('pt_br', 19): ['TODOS DE', 'CRACHÁ.', 'ATÉ VOCÊ,', 'MIMOSA'],
          ('pt_br', 20): ['COLEGA', 'NÃO É', 'ALMOÇO'],
          ('pt_br', 2): ['ESTA', 'INSTALAÇÃO', 'ESTÁ HÁ', '0 DIAS SEM', 'ACIDENTES'],
          ('pt_br', 17): ['BOLO', 'RESOLVE', 'A MAIORIA', 'DAS QUEIXAS']}
# accented capitals: the plain letter with its mark in two rows above it (a cedilla in the row below); a notice that
# has any is set at 8 texels a line instead of 6, so at most 5 lines
ACUTE, GRAVE, CIRCUMFLEX, TILDE = ['..#', '.#.'], ['#..', '.#.'], ['.#.', '#.#'], ['.#.#', '#.#.']
ACCENTS = {'Á': ('A', ACUTE), 'É': ('E', ACUTE), 'Í': ('I', ACUTE), 'Ó': ('O', ACUTE), 'Ú': ('U', ACUTE), 'À': ('A', GRAVE),
           'Â': ('A', CIRCUMFLEX), 'Ê': ('E', CIRCUMFLEX), 'Ô': ('O', CIRCUMFLEX), 'Ã': ('A', TILDE), 'Õ': ('O', TILDE),
           'Ç': ('C', None)}
LANGS = ('en_us', 'pt_br')
HEADER = {'en_us': 'NOTICE', 'pt_br': 'AVISO'}


def glyph(ch):
    return FONT.get(ACCENTS[ch][0] if ch in ACCENTS else ch, FONT['?'])


def known(ch):
    return ch in FONT or ch in ACCENTS


def width(s):
    return sum(len(glyph(ch)[0]) + 1 for ch in s) - 1 if s else 0


LANG_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', 'src', 'main', 'resources', 'assets',
                        'headinajar', 'lang')


def accented(text):
    return any(ch in ACCENTS for ch in text)


def pitch(text):
    return 8 if accented(text) else 6


def max_lines(text):
    return 5 if accented(text) else LINES


def marks_above(line):
    """Whether a line has a capital whose mark sits above it (a cedilla hangs below, in the row under its line)."""
    return any(ch in ACCENTS and ACCENTS[ch][1] is not None for ch in line)


def line_tops(text, n=None, lang='en_us'):
    """The y of each line's letters (their top row). A notice is set at its pitch (6 texels a line, 8 with any accent),
    the block centred under the rule; one that would then run off the board or into a corner screw (pt_br 0, 3 and 6 did
    until 0.7.2: a cedilla hangs a row below the last line, and five lines at 8 fill the field to its last row) is set
    line by line instead: 6 a line, 2 more above a line with a mark over a letter, 1 more under a line with a cedilla."""
    lines = wrap(text, n, lang)
    step = pitch(text)
    lead = step - 6
    block = lead + (len(lines) - 1) * step + 5
    top = 8 + (H_TEX - 8 - block + 1) // 2
    tops = [top + lead + i * step for i in range(len(lines))]
    foot = tops[-1] + 4 + (1 if 'Ç' in lines[-1] else 0)    # the last row inked
    if foot <= H_TEX - 2 and not _hits_screw(lines, tops):
        return tops
    rows, ys = 0, []
    for i, ln in enumerate(lines):
        rows += 2 if marks_above(ln) else 0
        ys.append(rows)
        rows += 5 + (1 if 'Ç' in ln else 0) + (1 if i < len(lines) - 1 else 0)
    top = 8 + (H_TEX - 8 - rows + 1) // 2
    if _hits_screw(lines, [top + y for y in ys]):
        top = 9                                       # up under the rule, clear of the lower screws
    return [top + y for y in ys]


SCREWS = ((1, 9), (W_TEX - 4, 9), (1, H_TEX - 4), (W_TEX - 4, H_TEX - 4))


def _hits_screw(lines, tops):
    for ln, y in zip(lines, tops):
        y0, y1 = y - (2 if marks_above(ln) else 0), y + 4 + (1 if 'Ç' in ln else 0)
        x0 = FIELD[0] + (FIELD_W - width(ln)) // 2
        x1 = x0 + width(ln) - 1
        for sx, sy in SCREWS:
            if not (x1 < sx or x0 > sx + 2 or y1 < sy or y0 > sy + 2):
                return True
    return False


def wrap(text, n=None, lang='en_us'):
    if (lang, n) in BREAKS:
        return BREAKS[(lang, n)]
    lines, cur = [], ''
    for w in text.split():
        if width(w) > FIELD_W:
            return None
        t = (cur + ' ' + w).strip()
        if width(t) <= FIELD_W:
            cur = t
        else:
            lines.append(cur)
            cur = w
    lines.append(cur)
    return lines


def notices(lang='en_us', table=None):
    d = table if table is not None else json.load(open(os.path.join(LANG_DIR, lang + '.json'), encoding='utf-8'))
    return sorted((int(k.rsplit('.', 1)[1]), v) for k, v in d.items() if k.startswith('placard.headinajar.'))


def fits(text, n=None, lang='en_us'):
    """(whether the notice sets in the field, its lines)."""
    ln = wrap(text, n, lang)
    ok = (ln is not None and len(ln) <= max_lines(text) and all(known(ch) for ch in text)
          and all(width(x) <= FIELD_W for x in ln) and ' '.join(ln) == text)
    return ok, ln


def draw(text, scale=4, n=None, lang='en_us'):
    from PIL import Image
    # palette D (art/palette.json): ink r1 27262d, paper e3 ddd5c3 (the board's enamel), the
    # header w2 f4bd1e (the house warning yellow, kept apart from the brass frame b3 by hue and by the dark gap)
    ink, paper, yellow = (0x27, 0x26, 0x2d, 255), (0xdd, 0xd5, 0xc3, 255), (0xf4, 0xbd, 0x1e, 255)
    im = Image.new('RGBA', (W_TEX, H_TEX), paper)
    px = im.load()
    for y in range(0, 7):
        for x in range(W_TEX):
            px[x, y] = yellow
    for x in range(W_TEX):
        px[x, 7] = ink
    # four screw heads in the board's corners (3x3 texels: a steel head, its top-left lit, its bottom-right dark)
    steel, lit, dark = (123, 129, 139, 255), (206, 211, 216, 255), (66, 70, 79, 255)   # s3, s5, s1 (palette D)
    for cx, cy in ((1, 9), (W_TEX - 4, 9), (1, H_TEX - 4), (W_TEX - 4, H_TEX - 4)):
        for yy in range(3):
            for xx in range(3):
                px[cx + xx, cy + yy] = steel
        px[cx, cy] = lit
        px[cx + 2, cy + 2] = dark

    def dot(x, y):
        if 0 <= x < W_TEX and 0 <= y < H_TEX:
            px[x, y] = ink

    def put(s, x0, y0):
        x = x0
        for ch in s:
            g = glyph(ch)
            for r, row in enumerate(g):
                for c, v in enumerate(row):
                    if v == '#':
                        dot(x + c, y0 + r)
            if ch in ACCENTS:
                mark = ACCENTS[ch][1]
                if mark is None:
                    dot(x + 1, y0 + 5)                  # the cedilla, in the row under the letter
                else:
                    for r, row in enumerate(mark):
                        for c, v in enumerate(row):
                            if v == '#':
                                dot(x + c, y0 - 2 + r)
            x += len(g[0]) + 1
    header = HEADER.get(lang, 'NOTICE')
    put(header, (W_TEX - width(header)) // 2, 1)
    lines = wrap(text, n, lang)
    for ln, y in zip(lines, line_tops(text, n, lang)):  # the block of lines centred under the rule
        put(ln, FIELD[0] + (FIELD_W - width(ln)) // 2, y)
    return im.resize((W_TEX * scale, H_TEX * scale), Image.NEAREST)


if __name__ == '__main__':
    bad = []
    for lang in LANGS:
        for n, t in notices(lang):
            ok, ln = fits(t, n, lang)
            print('%s %2d %-60s %s' % (lang, n, t, ' / '.join(ln) if ln else 'A WORD TOO LONG'), '' if ok else '  <-- DOES NOT FIT')
            if not ok:
                bad.append((lang, n))
    if len(sys.argv) > 1:
        from PIL import Image, ImageDraw
        sheet = [(lang, n, t) for lang in LANGS for n, t in notices(lang)]
        tiles = [draw(t, n=n, lang=lang) for lang, n, t in sheet]
        w, h = tiles[0].size
        rows = (len(tiles) + 3) // 4
        out = Image.new('RGB', (4 * (w + 8), rows * (h + 24)), (24, 26, 32))
        d = ImageDraw.Draw(out)
        for i, (tile, (lang, n, t)) in enumerate(zip(tiles, sheet)):
            x, y = (i % 4) * (w + 8), (i // 4) * (h + 24)
            d.text((x + 2, y + 4), '%s notice %d (48 x 48 texels, shown 4x)' % (lang, n), fill=(230, 230, 230))
            out.paste(tile, (x, y + 20))
        out.save(sys.argv[1])
    sys.exit(1 if bad else 0)

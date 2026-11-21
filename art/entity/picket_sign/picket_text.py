"""Picket signs: the six slogans in English and Brazilian Portuguese, hand-lettered on cardboard in the house 3x5
capitals of the Safety Placard (art/machines/safety_placard/placard_text.py: same glyphs, accents, spacing).

    python art/entity/picket_sign/picket_text.py            # writes textures/picket/<lang>/sign_<n>.png (12 files)
    python art/entity/picket_sign/picket_text.py --check    # no writes: fails if a slogan does not fit or a PNG differs
    python art/entity/picket_sign/picket_text.py --sheet OUT.png   # every sign at 6x, for a look

The board's slogan face is 12 x 8 block px with its own texture at 4 texels a pixel, as the placard's notice is
(48 x 32 texels). Layout in texels (x right, y down from the top-left):
    edge          a 1-texel rim of darker card all round (the cut edge of the cardboard, seen face-on)
    field         x 2..46, y 2..30 (44 x 28): lines of capitals 5 texels tall (with an accented capital in the
                  slogan, 2 more rows above each line for the marks); 1 texel between letters, words never split;
                  every line and the block of lines centred
    tape          two strips of brown packing tape over the top corners (the board was taped up in a hurry)
From the last line up, each line that still fits with 2 texels to spare each side is lettered twice the size (each
texel 2 x 2): the sign shouts its last word (NOW, BROTH, LUNCH, MOO); lines are 2 texels apart. An accent sits one
clear row above its letter. The sign's W is its own (GLYPHS).
The data generator may draw the same signs from its own copy of the slogans with `draw(text, lang)`; then the texts must
be exactly the ones in SLOGANS, or its pictures and these differ.
"""
import io
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, '..', '..', '..'))
sys.path.insert(0, os.path.join(ROOT, 'art', 'machines', 'safety_placard'))
sys.path.insert(0, os.path.join(ROOT, 'art', 'staff'))
import placard_text as P  # noqa: E402
from staff_palette import C  # noqa: E402

TEX = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'headinajar', 'textures')
W, H = 48, 32
FIELD = (2, 2, 46, 30)
FIELD_W, FIELD_H = FIELD[2] - FIELD[0], FIELD[3] - FIELD[1]
LANGS = ('en_us', 'pt_br')
# the order the plan gives them; 5 (MOO) is carried by cows and mooshrooms only, one in three
SLOGANS = [
    {'en_us': 'DENTAL NOW', 'pt_br': 'DENTISTA JÁ'},
    {'en_us': 'WARMER BROTH', 'pt_br': 'CALDO QUENTE'},
    {'en_us': 'THE JAR IS NOT AN OFFICE', 'pt_br': 'POTE NÃO É ESCRITÓRIO'},
    {'en_us': 'WE ARE NOT LUNCH', 'pt_br': 'NÃO SOMOS ALMOÇO'},
    {'en_us': 'FAIR WAGES', 'pt_br': 'SALÁRIO JUSTO'},
    {'en_us': 'MOO', 'pt_br': 'MUU'},
]
# a sign is shouted, not read: one or two words a line where the field allows it
BREAKS = {
    'DENTAL NOW': ['DENTAL', 'NOW'],
    'DENTISTA JÁ': ['DENTISTA', 'JÁ'],
    'WARMER BROTH': ['WARMER', 'BROTH'],
    'CALDO QUENTE': ['CALDO', 'QUENTE'],
    'THE JAR IS NOT AN OFFICE': ['THE JAR', 'IS NOT AN', 'OFFICE'],
    'POTE NÃO É ESCRITÓRIO': ['POTE', 'NÃO É', 'ESCRITÓRIO'],
    'WE ARE NOT LUNCH': ['WE ARE', 'NOT', 'LUNCH'],
    'NÃO SOMOS ALMOÇO': ['NÃO', 'SOMOS', 'ALMOÇO'],
    'FAIR WAGES': ['FAIR', 'WAGES'],
    'SALÁRIO JUSTO': ['SALÁRIO', 'JUSTO'],
}
# The sign's own few letters where the placard's read wrong at a sign's size (a blind critic read the placard's W as H
# or N in NOW, WARMER, WE, WAGES): a W whose middle stroke runs to a pointed foot. Everything else is the placard's.
GLYPHS = {'W': ['#...#', '#...#', '#.#.#', '#.#.#', '.#.#.']}
GAP = 2   # texels between lines (the placard's is 1; a marker hand spaces them wider)
# palette keys: cardboard (manila), its lit top edge, its cut edge, marker black, packing tape
CARD, CARD_LIT, EDGE, INK, TAPE, TAPE_DARK = 'manila.5', 'manila.6', 'manila.3', 'rubber.1', 'manila.2', 'manila.1'


def glyph(ch):
    base = P.ACCENTS[ch][0] if ch in P.ACCENTS else ch
    return GLYPHS.get(base) or P.glyph(ch)


def width(s):
    return sum(len(glyph(ch)[0]) + 1 for ch in s) - 1 if s else 0


def lines(text):
    return BREAKS.get(text, P.wrap(text))


def _lead(text, k):
    """Rows above an accented line: the mark's two rows (each k texels) and one clear texel."""
    return 2 * k + 1 if P.accented(text) else 0


def _height(ln, ks, text):
    return sum(_lead(text, k) + 5 * k for k in ks) + GAP * (len(ln) - 1)


def layout(text):
    """[(line, size)]: every line in 1x capitals, then, from the last line up, each line that still fits doubled (each
    texel 2 x 2): a hand-lettered sign shouts its last word."""
    ln = lines(text)
    ks = [1] * len(ln)
    for i in reversed(range(len(ln))):
        trial = ks[:i] + [2] + ks[i + 1:]
        if width(ln[i]) * 2 <= FIELD_W - 4 and _height(ln, trial, text) <= FIELD_H:
            ks = trial
        else:
            break
    return list(zip(ln, ks))


def fits(text, lang='en_us'):
    """(whether the slogan sets in the field, its lines)."""
    ln = lines(text)
    lay = layout(text)
    ok = (ln is not None and ' '.join(ln) == text and all(P.known(ch) for ch in text)
          and all(width(x) * k <= FIELD_W for x, k in lay) and _height(ln, [k for _, k in lay], text) <= FIELD_H)
    return ok, ln


def draw(text, lang='en_us'):
    from PIL import Image
    im = Image.new('RGBA', (W, H), C(CARD))
    px = im.load()
    for x in range(W):
        px[x, 0] = px[x, H - 1] = C(EDGE)
    for y in range(H):
        px[0, y] = px[W - 1, y] = C(EDGE)
    # the light catching the card's top edge, one texel in
    for x in range(1, W - 1):
        px[x, 1] = C(CARD_LIT)
    # packing tape over the two top corners: a strip 3 texels wide at 45 degrees, its lower edge a step darker
    for y in range(H):
        for x in range(W):
            for d in (x + y, (W - 1 - x) + y):
                if 3 <= d <= 5:
                    px[x, y] = C(TAPE_DARK if d == 5 else TAPE)

    def dot(x, y, k):
        for a in range(k):
            for b in range(k):
                if 0 <= x + a < W and 0 <= y + b < H:
                    px[x + a, y + b] = C(INK)

    def put(s, x0, y0, k):
        x = x0
        for ch in s:
            g = glyph(ch)
            for r, row in enumerate(g):
                for c, v in enumerate(row):
                    if v == '#':
                        dot(x + c * k, y0 + r * k, k)
            if ch in P.ACCENTS:
                mark = P.ACCENTS[ch][1]
                if mark is None:
                    dot(x + k, y0 + 5 * k, k)
                else:
                    for r, row in enumerate(mark):
                        for c, v in enumerate(row):
                            if v == '#':
                                dot(x + c * k, y0 - (2 - r) * k - 1, k)
            x += (len(g[0]) + 1) * k
    lay = layout(text)
    y = FIELD[1] + (FIELD_H - _height([l for l, _ in lay], [k for _, k in lay], text) + 1) // 2
    for s, k in lay:
        y += _lead(text, k)
        put(s, FIELD[0] + (FIELD_W - width(s) * k + 1) // 2, y, k)
        y += 5 * k + GAP
    return im


def png(text, lang):
    buf = io.BytesIO()
    draw(text, lang).save(buf, 'PNG', optimize=False)
    return buf.getvalue()


def outputs():
    return {'picket/%s/sign_%d.png' % (lang, n): s[lang] for n, s in enumerate(SLOGANS) for lang in LANGS}


def main():
    args = sys.argv[1:]
    if args[:1] == ['--sheet']:
        from PIL import Image
        order = sorted(outputs().items(), key=lambda r: (int(r[0].rsplit('_', 1)[1][:-4]), r[0]))
        out = Image.new('RGB', (2 * (W * 6 + 12), 6 * (H * 6 + 12)), (40, 42, 48))
        for i, (rel, t) in enumerate(order):
            out.paste(draw(t).resize((W * 6, H * 6), Image.NEAREST), ((i % 2) * (W * 6 + 12), (i // 2) * (H * 6 + 12)))
        out.save(args[1])
        print(args[1])
        return
    check = '--check' in args
    bad = 0
    for rel, text in outputs().items():
        lang = rel.split('/')[1]
        ok, ln = fits(text, lang)
        if not ok:
            print('DOES NOT FIT', rel, text, ln)
            bad += 1
            continue
        path = os.path.join(TEX, rel)
        data = png(text, lang)
        if check:
            same = os.path.exists(path) and open(path, 'rb').read() == data
            print(('same ' if same else 'DIFF ') + rel)
            bad += not same
        else:
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, 'wb') as fh:
                fh.write(data)
            print(rel, ' / '.join(ln))
    sys.exit(1 if bad else 0)


if __name__ == '__main__':
    main()

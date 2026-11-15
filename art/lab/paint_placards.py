"""Safety Placard notices 16 and 18, in English and Brazilian Portuguese, drawn the way the twelve are.

    python art/lab/paint_placards.py            # writes textures/placard/<lang>/notice_16.png and notice_18.png
    python art/lab/paint_placards.py --check    # no writes: fails if a notice does not fit or a PNG differs

The notices are lettering, not painting: art/machines/safety_placard/placard_text.py sets each language line in the
board's 44 x 40 texel field in the house 3x5 capitals on the 48 x 48 notice texture, and the data generator draws every
`placard.headinajar.<n>` line of the lang files with it. This script draws the two new ones from the same texts the
lang files carry, with the generator's own call, so its PNGs and the generator's are the same bytes. If the lang text
changes, the generator's drawing is the one that ships.
"""
import argparse
import io
import os
import sys

from pix import TEX, placard_text

NOTICES = {
    16: {'en_us': 'QUIET FLOOR: THE WARDEN IS WORKING', 'pt_br': 'SILÊNCIO: WARDEN TRABALHANDO'},   # pt: the road sign
    # "HOMENS TRABALHANDO"; "ANDAR SILENCIOSO: O WARDEN ESTÁ TRABALHANDO" needs five lines and runs into the screws
    18: {'en_us': 'PLEASE DO NOT TAP THE GLASS', 'pt_br': 'FAVOR NÃO BATER NO VIDRO'},
}


def screws(text, n, lang):
    """The lines that run into one of the board's four 3x3 corner screws (the field check does not look at them)."""
    P = placard_text
    lines = P.wrap(text, n, lang)
    step = P.pitch(text)
    lead = step - 6
    block = lead + (len(lines) - 1) * step + 5
    top = 8 + (P.H_TEX - 8 - block + 1) // 2
    hit = []
    for i, ln in enumerate(lines):
        y1 = top + lead + i * step + 5
        y0 = y1 - 5 - (2 if P.accented(ln) else 0)
        x0 = P.FIELD[0] + (P.FIELD_W - P.width(ln)) // 2
        x1 = x0 + P.width(ln) - 1
        for sx, sy in ((1, 9), (P.W_TEX - 4, 9), (1, P.H_TEX - 4), (P.W_TEX - 4, P.H_TEX - 4)):
            if not (x1 < sx or x0 > sx + 2 or y1 < sy or y0 > sy + 2):
                hit.append(ln)
    return hit


def png(text, n, lang):
    buf = io.BytesIO()
    placard_text.draw(text, scale=1, n=n, lang=lang).save(buf, format='PNG', optimize=True)
    return buf.getvalue()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--check', action='store_true')
    a = ap.parse_args()
    bad = []
    for n, texts in NOTICES.items():
        for lang, text in texts.items():
            ok, lines = placard_text.fits(text, n, lang)
            print('%s %d %-45s %s' % (lang, n, text, ' / '.join(lines or [])), '' if ok else '  <-- DOES NOT FIT')
            if not ok or screws(text, n, lang):
                bad.append((lang, n, 'does not fit' if not ok else 'runs into a screw'))
                continue
            path = os.path.join(TEX, 'placard', lang, 'notice_%d.png' % n)
            data = png(text, n, lang)
            if a.check:
                if not os.path.exists(path) or open(path, 'rb').read() != data:
                    bad.append((lang, n, 'differs'))
            else:
                os.makedirs(os.path.dirname(path), exist_ok=True)
                with open(path, 'wb') as fh:
                    fh.write(data)
    if bad:
        print('problems: %r' % bad)
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main())

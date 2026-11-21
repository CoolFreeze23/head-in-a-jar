"""Safety Placard notices 12 to 20, in English and Brazilian Portuguese, drawn the way the twelve are.

    python art/lab/paint_placards.py            # writes textures/placard/<lang>/notice_<n>.png for each
    python art/lab/paint_placards.py --check    # no writes: fails if a notice does not fit or a PNG differs

The notices are lettering, not painting: art/machines/safety_placard/placard_text.py sets each language line in the
board's 44 x 40 texel field in the house 3x5 capitals on the 48 x 48 notice texture, and the data generator draws every
`placard.headinajar.<n>` line of the lang files with it. This script draws the new ones from the same texts the
lang files carry, with the generator's own call, so its PNGs and the generator's are the same bytes. If the lang text
changes, the generator's drawing is the one that ships.
"""
import argparse
import io
import os
import sys

from pix import TEX, placard_text

NOTICES = {
    12: {'en_us': 'A HEALTHY BODY IS A PRODUCTIVE BODY', 'pt_br': 'CORPO SAUDÁVEL, CORPO PRODUTIVO'},   # en: hand break, BODY
    # on lines of its own (the natural wrap gives BODY IS A); pt wraps the same way by itself
    16: {'en_us': 'QUIET FLOOR: THE WARDEN IS WORKING', 'pt_br': 'SILÊNCIO: WARDEN TRABALHANDO'},   # pt: the road sign
    # "HOMENS TRABALHANDO"; "ANDAR SILENCIOSO: O WARDEN ESTÁ TRABALHANDO" needs five lines and runs into the screws
    18: {'en_us': 'PLEASE DO NOT TAP THE GLASS', 'pt_br': 'FAVOR NÃO BATER NO VIDRO'},
    13: {'en_us': 'HARD HATS MUST BE WORN BEYOND THIS POINT', 'pt_br': 'CAPACETE OBRIGATÓRIO A PARTIR DAQUI'},
    # en: hand break, THIS POINT kept together (the natural wrap gives BEYOND THIS / POINT); pt wraps by itself in four
    # lines ("USO OBRIGATÓRIO DE CAPACETE A PARTIR DAQUI" takes five and runs to the board's last texel row)
    14: {'en_us': 'NIGHT SHIFT ONLY AFTER DUSK', 'pt_br': 'TURNO DA NOITE SÓ APÓS ESCURECER'},
    # pt: hand break, SÓ APÓS kept together (the natural wrap gives NOITE SÓ / APÓS)
    19: {'en_us': 'ALL STAFF MUST WEAR LANYARDS. THIS MEANS YOU, BESSIE',
         'pt_br': 'TODOS DE CRACHÁ. ATÉ VOCÊ, MIMOSA'},   # en wraps by itself; pt: hand break TODOS DE / CRACHÁ. /
    # ATÉ VOCÊ, / MIMOSA (four lines; "ISSO INCLUI VOCÊ" took five); MIMOSA lands on a line of her own in both
    20: {'en_us': 'STAFF ARE NOT LUNCH', 'pt_br': 'COLEGA NÃO É ALMOÇO'},   # pt: FUNCIONÁRIO is 45 texels, one more than
    # the field's 44, so "FUNCIONÁRIO NÃO É ALMOÇO" cannot set; COLEGA ("colleague") keeps the deadpan; hand break
    # COLEGA / NÃO É / ALMOÇO (the natural wrap splits NÃO from É)
    15: {'en_us': 'STRIKES WILL BE MET WITH CAKE', 'pt_br': 'GREVES SERÃO RECEBIDAS COM BOLO'},   # both wrap by
    # themselves in four lines, CAKE and COM BOLO last
    17: {'en_us': 'CAKE RESOLVES MOST GRIEVANCES', 'pt_br': 'BOLO RESOLVE A MAIORIA DAS QUEIXAS'},   # pt: hand break
    # BOLO / RESOLVE / A MAIORIA / DAS QUEIXAS (the natural wrap leaves the article at the end of a line: RESOLVE A)
}


def screws(text, n, lang):
    """The lines that run into one of the board's four 3x3 corner screws (the field check does not look at them)."""
    P = placard_text
    lines = P.wrap(text, n, lang)
    return [ln for ln, y in zip(lines, P.line_tops(text, n, lang)) if P._hits_screw([ln], [y])]


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

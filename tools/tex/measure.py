"""Measurements from the texture style rules (colour count, largest colour share, neighbour jumps, regions,
lone pixels, palette membership, alpha levels, emissive share) and the pass or fail verdict per rule number.

Adapted from the study scripts texmeasure.py and grain.py (same definitions, so numbers compare with the study's
CSVs): luminance is Rec. 709 (0.2126 R + 0.7152 G + 0.0722 B); a jump is a 4-neighbour pair of opaque pixels
whose luminance differs by 25 or more; a region is a 4-connected run of one RGB colour; a lone pixel is a region
of size one. Only the first frame of an animated strip is measured.
"""
from collections import Counter


def lum(c):
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def metrics(im, frame_h=None):
    im = im.convert('RGBA')
    w, h = im.size
    if frame_h and frame_h < h:
        im = im.crop((0, 0, w, frame_h))
        h = frame_h
    px = im.load()
    opaque = [(x, y) for y in range(h) for x in range(w) if px[x, y][3] > 0]
    n = len(opaque)
    m = dict(w=w, h=h, opaque=n)
    if not n:
        return m
    cols = Counter(px[x, y][:3] for x, y in opaque)
    m['colours'] = len(cols)
    top_c, top_n = cols.most_common(1)[0]
    m['top_colour'] = '%02x%02x%02x' % top_c
    m['top_share'] = top_n / n
    pairs = jumps = sh = sv = nh = nv = 0
    dsum = 0.0
    for x, y in opaque:
        c = px[x, y]
        if x + 1 < w and px[x + 1, y][3]:
            d = abs(lum(c) - lum(px[x + 1, y])); pairs += 1; dsum += d; jumps += d >= 25
            nh += 1; sh += c[:3] == px[x + 1, y][:3]
        if y + 1 < h and px[x, y + 1][3]:
            d = abs(lum(c) - lum(px[x, y + 1])); pairs += 1; dsum += d; jumps += d >= 25
            nv += 1; sv += c[:3] == px[x, y + 1][:3]
    m['jumps'] = jumps / pairs if pairs else 0.0
    m['mean_dl'] = dsum / pairs if pairs else 0.0
    m['same_h'] = sh / nh if nh else 0.0
    m['same_v'] = sv / nv if nv else 0.0
    seen = set()
    sizes = []
    for x, y in opaque:
        if (x, y) in seen:
            continue
        c = px[x, y][:3]
        st = [(x, y)]
        seen.add((x, y))
        k = 0
        while st:
            a, b = st.pop()
            k += 1
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = (a + dx, b + dy)
                if 0 <= q[0] < w and 0 <= q[1] < h and q not in seen and px[q][3] and px[q][:3] == c:
                    seen.add(q)
                    st.append(q)
        sizes.append(k)
    m['regions'] = len(sizes)
    m['lone_share'] = sum(1 for s in sizes if s == 1) / n
    m['alphas'] = sorted({px[x, y][3] for y in range(h) for x in range(w)})
    m['lum_lo'] = min(lum(c) for c in cols)
    m['lum_hi'] = max(lum(c) for c in cols)
    # outline: opaque pixels with a transparent (or off-canvas) 4-neighbour
    ring = Counter()
    for x, y in opaque:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if not (0 <= q[0] < w and 0 <= q[1] < h) or px[q][3] == 0:
                ring[px[x, y][:3]] += 1
                break
    m['outline_colours'] = len(ring)
    m['cols'] = cols
    return m


def kind_of(rel):
    top = rel.split('/')[0] if '/' in rel else ''
    return {'block': 'block', 'item': 'item', 'entity': 'entity', 'gui': 'gui'}.get(top, 'other')


def verdicts(m, rel, palette_colours, kind=None, glow=None):
    """Returns [(rule, verdict, text)], verdict in PASS / FAIL / info / n/a."""
    kind = kind or kind_of(rel)
    out = []
    if not m.get('opaque'):
        return [('-', 'FAIL', 'texture is fully transparent')]
    is16 = (m['w'], m['h']) == (16, 16)
    face = kind == 'block' and is16

    def add(rule, ok, text, applies=True):
        out.append((rule, ('PASS' if ok else 'FAIL') if applies else 'n/a', text))

    off = {c: k for c, k in m['cols'].items() if c not in palette_colours}
    offn = sum(off.values())
    worst = ', '.join('%02x%02x%02x x%d' % (c + (k,)) for c, k in sorted(off.items(), key=lambda t: -t[1])[:4])
    add('R1', not off, 'palette membership: %d of %d colours off the house palette (%d px)%s'
        % (len(off), m['colours'], offn, (': ' + worst) if off else ''))
    blacks = [c for c in m['cols'] if lum(c) < 15]
    whites = m['cols'].get((255, 255, 255), 0)
    prim = [c for c in m['cols'] if c in ((255, 0, 0), (0, 255, 0), (0, 0, 255))]
    add('R8', not blacks and whites <= 2 and not prim,
        'darkest luminance %.0f (min 15), pure white %d px (max 2, glints only), pure primaries %d'
        % (m['lum_lo'], whites, len(prim)))
    add('R3', 6 <= m['colours'] <= 14, 'colour count %d (6 to 14)' % m['colours'], is16)
    add('R5', m['top_share'] <= 0.35, 'largest colour share %.0f%% (%s; at most 35%% on a block face unless '
        'it is one deliberate plane)' % (100 * m['top_share'], m['top_colour']), face)
    add('R11', m['jumps'] < 0.40, 'neighbour-jump ratio %.2f (whole face under 0.40, filler under 0.15)'
        % m['jumps'], face)
    add('R12', True, 'grain: same colour along rows %.0f%%, along columns %.0f%% (brushed metal, wood, hose: '
        'along the grain at least twice across it)' % (100 * m['same_h'], 100 * m['same_v']), False)
    out[-1] = ('R12', 'info', out[-1][2])
    add('R19', 60 <= m['regions'] <= 140, 'region count %d (60 to 140 on a 16x16 machine face)' % m['regions'], face)
    add('R21', 0.20 <= m['lone_share'] <= 0.45, 'lone-pixel share %.0f%% (20%% to 45%% on a face)'
        % (100 * m['lone_share']), face)
    if kind == 'item':
        add('R13', m['outline_colours'] > 1, 'outline colours %d (selout: shaded by material, never one global '
            'colour; heuristic)' % m['outline_colours'])
        a = [x for x in m['alphas'] if x not in (0, 255)]
        add('R28', not a, 'alpha levels %s (items are never translucent)' % m['alphas'])
    else:
        a = [x for x in m['alphas'] if x not in (0, 255)]
        add('R28', len(a) <= 2, 'alpha levels %s (cutout glass 0/255; tinted glass two levels)' % m['alphas'])
    if glow is not None:
        share = glow / m['opaque']
        lim = 0.15 if is16 else 0.04
        add('R22', share <= lim, 'emissive share %.1f%% (at most %d%% %s)'
            % (100 * share, 100 * lim, 'of a 16x16 face' if is16 else 'of a model texture'))
    if not is16:
        out.append(('-', 'info', 'size %dx%d: the colour, share, jump, region and lone-pixel limits are written '
                    'for 16x16 faces, so they are shown for reference only' % (m['w'], m['h'])))
    if kind not in ('block',) and is16:
        out.append(('-', 'info', 'kind %s: R5, R11, R19 and R21 are block-face rules; numbers: share %.0f%%, '
                    'jumps %.2f, regions %d, lone %.0f%% (vanilla items median 23%%, 0.47, 54, 29%%)'
                    % (kind, 100 * m['top_share'], m['jumps'], m['regions'], 100 * m['lone_share'])))
    return out

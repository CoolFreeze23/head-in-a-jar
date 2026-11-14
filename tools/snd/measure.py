"""Measure every sound the recipes make against the style guide's checklist, beside its named vanilla reference.

    python tools/snd/measure.py --refs DIR [--out DIR] [--spectro] [event ids...]

--refs: a folder holding copies of the vanilla reference files named as in the recipes (for example
        block__iron_trapdoor__close1.ogg). They are for measuring only and never enter the project.
--out:  where the CSV, the markdown table and (with --spectro) the spectrogram pairs go (default: --refs/..).

Per file: codec, channels and rate; peak (dBFS, decoded); DC; M max (K-weighted momentary maximum, 400 ms window);
centroid; heard length (until the 10 ms envelope last exceeds the loudest frame minus 40 dB); the last 50 ms against
the loudest 10 ms; first and last sample; hard cuts (nsynth.steps); onset; time of the loudest 5 ms frame.
References are measured as the game plays them: decoded at 44.1 kHz, resampled for their in-game pitch, scaled by
their in-game volume.
"""
import argparse
import csv
import itertools
import json
import os
import subprocess
import sys

import numpy as np
from scipy import signal

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import nsynth  # noqa: E402
from build import ALL, SOUNDS_DIR, files_of  # noqa: E402

SR = 44100
KINDS = {'click': (0.03, 0.15), 'latch': (0.15, 0.40), 'hiss': (0.6, 1.6), 'glug': (0.4, 0.8), 'stinger': (0.6, 1.5)}


def probe(path):
    out = subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'stream=channels,sample_rate,codec_name', '-of',
                          'json', path], capture_output=True, text=True, check=True).stdout
    s = json.loads(out)['streams'][0]
    return s['codec_name'], int(s['channels']), int(s['sample_rate'])


def decode(path):
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', path, '-ac', '1', '-ar', str(SR), '-f', 'f32le', '-'],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, '<f4').astype(np.float64)


def as_played(x, vol=1.0, pitch=1.0):
    if pitch != 1.0:
        n = int(len(x) / pitch)
        x = np.interp(np.arange(n) * pitch, np.arange(len(x)), x)
    return x * vol


def env(x, h):
    m = len(x) // h
    return np.sqrt((x[:m * h].reshape(m, h) ** 2).mean(1)) + 1e-12


def stats(x):
    e10 = env(x, 441)
    e5 = env(x, 220)
    thr = e10.max() * 10 ** (-2)
    above = np.where(e10 > thr)[0]
    f, P = signal.welch(x, SR, nperseg=min(4096, max(256, len(x) // 4)))
    onset_i = np.where(np.abs(x) > np.abs(x).max() * 10 ** (-30 / 20))[0]
    return dict(
        peak=round(20 * np.log10(np.abs(x).max() + 1e-12), 2),
        dc=round(float(x.mean()), 5),
        m_max=round(nsynth.m_max(x), 2),
        centroid=round(float((f * P).sum() / P.sum())),
        heard=round((above[-1] + 1) * 0.01, 3) if len(above) else 0.0,
        dur=round(len(x) / SR, 3),
        tail50=round(20 * np.log10(e10[-5:].mean() / e10.max()), 1),
        first=round(abs(x[0]), 5), last=round(abs(x[-1]), 5),
        cuts=nsynth.steps(x),
        onset_ms=round(onset_i[0] / SR * 1000, 1) if len(onset_i) else None,
        loudest_ms=round(int(np.argmax(e5)) * 220 / SR * 1000, 1))


def is_loop(s):
    k = s['kind']
    return s.get('loop') or k == 'loop' or isinstance(k, (list, tuple)) and k[0] == 'loop'


def seam(x):
    """A loop's seam against its own insides: the step from the last sample to the first over the 99.9th percentile
    of the file's sample-to-sample steps (1 or less: no click), and the level of the last 50 ms against the first 50 ms
    in dB (0: no jump in loudness across the join)."""
    d = np.abs(np.diff(x))
    jump = abs(x[0] - x[-1]) / (np.percentile(d, 99.9) + 1e-12)
    n = int(0.05 * SR)
    a, b = np.sqrt((x[:n] ** 2).mean()) + 1e-12, np.sqrt((x[-n:] ** 2).mean()) + 1e-12
    return round(float(jump), 2), round(float(20 * np.log10(b / a)), 1)


def heard_range(s):
    k = s['kind']
    if is_loop(s):
        return (0.5, 30.0)
    if isinstance(k, (list, tuple)):  # ('servo', move seconds)
        return round(k[1] - 0.02, 3), round(k[1] + 0.30, 3)  # as long as the move (10 ms frames), plus the stop's ring
    return KINDS[k]


def check(s, st, fmt, ref):
    lo, hi = heard_range(s)
    res = {}
    if is_loop(s):
        # a loop is heard played round and round: its loudness is the tiled file's, its ends join instead of fading
        res['format'] = fmt == ('vorbis', 1, 44100)
        res['peak'] = st['peak'] <= -1.0
        res['dc'] = abs(st['dc']) < 0.001
        res['class'] = abs(st['m_max'] - s['target']) <= 2.0
        res['seam'] = st['seam'][0] <= 1.0 and abs(st['seam'][1]) <= 3.0
        return res
    res['format'] = fmt == ('vorbis', 1, 44100)
    res['peak'] = st['peak'] <= -1.0
    res['dc'] = abs(st['dc']) < 0.001
    res['class'] = abs(st['m_max'] - s['target']) <= 2.0
    res['vs_ref'] = abs(st['m_max'] - ref['m_max']) <= 2.0
    res['centroid'] = 0.5 <= st['centroid'] / ref['centroid'] <= 2.0
    res['length'] = lo - 0.005 <= st['heard'] <= hi + 0.005
    res['tail'] = st['tail50'] <= -40.0
    res['no_cut'] = not st['cuts']
    # the encoder's pre-echo puts a little energy in the first samples (vanilla's files too: down to -19 dB under
    # their peak); the file is exactly 0 at both ends before encoding (nsynth.finish), and decoded the first sample
    # must sit 40 dB under the peak (no audible step) and the last within 1e-3 of zero.
    res['edges'] = st['first'] <= 10 ** ((st['peak'] - 40) / 20) and st['last'] < 1e-3
    res['onset'] = st['onset_ms'] is not None and st['onset_ms'] <= 5.0
    if s.get('preroll_ticks'):
        res['hit_time'] = abs(st['loudest_ms'] - s['hit_ms']) <= 8.0
    return res


def spectro(paths_labels, out_png, dur):
    from PIL import Image, ImageDraw
    tiles = []
    tmp = []
    for x, label in paths_labels:
        wav = out_png + f'.{len(tmp)}.wav'
        nsynth.write_wav(wav, x / max(1e-9, np.abs(x).max()) * 0.9)
        png = wav + '.png'
        subprocess.run(['ffmpeg', '-y', '-v', 'error', '-i', wav, '-lavfi',
                        f'apad=whole_dur={dur},atrim=0:{dur},showspectrumpic=s=480x300:legend=1:scale=log:color=intensity:stop=16000',
                        png], check=True)
        im = Image.open(png).convert('RGB')
        lab = Image.new('RGB', (im.width, 28), (255, 255, 255))
        ImageDraw.Draw(lab).text((8, 8), label, fill=(0, 0, 0))
        tile = Image.new('RGB', (im.width, im.height + 28), (255, 255, 255))
        tile.paste(lab, (0, 0))
        tile.paste(im, (0, 28))
        tiles.append(tile)
        tmp += [wav, png]
    W = sum(t.width for t in tiles)
    H = max(t.height for t in tiles)
    pair = Image.new('RGB', (W, H), (255, 255, 255))
    xo = 0
    for t in tiles:
        pair.paste(t, (xo, 0))
        xo += t.width
    pair.save(out_png)
    for f in tmp:
        os.remove(f)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--refs', required=True)
    ap.add_argument('--out')
    ap.add_argument('--spectro', action='store_true')
    ap.add_argument('ids', nargs='*')
    a = ap.parse_args()
    out = a.out or os.path.dirname(os.path.abspath(a.refs))
    os.makedirs(os.path.join(out, 'spec'), exist_ok=True)
    rows = []
    fails = []
    vanilla_rows = []
    for s in ALL:
        if s.get('vanilla_measure') and (not a.ids or s['id'] in a.ids):
            lo, hi = s['pitch']
            for vf in s['vanilla_measure']:
                st = stats(as_played(decode(os.path.join(a.refs, vf)), s['volume'], (lo + hi) / 2))
                vanilla_rows.append((s['id'], vf, (lo + hi) / 2, s['target'], st))
        if not s.get('fn') or (a.ids and s['id'] not in a.ids):
            continue
        r = s['ref']
        rx = as_played(decode(os.path.join(a.refs, r['file'])), r['vol'], r['pitch'])
        rst = stats(rx)
        takes = []
        for f in files_of(s):
            path = os.path.join(SOUNDS_DIR, *(f + '.ogg').split('/'))
            fmt = probe(path)
            x = decode(path)
            st = stats(x)
            if is_loop(s):
                st['m_max'] = round(nsynth.m_max(np.tile(x, 3)), 2)
                st['seam'] = seam(x)
            res = check(s, st, fmt, rst)
            pitches = s.get('pitch_per_play') or []
            extra = {}
            for p in pitches:  # the same file as the code plays it at each fixed pitch
                extra[p] = stats(as_played(x, 1.0, p))
            takes.append((f, st, x))
            ok = all(res.values())
            if not ok:
                fails.append((f, [k for k, v in res.items() if not v]))
            rows.append(dict(event=s['id'], file=f, cls=s['cls'], target=s['target'], ref=f"{r['event']} ({r['file']}, vol {r['vol']}, pitch {r['pitch']})",
                             ref_m_max=rst['m_max'], ref_centroid=rst['centroid'], ref_heard=rst['heard'],
                             heard_range='-'.join(str(v) for v in heard_range(s)), hit_ms=s.get('hit_ms'),
                             preroll_ticks=s.get('preroll_ticks', 0), **{k: st[k] for k in st if k not in ('cuts', 'seam')}, seam=st.get('seam', ''),
                             cuts=';'.join(map(str, st['cuts'])) or 'none', fails=';'.join(k for k, v in res.items() if not v) or 'PASS',
                             at_pitches=';'.join(f"p{p}: M {e['m_max']} cen {e['centroid']} heard {e['heard']}" for p, e in extra.items())))
        # takes differ by 5% in centroid or heard length
        for (fa, sa, _), (fb, sb, _) in itertools.combinations(takes, 2):
            dc = abs(sa['centroid'] - sb['centroid']) / min(sa['centroid'], sb['centroid'])
            dl = abs(sa['heard'] - sb['heard']) / min(sa['heard'], sb['heard'])
            if max(dc, dl) < 0.05:
                fails.append((f"{fa} vs {fb}", [f'takes too alike (centroid {dc:.1%}, length {dl:.1%})']))
        if a.spectro:
            x1 = takes[0][2]
            dur = round(max(len(x1), len(rx)) / SR + 0.05, 2)
            spectro([(x1, f"{takes[0][0]}  (M {takes[0][1]['m_max']}, {takes[0][1]['centroid']} Hz)"),
                     (rx, f"ref {r['event']} as played (M {rst['m_max']}, {rst['centroid']} Hz)")],
                    os.path.join(out, 'spec', f"pair_{s['id'].replace('.', '_')}.png"), dur)
    cols = list(rows[0].keys()) if rows else []
    with open(os.path.join(out, 'measure.csv'), 'w', newline='', encoding='utf-8') as fh:
        w = csv.DictWriter(fh, cols)
        w.writeheader()
        w.writerows(rows)
    print('file\tM max (target / ref)\tcentroid (ref)\theard (range)\tpeak\ttail50\tresult')
    for r in rows:
        print(f"{r['file']}\t{r['m_max']} ({r['target']} / {r['ref_m_max']})\t{r['centroid']} ({r['ref_centroid']})\t"
              f"{r['heard']} ({r['heard_range']})\t{r['peak']}\t{r['tail50']}\t{r['fails']}"
              + (f"\t[{r['at_pitches']}]" if r['at_pitches'] else ''))
    if vanilla_rows:
        print('\nvanilla files referenced by events, as played (volume, mid pitch):')
        for sid, vf, p, tgt, st in vanilla_rows:
            print(f"{sid}\t{vf}\tpitch {p:.2f}\tM {st['m_max']} (class {tgt})\tcentroid {st['centroid']}\theard {st['heard']}")
    print()
    print('FAILS:' if fails else 'all checks pass')
    for f, why in fails:
        print(' ', f, why)


if __name__ == '__main__':
    main()

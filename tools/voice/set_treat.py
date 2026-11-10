"""DOLORES's in-game set: every raw take through one of treat.py's chains (treat.py's P, M max -24,
bit-exact OGG), plus muffled copies of the lines the patient hears between the gas and the lid.
System Python:  python -I set_treat.py CANDIDATE TREATMENT RAWDIR OUTDIR [fc ...]
    e.g.  python -I set_treat.py voxcpm2 ringmod set_vox set_vox 1400
Seeds are treat.py's: crc32(candidate, treatment, lang, line id).
Writes <OUTDIR>\\<lang>\\<id>__<variant>.ogg and ..._muffled<fc>.ogg, and <OUTDIR>\\treat_log.json."""
import os, glob, json, os, sys
import numpy as np
from scipy import signal

sys.path.insert(0, os.path.dirname(__file__))
import treat as T  # noqa: E402

N = T.N
ROOT = os.environ['VOICE_WORK']
MUFFLE = dict(order=2, gain_db=-6.0)  # low-pass (cutoff chosen by intelligibility), 6 dB under the clear line


def cut_dead_air(x, sr, gap_s=0.2, quiet_db=-60.0, speech_db=-30.0):
    """Drop a stretch before the first word (or after the last) that is cut off from the speech by at least gap_s of
    near silence: some takes open with a faint blip, then about a second of silence. A take with no such gap is returned
    unchanged."""
    h = int(0.01 * sr); m = len(x) // h
    e = 20 * np.log10(np.sqrt((x[:m * h].reshape(m, h) ** 2).mean(1)) + 1e-12); e -= e.max()
    sp = np.where(e > speech_db)[0]
    q = e < quiet_db
    need = int(round(gap_s / 0.01))
    start, end = 0, len(x)
    lead = sp[0]
    while lead > 0 and not q[lead - 1]:
        lead -= 1  # back to the edge of the silence before the first word
    if lead >= need and q[lead - need:lead].all() and not q[:lead - need].all():
        start = lead * h  # something (a blip) before a gap of silence: start at the edge of the speech
    tail = sp[-1]
    while tail < m - 1 and not q[tail + 1]:
        tail += 1
    if tail + 1 + need <= m and q[tail + 1:tail + 1 + need].all() and not q[tail + 1:].all():
        end = (tail + 1) * h
    return x[start:end]


def muffle(y, fc):
    z = signal.sosfilt(signal.butter(MUFFLE['order'], fc, 'lowpass', fs=T.SR, output='sos'), y)
    return N.finish(z, T.TARGET + MUFFLE['gain_db'], fade_in_ms=1.0, fade_out_ms=15.0)


def main(cand, treat, rawdir, outdir, fcs):
    script = {L['id']: L for L in json.load(open(os.path.join(os.path.dirname(__file__), 'set_script.json'),
                                                   encoding='utf-8'))['lines']}
    rows = []
    for f in sorted(glob.glob(os.path.join(ROOT, 'raw', rawdir, '*.wav'))):
        name = os.path.basename(f)[:-4]
        lang, rest = name.split('_', 1)
        lid, var = rest.split('__')
        x, sr = T.read_wav(f)
        x = T.trim(T.to44(cut_dead_air(x, sr), sr))
        r = N.rng(T.seed_for(cand, treat, lang, lid))
        y = N.finish(T.build(x, treat, lang, r), T.TARGET, fade_in_ms=1.0, fade_out_ms=15.0)
        d = os.path.join(ROOT, outdir, lang)
        os.makedirs(d, exist_ok=True)
        base = os.path.join(d, f'{lid}__{var}')
        T.export(y, base)
        row = dict(lang=lang, id=lid, variant=var, dur=round(len(y) / T.SR, 3), mmax=round(N.m_max(y), 2),
                   peak_db=round(20 * np.log10(np.max(np.abs(y))), 2), steps=N.steps(y), muffled={})
        if script[lid].get('muffled'):
            for fc in fcs:
                m = muffle(y, fc)
                T.export(m, f'{base}_muffled{fc}')
                row['muffled'][str(fc)] = dict(mmax=round(N.m_max(m), 2), peak_db=round(20 * np.log10(np.max(np.abs(m))), 2),
                                               steps=N.steps(m))
        rows.append(row)
        print(name, row['dur'], row['mmax'], flush=True)
    json.dump(dict(candidate=cand, treatment=treat, muffle=MUFFLE, fcs=fcs, rows=rows),
              open(os.path.join(ROOT, outdir, 'treat_log.json'), 'w'), indent=1)


if __name__ == '__main__':
    a = sys.argv[1:]
    main(a[0], a[1], a[2], a[3], [int(v) for v in a[4:]] or [1400])

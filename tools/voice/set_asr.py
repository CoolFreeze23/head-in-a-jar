"""Intelligibility of DOLORES's set: the recogniser and settings (faster-whisper small, CPU int8, beam 5,
temperature 0, language forced) on every OGG in $VOICE_WORK\\<SETDIR>, WER against the spoken text.
Run with a Python that has faster-whisper and jiwer:  set_asr.py SETDIR. Writes <SETDIR>\\wer.json."""
import os, glob, json, os, subprocess, sys
import numpy as np
import jiwer

sys.path.insert(0, os.path.dirname(__file__))
from asr_wer import MODEL, norm  # noqa: E402
from faster_whisper import WhisperModel  # noqa: E402

ROOT = os.environ['VOICE_WORK']


def text_of(L, lang, var):
    if var == 'main':
        return L[lang]
    if var == 'short':
        return L['short'][lang]
    return L['alt'][lang][int(var[3:])]


def main(setdir='set'):
    script = {L['id']: L for L in json.load(open(os.path.join(os.path.dirname(__file__), 'set_script.json'),
                                                   encoding='utf-8'))['lines']}
    m = WhisperModel(MODEL, device='cpu', compute_type='int8', cpu_threads=8)
    rows = []
    for f in sorted(glob.glob(os.path.join(ROOT, setdir, '*', '*.ogg'))):
        lang = os.path.basename(os.path.dirname(f))
        name = os.path.basename(f)[:-4]
        lid, rest = name.split('__')
        var, _, muff = rest.partition('_muffled')
        ref = text_of(script[lid], lang, var)
        raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', f, '-ac', '1', '-ar', '16000', '-f', 'f32le', '-'],
                             capture_output=True, check=True).stdout
        segs, _ = m.transcribe(np.frombuffer(raw, '<f4').copy(), language=lang, beam_size=5, temperature=0.0,
                               condition_on_previous_text=False, vad_filter=False, without_timestamps=True)
        hyp = ' '.join(s.text.strip() for s in segs)
        R, H = norm(ref), norm(hyp)
        o = jiwer.process_words(R, H) if H else None
        errs = (o.substitutions + o.deletions + o.insertions) if o else len(R.split())
        rows.append(dict(lang=lang, id=lid, variant=var, muffled=int(muff) if muff else None, ref=ref, heard=hyp,
                         ref_words=len(R.split()), errors=errs, wer=round(errs / len(R.split()), 3)))
        print(lang, name, rows[-1]['wer'], '|', hyp, flush=True)
    json.dump(dict(model='faster-whisper small int8 cpu, beam 5, temperature 0', rows=rows),
              open(os.path.join(ROOT, setdir, 'wer.json'), 'w', encoding='utf-8'), indent=1, ensure_ascii=False)


if __name__ == '__main__':
    main(*sys.argv[1:])

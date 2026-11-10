"""Intelligibility check: faster-whisper "small" (CPU, int8, beam 5, temperature 0, language forced) transcribes every
processed OGG; word error rate against the script after normalisation. Run with a Python that has faster-whisper and jiwer.
Writes $VOICE_WORK\\proc\\wer.json.   --rescore recomputes WER from the stored transcripts."""
import os, glob, json, os, re, subprocess, sys, time, unicodedata
import numpy as np
import jiwer
from faster_whisper import WhisperModel

ROOT = os.environ['VOICE_WORK']
MODEL = os.environ['WHISPER_DIR']
NUM = [(r'\btwenty[\s-]*seven\b', '27'), (r'\bvinte e sete\b', '27'), (r'\b27\s*-?\s*b\b', '27 b'),
       (r'\btwo\b', '2'), (r'\bdois\b', '2'), (r'\b20 e 7', '27'), (r'\b27\s*-?\s*b\b', '27 b'),
       (r'\boops\b', 'oopsie'), (r'\boopsy\b', 'oopsie'), (r'\bwhoopsie\b', 'oopsie')]


def norm(s):
    s = unicodedata.normalize('NFC', s.lower())
    s = s.replace('-', ' ').replace('–', ' ')
    s = re.sub(r"[^\w\s]", ' ', s)
    s = re.sub(r'\s+', ' ', s).strip()
    for a, b in NUM:
        s = re.sub(a, b, s)
    return re.sub(r'\s+', ' ', s).strip()


def main():
    lines = json.load(open(ROOT + r'\scripts\lines.json', encoding='utf-8'))['lines']
    rescore = '--rescore' in sys.argv  # recompute WER from the stored transcripts (normaliser changes only)
    old = {(r['cand'], r['treat'], r['lang'], r['line']): r['heard'] for r in
           json.load(open(ROOT + r'\proc\wer.json', encoding='utf-8'))['rows']} if rescore else {}
    m = None if rescore else WhisperModel(MODEL, device='cpu', compute_type='int8', cpu_threads=8)
    rows = []
    files = sorted(glob.glob(ROOT + r'\proc\*\dolores_*.ogg'))
    for f in files:
        if rescore:
            cand, treat, lang, line = os.path.basename(f)[8:-4].rsplit('_', 3)
            ref, hyp = lines[line][lang]['display'], old[(cand, treat, lang, line)]
            R, H = norm(ref), norm(hyp)
            o = jiwer.process_words(R, H) if H else None
            errs = (o.substitutions + o.deletions + o.insertions) if o else len(R.split())
            rows.append(dict(cand=cand, treat=treat, lang=lang, line=line, ref=ref, heard=hyp, ref_words=len(R.split()),
                             errors=errs, wer=round(errs / len(R.split()), 3)))
            continue
        cand, treat, lang, line = os.path.basename(f)[8:-4].rsplit('_', 3)
        ref = lines[line][lang]['display']
        t0 = time.time()
        raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', f, '-ac', '1', '-ar', '16000', '-f', 'f32le', '-'],
                             capture_output=True, check=True).stdout
        audio = np.frombuffer(raw, '<f4').copy()  # decoded by ffmpeg (PyAV 19 dropped an argument faster-whisper uses)
        segs, _ = m.transcribe(audio, language=lang, beam_size=5, temperature=0.0, condition_on_previous_text=False,
                               vad_filter=False, without_timestamps=True)
        hyp = ' '.join(s.text.strip() for s in segs)
        R, H = norm(ref), norm(hyp)
        o = jiwer.process_words(R, H) if H else None
        errs = (o.substitutions + o.deletions + o.insertions) if o else len(R.split())
        rows.append(dict(cand=cand, treat=treat, lang=lang, line=line, ref=ref, heard=hyp, ref_words=len(R.split()),
                         errors=errs, wer=round(errs / len(R.split()), 3), asr_s=round(time.time() - t0, 2)))
        print(cand, treat, lang, line, rows[-1]['wer'], '|', hyp, flush=True)
    agg = {}
    for r in rows:
        k = f"{r['cand']}__{r['treat']}__{r['lang']}"
        a = agg.setdefault(k, [0, 0])
        a[0] += r['errors']; a[1] += r['ref_words']
    summary = {k: round(e / n, 3) for k, (e, n) in sorted(agg.items())}
    json.dump(dict(model='faster-whisper small int8 cpu', rows=rows, pooled_wer=summary), open(ROOT + r'\proc\wer.json', 'w',
              encoding='utf-8'), indent=1, ensure_ascii=False)
    for k, v in summary.items():
        print(k, v)


if __name__ == '__main__':
    main()

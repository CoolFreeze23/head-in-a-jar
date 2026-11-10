"""DOLORES's in-game set, raw VoxCPM2 takes in the designed voice: every line in both languages spoken with the
designed reference take (made by design_voice.py from a written description) as
reference_wav_path; cfg 2.0, 10 steps, seed 27 before every take, retry_badcase off, no denoiser. Every text variant in
set_script.json is generated so fit_set.py can choose.
Run with the VoxCPM2 Python (voxcpm 2.0.3, torch with CUDA). Writes $VOICE_WORK\\raw\\set_vox\\<lang>_<id>__<variant>.wav (48 kHz)."""
import os, hashlib, json, os, sys, time

os.environ.setdefault('HF_HUB_OFFLINE', '1')
sys.path.insert(0, os.path.dirname(__file__))
import torch  # noqa: E402
from design_voice import MODEL, CFG, STEPS, SEED, ROOT, seed, write, load  # noqa: E402


def variants(L, lang):
    out = [('main', L[lang])]
    if 'short' in L:
        out.append(('short', L['short'][lang]))
    for i, t in enumerate(L.get('alt', {}).get(lang, [])):
        out.append((f'alt{i}', t))
    return out


ANCHOR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'dolores_reference.wav')  # made by design_voice.py


def main(out_name='set_vox'):
    m = load()
    sr = m.tts_model.sample_rate
    script = json.load(open(os.path.join(os.path.dirname(__file__), 'set_script.json'), encoding='utf-8'))['lines']
    out = os.path.join(ROOT, 'raw', out_name)
    os.makedirs(out, exist_ok=True)
    log = []
    for lang in ('en', 'pt'):
        for L in script:
            for var, text in variants(L, lang):
                seed(SEED)
                t0 = time.time()
                x = m.generate(text=text, reference_wav_path=ANCHOR, cfg_value=CFG, inference_timesteps=STEPS,
                               retry_badcase=False)
                p = os.path.join(out, f'{lang}_{L["id"]}__{var}.wav')
                write(p, x, sr)
                log.append(dict(lang=lang, id=L['id'], variant=var, text=text, raw_s=round(len(x) / sr, 2),
                                gen_s=round(time.time() - t0, 2), md5=hashlib.md5(open(p, 'rb').read()).hexdigest()))
                print(log[-1]['lang'], log[-1]['id'], log[-1]['variant'], log[-1]['raw_s'], flush=True)
    log.append(dict(peak_vram_gb=round(torch.cuda.max_memory_allocated() / 1e9, 2) if torch.cuda.is_available() else None,
                    torch=torch.__version__, gpu=torch.cuda.get_device_name(0) if torch.cuda.is_available() else None))
    json.dump(log, open(os.path.join(out, 'gen_log.json'), 'w', encoding='utf-8'), indent=1, ensure_ascii=False)


if __name__ == '__main__':
    main(*sys.argv[1:])

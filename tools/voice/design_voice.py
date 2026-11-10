"""Design DOLORES's voice from words alone and save the reference take every line is spoken from.

VoxCPM2 is given a description of the voice and one sentence, with no reference recording (so no real person's voice is
cloned). The result is the reference take: every line, in both languages, uses it as reference_wav_path, so DOLORES is
one voice. Run with the VoxCPM2 Python (voxcpm 2.0.3, CUDA torch); VOXCPM_DIR holds the weights.
python design_voice.py OUT.wav"""
import os, random, sys, wave
import numpy as np
import torch

os.environ.setdefault('HF_HUB_OFFLINE', '1')
ROOT = os.environ['VOICE_WORK']
MODEL = os.environ['VOXCPM_DIR']
DESIGN = ('(A middle-aged woman, prim and very polite, a 1950s hospital switchboard operator, crisp clear diction, '
          'calm and measured, faintly cheerful, even pace)')
ANCHOR_TEXT = 'Good morning. Noggin and Sons thanks you for your patience. Please remain seated.'
SEED = 27
CFG, STEPS = 2.0, 10


def seed(s):
    random.seed(s); np.random.seed(s); torch.manual_seed(s); torch.cuda.manual_seed_all(s)


def write(path, x, sr):
    y = (np.clip(x, -1, 1) * 32767).astype('<i2')
    with wave.open(path, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr); w.writeframes(y.tobytes())


def load():
    from voxcpm import VoxCPM
    return VoxCPM(MODEL, zipenhancer_model_path=None, enable_denoiser=False, optimize=False)


def main(out):
    m = load()
    seed(SEED)
    x = m.generate(text=DESIGN + ANCHOR_TEXT, cfg_value=CFG, inference_timesteps=STEPS, retry_badcase=False)
    write(out, x, m.tts_model.sample_rate)
    print(out, round(len(x) / m.tts_model.sample_rate, 2), 's')


if __name__ == '__main__':
    main(sys.argv[1])

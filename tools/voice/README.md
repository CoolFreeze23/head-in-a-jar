# DOLORES's voice

DOLORES, the Separation Chair's assistant, speaks with a **machine-generated voice**. No person recorded these lines and
**no real person's voice is cloned**: the voice was designed from a written description by a text-to-speech model, and
every line is then put through a ring modulator and a small-speaker chain.

The files are in `src/main/resources/assets/headinajar/sounds/voice/en/` and `.../voice/pt/` (English and Brazilian
Portuguese). `lines.json` lists every line with its language key, the spoken text, its files, timing and how the game
plays it.

## How the voice was designed

1. **Design.** [VoxCPM2](https://github.com/OpenBMB/VoxCPM) (2B parameters, `voxcpm` 2.0.3) was given only a description
   in words, with no recording of anyone:
   *"A middle-aged woman, prim and very polite, a 1950s hospital switchboard operator, crisp clear diction, calm and
   measured, faintly cheerful, even pace"*, followed by the sentence "Good morning. Noggin and Sons thanks you for your
   patience. Please remain seated." (seed 27, guidance 2.0, 10 steps; `design_voice.py`).
2. **Reference take.** That synthetic take is `dolores_reference.wav` in this folder (48 kHz mono). It is the only
   reference any line uses, in both languages, so DOLORES is one voice in English and Portuguese.

## How a line is made

1. **Speech** (`gen_set_vox.py`). VoxCPM2 speaks the line with `dolores_reference.wav` as `reference_wav_path`: guidance
   2.0, 10 inference steps, seed 27 set (Python, NumPy, torch, CUDA) before every line, `retry_badcase` off, no denoiser,
   no text normaliser.
2. **Ring modulator** (`treat.py`, treatment `ringmod`): `0.3 x + 0.7 x sin(2 pi 30 t)`.
3. **Speaker chain** (same file). A small tiled room, then a small speaker: 320 Hz-3.4 kHz band, cabinet resonances,
   a box reflection and soft saturation. A relay click opens the line, a faint line hiss runs under it, and a squelch and
   a second click close it.
4. **Finishing.** Normalised to a maximum momentary loudness of -24 (close to the vanilla villager's idle sound), limited
   to -1 dBFS, written as mono OGG Vorbis (44.1 kHz, quality 5) with bit-exact flags. Every random choice in the chain is
   seeded from the line's name.
5. **Muffled copies** for the lines the patient hears during the operation: the same file through a 1.4 kHz low-pass,
   6 dB quieter.

`set_script.json` holds the spoken text (often the first sentence of the on-screen line; the screen keeps the full text).
`fit_set.py` chooses one take per line, fits the operation's lines to the chair's ticks and writes `lines.json`.

## Making a new line, or rebuilding

What a new line needs:

- **The reference take** `dolores_reference.wav` (this folder). Keep it: a new line must use it, or it will be a
  different voice.
- **The model:** VoxCPM2 weights from https://huggingface.co/openbmb/VoxCPM2 (`model.safetensors` 4.6 GB,
  `audiovae.pth` 377 MB, tokenizer and config), in the folder named by `VOXCPM_DIR`.
- **A CUDA GPU** with about 6 GB free (peak 6.0 GB measured on an 8 GB laptop RTX 5060, bfloat16). Speaking a line
  takes a little longer than the line lasts.
- **Python 3.12** with `voxcpm` 2.0.3 and torch 2.14.1 built for CUDA 13.0, run with `optimize=False` (no torch.compile).
- For the chain and checks: Python with NumPy and SciPy, ffmpeg, and for the intelligibility check faster-whisper with
  its `small` model (`WHISPER_DIR`). `VOICE_WORK` is a work folder; `MOD_ROOT` this repository.

Steps: `gen_set_vox.py` (raw takes), `set_treat.py voxcpm2 ringmod set_vox set_vox 1400` (chain), `set_asr.py set_vox`
(check), `fit_set.py` (choose, fit, write).

**Repeatability.** On the machine above, two full rebuilds from scratch gave byte-identical raw takes and byte-identical
game files, with nothing pinned beyond the seeds. On a different GPU, driver or torch version the raw takes may differ
slightly; the committed OGG files and `dolores_reference.wav` are all the game and any new line rely on, so a rebuild
elsewhere is never required.

## Licences

- **VoxCPM2 code and weights:** Apache License 2.0. "Weights and code released under the Apache-2.0 license, free for
  commercial use" (https://github.com/OpenBMB/VoxCPM); the model card is tagged `license: apache-2.0` and says "Released
  under the Apache-2.0 license, free for commercial use" (https://huggingface.co/openbmb/VoxCPM2). The model card forbids
  using it for impersonation and asks that AI-generated audio be labelled as such: this voice imitates no one, and this
  README is that label.
- **faster-whisper and its `small` model** (used only to check the files): MIT.
- The generated audio is part of this mod and is covered by the mod's own licence.

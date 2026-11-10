# Head in a Jar sound pipeline

Every new sound in the mod is synthesised in Python from a written recipe, layer by layer, and checked by measurement
against the vanilla sound it will be heard beside. Nothing is recorded and nothing from another game or mod is copied
in; where vanilla already does a material well (pouring liquid, bubble pops) the event refers to the vanilla file by
name in `sounds.json`, so it ships nothing and follows the player's resource pack.

## Files

| File | What it is |
|---|---|
| `nsynth.py` | The kit: struck modes (metal, glass), filtered noise, swept and FM oscillators, the motor model, bubbles, crackle; the finishing chain (rumble high-pass, DC removal, edge fades, loudness normalisation, -1.5 dBFS limiter); bit-exact OGG export. `place()` refuses to cut a layer off while it is still above -60 dB. |
| `recipes_feedback.py` | The feedback family: refused, done, warning, danger. |
| `recipes_jar.py` | The bare jar: tink, glug. |
| `recipes_chair.py` | The Separation Chair's operation, keyed to its animation. |
| `build.py` | Rebuilds every file and `events.json` from the recipes. |
| `measure.py` | Checks every file against its loudness class and its named vanilla reference; draws spectrogram pairs. |
| `events.json` | Generated hand-off: each event's files, volume, pitch range, attenuation, category, timing and subtitles. |

## Running it

Needs Python 3 with numpy and scipy, and ffmpeg (with libvorbis) on the PATH; `measure.py --spectro` also uses Pillow.

```
python tools/snd/build.py                     # every sound, then events.json
python tools/snd/build.py chair.cut           # one event
python tools/snd/measure.py --refs DIR [--spectro]
```

`--refs` is a folder of vanilla reference files for measuring only (copied from the game's asset store, named as in the
recipes, for example `block__iron_trapdoor__close1.ogg`). They never go into the repository.

Output goes to `src/main/resources/assets/headinajar/sounds/<family>/<name>_<take>.ogg`. Each take uses seed
`1000 x take + crc32(event id) % 1000` and is encoded with ffmpeg's bit-exact flags, so a rebuild gives the same bytes.
The older files directly in `sounds/` come from `tools/make_sounds.py` and are not touched here.

## The rules every file meets

- Mono OGG Vorbis, 44.1 kHz, quality 5. Sample peak at or below -1 dBFS after decoding.
- Starts and ends at silence: exactly zero at both ends before encoding; the last 50 ms at least 40 dB under the
  loudest 10 ms; no 5 ms frame that falls 30 dB from the one before (no hard cuts).
- Loudness: the maximum momentary loudness (K-weighted, 400 ms) sits within 2 LU of its class target and of its named
  vanilla reference as the game plays it. The file carries the loudness; code plays it at volume 0.8-1.0.

| Class | Target | Vanilla neighbour |
|---|---|---|
| Signature moment (the cut) | -18 | smithing table, beacon deactivate |
| Clamp, latch, alarm | -22 | iron door and iron trapdoor closing |
| Hiss, glug, clamp release | -24 | fire extinguish, bottle fill |
| Servo, hydraulic, glass clink | -27 | piston |
| Done chime, warning | -28 | note block, beacon ambient |
| Tick, relay, blip | -31 | crafter |

- Spectral centroid within an octave of the reference's; heard length (to -40 dB) inside the range for its kind
  (click 0.03-0.15 s, clamp or latch 0.15-0.40, a move as long as the move plus its stop, hiss 0.6-1.6, glug 0.4-0.8,
  stinger 0.6-1.5).
- Every file states where its hit lands. A sound played from an animation keyframe has its hit at 0-5 ms; a sound
  played by the server may start a whole number of ticks early (`contact_offset_ticks`) so its hit lands on the
  contact tick.
- Takes of one event differ by at least 5 % in centroid or length, by construction (per-take tables), not by chance.

## The house sound

1950s industrial-domestic: enamel and brass machines that work well and are a little pleased with themselves.
Mechanisms are crisp and dry, built from the same parts (clamp, servo, hydraulic, hiss, ratchet, relay, arc), so every
machine in the mod sounds like the same company made it. One FM voice, a vibraphone-like bell with carrier and
modulator at 1:3.5, is reserved for the company talking to the player: the done chime, the refusal, the warning, the
alarm's strike, the chair's countdown and ding. Their intervals come from the company's four-note motif, C E G C.

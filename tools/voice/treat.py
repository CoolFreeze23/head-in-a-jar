"""DOLORES treatments: deterministic processing chains from raw TTS takes to game-ready mono 44.1 kHz files.

python -I treat.py            (system Python 3.13 with numpy + scipy; ffmpeg on PATH)

Uses the house kit nsynth.py (next to this file) (finish = 35 Hz high-pass, DC removal, edge fades, K-weighted
M-max normalisation, -1 dBFS limiter). Every noise and click is seeded from crc32(candidate, treatment, lang, line), so a
rerun rebuilds the same samples (the OGG/MP3 encoders are deterministic for the same input and settings).

Treatments
  clean     resample, trim, finish. The reference: no character processing at all.
  intercom  1950s theatre wall speaker: relay click on, open-line hiss, band-limit, cabinet resonances, box reflection,
            soft saturation, squelch and release click off.
  vocoder   18-band channel vocoder: the voice's band envelopes re-imposed on a fixed-pitch buzz (all pitch movement
            removed, the "machine" voice), then the intercom chain.
  ringmod   30 Hz ring modulation (70% wet) on the voice, then the intercom chain.
  retro     8-bit-era voice chip: sample-and-hold to 11.025 kHz, 6-bit quantiser, then the intercom chain (for eSpeak NG).
"""
import os, json, os, subprocess, sys, wave, zlib
import numpy as np
from scipy import signal

sys.path.insert(0, os.environ.get('NSYNTH_DIR', os.path.dirname(os.path.abspath(__file__))))
import nsynth as N  # noqa: E402

SR = N.SR
ROOT = os.environ['VOICE_WORK']
TARGET = -24.0  # M max, LUFS-like: close to vanilla villager idle1 (-24.1)

# ---------------------------------------------------------------- parameters 
P = dict(
    trim_db=-50.0,            # trim leading/trailing audio quieter than peak-50 dB (10 ms envelope)
    trim_fade=(0.004, 0.025), # raised-cosine edges after the trim: in, out (s)
    lead=0.02,                # silence before the first event (s)
    click_to_speech=0.08,     # relay click, then the voice 80 ms later
    speech_to_squelch=0.06,
    tail=0.15,                # silence after the last event
    band=(320.0, 3400.0),     # small cone speaker, 4th-order Butterworth band-pass each side
    peaks=[(1100.0, 6.0, 2.5), (2400.0, 4.0, 3.0), (650.0, 2.0, 1.5)],  # (Hz, dB, Q) cabinet / cone resonances
    reflection=(0.0014, 0.22),  # box reflection: delay s, gain
    room=(0.02, 0.12),        # small tiled theatre: exponential noise tail tau (s), mix; also smooths eSpeak's hard word stops
    drive=2.2,                # tanh saturation drive (input peak-normalised)
    hiss_db=-42.0,            # open-line hiss, dB under the speech RMS, band 1.5-6 kHz, click to squelch
    click=dict(f0=2600.0, ratios=[1.0, 2.31, 3.87], amps=[1.0, 0.5, 0.3], decays=[0.003, 0.002, 0.001],
               noise_ms=2.0, level=0.35, release_after=0.032, release_gain=0.5),
    squelch=dict(dur=0.045, tau=0.015, band=(1000.0, 6000.0), level=0.22),
    voc=dict(bands=18, lo=150.0, hi=6500.0, env_hz=40.0, f0={'en': 200.0, 'pt': 200.0}, noise_mix=0.3, nharm=60),
    ring=dict(hz=30.0, wet=0.7),
    retro=dict(hold=4, bits=6),
    ogg_q=5, mp3_q=4,
)


def seed_for(*parts):
    return zlib.crc32('|'.join(parts).encode())


def read_wav(path):
    with wave.open(path) as w:
        sr = w.getframerate(); n = w.getnframes(); ch = w.getnchannels()
        x = np.frombuffer(w.readframes(n), '<i2').astype(np.float64) / 32768.0
    if ch > 1:
        x = x.reshape(-1, ch).mean(1)
    return x, sr


def to44(x, sr):
    if sr == SR:
        return x
    g = np.gcd(SR, sr)
    return signal.resample_poly(x, SR // g, sr // g)


def trim(x):
    h = int(0.01 * SR)
    m = len(x) // h
    e = 20 * np.log10(np.sqrt((x[:m * h].reshape(m, h) ** 2).mean(1)) + 1e-12)
    on = np.where(e > e.max() + P['trim_db'])[0]
    a, b = on[0] * h, min(len(x), (on[-1] + 1) * h)
    y = x[a:b].copy()
    fi, fo = (int(v * SR) for v in P['trim_fade'])  # raised-cosine edges so the trim never cuts mid-wave
    y[:fi] *= 0.5 - 0.5 * np.cos(np.linspace(0, np.pi, fi))
    y[-fo:] *= 0.5 + 0.5 * np.cos(np.linspace(0, np.pi, fo))
    return y


def peaking(x, f0, gain_db, q):
    A = 10 ** (gain_db / 40); w0 = 2 * np.pi * f0 / SR; al = np.sin(w0) / (2 * q)
    b = [1 + al * A, -2 * np.cos(w0), 1 - al * A]; a = [1 + al / A, -2 * np.cos(w0), 1 - al / A]
    return signal.lfilter(np.array(b) / a[0], np.array(a) / a[0], x)


def relay_click(r):
    c = P['click']
    body = N.modal(c['f0'], c['ratios'], c['amps'], c['decays'], 0.03, r)
    nz = N.bandpass(N.noise(c['noise_ms'] / 1000, r), 2000, 8000) * np.hanning(int(round(c['noise_ms'] / 1000 * SR)))
    x = N.mix(N.norm(body), 0.6 * N.norm(nz))
    out = np.zeros(int((c['release_after'] + 0.04) * SR))
    N.place(out, x, 0.0)
    N.place(out, c['release_gain'] * x, c['release_after'])
    return N.norm(out) * c['level']


def squelch(r):
    s = P['squelch']
    n = N.bandpass(N.noise(7 * s['tau'] + s['dur'], r), *s['band'])
    env = np.minimum(1, N.tt(7 * s['tau'] + s['dur']) / 0.002)
    t = N.tt(7 * s['tau'] + s['dur'])
    env = env * np.where(t < s['dur'], 1.0, np.exp(-(t - s['dur']) / s['tau']))
    return N.norm(n * env) * s['level']


def speaker(x, r):
    """Room tail, band-limit, cabinet, reflection, saturation (no clicks)."""
    tau, mix = P['room']
    ir = N.noise(7 * tau, r) * np.exp(-N.tt(7 * tau) / tau)
    wet = np.concatenate([signal.fftconvolve(x, ir), [0.0]])  # len(x) + len(ir)
    x = np.concatenate([x, np.zeros(len(ir))]) + mix * wet / np.max(np.abs(wet)) * np.max(np.abs(x))
    lo, hi = P['band']
    y = N.highpass(x, lo, 4)
    y = N.lowpass(y, hi, 4)
    for f, g, q in P['peaks']:
        y = peaking(y, f, g, q)
    d, gn = P['reflection']
    k = int(round(d * SR))
    y = y + gn * np.concatenate([np.zeros(k), y[:-k]])
    y = N.norm(y)
    dr = P['drive']
    return np.tanh(dr * y) / np.tanh(dr)


def intercom(x, r):
    """Wrap a voice in the relay click, line hiss, speaker and squelch."""
    v = speaker(x, r)
    rms = np.sqrt(np.mean(v[np.abs(v) > 0.01] ** 2))
    click = relay_click(r)
    t_speech = P['lead'] + P['click_to_speech']
    t_sq = t_speech + len(v) / SR + P['speech_to_squelch']
    sq = squelch(r)
    rel = relay_click(r) * P['click']['release_gain']
    total = t_sq + len(sq) / SR + P['click']['release_after'] + len(rel) / SR + P['tail']
    out = np.zeros(int(total * SR))
    hiss_len = t_sq - P['lead']
    hiss = N.bandpass(N.noise(hiss_len, r), 1500, 6000)
    hiss = hiss / np.sqrt(np.mean(hiss ** 2)) * rms * 10 ** (P['hiss_db'] / 20)
    f = int(0.01 * SR)
    hiss[:f] *= np.linspace(0, 1, f); hiss[-f:] *= np.linspace(1, 0, f)
    N.place(out, hiss, P['lead'])
    N.place(out, click, P['lead'])
    N.place(out, v, t_speech)
    N.place(out, sq, t_sq)
    N.place(out, rel, t_sq + len(sq) / SR - 0.005)
    return out


def vocode(x, lang, r):
    c = P['voc']
    edges = np.geomspace(c['lo'], c['hi'], c['bands'] + 1)
    f0 = c['f0'][lang]
    carrier = N.osc_harm(np.full(len(x), f0), c['nharm'], r, tilt=0.6)
    carrier = N.norm(carrier) + c['noise_mix'] * N.norm(N.noise(len(x) / SR, r)[:len(x)])
    env_sos = signal.butter(2, c['env_hz'], 'lowpass', fs=SR, output='sos')
    out = np.zeros_like(x)
    for lo, hi in zip(edges[:-1], edges[1:]):
        sos = N.sos_bp(lo, hi, 2)
        env = np.maximum(signal.sosfiltfilt(env_sos, np.abs(signal.sosfilt(sos, x))), 0)
        out += signal.sosfilt(sos, carrier) * env
    return N.norm(out)


def ringmod(x):
    c = P['ring']
    t = np.arange(len(x)) / SR
    return (1 - c['wet']) * x + c['wet'] * x * np.sin(2 * np.pi * c['hz'] * t)


def retro(x):
    c = P['retro']
    h = c['hold']
    y = N.lowpass(x, SR / h / 2 * 0.9, 4)  # anti-alias before the hold, so the grit is the chip's, not aliasing mush
    y = np.repeat(y[::h], h)[:len(x)]
    lv = 2 ** (c['bits'] - 1)
    y = np.round(N.norm(y) * (lv - 1)) / (lv - 1)
    return y


def build(x, treat, lang, r):
    if treat == 'clean':
        out = np.zeros(int((P['lead'] + len(x) / SR + P['tail']) * SR))
        return N.place(out, x, P['lead'])
    if treat == 'intercom':
        return intercom(x, r)
    if treat == 'vocoder':
        return intercom(vocode(x, lang, r), r)
    if treat == 'ringmod':
        return intercom(ringmod(x), r)
    if treat == 'retro':
        return intercom(retro(x), r)
    raise ValueError(treat)


def export(x, base):
    N.write_wav(base + '.wav', x)
    # not N.to_ogg: without +bitexact ffmpeg's Ogg muxer picks a random stream serial, so rebuilt files differ in bytes
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', base + '.wav', '-ac', '1', '-ar', str(SR), '-c:a', 'libvorbis',
                    '-q:a', str(P['ogg_q']), '-map_metadata', '-1', '-fflags', '+bitexact', '-flags:a', '+bitexact', base + '.ogg'], check=True)
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', base + '.wav', '-ac', '1', '-c:a', 'libmp3lame',
                    '-q:a', str(P['mp3_q']), '-map_metadata', '-1', '-fflags', '+bitexact', base + '.mp3'], check=True)

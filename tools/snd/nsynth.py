"""Noggin & Sons synthesis kit: the building blocks every Head in a Jar sound recipe is made from.

Layered synthesis with numpy and scipy: struck metal and glass as decaying modes, filtered noise, swept and FM
oscillators, envelopes, then one finishing chain (rumble high-pass, DC removal, edge fades, loudness normalisation
to a K-weighted momentary-max target, a -1 dBFS peak limiter) and export to mono OGG Vorbis through ffmpeg with
bit-exact flags. Every random choice comes from a generator seeded by the caller, so a take rebuilds bit for bit.

Rule the kit enforces: a decaying layer is always generated at least 7 decay times long (-60 dB) before it is placed,
so nothing is ever cut off while it is still ringing.
"""
import os
import subprocess
import wave

import numpy as np
from scipy import signal

SR = 44100
TICK = 0.05  # one game tick, seconds


# ---------------------------------------------------------------- basics

def tt(dur):
    return np.arange(int(round(dur * SR))) / SR


def rng(seed):
    return np.random.default_rng(seed)


def buf(dur):
    return np.zeros(int(round(dur * SR)))


def place(b, x, at, gain=1.0):
    """Add gain * x into buffer b starting at time `at` (s). Anything past the end is dropped, so callers size the
    buffer to hold every layer's tail."""
    i = int(round(at * SR))
    n = min(len(x), len(b) - i)
    if n < len(x) and np.max(np.abs(x[max(n, 0):])) > 1e-3 * max(np.max(np.abs(x)), 1e-12):
        raise ValueError(f'layer placed at {at:.3f} s would be cut off while still above -60 dB: lengthen the buffer')
    if n > 0:
        b[i:i + n] += gain * x[:n]
    return b


def mix(*layers):
    n = max(len(l) for l in layers)
    out = np.zeros(n)
    for l in layers:
        out[:len(l)] += l
    return out


def norm(x):
    return x / max(np.max(np.abs(x)), 1e-12)


def db(v):
    return 10 ** (v / 20.0)


# ---------------------------------------------------------------- filters

def bandpass(x, lo, hi, order=2):
    return signal.sosfilt(signal.butter(order, [lo, hi], btype='bandpass', fs=SR, output='sos'), x)


def lowpass(x, fc, order=2):
    return signal.sosfilt(signal.butter(order, fc, 'lowpass', fs=SR, output='sos'), x)


def highpass(x, fc, order=2):
    return signal.sosfilt(signal.butter(order, fc, 'highpass', fs=SR, output='sos'), x)


def sweep_bandpass(x, lo_curve, hi_curve, block=256):
    """Band-pass whose edges move over time (per-block filter with carried state). lo_curve, hi_curve: per sample."""
    out = np.zeros_like(x)
    zi = None
    for i in range(0, len(x), block):
        lo = float(np.clip(lo_curve[min(i, len(lo_curve) - 1)], 30, SR * 0.45))
        hi = float(np.clip(hi_curve[min(i, len(hi_curve) - 1)], lo * 1.2, SR * 0.48))
        sos = signal.butter(2, [lo, hi], btype='bandpass', fs=SR, output='sos')
        if zi is None:
            zi = np.zeros((sos.shape[0], 2))
        out[i:i + block], zi = signal.sosfilt(sos, x[i:i + block], zi=zi)
    return out


# ---------------------------------------------------------------- sources

def noise(dur, r):
    return r.standard_normal(int(round(dur * SR)))


def exp_env(dur, tau, attack=0.0005):
    """Exponential decay with a linear attack. Keep dur >= 7 * tau."""
    t = tt(dur)
    a = np.clip(t / attack, 0, 1) if attack > 0 else 1.0
    return a * np.exp(-t / tau)


def noise_burst(lo, hi, tau, r, attack=0.0005, order=2):
    """Band-passed noise with an exponential decay, generated 7 decay times long."""
    d = max(7 * tau + attack, 0.01)
    return bandpass(noise(d, r), lo, hi, order) * exp_env(d, tau, attack)


def modal(f0, ratios, amps, decays, r, jitter=0.0, attack=0.0003):
    """Struck metal or glass: decaying sines at inharmonic ratios, 7 longest decays long, 0.3 ms attack ramp."""
    t = tt(7 * max(decays) + attack)
    out = np.zeros_like(t)
    for ra, am, de in zip(ratios, amps, decays):
        f = f0 * ra * (1 + jitter * r.uniform(-1, 1))
        if f < SR * 0.45:
            out += am * np.sin(2 * np.pi * f * t + r.uniform(0, 2 * np.pi)) * np.exp(-t / de)
    return out * np.clip(t / attack, 0, 1)


def sweep_sine(f_start, f_end, tau, glide, attack=0.001):
    """A sine whose pitch glides from f_start to f_end (time constant `glide`) under an exponential decay `tau`."""
    t = tt(7 * tau + attack)
    f = f_end + (f_start - f_end) * np.exp(-t / glide)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / tau) * np.clip(t / attack, 0, 1)


def osc_harm(freq_curve, nharm, r, tilt=1.0, odd_only=False):
    """Harmonic oscillator on a per-sample frequency curve, partials 1/k^tilt, nothing above 0.45 SR."""
    ph = 2 * np.pi * np.cumsum(freq_curve) / SR
    out = np.zeros_like(freq_curve)
    for k in range(1, nharm + 1):
        if odd_only and k % 2 == 0:
            continue
        out += ((freq_curve * k) < SR * 0.45) * np.sin(k * ph + r.uniform(0, 2 * np.pi)) / k ** tilt
    return out


def fm_note(f, tau, r, index=(2.0, 0.3), index_tau=None, ratio=3.5, attack=0.003, hold=0.0, partner=None,
            tremolo=None):
    """The house voice (an FM 'vibraphone', carrier:modulator 1:3.5). The index falls from index[0] to index[1]
    with time constant index_tau (default tau/3), so the note starts bright and ends as a near sine.
    hold: seconds at full level before the decay starts. partner: (Hz offset, gain) adds a beating twin.
    tremolo: (rate Hz, depth 0..1)."""
    d = attack + hold + 7 * tau
    t = tt(d)
    it = index_tau or tau / 3
    ind = index[1] + (index[0] - index[1]) * np.exp(-t / it)
    env = np.clip(t / attack, 0, 1) * np.where(t < attack + hold, 1.0, np.exp(-(t - attack - hold) / tau))
    ph0, ph1 = r.uniform(0, 2 * np.pi, 2)
    out = np.sin(2 * np.pi * f * t + ph0 + ind * np.sin(2 * np.pi * f * ratio * t + ph1))
    if partner:
        df, g = partner
        out += g * np.sin(2 * np.pi * (f + df) * t + ph1 + ind * np.sin(2 * np.pi * (f + df) * ratio * t))
    if tremolo:
        rate, depth = tremolo
        out *= 1 - depth * (0.5 - 0.5 * np.cos(2 * np.pi * rate * t))
    return out * env


# ---------------------------------------------------------------- shared building blocks (style guide 3.5)

def tooth(r, f=2600.0, level=1.0):
    """One ratchet tooth: a 2.5-7 kHz noise tick (1.2 ms) and two short modes."""
    return level * norm(mix(noise_burst(2500, 7000, 0.0012, r),
                            0.6 * modal(f, [1, 1.58], [1, 0.5], [0.008, 0.005], r, 0.03)))


def strike(r, hp=1500.0, tau=0.001):
    """The attack that makes a hit read as a hit: high-passed noise, 1 ms decay."""
    d = 7 * tau + 0.002
    return norm(highpass(noise(d, r), hp) * exp_env(d, tau))


def thunk(r, f_start=150.0, f_end=90.0, tau=0.05, glide=0.025):
    """The weight under a hit. Keep it 8 dB or more under the body or the hit reads as a drum."""
    return sweep_sine(f_start, f_end, tau, glide)


def iron_body(r, f0=560.0, plate=900.0, decay_scale=1.0, noise_gain=0.35, band_scale=1.0):
    """Free-bar modes plus a plate plus a 1-7 kHz noise band (the band is what makes it iron, not a bell)."""
    d = [x * decay_scale for x in (0.07, 0.05, 0.03, 0.02)]
    bar = modal(f0, [1, 2.756, 5.404, 8.933], [1, 0.7, 0.5, 0.35], d, r, 0.01)
    pl = modal(plate, [1, 1.59, 2.14, 3.9], [1, 0.6, 0.4, 0.3], [x * decay_scale for x in (0.05, 0.04, 0.03, 0.02)], r)
    band = noise_burst(1000 * band_scale, 7000 * band_scale, 0.045 * decay_scale, r)
    return mix(0.45 * norm(bar), 0.27 * norm(pl), noise_gain * norm(band))


def relay_click(r, f=1800.0, release_after=0.03, level=1.0):
    """Pull-in click, then the armature's release 20-40 ms later at -6 dB and +10% pitch."""
    def one(fr):
        return norm(mix(highpass(noise(0.006, r), 3000) * exp_env(0.006, 0.0005),
                        0.5 * modal(fr, [1, 1.72], [1, 0.5], [0.010, 0.006], r, 0.03)))
    out = buf(release_after + 0.09)
    place(out, one(f), 0.0)
    place(out, one(f * 1.1), release_after, 0.5)
    return level * out


def glass_modes(r, f0, decays=(0.07, 0.045, 0.025, 0.015), amps=(1, 0.5, 0.3, 0.15)):
    """A glass vessel struck: modes at f0 x [1, 2.7, 5.1, 8.2] (ratios chosen, not measured from glass)."""
    return modal(f0, [1, 2.7, 5.1, 8.2], list(amps), list(decays), r, 0.01)


def bubble(r, f0, tau, rise=0.15, attack=0.001):
    """One Minnaert bubble: a decaying sine whose pitch rises by `rise` over its life (van den Doel's model, from
    memory: frequency about 3.26 / radius Hz, so a 3 mm bubble is about 1.1 kHz)."""
    t = tt(7 * tau + attack)
    f = f0 * (1 + rise * (1 - np.exp(-t / (2 * tau))))
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / tau) * np.clip(t / attack, 0, 1)


def speed_curve(t, start, stop, shape='sin2'):
    """Speed 0..1..0 across a move from start to stop (s). 'sin2' is the eased move; 'in' rises to the end."""
    u = np.clip((t - start) / max(stop - start, 1e-6), 0, 1)
    inside = (t > start) & (t < stop)
    if shape == 'sin2':
        return np.where(inside, np.sin(np.pi * u) ** 2, 0.0)
    if shape == 'in':
        return np.where(inside, u ** 2, 0.0)
    raise ValueError(shape)


def motor(t, s, r, base, top, grit=0.42, air=0.14, whine=0.36, harmonics=10, rotor_div=6, air_band=(1000, 9000)):
    """Farnell's motor: a whine whose pitch and level follow speed s, brush grit gated by the rotor phase, and
    broadband mechanical air. Returns the summed layers (each normalised, then weighted)."""
    fw = base + top * s
    fm = fw * (1 + 0.02 * s * np.sin(2 * np.pi * np.cumsum(fw / 3) / SR))
    w = bandpass(osc_harm(fm, harmonics, r, 1.2), 400, 3500) * s ** 0.7
    gate = (0.5 + 0.5 * np.cos(2 * np.pi * np.cumsum(fw / rotor_div) / SR)) ** 4
    g = bandpass(noise(len(t) / SR, r), 2500, 7000) * gate * s
    a = bandpass(noise(len(t) / SR, r), air_band[0], air_band[1]) * s ** 1.5
    return whine * norm(w) + grit * norm(g) + air * norm(a)


def crackle(r, dur, lo=2000, hi=10000, spacing=(0.010, 0.060), width=(0.002, 0.008), first_gain=1.0):
    """Electric arc: filtered-noise bursts 2-8 ms wide at random 10-60 ms spacing, each a random level. Each burst
    decays with a time constant of at least 3 ms, so no 5 ms frame falls more than 30 dB (no hard cuts)."""
    out = buf(dur + 0.05)
    at = 0.0
    k = 0
    while at < dur:
        w = r.uniform(*width)
        g = first_gain if k == 0 else r.uniform(0.25, 0.9)
        place(out, g * norm(noise_burst(lo, hi, max(w / 3, 0.003), r, attack=0.0003)), at)
        at += r.uniform(*spacing)
        k += 1
    return out


# ---------------------------------------------------------------- loudness and finishing

def _k_sos():
    """BS.1770 K-weighting as two biquads at 44.1 kHz (same design as measure.py)."""
    G, Q, fc = 3.99984385397, 0.7071752369554193, 1681.9744509555319
    A = 10 ** (G / 40.0)
    w0 = 2 * np.pi * fc / SR
    al = np.sin(w0) / (2 * Q)
    b = [A * ((A + 1) + (A - 1) * np.cos(w0) + 2 * np.sqrt(A) * al), -2 * A * ((A - 1) + (A + 1) * np.cos(w0)),
         A * ((A + 1) + (A - 1) * np.cos(w0) - 2 * np.sqrt(A) * al)]
    a = [(A + 1) - (A - 1) * np.cos(w0) + 2 * np.sqrt(A) * al, 2 * ((A - 1) - (A + 1) * np.cos(w0)),
         (A + 1) - (A - 1) * np.cos(w0) - 2 * np.sqrt(A) * al]
    s1 = np.hstack([np.array(b) / a[0], np.array(a) / a[0]])
    Q, fc = 0.5003270373253953, 38.13547087613982
    w0 = 2 * np.pi * fc / SR
    al = np.sin(w0) / (2 * Q)
    a = np.array([1 + al, -2 * np.cos(w0), 1 - al])
    s2 = np.hstack([np.array([1.0, -2.0, 1.0]) / a[0], a / a[0]])
    return np.vstack([s1, s2])


def m_max(x):
    """Maximum momentary loudness (K-weighted, 400 ms window, 10 ms hop), LUFS-like; short files are zero-padded."""
    xk = signal.sosfilt(_k_sos(), x)
    win = int(0.4 * SR)
    sq = np.concatenate([xk ** 2, np.zeros(win)])
    c = np.concatenate([[0], np.cumsum(sq)])
    starts = np.arange(0, max(1, len(x)), int(0.01 * SR))
    ms = (c[starts + win] - c[starts]) / win
    return -0.691 + 10 * np.log10(max(ms.max(), 1e-12))


def limit(x, ceiling_db=-1.0, release=0.05):
    """Peak limiter: instant attack (1 ms look-ahead), exponential release."""
    c = 10 ** (ceiling_db / 20)
    need = np.minimum(1.0, c / np.maximum(np.abs(x), 1e-9))
    la = int(0.001 * SR)
    need = np.minimum.reduce([np.roll(need, -k) for k in range(la)])
    if need.min() >= 1.0:
        return x
    g = np.empty_like(need)
    cur = 1.0
    rel = np.exp(-1 / (release * SR))
    for i, n in enumerate(need):
        cur = n if n < cur else n + (cur - n) * rel
        g[i] = cur
    return x * g


def fade_curve(n):
    """A fade from 1 to 0 over n samples whose end is smooth to the fourth power (raised cosine, squared), so a
    fade to digital zero never ends on an audible step."""
    return (0.5 + 0.5 * np.cos(np.linspace(0, np.pi, n))) ** 2


def finish(x, target_mmax, fade_in_ms=1.0, fade_out_ms=15.0, hp=35.0, ceiling_db=-1.5):
    """High-pass, DC out, raised-cosine edge fades, normalise to the target M max, limit the peak.
    The ceiling is -1.5 dBFS before encoding so the decoded Vorbis peak stays under -1.0."""
    x = highpass(x, hp, 2)
    x = x - x.mean()
    ni = int(fade_in_ms / 1000 * SR)
    no = int(fade_out_ms / 1000 * SR)
    if ni:
        x[:ni] *= 0.5 - 0.5 * np.cos(np.linspace(0, np.pi, ni))
    if no:
        x[-no:] *= fade_curve(no)
    x[0] = 0.0
    x[-1] = 0.0
    for _ in range(3):
        x = x * 10 ** ((target_mmax - m_max(x)) / 20)
        x = limit(x, ceiling_db)
    return x


def write_wav(path, x):
    y = np.clip(x, -1, 1)
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(np.round(y * 32767).astype('<i2').tobytes())


def to_ogg(wav, ogg, q=5):
    """Mono 44.1 kHz Vorbis q5. The bitexact flags fix the Ogg serial number and drop the encoder tag, so the
    same samples always give the same bytes."""
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-ac', '1', '-ar', str(SR), '-c:a', 'libvorbis',
                    '-q:a', str(q), '-fflags', '+bitexact', '-flags:a', '+bitexact', '-map_metadata', '-1', ogg],
                   check=True)


def export(x, ogg):
    os.makedirs(os.path.dirname(ogg), exist_ok=True)
    wav = ogg[:-4] + '.tmp.wav'
    write_wav(wav, x)
    try:
        to_ogg(wav, ogg)
    finally:
        os.remove(wav)


def steps(x, frame=0.005, drop_db=30.0, floor_db=-50.0):
    """Hard cuts inside a sound: 5 ms frames where the level falls by more than drop_db in one frame while the frame
    before was within floor_db of the loudest frame. Returns the times (s)."""
    h = int(frame * SR)
    m = len(x) // h
    e = 20 * np.log10(np.sqrt((x[:m * h].reshape(m, h) ** 2).mean(1)) + 1e-12)
    e -= e.max()
    return [round((i + 1) * frame, 3) for i in range(m - 1) if e[i] > floor_db and e[i] - e[i + 1] > drop_db]

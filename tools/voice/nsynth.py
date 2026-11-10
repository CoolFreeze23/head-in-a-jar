"""Noggin & Sons house synthesis kit.

Layered synthesis with numpy/scipy: modal metal, filtered noise, swept oscillators, envelopes, then a finishing
chain (DC/rumble high-pass, edge fades, loudness normalisation to a K-weighted momentary-max target, a peak
limiter at -1 dBFS) and export to mono OGG Vorbis through ffmpeg. Every random choice comes from a seeded
generator so a variant can be rebuilt bit for bit.
"""
import subprocess
import wave
import numpy as np
from scipy import signal

SR = 44100


def tt(dur):
    return np.arange(int(round(dur * SR))) / SR


def rng(seed):
    return np.random.default_rng(seed)


def place(buf, x, at):
    """Add x into buf starting at time `at` (s)."""
    i = int(round(at * SR))
    n = min(len(x), len(buf) - i)
    if n > 0:
        buf[i:i + n] += x[:n]
    return buf


def sos_bp(lo, hi, order=2):
    return signal.butter(order, [lo, hi], btype='bandpass', fs=SR, output='sos')


def bandpass(x, lo, hi, order=2):
    return signal.sosfilt(sos_bp(lo, hi, order), x)


def lowpass(x, fc, order=2):
    return signal.sosfilt(signal.butter(order, fc, 'lowpass', fs=SR, output='sos'), x)


def highpass(x, fc, order=2):
    return signal.sosfilt(signal.butter(order, fc, 'highpass', fs=SR, output='sos'), x)


def noise(dur, r):
    return r.standard_normal(int(round(dur * SR)))


def exp_env(dur, tau, attack=0.0005):
    """Exponential decay envelope; callers multiply it with a noise of the same `dur`, so keep dur >= 7 * tau."""
    t = tt(dur)
    a = np.clip(t / attack, 0, 1) if attack > 0 else 1.0
    return a * np.exp(-t / tau)


def modal(f0, ratios, amps, decays, dur, r, jitter=0.0):
    """Struck metal: a sum of decaying sines at inharmonic ratios. Higher modes decay faster.
    The layer is always at least 7 decay times long (-60 dB), so it can never be cut off while still ringing."""
    t = tt(max(dur, 7 * max(decays)))
    out = np.zeros_like(t)
    for k, (ra, am, de) in enumerate(zip(ratios, amps, decays)):
        f = f0 * ra * (1 + jitter * r.uniform(-1, 1))
        out += am * np.sin(2 * np.pi * f * t + r.uniform(0, 2 * np.pi)) * np.exp(-t / de)
    out *= np.clip(t / 0.0003, 0, 1)  # 0.3 ms attack ramp, no step
    return out


def sweep_sine(f_start, f_end, dur, tau, glide):
    """Low thunk: a sine whose pitch falls from f_start to f_end with time constant `glide`, amplitude decay tau."""
    t = tt(max(dur, 7 * tau))
    f = f_end + (f_start - f_end) * np.exp(-t / glide)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) * np.exp(-t / tau) * np.clip(t / 0.001, 0, 1)


def osc_harm(freq_curve, nharm, r, tilt=1.0):
    """Band-limited-ish harmonic oscillator following a per-sample frequency curve (1/k^tilt partials)."""
    ph = 2 * np.pi * np.cumsum(freq_curve) / SR
    out = np.zeros_like(freq_curve)
    for k in range(1, nharm + 1):
        mask = (freq_curve * k) < SR * 0.45
        out += mask * np.sin(k * ph + r.uniform(0, 2 * np.pi)) / k ** tilt
    return out


# ---------- finishing ----------

def _k_sos():
    # BS.1770 K-weighting as two biquads (same design as measure.py)
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
    """Maximum momentary loudness (K-weighted, 400 ms window, 10 ms hop), LUFS-like."""
    xk = signal.sosfilt(_k_sos(), x)
    win = int(0.4 * SR)
    sq = np.concatenate([xk ** 2, np.zeros(win)])
    c = np.concatenate([[0], np.cumsum(sq)])
    starts = np.arange(0, max(1, len(x)), int(0.01 * SR))
    ms = (c[starts + win] - c[starts]) / win
    return -0.691 + 10 * np.log10(max(ms.max(), 1e-12))


def limit(x, ceiling_db=-1.0, release=0.05):
    """Simple peak limiter: gain = min(1, ceiling/|x|) smoothed with instant attack and exponential release."""
    c = 10 ** (ceiling_db / 20)
    need = np.minimum(1.0, c / np.maximum(np.abs(x), 1e-9))
    # look-ahead 1 ms by taking a running minimum
    la = int(0.001 * SR)
    need = np.minimum.reduce([np.roll(need, -k) for k in range(la)])
    g = np.empty_like(need)
    cur = 1.0
    rel = np.exp(-1 / (release * SR))
    for i, n in enumerate(need):
        cur = n if n < cur else n + (cur - n) * rel
        g[i] = cur
    return x * g


def finish(x, target_mmax, fade_in_ms=1.0, fade_out_ms=15.0, hp=35.0):
    x = highpass(x, hp, 2)
    x = x - x.mean()
    ni = int(fade_in_ms / 1000 * SR)
    no = int(fade_out_ms / 1000 * SR)
    if ni:
        x[:ni] *= 0.5 - 0.5 * np.cos(np.linspace(0, np.pi, ni))
    if no:
        x[-no:] *= 0.5 + 0.5 * np.cos(np.linspace(0, np.pi, no))
    x[-1] = 0.0
    x = x * 10 ** ((target_mmax - m_max(x)) / 20)
    x = limit(x, -1.0)
    # one correction pass after limiting
    x = x * 10 ** ((target_mmax - m_max(x)) / 20)
    x = limit(x, -1.0)
    return x


def write_wav(path, x):
    y = np.clip(x, -1, 1)
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((y * 32767).astype('<i2').tobytes())


def to_ogg(wav, ogg, q=5):
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-ac', '1', '-ar', str(SR), '-c:a', 'libvorbis',
                    '-q:a', str(q), ogg], check=True)


def mix(*layers):
    """Sum layers of different lengths (zero-padded to the longest)."""
    n = max(len(l) for l in layers)
    out = np.zeros(n)
    for l in layers:
        out[:len(l)] += l
    return out


def norm(x):
    return x / max(np.max(np.abs(x)), 1e-12)


def steps(x, frame=0.005, drop_db=30.0, floor_db=-50.0):
    """Find hard cuts inside a sound: 5 ms frames where the level falls by more than drop_db in one frame
    while the frame before was above floor_db relative to the loudest frame. Returns the times (s)."""
    h = int(frame * SR)
    m = len(x) // h
    e = 20 * np.log10(np.sqrt((x[:m * h].reshape(m, h) ** 2).mean(1)) + 1e-12)
    e -= e.max()
    return [round((i + 1) * frame, 3) for i in range(m - 1) if e[i] > floor_db and e[i] - e[i + 1] > drop_db]

"""The arms family: the mecha's arm tools. The Arc Cannon (charge, crack, the hop from creature to creature), the
Plasma Lance (ignite, burn, collapse), the Flamethrower (ignite, roar), the Tractor Beam's hum, the Hydraulic Fist
(wind-up hiss, strike), the Rivet Gun, the Welding Torch, the Manipulator Hand, and two sounds of a hot or damaged
machine (a cooling tick, a spit of sparks).

Built from the house parts: the arc crackle (filtered-noise bursts 2-8 ms wide, 2-10 kHz, each burst decaying with a
time constant of at least 3 ms so nothing cuts hard), the hum (here at 1.5x and 3x the house 120 Hz: 180 Hz for the
Tractor Beam, 360 Hz for the Plasma Lance, each with a partner 0.5 Hz above), struck iron (strike, iron body, thunk),
the ratchet tooth, the relay click, the motor, hiss bands. No FM bell: that voice is the company talking.

Four files are loops (lance_hum, flame_roar, tractor_hum, weld_hiss): exactly 2.0 s, every periodic layer a whole
number of cycles (every frequency a multiple of 0.5 Hz), noise built periodic on the loop's own FFT bins
(recipes_fx.loop_noise), crackle bursts placed on a circle (a burst that runs past the end continues at the start),
no fades. Each is rotated to start on a zero crossing and passed through recipes_fx.loop_through_finish, so the file
that comes out of nsynth.finish() is the periodic signal itself.

Takes differ by construction (per-take tables of frequency scale, band, timing and length), not by luck of the seed.
"""
import numpy as np

from nsynth import (SR, TICK, tt, buf, place, mix, norm, bandpass, lowpass, highpass, sweep_bandpass, noise,
                    exp_env, noise_burst, sweep_sine, modal, osc_harm, tooth, strike, thunk, iron_body, relay_click,
                    speed_curve, motor, finish)
from recipes_fx import loop_noise, seam_on_zero, loop_through_finish, harmonic_loop, LOOP_N, LOOP_S

T = TICK


# ---------------------------------------------------------------- shared parts

def spark(r, lo, hi, w):
    """One arc burst: band-passed noise, 0.3 ms attack, decay w/3 (at least 3 ms, so no 5 ms frame drops 30 dB)."""
    return norm(noise_burst(lo, hi, max(w / 3, 0.003), r, attack=0.0003))


def flurry(r, dur, lo, hi, spacing, gain, width=(0.002, 0.008), first=1.0):
    """Arc crackle whose burst spacing and level follow the time: spacing(at) and gain(at) are functions of the
    burst's start time (s). The first burst is at 0 at level `first`."""
    out = buf(dur + 0.06)
    at = 0.0
    k = 0
    while at < dur:
        w = r.uniform(*width)
        g = first if k == 0 else gain(at) * r.uniform(0.45, 1.0)
        place(out, g * spark(r, lo, hi, w), at)
        at += spacing(at)
        k += 1
    return out


def soft(x, drive=2.0):
    """Gentle tanh saturation on the normalised signal: the first transient sits nearer the body, so the file reaches
    its loudness without the limiter flattening it."""
    p = max(np.max(np.abs(x)), 1e-12)
    return np.tanh(drive * x / p) / np.tanh(drive) * p


def place_circ(y, x, at, gain=1.0):
    """Add gain * x into the loop y starting at `at` (s), wrapping past the end back to the start, so the loop stays
    exactly periodic."""
    n = len(y)
    i = int(round(at * SR)) % n
    np.add.at(y, (i + np.arange(len(x))) % n, gain * x)
    return y


def rms(x):
    return float(np.sqrt(np.mean(x ** 2)))


def seam_balanced(y, crackle, side=0.05, guard=0.015, step=0.005):
    """Rotate a periodic signal so that no crackle burst (down to -60 dB) sounds within `guard` (s) of the seam and
    the first and last `side` seconds are as equal in level as possible (tried every `step` s round the loop), then
    onto the nearest zero crossing (seam_on_zero, within 5 ms). crackle: the crackle layer alone, aligned with y."""
    n = len(y)
    hs, g, st = int(side * SR), int(guard * SR), int(step * SR)
    yy = np.concatenate([y, y])
    loud = np.abs(np.concatenate([crackle, crackle, crackle])) > 1e-3 * np.max(np.abs(crackle))
    best, kb = None, 0
    for k in range(0, n, st):
        if loud[n + k - g:n + k + g].any():
            continue
        a = rms(yy[k:k + hs])
        b = rms(yy[k + n - hs:k + n])
        d = abs(20 * np.log10(a / b))
        if best is None or d < best:
            best, kb = d, k
    y2, _ = seam_on_zero(np.roll(y, -kb), search=221)
    return y2


# ---------------------------------------------------------------- the Arc Cannon

CHARGE = [dict(f0=2000.0, f1=8000.0, rise=0.27, grit=0.22), dict(f0=1700.0, f1=6600.0, rise=0.30, grit=0.32)]


def arc_charge(take, r):
    """The capacitor charging: a sine sweeping up 2 -> 8 kHz (take 2: 1.7 -> 6.6 kHz) log-linearly over 0.27-0.30 s,
    growing from a third of full level to full, with a faint octave below; grit (3-9 kHz noise gated at 120 Hz, the
    house buzz) that thickens as it nears full; then let go in a 12 ms decay so it is gone before the shot six ticks
    after the press. Starts at 3 ms (the press)."""
    p = CHARGE[take - 1]
    dur = 0.46
    b = buf(dur)
    t = tt(dur)
    t0, rise = 0.003, p['rise']
    u = np.clip((t - t0) / rise, 0, 1)
    f = p['f0'] * (p['f1'] / p['f0']) ** u
    ph = 2 * np.pi * np.cumsum(f) / SR
    tone = np.sin(ph + r.uniform(0, 6.28)) + 0.18 * np.sin(0.5 * ph + r.uniform(0, 6.28))
    env = (t >= t0) * np.clip((t - t0) / 0.004, 0, 1) * np.where(
        t < t0 + rise, 0.33 + 0.67 * u ** 1.5, np.exp(-(t - t0 - rise) / 0.012))
    b += 0.70 * norm(tone * env)
    gate = (0.5 + 0.5 * np.cos(2 * np.pi * 120 * t)) ** 4
    grit = bandpass(noise(dur, r), 3000, 9000) * gate * env * (0.3 + 0.7 * u)
    b += p['grit'] * norm(grit)
    return b


CRACK = [dict(lo=2000, hi=10000, dur=0.46, tau=0.14, thump=(120.0, 45.0)),
         dict(lo=1800, hi=8500, dur=0.50, tau=0.16, thump=(105.0, 40.0)),
         dict(lo=2400, hi=10000, dur=0.58, tau=0.19, thump=(135.0, 50.0))]


def arc_crack(take, r):
    """The shot: the arc crackle at its loudest. At 3 ms a full-level burst and the air's snap (a 0.5-12 kHz noise
    transient, 4 ms), with a short low body thump under it (a thunk falling 105-135 -> 40-50 Hz, 8 dB under); then a
    dense flurry (bursts 3-10 ms apart for 80 ms) thinning out (10-45 ms apart, growing) and dying away with a 0.13-0.19 s
    time constant over 0.46-0.58 s by take. Dry: no rumble, no reverb."""
    p = CRACK[take - 1]
    dur = p['dur'] + 0.30
    b = buf(dur)
    h = 0.003
    fl = flurry(r, p['dur'], p['lo'], p['hi'],
                spacing=lambda at: r.uniform(0.003, 0.010) if at < 0.08 else r.uniform(0.010, 0.045) * (1 + 2.0 * at),
                gain=lambda at: np.exp(-at / p['tau']))
    place(b, norm(fl), h, 1.0)
    place(b, norm(noise_burst(500, 12000, 0.004, r, attack=0.0003)), h, 0.80)
    place(b, strike(r, 2500), h, 0.40)
    place(b, thunk(r, p['thump'][0], p['thump'][1], 0.060, 0.020), h, 0.40)
    return soft(b, 2.5)


HOP = [dict(lo=2500, hi=9000, dur=0.100, n0=0.008), dict(lo=3000, hi=10000, dur=0.120, n0=0.010),
       dict(lo=2000, hi=7500, dur=0.140, n0=0.012), dict(lo=3500, hi=11000, dur=0.105, n0=0.007)]


def arc_hop(take, r):
    """One jump of the arc to the next creature: a smaller, drier crackle. A first burst at 3 ms, then bursts 7-27 ms
    apart (narrower: 1.5-4 ms) falling away over 100-140 ms by take, in a band 2-11 kHz set per take. No thump. Gently
    saturated so the first burst does not stand far above the rest."""
    p = HOP[take - 1]
    b = buf(p['dur'] + 0.20)
    fl = flurry(r, p['dur'], p['lo'], p['hi'], spacing=lambda at: r.uniform(p['n0'], p['n0'] + 0.015),
                gain=lambda at: np.exp(-at / (p['dur'] / 2.5)), width=(0.0015, 0.004))
    place(b, norm(fl), 0.003, 1.0)
    return soft(b, 1.5)


# ---------------------------------------------------------------- the Plasma Lance

LANCE_HARM = [1.0, 0.62, 0.45, 0.32, 0.22, 0.14, 0.09, 0.06]
IGNITE = [dict(m=1.00, siz=0.55, swell=0.10), dict(m=1.06, siz=0.70, swell=0.13)]


def lance_ignite(take, r):
    """The beam lighting: at 3 ms a sizzle onset (a 3-10 kHz fizz with a 30 ms decay and four crackle bursts), and the
    thick hum swelling under it: 360 Hz with harmonics 2-8 and a twin 3 Hz above, its pitch sliding up from 0.7x to
    full and its level from a fifth to full over 0.10-0.13 s; held to 0.34 s, then let go in 25 ms so the burn loop can
    take over."""
    p = IGNITE[take - 1]
    m = p['m']
    dur = 0.62
    b = buf(dur)
    t = tt(dur)
    t0 = 0.003
    u = np.clip((t - t0) / p['swell'], 0, 1)
    f = 360.0 * m * (0.7 + 0.3 * (1 - (1 - u) ** 2))
    ph = 2 * np.pi * np.cumsum(f) / SR
    hum = sum(a * np.sin(k * ph + r.uniform(0, 6.28)) for k, a in enumerate(LANCE_HARM, 1))
    ph2 = 2 * np.pi * np.cumsum(f + 3.0) / SR
    hum = hum + 0.45 * (np.sin(ph2 + r.uniform(0, 6.28)) + 0.4 * np.sin(2 * ph2 + r.uniform(0, 6.28)))
    hold = 0.34
    env = (t >= t0) * np.clip((t - t0) / 0.004, 0, 1) * (0.2 + 0.8 * u ** 1.3) * np.where(
        t < hold, 1.0, np.exp(-(t - hold) / 0.025))
    b += 0.75 * norm(hum * env)
    place(b, norm(noise_burst(3000 * m, 10000, 0.030, r, attack=0.001)), t0, p['siz'])
    for k, at in enumerate((0.0, 0.011, 0.024, 0.041)):
        place(b, spark(r, 2500, 9000, r.uniform(0.002, 0.006)), t0 + at, 0.50 * p['siz'] / (k + 1) ** 0.5)
    return b


def lance_hum(take, r):
    """The burning beam, a 2.0 s loop: the thick hum (360 Hz with harmonics 2-8, 720 cycles in the loop), a partner at
    360.5 Hz with its octave (721 and 1442 cycles: it beats once per loop), and a sizzle (2.5-9 kHz periodic noise,
    16 dB under the hum) flickering at 13 Hz and 3.5 Hz. No swell, no fades: the code chases pitch and volume.

    The loop starts at LANCE_SEAM, a zero crossing in the quiet stretch of the beat. The sizzle keeps the edges of the
    encoded file busy, so which crossing is used decides how closely the decoded first and last samples agree (from
    0.2% to 3.5% of the peak across the crossings near the null); at this one they agree to 0.2%. The offset belongs to
    this seed and this recipe: if either changes, choose the crossing again by decoding the file."""
    t = np.arange(LOOP_N) / SR
    hum = harmonic_loop(360.0, LANCE_HARM, r)
    partner = 0.5 * np.sin(2 * np.pi * 360.5 * t + r.uniform(0, 6.28)) + 0.2 * np.sin(2 * np.pi * 721.0 * t + r.uniform(0, 6.28))
    tone = hum + partner
    flick = 0.55 + 0.45 * (0.5 + 0.5 * np.cos(2 * np.pi * 13.0 * t + r.uniform(0, 6.28))) ** 2 * (
        0.6 + 0.4 * np.cos(2 * np.pi * 3.5 * t + r.uniform(0, 6.28)))
    siz = loop_noise(2500, 9000, r) * flick
    siz *= rms(tone) / rms(siz) * 10 ** (-16 / 20)
    y = np.roll(tone + siz, -LANCE_SEAM)
    assert np.sign(y[0]) != np.sign(y[-1]), 'LANCE_SEAM is not a zero crossing: choose it again'
    return loop_through_finish(y)


LANCE_SEAM = 588  # samples into the built cycle (see lance_hum)


OUT = [dict(m=1.00, fall=0.23, end=90.0, pop=0.255), dict(m=1.05, fall=0.27, end=120.0, pop=0.290)]


def lance_out(take, r):
    """The beam collapsing: the hum (360 Hz, harmonics 2-6) falls from full pitch to 90-120 Hz over 0.23-0.27 s,
    exponentially, its upper harmonics dying first and its level sagging; then a soft pop as it goes out (a small sine
    dropping 300 -> 120 Hz in 15 ms and a low-passed puff). Starts at 3 ms (the release)."""
    p = OUT[take - 1]
    m = p['m']
    dur = 0.62
    b = buf(dur)
    t = tt(dur)
    t0 = 0.003
    u = np.clip((t - t0) / p['fall'], 0, 1)
    f = 360.0 * m * (p['end'] / 360.0) ** (u ** 1.2)
    ph = 2 * np.pi * np.cumsum(f) / SR
    hum = sum(a * np.exp(-3.0 * (k - 1) * u) * np.sin(k * ph + r.uniform(0, 6.28))
              for k, a in enumerate(LANCE_HARM[:6], 1))
    env = (t >= t0) * np.clip((t - t0) / 0.003, 0, 1) * np.where(
        t < t0 + p['fall'], 1.0 - 0.55 * u ** 1.5, 0.45 * np.exp(-(t - t0 - p['fall']) / 0.010))
    b += 0.75 * norm(hum * env)
    place(b, sweep_sine(300 * m, 120 * m, 0.015, 0.008), p['pop'], 0.30)
    place(b, norm(lowpass(noise(0.08, r), 1500) * exp_env(0.08, 0.006, 0.0005)), p['pop'], 0.20)
    return b


# ---------------------------------------------------------------- the Flamethrower

def flame_roar(take, r):
    """The burning jet, a 2.0 s loop: a roar of periodic noise low-passed near 800 Hz (40-800 Hz) fluttering at 1.5,
    4 and 7.5 Hz, a breathier 0.8-3.5 kHz band 11 dB under it, a faint low gas rush (30-200 Hz), and fire crackle
    made periodic: small pops (1.5-7 kHz, 1-3 ms) 20-90 ms apart, some in pairs, placed on the loop's circle. The seam
    goes where no pop sounds within 15 ms and the 50 ms either side are level (seam_balanced)."""
    n = LOOP_N
    t = np.arange(n) / SR
    flut = (1 + 0.22 * np.sin(2 * np.pi * 1.5 * t + r.uniform(0, 6.28)) + 0.14 * np.sin(2 * np.pi * 4.0 * t + r.uniform(0, 6.28))
            + 0.08 * np.sin(2 * np.pi * 7.5 * t + r.uniform(0, 6.28)))
    roar = loop_noise(40, 800, r) * flut
    breath = loop_noise(800, 3500, r) * flut
    breath *= rms(roar) / rms(breath) * 10 ** (-11 / 20)
    rush = loop_noise(30, 200, r)
    rush *= rms(roar) / rms(rush) * 10 ** (-12 / 20)
    pops = np.zeros(n)
    at = r.uniform(0, 0.03)
    while at < LOOP_S:
        g = r.uniform(0.25, 1.0)
        place_circ(pops, spark(r, 1500, 7000, r.uniform(0.001, 0.003)), at, g)
        if r.uniform() < 0.3:
            place_circ(pops, spark(r, 1500, 7000, 0.002), at + r.uniform(0.004, 0.009), 0.6 * g)
        at += r.uniform(0.020, 0.090)
    y = roar + breath + rush
    pops *= 0.9 * np.max(np.abs(y)) / np.max(np.abs(pops))
    return loop_through_finish(seam_balanced(y + pops, pops))


FIGNITE = [dict(m=1.00, at=0.060, lp=1400.0, tau=0.075), dict(m=1.10, at=0.080, lp=1800.0, tau=0.065)]


def flame_ignite(take, r):
    """The pilot light and the catch: a piezo tick at 3 ms (a ratchet tooth at 3.2 kHz and a bright strike), then
    60-80 ms later a soft whoomp: low-passed noise swelling in 25 ms and dying with a 65-75 ms time constant, its
    cutoff opening to 1.4-1.8 kHz and closing again, over a low push (a sine falling 95 -> 55 Hz)."""
    p = FIGNITE[take - 1]
    m = p['m']
    dur = 0.70
    b = buf(dur)
    place(b, tooth(r, 3200 * m), 0.003, 0.55)
    place(b, strike(r, 3000), 0.003, 0.30)
    d = 0.55
    tw = tt(d)
    env = (0.5 - 0.5 * np.cos(np.pi * np.clip(tw / 0.025, 0, 1))) * np.where(tw < 0.025, 1.0, np.exp(-(tw - 0.025) / p['tau']))
    cut = 300 + (p['lp'] - 300) * np.exp(-((tw - 0.04) / 0.05) ** 2)
    wh = sweep_bandpass(noise(d, r), np.full(len(tw), 60.0), cut) * env
    place(b, norm(wh), p['at'], 0.85)
    place(b, sweep_sine(95 * m, 55 * m, 0.060, 0.030, attack=0.015), p['at'], 0.35)
    return b


# ---------------------------------------------------------------- the Tractor Beam

def tractor_hum(take, r):
    """The beam's hum, a 2.0 s loop: 180 Hz (1.5x the house hum) with harmonics 2-12 at 1/k, seen through a band-pass
    that sweeps once per loop (a slow 0.5 Hz wah, its centre 200 -> 520 Hz and back, a Gaussian 0.5 octave wide on a
    floor of 0.2), a 180.5 Hz partner 8 dB down (beats once per loop), and faint 0.5-2.5 kHz air. Each harmonic's gain
    is a function of the sweep, which is periodic in 2 s, so every layer closes on itself."""
    t = np.arange(LOOP_N) / SR
    f0 = 180.0
    c = 200.0 * 2.6 ** (0.5 - 0.5 * np.cos(2 * np.pi * 0.5 * t))
    y = np.zeros(LOOP_N)
    for k in range(1, 13):
        g = 0.2 + np.exp(-0.5 * (np.log2(k * f0 / c) / 0.5) ** 2)
        y += g / k * np.sin(2 * np.pi * f0 * k * t + r.uniform(0, 6.28))
    y += 0.4 * np.sin(2 * np.pi * 180.5 * t + r.uniform(0, 6.28))
    air = loop_noise(500, 2500, r)
    air *= rms(y) / rms(air) * 10 ** (-32 / 20)
    y, _ = seam_on_zero(y + air, quiet=True)
    return loop_through_finish(y)


# ---------------------------------------------------------------- the Hydraulic Fist

FHISS = [dict(m=1.00, hold=0.090, tau=0.028), dict(m=1.12, hold=0.110, tau=0.034), dict(m=0.90, hold=0.070, tau=0.024)]


def fist_hiss(take, r):
    """The wind-up: a valve tsk at 3 ms (4-12 kHz, 3 ms), then the cylinder charging: a hiss band (0.5-3.2 kHz, falling
    to 0.43-2.5 kHz: a big low cylinder, not a tyre valve) whose pressure rises in 15 ms, holds 70-110 ms and decays in
    24-34 ms; a small low-passed chuff of air under the start. Gone well before the blow eight ticks after the press."""
    p = FHISS[take - 1]
    m = p['m']
    dur = 0.50
    b = buf(dur)
    t = tt(dur)
    t0 = 0.004
    tr = np.maximum(t - t0, 0)
    pr = (t >= t0) * np.clip(tr / 0.015, 0, 1) * np.where(tr < p['hold'], 1.0, np.exp(-(tr - p['hold']) / p['tau']))
    lo = 500 * m * (1 - 0.15 * np.clip(tr / 0.15, 0, 1))
    hi = 3200 * m * (1 - 0.22 * np.clip(tr / 0.15, 0, 1))
    hiss = sweep_bandpass(noise(dur, r), lo, hi) * pr
    b += 0.80 * norm(hiss)
    place(b, norm(noise_burst(4000, 12000, 0.003, r, attack=0.0003)), 0.003, 0.45)
    place(b, norm(lowpass(noise(0.2, r), 600) * exp_env(0.2, 0.020, 0.004)), 0.004, 0.25)
    return b


STRIKE = [dict(m=1.00, dk=1.00), dict(m=1.10, dk=1.20), dict(m=0.88, dk=0.85)]


def fist_strike(take, r):
    """The blow: the piston strikes at 3 ms (a strike, a sharp 0.3-6 kHz crack), the fist's heavy iron body rings (iron
    body at 300 / 520 Hz and a 720 Hz plate), a deep thunk (110 -> 45 Hz, 8 dB under the body) gives the weight, a little ground debris;
    then the cylinder's exhaust chuffs (a 0.5-4 kHz puff 15 ms on) and a pin rattles at 60 ms. Gently saturated, and
    finished with a -2.2 dBFS ceiling (finish_strike): the blow's transient overshoots by up to 1 dB in the encoder."""
    q = STRIKE[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(1.30)
    h = 0.003
    place(b, strike(r, 1000 * m), h, 0.35)
    place(b, norm(noise_burst(300 * m, 6000 * m, 0.006, r, attack=0.0004)), h, 0.30)
    place(b, thunk(r, 110 * m, 45 * m, 0.090, 0.030), h, 0.32)
    place(b, norm(iron_body(r, 300 * m, 520 * m, 1.1 * dk, band_scale=m)), h, 0.80)
    place(b, norm(modal(720 * m, [1, 1.59, 2.14], [1, 0.6, 0.4], [0.045 * dk, 0.035 * dk, 0.022 * dk], r)), h, 0.30)
    place(b, norm(lowpass(noise(0.7, r), 3000) * exp_env(0.7, 0.05, 0.002)), h, 0.30)
    place(b, norm(noise_burst(500, 4000, 0.040, r, attack=0.006)), 0.018, 0.18)
    place(b, tooth(r, 2300 * m), 0.060, 0.12)
    return soft(b, 2.5)


def finish_strike(x, target_mmax):
    """The one-shot chain with a lower ceiling (-2.2 dBFS before encoding), so the decoded peak of the heavy blow
    stays under -1 dBFS."""
    return finish(x, target_mmax, fade_out_ms=30, ceiling_db=-2.2)


# ---------------------------------------------------------------- the Rivet Gun

RIVET = [dict(m=1.00, tink=0.055, tf=3300.0), dict(m=1.08, tink=0.072, tf=3600.0),
         dict(m=0.93, tink=0.060, tf=2900.0), dict(m=1.04, tink=0.090, tf=3900.0)]


def rivet_pak(take, r):
    """One rivet: the pneumatic 'pak' at 3 ms (a strike, the gun's short steel body at 1.3 kHz, a small thunk, and a
    very short 2-8 kHz hiss of exhaust, 10 ms) and the rivet's ricochet 55-90 ms later: a small high tink (2.9-3.9 kHz
    by take, ringing 24 ms) with a faint falling whine. Short enough to repeat every four ticks. Gently saturated."""
    q = RIVET[take - 1]
    m = q['m']
    b = buf(0.45)
    h = 0.003
    place(b, strike(r, 1500 * m), h, 0.55)
    place(b, norm(modal(1300 * m, [1, 2.756, 5.404], [1, 0.5, 0.3], [0.022, 0.015, 0.009], r)), h, 0.50)
    place(b, thunk(r, 200 * m, 120 * m, 0.025, 0.010), h, 0.30)
    place(b, norm(noise_burst(2000, 8000, 0.010, r, attack=0.0008)), h + 0.001, 0.45)
    tf = q['tf']
    place(b, norm(modal(tf, [1, 2.32, 3.61], [1, 0.4, 0.2], [0.024, 0.015, 0.008], r, 0.01)), q['tink'], 0.30)
    place(b, sweep_sine(tf * 1.25, tf * 0.95, 0.018, 0.020), q['tink'] + 0.002, 0.06)
    return soft(b, 1.5)


# ---------------------------------------------------------------- the Welding Torch

def weld_hiss(take, r):
    """The torch burning, a 2.0 s loop: a hiss band (3-12 kHz periodic noise, a little flutter at 6 and 17 Hz), arc
    crackle in the welder's rhythm (a cluster of two to five bursts every 125 ms, 16 to the loop, each cluster a random
    level and nudged up to 12 ms, plus a few strays), and the transformer's faint 120 Hz buzz (harmonics to 3 kHz,
    24 dB down). Bursts are placed on the loop's circle."""
    n = LOOP_N
    t = np.arange(n) / SR
    hiss = loop_noise(3000, 12000, r, order=3) * (1 + 0.15 * np.sin(2 * np.pi * 6.0 * t + r.uniform(0, 6.28))
                                                 + 0.10 * np.sin(2 * np.pi * 17.0 * t + r.uniform(0, 6.28)))
    cr = np.zeros(n)
    for j in range(16):
        at = j * 0.125 + r.uniform(0, 0.012)
        g = r.uniform(0.4, 1.0)
        for q in range(int(r.integers(2, 6))):
            place_circ(cr, spark(r, 2500, 10000, r.uniform(0.002, 0.006)), at, g * r.uniform(0.5, 1.0))
            at += r.uniform(0.004, 0.012)
    for _ in range(6):
        place_circ(cr, spark(r, 2500, 10000, 0.003), r.uniform(0, LOOP_S), r.uniform(0.2, 0.5))
    amps = [1 / k / np.sqrt(1 + (k * 120 / 3000) ** 4) for k in range(1, 26)]
    buzz = harmonic_loop(120.0, amps, r)
    hiss = norm(hiss)
    y = hiss + 1.1 * cr / np.max(np.abs(cr))
    buzz *= rms(hiss) / rms(buzz) * 10 ** (-24 / 20)
    y, _ = seam_on_zero(y + buzz, search=221)
    return loop_through_finish(y)


# ---------------------------------------------------------------- the Manipulator Hand

GRIP = [dict(m=1.00, move=2, dk=1.00), dict(m=1.10, move=2, dk=1.20), dict(m=0.92, move=3, dk=0.90)]


def hand_grip(take, r):
    """The hand closing: the relay click at 3 ms (the press), a short servo whir (the motor under sin^2 for 2-3 ticks),
    and the fingers clamping on the stop: a strike, small steel modes at 950 Hz, a thunk, a rubber-padded noise band;
    the lock's tooth 30 ms after."""
    q = GRIP[take - 1]
    m, dk = q['m'], q['dk']
    start = 0.008
    stop = start + q['move'] * T
    dur = round(stop + 0.40, 3)
    b = buf(dur)
    t = tt(dur)
    s = speed_curve(t, start, stop)
    b += 0.8 * motor(t, s, r, 300 * m, 800 * m, grit=0.45, air=0.16, whine=0.30)
    place(b, relay_click(r, 2000 * m), 0.003, 0.40)
    place(b, strike(r, 1800 * m), stop, 0.40)
    place(b, norm(modal(950 * m, [1, 2.756, 5.404], [1, 0.5, 0.3], [0.035 * dk, 0.024 * dk, 0.014 * dk], r)), stop, 0.50)
    place(b, thunk(r, 200 * m, 130 * m, 0.030, 0.012), stop, 0.30)
    place(b, norm(noise_burst(900 * m, 4500 * m, 0.012, r)), stop, 0.30)
    place(b, tooth(r, 2500 * m), stop + 0.030, 0.16)
    return b


# ---------------------------------------------------------------- a hot or damaged machine

TINK = [dict(f=2600.0, ring=0.020), dict(f=2950.0, ring=0.024), dict(f=2300.0, ring=0.017), dict(f=3400.0, ring=0.022)]


def thermal_tink(take, r):
    """A hot casing cooling: one small metal tick, the house ratchet tooth with a longer ring. A 2.5-7 kHz noise tick
    (1.2 ms) at 3 ms and three modes (x1, 1.58, 2.41) at 2.3-3.4 kHz by take, decaying with a 17-24 ms time constant
    (the ring is clearly there for about 40 ms), low-passed at 7 kHz so it stays small."""
    q = TINK[take - 1]
    b = buf(0.25)
    tick = mix(norm(noise_burst(2500, 7000, 0.0012, r)),
               0.8 * norm(modal(q['f'], [1, 1.58, 2.41], [1, 0.45, 0.2], [q['ring'], q['ring'] * 0.6, q['ring'] * 0.35], r, 0.01)))
    place(b, norm(tick), 0.003, 1.0)
    return lowpass(b, 7000)


SPARK = [dict(lo=2500, hi=10000, dur=0.12, fz=0.060), dict(lo=2000, hi=8000, dur=0.15, fz=0.065),
         dict(lo=3000, hi=11000, dur=0.09, fz=0.050)]


def spark_fizz(take, r):
    """A damaged machine spitting sparks: a short burst of arc crackle (bursts 4-15 ms apart, thinning, over 90-150 ms
    by take) from 3 ms, and a fizz under it (4-12 kHz noise, 3 ms rise, 50-65 ms decay) as the sparks fly."""
    q = SPARK[take - 1]
    b = buf(q['dur'] + 0.45)
    fl = flurry(r, q['dur'], q['lo'], q['hi'], spacing=lambda at: r.uniform(0.004, 0.015) * (1 + 3 * at),
                gain=lambda at: np.exp(-at / (q['dur'] / 1.8)), width=(0.0015, 0.005))
    place(b, norm(fl), 0.003, 1.0)
    place(b, norm(noise_burst(4000, 12000, q['fz'], r, attack=0.003)), 0.003, 0.40)
    return b


# ---------------------------------------------------------------- the event list

FLINT = dict(event='item.flintandsteel.use', file='fire__ignite.ogg', vol=1.0, pitch=1.0)
SMITHING = dict(event='block.smithing_table.use', file='block__smithing_table__smithing_table2.ogg', vol=1.0, pitch=0.95)
TWINKLE = dict(event='entity.firework_rocket.twinkle', file='fireworks__twinkle1.ogg', vol=1.0, pitch=0.975)
THUNDER = dict(event='entity.lightning_bolt.thunder', file='ambient__weather__thunder1.ogg', vol=1.0, pitch=0.9)
BOTTLE_FILL = dict(event='item.bottle.fill', file='item__bottle__fill1.ogg', vol=1.0, pitch=1.0)
GUARDIAN_BEAM = dict(event='entity.guardian.attack (full charge)', file='mob__guardian__attack_loop.ogg', vol=1.0, pitch=1.2)
FIRE_AMBIENT = dict(event='block.fire.ambient', file='fire__fire.ogg', vol=1.0, pitch=0.65)
BEACON_AMBIENT = dict(event='block.beacon.ambient', file='block__beacon__ambient.ogg', vol=1.0, pitch=1.0)
PISTON_IN = dict(event='block.piston.contract', file='tile__piston__in.ogg', vol=0.5, pitch=0.675)
GOLEM_HURT = dict(event='entity.iron_golem.hurt', file='mob__irongolem__hit1.ogg', vol=1.0, pitch=1.0)
IRON_DOOR_CLOSE = dict(event='block.iron_door.close', file='block__iron_door__close1.ogg', vol=0.9, pitch=0.95)
FIREWORK_BLAST = dict(event='entity.firework_rocket.blast', file='fireworks__blast1.ogg', vol=1.0, pitch=1.0)
FUSE = dict(event='entity.tnt.primed', file='random__fuse.ogg', vol=1.0, pitch=1.0)
CROSSBOW_MID = dict(event='item.crossbow.loading_middle', file='item__crossbow__loading_middle1.ogg', vol=0.325, pitch=1.0)
SPYGLASS = dict(event='item.spyglass.use', file='item__spyglass__use.ogg', vol=1.0, pitch=1.0)

BASE = dict(category='NEUTRAL', attenuation=16, fixed_range=False, volume=1.0, muffled=False)
LOOP_NOTE = (' Loop file: no fades in it. A client tickable instance created once per arm, started at volume 0 and '
             'stopped below 0.01 by the 0.2-per-tick chase; a fixed per-instance pitch offset x0.97-1.03.')
FIXED = ' Register fixed-range (createFixedRangeEvent) at its attenuation, so the server sends it that far.'


def ev(**kw):
    d = dict(BASE)
    d.update(kw)
    return d


# kind: a (name, seconds) pair gives the heard-length range seconds - 0.02 to seconds + 0.30 in measure.py
SOUNDS = [
    # ---- the Arc Cannon
    ev(id='arms.arc_charge', file='arms/arc_charge', fn=arc_charge, takes=2, target=-27.0, cls='servo (capacitor charge)',
       kind=('charge', 0.30), fade_ms=20, hit_ms=3, ref=FLINT, pitch=[0.95, 1.05], attenuation=24, fixed_range=True,
       subtitle_en='Arc Cannon charges', subtitle_pt='Canhão de Arco carrega',
       play='Server, on the press tick, at the arm. The whine starts at 3 ms and is gone by about 0.33 s; the shot (arms.arc_crack) follows 6 ticks later.' + FIXED),
    ev(id='arms.arc_crack', file='arms/arc_crack', fn=arc_crack, takes=3, target=-18.0, cls='signature moment (the shot)',
       kind=('crack', 0.40), fade_ms=40, hit_ms=3, ref=SMITHING, ref2=TWINKLE, ref3=THUNDER, pitch=[0.95, 1.05],
       attenuation=32, fixed_range=True, subtitle_en='Arc Cannon cracks', subtitle_pt='Canhão de Arco dispara',
       play='Server, on the shot tick (6 ticks after the press), at the arm; replaces the vanilla thunder sample. The crack lands at 3 ms; the crackle dies away over 0.4-0.6 s.' + FIXED),
    ev(id='arms.arc_hop', file='arms/arc_hop', fn=arc_hop, takes=4, target=-27.0, cls='servo class (one hop of the arc)',
       kind=('hop', 0.12), fade_ms=15, hit_ms=3, ref=FLINT, pitch=[0.94, 1.06], subtitle_en='Arc jumps', subtitle_pt='Arco salta',
       play='Server, at each creature the arc jumps to, 2 ticks apart per hop. Random take and pitch.'),
    # ---- the Plasma Lance
    ev(id='arms.lance_ignite', file='arms/lance_ignite', fn=lance_ignite, takes=2, target=-22.0, cls='clamp class (beam ignition)',
       kind=('ignite', 0.40), fade_ms=25, hit_ms=3, ref=IRON_DOOR_CLOSE, ref2=GUARDIAN_BEAM, pitch=[0.95, 1.05],
       attenuation=24, fixed_range=True, subtitle_en='Plasma Lance ignites', subtitle_pt='Lança de Plasma acende',
       play='Server, on the tick the beam lights, at the arm. The sizzle starts at 3 ms, the hum is full by 0.13 s and let go at 0.34 s; start arms.lance_hum on the same tick (it fades in by the chase).' + FIXED),
    ev(id='arms.lance_hum', file='arms/lance_hum', fn=lance_hum, takes=1, target=-24.0, cls='beam (loop, at full)',
       kind=('loop', LOOP_S), fade_ms=0, hit_ms=None, ref=BOTTLE_FILL, ref2=GUARDIAN_BEAM, pitch=[1.0, 1.0], attenuation=24,
       subtitle_en='Plasma Lance burns', subtitle_pt='Lança de Plasma queima', loop=True,
       play='Client loop at the arm while the beam burns. Volume 1.0 = -24 at full; pitch held within 0.75-1.25 if the code follows the beam\'s power.' + LOOP_NOTE),
    ev(id='arms.lance_out', file='arms/lance_out', fn=lance_out, takes=2, target=-27.0, cls='servo class (beam collapse)',
       kind=('out', 0.25), fade_ms=20, hit_ms=3, ref=BEACON_AMBIENT, pitch=[0.95, 1.05], subtitle_en='Plasma Lance cuts out', subtitle_pt='Lança de Plasma apaga',
       play='Server, on the tick the beam stops, at the arm; stop arms.lance_hum on the same tick. The fall runs 0.23-0.27 s and the pop lands at 255-290 ms.'),
    # ---- the Flamethrower
    ev(id='arms.flame_ignite', file='arms/flame_ignite', fn=flame_ignite, takes=2, target=-24.0, cls='hiss class (pilot and whoomp)',
       kind=('ignite', 0.35), fade_ms=25, hit_ms=3, ref=BOTTLE_FILL, ref2=FIRE_AMBIENT, pitch=[0.95, 1.05],
       subtitle_en='Flamethrower ignites', subtitle_pt='Lança-Chamas acende',
       play='Server, on the tick the flame starts, at the nozzle. The pilot tick is at 3 ms, the whoomp catches 60-80 ms later; start arms.flame_roar on the same tick.'),
    ev(id='arms.flame_roar', file='arms/flame_roar', fn=flame_roar, takes=1, target=-24.0, cls='hiss class (loop, at full)',
       kind=('loop', LOOP_S), fade_ms=0, hit_ms=None, ref=BOTTLE_FILL, ref2=FIRE_AMBIENT, pitch=[1.0, 1.0], attenuation=24,
       subtitle_en='Flamethrower roars', subtitle_pt='Lança-Chamas ruge', loop=True,
       play='Client loop at the nozzle while the flame runs. Volume 1.0 = -24 at full.' + LOOP_NOTE),
    # ---- the Tractor Beam
    ev(id='arms.tractor_hum', file='arms/tractor_hum', fn=tractor_hum, takes=1, target=-28.0, cls='hum (loop, at full)',
       kind=('loop', LOOP_S), fade_ms=0, hit_ms=None, ref=BEACON_AMBIENT, pitch=[1.0, 1.0],
       subtitle_en='Tractor Beam hums', subtitle_pt='Raio Trator zumbe', loop=True,
       play='Client loop at the arm while the beam holds. The wah sweeps once per 2 s loop. Volume 1.0 = -28 at full; pitch 0.75-1.25 if the code follows the held load.' + LOOP_NOTE),
    # ---- the Hydraulic Fist
    ev(id='arms.fist_hiss', file='arms/fist_hiss', fn=fist_hiss, takes=3, target=-27.0, cls='servo class (pneumatic wind-up)',
       kind=('hiss', 0.20), fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[0.94, 1.06], subtitle_en='Hydraulic Fist hisses', subtitle_pt='Punho Hidráulico chia',
       play='Server, on the press tick, at the fist. The valve tsk is at 3 ms and the hiss is gone by about 0.25 s; the blow (arms.fist_strike) lands 8 ticks later.'),
    ev(id='arms.fist_strike', file='arms/fist_strike', fn=fist_strike, finish=finish_strike, takes=3, target=-16.0, cls='heavy impact (the blow)',
       kind=('strike', 0.22), fade_ms=30, hit_ms=3, ref=GOLEM_HURT, pitch=[0.94, 1.06], attenuation=32, fixed_range=True,
       subtitle_en='Hydraulic Fist strikes', subtitle_pt='Punho Hidráulico golpeia',
       play='Server, on the contact tick (8 ticks after the press), at the fist. The strike is at 3 ms.' + FIXED),
    # ---- the Rivet Gun
    ev(id='arms.rivet_pak', file='arms/rivet_pak', fn=rivet_pak, takes=4, target=-22.0, cls='clamp class (pneumatic shot)',
       kind='latch', fade_ms=15, hit_ms=3, ref=FIREWORK_BLAST, pitch=[0.94, 1.06], attenuation=24, fixed_range=True,
       subtitle_en='Rivet Gun fires', subtitle_pt='Rebitadeira dispara',
       play='Server, on each shot (every 4 ticks while held), at the muzzle. The pak is at 3 ms, the ricochet tink at 55-90 ms. Random take, pitch 0.94-1.06, volume 0.9-1.0.' + FIXED),
    # ---- the Welding Torch
    ev(id='arms.weld_hiss', file='arms/weld_hiss', fn=weld_hiss, takes=1, target=-24.0, cls='hiss (loop, at full)',
       kind=('loop', LOOP_S), fade_ms=0, hit_ms=None, ref=FUSE, pitch=[1.0, 1.0],
       subtitle_en='Welding Torch hisses', subtitle_pt='Maçarico chia', loop=True,
       play='Client loop at the torch tip while it burns. Volume 1.0 = -24 at full.' + LOOP_NOTE),
    # ---- the Manipulator Hand
    ev(id='arms.hand_grip', file='arms/hand_grip', fn=hand_grip, takes=3, target=-27.0, cls='servo (hand closing)',
       kind='latch', fade_ms=15, hit_ms=3, ref=PISTON_IN, pitch=[0.94, 1.06],
       subtitle_en='Mecha hand grips', subtitle_pt='Mão Manipuladora agarra',
       play='Server, on the press tick, at the hand. The relay click is at 3 ms, the fingers clamp at 108-158 ms (2-3 ticks on) by take.'),
    # ---- a hot or damaged machine
    ev(id='arms.thermal_tink', file='arms/thermal_tink', fn=thermal_tink, takes=4, target=-36.0, cls='idle machine life',
       kind='click', fade_ms=10, hit_ms=3, ref=CROSSBOW_MID, pitch=[0.94, 1.06], attenuation=8,
       subtitle_en='Hot metal ticks', subtitle_pt='Metal quente estala',
       play='Client or server, now and then at a hot arm or engine after hard use (seconds apart, irregular). Very quiet by construction: play at volume 0.8-1.0.'),
    ev(id='arms.spark_fizz', file='arms/spark_fizz', fn=spark_fizz, takes=3, target=-31.0, cls='tick class (sparks)',
       kind=('fizz', 0.15), fade_ms=15, hit_ms=3, ref=SPYGLASS, pitch=[0.94, 1.06], attenuation=12,
       subtitle_en='Mecha sparks', subtitle_pt='Mecha solta faíscas',
       play='Server, now and then at a damaged mecha (low health), at a random point on the frame, with the spark particles. Random take and pitch.'),
]

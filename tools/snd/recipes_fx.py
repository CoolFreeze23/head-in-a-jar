"""The jar's abilities and the link's effects: Telekinesis (grab, hold, throw, set down, fizzle), the two body swaps,
the Lacrimal Laser, and the arc between linked Tesla Nodes.

Built from the house parts: relay clicks, sine whines, filtered-noise air, the hum (120 Hz family) and the arc crackle
(filtered-noise bursts 2-8 ms wide, 10-60 ms apart, 2-10 kHz). The FM bell voice is the company talking and is not used
here; nothing here may sound like the refusal (an FM E5 -> Eb5 over a thock), which the mod plays separately.

Two files are loops (tk.hold and tesla.arc): exactly 2.0 s, every periodic layer a whole number of cycles (180 Hz x 2 s
= 360, the 180.5 Hz partner 361, the 120 Hz buzz 240), noise made periodic by building it in the frequency domain on
the loop's own 0.5 Hz bins, and no fades. build.py passes every file through nsynth.finish(), which high-passes at
35 Hz from a zero filter state, removes DC, fades in over 1 ms and zeroes both end samples: all of that would put a
step at the seam. loop_through_finish() undoes it in advance (it divides the first millisecond by the fade, inverts the
high-pass and removes the mean), so the file that comes out of finish() is the periodic signal itself, rotated to
start on a zero crossing so the two zeroed end samples change almost nothing.

Takes differ by construction (per-take tables of frequency scale, decays, lengths and layer balance).
"""
import numpy as np
from scipy import signal

from nsynth import (SR, TICK, tt, buf, place, mix, norm, bandpass, lowpass, highpass, sweep_bandpass, noise,
                    exp_env, noise_burst, sweep_sine, relay_click, bubble, crackle)

T = TICK
LOOP_S = 2.0
LOOP_N = int(round(LOOP_S * SR))  # 88200 samples


# ---------------------------------------------------------------- loop helpers

def loop_noise(lo, hi, r, n=LOOP_N, order=2):
    """Band-limited noise that is exactly periodic over n samples: random phases on the loop's own FFT bins, shaped by
    a Butterworth-like band-pass magnitude. Peak-normalised."""
    f = np.fft.rfftfreq(n, 1 / SR)
    with np.errstate(divide='ignore'):
        mag = 1 / np.sqrt(1 + (f / hi) ** (2 * order)) / np.sqrt(1 + (lo / np.maximum(f, 1e-9)) ** (2 * order))
    mag[0] = 0.0
    spec = mag * np.exp(1j * r.uniform(0, 2 * np.pi, len(f)))
    return norm(np.fft.irfft(spec, n))


def seam_on_zero(y, search=441, quiet=False):
    """Rotate a periodic signal so it starts where it crosses zero (the smallest |y[n]| + |y[n-1]| in the first
    `search` samples), so finish() zeroing the first and last sample changes almost nothing. quiet: first move to the
    quietest 10 ms of the loop (for the hum, the partner beat's null), where the encoder's error at the file's edges is
    smallest. Returns the rotated signal and the total shift."""
    k0 = 0
    if quiet:
        w = int(0.01 * SR)
        lr = np.convolve(np.concatenate([y, y[:w]]) ** 2, np.ones(w) / w, 'valid')[:len(y)]
        k0 = int(np.argmin(lr))
        y = np.roll(y, -k0)
    k = int(np.argmin(np.abs(y[:search]) + np.abs(np.roll(y, 1)[:search])))
    return np.roll(y, -k), (k0 + k) % len(y)


def loop_through_finish(y, fade_in_ms=1.0, hp=35.0):
    """The signal z for which nsynth.finish(z, target, fade_out_ms=0) returns g x y (g the loudness gain; y[-1]
    becomes 0). finish() high-passes (2nd-order Butterworth at 35 Hz, zero initial state), subtracts the mean, fades
    the first `fade_in_ms` in with a raised cosine and zeroes both end samples; this applies the inverse of each step
    in reverse order. Its peak must stay under finish()'s -1.5 dBFS ceiling after the gain, or the limiter would act:
    at loop loudness it does not (checked in measurement: loop peaks are near -14 dBFS)."""
    y = np.asarray(y, dtype=np.float64)
    y = y - y.mean()
    n = len(y)
    ni = int(fade_in_ms / 1000 * SR)
    ramp = 0.5 - 0.5 * np.cos(np.linspace(0, np.pi, ni))
    h = y.copy()
    h[1:ni] = y[1:ni] / ramp[1:ni]
    h[0] = 0.0
    h[ni:] -= h.sum() / (n - ni)  # mean exactly zero, so finish()'s DC removal is a no-op
    b, a = signal.butter(2, hp, 'highpass', fs=SR)
    return signal.lfilter(a, b, h)  # inverse high-pass: finish()'s high-pass turns this back into h


def harmonic_loop(f0, amps, r, n=LOOP_N):
    """A sum of harmonics of f0 (f0 x loop length must be a whole number) with random phases."""
    t = np.arange(n) / SR
    assert abs(f0 * n / SR - round(f0 * n / SR)) < 1e-9, 'not a whole number of cycles'
    return sum(a * np.sin(2 * np.pi * f0 * k * t + r.uniform(0, 2 * np.pi)) for k, a in enumerate(amps, 1))


def sine_on(freq_curve, r, harm=(1.0,)):
    """Harmonic tone on a per-sample frequency curve."""
    ph = 2 * np.pi * np.cumsum(freq_curve) / SR
    return sum(a * np.sin(k * ph + r.uniform(0, 2 * np.pi)) for k, a in enumerate(harm, 1))


# ---------------------------------------------------------------- Telekinesis

GRAB = [dict(m=1.00, tau=0.030, air=0.40), dict(m=1.08, tau=0.036, air=0.48), dict(m=0.93, tau=0.026, air=0.34)]


def tk_grab(take, r):
    """Telekinesis takes hold: a relay click at 2 ms (the hit), a sine-glide whine rising 300 -> 900 Hz over 0.25 s
    (log-linear glide, harmonics 2 and 3 faint), then let go with a 26-36 ms decay so it is gone by about 0.42 s, and
    an air whoosh under it whose band rises with the whine (0.3 s)."""
    p = GRAB[take - 1]
    m = p['m']
    dur = 0.56
    b = buf(dur)
    t = tt(dur)
    t0, glide = 0.004, 0.25
    u = np.clip((t - t0) / glide, 0, 1)
    tone = sine_on(300 * m * 3.0 ** u, r, (1.0, 0.25, 0.08))
    env = np.clip((t - t0) / 0.02, 0, 1) * np.where(t < t0 + glide, 0.55 + 0.45 * u, np.exp(-(t - t0 - glide) / p['tau']))
    b += 0.55 * norm(tone * env)
    c = 500 * m * (1 + 2.5 * u)
    sw = sweep_bandpass(noise(dur, r), c / 1.5, c * 1.5)
    we = np.sin(np.pi * np.clip(t / 0.30, 0, 1)) ** 2
    b += p['air'] * norm(sw * we)
    place(b, relay_click(r, 1900 * m), 0.002, 0.45)
    return b


def tk_hold(take, r):
    """The hold loop, exactly 2.0 s: the house hum at 1.5x (180 Hz with harmonics 2-4 at 0.5, 0.3, 0.15), a 180.5 Hz
    partner at -6 dB (beats once per 2 s), faint 1-3 kHz air at -30 dB. No swell, no wobble, no fades: the code chases
    pitch (0.75-1.25) and volume and adds the labouring wobble by pitch. The loop point is the beat's null."""
    t = np.arange(LOOP_N) / SR
    hum = harmonic_loop(180.0, [1, 0.5, 0.3, 0.15], r)
    partner = 0.5 * np.sin(2 * np.pi * 180.5 * t + r.uniform(0, 2 * np.pi))
    tone = hum + partner
    air = loop_noise(1000, 3000, r)
    air *= np.sqrt(np.mean(tone ** 2) / np.mean(air ** 2)) * 10 ** (-30 / 20)
    y, _ = seam_on_zero(tone + air, quiet=True)  # every layer is periodic: the seam goes in the beat's null
    return loop_through_finish(y)


THROW = [dict(m=1.00, tau=0.085, lp=1800.0), dict(m=1.10, tau=0.100, lp=2100.0), dict(m=0.90, tau=0.072, lp=1500.0)]


def tk_throw(take, r):
    """The throw: the whine falling fast, 900 -> 200 Hz in 0.15 s, starting at once (2 ms, the hit), into an air
    burst: a short push transient and a shove of low-passed noise (12 ms attack, 72-100 ms decay) whose cutoff falls
    from 1.5-2.1 kHz to a third of that, heard about 0.4-0.5 s."""
    p = THROW[take - 1]
    m = p['m']
    dur = 0.78
    b = buf(dur)
    t = tt(dur)
    t0 = 0.002
    u = np.clip((t - t0) / 0.15, 0, 1)
    tone = sine_on(900 * m * (200 / 900) ** u, r, (1.0, 0.3, 0.1))
    wenv = np.clip((t - t0) / 0.003, 0, 1) * np.exp(-np.maximum(t - t0, 0) / 0.065)
    b += 0.50 * norm(tone * wenv)
    cut = p['lp'] * (0.33 + 0.67 * np.exp(-np.maximum(t - t0, 0) / 0.12))
    air = sweep_bandpass(noise(dur, r), np.full(len(t), 60.0), cut)
    aenv = np.clip((t - t0) / 0.012, 0, 1) * np.exp(-np.maximum(t - t0, 0) / p['tau'])
    b += 0.70 * norm(air * aenv)
    place(b, norm(noise_burst(300 * m, 3000 * m, 0.006, r)), t0, 0.30)
    return b


SET_DOWN = [880.0, 988.0, 784.0]


def pip(f, hold, tau, attack=0.005):
    t = tt(attack + hold + 7 * tau)
    env = np.clip(t / attack, 0, 1) * np.where(t < attack + hold, 1.0, np.exp(-(t - attack - hold) / tau))
    return np.sin(2 * np.pi * f * t) * env


def tk_set_down(take, r):
    """Let go gently: two short sine pips, the second a major third lower (x0.8) 65 ms later, soft 5 ms attacks,
    dry. Plain sines, not the house bell: this is the module, not the company talking."""
    f = SET_DOWN[take - 1]
    b = buf(0.32)
    place(b, pip(f, 0.025, 0.012), 0.003, 1.0)
    place(b, pip(f * 0.8, 0.035, 0.018), 0.068, 0.85)
    return b


FIZZLE = [dict(m=1.00, st=0.14, band=(1800, 6000)), dict(m=1.10, st=0.19, band=(2200, 7500)),
          dict(m=1.00, st=0.24, band=(2000, 7000))]


def tk_fizzle(take, r):
    """A grab that fails: the relay click and the grab whine start (300 Hz rising), but at 560 Hz (where a real grab
    is at 0.14 s) it stops rising and strains for 0.2 s (an 11 Hz and a 4.3 Hz pitch wobble, 13 Hz tremor), then sags
    and dies (35 ms) into a short crackle of static (1.5-7.5 kHz by take, thinning out over 0.14-0.24 s). No bell, no
    falling interval: not the refusal."""
    p = FIZZLE[take - 1]
    m = p['m']
    dur = 0.78
    b = buf(dur)
    t = tt(dur)
    t0, rise, end = 0.004, 0.10, 0.30
    strain = np.clip((t - 0.06) / 0.06, 0, 1)
    wob = 1 + strain * (0.05 * np.sin(2 * np.pi * 11 * t) + 0.03 * np.sin(2 * np.pi * 4.3 * t + r.uniform(0, 6.28)))
    sag = np.where(t < end, 1.0, 0.7 + 0.3 * np.exp(-(t - end) / 0.04))
    f = np.where(t < t0 + rise, 300 * m * (560 / 300) ** np.clip((t - t0) / rise, 0, 1), 560 * m) * wob * sag
    tone = sine_on(f, r, (1.0, 0.3, 0.12))
    env = np.clip((t - t0) / 0.02, 0, 1) * np.where(
        t < end, (0.7 + 0.3 * np.clip((t - t0) / rise, 0, 1)) * (0.8 + 0.2 * np.sin(2 * np.pi * 13 * t)),
        np.exp(-(t - end) / 0.035))
    b += 0.55 * norm(tone * env)
    st = crackle(r, p['st'], p['band'][0], p['band'][1], spacing=(0.004, 0.018), width=(0.001, 0.004))
    st = st * np.exp(-tt(len(st) / SR) / (p['st'] / 2.5))
    place(b, norm(st), end - 0.01, 0.65)
    place(b, relay_click(r, 1900 * m), 0.002, 0.40)
    return b


# ---------------------------------------------------------------- the swaps

SWAP = [dict(m=1.00, tilt=0.60), dict(m=0.88, tilt=0.75)]


def swap(take, r, up):
    """A 0.5 s static wipe: noise through a band (x1.3 either side of its centre) whose centre sweeps 500 Hz <-> 7 kHz
    log-linearly (up: head to body; down: body to head), roughened by a 2 ms random gate so it reads as static, a
    little quieter at the top of the sweep; the relay click (the swap) at 248 ms; the down sweep ends in a small
    bubble pip at 470 ms (back into the glass)."""
    p = SWAP[take - 1]
    m = p['m']
    dur = 0.66
    b = buf(dur)
    t = tt(dur)
    t0, length = 0.002, 0.495
    u = np.clip((t - t0) / length, 0, 1)
    c = 500 * m * 14.0 ** (u if up else 1 - u)
    band = sweep_bandpass(noise(dur, r), c / 1.3, c * 1.3)
    blk = int(0.002 * SR)
    g = np.repeat(r.uniform(0, 1, len(t) // blk + 1) ** 2, blk)[:len(t)]
    g = lowpass(g, 400)
    gate = 0.45 + 0.55 * g / max(g.max(), 1e-9)
    tilt = (c / (500 * m)) ** -p['tilt']
    rise_e = np.clip((t - t0) / 0.003, 0, 1)
    fall_e = np.clip((t0 + length - t) / 0.07, 0, 1)
    env = (0.5 - 0.5 * np.cos(np.pi * rise_e)) * (0.5 - 0.5 * np.cos(np.pi * fall_e)) ** 2  # smooth to the 4th power at the end
    b += 0.50 * norm(band * gate * tilt * env)
    place(b, relay_click(r, 1800 * m, release_after=0.025), 0.248, 1.40)
    if not up:
        place(b, bubble(r, 900 * m, 0.020, rise=0.25), 0.470, 0.35)
    return b


# ---------------------------------------------------------------- the Lacrimal Laser

LASER = [dict(m=1.00, hiss=0.20), dict(m=1.07, hiss=0.26), dict(m=0.93, hiss=0.15)]


def laser_fire(take, r):
    """The 6-tick beam: from 3 ms a glassy well-up shimmer (four inharmonic partials near 1.85 kHz, each with a twin
    7 Hz above, swelling over 0.09 s and gone by 0.16 s) over a sine rising 700 -> 1400 Hz; the beam body starts at
    once and fills by 0.1 s: a thin hum at 3x the house hum (360 Hz, harmonics 2-4) with a 3-9 kHz hiss, held to
    0.38 s, released in 20 ms; then a single small drip at 0.40 s (a sine falling 1.5 -> 0.8 kHz, the last tear)."""
    p = LASER[take - 1]
    m = p['m']
    dur = 0.62
    b = buf(dur)
    t = tt(dur)
    t0 = 0.003
    well = np.clip((t - t0) / 0.09, 0, 1)
    gone = np.clip((t - 0.10) / 0.06, 0, 1)
    senv = (t >= t0) * (0.15 + 0.85 * well) ** 2 * (0.5 + 0.5 * np.cos(np.pi * gone)) ** 2
    sh = np.zeros(len(t))
    for ra, a in zip([1, 1.5, 2.04, 2.7], [1, 0.6, 0.4, 0.25]):
        f = 1850 * m * ra
        sh += a * (np.sin(2 * np.pi * f * t + r.uniform(0, 6.28)) + 0.7 * np.sin(2 * np.pi * (f + 7) * t + r.uniform(0, 6.28)))
    rise = sine_on(700 * m * 2.0 ** np.clip((t - t0) / 0.1, 0, 1), r)
    b += 0.30 * norm(sh * senv) + 0.18 * norm(rise * senv)
    hold_end = 0.38
    benv = np.clip((t - t0) / 0.003, 0, 1) * (0.3 + 0.7 * np.clip((t - t0) / 0.1, 0, 1)) * np.where(
        t < hold_end, 1.0, np.exp(-(t - hold_end) / 0.02))
    hum = sine_on(np.full(len(t), 360.0 * m), r, (1.0, 0.5, 0.3, 0.15))
    hiss = bandpass(noise(dur, r), 3000 * m, 9000 * m)
    b += 0.55 * norm(hum * benv) + p['hiss'] * norm(hiss * benv)
    place(b, sweep_sine(1500 * m, 800 * m, 0.018, 0.012), 0.40, 0.40)
    return b


# ---------------------------------------------------------------- the Tesla Node arc

def tesla_arc(take, r):
    """The arc between linked Tesla Nodes, a 2.0 s loop: the house arc crackle (filtered-noise bursts 2-8 ms wide,
    10-60 ms apart, 2-10 kHz, each a random level) in flurries, over a faint 120 Hz buzz (harmonics 1-40 at 1/k,
    rolled off above 3 kHz, -22 dB under the crackle's peak). No burst sounds within 50 ms of the seam (bursts start
    55 ms in; the zero-crossing rotation moves them up to 5 ms), so the first and last 50 ms are the buzz alone; three more lulls of 70-130 ms fall at random inside the loop, so the
    gap at the seam is one flicker among several, not a beat every 2 s."""
    n = LOOP_N
    y = np.zeros(n)
    first, last = 0.055, LOOP_S - 0.055 - 0.025  # a burst's tail is under 25 ms (7 decay times of at most 2.7 ms)
    lulls = [r.uniform(lo, hi) for lo, hi in ((0.25, 0.55), (0.80, 1.15), (1.35, 1.70))]
    lull_len = r.uniform(0.07, 0.13, 3)
    at = first
    while at < last:
        if any(l <= at < l + ll for l, ll in zip(lulls, lull_len)):
            at += 0.005
            continue
        w = r.uniform(0.002, 0.008)
        g = r.uniform(0.25, 1.0)
        place(y, g * norm(noise_burst(2000, 10000, max(w / 3, 0.003), r, attack=0.0003)), at)
        at += r.uniform(0.010, 0.060)
    amps = [1 / kk / np.sqrt(1 + (kk * 120 / 3000) ** 4) for kk in range(1, 41)]
    buzz = harmonic_loop(120.0, amps, r)
    y = norm(y) + 0.08 * norm(buzz)
    y, _ = seam_on_zero(y, search=221)
    return loop_through_finish(y)


# ---------------------------------------------------------------- the event list

PISTON_IN = dict(event='block.piston.contract', file='tile__piston__in.ogg', vol=0.5, pitch=0.675)
BOTTLE_FILL = dict(event='item.bottle.fill', file='item__bottle__fill1.ogg', vol=1.0, pitch=1.0)
CRAFTER = dict(event='block.crafter.craft', file='block__crafter__craft.ogg', vol=1.0, pitch=1.0)
BEACON_AMBIENT = dict(event='block.beacon.ambient', file='block__beacon__ambient.ogg', vol=0.9, pitch=1.0)
SPYGLASS = dict(event='item.spyglass.use', file='item__spyglass__use.ogg', vol=1.0, pitch=1.0)
GUARDIAN_BEAM = dict(event='entity.guardian.attack (full charge)', file='mob__guardian__attack_loop.ogg', vol=1.0, pitch=1.2)

BASE = dict(category='PLAYERS', attenuation=16, fixed_range=False, volume=1.0, muffled=False)
LOOP_NOTE = (' Loop file: no fades in it. A client tickable instance created once, started at volume 0 and stopped at '
             '0.01 by the 0.2-per-tick chase; a fixed per-instance pitch offset x0.97-1.03.')


def ev(**kw):
    d = dict(BASE)
    d.update(kw)
    return d


# kind: a (name, seconds) pair gives the heard-length range seconds - 0.02 to seconds + 0.30 in measure.py
SOUNDS = [
    ev(id='fx.tk.grab', file='fx/tk_grab', fn=tk_grab, takes=3, target=-27.0, cls='servo (telekinesis grab)',
       kind=('grab', 0.25), fade_ms=20, hit_ms=2, ref=PISTON_IN, pitch=[0.94, 1.06],
       subtitle_en='Telekinesis takes hold', subtitle_pt='Telecinese agarra',
       play='Server, on the grab tick, at the held thing. The relay click (the hit) is at 2 ms; the whine is gone by about 0.42 s, so start fx.tk.hold on the same tick (it fades in by the chase). Replaces beacon.activate at pitch 2.'),
    ev(id='fx.tk.hold', file='fx/tk_hold', fn=tk_hold, takes=1, target=-28.0, cls='hum (loop, at full)',
       kind=('loop', LOOP_S), fade_ms=0, hit_ms=None, ref=BEACON_AMBIENT, pitch=[1.0, 1.0], attenuation=10,
       subtitle_en='Telekinesis hums', subtitle_pt='Telecinese zumbe', loop=True,
       play='Client loop at the held thing while it is held. Pitch 0.75-1.25 and volume chased from the held mass (heavier: lower and labouring); the wobble is the code\'s, by pitch (the file has none). Base volume 1.0 = -28 at full.' + LOOP_NOTE),
    ev(id='fx.tk.throw', file='fx/tk_throw', fn=tk_throw, takes=3, target=-24.0, cls='hiss (air burst)',
       kind=('throw', 0.30), fade_ms=25, hit_ms=2, ref=BOTTLE_FILL, pitch=[0.94, 1.06],
       subtitle_en='Telekinesis throws', subtitle_pt='Telecinese arremessa',
       play='Server, on the throw tick, at the thrown thing (stop fx.tk.hold on the same tick). The whine starts at 2 ms. Replaces illusioner.cast_spell. Vanilla impact sounds follow when it lands.'),
    ev(id='fx.tk.set_down', file='fx/tk_set_down', fn=tk_set_down, takes=3, target=-31.0, cls='blip',
       kind='latch', fade_ms=15, hit_ms=3, ref=CRAFTER, pitch=[0.94, 1.06], attenuation=8,
       subtitle_en='Telekinesis lets go', subtitle_pt='Telecinese solta',
       play='Server, when the player releases the held thing gently (R again), at the thing; stop fx.tk.hold on the same tick.'),
    ev(id='fx.tk.fizzle', file='fx/tk_fizzle', fn=tk_fizzle, takes=3, target=-27.0, cls='servo (failed grab)',
       kind=('fizzle', 0.42), fade_ms=25, hit_ms=2, ref=PISTON_IN, pitch=[0.94, 1.06],
       subtitle_en='Telekinesis fizzles', subtitle_pt='Telecinese pifa',
       play='Server, on a grab that fails (a block entity, too heavy, protected, nothing in sight), at the jar. Then the mod\'s own refusal (feedback.refused) separately, as it already does; this file is not a refusal and carries no bell.'),
    ev(id='fx.swap.up', file='fx/swap_up', fn=lambda take, r: swap(take, r, True), takes=2, target=-27.0,
       cls='servo (static wipe)', kind=('swap', 0.50), fade_ms=20, hit_ms=250, preroll_ticks=5, ref=PISTON_IN,
       pitch=[1.0, 1.0], subtitle_en='Mind leaves the jar', subtitle_pt='Mente sai do pote',
       play='Head to body. The relay click (the swap) is at 250 ms = 5 ticks: start the sound 5 ticks before the swap happens (or swap 5 ticks after starting it). Fixed pitch so the click stays on the tick; the two takes give the variety. Played to the swapping player and at the body.'),
    ev(id='fx.swap.down', file='fx/swap_down', fn=lambda take, r: swap(take, r, False), takes=2, target=-27.0,
       cls='servo (static wipe)', kind=('swap', 0.50), fade_ms=20, hit_ms=250, preroll_ticks=5, ref=PISTON_IN,
       pitch=[1.0, 1.0], subtitle_en='Mind returns to jar', subtitle_pt='Mente volta ao pote',
       play='Body to head: the same wipe sweeping down, ending in a small bubble pip at 470 ms. Click (the swap) at 250 ms = 5 ticks: start it 5 ticks before the swap. Fixed pitch.'),
    ev(id='fx.laser.fire', file='fx/laser_fire', fn=laser_fire, takes=3, target=-24.0, cls='beam fire',
       kind=('beam', 0.40), fade_ms=25, hit_ms=3, ref=BOTTLE_FILL, ref2=GUARDIAN_BEAM, pitch=[0.94, 1.06],
       attenuation=24, fixed_range=True,
       subtitle_en='Lacrimal Laser fires', subtitle_pt='Laser Lacrimal dispara',
       play='Server, on the fire tick, at the jar. The beam starts at once (3 ms) and the file covers the 6-tick beam (0.38 s) plus the drip at 0.40 s. Register fixed-range 24 (a weapon, heard across a room).'),
    ev(id='fx.tesla.arc', file='fx/tesla_arc', fn=tesla_arc, takes=1, target=-31.0, cls='arc crackle (loop)',
       kind=('loop', LOOP_S), fade_ms=0, hit_ms=None, ref=SPYGLASS, ref2=CRAFTER, pitch=[1.0, 1.0],
       category='BLOCKS', subtitle_en='Tesla Node arcs', subtitle_pt='Nó de Tesla faísca', loop=True,
       play='Client loop at the midpoint of the arc between two linked Tesla Nodes while the arc is drawn, attenuation 16. Volume 1.0.' + LOOP_NOTE),
]

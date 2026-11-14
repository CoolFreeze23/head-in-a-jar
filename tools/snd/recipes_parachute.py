"""The Golden Parachute: the gold-brocade canopy that lowers an ejected jar, shared by every frame's eject.

Heavy cloth, not nylon: gold brocade with a crimson valance, brass tassels, white cords and an ivory envelope pack. So
the cloth is low and slow (a band under about 1.5 kHz, fluttering at 4-20 Hz where nylon would buzz at 30-60), with
crease grains from the stiff weave and only the faintest metallic sheen of the gold thread. The tassels are small
brass; the cords creak as rope does on brass fittings; in water the brocade sinks with a glug.

Event ids are the shared `mecha.<name>`; files go under `mecha/parachute/`. The names and their ticks come from the
canopy's clips (`art/entity/golden_parachute/golden_parachute.bbmodel`). Every one is a keyframe sound and hits at
0-5 ms. The canopy's opening snap and its fold are the existing `mecha.parachute_pop` and `mecha.parachute_fold`
(in `recipes_mecha`), not remade here. Takes differ by construction (per-take tables).

Shared parts come from the kit (`nsynth`), the Bulwark's joint creak and the Kite's flutter and air parts.
"""
import numpy as np
from nsynth import *
from recipes_bulwark import creak
from recipes_kite import flutter, air_thump, spar_tick, after, ELYTRA_EQUIP, EQUIP_GENERIC, EQUIP_GOLD

T = TICK


# ---------------------------------------------------------------- the brocade

def brocade(r, dur, env, lo=90.0, hi=1300.0, rate=9.0, depth=0.7, grains=0.25, sheen=0.03,
            spacing=(0.012, 0.035)):
    """Heavy gold brocade moving: band-passed noise (lo-hi, softened above hi) under a slow, deep flutter (`rate` Hz,
    wandering 25 %), crease grains of the stiff weave (2.5 ms bursts at 700-3200 Hz, spaced 12-35 ms, each as loud as
    the envelope where it falls) and a faint 3-7 kHz sheen of the gold thread on the flutter's peaks."""
    n = len(env)
    f = flutter(n, rate, r, depth, power=3.0, jitter=0.25)
    out = norm(lowpass(bandpass(noise(dur, r), lo, hi), hi * 0.8) * f * env)
    g = buf(dur + 0.05)
    at = 0.0
    while at < dur:
        e = float(env[min(int(at * SR), n - 1)])
        if e > 0.02:
            place(g, norm(noise_burst(700, 3200, 0.0025, r, attack=0.0004)), at, e * r.uniform(0.3, 1.0))
        at += r.uniform(*spacing)
    if grains:
        out = out + grains * norm(g)[:n]
    if sheen:
        out = out + sheen * norm(bandpass(noise(dur, r), 3000, 7000) * f ** 2 * env)
    return out


def cloth_slap(r, m, tau=0.012, lo=150.0, hi=1500.0):
    """A fold of heavy cloth meeting air or a surface: a short low noise burst with a fast attack."""
    return norm(noise_burst(lo * m, hi * m, tau, r, attack=0.0006))


# ---------------------------------------------------------------- pack and pilot

EPOP = [dict(m=1.00, puff=0.006), dict(m=1.06, puff=0.010), dict(m=0.88, puff=0.004)]


def envelope_pop(take, r):
    """The ivory envelope pack's flap popping open (deploy 10): the press stud letting go at 3 ms (a short pop
    falling 650 -> 260 Hz and a small strike), the brass catch's tick, the canvas flap snapping open and fluttering
    a moment, a puff of air from the pack (300-2000 Hz)."""
    q = EPOP[take - 1]
    m = q['m']
    dur = 0.45
    b = buf(dur)
    t = tt(dur)
    place(b, sweep_sine(650 * m, 260 * m, 0.018, 0.006, attack=0.0005), 0.003, 0.55)
    place(b, strike(r, 2000), 0.003, 0.22)
    place(b, spar_tick(r, 2300 * m, decay=0.008), 0.003, 0.12)
    place(b, cloth_slap(r, m, 0.012, 250, 2200), 0.004, 0.55)
    td, g = after(t, 0.006)
    b += 0.22 * brocade(r, dur, g * np.clip(td / 0.004, 0, 1) * np.exp(-td / 0.045), 160 * m, 1800 * m, 26.0,
                        grains=0.2, sheen=0.0)
    place(b, norm(noise_burst(300 * m, 2000 * m, 0.045, r, attack=0.004)), q['puff'], 0.25)
    return b


PWHOOSH = [dict(m=1.00, L=0.28), dict(m=1.05, L=0.24), dict(m=0.90, L=0.32)]


def pilot_whoosh(take, r):
    """The pilot chute shooting out (deploy 12): a quick swing of air 0.24-0.32 s long through a band that opens from
    130-420 Hz to 380-1370 Hz with its speed, a thin cord zip under it, and the little chute catching the air at 80 %
    of the swing (a small cloth pop and a short flutter). It starts at -14 dB so the onset is on the key."""
    q = PWHOOSH[take - 1]
    m, L = q['m'], q['L']
    dur = round(L + 0.40, 3)
    b = buf(dur)
    t = tt(dur)
    s = np.where(t < L, np.sin(np.pi * np.clip(t / L, 0, 1)) ** 2, 0.0)
    env = 0.20 * np.clip(t / 0.003, 0, 1) * np.clip(1 - t / 0.12, 0, 1) + 0.80 * s
    b += 0.80 * norm(sweep_bandpass(noise(dur, r), (130 + 250 * s) * m, (420 + 950 * s) * m) * env)
    b += 0.06 * norm(bandpass(noise(dur, r), 1500, 5000) * env ** 2)
    c = 0.8 * L
    place(b, cloth_slap(r, m, 0.010, 300, 2000), c, 0.35)
    td, g = after(t, c)
    b += 0.25 * brocade(r, dur, g * np.clip(td / 0.005, 0, 1) * np.exp(-td / 0.06), 180 * m, 1800 * m, 22.0,
                        grains=0.2, sheen=0.0)
    return b


# ---------------------------------------------------------------- the canopy in the air

STREAM = [dict(m=1.00, L=0.30, r0=9.0, r1=16.0), dict(m=1.10, L=0.27, r0=11.0, r1=18.0),
          dict(m=0.90, L=0.33, r0=8.0, r1=14.0)]


def brocade_stream(take, r):
    """The canopy dragged out as a tall streaming bundle (deploy 14, six ticks before the snap on 20): the bundle
    leaving the pack with a slap at 3 ms, then the brocade fluttering harder and faster (8 -> 18 Hz) as it streams,
    air rushing past it, swelling for 0.27-0.33 s and stopping quickly where the snap takes over."""
    q = STREAM[take - 1]
    m, L = q['m'], q['L']
    dur = round(L + 0.35, 3)
    b = buf(dur)
    t = tt(dur)
    place(b, cloth_slap(r, m, 0.014, 150, 1500), 0.003, 0.50)
    place(b, thunk(r, 140 * m, 100 * m, 0.02, 0.01), 0.003, 0.15)
    u = np.clip(t / L, 0, 1)
    env = np.where(t < L, 0.45 + 0.55 * u, np.exp(-(t - L) / 0.025)) * np.clip(t / 0.004, 0, 1)
    b += 0.75 * brocade(r, dur, env, 100 * m, 1500 * m, q['r0'] + (q['r1'] - q['r0']) * u, depth=0.8)
    b += 0.30 * norm(bandpass(noise(dur, r), 300 * m, 2000 * m) * env ** 1.5)
    return b


BREATHE = [dict(m=1.00, L=0.80, rate=4.5), dict(m=1.10, L=0.95, rate=5.5), dict(m=0.90, L=0.70, rate=3.8)]


def brocade_breathe(take, r):
    """The open canopy breathing as it falls (descend 10 and 30, once a second): a slow soft billow of heavy cloth,
    0.70-0.95 s, a low band (60-650 Hz) with a shallow 4-6 Hz flutter, a few creases, a low air swell under it.
    Quiet by construction. It starts at -14 dB so its onset is on the key."""
    q = BREATHE[take - 1]
    m, L = q['m'], q['L']
    dur = round(L + 0.25, 3)
    b = buf(dur)
    t = tt(dur)
    sw = np.where(t < L, np.sin(np.pi * np.clip(t / L, 0, 1)) ** 2, 0.0)
    env = 0.20 * np.clip(t / 0.003, 0, 1) * np.clip(1 - t / 0.30, 0, 1) + 0.80 * sw
    b += 0.70 * brocade(r, dur, env, 60 * m, 650 * m, q['rate'], depth=0.5, grains=0.10, sheen=0.0,
                        spacing=(0.030, 0.080))
    b += 0.40 * norm(lowpass(noise(dur, r), 250) * env)
    return b


SPILL = [dict(m=1.00, hold=0.28, rate=17.0), dict(m=1.10, hold=0.24, rate=20.0), dict(m=0.92, hold=0.33, rate=15.0)]


def brocade_spill(take, r):
    """Steering (steer_l/r 3): the inner panel folding up and fluttering, spilling air. The panel flops over at 3 ms
    (a cloth slap and a small push of air), then flutters fast and deep (15-20 Hz) for 0.24-0.33 s while the spilled
    air hisses out through a band that falls from 900-2300 Hz to 400-1300 Hz."""
    q = SPILL[take - 1]
    m = q['m']
    dur = round(q['hold'] + 0.40, 3)
    b = buf(dur)
    t = tt(dur)
    place(b, cloth_slap(r, m, 0.015, 120, 1200), 0.003, 0.50)
    place(b, air_thump(r, 0.030, 500 * m), 0.003, 0.40)
    td, g = after(t, 0.003)
    env = g * np.clip(td / 0.02, 0, 1) * np.where(td < q['hold'], 1.0, np.exp(-(td - q['hold']) / 0.06))
    b += 0.65 * brocade(r, dur, env, 120 * m, 1600 * m, q['rate'], depth=0.85)
    k = np.exp(-td / 0.15)
    b += 0.22 * norm(sweep_bandpass(noise(dur, r), (400 + 500 * k) * m, (1300 + 1000 * k) * m) * env)
    return b


FLARE = [dict(m=1.00, tau=0.09, front=0.040), dict(m=1.10, tau=0.08, front=0.050), dict(m=0.90, tau=0.11, front=0.034)]


def brocade_flare(take, r):
    """The flare (flare 6): the back panel bellying full. A 'whoomp' of air filling the cloth at 3 ms (a low air
    thump and a soft sub), the brocade drawing taut with a slap, the front panel lifting with a smaller one 34-50 ms
    later, the cords taking the load (a short rope creak) and the cloth's flutter slowing from 10 to 6 Hz as it
    holds."""
    q = FLARE[take - 1]
    m = q['m']
    dur = 0.80
    b = buf(dur)
    t = tt(dur)
    place(b, air_thump(r, q['tau'], 380 * m), 0.003, 0.70)
    place(b, sweep_sine(75 * m, 45 * m, 0.08, 0.025, attack=0.003), 0.003, 0.25)
    place(b, cloth_slap(r, m, 0.010, 150, 1400), 0.003, 0.45)
    place(b, cloth_slap(r, m * 1.1, 0.010, 150, 1400), q['front'], 0.25)
    td, g = after(t, 0.003)
    env = g * np.clip(td / 0.01, 0, 1) * np.exp(-td / 0.15)
    b += 0.45 * brocade(r, dur, env, 90 * m, 1300 * m, 10.0 - 4.0 * np.clip(td / 0.3, 0, 1))
    place(b, creak(r, 0.10, 650 * m, rate=(0.012, 0.022), decay=0.005), 0.030, 0.10)
    return b


# ---------------------------------------------------------------- landing

FLUMP = [dict(m=1.00, tau=0.035, tick=0.040), dict(m=1.12, tau=0.030, tick=0.052), dict(m=0.88, tau=0.042, tick=0.034)]


def brocade_flump(take, r):
    """Heavy brocade falling onto something (land 8: the crown onto the swivel, the panels round the lid; land 26:
    flat on the ground): a low soft thud at 4 ms (a low air thump and a small thunk), the cloth slapping down, the
    weave crumpling for 60 ms, and a tassel's faint tick as it folds under."""
    q = FLUMP[take - 1]
    m = q['m']
    dur = 0.55
    b = buf(dur)
    t = tt(dur)
    h = 0.004
    place(b, air_thump(r, q['tau'], 350 * m, attack=0.002), h, 0.80)
    place(b, thunk(r, 110 * m, 70 * m, 0.035, 0.015), h, 0.30)
    place(b, cloth_slap(r, m, 0.012, 150, 1600), h, 0.55)
    td, g = after(t, h)
    b += 0.40 * brocade(r, dur, g * np.clip(td / 0.005, 0, 1) * np.exp(-td / 0.06), 100 * m, 1400 * m, 12.0,
                        grains=0.40)
    place(b, spar_tick(r, 3200 * m, decay=0.006), q['tick'], 0.05)
    return b


RUSTLE = [dict(m=1.00, L=0.22, rate=8.0), dict(m=1.12, L=0.18, rate=10.0), dict(m=0.90, L=0.26, rate=7.0)]


def brocade_rustle(take, r):
    """The canopy tilting on the jar before it is shrugged off (land 17): a short heavy rustle, 0.18-0.26 s, the
    brocade shifting with a slow flutter and a few creases, starting with a soft cloth touch at 3 ms."""
    q = RUSTLE[take - 1]
    m, L = q['m'], q['L']
    dur = round(L + 0.25, 3)
    b = buf(dur)
    t = tt(dur)
    place(b, cloth_slap(r, m, 0.008, 100, 900), 0.003, 0.40)
    td, g = after(t, 0.003)
    env = g * np.clip(td / 0.008, 0, 1) * np.where(td < L, 0.5 + 0.5 * np.sin(np.pi * np.clip(td / L, 0, 1)),
                                                   0.5 * np.exp(-(td - L) / 0.03))
    b += 0.75 * brocade(r, dur, env, 80 * m, 900 * m, q['rate'], grains=0.15, sheen=0.0)
    return b


SLIDE = [dict(m=1.00, L=0.28, rough=25.0), dict(m=1.12, L=0.32, rough=32.0), dict(m=0.90, L=0.25, rough=20.0)]


def brocade_slide(take, r):
    """The canopy sliding off the jar to the right (land 20, down by 26): the cloth catching at 3 ms, then 0.25-0.32 s
    of brocade dragging over brass and glass (a 250-2200 Hz friction band roughened at 20-32 Hz, fine creases every
    5-12 ms, the heavy cloth's body under it), swelling and easing off just before it lands."""
    q = SLIDE[take - 1]
    m, L = q['m'], q['L']
    dur = round(L + 0.25, 3)
    b = buf(dur)
    t = tt(dur)
    place(b, cloth_slap(r, m, 0.006, 200, 1500), 0.003, 0.40)
    td, g = after(t, 0.003)
    env = g * np.clip(td / 0.006, 0, 1) * np.where(td < L, 0.4 + 0.6 * np.sin(np.pi * np.clip(td / L, 0, 1)) ** 0.8,
                                                   0.4 * np.exp(-(td - L) / 0.03))
    rough = np.clip(0.4 + 0.6 * np.abs(lowpass(noise(dur, r), q['rough'])) / 0.3, 0, 2)
    b += 0.55 * norm(bandpass(noise(dur, r), 250 * m, 2200 * m) * env * rough)
    b += 0.40 * brocade(r, dur, env, 100 * m, 800 * m, 6.0, grains=0.35, sheen=0.02, spacing=(0.005, 0.012))
    return b


# ---------------------------------------------------------------- water

SLAP = [dict(m=1.00, drops=10), dict(m=1.12, drops=13), dict(m=0.82, drops=8)]


def brocade_slap(take, r):
    """The canopy slapping down flat on the water (water 9): the slap at 3 ms (a strike and a 200-3000 Hz burst), the
    water's low 'plap' (a sine falling 320 -> 140 Hz), the heavy cloth's push of air, a spray of 1.2-5 kHz noise, then
    8-13 droplets falling back (tiny bubbles at 0.7-1.8 kHz, thinning out to 0.32 s)."""
    q = SLAP[take - 1]
    m = q['m']
    dur = 0.85
    b = buf(dur)
    h = 0.003
    place(b, strike(r, 1200), h, 0.40)
    place(b, norm(noise_burst(200 * m, 3000 * m, 0.010, r, attack=0.0005)), h, 0.80)
    place(b, sweep_sine(320 * m, 140 * m, 0.035, 0.012, attack=0.001), h, 0.35)
    place(b, air_thump(r, 0.030, 600 * m, attack=0.002), h, 0.40)
    place(b, norm(noise_burst(1200 * m, 5000 * m, 0.10, r, attack=0.004)), 0.008, 0.20)
    for k in range(q['drops']):
        at = 0.03 + 0.29 * (k / q['drops']) ** 1.3 + r.uniform(-0.01, 0.01)
        place(b, bubble(r, r.uniform(700, 1800) * m, r.uniform(0.006, 0.012)), at, 0.18 * (1 - 0.6 * k / q['drops']))
    return b


SINK = [dict(m=1.00, glugs=((0.050, 300, 0.60), (0.190, 380, 0.50), (0.330, 260, 0.40))),
        dict(m=1.08, glugs=((0.060, 340, 0.60), (0.240, 280, 0.45))),
        dict(m=0.92, glugs=((0.045, 280, 0.60), (0.160, 350, 0.50), (0.270, 240, 0.42), (0.380, 310, 0.30)))]


def gold_sink(take, r):
    """Heavy brocade sinking (water 33, the slow sink): a wet cloth 'schlup' at 3 ms as it goes under (low-passed noise
    and a short burst), then the glug: two to four big bubbles (Minnaert tones at 240-380 Hz, each with a short gulp of
    low noise) 110-180 ms apart, smaller bubbles rising after, and the water closing over the cloth (a low swish
    fading over 0.5 s)."""
    q = SINK[take - 1]
    m = q['m']
    dur = 1.00
    b = buf(dur)
    t = tt(dur)
    place(b, air_thump(r, 0.050, 700 * m, attack=0.005), 0.003, 0.60)
    place(b, cloth_slap(r, m, 0.010, 300, 2500), 0.003, 0.30)
    for at, f, g in q['glugs']:
        place(b, bubble(r, f * m, 0.05, rise=0.25), at, g)
        place(b, norm(lowpass(noise(0.12, r), 600) * exp_env(0.12, 0.015, 0.003)), at, 0.20 * g)
    last = q['glugs'][-1][0]
    for k in range(6):
        at = last + 0.04 + 0.045 * k + r.uniform(-0.01, 0.01)
        place(b, bubble(r, r.uniform(700, 1400) * m, r.uniform(0.008, 0.015)), at, 0.12 * (1 - 0.12 * k))
    td, g = after(t, 0.003)
    b += 0.25 * norm(lowpass(noise(dur, r), 400) * g * np.clip(td / 0.03, 0, 1) * np.exp(-td / 0.18))
    return b


# ---------------------------------------------------------------- the streamer

FLAP = [[0.003, 0.085, 0.160, 0.255, 0.330, 0.420, 0.500], [0.003, 0.070, 0.150, 0.215, 0.310, 0.385, 0.470, 0.540],
        [0.003, 0.100, 0.185, 0.290, 0.370, 0.465]]


def brocade_flap(take, r):
    """The tangled streamer (streamer 0, its 12-tick loop): the line-over bundle flapping violently, six to eight
    hard cloth cracks 65-105 ms apart (a short burst with a push of air under it, each a different weight) over a
    continuous deep flutter at 11 Hz with air tearing past, for 0.55 s, so the next play picks it up."""
    cracks = FLAP[take - 1]
    m = [1.00, 1.10, 0.90][take - 1]
    dur = 0.95
    b = buf(dur)
    t = tt(dur)
    L = 0.56
    env = np.clip(t / 0.004, 0, 1) * np.where(t < L, 0.6 + 0.4 * np.sin(np.pi * np.clip(t / L, 0, 1)),
                                              0.6 * np.exp(-(t - L) / 0.05))
    b += 0.50 * brocade(r, dur, env, 100 * m, 1400 * m, 11.0, depth=0.9, grains=0.25)
    b += 0.15 * norm(bandpass(noise(dur, r), 300 * m, 1800 * m) * env)
    for k, at in enumerate(cracks):
        g = r.uniform(0.6, 1.0) if k else 1.0
        place(b, norm(noise_burst(150 * m, 1800 * m, 0.007, r, attack=0.0005)), at, 0.55 * g)
        place(b, air_thump(r, 0.020, 700 * m, attack=0.001), at, 0.30 * g)
    return b


# ---------------------------------------------------------------- tassels and cords

JINGLE = [[(0.003, 3600, 1.0), (0.024, 4300, 0.7), (0.051, 3100, 0.8), (0.083, 3900, 0.5), (0.120, 4600, 0.4),
           (0.165, 3400, 0.3)],
          [(0.003, 3200, 1.0), (0.018, 3800, 0.8), (0.040, 4500, 0.6), (0.066, 2900, 0.6), (0.098, 3500, 0.45),
           (0.130, 4100, 0.35), (0.170, 3300, 0.3), (0.215, 3700, 0.2)],
          [(0.003, 4000, 1.0), (0.035, 3300, 0.7), (0.080, 4400, 0.5), (0.128, 3600, 0.35)]]


def tassel_jingle(take, r):
    """The brass tassels jingling (deploy 26 after the snap; land 12 as the canopy settles round the lid): the small
    brass tassel caps knocking together, four to eight tinks 15-50 ms apart, dying away over 0.13-0.22 s (short
    inharmonic brass modes at 2.9-4.6 kHz, ratios 1 : 2.32 : 4.25), with the silk threads' soft swish."""
    b = buf(0.55)
    for at, f, g in JINGLE[take - 1]:
        x = modal(f * r.uniform(0.98, 1.02), [1, 2.32, 4.25, 6.63], [1, 0.5, 0.3, 0.15], [0.030, 0.018, 0.010, 0.007], r)
        place(b, norm(x), at, g)
    place(b, norm(noise_burst(1500, 6000, 0.050, r, attack=0.010)), 0.006, 0.12)
    return b


CREAK = [dict(m=1.00, dur=0.20, rough=40.0), dict(m=1.12, dur=0.16, rough=52.0), dict(m=0.90, dur=0.25, rough=34.0)]


def cords_creak(take, r):
    """The white cords taking the load in the flare's wind-up (flare 2): the swivel's brass fitting ticking at 3 ms,
    then the rope stretching over it, stick-slip creaks at about 520 Hz every 10-22 ms for 0.16-0.25 s, with a
    fibrous 1-3.5 kHz rasp roughened at 34-52 Hz."""
    q = CREAK[take - 1]
    m = q['m']
    dur = round(q['dur'] + 0.30, 3)
    b = buf(dur)
    t = tt(dur)
    place(b, spar_tick(r, 1900 * m, decay=0.006), 0.003, 0.25)
    place(b, creak(r, q['dur'], 520 * m, rate=(0.010, 0.022), decay=0.006, rise=0.10), 0.005, 0.60)
    td, g = after(t, 0.005)
    env = g * np.where(td < q['dur'], np.sin(np.pi * np.clip(td / q['dur'], 0, 1)) ** 0.7, 0.0)
    rough = np.clip(0.4 + 0.6 * np.abs(lowpass(noise(dur, r), q['rough'])) / 0.3, 0, 2)
    b += 0.20 * norm(bandpass(noise(dur, r), 1000 * m, 3500 * m) * env * rough)
    return b


SLACK = [dict(m=1.00, tau=0.060), dict(m=1.12, tau=0.050), dict(m=0.90, tau=0.075)]


def cords_slack(take, r):
    """The cords going slack on landing (land 1): the swivel's ring clicking as the load comes off (3 ms), the cords
    falling loose (a soft push of air and a swish through 400-1800 Hz, 50-75 ms decay) and the tension's faint
    twang down (420 -> 300 Hz)."""
    q = SLACK[take - 1]
    m = q['m']
    dur = 0.50
    b = buf(dur)
    t = tt(dur)
    place(b, spar_tick(r, 1800 * m, decay=0.008), 0.003, 0.22)
    place(b, air_thump(r, 0.025, 700 * m, attack=0.002), 0.003, 0.45)
    td, g = after(t, 0.010)
    b += 0.32 * norm(bandpass(noise(dur, r), 400 * m, 1800 * m) * g * np.clip(td / 0.02, 0, 1) * np.exp(-td / q['tau']))
    place(b, sweep_sine(420 * m, 300 * m, 0.030, 0.02), 0.006, 0.10)
    return b


# ---------------------------------------------------------------- references (measured as the game plays them)

WOOL_BREAK = dict(event='block.wool.break', file='dig__cloth1.ogg', vol=1.0, pitch=0.8)
SPLASH = dict(event='entity.generic.splash', file='liquid__splash.ogg', vol=1.0, pitch=1.0)
BOTTLE_FILL = dict(event='item.bottle.fill', file='item__bottle__fill1.ogg', vol=1.0, pitch=1.0)

BASE = dict(category='NEUTRAL', attenuation=16, fixed_range=False, volume=1.0)
KEY_NOTE = (' Keyframe volume 1.0 and pitch 1.0: the file carries the loudness (the clip\'s proposed volumes, '
            '0.2-0.5, were standing in for it).')


def ev(name, **kw):
    d = dict(BASE)
    d.update(id=f'mecha.{name}', file=f'mecha/parachute/{name}')
    d.update(kw)
    return d


SOUNDS = [
    # ---- pack and pilot
    ev('envelope_pop', fn=envelope_pop, takes=3, target=-25.0, cls='cloth snap (press stud)', kind='latch',
       fade_ms=15, hit_ms=3, ref=ELYTRA_EQUIP, pitch=[0.95, 1.05],
       subtitle_en='Parachute pack pops', subtitle_pt='Mochila do paraquedas abre',
       play='deploy tick 10 (the flap pops; it is torn away on 13).' + KEY_NOTE),
    ev('pilot_whoosh', fn=pilot_whoosh, takes=3, target=-25.0, cls='air (small whoosh)', kind=('whoosh', 0.30),
       fade_ms=25, hit_ms=3, ref=ELYTRA_EQUIP, pitch=[0.95, 1.05], subtitle_en=None, subtitle_pt=None,
       play='deploy tick 12 (the pilot chute shoots out, 11-15); it catches the air at 190-260 ms. No subtitle (envelope_pop carries it).' + KEY_NOTE),
    # ---- in the air
    ev('brocade_stream', fn=brocade_stream, takes=3, target=-26.0, cls='cloth (streaming flutter)',
       kind=('whoosh', 0.32), fade_ms=20, hit_ms=3, ref=ELYTRA_EQUIP, pitch=[0.96, 1.04],
       subtitle_en='Canopy streams out', subtitle_pt='Velame sai ondulando',
       play='deploy tick 14: swells to 270-330 ms and stops quickly, so it hands over to mecha.parachute_pop on chute:snap (tick 20, 300 ms after the key).' + KEY_NOTE),
    ev('brocade_breathe', fn=brocade_breathe, takes=3, target=-30.5, cls='cloth (breath, quiet)', kind='hiss',
       fade_ms=40, hit_ms=3, ref=WOOL_BREAK, pitch=[0.95, 1.05], subtitle_en=None, subtitle_pt=None,
       play='descend ticks 10 and 30 (once a second while it falls). Quiet by construction. No subtitle (small life).' + KEY_NOTE),
    ev('brocade_spill', fn=brocade_spill, takes=3, target=-27.5, cls='cloth (spilling air)', kind=('whoosh', 0.40),
       fade_ms=30, hit_ms=3, ref=ELYTRA_EQUIP, pitch=[0.95, 1.05],
       subtitle_en='Canopy spills air', subtitle_pt='Velame solta ar',
       play='steer_l / steer_r tick 3 (the inner panel folds up and flutters).' + KEY_NOTE),
    ev('brocade_flare', fn=brocade_flare, takes=3, target=-25.0, cls='cloth (filling)', kind=('whoosh', 0.40),
       fade_ms=30, hit_ms=3, ref=ELYTRA_EQUIP, pitch=[0.95, 1.05],
       subtitle_en='Canopy flares', subtitle_pt='Velame se enche',
       play='flare tick 6 (the back panel bellies); cords_creak plays on 2, in the wind-up.' + KEY_NOTE),
    # ---- landing
    ev('brocade_flump', fn=brocade_flump, takes=3, target=-26.5, cls='cloth (heavy fall)', kind='latch', fade_ms=15,
       hit_ms=4, ref=ELYTRA_EQUIP, pitch=[0.95, 1.05],
       subtitle_en='Canopy flumps down', subtitle_pt='Velame desaba',
       play='land tick 8 (the crown lands on the swivel) and land tick 26 (it comes down flat beside the jar), a different take each.' + KEY_NOTE),
    ev('brocade_rustle', fn=brocade_rustle, takes=3, target=-29.0, cls='cloth (rustle)', kind='latch', fade_ms=20,
       hit_ms=3, ref=WOOL_BREAK, pitch=[0.95, 1.05], subtitle_en=None, subtitle_pt=None,
       play='land tick 17 (the tilt before it is shrugged off). No subtitle (part of the landing).' + KEY_NOTE),
    ev('brocade_slide', fn=brocade_slide, takes=3, target=-29.0, cls='cloth (sliding)', kind=('whoosh', 0.28),
       fade_ms=20, hit_ms=3, ref=EQUIP_GENERIC, pitch=[0.95, 1.05],
       subtitle_en='Canopy slides off', subtitle_pt='Velame escorrega',
       play='land tick 20 (it slides off to the right; brocade_flump lands it on 26, 300 ms later).' + KEY_NOTE),
    # ---- water
    ev('brocade_slap', fn=brocade_slap, takes=3, target=-23.0, cls='splash (cloth on water)', kind=('whoosh', 0.30),
       fade_ms=30, hit_ms=3, ref=SPLASH, pitch=[0.95, 1.05], attenuation=20,
       subtitle_en='Canopy slaps water', subtitle_pt='Velame bate na água',
       play='water tick 9 (it lies down flat on the surface).' + KEY_NOTE),
    ev('gold_sink', fn=gold_sink, takes=3, target=-24.0, cls='glug', kind='glug', fade_ms=40, hit_ms=3,
       ref=BOTTLE_FILL, pitch=[0.95, 1.05], subtitle_en='Canopy sinks', subtitle_pt='Velame afunda',
       play='water tick 33 (it sinks, as gold brocade would): the glugs at 45-380 ms by take; the clip\'s bubbles on 40 and 52 come after it.' + KEY_NOTE),
    # ---- the streamer
    ev('brocade_flap', fn=brocade_flap, takes=3, target=-25.0, cls='cloth (violent flapping)', kind=('whoosh', 0.55),
       fade_ms=30, hit_ms=3, ref=ELYTRA_EQUIP, pitch=[0.95, 1.05], attenuation=20,
       subtitle_en='Canopy flaps, tangled', subtitle_pt='Velame embolado se debate',
       play='streamer tick 0, so once per 12-tick loop (0.6 s); heard about 0.6 s, so the plays join. Random take and pitch so the joins do not repeat.' + KEY_NOTE),
    # ---- tassels and cords
    ev('tassel_jingle', fn=tassel_jingle, takes=3, target=-29.5, cls='small brass jingle', kind='latch', fade_ms=15,
       hit_ms=3, ref=EQUIP_GOLD, pitch=[0.94, 1.06], subtitle_en='Tassels jingle', subtitle_pt='Borlas tilintam',
       play='deploy tick 26 (after the snap) and land tick 12.' + KEY_NOTE),
    ev('cords_creak', fn=cords_creak, takes=3, target=-29.5, cls='rope creak', kind='latch', fade_ms=15, hit_ms=3,
       ref=EQUIP_GENERIC, pitch=[0.95, 1.05], subtitle_en=None, subtitle_pt=None,
       play='flare tick 2 (the wind-up dip). No subtitle (brocade_flare carries it).' + KEY_NOTE),
    ev('cords_slack', fn=cords_slack, takes=3, target=-30.5, cls='rope (going slack)', kind='latch', fade_ms=15,
       hit_ms=3, ref=EQUIP_GENERIC, pitch=[0.95, 1.05], subtitle_en=None, subtitle_pt=None,
       play='land tick 1 (the cords go slack on contact, after mecha.parachute_fold on 0). No subtitle.' + KEY_NOTE),
]

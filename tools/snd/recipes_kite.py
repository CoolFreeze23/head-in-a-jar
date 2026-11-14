"""The Kite: Noggin & Sons' air courier, a brass-and-enamel ornithopter on long sparred wings and bird legs.

Its wings are machines, not feathers: a brass leading spar, enamel-stiffened membrane between dark ribs, driven from
brass root drums by servos. So a down-stroke is a soft thump of air with the membrane snapping taut and a light spar
creak, never a bird's flap; a spread or a fold is a small servo whining over a rustle of membrane, ending in a taut
'thwap' or a soft seat. The jar hangs under the body in a lantern whose floor is a lift on cables: catches, a small
winch, a pneumatic blow for the eject. Three rubber-and-steel talons grip a creature for the Talon Lift.

Event ids are `mecha.kite.<name>`; the code plays one when it exists and the shared `mecha.<name>` otherwise. The
names and their ticks come from the Kite's clips (`art/mecha/kite/kite.bbmodel`). Keyframe sounds hit at 0-5 ms;
the three server-played ones (talon_close, talon_open, floor_blow) hit at 3 ms on their clip tick. Takes differ by
construction (per-take tables).

Shared parts come from the kit (`nsynth`), the Bulwark's joint creak (`recipes_bulwark.creak`) and the Hopper's
reference definitions (`recipes_mecha`).
"""
import numpy as np
from nsynth import *
from recipes_bulwark import creak
from recipes_mecha import PISTON_IN, PISTON_OUT, TRAPDOOR_CLOSE, DOOR_OPEN

T = TICK


# ---------------------------------------------------------------- shared parts

def smooth(n, r, fc=4.0):
    """Slowly wandering noise, -1..1, for drifting rates and gusts."""
    x = lowpass(r.standard_normal(n + 4410), fc, 2)[4410:]  # drop the filter's start-up
    return x / max(np.max(np.abs(x)), 1e-12)


def flutter(n, rate, r, depth=0.6, power=2.0, jitter=0.15):
    """Amplitude flutter of a membrane or a cloth: (1 - depth) + depth |sin|^power, one bump per cycle of a rate
    (Hz, scalar or per sample) that wanders by +-jitter."""
    rate = np.broadcast_to(np.asarray(rate, dtype=float), (n,)) * (1 + jitter * smooth(n, r, 5.0))
    ph = np.cumsum(rate) / SR
    return (1 - depth) + depth * np.abs(np.sin(np.pi * ph + r.uniform(0, np.pi))) ** power


def membrane(r, dur, env, lo, hi, rate, depth=0.65, crisp=0.18):
    """The wing's enamel-stiffened membrane: band-passed noise under a flutter, and a little crisp 2.5-7 kHz crackle
    that follows the flutter harder (the enamel). env: per-sample envelope."""
    n = int(round(dur * SR))
    f = flutter(n, rate, r, depth)
    body = bandpass(noise(dur, r), lo, hi) * f * env
    cr = bandpass(noise(dur, r), 2500, 7000) * f ** 3 * env
    return norm(body) + crisp * norm(cr)


def air_thump(r, tau, fc, attack=0.004):
    """A push of air: low-passed noise with a 4 ms rise and an exponential fall, 7 decay times long."""
    d = 7 * tau + attack + 0.01
    t = tt(d)
    return norm(lowpass(noise(d, r), fc, 2) * np.clip(t / attack, 0, 1) * np.exp(-t / tau))


def spar_tick(r, f, level=1.0, decay=0.010):
    """The brass spar or root drum knocking once: short brass modes."""
    return level * norm(modal(f, [1, 2.4, 3.9], [1, 0.4, 0.2], [decay, decay * 0.65, decay * 0.4], r))


def after(t, at):
    """Time since `at`, zero before it, and a 0/1 gate."""
    return np.maximum(t - at, 0.0), (t >= at)


# ---------------------------------------------------------------- the wingbeat

def beat(r, m, tau, fl, rush, cr_at, dur, thump=0.75, sub=0.28, snap=0.45, rush_g=0.40, creak_g=0.10,
         creak_f=1150.0, root_g=0.12, fc=520.0):
    """One down-stroke, hit at 3 ms: the air thump (low-passed noise, a soft sub whump under it), the membrane snapping
    taut and fluttering out at `fl` Hz, the air rushing off the trailing edge (a band falling from 430-2500 Hz to
    180-900 Hz), the spar's light creak at `cr_at` and the root drum's small knock."""
    h = 0.003
    b = buf(dur)
    t = tt(dur)
    place(b, air_thump(r, tau, fc * m), h, thump)
    place(b, sweep_sine(85 * m, 48 * m, tau * 1.2, 0.02, attack=0.003), h, sub)
    td, g = after(t, h)
    env = g * np.clip(td / 0.002, 0, 1) * np.exp(-td / 0.045)
    b += snap * membrane(r, dur, env, 280 * m, 2600 * m, fl) / 1.18
    renv = g * np.clip(td / 0.015, 0, 1) * np.exp(-td / rush)
    k = np.exp(-td / 0.08)
    b += rush_g * norm(sweep_bandpass(noise(dur, r), (180 + 250 * k) * m, (900 + 1600 * k) * m) * renv)
    place(b, creak(r, 0.07, creak_f * m, rate=(0.012, 0.022), decay=0.004), cr_at, creak_g)
    place(b, spar_tick(r, 880 * m), h, root_g)
    return b


WB = [dict(m=1.00, tau=0.050, fl=34.0, rush=0.075, cr=0.030), dict(m=1.08, tau=0.045, fl=38.0, rush=0.085, cr=0.042),
      dict(m=0.92, tau=0.056, fl=30.0, rush=0.085, cr=0.024), dict(m=1.16, tau=0.042, fl=42.0, rush=0.120, cr=0.050)]


def wingbeat(take, r):
    """The flap's down-stroke (flap tick 6, near the bottom of the 7-tick stroke; takeoff 10): a soft air thump with a
    sub whump, the membrane snapping taut and fluttering at 30-42 Hz, the air rushing off, a light spar creak 24-50 ms
    on. Heard about 0.3 s, so a beat every 0.44 s (playback 1.8) does not pile up."""
    q = WB[take - 1]
    return beat(r, q['m'], q['tau'], q['fl'], q['rush'], q['cr'], 0.55)


WBL = [dict(m=1.15, tau=0.030, fl=44.0, rush=0.045, cr=0.022), dict(m=1.26, tau=0.026, fl=50.0, rush=0.040, cr=0.030),
       dict(m=1.00, tau=0.036, fl=40.0, rush=0.062, cr=0.018)]


def wingbeat_light(take, r):
    """A shallow hover beat (hover tick 4, every 0.6 s; talon_drop 3): the same stroke smaller and higher, a quicker
    thump and rush, the membrane flutter faster (40-50 Hz), less creak."""
    q = WBL[take - 1]
    return beat(r, q['m'], q['tau'], q['fl'], q['rush'], q['cr'], 0.40, thump=0.70, sub=0.18, snap=0.50,
                rush_g=0.38, creak_g=0.07)


WBH = [dict(m=0.86, tau=0.075, fl=26.0, rush=0.11, cr=(0.035, 0.150)),
       dict(m=0.80, tau=0.085, fl=23.0, rush=0.12, cr=(0.045, 0.180)),
       dict(m=0.92, tau=0.068, fl=29.0, rush=0.12, cr=(0.030, 0.130))]


def wingbeat_heavy(take, r):
    """A full stroke lifting a load (talon_grab 12, the heave; talon_carry 8, once a second): a deeper, longer thump
    and whump, the membrane straining at 23-29 Hz, a longer rush, the spar creaking twice under the load (lower, at
    35-45 ms and 130-180 ms) and the root drum's knock."""
    q = WBH[take - 1]
    m = q['m']
    b = beat(r, m, q['tau'], q['fl'], q['rush'], q['cr'][0], 0.75, thump=0.80, sub=0.36, snap=0.42, rush_g=0.42,
             creak_g=0.14, creak_f=900.0, root_g=0.16, fc=460.0)
    place(b, creak(r, 0.10, 820 * m, rate=(0.016, 0.028), decay=0.005, rise=-0.1), q['cr'][1], 0.10)
    return b


WBW = [dict(m=1.00, tau=0.040, fl=19.0, sag=0.22), dict(m=0.90, tau=0.046, fl=16.0, sag=0.28),
       dict(m=1.10, tau=0.036, fl=22.0, sag=0.18)]


def wingbeat_weak(take, r):
    """A weak half-beat on a flat battery (stall 4 and 14): the drive servo whining down as it gives out (a small
    motor falling from half speed over 0.18-0.28 s), a feeble thump, the membrane flapping loose and slow (16-22 Hz,
    deep flutter), the spar creaking as it sags (falling pitch)."""
    q = WBW[take - 1]
    m = q['m']
    dur = 0.60
    b = buf(dur)
    t = tt(dur)
    s = 0.55 * np.clip(1 - (t - 0.003) / q['sag'], 0, 1) ** 1.3 * (t > 0.003)
    b += 0.55 * motor(t, s, r, 240 * m, 420 * m, grit=0.30, air=0.10, whine=0.30, rotor_div=6)
    place(b, air_thump(r, q['tau'], 420 * m), 0.003, 0.55)
    td, g = after(t, 0.003)
    env = g * np.clip(td / 0.010, 0, 1) * np.exp(-td / 0.09)
    b += 0.40 * membrane(r, dur, env, 220 * m, 1800 * m, q['fl'], depth=0.85, crisp=0.10) / 1.1
    place(b, creak(r, 0.14, 980 * m, rate=(0.018, 0.032), decay=0.005, rise=-0.25), 0.030, 0.12)
    place(b, spar_tick(r, 820 * m), 0.003, 0.10)
    return b


# ---------------------------------------------------------------- spreading, folding, braking

UNF = [dict(m=1.00, mv=5.0, teeth=(0.080, 0.150)), dict(m=1.10, mv=4.5, teeth=(0.065, 0.120, 0.170)),
       dict(m=0.92, mv=5.5, teeth=(0.100, 0.190))]


def wing_unfurl(take, r):
    """The wings spreading on their root servos (boot 10, takeoff 2, idle_tic 9, build 168): a relay click at 3 ms, the
    servo's whine (320 + 640 s Hz) under a sin^2 move of 4.5-5.5 ticks, the membrane rustling open with the speed, the
    joints clicking out (two or three teeth), then at full spread the membrane snapping taut (a soft 'thwap'), the
    root lock's tooth and a short brass ring."""
    q = UNF[take - 1]
    m = q['m']
    start = 0.006
    stop = start + q['mv'] * T
    dur = round(stop + 0.40, 3)
    b = buf(dur)
    t = tt(dur)
    s = speed_curve(t, start, stop)
    b += 0.75 * motor(t, s, r, 320 * m, 640 * m, grit=0.30, air=0.10, whine=0.30, rotor_div=6)
    b += 0.50 * membrane(r, dur, s ** 1.2, 350 * m, 3200 * m, 20 * m + 12 * s) / 1.18
    place(b, relay_click(r, 1900 * m), 0.003, 0.25)
    for at in q['teeth']:
        place(b, tooth(r, 2300 * m), at, 0.14)
    place(b, norm(noise_burst(220 * m, 2200 * m, 0.012, r, attack=0.0008)), stop, 0.55)
    place(b, thunk(r, 140 * m, 90 * m, 0.030, 0.012), stop, 0.22)
    place(b, tooth(r, 2600 * m), stop + 0.004, 0.22)
    place(b, spar_tick(r, 1250 * m, decay=0.020), stop + 0.004, 0.15)
    td, g = after(t, stop)
    b += 0.16 * membrane(r, dur, g * np.exp(-td / 0.05), 300 * m, 2500 * m, 40.0) / 1.18
    return b


FOLD = [dict(m=1.00, mv=6.0, seat=0.045), dict(m=1.10, mv=5.0, seat=0.055), dict(m=0.90, mv=7.0, seat=0.038)]


def wing_fold(take, r):
    """The wings folding back along the body (boot 28, land 10, land_heavy 10, vent 86, idle_tic 24, build 186): a
    relay click, the servo whining down (300 + 560 s Hz) over 5-7 ticks, the membrane crumpling as it doubles over
    (a lower rustle, slower flutter, crease grains), the wing seating with a soft knock and the fold catch's tick
    38-55 ms after."""
    q = FOLD[take - 1]
    m = q['m']
    start = 0.006
    stop = start + q['mv'] * T
    dur = round(stop + 0.40, 3)
    b = buf(dur)
    t = tt(dur)
    s = speed_curve(t, start, stop)
    b += 0.70 * motor(t, s, r, 300 * m, 560 * m, grit=0.28, air=0.10, whine=0.28, rotor_div=6)
    b += 0.55 * membrane(r, dur, s ** 0.9, 260 * m, 2400 * m, 14 * m + 8 * s, depth=0.75) / 1.18
    at = start + 0.04
    while at < stop - 0.03:  # creases as the membrane doubles over
        place(b, norm(noise_burst(1200 * m, 4500 * m, 0.003, r, attack=0.0004)), at,
              0.12 * float(np.interp(at, t, s)) ** 0.5 * r.uniform(0.5, 1.0))
        at += r.uniform(0.018, 0.040)
    place(b, relay_click(r, 1700 * m), 0.003, 0.25)
    place(b, thunk(r, 150 * m, 100 * m, 0.028, 0.012), stop, 0.30)
    place(b, norm(noise_burst(300 * m, 2000 * m, 0.015, r, attack=0.002)), stop, 0.30)
    place(b, spar_tick(r, 1050 * m, decay=0.012), stop, 0.18)
    place(b, tooth(r, 2400 * m), stop + q['seat'], 0.18)
    return b


BRAKE = [dict(m=1.00, L=0.30, tau=0.11, fl=24.0), dict(m=0.80, L=0.34, tau=0.115, fl=21.0),
         dict(m=1.10, L=0.26, tau=0.10, fl=27.0)]


def wing_brake(take, r):
    """The flare's big cupped braking stroke (flare 7): the cupped wings catching the air at 3 ms (the membrane
    filling with a 'whoomph': a low thump and sub whump, the membrane slapping taut), then a long push of air through
    a band that opens from 100-400 Hz to 350-1500 Hz and closes again over 0.26-0.36 s, the membrane fluttering under
    load (21-27 Hz), the spars creaking hard and a servo straining under it."""
    q = BRAKE[take - 1]
    m, L = q['m'], q['L']
    dur = round(L + 0.50, 3)
    b = buf(dur)
    t = tt(dur)
    td, g = after(t, 0.003)
    push = g * np.where(td < 0.05, 0.55 + 0.45 * td / 0.05, np.exp(-np.maximum(td - 0.05, 0) / q['tau']))
    sw = np.where(td < L, np.sin(np.pi * np.clip(td / L, 0, 1)), 0.0)
    air = sweep_bandpass(noise(dur, r), (100 + 250 * sw) * m, (400 + 1100 * sw) * m) * push
    b += 0.75 * norm(air)
    b += 0.30 * norm(lowpass(noise(dur, r), 220) * push ** 1.5)
    place(b, air_thump(r, 0.07, 480 * m), 0.003, 0.65)
    place(b, sweep_sine(80 * m, 42 * m, 0.08, 0.025, attack=0.003), 0.003, 0.30)
    place(b, norm(noise_burst(250 * m, 2500 * m, 0.010, r, attack=0.0006)), 0.003, 0.50)
    env = g * np.clip(td / 0.004, 0, 1) * np.exp(-td / (0.6 * L))
    b += 0.32 * membrane(r, dur, env, 260 * m, 2400 * m, q['fl']) / 1.18
    s = 0.6 * sw
    b += 0.25 * motor(t, s, r, 260 * m, 300 * m, grit=0.25, air=0.08, whine=0.30, rotor_div=6)
    place(b, creak(r, 0.6 * L, 760 * m, rate=(0.014, 0.026), decay=0.005), 0.040, 0.16)
    place(b, spar_tick(r, 900 * m, decay=0.014), 0.003, 0.14)
    return b


SHRUG = [dict(m=1.00, gap=0.045, drop=0.33), dict(m=1.10, gap=0.060, drop=0.36), dict(m=0.92, gap=0.035, drop=0.30)]


def wing_shrug(take, r):
    """The refusal's lopsided shrug (talon_refused 15: left wing up 25 degrees, right 10, held 6 ticks): the left
    servo's quick rising 'zwip' at 3 ms, the right's smaller one 35-60 ms later, each with a little membrane rustle;
    then both let down a little sheepishly 0.30-0.36 s in (two short falling whines and a soft membrane flop)."""
    q = SHRUG[take - 1]
    m = q['m']
    dur = round(q['drop'] + 0.45, 3)
    b = buf(dur)
    t = tt(dur)
    for at, size, ff in ((0.003, 1.0, 1.0), (0.003 + q['gap'], 0.55, 1.08)):
        s = size * speed_curve(t, at, at + 0.10 * (0.6 + 0.4 * size))
        b += 0.55 * motor(t, s, r, 360 * m * ff, 520 * m * ff, grit=0.25, air=0.08, whine=0.32,
                          rotor_div=6)
        b += 0.30 * size * membrane(r, dur, s, 380 * m, 3000 * m, 26.0) / 1.18
        place(b, tooth(r, 2500 * m * ff), at, 0.16 * size)
        place(b, spar_tick(r, 1200 * m * ff, decay=0.014), at + 0.10 * (0.6 + 0.4 * size), 0.10 * size)
    for at, size, ff in ((q['drop'], 1.0, 1.0), (q['drop'] + 0.025, 0.6, 1.08)):
        u = np.clip((t - at) / 0.12, 0, 1)
        s = size * 0.7 * np.where((t > at) & (t < at + 0.12), np.sin(np.pi * u), 0.0)
        b += 0.40 * motor(t, s * (1 - 0.5 * u), r, 300 * m * ff, 260 * m * ff, grit=0.25, air=0.08, whine=0.30,
                          rotor_div=6)
        place(b, norm(noise_burst(250 * m, 1800 * m, 0.020, r, attack=0.004)), at + 0.10, 0.16 * size)
    place(b, spar_tick(r, 1300 * m), q['drop'] + 0.11, 0.06)
    return b


# ---------------------------------------------------------------- ground and life

SCRAPE = [dict(m=1.00, L=0.40, rough=44.0, catches=(0.12, 0.27)), dict(m=1.15, L=0.46, rough=58.0, catches=(0.09, 0.21, 0.36)),
          dict(m=0.90, L=0.36, rough=36.0, catches=(0.16,))]


def wing_scrape(take, r):
    """The limp's half-open left wing dragging its tip (limp 16): the brass spar tip catching the ground at 3 ms (a
    strike and a tooth), then scraping along for 0.36-0.46 s (band-passed noise 400-2800 Hz, and a low 150-700 Hz drag, roughened at 36-58 Hz,
    swelling and fading), stick-slip ticks where it catches, and the membrane hissing a little behind it."""
    q = SCRAPE[take - 1]
    m, L = q['m'], q['L']
    dur = round(L + 0.30, 3)
    b = buf(dur)
    t = tt(dur)
    td, g = after(t, 0.003)
    env = g * np.where(td < L, 0.45 + 0.55 * np.sin(np.pi * np.clip(td / L, 0, 1)) ** 0.7,
                       0.45 * np.exp(-(td - L) / 0.03)) * np.clip(td / 0.004, 0, 1)
    rough = np.clip(0.4 + 0.6 * np.abs(lowpass(noise(dur, r), q['rough'])) / 0.3, 0, 2)
    b += 0.55 * norm(bandpass(noise(dur, r), 400 * m, 2800 * m) * env * rough)
    b += 0.25 * norm(bandpass(noise(dur, r), 150 * m, 700 * m) * env * rough)
    b += 0.15 * membrane(r, dur, env, 300 * m, 2200 * m, 18.0) / 1.18
    place(b, strike(r, 1800 * m), 0.003, 0.35)
    place(b, tooth(r, 2100 * m), 0.003, 0.30)
    place(b, spar_tick(r, 1400 * m), 0.003, 0.20)
    for at in q['catches']:
        place(b, tooth(r, 1900 * m * r.uniform(0.92, 1.08)), at, 0.22)
        place(b, spar_tick(r, 1500 * m, decay=0.006), at, 0.10)
    return b


SLUMP = [dict(m=1.00, hits=(0.200, 0.245)), dict(m=0.90, hits=(0.215, 0.275)), dict(m=1.10, hits=(0.185, 0.215))]


def wing_slump(take, r):
    """The wings dropping limp as the machine dies (destroyed 12; they fall 3-16): the root servos' brakes letting go
    (two teeth at 3 and 12 ms), the membranes falling through the air (a low swoop, its band falling, 0.2 s) and
    flapping loose, then the two spars hitting the ground left and right 185-275 ms in (a brass thud each: a thunk, a
    700 Hz spar ring, the membrane slapping down, a little ground debris), and a last loose rustle."""
    q = SLUMP[take - 1]
    m = q['m']
    dur = 0.95
    b = buf(dur)
    t = tt(dur)
    place(b, tooth(r, 2200 * m), 0.003, 0.35)
    place(b, tooth(r, 2050 * m), 0.012, 0.28)
    place(b, strike(r, 1600 * m), 0.003, 0.20)
    td, g = after(t, 0.003)
    T1 = q['hits'][0]
    fall = g * np.where(td < T1, np.clip(td / 0.03, 0.3, 1) * (0.5 + 0.5 * td / T1), np.exp(-(td - T1) / 0.03))
    k = np.clip(td / T1, 0, 1)
    b += 0.45 * norm(sweep_bandpass(noise(dur, r), (300 - 150 * k) * m, (1800 - 1000 * k) * m) * fall)
    b += 0.25 * membrane(r, dur, fall, 250 * m, 2200 * m, 15.0, depth=0.85) / 1.18
    for k2, at in enumerate(q['hits']):
        gk = 1.0 - 0.2 * k2
        place(b, thunk(r, 130 * m, 70 * m, 0.045, 0.018), at, 0.55 * gk)
        place(b, spar_tick(r, 700 * m * (1 + 0.06 * k2), decay=0.030), at, 0.40 * gk)
        place(b, norm(noise_burst(200 * m, 2000 * m, 0.018, r, attack=0.0008)), at, 0.45 * gk)
        place(b, norm(lowpass(noise(0.3, r), 1800) * exp_env(0.3, 0.030, 0.002)), at, 0.20 * gk)
    td2, g2 = after(t, q['hits'][1] + 0.03)
    b += 0.10 * membrane(r, dur, g2 * np.clip(td2 / 0.03, 0, 1) * np.exp(-td2 / 0.08), 250 * m, 2000 * m, 12.0) / 1.18
    return b


RUFFLE = [dict(m=1.00, shakes=(0.003, 0.075, 0.140), rate=26.0), dict(m=1.10, shakes=(0.003, 0.065), rate=30.0),
          dict(m=0.92, shakes=(0.003, 0.085, 0.160, 0.225), rate=23.0)]


def feather_ruffle(take, r):
    """A preening shake (parked 42, idle_tic 11 and 15): the half-open wing shaken two to four times 65-85 ms apart,
    each shake a burst of membrane flutter (23-30 Hz, deep) with the brass ribs rattling (small ticks at 1.6-2.6 kHz)
    and a twitch of the root servo; dying away."""
    q = RUFFLE[take - 1]
    m = q['m']
    dur = round(q['shakes'][-1] + 0.35, 3)
    b = buf(dur)
    t = tt(dur)
    env = np.zeros(len(t))
    s = np.zeros(len(t))
    for k, at in enumerate(q['shakes']):
        gk = 0.82 ** k
        td, g = after(t, at)
        env += gk * g * np.clip(td / 0.006, 0, 1) * np.exp(-td / 0.030)
        s += 0.35 * gk * speed_curve(t, at, at + 0.05)
        for j in range(2):
            place(b, spar_tick(r, 2000 * m * r.uniform(0.85, 1.2), decay=0.006), at + 0.004 + 0.012 * j,
                  0.16 * gk * r.uniform(0.6, 1.0))
    b += 0.70 * membrane(r, dur, env, 350 * m, 3500 * m, q['rate'], depth=0.85, crisp=0.25) / 1.25
    b += 0.30 * motor(t, s, r, 380 * m, 400 * m, grit=0.25, air=0.08, whine=0.30, rotor_div=6)
    return b


RUSH = [dict(m=1.00, L=1.72, gust=1.1), dict(m=1.10, L=1.86, gust=1.5), dict(m=0.90, L=1.66, gust=0.8)]


def wind_rush(take, r):
    """Air past the spread wings in a glide (glide 0, every 2 s; stall_loop 0): a 1.6-1.8 s swell, not a loop, so
    two instances overlap gently. Noise through a band that opens from 110-800 Hz to 190-1700 Hz with the swell,
    gusting at 0.8-1.5 Hz, a low buffet under it and the membrane's faint flutter at the gust peaks. It starts at
    -14 dB so its onset sits on the key; no tones (no whistle)."""
    q = RUSH[take - 1]
    m, L = q['m'], q['L']
    dur = round(L + 0.20, 3)
    b = buf(dur)
    t = tt(dur)
    sw = np.where(t < L, np.sin(np.pi * np.clip(t / L, 0, 1)) ** 2, 0.0)
    gust = 1 + 0.25 * np.sin(2 * np.pi * q['gust'] * t + r.uniform(0, 6.28)) + 0.12 * smooth(len(t), r, 3.0)
    env = (0.20 * np.clip(t / 0.003, 0, 1) * np.clip(1 - t / 0.35, 0, 1) + 0.80 * sw) * gust
    air = sweep_bandpass(noise(dur, r), (110 + 80 * sw) * m, (800 + 900 * sw) * m) * env
    b += 0.85 * norm(air)
    b += 0.30 * norm(lowpass(noise(dur, r), 180) * env ** 1.5)
    b += 0.10 * membrane(r, dur, env * sw * np.clip(gust - 1, 0, 1), 400 * m, 2500 * m, 20.0) / 1.18
    return b


# ---------------------------------------------------------------- the lantern's floor

FREL = [dict(m=1.00, gap=0.011, drop=0.030), dict(m=1.10, gap=0.017, drop=0.036), dict(m=0.90, gap=0.008, drop=0.026)]


def floor_release(take, r):
    """The lantern's lift floor unlatching (board 1): the two catches clack out, at 4 ms and 8-17 ms later (a strike, a
    tooth, a brass catch body at 900 / 1400 Hz), the floor's seal venting a short hiss, and the floor settling onto
    its cables 26-36 ms in (a low cable twang at 180 Hz, small)."""
    q = FREL[take - 1]
    m = q['m']
    b = buf(0.55)
    for k, at in enumerate((0.004, 0.004 + q['gap'])):
        gk = 1.0 - 0.15 * k
        place(b, strike(r, 2200 * m), at, 0.45 * gk)
        place(b, tooth(r, 2800 * m * (1 + 0.04 * k)), at, 0.35 * gk)
        place(b, norm(iron_body(r, 900 * m, 1400 * m, 0.45, band_scale=1.1 * m)), at, 0.40 * gk)
    place(b, norm(noise_burst(2500 * m, 9000, 0.040, r, attack=0.005)), 0.012, 0.32)
    tw = modal(180 * m, [1, 2.01, 3.03, 4.06], [1, 0.6, 0.35, 0.2], [0.05, 0.035, 0.025, 0.018], r)
    place(b, norm(tw), q['drop'], 0.16)
    place(b, thunk(r, 150, 100, 0.025, 0.010), q['drop'], 0.10)
    return b


FLAT = [dict(m=1.00, dk=1.00, bounce=0.052), dict(m=1.10, dk=1.20, bounce=0.060), dict(m=0.90, dk=0.85, bounce=0.046)]


def floor_latch(take, r):
    """The floor hoisted home, the jar's lid meeting the brass lip (board 15, build 193): a clunk at 4 ms (a strike, the
    floor's iron body at 520 / 860 Hz, a thunk 140 -> 85 Hz), a faint glass tick as the lid meets the lip, the two
    catches snapping in (teeth at 28 and 36 ms) and the 0.4 px bounce's small knock 46-60 ms in."""
    q = FLAT[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(0.55)
    h = 0.004
    place(b, strike(r, 1500 * m), h, 0.40)
    place(b, norm(iron_body(r, 520 * m, 860 * m, 0.7 * dk, band_scale=m)), h, 0.60)
    place(b, thunk(r, 140 * m, 85 * m, 0.040, 0.015), h, 0.35)
    place(b, norm(glass_modes(r, 2300 * m, decays=(0.02, 0.012, 0.008, 0.005))), h + 0.002, 0.07)
    place(b, tooth(r, 2500 * m), 0.028, 0.20)
    place(b, tooth(r, 2650 * m), 0.036, 0.16)
    place(b, mix(norm(modal(600 * m, [1, 2.756], [1, 0.4], [0.020, 0.012], r)), 0.6 * thunk(r, 130, 90, 0.02, 0.01)),
          q['bounce'], 0.16)
    return b


FBLOW = [dict(m=1.00, taut=0.200, ramp=0.265), dict(m=1.15, taut=0.180, ramp=0.240)]


def floor_blow(take, r):
    """The eject blowing the lantern's floor down on its cables (eject 3, played on the tick): a pneumatic bang at 3 ms
    (a strike, a 300-7000 Hz blast, a boom 95 -> 42 Hz), the two catches clanging as they fly (750 Hz), the cables
    paying out (the winch's pawl ratcheting fast then slowing, a whirr rising), the cables snapping taut at 180-200 ms
    (a strike and a 160 Hz cable twang) and the floor tipping to a ramp with a clank 60 ms after."""
    q = FBLOW[take - 1]
    m = q['m']
    dur = 0.90
    b = buf(dur)
    t = tt(dur)
    h = 0.003
    place(b, strike(r, 1300), h, 0.60)
    place(b, norm(noise_burst(300 * m, 7000 * m, 0.030, r, attack=0.0008)), h, 0.70)
    place(b, thunk(r, 95, 42, 0.060, 0.020), h, 0.40)
    for k in range(2):
        place(b, norm(modal(750 * m * (1 + 0.05 * k), [1, 2.756, 5.404], [1, 0.6, 0.35], [0.04, 0.028, 0.016], r)),
              h + 0.006 * k, 0.28)
    at = 0.030
    while at < q['taut'] - 0.01:  # the pawl over the drum's teeth: 6 ms apart at first, slowing to 20 ms
        u = (at - 0.03) / (q['taut'] - 0.03)
        place(b, tooth(r, 2400 * m * (1 - 0.1 * u)), at, 0.14 * (1 - 0.4 * u))
        at += 0.006 + 0.014 * u
    s = speed_curve(t, 0.02, q['taut'] + 0.01)
    b += 0.20 * norm(bandpass(noise(dur, r), 800 * m, 3200 * m) * s)
    tw = modal(160 * m, [1, 2.01, 3.02, 4.05, 5.1], [1, 0.7, 0.45, 0.3, 0.2], [0.07, 0.05, 0.035, 0.025, 0.018], r)
    place(b, strike(r, 1500), q['taut'], 0.30)
    place(b, norm(tw), q['taut'], 0.30)
    place(b, norm(iron_body(r, 600 * m, 980 * m, 0.5, band_scale=m)), q['ramp'], 0.28)
    place(b, thunk(r, 140, 85, 0.030, 0.012), q['ramp'], 0.20)
    return b


WINCH = [dict(m=1.00, mv=4.0), dict(m=1.08, mv=4.5), dict(m=0.92, mv=5.0)]


def cable_winch(take, r):
    """The lantern floor's small winch (board 2, lowering; board 10, hoisting): a relay click, a small motor (380 +
    600 s Hz) over a 4-5 tick wind, the drum's pawl clicking at a rate that follows the speed (up to 45 a second),
    the cables creaking on the sheaves, and the brake's click at the end."""
    q = WINCH[take - 1]
    m = q['m']
    start = 0.006
    stop = start + q['mv'] * T
    dur = round(stop + 0.35, 3)
    b = buf(dur)
    t = tt(dur)
    s = speed_curve(t, start, stop)
    b += 0.65 * motor(t, s, r, 380 * m, 600 * m, grit=0.40, air=0.10, whine=0.30, rotor_div=5)
    ph = np.cumsum(45.0 * s) / SR
    for i in np.nonzero(np.diff(np.floor(ph)) > 0)[0]:
        place(b, tooth(r, 2700 * m * r.uniform(0.96, 1.04)), i / SR, 0.12 * float(s[i]) ** 0.5)
    place(b, creak(r, 0.6 * (stop - start), 1000 * m, rate=(0.014, 0.024), decay=0.004), start + 0.03, 0.08)
    place(b, relay_click(r, 1800 * m), 0.003, 0.25)
    place(b, relay_click(r, 1500 * m, release_after=0.02), stop, 0.30)
    place(b, thunk(r, 170, 120, 0.02, 0.01), stop, 0.10)
    return b


# ---------------------------------------------------------------- the talons

def talon_hit(r, m, steel=1.0, rubber=1.0, f=1800.0, hp=1800.0, ring=0.012):
    """One rubber-and-steel talon closing: a strike, the steel claw's short modes, the rubber toe's thock."""
    return mix(0.35 * steel * strike(r, hp * m),
               0.45 * steel * norm(modal(f * m, [1, 2.3, 3.6], [1, 0.45, 0.2], [ring, ring * 0.7, ring * 0.45], r, 0.02)),
               0.40 * rubber * norm(lowpass(noise(0.08, r), 900 * m) * exp_env(0.08, 0.008, 0.0008)),
               0.18 * rubber * thunk(r, 240 * m, 160 * m, 0.014, 0.006))


TSNAP = [dict(m=1.00, gaps=(0.0, 0.004, 0.009), reb=0.070), dict(m=1.10, gaps=(0.0, 0.006, 0.013), reb=0.082),
         dict(m=0.86, gaps=(0.0, 0.003, 0.007), reb=0.064)]


def talon_snap(take, r):
    """The three talons snapping shut on air (talon_refused 8, with the refusal): three hollow rubber-and-steel clacks
    within 7-13 ms (no creature between them: the claws meet, 1.8 kHz steel), overshooting, so two smaller rebound
    clacks 64-82 ms and 40 ms after that, and the tendon spring's faint falling 'tang'."""
    q = TSNAP[take - 1]
    m = q['m']
    b = buf(0.50)
    for k, dg in enumerate(q['gaps']):
        place(b, talon_hit(r, m * (1 + 0.04 * k), ring=0.022), 0.003 + dg, 0.75 * (1 - 0.12 * k))
    place(b, talon_hit(r, m * 1.1, steel=0.7, rubber=0.6, ring=0.016), q['reb'], 0.35)
    place(b, talon_hit(r, m * 1.15, steel=0.6, rubber=0.4, ring=0.012), q['reb'] + 0.040, 0.22)
    place(b, sweep_sine(1150 * m, 900 * m, 0.060, 0.04), 0.010, 0.10)
    return b


TCLOSE = [dict(m=1.00, gaps=(0.0, 0.007, 0.014), lock=0.080), dict(m=1.10, gaps=(0.0, 0.010, 0.019), lock=0.092),
          dict(m=0.90, gaps=(0.0, 0.005, 0.011), lock=0.070)]


def talon_close(take, r):
    """The talons closing on a creature (talon_grab 10, when the server takes hold): softer than the snap, a grip.
    Three rubber toes meeting a body within 11-19 ms (soft thuds: low-passed rubber thock and a small low knock, the
    steel barely in it), the steel tendons tightening (a creak from 25 ms) and the lock's tooth at 70-92 ms."""
    q = TCLOSE[take - 1]
    m = q['m']
    b = buf(0.45)
    for k, dg in enumerate(q['gaps']):
        at = 0.003 + dg
        gk = 1 - 0.1 * k
        place(b, talon_hit(r, m * (1 + 0.03 * k), steel=0.30, rubber=1.0, f=1400.0, hp=1200.0, ring=0.008), at, 0.70 * gk)
        place(b, norm(lowpass(noise(0.15, r), 500 * m) * exp_env(0.15, 0.018, 0.002)), at, 0.40 * gk)
    place(b, creak(r, 0.15, 700 * m, rate=(0.012, 0.020), decay=0.005), 0.025, 0.16)
    place(b, tooth(r, 2200 * m), q['lock'], 0.28)
    return b


TOPEN = [dict(m=1.00, gaps=(0.0, 0.005, 0.011)), dict(m=1.12, gaps=(0.0, 0.008, 0.016)), dict(m=0.90, gaps=(0.0, 0.004, 0.008))]


def talon_open(take, r):
    """The talons springing open to let a load go (talon_drop 1, played by the server on the tick): the catch's tooth
    at 3 ms, then each talon's spring snapping it open within 8-16 ms (a strike, a short spring twang falling 1000 ->
    760 Hz, the steel claw's 1.4 kHz modes) and a small knock as the toes hit their stops 40 ms on."""
    q = TOPEN[take - 1]
    m = q['m']
    b = buf(0.45)
    place(b, tooth(r, 2400 * m), 0.003, 0.30)
    for k, dg in enumerate(q['gaps']):
        at = 0.004 + dg
        gk = 1 - 0.12 * k
        place(b, strike(r, 1600 * m), at, 0.35 * gk)
        place(b, sweep_sine(1000 * m * (1 + 0.04 * k), 760 * m, 0.035, 0.015), at, 0.32 * gk)
        place(b, norm(modal(1400 * m, [1, 2.6, 4.1], [1, 0.4, 0.2], [0.025, 0.016, 0.010], r)), at, 0.30 * gk)
    place(b, mix(norm(modal(900 * m, [1, 2.4], [1, 0.4], [0.012, 0.008], r)), 0.5 * thunk(r, 200, 140, 0.012, 0.006)),
          0.045 + q['gaps'][-1], 0.22)
    return b


# ---------------------------------------------------------------- references (measured as the game plays them)

ELYTRA_EQUIP = dict(event='item.armor.equip_elytra', file='item__armor__equip_leather1.ogg', vol=1.0, pitch=1.0)
EQUIP_GOLD = dict(event='item.armor.equip_gold', file='item__armor__equip_gold1.ogg', vol=1.0, pitch=1.0)
EQUIP_GENERIC = dict(event='item.armor.equip_generic', file='item__armor__equip_generic1.ogg', vol=1.0, pitch=1.0)
EQUIP_IRON = dict(event='item.armor.equip_iron', file='item__armor__equip_iron1.ogg', vol=1.0, pitch=1.0)
COPPER_TRAPDOOR = dict(event='block.copper_trapdoor.close', file='block__copper_trapdoor__toggle1.ogg', vol=1.0, pitch=0.95)
TRIDENT = dict(event='item.trident.throw', file='item__trident__throw1.ogg', vol=1.0, pitch=1.0)
FIREWORK = dict(event='entity.firework_rocket.blast', file='fireworks__blast1.ogg', vol=1.0, pitch=1.0)
ELYTRA_GLIDE = dict(event='item.elytra.flying', file='item__elytra__elytra_loop.ogg', vol=0.5, pitch=1.0)

BASE = dict(category='NEUTRAL', attenuation=16, fixed_range=False, volume=1.0)
KEY_NOTE = (' Keyframe volume 1.0 and pitch 1.0: the file carries the loudness and the size (the clip\'s proposed '
            'volumes and pitches were standing in for it).')


def ev(name, **kw):
    d = dict(BASE)
    d.update(id=f'mecha.kite.{name}', file=f'mecha/kite/{name}')
    d.update(kw)
    return d


SOUNDS = [
    # ---- the wingbeat
    ev('wingbeat', fn=wingbeat, takes=4, target=-26.5, cls='wingbeat (servo class, a soft air thump)',
       kind=('step', 0.30), fade_ms=25, hit_ms=3, ref=ELYTRA_EQUIP, pitch=[0.94, 1.06], attenuation=24,
       subtitle_en='Mecha wings beat', subtitle_pt='Asas de mecha batem',
       play='flap tick 6 (the bottom of the down-stroke; flap plays 0.5-1.8x, so a beat every 0.44-1.6 s) and takeoff tick 10. Random take and pitch 0.94-1.06; heard about 0.3 s.' + KEY_NOTE),
    ev('wingbeat_light', fn=wingbeat_light, takes=3, target=-29.0, cls='wingbeat (light)', kind=('step', 0.18),
       fade_ms=20, hit_ms=3, ref=EQUIP_GENERIC, pitch=[0.94, 1.06], attenuation=20,
       subtitle_en='Mecha wings beat', subtitle_pt='Asas de mecha batem',
       play='hover tick 4 (a shallow beat every 0.6 s) and talon_drop tick 3. Random take and pitch.' + KEY_NOTE),
    ev('wingbeat_heavy', fn=wingbeat_heavy, takes=3, target=-24.5, cls='wingbeat (heavy, lifting a load)',
       kind=('step', 0.40), fade_ms=30, hit_ms=3, ref=ELYTRA_EQUIP, pitch=[0.95, 1.05], attenuation=24,
       subtitle_en='Wings beat heavily', subtitle_pt='Asas batem com força',
       play='talon_grab tick 12 (the heave) and talon_carry tick 8 (once per carry beat). The spar creaks twice under the load, the second at 130-180 ms.' + KEY_NOTE),
    ev('wingbeat_weak', fn=wingbeat_weak, takes=3, target=-29.5, cls='wingbeat (weak, flat battery)',
       kind=('step', 0.30), fade_ms=25, hit_ms=3, ref=EQUIP_GENERIC, pitch=[0.95, 1.05],
       subtitle_en='Wings falter', subtitle_pt='Asas fraquejam',
       play='stall ticks 4 and 14 (two weak half-beats, a different take each). The drive servo whines down as it gives out.' + KEY_NOTE),
    # ---- spreading, folding, braking
    ev('wing_unfurl', fn=wing_unfurl, takes=3, target=-27.0, cls='servo (wing spread)', kind=('servo', 5 * T),
       fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[0.97, 1.03], subtitle_en='Wings unfurl', subtitle_pt='Asas se abrem',
       play='boot 10, takeoff 2, idle_tic 9 (half-open) and build 168. A 4.5-5.5 tick spread; the membrane snaps taut at the end, 230-280 ms in.' + KEY_NOTE),
    ev('wing_fold', fn=wing_fold, takes=3, target=-28.0, cls='servo (wing fold)', kind=('servo', 6 * T), fade_ms=20,
       hit_ms=3, ref=PISTON_OUT, pitch=[0.97, 1.03], subtitle_en='Wings fold', subtitle_pt='Asas se fecham',
       play='boot 28, land 10, land_heavy 10, vent 86, idle_tic 24 and build 186. A 5-7 tick fold; the wing seats at 255-355 ms, its catch ticks 38-55 ms after.' + KEY_NOTE),
    ev('wing_brake', fn=wing_brake, takes=3, target=-21.5, cls='slam class (the braking stroke\'s air)',
       kind=('whoosh', 0.40), fade_ms=40, hit_ms=3, ref=TRIDENT, pitch=[0.96, 1.04], attenuation=24,
       subtitle_en='Wings brake', subtitle_pt='Asas freiam',
       play='flare tick 7 (the big cupped stroke): the wings catch the air on the key, the push lasts 0.26-0.36 s.' + KEY_NOTE),
    ev('wing_shrug', fn=wing_shrug, takes=3, target=-28.0, cls='servo (shrug)', kind=('servo', 0.36), fade_ms=25,
       hit_ms=3, ref=PISTON_OUT, pitch=[0.96, 1.04], subtitle_en='Mecha shrugs', subtitle_pt='Mecha dá de ombros',
       play='talon_refused tick 15 (the lopsided shrug): left wing up on the key, right 35-60 ms later, both let down 0.30-0.36 s in (as the 6-tick hold ends).' + KEY_NOTE),
    # ---- ground and life
    ev('wing_scrape', fn=wing_scrape, takes=3, target=-27.5, cls='scrape (servo class)', kind=('scrape', 0.42),
       fade_ms=25, hit_ms=3, ref=EQUIP_IRON, pitch=[0.95, 1.05], subtitle_en='Wingtip scrapes',
       subtitle_pt='Ponta da asa raspa',
       play='limp tick 16, once per limp cycle: the tip catches on the key and drags 0.36-0.46 s.' + KEY_NOTE),
    ev('wing_slump', fn=wing_slump, takes=3, target=-25.5, cls='landing class (wings falling limp)',
       kind=('move', 0.30), fade_ms=30, hit_ms=3, ref=ELYTRA_EQUIP, pitch=[0.96, 1.04], attenuation=20,
       subtitle_en='Wings go limp', subtitle_pt='Asas caem sem força',
       play='destroyed tick 12: the brakes let go on the key, the spars hit the ground 185-275 ms in (about tick 16, where the clip lays them down).' + KEY_NOTE),
    ev('feather_ruffle', fn=feather_ruffle, takes=3, target=-29.5, cls='membrane shake (small life)', kind='latch',
       fade_ms=20, hit_ms=3, ref=EQUIP_GOLD, pitch=[0.94, 1.06], attenuation=12,
       subtitle_en='Mecha preens', subtitle_pt='Mecha ajeita as asas',
       play='parked tick 42 (a twitch), idle_tic ticks 11 and 15 (the preening shakes, a different take each).' + KEY_NOTE),
    ev('wind_rush', fn=wind_rush, takes=3, target=-30.0, cls='air (a glide swell, layered)', kind=('swell', 1.62),
       fade_ms=80, hit_ms=3, ref=ELYTRA_GLIDE, pitch=[0.95, 1.05], attenuation=20,
       subtitle_en='Wind rushes', subtitle_pt='Vento passa zunindo',
       play='glide tick 0 (every 2.0 s at playback 1) and stall_loop tick 0. A 1.6-1.8 s swell, not a loop: consecutive plays overlap at their quiet ends. Random take and pitch so the overlaps do not repeat.' + KEY_NOTE),
    # ---- the lantern's floor
    ev('floor_release', fn=floor_release, takes=3, target=-24.0, cls='clamp release', kind='latch', fade_ms=15,
       hit_ms=4, ref=DOOR_OPEN, pitch=[0.95, 1.05], subtitle_en='Lantern floor unlatches',
       subtitle_pt='Piso da lanterna se solta',
       play='board tick 1: the catches at 4 ms and 8-17 ms later, the floor settles onto its cables at 26-36 ms.' + KEY_NOTE),
    ev('floor_latch', fn=floor_latch, takes=3, target=-22.5, cls='clamp (latch)', kind='latch', fade_ms=15, hit_ms=4,
       ref=TRAPDOOR_CLOSE, pitch=[0.95, 1.05], subtitle_en='Lantern floor latches',
       subtitle_pt='Piso da lanterna trava',
       play='board tick 15 (the lid meets the lip) and build tick 193: the clunk at 4 ms, the catches at 28 and 36 ms, the bounce at 46-60 ms (tick 16).' + KEY_NOTE),
    ev('floor_blow', fn=floor_blow, takes=2, target=-20.5, cls='slam (bang)', kind=('move', 0.30), fade_ms=30,
       hit_ms=3, ref=FIREWORK, pitch=[0.96, 1.04], attenuation=24,
       subtitle_en='Lantern floor blows open', subtitle_pt='Piso da lanterna estoura',
       play='Server on eject tick 3, at the lantern, on the tick (contact_offset_ticks 0): the bang on the tick, the cables snap taut at 180-200 ms (tick 7) and the floor tips to a ramp 60 ms later.'),
    ev('cable_winch', fn=cable_winch, takes=3, target=-27.0, cls='servo (winch)', kind=('servo', 4.5 * T),
       fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[0.97, 1.03], subtitle_en='Winch winds', subtitle_pt='Guincho enrola',
       play='board tick 2 (the floor lowering, to tick 5) and board tick 10 (the hoist, to 15). A 4-5 tick wind; the brake clicks at 206-256 ms by take.' + KEY_NOTE),
    # ---- the talons
    ev('talon_snap', fn=talon_snap, takes=3, target=-25.5, cls='clamp (hollow clack)', kind='latch', fade_ms=15,
       hit_ms=3, ref=COPPER_TRAPDOOR, pitch=[0.95, 1.05], attenuation=20,
       subtitle_en='Talons snap shut', subtitle_pt='Garras fecham no vazio',
       play='talon_refused tick 8, with talon:refused and the HUD reason on 9. It is the refusal\'s sound: play it on every refused Talon Lift, never skip it.' + KEY_NOTE),
    ev('talon_close', fn=talon_close, takes=3, target=-25.0, cls='clamp (soft grip)', kind='latch', fade_ms=15,
       hit_ms=3, ref=ELYTRA_EQUIP, pitch=[0.95, 1.05], attenuation=20,
       subtitle_en='Talons grip', subtitle_pt='Garras agarram',
       play='Server on talon_grab tick 10 (talon:close), when it takes hold, at fx_cargo, on the tick (contact_offset_ticks 0). The lock ticks at 70-92 ms.'),
    ev('talon_open', fn=talon_open, takes=3, target=-24.0, cls='clamp (spring release)', kind='latch', fade_ms=15,
       hit_ms=3, ref=TRAPDOOR_CLOSE, pitch=[0.95, 1.05], attenuation=20,
       subtitle_en='Talons let go', subtitle_pt='Garras soltam',
       play='Server on talon_drop tick 1 (talon:open), when the creature is let go, at fx_cargo, on the tick (contact_offset_ticks 0).'),
]

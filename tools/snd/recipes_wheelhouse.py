"""The Wheelhouse: Noggin & Sons' site tug, a small ivory works vehicle on four big rubber wheels.

A petrol twin with two exhaust pipes under striped flaps, a brass ship's wheel at the stern, a brass binnacle ring that
latches over the jar, a RIGHT OF WAY flag on a sprung stalk, and a yellow buffer beam on two spring buffers that it
rams things with. It is proud and brisk: it revs for no reason, it has a horn, and it believes it has right of way.

The engine is built from exhaust pulses: each firing is a short burst of low noise ringing the pipe's resonance (one
pipe at 150 Hz, the other at 172 Hz, firing in turn, the second a little weaker: the twin's lope), under the block's
low rumble at half the firing rate and a faint valve tick. One-shot engine sounds drive the same pulses from a rate
curve; the loop places them on a circle. The horn sounds the company motif's first two notes, C5 and E5, together,
twice. Everything else is the house parts: struck brass and iron for the ring, the flag and the flaps, springs for
the buffers and the suspension, rubber for the tyres, stick-slip for the brakes.

Event ids are `mecha.wheelhouse.<name>`; the code plays one when it exists and the Hopper's `mecha.<name>` otherwise.
Keyframe sounds hit at 0-5 ms; the server's moments (rush_go, rush_ram, horn) hit at 3 ms. Takes differ by
construction (per-take tables). The motor is a 2.0 s loop: 60 firings at 30 Hz placed on a circle, every periodic
layer a multiple of 0.5 Hz, every noise layer circular, every filter run on the loop tiled three times (the middle
copy kept), finished by `finish_loop` (no fades).
"""
import numpy as np
from nsynth import *
from recipes_mecha import circ_noise, finish_loop, soft
from recipes_mecha import PISTON_IN, PISTON_OUT, FIZZ, TRAPDOOR_CLOSE, DOOR_CLOSE, DOOR_OPEN, GOLEM_HURT, BELL
from recipes_bulwark import creak, BEACON_AMBIENT, FIREWORK, LANTERN, EQUIP_IRON, COPPER_TRAPDOOR, EQUIP_CHAIN

T = TICK


# ---------------------------------------------------------------- shared parts (the Rover borrows these too)

def circ(x, fn):
    """Run a filter on a loop without a seam: filter the loop tiled three times and keep the middle copy."""
    n = len(x)
    return fn(np.tile(x, 3))[n:2 * n]


def place_circ(b, x, at, gain=1.0):
    """Add x into the loop b at time `at`, wrapping round the end."""
    n = len(b)
    i = int(round(at * SR))
    np.add.at(b, (i + np.arange(len(x))) % n, gain * x)
    return b


def pulse(r, m=1.0, pipe=150.0, bright=1.0):
    """One firing: a burst of low noise (120-2500 Hz, 5 ms), the pipe's resonance ringing (pipe Hz x 1, 2.05, 3.2,
    4.4, 12 ms) and a small low knock from the block."""
    return mix(0.9 * norm(modal(pipe * m, [1, 2.05, 3.2, 4.4], [1, 0.5, 0.25, 0.12], [0.012, 0.007, 0.005, 0.003], r, 0.03)),
               0.8 * norm(noise_burst(120 * m, 2500 * m * bright, 0.005, r, attack=0.0006)),
               0.5 * thunk(r, 120 * m, 70 * m, 0.010, 0.005))


def firing_times(t, rate):
    """The times at which an engine whose firing rate follows `rate` (Hz, per sample) fires."""
    ph = np.cumsum(rate) / SR
    return t[1:][np.diff(np.floor(ph)) > 0]


def engine(dur, rate, level, r, m=1.0, lope=0.82, pipes=(150.0, 172.0), body=0.25, valve=0.08, bright=1.0,
           jitter=0.0008):
    """The petrol twin as a one-shot: pulses at the firing times of `rate`, each scaled by `level` there (alternate
    pulses `lope` weaker, the other pipe), low-passed at 3.2 kHz (the silencers); the block's rumble (harmonics of half
    the firing rate under 700 Hz) and the valve tick (3-8 kHz) follow the level. `level` must reach 0 a little before
    the end so every pulse rings out inside the buffer."""
    b = buf(dur)
    t = tt(dur)
    for k, at in enumerate(firing_times(t, rate)):
        g = float(np.interp(at, t, level)) * (1.0 if k % 2 == 0 else lope) * r.uniform(0.85, 1.0)
        if g < 1e-3:
            continue
        place(b, pulse(r, m, pipes[k % 2], bright), max(at + r.uniform(-jitter, jitter), 0.0), g)
    ex = lowpass(b, 3200)
    hum = lowpass(osc_harm(rate / 2, 16, r, 1.1), 700) * level
    gate = (0.5 + 0.5 * np.cos(2 * np.pi * np.cumsum(rate / 2) / SR + np.pi)) ** 12
    vt = bandpass(noise(dur, r), 3000, 8000) * gate * level
    return norm(ex) + body * norm(hum) + valve * norm(vt)


def curve(t, pts):
    """A piecewise-linear curve through (time, value) points."""
    xs, ys = zip(*pts)
    return np.interp(t, xs, ys)


def spring_twang(r, f0, f1, tau, wob=7.0, depth=0.04):
    """A coil spring let go: a tone falling from f0 to f1 with a wobble, a faint octave, decaying with tau."""
    t = tt(7 * tau + 0.002)
    f = (f1 + (f0 - f1) * np.exp(-t / (tau / 2))) * (1 + depth * np.sin(2 * np.pi * wob * t))
    ph = 2 * np.pi * np.cumsum(f) / SR
    return (np.sin(ph) + 0.25 * np.sin(2 * ph + 0.7)) * np.exp(-t / tau) * np.clip(t / 0.002, 0, 1)


def stickslip(r, fcurve, res, env, width=0.25, nharm=40):
    """Stick-slip squeal: a pulse train at fcurve Hz (the slips) through a resonance at `res` Hz (the part that sings),
    under the envelope env. The squeal's pitch follows fcurve's harmonics near res."""
    x = osc_harm(fcurve, nharm, r, 0.5)
    return bandpass(x, res * (1 - width), res * (1 + width)) * env


def beep(f, length, r, tilt=1.6, attack=0.004, release=0.010, nharm=9):
    """A plain electric beeper: odd harmonics (a rounded square wave) at f Hz, held `length` s."""
    d = length + 7 * release + attack
    t = tt(d)
    env = np.clip(t / attack, 0, 1) * np.where(t < length, 1.0, np.exp(-(t - length) / release))
    return osc_harm(np.full(len(t), float(f)), nharm, r, tilt, odd_only=True) * env


# ---------------------------------------------------------------- the engine loop

def motor_loop(take, r):
    """The petrol twin at its running speed, 2.0 s, seamless: 60 firings at 30 Hz on a circle (each up to 1 ms early or
    late, each a slightly different strength, the pipes alternating), low-passed at 3.2 kHz; the block's rumble
    (harmonics of 15 Hz, 1/k^1.2, under 700 Hz); the valve tick (3-8 kHz circular noise gated between firings); a
    gearbox whine at 450 Hz (the 30th harmonic of 15 Hz) with a 1.5 Hz wobble; intake air; a 0.5 Hz swell. Every
    frequency is a multiple of 0.5 Hz."""
    L = 2.0
    n = int(L * SR)
    t = np.arange(n) / SR
    ex = np.zeros(n)
    for k in range(60):
        at = k / 30.0 + r.uniform(-0.001, 0.001)
        g = (1.0 if k % 2 == 0 else 0.80) * r.uniform(0.86, 1.0)
        place_circ(ex, pulse(r, 1.0, (150.0, 172.0)[k % 2]), at % L, g)
    ex = circ(ex, lambda x: lowpass(x, 3200))
    hum = circ(sum(np.sin(2 * np.pi * 15.0 * k * t + r.uniform(0, 6.28)) / k ** 1.2 for k in range(1, 40)),
               lambda x: lowpass(x, 700))
    gate = (0.5 + 0.5 * np.cos(2 * np.pi * 30.0 * t + np.pi)) ** 12
    vt = circ_noise(n, r, 3000, 8000) * gate
    whine = np.sin(2 * np.pi * 450.0 * t + 0.4 * np.sin(2 * np.pi * 1.5 * t)) * (0.7 + 0.3 * np.sin(2 * np.pi * 1.0 * t))
    air = circ_noise(n, r, 250, 2500)
    swell = 1 + 0.06 * np.sin(2 * np.pi * 0.5 * t)
    return (1.0 * norm(ex) + 0.30 * norm(hum) + 0.07 * norm(vt) + 0.04 * norm(whine) + 0.08 * norm(air)) * swell


# ---------------------------------------------------------------- the engine's moments

REV = [dict(m=1.00, peak=2.1, up=0.16, pops=(0.52, 0.66)), dict(m=1.06, peak=2.4, up=0.19, pops=(0.48, 0.60, 0.71)),
       dict(m=0.94, peak=1.8, up=0.13, pops=(0.58,))]


def engine_rev(take, r):
    """A blip of the throttle, pleased with itself (idle_tic 4, drive_start 0, rush_windup 0, rush_refused 0): the
    throttle snaps open at 3 ms (a hard first firing), the firing rate leaps from 30 Hz to 54-72 Hz in 0.13-0.19 s and
    falls back, and the overrun crackles once to three times in the pipes as it drops; it fades as the running loop
    takes over."""
    q = REV[take - 1]
    m = q['m']
    dur = 1.30
    t = tt(dur)
    rate = 30 + 30 * (q['peak'] - 1) * np.where(t < q['up'], np.sin(0.5 * np.pi * t / q['up']),
                                                np.exp(-(t - q['up']) / 0.18))
    level = curve(t, [(0, 0.0), (0.003, 1.0), (q['up'], 1.0), (0.55, 0.55), (0.95, 0.0), (dur, 0.0)])
    b = engine(dur, rate, level, r, m)
    place(b, strike(r, 1200), 0.003, 0.25)
    for k, at in enumerate(q['pops']):
        place(b, norm(mix(pulse(r, m * 1.3, 210.0, 1.6), 0.4 * strike(r, 1500))), at, 0.55 / (k + 1) ** 0.5)
    return b


CATCH = [dict(m=1.00, stumble=(0.080, 0.130, 0.175)), dict(m=1.07, stumble=(0.070, 0.150, 0.190, 0.225)),
         dict(m=0.93, stumble=(0.095, 0.140))]


def engine_catch(take, r):
    """The engine catching (boot_engine 9): the starter's pinion clacks out at 3 ms as the first firing lands hard (a
    strike and a doubled pulse), two to four stumbling firings, then it runs, a short eager surge to 36 Hz, settling
    to 30 Hz and fading as the loop takes over."""
    q = CATCH[take - 1]
    m = q['m']
    dur = 1.30
    t = tt(dur)
    run0 = q['stumble'][-1] + 0.04
    rate = np.where(t < run0, 0.0, 30 + 6 * np.sin(np.pi * np.clip((t - run0) / 0.30, 0, 1)))
    level = curve(t, [(0, 0.0), (run0, 0.0), (run0 + 0.01, 0.85), (run0 + 0.30, 0.8), (1.05, 0.0), (dur, 0.0)])
    b = engine(dur, rate, level, r, m)
    place(b, norm(mix(pulse(r, m, 150.0, 1.3), 0.7 * pulse(r, m, 172.0, 1.3))), 0.003, 1.0)
    place(b, strike(r, 1100), 0.003, 0.35)
    place(b, tooth(r, 2200 * m), 0.006, 0.25)
    for k, at in enumerate(q['stumble']):
        place(b, pulse(r, m, (172.0, 150.0)[k % 2]), at, 0.55 + 0.1 * r.uniform())
    return b


STARTER = [dict(m=1.00, comp=6.5, crank=0.37), dict(m=1.08, comp=7.5, crank=0.34), dict(m=0.92, comp=5.5, crank=0.40)]


def crank(r, dur, s, comp_t, m, gain=1.0):
    """The starter motor cranking: a whine (190 + 520 s Hz), brush grit and air under the speed curve s, and a soft
    chuff from the pipes at every compression (comp_t)."""
    t = tt(dur)
    b = motor(t, s, r, 190 * m, 520 * m, grit=0.55, air=0.18, whine=0.34, rotor_div=7)
    for at in comp_t:
        place(b, norm(lowpass(noise(0.12, r), 700) * exp_env(0.12, 0.014, 0.004)), at, 0.35 * gain)
    return b


def starter_whirr(take, r):
    """The starter cranking the engine over (boot_engine 2, until the catch on 9): the solenoid clacks in at 3 ms (a
    relay click and a small thunk), the starter whines up in 30 ms and drags at every compression (5.5-7.5 a second:
    the speed dips and the pipes chuff), for 0.34-0.40 s, then lets go."""
    q = STARTER[take - 1]
    m = q['m']
    dur = round(q['crank'] + 0.40, 3)
    t = tt(dur)
    comp = np.arange(0.06, q['crank'], 1 / q['comp'])
    dips = sum(np.exp(-((t - c) / 0.025) ** 2) for c in comp)
    s = curve(t, [(0, 0), (0.005, 0), (0.035, 0.85), (q['crank'], 0.85), (q['crank'] + 0.06, 0.0), (dur, 0)]) * (1 - 0.35 * dips)
    b = crank(r, dur, s, comp, m)
    place(b, relay_click(r, 1500 * m), 0.003, 0.45)
    place(b, thunk(r, 160, 100, 0.025, 0.010), 0.003, 0.30)
    return b


REFUSE = [dict(m=1.00, comp=(0.07, 0.20, 0.36, 0.56), stop=0.62), dict(m=1.08, comp=(0.06, 0.18, 0.33, 0.52), stop=0.58)]


def rush_refused(take, r):
    """Rush Hour refused (the server's rush:refused, with the HUD line): the starter is tried and does not take it.
    The solenoid clacks in, the starter whines up and labours, each compression slower than the last (0.12-0.20 s
    apart), and on the last it stalls; the solenoid drops out with a click and a small tooth: no."""
    q = REFUSE[take - 1]
    m = q['m']
    dur = round(q['stop'] + 0.45, 3)
    t = tt(dur)
    dips = sum((0.4 + 0.15 * k) * np.exp(-((t - c) / (0.028 + 0.008 * k)) ** 2) for k, c in enumerate(q['comp']))
    s = curve(t, [(0, 0), (0.005, 0), (0.035, 0.80), (q['stop'] - 0.15, 0.55), (q['stop'], 0.0), (dur, 0)]) * np.clip(1 - dips, 0, 1)
    b = crank(r, dur, s, q['comp'], m, 0.9)
    place(b, relay_click(r, 1500 * m), 0.003, 0.45)
    place(b, thunk(r, 160, 100, 0.025, 0.010), 0.003, 0.30)
    place(b, relay_click(r, 1300 * m, release_after=0.035), q['stop'] + 0.01, 0.40)
    place(b, tooth(r, 1800 * m), q['stop'] + 0.06, 0.15)
    return b


BACKFIRE = [dict(m=1.00, flaps=(0.035, 0.062)), dict(m=1.10, flaps=(0.028, 0.075)), dict(m=0.90, flaps=(0.042, 0.058))]


def backfire_pop(take, r):
    """A backfire (rush_refused 8): a bang in the pipe at 3 ms (a strike, a pop falling 260 -> 70 Hz, a broadband
    blast 200-5000 Hz, the pipe ringing at 180 Hz), the striped flaps slapped open (two tin clacks 30-75 ms on), a
    puff of air after."""
    q = BACKFIRE[take - 1]
    m = q['m']
    b = buf(0.75)
    h = 0.003
    place(b, strike(r, 800), h, 0.55)
    place(b, sweep_sine(260 * m, 70 * m, 0.035, 0.010), h, 0.80)
    place(b, norm(noise_burst(200 * m, 5000 * m, 0.014, r, attack=0.0005)), h, 0.90)
    place(b, norm(modal(180 * m, [1, 2.05, 3.2], [1, 0.5, 0.25], [0.035, 0.020, 0.012], r)), h, 0.45)
    for k, at in enumerate(q['flaps']):
        place(b, norm(modal(1250 * m * (1 + 0.06 * k), [1, 1.59, 2.14], [1, 0.5, 0.3], [0.020, 0.014, 0.009], r)), at, 0.25)
    place(b, norm(noise_burst(400, 3000, 0.060, r, attack=0.010)), 0.020, 0.22)
    return soft(b, 3.0)


EXPOP = [dict(m=1.00), dict(m=1.12), dict(m=0.90)]


def exhaust_pop(take, r):
    """One last puff from the left pipe (rush_end 16): a small pop (a pulse, a pop falling 300 -> 110 Hz, a strike) and
    the flap ticking on the pipe 30 ms later."""
    m = EXPOP[take - 1]['m']
    b = buf(0.45)
    place(b, pulse(r, m * 1.2, 180.0, 1.4), 0.003, 0.9)
    place(b, sweep_sine(300 * m, 110 * m, 0.022, 0.008), 0.003, 0.45)
    place(b, strike(r, 1400), 0.003, 0.25)
    place(b, norm(modal(1300 * m, [1, 1.59], [1, 0.4], [0.012, 0.008], r)), 0.033, 0.15)
    return b


MISFIRE = [dict(m=1.00, dead=0.075, cough=0.120), dict(m=1.08, dead=0.090, cough=0.150), dict(m=0.92, dead=0.065, cough=0.105)]


def engine_misfire(take, r):
    """A misfire on a flat battery (limp 0 and 16): two firings, a dead one (a gap), a weak wet cough from the other
    pipe, and the drivetrain's lash taking up the jerk (a low knock and a small clunk)."""
    q = MISFIRE[take - 1]
    m = q['m']
    b = buf(0.55)
    place(b, pulse(r, m, 150.0), 0.003, 0.9)
    place(b, pulse(r, m, 172.0), 0.003 + 1 / 30, 0.75)
    place(b, norm(mix(lowpass(pulse(r, m * 0.8, 172.0, 0.6), 1500), 0.5 * noise_burst(150, 1200, 0.020, r, 0.004))),
          q['cough'], 0.75)
    place(b, mix(thunk(r, 130 * m, 75 * m, 0.035, 0.015), 0.5 * norm(modal(380 * m, [1, 2.756], [1, 0.4], [0.025, 0.015], r))),
          q['cough'] + 0.02, 0.45)
    return b


STOP = [dict(m=1.00, run=0.55, last=0.66), dict(m=1.08, run=0.48, last=0.60)]


def engine_stop(take, r):
    """The engine switched off (shutdown 4): the firings slow from 30 to 9 Hz over half a second and weaken, one last
    firing a little apart, and the block rocks back on its mounts (a deep knock and a short rattle of the flaps)."""
    q = STOP[take - 1]
    m = q['m']
    dur = 1.30
    t = tt(dur)
    rate = 9 + 21 * np.clip(1 - t / q['run'], 0, 1) ** 1.4
    level = curve(t, [(0, 0.0), (0.003, 0.9), (q['run'] * 0.5, 0.65), (q['run'], 0.30), (q['run'] + 0.03, 0.0), (dur, 0.0)])
    b = engine(dur, rate, level, r, m)
    place(b, pulse(r, m, 150.0), q['last'], 0.28)
    place(b, mix(thunk(r, 110 * m, 55 * m, 0.06, 0.02), 0.3 * norm(modal(420 * m, [1, 2.756], [1, 0.4], [0.03, 0.02], r))),
          q['last'] + 0.04, 0.55)
    for k in range(3):
        place(b, norm(modal(1300 * m * (1 + 0.05 * k), [1, 1.59], [1, 0.4], [0.010, 0.007], r)), q['last'] + 0.08 + 0.035 * k,
              0.12 / (k + 1))
    return b


# ---------------------------------------------------------------- Rush Hour

GO = [dict(m=1.00, top=88.0, chirp=1350.0), dict(m=1.06, top=96.0, chirp=1500.0)]


def rush_go(take, r):
    """Rush Hour starting, the signature moment (the server's rush:go): both pipes fire hard together at 3 and 9 ms
    (a strike and a low boom as it squats), then the engine is wound up, the firing rate climbing from 34 Hz to 88-96 Hz
    in under a second with the exhaust roar opening over it (a band rising from 300-1500 Hz to 700-3500 Hz); the rear
    tyres chirp as they bite (a rubber squeal at 1.35-1.5 kHz, 0.02-0.28 s). It hands over to the loop by 1.3 s."""
    q = GO[take - 1]
    m = q['m']
    dur = 1.70
    t = tt(dur)
    rate = 34 + (q['top'] - 34) * (1 - np.exp(-t / 0.35))
    level = curve(t, [(0, 0.0), (0.003, 1.0), (0.85, 1.0), (1.30, 0.0), (dur, 0.0)])
    b = engine(dur, rate, level, r, m, bright=1.4, body=0.35)
    u = np.clip(t / 0.9, 0, 1)
    roar = sweep_bandpass(noise(dur, r), (300 + 400 * u) * m, (1500 + 2000 * u) * m) * level ** 1.5
    b += 0.45 * norm(roar)
    env = curve(t, [(0, 0), (0.02, 0), (0.04, 1.0), (0.20, 0.7), (0.28, 0.0), (dur, 0)])
    fr = q['chirp'] / 4 * (1 + 0.10 * t) * (1 + 0.01 * np.sin(2 * np.pi * 23 * t))
    b += 0.22 * norm(stickslip(r, fr, q['chirp'], env, 0.20) + 0.3 * bandpass(noise(dur, r), 900, 4000) * env)
    for at in (0.003, 0.009):
        place(b, norm(mix(pulse(r, m, 150.0, 1.5), pulse(r, m, 172.0, 1.5))), at, 0.9)
    place(b, strike(r, 900), 0.003, 0.40)
    place(b, thunk(r, 100 * m, 45 * m, 0.07, 0.02), 0.003, 0.45)
    return soft(b, 2.5)


RAM = [dict(m=1.00, dk=1.00, rat=(0.12, 0.17, 0.25)), dict(m=1.08, dk=1.25, rat=(0.11, 0.19, 0.28)),
       dict(m=0.92, dk=0.85, rat=(0.14, 0.21))]


def rush_ram(take, r):
    """The buffer beam rammed into something (the server's rush:ram, contact on the tick): a heavy clang at 3 ms (a
    strike, the yellow beam's steel bar modes at 260 Hz, the frame's iron body at 330 / 560 Hz, a deep thunk 95 -> 40
    Hz, debris), the two spring buffers taking it (coil teeth 15-35 ms on and a spring tone falling 420 -> 300 Hz), and
    the binnacle ring rattling twice or three times after."""
    q = RAM[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(1.30)
    h = 0.003
    place(b, strike(r, 900), h, 0.50)
    place(b, norm(modal(260 * m, [1, 2.756, 5.404, 8.933], [1, 0.7, 0.45, 0.3], [x * dk for x in (0.12, 0.08, 0.05, 0.03)], r, 0.01)),
          h, 0.55)
    place(b, norm(iron_body(r, 330 * m, 560 * m, 1.2 * dk, band_scale=0.8 * m)), h, 0.60)
    place(b, thunk(r, 95 * m, 40 * m, 0.10, 0.03), h, 0.60)
    place(b, norm(lowpass(noise(0.8, r), 2200) * exp_env(0.8, 0.07, 0.002)), h, 0.40)
    for k, at in enumerate((0.015, 0.024, 0.035)):
        place(b, tooth(r, 1900 * m * (1 + 0.05 * k)), at, 0.16)
    place(b, spring_twang(r, 420 * m, 300 * m, 0.09), 0.020, 0.16)
    for k, at in enumerate(q['rat']):
        place(b, norm(modal(1700 * m * (1 + 0.07 * k), [1, 2.4, 3.9], [1, 0.45, 0.2], [0.016, 0.010, 0.007], r)), at, 0.16 / (k + 1) ** 0.6)
    return soft(b, 4.0)


BSPRING = [dict(m=1.00, gap=0.018), dict(m=1.10, gap=0.026), dict(m=0.90, gap=0.012)]


def buffer_spring(take, r):
    """The two spring buffers pushing the beam back out past its stop (rush_ram 2): each coil's twang (falling
    520 -> 380 Hz with a 7 Hz wobble) with a rattle of its turns, left then right 12-26 ms apart, and the beam's small
    knock on its stop."""
    q = BSPRING[take - 1]
    m = q['m']
    b = buf(0.80)
    for k, at in enumerate((0.004, 0.004 + q['gap'])):
        place(b, spring_twang(r, 520 * m * (1 + 0.04 * k), 380 * m, 0.07), at, 0.55)
        place(b, tooth(r, 2100 * m), at, 0.25)
    place(b, mix(norm(modal(330 * m, [1, 2.756], [1, 0.4], [0.03, 0.02], r)), 0.6 * thunk(r, 150, 95, 0.03, 0.012)), 0.07, 0.40)
    return b


WIND = [dict(m=1.00, run=0.75), dict(m=1.08, run=0.65)]


def rush_wind_down(take, r):
    """Rush Hour running out (rush_end 0): the engine lets off from 85 Hz back down to its 30 Hz run over 0.65-0.75 s,
    the roar closing (a band falling from 700-3500 Hz to 250-1200 Hz), the overrun crackling twice, the ram winding
    in under it (a small servo whine)."""
    q = WIND[take - 1]
    m = q['m']
    dur = 1.40
    t = tt(dur)
    u = np.clip(t / q['run'], 0, 1)
    rate = 30 + 55 * (1 - u) ** 1.5
    level = curve(t, [(0, 0.0), (0.003, 0.9), (q['run'], 0.55), (1.15, 0.0), (dur, 0.0)])
    b = engine(dur, rate, level, r, m, bright=1.2)
    roar = sweep_bandpass(noise(dur, r), (250 + 450 * (1 - u)) * m, (1200 + 2300 * (1 - u)) * m) * level ** 1.5
    b += 0.35 * norm(roar)
    s = speed_curve(t, 0.05, 0.50)
    b += 0.12 * norm(motor(t, s, r, 260 * m, 600 * m, grit=0.4, air=0.1, whine=0.3))
    for k, at in enumerate((0.30, 0.47)):
        place(b, norm(pulse(r, m * 1.3, 210.0, 1.6)), at, 0.35 / (k + 1))
    return b


FSNAP = [dict(m=1.00, dk=1.00), dict(m=1.10, dk=1.20), dict(m=0.90, dk=0.85)]


def flag_snap(take, r):
    """The RIGHT OF WAY flag snapping up past upright (rush_windup 8): the sprung stalk's catch lets go (a tooth), the
    striped tin plate whips through the air (a short swish), hits upright with a crisp clack (a strike, the plate's tin
    modes at 1.4 kHz) and shivers on its spring (a little twang falling 900 -> 760 Hz)."""
    q = FSNAP[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(0.55)
    place(b, tooth(r, 2400 * m), 0.003, 0.30)
    place(b, norm(noise_burst(1500 * m, 6000 * m, 0.012, r, attack=0.004)), 0.003, 0.35)
    place(b, strike(r, 2000), 0.012, 0.45)
    place(b, norm(modal(1400 * m, [1, 1.59, 2.14, 2.65, 3.9], [1, 0.7, 0.5, 0.3, 0.2], [x * dk for x in (0.05, 0.035, 0.025, 0.018, 0.012)], r)),
          0.012, 0.55)
    place(b, spring_twang(r, 900 * m, 760 * m, 0.05, wob=11.0), 0.015, 0.18)
    return b


FFOLD = [dict(m=1.00, bounce=0.070), dict(m=1.10, bounce=0.085), dict(m=0.90, bounce=0.060)]


def flag_fold(take, r):
    """The flag folded down when the boost runs out (rush_end 7, the landing): the plate lands on the mudguard with a
    duller tin clack (a strike, its modes at 1.2 kHz, short, a small knock) and one bounce 60-85 ms later."""
    q = FFOLD[take - 1]
    m = q['m']
    b = buf(0.45)
    for at, g in ((0.004, 1.0), (q['bounce'], 0.35)):
        place(b, strike(r, 1800), at, 0.35 * g)
        place(b, norm(modal(1200 * m, [1, 1.59, 2.14, 2.65], [1, 0.6, 0.4, 0.2], [0.025, 0.018, 0.012, 0.008], r)), at, 0.55 * g)
        place(b, thunk(r, 220 * m, 150 * m, 0.015, 0.008), at, 0.25 * g)
    return b


FFLOP = [dict(m=1.00, bounces=(0.004, 0.13, 0.20)), dict(m=0.92, bounces=(0.004, 0.15, 0.23, 0.28))]


def flag_flop(take, r):
    """The flag failing to get up (rush_refused 13): it flops back down onto the mudguard with a limp spring 'boing'
    (a twang falling 520 -> 330 Hz, slow wobble) and two or three floppy tin taps, each smaller."""
    q = FFLOP[take - 1]
    m = q['m']
    b = buf(0.80)
    place(b, spring_twang(r, 520 * m, 330 * m, 0.11, wob=5.0, depth=0.06), 0.006, 0.40)
    for k, at in enumerate(q['bounces']):
        g = 0.5 ** k
        place(b, norm(modal(1150 * m, [1, 1.59, 2.14], [1, 0.5, 0.3], [0.020, 0.014, 0.009], r)), at, 0.50 * g)
        place(b, thunk(r, 200 * m, 140 * m, 0.014, 0.008), at, 0.20 * g)
    return b


FCLANK = [dict(m=1.00, dk=1.00), dict(m=1.10, dk=1.20), dict(m=0.88, dk=0.85)]


def flap_clank(take, r):
    """A striped exhaust flap dropping shut on its pipe (rush_end 9 and 11, rush_refused 32): a strike, the small steel
    flap (modes at 950 Hz), the pipe answering at 180 Hz, a light knock; a tiny rebound tick 30 ms on."""
    q = FCLANK[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(0.45)
    place(b, strike(r, 1700), 0.004, 0.40)
    place(b, norm(modal(950 * m, [1, 1.59, 2.14, 2.65], [1, 0.6, 0.4, 0.2], [x * dk for x in (0.030, 0.022, 0.015, 0.010)], r)), 0.004, 0.60)
    place(b, norm(modal(180 * m, [1, 2.05], [1, 0.4], [0.025, 0.015], r)), 0.004, 0.25)
    place(b, thunk(r, 180, 120, 0.020, 0.010), 0.004, 0.20)
    place(b, norm(modal(1300 * m, [1, 1.59], [1, 0.4], [0.008, 0.005], r)), 0.034, 0.12)
    return b


# ---------------------------------------------------------------- running gear

SQUEAL = [dict(m=1.00, len=0.50, wob=9.0), dict(m=1.12, len=0.42, wob=11.0), dict(m=0.90, len=0.58, wob=7.5)]


def brake_squeal(take, r):
    """The brakes biting (brake 0): the shoes meet the drums (a soft thunk and a scrape), then a stick-slip squeal near
    2.35 kHz that swells in over 30 ms, wavers at 7.5-11 Hz and dies as it stops (0.42-0.58 s), over friction noise."""
    q = SQUEAL[take - 1]
    m = q['m']
    dur = round(q['len'] + 0.40, 3)
    t = tt(dur)
    env = curve(t, [(0, 0), (0.005, 0.15), (0.035, 1.0), (q['len'] * 0.6, 0.8), (q['len'], 0.0), (dur, 0)])
    fr = 2350 * m / 6 * (1 + 0.012 * np.sin(2 * np.pi * q['wob'] * t)) * (1 - 0.03 * t)
    sq = stickslip(r, fr, 2350 * m, env, 0.15)
    fric = bandpass(noise(dur, r), 800, 5000) * env ** 0.7
    b = buf(dur)
    b += 0.75 * norm(sq) + 0.25 * norm(fric)
    place(b, thunk(r, 150 * m, 90 * m, 0.03, 0.012), 0.003, 0.25)
    return b


BHISS = [dict(m=1.00, tau=0.22), dict(m=0.90, tau=0.27), dict(m=1.10, tau=0.18)]


def brake_hiss(take, r):
    """The brakes letting off as it stops (brake 12): the valve 'tsk' and a short pneumatic 'psshh', pressure up in 15 ms,
    held 60 ms, decaying 0.18-0.27 s through a band falling from 1.8-9 kHz to 1-5 kHz."""
    q = BHISS[take - 1]
    m = q['m']
    dur = 1.40
    b = buf(dur)
    t = tt(dur)
    t0 = np.maximum(t - 0.006, 0)
    pr = (t >= 0.006) * np.clip(t0 / 0.015, 0, 1) * np.where(t0 < 0.06, 1.0, np.exp(-(t0 - 0.06) / q['tau']))
    hiss = sweep_bandpass(noise(dur, r), (1000 + 800 * pr) * m, (5000 + 4000 * pr) * m) * pr
    b += 0.85 * norm(hiss)
    place(b, norm(noise_burst(4000, 12000, 0.0015, r)), 0.003, 0.40)
    return b


SCRUB = [dict(m=1.00, rough=55.0, len=0.24), dict(m=1.12, rough=70.0, len=0.20), dict(m=0.90, rough=42.0, len=0.28)]


def tyre_scrub(take, r):
    """A tyre scrubbing round as it pivots on the spot (turn 0 and 6): rubber dragged sideways over the ground, low
    noise (150-1800 Hz) roughened at 42-70 Hz, swelling and fading over 0.20-0.28 s, with a faint rubber squeak near
    700 Hz at its middle."""
    q = SCRUB[take - 1]
    m = q['m']
    dur = round(q['len'] + 0.30, 3)
    t = tt(dur)
    env = np.where(t < q['len'], np.sin(np.pi * np.clip(t / q['len'], 0, 1)) ** 0.6, 0.0) * np.clip(t / 0.003, 0, 1)
    rough = np.clip(0.4 + 0.6 * np.abs(lowpass(noise(dur, r), q['rough'])) / 0.3, 0, 2)
    sc = bandpass(noise(dur, r), 150 * m, 1800 * m) * env * rough
    sq = stickslip(r, np.full(len(t), 700 * m / 5), 700 * m, env ** 2, 0.2)
    b = buf(dur)
    b += 0.85 * norm(sc) + 0.12 * norm(sq)
    return b


SKID = [dict(m=1.00, len=0.40), dict(m=1.10, len=0.34), dict(m=0.92, len=0.46)]


def tyre_skid(take, r):
    """The rear tyres skidding through the handbrake drift (drift 3): a rubber squeal near 1.1 kHz, jittery (the slips
    come at an irregular rate), over a broad scrub (300-3000 Hz), 0.34-0.46 s, rising in 20 ms and fading."""
    q = SKID[take - 1]
    m = q['m']
    dur = round(q['len'] + 0.35, 3)
    t = tt(dur)
    env = curve(t, [(0, 0), (0.003, 0.2), (0.02, 1.0), (q['len'] * 0.7, 0.85), (q['len'], 0.0), (dur, 0)])
    jit = 1 + 0.03 * lowpass(noise(dur, r), 25) / 0.05
    fr = 1100 * m / 4 * jit
    sq = stickslip(r, fr, 1100 * m, env, 0.25)
    sc = bandpass(noise(dur, r), 300 * m, 3000 * m) * env
    b = buf(dur)
    b += 0.65 * norm(sq) + 0.40 * norm(sc)
    return b


SCLUNK = [dict(m=1.00, dk=1.00), dict(m=1.10, dk=1.20), dict(m=0.90, dk=0.85)]


def suspension_clunk(take, r):
    """A strut bottoming over a bump (bump 1 and 4, rush_refused 11): a thunk 120 -> 60 Hz, the chassis' iron body at
    300 / 520 Hz, the rubber bump stop (a soft low knock), the spring's coils rattling 20-40 ms on."""
    q = SCLUNK[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(0.55)
    h = 0.003
    place(b, strike(r, 1100), h, 0.25)
    place(b, thunk(r, 120 * m, 60 * m, 0.05, 0.018), h, 0.65)
    place(b, norm(iron_body(r, 300 * m, 520 * m, 0.7 * dk, band_scale=0.8 * m)), h, 0.45)
    place(b, norm(lowpass(noise(0.2, r), 900) * exp_env(0.2, 0.015, 0.002)), h, 0.30)
    place(b, tooth(r, 2000 * m), 0.022, 0.12)
    place(b, tooth(r, 2150 * m), 0.038, 0.08)
    return b


SCREAK = [dict(m=1.00, len=0.30), dict(m=1.12, len=0.25), dict(m=0.90, len=0.36)]


def suspension_creak(take, r):
    """The leaf springs creaking as the body settles onto them or lifts off (boot_engine 17, shutdown 14): stick-slip
    pings near 640 Hz under a swell, 0.25-0.36 s, a soft low groan under it."""
    q = SCREAK[take - 1]
    m = q['m']
    dur = round(q['len'] + 0.20, 3)
    b = buf(dur)
    t = tt(dur)
    place(b, creak(r, q['len'], 640 * m, rate=(0.014, 0.028), decay=0.006), 0.003, 1.0)
    env = np.where(t < q['len'], np.sin(np.pi * np.clip(t / q['len'], 0, 1)), 0.0)
    b += 0.15 * norm(lowpass(osc_harm(np.full(len(t), 70.0 * m), 6, r, 1.3), 500) * env)
    return b


SRISE = [dict(m=1.00, settle=0.62), dict(m=1.10, settle=0.70)]


def suspension_rise(take, r):
    """Rising off the bump stops on its springs (build 44, the rise runs to 60): the four springs creak as they take
    the weight (a long creak near 560 Hz over 0.55 s, a low groan rising 60 -> 85 Hz under it), the body overshoots and
    the springs settle with a soft coil 'boing' (a twang 330 -> 260 Hz) and a small knock at 0.62-0.70 s."""
    q = SRISE[take - 1]
    m = q['m']
    dur = round(q['settle'] + 0.50, 3)
    b = buf(dur)
    t = tt(dur)
    place(b, creak(r, 0.55, 560 * m, rate=(0.018, 0.034), decay=0.006), 0.003, 0.8)
    s = speed_curve(t, 0.003, q['settle'])
    gr = lowpass(osc_harm(60 * m * (1 + 0.4 * np.clip(t / q['settle'], 0, 1)), 8, r, 1.3), 600) * s ** 0.7
    b += 0.30 * norm(gr)
    place(b, spring_twang(r, 330 * m, 260 * m, 0.07, wob=6.0), q['settle'], 0.30)
    place(b, thunk(r, 140, 90, 0.03, 0.012), q['settle'], 0.30)
    return b


STEER = [dict(m=1.00, teeth=5), dict(m=1.10, teeth=4), dict(m=0.90, teeth=6)]


def steer_servo(take, r):
    """The steering box turning the front wheels to lock (steer 1, boot 30): a relay click, a short servo whine (250 +
    700 s Hz) over 0.2 s, the worm gear ticking through four to six teeth, and the lock's clunk."""
    q = STEER[take - 1]
    m = q['m']
    move = 0.20
    dur = round(move + 0.35, 3)
    b = buf(dur)
    t = tt(dur)
    s = speed_curve(t, 0.008, 0.008 + move)
    b += 0.8 * motor(t, s, r, 250 * m, 700 * m, grit=0.5, air=0.15, whine=0.3, rotor_div=6)
    place(b, relay_click(r, 1900 * m), 0.003, 0.35)
    for k in range(q['teeth']):
        place(b, tooth(r, 2300 * m), 0.03 + k * (move - 0.04) / q['teeth'], 0.12)
    place(b, mix(norm(modal(520 * m, [1, 2.756, 5.404], [1, 0.5, 0.3], [0.028, 0.018, 0.012], r)), 0.6 * thunk(r, 160, 100, 0.03, 0.012)),
          0.008 + move, 0.55)
    return b


# ---------------------------------------------------------------- the binnacle ring, the jar, the turret's ram

RLATCH = [dict(m=1.00, dk=1.00), dict(m=1.08, dk=1.25), dict(m=0.92, dk=0.85)]


def ring_latch(take, r):
    """The brass binnacle ring coming down onto the lid and its catch on the right post (board 18, build 193): a strike
    at 4 ms, the closed brass ring's own ring (modes at 1.1 kHz, longer than a bar's), the post's iron body, a thunk,
    the catch snapping over (teeth at 18 and 30 ms), the bounce knocking a tick later."""
    q = RLATCH[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(0.75)
    h = 0.004
    place(b, strike(r, 1600), h, 0.50)
    place(b, norm(modal(1100 * m, [1, 1.47, 2.09, 2.56, 3.3], [1, 0.6, 0.45, 0.3, 0.2], [x * dk for x in (0.09, 0.07, 0.05, 0.035, 0.02)], r, 0.01)),
          h, 0.40)
    place(b, norm(iron_body(r, 620 * m, 990 * m, 0.6 * dk, band_scale=m)), h, 0.50)
    place(b, thunk(r, 170, 110, 0.035, 0.015), h, 0.28)
    place(b, tooth(r, 2600 * m), h + 0.014, 0.25)
    place(b, tooth(r, 2800 * m), h + 0.026, 0.20)
    place(b, norm(iron_body(r, 660 * m, 1050 * m, 0.4 * dk, band_scale=m)), 0.052, 0.18)
    return b


CREL = [dict(m=1.00, at=0.045), dict(m=1.10, at=0.060), dict(m=0.90, at=0.038)]


def catch_release(take, r):
    """The ring pressed down off its catch (board 1): the catch's pawl clacks clear at 4 ms (a strike, a tooth, the
    post's small iron body at 700 / 1100 Hz) and the ring lifts with a brief brass shimmer (its modes at 1.1 kHz, small)
    and a hinge tick 38-60 ms on."""
    q = CREL[take - 1]
    m = q['m']
    b = buf(0.55)
    place(b, strike(r, 2000 * m), 0.004, 0.40)
    place(b, tooth(r, 2500 * m), 0.004, 0.35)
    place(b, norm(iron_body(r, 700 * m, 1100 * m, 0.45, band_scale=m)), 0.004, 0.45)
    place(b, norm(modal(1100 * m, [1, 1.47, 2.09, 2.56], [1, 0.6, 0.45, 0.3], [0.06, 0.045, 0.03, 0.02], r, 0.01)), 0.010, 0.16)
    place(b, tooth(r, 2100 * m), q['at'], 0.15)
    return b


RBLOW = [dict(m=1.00), dict(m=1.10)]


def ring_blow(take, r):
    """The eject blowing the ring up and over (eject 2): a pneumatic bang at 3 ms (a strike, a blast 400-7000 Hz, a
    boom 110 -> 50 Hz), the brass ring ringing as it flies (1.1 kHz, longer), the hinge clattering at 40 and 70 ms."""
    m = RBLOW[take - 1]['m']
    b = buf(0.75)
    h = 0.003
    place(b, strike(r, 1500), h, 0.60)
    place(b, norm(noise_burst(400 * m, 7000 * m, 0.025, r, attack=0.0008)), h, 0.70)
    place(b, thunk(r, 110, 50, 0.050, 0.020), h, 0.35)
    place(b, norm(modal(1100 * m, [1, 1.47, 2.09, 2.56], [1, 0.6, 0.45, 0.3], [0.08, 0.06, 0.04, 0.03], r, 0.01)), h, 0.35)
    place(b, tooth(r, 2200 * m), 0.040, 0.18)
    place(b, tooth(r, 2350 * m), 0.070, 0.12)
    return b


RCLANK = [dict(m=1.00, dk=1.00), dict(m=1.12, dk=1.20)]


def ring_clank(take, r):
    """The blown ring falling back to its stop on the hinge (eject 7): a strike, the brass ring and the post's iron
    (1.1 kHz modes, 620 / 990 Hz body), a thunk, and a small settle knock 100 ms on."""
    q = RCLANK[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(0.60)
    place(b, strike(r, 1500), 0.004, 0.45)
    place(b, norm(modal(1100 * m, [1, 1.47, 2.09, 2.56], [1, 0.6, 0.45, 0.3], [x * dk for x in (0.06, 0.045, 0.03, 0.02)], r, 0.01)),
          0.004, 0.35)
    place(b, norm(iron_body(r, 620 * m, 990 * m, 0.65 * dk, band_scale=m)), 0.004, 0.50)
    place(b, thunk(r, 140, 90, 0.03, 0.015), 0.004, 0.30)
    place(b, norm(modal(720 * m, [1, 2.756], [1, 0.4], [0.02, 0.012], r)), 0.100, 0.15)
    return b


JSEAT = [dict(m=1.00, dk=1.00), dict(m=1.12, dk=1.20), dict(m=0.90, dk=0.85)]


def jar_seat(take, r):
    """The jar lowered into the Wheelhouse's brass collar (board 11): a thick glass base meeting brass at 4 ms (a
    strike, low glass modes at 1.2 kHz, short), the collar's ring (1.75 kHz), a little thunk, the 0.4 px bounce
    knocking at 60 and 95 ms. A seat, not a smash."""
    q = JSEAT[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(0.50)

    def knock():
        return mix(0.35 * strike(r, 2400), 0.45 * norm(glass_modes(r, 1200 * m, decays=(0.05 * dk, 0.03 * dk, 0.018 * dk, 0.010 * dk))),
                   0.32 * norm(modal(1750 * m, [1, 2.4, 3.8], [1, 0.4, 0.2], [0.035, 0.022, 0.013], r)),
                   0.26 * thunk(r, 200, 130, 0.03, 0.015), 0.20 * norm(noise_burst(400, 2500, 0.010, r)))
    place(b, norm(knock()), 0.004, 1.0)
    place(b, norm(knock()), 0.060, 0.28)
    place(b, norm(knock()), 0.095, 0.09)
    return b


RAMS = [dict(m=1.00, len=0.10), dict(m=1.10, len=0.08), dict(m=0.90, len=0.12)]


def ram_stroke(take, r):
    """The turret's ram stroking out 2 px and back (boot 28): a valve 'tsk', the ram sliding (a short 'shff' of air and
    rod, 0.08-0.12 s) and its stop clacking (a strike, steel modes at 800 Hz, a light knock)."""
    q = RAMS[take - 1]
    m = q['m']
    dur = round(q['len'] + 0.30, 3)
    b = buf(dur)
    t = tt(dur)
    s = speed_curve(t, 0.003, 0.003 + q['len'])
    b += 0.45 * norm(bandpass(noise(dur, r), 600 * m, 4000 * m) * s)
    place(b, norm(noise_burst(3000, 10000, 0.010, r, attack=0.002)), 0.003, 0.25)
    at = 0.003 + q['len']
    place(b, strike(r, 1800), at, 0.35)
    place(b, norm(modal(800 * m, [1, 2.756, 5.404], [1, 0.5, 0.3], [0.025, 0.016, 0.010], r)), at, 0.55)
    place(b, thunk(r, 180, 120, 0.02, 0.01), at, 0.25)
    return b


# ---------------------------------------------------------------- the wreck, the horn, the beeper, the cooling engine

WPOP = [dict(m=1.00, bounce=0.14), dict(m=1.10, bounce=0.17), dict(m=0.92, bounce=0.12)]


def wheel_pop(take, r):
    """A whole wheel station coming off (destroyed 24): the stub axle's bolts shear at 3 ms (a sharp strike, a bright
    steel ping at 3.1 kHz, a spring-steel snap), the hub's iron body and a thunk as it pops out, and the tyre's first
    rubbery bounce 120-170 ms later."""
    q = WPOP[take - 1]
    m = q['m']
    b = buf(0.75)
    h = 0.003
    place(b, strike(r, 2200), h, 0.55)
    place(b, norm(modal(3100 * m, [1, 1.52, 2.3], [1, 0.5, 0.25], [0.030, 0.020, 0.012], r)), h, 0.30)
    place(b, spring_twang(r, 1300 * m, 1000 * m, 0.03, wob=13.0), h, 0.20)
    place(b, norm(iron_body(r, 420 * m, 700 * m, 0.8, band_scale=0.9 * m)), h, 0.50)
    place(b, thunk(r, 140 * m, 70 * m, 0.04, 0.015), h, 0.45)
    place(b, mix(thunk(r, 130 * m, 80 * m, 0.04, 0.015), 0.4 * norm(lowpass(noise(0.2, r), 800) * exp_env(0.2, 0.015, 0.002))),
          q['bounce'], 0.40)
    return b


WCLATTER = [dict(m=1.00, bounces=(0.003, 0.16, 0.27, 0.35, 0.41), wob=(14.0, 26.0)),
            dict(m=1.08, bounces=(0.003, 0.13, 0.23, 0.30), wob=(16.0, 30.0)),
            dict(m=0.93, bounces=(0.003, 0.18, 0.31, 0.40, 0.46, 0.50), wob=(12.0, 24.0))]


def wheel_clatter(take, r):
    """The loose wheel landing flat after its roll (destroyed 36): the tyre's rubber thud and the mudguard's and lamp's
    tin clatter at 3 ms, bouncing four to six times closer and smaller, then spinning down on its side like a coin
    (a low rubber 'wub' whose rate climbs from 12-16 to 24-30 a second as it settles)."""
    q = WCLATTER[take - 1]
    m = q['m']
    dur = 1.30
    b = buf(dur)
    t = tt(dur)
    for k, at in enumerate(q['bounces']):
        g = 0.62 ** k
        place(b, thunk(r, 130 * m, 70 * m, 0.04, 0.015), at, 0.60 * g)
        place(b, norm(modal(1250 * m * (1 + 0.04 * k), [1, 1.59, 2.14, 2.65], [1, 0.6, 0.4, 0.2], [0.03, 0.02, 0.014, 0.009], r)), at, 0.35 * g)
        place(b, strike(r, 1500), at, 0.25 * g)
    t0 = q['bounces'][-1]
    u = np.clip((t - t0) / 0.45, 0, 1)
    wf = q['wob'][0] + (q['wob'][1] - q['wob'][0]) * u
    wub = (0.5 + 0.5 * np.cos(2 * np.pi * np.cumsum(wf) / SR)) ** 3
    env = (t > t0) * np.clip((t - t0) / 0.03, 0, 1) * np.where(u < 1, (1 - u) ** 1.5, 0.0)
    b += 0.25 * norm(lowpass(noise(dur, r), 600) * wub * env + 1e-9)
    return b


def horn(take, r):
    """The Wheelhouse's horn, beep-beep, proud (boot 37 and wherever the code sounds it): two notes sounding together,
    C5 and E5 (the company motif's first two notes), each a buzzy band-limited sawtooth through a horn's 700-1600 Hz
    resonance, the diaphragm starting 3 % flat for 20 ms. A short beep (0.13 s), 70 ms gap, a longer one (0.26 s).
    Low-passed at 3.5 kHz: brisk, not shrill."""
    dur = 0.90
    b = buf(dur)
    for start, length in ((0.003, 0.13), (0.20, 0.26)):
        d = length + 0.15
        t = tt(d)
        env = np.clip(t / 0.008, 0, 1) * np.where(t < length, 1.0, np.exp(-(t - length) / 0.015))
        x = np.zeros(len(t))
        for f0, g in ((523.25, 1.0), (659.26, 0.85)):
            f = f0 * (1 - 0.03 * np.exp(-t / 0.02))
            x += g * osc_harm(f, 12, r, 0.9)
        x = bandpass(x, 300, 3200) + 0.7 * bandpass(x, 700, 1600)
        x *= 1 - 0.15 * (0.5 + 0.5 * np.cos(2 * np.pi * 30 * t))
        place(b, norm(x * env), start, 1.0)
    return lowpass(b, 3500, order=4)


def reverse_beep(take, r):
    """The reversing beeper (code, once a second while reversing, so it keeps time at any speed): one plain beep at C6
    (1046.5 Hz, odd harmonics, rounded), 0.20 s, low-passed at 4 kHz."""
    b = buf(0.40)
    place(b, beep(1046.5, 0.20, r), 0.003)
    return lowpass(b, 4000)


COOL = [dict(m=1.00), dict(m=1.15), dict(m=0.88)]


def cooling_tick(take, r):
    """The parked engine cooling (parked 30 and 66), very quiet: one small tick of hot metal contracting, a bright
    short ping (2.6 kHz) with a faint lower knock; low-passed."""
    m = COOL[take - 1]['m']
    b = buf(0.20)
    place(b, norm(modal(2600 * m, [1, 1.52, 2.4], [1, 0.4, 0.15], [0.010, 0.006, 0.004], r)), 0.003, 0.7)
    place(b, norm(modal(900 * m, [1, 2.3], [1, 0.3], [0.008, 0.005], r)), 0.003, 0.25)
    return lowpass(b, 6000)


# ---------------------------------------------------------------- references (measured as the game plays them)

MINECART = dict(event='entity.minecart.riding', file='minecart__base.ogg', vol=0.25, pitch=1.0)
RIPTIDE = dict(event='item.trident.riptide_1', file='item__trident__riptide1.ogg', vol=1.0, pitch=1.0)
COPPER_DOOR = dict(event='block.copper_door.open', file='block__copper_door__toggle1.ogg', vol=1.0, pitch=1.0)
NETHERITE_STEP = dict(event='block.netherite_block.step', file='block__netherite__step1.ogg', vol=1.0, pitch=1.0)
WARDEN_STEP = dict(event='entity.warden.step', file='mob__warden__step_1.ogg', vol=1.0, pitch=1.0)
BREEZE_JUMP = dict(event='entity.breeze.jump', file='mob__breeze__jump1.ogg', vol=1.0, pitch=1.0)
BULB = dict(event='block.copper_bulb.turn_on', file='block__copper_bulb__toggle.ogg', vol=1.0, pitch=1.0)
WOOD_TRAPDOOR = dict(event='block.wooden_trapdoor.open', file='block__wooden_trapdoor__open1.ogg', vol=0.9, pitch=1.0)

BASE = dict(category='NEUTRAL', attenuation=16, fixed_range=False, volume=1.0)
KEY_NOTE = ' Keyframe volume 1.0 and pitch 1.0 (the file carries the loudness).'


def ev(name, **kw):
    d = dict(BASE)
    d.update(id=f'mecha.wheelhouse.{name}', file=f'mecha/wheelhouse/{name}')
    d.update(kw)
    return d


SOUNDS = [
    # ---- the engine
    ev('motor', fn=motor_loop, finish=finish_loop, takes=1, target=-26.0, cls='hum, motor (loop at running speed)',
       kind=('loop', 2.0), fade_ms=0, hit_ms=None, ref=BEACON_AMBIENT, pitch=[0.97, 1.03], attenuation=16,
       subtitle_en=None, subtitle_pt=None,
       play='LOOP, as mecha.motor: one client tickable instance per running Wheelhouse looping this 2.0 s file (seamless: no fades in it; 30 firings a second at pitch 1.0). Pitch = (0.8 + 0.4 s) x a fixed per-instance offset 0.97-1.03, so it idles at 0.8 (24 firings a second) and runs flat out at 1.2; volume = 0.35 + 0.65 sqrt(s) while the engine runs (it idles audibly), 0 once it is stopped; chase both 0.2 a tick (pitch at most 0.05 a tick). No subtitle (the engine moments carry it).'),
    ev('engine_rev', fn=engine_rev, takes=3, target=-22.5, cls='engine (throttle blip)', kind=('servo', 0.95),
       fade_ms=30, hit_ms=3, ref=BREEZE_JUMP, pitch=[0.97, 1.03], attenuation=20,
       subtitle_en='Engine revs', subtitle_pt='Motor ronca',
       play='idle_tic 4, drive_start 0, rush_windup 0, rush_refused 0, over the running loop. The throttle snaps at 3 ms; the crackle lands 0.48-0.71 s in.' + KEY_NOTE),
    ev('engine_catch', fn=engine_catch, takes=3, target=-22.0, cls='engine (catching)', kind=('servo', 1.0),
       fade_ms=30, hit_ms=3, ref=BREEZE_JUMP, pitch=[0.97, 1.03], attenuation=20,
       subtitle_en='Engine starts', subtitle_pt='Motor pega',
       play='boot_engine 9: the first firing on the key, stumbling for 0.1-0.2 s, then running and fading by 1.05 s as the loop (started on the same tick from volume 0) takes over.' + KEY_NOTE),
    ev('starter_whirr', fn=starter_whirr, takes=3, target=-25.5, cls='servo (starter motor)', kind=('servo', 0.40),
       fade_ms=25, hit_ms=3, ref=PISTON_IN, pitch=[1.0, 1.0], subtitle_en='Starter cranks', subtitle_pt='Arranque gira',
       play='boot_engine 2: cranks for 0.34-0.40 s and lets go by tick 9 (the catch).' + KEY_NOTE),
    ev('engine_stop', fn=engine_stop, takes=2, target=-23.5, cls='engine (switched off)', kind=('servo', 0.75),
       fade_ms=30, hit_ms=3, ref=WARDEN_STEP, pitch=[0.97, 1.03], subtitle_en='Engine stops', subtitle_pt='Motor desliga',
       play='shutdown 4; stop the loop on the same tick. The last firing at 0.60-0.66 s, the rock back 40 ms after.' + KEY_NOTE),
    ev('engine_misfire', fn=engine_misfire, takes=3, target=-24.0, cls='engine (misfire)', kind='latch', fade_ms=15,
       hit_ms=3, ref=WARDEN_STEP, pitch=[0.95, 1.05], subtitle_en='Engine misfires', subtitle_pt='Motor falha',
       play='limp 0 and 16 (twice a cycle), over the loop. The cough lands 105-150 ms in, with the 1.2-degree jerk.' + KEY_NOTE),
    ev('backfire_pop', fn=backfire_pop, takes=3, target=-19.5, cls='slam (bang)', kind='latch', fade_ms=20, hit_ms=3,
       ref=FIREWORK, pitch=[0.95, 1.05], attenuation=24, subtitle_en='Engine backfires', subtitle_pt='Escapamento estoura',
       play='rush_refused 8, with the black puff: the bang on the key, the flaps slapped open 28-75 ms on.' + KEY_NOTE),
    ev('exhaust_pop', fn=exhaust_pop, takes=3, target=-24.5, cls='slam (small pop)', kind='latch', fade_ms=12,
       hit_ms=3, ref=FIREWORK, pitch=[0.95, 1.05], subtitle_en=None, subtitle_pt=None,
       play='rush_end 16, the last puff from the left pipe. No subtitle (rush_wind_down carries it).' + KEY_NOTE),
    # ---- Rush Hour
    ev('rush_go', fn=rush_go, takes=2, target=-18.0, cls='signature moment (the boost)', kind=('servo', 1.1),
       fade_ms=40, hit_ms=3, ref=RIPTIDE, pitch=[0.98, 1.02], attenuation=32,
       subtitle_en='Rush Hour: right of way', subtitle_pt='Hora do Rush: preferencial',
       play='Server on rush:go (rush_windup tick 12, the boost starts), at the Wheelhouse, with the particles, the FOV and the HUD line. Both pipes fire on the tick (3 ms); the wind-up runs to about 0.9 s and hands over to the loop (now at the boost\'s pitch) by 1.3 s.'),
    ev('rush_ram', fn=rush_ram, takes=3, target=-16.0, cls='heavy impact (the ram)', kind=('step', 0.70), fade_ms=30,
       hit_ms=3, ref=GOLEM_HURT, pitch=[0.95, 1.05], attenuation=32,
       subtitle_en='Wheelhouse rams', subtitle_pt='Rebocador abalroa',
       play='Server on rush:ram (rush_ram tick 0, the contact), at fx_buffer, with the dust and the camera trauma: the clang on the tick, the buffers 15-35 ms on, the ring rattling to 0.28 s.'),
    ev('rush_refused', fn=rush_refused, takes=2, target=-25.0, cls='servo (starter fails)', kind=('servo', 0.70),
       fade_ms=25, hit_ms=3, ref=PISTON_IN, pitch=[1.0, 1.0], attenuation=20,
       subtitle_en='Starter fails', subtitle_pt='Arranque não pega',
       play='Server on rush:refused (rush_refused tick 10), with the HUD remedy line. It is the refusal\'s sound: play it on every refused press, never skip it. The starter stalls at 0.58-0.62 s and the solenoid drops out.'),
    ev('rush_wind_down', fn=rush_wind_down, takes=2, target=-23.0, cls='engine (letting off)', kind=('servo', 1.05),
       fade_ms=30, hit_ms=3, ref=BREEZE_JUMP, pitch=[0.97, 1.03], subtitle_en='Rush Hour ends', subtitle_pt='Fim da Hora do Rush',
       play='rush_end 0, over the loop (which chases back down to its drive pitch at the same time).' + KEY_NOTE),
    ev('flag_snap', fn=flag_snap, takes=3, target=-23.0, cls='latch (flag up)', kind='latch', fade_ms=12, hit_ms=3,
       ref=TRAPDOOR_CLOSE, pitch=[0.95, 1.05], subtitle_en='Flag snaps up', subtitle_pt='Bandeira se ergue',
       play='rush_windup 8: the catch on the key, the plate hits upright 9 ms later.' + KEY_NOTE),
    ev('flag_fold', fn=flag_fold, takes=3, target=-25.0, cls='latch (flag down)', kind='latch', fade_ms=12, hit_ms=4,
       ref=COPPER_TRAPDOOR, pitch=[0.95, 1.05], subtitle_en='Flag folds', subtitle_pt='Bandeira abaixa',
       play='rush_end 7 (the landing); the bounce 60-85 ms on.' + KEY_NOTE),
    ev('flag_flop', fn=flag_flop, takes=2, target=-26.0, cls='latch (flag flops)', kind=('flop', 0.38), fade_ms=20,
       hit_ms=4, ref=COPPER_TRAPDOOR, pitch=[0.95, 1.05], subtitle_en=None, subtitle_pt=None,
       play='rush_refused 13. No subtitle (rush_refused carries it).' + KEY_NOTE),
    ev('flap_clank', fn=flap_clank, takes=3, target=-24.0, cls='slam (exhaust flap)', kind='latch', fade_ms=12,
       hit_ms=4, ref=TRAPDOOR_CLOSE, pitch=[0.95, 1.05], subtitle_en=None, subtitle_pt=None,
       play='rush_end 9 and 11 (a different take each), rush_refused 32. No subtitle (small).' + KEY_NOTE),
    ev('buffer_spring', fn=buffer_spring, takes=3, target=-25.0, cls='servo (spring buffers)', kind='latch',
       fade_ms=20, hit_ms=4, ref=EQUIP_CHAIN, pitch=[0.95, 1.05], subtitle_en=None, subtitle_pt=None,
       play='rush_ram 2. No subtitle (rush_ram carries it).' + KEY_NOTE),
    # ---- running gear
    ev('brake_squeal', fn=brake_squeal, takes=3, target=-25.5, cls='squeal (brakes)', kind=('servo', 0.50),
       fade_ms=25, hit_ms=3, ref=COPPER_DOOR, pitch=[0.95, 1.05], attenuation=20,
       subtitle_en='Brakes squeal', subtitle_pt='Freios rangem',
       play='brake 0: the shoes bite on the key, the squeal dies by 0.42-0.58 s.' + KEY_NOTE),
    ev('brake_hiss', fn=brake_hiss, takes=3, target=-25.0, cls='hiss (air brakes)', kind='hiss', fade_ms=40,
       hit_ms=3, ref=FIZZ, pitch=[0.95, 1.05], subtitle_en='Brakes hiss', subtitle_pt='Freios chiam',
       play='brake 12 ("brakes hiss when it stops").' + KEY_NOTE),
    ev('tyre_scrub', fn=tyre_scrub, takes=3, target=-28.0, cls='scrape (tyre, servo class)', kind=('step', 0.22),
       fade_ms=15, hit_ms=3, ref=NETHERITE_STEP, pitch=[0.94, 1.06], subtitle_en='Tyres scrub', subtitle_pt='Pneus arrastam',
       play='turn 0 and 6 (every 0.3 s while it pivots); a different take each time.' + KEY_NOTE),
    ev('tyre_skid', fn=tyre_skid, takes=3, target=-25.0, cls='squeal (tyres)', kind=('step', 0.40), fade_ms=25,
       hit_ms=3, ref=COPPER_DOOR, pitch=[0.95, 1.05], attenuation=20, subtitle_en='Tyres skid', subtitle_pt='Pneus derrapam',
       play='drift 3 (every 0.4 s while drifting); a different take each time.' + KEY_NOTE),
    ev('suspension_clunk', fn=suspension_clunk, takes=3, target=-24.0, cls='clamp (strut bottoms)', kind='latch',
       fade_ms=12, hit_ms=3, ref=TRAPDOOR_CLOSE, pitch=[0.94, 1.06], subtitle_en='Suspension clunks',
       subtitle_pt='Suspensão bate',
       play='bump 1 and 4, rush_refused 11.' + KEY_NOTE),
    ev('suspension_creak', fn=suspension_creak, takes=3, target=-28.0, cls='servo (springs creak)', kind=('servo', 0.30),
       fade_ms=20, hit_ms=3, ref=EQUIP_IRON, pitch=[0.95, 1.05], subtitle_en=None, subtitle_pt=None,
       play='boot_engine 17, shutdown 14. No subtitle (small life).' + KEY_NOTE),
    ev('suspension_rise', fn=suspension_rise, takes=2, target=-27.0, cls='servo (rising on springs)', kind=('servo', 0.75),
       fade_ms=25, hit_ms=3, ref=PISTON_IN, pitch=[0.98, 1.02], subtitle_en='Wheelhouse rises', subtitle_pt='Rebocador se ergue',
       play='build 44 (the rise runs to 60): the settle lands at 0.62-0.70 s (tick 56-58, the heft drop).' + KEY_NOTE),
    ev('steer_servo', fn=steer_servo, takes=3, target=-28.0, cls='servo (steering box)', kind=('servo', 0.22),
       fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[0.95, 1.05], subtitle_en=None, subtitle_pt=None,
       play='steer_l/r 1, boot 30. No subtitle (frequent and small).' + KEY_NOTE),
    # ---- the ring, the jar, the ram
    ev('ring_latch', fn=ring_latch, takes=3, target=-22.0, cls='clamp (latch)', kind='latch', fade_ms=15, hit_ms=4,
       ref=TRAPDOOR_CLOSE, pitch=[0.95, 1.05], subtitle_en='Binnacle ring latches', subtitle_pt='Anel da bitácula trava',
       play='board 18, build 193: the catch snaps over 14-26 ms after the contact.' + KEY_NOTE),
    ev('catch_release', fn=catch_release, takes=3, target=-24.0, cls='clamp release', kind='latch', fade_ms=12,
       hit_ms=4, ref=DOOR_OPEN, pitch=[0.95, 1.05], subtitle_en='Ring unlatches', subtitle_pt='Anel destrava',
       play='board 1.' + KEY_NOTE),
    ev('ring_blow', fn=ring_blow, takes=2, target=-20.5, cls='slam (bang)', kind='latch', fade_ms=15, hit_ms=3,
       ref=FIREWORK, pitch=[0.95, 1.05], attenuation=24, subtitle_en='Ring blows open', subtitle_pt='Anel abre com estouro',
       play='eject 2.' + KEY_NOTE),
    ev('ring_clank', fn=ring_clank, takes=2, target=-23.0, cls='slam (stop)', kind='latch', fade_ms=12, hit_ms=4,
       ref=TRAPDOOR_CLOSE, pitch=[0.95, 1.05], subtitle_en=None, subtitle_pt=None,
       play='eject 7. No subtitle (ring_blow carries it).' + KEY_NOTE),
    ev('jar_seat', fn=jar_seat, takes=3, target=-26.5, cls='glass clink', kind='latch', fade_ms=12, hit_ms=4,
       ref=LANTERN, pitch=[0.95, 1.05], subtitle_en='Jar seats', subtitle_pt='Pote se encaixa',
       play='board 11; the bounce knocks at 60 and 95 ms.' + KEY_NOTE),
    ev('ram_stroke', fn=ram_stroke, takes=3, target=-28.0, cls='servo (ram)', kind='latch', fade_ms=12, hit_ms=3,
       ref=PISTON_OUT, pitch=[0.95, 1.05], subtitle_en=None, subtitle_pt=None,
       play='boot 28. No subtitle (part of the self-test).' + KEY_NOTE),
    # ---- the wreck
    ev('wheel_pop', fn=wheel_pop, takes=3, target=-21.0, cls='slam (wheel comes off)', kind='latch', fade_ms=15,
       hit_ms=3, ref=DOOR_CLOSE, pitch=[0.95, 1.05], attenuation=24, subtitle_en='Wheel comes off', subtitle_pt='Roda se solta',
       play='destroyed 24.' + KEY_NOTE),
    ev('wheel_clatter', fn=wheel_clatter, takes=3, target=-22.0, cls='landing (loose wheel)', kind=('step', 0.85),
       fade_ms=30, hit_ms=3, ref=DOOR_CLOSE, pitch=[0.95, 1.05], attenuation=24, subtitle_en='Wheel clatters',
       subtitle_pt='Roda chacoalha',
       play='destroyed 36 (the contact).' + KEY_NOTE),
    # ---- the horn, the beeper, the parked engine
    ev('horn', fn=horn, takes=1, target=-22.0, cls='alarm (horn)', kind=('stinger', 0.50), fade_ms=30, hit_ms=3,
       ref=DOOR_CLOSE, pitch=[1.0, 1.0], attenuation=48, fixed_range=True,
       subtitle_en='Horn: beep-beep', subtitle_pt='Buzina: bi-bi',
       play='boot 37 and wherever the code sounds the horn; played on the moment itself (hit at 3 ms). Fixed pitch (C5 and E5 together). Register fixed-range 48 (createFixedRangeEvent).'),
    ev('reverse_beep', fn=reverse_beep, takes=1, target=-26.5, cls='warning (reversing beeper)', kind='latch',
       fade_ms=15, hit_ms=3, ref=BELL, pitch=[1.0, 1.0], subtitle_en='Wheelhouse reverses', subtitle_pt='Rebocador dá ré',
       play='Code, once every 20 ticks while reversing (not a keyframe, so it keeps time at any speed). Fixed pitch (C6).'),
    ev('cooling_tick', fn=cooling_tick, takes=3, target=-37.0, cls='idle machine life', kind='click', fade_ms=8,
       hit_ms=3, ref=BULB, pitch=[0.92, 1.08], subtitle_en=None, subtitle_pt=None,
       play='parked 30 and 66, a different take each. No subtitle (very quiet life).' + KEY_NOTE),
]

"""The Separation Chair (DOLORES): every sound of its operation, as a bystander hears it, and of its smaller clips.

The operation is 240 ticks (12 s). The patient sits; a yellow level-crossing barrier drops across the chest and latches;
a dryer dome on a two-stage mast nods and breathes gas from under its rim; DOLORES counts down; a scan ring slides down
over the face and back; a latch goes (the point of no return); the blade behind the neck winds up while a cloth privacy
curtain whips up on its brass rail; silence; the cut; the head hops a pixel; a polite ding. Then the jar's tray is
already out beside the chair: its lid pops open, the temple jaws take the head, the rail telescopes up and holds, the
dome swings 180 degrees round the mast, lowers, and lets the head drop into the saline with a plop; the saline pours,
the lid slams, the dome swings home, the curtain comes up again while the Collar Cam is bolted on with three ratchet
clicks, sparks jump from the dome's cap during the neural handshake, the lamp turns green, the barrier unlatches and
lifts. Smaller clips: the barrier taps while waiting, the tray slides out and in, the botched transfer hiccups and
squeaks, an abort deflates everything, and aftercare puffs the vents and bows.

One recipe with parameters serves several beats: the clamp makes the barrier's latch and its settle; the hydraulic makes
the barrier's drop and lift and the rail's lifts and lowers; the servo makes every motor move at that move's length in
ticks. Times inside a file are seconds; one tick is 50 ms. A file whose hit is not at its start says so in `hit_ms`,
and `preroll_ticks` is how many ticks before the contact it must start. Takes differ by construction (a per-take table
of frequencies, decays and layer balances), not by luck of the seed.
"""
import numpy as np
from nsynth import *

T = TICK
TAKE_M = [1.00, 1.10, 0.90]  # per-take frequency scale (modes and noise bands together)
TAKE_D = [1.00, 1.25, 0.80]  # per-take decay scale, so the takes also differ in length


# ---------------------------------------------------------------- clamp family: barrier latch and settle, jaws, unlatch

LATCH = {
    'latch': dict(f0=600.0, plate=960.0, tooth=2700.0, th=(150, 95), decay=0.80, teeth=2),
    'settle': dict(f0=500.0, plate=800.0, tooth=2450.0, th=(135, 82), decay=0.70, teeth=1),
}


def barrier_clamp(take, r, which):
    """Clamp: ratchet teeth as the barrier's tip rides over the pawl on the mast, then at exactly 100 ms the strike, a
    short iron body, a metallic noise band, a small thunk and a low-passed tail. 'settle' is the bounce falling back
    onto the latch: two semitones lower, one tooth, shorter."""
    p = LATCH[which]
    m = TAKE_M[take - 1]
    dk = [1.00, 1.15, 0.85][take - 1]  # gentler than TAKE_D: a clamp must stay inside 0.40 s
    b = buf(0.68)
    hit = 2 * T
    at_list = [0.003, 0.049 + r.uniform(-0.004, 0.004)][:p['teeth']]
    for k, at in enumerate(at_list):
        place(b, tooth(r, p['tooth'] * m * (1 + 0.05 * k)), at, 0.20)
    place(b, strike(r, 1500), hit, 0.60)
    place(b, norm(iron_body(r, p['f0'] * m, p['plate'] * m, p['decay'] * dk, band_scale=m)), hit, 0.62)
    place(b, thunk(r, *p['th']), hit, 0.30)
    place(b, norm(lowpass(noise(0.56, r), 1200) * exp_env(0.56, 0.08, 0.002)), hit, 0.05)
    return b


def claw_clamp(take, r):
    """The temple jaws closing on the head (into contact): a padded strike at 4 ms, a short small body, a rubber thud,
    then a solenoid tooth 60 ms later as they lock. Nothing wet."""
    m = TAKE_M[take - 1]
    dk = [1.00, 1.25, 1.10][take - 1]
    b = buf(0.45)
    hit = 0.004
    place(b, norm(highpass(noise(0.03, r), 1200 * m) * exp_env(0.03, 0.003)), hit, 0.45)
    place(b, norm(modal(760 * m, [1, 2.756, 5.404], [1, 0.5, 0.3], [0.030 * dk, 0.022 * dk, 0.012 * dk], r, 0.01)), hit, 0.45)
    place(b, norm(noise_burst(1000 * m, 6000 * m, 0.020, r)), hit, 0.38)
    place(b, thunk(r, 220, 140, 0.030, 0.015), hit, 0.20)
    place(b, tooth(r, 2300 * m), hit + 0.060, 0.16)
    return b


def unlatch(take, r):
    """Clamp release: a short hiss puff as the latch vents, a lighter strike at 50 ms, the iron body x1.12 with decays
    x0.7, no teeth."""
    m = TAKE_M[take - 1]
    dk = TAKE_D[take - 1]
    b = buf(0.50)
    place(b, norm(noise_burst(2000 * m, 8000 * m, 0.040 * dk, r, attack=0.005)), 0.003, 0.45)
    hit = T
    place(b, strike(r, 1800 * m), hit, 0.42)
    place(b, norm(iron_body(r, 560 * 1.12 * m, 900 * 1.12 * m, 0.7 * dk, band_scale=m)), hit, 0.60)
    place(b, thunk(r, 160, 110, 0.03, 0.015), hit, 0.14)
    return b


def latch(take, r):
    """The point of no return: a relay pulls in and a small bolt shoots home: a click, then 15 ms later a short steel
    knock (strike, a small iron body at 900 Hz with short decays, a light thunk)."""
    m, dk = [(1.00, 1.0), (1.12, 1.4)][take - 1]
    b = buf(0.40)
    place(b, relay_click(r, 1900 * m), 0.003, 0.45)
    place(b, strike(r, 2000 * m), 0.018, 0.40)
    place(b, norm(iron_body(r, 900 * m, 1400 * m, 0.45 * dk, band_scale=m)), 0.018, 0.55)
    place(b, thunk(r, 180, 120, 0.02, 0.01), 0.018, 0.20)
    return b


# ---------------------------------------------------------------- hydraulic: barrier drop and lift, rail lifts and lowers

HYD_AIR = [0.42, 0.60, 0.40]


def hydraulic(take, r, s0, s1, curve='sin2', stop_gain=0.9, valve=0.25, puff=False, settle=None):
    """Hydraulic stroke: valve hiss at 3 ms, then from s0 to s1 (s) one speed curve drives a low-passed whoosh whose
    cutoff rises with speed, pneumatic air and a 70-110 Hz groan with 4 Hz vibrato; an end-stop (iron body, thunk,
    strike) at s1 unless stop_gain is 0; a vent puff after it if asked; a soft settle knock at `settle` (s) if given
    (the overshoot coming to rest). curve 'in' accelerates into s1 (a drop into contact) and lets go in 25 ms."""
    m = TAKE_M[take - 1]
    dur = round(max(s1, settle or 0) + 0.42, 3)
    b = buf(dur)
    t = tt(dur)
    if curve == 'in':
        u = np.clip((t - s0) / (s1 - s0), 0, 1)
        s = np.where(t < s1, u ** 2, np.exp(-(t - s1) / 0.025)) * (t > s0)
    else:
        s = speed_curve(t, s0, s1)
    whoosh = sweep_bandpass(noise(dur, r), np.full(len(t), 60.0), 300 + 900 * s) * s
    air = bandpass(noise(dur, r), 800 * m, 5000 * m) * s ** 1.5
    fg = 80 * m * (1 + 0.25 * s) * (1 + 0.03 * np.sin(2 * np.pi * 4 * t))
    groan = np.sin(2 * np.pi * np.cumsum(fg) / SR) * s ** 0.5
    b += 0.50 * norm(whoosh) + HYD_AIR[take - 1] * norm(air) + 0.22 * norm(groan)
    place(b, norm(noise_burst(3000, 10000, 0.050, r, attack=0.008)), 0.003, valve * [1.0, 0.8, 1.6][take - 1])  # the valve opens
    if stop_gain:
        stop = mix(0.45 * norm(iron_body(r, 380 * m, 620 * m, 0.8, band_scale=m)), 0.55 * thunk(r, 140, 80, 0.05, 0.02),
                   0.35 * strike(r, 1500))
        place(b, stop, s1, stop_gain)
    if puff:
        place(b, norm(noise_burst(1500, 7000, 0.040, r, attack=0.008)), s1 + 0.012, 0.30)
    if settle:
        place(b, mix(norm(modal(520 * m, [1, 2.756], [1, 0.5], [0.025, 0.015], r)), 0.6 * thunk(r, 120, 90, 0.03, 0.015)),
              settle, 0.25 * stop_gain)
    return b


# ---------------------------------------------------------------- servo: every motor move

SERVO = {
    'large': dict(base=160.0, top=520.0, clunk=430.0, click=1500.0),  # the dome swinging round the mast
    'small': dict(base=260.0, top=800.0, clunk=640.0, click=2000.0),  # the barrier's motor
}
SERVO_TAKES = [dict(m=1.00, grit=0.42, air=0.14, cd=1.0), dict(m=1.08, grit=0.54, air=0.24, cd=1.25),
               dict(m=0.92, grit=0.32, air=0.09, cd=0.8)]


def servo(take, r, size, move_ticks, pre_ticks=0, settle_ticks=None, clunk_gain=1.0):
    """Servo move (whine kept low against grit and air, so it reads as a motor): relay click at 3 ms, the motor (whine
    'base + top x s', grit gated by the rotor, air) under s = sin^2 across exactly move_ticks, the stop clunk on the
    last tick of the move, a settle tick 45 ms later or, with settle_ticks, that many ticks after the stop (an
    overshoot coming to rest). pre_ticks puts a small wind-back bump (speed 0.3) before the main move."""
    p = SERVO[size]
    q = SERVO_TAKES[take - 1]
    m = q['m']
    move = move_ticks * T
    start = 0.010 + pre_ticks * T
    stop = start + move
    settle_at = stop + (settle_ticks * T if settle_ticks else 0.045)
    dur = round(max(stop + 0.39, settle_at + 0.12), 3)  # the stop clunk rings 0.4 s to -60 dB
    b = buf(dur)
    t = tt(dur)
    s = speed_curve(t, start, stop)
    if pre_ticks:
        s = s + 0.3 * speed_curve(t, 0.010, start)
    b += motor(t, s, r, p['base'] * m, p['top'] * m, grit=q['grit'], air=q['air'], whine=0.28)
    place(b, relay_click(r, p['click'] * m), 0.003, 0.35)
    cd = q['cd']
    cl = mix(0.35 * norm(modal(p['clunk'] * m, [1, 2.756, 5.404], [1, 0.6, 0.4], [0.045 * cd, 0.03 * cd, 0.018 * cd], r)),
             0.25 * norm(noise_burst(1200 * m, 6000 * m, 0.025 * cd, r)), 0.40 * thunk(r, 130, 95, 0.035 * cd, 0.02))
    place(b, cl, stop, clunk_gain)
    place(b, modal(2300 * m, [1, 1.5], [1, 0.4], [0.006, 0.004], r, 0.05), settle_at, 0.12)
    return b


def servo_tick(take, r):
    """A relay tick: pull-in, the armature's release 20-45 ms later at -6 dB, +10 %. The chair's small life sounds."""
    f, rel = [(2300.0, 0.030), (2500.0, 0.024), (2150.0, 0.036), (2700.0, 0.045)][take - 1]
    b = buf(0.16)
    place(b, relay_click(r, f, release_after=rel), 0.003)
    return b


def servo_hiccup(take, r):
    """The botched swing catching (the dome jerks back and holds two ticks): a gear-skip 'khk' at 3 ms (strike, three
    teeth 8 ms apart, a small iron body), the motor's whine dropping to a stall for two ticks, then picking up again and
    fading as the swing continues."""
    m, nt = [(1.00, 3), (1.20, 2)][take - 1]
    dur = 0.62
    b = buf(dur)
    t = tt(dur)
    s = np.interp(t, [0, 0.01, 0.07, 0.17, 0.33, 0.45, dur], [0.0, 0.7, 0.12, 0.12, 0.6, 0.0, 0.0])
    b += motor(t, s, r, 160 * m, 520 * m, grit=0.35, air=0.10, whine=0.30)
    place(b, strike(r, 1500), 0.003, 0.30)
    for k in range(nt):
        place(b, tooth(r, 2200 * m * (1 - 0.06 * k)), 0.003 + 0.008 * k, 0.20)
    place(b, norm(iron_body(r, 600 * m, 950 * m, 0.6, band_scale=m)), 0.003, 0.45)
    return b


# ---------------------------------------------------------------- gas

GAS = [dict(tau=0.34, puff=1.40, hi=(5000, 15000)), dict(tau=0.33, puff=1.42, hi=(4500, 13000)),
       dict(tau=0.30, puff=1.34, hi=(5500, 16000))]


def gas_hiss(take, r):
    """Gas hiss: valve tsk; pressure rises in 25 ms, holds 0.3 s, decays; the hiss band slides from high to 2-6 kHz as
    pressure falls; a low flow rumble; a faint 2.9 kHz whistle that dies first; one last little puff about 1.4 s in,
    after the hiss has finished and after the countdown (the joke)."""
    p = GAS[take - 1]
    dur = 1.75
    b = buf(dur)
    t = tt(dur)
    t0 = np.maximum(t - 0.012, 0)
    pr = (t >= 0.012) * np.clip(t0 / 0.025, 0, 1) * np.where(t0 < 0.3, 1.0, np.exp(-(t0 - 0.3) / p['tau']))
    n = noise(dur, r)
    hiss = (pr * bandpass(n, *p['hi']) + (1 - pr) * 0.7 * bandpass(n, 2000, 6000)) * pr
    flow = lowpass(noise(dur, r), 500) * pr ** 1.5
    fwh = 2900 * (0.92 + 0.08 * pr) * (1 + 0.004 * np.sin(2 * np.pi * 5.5 * t))
    whistle = np.sin(2 * np.pi * np.cumsum(fwh) / SR) * pr ** 2.5
    b += 0.75 * norm(hiss) + 0.15 * norm(flow) + 0.03 * whistle
    v = mix(noise_burst(4000, 12000, 0.0015, r), 0.4 * modal(3100, [1, 1.62], [1, 0.4], [0.006, 0.004], r))
    place(b, norm(v), 0.003, 0.5)
    place(b, norm(noise_burst(1500, 7000, 0.025, r, attack=0.008)), p['puff'], 0.22)
    return b


def vent_hiss(take, r):
    """Aftercare: the vents puff, left then right (50 and 100 ms in), after a soft valve tsk: two short high hisses
    with an 8 ms swell and a 110-140 ms decay."""
    m, d = [(1.00, 0.11), (1.15, 0.14)][take - 1]
    b = buf(1.15)
    place(b, norm(noise_burst(4000, 12000, 0.003, r)), 0.003, 0.20)
    place(b, norm(noise_burst(3000 * m, 14000, d, r, attack=0.008)), T, 0.80)
    place(b, norm(noise_burst(2600 * m, 13000, d, r, attack=0.008)), 2 * T, 0.65)
    return b


def deflate(take, r):
    """The abort: everything springs back to rest. A big pneumatic release (1.2-6 kHz, 6 ms swell, 120 ms decay, over a
    low flow), the motors spinning down (whine falling with speed over 0.45 s) and two soft clunks as parts arrive at
    rest (0.25 and 0.45 s)."""
    m = [1.00, 1.10][take - 1]
    dur = 1.15
    b = buf(dur)
    t = tt(dur)
    place(b, norm(noise_burst(1200 * m, 6000 * m, 0.12, r, attack=0.006)), 0.003, 0.55)
    place(b, norm(lowpass(noise(0.9, r), 600) * exp_env(0.9, 0.12, 0.006)), 0.003, 0.45)
    s = np.interp(t, [0, 0.02, 0.45, dur], [0.0, 0.9, 0.0, 0.0]) ** 1.3
    b += 0.5 * motor(t, s, r, 160 * m, 520 * m, grit=0.4, air=0.15, whine=0.28)
    for at, g in [(0.25, 0.35), (0.45, 0.25)]:
        place(b, mix(norm(modal(430 * m, [1, 2.756, 5.404], [1, 0.6, 0.4], [0.04, 0.03, 0.018], r)),
                     0.5 * thunk(r, 130, 95, 0.035, 0.02)), at, g)
    return b


# ---------------------------------------------------------------- DOLORES's voice: beep, ding, bow chime

def beep(take, r):
    """One soft pip on the house voice at E5 (40 ms hold, 15 ms decay). Played at pitch 1.0, 0.891 and 0.794 for 'three,
    two, one' (E5, D5, C5, stepwise down to the motif's home note; not 1.2 / 1.0 / 0.8, which from E5 is the G-E-C
    figure the company avoids), and at 1.189 (G5) as the scan's confirmation bip."""
    b = buf(0.30)
    place(b, fm_note(659.26, 0.015, r, index=(1.8, 0.7), index_tau=0.03, attack=0.003, hold=0.040), 0.003)
    return lowpass(b, 6000)


def ding(take, r):
    """The polite desk ding after the cut: one C6 on the house voice, a twin 2 Hz above (a slow beat, like a cheap
    counter bell), a tiny hammer tick. The most violent moment, followed by the politest sound. Played a major third up
    (pitch 1.26, E6) when the lamp turns green."""
    b = buf(1.18)
    place(b, fm_note(1046.5, 0.16, r, index=(1.6, 0.25), attack=0.002, partner=(2.0, 0.5)), 0.003)
    place(b, strike(r, 5000), 0.003, 0.12)
    return lowpass(b, 8000)


def bow_chime(take, r):
    """The dome's bow in aftercare: C6 and E6 together on the house voice, soft (index 1.0), a little apart in time
    (E6 15 ms late) so it rolls rather than strikes: a small, pleased 'thank you'."""
    b = buf(1.25)
    place(b, fm_note(1046.5, 0.15, r, index=(1.0, 0.2), attack=0.004), 0.003, 1.0)
    place(b, fm_note(1318.5, 0.14, r, index=(1.0, 0.2), attack=0.004), 0.018, 0.8)
    return lowpass(b, 7000)


# ---------------------------------------------------------------- scan, curtain, blade, cut, plonk

def scan_chirp(take, r):
    """The scanner powering up: the chair's mains hum (120 Hz) energising under a rising valve tone whose fundamental
    climbs 180 -> 540 Hz (the hum x1.5 to x4.5) with harmonics 1-4 at 1, 0.5, 0.3, 0.15, a little 1-3 kHz noise, a soft
    relay click at the start. 14 ticks: the ring's wind-up and the start of its descent."""
    dur = 0.78
    b = buf(dur)
    t = tt(dur)
    u = np.clip(t / 0.5, 0, 1)
    f = 180 * (1 + 2 * u ** 1.5)
    ph = 2 * np.pi * np.cumsum(f) / SR
    tone = sum(a * np.sin(k * ph + r.uniform(0, 6.28)) for k, a in zip([1, 2, 3, 4], [1, 0.5, 0.3, 0.15]))
    tail = np.clip((t - 0.55) / 0.17, 0, 1)
    env = np.clip(t / 0.04, 0, 1) * (0.6 + 0.4 * u) * (0.5 + 0.5 * np.cos(np.pi * tail)) ** 2
    hum = sum(a * np.sin(2 * np.pi * 120 * k * t) for k, a in zip([1, 2, 3], [1, 0.5, 0.3]))
    b += 0.75 * norm(tone) * env + 0.18 * norm(hum) * env * u
    b += 0.03 * norm(bandpass(noise(dur, r), 1000, 3000)) * env
    place(b, relay_click(r, 1700), 0.003, 0.25)
    return b


CURTAIN = [dict(ring=2900.0, cloth=(350, 2600), n=1.0), dict(ring=3300.0, cloth=(450, 3200), n=1.2),
           dict(ring=2600.0, cloth=(300, 2200), n=0.85)]


def curtain(take, r):
    """The privacy curtain whipping up on its brass rail (5 ticks, decelerating, then 2 ticks of settle): the rings
    zip along the rail (small brass ticks whose rate and level follow the speed: 25 + 120 x s per second), the cloth
    swishes and flutters (band-passed noise with a 14-24 Hz flutter), the rings bunch at the end of the rail at 250 ms
    (a soft brass clack and a cloth snap), and a last little jingle as it settles. No board, no wood."""
    p = CURTAIN[take - 1]
    dur = 0.80
    b = buf(dur)
    t = tt(dur)
    up = 5 * T
    u = np.clip(t / up, 0, 1)
    s = np.where(t < up, np.clip(t / 0.015, 0, 1) * (1 - u) ** 2, 0.0)
    flutter = 0.6 + 0.4 * np.sin(2 * np.pi * np.cumsum(r.uniform(14, 24) + 4 * s) / SR) ** 2
    cloth = bandpass(noise(dur, r), *p['cloth']) * (s ** 0.7) * flutter
    b += 0.55 * norm(cloth)
    at = 0.004
    while at < up - 0.01:
        sp = float(np.interp(at, t, s))
        if sp < 0.03:
            break
        place(b, norm(modal(p['ring'] * r.uniform(0.92, 1.08), [1, 1.73, 2.9], [1, 0.5, 0.25], [0.010, 0.007, 0.004], r)),
              at, 0.30 * sp ** 0.6 * r.uniform(0.6, 1.0))
        at += 1.0 / (25 + 120 * sp) * r.uniform(0.7, 1.3)
    clack = mix(norm(modal(p['ring'] * 0.8, [1, 1.73, 2.9, 4.2], [1, 0.6, 0.35, 0.2], [0.03, 0.02, 0.012, 0.008], r)),
                0.6 * norm(noise_burst(500 * p['n'], 4000 * p['n'], 0.015, r, attack=0.002)))
    place(b, norm(clack), up, 0.45)
    for k in range(3):
        place(b, norm(modal(p['ring'] * r.uniform(0.9, 1.1), [1, 1.73], [1, 0.4], [0.008, 0.005], r)),
              up + 0.04 + 0.025 * k + r.uniform(0, 0.01), 0.12 / (k + 1))
    return b


WINDUP = [dict(top=1000.0, grit=0.45, air=0.18), dict(top=1100.0, grit=0.58, air=0.28), dict(top=920.0, grit=0.34, air=0.11)]


def whine_rise(take, r):
    """The blade drawing back (10 ticks): the saw motor spins up (whine 300 -> 1300 Hz, grit, air), a slow heavy arm
    groan under it, and the whole thing is gone at 500 ms: faded over the last 50 ms, so the hold and the strike that
    follow are silent on purpose (the room holds its breath before the cut)."""
    q = WINDUP[take - 1]
    dur = 0.60
    b = buf(dur)
    t = tt(dur)
    u = np.clip(t / 0.45, 0, 1)
    s = u ** 1.3
    fade = np.clip((t - 0.45) / 0.05, 0, 1)
    lvl = (0.15 + 0.85 * u) * (0.5 + 0.5 * np.cos(np.pi * fade)) ** 2
    b += motor(t, s, r, 300, q['top'], grit=q['grit'], air=q['air'], whine=0.36) * lvl
    arm = speed_curve(t, 0.0, 0.50)
    b += 0.6 * motor(t, arm, r, 120, 200, grit=0.2, air=0.05, whine=0.3)
    place(b, relay_click(r, 1600), 0.003, 0.30)
    return b


CUT = [3200.0, 3500.0]


def cut(take, r):
    """The cut: 'shhk'. A strike at 4 ms, an air swish whose band falls 8 -> 2 kHz in 150 ms, a short steel shing, the
    saw motor's whine stopping (2.4 -> 1.1 kHz), a low thunk for weight, the blade's follow-through tang at 150 ms
    (3 ticks), a short air tail. Clean and dry: no flesh, no squelch, nothing wet."""
    f = CUT[take - 1]
    dur = 1.0
    b = buf(dur)
    place(b, strike(r, 2000), 0.004, 0.70)
    d = 0.5
    t = tt(d)
    lo = 1500 + 4000 * np.exp(-t / 0.06)
    sw = sweep_bandpass(noise(d, r), lo, lo * 2.2) * np.clip(t / 0.002, 0, 1) * np.exp(-t / 0.05)
    place(b, norm(sw), 0.004, 0.90)
    shing = mix(norm(modal(f, [1, 2.32, 4.1, 6.3], [1, 0.6, 0.4, 0.25], [0.09, 0.06, 0.04, 0.025], r, 0.01)),
                0.5 * norm(noise_burst(3000, 9000, 0.03, r)))
    place(b, norm(shing), 0.004, 0.35)
    place(b, sweep_sine(2400, 1100, 0.08, 0.08), 0.005, 0.15)
    place(b, thunk(r, 120, 70, 0.06, 0.03), 0.004, 0.35)
    place(b, norm(modal(1900, [1, 2.756, 5.404], [1, 0.5, 0.3], [0.04, 0.03, 0.02], r)), 3 * T, 0.18)
    place(b, norm(lowpass(noise(0.9, r), 1500) * exp_env(0.9, 0.12, 0.004)), 0.004, 0.05)
    return b


PLONK = [dict(f=260.0, d=1.0), dict(f=290.0, d=1.2), dict(f=235.0, d=0.85)]


def plonk(take, r):
    """The head coming loose: it hops a pixel and sits back down. A faint lift-off tick at 3 ms, then at 150 ms
    (3 ticks, the landing) a dry, muted 'plonk': a hollow low body (modes at about 260 Hz x 1, 2.3, 3.9, short) on felt
    (low noise, 8 ms). Like a coconut set down on a cushion. Nothing wet."""
    p = PLONK[take - 1]
    b = buf(0.55)
    place(b, norm(noise_burst(1500, 5000, 0.002, r)), 0.003, 0.05)
    land = 3 * T
    place(b, norm(modal(p['f'], [1, 2.3, 3.9], [1, 0.45, 0.2], [0.045 * p['d'], 0.025 * p['d'], 0.012 * p['d']], r, 0.02)),
          land, 0.75)
    place(b, norm(lowpass(noise(0.06, r), 800) * exp_env(0.06, 0.008, 0.001)), land, 0.30)
    return lowpass(b, 3000)


# ---------------------------------------------------------------- the jar and its tray: lid, clink, drawer

def lid_pop(take, r):
    """The jar's lid popping open: a suction pop (a sine falling 500 -> 150 Hz in 40 ms and a puff of air), a short dry
    hinge creak as it flips (stick-slip pings at 1.4 kHz, 140 ms), and the tin lid landing open at 350 ms (7 ticks)
    with a small tick."""
    m = [1.00, 1.15][take - 1]
    b = buf(0.62)
    place(b, sweep_sine(500 * m, 150 * m, 0.03, 0.012), 0.003, 0.55)
    place(b, norm(noise_burst(800, 5000, 0.012, r, attack=0.002)), 0.003, 0.30)
    at = 0.06
    while at < 0.20:
        place(b, norm(modal(1400 * m * r.uniform(0.95, 1.05), [1, 2.3], [1, 0.3], [0.006, 0.004], r)), at, 0.22 * r.uniform(0.4, 1.0))
        at += r.uniform(0.018, 0.035)
    land = mix(norm(modal(950 * m, [1, 1.59, 2.14, 3.9], [1, 0.6, 0.4, 0.3], [0.03, 0.025, 0.018, 0.012], r)),
               0.5 * norm(noise_burst(1500, 7000, 0.01, r)))
    place(b, norm(land), 7 * T, 0.35)
    return b


def clink(take, r):
    """The lid slamming shut on the jar (into contact): a glass tap (strike, glass modes at 1.65 kHz) with the tin
    lid's own plate modes, and a small bounce 35 ms later."""
    m = TAKE_M[take - 1]
    b = buf(0.50)
    hit = mix(0.4 * strike(r, 3000), 0.5 * norm(glass_modes(r, 1650 * m, decays=(0.05, 0.035, 0.02, 0.012))),
              0.35 * norm(modal(950 * m, [1, 1.59, 2.14, 3.9], [1, 0.6, 0.4, 0.3], [0.03, 0.025, 0.018, 0.012], r)),
              0.2 * norm(noise_burst(1500 * m, 7000 * m, 0.015, r)))
    place(b, norm(hit), 0.004, 1.0)
    place(b, norm(hit), 0.039, 0.25)
    return highpass(b, 400)


DRAWER = [dict(m=1.00, roll=0.34, hiss=0.16), dict(m=1.12, roll=0.40, hiss=0.12)]


def drawer(take, r, slide_ticks, loaded):
    """The jar's tray sliding out from under the seat (or back in): a tick of wind-up, then the slide decelerating to
    its end over slide_ticks (decelerating: speed (1 - u)^1.5, a little gentler than easeOutCubic's so the end stays audible): roller rumble gated at the roller rate,
    slide hiss, a soft low motor; with a jar aboard, two faint glass knocks as it rides. The stop is its own sound."""
    p = DRAWER[take - 1]
    m = p['m']
    start = T
    dur = round(start + slide_ticks * T + 0.25, 3)
    b = buf(dur)
    t = tt(dur)
    u = np.clip((t - start) / (slide_ticks * T), 0, 1)
    s = np.where((t > start) & (u < 1), np.clip((t - start) / 0.02, 0, 1) * (1 - u) ** 1.5, 0.0)
    s = s + 0.15 * speed_curve(t, 0.003, start)
    b += 0.7 * motor(t, s, r, 120 * m, 300 * m, grit=0.25, air=0.08, whine=0.18)
    gate = (0.5 + 0.5 * np.cos(2 * np.pi * np.cumsum(10 + 30 * s) / SR)) ** 2
    b += p['roll'] * norm(lowpass(noise(dur, r), 900) * gate * s)
    b += p['hiss'] * norm(bandpass(noise(dur, r), 1500, 6000) * s ** 1.5)
    place(b, relay_click(r, 1500 * m), 0.003, 0.20)
    if loaded:
        for at in (start + 0.06, start + 0.19):
            place(b, norm(glass_modes(r, 1500 * m * r.uniform(0.95, 1.05), decays=(0.025, 0.015, 0.01, 0.006))), at, 0.10)
    return b


def drawer_stop(take, r):
    """The tray reaching its end, softly: a rubber bumper (a low bump falling 160 -> 100 Hz), a felt-damped body at
    about 420 Hz, a faint rattle tick 30 ms later."""
    m = TAKE_M[take - 1]
    b = buf(0.45)
    place(b, thunk(r, 160 * m, 100 * m, 0.03, 0.015), 0.004, 0.70)
    place(b, norm(modal(420 * m, [1, 2.4, 4.1], [1, 0.4, 0.2], [0.03, 0.018, 0.01], r, 0.02)), 0.004, 0.45)
    place(b, norm(lowpass(noise(0.05, r), 1500) * exp_env(0.05, 0.006, 0.001)), 0.004, 0.25)
    place(b, norm(modal(1800 * m, [1, 1.6], [1, 0.4], [0.006, 0.004], r)), 0.034, 0.08)
    return b


def tap(take, r):
    """The waiting barrier tapping toward the patient like a clerk: a short motor blip (60 ms) and, as it turns back,
    a small enamel tick of its pad, after the motor's relay click."""
    m = TAKE_M[take - 1]
    dur = 0.30
    b = buf(dur)
    t = tt(dur)
    s = 0.6 * speed_curve(t, 0.003, 0.065)
    b += 0.5 * motor(t, s, r, 260 * m, 800 * m, grit=0.4, air=0.12, whine=0.22)
    place(b, relay_click(r, 2000 * m), 0.003, 0.30)
    place(b, norm(modal(1500 * m, [1, 2.1, 3.4], [1, 0.4, 0.2], [0.012, 0.008, 0.005], r)), 0.065, 0.45)
    place(b, norm(noise_burst(800, 3000, 0.003, r)), 0.065, 0.20)
    return b


def oopsie_squeak(take, r):
    """The botched head turning over between the rubber jaws: two short rubber squeaks (stick-slip: a sine gliding up
    1100 -> 1500 Hz, gated by 40-50 Hz pulses, with a little noise), the second higher and shorter, 180 ms later."""
    m = [1.00, 1.12][take - 1]
    b = buf(0.62)
    for at, (f0, f1, d, g) in [(0.003, (1100, 1500, 0.13, 1.0)), (0.183, (1300, 1800, 0.09, 0.8))]:
        t = tt(d)
        f = (f0 + (f1 - f0) * t / d) * m
        pulse = (0.5 + 0.5 * np.cos(2 * np.pi * np.cumsum(np.full(len(t), r.uniform(40, 50))) / SR)) ** 3
        env = np.sin(np.pi * t / d) ** 2
        sq = (np.sin(2 * np.pi * np.cumsum(f) / SR) + 0.3 * np.sin(4 * np.pi * np.cumsum(f) / SR)) * pulse * env
        sq += 0.15 * bandpass(noise(d, r), 800, 3000) * env
        place(b, norm(sq), at, g)
    place(b, norm(noise_burst(800, 3000, 0.002, r)), 0.003, 0.30)  # the rubber catching
    return b


# ---------------------------------------------------------------- the bolts, the handshake, the sparks

def ratchet(take, r):
    """One wrench click bolting the Collar Cam on (the three come 2 ticks apart, so each is short): the pawl clank at
    4 ms (a tooth and a small iron body, short decays) and one more tooth 40 ms later as the wrench completes its turn.
    Code raises the pitch a little on each of the three."""
    m = TAKE_M[take - 1]
    second = [0.044, 0.062, 0.036][take - 1]
    b = buf(0.24)
    clank = mix(tooth(r, 3000 * m), 0.5 * norm(modal(1500 * m, [1, 2.756, 5.404], [1, 0.5, 0.3], [0.014, 0.010, 0.006], r)),
                0.3 * norm(noise_burst(2000 * m, 7000 * m, 0.010, r)))
    place(b, norm(clank), 0.004, 1.0)
    place(b, tooth(r, 3000 * m * 1.03), second, 0.45)
    return b


def handshake(take, r):
    """The neural handshake starting (from the dome's cap): a relay click, then static that washes up (dense crackle
    through a band rising from 400-1200 Hz to 2-4.5 kHz over 0.45 s, swelling and fading), a short mains buzz as the
    link locks, and a second relay click at 500 ms."""
    q = [dict(hi=4500.0, lock=0.53), dict(hi=4000.0, lock=0.60)][take - 1]
    dur = 0.95
    b = buf(dur)
    place(b, relay_click(r, 1800), 0.003, 0.40)
    sd = 0.50
    st = crackle(r, sd, 300, 6000, spacing=(0.003, 0.014), width=(0.001, 0.004), first_gain=0.5)
    ts = tt(len(st) / SR)
    u = np.clip(ts / sd, 0, 1)
    st = sweep_bandpass(st, 400 + 1600 * u, 1200 + (q['hi'] - 1200) * u)
    env = np.sin(np.pi * np.clip(ts / (sd + 0.02), 0, 1)) ** 1.2
    place(b, norm(st * env), 0.02, 0.60)
    t = tt(0.10)
    buzz = bandpass(noise(0.10, r), 1500, 5000) * (0.5 + 0.5 * np.cos(2 * np.pi * 100 * t)) ** 3 * np.sin(np.pi * t / 0.10)
    place(b, norm(buzz), q['lock'] - 0.08, 0.25)
    place(b, relay_click(r, 2000), q['lock'], 0.30)
    return b


SPARK = [0.20, 0.25, 0.18]


def spark(take, r):
    """A handshake spark from the dome's cap: an arc crackle (bursts 2-8 ms, 10-60 ms apart, 3-14 kHz, the first the
    strongest) and a 70 ms mains buzz (2-8 kHz noise gated at 100 Hz)."""
    d = SPARK[take - 1]
    b = buf(d + 0.2)
    place(b, norm(crackle(r, d, 3000, 14000)), 0.004, 0.8)
    t = tt(0.07)
    buzz = bandpass(noise(0.07, r), 2000, 8000) * (0.5 + 0.5 * np.cos(2 * np.pi * 100 * t)) ** 3 * np.sin(np.pi * t / 0.07)
    place(b, norm(buzz), 0.005, 0.30)
    return b


# ---------------------------------------------------------------- the event list

PISTON_IN = dict(event='block.piston.contract', file='tile__piston__in.ogg', vol=0.5, pitch=0.675)
PISTON_OUT = dict(event='block.piston.extend', file='tile__piston__out.ogg', vol=0.5, pitch=0.725)
FIZZ = dict(event='block.fire.extinguish', file='random__fizz.ogg', vol=0.5, pitch=2.0)
TRAPDOOR_CLOSE = dict(event='block.iron_trapdoor.close', file='block__iron_trapdoor__close1.ogg', vol=0.9, pitch=0.95)
LANTERN = dict(event='block.lantern.place', file='block__lantern__place1.ogg', vol=1.0, pitch=0.88)
CLICK = dict(event='block.dispenser.dispense', file='random__click.ogg', vol=1.0, pitch=1.0)
XYLO = dict(event='block.note_block.xylophone', file='note__xylobone.ogg', vol=1.0, pitch=1.0)
SHELF = dict(event='block.chiseled_bookshelf.insert', file='block__chiseled_bookshelf__insert1.ogg', vol=1.0, pitch=1.0)
WOOD_TRAPDOOR = dict(event='block.wooden_trapdoor.open', file='block__wooden_trapdoor__open1.ogg', vol=0.9, pitch=0.95)

BASE = dict(category='BLOCKS', attenuation=16, fixed_range=False, volume=1.0)
KEY_NOTE = ' Keyframe volume 1.0 (the file carries the loudness).'


def ev(**kw):
    d = dict(BASE)
    d.update(kw)
    return d


SOUNDS = [
    # ---- the operation, in order
    ev(id='chair.servo_barrier', file='chair/servo_barrier', fn=lambda take, r: servo(take, r, 'small', 3), takes=3,
       target=-27.0, cls='servo', kind=('servo', 3 * T), fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[0.97, 1.03],
       ticks=[0], subtitle_en='Barrier motor whirs', subtitle_pt='Motor da cancela zune', muffled=False,
       play='Server at t 0: the barrier lifts 0-3 (the motor move) and holds 3-5.'),
    ev(id='chair.barrier_drop', file='chair/barrier_drop', fn=lambda take, r: hydraulic(take, r, 2 * T, 10 * T, 'in', 0, 0.30),
       takes=3, target=-27.0, cls='servo (hydraulic stroke)', kind=('servo', 10 * T), fade_ms=20, hit_ms=3,
       ref=PISTON_IN, pitch=[0.98, 1.02], ticks=[3], subtitle_en='Barrier lowers', subtitle_pt='Cancela desce', muffled=False,
       play='Server at t 3: the valve opens in the hold, the stroke accelerates 5-13 into contact and lets go there; chair.barrier_latch is the hit at 13.'),
    ev(id='chair.barrier_latch', file='chair/barrier_latch', fn=lambda take, r: barrier_clamp(take, r, 'latch'), takes=3,
       target=-22.0, cls='clamp', kind='latch', fade_ms=12, hit_ms=100, preroll_ticks=2, ref=TRAPDOOR_CLOSE,
       pitch=[0.94, 1.06], ticks=[13], subtitle_en='Barrier clamps shut', subtitle_pt='Cancela trava', muffled=False,
       play='Server at t 11: two ratchet teeth, the strike at 100 ms on the contact tick 13.'),
    ev(id='chair.barrier_settle', file='chair/barrier_settle', fn=lambda take, r: barrier_clamp(take, r, 'settle'), takes=3,
       target=-24.0, cls='clamp (lighter)', kind='latch', fade_ms=12, hit_ms=100, preroll_ticks=2, ref=TRAPDOOR_CLOSE,
       pitch=[0.94, 1.06], ticks=[19], subtitle_en=None, subtitle_pt=None, muffled=False,
       play='Server at t 17: the bounce falls back onto the latch, strike at 100 ms on t 19. No subtitle (part of the clamping).'),
    ev(id='chair.hood_nod', file='chair/hood_nod', fn=lambda take, r: servo(take, r, 'large', 6, clunk_gain=0.45), takes=3,
       target=-27.0, cls='servo (small move)', kind=('servo', 6 * T), fade_ms=20, hit_ms=3, ref=PISTON_IN,
       pitch=[1.0, 1.0], pitch_per_play=[0.857, 1.2], ticks=[21, 56], subtitle_en='Dome nods', subtitle_pt='Cúpula acena',
       muffled=False, clip_key='key 21: change chair.servo_tick to chair.hood_nod|1.0|0.857; add key 56 chair.hood_nod|1.0|1.2',
       play='Key 21 at pitch 0.857 (the 6-tick file lasts 7: the dome nods toward the face 21-28) and key 56 at 1.2 (5 ticks: it nods back 56-61). A soft stop, not a clunk.' + KEY_NOTE),
    ev(id='chair.gas_hiss', file='chair/gas_hiss', fn=gas_hiss, takes=3, target=-24.0, cls='hiss', kind='hiss',
       fade_ms=60, hit_ms=3, ref=FIZZ, pitch=[0.97, 1.03], ticks=[30], subtitle_en='Anaesthetic hisses',
       subtitle_pt='Anestésico chia', muffled=True,
       play='Server at t 30 at the gas locator under the dome. Its last little puff lands 1.34-1.42 s in (t 57-58), after the hiss has died and after the third beep: do not cut it short.'),
    ev(id='chair.beep', file='chair/beep', fn=beep, takes=1, target=-30.0, cls='tick (blip)', kind='click',
       fade_ms=10, hit_ms=3, ref=XYLO, pitch=[1.0, 1.0], pitch_per_play=[1.0, 0.8909, 0.7937, 1.1892], ticks=[40, 47, 54, 94],
       subtitle_en='DOLORES counts down', subtitle_pt='DOLORES faz a contagem', muffled=True,
       play='Keys 40, 47, 54 at pitch 1.0, 0.8909, 0.7937 (E5, D5, C5) and the confirmation bip at 94 at 1.1892 (G5). No random pitch.' + KEY_NOTE),
    ev(id='chair.servo_tick', file='chair/servo_tick', fn=servo_tick, takes=4, target=-31.0, cls='relay click',
       kind='click', fade_ms=8, hit_ms=3, ref=dict(event='item.spyglass.use', file='item__spyglass__use.ogg', vol=1.0, pitch=1.0),
       pitch=[0.94, 1.06], ticks=[68, 74, 80, 86, 212], subtitle_en=None, subtitle_pt=None, muffled=True,
       play='The scan ticks (keys 68, 74, 80, 86) at volume 0.6, pitch 1.3; the lid leaning out (212), idle (52) at volume 0.5. No subtitle: small life.'),
    ev(id='chair.scan_chirp', file='chair/scan_chirp', fn=scan_chirp, takes=1, target=-26.0, cls='servo (scanner)',
       kind='stinger', fade_ms=30, hit_ms=3,
       ref=dict(event='block.beacon.activate', file='block__beacon__activate.ogg', vol=0.7, pitch=1.8),
       pitch=[1.0, 1.0], ticks=[60], subtitle_en='Scanner chirps', subtitle_pt='Scanner apita', muffled=True,
       clip_key='remove key 64 (chair.servo): the chirp covers the ring\'s start',
       play='Server at t 60. 14 ticks long. The scan loop is not made yet.'),
    ev(id='chair.latch', file='chair/latch', fn=latch, takes=2, target=-27.0, cls='relay and small latch', kind='latch',
       fade_ms=10, hit_ms=3, ref=CLICK, pitch=[0.97, 1.03], ticks=[100], subtitle_en='Something latches',
       subtitle_pt='Algo trava', muffled=True, play='Key 100 (the point of no return).' + KEY_NOTE),
    ev(id='chair.whine_rise', file='chair/whine_rise', fn=whine_rise, takes=3, target=-26.0, cls='servo (saw spin-up)',
       kind=('servo', 10 * T), fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[1.0, 1.0], ticks=[104],
       subtitle_en='Blade winds up', subtitle_pt='Lâmina toma impulso', muffled=True,
       play='Key 104, fixed pitch 1.0. Silent from 500 ms (t 114): 114-120 is silent on purpose, apart from the curtain settling.' + KEY_NOTE),
    ev(id='chair.curtain', file='chair/curtain', fn=curtain, takes=3, target=-27.0, cls='servo (cloth and rings)',
       kind=('servo', 5 * T), fade_ms=20, hit_ms=3,
       ref=dict(event='item.armor.equip_chain', file='item__armor__equip_chain1.ogg', vol=1.0, pitch=1.0),
       pitch=[0.95, 1.05], ticks=[107, 222], subtitle_en='Curtain: privacy, please', subtitle_pt='Cortina de privacidade sobe',
       muffled=True, clip_key='key 112 (chair.placard): move to 107 and rename chair.curtain; key 222 (chair.placard): rename chair.curtain',
       play='Keys at the start of each whip up: 107 (up 107-112, settles 114) and 222 (up 222-227, settles 229). The rings bunch at the rail\'s end at 250 ms. Replaces chair.placard.' + KEY_NOTE),
    ev(id='chair.cut', file='chair/cut', fn=cut, takes=2, target=-18.0, cls='signature moment', kind='latch',
       fade_ms=40, hit_ms=4, ref=dict(event='block.smithing_table.use', file='block__smithing_table__smithing_table2.ogg', vol=1.0, pitch=1.0),
       pitch=[0.95, 1.05], attenuation=24, fixed_range=True, ticks=[120], subtitle_en='Blade separates',
       subtitle_pt='Lâmina separa', muffled=False,
       play='Server at t 120, the contact tick, to everyone except the patient (the patient hears total silence). Register fixed-range 24.'),
    ev(id='chair.plonk', file='chair/plonk', fn=plonk, takes=3, target=-29.0, cls='tick (soft knock)', kind='latch',
       fade_ms=15, hit_ms=150, preroll_ticks=3, ref=SHELF, pitch=[0.95, 1.05], ticks=[125],
       subtitle_en=None, subtitle_pt=None, muffled=False,
       play='Key 122 (the top of the hop): the landing knock is at 150 ms, on t 125. No subtitle (part of the cut).' + KEY_NOTE),
    ev(id='chair.ding', file='chair/ding', fn=ding, takes=1, target=-27.0, cls='done chime', kind='stinger',
       fade_ms=50, hit_ms=3, ref=dict(event='block.note_block.bell', file='note__bell.ogg', vol=1.0, pitch=1.0),
       pitch=[1.0, 1.0], pitch_per_play=[1.0, 1.2599], ticks=[128, 230], subtitle_en='DOLORES dings politely',
       subtitle_pt='DOLORES toca a campainha', muffled=False,
       play='Key 128 at pitch 1.0; key 230 (lamp green) at 1.2599. No random pitch.' + KEY_NOTE),
    ev(id='chair.saline_glug', vanilla=['minecraft:item/bottle/fill1', 'minecraft:item/bottle/fill2', 'minecraft:item/bottle/fill3', 'minecraft:item/bottle/fill4'],
       vanilla_measure=['item__bottle__fill1.ogg', 'item__bottle__fill2.ogg', 'item__bottle__fill3.ogg'], target=-24.0,
       cls='glug', pitch=[0.80, 0.90], ticks=[140, 199, 205], subtitle_en='Saline glugs', subtitle_pt='Soro gorgoleja',
       muffled=True,
       play='Vanilla bottle-fill recordings referenced by name in sounds.json. Server at t 140, 199, 205, pitch 0.80-0.90.'),
    ev(id='chair.lid_pop', file='chair/lid_pop', fn=lid_pop, takes=2, target=-29.0, cls='tick (pop and hinge)',
       kind=('servo', 7 * T), fade_ms=15, hit_ms=3,
       ref=dict(event='item.bottle.empty', file='item__bottle__empty1.ogg', vol=1.0, pitch=1.0),
       pitch=[0.95, 1.05], ticks=[141], subtitle_en='Jar lid pops open', subtitle_pt='Tampa do pote abre', muffled=True,
       play='Key 141: the suction pop at 3 ms, the hinge creaks as it flips, the lid lands open at 350 ms (t 148).' + KEY_NOTE),
    ev(id='chair.clamp', file='chair/clamp', fn=claw_clamp, takes=3, target=-25.0, cls='clamp release (soft clamp)',
       kind='latch', fade_ms=12, hit_ms=4,
       ref=dict(event='block.copper_trapdoor.close', file='block__copper_trapdoor__toggle1.ogg', vol=1.0, pitch=0.95),
       pitch=[0.94, 1.06], ticks=[148], subtitle_en='Jaws grip', subtitle_pt='Garras seguram', muffled=True,
       play='Key 148: the jaws meet the temples at 4 ms; the solenoid locks 60 ms later.' + KEY_NOTE),
    ev(id='chair.hydraulic', file='chair/hydraulic',
       fn=lambda take, r: hydraulic(take, r, 0.01, 0.01 + 9 * T, 'sin2', 0.6, 0.30, settle=0.01 + 11 * T),
       takes=3, target=-27.0, cls='servo (hydraulic stroke)', kind=('servo', 11 * T), fade_ms=20, hit_ms=3,
       ref=PISTON_IN, pitch=[1.0, 1.0], ticks=[152, 184], subtitle_en='Mast hydraulics hiss',
       subtitle_pt='Hidráulica do mastro chia', muffled=True,
       play='Keys 152 (the rail telescopes up 8 px: arrives 161, settles 163) and 184 (lowers 13 px: arrives 193, settles 195), pitch 1.0: the stop is at 450 ms, the settle knock at 550 ms.' + KEY_NOTE),
    ev(id='chair.hydraulic_short', file='chair/hydraulic_short',
       fn=lambda take, r: hydraulic(take, r, 0.01, 0.01 + 7 * T, 'sin2', 0.5, 0.30, settle=0.01 + 9 * T),
       takes=3, target=-27.0, cls='servo (hydraulic stroke)', kind=('servo', 9 * T), fade_ms=20, hit_ms=3,
       ref=PISTON_IN, pitch=[1.0, 1.0], ticks=[204], subtitle_en='Mast hydraulics hiss',
       subtitle_pt='Hidráulica do mastro chia', muffled=True,
       clip_key='key 204: change chair.hydraulic|0.5|1.1429 to chair.hydraulic_short|1.0|1.0',
       play='Key 204: the rail rises (arrives 211, settles 213).' + KEY_NOTE),
    ev(id='chair.servo', file='chair/servo', fn=lambda take, r: servo(take, r, 'large', 12, pre_ticks=2, settle_ticks=3),
       takes=3, target=-27.0, cls='servo', kind=('servo', 17 * T), fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[0.98, 1.02],
       ticks=[166], subtitle_en='Dome swings', subtitle_pt='Cúpula gira', muffled=True,
       clip_key='key 166: pitch 1.0',
       play='Key 166: the small wind-back bump (166-168), the 180-degree swing to the jar (168-180, stop clunk on 180), the settle tick at 183.' + KEY_NOTE),
    ev(id='chair.servo_home', file='chair/servo_home', fn=lambda take, r: servo(take, r, 'large', 11, settle_ticks=4),
       takes=3, target=-27.0, cls='servo', kind=('servo', 15 * T), fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[0.98, 1.02],
       ticks=[218], subtitle_en='Dome swings', subtitle_pt='Cúpula gira', muffled=True,
       clip_key='key 218: change chair.servo to chair.servo_home|1.0|1.0',
       play='Key 218: the swing home (218-229, stop clunk on 229), the settle tick at 233.' + KEY_NOTE),
    ev(id='chair.plop', vanilla=['minecraft:block/bubble_column/bubble1', 'minecraft:block/bubble_column/bubble2', 'minecraft:block/bubble_column/bubble3'],
       vanilla_measure=['block__bubble_column__bubble1.ogg', 'block__bubble_column__bubble2.ogg'], target=-27.0,
       cls='glass clink class (small liquid event)', pitch=[0.60, 0.70], attenuation=12, ticks=[198],
       subtitle_en='Head plops in', subtitle_pt='Cabeça cai no soro', muffled=True,
       play='Vanilla bubble-pop recordings by name, pitched down to one big bubble, at key 198 when the head drops into the saline.'),
    ev(id='chair.handshake', file='chair/handshake', fn=handshake, takes=2, target=-28.0, cls='servo (static)',
       kind='stinger', fade_ms=20, hit_ms=3, ref=PISTON_OUT, pitch=[0.97, 1.03], ticks=[214],
       subtitle_en='Neural link handshakes', subtitle_pt='Conexão neural se inicia', muffled=True,
       play='Server at t 214 (from the dome\'s cap). Replaces chair.antenna.'),
    ev(id='chair.clink', file='chair/clink', fn=clink, takes=3, target=-26.5, cls='glass clink', kind='latch',
       fade_ms=15, hit_ms=4, ref=LANTERN, pitch=[0.94, 1.06], ticks=[215], subtitle_en='Jar lid clinks shut',
       subtitle_pt='Tampa do pote fecha', muffled=True, play='Key 215: the lid slams onto the jar.' + KEY_NOTE),
    ev(id='chair.spark', file='chair/spark', fn=spark, takes=3, target=-24.5, cls='hiss (arc crackle)', kind='latch',
       fade_ms=10, hit_ms=4, ref=FIZZ, pitch=[0.94, 1.06], ticks=[221, 228, 235], subtitle_en='Dome sparks',
       subtitle_pt='Cúpula solta faíscas', muffled=True,
       play='Keys 221, 228, 235 at the spark locator on the dome cap, with the particles. Random take and pitch.'),
    ev(id='chair.ratchet', file='chair/ratchet', fn=ratchet, takes=3, target=-28.2, cls='ratchet (one click; the run of three about -27)',
       kind='click', fade_ms=8, hit_ms=4, ref=CLICK, pitch=[1.0, 1.0], pitch_per_play=[1.0, 1.03, 1.06], ticks=[228, 230, 232],
       subtitle_en='Wrench ratchets', subtitle_pt='Chave aperta o parafuso', muffled=True,
       volume=0.7,
       play='Server at t 228, 230, 232 at pitch 1.0, 1.03, 1.06, a different take each time, at volume 0.7: three clicks inside one 400 ms window add up (the run measures -23.8 at volume 1, -26.9 at 0.7, the whole-ratchet class). Each click is under 0.1 s and the gaps fall to -55 dB, so the three stay distinct at 100 ms apart.'),
    ev(id='chair.unlatch', file='chair/unlatch', fn=unlatch, takes=3, target=-24.0, cls='clamp release',
       kind='latch', fade_ms=12, hit_ms=50,
       ref=dict(event='block.iron_door.open', file='block__iron_door__open1.ogg', vol=0.9, pitch=0.95),
       pitch=[0.97, 1.03], ticks=[234], subtitle_en='Barrier unlatches', subtitle_pt='Cancela destrava', muffled=False,
       play='Key 234: the latch vents at once and lets go at 50 ms (t 235). Also the abort (with chair.barrier_lift).' + KEY_NOTE),
    ev(id='chair.barrier_lift', file='chair/barrier_lift', fn=lambda take, r: hydraulic(take, r, 0.01, 0.01 + 4 * T, 'sin2', 0.7, 0.32),
       takes=3, target=-27.0, cls='servo (hydraulic stroke)', kind=('servo', 4 * T), fade_ms=20, hit_ms=3,
       ref=PISTON_IN, pitch=[0.98, 1.02], ticks=[236], subtitle_en='Barrier rises', subtitle_pt='Cancela sobe', muffled=False,
       play='Server at t 236: the barrier lifts 236-240 and stops on 240 (it overshoots; aftercare settles it).'),
    # ---- the other clips
    ev(id='chair.tap', file='chair/tap', fn=tap, takes=3, target=-30.0, cls='tick (idle life)', kind='click',
       fade_ms=10, hit_ms=3, ref=WOOD_TRAPDOOR, pitch=[0.94, 1.06], attenuation=8, ticks=None,
       subtitle_en=None, subtitle_pt=None, muffled=False,
       play='Clip "ready", keys 8 and 16: the waiting barrier taps toward the patient. Attenuation 8, no subtitle (idle life).' + KEY_NOTE),
    ev(id='chair.drawer_out', file='chair/drawer_out', fn=lambda take, r: drawer(take, r, 10, True), takes=2,
       target=-27.0, cls='servo (slide)', kind=('servo', 10 * T), fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[0.97, 1.03],
       ticks=None, subtitle_en='Jar tray slides out', subtitle_pt='Bandeja do pote desliza', muffled=False,
       play='Clip "tray_out", key 1: the slide runs 2-11 decelerating; chair.drawer_stop at key 11.' + KEY_NOTE),
    ev(id='chair.drawer_in', file='chair/drawer_in', fn=lambda take, r: drawer(take, r, 11, False), takes=2,
       target=-27.0, cls='servo (slide)', kind=('servo', 11 * T), fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[0.97, 1.03],
       ticks=None, subtitle_en='Jar tray slides in', subtitle_pt='Bandeja do pote recolhe', muffled=False,
       play='Clip "tray_in", key 1: the slide runs 2-12; chair.drawer_stop at key 12.' + KEY_NOTE),
    ev(id='chair.drawer_stop', file='chair/drawer_stop', fn=drawer_stop, takes=3, target=-29.0, cls='tick (soft stop)',
       kind='latch', fade_ms=12, hit_ms=4, ref=SHELF, pitch=[0.94, 1.06], ticks=None, subtitle_en=None, subtitle_pt=None,
       muffled=False, play='Clips "tray_out" key 11 and "tray_in" key 12. No subtitle (part of the slide).' + KEY_NOTE),
    ev(id='chair.servo_hiccup', file='chair/servo_hiccup', fn=servo_hiccup, takes=2, target=-26.0, cls='servo (gear skip)',
       kind=('servo', 0.40), fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[0.97, 1.03], ticks=None,
       subtitle_en='Dome hiccups', subtitle_pt='Cúpula engasga', muffled=True,
       play='Clip "botch", key 174: the swing catches and holds two ticks. It plays over the swing\'s own chair.servo (key 166).' + KEY_NOTE),
    ev(id='chair.oopsie_squeak', file='chair/oopsie_squeak', fn=oopsie_squeak, takes=2, target=-27.5, cls='tick (rubber squeak)',
       kind='latch', fade_ms=15, hit_ms=3,
       ref=dict(event='block.copper_door.open', file='block__copper_door__toggle1.ogg', vol=1.0, pitch=0.95),
       pitch=[0.95, 1.05], ticks=None, subtitle_en='Jaws squeak', subtitle_pt='Garras rangem', muffled=True,
       play='Clip "botch", key 177: the head turns over between the rubber jaws.' + KEY_NOTE),
    ev(id='chair.deflate', file='chair/deflate', fn=deflate, takes=2, target=-24.0, cls='hiss (pneumatic release)',
       kind='hiss', fade_ms=40, hit_ms=3, ref=PISTON_IN, pitch=[0.97, 1.03], ticks=None,
       subtitle_en='Chair deflates', subtitle_pt='Cadeira esvazia', muffled=False,
       play='Clip "abort", key 4: everything springs back to rest. Plays with chair.unlatch and chair.barrier_lift (server).' + KEY_NOTE),
    ev(id='chair.hiss', file='chair/vent_hiss', fn=vent_hiss, takes=2, target=-25.0, cls='hiss (vents)', kind='hiss',
       fade_ms=30, hit_ms=50, ref=FIZZ, pitch=[0.95, 1.05], ticks=None, subtitle_en='Vents puff', subtitle_pt='Respiros sopram',
       muffled=False, play='Clip "aftercare", key 1: the puffs land at 50 and 100 ms with the clouds at keys 2 and 3.' + KEY_NOTE),
    ev(id='chair.bow_chime', file='chair/bow_chime', fn=bow_chime, takes=1, target=-30.0, cls='done chime (soft)',
       kind='stinger', fade_ms=50, hit_ms=3, ref=XYLO, pitch=[1.0, 1.0], ticks=None,
       subtitle_en='DOLORES bows', subtitle_pt='DOLORES faz uma reverência', muffled=False,
       play='Clip "aftercare", key 14: the dome\'s bow. No random pitch.' + KEY_NOTE),
]

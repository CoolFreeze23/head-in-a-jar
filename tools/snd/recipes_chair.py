"""The Separation Chair's operation (DOLORES), as a bystander hears it: 240 ticks.

Timeline: the chair's operation clip (`art/chair/chair_anim.py`), which replaced the
motion study's beat sheet: a level-crossing barrier instead of cuffs and a chest bar, a gas horn on the barrier, a hood
on a mast that lifts, swings and lowers the head into a jar standing at the outlet, a lid that flips, no tray. Event ids
are the ones the animator's sound keys already name (chair.servo, chair.hydraulic, chair.clamp, ...) wherever one file
fits every use of the id; the rest are new ids for server-played beats.

One recipe with parameters serves several beats: the clamp makes the barrier's latch and its settle, the hydraulic
makes the barrier's drop and lift and the hood's lifts and lowers, the servo makes every motor move at that move's
length in ticks. Times inside a file are seconds; one tick is 50 ms. A server-played file may start before its contact
(`preroll_ticks`, `hit_ms`); a keyframed file has its hit at 0-5 ms, because a key fires on its own tick.
Takes differ by construction (a per-take table of frequencies, decays and layer balances), not by luck of the seed.
"""
import numpy as np
from nsynth import *

T = TICK
TAKE_M = [1.00, 1.10, 0.90]  # per-take frequency scale (modes and noise bands together)
TAKE_D = [1.00, 1.25, 0.80]  # per-take decay scale, so the takes also differ in length


# ---------------------------------------------------------------- clamp family: barrier latch and settle, claw, unlatch

LATCH = {
    'latch': dict(f0=600.0, plate=960.0, tooth=2700.0, th=(150, 95), decay=0.80, teeth=2),
    'settle': dict(f0=500.0, plate=800.0, tooth=2450.0, th=(135, 82), decay=0.70, teeth=1),
}


def barrier_clamp(take, r, which):
    """Clamp (prototype round 2): ratchet teeth as the barrier's tip rides over the pawl on the mast, then at exactly
    100 ms the strike, a short iron body, a metallic noise band, a small thunk and a low-passed tail. 'settle' is the
    bounce falling back onto the latch at t 19: two semitones lower, one tooth, shorter."""
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
    """The pincer's front jaw closing on the forehead (key 148, easeInQuad into contact): a padded strike at 4 ms, a
    short small body, a rubber thud, then a solenoid tooth 60 ms later as it locks. Nothing wet."""
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
    """Clamp release (key 232): a short hiss puff as the latch vents, a lighter strike at 50 ms (t 233, the barrier
    starts to lift), the iron body x1.12 with decays x0.7, no teeth."""
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
    """The point of no return (key 100): a relay pulls in and a small bolt shoots home: a click, then 15 ms later a
    short steel knock (strike, a small iron body at 900 Hz with short decays, a light thunk)."""
    m, dk = [(1.00, 1.0), (1.12, 1.4)][take - 1]
    b = buf(0.40)
    place(b, relay_click(r, 1900 * m), 0.003, 0.45)
    place(b, strike(r, 2000 * m), 0.018, 0.40)
    place(b, norm(iron_body(r, 900 * m, 1400 * m, 0.45 * dk, band_scale=m)), 0.018, 0.55)
    place(b, thunk(r, 180, 120, 0.02, 0.01), 0.018, 0.20)
    return b


# ---------------------------------------------------------------- hydraulic: barrier drop and lift, hood lift and lower

HYD_AIR = [0.42, 0.60, 0.40]


def hydraulic(take, r, s0, s1, curve='sin2', stop_gain=0.9, valve=0.25, puff=False):
    """Hydraulic stroke: valve hiss at 3 ms, then from s0 to s1 (s) one speed curve drives a low-passed whoosh whose
    cutoff rises with speed, pneumatic air and a 70-110 Hz groan with 4 Hz vibrato; an end-stop (iron body, thunk,
    strike) at s1 unless stop_gain is 0; a vent puff after it if asked. curve 'in' accelerates into s1 (a drop into
    contact) and lets go in 25 ms."""
    m = TAKE_M[take - 1]
    dur = round(s1 + 0.42, 3)
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
    return b


# ---------------------------------------------------------------- servo: every motor move

SERVO = {
    'large': dict(base=160.0, top=520.0, clunk=430.0, click=1500.0),  # the hood swinging round the mast
    'small': dict(base=260.0, top=800.0, clunk=640.0, click=2000.0),  # the barrier's motor, the gas horn
}
SERVO_TAKES = [dict(m=1.00, grit=0.42, air=0.14, cd=1.0), dict(m=1.08, grit=0.54, air=0.24, cd=1.25),
               dict(m=0.92, grit=0.32, air=0.09, cd=0.8)]


def servo(take, r, size, move_ticks):
    """Servo move (prototype round 2; whine lowered from 0.40 to 0.28 and grit and air raised, against the 'synth'
    risk): relay click at 3 ms, the motor (whine 'base + top x s', grit gated by the rotor, air) under s = sin^2 across
    exactly move_ticks, the stop clunk on the last tick of the move, a settle tick 45 ms later."""
    p = SERVO[size]
    q = SERVO_TAKES[take - 1]
    m = q['m']
    move = move_ticks * T
    start, stop = 0.010, 0.010 + move
    dur = round(move + 0.40, 3)  # the stop clunk rings up to 0.4 s to -60 dB; the heard length stays within move + 0.3
    b = buf(dur)
    t = tt(dur)
    s = speed_curve(t, start, stop)
    b += motor(t, s, r, p['base'] * m, p['top'] * m, grit=q['grit'], air=q['air'], whine=0.28)
    place(b, relay_click(r, p['click'] * m), 0.003, 0.35)
    cd = q['cd']
    cl = mix(0.35 * norm(modal(p['clunk'] * m, [1, 2.756, 5.404], [1, 0.6, 0.4], [0.045 * cd, 0.03 * cd, 0.018 * cd], r)),
             0.25 * norm(noise_burst(1200 * m, 6000 * m, 0.025 * cd, r)), 0.40 * thunk(r, 130, 95, 0.035 * cd, 0.02))
    place(b, cl, stop)
    place(b, modal(2300 * m, [1, 1.5], [1, 0.4], [0.006, 0.004], r, 0.05), stop + 0.045, 0.12)
    return b


def servo_tick(take, r):
    """A relay tick (the study's relay click): pull-in, the armature's release 20-40 ms later at -6 dB, +10 %. The
    chair's small life sounds: idle (key 52), aftercare (key 11), and the scan ticks."""
    f, rel = [(2300.0, 0.030), (2500.0, 0.024), (2150.0, 0.036), (2700.0, 0.045)][take - 1]
    b = buf(0.16)
    place(b, relay_click(r, f, release_after=rel), 0.003)
    return b


# ---------------------------------------------------------------- gas

GAS = [dict(tau=0.34, puff=1.40, hi=(5000, 15000)), dict(tau=0.33, puff=1.42, hi=(4500, 13000)),
       dict(tau=0.30, puff=1.34, hi=(5500, 16000))]


def gas_hiss(take, r):
    """Gas hiss (prototype round 2): valve tsk; pressure rises in 25 ms, holds 0.3 s, decays; the hiss band slides
    from high to 2-6 kHz as pressure falls; a low flow rumble; a faint 2.9 kHz whistle that dies first; one last
    puff at about 1.4 s, so from t 30 it lands near t 58, after the third beep and as the horn folds (the joke)."""
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


# ---------------------------------------------------------------- DOLORES's voice: beep and ding

def beep(take, r):
    """One soft pip on the house voice at E5 (40 ms hold, 15 ms decay). Keys 40, 47, 54 play it at pitch 1.0, 0.891
    and 0.794 for 'three, two, one': E5, D5, C5, stepwise down to the motif's home note. (Not 1.2 / 1.0 / 0.8: from E5
    that is G-E-C, the NBC figure the company avoids.) Key 94's confirmation bip is the same pip at 1.189 (G5)."""
    b = buf(0.30)
    place(b, fm_note(659.26, 0.015, r, index=(1.8, 0.7), index_tau=0.03, attack=0.003, hold=0.040), 0.003)
    return lowpass(b, 6000)


def ding(take, r):
    """The polite desk ding (key 128): one C6 on the house voice, a twin 2 Hz above (a slow beat, like a cheap
    counter bell), a tiny hammer tick. The most violent moment, followed by the politest sound. Key 230 (lamp to
    green) plays it a major third up, at pitch 1.26 (E6)."""
    b = buf(1.18)
    place(b, fm_note(1046.5, 0.16, r, index=(1.6, 0.25), attack=0.002, partner=(2.0, 0.5)), 0.003)
    place(b, strike(r, 5000), 0.003, 0.12)
    return lowpass(b, 8000)


# ---------------------------------------------------------------- scan, placard, blade, cut

def scan_chirp(take, r):
    """The scanner powering up at t 60: the chair's mains hum (120 Hz) energising under a rising valve tone whose
    fundamental climbs 180 -> 540 Hz (the hum x1.5 to x4.5) with harmonics 1-4 at 1, 0.5, 0.3, 0.15, a little
    1-3 kHz noise, a soft relay click at the start. 14 ticks: it covers the ring's wind-up and start (60-68), so the
    servo key at 64 is not needed."""
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


PLACARD = [dict(f=1350.0, nb=1.0), dict(f=1500.0, nb=1.25), dict(f=1220.0, nb=0.8)]


def placard(take, r):
    """The PRIVACY, PLEASE placard reaching upright (key 110, overshoot 4 degrees, settle by 113): the enamel sign
    hits its stop at 4 ms (thin plate modes, a metallic noise band, a strike), then two smaller rattles at 35 and
    62 ms as it overshoots and settles."""
    f, nb = PLACARD[take - 1]['f'], PLACARD[take - 1]['nb']
    b = buf(0.48)
    plate = mix(norm(modal(f, [1, 1.59, 2.14, 3.9, 5.1], [1, 0.7, 0.5, 0.35, 0.2], [0.05, 0.04, 0.03, 0.02, 0.015], r, 0.01)),
                0.6 * norm(noise_burst(1000 * nb, 7000 * nb, 0.02, r)), 0.4 * strike(r, 2000 * nb))
    for at, g in [(0.004, 0.70), (0.039, 0.28), (0.066, 0.14)]:
        place(b, norm(plate), at, g)
    return b


WINDUP = [dict(top=1000.0, grit=0.45, air=0.18), dict(top=1100.0, grit=0.58, air=0.28), dict(top=920.0, grit=0.34, air=0.11)]


def whine_rise(take, r):
    """The blade drawing back (key 104, to 114): the saw motor spins up (whine 300 -> 1300 Hz, grit, air), a slow
    heavy arm groan under it, and the whole thing is gone by 114: faded over the last 50 ms, so the hold 114-117 and
    the strike 117-120 are silent on purpose (the room holds its breath before the cut)."""
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
    """The cut at t 120: 'shhk'. A strike at 4 ms, an air swish whose band falls 8 -> 2 kHz in 150 ms, a short
    steel shing, the saw motor's whine stopping (2.4 -> 1.1 kHz), a low thunk for weight, the blade's follow-through
    tang at 150 ms (3 ticks), a short air tail. Clean and dry: no flesh, no squelch, nothing wet."""
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


# ---------------------------------------------------------------- the jar: lid pop, clink; the bolts

def lid_pop(take, r):
    """The jar's lid popping open (key 141): a suction pop (a sine falling 500 -> 150 Hz in 40 ms and a puff of air),
    a short dry hinge creak as it flips (stick-slip pings at 1.4 kHz, 140 ms), and the tin lid landing open at 350 ms
    (t 148) with a small tick."""
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
    """The lid slamming shut on the jar (key 227, easeInQuad into contact): a glass tap (strike, glass modes at
    1.65 kHz) with the tin lid's own plate modes, and a small bounce 35 ms later (the lid bounces 3 degrees)."""
    m = TAKE_M[take - 1]
    b = buf(0.50)
    hit = mix(0.4 * strike(r, 3000), 0.5 * norm(glass_modes(r, 1650 * m, decays=(0.05, 0.035, 0.02, 0.012))),
              0.35 * norm(modal(950 * m, [1, 1.59, 2.14, 3.9], [1, 0.6, 0.4, 0.3], [0.03, 0.025, 0.018, 0.012], r)),
              0.2 * norm(noise_burst(1500 * m, 7000 * m, 0.015, r)))
    place(b, norm(hit), 0.004, 1.0)
    place(b, norm(hit), 0.039, 0.25)
    return highpass(b, 400)


def ratchet(take, r):
    """One wrench click bolting the Collar Cam on behind the placard (server t 220, 224, 228): the pawl clank at 4 ms
    (a tooth and a small iron body), two more teeth as the wrench completes its turn (40 ms then 44 ms, each 2 %
    higher, softer), then the pawl drop (a 300 Hz bump). Code raises the pitch a little on each of the three."""
    m = TAKE_M[take - 1]
    b = buf(0.35)
    clank = mix(tooth(r, 3000 * m), 0.5 * norm(modal(1500 * m, [1, 2.756, 5.404], [1, 0.5, 0.3], [0.025, 0.018, 0.010], r)),
                0.3 * norm(noise_burst(2000 * m, 7000 * m, 0.015, r)))
    place(b, norm(clank), 0.004, 1.0)
    place(b, tooth(r, 3000 * m * 1.02), 0.044, 0.55)
    place(b, tooth(r, 3000 * m * 1.04), 0.088, 0.45)
    place(b, thunk(r, 300, 240, 0.02, 0.01), 0.13, 0.35)
    return b


# ---------------------------------------------------------------- the link: antenna, sparks

def antenna(take, r):
    """The antenna rising for the handshake (server t 214; rises 214-219, settles 222): a relay click, a tiny fast
    motor and the tube's slide, an end tick at 260 ms and a settle tick after the overshoot; a static burst as it
    catches the signal. Covers the servo key at 219."""
    top, sg, band, sd = [(1200.0, 0.35, (1000, 4000), 0.26), (1400.0, 0.45, (700, 3000), 0.33)][take - 1]
    dur = 0.55
    b = buf(dur)
    t = tt(dur)
    s = speed_curve(t, 0.005, 0.26)
    b += 0.6 * motor(t, s, r, 500, top, grit=0.30, air=0.14, whine=0.18, air_band=(800, 5000))
    b += 0.20 * norm(bandpass(noise(dur, r), 2000, 5000)) * s
    place(b, relay_click(r, 1700), 0.003, 0.30)
    place(b, modal(2400, [1, 1.5], [1, 0.4], [0.008, 0.005], r), 0.26, 0.40)
    place(b, modal(2500, [1, 1.5], [1, 0.4], [0.006, 0.004], r), 0.31, 0.18)
    st = crackle(r, sd, band[0], band[1], spacing=(0.004, 0.02), width=(0.001, 0.004), first_gain=0.6)
    e = np.clip(np.sin(np.pi * np.clip(tt(len(st) / SR) / (sd + 0.01), 0, 1)), 0, 1)
    place(b, norm(st) * e, 0.08, sg)
    return b


SPARK = [0.20, 0.25, 0.18]


def spark(take, r):
    """A handshake spark (with the electric_spark particles at 222, 229, 236): an arc crackle (bursts 2-8 ms,
    10-60 ms apart, 3-14 kHz, the first the strongest) and a 70 ms mains buzz (2-8 kHz noise gated at 100 Hz)."""
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

BASE = dict(category='BLOCKS', attenuation=16, fixed_range=False, volume=1.0)
KEY_NOTE = ' Keyframe volume 1.0 (the file carries the loudness; the 0.2-0.6 key volumes in chair_anim.py would put it 4-14 dB under its class).'


def ev(**kw):
    d = dict(BASE)
    d.update(kw)
    return d


SOUNDS = [
    ev(id='chair.servo_barrier', file='chair/servo_barrier', fn=lambda take, r: servo(take, r, 'small', 3), takes=3,
       target=-27.0, cls='servo', kind=('servo', 3 * T), fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[0.97, 1.03],
       ticks=[0], subtitle_en='Barrier motor whirs', subtitle_pt='Motor da cancela zune', muffled=False,
       anim_key='replaces key 2 (chair.servo) and the server\'s piston.extend at 0',
       play='Server at t 0 (the barrier\'s 3-tick wind-up 0-3; motor start). Replaces piston.extend at 0; remove the chair.servo key at 2.'),
    ev(id='chair.barrier_drop', file='chair/barrier_drop', fn=lambda take, r: hydraulic(take, r, 2 * T, 10 * T, 'in', 0, 0.30),
       takes=3, target=-27.0, cls='servo (hydraulic stroke)', kind=('servo', 10 * T), fade_ms=20, hit_ms=3,
       ref=PISTON_IN, pitch=[0.98, 1.02], ticks=[3], subtitle_en='Barrier lowers', subtitle_pt='Cancela desce', muffled=False,
       play='Server at t 3: the valve opens in the hold (3-5), the stroke accelerates 5-13 into contact and lets go there; chair.barrier_latch supplies the hit at 13. Narrow pitch range: the end must stay on 13.'),
    ev(id='chair.barrier_latch', file='chair/barrier_latch', fn=lambda take, r: barrier_clamp(take, r, 'latch'), takes=3,
       target=-22.0, cls='clamp', kind='latch', fade_ms=12, hit_ms=100, preroll_ticks=2, ref=TRAPDOOR_CLOSE,
       pitch=[0.94, 1.06], ticks=[13], subtitle_en='Barrier clamps shut', subtitle_pt='Cancela trava', muffled=False,
       play='Server at t 11: two ratchet teeth as the tip rides over the pawl, the strike lands at 100 ms on the contact tick 13. Replaces iron_trapdoor.close at 13.'),
    ev(id='chair.barrier_settle', file='chair/barrier_settle', fn=lambda take, r: barrier_clamp(take, r, 'settle'), takes=3,
       target=-24.0, cls='clamp (lighter)', kind='latch', fade_ms=12, hit_ms=100, preroll_ticks=2, ref=TRAPDOOR_CLOSE,
       pitch=[0.94, 1.06], ticks=[19], subtitle_en=None, subtitle_pt=None, muffled=False,
       play='Server at t 17: the bounce falls back onto the latch, strike at 100 ms on t 19. Replaces the second trapdoor close at 19. No subtitle (part of the same clamping).'),
    ev(id='chair.servo_horn', file='chair/servo_horn', fn=lambda take, r: servo(take, r, 'small', 6), takes=3, target=-27.0,
       cls='servo', kind=('servo', 6 * T), fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[1.0, 1.0],
       pitch_per_play=[1.0, 1.2], ticks=[21, 56], subtitle_en='Gas horn swings', subtitle_pt='Corneta de gás gira', muffled=True,
       anim_key='key 21: change chair.servo_tick to chair.servo_horn; add a key 56 chair.servo_horn|1.0|1.2',
       play='Key 21 at pitch 1.0 (the horn stands up 21-27) and key 56 at pitch 1.2 (it folds 56-61: pitch 1.2 makes the 6-tick file last 5 ticks).'),
    ev(id='chair.gas_hiss', file='chair/gas_hiss', fn=gas_hiss, takes=3, target=-24.0, cls='hiss', kind='hiss',
       fade_ms=60, hit_ms=3, ref=FIZZ, pitch=[0.97, 1.03], ticks=[30], subtitle_en='Anaesthetic hisses',
       subtitle_pt='Anestésico chia', muffled=True,
       play='Server at t 30 at the gas locator. Its last little puff lands 1.34-1.46 s in (t 57-59), after the hiss has died and the third beep (the joke): do not cut it short. Replaces fire.extinguish.'),
    ev(id='chair.beep', file='chair/beep', fn=beep, takes=1, target=-30.0, cls='tick (blip)', kind='click',
       fade_ms=10, hit_ms=3, ref=dict(event='block.note_block.xylophone', file='note__xylobone.ogg', vol=1.0, pitch=1.0),
       pitch=[1.0, 1.0], pitch_per_play=[1.0, 0.8909, 0.7937, 1.1892], ticks=[40, 47, 54, 94],
       subtitle_en='DOLORES counts down', subtitle_pt='DOLORES faz a contagem', muffled=True,
       anim_key='keys 40, 47, 54: pitch 1.0, 0.8909, 0.7937 instead of 1.2, 1.0, 0.8; key 94 (chair.bip): change to chair.beep|1.0|1.1892',
       play='Keys 40, 47, 54 at pitch 1.0, 0.8909, 0.7937 (E5, D5, C5) and the confirmation bip at 94 at 1.1892 (G5). No random pitch.' + KEY_NOTE),
    ev(id='chair.servo_tick', file='chair/servo_tick', fn=servo_tick, takes=4, target=-31.0, cls='relay click',
       kind='click', fade_ms=8, hit_ms=3,
       ref=dict(event='item.spyglass.use', file='item__spyglass__use.ogg', vol=1.0, pitch=1.0),
       pitch=[0.94, 1.06], ticks=[68, 74, 80, 86], subtitle_en=None, subtitle_pt=None, muffled=True,
       anim_key='keys 68-86 (chair.scan_tick): change to chair.servo_tick|0.6|1.3; idle key 52 and aftercare key 11 already name it (volume 0.5)',
       play='The scan ticks (keys 68, 74, 80, 86) at volume 0.6, pitch 1.3; idle (52) and aftercare (11) at volume 0.5 (idle life, about -37). No subtitle: small life.'),
    ev(id='chair.scan_chirp', file='chair/scan_chirp', fn=scan_chirp, takes=1, target=-26.0, cls='servo (scanner)',
       kind='stinger', fade_ms=30, hit_ms=3,
       ref=dict(event='block.beacon.activate', file='block__beacon__activate.ogg', vol=0.7, pitch=1.8),
       pitch=[1.0, 1.0], ticks=[60], subtitle_en='Scanner chirps', subtitle_pt='Scanner apita', muffled=True,
       anim_key='remove key 64 (chair.servo): the chirp covers the ring\'s start',
       play='Server at t 60, replacing beacon.activate. 14 ticks long. The scan loop (hum x1.5, 1 Hz sweep) is not in this batch.'),
    ev(id='chair.latch', file='chair/latch', fn=latch, takes=2, target=-27.0, cls='relay and small latch', kind='latch',
       fade_ms=10, hit_ms=3, ref=CLICK, pitch=[0.97, 1.03], ticks=[100], subtitle_en='Something latches',
       subtitle_pt='Algo trava', muffled=True,
       play='Key 100 (the point of no return, with consume()).' + KEY_NOTE),
    ev(id='chair.whine_rise', file='chair/whine_rise', fn=whine_rise, takes=3, target=-26.0, cls='servo (saw spin-up)',
       kind=('servo', 10 * T), fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[1.0, 1.0], ticks=[104],
       subtitle_en='Blade winds up', subtitle_pt='Lâmina toma impulso', muffled=True,
       play='Key 104, fixed pitch 1.0 (a pitch below 1 would stretch it past 114). Silent from 500 ms (t 114): 114-120 must be silent, so stop every chair loop by 114 too.' + KEY_NOTE),
    ev(id='chair.placard', file='chair/placard', fn=placard, takes=3, target=-27.5, cls='servo (light plate and rattle)',
       kind='latch', fade_ms=15, hit_ms=4,
       ref=dict(event='block.iron_trapdoor.open', file='block__iron_trapdoor__open1.ogg', vol=0.9, pitch=0.95),
       pitch=[0.95, 1.05], ticks=[110, 222], subtitle_en='Privacy placard flips up', subtitle_pt='Placa de privacidade sobe',
       muffled=True, anim_key='key 110 already names it; add a key at the encore\'s arrival (about 222)',
       play='Key 110: the placard reaches upright at 4 ms, two rattles as it settles. Also at the encore (it comes up again about 222).' + KEY_NOTE),
    ev(id='chair.cut', file='chair/cut', fn=cut, takes=2, target=-18.0, cls='signature moment', kind='latch',
       fade_ms=40, hit_ms=4, ref=dict(event='block.smithing_table.use', file='block__smithing_table__smithing_table2.ogg', vol=1.0, pitch=1.0),
       pitch=[0.95, 1.05], attenuation=24, fixed_range=True, ticks=[120], subtitle_en='Blade separates',
       subtitle_pt='Lâmina separa', muffled=False,
       play='Server at t 120, the contact tick, to everyone except the patient (the patient hears total silence for 1 s: no muffled version, nothing). Register fixed-range 24. Replaces booth_cut and sheep.shear.'),
    ev(id='chair.ding', file='chair/ding', fn=ding, takes=1, target=-27.0, cls='done chime', kind='stinger',
       fade_ms=50, hit_ms=3, ref=dict(event='block.note_block.bell', file='note__bell.ogg', vol=1.0, pitch=1.0),
       pitch=[1.0, 1.0], pitch_per_play=[1.0, 1.2599], ticks=[128, 230], subtitle_en='DOLORES dings politely',
       subtitle_pt='DOLORES toca a campainha', muffled=False,
       anim_key='key 230: pitch 1.2599 instead of 1.3 (a major third up, the motif\'s E)',
       play='Key 128 at pitch 1.0, to everyone (the patient is in the silent second: exclude them if the client silence is built); key 230 (lamp green) at 1.2599. No random pitch.' + KEY_NOTE),
    ev(id='chair.lid_pop', file='chair/lid_pop', fn=lid_pop, takes=2, target=-29.0, cls='tick (pop and hinge)',
       kind=('servo', 7 * T), fade_ms=15, hit_ms=3,
       ref=dict(event='item.bottle.empty', file='item__bottle__empty1.ogg', vol=1.0, pitch=1.0),
       pitch=[0.95, 1.05], ticks=[141], subtitle_en='Jar lid pops open', subtitle_pt='Tampa do pote abre', muffled=True,
       play='Key 141: the suction pop at 3 ms, the hinge creaks as it flips, the lid lands open at 350 ms (t 148).' + KEY_NOTE),
    ev(id='chair.saline_glug', vanilla=['minecraft:item/bottle/fill1', 'minecraft:item/bottle/fill2', 'minecraft:item/bottle/fill3', 'minecraft:item/bottle/fill4'],
       vanilla_measure=['item__bottle__fill1.ogg', 'item__bottle__fill2.ogg', 'item__bottle__fill3.ogg'], target=-24.0,
       cls='glug', pitch=[0.80, 0.90], ticks=[140, 198, 204], subtitle_en='Saline glugs', subtitle_pt='Soro gorgoleja',
       muffled=True,
       play='Vanilla bottle-fill recordings referenced by name in sounds.json (a pour is turbulence, which synthesis does badly). Server at t 140, 198, 204 at the pour locator, pitch 0.80-0.90 (a bigger vessel than a bottle). Replaces the plain item.bottle.fill calls.'),
    ev(id='chair.clamp', file='chair/clamp', fn=claw_clamp, takes=3, target=-25.0, cls='clamp release (soft clamp)',
       kind='latch', fade_ms=12, hit_ms=4,
       ref=dict(event='block.copper_trapdoor.close', file='block__copper_trapdoor__toggle1.ogg', vol=1.0, pitch=0.95),
       pitch=[0.94, 1.06], ticks=[148], subtitle_en='Pincer grips', subtitle_pt='Pinça segura', muffled=True,
       play='Key 148: the jaw meets the forehead at 4 ms; the solenoid locks 60 ms later.' + KEY_NOTE),
    ev(id='chair.hydraulic', file='chair/hydraulic', fn=lambda take, r: hydraulic(take, r, 0.01, 0.01 + 8 * T, 'sin2', 0.6, 0.30),
       takes=3, target=-27.0, cls='servo (hydraulic stroke)', kind=('servo', 8 * T), fade_ms=20, hit_ms=3,
       ref=PISTON_IN, pitch=[1.0, 1.0], pitch_per_play=[1.0, 1.0, 1.1429], ticks=[152, 181, 206],
       subtitle_en='Hood hydraulics hiss', subtitle_pt='Hidráulica do capô chia', muffled=True,
       anim_key='keys 152, 181, 206 already name it: pitch 1.0, 1.0 (not 0.9), 1.1429 (not 1.1)',
       play='Keys 152 (hood lifts 152-160), 181 (lowers 181-189) at pitch 1.0 and 206 (rises 206-213) at 1.1429 so the 8-tick stroke lasts 7; the soft stop lands on the move\'s last tick.' + KEY_NOTE),
    ev(id='chair.servo', file='chair/servo', fn=lambda take, r: servo(take, r, 'large', 11), takes=3, target=-27.0,
       cls='servo', kind=('servo', 11 * T), fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[0.97, 1.03], ticks=[166, 217],
       subtitle_en='Hood swings', subtitle_pt='Capô gira', muffled=True,
       anim_key='key 166: pitch 1.0 (not 0.9); add a key 217 chair.servo|1.0|1.0 (the swing home); keys 2, 64, 219 move to other events (see their entries)',
       play='Keys 166 (swing to the jar, 166-177) and 217 (swing home, 217-228): the stop clunk lands on 177 and 228. Pitch 0.97-1.03 keeps the clunk within a third of a tick.' + KEY_NOTE),
    ev(id='chair.plop', vanilla=['minecraft:block/bubble_column/bubble1', 'minecraft:block/bubble_column/bubble2', 'minecraft:block/bubble_column/bubble3'],
       vanilla_measure=['block__bubble_column__bubble1.ogg', 'block__bubble_column__bubble2.ogg'], target=-27.0,
       cls='glass clink class (small liquid event)', pitch=[0.60, 0.70], attenuation=12, ticks=[197],
       subtitle_en='Head plops in', subtitle_pt='Cabeça cai no soro', muffled=True,
       play='Vanilla bubble-pop recordings by name, pitched down to one big bubble, at key 197 when the head drops into the jar. Key volume 1.0.'),
    ev(id='chair.antenna', file='chair/antenna', fn=antenna, takes=2, target=-28.0, cls='servo (small, with static)',
       kind=('servo', 6 * T), fade_ms=20, hit_ms=3, ref=PISTON_OUT, pitch=[0.97, 1.03], ticks=[214],
       subtitle_en='Antenna extends', subtitle_pt='Antena se estende', muffled=True,
       anim_key='remove key 219 (chair.servo): this file covers the rise',
       play='Server at t 214, replacing beacon.power_select.'),
    ev(id='chair.ratchet', file='chair/ratchet', fn=ratchet, takes=3, target=-27.0, cls='ratchet (whole)', kind='latch',
       fade_ms=10, hit_ms=4, ref=CLICK, pitch=[1.0, 1.0], pitch_per_play=[1.0, 1.03, 1.06], ticks=[220, 224, 228],
       subtitle_en='Wrench ratchets', subtitle_pt='Chave aperta o parafuso', muffled=True,
       play='Server at t 220, 224, 228 at pitch 1.0, 1.03, 1.06 (tension rising), a different take each time. Replaces chain.place.'),
    ev(id='chair.spark', file='chair/spark', fn=spark, takes=3, target=-24.5, cls='hiss (arc crackle)', kind='latch',
       fade_ms=10, hit_ms=4, ref=FIZZ, pitch=[0.94, 1.06], ticks=[222, 229, 236], subtitle_en='Antenna sparks',
       subtitle_pt='Antena solta faíscas', muffled=True,
       anim_key='add sound keys chair.spark|1.0|1.0 at 222, 229, 236 beside the electric_spark particle keys',
       play='Keys 222, 229, 236 at the spark locator, with the particles. Random take and pitch.'),
    ev(id='chair.clink', file='chair/clink', fn=clink, takes=3, target=-26.5, cls='glass clink', kind='latch',
       fade_ms=15, hit_ms=4, ref=LANTERN, pitch=[0.94, 1.06], ticks=[227], subtitle_en='Jar lid clinks shut',
       subtitle_pt='Tampa do pote fecha', muffled=True, play='Key 227: the lid slams onto the jar.' + KEY_NOTE),
    ev(id='chair.unlatch', file='chair/unlatch', fn=unlatch, takes=3, target=-24.0, cls='clamp release',
       kind='latch', fade_ms=12, hit_ms=50,
       ref=dict(event='block.iron_door.open', file='block__iron_door__open1.ogg', vol=0.9, pitch=0.95),
       pitch=[0.97, 1.03], ticks=[232], subtitle_en='Barrier unlatches', subtitle_pt='Cancela destrava', muffled=False,
       play='Key 232: the latch vents at once and lets go at 50 ms (t 233) as the barrier starts to lift. Also the abort (with chair.barrier_lift).' + KEY_NOTE),
    ev(id='chair.barrier_lift', file='chair/barrier_lift', fn=lambda take, r: hydraulic(take, r, 0.01, 0.01 + 6 * T, 'sin2', 0.8, 0.15),
       takes=3, target=-27.0, cls='servo (hydraulic stroke)', kind=('servo', 6 * T), fade_ms=20, hit_ms=3,
       ref=PISTON_IN, pitch=[0.98, 1.02], ticks=[233], subtitle_en='Barrier rises', subtitle_pt='Cancela sobe', muffled=False,
       play='Server at t 233: the barrier lifts 233-239 and stops upright on 239.'),
]

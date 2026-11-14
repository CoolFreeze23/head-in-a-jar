"""The mecha family: the Hopper (and any later frame that shares its parts), keyed to its clips.

The Hopper is a light, bird-legged walker: an ivory enamel pod on two reverse-jointed legs, the pilot's jar standing in
the open on top in a brass collar, held by one brass hoop that comes down over the lid and two rubber-padded clamps on
the glass. Its sounds are the house parts at a smaller size: servos for the arms and the chin sensor, hydraulics for
the legs, a ratchet for the Pogo Protocol's crouch, relays for the lamps, struck iron and brass for the hoop and the
flaps, glass on metal for the jar. A small drivetrain hum loops under the walk and follows its speed.

Keyframe sounds hit at 0-5 ms. Server-played contact sounds hit at 3 ms, except the dragged foot's scrape, which
starts 4 ticks before the plant (`preroll_ticks`). One tick is 50 ms; a move sound is as long as its move plus the
stop's ring. Takes differ by construction (per-take tables), not by luck of the seed.

The motor is a loop: one file of exactly 2.0 s in which every periodic layer completes a whole number of cycles and
every noise layer is circular (band-limited in the frequency domain over the whole loop), finished by `finish_loop`
(no fades, no high-pass transient) instead of the one-shot chain.
"""
import numpy as np
from nsynth import *
from recipes_chair import hydraulic

T = TICK


# ---------------------------------------------------------------- loop helpers

def circ_noise(n, r, lo, hi):
    """White noise band-limited to lo-hi Hz over exactly n samples: periodic in n, so it loops without a seam."""
    X = np.fft.rfft(r.standard_normal(n))
    f = np.fft.rfftfreq(n, 1.0 / SR)
    X[(f < lo) | (f > hi)] = 0
    return np.fft.irfft(X, n)


def finish_loop(x, target_mmax, fade_out_ms=0, ceiling_db=-1.5):
    """The loop's finishing chain: DC out, loudness to the target M max measured on the loop played twice, no fades and
    no filter (a filter's start-up transient or an edge fade would put a tick on every seam). Refuses a loop whose peak
    would pass the ceiling rather than limit it (a limiter's release would not wrap round)."""
    x = x - x.mean()
    for _ in range(2):
        x = x * 10 ** ((target_mmax - m_max(np.tile(x, 2))) / 20)
    if np.max(np.abs(x)) > db(ceiling_db):
        raise ValueError(f'loop peak {20 * np.log10(np.max(np.abs(x))):.2f} dBFS over the ceiling: lower the crest')
    return x


MOTOR = dict(f0=96.0, partner=96.5, mesh=7, rotor=24.0, pump=4.0, swell=0.5)


def motor_loop(take, r):
    """The drivetrain hum of a small walker at full speed, 2.0 s, a whole number of cycles of every layer: a 96 Hz hum
    (harmonics 1-8, 1/k^1.3) and a 96.5 Hz partner that beats once per loop; the gear mesh (7th harmonic, 672 Hz) with
    a 3 Hz wobble; brush grit (2.5-7 kHz circular noise) gated by the rotor at 24 Hz; the leg pump's soft chuff at 4 Hz
    (low circular noise); mechanical air; a 0.5 Hz swell. Every frequency is a multiple of 0.5 Hz."""
    p = MOTOR
    L = 2.0
    n = int(L * SR)
    t = np.arange(n) / SR
    hum = sum(np.sin(2 * np.pi * p['f0'] * k * t + r.uniform(0, 6.28)) / k ** 1.3 for k in range(1, 9))
    partner = sum(np.sin(2 * np.pi * p['partner'] * k * t + r.uniform(0, 6.28)) / k ** 1.6 for k in range(1, 5))
    fm = p['f0'] * p['mesh']
    mesh = np.sin(2 * np.pi * fm * t + 0.6 * np.sin(2 * np.pi * 3.0 * t)) * (0.7 + 0.3 * np.sin(2 * np.pi * 1.5 * t))
    gate = (0.5 + 0.5 * np.cos(2 * np.pi * p['rotor'] * t)) ** 4
    grit = circ_noise(n, r, 2500, 7000) * gate
    chuff = circ_noise(n, r, 60, 400) * (0.5 + 0.5 * np.cos(2 * np.pi * p['pump'] * t)) ** 3
    air = circ_noise(n, r, 300, 3000)
    swell = 1 + 0.08 * np.sin(2 * np.pi * p['swell'] * t)
    x = (1.0 * norm(hum) + 0.35 * norm(partner) + 0.10 * norm(mesh) + 0.10 * norm(grit) + 0.22 * norm(chuff)
         + 0.07 * norm(air)) * swell
    return x


def soft(x, drive=2.0):
    """Gentle saturation for the heavy crashes: tanh on the normalised signal, so the first transient's peak sits
    about 3 dB nearer the body and the file reaches its loudness without the limiter (which would only flatten it)."""
    p = max(np.max(np.abs(x)), 1e-12)
    return np.tanh(drive * x / p) / np.tanh(drive) * p


# ---------------------------------------------------------------- footfalls

STEP = [dict(m=1.00, dk=1.00, rat=(0.050, 0.064)), dict(m=1.09, dk=1.25, rat=(0.056, 0.074)),
        dict(m=0.92, dk=0.85, rat=(0.046, 0.058)), dict(m=0.82, dk=1.40, rat=(0.060, 0.080))]


def step(take, r):
    """One footfall of the light frame (the metal layer; the block's own step plays with it): the steel foot pad's
    strike at 3 ms, the weight (a thunk 150 -> 85 Hz), the foot plate's short modes at about 620 Hz, grit of the sole on
    the ground, a little hydraulic 'tss' as the leg takes the load, and the brass hoop's rattle a tick later."""
    q = STEP[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(0.62)
    h = 0.003
    place(b, strike(r, 1200 * m), h, 0.30)
    place(b, thunk(r, 150 * m, 85 * m, 0.060, 0.022), h, 0.60)
    place(b, norm(modal(620 * m, [1, 2.3, 3.9, 5.6], [1, 0.5, 0.3, 0.15], [0.040 * dk, 0.030 * dk, 0.020 * dk, 0.012 * dk], r, 0.01)), h, 0.45)
    place(b, norm(noise_burst(300 * m, 2500 * m, 0.015, r)), h, 0.30)
    place(b, norm(noise_burst(2500, 8000, 0.030, r, attack=0.006)), h + 0.015, 0.06)
    for k, at in enumerate(q['rat']):
        place(b, norm(modal(2400 * m * (1 + 0.06 * k), [1, 1.6], [1, 0.4], [0.008, 0.005], r)), at, 0.12 / (k + 1))
    return b


STEP_LIGHT = [dict(m=1.00, dk=1.00), dict(m=1.10, dk=1.30), dict(m=0.91, dk=0.85), dict(m=1.20, dk=1.10)]


def step_light(take, r):
    """Half a footfall: a toe set down while marching in place. A smaller thunk (180 -> 120 Hz), the foot plate at about
    800 Hz, short, a touch of sole grit; no rattle."""
    q = STEP_LIGHT[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(0.40)
    h = 0.003
    place(b, strike(r, 1500 * m), h, 0.20)
    place(b, thunk(r, 180 * m, 120 * m, 0.035, 0.015), h, 0.55)
    place(b, norm(modal(800 * m, [1, 2.3, 3.9], [1, 0.45, 0.2], [0.025 * dk, 0.018 * dk, 0.010 * dk], r, 0.01)), h, 0.45)
    place(b, norm(noise_burst(400 * m, 3000 * m, 0.010, r)), h, 0.25)
    return b


STEP_HEAVY = [dict(m=1.00, dk=1.00, bits=(0.07, 0.12, 0.19)), dict(m=1.10, dk=1.20, bits=(0.08, 0.15, 0.24)),
              dict(m=0.86, dk=0.85, bits=(0.06, 0.10, 0.16))]


def step_heavy(take, r):
    """A heavy landing (and the pogo landing): the whole frame bottoms out. A deep thunk (110 -> 50 Hz), the pod's iron
    body and plate (300 / 520 Hz), the strike, ground debris (low-passed noise), the legs' hydraulics absorbing ('tsh'),
    then the frame's loose parts clattering 60-250 ms after (brass hoop, arm pins)."""
    q = STEP_HEAVY[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(1.40)
    h = 0.003
    place(b, strike(r, 1000 * m), h, 0.22)
    place(b, thunk(r, 110 * m, 50 * m, 0.10, 0.03), h, 0.35)
    place(b, norm(iron_body(r, 340 * m, 620 * m, 1.1 * dk, band_scale=m)), h, 0.75)
    place(b, norm(modal(900 * m, [1, 1.59, 2.14], [1, 0.6, 0.4], [0.04 * dk, 0.03 * dk, 0.02 * dk], r)), h, 0.30)
    place(b, norm(lowpass(noise(0.8, r), 3500) * exp_env(0.8, 0.07, 0.002)), h, 0.45)
    place(b, norm(noise_burst(1500, 6000, 0.05, r, attack=0.01)), h + 0.02, 0.12)
    for k, at in enumerate(q['bits']):
        place(b, norm(modal(2100 * m * (1 + 0.07 * k), [1, 1.6, 2.7], [1, 0.4, 0.2], [0.012, 0.008, 0.005], r)), at, 0.16 / (k + 1) ** 0.7)
    return soft(b, 3.0)


STEP_DRAG = [dict(m=1.00, rough=55.0), dict(m=1.12, rough=75.0), dict(m=0.90, rough=40.0)]


def step_drag(take, r):
    """The limp's dragged toe: a steel toe scraping along the ground for 4 ticks (band-passed noise roughened at 40-75 Hz,
    growing), ending at 200 ms in the toe catching (a small strike and knock) on the plant tick, where the step itself
    takes over. Starts 4 ticks before the plant."""
    q = STEP_DRAG[take - 1]
    m = q['m']
    dur = 0.62
    b = buf(dur)
    t = tt(dur)
    hit = 4 * T
    env = np.where(t < hit, 0.35 + 0.65 * (t / hit) ** 1.5, np.exp(-(t - hit) / 0.006)) * np.clip(t / 0.003, 0, 1)
    rough = 0.4 + 0.6 * np.abs(lowpass(noise(dur, r), q['rough'])) / 0.3
    sc = bandpass(noise(dur, r), 500 * m, 3000 * m) * env * np.clip(rough, 0, 2)
    b += 0.30 * norm(sc)
    place(b, strike(r, 1500 * m), hit, 0.40)
    place(b, norm(modal(1050 * m, [1, 2.3, 3.7], [1, 0.4, 0.2], [0.018, 0.012, 0.008], r)), hit, 0.50)
    place(b, thunk(r, 170, 110, 0.03, 0.015), hit, 0.20)
    return b


TAP = [1.00, 1.12, 0.90]


def tap(take, r):
    """A soft rubber foot tap (the boot's toe taps): a small low thunk (210 -> 140 Hz), a rubber body at about 320 Hz,
    a touch of felt; low-passed, no ring."""
    m = TAP[take - 1]
    b = buf(0.32)
    h = 0.003
    place(b, thunk(r, 210 * m, 140 * m, 0.022, 0.012), h, 0.60)
    place(b, norm(modal(320 * m, [1, 2.1, 3.3], [1, 0.35, 0.15], [0.020, 0.012, 0.007], r, 0.02)), h, 0.45)
    place(b, norm(lowpass(noise(0.05, r), 1000) * exp_env(0.05, 0.005, 0.001)), h, 0.20)
    return lowpass(b, 2500)


LAND = [dict(m=1.00, gap=0.016, rat=0.10), dict(m=1.10, gap=0.024, rat=0.12), dict(m=0.90, gap=0.010, rat=0.085)]


def land_light(take, r):
    """A short drop: both feet land a few ms apart (thunk 140 -> 70 Hz, the foot plates), the legs' hydraulics absorb
    ('tsh' 30 ms on), the hoop rattles once 100 ms later."""
    q = LAND[take - 1]
    m = q['m']
    b = buf(0.80)
    h = 0.003
    place(b, strike(r, 1200 * m), h, 0.45)
    place(b, thunk(r, 140 * m, 70 * m, 0.060, 0.025), h, 0.35)
    for k, at in enumerate((h, h + q['gap'])):
        place(b, norm(modal(650 * m * (1 + 0.05 * k), [1, 2.3, 3.9], [1, 0.5, 0.3], [0.045, 0.032, 0.02], r, 0.01)), at, 0.55 - 0.08 * k)
        place(b, norm(noise_burst(500 * m, 4000 * m, 0.012, r)), at, 0.30)
    place(b, norm(noise_burst(2000, 7000, 0.035, r, attack=0.008)), 0.030, 0.12)
    place(b, tooth(r, 2300 * m), q['rat'], 0.12)
    place(b, tooth(r, 2450 * m), q['rat'] + 0.016, 0.07)
    return b


# ---------------------------------------------------------------- servos and relays

ARM = dict(base=230.0, top=700.0, clunk=560.0, click=1900.0)
MS_TAKES = [dict(m=1.00, grit=0.42, air=0.14, cd=1.00), dict(m=1.07, grit=0.55, air=0.24, cd=1.25),
            dict(m=0.93, grit=0.32, air=0.10, cd=0.80), dict(m=1.13, grit=0.48, air=0.18, cd=1.10)]


def mservo(take, r, move_ticks, p=ARM, clunk_gain=1.0):
    """An arm or limb servo move (the chair's servo at the Hopper's size): relay click at 3 ms, the motor under
    s = sin^2 across exactly move_ticks, the stop clunk on the last tick, a settle tick 45 ms after it."""
    q = MS_TAKES[take - 1]
    m = q['m']
    start = 0.010
    stop = start + move_ticks * T
    dur = round(stop + 0.39, 3)
    b = buf(dur)
    t = tt(dur)
    s = speed_curve(t, start, stop)
    b += motor(t, s, r, p['base'] * m, p['top'] * m, grit=q['grit'], air=q['air'], whine=0.28)
    place(b, relay_click(r, p['click'] * m), 0.003, 0.35)
    cd = q['cd']
    cl = mix(0.35 * norm(modal(p['clunk'] * m, [1, 2.756, 5.404], [1, 0.6, 0.4], [0.045 * cd, 0.03 * cd, 0.018 * cd], r)),
             0.25 * norm(noise_burst(1200 * m, 6000 * m, 0.025 * cd, r)), 0.40 * thunk(r, 130, 95, 0.035 * cd, 0.02))
    place(b, cl, stop, clunk_gain)
    place(b, modal(2300 * m, [1, 1.5], [1, 0.4], [0.006, 0.004], r, 0.05), stop + 0.045, 0.12)
    return b


def servo_chord(take, r):
    """The boot's self-test: three servos spin up together, their whines a major triad (C, E, G: the company's motif),
    rise a little as one for 7 ticks and stop on one shared clunk, with the settle tick after. A relay starts it."""
    dur = 0.80
    b = buf(dur)
    t = tt(dur)
    s = speed_curve(t, 0.010, 0.010 + 7 * T)
    for f, g in ((130.81, 1.0), (164.81, 0.8), (196.0, 0.7)):
        b += g * motor(t, s, r, f, 0.12 * f, grit=0.22, air=0.08, whine=0.45, harmonics=12)
    place(b, relay_click(r, 1800), 0.003, 0.45)
    stop = 0.010 + 7 * T
    cl = mix(0.35 * norm(modal(560, [1, 2.756, 5.404], [1, 0.6, 0.4], [0.045, 0.03, 0.018], r)),
             0.25 * norm(noise_burst(1200, 6000, 0.025, r)), 0.40 * thunk(r, 130, 95, 0.035, 0.02))
    place(b, cl, stop, 1.6)
    place(b, modal(2300, [1, 1.5], [1, 0.4], [0.006, 0.004], r, 0.05), stop + 0.045, 0.18)
    return b


SETTLE = [dict(m=1.00, at=0.050, cd=1.00), dict(m=1.22, at=0.062, cd=1.00), dict(m=1.45, at=0.042, cd=1.00)]


def servo_settle(take, r):
    """A move coming to rest: a soft clunk at 4 ms (small iron modes at 520 Hz, a noise band, a thunk) and the settle
    tick 40-60 ms later."""
    q = SETTLE[take - 1]
    m = q['m']
    b = buf(0.45)
    cd = q['cd']
    cl = mix(0.35 * norm(modal(520 * m, [1, 2.756, 5.404], [1, 0.6, 0.4], [0.035 * cd, 0.025 * cd, 0.015 * cd], r)),
             0.30 * norm(noise_burst(1200 * m, 6000 * m, 0.02, r)), 0.35 * thunk(r, 130, 95, 0.03 * cd, 0.02))
    place(b, cl, 0.004, 1.0)
    place(b, modal(2300 * m, [1, 1.5], [1, 0.4], [0.006, 0.004], r, 0.05), q['at'], 0.22)
    return b


SAG = [dict(m=1.00, creak=950.0, thump=0.20), dict(m=1.10, creak=1150.0, thump=0.23), dict(m=0.90, creak=820.0, thump=0.18)]


def servo_sag(take, r):
    """A servo giving up under load (the dry fire's sag, the vent's slump): the brake lets go (a small tooth), the
    motor spins down as the arm drops (whine falling with speed), the joint creaks (stick-slip pings), and the arm
    bottoms out softly at about 200 ms."""
    q = SAG[take - 1]
    m = q['m']
    dur = round(q['thump'] + 0.42, 3)
    b = buf(dur)
    t = tt(dur)
    s = np.interp(t, [0, 0.01, q['thump'], q['thump'] + 0.04, dur], [0.0, 0.8, 0.15, 0.0, 0.0])
    b += motor(t, s, r, 160 * m, 420 * m, grit=0.40, air=0.16, whine=0.24)
    place(b, tooth(r, 2200 * m), 0.003, 0.25)
    at = 0.03
    while at < q['thump'] - 0.02:
        place(b, norm(modal(q['creak'] * r.uniform(0.95, 1.05), [1, 2.3], [1, 0.3], [0.006, 0.004], r)), at, 0.16 * r.uniform(0.5, 1.0))
        at += r.uniform(0.020, 0.035)
    place(b, mix(norm(modal(420 * m, [1, 2.756], [1, 0.4], [0.03, 0.02], r)), 0.7 * thunk(r, 140, 90, 0.035, 0.015)), q['thump'], 0.45)
    return b


CATCH = [dict(m=1.00, nt=3), dict(m=1.25, nt=2), dict(m=0.90, nt=4)]


def servo_catch(take, r):
    """The limp's bad leg catching: a gear-skip 'khk' at 3 ms (a strike, two to four teeth 8 ms apart, a small iron
    body), the motor stalling for two ticks and picking up again."""
    q = CATCH[take - 1]
    m = q['m']
    dur = 0.62
    b = buf(dur)
    t = tt(dur)
    s = np.interp(t, [0, 0.01, 0.06, 0.13, 0.22, 0.30, dur], [0.0, 0.6, 0.10, 0.10, 0.5, 0.0, 0.0])
    b += motor(t, s, r, 200 * m, 600 * m, grit=0.40, air=0.12, whine=0.28)
    place(b, strike(r, 1500), 0.003, 0.30)
    for k in range(q['nt']):
        place(b, tooth(r, 2300 * m * (1 - 0.06 * k)), 0.003 + 0.008 * k, 0.20)
    place(b, norm(iron_body(r, 640 * m, 1000 * m, 0.5, band_scale=m)), 0.003, 0.40)
    return b


FAIL = [dict(m=1.00, drops=(0.15, 0.32, 0.50), end=0.72), dict(m=1.12, drops=(0.12, 0.27, 0.45, 0.58), end=0.80)]


def servo_fail(take, r):
    """A servo dying: the whine falls from full speed to nothing over about 0.7 s and stutters as the windings short
    (smooth dropouts), the gears grind (irregular teeth, slowing), one last clunk as it seizes, and a small electric
    fizz."""
    q = FAIL[take - 1]
    m = q['m']
    dur = round(q['end'] + 0.45, 3)
    b = buf(dur)
    t = tt(dur)
    s = np.clip(1 - t / q['end'], 0, 1) ** 1.2 * np.clip(t / 0.01, 0, 1)
    for d in q['drops']:
        s = s * (1 - 0.8 * np.exp(-((t - d) / 0.012) ** 2))
    b += motor(t, s, r, 220 * m, 650 * m, grit=0.50, air=0.16, whine=0.30)
    place(b, relay_click(r, 1700 * m), 0.003, 0.30)
    at = 0.05
    while at < q['end'] - 0.08:
        place(b, tooth(r, 2000 * m * r.uniform(0.9, 1.1)), at, 0.16 * float(np.interp(at, t, s)) ** 0.5)
        at += r.uniform(0.03, 0.06) * (1 + 1.5 * at / q['end'])
    cl = mix(0.4 * norm(modal(430 * m, [1, 2.756, 5.404], [1, 0.6, 0.4], [0.04, 0.03, 0.018], r)),
             0.5 * thunk(r, 140, 90, 0.035, 0.02), 0.2 * strike(r, 1500))
    place(b, cl, q['end'], 0.55)
    place(b, norm(crackle(r, 0.12, 2000, 9000)), q['end'] + 0.02, 0.18)
    return b


RELAY = [(2600.0, 0.028), (2850.0, 0.022), (2400.0, 0.034), (3100.0, 0.040)]


def relay(take, r):
    """A relay: pull-in click and the armature's release 22-40 ms later (boot lamps, one per fitted module)."""
    f, rel = RELAY[take - 1]
    b = buf(0.16)
    place(b, relay_click(r, f, release_after=rel), 0.003)
    return b


STICK = [1.00, 1.10, 0.90]


def servo_tick(take, r):
    """The chin sensor stopping at each point of the boot sweep: a 2-tick motor blip and a small bright stop tick."""
    m = STICK[take - 1]
    dur = 0.24
    b = buf(dur)
    t = tt(dur)
    s = 0.5 * speed_curve(t, 0.003, 0.040)
    b += 0.30 * motor(t, s, r, 400 * m, 1200 * m, grit=0.5, air=0.2, whine=0.2)
    place(b, strike(r, 3000), 0.040, 0.30)
    place(b, norm(modal(2900 * m, [1, 1.5, 2.6], [1, 0.4, 0.2], [0.008, 0.005, 0.003], r)), 0.040, 0.60)
    place(b, relay_click(r, 2800 * m), 0.003, 0.25)
    return b


RTICK = [1.00, 1.12, 0.90]


def relay_tick(take, r):
    """The parked vent flap ticking (very quiet): a small low knock of the flap (480 Hz) on a soft thunk, a faint tick;
    low-passed."""
    m = RTICK[take - 1]
    b = buf(0.22)
    place(b, norm(modal(480 * m, [1, 2.3], [1, 0.35], [0.012, 0.008], r)), 0.003, 0.70)
    place(b, thunk(r, 200 * m, 150 * m, 0.012, 0.008), 0.003, 0.30)
    place(b, tooth(r, 2000 * m), 0.003, 0.06)
    return lowpass(b, 2500)


BLIP = [1.00, 1.10]


def standby_blip(take, r):
    """The parked chest lamp's standby blip: a soft 520 Hz sine pip (4 ms rise, 30 ms hold, 20 ms decay) with a faint
    octave, low-passed: very small."""
    m = BLIP[take - 1]
    d = 0.25
    t = tt(d)
    f = 520 * m
    env = np.clip(t / 0.004, 0, 1) * np.where(t < 0.034, 1.0, np.exp(-(t - 0.034) / 0.020))
    x = (np.sin(2 * np.pi * f * t) + 0.2 * np.sin(4 * np.pi * f * t)) * env
    b = buf(0.30)
    place(b, x, 0.003)
    return lowpass(b, 2000)


# ---------------------------------------------------------------- hydraulics and the pogo

PCHARGE = [dict(m=1.00, air=0.42), dict(m=1.10, air=0.60)]


def pogo_charge(take, r):
    """The Pogo Protocol's charge: a hydraulic groan that rises for 1.45 s (ticks 1-30) under the four ratchet stages:
    the valve opens (a tsk), pressure builds (a low-passed whoosh whose cutoff climbs, pump air, a groan rising 60 ->
    140 Hz), the hull trembles at 10 Hz in the last third, and the pressure holds, fading over about 0.3 s into the
    charged hold."""
    q = PCHARGE[take - 1]
    m = q['m']
    full = 1.45
    dur = round(full + 0.48, 3)
    b = buf(dur)
    t = tt(dur)
    u = np.clip((t - 0.01) / full, 0, 1)
    s = np.where(t < full + 0.01, u ** 1.2, np.exp(-(t - full - 0.01) / 0.05)) * (t > 0.01)
    whoosh = sweep_bandpass(noise(dur, r), np.full(len(t), 60.0), 300 + 1100 * s) * s
    air = bandpass(noise(dur, r), 800 * m, 5000 * m) * s ** 1.5
    fg = 60 * m * (1 + 1.3 * s) * (1 + 0.03 * np.sin(2 * np.pi * 5 * t))
    groan = osc_harm(fg, 6, r, 1.5) * s ** 0.6
    trem = 1 - 0.3 * np.clip((u - 0.7) / 0.3, 0, 1) * (0.5 + 0.5 * np.cos(2 * np.pi * 10 * t))
    b += (0.50 * norm(whoosh) + q['air'] * norm(air) + 0.22 * norm(groan)) * trem
    place(b, norm(noise_burst(3000, 10000, 0.050, r, attack=0.004)), 0.003, 0.35)
    return b


PRATCH = [dict(m=1.00, teeth=(0.035, 0.065)), dict(m=1.10, teeth=(0.042, 0.080)), dict(m=0.80, teeth=(0.030, 0.054))]


def pogo_ratchet(take, r):
    """One stage of the crouch: the pawl drops into the next tooth at 4 ms (a tooth, a small iron body at 1.3 kHz, a
    strike, a little thunk), the rack overruns by two teeth, and the hydraulics settle with a small 'tss' at 100 ms."""
    q = PRATCH[take - 1]
    m = q['m']
    b = buf(0.42)
    clack = mix(tooth(r, 2800 * m), 0.6 * norm(modal(1300 * m, [1, 2.756, 5.404], [1, 0.5, 0.3], [0.020, 0.014, 0.008], r)),
                0.35 * strike(r, 2000), 0.25 * thunk(r, 170, 110, 0.03, 0.015))
    place(b, norm(clack), 0.004, 1.0)
    for k, at in enumerate(q['teeth']):
        place(b, tooth(r, 2800 * m * (1.02 + 0.02 * k)), at, 0.35 - 0.13 * k)
    place(b, norm(noise_burst(2000, 8000, 0.020, r, attack=0.004)), 0.100, 0.12)
    return b


PLAUNCH = [dict(m=1.00), dict(m=1.12)]


def pogo_launch(take, r):
    """The spring release: a deep 'thoonk'. At 3 ms the latch lets go (a small tick), a low thump falls 130 -> 48 Hz, a
    hollow tube resonance (165 Hz with 2.05x and 3.1x, damped in 70 ms: a hollow 'oonk', never a ringing boing), and a
    low-passed blast of air and dust. No pitch wobble, no rising tone."""
    m = PLAUNCH[take - 1]['m']
    b = buf(0.95)
    h = 0.003
    place(b, sweep_sine(150 * m, 55 * m, 0.085, 0.030), h, 1.0)
    place(b, norm(modal(190 * m, [1, 2.05, 3.1], [1, 0.35, 0.15], [0.080, 0.045, 0.028], r)), h, 0.70)
    place(b, strike(r, 800), h, 0.25)
    place(b, tooth(r, 2400 * m), h, 0.06)
    place(b, norm(lowpass(noise(0.5, r), 1500) * exp_env(0.5, 0.06, 0.002)), h, 0.40)
    return lowpass(b, 3000)


def whoosh(take, r):
    """The leap: the frame through the air, half a second. Air rising and falling (a band 200-2100 Hz that opens with
    the speed), a low rumble under it; starts at -18 dB so the onset is on the key."""
    m = [1.00, 1.15][take - 1]
    dur = 0.80
    b = buf(dur)
    t = tt(dur)
    s = np.where(t < 0.5, np.sin(np.pi * t / 0.5) ** 2, 0.0)
    env = (0.12 * np.clip(t / 0.003, 0, 1) * np.clip(1 - t / 0.25, 0, 1) + 0.88 * s)
    air = sweep_bandpass(noise(dur, r), (180 + 450 * s) * m, (550 + 1350 * s) * m) * env
    rum = lowpass(noise(dur, r), 250) * env ** 1.5
    b += 0.8 * norm(air) + 0.3 * norm(rum)
    return b


def hydraulic_stand(take, r):
    """The frame standing up from its sag (boot_legs from tick 4, build from 44): the chair's hydraulic stroke over
    10 ticks, a soft end-stop and a settle knock 3 ticks after."""
    return hydraulic(take, r, 0.01, 0.01 + 10 * T, 'sin2', 0.6, 0.30, settle=0.01 + 13 * T)


# ---------------------------------------------------------------- the hoop, the clamps, the jar

BLATCH = [dict(m=1.00, dk=1.00), dict(m=1.10, dk=1.15), dict(m=0.90, dk=0.85)]


def bar_latch(take, r):
    """The brass hoop comes down on the lid and latches (keyed on the contact): the strike at 4 ms, a brass-bright
    iron body (640 / 1020 Hz), a thunk, the two front hooks catching the rim (teeth at 20 and 32 ms), the bounce
    falling back a tick later (50 ms), and a last tiny tick as it sits down (150 ms)."""
    q = BLATCH[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(0.62)
    h = 0.004
    place(b, strike(r, 1500), h, 0.55)
    place(b, norm(iron_body(r, 640 * m, 1020 * m, 0.7 * dk, band_scale=m)), h, 0.60)
    place(b, thunk(r, 160, 100, 0.040, 0.020), h, 0.30)
    place(b, tooth(r, 2600 * m), h + 0.016, 0.22)
    place(b, tooth(r, 2750 * m), h + 0.028, 0.18)
    place(b, norm(iron_body(r, 680 * m, 1080 * m, 0.4 * dk, band_scale=m)), 0.050, 0.20)
    place(b, tooth(r, 2200 * m), 0.150, 0.08)
    return b


BUNL = [dict(m=1.00, dk=1.00), dict(m=1.10, dk=1.25), dict(m=0.90, dk=0.80)]


def bar_unlatch(take, r):
    """The hoop pressed down to unlatch it: a short hiss as the catch vents (at once), then the hooks let go at 50 ms
    (1 tick: a lighter strike, the brass body, a small thunk, a hook tick)."""
    q = BUNL[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(0.50)
    place(b, norm(noise_burst(2000 * m, 8000 * m, 0.040 * dk, r, attack=0.005)), 0.003, 0.45)
    hit = T
    place(b, strike(r, 1800 * m), hit, 0.42)
    place(b, norm(iron_body(r, 640 * m, 1020 * m, 0.6 * dk, band_scale=m)), hit, 0.58)
    place(b, thunk(r, 160, 110, 0.03, 0.015), hit, 0.14)
    place(b, tooth(r, 2500 * m), hit + 0.020, 0.12)
    return b


CLAMP = [dict(m=1.00, dk=1.00, gap=0.014), dict(m=1.10, dk=1.25, gap=0.020), dict(m=0.92, dk=1.00, gap=0.011)]


def clamp_close(take, r):
    """Two rubber-padded clamps close on the glass, left then right a few ms apart: each a rubber thud (220 -> 150 Hz),
    the short steel arm (950 Hz), a rubber noise band and a faint glass tick under the pad; the solenoid locks with a
    tooth 55 ms on. Nothing sharp: the glass is held, not struck."""
    q = CLAMP[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(0.45)
    for k, at in enumerate((0.004, 0.004 + q['gap'])):
        g = 1.0 - 0.15 * k
        place(b, thunk(r, 220 * m, 150 * m, 0.030, 0.012), at, 0.30 * g)
        place(b, norm(modal(950 * m * (1 + 0.04 * k), [1, 2.756, 5.404], [1, 0.5, 0.3], [0.035 * dk, 0.025 * dk, 0.014 * dk], r)), at, 0.40 * g)
        place(b, norm(noise_burst(900 * m, 4500 * m, 0.014, r)), at, 0.38 * g)
        place(b, norm(glass_modes(r, 2300 * m, decays=(0.02, 0.012, 0.008, 0.005))), at, 0.10 * g)
    place(b, tooth(r, 2400 * m), 0.055, 0.15)
    return b


JSEAT = [dict(m=1.00, dk=1.00), dict(m=1.12, dk=1.20), dict(m=0.90, dk=0.85)]


def jar_seat(take, r):
    """The glass jar setting into its brass collar on the steel seat: a thick glass base meeting metal at 4 ms (a strike,
    low glass modes at 1.25 kHz, short), the collar's own small ring (1.9 kHz), a little thunk for the jar's weight; then
    the 0.4 px bounce: a smaller knock at 62 ms and a tiny one at 98 ms. A seat, not a smash: no shatter noise."""
    q = JSEAT[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(0.50)

    def knock():
        return mix(0.35 * strike(r, 2500), 0.45 * norm(glass_modes(r, 1250 * m, decays=(0.05 * dk, 0.03 * dk, 0.018 * dk, 0.010 * dk))),
                   0.30 * norm(modal(1900 * m, [1, 2.4, 3.8], [1, 0.4, 0.2], [0.03, 0.02, 0.012], r)),
                   0.25 * thunk(r, 210, 140, 0.03, 0.015), 0.20 * norm(noise_burst(400, 2500, 0.010, r)))
    place(b, norm(knock()), 0.004, 1.0)
    place(b, norm(knock()), 0.062, 0.28)
    place(b, norm(knock()), 0.098, 0.09)
    return b


BBLOW = [dict(m=1.00), dict(m=1.10)]


def bar_blow(take, r):
    """The eject blowing the hoop open: a pneumatic bang at 3 ms (a strike, a broadband blast 400-7000 Hz, a short boom
    110 -> 50 Hz), the brass hoop clanging as it flies (880 Hz, short), the hinge clattering at 40 and 70 ms."""
    m = BBLOW[take - 1]['m']
    b = buf(0.65)
    h = 0.003
    place(b, strike(r, 1500), h, 0.60)
    place(b, norm(noise_burst(400 * m, 7000 * m, 0.025, r, attack=0.0008)), h, 0.70)
    place(b, thunk(r, 110, 50, 0.050, 0.020), h, 0.35)
    place(b, norm(modal(880 * m, [1, 2.756, 5.404], [1, 0.6, 0.35], [0.05, 0.035, 0.02], r)), h, 0.35)
    place(b, tooth(r, 2200 * m), 0.040, 0.18)
    place(b, tooth(r, 2350 * m), 0.070, 0.12)
    return b


BCLANK = [dict(m=1.00, dk=1.00), dict(m=1.12, dk=1.20)]


def bar_clank(take, r):
    """The blown hoop hitting its stop on the hinge: a strike, the brass body (680 / 1080 Hz), a thunk, and a small
    settle knock 100 ms later."""
    q = BCLANK[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(0.55)
    place(b, strike(r, 1500), 0.004, 0.45)
    place(b, norm(iron_body(r, 680 * m, 1080 * m, 0.65 * dk, band_scale=m)), 0.004, 0.55)
    place(b, thunk(r, 140, 90, 0.03, 0.015), 0.004, 0.30)
    place(b, norm(modal(720 * m, [1, 2.756], [1, 0.4], [0.02, 0.012], r)), 0.100, 0.15)
    return b


# ---------------------------------------------------------------- arm refusal, hurt, collapse

DRY = [dict(m=1.00, at=0.045), dict(m=1.10, at=0.060), dict(m=0.90, at=0.038)]


def dry_fire(take, r):
    """An arm module that cannot fire: the hammer falls on nothing (a strike, a small spring-steel body at 2.6 kHz, a
    tooth) at 3 ms; the follower settles with a lower, softer tick 40-60 ms later; and a tiny falling spring tone
    (2.0 -> 1.5 kHz, very faint) is all that is left: small, final, a little sad."""
    q = DRY[take - 1]
    m = q['m']
    b = buf(0.32)
    place(b, strike(r, 2500 * m), 0.003, 0.40)
    place(b, norm(modal(2600 * m, [1, 1.6, 2.9], [1, 0.5, 0.25], [0.012, 0.008, 0.005], r)), 0.003, 0.50)
    place(b, tooth(r, 3000 * m), 0.003, 0.25)
    place(b, norm(modal(1700 * m, [1, 1.6], [1, 0.4], [0.010, 0.006], r)), q['at'], 0.22)
    place(b, sweep_sine(2000 * m, 1500 * m, 0.020, 0.020), q['at'] + 0.025, 0.07)
    return b


HURT = [dict(m=1.00, dk=1.00, rat=(0.050, 0.130)), dict(m=1.08, dk=1.20, rat=(0.060, 0.150)),
        dict(m=0.92, dk=0.95, rat=(0.045, 0.110)), dict(m=1.16, dk=1.45, rat=(0.070, 0.170))]


def hurt(take, r):
    """A mecha takes a hit: it clangs. A strike, the enamel pod's sheet-metal modes (520 Hz x 1, 1.59, 2.14, 2.65, 3.9),
    the frame's iron body, a thunk; the brass hoop rattles twice after (2.5 then 0.8 degrees in the clip). No voice."""
    q = HURT[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(0.95)
    h = 0.003
    place(b, strike(r, 1200), h, 0.50)
    place(b, norm(modal(520 * m, [1, 1.59, 2.14, 2.65, 3.9], [1, 0.7, 0.5, 0.35, 0.2],
                        [x * dk for x in (0.07, 0.055, 0.04, 0.03, 0.02)], r, 0.01)), h, 0.45)
    place(b, norm(iron_body(r, 430 * m, 700 * m, 0.9 * dk, band_scale=m)), h, 0.55)
    place(b, thunk(r, 150, 85, 0.05, 0.02), h, 0.30)
    place(b, tooth(r, 2300 * m), q['rat'][0], 0.15)
    place(b, norm(modal(1800 * m, [1, 2.4], [1, 0.4], [0.012, 0.008], r)), q['rat'][1], 0.08)
    return b


COLLAPSE = [dict(m=1.06, bits=[(0.32, 2600, 0.12), (0.45, 1900, 0.10), (0.58, 3100, 0.07), (0.70, 2400, 0.08),
                               (0.86, 2200, 0.05), (0.98, 2350, 0.04), (1.08, 2450, 0.035), (1.16, 2500, 0.03), (1.21, 2550, 0.025)], lens=0.52),
            dict(m=1.25, bits=[(0.28, 2300, 0.11), (0.40, 2900, 0.09), (0.55, 2000, 0.08), (0.74, 2700, 0.06),
                               (0.92, 2500, 0.05), (1.06, 2550, 0.04), (1.18, 2600, 0.035), (1.27, 2650, 0.03), (1.33, 2700, 0.025)], lens=0.62)]


def collapse(take, r):
    """The frame toppling on its face: the crash at 3 ms (a strike, a deep thunk 100 -> 40 Hz, the pod's iron body and
    sheet-metal modes, ground debris), the bounce 2 ticks later (a smaller crash) and the last settle at 4 ticks; then
    the loose bits: bolts and pins dropping and rolling, a lamp lens tinkling, a last nut spinning to a stop
    (tink-tink-tk, faster and quieter)."""
    q = COLLAPSE[take - 1]
    m = q['m']
    b = buf(2.10)
    h = 0.003
    place(b, strike(r, 1000), h, 0.45)
    place(b, thunk(r, 100 * m, 40 * m, 0.12, 0.04), h, 0.55)
    place(b, norm(iron_body(r, 300 * m, 480 * m, 1.6, band_scale=0.8 * m)), h, 0.60)
    place(b, norm(modal(380 * m, [1, 1.59, 2.14, 2.65], [1, 0.7, 0.5, 0.3], [0.10, 0.08, 0.06, 0.04], r, 0.01)), h, 0.35)
    place(b, norm(lowpass(noise(0.9, r), 2500) * exp_env(0.9, 0.10, 0.002)), h, 0.40)
    place(b, mix(thunk(r, 130, 70, 0.06, 0.02), 0.6 * norm(iron_body(r, 340 * m, 540 * m, 0.7, band_scale=0.8 * m))), 2 * T, 0.30)
    place(b, mix(thunk(r, 120, 80, 0.04, 0.02), 0.4 * norm(modal(420 * m, [1, 2.756], [1, 0.4], [0.03, 0.02], r))), 4 * T, 0.12)
    for at, f, g in q['bits']:
        place(b, norm(modal(f * m, [1, 2.4, 4.1], [1, 0.4, 0.2], [0.02, 0.012, 0.008], r, 0.02)), at, g)
    place(b, norm(glass_modes(r, 3200 * m, decays=(0.03, 0.02, 0.012, 0.008))), q['lens'], 0.06)
    return soft(b)


# ---------------------------------------------------------------- vents

VBLAST = [dict(m=1.00, tau=0.26), dict(m=1.15, tau=0.22)]


def vent_blast(take, r):
    """The scald blast as both flaps blow open: the flaps bang at 3 and 13 ms (a strike and a short steel flap each);
    steam bursts (pressure up in 15 ms, holds 120 ms, decays), a hiss band 3-16 kHz with a lower 1.5-6 kHz roar under
    it while the pressure is high; a soft low whump."""
    q = VBLAST[take - 1]
    m = q['m']
    dur = 1.75
    b = buf(dur)
    t = tt(dur)
    t0 = np.maximum(t - 0.004, 0)
    pr = (t >= 0.004) * np.clip(t0 / 0.015, 0, 1) * np.where(t0 < 0.12, 1.0, np.exp(-(t0 - 0.12) / q['tau']))
    n = noise(dur, r)
    hiss = bandpass(n, 3000 * m, 16000) * pr + 0.4 * bandpass(noise(dur, r), 1500, 6000) * pr ** 2
    b += 0.85 * norm(hiss)
    for at in (0.003, 0.013):
        place(b, strike(r, 2500), at, 0.25)
        place(b, norm(modal(700 * m, [1, 1.59, 2.14], [1, 0.5, 0.3], [0.020, 0.015, 0.010], r)), at, 0.18)
    place(b, norm(lowpass(noise(0.6, r), 400) * exp_env(0.6, 0.08, 0.004)), 0.003, 0.15)
    return b


VCLANK = [dict(m=1.00, dk=1.00), dict(m=1.10, dk=1.20), dict(m=0.90, dk=0.85)]


def vent_clank(take, r):
    """A striped vent flap slamming shut: a strike, the steel flap's iron body (620 / 980 Hz), a thunk, its spring
    rattling once 30 ms later."""
    q = VCLANK[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(0.50)
    place(b, strike(r, 1800), 0.004, 0.45)
    place(b, norm(iron_body(r, 620 * m, 980 * m, 0.6 * dk, band_scale=m)), 0.004, 0.60)
    place(b, thunk(r, 150, 95, 0.035, 0.015), 0.004, 0.30)
    place(b, tooth(r, 2200 * m), 0.030, 0.12)
    return b


VWHISTLE = [dict(m=1.00, hold=0.45), dict(m=1.08, hold=0.55)]


def vent_whistle(take, r):
    """The kettle at the end of a vent: a breath of steam (1.5-7 kHz) from the start, a whistle that sounds in it,
    rises 2150 -> 2500 Hz, holds with a 6 Hz warble, then droops and fades as the pressure runs out; one small 'pft'
    after."""
    q = VWHISTLE[take - 1]
    m = q['m']
    dur = 1.25
    b = buf(dur)
    t = tt(dur)
    hold = q['hold']
    lvl = np.clip((t - 0.03) / 0.17, 0, 1) * np.where(t < hold, 1.0, np.exp(-(t - hold) / 0.10))
    f = m * (2150 + 350 * np.clip(t / 0.2, 0, 1) - 250 * np.clip((t - hold) / 0.3, 0, 1)) * (1 + 0.008 * np.sin(2 * np.pi * 6 * t))
    ph = 2 * np.pi * np.cumsum(f) / SR
    wh = (np.sin(ph) + 0.15 * np.sin(2 * ph)) * lvl
    br = bandpass(noise(dur, r), 1500, 7000) * np.clip(t / 0.005, 0, 1) * np.where(t < hold, 1.0, np.exp(-(t - hold) / 0.12))
    b += 0.50 * wh + 0.30 * norm(br)
    place(b, norm(noise_burst(1500, 5000, 0.025, r, attack=0.006)), hold + 0.33, 0.18)
    return b


# ---------------------------------------------------------------- eject, alarm, parachute

EPOP = [dict(m=1.00), dict(m=1.15)]


def eject_pop(take, r):
    """The jar fired clear: a cork pop (a sine falling 950 -> 260 Hz in 25 ms and a sharp strike) at 3 ms, a puff of
    air (500-4000 Hz, 50 ms), a small low whump, and the air jet hissing after it."""
    m = EPOP[take - 1]['m']
    b = buf(0.60)
    h = 0.003
    place(b, sweep_sine(950 * m, 260 * m, 0.025, 0.008), h, 0.60)
    place(b, strike(r, 2500), h, 0.35)
    place(b, norm(noise_burst(500 * m, 4000 * m, 0.050, r, attack=0.003)), h, 0.50)
    place(b, thunk(r, 130, 65, 0.05, 0.02), h, 0.30)
    place(b, norm(noise_burst(1000 * m, 6000 * m, 0.070, r, attack=0.010)), h + 0.005, 0.25)
    return b


def klaxon(take, r):
    """The eject alarm: two short horn blasts (a buzzy band-limited sawtooth through a 700-1400 Hz horn resonance,
    a 35 Hz growl), each gliding up a fourth from 330 to 440 Hz in 120 ms and holding, 0.33 s and 0.38 s long with a
    90 ms gap. Low-passed at 2.2 kHz (4th order): urgent, never shrill."""
    dur = 1.25
    b = buf(dur)
    for start, length in ((0.003, 0.33), (0.42, 0.38)):
        d = length + 0.2
        t = tt(d)
        f = 440 - 110 * np.exp(-t / 0.04)
        x = osc_harm(f, 14, r, 0.9)
        x = bandpass(x, 250, 2600) + 0.6 * bandpass(x, 700, 1400)
        x = x * (1 - 0.25 * (0.5 + 0.5 * np.cos(2 * np.pi * 35 * t)))
        env = np.clip(t / 0.015, 0, 1) * np.where(t < length, 1.0, np.exp(-(t - length) / 0.02))
        place(b, norm(x * env), start, 1.0)
    return lowpass(b, 2200, order=4)


PPOP = [dict(m=1.00), dict(m=1.20)]


def parachute_pop(take, r):
    """A small gold canopy snapping open: a cloth crack at 3 ms (a 7 ms noise burst, 400-3500 Hz), the canopy filling
    (band-passed cloth noise with an 18-26 Hz flutter, 70 ms decay), a small low thump of air."""
    m = PPOP[take - 1]['m']
    dur = 0.55
    b = buf(dur)
    t = tt(dur)
    place(b, norm(noise_burst(400 * m, 3500 * m, 0.007, r, attack=0.0005)), 0.003, 0.80)
    flutter = 0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(np.full(len(t), r.uniform(18, 26))) / SR) ** 2
    cloth = bandpass(noise(dur, r), 200 * m, 1600 * m) * flutter * np.clip(t / 0.01, 0, 1) * np.exp(-t / 0.07)
    b += 0.55 * norm(cloth)
    place(b, thunk(r, 110, 80, 0.04, 0.02), 0.003, 0.35)
    return b


PFOLD = [dict(m=1.00), dict(m=1.15)]


def parachute_fold(take, r):
    """The canopy collapsing on landing: a soft low cloth swish (150-1200 Hz) with a slowing flutter, over a small
    soft thump; low-passed."""
    m = PFOLD[take - 1]['m']
    dur = 0.65
    b = buf(dur)
    t = tt(dur)
    flutter = 0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(14 - 6 * np.clip(t / 0.3, 0, 1)) / SR) ** 2
    cloth = bandpass(noise(dur, r), 120 * m, 900 * m) * flutter * np.clip(t / 0.020, 0, 1) * np.exp(-t / 0.08)
    b += 0.70 * norm(cloth)
    place(b, thunk(r, 120, 80, 0.03, 0.015), 0.003, 0.30)
    return lowpass(b, 1000)


# ---------------------------------------------------------------- module seat, life, power

BSEAT = [dict(m=1.00, sp=1.00), dict(m=1.10, sp=1.20), dict(m=0.90, sp=1.45)]


def build_seat(take, r):
    """A module clunking home on its socket: the clunk at 4 ms (a strike, an iron body at 500 Hz, a thunk), then three
    ratchet teeth as the lock turns (44, 48 and 53 ms apart, each 2 % higher)."""
    q = BSEAT[take - 1]
    m = q['m']
    b = buf(0.50)
    place(b, strike(r, 1500), 0.004, 0.40)
    place(b, norm(iron_body(r, 500 * m, 800 * m, 0.55, band_scale=m)), 0.004, 0.55)
    place(b, thunk(r, 150, 95, 0.035, 0.015), 0.004, 0.35)
    at = 0.050
    for k in range(3):
        place(b, tooth(r, 2700 * m * 1.02 ** k), at, 0.22)
        at += 0.044 * q['sp'] * 1.1 ** k
    return b


RATTLE = [[0.028, 0.034, 0.025, 0.041, 0.030, 0.046, 0.052], [0.022, 0.040, 0.031, 0.027, 0.050, 0.038],
          [0.035, 0.026, 0.044, 0.029, 0.055, 0.040, 0.034, 0.060]]


def rattle(take, r):
    """The loose restraint rattling: brass ticks (2.2 kHz) and lower hoop knocks (900 Hz) at irregular 22-60 ms
    intervals, dying away."""
    b = buf(0.55)
    at = 0.003
    for k, dt in enumerate(RATTLE[take - 1]):
        g = 0.85 ** k * r.uniform(0.7, 1.0)
        if k % 3 == 1:
            place(b, norm(modal(900 * r.uniform(0.95, 1.05), [1, 2.756], [1, 0.4], [0.020, 0.012], r)), at, 0.6 * g)
        else:
            place(b, norm(modal(2200 * r.uniform(0.92, 1.08), [1, 2.4, 3.9], [1, 0.45, 0.2], [0.012, 0.008, 0.005], r)), at, g)
        at += dt
    return b


PDOWN = [dict(m=1.00, spin=0.70), dict(m=1.10, spin=0.85)]


def power_down(take, r):
    """Shutdown: the main relay drops out (a big click and a thunk), the motors spin down over about 0.7 s (whine
    falling with speed), the mains hum dies with them, one last small tick."""
    q = PDOWN[take - 1]
    m = q['m']
    dur = 1.30
    b = buf(dur)
    t = tt(dur)
    s = 0.9 * np.clip(1 - (t - 0.01) / q['spin'], 0, 1) ** 1.5 * (t > 0.01)
    b += 0.8 * motor(t, s, r, 200 * m, 600 * m, grit=0.30, air=0.12, whine=0.32)
    hum = sum(a * np.sin(2 * np.pi * 100 * k * t) for k, a in zip([1, 2, 3], [1, 0.5, 0.3]))
    b += 0.15 * norm(hum) * s ** 0.5
    place(b, relay_click(r, 1300 * m), 0.003, 0.50)
    place(b, thunk(r, 140, 90, 0.03, 0.015), 0.003, 0.30)
    place(b, norm(modal(2000 * m, [1, 1.5], [1, 0.4], [0.006, 0.004], r)), q['spin'] + 0.08, 0.08)
    return b


SIGH = [dict(m=1.00, tau=0.28, puff=1.15), dict(m=0.88, tau=0.24, puff=1.05)]


def sigh(take, r):
    """A pneumatic sigh, not a voice: a valve tsk, then air let out (pressure up in 40 ms, decaying) through a band that
    falls from 700-4500 Hz to 350-1800 Hz as the pressure drops, a little low breathiness; one last small puff after
    it has finished."""
    q = SIGH[take - 1]
    m = q['m']
    dur = 1.55
    b = buf(dur)
    t = tt(dur)
    t0 = np.maximum(t - 0.01, 0)
    pr = (t >= 0.01) * np.clip(t0 / 0.04, 0, 1) * np.where(t0 < 0.06, 1.0, np.exp(-(t0 - 0.06) / q['tau']))
    lo = (350 + 350 * pr) * m
    hi = (1800 + 2700 * pr) * m
    hiss = sweep_bandpass(noise(dur, r), lo, hi) * pr
    breath = bandpass(noise(dur, r), 300, 600) * pr ** 1.5
    b += 0.80 * norm(hiss) + 0.25 * norm(breath)
    place(b, norm(noise_burst(4000, 12000, 0.0015, r)), 0.003, 0.45)
    place(b, norm(noise_burst(1200, 5000, 0.030, r, attack=0.008)), q['puff'], 0.20)
    return b


def scan_chirp(take, r):
    """The sensor turret powering up for the inspection: a relay click, then a clean tone (harmonics 1-3) rising
    600 -> 1500 Hz in 0.3 s, holding with an 8 Hz scanning vibrato, released by 0.6 s, and a short 1.8 kHz bip. Plain
    sines, not the company's FM voice."""
    dur = 0.95
    b = buf(dur)
    t = tt(dur)
    u = np.clip(t / 0.3, 0, 1)
    f = (600 + 900 * u ** 1.3) * (1 + 0.03 * np.sin(2 * np.pi * 8 * t) * (t > 0.3))
    ph = 2 * np.pi * np.cumsum(f) / SR
    tone = np.sin(ph) + 0.3 * np.sin(2 * ph) + 0.1 * np.sin(3 * ph)
    env = np.clip(t / 0.010, 0, 1) * (0.5 + 0.5 * np.cos(np.pi * np.clip((t - 0.48) / 0.12, 0, 1))) ** 2
    b += 0.7 * tone * env
    place(b, relay_click(r, 1800), 0.003, 0.30)
    d = 0.20
    tb = tt(d)
    bip = np.sin(2 * np.pi * 1800 * tb) * np.clip(tb / 0.003, 0, 1) * np.where(tb < 0.04, 1.0, np.exp(-(tb - 0.04) / 0.015))
    place(b, bip, 0.62, 0.35)
    return b


def ding(take, r):
    """Build ready: the done chime on the house voice, G5 then C6 150 ms apart (the motif's rising fourth), each with a
    twin 2 Hz above."""
    b = buf(1.50)
    place(b, fm_note(783.99, 0.14, r, index=(1.6, 0.25), attack=0.002, partner=(2.0, 0.4)), 0.003, 0.85)
    place(b, fm_note(1046.5, 0.18, r, index=(1.6, 0.25), attack=0.002, partner=(2.0, 0.4)), 0.153, 1.0)
    return lowpass(b, 8000)


# ---------------------------------------------------------------- the event list

PISTON_IN = dict(event='block.piston.contract', file='tile__piston__in.ogg', vol=0.5, pitch=0.675)
PISTON_OUT = dict(event='block.piston.extend', file='tile__piston__out.ogg', vol=0.5, pitch=0.725)
FIZZ = dict(event='block.fire.extinguish', file='random__fizz.ogg', vol=0.5, pitch=2.0)
TRAPDOOR_CLOSE = dict(event='block.iron_trapdoor.close', file='block__iron_trapdoor__close1.ogg', vol=0.9, pitch=0.95)
DOOR_CLOSE = dict(event='block.iron_door.close', file='block__iron_door__close1.ogg', vol=0.9, pitch=0.95)
DOOR_OPEN = dict(event='block.iron_door.open', file='block__iron_door__open1.ogg', vol=0.9, pitch=0.95)
SPYGLASS = dict(event='item.spyglass.use', file='item__spyglass__use.ogg', vol=1.0, pitch=1.0)
BULB = dict(event='block.copper_bulb.turn_on', file='block__copper_bulb__toggle.ogg', vol=1.0, pitch=1.0)
CLICK = dict(event='block.dispenser.dispense', file='random__click.ogg', vol=1.0, pitch=1.0)
DISP_FAIL = dict(event='block.dispenser.fail', file='random__click.ogg', vol=1.0, pitch=1.2)
WARDEN_STEP = dict(event='entity.warden.step', file='mob__warden__step_1.ogg', vol=1.0, pitch=1.0)
GOLEM_STEP = dict(event='entity.iron_golem.step', file='mob__irongolem__walk2.ogg', vol=1.0, pitch=1.0)
SHELF = dict(event='block.chiseled_bookshelf.insert', file='block__chiseled_bookshelf__insert1.ogg', vol=1.0, pitch=1.0)
GOLEM_HURT = dict(event='entity.iron_golem.hurt', file='mob__irongolem__hit1.ogg', vol=1.0, pitch=1.0)
BELL = dict(event='block.note_block.bell', file='note__bell.ogg', vol=1.0, pitch=1.0)

BASE = dict(category='NEUTRAL', attenuation=16, fixed_range=False, volume=1.0)
KEY_NOTE = ' Keyframe volume 1.0 (the file carries the loudness).'


def ev(**kw):
    d = dict(BASE)
    d.update(kw)
    return d


SOUNDS = [
    # ---- the walk
    ev(id='mecha.motor', file='mecha/motor', fn=motor_loop, finish=finish_loop, takes=1, target=-28.0,
       cls='hum, motor (loop at full speed)', kind=('loop', 2.0), fade_ms=0, hit_ms=None,
       ref=dict(event='block.beacon.ambient', file='block__beacon__ambient.ogg', vol=1.0, pitch=1.0),
       pitch=[0.97, 1.03], attenuation=10, subtitle_en=None, subtitle_pt=None,
       play='LOOP: one client tickable sound instance per walking mecha, created once, looping this 2.0 s file (seamless: no fades in it). Speed s 0..1 from the synced walk speed: pitch = (0.8 + 0.4 s) x a fixed per-instance offset 0.97-1.03, volume = sqrt(s); chase both 0.2 a tick (pitch at most 0.05 a tick); start from volume 0 and stop below 0.01. No subtitle (the footsteps carry it).'),
    ev(id='mecha.step', file='mecha/step', fn=step, takes=4, target=-27.0, cls='medium step (servo class)',
       kind=('step', 0.20), fade_ms=15, hit_ms=3, ref=GOLEM_STEP, pitch=[0.94, 1.06], attenuation=24,
       subtitle_en='Mecha footsteps', subtitle_pt='Passos de mecha',
       play='Server at each walk plant (walk ticks 0 and 6, limp 0 and 12) at the foot, with the block\'s own step sound at pitch 1.2. Random take, pitch 0.94-1.06, volume 0.9-1.0. Debounce 2 ticks per foot.'),
    ev(id='mecha.step_light', file='mecha/step_light', fn=step_light, takes=4, target=-31.0, cls='light step (tick class)',
       kind=('step', 0.15), fade_ms=12, hit_ms=3, ref=SHELF, pitch=[0.94, 1.06],
       subtitle_en='Mecha footsteps', subtitle_pt='Passos de mecha',
       play='Turn clips (marching in place: turn_l / turn_r ticks 4 and 10) and other half-weight toe sets. The file is already the half weight: play at volume 1.0, not 0.5. Random take and pitch.'),
    ev(id='mecha.step_heavy', file='mecha/step_heavy', fn=step_heavy, takes=3, target=-16.5, cls='heavy impact',
       kind=('step', 0.40), fade_ms=30, hit_ms=3, ref=GOLEM_HURT, pitch=[0.95, 1.05], attenuation=32,
       subtitle_en='Mecha lands heavily', subtitle_pt='Mecha aterrissa pesado',
       play='Server on the contact tick of land_heavy (tick 0) and pogo_land (tick 0), with the dust ring. Random take and pitch.'),
    ev(id='mecha.step_drag', file='mecha/step_drag', fn=step_drag, takes=3, target=-28.0, cls='scrape (servo class)',
       kind=('step', 0.25), fade_ms=15, hit_ms=200, preroll_ticks=4,
       ref=dict(event='item.armor.equip_iron', file='item__armor__equip_iron1.ogg', vol=1.0, pitch=1.0),
       pitch=[0.95, 1.05], subtitle_en='Mecha foot drags', subtitle_pt='Pé de mecha se arrasta',
       play='Limp: start it 4 ticks before the left plant (limp tick 20 of the previous cycle) so the toe catches at 200 ms on tick 0, where mecha.step plays as usual.'),
    ev(id='mecha.tap', file='mecha/tap', fn=tap, takes=3, target=-30.0, cls='tick (soft knock)', kind='click',
       fade_ms=10, hit_ms=3,
       ref=SHELF,
       pitch=[0.94, 1.06], subtitle_en=None, subtitle_pt=None,
       play='boot_legs ticks 30 and 35 (tap:l, tap:r) and idle_tic tick 12 (tap:both: play once). At the foot. No subtitle (small life).'),
    ev(id='mecha.land_light', file='mecha/land_light', fn=land_light, takes=3, target=-24.5, cls='landing (between step and slam)',
       kind=('step', 0.25), fade_ms=20, hit_ms=3, ref=WARDEN_STEP, pitch=[0.95, 1.05], attenuation=24,
       subtitle_en='Mecha lands', subtitle_pt='Mecha aterrissa',
       play='Server on land tick 0 (falls under 4 blocks).'),
    # ---- servos and relays
    ev(id='mecha.servo', file='mecha/servo', fn=lambda take, r: mservo(take, r, 5), takes=4, target=-27.0, cls='servo',
       kind=('servo', 5 * T), fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[1.0, 1.0],
       subtitle_en='Servo whirs', subtitle_pt='Servo zune',
       clip_key='every mecha.servo key: volume 1.0, keep the key\'s pitch (0.9-1.3)',
       play='Keys: board 3, 14; boot 13, 18; spin 1; spin_end 2; recoil 1; strike 1; guard 1; guard_end 1; dry_fire 1. A 5-tick move with its stop clunk at 260 ms; at the key\'s pitch p the move lasts 5/p ticks (0.9: 5.6, 1.3: 3.8). Random take; the key fixes the pitch.' + KEY_NOTE),
    ev(id='mecha.servo_tick', file='mecha/servo_tick', fn=servo_tick, takes=3, target=-31.0, cls='tick (sensor stop)',
       kind='click', fade_ms=8, hit_ms=3, ref=SPYGLASS, pitch=[0.94, 1.06], attenuation=8, subtitle_en=None, subtitle_pt=None,
       play='boot 11, 17, 23 (the sensor\'s stops) and idle_tic 3. The stop tick lands at 40 ms. No subtitle (small life).' + KEY_NOTE),
    ev(id='mecha.servo_chord', file='mecha/servo_chord', fn=servo_chord, takes=1, target=-27.0, cls='servo (self-test)',
       kind=('servo', 0.36), fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[1.0, 1.0],
       subtitle_en='Mecha self-tests', subtitle_pt='Mecha faz autoteste',
       play='boot tick 0, fixed pitch 1.0 (the whines are a C major triad). Shared stop clunk at 360 ms (tick 7).' + KEY_NOTE),
    ev(id='mecha.servo_settle', file='mecha/servo_settle', fn=servo_settle, takes=3, target=-28.5, cls='servo (settle)',
       kind='latch', fade_ms=12, hit_ms=4, ref=PISTON_OUT, pitch=[0.95, 1.05], subtitle_en=None, subtitle_pt=None,
       play='pogo_land tick 12. No subtitle (part of the landing).' + KEY_NOTE),
    ev(id='mecha.servo_sag', file='mecha/servo_sag', fn=servo_sag, takes=3, target=-27.0, cls='servo (sag)',
       kind=('servo', 0.20), fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[1.0, 1.0], subtitle_en=None, subtitle_pt=None,
       clip_key='dry_fire_l/r key 10: servo_sag|1.0|0.8 (was 0.3|0.8); for the vent slump add servo_sag|1.0|0.8 at vent tick 8',
       play='dry_fire 10 at pitch 0.8 (the sag runs about 5 ticks and bottoms out 225-290 ms in, by take); vent slump at tick 8, pitch 0.8. No subtitle (dry_fire carries it).' + KEY_NOTE),
    ev(id='mecha.servo_catch', file='mecha/servo_catch', fn=servo_catch, takes=3, target=-27.0, cls='servo (gear skip)',
       kind=('servo', 0.30), fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[1.0, 1.0],
       subtitle_en='Servo stutters', subtitle_pt='Servo engasga',
       clip_key='limp key 16: servo_catch|1.0|0.8 (was 0.4|0.8)',
       play='limp tick 16 at pitch 0.8: the gear skips on the key, the motor stalls two ticks and picks up.' + KEY_NOTE),
    ev(id='mecha.servo_fail', file='mecha/servo_fail', fn=servo_fail, takes=2, target=-26.0, cls='servo (dying)',
       kind=('servo', 0.76), fade_ms=30, hit_ms=3, ref=PISTON_IN, pitch=[0.97, 1.03],
       subtitle_en='Servo dies', subtitle_pt='Servo morre',
       play='destroyed tick 12: the whine falls and stutters; the seize clunk lands at 720-800 ms (ticks 26-28, as the frame topples).' + KEY_NOTE),
    ev(id='mecha.relay_click', file='mecha/relay_click', fn=relay, takes=4, target=-31.0, cls='relay click', kind='click',
       fade_ms=8, hit_ms=3, ref=SPYGLASS, pitch=[1.0, 1.0], attenuation=8, subtitle_en='Relay clicks', subtitle_pt='Relé estala',
       play='boot 2, 5, 8 at the key pitches 1.0, 1.1, 1.2; and from code one per fitted module, 3 ticks apart from boot tick 26, pitch 1.0 + 0.05 per module. Random take.' + KEY_NOTE),
    ev(id='mecha.relay_tick', file='mecha/relay_tick', fn=relay_tick, takes=3, target=-37.0, cls='idle machine life',
       kind='click', fade_ms=8, hit_ms=3, ref=BULB, pitch=[0.94, 1.06], attenuation=6, subtitle_en=None, subtitle_pt=None,
       play='parked tick 52 (the vent flap ticks). Very quiet by construction: play at volume 1.0. No subtitle.' + KEY_NOTE),
    ev(id='mecha.standby_blip', file='mecha/standby_blip', fn=standby_blip, takes=2, target=-37.0, cls='idle machine life',
       kind='click', fade_ms=10, hit_ms=3, ref=BULB, pitch=[1.0, 1.0], attenuation=6, subtitle_en=None, subtitle_pt=None,
       clip_key='parked key 0: standby_blip|1.0|1.0 (was 0.15|1.4: the file is already quiet and pitched)',
       play='parked tick 0, once per 80-tick loop. No subtitle.' + KEY_NOTE),
    # ---- hydraulics and the pogo
    ev(id='mecha.hydraulic_stand', file='mecha/hydraulic_stand', fn=hydraulic_stand, takes=3, target=-27.0,
       cls='servo (hydraulic stroke)', kind=('servo', 13 * T), fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[0.98, 1.02],
       subtitle_en='Mecha stands up', subtitle_pt='Mecha se levanta',
       play='boot_legs tick 4 (the stroke 4-14, the stop at 510 ms on 14, the settle knock at 660 ms on 17) and build tick 44.' + KEY_NOTE),
    ev(id='mecha.pogo_charge', file='mecha/pogo_charge', fn=pogo_charge, takes=2, target=-27.0, cls='servo (hydraulic charge)',
       kind=('servo', 1.55), fade_ms=30, hit_ms=3, ref=PISTON_IN, pitch=[0.98, 1.02],
       subtitle_en='Pogo spring charges', subtitle_pt='Mola do pogo carrega',
       play='pogo_crouch tick 1. Rises for 1.45 s (to tick 30), then fades over about 0.3 s. Stop it if the crouch is released early (fade 2 ticks).' + KEY_NOTE),
    ev(id='mecha.pogo_ratchet', file='mecha/pogo_ratchet', fn=pogo_ratchet, takes=3, target=-27.0, cls='ratchet (one stage)',
       kind='latch', fade_ms=12, hit_ms=4, ref=CLICK, pitch=[1.0, 1.0],
       subtitle_en='Pogo ratchets', subtitle_pt='Catraca do pogo estala',
       play='pogo_crouch 7, 14, 21, 28 at the key pitches 1.0, 1.1, 1.2, 1.3; a different take each.' + KEY_NOTE),
    ev(id='mecha.pogo_launch', file='mecha/pogo_launch', fn=pogo_launch, takes=2, target=-18.0, cls='signature moment (the leap)',
       kind=('step', 0.40), fade_ms=30, hit_ms=3,
       ref=dict(event='block.beacon.deactivate', file='block__beacon__deactivate.ogg', vol=1.0, pitch=1.0),
       pitch=[0.95, 1.05], attenuation=32, subtitle_en='Mecha springs', subtitle_pt='Mecha salta na mola',
       play='Server on pogo_leap tick 1 with the impulse and dust ring. Also the plain jump (jump tick 2) at volume 0.7, pitch 1.1.'),
    ev(id='mecha.whoosh', file='mecha/whoosh', fn=whoosh, takes=2, target=-21.0, cls='slam class (the leap\'s air)',
       kind=('whoosh', 0.50), fade_ms=30, hit_ms=3,
       ref=dict(event='item.trident.throw', file='item__trident__throw1.ogg', vol=1.0, pitch=1.0),
       pitch=[0.95, 1.05], attenuation=24, subtitle_en=None, subtitle_pt=None,
       play='pogo_leap tick 5. No subtitle (part of the leap).' + KEY_NOTE),
    # ---- the hoop, the clamps, the jar
    ev(id='mecha.bar_unlatch', file='mecha/bar_unlatch', fn=bar_unlatch, takes=3, target=-24.0, cls='clamp release',
       kind='latch', fade_ms=12, hit_ms=50, ref=DOOR_OPEN, pitch=[0.95, 1.05],
       subtitle_en='Restraint unlatches', subtitle_pt='Trava do pote se solta',
       play='board tick 1: the catch vents at once, the hooks let go at 50 ms (tick 2).' + KEY_NOTE),
    ev(id='mecha.bar_latch', file='mecha/bar_latch', fn=bar_latch, takes=3, target=-22.0, cls='clamp (latch)',
       kind='latch', fade_ms=12, hit_ms=4, ref=TRAPDOOR_CLOSE, pitch=[0.95, 1.05],
       subtitle_en='Restraint latches', subtitle_pt='Trava do pote fecha',
       play='board tick 18 and build tick 193, on the contact: the bounce at 50 ms (19).' + KEY_NOTE),
    ev(id='mecha.clamp_close', file='mecha/clamp_close', fn=clamp_close, takes=3, target=-25.0, cls='clamp (soft)',
       kind='latch', fade_ms=12, hit_ms=4,
       ref=dict(event='block.copper_trapdoor.close', file='block__copper_trapdoor__toggle1.ogg', vol=1.0, pitch=0.95),
       pitch=[0.95, 1.05], subtitle_en='Jar clamps grip', subtitle_pt='Grampos prendem o pote',
       play='board tick 20 (and build 193-195 after the latch, at tick 194): left pad at 4 ms, right 11-20 ms later, the lock at 55 ms.' + KEY_NOTE),
    ev(id='mecha.jar_seat', file='mecha/jar_seat', fn=jar_seat, takes=3, target=-26.5, cls='glass clink',
       kind='latch', fade_ms=12, hit_ms=4,
       ref=dict(event='block.lantern.place', file='block__lantern__place1.ogg', vol=1.0, pitch=0.88),
       pitch=[0.95, 1.05], subtitle_en='Jar seats', subtitle_pt='Pote se encaixa',
       play='board tick 11 (the jar seats), bounce knocks at 62 and 98 ms (12-13).' + KEY_NOTE),
    ev(id='mecha.bar_blow', file='mecha/bar_blow', fn=bar_blow, takes=2, target=-20.5, cls='slam (bang)',
       kind='latch', fade_ms=15, hit_ms=3,
       ref=dict(event='entity.firework_rocket.blast', file='fireworks__blast1.ogg', vol=1.0, pitch=1.0),
       pitch=[0.95, 1.05], attenuation=24, subtitle_en='Restraint blows open', subtitle_pt='Trava abre com estouro',
       play='eject tick 2.' + KEY_NOTE),
    ev(id='mecha.bar_clank', file='mecha/bar_clank', fn=bar_clank, takes=2, target=-23.0, cls='slam (stop)',
       kind='latch', fade_ms=12, hit_ms=4, ref=TRAPDOOR_CLOSE, pitch=[0.95, 1.05], subtitle_en=None, subtitle_pt=None,
       play='eject tick 8 (the hoop hits its stop). No subtitle (part of the eject).' + KEY_NOTE),
    # ---- refusal, hurt, collapse
    ev(id='mecha.dry_fire', file='mecha/dry_fire', fn=dry_fire, takes=3, target=-28.5, cls='tick (dry click)',
       kind='click', fade_ms=10, hit_ms=3, ref=DISP_FAIL, pitch=[0.95, 1.05],
       subtitle_en='Arm dry-fires', subtitle_pt='Braço falha o disparo',
       play='dry_fire tick 7 (arm:dry:<side>), at the wrist, with the refusal line. Played for every refusal of an arm input, so it must never be skipped.'),
    ev(id='mecha.hurt', file='mecha/hurt', fn=hurt, takes=4, target=-21.0, cls='slam (clang)',
       kind=('step', 0.30), fade_ms=20, hit_ms=3, ref=DOOR_CLOSE, pitch=[0.94, 1.06], attenuation=24,
       subtitle_en='Mecha takes a hit', subtitle_pt='Mecha leva um golpe',
       play='The mecha\'s hurt sound (getHurtSound), on the hit, with the hurt clip. Random take, pitch 0.94-1.06.'),
    ev(id='mecha.collapse', file='mecha/collapse', fn=collapse, takes=2, target=-15.0, cls='heavy impact (the crash)',
       kind=('step', 1.20), fade_ms=40, hit_ms=3, ref=GOLEM_HURT, pitch=[0.97, 1.03], attenuation=32,
       subtitle_en='Mecha collapses', subtitle_pt='Mecha desaba',
       play='Server on destroyed tick 27 (destroyed:thud, the contact): the crash at 3 ms, the bounce at 100 ms (29), loose bits for about 1.2 s after.'),
    # ---- vents
    ev(id='mecha.vent_blast', file='mecha/vent_blast', fn=vent_blast, takes=2, target=-23.0, cls='hiss (scald blast)',
       kind='hiss', fade_ms=60, hit_ms=3, ref=FIZZ, pitch=[0.95, 1.05], attenuation=24,
       subtitle_en='Vents blast steam', subtitle_pt='Respiros soltam vapor',
       play='Server on vent tick 3 (vent:blast), from between the two vents; replaces LAVA_EXTINGUISH.'),
    ev(id='mecha.vent_clank', file='mecha/vent_clank', fn=vent_clank, takes=3, target=-23.0, cls='slam (flap)',
       kind='latch', fade_ms=12, hit_ms=4, ref=TRAPDOOR_CLOSE, pitch=[0.95, 1.05],
       subtitle_en='Vent flap clanks', subtitle_pt='Aba do respiro bate',
       play='vent 82 and 86; land_heavy 12; pogo_leap 2; pogo_land 11.' + KEY_NOTE),
    ev(id='mecha.vent_whistle', file='mecha/vent_whistle', fn=vent_whistle, takes=2, target=-25.0, cls='hiss (kettle)',
       kind='stinger', fade_ms=40, hit_ms=3, ref=DOOR_OPEN, pitch=[0.97, 1.03],
       subtitle_en='Vent whistles', subtitle_pt='Respiro apita',
       play='vent tick 76: the whistle sounds by 200 ms and droops from 450-550 ms (ticks 85-87, as the flaps clank); the last puff lands at 780-880 ms.' + KEY_NOTE),
    # ---- eject
    ev(id='mecha.klaxon', file='mecha/klaxon', fn=klaxon, takes=1, target=-22.0, cls='alarm', kind='stinger', fade_ms=30,
       hit_ms=3, ref=DOOR_CLOSE, pitch=[1.0, 1.0], attenuation=48, fixed_range=True,
       subtitle_en='Eject klaxon sounds', subtitle_pt='Buzina de ejeção soa',
       play='Server on eject tick 0, once. Fixed pitch. Register fixed-range 48 (createFixedRangeEvent).'),
    ev(id='mecha.eject_pop', file='mecha/eject_pop', fn=eject_pop, takes=2, target=-22.0, cls='slam (pop)',
       kind='latch', fade_ms=15, hit_ms=3, ref=DOOR_CLOSE, pitch=[0.95, 1.05], attenuation=24,
       subtitle_en='Jar ejects', subtitle_pt='Pote é ejetado',
       play='Server on eject tick 4 (eject:jar) at fx_jar_exit, with the poof ring and the launch.'),
    ev(id='mecha.parachute_pop', file='mecha/parachute_pop', fn=parachute_pop, takes=2, target=-26.0, cls='cloth snap',
       kind='latch', fade_ms=15, hit_ms=3,
       ref=dict(event='item.armor.equip_elytra', file='item__armor__equip_leather1.ogg', vol=1.0, pitch=1.0),
       pitch=[0.95, 1.05], subtitle_en='Parachute opens', subtitle_pt='Paraquedas abre',
       play='Code, at the jar, on the tick the canopy is first drawn open.'),
    ev(id='mecha.parachute_fold', file='mecha/parachute_fold', fn=parachute_fold, takes=2, target=-29.0, cls='cloth (soft)',
       kind='latch', fade_ms=15, hit_ms=3,
       ref=dict(event='block.wool.break', file='dig__cloth1.ogg', vol=1.0, pitch=0.8),
       pitch=[0.95, 1.05], subtitle_en='Parachute folds', subtitle_pt='Paraquedas recolhe',
       play='Code, at the jar, on the landing tick when the canopy collapses.'),
    ev(id='mecha.sigh', file='mecha/sigh', fn=sigh, takes=2, target=-26.0, cls='hiss (pneumatic sigh)', kind='hiss',
       fade_ms=40, hit_ms=3, ref=PISTON_IN, pitch=[0.95, 1.05], subtitle_en='Mecha sighs', subtitle_pt='Mecha suspira',
       play='shutdown 14, eject 26, build 196. The last little puff lands at 1.05-1.15 s.' + KEY_NOTE),
    # ---- life cycle and build
    ev(id='mecha.power_down', file='mecha/power_down', fn=power_down, takes=2, target=-27.0, cls='servo (spin-down)',
       kind=('servo', 0.78), fade_ms=30, hit_ms=3, ref=PISTON_IN, pitch=[0.97, 1.03],
       subtitle_en='Mecha powers down', subtitle_pt='Mecha desliga',
       play='shutdown tick 0: the relay drops at once, the spin-down runs to tick 14 (take 1) or 17 (take 2), with the sag.' + KEY_NOTE),
    ev(id='mecha.rattle', file='mecha/rattle', fn=rattle, takes=3, target=-27.0, cls='rattle (servo class)', kind='latch',
       fade_ms=12, hit_ms=3, ref=dict(event='item.armor.equip_chain', file='item__armor__equip_chain1.ogg', vol=1.0, pitch=1.0),
       pitch=[0.94, 1.06], subtitle_en=None, subtitle_pt=None,
       play='boot_legs 18, vent 95. No subtitle (small life).' + KEY_NOTE),
    ev(id='mecha.build_seat', file='mecha/build_seat', fn=build_seat, takes=3, target=-24.0, cls='clamp (module seat)',
       kind='latch', fade_ms=12, hit_ms=4, ref=TRAPDOOR_CLOSE, pitch=[0.95, 1.05],
       subtitle_en='Module clunks home', subtitle_pt='Módulo encaixa',
       play='build:seat:<slot> on the seated tick (65, 76, 87, 98, 109, 120, 131, 142) at the socket, with the sparks; skip empty slots. The clunk is on the tick, the three teeth follow in 0.14-0.17 s.'),
    ev(id='mecha.scan_chirp', file='mecha/scan_chirp', fn=scan_chirp, takes=1, target=-26.0, cls='servo (scanner)',
       kind='stinger', fade_ms=30, hit_ms=3, ref=BELL, pitch=[1.0, 1.0], subtitle_en='Sensor scans',
       subtitle_pt='Sensor faz varredura', play='build tick 174 (the inspection). Fixed pitch.' + KEY_NOTE),
    ev(id='mecha.ding', file='mecha/ding', fn=ding, takes=1, target=-27.0, cls='done chime', kind='stinger',
       fade_ms=50, hit_ms=3, ref=BELL, pitch=[1.0, 1.0], subtitle_en='Mecha dings: ready', subtitle_pt='Mecha toca: pronto',
       play='build tick 190 (ready). Fixed pitch: G5 then C6 at 153 ms.' + KEY_NOTE),
]

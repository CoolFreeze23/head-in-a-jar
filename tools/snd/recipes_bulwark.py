"""The Bulwark: Noggin & Sons' company loader, a 3.1-block, 1.2-tonne-feeling biped that lifts with its legs.

The Hopper's parts at the Bulwark's size: everything lower, slower and heavier. Its legs are hydraulic, not sprung,
so each plant is a weighted boot (a deep thunk with an iron body and a short joint creak) rather than a steel foot;
its servos are big and slow, with a heavy stop clunk; the jar is held by two brass clamp posts, not a hoop; the fists
close a hazard-yellow finger block. A deeper drivetrain hum loops under the walk with a slow mechanical beat.

Event ids are `mecha.bulwark.<name>`; the code plays one when it exists and the Hopper's `mecha.<name>` otherwise, so
each name here mirrors a Hopper event or a moment in the Bulwark's clips (bulwark-art.md section 5). Keyframe sounds
hit at 0-5 ms; server contact sounds hit at 3 ms, except the dragged boot's scrape (starts 4 ticks before the plant).
Takes differ by construction (per-take tables). The motor is a 3.0 s loop built like the Hopper's: every periodic
layer completes whole cycles (all its frequencies are multiples of 1/3 Hz), every noise layer is circular, and it is
finished by `finish_loop` (no fades).

Shared helpers come from the kit (`nsynth`) and the Hopper's recipes (`recipes_mecha`: the loop's noise and finishing
chain, the gentle saturation for the crashes, and the reference definitions).
"""
import numpy as np
from nsynth import *
from recipes_mecha import circ_noise, finish_loop, soft
from recipes_mecha import (PISTON_IN, PISTON_OUT, FIZZ, TRAPDOOR_CLOSE, DOOR_CLOSE, DOOR_OPEN, DISP_FAIL, SHELF,
                           GOLEM_STEP)

T = TICK


# ---------------------------------------------------------------- shared parts

def creak(r, dur, f0, rate=(0.016, 0.032), decay=0.005, rise=0.15):
    """A joint taking load: stick-slip pings at about f0 (jittered, drifting up by `rise` across the creak) at 16-32 ms
    spacing under a sin^0.7 swell. dur: the creak's length; the buffer holds the last ping's ring."""
    out = buf(dur + 0.06)
    at = 0.0
    while at < dur:
        u = at / dur
        g = np.sin(np.pi * u) ** 0.7 * r.uniform(0.5, 1.0)
        f = f0 * r.uniform(0.95, 1.05) * (1 + rise * u)
        place(out, norm(modal(f, [1, 2.3, 3.7], [1, 0.4, 0.2], [decay, decay * 0.7, decay * 0.5], r)), at, g)
        at += r.uniform(*rate)
    return out


def clunk(r, f0, m=1.0, cd=1.0, thump=(120.0, 75.0), band=(1000, 6000), band_gain=0.30):
    """A big servo's stop: iron modes at f0 (short, 36 ms, so it clunks rather than rings), a noise band and a deep
    thunk (heavier than the Hopper's)."""
    return mix(0.40 * norm(modal(f0 * m, [1, 2.756, 5.404], [1, 0.6, 0.35], [0.036 * cd, 0.024 * cd, 0.015 * cd], r)),
               band_gain * norm(noise_burst(band[0] * m, band[1] * m, 0.025 * cd, r)),
               0.45 * thunk(r, thump[0], thump[1], 0.045 * cd, 0.02))


def boot(r, m, thump=(95.0, 42.0), tau=0.08, body=0.30, body_f=(260.0, 480.0), strike_hp=900.0, dk=1.0):
    """The weighted boot: rubber sole and brass toe cap meeting the ground. A deep thunk, a little boot-rubber knock
    above it, the leg's iron body (kept low in the mix: weight first), the strike, and ground debris."""
    b = buf(1.0)
    h = 0.003
    place(b, strike(r, strike_hp * m), h, 0.20)
    place(b, thunk(r, thump[0] * m, thump[1] * m, tau, 0.03), h, 0.75)
    place(b, sweep_sine(170 * m, 100 * m, 0.035, 0.012), h, 0.25)
    place(b, norm(iron_body(r, body_f[0] * m, body_f[1] * m, 0.9 * dk, band_scale=0.8 * m)), h, body)
    place(b, norm(lowpass(noise(0.4, r), 1500) * exp_env(0.4, 0.045, 0.002)), h, 0.25)
    return b


# ---------------------------------------------------------------- the motor loop

BMOTOR = dict(f0=62.0, partner=62.0 + 1 / 3, mesh=11, rotor=15.0, pump=2.0, knock=2 / 3, swell=1 / 3)


def motor_loop(take, r):
    """The loader's drivetrain at full speed, 3.0 s, a whole number of cycles of every layer: a 62 Hz hum (harmonics
    1-12, 1/k^1.1) and a partner 1/3 Hz above that beats once per loop; the gear mesh (11th harmonic, 682 Hz) with a
    2 Hz wobble and a 4/3 Hz swell; brush grit (2-6 kHz circular noise) gated by the rotor at 15 Hz; the leg pump's
    chuff twice a second (low circular noise); the slow mechanical beat, a gear knock every 1.5 s (a narrow pulse on
    150-900 Hz circular noise); air; a 1/3 Hz swell. Every frequency is a multiple of 1/3 Hz."""
    p = BMOTOR
    L = 3.0
    n = int(L * SR)
    t = np.arange(n) / SR
    hum = sum(np.sin(2 * np.pi * p['f0'] * k * t + r.uniform(0, 6.28)) / k ** 1.1 for k in range(1, 13))
    partner = sum(np.sin(2 * np.pi * p['partner'] * k * t + r.uniform(0, 6.28)) / k ** 1.5 for k in range(1, 6))
    fm = p['f0'] * p['mesh']
    mesh = np.sin(2 * np.pi * fm * t + 0.5 * np.sin(2 * np.pi * 2.0 * t)) * (0.7 + 0.3 * np.sin(2 * np.pi * (4 / 3) * t))
    gate = (0.5 + 0.5 * np.cos(2 * np.pi * p['rotor'] * t)) ** 4
    grit = circ_noise(n, r, 2000, 6000) * gate
    chuff = circ_noise(n, r, 50, 350) * (0.5 + 0.5 * np.cos(2 * np.pi * p['pump'] * t)) ** 4
    knock = circ_noise(n, r, 150, 900) * (0.5 + 0.5 * np.cos(2 * np.pi * p['knock'] * t)) ** 24
    air = circ_noise(n, r, 250, 2500)
    swell = 1 + 0.07 * np.sin(2 * np.pi * p['swell'] * t)
    x = (1.0 * norm(hum) + 0.35 * norm(partner) + 0.16 * norm(mesh) + 0.14 * norm(grit) + 0.30 * norm(chuff)
         + 0.22 * norm(knock) + 0.10 * norm(air)) * swell
    return x


# ---------------------------------------------------------------- footfalls

BSTEP_HEAVY = [dict(m=1.00, dk=1.00, cr=0.06), dict(m=1.08, dk=1.20, cr=0.075), dict(m=0.90, dk=0.85, cr=0.05)]


def step_heavy(take, r):
    """The walk's footfall (every plant, 0.45 s apart): the weighted boot at 3 ms (a thunk 95 -> 42 Hz, boot rubber,
    the leg's iron body at 260 / 480 Hz kept under the weight, the strike, debris), the hydraulic leg taking the load
    (a soft 'tsh' 20 ms on) and the knee joint's short creak 50-150 ms after."""
    q = BSTEP_HEAVY[take - 1]
    m, dk = q['m'], q['dk']
    b = boot(r, m, dk=dk)
    place(b, norm(noise_burst(1200, 4500, 0.035, r, attack=0.010)), 0.023, 0.05)
    place(b, creak(r, 0.10, 720 * m), q['cr'], 0.05)
    return b


BSTEP = [dict(m=1.00, dk=1.00), dict(m=1.12, dk=1.25), dict(m=0.88, dk=0.85)]


def bstep(take, r):
    """The lighter shuffle (turning in place): a smaller thunk (120 -> 65 Hz), the boot shuffling on the ground (sole
    grit swelling in over 10 ms), a short iron body at 340 / 600 Hz; no creak."""
    q = BSTEP[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(0.70)
    h = 0.003
    place(b, strike(r, 1100 * m), h, 0.15)
    place(b, thunk(r, 120 * m, 65 * m, 0.050, 0.020), h, 0.70)
    place(b, norm(iron_body(r, 340 * m, 600 * m, 0.6 * dk, band_scale=0.8 * m)), h, 0.20)
    place(b, norm(noise_burst(300 * m, 2000 * m, 0.020, r, attack=0.010)), h, 0.22)
    return b


BDRAG = [dict(m=1.00, rough=38.0), dict(m=1.25, rough=52.0), dict(m=0.88, rough=30.0)]


def step_drag(take, r):
    """The limp's dragged left boot: the rubber sole and toe cap scraping along the ground for 4 ticks (band-passed
    noise 300-2000 Hz roughened at 30-52 Hz, growing), ending at 200 ms in the toe catching (a strike, a toe-cap knock
    at 700 Hz and a thunk) on the plant tick. Starts 4 ticks before the plant."""
    q = BDRAG[take - 1]
    m = q['m']
    dur = 0.70
    b = buf(dur)
    t = tt(dur)
    hit = 4 * T
    env = np.where(t < hit, 0.35 + 0.65 * (t / hit) ** 1.5, np.exp(-(t - hit) / 0.006)) * np.clip(t / 0.003, 0, 1)
    rough = 0.4 + 0.6 * np.abs(lowpass(noise(dur, r), q['rough'])) / 0.3
    sc = bandpass(noise(dur, r), 300 * m, 2000 * m) * env * np.clip(rough, 0, 2)
    b += 0.30 * norm(sc)
    place(b, strike(r, 1200 * m), hit, 0.40)
    place(b, norm(modal(700 * m, [1, 2.3, 3.7], [1, 0.4, 0.2], [0.022, 0.014, 0.009], r)), hit, 0.50)
    place(b, thunk(r, 120, 70, 0.035, 0.015), hit, 0.25)
    return b


BSTOMP = [dict(m=1.00, settle=0.100), dict(m=1.10, settle=0.125)]


def stomp(take, r):
    """The boot's stamp at the end of boot_legs, satisfied: the weighted boot set down hard (a thunk 100 -> 40 Hz),
    the brass toe cap's short ring (1150 Hz, small), a pleased hydraulic 'tss' 40 ms on, and the hull settling onto it
    two ticks later (a soft low knock)."""
    q = BSTOMP[take - 1]
    m = q['m']
    b = boot(r, m, thump=(100.0, 40.0), tau=0.09, body=0.28)
    place(b, norm(modal(1150 * m, [1, 2.4, 3.9], [1, 0.4, 0.2], [0.030, 0.020, 0.012], r)), 0.003, 0.08)
    place(b, norm(noise_burst(1500, 5000, 0.040, r, attack=0.012)), 0.040, 0.05)
    place(b, thunk(r, 110 * m, 70 * m, 0.04, 0.015), q['settle'], 0.20)
    return b


BLAND = [dict(m=1.00, gap=0.018, rat=0.12, tau=0.075), dict(m=1.10, gap=0.026, rat=0.14, tau=0.085),
         dict(m=0.90, gap=0.012, rat=0.10, tau=0.095)]


def land_light(take, r):
    """A short drop: both boots land a few ms apart (a thunk 110 -> 50 Hz each, boot rubber, the legs' iron body), the
    hydraulics absorb ('tsh' 30 ms on) and the knees creak, the clamp posts rattle once 100-140 ms later."""
    q = BLAND[take - 1]
    m = q['m']
    b = buf(1.0)
    for k, at in enumerate((0.003, 0.003 + q['gap'])):
        g = 1.0 - 0.2 * k
        place(b, strike(r, 1000 * m), at, 0.25 * g)
        place(b, thunk(r, 110 * m, 50 * m, q['tau'], 0.025), at, 0.60 * g)
        place(b, sweep_sine(170 * m, 100 * m, 0.030, 0.012), at, 0.22 * g)
        place(b, norm(iron_body(r, (300 + 25 * k) * m, 520 * m, 0.8, band_scale=0.8 * m)), at, 0.30 * g)
    place(b, norm(lowpass(noise(0.4, r), 1800) * exp_env(0.4, 0.04, 0.002)), 0.003, 0.25)
    place(b, norm(noise_burst(1500, 6000, 0.040, r, attack=0.010)), 0.030, 0.08)
    place(b, creak(r, 0.12, 680 * m), 0.06, 0.06)
    place(b, tooth(r, 2000 * m), q['rat'], 0.08)
    place(b, tooth(r, 2150 * m), q['rat'] + 0.018, 0.05)
    return b


BLAND_HEAVY = [dict(m=1.00, dk=1.00, bits=(0.09, 0.15, 0.24, 0.31)), dict(m=1.20, dk=1.40, bits=(0.10, 0.18, 0.27, 0.36))]


def land_heavy(take, r):
    """A drop from a height, the heaviest impact the walker makes: a deep thunk (85 -> 35 Hz), the whole frame's iron
    body (260 / 480 Hz) and a plate, the strike, a burst of ground debris, the hydraulics bottoming out ('tshh' 25 ms
    on), loose parts clattering 90-360 ms after, and the frame's groan as it takes the weight (a creak 0.12-0.40 s)."""
    q = BLAND_HEAVY[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(1.60)
    h = 0.003
    place(b, strike(r, 900 * m), h, 0.25)
    place(b, thunk(r, 85 * m, 35 * m, 0.12, 0.035), h, 0.60)
    place(b, norm(iron_body(r, 260 * m, 480 * m, 1.3 * dk, band_scale=0.8 * m)), h, 0.70)
    place(b, norm(modal(700 * m, [1, 1.59, 2.14], [1, 0.6, 0.4], [0.05 * dk, 0.035 * dk, 0.02 * dk], r)), h, 0.25)
    place(b, norm(lowpass(noise(0.9, r), 2500) * exp_env(0.9, 0.09, 0.002)), h, 0.50)
    place(b, norm(noise_burst(1200, 5000, 0.06, r, attack=0.012)), h + 0.025, 0.12)
    for k, at in enumerate(q['bits']):
        place(b, norm(modal(1800 * m * (1 + 0.08 * k), [1, 1.6, 2.7], [1, 0.4, 0.2], [0.014, 0.009, 0.006], r)), at,
              0.14 / (k + 1) ** 0.7)
    place(b, creak(r, 0.28, 520 * m, rate=(0.020, 0.040), decay=0.007), 0.12, 0.10)
    return soft(b, 4.0)


# ---------------------------------------------------------------- big slow servos

BS_TAKES = [dict(m=1.00, grit=0.42, air=0.16, cd=1.00), dict(m=1.08, grit=0.55, air=0.24, cd=1.20),
            dict(m=0.92, grit=0.34, air=0.12, cd=0.85)]
BIG = dict(base=150.0, top=380.0, clunk=400.0, click=1500.0)


def bservo(take, r, move_ticks=6, p=BIG):
    """A big slow arm or shoulder servo: a relay click at 3 ms, the motor (whine 150 + 380 s Hz, grit, air) under
    s = sin^2 over move_ticks, a gearbox rumble under it, the heavy stop clunk on the last tick (iron modes at 400 Hz,
    a thunk 120 -> 75 Hz) and a settle tick 60 ms after."""
    q = BS_TAKES[take - 1]
    m = q['m']
    start = 0.010
    stop = start + move_ticks * T
    dur = round(stop + 0.45, 3)
    b = buf(dur)
    t = tt(dur)
    s = speed_curve(t, start, stop)
    b += motor(t, s, r, p['base'] * m, p['top'] * m, grit=q['grit'], air=q['air'], whine=0.24, rotor_div=8)
    b += 0.10 * norm(lowpass(noise(dur, r), 300)) * s
    place(b, relay_click(r, p['click'] * m), 0.003, 0.30)
    place(b, clunk(r, p['clunk'], m, q['cd']), stop, 1.0)
    place(b, modal(1900 * m, [1, 1.5], [1, 0.4], [0.007, 0.005], r, 0.05), stop + 0.060, 0.12)
    return b


BSETTLE = [dict(m=1.00, at=0.055, cd=1.00), dict(m=1.18, at=0.072, cd=1.15), dict(m=0.88, at=0.045, cd=0.85)]


def servo_settle(take, r):
    """A big move coming to rest: a soft heavy clunk at 4 ms (iron modes at 420 Hz, a bright noise band, a thunk) and
    the settle tick 45-72 ms later."""
    q = BSETTLE[take - 1]
    m = q['m']
    b = buf(0.50)
    place(b, clunk(r, 420, m, q['cd'], thump=(125.0, 85.0), band=(1500, 8000), band_gain=0.60), 0.004, 1.0)
    place(b, strike(r, 2000), 0.004, 0.25)
    place(b, modal(2000 * m, [1, 1.5], [1, 0.4], [0.007, 0.005], r, 0.05), q['at'], 0.35)
    return b


BSAG = [dict(m=1.00, creak=760.0, thump=0.26), dict(m=1.10, creak=900.0, thump=0.30), dict(m=0.90, creak=660.0, thump=0.23)]


def servo_sag(take, r):
    """A big servo giving up under load: the brake lets go (a tooth), the motor spins down as the arm drops (whine
    120 + 320 s Hz falling), the joint creaks, and the heavy arm bottoms out softly at 230-300 ms."""
    q = BSAG[take - 1]
    m = q['m']
    dur = round(q['thump'] + 0.45, 3)
    b = buf(dur)
    t = tt(dur)
    s = np.interp(t, [0, 0.01, q['thump'], q['thump'] + 0.05, dur], [0.0, 0.8, 0.15, 0.0, 0.0])
    b += motor(t, s, r, 120 * m, 320 * m, grit=0.42, air=0.18, whine=0.22, rotor_div=8)
    place(b, tooth(r, 2000 * m), 0.003, 0.25)
    place(b, creak(r, q['thump'] - 0.05, q['creak'] * m, decay=0.006), 0.03, 0.16)
    place(b, mix(norm(modal(360 * m, [1, 2.756], [1, 0.4], [0.035, 0.022], r)), 0.8 * thunk(r, 120, 75, 0.045, 0.018)),
          q['thump'], 0.45)
    return b


BCATCH = [dict(m=1.00, nt=3), dict(m=1.22, nt=2), dict(m=0.88, nt=5)]


def servo_catch(take, r):
    """The limp's bad leg catching: a gear-skip 'khrk' at 3 ms (a strike, two to five teeth 10 ms apart, an iron body
    at 500 / 820 Hz), the big motor stalling for two ticks and dragging itself up to speed again."""
    q = BCATCH[take - 1]
    m = q['m']
    dur = 0.75
    b = buf(dur)
    t = tt(dur)
    s = np.interp(t, [0, 0.01, 0.07, 0.16, 0.26, 0.36, dur], [0.0, 0.6, 0.10, 0.10, 0.5, 0.0, 0.0])
    b += motor(t, s, r, 150 * m, 420 * m, grit=0.42, air=0.14, whine=0.26, rotor_div=8)
    place(b, strike(r, 1300), 0.003, 0.30)
    for k in range(q['nt']):
        place(b, tooth(r, 2000 * m * (1 - 0.06 * k)), 0.003 + 0.010 * k, 0.20)
    place(b, norm(iron_body(r, 500 * m, 820 * m, 0.6, band_scale=m)), 0.003, 0.40)
    place(b, thunk(r, 130, 80, 0.035, 0.015), 0.003, 0.20)
    return b


BFAIL = [dict(m=1.00, drops=(0.18, 0.38, 0.58, 0.74), end=0.85), dict(m=1.12, drops=(0.15, 0.33, 0.52, 0.70, 0.82), end=0.95)]


def servo_fail(take, r):
    """A big servo dying: the whine (170 + 480 s Hz) falls from full speed to nothing over 0.85-0.95 s and stutters as
    the windings short, the gears grind (irregular teeth, slowing), one heavy last clunk as it seizes (iron modes at
    340 Hz, a deep thunk), and a small electric fizz."""
    q = BFAIL[take - 1]
    m = q['m']
    dur = round(q['end'] + 0.50, 3)
    b = buf(dur)
    t = tt(dur)
    s = np.clip(1 - t / q['end'], 0, 1) ** 1.2 * np.clip(t / 0.01, 0, 1)
    for d in q['drops']:
        s = s * (1 - 0.8 * np.exp(-((t - d) / 0.014) ** 2))
    b += motor(t, s, r, 170 * m, 480 * m, grit=0.50, air=0.16, whine=0.28, rotor_div=8)
    place(b, relay_click(r, 1400 * m), 0.003, 0.30)
    at = 0.06
    while at < q['end'] - 0.08:
        place(b, tooth(r, 1800 * m * r.uniform(0.9, 1.1)), at, 0.16 * float(np.interp(at, t, s)) ** 0.5)
        at += r.uniform(0.035, 0.07) * (1 + 1.5 * at / q['end'])
    cl = mix(0.4 * norm(modal(340 * m, [1, 2.756, 5.404], [1, 0.6, 0.4], [0.05, 0.035, 0.02], r)),
             0.55 * thunk(r, 120, 70, 0.045, 0.02), 0.2 * strike(r, 1300))
    place(b, cl, q['end'], 0.60)
    place(b, norm(crackle(r, 0.12, 2000, 9000)), q['end'] + 0.02, 0.18)
    return b


# ---------------------------------------------------------------- hydraulics

BHYD = [dict(m=1.00, air=0.40, valve=0.25), dict(m=1.10, air=0.58, valve=0.20), dict(m=0.90, air=0.36, valve=0.40)]


def bhydraulic(take, r, s0, s1, groan=(50.0, 0.40), stop_gain=0.8, settle=None, curve='sin2', creak_f=560.0,
               relief=None):
    """The loader's hydraulic stroke: the valve opens at 3 ms (a 'tsk'), then one speed curve from s0 to s1 drives a
    low-passed whoosh whose cutoff rises with speed, pneumatic air, and one groan (a 50 Hz harmonic tone rising with
    speed, 3 Hz vibrato) with the cylinder creaking at mid-stroke; a heavy end-stop at s1 unless stop_gain is 0; a
    settle knock at `settle`; with `relief`, a pressure-relief 'tss' at that time instead. curve 'in' rises into s1 and
    lets the pressure go in 50 ms (a lift)."""
    q = BHYD[take - 1]
    m = q['m']
    dur = round(max(s1, settle or 0, relief or 0) + 0.45, 3)
    b = buf(dur)
    t = tt(dur)
    if curve == 'in':
        u = np.clip((t - s0) / (s1 - s0), 0, 1)
        s = np.where(t < s1, u ** 1.5, np.exp(-(t - s1) / 0.05)) * (t > s0)
    else:
        s = speed_curve(t, s0, s1)
    whoosh = sweep_bandpass(noise(dur, r), np.full(len(t), 60.0), 250 + 800 * s) * s
    air = bandpass(noise(dur, r), 700 * m, 4500 * m) * s ** 1.5
    fg = groan[0] * m * (1 + groan[1] * s) * (1 + 0.03 * np.sin(2 * np.pi * 3 * t))
    gr = osc_harm(fg, 8, r, 1.3) * s ** 0.6
    b += 0.45 * norm(whoosh) + q['air'] * norm(air) + 0.26 * norm(gr)
    place(b, norm(noise_burst(3000, 10000, 0.050, r, attack=0.008)), 0.003, q['valve'])
    mid = s0 + 0.3 * (s1 - s0)
    place(b, creak(r, 0.4 * (s1 - s0), creak_f * m, rate=(0.020, 0.038), decay=0.006), mid, 0.07)
    if stop_gain:
        stop = mix(0.45 * norm(iron_body(r, 300 * m, 500 * m, 0.9, band_scale=0.9 * m)), 0.60 * thunk(r, 115, 60, 0.06, 0.02),
                   0.30 * strike(r, 1300))
        place(b, stop, s1, stop_gain)
    if settle:
        place(b, mix(norm(modal(420 * m, [1, 2.756], [1, 0.5], [0.03, 0.018], r)), 0.7 * thunk(r, 110, 75, 0.035, 0.015)),
              settle, 0.25 * max(stop_gain, 0.6))
    if relief:
        place(b, norm(noise_burst(1500, 6000, 0.040, r, attack=0.008)), relief, 0.20)
    return b


def hydraulic_stand(take, r):
    """Rising off the parked sag with one long groan (boot_legs tick 5: the hips rise 5-17; build tick 44): a 12-tick
    stroke, the end-stop at 610 ms (tick 17), the settle knock 4 ticks after (810 ms, tick 21)."""
    return bhydraulic(take, r, 0.01, 0.01 + 12 * T, groan=(48.0, 0.45), stop_gain=0.7, settle=0.01 + 16 * T)


def hydraulic_lift(take, r):
    """Lifting a load with the legs (grab tick 13: the lift runs 12-20; throw tick 3: the hoist): the pressure rises for
    7 ticks (curve 'in', the groan climbing 52 -> 100 Hz), no end-stop, then the relief valve's 'tss' as it holds."""
    return bhydraulic(take, r, 0.01, 0.01 + 7 * T, groan=(52.0, 0.90), stop_gain=0, curve='in', creak_f=640.0,
                      relief=0.01 + 7 * T + 0.03)


BSTRAIN = [dict(m=1.00, swell=1.6, hold=0.55, creaks=(0.12, 0.48, 0.80)), dict(m=1.10, swell=2.1, hold=0.62, creaks=(0.20, 0.62)),
           dict(m=0.85, swell=1.3, hold=0.75, creaks=(0.08, 0.36, 0.58, 0.86))]


def hydraulic_strain(take, r):
    """Holding a load (the hold clip, every 2 s): the cylinders seep a strain hiss (800-4000 Hz) that swells in over
    0.1 s, pulses at 1.3-2.1 Hz, holds 0.55-0.75 s and fades, a faint low groan (45 Hz) with a 9 Hz tremble under it, and the
    frame creaking two to four times. A small valve tick at 3 ms marks the start."""
    q = BSTRAIN[take - 1]
    m = q['m']
    dur = 1.30
    b = buf(dur)
    t = tt(dur)
    env = np.clip(t / 0.10, 0, 1) * np.where(t < q['hold'], 1.0, np.exp(-(t - q['hold']) / 0.10))
    pulse = 0.75 + 0.25 * np.sin(2 * np.pi * q['swell'] * t)
    hiss = bandpass(noise(dur, r), 800 * m, 4000 * m) * env * pulse
    groan = osc_harm(np.full(len(t), 45.0 * m), 6, r, 1.3) * env * (1 - 0.3 * (0.5 + 0.5 * np.cos(2 * np.pi * 9 * t)))
    b += 0.55 * norm(hiss) + 0.25 * norm(groan)
    place(b, tooth(r, 1800 * m), 0.003, 0.35)  # the valve's tick as the strain starts
    for at in q['creaks']:
        place(b, creak(r, 0.09, 820 * m * r.uniform(0.92, 1.08), decay=0.006), at, 0.35)
    return b


# ---------------------------------------------------------------- the jar's clamp posts, the jar, the fists

BCREL = [dict(m=1.00, gap=0.012), dict(m=1.10, gap=0.018), dict(m=0.90, gap=0.009)]


def clamp_release(take, r):
    """The two clamp posts unlatch (board tick 1; the posts swing open 1-6): each post's pawl clacks out of its catch
    (a strike, a tooth, the brass post's body at 600 / 950 Hz), left at 4 ms and right 9-18 ms later, and the pads'
    air vents with a short hiss as they come off the glass."""
    q = BCREL[take - 1]
    m = q['m']
    b = buf(0.50)
    for k, at in enumerate((0.004, 0.004 + q['gap'])):
        g = 1.0 - 0.15 * k
        place(b, strike(r, 1800 * m), at, 0.40 * g)
        place(b, tooth(r, 2500 * m * (1 + 0.04 * k)), at, 0.30 * g)
        place(b, norm(iron_body(r, 600 * m, 950 * m, 0.55, band_scale=m)), at, 0.45 * g)
        place(b, thunk(r, 160, 110, 0.030, 0.015), at, 0.15 * g)
    place(b, norm(noise_burst(2000 * m, 8000 * m, 0.045, r, attack=0.006)), 0.015, 0.35)
    return b


BCCLOSE = [dict(m=1.00, dk=1.00, gap=0.014), dict(m=1.10, dk=1.25, gap=0.020), dict(m=1.20, dk=0.80, gap=0.010)]


def clamp_close(take, r):
    """The clamp posts swing back and close on the glass with a clunk (board 18, eject 30, build 194): each post hits
    its stop (a strike, the brass post's iron body at 500 / 820 Hz, a thunk 140 -> 85 Hz) with its rubber pad meeting
    the glass (a soft thud and a faint glass tick), left then right 10-20 ms apart; the lock catches 60 ms on."""
    q = BCCLOSE[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(0.55)
    for k, at in enumerate((0.004, 0.004 + q['gap'])):
        g = 1.0 - 0.15 * k
        place(b, strike(r, 1500 * m), at, 0.35 * g)
        place(b, norm(iron_body(r, 500 * m * (1 + 0.03 * k), 820 * m, 0.7 * dk, band_scale=m)), at, 0.55 * g)
        place(b, thunk(r, 140 * m, 85 * m, 0.040, 0.015), at, 0.35 * g)
        place(b, norm(noise_burst(800 * m, 4000 * m, 0.014, r)), at, 0.20 * g)
        place(b, norm(glass_modes(r, 2100 * m, decays=(0.02, 0.012, 0.008, 0.005))), at + 0.002, 0.06 * g)
    place(b, tooth(r, 2300 * m), 0.060, 0.16)
    return b


BCBLOW = [dict(m=1.00, stops=(0.060, 0.074)), dict(m=1.25, stops=(0.052, 0.090))]


def clamp_blow(take, r):
    """The eject blowing both clamp posts open (eject tick 3): a pneumatic bang at 3 ms (a strike, a broadband blast
    300-7000 Hz, a boom 95 -> 42 Hz), the two brass posts clanging as they fly (700 Hz, 5 ms apart), then hitting
    their stops 50-80 ms later."""
    q = BCBLOW[take - 1]
    m = q['m']
    b = buf(0.70)
    h = 0.003
    place(b, strike(r, 1300), h, 0.60)
    place(b, norm(noise_burst(300 * m, 7000 * m, 0.030, r, attack=0.0008)), h, 0.70)
    place(b, thunk(r, 95, 42, 0.060, 0.020), h, 0.40)
    for k in range(2):
        place(b, norm(modal(700 * m * (1 + 0.05 * k), [1, 2.756, 5.404], [1, 0.6, 0.35], [0.05, 0.035, 0.02], r)),
              h + 0.005 * k, 0.30)
    for k, at in enumerate(q['stops']):
        place(b, norm(iron_body(r, 620 * m * (1 + 0.04 * k), 1000 * m, 0.4, band_scale=m)), at, 0.22)
        place(b, tooth(r, 2200 * m), at, 0.12)
    return b


BJSEAT = [dict(m=1.00, dk=1.00), dict(m=1.12, dk=1.20), dict(m=0.90, dk=0.85)]


def jar_seat(take, r):
    """The jar setting into the Bulwark's brass collar (board tick 11): a thick glass base meeting metal at 4 ms (a
    strike, low glass modes at 1.15 kHz, short), the heavier collar's ring (1.6 kHz), a thunk for the jar's weight;
    the bounce knocks at 65 and 100 ms. A seat, not a smash."""
    q = BJSEAT[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(0.55)

    def knock():
        return mix(0.35 * strike(r, 2300), 0.45 * norm(glass_modes(r, 1150 * m, decays=(0.05 * dk, 0.03 * dk, 0.018 * dk, 0.010 * dk))),
                   0.32 * norm(modal(1600 * m, [1, 2.4, 3.8], [1, 0.4, 0.2], [0.035, 0.022, 0.014], r)),
                   0.30 * thunk(r, 190, 120, 0.035, 0.015), 0.20 * norm(noise_burst(400, 2500, 0.010, r)))
    place(b, norm(knock()), 0.004, 1.0)
    place(b, norm(knock()), 0.065, 0.28)
    place(b, norm(knock()), 0.100, 0.09)
    return b


BGRIP = [dict(m=1.00, rebound=0.040), dict(m=1.12, rebound=0.052), dict(m=0.90, rebound=0.034)]


def grip_clack(take, r):
    """A fist's finger block closing in the boot's self-test (boot 20 and 24): a hollow clack at 3 ms (the block meeting
    the palm: a strike, hollow modes at 900 Hz, a small low knock) and the pawl's tick, then a tiny rebound tick."""
    q = BGRIP[take - 1]
    m = q['m']
    b = buf(0.22)
    place(b, strike(r, 2000 * m), 0.003, 0.30)
    place(b, norm(modal(900 * m, [1, 2.1, 3.4], [1, 0.5, 0.25], [0.012, 0.009, 0.006], r, 0.02)), 0.003, 0.55)
    place(b, thunk(r, 260 * m, 180 * m, 0.012, 0.006), 0.003, 0.20)
    place(b, tooth(r, 2600 * m), 0.006, 0.20)
    place(b, norm(modal(1300 * m, [1, 2.1], [1, 0.4], [0.006, 0.004], r)), q['rebound'], 0.12)
    return b


BGRAB = [dict(m=1.00, gap=0.028, dk=1.00), dict(m=1.08, gap=0.036, dk=1.20), dict(m=0.92, gap=0.022, dk=0.85)]


def grab_close(take, r):
    """Both fists clamping onto a load (grab tick 10, the server takes hold): a heavy double clunk, left then right
    22-36 ms apart, each a strike, the fist's iron body (420 / 700 Hz), a thunk 130 -> 70 Hz and the finger block's
    hollow clack; then the grip's hydraulics locking (a 'tss' at 80 ms and a lock tooth at 105 ms)."""
    q = BGRAB[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(0.60)
    for k, at in enumerate((0.003, 0.003 + q['gap'])):
        g = 1.0 - 0.12 * k
        place(b, strike(r, 1300 * m), at, 0.35 * g)
        place(b, norm(iron_body(r, 420 * m * (1 + 0.04 * k), 700 * m, 0.8 * dk, band_scale=0.9 * m)), at, 0.60 * g)
        place(b, thunk(r, 130 * m, 70 * m, 0.045, 0.018), at, 0.45 * g)
        place(b, norm(modal(900 * m, [1, 2.1, 3.4], [1, 0.5, 0.25], [0.012, 0.009, 0.006], r, 0.02)), at, 0.18 * g)
    place(b, norm(noise_burst(1500, 6000, 0.030, r, attack=0.008)), 0.080, 0.10)
    place(b, tooth(r, 2200 * m), 0.105, 0.12)
    return b


BGDRY = [dict(m=1.00, gap=0.006, relay=0.060), dict(m=1.10, gap=0.009, relay=0.072), dict(m=0.90, gap=0.004, relay=0.052)]


def grab_dry(take, r):
    """The fists snapping shut on air (grab_refused tick 10; also the refusal's sound, so it must never be skipped): a
    hollow clack, both fists 4-9 ms apart (the finger blocks hitting the palms with nothing between: hollow modes at
    800 Hz, a strike, a light knock), the overshoot's rebound tick two ticks later, and a small relay click 50-70 ms
    in (the refusal relay)."""
    q = BGDRY[take - 1]
    m = q['m']
    b = buf(0.40)
    for k, at in enumerate((0.003, 0.003 + q['gap'])):
        g = 1.0 - 0.15 * k
        place(b, strike(r, 1800 * m), at, 0.35 * g)
        place(b, norm(modal(800 * m * (1 + 0.05 * k), [1, 2.1, 3.4, 4.9], [1, 0.55, 0.3, 0.12],
                            [0.030, 0.020, 0.012, 0.008], r, 0.02)), at, 0.60 * g)
        place(b, thunk(r, 210 * m, 140 * m, 0.020, 0.010), at, 0.22 * g)
    place(b, relay_click(r, 2200 * m, release_after=0.025), q['relay'], 0.30)
    place(b, norm(modal(1000 * m, [1, 2.1], [1, 0.4], [0.015, 0.010], r)), 0.100, 0.25)
    return b


BSHRUG = [dict(m=1.00, tau=0.15), dict(m=0.88, tau=0.18)]


def shrug_hiss(take, r):
    """The shrug after a refused grab (grab_refused tick 15): a short pneumatic sigh, a valve 'tsk' then air let out
    through a band that falls from 600-4000 Hz to 400-2000 Hz as the pressure drops (0.15-0.18 s decay), with a
    little low breath."""
    q = BSHRUG[take - 1]
    m = q['m']
    dur = 1.20
    b = buf(dur)
    t = tt(dur)
    t0 = np.maximum(t - 0.01, 0)
    pr = (t >= 0.01) * np.clip(t0 / 0.03, 0, 1) * np.where(t0 < 0.05, 1.0, np.exp(-(t0 - 0.05) / q['tau']))
    hiss = sweep_bandpass(noise(dur, r), (400 + 200 * pr) * m, (2000 + 2000 * pr) * m) * pr
    breath = bandpass(noise(dur, r), 250, 550) * pr ** 1.5
    b += 0.80 * norm(hiss) + 0.25 * norm(breath)
    place(b, norm(noise_burst(4000, 12000, 0.0015, r)), 0.003, 0.40)
    return b


BWHOOSH = [dict(m=1.00, L=0.60), dict(m=1.15, L=0.68)]


def whoosh(take, r):
    """The throw's heave (throw tick 12, just after the release): a big swing of air, 0.60-0.68 s, a band that opens
    from 120-400 Hz to 450-1400 Hz with the speed of the swing, a low rumble under it; starts at -18 dB so the onset
    is on the key."""
    q = BWHOOSH[take - 1]
    m, L = q['m'], q['L']
    dur = 0.95
    b = buf(dur)
    t = tt(dur)
    s = np.where(t < L, np.sin(np.pi * t / L) ** 2, 0.0)
    env = 0.22 * np.clip(t / 0.003, 0, 1) * np.clip(1 - t / 0.25, 0, 1) + 0.78 * s
    air = sweep_bandpass(noise(dur, r), (120 + 330 * s) * m, (400 + 1000 * s) * m) * env
    rum = lowpass(noise(dur, r), 200) * env ** 1.5
    b += 0.8 * norm(air) + 0.35 * norm(rum)
    return b


BTREL = [dict(m=1.00, gap=0.010), dict(m=1.25, gap=0.016)]


def throw_release(take, r):
    """The fists springing open as the load leaves (throw tick 11): each finger block's spring snapping it open (a
    strike, a short spring twang falling 950 -> 720 Hz, a spring-steel body at 1.3 kHz, the fist's small iron body),
    left then right 10-16 ms apart, and the catch's tooth."""
    q = BTREL[take - 1]
    m = q['m']
    b = buf(0.50)
    for k, at in enumerate((0.003, 0.003 + q['gap'])):
        g = 1.0 - 0.15 * k
        place(b, strike(r, 1600 * m), at, 0.35 * g)
        place(b, sweep_sine(950 * m, 720 * m, 0.040, 0.015), at, 0.30 * g)
        place(b, norm(modal(1300 * m, [1, 2.6, 4.1], [1, 0.4, 0.2], [0.030, 0.018, 0.010], r)), at, 0.30 * g)
        place(b, norm(iron_body(r, 600 * m, 950 * m, 0.5, band_scale=m)), at, 0.35 * g)
    place(b, tooth(r, 2400 * m), 0.030, 0.12)
    return b


BSETDOWN = [dict(m=1.00, tau=0.065), dict(m=1.10, tau=0.075), dict(m=0.90, tau=0.055)]


def set_down(take, r):
    """A load set down on the ground: a soft heavy thud (a thunk 90 -> 45 Hz, the load's body as low-passed noise, a
    little ground debris), the fists letting go with a small knock 3 ticks later and the hydraulics easing ('tss')."""
    q = BSETDOWN[take - 1]
    m = q['m']
    b = buf(0.80)
    h = 0.003
    place(b, thunk(r, 90 * m, 45 * m, q['tau'], 0.025), h, 0.80)
    place(b, norm(lowpass(noise(0.3, r), 900 * m) * exp_env(0.3, 0.030, 0.003)), h, 0.40)
    place(b, norm(lowpass(noise(0.3, r), 2000) * exp_env(0.3, 0.020, 0.002)), h, 0.12)
    place(b, mix(thunk(r, 160, 110, 0.020, 0.010), 0.5 * norm(modal(500 * m, [1, 2.4], [1, 0.4], [0.015, 0.010], r))),
          0.15, 0.12)
    place(b, norm(noise_burst(1200, 4500, 0.040, r, attack=0.010)), 0.17, 0.05)
    return b


# ---------------------------------------------------------------- life: the neck, vents, refusal, rattle

BNECK = [dict(m=1.00, second=0.075, teeth=3), dict(m=1.12, second=0.090, teeth=4), dict(m=0.90, second=0.065, teeth=3)]


def neck_crack(take, r):
    """The idle tic: the torso's ring bearing cracking like a neck (idle_tic 10 and 22; one file per crack). Two
    ratchety pops: each a cluster of teeth 3-4 ms apart, a pop of the bearing's iron (700 Hz) and a small thunk; the
    second pop 65-90 ms after the first, a little smaller."""
    q = BNECK[take - 1]
    m = q['m']
    b = buf(0.45)
    for k, at in enumerate((0.003, q['second'])):
        g = 1.0 - 0.25 * k
        for j in range(q['teeth']):
            place(b, tooth(r, 1700 * m * (1 + 0.05 * j)), at + 0.0035 * j, 0.25 * g * (1 - 0.2 * j))
        place(b, strike(r, 1500 * m), at, 0.30 * g)
        place(b, norm(modal(700 * m, [1, 2.4, 3.9], [1, 0.4, 0.2], [0.018, 0.012, 0.008], r)), at, 0.40 * g)
        place(b, thunk(r, 170 * m, 110 * m, 0.020, 0.010), at, 0.20 * g)
    return b


BVBLAST = [dict(m=1.00, tau=0.30), dict(m=1.12, tau=0.25)]


def vent_blast(take, r):
    """The scald blast as both big flaps blow open: the flaps bang at 3 and 15 ms (a strike and a heavy steel flap at
    520 Hz each); the steam bursts (pressure up in 15 ms, holds 150 ms, decays 0.25-0.30 s), a hiss band 2.5-16 kHz
    with a 1-5 kHz roar under it while the pressure is high; a low whump."""
    q = BVBLAST[take - 1]
    m = q['m']
    dur = 1.90
    b = buf(dur)
    t = tt(dur)
    t0 = np.maximum(t - 0.004, 0)
    pr = (t >= 0.004) * np.clip(t0 / 0.015, 0, 1) * np.where(t0 < 0.15, 1.0, np.exp(-(t0 - 0.15) / q['tau']))
    hiss = bandpass(noise(dur, r), 2500 * m, 16000) * pr + 0.40 * bandpass(noise(dur, r), 1000, 5000) * pr ** 2
    b += 0.85 * norm(hiss)
    for at in (0.003, 0.015):
        place(b, strike(r, 2200), at, 0.25)
        place(b, norm(modal(520 * m, [1, 1.59, 2.14], [1, 0.5, 0.3], [0.025, 0.018, 0.012], r)), at, 0.20)
    place(b, norm(lowpass(noise(0.7, r), 300) * exp_env(0.7, 0.09, 0.004)), 0.003, 0.18)
    return b


BVCLANK = [dict(m=1.00, dk=1.00), dict(m=1.10, dk=1.20), dict(m=0.90, dk=0.85)]


def vent_clank(take, r):
    """A big striped vent flap slamming shut: a strike, the heavy steel flap's iron body (480 / 760 Hz), a thunk
    130 -> 80 Hz, its spring rattling once 35 ms later."""
    q = BVCLANK[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(0.55)
    place(b, strike(r, 1600), 0.004, 0.45)
    place(b, norm(iron_body(r, 480 * m, 760 * m, 0.7 * dk, band_scale=m)), 0.004, 0.60)
    place(b, thunk(r, 130, 80, 0.040, 0.015), 0.004, 0.35)
    place(b, tooth(r, 2000 * m), 0.035, 0.12)
    return b


BDRY = [dict(m=1.00, at=0.050), dict(m=1.10, at=0.064), dict(m=1.22, at=0.042)]


def dry_fire(take, r):
    """A heavy arm module that cannot fire: the hammer falls on nothing at 3 ms (a strike, a spring-steel body at
    2.2 kHz, a tooth), the follower settles with a lower knock (1.3 kHz) 40-65 ms later, and a very faint falling spring
    tone (1.7 -> 1.3 kHz)."""
    q = BDRY[take - 1]
    m = q['m']
    b = buf(0.32)
    place(b, strike(r, 2300 * m), 0.003, 0.45)
    place(b, norm(modal(2200 * m, [1, 1.6, 2.9], [1, 0.5, 0.25], [0.011, 0.008, 0.005], r)), 0.003, 0.50)
    place(b, tooth(r, 2800 * m), 0.003, 0.25)
    place(b, norm(modal(1300 * m, [1, 1.6], [1, 0.4], [0.010, 0.006], r)), q['at'], 0.24)
    place(b, sweep_sine(1700 * m, 1300 * m, 0.018, 0.018), q['at'] + 0.020, 0.06)
    return b


BHURT = [dict(m=1.00, dk=1.00, rat=(0.055, 0.140)), dict(m=1.10, dk=1.25, rat=(0.065, 0.165)),
         dict(m=1.22, dk=1.10, rat=(0.048, 0.120))]


def hurt(take, r):
    """The loader takes a hit: a deep clang. A strike, the enamel body's sheet-metal modes (400 Hz x 1, 1.59, 2.14,
    2.65, 3.9), the frame's iron body (330 / 560 Hz), a thunk 120 -> 70 Hz; the clamp posts rattle twice after."""
    q = BHURT[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(1.10)
    h = 0.003
    place(b, strike(r, 1100), h, 0.50)
    place(b, norm(modal(400 * m, [1, 1.59, 2.14, 2.65, 3.9], [1, 0.7, 0.5, 0.35, 0.2],
                        [x * dk for x in (0.08, 0.065, 0.05, 0.035, 0.025)], r, 0.01)), h, 0.45)
    place(b, norm(iron_body(r, 330 * m, 560 * m, 1.0 * dk, band_scale=m)), h, 0.55)
    place(b, thunk(r, 120, 70, 0.06, 0.02), h, 0.35)
    place(b, tooth(r, 2100 * m), q['rat'][0], 0.15)
    place(b, norm(modal(1500 * m, [1, 2.4], [1, 0.4], [0.014, 0.009], r)), q['rat'][1], 0.09)
    return b


BCOLLAPSE = [dict(m=1.00, fists=(0.030, 0.048), bits=[(0.34, 2300, 0.11), (0.47, 1700, 0.09), (0.60, 2700, 0.07),
                                                      (0.74, 2100, 0.07), (0.90, 1950, 0.05), (1.03, 2050, 0.04),
                                                      (1.14, 2150, 0.045), (1.23, 2200, 0.04), (1.31, 2250, 0.035), (1.38, 2300, 0.03)]),
             dict(m=1.15, fists=(0.024, 0.056), bits=[(0.30, 2000, 0.10), (0.43, 2550, 0.09), (0.58, 1800, 0.08),
                                                      (0.78, 2400, 0.06), (0.97, 2200, 0.05), (1.12, 2250, 0.04),
                                                      (1.25, 2300, 0.035), (1.35, 2350, 0.03), (1.42, 2400, 0.025)])]


def collapse(take, r):
    """The 40-tick collapse's moment at tick 27: the loader slumps forward onto its front and fists, the heaviest crash
    in the set. The front hits at 3 ms (a strike, a deep thunk 80 -> 32 Hz, the body's iron and sheet-metal modes at
    240-330 Hz, ground debris), the two fists 24-56 ms after (an iron body and a thunk each), a bounce 2 ticks later
    and a settle at 4; the frame groans as it takes the weight (a slow creak 0.25-0.75 s); then bolts and pins drop
    and roll, faster and quieter, and a last hydraulic line leaks a little."""
    q = BCOLLAPSE[take - 1]
    m = q['m']
    b = buf(2.40)
    h = 0.003
    place(b, strike(r, 900), h, 0.45)
    place(b, thunk(r, 80 * m, 32 * m, 0.15, 0.04), h, 0.60)
    place(b, norm(iron_body(r, 240 * m, 420 * m, 1.8, band_scale=0.75 * m)), h, 0.60)
    place(b, norm(modal(330 * m, [1, 1.59, 2.14, 2.65], [1, 0.7, 0.5, 0.3], [0.12, 0.09, 0.07, 0.05], r, 0.01)), h, 0.35)
    place(b, norm(lowpass(noise(1.1, r), 2000) * exp_env(1.1, 0.12, 0.002)), h, 0.45)
    for at in q['fists']:
        place(b, mix(thunk(r, 120 * m, 60 * m, 0.06, 0.02), 0.7 * norm(iron_body(r, 420 * m, 700 * m, 0.8, band_scale=0.8 * m))), at, 0.35)
    place(b, mix(thunk(r, 110, 60, 0.07, 0.02), 0.6 * norm(iron_body(r, 280 * m, 480 * m, 0.8, band_scale=0.75 * m))), 2 * T, 0.30)
    place(b, mix(thunk(r, 100, 65, 0.05, 0.02), 0.4 * norm(modal(360 * m, [1, 2.756], [1, 0.4], [0.035, 0.022], r))), 4 * T, 0.12)
    place(b, creak(r, 0.50, 420 * m, rate=(0.025, 0.050), decay=0.008, rise=-0.2), 0.25, 0.07)
    for at, f, g in q['bits']:
        place(b, norm(modal(f * m, [1, 2.4, 4.1], [1, 0.4, 0.2], [0.02, 0.012, 0.008], r, 0.02)), at, g)
    t = tt(1.0)
    leak = bandpass(noise(1.0, r), 2000, 7000) * np.clip(t / 0.1, 0, 1) * np.exp(-t / 0.15)
    place(b, norm(leak), 0.50, 0.03)
    return soft(b, 3.5)


BRATTLE = [[0.030, 0.038, 0.027, 0.046, 0.034, 0.052, 0.060], [0.025, 0.044, 0.035, 0.030, 0.056, 0.042],
           [0.038, 0.029, 0.049, 0.033, 0.062, 0.045, 0.039, 0.066]]


def rattle(take, r):
    """The clamp posts and loose panels rattling (boot_legs, vent 95): brass ticks (1.9 kHz) and lower panel knocks
    (650 Hz) at irregular 25-66 ms intervals, dying away."""
    b = buf(0.60)
    at = 0.003
    for k, dt in enumerate(BRATTLE[take - 1]):
        g = 0.85 ** k * r.uniform(0.7, 1.0)
        if k % 3 == 1:
            place(b, norm(modal(650 * r.uniform(0.95, 1.05), [1, 2.756], [1, 0.4], [0.024, 0.015], r)), at, 0.65 * g)
        else:
            place(b, norm(modal(1900 * r.uniform(0.92, 1.08), [1, 2.4, 3.9], [1, 0.45, 0.2], [0.013, 0.009, 0.006], r)), at, g)
        at += dt
    return b


BSIGH = [dict(m=1.00, tau=0.34, puff=1.30), dict(m=0.88, tau=0.29, puff=1.20)]


def sigh(take, r):
    """A big pneumatic sigh, not a voice (shutdown 17, eject 26, build 197): a valve 'tsk', then air let out (pressure
    up in 50 ms, decaying 0.29-0.34 s) through a band falling from 550-3800 Hz to 280-1500 Hz as the pressure drops,
    a low breath under it; one last small puff after it has finished (the joke)."""
    q = BSIGH[take - 1]
    m = q['m']
    dur = 1.70
    b = buf(dur)
    t = tt(dur)
    t0 = np.maximum(t - 0.01, 0)
    pr = (t >= 0.01) * np.clip(t0 / 0.05, 0, 1) * np.where(t0 < 0.07, 1.0, np.exp(-(t0 - 0.07) / q['tau']))
    lo = (280 + 270 * pr) * m
    hi = (1500 + 2300 * pr) * m
    hiss = sweep_bandpass(noise(dur, r), lo, hi) * pr
    breath = bandpass(noise(dur, r), 220, 500) * pr ** 1.5
    b += 0.80 * norm(hiss) + 0.28 * norm(breath)
    place(b, norm(noise_burst(4000, 12000, 0.0015, r)), 0.003, 0.45)
    place(b, norm(noise_burst(1000, 4500, 0.030, r, attack=0.008)), q['puff'], 0.20)
    return b


BPDOWN = [dict(m=1.00, spin=0.90), dict(m=1.10, spin=1.05)]


def power_down(take, r):
    """Shutdown (shutdown tick 0): the main relay drops out (a big click and a deep thunk), the drivetrain spins down
    over 0.90-1.05 s (whine 150 + 420 s Hz falling with speed), the 62 Hz drivetrain hum dies with it, one last small
    tick."""
    q = BPDOWN[take - 1]
    m = q['m']
    dur = 1.55
    b = buf(dur)
    t = tt(dur)
    s = 0.9 * np.clip(1 - (t - 0.01) / q['spin'], 0, 1) ** 1.5 * (t > 0.01)
    b += 0.8 * motor(t, s, r, 150 * m, 420 * m, grit=0.32, air=0.14, whine=0.30, rotor_div=8)
    hum = sum(a * np.sin(2 * np.pi * 62 * k * t) for k, a in zip([1, 2, 3, 4], [1, 0.6, 0.4, 0.25]))
    b += 0.20 * norm(hum) * s ** 0.5
    place(b, relay_click(r, 1100 * m), 0.003, 0.50)
    place(b, thunk(r, 120, 70, 0.04, 0.015), 0.003, 0.35)
    place(b, norm(modal(1800 * m, [1, 1.5], [1, 0.4], [0.007, 0.005], r)), q['spin'] + 0.08, 0.08)
    return b


# ---------------------------------------------------------------- references (measured as the game plays them)

GOLEM_WALK1 = dict(event='entity.iron_golem.step', file='mob__irongolem__walk1.ogg', vol=1.0, pitch=1.0)
ATTACK_WEAK = dict(event='entity.player.attack.weak', file='entity__player__attack__weak1.ogg', vol=1.0, pitch=1.0)
MACE = dict(event='item.mace.smash_ground', file='item__mace__smash_ground1.ogg', vol=1.0, pitch=1.0)
VAULT_FAIL = dict(event='block.vault.insert_item_fail', file='block__vault__insert_fail.ogg', vol=1.0, pitch=1.0)
BOTTLE = dict(event='item.bottle.empty', file='item__bottle__empty1.ogg', vol=1.0, pitch=1.0)
COPPER_TRAPDOOR = dict(event='block.copper_trapdoor.close', file='block__copper_trapdoor__toggle1.ogg', vol=1.0, pitch=0.95)
LANTERN = dict(event='block.lantern.place', file='block__lantern__place1.ogg', vol=1.0, pitch=0.88)
EQUIP_IRON = dict(event='item.armor.equip_iron', file='item__armor__equip_iron1.ogg', vol=1.0, pitch=1.0)
EQUIP_CHAIN = dict(event='item.armor.equip_chain', file='item__armor__equip_chain1.ogg', vol=1.0, pitch=1.0)
TRIDENT = dict(event='item.trident.throw', file='item__trident__throw1.ogg', vol=1.0, pitch=1.0)
FIREWORK = dict(event='entity.firework_rocket.blast', file='fireworks__blast1.ogg', vol=1.0, pitch=1.0)
BEACON_AMBIENT = dict(event='block.beacon.ambient', file='block__beacon__ambient.ogg', vol=1.0, pitch=1.0)

BASE = dict(category='NEUTRAL', attenuation=16, fixed_range=False, volume=1.0)
KEY_NOTE = (' Keyframe volume 1.0 and pitch 1.0 (the file carries the Bulwark\'s loudness and size; the clip\'s '
            'proposed 0.5-0.8 volume and 0.8-0.9 pitch were standing in for it with the Hopper\'s file).')


def ev(name, **kw):
    d = dict(BASE)
    d.update(id=f'mecha.bulwark.{name}', file=f'mecha/bulwark/{name}')
    d.update(kw)
    return d


SOUNDS = [
    # ---- the walk
    ev('motor', fn=motor_loop, finish=finish_loop, takes=1, target=-27.5, cls='hum, motor (loop at full speed)',
       kind=('loop', 3.0), fade_ms=0, hit_ms=None, ref=BEACON_AMBIENT, pitch=[0.97, 1.03], attenuation=12,
       subtitle_en=None, subtitle_pt=None,
       play='LOOP, as mecha.motor: one client tickable instance per walking Bulwark looping this 3.0 s file (seamless: no fades in it). Pitch = (0.8 + 0.4 s) x a fixed per-instance offset 0.97-1.03, a little lower while carrying a load; volume = sqrt(s); chase both 0.2 a tick (pitch at most 0.05 a tick); start from 0, stop below 0.01. No subtitle (the footsteps carry it).'),
    ev('step_heavy', fn=step_heavy, takes=3, target=-25.0, cls='heavy walker footfall (between step and landing)',
       kind=('step', 0.30), fade_ms=20, hit_ms=3, ref=GOLEM_WALK1, pitch=[0.95, 1.05], attenuation=24,
       subtitle_en='Heavy mecha footsteps', subtitle_pt='Passos pesados de mecha',
       play='Server at every walk plant (step:l_heavy / step:r_heavy: walk ticks 0 and 12, walk_b 0 and 9, limp 16), at the boot, volume 1.0 with the block\'s own step sound underneath. Random take, pitch 0.95-1.05. Heard for about 0.4 s, so plants 0.45 s apart do not pile up.'),
    ev('step', fn=bstep, takes=3, target=-27.5, cls='medium step (servo class)', kind=('step', 0.22), fade_ms=15,
       hit_ms=3, ref=GOLEM_STEP, pitch=[0.94, 1.06], attenuation=20,
       subtitle_en='Heavy mecha footsteps', subtitle_pt='Passos pesados de mecha',
       play='Turning in place (turn_l / turn_r plants at ticks 7 and 15) and other light shuffles. Volume 1.0. Random take and pitch.'),
    ev('step_drag', fn=step_drag, takes=3, target=-27.0, cls='scrape (servo class)', kind=('step', 0.25),
       fade_ms=15, hit_ms=200, preroll_ticks=4, ref=EQUIP_IRON, pitch=[0.95, 1.05],
       subtitle_en='Mecha boot drags', subtitle_pt='Bota de mecha se arrasta',
       play='Limp (flat battery): start it 4 ticks before the left plant (limp tick 28 of the previous cycle) so the toe catches at 200 ms on tick 0 (step:l_drag).'),
    ev('stomp', fn=stomp, takes=2, target=-24.0, cls='heavy walker footfall (the stamp)', kind=('step', 0.30),
       fade_ms=20, hit_ms=3, ref=GOLEM_WALK1, pitch=[0.97, 1.03], attenuation=24,
       subtitle_en='Mecha stamps its boot', subtitle_pt='Mecha bate a bota',
       play='boot_legs tick 32 (stomp:r), at the right boot, volume 1.0: 1 dB over a walking plant, with the dust. The hull settles onto it at 100-125 ms.'),
    ev('land_light', fn=land_light, takes=3, target=-22.5, cls='landing (between step and slam)', kind=('step', 0.27),
       fade_ms=20, hit_ms=3, ref=ATTACK_WEAK, pitch=[0.95, 1.05], attenuation=24,
       subtitle_en='Mecha lands', subtitle_pt='Mecha aterrissa',
       play='Server on land tick 0 (land:light).'),
    ev('land_heavy', fn=land_heavy, takes=2, target=-15.0, cls='heavy impact', kind=('step', 0.50), fade_ms=30,
       hit_ms=3, ref=MACE, pitch=[0.96, 1.04], attenuation=32,
       subtitle_en='Mecha lands heavily', subtitle_pt='Mecha aterrissa pesado',
       play='Server on land_heavy tick 0 (land:heavy), with the dust ring. The frame groans 0.12-0.40 s after.'),
    # ---- servos and hydraulics
    ev('servo', fn=bservo, takes=3, target=-27.0, cls='servo', kind=('servo', 6 * T), fade_ms=20, hit_ms=3,
       ref=PISTON_IN, pitch=[1.0, 1.0], subtitle_en='Servo whirs', subtitle_pt='Servo zune',
       play='Every Bulwark servo key (board 3, 14; boot 16; grab 2; grab_refused 2; the arm verbs). A 6-tick move with its heavy stop clunk at 310 ms; at the key\'s pitch p the move lasts 6/p ticks. Random take.' + KEY_NOTE),
    ev('servo_settle', fn=servo_settle, takes=3, target=-28.0, cls='servo (settle)', kind='latch', fade_ms=12,
       hit_ms=4, ref=PISTON_OUT, pitch=[0.95, 1.05], subtitle_en=None, subtitle_pt=None,
       play='throw tick 14 (the follow-through comes to rest). No subtitle.' + KEY_NOTE),
    ev('servo_sag', fn=servo_sag, takes=3, target=-27.0, cls='servo (sag)', kind=('servo', 0.28), fade_ms=20,
       hit_ms=3, ref=PISTON_IN, pitch=[1.0, 1.0], subtitle_en=None, subtitle_pt=None,
       play='dry_fire 10 and the vent slump, where the Hopper plays mecha.servo_sag; the arm bottoms out at 230-300 ms by take. No subtitle (dry_fire carries it).' + KEY_NOTE),
    ev('servo_catch', fn=servo_catch, takes=3, target=-27.0, cls='servo (gear skip)', kind=('servo', 0.36),
       fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[1.0, 1.0], subtitle_en='Servo stutters', subtitle_pt='Servo engasga',
       play='limp tick 22: the gear skips on the key, the motor stalls two ticks and drags itself up again.' + KEY_NOTE),
    ev('servo_fail', fn=servo_fail, takes=2, target=-26.0, cls='servo (dying)', kind=('servo', 0.95), fade_ms=30,
       hit_ms=3, ref=PISTON_IN, pitch=[0.97, 1.03], subtitle_en='Servo dies', subtitle_pt='Servo morre',
       play='destroyed tick 12: the whine falls and stutters; the seize clunk lands at 850-950 ms (ticks 29-31, as it settles after the slump at 27).' + KEY_NOTE),
    ev('hydraulic_stand', fn=hydraulic_stand, takes=3, target=-26.5, cls='servo (hydraulic stroke)',
       kind=('servo', 16 * T), fade_ms=25, hit_ms=3, ref=PISTON_IN, pitch=[0.98, 1.02],
       subtitle_en='Mecha stands up', subtitle_pt='Mecha se levanta',
       play='boot_legs tick 5 (one groan: the stroke runs 5-17, the end-stop at 610 ms on 17, the settle knock at 810 ms on 21) and build tick 44.' + KEY_NOTE),
    ev('hydraulic_lift', fn=hydraulic_lift, takes=3, target=-26.5, cls='servo (hydraulic lift)', kind=('servo', 0.40),
       fade_ms=25, hit_ms=3, ref=PISTON_IN, pitch=[0.98, 1.02],
       subtitle_en='Hydraulics lift', subtitle_pt='Hidráulica levanta',
       play='grab tick 13 (the lift 12-20) and throw tick 3 (the hoist): the pressure rises for 7 ticks (to 360 ms), the relief valve sighs at 390 ms.' + KEY_NOTE),
    ev('hydraulic_strain', fn=hydraulic_strain, takes=3, target=-28.5, cls='hiss (strain, repeated)', kind='hiss',
       fade_ms=40, hit_ms=3, ref=PISTON_OUT, pitch=[0.95, 1.05],
       subtitle_en='Hydraulics strain', subtitle_pt='Hidráulica força',
       play='hold tick 20, so once every 2 s while it holds a load; a different take each time (the creaks fall in different places). Quiet by construction.' + KEY_NOTE),
    # ---- the jar's clamp posts, the jar, the fists
    ev('clamp_release', fn=clamp_release, takes=3, target=-24.0, cls='clamp release', kind='latch', fade_ms=12,
       hit_ms=4, ref=DOOR_OPEN, pitch=[0.95, 1.05],
       subtitle_en='Jar clamps unlatch', subtitle_pt='Grampos do pote se soltam',
       play='board tick 1: the left post unlatches at 4 ms, the right 9-18 ms later, the pads vent.' + KEY_NOTE),
    ev('clamp_close', fn=clamp_close, takes=3, target=-23.0, cls='clamp (latch)', kind='latch', fade_ms=12, hit_ms=4,
       ref=TRAPDOOR_CLOSE, pitch=[0.95, 1.05], subtitle_en='Jar clamps close', subtitle_pt='Grampos do pote fecham',
       play='board tick 18, eject tick 30, build tick 194: left post at 4 ms, right 10-20 ms later, the lock at 60 ms.' + KEY_NOTE),
    ev('clamp_blow', fn=clamp_blow, takes=2, target=-20.5, cls='slam (bang)', kind='latch', fade_ms=15, hit_ms=3,
       ref=FIREWORK, pitch=[0.95, 1.05], attenuation=24,
       subtitle_en='Jar clamps blow open', subtitle_pt='Grampos abrem com estouro',
       play='eject tick 3: the bang on the key, the posts hit their stops 50-80 ms later.' + KEY_NOTE),
    ev('jar_seat', fn=jar_seat, takes=3, target=-26.5, cls='glass clink', kind='latch', fade_ms=12, hit_ms=4,
       ref=LANTERN, pitch=[0.95, 1.05], subtitle_en='Jar seats', subtitle_pt='Pote se encaixa',
       play='board tick 11 (the jar seats), bounce knocks at 65 and 100 ms (12-13).' + KEY_NOTE),
    ev('grip_clack', fn=grip_clack, takes=3, target=-27.0, cls='tick (finger block)', kind='click', fade_ms=8,
       hit_ms=3, ref=COPPER_TRAPDOOR, pitch=[0.95, 1.05], subtitle_en=None, subtitle_pt=None,
       play='boot ticks 20 and 24 (the self-test closes each fist\'s finger block). No subtitle (part of the self-test).' + KEY_NOTE),
    ev('grab_close', fn=grab_close, takes=3, target=-22.0, cls='clamp (heavy double clunk)', kind='latch',
       fade_ms=15, hit_ms=3, ref=TRAPDOOR_CLOSE, pitch=[0.95, 1.05], attenuation=20,
       subtitle_en='Mecha grabs', subtitle_pt='Mecha agarra',
       play='Server on grab tick 10 (grab:close), when it takes hold, at the midpoint of the fists: left fist on the tick, right 22-36 ms later, the grip locks at 105 ms.'),
    ev('grab_dry', fn=grab_dry, takes=3, target=-25.5, cls='clamp (hollow clack)', kind='latch', fade_ms=12,
       hit_ms=3, ref=COPPER_TRAPDOOR, pitch=[0.95, 1.05], attenuation=20,
       subtitle_en='Fists close on nothing', subtitle_pt='Punhos fecham no vazio',
       play='Server on grab_refused tick 10 (grab:dry), with the HUD refusal line. It is the refusal\'s sound: play it on every refused grab press, never skip it. The relay clicks 50-70 ms in.'),
    ev('shrug_hiss', fn=shrug_hiss, takes=2, target=-26.0, cls='hiss (short sigh)', kind='hiss', fade_ms=40,
       hit_ms=3, ref=BOTTLE, pitch=[0.95, 1.05], subtitle_en='Mecha shrugs', subtitle_pt='Mecha dá de ombros',
       play='grab_refused tick 15 (the shrug).' + KEY_NOTE),
    ev('whoosh', fn=whoosh, takes=2, target=-20.5, cls='slam class (the throw\'s air)', kind=('whoosh', 0.55),
       fade_ms=30, hit_ms=3, ref=TRIDENT, pitch=[0.95, 1.05], attenuation=24, subtitle_en=None, subtitle_pt=None,
       play='throw tick 12, the tick after the release. No subtitle (throw_release carries it).' + KEY_NOTE),
    ev('throw_release', fn=throw_release, takes=2, target=-23.0, cls='clamp (spring release)', kind='latch',
       fade_ms=12, hit_ms=3, ref=TRAPDOOR_CLOSE, pitch=[0.95, 1.05], attenuation=20,
       subtitle_en='Mecha throws', subtitle_pt='Mecha arremessa',
       play='Server on throw tick 11 (throw:release), when it lets go, at the fists.'),
    ev('set_down', fn=set_down, takes=3, target=-24.5, cls='landing (soft heavy thud)', kind=('step', 0.25),
       fade_ms=20, hit_ms=3, ref=GOLEM_WALK1, pitch=[0.95, 1.05], attenuation=20,
       subtitle_en='Mecha sets load down', subtitle_pt='Mecha pousa a carga',
       play='Code, at the load, on the tick a held load is put down on the ground (not thrown). The fists let go 150 ms later.'),
    # ---- life, vents, damage
    ev('neck_crack', fn=neck_crack, takes=3, target=-26.0, cls='ratchet (idle tic)', kind='latch', fade_ms=12,
       hit_ms=3, ref=VAULT_FAIL, pitch=[0.95, 1.05],
       subtitle_en='Mecha cracks its neck', subtitle_pt='Mecha estala o pescoço',
       play='idle_tic ticks 10 and 22, one file each (a different take): two ratchety pops 65-90 ms apart.' + KEY_NOTE),
    ev('vent_blast', fn=vent_blast, takes=2, target=-23.0, cls='hiss (scald blast)', kind='hiss', fade_ms=60,
       hit_ms=3, ref=FIZZ, pitch=[0.95, 1.05], attenuation=24,
       subtitle_en='Vents blast steam', subtitle_pt='Respiros soltam vapor',
       play='Server on vent tick 3 (vent:blast), from between the two vents.'),
    ev('vent_clank', fn=vent_clank, takes=3, target=-22.5, cls='slam (flap)', kind='latch', fade_ms=12, hit_ms=4,
       ref=TRAPDOOR_CLOSE, pitch=[0.95, 1.05], subtitle_en='Vent flap clanks', subtitle_pt='Aba do respiro bate',
       play='vent 82 and 86, land_heavy\'s flaps closing.' + KEY_NOTE),
    ev('collapse', fn=collapse, takes=2, target=-14.0, cls='heavy impact (the crash)', kind=('step', 1.40),
       fade_ms=40, hit_ms=3, ref=MACE, pitch=[0.97, 1.03], attenuation=32,
       subtitle_en='Mecha collapses', subtitle_pt='Mecha desaba',
       play='Server on destroyed tick 27 (destroyed:thud): the front hits at 3 ms, the fists 24-56 ms after, the bounce at 100 ms (29), the groan and loose bits for about 1.4 s.'),
    ev('hurt', fn=hurt, takes=3, target=-20.5, cls='slam (clang)', kind=('step', 0.32), fade_ms=20, hit_ms=3,
       ref=DOOR_CLOSE, pitch=[0.94, 1.06], attenuation=24,
       subtitle_en='Mecha takes a hit', subtitle_pt='Mecha leva um golpe',
       play='The Bulwark\'s hurt sound (getHurtSound), on the hit, with the hurt clip. Random take, pitch 0.94-1.06.'),
    ev('dry_fire', fn=dry_fire, takes=3, target=-28.5, cls='tick (dry click)', kind='click', fade_ms=10, hit_ms=3,
       ref=DISP_FAIL, pitch=[0.95, 1.05], subtitle_en='Arm dry-fires', subtitle_pt='Braço falha o disparo',
       play='dry_fire tick 7 (arm:dry:<side>), at the wrist, with the refusal line. Played for every refusal of an arm input, so it must never be skipped.'),
    ev('rattle', fn=rattle, takes=3, target=-27.0, cls='rattle (servo class)', kind='latch', fade_ms=12, hit_ms=3,
       ref=EQUIP_CHAIN, pitch=[0.94, 1.06], subtitle_en=None, subtitle_pt=None,
       play='vent 95 and the hurt clip\'s clamp rattle. No subtitle (small life).' + KEY_NOTE),
    ev('sigh', fn=sigh, takes=2, target=-26.0, cls='hiss (pneumatic sigh)', kind='hiss', fade_ms=40, hit_ms=3,
       ref=PISTON_IN, pitch=[0.95, 1.05], subtitle_en='Mecha sighs', subtitle_pt='Mecha suspira',
       play='shutdown 17, eject 26, build 197. The last little puff lands at 1.20-1.30 s.' + KEY_NOTE),
    ev('power_down', fn=power_down, takes=2, target=-27.0, cls='servo (spin-down)', kind=('servo', 0.95),
       fade_ms=30, hit_ms=3, ref=PISTON_IN, pitch=[0.97, 1.03], subtitle_en='Mecha powers down',
       subtitle_pt='Mecha desliga',
       play='shutdown tick 0: the relay drops at once, the spin-down runs to about tick 18 (take 1) or 21 (take 2), with the sag.' + KEY_NOTE),
]

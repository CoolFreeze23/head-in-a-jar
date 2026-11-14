"""Travel: how a head in a glass jar gets about. The Wheels module folding down and away and its little motor
driving, the bare jar tipped on its side and rolled (very, very slowly), set upright again, the Hop Springs releasing
and the jar landing, the Propeller flying it, and the Shock Lid zapping whatever hit the jar.

Every statement here comes from the recipe or from a measurement.

Two materials. The jar is glass with saline inside: its knocks are the jar's own struck glass modes (ratios 1, 2.7,
5.1, 8.2, as in the jar's tap and its cracks), damped short by the liquid, and its insides slosh: band noise
(150-1400 Hz) through a resonance that moves as the liquid settles, in lumps (the saline lapping back and forth), with
a little 1-3.5 kHz lap on the crests and a few Minnaert bubbles. The modules are the company's crisp, dry 1950s
appliance parts: the servo clack (strike, a small enamelled plate, a tooth), the clamp release (a vent puff, a light
strike and a small iron body), the hiss puff, the ratchet pawl, the motor model (whine, brush grit gated at the rotor
rate, air) and the arc crackle. No cartoon boing anywhere: no pitched spring mode, no glide.

Three files are loops (jar.drive, jar.roll, jar.propeller). They are built periodic as the other loops here are
(recipes_machines.loop_finish): every noise layer is filtered circularly in the frequency domain, every periodic part
completes a whole number of cycles in the file, every transient is placed circularly (its tail wraps round to the
start), and loop_finish replaces the one-shot chain (no fades, no zeroed ends), rotating the file to its quietest
near-zero sample so the seam sits in a lull.

Times inside a file are seconds; one tick is 50 ms. Takes differ by construction (per-take tables of frequency scale,
decay scale, timings and layer balance), not by luck of the seed.
"""
import numpy as np
from nsynth import *
from recipes_machines import loop_finish, cband, cnoise

T = TICK


# ---------------------------------------------------------------- shared pieces

def clack(r, f, d=1.0, hp=1500.0):
    """One small servo clack (a wheel leg's latch snapping home): a strike (high-passed at hp, 1.5 kHz times the take's scale), a small enamelled-steel plate
    at f (ratios 1, 1.59, 2.14, 3.9, 12 ms and shorter), a faint ratchet tooth above it and a light thunk well under it."""
    return norm(mix(0.38 * strike(r, hp),
                    0.60 * norm(modal(f, [1, 1.59, 2.14, 3.9], [1, 0.6, 0.4, 0.25],
                                      [0.012 * d, 0.009 * d, 0.006 * d, 0.004 * d], r, 0.01)),
                    0.12 * tooth(r, f * 1.7),
                    0.16 * thunk(r, 230, 150, 0.015, 0.008)))


def glass_knock(r, f0, d=1.0, shell=650.0, shell_gain=0.20, strike_hp=2500.0, th=(160.0, 95.0), th_gain=0.20,
                th_tau=0.040):
    """The jar's glass meeting something: a strike, the jar's glass modes at f0 damped by the saline (45 ms and
    shorter, times d), the big vessel's low shell mode, and a thunk for the weight (kept 8 dB or more under the glass,
    so it reads as glass, not a drum)."""
    return norm(mix(0.35 * strike(r, strike_hp),
                    0.60 * norm(glass_modes(r, f0, decays=(0.045 * d, 0.030 * d, 0.018 * d, 0.010 * d))),
                    shell_gain * norm(modal(shell, [1, 1.52], [1, 0.3], [0.030 * d, 0.018 * d], r, 0.01)),
                    th_gain * thunk(r, th[0], th[1], th_tau, th_tau / 2)))


def slosh(r, decay, band=(150.0, 1400.0), fr=(900.0, 350.0), rate=9.0, rise=0.015, nbub=3, bub=(550.0, 1250.0),
          lap=0.14, bub_gain=0.30):
    """The saline sloshing inside the jar: band noise and a resonance (a band x0.75-1.3 round a centre gliding from
    fr[0] to fr[1] as it settles), swelling in `rise` and decaying with `decay`, in lumps `rate` a second (the liquid
    lapping back and forth), a little 1-3.5 kHz lap on the crests, and `nbub` Minnaert bubbles in its first part.
    Generated 7 decay times long."""
    d = 7 * decay + rise + 0.02
    t = tt(d)
    env = (1 - np.exp(-t / rise)) * np.exp(-t / decay)
    wave = 0.45 + 0.55 * np.cos(np.pi * rate * t) ** 2
    fc = fr[1] + (fr[0] - fr[1]) * np.exp(-t / (1.5 * decay))
    body = bandpass(noise(d, r), band[0], band[1])
    res = sweep_bandpass(noise(d, r), fc * 0.75, fc * 1.3)
    lp = bandpass(noise(d, r), 1000, 3500)
    out = 0.50 * norm(body * env * wave) + 0.50 * norm(res * env * wave) + lap * norm(lp * env * wave ** 2)
    for k in range(nbub):
        f = r.uniform(*bub)
        tau = 0.012 * np.sqrt(1000 / f)
        at = r.uniform(0.15, 1.6) * decay
        place(out, bubble(r, f, tau, rise=r.uniform(0.12, 0.22)), at, bub_gain * r.uniform(0.6, 1.0))
    return out


def place_wrap(y, x, at):
    """Add x into the periodic buffer y from time `at`, its tail wrapping round to the start (a loop's transient)."""
    i0 = int(round(at * SR))
    idx = (i0 + np.arange(len(x))) % len(y)
    np.add.at(y, idx, x)
    return y


# ---------------------------------------------------------------- the Wheels module: down, up

LEG = [1.00, 0.96, 1.04, 0.98]  # the four wheel legs' clack pitch (front left, front right, back left, back right)
WHEELS_DOWN = [dict(m=1.00, d=1.00, tau=0.034, puff=0.335, puff_g=0.45),
               dict(m=1.08, d=1.20, tau=0.046, puff=0.345, puff_g=0.52),
               dict(m=0.92, d=0.85, tau=0.022, puff=0.322, puff_g=0.40)]


def wheels_down(take, r):
    """The Wheels module deploying, 'clk-clk-clk-clk': four small servo clacks 100 ms apart (the first at 3 ms, each
    leg a little different in pitch about 1.25 kHz), each followed by a 50 ms whirr of its tiny servo pulling the leg
    tight, then at 322-345 ms a short hiss puff (1-4.5 kHz, 6 ms swell, 22-46 ms decay) as the jar settles onto the
    wheels' dampers, with a faint glass settle knock and a soft thunk under it."""
    p = WHEELS_DOWN[take - 1]
    m, d = p['m'], p['d']
    dur = 0.80
    b = buf(dur)
    t = tt(dur)
    s = np.zeros(len(t))
    for k, lg in enumerate(LEG):
        at = 0.003 + k * 2 * T
        place(b, clack(r, 1250 * m * lg, d, 1500 * m), at, 1.0 - 0.06 * k)
        s += 0.5 * speed_curve(t, at + 0.006, at + 0.056)
    b += 0.16 * motor(t, s, r, 700 * m, 900 * m, grit=0.40, air=0.15, whine=0.30)
    place(b, norm(noise_burst(1000 * m, 4500 * m, p['tau'], r, attack=0.006)), p['puff'], p['puff_g'])
    place(b, glass_knock(r, 1700 * m, 0.6 * d, shell=600 * m, th_gain=0.30), p['puff'] + 0.004, 0.16)
    return b


WHEELS_UP = [dict(m=1.00, d=1.00, tau=0.026, rel=0.330, body=0.35),
             dict(m=1.08, d=1.20, tau=0.034, rel=0.340, body=0.42),
             dict(m=0.92, d=0.85, tau=0.021, rel=0.325, body=0.30)]


def wheels_up(take, r):
    """The Wheels module folding away: the same four clacks in reverse order (back right first), each 10 % higher than
    on the way down, each with its servo whirr; then a small clamp release at 325-340 ms: the house unlatch made small
    (a vent puff, 1.2-5 kHz, 21-34 ms, and 30 ms later a light strike on a small iron body at 1.1 kHz with short decays)."""
    p = WHEELS_UP[take - 1]
    m, d = p['m'], p['d']
    dur = 0.80
    b = buf(dur)
    t = tt(dur)
    s = np.zeros(len(t))
    for k, lg in enumerate(LEG[::-1]):
        at = 0.003 + k * 2 * T
        place(b, clack(r, 1250 * 1.10 * m * lg, d, 1500 * m), at, 1.0 - 0.06 * k)
        s += 0.5 * speed_curve(t, at + 0.006, at + 0.056)
    b += 0.16 * motor(t, s, r, 770 * m, 990 * m, grit=0.40, air=0.15, whine=0.30)
    place(b, norm(noise_burst(1200 * m, 5000 * m, p['tau'], r, attack=0.005)), p['rel'], 0.40)
    place(b, strike(r, 1800 * m), p['rel'] + 0.030, 0.30)
    place(b, norm(iron_body(r, 1100 * m, 1700 * m, 0.35 * d, band_scale=m)), p['rel'] + 0.030, p['body'])
    return b


# ---------------------------------------------------------------- the Wheels module: driving (loop)

DRIVE_L = 2.0
DRIVE_F0 = 1200.0  # Hz at pitch 1.0: 2400 whole cycles in the loop; 900-1800 Hz at pitch 0.75-1.5
DRIVE_ROTOR = DRIVE_F0 / 6  # 200 Hz brush rate (the kit's motor: grit gated at the whine / 6): 400 cycles
DRIVE_GEAR = 50.0  # Hz gear-mesh rate: 100 teeth a loop
DRIVE_WHEEL = 2.5  # Hz wheel turns: 5 a loop


def drive(take, r):
    """The Wheels' tiny electric motor driving, a remote-control car with a head in it; a seamless 2.0 s loop. A small
    whine on 1200 Hz (8 harmonics falling as 1/k^1.2, band 800-7000 Hz, a 0.2 % wobble once a wheel turn), the brush
    grit (2.5-8 kHz noise gated by the commutator at 200 Hz), a little gear grit (1.5-6 kHz noise in narrow pulses at
    the 50 Hz gear mesh, each tooth its own size from a 100-entry table, so it is rough, not a tone), their level
    rocking 20 % once a wheel turn (2.5 Hz), and faint air. Whole cycles in 2.0 s: 2400, 400, 100 and 5."""
    n = int(round(DRIVE_L * SR))
    t = np.arange(n) / SR
    ph = 2 * np.pi * DRIVE_F0 * t + 0.002 * DRIVE_F0 / DRIVE_WHEEL * np.sin(2 * np.pi * DRIVE_WHEEL * t)
    whine = sum((1 / h ** 1.2) * np.sin(h * ph + r.uniform(0, 2 * np.pi)) for h in range(1, 9))
    whine = cband(whine, 800, 7000)
    brush = cband(cnoise(n, r), 2500, 8000) * (0.5 + 0.5 * np.cos(2 * np.pi * DRIVE_ROTOR * t)) ** 4
    teeth = r.uniform(0.2, 1.0, int(DRIVE_GEAR * DRIVE_L))
    # the tooth size steps where the pulse is at zero (half a mesh period off its peak), so no step is heard
    size = teeth[np.floor(t * DRIVE_GEAR + 0.5).astype(int) % len(teeth)]
    gear = cband(cnoise(n, r), 1500, 6000) * (0.5 + 0.5 * np.cos(2 * np.pi * DRIVE_GEAR * t)) ** 8 * size
    rock = 0.8 + 0.2 * np.sin(2 * np.pi * DRIVE_WHEEL * t + r.uniform(0, 2 * np.pi))
    air = cband(cnoise(n, r), 1000, 9000)
    return 0.30 * norm(whine) + 0.30 * norm(brush) * rock + 0.24 * norm(gear) * rock + 0.10 * norm(air)


# ---------------------------------------------------------------- the bare jar: tip over, sit up

TIP = [dict(m=1.00, d=1.00, decay=0.125, rate=8.0, fr=(900.0, 320.0), band=(150.0, 1400.0), at=0.016),
       dict(m=1.09, d=1.20, decay=0.140, rate=9.5, fr=(1300.0, 450.0), band=(200.0, 1900.0), at=0.020),
       dict(m=0.91, d=0.85, decay=0.105, rate=7.0, fr=(800.0, 280.0), band=(150.0, 1400.0), at=0.013)]


def tip_over(take, r):
    """The bare jar tipped on its side to roll: the glass knock as the side meets the ground at 3 ms (the jar's glass
    modes at about 1.5 kHz, damped, with its low shell mode), a soft thunk under it, and from 13-20 ms the big slosh of
    the saline inside (a 15 ms swell, 105-140 ms decay, lumps 7-9.5 a second, the resonance falling from 0.8-1.3 kHz
    to 280-450 Hz as it settles, the noise band 150-1400 Hz or 200-1900 Hz by take, four bubbles)."""
    p = TIP[take - 1]
    m, d = p['m'], p['d']
    b = buf(1.25)
    place(b, glass_knock(r, 1500 * m, d, shell=620 * m, shell_gain=0.25, th=(150.0, 85.0), th_gain=0.22,
                         th_tau=0.045), 0.003, 1.0)
    place(b, norm(slosh(r, p['decay'], band=p['band'], fr=p['fr'], rate=p['rate'], nbub=4)), p['at'], 0.80)
    return b


SIT = [dict(m=1.00, d=1.00, decay=0.090, fr=(330.0, 850.0), hit=0.250),
       dict(m=1.08, d=1.20, decay=0.110, fr=(380.0, 980.0), hit=0.250),
       dict(m=0.92, d=0.85, decay=0.075, fr=(300.0, 760.0), hit=0.250)]


def sit_up(take, r):
    """The jar set back upright: the base leaving the ground at 3 ms (a faint glass tick), the slosh first as it lifts
    (a slower 40 ms swell, 75-110 ms decay, the resonance rising from about 300 Hz to 0.8-1 kHz as the saline runs back
    down to the base), then at 250 ms (5 ticks) the clink of the base landing upright (the jar's glass modes higher,
    about 2.2 kHz, with a light thunk), and a little settling slop after it."""
    p = SIT[take - 1]
    m, d = p['m'], p['d']
    b = buf(1.20)
    place(b, norm(mix(noise_burst(2500, 7000, 0.003, r, attack=0.0003),
                      0.6 * modal(2600 * m, [1, 1.47], [1, 0.4], [0.004, 0.0025], r))), 0.003, 0.30)
    place(b, norm(slosh(r, p['decay'], fr=p['fr'], rate=7.5, rise=0.040, nbub=3)), 0.006, 0.55)
    place(b, glass_knock(r, 2200 * m, d, shell=700 * m, shell_gain=0.15, strike_hp=3000.0, th=(180.0, 115.0),
                         th_gain=0.18, th_tau=0.030), p['hit'], 1.0)
    place(b, norm(slosh(r, 0.050, fr=(700.0, 500.0), rate=12.0, rise=0.008, nbub=1)), p['hit'] + 0.012, 0.22)
    return b


# ---------------------------------------------------------------- the bare jar: rolling (loop)

ROLL_L = 3.0  # s: two revolutions at pitch 1.0 (one every 1.5 s)
ROLL_REV = 2 / ROLL_L  # revolutions a second at pitch 1.0


def glunk(r, f, strap_gain, delay, decay_scale):
    """One 'glunk' a revolution: the goggles strap thunking against the ground (a low thump falling 130 -> 75 Hz and a
    short leathery slap of 250-1500 Hz noise), then `delay` later the dull glass knock of the jar's side (its glass
    modes about f with short decays, low-passed at 3.5 kHz so it stays dull)."""
    out = buf(0.40)
    place(out, thunk(r, 130, 75, 0.035, 0.015), 0.0, 0.55 * strap_gain)
    place(out, norm(lowpass(noise_burst(250, 1500, 0.010, r, attack=0.001), 1500)), 0.0, 0.45 * strap_gain)
    gk = mix(0.25 * strike(r, 1500),
             0.60 * norm(glass_modes(r, f, decays=tuple(x * decay_scale for x in (0.035, 0.020, 0.012, 0.007)))),
             0.20 * norm(modal(f * 0.42, [1, 1.52], [1, 0.3], [0.025, 0.015], r)))
    place(out, norm(lowpass(gk, 3500)), delay, 0.80)
    return out


def roll(take, r):
    """The tipped jar rolling very, very slowly ('glunk ... glunk'), a seamless 3.0 s loop holding two revolutions.
    A low glass rumble (circular 70-260 Hz noise) bumped by the ground (a smooth random 2-12 Hz level, periodic) and
    rocking once a revolution, the glass faintly carrying it (900-1400 Hz at -26 dB); one glunk a revolution, at
    0.45 s and 1.95 s, not alike (the second 6 % higher, the strap later and softer, the glass ringing shorter); and a
    small slosh between them as the saline turns over (1.15 s and 2.65 s, different sizes and rates). Whole cycles:
    the revolution rocking 2 in the loop; noise periodic by construction; transients placed with wrap-round."""
    n = int(round(ROLL_L * SR))
    t = np.arange(n) / SR
    bumps = 0.70 + 0.30 * norm(cband(cnoise(n, r), 2, 12))
    rev = 0.85 + 0.15 * np.cos(2 * np.pi * ROLL_REV * t + r.uniform(0, 2 * np.pi))
    rumble = cband(cnoise(n, r), 70, 260) * bumps * rev
    sing = cband(cnoise(n, r), 900, 1400) * bumps
    y = 0.30 * norm(rumble) + 0.015 * norm(sing)
    tr = np.zeros(n)
    place_wrap(tr, glunk(r, 1150.0, 1.00, 0.012, 1.00), 0.45)
    place_wrap(tr, glunk(r, 1220.0, 0.75, 0.019, 0.80), 1.95)
    place_wrap(tr, 0.38 * norm(slosh(r, 0.080, fr=(600.0, 380.0), rate=8.0, rise=0.030, nbub=2, bub_gain=0.25)), 1.15)
    place_wrap(tr, 0.30 * norm(slosh(r, 0.100, fr=(520.0, 340.0), rate=6.5, rise=0.040, nbub=1, bub_gain=0.25)), 2.65)
    return y + tr


# ---------------------------------------------------------------- Hop Springs

HOP = [dict(m=1.00, gap=0.018, tau=0.040, puff=0.55), dict(m=1.10, gap=0.025, tau=0.052, puff=0.62),
       dict(m=0.90, gap=0.013, tau=0.038, puff=0.50)]


def hop(take, r):
    """Hop Springs releasing under the jar, 'tchk' and a puff, no boing: the catch letting go at 3 ms (a strike and a
    ratchet tooth), the pawl dropping onto its stop 13-25 ms later (a tooth, a small brass body at 1.6 kHz with 10 ms
    decays, a short noise band: the pile's ratchet clank), a puff of air from 8 ms (0.7-6 kHz, 6 ms swell, 38-52 ms
    decay) and a light low thump of the jar leaving the ground. No pitched spring mode and no glide."""
    p = HOP[take - 1]
    m = p['m']
    b = buf(0.50)
    place(b, strike(r, 3000), 0.003, 0.35)
    place(b, tooth(r, 3000 * m), 0.003, 0.55)
    clank = mix(tooth(r, 2600 * m), 0.45 * norm(modal(1600 * m, [1, 2.756, 5.404], [1, 0.5, 0.3], [0.010, 0.007, 0.004], r)),
                0.25 * norm(noise_burst(2000, 7000, 0.006, r)))
    place(b, norm(clank), 0.003 + p['gap'], 1.0)
    place(b, norm(noise_burst(700 * m, 6000 * m, p['tau'], r, attack=0.006)), 0.008, p['puff'])
    place(b, thunk(r, 200, 130, 0.020, 0.010), 0.004, 0.12)
    return b


HOP_LAND = [dict(m=1.00, d=1.00, decay=0.060, rate=12.0), dict(m=1.08, d=1.20, decay=0.075, rate=10.0),
            dict(m=0.92, d=0.85, decay=0.050, rate=14.0)]


def hop_land(take, r):
    """The jar landing from a hop: a glass knock on its base at 3 ms (the jar's glass modes about 1.8 kHz with its low
    shell mode and a thunk for the weight) and from 10 ms a short slosh (8 ms swell, 50-75 ms decay, two bubbles)."""
    p = HOP_LAND[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.75)
    place(b, glass_knock(r, 1800 * m, d, shell=650 * m, shell_gain=0.22, th=(160.0, 95.0), th_gain=0.24,
                         th_tau=0.040), 0.003, 1.0)
    place(b, norm(slosh(r, p['decay'], fr=(800.0, 400.0), rate=p['rate'], rise=0.008, nbub=2)), 0.010, 0.55)
    return b


# ---------------------------------------------------------------- the Propeller (loop)

PROP_L = 2.0
PROP_BLADE = 55.0  # Hz blade-pass rate at pitch 1.0: 110 whole cycles; 41-72 Hz at pitch 0.75-1.3
PROP_ROTOR = PROP_BLADE / 2  # two blades: 27.5 Hz hub, 55 cycles
PROP_WHINE = 12 * PROP_BLADE  # 660 Hz motor whine: 1320 cycles


def propeller(take, r):
    """The Propeller flying the jar, the mod's mosquito; a seamless 2.0 s loop. The blade-pass whir: 300-5000 Hz noise
    swelling 55 times a second ((0.5 + 0.5 cos)^3 over a 25 % floor), the two blades not alike (12 % level difference,
    so the hub's 27.5 Hz shows), with a brighter 2-9 kHz tip noise on the sharpest part of each pass; a small motor
    whine on 660 Hz (10 harmonics at 1/k^1.1, band 500-7000 Hz, a 0.3 % wobble once a turn: the buzz); a faint hub
    flutter (120-500 Hz noise gated once a turn). Whole cycles in 2.0 s: 110, 55 and 1320."""
    n = int(round(PROP_L * SR))
    t = np.arange(n) / SR
    gate = (0.5 + 0.5 * np.cos(2 * np.pi * PROP_BLADE * t)) ** 3
    blades = 1 + 0.12 * np.cos(2 * np.pi * PROP_ROTOR * t)
    whir = cband(cnoise(n, r), 300, 5000) * (0.25 + 0.75 * gate) * blades
    tip = cband(cnoise(n, r), 2000, 9000) * gate ** 2 * blades
    ph = 2 * np.pi * PROP_WHINE * t + 0.003 * PROP_WHINE / PROP_ROTOR * np.sin(2 * np.pi * PROP_ROTOR * t)
    whine = cband(sum((1 / h ** 1.1) * np.sin(h * ph + r.uniform(0, 2 * np.pi)) for h in range(1, 11)), 500, 7000)
    hub = cband(cnoise(n, r), 120, 500) * (0.5 + 0.5 * np.cos(2 * np.pi * PROP_ROTOR * t + r.uniform(0, 2 * np.pi))) ** 2
    return 0.45 * norm(whir) + 0.12 * norm(tip) + 0.20 * norm(whine) + 0.10 * norm(hub)


# ---------------------------------------------------------------- the Shock Lid

SHOCK = [dict(m=1.00, band=(2000, 10000), thump=0.45), dict(m=1.12, band=(3000, 14000), thump=0.32),
         dict(m=0.90, band=(1800, 9000), thump=0.52)]


def shock_lid(take, r):
    """The Shock Lid zapping something that hit the jar: a sharp snap at 3 ms, then the kit's arc crackle (filtered
    noise bursts 2-8 ms wide, 6-25 ms apart, over about 0.12 s, the first the strongest), a short mains buzz under its
    first 60 ms (1.5-5 kHz noise gated at 100 Hz), over the low thump of the discharge (a sine falling 130 -> 60 Hz,
    45 ms, and 40-300 Hz noise, 30 ms)."""
    p = SHOCK[take - 1]
    m = p['m']
    b = buf(0.60)
    place(b, strike(r, 3000, 0.0008), 0.003, 0.60)
    place(b, norm(crackle(r, 0.12, p['band'][0], p['band'][1], spacing=(0.006, 0.025), width=(0.002, 0.008))),
          0.004, 0.80)
    tb = tt(0.06)
    bz = bandpass(noise(0.06, r), 1500, 5000) * (0.5 + 0.5 * np.cos(2 * np.pi * 100 * tb)) ** 3 * np.sin(np.pi * tb / 0.06)
    place(b, norm(bz), 0.005, 0.20)
    place(b, thunk(r, 130 * m, 60 * m, 0.045, 0.015), 0.003, p['thump'])
    place(b, norm(noise_burst(40, 300, 0.030, r, attack=0.002)), 0.003, p['thump'] * 0.6)
    return b


# ---------------------------------------------------------------- the mecha's arm and back modules: Arc Cannon, Tesla Coil

ARC = [dict(m=1.00, tau=0.115, band=(1500, 12000), boom=(180.0, 45.0)),
       dict(m=1.10, tau=0.135, band=(2000, 14000), boom=(200.0, 50.0)),
       dict(m=0.90, tau=0.120, band=(1200, 10000), boom=(165.0, 40.0))]


def arc_cannon(take, r):
    """The mecha's Arc Cannon firing, heard far off: a sharp snap at 3 ms, the kit's arc crackle in dense bursts (2-8 ms
    wide, 3-12 ms apart, falling away over about 0.15 s, 1.2-14 kHz by take), on the sharp low boom of the discharge (a
    sine falling 165-200 -> 40-50 Hz with a 30 ms glide, 30-250 Hz noise with a 1 ms attack, a 300-2000 Hz body),
    kept under the crack so the file is a crack with a boom in it, ringing off with a 115-135 ms decay so it is gone by about 0.5 s. No thunder roll: no long rumble after it."""
    p = ARC[take - 1]
    m = p['m']
    b = buf(1.10)
    place(b, strike(r, 2000, 0.0008), 0.003, 0.30)
    cr = crackle(r, 0.15, p['band'][0], p['band'][1], spacing=(0.003, 0.012), width=(0.002, 0.008), first_gain=0.7)
    cr = cr * np.exp(-tt(len(cr) / SR) / 0.07)
    place(b, norm(cr), 0.004, 1.0)
    f0, f1 = p['boom']
    place(b, sweep_sine(f0, f1, p['tau'], 0.030, attack=0.001), 0.003, 0.22)
    place(b, norm(noise_burst(30, 250, p['tau'] * 0.75, r, attack=0.001)), 0.003, 0.14)
    place(b, norm(noise_burst(300 * m, 2000 * m, p['tau'], r, attack=0.001)), 0.003, 0.40)
    return b


TESLA = [dict(m=1.00, band=(2000, 10000), end=0.340, buzz=0.30), dict(m=1.10, band=(2500, 12000), end=0.320, buzz=0.25),
         dict(m=0.90, band=(1700, 9000), end=0.360, buzz=0.34)]


def tesla_coil(take, r):
    """The mecha's Tesla Coil zapping what is near it: a short buzzing arc. Crackle bursts (2-8 ms, 5-30 ms apart) from
    4 ms to 0.32-0.36 s, a 120 Hz mains buzz under them (harmonics 1-30 at 1/k, 300-4000 Hz, kept under the crackle, with 1.5-5 kHz noise gated
    at 120 Hz), a 10 ms rise, held, a 15 ms release, ending in a snap at 0.32-0.36 s (a strike, a 2-10 kHz tick and a
    small thunk)."""
    p = TESLA[take - 1]
    m = p['m']
    end = p['end']
    b = buf(0.80)
    place(b, norm(crackle(r, end - 0.03, p['band'][0], p['band'][1], spacing=(0.005, 0.030), width=(0.002, 0.008))),
          0.004, 0.75)
    d = end + 7 * 0.015
    t = tt(d)
    env = np.clip(t / 0.010, 0, 1) * np.where(t < end, 1.0, np.exp(-(t - end) / 0.015))
    hum = bandpass(osc_harm(np.full(len(t), 120.0 * m), 30, r, 1.0), 300, 4000)
    gz = bandpass(noise(d, r), 1500, 5000) * (0.5 + 0.5 * np.cos(2 * np.pi * 120.0 * m * t)) ** 3
    place(b, norm(norm(hum) + 0.6 * norm(gz)) * env, 0.004, p['buzz'])
    place(b, strike(r, 3000, 0.0008), end, 0.70)
    place(b, norm(noise_burst(2000, 10000, 0.004, r, attack=0.0003)), end, 0.50)
    place(b, thunk(r, 160, 90, 0.025, 0.010), end, 0.20)
    return b


# ---------------------------------------------------------------- the event list

PISTON_IN = dict(event='block.piston.contract', file='tile__piston__in.ogg', vol=0.5, pitch=0.675)
PISTON_OUT = dict(event='block.piston.extend', file='tile__piston__out.ogg', vol=0.5, pitch=0.725)
BEACON_AMBIENT = dict(event='block.beacon.ambient', file='block__beacon__ambient.ogg', vol=0.9, pitch=1.0)
CLICK = dict(event='block.dispenser.dispense', file='random__click.ogg', vol=1.0, pitch=1.0)
VAULT_OFF = dict(event='block.vault.deactivate', file='block__vault__deactivate.ogg', vol=1.0, pitch=1.0)
FIREWORK_BLAST = dict(event='entity.firework_rocket.blast', file='fireworks__blast1.ogg', vol=1.0, pitch=1.0)
BEE_STING = dict(event='entity.bee.sting (what LifeSupport plays now, volume 1, pitch 0.5)', file='mob__bee__sting.ogg',
                 vol=1.0, pitch=0.45)

BASE = dict(category='PLAYERS', attenuation=12, fixed_range=False, volume=1.0, muffled=False)
LOOP_NOTE = (' Loop file: no fades in it. A client tickable sound instance with looping on, created once, started at '
             'volume 0 and stopped when the chased volume falls under 0.01 (volume chased 0.2 a tick, pitch at most '
             '0.05 a tick). Register with "stream": false so the whole file is one looped buffer.')


def ev(**kw):
    d = dict(BASE)
    d.update(kw)
    return d


# kind: a (name, seconds) pair gives the heard-length range seconds - 0.02 to seconds + 0.30 in measure.py
SOUNDS = [
    ev(id='jar.wheels_down', file='jar/wheels_down', fn=wheels_down, takes=3, target=-27.0, cls='servo (four clacks)',
       kind=('servo', 0.40), fade_ms=20, hit_ms=3, ref=PISTON_OUT, pitch=[0.95, 1.05],
       subtitle_en='Jar wheels fold down', subtitle_pt='Rodas do pote descem',
       play='Server, at the jar, on the tick the Wheels module deploys (replaces the piston extend at pitch 1.6). The four clacks are at 3, 103, 203, 303 ms, the settle puff at 322-345 ms. Random pitch 0.95-1.05.'),
    ev(id='jar.wheels_up', file='jar/wheels_up', fn=wheels_up, takes=3, target=-27.0, cls='servo (four clacks, clamp release)',
       kind=('servo', 0.40), fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[0.95, 1.05],
       subtitle_en='Jar wheels fold away', subtitle_pt='Rodas do pote recolhem',
       play='Server, at the jar, on the tick the Wheels fold away. Clacks at 3, 103, 203, 303 ms (reverse order, 10 % higher than jar.wheels_down), the clamp release at 325-370 ms. Random pitch 0.95-1.05.'),
    ev(id='jar.drive', file='jar/drive', fn=drive, takes=1, target=-31.0, cls='motor loop (small), as played round',
       kind=('loop', DRIVE_L), fade_ms=0, hit_ms=None, ref=BEACON_AMBIENT, finish=loop_finish, loop=True,
       pitch=[0.75, 1.5], subtitle_en='Jar wheels whir', subtitle_pt='Rodas do pote zumbem',
       play='Client loop at the jar while the Wheels are down and the jar moves. Pitch = 0.75 + 0.75 x (horizontal speed / the Wheels top speed), clamped 0.75-1.5 (the whine 900-1800 Hz); volume = 0.35 + 0.65 x that same fraction while moving, chased to 0 when it stops. The block underfoot is the code\'s, separately. Base volume 1.0 = -31 at full.' + LOOP_NOTE),
    ev(id='jar.tip_over', file='jar/tip_over', fn=tip_over, takes=3, target=-27.0, cls='glass knock and slosh',
       kind=('tip', 0.45), fade_ms=40, hit_ms=3, ref=PISTON_IN, pitch=[0.95, 1.05],
       subtitle_en='Jar tips over', subtitle_pt='Pote tomba',
       play='Server, at the jar, on the tick the bare jar is tipped on its side (replaces glass.hit at pitch 0.7). The knock is at 3 ms, the slosh from 13-20 ms. Random pitch 0.95-1.05.'),
    ev(id='jar.sit_up', file='jar/sit_up', fn=sit_up, takes=3, target=-27.0, cls='slosh and glass clink',
       kind=('sit', 0.40), fade_ms=40, hit_ms=250, ref=PISTON_IN, pitch=[0.95, 1.05],
       subtitle_en='Jar rights itself', subtitle_pt='Pote se endireita',
       play='Server, at the jar, on the tick the jar is set upright (replaces glass.hit at pitch 1.1). The slosh comes first; the base lands with its clink at 250 ms (5 ticks): if the client animates the jar righting itself, land it 5 ticks after this tick. Random pitch 0.95-1.05.'),
    ev(id='jar.roll', file='jar/roll', fn=roll, takes=1, target=-31.0, cls='rumble and glunk loop, as played round',
       kind=('loop', ROLL_L), fade_ms=0, hit_ms=None, ref=BEACON_AMBIENT, finish=loop_finish, loop=True, attenuation=10,
       pitch=[0.8, 1.2], subtitle_en='Jar rolls', subtitle_pt='Pote rola',
       play='Client loop at the tipped jar while it rolls. At pitch 1.0 the file is one revolution every 1.5 s (two glunks in 3.0 s). Pitch = 0.8 + 0.4 x (speed / the rolling top speed), clamped 0.8-1.2 (a revolution every 1.875 s to 1.25 s); if the code knows the jar\'s real turn rate, pitch = 1.5 x revolutions a second, clamped. Volume = speed fraction, chased; stop it when the jar stops or sits up.' + LOOP_NOTE),
    ev(id='jar.hop', file='jar/hop', fn=hop, takes=3, target=-27.0, cls='servo (ratchet release and puff)',
       kind='latch', fade_ms=20, hit_ms=3, ref=CLICK, pitch=[0.95, 1.05],
       subtitle_en='Hop Springs release', subtitle_pt='Molas de salto disparam',
       play='Server, at the jar, on the tick the Hop Springs fire. The catch is at 3 ms, the pawl 13-25 ms later, the puff from 8 ms. Random pitch 0.95-1.05; jar.hop_land when it comes down.'),
    ev(id='jar.hop_land', file='jar/hop_land', fn=hop_land, takes=3, target=-27.0, cls='glass knock and slosh',
       kind='latch', fade_ms=30, hit_ms=3, ref=PISTON_IN, pitch=[0.95, 1.05],
       subtitle_en='Jar lands', subtitle_pt='Pote aterrissa',
       play='Server, at the jar, on the tick a hopping jar lands (on the ground again after jar.hop). The knock is at 3 ms. Random pitch 0.95-1.05; debounce 4 ticks.'),
    ev(id='jar.propeller', file='jar/propeller', fn=propeller, takes=1, target=-28.0, cls='motor loop (propeller), as played round',
       kind=('loop', PROP_L), fade_ms=0, hit_ms=None, ref=BEACON_AMBIENT, finish=loop_finish, loop=True, attenuation=16,
       pitch=[0.75, 1.3], subtitle_en='Propeller whirs', subtitle_pt='Hélice gira',
       play='Client loop at the jar while the Propeller flies it. Pitch = 0.75 + 0.55 x thrust (0 = idling aloft, 1 = full climb), clamped 0.75-1.3 (blade pass 41-72 Hz); volume 0.6 + 0.4 x thrust while flying, chased to 0 when the propeller stops. Base volume 1.0 = -28 at full.' + LOOP_NOTE),
    ev(id='jar.shock_lid', file='jar/shock_lid', fn=shock_lid, takes=3, target=-24.0, cls='arc crackle over a thump',
       kind='latch', fade_ms=20, hit_ms=3, ref=VAULT_OFF, ref2=BEE_STING, pitch=[0.94, 1.06],
       subtitle_en='Shock Lid zaps', subtitle_pt='Tampa de choque dá choque',
       play='Server, at the jar, when the Shock Lid zaps an attacker (replaces the bee sting at pitch 0.5 in LifeSupport). The snap is at 3 ms. Random pitch 0.94-1.06.'),
    ev(id='module.arc_cannon', file='module/arc_cannon', fn=arc_cannon, takes=3, target=-20.0,
       cls='arc crack and boom (weapon, heard far)', kind=('boom', 0.50), fade_ms=40, hit_ms=3, ref=FIREWORK_BLAST,
       pitch=[0.95, 1.05], attenuation=32, category='NEUTRAL',
       subtitle_en='Arc Cannon cracks', subtitle_pt='Canhão de arco estala',
       play='Server, at the mecha, on the tick the Arc Cannon fires (replaces LIGHTNING_BOLT_IMPACT at pitch 1.6 in MechaArms line 239). The snap and the boom start at 3 ms; it rings off by about 0.5 s. Random pitch 0.95-1.05. Attenuation 32 (heard far off); register fixed-range 32 if the server should send it that far.'),
    ev(id='module.tesla_coil', file='module/tesla_coil', fn=tesla_coil, takes=3, target=-24.0,
       cls='buzzing arc and snap', kind=('arc', 0.36), fade_ms=20, hit_ms=3, ref=VAULT_OFF, pitch=[0.94, 1.06],
       attenuation=16, category='NEUTRAL', subtitle_en='Tesla Coil zaps', subtitle_pt='Bobina de Tesla dá choque',
       play='Server, at the mecha, on each Tesla Coil zap (replaces BEE_LOOP_AGGRESSIVE played as a one-shot in MechaSystems line 202). The buzz starts at 4 ms, the snap ends it at 320-360 ms. Random pitch 0.94-1.06.'),
]

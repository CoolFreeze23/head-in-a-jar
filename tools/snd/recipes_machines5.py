"""The machines, fifth batch: the Leech Jar, the Neural Socket and the Leyden Jar.

The Leech Jar is a gumball machine: a stepped glass globe of dark water with a pale leech on its surface, a brass
nozzle on the globe's outside that pops out to sample the creature standing next to it, and a brass crank on the
pedestal's side that turns a full circle when a sample vial drops into the window (twice when the player empties the
rack). The leech stirs in the water as a creature comes close (a small wet slosh inside the glass) and flops back onto
the water when it lets go; the nozzle pops out on its spring; the crank's ratchet clicks round once; the vial plinks
onto the brass rack; emptied, the vials clatter out. The bite itself is the server's (vanilla hurt and honey-drink
sounds): there is no bite here.

The Neural Socket is an ivory pillar with a dark steel cap on which a player's head-in-a-jar stands: the clamps' servo
whirrs as the four brass corner clamps climb 6 px up the pillar's corners, they bite the jar's base together, the
brass jack rises 1 px and seats into the jar's base with a click and the start of a hum; on release the pneumatics
sigh and the clamps drop back onto their stops.

The Leyden Jar is a glass battery jar in a steel lantern cage with a brass knob on top: when it reaches full charge
one spark arcs from the knob to the handle, a small dry snap and a short crackle.

Times inside a file are seconds; one tick is 50 ms. Every file has its hit at 3 ms: the keyframed ones because the
client plays them at the clip's key, the Leyden Jar's because the server plays it on the tick the jar is full (no
pre-roll). Takes differ by construction (a per-take table of frequencies, decays and timings), not by luck of the seed.
"""
import numpy as np
from nsynth import *
from recipes_machines import small_tick
from recipes_machines2 import env_seg, release, smooth, knock, brass_ring

T = TICK


# ---------------------------------------------------------------- shared pieces

def squelch(r, lo, hi, tau, gate_hz=55.0, attack=0.002):
    """A wet squelch: band noise with an exponential decay, chopped at gate_hz like a body pulling off a wet surface."""
    d = 7 * tau + 0.01
    t = tt(d)
    return norm(bandpass(noise(d, r), lo, hi) * exp_env(d, tau, attack) * (0.6 + 0.4 * np.cos(2 * np.pi * gate_hz * t)))


def ease_in_out_cubic(u):
    u = np.clip(u, 0, 1)
    return np.where(u < 0.5, 4 * u ** 3, 1 - (-2 * u + 2) ** 3 / 2)


def vial(r, f, d=1.0, brass=1250.0, brass_gain=0.40):
    """A small glass sample vial striking brass wire: a strike, the vial's glass modes about f (upper partials kept low,
    a thin vial, not a goblet) and the brass wire's short ring."""
    return norm(mix(0.35 * strike(r, 3000),
                    0.60 * norm(glass_modes(r, f, decays=(0.040 * d, 0.024 * d, 0.014 * d, 0.008 * d), amps=(1, 0.30, 0.12, 0.05))),
                    brass_gain * brass_ring(r, brass, 0.6 * d)))


# ======================================================================= the Leech Jar

STIR = [dict(m=1.00, slosh=4.5, tau=0.14, bub=((0.060, 820.0, 0.016), (0.190, 1040.0, 0.013))),
        dict(m=1.10, slosh=5.5, tau=0.11, bub=((0.045, 900.0, 0.015), (0.140, 1150.0, 0.012), (0.260, 980.0, 0.012))),
        dict(m=0.92, slosh=3.8, tau=0.15, bub=((0.080, 760.0, 0.018), (0.240, 940.0, 0.014)))]


def leech_stir(take, r):
    """The leech stirring in the water as a creature comes close (wake key 1, the swim toward the glass begins): its
    pale body turns over at 3 ms (a small squelch, 500-2800 Hz band noise chopped at 45 Hz, 30 ms, over a soft low bump
    220 -> 140 Hz), the dark water sloshes against the curved glass (350-1800 Hz noise rocking at 3.8-5.5 Hz through a
    resonance that rises 650 -> 1000 Hz, dying with a 0.11-0.15 s time constant), two or three small bubbles rise, and
    the globe answers faintly (a short glass mode about 1.1 kHz). Low-passed at 3.5 kHz: it is all inside the glass."""
    p = STIR[take - 1]
    m = p['m']
    dur = 1.00
    b = buf(dur)
    t = tt(dur)
    place(b, squelch(r, 500 * m, 2800 * m, 0.030, 45.0), 0.003, 0.75)
    place(b, sweep_sine(220 * m, 140 * m, 0.040, 0.015), 0.003, 0.30)
    e = (t > 0.010) * np.clip((t - 0.010) / 0.025, 0, 1) * np.exp(-np.maximum(t - 0.035, 0) / p['tau'])
    rock = 0.55 + 0.45 * np.cos(2 * np.pi * p['slosh'] * t)
    n = bandpass(noise(dur, r), 350 * m, 1800 * m) * e * rock
    fr = (650 + 350 * np.clip(t / 0.4, 0, 1)) * m
    b += 0.20 * norm(n) + 0.45 * norm(sweep_bandpass(n, fr * 0.8, fr * 1.25))
    for at, f, tau in p['bub']:
        place(b, bubble(r, f * m, tau, rise=0.18), at, 0.30)
    place(b, norm(glass_modes(r, 1100 * m, decays=(0.030, 0.018, 0.010, 0.006), amps=(1, 0.3, 0.1, 0.05))), 0.004, 0.08)
    return lowpass(b, 3500)


FLOP = [dict(m=1.00, spray=0.25, bub=(0.022, 0.075), ripple=6.5), dict(m=1.12, spray=0.18, bub=(0.018, 0.060), ripple=7.5),
        dict(m=0.88, spray=0.32, bub=(0.028, 0.095), ripple=5.5)]


def leech_flop(take, r):
    """The leech letting go of the glass and flopping back onto the water (settle key 10, the drift's end): its soft body
    slaps the surface at 3 ms (a wet slap: a low bump 300 -> 150 Hz under a 500-3500 Hz squelch chopped at 60 Hz, 18
    ms), a little spray (1.2-5 kHz, 35 ms), one bubble on the slap and one 60-95 ms later, and the ripple lapping at the
    glass (300-1200 Hz rocking at 5.5-7.5 Hz, 60 ms decay). Low-passed at 4 kHz: soft, behind glass."""
    p = FLOP[take - 1]
    m = p['m']
    dur = 0.60
    b = buf(dur)
    t = tt(dur)
    place(b, sweep_sine(300 * m, 150 * m, 0.030, 0.010), 0.003, 0.45)
    place(b, squelch(r, 500 * m, 3500 * m, 0.018, 60.0), 0.003, 0.70)
    place(b, norm(noise_burst(1200 * m, 5000 * m, 0.035, r, attack=0.003)), 0.008, p['spray'])
    place(b, bubble(r, 900 * m, 0.015, rise=0.3), p['bub'][0], 0.28)
    place(b, bubble(r, 1150 * m, 0.012, rise=0.25), p['bub'][1], 0.18)
    e = (t > 0.030) * np.clip((t - 0.030) / 0.015, 0, 1) * np.exp(-np.maximum(t - 0.045, 0) / 0.060)
    b += 0.25 * norm(bandpass(noise(dur, r), 300 * m, 1200 * m) * e * (0.5 + 0.5 * np.cos(2 * np.pi * p['ripple'] * t)))
    return lowpass(b, 4000)


NOZZLE = [dict(m=1.00, d=1.00, back=0.050), dict(m=1.10, d=0.80, back=0.046), dict(m=0.90, d=1.25, back=0.055)]


def leech_nozzle_pop(take, r):
    """The brass nozzle cup popping out 2 px on its spring toward the creature (sample key 5, the end of the easeOutBack
    push): the spring catch lets go at 3 ms (a ratchet-like tooth about 2.8 kHz), the brass cup's shell rings briefly
    (free-bar modes about 1.5 kHz), the air in the cup gives a small hollow pop (a sine falling 1.1 kHz -> 700 Hz in a
    few ms, with a 600-3000 Hz puff), the spring twangs faintly (about 620 Hz, stretched partials, 50 ms) and the
    overshoot settles back 46-55 ms later (a small tick)."""
    p = NOZZLE[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.50)
    place(b, tooth(r, 2800 * m), 0.003, 0.45)
    place(b, brass_ring(r, 1500 * m, 0.9 * d), 0.003, 0.55)
    place(b, sweep_sine(1100 * m, 700 * m, 0.012 * d, 0.004), 0.003, 0.45)
    place(b, norm(noise_burst(600 * m, 3000 * m, 0.004, r)), 0.003, 0.30)
    place(b, norm(modal(620 * m, [1, 2.06, 3.17], [1, 0.4, 0.2], [0.050 * d, 0.030 * d, 0.020 * d], r, 0.02)), 0.004, 0.18)
    place(b, small_tick(r, 2100 * m, 0.004), 0.003 + p['back'], 0.20)
    return b


CRANK = [dict(m=1.00, teeth=12, body=1700.0), dict(m=1.08, teeth=10, body=1900.0), dict(m=1.28, teeth=14, body=1500.0)]
TURN = 0.35   # s: the crank's turn, ticks 11-18 of clip sample (easeInOutCubic through 372 degrees)
SETTLE = 0.40  # s: back to 360 at tick 19


def leech_crank_ratchet(take, r):
    """The brass crank turning one full circle on its ratchet (sample key 11; empty keys 1 and 11): the pawl lifts at
    3 ms as the turn starts, then clicks over each tooth of the ratchet wheel (10-14 a turn) at the times the
    easeInOutCubic angle (0 -> 372 degrees over 0.35 s) crosses each tooth, so the clicks bunch in the middle of the turn
    and spread at its ends, louder where the crank runs fastest; each click a tooth (2.6-3.3 kHz) on the wheel's small
    brass body (1.7-2.05 kHz) with a little gear grit following the speed; then at 0.40 s (tick 19) the crank settles back
    to 360 and the pawl seats in its tooth with a firmer click."""
    p = CRANK[take - 1]
    m, N = p['m'], p['teeth']
    dur = 0.75
    b = buf(dur)
    t = tt(dur)
    ug = np.linspace(0, 1, 4001)
    ang = 372 * ease_in_out_cubic(ug)
    sp = np.gradient(ang, ug) / (372 * 1.5)  # easeInOutCubic peaks at 1.5x the mean speed: 0..1
    grit = np.interp(t / TURN, ug, sp) * (t < TURN)
    b += 0.10 * norm(bandpass(noise(dur, r), 2000 * m, 6000 * m) * smooth(grit, 5))

    def click(f, body, g):
        place_at = mix(tooth(r, f), 0.45 * norm(modal(body, [1, 2.756], [1, 0.4], [0.008, 0.005], r, 0.02)))
        return g * norm(place_at)
    place(b, click(2600 * m, p['body'] * m, 1.0), 0.003, 0.45)
    for k in range(1, N + 1):
        a = k * 360.0 / N
        uk = float(np.interp(a, ang, ug))
        tk = uk * TURN
        s = float(np.interp(uk, ug, sp))
        place(b, click(2600 * m * r.uniform(0.97, 1.03), p['body'] * m * r.uniform(0.98, 1.02), 1.0), tk, 0.35 + 0.55 * s)
    seat = mix(tooth(r, 2300 * m), 0.6 * knock(r, 1300 * m, 0.7, th=(260, 180), th_gain=0.2, nz=0.25, band=(800, 5000), hp=2000))
    place(b, norm(seat), SETTLE, 0.80)
    return b


PLINK = [dict(m=1.00, d=1.00, f=2300.0, bounce=0.034), dict(m=1.09, d=0.80, f=2550.0, bounce=0.028),
         dict(m=0.92, d=1.20, f=2100.0, bounce=0.041)]


def leech_plink(take, r):
    """A sample vial dropping into the brass rack in the window (sample key 19): the vial strikes the rack's wire at 3 ms
    (a strike, the vial's thin glass modes about 2.1-2.55 kHz, upper partials kept low, and the brass wire's short ring
    about 1.25 kHz), hops once 28-41 ms later, softer and a hair higher, and rolls against its neighbour (a tiny tick
    about 100 ms on). High-passed at 400 Hz: a small vial, no body."""
    p = PLINK[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.60)
    place(b, vial(r, p['f'], d, 1250 * m), 0.003, 1.0)
    place(b, vial(r, p['f'] * 1.012, 0.6 * d, 1260 * m, 0.30), 0.003 + p['bounce'], 0.30)
    place(b, small_tick(r, 3000 * m, 0.003), 0.100, 0.08)
    return highpass(b, 400)


CLATTER = [dict(m=1.00, d=1.00, hits=((0.003, 2300, 1.00), (0.031, 2700, 0.80), (0.058, 2100, 0.85), (0.092, 2500, 0.65),
                                      (0.124, 2900, 0.55), (0.170, 2200, 0.45), (0.215, 2600, 0.30), (0.268, 2400, 0.18))),
           dict(m=1.08, d=0.85, hits=((0.003, 2500, 1.00), (0.022, 2200, 0.75), (0.047, 2800, 0.80), (0.075, 2400, 0.60),
                                      (0.118, 2650, 0.45), (0.160, 2350, 0.32), (0.205, 2550, 0.18), (0.258, 2450, 0.12))),
           dict(m=0.92, d=1.20, hits=((0.003, 2200, 1.00), (0.040, 2500, 0.85), (0.071, 2050, 0.75), (0.108, 2700, 0.65),
                                      (0.140, 2300, 0.55), (0.186, 2600, 0.42), (0.240, 2150, 0.30), (0.300, 2450, 0.20),
                                      (0.352, 2300, 0.10)))]


def leech_vials_clatter(take, r):
    """The vials tumbling out of the rack when the player empties it (empty key 21): seven to nine glass vials knocking
    against each other and the brass rack in an uneven run, thick at the start and thinning out over 0.2-0.35 s (each
    hit a vial strike about 2-2.9 kHz with the brass wire's ring, half of them doubled 4-9 ms later where two vials touch),
    over a faint rattle of the rack's wire (1-4 kHz noise, 0.15 s). High-passed at 400 Hz, low-passed at 7 kHz."""
    p = CLATTER[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.80)
    t = tt(0.80)
    for k, (at, f, g) in enumerate(p['hits']):
        place(b, vial(r, f * m, d, 1250 * m * r.uniform(0.95, 1.05), 0.45), at, g)
        if k % 2 == 1:
            place(b, vial(r, f * m * 1.07, 0.5 * d, 1300 * m, 0.2), at + r.uniform(0.004, 0.009), 0.35 * g)
    e = (t > 0.003) * np.clip((t - 0.003) / 0.01, 0, 1) * np.exp(-np.maximum(t - 0.013, 0) / 0.05)
    b += 0.08 * norm(bandpass(noise(0.80, r), 1000 * m, 4000 * m) * e)
    return lowpass(highpass(b, 400), 7000)


# ======================================================================= the Neural Socket

SERVO = [dict(m=1.00, grit=0.40, air=0.16), dict(m=1.08, grit=0.50, air=0.20), dict(m=0.85, grit=0.30, air=0.10)]
CLIMB = (0.05, 0.35)  # s after clamp key 1: the clamps climb over ticks 2-8 and bite at 8 (socket.clamp)


def socket_servo(take, r):
    """The clamps' servo starting their climb (clamp key 1): the drive's relay pulls in at 3 ms, the house motor (whine,
    commutator grit, air) spins up over 60 ms from 50 ms (tick 2) and runs while the four brass clamps slide 6 px up the
    pillar's ivory corners (a faint 1.5-6 kHz slide under it), and stops dead at 350 ms (tick 8) as the clamps bite (that
    bite is socket.clamp), running down in about 20 ms."""
    p = SERVO[take - 1]
    m = p['m']
    dur = 0.90
    b = buf(dur)
    t = tt(dur)
    s = np.clip((t - CLIMB[0]) / 0.06, 0, 1) * (1 - 0.15 * np.clip((t - 0.11) / 0.24, 0, 1))
    s = release(smooth(s, 8), t, CLIMB[1], 0.018)
    b += motor(t, s, r, 170 * m, 520 * m, grit=p['grit'], air=p['air'], whine=0.30)
    b += 0.12 * norm(bandpass(noise(dur, r), 1500 * m, 6000 * m) * s ** 1.5)
    place(b, relay_click(r, 1700 * m, release_after=0.026), 0.003, 0.45)
    return b


CLAMP = [dict(m=1.00, d=1.00, stag=(0.000, 0.002, 0.004, 0.007), lock=0.024),
         dict(m=1.08, d=0.80, stag=(0.000, 0.001, 0.003, 0.005), lock=0.020),
         dict(m=0.92, d=1.25, stag=(0.000, 0.003, 0.006, 0.010), lock=0.030)]


def socket_clamp(take, r):
    """The four brass clamps biting the jar's base together (clamp key 8): four hits within 5-10 ms of each other at
    3 ms (each a strike, a brass body in free-bar modes about 1.25-1.45 kHz and a short metallic noise band), the dark
    steel cap under them answering (an iron body about 560 Hz with its plate and a thunk 160 -> 100 Hz), the jar's glass
    base ringing faintly where it is gripped (about 1.7 kHz, short), and the clamps' pawls locking 20-30 ms later (a
    tooth)."""
    p = CLAMP[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.60)
    for k, st in enumerate(p['stag']):
        hit = mix(0.40 * strike(r, 1500 * m),
                  0.60 * norm(modal(1250 * m * (1 + 0.05 * k), [1, 2.756, 5.404], [1, 0.5, 0.25], [0.025 * d, 0.015 * d, 0.008 * d], r, 0.01)),
                  0.25 * norm(noise_burst(1000 * m, 6000 * m, 0.006, r)))
        place(b, norm(hit), 0.003 + st, [1.0, 0.85, 0.9, 0.75][k])
    place(b, norm(iron_body(r, 560 * m, 900 * m, 0.8 * d, noise_gain=0.20)), 0.003, 1.10)
    place(b, thunk(r, 160 * m, 100 * m, 0.040, 0.015), 0.003, 0.40)
    place(b, norm(glass_modes(r, 1700 * m, decays=(0.030 * d, 0.020 * d, 0.012 * d, 0.008 * d), amps=(1, 0.3, 0.12, 0.05))), 0.005, 0.15)
    place(b, tooth(r, 2400 * m), 0.003 + p['lock'], 0.30)
    return b


PLUG = [dict(m=1.00, hum=120.0, peak=0.040, tau=0.050), dict(m=1.08, hum=100.0, peak=0.035, tau=0.065),
        dict(m=0.93, hum=140.0, peak=0.045, tau=0.040)]


def socket_plug(take, r):
    """The brass jack seating into the jar's base (clamp key 12): the plug's click at 3 ms (a small dull knock about
    1.9 kHz with the jack's brass ring about 2.3 kHz), the socket's contact springs wiping it (a 3-8 kHz flick, 4 ms),
    and a tiny hum as the circuit makes (a 100-140 Hz buzz, harmonics 1/k^0.9 from 250 Hz to 3 kHz, well under the click, swelling in to 35-45 ms and
    dying with a 40-65 ms time constant). Low-passed at 6 kHz."""
    p = PLUG[take - 1]
    m = p['m']
    dur = 0.60
    b = buf(dur)
    t = tt(dur)
    place(b, knock(r, 1900 * m, 0.6, th=(300, 220), th_gain=0.15, nz=0.25, band=(1500, 6000), hp=2500), 0.003, 0.80)
    place(b, brass_ring(r, 2300 * m, 0.4), 0.003, 0.30)
    place(b, norm(noise_burst(3000, 8000, 0.0015, r)), 0.004, 0.15)
    e = (t > 0.006) * np.clip((t - 0.006) / (p['peak'] - 0.006), 0, 1) ** 2 * np.exp(-np.maximum(t - p['peak'], 0) / p['tau'])
    hum = bandpass(osc_harm(np.full(len(t), p['hum']), 25, r, 0.9), 250, 3000)
    b += 0.13 * norm(hum * e)
    return lowpass(b, 6000)


SIGH = [dict(tau=0.15, lo=4000.0, hi=15000.0), dict(tau=0.13, lo=4500.0, hi=16000.0), dict(tau=0.19, lo=3600.0, hi=14000.0)]


def socket_sigh(take, r):
    """The clamps' pneumatics letting go (release key 0): the valve's dry tsk at 3 ms (a small tick about 3.2 kHz and a
    soft 'thup' of the piston, a thunk 200 -> 140 Hz), then the air let out: a hiss up in 6 ms, held 40 ms, decaying
    with a 0.13-0.19 s time constant, its band falling as the pressure goes (3.6-16 kHz down to about 2.2-10 kHz), with a
    faint lower breath (1.5-4 kHz) under it."""
    p = SIGH[take - 1]
    dur = 1.30
    b = buf(dur)
    t = tt(dur)
    place(b, small_tick(r, 3200, 0.003), 0.003, 0.30)
    place(b, thunk(r, 200, 140, 0.015, 0.006), 0.003, 0.15)
    t0 = np.maximum(t - 0.005, 0)
    pr = (t >= 0.005) * np.clip(t0 / 0.006, 0, 1) * np.where(t0 < 0.04, 1.0, np.exp(-(t0 - 0.04) / p['tau']))
    b += 0.90 * norm(sweep_bandpass(noise(dur, r), p['lo'] * (0.6 + 0.4 * pr), p['hi'] * (0.6 + 0.4 * pr)) * pr)
    b += 0.10 * norm(bandpass(noise(dur, r), 1500, 4000) * pr)
    return b


DROP = [dict(m=1.00, d=1.00, stag=(0.000, 0.006, 0.011, 0.017), bounce=0.062),
        dict(m=1.09, d=0.95, stag=(0.000, 0.004, 0.009, 0.013), bounce=0.095),
        dict(m=0.91, d=1.20, stag=(0.000, 0.008, 0.015, 0.024), bounce=0.068)]


def socket_drop(take, r):
    """The four clamps landing back on their stops at the foot of the pillar (release key 8; they slide 6 px down and
    land on 8): four light knocks 13-24 ms apart from 3 ms (each a strike, a damped brass body about 1-1.15 kHz, a light
    thunk and a short noise band, with the clamp's brass ring about 1.7 kHz), two of them bouncing once 62-95 ms later,
    softer, and the steel cap answering faintly (about 600 Hz). Lighter and looser than socket.clamp: no pawls, no glass."""
    p = DROP[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.50)
    for k, st in enumerate(p['stag']):
        hit = mix(knock(r, 1000 * m * (1 + 0.04 * k), 0.8 * d, th=(220, 150), th_gain=0.30, nz=0.25, band=(800, 5000), hp=1800),
                  0.50 * brass_ring(r, 1700 * m * (1 + 0.03 * k), 1.0 * d))
        place(b, norm(hit), 0.003 + st, [1.0, 0.8, 0.9, 0.7][k])
    for k, st in enumerate(p['stag'][:2]):
        place(b, knock(r, 1050 * m * (1 + 0.04 * k), 0.6 * d, th_gain=0.15, nz=0.2, band=(800, 5000), hp=2000), 0.003 + p['bounce'] + st, 0.35 - 0.08 * k)
    place(b, norm(modal(600 * m, [1, 2.3, 3.6], [1, 0.4, 0.2], [0.020 * d, 0.012 * d, 0.007 * d], r, 0.02)), 0.004, 0.12)
    return b


# ======================================================================= the Leyden Jar

FULL = [dict(m=1.00, L=0.19, lo=2500.0, hi=12000.0), dict(m=1.10, L=0.25, lo=3000.0, hi=13000.0),
        dict(m=0.90, L=0.17, lo=2200.0, hi=11000.0)]


def leyden_full(take, r):
    """The jar at full charge, one spark arcing from the brass knob to the handle (the server, on the tick the jar is
    full): a dry snap at 3 ms (a strike and a 1.5-12 kHz crack, 4 ms), a short arc crackle (2.2-13 kHz bursts 8-30 ms
    apart for 0.17-0.25 s, thinning), a 40 ms whisper of buzz (100 Hz-gated noise) and the knob's tiny brass ping (about
    3.2 kHz)."""
    p = FULL[take - 1]
    m, L = p['m'], p['L']
    b = buf(L + 0.40)
    place(b, strike(r, 3000 * m, 0.0008), 0.003, 1.0)
    place(b, norm(noise_burst(1500 * m, 12000, 0.004, r)), 0.003, 0.70)
    cr = crackle(r, L, p['lo'], p['hi'], spacing=(0.008, 0.030), width=(0.002, 0.006), first_gain=0.6)
    cr *= np.clip(1 - tt(len(cr) / SR) / (L + 0.05), 0, 1) ** 1.2
    place(b, norm(cr), 0.006, 0.45)
    tb = tt(0.04)
    bz = bandpass(noise(0.04, r), 1500, 6000) * (0.5 + 0.5 * np.cos(2 * np.pi * 100 * tb)) ** 3 * np.sin(np.pi * tb / 0.04)
    place(b, norm(np.concatenate([bz, np.zeros(int(0.02 * SR))])), 0.005, 0.20)
    place(b, brass_ring(r, 3200 * m, 0.5), 0.004, 0.12)
    return b


# ---------------------------------------------------------------- references (measured as the game plays them)

PISTON_IN = dict(event='block.piston.contract', file='tile__piston__in.ogg', vol=0.5, pitch=0.675)
SPYGLASS = dict(event='item.spyglass.use', file='item__spyglass__use.ogg', vol=1.0, pitch=1.0)
FIZZ = dict(event='block.fire.extinguish', file='random__fizz.ogg', vol=0.5, pitch=2.0)
IRON_TD_OPEN = dict(event='block.iron_trapdoor.open', file='block__iron_trapdoor__open1.ogg', vol=0.9, pitch=0.95)
IRON_TD_CLOSE = dict(event='block.iron_trapdoor.close', file='block__iron_trapdoor__close1.ogg', vol=0.9, pitch=0.95)
COPPER_TD_CLOSE = dict(event='block.copper_trapdoor.close', file='block__copper_trapdoor__toggle1.ogg', vol=1.0, pitch=0.95)
WOOD_TD_OPEN = dict(event='block.wooden_trapdoor.open', file='block__wooden_trapdoor__open1.ogg', vol=0.9, pitch=0.95)
LANTERN = dict(event='block.lantern.place', file='block__lantern__place1.ogg', vol=1.0, pitch=0.88)
HONEY_PLACE = dict(event='block.honey_block.place', file='block__honeyblock__step1.ogg', vol=1.0, pitch=0.8)

BASE = dict(category='BLOCKS', attenuation=8, fixed_range=False, volume=1.0, ticks=None, muffled=False)
KEY_NOTE = ' Keyframe volume 1.0 (the file carries the loudness); the key\'s pitch is kept.'


def ev(**kw):
    d = dict(BASE)
    d.update(kw)
    return d


SOUNDS = [
    # ---- the Leech Jar
    ev(id='leech.stir', file='leech/stir', fn=leech_stir, takes=3, target=-27.0, cls='glug class, softer (a slosh in the globe)',
       kind='glug', fade_ms=40, hit_ms=3, ref=HONEY_PLACE, pitch=[0.95, 1.05],
       subtitle_en='Leech stirs', subtitle_pt='Sanguessuga se mexe',
       clip_key='wake key 1: leech.stir|0.3|1.0 -> leech.stir|1.0|1.0',
       play='Leech Jar clip wake, key 1 (a creature has come close; the leech starts its swim to the glass): the squelch at 3 ms, the slosh dying over about half a second, two or three bubbles.' + KEY_NOTE),
    ev(id='leech.flop', file='leech/flop', fn=leech_flop, takes=3, target=-27.0, cls='wet event (soft flop on the water)',
       kind='latch', fade_ms=15, hit_ms=3, ref=HONEY_PLACE, pitch=[0.95, 1.05],
       subtitle_en='Leech flops back', subtitle_pt='Sanguessuga volta à água',
       clip_key='settle key 10: leech.flop|0.3|1.0 -> leech.flop|1.0|1.0',
       play='Leech Jar clip settle, key 10 (the leech back in the middle of the water): the slap at 3 ms, the spray and a bubble, the ripple lapping the glass for about 0.2 s.' + KEY_NOTE),
    ev(id='leech.nozzle_pop', file='leech/nozzle_pop', fn=leech_nozzle_pop, takes=3, target=-28.0, cls='latch (small sprung pop)',
       kind='latch', fade_ms=12, hit_ms=3, ref=IRON_TD_OPEN, pitch=[0.95, 1.05],
       subtitle_en='Sampling nozzle pops out', subtitle_pt='Bico de coleta salta',
       clip_key='sample key 5: leech.nozzle_pop|0.4|1.0 -> leech.nozzle_pop|1.0|1.0',
       play='Leech Jar clip sample, key 5 (the nozzle cup at the end of its 2 px easeOutBack pop): the catch, the cup\'s ring and the hollow pop at 3 ms, the overshoot\'s tick 46-55 ms on. The bite on 6 is the server\'s vanilla hurt and honey-drink sounds, not this.' + KEY_NOTE),
    ev(id='leech.crank_ratchet', file='leech/crank_ratchet', fn=leech_crank_ratchet, takes=3, target=-30.0, cls='tick-class run (ratchet crank, one turn)',
       kind=('servo', 0.40), fade_ms=15, hit_ms=3, ref=SPYGLASS, pitch=[0.97, 1.03],
       subtitle_en='Leech Jar crank turns', subtitle_pt='Manivela gira estalando',
       clip_key='sample key 11; empty keys 1 and 11: leech.crank_ratchet|0.5|1.0 -> leech.crank_ratchet|1.0|1.0',
       play='Leech Jar clips sample (key 11: one turn over ticks 11-18, back to 360 on 19) and empty (keys 1 and 11: two turns over ticks 2-21, one file per turn): the pawl lifts at 3 ms, 10-14 clicks over 0.35 s bunched in the middle of the turn, the pawl seats at 0.40 s. A random take each time, so the two turns of empty differ.' + KEY_NOTE),
    ev(id='leech.plink', file='leech/plink', fn=leech_plink, takes=3, target=-26.5, cls='glass clink',
       kind='latch', fade_ms=15, hit_ms=3, ref=LANTERN, pitch=[0.95, 1.05],
       subtitle_en='Sample vial drops', subtitle_pt='Frasco de amostra cai',
       clip_key='sample key 19: leech.plink|0.6|1.0 -> leech.plink|1.0|1.0',
       play='Leech Jar clip sample, key 19 (the vial lands in the rack as the blockstate `samples` changes): the clink at 3 ms, one hop 28-41 ms on, a roll tick at 100 ms.' + KEY_NOTE),
    ev(id='leech.vials_clatter', file='leech/vials_clatter', fn=leech_vials_clatter, takes=3, target=-26.0, cls='glass clink class (a short clatter)',
       kind=('servo', 0.35), fade_ms=20, hit_ms=3, ref=LANTERN, pitch=[0.95, 1.05],
       subtitle_en='Vials clatter out', subtitle_pt='Frascos caem tilintando',
       clip_key='empty key 21: leech.vials_clatter|0.5|1.0 -> leech.vials_clatter|1.0|1.0',
       play='Leech Jar clip empty, key 21 (the end of the second turn; the rack clears on the server\'s tick): the first vial at 3 ms, seven to nine knocks thinning out over 0.2-0.35 s.' + KEY_NOTE),
    # ---- the Neural Socket
    ev(id='socket.servo', file='socket/servo', fn=socket_servo, takes=3, target=-27.0, cls='servo (clamps climbing)',
       kind=('servo', 0.35), fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[0.97, 1.03], subtitle_en=None, subtitle_pt=None,
       clip_key='clamp key 1: socket.servo|0.4|1.0 -> socket.servo|1.0|1.0',
       play='Neural Socket clip clamp, key 1: the relay at 3 ms, the motor from 50 ms (tick 2) while the clamps climb, stopping dead at 350 ms (tick 8) where socket.clamp bites. No subtitle (socket.clamp carries it).' + KEY_NOTE),
    ev(id='socket.clamp', file='socket/clamp', fn=socket_clamp, takes=3, target=-22.0, cls='clamp',
       kind='latch', fade_ms=12, hit_ms=3, ref=IRON_TD_CLOSE, pitch=[0.95, 1.05], attenuation=12,
       subtitle_en='Socket clamps jar', subtitle_pt='Soquete prende o pote',
       clip_key='clamp key 8: socket.clamp|0.6|1.0 -> socket.clamp|1.0|1.0',
       play='Neural Socket clip clamp, key 8 (the four clamps at the top of their 6 px climb, gripping the jar\'s base; the poof particles on 9): four bites within 5-10 ms from 3 ms, the steel cap\'s body, the pawls locking 20-30 ms on.' + KEY_NOTE),
    ev(id='socket.plug', file='socket/plug', fn=socket_plug, takes=3, target=-30.0, cls='tick (plug-in click with a hum onset)',
       kind='latch', fade_ms=15, hit_ms=3, ref=WOOD_TD_OPEN, pitch=[0.97, 1.03],
       subtitle_en='Jar plugged in', subtitle_pt='Pote conectado',
       clip_key='clamp key 12: socket.plug|0.5|1.0 -> socket.plug|1.0|1.0',
       play='Neural Socket clip clamp, key 12 (the jack risen 1 px into the jar\'s base): the click at 3 ms, the hum swelling to 35-45 ms and gone by about 0.25 s.' + KEY_NOTE),
    ev(id='socket.sigh', file='socket/sigh', fn=socket_sigh, takes=3, target=-24.0, cls='hiss (clamp release)',
       kind='hiss', fade_ms=30, hit_ms=3, ref=FIZZ, pitch=[0.95, 1.05],
       subtitle_en='Socket clamps release', subtitle_pt='Soquete solta as garras',
       clip_key='release key 0: socket.sigh|0.5|1.0 -> socket.sigh|1.0|1.0',
       play='Neural Socket clip release, key 0: the valve\'s tsk at 3 ms, the hiss held 40 ms and dying over about 0.6-0.8 s, before the clamps land at key 8 (socket.drop).' + KEY_NOTE),
    ev(id='socket.drop', file='socket/drop', fn=socket_drop, takes=3, target=-26.5, cls='latch (clamps onto their stops, lighter than the clamp)',
       kind='latch', fade_ms=12, hit_ms=3, ref=COPPER_TD_CLOSE, pitch=[0.95, 1.05], subtitle_en=None, subtitle_pt=None,
       clip_key='release key 8: socket.drop|0.5|1.0 -> socket.drop|1.0|1.0',
       play='Neural Socket clip release, key 8 (the clamps back on their stops): four light knocks 13-24 ms apart from 3 ms, two bouncing 62-95 ms on. No subtitle (socket.sigh carries the release).' + KEY_NOTE),
    # ---- the Leyden Jar
    ev(id='leyden.full', file='leyden/full', fn=leyden_full, takes=3, target=-30.5, cls='tick (small electric snap)',
       kind='latch', fade_ms=12, hit_ms=3, ref=SPYGLASS, pitch=[0.94, 1.06], attenuation=12,
       subtitle_en='Leyden jar fully charged', subtitle_pt='Garrafa de Leyden carregada',
       play='Server, at the Leyden Jar, once on the tick the jar reaches full charge (with the one spark from the knob to the handle), volume 1.0, random pitch 0.94-1.06, no pre-roll: the snap at 3 ms, the crackle over 0.17-0.25 s.'),
]

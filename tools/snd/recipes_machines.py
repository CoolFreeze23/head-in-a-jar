"""The machines: the house kit every machine shares (drawer, hatch, collection flag, refusal rattle), the Galvanic
Pile, the Saline Mixer and the Centrifuge.

The kit is the company's furniture: a small enamel-and-steel drawer, a 6x6 px hinged enamel hatch, a yellow
"collect me" flag on a sprung steel arm, and the lead part's rattle under the house refusal. They are small parts, so
they are quieter than the chair's: soft clacks and light taps, attenuation 8.

The Galvanic Pile is a Volta pile in a screw press over a glass pan of meat: a spark crackles across a 2 px gap between
two copper posts, the screw's ratchet clicks as the T-handle turns, the press sighs when the last fuel is spent, a lump
of meat plops into the pan, and now and then the meat twitches with a tiny galvanic buzz.

The Saline Mixer is a soda fountain: brass caps tink down onto three glass canisters as each ingredient is dosed, lift
when the screen opens and settle when it shuts; the tap glugs a bottle full; a paddle stirs the tank (a loop).

The Centrifuge is a chair-o-plane on a lab bench: its only new sound is the rotor's whine (a loop).

Times inside a file are seconds; one tick is 50 ms. Every keyframed file has its hit at 0-5 ms unless `hit_ms` says
otherwise; the plop is played by the server 4 ticks early (`preroll_ticks`) so its hit lands on the contact tick.
Takes differ by construction (a per-take table of frequencies, decays and timings), not by luck of the seed.

Loops are built periodic: every noise layer is filtered circularly (in the frequency domain), every periodic layer
completes a whole number of cycles in the file, and `loop_finish` replaces the one-shot finishing chain (no fades, no
zeroed ends, a circular high-pass), so the last sample runs straight into the first.
"""
import numpy as np
from nsynth import *
from nsynth import m_max

T = TICK


# ---------------------------------------------------------------- shared pieces

def enamel_contact(r, f0, d=1.0, hp=1200.0, body=0.55, th=(180.0, 110.0), noise_band=(900.0, 5000.0), th_gain=0.30, nz=0.22):
    """A small enamelled steel part meeting its frame: a strike, a short plate body (ratios 1, 1.59, 2.14, 3.9), a
    light thunk and a short metallic noise band. d scales the decays."""
    return norm(mix(0.45 * strike(r, hp),
                    body * norm(modal(f0, [1, 1.59, 2.14, 3.9], [1, 0.6, 0.4, 0.25],
                                      [0.030 * d, 0.022 * d, 0.015 * d, 0.010 * d], r, 0.01)),
                    th_gain * thunk(r, th[0], th[1], 0.028, 0.014),
                    nz * norm(noise_burst(noise_band[0], noise_band[1], 0.010, r))))


def small_tick(r, f, decay=0.005, level=1.0):
    """A tiny metal tick: two short modes and a 1.5 ms noise flick."""
    return level * norm(mix(modal(f, [1, 1.6], [1, 0.4], [decay, decay * 0.6], r, 0.03),
                            0.4 * norm(noise_burst(2000, 7000, 0.0015, r))))


# ---------------------------------------------------------------- the kit: drawer

DRAWER_OUT = [dict(m=1.00, roll=0.45, hiss=0.14, rate=55.0, box=0.12),
              dict(m=1.06, roll=0.42, hiss=0.07, rate=72.0, box=0.10),
              dict(m=0.90, roll=0.54, hiss=0.09, rate=44.0, box=0.16)]


def drawer_out(take, r):
    """The kit drawer starting out (it travels 3 px over ticks 0-4): the detent letting go at 3 ms (a dull click: a
    strike and two short modes about 1.25 kHz), then the slide from tick 1 to tick 4 decelerating like easeOutCubic
    (speed (1 - u)^2): steel-runner roller rumble gated at the roller rate, a faint runner hiss, the enamel box's
    hollow resonance. The stop at tick 4 is machine.drawer_stop."""
    p = DRAWER_OUT[take - 1]
    m = p['m']
    dur = 0.50
    b = buf(dur)
    t = tt(dur)
    place(b, norm(mix(0.6 * strike(r, 2000),
                      modal(1250 * m, [1, 2.1, 3.3], [1, 0.45, 0.2], [0.008, 0.005, 0.003], r, 0.02))), 0.003, 0.50)
    start, stop = T, 4 * T
    u = np.clip((t - start) / (stop - start), 0, 1)
    s = np.where((t > start) & (t < stop), np.clip((t - start) / 0.012, 0, 1) * (1 - u) ** 2, 0.0)
    gate = (0.5 + 0.5 * np.cos(2 * np.pi * np.cumsum(p['rate'] * (0.3 + 0.7 * s)) / SR)) ** 2
    b += p['roll'] * norm(bandpass(noise(dur, r), 150 * m, 1400 * m) * gate * s)
    b += p['hiss'] * norm(bandpass(noise(dur, r), 1500 * m, 5000 * m) * s ** 1.5)
    b += p['box'] * norm(bandpass(noise(dur, r), 640 * m, 760 * m) * s)
    return b


DRAWER_STOP = [dict(m=1.00, d=1.00, tick=0.026), dict(m=1.10, d=0.80, tick=0.034), dict(m=0.92, d=1.20, tick=0.020)]


def drawer_stop(take, r):
    """The drawer reaching its stop: a small dull click. A rubber bumper (a low bump falling 220 -> 140 Hz), a
    felt-damped enamel body about 820 Hz, a little low noise, and the brass pull's tiny tick 20-34 ms later."""
    p = DRAWER_STOP[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.30)
    place(b, thunk(r, 220 * m, 140 * m, 0.018, 0.010), 0.003, 0.50)
    place(b, norm(modal(820 * m, [1, 2.3, 3.9], [1, 0.4, 0.2], [0.016 * d, 0.010 * d, 0.006 * d], r, 0.02)), 0.003, 0.50)
    place(b, norm(lowpass(noise(0.04, r), 3500) * exp_env(0.04, 0.004, 0.0005)), 0.003, 0.35)
    place(b, small_tick(r, 2100 * m, 0.004), p['tick'], 0.07)
    return b


DRAWER_SHUT = [dict(m=1.00, d=1.00, bounce=0.100), dict(m=1.10, d=1.25, bounce=0.094),
               dict(m=0.90, d=0.80, bounce=0.106)]


def drawer_shut(take, r):
    """The drawer closing (contact at tick 4 of the 6-tick close, the key): a soft clack of the enamel front on its
    frame at 3 ms (a 1.05 kHz plate body, short; the thunk kept light), the brass pull's tick 20 ms later, and the 0.5 px bounce coming back
    to rest at about 100 ms (tick 6), quieter and a little higher."""
    p = DRAWER_SHUT[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.55)
    place(b, enamel_contact(r, 1050 * m, d, th_gain=0.18, nz=0.32, noise_band=(1200 * m, 6000 * m)), 0.003, 1.0)
    place(b, small_tick(r, 2600 * m, 0.004), 0.023, 0.08)
    place(b, enamel_contact(r, 1100 * m, d * 0.8, hp=1800, th_gain=0.15), 0.003 + p['bounce'], 0.26)
    return b


# ---------------------------------------------------------------- the kit: hatch

HATCH_OPEN = [dict(m=1.00, creak=1600.0, stop=0.200), dict(m=1.12, creak=1850.0, stop=0.212)]


def hatch_open(take, r):
    """The 6x6 px hatch swinging open 100 degrees over 5 ticks: the spring catch letting go at 3 ms (a relay-like
    click and its release 20 ms later), a short dry hinge creak (stick-slip pings) while it swings, a faint air of the
    panel, and the soft tap of the hatch reaching its stay at tick 4 (the +6 % overshoot), with a settle tick 45 ms on."""
    p = HATCH_OPEN[take - 1]
    m = p['m']
    dur = 0.65
    b = buf(dur)
    t = tt(dur)
    place(b, relay_click(r, 2200 * m, release_after=0.020), 0.003, 0.40)
    at = 0.05
    while at < 0.18:
        place(b, norm(modal(p['creak'] * m * r.uniform(0.95, 1.05), [1, 2.3], [1, 0.3], [0.005, 0.003], r)), at,
              0.16 * r.uniform(0.4, 1.0))
        at += r.uniform(0.012, 0.025)
    b += 0.08 * norm(bandpass(noise(dur, r), 600, 3000) * speed_curve(t, 0.05, p['stop']))
    place(b, norm(mix(modal(1100 * m, [1, 1.59, 2.14], [1, 0.5, 0.3], [0.020, 0.015, 0.010], r),
                      0.4 * norm(noise_burst(1000, 5000, 0.008, r)))), p['stop'], 0.34)
    place(b, small_tick(r, 2300 * m, 0.005), p['stop'] + 0.045, 0.10)
    return b


HATCH_SHUT = [dict(m=1.00, d=1.00, bounce=0.100), dict(m=1.10, d=1.25, bounce=0.094), dict(m=0.90, d=0.85, bounce=0.106)]


def hatch_shut(take, r):
    """The hatch shutting (contact at tick 4 of 6, the key): the enamel panel strikes its frame at 3 ms (a 1.15 kHz
    plate body with a metallic band, a light thunk), the magnet catch takes it 15 ms later (one tooth), and the 5 %
    bounce comes back to rest at about 100 ms (tick 6)."""
    p = HATCH_SHUT[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.60)
    place(b, enamel_contact(r, 1150 * m, d * 1.3, hp=1500, noise_band=(1200 * m, 7000 * m), th_gain=0.18, nz=0.32), 0.003, 1.0)
    place(b, tooth(r, 2400 * m), 0.018, 0.18)
    place(b, enamel_contact(r, 1200 * m, d, hp=1800, th_gain=0.15), 0.003 + p['bounce'], 0.22)
    return b


# ---------------------------------------------------------------- the kit: collection flag

FLAG_UP = [dict(m=1.00, tw=820.0, stop=0.120), dict(m=1.10, tw=900.0, stop=0.105), dict(m=0.90, tw=740.0, stop=0.135)]


def flag_up(take, r):
    """The yellow flag springing up (keyed at tick 2, mid-raise): the spring catch flicks at 3 ms (a strike and a
    tooth), the steel leaf spring gives one short damped twang (a mode about 820 Hz, 35 ms: never a boing, no glide),
    a breath of air, and the arm meets its stop as it overshoots vertical (a light plate tap), the plate jiggling
    twice after."""
    p = FLAG_UP[take - 1]
    m = p['m']
    dur = 0.50
    b = buf(dur)
    t = tt(dur)
    place(b, strike(r, 2500), 0.003, 0.30)
    place(b, tooth(r, 2600 * m), 0.003, 0.22)
    place(b, norm(modal(p['tw'] * m, [1, 2.76], [1, 0.3], [0.035, 0.015], r)), 0.003, 0.30)
    b += 0.06 * norm(bandpass(noise(dur, r), 1500, 6000) * speed_curve(t, 0.003, p['stop']))
    place(b, norm(mix(modal(1500 * m, [1, 1.59, 2.14], [1, 0.5, 0.3], [0.015, 0.010, 0.007], r),
                      0.4 * norm(noise_burst(1200, 6000, 0.006, r)))), p['stop'], 0.30)
    place(b, small_tick(r, 2000 * m, 0.004), p['stop'] + 0.040, 0.08)
    place(b, small_tick(r, 2150 * m, 0.004), p['stop'] + 0.070, 0.05)
    return b


FLAG_DOWN = [dict(m=1.00, d=1.00, bounce=0.100), dict(m=1.12, d=0.80, bounce=0.094), dict(m=0.90, d=1.20, bounce=0.106)]


def flag_down(take, r):
    """The flag folding back flat (contact at tick 4, the key): a light tap of the steel arm on the enamel side at
    3 ms (a 1.3 kHz bar body, short), and its 4-degree bounce landing again at about 100 ms (tick 6), softer."""
    p = FLAG_DOWN[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.45)

    def tap(fr, dd):
        return norm(mix(0.40 * strike(r, 2000),
                        0.55 * norm(modal(fr, [1, 2.756, 5.404], [1, 0.5, 0.3], [0.022 * dd, 0.014 * dd, 0.008 * dd], r)),
                        0.25 * thunk(r, 220, 140, 0.020, 0.010),
                        0.25 * norm(noise_burst(1200, 6000, 0.008, r))))
    place(b, tap(1300 * m, d), 0.003, 1.0)
    place(b, tap(1360 * m, d * 0.8), 0.003 + p['bounce'], 0.25)
    return b


# ---------------------------------------------------------------- the kit: refusal rattle

RATTLE = [dict(m=1.00, n=3, gap=0.007), dict(m=1.22, n=2, gap=0.010), dict(m=0.90, n=4, gap=0.006)]


def chatter(r, f, n, gap):
    """One stroke of a loose part chattering in its mount: n tiny ticks gap s apart, each softer."""
    out = buf(0.09)
    for k in range(n):
        tick = mix(modal(f * r.uniform(0.94, 1.06), [1, 1.73, 2.9], [1, 0.5, 0.25], [0.006, 0.004, 0.003], r),
                   0.5 * norm(noise_burst(1500, 6000, 0.0015, r)))
        place(out, norm(tick), k * gap, [1.0, 0.6, 0.4, 0.3][k])
    return out


def rattle(take, r):
    """The lead part's refusal rattle (8 ticks: +a, -a, +a, 0 at 2, 4, 6, 8): a faint tick at 3 ms as it leaves rest,
    then a small metallic chatter at each reversal, 100, 200 and 300 ms (the -a side a little lower), each stroke 2-4
    tiny ticks 6-10 ms apart. Built to sit under feedback.refused: its body is at 1.6-2.2 kHz and noise, above the
    refusal's notes (E5, Eb5) and below its 5 kHz low-pass."""
    p = RATTLE[take - 1]
    f = 1800.0 * p['m']
    b = buf(0.50)
    place(b, small_tick(r, f * 1.1, 0.004), 0.003, 0.15)
    for at, fr, g in [(0.100, 1.00, 1.0), (0.200, 0.93, 0.85), (0.300, 1.00, 0.70)]:
        place(b, chatter(r, f * fr, p['n'], p['gap']), at, g)
    return b


# ---------------------------------------------------------------- the Galvanic Pile

CRACKLE = [dict(d=0.16, lo=3000, hi=14000, buzz=0.05), dict(d=0.21, lo=2500, hi=12000, buzz=0.07),
           dict(d=0.16, lo=3500, hi=15000, buzz=0.04)]


def pile_crackle(take, r):
    """The spark jumping the 2 px gap between the copper posts: a sharp snap at 3 ms, then arc crackle (bursts 2-6 ms
    wide, 8-35 ms apart, 2.5-15 kHz) for 160-210 ms, and a faint 100 Hz-gated buzz under the first 40-70 ms."""
    p = CRACKLE[take - 1]
    b = buf(p['d'] + 0.25)
    place(b, strike(r, 3000, 0.0008), 0.003, 0.9)
    place(b, norm(crackle(r, p['d'], p['lo'], p['hi'], spacing=(0.008, 0.035), width=(0.002, 0.006))), 0.004, 0.7)
    t = tt(p['buzz'])
    bz = bandpass(noise(p['buzz'], r), 1500, 6000) * (0.5 + 0.5 * np.cos(2 * np.pi * 100 * t)) ** 3 * np.sin(np.pi * t / p['buzz'])
    place(b, norm(bz), 0.005, 0.25)
    return b


PILE_RATCHET = [dict(f=2700.0, sec=0.024, body=1700.0), dict(f=3000.0, sec=0.032, body=1900.0),
                dict(f=2400.0, sec=0.018, body=1500.0), dict(f=3300.0, sec=0.040, body=2100.0)]


def pile_ratchet(take, r):
    """One click of the screw's ratchet as the T-handle turns: the pawl drops at 4 ms (a tooth, a small brass body with
    10 ms decays, a short noise band) and bounces once 18-40 ms later (one quieter tooth). Each take is gone well inside
    100 ms, so clicks 3 ticks apart never pile up."""
    p = PILE_RATCHET[take - 1]
    b = buf(0.20)
    clank = mix(tooth(r, p['f']), 0.45 * norm(modal(p['body'], [1, 2.756, 5.404], [1, 0.5, 0.3], [0.010, 0.007, 0.004], r)),
                0.25 * norm(noise_burst(2000, 7000, 0.006, r)))
    place(b, norm(clank), 0.004, 1.0)
    place(b, tooth(r, p['f'] * 1.04), p['sec'], 0.30)
    return b


SIGH = [dict(m=1.00, tau=0.11, end=0.300), dict(m=1.12, tau=0.08, end=0.270)]


def pile_sigh(take, r):
    """The press letting go when the last fuel is spent (keyed at tick 2 of the finish clip; the handle unwinds and the
    press rises over ticks 2-8): a valve tsk at 3 ms, a breathy exhale whose band falls as the pressure goes (25 ms
    swell, 80-110 ms decay), the screw's thread ticking as it backs off, a low groan of the press plate, and a soft
    knock as the plate comes to rest at 270-300 ms (about tick 8)."""
    p = SIGH[take - 1]
    m = p['m']
    dur = 0.80
    b = buf(dur)
    t = tt(dur)
    place(b, norm(noise_burst(3000, 9000, 0.002, r)), 0.003, 0.25)
    t0 = np.maximum(t - 0.006, 0)
    pr = (t >= 0.006) * np.clip(t0 / 0.025, 0, 1) * np.where(t0 < 0.03, 1.0, np.exp(-(t0 - 0.03) / p['tau']))
    ex = sweep_bandpass(noise(dur, r), 450 * m + 1200 * m * pr, 1400 * m + 3000 * m * pr) * pr
    b += 0.70 * norm(ex)
    s = speed_curve(t, 0.02, p['end'])
    b += 0.14 * norm(np.sin(2 * np.pi * np.cumsum(95 * m * (1 + 0.15 * s)) / SR) * s ** 0.5)
    at = 0.06
    while at < p['end'] - 0.05:
        place(b, norm(modal(1250 * m * r.uniform(0.95, 1.05), [1, 2.3], [1, 0.3], [0.004, 0.003], r)), at, 0.07)
        at += r.uniform(0.030, 0.050)
    place(b, norm(mix(modal(450 * m, [1, 2.756], [1, 0.5], [0.025, 0.015], r), 0.6 * thunk(r, 130, 90, 0.03, 0.015))),
          p['end'], 0.30)
    return b


PLOP = [dict(f=170.0, wet=1.00, glass=2500.0), dict(f=200.0, wet=1.20, glass=2800.0), dict(f=150.0, wet=0.85, glass=2250.0)]


def pile_plop(take, r):
    """A lump of meat dropping into the glass pan. The server starts it at the click, 4 ticks before the lump lands, so
    the file opens with a faint flick (the lump leaving the hand) and the air of the fall rising, and the hit is at
    200 ms: a wet small thud (a low bump 1.6 f -> f, a short squelch of 600-4500 Hz band noise gated at 55 Hz, one bubble-like
    blip) with a faint glass tick of the pan; then at 400 ms (tick 8) the lump's 0.5 px bounce settles with a softer
    squish."""
    p = PLOP[take - 1]
    hit = 4 * T
    dur = 0.80
    b = buf(dur)
    t = tt(dur)
    place(b, norm(noise_burst(600, 3000, 0.004, r)), 0.003, 0.07)
    u = np.clip(t / hit, 0, 1)
    fall = np.where(t < hit, u ** 2, 0.0)
    b += 0.05 * norm(bandpass(noise(dur, r), 400, 2000) * fall)

    def squelch(scale, tau):
        d = 7 * tau + 0.01
        tq = tt(d)
        n = bandpass(noise(d, r), 600 * p['wet'] * scale, 4500 * p['wet'] * scale)
        return norm(n * exp_env(d, tau, 0.002) * (0.6 + 0.4 * np.cos(2 * np.pi * 55 * tq)))
    place(b, sweep_sine(p['f'] * 1.6, p['f'], 0.030, 0.012), hit, 0.45)
    place(b, squelch(1.0, 0.025), hit, 0.70)
    place(b, bubble(r, 950 * p['wet'], 0.018, rise=0.35), hit + 0.008, 0.30)
    place(b, norm(glass_modes(r, p['glass'], decays=(0.020, 0.012, 0.008, 0.005))), hit + 0.002, 0.30)
    place(b, squelch(1.2, 0.015), 8 * T, 0.16)
    return b


BZZT = [dict(f0=100.0, d=0.090), dict(f0=120.0, d=0.075), dict(f0=85.0, d=0.110)]


def pile_bzzt(take, r):
    """The meat twitching with a tiny galvanic buzz (it hops 0.5 px for 4 ticks): a mains-like buzz, a pulse train at
    85-120 Hz sagging 8 % as it dies, band-limited 150-2500 Hz, with a little crackle gated at the same rate, swelling
    and gone in 75-110 ms; a faint soft tick as the lump lands."""
    p = BZZT[take - 1]
    d = p['d']
    b = buf(0.35)
    t = tt(d)
    f = p['f0'] * (1 - 0.08 * t / d)
    env = np.sin(np.pi * t / d) ** 0.7
    pulse = bandpass(osc_harm(f, 20, r, tilt=0.6), 150, 2500)
    gate = (0.5 + 0.5 * np.cos(2 * np.pi * np.cumsum(f) / SR)) ** 4
    cr = bandpass(noise(d, r), 800, 3000) * gate
    place(b, norm(norm(pulse) + 0.3 * norm(cr)) * env, 0.003, 1.0)
    place(b, norm(lowpass(noise_burst(300, 1500, 0.006, r), 1500)), 0.003 + d, 0.12)
    return b


# ---------------------------------------------------------------- the Saline Mixer

TINK = [dict(m=1.00, d=1.00, rat=0.014), dict(m=1.08, d=1.25, rat=0.018), dict(m=0.92, d=0.85, rat=0.011)]


def mixer_tink(take, r):
    """A brass cap flapping down onto its glass canister (the dose contact): a strike, glass modes at about 2 kHz
    (ratios 1, 2.7, 5.1, 8.2; 60 ms) and the cap's own brass plate at 2.9 kHz (25 ms), and the cap bouncing once on
    its hinge 11-18 ms later. High-passed at 500 Hz: small glass, no body."""
    p = TINK[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.60)

    def hit(mm):
        return norm(mix(0.40 * strike(r, 3000),
                        0.55 * norm(glass_modes(r, 2000 * mm, decays=tuple(x * d for x in (0.060, 0.040, 0.022, 0.012)))),
                        0.35 * norm(modal(2900 * mm, [1, 1.59, 2.14], [1, 0.5, 0.3], [0.025 * d, 0.018 * d, 0.010 * d], r))))
    place(b, hit(m), 0.003, 1.0)
    place(b, hit(m * 1.03), 0.003 + p['rat'], 0.22)
    return highpass(b, 500)


CAPS_UP = [dict(m=1.00, lift=0.050), dict(m=1.10, lift=0.045)]


def mixer_caps_up(take, r):
    """The three caps lifting 2 px a tick apart as the screen opens: the linkage under the lid clicks at 3 ms (a soft
    relay), each cap unseats from its glass rim a tick after it starts (a small glass tick, each a little higher), its
    hinge creaks faintly as it rises, and it settles at its 2 px open stop 4 ticks after its start (a soft brass tap):
    the taps land at 200, 250 and 300 ms."""
    p = CAPS_UP[take - 1]
    m = p['m']
    b = buf(0.70)
    place(b, relay_click(r, 1500 * m, release_after=0.025), 0.003, 0.30)
    for k in range(3):
        s0 = k * T
        place(b, norm(modal(1900 * m * (1 + 0.06 * k), [1, 2.7], [1, 0.3], [0.006, 0.004], r)), s0 + p['lift'], 0.26)
        for c in range(2):
            place(b, norm(modal(1200 * m * r.uniform(0.95, 1.05), [1, 2.3], [1, 0.3], [0.004, 0.003], r)),
                  s0 + p['lift'] + 0.03 + 0.02 * c, 0.07)
        place(b, norm(modal(1150 * m * (1 + 0.05 * k), [1, 1.59, 2.14], [1, 0.5, 0.3], [0.012, 0.009, 0.006], r)),
              s0 + 4 * T, 0.24)
    return b


CAP_DOWN = [dict(m=1.00, d=1.00), dict(m=1.10, d=0.80), dict(m=0.90, d=1.15)]


def mixer_cap_down(take, r):
    """One cap dropping back onto its canister: a strike, glass modes about 2.6 kHz with 10 ms decays, a 3.4 kHz brass
    mode, high-passed at 800 Hz. Gone in about 60 ms, so three of them a tick apart stay three."""
    p = CAP_DOWN[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.20)
    hit = mix(0.45 * strike(r, 2500),
              0.50 * norm(glass_modes(r, 2600 * m, decays=(0.010 * d, 0.007 * d, 0.005 * d, 0.003 * d))),
              0.35 * norm(modal(3400 * m, [1, 1.59], [1, 0.4], [0.006 * d, 0.004 * d], r)))
    place(b, norm(hit), 0.003, 1.0)
    return highpass(b, 800)


GLUG = [dict(L=0.34, rise=(480.0, 820.0), bub=[(0.04, 700, 0.020), (0.13, 880, 0.016), (0.21, 1040, 0.014)]),
        dict(L=0.41, rise=(430.0, 760.0), bub=[(0.05, 620, 0.022), (0.16, 760, 0.018), (0.25, 900, 0.016), (0.31, 1100, 0.012)]),
        dict(L=0.32, rise=(540.0, 900.0), bub=[(0.03, 760, 0.018), (0.12, 950, 0.015), (0.19, 1150, 0.013)]),
        dict(L=0.37, rise=(460.0, 860.0), bub=[(0.06, 660, 0.021), (0.15, 820, 0.017), (0.27, 980, 0.014)])]


def mixer_glug(take, r):
    """The tap at its stop and a bottle filling (keyed at tick 4 of the 10-tick pull): a soft knock of the tap at 3 ms;
    a short pour of saline (band noise 300-2500 Hz with a 15-22 Hz flutter) whose resonance rises as the bottle fills
    (a band sweeping 480 -> 820 Hz and the like); three or four bubbles. 0.32-0.41 s of pour, low-passed at 4 kHz,
    soft: it repeats every half second while bottling."""
    p = GLUG[take - 1]
    L = p['L']
    dur = round(L + 0.32, 3)
    b = buf(dur)
    t = tt(dur)
    place(b, norm(mix(modal(900, [1, 2.3], [1, 0.3], [0.012, 0.008], r), 0.5 * norm(noise_burst(600, 3000, 0.004, r)))),
          0.003, 0.30)
    env = np.clip((t - 0.005) / 0.020, 0, 1) * np.where(t < L - 0.06, 1.0, (0.5 + 0.5 * np.cos(np.pi * np.clip((t - L + 0.06) / 0.06, 0, 1))) ** 2)
    flut = 0.65 + 0.35 * np.sin(2 * np.pi * np.cumsum(np.full(len(t), r.uniform(15, 22))) / SR) ** 2
    n = bandpass(noise(dur, r), 300, 2500) * flut * env
    fr = p['rise'][0] + (p['rise'][1] - p['rise'][0]) * np.clip(t / L, 0, 1)
    res = sweep_bandpass(n, fr * 0.8, fr * 1.25)
    b += 0.22 * norm(n) + 0.60 * norm(res)
    for at, f, tau in p['bub']:
        place(b, bubble(r, f, tau, rise=0.2), at, 0.40)
    return lowpass(b, 4000)


# ---------------------------------------------------------------- loops: periodic building blocks

def cnoise(n, r):
    """White noise n samples long, to be filtered circularly."""
    return r.standard_normal(n)


def cband(x, lo=None, hi=None, order=2):
    """Butterworth-shaped band-pass applied in the frequency domain: circular, so a periodic input stays periodic."""
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    H = np.ones_like(f)
    if hi:
        H *= 1 / np.sqrt(1 + (f / hi) ** (2 * order))
    if lo:
        fl = np.maximum(f, 1e-6)
        H *= (fl / lo) ** order / np.sqrt(1 + (fl / lo) ** (2 * order))
    return np.fft.irfft(X * H, len(x))


def loop_finish(x, target_mmax, hp=35.0, ceiling_db=-1.5):
    """The loop's finishing chain: a circular high-pass, DC out, normalise so the file played round and round has the
    target M max, and refuse (rather than limit, which would break the period) if the peak would pass the ceiling.
    No fades and no zeroed ends: the last sample runs into the first. The file is rotated (still the same period) to
    start where the signal is smallest and steps least, so the seam is a near-zero crossing in a quiet moment."""
    x = cband(x, lo=hp, order=2)
    x = x - x.mean()
    a = np.abs(x)
    score = a + np.abs(x - np.roll(x, 1)) + 0.5 * np.convolve(np.concatenate([a[-220:], a, a[:220]]), np.ones(441) / 441, 'valid')
    x = np.roll(x, -int(np.argmin(score)))
    x = x * 10 ** ((target_mmax - m_max(np.tile(x, 3))) / 20)
    pk = 20 * np.log10(np.abs(x).max())
    if pk > ceiling_db:
        raise ValueError(f'loop peak {pk:.1f} dBFS above {ceiling_db}: lower the transient layers')
    return x


PADDLE_L = 3.3  # s: 6 sloshes at 1.818 Hz (2 a turn at 1 turn/s heard at pitch 1.1) and 3 turns


def mixer_paddle(take, r):
    """The paddle stirring saline in the glass tank, a seamless 3.3 s loop. At full speed the paddle turns once a
    second and is played at pitch 1.1, so the file holds 3 turns (3.0 s heard) and 6 sloshes (one per blade pass),
    each a raised-cosine swell of soft low liquid noise (150-1400 Hz) with a little lap of 1-3.5 kHz on its crest,
    their sizes uneven (a per-slosh table) so it does not tick like a clock; a slow wandering 'lap' band; under it the
    spindle's faint hum (120 Hz and harmonics, 396 whole cycles) and a fainter 880 Hz bearing whine (2904 cycles)."""
    n = int(round(PADDLE_L * SR))
    t = np.arange(n) / SR
    ph = (t / PADDLE_L * 6) % 1.0  # slosh phase, 6 per loop
    k = np.floor(t / PADDLE_L * 6).astype(int) % 6
    size = np.array([1.00, 0.82, 0.94, 0.78, 1.00, 0.88])[k]
    swell = (0.5 - 0.5 * np.cos(2 * np.pi * ph)) ** 1.5 * size
    body = cband(cnoise(n, r), 150, 1400)
    lap = cband(cnoise(n, r), 1000, 3500)
    wander = 0.5 + 0.5 * np.sin(2 * np.pi * 3 * t / PADDLE_L + 1.0)  # three slow swings a loop
    wobble = cband(cnoise(n, r), 300, 700) * (0.6 + 0.4 * wander)
    slosh = 0.62 * norm(body * (0.25 + 0.75 * swell)) + 0.14 * norm(lap * swell ** 2) + 0.18 * norm(wobble)
    hum = sum(a * np.sin(2 * np.pi * 120 * h * t + r.uniform(0, 2 * np.pi)) for h, a in [(1, 1.0), (2, 0.5), (3, 0.25)])
    whine = np.sin(2 * np.pi * 880 * t + r.uniform(0, 2 * np.pi)) * (0.8 + 0.2 * np.sin(2 * np.pi * 3 * t / PADDLE_L))
    return slosh + 0.07 * norm(hum) + 0.015 * whine


WHINE_L = 2.4  # s: 20 tube passes at 8.333 Hz (10 Hz heard at pitch 1.2) and 5 turns at 2.083 Hz (2.5 turns/s)
WHINE_F0 = 437.5  # Hz: 1050 whole cycles in the loop; heard 350 Hz at pitch 0.8 and 525 Hz at 1.2


def centrifuge_whine(take, r):
    """The rotor at full speed, a seamless 2.4 s loop. A small motor's whine (437.5 Hz and 8 harmonics falling as
    1/k^1.3, band-limited 300-4000 Hz, with a 0.3 % wobble once a turn) kept under its brush grit (2.5-7 kHz noise gated
    by the commutator at f0/6 = 72.9 Hz) and its air, so it reads as a motor, not a synth; a low drive hum at f0/3; and
    the faint air swish of the four tubes passing (800-6000 Hz noise swelling 20 times a loop: 10 Hz at full speed's
    pitch 1.2). Every periodic layer completes whole cycles in the 2.4 s (1050, 175, 350, 20, 5)."""
    n = int(round(WHINE_L * SR))
    t = np.arange(n) / SR
    turn = 5 / WHINE_L
    ph = 2 * np.pi * WHINE_F0 * t + 0.003 * WHINE_F0 / turn * np.sin(2 * np.pi * turn * t)
    whine = sum((1 / h ** 1.3) * np.sin(h * ph + r.uniform(0, 2 * np.pi)) for h in range(1, 9))
    whine = cband(whine, 300, 4000)
    gate = (0.5 + 0.5 * np.cos(2 * np.pi * (WHINE_F0 / 6) * t)) ** 4
    grit = cband(cnoise(n, r), 2500, 7000) * gate
    air = cband(cnoise(n, r), 800, 6000)
    swish = (0.5 - 0.5 * np.cos(2 * np.pi * 20 * t / WHINE_L)) ** 2
    tube = np.array([1.0, 0.9, 0.95, 0.85])[np.floor(t / WHINE_L * 20).astype(int) % 4]  # the four tubes are not alike
    hum = np.sin(2 * np.pi * WHINE_F0 / 3 * t + r.uniform(0, 2 * np.pi)) + 0.4 * np.sin(2 * np.pi * WHINE_F0 / 1.5 * t)
    return (0.30 * norm(whine) + 0.30 * norm(grit) + 0.16 * norm(air * (0.55 + 0.45 * swish * tube))
            + 0.14 * norm(hum))


# ---------------------------------------------------------------- references (measured as the game plays them)

PISTON_IN = dict(event='block.piston.contract', file='tile__piston__in.ogg', vol=0.5, pitch=0.675)
CRAFTER = dict(event='block.crafter.craft', file='block__crafter__craft.ogg', vol=1.0, pitch=1.0)
BOOK_PICKUP = dict(event='block.chiseled_bookshelf.pickup', file='block__chiseled_bookshelf__pickup1.ogg', vol=1.0, pitch=1.0)
IRON_TD_OPEN = dict(event='block.iron_trapdoor.open', file='block__iron_trapdoor__open1.ogg', vol=0.9, pitch=0.95)
COPPER_TD_CLOSE = dict(event='block.copper_trapdoor.close', file='block__copper_trapdoor__toggle1.ogg', vol=1.0, pitch=0.95)
WOOD_TD_OPEN = dict(event='block.wooden_trapdoor.open', file='block__wooden_trapdoor__open1.ogg', vol=0.9, pitch=0.95)
CHAIN_EQUIP = dict(event='item.armor.equip_chain', file='item__armor__equip_chain1.ogg', vol=1.0, pitch=1.0)
TORCH_BURNOUT = dict(event='block.redstone_torch.burnout', file='random__fizz.ogg', vol=0.5, pitch=2.0)
SPYGLASS = dict(event='item.spyglass.use', file='item__spyglass__use.ogg', vol=1.0, pitch=1.0)
HONEY_PLACE = dict(event='block.honey_block.place', file='block__honeyblock__step1.ogg', vol=1.0, pitch=0.8)
COPPER_BULB = dict(event='block.copper_bulb.turn_on', file='block__copper_bulb__toggle.ogg', vol=1.0, pitch=1.0)
LANTERN = dict(event='block.lantern.place', file='block__lantern__place1.ogg', vol=1.0, pitch=0.88)
BOTTLE_FILL = dict(event='item.bottle.fill', file='item__bottle__fill2.ogg', vol=1.0, pitch=1.0)
BEACON_AMBIENT = dict(event='block.beacon.ambient', file='block__beacon__ambient.ogg', vol=0.9, pitch=1.0)

BASE = dict(category='BLOCKS', attenuation=8, fixed_range=False, volume=1.0, ticks=None, muffled=False)
KEY_NOTE = ' Keyframe volume 1.0 (the file carries the loudness).'


def ev(**kw):
    d = dict(BASE)
    d.update(kw)
    return d


SOUNDS = [
    # ---- the house kit (every machine)
    ev(id='machine.drawer_out', file='machine/drawer_out', fn=drawer_out, takes=3, target=-27.0, cls='servo (small slide)',
       kind=('servo', 4 * T), fade_ms=20, hit_ms=3, ref=BOOK_PICKUP, pitch=[0.95, 1.05],
       subtitle_en='Drawer slides out', subtitle_pt='Gaveta desliza para fora',
       clip_key='drawer_open key 0: machine.drawer_out|0.5|1.0 -> machine.drawer_out|1.0|1.0',
       play='Kit clip drawer_open, key 0: the detent lets go at 3 ms, the slide runs ticks 1-4 decelerating; machine.drawer_stop is the stop at key 4.' + KEY_NOTE),
    ev(id='machine.drawer_stop', file='machine/drawer_stop', fn=drawer_stop, takes=3, target=-31.0, cls='tick (soft stop)',
       kind='click', fade_ms=10, hit_ms=3, ref=CRAFTER, pitch=[1.05, 1.15], subtitle_en=None, subtitle_pt=None,
       clip_key='drawer_open key 4: machine.drawer_stop|0.4|1.1 -> machine.drawer_stop|1.0|1.1',
       play='Kit clip drawer_open, key 4, pitch 1.1 (the random range is centred on it). No subtitle (part of the slide).' + KEY_NOTE),
    ev(id='machine.drawer_shut', file='machine/drawer_shut', fn=drawer_shut, takes=3, target=-28.5, cls='soft clack',
       kind='latch', fade_ms=12, hit_ms=3, ref=IRON_TD_OPEN, pitch=[0.95, 1.05],
       subtitle_en='Drawer shuts', subtitle_pt='Gaveta fecha',
       clip_key='drawer_close key 4: machine.drawer_shut|0.5|1.0 -> machine.drawer_shut|1.0|1.0',
       play='Kit clip drawer_close, key 4 (the contact): the clack at 3 ms, the 0.5 px bounce lands at about 100 ms (tick 6).' + KEY_NOTE),
    ev(id='machine.hatch_open', file='machine/hatch_open', fn=hatch_open, takes=2, target=-28.5, cls='servo (small hinge)',
       kind=('servo', 5 * T), fade_ms=20, hit_ms=3, ref=IRON_TD_OPEN, pitch=[0.95, 1.05],
       subtitle_en='Hatch opens', subtitle_pt='Portinhola abre',
       clip_key='hatch_open key 0: machine.hatch_open|1.0|1.0',
       play='Kit clip hatch_open, key 0: catch at 3 ms, creak while it swings, the stay tap at 200-212 ms (tick 4). Not used by the Pile, Mixer or Centrifuge yet.' + KEY_NOTE),
    ev(id='machine.hatch_shut', file='machine/hatch_shut', fn=hatch_shut, takes=3, target=-27.0, cls='servo (small panel shut)',
       kind='latch', fade_ms=12, hit_ms=3, ref=COPPER_TD_CLOSE, pitch=[0.95, 1.05],
       subtitle_en='Hatch shuts', subtitle_pt='Portinhola fecha',
       clip_key='hatch_shut key 4: machine.hatch_shut|1.0|1.0',
       play='Kit clip hatch_shut, key 4 (the contact): the strike at 3 ms, the magnet catch at 18 ms, the bounce lands at about 100 ms (tick 6). Not used by these three machines yet.' + KEY_NOTE),
    ev(id='machine.flag_up', file='machine/flag_up', fn=flag_up, takes=3, target=-30.0, cls='tick (sprung flick)',
       kind='latch', fade_ms=12, hit_ms=3, ref=WOOD_TD_OPEN, pitch=[0.95, 1.05],
       subtitle_en='Flag: ready to collect', subtitle_pt='Bandeira: pode retirar',
       clip_key='flag_up key 2: machine.flag_up|0.6|1.0 -> machine.flag_up|1.0|1.0',
       play='Kit clip flag_up, key 2: the spring flicks at 3 ms, the arm meets its stop as it overshoots at 105-135 ms, the plate jiggles after.' + KEY_NOTE),
    ev(id='machine.flag_down', file='machine/flag_down', fn=flag_down, takes=3, target=-30.5, cls='tick (light tap)',
       kind='latch', fade_ms=12, hit_ms=3, ref=WOOD_TD_OPEN, pitch=[0.95, 1.05],
       subtitle_en='Flag folds down', subtitle_pt='Bandeira abaixa',
       clip_key='flag_down key 4: machine.flag_down|0.5|1.0 -> machine.flag_down|1.0|1.0',
       play='Kit clip flag_down, key 4 (the contact): the tap at 3 ms, the bounce lands at about 100 ms (tick 6).' + KEY_NOTE),
    ev(id='machine.rattle', file='machine/rattle', fn=rattle, takes=3, target=-27.0, cls='servo-class file, played quieter (a layer)',
       kind='latch', fade_ms=12, hit_ms=100, ref=CHAIN_EQUIP, pitch=[0.95, 1.05], volume=0.4,
       subtitle_en=None, subtitle_pt=None,
       clip_key='refuse (and refuse_s/k/r, refuse_open) key 0: machine.rattle|0.4|1.0 (keep 0.4)',
       play='Every machine\'s refuse clip, key 0, at volume 0.4 (deliberately: it is a layer under the server\'s feedback.refused, which carries the subtitle and the message). Strokes at 100, 200, 300 ms on the clip\'s reversals (ticks 2, 4, 6); a faint tick at 3 ms.'),
    # ---- the Galvanic Pile
    ev(id='pile.crackle', file='pile/crackle', fn=pile_crackle, takes=3, target=-26.0, cls='arc crackle (small)',
       kind='latch', fade_ms=10, hit_ms=3, ref=TORCH_BURNOUT, pitch=[0.94, 1.06],
       subtitle_en='Pile sparks', subtitle_pt='Pilha solta faísca',
       clip_key='work key 7: pile.crackle|0.4|1.0 -> pile.crackle|1.0|1.0',
       play='Pile clip work, key 7 (a tick after the spark particle at 6), at term_a. Every 50 ticks while working.' + KEY_NOTE),
    ev(id='pile.ratchet', file='pile/ratchet', fn=pile_ratchet, takes=4, target=-31.0, cls='ratchet tooth',
       kind='click', fade_ms=8, hit_ms=4, ref=SPYGLASS, pitch=[1.0, 1.0], pitch_per_play=[0.9, 0.95, 1.0, 1.05, 1.1],
       subtitle_en='Press screw ratchets', subtitle_pt='Catraca da prensa estala',
       clip_key='refuel keys 5, 8, 11, 14: pile.ratchet|0.4|0.9, 0.95, 1.0, 1.05 -> volume 1.0, same pitches; work keys 35, 47: pile.ratchet|0.35|1.0 and |0.35|1.1 -> volume 1.0, same pitches',
       play='Refuel keys 5, 8, 11, 14 at pitch 0.9, 0.95, 1.0, 1.05; work keys 35 and 47 at pitch 1.0 and 1.1; volume 1.0 for all (the four refuel clicks together measure about -26.4, the whole-ratchet class). A random take each time. Each click is gone in under 100 ms (-84 dB or lower 100 ms after its peak), so clicks 3 ticks apart stay separate.'),
    ev(id='pile.sigh', file='pile/sigh', fn=pile_sigh, takes=2, target=-26.0, cls='hiss (clamp release, soft)',
       kind=('servo', 0.30), fade_ms=25, hit_ms=3, ref=PISTON_IN, pitch=[0.97, 1.03],
       subtitle_en='Press sighs, spent', subtitle_pt='Prensa suspira, esgotada',
       clip_key='finish key 2: pile.sigh|0.5|1.0 -> pile.sigh|1.0|1.0',
       play='Pile clip finish, key 2: the exhale at once, the plate comes to rest at 300 ms (tick 8), with the poof at 3.' + KEY_NOTE),
    ev(id='pile.plop', file='pile/plop', fn=pile_plop, takes=3, target=-27.0, cls='glass clink class (small wet event)',
       kind=('servo', 0.40), fade_ms=20, hit_ms=200, preroll_ticks=4, ref=HONEY_PLACE, pitch=[0.95, 1.05],
       subtitle_en='Meat plops into pan', subtitle_pt='Carne cai na cuba',
       play='Server, at the pan, on the insert click: the file\'s hit is at 200 ms, so started at the click it lands on tick 4 of the 8-tick plop (contact_offset_ticks 4). The bounce settles at 400 ms (tick 8).'),
    ev(id='pile.bzzt', file='pile/bzzt', fn=pile_bzzt, takes=3, target=-36.5, cls='idle machine life',
       kind='click', fade_ms=10, hit_ms=3, ref=COPPER_BULB, pitch=[0.94, 1.06],
       subtitle_en='Meat twitches', subtitle_pt='Carne se contorce',
       play='Client, at the pan, with the twitch (every 600 ticks while working; the lump hops 0.5 px for 4 ticks): play at the twitch\'s first tick, volume 1.0. Very quiet by design (idle life).'),
    # ---- the Saline Mixer
    ev(id='mixer.tink', file='mixer/tink', fn=mixer_tink, takes=3, target=-26.5, cls='glass clink',
       kind='latch', fade_ms=15, hit_ms=3, ref=LANTERN, pitch=[1.0, 1.0], pitch_per_play=[1.25, 1.1, 0.95],
       subtitle_en='Canister cap tinks', subtitle_pt='Tampa do frasco tilinta',
       clip_key='batch keys 12, 26, 40: mixer.tink|0.4|1.25, 1.1, 0.95 -> volume 1.0, same pitches',
       play='Mixer clip batch, keys 12, 26, 40 (the three dose contacts) at pitch 1.25, 1.1, 0.95: sugar, kelp, redstone. A random take each time.' + KEY_NOTE),
    ev(id='mixer.caps_up', file='mixer/caps_up', fn=mixer_caps_up, takes=2, target=-29.5, cls='tick (three small lifts)',
       kind=('servo', 0.30), fade_ms=15, hit_ms=3, ref=WOOD_TD_OPEN, pitch=[0.97, 1.03],
       subtitle_en='Canister caps lift', subtitle_pt='Tampas dos frascos sobem',
       clip_key='open key 0: mixer.caps_up|0.4|1.0 -> mixer.caps_up|1.0|1.0',
       play='Mixer clip open, key 0: one sound for all three caps (unseat ticks at 45-150 ms, open stops at 200, 250, 300 ms).' + KEY_NOTE),
    ev(id='mixer.cap_down', file='mixer/cap_down', fn=mixer_cap_down, takes=3, target=-31.0, cls='tick',
       kind='click', fade_ms=8, hit_ms=3, ref=SPYGLASS, pitch=[1.0, 1.0], pitch_per_play=[1.2, 1.1, 1.0], volume=0.6,
       subtitle_en='Canister caps settle', subtitle_pt='Tampas dos frascos baixam',
       clip_key='close keys 3, 4, 5: mixer.cap_down|0.3|1.2, 1.1, 1.0 -> volume 0.6, same pitches',
       play='Mixer clip close, keys 3, 4, 5 at pitch 1.2, 1.1, 1.0, volume 0.6 (three clicks in 100 ms add up; see the trio measurement). A different take each time if the handler allows.'),
    ev(id='mixer.glug', file='mixer/glug', fn=mixer_glug, takes=4, target=-26.0, cls='glug (soft, repeating)',
       kind=('servo', 0.30), fade_ms=20, hit_ms=3, ref=BOTTLE_FILL, pitch=[0.95, 1.05],
       subtitle_en='Saline tap glugs', subtitle_pt='Torneira de soro gorgoleja',
       clip_key='bottle key 4: mixer.glug|0.5|1.0 -> mixer.glug|1.0|1.0',
       play='Mixer clip bottle, key 4 (the tap at its stop), once per 10-tick loop while bottling. Random take and pitch 0.95-1.05 every time, so the run never repeats exactly.' + KEY_NOTE),
    ev(id='mixer.paddle', file='mixer/paddle', fn=mixer_paddle, takes=1, target=-29.5, cls='hum/motor loop (soft), at full speed',
       kind='loop', fade_ms=0, hit_ms=None, ref=BEACON_AMBIENT, finish=loop_finish, loop=True, attenuation=10,
       pitch=[0.9, 1.1], subtitle_en='Mixer stirs saline', subtitle_pt='Misturador mexe o soro',
       play='Looped client sound (a tickable sound instance with looping on, created once per mixer, at locator tank): volume = paddle speed (0 to 1), pitch = 0.9 + 0.2 x speed, both chased (0.2 a tick, pitch at most 0.05 a tick); start at volume 0, stop when the volume falls under 0.01. Register with "stream": false so the whole file is one looped buffer. Seamless 3.3 s file, no fades in it.'),
    # ---- the Centrifuge
    ev(id='centrifuge.whine', file='centrifuge/whine', fn=centrifuge_whine, takes=1, target=-28.0, cls='hum/motor loop (centrifuge), at full speed',
       kind='loop', fade_ms=0, hit_ms=None, ref=BEACON_AMBIENT, finish=loop_finish, loop=True, attenuation=12,
       pitch=[0.8, 1.2], subtitle_en='Centrifuge whirs', subtitle_pt='Centrífuga zune',
       play='Looped client sound (a tickable sound instance with looping on, created once per centrifuge, at locator whine): volume = rotor speed s (0 to 1), pitch = 0.8 + 0.4 x s, both chased (0.2 a tick, pitch at most 0.05 a tick); start at volume 0, stop when the volume falls under 0.01. Register with "stream": false. Seamless 2.4 s file, no fades in it. The finish stays the server\'s vanilla brewing-stand sound plus feedback.done.'),
]

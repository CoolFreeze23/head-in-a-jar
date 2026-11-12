"""The machines, second batch: the Life Support Cradle, the Body Printer and the Gene Splicer.

The Life Support Cradle is a convalescent's wingback armchair with a brass bellows column behind it that breathes for a
docked head in a jar: on docking a pale rubber hose swings off its brass hook onto the jar's valve and a wool blanket
is laid over the lap; on undocking the seal lets go, the hose springs back onto its hook and the blanket goes back on
the arm. While a head is docked the bellows rises 6 px and sinks every 3 s: a soft leather-bellows breath in and out.

The Body Printer is a coin-operated photo booth that prints a body: a brass print bar climbs a steel post a pixel every
40 ticks while a copper print head shuttles across it (the carriage's whirr, a loop), the head measures the shoulders
(taps and a tape-measure zip), three ratchet clicks set the collar, the develop starts with a rising photographic hum
and the bar comes back down onto the floor; the glass door unlatches and swings open onto its stop, a headless body
steps out barefoot on the booth's metal floor, and later the door swings back and shuts. A small coin-op bell pings
when all four materials are in; the mechanism winds down when the power fails and whirs back to life when it returns.

The Gene Splicer is a vintage hand-crank sewing machine on a lab bench sewing a gene strand: the clutch engages, the
needle stitches twice a second (now and then a stitch catches and stutters), the wheel coasts down when it stops,
hiccups once or twice at the finish (the wheel kicked back, the spools hopping), and the serum cassette pops up out of
its deck. Picking a gene draws thread off a spool and seats it back; a little bell on the machine dings as the strand
reaches each stage.

Times inside a file are seconds; one tick is 50 ms. Every keyframed file has its hit at 0-5 ms. The bell is played by
the server with its hit at 3 ms. Takes differ by construction (a per-take table of frequencies, decays and timings),
not by luck of the seed. The whirr is a loop, built periodic and finished by the first batch's `loop_finish`.
"""
import numpy as np
from nsynth import *
from recipes_machines import small_tick, cnoise, cband, loop_finish

T = TICK


# ---------------------------------------------------------------- shared pieces

def env_seg(t, a, b, shape='sin2'):
    """Speed 0..1..0 across a move from a to b (s); 'in' accelerates into b; 'out' starts fast and slows to b."""
    u = np.clip((t - a) / max(b - a, 1e-6), 0, 1)
    inside = (t > a) & (t < b)
    if shape == 'sin2':
        return np.where(inside, np.sin(np.pi * u) ** 2, 0.0)
    if shape == 'in':
        return np.where(inside, u, 0.0)
    if shape == 'out':
        return np.where(inside, (1 - u) ** 2, 0.0)
    raise ValueError(shape)


def release(s, t, at, tau):
    """Let a speed curve go smoothly at `at`: from there it decays with time constant tau instead of stopping dead."""
    v = float(s[t < at][-1])
    return np.where(t < at, s, v * np.exp(-(t - at) / tau))


def smooth(x, ms):
    """A gentle moving-average smoother for envelopes (ms wide)."""
    n = max(1, int(ms / 1000 * SR))
    return np.convolve(x, np.ones(n) / n, 'same')


def creaks(r, b, t0, t1, f, gap=(0.014, 0.030), level=0.12, d=0.004):
    """Stick-slip pings of a hinge or a leather pleat between t0 and t1: short modes at f (+-5 %), uneven gaps."""
    at = t0
    while at < t1:
        place(b, norm(modal(f * r.uniform(0.95, 1.05), [1, 2.3], [1, 0.3], [d, d * 0.6], r)), at, level * r.uniform(0.4, 1.0))
        at += r.uniform(*gap)
    return b


def knock(r, f, d=1.0, th=(220.0, 150.0), th_gain=0.35, nz=0.3, band=(600.0, 4000.0), hp=1500.0, ratios=(1, 2.3, 3.9)):
    """A small dull contact: a strike, a short damped body at f, a light thunk, a short noise band."""
    return norm(mix(0.35 * strike(r, hp),
                    0.6 * norm(modal(f, list(ratios), [1, 0.45, 0.2], [0.014 * d, 0.009 * d, 0.005 * d], r, 0.02)),
                    th_gain * thunk(r, th[0], th[1], 0.018, 0.010),
                    nz * norm(noise_burst(band[0], band[1], 0.008, r))))


def brass_ring(r, f, d=1.0):
    """A small brass part ringing briefly (free-bar ratios)."""
    return norm(modal(f, [1, 2.756, 5.404], [1, 0.5, 0.25], [0.030 * d, 0.018 * d, 0.010 * d], r, 0.01))


def bell_modes(r, f, decays, amps=(1.0, 0.45, 0.30, 0.18, 0.10)):
    """A small struck bell: a fundamental, its octave, a minor-third partial an octave up, a fifth and the nominal's
    upper partial (ratios 1, 2, 2.4, 3, 4.2; chosen, not measured from a bell)."""
    return modal(f, [1, 2.0, 2.4, 3.0, 4.2], list(amps), list(decays), r, 0.002)


# ======================================================================= the Life Support Cradle

HOSE_SWING = [dict(m=1.00, lo=500.0, hi=3200.0, wub=0.16), dict(m=1.06, lo=560.0, hi=3300.0, wub=0.10),
              dict(m=0.88, lo=420.0, hi=2700.0, wub=0.22)]


def cradle_hose_swing(take, r):
    """The pale rubber hose lifted off its brass hook (keyed at dock tick 2, the top of the 4-degree lift): the hose end
    leaving the hook at 3 ms (a soft rubbery tick and a tiny brass tick), a held tick, then from 50 ms (tick 3) the
    hose laid forward and turned inward, easeInQuad, accelerating into the valve at 300 ms (tick 8, which is
    cradle.hose_click): a rubber swish whose band and level rise with the speed, and a faint low flex 'wub' of the
    hose. The swish lets go in 20 ms at the contact."""
    p = HOSE_SWING[take - 1]
    m = p['m']
    dur = 0.55
    b = buf(dur)
    t = tt(dur)
    place(b, knock(r, 700 * m, 0.7, th=(260, 180), th_gain=0.25, nz=0.25, band=(500, 3000)), 0.003, 0.55)
    place(b, small_tick(r, 2400 * m, 0.004), 0.006, 0.12)
    a, c = 3 * T - 2 * T, 8 * T - 2 * T  # 50 ms .. 300 ms after the key
    s = release(env_seg(t, a, c, 'in'), t, c, 0.020) * np.clip((t - a) / 0.02, 0, 1)
    sw = sweep_bandpass(noise(dur, r), p['lo'] * m * (0.7 + 0.5 * s), p['hi'] * m * (0.6 + 0.6 * s)) * s ** 1.3
    b += 0.75 * norm(sw)
    wub = bandpass(noise(dur, r), 150, 450) * s * (0.6 + 0.4 * np.sin(2 * np.pi * 11 * t))
    b += p['wub'] * norm(wub)
    return b


HOSE_CLICK = [dict(m=1.00, d=1.00, det=0.012), dict(m=1.10, d=0.80, det=0.016), dict(m=0.91, d=1.20, det=0.010)]


def cradle_hose_click(take, r):
    """The hose end clicking onto the jar's brass valve (dock tick 8, easeInQuad into the contact): the brass nozzle meets
    the valve at 3 ms (a strike, a small brass ring about 2.1 kHz, the valve's duller body about 900 Hz and a rubber
    thump), the coupling's spring detent clicks home 10-16 ms later, and the 4-degree bounce (ticks 9-10) seats it
    again at 100 ms, softer."""
    p = HOSE_CLICK[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.45)
    hit = mix(0.40 * strike(r, 2500), 0.45 * brass_ring(r, 2100 * m, 0.8 * d),
              0.40 * norm(modal(900 * m, [1, 2.3, 3.6], [1, 0.4, 0.2], [0.012 * d, 0.008 * d, 0.005 * d], r)),
              0.30 * thunk(r, 240, 160, 0.016, 0.008))
    place(b, norm(hit), 0.003, 1.0)
    place(b, tooth(r, 3000 * m), 0.003 + p['det'], 0.35)
    place(b, norm(mix(0.5 * brass_ring(r, 2150 * m, 0.6 * d), 0.4 * thunk(r, 240, 160, 0.012, 0.006))), 0.103, 0.28)
    return b


WHOOSH = [dict(m=1.00, flut=19.0, rus=0.10, s=(1.0, 1.0)), dict(m=1.14, flut=25.0, rus=0.14, s=(0.8, 1.1)),
          dict(m=0.88, flut=15.0, rus=0.07, s=(1.15, 0.9))]


def cradle_blanket_whoosh(take, r):
    """A wool blanket swung through the air (dock tick 13; undock tick 7 at pitch 1.1). Dock: lifted off the arm at 3 ms
    (a soft cloth rustle), pulled forward and turning 0-150 ms (ticks 13-16), carried low across the front 150-300 ms
    (16-19), dropped onto the seat rail, accelerating, 300-450 ms (19-22, where cradle.flump takes over). Each leg is a
    swell of soft low cloth air (150-1500 Hz) with a wool flutter of 15-25 Hz and a few faint fabric rustles on top; the
    drop lets go in 30 ms. At pitch 1.1 (undock 7-16) the file is 41 ms short of the 450 ms move."""
    p = WHOOSH[take - 1]
    m = p['m']
    dur = 0.72
    b = buf(dur)
    t = tt(dur)
    place(b, norm(noise_burst(300 * m, 2500 * m, 0.012, r, attack=0.002)), 0.003, 0.35)
    s = 0.45 * p['s'][0] * env_seg(t, 0.0, 0.15) + 0.85 * p['s'][1] * env_seg(t, 0.13, 0.31)
    s = s + release(env_seg(t, 0.30, 0.45, 'in'), t, 0.45, 0.030) * np.clip((t - 0.30) / 0.03, 0, 1)
    s = smooth(s, 8)
    flut = 0.62 + 0.38 * np.sin(2 * np.pi * np.cumsum(p['flut'] * (0.7 + 0.6 * s)) / SR) ** 2
    air = sweep_bandpass(noise(dur, r), 150 * m * (0.8 + 0.6 * s), 900 * m * (0.8 + 0.8 * s)) * s * flut
    b += 0.80 * norm(air)
    at = 0.05
    while at < 0.44:
        sp = float(np.interp(at, t, s))
        place(b, norm(noise_burst(1500 * m, 5000 * m, 0.004, r, attack=0.001)), at, p['rus'] * sp * r.uniform(0.4, 1.0))
        at += r.uniform(0.02, 0.05)
    return lowpass(b, 6000)


FLUMP = [dict(m=1.00, th=(130.0, 85.0), bounce=0.100, rus=0.10), dict(m=1.15, th=(150.0, 100.0), bounce=0.092, rus=0.14),
         dict(m=0.87, th=(115.0, 75.0), bounce=0.108, rus=0.06)]


def cradle_flump(take, r):
    """The blanket landing soft (dock tick 22 on the seat's front rail; undock tick 16 on the arm, pitch 1.2): a soft
    low thump at 3 ms, a muffled wool body (noise 80-900 Hz, 35 ms), the air pushed out from under it (a short puff
    300-1500 Hz) and the cloth settling (a faint rustle), then the half-pixel bounce landing again at about 100 ms,
    softer. Low-passed at 2.5 kHz: wool, no rattle."""
    p = FLUMP[take - 1]
    m = p['m']
    b = buf(0.55)
    place(b, thunk(r, p['th'][0], p['th'][1], 0.035, 0.015), 0.003, 0.55)
    body = lowpass(bandpass(noise(0.30, r), 80 * m, 900 * m), 1200) * exp_env(0.30, 0.035, 0.003)
    place(b, norm(body), 0.003, 0.85)
    place(b, norm(noise_burst(300 * m, 1500 * m, 0.030, r, attack=0.006)), 0.008, 0.30)
    place(b, norm(noise_burst(800 * m, 2500 * m, 0.040, r, attack=0.01)), 0.02, p['rus'])
    place(b, norm(mix(0.6 * thunk(r, p['th'][0] * 1.1, p['th'][1] * 1.1, 0.02, 0.01),
                      norm(lowpass(bandpass(noise(0.2, r), 100 * m, 800 * m), 1200) * exp_env(0.2, 0.020, 0.002)))),
          p['bounce'], 0.25)
    return lowpass(b, 2500)


PFFF = [dict(m=1.00, tau=0.080, lo=4000.0, hi=14000.0), dict(m=1.10, tau=0.060, lo=4400.0, hi=15000.0),
        dict(m=0.92, tau=0.105, lo=3700.0, hi=14000.0)]


def cradle_seal_pfff(take, r):
    """The valve seal breaking on undock (tick 0; the hose presses 4 degrees, then springs up): the rubber seal unsticks
    at 3 ms (a tiny suction pop, a low 300 Hz bump and a 4 kHz tick), then a small pneumatic pfff: pressure up in 8 ms,
    decaying 60-105 ms, its band falling as the pressure goes (4-15 kHz down to about 2.5-9 kHz), 0.3-0.4 s heard."""
    p = PFFF[take - 1]
    m = p['m']
    dur = 0.70
    b = buf(dur)
    t = tt(dur)
    place(b, sweep_sine(420 * m, 260 * m, 0.010, 0.004), 0.003, 0.30)
    place(b, small_tick(r, 4000 * m, 0.003), 0.003, 0.30)
    t0 = np.maximum(t - 0.008, 0)
    pr = (t >= 0.008) * np.clip(t0 / 0.008, 0, 1) * np.exp(-t0 / p['tau'])
    hs = sweep_bandpass(noise(dur, r), p['lo'] * (0.65 + 0.35 * pr), p['hi'] * (0.6 + 0.4 * pr)) * pr
    b += 0.90 * norm(hs)
    return b


HANG = [dict(m=1.00, d=1.00, bounce=0.100), dict(m=1.12, d=0.80, bounce=0.094), dict(m=0.90, d=1.20, bounce=0.106)]


def cradle_hose_hang(take, r):
    """The hose dropped back on its brass hook (undock tick 4, easeInQuad into the contact): the rubber end knocks the
    hook at 3 ms (a dull rubbery knock about 520 Hz and a short thump), the brass hook rings briefly (about 1.8 kHz),
    the hose wobbles once (a faint low flex), and the 5-degree bounce (tick 5) knocks again at about 100 ms (tick 6),
    softer."""
    p = HANG[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.50)
    place(b, knock(r, 520 * m, d, th=(230, 150), th_gain=0.40, nz=0.20, band=(400, 2500), hp=1200), 0.003, 0.75)
    place(b, brass_ring(r, 1800 * m, d), 0.004, 0.40)
    place(b, norm(bandpass(noise(0.12, r), 120, 400) * np.sin(np.pi * tt(0.12) / 0.12) ** 2), 0.02, 0.10)
    place(b, mix(0.6 * knock(r, 560 * m, d * 0.8, th_gain=0.3, nz=0.15), 0.3 * brass_ring(r, 1850 * m, d * 0.7)),
          0.003 + p['bounce'], 0.30)
    return b


# The breath: the bellows' crest rises 6 px over breathe ticks 2-22 (easeInOutSine) and is held to 32, sinks to half a
# pixel by 48 (easeInOutSine) and lands on 50 (easeInQuad), with a 0.4 px bounce at 52.
INHALE = [dict(m=1.00, pk=0.45, L=0.95, flap=900.0, lo=260.0, hi=2000.0, pleat=640.0),
          dict(m=1.08, pk=0.40, L=0.90, flap=1000.0, lo=300.0, hi=2300.0, pleat=700.0),
          dict(m=0.92, pk=0.50, L=1.00, flap=820.0, lo=230.0, hi=1800.0, pleat=590.0),
          dict(m=1.04, pk=0.47, L=1.06, flap=950.0, lo=280.0, hi=2150.0, pleat=760.0)]


def cradle_inhale(take, r):
    """The bellows drawing air in (keyed at breathe tick 3; the crest rises 2-22, fastest about tick 12): the leather
    intake flap lifting at 3 ms (a soft 'tup': a short low noise burst and a damped mode about 900 Hz), then the air
    drawn in, its level following the crest's speed (a sin^2-like swell peaking 400-500 ms in and gone by 0.9-1.06 s)
    and its band a little brighter as it speeds up (about 250-2200 Hz), with two or three faint creaks of the leather
    pleats as they open. Soft, dark and different each take: it repeats every 3 s for as long as a head is docked."""
    p = INHALE[take - 1]
    m = p['m']
    dur = round(p['L'] + 0.35, 3)
    b = buf(dur)
    t = tt(dur)
    place(b, norm(mix(norm(noise_burst(150, 1200, 0.006, r, attack=0.001)),
                      0.5 * norm(modal(p['flap'], [1, 2.2], [1, 0.3], [0.006, 0.004], r)))), 0.003, 0.30)
    u = np.clip((t - 0.01) / p['L'], 0, 1)
    k = np.log(0.5) / np.log(p['pk'] / p['L'])  # skew so the swell peaks at pk
    s = np.sin(np.pi * u ** k) ** 2 * ((t > 0.01) & (t < 0.01 + p['L']))
    s = smooth(s, 20)
    air = sweep_bandpass(noise(dur, r), p['lo'] * (0.8 + 0.4 * s), p['hi'] * (0.7 + 0.5 * s)) * s
    b += 0.85 * norm(air)
    b += 0.10 * norm(bandpass(noise(dur, r), 120, 400) * s ** 2)
    for at in (0.18 * p['L'], 0.42 * p['L'], 0.70 * p['L']):
        place(b, norm(modal(p['pleat'] * r.uniform(0.95, 1.05), [1, 2.3], [1, 0.3], [0.005, 0.003], r)), at, 0.05)
    return lowpass(b, 3500)


EXHALE = [dict(m=1.00, tau=0.14, lo=200.0, hi=1500.0, pad=170.0), dict(m=1.07, tau=0.13, lo=240.0, hi=1700.0, pad=190.0),
          dict(m=0.93, tau=0.17, lo=180.0, hi=1350.0, pad=150.0), dict(m=1.03, tau=0.15, lo=220.0, hi=1600.0, pad=210.0)]


def cradle_exhale(take, r):
    """The breath going out (keyed at breathe tick 50, where the crest lands on the column): the padded crest settling at
    3 ms (a very soft leather thump about 150-210 Hz and the exhaust flap's tap), then the air let out through the
    relief valve as a soft sigh (noise about 200-1700 Hz, darker than the in-breath, up in 30 ms and decaying with a
    130-170 ms time constant, its band falling as it empties, so it is gone before the next in-breath 650 ms on), and the 0.4 px bounce landing again at about 100 ms
    (tick 52), barely there."""
    p = EXHALE[take - 1]
    m = p['m']
    dur = round(7 * p['tau'] + 0.25, 3)
    b = buf(dur)
    t = tt(dur)
    place(b, thunk(r, p['pad'] * 1.3, p['pad'], 0.030, 0.012), 0.003, 0.35)
    place(b, norm(noise_burst(200, 1200, 0.008, r, attack=0.001)), 0.003, 0.25)
    t0 = np.maximum(t - 0.01, 0)
    e = (t > 0.01) * np.clip(t0 / 0.03, 0, 1) * np.exp(-np.maximum(t0 - 0.03, 0) / p['tau'])
    air = sweep_bandpass(noise(dur, r), p['lo'] * m * (0.75 + 0.35 * e), p['hi'] * m * (0.6 + 0.5 * e)) * e
    b += 0.90 * norm(air)
    place(b, thunk(r, p['pad'] * 1.3, p['pad'], 0.020, 0.010), 0.103, 0.10)
    return lowpass(b, 3000)


# ======================================================================= the Body Printer

START = [dict(m=1.00, grit=0.40, air=0.16, top=0.50), dict(m=1.08, grit=0.52, air=0.24, top=0.45),
         dict(m=0.93, grit=0.32, air=0.12, top=0.56)]


def printer_start(take, r):
    """The booth whirring into life (print tick 0; unstall tick 1 at pitch 1.1): the contactor pulls in at 3 ms (a
    relay click with its release), the carriage motor spins up over about half a second (the house motor: whine,
    commutator grit gated by the rotor, air; speed rising like u^0.6), the drive gear engages at 140 ms (a small tooth
    and a steel tick), and the motor settles and fades from about 0.55 s as the whirr loop takes over (it starts at
    volume 0 and is chased up 0.2 a tick)."""
    p = START[take - 1]
    m = p['m']
    dur = 0.95
    b = buf(dur)
    t = tt(dur)
    up = np.clip((t - 0.01) / p['top'], 0, 1) ** 0.6 * (t > 0.01)
    fade = np.where(t < 0.55, 1.0, np.exp(-(t - 0.55) / 0.07))
    b += motor(t, np.clip(up, 0, 1), r, 180 * m, 520 * m, grit=p['grit'], air=p['air'], whine=0.26) * fade
    place(b, relay_click(r, 1800 * m, release_after=0.028), 0.003, 0.45)
    place(b, tooth(r, 2700 * m), 0.14, 0.25)
    place(b, small_tick(r, 2200 * m, 0.005), 0.15, 0.15)
    return b


TAP = [dict(m=1.00, d=1.00, th=0.30), dict(m=1.10, d=0.80, th=0.20), dict(m=0.90, d=1.20, th=0.40),
       dict(m=1.24, d=0.90, th=0.15)]


def printer_tap(take, r):
    """A light tap of the copper print head on the brass bar (the head dips half a pixel, easeInQuad, into the
    contact): a strike, a small copper body about 1.9 kHz (plate ratios, 8-12 ms) and a light thunk. Gone in about
    80 ms, so taps 5 ticks apart stay apart."""
    p = TAP[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.18)
    hit = mix(0.40 * strike(r, 2500), 0.60 * norm(modal(1900 * m, [1, 1.59, 2.14, 3.9], [1, 0.5, 0.35, 0.15],
                                                         [0.012 * d, 0.008 * d, 0.006 * d, 0.004 * d], r, 0.02)),
              p['th'] * thunk(r, 300 * m, 220 * m, 0.012, 0.006))
    place(b, norm(hit), 0.003, 1.0)
    return b


ZIP = [dict(m=1.00, rate=(70.0, 260.0), air=0.20), dict(m=1.12, rate=(60.0, 300.0), air=0.12)]


def printer_zip(take, r):
    """The print head's quick measuring zip across the bar (print ticks 972-978, easeInOutCubic, 9 px): a tape
    measure's zip. The pawl lets go at 3 ms (a tick), then tiny teeth (2.5-7 kHz ticks and a 2.6 kHz mode) at a rate
    that follows the speed (60-300 a second), a faint air of the head, and the stop tick as it arrives at the right
    end at 300 ms, with a settle tick 45 ms on."""
    p = ZIP[take - 1]
    m = p['m']
    dur = 0.55
    b = buf(dur)
    t = tt(dur)
    u = np.clip((t - 0.005) / 0.295, 0, 1)
    s = np.where((t > 0.005) & (t < 0.30), np.where(u < 0.5, 4 * u ** 3, 1 - (-2 * u + 2) ** 3 / 2), 0)
    s = np.gradient(s) * SR * 0.295 / 1.5  # the speed of easeInOutCubic, peak 1
    s = np.clip(s, 0, 1)
    place(b, small_tick(r, 3000 * m, 0.003), 0.003, 0.35)
    at = 0.02
    while at < 0.29:
        sp = float(np.interp(at, t, s))
        if sp > 0.05:
            place(b, tooth(r, 2600 * m * r.uniform(0.97, 1.03)), at, 0.25 + 0.45 * sp)
        at += 1.0 / (p['rate'][0] + p['rate'][1] * sp)
    b += p['air'] * norm(bandpass(noise(dur, r), 1500 * m, 7000 * m) * s ** 1.5)
    place(b, knock(r, 2000 * m, 0.7, th=(320, 220), th_gain=0.2, nz=0.25, band=(1500, 6000), hp=2500), 0.30, 0.75)
    place(b, small_tick(r, 2300 * m, 0.004), 0.345, 0.15)
    return b


RATCHET = [dict(f=2800.0, body=1700.0, sec=0.018), dict(f=3200.0, body=2000.0, sec=0.024),
           dict(f=2450.0, body=1450.0, sec=0.014), dict(f=3600.0, body=2300.0, sec=0.030)]


def printer_ratchet(take, r):
    """One click of the Collar Cam's ratchet (print ticks 1000, 1002, 1004 at pitch 1.0, 1.06, 1.12): the pawl drops at
    4 ms (a tooth, a small brass body with 6-8 ms decays and a short noise band) and bounces once 14-30 ms later (a
    quieter tooth). Gone well inside 100 ms, so three clicks 2 ticks apart stay three."""
    p = RATCHET[take - 1]
    b = buf(0.15)
    clank = mix(tooth(r, p['f']), 0.45 * norm(modal(p['body'], [1, 2.756, 5.404], [1, 0.5, 0.3], [0.008, 0.006, 0.004], r)),
                0.25 * norm(noise_burst(2000, 7000, 0.005, r)))
    place(b, norm(clank), 0.004, 1.0)
    place(b, tooth(r, p['f'] * 1.05), 0.004 + p['sec'], 0.30)
    return b


DEVELOP = [dict(f0=880.0, f1=1760.0, hum=110.0, L=1.30, siz=0.10), dict(f0=830.0, f1=1660.0, hum=100.0, L=1.34, siz=0.14)]


def printer_develop(take, r):
    """The develop starting (print tick 1008; then the bar sinks for 6.6 s as the develop line): the develop lamp's
    relay clicks in at 3 ms, the booth's transformer hums up (100-110 Hz and its harmonics, swelling over 0.3 s), and a
    photographic whine rises an octave (about 880 -> 1760 Hz, a soft tone with two harmonics and a 5 Hz shimmer) over
    1.0-1.1 s, with a faint chemical sizzle (3-8 kHz) under it; it all fades by 1.3-1.4 s (the sound marks the start,
    not the whole develop)."""
    p = DEVELOP[take - 1]
    L = p['L']
    dur = round(L + 0.30, 3)
    b = buf(dur)
    t = tt(dur)
    place(b, relay_click(r, 1700, release_after=0.032), 0.003, 0.40)
    env = np.clip((t - 0.01) / 0.30, 0, 1) * np.where(t < L - 0.35, 1.0, np.exp(-(t - (L - 0.35)) / 0.10))
    env = smooth(env * (t > 0.01), 10)
    u = np.clip((t - 0.01) / (L - 0.25), 0, 1)
    fw = p['f0'] * (p['f1'] / p['f0']) ** (u ** 1.5)
    ph = 2 * np.pi * np.cumsum(fw) / SR
    shim = 1 + 0.06 * np.sin(2 * np.pi * 5 * t)
    whine = (np.sin(ph) + 0.35 * np.sin(2 * ph) + 0.12 * np.sin(3 * ph)) * shim * np.clip(u * 1.6, 0, 1)
    hum = osc_harm(np.full(len(t), p['hum']) * (1 + 0.15 * u), 8, r, tilt=1.2)
    siz = bandpass(noise(dur, r), 3000, 8000) * (0.5 + 0.5 * np.abs(np.sin(2 * np.pi * 13 * t)))
    b += env * (0.42 * norm(whine) + 0.30 * norm(hum) + p['siz'] * norm(siz))
    return b


THUNK = [dict(m=1.00, d=1.00, ch=0.016), dict(m=1.09, d=0.80, ch=0.020), dict(m=0.91, d=1.20, ch=0.013)]


def printer_thunk(take, r):
    """The brass print bar landing on the booth floor (print tick 1140, pitch 0.9; the refusal's slam at tick 6, pitch
    1.1): a strike, the bar's body (free-bar modes about 750-900 Hz, 50/30/18 ms, and a 1-6 kHz noise band), the weight (a
    thunk 140 -> 85 Hz), the sleeve rattling on its steel post 13-20 ms later (two tiny ticks), and a settle tick
    about 50 ms on."""
    p = THUNK[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.55)
    bar = mix(0.45 * strike(r, 1800),
              0.50 * norm(modal(820 * m, [1, 2.756, 5.404, 8.933], [1, 0.6, 0.4, 0.2], [0.050 * d, 0.030 * d, 0.018 * d, 0.010 * d], r, 0.01)),
              0.40 * norm(noise_burst(1000 * m, 6000 * m, 0.020 * d, r)),
              0.30 * thunk(r, 140, 85, 0.040, 0.018))
    place(b, norm(bar), 0.003, 1.0)
    for k in range(2):
        place(b, small_tick(r, 2400 * m * (1 + 0.07 * k), 0.004), 0.003 + p['ch'] * (k + 1), 0.14 / (k + 1))
    place(b, small_tick(r, 1900 * m, 0.005), 0.055, 0.10)
    return b


UNLATCH = [dict(m=1.00, creak=1500.0, cg=0.14, end=0.50), dict(m=1.20, creak=1900.0, cg=0.15, end=0.50),
           dict(m=0.88, creak=1300.0, cg=0.18, end=0.57)]


def printer_unlatch(take, r):
    """The glass door's latch letting go (deliver tick 2): the lever clacks back at 3 ms (a relay-like click and a small
    brass body about 1.6 kHz), the latch spring releases 25 ms later (a lighter tick), the door is pulled a hair in
    against its seal (a faint rubber squeak at 150 ms, tick 5), and the brass hinge creaks faintly as the swing starts
    (ticks 5-13) over the faint air of the moving glass, fading before the stop (printer.door_stop at tick 15)."""
    p = UNLATCH[take - 1]
    m = p['m']
    dur = 0.80
    b = buf(dur)
    place(b, mix(0.7 * relay_click(r, 1900 * m, release_after=0.025), 0.45 * brass_ring(r, 1600 * m, 0.9)), 0.003, 1.0)
    place(b, sweep_sine(900 * m, 1150 * m, 0.012, 0.010), 0.15, 0.06)
    creaks(r, b, 0.17, p['end'], p['creak'] * m, gap=(0.020, 0.045), level=p['cg'])
    t = tt(dur)
    b += 0.03 * norm(bandpass(noise(dur, r), 400 * m, 2500 * m) * env_seg(t, 0.14, p['end'] + 0.06))
    return b


DOOR_STOP = [dict(m=1.00, d=1.00, buzz=3, gl=2600.0), dict(m=1.10, d=0.85, buzz=2, gl=2900.0),
             dict(m=0.85, d=1.30, buzz=2, gl=2200.0)]


def printer_door_stop(take, r):
    """The glass door reaching its stop (deliver tick 15, pitch 0.9; it overshoots to 104 degrees and eases back to 100
    by tick 19): the brass frame knocks the rubber stop at 3 ms (a soft thump, a damped frame body about 900 Hz), the
    glass rattles in its frame (short glass modes and 2-4 buzzes 9-12 ms apart), and a faint hinge tick as it settles
    back at 200 ms."""
    p = DOOR_STOP[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.55)
    place(b, knock(r, 900 * m, d, th=(200, 130), th_gain=0.45, nz=0.25, band=(600, 4000), hp=1200), 0.003, 0.85)
    place(b, norm(glass_modes(r, p['gl'] * m, decays=(0.020 * d, 0.012 * d, 0.008 * d, 0.005 * d))), 0.005, 0.30)
    for k in range(p['buzz']):
        place(b, norm(mix(noise_burst(1500, 6000, 0.002, r), 0.5 * modal(p['gl'] * m * 1.05, [1, 2.7], [1, 0.3], [0.004, 0.003], r))),
              0.015 + 0.0105 * k, 0.20 * 0.7 ** k)
    place(b, small_tick(r, 2000 * m, 0.005), 0.20, 0.10)
    return b


STEP = [dict(m=1.00, slap=0.030, plate=640.0, sg=0.60), dict(m=1.10, slap=0.036, plate=720.0, sg=0.50),
        dict(m=0.90, slap=0.026, plate=580.0, sg=0.70), dict(m=1.05, slap=0.042, plate=800.0, sg=0.55)]


def printer_step(take, r):
    """A bare footstep on the booth's metal floor (deliver ticks 24, 30, 35 at pitch 1.0, 0.95, 0.85): the heel lands
    at 3 ms (a soft low thud, a thunk 110 -> 70 Hz and a muffled noise body to 700 Hz), the floor's sheet steel answers
    (plate modes about 520-840 Hz, 40-55 ms, under the thud so it is a floor, not a gong), and the ball of the foot comes down
    26-42 ms later with a small skin slap (800-3500 Hz, 6 ms) and a second soft plate touch."""
    p = STEP[take - 1]
    m = p['m']
    b = buf(0.50)
    place(b, thunk(r, 110 * m, 70 * m, 0.030, 0.012), 0.003, 0.40)
    place(b, norm(lowpass(bandpass(noise(0.15, r), 60, 700), 900) * exp_env(0.15, 0.015, 0.002)), 0.003, 0.45)
    plate = modal(p['plate'] * m, [1, 1.59, 2.14, 3.9], [1, 0.6, 0.4, 0.2], [0.055, 0.040, 0.025, 0.015], r, 0.01)
    place(b, norm(plate), 0.004, 0.50)
    place(b, norm(noise_burst(800 * m, 3500 * m, 0.006, r, attack=0.001)), 0.003 + p['slap'], p['sg'])
    place(b, norm(modal(p['plate'] * m * 1.04, [1, 1.59, 2.14], [1, 0.5, 0.3], [0.035, 0.025, 0.015], r)), 0.004 + p['slap'], 0.12)
    return b


SWING = [dict(m=1.00, creak=1300.0, cg=0.16, air=0.55), dict(m=1.12, creak=1500.0, cg=0.12, air=0.45)]


def printer_door_swing(take, r):
    """The glass door swinging back shut (shut tick 0): a soft knock as it leaves the stop at 3 ms, a small wind-up
    outward (to 103 degrees by tick 3), then the swing to 0 over ticks 3-15, easeInQuad, accelerating: the brass hinge
    creaks (stick-slip pings at about 1.3-1.5 kHz, faster as it speeds up) and the glass panel pushes air (a swish
    300-2500 Hz rising with the speed). It lets go in 25 ms at 750 ms, where printer.door_shut is the contact."""
    p = SWING[take - 1]
    m = p['m']
    dur = 1.00
    b = buf(dur)
    t = tt(dur)
    place(b, knock(r, 850 * m, 0.7, th=(200, 140), th_gain=0.3, nz=0.2, band=(500, 3000), hp=1500), 0.003, 0.45)
    s = 0.15 * env_seg(t, 0.0, 0.15) + release(env_seg(t, 0.15, 0.75, 'in'), t, 0.75, 0.025) * np.clip((t - 0.15) / 0.05, 0, 1)
    air = sweep_bandpass(noise(dur, r), 300 * m * (0.8 + 0.5 * s), 1500 * m * (0.8 + 0.8 * s)) * s ** 1.4
    b += p['air'] * norm(air)
    at = 0.05
    while at < 0.73:
        sp = float(np.interp(at, t, s))
        place(b, norm(modal(p['creak'] * m * r.uniform(0.95, 1.05), [1, 2.3], [1, 0.3], [0.005, 0.003], r)), at,
              p['cg'] * (0.3 + 0.7 * sp) * r.uniform(0.5, 1.0))
        at += r.uniform(0.014, 0.030) / (0.6 + 0.8 * sp)
    return b


SHUT = [dict(m=1.00, d=1.00, gl=2300.0, bounce=0.250), dict(m=1.09, d=0.80, gl=2600.0, bounce=0.240),
        dict(m=0.92, d=1.20, gl=2100.0, bounce=0.262)]


def printer_door_shut(take, r):
    """The glass door in its brass frame shutting (shut tick 15, easeInQuad into the frame): a strike, the brass frame's
    body (free-bar modes about 700 Hz and a plate about 1.1 kHz, the house iron body made brassier), the weight (a
    thunk 150 -> 90 Hz), the glass pane rattling in its frame (short glass modes about 2.1-2.6 kHz and three buzzes),
    the catch taking it 20 ms later (one tooth), and the 2-degree bounce (ticks 17-20) closing again at about 250 ms,
    soft."""
    p = SHUT[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.70)
    body = iron_body(r, 700 * m, 1100 * m, 0.55 * d, noise_gain=0.30, band_scale=m)
    place(b, norm(mix(0.40 * strike(r, 1500), norm(body), 0.45 * thunk(r, 150, 90, 0.035, 0.015))), 0.003, 1.0)
    place(b, norm(glass_modes(r, p['gl'] * m, decays=(0.025 * d, 0.015 * d, 0.010 * d, 0.006 * d))), 0.004, 0.30)
    for k in range(3):
        place(b, norm(noise_burst(1500, 6000, 0.002, r)), 0.012 + 0.010 * k, 0.15 * 0.7 ** k)
    place(b, tooth(r, 2500 * m), 0.023, 0.25)
    place(b, norm(mix(knock(r, 750 * m, 0.8, th_gain=0.3), 0.3 * norm(glass_modes(r, p['gl'] * m * 1.02, decays=(0.010, 0.006, 0.004, 0.003))))),
          p['bounce'], 0.16)
    return b


def printer_ready(take, r):
    """The booth's little ready ping when all four materials are in (loaded tick 7, the head arriving at the centre): a
    small coin-op bell struck once at E6 (1318.5 Hz; the company's motif note, not its FM voice, which stays for the
    company talking), bell partials 1, 2, 2.4, 3, 4.2 (the upper ones short), a light strike, 0.6-0.7 s heard."""
    b = buf(1.20)
    place(b, strike(r, 3000), 0.003, 0.25)
    place(b, norm(bell_modes(r, 1318.5, (0.155, 0.07, 0.045, 0.025, 0.012))), 0.003, 1.0)
    return b


WIND = [dict(m=1.00, grit=0.42, air=0.16, tau=0.22), dict(m=1.08, grit=0.54, air=0.22, tau=0.18)]


def printer_wind_down(take, r):
    """The mechanism winding down for lack of power (stall tick 0; the bar's free end sags 15 degrees over 16 ticks):
    the contactor drops out at 3 ms (a relay click), the carriage motor runs down (the house motor, speed falling from 1
    with an 0.18-0.22 s time constant, its whine dropping with it), the bar's sleeve creaks on its post as the end sags
    (slow stick-slip pings, 0.15-0.75 s) and settles with a soft knock at 0.80 s (tick 16)."""
    p = WIND[take - 1]
    m = p['m']
    dur = 1.25
    b = buf(dur)
    t = tt(dur)
    s = np.exp(-np.maximum(t - 0.01, 0) / p['tau']) * np.clip(t / 0.01, 0, 1)
    s = np.where(t < 0.9, s, s * np.exp(-(t - 0.9) / 0.03))
    b += motor(t, s, r, 180 * m, 520 * m, grit=p['grit'], air=p['air'], whine=0.26)
    place(b, relay_click(r, 1700 * m, release_after=0.030), 0.003, 0.40)
    creaks(r, b, 0.15, 0.75, 700 * m, gap=(0.04, 0.08), level=0.10, d=0.006)
    place(b, knock(r, 650 * m, 1.0, th=(170, 110), th_gain=0.45, nz=0.2, band=(500, 3000), hp=1200), 0.80, 0.30)
    return b


WHIRR_L = 3.0      # s
WHIRR_F0 = 293.333  # Hz, 880 whole cycles in 3.0 s; heard 235-293 Hz at pitch 0.8-1.0


def printer_whirr(take, r):
    """The print carriage's whirr while printing, a seamless 3.0 s loop (played at volume = work rate and pitch 0.8 +
    0.2 x rate, so full rate is pitch 1.0). A small drive motor's whine (293.33 Hz and 7 harmonics falling as 1/k^1.3,
    band-limited 250-3500 Hz) kept under its brush grit (2.5-7 kHz noise gated by the commutator at f0/4) and its air,
    a soft gear-mesh texture (1.2-4 kHz noise gated at 2 f0 / 3) and a low belt hum at f0/2; the load wanders slowly
    (one and two swells a loop, never on the shuttle's 2 s rhythm, which drifts against the loop under brown-out).
    Every periodic layer completes whole cycles in the 3.0 s (880, 220, 587, 440, 1, 2)."""
    n = int(round(WHIRR_L * SR))
    t = np.arange(n) / SR
    f0 = 880 / WHIRR_L
    ph = 2 * np.pi * f0 * t + 0.002 * f0 * np.sin(2 * np.pi * 2 * t / WHIRR_L)
    whine = sum((1 / h ** 1.3) * np.sin(h * ph + r.uniform(0, 2 * np.pi)) for h in range(1, 9))
    whine = cband(whine, 250, 3500)
    gate = (0.5 + 0.5 * np.cos(2 * np.pi * (220 / WHIRR_L) * t)) ** 4
    grit = cband(cnoise(n, r), 2500, 7000) * gate
    mesh_gate = (0.5 + 0.5 * np.cos(2 * np.pi * (587 / WHIRR_L) * t)) ** 2
    mesh = cband(cnoise(n, r), 1200, 4000) * mesh_gate
    air = cband(cnoise(n, r), 600, 5000)
    belt = np.sin(2 * np.pi * (440 / WHIRR_L) * t + r.uniform(0, 2 * np.pi))
    load = 0.85 + 0.10 * np.sin(2 * np.pi * t / WHIRR_L + 0.7) + 0.05 * np.sin(2 * np.pi * 2 * t / WHIRR_L + 2.1)
    return load * (0.26 * norm(whine) + 0.30 * norm(grit) + 0.14 * norm(mesh) + 0.16 * norm(air) + 0.14 * norm(belt))


# ======================================================================= the Gene Splicer

STITCH = [dict(m=1.00, body=480.0, hook=0.018, tsk=0.030, bg=0.55), dict(m=1.09, body=540.0, hook=0.022, tsk=0.036, bg=0.45),
          dict(m=0.88, body=400.0, hook=0.015, tsk=0.026, bg=0.70), dict(m=1.25, body=660.0, hook=0.026, tsk=0.042, bg=0.35)]


def splicer_stitch(take, r):
    """One stitch (keyed on the needle's contact, tick 5 of each 10-tick stitch; pitches 0.95-1.05, 0.9 after a
    stutter, 0.85 for the coast's last stitch): the needle bar reaching the bottom of its stroke at 3 ms (a soft
    'chk': a light strike, a small steel mode about 2.4 kHz with 4-6 ms decays and the cast-iron head's damped body
    about 400-660 Hz with a small thunk), the shuttle hook catching the thread 15-26 ms later (a quiet tooth) and the
    thread drawing tight 26-42 ms in (a 4 ms tsk). Low-passed at 6 kHz and short (under 0.12 s): it repeats twice a
    second for a minute, so it must stay dull and small."""
    p = STITCH[take - 1]
    m = p['m']
    b = buf(0.22)
    hit = mix(0.25 * strike(r, 3000), 0.40 * norm(modal(2400 * m, [1, 1.6], [1, 0.4], [0.006, 0.004], r, 0.02)),
              p['bg'] * norm(modal(p['body'], [1, 2.3, 3.6], [1, 0.4, 0.2], [0.012, 0.008, 0.005], r, 0.02)),
              0.30 * thunk(r, 210, 150, 0.014, 0.007))
    place(b, norm(hit), 0.003, 1.0)
    place(b, tooth(r, 3000 * m), 0.003 + p['hook'], 0.18)
    place(b, norm(noise_burst(3000, 8000, 0.004, r, attack=0.001)), 0.003 + p['tsk'], 0.10)
    return lowpass(b, 6000)


STUTTER = [dict(m=1.00, n=4, gap=0.022, g=0.15), dict(m=1.12, n=3, gap=0.028, g=0.165), dict(m=0.85, n=5, gap=0.018, g=0.21)]


def splicer_stutter(take, r):
    """A stitch that catches (stutter keyed a tick into the hang: the needle stops a third of the way down for 2 ticks
    and punches late, played as splicer.stitch at pitch 0.9 four ticks on): the needle meets the snag at 3 ms (a tick
    and a small knock), the wheel's gear chatters against the jam (3-5 quick teeth 18-28 ms apart, each softer and a
    little lower), and the strand groans under the tension (a narrow 500-1500 Hz band swelling and gone in 150-210 ms), all
    gone before the late punch at 200 ms."""
    p = STUTTER[take - 1]
    m = p['m']
    dur = 0.40
    b = buf(dur)
    t = tt(dur)
    place(b, knock(r, 520 * m, 0.8, th=(220, 160), th_gain=0.3, nz=0.25, band=(800, 4000), hp=2500), 0.003, 0.70)
    for k in range(p['n']):
        place(b, tooth(r, 2600 * m * (1 - 0.03 * k)), 0.02 + p['gap'] * k, 0.45 * 0.8 ** k)
    g = env_seg(t, 0.01, p['g']) * (0.7 + 0.3 * np.sin(2 * np.pi * 37 * t))
    b += 0.30 * norm(bandpass(noise(dur, r) * g, 500 * m, 1500 * m, 4))
    return lowpass(b, 7000)


CLUTCH = [dict(m=1.00, slap=0.012, teeth=0.010), dict(m=1.12, slap=0.016, teeth=0.007)]


def splicer_clutch(take, r):
    """The clutch engaging (start tick 3, as the wheel's 30-degree rock back reaches its hold): the clutch knob's dog
    teeth meet at 3 ms (two teeth 7-10 ms apart and a strike), the cast-iron body answers (free-bar modes about 1 kHz,
    40 ms), the belt slaps taut 12-16 ms in (a low 150-800 Hz thwap) and the knob's detent settles 120 ms later."""
    p = CLUTCH[take - 1]
    m = p['m']
    b = buf(0.45)
    place(b, strike(r, 2000), 0.003, 0.40)
    place(b, tooth(r, 2600 * m), 0.003, 0.45)
    place(b, tooth(r, 2750 * m), 0.003 + p['teeth'], 0.35)
    place(b, norm(modal(1000 * m, [1, 2.756, 5.404], [1, 0.5, 0.25], [0.040, 0.024, 0.012], r, 0.01)), 0.004, 0.55)
    place(b, thunk(r, 160, 100, 0.025, 0.012), 0.004, 0.20)
    place(b, norm(noise_burst(150, 800, 0.020, r, attack=0.003)), 0.003 + p['slap'], 0.25)
    place(b, small_tick(r, 2200 * m, 0.006), 0.12, 0.20)
    return b


COAST = [dict(m=1.00, teeth=24, hum=220.0, air=0.25), dict(m=0.80, teeth=18, hum=190.0, air=0.15)]


def splicer_coast(take, r):
    """The wheel coasting down (stop/finish tick 0: one turn in 20 ticks, easeOutQuad from full speed, then a 3-degree
    rock back at 23 and a settle at 26): the drive lets go at 3 ms (a light tick), then the mechanism runs down over
    1.0 s, speed falling linearly to 0: gear grit gated at the teeth rate (18-24 teeth a turn, 36-48 a second falling
    to 0), the shuttle's soft hum (190-220 Hz times the speed), a little air; the pawl clicks back on the rock at 1.15 s
    and a settle tick on 1.30 s. The last stitch at tick 6 is splicer.stitch at 0.85."""
    p = COAST[take - 1]
    m = p['m']
    dur = 1.55
    b = buf(dur)
    t = tt(dur)
    s = np.clip(1 - (t - 0.005) / 1.0, 0, 1) * (t > 0.005)
    s = smooth(s, 15)
    rot = 2.0 * s  # turns a second
    gate = (0.5 + 0.5 * np.cos(2 * np.pi * np.cumsum(p['teeth'] * rot) / SR)) ** 6
    grit = bandpass(noise(dur, r), 1500 * m, 6000 * m) * gate * s
    hum = osc_harm(p['hum'] * m * (0.5 + 0.5 * s), 5, r, tilt=1.4) * s ** 1.5
    air = bandpass(noise(dur, r), 400, 3000) * s ** 1.5
    b += 0.45 * norm(grit) + 0.25 * norm(hum) + p['air'] * norm(air)
    place(b, small_tick(r, 2400 * m, 0.004), 0.003, 0.30)
    place(b, mix(tooth(r, 2500 * m), 0.5 * norm(modal(800 * m, [1, 2.756], [1, 0.4], [0.015, 0.010], r))), 1.15, 0.35)
    place(b, small_tick(r, 2100 * m, 0.004), 1.30, 0.15)
    return b


HIC = [dict(m=1.00, blip=(520.0, 780.0), land=0.200, sp=(0.000, 0.011, 0.020)),
       dict(m=1.20, blip=(600.0, 900.0), land=0.186, sp=(0.000, 0.009, 0.018)),
       dict(m=0.82, blip=(460.0, 690.0), land=0.214, sp=(0.000, 0.013, 0.025))]


def splicer_hic(take, r):
    """A hiccup at the finish (keyed on the kick, finish tick 31 and 41 at pitch 1.0 and 1.12): the wheel is kicked back
    30 degrees (easeInExpo) and the needle jolts 3 px: a sharp 'hk' at 3 ms (a strike, two pawl teeth 7 ms apart and a
    short cast-iron body about 650 Hz), with the tiny comic catch on top: a 25 ms upward blip (about 520 -> 780 Hz, a
    soft sine, 8 dB under the clack: a hiccup's glottal catch, never a boing) and a breath of noise. Then the three
    spools, hopped 2 px, land on their pins at 186-214 ms (tick 5 of the hiccup is 200 ms): three small wooden taps 9-25 ms apart."""
    p = HIC[take - 1]
    m = p['m']
    b = buf(0.50)
    place(b, strike(r, 2000), 0.003, 0.40)
    place(b, tooth(r, 2700 * m), 0.003, 0.45)
    place(b, tooth(r, 2900 * m), 0.010, 0.30)
    place(b, norm(modal(650 * m, [1, 2.756, 5.404], [1, 0.5, 0.25], [0.022, 0.014, 0.008], r, 0.01)), 0.004, 0.55)
    place(b, thunk(r, 170, 110, 0.020, 0.010), 0.004, 0.25)
    bl = tt(0.12)
    f = p['blip'][0] + (p['blip'][1] - p['blip'][0]) * (1 - np.exp(-bl / 0.010))
    blip = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.clip(bl / 0.004, 0, 1) * np.exp(-bl / 0.014)
    place(b, blip, 0.012, 0.35)
    place(b, norm(noise_burst(900, 2500, 0.010, r, attack=0.002)), 0.012, 0.12)
    for k, dt in enumerate(p['sp']):
        place(b, norm(mix(modal(1500 * m * (1 + 0.08 * k), [1, 2.7, 4.9], [1, 0.4, 0.2], [0.008, 0.005, 0.003], r),
                          0.4 * thunk(r, 280, 200, 0.010, 0.005))), p['land'] + dt, 0.22)
    return b


EJECT = [dict(m=1.00, stop=0.150, settle=0.350), dict(m=1.10, stop=0.140, settle=0.335)]


def splicer_eject(take, r):
    """The serum cassette popping up out of its deck (finish T+2, at the bottom of a half-pixel dip): the deck's spring
    latch lets go at 3 ms (a click and a short damped steel-leaf twang about 900 Hz, 20 ms: a spring, not a boing),
    the cassette shoots up (easeOutCubic: a short friction slide, 800-4000 Hz noise falling as (1 - u)^2), meets its
    guide posts' catch at 140-150 ms (a soft bakelite tap) and settles back half a pixel at 335-350 ms (tick T+9, a
    softer tap)."""
    p = EJECT[take - 1]
    m = p['m']
    dur = 0.65
    b = buf(dur)
    t = tt(dur)
    place(b, relay_click(r, 2000 * m, release_after=0.022), 0.003, 0.45)
    place(b, norm(modal(900 * m, [1, 2.76], [1, 0.3], [0.020, 0.010], r)), 0.004, 0.30)
    s = env_seg(t, 0.006, p['stop'], 'out') * np.clip((t - 0.006) / 0.006, 0, 1)
    b += 0.30 * norm(bandpass(noise(dur, r), 800 * m, 4000 * m) * s)
    place(b, knock(r, 1300 * m, 0.8, th=(300, 200), th_gain=0.25, nz=0.3, band=(800, 5000), hp=2000), p['stop'], 0.60)
    place(b, knock(r, 1350 * m, 0.6, th=(300, 200), th_gain=0.2, nz=0.2, band=(800, 5000), hp=2000), p['settle'], 0.22)
    return b


THREAD = [dict(m=1.00, flut=(40.0, 16.0), tau=0.060), dict(m=1.12, flut=(48.0, 20.0), tau=0.050),
          dict(m=0.90, flut=(34.0, 13.0), tau=0.075)]


def splicer_thread(take, r):
    """Thread drawn off a spool as a gene is picked (pick tick 1: the spool lifts 2.5 px by tick 4): the thread snaps
    taut at 3 ms (a tiny tick), then a quick fine zip of thread over the guide (noise 2.5-9 kHz, up in 8 ms and decaying
    with a 50-75 ms time constant), fluttering with the spool's turns (40 -> 16 a second as it slows)."""
    p = THREAD[take - 1]
    m = p['m']
    dur = 0.65
    b = buf(dur)
    t = tt(dur)
    place(b, small_tick(r, 3500 * m, 0.003), 0.003, 0.35)
    t0 = np.maximum(t - 0.006, 0)
    e = (t > 0.006) * np.clip(t0 / 0.008, 0, 1) * np.exp(-t0 / p['tau'])
    fr = p['flut'][1] + (p['flut'][0] - p['flut'][1]) * np.exp(-t0 / 0.08)
    fl = 0.55 + 0.45 * np.sin(2 * np.pi * np.cumsum(fr) / SR) ** 2
    b += 0.85 * norm(bandpass(noise(dur, r), 2500 * m, 9000 * m) * e * fl)
    return b


SEAT = [dict(m=1.00, d=1.00, bounce=0.065), dict(m=1.11, d=0.80, bounce=0.058), dict(m=0.90, d=1.20, bounce=0.072)]


def splicer_seat(take, r):
    """The spool seating back on its pin (pick tick 8, easeInQuad into the contact): a small wooden spool's knock at
    3 ms (wood modes about 1.4 kHz, ratios 1, 2.7, 4.9, 6-10 ms, a little thunk) with its brass flange's tick, and the
    bounce touching down again 58-72 ms later, softer."""
    p = SEAT[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.25)

    def tap(f, dd):
        return norm(mix(0.30 * strike(r, 2500),
                        0.60 * norm(modal(f, [1, 2.7, 4.9], [1, 0.4, 0.2], [0.010 * dd, 0.006 * dd, 0.004 * dd], r, 0.02)),
                        0.30 * thunk(r, 280, 200, 0.012, 0.006)))
    place(b, tap(1400 * m, d), 0.003, 1.0)
    place(b, small_tick(r, 2900 * m, 0.003), 0.005, 0.15)
    place(b, tap(1450 * m, d * 0.7), 0.003 + p['bounce'], 0.25)
    return b


def splicer_bell(take, r):
    """The machine's little bell as the strand reaches the next stage (server, four times a job): a small sewing-room
    bell struck once at C7 (2093 Hz, the motif's home note two octaves up), bell partials 1, 2, 2.4, 3, 4.2 with the
    upper ones short, a light strike and the clapper's tiny second touch 30 ms later; 0.6-0.7 s heard, 2.5 LU under
    feedback.done and an octave and more above it, so it never reads as the company's chime."""
    b = buf(1.10)
    place(b, strike(r, 4000), 0.003, 0.20)
    place(b, norm(bell_modes(r, 2093.0, (0.15, 0.06, 0.035, 0.02, 0.01))), 0.003, 1.0)
    place(b, norm(bell_modes(r, 2093.0, (0.05, 0.03, 0.02, 0.012, 0.008))), 0.033, 0.12)
    return b


# ---------------------------------------------------------------- references (measured as the game plays them)

PISTON_IN = dict(event='block.piston.contract', file='tile__piston__in.ogg', vol=0.5, pitch=0.675)
SPYGLASS = dict(event='item.spyglass.use', file='item__spyglass__use.ogg', vol=1.0, pitch=1.0)
FIZZ = dict(event='block.fire.extinguish', file='random__fizz.ogg', vol=0.5, pitch=2.0)
IRON_TD_OPEN = dict(event='block.iron_trapdoor.open', file='block__iron_trapdoor__open1.ogg', vol=0.9, pitch=0.95)
IRON_TD_CLOSE = dict(event='block.iron_trapdoor.close', file='block__iron_trapdoor__close1.ogg', vol=0.9, pitch=0.95)
IRON_DOOR_OPEN = dict(event='block.iron_door.open', file='block__iron_door__open1.ogg', vol=0.9, pitch=0.95)
IRON_DOOR_CLOSE = dict(event='block.iron_door.close', file='block__iron_door__close2.ogg', vol=0.9, pitch=0.95)
COPPER_TD_CLOSE = dict(event='block.copper_trapdoor.close', file='block__copper_trapdoor__toggle1.ogg', vol=1.0, pitch=0.95)
COPPER_DOOR_OPEN = dict(event='block.copper_door.open', file='block__copper_door__toggle1.ogg', vol=1.0, pitch=0.95)
WOOD_TD_OPEN = dict(event='block.wooden_trapdoor.open', file='block__wooden_trapdoor__open1.ogg', vol=0.9, pitch=0.95)
LANTERN = dict(event='block.lantern.place', file='block__lantern__place1.ogg', vol=1.0, pitch=0.88)
XYLO = dict(event='block.note_block.xylophone', file='note__xylobone.ogg', vol=1.0, pitch=1.0)
LEATHER_1 = dict(event='item.armor.equip_leather', file='item__armor__equip_leather1.ogg', vol=1.0, pitch=1.0)
LEATHER_2 = dict(event='item.armor.equip_leather', file='item__armor__equip_leather2.ogg', vol=1.0, pitch=1.0)
EQUIP_IRON = dict(event='item.armor.equip_iron', file='item__armor__equip_iron1.ogg', vol=1.0, pitch=1.0)
WOOL_PLACE = dict(event='block.wool.place', file='dig__cloth1.ogg', vol=1.0, pitch=0.8)
SNIFFING = dict(event='entity.sniffer.sniffing', file='mob__sniffer__sniffing1.ogg', vol=1.0, pitch=1.0)
VAULT_ACTIVATE = dict(event='block.vault.activate', file='block__vault__activate.ogg', vol=1.0, pitch=1.0)
BRUSHING = dict(event='item.brush.brushing.generic', file='item__brush__brushing_generic1.ogg', vol=1.0, pitch=1.0)
BEACON_AMBIENT = dict(event='block.beacon.ambient', file='block__beacon__ambient.ogg', vol=0.9, pitch=1.0)

BASE = dict(category='BLOCKS', attenuation=8, fixed_range=False, volume=1.0, ticks=None, muffled=False)
KEY_NOTE = ' Keyframe volume 1.0 (the file carries the loudness); the key\'s pitch is kept.'


def ev(**kw):
    d = dict(BASE)
    d.update(kw)
    return d


SOUNDS = [
    # ---- the Life Support Cradle
    ev(id='cradle.hose_swing', file='cradle/hose_swing', fn=cradle_hose_swing, takes=3, target=-29.0,
       cls='servo-class swish (light)', kind=('servo', 0.30), fade_ms=15, hit_ms=3, ref=LEATHER_2, pitch=[0.95, 1.05],
       subtitle_en=None, subtitle_pt=None,
       clip_key='dock(_c) key 2: cradle.hose_swing|0.3|1.0 -> cradle.hose_swing|1.0|1.0',
       play='Cradle clips dock and dock_c, key 2 (the top of the 4-degree lift): the unhook at 3 ms, the swish 50-300 ms accelerating into the valve; cradle.hose_click is the contact at key 8. No subtitle (part of docking; the click carries it).' + KEY_NOTE),
    ev(id='cradle.hose_click', file='cradle/hose_click', fn=cradle_hose_click, takes=3, target=-27.5, cls='latch (small brass click)',
       kind='latch', fade_ms=12, hit_ms=3, ref=IRON_TD_OPEN, pitch=[0.95, 1.05],
       subtitle_en='Hose clicks onto valve', subtitle_pt='Mangueira encaixa na válvula',
       clip_key='dock(_c) key 8: cradle.hose_click|0.5|1.0 -> cradle.hose_click|1.0|1.0',
       play='Cradle clips dock and dock_c, key 8 (the contact): the click at 3 ms, the detent 10-16 ms later, the bounce seats again at 100 ms (tick 10).' + KEY_NOTE),
    ev(id='cradle.blanket_whoosh', file='cradle/blanket_whoosh', fn=cradle_blanket_whoosh, takes=3, target=-27.5,
       cls='servo-class swish (cloth)', kind=('servo', 0.45), fade_ms=20, hit_ms=3, ref=LEATHER_1, pitch=[1.0, 1.0],
       pitch_per_play=[1.0, 1.1], subtitle_en=None, subtitle_pt=None,
       clip_key='dock(_c) key 13: cradle.blanket_whoosh|0.4|1.0 -> |1.0|1.0; undock(_c) key 7: cradle.blanket_whoosh|0.4|1.1 -> |1.0|1.1',
       play='Dock key 13 at pitch 1.0 (lift at 3 ms, the two carries 0-300 ms, the drop 300-450 ms into cradle.flump at key 22); undock key 7 at pitch 1.1 (the same 9-tick move: the file ends 41 ms before the flump at 16). No subtitle (cradle.flump carries it).' + KEY_NOTE),
    ev(id='cradle.flump', file='cradle/flump', fn=cradle_flump, takes=3, target=-28.5, cls='soft landing (wool)',
       kind='latch', fade_ms=15, hit_ms=3, ref=WOOL_PLACE, pitch=[1.0, 1.0], pitch_per_play=[1.0, 1.2],
       subtitle_en='Blanket flumps', subtitle_pt='Cobertor cai macio',
       clip_key='dock(_c) key 22: cradle.flump|0.5|1.0 -> |1.0|1.0; undock(_c) key 16: cradle.flump|0.4|1.2 -> |1.0|1.2',
       play='Dock key 22 (onto the seat rail) at pitch 1.0, undock key 16 (onto the arm) at pitch 1.2: the landing at 3 ms, the half-pixel bounce lands again at 100 ms (83 ms at 1.2).' + KEY_NOTE),
    ev(id='cradle.seal_pfff', file='cradle/seal_pfff', fn=cradle_seal_pfff, takes=3, target=-25.0, cls='hiss (small pfff)',
       kind=('servo', 0.30), fade_ms=25, hit_ms=3, ref=FIZZ, pitch=[0.95, 1.05],
       subtitle_en='Valve seal hisses', subtitle_pt='Vedação da válvula chia',
       clip_key='undock(_c) key 0: cradle.seal_pfff|0.5|1.0 -> cradle.seal_pfff|1.0|1.0',
       play='Cradle clips undock and undock_c, key 0 (with the cradle_puff particle at 1): the seal unsticks at 3 ms, the pfff decays over 0.3-0.4 s.' + KEY_NOTE),
    ev(id='cradle.hose_hang', file='cradle/hose_hang', fn=cradle_hose_hang, takes=3, target=-26.5, cls='latch (rubber on brass hook)',
       kind='latch', fade_ms=12, hit_ms=3, ref=LANTERN, pitch=[0.95, 1.05],
       subtitle_en='Hose hung back up', subtitle_pt='Mangueira volta ao gancho',
       clip_key='undock(_c) key 4: cradle.hose_hang|0.4|1.0 -> cradle.hose_hang|1.0|1.0',
       play='Cradle clips undock and undock_c, key 4 (the contact on the hook): the knock at 3 ms, the bounce knocks again at 100 ms (tick 6).' + KEY_NOTE),
    ev(id='cradle.inhale', file='cradle/inhale', fn=cradle_inhale, takes=4, target=-30.6, cls='breath (soft, repeating; tick class level)',
       kind='hiss', fade_ms=40, hit_ms=3, ref=SNIFFING, pitch=[0.96, 1.04],
       subtitle_en='Bellows breathe in', subtitle_pt='O fole puxa o ar',
       clip_key='breathe key 3: cradle.inhale|0.3|1.0 -> cradle.inhale|1.0|1.0',
       play='Cradle clip breathe (60 ticks, looped while a head is docked and powered), key 3: the flap at 3 ms, the in-breath swells to a peak 400-500 ms in and is gone by 0.9-1.06 s (the crest tops out at tick 22). Four takes; a random one each breath, and if the handler allows, never the same take twice running.' + KEY_NOTE),
    ev(id='cradle.exhale', file='cradle/exhale', fn=cradle_exhale, takes=4, target=-30.6, cls='breath (soft, repeating; tick class level)',
       kind='hiss', fade_ms=40, hit_ms=3, ref=SNIFFING, pitch=[0.96, 1.04],
       subtitle_en='Bellows breathe out', subtitle_pt='O fole solta o ar',
       clip_key='breathe key 50: cradle.exhale|0.35|1.0 -> cradle.exhale|1.0|1.0',
       play='Cradle clip breathe, key 50 (the crest landing on the column, easeInQuad): the soft landing and the flap at 3 ms, then the out-breath through the relief valve, gone in 0.6-0.8 s (before the next inhale at tick 63); the 0.4 px bounce touches at 100 ms (tick 52). The crest sinks over ticks 32-48 before this key; the file is built for the key at 50. Four takes, as for the inhale.' + KEY_NOTE),
    # ---- the Body Printer
    ev(id='printer.start', file='printer/start', fn=printer_start, takes=3, target=-27.0, cls='servo (motor spin-up)',
       kind=('servo', 0.75), fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[1.0, 1.0], pitch_per_play=[1.0, 1.1],
       attenuation=12, subtitle_en='Body Printer starts', subtitle_pt='Impressora de Corpos liga',
       clip_key='print key 0: printer.start|0.6|1.0 -> |1.0|1.0; unstall key 1: printer.start|0.4|1.1 -> |1.0|1.1',
       play='Printer clip print, key 0 (pitch 1.0), and unstall, key 1 (pitch 1.1, the recovery): the contactor at 3 ms, the spin-up over 0.5 s, the gear at 140 ms; it fades from 0.55 s as printer.whirr is chased up.' + KEY_NOTE),
    ev(id='printer.tap', file='printer/tap', fn=printer_tap, takes=4, target=-30.0, cls='tick (light tap)',
       kind='click', fade_ms=8, hit_ms=3, ref=WOOD_TD_OPEN, pitch=[1.0, 1.0], pitch_per_play=[1.2, 1.2, 1.1, 1.1, 0.9],
       subtitle_en='Print head taps', subtitle_pt='Cabeçote de impressão bate',
       clip_key='print keys 963, 968: printer.tap|0.4|1.2; 981, 986: |0.4|1.1; 1154: |0.3|0.9; unloaded key 8: |0.3|0.9 -> volume 1.0, same pitches',
       play='Printer clip print, keys 963, 968 (pitch 1.2), 981, 986 (1.1), 1154 (0.9), and unloaded key 8 (0.9): each tap at 3 ms, gone in about 80 ms. A random take each time.' + KEY_NOTE),
    ev(id='printer.zip', file='printer/zip', fn=printer_zip, takes=2, target=-30.0, cls='tick (light zip)',
       kind=('servo', 0.30), fade_ms=15, hit_ms=3, ref=SPYGLASS, pitch=[0.97, 1.03],
       subtitle_en='Print head measures', subtitle_pt='Cabeçote tira as medidas',
       clip_key='print key 972: printer.zip|0.5|1.0 -> printer.zip|1.0|1.0',
       play='Printer clip print, key 972: the pawl at 3 ms, the teeth following the easeInOutCubic speed, the stop tick at 300 ms (tick 978).' + KEY_NOTE),
    ev(id='printer.ratchet', file='printer/ratchet', fn=printer_ratchet, takes=4, target=-31.0, cls='ratchet tooth',
       kind='click', fade_ms=8, hit_ms=4, ref=SPYGLASS, pitch=[1.0, 1.0], pitch_per_play=[1.0, 1.06, 1.12],
       subtitle_en='Collar Cam ratchets', subtitle_pt='Catraca do colarinho estala',
       clip_key='print keys 1000, 1002, 1004: printer.ratchet|0.5|1.0, 1.06, 1.12 -> volume 1.0, same pitches',
       play='Printer clip print, keys 1000, 1002, 1004 at pitch 1.0, 1.06, 1.12: each click at 4 ms and gone inside 100 ms, so the three stay three. A different take each time if the handler allows.' + KEY_NOTE),
    ev(id='printer.develop', file='printer/develop', fn=printer_develop, takes=2, target=-26.0, cls='servo-class hum (rising)',
       kind='stinger', fade_ms=40, hit_ms=3, ref=VAULT_ACTIVATE, pitch=[1.0, 1.0], attenuation=12,
       subtitle_en='Body print develops', subtitle_pt='A impressão se revela',
       clip_key='print key 1008: printer.develop|0.6|1.0 -> printer.develop|1.0|1.0',
       play='Printer clip print, key 1008 (the wind-up\'s top; the bar then sinks to 1140): the relay at 3 ms, the hum and the rising whine over 1.0-1.1 s, faded by 1.3-1.4 s.' + KEY_NOTE),
    ev(id='printer.thunk', file='printer/thunk', fn=printer_thunk, takes=3, target=-23.5, cls='slam (bar on the floor)',
       kind='latch', fade_ms=12, hit_ms=3, ref=IRON_TD_CLOSE, pitch=[1.0, 1.0], pitch_per_play=[0.9, 1.1], attenuation=12,
       subtitle_en='Print bar thunks', subtitle_pt='Barra de impressão bate',
       clip_key='print key 1140: printer.thunk|0.5|0.9 -> |1.0|0.9; refuse key 6: printer.thunk|0.4|1.1 -> |1.0|1.1',
       play='Printer clip print, key 1140 (the bar lands; pitch 0.9) and refuse, key 6 (the slam after the kick; pitch 1.1, under the server\'s feedback.refused): the hit at 3 ms, the sleeve rattles 13-40 ms later, a settle tick at 55 ms.' + KEY_NOTE),
    ev(id='printer.unlatch', file='printer/unlatch', fn=printer_unlatch, takes=3, target=-25.0, cls='latch release',
       kind=('servo', 0.50), fade_ms=20, hit_ms=3, ref=IRON_DOOR_OPEN, pitch=[0.95, 1.05],
       subtitle_en='Booth door unlatches', subtitle_pt='Porta da cabine destrava',
       clip_key='deliver key 2: printer.unlatch|0.5|1.0 -> printer.unlatch|1.0|1.0',
       play='Printer clip deliver, key 2: the latch at 3 ms, the spring 25 ms later, the seal squeak at 150 ms (tick 5, the wind-up), the hinge creaking faintly through the swing, gone before printer.door_stop at 15.' + KEY_NOTE),
    ev(id='printer.door_stop', file='printer/door_stop', fn=printer_door_stop, takes=3, target=-26.5, cls='latch (door on its stop)',
       kind='latch', fade_ms=12, hit_ms=3, ref=COPPER_TD_CLOSE, pitch=[1.0, 1.0], pitch_per_play=[0.9],
       subtitle_en='Booth door swings open', subtitle_pt='Porta da cabine se abre',
       clip_key='deliver key 15: printer.door_stop|0.6|0.9 -> printer.door_stop|1.0|0.9',
       play='Printer clip deliver, key 15 (104 degrees, the overshoot) at pitch 0.9: the knock at 3 ms, the glass rattle 15-50 ms, the hinge settle tick at 200 ms (222 ms at 0.9; the door is back at 100 on tick 19).' + KEY_NOTE),
    ev(id='printer.step', file='printer/step', fn=printer_step, takes=4, target=-29.0, cls='step (bare foot, light)',
       kind='latch', fade_ms=12, hit_ms=3, ref=EQUIP_IRON, pitch=[1.0, 1.0], pitch_per_play=[1.0, 0.95, 0.85],
       subtitle_en='Printed body steps out', subtitle_pt='Corpo impresso sai andando',
       clip_key='deliver keys 24, 30, 35: printer.step|0.4|1.0, |0.4|0.95, |0.5|0.85 -> volume 1.0, same pitches',
       play='Printer clip deliver, keys 24, 30, 35 at pitch 1.0, 0.95, 0.85 (the body walking out, ducking under the sign): the heel at 3 ms, the ball of the foot 26-42 ms later. A different take each step if the handler allows.' + KEY_NOTE),
    ev(id='printer.door_swing', file='printer/door_swing', fn=printer_door_swing, takes=2, target=-27.7, cls='servo-class swing (hinge and air)',
       kind=('servo', 0.75), fade_ms=20, hit_ms=3, ref=COPPER_DOOR_OPEN, pitch=[0.97, 1.03],
       subtitle_en=None, subtitle_pt=None,
       clip_key='shut key 0: printer.door_swing|0.4|1.0 -> printer.door_swing|1.0|1.0',
       play='Printer clip shut, key 0: the knock off the stop at 3 ms, the wind-up to tick 3, the swing accelerating 150-750 ms; printer.door_shut is the contact at key 15. No subtitle (the shut carries it).' + KEY_NOTE),
    ev(id='printer.door_shut', file='printer/door_shut', fn=printer_door_shut, takes=3, target=-22.5, cls='slam (glass door in a brass frame)',
       kind='latch', fade_ms=15, hit_ms=3, ref=IRON_DOOR_CLOSE, pitch=[0.95, 1.05], attenuation=12,
       subtitle_en='Booth door shuts', subtitle_pt='Porta da cabine fecha',
       clip_key='shut key 15: printer.door_shut|0.6|1.0 -> printer.door_shut|1.0|1.0',
       play='Printer clip shut, key 15 (the contact): the slam at 3 ms, the glass rattle and the catch 12-35 ms, the 2-degree bounce closes again at 250 ms (tick 20).' + KEY_NOTE),
    ev(id='printer.ready', file='printer/ready', fn=printer_ready, takes=1, target=-29.5, cls='blip (small bell ping)',
       kind='stinger', fade_ms=40, hit_ms=3, ref=XYLO, pitch=[1.0, 1.0],
       subtitle_en='Printer pings: ready', subtitle_pt='Impressora apita: pronta',
       clip_key='loaded key 7: printer.ready|0.4|1.0 -> printer.ready|1.0|1.0',
       play='Printer clip loaded, key 7 (all four materials in; the head arrives at the centre): one E6 bell ping, fixed pitch, 0.6-0.7 s.' + KEY_NOTE),
    ev(id='printer.wind_down', file='printer/wind_down', fn=printer_wind_down, takes=2, target=-27.0, cls='servo (motor running down)',
       kind=('servo', 0.80), fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[0.97, 1.03], attenuation=12,
       subtitle_en='Printer winds down', subtitle_pt='Impressora perde o fôlego',
       clip_key='stall key 0: printer.wind_down|0.4|1.0 -> printer.wind_down|1.0|1.0',
       play='Printer clip stall, key 0 (the rate has been 0 for 20 ticks): the contactor drops at 3 ms, the motor runs down over about 0.6 s, the sleeve creaks as the bar sags, the knock at 0.80 s (tick 16).' + KEY_NOTE),
    ev(id='printer.whirr', file='printer/whirr', fn=printer_whirr, takes=1, target=-29.0, cls='hum/motor loop (small carriage), at full rate',
       kind='loop', fade_ms=0, hit_ms=None, ref=BEACON_AMBIENT, finish=loop_finish, loop=True, attenuation=10,
       pitch=[0.8, 1.0], subtitle_en='Print carriage whirs', subtitle_pt='Carro de impressão zune',
       play='Looped client sound (a tickable sound instance with looping on, created once per printer, at the carriage): volume = work rate (0 to 1), pitch = 0.8 + 0.2 x rate, both chased (0.2 a tick, pitch at most 0.05 a tick); start at volume 0 when the print starts, stop when the volume falls under 0.01 (the print ends, or a stall). Register with "stream": false. Seamless 3.0 s file, no fades in it; it carries no shuttle rhythm (the shuttle and the loop drift apart under brown-out).'),
    # ---- the Gene Splicer
    ev(id='splicer.stitch', file='splicer/stitch', fn=splicer_stitch, takes=4, target=-30.5, cls='tick (needle stitch, repeating)',
       kind='click', fade_ms=8, hit_ms=3, ref=WOOD_TD_OPEN, pitch=[1.0, 1.0], pitch_per_play=[1.0, 0.95, 1.05, 0.98, 0.9, 0.85],
       subtitle_en='Splicer stitches', subtitle_pt='Emendador costura',
       clip_key='work_0/1/2 keys 5, 15, ... 75: splicer.stitch|0.3|1.0, 0.95, 1.05, 0.98 (cycling); after a stutter t0+8: |0.3|0.9; stop and finish key 6: |0.3|0.85 -> volume 1.0, same pitches',
       play='Splicer clips work_0/1/2, every stitch\'s contact (t0 + 5) at the clip\'s cycling pitches, t0 + 8 after a stutter at 0.9, and the coast\'s last stitch (stop/finish key 6) at 0.85: the chk at 3 ms, gone in under 0.12 s. Twice a second for a minute: a random take each time, never the same twice running if the handler allows.' + KEY_NOTE),
    ev(id='splicer.stutter', file='splicer/stutter', fn=splicer_stutter, takes=3, target=-29.5, cls='tick (caught stitch)',
       kind='latch', fade_ms=12, hit_ms=3, ref=WOOD_TD_OPEN, pitch=[0.95, 1.05],
       subtitle_en='Stitch catches', subtitle_pt='O ponto engasga',
       clip_key='work_1 key 74, work_2 keys 34 and 74 (t0 + 4): splicer.stutter|0.4|1.0 -> splicer.stutter|1.0|1.0',
       play='Splicer clips work_1 and work_2, a tick into each stuttering stitch\'s hang (t0 + 4): the snag at 3 ms, the chatter to about 120 ms, the groan gone by 150 ms; the late punch is splicer.stitch at 0.9 four ticks on.' + KEY_NOTE),
    ev(id='splicer.clutch', file='splicer/clutch', fn=splicer_clutch, takes=2, target=-27.5, cls='latch (clutch engaging)',
       kind='latch', fade_ms=12, hit_ms=3, ref=IRON_TD_OPEN, pitch=[0.95, 1.05],
       subtitle_en='Splicer clutch engages', subtitle_pt='Embreagem do emendador engata',
       clip_key='start key 3: splicer.clutch|0.5|1.0 -> splicer.clutch|1.0|1.0',
       play='Splicer clip start, key 3 (the rock back reaches its hold): the dog teeth at 3 ms, the belt slap 12-16 ms, the detent at 120 ms.' + KEY_NOTE),
    ev(id='splicer.coast', file='splicer/coast', fn=splicer_coast, takes=2, target=-27.0, cls='servo-class run-down (no motor)',
       kind=('servo', 1.30), fade_ms=25, hit_ms=3, ref=PISTON_IN, pitch=[0.97, 1.03],
       subtitle_en='Splicer wheel coasts', subtitle_pt='Volante desacelera',
       clip_key='stop and finish_0/1/2 key 0: splicer.coast|0.5|1.0 -> splicer.coast|1.0|1.0',
       play='Splicer clips stop and finish_0/1/2, key 0: the drive lets go at 3 ms, the run-down over 1.0 s (the turn ends on tick 20), the pawl on the rock back at 1.15 s (tick 23) and the settle at 1.30 s (tick 26).' + KEY_NOTE),
    ev(id='splicer.hic', file='splicer/hic', fn=splicer_hic, takes=3, target=-26.5, cls='latch (mechanical hiccup)',
       kind='latch', fade_ms=12, hit_ms=3, ref=COPPER_TD_CLOSE, pitch=[1.0, 1.0], pitch_per_play=[1.0, 1.12],
       subtitle_en='Splicer hiccups', subtitle_pt='Emendador soluça',
       clip_key='finish_1 key 31: splicer.hic|0.6|1.0; finish_2 keys 31, 41: |0.6|1.0 and |0.6|1.12 -> volume 1.0, same pitches',
       play='Splicer clips finish_1 and finish_2, on each kick (t + 1): the clack and the catch at 3-40 ms, the three spools land at 186-214 ms by take (166-191 ms at 1.12; the clip\'s tick 5 is 200 ms).' + KEY_NOTE),
    ev(id='splicer.eject', file='splicer/eject', fn=splicer_eject, takes=2, target=-27.0, cls='latch (cassette pop-up)',
       kind=('servo', 0.20), fade_ms=15, hit_ms=3, ref=IRON_TD_OPEN, pitch=[0.95, 1.05],
       subtitle_en='Serum cassette pops up', subtitle_pt='Cassete de soro salta',
       clip_key='finish_0/1/2 key T + 2: splicer.eject|0.6|1.0 -> splicer.eject|1.0|1.0',
       play='Splicer clips finish_0/1/2 at T + 2 (the bottom of the dip): the latch at 3 ms, the slide up, the catch at 140-150 ms, the half-pixel settle at 335-350 ms (T + 9).' + KEY_NOTE),
    ev(id='splicer.thread', file='splicer/thread', fn=splicer_thread, takes=3, target=-32.0, cls='tick (thread zip)',
       kind=('servo', 0.15), fade_ms=15, hit_ms=3, ref=BRUSHING, pitch=[1.0, 1.0], pitch_per_play=[0.95, 1.0, 1.05],
       subtitle_en='Thread unspools', subtitle_pt='Linha desenrola do carretel',
       clip_key='pick_1/2/3 key 1: splicer.thread|0.4|0.95, 1.0, 1.05 -> volume 1.0, same pitches',
       play='Splicer clips pick_1, pick_2, pick_3, key 1 at pitch 0.95, 1.0, 1.05: the tug at 3 ms, the zip decaying over about 0.3 s as the spool lifts to tick 4.' + KEY_NOTE),
    ev(id='splicer.seat', file='splicer/seat', fn=splicer_seat, takes=3, target=-30.5, cls='tick (spool on its pin)',
       kind='click', fade_ms=8, hit_ms=3, ref=WOOD_TD_OPEN, pitch=[0.95, 1.05], subtitle_en=None, subtitle_pt=None,
       clip_key='pick_1/2/3 key 8: splicer.seat|0.4|1.0 -> splicer.seat|1.0|1.0',
       play='Splicer clips pick_1/2/3, key 8 (the contact): the knock at 3 ms, the bounce 58-72 ms later. No subtitle (part of the pick; the thread carries it).' + KEY_NOTE),
    ev(id='splicer.bell', file='splicer/bell', fn=splicer_bell, takes=1, target=-30.5, cls='blip (small bell), under feedback.done',
       kind='stinger', fade_ms=40, hit_ms=3, ref=XYLO, pitch=[1.0, 1.0], attenuation=12,
       subtitle_en='Splicer bell dings', subtitle_pt='Sininho do emendador toca',
       play='Server, at the splicer, when the strand reaches the next stage (the `stitch` blockstate steps; four times a job), volume 1.0, fixed pitch, on that tick (hit at 3 ms, no pre-roll). Not at the finish: the finish stays feedback.done. Debounce 20 ticks.'),
]

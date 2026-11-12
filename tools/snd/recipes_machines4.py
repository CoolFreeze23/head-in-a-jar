"""The machines, fourth batch: the Relay Mast (and its Mk II), the Signal Jammer, the Turret Pedestal and the Body
Locker (the Cloakroom).

The Relay Mast is a radio dish on a lamp post: on a route change its yoke's servo winds the dish 9 degrees the wrong
way, takes up the gear's backlash and swings it round the pole (the bearing groaning under the dish's weight); when the
power goes the brake lets go and the dish sags face-down onto its stop; a lightning strike in range makes it flinch
face-up with a crack of static off the bowl and creep back. The Mk II's telescoping lamp stage rises out of the dish's
top on a solenoid and drops back into the pole.

The Signal Jammer is a rooftop whirlybird ventilator that scrambles the air: its turbine whirrs (a loop played at a
pitch and volume following its speed) and a burst of radio static goes up when it starts jamming.

The Turret Pedestal is a searchlight-style gun mount carrying the real jar: the blaze's shot is wound up by an igniter
and a building burner roar under the muzzle's tremble, the zombie's headbutt is a pneumatic ram banging onto its stop
with the jar knocking in its band, the creeper's pulse is gas hissing out of the collar in two swells; while it tracks
the yaw servo ticks, and it chirps twice when it locks on.

The Body Locker is a theatre cloakroom kiosk: a desk bell on the brass countertop, a brass slatted roll shutter that
ratchets down and lands on the sill (and is unlatched and rolled back up), a soft pneumatic whoosh as the body is taken
in, a brass ticket clinking onto (and off) its hook, the ticket rattled on its hook for "not yours", and the portrait
card clicked into its frame and flipped back to VACANT.

Times inside a file are seconds; one tick is 50 ms. Every file has its hit at 0-5 ms (no pre-roll in this batch). Takes
differ by construction (a per-take table of frequencies, decays and timings), not by luck of the seed. The one loop
(the jammer's whirr) is built periodic and finished by the first batch's `loop_finish`.
"""
import numpy as np
from nsynth import *
from recipes_machines import small_tick, enamel_contact, cnoise, cband, loop_finish
from recipes_machines2 import env_seg, release, smooth, knock, brass_ring, bell_modes
from recipes_machines3 import pad

T = TICK


# ---------------------------------------------------------------- shared pieces

def groan(r, dur, s, f0=(55.0, 85.0), band=(250.0, 1800.0), res=700.0, jitter=0.05):
    """A slewing bearing groaning under load at speed s (0..1 per sample): stick-slip as a rough buzz whose rate follows
    the speed (f0[0] -> f0[1] Hz), its period jittered (an 8 ms smoothed random walk), through the casting round it (a
    band-pass and a resonance about res Hz); its level goes as s^1.2."""
    t = tt(dur)
    wob = smooth(r.standard_normal(len(t)), 8)
    wob = wob / max(np.abs(wob).max(), 1e-9)
    f = (f0[0] + (f0[1] - f0[0]) * s) * (1 + jitter * wob)
    x = osc_harm(f, 30, r, 0.5)
    y = norm(bandpass(x, band[0], band[1])) + 0.6 * norm(bandpass(x, res * 0.85, res * 1.15))
    return norm(y) * s ** 1.2


def slat(r, f, d=1.0):
    """One brass shutter slat knocking its neighbour: a strike and a short thin plate (ratios 1, 1.59, 2.14)."""
    return norm(mix(0.40 * strike(r, 2500),
                    0.70 * norm(modal(f, [1, 1.59, 2.14], [1, 0.45, 0.25], [0.006 * d, 0.004 * d, 0.003 * d], r, 0.03))))


def chirp(f0, f1, dur, h2=0.25):
    """A small speaker's chirp: a sine gliding f0 -> f1 (exponentially) over dur with a little second harmonic, up in
    3 ms, gliding over dur and decaying with a time constant of dur / 3 (generated 7 time constants long)."""
    t = tt(7 * dur / 3)
    f = f0 * (f1 / f0) ** np.minimum(t / dur, 1)
    ph = 2 * np.pi * np.cumsum(f) / SR
    e = np.clip(t / 0.003, 0, 1) * np.exp(-t / (dur / 3))
    return (np.sin(ph) + h2 * np.sin(2 * ph)) * e


# ======================================================================= the Relay Mast (Mk I and Mk II)

MSWING = [dict(m=1.00, f0=(55.0, 82.0), res=700.0, rel=0.58, slack=0.300),
          dict(m=1.08, f0=(62.0, 92.0), res=820.0, rel=0.53, slack=0.290),
          dict(m=0.92, f0=(48.0, 74.0), res=600.0, rel=0.66, slack=0.310)]


def mast_swing(take, r):
    """The dish starting a turn on its yoke (the code's aim_turn, tick 0 = the new next hop): the servo's relay pulls in
    at 3 ms and winds the dish the wrong way (ticks 0-5, a third of the speed), the gear's backlash is taken up the other
    way with a knock at 290-310 ms (the hold, tick 6), and the swing gets going: the yoke's motor (whine, grit, air)
    under the slewing bearing groaning with the dish's weight (stick-slip 48-92 Hz through the casting, a resonance
    about 600-820 Hz). Short: it lets go over about 0.15 s from 0.50-0.66 s, while the turn itself runs on in silence."""
    p = MSWING[take - 1]
    m = p['m']
    dur = 1.30
    b = buf(dur)
    t = tt(dur)
    wind = 0.35 * env_seg(t, 0.003, 0.25)
    go = np.clip((t - p['slack']) / 0.20, 0, 1) ** 1.5
    s = release(np.maximum(wind, go), t, p['rel'], 0.035)
    s = smooth(s, 8)
    b += 0.45 * motor(t, s, r, 130 * m, 380 * m, grit=0.30, air=0.12, whine=0.30)
    b += 0.60 * groan(r, dur, s, p['f0'], (220 * m, 1800 * m), p['res'])
    place(b, relay_click(r, 1700 * m, release_after=0.028), 0.003, 0.55)
    place(b, mix(knock(r, 900 * m, 0.8, th=(200, 140), th_gain=0.35, nz=0.25, band=(600, 4000), hp=1500),
                 0.5 * tooth(r, 2400 * m)), p['slack'], 0.50)
    return b


DROOP = [dict(m=1.00, f0=(40.0, 70.0), res=650.0, stop=850.0), dict(m=1.10, f0=(46.0, 80.0), res=760.0, stop=950.0)]


def mast_droop(take, r):
    """The dish sagging when the power goes (the code's droop: 20 degrees face-down over 16 ticks, easeInQuad, and a
    2-degree bounce): the brake's relay drops out at 3 ms with the shoe's clack, the dish falls faster and faster (the
    speed rising as the easeInQuad's), the bearing groaning up with it (40-80 Hz) and the motor dragged round backwards
    faintly, until the dish lands on its down stop at 800 ms (tick 16: a dull iron knock about 850-950 Hz with a thunk,
    the bowl's brass rim ringing briefly) and the bounce settles back onto it at 1.0 s (tick 20), soft."""
    p = DROOP[take - 1]
    m = p['m']
    dur = 1.50
    b = buf(dur)
    t = tt(dur)
    stop = 0.80
    s = np.where((t > 0.01) & (t < stop), np.clip((t - 0.01) / (stop - 0.01), 0, 1), 0.0)
    s = smooth(s, 6)
    b += 0.30 * motor(t, s, r, 90 * m, 260 * m, grit=0.25, air=0.10, whine=0.20)
    b += 0.55 * groan(r, dur, s, p['f0'], (200 * m, 1600 * m), p['res'])
    place(b, mix(relay_click(r, 1500 * m, release_after=0.030), 0.6 * knock(r, 1100 * m, 0.6, th_gain=0.2, nz=0.2)), 0.003, 0.55)
    hit = mix(0.45 * strike(r, 1500), norm(iron_body(r, p['stop'] * m, 1300 * m, 0.30, noise_gain=0.25)),
              0.40 * thunk(r, 170, 110, 0.030, 0.012), 0.35 * brass_ring(r, 1150 * m, 0.8))
    place(b, norm(hit), stop, 1.0)
    place(b, knock(r, p['stop'] * 1.05 * m, 0.5, th_gain=0.25, nz=0.15), 1.00, 0.14)
    return b


FLINCH = [dict(m=1.00, crk=0.12, back=1.60), dict(m=1.12, crk=0.18, back=1.50)]


def mast_flinch(take, r):
    """The dish flinching at a lightning strike in range (the code's flinch: 18 degrees face-up in 2 ticks, creeping back
    over 30): the yoke's gears slammed at 3 ms (a hard clank, iron about 650 Hz, and a thunk), a crack of static off
    the bowl (a 1-9 kHz snap and 120-160 ms of arc crackle thinning out), the dish's brass rim shivering, then the servo
    creeping it back (easeInOutSine over 0.1-1.5 s): a faint motor and the bearing's groan, and a settle tick as it comes
    to rest at 1.50-1.60 s."""
    p = FLINCH[take - 1]
    m = p['m']
    dur = 2.00
    b = buf(dur)
    t = tt(dur)
    clank = mix(0.50 * strike(r, 1500, 0.0012), norm(iron_body(r, 650 * m, 1100 * m, 0.45, noise_gain=0.30)),
                0.40 * thunk(r, 180, 110, 0.030, 0.012))
    place(b, norm(clank), 0.003, 1.0)
    place(b, norm(noise_burst(1000, 9000, 0.004, r)), 0.003, 0.60)
    cr = crackle(r, p['crk'], 2000, 10000, spacing=(0.006, 0.030), width=(0.002, 0.005))
    cr *= np.clip(1 - tt(len(cr) / SR) / (p['crk'] + 0.05), 0, 1) ** 1.5
    place(b, norm(cr), 0.006, 0.45)
    place(b, brass_ring(r, 1200 * m, 2.0), 0.005, 0.30)
    s = env_seg(t, 0.10, p['back']) ** 0.7
    s = smooth(s, 10)
    b += 0.10 * motor(t, s, r, 110 * m, 300 * m, grit=0.25, air=0.10, whine=0.30)
    b += 0.10 * groan(r, dur, s, (45 * m, 65 * m), (220, 1500), 700 * m)
    place(b, small_tick(r, 2200 * m, 0.005), p['back'] + 0.01, 0.12)
    return b


STAGE_UP = [dict(m=1.00, lo=1500.0, hi=5500.0, top=0.300), dict(m=0.88, lo=1200.0, hi=4500.0, top=0.290)]


def mast_stage_up(take, r):
    """The Mk II's lamp stage rising out of the dish's top (stage_up key 1; the clip's dip bottoms at tick 2, the stage
    shoots up 4.5 px easeOutCubic to tick 7 and settles back 0.5 px by 10): the stage's solenoid pulls in at 3 ms, the
    catch lets go at 50 ms (a small tick), the brass tube slides up its sleeve fast and slowing (a 1.2-5.5 kHz rub and a
    spring's whir, gone by 290-300 ms) onto its top stop (a light tick, tick 7), and sinks the half pixel back onto its
    detent at 450 ms (tick 10, a small latch click)."""
    p = STAGE_UP[take - 1]
    m = p['m']
    dur = 0.80
    b = buf(dur)
    t = tt(dur)
    place(b, relay_click(r, 1900 * m, release_after=0.024), 0.003, 0.70)
    place(b, small_tick(r, 2300 * m, 0.004), 0.050, 0.30)
    s = env_seg(t, 0.050, p['top'], 'out') * np.clip((t - 0.050) / 0.008, 0, 1)
    s = smooth(s, 4)
    b += 0.24 * norm(sweep_bandpass(noise(dur, r), p['lo'] * (0.7 + 0.3 * s), p['hi'] * (0.6 + 0.4 * s)) * s)
    w = np.sin(2 * np.pi * np.cumsum(420 * m * (0.5 + 0.5 * s)) / SR) * s
    b += 0.08 * norm(w)
    place(b, mix(small_tick(r, 2600 * m, 0.004), 0.5 * knock(r, 1400 * m, 0.4, th_gain=0.1, nz=0.2)), p['top'], 0.40)
    place(b, mix(0.6 * tooth(r, 2800 * m), 0.5 * knock(r, 1200 * m, 0.5, th=(260, 180), th_gain=0.2, nz=0.2)), 0.450, 0.55)
    return b


STAGE_DOWN = [dict(m=1.00, d=1.00, puff=(500.0, 3000.0)), dict(m=1.10, d=0.80, puff=(600.0, 3500.0))]


def mast_stage_down(take, r):
    """The Mk II's lamp stage dropping back into the pole (stage_down key 7, the contact: easeInQuad into it): the
    stage's collar seats on the pole's mouth at 3 ms (a strike, the steel tube's ring about 1.3 kHz, a duller body
    about 600 Hz and a thunk), the air in the pole puffs out round it (a short 0.5-3.5 kHz pfft), and the 0.4 px bounce
    seats it again at 150 ms (tick 10), softer."""
    p = STAGE_DOWN[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.50)
    hit = mix(0.45 * strike(r, 1800 * m),
              0.55 * norm(modal(1300 * m, [1, 2.756, 5.404], [1, 0.5, 0.25], [0.025 * d, 0.015 * d, 0.008 * d], r, 0.01)),
              0.45 * norm(modal(600 * m, [1, 2.3, 3.6], [1, 0.4, 0.2], [0.018 * d, 0.011 * d, 0.006 * d], r, 0.02)),
              0.35 * thunk(r, 200, 130, 0.022, 0.010))
    place(b, norm(hit), 0.003, 1.0)
    place(b, norm(noise_burst(p['puff'][0], p['puff'][1], 0.018, r, attack=0.003)), 0.006, 0.25)
    place(b, mix(0.6 * knock(r, 1250 * m, 0.6 * d, th_gain=0.25, nz=0.15), 0.3 * small_tick(r, 2400 * m, 0.004)), 0.153, 0.28)
    return b


# ======================================================================= the Signal Jammer

WHIRR_L = 2.0  # s: four turns at the jamming rate (36 degrees a tick, a turn per 10 ticks)


def jammer_whirr(take, r):
    """The turbine at its jamming rate, a seamless 2.0 s loop (four turns, 2 a second; eight blades, so the blades pass
    at 16 Hz): the air the blades chop (350-4500 Hz noise pulsing at the blade rate, a little stronger once a turn where
    the thick yellow marker blade passes), the blade tips' hiss (3-10 kHz), a low rumble in the drum (60-400 Hz), the
    bearing's whine (880 Hz and two harmonics, wavering once a turn), a tick of the bearing's cage once a turn, and the
    jamming itself: a faint warbling tone (1.5 kHz swept +-250 Hz six times a second, pulsing four times a second).
    Every periodic layer completes whole cycles in the 2.0 s."""
    n = int(round(WHIRR_L * SR))
    t = np.arange(n) / SR
    blade = (0.5 + 0.5 * np.cos(2 * np.pi * 16 * t)) ** 2
    turn = 1 + 0.15 * np.cos(2 * np.pi * 2 * t + 0.7)
    air = cband(cnoise(n, r), 350, 4500) * (0.40 + 0.60 * blade) * turn
    hiss = cband(cnoise(n, r), 3000, 10000) * (0.55 + 0.45 * blade)
    rum = cband(cnoise(n, r), 60, 400) * (0.85 + 0.15 * np.cos(2 * np.pi * 2 * t + 1.3))
    ph = 2 * np.pi * 880.0 * t + 0.6 * np.sin(2 * np.pi * 2 * t)
    whine = np.sin(ph) + 0.30 * np.sin(2 * ph + 0.4) + 0.10 * np.sin(3 * ph + 1.1)
    pw = 2 * np.pi * 1500.0 * t + (250.0 / 6.0) * np.sin(2 * np.pi * 6 * t)
    warble = np.sin(pw) * (0.55 + 0.45 * np.cos(2 * np.pi * 4 * t))
    tk = np.zeros(n)
    for k in range(4):
        x = np.zeros(n)
        c = small_tick(r, 2600 * (1 + 0.02 * (k % 2)), 0.004)
        x[:len(c)] = c
        tk += np.roll(x, int((0.11 + 0.5 * k) * SR))
    return (0.40 * norm(air) + 0.14 * norm(hiss) + 0.22 * norm(rum) + 0.10 * norm(whine) + 0.07 * norm(warble)
            + 0.05 * norm(tk))


STATIC = [dict(L=0.30, wh=(2600.0, 900.0), blk=0.008, band=(300.0, 4500.0)),
          dict(L=0.40, wh=(2200.0, 700.0), blk=0.011, band=(250.0, 4000.0)),
          dict(L=0.22, wh=(3000.0, 1200.0), blk=0.006, band=(350.0, 5000.0))]


def jammer_static(take, r):
    """A burst of radio static as the jammer starts jamming (the code, on `jamming` going true): its relay pulls in at
    3 ms and the air fills with a radio's noise: band-limited static (250-5000 Hz) chopped into uneven 6-11 ms grains,
    arc crackle in it and a heterodyne whistle sliding down (2.2-3 kHz to 0.7-1.2 kHz, with a 7 Hz waver), all through
    a little loudspeaker (the band again, driven into tanh); it holds 0.22-0.40 s and dies away with a 0.10 s time
    constant."""
    p = STATIC[take - 1]
    L = p['L']
    dur = 1.40
    b = buf(dur)
    t = tt(dur)
    n = len(t)
    env = (t > 0.004) * np.clip((t - 0.004) / 0.003, 0, 1) * np.where(t < L, 1.0, np.exp(-(t - L) / 0.10))
    nb = int(np.ceil(n / (p['blk'] * SR)))
    g = np.repeat(r.uniform(0.15, 1.0, nb), int(p['blk'] * SR) + 1)[:n]
    g = smooth(g, 2)
    st = bandpass(noise(dur, r), p['band'][0], p['band'][1]) * g
    cr = crackle(r, L + 0.25, 1500, 8000, spacing=(0.006, 0.030), width=(0.002, 0.005))
    crk = np.zeros(n)
    crk[:min(n, len(cr))] = cr[:n]
    fw = (p['wh'][1] + (p['wh'][0] - p['wh'][1]) * np.exp(-t / 0.15)) * (1 + 0.03 * np.sin(2 * np.pi * 7 * t))
    wh = np.sin(2 * np.pi * np.cumsum(fw) / SR)
    sig = (0.70 * norm(st) + 0.40 * norm(crk) + 0.30 * wh) * env
    y = bandpass(pad(sig), p['band'][0], p['band'][1])
    y = np.tanh(2.0 * norm(y)) / np.tanh(2.0)
    b += y[:n]
    place(b, relay_click(r, 1800, release_after=0.025), 0.003, 0.45)
    return b


# ======================================================================= the Turret Pedestal

WINDUP = [dict(m=1.00, wh=(380.0, 1500.0), roar=(250.0, 2800.0), rel=0.48), dict(m=1.08, wh=(420.0, 1700.0), roar=(300.0, 3200.0), rel=0.44)]


def turret_windup(take, r):
    """The blaze's shot wound up (charge key 1; the muzzle trembles a tick each way over ticks 1-9 and the fireball
    leaves with the kick on 12): the igniter snaps at 3 ms, the burner under the muzzle roars up (250-3200 Hz noise
    brightening and swelling to 0.45 s), the igniter's coil whines up (380-1700 Hz), the muzzle's tremble chatters in
    its collar every tick (small steel ticks every 50 ms, growing), and it all lets go in about 40 ms from 0.44-0.48 s
    (tick 10), leaving the shot to the kick."""
    p = WINDUP[take - 1]
    m = p['m']
    dur = 1.00
    b = buf(dur)
    t = tt(dur)
    place(b, mix(strike(r, 3000, 0.0008), 0.7 * norm(noise_burst(2000, 9000, 0.003, r)), 0.4 * relay_click(r, 2000 * m, 0.02)), 0.003, 0.70)
    u = np.clip((t - 0.005) / (p['rel'] - 0.005), 0, 1)
    s = release(np.where(t > 0.005, 0.15 + 0.85 * u ** 1.3, 0.0), t, p['rel'], 0.035)
    s = smooth(s, 4)
    roar = sweep_bandpass(noise(dur, r), p['roar'][0] * (0.8 + 0.4 * s), p['roar'][1] * (0.5 + 0.5 * s))
    roar *= 1 + 0.3 * smooth(r.standard_normal(len(t)), 15) / 0.05
    b += 0.55 * norm(roar * s)
    fw = p['wh'][0] * (p['wh'][1] / p['wh'][0]) ** u
    ph = 2 * np.pi * np.cumsum(fw) / SR
    b += 0.20 * norm((np.sin(ph) + 0.3 * np.sin(2 * ph)) * s)
    for k in range(1, 9):
        place(b, small_tick(r, 2300 * m * (1.04 if k % 2 else 0.96), 0.004), 0.003 + k * T, 0.10 + 0.04 * k)
    return b


LUNGE = [dict(m=1.00, d=1.00, glass=1650.0, hops=(0.014, 0.034)), dict(m=1.08, d=0.82, glass=1800.0, hops=(0.012, 0.029)),
         dict(m=0.92, d=1.20, glass=1500.0, hops=(0.017, 0.040))]


def turret_lunge(take, r):
    """The zombie's headbutt (lunge key 6, the end of the 2.5 px thrust; the cradle pitches 20 degrees forward with it):
    the pneumatic ram bangs onto its stop at 3 ms (a hard strike, the steel ram's body about 450-500 Hz, short, with a
    thunk under it), the ram's air chuffs out (300-2500 Hz, 30 ms), the real jar knocks in its brass foot band and its
    glass rattles twice (glass modes about 1.5-1.8 kHz, 12-40 ms on), and the ram's exhaust sighs out as it draws back
    (ticks 7-11)."""
    p = LUNGE[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.70)
    t = tt(0.70)
    hit = mix(0.55 * strike(r, 1200 * m, 0.0012), norm(iron_body(r, 470 * m, 800 * m, 0.40 * d, noise_gain=0.30)),
              0.45 * thunk(r, 160, 100, 0.035, 0.012), 0.35 * brass_ring(r, 1250 * m, 0.7 * d))
    place(b, norm(hit), 0.003, 1.0)
    place(b, norm(noise_burst(300, 2500, 0.030, r, attack=0.002)), 0.004, 0.40)
    for k, h in enumerate(p['hops']):
        place(b, norm(glass_modes(r, p['glass'] * m * (1 + 0.04 * k), decays=(0.020 * d, 0.012 * d, 0.008 * d, 0.005 * d))),
              0.003 + h, [0.35, 0.20][k])
    ex = env_seg(t, 0.06, 0.30) * 0.6
    b += 0.12 * norm(bandpass(noise(0.70, r), 1500, 7000) * ex)
    return b


THISS = [dict(lo=4500.0, hi=15000.0, p2=0.65, tail=0.10), dict(lo=5000.0, hi=16000.0, p2=0.55, tail=0.13)]


def turret_hiss(take, r):
    """The creeper's pulse (pulse key 0; the collar swells out 1 px by tick 3 and back by 5, then a smaller swell to 7
    and back by 9): gas hisses out of the collar in two swells following it (peaks at 150 and 350 ms, the second at
    55-65 %), bright (4.5-16 kHz, its band rising with the pressure), sputtering a little (small crackles), with a soft
    valve tick at 3 ms, and fizzling away after 450 ms."""
    p = THISS[take - 1]
    dur = 1.20
    b = buf(dur)
    t = tt(dur)
    sw = (np.exp(-((t - 0.15) / 0.075) ** 2) + p['p2'] * np.exp(-((t - 0.35) / 0.065) ** 2))
    sw = np.maximum(sw, np.where(t > 0.35, p['p2'] * np.exp(-(t - 0.35) / p['tail']), 0.0))
    sw = sw * np.clip((t - 0.004) / 0.004, 0, 1)
    b += 0.85 * norm(sweep_bandpass(noise(dur, r), p['lo'] * (0.7 + 0.3 * sw), p['hi'] * (0.7 + 0.3 * sw)) * sw)
    cr = crackle(r, 0.40, 3000, 12000, spacing=(0.015, 0.050), width=(0.002, 0.004))
    place(b, norm(cr), 0.050, 0.20)
    place(b, mix(small_tick(r, 3200, 0.003), 0.6 * norm(noise_burst(3000, 12000, 0.004, r))), 0.003, 0.45)
    return b


TSERVO = [dict(m=1.00, run=0.030), dict(m=1.12, run=0.024), dict(m=0.88, run=0.040)]


def turret_servo(take, r):
    """One step of the yaw servo while it tracks (the code's chase, yaw += (bearing - yaw) * 0.3 a tick): the gear
    train's tooth taking up at 3 ms (a small dull tick about 700 Hz), a whir of the little motor for 24-40 ms (whine
    and grit) and a softer tick as it stops; low-passed at 2.5 kHz by the drum. Very soft: it ticks while the turret
    turns after its target."""
    p = TSERVO[take - 1]
    m = p['m']
    dur = 0.25
    b = buf(dur)
    t = tt(dur)
    tick = mix(0.6 * norm(modal(700 * m, [1, 1.7, 2.6], [1, 0.4, 0.2], [0.008, 0.005, 0.003], r, 0.02)),
               0.25 * norm(noise_burst(600, 3000, 0.0015, r)), 0.20 * thunk(r, 260, 200, 0.008, 0.004))
    place(b, norm(tick), 0.003, 1.0)
    s = smooth(env_seg(t, 0.004, 0.004 + p['run']), 3)
    b += 0.30 * motor(t, s, r, 180 * m, 400 * m, grit=0.30, air=0.10, whine=0.40)
    place(b, norm(modal(640 * m, [1, 1.7], [1, 0.4], [0.006, 0.004], r, 0.02)), 0.006 + p['run'], 0.40)
    return lowpass(pad(b, 0.02), 2500)


LOCK = [dict(c1=(1100.0, 2200.0), c2=(1300.0, 2600.0), gap=0.085), dict(c1=(1200.0, 2400.0), c2=(1450.0, 2900.0), gap=0.075)]


def turret_lock(take, r):
    """The turret locking on (the code, on a new target with the snap): the sight's relay at 3 ms and two quick rising
    chirps from its little speaker (an octave up in 45 ms, the second higher, 75-85 ms later), through the speaker's
    band (600-5000 Hz). Not the company's bell: a machine's beep."""
    p = LOCK[take - 1]
    b = buf(0.40)
    place(b, relay_click(r, 2100, release_after=0.020), 0.003, 0.40)
    place(b, chirp(p['c1'][0], p['c1'][1], 0.045), 0.005, 0.85)
    place(b, chirp(p['c2'][0], p['c2'][1], 0.050), 0.005 + p['gap'], 1.0)
    return bandpass(pad(b, 0.02), 600, 5000)


# ======================================================================= the Body Locker (the Cloakroom)

def locker_bell(take, r):
    """The cloakroom's desk bell on the brass countertop (the server at check-in and check-out, tick 0, at pitch 1.0;
    the checkout clip's second ding, key 2, at pitch 2.0): a reception bell struck once at E6 (1318.5 Hz, the motif's
    E; at pitch 2.0 the E7 of the Employee Mount's desk bell), bell partials 1, 2, 2.4, 3, 4.2 with the upper ones
    kept low, a light strike and the plunger's second touch 22 ms later; about 1.2 s heard (0.6 s at pitch 2.0)."""
    b = buf(1.80)
    place(b, strike(r, 4000), 0.003, 0.20)
    place(b, norm(bell_modes(r, 1318.5, (0.26, 0.10, 0.06, 0.035, 0.018), amps=(1.0, 0.30, 0.22, 0.10, 0.05))), 0.003, 1.0)
    place(b, norm(bell_modes(r, 1318.5, (0.05, 0.025, 0.018, 0.012, 0.008), amps=(1.0, 0.30, 0.22, 0.10, 0.05))), 0.025, 0.10)
    return b


RATCHET = [dict(m=1.00, slats=(0.006, 0.013, 0.021)), dict(m=1.16, slats=(0.005, 0.011)),
           dict(m=0.93, slats=(0.008, 0.017, 0.027, 0.038))]


def locker_ratchet(take, r):
    """One slat of the roll shutter clicking past its pawl (checkin keys 9, 11, 13 at pitch 1.0, 1.05, 1.1 as it rolls
    down; checkout keys 8, 11, 14 at 1.1, 1.05, 1.0 as it rolls up): the pawl's tooth at 3 ms (about 2.6 kHz) and the
    brass slats knocking their neighbours down the curtain, two to four small knocks 5-38 ms after it, each softer;
    high-passed at 1.2 kHz. Gone inside 80 ms, so clicks two ticks apart stay separate."""
    p = RATCHET[take - 1]
    m = p['m']
    b = buf(0.20)
    place(b, tooth(r, 2600 * m), 0.003, 1.0)
    for k, g in enumerate(p['slats']):
        place(b, slat(r, 2000 * m * (1 + 0.06 * k), 0.8), 0.003 + g, [0.45, 0.30, 0.18, 0.11][k])
    return highpass(b, 1200)


THUNK = [dict(m=1.00, d=1.00, slats=(0.006, 0.012, 0.019, 0.028, 0.039), bounce=0.100),
         dict(m=1.08, d=0.82, slats=(0.005, 0.010, 0.016, 0.023), bounce=0.095),
         dict(m=0.92, d=1.20, slats=(0.007, 0.015, 0.024, 0.035, 0.048, 0.062), bounce=0.108)]


def locker_thunk(take, r):
    """The shutter landing on the sill (checkin key 14, the end of the easeInQuad roll down): the bottom bar hits the
    enamelled sill at 3 ms (a strike, the bar's steel body about 650 Hz, the counter's ivory panel about 380 Hz and a
    thunk), the brass slats concertina down onto it one after another (4-6 knocks 5-62 ms on, each softer) and the
    0.5 px bounce lands again at 95-108 ms (ticks 15-16), with a slat or two."""
    p = THUNK[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.60)
    hit = mix(0.50 * strike(r, 1500 * m), 0.80 * knock(r, 650 * m, 1.6 * d, th=(190, 120), th_gain=0.40, nz=0.30, band=(600, 5000), hp=1200),
              0.45 * norm(modal(380 * m, [1, 2.3, 3.6], [1, 0.4, 0.2], [0.030 * d, 0.018 * d, 0.010 * d], r, 0.02)),
              0.30 * thunk(r, 170, 110, 0.030, 0.012))
    place(b, norm(hit), 0.003, 1.0)
    for k, g in enumerate(p['slats']):
        place(b, slat(r, 1800 * m * (1 + 0.05 * (k % 3)), d), 0.003 + g, 0.40 * 0.78 ** k)
    place(b, mix(0.6 * knock(r, 680 * m, 0.8 * d, th_gain=0.3, nz=0.2), 0.4 * slat(r, 1850 * m, d)), 0.003 + p['bounce'], 0.30)
    place(b, slat(r, 1950 * m, d), 0.003 + p['bounce'] + 0.008, 0.12)
    return b


UNLATCH = [dict(m=1.00, zing=3100.0, lift=0.030), dict(m=1.10, zing=3400.0, lift=0.026)]


def locker_unlatch(take, r):
    """The shutter's catch let go at check-out (the code at checkout tick 4, the 1 px lift to 6): the sprung catch
    clacks at 3 ms (a relay-like click about 1.6 kHz, the catch's knock about 900 Hz and a small brass ring), the roll's
    spring takes the weight with a faint zing (about 3.1-3.4 kHz) and the curtain lifts off the sill, its slats shuffling
    (three light knocks from 26-30 ms)."""
    p = UNLATCH[take - 1]
    m = p['m']
    b = buf(0.55)
    place(b, mix(0.4 * relay_click(r, 1600 * m, release_after=0.022), 0.6 * knock(r, 900 * m, 1.4, th=(240, 160), th_gain=0.3, nz=0.2),
                 0.4 * brass_ring(r, 1400 * m, 1.3)), 0.003, 1.0)
    place(b, norm(modal(p['zing'], [1, 1.5, 2.3], [1, 0.4, 0.2], [0.050, 0.030, 0.018], r, 0.02)), 0.010, 0.10)
    for k in range(3):
        place(b, slat(r, 2100 * m * (1 + 0.05 * k), 0.8), p['lift'] + 0.012 * k, [0.30, 0.22, 0.15][k])
    return b


WHOOSH = [dict(L=0.30, lo=(300.0, 900.0), hi=(1500.0, 4500.0), land=0.300), dict(L=0.26, lo=(350.0, 1000.0), hi=(1700.0, 5000.0), land=0.270)]


def locker_checkin(take, r):
    """A body taken in at check-in (the server, at the locker, on checkin tick 0, as the client's puff trail runs to the
    window over ticks 0-6): the booth's suction valve opens at 3 ms (a soft chk and a low pop), the air rushes in toward
    the window (a soft whoosh, its band rising from 300-1700 Hz to 0.9-5 kHz and its level swelling over 0.26-0.30 s)
    and the body lands inside the rubber room with a muffled fwump (a dull thunk and a cloth puff) at 270-300 ms
    (tick 6)."""
    p = WHOOSH[take - 1]
    L = p['L']
    dur = 0.90
    b = buf(dur)
    t = tt(dur)
    place(b, mix(0.6 * strike(r, 1500, 0.0012), sweep_sine(260, 160, 0.010, 0.004), 0.4 * norm(noise_burst(400, 2500, 0.006, r))), 0.003, 0.55)
    u = np.clip((t - 0.005) / L, 0, 1)
    e = np.where(t < 0.005 + L, np.sin(np.pi * u * 0.55) ** 1.5, np.sin(np.pi * 0.55) ** 1.5 * np.exp(-(t - 0.005 - L) / 0.045))
    e = smooth(e * (t > 0.005), 4)
    lo = p['lo'][0] * (p['lo'][1] / p['lo'][0]) ** u
    hi = p['hi'][0] * (p['hi'][1] / p['hi'][0]) ** u
    b += 0.75 * norm(sweep_bandpass(noise(dur, r), lo, hi) * e)
    fw = mix(0.7 * thunk(r, 150, 95, 0.030, 0.012), 0.5 * norm(noise_burst(200, 1500, 0.020, r, attack=0.003)))
    place(b, norm(lowpass(pad(fw), 1800)), p['land'], 0.55)
    return b


PORTRAIT_ON = [dict(m=1.00, seat=0.014), dict(m=1.15, seat=0.011)]


def locker_portrait_on(take, r):
    """The portrait card slipped into its brass frame (checkin tick 18, `locker:portrait_on`): the card's edge ticks the
    frame at 3 ms (a tiny brass tick about 3.2-3.7 kHz with a flick of paper, 2-8 kHz) and it seats against the frame's
    lip 11-14 ms later (a smaller tick). Very soft."""
    p = PORTRAIT_ON[take - 1]
    m = p['m']
    b = buf(0.20)
    place(b, mix(small_tick(r, 3200 * m, 0.004), 0.6 * norm(noise_burst(2000, 8000, 0.003, r))), 0.003, 1.0)
    place(b, small_tick(r, 3600 * m, 0.003), 0.003 + p['seat'], 0.40)
    return highpass(b, 1500)


PORTRAIT_OFF = [dict(m=1.00, flip=0.022), dict(m=1.10, flip=0.018)]


def locker_portrait_off(take, r):
    """The portrait card flipped back to VACANT (checkout tick 18, `locker:portrait_off`): the card's catch lets go at
    3 ms (a tiny tick about 2.9 kHz), the card flaps over (a 1.5-6 kHz flick of air, 15-20 ms) and clicks flat against
    the frame 18-22 ms later (a tiny brass tick about 3.4 kHz). Very soft."""
    p = PORTRAIT_OFF[take - 1]
    m = p['m']
    b = buf(0.22)
    t = tt(0.22)
    place(b, small_tick(r, 2900 * m, 0.003), 0.003, 0.70)
    fl = env_seg(t, 0.004, 0.003 + p['flip'])
    b += 0.35 * norm(bandpass(noise(0.22, r), 1500, 6000) * fl)
    place(b, mix(small_tick(r, 3400 * m, 0.004), 0.5 * norm(noise_burst(2500, 8000, 0.002, r))), 0.003 + p['flip'], 1.0)
    return highpass(b, 1500)


TCLINK = [dict(m=1.00, d=1.00, touch=0.026), dict(m=1.08, d=0.85, touch=0.021)]


def locker_ticket_clink(take, r):
    """The brass cloakroom ticket on its hook (checkin key 16, hung as it appears and starts to swing; checkout key 4,
    taken off; pitch 1.2 both): the ticket's brass disc strikes the hook at 3 ms (a strike, the disc's plate modes about
    2.2 kHz, ratios 1, 1.59, 2.14, 2.83, 20-40 ms, and the hook's thin ring about 3.3 kHz) and touches it again as it
    swings, 21-26 ms on."""
    p = TCLINK[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.50)
    disc = norm(modal(2200 * m, [1, 1.59, 2.14, 2.83], [1, 0.6, 0.4, 0.25], [0.040 * d, 0.028 * d, 0.018 * d, 0.012 * d], r, 0.01))
    hook = norm(modal(3300 * m, [1, 2.756], [1, 0.3], [0.025 * d, 0.012 * d], r))
    place(b, norm(mix(0.35 * strike(r, 3000), 0.70 * disc, 0.30 * hook)), 0.003, 1.0)
    place(b, norm(mix(0.3 * strike(r, 3000), 0.6 * norm(modal(2250 * m, [1, 1.59, 2.14], [1, 0.5, 0.3], [0.020, 0.014, 0.009], r)))),
          0.003 + p['touch'], 0.30)
    return highpass(b, 600)


TRATTLE = [dict(m=1.00, n=3, gap=0.008), dict(m=1.08, n=2, gap=0.011)]


def locker_ticket_rattle(take, r):
    """The ticket rattled on its hook for "not yours" (refuse_ticket key 0; +-8 degrees, reversals at ticks 2, 4, 6): a
    faint tick at 3 ms as it leaves rest, then at each reversal (100, 200, 300 ms) the brass disc chattering against
    the hook, 2-3 tiny clinks 8-11 ms apart (about 2.1-2.3 kHz, short), the middle one a little lower. A layer under the
    server's feedback.refused, as machine.rattle is."""
    p = TRATTLE[take - 1]
    f = 2100.0 * p['m']
    b = buf(0.50)
    place(b, small_tick(r, f * 1.1, 0.004), 0.003, 0.15)
    for at, fr, g in [(0.100, 1.00, 1.0), (0.200, 0.94, 0.85), (0.300, 1.00, 0.70)]:
        for k in range(p['n']):
            c = norm(mix(0.2 * strike(r, 3000),
                         norm(modal(f * fr * r.uniform(0.96, 1.04), [1, 1.59, 2.14], [1, 0.5, 0.3], [0.009, 0.006, 0.004], r))))
            place(b, c, at + k * p['gap'], g * [1.0, 0.6, 0.4][k])
    return highpass(b, 800)


# ---------------------------------------------------------------- references (measured as the game plays them)

PISTON_IN = dict(event='block.piston.contract', file='tile__piston__in.ogg', vol=0.5, pitch=0.675)
CRAFTER = dict(event='block.crafter.craft', file='block__crafter__craft.ogg', vol=1.0, pitch=1.0)
SPYGLASS = dict(event='item.spyglass.use', file='item__spyglass__use.ogg', vol=1.0, pitch=1.0)
FIZZ = dict(event='block.fire.extinguish', file='random__fizz.ogg', vol=0.5, pitch=2.0)
IRON_TD_OPEN = dict(event='block.iron_trapdoor.open', file='block__iron_trapdoor__open1.ogg', vol=0.9, pitch=0.95)
IRON_TD_CLOSE = dict(event='block.iron_trapdoor.close', file='block__iron_trapdoor__close1.ogg', vol=0.9, pitch=0.95)
IRON_DOOR_OPEN = dict(event='block.iron_door.open', file='block__iron_door__open1.ogg', vol=0.9, pitch=0.95)
COPPER_TD_CLOSE = dict(event='block.copper_trapdoor.close', file='block__copper_trapdoor__toggle1.ogg', vol=1.0, pitch=0.95)
LANTERN = dict(event='block.lantern.place', file='block__lantern__place1.ogg', vol=1.0, pitch=0.88)
CHAIN_EQUIP = dict(event='item.armor.equip_chain', file='item__armor__equip_chain1.ogg', vol=1.0, pitch=1.0)
XYLO = dict(event='block.note_block.xylophone', file='note__xylobone.ogg', vol=1.0, pitch=1.0)
VAULT_ACTIVATE = dict(event='block.vault.activate', file='block__vault__activate.ogg', vol=1.0, pitch=1.0)
BEACON_AMBIENT = dict(event='block.beacon.ambient', file='block__beacon__ambient.ogg', vol=0.9, pitch=1.0)

BASE = dict(category='BLOCKS', attenuation=8, fixed_range=False, volume=1.0, ticks=None, muffled=False)
KEY_NOTE = ' Keyframe volume 1.0 (the file carries the loudness); the key\'s pitch is kept.'


def ev(**kw):
    d = dict(BASE)
    d.update(kw)
    return d


SOUNDS = [
    # ---- the Relay Mast (Mk I and Mk II)
    ev(id='mast.swing', file='mast/swing', fn=mast_swing, takes=3, target=-27.0, cls='servo (dish turning on its yoke)',
       kind=('servo', 0.66), fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[0.95, 1.05], attenuation=12,
       subtitle_en='Relay dish turns', subtitle_pt='Antena do relé gira',
       play='Client, at the dish (Mk I and Mk II), when a turn starts (aim_turn from the current yaw on every change of `aim`, tick 0), volume 1.0, random pitch 0.95-1.05, no pre-roll: the relay at 3 ms, the wrong-way wind-up to 250 ms, the backlash knock at 290-310 ms (the hold, tick 6), the swing\'s motor and bearing groan, let go from 0.53-0.66 s; the rest of the turn is silent. Short on purpose: one per turn, also when a new aim arrives mid-turn.'),
    ev(id='mast.droop', file='mast/droop', fn=mast_droop, takes=2, target=-27.0, cls='servo (dish sagging onto its stop)',
       kind=('servo', 0.80), fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[0.95, 1.05], attenuation=12,
       subtitle_en='Relay dish droops', subtitle_pt='Antena do relé tomba',
       play='Client, at the dish (Mk I and Mk II), when `powered` goes false and the droop starts (tick 0), volume 1.0, random pitch 0.95-1.05, no pre-roll: the brake\'s relay at 3 ms, the sag groaning faster to the stop at 800 ms (tick 16), the bounce settling at 1.0 s (tick 20). Nothing for the power coming back (the lamp and the next swing carry it).'),
    ev(id='mast.flinch', file='mast/flinch', fn=mast_flinch, takes=2, target=-25.0, cls='arc crackle and clank (lightning flinch)',
       kind=('servo', 1.52), fade_ms=25, hit_ms=3, ref=VAULT_ACTIVATE, pitch=[0.95, 1.05], attenuation=12,
       subtitle_en='Relay dish flinches', subtitle_pt='Antena do relé estremece',
       play='Client, at the dish (Mk I and Mk II), when the flinch starts (a lightning strike within the mast\'s range while it rains), volume 1.0, random pitch 0.95-1.05, no pre-roll: the clank and the static\'s crack at 3 ms, the creep back faint to the settle tick at 1.50-1.60 s. It plays under the thunder, so it does not try to be loud.'),
    ev(id='mast.stage_up', file='mast/stage_up', fn=mast_stage_up, takes=2, target=-28.0, cls='servo (small solenoid lift)',
       kind=('servo', 0.45), fade_ms=15, hit_ms=3, ref=IRON_TD_OPEN, pitch=[0.97, 1.03],
       subtitle_en='Mast lamp rises', subtitle_pt='Lâmpada do mastro sobe',
       clip_key='stage_up key 1: mast.stage_up|0.4|1.0 -> mast.stage_up|1.0|1.0',
       play='Mk II clip stage_up, key 1 (the dip): the solenoid at 3 ms, the catch at 50 ms (tick 2), the slide up to the top stop at 290-300 ms (tick 7), the detent at 450 ms (tick 10).' + KEY_NOTE),
    ev(id='mast.stage_down', file='mast/stage_down', fn=mast_stage_down, takes=2, target=-26.5, cls='latch (stage seated in the pole)',
       kind='latch', fade_ms=12, hit_ms=3, ref=COPPER_TD_CLOSE, pitch=[0.97, 1.03],
       subtitle_en='Mast lamp lowers', subtitle_pt='Lâmpada do mastro desce',
       clip_key='stage_down key 7: mast.stage_down|0.4|1.0 -> mast.stage_down|1.0|1.0',
       play='Mk II clip stage_down, key 7 (the contact): the seat at 3 ms, the puff, the bounce seated again at 150 ms (tick 10).' + KEY_NOTE),
    # ---- the Signal Jammer
    ev(id='jammer.whirr', file='jammer/whirr', fn=jammer_whirr, takes=1, target=-29.0, cls='hum/motor loop (turbine), at the jamming rate',
       kind='loop', fade_ms=0, hit_ms=None, ref=BEACON_AMBIENT, finish=loop_finish, loop=True, attenuation=12,
       pitch=[0.5, 1.0], subtitle_en='Jammer turbine whirrs', subtitle_pt='Turbina do bloqueador gira',
       play='Looped client sound (a tickable sound instance with looping on, created once per jammer, at the rotor) while it is awake and `speed` (the code\'s degrees a tick) is above 0.5: with u = speed / 36, volume = 0.25 + 0.75 x u and pitch = 0.5 + 0.5 x u (idle, 4.5 a tick: volume 0.34, pitch 0.56; jamming, 36: 1.0 and 1.0), both chased (0.2 a tick, pitch at most 0.05 a tick); start at volume 0, stop when the volume falls under 0.01 or the speed under 0.5. Register with "stream": false. Seamless 2.0 s file (four turns at the jamming rate), no fades in it.'),
    ev(id='jammer.static', file='jammer/static', fn=jammer_static, takes=3, target=-25.0, cls='arc crackle class (radio static burst)',
       kind='hiss', fade_ms=30, hit_ms=3, ref=VAULT_ACTIVATE, pitch=[0.94, 1.06], attenuation=12,
       subtitle_en='Jammer crackles', subtitle_pt='Bloqueador chia',
       play='Client, at the jammer, when `jamming` goes true (not again while it stays true), volume 1.0, random pitch 0.94-1.06, no pre-roll: the relay at 3 ms, the static held 0.22-0.40 s and dying away, about 0.7-0.9 s heard.'),
    # ---- the Turret Pedestal
    ev(id='turret.windup', file='turret/windup', fn=turret_windup, takes=2, target=-27.0, cls='servo-class (burner winding up)',
       kind=('servo', 0.48), fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[0.97, 1.03], attenuation=12,
       subtitle_en='Turret winds up', subtitle_pt='Torreta se prepara',
       clip_key='charge key 1: turret.windup|0.5|1.0 -> turret.windup|1.0|1.0',
       play='Turret clip charge, key 1 (the blaze): the igniter at 3 ms, the roar and the coil\'s whine swelling under the tremble\'s ticks (every 50 ms), let go at 0.44-0.48 s (tick 10). The shot itself (the kick on 12) is the server\'s fireball sound; this file stops before it.' + KEY_NOTE),
    ev(id='turret.lunge', file='turret/lunge', fn=turret_lunge, takes=3, target=-23.5, cls='slam (ram onto its stop)',
       kind='latch', fade_ms=12, hit_ms=3, ref=IRON_TD_CLOSE, pitch=[0.95, 1.05], attenuation=12,
       subtitle_en='Turret headbutts', subtitle_pt='Torreta dá uma cabeçada',
       clip_key='lunge key 6: turret.lunge|0.5|1.0 -> turret.lunge|1.0|1.0',
       play='Turret clip lunge, key 6 (the end of the thrust, with the code\'s 20-degree headbutt): the bang at 3 ms, the chuff, the jar\'s glass rattling 12-40 ms on, the exhaust as the ram draws back.' + KEY_NOTE),
    ev(id='turret.hiss', file='turret/hiss', fn=turret_hiss, takes=2, target=-24.5, cls='hiss (gas out of the collar)',
       kind='hiss', fade_ms=30, hit_ms=3, ref=FIZZ, pitch=[0.95, 1.05], attenuation=12,
       subtitle_en='Turret hisses', subtitle_pt='Torreta chia',
       clip_key='pulse key 0: turret.hiss|0.6|1.0 -> turret.hiss|1.0|1.0',
       play='Turret clip pulse, key 0 (the creeper): the valve tick at 3 ms, the two swells of hiss at 150 and 350 ms with the collar\'s, fizzling away by about 0.7 s.' + KEY_NOTE),
    ev(id='turret.servo', file='turret/servo', fn=turret_servo, takes=3, target=-31.5, cls='tick (yaw servo step, repeating)',
       kind='click', fade_ms=8, hit_ms=3, ref=CRAFTER, pitch=[0.94, 1.06], subtitle_en=None, subtitle_pt=None,
       play='Client, at the turret, while it tracks a target (the chase, after the snap): on a tick where the yaw moves more than 1 degree, at most every fourth tick (a run of steps 4 ticks apart measures about -28; every second tick it builds to about -25, too loud for garnish), volume 1.0, random pitch 0.94-1.06 (pitch 0.9 + 0.1 x min(1, step / 10) would follow the step\'s size), a random take: the tooth at 3 ms, the whir for 24-40 ms, gone in under 0.1 s. Not during the patrol sweep. No subtitle (turret.lock carries the tracking).'),
    ev(id='turret.lock', file='turret/lock', fn=turret_lock, takes=2, target=-28.0, cls='warning (lock-on chirp)',
       kind='latch', fade_ms=12, hit_ms=3, ref=XYLO, pitch=[1.0, 1.0], attenuation=12,
       subtitle_en='Turret locks on', subtitle_pt='Torreta trava a mira',
       play='Client, at the turret, on a new target (the synced targetId changing from -1 or to another mob, with the snap), volume 1.0, fixed pitch (a beep): the relay at 3 ms, two rising chirps 75-85 ms apart, gone by about 0.2 s. Not again on the cooldown\'s re-sync of the same target.'),
    # ---- the Body Locker (the Cloakroom)
    ev(id='locker.bell', file='locker/bell', fn=locker_bell, takes=1, target=-29.0, cls='blip (desk bell)',
       kind='stinger', fade_ms=40, hit_ms=3, ref=XYLO, pitch=[1.0, 1.0], pitch_per_play=[1.0, 2.0], attenuation=12,
       subtitle_en='Cloakroom bell dings', subtitle_pt='Campainha da chapelaria',
       clip_key='checkout key 2: locker.bell|0.4|2.0 -> locker.bell|1.0|2.0',
       play='Server, at the locker, on checkin and checkout tick 0, volume 1.0, pitch 1.0 (one E6 ding, about 1.2 s); and the checkout clip\'s second ding, key 2, at pitch 2.0 (the octave above, about 0.6 s). Fixed pitch: the octave pair is the cloakroom\'s ding-ding. One file: the pitch is the note.' + KEY_NOTE),
    ev(id='locker.ratchet', file='locker/ratchet', fn=locker_ratchet, takes=3, target=-31.0, cls='ratchet tooth',
       kind='click', fade_ms=8, hit_ms=3, ref=SPYGLASS, pitch=[1.0, 1.0], pitch_per_play=[1.0, 1.05, 1.1],
       subtitle_en='Cloakroom shutter rolls', subtitle_pt='Persiana da chapelaria rola',
       clip_key='checkin keys 9, 11, 13: locker.ratchet|0.4|1.0, 1.05, 1.1; checkout keys 8, 11, 14: |0.4|1.1, 1.05, 1.0 -> volume 1.0, same pitches',
       play='Locker clips checkin (keys 9, 11, 13 at pitch 1.0, 1.05, 1.1, rolling down) and checkout (keys 8, 11, 14 at 1.1, 1.05, 1.0, rolling up): the pawl at 3 ms, the slats 5-38 ms on, gone inside 80 ms. A different take each time if the handler allows.' + KEY_NOTE),
    ev(id='locker.thunk', file='locker/thunk', fn=locker_thunk, takes=3, target=-23.5, cls='slam (shutter on the sill)',
       kind='latch', fade_ms=12, hit_ms=3, ref=IRON_TD_CLOSE, pitch=[0.95, 1.05], attenuation=12,
       subtitle_en='Shutter slams down', subtitle_pt='Persiana bate no balcão',
       clip_key='checkin key 14: locker.thunk|0.6|1.0 -> locker.thunk|1.0|1.0',
       play='Locker clip checkin, key 14 (the end of the roll down): the bar on the sill at 3 ms, the slats concertina 5-62 ms on, the bounce lands at 95-108 ms (ticks 15-16).' + KEY_NOTE),
    ev(id='locker.unlatch', file='locker/unlatch', fn=locker_unlatch, takes=2, target=-25.0, cls='latch release',
       kind='latch', fade_ms=12, hit_ms=3, ref=IRON_DOOR_OPEN, pitch=[0.95, 1.05],
       subtitle_en='Shutter unlatches', subtitle_pt='Persiana destrava',
       play='Client, at the locker, on checkout tick 4 (the clip keys only the 1 px lift; play this with it, or add it to the clip as locker.unlatch|1.0|1.0 at key 4 beside locker.ticket_clink), volume 1.0, random pitch 0.95-1.05: the catch at 3 ms, the spring\'s zing, the slats shuffling 26-54 ms on, before the ratchet at 8.'),
    ev(id='locker.checkin', file='locker/checkin', fn=locker_checkin, takes=2, target=-27.0, cls='servo-class swish (a body drawn in)',
       kind=('servo', 0.30), fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[0.95, 1.05],
       subtitle_en='Body whisked inside', subtitle_pt='Corpo sugado para dentro',
       play='Server, at the locker (or the client with its puff trail), on checkin tick 0 (the host stored), with locker.bell, volume 1.0, random pitch 0.95-1.05, no pre-roll: the valve at 3 ms, the whoosh toward the window to 0.26-0.30 s, the fwump inside at 270-300 ms (tick 6, the end of the trail).'),
    ev(id='locker.portrait_on', file='locker/portrait_on', fn=locker_portrait_on, takes=2, target=-31.5, cls='tick (card into its frame)',
       kind='click', fade_ms=8, hit_ms=3, ref=SPYGLASS, pitch=[0.95, 1.05], subtitle_en=None, subtitle_pt=None,
       play='Client, at locator portrait, on the checkin clip\'s timeline key 18 (`locker:portrait_on`, as the portrait is drawn), volume 1.0, random pitch 0.95-1.05: the tick at 3 ms, the seat 11-14 ms on. No subtitle (garnish; the shutter and the chime carry the check-in).'),
    ev(id='locker.portrait_off', file='locker/portrait_off', fn=locker_portrait_off, takes=2, target=-31.5, cls='tick (card flipped to VACANT)',
       kind='click', fade_ms=8, hit_ms=3, ref=SPYGLASS, pitch=[0.95, 1.05], subtitle_en=None, subtitle_pt=None,
       play='Client, at locator portrait, on the checkout clip\'s timeline key 18 (`locker:portrait_off`, as the card returns to VACANT), volume 1.0, random pitch 0.95-1.05: the catch at 3 ms, the flap, the click flat 18-22 ms on. No subtitle (garnish).'),
    ev(id='locker.ticket_clink', file='locker/ticket_clink', fn=locker_ticket_clink, takes=2, target=-26.5, cls='glass clink class (brass ticket on a hook)',
       kind='latch', fade_ms=15, hit_ms=3, ref=LANTERN, pitch=[1.0, 1.0], pitch_per_play=[1.2],
       subtitle_en='Cloakroom ticket clinks', subtitle_pt='Ficha da chapelaria tilinta',
       clip_key='checkin key 16 and checkout key 4: locker.ticket_clink|0.4|1.2 -> locker.ticket_clink|1.0|1.2',
       play='Locker clips checkin (key 16, the ticket appears on the hook) and checkout (key 4, it is taken off), pitch 1.2: the disc on the hook at 3 ms, its second touch 21-26 ms on.' + KEY_NOTE),
    ev(id='locker.ticket_rattle', file='locker/ticket_rattle', fn=locker_ticket_rattle, takes=2, target=-27.0, cls='servo-class file, played quieter (a layer)',
       kind='latch', fade_ms=12, hit_ms=100, ref=CHAIN_EQUIP, pitch=[0.95, 1.05], volume=0.4,
       subtitle_en=None, subtitle_pt=None,
       clip_key='refuse_ticket key 0: locker.ticket_rattle|0.4|1.0 (keep 0.4)',
       play='Locker clip refuse_ticket, key 0, at volume 0.4 (deliberately: a layer under the server\'s feedback.refused, which carries the subtitle and the message, as machine.rattle): a faint tick at 3 ms, the chatter at 100, 200, 300 ms on the clip\'s reversals (ticks 2, 4, 6).'),
]

"""The switchboard batch: the radio link heard in the head, the Neural Socket as a switchboard, and the clip sounds the
Charging Dock, the Injection Rig and the Leech Jar keyed in their animations but had no event for.

Every statement here comes from a recipe, a measurement or a spectrogram.

The link heard. A player whose head sits in a jar elsewhere drives their body over a radio link of quality 0-100; the
static is heard in their own head (not positional). Three seamless loops, one per band: faint (40-69, a 2-6 kHz hiss
with a pop now and then, 6.0 s), static (20-39, broadband 300 Hz-8 kHz with a crackle texture and pops, 4.0 s) and
heavy (1-19, louder static, denser crackle and a 'searching' whistle: a 1.1 kHz sine whose pitch swings +-10 % at
1 Hz, the sound of a radio hunting for its station, 4.0 s). Every noise layer is filtered circularly (in the frequency
domain), every crackle burst that runs past the end is folded back onto the start, every periodic layer completes a
whole number of cycles in the file (the whistle: 4400 cycles in 4 s, its sweep 4), and the first batch's
`loop_finish` finishes them (no fades, a circular high-pass, the seam a near-zero crossing in a quiet moment).

The Neural Socket as a switchboard. Plugged in, a jar drives six redstone channels, one per face. Plugging in is six
relay pull-ins rippling 25 ms apart (a telephone exchange putting a call through) and then one note on the house FM
voice, G5 reached by a short glide up from F5 (the motif's G: the company has connected you). Unplugging is the
ripple in reverse, six armature releases falling in pitch, ending in the clamps' soft pneumatic release; no bell. A
channel on is one bright pull-in (the code plays it at six pitches, one per face); off is a softer, duller release; a
zombie head's 1 Hz clock is a small dry tick, low-passed so it can repeat for as long as the head sits there.

The clip sounds. dock.lever is the knife switch's blade taken by its T-grip (there is no separate lever and no ratchet
in the Dock: the `contacts` bone is the switch), dipped 6 degrees open onto its sprung stop and thrown up toward the
jaws, its hinge rubbing faster and faster until dock.clack lands on it at 0.45 s. dock.unclack is the blade pulled out
of the copper jaws (the leaves let go with a snap, wipe the blade and spring shut behind it at the break-spark).
dock.tap is a knuckle on the switch's wooden T-grip (the Dock's use: the switch jumps, the needle's wobble is code).
rig.cancel is the syringe's start upward when a jab is called off (the arm's swing home and the holster are their own
keys); rig.fill is serum drawn up into the syringe from the globe; rig.drain is the plunger let down into the barrel:
the last of the serum squirts back and dribbles. leech.bite is the nozzle cup's nip on the creature at the clip's
`leech:bite` instruction.

Times inside a file are seconds; one tick is 50 ms. Every one-shot has its hit at 3 ms (keyframed or played on its
tick, no pre-roll). Takes differ by construction (per-take tables of frequencies, decays and timings).
"""
import numpy as np
from nsynth import *
from recipes_machines import small_tick, cnoise, cband, loop_finish
from recipes_machines2 import env_seg, release, smooth, knock, brass_ring, creaks
from recipes_machines5 import squelch

T = TICK
G5, F5 = 783.99, 698.46


# ---------------------------------------------------------------- shared pieces

def pull_in(r, f, d=1.0, bright=1.0, hp=3000.0):
    """A relay's armature pulling in: a high-passed noise flick (0.5 ms) on two short modes at f and 1.72 f (the
    kit's relay_click without its release)."""
    return norm(mix(bright * highpass(noise(0.006, r), hp) * exp_env(0.006, 0.0005),
                    0.5 * modal(f, [1, 1.72], [1, 0.5], [0.010 * d, 0.006 * d], r, 0.03)))


def fm_glide(f_end, f_start, glide, tau, r, index=(2.0, 0.3), index_tau=None, ratio=3.5, attack=0.003):
    """The house voice (FM, carrier:modulator 1:3.5, index falling) on a pitch that glides from f_start to f_end with
    time constant `glide`; the modulator follows the carrier's phase, so the ratio holds through the glide."""
    d = attack + 7 * tau
    t = tt(d)
    f = f_end + (f_start - f_end) * np.exp(-t / glide)
    ph = 2 * np.pi * np.cumsum(f) / SR
    it = index_tau or tau / 3
    ind = index[1] + (index[0] - index[1]) * np.exp(-t / it)
    ph0, ph1 = r.uniform(0, 2 * np.pi, 2)
    out = np.sin(ph + ph0 + ind * np.sin(ratio * ph + ph1))
    return out * np.clip(t / attack, 0, 1) * np.exp(-t / tau)


def fold(x, n):
    """A buffer longer than the loop folded onto n samples (what runs past the end lands at the start)."""
    out = np.zeros(n)
    for i in range(0, len(x), n):
        seg = x[i:i + n]
        out[:len(seg)] += seg
    return out


def pop(r, lo=1200.0, hi=9000.0, tau=0.0015, tail=2):
    """A radio static pop: a strike and a broadband noise burst, then one or two smaller crackles 4-12 ms after it."""
    b = buf(0.06)
    place(b, strike(r, 1000), 0.0, 0.7)
    place(b, norm(noise_burst(lo, hi, tau, r, attack=0.0002)), 0.0, 1.0)
    at = 0.0
    for k in range(tail):
        at += r.uniform(0.004, 0.012)
        place(b, norm(noise_burst(lo * 1.3, hi, max(tau * 0.8, 0.001), r, attack=0.0002)), at, r.uniform(0.2, 0.45))
    return b


def crackle_texture(r, n, lo, hi, spacing, level=(0.15, 0.5)):
    """The house crackle (filtered-noise bursts with 1-3 ms decays, random levels) spread over a whole loop, folded."""
    L = n / SR
    b = buf(L + 0.05)
    at = r.uniform(0, spacing[1])
    while at < L:
        place(b, r.uniform(*level) * norm(noise_burst(lo, hi, r.uniform(0.001, 0.003), r, attack=0.0003)), at)
        at += r.uniform(*spacing)
    return fold(b, n)


def periodic_wander(t, L, parts, r):
    """A slow loudness wander made of sines that complete whole cycles in the loop: parts = [(cycles, depth), ...]."""
    w = np.ones(len(t))
    for c, dep in parts:
        w += dep * np.sin(2 * np.pi * c * t / L + r.uniform(0, 2 * np.pi))
    return w


# ======================================================================= the link heard

FAINT_L = 6.0
STATIC_L = 4.0
HEAVY_L = 4.0


def link_static_faint(take, r):
    """Quality 40-69, a seamless 6.0 s loop: a faint hiss (noise 2-6 kHz, its loudness wandering +-15 % once and
    +-8 % three times a loop) and two crackle pops at uneven spacing (1.43 s and 4.02 s: gaps of 2.59 and 3.41 s
    round the loop), each a strike, a 1.2-9 kHz burst and one or two smaller crackles after it, about 22 dB over the
    hiss's level."""
    n = int(round(FAINT_L * SR))
    t = np.arange(n) / SR
    hiss = norm(cband(cnoise(n, r), 2000, 6000)) * periodic_wander(t, FAINT_L, [(1, 0.15), (3, 0.08)], r)
    rms = np.sqrt(np.mean(hiss ** 2))
    pops = buf(FAINT_L + 0.1)
    for at, tail in ((1.43, 2), (4.02, 1)):
        place(pops, norm(pop(r, tail=tail)), at)
    pops = fold(pops, n)
    return hiss + rms * 10 ** (22 / 20) * pops


def link_static(take, r):
    """Quality 20-39, a seamless 4.0 s loop: continuous static (300 Hz-8 kHz noise, a little more weight in the
    300-2000 Hz half, fluttering at 2.75 and 6.25 Hz and wandering once a loop), a crackle texture of small 1.5-8 kHz
    bursts 15-70 ms apart, and four pops at uneven times (0.37, 1.21, 2.64, 3.33 s), about 16 dB over the static."""
    n = int(round(STATIC_L * SR))
    t = np.arange(n) / SR
    body = norm(cband(cnoise(n, r), 300, 2000)) + 0.7 * norm(cband(cnoise(n, r), 2000, 8000))
    body = norm(body) * periodic_wander(t, STATIC_L, [(11, 0.12), (25, 0.08), (1, 0.08)], r)
    rms = np.sqrt(np.mean(body ** 2))
    tex = crackle_texture(r, n, 1500, 8000, (0.015, 0.070))
    pops = buf(STATIC_L + 0.1)
    for at, tail in ((0.37, 1), (1.21, 2), (2.64, 1), (3.33, 2)):
        place(pops, norm(pop(r, 1000, 8000, 0.0018, tail)), at)
    pops = fold(pops, n)
    return body + rms * (10 ** (6 / 20) * tex + 10 ** (16 / 20) * pops)


def link_static_heavy(take, r):
    """Quality 1-19, a seamless 4.0 s loop: heavy static (200 Hz-9 kHz noise fluttering at 2.75, 6.25 and 9.5 Hz), a
    dense crackle texture (bursts 8-40 ms apart) and five pops, and the 'searching' whistle: a 1.1 kHz sine (second
    harmonic at -14 dB) whose pitch swings +-10 % once a second (four sweeps, 4400 whole cycles in the loop),
    swelling and fading twice a loop as if it nearly catches the station, about 4 dB under the static."""
    n = int(round(HEAVY_L * SR))
    t = np.arange(n) / SR
    body = norm(cband(cnoise(n, r), 200, 1800)) + 0.8 * norm(cband(cnoise(n, r), 1800, 9000))
    body = norm(body) * periodic_wander(t, HEAVY_L, [(11, 0.18), (25, 0.12), (38, 0.08), (1, 0.10)], r)
    rms = np.sqrt(np.mean(body ** 2))
    tex = crackle_texture(r, n, 1200, 9000, (0.008, 0.040), (0.2, 0.7))
    pops = buf(HEAVY_L + 0.1)
    for at, tail in ((0.21, 2), (0.93, 1), (1.88, 2), (2.47, 1), (3.52, 2)):
        place(pops, norm(pop(r, 900, 8000, 0.002, tail)), at)
    pops = fold(pops, n)
    # the whistle: f(t) = 1100 (1 + 0.1 sin 2 pi t), its phase integrated exactly so the loop holds 4400 cycles
    ph = 2 * np.pi * 1100 * (t - 0.1 * np.cos(2 * np.pi * t) / (2 * np.pi)) + r.uniform(0, 2 * np.pi)
    sw = np.sin(ph) + 0.2 * np.sin(2 * ph)
    swell = 0.55 + 0.45 * np.sin(2 * np.pi * 2 * t / HEAVY_L + 0.7)
    whistle = sw * swell
    whistle *= rms / np.sqrt(np.mean(whistle ** 2)) * 10 ** (-4 / 20)
    return body + whistle + rms * (10 ** (5 / 20) * tex + 10 ** (13 / 20) * pops)


# ======================================================================= the Neural Socket as a switchboard

RELAY_UP = [dict(m=1.00, tau=0.075, i0=2.0, gains=(0.85, 1.0, 0.8, 0.95, 0.75, 0.9)),
            dict(m=1.08, tau=0.090, i0=1.6, gains=(0.9, 0.8, 1.0, 0.75, 0.95, 0.85))]


def socket_relay_up(take, r):
    """Plugging in: six relays pull in 25 ms apart from 3 ms (each a pull-in click, rising 3 % a step from about
    1.6 kHz, uneven in level like a rank of selectors stepping), each armature's contacts bouncing once 4 ms later;
    then at 160 ms one note on the house FM voice gliding up from F5 to G5 (time constant 15 ms), decaying with a
    time constant of 75-90 ms. Low-passed at 7 kHz."""
    p = RELAY_UP[take - 1]
    m = p['m']
    b = buf(0.17 + 7 * p['tau'] + 0.02)
    for k in range(6):
        at = 0.003 + 0.025 * k
        f = 1600 * m * 1.03 ** k
        place(b, pull_in(r, f), at, 0.55 * p['gains'][k])
        place(b, pull_in(r, f * 1.12, 0.5, 0.6), at + 0.004, 0.18 * p['gains'][k])
    place(b, fm_glide(G5, F5, 0.015, p['tau'], r, index=(p['i0'], 0.3)), 0.160, 0.75)
    return lowpass(b, 7000)


RELAY_DOWN = [dict(m=1.00, tau=0.060, band=(1800.0, 7000.0)), dict(m=0.92, tau=0.075, band=(1500.0, 6000.0))]


def socket_relay_down(take, r):
    """Unplugging: the ripple in reverse, six armatures dropping out 25 ms apart from 3 ms, each a release tick (a
    lighter flick on two modes) falling 4 % a step from about 1.95 kHz; then at 165 ms the clamps' soft pneumatic
    release: the valve's small tick, a dull piston 'thup' (a knock about 700 Hz) and a short hiss (up in 4 ms, decaying
    with a 60-75 ms time constant, its band falling as the pressure goes). No bell."""
    p = RELAY_DOWN[take - 1]
    m = p['m']
    dur = 0.80
    b = buf(dur)
    t = tt(dur)
    for k in range(6):
        at = 0.003 + 0.025 * k
        f = 1950 * m * 0.96 ** k
        place(b, pull_in(r, f, 0.7, 0.5, 3500), at, 0.50 * (1.0 - 0.06 * k))
    t0 = 0.165
    place(b, small_tick(r, 3000 * m, 0.003), t0, 0.15)
    place(b, knock(r, 700 * m, 0.7, th=(200, 140), th_gain=0.25, nz=0.15, band=(500, 2500), hp=1500), t0, 0.22)
    u = np.maximum(t - t0 - 0.002, 0)
    pr = (t >= t0 + 0.002) * np.clip(u / 0.004, 0, 1) * np.exp(-u / p['tau'])
    lo, hi = p['band']
    b += 0.24 * norm(sweep_bandpass(noise(dur, r), lo * (0.6 + 0.4 * pr), hi * (0.6 + 0.4 * pr)) * pr)
    return b


CH_ON = [dict(f=2400.0, d=1.00, bounce=0.0040), dict(f=2650.0, d=0.80, bounce=0.0035), dict(f=2950.0, d=0.90, bounce=0.0048)]


def socket_channel_on(take, r):
    """A channel switched on: one bright pull-in at 3 ms (a 3 kHz high-passed flick on modes about 2.4-2.95 kHz and
    1.72x that) with its contacts bouncing once 3.5-5 ms later, gone in about 60 ms. The code plays it at six pitches
    (1.0 to 1.68, one per face), so it is short and bright enough to read as a click at all of them."""
    p = CH_ON[take - 1]
    b = buf(0.14)
    place(b, pull_in(r, p['f'], p['d'], 1.2), 0.003, 1.0)
    place(b, pull_in(r, p['f'] * 1.08, 0.5 * p['d'], 0.8, 4000), 0.003 + p['bounce'], 0.30)
    return b


CH_OFF = [dict(f=1500.0, d=1.00, lp=5000.0), dict(f=1650.0, d=0.85, lp=5500.0), dict(f=1280.0, d=1.20, lp=4300.0)]


def socket_channel_off(take, r):
    """A channel switched off: the armature let go at 3 ms, softer and duller than the pull-in: a lighter flick (2 kHz
    high-pass) on modes about 1.3-1.65 kHz with a slightly longer, damped body, a faint knock under it, low-passed at
    4.3-5.5 kHz."""
    p = CH_OFF[take - 1]
    b = buf(0.16)
    x = mix(pull_in(r, p['f'], 1.2 * p['d'], 0.5, 2000),
            0.35 * knock(r, 900, 0.6 * p['d'], th=(260, 200), th_gain=0.15, nz=0.2, band=(800, 4000), hp=1500))
    place(b, norm(x), 0.003, 1.0)
    return lowpass(b, p['lp'])


METRO = [dict(m=1.00, d=1.00), dict(m=1.08, d=0.85), dict(m=0.92, d=1.15), dict(m=1.17, d=0.90)]


def socket_metronome(take, r):
    """The zombie head's 1 Hz clock: a small dry relay tick at 3 ms (a soft flick on two short modes about
    1.4-1.75 kHz, 3-6 ms, and a faint low tick under it), low-passed at 4 kHz so it does not grate when it repeats
    every second. Four takes, a random one per tick, so the clock does not sound like a loop."""
    p = METRO[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.12)
    x = mix(0.6 * norm(modal(1500 * m, [1, 1.72], [1, 0.4], [0.005 * d, 0.003 * d], r, 0.03)),
            0.35 * norm(highpass(noise(0.004, r), 2000) * exp_env(0.004, 0.0004)),
            0.20 * norm(modal(600 * m, [1, 2.3], [1, 0.3], [0.004 * d, 0.003 * d], r)))
    place(b, norm(lowpass(np.concatenate([x, np.zeros(400)]), 4000)), 0.003, 1.0)
    return b


# ======================================================================= the Charging Dock

LEVER = [dict(m=1.00, rub=(900.0, 3400.0), creak=1700.0), dict(m=1.08, rub=(1000.0, 3600.0), creak=1850.0),
         dict(m=0.90, rub=(800.0, 3100.0), creak=1550.0)]


def dock_lever(take, r):
    """The knife switch taken by its T-grip and thrown (occupy, occupy_low, topup key 0; dock.clack lands at 0.45 s):
    the hand takes the wooden grip at 3 ms (a soft wood knock about 650 Hz), the blade dips 6 degrees open
    (easeOutQuad to 150 ms) with a short rub in its brass hinge cheeks and presses onto its sprung open stop at about
    63 ms (where it passes the stop's 4 degrees: a soft knock), holds a tick, then is thrown up toward the jaws
    (easeInQuad, 200-450 ms): the hinge rub and its stick-slip creaks grow with the speed, with a faint swish of the
    blade through the air, and let go in 12 ms at 445 ms, under the clack."""
    p = LEVER[take - 1]
    m = p['m']
    dur = 0.75
    b = buf(dur)
    t = tt(dur)
    place(b, knock(r, 650 * m, 0.8, th=(220, 150), th_gain=0.3, nz=0.2, band=(500, 2500), hp=1200), 0.003, 0.55)
    place(b, knock(r, 1100 * m, 0.6, th_gain=0.15, nz=0.2, band=(800, 4000), hp=1800), 0.063, 0.30)
    dip = np.where((t > 0.003) & (t < 0.15), (1 - np.clip((t - 0.003) / 0.147, 0, 1)), 0.0) * np.clip((t - 0.003) / 0.006, 0, 1)
    throw = env_seg(t, 0.20, 0.445, 'in')
    s = release(smooth(np.maximum(0.45 * dip, throw), 6), t, 0.445, 0.012)
    rub = bandpass(noise(dur, r), p['rub'][0] * m, p['rub'][1] * m)
    b += 0.35 * norm(rub * s ** 1.2)
    swish = sweep_bandpass(noise(dur, r), 300 + 500 * throw, 1200 + 2500 * throw)
    b += 0.10 * norm(swish * s ** 2)
    creaks(r, b, 0.21, 0.43, p['creak'] * m, gap=(0.018, 0.040), level=0.10)
    creaks(r, b, 0.01, 0.10, p['creak'] * m * 0.95, gap=(0.020, 0.035), level=0.06)
    return b


UNCLACK = [dict(m=1.00, d=1.00, clear=0.050), dict(m=1.10, d=0.80, clear=0.046), dict(m=0.88, d=1.30, clear=0.056)]


def dock_unclack(take, r):
    """The knife switch pulled out of its copper jaws (full and vacate key 2; the reverse of dock.clack): the blade
    breaks the jaws' grip at 3 ms (a release snap: a strike, the jaws' spring leaves about 2.05 kHz ringing briefly,
    the brass blade's free-bar modes about 1.25 kHz), the leaves wipe along the blade as it slides out (a 2.5-8 kHz
    rasp rising to the clearance), and as the blade clears the jaws at 46-56 ms (the break-spark, key 3) the leaves
    spring shut behind it: a spring's twang (two leaves about 2.2 kHz, a hair apart, 30-40 ms) and a small tick. No
    thunk: nothing lands (dock.fall does, at key 8)."""
    p = UNCLACK[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.55)
    snap = mix(0.45 * strike(r, 1800 * m),
               0.45 * norm(modal(2050 * m, [1, 1.59, 2.14], [1, 0.5, 0.3], [0.014 * d, 0.009 * d, 0.006 * d], r)),
               0.40 * norm(modal(1250 * m, [1, 2.756, 5.404], [1, 0.5, 0.25], [0.022 * d, 0.014 * d, 0.008 * d], r, 0.01)),
               0.20 * norm(noise_burst(1500 * m, 7000 * m, 0.004, r)))
    place(b, norm(snap), 0.003, 1.0)
    L = p['clear']
    w = tt(L)
    wipe = bandpass(noise(L, r), 2500 * m, 8000 * m) * (0.4 + 0.6 * w / L) * np.sin(np.pi * w / L) ** 0.5
    place(b, norm(np.concatenate([wipe, np.zeros(400)])), 0.004, 0.22)
    tw = mix(norm(modal(2200 * m, [1, 2.3], [1, 0.3], [0.035 * d, 0.015 * d], r)),
             0.8 * norm(modal(2200 * m * 1.012, [1, 2.3], [1, 0.3], [0.030 * d, 0.012 * d], r)),
             0.5 * strike(r, 2500))
    place(b, norm(tw), 0.003 + L, 0.55)
    place(b, small_tick(r, 3000 * m, 0.003), 0.005 + L, 0.15)
    return b


DTAP = [dict(m=1.00, d=1.00, hinge=0.011), dict(m=1.18, d=1.00, hinge=0.009), dict(m=0.85, d=1.00, hinge=0.014)]


def dock_tap(take, r):
    """The Dock's use (tap, tap_low, tap_on key 0): a knuckle on the switch's wooden T-grip at 3 ms (a soft knock, a
    damped wooden body about 510-710 Hz with a light thunk), and the blade jumping in its brass hinge cheeks 9-14 ms later
    (two faint brass ticks about 1.45-2.05 kHz). Low-passed at 3.5 kHz: a finger on wood."""
    p = DTAP[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.16)
    place(b, knock(r, 600 * m, 1.2 * d, th=(200 * m, 140 * m), th_gain=0.40, nz=0.15, band=(400, 2000), hp=900, ratios=(1, 2.1, 3.4)), 0.003, 1.0)
    place(b, small_tick(r, 1700 * m, 0.004), 0.003 + p['hinge'], 0.12)
    place(b, small_tick(r, 1750 * m, 0.003), 0.003 + p['hinge'] * 2.2, 0.06)
    return lowpass(b, 3500)


# ======================================================================= the Injection Rig

CANCEL = [dict(m=1.00, settle=0.150), dict(m=1.09, settle=0.140)]


def rig_cancel(take, r):
    """The jab called off (cancel key 0; the arm's swing home is rig.servo at key 2 and the drop into the sheath
    rig.holster at key 12): the syringe stands upright and starts up 0.5 px: the tip-out hinge's sprung catch snaps
    it upright at 3 ms (a tooth and a small brass ring about 1.5 kHz, the carriage's knock about 1.1 kHz), the
    carriage's little drive gives a short reversing whirr (the house motor, up in 15 ms, run down by 100 ms, as the
    syringe hops up and eases back), and it comes to rest at 140-150 ms (tick 3) with a soft tick."""
    p = CANCEL[take - 1]
    m = p['m']
    dur = 0.55
    b = buf(dur)
    t = tt(dur)
    place(b, mix(0.6 * tooth(r, 2600 * m), 0.5 * brass_ring(r, 1500 * m, 0.6),
                 0.6 * knock(r, 1100 * m, 0.7, th=(240, 170), th_gain=0.25, nz=0.2, band=(800, 5000), hp=2000)), 0.003, 1.0)
    s = smooth(np.clip((t - 0.004) / 0.015, 0, 1) * np.where(t < 0.03, 1.0, np.exp(-(t - 0.03) / 0.025)) * (t > 0.004), 4)
    b += 0.55 * motor(t, s, r, 220 * m, 520 * m, grit=0.40, air=0.15, whine=0.35)
    place(b, small_tick(r, 2300 * m, 0.004), p['settle'], 0.18)
    return b


def squeak(r, f, dur, rise=0.4):
    """Rubber on glass (the Rig's squeak): a sine rising by `rise` across dur with a stick-slip warble (90 Hz, 40 Hz
    deep) and a 140 Hz chatter, a sin^2 swell; second harmonic at 0.3."""
    t = tt(dur)
    fi = f * (1 + rise * t / dur) + 40 * np.sin(2 * np.pi * 90 * t)
    ph = 2 * np.pi * np.cumsum(fi) / SR
    return (np.sin(ph) + 0.3 * np.sin(2 * ph)) * np.sin(np.pi * t / dur) ** 2 * (0.7 + 0.3 * np.sin(2 * np.pi * 140 * t))


FILL = [dict(m=1.00, sq=1100.0, gur=(600.0, 1400.0), bub=((0.040, 700.0), (0.085, 860.0), (0.130, 1000.0), (0.175, 1150.0))),
        dict(m=1.10, sq=1250.0, gur=(700.0, 1600.0), bub=((0.030, 780.0), (0.070, 940.0), (0.120, 1100.0)))]


def rig_fill(take, r):
    """Serum drawn up into the syringe from the globe (prime key 1; the plunger draws 3.25 px over 0-200 ms in the
    file, easeOutCubic: fast, then slowing, and sinks back 0.25 px by 300 ms): the seal unsticks at 3 ms (a soft
    tick), the plunger's rubber seal squeaks on the glass barrel (rising about 1.1-1.25 kHz, louder while it runs
    fast), the serum rushes in: a liquid suction (400-2500 Hz noise through a resonance rising 600 -> 1400 Hz or
    700 -> 1600 Hz) with three or four small bubbles rising in pitch, following the draw's speed, and at 200 ms the
    draw stops and the vacuum pulls the plunger back a hair with a small wet gulp. Low-passed at 5 kHz."""
    p = FILL[take - 1]
    m = p['m']
    dur = 0.60
    b = buf(dur)
    t = tt(dur)
    u = np.clip((t - 0.003) / 0.197, 0, 1)
    sp = np.where((t > 0.003) & (t < 0.200), (1 - u) ** 2, 0.0) * np.clip((t - 0.003) / 0.008, 0, 1)
    sp = release(smooth(sp, 6), t, 0.200, 0.020)
    place(b, small_tick(r, 1800 * m, 0.004), 0.003, 0.25)
    sq = squeak(r, p['sq'], 0.10, rise=0.15)
    i0 = int(round(0.004 * SR))
    place(b, sq * sp[i0:i0 + len(sq)], 0.004, 0.22)
    lo, hi = p['gur']
    fr = lo + (hi - lo) * u
    liq = bandpass(noise(dur, r), 400, 2500) * sp
    b += 0.20 * norm(liq) + 0.45 * norm(sweep_bandpass(liq, fr * 0.8, fr * 1.25))
    for at, f in p['bub']:
        place(b, bubble(r, f * m, 0.012, rise=0.35), at, 0.30)
    place(b, bubble(r, 650 * m, 0.018, rise=0.25), 0.205, 0.28)
    place(b, thunk(r, 260, 180, 0.012, 0.006), 0.205, 0.10)
    return lowpass(b, 5000)


DRAIN = [dict(m=1.00, tau=0.030, drips=((0.060, 1300.0), (0.150, 1100.0), (0.265, 1450.0)), bounce=0.050),
         dict(m=1.08, tau=0.040, drips=((0.075, 1200.0), (0.120, 1500.0), (0.200, 1000.0), (0.300, 1350.0)), bounce=0.046)]


def rig_drain(take, r):
    """Serum let back out (unprime key 4, the plunger at the bottom of the barrel after sinking 3 px): the thumb pad
    seats on the barrel's flange at 3 ms (a soft knock, the glass barrel about 2.2 kHz, the brass flange's ring), the
    last of the serum squirts back into the globe (a short wet hiss, 1-6 kHz noise up in 3 ms, decaying with a 30-40 ms
    time constant, sputtering at 45 Hz), the quarter-pixel bounce taps again 46-50 ms on, and three or four drops
    dribble back into the globe, thinning out (small bubbles, the first two louder). Low-passed at 7 kHz."""
    p = DRAIN[take - 1]
    m = p['m']
    dur = 0.70
    b = buf(dur)
    t = tt(dur)
    hit = mix(0.30 * strike(r, 2000), 0.40 * norm(glass_modes(r, 2200 * m, decays=(0.015, 0.010, 0.006, 0.004))),
              0.30 * brass_ring(r, 1800 * m, 0.6), 0.30 * thunk(r, 230, 160, 0.015, 0.006))
    place(b, norm(hit), 0.003, 0.65)
    u = np.maximum(t - 0.004, 0)
    pr = (t >= 0.004) * np.clip(u / 0.003, 0, 1) * np.exp(-u / p['tau']) * (0.65 + 0.35 * np.cos(2 * np.pi * 45 * t))
    b += 0.60 * norm(bandpass(noise(dur, r), 1000 * m, 6000 * m) * pr)
    place(b, norm(glass_modes(r, 2250 * m, decays=(0.008, 0.005, 0.004, 0.003))), 0.003 + p['bounce'], 0.15)
    for k, (at, f) in enumerate(p['drips']):
        place(b, bubble(r, f * m, 0.010, rise=0.45), at, 0.40 if k < 2 else 0.22)
    return lowpass(b, 7000)


# ======================================================================= the Leech Jar

BITE = [dict(tsk=(3500.0, 9000.0), sq=(600.0, 3000.0), kiss=(900.0, 1400.0), g=(0.70, 0.60, 0.45)),
        dict(tsk=(4500.0, 11000.0), sq=(700.0, 3300.0), kiss=(1000.0, 1550.0), g=(1.00, 0.32, 0.45)),
        dict(tsk=(4000.0, 10000.0), sq=(800.0, 3600.0), kiss=(1150.0, 1700.0), g=(1.00, 0.26, 0.10))]


def leech_bite(take, r):
    """The nozzle cup's nip on the creature (sample, the `leech:bite` instruction at 0.3 s, with the server's vanilla
    hurt and honey-drink sounds): a soft wet suction 'tsk' at 3 ms (a 3-11 kHz noise flick, 1.5 ms), the cup's lip
    sealing (a tiny kiss: a sine rising about 0.9 -> 1.4 or 1.15 -> 1.7 kHz in a few ms) and a tiny squelch (600-3600 Hz noise
    chopped at 70 Hz, 12 ms). Gone in about 0.1 s."""
    p = BITE[take - 1]
    b = buf(0.18)
    place(b, norm(noise_burst(p['tsk'][0], p['tsk'][1], 0.0015, r, attack=0.0003)), 0.003, p['g'][0])
    place(b, 0.5 * sweep_sine(p['kiss'][0], p['kiss'][1], 0.008, 0.003), 0.004, p['g'][2])
    place(b, squelch(r, p['sq'][0], p['sq'][1], 0.012, 70.0), 0.006, p['g'][1])
    return b


# ---------------------------------------------------------------- references (measured as the game plays them)

BEACON_AMBIENT = dict(event='block.beacon.ambient', file='block__beacon__ambient.ogg', vol=0.9, pitch=1.0)
IRON_TD_OPEN = dict(event='block.iron_trapdoor.open', file='block__iron_trapdoor__open1.ogg', vol=0.9, pitch=0.95)
COPPER_TD_CLOSE = dict(event='block.copper_trapdoor.close', file='block__copper_trapdoor__toggle1.ogg', vol=1.0, pitch=0.95)
SPYGLASS = dict(event='item.spyglass.use', file='item__spyglass__use.ogg', vol=1.0, pitch=1.0)
CRAFTER = dict(event='block.crafter.craft', file='block__crafter__craft.ogg', vol=1.0, pitch=1.0)
LEVER_ON = dict(event='block.lever.click (on: volume 0.3, pitch 0.6)', file='random__click.ogg', vol=0.3, pitch=0.6)
LEVER_OFF = dict(event='block.lever.click (off: volume 0.3, pitch 0.5)', file='random__click.ogg', vol=0.3, pitch=0.5)
COPPER_STEP = dict(event='block.copper.step (file at volume 1.0: a loudness and colour neighbour, not as a step plays)',
                   file='block__copper__step2.ogg', vol=1.0, pitch=1.0)
BOTTLE_FILL = dict(event='item.bottle.fill', file='item__bottle__fill2.ogg', vol=1.0, pitch=1.0)
BOTTLE_EMPTY = dict(event='item.bottle.empty', file='item__bottle__empty1.ogg', vol=1.0, pitch=1.0)
BEEHIVE_DRIP = dict(event='block.beehive.drip (file at volume 1.0)', file='block__beehive__drip1.ogg', vol=1.0, pitch=1.0)

BASE = dict(category='BLOCKS', attenuation=8, fixed_range=False, volume=1.0, ticks=None, muffled=False)
KEY_NOTE = ' Keyframe volume 1.0 (the file carries the loudness); the key\'s pitch is kept, times the random range.'
LINK_NOTE = (' Loop file, no fades in it: a client sound instance with looping on, created once, heard by the driving '
             'player only and not positional (relative to the listener, no attenuation by distance; attenuation 16 '
             'is the sounds.json value only). Register with "stream": false. Volume 1.0 = the M max written here; the '
             'band level is the code\'s.')
SOCKET_PITCHES = [1.0, 1.1225, 1.2599, 1.3348, 1.4983, 1.6818]  # a major scale, do re mi fa sol la (1.0 to 1.68)


def ev(**kw):
    d = dict(BASE)
    d.update(kw)
    return d


SOUNDS = [
    # ---- the link heard (in the driving player's own head)
    ev(id='link.static_faint', file='link/static_faint', fn=link_static_faint, takes=1, target=-40.0,
       cls='link static, faint (loop, at full)', kind=('loop', FAINT_L), fade_ms=0, hit_ms=None, ref=BEACON_AMBIENT,
       finish=loop_finish, loop=True, category='PLAYERS', attenuation=16, pitch=[1.0, 1.0],
       subtitle_en='Link hisses', subtitle_pt='Conexão chia',
       play='While the link quality is 40-69: play this band\'s loop at the band level, and swell it to volume 1.0 for the ticks the weak link drops the body\'s inputs (chase 0.25 a tick up and down so the swell is not a step). Seamless 6.0 s: two pops 2.59 and 3.41 s apart round the loop. Fixed pitch.' + LINK_NOTE),
    ev(id='link.static', file='link/static', fn=link_static, takes=1, target=-34.0,
       cls='link static (loop, at full)', kind=('loop', STATIC_L), fade_ms=0, hit_ms=None, ref=BEACON_AMBIENT,
       finish=loop_finish, loop=True, category='PLAYERS', attenuation=16, pitch=[1.0, 1.0],
       subtitle_en='Link crackles', subtitle_pt='Conexão estala',
       play='While the link quality is 20-39: this band\'s loop at the band level, swelled to volume 1.0 for the ticks inputs are dropped (chase 0.25 a tick). Seamless 4.0 s, four pops at uneven times. When the band changes, cross-fade the two loops over about 5 ticks rather than cutting. Fixed pitch.' + LINK_NOTE),
    ev(id='link.static_heavy', file='link/static_heavy', fn=link_static_heavy, takes=1, target=-30.0,
       cls='link static, heavy, with the searching whistle (loop, at full)', kind=('loop', HEAVY_L), fade_ms=0,
       hit_ms=None, ref=BEACON_AMBIENT, finish=loop_finish, loop=True, category='PLAYERS', attenuation=16,
       pitch=[1.0, 1.0], subtitle_en='Link breaks up', subtitle_pt='Conexão falha',
       play='While the link quality is 1-19: this band\'s loop at the band level, swelled to volume 1.0 for the ticks inputs are dropped (chase 0.25 a tick). Seamless 4.0 s: the whistle sweeps 1.1 kHz +-10 % once a second (four sweeps). Fixed pitch: a pitch change would move the whistle and the sweep rate.' + LINK_NOTE),
    # ---- the Neural Socket as a switchboard
    ev(id='socket.relay_up', file='socket/relay_up', fn=socket_relay_up, takes=2, target=-28.0,
       cls='done-chime class (relay ripple and the house voice)', kind=('cascade', 0.45), fade_ms=30, hit_ms=3,
       ref=IRON_TD_OPEN, pitch=[1.0, 1.0], attenuation=12,
       subtitle_en='Switchboard connects', subtitle_pt='Mesa telefônica conecta',
       play='At the socket when a jar is plugged in and its six channels come up, volume 1.0: six relays 25 ms apart from 3 ms, the house-voice G5 at 160 ms, about 0.5 s heard. If socket.plug (the jack\'s click) falls on the same tick, start this 2 ticks after it. Fixed pitch: the note is the company\'s.'),
    ev(id='socket.relay_down', file='socket/relay_down', fn=socket_relay_down, takes=2, target=-28.0,
       cls='servo-class (relay ripple out, soft release)', kind=('cascade', 0.40), fade_ms=25, hit_ms=3,
       ref=IRON_TD_OPEN, pitch=[0.97, 1.03], attenuation=12,
       subtitle_en='Switchboard disconnects', subtitle_pt='Mesa telefônica desconecta',
       play='At the socket when the jar is unplugged and its channels drop, volume 1.0, random pitch 0.97-1.03: six releases 25 ms apart from 3 ms falling in pitch, the soft pneumatic release at 165 ms, about 0.45 s heard. No bell. If the release clip\'s socket.sigh plays on the same tick, start this first and leave the sigh to its key.'),
    ev(id='socket.channel_on', file='socket/channel_on', fn=socket_channel_on, takes=3, target=-31.0,
       cls='tick (relay pull-in, bright)', kind='click', fade_ms=8, hit_ms=3, ref=SPYGLASS, ref2=LEVER_ON,
       pitch=[1.0, 1.0], pitch_per_play=SOCKET_PITCHES, attenuation=10,
       subtitle_en='Socket channel on', subtitle_pt='Canal do soquete liga',
       play='At the socket, on the face whose channel turns on, volume 1.0, pitch by face, six values low to high: 1.0, 1.1225, 1.2599, 1.3348, 1.4983, 1.6818 (a major scale, do re mi fa sol la; the brief\'s 1.0 to 1.68; the face order is the code\'s). A random take each time. The click at 3 ms, gone in about 60 ms (36 ms at 1.68).'),
    ev(id='socket.channel_off', file='socket/channel_off', fn=socket_channel_off, takes=3, target=-33.0,
       cls='tick (relay release, soft)', kind='click', fade_ms=8, hit_ms=3, ref=COPPER_STEP, ref2=LEVER_OFF,
       pitch=[1.0, 1.0], pitch_per_play=SOCKET_PITCHES, attenuation=10,
       subtitle_en='Socket channel off', subtitle_pt='Canal do soquete desliga',
       play='At the socket, on the face whose channel turns off, volume 1.0, at the same per-face pitch as socket.channel_on, so a face\'s on and off are a pair (if the code would rather not, a random pitch 0.95-1.05 is fine). A random take each time. The release at 3 ms, gone in about 70 ms.'),
    ev(id='socket.metronome', file='socket/metronome', fn=socket_metronome, takes=4, target=-36.0,
       cls='idle machine life (clock tick)', kind='click', fade_ms=8, hit_ms=3, ref=LEVER_ON,
       pitch=[0.98, 1.02], attenuation=8,
       subtitle_en='Socket clock ticks', subtitle_pt='Relógio do soquete bate',
       play='At the socket, once a second (every 20 ticks) while a zombie head sits in it, volume 1.0, random pitch 0.98-1.02, a random take each time. If the clock also switches a channel on that tick, play this and not channel_on/off for that pulse, or the two will double. Very quiet by design (idle life).'),
    # ---- the Charging Dock
    ev(id='dock.lever', file='dock/lever', fn=dock_lever, takes=3, target=-27.5, cls='servo-class (knife switch taken and thrown)',
       kind=('servo', 0.44), fade_ms=20, hit_ms=3, ref=IRON_TD_OPEN, pitch=[0.97, 1.03],
       subtitle_en='Dock lever thrown', subtitle_pt='Alavanca da doca puxada',
       clip_key='occupy, occupy_low, topup key 0: dock.lever|0.4|1.0 -> dock.lever|1.0|1.0',
       play='Dock clips occupy, occupy_low and topup, key 0: the grip taken at 3 ms, the dip onto the stop at 63 ms, the throw\'s hinge rub growing from 200 ms and let go at 445 ms, just under dock.clack at 0.45 s (key 9).' + KEY_NOTE),
    ev(id='dock.unclack', file='dock/unclack', fn=dock_unclack, takes=3, target=-26.0, cls='latch (knife switch out of its jaws)',
       kind='latch', fade_ms=12, hit_ms=3, ref=COPPER_TD_CLOSE, ref2=IRON_TD_OPEN, pitch=[0.95, 1.05], attenuation=12,
       subtitle_en='Knife switch opens', subtitle_pt='Chave-faca abre',
       clip_key='full and vacate key 2: dock.unclack|0.5|1.0 -> dock.unclack|1.0|1.0',
       play='Dock clips full and vacate, key 2: the release snap at 3 ms, the wipe, the leaves springing shut at 46-56 ms (on the break-spark, key 3); the fall onto the stop is dock.fall at key 8.' + KEY_NOTE),
    ev(id='dock.tap', file='dock/tap', fn=dock_tap, takes=3, target=-31.0, cls='tick (knuckle on the grip)',
       kind='click', fade_ms=8, hit_ms=3, ref=CRAFTER, pitch=[0.95, 1.05],
       subtitle_en='Knife switch tapped', subtitle_pt='Batidinha na chave-faca',
       clip_key='tap, tap_low, tap_on key 0: dock.tap|0.5|1.0 -> dock.tap|1.0|1.0',
       play='Dock clips tap, tap_low and tap_on, key 0 (the Dock used: the switch is knocked): the knuckle at 3 ms, the hinge 9-14 ms on, gone in about 0.1 s; the switch\'s landing is dock.fall (tap) or dock.clack (tap_on, tap_low).' + KEY_NOTE),
    # ---- the Injection Rig
    ev(id='rig.cancel', file='rig/cancel', fn=rig_cancel, takes=2, target=-27.5, cls='latch (syringe starts up, small whirr)',
       kind='latch', fade_ms=15, hit_ms=3, ref=IRON_TD_OPEN, pitch=[0.97, 1.03],
       subtitle_en='Rig withdraws', subtitle_pt='Injetor recua',
       clip_key='cancel key 0: rig.cancel|0.5|1.0 -> rig.cancel|1.0|1.0',
       play='Rig clip cancel, key 0: the catch at 3 ms, the whirr gone by 100 ms (before rig.servo at key 2), the rest tick at 140-150 ms. The swing home and the holster are rig.servo (key 2) and rig.holster (key 12).' + KEY_NOTE),
    ev(id='rig.fill', file='rig/fill', fn=rig_fill, takes=2, target=-27.0, cls='glug class, soft (a liquid draw)',
       kind=('servo', 0.30), fade_ms=20, hit_ms=3, ref=BOTTLE_FILL, pitch=[0.95, 1.05],
       subtitle_en='Syringe fills', subtitle_pt='Seringa enche',
       clip_key='prime key 1: rig.fill|0.4|1.0 -> rig.fill|1.0|1.0',
       play='Rig clip prime, key 1 (the plunger\'s draw, ticks 1-5): the unstick at 3 ms, the squeak and the suction following the draw\'s speed to 200 ms, the gulp at 205 ms.' + KEY_NOTE),
    ev(id='rig.drain', file='rig/drain', fn=rig_drain, takes=2, target=-26.0, cls='hiss class, short (squirt and dribble)',
       kind=('servo', 0.30), fade_ms=20, hit_ms=3, ref=BOTTLE_EMPTY, pitch=[0.95, 1.05],
       subtitle_en='Syringe drains', subtitle_pt='Seringa esvazia',
       clip_key='unprime key 4: rig.drain|0.4|1.0 -> rig.drain|1.0|1.0',
       play='Rig clip unprime, key 4 (the plunger at the bottom of its 3 px sink): the seat at 3 ms, the squirt decaying over about 0.15 s, the bounce 46-50 ms on, drops to about 0.3 s.' + KEY_NOTE),
    # ---- the Leech Jar
    ev(id='leech.bite', file='leech/bite', fn=leech_bite, takes=3, target=-31.0, cls='tick (a wet nip)',
       kind='click', fade_ms=8, hit_ms=3, ref=BEEHIVE_DRIP, pitch=[0.95, 1.05],
       subtitle_en='Leech bites', subtitle_pt='Sanguessuga morde',
       clip_key='sample, timeline instruction "leech:bite;" at 0.3 s (key 6): not a sound key; play leech.bite from the instruction handler',
       play='Leech Jar clip sample at 0.3 s (key 6, the instruction leech:bite), at the nozzle, volume 1.0: the tsk at 3 ms, gone in about 0.1 s. The server plays vanilla hurt and honey-drink sounds on the same tick; this is the nip under them.'),
]

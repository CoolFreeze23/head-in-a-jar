"""The Chimera: the lab result that was issued a hard hat.

A company specimen cage with something pale, soft and pleased with itself grown through the bars, an eye on a tall
stalk, three serum vials for gills and a hi-vis hard hat on the director. The joke is the safety gear, so the sounds
are dry, never gore: the growth is doughy, not wet; it yawns, sniffs, gulps and coughs like a large calm animal heard
through the cage's grille (a short comb, so every breath has the cage in it), and the cage itself is the company's
steel. The eye clicks on its ball joint. On Drive it Grafts: it sniffs the arm that hit, gulps (soft, doughy,
satisfied), swells and shivers; refused, it gags politely and coughs a puff from its gill covers.

Event ids are `mecha.chimera.<name>`; the clips key them by the bare name (chimera-art.md section 5). Every file is
played from an animation keyframe, so every hit lands at 3 ms. Takes differ by construction (per-take tables). None of
the creature sounds uses the house voice: that belongs to the company talking, and the growth is not the company.

Shared parts come from the kit (`nsynth`), the Bulwark (`recipes_bulwark`: the creak and its references) and the
Hopper (`recipes_mecha`: the saturation and its references).
"""
import numpy as np
from nsynth import *
from recipes_mecha import soft, PISTON_IN, PISTON_OUT
from recipes_bulwark import creak, VAULT_FAIL, BOTTLE, EQUIP_CHAIN
from recipes_heavy import CRAFTER
from recipes_wheelhouse import WOOD_TRAPDOOR

T = TICK


# ---------------------------------------------------------------- shared parts

def grille(x, d=0.0009, g=0.35):
    """The cage's grille in front of the breath: a short feed-forward comb (0.9 ms, notches every 1.1 kHz), so the
    breath sounds as if it comes through bars. No feedback, so it adds no ring of its own."""
    n = int(round(d * SR))
    y = x.copy()
    y[n:] += g * x[:-n]
    return y


def breath_band(dur, r, lo, hi):
    """Breath noise through a moving band: lo, hi per-sample curves."""
    return sweep_bandpass(noise(dur, r), lo, hi)


# ---------------------------------------------------------------- the boot: the yawn, the crack, the shiver

YAWN = [dict(m=1.00, L=1.00, f=(64.0, 98.0, 52.0)), dict(m=0.90, L=1.12, f=(56.0, 88.0, 46.0))]


def yawn(take, r):
    """The boot's big soft yawn (boot tick 8: the cockpit tilts back and the growth swells 6-14, holds to 22 and lets
    go by 28), heard through the grille: a throat opening at 3 ms (a dry little 'hk'), a long inhale over the first
    30 % (breath rising through 400-2600 Hz), then the yawn itself, a low unvoiced-ish drone well under any human
    voice (52-98 Hz, rough, its two formants opening like a jaw: 380 -> 650 -> 420 Hz and 900 -> 1150 -> 800 Hz)
    with breath over it, and the long contented exhale (from 62 % of the way, the band falling to 250-900 Hz).
    1.00-1.12 s by take."""
    q = YAWN[take - 1]
    m, L = q['m'], q['L']
    dur = round(L + 0.50, 3)
    t = tt(dur)
    u = t / L
    # the voice: a rough low drone, unmistakably not a person (too low, too breathy, too even)
    f = np.interp(u, [0.22, 0.45, 0.80], q['f']) * m
    vo = np.sin(np.pi * np.clip((u - 0.22) / 0.58, 0, 1)) ** 1.2
    src = osc_harm(f, 40, r, 1.2) * (1 + 0.15 * np.sin(2 * np.pi * 23.0 * t))
    F1 = np.interp(u, [0.22, 0.45, 0.80], [380.0, 650.0, 420.0]) * m
    F2 = np.interp(u, [0.22, 0.45, 0.80], [900.0, 1150.0, 800.0]) * m
    voice = (norm(sweep_bandpass(src, F1 * 0.8, F1 * 1.25)) + 0.5 * norm(sweep_bandpass(src, F2 * 0.85, F2 * 1.2))
             + 0.25 * norm(lowpass(src, 300))) * vo
    # the breath: an inhale that starts quiet on the key, the yawn's breath, then the long exhale
    inh = (0.12 + 0.88 * np.clip(u / 0.30, 0, 1) ** 1.5) * np.where(u < 0.30, 1.0, np.exp(-(t - 0.30 * L) / 0.06))
    b_in = breath_band(dur, r, (400 + 200 * np.clip(u / 0.3, 0, 1)) * m, (2600 + 600 * np.clip(u / 0.3, 0, 1)) * m) * inh
    b_mid = breath_band(dur, r, np.full(len(t), 300.0 * m), np.full(len(t), 2200.0 * m)) * vo
    te = 0.62 * L
    exh = np.clip((t - te) / (0.08 * L), 0, 1) * np.where(t < te + 0.08 * L, 1.0, np.exp(-(t - te - 0.08 * L) / 0.09))
    ue = np.clip((t - te) / (0.38 * L), 0, 1)
    b_ex = breath_band(dur, r, np.full(len(t), 250.0 * m), (1800 - 900 * ue) * m) * exh
    b = 0.55 * norm(voice) + 0.40 * norm(b_in) + 0.35 * norm(b_mid) + 0.55 * norm(b_ex)
    place(b, norm(lowpass(noise_burst(300, 3000, 0.012, r), 2500)), 0.003, 0.20)
    return lowpass(grille(b), 6000)


JCRACK = [dict(m=1.00, gap=0.055, n=3), dict(m=1.12, gap=0.072, n=2), dict(m=0.90, gap=0.100, n=4)]


def joint_crack(take, r):
    """A cage joint cracking like a knuckle as the arm stretches (boot tick 24): a dry pop at 3 ms (a strike, a short
    knock at 950 Hz, a small low knock), two to four grains of grit after it, a second smaller pop 55-100 ms later, and
    the cage creaking for a moment under it. Dry: nothing rings."""
    q = JCRACK[take - 1]
    m = q['m']
    b = buf(0.45)
    h = 0.003
    place(b, strike(r, 1800 * m), h, 0.40)
    place(b, norm(modal(950 * m, [1, 2.3, 3.8], [1, 0.45, 0.2], [0.010, 0.007, 0.004], r, 0.02)), h, 0.55)
    place(b, thunk(r, 220 * m, 140 * m, 0.018, 0.008), h, 0.25)
    for j in range(q['n']):
        place(b, tooth(r, 2200 * m * r.uniform(0.9, 1.1)), h + 0.008 + 0.006 * j, 0.18 * 0.8 ** j)
    place(b, strike(r, 1800 * m), q['gap'], 0.20)
    place(b, norm(modal(820 * m, [1, 2.3, 3.8], [1, 0.45, 0.2], [0.008, 0.006, 0.004], r, 0.02)), q['gap'], 0.30)
    place(b, creak(r, 0.08, 520 * m, decay=0.004), 0.02, 0.06)
    return b


SHIVER = [dict(m=1.00, L=0.42, rate=16.0, bars=7), dict(m=1.10, L=0.50, rate=13.5, bars=9),
          dict(m=0.90, L=0.36, rate=18.5, bars=6)]


def shiver(take, r):
    """A rubbery shudder with the cage rattling (boot tick 27; graft tick 28, the satisfied one): the growth trembles
    at 13.5-18.5 Hz for 0.36-0.50 s, dying away (rubbery: a 180-1100 Hz band and a low stick-slip voice at 240 Hz that
    quivers with it; dry, no wet), and the cage's bars knock at the tremble's peaks (steel ticks at 1.5-2.4 kHz), the
    first on the key."""
    q = SHIVER[take - 1]
    m, L, rate = q['m'], q['L'], q['rate']
    dur = round(L + 0.35, 3)
    b = buf(dur)
    t = tt(dur)
    env = np.where(t < L, np.clip(1 - t / L, 0, 1) ** 1.6, 0.0) * np.clip((t - 0.003) / 0.012, 0, 1)
    trem = (0.5 + 0.5 * np.cos(2 * np.pi * rate * t)) ** 2
    rubber = bandpass(noise(dur, r), 180 * m, 1100 * m) * env * (0.35 + 0.65 * trem)
    voice = lowpass(osc_harm(240 * m * (1 + 0.04 * np.sin(2 * np.pi * rate * t)), 10, r, 1.5), 1400) * env * trem
    b += 0.55 * norm(rubber) + 0.22 * norm(voice)
    for k in range(q['bars']):
        at = 0.003 + k / rate
        if at > L:
            break
        f = r.uniform(1500, 2400) * m
        place(b, norm(modal(f, [1, 2.756, 5.404], [1, 0.4, 0.15], [0.008, 0.005, 0.003], r, 0.02)), at,
              0.40 * (1 - at / L) ** 0.8)
    return b


# ---------------------------------------------------------------- the idle tic: the twitch and the tut

TWITCH = [dict(m=1.00, jerk=0.045, second=0.200), dict(m=1.10, jerk=0.038, second=0.160),
          dict(m=0.90, jerk=0.055, second=0.240)]


def twitch(take, r):
    """The left forearm jerking up on its own (idle_tic tick 5, easeOutExpo): a small servo kicked into a spurt it did
    not ask for. The gear lash at 3 ms, the motor's spike (whine 320 + 900 s Hz, up in 8 ms and gone in 38-55 ms), the
    arm hitting the top of its jerk (a small knock), and the second little jerk four ticks later (tick 9), smaller."""
    q = TWITCH[take - 1]
    m, j = q['m'], q['jerk']
    dur = round(q['second'] + 0.35, 3)
    b = buf(dur)
    t = tt(dur)

    def spike(at, jj):
        x = np.clip((t - at) / 0.008, 0, 1) * np.exp(-np.maximum(t - at, 0) / jj)
        return x * (t >= at)

    s = np.clip(spike(0.003, j) + 0.4 * spike(q['second'], 0.6 * j), 0, 1)
    b += motor(t, s, r, 320 * m, 900 * m, grit=0.35, air=0.10, whine=0.40, rotor_div=6)
    place(b, strike(r, 2500 * m), 0.003, 0.25)
    place(b, mix(norm(modal(700 * m, [1, 2.756], [1, 0.4], [0.012, 0.008], r)), 0.4 * thunk(r, 220, 150, 0.015, 0.008)),
          0.003 + 2 * j, 0.30)
    place(b, norm(modal(780 * m, [1, 2.756], [1, 0.4], [0.008, 0.005], r)), q['second'] + 0.02, 0.12)
    return b


TUT = [dict(m=1.00, gap=0.120), dict(m=1.10, gap=0.150), dict(m=0.92, gap=0.180)]


def tut(take, r):
    """The eye tutting at the arm (idle_tic tick 15, the eye tilting in disapproval): its ball joint clicking twice,
    'tut-tut', 120-180 ms apart, the second softer. Each click a tiny strike and the joint's hollow little 'tock' at
    760 Hz, 4-9 ms."""
    q = TUT[take - 1]
    m = q['m']
    b = buf(q['gap'] + 0.25)
    for k, at in enumerate((0.003, 0.003 + q['gap'])):
        g = 1.0 - 0.3 * k
        place(b, strike(r, 3000 * m), at, 0.15 * g)
        place(b, norm(modal(760 * m, [1, 1.9, 3.1], [1, 0.45, 0.2], [0.009, 0.006, 0.004], r, 0.02)), at, 0.60 * g)
        place(b, thunk(r, 300 * m, 220 * m, 0.008, 0.004), at, 0.20 * g)
        place(b, norm(noise_burst(500, 2500, 0.002, r)), at, 0.20 * g)
    return b


# ---------------------------------------------------------------- the Graft: sniff, gulp; refused: gag, cough

SNIFF = [dict(m=1.00, at=(0.003, 0.150, 0.290), L=(0.085, 0.070, 0.105)),
         dict(m=1.12, at=(0.003, 0.140), L=(0.090, 0.120)),
         dict(m=0.90, at=(0.003, 0.165, 0.300), L=(0.070, 0.075, 0.125))]


def sniff(take, r):
    """Two or three short sniffs at the forearm (graft tick 5, graft_refused tick 7), through the grille: each a quick
    inhale (up in about 10 ms, 70-125 ms long, tailing off in 10-19 ms), breath through a band rising as it pulls
    (450-2200 -> 650-3000 Hz) with a nasal resonance near 1.1 kHz; the last one longer, as if checking."""
    q = SNIFF[take - 1]
    m = q['m']
    dur = round(q['at'][-1] + q['L'][-1] + 0.30, 3)
    b = buf(dur)
    for k, (at, L) in enumerate(zip(q['at'], q['L'])):
        tail = 0.15 * L
        d = round(0.6 * L + 7 * tail, 3)
        t = tt(d)
        u = np.clip(t / L, 0, 1)
        env = np.clip(t / (0.12 * L), 0, 1) * np.where(t < 0.6 * L, 1.0, np.exp(-(t - 0.6 * L) / tail))
        air = breath_band(d, r, (450 + 200 * u) * m, (2200 + 800 * u) * m) * env
        nose = bandpass(noise(d, r), 950 * m * (1 + 0.06 * k), 1300 * m * (1 + 0.06 * k)) * env
        place(b, 0.8 * norm(air) + 0.35 * norm(nose), at, 1.0 - 0.1 * k)
    return grille(b)


GULP = [dict(m=1.00, swell=0.16, puff=0.26), dict(m=1.10, swell=0.13, puff=0.22), dict(m=0.90, swell=0.20, puff=0.31)]


def squelch_gulp(take, r):
    """The Graft's gulp and swell (graft tick 12: the growth gulps and swells 24 % on 12-14; build ticks 10, 30, 50,
    70, the four feeds): soft, doughy, satisfied, and dry. A throat 'glk' at 3 ms (a falling tone 420 -> 200 Hz and
    a dull dough knock, no bubbles), the dough stretching as the growth inflates (a 150-1400 Hz band swelling over
    0.13-0.20 s, a low tone rising 70 -> 115 Hz under it, the skin creaking low), then a contented puff out through the
    grille 0.22-0.31 s in (its band falling from 1.2-2.6 kHz to 0.5-1.3 kHz)."""
    q = GULP[take - 1]
    m, sw = q['m'], q['swell']
    dur = 1.10
    b = buf(dur)
    t = tt(dur)
    h = 0.003
    place(b, sweep_sine(420 * m, 200 * m, 0.030, 0.012), h, 0.45)
    place(b, thunk(r, 150 * m, 90 * m, 0.040, 0.018), h, 0.22)
    place(b, norm(lowpass(noise(0.3, r), 1600 * m) * exp_env(0.3, 0.022, 0.002)), h, 0.35)
    u = np.clip((t - 0.02) / sw, 0, 1)
    es = np.sin(np.pi * np.clip((t - 0.02) / (2 * sw), 0, 1)) * (t > 0.02)
    dough = bandpass(noise(dur, r), 150 * m, 1400 * m) * es * (1 + 0.3 * np.sin(2 * np.pi * 7.0 * t))
    tone = osc_harm((70 + 45 * u) * m, 6, r, 1.6) * es
    b += 0.40 * norm(dough) + 0.12 * norm(tone)
    place(b, creak(r, sw, 260 * m, rate=(0.018, 0.030), decay=0.006, rise=0.25), 0.03, 0.12)
    p = q['puff']
    pr = np.clip((t - p) / 0.03, 0, 1) * np.where(t < p + 0.03, 1.0, np.exp(-(t - p - 0.03) / 0.08)) * (t > p)
    up = np.clip((t - p) / 0.25, 0, 1)
    puff = breath_band(dur, r, (1200 - 700 * up) * m, (2600 - 1300 * up) * m) * pr
    b += 0.45 * norm(puff)
    return grille(b, g=0.25)


RETCH = [dict(m=1.00, hacks=(0.003, 0.115), g=(1.0, 0.55), tau=0.050),
         dict(m=1.10, hacks=(0.003, 0.105, 0.190), g=(1.0, 0.50, 0.25), tau=0.042),
         dict(m=0.90, hacks=(0.003,), g=(1.0,), tau=0.065)]


def retch_dry(take, r):
    """The refusal's recoil (graft_refused tick 12, the body snapping back): a dry hacking reflex, short, polite. One
    to three hacks, each a glottal catch (a strike and a bright tick), a throat-band burst through two formants
    (600-900 and 1200-1800 Hz), a 75 Hz rasp and a small chest knock; through the grille. Nothing wet."""
    q = RETCH[take - 1]
    m = q['m']
    dur = round(q['hacks'][-1] + 0.45, 3)
    b = buf(dur)
    for at, g in zip(q['hacks'], q['g']):
        tau = q['tau']
        d = round(7 * tau + 0.01, 3)
        t = tt(d)
        env = np.clip(t / 0.004, 0, 1) * np.exp(-t / tau)
        n = len(t)
        body = (norm(bandpass(noise(d, r), 600 * m, 900 * m)) + 0.7 * norm(bandpass(noise(d, r), 1200 * m, 1800 * m))
                + 0.4 * norm(bandpass(noise(d, r), 400 * m, 2600 * m)))
        rasp = bandpass(osc_harm(np.full(n, 75.0 * m) * (1 + 0.05 * np.sin(2 * np.pi * 11 * t)), 30, r, 0.8), 300, 3000)
        place(b, 0.75 * norm(body * env) + 0.35 * norm(rasp * env ** 1.5), at, g)
        place(b, strike(r, 1500 * m), at, 0.25 * g)
        place(b, norm(noise_burst(1500, 5000, 0.004, r)), at, 0.20 * g)
        place(b, thunk(r, 120 * m, 80 * m, 0.030, 0.012), at, 0.25 * g)
    return grille(b)


COUGH = [dict(m=1.00, tau=0.075, flaps=(0.016, 0.024)), dict(m=1.10, tau=0.060, flaps=(0.012, 0.021)),
         dict(m=0.90, tau=0.095, flaps=(0.020, 0.031))]


def cough(take, r):
    """A puff of a cough out through the gill covers (graft_refused tick 18, the covers flaring on 17-18 and the
    clouds on 19-20): one cough at 3 ms (a glottal catch, a 300-3200 Hz burst with a 500-800 Hz throat formant and a
    30 ms buzz at 90 Hz), the two covers flapping open 12-31 ms in (small damped knocks at 1.1 kHz), and the air
    puffing out of the vents (800-5000 Hz, 80 ms)."""
    q = COUGH[take - 1]
    m, tau = q['m'], q['tau']
    b = buf(0.80)
    d = round(7 * tau + 0.01, 3)
    t = tt(d)
    env = np.clip(t / 0.003, 0, 1) * np.exp(-t / tau)
    burst = norm(bandpass(noise(d, r), 300 * m, 3200 * m)) + 0.8 * norm(bandpass(noise(d, r), 500 * m, 800 * m))
    buzz = bandpass(osc_harm(np.full(len(t), 90.0 * m), 25, r, 0.9), 200, 2500) * np.clip(t / 0.003, 0, 1) * np.exp(-t / 0.012)
    place(b, 0.8 * norm(burst * env) + 0.3 * norm(buzz), 0.003, 1.0)
    place(b, strike(r, 1500 * m), 0.003, 0.20)
    for k, at in enumerate(q['flaps']):
        place(b, norm(modal(1100 * m * (1 + 0.05 * k), [1, 2.3], [1, 0.4], [0.006, 0.004], r)), at, 0.18)
    tp = tt(0.6)
    puff = bandpass(noise(0.6, r), 800 * m, 5000 * m) * np.clip(tp / 0.010, 0, 1) * np.exp(-tp / 0.080)
    place(b, norm(puff), 0.020, 0.40)
    return grille(b)


# ---------------------------------------------------------------- the build: standing up, the hard hat

STAND = [dict(m=1.00, L=0.40, f=600.0), dict(m=1.10, L=0.46, f=660.0), dict(m=0.90, L=0.35, f=560.0)]


def stand_creak(take, r):
    """The cage frame creaking as the Chimera stands for the first time (build tick 84; the hips rise 80-92): the
    joints unlocking at 3 ms (a tooth), the welded bars taking the load (stick-slip at 560-660 Hz drifting up over
    0.35-0.46 s), the frame lower under it, a few bars ticking, the growth's soft weight shifting (low noise), and the
    frame settling with a knock as it gets there (640 Hz)."""
    q = STAND[take - 1]
    m, L = q['m'], q['L']
    dur = round(L + 0.45, 3)
    b = buf(dur)
    t = tt(dur)
    place(b, tooth(r, 2000 * m), 0.003, 0.30)
    place(b, creak(r, L, q['f'] * m, rate=(0.022, 0.040), decay=0.007, rise=0.20), 0.003, 0.80)
    place(b, creak(r, 0.8 * L, 0.70 * q['f'] * m, rate=(0.030, 0.050), decay=0.009), 0.05, 0.35)
    for _ in range(3):
        place(b, norm(modal(r.uniform(1600, 2200) * m, [1, 2.756], [1, 0.4], [0.008, 0.005], r)), r.uniform(0.08, L), 0.15)
    mass = lowpass(noise(dur, r), 400) * np.sin(np.pi * np.clip(t / L, 0, 1)) ** 2
    b += 0.12 * norm(mass)
    place(b, mix(0.5 * thunk(r, 130, 85, 0.035, 0.015), norm(modal(640 * m, [1, 2.756], [1, 0.4], [0.025, 0.016], r))),
          L, 0.30)
    return b


HAT = [dict(m=1.00, b1=0.070, b2=0.125), dict(m=1.10, b1=0.062, b2=0.112), dict(m=0.90, b1=0.085, b2=0.150)]


def hard_hat_clonk(take, r):
    """Health and safety (build tick 207, the hat landing on 205-207 with a bounce): a plastic hard hat dropped onto the
    jar's lid. A hollow plastic clonk at 3 ms (a small strike, the shell's modes at 820 Hz x 1, 1.47, 2.09, 2.56, 3.3,
    damped in 4-16 ms as plastic is, the dome's air at 420 Hz, a little knock, the brass lid ticking under it), then it
    rocks: a smaller clonk 62-85 ms later and a last one 112-150 ms in."""
    q = HAT[take - 1]
    m = q['m']
    b = buf(0.55)

    def hit(dk):
        return mix(0.25 * strike(r, 2200 * m),
                   0.60 * norm(modal(820 * m, [1, 1.47, 2.09, 2.56, 3.3], [1, 0.7, 0.5, 0.3, 0.2],
                                     [x * dk for x in (0.016, 0.012, 0.009, 0.006, 0.004)], r, 0.02)),
                   0.30 * sweep_sine(420 * m, 380 * m, 0.020 * dk, 0.01),
                   0.10 * thunk(r, 200 * m, 140 * m, 0.020, 0.008),
                   0.08 * norm(modal(2300 * m, [1, 2.4], [1, 0.3], [0.006, 0.004], r)))

    place(b, norm(hit(1.0)), 0.003, 1.0)
    place(b, norm(hit(0.8)), q['b1'], 0.35)
    place(b, norm(hit(0.6)), q['b2'], 0.12)
    return b


# ---------------------------------------------------------------- references (measured as the game plays them)

HORSE_BREATHE = dict(event='entity.horse.breathe', file='mob__horse__breathe1.ogg', vol=1.0, pitch=1.0)
SNIFFING = dict(event='entity.sniffer.sniffing', file='mob__sniffer__sniffing1.ogg', vol=1.0, pitch=1.0)
BURP = dict(event='entity.player.burp', file='random__burp.ogg', vol=0.5, pitch=0.95)

BASE = dict(category='NEUTRAL', attenuation=12, fixed_range=False, volume=1.0)
KEY_NOTE = (' Keyframe volume 1.0 and pitch 1.0 (the file carries the loudness and the size; the clip\'s proposed '
            'volume and pitch were placeholders).')


def ev(name, **kw):
    d = dict(BASE)
    d.update(id=f'mecha.chimera.{name}', file=f'mecha/chimera/{name}')
    d.update(kw)
    return d


SOUNDS = [
    # ---- the boot
    ev('yawn', fn=yawn, takes=2, target=-27.0, cls='breath (the boot\'s yawn, through the grille)',
       kind=('yawn', 1.00), fade_ms=40, hit_ms=3, ref=HORSE_BREATHE, pitch=[0.97, 1.03], attenuation=16,
       subtitle_en='Chimera yawns', subtitle_pt='Quimera boceja',
       play='boot tick 8: the inhale runs with the tilt back (to tick 14), the yawn holds to about tick 22, the exhale lets go by 28. 1.00 / 1.12 s by take. Not a voice: keep the pitch near 1.0, or it starts to sound like one.' + KEY_NOTE),
    ev('joint_crack', fn=joint_crack, takes=3, target=-26.0, cls='ratchet (a joint cracking)', kind='click',
       fade_ms=12, hit_ms=3, ref=VAULT_FAIL, pitch=[0.95, 1.05],
       subtitle_en='Chimera cracks a joint', subtitle_pt='Quimera estala uma junta',
       play='boot tick 24 (the stretch at its widest). The second pop 55-100 ms after the first.' + KEY_NOTE),
    ev('shiver', fn=shiver, takes=3, target=-26.5, cls='rattle (a rubbery shudder)', kind=('shiver', 0.36),
       fade_ms=20, hit_ms=3, ref=EQUIP_CHAIN, pitch=[0.95, 1.05],
       subtitle_en='Chimera shudders', subtitle_pt='Quimera estremece',
       play='boot tick 27 and graft tick 28 (the satisfied shiver after the gulp). 0.36-0.50 s by take, dying away; the bars knock on the tremble.' + KEY_NOTE),
    # ---- the idle tic
    ev('twitch', fn=twitch, takes=3, target=-28.5, cls='servo (an involuntary jerk)', kind=('servo', 0.16),
       fade_ms=15, hit_ms=3, ref=PISTON_OUT, pitch=[0.95, 1.05],
       subtitle_en='Arm twitches unbidden', subtitle_pt='Braço se mexe sozinho',
       play='idle_tic tick 5 (the left forearm jerks up, easeOutExpo); the second little jerk lands 160-240 ms on (tick 9). If the code mirrors the tic onto the right arm, the same file.' + KEY_NOTE),
    ev('tut', fn=tut, takes=3, target=-31.0, cls='tick (the eye\'s ball joint)', kind=('taps', 0.13), fade_ms=10,
       hit_ms=3, ref=CRAFTER, pitch=[0.97, 1.03], attenuation=8,
       subtitle_en='Eye tuts disapprovingly', subtitle_pt='Olho estala, reprovando',
       play='idle_tic tick 15 (the eye tilts at the arm): two clicks, tut-tut, 120-180 ms apart.' + KEY_NOTE),
    # ---- the Graft
    ev('sniff', fn=sniff, takes=3, target=-28.5, cls='breath (sniffs)', kind=('sniff', 0.30), fade_ms=20, hit_ms=3,
       ref=SNIFFING, pitch=[0.96, 1.04], subtitle_en='Chimera sniffs', subtitle_pt='Quimera fareja',
       play='graft tick 5 and graft_refused tick 7 (the forearm at the eye): two or three sniffs, the first on the key.' + KEY_NOTE),
    ev('squelch_gulp', fn=squelch_gulp, takes=3, target=-27.0, cls='glug (a soft doughy gulp)', kind='glug',
       fade_ms=40, hit_ms=3, ref=BURP, pitch=[0.97, 1.03], attenuation=16,
       subtitle_en='Graft goes down well', subtitle_pt='O enxerto desce bem',
       play='graft tick 12 (the gulp on the key, the swell over ticks 12-14, the puff out 0.22-0.31 s in) and build ticks 10, 30, 50, 70 (the feeds). In the build the clip\'s falling pitch (1.05, 0.95, 0.85, 0.75: the growth getting bigger) is deliberate and may stay; the volume should be 1.0. Everywhere else pitch 1.0.' + KEY_NOTE),
    ev('retch_dry', fn=retch_dry, takes=3, target=-26.0, cls='refusal (a dry gag)', kind=('retch', 0.13),
       fade_ms=20, hit_ms=3, ref=VAULT_FAIL, pitch=[0.96, 1.04],
       subtitle_en='Chimera gags, politely', subtitle_pt='Quimera engasga, educada',
       play='graft_refused tick 12 (the recoil). It is part of the refusal: every refused Drive press must reach graft_refused and this sound, never skip it. One to three hacks by take.' + KEY_NOTE),
    ev('cough', fn=cough, takes=3, target=-26.5, cls='hiss (a cough out of the gills)', kind=('cough', 0.20),
       fade_ms=25, hit_ms=3, ref=BOTTLE, pitch=[0.95, 1.05],
       subtitle_en='Gills cough', subtitle_pt='Guelras tossem',
       play='graft_refused tick 18 (the gill covers flare on 17-18, the clouds on 19-20): the cough on the key, the covers flap 12-31 ms in, the puff out after.' + KEY_NOTE),
    # ---- the build
    ev('stand_creak', fn=stand_creak, takes=3, target=-26.5, cls='servo (the cage taking its weight)',
       kind=('servo', 0.40), fade_ms=25, hit_ms=3, ref=PISTON_IN, pitch=[0.97, 1.03],
       subtitle_en='Cage creaks upright', subtitle_pt='Gaiola range ao se erguer',
       play='build tick 84 (the hips rise 80-92): the creak runs 0.35-0.46 s by take and the frame settles with a knock at the end, near tick 92 (build:stand).' + KEY_NOTE),
    ev('hard_hat_clonk', fn=hard_hat_clonk, takes=3, target=-27.5, cls='latch (hollow plastic)', kind='latch',
       fade_ms=12, hit_ms=3, ref=WOOD_TRAPDOOR, pitch=[0.95, 1.05],
       subtitle_en='Hard hat issued', subtitle_pt='Capacete entregue',
       play='build tick 207 (the hat lands on the lid; build:hat on 206). Also wherever the hat drops back onto the lid after board or eject, if the code wants one. It rocks twice after.' + KEY_NOTE),
]

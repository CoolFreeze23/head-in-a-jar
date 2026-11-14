"""The house family: the company talking to the player, and the few field sounds that go with it.

The stingers are all on the house voice (the FM 'vibraphone' of recipes_feedback: carrier:modulator 1:3.5, the index
falling from about 2 to 0.3, a 3-6 ms attack) and take their notes from the company motif C5 E5 G5 C6 (523.25,
659.26, 783.99, 1046.50 Hz):
  jingle           C5 E5 G5 C6 rising 120 ms apart, overlapping, the last note 30 cents flat over a muted wooden bonk
  jingle_shutdown  C6 G5 E5 C5 falling, the last note 50 cents flat and stopped by a felt damper
  jingle_botch     C5 Eb5 G5 B4: the third gone minor, the last note dropping instead of rising, a louder bonk
  desk_ding        one C6 with a beating twin 2 Hz below, a long ring, after a small plunger tick (the counter bell)
  link_restored    ends on the done chime's G5 -> C6
  mecha_boot       a servo spin-up and two relays running into the jingle
No boings, no trombone, and not the G-E-C figure. The field sounds (the Surgical Saw's cut, the link to the body
breaking, the Gene Splicer, a mutation, a grumbling employee, a plate bolted on at the Assembly Gantry) are built from
the house parts: strikes, iron bodies, ratchet teeth, relay clicks, the motor, crackle, Minnaert bubbles and the
synthesised voice of recipes_machines3 (a glottal buzz through moving formant bands; no words, nothing recorded).

Every statement here comes from the recipe or from a measurement. Times inside a
file are seconds; one tick is 50 ms. Takes differ by construction (per-take tables), not by luck of the seed.
"""
import numpy as np
from nsynth import *
from recipes_body import soft_square, chirp_sine
from recipes_machines3 import voice

T = TICK
C5, E5, G5, C6 = 523.25, 659.26, 783.99, 1046.50
EB5, B4, G6 = 622.25, 493.88, 1567.98


def cents(f, c):
    return f * 2 ** (c / 1200.0)


# ---------------------------------------------------------------- shared pieces

def wood_bonk(r, f=185.0, d=1.0):
    """A muted wooden knock: a hollow wooden body (modes about 185 Hz x 1, 2.57, 4.3, 10-35 ms), a felt-padded
    strike (300-3000 Hz noise, 3 ms) and a small thunk 170 -> 110 Hz, all low-passed at 1.8 kHz (muted)."""
    body = modal(f, [1, 2.57, 4.3], [1, 0.45, 0.2], [0.035 * d, 0.018 * d, 0.010 * d], r, 0.02)
    tick = noise_burst(300, 3000, 0.003, r, attack=0.0008)
    k = mix(0.8 * norm(body), 0.35 * norm(tick), 0.45 * thunk(r, 170, 110, 0.025, 0.012))
    return norm(lowpass(np.concatenate([k, np.zeros(int(0.02 * SR))]), 1800))


def jingle_body(b, r, at, notes, gains, taus, last_cents=0.0, last_hold=0.0, spacing=0.120, index=(2.0, 0.3),
                attack=0.006, bonk=0.25, bonk_f=185.0, damper=None, partner=None):
    """Lay a four-note figure on the house voice into b from `at`: notes `spacing` apart, overlapping, each with a soft
    attack; the last note `last_cents` off pitch, held `last_hold` s before its decay. bonk: the muted wooden knock under
    the last note at this gain (0.25 = 12 dB under a note at gain 1). damper: (seconds after the last note starts,
    release tau): a felt damper stops it. partner: (Hz, gain) a beating twin on the last note."""
    n = len(notes)
    for k, f in enumerate(notes):
        last = k == n - 1
        if last:
            f = cents(f, last_cents)
        x = fm_note(f, taus[k], r, index=index, attack=attack, hold=last_hold if last else 0.0,
                    partner=partner if last else None)
        if last and damper:
            td, tau = damper
            t = np.arange(len(x)) / SR
            x = x * np.where(t < td, 1.0, np.exp(-(t - td) / tau))
            x = x[:int((td + 7 * tau) * SR)]
        place(b, x, at + k * spacing, gains[k])
    t_last = at + (n - 1) * spacing
    if bonk:
        place(b, wood_bonk(r, bonk_f), t_last, bonk)
    return t_last


# ---------------------------------------------------------------- the jingles

def jingle(take, r):
    """The Noggin & Sons jingle: C5 E5 G5 C6 120 ms apart from 3 ms, overlapping (each rings with a 0.09 s decay),
    6 ms attacks; the last C6 30 cents flat, held 0.12 s and fading over about 0.6 s, a muted wooden bonk under it
    12 dB down. Low-passed at 5 kHz (the done chime uses 7 kHz; 5 keeps the centroid within an octave of the flute)."""
    b = buf(1.75)
    jingle_body(b, r, 0.003, [C5, E5, G5, C6], [0.72, 0.78, 0.84, 1.0], [0.09, 0.09, 0.09, 0.13],
                last_cents=-30.0, last_hold=0.12)
    return lowpass(b, 5000)


def jingle_shutdown(take, r):
    """The jingle signing off: C6 G5 E5 C5 130 ms apart, softer (index 1.6 -> 0.3); the last C5 50 cents flat, let ring
    0.42 s and then stopped by a felt damper (released with a 22 ms time constant, gone in about 0.1 s), a very soft
    felt touch at the damper."""
    b = buf(1.25)
    tl = jingle_body(b, r, 0.003, [C6, G5, E5, C5], [1.0, 0.92, 0.88, 0.95], [0.085, 0.09, 0.095, 0.16],
                     last_cents=-50.0, spacing=0.130, index=(1.6, 0.3), bonk=0.0, damper=(0.42, 0.022))
    place(b, norm(lowpass(noise_burst(200, 1500, 0.010, r, attack=0.003), 1200)), tl + 0.42, 0.06)
    return lowpass(b, 6500)


def jingle_botch(take, r):
    """The jingle going wrong: C5 Eb5 G5 then B4 (the third gone minor, the last note dropping where it should rise),
    120 ms apart, the B4 held 0.10 s with a sour twin 3 Hz above, and the wooden bonk louder (6 dB under the notes)
    and a little lower (165 Hz)."""
    b = buf(1.75)
    jingle_body(b, r, 0.003, [C5, EB5, G5, B4], [0.72, 0.78, 0.84, 1.0], [0.09, 0.09, 0.09, 0.14],
                last_hold=0.10, bonk=0.50, bonk_f=165.0, partner=(3.0, 0.35))
    return lowpass(b, 5000)


DING = [dict(tau=0.165, pg=0.50, i0=1.7), dict(tau=0.195, pg=0.28, i0=1.5)]


def desk_ding(take, r):
    """The polite desk bell: the palm on the plunger (a small steel tick at 3 ms), then at 10 ms one C6 on the house
    voice with a long ring (decay 0.165 / 0.195 s, heard about 0.8 s) and a twin 2 Hz below beating slowly (at 0.50 /
    0.28 of the note). Not the chair's ding (that one's twin is 2 Hz above, with no plunger)."""
    p = DING[take - 1]
    b = buf(0.003 + 0.007 + 7 * p['tau'] + 0.05)
    place(b, norm(mix(modal(3200, [1, 1.6], [1, 0.4], [0.004, 0.0025], r, 0.03), 0.4 * norm(noise_burst(3000, 9000, 0.0012, r)))),
          0.003, 0.10)
    place(b, fm_note(C6, p['tau'], r, index=(p['i0'], 0.25), attack=0.003, partner=(-2.0, p['pg'])), 0.010, 1.0)
    return lowpass(b, 8000)


# ---------------------------------------------------------------- the Surgical Saw in the field

CUTS = [dict(rate=190.0, band=(3500, 11000), L=(0.058, 0.052), snip=5200.0, d=1.00, gap=0.012),
        dict(rate=235.0, band=(4200, 12500), L=(0.052, 0.046), snip=5900.0, d=0.80, gap=0.016),
        dict(rate=160.0, band=(3000, 10000), L=(0.064, 0.056), snip=4700.0, d=1.30, gap=0.008)]
SNIP_AT = 3 * T  # the snip lands exactly 3 ticks in, with body.cork's pop


def rasp(r, L, rate, band, bright=1.0):
    """One quick stroke of a fine serrated blade: noise in `band` gated by the serration (pulses at `rate` per second,
    speeding up 20 % through the stroke), swelling over the first 40 % of L (raised cosine) and then released with a
    time constant of L/5 (generated 7 of them long, so the stroke never stops on a step)."""
    a = 0.4 * L
    tau = L / 5
    d = a + 7 * tau
    t = tt(d)
    u = np.clip(t / L, 0, 1)
    env = np.where(t < a, 0.5 - 0.5 * np.cos(np.pi * np.clip(t / a, 0, 1)), np.exp(-(t - a) / tau))
    gate = (0.5 + 0.5 * np.cos(2 * np.pi * np.cumsum(rate * (0.9 + 0.2 * u)) / SR)) ** 4
    x = bandpass(noise(d, r), band[0] * bright, min(band[1] * bright, 16000)) * (0.35 + 0.65 * gate) * env
    return norm(x)


def booth_cut(take, r):
    """The Surgical Saw cutting a head free in the field: two quick rasping strokes of a fine blade (3 ms and about
    75 ms, 46-64 ms each, the second brighter and a little shorter), then at exactly 150 ms a clean snip: a strike, two
    blade modes 3 ms apart (4.7-5.9 kHz, ringing 20-40 ms) and a tiny steel shing."""
    p = CUTS[take - 1]
    d = p['d']
    b = buf(0.75)
    L1, L2 = p['L']
    place(b, strike(r, 5000), 0.003, 0.12)  # the blade touching
    place(b, rasp(r, L1, p['rate'], p['band']), 0.003, 0.30)
    place(b, rasp(r, L2, p['rate'] * 1.15, p['band'], 1.12), 0.003 + L1 + p['gap'], 0.34)
    place(b, strike(r, 4000), SNIP_AT, 0.90)
    for k, (fm, g) in enumerate([(1.0, 0.70), (1.07, 0.45)]):
        place(b, norm(modal(p['snip'] * fm, [1, 1.47, 2.31], [1, 0.5, 0.3], [0.032 * d, 0.020 * d, 0.011 * d], r, 0.01)),
              SNIP_AT + 0.003 * k, g)
    place(b, norm(noise_burst(4000, 12000, 0.004, r)), SNIP_AT, 0.40)
    place(b, norm(modal(p['snip'] * 1.6, [1, 2.32], [1, 0.4], [0.060 * d, 0.035 * d], r)), SNIP_AT + 0.002, 0.10)
    return highpass(b, 1500, 2)


# ---------------------------------------------------------------- the link to the body

LOST = [dict(band=(1500, 6000), whine=15000.0, wtau=0.22, st=0.30), dict(band=(1200, 5200), whine=14500.0, wtau=0.27, st=0.30)]


def link_lost(take, r):
    """The link to the body breaking: static rises for 300 ms (dense crackle and hiss through a band that opens, level
    rising as u^2), then the picture tube switching off at 300 ms: a 'thwump' (a sine falling 1200 -> 60 Hz in 250 ms,
    exponential glide, decaying), a click (strike and relay), and the flyback's whine near 15 kHz (faint under the
    static and through the thwump, then dying over about 0.5 s from 550 ms with a 0.22 / 0.27 s decay, sagging 4 %).
    The static opens on one faint crackle at 3 ms (the link faltering). Every layer decays to silence; nothing is cut."""
    p = LOST[take - 1]
    ts0 = p['st']
    hold = ts0 + 0.25  # the whine holds through the thwump, then dies
    dur = round(0.003 + hold + 7 * p['wtau'] + 0.06, 3)
    b = buf(dur)
    st = crackle(r, ts0 + 0.02, 600, 9000, spacing=(0.002, 0.009), width=(0.001, 0.003), first_gain=0.3)
    n = len(st)
    ts = np.arange(n) / SR
    u = np.clip(ts / ts0, 0, 1)
    hiss = bandpass(noise(n / SR, r), *p['band'])
    lvl = (0.04 + 0.96 * u ** 2) * np.where(ts < ts0, 1.0, np.exp(-(ts - ts0) / 0.012))
    stat = sweep_bandpass(0.7 * norm(st) + 0.5 * norm(hiss), p['band'][0] * (0.6 + 0.4 * u), p['band'][1] * (0.7 + 0.3 * u))
    place(b, norm(stat) * lvl, 0.003, 0.55)
    place(b, norm(noise_burst(1200, 8000, 0.003, r, attack=0.0004)), 0.003, 0.10)
    # the thwump
    d = 0.003 + 9 * 0.10  # 9 decay times, so the thwump's tail is under -78 dB where it ends
    t = tt(d)
    f = 60.0 * (1200.0 / 60.0) ** (1 - np.clip(t / 0.25, 0, 1))
    th = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.clip(t / 0.001, 0, 1) * np.exp(-t / 0.10)
    place(b, th, ts0 + 0.003, 1.0)
    place(b, strike(r, 2500), ts0 + 0.003, 0.45)
    place(b, relay_click(r, 1500), ts0 + 0.003, 0.30)
    # the whine: faint from the start, then dying after the switch-off
    wd = hold + 7 * p['wtau'] + 0.01
    tw = tt(wd)
    fw = p['whine'] * (1 - 0.04 * np.clip((tw - hold) / (4 * p['wtau']), 0, 1))
    wenv = np.clip(tw / 0.02, 0, 1) * np.where(tw < ts0, 0.5 + 0.5 * tw / ts0, 1.0) * np.where(tw < hold, 1.0, np.exp(-(tw - hold) / p['wtau']))
    place(b, np.sin(2 * np.pi * np.cumsum(fw) / SR) * wenv, 0.003, 0.08)
    return b


RESTORED = [dict(f0=420.0, pulse=0.030, gap=0.060, tau=0.075), dict(f0=360.0, pulse=0.036, gap=0.066, tau=0.085)]


def link_restored(take, r):
    """The link restored: a tuning sweep (a sine gliding up from 360-420 Hz onto G6, 1568 Hz, over 0.22 s, the way a
    dial is tuned in) inside static whose band narrows onto the tone and fades as it locks; two short pager pulses on G6
    (soft squares 30-36 ms, 60-66 ms apart); then the done chime on the house voice: G5, and C6 100 ms later."""
    p = RESTORED[take - 1]
    dur = 1.20
    b = buf(dur)
    sw = 0.22
    t = tt(sw + 0.06)
    u = np.clip(t / sw, 0, 1)
    f = G6 - (G6 - p['f0']) * (1 - u) ** 2.2
    tone_env = np.clip(t / 0.004, 0, 1) * (0.5 + 0.5 * u) * np.where(t < sw, 1.0, np.exp(-(t - sw) / 0.010))
    place(b, np.sin(2 * np.pi * np.cumsum(f) / SR) * tone_env, 0.003, 0.30)
    stn = crackle(r, sw, 500, 8000, spacing=(0.002, 0.008), width=(0.001, 0.003), first_gain=0.3)[:len(t)]
    stn = np.concatenate([stn, np.zeros(len(t) - len(stn))]) + 0.5 * norm(noise(len(t) / SR, r))
    lo = 500 + (G6 / 1.15 - 500) * u
    hi = 8000 - (8000 - G6 * 1.15) * u
    st_env = np.clip(t / 0.01, 0, 1) * (1 - u) ** 1.5 * np.where(t < sw, 1.0, 0.0)
    place(b, norm(sweep_bandpass(stn, lo, hi)) * st_env, 0.003, 0.30)
    at = 0.003 + sw + 0.02
    for k in range(2):
        place(b, soft_square(G6, p['pulse'], r, h3=0.10, h5=0.03), at + k * p['gap'], 0.32)
    at += p['gap'] + p['pulse'] + 0.05
    place(b, fm_note(G5, p['tau'], r, index=(2.0, 0.3)), at, 0.80)
    place(b, fm_note(C6, p['tau'] * 1.1, r, index=(2.0, 0.3)), at + 0.100, 1.0)
    return lowpass(b, 8000)


# ---------------------------------------------------------------- the Assembly Gantry

def mecha_boot(take, r):
    """A mecha powering up at the Assembly Gantry: a relay at 3 ms, the servo spinning up over 0.35 s (the motor model,
    whine 220 -> 1120 Hz, speed rising as u^1.5) and letting go in 30 ms, two relay clicks at its top (0.36 and
    0.41 s), then the jingle from 0.44 s as in house.jingle (the last C6 30 cents flat over the wooden bonk)."""
    dur = 2.25
    b = buf(dur)
    t = tt(dur)
    up = 0.35
    u = np.clip((t - 0.010) / up, 0, 1)
    s = np.where(t < 0.010 + up, u ** 1.5, np.exp(-(t - 0.010 - up) / 0.030)) * (t > 0.010)
    b += 0.45 * motor(t, s, r, 220, 900, grit=0.30, air=0.12, whine=0.40)
    place(b, relay_click(r, 1600), 0.003, 0.25)
    place(b, relay_click(r, 1800), 0.36, 0.40)
    place(b, relay_click(r, 2050), 0.41, 0.40)
    jb = buf(dur)
    jingle_body(jb, r, 0.44, [C5, E5, G5, C6], [0.72, 0.78, 0.84, 1.0], [0.09, 0.09, 0.09, 0.13],
                last_cents=-30.0, last_hold=0.12)
    b += 0.9 * lowpass(jb, 5000)
    return b


PATCH = [dict(m=1.00, d=0.80, teeth=3, sp=0.016, turns=(0.18, 0.32)), dict(m=1.10, d=0.65, teeth=4, sp=0.013, turns=(0.17, 0.29)),
         dict(m=0.90, d=1.00, teeth=3, sp=0.019, turns=(0.20, 0.36))]


def gantry_patch(take, r):
    """A steel plate patched onto a mecha: the plate set down at 3 ms (a strike, the kit's iron body at about 380 Hz
    low-passed at 4 kHz so it is dull, a thunk 140 -> 85 Hz), then two quick ratchet turns of a bolt (3-4 teeth 13-19 ms
    apart each, the teeth rising a little in pitch and level, the second turn tighter)."""
    p = PATCH[take - 1]
    m = p['m']
    b = buf(0.75)
    plate = mix(0.35 * strike(r, 1200), 0.75 * norm(iron_body(r, 380 * m, 620 * m, p['d'], noise_gain=0.25, band_scale=0.85)),
                0.45 * thunk(r, 140, 85, 0.045, 0.02))
    place(b, norm(lowpass(np.concatenate([plate, np.zeros(int(0.02 * SR))]), 4000)), 0.003, 1.0)
    for k, at in enumerate(p['turns']):
        for j in range(p['teeth']):
            place(b, tooth(r, 2500 * m * (1 + 0.03 * j + 0.05 * k)), at + j * p['sp'] * (1 - 0.1 * k),
                  (0.16 + 0.03 * j) * (1.0 + 0.15 * k))
    return b


# ---------------------------------------------------------------- the Gene Splicer and the gene report

SPLICE = [dict(m=1.00, thup=(220.0, 125.0), at=(0.060, 0.115), bub=(720.0, 940.0, 1150.0), seal=0.080),
          dict(m=1.12, thup=(250.0, 140.0), at=(0.072, 0.135), bub=(820.0, 1060.0, 1300.0), seal=0.100)]


def gene_splice(take, r):
    """The Gene Splicer finishing a serum, like a tape splice: a small blade 'snick' at 3 ms (a strike, a short
    blade mode about 4.2 kHz, a 3-9 kHz flick), the splice pressed home 60-72 ms later (a soft 'thup': a sine falling
    220 -> 125 Hz with a felt-padded noise puff), then a short wet seal (a squelch of low-passed noise swelling and
    falling over 80-100 ms, three small Minnaert bubbles 35-45 ms apart). No bell."""
    p = SPLICE[take - 1]
    m = p['m']
    b = buf(0.95)
    sn = mix(0.5 * strike(r, 3500), 0.6 * norm(modal(4200 * m, [1, 1.47], [1, 0.4], [0.006, 0.004], r, 0.02)),
             0.4 * norm(noise_burst(3000, 9000, 0.002, r)))
    place(b, norm(sn), 0.003, 0.55)
    a1, a2 = p['at']
    place(b, sweep_sine(*p['thup'], 0.025, 0.012, attack=0.002), a1, 0.85)
    place(b, norm(lowpass(noise_burst(200, 2000, 0.010, r, attack=0.002), 1200)), a1, 0.40)
    d = p['seal'] * 7
    t = tt(d)
    sq = lowpass(noise(d, r), 1500) * np.clip(t / 0.008, 0, 1) * np.exp(-t / p['seal'])
    sq *= 0.6 + 0.4 * np.sin(2 * np.pi * 38 * t) ** 2
    place(b, norm(sq), a2, 0.45)
    for k, (f, tau, at, g) in enumerate(zip(p['bub'], (0.015, 0.012, 0.010), (0.010, 0.050, 0.095), (0.40, 0.30, 0.22))):
        place(b, bubble(r, f, tau, rise=0.25), a2 + at * (1 + 0.15 * (take - 1)), g)
    return b


MUT = [dict(f=170.0, wob=5.0, depth=0.16, gur=(520.0, 1600.0), n=7, sp=0.032, wl=0.46),
       dict(f=150.0, wob=4.4, depth=0.20, gur=(460.0, 1450.0), n=8, sp=0.036, wl=0.50)]


def gene_mutation(take, r):
    """A mutation appearing in a body: an organic shudder at 3 ms (150-1500 Hz noise fluttering at 14 Hz, up in 10 ms,
    decaying 70 ms), a low wet wobble from 60 ms (a squelchy buzz on 150-170 Hz whose pitch wobbles 16-20 % at 4.4-5 Hz,
    heard through a wet band from 1.5 to 9 times the pitch that follows it, with low noise in it, 0.46-0.50 s), and a
    short rising bubbly gurgle at the end (7-8 Minnaert bubbles climbing 460-520 -> 1450-1600 Hz, 32-36 ms apart).
    No bell."""
    p = MUT[take - 1]
    dur = 1.25
    b = buf(dur)
    sd = 0.45
    t = tt(sd)
    fl = (0.5 + 0.5 * np.cos(2 * np.pi * 14 * t)) ** 2
    sh = bandpass(noise(sd, r), 150, 1500) * (0.4 + 0.6 * fl) * np.clip(t / 0.010, 0, 1) * np.exp(-t / 0.07)
    place(b, norm(sh), 0.003, 0.55)
    wl = p['wl']
    tw = tt(wl + 0.05)
    uw = np.clip(tw / wl, 0, 1)
    f0 = p['f'] * (1 + p['depth'] * np.sin(2 * np.pi * p['wob'] * tw)) * (1 - 0.10 * uw)
    src = osc_harm(f0, 24, r, 0.9) + 0.25 * norm(lowpass(noise(wl + 0.05, r), 1500))
    wet = sweep_bandpass(src, f0 * 1.5, f0 * 9.0)
    wenv = np.where(tw < wl, np.sin(np.pi * uw) ** 0.6, 0.0)
    place(b, norm(wet) * wenv, 0.060, 0.80)
    at = 0.060 + wl - 0.04
    for k in range(p['n']):
        fb = p['gur'][0] * (p['gur'][1] / p['gur'][0]) ** (k / (p['n'] - 1))
        place(b, bubble(r, fb * r.uniform(0.96, 1.04), r.uniform(0.010, 0.016), rise=0.2), at, 0.60 * r.uniform(0.7, 1.0))
        at += p['sp'] * r.uniform(0.8, 1.2)
    return b


# ---------------------------------------------------------------- the unpaid employee

GRUMBLE = [dict(f0=((176, 142), (168, 140), (180, 146)), L=(0.30, 0.25, 0.34), gaps=(0.10, 0.12),
                vow=(('m', 'o', 'm'), ('u', 'a', 'm'), ('o', 'e', 'u'))),
           dict(f0=((170, 140), (178, 148), (164, 140)), L=(0.28, 0.25, 0.30), gaps=(0.09, 0.10),
                vow=(('o', 'm', 'u'), ('m', 'a', 'o'), ('e', 'u', 'm'))),
           dict(f0=((180, 150), (166, 140), (174, 142)), L=(0.34, 0.28, 0.38), gaps=(0.11, 0.13),
                vow=(('a', 'm', 'o'), ('o', 'u', 'm'), ('m', 'o', 'u')))]


def staff_complain(take, r):
    """An unpaid employee (a villager head at a desk mount) muttering: three short grumbles, each two run-together
    syllables on a glottal buzz whose pitch falls from 164-180 Hz to 140-150 Hz, through two moving formant bands
    (the voice of recipes_machines3 with its third band dropped), breathy, small gaps between (80-130 ms). No words."""
    p = GRUMBLE[take - 1]
    total = sum(p['L']) + sum(p['gaps'])
    dur = round(0.003 + total + 0.15, 3)
    sylls = []
    at = 0.003
    for k in range(3):
        L = p['L'][k]
        fa, fb = p['f0'][k]
        va, vm, vb = p['vow'][k]
        fm = fa - 0.55 * (fa - fb)
        g = [1.0, 0.85, 0.95][k]
        sylls.append((at, 0.58 * L, fa, fm, va, vm, g))
        sylls.append((at + 0.50 * L, 0.50 * L, fm, fb, vm, vb, 0.75 * g))
        at += L + (p['gaps'][k] if k < 2 else 0)
    x = voice(r, dur, sylls, tilt=1.0, breath=0.22, jitter=0.02, gains=(1.0, 0.75))
    return lowpass(highpass(x, 110), 4000)


# ---------------------------------------------------------------- references (measured as the game plays them)

BEACON_POWER = dict(event='block.beacon.power_select', file='block__beacon__power1.ogg', vol=1.0, pitch=1.0)
BEACON_DEACTIVATE = dict(event='block.beacon.deactivate', file='block__beacon__deactivate.ogg', vol=1.0, pitch=1.0)
FLUTE = dict(event='block.note_block.flute', file='note__flute.ogg', vol=1.0, pitch=1.0)
IRON_DOOR_CLOSE = dict(event='block.iron_door.close', file='block__iron_door__close2.ogg', vol=0.9, pitch=0.95)
XYLO = dict(event='block.note_block.xylophone', file='note__xylobone.ogg', vol=1.0, pitch=1.0)
SHEAR = dict(event='entity.sheep.shear', file='mob__sheep__shear.ogg', vol=1.0, pitch=1.0)
VAULT_REJECT = dict(event='block.vault.reject_rewarded_player', file='block__vault__reject_rewarded_player.ogg', vol=1.0, pitch=1.0)
VAULT_ACTIVATE = dict(event='block.vault.activate', file='block__vault__activate.ogg', vol=1.0, pitch=1.0)
BOTTLE_FILL = dict(event='item.bottle.fill', file='item__bottle__fill1.ogg', vol=1.0, pitch=1.0)
SLIME_SMALL = dict(event='entity.slime.squish_small', file='mob__slime__small2.ogg', vol=1.0, pitch=1.0)
SLIME_BIG = dict(event='entity.slime.squish', file='mob__slime__big1.ogg', vol=1.0, pitch=1.0)
VILLAGER = dict(event='entity.villager.ambient', file='mob__villager__idle2.ogg', vol=1.0, pitch=1.0)
EQUIP_IRON = dict(event='item.armor.equip_iron', file='item__armor__equip_iron1.ogg', vol=1.0, pitch=1.0)
PISTON_OUT = dict(event='block.piston.extend', file='tile__piston__out.ogg', vol=0.5, pitch=0.725)

BASE = dict(volume=1.0, pitch=[1.0, 1.0], attenuation=16, fixed_range=False, muffled=False)


def ev(**kw):
    d = dict(BASE)
    d.update(kw)
    return d


SOUNDS = [
    ev(id='house.jingle', file='house/jingle', fn=jingle, takes=1, target=-18.0, cls='signature moment (the jingle)',
       kind='stinger', fade_ms=50, hit_ms=3, ref=FLUTE, ref2=BEACON_POWER, attenuation=48, fixed_range=True,
       category='BLOCKS for a machine, PLAYERS for the player\'s own moment',
       subtitle_en='Noggin & Sons jingle', subtitle_pt='Vinheta da Noggin & Sons',
       play='At the end of the operation, when a body is printed, and at a Board Meeting quorum. Register fixed-range 48. Volume 1.0, fixed pitch (musical; one take). Never twice within 10 s (200 ticks) for one player: drop the second, do not queue it.'),
    ev(id='house.jingle_shutdown', file='house/jingle_shutdown', fn=jingle_shutdown, takes=1, target=-22.0,
       cls='clamp class (the jingle signing off)', kind='stinger', fade_ms=50, hit_ms=3, ref=IRON_DOOR_CLOSE,
       ref2=BEACON_DEACTIVATE, category='NEUTRAL for a mecha, PLAYERS for a jar',
       subtitle_en='Jingle signs off', subtitle_pt='Vinheta se despede',
       play='When something of the company\'s powers down politely: a mecha shut down (at the mecha), a jar going flat (at the jar). Volume 1.0, fixed pitch, one take. Not more than once per 10 s per source.'),
    ev(id='house.jingle_botch', file='house/jingle_botch', fn=jingle_botch, takes=1, target=-18.0,
       cls='signature moment (the jingle gone wrong)', kind='stinger', fade_ms=50, hit_ms=3, ref=FLUTE, ref2=BEACON_POWER,
       attenuation=24, category='BLOCKS',
       subtitle_en='Jingle goes wrong', subtitle_pt='Vinheta desafina',
       play='The botched operation, in place of the old sad trombone: the event booth_oopsie lists these same files. At the chair, volume 1.0, fixed pitch, one take; normal (not fixed) range 24.'),
    ev(id='house.desk_ding', file='house/desk_ding', fn=desk_ding, takes=2, target=-28.0, cls='done chime (desk bell)',
       kind='stinger', fade_ms=50, hit_ms=10, ref=XYLO, attenuation=8,
       category='PLAYERS for the player\'s own action, BLOCKS at a machine',
       subtitle_en='Desk bell dings', subtitle_pt='Sineta de balcão toca',
       play='"All done here, next please": after the Surgical Saw\'s cut, an embalming and a suture, at the patient. Volume 1.0, fixed pitch; the plunger tick is at 3 ms and the note at 10 ms. Not DOLORES\'s chair.ding, which stays as it is. Not more than once per 20 ticks per source.'),
    ev(id='booth_cut', file='house/booth_cut', fn=booth_cut, takes=3, target=-24.0, cls='hiss class (a fine blade)',
       kind=('cut', 0.30), fade_ms=15, hit_ms=150, preroll_ticks=3, ref=SHEAR, volume=0.8, category='PLAYERS',
       subtitle_en='Surgical Saw cuts', subtitle_pt='Serra cirúrgica corta',
       play='Server, at the creature, when the Surgical Saw cuts a head free in the field: start it on the same tick as body.cork (3 ticks before the head comes off), volume 0.8, fixed pitch (so the snip stays on the tick; three takes give the variety). The two rasps run 3-130 ms and the snip lands at 150 ms, on the cork\'s pop. At volume 0.8 it plays about 1.9 LU under the file\'s -24.'),
    ev(id='link_lost', file='house/link_lost', fn=link_lost, takes=2, target=-22.0, cls='clamp class (switch-off)',
       kind='stinger', fade_ms=60, hit_ms=3, ref=VAULT_REJECT, ref2=BEACON_DEACTIVATE, category='PLAYERS',
       subtitle_en='Link lost', subtitle_pt='Conexão perdida',
       play='To the player whose link to the body breaks, at the player (or the jar). Volume 1.0, fixed pitch. The static rises from 3 ms; the tube switches off at 300 ms (6 ticks). Not more than once per 2 s per player.'),
    ev(id='link_restored', file='house/link_restored', fn=link_restored, takes=2, target=-24.0, cls='hiss class (tuning in, then the done chime)',
       kind='stinger', fade_ms=50, hit_ms=3, ref=VAULT_ACTIVATE, category='PLAYERS',
       subtitle_en='Link restored', subtitle_pt='Conexão restabelecida',
       play='To the player whose link to the body comes back, at the player (or the jar). Volume 1.0, fixed pitch (it ends on the done chime). The sweep starts at 3 ms; the chime\'s G5 lands about 0.36 s in and its C6 100 ms later.'),
    ev(id='mecha_boot', file='house/mecha_boot', fn=mecha_boot, takes=1, target=-18.0, cls='signature moment (a mecha powers up)',
       kind=('servo', 1.45), fade_ms=50, hit_ms=3, ref=FLUTE, ref2=BEACON_POWER, attenuation=48, fixed_range=True,
       category='BLOCKS', subtitle_en='Mecha powers up', subtitle_pt='Mecha liga',
       play='Server, at the Assembly Gantry (the mecha), on the tick the gantry finishes the mecha. Register fixed-range 48. Volume 1.0, fixed pitch, one take. The spin-up runs 10-360 ms, relays at 0.36 and 0.41 s, the jingle from 0.44 s. Counts as a jingle for the 10 s rule: no house.jingle for that player within 10 s of it.'),
    ev(id='gene_splice', file='house/gene_splice', fn=gene_splice, takes=2, target=-24.0, cls='hiss class (splice and seal)',
       kind=('splice', 0.40), fade_ms=20, hit_ms=3, ref=BOTTLE_FILL, ref2=SLIME_SMALL, pitch=[0.95, 1.05], category='BLOCKS',
       subtitle_en='Genes splice', subtitle_pt='Genes se unem',
       play='Server, at the Gene Splicer, when a serum is finished. Volume 1.0, random pitch 0.95-1.05. The snick is at 3 ms, the splice presses home 60-72 ms later and the seal follows.'),
    ev(id='gene_mutation', file='house/gene_mutation', fn=gene_mutation, takes=2, target=-24.0, cls='hiss class (organic)',
       kind=('mutation', 0.75), fade_ms=30, hit_ms=3, ref=BOTTLE_FILL, ref2=SLIME_BIG, pitch=[0.95, 1.05], category='PLAYERS',
       subtitle_en='Something mutates', subtitle_pt='Algo sofre mutação',
       play='When a gene report shows that a mutation has appeared in a body: at the player reading it (to that player). Volume 1.0, random pitch 0.95-1.05.'),
    ev(id='staff_complain', file='house/staff_complain', fn=staff_complain, takes=3, target=-27.0, cls='voice (muttering, synthesised)',
       kind=('voice', 1.00), fade_ms=40, hit_ms=3, ref=VILLAGER, pitch=[0.95, 1.05], category='VOICE',
       subtitle_en='Staff complains', subtitle_pt='Funcionário reclama',
       play='At the desk mount, when an unpaid employee complains. Volume 1.0, random pitch 0.95-1.05, a different take each time if the handler allows. No words: synthesised, nothing sampled. Not more than once per 5 s per mount.'),
    ev(id='gantry.patch', file='gantry/patch', fn=gantry_patch, takes=3, target=-27.0, cls='servo class (plate and ratchet)',
       kind=('patch', 0.36), fade_ms=20, hit_ms=3, ref=EQUIP_IRON, ref2=PISTON_OUT, pitch=[0.94, 1.06], category='NEUTRAL',
       subtitle_en='Plating patched', subtitle_pt='Blindagem remendada',
       play='At the mecha, when a steel plate is patched on at the Assembly Gantry or by hand (in place of the anvil sound). Volume 1.0, random pitch 0.94-1.06. The plate lands at 3 ms; the bolt\'s two ratchet turns follow at about 0.17 and 0.30 s.'),
]

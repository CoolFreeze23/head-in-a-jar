"""The machines, third batch: the Cerebral Array, the Employee Mount, the DNA Sequencer, the Injection Rig, the Tesla
Node and the Charging Dock.

The Cerebral Array is a jury box of nine specimen jars under a chairman's desk: at a vote the gavel bangs its sound
block, the heads murmur, nine enamel cards flip up one by one on the jars' lids (one file, played at rising pitch) and
drop back a row at a time; the broth urn on the desk steams faintly while the jury is fed.

The Employee Mount is an office cubicle round a head in a jar: an unpaid employee's complaint lifts the bakelite
handset, the line rings out once and picks up, Human Resources mumbles tinnily down it and the handset is slammed back;
a paid one stamps forms (the rubber stamp lifted and thumped down on the desk), is paid with a clink on the copper tray,
cheers when it turns round from the wall to be paid, sighs when it has been facing the wall for days, and the desk bell
tings on a refusal.

The DNA Sequencer is a brass cash register: its keys clack down at four pitches, the crank turns a call up through the
gears, the call flag (the tab) pops up through its slot and drops back, the receipt is torn off and the register bell
rings at the end of a read (the kit's drawer sounds do the drawer).

The Injection Rig is a 1950s petrol pump whose nozzle is a syringe: the syringe unlatches and lifts out of its brass
sheath, the arm's servo swings it to the player, it cocks back, the jab hisses (the server), and the result answers:
accepted (the plunger home and the pump's bell), rejected (a rubbery burp and the counter rolling), mutated (the needle
sproinged back on its spring); the servo swings it home and it drops into the holster.

The Tesla Node is a small Tesla coil with a floating steel ring and a brass ball pointer: it perks up (the ring lifts on
its field), the ball looks round the rim, the ring settles; linked, the ball swings onto its partner and the arc zaps;
the link breaks with a falling crackle; the coil hums while sending (a loop) and winds down when it stops.

The Charging Dock is a doctor's dial scale with a knife switch: the switch is thrown into its copper jaws with a clack,
falls open onto its stop, the scale's bell dings when the jar is full, the arc fizzles out on a brown-out, the meter
ticks while charging and small sparks snap at the jaws.

Times inside a file are seconds; one tick is 50 ms. Every keyframed file has its hit at 0-5 ms, and so does every file
the server or the code plays (no pre-roll in this batch). Takes differ by construction (a per-take table of
frequencies, decays and timings), not by luck of the seed. The two loops (the urn's steam, the coil's hum) are built
periodic and finished by the first batch's `loop_finish`.

The voices (the jury's murmur, Human Resources down the line, the employee's cheer and sigh) are synthesised: a
glottal buzz on a pitch curve and a little breath through three gliding formant bands; there are no words and no
recorded speech in them.
"""
import numpy as np
from nsynth import *
from recipes_machines import small_tick, enamel_contact, cnoise, cband, loop_finish
from recipes_machines2 import env_seg, release, smooth, knock, brass_ring, bell_modes

T = TICK


# ---------------------------------------------------------------- shared pieces

def bakelite(r, f, d=1.0):
    """Bakelite meeting bakelite: a strike, a hard plastic body (ratios 1, 1.83, 2.9, 4.4; short), a short bright noise
    band and a faint thunk. d scales the decays."""
    return norm(mix(0.45 * strike(r, 2000),
                    0.60 * norm(modal(f, [1, 1.83, 2.9, 4.4], [1, 0.5, 0.3, 0.15],
                                      [0.009 * d, 0.006 * d, 0.004 * d, 0.003 * d], r, 0.02)),
                    0.30 * norm(noise_burst(1200, 6000, 0.005, r)),
                    0.15 * thunk(r, 300, 220, 0.010, 0.005)))


def pad(x, s=0.04):
    """x with s seconds of silence after it, so a filter's ring has room to die inside the array."""
    return np.concatenate([x, np.zeros(int(round(s * SR)))])


def phone(x, drive=2.5):
    """Through a 1950s handset's earpiece: the telephone band, the small diaphragm driven a little hard (tanh) and its
    resonance about 1.1-1.9 kHz. Padded so the filters ring out inside the array."""
    y = bandpass(pad(x), 350, 3200)
    y = np.tanh(drive * norm(y)) / np.tanh(drive)
    return norm(0.7 * y + 0.5 * norm(bandpass(y, 1100, 1900)))


def thum(r, f, tau, nharm=40, tilt=0.6):
    """A coil's field catching or letting go: a short buzz on f (harmonics 1/k^tilt, 150-6000 Hz), decaying with tau."""
    d = 7 * tau + 0.002
    x = osc_harm(np.full(int(round(d * SR)), f), nharm, r, tilt) * exp_env(d, tau, 0.002)
    return norm(bandpass(pad(x), 150, 6000))


VOWEL = {'a': (730, 1150, 2450), 'o': (520, 880, 2400), 'e': (480, 1750, 2500), 'u': (370, 800, 2300),
         'i': (330, 2050, 2750), 'm': (260, 1000, 2300)}  # formants (Hz), chosen; 'm' is a closed-mouth hum


def voice(r, dur, sylls, tilt=1.2, breath=0.10, jitter=0.012, shape='swell', bw=(0.80, 1.22), gains=(1.0, 0.55, 0.22)):
    """A synthesised voice without words: a glottal buzz (40 harmonics on an f0 curve, 1/k^tilt) and a little breath
    (500-5000 Hz noise), through three formant bands (gains 1, 0.55, 0.22 by default) that glide from vowel to vowel.
    sylls: (start s, length s, f0 start, f0 end, vowel start, vowel end, level). Between syllables the pitch and the
    formants glide; the level is the sum of the syllables' envelopes (smoothed over 4 ms): 'swell' sin(pi u)^0.7 (a
    syllable starting on the hit, before 10 ms, comes up in 8 ms and falls as the swell's second half), or 'sigh' (up
    in 12 ms, then falling as (1 - u)^1.6). The pitch wanders by +-jitter (a 25 ms smoothed random walk)."""
    t = tt(dur)
    n = len(t)
    env = np.zeros(n)
    kt, kf, kF = [], [], []
    for (a, L, fa, fb, va, vb, g) in sylls:
        u = np.clip((t - a) / L, 0, 1)
        inside = (t > a) & (t < a + L)
        if shape == 'sigh':
            e = np.clip((t - a) / 0.012, 0, 1) * (1 - u) ** 1.6
        elif a < 0.01:  # a syllable on the hit: up in 8 ms, then falling as the second half of the swell
            e = np.clip((t - a) / 0.008, 0, 1) * np.sin(np.pi * (0.5 + 0.5 * u)) ** 0.7
        else:
            e = np.sin(np.pi * u) ** 0.7
        env += g * np.where(inside, e, 0.0)
        kt += [a, a + L]
        kf += [fa, fb]
        kF += [VOWEL[str(va)], VOWEL[str(vb)]]
    env = smooth(env, 4)
    order = np.argsort(kt, kind='stable')
    kt = np.array(kt)[order]
    kf = np.array(kf)[order]
    kF = np.array(kF, dtype=float)[order]
    wob = smooth(r.standard_normal(n), 25)
    wob = wob / max(np.abs(wob).max(), 1e-9)
    f0 = np.interp(t, kt, kf) * (1 + jitter * wob)
    src = norm(osc_harm(f0, 40, r, tilt)) + breath * norm(bandpass(noise(dur, r), 500, 5000))
    x = src * env
    out = np.zeros(n)
    for k, gk in enumerate(gains):
        Fk = np.interp(t, kt, kF[:, k])
        out += gk * norm(sweep_bandpass(x, Fk * bw[0], Fk * bw[1]))
    return out


def rub(r, dur, s, lo=2000.0, hi=8000.0, whir=(700.0, 0.10)):
    """A brass shoe sliding on a steel rim at speed s (0..1, per sample): a brushed rub whose band brightens with the
    speed and a faint bearing whir whose pitch follows it."""
    x = sweep_bandpass(noise(dur, r), lo * (0.7 + 0.3 * s), hi * (0.6 + 0.4 * s)) * s ** 1.3
    w = np.sin(2 * np.pi * np.cumsum(whir[0] * (0.4 + 0.6 * s)) / SR) * s
    return norm(x) + whir[1] * norm(w)


# ======================================================================= the Cerebral Array

def array_card(take, r):
    """One jury card flipped up (vote keys 14, 17, ... 38, a card every 3 ticks at pitch 0.925 to 1.125): the enamelled
    4x1x3 card's hinge lets go at 3 ms (a small brass tick about 2.4 kHz), the card whips up (easeOutBack: fastest at
    once) as a quick stiff flap of air (500-2500 Hz, up in 12 ms and gone by about 90 ms) and its thin tin body flexes
    (plate ratios about 1.05 kHz, 3-10 ms). Gone in about 0.1 s, so nine cards 150 ms apart stay nine."""
    dur = 0.25
    b = buf(dur)
    t = tt(dur)
    place(b, small_tick(r, 2400, 0.003), 0.003, 0.25)
    s = env_seg(t, 0.004, 0.09, 'out') * np.clip((t - 0.004) / 0.012, 0, 1)
    b += 0.45 * norm(bandpass(noise(dur, r), 500, 2500) * s)
    place(b, norm(modal(1050, [1, 1.59, 2.14, 3.9], [1, 0.45, 0.25, 0.1], [0.010, 0.007, 0.004, 0.003], r, 0.02)), 0.005, 0.60)
    return b


CARDS_DOWN = [dict(m=1.00, d=1.00, gaps=(0.000, 0.009, 0.017)), dict(m=1.12, d=0.80, gaps=(0.000, 0.006, 0.021)),
              dict(m=0.88, d=1.30, gaps=(0.000, 0.012, 0.034))]


def array_cards_down(take, r):
    """A row of three cards dropping back onto their jars' brass lids (vote keys 53 and 55, pitch 1.05 and 0.95;
    easeInQuad into the contact): three light taps 6-25 ms apart (a strike, the card's tin plate about 1.15 kHz, the
    brass lid's small ring about 2.4 kHz, a faint thunk), and the 6-degree bounce settling a tick later (three soft
    ticks about 53 ms on)."""
    p = CARDS_DOWN[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.35)
    for k, g in enumerate(p['gaps']):
        tap = mix(0.35 * strike(r, 2500 * m),
                  0.60 * norm(modal(1150 * m * (1 + 0.05 * (k - 1)), [1, 1.59, 2.14, 3.9], [1, 0.5, 0.3, 0.15],
                                    [0.012 * d, 0.008 * d, 0.005 * d, 0.003 * d], r, 0.02)),
                  0.30 * norm(modal(2400 * m * (1 + 0.03 * k), [1, 2.756], [1, 0.3], [0.010 * d, 0.006 * d], r)),
                  0.20 * thunk(r, 260, 190, 0.010, 0.005))
        place(b, norm(tap), 0.003 + g, [1.0, 0.8, 0.9][k])
    for k, g in enumerate(p['gaps']):
        place(b, small_tick(r, 1300 * m * (1 + 0.04 * k), 0.005), 0.053 + g, 0.20)
    return b


GAVEL = [dict(m=1.00, d=1.00, blk=380.0, bounce=0.050), dict(m=1.14, d=0.80, blk=420.0, bounce=0.046),
         dict(m=0.88, d=1.25, blk=350.0, bounce=0.056)]


def array_gavel(take, r):
    """The chairman's hardwood gavel banging its sound block (the server at the vote tick T; vote_anyway keys 45 and 50;
    the refusal's taps at 4 and 8): a hard strike at 3 ms, the gavel head's dry wood modes (about 950 Hz, ratios 1,
    2.57, 4.3, 6.1, 5-20 ms), the block's darker body (350-420 Hz, 40 ms), the weight (a thunk 170 -> 105 Hz, kept
    under the wood) and a short splinter of noise; the 6-degree bounce comes down again a tick on (46-56 ms), a light
    second tock."""
    p = GAVEL[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.60)
    head = norm(modal(950 * m, [1, 2.57, 4.3, 6.1], [1, 0.6, 0.35, 0.2], [0.020 * d, 0.012 * d, 0.007 * d, 0.005 * d], r, 0.02))
    block = norm(modal(p['blk'], [1, 2.3, 3.9], [1, 0.5, 0.25], [0.040 * d, 0.024 * d, 0.012 * d], r, 0.02))
    hit = mix(0.55 * strike(r, 1200 * m, 0.0012), 0.85 * head, 0.40 * block, 0.25 * thunk(r, 170, 105, 0.030, 0.012),
              0.35 * norm(noise_burst(700 * m, 5000 * m, 0.008, r)))
    place(b, norm(hit), 0.003, 1.0)
    tock = mix(0.40 * strike(r, 1500), 0.60 * norm(modal(850 * m, [1, 2.57, 4.3], [1, 0.5, 0.25], [0.010, 0.006, 0.004], r, 0.02)),
               0.40 * norm(modal(p['blk'] * 1.02, [1, 2.3], [1, 0.4], [0.015, 0.010], r)))
    place(b, norm(tock), 0.003 + p['bounce'], 0.28)
    return b


MURMUR = [dict(L=1.05, f0s=(98.0, 122.0, 141.0, 112.0), lp=3200.0), dict(L=1.25, f0s=(105.0, 92.0, 133.0, 156.0), lp=3600.0)]


def array_murmur(take, r):
    """The jury murmuring at the vote (the server at T, with the gavel): four low synthesised voices (f0 92-156 Hz, no
    words) all catching their breath on the bang at 3 ms (a 400-3500 Hz intake of breath, 30 ms, and an 'oh' coming up
    in 8 ms) and running on in short uneven syllables of 0.12-0.26 s on the vowels a, o, e, i and a hum (the upper
    formants as strong as the first: tinny heads), each voice's pitch drifting down a little and its level sinking over
    the 1.05-1.25 s; high-passed at 250 Hz and low-passed at 3.2-3.6 kHz (heads behind glass)."""
    p = MURMUR[take - 1]
    L = p['L']
    dur = round(L + 0.35, 3)
    b = buf(dur)
    vowels = ['a', 'o', 'e', 'i', 'm']
    for vi, f0 in enumerate(p['f0s']):
        at = 0.003
        sylls = []
        first = True
        while at < L - 0.12:
            Ls = min(r.uniform(0.12, 0.26), L - at)
            g = (1.0 if first else r.uniform(0.45, 0.9)) * (1 - 0.35 * at / L)
            sylls.append((at, Ls, f0 * r.uniform(0.98, 1.12), f0 * r.uniform(0.85, 1.0),
                          vowels[r.integers(5)], vowels[r.integers(5)], g))
            at += Ls + r.uniform(0.02, 0.09)
            first = False
        b += [1.0, 0.8, 0.7, 0.6][vi] * norm(voice(r, dur, sylls, tilt=1.0, breath=0.15, gains=(0.8, 0.8, 0.4)))
    place(b, 0.9 * norm(noise_burst(400, 3500, 0.030, r, attack=0.001)), 0.003, np.abs(b).max())
    return lowpass(highpass(b, 250), p['lp'])


STEAM_L = 2.0  # s


def array_steam(take, r):
    """The broth urn's faint steam, a seamless 2.0 s loop played while the jury is fed: a soft hiss (2.5-9 kHz noise)
    breathing slowly (one and three swells a loop) over a fainter low simmer in the urn (180-700 Hz, two swells).
    Every periodic layer completes whole cycles in the 2.0 s (1, 3, 2)."""
    n = int(round(STEAM_L * SR))
    t = np.arange(n) / SR
    hiss = cband(cnoise(n, r), 2500, 9000)
    puff = 0.75 + 0.15 * np.sin(2 * np.pi * t / STEAM_L + 0.4) + 0.10 * np.sin(2 * np.pi * 3 * t / STEAM_L + 1.9)
    sim = cband(cnoise(n, r), 180, 700) * (0.6 + 0.4 * np.sin(2 * np.pi * t / STEAM_L + 0.3) ** 2)
    return puff * (0.80 * norm(hiss) + 0.20 * norm(sim))


# ======================================================================= the Employee Mount

LIFT = [dict(m=1.00, off=0.055, ting=1900.0), dict(m=1.09, off=0.066, ting=2050.0)]


def mount_receiver_lift(take, r):
    """The handset lifted off its cradle (complaint key 2, at the bottom of the quarter-pixel press): the bakelite pressed
    a hair at 3 ms (a small tick), then the handset leaves the cradle at 55-66 ms (tick 3; a bakelite unseat knock about
    1.1 kHz over the case's body about 650 Hz), the hookswitch springs up under it (a relay-like click with its release
    18 ms later) and the phone's ringer gongs tinkle once, faintly (a small bell about 1.9-2.05 kHz). Low-passed at
    6 kHz."""
    p = LIFT[take - 1]
    m = p['m']
    b = buf(0.55)
    place(b, bakelite(r, 1300 * m, 0.7), 0.003, 0.45)
    place(b, bakelite(r, 1100 * m, 1.0), p['off'], 0.80)
    place(b, relay_click(r, 1800 * m, release_after=0.018), p['off'] + 0.004, 0.45)
    place(b, norm(modal(650 * m, [1, 2.3, 3.6], [1, 0.4, 0.2], [0.018, 0.011, 0.006], r, 0.02)), p['off'], 0.30)
    place(b, norm(bell_modes(r, p['ting'], (0.060, 0.030, 0.020, 0.012, 0.008))), p['off'] + 0.006, 0.08)
    return lowpass(b, 6000)


RINGBACK = [dict(f=(400.0, 450.0), L=0.170, pick=0.192), dict(f=(425.0, 475.0), L=0.205, pick=0.228)]


def mount_hr_ringback(take, r):
    """The line ringing out in the handset at the ear (complaint key 12) and picked up at the far end: one ringback burr
    (two tones a 50 Hz beat apart, 400 + 450 or 425 + 475 Hz, so it purrs) through the earpiece (telephone band, a
    little driven, its resonance about 1.5 kHz), up in 6 ms, cut short after 170-205 ms in 25 ms as Human Resources
    lifts the receiver (a small dull clunk in the line at 192-228 ms) and the open line breathes for a moment. Quiet:
    it comes from a handset."""
    p = RINGBACK[take - 1]
    dur = 0.55
    b = buf(dur)
    t = tt(dur)
    s = np.sin(2 * np.pi * p['f'][0] * t) + np.sin(2 * np.pi * p['f'][1] * t + r.uniform(0, 2 * np.pi))
    e = (t > 0.003) * np.clip((t - 0.003) / 0.006, 0, 1) * np.where(
        t < p['L'], 1.0, (0.5 + 0.5 * np.cos(np.pi * np.clip((t - p['L']) / 0.025, 0, 1))) ** 2)
    burr = phone(s * e, 2.0)
    b += 0.85 * burr[:len(b)]
    click = mix(norm(noise_burst(300, 3000, 0.003, r)), 0.5 * thunk(r, 260, 180, 0.010, 0.005))
    place(b, phone(click, 2.0), p['pick'], 0.50)
    place(b, phone(noise(0.30, r) * exp_env(0.30, 0.040, 0.004), 1.5), p['pick'], 0.12)
    return b


MUMBLE = [dict(f0=205.0, L=1.05, stress=(0.003, 0.150, 0.300, 0.450, 0.600)),
          dict(f0=228.0, L=1.22, stress=(0.003, 0.150, 0.300, 0.450, 0.600, 0.900))]


def mount_hr_mumble(take, r):
    """Human Resources down the line (complaint key 16, the handset at the employee's ear): one tinny synthesised voice
    with no words (f0 205-228 Hz), loud stressed syllables at 0, 150, 300, 450 and 600 ms, under the handset's jabbers
    at ticks 16, 19, 22, 25 and 28 (each rising a third and falling), quicker unstressed ones between them, then a
    falling tail to 1.05-1.2 s, over the open line's faint hiss; all through the earpiece (telephone band, driven harder
    than the ringback)."""
    p = MUMBLE[take - 1]
    f0, L = p['f0'], p['L']
    dur = round(L + 0.30, 3)
    vowels = ['a', 'o', 'e', 'u', 'i']
    sylls = []
    for a in p['stress']:
        Ls = r.uniform(0.09, 0.12)
        sylls.append((a, Ls, f0 * 1.30 * r.uniform(0.95, 1.05), f0 * 1.05, vowels[r.integers(5)], vowels[r.integers(5)], 1.0))
        L2 = 0.15 - Ls - 0.012
        if L2 > 0.025:
            v = vowels[r.integers(5)]
            sylls.append((a + Ls + 0.004, L2, f0 * 1.02, f0 * 0.95, v, v, 0.55))
    at = p['stress'][-1] + 0.16
    k = 0
    while at < L - 0.08:
        Ls = min(r.uniform(0.07, 0.13), L - at)
        sylls.append((at, Ls, f0 * (1.05 - 0.04 * k), f0 * (0.92 - 0.04 * k), vowels[r.integers(5)], vowels[r.integers(5)],
                      0.75 * 0.85 ** k))
        at += Ls + r.uniform(0.01, 0.04)
        k += 1
    v = voice(r, dur, sylls, tilt=1.0, breath=0.15)
    t = tt(dur)
    line = bandpass(noise(dur, r), 400, 3000) * np.clip(t / 0.01, 0, 1) * np.where(t < L, 1.0, np.exp(-(t - L) / 0.03))
    return phone(norm(v) + 0.025 * norm(line), 3.0)


RCLACK = [dict(m=1.00, d=1.00, gong=2350.0, bounce=0.050), dict(m=1.08, d=0.85, gong=2500.0, bounce=0.046),
          dict(m=0.92, d=1.15, gong=2200.0, bounce=0.055)]


def mount_receiver_clack(take, r):
    """The handset slammed back on the cradle (complaint key 64, a 3-tick easeInQuad slam): a strike, the bakelite's
    hard body (about 1.1 kHz, long for bakelite) and the phone's darker case (about 560 Hz) with a thunk, the hookswitch
    banged down (a relay-like click and its release), the ringer's gongs jangling once in the case (a small bell about
    2.2-2.5 kHz, short) and the 0.5 px bounce knocking again a tick on (46-55 ms)."""
    p = RCLACK[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.60)
    hit = mix(0.45 * strike(r, 1500), 0.65 * bakelite(r, 1100 * m, 1.4 * d),
              0.40 * norm(modal(560 * m, [1, 2.3, 3.6], [1, 0.45, 0.2], [0.020 * d, 0.012 * d, 0.007 * d], r, 0.02)),
              0.40 * thunk(r, 200, 130, 0.025, 0.010))
    place(b, norm(hit), 0.003, 1.0)
    place(b, relay_click(r, 2200 * m, release_after=0.020), 0.006, 0.35)
    place(b, norm(bell_modes(r, p['gong'], (0.045, 0.025, 0.018, 0.010, 0.006))), 0.008, 0.16)
    place(b, bakelite(r, 1200 * m, 0.8), 0.003 + p['bounce'], 0.25)
    return b


SLIFT = [dict(m=1.00, f=(320.0, 200.0), band=(300.0, 1400.0), neck=0.060),
         dict(m=1.18, f=(370.0, 230.0), band=(360.0, 1700.0), neck=0.052),
         dict(m=0.88, f=(290.0, 180.0), band=(260.0, 1250.0), neck=0.080)]


def mount_stamp_lift(take, r):
    """The rubber stamp lifted off the form (stamp key 2 and every 10 ticks in stamp_3; it rises 6 px by tick 5): the
    inked rubber die unsticks at 3 ms (a tacky low pop, 320 -> 200 Hz, and a soft 300-1400 Hz puff), the wooden knob
    knocks faintly in the hand (about 700 Hz) and the copper neck ticks as the lean starts, 52-80 ms on. Very soft: every
    paid employee does it every 5-10 s."""
    p = SLIFT[take - 1]
    m = p['m']
    b = buf(0.25)
    place(b, sweep_sine(p['f'][0], p['f'][1], 0.012, 0.004), 0.003, 0.50)
    place(b, norm(noise_burst(p['band'][0], p['band'][1], 0.010, r, attack=0.001)), 0.003, 0.45)
    place(b, norm(modal(700 * m, [1, 2.7, 4.9], [1, 0.4, 0.2], [0.008, 0.005, 0.003], r, 0.02)), 0.006, 0.25)
    place(b, small_tick(r, 1900 * m, 0.004), p['neck'], 0.10)
    return b


THUMP = [dict(m=1.00, desk=430.0, slap=0.30, bounce=0.050), dict(m=1.08, desk=480.0, slap=0.22, bounce=0.046),
         dict(m=0.92, desk=390.0, slap=0.38, bounce=0.054)]


def mount_stamp_thump(take, r):
    """The rubber stamp struck down on a form on the desk (stamp key 11; stamp_3 keys 11, 21, 31 at pitch 1.0, 1.05,
    1.1): a soft dull thump at 3 ms (a thunk 180 -> 115 Hz under the rubber), the desk's wooden body (390-480 Hz,
    ratios 1, 2.57, 4.3, 6-18 ms), the paper's slap (500-3000 Hz, 6 ms) and the 0.5 px bounce touching down a tick on;
    low-passed at 4 kHz. Soft: it repeats all day."""
    p = THUMP[take - 1]
    m = p['m']
    b = buf(0.40)
    hit = mix(0.25 * strike(r, 1200), 0.28 * thunk(r, 180 * m, 115 * m, 0.025, 0.010),
              0.60 * norm(modal(p['desk'], [1, 2.57, 4.3], [1, 0.5, 0.25], [0.018, 0.011, 0.006], r, 0.02)),
              (p['slap'] + 0.10) * norm(noise_burst(500, 3000, 0.006, r, attack=0.0008)))
    place(b, norm(lowpass(pad(hit), 4000)), 0.003, 1.0)
    place(b, norm(mix(0.5 * thunk(r, 190 * m, 120 * m, 0.012, 0.006),
                      0.6 * norm(modal(p['desk'] * 1.03, [1, 2.57], [1, 0.4], [0.010, 0.006], r)))), 0.003 + p['bounce'], 0.20)
    return b


CLINK = [dict(m=1.00, d=1.00, hops=(0.000, 0.062, 0.101, 0.127)), dict(m=1.07, d=0.85, hops=(0.000, 0.055, 0.088, 0.110)),
         dict(m=0.93, d=1.15, hops=(0.000, 0.070, 0.115, 0.146))]


def mount_clink(take, r):
    """The pay dropping into the copper tray (payday key 0, pitch 1.2): the emerald strikes the tray at 3 ms (a strike,
    the copper plate's modes about 1.85 kHz, ratios 1, 1.59, 2.14, 2.83, 3.9, 20-50 ms, and the gem's tiny glassy tick
    about 4.2 kHz) and hops three times, each lower and sooner (55-146 ms), like a coin settling. High-passed at 400 Hz."""
    p = CLINK[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.60)
    for k, h in enumerate(p['hops']):
        tray = norm(modal(1850 * m, [1, 1.59, 2.14, 2.83, 3.9], [1, 0.7, 0.5, 0.3, 0.2],
                          [0.050 * d, 0.035 * d, 0.025 * d, 0.015 * d, 0.010 * d], r, 0.01))
        gem = norm(glass_modes(r, 4200 * m * (1 + 0.03 * k), decays=(0.010, 0.007, 0.005, 0.003)))
        place(b, norm(mix(0.35 * strike(r, 3000), 0.60 * tray, 0.20 * gem)), 0.003 + h, [1.0, 0.45, 0.28, 0.16][k])
    return highpass(b, 400)


CHEER = [dict(f0=300.0, up=1.60, L=(0.090, 0.160), gap=0.030), dict(f0=335.0, up=1.50, L=(0.080, 0.200), gap=0.025)]


def mount_cheer(take, r):
    """The employee's small happy chirp as it turns round to be paid (payday_back key 40): a tinny synthesised voice
    with no words, two quick syllables, an 'e' coming up on the hit and then a brighter 'i' rising a sixth or more
    (300-335 Hz up to about 480-500 Hz), through the jar's glass (500-4500 Hz with a little glass ring about 2.2 kHz)."""
    p = CHEER[take - 1]
    f0 = p['f0']
    dur = 0.60
    a1, L1 = 0.003, p['L'][0]
    a2, L2 = a1 + L1 + p['gap'], p['L'][1]
    sylls = [(a1, L1, f0, f0 * 1.15, 'e', 'e', 0.8), (a2, L2, f0 * 1.25, f0 * p['up'], 'e', 'i', 1.0)]
    v = voice(r, dur, sylls, tilt=0.9, breath=0.10, jitter=0.008)
    y = bandpass(pad(v), 500, 4500)
    return norm(y) + 0.30 * norm(bandpass(y, 2000, 2400))


def mount_bell_ting(take, r):
    """The desk bell's ting on a refusal (refuse key 1): a small reception bell struck once at E7 (2637 Hz, the
    motif's E two octaves up from the refusal's E5), its upper partials kept low, a light strike and the plunger's
    tiny second touch 22 ms later; 0.7 s heard. A layer under the server's feedback.refused (played at volume 0.5)."""
    b = buf(1.30)
    place(b, strike(r, 4000), 0.003, 0.20)
    place(b, norm(bell_modes(r, 2637.0, (0.16, 0.06, 0.035, 0.02, 0.01), amps=(1.0, 0.25, 0.20, 0.08, 0.04))), 0.003, 1.0)
    place(b, norm(bell_modes(r, 2637.0, (0.04, 0.02, 0.015, 0.01, 0.006), amps=(1.0, 0.25, 0.20, 0.08, 0.04))), 0.025, 0.10)
    return b


SIGH = [dict(f0=(175.0, 105.0), L=0.80, v=('a', 'u'), lp=4000.0), dict(f0=(160.0, 95.0), L=0.92, v=('e', 'a'), lp=4500.0)]


def mount_sigh(take, r):
    """The employee's short tinny sigh (code, every 400 ticks while it has been facing the wall for three days or more):
    a synthesised breath out, mostly air, voiced underneath on a falling pitch (175 -> 105 Hz or 160 -> 95 Hz) and
    gliding from one vowel to a darker one, up in 12 ms and dying away as (1 - u)^1.6 over 0.8-0.9 s, through the jar's
    glass (350 Hz to 4-4.5 kHz)."""
    p = SIGH[take - 1]
    L = p['L']
    dur = round(L + 0.35, 3)
    sylls = [(0.003, L, p['f0'][0], p['f0'][1], p['v'][0], p['v'][1], 1.0)]
    v = voice(r, dur, sylls, tilt=1.4, breath=0.9, jitter=0.015, shape='sigh')
    return lowpass(highpass(pad(v), 350), p['lp'])


# ======================================================================= the DNA Sequencer

def sequencer_key(take, r):
    """A cash-register key struck home (press_a/c/g/t key 2, the key reaching the bottom of its 1 px well; pitch 0.8,
    0.9, 1.0, 1.1 for A, C, G, T): the ivory cap meets the well at 3 ms (a strike and a hard bakelite-like click about
    1.7 kHz), the key's steel lever drops on its stop under the panel (a damped steel body about 900 Hz, free-bar
    ratios, 6-18 ms, and a thunk), the return spring pings faintly (about 3.1 kHz) and the escapement clicks 18 ms on."""
    b = buf(0.30)
    hit = mix(0.45 * strike(r, 2500), 0.45 * bakelite(r, 1700, 0.8),
              0.55 * norm(modal(900, [1, 2.756, 5.404], [1, 0.5, 0.25], [0.018, 0.011, 0.006], r, 0.01)),
              0.30 * thunk(r, 220, 150, 0.018, 0.008))
    place(b, norm(hit), 0.003, 1.0)
    place(b, norm(modal(3100, [1, 1.6], [1, 0.3], [0.020, 0.010], r)), 0.004, 0.08)
    place(b, tooth(r, 2700), 0.021, 0.30)
    return b


CRANK = [dict(m=1.00, teeth=24, clunk=620.0, dec=0.45), dict(m=1.08, teeth=20, clunk=690.0, dec=0.40),
         dict(m=0.85, teeth=28, clunk=520.0, dec=0.65)]


def sequencer_crank(take, r):
    """The crank turned once forward (ring key 4: the turn runs ticks 3-8, easeInOutSine, so the file opens a fifth of
    the way in, at speed): the gear train's teeth click past (20-28 a turn, at the times the easeInOutSine angle crosses
    each tooth, so they bunch in the middle and spread at the ends), a little gear grit with the speed, then at 200 ms
    (tick 8, the 370-degree overshoot) the register's main lever trips with a solid clunk (iron body about 560-690 Hz),
    and the pawl clicks back as it settles to 360 at 250 ms (tick 9)."""
    p = CRANK[take - 1]
    m = p['m']
    dur = 0.65
    b = buf(dur)
    t = tt(dur)
    u = np.clip((t + 0.05) / 0.25, 0, 1)
    s = np.where(t < 0.20, np.sin(np.pi * u), 0.0) * np.clip(t / 0.004, 0, 1)
    s = smooth(s, 5)
    b += 0.22 * norm(bandpass(noise(dur, r), 1500 * m, 6000 * m) * s)
    pos0 = (1 - np.cos(0.2 * np.pi)) / 2
    place(b, tooth(r, 2300 * m), 0.003, 0.55)
    N = p['teeth']
    for k in range(int(np.ceil(pos0 * N)), N):
        uk = np.arccos(1 - 2 * k / N) / np.pi
        tk = uk * 0.25 - 0.05
        if 0.008 < tk < 0.195:
            sp = np.sin(np.pi * uk)
            place(b, tooth(r, 2300 * m * r.uniform(0.97, 1.03)), tk, 0.25 + 0.35 * sp)
    clunk = mix(0.40 * strike(r, 1500), norm(iron_body(r, p['clunk'] * m, 1000 * m, p['dec'], noise_gain=0.25, band_scale=m)),
                0.35 * thunk(r, 170, 110, 0.025, 0.010))
    place(b, norm(clunk), 0.200, 0.85)
    place(b, mix(tooth(r, 2900 * m), 0.4 * small_tick(r, 2100 * m, 0.004)), 0.250, 0.30)
    return b


TAB = [dict(m=1.00, d=1.00, wob=0.034), dict(m=1.10, d=0.80, wob=0.027)]


def sequencer_tab(take, r):
    """The call flag popping up through its slot onto its top stop (ring key 7; tally at every slot's +5, pitch 1.0
    rising 0.02 a slot; load key 1 at 0.9): the enamelled plate meets the slot's top at 3 ms (a light enamel contact
    about 1.5 kHz, a strike and a bright noise band), its spring twangs briefly (about 900 Hz) and the half-pixel
    overshoot wobbles back 27-34 ms later (a small tick). Gone inside 0.12 s."""
    p = TAB[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.30)
    place(b, enamel_contact(r, 1500 * m, 0.6 * d, hp=2000, body=0.5, th=(300, 220), th_gain=0.15, nz=0.30,
                            noise_band=(1500, 6000)), 0.003, 1.0)
    place(b, norm(modal(900 * m, [1, 2.76], [1, 0.3], [0.022 * d, 0.012 * d], r)), 0.004, 0.18)
    place(b, small_tick(r, 2300 * m, 0.004), 0.003 + p['wob'], 0.20)
    return b


TAB_DOWN = [dict(m=1.00, d=1.00, bounce=0.050), dict(m=1.09, d=0.80, bounce=0.046)]


def sequencer_tab_down(take, r):
    """The call flag dropping into the body (tally key 93 or 107; unload key 4; easeInQuad): the plate hits the bottom
    of its well inside the brass body at 3 ms (an enamel contact about 1.1 kHz, muffled by the body: low-passed at
    3.5 kHz), the body round it answers (about 520 Hz) and the bounce touches down a tick on, softer."""
    p = TAB_DOWN[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.40)
    place(b, lowpass(pad(enamel_contact(r, 1100 * m, 0.8 * d, body=0.6, th=(240, 160), th_gain=0.35, nz=0.2)), 3500), 0.003, 1.0)
    place(b, norm(modal(520 * m, [1, 2.3, 3.6], [1, 0.4, 0.2], [0.025 * d, 0.015 * d, 0.008 * d], r)), 0.004, 0.35)
    place(b, lowpass(pad(enamel_contact(r, 1150 * m, 0.5, body=0.5, th_gain=0.2, nz=0.15)), 3500), 0.003 + p['bounce'], 0.25)
    return b


TEAR = [dict(L=0.060, lo=1200.0, hi=7000.0, flut=0.12, fr=26.0), dict(L=0.085, lo=1000.0, hi=6000.0, flut=0.16, fr=21.0)]


def sequencer_tear(take, r):
    """The receipt torn off against the serrated edge (tear key 4, the end of the 3-tick easeInExpo flick): the paper's
    fibres part in a dense run of tiny crackles (1-7 kHz bursts 1.2-4 ms apart, 60-85 ms, fading a little) over the
    paper's own rasp (600-3000 Hz), then the free strip whips and flutters for 0.12-0.16 s (an 800-4000 Hz swish
    fluttering at 21-26 Hz)."""
    p = TEAR[take - 1]
    L = p['L']
    dur = 0.55
    b = buf(dur)
    t = tt(dur)
    at = 0.003
    while at < 0.003 + L:
        u = (at - 0.003) / L
        place(b, norm(noise_burst(p['lo'], p['hi'], 0.0012, r, attack=0.0002)), at, r.uniform(0.4, 1.0) * (1 - 0.4 * u))
        at += r.uniform(0.0012, 0.004)
    e = (t > 0.003) * np.clip((t - 0.003) / 0.004, 0, 1) * np.where(t < 0.003 + L, 1.0, np.exp(-(t - 0.003 - L) / 0.015))
    b += 0.30 * norm(bandpass(noise(dur, r), 600, 3000) * e)
    t0 = np.maximum(t - 0.003 - L * 0.6, 0)
    fe = (t > 0.003 + L * 0.6) * np.clip(t0 / 0.01, 0, 1) * np.exp(-t0 / (p['flut'] / 4))
    fl = 0.5 + 0.5 * np.sin(2 * np.pi * p['fr'] * t) ** 2
    b += 0.25 * norm(bandpass(noise(dur, r), 800, 4000) * fe * fl)
    return b


def sequencer_bell(take, r):
    """The cash-register bell at the end of a read (ding and ding_open key 1 at pitch 1.0; a full house's second bell at
    7 or 8, pitch 1.12): a bright little bell struck once by its spring hammer at G6 (1568 Hz, the motif's G), bell
    partials 1, 2, 2.4, 3, 4.2 with the upper ones fairly strong (a till bell, not a sewing-room one), a light strike
    and the hammer's rebound 15 ms later; about 0.9 s heard."""
    b = buf(1.45)
    place(b, strike(r, 3500), 0.003, 0.30)
    place(b, norm(bell_modes(r, 1568.0, (0.20, 0.09, 0.06, 0.03, 0.015), amps=(1.0, 0.5, 0.4, 0.25, 0.15))), 0.003, 1.0)
    place(b, norm(bell_modes(r, 1568.0, (0.06, 0.03, 0.02, 0.012, 0.008))), 0.018, 0.25)
    return b


# ======================================================================= the Injection Rig

RUNLATCH = [dict(m=1.00, lo=1200.0, hi=4500.0, clear=0.300), dict(m=1.10, lo=1400.0, hi=5000.0, clear=0.280)]


def rig_unlatch(take, r):
    """The syringe's latch letting go (inject key 0; it ticks up half a pixel): the sprung latch clacks at 3 ms (a
    relay-like click about 1.5 kHz, a small brass ring about 1.3 kHz and the latch body's knock about 800 Hz), then from
    100 ms (tick 2) the needle slides up out of its brass sheath (easeOutCubic: fast, then slowing; a thin 1.2-5 kHz
    scrape) and clears the sheath's mouth at 280-300 ms with a tiny brass tick. Low-passed at 7 kHz."""
    p = RUNLATCH[take - 1]
    m = p['m']
    dur = 0.60
    b = buf(dur)
    t = tt(dur)
    place(b, mix(0.6 * relay_click(r, 1500 * m, release_after=0.022), 0.4 * brass_ring(r, 1300 * m, 0.8),
                 0.5 * knock(r, 800 * m, 0.8, th=(240, 160), th_gain=0.3, nz=0.15, band=(500, 3000), hp=1200)), 0.003, 1.0)
    s = env_seg(t, 0.10, p['clear'], 'out') * np.clip((t - 0.10) / 0.01, 0, 1)
    b += 0.18 * norm(bandpass(noise(dur, r), p['lo'], p['hi']) * s)
    place(b, small_tick(r, 2400 * m, 0.004), p['clear'], 0.12)
    return lowpass(b, 7000)


RSERVO = [dict(m=1.00, grit=0.40, air=0.16), dict(m=1.08, grit=0.50, air=0.20), dict(m=0.92, grit=0.34, air=0.13)]


def rig_servo(take, r):
    """The arm's servo swinging the syringe (inject key 7 at pitch 1.0, out to the player; the tail's key 4 and cancel
    key 2 at pitch 0.9, home): the drive's relay pulls in at 3 ms, the house motor (whine, commutator grit, air) gives
    a small wind-up back to 100 ms (ticks 7-9, a third of the speed) and then the swing (easeInOutSine to 500 ms), the
    gear train stops against its end at 500 ms (a tooth and a small steel knock) and the 4 % overshoot settles back at
    620 ms (a soft tick)."""
    p = RSERVO[take - 1]
    m = p['m']
    stop = 0.50
    dur = 1.00
    b = buf(dur)
    t = tt(dur)
    s = smooth(np.maximum(0.35 * env_seg(t, 0.003, 0.10), env_seg(t, 0.10, stop)), 8)
    b += motor(t, s, r, 150 * m, 480 * m, grit=p['grit'], air=p['air'], whine=0.30)
    place(b, relay_click(r, 1700 * m, release_after=0.026), 0.003, 0.45)
    place(b, mix(tooth(r, 2600 * m), knock(r, 1300 * m, 0.7, th=(260, 180), th_gain=0.25, nz=0.25, band=(800, 5000), hp=2000)),
          stop, 0.35)
    place(b, small_tick(r, 2200 * m, 0.004), stop + 0.12, 0.15)
    return b


PRIME = [dict(m=1.00, sq=1150.0, hold=0.103), dict(m=1.09, sq=1300.0, hold=0.098)]


def squeak(r, f, dur, rise=0.4):
    """Rubber on glass: a sine rising by `rise` across dur with a stick-slip warble (90 Hz, 40 Hz deep) and a 140 Hz
    chatter, a sin^2 swell; second harmonic at 0.3."""
    t = tt(dur)
    fi = f * (1 + rise * t / dur) + 40 * np.sin(2 * np.pi * 90 * t)
    ph = 2 * np.pi * np.cumsum(fi) / SR
    return (np.sin(ph) + 0.3 * np.sin(2 * ph)) * np.sin(np.pi * t / dur) ** 2 * (0.7 + 0.3 * np.sin(2 * np.pi * 140 * t))


def rig_prime(take, r):
    """The jab's wind-up (inject key 25, pitch 1.1): the syringe drawn back a fifth of the jab, cocking: a sprung catch
    at 3 ms (a tooth and a small brass ring about 1.5 kHz), the plunger's rubber seal squeaking on the glass barrel as
    it shifts (a short rising squeak about 1.15-1.3 kHz, 70 ms), and the catch taking the hold at about 100 ms (tick
    27, a tooth and a tick). The jab that follows is the server's rig.hiss."""
    p = PRIME[take - 1]
    m = p['m']
    b = buf(0.40)
    place(b, mix(0.6 * tooth(r, 2600 * m), 0.5 * brass_ring(r, 1500 * m, 0.6)), 0.003, 0.80)
    place(b, squeak(r, p['sq'], 0.07), 0.010, 0.35)
    place(b, mix(tooth(r, 2900 * m), 0.4 * small_tick(r, 2000 * m, 0.004), 0.5 * brass_ring(r, 1550 * m, 0.7)), p['hold'], 0.55)
    return b


PLUNGE = [dict(m=1.00, d=1.00, bounce=0.050), dict(m=1.08, d=0.82, bounce=0.046)]


def rig_plunge(take, r):
    """The plunger driven home by the jab's momentum (accepted and mutated tails, key 2): the thumb pad bottoms on the
    barrel's flange at 3 ms (the glass barrel's knock about 2.2 kHz, the brass flange's ring about 1.8 kHz, a strike and
    a thunk), the last of the serum squirts out (a short wet 1-5 kHz hiss) and the quarter-pixel bounce taps again a
    tick on."""
    p = PLUNGE[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.45)
    hit = mix(0.40 * strike(r, 2000), 0.45 * norm(glass_modes(r, 2200 * m, decays=(0.020 * d, 0.012 * d, 0.008 * d, 0.005 * d))),
              0.40 * brass_ring(r, 1800 * m, 0.8 * d), 0.35 * thunk(r, 230, 160, 0.020, 0.008))
    place(b, norm(hit), 0.003, 1.0)
    place(b, norm(noise_burst(1000, 5000, 0.020, r, attack=0.004)), 0.006, 0.25)
    place(b, norm(mix(0.5 * norm(glass_modes(r, 2250 * m, decays=(0.010, 0.007, 0.005, 0.003))), 0.4 * small_tick(r, 1900 * m, 0.004))),
          0.003 + p['bounce'], 0.22)
    return b


def rig_ding(take, r):
    """The pump's bell for an accepted serum (accepted tail key 3, pitch 1.2): a petrol pump's solenoid bell, the
    solenoid's clack at 3 ms and its hammer striking an E6 bell (1318.5 Hz; heard at pitch 1.2 about 1582 Hz, a hair
    above the motif's G6) 2 ms later, bell partials 1, 2, 2.4, 3, 4.2, shorter than the printer's ping (a pump's dry
    'ding'); about 0.6 s heard."""
    b = buf(1.10)
    place(b, relay_click(r, 1900, release_after=0.030), 0.003, 0.30)
    place(b, strike(r, 3000), 0.005, 0.25)
    place(b, norm(bell_modes(r, 1318.5, (0.13, 0.06, 0.04, 0.022, 0.011))), 0.005, 1.0)
    return b


BURP = [dict(f0=(130.0, 85.0), L=0.20, F=(800.0, 1700.0), flut=30.0), dict(f0=(150.0, 95.0), L=0.25, F=(700.0, 1500.0), flut=26.0)]


def rig_burp(take, r):
    """The rejected serum kicked back up the barrel (rejected tail key 1, pitch 0.8): the plunger's thump at 3 ms, then
    a pocket of air forced back through the liquid past the rubber seal: a short rubbery burp, a rough voiced buzz (f0
    falling 130 -> 85 or 150 -> 95 Hz, its period jittered) through two wet formants (about 700-800 and 1500-1700 Hz),
    chattering at 26-30 Hz as the seal flaps, dying as (1 - u)^1.2 over 0.2-0.25 s, a wet froth of 1-4 kHz noise on it,
    and two bubbles bursting in it."""
    p = BURP[take - 1]
    L = p['L']
    dur = round(L + 0.35, 3)
    b = buf(dur)
    t = tt(dur)
    u = np.clip((t - 0.003) / L, 0, 1)
    wob = smooth(r.standard_normal(len(t)), 6)
    wob /= max(np.abs(wob).max(), 1e-9)
    f0 = p['f0'][0] * (p['f0'][1] / p['f0'][0]) ** u * (1 + 0.06 * wob)
    src = norm(osc_harm(f0, 40, r, 0.8))
    env = (t > 0.003) * np.clip((t - 0.003) / 0.006, 0, 1) * (1 - u) ** 1.2
    fl = 0.6 + 0.4 * np.sin(2 * np.pi * p['flut'] * t)
    x = src * env * fl
    out = norm(bandpass(x, p['F'][0] * 0.7, p['F'][0] * 1.4)) + 0.6 * norm(bandpass(x, p['F'][1] * 0.8, p['F'][1] * 1.3))
    froth = bandpass(noise(dur, r), 1000, 4000) * env * fl
    b += 0.80 * norm(out) + 0.18 * norm(froth)
    place(b, thunk(r, 200, 120, 0.020, 0.008), 0.003, 0.30)
    place(b, bubble(r, 700, 0.015, rise=0.4), 0.06, 0.25)
    place(b, bubble(r, 950, 0.012, rise=0.5), 0.003 + L * 0.75, 0.20)
    return b


COUNTER = [dict(m=1.00, n=16, L=0.36), dict(m=1.10, n=13, L=0.31)]


def rig_counter(take, r):
    """The pump's counter wheels rolling on a rejection (rejected tail key 3, pitch 0.9; the counter texture rolls):
    the number wheels spun and coasting, their detents clicking past at about 90 a second slowing to 11 (13-16 clicks,
    every fifth a heavier carry with a small body about 1.5 kHz), and the wheels landing on their detent with a small
    clunk at 0.31-0.36 s."""
    p = COUNTER[take - 1]
    m, n, L = p['m'], p['n'], p['L']
    dur = round(L + 0.30, 3)
    b = buf(dur)
    for k in range(n):
        tk = 0.003 + L * (1 - (1 - k / n) ** 0.5)
        g = 1.0 - 0.4 * k / n
        click = tooth(r, 3200 * m * r.uniform(0.97, 1.03))
        if k % 5 == 0:
            click = norm(mix(click, 0.6 * norm(modal(1500 * m, [1, 2.756], [1, 0.4], [0.010, 0.006], r))))
            g *= 1.2
        place(b, click, tk, 0.55 * g)
    place(b, mix(knock(r, 1700 * m, 0.6, th=(300, 220), th_gain=0.2, nz=0.3, band=(1500, 6000), hp=2500), 0.5 * tooth(r, 3000 * m)),
          0.003 + L, 0.60)
    return b


SPROING = [dict(f=(420.0, 340.0), vib=(22.0, 9.0), L=0.55, coil=2400.0), dict(f=(470.0, 380.0), vib=(26.0, 11.0), L=0.65, coil=2700.0)]


def rig_sproing(take, r):
    """The needle startled back on its spring (mutated tail key 3): the needle's knock at 3 ms and a coil spring set
    ringing: a twang whose pitch settles (420 -> 340 or 470 -> 380 Hz) under a wide, slowing wobble (a vibrato of 22 ->
    9 or 26 -> 11 Hz, +-18 % falling to +-3 %), its partials stretched like a spring's (1, 2.06, 3.17, 4.31, 5.5),
    decaying with a fifth of 0.55-0.65 s; the coil's dispersive chirp at the start (3.5 -> 1 kHz in about 30 ms) and
    its bright short ring (2.4-2.7 kHz)."""
    p = SPROING[take - 1]
    L = p['L']
    dur = round(L + 0.45, 3)
    b = buf(dur)
    t = tt(dur)
    vr = p['vib'][1] + (p['vib'][0] - p['vib'][1]) * np.exp(-t / 0.15)
    depth = 0.15 * np.exp(-t / 0.12) + 0.03
    fb = p['f'][1] + (p['f'][0] - p['f'][1]) * np.exp(-t / 0.08)
    fi = fb * (1 + depth * np.sin(2 * np.pi * np.cumsum(vr) / SR))
    tw = sum(a * np.sin(2 * np.pi * np.cumsum(fi * k) / SR + r.uniform(0, 2 * np.pi))
             for k, a in zip((1, 2.06, 3.17, 4.31, 5.5), (1.0, 0.7, 0.55, 0.4, 0.25)))
    env = (t > 0.003) * np.clip((t - 0.003) / 0.003, 0, 1) * np.exp(-np.maximum(t - 0.003, 0) / (L / 5))
    b += 0.70 * norm(tw * env)
    place(b, sweep_sine(3500, 1000, 0.012, 0.010), 0.003, 0.30)
    place(b, norm(modal(p['coil'], [1, 1.5, 2.3], [1, 0.5, 0.3], [0.060, 0.035, 0.020], r, 0.02)), 0.004, 0.35)
    place(b, knock(r, 1400, 0.7, th=(260, 180), th_gain=0.2, nz=0.3, band=(800, 5000), hp=2000), 0.003, 0.55)
    return b


HOLSTER = [dict(m=1.00, d=1.00, tip=0.008, bounce=0.050), dict(m=1.09, d=0.82, tip=0.011, bounce=0.046),
           dict(m=0.92, d=1.18, tip=0.006, bounce=0.055)]


def rig_holster(take, r):
    """The syringe dropped back into its brass sheath (the tails' key 15, cancel key 12; easeInQuad into the contact):
    the flange seats on the sheath's mouth at 3 ms (a strike, the brass sheath's ring about 1.9 kHz and a duller body
    about 800 Hz with a thunk), the needle's tip ticks the sheath's bottom 6-11 ms later, and the half-pixel bounce seats
    it again a tick on, with a settle tick at 100 ms."""
    p = HOLSTER[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.50)
    place(b, mix(0.9 * knock(r, 800 * m, d, th=(230, 150), th_gain=0.35, nz=0.25, band=(700, 4500), hp=1500),
                 0.55 * brass_ring(r, 1900 * m, 1.8 * d)), 0.003, 1.0)
    place(b, small_tick(r, 3200 * m, 0.004), 0.003 + p['tip'], 0.25)
    place(b, mix(0.6 * knock(r, 850 * m, 0.7 * d, th_gain=0.25, nz=0.15), 0.35 * brass_ring(r, 1950 * m, 0.6 * d)),
          0.003 + p['bounce'], 0.28)
    place(b, small_tick(r, 2400 * m, 0.004), 0.103, 0.10)
    return b


HISS = [dict(tau=0.075, lo=4500.0, hi=15000.0), dict(tau=0.062, lo=5000.0, hi=16000.0), dict(tau=0.095, lo=4000.0, hi=14000.0)]


def rig_hiss(take, r):
    """The injection at the jab (the server, on its tick 30, with the flinch and the plaster): the needle goes in at 3 ms
    (a tiny dry 'tk' about 3.5 kHz), the serum is driven in under pressure: a pneumatic hypo hiss up in 4 ms, decaying
    with a 62-95 ms time constant, its band falling as the pressure goes (4-16 kHz down to about 2.6-10 kHz)."""
    p = HISS[take - 1]
    dur = 0.70
    b = buf(dur)
    t = tt(dur)
    place(b, small_tick(r, 3500, 0.003), 0.003, 0.35)
    t0 = np.maximum(t - 0.005, 0)
    pr = (t >= 0.005) * np.clip(t0 / 0.004, 0, 1) * np.exp(-t0 / p['tau'])
    b += 0.90 * norm(sweep_bandpass(noise(dur, r), p['lo'] * (0.65 + 0.35 * pr), p['hi'] * (0.6 + 0.4 * pr)) * pr)
    return b


# ======================================================================= the Tesla Node

def buzz(r, f, dur, nharm=25, tilt=0.8, band=(150.0, 5000.0)):
    """The coil's electric buzz on a per-sample frequency curve f: harmonics 1/k^tilt, band-limited."""
    return bandpass(osc_harm(f, nharm, r, tilt), band[0], band[1])


PERK = [dict(m=1.00, bw=(320.0, 1150.0)), dict(m=1.08, bw=(360.0, 1300.0))]


def tesla_perk(take, r):
    """The node perking up (perk key 0, the sender's first wrench click; the ring crouches by 2 and lifts 3 px by 7,
    easeOutBack): its relay pulls in at 3 ms, and as the ring lifts the field charges: a rising 'bwip' (a sine gliding
    320 -> 1150 Hz or so over 80-350 ms, with its second harmonic), the coil's buzz rising with it (60 -> 120 Hz) and a
    breath of corona (3-9 kHz, gated by the buzz), all let go in 40 ms after the overshoot at 350 ms."""
    p = PERK[take - 1]
    m = p['m']
    dur = 0.75
    b = buf(dur)
    t = tt(dur)
    place(b, relay_click(r, 1900 * m, release_after=0.025), 0.003, 0.50)
    u = np.clip((t - 0.08) / 0.27, 0, 1)
    e = np.where(t < 0.35, u ** 1.5, np.exp(-(t - 0.35) / 0.04)) * (t > 0.08)
    e = smooth(e, 6)
    fbw = p['bw'][0] * (p['bw'][1] / p['bw'][0]) ** u
    ph = 2 * np.pi * np.cumsum(fbw) / SR
    bwip = np.sin(ph) + 0.3 * np.sin(2 * ph)
    fh = (60 + 60 * u) * m
    bz = buzz(r, fh, dur)
    gate = (0.5 + 0.5 * np.cos(2 * np.pi * np.cumsum(fh) / SR)) ** 4
    cor = bandpass(noise(dur, r), 3000, 9000) * gate
    b += e * (0.45 * norm(bwip) + 0.35 * norm(bz) + 0.15 * norm(cor))
    return b


LOOK = [dict(m=1.00, lo=2000.0, hi=8000.0), dict(m=1.10, lo=2300.0, hi=9000.0)]


def tesla_look(take, r):
    """The ball looking round (perk key 7; the code sweeps the aim base + 100 sin(2 pi (t - 6)/26) over ticks 6-32): the
    pointer's brass shoe sliding round the steel rim, its speed following the sweep (|cos|: fast at the start, stopped
    at the two turns 275 and 925 ms in, fast again at 0.6 s, and easing to rest by 1.35 s as the spring catches it): a
    brushed rub (2-9 kHz) and a faint bearing whir, with the shoe's play ticking at each turn and a settle tick at the
    end."""
    p = LOOK[take - 1]
    m = p['m']
    dur = 1.80
    b = buf(dur)
    t = tt(dur)
    sp = np.abs(np.cos(2 * np.pi * (t / T + 1) / 26))
    sp = np.where(t < 1.25, sp, sp[int(1.25 * SR) - 1] * np.exp(-(t - 1.25) / 0.05)) * np.clip(t / 0.003, 0, 1)
    sp = smooth(sp, 10)
    b += 0.70 * rub(r, dur, sp, p['lo'] * m, p['hi'] * m, (700 * m, 0.08))
    place(b, small_tick(r, 2600 * m, 0.004), 0.003, 0.30)
    for at in (0.275, 0.925):
        place(b, small_tick(r, 2400 * m * r.uniform(0.97, 1.03), 0.004), at, 0.30)
    place(b, small_tick(r, 2200 * m, 0.005), 1.36, 0.25)
    return b


SETTLE = [dict(m=1.00, tau=0.050), dict(m=1.10, tau=0.040)]


def tesla_settle(take, r):
    """The ring coming down onto its field (perk key 31, easeInQuad from tick 26): it never touches; the field catches it
    with a soft magnetic 'thum' at 3 ms (a short buzz about 110-120 Hz and its harmonics, 40-50 ms), the pointer's shoe
    rattles once on the rim (a small tick), and the 0.3 px bounce is caught again at 100 ms (tick 33), softer."""
    p = SETTLE[take - 1]
    m = p['m']
    b = buf(0.55)
    place(b, thum(r, 110 * m, p['tau']), 0.003, 1.0)
    place(b, small_tick(r, 2400 * m, 0.004), 0.006, 0.30)
    place(b, thum(r, 115 * m, p['tau'] * 0.6), 0.103, 0.35)
    return b


SWING = [dict(m=1.00, delta=135.0), dict(m=1.10, delta=80.0)]


def spring_speed(delta, ticks=24):
    """The pointer's aim spring per tick (err = aim - yaw; vel = clamp(vel * 0.5 + 0.15 * err, +-20); yaw += vel) for
    a swing of `delta` degrees: per-tick |vel| / 20 at the tick ends, and the ticks where the motion reverses."""
    yaw, vel = 0.0, 0.0
    sp, rev = [0.0], []
    for k in range(ticks):
        err = delta - yaw
        nv = float(np.clip(vel * 0.5 + 0.15 * err, -20, 20))
        if k and np.sign(nv) != np.sign(vel) and abs(nv) > 0.05:
            rev.append(k)
        vel = nv
        yaw += vel
        sp.append(abs(vel) / 20)
    return np.array(sp), rev


def tesla_swing(take, r):
    """The ball swinging onto its partner (link key 0; the code's aim spring, ticks 0-15): the pointer's shoe sliding
    round the rim at the spring's speed (simulated per tick for a swing of 135 or 80 degrees: it runs at the 20-degree
    clamp, then eases in and overshoots about 3 degrees), a brushed rub and a faint whir as in tesla.look, the shoe's
    play ticking where it turns back, and a settle tick."""
    p = SWING[take - 1]
    m = p['m']
    sp, rev = spring_speed(p['delta'])
    dur = 1.30
    b = buf(dur)
    t = tt(dur)
    s = np.interp(t / T, np.arange(len(sp)), sp) * np.clip(t / 0.003, 0, 1)
    s = smooth(s, 10)
    b += 0.70 * rub(r, dur, s, 2000 * m, 8000 * m, (650 * m, 0.08))
    place(b, small_tick(r, 2600 * m, 0.004), 0.003, 0.30)
    for k in rev[:2]:
        place(b, small_tick(r, 2400 * m, 0.004), k * T, 0.22)
    last = np.where(s > 0.02)[0]
    place(b, small_tick(r, 2200 * m, 0.005), last[-1] / SR + 0.01, 0.18)
    return b


ZAP = [dict(d=0.16, f=(90.0, 140.0), m=1.00), dict(d=0.20, f=(80.0, 125.0), m=0.92), dict(d=0.13, f=(100.0, 160.0), m=1.08)]


def tesla_link_zap(take, r):
    """The arc striking to the partner (link key 16): a hard snap at 3 ms (a strike and an 0.8-8 kHz crack), arc crackle
    (2-12 kHz bursts 6-25 ms apart) for 130-200 ms over a buzz rising 80-100 -> 125-160 Hz as the arc takes hold, and
    the ring's 1.5 px jump a tick later caught by its field (a soft thum at 53 ms)."""
    p = ZAP[take - 1]
    m, d = p['m'], p['d']
    b = buf(d + 0.40)
    place(b, strike(r, 2500, 0.0008), 0.003, 1.0)
    place(b, norm(noise_burst(800, 8000, 0.004, r)), 0.003, 0.70)
    place(b, norm(crackle(r, d, 2000, 12000, spacing=(0.006, 0.025), width=(0.002, 0.006))), 0.004, 0.55)
    tb = tt(d)
    f = p['f'][0] + (p['f'][1] - p['f'][0]) * tb / d
    bz = buzz(r, f, d, tilt=0.6, band=(200, 6000)) * np.sin(np.pi * tb / d) ** 0.5
    place(b, norm(pad(bz, 0.02)), 0.004, 0.45)
    place(b, thum(r, 120 * m, 0.035), 0.053, 0.25)
    return b


UNLINK = [dict(m=1.00, pew=(1100.0, 260.0), L=0.26), dict(m=1.10, pew=(1250.0, 300.0), L=0.22)]


def tesla_unlink(take, r):
    """The link broken (unlink key 0; the ring lets go upward and sinks): a small snap at 3 ms as the arc lets go, a
    falling 'pew' (a sine and its harmonics sliding 1.1-1.25 kHz down to 260-300 Hz over 0.22-0.26 s, buzzing at 60 Hz
    under it), the crackle thinning out over 150 ms, and the coil's relay dropping out at 30 ms."""
    p = UNLINK[take - 1]
    m, L = p['m'], p['L']
    dur = L + 0.45
    b = buf(dur)
    t = tt(dur)
    place(b, strike(r, 3000, 0.0008), 0.003, 0.70)
    place(b, norm(noise_burst(1500, 9000, 0.003, r)), 0.003, 0.50)
    u = np.clip((t - 0.004) / L, 0, 1)
    fp = p['pew'][0] * (p['pew'][1] / p['pew'][0]) ** u
    ph = 2 * np.pi * np.cumsum(fp) / SR
    e = (t > 0.004) * np.clip((t - 0.004) / 0.004, 0, 1) * np.where(
        u < 1, (1 - 0.85 * u) ** 1.3, 0.15 ** 1.3 * np.exp(-(t - 0.004 - L) / 0.02))
    pew = (np.sin(ph) + 0.4 * np.sin(2 * ph) + 0.2 * np.sin(3 * ph)) * (0.75 + 0.25 * np.cos(2 * np.pi * 60 * t))
    b += 0.75 * norm(pew * e)
    cr = crackle(r, 0.15, 2000, 10000, spacing=(0.010, 0.045), width=(0.002, 0.005))
    cr *= np.clip(1 - tt(len(cr) / SR) / 0.2, 0, 1) ** 1.5
    place(b, norm(cr), 0.005, 0.35)
    place(b, relay_click(r, 1600 * m, release_after=0.028), 0.030, 0.30)
    return b


WIND = [dict(m=1.00, tau=0.25, land=0.250), dict(m=1.08, tau=0.20, land=0.240)]


def tesla_wind_down(take, r):
    """Sending stops (stop key 0: the field collapses, the ring falls half a pixel below rest by tick 5, bounces at 8):
    the relay drops out at 3 ms, the coil's hum runs down (the buzz falling 120 -> about 35 Hz, its level falling with a
    0.20-0.25 s time constant), the corona hiss goes in 80 ms, and the ring's fall is caught by what is left of the field
    at 240-250 ms (a soft thum) with the bounce at 400 ms, fainter."""
    p = WIND[take - 1]
    m = p['m']
    dur = 1.30
    b = buf(dur)
    t = tt(dur)
    s = np.exp(-np.maximum(t - 0.008, 0) / p['tau']) * np.clip((t - 0.003) / 0.005, 0, 1)
    s = np.where(t < 0.95, s, s * np.exp(-(t - 0.95) / 0.03))
    f = (35 + 85 * s) * m
    bz = buzz(r, f, dur, nharm=60, tilt=0.55, band=(150, 6000))
    gate = (0.5 + 0.5 * np.cos(2 * np.pi * np.cumsum(f) / SR)) ** 4
    cor = bandpass(noise(dur, r), 3000, 9000) * gate * np.exp(-t / 0.08) * np.clip((t - 0.003) / 0.005, 0, 1)
    b += 0.60 * norm(bz * s) + 0.25 * norm(cor)
    place(b, relay_click(r, 1700 * m, release_after=0.030), 0.003, 0.45)
    place(b, thum(r, 90 * m, 0.040), p['land'], 0.30)
    place(b, thum(r, 85 * m, 0.030), 0.40, 0.12)
    return b


HUM_L = 2.0  # s: one turn of the ring at full rate (a turn per 40 ticks)


def tesla_hum(take, r):
    """The coil sending, a seamless 2.0 s loop (one turn of the ring at full rate): the coil's buzz (120 Hz and 30
    harmonics falling as 1/k^0.8, band-limited 150-6000 Hz; 240 whole cycles), its corona hiss (3-11 kHz noise gated by
    the buzz), a faint 60 Hz mains under it, the stepped ring's four corners passing as soft air swishes (600-4000 Hz,
    four a loop) and six small crackles placed round the loop (circularly); the load wanders once a loop."""
    n = int(round(HUM_L * SR))
    t = np.arange(n) / SR
    ph = 2 * np.pi * 120.0 * t
    bz = sum((1 / k ** 0.8) * np.sin(k * ph + r.uniform(0, 2 * np.pi)) for k in range(1, 31))
    bz = cband(bz, 150, 6000)
    gate = (0.5 + 0.5 * np.cos(ph)) ** 6
    cor = cband(cnoise(n, r), 3000, 11000) * (0.3 + 0.7 * gate)
    swish = (0.5 - 0.5 * np.cos(2 * np.pi * 4 * t / HUM_L)) ** 2
    air = cband(cnoise(n, r), 600, 4000) * swish * np.array([1.0, 0.85, 0.95, 0.8])[np.floor(t / HUM_L * 4).astype(int) % 4]
    crk = np.zeros(n)
    for at, g in ((0.13, 1.0), (0.41, 0.6), (0.77, 0.8), (1.02, 0.5), (1.38, 0.9), (1.71, 0.6)):
        x = np.zeros(n)
        bst = norm(noise_burst(2500, 10000, 0.003, r))
        x[:len(bst)] = g * bst
        crk += np.roll(x, int(at * SR))
    mains = np.sin(2 * np.pi * 60.0 * t + r.uniform(0, 2 * np.pi))
    load = 0.92 + 0.08 * np.sin(2 * np.pi * t / HUM_L + 0.3)
    return load * (0.36 * norm(bz) + 0.24 * norm(cor) + 0.14 * norm(air) + 0.08 * norm(crk) + 0.10 * mains)


# ======================================================================= the Charging Dock

DCLACK = [dict(m=1.00, d=1.00, seat=0.153), dict(m=1.10, d=0.80, seat=0.142), dict(m=0.88, d=1.30, seat=0.172)]


def dock_clack(take, r):
    """The knife switch thrown home into its copper jaws (occupy and topup key 9, brownout_out key 6, tap_on key 7,
    tap_low key 3; easeInQuad into the contact): a strike at 3 ms, the brass blade's ring (free-bar modes about 1.25 kHz,
    10-30 ms), the jaws' spring leaves (about 2 kHz, short) wiping the blade (a 2.5-8 kHz rasp, 12 ms), the slate
    board's dull body (about 380 Hz) and a thunk; the blade bounces 4 degrees out (tick 10) and seats again at about
    150 ms (tick 12), softer."""
    p = DCLACK[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.60)
    hit = mix(0.45 * strike(r, 1800 * m),
              0.50 * norm(modal(1250 * m, [1, 2.756, 5.404], [1, 0.55, 0.3], [0.030 * d, 0.018 * d, 0.010 * d], r, 0.01)),
              0.35 * norm(modal(2050 * m, [1, 1.59, 2.14], [1, 0.5, 0.3], [0.015 * d, 0.010 * d, 0.006 * d], r)),
              0.35 * thunk(r, 190, 120, 0.030, 0.012), 0.30 * norm(noise_burst(1500 * m, 7000 * m, 0.006, r)))
    place(b, norm(hit), 0.003, 1.0)
    w = tt(0.03)
    place(b, norm(bandpass(noise(0.03, r), 2500 * m, 8000 * m) * np.sin(np.pi * w / 0.012) ** 2 * (w < 0.012)), 0.004, 0.20)
    place(b, norm(modal(380 * m, [1, 2.3], [1, 0.4], [0.025, 0.015], r)), 0.004, 0.30)
    place(b, norm(mix(0.4 * strike(r, 2000), 0.6 * norm(modal(1260 * m, [1, 2.756], [1, 0.4], [0.015, 0.009], r)),
                      0.3 * norm(modal(2080 * m, [1, 1.59], [1, 0.4], [0.008, 0.005], r)))), p['seat'], 0.30)
    return b


FALL = [dict(m=1.00, d=1.00, bounce=0.200), dict(m=1.14, d=0.75, bounce=0.188), dict(m=0.88, d=1.25, bounce=0.214)]


def dock_fall(take, r):
    """The knife switch falling open onto its stop under its own weight (vacate and full key 8, vacate_low key 6, tap
    key 7; easeInQuad into the contact): the blade's heel knocks the steel stop at 3 ms (a dull knock about 900 Hz with
    a thunk), the long brass blade rings on briefly (free-bar modes about 1.1 kHz, 40 ms) and the T-grip's wood ticks;
    the 2-degree bounce comes down again at about 200 ms (tick 12), soft."""
    p = FALL[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.60)
    place(b, knock(r, 900 * m, d, th=(200, 130), th_gain=0.45, nz=0.25, band=(600 * m, 4000 * m), hp=1200 * m), 0.003, 0.85)
    place(b, norm(modal(1100 * m, [1, 2.756, 5.404], [1, 0.5, 0.25], [0.040 * d, 0.024 * d, 0.012 * d], r, 0.01)), 0.004, 0.40)
    place(b, norm(modal(1600 * m, [1, 2.7], [1, 0.4], [0.006, 0.004], r)), 0.006, 0.15)
    place(b, mix(0.6 * knock(r, 950 * m, 0.7 * d, th_gain=0.3, nz=0.15), 0.25 * norm(modal(1110 * m, [1, 2.756], [1, 0.4], [0.020, 0.012], r))),
          p['bounce'], 0.25)
    return b


def dock_ding(take, r):
    """The scale's bell when the jar is full (full key 10, as the switch trips open): a doctor's-scale bell struck once
    at C6 (1046.5 Hz, the motif's home note), bell partials 1, 2, 2.4, 3, 4.2, rounder and longer than the till's (1.1 s
    heard), a light strike."""
    b = buf(1.70)
    place(b, strike(r, 2500), 0.003, 0.20)
    place(b, norm(bell_modes(r, 1046.5, (0.24, 0.11, 0.07, 0.04, 0.02))), 0.003, 1.0)
    return b


FIZZLE = [dict(m=1.00, f=(100.0, 62.0), n=9), dict(m=1.08, f=(110.0, 70.0), n=7)]


def dock_fizzle(take, r):
    """The brown-out (brownout key 0, occupy_low key 12): the arc in the jaws sputters as the power fails: a mains buzz
    sagging 100-110 -> 62-70 Hz and dying over 0.7 s, cut in and out by the sputter (7-9 uneven gaps), crackle (1.5-6 kHz
    bursts) thinning out, the blade's dull scrape as it sags out of the jaws (0.25-0.65 s, ticks 5-13) and a soft puff
    of smoke at 400 ms (tick 8)."""
    p = FIZZLE[take - 1]
    m = p['m']
    dur = 1.20
    b = buf(dur)
    t = tt(dur)
    place(b, strike(r, 2500, 0.0008), 0.003, 0.60)
    s = np.exp(-np.maximum(t - 0.003, 0) / 0.25) * np.clip(t / 0.003, 0, 1)
    s = np.where(t < 0.75, s, s * np.exp(-(t - 0.75) / 0.03))
    f = (p['f'][1] + (p['f'][0] - p['f'][1]) * s) * m
    sput = np.ones(len(t))
    at = 0.05
    for _ in range(p['n']):
        w = r.uniform(0.015, 0.05)
        sput *= 1 - 0.85 * np.exp(-((t - at - w / 2) / (w / 2.5)) ** 2)
        at += r.uniform(0.04, 0.09)
    bz = buzz(r, f, dur, tilt=0.7, band=(120, 4000)) * s * sput
    b += 0.55 * norm(bz)
    cr = crackle(r, 0.6, 1500, 6000, spacing=(0.012, 0.060), width=(0.002, 0.006))
    cr *= np.exp(-tt(len(cr) / SR) / 0.22)
    place(b, norm(cr), 0.004, 0.40)
    sc = env_seg(t, 0.25, 0.65) * (0.6 + 0.4 * np.sin(2 * np.pi * 23 * t) ** 2)
    b += 0.10 * norm(bandpass(noise(dur, r), 500 * m, 2500 * m) * sc)
    place(b, norm(noise_burst(300, 1500, 0.030, r, attack=0.008)), 0.40, 0.18)
    return b


MTICK = [dict(m=1.00, d=1.00), dict(m=1.12, d=0.80), dict(m=0.84, d=1.50)]


def dock_meter_tick(take, r):
    """The dial's soft tick while charging (charging keys 10 and 30, pitch 1.0 and 1.1): the needle's movement steps
    behind the dial glass at 3 ms: a tiny brass escapement tick (about 800 Hz, 4-8 ms, with a flick of noise), low-passed
    at 2 kHz by the glass. Very soft: it ticks every second while the jar charges."""
    p = MTICK[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.20)
    x = mix(0.6 * norm(modal(800 * m, [1, 1.7, 2.6], [1, 0.4, 0.2], [0.008 * d, 0.005 * d, 0.003 * d], r, 0.02)),
            0.30 * norm(noise_burst(600 * m, 3000 * m, 0.0015, r)), 0.20 * thunk(r, 260, 200, 0.008, 0.004))
    place(b, norm(lowpass(pad(x), 2000)), 0.003, 1.0)
    return b


SPARK = [dict(n=2, lo=3000.0, hi=12000.0, gap=0.012), dict(n=4, lo=2200.0, hi=9000.0, gap=0.014),
         dict(n=1, lo=3500.0, hi=14000.0, gap=0.015)]


def dock_spark(take, r):
    """A small electric snap at the jaws (with the electric_spark particles: occupy key 10, the charging loop's 0 and 20,
    tap_on 3 and 8): a sharp snap at 3 ms (a strike and a 3-14 kHz crack) and one to three smaller cracks 9-15 ms apart
    (four for take 2) after it, with a 20 ms whisper of buzz (100 Hz-gated noise)."""
    p = SPARK[take - 1]
    b = buf(0.22)
    place(b, strike(r, 3000, 0.0008), 0.003, 1.0)
    place(b, norm(noise_burst(p['lo'], p['hi'], 0.003, r)), 0.003, 0.70)
    for k in range(p['n']):
        place(b, norm(noise_burst(p['lo'], p['hi'], 0.003, r)), 0.003 + p['gap'] * (k + 1), 0.45 * 0.7 ** k)
    tb = tt(0.02)
    bz = bandpass(noise(0.02, r), 1500, 6000) * (0.5 + 0.5 * np.cos(2 * np.pi * 100 * tb)) ** 3 * np.sin(np.pi * tb / 0.02)
    place(b, norm(pad(bz, 0.01)), 0.005, 0.20)
    return b


# ---------------------------------------------------------------- references (measured as the game plays them)

PISTON_IN = dict(event='block.piston.contract', file='tile__piston__in.ogg', vol=0.5, pitch=0.675)
CRAFTER = dict(event='block.crafter.craft', file='block__crafter__craft.ogg', vol=1.0, pitch=1.0)
SPYGLASS = dict(event='item.spyglass.use', file='item__spyglass__use.ogg', vol=1.0, pitch=1.0)
FIZZ = dict(event='block.fire.extinguish', file='random__fizz.ogg', vol=0.5, pitch=2.0)
IRON_TD_OPEN = dict(event='block.iron_trapdoor.open', file='block__iron_trapdoor__open1.ogg', vol=0.9, pitch=0.95)
IRON_TD_CLOSE = dict(event='block.iron_trapdoor.close', file='block__iron_trapdoor__close1.ogg', vol=0.9, pitch=0.95)
COPPER_TD_CLOSE = dict(event='block.copper_trapdoor.close', file='block__copper_trapdoor__toggle1.ogg', vol=1.0, pitch=0.95)
WOOD_TD_OPEN = dict(event='block.wooden_trapdoor.open', file='block__wooden_trapdoor__open1.ogg', vol=0.9, pitch=0.95)
WOOD_TD_CLOSE = dict(event='block.wooden_trapdoor.close', file='block__wooden_trapdoor__close1.ogg', vol=0.9, pitch=0.95)
LANTERN = dict(event='block.lantern.place', file='block__lantern__place1.ogg', vol=1.0, pitch=0.88)
HONEY_PLACE = dict(event='block.honey_block.place', file='block__honeyblock__step1.ogg', vol=1.0, pitch=0.8)
XYLO = dict(event='block.note_block.xylophone', file='note__xylobone.ogg', vol=1.0, pitch=1.0)
SNIFFING = dict(event='entity.sniffer.sniffing', file='mob__sniffer__sniffing1.ogg', vol=1.0, pitch=1.0)
VAULT_ACTIVATE = dict(event='block.vault.activate', file='block__vault__activate.ogg', vol=1.0, pitch=1.0)
BEACON_AMBIENT = dict(event='block.beacon.ambient', file='block__beacon__ambient.ogg', vol=0.9, pitch=1.0)

BASE = dict(category='BLOCKS', attenuation=8, fixed_range=False, volume=1.0, ticks=None, muffled=False)
KEY_NOTE = ' Keyframe volume 1.0 (the file carries the loudness); the key\'s pitch is kept.'


def ev(**kw):
    d = dict(BASE)
    d.update(kw)
    return d


SOUNDS = [
    # ---- the Cerebral Array
    ev(id='array.card', file='array/card', fn=array_card, takes=1, target=-30.5, cls='tick (card flip, one of nine)',
       kind='click', fade_ms=8, hit_ms=3, ref=WOOD_TD_OPEN, pitch=[1.0, 1.0],
       pitch_per_play=[0.925, 0.95, 0.975, 1.0, 1.025, 1.05, 1.075, 1.1, 1.125],
       subtitle_en='Jury cards flip up', subtitle_pt='Cartões do júri se erguem',
       clip_key='vote and vote_anyway keys 14, 17, ... 38: array.card|0.3|0.925 ... 1.125 (0.9 + 0.025 i) -> volume 1.0, same pitches',
       play='Array clips vote and vote_anyway, a card every 3 ticks from key 14 to 38 (one per seat, the empty seats\' keys too unless the code drops them) at pitch 0.9 + 0.025 i, i = 1..9: the hinge at 3 ms, the flap gone in about 0.1 s, so the nine stay nine. One file: the rising pitch is the ripple.' + KEY_NOTE),
    ev(id='array.cards_down', file='array/cards_down', fn=array_cards_down, takes=3, target=-29.5, cls='tick (a row of cards landing)',
       kind='click', fade_ms=8, hit_ms=3, ref=WOOD_TD_OPEN, pitch=[1.0, 1.0], pitch_per_play=[1.05, 0.95],
       subtitle_en='Jury cards drop', subtitle_pt='Cartões do júri baixam',
       clip_key='vote and vote_anyway keys 53, 55: array.cards_down|0.5|1.05 and |0.5|0.95 -> volume 1.0, same pitches',
       play='Array clips vote and vote_anyway, keys 53 and 55 (the back and front rows\' contacts; the middle row\'s, 54, falls between the two files): three taps 6-25 ms apart at 3 ms, the bounce a tick on. A different take each time if the handler allows.' + KEY_NOTE),
    ev(id='array.gavel', file='array/gavel', fn=array_gavel, takes=3, target=-23.0, cls='slam (gavel on its block)',
       kind='latch', fade_ms=12, hit_ms=3, ref=WOOD_TD_CLOSE, pitch=[0.97, 1.03], attenuation=12,
       subtitle_en='Gavel bangs', subtitle_pt='Malhete bate na mesa',
       clip_key='vote_anyway keys 45, 50: array.gavel|1.0|0.95 and |1.0|1.08 (keep); refuse keys 4, 8: array.gavel|0.4|1.4 and |0.4|1.5 (keep 0.4: light taps, a layer under feedback.refused)',
       play='Server, at the array, on the vote tick T (the strike of the clip\'s first bang, c10), volume 1.0, random pitch 0.97-1.03, no pre-roll (hit at 3 ms); vote_anyway\'s two extra bangs (keys 45, 50) and the refusal\'s taps (keys 4, 8) are keyframed at the pitches the clips give. The bounce tocks again 46-55 ms on.'),
    ev(id='array.murmur', file='array/murmur', fn=array_murmur, takes=2, target=-29.5, cls='voices (low murmur, synthesised)',
       kind='hiss', fade_ms=40, hit_ms=3, ref=SNIFFING, pitch=[0.95, 1.05], attenuation=12,
       subtitle_en='Jury murmurs', subtitle_pt='O júri murmura',
       play='Server, at the array, on the vote tick T with array.gavel, volume 1.0, random pitch 0.95-1.05: four voices catch their breath on the bang (3 ms) and murmur for 1.05-1.25 s. No words: synthesised. Not the older sounds/array_murmur.ogg from tools/make_sounds.py, which this replaces if the mod registers that one under this id.'),
    ev(id='array.steam', file='array/steam', fn=array_steam, takes=1, target=-34.0, cls='idle machine life (faint loop)',
       kind='loop', fade_ms=0, hit_ms=None, ref=BEACON_AMBIENT, finish=loop_finish, loop=True, attenuation=6,
       pitch=[1.0, 1.0], subtitle_en='Broth urn steams', subtitle_pt='A urna de caldo fumega',
       play='Looped client sound (a tickable sound instance with looping on, created once per array, at locator urn) while `fed` is true and the broth is above 0: volume 1.0, pitch 1.0; start at volume 0 and chase it up 0.1 a tick, down the same when fed ends, stop under 0.01. Register with "stream": false. Seamless 2.0 s file, no fades in it. Deliberately faint (idle life).'),
    # ---- the Employee Mount
    ev(id='mount.receiver_lift', file='mount/receiver_lift', fn=mount_receiver_lift, takes=2, target=-28.0, cls='latch (handset off its cradle)',
       kind='latch', fade_ms=12, hit_ms=3, ref=IRON_TD_OPEN, pitch=[0.97, 1.03],
       subtitle_en='Receiver lifted', subtitle_pt='Fone tirado do gancho',
       clip_key='complaint key 2: mount.receiver_lift|0.5|1.0 -> mount.receiver_lift|1.0|1.0',
       play='Mount clip complaint, key 2 (the bottom of the quarter-pixel press): the press tick at 3 ms, the handset unseats at 55-66 ms (tick 3) with the hookswitch and a faint ringer tinkle.' + KEY_NOTE),
    ev(id='mount.hr_ringback', file='mount/hr_ringback', fn=mount_hr_ringback, takes=2, target=-30.5, cls='tick-class level (a burr in a handset)',
       kind='latch', fade_ms=15, hit_ms=3, ref=WOOD_TD_OPEN, pitch=[1.0, 1.0],
       subtitle_en='Handset rings out', subtitle_pt='O fone chama do outro lado',
       clip_key='complaint key 12: mount.hr_ringback|0.3|1.0 -> mount.hr_ringback|1.0|1.0',
       play='Mount clip complaint, key 12 (the handset just at the ear): one ringback burr from 3 ms, cut at 170-205 ms by the far end picking up (the clunk at 192-228 ms), just before mount.hr_mumble at key 16. Fixed pitch (a telephone tone).' + KEY_NOTE),
    ev(id='mount.hr_mumble', file='mount/hr_mumble', fn=mount_hr_mumble, takes=2, target=-30.5, cls='voice (tinny, through a handset; synthesised)',
       kind='hiss', fade_ms=40, hit_ms=3, ref=SNIFFING, pitch=[0.96, 1.04],
       subtitle_en='HR mumbles down the line', subtitle_pt='RH resmunga ao telefone',
       clip_key='complaint key 16: mount.hr_mumble|0.35|1.0 -> mount.hr_mumble|1.0|1.0',
       play='Mount clip complaint, key 16: stressed syllables at 0, 150, 300, 450, 600 ms land on the handset\'s jabbers (ticks 16, 19, 22, 25, 28), then a falling tail to 1.05-1.2 s, well before the lift at 60. No words: synthesised.' + KEY_NOTE),
    ev(id='mount.receiver_clack', file='mount/receiver_clack', fn=mount_receiver_clack, takes=3, target=-24.0, cls='slam (handset on its cradle)',
       kind='latch', fade_ms=12, hit_ms=3, ref=IRON_TD_CLOSE, pitch=[0.95, 1.05], attenuation=12,
       subtitle_en='Receiver slammed down', subtitle_pt='Fone batido no gancho',
       clip_key='complaint key 64: mount.receiver_clack|0.6|1.0 -> mount.receiver_clack|1.0|1.0',
       play='Mount clip complaint, key 64 (the slam\'s contact): the clack, the hookswitch and the ringer\'s jangle at 3-10 ms, the bounce a tick on (65).' + KEY_NOTE),
    ev(id='mount.stamp_lift', file='mount/stamp_lift', fn=mount_stamp_lift, takes=3, target=-32.5, cls='tick (soft, repeating)',
       kind='click', fade_ms=8, hit_ms=3, ref=CRAFTER, pitch=[0.95, 1.05], subtitle_en=None, subtitle_pt=None,
       clip_key='stamp key 2; stamp_3 keys 2, 12, 22: mount.stamp_lift|0.25|1.0 -> mount.stamp_lift|1.0|1.0',
       play='Mount clips stamp (key 2) and stamp_3 (keys 2, 12, 22), played by each client at every paid tic: the unstick at 3 ms, the neck\'s tick at 60 ms. No subtitle (part of the stamp; the thump carries it).' + KEY_NOTE),
    ev(id='mount.stamp_thump', file='mount/stamp_thump', fn=mount_stamp_thump, takes=3, target=-31.0, cls='tick (soft thump, repeating)',
       kind='click', fade_ms=8, hit_ms=3, ref=CRAFTER, pitch=[1.0, 1.0], pitch_per_play=[1.0, 1.05, 1.1],
       subtitle_en='Clerk stamps a form', subtitle_pt='Funcionário carimba',
       clip_key='stamp key 11: mount.stamp_thump|0.5|1.0; stamp_3 keys 11, 21, 31: |0.5|1.0, 1.05, 1.1 -> volume 1.0, same pitches',
       play='Mount clips stamp (key 11) and stamp_3 (keys 11, 21, 31 at pitch 1.0, 1.05, 1.1): the thump at 3 ms, the bounce a tick on. A random take each time.' + KEY_NOTE),
    ev(id='mount.clink', file='mount/clink', fn=mount_clink, takes=3, target=-26.0, cls='glass clink class (gem on a copper tray)',
       kind='latch', fade_ms=15, hit_ms=3, ref=LANTERN, pitch=[1.0, 1.0], pitch_per_play=[1.2],
       subtitle_en='Pay clinks into tray', subtitle_pt='Pagamento tilinta na bandeja',
       clip_key='payday and payday_back key 0: mount.clink|0.6|1.2 -> mount.clink|1.0|1.2',
       play='Mount clips payday and payday_back, key 0 (the emerald dropped into the tray by code), pitch 1.2: the strike at 3 ms, three hops 55-146 ms on.' + KEY_NOTE),
    ev(id='mount.cheer', file='mount/cheer', fn=mount_cheer, takes=2, target=-29.0, cls='voice (small happy chirp; synthesised)',
       kind='latch', fade_ms=15, hit_ms=3, ref=XYLO, pitch=[0.97, 1.03],
       subtitle_en='Employee cheers', subtitle_pt='Funcionário comemora',
       clip_key='payday_back key 40: mount.cheer|0.4|1.0 -> mount.cheer|1.0|1.0',
       play='Mount clip payday_back, key 40 (the head has turned round from the wall to be paid): two quick rising syllables from 3 ms, about 0.3 s. No words: synthesised.' + KEY_NOTE),
    ev(id='mount.bell_ting', file='mount/bell_ting', fn=mount_bell_ting, takes=1, target=-29.5, cls='blip (desk bell), played as a layer',
       kind='stinger', fade_ms=40, hit_ms=3, ref=XYLO, pitch=[1.0, 1.0], volume=0.5,
       subtitle_en=None, subtitle_pt=None,
       clip_key='refuse key 1: mount.bell_ting|0.4|1.0 -> mount.bell_ting|0.5|1.0 (keep it a layer)',
       play='Mount clip refuse, key 1, at volume 0.5 (deliberately: a layer under the server\'s feedback.refused, which carries the subtitle and the message, as machine.rattle at 0.4): one E7 ting, 0.7 s, fixed pitch.'),
    ev(id='mount.sigh', file='mount/sigh', fn=mount_sigh, takes=2, target=-30.5, cls='breath (soft; tick class level)',
       kind='hiss', fade_ms=40, hit_ms=3, ref=SNIFFING, pitch=[0.95, 1.05],
       subtitle_en='Employee sighs', subtitle_pt='Funcionário suspira',
       play='Client, at the jar (locator jar_top), every 400 ticks while the head has been turned to the wall (three days or more unpaid), volume 1.0, random pitch 0.95-1.05: the breath out from 3 ms, 0.8-0.9 s. No words: synthesised.'),
    # ---- the DNA Sequencer
    ev(id='sequencer.key', file='sequencer/key', fn=sequencer_key, takes=1, target=-29.5, cls='tick (register key)',
       kind='click', fade_ms=8, hit_ms=3, ref=WOOD_TD_OPEN, pitch=[1.0, 1.0], pitch_per_play=[0.8, 0.9, 1.0, 1.1],
       subtitle_en='Register key clacks', subtitle_pt='Tecla da caixa estala',
       clip_key='press_a/c/g/t key 2: sequencer.key|0.5|0.8, 0.9, 1.0, 1.1 -> volume 1.0, same pitches',
       play='Sequencer clips press_a, press_c, press_g, press_t, key 2 (the key at the bottom of its well) at pitch 0.8, 0.9, 1.0, 1.1: the clack at 3 ms, the escapement 18 ms on, gone in about 0.1 s. One file: the pitch is the key.' + KEY_NOTE),
    ev(id='sequencer.crank', file='sequencer/crank', fn=sequencer_crank, takes=3, target=-28.0, cls='servo-class (crank and gear train)',
       kind=('servo', 0.25), fade_ms=20, hit_ms=3, ref=IRON_TD_OPEN, pitch=[0.97, 1.03],
       subtitle_en='Register crank turns', subtitle_pt='Manivela da caixa gira',
       clip_key='ring key 4: sequencer.crank|0.4|1.0 -> sequencer.crank|1.0|1.0',
       play='Sequencer clip ring, key 4 (the turn is ticks 3-8): the teeth from 3 ms to 195 ms, the lever\'s clunk at 200 ms (tick 8), the pawl at 250 ms (tick 9). Every call: a random take each time.' + KEY_NOTE),
    ev(id='sequencer.tab', file='sequencer/tab', fn=sequencer_tab, takes=2, target=-30.0, cls='tick (flag on its stop)',
       kind='click', fade_ms=8, hit_ms=3, ref=WOOD_TD_OPEN, pitch=[1.0, 1.0], pitch_per_play=[1.0, 0.9],
       subtitle_en='Register tab pops up', subtitle_pt='A plaqueta da caixa sobe',
       clip_key='ring key 7: sequencer.tab|0.4|1.0; tally keys 5, 12, ... (slot s at 7s + 5): |0.4|1.0 + 0.02 s; load key 1: |0.4|0.9 -> volume 1.0, same pitches',
       play='Sequencer clips ring (key 7), tally and tally_full (each slot\'s pop at 7s + 5, pitch 1.0 + 0.02 s) and load (key 1, pitch 0.9): the plate on its top stop at 3 ms, the wobble 27-34 ms on, gone inside 0.12 s.' + KEY_NOTE),
    ev(id='sequencer.tab_down', file='sequencer/tab_down', fn=sequencer_tab_down, takes=2, target=-30.0, cls='tick (flag into the body)',
       kind='click', fade_ms=10, hit_ms=3, ref=WOOD_TD_OPEN, pitch=[0.97, 1.03],
       subtitle_en='Register tab drops', subtitle_pt='A plaqueta da caixa desce',
       clip_key='tally key 93 (tally_full 107), unload key 4: sequencer.tab_down|0.4|1.0 -> sequencer.tab_down|1.0|1.0',
       play='Sequencer clips tally (key 93), tally_full (key 107) and unload (key 4): the plate at the bottom of its well at 3 ms, the bounce a tick on.' + KEY_NOTE),
    ev(id='sequencer.tear', file='sequencer/tear', fn=sequencer_tear, takes=2, target=-30.5, cls='tick-class rasp (paper tear)',
       kind=('servo', 0.15), fade_ms=15, hit_ms=3, ref=SPYGLASS, pitch=[0.95, 1.05],
       subtitle_en='Receipt torn off', subtitle_pt='Recibo arrancado',
       clip_key='tear key 4: sequencer.tear|0.5|1.0 -> sequencer.tear|1.0|1.0',
       play='Sequencer clip tear, key 4 (the end of the flick): the fibres part from 3 ms over 60-85 ms, the strip flutters to about 0.2 s; the paper particle at 6.' + KEY_NOTE),
    ev(id='sequencer.bell', file='sequencer/bell', fn=sequencer_bell, takes=1, target=-29.0, cls='blip (till bell)',
       kind='stinger', fade_ms=40, hit_ms=3, ref=XYLO, pitch=[1.0, 1.0], pitch_per_play=[1.0, 1.12], attenuation=12,
       subtitle_en='Register bell rings', subtitle_pt='Campainha da caixa toca',
       clip_key='ding and ding_open key 1: sequencer.bell|0.7|1.0; ding2 key 7 and ding2_open key 8: |0.7|1.12 -> volume 1.0, same pitches',
       play='Sequencer clips ding, ding_open (key 1, pitch 1.0) and ding2, ding2_open (key 1 at 1.0, then key 7 or 8 at 1.12, a full house): one G6 bell from 3 ms, about 0.9 s. The drawer\'s machine.drawer_stop and drawer_shut are the kit\'s.' + KEY_NOTE),
    # ---- the Injection Rig
    ev(id='rig.unlatch', file='rig/unlatch', fn=rig_unlatch, takes=2, target=-28.0, cls='latch release (with the lift)',
       kind=('servo', 0.30), fade_ms=20, hit_ms=3, ref=IRON_TD_OPEN, pitch=[0.97, 1.03],
       subtitle_en='Syringe unlatches', subtitle_pt='Seringa destrava',
       clip_key='inject key 0: rig.unlatch|0.5|1.0 -> rig.unlatch|1.0|1.0',
       play='Rig clip inject, key 0: the latch at 3 ms, the needle sliding out of the sheath from 100 ms (tick 2), clear at 280-300 ms.' + KEY_NOTE),
    ev(id='rig.servo', file='rig/servo', fn=rig_servo, takes=3, target=-27.0, cls='servo (arm swing)',
       kind=('servo', 0.55), fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[1.0, 1.0], pitch_per_play=[1.0, 0.9],
       subtitle_en='Injection arm swings', subtitle_pt='Braço da injeção gira',
       clip_key='inject key 7: rig.servo|0.4|1.0; accepted/rejected/mutated key 4 and cancel key 2: |0.4|0.9 -> volume 1.0, same pitches',
       play='Rig clip inject, key 7 (pitch 1.0, the swing out: wind-up 7-9, out 9-17) and the tails\' key 4 and cancel\'s key 2 (pitch 0.9, home): the relay at 3 ms, the motor to the stop at 500 ms (556 ms at 0.9), the settle tick at 620 ms. A random take each time.' + KEY_NOTE),
    ev(id='rig.prime', file='rig/prime', fn=rig_prime, takes=2, target=-29.0, cls='latch (cocking)',
       kind='latch', fade_ms=12, hit_ms=3, ref=IRON_TD_OPEN, pitch=[1.0, 1.0], pitch_per_play=[1.1],
       subtitle_en=None, subtitle_pt=None,
       clip_key='inject key 25: rig.prime|0.4|1.1 -> rig.prime|1.0|1.1',
       play='Rig clip inject, key 25 at pitch 1.1 (the jab\'s wind-up): the catch at 3 ms, the squeak, the hold at about 100 ms (tick 27). No subtitle (part of the jab; rig.hiss carries it).' + KEY_NOTE),
    ev(id='rig.plunge', file='rig/plunge', fn=rig_plunge, takes=2, target=-27.5, cls='latch (plunger home, short)',
       kind='click', fade_ms=10, hit_ms=3, ref=IRON_TD_OPEN, pitch=[0.97, 1.03],
       subtitle_en='Plunger driven home', subtitle_pt='Êmbolo vai até o fim',
       clip_key='accepted and mutated key 2: rig.plunge|0.4|1.0 -> rig.plunge|1.0|1.0',
       play='Rig tails accepted and mutated, key 2 (inject tick 32): the thumb on the flange at 3 ms, the squirt, the bounce a tick on.' + KEY_NOTE),
    ev(id='rig.ding', file='rig/ding', fn=rig_ding, takes=1, target=-29.5, cls='blip (pump bell)',
       kind='stinger', fade_ms=40, hit_ms=3, ref=XYLO, pitch=[1.0, 1.0], pitch_per_play=[1.2], attenuation=12,
       subtitle_en='Rig dings: accepted', subtitle_pt='Injetora toca: aceito',
       clip_key='accepted key 3: rig.ding|0.5|1.2 -> rig.ding|1.0|1.2',
       play='Rig tail accepted, key 3 (inject tick 33), pitch 1.2 (the E6 bell heard about G6): the solenoid at 3 ms, the bell at 5 ms, about 0.6 s.' + KEY_NOTE),
    ev(id='rig.burp', file='rig/burp', fn=rig_burp, takes=2, target=-26.5, cls='wet event (rubbery burp)',
       kind='latch', fade_ms=15, hit_ms=3, ref=HONEY_PLACE, pitch=[1.0, 1.0], pitch_per_play=[0.8],
       subtitle_en='Serum burps back', subtitle_pt='O soro volta num arroto',
       clip_key='rejected key 1: rig.burp|0.6|0.8 -> rig.burp|1.0|0.8',
       play='Rig tail rejected, key 1 (inject tick 31), pitch 0.8: the plunger\'s thump at 3 ms, the burp over 0.2-0.25 s (0.25-0.31 s at 0.8).' + KEY_NOTE),
    ev(id='rig.counter', file='rig/counter', fn=rig_counter, takes=2, target=-30.5, cls='tick-class run (counter wheels)',
       kind=('servo', 0.34), fade_ms=15, hit_ms=3, ref=SPYGLASS, pitch=[1.0, 1.0], pitch_per_play=[0.9],
       subtitle_en='Pump counter rolls', subtitle_pt='O contador gira',
       clip_key='rejected key 3: rig.counter|0.4|0.9 -> rig.counter|1.0|0.9',
       play='Rig tail rejected, key 3 (inject tick 33), pitch 0.9 (with the counter blockstate rolling): the detents from 3 ms slowing to the clunk at 0.31-0.36 s (0.34-0.40 s at 0.9).' + KEY_NOTE),
    ev(id='rig.sproing', file='rig/sproing', fn=rig_sproing, takes=2, target=-27.0, cls='servo-class twang (coil spring)',
       kind=('servo', 0.45), fade_ms=25, hit_ms=3, ref=PISTON_IN, pitch=[0.97, 1.03],
       subtitle_en='Needle sproings back', subtitle_pt='Agulha salta para trás',
       clip_key='mutated key 3: rig.sproing|0.5|1.0 -> rig.sproing|1.0|1.0',
       play='Rig tail mutated, key 3 (inject tick 33, the needle startling back): the knock and the chirp at 3 ms, the spring\'s wobble dying over about half a second.' + KEY_NOTE),
    ev(id='rig.holster', file='rig/holster', fn=rig_holster, takes=3, target=-26.5, cls='latch (syringe in its sheath)',
       kind='latch', fade_ms=12, hit_ms=3, ref=COPPER_TD_CLOSE, pitch=[0.95, 1.05],
       subtitle_en='Syringe holstered', subtitle_pt='Seringa volta ao coldre',
       clip_key='accepted/rejected/mutated key 15 and cancel key 12: rig.holster|0.5|1.0 -> rig.holster|1.0|1.0',
       play='Rig tails\' key 15 (inject tick 45) and cancel\'s key 12: the seat at 3 ms, the tip 6-11 ms on, the bounce a tick on, a settle tick at 100 ms.' + KEY_NOTE),
    ev(id='rig.hiss', file='rig/hiss', fn=rig_hiss, takes=3, target=-25.0, cls='hiss (the injection)',
       kind=('servo', 0.30), fade_ms=25, hit_ms=3, ref=FIZZ, pitch=[0.95, 1.05],
       subtitle_en='Injection hisses', subtitle_pt='A injeção chia',
       play='Server, at the rig (or at the injected player), on the pending inject\'s tick 30 (the jab lands; with the flinch and the plaster), volume 1.0, random pitch 0.95-1.05, no pre-roll: the needle\'s tk at 3 ms, the hiss decaying over 0.25-0.4 s.'),
    # ---- the Tesla Node
    ev(id='tesla.perk', file='tesla/perk', fn=tesla_perk, takes=2, target=-27.0, cls='servo-class (field charging)',
       kind=('servo', 0.35), fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[0.97, 1.03],
       subtitle_en='Tesla node perks up', subtitle_pt='Nó Tesla se anima',
       clip_key='perk key 0: tesla.perk|0.5|1.0 -> tesla.perk|1.0|1.0',
       play='Tesla clip perk, key 0: the relay at 3 ms, the rising bwip with the ring\'s lift to 350 ms (tick 7), let go in 40 ms.' + KEY_NOTE),
    ev(id='tesla.look', file='tesla/look', fn=tesla_look, takes=2, target=-31.0, cls='servo-class rub (ball on the rim, soft)',
       kind=('servo', 1.25), fade_ms=25, hit_ms=3, ref=SPYGLASS, pitch=[0.97, 1.03], subtitle_en=None, subtitle_pt=None,
       clip_key='perk key 7: tesla.look|0.3|1.0 -> tesla.look|1.0|1.0',
       play='Tesla clip perk, key 7 (the code\'s look-round sweep runs ticks 6-32): the rub follows the sweep\'s speed, turns at 275 and 925 ms, eases to rest by 1.35 s. No subtitle (part of the perk).' + KEY_NOTE),
    ev(id='tesla.settle', file='tesla/settle', fn=tesla_settle, takes=2, target=-29.5, cls='tick-class (ring caught by its field)',
       kind='latch', fade_ms=12, hit_ms=3, ref=WOOD_TD_OPEN, pitch=[0.97, 1.03],
       subtitle_en='Tesla node settles', subtitle_pt='Nó Tesla se acomoda',
       clip_key='perk key 31: tesla.settle|0.3|1.0 -> tesla.settle|1.0|1.0',
       play='Tesla clip perk, key 31 (the ring back on its field): the thum at 3 ms, the bounce caught again at 100 ms (tick 33).' + KEY_NOTE),
    ev(id='tesla.swing', file='tesla/swing', fn=tesla_swing, takes=2, target=-30.0, cls='servo-class rub (ball swinging round)',
       kind=('servo', 0.70), fade_ms=25, hit_ms=3, ref=SPYGLASS, pitch=[0.97, 1.03], subtitle_en=None, subtitle_pt=None,
       clip_key='link key 0: tesla.swing|0.4|1.0 -> tesla.swing|1.0|1.0',
       play='Tesla clip link, key 0 (the code\'s aim spring swings the ball, ticks 0-15): the rub at the spring\'s speed, a tick where it turns back, a settle tick. No subtitle (tesla.link_zap carries it).' + KEY_NOTE),
    ev(id='tesla.link_zap', file='tesla/link_zap', fn=tesla_link_zap, takes=3, target=-25.0, cls='arc crackle (the link)',
       kind='latch', fade_ms=12, hit_ms=3, ref=VAULT_ACTIVATE, pitch=[0.94, 1.06], attenuation=12,
       subtitle_en='Tesla arc links', subtitle_pt='Arco Tesla se conecta',
       clip_key='link key 16: tesla.link_zap|0.6|1.0 -> tesla.link_zap|1.0|1.0',
       play='Tesla clip link, key 16 (start drawing the arc on this tick): the snap at 3 ms, the crackle and buzz 130-200 ms, the ring\'s jump caught at 53 ms (tick 17).' + KEY_NOTE),
    ev(id='tesla.unlink', file='tesla/unlink', fn=tesla_unlink, takes=2, target=-27.0, cls='servo-class (arc letting go)',
       kind='latch', fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[0.97, 1.03],
       subtitle_en='Tesla link breaks', subtitle_pt='Ligação Tesla se rompe',
       clip_key='unlink key 0: tesla.unlink|0.4|1.0 -> tesla.unlink|1.0|1.0',
       play='Tesla clip unlink, key 0: the snap at 3 ms, the falling pew over 0.22-0.26 s, the relay at 30 ms.' + KEY_NOTE),
    ev(id='tesla.wind_down', file='tesla/wind_down', fn=tesla_wind_down, takes=2, target=-27.0, cls='servo (hum running down)',
       kind=('servo', 0.80), fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[0.97, 1.03], attenuation=12,
       subtitle_en='Tesla node winds down', subtitle_pt='Nó Tesla desacelera',
       clip_key='stop key 0: tesla.wind_down|0.4|1.0 -> tesla.wind_down|1.0|1.0',
       play='Tesla clip stop, key 0 (sending ends; stop tesla.hum at the same moment): the relay at 3 ms, the hum running down over about 0.8 s, the ring\'s fall caught at 240-250 ms (tick 5) and its bounce at 400 ms (tick 8).' + KEY_NOTE),
    ev(id='tesla.hum', file='tesla/hum', fn=tesla_hum, takes=1, target=-29.0, cls='hum/motor loop (the coil sending), at full rate',
       kind='loop', fade_ms=0, hit_ms=None, ref=BEACON_AMBIENT, finish=loop_finish, loop=True, attenuation=12,
       pitch=[0.85, 1.0], subtitle_en='Tesla node hums', subtitle_pt='Nó Tesla zumbe',
       play='Looped client sound (a tickable sound instance with looping on, created once per node, at locator coil) while `sending`: volume = rate / 4 (0 to 1), pitch = 0.85 + 0.15 x rate / 4 (the brown-out\'s send_low is about volume 0.4, pitch 0.9), both chased (0.2 a tick, pitch at most 0.05 a tick); start at volume 0, stop when the volume falls under 0.01 (tesla.wind_down covers the end). Register with "stream": false. Seamless 2.0 s file (one turn of the ring at full rate), no fades in it.'),
    # ---- the Charging Dock
    ev(id='dock.clack', file='dock/clack', fn=dock_clack, takes=3, target=-24.5, cls='slam (knife switch into its jaws)',
       kind='latch', fade_ms=12, hit_ms=3, ref=IRON_TD_CLOSE, pitch=[0.95, 1.05], attenuation=12,
       subtitle_en='Knife switch closes', subtitle_pt='Chave-faca fecha',
       clip_key='occupy, topup, occupy_low key 9; brownout_out key 6; tap_on key 7: dock.clack|0.6|1.0 -> |1.0|1.0; tap_low key 3: |0.4|1.0 (keep 0.4: it will not stay)',
       play='Dock clips occupy, topup and occupy_low (key 9), brownout_out (6), tap_on (7) and tap_low (3, at 0.4): the clack at 3 ms, the wipe, the blade seated again at about 150 ms (tick 12).' + KEY_NOTE),
    ev(id='dock.fall', file='dock/fall', fn=dock_fall, takes=3, target=-26.5, cls='latch (switch onto its open stop)',
       kind='latch', fade_ms=12, hit_ms=3, ref=COPPER_TD_CLOSE, pitch=[0.95, 1.05],
       subtitle_en='Knife switch falls open', subtitle_pt='Chave-faca cai aberta',
       clip_key='vacate and full key 8, vacate_low key 6, tap key 7: dock.fall|0.4|1.0 (dock_anim.py keys these as dock.thunk; rename to dock.fall) -> dock.fall|1.0|1.0',
       play='Dock clips vacate and full (key 8), vacate_low (6) and tap (7): the knock on the stop at 3 ms, the blade\'s ring, the bounce down again at about 200 ms. dock_anim.py currently names this event dock.thunk.' + KEY_NOTE),
    ev(id='dock.ding', file='dock/ding', fn=dock_ding, takes=1, target=-29.5, cls='blip (scale bell)',
       kind='stinger', fade_ms=40, hit_ms=3, ref=XYLO, pitch=[1.0, 1.0], attenuation=12,
       subtitle_en='Dock dings: full', subtitle_pt='Base toca: carga completa',
       clip_key='full key 10: dock.ding|0.6|1.0 -> dock.ding|1.0|1.0',
       play='Dock clip full, key 10 (the switch tripped open, the jar full): one C6 bell from 3 ms, about 1.1 s, fixed pitch.' + KEY_NOTE),
    ev(id='dock.fizzle', file='dock/fizzle', fn=dock_fizzle, takes=2, target=-27.0, cls='servo-class (arc sputtering out)',
       kind=('servo', 0.80), fade_ms=25, hit_ms=3, ref=PISTON_IN, pitch=[0.97, 1.03],
       subtitle_en='Dock browns out', subtitle_pt='Base perde a força',
       clip_key='brownout key 0, occupy_low key 12: dock.fizzle|0.4|1.0 -> dock.fizzle|1.0|1.0',
       play='Dock clips brownout (key 0) and occupy_low (key 12): the snap at 3 ms, the sputtering buzz dying over 0.7 s, the blade\'s scrape 0.25-0.65 s, the smoke\'s puff at 400 ms (tick 8).' + KEY_NOTE),
    ev(id='dock.meter_tick', file='dock/meter_tick', fn=dock_meter_tick, takes=3, target=-32.0, cls='tick (soft, repeating)',
       kind='click', fade_ms=8, hit_ms=3, ref=CRAFTER, pitch=[1.0, 1.0], pitch_per_play=[1.0, 1.1],
       subtitle_en='Charge meter ticks', subtitle_pt='Medidor de carga tiquetaqueia',
       clip_key='charging keys 10 and 30: dock.meter_tick|0.2|1.0 and |0.2|1.1 (dock_anim.py keys these as dock.tick; rename to dock.meter_tick) -> volume 1.0, same pitches',
       play='Dock clip charging (a 40-tick loop while charging), keys 10 and 30 at pitch 1.0 and 1.1: one soft tick at 3 ms, gone in under 50 ms. dock_anim.py currently names this event dock.tick.' + KEY_NOTE),
    ev(id='dock.spark', file='dock/spark', fn=dock_spark, takes=3, target=-30.5, cls='tick (small electric snap)',
       kind='click', fade_ms=8, hit_ms=3, ref=SPYGLASS, pitch=[0.94, 1.06], subtitle_en=None, subtitle_pt=None,
       play='Client, at locator jaws, with the minecraft:electric_spark particle the clips emit (occupy and topup key 10, charging keys 0 and 20, tap_on keys 3 and 8), volume 1.0, random pitch 0.94-1.06: the snap at 3 ms, gone in about 60 ms. No subtitle (the clack and the meter carry the charging). The clips key the particle only; play this beside it, or add it to the clips as dock.spark|1.0|1.0.'),
]

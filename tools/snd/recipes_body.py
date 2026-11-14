"""Bodies and the jar's glass: the Surgical Saw popping a creature's head off, a jar seating on (and leaving) a
creature's neck, the neural handshake's notes and its miss, a headless body powering down and up, the Suture Kit's
stitch, the syringe drawing, and the jar's glass cracking and dripping as its integrity falls.

Built from the house parts: the servo whine (the motor model, here at twice the servo's pitch), ratchet teeth, a
Minnaert bubble for every wet sound (a decaying sine whose pitch rises as it closes), struck glass modes, filtered-noise
air, the collar camera's soft-square beep, and the house voice (the FM 'vibraphone', carrier:modulator 1:3.5) for the
one handshake note, which the code plays at four pitches for the company motif C5 E5 G5 C6.

The head comes off like a champagne cork, without gore: the code layers vanilla `entity.sheep.shear` on top for the
cut itself, so nothing here snips. Times inside a file are seconds; one tick is 50 ms. A file whose hit is not at its
start says so in `hit_ms`, and `preroll_ticks` is how many ticks before the contact it must start. Takes differ by
construction (per-take tables of frequencies, decays, lengths and layer balance), not by luck of the seed.
"""
import numpy as np
from nsynth import *

T = TICK
C5 = 523.25


def chirp_sine(f_curve, tau, attack=0.001, r=None):
    """A sine on a per-sample frequency curve under an exponential decay `tau` with a linear attack (the curve must be
    7 decay times long)."""
    t = np.arange(len(f_curve)) / SR
    ph0 = r.uniform(0, 2 * np.pi) if r is not None else 0.0
    return np.sin(2 * np.pi * np.cumsum(f_curve) / SR + ph0) * np.exp(-t / tau) * np.clip(t / attack, 0, 1)


def drop(r, f0, tau=0.006, chirp=0.55, splash=0.22):
    """One water drop: a Minnaert bubble with a fast upward chirp (f0 rising by `chirp` with a 6 ms time constant, so
    it is nearly there by 30 ms) and a tiny splash (2-8 kHz noise, 3 ms) under its first moment."""
    t = tt(7 * max(tau, 0.003) + 0.002)
    f = f0 * (1 + chirp * (1 - np.exp(-t / 0.006)))
    b = norm(chirp_sine(f, tau, 0.0008, r))
    sp = norm(noise_burst(2000, 8000, 0.003, r, attack=0.0004))
    return mix(b, splash * sp)


def glass_tick(r, f, level=1.0, tau=0.0025):
    """One tiny tick of glass giving way: a 3-6 kHz noise tick (1-2 ms) and one short mode at f (decay `tau`)."""
    k = mix(norm(noise_burst(3000, 6000, 0.0012, r, attack=0.0002)),
            0.7 * norm(modal(f, [1, 1.47], [1, 0.4], [tau, tau * 0.6], r, 0.02, attack=0.0002)))
    return level * norm(k)


def soft_env(dur, rise, tau):
    """A soft-edged gate: a raised-cosine rise `rise` long, held to `dur`, then an exponential release `tau` (generated
    7 release times long, so it never stops on a step)."""
    t = tt(dur + 7 * tau)
    e = np.where(t < dur, 1.0, np.exp(-(t - dur) / tau))
    return e * np.where(t < rise, 0.5 - 0.5 * np.cos(np.pi * np.clip(t / rise, 0, 1)), 1.0)


def soft_square(f, dur, r, h3=0.12, h5=0.04, rise=0.005, tau=0.006):
    """The collar camera's beep: a sine with a little 3rd and 5th harmonic (a square with its corners filed off),
    a 5 ms rise, held, a 6 ms exponential release."""
    e = soft_env(dur, rise, tau)
    t = np.arange(len(e)) / SR
    ph = 2 * np.pi * f * t + r.uniform(0, 2 * np.pi)
    return (np.sin(ph) + h3 * np.sin(3 * ph) + h5 * np.sin(5 * ph)) * e


# ---------------------------------------------------------------- the cork

CORK_TAKES = [dict(m=1.00, pop=(900.0, 300.0), puff=0.30, whine=0.30),
              dict(m=1.08, pop=(960.0, 320.0), puff=0.42, whine=0.24),
              dict(m=0.93, pop=(860.0, 280.0), puff=0.22, whine=0.36)]
POP_AT = 3 * T  # the pop lands exactly 3 ticks in


def cork(take, r):
    """The saw takes a head off like a champagne cork. A short servo whine-up from 3 ms to 150 ms (the house servo
    voice at twice its pitch: a soft relay click at 3 ms, then whine '320 + 1040 s' Hz, s rising as u squared, grit and
    air), cut short by the pop at
    exactly 150 ms (3 ticks): a sine falling 900 -> 300 Hz in about 15 ms (glide 5 ms, decay 12 ms) with a short air
    puff (700-5000 Hz noise, 10 ms) and a tiny high-passed strike for the cork leaving the neck. The whine lets go in
    10 ms after the pop. No snip: the code layers the vanilla shear on top."""
    p = CORK_TAKES[take - 1]
    m = p['m']
    dur = 0.36
    b = buf(dur)
    t = tt(dur)
    u = np.clip((t - 0.003) / (POP_AT - 0.003), 0, 1)
    s = np.where(t < POP_AT, u ** 2, np.exp(-(t - POP_AT) / 0.010)) * (t > 0.003)
    b += p['whine'] * motor(t, s, r, 320 * m, 1040 * m, grit=0.42, air=0.14, whine=0.36)
    place(b, relay_click(r, 3000 * m), 0.003, 0.12)
    f0, f1 = p['pop']
    place(b, sweep_sine(f0, f1 - 20, 0.012, 0.005, attack=0.0006), POP_AT, 1.0)
    place(b, norm(noise_burst(700, 5000, 0.010, r, attack=0.0006)), POP_AT, p['puff'])
    place(b, strike(r, 2500), POP_AT, 0.25)
    return lowpass(b, 9000)


# ---------------------------------------------------------------- the jar on a neck

BAYONET_TAKES = [dict(m=1.00, seal=0.090, lp=(400, 1600)), dict(m=1.07, seal=0.115, lp=(450, 1900)),
                 dict(m=0.93, seal=0.075, lp=(350, 1400))]


def fwup(r, length, lo, hi):
    """The suction seal: low-passed air whose cutoff moves from lo to hi Hz under a sin-squared swell `length` long,
    a faint low sine rising 120 -> 240 Hz under it (the seal pulling in)."""
    d = length + 0.05
    t = tt(d)
    e = np.where(t < length, np.sin(np.pi * np.clip(t / length, 0, 1)) ** 2, 0.0)
    cut = lo + (hi - lo) * np.clip(t / length, 0, 1)
    air = sweep_bandpass(noise(d, r), np.full(len(t), 60.0), cut) * e
    low = chirp_sine(120 + 120 * np.clip(t / length, 0, 1), 10.0, 0.004, r) * e
    return mix(norm(air), 0.25 * low)


def bayonet(take, r):
    """A jar seats on a creature's neck: a bayonet twist (two ratchet teeth 50 ms apart, the first at 3 ms, the second
    2 % higher), then the soft suction seal 'fwup' 15 ms after the second tooth."""
    p = BAYONET_TAKES[take - 1]
    m = p['m']
    b = buf(0.36)
    place(b, tooth(r, 2600 * m), 0.003, 1.0)
    place(b, tooth(r, 2600 * m * 1.02), 0.053, 0.85)
    place(b, fwup(r, p['seal'], *p['lp']), 0.068, 0.55)
    return b


UNSEAT_TAKES = [dict(m=1.00, pop=(720.0, 380.0), first=0.085, air=0.060),
                dict(m=1.07, pop=(780.0, 400.0), first=0.100, air=0.075),
                dict(m=0.93, pop=(660.0, 350.0), first=0.075, air=0.050)]


def unseat(take, r):
    """The jar lets go of the neck: a small seal pop at 3 ms (a sine falling about 720 -> 380 Hz, decay 12 ms, with a
    low-passed air puff) and the air rushing back in under it (low-passed noise, 50-75 ms), then the two teeth of the
    bayonet turning back, 50 ms apart, the second 2 % lower."""
    p = UNSEAT_TAKES[take - 1]
    m = p['m']
    b = buf(0.36)
    f0, f1 = p['pop']
    place(b, sweep_sine(f0, f1, 0.012, 0.005, attack=0.0006), 0.003, 1.0)
    place(b, norm(lowpass(noise_burst(300, 4000, 0.008, r, attack=0.0008), 2000)), 0.003, 0.45)
    place(b, fwup(r, p['air'], 900, 300), 0.006, 0.35)
    place(b, tooth(r, 2600 * m), p['first'], 0.55)
    place(b, tooth(r, 2600 * m * 0.98), p['first'] + 0.050, 0.45)
    return b


# ---------------------------------------------------------------- the handshake

def note(take, r):
    """One note of the company voice on C5 (the FM 'vibraphone', carrier:modulator 1:3.5), decay 90 ms, the index
    falling 1.9 -> 0.5 over 60 ms so the strike is bright and the ring is nearly a sine. No noise strike, no beating
    partner and nothing above 7 kHz, so it stays clean played at pitch 1.26, 1.5 and 2.0 (E5, G5, C6)."""
    b = buf(0.70)
    place(b, fm_note(C5, 0.090, r, index=(1.9, 0.5), index_tau=0.060, attack=0.002), 0.003)
    return lowpass(b, 7000)


MISS_TAKES = [dict(f=(110.0, 116.0), dur=0.250), dict(f=(104.0, 110.0), dur=0.275)]


def miss(take, r):
    """A missed handshake: a low two-tone buzz, two soft squares (odd harmonics to 4 kHz) at 110 and 116 Hz beating
    6 times a second, 0.25 s with soft edges (an 8 ms raised-cosine rise, an 8 ms exponential release), through a small speaker's band (300-3500 Hz)."""
    p = MISS_TAKES[take - 1]
    e = soft_env(p['dur'] - 0.020, 0.008, 0.008)
    n = len(e) + int(0.02 * SR)
    x = sum(osc_harm(np.full(n, f), 35, r, 1.0, odd_only=True) for f in p['f'])
    x = bandpass(x, 300, 3500)[int(0.02 * SR):]  # the filter's start-up is dropped, not heard
    b = buf(p['dur'] + 0.08)
    place(b, norm(x * e), 0.003)
    return b


# ---------------------------------------------------------------- the body empties and fills

POWER_TAKES = [dict(beep=0.140, step=0.200), dict(beep=0.160, step=0.225)]


def power(take, r, up):
    """The collar camera's three-note beep, tiny and soft: 1.2, 0.9, 0.6 kHz going down (the body empties) or the same
    rising (it fills), each a soft square `beep` long, `step` apart, from 3 ms. Going down, the last beep is softer;
    going up, the last is the loudest."""
    p = POWER_TAKES[take - 1]
    fs = [600.0, 900.0, 1200.0] if up else [1200.0, 900.0, 600.0]
    lv = [0.80, 0.90, 1.0] if up else [1.0, 0.90, 0.75]
    b = buf(2 * p['step'] + p['beep'] + 0.12)
    for k, (f, l) in enumerate(zip(fs, lv)):
        place(b, l * soft_square(f, p['beep'], r), 0.003 + k * p['step'])
    return b


# ---------------------------------------------------------------- the Suture Kit and the syringe

SNICK_TAKES = [dict(f=2100.0, tss=0.006, gap=0.018), dict(f=2400.0, tss=0.008, gap=0.024),
               dict(f=1850.0, tss=0.005, gap=0.014), dict(f=2700.0, tss=0.007, gap=0.030)]


def snick(take, r):
    """One stitch: the thread drawn through (a soft 3-9 kHz 'tss' with a 3 ms attack), then a tiny pluck as it pulls
    tight `gap` later (one short mode near 2 kHz, decay 8 ms, a 4 kHz-high-passed tick on it)."""
    p = SNICK_TAKES[take - 1]
    b = buf(0.14)
    place(b, norm(noise_burst(3000, 9000, p['tss'], r, attack=0.003)), 0.003, 0.55)
    pl = mix(norm(modal(p['f'], [1, 2.01, 3.03], [1, 0.35, 0.15], [0.008, 0.005, 0.003], r, 0.02)),
             0.35 * strike(r, 4000))
    place(b, norm(pl), 0.003 + p['gap'], 0.80)
    return b


PLIP_TAKES = [dict(f=950.0, tau=0.010), dict(f=1100.0, tau=0.009), dict(f=1260.0, tau=0.008)]


def plip(take, r):
    """A syringe drawing: one tiny wet 'plip', a Minnaert bubble (a sine at 950-1260 Hz rising 15 % over 25 ms, decay
    8-10 ms), and the faintest plunger click under its start (a 3 kHz-high-passed tick, -18 dB)."""
    p = PLIP_TAKES[take - 1]
    b = buf(0.12)
    t = tt(7 * p['tau'] + 0.002)
    f = p['f'] * (1 + 0.15 * (1 - np.exp(-t / 0.008)))
    place(b, norm(chirp_sine(f, p['tau'], 0.001, r)), 0.003, 1.0)
    place(b, strike(r, 3000), 0.003, 0.125)
    return b


# ---------------------------------------------------------------- the jar's glass

def crack_times(n, span, ratio):
    """n tick times from 0 to `span` whose spacing shrinks by `ratio` each time (the crack speeding up)."""
    gaps = ratio ** np.arange(n - 1)
    gaps = gaps / gaps.sum() * span
    return np.concatenate([[0.0], np.cumsum(gaps)])


CRACK_TAKES = [dict(m=1.00, ratio=0.80, ring=2400.0, d=1.0), dict(m=1.12, ratio=0.74, ring=2650.0, d=0.8),
               dict(m=0.86, ratio=0.85, ring=2180.0, d=1.3)]


def crack(take, r):
    """The jar's glass crosses half integrity: a glass tick at 3 ms (the jar's own modes, f0 2.2-2.65 kHz, damped by
    the saline as in the jar's tap, ringing 30-50 ms, under a strike), then the crack: 8 tiny decaying ticks at 3-6 kHz over 120 ms,
    each gap 15-26 % shorter than the last, like ice cracking."""
    p = CRACK_TAKES[take - 1]
    m = p['m']
    b = buf(0.40)
    place(b, norm(glass_modes(r, p['ring'], decays=tuple(x * p['d'] for x in (0.040, 0.026, 0.014, 0.009)))), 0.003, 0.55)
    place(b, strike(r, 4000), 0.003, 0.40)
    for k, at in enumerate(crack_times(8, 0.120, p['ratio'])):
        lvl = r.uniform(0.55, 1.0) * (0.85 + 0.15 * k / 7)
        place(b, glass_tick(r, r.uniform(3000, 6000) * m, lvl, tau=r.uniform(0.002, 0.004)), 0.012 + at)
    return highpass(b, 600, 4)


CRACK_LONG_TAKES = [dict(m=1.00, ratio=0.90, drip=1100.0, ring=2400.0, wait=0.040),
                    dict(m=1.14, ratio=0.86, drip=1250.0, ring=2650.0, wait=0.080)]


def crack_long(take, r):
    """The glass crosses a quarter integrity: a glass tick at 3 ms, then a longer crack, 16 ticks at 3-6 kHz over
    300 ms, each gap 10-14 % shorter than the last and the ticks growing a little, then one water drop 40-80 ms after
    the last tick (the jar's first leak)."""
    p = CRACK_LONG_TAKES[take - 1]
    m = p['m']
    b = buf(0.48)
    place(b, norm(glass_modes(r, p['ring'], decays=(0.030, 0.020, 0.012, 0.008))), 0.003, 0.55)
    place(b, strike(r, 4000), 0.003, 0.40)
    times = crack_times(16, 0.300, p['ratio'])
    for k, at in enumerate(times):
        lvl = r.uniform(0.55, 1.0) * (0.75 + 0.25 * k / 15)
        place(b, glass_tick(r, r.uniform(3000, 6000) * m, lvl, tau=r.uniform(0.002, 0.004)), 0.012 + at)
    place(b, drop(r, p['drip']), 0.012 + times[-1] + p['wait'], 0.45)
    return highpass(b, 400, 4)


DRIP_TAKES = [dict(f=1100.0, tau=0.006), dict(f=1000.0, tau=0.007), dict(f=1220.0, tau=0.005)]


def drip(take, r):
    """A slow drip from a cracked jar: one water drop at 3 ms, a sine at 1.0-1.22 kHz chirping fast upward (about
    30 ms long, decay 5-7 ms) over a tiny splash. Its spectral neighbour is vanilla's dripstone drip into a cauldron,
    which the game plays about 6 LU under the idle class even at full volume; this file follows the class (-36) so
    the drip is heard beside the jar's other small sounds, not that drip's loudness."""
    p = DRIP_TAKES[take - 1]
    b = buf(0.12)
    place(b, drop(r, p['f'], p['tau']), 0.003)
    return b


# ---------------------------------------------------------------- the event list

TRAPDOOR_CLOSE = dict(event='block.iron_trapdoor.close', file='block__iron_trapdoor__close1.ogg', vol=0.9, pitch=0.95)
PISTON_IN = dict(event='block.piston.contract', file='tile__piston__in.ogg', vol=0.5, pitch=0.675)
XYLO = dict(event='block.note_block.xylophone', file='note__xylobone.ogg', vol=1.0, pitch=1.0)
CRAFTER = dict(event='block.crafter.craft', file='block__crafter__craft.ogg', vol=1.0, pitch=1.0)
SPYGLASS = dict(event='item.spyglass.use', file='item__spyglass__use.ogg', vol=1.0, pitch=1.0)
BUBBLE = dict(event='block.bubble_column.bubble_pop', file='block__bubble_column__bubble1.ogg', vol=1.0, pitch=1.0)
AMETHYST_BREAK = dict(event='block.amethyst_cluster.break', file='block__amethyst_cluster__break1.ogg', vol=1.0, pitch=0.8)
GLASS_BREAK = dict(event='block.glass.break', file='random__glass2.ogg', vol=1.0, pitch=0.8)
DRIPSTONE = dict(event='block.pointed_dripstone.drip_water_into_cauldron', file='block__pointed_dripstone__drip_water_cauldron1.ogg',
                 vol=1.0, pitch=1.0)

BASE = dict(fixed_range=False, volume=1.0, muffled=False)


def ev(**kw):
    d = dict(BASE)
    d.update(kw)
    return d


# kind: a (name, seconds) pair gives the heard-length range seconds - 0.02 to seconds + 0.30 in measure.py
SOUNDS = [
    ev(id='body.cork', file='body/cork', fn=cork, takes=3, target=-22.0, cls='clamp (head pops off)', kind='latch',
       fade_ms=15, hit_ms=150, preroll_ticks=3, ref=TRAPDOOR_CLOSE, pitch=[1.0, 1.0], attenuation=16,
       category='NEUTRAL', subtitle_en='Head pops off', subtitle_pt='Cabeça salta fora',
       play='Server, at the creature, when the Surgical Saw takes its head. The pop is at 150 ms = 3 ticks: start the sound 3 ticks before the head comes off (or detach it 3 ticks after starting the sound). Fixed pitch so the pop stays on the tick; the three takes give the variety. Layer vanilla entity.sheep.shear in code on the detach tick for the cut; this file has no snip.'),
    ev(id='body.bayonet', file='body/bayonet', fn=bayonet, takes=3, target=-27.0, cls='servo (bayonet and seal)',
       kind='latch', fade_ms=15, hit_ms=3, ref=PISTON_IN, pitch=[0.95, 1.05], attenuation=12, category='NEUTRAL',
       subtitle_en='Jar locks on', subtitle_pt='Pote se encaixa',
       play='Server, at the creature\'s neck, on the tick the jar seats. The first tooth is at 3 ms; the seal follows by 70 ms.'),
    ev(id='body.unseat', file='body/unseat', fn=unseat, takes=3, target=-27.0, cls='servo (seal and bayonet)',
       kind='latch', fade_ms=15, hit_ms=3, ref=PISTON_IN, pitch=[0.95, 1.05], attenuation=12, category='NEUTRAL',
       subtitle_en='Jar unlocks', subtitle_pt='Pote se solta',
       play='Server, at the creature\'s neck, on the tick the jar comes off. The seal pop is at 3 ms, the teeth at 45 and 95 ms.'),
    ev(id='body.note', file='body/note', fn=note, takes=1, target=-28.0, cls='done chime (one note)',
       kind=('note', 0.37), fade_ms=50, hit_ms=3, ref=XYLO, pitch=[1.0, 1.0],
       pitch_per_play=[1.0, 1.2599, 1.4983, 2.0], attenuation=8, category='PLAYERS',
       subtitle_en='Handshake note', subtitle_pt='Nota do aperto',
       play='One note per handshake step at pitch 1.0, 1.26, 1.5, 2.0 (C5, E5, G5, C6). No random pitch. The strike is at 3 ms; heard about 0.42 s at pitch 1.0 and half that at 2.0.'),
    ev(id='body.miss', file='body/miss', fn=miss, takes=2, target=-27.0, cls='servo (buzz)', kind='latch',
       fade_ms=15, hit_ms=3, ref=PISTON_IN, pitch=[0.95, 1.05], attenuation=8, category='PLAYERS',
       subtitle_en='Handshake misses', subtitle_pt='Aperto falha',
       play='When a handshake ring is missed, at the player. The buzz starts at 3 ms (20 ms soft edge) and lasts 0.25 s.'),
    ev(id='body.power_down', file='body/power_down', fn=lambda take, r: power(take, r, False), takes=2, target=-31.0,
       cls='blip', kind=('beeps', 0.58), fade_ms=10, hit_ms=3, ref=CRAFTER, pitch=[1.0, 1.0], attenuation=8,
       category='NEUTRAL', subtitle_en='Body powers down', subtitle_pt='Corpo desliga',
       play='At the headless body, on the tick its owner leaves it. Three beeps 1.2, 0.9, 0.6 kHz from 3 ms. Fixed pitch (the notes are the message); two takes.'),
    ev(id='body.power_up', file='body/power_up', fn=lambda take, r: power(take, r, True), takes=2, target=-31.0,
       cls='blip', kind=('beeps', 0.58), fade_ms=10, hit_ms=3, ref=CRAFTER, pitch=[1.0, 1.0], attenuation=8,
       category='NEUTRAL', subtitle_en='Body powers up', subtitle_pt='Corpo liga',
       play='At the body, on the tick a head takes it over. Three beeps 0.6, 0.9, 1.2 kHz from 3 ms. Fixed pitch; two takes.'),
    ev(id='body.snick', file='body/snick', fn=snick, takes=4, target=-31.0, cls='tick (stitch)', kind='click',
       fade_ms=8, hit_ms=3, ref=SPYGLASS, pitch=[0.94, 1.06], attenuation=6, category='PLAYERS',
       subtitle_en=None, subtitle_pt=None,
       play='One per stitch while the Suture Kit works, at the patient. Random pitch 0.94-1.06 and volume 0.9-1.0. No subtitle: small life.'),
    ev(id='body.plip', file='body/plip', fn=plip, takes=3, target=-31.0, cls='blip (wet)', kind='click',
       fade_ms=8, hit_ms=3, ref=BUBBLE, pitch=[0.94, 1.06], attenuation=6, category='PLAYERS',
       subtitle_en='Syringe draws', subtitle_pt='Seringa aspira',
       play='At the syringe, on each draw. Random pitch 0.94-1.06; debounce 2 ticks.'),
    ev(id='jar.crack', file='jar/crack', fn=crack, takes=3, target=-25.0, cls='hiss class (glass crack)', kind='latch',
       fade_ms=15, hit_ms=3, ref=AMETHYST_BREAK, pitch=[0.95, 1.05], attenuation=12, category='PLAYERS',
       subtitle_en='Jar glass cracks', subtitle_pt='Vidro do pote racha',
       play='At the jar, once, when its glass integrity falls below half. The glass tick is at 3 ms.'),
    ev(id='jar.crack_long', file='jar/crack_long', fn=crack_long, takes=2, target=-21.0, cls='clamp class (glass splits)',
       kind=('crack', 0.38), fade_ms=15, hit_ms=3, ref=GLASS_BREAK, pitch=[0.95, 1.05], attenuation=16,
       category='PLAYERS', subtitle_en='Jar glass splits', subtitle_pt='Vidro do pote trinca',
       play='At the jar, once, when its glass integrity falls below a quarter. The first tick is at 3 ms; the drop lands about 0.35 s in. jar.drip can follow from then on.'),
    ev(id='jar.drip', file='jar/drip', fn=drip, takes=3, target=-36.0, cls='idle machine life (drip)', kind='click',
       fade_ms=8, hit_ms=3, ref=DRIPSTONE, pitch=[0.94, 1.06], attenuation=6, category='PLAYERS',
       subtitle_en='Jar drips', subtitle_pt='Pote pinga',
       play='At a cracked jar, now and then (every 2-6 s at random while cracked below a quarter). Random pitch 0.94-1.06.'),
]

"""The heavy frames beyond the Bulwark: the Colossus and the Executive.

The Colossus is the company's gatehouse: two enamel towers on long legs, heavier and slower than the Bulwark. On Siege
it kneels, digs its knee spades into the ground and becomes a building, and the planning office is told about it with
a polite chime (or refuses the permit with a dry office buzzer). Its parts are the Bulwark's at a larger size: deeper
footfalls that make the ground answer, a long metered kneel, groaning heaves, pneumatic cradles for the shoulder
weapons with heavy latches, a slewing ring for the turrets, and a building's hum while sieged.

The Executive is the board's company car: a dark suit on narrow legs, and expensive things are quiet. Its footfall is
the Colossus's step through felt; its sunroof runs on a small fast electric motor and shuts on a rubber seal; it tugs
its lapels, taps a polished shoe and checks a ticking wristwatch. On boot and on the drive key it plays the premium
jingle, the company motif C E G C in its fullest arrangement on the house voice.

Event ids are `mecha.colossus.<name>` and `mecha.executive.<name>`; the code plays a frame's own event when it exists,
then the Bulwark's, then the shared one. Keyframe sounds hit at 0-5 ms; server contact sounds hit at 3 ms. Takes differ
by construction (per-take tables). The Siege hum is a 3.0 s loop built like the motors: every periodic layer completes
whole cycles (every frequency a multiple of 1/3 Hz), every noise layer is circular, and it is finished by
`finish_loop` (no fades).

Shared parts come from the kit (`nsynth`), the Bulwark (`recipes_bulwark`: the weighted boot, the creak, the hydraulic
stroke), the Hopper (`recipes_mecha`: the loop helpers, the saturation, the references) and the feedback family (the
motif's notes).
"""
import numpy as np
from scipy import signal as _sig
from nsynth import *
from recipes_mecha import circ_noise, finish_loop, soft
from recipes_mecha import PISTON_IN, PISTON_OUT, TRAPDOOR_CLOSE, SPYGLASS, BELL, GOLEM_STEP
from recipes_bulwark import boot, creak, bhydraulic, GOLEM_WALK1, VAULT_FAIL, BOTTLE, BEACON_AMBIENT
from recipes_feedback import C5, E5, G5, C6, G4

T = TICK
C4, E6 = 261.63, 1318.51


# ================================================================ the Colossus

# ---------------------------------------------------------------- the planning office

def planning_chime(take, r):
    """The planning office's chime as Siege begins (siege_in tick 2): a public-address 'ding-dong' on the house voice,
    E5 then C5 330 ms later (the motif's first interval, falling: polite, not pleased), each with a twin 1.5 Hz above
    so it shimmers like a hall chime; the second note rings a little longer."""
    b = buf(2.2)
    place(b, fm_note(E5, 0.18, r, index=(2.2, 0.45), attack=0.003, partner=(1.5, 0.35)), 0.003, 1.0)
    place(b, fm_note(C5, 0.22, r, index=(2.0, 0.45), attack=0.003, partner=(1.5, 0.35)), 0.333, 0.95)
    return lowpass(b, 8000)


BUZZ = [dict(f=120.0, hold=0.30, res=2300.0), dict(f=105.0, hold=0.36, res=2000.0)]


def permit_denied(take, r):
    """The permit refused (siege_refused tick 9): an office door buzzer, dry. The armature hammers its stop at the
    coil's 105-120 Hz (narrow pulses, each ringing a small steel plate at 2.0-2.3 kHz for 2-3 ms) over a little of the
    coil's own hum, driven into a rasp and kept above 900 Hz (a small buzzer has no bass); on at 3 ms, held
    0.30-0.36 s, off in 12 ms. No room, no tail."""
    q = BUZZ[take - 1]
    dur = round(q['hold'] + 0.25, 3)
    n = int(round(dur * SR))
    t = tt(dur)
    ph = 2 * np.pi * q['f'] * t + 0.15 * np.sin(2 * np.pi * 7.0 * t)
    arm = (0.5 + 0.5 * np.cos(ph)) ** 40
    arm = arm - arm.mean()
    ir = modal(q['res'], [1, 2.3, 3.6], [1, 0.5, 0.3], [0.0025, 0.0018, 0.0012], r)
    x = _sig.fftconvolve(arm, ir)[:n]
    x = norm(x) + 0.1 * norm(osc_harm(np.full(n, q['f']), 12, r, 1.0))
    x = lowpass(highpass(np.tanh(2.5 * norm(x)), 900, 4), 7000)
    t0 = t - 0.003
    env = (t0 >= 0) * np.clip(t0 / 0.003, 0, 1) * np.where(t0 < q['hold'], 1.0, np.exp(-(t0 - q['hold']) / 0.012))
    return x * env


# ---------------------------------------------------------------- the kneel, the heave, the cradles

SINK = [dict(m=1.00, bleed=0.30), dict(m=1.08, bleed=0.40), dict(m=0.92, bleed=0.24)]


def hydraulic_sink(take, r):
    """The long kneel (siege_in tick 24: the deck sinks 24-36, locked on 38): the valve opens at 3 ms and the loader's
    hydraulic stroke runs 12 ticks with a low groan (40 Hz, rising only a little: the weight is doing the work), a
    metered bleed hiss whose band falls as the cylinders empty (900-4200 Hz down to 500-2400 Hz), a slow low rumble
    of the weight going down, a soft end-stop at 610 ms (tick 36) and a settle knock at 710 ms (tick 38). The knee pads
    meet the ground at 350 ms, but that is siege_anchor's thud, not this file's."""
    q = SINK[take - 1]
    m = q['m']
    s1 = 0.01 + 12 * T
    b = bhydraulic(take, r, 0.01, s1, groan=(40.0, 0.30), stop_gain=0.35, settle=0.01 + 14 * T, creak_f=470.0)
    dur = len(b) / SR
    t = tt(dur)
    s = speed_curve(t, 0.01, s1)
    u = np.clip((t - 0.01) / (s1 - 0.01), 0, 1)
    bleed = sweep_bandpass(noise(dur, r), (900 - 400 * u) * m, (4200 - 1800 * u) * m) * s ** 0.8
    b += q['bleed'] * norm(bleed) + 0.18 * norm(lowpass(noise(dur, r), 160)) * s
    return b


GROAN = [dict(m=1.00, st=16, f=38.0), dict(m=1.08, st=19, f=42.0)]


def hydraulic_groan(take, r):
    """A heave up under the whole building's weight (build tick 60, between the two stands; also for the release's
    heave): the pressure climbs for 16-19 ticks (the groan rising 38-42 -> 70-78 Hz), the frame creaking low twice as
    it takes the load (300-390 Hz stick-slip), no end-stop, and the relief valve's 'tss' as it holds."""
    q = GROAN[take - 1]
    m = q['m']
    s1 = 0.01 + q['st'] * T
    b = bhydraulic(take, r, 0.01, s1, groan=(q['f'], 0.85), stop_gain=0, curve='in', creak_f=430.0, relief=s1 + 0.03)
    place(b, norm(noise_burst(3000, 10000, 0.004, r)), 0.003, 0.35)
    place(b, creak(r, 0.35, 300 * m, rate=(0.030, 0.055), decay=0.009, rise=0.3), 0.15, 0.10)
    place(b, creak(r, 0.30, 360 * m, rate=(0.028, 0.050), decay=0.008), s1 - 0.35, 0.09)
    return b


HISS = [dict(m=1.00, hold=0.45, tau=0.16), dict(m=1.12, hold=0.58, tau=0.19), dict(m=0.90, hold=0.36, tau=0.14)]


def hydraulic_hiss(take, r):
    """The shoulder cradles unfolding on air (boot 8, build 101 and 289, shutdown 5, siege_in 37): a valve 'tsk' at
    3 ms, the pressure up in 25 ms and easing by a quarter while the cradle swings (0.36-0.58 s), the flow fluttering at
    4.5 Hz as the linkage moves, a band of 600-2600 Hz opening a little with the pressure and a low body under it, then
    a 0.14-0.19 s decay and one last small 'pff'."""
    q = HISS[take - 1]
    m = q['m']
    dur = 1.60
    b = buf(dur)
    t = tt(dur)
    t0 = np.maximum(t - 0.006, 0)
    pr = (t >= 0.006) * np.clip(t0 / 0.025, 0, 1) * np.where(t0 < q['hold'], 1 - 0.25 * t0 / q['hold'],
                                                             0.75 * np.exp(-(t0 - q['hold']) / q['tau']))
    flow = pr * (1 + 0.12 * np.sin(2 * np.pi * 4.5 * t))
    hiss = sweep_bandpass(noise(dur, r), (600 + 200 * pr) * m, (2600 + 900 * pr) * m) * flow
    body = bandpass(noise(dur, r), 250, 700) * flow ** 1.5
    b += 0.80 * norm(hiss) + 0.30 * norm(body)
    place(b, norm(noise_burst(4000, 12000, 0.0015, r)), 0.003, 0.45)
    place(b, norm(noise_burst(1200, 5000, 0.030, r, attack=0.008)), q['hold'] + 0.12, 0.15)
    return b


LATCH = [dict(m=1.00, dk=1.00, pawl=0.042), dict(m=1.10, dk=1.20, pawl=0.052), dict(m=0.95, dk=0.85, pawl=0.036)]


def latch_clunk(take, r):
    """A shoulder cradle locking (boot 17 and 22, build 110 and 115): the latch bolt shooting home at 4 ms (a strike,
    the bolt's iron body at 560 / 900 Hz, a thunk 125 -> 70 Hz, the end of its slide), the pawl dropping in behind it
    36-52 ms later (a tooth), and the cradle's weight settling onto the latch 45 ms after that (a low knock)."""
    q = LATCH[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(0.70)
    h = 0.004
    place(b, strike(r, 1300 * m), h, 0.50)
    place(b, norm(iron_body(r, 560 * m, 900 * m, 0.85 * dk, band_scale=m)), h, 0.65)
    place(b, thunk(r, 125 * m, 70 * m, 0.050, 0.018), h, 0.28)
    place(b, norm(noise_burst(1000 * m, 5000 * m, 0.012, r)), h, 0.30)
    place(b, tooth(r, 2100 * m), q['pawl'], 0.20)
    place(b, mix(thunk(r, 110, 70, 0.030, 0.012), 0.5 * norm(modal(330 * m, [1, 2.756], [1, 0.4], [0.03, 0.02], r))),
          q['pawl'] + 0.045, 0.18)
    return b


TWHIR = [dict(m=1.00, ticks=8, grit=0.35), dict(m=1.08, ticks=9, grit=0.45), dict(m=0.92, ticks=10, grit=0.30)]


def turret_whir(take, r):
    """The shoulder weapons swivelling (idle_tic 1 and 19): a small relay click at 3 ms, then a slewing-ring motor
    (whine 260 + 520 s Hz, brush grit, air) over an eased 8-10 tick swing, the ring's gear teeth meshing at up to
    70 teeth a second (a 1.2-4 kHz band gated by the tooth rate), and an electric brake holding at the end (a soft
    tick and a small knock), no clunk: the turret is light against the frame."""
    q = TWHIR[take - 1]
    m = q['m']
    start = 0.006
    stop = start + q['ticks'] * T
    dur = round(stop + 0.35, 3)
    b = buf(dur)
    t = tt(dur)
    s = speed_curve(t, start, stop)
    b += motor(t, s, r, 260 * m, 520 * m, grit=q['grit'], air=0.12, whine=0.30, rotor_div=4)
    gate = (0.5 + 0.5 * np.cos(2 * np.pi * np.cumsum(70 * m * s) / SR)) ** 6
    b += 0.22 * norm(bandpass(noise(dur, r), 1200 * m, 4000 * m) * gate * s)
    place(b, relay_click(r, 2000 * m, release_after=0.02), 0.003, 0.25)
    place(b, mix(norm(modal(1500 * m, [1, 1.6], [1, 0.4], [0.006, 0.004], r)), 0.4 * thunk(r, 200, 150, 0.015, 0.008)),
          stop, 0.18)
    return b


# ---------------------------------------------------------------- the sieged building

def siege_hum(take, r):
    """The sieged Colossus as a building, 3.0 s, a whole number of cycles of every layer: a transformer's mains hum
    (60 Hz with its 120 Hz strongest, harmonics to 360 Hz) and a twin of the 120 Hz group 1/3 Hz above that beats once
    per loop; the deck's slow heave as the cylinders breathe (low circular noise swelling once per loop); ventilation
    air; heat let off a vent twice a loop (a soft 1.5-7 kHz hiss swelling at 2/3 Hz) with the vent flaps fluttering
    at 12 Hz under it; a 1/3 Hz swell. Every frequency is a multiple of 1/3 Hz."""
    L = 3.0
    n = int(L * SR)
    t = np.arange(n) / SR
    mains = sum(a * np.sin(2 * np.pi * 60.0 * k * t + r.uniform(0, 6.28))
                for k, a in ((1, 0.6), (2, 1.0), (3, 0.35), (4, 0.25), (5, 0.12), (6, 0.08)))
    twin = sum(np.sin(2 * np.pi * (120.0 + 1 / 3) * k * t + r.uniform(0, 6.28)) / k for k in (1, 2, 3))
    heave = 0.5 - 0.5 * np.cos(2 * np.pi * t / 3.0)
    breath = circ_noise(n, r, 80, 400) * (0.4 + 0.6 * heave)
    air = circ_noise(n, r, 300, 3000)
    puff = (0.5 + 0.5 * np.cos(2 * np.pi * (2 / 3) * t + 1.0)) ** 6
    vent = circ_noise(n, r, 1500, 7000) * puff
    flutter = circ_noise(n, r, 600, 2200) * (0.5 + 0.5 * np.cos(2 * np.pi * 12.0 * t)) ** 8 * puff
    x = (1.0 * norm(mains) + 0.30 * norm(twin) + 0.22 * norm(breath) + 0.08 * norm(air) + 0.12 * norm(vent)
         + 0.06 * norm(flutter)) * (1 + 0.05 * np.sin(2 * np.pi * t / 3.0))
    return x


# ---------------------------------------------------------------- the ribbon

SNIP = [dict(m=1.00, part=0.022, sl=0.010), dict(m=1.12, part=0.030, sl=0.016), dict(m=0.90, part=0.018, sl=0.007)]


def snip(take, r):
    """Ceremonial scissors cutting the ribbon (build 260 and 263): dry and small. The blades meet at 3 ms (a bright
    strike, the blades' steel modes at 3.8 kHz, 3-10 ms), the edges finish sliding past each other (5-14 kHz, 7-16 ms by take), the
    pivot clicks (2.4 kHz), and the ribbon parts 18-30 ms on with a faint satin tick."""
    q = SNIP[take - 1]
    m = q['m']
    b = buf(0.30)
    h = 0.003
    place(b, strike(r, 4500 * m), h, 0.55)
    place(b, norm(modal(3800 * m, [1, 2.3, 3.1, 4.7], [1, 0.5, 0.35, 0.2], [0.010, 0.007, 0.005, 0.003], r, 0.02)),
          h, 0.45)
    d = round(7 * q['sl'] + 0.002, 3)
    ts = tt(d)
    place(b, norm(bandpass(noise(d, r), 5000 * m, 14000) * np.exp(-ts / q['sl']) * np.clip(ts / 0.001, 0, 1)), h, 0.35)
    place(b, norm(modal(2400 * m, [1, 1.7], [1, 0.4], [0.004, 0.003], r)), h + 0.001, 0.25)
    place(b, norm(noise_burst(2500, 9000, 0.002, r)), q['part'], 0.18)
    return soft(b, 2.5)


# ---------------------------------------------------------------- footfalls and the anchor

QUAKE = [dict(m=1.00, dk=1.00, cr=0.07, rat=0.16, rum=0.16), dict(m=1.08, dk=1.20, cr=0.09, rat=0.19, rum=0.19),
         dict(m=0.85, dk=0.75, cr=0.06, rat=0.13, rum=0.13)]


def step_quake(take, r):
    """The Colossus's footfall (every walk plant, 0.45-0.65 s apart): the Bulwark's weighted boot made bigger (a thunk
    78 -> 30 Hz held 130 ms, the leg's iron body at 210 / 390 Hz, a lower strike), the ground answering with a rumble
    under 110 Hz that swells for 40 ms and rolls away (0.13-0.19 s decays by take), the leg's 'tsh', a low knee creak, and
    something inside the towers rattling once 130-190 ms later (a brass tick and a panel knock)."""
    q = QUAKE[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(1.40)
    place(b, boot(r, m, thump=(78.0, 30.0), tau=0.13, body=0.30, body_f=(210.0, 390.0), strike_hp=700.0, dk=1.2 * dk),
          0.0, 1.0)
    d = 1.2
    t = tt(d)
    place(b, norm(lowpass(noise(d, r), 110) * np.clip(t / 0.04, 0, 1) * np.exp(-t / q['rum'])), 0.003, 0.35)
    place(b, norm(noise_burst(1200, 4500, 0.040, r, attack=0.010)), 0.025, 0.05)
    place(b, creak(r, 0.14, 560 * m, rate=(0.020, 0.040), decay=0.006), q['cr'], 0.05)
    place(b, norm(modal(1700 * m, [1, 2.4, 3.9], [1, 0.45, 0.2], [0.012, 0.008, 0.005], r)), q['rat'], 0.05)
    place(b, norm(modal(620 * m, [1, 2.756], [1, 0.4], [0.020, 0.013], r)), q['rat'] + 0.03, 0.05)
    return soft(b, 2.0)


ANCHOR = [dict(m=1.00, gap=0.012, settle=0.15), dict(m=1.10, gap=0.020, settle=0.18)]


def siege_anchor(take, r):
    """The knee spades biting into the ground (siege:anchor, siege_in tick 31; also siege:unanchor): a heavy thud with
    grit. A low strike, a deep thunk 72 -> 28 Hz, the two spades' iron 12-20 ms apart (300-320 / 540 Hz), a soil thump
    (noise under 400 Hz), debris, and a crunch of grains as the teeth go in (900-6000 Hz specks 2-9 ms apart, thinning
    over 0.3 s); the building settles onto its knees 150-180 ms later (a lower knock and a creak)."""
    q = ANCHOR[take - 1]
    m = q['m']
    b = buf(1.60)
    h = 0.003
    place(b, strike(r, 600), h, 0.35)
    place(b, thunk(r, 72 * m, 28 * m, 0.15, 0.04), h, 0.65)
    for k, at in enumerate((h, h + q['gap'])):
        place(b, norm(iron_body(r, (300 + 20 * k) * m, 540 * m, 1.0, band_scale=0.7 * m)), at, 0.30)
    place(b, norm(lowpass(noise(0.5, r), 400) * exp_env(0.5, 0.06, 0.002)), h, 0.40)
    place(b, norm(lowpass(noise(0.9, r), 2500) * exp_env(0.9, 0.11, 0.002)), h, 0.30)
    at = h
    while at < 0.30:
        g = np.exp(-(at - h) / 0.08) * r.uniform(0.3, 1.0)
        place(b, norm(noise_burst(900, 6000, r.uniform(0.0006, 0.002), r)), at, 0.35 * g)
        at += r.uniform(0.002, 0.009) * (1 + 3 * (at - h))
    place(b, mix(thunk(r, 95, 50, 0.07, 0.02), 0.5 * norm(iron_body(r, 260 * m, 450 * m, 0.8, band_scale=0.7))),
          q['settle'], 0.25)
    place(b, creak(r, 0.30, 380 * m, rate=(0.025, 0.050), decay=0.008), 0.20, 0.06)
    return soft(b, 3.0)


# ================================================================ the Executive

def premium_jingle(take, r):
    """The premium jingle (boot 0, drive_jingle 1, build 230): the motif C5 E5 G5 rising 110 ms apart on the house
    voice with a soft C4 under the first note, then C6 held with E6 a third above, G4 and C4 under it (a full C major
    chord), the top note with a slow vibraphone tremolo, every voice with a twin 2 Hz above; done by 0.45 s, ringing to
    about 1.2 s. The boot jingle's four notes, pleased with themselves."""
    b = buf(2.6)
    for f, at in ((C5, 0.003), (E5, 0.113), (G5, 0.223)):
        place(b, fm_note(f, 0.11, r, index=(1.8, 0.25), attack=0.002, partner=(2.0, 0.4)), at, 0.80)
    place(b, fm_note(C4, 0.12, r, index=(0.6, 0.1), attack=0.004, partner=(2.0, 0.3)), 0.003, 0.40)
    top = 0.333
    place(b, fm_note(C6, 0.19, r, index=(1.6, 0.25), attack=0.002, hold=0.04, partner=(2.0, 0.4), tremolo=(5.5, 0.25)),
          top, 1.0)
    place(b, fm_note(E6, 0.15, r, index=(1.2, 0.2), attack=0.002, partner=(2.0, 0.3)), top, 0.40)
    place(b, fm_note(G4, 0.18, r, index=(0.7, 0.1), attack=0.004, partner=(2.0, 0.3)), top, 0.35)
    place(b, fm_note(C4, 0.22, r, index=(0.5, 0.1), attack=0.005, partner=(2.0, 0.3)), top, 0.45)
    return lowpass(b, 8000)


TUG = [dict(m=1.00, tau=0.050, fib=6), dict(m=1.15, tau=0.065, fib=9), dict(m=0.88, tau=0.040, fib=4)]


def cloth_tug(take, r):
    """A glove tugging a lapel or the tie (boot 25 and 29, drive_jingle 10, build 210): soft cloth, small. A quick pull
    of fabric at 3 ms (300-1900 Hz noise dragged unevenly by the weave, a 6 ms rise and a 40-65 ms decay), the cloth
    coming taut with a soft low 'thp', a few fibre crackles, and the glove's leather creaking very faintly."""
    q = TUG[take - 1]
    m, tau = q['m'], q['tau']
    d = round(7 * tau + 0.01, 3)
    b = buf(d + 0.10)
    t = tt(d)
    drag = np.clip(0.6 + 0.4 * np.abs(lowpass(noise(d, r), 60)) / 0.2, 0, 1.6)
    place(b, norm(bandpass(noise(d, r), 300 * m, 1900 * m) * np.clip(t / 0.006, 0, 1) * np.exp(-t / tau) * drag),
          0.003, 0.70)
    place(b, norm(lowpass(noise(0.12, r), 350) * exp_env(0.12, 0.012, 0.002)), 0.003 + 0.6 * tau, 0.60)
    for _ in range(q['fib']):
        place(b, norm(noise_burst(2000, 7000, 0.0008, r)), 0.006 + r.uniform(0, 1.2 * tau), 0.08 * r.uniform(0.5, 1.0))
    place(b, creak(r, 0.04, 1300 * m, rate=(0.008, 0.014), decay=0.002), 0.010, 0.05)
    return b


SHOE = [dict(m=1.00, taps=(0.003, 0.104)), dict(m=1.10, taps=(0.003, 0.088, 0.176)), dict(m=0.92, taps=(0.003, 0.125))]


def shoe_tap(take, r):
    """A polished shoe tapping, impatient (idle_tic 14): two or three heel taps 88-125 ms apart, each a small strike,
    the hard heel block's knock (780 Hz modes, 3-7 ms), the floor under it (a thunk 230 -> 160 Hz) and a little scuff;
    each tap a little softer than the one before."""
    q = SHOE[take - 1]
    m = q['m']
    b = buf(0.50)
    for k, at in enumerate(q['taps']):
        g = 1.0 - 0.18 * k
        place(b, strike(r, 2500 * m), at, 0.25 * g)
        place(b, norm(modal(780 * m, [1, 2.2, 3.6], [1, 0.45, 0.2], [0.007, 0.005, 0.003], r, 0.02)), at, 0.60 * g)
        place(b, thunk(r, 230 * m, 160 * m, 0.012, 0.006), at, 0.35 * g)
        place(b, norm(noise_burst(400, 2500, 0.003, r)), at, 0.25 * g)
    return b


WATCH = [dict(m=1.00, beats=4, gap=1 / 6), dict(m=1.07, beats=5, gap=1 / 6), dict(m=0.94, beats=4, gap=0.20)]


def watch_tick(take, r):
    """A wristwatch ticking as it is checked (idle_tic 7): four or five beats of a mechanical movement, 5-6 a second,
    the first at 3 ms. Each beat is the escapement locking (a 0.4 ms 4-12 kHz tick) then the pallet's drop 1.8 ms
    later (steel modes at 4.3 kHz, alternate beats 7 % lower: tick, tock) and a faint ring of the case at 2.1 kHz."""
    q = WATCH[take - 1]
    m = q['m']
    b = buf(0.003 + (q['beats'] - 1) * q['gap'] + 0.15)
    for k in range(q['beats']):
        at = 0.003 + k * q['gap']
        f = 4300 * m * (1.0 if k % 2 == 0 else 0.93)
        place(b, norm(noise_burst(4000, 12000, 0.0004, r)), at, 0.50)
        place(b, norm(modal(f, [1, 1.6, 2.5], [1, 0.5, 0.3], [0.002, 0.0015, 0.001], r)), at + 0.0018, 0.60)
        place(b, norm(modal(2100 * m, [1, 2.9], [1, 0.3], [0.006, 0.004], r)), at + 0.0018, 0.15)
    return b


SUNM = [dict(m=1.00, run=0.13), dict(m=1.10, run=0.15)]


def sunroof_motor(take, r):
    """The sunroof flipping open fast (eject tick 1, open by 3): a small relay click at 3 ms, a fast electric motor
    (whine 520 + 1300 s Hz, its speed a half-sine over 0.13-0.15 s), the panel's track zipping under it, and the panel
    stopping on a padded end (a small damped knock)."""
    q = SUNM[take - 1]
    m = q['m']
    start = 0.004
    stop = start + q['run']
    dur = round(stop + 0.30, 3)
    b = buf(dur)
    t = tt(dur)
    s = np.where((t > start) & (t < stop), np.sin(np.pi * (t - start) / q['run']), 0.0)
    b += motor(t, s, r, 520 * m, 1300 * m, grit=0.25, air=0.12, whine=0.45, rotor_div=5)
    zip_gate = (0.5 + 0.5 * np.cos(2 * np.pi * np.cumsum(180 * s) / SR)) ** 4
    b += 0.12 * norm(bandpass(noise(dur, r), 2500, 8000) * s ** 2 * zip_gate)
    place(b, relay_click(r, 2600 * m, release_after=0.02), 0.003, 0.20)
    place(b, mix(norm(modal(650 * m, [1, 2.3], [1, 0.3], [0.008, 0.005], r)), 0.5 * thunk(r, 220, 160, 0.012, 0.006)),
          stop, 0.22)
    return b


SHUT = [dict(m=1.00, dk=1.00, catch=0.045), dict(m=1.10, dk=1.20, catch=0.055), dict(m=0.90, dk=0.85, catch=0.038)]


def sunroof_shut(take, r):
    """The sunroof shutting (eject tick 23): a soft, well-damped thunk. The panel's weight onto its rubber seal at
    4 ms (a thunk 150 -> 90 Hz, the seal taking it as low-passed noise under 2 kHz), the enamel panel's modes at
    520 Hz damped to 12-22 ms, and the latch catching, muffled, 38-55 ms later."""
    q = SHUT[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(0.60)
    h = 0.004
    place(b, strike(r, 1500), h, 0.12)
    place(b, thunk(r, 150 * m, 90 * m, 0.045 * dk, 0.015), h, 0.35)
    place(b, norm(lowpass(noise(0.2, r), 2000 * m) * exp_env(0.2, 0.018 * dk, 0.0015)), h, 0.55)
    place(b, norm(modal(520 * m, [1, 1.59, 2.14], [1, 0.5, 0.3], [0.018 * dk, 0.012 * dk, 0.008 * dk], r)), h, 0.60)
    place(b, norm(noise_burst(400, 2000, 0.006, r)), h, 0.20)
    place(b, mix(norm(modal(1400 * m, [1, 1.7], [1, 0.4], [0.004, 0.003], r)), 0.4 * thunk(r, 260, 190, 0.010, 0.005)),
          q['catch'], 0.18)
    return b


QUIET = [dict(m=1.00, dk=1.00), dict(m=1.12, dk=1.20), dict(m=0.90, dk=0.85)]


def step_quiet(take, r):
    """The Executive's footfall (every walk plant, 0.4-0.55 s apart): the Colossus's boot made smaller and set down
    through felt. A thunk 110 -> 70 Hz, the leg's body, the strike; then everything over 900 Hz and the rumble under
    90 Hz taken out, and the felt pad's soft 'pff' on top. No ground rumble, no creak."""
    q = QUIET[take - 1]
    m, dk = q['m'], q['dk']
    b = boot(r, m, thump=(110.0, 70.0), tau=0.05, body=0.22, body_f=(260.0, 480.0), strike_hp=900.0, dk=0.6 * dk)
    b = highpass(lowpass(b, 900 * m), 90)
    place(b, norm(lowpass(noise(0.15, r), 700) * exp_env(0.15, 0.015, 0.003)), 0.003, 0.25)
    return b


# ---------------------------------------------------------------- references (measured as the game plays them)

BEACON_DEACT = dict(event='block.beacon.deactivate', file='block__beacon__deactivate.ogg', vol=1.0, pitch=1.0)
SHEAR = dict(event='entity.sheep.shear', file='mob__sheep__shear.ogg', vol=1.0, pitch=1.0)
SHUTTER = dict(event='block.trial_spawner.close_shutter', file='block__trial_spawner__close_shutter.ogg', vol=1.0, pitch=1.0)
CRAFTER = dict(event='block.crafter.craft', file='block__crafter__craft.ogg', vol=1.0, pitch=1.0)
EQUIP_LEATHER = dict(event='item.armor.equip_leather', file='item__armor__equip_leather1.ogg', vol=1.0, pitch=1.0)

BASE = dict(category='NEUTRAL', attenuation=16, fixed_range=False, volume=1.0)
KEY_NOTE = (' Keyframe volume 1.0 and pitch 1.0 (the file carries the loudness and the frame\'s size; the clip\'s '
            'proposed volume and pitch were placeholders).')


def ev(frame, name, **kw):
    d = dict(BASE)
    d.update(id=f'mecha.{frame}.{name}', file=f'mecha/{frame}/{name}')
    d.update(kw)
    return d


def col(name, **kw):
    return ev('colossus', name, **kw)


def exe(name, **kw):
    return ev('executive', name, **kw)


SOUNDS = [
    # ---- the Colossus: the planning office
    col('planning_chime', fn=planning_chime, takes=1, target=-27.0, cls='done chime (public-address ding-dong)',
        kind='stinger', fade_ms=50, hit_ms=3, ref=BELL, pitch=[1.0, 1.0], attenuation=24,
        subtitle_en='Planning office chimes', subtitle_pt='Sino do Planejamento toca',
        play='siege_in tick 2, once, as Siege begins. Fixed pitch: E5 at 3 ms, C5 at 333 ms.' + KEY_NOTE),
    col('permit_denied', fn=permit_denied, takes=2, target=-25.0, cls='buzzer (refusal, dry)', kind=('buzz', 0.36),
        fade_ms=10, hit_ms=3, ref=VAULT_FAIL, pitch=[1.0, 1.0], attenuation=16,
        subtitle_en='Siege permit denied', subtitle_pt='Licença de cerco negada',
        play='siege_refused tick 9, with the refusal line. It is the refusal\'s sound: every refused Siege press must reach it, never skip it. Fixed pitch (the buzzer\'s 105-120 Hz is its voice). Held 0.30-0.36 s by take.' + KEY_NOTE),
    # ---- the kneel, the heave, the cradles
    col('hydraulic_sink', fn=hydraulic_sink, takes=3, target=-26.5, cls='servo (hydraulic stroke, kneel)',
        kind=('servo', 0.80), fade_ms=25, hit_ms=3, ref=PISTON_IN, pitch=[0.98, 1.02], attenuation=20,
        subtitle_en='Colossus kneels', subtitle_pt='Colosso se ajoelha',
        play='siege_in tick 24: the stroke runs to 610 ms (tick 36), the settle knock at 710 ms (tick 38, siege:locked). The knees meet the ground at 350 ms (tick 31), where siege_anchor plays from the server.' + KEY_NOTE),
    col('hydraulic_groan', fn=hydraulic_groan, takes=2, target=-26.5, cls='servo (hydraulic heave)',
        kind=('servo', 1.00), fade_ms=25, hit_ms=3, ref=PISTON_IN, pitch=[0.98, 1.02], attenuation=20,
        subtitle_en='Hydraulics groan', subtitle_pt='Hidráulica geme',
        play='build tick 60 (the groan between the two stands). For the release, key it on siege_out tick 12 (siege:unanchor, the first heave up); the clip now keys mecha.hydraulic_lift on 7. The pressure climbs for 16-19 ticks, the relief valve sighs 30 ms after.' + KEY_NOTE),
    col('hydraulic_hiss', fn=hydraulic_hiss, takes=3, target=-25.5, cls='hiss (pneumatic cradles)', kind='hiss',
        fade_ms=40, hit_ms=3, ref=BOTTLE, pitch=[0.95, 1.05],
        subtitle_en='Hydraulics hiss', subtitle_pt='Hidráulica chia',
        play='boot 8, build 101 and 289, shutdown 5, siege_in 37: the cradles unfolding or folding. Holds 0.36-0.58 s by take, the last puff 120 ms after.' + KEY_NOTE),
    col('latch_clunk', fn=latch_clunk, takes=3, target=-22.5, cls='clamp, latch', kind='latch', fade_ms=12, hit_ms=4,
        ref=TRAPDOOR_CLOSE, pitch=[0.95, 1.05],
        subtitle_en='Weapon cradle latches', subtitle_pt='Suporte de arma trava',
        play='boot 17 and 22, build 110 and 115: the bolt at 4 ms, the pawl at 36-52 ms, the cradle settling 45 ms after.' + KEY_NOTE),
    col('turret_whir', fn=turret_whir, takes=3, target=-27.0, cls='servo (slewing ring)', kind=('servo', 0.45),
        fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[0.97, 1.03],
        subtitle_en='Shoulder guns swivel', subtitle_pt='Armas do ombro giram',
        play='idle_tic 1 and 19 (the scan), and wherever the shoulder weapons swivel. An 8-10 tick swing by take; the brake holds at 406-506 ms.' + KEY_NOTE),
    # ---- the sieged building
    col('siege_hum', fn=siege_hum, finish=finish_loop, takes=1, target=-28.0, cls='hum (Siege loop)',
        kind=('loop', 3.0), fade_ms=0, hit_ms=None, ref=BEACON_AMBIENT, pitch=[1.0, 1.0], attenuation=12,
        subtitle_en='Sieged Colossus hums', subtitle_pt='Colosso em cerco zumbe',
        play='LOOP while SIEGED: one client tickable instance looping this 3.0 s file (seamless: no fades in it), from siege:locked (siege_in tick 38) to siege:release (siege_out tick 0); fade in and out over 10 ticks (volume 0 -> 1). The siege clip\'s key on tick 20 should not start a second copy each pass: the file is a loop, not a one-shot. Volume 1.0, pitch 1.0.'),
    # ---- the ribbon
    col('snip', fn=snip, takes=3, target=-25.0, cls='tick (scissors)', kind='click', fade_ms=8, hit_ms=3, ref=SHEAR,
        pitch=[0.95, 1.05], attenuation=12, subtitle_en='Scissors snip', subtitle_pt='Tesoura corta',
        play='build 260 and 263 (the ribbon): the blades meet at 3 ms, the ribbon parts 18-30 ms on. A different take each.' + KEY_NOTE),
    # ---- footfalls and the anchor
    col('step_quake', fn=step_quake, takes=3, target=-24.5, cls='heavy walker footfall (the building walks)',
        kind=('step', 0.50), fade_ms=25, hit_ms=3, ref=GOLEM_WALK1, pitch=[0.95, 1.05], attenuation=32,
        subtitle_en='Colossus footsteps', subtitle_pt='Passos do Colosso',
        play='Server at every step:l_quake / step:r_quake (walk 0 and 12, walk_b 0 and 9, walk_c 0 and 13, limp 16, build 90 and 97, siege_in 13 and 22, siege_out 30 and 39), at the boot, with the dust ring, the small shake and the block\'s own step sound underneath. Random take, pitch 0.95-1.05.'),
    col('siege_anchor', fn=siege_anchor, takes=2, target=-18.0, cls='signature moment (the anchor)',
        kind=('step', 0.55), fade_ms=40, hit_ms=3, ref=BEACON_DEACT, pitch=[0.97, 1.03], attenuation=32,
        subtitle_en='Knee spades dig in', subtitle_pt='Pás dos joelhos cravam',
        play='Server on siege:anchor (siege_in tick 31) between the two spades, with the block-crack particles and the shake. Also on siege:unanchor (siege_out tick 12) at pitch 1.1 and volume 0.8: the spades pulled out of the ground.'),
    # ---- the Executive
    exe('premium_jingle', fn=premium_jingle, takes=1, target=-26.0, cls='done chime (the premium jingle)',
        kind='stinger', fade_ms=50, hit_ms=3, ref=BELL, pitch=[1.0, 1.0], attenuation=16,
        subtitle_en='Premium jingle plays', subtitle_pt='Toca o jingle premium',
        play='boot 0, drive_jingle 1 (the drive key), build 230. Fixed pitch: C5 E5 G5 110 ms apart, the C major chord on C6 at 333 ms. Do not restart it while it plays.' + KEY_NOTE),
    exe('cloth_tug', fn=cloth_tug, takes=3, target=-27.2, cls='cloth (soft, small)', kind=('tug', 0.15), fade_ms=15,
        hit_ms=3, ref=EQUIP_LEATHER, pitch=[0.95, 1.05], attenuation=8,
        subtitle_en='Mecha adjusts its suit', subtitle_pt='Mecha ajeita o terno',
        play='boot 25 and 29 (the tie), drive_jingle 10 (the lapels), build 210. A different take each.' + KEY_NOTE),
    exe('shoe_tap', fn=shoe_tap, takes=3, target=-30.5, cls='tick (heel taps)', kind=('taps', 0.12), fade_ms=10,
        hit_ms=3, ref=CRAFTER, pitch=[0.95, 1.05], attenuation=10,
        subtitle_en='Shoe taps impatiently', subtitle_pt='Sapato bate, impaciente',
        play='idle_tic 14: two or three heel taps 88-125 ms apart, the first at 3 ms.' + KEY_NOTE),
    exe('watch_tick', fn=watch_tick, takes=3, target=-32.5, cls='tick (wristwatch)', kind=('ticks', 0.50),
        fade_ms=10, hit_ms=3, ref=SPYGLASS, pitch=[1.0, 1.0], attenuation=6,
        subtitle_en='Wristwatch ticks', subtitle_pt='Relógio de pulso tiquetaqueia',
        play='idle_tic 7 (the watch comes up): four or five beats at 5-6 a second. Fixed pitch.' + KEY_NOTE),
    exe('sunroof_motor', fn=sunroof_motor, takes=2, target=-28.5, cls='servo (small fast motor)',
        kind=('servo', 0.15), fade_ms=15, hit_ms=3, ref=PISTON_OUT, pitch=[1.0, 1.0],
        subtitle_en='Sunroof opens', subtitle_pt='Teto solar abre',
        play='eject tick 1: the panel runs 0.13-0.15 s by take and stops on its pad by tick 4, before clamp_blow on 3 is over.' + KEY_NOTE),
    exe('sunroof_shut', fn=sunroof_shut, takes=3, target=-26.5, cls='latch (soft, damped)', kind='latch',
        fade_ms=12, hit_ms=4, ref=SHUTTER, pitch=[0.95, 1.05],
        subtitle_en='Sunroof shuts', subtitle_pt='Teto solar fecha',
        play='eject tick 23: the panel onto its seal at 4 ms, the latch 38-55 ms later.' + KEY_NOTE),
    exe('step_quiet', fn=step_quiet, takes=3, target=-29.0, cls='medium step, muffled (felt)', kind=('step', 0.22),
        fade_ms=15, hit_ms=3, ref=GOLEM_STEP, pitch=[0.95, 1.05], attenuation=12,
        subtitle_en='Quiet mecha footsteps', subtitle_pt='Passos discretos de mecha',
        play='Server at every step:l_quiet / step:r_quiet (walk 0 and 10, walk_b 0 and 8, walk_c 0 and 11, limp 16), at the boot, with the block\'s own step sound underneath at its usual volume. No shake. Random take, pitch 0.95-1.05.'),
]

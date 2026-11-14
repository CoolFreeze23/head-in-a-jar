"""The Lunk: a Utility Shell made from a grey office filing cabinet.

Three steel drawers on stubby legs, long gorilla arms in black work gloves that knuckle the floor, the jar held on the
lid by two big bulldog clips, a webcam. It is strong, cheap and from the office, so it sounds like office furniture
asked to do a loader's job: thin sheet steel that booms hollow and rattles, drawers on ball-bearing runners, papers
inside, spring-steel clips, and one tired servo per arm. Nothing about it is enamel or brass; it is not the company's
machine voice, so it never uses the house bell.

Event ids are `mecha.lunk.<name>`; the clips key them by the bare name (lunk-art.md section 5), and the walk's keys
(`step:*_lunk`, `knuckle:*`, `knuckles:both`) are played by the code at the planted limb. Every file hits at 3 ms.
Takes differ by construction (per-take tables). The walk's two sounds are quiet layers under the block's own step
sound: in the 12-tick cycle a limb plants every 3 ticks (leg, fist, leg, fist), so both are short and dull.

Shared parts come from the kit (`nsynth`), the Bulwark (`recipes_bulwark`: the weighted boot, the creak, references)
and the Hopper (`recipes_mecha`: the saturation and its references).
"""
import numpy as np
from nsynth import *
from recipes_mecha import soft, PISTON_IN, PISTON_OUT, DOOR_OPEN, DOOR_CLOSE, CLICK, SHELF, GOLEM_STEP
from recipes_bulwark import creak, boot
from recipes_heavy import EQUIP_LEATHER

T = TICK


def sheet(r, f0, m=1.0, dk=1.0, n=6):
    """Thin cabinet sheet steel: a box panel's modes at f0 x 1, 1.59, 2.14, 2.65, 3.9, 5.2, ringing hollow for
    15-60 ms (x dk). Thin steel, so it booms and rattles rather than rings like the company's enamel."""
    ratios = [1, 1.59, 2.14, 2.65, 3.9, 5.2][:n]
    amps = [1, 0.8, 0.6, 0.45, 0.3, 0.2][:n]
    dec = [x * dk for x in (0.06, 0.05, 0.04, 0.03, 0.02, 0.015)][:n]
    return modal(f0 * m, ratios, amps, dec, r, 0.01)


def rattle_ticks(b, r, times, m, gain, f=(1300, 2000)):
    """Loose things inside the drawers and the other drawers in their runners: steel ticks at the given times, each
    0.75 of the one before, every third a lower panel knock."""
    for k, at in enumerate(times):
        g = gain * 0.75 ** k * r.uniform(0.7, 1.0)
        if k % 3 == 2:
            place(b, norm(modal(650 * m * r.uniform(0.95, 1.05), [1, 2.756], [1, 0.4], [0.020, 0.013], r)), at, 0.7 * g)
        else:
            place(b, norm(modal(r.uniform(*f) * m, [1, 2.4, 3.9], [1, 0.45, 0.2], [0.012, 0.008, 0.005], r)), at, g)


# ---------------------------------------------------------------- the drawers

DSLIDE = [dict(m=1.00, run=0.20, roll=95.0), dict(m=1.10, run=0.17, roll=110.0), dict(m=0.90, run=0.24, roll=80.0)]


def drawer_slide(take, r):
    """A steel filing drawer running out on its ball-bearing runners (idle_tic tick 5, open by 8; parked tick 145, the
    drawer 'breathing'): the catch letting go at 3 ms (a tooth), the bearings rumbling (a 1.5-6 kHz band gated at
    80-110 balls a second at full speed) over 0.17-0.24 s, the empty box humming as it moves (250-700 Hz), papers
    shushing inside, and the drawer reaching its stop (a short sheet-steel bump)."""
    q = DSLIDE[take - 1]
    m = q['m']
    start = 0.003
    stop = start + q['run']
    dur = round(stop + 0.40, 3)
    b = buf(dur)
    t = tt(dur)
    u = np.clip((t - start) / q['run'], 0, 1)
    s = np.where((t > start) & (t < stop), np.sin(np.pi * u) ** 0.8, 0.0)
    gate = (0.5 + 0.5 * np.cos(2 * np.pi * np.cumsum(q['roll'] * s) / SR)) ** 3
    roll = bandpass(noise(dur, r), 1500 * m, 6000 * m) * s * (0.4 + 0.6 * gate)
    box = bandpass(noise(dur, r), 250 * m, 700 * m) * s ** 1.2
    hiss = bandpass(noise(dur, r), 3000, 9000) * s ** 2
    paper = bandpass(noise(dur, r), 800, 4000) * s
    b += 0.50 * norm(roll) + 0.35 * norm(box) + 0.12 * norm(hiss) + 0.10 * norm(paper)
    place(b, tooth(r, 2600 * m), 0.003, 0.35)
    place(b, mix(norm(modal(560 * m, [1, 1.59, 2.14, 2.65], [1, 0.6, 0.4, 0.25], [0.020, 0.015, 0.010, 0.007], r)),
                 0.5 * thunk(r, 160, 100, 0.025, 0.010), 0.3 * strike(r, 1800)), stop, 0.45)
    return b


DSLAM = [dict(m=1.00, dk=1.00, rat=(0.045, 0.080, 0.120)), dict(m=1.10, dk=1.20, rat=(0.035, 0.070)),
         dict(m=0.90, dk=0.85, rat=(0.050, 0.095, 0.130, 0.170))]


def drawer_slam(take, r):
    """The drawer shoved shut by a fist (idle_tic tick 16; toss tick 21, the drawer that jolted open): a sheet-steel
    slam at 3 ms (a strike, the cabinet's thin box booming at 380 Hz and up, the drawer front's iron at 620 / 1000 Hz,
    a thunk 140 -> 85 Hz kept small), the catch clicking 8 ms on, the papers inside slapping, and the other drawers and whatever is
    in them rattling 35-170 ms after."""
    q = DSLAM[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(0.80)
    h = 0.003
    place(b, strike(r, 1400 * m), h, 0.50)
    place(b, norm(sheet(r, 380, m, dk)), h, 0.50)
    place(b, norm(iron_body(r, 620 * m, 1000 * m, 0.6 * dk, band_scale=m)), h, 0.55)
    place(b, thunk(r, 140 * m, 85 * m, 0.045, 0.018), h, 0.22)
    place(b, tooth(r, 2300 * m), h + 0.008, 0.30)
    place(b, norm(noise_burst(600, 3500, 0.010, r)), h + 0.004, 0.30)
    rattle_ticks(b, r, q['rat'], m, 0.16)
    return b


BURST = [dict(m=1.00, stops=(0.045, 0.068, 0.096), paper=0.75, tip=0.32),
         dict(m=1.10, stops=(0.038, 0.060, 0.090), paper=0.95, tip=0.40)]


def drawers_burst(take, r):
    """The collapse (destroyed tick 13, the drawers thrown out on 12-14): every catch gives at once (a strike and three
    teeth), three drawers zip out on their runners and hit their stops 38-96 ms in (sheet-steel bangs, each smaller),
    the empty cabinet booms, the filing goes everywhere (paper fluttering and slapping down for 0.75-0.95 s, thinning),
    and the top drawer tips askew in its runners with a clatter 0.32-0.40 s in."""
    q = BURST[take - 1]
    m = q['m']
    dur = 1.90
    b = buf(dur)
    t = tt(dur)
    h = 0.003
    place(b, strike(r, 1200), h, 0.50)
    for j in range(3):
        place(b, tooth(r, 2400 * m * (1 + 0.05 * j)), h + 0.004 * j, 0.25)
    for k, at in enumerate(q['stops']):
        g = 1.0 - 0.15 * k
        zt = np.clip((t - h) / (at - h), 0, 1)
        zs = np.where(t < at, zt ** 1.5, 0.0) * (t > h)
        zgate = (0.5 + 0.5 * np.cos(2 * np.pi * np.cumsum(140 * m * zs) / SR)) ** 3
        b += 0.18 * g * norm(bandpass(noise(dur, r), 1500 * m, 6000 * m) * zs * (0.4 + 0.6 * zgate))
        place(b, strike(r, 1400 * m), at, 0.45 * g)
        place(b, norm(sheet(r, 340 * (1 + 0.07 * k), m, 1.0)), at, 0.40 * g)
        place(b, norm(iron_body(r, 560 * m * (1 + 0.06 * k), 950 * m, 0.7, band_scale=m)), at, 0.50 * g)
        place(b, thunk(r, 130 * m, 75 * m, 0.040, 0.018), at, 0.20 * g)
    place(b, norm(modal(150 * m, [1, 1.59, 2.14], [1, 0.6, 0.4], [0.09, 0.07, 0.05], r, 0.01)), h, 0.12)
    # the filing: a flutter of sheets (noise roughened at 20-40 Hz) and pages slapping down, thinning
    P = q['paper']
    pe = np.clip((t - 0.06) / 0.03, 0, 1) * np.exp(-np.maximum(t - 0.06, 0) / (P / 3.5)) * (t > 0.06)
    rough = np.clip(0.3 + np.abs(lowpass(noise(dur, r), 35)) / 0.15, 0, 2.0)
    b += 0.40 * norm(bandpass(noise(dur, r), 1200, 9000) * pe * rough)
    at = 0.07
    while at < P:
        g = np.exp(-(at - 0.07) / (P / 3)) * r.uniform(0.3, 1.0)
        place(b, norm(noise_burst(800, 5000, r.uniform(0.002, 0.006), r)), at, 0.28 * g)
        at += r.uniform(0.012, 0.045) * (1 + 2 * at / P)
    place(b, norm(iron_body(r, 600 * m, 950 * m, 0.5, band_scale=m)), q['tip'], 0.25)
    place(b, norm(sheet(r, 300, m, 0.6, 4)), q['tip'] + 0.004, 0.15)
    place(b, norm(iron_body(r, 640 * m, 1000 * m, 0.4, band_scale=m)), q['tip'] + 0.065, 0.12)
    return soft(b, 2.5)


# ---------------------------------------------------------------- the jar: the bulldog clips and the pat

CLIP = [dict(m=1.00, jaw=0.004), dict(m=1.12, jaw=0.006), dict(m=0.90, jaw=0.003)]


def bulldog_clip(take, r):
    """A big bulldog clip snapping shut on the jar's base rim (board tick 20) or springing open (alight tick 2): the
    spring steel letting go at 3 ms (a bright strike, the spring's modes at 3.1 kHz, a faint twang 1.9 -> 1.6 kHz), the
    black jaws meeting the rim 3-6 ms later (a short knock at 1.15 kHz) and the glass ticking under them."""
    q = CLIP[take - 1]
    m = q['m']
    b = buf(0.25)
    h = 0.003
    place(b, strike(r, 3500 * m), h, 0.45)
    place(b, norm(modal(3100 * m, [1, 1.83, 2.9], [1, 0.5, 0.25], [0.008, 0.005, 0.003], r, 0.02)), h, 0.45)
    place(b, sweep_sine(1900 * m, 1600 * m, 0.020, 0.010), h, 0.12)
    place(b, norm(modal(1150 * m, [1, 2.4], [1, 0.4], [0.010, 0.006], r)), h + q['jaw'], 0.45)
    place(b, norm(glass_modes(r, 2600 * m, decays=(0.010, 0.006, 0.004, 0.003))), h + q['jaw'], 0.10)
    return b


PAT = [dict(m=1.00, dk=1.00), dict(m=1.20, dk=1.20), dict(m=0.85, dk=0.85)]


def pat(take, r):
    """A gloved fist patting the steel lid once, fondly (alight tick 22, after the jar is set down): the glove's soft
    leather thump at 3 ms (200-1600 Hz noise, 12 ms), a small thunk 170 -> 110 Hz, the lid's sheet steel answering
    hollow but damped by the fist (420 Hz and up, 10-35 ms), a faint creak of the glove and the empty ring ticking."""
    q = PAT[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(0.45)
    h = 0.003
    place(b, norm(bandpass(noise(0.2, r), 200 * m, 1600 * m) * exp_env(0.2, 0.012, 0.002)), h, 0.55)
    place(b, thunk(r, 170 * m, 110 * m, 0.030, 0.012), h, 0.22)
    place(b, norm(modal(420 * m, [1, 1.59, 2.14, 2.65, 3.9], [1, 0.7, 0.5, 0.35, 0.2],
                        [x * dk for x in (0.035, 0.028, 0.022, 0.016, 0.010)], r, 0.01)), h, 0.45)
    place(b, creak(r, 0.03, 1100 * m, rate=(0.006, 0.012), decay=0.002), 0.006, 0.05)
    place(b, norm(modal(1900 * m, [1, 2.4], [1, 0.3], [0.012, 0.008], r)), h + 0.002, 0.05)
    return b


# ---------------------------------------------------------------- the arms' servos: giving up, going flat, straining

SDOWN = [dict(m=1.00, L=0.30), dict(m=1.10, L=0.26), dict(m=0.90, L=0.36)]


def servo_down(take, r):
    """A falling servo whine, giving up (toss_refused tick 9, the arms drop 9-13; alight tick 26, the slump): the
    brake letting go at 3 ms (a tooth), the arm servo running down from most of its speed to nothing over 0.26-0.36 s
    (whine 160 + 520 s Hz falling), the shoulder creaking a little, and the arms bottoming out softly at the end."""
    q = SDOWN[take - 1]
    m, L = q['m'], q['L']
    dur = round(L + 0.40, 3)
    b = buf(dur)
    t = tt(dur)
    s = 0.85 * np.clip(1 - (t - 0.003) / L, 0, 1) ** 1.3 * (t > 0.003)
    b += motor(t, s, r, 160 * m, 520 * m, grit=0.40, air=0.14, whine=0.34, rotor_div=8)
    place(b, tooth(r, 1900 * m), 0.003, 0.25)
    place(b, creak(r, 0.5 * L, 700 * m, decay=0.005), 0.05, 0.08)
    place(b, mix(norm(modal(380 * m, [1, 2.756], [1, 0.4], [0.030, 0.020], r)), 0.7 * thunk(r, 120, 75, 0.040, 0.015)),
          L, 0.35)
    return b


SWIND = [dict(m=1.00, L=1.40, drops=(0.45, 0.80, 1.10)), dict(m=1.08, L=1.50, drops=(0.38, 0.70, 1.02, 1.25))]


def servo_wind_down(take, r):
    """The jar going flat (flat tick 2; the arms sag to 30, the cabinet slumps to 32-36, the webcam goes out last):
    the supply relay dropping at 3 ms, both arm servos winding down together over 1.40-1.50 s (whine 150 + 480 s Hz)
    and stumbling three or four times as the charge fails, the 70 Hz drive hum dying with them, the shoulders creaking
    as the arms come down, and one last small tick."""
    q = SWIND[take - 1]
    m, L = q['m'], q['L']
    dur = round(L + 0.50, 3)
    b = buf(dur)
    t = tt(dur)
    s = 0.95 * np.clip(1 - (t - 0.003) / L, 0, 1) ** 1.4 * (t > 0.003)
    for d in q['drops']:
        s = s * (1 - 0.55 * np.exp(-((t - d) / 0.025) ** 2))
    b += motor(t, s, r, 150 * m, 480 * m, grit=0.35, air=0.14, whine=0.32, rotor_div=8)
    hum = sum(a * np.sin(2 * np.pi * 70 * m * k * t) for k, a in zip([1, 2, 3, 4], [1, 0.6, 0.4, 0.25]))
    b += 0.18 * norm(hum) * s ** 0.5
    place(b, relay_click(r, 1300 * m), 0.003, 0.35)
    place(b, creak(r, 0.40, 620 * m, rate=(0.020, 0.040), decay=0.006), 0.50, 0.06)
    place(b, norm(modal(1700 * m, [1, 1.5], [1, 0.4], [0.007, 0.005], r)), L + 0.06, 0.08)
    return b


STRAIN = [dict(m=1.00, L=0.30, trem=10.0, pops=(0.06, 0.19)), dict(m=1.10, L=0.26, trem=12.0, pops=(0.10,)),
          dict(m=0.90, L=0.36, trem=8.5, pops=(0.05, 0.16, 0.27))]


def strain(take, r):
    """The toss's strain hold (toss tick 15; the hold trembles 14-18, the throw on 19): the load comes on at 3 ms (a
    tooth), both arm servos stalled under it (whine 130 + 300 s Hz at a third of their speed, trembling at 8.5-12 Hz
    with the hips), a low groan at 58 Hz trembling with them, and the cabinet's sheet steel flexing with one to three
    'oil-can' pops (a bending knock and the thin box's boom at 240 Hz)."""
    q = STRAIN[take - 1]
    m, L, tr = q['m'], q['L'], q['trem']
    dur = round(L + 0.40, 3)
    b = buf(dur)
    t = tt(dur)
    env = np.clip(t / 0.04, 0, 1) * np.where(t < L, 1.0, np.exp(-(t - L) / 0.05))
    wob = np.sin(2 * np.pi * tr * t)
    s = 0.35 * env * (1 + 0.25 * wob)
    b += motor(t, s, r, 130 * m, 300 * m, grit=0.50, air=0.12, whine=0.30, rotor_div=8)
    groan = osc_harm(np.full(len(t), 58.0 * m) * (1 + 0.03 * wob), 8, r, 1.3) * env * (0.7 + 0.3 * wob)
    b += 0.28 * norm(groan)
    place(b, tooth(r, 1800 * m), 0.003, 0.25)
    for at in q['pops']:
        place(b, mix(sweep_sine(420 * m, 300 * m, 0.030, 0.010),
                     0.6 * norm(modal(240 * m, [1, 1.59, 2.14], [1, 0.6, 0.4], [0.04, 0.03, 0.02], r)),
                     0.3 * strike(r, 1500)), at, 0.30)
    return b


# ---------------------------------------------------------------- sitting down, and the walk

CLUNK = [dict(m=1.00, dk=1.00, rat=(0.050, 0.085, 0.130)), dict(m=1.10, dk=1.20, rat=(0.060, 0.100)),
         dict(m=0.90, dk=0.85, rat=(0.045, 0.075, 0.110, 0.150))]


def clunk(take, r):
    """Sitting down hard on its plinth (flat tick 34, the slump's end; also destroyed tick 27 if the code wants its own
    thud there): a thunk 130 -> 60 Hz, the cabinet's thin box booming over it (200 Hz and up, 35-90 ms), the plinth's
    dull knock (noise under 1.2 kHz), a little iron, and the drawers' contents rattling 45-150 ms after."""
    q = CLUNK[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(0.90)
    h = 0.003
    place(b, strike(r, 900 * m), h, 0.30)
    place(b, thunk(r, 130 * m, 60 * m, 0.07 * dk, 0.030), h, 0.45)
    place(b, norm(modal(200 * m, [1, 1.59, 2.14, 2.65], [1, 0.7, 0.5, 0.3], [x * dk for x in (0.08, 0.065, 0.05, 0.035)],
                        r, 0.01)), h, 0.60)
    place(b, norm(lowpass(noise(0.4, r), 1200) * exp_env(0.4, 0.030, 0.002)), h, 0.40)
    place(b, norm(iron_body(r, 300 * m, 520 * m, 0.6, band_scale=0.6 * m)), h, 0.20)
    rattle_ticks(b, r, q['rat'], m, 0.08)
    return soft(b, 2.0)


KNUCK = [dict(m=1.00, dk=1.00), dict(m=1.10, dk=0.85), dict(m=0.90, dk=1.15), dict(m=1.21, dk=0.95)]


def knuckle(take, r):
    """A rubber-gloved fist planting on the floor as it knuckle-walks (knuckle:l on walk tick 3, knuckle:r on 9; both
    together on walk_fast's knuckles:both): dull, soft and low, a quiet layer under the block's step sound. A thunk
    160 -> 95 Hz, the glove's rubber taking it (noise under 1 kHz, 9-12 ms), a soft 'thp', a hint of floor grit;
    everything over 1.5 kHz taken out so twice a cycle never tires."""
    q = KNUCK[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(0.40)
    h = 0.003
    place(b, thunk(r, 160 * m, 95 * m, 0.028 * dk, 0.012), h, 0.42)
    place(b, norm(lowpass(noise(0.15, r), 1000 * m) * exp_env(0.15, 0.010 * dk, 0.0015)), h, 0.60)
    place(b, sweep_sine(260 * m, 190 * m, 0.012, 0.006), h, 0.30)
    place(b, norm(noise_burst(800, 3000, 0.006, r)), h, 0.06)
    return lowpass(b, 1500)


SLUNK = [dict(m=1.00, dk=1.00, box=0.10), dict(m=1.10, dk=1.15, box=0.14), dict(m=0.90, dk=0.85, box=0.08),
         dict(m=1.21, dk=1.00, box=0.12)]


def step_lunk(take, r):
    """The stubby foot planting (step:l_lunk on walk tick 0, step:r_lunk on 6): the Bulwark's weighted boot made
    lighter and plainer (a thunk 115 -> 55 Hz held 50 ms, the steel foot's small body at 300 / 540 Hz, a strike,
    floor debris) and the cabinet above answering hollow for a moment (its box at 165 Hz). A quiet layer under the
    block's own step sound."""
    q = SLUNK[take - 1]
    m, dk = q['m'], q['dk']
    b = boot(r, m, thump=(115.0, 55.0), tau=0.05, body=0.18, body_f=(300.0, 540.0), strike_hp=1000.0, dk=0.7 * dk)
    place(b, norm(modal(165 * m, [1, 1.59, 2.14], [1, 0.6, 0.4], [0.05, 0.04, 0.03], r, 0.01)), 0.006, q['box'])
    return b


# ---------------------------------------------------------------- references (measured as the game plays them)

ANVIL = dict(event='block.anvil.land', file='random__anvil_land.ogg', vol=0.3, pitch=0.95)
BARREL_CLOSE = dict(event='block.barrel.close', file='block__barrel__close.ogg', vol=0.5, pitch=0.95)

BASE = dict(category='NEUTRAL', attenuation=16, fixed_range=False, volume=1.0)
KEY_NOTE = (' Keyframe volume 1.0 and pitch 1.0 (the file carries the loudness and the size; the clip\'s proposed '
            'volume and pitch were placeholders).')


def ev(name, **kw):
    d = dict(BASE)
    d.update(id=f'mecha.lunk.{name}', file=f'mecha/lunk/{name}')
    d.update(kw)
    return d


SOUNDS = [
    # ---- the drawers
    ev('drawer_slide', fn=drawer_slide, takes=3, target=-25.0, cls='servo (a drawer on its runners)',
       kind=('servo', 0.20), fade_ms=20, hit_ms=3, ref=DOOR_OPEN, pitch=[0.96, 1.04],
       subtitle_en='Drawer slides open', subtitle_pt='Gaveta desliza e abre',
       play='idle_tic tick 5 (the drawer runs out 4-8): 0.17-0.24 s to its stop by take. parked tick 145 (the drawer breathing, open a pixel): there the clip\'s volume 0.15 is right and should stay, so it is barely there; its pitch 1.2 may stay too (a short pull).' + KEY_NOTE),
    ev('drawer_slam', fn=drawer_slam, takes=3, target=-22.5, cls='slam (sheet-steel drawer)', kind='latch',
       fade_ms=15, hit_ms=3, ref=DOOR_CLOSE, pitch=[0.95, 1.05], attenuation=20,
       subtitle_en='Drawer slams shut', subtitle_pt='Gaveta fecha com tudo',
       play='idle_tic tick 16 (the fist shoves it shut, easeInExpo) and toss tick 21 (the drawer that jolted open on the throw). The rattle runs 35-170 ms after.' + KEY_NOTE),
    ev('drawers_burst', fn=drawers_burst, takes=2, target=-20.0, cls='crash (drawers and filing)',
       kind=('step', 0.90), fade_ms=40, hit_ms=3, ref=ANVIL, pitch=[0.97, 1.03], attenuation=24,
       subtitle_en='Filing goes everywhere', subtitle_pt='Arquivo voa para todo lado',
       play='destroyed tick 13 (the drawers thrown out 12-14): the stops bang 38-96 ms in, the paper settles over 0.75-0.95 s, the top drawer tips at 0.32-0.40 s. The sit-down thud on 27 is the Bulwark\'s collapse or this family\'s clunk.' + KEY_NOTE),
    # ---- the jar
    ev('bulldog_clip', fn=bulldog_clip, takes=3, target=-26.0, cls='click (spring clip)', kind='click', fade_ms=8,
       hit_ms=3, ref=CLICK, pitch=[0.95, 1.05], attenuation=12,
       subtitle_en='Bulldog clips snap', subtitle_pt='Prendedores estalam',
       play='board tick 20 (the clips bite, easeInExpo) and alight tick 2 (they spring open). One file for both clips: they move together.' + KEY_NOTE),
    ev('pat', fn=pat, takes=3, target=-27.0, cls='knock (gloved fist on sheet steel)', kind=('pat', 0.15), fade_ms=12,
       hit_ms=3, ref=EQUIP_LEATHER, pitch=[0.95, 1.05], attenuation=12,
       subtitle_en='Lunk pats the lid', subtitle_pt='Brutamontes dá um tapinha',
       play='alight tick 22 (the right fist comes down on the empty lid once).' + KEY_NOTE),
    # ---- the arms' servos
    ev('servo_down', fn=servo_down, takes=3, target=-27.0, cls='servo (giving up)', kind=('servo', 0.30),
       fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[0.97, 1.03],
       subtitle_en='Servos give up', subtitle_pt='Servos desistem',
       play='toss_refused tick 9 (the arms drop 9-13; it is the refusal\'s sound, so every refused toss must reach it, never skip it) and alight tick 26 (the slump). The arms bottom out at 0.26-0.36 s by take.' + KEY_NOTE),
    ev('servo_wind_down', fn=servo_wind_down, takes=2, target=-27.0, cls='servo (spin-down, the jar flat)',
       kind=('servo', 1.40), fade_ms=30, hit_ms=3, ref=PISTON_IN, pitch=[0.98, 1.02],
       subtitle_en='Lunk runs out of steam', subtitle_pt='Brutamontes fica sem pique',
       play='flat tick 2: the wind-down runs 1.40 / 1.50 s by take (to about tick 30-32, where the arms reach the floor), the last tick after. clunk follows on 34.' + KEY_NOTE),
    ev('strain', fn=strain, takes=3, target=-26.5, cls='servo (stalled under load)', kind=('servo', 0.30),
       fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[0.97, 1.03],
       subtitle_en='Lunk strains', subtitle_pt='Brutamontes faz força',
       play='toss tick 15 (the hold trembles 14-18): 0.26-0.36 s by take, so it is over by the throw on 19 (take 3 runs 20 ms past it, under the whoosh).' + KEY_NOTE),
    # ---- sitting down, and the walk
    ev('clunk', fn=clunk, takes=3, target=-24.5, cls='landing (cabinet on its plinth)', kind=('step', 0.30),
       fade_ms=20, hit_ms=3, ref=BARREL_CLOSE, pitch=[0.95, 1.05], attenuation=20,
       subtitle_en='Lunk sits down hard', subtitle_pt='Brutamontes senta com tudo',
       play='flat tick 34 (the slump lands; the drawer jolts on 33-34). The clip keys pitch 0.8 for weight; this file is already that heavy, so 1.0. Also usable at destroyed tick 27 (the sit-down on the plinth).' + KEY_NOTE),
    ev('knuckle', fn=knuckle, takes=4, target=-30.0, cls='quiet step layer (gloved knuckles)', kind=('step', 0.12),
       fade_ms=15, hit_ms=3, ref=SHELF, pitch=[0.94, 1.06], attenuation=10,
       subtitle_en='Knuckles thud', subtitle_pt='Nós dos dedos batem',
       play='Code at the planted fist on knuckle:l (walk tick 3) and knuckle:r (9), and on walk_fast\'s knuckles:both (3 and 9, once for both fists, or each fist at 0.7). Random take, pitch 0.94-1.06, with the block\'s own step sound at its usual volume over it (this is the quiet layer). Twice a 0.6 s cycle, so it is short and dull on purpose.'),
    ev('step_lunk', fn=step_lunk, takes=4, target=-27.5, cls='medium step (stubby foot, lighter than the Bulwark)',
       kind=('step', 0.25), fade_ms=15, hit_ms=3, ref=GOLEM_STEP, pitch=[0.95, 1.05], attenuation=12,
       subtitle_en='Lunk footsteps', subtitle_pt='Passos do Brutamontes',
       play='Code at the planted foot on step:l_lunk (walk and walk_fast tick 0) and step:r_lunk (6), with the block\'s own step sound at its usual volume. Random take, pitch 0.95-1.05. 2.5 dB under the Bulwark\'s step_heavy, no shake.'),
]

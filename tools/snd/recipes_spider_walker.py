"""The Spider Walker's own: the metal thump of a pad set down.

The Spider Walker is the tea trolley's biscuit tin on eight bent-wire legs: brass wire thighs, long steel shins, dark
steel points. Its legs find their own footing (mecha/ProceduralLegs.java), so a pad lands whenever that leg has finished
its step, not on a clip's beat: eight a stride, up to about twenty-five a second flat out. Each one is a heavy metal
leg landing, in the manner of the walkers this gait comes from (a heavy-metal-block footfall at every foot), made here
from the house parts. The weight comes first: a deep thunk (160 -> 70 Hz) with a sub under it, the steel shin's low
body (about 240 Hz, ringing about 70 ms) and the leg's iron answer (330 / 560 Hz); only a little of the steel point's
tick and the tin above, and dark (low-passed at 3.2 kHz). Made heavier at the owner's word after the first, lighter
version: more than half its energy is now under 250 Hz (it was under a third) and it rings 0.22-0.37 s.
Measured against vanilla's heavy metal leg, the iron golem's step: within 2 LU of it, its centroid within an octave.

The block's own step sound plays with it, quieter and higher (the code's overlay). Every file hits at 3 ms. Takes
differ by construction (the per-take table), not by luck of the seed. The owner heard the first version and asked for this one heavier; this one is unheard.
"""
from nsynth import *

PAD = [dict(m=1.00, dk=1.00, tin=0.16), dict(m=1.07, dk=0.85, tin=0.20), dict(m=0.93, dk=1.15, tin=0.14),
       dict(m=1.13, dk=0.62, tin=0.34), dict(m=0.87, dk=1.45, tin=0.18)]


def pad(take, r):
    """One heavy pad down: the weight first. A deep thunk (160 -> 70 Hz, 55 ms) with a sub under it (85 -> 48 Hz), the
    steel shin's low body (about 240 Hz, ringing 70 ms), the leg's iron answer (330 / 560 Hz), then only a little of the
    point's tick and the tin above; dark (low-passed at 3.2 kHz)."""
    q = PAD[take - 1]
    m, dk = q['m'], q['dk']
    b = buf(0.90)
    h = 0.003
    place(b, strike(r, 1200 * m), h, 0.12)
    place(b, thunk(r, 160 * m, 70 * m, 0.055, 0.020), h, 0.45)
    place(b, sweep_sine(85 * m, 48 * m, 0.080, 0.030), h, 0.18)
    place(b, norm(modal(240 * m, [1, 1.93, 2.71, 3.6], [1, 0.6, 0.4, 0.2], [0.070 * dk, 0.048 * dk, 0.030 * dk, 0.018 * dk], r, 0.01)), h, 0.80)
    place(b, norm(iron_body(r, 330 * m, 560 * m, 0.8 * dk, noise_gain=0.2, band_scale=0.7 * m)), h, 0.45)
    place(b, norm(modal(950 * m, [1, 1.52, 2.34], [1, 0.4, 0.15], [0.016 * dk, 0.011 * dk, 0.007 * dk], r, 0.02)), h + 0.002, q['tin'])
    place(b, norm(lowpass(noise(0.3, r), 1400) * exp_env(0.3, 0.030, 0.002)), h, 0.20)
    return lowpass(b, 3200)


# ---------------------------------------------------------------- references (measured as the game plays them)

# the footfall the gait's original plays at every foot (there at 0.2-0.3 and pitch 1.0); made heavier at the owner's
# word ("make it heavier"), ours is matched to it played louder and lower, at 0.8 and pitch 0.85
NETHERITE_STEP = dict(event='block.netherite_block.step', file='block__netherite__step1.ogg', vol=0.8, pitch=0.85)
# made heavier, it is measured against vanilla's heavy metal leg, the iron golem's step (the mecha steps' reference)
GOLEM_STEP = dict(event='entity.iron_golem.step', file='mob__irongolem__walk2.ogg', vol=1.0, pitch=1.0)

BASE = dict(category='NEUTRAL', attenuation=12, fixed_range=False, volume=1.0)


def ev(name, **kw):
    d = dict(BASE)
    d.update(id=f'mecha.spider_walker.{name}', file=f'mecha/spider_walker/{name}')
    d.update(kw)
    return d


SOUNDS = [
    ev('pad', fn=pad, takes=5, target=-29.0, cls='medium step (a heavy metal leg landing; 2 under the mecha step: eight feet)', kind=('step', 0.15), fade_ms=12, hit_ms=3,
       ref=GOLEM_STEP, pitch=[0.94, 1.06],
       subtitle_en='Spider Walker steps', subtitle_pt='Andador Aranha pisa',
       play='Client, by the legs that find their own footing: at each pad the moment it lands, at the pad. Random take, '
            'pitch 0.94-1.06 (x 1.12 on a wall\'s face, a lighter tap), volume 0.85-1.0; under water volume x 0.5 and '
            'pitch x 0.75. The block\'s own step sound (on a wall its hit sound) over it at volume 0.08, pitch x 1.6.'),
]

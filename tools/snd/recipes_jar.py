"""The bare jar's two answers: a glass tink when it nudges something, a glug of bubbles when it tries an ability it
doesn't have.

Why the tink is synthesised: vanilla's `block.glass.hit` is the stone footstep set (`step/stone1-6`, centroid about
270 Hz, measured), not a glass sound, so pitching it up gives a pitched-up stone knock. The jar's tap is built as a
struck glass vessel damped by the saline inside: short modes, no low end.

Why the glug is synthesised, with a vanilla layer under it: a burst of bubbles is the one liquid sound the bubble
model handles well (each bubble is a single decaying, slightly rising sine); pours and splashes are turbulence and
stay vanilla. The vanilla bubble-pop files, referenced in sounds.json (nothing shipped), go under it at low volume for
real-water texture. If it reads as a synthesiser by ear, drop the synthesised entries and raise the vanilla layer to 1.0.
"""
import numpy as np
from nsynth import *

TINK_TAKES = [dict(f0=2400.0), dict(f0=2650.0), dict(f0=2180.0)]


def tink(take, r):
    """Strike (high-passed 4 kHz) + glass modes at f0 x [1, 2.7, 5.1, 8.2] with saline-damped decays + a tiny knock
    of the jar's base (900-3200 Hz noise, 4 ms); everything under 700 Hz removed."""
    p = TINK_TAKES[take - 1]
    b = buf(0.55)
    place(b, strike(r, 4000), 0.004, 0.45)
    place(b, norm(glass_modes(r, p['f0'], decays=(0.060, 0.040, 0.022, 0.013))), 0.004, 0.60)
    place(b, norm(noise_burst(900, 3200, 0.004, r)), 0.004, 0.22)
    return highpass(b, 700, 4)


GLUG_TAKES = [dict(n=7, rate=14.0, first_r=5.5), dict(n=9, rate=18.0, first_r=6.0)]


def glug(take, r):
    """A burp of bubbles: one big bubble first, then a train of smaller ones, pitch rising and level falling as they
    reach the top. Radius 2.5-5 mm (650-1300 Hz), decay about 12 ms x sqrt(1 kHz / f), rise 12-20 %.
    A low surface 'blub' (low-passed noise, 25 ms) under the first bubble; everything low-passed at 4 kHz."""
    p = GLUG_TAKES[take - 1]
    b = buf(0.85)
    at = 0.004
    place(b, norm(bandpass(noise(0.2, r), 250, 1000) * exp_env(0.2, 0.025, 0.003)), at, 0.18)
    for k in range(p['n']):
        rad = p['first_r'] if k == 0 else r.uniform(2.5, 5.0) * (1 - 0.03 * k)
        f = 3.26 / (rad / 1000)
        tau = 0.012 * np.sqrt(1000 / f)
        lvl = (1.0 if k == 0 else r.uniform(0.45, 0.8)) * (1 - 0.06 * k)
        place(b, bubble(r, f, tau, rise=r.uniform(0.12, 0.20)), at, lvl)
        at += float(np.clip(r.exponential(1 / p['rate']), 0.03, 0.12))
    return lowpass(b, 4000)


SOUNDS = [
    dict(id='jar.tink', file='jar/tink', fn=tink, takes=3, target=-26.5, cls='glass clink', kind='latch', fade_ms=15,
         hit_ms=4,
         ref=dict(event='block.lantern.place', file='block__lantern__place1.ogg', vol=1.0, pitch=0.88),
         volume=0.9, pitch=[0.94, 1.06], attenuation=8, fixed_range=False, category='PLAYERS',
         subtitle_en='Jar taps', subtitle_pt='Pote dá batidinhas', muffled=False,
         play='At the jar, when a jar with no hands nudges something (use or attack with nothing to act on). Random volume 0.8-1.0 and pitch 0.94-1.06 per play; debounce 4 ticks.'),
    dict(id='jar.glug', file='jar/glug', fn=glug, takes=2, target=-25.5, cls='glug', kind='glug', fade_ms=40,
         hit_ms=4,
         ref=dict(event='item.bottle.fill', file='item__bottle__fill2.ogg', vol=1.0, pitch=1.0),
         ref2=dict(event='item.bottle.fill', file='item__bottle__fill1.ogg', vol=1.0, pitch=1.0),
         layer=dict(id='jar.glug_texture', vanilla=['minecraft:block/bubble_column/bubble1', 'minecraft:block/bubble_column/bubble2', 'minecraft:block/bubble_column/bubble3'],
                    volume=0.5, pitch=[0.9, 1.1], subtitle=None,
                    note='Compound layer, no subtitle: play in the same tick as jar.glug, from the same position.'),
         volume=1.0, pitch=[0.95, 1.05], attenuation=8, fixed_range=False, category='PLAYERS',
         subtitle_en='Jar gurgles', subtitle_pt='Pote borbulha', muffled=False,
         play='At the jar, when the bare jar tries an ability it does not have, together with feedback.refused (glug first, refused 2 ticks later) and the action-bar reason. Debounce 20 ticks.'),
]

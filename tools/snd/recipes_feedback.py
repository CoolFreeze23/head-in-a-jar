"""The feedback family: four sounds the whole mod shares, so a refusal is always heard as a refusal.

All four are on the house voice (the FM 'vibraphone', carrier:modulator 1:3.5) and take their intervals from the
company motif C5 E5 G5 C6:
  done     G5 -> C6, a rising fourth (the motif's last two notes)
  refused  E5 -> Eb5, a falling semitone (the motif's E, let down), over a soft relay thock: the machine tried
  warning  C5 -> G4, a falling fourth an octave down (done turned upside down), slow tremolo: a throat cleared
  danger   A5 / F5 alternating, a major third (the motif's first interval), soft-square sustain under an FM strike
The FM bell voice is reserved for these, the jingles and the chair's countdown and ding; no machine uses it otherwise.
"""
import numpy as np
from nsynth import *

E5, EB5, C5, G4, D5, G5, C6, A5, F5 = 659.26, 622.25, 523.25, 392.00, 587.33, 783.99, 1046.50, 880.00, 698.46


def thock(r, scale=1.0):
    """A soft relay thock: a damped low-mid knock, no bright click (the refusal must never grate)."""
    k = mix(norm(modal(520 * scale, [1, 1.72, 2.9], [1, 0.4, 0.2], [0.012, 0.008, 0.004], r, 0.02)),
            0.6 * thunk(r, 210 * scale, 140 * scale, 0.015, 0.01),
            0.25 * norm(noise_burst(600, 2500, 0.002, r)))
    return norm(k)


REFUSED_TAKES = [dict(gap=0.090, i0=1.40, th=1.00), dict(gap=0.080, i0=0.95, th=0.90), dict(gap=0.100, i0=1.85, th=1.10)]


def refused(take, r):
    """'Nuh-uh': thock at 0, E5 at 4 ms (hold 40 ms, decay 25 ms), Eb5 a beat later, slightly longer and softer."""
    p = REFUSED_TAKES[take - 1]
    b = buf(0.40)
    place(b, thock(r, p['th']), 0.003, 0.42)
    place(b, fm_note(E5, 0.025, r, index=(p['i0'], 0.15), index_tau=0.02, attack=0.003, hold=0.040), 0.004, 1.0)
    place(b, fm_note(EB5, 0.032, r, index=(p['i0'] * 0.9, 0.15), index_tau=0.02, attack=0.004, hold=0.048),
          0.004 + p['gap'], 0.85)
    return lowpass(b, 5000)


DONE_TAKES = [dict(tau=0.120, i0=2.0), dict(tau=0.138, i0=1.6)]


def done(take, r):
    """Done chime: G5 then C6 150 ms apart on the house voice, overlapping."""
    p = DONE_TAKES[take - 1]
    b = buf(0.17 + 7 * p['tau'] * 1.1)
    place(b, fm_note(G5, p['tau'], r, index=(p['i0'], 0.3)), 0.003, 0.80)
    place(b, fm_note(C6, p['tau'] * 1.1, r, index=(p['i0'], 0.3)), 0.153, 1.0)
    return lowpass(b, 7000)


WARNING_TAKES = [dict(trem=3.0, tau=0.16), dict(trem=3.4, tau=0.18)]


def warning(take, r):
    """'Ahem': a soft C5 pickup, then a low G4 bong with a D5 partial, 15 ms attack, slow tremolo, long fade."""
    p = WARNING_TAKES[take - 1]
    b = buf(0.26 + 7 * p['tau'])
    place(b, fm_note(C5, 0.04, r, index=(0.6, 0.05), attack=0.003, hold=0.03), 0.002, 0.40)
    main = fm_note(G4, p['tau'], r, index=(0.8, 0.05), attack=0.015, hold=0.12, tremolo=(p['trem'], 0.25))
    part = fm_note(D5, p['tau'] * 0.8, r, index=(0.3, 0.05), attack=0.015, hold=0.10, tremolo=(p['trem'], 0.25))
    place(b, main, 0.11, 1.0)
    place(b, part, 0.11, 0.12)
    return lowpass(b, 1600)


def danger(take, r):
    """Two-tone alarm burst A5 F5 A5 F5, 0.25 s each: a soft square sustain (odd harmonics 1, 3, 5) with the house
    voice striking each tone, 5 ms crossfades between tones, the last strike left to ring 60 ms."""
    b = buf(1.45)
    seg = 0.25
    for k in range(4):
        f = A5 if k % 2 == 0 else F5
        t0 = 0.003 + k * seg
        n = int(round((seg + 0.005) * SR))
        t = np.arange(n) / SR
        sq = bandpass(osc_harm(np.full(n, f), 5, r, 1.0, odd_only=True), 300, 4000)
        env = np.clip(t / 0.005, 0, 1) * np.clip((seg + 0.005 - t) / 0.005, 0, 1)
        place(b, 0.55 * norm(sq) * env, t0)
        place(b, fm_note(f, 0.06 if k < 3 else 0.09, r, index=(2.0, 0.3)), t0, 0.5)
    return b


# the house refusal's sibling rule: never two refusals for one source within 10 ticks (see events.json notes)
SOUNDS = [
    dict(id='feedback.refused', file='feedback/refused', fn=refused, takes=3, target=-31.0, cls='tick',
         kind='latch', fade_ms=20, hit_ms=4,
         ref=dict(event='block.crafter.craft', file='block__crafter__craft.ogg', vol=1.0, pitch=1.0),
         ref2=dict(event='block.vault.insert_item_fail', file='block__vault__insert_fail.ogg', vol=1.0, pitch=1.0),
         volume=1.0, pitch=[1.0, 1.0], attenuation=8, fixed_range=False, category='source of the refusal (BLOCKS for a machine, PLAYERS for the jar or a form, NEUTRAL for a mecha)',
         subtitle_en='Noggin & Sons declines', subtitle_pt='Noggin & Sons lamenta',
         muffled=False,
         play='At the thing that refused, on the click that was refused. Debounce 10 ticks per player and per reason; the bare jar plays it on the first refused click in 3 s only, then the action-bar line alone. Fixed pitch: the interval is the message; the three takes give the variety.'),
    dict(id='feedback.done', file='feedback/done', fn=done, takes=2, target=-28.0, cls='done chime',
         kind='stinger', fade_ms=50, hit_ms=3,
         ref=dict(event='block.note_block.xylophone', file='note__xylobone.ogg', vol=1.0, pitch=1.0),
         volume=1.0, pitch=[1.0, 1.0], attenuation=8, fixed_range=False, category='BLOCKS for a machine, PLAYERS for the player\'s own action',
         subtitle_en='Noggin & Sons approves', subtitle_pt='Noggin & Sons aprova',
         muffled=False,
         play='When a machine finishes or an action is accepted. No random pitch (musical). Not more than once per 20 ticks per source.'),
    dict(id='feedback.warning', file='feedback/warning', fn=warning, takes=2, target=-28.0, cls='hum (warning)',
         kind='stinger', fade_ms=80, hit_ms=5,
         ref=dict(event='block.beacon.ambient', file='block__beacon__ambient.ogg', vol=0.9, pitch=1.0),
         volume=1.0, pitch=[1.0, 1.0], attenuation=8, fixed_range=False, category='PLAYERS',
         subtitle_en='Management coughs', subtitle_pt='A gerência pigarreia',
         muffled=False,
         play='To the player concerned only (send to that player, not to the level). Once a minute at most per cause; escalate by count (one ahem at the first threshold, two at the next, 12 ticks apart), then feedback.danger. No random pitch.'),
    dict(id='feedback.danger', file='feedback/danger', fn=danger, takes=1, target=-22.0, cls='alarm',
         kind='stinger', fade_ms=40, hit_ms=3,
         ref=dict(event='block.iron_door.close', file='block__iron_door__close2.ogg', vol=0.9, pitch=0.95),
         volume=1.0, pitch=[1.0, 1.0], attenuation=48, fixed_range=True, category='BLOCKS for a world alarm, PLAYERS for a personal one',
         subtitle_en='Alarm sounds', subtitle_pt='Alarme dispara',
         muffled=False,
         play='A 1.0 s burst (A F A F). While the condition holds, start it again every 25 ticks; it is not a loop file (it has fades). World alarms: register fixed-range 48 and play at volume 1. Personal alarms: to that player only, at volume 0.8, normal 16-block range. No random pitch.'),
]

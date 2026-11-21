"""The Redundancy Package: every clip, keyed in ticks, written into redundancy_package.bbmodel (or, with --compact,
redundancy_package_compact.bbmodel).

The Golden Parachute's eight clips under the same names and lengths (golden_parachute_anim.py, whose helpers this
borrows: droop, merge, the cord re-aiming and the writer), so the renderer's choice of clip, its timings (open on tick
20, `land` kept 40 ticks, `water` 60) and its sound and particle keys hold. What differs, and why:
- every offset is about half the parachute's (the canopy is half its size and rides 11.5 px over the swivel, not 22);
- `land` and `water` never drop the canopy onto the jar: the crown is 8 px across, narrower than the lid, so a canopy
  falling onto the jar would hang its panels through the lid and the glass. Instead it slides off to the right while
  still above the envelope (clear of it by a pixel at the closest tick), collapses and comes down flat beside the jar;
- the address label hangs at the back of the jar, so it swings in its own plane (rot z); it tips toward the glass
  (rot x) by 3.5 degrees at most (its foot is 5 px down and half a pixel from the glass).

    python art/entity/redundancy_package/redundancy_package_anim.py [--compact]                 # write the clips
    python art/entity/redundancy_package/redundancy_package_anim.py --model X --preview OUT     # baked preview copy

Signs (Blockbench, as displayed), as the parachute's: canopy / cloth rot z + = the left side down, rot x + = the front
up; a panel's "droop" is degrees below level (open 32, hanging 90); flap rot x + = open. Visibility only by step keys
on scale (0 or 1): nothing is scaled.
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(ART, 'entity', 'golden_parachute'))
sys.path.insert(0, os.path.join(ART, 'mecha', 'hopper'))
import redundancy_package_geo as G  # noqa: E402  (reads --compact itself)
import golden_parachute_anim as GA  # noqa: E402
from hopper_anim import k, R, P, snd  # noqa: E402

# the parachute's helpers, pointed at this geometry; keyframe UUIDs from a counter, so a rebuild is byte-identical
import hopper_anim as HA  # noqa: E402
import itertools  # noqa: E402
import uuid  # noqa: E402
_COUNT = itertools.count()


def _nid():
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'headinajar/%s/%d' % (G.NAME, next(_COUNT))))


HA.nid = GA.nid = _nid
GA.G = G
GA.OPEN = G.OPEN
show, droop, all_droop, merge = GA.show, GA.droop, GA.all_droop, GA.merge

MODEL = os.path.join(HERE, G.NAME + '.bbmodel')
OPEN = G.OPEN
PACK_Y = -(G.CROWN_Y - G.SWIVEL_TOP)          # the cloth's offset that sits the crown on the swivel
FLAT_Y = -(G.CROWN_Y - 0.5 - G.HEM)           # the cloth's offset that lays the flat canopy's valance on the ground
PILOT = 13.5                                  # the pilot chute's offset when open: its dome 5 px over the rosette
SIDE = 20.0                                   # where the flat canopy lies, right of the jar (3 px clear of its base)
HIDE_FLAP = {'flap': {'scale': show([(0, 0)])}}


def tag_swing(n, amp=6.0, lag=3):
    """The label sways +-amp in its own plane, lagging the canopy's sway by `lag` ticks, and tips a quarter as far."""
    return {'tag': {'rotation': [k(t, R(z=round(amp * math.sin(2 * math.pi * (t - lag) / n), 3),
                                        x=round(0.25 * amp * math.sin(2 * math.pi * (t - lag) / n), 3)), 'easeInOutSine')
                                 for t in range(0, n + 1, max(1, n // 8))]}}


# ==================================================================================================================
def deploy():
    """As the parachute's. 0-9 the jar rises with the envelope shut, the label trailing. The flap twitches (9), pops
    open (10) and is torn away (13). The pilot chute shoots out (11-15) and drags the canopy out as a streaming bundle,
    its panels pointing up, bursting 5 px clear of the envelope at once (11) so it never hangs over the jar, twisting 50
    degrees and back (11-19). The snap (20, the code's open): the panels slam down to 18 degrees of droop, the canopy
    yanks down 1.25 px and rebounds; it settles on an elastic by 28. Then descend."""
    flutter = [(t, -70 + (5 if t % 2 else -5)) for t in range(12, 19)]
    b = merge(
        {'cloth': {'scale': show([(0, 0), (11, 1)]),
                   'position': [k(0, P(y=PACK_Y)), k(10, P(y=PACK_Y)), k(11, P(y=PACK_Y + 5), 'easeOutExpo'),
                                k(12, P(y=PACK_Y + 4.75), 'easeOutQuad'), k(18, P(y=-0.5), 'easeOutCubic'), k(19, P()),
                                k(20, P(y=-1.25), 'easeInQuad'), k(22, P(y=0.5), 'easeOutQuad'),
                                k(24, P(y=-0.25), 'easeInOutSine'), k(28, P(), 'easeInOutSine')],
                   'rotation': [k(0, R()), k(11, R()), k(15, R(y=50, z=4), 'easeOutCubic'), k(19, R(y=-6, z=-2), 'easeInOutSine'),
                                k(20, R(z=-4), 'easeOutExpo'), k(28, R(), 'easeInElastic', [3])]},
         'cords': {'scale': show([(0, 0), (20, 1)])},
         'pilot': {'position': [k(0, P(y=-PACK_Y)), k(10, P(y=-PACK_Y)), k(11, P(y=-PACK_Y - 5), 'easeOutExpo'),
                                k(12, P(y=-PACK_Y - 5.25), 'easeOutQuad'), k(15, P(y=-PACK_Y + 15), 'easeOutCubic'),
                                k(19, P(y=20), 'easeInOutSine'), k(22, P(y=PILOT - 0.5), 'easeInOutSine'),
                                k(25, P(y=PILOT), 'easeInOutSine')]},
         'flap': {'rotation': [k(0, R()), k(9, R()), k(10, R(x=-4), 'easeOutQuad'), k(11, R(x=160), 'easeOutExpo'),
                               k(13, R(x=175), 'easeOutQuad')],
                  'scale': show([(0, 1), (13, 0)])},
         'tag': {'rotation': [k(0, R()), k(1, R(z=-25, x=-3), 'easeOutQuad'), k(8, R(z=-15, x=-2), 'easeInOutSine'),
                              k(14, R(z=-4), 'easeInOutSine'), k(20, R(z=14, x=2), 'easeOutQuad'),
                              k(24, R(z=-5), 'easeInOutSine'), k(30, R(), 'easeInOutSine')]}},
        all_droop([(0, -70), (11, -70)] + flutter + [(19, -70), (20, 18, 'easeOutExpo'), (28, OPEN, 'easeInElastic', [3])]))
    return {'length': 30, 'loop': 'hold', 'bones': b,
            'effects': [(10, 'sound', snd('envelope_pop', 0.5, 1.1), None), (12, 'sound', snd('pilot_whoosh', 0.5, 1.2), None),
                        (14, 'sound', snd('brocade_stream', 0.5, 1.0), None), (20, 'timeline', 'chute:snap', None),
                        (22, 'particle', 'gilt_flakes', 'fx_crown'), (26, 'sound', snd('tassel_jingle', 0.3, 1.1), None)]}


def descend():
    """Loop under the open canopy, as the parachute's: it breathes (front and back panels 6 degrees, the sides 5 ticks
    behind), bobs half a pixel, sways 4 degrees side to side and 2 fore and aft; the label sways a few ticks behind;
    the pilot chute bobs on its bridle. The code's swing with the drift goes on top, on `canopy`."""
    n = 40
    fb = [(t, OPEN + 6 * math.cos(2 * math.pi * t / n), 'easeInOutSine') for t in range(0, n + 1, 5)]
    lr = [(t, OPEN + 6 * math.cos(2 * math.pi * (t - 5) / n), 'easeInOutSine') for t in range(0, n + 1, 5)]
    b = merge(
        {'cloth': {'position': [k(t, P(y=round(0.5 * math.cos(2 * math.pi * t / n), 3)), 'easeInOutSine') for t in range(0, n + 1, 5)],
                   'rotation': [k(t, R(z=round(4 * math.sin(2 * math.pi * t / n), 3), x=round(2 * math.sin(4 * math.pi * t / n), 3)),
                                  'easeInOutSine') for t in range(0, n + 1, 5)]},
         'pilot': {'position': [k(t, P(y=round(PILOT + 0.4 * math.sin(2 * math.pi * (t - 8) / n), 3)), 'easeInOutSine')
                                for t in range(0, n + 1, 5)]}},
        HIDE_FLAP, droop({'f': fb, 'b': fb, 'l': lr, 'r': lr}), tag_swing(n))
    return {'length': n, 'loop': 'loop', 'bones': b,
            'effects': [(10, 'sound', snd('brocade_breathe', 0.2, 1.0), None), (30, 'sound', snd('brocade_breathe', 0.2, 0.95), None)]}


def steer(direction):
    """As the parachute's: a 3-degree wind-up the other way, a 12-degree bank into the turn, the inner panel folding up
    and fluttering, the outer one holding the dome; it holds banked 11."""
    s = 1 if direction == 'l' else -1
    inner, outer = ('l', 'r') if s > 0 else ('r', 'l')
    flut = [(t, 4 + (3 if t % 2 else 0)) for t in range(7, 13)]
    b = merge(
        {'cloth': {'rotation': [k(0, R()), k(2, R(z=-3 * s), 'easeOutQuad'), k(6, R(z=12 * s, x=-2), 'easeOutCubic'),
                                k(12, R(z=11 * s, x=-1.5), 'easeInOutSine')]},
         'pilot': {'position': [k(0, P(y=PILOT))]}},
        HIDE_FLAP,
        droop({inner: [(0, OPEN), (2, OPEN + 3, 'easeOutQuad'), (6, 4, 'easeOutCubic')] + flut,
               outer: [(0, OPEN), (2, OPEN - 2, 'easeOutQuad'), (6, 36, 'easeOutCubic'), (12, 34, 'easeInOutSine')],
               'f': [(0, OPEN), (6, 36, 'easeInOutSine')], 'b': [(0, OPEN), (6, 34, 'easeInOutSine')]}),
        tag_swing(12, 4, 2))
    return {'length': 12, 'loop': 'hold', 'bones': b,
            'effects': [(3, 'sound', snd('brocade_spill', 0.4, 1.0), None)]}


def flare():
    """Once, about 2 blocks above the ground, as the parachute's: a 3-degree dip of the front, then the canopy pitches
    back 16 and surges up 0.75 px, the back panel bellying down; it holds 3 ticks and settles over 7."""
    b = merge(
        {'cloth': {'rotation': [k(0, R()), k(2, R(x=-3), 'easeOutQuad'), k(6, R(x=16), 'easeOutCubic'),
                                k(9, R(x=15)), k(13, R(x=-2), 'easeInOutSine'), k(16, R(), 'easeInOutSine')],
                   'position': [k(0, P()), k(6, P(y=0.75), 'easeOutCubic'), k(9, P(y=0.75)), k(16, P(), 'easeInOutSine')]},
         'pilot': {'position': [k(0, P(y=PILOT))]}},
        HIDE_FLAP,
        droop({'f': [(0, OPEN), (2, 36, 'easeOutQuad'), (6, 18, 'easeOutCubic'), (9, 19), (13, 34, 'easeInOutSine'), (16, OPEN, 'easeInOutSine')],
               'b': [(0, OPEN), (2, 29, 'easeOutQuad'), (6, 48, 'easeOutCubic'), (9, 47), (13, 30, 'easeInOutSine'), (16, OPEN, 'easeInOutSine')],
               'l': [(0, OPEN), (6, 40, 'easeOutCubic'), (9, 40), (16, OPEN, 'easeInOutSine')],
               'r': [(0, OPEN), (6, 40, 'easeOutCubic'), (9, 40), (16, OPEN, 'easeInOutSine')]}),
        {'tag': {'rotation': [k(0, R()), k(6, R(z=-10, x=-2), 'easeOutCubic'), k(10, R(z=6), 'easeInOutSine'), k(16, R(), 'easeInOutSine')]}})
    return {'length': 16, 'loop': 'once', 'bones': b,
            'effects': [(2, 'sound', snd('cords_creak', 0.4, 1.0), None), (6, 'sound', snd('brocade_flare', 0.5, 1.0), None)]}


def land():
    """The jar touches down (0, the code's fold). The cords go slack (1) and the canopy lifts half a pixel (the
    wind-up), its panels already folding down; it drifts off to the right while still above the envelope (2-9, every
    edge clear of the lid's lugs before it comes below the envelope's top), tipping right, and falls beside the jar
    (9-14), the panels hanging, then spreading again one after another, 3 ticks each, as it nears the ground (8-14, the
    tassels folding under), so nothing passes below the ground at contact (14) and the dome never turns into a flat
    sheet in one step. Its edges then lift a little, unevenly, as cloth settles; the last panel slumps (17-19). The
    pilot chute drops onto the rosette, its bridle through the vent, and flops over away from the jar (18-21). At rest
    it is 3 px tall, 3 px clear of the jar's base. Stays for 40 ticks."""
    pos = [k(0, P()), k(2, P(y=0.5), 'easeOutQuad'), k(6, P(x=12, y=0), 'easeInOutSine'),
           k(9, P(x=SIDE, y=-7), 'easeInQuad'), k(14, P(x=SIDE, y=FLAT_Y), 'easeInQuad'),
           k(15, P(x=SIDE + 0.25, y=FLAT_Y + 0.3), 'easeOutQuad'), k(17, P(x=SIDE + 0.25, y=FLAT_Y), 'easeInQuad')]
    rot = [k(0, R()), k(2, R(z=3), 'easeOutQuad'), k(6, R(z=-4), 'easeInOutSine'), k(9, R(z=-22), 'easeInQuad'),
           k(14, R(), 'easeOutQuad')]
    hang = [(0, OPEN), (3, 50, 'easeInQuad'), (6, 80, 'easeInQuad')]
    lay = {'r': 8, 'f': 9, 'b': 10, 'l': 11}
    curl = {'r': -3, 'f': -5, 'b': -2, 'l': 0}       # at rest the cloth's edges lift a little, unevenly: not a board
    dr = {}
    for side, t0 in lay.items():
        dr[side] = hang + [(t0, 40, 'easeInQuad'), (t0 + 3, 0, 'easeInOutSine')]
        if side == 'l':
            dr[side] += [(17, 6, 'easeOutQuad'), (19, 0, 'easeInBounce', [0.4])]
        else:
            dr[side] += [(t0 + 10, curl[side], 'easeInOutSine')]
    b = merge(
        {'cloth': {'position': pos, 'rotation': rot}, 'cords': {'scale': show([(0, 1), (1, 0)])},
         'tassels_l': {'scale': show([(0, 1), (13, 0)])}, 'tassels_r': {'scale': show([(0, 1), (13, 0)])},
         'pilot': {'position': [k(0, P(y=PILOT)), k(6, P(y=PILOT - 1), 'easeInQuad'), k(14, P(y=9.5), 'easeInQuad'),
                                k(18, P(y=9.5)), k(21, P(x=2, y=12.5), 'easeInQuad')],
                   'rotation': [k(0, R()), k(18, R()), k(21, R(z=-80), 'easeInQuad')]},
         'tag': {'rotation': [k(0, R()), k(1, R(z=18), 'easeOutQuad'), k(5, R(z=-8), 'easeInOutSine'), k(9, R(z=3), 'easeInOutSine'),
                              k(13, R(), 'easeInOutSine')]}},
        HIDE_FLAP, droop(dr))
    return {'length': 40, 'loop': 'hold', 'bones': b,
            'effects': [(1, 'sound', snd('cords_slack', 0.3, 1.0), None), (5, 'sound', snd('brocade_rustle', 0.3, 1.1), None),
                        (8, 'sound', snd('brocade_slide', 0.4, 1.0), None), (14, 'sound', snd('brocade_flump', 0.4, 0.9), None),
                        (15, 'particle', 'gilt_flakes', 'fx_crown'), (18, 'sound', snd('tassel_jingle', 0.3, 0.9), None),
                        (30, 'sound', snd('sigh', 0.4, 1.0), None)]}


def water():
    """In water (the code folds on touching it): the cords go slack (1), the canopy drifts off to the right, clear of
    the envelope, and comes down flat on the surface beside the jar, tilted 4 degrees, the tassels gone under, the
    valance flat; it bobs half a pixel, and then sinks, slowly, the way gold brocade would: bubbles. Stays 60 ticks."""
    wy = FLAT_Y - 1
    b = merge(
        {'cloth': {'position': [k(0, P()), k(2, P(y=0.5), 'easeOutQuad'), k(5, P(x=12, y=-1), 'easeInOutSine'),
                                k(8, P(x=SIDE, y=-6), 'easeInOutSine'), k(10, P(x=SIDE, y=wy), 'easeInQuad'),
                                k(14, P(x=SIDE, y=wy - 0.5), 'easeOutQuad'), k(20, P(x=SIDE + 0.25, y=wy), 'easeInOutSine'),
                                k(26, P(x=SIDE + 0.25, y=wy - 0.5), 'easeInOutSine'), k(32, P(x=SIDE + 0.5, y=wy), 'easeInOutSine'),
                                k(58, P(x=SIDE + 1, y=wy - 11), 'easeInQuad')],
                   'rotation': [k(0, R()), k(10, R(z=-4, x=2), 'easeInOutSine'), k(20, R(z=-3, x=4), 'easeInOutSine'),
                                k(32, R(z=-5, x=2), 'easeInOutSine'), k(58, R(z=-14, x=6), 'easeInQuad')]},
         'cords': {'scale': show([(0, 1), (1, 0)])},
         'pilot': {'scale': show([(0, 1), (9, 0)]), 'position': [k(0, P(y=PILOT)), k(8, P(y=PILOT - 2), 'easeInQuad')]},
         'tassels_l': {'scale': show([(0, 1), (10, 0)])}, 'tassels_r': {'scale': show([(0, 1), (10, 0)])}},
        HIDE_FLAP, all_droop([(0, OPEN), (10, -2, 'easeInOutSine'), (14, 0, 'easeOutQuad'), (32, -1), (58, 12, 'easeInQuad')]),
        tag_swing(40, 5))
    return {'length': 60, 'loop': 'hold', 'bones': b,
            'effects': [(9, 'sound', snd('brocade_slap', 0.5, 1.0), None), (10, 'particle', 'splash', 'fx_crown'),
                        (22, 'particle', 'bubble', 'fx_crown'), (33, 'sound', snd('gold_sink', 0.4, 0.9), None),
                        (40, 'particle', 'bubble', 'fx_crown'), (52, 'particle', 'bubble', 'fx_crown')]}


def streamer():
    """Loop: the tangle (no room overhead), as the parachute's: out of the envelope but it will not open; the front
    panel flipped down through the lines while the other three point up, flapping; the bundle spinning 30 degrees a
    tick; the pilot chute whipping; the cords hidden in the tangle. Followed by `land` on contact."""
    n = 12
    b = merge(
        {'cloth': {'position': [k(0, P(y=-3)), k(6, P(y=-2.5), 'easeInOutSine'), k(12, P(y=-3), 'easeInOutSine')],
                   'rotation': [k(t, R(y=30 * t, z=(4 if t % 4 < 2 else -4))) for t in range(n + 1)]},
         'cords': {'scale': show([(0, 0)])},
         'pilot': {'position': [k(0, P(y=20)), k(3, P(x=1, y=20.5)), k(6, P(y=20)), k(9, P(x=-1, y=20.5)), k(12, P(y=20))]}},
        HIDE_FLAP,
        droop({'f': [(t, 150 + (6 if t % 2 else -6)) for t in range(n + 1)],
               'b': [(t, -66 + (-6 if t % 2 else 6)) for t in range(n + 1)],
               'l': [(t, -64 + (8 if t % 4 < 2 else -4)) for t in range(n + 1)],
               'r': [(t, -64 + (-4 if t % 4 < 2 else 8)) for t in range(n + 1)]}),
        tag_swing(n, 14, 1))
    return {'length': n, 'loop': 'loop', 'bones': b, 'effects': [(0, 'sound', snd('brocade_flap', 0.4, 1.1), None)]}


def clips():
    return [('deploy', deploy()), ('descend', descend()), ('steer_l', steer('l')), ('steer_r', steer('r')),
            ('flare', flare()), ('land', land()), ('water', water()), ('streamer', streamer())]


GA.clips = clips


def main():
    path = sys.argv[sys.argv.index('--model') + 1] if '--model' in sys.argv else MODEL
    with open(path, encoding='utf-8') as f:
        model = json.load(f)
    model['animations'] = GA.build_anims(model)
    if '--model' not in sys.argv:
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(model, f, indent=1)
    if '--preview' in sys.argv:
        out = sys.argv[sys.argv.index('--preview') + 1]
        prev = dict(model)
        prev['animations'] = GA.build_anims(model, baked=True)
        with open(out, 'w', encoding='utf-8') as f:
            json.dump(prev, f, indent=1)
    print('wrote %d clips into %s' % (len(model['animations']), os.path.basename(path)))


if __name__ == '__main__':
    main()

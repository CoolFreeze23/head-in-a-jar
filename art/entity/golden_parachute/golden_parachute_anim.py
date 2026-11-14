"""The Golden Parachute: every clip, keyed in ticks, written into golden_parachute.bbmodel.

Source of truth for the clips; hand edits to the bbmodel's animations are overwritten. The keyframe helpers and GeckoLib
4.9.3 easing curves are the Hopper's (hopper_anim.py). GeckoLib names its elastic and bounce easings backwards: a settle
after arriving is easeInElastic / easeInBounce.

    python art/entity/golden_parachute/golden_parachute_anim.py                        # write the clips
    python art/entity/golden_parachute/golden_parachute_anim.py --model X --preview OUT  # baked preview copy

Signs (Blockbench, as displayed): canopy / cloth rot z + = the left side down, rot x + = the front up; a panel's
"droop" is degrees below level (open = 20, hanging = 90; written to each panel's own axis by droop()); flap rot x + =
open; tag rot x + = swung forward. Visibility only by step keys on scale (0 or 1): nothing is scaled.
"""
import copy
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(ART, 'mecha', 'hopper'))
import golden_parachute_geo as G  # noqa: E402
from hopper_anim import k, R, P, S, value_at, keyframe, bake, snd, nid  # noqa: E402,F401

MODEL = os.path.join(HERE, 'golden_parachute.bbmodel')
T = 20.0
OPEN = G.OPEN
HANG = 90.0
PACK_Y = -(G.CROWN_Y - 22.0)        # the cloth's offset that sits the crown on the swivel (crown bottom at 22)


def show(on_ticks):
    return [k(t, S(1 if on else 0), interp='step') for t, on in on_ticks]


def droop(keys_by_side):
    """{side: [(t, droop, ease, args)]} -> panel rotation channels (relative to the open rest pose)."""
    out = {}
    for side, keys in keys_by_side.items():
        chan = []
        for key in keys:
            t, d = key[0], key[1]
            e = key[2] if len(key) > 2 else None
            a = key[3] if len(key) > 3 else None
            dd = d - OPEN
            v = {'f': R(x=-dd), 'b': R(x=dd), 'l': R(z=dd), 'r': R(z=-dd)}[side]
            chan.append(k(t, [round(x, 3) for x in v], e, a))
        out['panel_' + side] = {'rotation': chan}
    return out


def all_droop(keys):
    return droop({s: keys for s in 'fblr'})


def merge(*ds):
    out = {}
    for d in ds:
        for b, ch in d.items():
            out.setdefault(b, {}).update(ch)
    return out


def tag_swing(n, amp=6.0, lag=3):
    """The tag swings +-amp, lagging the canopy's sway by `lag` ticks (it hangs turned 20 degrees: its rest)."""
    return {'tag': {'rotation': [k(t, R(x=round(amp * math.sin(2 * math.pi * (t - lag) / n), 3),
                                        z=round(0.5 * amp * math.sin(2 * math.pi * (t - lag) / n), 3)), 'easeInOutSine')
                                 for t in range(0, n + 1, max(1, n // 8))]}}


HIDE_FLAP = {'flap': {'scale': show([(0, 0)])}}


# ==================================================================================================================
def deploy():
    """From the eject hand-over (the frame's eject clip tick 4: the jar is fired up and back). 0-9 the jar rises with
    the envelope shut, the tag trailing. The flap twitches (9), pops open (10) and is torn away (13). The pilot chute
    shoots out (11-15) and drags the canopy out of the envelope as a tall streaming bundle: its four panels pointing
    up (70 degrees above level), 16 px across and twice that tall, fluttering, bursting 10 px clear of the envelope at
    once (11) so it never hangs over the jar, twisting 50 degrees and back as it streams up (11-19). THE SNAP (20, the
    code's open): the panels slam down past the dome to 18 degrees of droop, the cords snap taut, the whole canopy
    yanks down 2.5 px and rebounds; it settles on an elastic by 28. Then descend."""
    flutter = [(t, -70 + (5 if t % 2 else -5)) for t in range(12, 19)]
    b = merge(
        {'cloth': {'scale': show([(0, 0), (11, 1)]),
                   'position': [k(0, P(y=PACK_Y)), k(10, P(y=PACK_Y)), k(11, P(y=PACK_Y + 10), 'easeOutExpo'),
                                k(12, P(y=PACK_Y + 9.5), 'easeOutQuad'), k(18, P(y=-1), 'easeOutCubic'), k(19, P()),
                                k(20, P(y=-2.5), 'easeInQuad'), k(22, P(y=1), 'easeOutQuad'), k(24, P(y=-0.5), 'easeInOutSine'),
                                k(28, P(), 'easeInOutSine')],
                   'rotation': [k(0, R()), k(11, R()), k(15, R(y=50, z=4), 'easeOutCubic'), k(19, R(y=-6, z=-2), 'easeInOutSine'),
                                k(20, R(z=-4), 'easeOutExpo'), k(28, R(), 'easeInElastic', [3])]},
         'cords': {'scale': show([(0, 0), (20, 1)])},
         'pilot': {'position': [k(0, P(y=-PACK_Y)), k(10, P(y=-PACK_Y)), k(11, P(y=-PACK_Y - 10), 'easeOutExpo'),
                                k(12, P(y=-PACK_Y - 10.5), 'easeOutQuad'), k(15, P(y=-PACK_Y + 30), 'easeOutCubic'),
                                k(19, P(y=40), 'easeInOutSine'), k(22, P(y=27), 'easeInOutSine'), k(25, P(y=28), 'easeInOutSine')]},
         'flap': {'rotation': [k(0, R()), k(9, R()), k(10, R(x=-4), 'easeOutQuad'), k(11, R(x=160), 'easeOutExpo'),
                               k(13, R(x=175), 'easeOutQuad')],
                  'scale': show([(0, 1), (13, 0)])},
         'tag': {'rotation': [k(0, R()), k(1, R(x=-25), 'easeOutQuad'), k(8, R(x=-15), 'easeInOutSine'),
                              k(14, R(x=-4), 'easeInOutSine'), k(20, R(x=14), 'easeOutQuad'), k(24, R(x=-5), 'easeInOutSine'),
                              k(30, R(), 'easeInOutSine')]}},
        all_droop([(0, -70), (11, -70)] + flutter + [(19, -70), (20, 18, 'easeOutExpo'), (28, OPEN, 'easeInElastic', [3])]))
    return {'length': 30, 'loop': 'hold', 'bones': b,
            'effects': [(10, 'sound', snd('envelope_pop', 0.5, 1.1), None), (12, 'sound', snd('pilot_whoosh', 0.5, 1.2), None),
                        (14, 'sound', snd('brocade_stream', 0.5, 1.0), None), (20, 'timeline', 'chute:snap', None),
                        (22, 'particle', 'gilt_flakes', 'fx_crown'), (26, 'sound', snd('tassel_jingle', 0.3, 1.1), None)]}


def descend():
    """Loop under the open canopy: it breathes (the front and back panels open and close 6 degrees, the sides 5 ticks
    behind), bobs 1 px, sways 4 degrees side to side and 2 fore and aft; the tag swings 6 degrees a few ticks behind;
    the pilot chute bobs on its bridle. The code's swing with the drift goes on top, on `canopy`."""
    n = 40
    fb = [(t, OPEN + 6 * math.cos(2 * math.pi * t / n), 'easeInOutSine') for t in range(0, n + 1, 5)]
    lr = [(t, OPEN + 6 * math.cos(2 * math.pi * (t - 5) / n), 'easeInOutSine') for t in range(0, n + 1, 5)]
    b = merge(
        {'cloth': {'position': [k(t, P(y=round(1.0 * math.cos(2 * math.pi * t / n), 3)), 'easeInOutSine') for t in range(0, n + 1, 5)],
                   'rotation': [k(t, R(z=round(4 * math.sin(2 * math.pi * t / n), 3), x=round(2 * math.sin(4 * math.pi * t / n), 3)),
                                  'easeInOutSine') for t in range(0, n + 1, 5)]},
         'pilot': {'position': [k(t, P(y=round(28 + 0.8 * math.sin(2 * math.pi * (t - 8) / n), 3)), 'easeInOutSine')
                                for t in range(0, n + 1, 5)]}},
        HIDE_FLAP, droop({'f': fb, 'b': fb, 'l': lr, 'r': lr}), tag_swing(n))
    return {'length': n, 'loop': 'loop', 'bones': b,
            'effects': [(10, 'sound', snd('brocade_breathe', 0.2, 1.0), None), (30, 'sound', snd('brocade_breathe', 0.2, 0.95), None)]}


def steer(direction):
    """Hold while the pilot steers (the code's drift swing banks it too): a 3-degree wind-up the other way, then it
    banks 12 into the turn and pitches 2 down; the inner side's panel folds up 28 degrees (to 4 below level) and
    flutters there, spilling air; the outer one holds the dome; it holds banked 11."""
    s = 1 if direction == 'l' else -1
    inner, outer = ('l', 'r') if s > 0 else ('r', 'l')
    flut = [(t, 4 + (3 if t % 2 else 0)) for t in range(7, 13)]
    b = merge(
        {'cloth': {'rotation': [k(0, R()), k(2, R(z=-3 * s), 'easeOutQuad'), k(6, R(z=12 * s, x=-2), 'easeOutCubic'),
                                k(12, R(z=11 * s, x=-1.5), 'easeInOutSine')]},
         'pilot': {'position': [k(0, P(y=28))]}},
        HIDE_FLAP,
        droop({inner: [(0, OPEN), (2, OPEN + 3, 'easeOutQuad'), (6, 4, 'easeOutCubic')] + flut,
               outer: [(0, OPEN), (2, OPEN - 2, 'easeOutQuad'), (6, 36, 'easeOutCubic'), (12, 34, 'easeInOutSine')],
               'f': [(0, OPEN), (6, 36, 'easeInOutSine')], 'b': [(0, OPEN), (6, 34, 'easeInOutSine')]}),
        tag_swing(12, 4, 2))
    return {'length': 12, 'loop': 'hold', 'bones': b,
            'effects': [(3, 'sound', snd('brocade_spill', 0.4, 1.0), None)]}


def flare():
    """Once, about 2 blocks above the ground: the brakes. A 3-degree dip of the front (wind-up), then the canopy
    pitches back 16 and surges up 1.5 px, the back panel bellying down to 48, the front lifting to 18, the sides
    pulling in to 40; it holds 3 ticks, then settles to the dome over 7 with a 2-degree overshoot."""
    b = merge(
        {'cloth': {'rotation': [k(0, R()), k(2, R(x=-3), 'easeOutQuad'), k(6, R(x=16), 'easeOutCubic'),
                                k(9, R(x=15)), k(13, R(x=-2), 'easeInOutSine'), k(16, R(), 'easeInOutSine')],
                   'position': [k(0, P()), k(6, P(y=1.5), 'easeOutCubic'), k(9, P(y=1.5)), k(16, P(), 'easeInOutSine')]},
         'pilot': {'position': [k(0, P(y=28))]}},
        HIDE_FLAP,
        droop({'f': [(0, OPEN), (2, 36, 'easeOutQuad'), (6, 18, 'easeOutCubic'), (9, 19), (13, 34, 'easeInOutSine'), (16, OPEN, 'easeInOutSine')],
               'b': [(0, OPEN), (2, 29, 'easeOutQuad'), (6, 48, 'easeOutCubic'), (9, 47), (13, 30, 'easeInOutSine'), (16, OPEN, 'easeInOutSine')],
               'l': [(0, OPEN), (6, 40, 'easeOutCubic'), (9, 40), (16, OPEN, 'easeInOutSine')],
               'r': [(0, OPEN), (6, 40, 'easeOutCubic'), (9, 40), (16, OPEN, 'easeInOutSine')]}),
        {'tag': {'rotation': [k(0, R()), k(6, R(x=-10), 'easeOutCubic'), k(10, R(x=6), 'easeInOutSine'), k(16, R(), 'easeInOutSine')]}})
    return {'length': 16, 'loop': 'once', 'bones': b,
            'effects': [(2, 'sound', snd('cords_creak', 0.4, 1.0), None), (6, 'sound', snd('brocade_flare', 0.5, 1.0), None)]}


FLAT_Y = -(G.CROWN_Y - 4.0)          # the cloth's offset that lays the flat canopy's valance on the ground


def land():
    """The jar touches down (0, the code's fold). The cords go slack (1). The canopy hangs a tick (wind-up), then
    falls onto the jar: the crown lands on the swivel (8) and the panels drop to hang round the lid like a lampshade
    (outside the glass), with a bounce. A beat (12-15). Then it is shrugged off: a tilt to the left (the wind-up, 17),
    it slides off to the right, tipping (19-24), and comes down flat on the ground beside the jar (contact 26), its
    panels laying out one after another 2 ticks apart (the tassels folding under), the last with a slump (32). At rest
    it is 4 px tall. The
    pilot chute flops over beside it. Stays for 40 ticks."""
    pos = [k(0, P()), k(2, P(y=1), 'easeOutQuad'), k(8, P(y=PACK_Y), 'easeInQuad'), k(10, P(y=PACK_Y + 0.7), 'easeOutQuad'),
           k(12, P(y=PACK_Y), 'easeInQuad'), k(17, P(y=PACK_Y)), k(19, P(x=1, y=PACK_Y)), k(21, P(x=7, y=PACK_Y + 1), 'easeInQuad'),
           k(24, P(x=20, y=FLAT_Y + 8), 'easeInQuad'), k(26, P(x=28, y=FLAT_Y), 'easeInQuad'), k(27, P(x=28.5, y=FLAT_Y + 0.6), 'easeOutQuad'),
           k(29, P(x=28.5, y=FLAT_Y), 'easeInQuad')]
    rot = [k(0, R()), k(15, R()), k(17, R(z=5), 'easeOutQuad'), k(19, R(z=2), 'easeInOutSine'), k(21, R(z=-25), 'easeInQuad'),
           k(24, R(z=-30), 'easeInOutSine'), k(26, R(z=-3), 'easeInQuad'), k(29, R(), 'easeInOutSine')]
    hang = [(0, OPEN), (4, 44, 'easeInQuad'), (8, 92, 'easeInQuad'), (10, 84, 'easeOutQuad'), (12, 92, 'easeInQuad'), (22, 92)]
    lay = {'r': 24, 'f': 26, 'b': 28, 'l': 30}
    dr = {}
    for side, t0 in lay.items():
        dr[side] = hang + [(t0, 40, 'easeInQuad'), (t0 + 2, 0, 'easeInQuad')]
        if side == 'l':
            dr[side] += [(33, 6, 'easeOutQuad'), (35, 0, 'easeInBounce', [0.4])]
    b = merge(
        {'cloth': {'position': pos, 'rotation': rot}, 'cords': {'scale': show([(0, 1), (1, 0)])},
         'tassels_l': {'scale': show([(0, 1), (27, 0)])}, 'tassels_r': {'scale': show([(0, 1), (25, 0)])},
         'pilot': {'position': [k(0, P(y=28)), k(8, P(y=22), 'easeInQuad'), k(24, P(y=22)), k(28, P(x=-6, y=22.5), 'easeInQuad')],
                   'rotation': [k(0, R()), k(24, R()), k(28, R(z=80), 'easeInQuad')]},
         'tag': {'rotation': [k(0, R()), k(1, R(x=18), 'easeOutQuad'), k(5, R(x=-8), 'easeInOutSine'), k(9, R(x=3), 'easeInOutSine'),
                              k(13, R(), 'easeInOutSine')]}},
        HIDE_FLAP, droop(dr))
    return {'length': 40, 'loop': 'hold', 'bones': b,
            'effects': [(1, 'sound', snd('cords_slack', 0.3, 1.0), None), (8, 'sound', snd('brocade_flump', 0.5, 1.0), None),
                        (12, 'sound', snd('tassel_jingle', 0.3, 0.9), None), (17, 'sound', snd('brocade_rustle', 0.3, 1.1), None),
                        (20, 'sound', snd('brocade_slide', 0.4, 1.0), None), (26, 'sound', snd('brocade_flump', 0.4, 0.9), None),
                        (27, 'particle', 'gilt_flakes', 'fx_crown'), (36, 'sound', snd('sigh', 0.4, 1.0), None)]}


def water():
    """In water (the code folds on touching it): the cords go slack (1), the canopy drifts off to the right and comes
    down flat on the surface beside the jar, tilted 4 degrees, the tassels gone under (hidden), the valance flat; it
    bobs 1 px, and then sinks, slowly, the way gold brocade would: bubbles. Stays 60 ticks."""
    wy = FLAT_Y - 2
    b = merge(
        {'cloth': {'position': [k(0, P()), k(2, P(y=1), 'easeOutQuad'), k(6, P(x=24, y=-8), 'easeInOutSine'),
                                k(10, P(x=28, y=wy), 'easeInQuad'), k(14, P(x=28, y=wy - 1), 'easeOutQuad'),
                                k(20, P(x=28.5, y=wy), 'easeInOutSine'), k(26, P(x=28.5, y=wy - 1), 'easeInOutSine'),
                                k(32, P(x=29, y=wy), 'easeInOutSine'), k(58, P(x=30, y=wy - 22), 'easeInQuad')],
                   'rotation': [k(0, R()), k(10, R(z=-4, x=2), 'easeInOutSine'), k(20, R(z=-3, x=4), 'easeInOutSine'),
                                k(32, R(z=-5, x=2), 'easeInOutSine'), k(58, R(z=-14, x=6), 'easeInQuad')]},
         'cords': {'scale': show([(0, 1), (1, 0)])}, 'pilot': {'scale': show([(0, 1), (9, 0)])},
         'tassels_l': {'scale': show([(0, 1), (10, 0)])}, 'tassels_r': {'scale': show([(0, 1), (10, 0)])}},
        HIDE_FLAP, all_droop([(0, OPEN), (10, -2, 'easeInOutSine'), (14, 0, 'easeOutQuad'), (32, -1), (58, 12, 'easeInQuad')]),
        tag_swing(40, 5))
    return {'length': 60, 'loop': 'hold', 'bones': b,
            'effects': [(9, 'sound', snd('brocade_slap', 0.5, 1.0), None), (10, 'particle', 'splash', 'fx_crown'),
                        (22, 'particle', 'bubble', 'fx_crown'), (33, 'sound', snd('gold_sink', 0.4, 0.9), None),
                        (40, 'particle', 'bubble', 'fx_crown'), (52, 'particle', 'bubble', 'fx_crown')]}


def streamer():
    """Loop: the tangled failure (a proposal: today's code has no too-low case; see the notes). Out of the envelope
    but it will not open: a line-over, the front panel flipped down through the lines (150 degrees) while the other
    three point up, flapping; the whole bundle spinning 30 degrees a tick; the pilot chute whipping; the cords hidden
    in the tangle. Followed by `land` on contact."""
    n = 12
    b = merge(
        {'cloth': {'position': [k(0, P(y=-6)), k(6, P(y=-5), 'easeInOutSine'), k(12, P(y=-6), 'easeInOutSine')],
                   'rotation': [k(t, R(y=30 * t, z=(4 if t % 4 < 2 else -4))) for t in range(n + 1)]},
         'cords': {'scale': show([(0, 0)])},
         'pilot': {'position': [k(0, P(y=40)), k(3, P(x=2, y=41)), k(6, P(y=40)), k(9, P(x=-2, y=41)), k(12, P(y=40))]}},
        HIDE_FLAP,
        droop({'f': [(t, 150 + (6 if t % 2 else -6)) for t in range(n + 1)],
               'b': [(t, -66 + (-6 if t % 2 else 6)) for t in range(n + 1)],
               'l': [(t, -64 + (8 if t % 4 < 2 else -4)) for t in range(n + 1)],
               'r': [(t, -64 + (-4 if t % 4 < 2 else 8)) for t in range(n + 1)]}),
        tag_swing(n, 14, 1))
    return {'length': n, 'loop': 'loop', 'bones': b, 'effects': [(0, 'sound', snd('brocade_flap', 0.4, 1.1), None)]}


def cords_follow(clip):
    """Each cord's top stays on its panel's outer corner: from the front and back panels' droop on every tick, the cord's
    tilt and yaw are re-aimed (its length is fixed; it is shown only while the canopy is open)."""
    bones = clip['bones']

    def droop_at(side, t):
        ch = bones.get('panel_' + side, {}).get('rotation')
        if not ch:
            return OPEN
        v = value_at(sorted(ch, key=lambda x: x[0]), t)
        return OPEN - v[0] if side == 'f' else OPEN + v[0]
    for c, (sx, sz) in G.CORNERS.items():
        side = 'f' if sz < 0 else 'b'
        rest = G.cord_rot(sx, sz)
        keys = []
        for t in range(0, int(clip['length']) + 1):
            tilt, yaw = G.cord_aim(sx, sz, droop_at(side, t))
            keys.append(k(t, [round(tilt - rest[0], 3), round(yaw - rest[1], 3), 0]))
        bones.setdefault('cord_' + c, {})['rotation'] = keys


def clips():
    return [('deploy', deploy()), ('descend', descend()), ('steer_l', steer('l')), ('steer_r', steer('r')),
            ('flare', flare()), ('land', land()), ('water', water()), ('streamer', streamer())]


def build_anims(model, baked=False):
    groups = {g['name']: g['uuid'] for g in model['groups']}
    anims = []
    for name, clip in clips():
        clip = copy.deepcopy(clip)
        cords_follow(clip)
        animators = {}
        for bone, chans in clip['bones'].items():
            if bone not in groups:
                sys.exit('%s: no bone %s' % (name, bone))
            kfs = []
            for channel, keys in chans.items():
                keys = sorted(keys, key=lambda x: x[0])
                times = [x[0] for x in keys]
                if len(set(times)) != len(times):
                    sys.exit('%s/%s/%s: two keys on one tick' % (name, bone, channel))
                if baked:
                    keys = bake(keys)
                for (t, v, e, a, interp) in keys:
                    if t > clip['length'] or t < 0:
                        sys.exit('%s/%s: key out of range at %s' % (name, bone, t))
                    kfs.append(keyframe(channel, t, v, e, a, interp))
            animators[groups[bone]] = {'name': bone, 'type': 'bone', 'keyframes': kfs}
        effects, seen = [], set()
        for (t, channel, effect, locator) in sorted(clip['effects'], key=lambda x: x[0]):
            if t in seen:
                sys.exit('%s: two effects on tick %s' % (name, t))
            if t > clip['length']:
                sys.exit('%s: effect out of range at %s' % (name, t))
            seen.add(t)
            dp = {'script': effect} if channel == 'timeline' else dict({'effect': effect, 'file': ''},
                                                                         **({'locator': locator} if locator else {}))
            effects.append({'channel': channel, 'data_points': [dp], 'uuid': nid(), 'time': round(t / T, 4),
                            'color': -1, 'interpolation': 'linear'})
        if effects:
            animators['effects'] = {'name': 'Effects', 'type': 'effect', 'keyframes': effects}
        anims.append({'uuid': nid(), 'name': name, 'loop': clip['loop'], 'override': False,
                      'length': clip['length'] / T, 'snapping': 20, 'selected': False, 'animators': animators})
    return anims


def main():
    path = sys.argv[sys.argv.index('--model') + 1] if '--model' in sys.argv else MODEL
    with open(path, encoding='utf-8') as f:
        model = json.load(f)
    model['animations'] = build_anims(model)
    if '--model' not in sys.argv:
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(model, f, indent=1)
    if '--preview' in sys.argv:
        out = sys.argv[sys.argv.index('--preview') + 1]
        prev = copy.deepcopy(model)
        prev['animations'] = build_anims(model, baked=True)
        with open(out, 'w', encoding='utf-8') as f:
            json.dump(prev, f, indent=1)
    print('wrote %d clips' % len(model['animations']))


if __name__ == '__main__':
    main()

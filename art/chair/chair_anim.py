"""Separation Chair (DOLORES): every clip, keyed by server tick, written into separation_chair.bbmodel.

This file is the source of truth for the chair's animations: edit the tables here and run it again; hand edits to the
bbmodel's animations in Blockbench are overwritten by the next run. Geometry comes from chair_geo.py and is edited in
Blockbench; this script only replaces the "animations" array.

    python art/chair/chair_anim.py                 # write the clips into the bbmodel (sparse keys, GeckoLib easings)
    python art/chair/chair_anim.py --preview OUT   # also write a review copy with every eased segment baked into
                                                   # per-tick linear keys (GeckoLib 4.9.3's own curves), so contact
                                                   # sheets and sampled poses show the in-between spacing

Key tuples: (tick, value, easing, easing_args). The easing shapes the move ARRIVING at that key (GeckoLib rule).
GeckoLib 4.9.3: easeIn* of elastic/bounce is the settle (EasingType.java: easeOut(f) = 1 - f(1 - t), and the raw
elastic and bounce curves settle at their end), so settles here use easeInElastic / easeInBounce.
"""
import copy
import json
import math
import os
import sys
import uuid as uuidlib

HERE = os.path.dirname(os.path.abspath(__file__))
MODEL = os.path.join(HERE, 'separation_chair.bbmodel')
T = 20.0  # ticks per second


def k(t, v, e=None, a=None, interp='linear'):
    return (t, v, e, a, interp)


def R(x=0, y=0, z=0):
    return [x, y, z]


def S(s):
    return [s, s, s]


def lamp(on_ticks):
    """Step keys for a lamp: list of (tick, on)."""
    return [k(t, S(1 if on else 0), interp='step') for t, on in on_ticks]


# ------------------------------------------------------------------------------------------------------------------
# Lamps: each lamp is a dark lens (static) under a lit cap (lamp_* bone). The caps are toggled with step scale keys
# (0 = hidden, 1 = shown) purely as visibility; the renderer may instead hide the bone from the same table.
# ------------------------------------------------------------------------------------------------------------------
LAMPS_GREEN = {'lamp_green': {'scale': lamp([(0, 1)])}, 'lamp_amber': {'scale': lamp([(0, 0)])},
               'lamp_red': {'scale': lamp([(0, 0)])}}

# ------------------------------------------------------------------------------------------------------------------
# idle: 80 ticks, loop. The dryer hood sways 0.8 degrees, the ring breathes 0.5 px, the barrier breathes, the jaws
# twitch once. Lamp green steady.
# ------------------------------------------------------------------------------------------------------------------
IDLE = {
    'length': 80, 'loop': 'loop',
    'bones': dict({
        'boom': {'rotation': [k(0, R(y=0.8)), k(40, R(y=-0.8), 'easeInOutSine'), k(80, R(y=0.8), 'easeInOutSine')]},
        'scan_ring': {'position': [k(0, R()), k(40, R(y=0.5), 'easeInOutSine'), k(80, R(), 'easeInOutSine')]},
        'barrier': {'rotation': [k(0, R()), k(20, R(z=-0.6), 'easeInOutSine'), k(60, R(z=0.4), 'easeInOutSine'),
                                 k(80, R(), 'easeInOutSine')]},
        'claw_jaw_l': {'rotation': [k(0, R()), k(50, R()), k(52, R(z=4), 'easeOutQuad'),
                                    k(56, R(), 'easeOutBack', [0.5])]},
        'claw_jaw_r': {'rotation': [k(0, R()), k(50, R()), k(52, R(z=-4), 'easeOutQuad'),
                                    k(56, R(), 'easeOutBack', [0.5])]},
        'scan_plane': {'scale': lamp([(0, 0)])},
    }, **LAMPS_GREEN),
    'effects': [
        (52, 'sound', 'headinajar:chair.servo_tick|0.5|1.4', None),
    ],
}

# ------------------------------------------------------------------------------------------------------------------
# ready: 40 ticks, loop. Patient seated, Form 27-B unsigned. The barrier taps twice like a waiting clerk; the amber
# lamp is on 20 ticks, off 20 (one flash per 40 ticks); the jaws flex.
# ------------------------------------------------------------------------------------------------------------------
READY = {
    'length': 40, 'loop': 'loop',
    'bones': {
        'barrier': {'rotation': [k(0, R()), k(4, R(z=5), 'easeOutCubic'), k(8, R(), 'easeInQuad'),
                                 k(12, R(z=4), 'easeOutCubic'), k(16, R(), 'easeInQuad'), k(40, R())]},
        'boom': {'rotation': [k(0, R(y=0.6)), k(20, R(y=-0.6), 'easeInOutSine'), k(40, R(y=0.6), 'easeInOutSine')]},
        'claw_jaw_l': {'rotation': [k(0, R()), k(24, R()), k(28, R(z=5), 'easeInOutSine'), k(34, R(), 'easeInOutSine')]},
        'claw_jaw_r': {'rotation': [k(0, R()), k(24, R()), k(28, R(z=-5), 'easeInOutSine'), k(34, R(), 'easeInOutSine')]},
        'lamp_green': {'scale': lamp([(0, 0)])},
        'lamp_amber': {'scale': lamp([(0, 1), (20, 0)])},
        'lamp_red': {'scale': lamp([(0, 0)])},
        'scan_plane': {'scale': lamp([(0, 0)])},
    },
    'effects': [
        (8, 'sound', 'headinajar:chair.tap|1.0|1.0', None),
        (16, 'sound', 'headinajar:chair.tap|1.0|1.0', None),
    ],
}

# ------------------------------------------------------------------------------------------------------------------
# The dock tray has its own controller ("tray"): out when a jar is loaded, while a patient waits or the operation
# runs, and while the finished jar stands on it; in when the chair is empty. 14 ticks each, hold on the last frame.
# ------------------------------------------------------------------------------------------------------------------
TRAY_OUT = {
    'length': 14, 'loop': 'hold',
    'bones': {'jar_tray': {'position': [k(0, R()), k(2, R(x=0.5), 'easeOutQuad'), k(11, R(x=-18.4), 'easeOutCubic'),
                                        k(14, R(x=-18), 'easeInOutSine')]}},
    'effects': [(1, 'sound', 'headinajar:chair.drawer_out|1.0|1.0', None),
                (11, 'sound', 'headinajar:chair.drawer_stop|1.0|1.0', None)],
}
TRAY_IN = {
    'length': 14, 'loop': 'hold',
    'bones': {'jar_tray': {'position': [k(0, R(x=-18)), k(2, R(x=-18.5), 'easeOutQuad'),
                                        k(12, R(x=0.3), 'easeInOutCubic'), k(14, R(), 'easeInOutSine')]}},
    'effects': [(1, 'sound', 'headinajar:chair.drawer_in|1.0|1.0', None),
                (12, 'sound', 'headinajar:chair.drawer_stop|1.0|1.0', None)],
}


def operation(botch=False):
    """The 240-tick operation. Contact keys sit on the server ticks of the operation."""
    bones = {
        # Beat 1, restraint (0-20): the level-crossing barrier drops across the chest and latches on the mast.
        'barrier': {'rotation': [
            k(0, R()), k(3, R(z=-8), 'easeOutQuad'), k(5, R(z=-8)),
            k(13, R(z=90), 'easeInQuad'),                       # contact, server t=13 clank
            k(16, R(z=85), 'easeOutQuad'), k(19, R(z=90), 'easeInQuad'),   # bounce, server t=19 tap
            k(120, R(z=90)), k(121, R(z=91.5), 'easeOutQuad'), k(125, R(z=90), 'easeInOutSine'),  # jank at the cut
            k(234, R(z=90)), k(235, R(z=93), 'easeOutQuad'), k(236, R(z=93)),
            k(240, R(z=-3), 'easeInOutCubic')]},
        # Beat 2, gas (20-60): the dryer hood nods toward the face and breathes the gas down (server particles at
        # `gas`). Beat 5's swing (165-183) and the return (215-233) are on the same channel.
        'boom': {'rotation': [
            k(0, R()), k(20, R()), k(22, R(z=1), 'easeOutQuad'), k(28, R(z=-3), 'easeInOutSine'),
            k(56, R(z=-3)), k(61, R(), 'easeInOutSine'),
            k(165, R()), k(167, R(y=-5), 'easeOutQuad'), k(168, R(y=-5)),
            k(180, R(y=183), 'easeInOutSine'), k(183, R(y=180), 'easeInOutSine'),
            k(215, R(y=180)), k(217, R(y=184), 'easeOutQuad'), k(218, R(y=184)),
            k(229, R(y=-2), 'easeInOutSine'), k(233, R(), 'easeInOutSine')]},
        # Beat 3, scan (60-100): the ring slides down its rods to the mouth and back.
        'scan_ring': {'position': [
            k(0, R()), k(60, R()), k(64, R(y=1), 'easeOutSine'), k(68, R(y=-2), 'easeInSine'),
            k(90, R(y=-6.5)),                                     # linear on purpose: a scan is constant speed
            k(94, R(y=-7), 'easeOutQuad'), k(96, R(y=-7)),
            k(104, R(y=0.5), 'easeInOutCubic'), k(107, R(), 'easeInOutSine')]},
        'scan_plane': {'scale': lamp([(0, 0), (64, 1), (95, 0)])},
        # Beat 4, the cut (100-140): the blade winds up tilted so its flat shows over the right shoulder, holds, the
        # privacy curtain whips up just before the strike, the blade flattens and strikes on t=120, the head hops.
        'privacy_panel': {'rotation': [
            k(0, R()), k(106, R()), k(107, R(y=-5), 'easeOutQuad'),
            k(112, R(y=94), 'easeOutCubic'), k(114, R(y=90), 'easeInOutSine'),
            k(128, R(y=90)), k(134, R(), 'easeInOutSine'),
            k(220, R()), k(222, R(y=-5), 'easeOutQuad'), k(227, R(y=94), 'easeOutCubic'),
            k(229, R(y=90), 'easeInOutSine'), k(233, R(y=90)), k(238, R(), 'easeInOutSine')]},
        'blade_arm': {'rotation': [
            k(0, R()), k(104, R()), k(114, R(x=25, y=-15), 'easeInOutSine'), k(117, R(x=25, y=-15)),
            k(118, R(y=-13), 'easeOutQuad'),                      # flattens as it goes behind the curtain
            k(120, R(y=200), 'easeInExpo'),                       # passes the neck (90) at about 119.8
            k(123, R(y=212), 'easeOutCubic'), k(130, R(y=200), 'easeInElastic', [4]),
            k(132, R(y=200)), k(142, R(), 'easeInOutSine')]},
        # Beat 5, into the jar (140-219).
        'jar_lid': {'rotation': [
            k(0, R()), k(140, R()), k(141, R(z=2), 'easeOutQuad'), k(142, R(z=2)),
            k(148, R(z=98), 'easeOutCubic'), k(151, R(z=95), 'easeInOutSine'),
            k(209, R(z=95)), k(211, R(z=99), 'easeOutQuad'), k(212, R(z=99)),
            k(215, R(), 'easeInQuad'),                           # slams shut before the hood swings home, clink
            k(217, R(z=3), 'easeOutQuad'), k(219, R(), 'easeInQuad')]},
        'claw': {'position': [
            k(0, R()), k(140, R()), k(144, R(y=-0.5), 'easeInCubic'),
            k(195, R(y=-0.5)), k(198, R(), 'easeOutCubic')]},      # weight gone
        'claw_jaw_l': {'rotation': [
            k(0, R()), k(144, R()), k(148, R(z=9), 'easeInQuad'), k(150, R(z=8), 'easeOutQuad'),
            k(152, R(z=9), 'easeInOutSine'),
            k(195, R(z=9)), k(198, R(z=-2), 'easeOutCubic'), k(200, R(), 'easeInOutSine')]},
        'claw_jaw_r': {'rotation': [
            k(0, R()), k(144, R()), k(148, R(z=-9), 'easeInQuad'), k(150, R(z=-8), 'easeOutQuad'),
            k(152, R(z=-9), 'easeInOutSine'),
            k(195, R(z=-9)), k(198, R(z=2), 'easeOutCubic'), k(200, R(), 'easeInOutSine')]},
        # the telescoping lift: the rail rises out of the mast, the head comes 8 px clear of the neck and holds
        'boom_lift': {'position': [
            k(0, R()), k(150, R()), k(152, R(y=-0.5), 'easeOutQuad'), k(161, R(y=8.3), 'easeInOutCubic'),
            k(163, R(y=8), 'easeInOutSine'),
            k(183, R(y=8)), k(184, R(y=8.5), 'easeOutQuad'), k(193, R(y=-12.8), 'easeInOutCubic'),
            k(195, R(y=-12.5), 'easeInOutSine'),
            k(203, R(y=-12.5)), k(204, R(y=-13), 'easeOutQuad'), k(211, R(y=1.2), 'easeInOutCubic'),
            k(213, R(y=1), 'easeInOutSine'), k(228, R(y=1)), k(233, R(), 'easeInOutSine')]},
        'head_carry': {
            'rotation': [k(0, R()), k(167, R()), k(170, R(x=4), 'easeOutQuad'), k(176, R(x=-3), 'easeInOutSine'),
                         k(182, R(x=2), 'easeInOutSine'), k(186, R(), 'easeInOutSine')],
            'position': [k(0, R()), k(120, R()), k(122, R(y=1), 'easeOutQuad'), k(125, R(), 'easeInQuad'),  # the hop
                         k(195, R()), k(198, R(y=-3.25), 'easeInQuad'),                                   # the drop
                         k(200, R(y=-2.75), 'easeOutQuad'), k(202, R(y=-3.25), 'easeInQuad')]},
        'lamp_green': {'scale': lamp([(0, 1), (14, 0), (230, 1)])},
        'lamp_amber': {'scale': lamp([(0, 0), (14, 1), (40, 0), (43, 1), (47, 0), (50, 1), (54, 0), (57, 1), (96, 0)])},
        'lamp_red': {'scale': lamp([(0, 0), (96, 1), (230, 0)])},
    }
    effects = [
        (21, 'sound', 'headinajar:chair.hood_nod|1.0|0.857', None),
        (40, 'sound', 'headinajar:chair.beep|1.0|1.0', None),
        (47, 'sound', 'headinajar:chair.beep|1.0|0.8909', None),
        (54, 'sound', 'headinajar:chair.beep|1.0|0.7937', None),
        (56, 'sound', 'headinajar:chair.hood_nod|1.0|1.2', None),
        (68, 'sound', 'headinajar:chair.servo_tick|0.6|1.3', None),
        (74, 'sound', 'headinajar:chair.servo_tick|0.6|1.3', None),
        (80, 'sound', 'headinajar:chair.servo_tick|0.6|1.3', None),
        (86, 'sound', 'headinajar:chair.servo_tick|0.6|1.3', None),
        (94, 'sound', 'headinajar:chair.beep|1.0|1.1892', None),
        (100, 'sound', 'headinajar:chair.latch|1.0|1.0', None),
        (104, 'sound', 'headinajar:chair.whine_rise|1.0|1.0', None),
        (107, 'sound', 'headinajar:chair.curtain|1.0|1.0', None),
        (122, 'sound', 'headinajar:chair.plonk|1.0|1.0', None),
        (128, 'sound', 'headinajar:chair.ding|1.0|1.0', None),
        (141, 'sound', 'headinajar:chair.lid_pop|1.0|1.0', None),
        (148, 'sound', 'headinajar:chair.clamp|1.0|1.0', None),
        (152, 'sound', 'headinajar:chair.hydraulic|1.0|1.0', None),
        (166, 'sound', 'headinajar:chair.servo|1.0|1.0', None),
        (184, 'sound', 'headinajar:chair.hydraulic|1.0|1.0', None),
        (198, 'sound', 'headinajar:chair.plop|1.0|1.0', None),
        (199, 'particle', 'falling_water', 'pour'),
        (200, 'timeline', 'fx:bubbles:jar_anchor:4', None),
        (204, 'sound', 'headinajar:chair.hydraulic_short|1.0|1.0', None),
        (212, 'sound', 'headinajar:chair.servo_tick|0.5|1.0', None),
        (215, 'sound', 'headinajar:chair.clink|1.0|1.0', None),
        (218, 'sound', 'headinajar:chair.servo_home|1.0|1.0', None),
        (221, 'particle', 'electric_spark', 'spark'),
        (222, 'sound', 'headinajar:chair.curtain|1.0|1.0', None),
        (228, 'particle', 'electric_spark', 'spark'),
        (230, 'sound', 'headinajar:chair.ding|1.0|1.2599', None),
        (234, 'sound', 'headinajar:chair.unlatch|1.0|1.0', None),
        (235, 'particle', 'electric_spark', 'spark'),
    ]
    if botch:
        # "Oopsie": the hood hiccups 12 degrees back on its swing and holds 2 ticks; the head somersaults forward
        # between the jaws (about the temples) and goes into the jar upside down.
        rot = [x for x in bones['boom']['rotation'] if not (165 <= x[0] <= 183)] + [
            k(165, R()), k(167, R(y=-5), 'easeOutQuad'), k(168, R(y=-5)),
            k(172, R(y=62), 'easeInQuad'), k(174, R(y=50), 'easeOutQuad'), k(176, R(y=50)),   # hiccup and hold
            k(182, R(y=183), 'easeInOutSine'), k(185, R(y=180), 'easeInOutSine')]
        bones['boom']['rotation'] = rot
        bones['head_carry']['rotation'] = [
            k(0, R()), k(174, R()), k(176, R(x=-30), 'easeInQuad'), k(177, R(x=-24), 'easeOutQuad'),
            k(181, R(x=-180), 'easeInOutCubic'), k(183, R(x=-172), 'easeOutQuad'), k(186, R(x=-180), 'easeInOutSine')]
        effects = effects + [
            (174, 'sound', 'headinajar:chair.servo_hiccup|1.0|1.0', None),
            (177, 'sound', 'headinajar:chair.oopsie_squeak|1.0|1.0', None),
        ]
    return {'length': 240, 'loop': 'hold', 'bones': bones, 'effects': sorted(effects)}


# ------------------------------------------------------------------------------------------------------------------
# abort: 16 ticks, once. Started with a 10-tick controller transition from wherever the operation was (t < 100); the
# clip itself starts from the rest pose and springs past it, like something let go of.
# ------------------------------------------------------------------------------------------------------------------
ABORT = {
    'length': 16, 'loop': 'once',
    'bones': {
        'barrier': {'rotation': [k(0, R()), k(4, R(z=-6), 'easeOutCubic'), k(9, R(z=2), 'easeInOutSine'),
                                 k(14, R(), 'easeInOutSine')]},
        'boom': {'rotation': [k(0, R()), k(5, R(y=2), 'easeOutQuad'), k(10, R(y=-1), 'easeInOutSine'),
                              k(16, R(), 'easeInOutSine')]},
        'scan_ring': {'position': [k(0, R()), k(4, R(y=0.5), 'easeOutQuad'), k(8, R(), 'easeInQuad')]},
        'lamp_green': {'scale': lamp([(0, 0), (8, 1)])},
        'lamp_amber': {'scale': lamp([(0, 1), (8, 0)])},
        'lamp_red': {'scale': lamp([(0, 0)])},
        'scan_plane': {'scale': lamp([(0, 0)])},
    },
    'effects': [
        (4, 'sound', 'headinajar:chair.deflate|1.0|1.0', None),
    ],
}

# ------------------------------------------------------------------------------------------------------------------
# aftercare: 30 ticks, once. From the operation's last frame back to idle: the barrier settles, the vents puff, and the
# hood makes one small bow toward the seat (the machine thanks you for your custom).
# ------------------------------------------------------------------------------------------------------------------
AFTERCARE = {
    'length': 30, 'loop': 'once',
    'bones': dict({
        'barrier': {'rotation': [k(0, R(z=-3)), k(6, R(), 'easeInOutSine')]},
        'boom': {'rotation': [k(0, R()), k(6, R()), k(9, R(z=1), 'easeOutQuad'), k(14, R(z=-3), 'easeInOutSine'),
                              k(18, R(z=-3)), k(26, R(), 'easeInOutSine'), k(30, R(y=0.8), 'easeInOutSine')]},
        'scan_plane': {'scale': lamp([(0, 0)])},
    }, **LAMPS_GREEN),
    'effects': [
        (1, 'sound', 'headinajar:chair.hiss|1.0|1.0', None),
        (2, 'particle', 'cloud', 'vent_l'),
        (3, 'particle', 'cloud', 'vent_r'),
        (14, 'sound', 'headinajar:chair.bow_chime|1.0|1.0', None),
    ],
}

CLIPS = [('idle', IDLE), ('ready', READY), ('operation', operation(False)), ('botch', operation(True)),
         ('abort', ABORT), ('aftercare', AFTERCARE), ('tray_out', TRAY_OUT), ('tray_in', TRAY_IN)]


# ------------------------------------------------------------------------------------------------------------------
# GeckoLib 4.9.3 easing curves (EasingType.java), for the baked review copy
# ------------------------------------------------------------------------------------------------------------------
def _base(name, arg):
    n = name.lower()
    fam = n.replace('easeinout', '').replace('easein', '').replace('easeout', '')
    if fam == 'sine':
        f = lambda t: 1 - math.cos(t * math.pi / 2)
    elif fam == 'quad':
        f = lambda t: t * t
    elif fam == 'cubic':
        f = lambda t: t ** 3
    elif fam == 'quart':
        f = lambda t: t ** 4
    elif fam == 'quint':
        f = lambda t: t ** 5
    elif fam == 'expo':
        f = lambda t: 2 ** (10 * (t - 1))
    elif fam == 'circ':
        f = lambda t: 1 - math.sqrt(max(0.0, 1 - t * t))
    elif fam == 'back':
        n2 = 1.70158 if arg is None else arg * 1.70158
        f = lambda t: t * t * ((n2 + 1) * t - n2)
    elif fam == 'elastic':
        n2 = 1 if arg is None else arg
        f = lambda t: 1 - math.cos(t * math.pi / 2) ** 3 * math.cos(t * n2 * math.pi)
    elif fam == 'bounce':
        b = 0.5 if arg is None else arg
        f = lambda x: min(121 / 16 * x * x, 121 / 4 * b * (x - 6 / 11) ** 2 + 1 - b,
                          121 * b * b * (x - 9 / 11) ** 2 + 1 - b * b,
                          484 * b ** 3 * (x - 10.5 / 11) ** 2 + 1 - b ** 3)
    else:
        raise ValueError(name)
    if n.startswith('easeinout'):
        return lambda t: f(t * 2) / 2 if t < 0.5 else 1 - f((1 - t) * 2) / 2
    if n.startswith('easeout'):
        return lambda t: 1 - f(1 - t)
    return f


def ease(name, args, t):
    if not name or name.lower() in ('linear', 'none'):
        return t
    return _base(name, args[0] if args else None)(t)


# ------------------------------------------------------------------------------------------------------------------
# bbmodel writing
# ------------------------------------------------------------------------------------------------------------------
def nid():
    return str(uuidlib.uuid4())


def keyframe(channel, t, v, e, a, interp):
    kf = {'channel': channel, 'data_points': [{'x': v[0], 'y': v[1], 'z': v[2]}], 'uuid': nid(),
          'time': round(t / T, 4), 'color': -1, 'interpolation': interp}
    if e and interp == 'linear' and e.lower() != 'linear':
        kf['easing'] = e
        if a is not None:
            kf['easingArgs'] = a
    return kf


def bake(keys):
    """Per-tick linear keys reproducing GeckoLib's eased segments (review copy only)."""
    out = []
    for i, (t, v, e, a, interp) in enumerate(keys):
        if i == 0:
            out.append((t, v, None, None, interp))
            continue
        t0, v0 = keys[i - 1][0], keys[i - 1][1]
        if keys[i - 1][4] == 'step' or interp == 'step':
            out.append((t, v, None, None, interp))
            continue
        for tt in range(int(t0) + 1, int(t) + 1):
            f = ease(e, a, (tt - t0) / float(t - t0))
            out.append((tt, [v0[j] + (v[j] - v0[j]) * f for j in range(3)], None, None, 'linear'))
    return out


def build(model, baked=False):
    groups = {g['name']: g['uuid'] for g in model['groups']}
    anims = []
    for name, clip in CLIPS:
        animators = {}
        for bone, chans in clip['bones'].items():
            kfs = []
            for channel, keys in chans.items():
                keys = sorted(keys, key=lambda x: x[0])
                times = [x[0] for x in keys]
                if len(set(times)) != len(times):
                    sys.exit('%s/%s/%s: two keys on one tick' % (name, bone, channel))
                if baked:
                    keys = bake(keys)
                for (t, v, e, a, interp) in keys:
                    if t > clip['length']:
                        sys.exit('%s/%s: key past the end' % (name, bone))
                    kfs.append(keyframe(channel, t, v, e, a, interp))
            animators[groups[bone]] = {'name': bone, 'type': 'bone', 'keyframes': kfs}
        effects = []
        seen = set()
        for (t, channel, effect, locator) in clip['effects']:
            if t in seen:
                sys.exit('%s: two effects on tick %s' % (name, t))
            seen.add(t)
            if channel == 'timeline':
                dp = {'script': effect}
            else:
                dp = {'effect': effect, 'file': ''}
                if locator:
                    dp['locator'] = locator
            effects.append({'channel': channel, 'data_points': [dp], 'uuid': nid(), 'time': round(t / T, 4),
                            'color': -1, 'interpolation': 'linear'})
        if baked and name in ('ready', 'operation', 'botch', 'abort', 'aftercare'):
            # review copy only: with a jar loaded the tray controller holds the tray out
            animators[groups['jar_tray']] = {'name': 'jar_tray', 'type': 'bone', 'keyframes': [
                keyframe('position', 0, R(x=-18), None, None, 'linear')]}
        if baked and name in ('operation', 'botch'):
            # review copy only: the stand-in head stops being drawn on the claw once the jar has it (the game
            # switches to drawing the head in the jar at t=202)
            animators[groups['standin_head']] = {'name': 'standin_head', 'type': 'bone', 'keyframes': [
                keyframe('scale', 0, S(1), None, None, 'step'), keyframe('scale', 202, S(0), None, None, 'step')]}
        if effects:
            animators['effects'] = {'name': 'Effects', 'type': 'effect', 'keyframes': effects}
        anims.append({'uuid': nid(), 'name': name, 'loop': clip['loop'], 'override': False,
                      'length': clip['length'] / T, 'snapping': 20, 'selected': False, 'animators': animators})
    return anims


def main():
    with open(MODEL, encoding='utf-8') as f:
        model = json.load(f)
    model['animations'] = build(model)
    with open(MODEL, 'w', encoding='utf-8') as f:
        json.dump(model, f, indent=1)
    if '--preview' in sys.argv:
        out = sys.argv[sys.argv.index('--preview') + 1]
        prev = copy.deepcopy(model)
        prev['animations'] = build(model, baked=True)
        with open(out, 'w', encoding='utf-8') as f:
            json.dump(prev, f, indent=1)
    print('wrote %d clips' % len(CLIPS))


if __name__ == '__main__':
    main()

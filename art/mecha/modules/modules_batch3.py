"""Module shapes, fourth and last batch: the ten torso modules left and the five core tells, each in
art/mecha/modules/<module>/ in the light and the heavy size class.

    python art/mecha/modules/modules_batch3.py            # writes <module>/<module>.bbmodel and <module>_heavy.bbmodel
    python art/mecha/modules/modules_batch3.py --check    # sizes and coplanar faces only

Same mount convention, helpers and size classes as modules_more.py and modules_batch2.py (imported).

Shared models (one geometry, a different face):
- canister(): Fire Suppression, Life Support Recycler, Coolant Loop and Medical Injector. A steel tank with a valve
  wheel on top and a hose with a nozzle down its right side; the face is the coloured band round the tank and the
  1 px symbol on it.
- energy_shield's projector: Stealth Field, with its own lens (dark glass with a violet pupil) and a slower ring.
- the Chimera's Gene Port vial pieces (socket ring, glass vial, serum, brass cap): Bio-Integration Port.
- core_tell(): the five cores. socket_core points down under the hips (-Z is down, +Y is forward), so the tell is a
  dark hatch under the hull with a 3 px housing below it, a round face on its front and an amber lamp on its back.
  The face is swapped per core; Combustion adds a stubby exhaust pipe.

Torso modules hang DOWN from their socket (nothing above y 1) and stay 6 px wide or less, like the third batch.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.dirname(HERE))
import bbbuild  # noqa: E402
import modules_more as M  # noqa: E402
import modules_batch2 as M3  # noqa: E402  (its materials: lamp_g, lamp_r, lamp_e)
from modules_more import C, k  # noqa: E402

M.MATERIALS.update({'red': ('c8372d', 255), 'white': ('eeeae0', 255), 'green': ('4f9a48', 255), 'blue': ('3d74c4', 255),
                    'lamp_v': ('b98cff', 255), 'lamp_o': ('ff7a1a', 255), 'serum': ('c060d0', 255), 'cell': ('1f2d4d', 255)})
small_plate = M3.small_plate
MODULES = {}


# ==================================================================================================================
# The canister (shared): Fire Suppression, Life Support Recycler, Coolant Loop, Medical Injector
# ==================================================================================================================
def canister(band, symbol, clips, motion):
    return dict(
        slot='TORSO', verb=list(clips)[0],
        bones=[('mount', [0, 0, 0], None, None), ('valve', [0, 0.5, -3], 'mount', None), ('nozzle', [2.5, -3, -3], 'mount', None)],
        cubes=[small_plate(4, 3, 1),
               C('tank', [-2, -11, -5], [2, -1, -1], 'steel', 'mount'),
               C('cap', [-1, -1, -4], [1, 0, -2], 'dsteel', 'mount'),
               C('band', [-2.5, -8, -5.5], [2.5, -4, -0.5], band, 'mount'),
               C('foot', [-2.5, -12, -5.5], [2.5, -11, -0.5], 'dsteel', 'mount'),
               C('wheel', [-2, 0, -5], [2, 1, -1], 'brass', 'valve'),
               C('hose', [2, -9, -3.5], [3, -3, -2.5], 'rubber', 'nozzle'),
               C('tip', [2, -11, -4], [3, -9, -2], 'dsteel', 'nozzle')] + symbol,
        locators=[('fx_nozzle', [2.5, -11, -3], 'nozzle')],
        clips=clips, motion=motion)


def face(name, f, t, mat):
    return C(name, f, t, mat, 'mount')


SPIN_VALVE = {'rotation': [k(0, [0, 0, 0]), k(4, [0, 180, 0]), k(8, [0, 360, 0])]}

MODULES['fire_suppression'] = canister(
    'red',
    [face('horn', [-0.5, -6, -6.5], [0.5, -4, -5.5], 'white'), face('horn_bell', [-1, -7, -6.5], [1, -6, -5.5], 'white'),
     face('horn_mouth', [-1.5, -8, -6.5], [1.5, -7, -5.5], 'white')],
    {'burst': {'length': 16, 'loop': 'once', 'bones': {
        'nozzle': {'rotation': [k(0, [0, 0, 0]), k(3, [70, 0, 0], 'easeOutCubic'), k(12, [70, 0, 0]), k(16, [0, 0, 0], 'easeInOutSine')]},
        'valve': SPIN_VALVE},
        'effects': [(3, 'timeline', 'fire_suppression:spray')]}},
    'a steel tank with a red band and a white extinguisher horn, belled at the mouth; it fires, the nozzle swings out 70 degrees and the valve wheel spins')

MODULES['life_support_recycler'] = canister(
    'green',
    [face('lamp', [-1, -7, -6.5], [1, -5, -5.5], 'lamp_g')],
    {'sustain': {'length': 40, 'loop': 'loop', 'bones': {
        'valve': {'rotation': [k(0, [0, 0, 0]), k(20, [0, 180, 0]), k(40, [0, 360, 0])]},
        'nozzle': {'rotation': [k(0, [0, 0, 0]), k(20, [0, 0, 6], 'easeInOutSine'), k(40, [0, 0, 0], 'easeInOutSine')]}}}},
    'the same tank with a green band and a green lamp; recycling, the valve turns slowly and the hose sways')

MODULES['coolant_loop'] = canister(
    'blue',
    [face('flake_c', [-0.5, -6.5, -6.5], [0.5, -5.5, -5.5], 'white')] +
    [face('flake_%d' % i, [x, y, -6.5], [x + 1, y + 1, -5.5], 'white') for i, (x, y) in enumerate(((-1.5, -5.5), (0.5, -5.5), (-1.5, -7.5), (0.5, -7.5)))],
    {'sustain': {'length': 20, 'loop': 'loop', 'bones': {
        'valve': {'rotation': [k(0, [0, 0, 0]), k(10, [0, -180, 0]), k(20, [0, -360, 0])]},
        'nozzle': {'position': [k(0, [0, 0, 0]), k(10, [0, 0.5, 0], 'easeInOutSine'), k(20, [0, 0, 0], 'easeInOutSine')]}}}},
    'the same tank with a blue band and a white snowflake; cooling, the valve turns back the other way and the hose throbs')

MODULES['medical_injector'] = canister(
    'white',
    [face('cross_v', [-0.5, -7.5, -6.5], [0.5, -4.5, -5.5], 'red'), face('cross_l', [-1.5, -6.5, -6.5], [-0.5, -5.5, -5.5], 'red'),
     face('cross_r', [0.5, -6.5, -6.5], [1.5, -5.5, -5.5], 'red')],
    {'burst': {'length': 12, 'loop': 'once', 'bones': {
        'nozzle': {'rotation': [k(0, [0, 0, 0]), k(3, [40, 0, 0], 'easeOutCubic'), k(5, [40, 0, 0]), k(12, [0, 0, 0], 'easeInOutSine')],
                   'position': [k(0, [0, 0, 0]), k(3, [0, 0, 0]), k(5, [0, -1.5, 0], 'easeOutExpo'), k(8, [0, 0, 0], 'easeInOutSine')]},
        'valve': {'rotation': [k(0, [0, 0, 0]), k(5, [0, 90, 0], 'easeOutCubic')]}},
        'effects': [(5, 'timeline', 'medical_injector:inject')]}},
    'the same tank with a white band and a red cross; when it doses, the nozzle swings out and plunges 1.5 px')

# ==================================================================================================================
# Signal Booster, Solar Skin, Tesla Coil, Workbench (each its own)
# ==================================================================================================================
MODULES['signal_booster'] = dict(
    slot='TORSO', verb='sustain',
    bones=[('mount', [0, 0, 0], None, None), ('dish', [0, -6, -3], 'mount', [-15, 0, 0]), ('whip', [3, -2, -1.5], 'mount', None),
           ('blink', [3, -10.5, -1.5], 'whip', None)],
    cubes=[small_plate(4, 3, 1),
           C('housing', [-2, -9, -3], [2, -2, 0], 'dsteel', 'mount'),
           C('dish_bowl', [-1.5, -7.5, -4], [1.5, -4.5, -3], 'dsteel', 'dish'),
           C('rim_t', [-2.5, -4.5, -5], [2.5, -3.5, -3], 'bright', 'dish'),
           C('rim_b', [-2.5, -8.5, -5], [2.5, -7.5, -3], 'bright', 'dish'),
           C('rim_l', [-2.5, -7.5, -5], [-1.5, -4.5, -3], 'bright', 'dish'),
           C('rim_r', [1.5, -7.5, -5], [2.5, -4.5, -3], 'bright', 'dish'),
           C('feed_arm', [-0.5, -6.5, -7], [0.5, -5.5, -4], 'rubber', 'dish'),
           C('feed', [-1, -7, -8], [1, -5, -7], 'brass', 'dish'),
           C('whip_base', [2, -3, -2.5], [4, -2, -0.5], 'dsteel', 'whip'),
           C('whip_rod', [2.5, -10, -2], [3.5, -3, -1], 'rubber', 'whip'),
           C('whip_tip', [2.5, -11, -2], [3.5, -10, -1], 'lamp_r', 'blink')],
    locators=[('fx_signal', [0, -6, -7], 'dish')],
    clips={'sustain': {'length': 20, 'loop': 'loop', 'bones': {
        'whip': {'rotation': [k(0, [0, 0, 0]), k(4, [0, 0, 170], 'easeOutCubic'), k(20, [0, 0, 170])]},
        'dish': {'rotation': [k(0, [0, -25, 0]), k(10, [0, 25, 0], 'easeInOutSine'), k(20, [0, -25, 0], 'easeInOutSine')]},
        'blink': {'scale': [k(0, [1, 1, 1]), k(10, [0, 0, 0]), k(14, [1, 1, 1])]}}},
           'idle': {'length': 1, 'loop': 'hold', 'bones': {'whip': {'rotation': [k(0, [0, 0, 0])]}}}},
    motion='a small tilted dish (bright rim, dark bowl, brass feed on a short arm) on a dark box with a whip antenna folded down its side; boosting, the whip swings up over the shoulder, its red tip blinks and the dish sweeps 25 degrees each way')

MODULES['solar_skin'] = dict(
    slot='TORSO', verb='sustain',
    bones=[('mount', [0, 0, 0], None, None), ('panel', [0, -1, -1], 'mount', None)],
    cubes=[small_plate(4, 3, 1),
           C('frame', [-3, -11, -2], [3, -1, -1], 'brass', 'panel')] +
          [C('cell_%d%d' % (r, c), [x0, y0, -3], [x0 + 2, y0 + 2, -2], 'cell', 'panel')
           for r, y0 in enumerate((-4, -7, -10)) for c, x0 in enumerate((-2.5, 0.5))] +
          [C('glint', [0.5, -4, -4], [1.5, -3, -3], 'lamp_b', 'panel')],
    locators=[],
    clips={'sustain': {'length': 40, 'loop': 'loop', 'bones': {
        'panel': {'rotation': [k(0, [24, 0, 0]), k(20, [24, 10, 0], 'easeInOutSine'), k(40, [24, 0, 0], 'easeInOutSine')]}}},
           'idle': {'length': 1, 'loop': 'hold', 'bones': {'panel': {'rotation': [k(0, [0, 0, 0])]}}}},
    motion='a flat brass frame of six dark blue cells with a glint, lying on the chest; in the sun the panel tilts out 24 degrees and follows it')

MODULES['tesla_coil'] = dict(
    slot='TORSO', verb='sustain',
    bones=[('mount', [0, 0, 0], None, None), ('arcs', [0, -1, -3], 'mount', None)],
    cubes=[small_plate(4, 3, 1),
           C('post', [-1, -11, -4], [1, -3, -2], 'dsteel', 'mount'),
           C('base', [-2, -12, -5], [2, -11, -1], 'dsteel', 'mount'),
           C('coil_0', [-2.5, -10, -5.5], [2.5, -9, -0.5], 'copper', 'mount'),
           C('coil_1', [-2.5, -8, -5.5], [2.5, -7, -0.5], 'copper', 'mount'),
           C('coil_2', [-2.5, -6, -5.5], [2.5, -5, -0.5], 'copper', 'mount'),
           C('ball', [-1.5, -3, -4.5], [1.5, 1, -1.5], 'brass', 'mount'),
           C('ball_band', [-2, -2, -5], [2, 0, -1], 'brass', 'mount'),
           C('arc_a', [2, -1, -3.5], [4, 0, -2.5], 'lamp_b', 'arcs'),
           C('arc_b', [3, -4, -3.5], [4, -1, -2.5], 'lamp_b', 'arcs'),
           C('arc_c', [-4, -2, -3.5], [-2, -1, -2.5], 'lamp_b', 'arcs'),
           C('arc_d', [-4, -1, -3.5], [-3, 2, -2.5], 'lamp_b', 'arcs')],
    locators=[('fx_arc', [0, -1, -5], 'mount')],
    clips={'sustain': {'length': 6, 'loop': 'loop', 'bones': {
        'arcs': {'scale': [k(0, [1, 1, 1]), k(1, [0, 0, 0]), k(2, [1, 1, 1]), k(4, [0, 0, 0]), k(5, [1, 1, 1])],
                 'rotation': [k(0, [0, 0, 0]), k(2, [0, 0, 40]), k(4, [0, 0, -30]), k(6, [0, 0, 0])]}},
        'effects': [(0, 'timeline', 'tesla_coil:crackle')]},
           'idle': {'length': 1, 'loop': 'hold', 'bones': {'arcs': {'scale': [k(0, [0, 0, 0])]}}}},
    motion='three copper windings, wider than the stepped brass ball above them, on a dark post; charged, blue arcs flicker off the ball and jump about')

MODULES['workbench'] = dict(
    slot='TORSO', verb='deploy',
    bones=[('mount', [0, 0, 0], None, None), ('jaw', [1.5, -3, -4], 'mount', None), ('drawer', [0, -9, -4], 'mount', None)],
    cubes=[small_plate(4, 3, 1),
           C('strap', [-1, -4, -2], [1, -2, 0], 'dsteel', 'mount'),
           C('vise_base', [-2, -5, -5], [2, -4, -1], 'dsteel', 'mount'),
           C('jaw_fixed', [-2, -4, -5], [-1, -1, -2], 'bright', 'mount'),
           C('jaw_moving', [1, -4, -5], [2, -1, -2], 'bright', 'jaw'),
           C('screw', [2, -3, -4], [4, -2, -3], 'dsteel', 'jaw'),
           C('screw_bar', [4, -4.5, -4], [5, -0.5, -3], 'brass', 'jaw'),
           C('box', [-3, -11, -4], [3, -6, -1], 'red', 'mount'),
           C('lid_band', [-3.5, -6, -4.5], [3.5, -5, -0.5], 'dsteel', 'mount'),
           C('drawer_front', [-2.5, -10, -5], [2.5, -8, -4], 'dsteel', 'drawer'),
           C('drawer_pull', [-0.5, -9.5, -6], [0.5, -8.5, -5], 'brass', 'drawer')],
    locators=[('fx_vise', [0, -3, -4], 'mount')],
    clips={'deploy': {'length': 8, 'loop': 'hold', 'bones': {
        'jaw': {'position': [k(0, [0, 0, 0]), k(4, [1.5, 0, 0], 'easeOutCubic')]},
        'drawer': {'position': [k(0, [0, 0, 0]), k(2, [0, 0, 0]), k(8, [0, 0, -3], 'easeOutCubic')]}},
        'effects': [(2, 'timeline', 'workbench:open')]},
           'retract': {'length': 6, 'loop': 'hold', 'bones': {
        'jaw': {'position': [k(0, [1.5, 0, 0]), k(6, [0, 0, 0], 'easeInOutSine')]},
        'drawer': {'position': [k(0, [0, 0, -3]), k(4, [0, 0, 0], 'easeInQuad')]}}}},
    motion='a red toolbox with a vise on its lid (two bright jaws, a brass T-bar screw); opened, the vise jaw winds out 1.5 px and a drawer slides out 3 px')

# ==================================================================================================================
# Stealth Field (energy_shield's projector, its own lens) and Bio-Integration Port (the Gene Port vial pieces)
# ==================================================================================================================
MODULES['stealth_field'] = dict(
    slot='TORSO', verb='sustain',
    bones=[('mount', [0, 0, 0], None, None), ('ring', [0, -4, -3.5], 'mount', None), ('pupil', [0, -4, -4], 'mount', None)],
    cubes=[small_plate(4, 3, 1),
           C('emitter_box', [-2, -7, -3], [2, 0, -1], 'dsteel', 'mount'),
           C('ring_t', [-2.5, -1.5, -4], [2.5, -0.5, -3], 'brass', 'ring'),
           C('ring_b', [-2.5, -7.5, -4], [2.5, -6.5, -3], 'brass', 'ring'),
           C('lens', [-1.5, -5.5, -4], [1.5, -2.5, -3], 'lens', 'mount'),
           C('pupil_c', [-0.5, -4.5, -5], [0.5, -3.5, -4], 'lamp_v', 'pupil')],
    locators=[('fx_cloak', [0, -4, -5], 'mount')],
    clips={'sustain': {'length': 32, 'loop': 'loop', 'bones': {
        'ring': {'rotation': [k(0, [0, 0, 0]), k(32, [0, 0, -360])]},
        'pupil': {'scale': [k(0, [1, 1, 1]), k(28, [0, 0, 0]), k(30, [1, 1, 1])]}}}},
    motion="the energy shield's projector with a black lens and a violet pupil; cloaked, the ring turns slowly backwards and the pupil winks")

MODULES['bio_integration_port'] = dict(
    slot='TORSO', verb='sustain',
    bones=[('mount', [0, 0, 0], None, None), ('serum', [0, -9, -3], 'mount', None)],
    cubes=[small_plate(4, 3, 1),
           C('backing', [-2.5, -10, -2], [2.5, -2, 0], 'dsteel', 'mount'),
           C('vial', [-1.5, -10, -4], [1.5, -2, -2], 'vial', 'mount'),
           C('socket_ring', [-2, -11, -4.5], [2, -9, -1.5], 'rubber', 'mount'),
           C('vial_cap', [-2, -2, -4.5], [2, -1, -1.5], 'brass', 'mount'),
           C('serum_fill', [-1, -9, -3.5], [1, -4, -2.5], 'serum', 'serum'),
           C('status', [1.5, -7, -3], [2.5, -6, -2], 'lamp_g', 'mount')],
    locators=[('fx_gene', [0, -1, -3], 'mount')],
    clips={'sustain': {'length': 40, 'loop': 'loop', 'bones': {
        'serum': {'position': [k(0, [0, 0, 0]), k(30, [0, -2, 0], 'easeInOutSine'), k(34, [0, -2, 0]), k(40, [0, 0, 0])]}},
        'effects': [(0, 'timeline', 'bio_port:infuse')]}},
    motion="one of the Chimera's Gene Port vials in its black socket ring and brass cap, on a dark backing with a green lamp; infusing, the serum drains down into the ring")


# ==================================================================================================================
# The core tell (shared): socket_core points down under the hips. -Z is down, +Y is forward, so the face is on +Y.
# ==================================================================================================================
def core_tell(face_mat, extra_bones, extras, clips, motion, locators=()):
    return dict(
        slot='CORE', verb=list(clips)[0],
        bones=[('mount', [0, 0, 0], None, None)] + extra_bones,
        cubes=[C('hatch', [-3, -3, -1], [3, 3, 0], 'dsteel', 'mount'),
               C('housing', [-2.5, -2.5, -4], [2.5, 2.5, -1], 'rubber', 'mount'),
               C('bezel', [-2, 2.5, -4.5], [2, 3.5, -0.5], 'brass', 'mount'),
               C('face', [-1.5, 3.5, -4], [1.5, 4.5, -1], face_mat, 'mount'),
               C('lamp', [-0.5, -3.5, -3], [0.5, -2.5, -2], 'lamp_a', 'mount')] + extras,
        locators=list(locators), clips=clips, motion=motion)


MODULES['galvanic_stack'] = core_tell(
    'white', [('needle', [0, 4.5, -2.5], 'mount', None)],
    [C('needle_bar', [-0.5, 4.5, -3.5], [0.5, 5.5, -0.5], 'lens', 'needle')],
    {'sustain': {'length': 16, 'loop': 'loop', 'bones': {
        'needle': {'rotation': [k(0, [0, -50, 0]), k(8, [0, 50, 0], 'easeInOutSine'), k(16, [0, -50, 0], 'easeInOutSine')]}}}},
    'the core hatch with a white voltmeter face; running, its black needle swings 50 degrees each way')

MODULES['combustion_kernel'] = core_tell(
    'lamp_r', [('exhaust', [3.5, 0, -2], 'mount', None)],
    [C('stub', [2.5, -1, -3], [4.5, 1, -1], 'dsteel', 'exhaust'),
     C('mouth', [4.5, -0.5, -2.5], [5.5, 0.5, -1.5], 'lens', 'exhaust')],
    {'sustain': {'length': 4, 'loop': 'loop', 'bones': {
        'exhaust': {'position': [k(0, [0, 0, 0]), k(1, [0.3, 0, 0.2]), k(2, [0, 0, 0]), k(3, [0.2, 0, -0.2]), k(4, [0, 0, 0])]}},
        'effects': [(0, 'timeline', 'combustion:puff')]}},
    'the core hatch with a hot orange window and a stubby exhaust pipe out the side that shudders while it burns',
    locators=[('fx_exhaust', [5.5, 0, -2], 'exhaust')])

MODULES['bio_reactor_core'] = core_tell(
    'lamp_g', [('bubble', [0, 4.5, -3], 'mount', None)],
    [C('bubble_a', [-1, 4.5, -4], [0, 5.5, -3], 'enamel', 'bubble'), C('bubble_b', [0.5, 4.5, -3], [1.5, 5.5, -2], 'enamel', 'bubble')],
    {'sustain': {'length': 20, 'loop': 'loop', 'bones': {
        'bubble': {'position': [k(0, [0, 0, 0]), k(16, [0, 0, 2], 'easeInQuad'), k(17, [0, 0, 0])],
                   'scale': [k(0, [1, 1, 1]), k(16, [0, 0, 0]), k(18, [1, 1, 1])]}}}},
    'the core hatch with a green window; two pale bubbles rise through it and pop')

MODULES['capacitor_bank'] = core_tell(
    'lens', [('bar_0', [0, 4.5, -3.5], 'mount', None), ('bar_1', [0, 4.5, -2.5], 'mount', None), ('bar_2', [0, 4.5, -1.5], 'mount', None)],
    [C('bar_0c', [-2, 4.5, -4], [-1, 5.5, -3], 'lamp_b', 'bar_0'),
     C('bar_1c', [-0.5, 4.5, -4], [0.5, 5.5, -2], 'lamp_b', 'bar_1'),
     C('bar_2c', [1, 4.5, -4], [2, 5.5, -1], 'lamp_b', 'bar_2')],
    {'sustain': {'length': 24, 'loop': 'loop', 'bones': {
        'bar_0': {'scale': [k(0, [1, 1, 1])]},
        'bar_1': {'scale': [k(0, [0, 0, 0]), k(6, [1, 1, 1])]},
        'bar_2': {'scale': [k(0, [0, 0, 0]), k(12, [1, 1, 1]), k(20, [0, 0, 0])]}}}},
    'the core hatch with a dark face and a rising three-bar blue graph (1, 2 and 3 px) that fills bar by bar')

MODULES['fusion_bottle'] = core_tell(
    'lens', [('ring', [0, 4.5, -2.5], 'mount', None)],
    [C('ring_b', [-1.5, 4.5, -4], [1.5, 5.5, -3], 'lamp_o', 'ring'),
     C('ring_t', [-1.5, 4.5, -2], [1.5, 5.5, -1], 'lamp_o', 'ring'),
     C('ring_l', [-1.5, 4.5, -3], [-0.5, 5.5, -2], 'lamp_o', 'ring'),
     C('ring_r', [0.5, 4.5, -3], [1.5, 5.5, -2], 'lamp_o', 'ring'),
     C('plasma', [-0.5, 4.5, -3], [0.5, 6.5, -2], 'lamp_b', 'mount')],
    {'sustain': {'length': 8, 'loop': 'loop', 'bones': {'ring': {'rotation': [k(0, [0, 0, 0]), k(8, [0, 360, 0])]}}}},
    'the core hatch with a dark face, a glowing orange ring turning round a blue spark')

NO_HEAVY = ('solar_skin',)   # a doubled panel walled over the Colossus chest: one size
HEAVY_OWN = {}


def main():
    problems = 0
    for name, m in list(MODULES.items()):
        if name in NO_HEAVY:
            continue
        MODULES[name + '_heavy'] = HEAVY_OWN.get(name) or M.heavy(m)
    for name, m in MODULES.items():
        bad, plus, cp = M.check(name, m)
        plus = [c[0] for c in m['cubes'] if c[4].startswith('mount') and max(c[1][2], c[2][2]) > 1e-6]
        if bad or plus or cp:
            problems += 1
            sys.stderr.write('%s: sizes %s, +z %s, coplanar %s\n' % (name, bad, plus, cp))
    if problems or '--check' in sys.argv:
        sys.exit(1 if problems else 0)
    for name, m in MODULES.items():
        d = os.path.join(HERE, name.replace('_heavy', ''))
        os.makedirs(d, exist_ok=True)
        offs = M.pack(m['cubes'])
        png = os.path.join(d, '%s_placeholder.png' % name)
        M.draw(m['cubes'], offs, png)
        path = os.path.join(d, '%s.bbmodel' % name)
        bbbuild.build(path, name, m['bones'], m['cubes'], m['locators'], offs, png, M.TEX, keep_animations=False)
        with open(path, encoding='utf-8') as f:
            model = json.load(f)
        model['animations'] = M.clip_json(model, m['clips'])
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(model, f, indent=1)
    print('wrote %d module files' % len(MODULES))


if __name__ == '__main__':
    main()

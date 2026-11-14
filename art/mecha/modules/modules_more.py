"""Module shapes, second batch: one small GeckoLib .bbmodel per module, each in art/mecha/modules/<module>/.

    python art/mecha/modules/modules_more.py            # writes <module>/<module>.bbmodel and its placeholder sheet
    python art/mecha/modules/modules_more.py --check    # sizes and coplanar faces only

The mount convention is modules_geo.py's (the first three: drill, jump_jets, hydraulic_legs), kept exactly:
- 16 px a block, the mount point at the origin, the working end toward -Z, +Y the module's up. Nothing lies on the
  +Z side of z = 0 except what sinks into the part it is fixed to.
- The top bone is `mount` (or `mount_<part>` for a module drawn on both sides). It is drawn at the socket bone's pivot
  with the socket's full transform; no module is scaled.
- Every module starts with the same 6 x 6 x 1 dark mount plate at z -1..0 (8 x 6 for back packs), so it reads as
  bolted on whether the socket is a Hopper's forearm cuff or a Colossus's.
- A module's motion is a clip in its own file on its own bones, named for the arm verb or slot action that plays it:
  spin, recoil, sustain, strike, guard, deploy, retract, burst, idle.

Sizes: two classes at one mount convention. The light class (<module>.bbmodel: arm tools within 12 px across and 25
px long, most 6 x 6 x 17; back packs within 16 x 18 x 12 folded) is for frames under 3 blocks (Hopper, Chimera, Kite,
Skitter, Wheelhouse). The heavy class (<module>_heavy.bbmodel) is the same module with every coordinate doubled, for
the Bulwark, Colossus and Executive: whole pixels, so one texel size, and nothing is scaled at run time.
"""
import json
import os
import sys
import uuid

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import bbbuild  # noqa: E402

TEX = (128, 128)
MATERIALS = {
    'enamel': ('ddd5c3', 255), 'brass': ('d99a35', 255), 'steel': ('7b818b', 255), 'dsteel': ('5c616b', 255),
    'bright': ('ced3d8', 255), 'rubber': ('27262d', 255), 'copper': ('c26b4c', 255), 'lens': ('141318', 255),
    'lamp_a': ('ffc24e', 255), 'lamp_b': ('8fe3ff', 255), 'yellow': ('f4bd1e', 255), 'vial': ('86cac1', 110),
    'blood': ('7d323a', 255),
}


def C(name, f, t, mat, parent, **kw):
    return (name, list(f), list(t), mat, parent, kw)


def plate(w=6, h=6):
    return C('plate', [-w / 2.0, -h / 2.0, -1], [w / 2.0, h / 2.0, 0], 'dsteel', 'mount')


def k(t, v, e=None):
    return (t, v, e) if e else (t, v)


MODULES = {}

# ==================================================================================================================
# ARM tools (socket_arm_l/r on every frame; socket_arm_2/3, the shoulder hardpoints, on the Colossus and Executive)
# ==================================================================================================================
MODULES['chainsaw'] = dict(
    slot='ARM', verb='spin',
    bones=[('mount', [0, 0, 0], None, None), ('chain', [0, 0, -12], 'mount', None)],
    cubes=[plate(),
           C('engine', [-3, -3, -7], [3, 3, -1], 'yellow', 'mount'),
           C('grip', [-1, 3, -7], [1, 6, -2], 'rubber', 'mount'),
           C('grip_bar', [-1, 5, -9], [1, 6, -7], 'rubber', 'mount'),
           C('muffler', [3, -2, -6], [4, 1, -2], 'dsteel', 'mount'),
           C('bar', [-1, -2, -19], [1, 2, -7], 'bright', 'mount'),
           C('nose', [-1, -1.5, -21], [1, 1.5, -19], 'bright', 'mount'),
           C('chain_t', [-1.5, 2, -19], [1.5, 3, -7], 'rubber', 'chain'),
           C('chain_b', [-1.5, -3, -19], [1.5, -2, -7], 'rubber', 'chain'),
           C('tooth_0', [-1.5, 3, -10], [1.5, 4, -9], 'steel', 'chain'),
           C('tooth_1', [-1.5, 3, -14], [1.5, 4, -13], 'steel', 'chain'),
           C('tooth_2', [-1.5, 3, -18], [1.5, 4, -17], 'steel', 'chain'),
           C('tooth_3', [-1.5, -4, -12], [1.5, -3, -11], 'steel', 'chain'),
           C('tooth_4', [-1.5, -4, -16], [1.5, -3, -15], 'steel', 'chain')],
    locators=[('fx_tip', [0, 0, -20.5], 'mount')],
    clips={'spin': {'length': 4, 'loop': 'loop', 'bones': {
        'chain': {'position': [k(0, [0, 0, 0]), k(1, [0, 0, -1]), k(2, [0, 0, -2]), k(3, [0, 0, -3]), k(4, [0, 0, 0])]},
        'mount': {'rotation': [k(0, [0, 0, 0]), k(1, [2, 0, -1.5]), k(2, [0, 0, 0]), k(3, [-2, 0, 1.5]), k(4, [0, 0, 0])]}}}},
    motion='the teeth run 3 px along the bar and snap back each 4 ticks; the saw judders 2 degrees; rounded nose, rear grip')

MODULES['hydraulic_fist'] = dict(
    slot='ARM', verb='strike',
    bones=[('mount', [0, 0, 0], None, None), ('ram', [0, 0, -8], 'mount', None)],
    cubes=[plate(),
           C('cylinder', [-2.5, -2.5, -8], [2.5, 2.5, -1], 'dsteel', 'mount'),
           C('band', [-3, -3, -5], [3, 3, -4], 'brass', 'mount'),
           C('rod', [-1, -1, -11], [1, 1, -7], 'bright', 'ram'),
           C('fist', [-3, -3, -16], [3, 3, -11], 'steel', 'ram'),
           C('knuckles', [-3.5, -1.5, -17], [3.5, 2.5, -16], 'brass', 'ram')],
    locators=[('fx_tip', [0, 0, -17], 'ram')],
    clips={'strike': {'length': 12, 'loop': 'once', 'bones': {
        'ram': {'position': [k(0, [0, 0, 0]), k(2, [0, 0, -7], 'easeInExpo'), k(4, [0, 0, -7]), k(12, [0, 0, 0], 'easeInOutSine')]}},
        'effects': [(2, 'timeline', 'fist:impact')]}},
    motion='the fist punches 7 px out on a 2-tick ram and returns')

MODULES['grapple_hook'] = dict(
    slot='ARM', verb='deploy',
    bones=[('mount', [0, 0, 0], None, None), ('hook', [0, 0, -11], 'mount', None)],
    cubes=[plate(),
           C('tube', [-2, -2, -11], [2, 2, -1], 'dsteel', 'mount'),
           C('reel', [2, -2, -8], [4, 2, -3], 'copper', 'mount'),
           C('muzzle', [-2.5, -2.5, -12], [2.5, 2.5, -11], 'brass', 'mount'),
           C('shank', [-1, -1, -17], [1, 1, -12], 'bright', 'hook'),
           C('tine_u', [-1, 1, -17], [1, 4, -16], 'brass', 'hook'),
           C('tine_u_tip', [-1, 4, -16], [1, 5, -13], 'brass', 'hook'),
           C('tine_l', [-4, -2, -17], [-1, 0, -16], 'brass', 'hook'),
           C('tine_l_tip', [-5, -3, -16], [-3, -1, -13], 'brass', 'hook'),
           C('tine_r', [1, -2, -17.5], [4, 0, -16.5], 'brass', 'hook'),
           C('tine_r_tip', [3, -3, -16.5], [5, -1, -13.5], 'brass', 'hook')],
    locators=[('fx_tip', [0, 0, -17], 'hook')],
    clips={'deploy': {'length': 4, 'loop': 'hold', 'bones': {
        'hook': {'position': [k(0, [0, 0, 0]), k(3, [0, 0, -40], 'easeOutQuad')],
                 'scale': [k(0, [1, 1, 1]), k(3, [0, 0, 0])]}}, 'effects': [(1, 'timeline', 'grapple:fire')]},
           'retract': {'length': 8, 'loop': 'once', 'bones': {
        'hook': {'position': [k(0, [0, 0, -30]), k(5, [0, 0, 0], 'easeInQuad'), k(6, [0, 0, 1.5], 'easeOutQuad'), k(8, [0, 0, 0])],
                 'scale': [k(0, [1, 1, 1])]},
        'mount': {'rotation': [k(0, [0, 0, 0]), k(5, [0, 0, 0]), k(6, [-6, 0, 0], 'easeOutExpo'), k(8, [0, 0, 0], 'easeInOutSine')]}},
        'effects': [(5, 'timeline', 'grapple:home')]}},
    motion='a three-tined grapnel flies off the tube (the game draws the line), comes home from 30 px and the arm jerks')

MODULES['tractor_beam'] = dict(
    slot='ARM', verb='sustain',
    bones=[('mount', [0, 0, 0], None, None), ('dish', [0, 0, -8], 'mount', None)],
    cubes=[plate(),
           C('neck', [-2, -2, -5], [2, 2, -1], 'dsteel', 'mount'),
           C('coil', [-3, -3, -7], [3, 3, -5], 'copper', 'mount'),
           C('dish_t', [-5, 4, -10], [5, 5, -7], 'brass', 'dish'),
           C('dish_b', [-5, -5, -10], [5, -4, -7], 'brass', 'dish'),
           C('dish_l', [-5, -4, -10], [-4, 4, -7], 'brass', 'dish'),
           C('dish_r', [4, -4, -10], [5, 4, -7], 'brass', 'dish'),
           C('dish_back', [-4, -4, -8], [4, 4, -7], 'dsteel', 'dish'),
           C('emitter', [-2, -2, -10], [2, 2, -8], 'lamp_b', 'dish')],
    locators=[('fx_beam', [0, 0, -10], 'dish')],
    clips={'sustain': {'length': 8, 'loop': 'loop', 'bones': {
        'dish': {'rotation': [k(0, [0, 0, 0]), k(8, [0, 0, 90])]},
        'mount': {'rotation': [k(0, [0, 0, 0]), k(4, [1, 0, 0], 'easeInOutSine'), k(8, [0, 0, 0], 'easeInOutSine')]}}}},
    motion='the dish turns a quarter every 8 ticks behind its blue emitter')

MODULES['rivet_gun'] = dict(
    slot='ARM', verb='recoil',
    bones=[('mount', [0, 0, 0], None, None), ('barrel', [0, 0.5, -9], 'mount', None), ('drum', [0, 3, -5], 'mount', None)],
    cubes=[plate(),
           C('body', [-2.5, -3, -9], [2.5, 2, -1], 'enamel', 'mount'),
           C('handle', [-1, -6, -4], [1, -3, -2], 'rubber', 'mount'),
           C('drum_case', [-3, 2, -7], [3, 6, -3], 'brass', 'drum'),
           C('drum_cap', [-3.5, 3, -6], [3.5, 5, -4], 'dsteel', 'drum'),
           C('barrel_tube', [-1, -0.5, -20], [1, 1.5, -9], 'bright', 'barrel'),
           C('shroud', [-1.5, -1, -14.5], [1.5, 2, -9.5], 'steel', 'barrel'),
           C('muzzle', [-1.5, -1, -21], [1.5, 2, -20], 'dsteel', 'barrel')],
    locators=[('fx_tip', [0, 0.5, -21], 'barrel')],
    clips={'recoil': {'length': 6, 'loop': 'once', 'bones': {
        'barrel': {'position': [k(0, [0, 0, 0]), k(1, [0, 0, 3], 'easeOutExpo'), k(4, [0, 0, 0], 'easeInQuad')]},
        'drum': {'rotation': [k(0, [0, 0, 0]), k(2, [0, 0, 0]), k(4, [0, 0, 45], 'easeInOutSine')],
                 'position': [k(0, [0, 0, 0]), k(1, [0, -1, 0], 'easeOutExpo'), k(4, [0, 0, 0], 'easeInQuad')]},
        'mount': {'rotation': [k(0, [0, 0, 0]), k(1, [-6, 0, 0], 'easeOutExpo'), k(6, [0, 0, 0], 'easeInOutSine')]}},
        'effects': [(0, 'timeline', 'rivet:fire')]}},
    motion='a long barrel and a round rivet drum: the barrel kicks 3 px, the drum drops and turns a rivet, the gun bucks 6 degrees')

MODULES['riot_shield'] = dict(
    slot='ARM', verb='guard',
    bones=[('mount', [0, 0, 0], None, None), ('shield', [0, 0, -4], 'mount', None)],
    cubes=[plate(),
           C('bracket', [-1.5, -2, -4], [1.5, 2, -1], 'dsteel', 'mount'),
           C('face', [-6, -8, -6], [6, 8, -4], 'enamel', 'shield'),
           C('rim_t', [-6.5, 8, -6.5], [6.5, 9, -3.5], 'steel', 'shield'),
           C('rim_b', [-6.5, -9, -6.5], [6.5, -8, -3.5], 'steel', 'shield'),
           C('window', [-3, 3, -6.5], [3, 6, -5.5], 'lens', 'shield'),
           C('stripe', [-5.5, -6, -6.5], [5.5, -4, -5.5], 'yellow', 'shield')],
    locators=[('fx_face', [0, 0, -6.5], 'shield')],
    clips={'guard': {'length': 6, 'loop': 'hold', 'bones': {
        'shield': {'rotation': [k(0, [0, 0, 0]), k(4, [0, 30, 0], 'easeOutCubic'), k(6, [0, 26, 0], 'easeInOutSine')]}}},
           'deploy': {'length': 8, 'loop': 'hold', 'bones': {
        'shield': {'rotation': [k(0, [90, 0, 0]), k(6, [-4, 0, 0], 'easeOutCubic'), k(8, [0, 0, 0], 'easeInOutSine')]}}},
           'idle': {'length': 1, 'loop': 'hold', 'bones': {'shield': {'rotation': [k(0, [90, 0, 0])]}}}},
    motion='carried folded flat along the forearm (idle); swings up to face front (deploy); guard turns it 30 degrees across the body')

MODULES['manipulator_hand'] = dict(
    slot='ARM', verb='strike',
    bones=[('mount', [0, 0, 0], None, None), ('finger_t', [0, 2.5, -8], 'mount', None),
           ('finger_b', [0, -2.5, -8], 'mount', None)],
    cubes=[plate(),
           C('wrist', [-2, -2, -5], [2, 2, -1], 'dsteel', 'mount'),
           C('palm', [-3, -3, -8], [3, 3, -5], 'steel', 'mount'),
           C('finger_tl', [-3, 2, -14], [-1, 3, -8], 'steel', 'finger_t'),
           C('finger_tr', [1, 2, -14], [3, 3, -8], 'steel', 'finger_t'),
           C('tip_tl', [-3, 1, -15], [-1, 3, -14], 'rubber', 'finger_t'),
           C('tip_tr', [1, 1, -15], [3, 3, -14], 'rubber', 'finger_t'),
           C('thumb', [-1, -3, -13], [1, -2, -8], 'steel', 'finger_b'),
           C('tip_b', [-1, -3, -14], [1, -1, -13], 'rubber', 'finger_b')],
    locators=[('fx_tip', [0, 0, -14], 'mount')],
    clips={'strike': {'length': 10, 'loop': 'once', 'bones': {
        'finger_t': {'rotation': [k(0, [0, 0, 0]), k(2, [18, 0, 0], 'easeOutQuad'), k(4, [-20, 0, 0], 'easeInQuad'), k(10, [0, 0, 0], 'easeInOutSine')]},
        'finger_b': {'rotation': [k(0, [0, 0, 0]), k(2, [-18, 0, 0], 'easeOutQuad'), k(4, [20, 0, 0], 'easeInQuad'), k(10, [0, 0, 0], 'easeInOutSine')]}},
        'effects': [(4, 'timeline', 'hand:grip')]},
           'guard': {'length': 4, 'loop': 'hold', 'bones': {
        'finger_t': {'rotation': [k(0, [0, 0, 0]), k(4, [-20, 0, 0], 'easeInQuad')]},
        'finger_b': {'rotation': [k(0, [0, 0, 0]), k(4, [20, 0, 0], 'easeInQuad')]}}}},
    motion='two fingers and a thumb open wide, then snap shut')

MODULES['excavator_scoop'] = dict(
    slot='ARM', verb='strike',
    bones=[('mount', [0, 0, 0], None, None), ('bucket', [0, 0, -9], 'mount', None)],
    cubes=[plate(),
           C('boom', [-2, -2, -9], [2, 2, -1], 'dsteel', 'mount'),
           C('ram', [-1, 2, -8], [1, 3, -2], 'bright', 'mount'),
           C('back', [-4, -4, -11], [4, 3, -9], 'yellow', 'bucket'),
           C('floor', [-4, -5, -17], [4, -4, -9], 'yellow', 'bucket'),
           C('side_l', [-4, -4, -16], [-3, 1, -11], 'yellow', 'bucket'),
           C('side_r', [3, -4, -16], [4, 1, -11], 'yellow', 'bucket'),
           C('teeth', [-4, -6, -18], [4, -4, -17], 'steel', 'bucket')],
    locators=[('fx_tip', [0, -5, -18], 'bucket')],
    clips={'strike': {'length': 14, 'loop': 'once', 'bones': {
        'bucket': {'rotation': [k(0, [0, 0, 0]), k(3, [-25, 0, 0], 'easeOutQuad'), k(7, [60, 0, 0], 'easeInCubic'), k(9, [55, 0, 0]),
                                k(14, [0, 0, 0], 'easeInOutSine')]}}, 'effects': [(7, 'timeline', 'scoop:bite')]}},
    motion='the bucket drops its lip, then curls 60 degrees up through the dig')

MODULES['flamethrower'] = dict(
    slot='ARM', verb='sustain',
    bones=[('mount', [0, 0, 0], None, None), ('nozzle', [0, -1, -10], 'mount', None), ('pilot', [0, -3.5, -16.5], 'nozzle', None),
           ('flame', [0, -1, -17], 'nozzle', None)],
    cubes=[plate(),
           C('body', [-2.5, -3, -10], [2.5, 1, -1], 'dsteel', 'mount'),
           C('tank', [-2, 1, -9], [2, 5, -2], 'copper', 'mount'),
           C('tank_cap', [-1, 5, -6], [1, 6, -4], 'brass', 'mount'),
           C('pipe', [-1, -2, -15], [1, 0, -10], 'bright', 'nozzle'),
           C('bell', [-2, -3, -17], [2, 1, -15], 'steel', 'nozzle'),
           C('pilot_flame', [-0.5, -4, -17], [0.5, -3, -16], 'lamp_a', 'pilot'),
           C('flame_a', [-2.5, -3.5, -22], [2.5, 1.5, -17], 'lamp_a', 'flame'),
           C('flame_b', [-3.5, -4.5, -27], [3.5, 2.5, -22], 'lamp_a', 'flame')],
    locators=[('fx_tip', [0, -1, -17], 'nozzle')],
    clips={'sustain': {'length': 6, 'loop': 'loop', 'bones': {
        'flame': {'scale': [k(0, [1, 1, 1])]},
        'nozzle': {'rotation': [k(0, [0, 0, 0]), k(2, [0, 6, 0], 'easeInOutSine'), k(4, [0, -6, 0], 'easeInOutSine'), k(6, [0, 0, 0], 'easeInOutSine')]},
        'mount': {'rotation': [k(0, [0, 0, 0]), k(3, [-2, 0, 0], 'easeInOutSine'), k(6, [0, 0, 0], 'easeInOutSine')]}}},
           'idle': {'length': 1, 'loop': 'hold', 'bones': {'flame': {'scale': [k(0, [0, 0, 0])]}}}},
    motion='a 10 px flame tongue out of the bell (shown only while firing), sweeping 6 degrees; a pilot flame at idle')

MODULES['arc_cannon'] = dict(
    slot='ARM', verb='sustain',
    bones=[('mount', [0, 0, 0], None, None), ('prong_l', [-2, 0, -8], 'mount', None), ('prong_r', [2, 0, -8], 'mount', None)],
    cubes=[plate(),
           C('body', [-3, -3, -8], [3, 3, -1], 'dsteel', 'mount'),
           C('ring_a', [-3.5, -3.5, -5], [3.5, 3.5, -4], 'copper', 'mount'),
           C('ring_b', [-3.5, -3.5, -7.5], [3.5, 3.5, -6.5], 'copper', 'mount'),
           C('prong_lc', [-3, -1, -17], [-1, 1, -8], 'copper', 'prong_l'),
           C('ball_l', [-3.5, -1.5, -18], [-0.5, 1.5, -17], 'brass', 'prong_l'),
           C('prong_rc', [1, -1, -17], [3, 1, -8], 'copper', 'prong_r'),
           C('ball_r', [0.5, -1.5, -18], [3.5, 1.5, -17], 'brass', 'prong_r')],
    locators=[('fx_arc', [0, 0, -17.5], 'mount')],
    clips={'sustain': {'length': 6, 'loop': 'loop', 'bones': {
        'prong_l': {'rotation': [k(0, [0, 0, 0]), k(3, [0, -8, 0], 'easeInOutSine'), k(6, [0, 0, 0], 'easeInOutSine')]},
        'prong_r': {'rotation': [k(0, [0, 0, 0]), k(3, [0, 8, 0], 'easeInOutSine'), k(6, [0, 0, 0], 'easeInOutSine')]}}},
           'recoil': {'length': 8, 'loop': 'once', 'bones': {
        'mount': {'rotation': [k(0, [0, 0, 0]), k(1, [-8, 0, 0], 'easeOutExpo'), k(8, [0, 0, 0], 'easeInOutSine')]},
        'prong_l': {'rotation': [k(0, [0, -8, 0]), k(1, [0, 4, 0], 'easeOutExpo'), k(6, [0, 0, 0], 'easeInOutSine')]},
        'prong_r': {'rotation': [k(0, [0, 8, 0]), k(1, [0, -4, 0], 'easeOutExpo'), k(6, [0, 0, 0], 'easeInOutSine')]}},
        'effects': [(0, 'timeline', 'arc:discharge')]}},
    motion='the copper prongs breathe apart while it charges; on the shot they snap shut and the cannon kicks 8 degrees')

MODULES['hemo_syringe'] = dict(
    slot='ARM', verb='strike',
    bones=[('mount', [0, 0, 0], None, None), ('needle', [0, 0, -12], 'mount', None), ('plunger', [0, 0, -3], 'mount', None)],
    cubes=[plate(),
           C('collar', [-3, -3, -3], [3, 3, -1], 'brass', 'mount'),
           C('barrel', [-2, -2, -12], [2, 2, -3], 'vial', 'mount'),
           C('blood', [-1.5, -1.5, -11], [1.5, 1.5, -6], 'blood', 'mount'),
           C('plunger_head', [-1.5, -1.5, -5], [1.5, 1.5, -4], 'rubber', 'plunger'),
           C('hub', [-1, -1, -13], [1, 1, -12], 'brass', 'needle'),
           C('needle_pin', [-0.5, -0.5, -18], [0.5, 0.5, -13], 'bright', 'needle')],
    locators=[('fx_tip', [0, 0, -18], 'needle')],
    clips={'strike': {'length': 14, 'loop': 'once', 'bones': {
        'needle': {'position': [k(0, [0, 0, 0]), k(2, [0, 0, -5], 'easeInExpo'), k(8, [0, 0, -5]), k(12, [0, 0, 0], 'easeInOutSine')]},
        'plunger': {'position': [k(0, [0, 0, 0]), k(2, [0, 0, 0]), k(8, [0, 0, -2], 'easeInOutSine'), k(14, [0, 0, 0], 'easeInOutSine')]}},
        'effects': [(2, 'timeline', 'syringe:stab')]}},
    motion='the needle stabs 5 px out, the plunger draws back as it fills')

MODULES['harvester'] = dict(
    slot='ARM', verb='spin',
    bones=[('mount', [0, 0, 0], None, None), ('reel', [0, -2, -10], 'mount', None)],
    cubes=[plate(),
           C('boom', [-1.5, -1.5, -9], [1.5, 1.5, -1], 'dsteel', 'mount'),
           C('fork_l', [-6, -2, -11], [-5, 1, -8], 'yellow', 'mount'),
           C('fork_r', [5, -2, -11], [6, 1, -8], 'yellow', 'mount'),
           C('fork_bar', [-6, 1, -10], [6, 2, -8], 'yellow', 'mount'),
           C('axle', [-5, -2.5, -10.5], [5, -1.5, -9.5], 'bright', 'reel'),
           C('bat_a', [-5, 1.5, -11], [5, 2.5, -9], 'steel', 'reel'),
           C('bat_b', [-5, -6.5, -11], [5, -5.5, -9], 'steel', 'reel'),
           C('bat_c', [-5, -3, -14.5], [5, -1, -13.5], 'steel', 'reel'),
           C('tine_a', [-4.5, 2.5, -10.5], [4.5, 3.5, -9.5], 'brass', 'reel'),
           C('tine_c', [-4.5, -2.5, -15.5], [4.5, -1.5, -14.5], 'brass', 'reel')],
    locators=[('fx_tip', [0, -2, -15], 'reel')],
    clips={'spin': {'length': 9, 'loop': 'loop', 'bones': {'reel': {'rotation': [k(0, [0, 0, 0]), k(3, [-120, 0, 0]), k(6, [-240, 0, 0]),
                                                                                 k(9, [-360, 0, 0])]}}}},
    motion='a combine reel: three tined bats on a crosswise axle in a yellow fork, turning down and in every 9 ticks')

MODULES['welding_torch'] = dict(
    slot='ARM', verb='sustain',
    bones=[('mount', [0, 0, 0], None, None), ('torch', [0, 0, -9], 'mount', None), ('flame', [0, -4, -18], 'torch', None)],
    cubes=[plate(),
           C('bottle', [-3, -3, -9], [1, 4, -1], 'copper', 'mount'),
           C('bottle_cap', [-2, 4, -6], [0, 5, -3], 'brass', 'mount'),
           C('regulator', [1, 0, -7], [3, 3, -3], 'brass', 'mount'),
           C('hose', [1, -2, -9], [2, 0, -5], 'rubber', 'mount'),
           C('handle', [-1, -1, -14], [1, 1, -9], 'rubber', 'torch'),
           C('neck', [-1, -1, -18], [1, 1, -14], 'bright', 'torch'),
           C('hook', [-1, -4, -19], [1, -1, -17], 'bright', 'torch'),
           C('tip', [-1.5, -5, -19.5], [1.5, -4, -16.5], 'brass', 'torch'),
           C('flame_core', [-1, -8, -19], [1, -5, -17], 'lamp_b', 'flame')],
    locators=[('fx_tip', [0, -7, -18], 'flame')],
    clips={'sustain': {'length': 4, 'loop': 'loop', 'bones': {
        'flame': {'scale': [k(0, [1, 1, 1]), k(3, [0, 0, 0]), k(4, [1, 1, 1])]},
        'torch': {'rotation': [k(0, [0, 0, 0]), k(2, [2, 2, 0], 'easeInOutSine'), k(4, [0, 0, 0], 'easeInOutSine')]}}},
           'idle': {'length': 1, 'loop': 'hold', 'bones': {'flame': {'scale': [k(0, [0, 0, 0])]}}}},
    motion='a gas bottle and regulator, a torch with a hooked nozzle; a blue flame 3 px long flickers down from the hook')

MODULES['plasma_lance'] = dict(
    slot='ARM', verb='strike',
    bones=[('mount', [0, 0, 0], None, None), ('lance', [0, 0, -7], 'mount', None), ('core', [0, 0, -9], 'lance', None)],
    cubes=[plate(),
           C('grip', [-2, -2, -6], [2, 2, -1], 'dsteel', 'mount'),
           C('guard', [-4, -1, -7], [4, 1, -6], 'brass', 'mount'),
           C('shaft', [-1, -1, -18], [1, 1, -7], 'steel', 'lance'),
           C('ring_a', [-1.5, -1.5, -13], [1.5, 1.5, -12], 'brass', 'lance'),
           C('head_back', [-3, -3, -19], [3, 3, -18], 'brass', 'lance'),
           C('emitter', [-2.5, -2.5, -22], [2.5, 2.5, -19], 'lamp_b', 'lance'),
           C('head_fins', [-4, -0.5, -21.5], [4, 0.5, -19.5], 'brass', 'lance'),
           C('core_glow', [-1.5, -1.5, -10], [1.5, 1.5, -8], 'lamp_b', 'core', inflate=0.1)],
    locators=[('fx_tip', [0, 0, -22], 'lance')],
    clips={'strike': {'length': 14, 'loop': 'once', 'bones': {
        'lance': {'position': [k(0, [0, 0, 0]), k(3, [0, 0, 3], 'easeOutQuad'), k(5, [0, 0, -7], 'easeInExpo'), k(8, [0, 0, -7]),
                               k(14, [0, 0, 0], 'easeInOutSine')]}},
        'effects': [(5, 'timeline', 'lance:hit')]},
           'sustain': {'length': 8, 'loop': 'loop', 'bones': {
        'core': {'position': [k(0, [0, 0, 0]), k(4, [0, 0, -8], 'easeInOutSine'), k(8, [0, 0, 0], 'easeInOutSine')]}}}},
    motion='a lance with a wide glowing emitter head between brass fins; it draws back 3 px and thrusts 7')

# ==================================================================================================================
# BACK packs (socket_back / socket_back_1: the socket turns -Z to face out of the frame's back)
# ==================================================================================================================
MODULES['rocket_pack'] = dict(
    slot='BACK', verb='burst',
    bones=[('mount', [0, 0, 0], None, None), ('bell_l', [-3.5, -5, -4.5], 'mount', None), ('bell_r', [3.5, -5, -4.5], 'mount', None)],
    cubes=[plate(8, 6),
           C('saddle', [-4, -4, -3], [4, 4, -1], 'dsteel', 'mount'),
           C('rocket_l', [-5, -5, -6], [-2, 5, -3], 'enamel', 'mount'),
           C('rocket_r', [2, -5, -6], [5, 5, -3], 'enamel', 'mount'),
           C('nose_l', [-4.5, 5, -5.5], [-2.5, 7, -3.5], 'brass', 'mount'),
           C('nose_r', [2.5, 5, -5.5], [4.5, 7, -3.5], 'brass', 'mount'),
           C('fin_l', [-7, -5, -5], [-5, -1, -4], 'yellow', 'mount'),
           C('fin_r', [5, -5, -5], [7, -1, -4], 'yellow', 'mount'),
           C('throat_l', [-4.5, -6, -5.5], [-2.5, -5, -3.5], 'dsteel', 'bell_l'),
           C('flare_l', [-5.5, -8, -6.5], [-1.5, -6, -2.5], 'steel', 'bell_l'),
           C('throat_r', [2.5, -6, -5.5], [4.5, -5, -3.5], 'dsteel', 'bell_r'),
           C('flare_r', [1.5, -8, -6.5], [5.5, -6, -2.5], 'steel', 'bell_r')],
    locators=[('fx_jet_0', [-3.5, -8, -4.5], 'bell_l'), ('fx_jet_1', [3.5, -8, -4.5], 'bell_r')],
    clips={'burst': {'length': 6, 'loop': 'once', 'bones': {
        'bell_l': {'rotation': [k(0, [0, 0, 0]), k(1, [-20, 0, -12], 'easeOutExpo'), k(6, [0, 0, 0], 'easeInOutSine')]},
        'bell_r': {'rotation': [k(0, [0, 0, 0]), k(1, [-20, 0, 12], 'easeOutExpo'), k(6, [0, 0, 0], 'easeInOutSine')]},
        'mount': {'position': [k(0, [0, 0, 0]), k(1, [0, -1.5, 0], 'easeOutExpo'), k(6, [0, 0, 0], 'easeInOutSine')]}},
        'effects': [(0, 'timeline', 'rocket:burst')]},
           'sustain': {'length': 4, 'loop': 'loop', 'bones': {
        'bell_l': {'rotation': [k(0, [-12, 0, -6]), k(2, [-16, 3, -9]), k(4, [-12, 0, -6])]},
        'bell_r': {'rotation': [k(0, [-12, 0, 6]), k(2, [-16, -3, 9]), k(4, [-12, 0, 6])]}}}},
    motion='two finned rockets with flared bells; on a burst the bells splay out 20 degrees, thrusting they shake splayed')

MODULES['glider_wings'] = dict(
    slot='BACK', verb='deploy',
    bones=[('mount', [0, 0, 0], None, None), ('wing_l', [-2, 5, -2], 'mount', None), ('wing_r', [2, 5, -2], 'mount', None)],
    cubes=[plate(8, 6),
           C('spine', [-2, -6, -3], [2, 6, -1], 'dsteel', 'mount'),
           C('hinge_l', [-3, 4, -3.5], [-1, 7, -0.5], 'brass', 'wing_l'),
           C('spar_l', [-4, -8, -3], [-2, 5, -1], 'brass', 'wing_l'),
           C('web_l_root', [-7, -3, -2.5], [-4, 4, -1.5], 'enamel', 'wing_l'),
           C('web_l_mid', [-6, -6, -2.5], [-4, -3, -1.5], 'enamel', 'wing_l'),
           C('web_l_tip', [-5, -8, -2.5], [-4, -6, -1.5], 'enamel', 'wing_l'),
           C('rib_l', [-7, 0, -3.5], [-4, 1, -2.5], 'steel', 'wing_l'),
           C('hinge_r', [1, 4, -3.5], [3, 7, -0.5], 'brass', 'wing_r'),
           C('spar_r', [2, -8, -3], [4, 5, -1], 'brass', 'wing_r'),
           C('web_r_root', [4, -3, -2.5], [7, 4, -1.5], 'enamel', 'wing_r'),
           C('web_r_mid', [4, -6, -2.5], [6, -3, -1.5], 'enamel', 'wing_r'),
           C('web_r_tip', [4, -8, -2.5], [5, -6, -1.5], 'enamel', 'wing_r'),
           C('rib_r', [4, 0, -3.5], [7, 1, -2.5], 'steel', 'wing_r')],
    locators=[('fx_tip_l', [-3, -8, -2], 'wing_l'), ('fx_tip_r', [3, -8, -2], 'wing_r')],
    clips={'deploy': {'length': 10, 'loop': 'hold', 'bones': {
        'wing_l': {'rotation': [k(0, [0, 0, 0]), k(8, [0, 0, -96], 'easeOutCubic'), k(10, [0, 0, -90], 'easeInOutSine')]},
        'wing_r': {'rotation': [k(0, [0, 0, 0]), k(8, [0, 0, 96], 'easeOutCubic'), k(10, [0, 0, 90], 'easeInOutSine')]}},
        'effects': [(8, 'timeline', 'wings:open')]},
           'retract': {'length': 10, 'loop': 'hold', 'bones': {
        'wing_l': {'rotation': [k(0, [0, 0, -90]), k(10, [0, 0, 0], 'easeInOutCubic')]},
        'wing_r': {'rotation': [k(0, [0, 0, 90]), k(10, [0, 0, 0], 'easeInOutCubic')]}}}},
    motion='two tapered wings folded down the back on brass hinges; deploy swings them out level, 26 px tip to tip')

MODULES['cargo_hold'] = dict(
    slot='BACK', verb='deploy',
    bones=[('mount', [0, 0, 0], None, None), ('lid', [0, 4, -1], 'mount', None)],
    cubes=[plate(8, 6),
           C('crate', [-5, -5, -9], [5, 4, -1], 'steel', 'mount'),
           C('strap_t', [-5.5, 1, -9.5], [5.5, 2, -0.5], 'rubber', 'mount'),
           C('strap_b', [-5.5, -3.5, -9.5], [5.5, -2.5, -0.5], 'rubber', 'mount'),
           C('corner_l', [-5.5, -5.5, -9.5], [-4.5, -4.5, -0.5], 'brass', 'mount'),
           C('corner_r', [4.5, -5.5, -9.5], [5.5, -4.5, -0.5], 'brass', 'mount'),
           C('lid_plate', [-5.5, 4, -9.5], [5.5, 5, -0.5], 'enamel', 'lid'),
           C('lid_handle', [-2, 5, -6], [2, 6, -4], 'brass', 'lid')],
    locators=[('fx_hold', [0, 4, -5], 'mount')],
    clips={'deploy': {'length': 8, 'loop': 'hold', 'bones': {
        'lid': {'rotation': [k(0, [0, 0, 0]), k(6, [84, 0, 0], 'easeOutCubic'), k(8, [78, 0, 0], 'easeInOutSine')]}},
        'effects': [(1, 'timeline', 'hold:open')]},
           'retract': {'length': 6, 'loop': 'hold', 'bones': {
        'lid': {'rotation': [k(0, [78, 0, 0]), k(5, [-2, 0, 0], 'easeInQuad'), k(6, [0, 0, 0])]}},
        'effects': [(5, 'timeline', 'hold:shut')]}},
    motion='a strapped crate; the lid swings up 80 degrees when the hold is opened')

MODULES['missile_pod'] = dict(
    slot='BACK', verb='recoil',
    bones=[('mount', [0, 0, 0], None, None), ('pod', [0, 0, -1], 'mount', [50, 0, 0]), ('hatch', [0, 4.5, -9.5], 'pod', None)],
    cubes=[plate(8, 6),
           C('box', [-5, -4, -9], [5, 4, -1], 'dsteel', 'pod'),
           C('tube_0', [-4, -3, -9.5], [-1, 0, -8.5], 'lens', 'pod'),
           C('tube_1', [1, -3, -9.5], [4, 0, -8.5], 'lens', 'pod'),
           C('tube_2', [-4, 0.5, -9.5], [-1, 3.5, -8.5], 'lens', 'pod'),
           C('tube_3', [1, 0.5, -9.5], [4, 3.5, -8.5], 'lens', 'pod'),
           C('hatch_plate', [-5.5, -4.5, -10.5], [5.5, 4.5, -9.5], 'enamel', 'hatch'),
           C('hatch_stripe', [-5.5, -1, -11.5], [5.5, 1, -10.5], 'yellow', 'hatch'),
           C('cradle', [-4, -3, -3], [4, -1, -1], 'steel', 'mount')],
    locators=[('fx_tube', [0, 0, -10], 'pod')],
    clips={'deploy': {'length': 6, 'loop': 'hold', 'bones': {
        'hatch': {'rotation': [k(0, [0, 0, 0]), k(5, [110, 0, 0], 'easeOutCubic'), k(6, [104, 0, 0])]}}},
           'recoil': {'length': 8, 'loop': 'once', 'bones': {
        'hatch': {'rotation': [k(0, [104, 0, 0])]},
        'pod': {'rotation': [k(0, [0, 0, 0]), k(1, [-8, 0, 0], 'easeOutExpo'), k(8, [0, 0, 0], 'easeInOutSine')]}},
        'effects': [(0, 'timeline', 'missile:launch')]},
           'idle': {'length': 1, 'loop': 'hold', 'bones': {'hatch': {'rotation': [k(0, [0, 0, 0])]}}}},
    motion='a four-tube pod raised 50 degrees; the striped hatch flips open, the pod kicks per launch')

MODULES['smoke_mortar'] = dict(
    slot='BACK', verb='recoil',
    bones=[('mount', [0, 0, 0], None, None), ('tube', [0, -1, -3.5], 'mount', [60, 0, 0])],
    cubes=[plate(8, 6),
           C('base', [-4, -4, -6], [4, -1, -1], 'dsteel', 'mount'),
           C('barrel', [-2, -2, -11], [2, 2, -3], 'steel', 'tube'),
           C('muzzle', [-2.5, -2.5, -12], [2.5, 2.5, -11], 'brass', 'tube'),
           C('band', [-2.5, -2.5, -8], [2.5, 2.5, -7], 'yellow', 'tube')],
    locators=[('fx_muzzle', [0, 0, -12], 'tube')],
    clips={'recoil': {'length': 10, 'loop': 'once', 'bones': {
        'tube': {'position': [k(0, [0, 0, 0]), k(1, [0, 0, 3], 'easeOutExpo'), k(10, [0, 0, 0], 'easeInOutSine')]}},
        'effects': [(0, 'timeline', 'mortar:fire')]}},
    motion='a stubby tube raised 60 degrees on a squat base; it slams back 3 px per round')

MODULES['radiator_fins'] = dict(
    slot='BACK', verb='sustain',
    bones=[('mount', [0, 0, 0], None, None)] + [('fin_%d' % i, [0, -4 + 2 * i, -4], 'mount', None) for i in range(5)],
    cubes=[plate(8, 6),
           C('frame_l', [-6, -6, -6], [-5, 6, -1], 'dsteel', 'mount'),
           C('frame_r', [5, -6, -6], [6, 6, -1], 'dsteel', 'mount'),
           C('frame_t', [-5, 5, -6], [5, 6, -1], 'dsteel', 'mount'),
           C('frame_b', [-5, -6, -6], [5, -5, -1], 'dsteel', 'mount'),
           C('core', [-5, -5, -2], [5, 5, -1], 'copper', 'mount')]
          + [C('fin_c%d' % i, [-5, -4.5 + 2 * i, -6], [5, -3.5 + 2 * i, -2], 'steel', 'fin_%d' % i) for i in range(5)],
    locators=[('fx_heat', [0, 0, -6], 'mount')],
    clips={'sustain': {'length': 12, 'loop': 'loop', 'bones': dict(
        ('fin_%d' % i, {'rotation': [k(0, [-60, 0, 0]), k(3 + i, [-80, 0, 0], 'easeInOutSine'), k(12, [-60, 0, 0], 'easeInOutSine')]})
        for i in range(5))},
           'deploy': {'length': 8, 'loop': 'hold', 'bones': dict(
        ('fin_%d' % i, {'rotation': [k(0, [0, 0, 0]), k(1 + i, [0, 0, 0]), k(3 + i, [-60, 0, 0], 'easeOutQuad')]}) for i in range(5))}},
    motion='five louvred fins in a frame, closed at rest; venting, they stand open 60-80 degrees in a ripple')


# ==================================================================================================================
def size(c):
    return [abs(c[2][i] - c[1][i]) for i in range(3)]


HEAVY = 2        # the heavy size class: for the Bulwark, Colossus and Executive (and anything 3+ blocks tall)


def r2(v):
    return round(v * 2) / 2.0


def heavy(m):
    """The same module at the heavy size: every coordinate and cube size doubled (whole pixels, one texel size: nothing
    scaled at run time), clip translations doubled, angles kept."""
    hm = dict(m)
    hm['bones'] = [(n, [r2(c * HEAVY) for c in piv], p, rot) for n, piv, p, rot in m['bones']]
    cubes = []
    for name, f, t, mat, par, kw in m['cubes']:
        lo = [min(f[i], t[i]) for i in range(3)]
        sz = [max(1, round(abs(t[i] - f[i]) * HEAVY)) for i in range(3)]
        mid = [(f[i] + t[i]) / 2.0 * HEAVY for i in range(3)]
        a = [r2(mid[i] - sz[i] / 2.0) for i in range(3)]
        b = [a[i] + sz[i] for i in range(3)]
        if b[2] > 0 and par == 'mount':        # nothing past the mount face
            a[2], b[2] = a[2] - b[2], 0.0
        cubes.append((name, a, b, mat, par, kw))
    hm['cubes'] = cubes
    hm['locators'] = [(n, [r2(c * HEAVY) for c in pos], p) for n, pos, p in m['locators']]
    clips = {}
    for cn, c in m['clips'].items():
        c2 = dict(c)
        c2['bones'] = {}
        for bone, chans in c['bones'].items():
            c2['bones'][bone] = {ch: ([(kk[0], [v * HEAVY for v in kk[1]]) + tuple(kk[2:]) for kk in keys]
                                      if ch == 'position' else keys) for ch, keys in chans.items()}
        clips[cn] = c2
    hm['clips'] = clips
    return hm


def check(name, m):
    bad = [c[0] for c in m['cubes'] if any(abs(s - round(s)) > 1e-6 or s < 1 - 1e-6 for s in size(c))]
    plus = [c[0] for c in m['cubes'] if c[4] == 'mount' and max(c[1][2], c[2][2]) > 1e-6]
    cp = []
    cs = m['cubes']
    for i, a in enumerate(cs):
        for b in cs[i + 1:]:
            if a[4] != b[4]:
                continue
            for ax in range(3):
                for va, vb in ((a[1][ax], b[1][ax]), (a[2][ax], b[2][ax])):
                    if abs(va - vb) < 1e-6:
                        o = [j for j in range(3) if j != ax]
                        if all(min(a[2][j], b[2][j]) - max(a[1][j], b[1][j]) > 1e-6 for j in o):
                            cp.append((a[0], b[0], 'xyz'[ax]))
    return bad, plus, cp


def pack(cs):
    offs = {}
    x = y = shelf = 0
    for c in sorted(cs, key=lambda c: (c[3], -(abs(c[2][1] - c[1][1]) + abs(c[2][2] - c[1][2])))):
        w, h, d = [max(1, round(s)) for s in size(c)]
        uw, uh = 2 * (w + d), d + h
        if x + uw > TEX[0]:
            y += shelf
            x = shelf = 0
        offs[c[0]] = [x, y]
        x += uw
        shelf = max(shelf, uh)
    if y + shelf > TEX[1]:
        sys.exit('texture overflow')
    return offs


def draw(cs, offs, path):
    from PIL import Image
    img = Image.new('RGBA', TEX, (0, 0, 0, 0))
    px = img.load()
    for c in cs:
        w, h, d = [max(1, round(s)) for s in size(c)]
        ox, oy = offs[c[0]]
        hexv, alpha = MATERIALS[c[3]]
        col = tuple(int(hexv[i:i + 2], 16) for i in (0, 2, 4)) + (alpha,)
        for rx, ry, rw, rh in [(ox + d, oy, w * 2, d), (ox, oy + d, 2 * (w + d), h)]:
            for yy in range(ry, ry + rh):
                for xx in range(rx, rx + rw):
                    px[xx, yy] = col
    img.save(path)


def clip_json(model, clips):
    groups = {g['name']: g['uuid'] for g in model['groups']}
    out = []
    for name, c in clips.items():
        animators = {}
        for bone, chans in c['bones'].items():
            kfs = []
            for ch, keys in chans.items():
                for key in keys:
                    t, v = key[0], key[1]
                    interp = 'step' if ch == 'scale' else 'linear'
                    kf = {'channel': ch, 'data_points': [{'x': v[0], 'y': v[1], 'z': v[2]}], 'uuid': str(uuid.uuid4()),
                          'time': round(t / 20.0, 4), 'color': -1, 'interpolation': interp}
                    if len(key) > 2 and interp == 'linear':
                        kf['easing'] = key[2]
                    kfs.append(kf)
            animators[groups[bone]] = {'name': bone, 'type': 'bone', 'keyframes': kfs}
        effs = [{'channel': kind, 'data_points': [{'script': s}], 'uuid': str(uuid.uuid4()), 'time': round(t / 20.0, 4),
                 'color': -1, 'interpolation': 'linear'} for (t, kind, s) in c.get('effects', [])]
        if effs:
            animators['effects'] = {'name': 'Effects', 'type': 'effect', 'keyframes': effs}
        out.append({'uuid': str(uuid.uuid4()), 'name': name, 'loop': c['loop'], 'override': False,
                    'length': c['length'] / 20.0, 'snapping': 20, 'selected': False, 'animators': animators})
    return out


def main():
    problems = 0
    for name, m in list(MODULES.items()):
        MODULES[name + '_heavy'] = heavy(m)
    for name, m in MODULES.items():
        bad, plus, cp = check(name, m)
        if bad or plus or cp:
            problems += 1
            sys.stderr.write('%s: sizes %s, +z %s, coplanar %s\n' % (name, bad, plus, cp))
    if problems or '--check' in sys.argv:
        sys.exit(1 if problems else 0)
    for name, m in MODULES.items():
        d = os.path.join(HERE, name.replace('_heavy', ''))
        os.makedirs(d, exist_ok=True)
        offs = pack(m['cubes'])
        png = os.path.join(d, '%s_placeholder.png' % name)
        draw(m['cubes'], offs, png)
        path = os.path.join(d, '%s.bbmodel' % name)
        bbbuild.build(path, name, m['bones'], m['cubes'], m['locators'], offs, png, TEX, keep_animations=False)
        with open(path, encoding='utf-8') as f:
            model = json.load(f)
        model['animations'] = clip_json(model, m['clips'])
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(model, f, indent=1)
    print('wrote %d module files' % len(MODULES))


if __name__ == '__main__':
    main()

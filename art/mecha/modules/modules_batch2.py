"""Module shapes, third batch: Armor Plating, the last back packs, the sensors, the legs modules and three torso modules,
each in art/mecha/modules/<module>/ in the light and the heavy size class.

    python art/mecha/modules/modules_batch2.py            # writes <module>/<module>.bbmodel and <module>_heavy.bbmodel
    python art/mecha/modules/modules_batch2.py --check    # sizes and coplanar faces only

Same mount convention, helpers and size classes as modules_more.py (imported): the origin is the mount point, the
working end toward -Z, the top bone `mount` (or `mount_thigh` and `mount_shin` for a legs module, drawn on each leg's
outer face at socket_thigh_l/r and socket_shin_l/r), the heavy class every coordinate doubled.

Placement notes:
- Torso modules hang DOWN from their socket (nothing above y 0 but the mount plate), and stay 5 px wide (10 heavy),
  so a chest socket beside a lamp or a name plate never reaches it.
- Sensors stay within 8 x 8 x 9 and have one size only (no heavy class): a sensor socket points up off a turret or
  forward off a chest, close under the jar, so a doubled sensor would reach the jar's base.
- Armor Plating's heavy class is hand-set at 1.5x (7 x 14 px), not doubled: doubled plates made one wall over a chest.
- Legs modules dress the outside of a leg at the thigh and shin sockets (each socket's -Z points out of the leg); none
  replaces a frame's leg bones.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.dirname(HERE))
import bbbuild  # noqa: E402
import modules_more as M  # noqa: E402
from modules_more import C, k  # noqa: E402

M.MATERIALS.update({'lamp_g': ('77f08a', 255), 'lamp_r': ('ff5a36', 255), 'lamp_e': ('3fbfa6', 255)})
MODULES = {}


def small_plate(w=4, h=3, top=1):
    return C('plate', [-w / 2.0, top - h, -1], [w / 2.0, top, 0], 'dsteel', 'mount')


# ==================================================================================================================
# Armor Plating (TORSO): three overlapping slats hung below the socket, riveted; they rattle when hit
# ==================================================================================================================
MODULES['armor_plating'] = dict(
    slot='TORSO', verb='hit',
    bones=[('mount', [0, 0, 0], None, None), ('plate_0', [0, -1.5, -1], 'mount', None), ('plate_1', [0, -5.5, -1], 'mount', None), ('plate_2', [0, -9.5, -1], 'mount', None)],
    cubes=[small_plate(4, 3, 1),
           C('hanger', [-1, -10, -2], [1, 1, -1], 'dsteel', 'mount'),
           C('plate_0', [-2.5, -3, -3.0], [2.5, 0, -1], 'steel', 'plate_0'),
           C('rivet_0a', [-2.0, -2.0, -4.0], [-1.0, -1.0, -3.0], 'brass', 'plate_0'),
           C('rivet_0b', [1.0, -2.0, -4.0], [2.0, -1.0, -3.0], 'brass', 'plate_0'),
           C('plate_1', [-2.5, -7, -4], [2.5, -4, -1], 'steel', 'plate_1'),
           C('rivet_1a', [-2.0, -6.0, -5], [-1.0, -5.0, -4], 'brass', 'plate_1'),
           C('rivet_1b', [1.0, -6.0, -5], [2.0, -5.0, -4], 'brass', 'plate_1'),
           C('plate_2', [-2.5, -11, -4.0], [2.5, -8, -1], 'steel', 'plate_2'),
           C('rivet_2a', [-2.0, -10.0, -5.0], [-1.0, -9.0, -4.0], 'brass', 'plate_2'),
           C('rivet_2b', [1.0, -10.0, -5.0], [2.0, -9.0, -4.0], 'brass', 'plate_2'),],
    locators=[('fx_hit', [0, -4, -4], 'plate_1')],
    clips={'hit': {'length': 6, 'loop': 'once', 'bones': {'plate_0': {'position': [k(0, [0, 0, 0]), k(1, [0, 1.5, -0.5], 'easeOutExpo'), k(6, [0, 0, 0], 'easeInOutSine')], 'rotation': [k(0, [0, 0, 0]), k(1, [-12, 0, 0], 'easeOutExpo'), k(6, [0, 0, 0], 'easeInOutSine')]}, 'plate_1': {'position': [k(0, [0, 0, 0]), k(1, [0, 0, -0.5], 'easeOutExpo'), k(6, [0, 0, 0], 'easeInOutSine')], 'rotation': [k(0, [0, 0, 0]), k(1, [0, 0, 0], 'easeOutExpo'), k(6, [0, 0, 0], 'easeInOutSine')]}, 'plate_2': {'position': [k(0, [0, 0, 0]), k(1, [0, -1.5, -0.5], 'easeOutExpo'), k(6, [0, 0, 0], 'easeInOutSine')], 'rotation': [k(0, [0, 0, 0]), k(1, [12, 0, 0], 'easeOutExpo'), k(6, [0, 0, 0], 'easeInOutSine')]}}}},
    motion='three separate riveted plates with dark gaps, hung below the socket; a hit throws them apart like shutters')
HEAVY_OWN = {'armor_plating': dict(
    slot='TORSO', verb='hit',
    bones=[('mount', [0, 0, 0], None, None), ('plate_0', [0, -2.0, -1], 'mount', None), ('plate_1', [0, -7.0, -1], 'mount', None), ('plate_2', [0, -12.0, -1], 'mount', None)],
    cubes=[small_plate(4, 3, 1),
           C('hanger', [-1, -13, -2], [1, 1, -1], 'dsteel', 'mount'),
           C('plate_0', [-3.5, -4, -4.0], [3.5, 0, -1], 'steel', 'plate_0'),
           C('rivet_0a', [-3.0, -2.0, -5.0], [-2.0, -1.0, -4.0], 'brass', 'plate_0'),
           C('rivet_0b', [2.0, -2.0, -5.0], [3.0, -1.0, -4.0], 'brass', 'plate_0'),
           C('plate_1', [-3.5, -9, -5], [3.5, -5, -1], 'steel', 'plate_1'),
           C('rivet_1a', [-3.0, -7.0, -6], [-2.0, -6.0, -5], 'brass', 'plate_1'),
           C('rivet_1b', [2.0, -7.0, -6], [3.0, -6.0, -5], 'brass', 'plate_1'),
           C('plate_2', [-3.5, -14, -5.0], [3.5, -10, -1], 'steel', 'plate_2'),
           C('rivet_2a', [-3.0, -12.0, -6.0], [-2.0, -11.0, -5.0], 'brass', 'plate_2'),
           C('rivet_2b', [2.0, -12.0, -6.0], [3.0, -11.0, -5.0], 'brass', 'plate_2'),],
    locators=[('fx_hit', [0, -4, -4], 'plate_1')],
    clips={'hit': {'length': 6, 'loop': 'once', 'bones': {'plate_0': {'position': [k(0, [0, 0, 0]), k(1, [0, 1.5, -0.5], 'easeOutExpo'), k(6, [0, 0, 0], 'easeInOutSine')], 'rotation': [k(0, [0, 0, 0]), k(1, [-12, 0, 0], 'easeOutExpo'), k(6, [0, 0, 0], 'easeInOutSine')]}, 'plate_1': {'position': [k(0, [0, 0, 0]), k(1, [0, 0, -0.5], 'easeOutExpo'), k(6, [0, 0, 0], 'easeInOutSine')], 'rotation': [k(0, [0, 0, 0]), k(1, [0, 0, 0], 'easeOutExpo'), k(6, [0, 0, 0], 'easeInOutSine')]}, 'plate_2': {'position': [k(0, [0, 0, 0]), k(1, [0, -1.5, -0.5], 'easeOutExpo'), k(6, [0, 0, 0], 'easeInOutSine')], 'rotation': [k(0, [0, 0, 0]), k(1, [12, 0, 0], 'easeOutExpo'), k(6, [0, 0, 0], 'easeInOutSine')]}}}},
    motion='three separate riveted plates with dark gaps, hung below the socket; a hit throws them apart like shutters')}

# ==================================================================================================================
# BACK
# ==================================================================================================================
MODULES['passenger_pod'] = dict(
    slot='BACK', verb='deploy',
    bones=[('mount', [0, 0, 0], None, None), ('canopy', [0, 4, -1.5], 'mount', None)],
    cubes=[M.plate(8, 6),
           C('tub', [-3.5, -4, -7], [3.5, 1, -1], 'enamel', 'mount'),
           C('rim', [-4.5, 1, -7.5], [4.5, 2, -0.5], 'brass', 'mount'),
           C('seat', [-2, -3, -3], [2, 3, -2], 'rubber', 'mount'),
           C('step', [-1.5, -6, -5], [1.5, -4, -3], 'dsteel', 'mount'),
           C('dome', [-3, 2, -7], [3, 4, -1], 'vial', 'canopy'),
           C('dome_top', [-2, 4, -6], [2, 5, -2], 'vial', 'canopy'),
           C('dome_frame', [-0.5, 2.5, -7.5], [0.5, 5.5, -0.5], 'brass', 'canopy')],
    locators=[('fx_seat', [0, -1, -4], 'mount')],
    clips={'deploy': {'length': 8, 'loop': 'hold', 'bones': {
        'canopy': {'rotation': [k(0, [0, 0, 0]), k(6, [70, 0, 0], 'easeOutCubic'), k(8, [64, 0, 0], 'easeInOutSine')]}},
        'effects': [(1, 'timeline', 'pod:open')]},
           'retract': {'length': 6, 'loop': 'hold', 'bones': {
        'canopy': {'rotation': [k(0, [64, 0, 0]), k(5, [-2, 0, 0], 'easeInQuad'), k(6, [0, 0, 0])]}}}},
    motion='a little enamel tub with a seat and a glass bubble canopy that swings up 70 degrees for a passenger')

MODULES['battery_rack'] = dict(
    slot='BACK', verb='deploy',
    bones=[('mount', [0, 0, 0], None, None), ('cell_1', [0, 0, -4], 'mount', None)],
    cubes=[M.plate(8, 6),
           C('rack_l', [-6, -6, -6], [-5, 6, -1], 'dsteel', 'mount'),
           C('rack_r', [5, -6, -6], [6, 6, -1], 'dsteel', 'mount'),
           C('rack_b', [-5, -6, -6], [5, -5, -1], 'dsteel', 'mount'),
           C('cell_0', [-4.5, -5, -5], [-1.5, 5, -2], 'copper', 'mount'),
           C('cell_2', [1.5, -5, -5], [4.5, 5, -2], 'copper', 'mount'),
           C('cell_1c', [-1.5, -5, -5.5], [1.5, 5, -2.5], 'copper', 'cell_1'),
           C('cap_0', [-4, 5, -4.5], [-2, 6, -2.5], 'brass', 'mount'),
           C('cap_1', [-1, 5, -5], [1, 6, -3], 'brass', 'cell_1'),
           C('cap_2', [2, 5, -4.5], [4, 6, -2.5], 'brass', 'mount'),
           C('level_0', [-3.5, 2, -6], [-2.5, 4, -5], 'lamp_g', 'mount'),
           C('level_1', [-0.5, 2, -6.5], [0.5, 4, -5.5], 'lamp_g', 'cell_1'),
           C('level_2', [2.5, 2, -6], [3.5, 4, -5], 'lamp_g', 'mount')],
    locators=[('fx_cell', [0, 6, -4], 'cell_1')],
    clips={'deploy': {'length': 8, 'loop': 'once', 'bones': {
        'cell_1': {'position': [k(0, [0, 0, 0]), k(2, [0, 6, 0], 'easeOutQuad'), k(5, [0, 6, 0]), k(7, [0, -0.5, 0], 'easeInQuad'),
                                k(8, [0, 0, 0])]}},
        'effects': [(2, 'timeline', 'battery:eject'), (7, 'timeline', 'battery:seat')]}},
    motion='three copper cells in an open rack, each with a green level lamp; a swap pops the middle cell 6 px up and back')

MODULES['ender_satchel'] = dict(
    slot='BACK', verb='deploy',
    bones=[('mount', [0, 0, 0], None, None), ('flap', [0, 4, -6.5], 'mount', None)],
    cubes=[M.plate(8, 6),
           C('bag', [-5, -5, -6], [5, 4, -1], 'rubber', 'mount'),
           C('inside', [-4, 2, -5.5], [4, 3, -1.5], 'lamp_e', 'mount'),
           C('strap_l', [-4.5, -6, -6.5], [-3.5, 5, -0.5], 'brass', 'mount'),
           C('strap_r', [3.5, -6, -6.5], [4.5, 5, -0.5], 'brass', 'mount'),
           C('flap_plate', [-5.5, -1, -7.5], [5.5, 4, -6.5], 'rubber', 'flap'),
           C('clasp', [-2, -1.5, -8.5], [2, 2.5, -7.5], 'brass', 'flap'),
           C('eye', [-1.5, -1, -9.5], [1.5, 2, -8.5], 'lamp_g', 'flap'),
           C('pupil', [-0.5, 0, -10.5], [0.5, 1, -9.5], 'lens', 'flap')],
    locators=[('fx_eye', [0, 0.5, -9.5], 'flap')],
    clips={'deploy': {'length': 10, 'loop': 'hold', 'bones': {
        'flap': {'rotation': [k(0, [0, 0, 0]), k(4, [-150, 0, 0], 'easeOutCubic'), k(6, [-140, 0, 0], 'easeInOutSine')]}},
        'effects': [(2, 'timeline', 'satchel:open')]},
           'retract': {'length': 6, 'loop': 'hold', 'bones': {
        'flap': {'rotation': [k(0, [-140, 0, 0]), k(5, [2, 0, 0], 'easeInQuad'), k(6, [0, 0, 0])]}}}},
    motion='a black satchel with a big green ender eye on its brass clasp; opened, the flap swings right back over the top and the teal inside shows')

# ==================================================================================================================
# SENSORS (socket_sensor: -Z points up off a turret, or forward off a chest)
# ==================================================================================================================
MODULES['night_optics'] = dict(
    slot='SENSOR', verb='deploy',
    bones=[('mount', [0, 0, 0], None, None), ('goggles', [0, 0, -4], 'mount', None)],
    cubes=[small_plate(4, 4, 2),
           C('post', [-1, -1, -4], [1, 1, -1], 'dsteel', 'mount'),
           C('bar', [-3, -1, -6], [3, 1, -5], 'brass', 'goggles'),
           C('tube_l', [-3.5, -1.5, -8], [-0.5, 1.5, -4], 'dsteel', 'goggles'),
           C('tube_r', [0.5, -1.5, -8], [3.5, 1.5, -4], 'dsteel', 'goggles'),
           C('lens_l', [-3, -1, -9], [-1, 1, -8], 'lamp_g', 'goggles'),
           C('lens_r', [1, -1, -9], [3, 1, -8], 'lamp_g', 'goggles')],
    locators=[('fx_lens', [0, 0, -9], 'goggles')],
    clips={'deploy': {'length': 6, 'loop': 'hold', 'bones': {
        'goggles': {'rotation': [k(0, [0, 0, 0]), k(5, [-94, 0, 0], 'easeOutCubic'), k(6, [-90, 0, 0])]}}},
           'idle': {'length': 1, 'loop': 'hold', 'bones': {'goggles': {'rotation': [k(0, [0, 0, 0])]}}}},
    motion='twin night tubes with their green lenses showing on the ends; switched on they swing over 90 degrees to look ahead')

MODULES['bio_scanner'] = dict(
    slot='SENSOR', verb='sustain',
    bones=[('mount', [0, 0, 0], None, None), ('needle', [0, 0, -5], 'mount', None)],
    cubes=[small_plate(4, 4, 2),
           C('stem', [-1, -1, -3], [1, 1, -1], 'dsteel', 'mount'),
           C('ring_t', [-3, 2, -5], [3, 3, -3], 'brass', 'mount'),
           C('ring_b', [-3, -3, -5], [3, -2, -3], 'brass', 'mount'),
           C('ring_l', [-3, -2, -5], [-2, 2, -3], 'brass', 'mount'),
           C('ring_r', [2, -2, -5], [3, 2, -3], 'brass', 'mount'),
           C('face', [-2, -2, -4], [2, 2, -3], 'lamp_g', 'mount'),
           C('needle_bar', [-0.5, 0, -5], [0.5, 2, -4], 'lens', 'needle')],
    locators=[('fx_scan', [0, 0, -5], 'mount')],
    clips={'sustain': {'length': 12, 'loop': 'loop', 'bones': {
        'needle': {'rotation': [k(0, [0, 0, -60]), k(6, [0, 0, 60], 'easeInOutSine'), k(12, [0, 0, -60], 'easeInOutSine')]},
        'mount': {'rotation': [k(0, [0, 0, 0]), k(6, [0, 20, 0], 'easeInOutSine'), k(12, [0, 0, 0], 'easeInOutSine')]}}}},
    motion='a brass-ringed gauge with a green face and a black needle; scanning, the needle swings 120 degrees and the gauge turns to look')

MODULES['thermal_imaging'] = dict(
    slot='SENSOR', verb='deploy',
    bones=[('mount', [0, 0, 0], None, None), ('shutter', [0, 2.5, -6], 'mount', None)],
    cubes=[small_plate(4, 4, 2),
           C('box', [-2.5, -2.5, -6], [2.5, 2.5, -1], 'dsteel', 'mount'),
           C('fin_a', [-3.5, -2, -5], [-2.5, 2, -2], 'steel', 'mount'),
           C('fin_b', [2.5, -2, -5], [3.5, 2, -2], 'steel', 'mount'),
           C('lens', [-1.5, -1.5, -7], [1.5, 1.5, -6], 'lamp_r', 'mount'),
           C('shutter_plate', [-2, -2.5, -8], [2, 2.5, -7], 'yellow', 'shutter')],
    locators=[('fx_lens', [0, 0, -6.5], 'mount')],
    clips={'deploy': {'length': 4, 'loop': 'hold', 'bones': {
        'shutter': {'rotation': [k(0, [0, 0, 0]), k(4, [100, 0, 0], 'easeOutCubic')]}}},
           'idle': {'length': 1, 'loop': 'hold', 'bones': {'shutter': {'rotation': [k(0, [0, 0, 0])]}}}},
    motion='a finned camera with a hot orange lens behind a yellow shutter that flips open')

MODULES['ore_scanner'] = dict(
    slot='SENSOR', verb='spin',
    bones=[('mount', [0, 0, 0], None, None), ('rotor', [0, 0, -6], 'mount', None)],
    cubes=[small_plate(4, 4, 2),
           C('mast', [-1, -1, -6], [1, 1, -1], 'steel', 'mount'),
           C('collar', [-1.5, -1.5, -3], [1.5, 1.5, -2], 'brass', 'mount'),
           C('arm', [-4, -0.5, -7], [4, 0.5, -6], 'dsteel', 'rotor'),
           C('crystal_l', [-5, -1, -8.5], [-3, 1, -6.5], 'lamp_e', 'rotor'),
           C('crystal_r', [3, -1, -8.5], [5, 1, -6.5], 'lamp_e', 'rotor')],
    locators=[('fx_ping', [0, 0, -7], 'rotor')],
    clips={'spin': {'length': 8, 'loop': 'loop', 'bones': {'rotor': {'rotation': [k(0, [0, 0, 0]), k(4, [0, 0, 180]), k(8, [0, 0, 360])]}}}},
    motion='a mast with a crystal-tipped bar spinning on top, a turn every 8 ticks')

MODULES['target_lock'] = dict(
    slot='SENSOR', verb='sustain',
    bones=[('mount', [0, 0, 0], None, None), ('scope', [0, 0, -3], 'mount', None)],
    cubes=[small_plate(4, 4, 2),
           C('yoke', [-2, -1, -3], [2, 1, -1], 'dsteel', 'mount'),
           C('tube', [-1, -1, -3.5], [1, 6, -1.5], 'steel', 'scope'),
           C('hood', [-1.5, 5.5, -4], [1.5, 6.5, -1], 'dsteel', 'scope'),
           C('reticle', [-0.5, 6.5, -3], [0.5, 7.5, -2], 'lamp_r', 'scope')],
    locators=[('fx_lock', [0, 7, -2.5], 'scope')],
    clips={'sustain': {'length': 16, 'loop': 'loop', 'bones': {
        'scope': {'rotation': [k(0, [0, 0, 0]), k(4, [0, 0, 20], 'easeInOutSine'), k(12, [0, 0, -20], 'easeInOutSine'),
                               k(16, [0, 0, 0], 'easeInOutSine')]}}}},
    motion='a short scope with a red reticle lamp, panning 20 degrees each way while it hunts for a lock')

# ==================================================================================================================
# LEGS (dressings at socket_thigh_l/r and socket_shin_l/r: -Z out of the leg's outer face, +Y up the leg)
# ==================================================================================================================
MODULES['spring_heels'] = dict(
    slot='LEGS', verb='burst',
    bones=[('mount_thigh', [0, 0, 0], None, None), ('mount_shin', [0, 0, 0], None, None), ('spring', [0, -4, -2], 'mount_shin', None)],
    cubes=[C('brace', [-1.5, -2, -2], [1.5, 2, 0], 'dsteel', 'mount_thigh'),
           C('clamp', [-2, 0, -2], [2, 2, 0], 'dsteel', 'mount_shin'),
           C('coil_a', [-2, -1, -5], [2, 0, -1], 'yellow', 'spring'),
           C('coil_b', [-2, -3, -5], [2, -2, -1], 'yellow', 'spring'),
           C('coil_c', [-2, -5, -5], [2, -4, -1], 'yellow', 'spring'),
           C('core', [-0.5, -6, -3.5], [0.5, 1, -2.5], 'bright', 'spring'),
           C('pad', [-2.5, -7, -5.5], [2.5, -6, -0.5], 'rubber', 'spring')],
    locators=[('fx_heel', [0, -7, -3], 'spring')],
    clips={'burst': {'length': 8, 'loop': 'once', 'bones': {
        'spring': {'position': [k(0, [0, 0, 0]), k(2, [0, 2.5, 0], 'easeOutQuad'), k(4, [0, -2.5, -1], 'easeOutExpo'),
                                k(8, [0, 0, 0], 'easeInElastic')]}}, 'effects': [(4, 'timeline', 'spring:launch')]}},
    motion='a yellow coil spring standing 5 px off each shin; it squashes 2.5 px and kicks out 2.5 on a jump')

MODULES['anchor_clamps'] = dict(
    slot='LEGS', verb='deploy',
    bones=[('mount_thigh', [0, 0, 0], None, None), ('mount_shin', [0, 0, 0], None, None), ('spike', [0, -2, -2], 'mount_shin', None)],
    cubes=[C('line', [-0.5, -4, -2], [0.5, 4, -1], 'rubber', 'mount_thigh'),
           C('line_clip', [-1.5, -1, -3], [1.5, 1, 0], 'dsteel', 'mount_thigh'),
           C('sleeve', [-2, -3, -3], [2, 3, 0], 'yellow', 'mount_shin'),
           C('spike_rod', [-1, -9, -2.5], [1, -2, -0.5], 'bright', 'spike'),
           C('spike_tip', [-0.5, -11, -2], [0.5, -9, -1], 'bright', 'spike')],
    locators=[('fx_spike', [0, -11, -1.5], 'spike')],
    clips={'deploy': {'length': 6, 'loop': 'hold', 'bones': {
        'spike': {'position': [k(0, [0, 0, 0]), k(3, [0, -8, 0], 'easeInExpo'), k(6, [0, -8, 0])]}},
        'effects': [(3, 'timeline', 'anchor:bite')]},
           'retract': {'length': 6, 'loop': 'hold', 'bones': {
        'spike': {'position': [k(0, [0, -8, 0]), k(6, [0, 0, 0], 'easeInOutCubic')]}}}},
    motion='a striped sleeve on each shin; braced, a spike drives 8 px down past the foot into the ground')

MODULES['aqua_fins'] = dict(
    slot='LEGS', verb='idle',
    bones=[('mount_thigh', [0, 0, 0], None, None), ('mount_shin', [0, 0, 0], None, None), ('fin', [0, 0, -1], 'mount_shin', None),
           ('fin_t', [0, 0, -1], 'mount_thigh', None)],
    cubes=[C('thigh_root', [-1, -2, -2], [1, 2, 0], 'dsteel', 'mount_thigh'),
           C('thigh_fin', [-0.5, -3, -6], [0.5, 3, -2], 'lamp_e', 'fin_t'),
           C('fin_root', [-1, -3, -2], [1, 3, 0], 'dsteel', 'mount_shin'),
           C('fin_blade', [-0.5, -7, -8], [0.5, 4, -2], 'lamp_e', 'fin'),
           C('fin_rib', [-1, -6.5, -3.5], [1, 3.5, -2.5], 'brass', 'fin')],
    locators=[('fx_fin', [0, -6, -5], 'fin')],
    clips={'idle': {'length': 16, 'loop': 'loop', 'bones': {
        'fin': {'rotation': [k(0, [0, -35, 0]), k(8, [0, 35, 0], 'easeInOutSine'), k(16, [0, -35, 0], 'easeInOutSine')]},
        'fin_t': {'rotation': [k(0, [0, 25, 0]), k(8, [0, -25, 0], 'easeInOutSine'), k(16, [0, 25, 0], 'easeInOutSine')]}}}},
    motion='teal fins standing 6-8 px off each shin and thigh, sculling 35 degrees either way')

MODULES['sprint_servos'] = dict(
    slot='LEGS', verb='spin',
    bones=[('mount_thigh', [0, 0, 0], None, None), ('mount_shin', [0, 0, 0], None, None),
           ('wheel_t', [0, 0, -4], 'mount_thigh', None), ('wheel_s', [0, 0, -4], 'mount_shin', None)],
    cubes=[C('motor_t', [-2, -2, -3], [2, 2, 0], 'dsteel', 'mount_thigh'),
           C('disc_t', [-3.5, -3.5, -4], [3.5, 3.5, -3], 'brass', 'wheel_t'),
           C('spoke_t', [-0.5, -3.5, -5], [0.5, 3.5, -4], 'lens', 'wheel_t'),
           C('motor_s', [-1.5, -1.5, -3], [1.5, 1.5, 0], 'dsteel', 'mount_shin'),
           C('disc_s', [-3, -3, -4], [3, 3, -3], 'brass', 'wheel_s'),
           C('spoke_s', [-0.5, -3, -5], [0.5, 3, -4], 'lens', 'wheel_s')],
    locators=[],
    clips={'spin': {'length': 4, 'loop': 'loop', 'bones': {
        'wheel_t': {'rotation': [k(0, [0, 0, 0]), k(2, [0, 0, 180]), k(4, [0, 0, 360])]},
        'wheel_s': {'rotation': [k(0, [0, 0, 0]), k(2, [0, 0, -180]), k(4, [0, 0, -360])]}}}},
    motion='a 7 px brass flywheel on the hip and a 6 px one at the knee, each with a black spoke, spinning while sprinting')

MODULES['stilts'] = dict(
    slot='LEGS', verb='deploy',
    bones=[('mount_thigh', [0, 0, 0], None, None), ('mount_shin', [0, 0, 0], None, None), ('strut', [0, -2, -2], 'mount_shin', None)],
    cubes=[C('ram', [-1, -3, -2], [1, 3, 0], 'brass', 'mount_thigh'),
           C('housing', [-1.5, -3, -3], [1.5, 3, 0], 'dsteel', 'mount_shin'),
           C('strut_rod', [-0.5, -10, -2], [0.5, -2, -1], 'bright', 'strut'),
           C('strut_foot', [-2, -11, -3.5], [2, -10, 0.5], 'rubber', 'strut')],
    locators=[('fx_foot', [0, -11, -1.5], 'strut')],
    clips={'deploy': {'length': 8, 'loop': 'hold', 'bones': {
        'strut': {'position': [k(0, [0, 0, 0]), k(8, [0, -6, 0], 'easeInOutCubic')]}},
        'effects': [(8, 'timeline', 'stilts:up')]},
           'retract': {'length': 8, 'loop': 'hold', 'bones': {
        'strut': {'position': [k(0, [0, -6, 0]), k(8, [0, 0, 0], 'easeInOutCubic')]}}}},
    motion='a telescoping strut down each shin; extended, its foot reaches 6 px below the frame\'s sole')

MODULES['arachnid_legs'] = dict(
    slot='LEGS', verb='deploy',
    bones=[('mount_thigh', [0, 0, 0], None, None), ('mount_shin', [0, 0, 0], None, None),
           ('upper', [0, 1, -2], 'mount_thigh', [0, 0, 0]), ('lower', [0, 1, -9], 'upper', [0, 0, 0])],
    cubes=[C('hub', [-2, -2, -3], [2, 2, 0], 'dsteel', 'mount_thigh'),
           C('upper_seg', [-1, 0, -9], [1, 2, -2], 'steel', 'upper'),
           C('knuckle', [-1.5, -0.5, -10], [1.5, 2.5, -8], 'brass', 'upper'),
           C('lower_seg', [-0.5, -9, -9.5], [0.5, 1, -8.5], 'steel', 'lower'),
           C('claw', [-1, -10, -10], [1, -9, -8], 'rubber', 'lower'),
           C('shin_guard', [-1.5, -2, -2], [1.5, 2, 0], 'dsteel', 'mount_shin')],
    locators=[('fx_claw', [0, -10, -9], 'lower')],
    clips={'deploy': {'length': 10, 'loop': 'hold', 'bones': {
        'upper': {'rotation': [k(0, [0, 0, 0]), k(8, [0, -30, 0], 'easeOutCubic'), k(10, [0, -26, 0])]},
        'lower': {'rotation': [k(0, [0, 0, 0]), k(8, [-30, 0, 0], 'easeOutCubic'), k(10, [-26, 0, 0])]}}},
           'sustain': {'length': 12, 'loop': 'loop', 'bones': {
        'upper': {'rotation': [k(0, [0, -26, 0]), k(6, [8, -20, 0], 'easeInOutSine'), k(12, [0, -26, 0], 'easeInOutSine')]},
        'lower': {'rotation': [k(0, [-26, 0, 0]), k(6, [-40, 0, 0], 'easeInOutSine'), k(12, [-26, 0, 0], 'easeInOutSine')]}}}},
    motion='a jointed spider leg off each hip, carried out and its claw hanging; deployed they splay, walking they paw')

MODULES['hover_skirt'] = dict(
    slot='LEGS', verb='deploy',
    bones=[('mount_thigh', [0, 0, 0], None, None), ('mount_shin', [0, 0, 0], None, None), ('skirt', [0, 2, -2], 'mount_thigh', None),
           ('fan', [0, -4, -3], 'mount_shin', None)],
    cubes=[C('hinge', [-2, 0, -2], [2, 3, 0], 'dsteel', 'mount_thigh'),
           C('skirt_panel', [-3.5, -5, -3], [3.5, 2, -2], 'enamel', 'skirt'),
           C('skirt_hem', [-4, -6, -4], [4, -5, -1], 'lamp_b', 'skirt'),
           C('duct', [-3, -5, -5], [3, -2, 0], 'dsteel', 'mount_shin'),
           C('fan_blade', [-2.5, -4.5, -4], [2.5, -3.5, -3], 'bright', 'fan'),
           C('glow', [-3.5, -6, -5.5], [3.5, -5, -0.5], 'lamp_b', 'mount_shin')],
    locators=[('fx_fan', [0, -6, -3], 'mount_shin')],
    clips={'deploy': {'length': 6, 'loop': 'hold', 'bones': {
        'skirt': {'rotation': [k(0, [0, 0, 0]), k(5, [32, 0, 0], 'easeOutCubic'), k(6, [28, 0, 0])]}}},
           'spin': {'length': 2, 'loop': 'loop', 'bones': {'fan': {'rotation': [k(0, [0, 0, 0]), k(1, [0, 90, 0]), k(2, [0, 180, 0])]},
                                                           'skirt': {'rotation': [k(0, [28, 0, 0])]}}}},
    motion='an enamel skirt with a glowing blue hem at each hip, and a blue-lit fan duct under each shin; hovering, the skirts flare out 30 degrees')

MODULES['lava_treads'] = dict(
    slot='LEGS', verb='spin',
    bones=[('mount_thigh', [0, 0, 0], None, None), ('mount_shin', [0, 0, 0], None, None), ('track', [0, -4, -3], 'mount_shin', None)],
    cubes=[C('heat_plate', [-2, -2, -3], [2, 2, 0], 'copper', 'mount_thigh'),
           C('frame', [-3.5, -6, -3], [3.5, -2, 0], 'dsteel', 'mount_shin'),
           C('track_t', [-4.5, -2, -5], [4.5, -1, -2], 'copper', 'track'),
           C('track_b', [-4.5, -7, -5], [4.5, -6, -2], 'copper', 'track'),
           C('track_f', [-5.5, -6, -5], [-4.5, -2, -2], 'copper', 'track'),
           C('lug_a', [-3, -1, -5], [-2, 0, -2], 'brass', 'track'),
           C('lug_b', [1, -8, -5], [2, -7, -2], 'brass', 'track')],
    locators=[('fx_track', [0, -7, -3], 'track')],
    clips={'spin': {'length': 4, 'loop': 'loop', 'bones': {
        'track': {'position': [k(0, [0, 0, 0]), k(1, [-1, 0, 0]), k(2, [-2, 0, 0]), k(3, [-3, 0, 0]), k(4, [0, 0, 0])]}}}},
    motion='a copper-red track loop standing 5 px off each shin with brass lugs running round it; a copper heat plate on each thigh')

# ==================================================================================================================
# TORSO (hang down from the chest sockets, 5 px wide)
# ==================================================================================================================
MODULES['thorn_plating'] = dict(
    slot='TORSO', verb='hit',
    bones=[('mount', [0, 0, 0], None, None), ('thorns', [0, -4, -2], 'mount', None)],
    cubes=[small_plate(4, 3, 1),
           C('slab', [-2.5, -9, -2], [2.5, 0, -1], 'dsteel', 'mount'),
           C('thorn_a', [-2, -2.5, -4], [-1, -1.5, -2], 'bright', 'thorns'),
           C('thorn_b', [1, -4.5, -4], [2, -3.5, -2], 'bright', 'thorns'),
           C('thorn_c', [-2, -6.5, -4], [-1, -5.5, -2], 'bright', 'thorns'),
           C('thorn_d', [1, -8.5, -4], [2, -7.5, -2], 'bright', 'thorns'),
           C('thorn_e', [-0.5, -5, -5], [0.5, -4, -2], 'bright', 'thorns')],
    locators=[('fx_hit', [0, -4, -4], 'thorns')],
    clips={'hit': {'length': 8, 'loop': 'once', 'bones': {
        'thorns': {'position': [k(0, [0, 0, 0]), k(1, [0, 0, -4], 'easeOutExpo'), k(4, [0, 0, -4]), k(8, [0, 0, 0], 'easeInOutSine')]}},
        'effects': [(1, 'timeline', 'thorns:prick')]}},
    motion='a dark slab bristling with five steel thorns that shoot out 4 px when struck')

MODULES['reactive_armour'] = dict(
    slot='TORSO', verb='hit',
    bones=[('mount', [0, 0, 0], None, None), ('tile_0', [-1.5, -2.5, -2], 'mount', None)],
    cubes=[small_plate(4, 3, 1),
           C('backing', [-3, -9, -2], [3, 0, -1], 'rubber', 'mount'),
           C('tile_0c', [-2.5, -4, -3], [-0.5, -1, -2], 'yellow', 'tile_0'),
           C('tile_1', [0.5, -4, -3], [2.5, -1, -2], 'yellow', 'mount'),
           C('tile_2', [-2.5, -8, -3], [-0.5, -5, -2], 'yellow', 'mount'),
           C('tile_3', [0.5, -8, -3], [2.5, -5, -2], 'yellow', 'mount')],
    locators=[('fx_blast', [-1.5, -2.5, -3], 'tile_0')],
    clips={'hit': {'length': 6, 'loop': 'hold', 'bones': {
        'tile_0': {'position': [k(0, [0, 0, 0]), k(2, [0, 2, -6], 'easeOutExpo')], 'scale': [k(0, [1, 1, 1]), k(2, [0, 0, 0])]}},
        'effects': [(0, 'timeline', 'reactive:blast')]}},
    motion='four hazard-yellow tiles with black seams between them; a hit blows one off (the game restores it on repair)')

MODULES['energy_shield'] = dict(
    slot='TORSO', verb='sustain',
    bones=[('mount', [0, 0, 0], None, None), ('ring', [0, -4, -3.5], 'mount', None)],
    cubes=[small_plate(4, 3, 1),
           C('emitter_box', [-2, -7, -3], [2, 0, -1], 'dsteel', 'mount'),
           C('ring_t', [-2.5, -1.5, -4], [2.5, -0.5, -3], 'brass', 'ring'),
           C('ring_b', [-2.5, -7.5, -4], [2.5, -6.5, -3], 'brass', 'ring'),
           C('lens', [-1.5, -5.5, -4], [1.5, -2.5, -3], 'lamp_b', 'mount')],
    locators=[('fx_shield', [0, -4, -4], 'mount')],
    clips={'sustain': {'length': 8, 'loop': 'loop', 'bones': {'ring': {'rotation': [k(0, [0, 0, 0]), k(8, [0, 0, 360])]}}}},
    motion='a small projector with a blue lens and a brass ring that turns while the shield is up (the bubble is the game\'s)')


NO_HEAVY = ('night_optics', 'bio_scanner', 'thermal_imaging', 'ore_scanner', 'target_lock')   # sensors: one size


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

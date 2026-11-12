"""Turret Pedestal: geometry.

    python art/machines/turret_pedestal/turret_geo.py    # writes turret_pedestal.bbmodel and its placeholder PNG
    python art/machines/turret_pedestal/turret_anim.py   # then the clips (and the review copy)

A ship's searchlight on its mount (the head is the real Life Support Jar
at 1:1, drawn by the game at an anchor, never a scaled specimen jar): K1 feet and plinth, a steel base drum with the
lamp and the brass nameplate in its front, a brass turntable (crossed 14x2x10 / 10x2x14) that turns, two dark fork
cheeks on it with brass pin caps, and on the pin a brass tray with a brass band round the jar's foot. The jar stands on the
tray and tilts with it; under the tray's front edge the muzzle: a 4x4 brass barrel 7 px long ending in a 6x6 collar
round a 2x2 dark bore, so the glass faces front and the gun is plainly below it. 31 px (the jar is 16 tall), top block.

Bones (3):
    yaw     pivot (0, 8, 0);    rotation CODE (Y: patrol sweep, target chase, the refusal's "no")
    pitch   pivot (0, 13, 0);   child of yaw; rotation CODE (X: aim, unpowered droop, guardian tremble, evoker nod)
    recoil  pivot (0, 12, -8);  child of pitch; position KEYED (the shot clips, the refusal rattle)
The jar: drawn by the game at 1:1 with its base centre at `jar_anchor` (0, 15, 0), in the pitch bone's frame.
Faces -Z; +X is the machine's right.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
import kit  # noqa: E402

PATH = os.path.join(HERE, 'turret_pedestal.bbmodel')
REVIEW = 'build/machines-review/turret_pedestal_review.bbmodel'
JAR_Y = 15


def model():
    m = kit.Model('turret_pedestal', budget=3)
    kit.plinth(m, -5, 5, -5, 5)
    kit.casing(m, 'base', -6, 6, -6, 6, 3, 8, mat='steel')
    # v2: the front of the base is left plain under the barrel (the lamp and the plate read as a face); the lamp on
    # the right flank, the nameplate on the back
    kit.lamp(m, 'east', 6, -2, 3.5)                         # bezel y 3.5-7.5, z -2..2
    kit.makers_plate(m, 'south', 6, -2, 4.5, w=4, h=2, name='nameplate')

    # ---- the turntable (yaw) ----
    m.bone('yaw', [0, 8, 0], code=('rotation',))
    # v2: the turntable dark steel (one brass cradle on a dark mount on a grey base), and the fork made visible: two
    # cheeks 2x3x6 out at x +-5..7 with brass pin caps on their outer faces, on the pitch axis
    m.box('turn_a', [-7, 8, -5], [7, 10, 5], 'dsteel', 'yaw', part='turntable')
    m.box('turn_b', [-5, 8, -7], [5, 10, 7], 'dsteel', 'yaw', part='turntable', off=('up', 'down'))
    m.box('cheek_l', [-7, 10, -3], [-5, 13, 3], 'dsteel', 'yaw', part='cheek_l')
    m.box('cheek_r', [5, 10, -3], [7, 13, 3], 'dsteel', 'yaw', part='cheek_r')
    m.box('pin_l', [-8, 12, -1], [-7, 14, 1], 'brass', 'yaw', part='pin_l')
    m.box('pin_r', [7, 12, -1], [8, 14, 1], 'brass', 'yaw', part='pin_r')

    # ---- the cradle (pitch): the pin's knuckle between the cheeks, the tray, the band round the jar's foot ----
    m.bone('pitch', [0, 13, 0], 'yaw', code=('rotation',))
    m.box('lug_l', [-5, 11, -1], [-4, 14, 1], 'brass', 'pitch', part='lug_l')          # the tray's lugs on the pin
    m.box('lug_r', [4, 11, -1], [5, 14, 1], 'brass', 'pitch', part='lug_r')
    m.box('tray', [-7, 14, -7], [7, 15, 7], 'brass', 'pitch')
    J = JAR_Y
    m.box('band_f', [-8, J, -8], [8, J + 2, -7], 'brass', 'pitch', part='band')
    m.box('band_b', [-8, J, 7], [8, J + 2, 8], 'brass', 'pitch', part='band')
    m.box('band_l', [-8, J, -7], [-7, J + 2, 7], 'brass', 'pitch', part='band')
    m.box('band_r', [7, J, -7], [8, J + 2, 7], 'brass', 'pitch', part='band')
    m.locator('jar_anchor', [0, J, 0], 'pitch')

    # ---- the muzzle (recoil): a barrel under the tray's front, a flared frame round a black mouth ----
    m.bone('recoil', [0, 12, -8], 'pitch')
    # v2: a barrel longer than it is wide (4x4, 7 px) ending in a 6x6x2 collar round a 2x2 dark bore
    m.box('barrel', [-2, 10, -11], [2, 14, -4], 'brass', 'recoil')
    kit.rim(m, 'collar', 'north', -11, -3, 3, 9, 15, mat='brass', parent='recoil', width=2, depth=2)
    m.box('bore', [-1, 11, -12], [1, 13, -11], 'rubber', 'recoil')
    m.locator('muzzle', [0, 12, -14], 'recoil')             # the beam's start (guardian lock) and every shot's spawn

    # ---- standin: the 1:1 Life Support Jar the game draws at jar_anchor ----
    g = m.group('standin_jar', 'pitch')
    m.box('sj_base', [-7, J, -7], [7, J + 1, 7], 'stand', g)
    m.box('sj_glass', [-6.5, J + 1, -6.5], [6.5, J + 13, 6.5], 'glass', g)
    m.box('sj_head', [-4, J + 1.5, -4], [4, J + 9.5, 4], 'standi', g)
    m.box('sj_lid', [-7, J + 13, -7], [7, J + 14, 7], 'stand', g)
    m.box('sj_valve', [-2, J + 14, -2], [2, J + 16, 2], 'stand', g)

    m.shape('lower', (1, 0, 1, 15, 16, 15))
    m.shape('upper', (1, 0, 1, 15, 14, 15))
    return m


if __name__ == '__main__':
    m = model()
    m.write(PATH)
    print(kit.shapes_java(m))

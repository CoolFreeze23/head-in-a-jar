"""Body Locker, the Cloakroom (second design): geometry.

    python art/machines/body_locker/locker_geo.py     # writes body_locker.bbmodel and its placeholder PNG
    python art/machines/body_locker/locker_anim.py    # then the clips (and the review copy)

A theatre cloakroom kiosk: an enamel counter on K1 feet (the lamp and the maker's plate in its rimmed front panel), a
brass countertop 2 px proud with a desk bell on it; behind, a booth: two enamel cheeks and a back, a serving window
10 x 8 and 5 deep onto a dark rubber-lined back room where two empty brass coat-hangers hang from a rail; a teal awning
over the window; a steel shutter box; an enamel header with a brass portrait frame (the card inside it shows what is
checked in, drawn by the renderer); a brass hook on the right cheek where the ticket (No. 27) hangs.

Never a body: the token (the ticket) and the portrait say what is in; the body is "in the back". Checked in, the brass
shutter rolls down over the dark window: the booth's face turns from dark to light, the across-the-room read.
32 px, top block.

Bones (2, budget 2):
    shutter   pivot (0, 21, -1.5); position KEYED (y -7: rolled down over the window; rest = rolled up in the box)
    ticket    pivot (5, 20, -4.5); rotation and scale KEYED (it appears on the hook and swings; scale 0/1 = shown)
Faces -Z; +X is the machine's right (the viewer's left).
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
import kit  # noqa: E402

PATH = os.path.join(HERE, 'body_locker.bbmodel')
REVIEW = 'build/machines-review/body_locker_review.bbmodel'
SHUTTER_DROP = 7


def model():
    m = kit.Model('body_locker', budget=2)
    kit.plinth(m, -6, 6, -4, 5)

    # ---- the counter: 14 x 9 x 11, its front a panel inside a raised 1 px rim; lamp right, plate left ----
    m.box('counter', [-7, 3, -5], [7, 12, 6], 'enamel')
    kit.rim(m, 'counter_rim', 'north', -5, -6, 6, 4, 11, 'enamel', part='counter_rim')
    kit.lamp(m, 'north', -5, 1, 6)                           # the machine's right of the panel (the viewer's left)
    kit.makers_plate(m, 'north', -5, -5, 5)
    # the countertop: brass, 2 px proud at the front (a shadow line under it), 1 px at the sides and back
    m.box('countertop', [-8, 12, -7], [8, 13, 7], 'brass')
    # the desk bell at the counter's left end (v2: the far end from the ticket, so the two never read as one)
    m.box('bell_base', [-6, 13, -6], [-3, 14, -3], 'steel', part='bell')
    m.box('bell_dome', [-5, 14, -5], [-3, 16, -3], 'brass', part='bell_dome')
    m.locator('bell', [-4, 16, -4])

    # ---- the booth (v2: narrowed to x -6..6, so the countertop shows 2 px shoulders and the front steps at y 13): back
    # and cheeks (y 13-32) ----
    m.box('booth_back', [-6, 13, 3], [6, 32, 6], 'enamel')
    m.box('cheek_l', [-6, 13, -3], [-4, 32, 3], 'enamel', part='cheek_l')
    m.box('cheek_r', [4, 13, -3], [6, 32, 3], 'enamel', part='cheek_r')
    # a staff door on the back (the back's one feature)
    m.box('staff_door', [-3, 14, 6], [3, 24, 7], 'enamel2', part='staff_door')
    m.box('door_knob', [1, 18, 7], [2, 19, 8], 'brass', part='door_knob')
    # the back room behind the serving window (x -4..4, y 14-22, 5 deep): rubber floor and back, a brass rail 2 px
    # under the awning, ONE empty coat-hanger on it (v2: two blocks under a rail read as teeth)
    m.box('room_floor', [-4, 13, -3], [4, 14, 3], 'rubber')
    m.box('room_back', [-4, 14, 2], [4, 22, 3], 'rubber')
    m.box('rail', [-4, 19, 0], [4, 20, 1], 'brass', part='rail')
    m.box('hanger_neck', [-1, 17, 0], [1, 19, 1], 'brass', part='hanger')
    m.box('hanger_shoulders', [-3, 15, 0], [3, 17, 1], 'brass', part='hanger')
    m.locator('window', [0, 18, -3])
    # the shutter box over the window, the awning (teal canvas, 3 px proud, 1 px past the booth each side), the header
    m.box('shutter_box', [-4, 22, -3], [4, 25, 3], 'steel')
    m.box('awning', [-7, 22, -6], [7, 24, -3], 'saline', part='awning')
    m.box('header', [-4, 25, -3], [4, 32, 3], 'enamel')
    # the portrait: a brass frame 8x6 proud of the header; the card is the header's face inside it (the renderer draws
    # the head or the frame's item on it, face-on)
    kit.rim(m, 'portrait', 'north', -3, -4, 4, 25, 31, 'brass', depth=1)
    m.anchor('portrait', [0, 26, -3.1], 'the stored host head (head code, fitted to 6x4 px) or the frame item; '
             'face-on, one flat sprite quad within 16 blocks', standin=((6, 4, 1), 'standi'))
    # the hook on the right cheek's front
    m.box('hook', [4, 20, -4], [6, 21, -3], 'brass', part='hook')
    m.locator('hook', [5, 20, -4.5])

    # ---- the shutter (bone): 8 x 8, four brass slats proud of a brass backing (the grooves are real). v2: at rest its
    # bottom slat shows as a brass bar under the awning (y 21-22); it drops 7 px onto the window's floor (y 14) ----
    m.bone('shutter', [0, 21, -1.5])
    m.box('shutter_back', [-4, 21, -1], [4, 29, 0], 'brass', 'shutter', part='shutter')
    for i in range(4):
        y = 21 + 2 * i
        m.box('slat_%d' % i, [-4, y, -2], [4, y + 1, -1], 'brass', 'shutter', part='shutter')

    # ---- the ticket (bone): a tag 2x4x1 on a ring, hung from the hook ----
    m.bone('ticket', [5, 20, -4.5])
    m.box('ticket_ring', [4.5, 19, -5], [5.5, 20, -4], 'brass', 'ticket', part='ticket')
    m.box('ticket_tag', [4, 15, -5], [6, 19, -4], 'yellow', 'ticket', part='ticket')

    m.shape('lower', (1, 0, 3, 15, 12, 14))
    m.shape('lower', (0, 12, 1, 16, 13, 15))
    m.shape('lower', (2, 13, 5, 14, 16, 14))
    m.shape('upper', (2, 0, 5, 14, 16, 14))
    return m


if __name__ == '__main__':
    m = model()
    m.write(PATH)
    print(kit.shapes_java(m))

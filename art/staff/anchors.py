"""Where the staff badge is drawn on each block that wears it: the numbers the renderers use.

    python art/staff/anchors.py          # prints the table (kit px and block px) that goes into the notes
    python art/staff/anchors.py --json   # the same as JSON

Spaces:
- kit px: the .bbmodel's own space (art/machines/<block>/*.bbmodel; GeckoLib model px): origin at the block's bottom
  centre, y up, the front is -Z, +X is the machine's right (the viewer's left).
- block px: the baked block model's space (models/block/<block>.json, 0..16): x and z are kit + 8, y the same (checked on
  jarred_head.json, turret_pedestal.json, employee_mount.json, neural_socket.json and cerebral_array.json: e.g. the
  Turret's base_a kit [-5, 3, -6] is block [3, 3, 2]).
Both are for FACING north (the front is north). The renderers turn the badge with FACING exactly as the block's model
turns. All five badges lie on the front (north) face of a STATIC part (never a bone), so nothing moves them.

Each badge is two quads on one plane facing north, at `z` px (0.05 px in front of the face it lies on):
- frame: FRAME region of staff_badge.png (5x5: the clip on top, the dark edge round a clear 3x2 hole), drawn UNTINTED
  (vertex colour white), over the rect `frame`;
- face: FACE region (3x2), TINTED with the badge colour, over the rect `face` (the frame's hole: 1 px in from its
  sides and bottom, 2 px down from its top). Same plane: the hole is clear, so the two never overlap.
Rects are [x0, y0, x1, y1] in px, x0 < x1, y0 < y1. Seen from the front, the sheet's left column is at the viewer's
LEFT (x1 in kit px; also x1 in block px, since block = kit + 8) and the sheet's top row is at y1. A north-facing quad in
the block model's usual UV orientation does exactly this.
"""
import json
import sys

# regions of the sheet (sheet px): mirrors paint_badge.REGIONS
FRAME_UV = (0, 0, 5, 5)
FACE_UV = (6, 0, 9, 2)

ANCHORS = {
    'plinth': dict(
        holder="the plaque backing (plaque_back) and its nameplate: the clip on the plaque's top rim at its viewer-right "
               "end; the badge covers the nameplate's right 4 px",
        z=-8.05, frame=(-7, 1, -2, 6)),
    'turret': dict(
        holder="the static base drum (base_a) front: the clip on its top row under the turntable's lip, at the "
               "viewer's right of the barrel (off the barrel's line, so barrel and badge do not make a face)",
        z=-6.05, frame=(-5, 3, 0, 8)),
    'array': dict(
        holder="the lowest row's front rail (rail_0 + railcap_0): the clip over the rail's top edge in front of the "
               "middle seat",
        z=-9.05, frame=(-2, 1, 3, 6)),
    'mount': dict(
        holder="the desk's front edge (desk): the clip on its top row at the viewer's right of the brass nameplate, "
               "under the pay tray; the lower 3 px hang below the desk",
        z=-8.05, frame=(-8, 1, -3, 6)),
    'socket': dict(
        holder="the pillar's foot under the level slot: the clip on the slot's lower lip (skin_n_2's top row), the "
               "badge over the skin below and the plinth's front; well away from the lamp so the two never read as a "
               "two-lamp panel",
        z=-7.05, frame=(-5, 0, 0, 5)),
}


def face_rect(frame):
    x0, y0, x1, y1 = frame
    return (x0 + 1, y0 + 1, x1 - 1, y0 + 3)


BLOCKS = [
    dict(key='plinth', bbmodel='art/machines/jarred_head/jarred_head.bbmodel', sheet='jarred_head.png',
         model='models/block/jarred_head.json'),
    dict(key='turret', bbmodel='art/machines/turret_pedestal/turret_pedestal.bbmodel', sheet='turret_pedestal.png',
         model='Turret Pedestal (static body in models/block/turret_pedestal.json)'),
    dict(key='array', bbmodel='art/machines/cerebral_array/cerebral_array.bbmodel', sheet='cerebral_array.png',
         model='Cerebral Array (static body in models/block/cerebral_array.json)'),
    dict(key='mount', bbmodel='art/machines/employee_mount/employee_mount.bbmodel', sheet='employee_mount.png',
         model='Employee Mount (static body in models/block/employee_mount.json)'),
    dict(key='socket', bbmodel='art/machines/neural_socket/neural_socket.bbmodel', sheet='neural_socket.png',
         model='Neural Socket (static body in models/block/neural_socket.json)'),
]


def untinted(u, v):
    """Sheet pixels drawn untinted (the frame: clip and edge)."""
    return FRAME_UV[0] <= u < FRAME_UV[2] and FRAME_UV[1] <= v < FRAME_UV[3]


def quads(key):
    """For the preview: zero-depth planes with a north face, in kit px."""
    a = ANCHORS[key]
    out = []
    for rect, uv in ((a['frame'], FRAME_UV), (face_rect(a['frame']), FACE_UV)):
        x0, y0, x1, y1 = rect
        out.append(dict(face='north', frm=[x0, y0, a['z']], to=[x1, y1, a['z']], uv=list(uv)))
    return out


def block_px(rect):
    x0, y0, x1, y1 = rect
    return (x0 + 8, y0, x1 + 8, y1)


def table():
    rows = ['| Block | Model | Face | kit z | kit frame | kit face | block z | block frame | block face | Lies on |',
            '|---|---|---|---|---|---|---|---|---|---|']
    for b in BLOCKS:
        a = ANCHORS[b['key']]
        rows.append('| %s | %s | north | %.2f | %s | %s | %.2f | %s | %s | %s |' % (
            b['key'], b['model'], a['z'], list(a['frame']), list(face_rect(a['frame'])), a['z'] + 8,
            list(block_px(a['frame'])), list(block_px(face_rect(a['frame']))), a['holder']))
    return '\n'.join(rows)


if __name__ == '__main__':
    if '--json' in sys.argv:
        print(json.dumps(ANCHORS, indent=1))
    else:
        print(table())

"""The DNA Sequencer's Directory tab: textures/gui/directory.png (256 x 256).

    python art/lab/paint_directory.py

Sheet layout (x, y, w, h), as agreed with the screen's code:
    (0, 0, 26, 24)   tab, not selected: drawn at (tabX, topPos - 22); its bottom two rows are clear, so the panel's
                     own top edge closes it whatever the draw order
    (26, 0, 26, 25)  tab, selected: drawn at (tabX, topPos - 22) after the panel; rows 22-24 cover the panel's top
                     outline and its two highlight rows, so the tab opens into the panel
    (52, 0, 26, 25)  tab, selected, for a tab at the panel's left corner (keeps the panel's black left edge)
    (0, 96, 16, 16)  stamp: sampled (a drop of blood, bottom-left)    16x16 overlays, each with its 6x6 pictogram in
    (16, 96, 16, 16) stamp: jarred (a jar, bottom-right)             its own corner: draw every one that applies at
    (32, 96, 16, 16) stamp: ridden (a saddle, top-right)             the cell's item position (cell + 1, cell + 1),
    (48, 96, 16, 16) stamp: hired (an ID badge, top-left; 0.7.0)     after the egg; they never overlap
    (32, 32, 6, 15)  scroll thumb
    (38, 32, 6, 15)  scroll thumb, nothing to scroll
    (0, 48, 16, 6)   the [REDACTED] bar: drawn at (cell + 1, cell + 6), over the egg or the folder
    (0, 64, 16, 16)  personnel folder: a creature with no spawn egg

The tabs speak the screen's own language (vanilla's greys, a lit top-left and a shaded bottom-right, a black outline)
with the corners cut at 45 degrees like a filing folder's tab. The stamps are small outlined pictograms in the house
colours. Every pixel is a palette key; nothing is random.
"""
from pix import blank, put, rect, save, stamp

O, D, G, F, W = 'gui.0', 'gui.2', 'gui.3', 'gui.4', 'gui.5'   # outline, shadow, slot grey, face, highlight
TAB_W, CUT = 26, 3


def tab(im, x0, y0, selected, corner=False):
    """A folder tab 26 wide. Selected: rows 0-21 above the panel, 22-24 over its top edge. Not selected: one row lower,
    rows 1-21, darker face, rows 22-23 clear."""
    face = F if selected else G
    top = 0 if selected else 1
    for y in range(top, 22):
        r = y - top
        lx = max(0, CUT - r)
        rx = TAB_W - 1 - max(0, CUT - r)
        for x in range(lx, rx + 1):
            if r == 0 or x == lx or x == rx:
                k = O
            elif r <= 2 or x - lx <= 2 and r > 0:
                k = W                                   # lit top rows and left side (follows the cut)
            elif rx - x <= 2:
                k = D                                   # shaded right side
            else:
                k = face
            put(im, x0 + x, y0 + y, k)
        # the cut corner's highlight runs along the diagonal: the pixel next to the outline on the right cut is lit
        if 0 < r < CUT + 1 and rx - lx > 4:
            put(im, x0 + rx - 1, y0 + y, W if r <= 2 else D)
    if not selected:
        return
    # rows 22-24 sit over the panel's rows 0-2 (black outline, then two highlight rows)
    for i, y in enumerate((22, 23, 24)):
        for x in range(TAB_W):
            if x <= 2:
                k = (O if x == 0 else W) if (i == 0 or corner) else W
            elif x >= TAB_W - 3:
                k = [[D, D, O], [D, W, W], [face, W, W]][i][x - (TAB_W - 3)]
            else:
                k = face
            put(im, x0 + x, y0 + y, k)


# The stamps: 6x6 outlined pictograms in the house colours, each in its own corner of a 16x16 overlay drawn over the
# grid cell's item (cell + 1, cell + 1), as vanilla puts counts and bars in corners: sampled bottom-left, jarred
# bottom-right, ridden top-right, hired top-left. They never touch one another, and the egg keeps its middle.
# Sampled: a drop of blood, point up, lit top-left (at this size a syringe reads as a slash). Jarred: a steel lid, pale
# green glass round a pink head, saline at the bottom. Ridden: a saddle from the front, cantle and pommel either end of
# the seat, a steel stirrup hanging each side (from the side it read as a boot). Hired: a portrait ID card (photo, name line) hanging from the lanyard's red strap.
DROP = ['..#...', '.#r#..', '#Wrr#.', '#rrr#.', '#rrR#.', '.###..']
JAR = ['######', '#ssss#', '#gffg#', '#gffg#', '#jjjj#', '.####.']
SADDLE = ['#....#', '#B##B#', '#bbbb#', 'q#bb#q', 'q.##.q', '......']
LANYARD = ['.e..e.', '.#ee#.', '.#pp#.', '.#ww#.', '.#kk#.', '.####.']
CORNERS = [(0, 10), (10, 10), (10, 0), (0, 0)]   # sampled, jarred, ridden, hired: each pictogram's place in its 16x16
STAMP_KEYS = {'#': 'rubber.0', 'r': 'red_ink.2', 'R': 'red_ink.1', 'W': 'enamel.4', 's': 'steel.4', 'g': 'jar_glass.4',
              'f': 'flesh.3', 'j': 'jar_glass.2', 'b': 'brass.1', 'B': 'brass.2', 'q': 'steel.3', 'e': 'red_ink.1',
              'w': 'enamel.5', 'p': 'ink.1', 'k': 'ink.2'}
STAMPS = [DROP, JAR, SADDLE, LANYARD]


SCROLL = ['WWWWWD',
          'WFFFFD',
          'WFFFFD',
          'WFFFFD',
          'WFFFFD',
          'WFGGFD',
          'WFFFFD',
          'WFGGFD',
          'WFFFFD',
          'WFGGFD',
          'WFFFFD',
          'WFFFFD',
          'WFFFFD',
          'WFFFFD',
          'DDDDDD']


def scroll(im, x0, y0, enabled):
    leg = {'W': W, 'F': F, 'G': G, 'D': D} if enabled else {'W': W, 'F': G, 'G': D, 'D': D}
    stamp(im, x0, y0, SCROLL, leg)


REDACTED = ['........rrrrrrrr',   # a flat-ended felt-tip bar the full width of the icon, one pixel off the level
            'rrrrrrrrrrrrrrrr',   # where the pen was lifted, one faint streak where it went over twice
            'rrrrrqqqqqqrrrrr',
            'rrrrrrrrrrrrrrrr',
            'rrrrrrrr........',
            '................']

FOLDER = ['................',     # a manila folder: tab, a sheet showing, a paper label on the front flap and
          '.oooo...........',     # a red tag at its corner
          'oMMMMo..........',
          'oMMMMMooooooooo.',
          'oMppppppppppppMo',
          'oMpPPPPPPPPPPpMo',
          'oMpPkkkkkkkPPpMo',
          'oNNNNNNNNNNNNNNo',
          'oNnnnnnnnnnnnnNo',
          'oNnnnnnnnnnnnnNo',
          'oNnPPPPPPnnnnnNo',
          'oNnPkkkkPnnnnnNo',
          'oNnPPPPPPnnnRnNo',
          'oNnnnnnnnnnnRnNo',
          'ommmmmmmmmmmmmmo',
          '.oooooooooooooo.']
FOLDER_KEYS = {'o': 'rubber.1', 'M': 'manila.4', 'N': 'manila.5', 'n': 'manila.4', 'm': 'manila.2', 'p': 'enamel.4',
               'P': 'enamel.5', 'k': 'ink.1', 'R': 'red_ink.1'}


def paint():
    im = blank(256, 256)
    tab(im, 0, 0, False)
    tab(im, 26, 0, True)
    tab(im, 52, 0, True, corner=True)
    for i, (picto, (cx, cy)) in enumerate(zip(STAMPS, CORNERS)):
        stamp(im, i * 16 + cx, 96 + cy, picto, STAMP_KEYS)
    scroll(im, 32, 32, True)
    scroll(im, 38, 32, False)
    stamp(im, 0, 48, REDACTED, {'r': 'rubber.0', 'q': 'rubber.1'})
    stamp(im, 0, 64, FOLDER, FOLDER_KEYS)
    return im


if __name__ == '__main__':
    print(save(paint(), 'gui/directory.png'))

"""The ten-block board: renders made at the size a 19 px machine is drawn at ten blocks, put on a 128 px canvas, and
the same 3x nearest-neighbour.

    python farboard.py OUT.png DIR "label=stem" ...      (DIR holds stem.png; one row of small, one row of 3x)

The kit's renderer fits the model to the image, so a 32 px machine fills about 116 of 128 px (the Pile measured) and
this 19 px one about 106. Ten blocks is the Pile's scale, 3.6 screen px per model px: the renders for this board are
made at 84 px (the mount then about 69 px tall) and centred, bottom-aligned, on a 128 px canvas.
"""
import sys
from PIL import Image, ImageDraw

out, d = sys.argv[1], sys.argv[2]
items = [a.split('=', 1) for a in sys.argv[3:]]
BG = (32, 34, 40, 255)
cells = []
for label, stem in items:
    im = Image.open('%s/%s.png' % (d, stem)).convert('RGBA')
    c = Image.new('RGBA', (128, 128), BG)
    c.alpha_composite(im, ((128 - im.size[0]) // 2, 128 - im.size[1] - 4))
    cells.append((label, c))
n = len(cells)
W = 384
board = Image.new('RGBA', (n * W, 18 + 128 + 18 + 384), (24, 25, 30, 255))
dr = ImageDraw.Draw(board)
for i, (label, c) in enumerate(cells):
    dr.text((i * W + 4, 3), label + ' (ten blocks)', fill=(230, 230, 230, 255))
    board.alpha_composite(c, (i * W + (W - 128) // 2, 18))
    dr.text((i * W + 4, 18 + 128 + 3), label + ' x3', fill=(230, 230, 230, 255))
    board.alpha_composite(c.resize((384, 384), Image.NEAREST), (i * W, 18 + 128 + 18))
board.convert('RGB').save(out)
print(out)

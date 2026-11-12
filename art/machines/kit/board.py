"""Compose labelled renders into one board. usage: board.py OUT.png W label=path ..."""
import sys
from PIL import Image, ImageDraw
out, cols = sys.argv[1], int(sys.argv[2])
items = [a.split('=', 1) for a in sys.argv[3:]]
ims = [(l, Image.open(p).convert('RGB')) for l, p in items]
w = max(i.width for _, i in ims); h = max(i.height for _, i in ims)
rows = (len(ims) + cols - 1) // cols
B = Image.new('RGB', (cols * w, rows * (h + 18)), (24, 26, 32))
d = ImageDraw.Draw(B)
for n, (l, im) in enumerate(ims):
    x, y = (n % cols) * w, (n // cols) * (h + 18)
    B.paste(im, (x, y + 18)); d.text((x + 4, y + 3), l, fill=(230, 230, 230))
B.save(out)

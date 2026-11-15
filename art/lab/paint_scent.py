"""Keen Nose's scent particle: textures/particle/scent_0.png and scent_1.png (4 x 4 each, two frames).

    python art/lab/paint_scent.py

A wisp of scent drifting off a creature whose genes the team has not finished reading: frame 0 a lopsided puff, frame 1
the puff pulled into a curl: a tail at the top right, a pair at the middle left, a fading drop below. Lilac in an
opaque violet edge, so it keeps its hue on grass and sky and is nobody's green or white (vanilla's happy-villager,
bone-meal, end-rod and enchanting sparkles). Seen at night only if the particle draws at full brightness.
"""
from PIL import Image

from lab_palette import C
from pix import save

FRAMES = [['.vv.',
           'vLLv',
           'vLvv',
           '.vv.'],
          ['..Lv',
           '.v..',
           'vv..',
           '..u.']]
KEYS = {'L': ('violet.4', 255), 'v': ('violet.3', 255), 'u': ('violet.3', 120)}


def frame(rows):
    im = Image.new('RGBA', (4, 4), (0, 0, 0, 0))
    px = im.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in KEYS:
                key, alpha = KEYS[ch]
                px[x, y] = C(key)[:3] + (alpha,)
    return im


if __name__ == '__main__':
    for i, rows in enumerate(FRAMES):
        print(save(frame(rows), 'particle/scent_%d.png' % i))

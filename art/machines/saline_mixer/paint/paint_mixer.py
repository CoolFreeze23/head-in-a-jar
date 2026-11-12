"""Saline Mixer (the Soda Fountain): the painted sheet and its state textures, every pixel placed from palette keys.

    python -I art/machines/saline_mixer/paint/paint_mixer.py                # writes the textures into the mod assets
    python -I art/machines/saline_mixer/paint/paint_mixer.py --preview DIR  # also DIR/mixer_rest.png, mixer_work.png
Then adopt the files it prints with tools/tex/tex.py adopt.

Writes, under textures/block/machine/:
  saline_mixer.png            128x144, the baked model and the GeckoLib bones (paddle, caps, tap, flag)
  saline_mixer_ripple.png     + .mcmeta: the saline's top face at rest (4 frames x 10 ticks): two slow ripples drift,
                              a bubble rises and pops once a loop
  saline_mixer_swirl.png      + .mcmeta: the saline's top face under lamp=work (4 frames x 5 ticks = one paddle turn
                              at full speed): a two-armed vortex round a dark dimple
  saline_mixer_water_0..4.png the sight glass, one per `water` level (its element's box-UV layout at (0, 0))

The value plan (110 px first):
  LIGHT  the enamel counter (e2 field, e3 lip and bead, e0-e1 base shadow) and the brass frame of the tank: four
         posts and the lid (b3 field, b4 lit rows, b1 undersides)
  DARK   the bottle niche (rubber r0-r1, the front's one focal dark), the plinth and feet, the tap's knob and collar
  ACCENT the teal saline (l1 body, l3 meniscus) seen through clear glass; the brass caps; the yellow flag
Story marks, once each: a saline drip in the drip tray's slot under the spout; the maker's plate on the back panel;
the shaker holes in the three caps (one construction, not a mark); the hand-polished glint on the tap knob is NOT
drawn (rubber takes no glint).
"""
import argparse
import math
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', 'kit', 'paint'))
import kitpaint as K  # noqa: E402

MDIR = os.path.join(HERE, '..')
BBMODEL = os.path.join(MDIR, 'saline_mixer.bbmodel')
PLACEHOLDER = os.path.join(MDIR, 'saline_mixer_placeholder.png')
SHEET = os.path.join(K.OUT, 'saline_mixer.png')

COUNTER = ['fs_l', 'fs_r', 'fs_sill', 'fs_head', 'k_l', 'k_r', 'k_sill', 'k_head', 'k_back', 'wing_r', 'ws_lo',
           'ws_hi', 'ws_back', 'bs']
GLASS = ['tank', 'can_s', 'can_k', 'can_r']
Y0, Y1 = 3, 14


# ===================================================================================================== state textures
def water_rows(level):
    """The 2x4 sight glass front: empty rows dark glass (t0, a t4 glint at the top left), water rows from the bottom,
    its top row the meniscus (t3), the rest t2."""
    rows = []
    for r in range(4):
        filled = r >= 4 - level
        if filled:
            k = 't3' if r == 4 - level else 't2'
            rows.append([k, k])
        else:
            rows.append(['t4' if r == 0 else 't0', 't0'])
    return rows


def water_textures():
    paths = []
    lay = K._boxuv(2, 4, 1)
    for lv in range(5):
        img = Image.new('RGBA', (16, 16), K.rgba('t1'))
        px = img.load()
        for s, (x0, y0, w, h) in lay.items():
            for r in range(h):
                for c in range(w):
                    px[x0 + c, y0 + r] = K.rgba({'up': 't2', 'down': 't0'}.get(s, 't1'))
        x0, y0 = lay['north'][:2]
        for r, row in enumerate(water_rows(lv)):
            for c, k in enumerate(row):
                px[x0 + c, y0 + r] = K.rgba(k)
        p = os.path.join(K.OUT, 'saline_mixer_water_%d.png' % lv)
        img.save(p)
        paths.append(p)
    return paths


def ripple_frame(f):
    """8x6 (in a 16x16 frame, the rest the same pattern continued): field l1; two 1 px l2 ripple lines that step up
    and down every 2 px and drift one pixel a frame; a bubble at (5, 2) rises (frame 1: l3) and pops (frame 2: an l1
    dimple l0 ringed by l3), gone in frame 3."""
    g = [['l1'] * 16 for _ in range(16)]
    for c in range(16):
        for base in (1, 4, 7, 10, 13):
            r = base + ((c + f) // 2) % 2
            if r < 16:
                g[r][c] = 'l2'
    if f == 1:
        g[2][5] = 'l3'
    if f == 2:
        g[2][5] = 'l0'
        g[1][5] = 'l3'
        g[3][5] = 'l3'
    return g


def swirl_frame(f):
    """The vortex: field l1 (stirred); two arms of l3/l2 at 180 degrees, turning 45 degrees a frame round the dimple
    at the face's centre (l0, 2x2 under the shaft)."""
    g = [['l1'] * 16 for _ in range(16)]
    cx, cy = 4.0, 3.0
    for c in range(16):
        for r in range(16):
            dx, dy = c + 0.5 - cx, r + 0.5 - cy
            rad = math.hypot(dx, dy)
            if rad < 1.0:
                g[r][c] = 'l0'
                continue
            ang = math.degrees(math.atan2(dy, dx)) - 45 * f - rad * 40
            a = ang % 180
            if a < 30:
                g[r][c] = 'l3'
            elif a < 55:
                g[r][c] = 'l2'
    return g


def strip(path, frames, frametime):
    img = Image.new('RGBA', (16, 16 * len(frames)))
    px = img.load()
    for i, g in enumerate(frames):
        for r in range(16):
            for c in range(16):
                px[c, 16 * i + r] = K.rgba(g[r][c])
    img.save(path)
    K.write_mcmeta(path, frametime=frametime)
    return path


# ===================================================================================================== the sheet
def counter(S):
    for n in COUNTER:
        for f in S.faces(n, K.SIDES):
            K.enamel_body(f, Y0, Y1)
        S.face(n, 'down').fill('e0')

        def top(c, r, x, y, z):
            if -4 < x < 4 and -3 < z < 3:
                return 'e1'                      # under the saline (hidden)
            if -5 < x < 5 and -4 < z < 4:
                return 'e1'                      # the counter seen inside the glass, round the saline: shadowed
            return 'e4' if (z < -5 or x > 6) else 'e3'
        S.face(n, 'up').by_world(top)
    # the niche's reveal: the left-hand wall (fs_r's west face) in shadow, the right-hand wall (fs_l's east face) lit,
    # the head's underside the reveal's ceiling
    S.face('fs_r', 'west').fill('e1')
    S.face('fs_l', 'east').fill('e3')
    S.face('fs_head', 'down').fill('e0')
    S.face('k_head', 'down').fill('e0')
    # the right side's pressed panel (T9 inset), in the free field between the tap housing (z < -3) and the flag
    # (y < 7): z -2..4, y 7..12; its top row and left column (the back, +z, as seen) in shadow, bottom and right lit
    for n in ('k_r', 'wing_r', 'fs_r'):
        def inset(c, r, x, y, z):
            if not (-2 < z < 4 and 7 < y < 12):
                return None
            if y > 11 or z > 3:
                return 'e1'
            if y < 8 or z < -1:
                return 'e3'
            return 'e2'
        S.face(n, 'east').by_world(inset)
    # the back: the panel inside the raised rim is recessed: field e1, its top row and left column in the rim's
    # shadow (e0); the rim itself raised and lighter (e3, top e4)
    f = S.face('bs', 'south')

    def back(c, r, x, y, z):
        if -4 < x < 4 and 5 < y < 12:
            return 'e0' if (y > 11 or x < -3) else 'e1'
        return None
    f.by_world(back)
    for n in S.names('back_rim_'):
        K.fill(S, n, 'e3')
        S.face(n, 'up').fill('e4')
        S.face(n, 'down').fill('e1')
        S.face(n, 'south').fill('e3')
    K.makers_plate(S, 'makers_plate', 'south')


def niche(S):
    K.recess(S, 'niche_back', 'r0')
    for n in ('niche_l', 'niche_r', 'niche_ceil'):
        K.fill(S, n, 'r1')
    S.face('niche_ceil', 'down').fill('r0')
    # the drip tray: a b4 front lip, three r0 slots in two rows, the back row under the niche in shadow (b2); one
    # slot under the spout holds a saline drip (the story mark)
    t = 'tray'
    for f in S.faces(t, K.SIDES):
        f.fill('b2')
    S.face(t, 'north').fill('b4')
    S.face(t, 'down').fill('b1')

    def tray(c, r, x, y, z):
        if z < -6:
            return 'b4'
        if z > -4:
            return 'b2'
        if int(math.floor(x)) in (-5, -3, -1):
            return 'l1' if (int(math.floor(x)) == -3 and z < -5) else 'r0'
        return 'b3'
    S.face(t, 'up').by_world(tray)
    K.small(S, 'spout', 'b', 3, top='b4', bottom='b1')
    S.face('spout', 'down').put(0, 0, 'r0')


def tank(S, level_shown=2):
    # the tank: a clear frame and ONE short glint at the upper left over the liquid (T22); the canisters keep only
    # their side columns and foot, so their 4x4 faces show a 2 px window onto the ingredient rather than a frame
    # round a 2x2 hole
    for f in S.faces('tank'):
        K.clear_glass(f, streaks=False)
        if f.W >= 6 and f.side in K.SIDES:
            f.put(2, 2, 'g3')
            f.put(3, 1, 'g3')
    for n in ('can_s', 'can_k', 'can_r'):
        for f in S.faces(n):
            K.clear_glass(f, streaks=False, edges='lrb' if f.side in K.SIDES else 'tblr')
    for n in S.names('post_'):
        K.brass_post(S, n)
    for n in S.names('saline_'):
        for f in S.faces(n, K.SIDES):
            K.liquid_side(f, 'l', bubbles=[(5, f.H - 3), (2, f.H - 4)] if f.side == 'north' else ())
        g = ripple_frame(0)
        up = S.face(n, 'up')
        for c, r in up.each():
            up.put(c, r, g[r][c])
        S.face(n, 'down').fill('l0')
    K.brass_plate(S, 'lid')
    # the caps: brass small parts, the top face lit with two shaker holes (r0) on the diagonal
    for n in ('cap_s_lid', 'cap_k_lid', 'cap_r_lid'):
        K.small(S, n, 'b', 3, top='b4', bottom='b1')
        up = S.face(n, 'up')
        up.put(1, 1, 'r0')
        up.put(2, 2, 'r0')
        up.put(0, 0, 'b5')
    # the paddle: machined steel, a lit top row on the blade and the bar
    for n in ('paddle_blade', 'paddle_bar'):
        K.steel_part(S, n, field='s3', lit='s4', under='s2')
    for n in ('paddle_shaft', 'paddle_top'):
        K.small(S, n, 's', 3)


def tap_and_flag(S):
    K.brass_housing(S, 'tap_housing')
    K.small(S, 'tap_collar', 's', 1, top='s2', bottom='s0')
    K.brass_post(S, 'tap_stick', foot=False)
    knob = 'tap_knob'
    for f in S.faces(knob, K.SIDES):
        f.fill('r2')
        f.row(0, 'r3')
    S.face(knob, 'up').fill('r3')
    S.face(knob, 'down').fill('r1')
    K.flag(S, 'flag')


def paint(S, level_shown=2):
    K.plinth(S)
    K.feet(S)
    counter(S)
    niche(S)
    K.lamp_bezel(S)
    K.lens_on_sheet(S, 'lamp_lens', 'ready')
    # the sight glass island (the game swaps the whole element's texture by `water`): level 2 for review renders
    wf = S.face('water_face', 'north')
    for r, row in enumerate(water_rows(2)):
        for c, k in enumerate(row):
            wf.put(c, r, k)
    for s in ('east', 'west', 'south'):
        S.face('water_face', s).fill('t1')
    S.face('water_face', 'up').fill('t2')
    S.face('water_face', 'down').fill('t0')
    K.side_port(S, 'port', 'west')
    tank(S, level_shown)
    tap_and_flag(S)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview')
    a = ap.parse_args()
    S = K.Sheet(BBMODEL)
    paint(S)
    miss = S.missing(clear_ok=GLASS)
    if miss:
        sys.exit('unpainted faces: %s' % miss)
    out = [S.save(SHEET)]
    out.append(strip(os.path.join(K.OUT, 'saline_mixer_ripple.png'), [ripple_frame(i) for i in range(4)], 10))
    out.append(strip(os.path.join(K.OUT, 'saline_mixer_swirl.png'), [swirl_frame(i) for i in range(4)], 5))
    out += water_textures()
    for p in out:
        print('wrote', p)
    if a.preview:
        os.makedirs(a.preview, exist_ok=True)
        K.standins_from(S, PLACEHOLDER)
        S.save(os.path.join(a.preview, 'mixer_rest.png'))
        K.lens_on_sheet(S, 'lamp_lens', 'work')
        g = swirl_frame(0)
        for n in S.names('saline_'):
            up = S.face(n, 'up')
            for c, r in up.each():
                up.put(c, r, g[r][c])
        S.save(os.path.join(a.preview, 'mixer_work.png'))
        print('previews in', a.preview)


if __name__ == '__main__':
    main()

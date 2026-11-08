"""Phase 2 textures. Imported at the end of draw_textures.py, which provides the helpers and palettes."""
import random


def run(g):
    img, rect, px, outline, save, hexc = g['img'], g['rect'], g['px'], g['outline'], g['save'], g['hexc']
    gear_base, noise_fill = g['gear_base'], g['noise_fill']
    STEEL, IRON, COPPER, GOLD, CYAN, GREEN, RED, PURPLE, ORANGE = (g[k] for k in
                                                                 ('STEEL', 'IRON', 'COPPER', 'GOLD', 'CYAN', 'GREEN', 'RED', 'PURPLE', 'ORANGE'))
    GLASS, SALINE, LEATHER, BRASS, WHITE = g['GLASS'], g['SALINE'], g['LEATHER'], g['BRASS'], g['WHITE']
    YELLOW, BLACK = hexc('f2c618'), hexc('1a1a1a')

    def done(im, *path):
        outline(im)
        save(im, *path)

    # --- items --------------------------------------------------------------------------------
    im = img(16, 16)
    rect(im, 3, 4, 12, 11, GLASS[0]); rect(im, 3, 4, 12, 4, GLASS[1]); rect(im, 5, 6, 10, 9, hexc('8fe08f', 160))
    px(im, [(4, 5), (5, 5)], GLASS[2])
    done(im, 'item', 'glass_patch.png')

    im = img(16, 16)
    rect(im, 3, 4, 12, 11, hexc('1f4a2a')); rect(im, 4, 5, 11, 10, hexc('2a6a3a'))
    for x in range(3, 13, 2):
        px(im, [(x, 3), (x, 12)], GOLD[2])
    for y in range(5, 11, 2):
        px(im, [(2, y), (13, y)], GOLD[2])
    rect(im, 6, 7, 9, 8, hexc('e8e8e8')); px(im, [(5, 6), (10, 9)], CYAN[2])
    done(im, 'item', 'synapse_chip.png')

    im = img(16, 16)
    for i in range(9):
        rect(im, 3 + i, 12 - i, 4 + i, 12 - i, STEEL[2] if i % 2 else STEEL[1])
    rect(im, 10, 2, 13, 3, STEEL[2]); rect(im, 12, 4, 13, 5, STEEL[2]); px(im, [(11, 4)], (0, 0, 0, 0))
    rect(im, 2, 12, 4, 14, RED[1])
    done(im, 'item', 'surgical_wrench.png')

    def goggles(metal, lens, name):
        im = img(16, 16)
        rect(im, 1, 7, 14, 8, LEATHER[1])
        for cx in (4, 11):
            for x in range(16):
                for y in range(16):
                    d = ((x - cx) ** 2 + (y - 7.5) ** 2) ** 0.5
                    if d <= 3.0:
                        im.putpixel((x, y), metal[2] if d > 2.0 else lens)
        px(im, [(3, 6), (10, 6)], hexc('ffffff'))
        rect(im, 12, 1, 12, 4, metal[1]); px(im, [(12, 0)], GREEN[2])
        done(im, 'item', name)

    goggles(GOLD, hexc('ffd860', 230), 'relay_goggles_mk2.png')
    goggles([hexc('3a8a8a'), hexc('5ad8d0'), hexc('a0f8f0'), hexc('ffffff')], hexc('c060ff', 230), 'relay_goggles_mk3.png')

    im = img(16, 16)
    rect(im, 3, 5, 12, 13, IRON[1]); rect(im, 4, 6, 11, 12, IRON[2]); rect(im, 4, 6, 11, 8, SALINE[1]); rect(im, 3, 4, 12, 4, IRON[0])
    rect(im, 2, 3, 2, 4, IRON[0]); rect(im, 13, 3, 13, 4, IRON[0])
    done(im, 'item', 'cranial_saline_bucket.png')

    def module(name, draw):
        im = gear_base(IRON)
        draw(im)
        done(im, 'item', name + '.png')

    module('suction_cups', lambda im: [rect(im, x, 5, x + 2, 7, hexc('d86a8a')) for x in (4, 9)] + [rect(im, x, 9, x + 2, 11, hexc('d86a8a')) for x in (4, 9)])
    module('spider_legs', lambda im: [px(im, [(3 + i, 5 + abs(2 - i) // 1), (12 - i, 5 + abs(2 - i))], BLACK) for i in range(5)] + [rect(im, 6, 7, 9, 9, BLACK)])
    module('speaker_grille', lambda im: [rect(im, 4, y, 11, y, IRON[0]) for y in range(5, 12, 2)])
    module('saline_recycler', lambda im: [rect(im, 5, 5, 10, 10, SALINE[1]), rect(im, 7, 4, 8, 4, SALINE[2]), px(im, [(6, 6), (9, 9)], hexc('ffffff'))])
    module('shock_lid', lambda im: [px(im, [(8, 3), (7, 4), (7, 5), (6, 6), (7, 7), (8, 7), (9, 7), (8, 8), (8, 9), (7, 10), (7, 11)], YELLOW)])
    module('self_righting_gyro', lambda im: [rect(im, 4, 7, 11, 8, GOLD[2]), rect(im, 7, 4, 8, 11, GOLD[1]), rect(im, 7, 7, 8, 8, RED[2])])
    module('hub_antenna', lambda im: [rect(im, 7, 3, 8, 11, STEEL[2]), px(im, [(5, 4), (10, 4), (4, 3), (11, 3)], CYAN[2]), px(im, [(7, 2), (8, 2)], RED[2])])
    module('life_sign_monitor', lambda im: [rect(im, 4, 5, 11, 10, hexc('0f2a1a')), px(im, [(4, 8), (5, 8), (6, 8), (7, 6), (8, 10), (9, 7), (10, 8), (11, 8)], GREEN[2])])
    module('hat_mount', lambda im: [rect(im, 5, 5, 10, 8, LEATHER[1]), rect(im, 3, 9, 12, 9, LEATHER[0])])

    # --- blocks -------------------------------------------------------------------------------
    def machine_side():
        im = img(16, 16)
        noise_fill(im, 0, 0, 15, 15, STEEL, 23)
        rect(im, 0, 0, 15, 0, STEEL[3]); rect(im, 0, 15, 15, 15, STEEL[0]); rect(im, 0, 0, 0, 15, STEEL[2]); rect(im, 15, 0, 15, 15, STEEL[0])
        px(im, [(2, 2), (13, 2), (2, 13), (13, 13)], STEEL[0])
        return im

    save(machine_side(), 'block', 'machine_side.png')
    im = machine_side(); rect(im, 3, 3, 12, 12, hexc('1e2a30')); rect(im, 4, 4, 11, 11, SALINE[1]); rect(im, 6, 6, 9, 9, SALINE[2])
    save(im, 'block', 'saline_mixer_top.png')
    im = machine_side(); rect(im, 3, 3, 12, 8, hexc('1e2a30')); rect(im, 4, 4, 11, 7, SALINE[1]); px(im, [(5, 5), (8, 6), (10, 4)], SALINE[2])
    rect(im, 3, 10, 12, 12, hexc('0f2a1a')); rect(im, 4, 11, 7, 11, GREEN[2])
    save(im, 'block', 'saline_mixer_front.png')
    im = machine_side(); rect(im, 2, 2, 13, 13, hexc('1e2a30')); rect(im, 3, 3, 12, 12, hexc('d88a8a', 220)); rect(im, 5, 5, 10, 10, hexc('b05a5a'))
    save(im, 'block', 'body_printer_top.png')
    im = machine_side(); rect(im, 3, 2, 12, 13, hexc('1e2a30')); rect(im, 4, 3, 11, 12, GLASS[0])
    rect(im, 7, 4, 8, 5, hexc('d8a07a')); rect(im, 6, 6, 9, 9, hexc('d8a07a')); rect(im, 6, 10, 7, 12, hexc('d8a07a')); rect(im, 8, 10, 9, 12, hexc('d8a07a'))
    save(im, 'block', 'body_printer_front.png')

    im = img(16, 16)
    rect(im, 0, 0, 15, 15, GLASS[0]); rect(im, 0, 0, 0, 15, GLASS[1]); rect(im, 1, 8, 14, 15, COPPER[1]); rect(im, 1, 8, 14, 8, COPPER[3])
    rect(im, 7, 0, 8, 7, COPPER[2])
    save(im, 'block', 'leyden_jar.png')
    im = img(16, 16); noise_fill(im, 0, 0, 15, 15, COPPER, 31)
    save(im, 'block', 'leyden_cap.png')
    im = img(16, 16)
    for y in range(16):
        rect(im, 0, y, 15, y, COPPER[2] if y % 2 else COPPER[1])
    save(im, 'block', 'tesla_coil.png')
    im = img(16, 16); noise_fill(im, 0, 0, 15, 15, STEEL, 37)
    for x in range(16):
        for y in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 4:
                im.putpixel((x, y), STEEL[3])
    save(im, 'block', 'tesla_ball.png')
    im = img(16, 16)
    for y in range(16):
        rect(im, 0, y, 15, y, STEEL[1] if y % 4 else STEEL[2])
    save(im, 'block', 'mast_pole.png')
    im = img(16, 16)
    for y in range(16):
        rect(im, 0, y, 15, y, GOLD[1] if y % 4 else GOLD[2])
    save(im, 'block', 'mast_pole_gold.png')
    im = img(16, 16); rect(im, 0, 0, 15, 15, STEEL[3]); rect(im, 3, 3, 12, 12, STEEL[2]); rect(im, 7, 7, 8, 8, RED[2])
    save(im, 'block', 'mast_dish.png')
    im = img(16, 16); noise_fill(im, 0, 0, 15, 15, [hexc('2a2a30'), hexc('3a3a44'), hexc('4a4a58'), hexc('6a6a7a')], 41)
    rect(im, 3, 3, 12, 6, hexc('1a1a1a')); px(im, [(4, 4), (6, 5), (8, 4), (10, 5)], RED[2])
    for x in range(2, 14, 3):
        rect(im, x, 9, x, 13, hexc('6a6a7a'))
    save(im, 'block', 'jammer.png')

    # placards: hazard border plus an icon
    def placard(n, icon):
        im = img(16, 16)
        rect(im, 0, 0, 15, 15, YELLOW)
        for i in range(0, 16, 4):
            rect(im, i, 0, i + 1, 0, BLACK); rect(im, i + 2, 15, i + 3, 15, BLACK)
            rect(im, 0, i, 0, i + 1, BLACK); rect(im, 15, i + 2, 15, i + 3, BLACK)
        rect(im, 2, 3, 13, 12, hexc('f8f0d0'))
        icon(im)
        save(im, 'block', f'placard_{n}.png')

    def head(im, x, y, c=hexc('d8a07a')):
        rect(im, x, y, x + 3, y + 3, c); px(im, [(x + 1, y + 1), (x + 2, y + 1)], BLACK)

    icons = [
        lambda im: (head(im, 5, 5), rect(im, 9, 6, 11, 9, STEEL[1])),
        lambda im: (rect(im, 5, 4, 10, 10, GLASS[1]), head(im, 6, 6), px(im, [(3, 5), (12, 5), (3, 9), (12, 9)], BLACK)),
        lambda im: (rect(im, 6, 4, 9, 4, BLACK), rect(im, 6, 10, 9, 10, BLACK), rect(im, 6, 4, 6, 10, BLACK), rect(im, 9, 4, 9, 10, BLACK)),
        lambda im: (head(im, 5, 6), px(im, [(10, 4), (11, 4), (11, 5), (10, 6), (10, 8)], BLACK)),
        lambda im: [rect(im, 3 + i, 10 - i, 4 + i, 10 - i, STEEL[2]) for i in range(8)],
        lambda im: (rect(im, 6, 5, 9, 10, SALINE[1]), [px(im, [(4 + i, 4 + i), (11 - i, 4 + i)], RED[2]) for i in range(8)]),
        lambda im: (rect(im, 6, 5, 9, 8, hexc('5a7aa0')), rect(im, 6, 9, 6, 11, hexc('3a3a6a')), rect(im, 9, 9, 9, 11, hexc('3a3a6a'))),
        lambda im: (rect(im, 5, 4, 10, 10, GLASS[1]), px(im, [(4, 3), (11, 3), (12, 6)], YELLOW), head(im, 6, 6)),
        lambda im: (rect(im, 4, 7, 7, 9, hexc('d8a07a')), rect(im, 9, 5, 12, 8, hexc('8a6a4a')), px(im, [(3, 4), (13, 11)], RED[2])),
        lambda im: (rect(im, 6, 4, 9, 7, hexc('5a7aa0')), rect(im, 5, 8, 10, 11, BLACK)),
        lambda im: (px(im, [(5, 10), (6, 9), (6, 8), (7, 7), (7, 10), (8, 6), (8, 9)], ORANGE[1]), head(im, 9, 7)),
        lambda im: (rect(im, 4, 6, 11, 9, hexc('ffffff')), rect(im, 7, 6, 8, 9, CYAN[1]), px(im, [(7, 7)], BLACK)),
    ]
    for n, icon in enumerate(icons):
        placard(n, icon)

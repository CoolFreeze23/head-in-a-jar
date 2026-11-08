"""Phase 3 textures. Imported at the end of draw_textures.py."""


def run(g):
    img, rect, px, outline, save, hexc = g['img'], g['rect'], g['px'], g['outline'], g['save'], g['hexc']
    box_uv, noise_fill = g['box_uv'], g['noise_fill']
    STEEL, IRON, COPPER, GOLD, CYAN, GREEN, RED, BRASS = (g[k] for k in ('STEEL', 'IRON', 'COPPER', 'GOLD', 'CYAN', 'GREEN', 'RED', 'BRASS'))
    GLASS, SALINE = g['GLASS'], g['SALINE']
    BLACK = hexc('1a1a1a')

    def done(im, *path):
        outline(im)
        save(im, *path)

    # items
    im = img(16, 16)
    for i in range(9):
        rect(im, 5 + i, 10 - i, 6 + i, 10 - i, STEEL[2])
        px(im, [(5 + i, 11 - i)], STEEL[0] if i % 2 else STEEL[3])
    for i in range(0, 9, 2):
        px(im, [(6 + i, 11 - i)], STEEL[0])
    rect(im, 2, 11, 5, 14, hexc('6a3a1a')); rect(im, 3, 12, 4, 13, hexc('8a5a2a'))
    done(im, 'item', 'surgical_saw.png')

    im = img(16, 16)
    rect(im, 7, 2, 8, 3, hexc('6a4a2a')); rect(im, 6, 4, 9, 4, GLASS[1])
    rect(im, 5, 5, 10, 13, GLASS[0]); rect(im, 6, 8, 9, 12, hexc('b8d860', 210)); rect(im, 6, 8, 9, 8, hexc('e0f890', 230))
    px(im, [(6, 6)], GLASS[2])
    done(im, 'item', 'embalming_fluid.png')

    im = img(16, 16)
    for i in range(12):
        px(im, [(2 + i, 8 + (1 if i % 2 else -1)), (2 + i, 4 + (i % 3)), (2 + i, 12 - (i % 3))], GOLD[2] if i % 2 else hexc('f0a0c0'))
    rect(im, 6, 6, 9, 9, hexc('f0a0c0')); px(im, [(7, 7)], GOLD[3])
    done(im, 'item', 'neural_lace.png')

    im = img(16, 16)
    rect(im, 4, 2, 11, 2, IRON[2]); rect(im, 3, 3, 12, 3, IRON[1])
    rect(im, 4, 4, 11, 12, GLASS[0]); rect(im, 5, 6, 10, 10, hexc('6a8a4a')); px(im, [(6, 7), (9, 7)], BLACK); rect(im, 6, 9, 9, 9, hexc('3a5a2a'))
    rect(im, 5, 10, 10, 12, SALINE[1]); rect(im, 3, 13, 12, 14, IRON[1])
    done(im, 'item', 'jarred_head.png')

    im = img(16, 16)
    rect(im, 5, 2, 10, 3, STEEL[1]); rect(im, 3, 4, 12, 10, STEEL[2]); rect(im, 2, 4, 2, 10, STEEL[1]); rect(im, 13, 4, 13, 10, STEEL[1])
    rect(im, 4, 11, 6, 14, STEEL[1]); rect(im, 9, 11, 11, 14, STEEL[1]); rect(im, 6, 5, 9, 7, CYAN[1])
    done(im, 'item', 'lunk_chassis.png')

    im = img(16, 16)
    rect(im, 2, 6, 13, 10, hexc('d0a020')); rect(im, 2, 6, 13, 6, hexc('f0c840')); rect(im, 6, 4, 9, 5, STEEL[2])
    for x in (3, 10):
        rect(im, x, 10, x + 2, 12, BLACK); px(im, [(x + 1, 11)], STEEL[2])
    done(im, 'item', 'rover_chassis.png')

    im = img(16, 16)
    rect(im, 5, 6, 10, 9, STEEL[1]); rect(im, 6, 4, 9, 5, STEEL[2])
    for i, (x, y) in enumerate([(1, 4), (2, 7), (2, 10), (3, 12), (14, 4), (13, 7), (13, 10), (12, 12)]):
        px(im, [(x, y)], STEEL[0])
        rect(im, min(x, 5 if x < 8 else 10), y, max(x, 5 if x < 8 else 10), y, STEEL[0])
    done(im, 'item', 'spider_walker_chassis.png')

    # block
    im = img(16, 16)
    noise_fill(im, 0, 0, 15, 15, STEEL, 47)
    rect(im, 2, 1, 13, 14, hexc('1e2a30')); rect(im, 3, 2, 12, 13, hexc('a8d8f0', 200))
    rect(im, 6, 4, 9, 6, hexc('6a8aa0')); rect(im, 5, 7, 10, 12, hexc('6a8aa0')); px(im, [(4, 3), (11, 3), (4, 12), (11, 12)], hexc('ffffff'))
    rect(im, 12, 7, 13, 8, RED[2])
    save(im, 'block', 'body_locker_front.png')

    # shell entity textures
    def paint_steel(tex, u, v, w, h, d, ramp, accent=None):
        def fill(face, x, y, fw, fh):
            if face in ('top', 'bottom'):
                return ramp[2] if (x + y) % 7 else ramp[1]
            edge = x in (0, fw - 1) or y in (0, fh - 1)
            if accent and face == 'north' and fh > 6 and fw > 6 and fh // 3 <= y <= fh // 3 + 1 and 1 < x < fw - 2:
                return accent
            return ramp[0] if edge else (ramp[1] if (x * 3 + y) % 5 else ramp[2])
        box_uv(tex, u, v, w, h, d, fill)

    lunk = img(64, 64)
    paint_steel(lunk, 0, 0, 16, 24, 8, [hexc('4a5058'), hexc('6a727c'), hexc('8a949e')], CYAN[2])
    paint_steel(lunk, 48, 0, 6, 2, 6, BRASS)
    paint_steel(lunk, 0, 32, 4, 22, 6, [hexc('3a4048'), hexc('5a626c'), hexc('7a848e')])
    paint_steel(lunk, 20, 32, 5, 14, 5, [hexc('3a4048'), hexc('5a626c'), hexc('7a848e')])
    save(lunk, 'entity', 'lunk.png')

    rover = img(72, 40)
    paint_steel(rover, 0, 0, 16, 6, 20, [hexc('a07818'), hexc('d0a020'), hexc('f0c840')], hexc('202020'))
    paint_steel(rover, 0, 26, 6, 3, 6, BRASS)
    box_uv(rover, 40, 26, 3, 6, 6, lambda f, x, y, w, h: BLACK if f in ('north', 'south', 'top', 'bottom') else (STEEL[2] if (x + y) % 3 == 0 else hexc('2a2a2a')))
    save(rover, 'entity', 'rover.png')

    spider = img(64, 32)
    paint_steel(spider, 0, 0, 12, 6, 12, [hexc('2a2e34'), hexc('3e444c'), hexc('5a626c')], RED[2])
    paint_steel(spider, 0, 18, 6, 2, 6, BRASS)
    box_uv(spider, 24, 18, 14, 2, 2, lambda f, x, y, w, h: STEEL[1] if x % 4 else STEEL[0])
    save(spider, 'entity', 'spider_walker.png')

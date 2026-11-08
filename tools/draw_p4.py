"""Phase 4 textures: mecha frames, the module sheet, module icons, the Gantry. Imported at the end of draw_textures.py."""
import random


def run(g):
    img, rect, px, outline, save, hexc = g['img'], g['rect'], g['px'], g['outline'], g['save'], g['hexc']
    box_uv, noise_fill = g['box_uv'], g['noise_fill']
    STEEL, IRON, COPPER, GOLD, CYAN, GREEN, RED, BRASS, ORANGE, PURPLE = (g[k] for k in
                                                                          ('STEEL', 'IRON', 'COPPER', 'GOLD', 'CYAN', 'GREEN', 'RED', 'BRASS', 'ORANGE', 'PURPLE'))
    BLACK = hexc('1a1a1a')
    PAINT = [hexc('8a5a10'), hexc('d09018'), hexc('f0b830'), hexc('ffe080')]   # safety yellow
    DARK = [hexc('2a2e34'), hexc('3e444c'), hexc('5a626c'), hexc('7a848e')]

    def done(im, *path):
        outline(im)
        save(im, *path)

    def armour(tex, u, v, w, h, d, ramp, stripe=None, light=None):
        def fill(face, x, y, fw, fh):
            if face in ('top', 'bottom'):
                return ramp[2] if (x + y) % 6 else ramp[1]
            if x in (0, fw - 1) or y in (0, fh - 1):
                return ramp[0]
            if stripe and face == 'north' and y == fh // 2:
                return stripe if (x // 2) % 2 == 0 else BLACK
            if light and face == 'north' and y == 2 and fw // 2 - 1 <= x <= fw // 2:
                return light
            if (x, y) in ((1, 1), (fw - 2, 1), (1, fh - 2), (fw - 2, fh - 2)):
                return ramp[0]
            return ramp[2] if (x + 2 * y) % 7 == 0 else ramp[1]
        box_uv(tex, u, v, w, h, d, fill)

    # frames
    hop = img(64, 64)
    armour(hop, 0, 0, 14, 14, 8, PAINT, BLACK, CYAN[2])
    armour(hop, 44, 0, 8, 2, 8, DARK)
    armour(hop, 0, 40, 4, 16, 4, DARK)
    armour(hop, 24, 40, 5, 22, 5, DARK)
    save(hop, 'entity', 'hopper.png')
    bul = img(128, 64)
    armour(bul, 0, 0, 20, 22, 12, [hexc('3a4a3a'), hexc('5a6e52'), hexc('7a9070'), hexc('a0b890')], PAINT[2], RED[2])
    armour(bul, 64, 0, 10, 2, 10, DARK)
    armour(bul, 0, 40, 6, 22, 6, DARK)
    armour(bul, 24, 40, 7, 24, 7, DARK)
    save(bul, 'entity', 'bulwark.png')

    # module sheet: colour swatches matching the module model's texOffs
    sheet = img(64, 64)
    swatches = [((0, 0, 16, 12), GOLD), ((16, 0, 16, 12), STEEL), ((32, 0, 32, 12), DARK), ((0, 12, 16, 12), BRASS), ((16, 12, 16, 12), PURPLE),
                ((32, 12, 32, 12), STEEL), ((0, 24, 28, 20), DARK), ((28, 24, 16, 14), ORANGE), ((44, 24, 20, 16), RED), ((0, 44, 50, 6), hexc('d8d8e8')),
                ((28, 38, 22, 14), PAINT), ((50, 40, 14, 10), STEEL), ((50, 50, 14, 14), GREEN), ((0, 50, 26, 14), IRON), ((26, 56, 10, 8), CYAN),
                ((36, 56, 8, 8), ORANGE), ((44, 56, 6, 8), DARK)]
    for (x, y, w, h), ramp in swatches:
        rng = random.Random(x * 7 + y)
        for i in range(x, min(64, x + w)):
            for j in range(y, min(64, y + h)):
                sheet.putpixel((i, j), ramp[rng.choice([1, 1, 2, 0]) if len(ramp) > 2 else 0])
    save(sheet, 'entity', 'mecha_modules.png')

    # items: plating, frames
    im = img(16, 16)
    rect(im, 2, 3, 13, 12, STEEL[1]); rect(im, 2, 3, 13, 3, STEEL[3]); rect(im, 2, 12, 13, 12, STEEL[0])
    px(im, [(3, 4), (12, 4), (3, 11), (12, 11)], STEEL[0]); rect(im, 4, 7, 11, 8, PAINT[2])
    done(im, 'item', 'mecha_plating.png')
    for name, ramp in (('hopper_frame', PAINT), ('bulwark_frame', [hexc('3a4a3a'), hexc('5a6e52'), hexc('7a9070'), hexc('a0b890')])):
        im = img(16, 16)
        rect(im, 5, 2, 10, 7, ramp[1]); rect(im, 6, 3, 9, 4, CYAN[2]); rect(im, 3, 3, 4, 8, DARK[2]); rect(im, 11, 3, 12, 8, DARK[2])
        rect(im, 5, 8, 6, 14, DARK[1]); rect(im, 9, 8, 10, 14, DARK[1])
        done(im, 'item', name + '.png')

    # module icons: a plate tinted by slot kind with a pictogram
    kinds = {'LEGS': GREEN, 'BACK': CYAN, 'CORE': GOLD, 'TORSO': STEEL, 'ARM': RED, 'SENSOR': PURPLE}
    picto = {
        'hydraulic_legs': [(6, 4, 7, 12), (9, 4, 10, 12)], 'spring_heels': [(5, 11, 10, 12), (6, 8, 9, 8), (5, 5, 10, 5)],
        'anchor_clamps': [(7, 3, 8, 11), (4, 10, 11, 11)], 'aqua_fins': [(4, 6, 11, 7), (6, 8, 9, 10)],
        'jump_jets': [(5, 4, 6, 9), (9, 4, 10, 9), (5, 10, 6, 12), (9, 10, 10, 12)], 'rocket_pack': [(6, 3, 9, 10), (6, 11, 9, 13)],
        'glider_wings': [(2, 7, 13, 8), (4, 6, 11, 6)], 'cargo_hold': [(4, 5, 11, 11)], 'passenger_pod': [(5, 4, 10, 10)],
        'radiator_fins': [(4, 4, 4, 11), (7, 4, 7, 11), (10, 4, 10, 11)], 'battery_rack': [(4, 5, 11, 10), (7, 3, 8, 4)],
        'galvanic_stack': [(6, 3, 9, 12)], 'combustion_kernel': [(5, 6, 10, 11), (7, 4, 8, 5)], 'armor_plating': [(4, 4, 11, 11)],
        'thorn_plating': [(4, 6, 11, 9), (5, 4, 5, 5), (10, 4, 10, 5)], 'fire_suppression': [(6, 4, 9, 11)], 'life_support_recycler': [(5, 5, 10, 10)],
        'signal_booster': [(7, 3, 8, 11), (4, 4, 11, 4)], 'solar_skin': [(3, 5, 12, 10)], 'coolant_loop': [(4, 5, 11, 6), (4, 9, 11, 10)],
        'drill': [(7, 3, 8, 12), (6, 5, 9, 9)], 'chainsaw': [(4, 6, 11, 9)], 'hydraulic_fist': [(5, 5, 10, 10)], 'grapple_hook': [(7, 3, 8, 10), (5, 10, 10, 11)],
        'tractor_beam': [(6, 4, 9, 7), (5, 8, 10, 12)], 'rivet_gun': [(3, 7, 12, 8), (5, 9, 6, 11)], 'riot_shield': [(4, 3, 11, 12)],
        'night_optics': [(4, 6, 6, 8), (9, 6, 11, 8)], 'bio_scanner': [(5, 5, 10, 10)],
    }
    import importlib.util
    import os
    spec = importlib.util.spec_from_file_location('gen_p4', os.path.join(os.path.dirname(__file__), 'gen_p4.py'))
    slots = {'hydraulic_legs': 'LEGS', 'spring_heels': 'LEGS', 'anchor_clamps': 'LEGS', 'aqua_fins': 'LEGS', 'jump_jets': 'BACK', 'rocket_pack': 'BACK',
             'glider_wings': 'BACK', 'cargo_hold': 'BACK', 'passenger_pod': 'BACK', 'radiator_fins': 'BACK', 'battery_rack': 'BACK',
             'galvanic_stack': 'CORE', 'combustion_kernel': 'CORE', 'armor_plating': 'TORSO', 'thorn_plating': 'TORSO', 'fire_suppression': 'TORSO',
             'life_support_recycler': 'TORSO', 'signal_booster': 'TORSO', 'solar_skin': 'TORSO', 'coolant_loop': 'TORSO', 'drill': 'ARM',
             'chainsaw': 'ARM', 'hydraulic_fist': 'ARM', 'grapple_hook': 'ARM', 'tractor_beam': 'ARM', 'rivet_gun': 'ARM', 'riot_shield': 'ARM',
             'night_optics': 'SENSOR', 'bio_scanner': 'SENSOR'}
    for name, slot in slots.items():
        im = img(16, 16)
        ramp = kinds[slot]
        rect(im, 1, 1, 14, 14, DARK[1]); rect(im, 1, 1, 14, 1, DARK[3]); rect(im, 1, 14, 14, 14, DARK[0])
        px(im, [(2, 2), (13, 2), (2, 13), (13, 13)], DARK[0])
        for (x0, y0, x1, y1) in picto[name]:
            rect(im, x0, y0, x1, y1, ramp[2])
            rect(im, x0, y0, x1, y0, ramp[3] if len(ramp) > 3 else ramp[2])
        done(im, 'item', f'mecha_{name}.png')

    # gantry block
    im = img(16, 16)
    noise_fill(im, 0, 0, 15, 15, DARK, 53)
    for i in range(0, 16, 4):
        rect(im, i, 0, i + 1, 0, PAINT[2]); rect(im, i + 2, 15, i + 3, 15, PAINT[2])
    rect(im, 3, 3, 12, 12, DARK[0]); rect(im, 4, 4, 11, 11, DARK[2]); rect(im, 7, 7, 8, 8, CYAN[2])
    save(im, 'block', 'gantry_top.png')
    im = img(16, 16)
    noise_fill(im, 0, 0, 15, 15, DARK, 59)
    for i in range(0, 16, 4):
        rect(im, i, 10, i + 1, 10, PAINT[2]); rect(im, i + 2, 11, i + 3, 11, BLACK)
    save(im, 'block', 'gantry_side.png')

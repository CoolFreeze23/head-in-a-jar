"""Phase 6 textures: the new frames (entity skins and icons), the new module icons, heads at work. Imported by draw_textures.py."""


def run(g):
    img, rect, px, outline, save, hexc = g['img'], g['rect'], g['px'], g['outline'], g['save'], g['hexc']
    box_uv, noise_fill = g['box_uv'], g['noise_fill']
    STEEL, IRON, COPPER, GOLD, CYAN, GREEN, RED, BRASS, ORANGE, PURPLE = (g[k] for k in
                                                                          ('STEEL', 'IRON', 'COPPER', 'GOLD', 'CYAN', 'GREEN', 'RED', 'BRASS', 'ORANGE', 'PURPLE'))
    BLACK = hexc('1a1a1a')
    DARK = [hexc('2a2e34'), hexc('3e444c'), hexc('5a626c'), hexc('7a848e')]
    TEAL = [hexc('1a4a4a'), hexc('2a7470'), hexc('48a8a0'), hexc('90e0d8')]            # Wheelhouse paint
    RUST = [hexc('4a2018'), hexc('7a3a24'), hexc('a85a34'), hexc('d08a5a')]           # Skitter
    SKY = [hexc('2a3a6a'), hexc('4060a0'), hexc('6a90d0'), hexc('b0d0f8')]            # Kite
    GUN = [hexc('30302e'), hexc('4a4a46'), hexc('6a6a64'), hexc('9a9a90')]            # Colossus gunmetal
    EXEC = [hexc('6a4a10'), hexc('a07818'), hexc('d8b030'), hexc('f8e890')]           # Executive gold
    SAIL = [hexc('8a8070'), hexc('c8bca0'), hexc('e8dcc0'), hexc('fff8e8')]

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

    def tyre(tex, u, v, w, h, d):
        def fill(face, x, y, fw, fh):
            if face in ('east', 'west'):
                cx, cy = fw / 2 - 0.5, fh / 2 - 0.5
                r = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
                return STEEL[2] if r < 2 else BLACK if r < fw / 2 else None
            return BLACK if (x + y) % 3 else hexc('2a2a2a')
        box_uv(tex, u, v, w, h, d, fill)

    # --- frames ----------------------------------------------------------------------------------------
    wh = img(128, 128)
    armour(wh, 0, 0, 26, 10, 36, TEAL, hexc('f0f0f0'), GOLD[2])
    armour(wh, 0, 48, 10, 2, 10, DARK)
    tyre(wh, 48, 48, 4, 12, 12)
    armour(wh, 88, 48, 6, 6, 10, DARK, None, RED[2])
    save(wh, 'entity', 'wheelhouse.png')

    sk = img(128, 64)
    armour(sk, 0, 0, 16, 8, 20, RUST, None, GREEN[2])
    armour(sk, 0, 30, 8, 2, 8, DARK)
    armour(sk, 40, 30, 12, 2, 2, RUST)
    armour(sk, 40, 36, 2, 16, 2, DARK)
    armour(sk, 80, 30, 2, 2, 6, [hexc('e8dcc0'), hexc('d0c8a8'), hexc('f0e8d0'), hexc('ffffff')])
    save(sk, 'entity', 'skitter.png')

    ki = img(128, 64)
    armour(ki, 0, 0, 12, 10, 8, SKY, hexc('f0f0f0'), CYAN[2])
    armour(ki, 44, 0, 8, 2, 8, DARK)
    armour(ki, 0, 40, 3, 14, 3, DARK)
    armour(ki, 24, 40, 4, 20, 4, DARK)

    def wing(face, x, y, fw, fh):
        if face in ('top', 'bottom'):
            if x % 5 == 0:
                return SAIL[0]
            return SAIL[2] if (x + y) % 4 else SAIL[1]
        return SAIL[0]
    box_uv(ki, 64, 20, 20, 1, 10, wing)
    save(ki, 'entity', 'kite.png')

    for name, ramp, light in (('colossus', GUN, RED[2]), ('executive', EXEC, CYAN[2])):
        tex = img(128, 64)
        armour(tex, 0, 0, 20, 22, 12, ramp, BLACK if name == 'colossus' else PURPLE[2], light)
        armour(tex, 64, 0, 10, 2, 10, DARK)
        armour(tex, 0, 40, 6, 22, 6, DARK if name == 'colossus' else ramp)
        armour(tex, 24, 40, 7, 24, 7, DARK)
        save(tex, 'entity', name + '.png')

    # frame icons: a silhouette per frame
    def frame_icon(name, ramp, shape):
        im = img(16, 16)
        shape(im, ramp)
        done(im, 'item', name + '_frame.png')

    frame_icon('wheelhouse', TEAL, lambda im, r: (rect(im, 1, 6, 14, 10, r[1]), rect(im, 4, 4, 9, 5, r[2]), rect(im, 2, 11, 4, 13, BLACK),
                                                  rect(im, 11, 11, 13, 13, BLACK), rect(im, 6, 2, 7, 3, DARK[2])))
    frame_icon('skitter', RUST, lambda im, r: (rect(im, 5, 5, 10, 9, r[1]), rect(im, 6, 4, 9, 4, r[2]),
                                               [rect(im, 1 + i * 4 if i < 2 else 3 + i * 3, 10, 1 + i * 4 if i < 2 else 3 + i * 3, 13, DARK[1]) for i in range(4)],
                                               rect(im, 2, 9, 13, 9, DARK[2])))
    frame_icon('colossus', GUN, lambda im, r: (rect(im, 3, 1, 12, 9, r[1]), rect(im, 6, 2, 9, 3, RED[2]), rect(im, 1, 2, 2, 10, DARK[2]),
                                               rect(im, 13, 2, 14, 10, DARK[2]), rect(im, 4, 10, 6, 15, DARK[1]), rect(im, 9, 10, 11, 15, DARK[1])))
    frame_icon('kite', SKY, lambda im, r: (rect(im, 0, 4, 15, 5, SAIL[2]), rect(im, 6, 3, 9, 9, r[1]), rect(im, 7, 4, 8, 5, CYAN[2]),
                                           rect(im, 6, 10, 6, 14, DARK[1]), rect(im, 9, 10, 9, 14, DARK[1])))
    frame_icon('executive', EXEC, lambda im, r: (rect(im, 3, 1, 12, 9, r[1]), rect(im, 6, 2, 9, 3, CYAN[2]), rect(im, 1, 2, 2, 10, r[0]),
                                                 rect(im, 13, 2, 14, 10, r[0]), rect(im, 4, 10, 6, 15, DARK[1]), rect(im, 9, 10, 11, 15, DARK[1]),
                                                 px(im, [(5, 6), (7, 6), (9, 6), (11, 6)], PURPLE[2])))

    # --- module icons (same plate as P4, tinted by slot kind) --------------------------------------------
    kinds = {'LEGS': GREEN, 'BACK': CYAN, 'CORE': GOLD, 'TORSO': STEEL, 'ARM': RED, 'SENSOR': PURPLE}
    modules = {
        'sprint_servos': ('LEGS', [(4, 5, 11, 6), (4, 9, 11, 10), (10, 4, 11, 11)]), 'stilts': ('LEGS', [(5, 3, 6, 12), (9, 3, 10, 12), (4, 12, 11, 12)]),
        'arachnid_legs': ('LEGS', [(3, 4, 4, 11), (6, 4, 6, 11), (9, 4, 9, 11), (11, 4, 12, 11), (3, 7, 12, 8)]),
        'hover_skirt': ('LEGS', [(3, 5, 12, 8), (4, 10, 11, 10), (5, 12, 10, 12)]), 'lava_treads': ('LEGS', [(3, 6, 12, 10), (4, 11, 11, 12)]),
        'ender_satchel': ('BACK', [(4, 4, 11, 11), (6, 6, 9, 9)]), 'smoke_mortar': ('BACK', [(6, 6, 9, 12), (4, 2, 11, 4)]),
        'missile_pod': ('BACK', [(4, 4, 5, 11), (7, 4, 8, 11), (10, 4, 11, 11)]), 'bio_reactor_core': ('CORE', [(5, 4, 10, 11), (6, 3, 9, 3)]),
        'capacitor_bank': ('CORE', [(3, 5, 5, 11), (7, 5, 8, 11), (10, 5, 12, 11)]), 'fusion_bottle': ('CORE', [(5, 3, 10, 12), (6, 6, 9, 9)]),
        'reactive_armour': ('TORSO', [(3, 3, 6, 6), (9, 3, 12, 6), (3, 9, 6, 12), (9, 9, 12, 12)]), 'energy_shield': ('TORSO', [(4, 3, 11, 4), (3, 5, 12, 10), (5, 11, 10, 12)]),
        'medical_injector': ('TORSO', [(7, 3, 8, 12), (3, 7, 12, 8)]), 'stealth_field': ('TORSO', [(4, 4, 11, 11)]),
        'tesla_coil': ('TORSO', [(7, 3, 8, 11), (5, 2, 10, 3), (6, 12, 9, 12)]), 'workbench': ('TORSO', [(3, 4, 12, 6), (4, 7, 5, 12), (10, 7, 11, 12)]),
        'bio_integration_port': ('TORSO', [(5, 5, 10, 10), (7, 3, 8, 4)]), 'manipulator_hand': ('ARM', [(4, 4, 5, 9), (7, 3, 8, 9), (10, 4, 11, 9), (4, 9, 11, 12)]),
        'excavator_scoop': ('ARM', [(3, 7, 12, 11), (3, 4, 4, 7)]), 'flamethrower': ('ARM', [(3, 7, 9, 8), (10, 5, 12, 10)]),
        'arc_cannon': ('ARM', [(3, 6, 10, 9), (11, 4, 12, 11)]), 'hemo_syringe': ('ARM', [(4, 10, 9, 11), (9, 7, 11, 9), (11, 4, 12, 6)]),
        'harvester': ('ARM', [(3, 4, 12, 5), (4, 6, 4, 11), (7, 6, 7, 11), (10, 6, 10, 11)]), 'welding_torch': ('ARM', [(4, 9, 9, 11), (10, 5, 11, 8)]),
        'plasma_lance': ('ARM', [(2, 7, 13, 8), (11, 5, 12, 10)]), 'thermal_imaging': ('SENSOR', [(4, 5, 7, 9), (9, 5, 12, 9)]),
        'ore_scanner': ('SENSOR', [(4, 4, 11, 11), (7, 7, 8, 8)]), 'target_lock': ('SENSOR', [(7, 3, 8, 12), (3, 7, 12, 8), (5, 5, 10, 10)]),
    }
    for name, (slot, rects) in modules.items():
        im = img(16, 16)
        ramp = kinds[slot]
        rect(im, 1, 1, 14, 14, DARK[1]); rect(im, 1, 1, 14, 1, DARK[3]); rect(im, 1, 14, 14, 14, DARK[0])
        px(im, [(2, 2), (13, 2), (2, 13), (13, 13)], DARK[0])
        for (x0, y0, x1, y1) in rects:
            rect(im, x0, y0, x1, y1, ramp[2])
            rect(im, x0, y0, x1, y0, ramp[3] if len(ramp) > 3 else ramp[2])
        done(im, 'item', f'mecha_{name}.png')

    im = img(16, 16)                            # plasma pellet: a glowing capsule
    rect(im, 4, 5, 11, 10, PURPLE[1]); rect(im, 5, 4, 10, 11, PURPLE[1]); rect(im, 6, 6, 9, 9, hexc('f0c8ff')); px(im, [(6, 5)], hexc('ffffff'))
    done(im, 'item', 'plasma_pellet.png')

    # --- heads at work, the socket, quantum gear (P6c/d) ---------------------------------------------------
    jar_item, module = g['jar_item'], g['module']
    save(jar_item(GOLD, PURPLE[2]), 'item', 'executive_life_support_jar.png')

    module('cortex_coprocessor', lambda im: (rect(im, 5, 5, 10, 10, GREEN[1]), rect(im, 6, 6, 9, 9, GREEN[2]), px(im, [(4, 6), (4, 9), (11, 6), (11, 9)], GOLD[2])))
    module('lacrimal_laser', lambda im: (rect(im, 6, 4, 9, 7, CYAN[2]), rect(im, 7, 8, 8, 12, CYAN[1]), px(im, [(7, 13)], hexc('ffffff'))))
    module('body_recall_beacon', lambda im: (rect(im, 6, 4, 9, 10, GOLD[2]), rect(im, 5, 10, 10, 11, GOLD[1]), px(im, [(7, 12)], GOLD[0])))

    LENS = hexc('70d0ff', 220)
    upgrade_marks = {
        'wide_lens': [(3, 6, 12, 9)], 'periscope_stalk': [(7, 2, 8, 12), (6, 2, 9, 4)], 'night_optics': [(4, 6, 6, 9), (9, 6, 11, 9)],
        'waterproof_seal': [(4, 4, 11, 11)], 'shielded_housing': [(3, 3, 12, 12)], 'antenna_whip': [(7, 1, 8, 12), (5, 1, 10, 1)],
        'noise_filter': [(3, 5, 12, 5), (3, 8, 12, 8), (3, 11, 12, 11)], 'thermal_overlay': [(4, 4, 11, 11)], 'zoom_optic': [(3, 6, 12, 9), (11, 5, 12, 10)],
    }
    colours = {'wide_lens': LENS, 'periscope_stalk': BRASS[2], 'night_optics': GREEN[2], 'waterproof_seal': CYAN[1], 'shielded_housing': hexc('5a5a6a'),
               'antenna_whip': COPPER[2], 'noise_filter': hexc('e8e8e8'), 'thermal_overlay': ORANGE[2], 'zoom_optic': LENS}
    for name, marks in upgrade_marks.items():
        im = img(16, 16)
        rect(im, 2, 2, 13, 13, BRASS[1]); rect(im, 2, 2, 13, 2, BRASS[3]); rect(im, 2, 13, 13, 13, BRASS[0])
        for (x0, y0, x1, y1) in marks:
            rect(im, x0, y0, x1, y1, colours[name])
        done(im, 'item', f'collar_{name}.png')

    im = img(16, 16)                            # board room core: a gold gavel block
    rect(im, 3, 3, 12, 12, EXEC[1]); rect(im, 3, 3, 12, 3, EXEC[3]); rect(im, 5, 5, 10, 10, PURPLE[1]); rect(im, 6, 6, 9, 9, hexc('f0d0ff'))
    done(im, 'item', 'board_room_core.png')
    im = img(16, 16)                            # quantum knot: two linked rings
    for cx in (6, 9):
        for x in range(16):
            for y in range(16):
                d = ((x - cx) ** 2 + (y - 7.5) ** 2) ** 0.5
                if 2.5 <= d <= 3.6:
                    im.putpixel((x, y), CYAN[2] if cx == 6 else PURPLE[2])
    done(im, 'item', 'quantum_knot.png')
    im = img(16, 16)                            # quantum goggles
    rect(im, 1, 7, 14, 8, hexc('3a2a4a'))
    for cx in (4, 11):
        for x in range(16):
            for y in range(16):
                d = ((x - cx) ** 2 + (y - 7.5) ** 2) ** 0.5
                if d <= 3.0:
                    im.putpixel((x, y), PURPLE[2] if d > 2.0 else hexc('a0f0ff', 230))
    px(im, [(3, 6), (10, 6)], hexc('ffffff'))
    done(im, 'item', 'quantum_goggles.png')
    for name, n in (('entangled_pair', 2), ('entangled_shard', 1)):
        im = img(16, 16)
        for i in range(n):
            ox = 0 if n == 1 else (-3 if i == 0 else 3)
            rect(im, 6 + ox, 3, 9 + ox, 12, PURPLE[1] if i == 0 else CYAN[1]); rect(im, 7 + ox, 4, 8 + ox, 11, PURPLE[3] if i == 0 else CYAN[3])
        done(im, 'item', name + '.png')
    im = img(16, 16)                            # EMP grenade
    rect(im, 4, 5, 11, 13, hexc('3a4048')); rect(im, 5, 6, 10, 12, hexc('4a5058')); rect(im, 6, 3, 9, 4, STEEL[1]); rect(im, 10, 2, 12, 3, STEEL[2])
    px(im, [(7, 8), (8, 9), (6, 10)], CYAN[2])
    done(im, 'item', 'emp_grenade.png')

    # blocks
    im = img(16, 16); noise_fill(im, 0, 0, 15, 15, BRASS, 81); rect(im, 0, 0, 15, 0, BRASS[3]); save(im, 'block', 'turret_base.png')
    im = img(16, 16); noise_fill(im, 0, 0, 15, 15, STEEL, 82)
    for i in range(0, 16, 3):
        rect(im, i, 7, i, 8, BLACK)
    save(im, 'block', 'turret_ring.png')
    im = img(16, 16); noise_fill(im, 0, 0, 15, 15, BRASS, 83); rect(im, 3, 3, 12, 6, hexc('201810'))
    for i, c in enumerate('STAFF'):
        px(im, [(4 + i * 2, 4)], GOLD[3])
    save(im, 'block', 'mount_plate.png')
    im = img(16, 16); noise_fill(im, 0, 0, 15, 15, DARK, 84)
    for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12), (7, 7)):
        rect(im, x - 1, y - 1, x, y, RED[2])
    rect(im, 0, 7, 15, 8, COPPER[1])
    save(im, 'block', 'socket_side.png')
    im = img(16, 16); noise_fill(im, 0, 0, 15, 15, DARK, 85); rect(im, 3, 3, 12, 12, COPPER[1]); rect(im, 5, 5, 10, 10, hexc('101418'))
    rect(im, 7, 7, 8, 8, CYAN[2])
    save(im, 'block', 'socket_top.png')
    im = img(16, 16); noise_fill(im, 0, 0, 15, 15, STEEL, 86)
    for i in range(3):
        for j in range(3):
            rect(im, 2 + i * 4, 2 + j * 4, 4 + i * 4, 4 + j * 4, hexc('5a3a4a'))
    save(im, 'block', 'array_top.png')
    im = img(16, 16); noise_fill(im, 0, 0, 15, 15, STEEL, 87)
    for i in range(3):
        for j in range(3):
            rect(im, 1 + i * 5, 1 + j * 5, 4 + i * 5, 4 + j * 5, hexc('203038')); px(im, [(2 + i * 5, 2 + j * 5)], CYAN[2])
    save(im, 'block', 'array_front.png')
    im = img(16, 16)                            # nutrient broth bucket
    rect(im, 3, 5, 12, 13, IRON[1]); rect(im, 4, 6, 11, 12, IRON[2]); rect(im, 4, 6, 11, 8, hexc('c89a48')); rect(im, 3, 4, 12, 4, IRON[0])
    rect(im, 2, 3, 2, 4, IRON[0]); rect(im, 13, 3, 13, 4, IRON[0]); px(im, [(6, 7), (9, 7)], hexc('e8c070'))
    done(im, 'item', 'nutrient_broth_bucket.png')

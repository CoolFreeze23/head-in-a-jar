"""Phase 5 textures: lab tools and samples, the lab machines, the Leech Jar, the Chimera. Imported at the end of draw_textures.py."""


def run(g):
    img, rect, px, outline, save, hexc = g['img'], g['rect'], g['px'], g['outline'], g['save'], g['hexc']
    box_uv, noise_fill = g['box_uv'], g['noise_fill']
    STEEL, IRON, COPPER, GOLD, CYAN, GREEN, RED, PURPLE = (g[k] for k in ('STEEL', 'IRON', 'COPPER', 'GOLD', 'CYAN', 'GREEN', 'RED', 'PURPLE'))
    GLASS, BRASS = g['GLASS'], g['BRASS']
    BLACK = hexc('1a1a1a')
    BLOOD = [hexc('4a0a10'), hexc('8a1420'), hexc('c42838'), hexc('f06070')]
    FLESH = [hexc('6a2a3a'), hexc('a04a5c'), hexc('d07a88'), hexc('f0b0b8')]
    DNA = [hexc('1a4a6a'), hexc('3a8ac0'), hexc('8ad0f0')]
    LAB = [hexc('9aa4aa'), hexc('c4ccd0'), hexc('e2e8ea'), hexc('f6fafa')]   # clinical white enamel

    def done(im, *path):
        outline(im)
        save(im, *path)

    # --- collection tools ----------------------------------------------------------------------
    def syringe(name, barrel, tip, plunger, fill):
        im = img(16, 16)
        for i in range(7):                      # barrel on the diagonal
            rect(im, 5 + i, 9 - i, 6 + i, 10 - i, barrel)
        for i in range(5):
            px(im, [(6 + i, 9 - i)], fill)
        rect(im, 2, 12, 3, 13, plunger); rect(im, 3, 11, 4, 12, plunger)   # plunger
        px(im, [(1, 13), (2, 14)], plunger)
        for i in range(3):                      # needle
            px(im, [(12 + i, 3 - i)], tip)
        done(im, 'item', name + '.png')

    syringe('syringe', GLASS[1], STEEL[3], STEEL[1], BLOOD[2])
    syringe('precision_syringe', GLASS[1], GOLD[3], GOLD[1], BLOOD[2])
    syringe('leviathan_needle', hexc('4a4048'), hexc('d0c8e0'), hexc('2a2228'), PURPLE[2])

    im = img(16, 16)                            # marrow drill: pistol grip and a bone-coloured bit
    rect(im, 3, 6, 10, 9, STEEL[1]); rect(im, 3, 6, 10, 6, STEEL[3]); rect(im, 4, 10, 6, 14, BLACK); rect(im, 5, 10, 6, 13, STEEL[0])
    rect(im, 11, 7, 14, 8, hexc('e8dcc0')); px(im, [(12, 7), (14, 8)], hexc('a89870'))
    px(im, [(8, 7)], RED[2])
    done(im, 'item', 'marrow_drill.png')

    im = img(16, 16)                            # oil tap: a valve with a spout
    rect(im, 2, 6, 11, 8, BRASS[1]); rect(im, 2, 6, 11, 6, BRASS[3]); rect(im, 5, 2, 8, 5, BRASS[2]); rect(im, 4, 2, 9, 2, RED[1])
    rect(im, 11, 8, 12, 11, BRASS[0]); px(im, [(12, 12), (12, 14)], hexc('202018'))
    done(im, 'item', 'oil_tap.png')

    im = img(16, 16)                            # ecto trap: a lantern-cage with a green wisp
    rect(im, 4, 3, 11, 13, hexc('2a3a30')); rect(im, 5, 4, 10, 12, hexc('9af0c0', 120))
    rect(im, 6, 6, 9, 10, hexc('6af0a0')); px(im, [(7, 7)], hexc('e0fff0'))
    rect(im, 3, 2, 12, 2, STEEL[1]); rect(im, 3, 14, 12, 14, STEEL[1]); rect(im, 7, 0, 8, 1, STEEL[2])
    done(im, 'item', 'ecto_trap.png')

    # --- samples -------------------------------------------------------------------------------
    def vial(name, fill, cap):
        im = img(16, 16)
        rect(im, 6, 3, 9, 13, GLASS[0]); rect(im, 6, 7, 9, 13, fill[1]); rect(im, 6, 7, 9, 7, fill[2]); rect(im, 7, 13, 8, 14, fill[0])
        rect(im, 5, 1, 10, 2, cap); px(im, [(6, 4), (6, 5)], GLASS[2])
        done(im, 'item', name + '.png')

    vial('blood_sample', BLOOD, STEEL[1])
    vial('plasma_vial', [hexc('a07a20'), hexc('e8c050', 210), hexc('fff0a0')], STEEL[1])

    im = img(16, 16)                            # DNA fraction: a tube with a double helix
    rect(im, 5, 2, 10, 13, GLASS[0]); rect(im, 4, 1, 11, 1, CYAN[1])
    for y in range(3, 13):
        a = 5 + round(2.5 + 2.5 * __import__('math').sin(y * 0.9))
        b = 10 - (a - 5)
        px(im, [(a, y)], DNA[2]); px(im, [(b, y)], RED[2])
        if y % 2 == 0:
            rect(im, min(a, b), y, max(a, b), y, DNA[0])
    done(im, 'item', 'dna_fraction.png')

    im = img(16, 16)                            # bio-gel: a translucent pink blob in a dish
    rect(im, 2, 11, 13, 13, GLASS[1]); rect(im, 3, 13, 12, 13, GLASS[0])
    rect(im, 4, 7, 11, 11, FLESH[1]); rect(im, 5, 6, 10, 6, FLESH[2]); px(im, [(6, 7), (7, 8)], FLESH[3])
    done(im, 'item', 'bio_gel.png')

    im = img(16, 16)                            # serum cassette: a cartridge with a glowing window
    rect(im, 3, 2, 12, 13, LAB[1]); rect(im, 3, 2, 12, 2, LAB[3]); rect(im, 3, 13, 12, 13, LAB[0])
    rect(im, 5, 4, 10, 10, hexc('201828')); rect(im, 6, 5, 9, 9, PURPLE[2]); px(im, [(6, 5)], PURPLE[3])
    rect(im, 6, 14, 9, 15, STEEL[1]); px(im, [(4, 11), (11, 11)], GREEN[2])
    done(im, 'item', 'serum_cassette.png')

    im = img(16, 16)                            # gene wash: a milky bottle
    rect(im, 4, 6, 11, 13, GLASS[0]); rect(im, 5, 7, 10, 12, hexc('f0f4f8', 230)); rect(im, 5, 7, 10, 7, hexc('ffffff'))
    rect(im, 6, 3, 9, 5, GLASS[1]); rect(im, 6, 2, 9, 2, hexc('8a5a30')); px(im, [(7, 9), (8, 10)], CYAN[2])
    done(im, 'item', 'gene_wash.png')

    im = img(16, 16)                            # chimera frame icon: hopper silhouette in flesh and purple
    rect(im, 5, 2, 10, 7, FLESH[1]); rect(im, 6, 3, 9, 4, PURPLE[2]); rect(im, 3, 3, 4, 8, FLESH[0]); rect(im, 11, 3, 12, 8, FLESH[0])
    rect(im, 5, 8, 6, 14, hexc('3e444c')); rect(im, 9, 8, 10, 14, hexc('3e444c'))
    px(im, [(5, 6), (10, 6), (7, 7)], GREEN[2])
    done(im, 'item', 'chimera_frame.png')

    # --- lab blocks ----------------------------------------------------------------------------
    def panel(seed):
        im = img(16, 16)
        noise_fill(im, 0, 0, 15, 15, LAB, seed)
        rect(im, 0, 0, 15, 0, LAB[0]); rect(im, 0, 15, 15, 15, LAB[0]); rect(im, 0, 0, 0, 15, LAB[0]); rect(im, 15, 0, 15, 15, LAB[0])
        return im

    im = panel(61)
    rect(im, 1, 13, 14, 14, hexc('3a7a9a')); px(im, [(3, 2), (12, 2)], STEEL[1])     # blue clinical stripe
    save(im, 'block', 'lab_side.png')

    im = panel(62)                              # centrifuge: round lid with a spinning rotor
    for x in range(16):
        for y in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d <= 6:
                im.putpixel((x, y), STEEL[1] if d > 5 else GLASS[0] if d > 2 else STEEL[2])
    rect(im, 7, 3, 8, 12, STEEL[0]); rect(im, 3, 7, 12, 8, STEEL[0]); px(im, [(4, 4), (11, 11), (11, 4), (4, 11)], BLOOD[2])
    save(im, 'block', 'centrifuge_top.png')
    im = panel(63)
    rect(im, 3, 3, 12, 8, hexc('203038')); rect(im, 4, 4, 11, 7, CYAN[0]); rect(im, 5, 5, 8, 5, CYAN[2])
    rect(im, 4, 10, 5, 11, GREEN[2]); rect(im, 7, 10, 8, 11, RED[2]); rect(im, 10, 10, 11, 11, GOLD[2])
    save(im, 'block', 'centrifuge_front.png')

    im = panel(64)                              # sequencer: a glass slide bay
    rect(im, 3, 3, 12, 12, STEEL[1]); rect(im, 4, 6, 11, 9, GLASS[1]); rect(im, 5, 7, 10, 8, DNA[1])
    save(im, 'block', 'dna_sequencer_top.png')
    im = panel(65)                              # a readout of coloured bases
    rect(im, 2, 2, 13, 9, hexc('101820'))
    cols = [GREEN[2], CYAN[2], GOLD[2], RED[2]]
    for i, x in enumerate(range(3, 13)):
        h = 3 + (i * 7) % 5
        rect(im, x, 9 - h, x, 8, cols[i % 4])
    rect(im, 3, 11, 6, 12, STEEL[1]); rect(im, 9, 11, 12, 12, STEEL[1])
    save(im, 'block', 'dna_sequencer_front.png')

    im = panel(66)                              # splicer: three glowing vials in a rack
    rect(im, 2, 5, 13, 10, STEEL[1])
    for i, c in enumerate((PURPLE[2], GREEN[2], CYAN[2])):
        rect(im, 3 + i * 4, 4, 4 + i * 4, 11, c); px(im, [(3 + i * 4, 4)], hexc('ffffff'))
    save(im, 'block', 'gene_splicer_top.png')
    im = panel(67)
    rect(im, 3, 2, 12, 9, hexc('180c20')); rect(im, 4, 3, 11, 8, PURPLE[0])
    for y in range(3, 9):
        a = 5 + round(2.5 + 2.5 * __import__('math').sin(y * 1.1))
        px(im, [(a, y)], PURPLE[3]); px(im, [(15 - a, y)], GREEN[2])
    rect(im, 5, 11, 10, 12, RED[1])
    save(im, 'block', 'gene_splicer_front.png')

    im = panel(68)                              # injection rig: an arm with a needle
    rect(im, 6, 2, 9, 13, STEEL[1]); rect(im, 7, 3, 8, 12, STEEL[2]); rect(im, 7, 13, 8, 14, STEEL[3])
    save(im, 'block', 'injection_rig_top.png')
    im = panel(69)
    rect(im, 4, 2, 11, 12, STEEL[1]); rect(im, 5, 3, 10, 7, GLASS[1]); rect(im, 6, 5, 9, 7, PURPLE[2])
    rect(im, 7, 8, 8, 13, STEEL[3]); px(im, [(7, 14)], STEEL[3]); rect(im, 3, 13, 12, 14, RED[1])
    save(im, 'block', 'injection_rig_front.png')

    # leech jar: glass with dark leeches, and a steel lid
    im = img(16, 16)
    rect(im, 0, 0, 15, 15, hexc('a8d8c8', 70)); rect(im, 0, 0, 15, 0, GLASS[1]); rect(im, 0, 0, 0, 15, GLASS[1])
    rect(im, 0, 9, 15, 15, hexc('5a7a50', 150))
    for (x, y) in ((3, 11), (8, 12), (11, 10), (5, 13), (12, 13)):
        rect(im, x, y, x + 2, y, hexc('2a1a14')); px(im, [(x + 3, y - 1)], hexc('2a1a14'))
    save(im, 'block', 'leech_jar.png')
    im = img(16, 16)
    noise_fill(im, 0, 0, 15, 15, STEEL, 70)
    save(im, 'block', 'leech_jar_lid.png')

    # --- chimera (hopper layout, 64x64) -------------------------------------------------------
    def flesh_plate(ramp, vein=None):
        def fill(face, x, y, fw, fh):
            if face in ('top', 'bottom'):
                return ramp[2] if (x + y) % 5 else ramp[1]
            if x in (0, fw - 1) or y in (0, fh - 1):
                return ramp[0]
            if vein and (x * 3 + y * 5) % 11 == 0:
                return vein
            return ramp[2] if (x + 2 * y) % 7 == 0 else ramp[1]
        return fill

    chi = img(64, 64)
    box_uv(chi, 0, 0, 14, 14, 8, flesh_plate(FLESH, PURPLE[1]))
    # gene ports: three glowing sockets across the chest
    for i in range(3):
        rect(chi, 8 + 8 + i * 4, 8 + 6, 8 + 9 + i * 4, 8 + 7, GREEN[2])
    box_uv(chi, 44, 0, 8, 2, 8, flesh_plate([hexc('2a2e34'), hexc('3e444c'), hexc('5a626c'), hexc('7a848e')]))
    box_uv(chi, 0, 40, 4, 16, 4, flesh_plate([FLESH[0], hexc('7a3a4a'), FLESH[1], FLESH[2]], PURPLE[1]))
    box_uv(chi, 24, 40, 5, 22, 5, flesh_plate([hexc('2a2e34'), hexc('3e444c'), hexc('5a626c'), hexc('7a848e')]))
    save(chi, 'entity', 'chimera.png')

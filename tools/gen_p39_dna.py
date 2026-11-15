"""Lines and data for the 0.6.2 release (DNA for every mob): the forty new genes, their named pairs, the Staff Directory,
the Body Printer's Template slot, two new Safety Placard notices, five memos and five advancements. Imported by
gen_data.py after gen_p38_heads and before the handbook generator (gen_p27_clarity), whose text check and handbook read
the final lines and entries.

What the genes, pairs, hints and printer say comes from gen_p39_lines (data only); this file turns it into language
keys and writes the rest:
- the genes' names, descriptions and grey cost lines (gene.headinajar.<id>, .desc, .cost);
- every named pair (synergy.headinajar.<a>.<b> / conflict.headinajar.<a>.<b>, each with .comment);
- the Directory's two hint rows per species (hint.headinajar.minecraft.<path>.where / .jar);
- the sound events of the genes (sounds.json, after gen_p35's pattern: each borrows a vanilla sound) and subtitles;
- the Safety Placard's notices 16 and 18 (the property is 0..20 from this release: models and blockstate for every
  value, notices not yet shipped drawn as notice 0), drawn again by gen_p25's notices from the language lines;
- the scent particle's sprite list, the five advancements (two hidden), and the handbook's new entries.
Every action-bar line it makes is checked under 400 px with its longest real arguments, in both languages.
"""
import json
import os

try:
    import gen_p39_lines as L
except ImportError:  # the writer's lines are not merged yet: the genes get their book names only
    L = None

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources')
ACTION_BAR_PX = 400

# The 40 genes in the order they are appended to Gene (its ids), with the book's English names (used only until the
# writer's lines are merged).
GENES = [
    ('ruminant_gut', 'Ruminant Gut'), ('stew_glands', 'Stew Glands'), ('swine_appetite', 'Swine Appetite'),
    ('fleece', 'Fleece'), ('mountain_hooves', 'Mountain Hooves'), ('phase', 'Phase'), ('bamboo_diet', 'Bamboo Diet'),
    ('snow_pounce', 'Snow Pounce'), ('pollinator', 'Pollinator'), ('arctic_coat', 'Arctic Coat'), ('roll_up', 'Roll Up'),
    ('keen_nose', 'Keen Nose'), ('mimicry', 'Mimicry'), ('tongue', 'Tongue'), ('tadpole_tail', 'Tadpole Tail'),
    ('puffer', 'Puffer'), ('spit_gland', 'Spit Gland'), ('dusk_draught', 'Dusk Draught'), ('undeath', 'Undeath'),
    ('sun_baked', 'Sun-Baked'), ('rehabilitated', 'Rehabilitated'), ('gilded_rot', 'Gilded Rot'), ('rampage', 'Rampage'),
    ('bowstring_arms', 'Bowstring Arms'), ('rime_arrows', 'Rime Arrows'), ('spore_arrows', 'Spore Arrows'),
    ('undertow_stride', 'Undertow Stride'), ('snow_trail', 'Snow Trail'), ('gold_standard', 'Gold Standard'),
    ('brute_strength', 'Brute Strength'), ('breaking_and_entering', 'Breaking and Entering'),
    ('steady_aim', 'Steady Aim'), ('fang_line', 'Fang Line'), ('potion_tolerance', 'Potion Tolerance'),
    ('bulldoze', 'Bulldoze'), ('weeping_glands', 'Weeping Glands'), ('pearl_digestion', 'Pearl Digestion'),
    ('levitation_bolt', 'Levitation Bolt'), ('stone_lungs', 'Stone Lungs'), ('fetch', 'Fetch'),
]

# The new carriers' names for gen_p16's and gen_p27's "who carries what" lists (vanilla's own en and pt_br names).
CARRIERS = {
    'cow': ('Cow', 'Vaca'), 'mooshroom': ('Mooshroom', 'Mooshroom'), 'pig': ('Pig', 'Porco'), 'sheep': ('Sheep', 'Ovelha'),
    'goat': ('Goat', 'Cabra'), 'vex': ('Vex', 'Obsessor'), 'panda': ('Panda', 'Panda'), 'fox': ('Fox', 'Raposa'),
    'bee': ('Bee', 'Abelha'), 'polar_bear': ('Polar Bear', 'Urso-Polar'), 'armadillo': ('Armadillo', 'Tatu'),
    'sniffer': ('Sniffer', 'Farejador'), 'parrot': ('Parrot', 'Papagaio'), 'frog': ('Frog', 'Sapo'),
    'tadpole': ('Tadpole', 'Girino'), 'pufferfish': ('Pufferfish', 'Baiacu'), 'llama': ('Llama', 'Lhama'),
    'trader_llama': ('Trader Llama', 'Lhama de Mercador'), 'wandering_trader': ('Wandering Trader', 'Mercador Ambulante'),
    'zombie': ('Zombie', 'Zumbi'), 'husk': ('Husk', 'Cascoso'), 'zombie_villager': ('Zombie Villager', 'Aldeão Zumbi'),
    'zombified_piglin': ('Zombified Piglin', 'Piglin-Zumbificado'), 'zoglin': ('Zoglin', 'Zoglin'),
    'skeleton': ('Skeleton', 'Esqueleto'), 'stray': ('Stray', 'Errante'), 'bogged': ('Bogged', 'Pantanoso'),
    'skeleton_horse': ('Skeleton Horse', 'Cavalo-Esqueleto'), 'snow_golem': ('Snow Golem', 'Golem de Neve'),
    'piglin': ('Piglin', 'Piglin'), 'piglin_brute': ('Piglin Brute', 'Piglin Bárbaro'), 'vindicator': ('Vindicator', 'Vindicador'),
    'pillager': ('Pillager', 'Saqueador'), 'evoker': ('Evoker', 'Invocador'), 'witch': ('Witch', 'Bruxa'),
    'ravager': ('Ravager', 'Devastador'), 'ghast': ('Ghast', 'Ghast'), 'endermite': ('Endermite', 'Endermite'),
    'shulker': ('Shulker', 'Shulker'), 'silverfish': ('Silverfish', 'Traça'), 'allay': ('Allay', 'Sereno'),
}

# Where the writer's Portuguese names a vanilla thing in other words than vanilla's own pt_br does, vanilla's words win.
VANILLA_PT = [('neve fofa', 'neve em pó'), ('toca-discos', 'jukebox'), ('câmaras de desafio', 'câmaras do desafio')]


def _pt(text):
    for theirs, vanilla in VANILLA_PT:
        text = text.replace(theirs, vanilla)
    return text


# Sounds: event -> (the vanilla event it borrows, volume, pitch). Subtitles from the writer's SUBTITLES.
SOUNDS = {
    'gene.quirk.moo': ('minecraft:entity.cow.ambient', 0.8, 1.1),
    'gene.quirk.oink': ('minecraft:entity.pig.ambient', 0.7, 1.1),
    'gene.quirk.croak': ('minecraft:entity.frog.ambient', 0.8, 1.0),
    'gene.quirk.sneeze': ('minecraft:entity.panda.sneeze', 0.8, 1.2),
    'gene.quirk.sob': ('minecraft:entity.ghast.ambient', 0.6, 1.3),
    'gene.tongue': ('minecraft:entity.frog.tongue', 1.0, 1.0),
    'gene.spit': ('minecraft:entity.llama.spit', 1.0, 1.0),
    'gene.phase': ('minecraft:entity.vex.charge', 1.0, 0.8),
    'gene.pounce': ('minecraft:entity.fox.aggro', 1.0, 1.0),
    'gene.puff': ('minecraft:entity.puffer_fish.blow_up', 1.0, 1.0),
}
SUBTITLES_FALLBACK = {
    'gene.quirk.moo': 'Colleague moos', 'gene.quirk.oink': 'Colleague oinks', 'gene.quirk.croak': 'Colleague croaks',
    'gene.quirk.sneeze': 'Colleague sneezes', 'gene.quirk.sob': 'Colleague sobs', 'gene.tongue': 'Tongue lashes',
    'gene.spit': 'Spits', 'gene.phase': 'Phases through the wall', 'gene.pounce': 'Pounces', 'gene.puff': 'Puffs up',
}

# The placard notices this release ships (the property is 0..20; notices 12-15, 17, 19 and 20 ship later).
PLACARD_NOTICES = 21
PLACARD_SHIPPED = list(range(12)) + [16, 18]
PLACARD_LINES = {
    'placard.headinajar.16': ('QUIET FLOOR: THE WARDEN IS WORKING', 'SILÊNCIO: WARDEN TRABALHANDO'),
    'placard.headinajar.18': ('PLEASE DO NOT TAP THE GLASS', 'FAVOR NÃO BATER NO VIDRO'),
}

# ---------------------------------------------------------------------------------------------------------------
# Lines the Java needs that the writer's module does not carry: (key, en, pt). Each stream keeps its own list, so
# their additions never touch the same lines. The writer may move any of them into gen_p39_lines.
MORE_SAMPLES = [       # the integrator: samples, the Splicer, memos
    ('gui.headinajar.splicer.tip.fits', 'Fits: %s', 'Cabe em: %s'),
    ('gui.headinajar.splicer.chip_hint', '%s. Press for the next department; the other button goes back.',
     '%s. Aperte para o próximo departamento; o outro botão volta.'),
    ('gui.headinajar.splicer.pair.synergy_plain', '%s (+10)', '%s (+10)'),
    ('gui.headinajar.splicer.pair.conflict_plain', '%s (-15)', '%s (-15)'),
]
MORE_GENTLE = [        # the gentler creatures' genes (GentleGenes)
    ('refusal.headinajar.gene.full', 'You are full. Lunch can wait.', 'Você está cheio. O almoço pode esperar.'),
    ('refusal.headinajar.gene.graze.chewing', 'Still chewing the last mouthful.', 'Ainda mastigando o último bocado.'),
    ('refusal.headinajar.gene.graze.claim', 'This grass is protected. Graze somewhere else.',
     'Esta grama é protegida. Paste em outro lugar.'),
    ('refusal.headinajar.gene.stew.digesting', 'One flower at a time. Your glands are still on the last one.',
     'Uma flor por vez. As glândulas ainda estão na última.'),
    ('refusal.headinajar.gene.fleece.shorn', 'No fleece to speak of yet. Give it a few minutes.',
     'Ainda não tem lã que preste. Dê uns minutos.'),
    ('refusal.headinajar.gene.fleece.dyed', 'That fleece is that colour already.', 'Essa lã já é dessa cor.'),
    ('refusal.headinajar.gene.roll_up.curled', 'You are curled up. Stop sneaking to uncurl.',
     'Você está encolhido. Pare de agachar para se desenrolar.'),
    ('message.headinajar.gene.roll_up', 'You curl up low. HR calls this a defensive posture.',
     'Você se encolhe rente ao chão. O RH chama isso de postura defensiva.'),
    ('message.headinajar.gene.fleece_grown', 'Your fleece has grown back. Mind the fireplace.',
     'Sua lã cresceu de novo. Cuidado com a lareira.'),
    ('message.headinajar.gene.dusk_milk', 'Dawn. You drank your milk, and every effect went with it.',
     'Amanheceu. Você bebeu seu leite, e todo efeito foi junto.'),
    ('message.headinajar.gene.fetch_dance', 'The music is on. You dance instead. HR has no comment.',
     'A música tocou. Você dança em vez de bater. O RH não comenta.'),
    ('message.headinajar.gene.nose_found', 'Your nose found something. Facilities will want it planted.',
     'Seu nariz achou alguma coisa. As Instalações vão querer plantar.'),
]
MORE_HOSTILE = [       # the hostile creatures' genes (HostileGenes)
    ('refusal.headinajar.gene.brute_strength.barter', 'Piglins do not barter with brutes. Nobody does.',
     'Piglin não negocia com bruto. Ninguém negocia.'),
    ('refusal.headinajar.gene.breaking_and_entering.axe', 'Put the axe away. The villager remembers the last door.',
     'Guarde o machado. O aldeão se lembra da última porta.'),
    ('message.headinajar.gene.rehab_suspended', 'Assault on a villager noted. Your discount is suspended for a day.',
     'Agressão a aldeão registrada. Seu desconto está suspenso por um dia.'),
    ('message.headinajar.gene.milk', 'Milk does nothing for you. The witch gene has seen to that.',
     'Leite não faz nada por você. O gene da bruxa cuidou disso.'),
    ('staff.headinajar.history.stone_lungs', 'Let out of a stone block day %s.',
     'Saiu de um bloco de pedra no dia %s.'),
]
MORE_ACTIVES = [        # the actives, Bulldoze, Weeping Glands (GeneActives)
    ('refusal.headinajar.gene.tongue.too_big', 'That is more than a mouthful. Chicken-sized or smaller, please.',
     'Isso é mais que uma bocada. Do tamanho de uma galinha, no máximo.'),
    ('refusal.headinajar.mecha.graft.heavy', '%s is too much creature for one graft. Graft something lighter.',
     '%s é bicho demais para um enxerto só. Enxerte algo mais leve.'),
    # an active pressed while the mecha is still running its boot after boarding
    ('refusal.headinajar.gene.booting', '%s waits for the self-test. Nothing fires before the checklist.',
     '%s espera o autoteste. Nada dispara antes da vistoria.'),
]
MORE_DIRECTORY = [     # the Staff Directory
    ('refusal.headinajar.lab.directory_carrying', 'Put that down first. The Directory is not a shelf.',
     'Largue isso primeiro. O cadastro não é prateleira.'),
    ('gui.headinajar.directory.title', 'Title: %s', 'Cargo: %s'),
]
MORE_PRINTER = [       # the Body Printer's Template slot
    # a marine print finished after its doorstep was drained: it waits in the vat until the doorstep is wet again
    ('warning.headinajar.printer.dry', 'The doorstep dried out. Your %s waits in the vat.',
     'A soleira secou. O pedido (%s) espera no tanque.'),
    # a tiny print takes one redstone block: the scaled need line in the singular
    ('gui.headinajar.printer.need_scaled_one', '%1$s flesh, %2$s bone blocks,\n1 redstone block, %4$s slime',
     '%1$s carnes, %2$s blocos de osso,\n1 de redstone, %4$s de slime'),
]

# The writer's lines put right here (the text check found them): key -> (en, pt).
OVERRIDES = {
    # the printer's need box is 148 px wide since the Template slot: the pt own-body line ran to a third row
    'gui.headinajar.printer.need': ('16 flesh, 8 bone blocks,\n4 redstone blocks, 8 slime',
                                    '16 carnes, 8 blocos de osso,\n4 de redstone, 8 de slime'),
    # 424 px with the rest of the frame: shortened
    'refusal.headinajar.printer.poor': ('Too poor a fraction to print from (quality %s). Bring 40 or more.',
                                        'Fração fraca demais para imprimir (qualidade %s). Mínimo: 40.'),
    # "Shift" reads as a key name to the text check
    'synergy.headinajar.glow_cells.cat_eyes.comment': ("Glows and sees. The night crew's favourite.",
                                                       'Brilha e enxerga. O queridinho do Turno da Noite.'),
}

# Action-bar lines with arguments: key -> lambda lang_index: [list of argument lists] (the longest real ones).
ACTION_BAR_ARGS = {
    'warning.headinajar.printer.dry': lambda i: [[('Tropical Fish', 'Peixe-tropical')[i]], [('Glow Squid', 'Lula-brilhante')[i]]],
    # the longest creature names a Graft refuses as too heavy (load 4 and up)
    'refusal.headinajar.mecha.graft.heavy': lambda i: [['Shulker'], ['Ravager'], ['Evoker'], ['Ghast'], ['Vex']] if i == 0
    else [['Devastador'], ['Invocador'], ['Shulker']],
    # the longest active gene names a booting mecha refuses
    'refusal.headinajar.gene.booting': lambda i: [['Levitation Bolt'], ['Snow Pounce']] if i == 0 else [['Disparo Levitante'], ['Fileira de Presas'], ['Glândula de Cuspe']],
}


def _event(name):
    return {'done': {'trigger': 'headinajar:event', 'conditions': {'event': name}}}


def _lines():
    """Every plain (key, en, pt) line of this release."""
    out = []
    if L is not None:
        for name in ('PRINTER', 'MEMOS', 'GENE_LINES', 'SPLICER', 'DIRECTORY'):
            out += [tuple(x) for x in getattr(L, name, [])]
    for extra in (MORE_SAMPLES, MORE_GENTLE, MORE_HOSTILE, MORE_ACTIVES, MORE_DIRECTORY, MORE_PRINTER):
        out += extra
    for key, (en, pt) in PLACARD_LINES.items():
        out.append((key, en, pt))
    return out


def early(EN, PT):
    """Before gen_p16 and gen_p27 list who carries which gene (read from Gene.java): the new genes' names and carriers."""
    import gen_p16_repairs3
    gen_p16_repairs3.CREATURES.update(CARRIERS)
    _genes(EN, PT)


def _genes(EN, PT):
    ids = [g for g, _n in GENES]
    if L is None:
        for gene, name in GENES:
            EN[f'gene.headinajar.{gene}'] = PT[f'gene.headinajar.{gene}'] = name
        return
    assert list(L.GENES) == ids, 'the writer\'s genes are not the enum\'s, in order'
    for gene, (en_name, pt_name, en_desc, pt_desc, en_cost, pt_cost) in L.GENES.items():
        key = f'gene.headinajar.{gene}'
        EN[key], PT[key] = en_name, _pt(pt_name)
        EN[key + '.desc'], PT[key + '.desc'] = en_desc, _pt(pt_desc)
        EN[key + '.cost'], PT[key + '.cost'] = en_cost, _pt(pt_cost)


def _pairs(EN, PT):
    if L is None:
        return
    for kind, rows in (('synergy', L.OLD_SYNERGIES + L.SYNERGIES), ('conflict', L.OLD_CONFLICTS + L.CONFLICTS)):
        for a, b, en, pt, en_c, pt_c in rows:
            key = f'{kind}.headinajar.{a}.{b}'
            EN[key], PT[key] = en, pt
            if en_c:
                EN[key + '.comment'], PT[key + '.comment'] = en_c, pt_c
    for kind, (en, pt) in L.PAIR_LINE.items():
        EN[f'gui.headinajar.splicer.pair.{kind}'], PT[f'gui.headinajar.splicer.pair.{kind}'] = en, pt


def _hints(EN, PT):
    if L is None:
        return
    from gen_p37_foundations import STAFF
    assert set(L.HINTS) == set(STAFF) | {'ender_dragon'}, set(L.HINTS) ^ (set(STAFF) | {'ender_dragon'})
    for path, (en_where, en_jar, pt_where, pt_jar) in L.HINTS.items():
        key = f'hint.headinajar.minecraft.{path}'
        EN[key + '.where'], PT[key + '.where'] = en_where, _pt(pt_where)
        EN[key + '.jar'], PT[key + '.jar'] = en_jar, _pt(pt_jar)


def _sounds(EN, PT, write):
    path = os.path.join(RES, 'assets', 'headinajar', 'sounds.json')
    with open(path, encoding='utf-8') as f:
        table = json.load(f)
    subtitles = getattr(L, 'SUBTITLES', None) if L is not None else None
    for name, (of, volume, pitch) in SOUNDS.items():
        en, pt = subtitles[name] if subtitles and name in subtitles else (SUBTITLES_FALLBACK[name],) * 2
        assert len(en) <= 24 and len(pt) <= 30, name
        key = f'subtitles.headinajar.{name}'
        EN[key], PT[key] = en, pt
        sound = {'name': of, 'type': 'event'}
        if volume != 1.0:
            sound['volume'] = volume
        if pitch != 1.0:
            sound['pitch'] = pitch
        table[name] = {'sounds': [sound], 'subtitle': key}
    write('assets/headinajar/sounds.json', table)


def _placards(EN, PT, write):
    """Models and blockstate for notices 0..20 (the unshipped ones show notice 0), then every notice drawn again."""
    import gen_p25_machines5 as M
    K = M.K
    model = K._load('safety_placard')
    sheet = K._sheet_uv(model)
    els = []
    for e in K._cubes(model, K._find(model, 'static')):
        def uv(face, data, e=e):
            if e['name'] == 'board' and face == 'north':
                return '#notice', [0, 0, 16, 16]
            return sheet(face, data)
        els.append(K._element(model, e, uv))
    variants = {}
    for n in range(PLACARD_NOTICES):
        shown = n if n in PLACARD_SHIPPED else 0
        if n in PLACARD_SHIPPED:
            write(f'assets/headinajar/models/block/safety_placard_{n}.json',
                  K._model('safety_placard', els, dict(K._sheet('safety_placard'), notice=f'headinajar:block/placard/notice_{n}')))
        for d, y in M.PLACARD_TURN.items():
            variants[f'facing={d},notice={n}'] = dict({'model': f'headinajar:block/safety_placard_{shown}'}, **({'y': y} if y else {}))
    write('assets/headinajar/blockstates/safety_placard.json', {'variants': variants})
    # the notices are drawn from the language lines: 16 and 18 are new, so draw the set again (the twelve come out the same)
    M.notices(EN, PT, write)


def _particles(write):
    write('assets/headinajar/particles/scent.json', {'textures': ['headinajar:scent_0', 'headinajar:scent_1']})


def _advancements(EN, PT, write):
    if L is None:
        return
    for adv, parent, icon, hidden, en_t, en_d, pt_t, pt_d in L.ADVANCEMENTS:
        write(f'data/headinajar/advancement/{adv}.json', {
            'parent': f'headinajar:{parent}', 'criteria': _event(adv),
            'display': {'icon': {'id': icon}, 'title': {'translate': f'advancements.headinajar.{adv}.title'},
                        'description': {'translate': f'advancements.headinajar.{adv}.description'}, 'frame': 'task',
                        'show_toast': True, 'announce_to_chat': not hidden, 'hidden': hidden}})
        for table, t, d in ((EN, en_t, en_d), (PT, pt_t, pt_d)):
            table[f'advancements.headinajar.{adv}.title'], table[f'advancements.headinajar.{adv}.description'] = t, d


WRITTEN_PAGES = 180


def _raise_written_cap():
    """The written edition grows with forty more genes in its carriers list and this release's entries: gen_p27's
    emit_handbook is re-made with a higher cap (as gen_p38 did), word for word otherwise."""
    import ast
    import gen_p27_clarity as c
    # read from the file: gen_p38 has already re-made the function, so its live source is not the file's
    text = open(c.__file__, encoding='utf-8').read()
    node = next(n for n in ast.parse(text).body if isinstance(n, ast.FunctionDef) and n.name == 'emit_handbook')
    src = ast.get_source_segment(text, node)
    old = "assert len(pages) <= 120, f'the written edition is {len(pages)} pages; keep it at 120 or under'"
    assert src.count(old) == 1, 'gen_p27_clarity.emit_handbook changed: look at its page cap again'
    src = src.replace(old, f"assert len(pages) <= {WRITTEN_PAGES}, f'the written edition is {{len(pages)}} pages; "
                           f"keep it at {WRITTEN_PAGES} or under'")
    exec(compile(src, c.__file__, 'exec'), c.__dict__)


def _check_action_bar(EN, PT, keys):
    from gen_p27_clarity import width, fill
    wide = getattr(L, 'WIDE_ARGS', {}) if L is not None else {}
    over = []
    for key in keys:
        if not key.startswith(('refusal.headinajar.', 'message.headinajar.', 'warning.headinajar.')):
            continue
        for i, table in enumerate((EN, PT)):
            if key in ACTION_BAR_ARGS:
                arg_sets = ACTION_BAR_ARGS[key](i)
            elif key in wide:
                arg_sets = [list(wide[key][i])]
            else:
                arg_sets = [[]]
            for args in arg_sets:
                line = fill(table[key], args) if args else table[key]
                if width(line) >= ACTION_BAR_PX:
                    over.append((key, ('en', 'pt')[i], width(line), line))
    assert not over, f'{len(over)} action-bar lines over {ACTION_BAR_PX} px: {over[:5]}'


# ---------------------------------------------------------------------------------------------------------------
# The handbook. Numbers are the code's: genetics/GeneNumbers.java, block/PrintCatalogue.java, genetics/Directory.java.
# The gene pages are made from the writer's names and costs and the enum's loads, packed to the page.

# (gene id, load) in enum order, for the department pages (loads from Gene.java)
LOADS = {
    'ruminant_gut': 1, 'stew_glands': 2, 'swine_appetite': 1, 'fleece': 2, 'mountain_hooves': 2, 'phase': 4,
    'bamboo_diet': 1, 'snow_pounce': 3, 'pollinator': 2, 'arctic_coat': 2, 'roll_up': 3, 'keen_nose': 1, 'mimicry': 1,
    'tongue': 3, 'tadpole_tail': 1, 'puffer': 2, 'spit_gland': 2, 'dusk_draught': 2, 'undeath': 3, 'sun_baked': 2,
    'rehabilitated': 2, 'gilded_rot': 2, 'rampage': 3, 'bowstring_arms': 2, 'rime_arrows': 2, 'spore_arrows': 2,
    'undertow_stride': 2, 'snow_trail': 2, 'gold_standard': 2, 'brute_strength': 3, 'breaking_and_entering': 2,
    'steady_aim': 2, 'fang_line': 4, 'potion_tolerance': 3, 'bulldoze': 4, 'weeping_glands': 4, 'pearl_digestion': 2,
    'levitation_bolt': 4, 'stone_lungs': 2, 'fetch': 2,
}
# the department of each new gene: its species' (gen_p37's table)
SOURCE = {
    'ruminant_gut': 'cow', 'stew_glands': 'mooshroom', 'swine_appetite': 'pig', 'fleece': 'sheep',
    'mountain_hooves': 'goat', 'phase': 'vex', 'bamboo_diet': 'panda', 'snow_pounce': 'fox', 'pollinator': 'bee',
    'arctic_coat': 'polar_bear', 'roll_up': 'armadillo', 'keen_nose': 'sniffer', 'mimicry': 'parrot', 'tongue': 'frog',
    'tadpole_tail': 'tadpole', 'puffer': 'pufferfish', 'spit_gland': 'llama', 'dusk_draught': 'wandering_trader',
    'undeath': 'zombie', 'sun_baked': 'husk', 'rehabilitated': 'zombie_villager', 'gilded_rot': 'zombified_piglin',
    'rampage': 'zoglin', 'bowstring_arms': 'skeleton', 'rime_arrows': 'stray', 'spore_arrows': 'bogged',
    'undertow_stride': 'skeleton_horse', 'snow_trail': 'snow_golem', 'gold_standard': 'piglin',
    'brute_strength': 'piglin_brute', 'breaking_and_entering': 'vindicator', 'steady_aim': 'pillager',
    'fang_line': 'evoker', 'potion_tolerance': 'witch', 'bulldoze': 'ravager', 'weeping_glands': 'ghast',
    'pearl_digestion': 'endermite', 'levitation_bolt': 'shulker', 'stone_lungs': 'silverfish', 'fetch': 'allay',
}
FITS = {1: ('body, host, mecha', 'corpo, hospedeiro, mecha'), 2: ('body, host, mecha', 'corpo, hospedeiro, mecha'),
        3: ('body, host, mecha', 'corpo, hospedeiro, mecha'), 4: ('host, mecha', 'hospedeiro, mecha')}

DEPARTMENTS_INTRO = (
    'Every natural creature now carries a gene: forty new ones, eighty-nine in all. Each belongs to its creature\'s '
    'department, and the Splicer sorts them that way: a row\'s chip steps through the departments with a usable '
    'gene, the arrows through its genes. The cost is printed in grey on the cassette.',
    'Toda criatura natural carrega um gene agora: quarenta novos, oitenta e nove ao todo. Cada um pertence ao '
    'departamento da criatura, e a Emendadora os ordena assim: o selo da fileira passa pelos departamentos com gene '
    'utilizável, as setas pelos genes dele. O custo vem em cinza no cassete.')
DEPARTMENTS_FIT = (
    'Each gene below is named with its load and its cost. Load 1 to 3 fits a body, a host and a mecha; load 4 only a '
    'host and a Gene Port. Numbers work at 70% in bodies '
    'and hosts. Food, bows, trades and armour work for people only: a host or a mecha has no stomach for them.',
    'Cada gene abaixo vem com a carga e o custo. Carga de 1 a 3 cabe em corpo, hospedeiro e mecha; carga 4, só em '
    'hospedeiro e Porta Genética. Os números valem '
    '70% em corpos e hospedeiros. Comida, arcos, trocas e armadura só valem para gente: hospedeiro e mecha não têm '
    'estômago para isso.')

PAIRS_INTRO = (
    'A pair in one strand is named. A synergy adds 10 stability, a conflict takes 15. Point at Stab on the Splicer '
    'to read the strand\'s pairs. Most new pairs only fit a host or a Gene Port: a body\'s serum holds load 3.',
    'Um par na mesma fita tem nome. Sinergia soma 10 de estabilidade; conflito tira 15. Aponte para Est na Emendadora '
    'para ler os pares da fita. Quase todo par novo só cabe em hospedeiro ou Porta Genética: o soro do corpo leva '
    'carga 3.')

DIRECTORY_PAGES = [
    ('text',
     'Open a $(item)DNA Sequencer$() and press the $(l)Directory$() tab: the 82 vanilla creatures by department, '
     'each with its spawn egg. A cell is stamped when your team $(l)samples$() the species, $(l)jars$() its head, '
     'or $(l)rides$() it as a host. Teammates share one Directory; alone, it is yours.',
     'Abra um $(item)Sequenciador de DNA$() e aperte a aba $(l)Cadastro$(): as 82 criaturas por departamento, cada '
     'uma com seu ovo. A célula ganha carimbo quando a equipe $(l)colhe$() a espécie, $(l)põe a cabeça$() no pote ou '
     'a $(l)monta$() como hospedeiro. Colegas de equipe dividem um Cadastro; sozinho, é seu.'),
    ('text',
     'Point at a cell for its blood, tool, genes and your reads, its title and the jar it needs; a blank cell gives '
     'a hint. Three are $(l)[REDACTED]$() until filed. 10 species stamped: a memo. 25 jarred: $(l)Headhunter$(). All '
     '77 species on the books sampled: $(l)Complete Census$().',
     'Aponte uma célula: sangue, ferramenta, genes e leituras, cargo e pote. Célula em branco dá uma pista. Três '
     'são $(l)[CONFIDENCIAL]$() até cadastradas. 10 espécies: um memorando. 25 em potes: $(l)Caça-Talentos$(). '
     'As 77 espécies cadastradas colhidas: $(l)Censo Completo$().'),
]

PRINTER_PAGES = [
    ('text',
     '$(l)Print-to-Order:$() a DNA Fraction of quality 40 or better in the $(l)Template$() slot prints a headless '
     'host of its species, in 60 s, at the fraction\'s quality as freshness. Anyone may press Print; the presser is '
     'its employer. The fraction is used up. Printed hosts drop no loot when they die.',
     '$(l)Impressão sob encomenda:$() uma Fração de DNA de qualidade 40 ou mais no espaço $(l)Molde$() imprime um '
     'hospedeiro sem cabeça da espécie, em 60 s, com o frescor da qualidade da fração. Qualquer um aperta Imprimir; '
     'quem aperta é o empregador. A fração se gasta. Impresso não larga nada ao morrer.'),
    ('text',
     'The door is one block wide, two tall. $(l)Tiny$() (chicken, rabbit, frog, fish): a quarter of the materials. '
     '$(l)Small$() (pig, sheep, wolf, fox, cat): half. $(l)Medium$() (cow, villager, zombie, creeper, strider, llama, '
     'squid): a full print. Marine models need water at the doorstep. No bosses, golems, spirits or heads alone.',
     'A porta tem um bloco de largura e dois de altura. $(l)Miúdo$() (galinha, coelho, sapo, peixe): um quarto do '
     'material. $(l)Pequeno$() (porco, ovelha, lobo, raposa, gato): metade. $(l)Médio$() (vaca, aldeão, zumbi, '
     'creeper, lavagante, lhama, lula): o inteiro. Marinhos pedem água na soleira. Nada de chefões, golens, '
     'espíritos ou só cabeça.'),
]

JEI_PRINTER = (
    'Prints a new headless body for a head in a jar, in 60 seconds: 16 rotten flesh, 8 bone blocks, 4 redstone '
    'blocks, 8 slime balls, 2,000 mB of saline and 200 kFE. With a DNA Fraction of quality 40 or better in the '
    'Template slot it prints a host of that species instead, materials scaled by its size; anyone may press Print.',
    'Imprime um novo corpo sem cabeça para uma cabeça no pote, em 60 segundos: 16 carnes podres, 8 blocos de osso, 4 '
    'blocos de redstone, 8 bolas de slime, 2.000 mB de soro e 200 kFE. Com uma Fração de DNA de qualidade 40 ou mais '
    'no espaço Molde, imprime um hospedeiro dessa espécie, com o material conforme o tamanho; qualquer um aperta '
    'Imprimir.')
JEI_LINES = {
    'jei.headinajar.gene_splicer': (
        'Splices up to three library genes (at least 30% read) into a Serum Cassette with Bio-Gel, a glass bottle and '
        'an optional catalyst. Each row\'s chip picks a department, its arrows a gene of it.',
        'Emenda até três genes da biblioteca (lidos 30% ou mais) num Cassete de Soro, com Biogel, uma garrafa e um '
        'catalisador opcional. O selo de cada fileira escolhe o departamento; as setas, o gene.'),
}


def _pack(lines, limit=14):
    """(en, pt) lines joined into as few text pages as fit both languages (the written edition's count)."""
    from gen_p27_clarity import written_lines
    pages, now = [], []
    for line in lines:
        trial = now + [line]
        if now and any(len(written_lines('$(br)'.join(x[i] for x in trial))) > limit for i in (0, 1)):
            pages.append(now)
            now = [line]
        else:
            now = trial
    if now:
        pages.append(now)
    return [('text', '$(br)'.join(x[0] for x in p), '$(br)'.join(x[1] for x in p)) for p in pages]


def _strip_cost(text):
    text = text.replace('%%', '%')  # a language line's escaped percent; a page is not a format string
    for prefix in ('Cost: ', 'Custo: '):
        if text.startswith(prefix):
            return text[len(prefix):]
    return text


def _gene_pages(EN, PT):
    """The department pages: each department's new genes, with load, where they fit and the cost."""
    from gen_p37_foundations import DEPARTMENTS as D
    lines = []
    for dept in D:
        genes = [g for g in LOADS if SOURCE[g] in (dept[5] or [])]
        if not genes:
            continue
        lines.append(('$(l)' + dept[1] + '$()', '$(l)' + dept[3] + '$()'))
        for g in genes:
            row = []
            for i, table in enumerate((EN, PT)):
                row.append(f'$(item){table[f"gene.headinajar.{g}"]}$() {LOADS[g]}: {_strip_cost(table[f"gene.headinajar.{g}.cost"])}')
            lines.append(tuple(row))
    return _pack(lines)


def _pair_pages(EN, PT):
    def name(t, g):
        return t[f'gene.headinajar.{g}']
    lines = [('$(l)Synergies (+10)$()', '$(l)Sinergias (+10)$()')]
    for a, b, *_ in L.OLD_SYNERGIES + L.SYNERGIES:
        lines.append(tuple(f'{t[f"synergy.headinajar.{a}.{b}"]}: {name(t, a)} + {name(t, b)}' for t in (EN, PT)))
    lines.append(('$(l)Conflicts (-15)$()', '$(l)Conflitos (-15)$()'))
    for a, b, *_ in L.OLD_CONFLICTS + L.CONFLICTS:
        lines.append(tuple(f'{name(t, a)} + {name(t, b)}' for t in (EN, PT)))
    return _pack(lines)


def _handbook(EN, PT):
    if L is None:
        return
    import gen_p27_handbook_a as ha
    import gen_p27_handbook_b as hb
    entries = [
        {'cat': 'research', 'id': 'departments', 'sort': 20, 'icon': 'headinajar:gene_splicer',
         'en': 'Gene Departments', 'pt': 'Departamentos',
         'pages': [('text',) + DEPARTMENTS_INTRO, ('text',) + DEPARTMENTS_FIT] + _gene_pages(EN, PT),
         'note': ('HR apologises to the cow.', 'O RH pede desculpas à vaca.')},
        {'cat': 'research', 'id': 'pairs', 'sort': 21, 'icon': 'headinajar:serum_cassette',
         'en': 'Named Pairs', 'pt': 'Pares Nomeados',
         'pages': [('text',) + PAIRS_INTRO] + _pair_pages(EN, PT)},
        {'cat': 'research', 'id': 'directory', 'sort': 22, 'icon': 'headinajar:dna_sequencer',
         'en': 'Staff Directory', 'pt': 'Cadastro de Pessoal',
         'pages': DIRECTORY_PAGES,
         'note': ('Every position will be filled.', 'Toda vaga será preenchida.')},
    ]
    have = {(e['cat'], e['id']) for e in hb.ENTRIES}
    for e in entries:
        if (e['cat'], e['id']) not in have:
            hb.ENTRIES.append(e)
    printer = next(e for e in ha.ENTRIES if (e['cat'], e['id']) == ('life_support', 'body_printer'))
    if len(printer['pages']) == 2:
        printer['pages'] += PRINTER_PAGES
    ha.JEI_INFO['headinajar:body_printer'] = JEI_PRINTER
    hb.JEI_INFO['headinajar:safety_placard'] = ('Hangs on a wall. Use it to change the notice: there are fourteen.',
                                               'Fica pendurada na parede. Use para trocar o aviso: são catorze.')
    for key, (en, pt) in JEI_LINES.items():
        EN[key], PT[key] = en, pt


def run(EN, PT, write, shaped):
    lines = _lines()
    taken = [key for key, _e, _p in lines if key in EN]
    assert not taken, f'already in the language files: {taken}'
    for key, en, pt in lines:
        EN[key], PT[key] = en, _pt(pt)
    _genes(EN, PT)
    _pairs(EN, PT)
    _hints(EN, PT)
    _sounds(EN, PT, write)
    _placards(EN, PT, write)
    _particles(write)
    _advancements(EN, PT, write)
    _handbook(EN, PT)
    # the Night Shift is a department, not a key: the text check's key pattern learns the difference
    import re
    import gen_p27_clarity as c
    c.PHYSICAL_KEY = re.compile(c.PHYSICAL_KEY.pattern.replace(r'|\bShift\b|', r'|(?<!Night )\bShift\b|'))
    assert '(?<!Night )' in c.PHYSICAL_KEY.pattern
    for key, (en, pt) in OVERRIDES.items():
        assert key in EN, key
        EN[key], PT[key] = en, pt
    _raise_written_cap()
    _check_action_bar(EN, PT, [k for k, _e, _p in lines])

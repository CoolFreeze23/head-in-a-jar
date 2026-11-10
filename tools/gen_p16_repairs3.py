"""Lines and data for the third round of repairs: nothing lethal or costly without a word first, and no input left
unanswered. Imported by gen_data.py.

Refusal lines follow gen_p9.py's rule (what is wrong, then what to do next). Warnings are said to a head's owner before
something costly happens to it: what is happening, how much glass is left, and what to do (lang key
'warning.headinajar.<id>'). %s arguments are filled in by the code.
"""
import os
import re

# id (after 'refusal.headinajar.'): (en, pt)
REFUSALS = {
    # another player hit a placed head: %s its owner
    'head.hit_not_yours': ("That is %s's head. The glass is not for knocking.",
                           'Essa é a cabeça de %s. O vidro não é para bater.'),
    # Print pressed with less power stored than a whole print takes: %1$s kFE needed, %2$s kFE stored, %3$s the Galvanic Pile
    'printer.no_power': ('A print takes %1$s kFE stored; it has %2$s. Feed its %3$s first.',
                         'Uma impressão precisa de %1$s kFE guardados; tem %2$s. Alimente a %3$s antes.'),
    # Print pressed while a finished body waits in the vat for its owner (%s) to be a jar again
    'printer.waiting': ("A body waits in the vat for %s. It comes out once they're a jar.",
                        'Um corpo espera no tanque por %s. Ele sai quando voltar a ser pote.'),
}

# id (after 'warning.headinajar.'): (en, pt)
WARNINGS = {
    # a jar (bare, on a host, in a socket or cockpit) knocked: %1$s glass left, of %2$s, %3$s the Glass Patch
    'jar.glass_hit': ('The glass took a knock: %1$s/%2$s left. A %3$s mends it.',
                      'O vidro levou um tranco: restam %1$s/%2$s. Um %3$s conserta.'),
    'jar.glass_low': ('Glass at %1$s/%2$s: the next bad knock is a real death. Get clear, then a %3$s.',
                      'Vidro em %1$s/%2$s: o próximo tranco forte é morte de verdade. Saia daí e use um %3$s.'),
    # no glass left, and the hits now go to the head itself: %s the Life Support Cradle
    'jar.glass_gone': ('No glass left: every hit now is real. Get away, then rest in a %s.',
                       'Sem vidro: cada golpe agora é de verdade. Fuja e descanse num %s.'),
    # the owner's placed head, out in the world: %1$s/%2$s glass, %3$s the Head Satchel
    'head.hit': ('Your head is being knocked about: glass %1$s/%2$s. Fetch it with your %3$s.',
                 'Sua cabeça está levando pancadas: vidro %1$s/%2$s. Busque com sua %3$s.'),
    'head.burning': ('Your head is in the fire: glass %1$s/%2$s, one a second. Fetch it with your %3$s now.',
                     'Sua cabeça está no fogo: vidro %1$s/%2$s, um por segundo. Busque já com sua %3$s.'),
    # the mecha around a pilot destroyed: %1$s the mecha, %2$s/%3$s glass, %4$s the Glass Patch
    'mecha.wrecked': ('%1$s is wrecked. You were thrown clear; the glass took one knock: %2$s/%3$s.',
                      '%1$s foi destruído. Você foi lançado para fora; o vidro levou um tranco: %2$s/%3$s.'),
    # a print finished while its owner had their head on (or was out in another body): %s the Body Printer
    'printer.body_waits': ('Your new body is ready, but you have one on. It waits in the %s until you are a jar.',
                           'Seu corpo novo está pronto, mas você já está num. Ele espera na %s até você ser pote.'),
}

# (key, en, pt)
LANG = [
    # deaths, in the company's words. Without a culprit the line takes the victim (%1$s); '.player' adds who they were
    # fighting (%2$s); with a culprit, %2$s is it, and '.item' adds its named weapon (%3$s).
    ('death.attack.headinajar.jar_broken', "%1$s's jar gave out. Noggin & Sons regrets the breakage",
     'O pote de %1$s não aguentou. A Noggin & Filhos lamenta a quebra'),
    ('death.attack.headinajar.jar_broken.player', "%1$s's jar gave out while fighting %2$s. Noggin & Sons regrets the breakage",
     'O pote de %1$s não aguentou enquanto lutava contra %2$s. A Noggin & Filhos lamenta a quebra'),
    ('death.attack.headinajar.jar_smashed', "%1$s's jar was smashed by %2$s. Noggin & Sons regrets the breakage",
     'O pote de %1$s foi quebrado por %2$s. A Noggin & Filhos lamenta a quebra'),
    ('death.attack.headinajar.jar_smashed.item', "%1$s's jar was smashed by %2$s using %3$s. Noggin & Sons regrets the breakage",
     'O pote de %1$s foi quebrado por %2$s usando %3$s. A Noggin & Filhos lamenta a quebra'),
    ('death.attack.headinajar.head_lost', '%1$s lost their head, jar and all',
     '%1$s perdeu a cabeça, com pote e tudo'),
    ('death.attack.headinajar.head_lost.player', '%1$s lost their head, jar and all, while fighting %2$s',
     '%1$s perdeu a cabeça, com pote e tudo, enquanto lutava contra %2$s'),
    ('death.attack.headinajar.head_burned', '%1$s left their head too near the fire. Noggin & Sons accepts no liability',
     '%1$s deixou a cabeça perto demais do fogo. A Noggin & Filhos não se responsabiliza'),
    ('death.attack.headinajar.head_burned.player', '%1$s left their head too near the fire while fighting %2$s. Noggin & Sons accepts no liability',
     '%1$s deixou a cabeça perto demais do fogo enquanto lutava contra %2$s. A Noggin & Filhos não se responsabiliza'),
    # the chair's closing line when the head was piped to a Cradle instead of the outlet (text only)
    ('dolores.headinajar.done_cradle', 'DOLORES: Neuro-link established. Your head has been piped to the Cradle.',
     'DOLORES: Neuroconexão estabelecida. Sua cabeça foi enviada ao Berço.'),
    # a Blood Sample or DNA Fraction: which genes its creature carries (%s their names), and how many auto-reads of
    # samples this good take each one to a Draft (%s)
    ('item.headinajar.sample.carries', 'Carries: %s', 'Carrega: %s'),
    ('item.headinajar.sample.no_genes', 'Carries no readable genes', 'Não carrega genes legíveis'),
    ('item.headinajar.sample.reads', 'About %s auto-reads of this quality to a Draft (30%%)',
     'Cerca de %s leituras automáticas desta qualidade até um Rascunho (30%%)'),
    # the Splicer's load: %1$s the strand's load, %2$s the cap of the lightest thing that takes it, %3$s that thing
    ('gui.headinajar.splicer.load_fits', 'Load %1$s/%2$s (%3$s)', 'Carga %1$s/%2$s (%3$s)'),
    # %1$s the load, %2$s the heaviest any target takes
    ('gui.headinajar.splicer.load_heavy', 'Load %1$s/%2$s: too heavy', 'Carga %1$s/%2$s: pesada demais'),
    ('gui.headinajar.splicer.target.body', 'body', 'corpo'),
    ('gui.headinajar.splicer.target.host', 'host', 'hospedeiro'),
    ('gui.headinajar.splicer.target.mecha', 'Chimera', 'Quimera'),
]

# the vanilla creatures that carry genes, by their names in both languages (the mod's other carriers come from mods
# that may not be installed, and are left out of the handbook)
CREATURES = {
    'spider': ('Spider', 'Aranha'), 'cave_spider': ('Cave Spider', 'Aranha-das-cavernas'), 'enderman': ('Enderman', 'Enderman'),
    'rabbit': ('Rabbit', 'Coelho'), 'strider': ('Strider', 'Lavagante'), 'phantom': ('Phantom', 'Phantom'), 'bat': ('Bat', 'Morcego'),
    'dolphin': ('Dolphin', 'Golfinho'), 'horse': ('Horse', 'Cavalo'), 'camel': ('Camel', 'Camelo'), 'donkey': ('Donkey', 'Burro'),
    'mule': ('Mule', 'Mula'), 'breeze': ('Breeze', 'Breeze'), 'squid': ('Squid', 'Lula'), 'cod': ('Cod', 'Bacalhau'),
    'salmon': ('Salmon', 'Salmão'), 'drowned': ('Drowned', 'Afogado'), 'tropical_fish': ('Tropical Fish', 'Peixe-tropical'),
    'iron_golem': ('Iron Golem', 'Golem de ferro'), 'turtle': ('Turtle', 'Tartaruga'), 'blaze': ('Blaze', 'Blaze'),
    'magma_cube': ('Magma Cube', 'Cubo de magma'), 'slime': ('Slime', 'Slime'), 'wither_skeleton': ('Wither Skeleton', 'Esqueleto Wither'),
    'cat': ('Cat', 'Gato'), 'ocelot': ('Ocelot', 'Jaguatirica'), 'glow_squid': ('Glow Squid', 'Lula-brilhante'), 'wolf': ('Wolf', 'Lobo'),
    'hoglin': ('Hoglin', 'Hoglin'), 'guardian': ('Guardian', 'Guardião'), 'elder_guardian': ('Elder Guardian', 'Guardião-ancião'),
    'creeper': ('Creeper', 'Creeper'), 'axolotl': ('Axolotl', 'Axolote'), 'chicken': ('Chicken', 'Galinha'),
    'villager': ('Villager', 'Aldeão'), 'ender_dragon': ('Ender Dragon', 'Dragão do Ender'), 'warden': ('Warden', 'Warden'),
    'wither': ('Wither', 'Wither'),
}

HANDBOOK_INTRO = (
    ('A gene goes into a serum once your library has it read to 30%: a $(item)Draft$(). Each auto-read of a $(item)DNA '
     'Fraction$() at the $(item)DNA Sequencer$() adds 15% of the fraction\'s quality, shared among its creature\'s genes. '
     'A quality-80 fraction of a one-gene creature adds 12%: three reads to a Draft. A two-gene creature takes five. '
     'Calling the bases yourself reads up to 25% of the quality, so fewer. A fraction\'s tooltip counts the reads for you.',
     'Um gene entra num soro quando sua biblioteca o tem lido a 30%: um $(item)Rascunho$(). Cada leitura automática de uma '
     '$(item)Fração de DNA$() no $(item)Sequenciador de DNA$() soma 15% da qualidade da fração, dividida entre os genes da '
     'criatura. Uma fração de qualidade 80 de uma criatura com um gene soma 12%: três leituras até um Rascunho. Com dois '
     'genes, cinco. Chamar as bases você mesmo lê até 25% da qualidade, então menos. A dica da fração conta as leituras.'),
    ('Creatures not listed here (a cow, a pig, a sheep, a zombie) carry nothing readable: their fractions say so. '
     'The pages that follow list every gene and who carries it.',
     'Criaturas fora desta lista (vaca, porco, ovelha, zumbi) não carregam nada legível: as frações delas dizem isso. '
     'As páginas a seguir listam cada gene e quem o carrega.'),
)


def genes_from_source():
    """(gene id, [vanilla creature ids]) in the order Gene.java declares them, read from the source itself."""
    here = os.path.dirname(__file__)
    path = os.path.join(here, '..', 'src', 'main', 'java', 'com', 'coolfreeze23', 'headinajar', 'genetics', 'Gene.java')
    text = open(path, encoding='utf-8').read()
    body = text[text.index('public enum Gene {'):text.index('public enum Category')]
    out = []
    for m in re.finditer(r'^\s+([A-Z_]+)\((Category\.[A-Z]+[^)]*)\)[,;]', body, re.M):
        creatures = [c.split(':', 1)[1] for c in re.findall(r'"([a-z_]+:[a-z_]+)"', m.group(2)) if c.startswith('minecraft:')]
        if creatures:
            out.append((m.group(1).lower(), creatures))
    return out


def run(EN, PT, write, shaped):
    for key, (en, pt) in REFUSALS.items():
        EN[f'refusal.headinajar.{key}'], PT[f'refusal.headinajar.{key}'] = en, pt
    for key, (en, pt) in WARNINGS.items():
        EN[f'warning.headinajar.{key}'], PT[f'warning.headinajar.{key}'] = en, pt
    for key, en, pt in LANG:
        EN[key], PT[key] = en, pt

    # the deaths' own damage types (message ids match the lang keys above); the two dealt by the mod itself end a head
    # out in the world, as the generic kill did, so they pass every protection a kill passes
    for name in ('jar_broken', 'jar_smashed', 'head_lost', 'head_burned'):
        write(f'data/headinajar/damage_type/{name}.json', {'message_id': f'headinajar.{name}', 'exhaustion': 0.0, 'scaling': 'never'})
    dealt = ['headinajar:head_lost', 'headinajar:head_burned']
    for tag in ('bypasses_armor', 'bypasses_cooldown', 'bypasses_effects', 'bypasses_enchantments', 'bypasses_invulnerability',
                'bypasses_resistance', 'bypasses_shield', 'no_knockback'):
        write(f'data/minecraft/tags/damage_type/{tag}.json', {'replace': False, 'values': dealt})

    # the handbook: who carries which gene, and how many reads a Draft takes
    genes = genes_from_source()
    for lang_code, idx, table in (('en_us', 0, EN), ('pt_br', 1, PT)):
        pages = [p[idx] for p in HANDBOOK_INTRO]
        lines = []
        for gene, creatures in genes:
            name = table[f'gene.headinajar.{gene}']
            lines.append(f'$(item){name}$(): ' + ', '.join(CREATURES[c][idx] for c in creatures))
        for i in range(0, len(lines), 7):
            pages.append('$(br)'.join(lines[i:i + 7]))
        write(f'assets/headinajar/patchouli_books/handbook/{lang_code}/entries/genetics/carriers.json', {
            'name': 'Who carries what' if idx == 0 else 'Quem carrega o quê',
            'icon': 'headinajar:dna_fraction', 'category': 'headinajar:genetics', 'sortnum': 1,
            'pages': [{'type': 'patchouli:text', 'text': t} for t in pages]})

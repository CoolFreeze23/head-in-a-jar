"""Phase 5 (Genetics Department) data: blood-type tags, recipes, models and language. Called by gen_data.py."""
import json
import os

GENES = {
    'spider_climb': ('Spider Climb', 'Escalada de Aranha'), 'ender_blink': ('Ender Blink', 'Piscar do Ender'),
    'rabbit_spring': ('Rabbit Spring', 'Salto de Coelho'), 'strider_soles': ('Strider Soles', 'Solas de Lavagante'),
    'phantom_membrane': ('Phantom Membrane', 'Membrana de Phantom'), 'dolphin_grace': ('Dolphin Grace', 'Graça do Golfinho'),
    'equine_gait': ('Equine Gait', 'Passo Equino'), 'breeze_updraft': ('Breeze Updraft', 'Corrente de Breeze'), 'gills': ('Gills', 'Guelras'),
    'golem_frame': ('Golem Frame', 'Estrutura de Golem'), 'turtle_shell': ('Turtle Shell', 'Casco de Tartaruga'),
    'blaze_core': ('Blaze Core', 'Núcleo de Blaze'), 'slime_body': ('Slime Body', 'Corpo de Slime'), 'wither_ward': ('Wither Ward', 'Guarda Wither'),
    'cat_eyes': ('Cat Eyes', 'Olhos de Gato'), 'glow_cells': ('Glow Cells', 'Células Brilhantes'), 'echolocation': ('Echolocation', 'Ecolocalização'),
    'venom_sac': ('Venom Sac', 'Bolsa de Veneno'), 'pack_instinct': ('Pack Instinct', 'Instinto de Matilha'),
    'hoglin_tusks': ('Hoglin Tusks', 'Presas de Hoglin'), 'guardian_eye': ('Guardian Eye', 'Olho de Guardião'),
    'creeper_gland': ('Creeper Gland', 'Glândula de Creeper'), 'axolotl_regrowth': ('Axolotl Regrowth', 'Regeneração de Axolote'),
    'feather_bones': ('Feather Bones', 'Ossos de Pena'), 'heros_blood': ("Hero's Blood", 'Sangue de Herói'),
    'draconic_wings': ('Draconic Wings', 'Asas Dracônicas'), 'sonic_boom': ('Sonic Boom', 'Estrondo Sônico'),
    'hiccups': ('Hiccups', 'Soluços'), 'clucking': ('Clucking', 'Cacarejo'), 'bioluminescence': ('Bioluminescence', 'Bioluminescência'),
    'brittle_bones': ('Brittle Bones', 'Ossos Frágeis'), 'photophobia': ('Photophobia', 'Fotofobia'), 'glass_jaw': ('Glass Jaw', 'Queixo de Vidro'),
    'gigantism': ('Gigantism', 'Gigantismo'), 'shrinkage': ('Shrinkage', 'Encolhimento'),
    'magnetic_personality': ('Magnetic Personality', 'Personalidade Magnética'), 'hybrid_vigour': ('Hybrid Vigour', 'Vigor Híbrido'),
    'chimeric_harmony': ('Chimeric Harmony', 'Harmonia Quimérica'),
}

BLOOD = {
    # type: (en, pt, members)
    'warm': ('Warm blood', 'Sangue quente', []),
    'cold': ('Cold blood', 'Sangue frio', ['squid', 'glow_squid', 'cod', 'salmon', 'tropical_fish', 'pufferfish', 'turtle', 'axolotl', 'frog',
                                           'tadpole', 'guardian', 'elder_guardian', 'dolphin', 'sniffer']),
    'ichor': ('Ichor', 'Icor', ['spider', 'cave_spider', 'bee', 'silverfish', 'slime', 'armadillo']),
    'ender': ('Ender blood', 'Sangue do Ender', ['enderman', 'endermite', 'shulker', 'ender_dragon']),
    'brimstone': ('Brimstone blood', 'Sangue de enxofre', ['blaze', 'magma_cube', 'strider', 'hoglin', 'piglin', 'piglin_brute', 'ghast']),
    'grave': ('Grave blood', 'Sangue de cova', ['zombie', 'husk', 'drowned', 'zombie_villager', 'zombified_piglin', 'zoglin', 'phantom',
                                               'zombie_horse']),
    'arcane': ('Arcane blood', 'Sangue arcano', ['witch', 'evoker', 'illusioner', 'warden']),
    'marrow': ('Marrow (bloodless)', 'Medula (sem sangue)', ['skeleton', 'stray', 'wither_skeleton', 'bogged', 'skeleton_horse', 'wither']),
    'oil': ('Oil (bloodless)', 'Óleo (sem sangue)', ['iron_golem', 'headinajar:lunk', 'headinajar:rover', 'headinajar:spider_walker',
                                                     'headinajar:hopper', 'headinajar:bulwark']),
    'ecto': ('Ecto (bloodless)', 'Ecto (sem sangue)', ['vex', 'allay', 'breeze', 'snow_golem']),
}

ITEMS = {
    'syringe': ('Syringe', 'Seringa'), 'precision_syringe': ('Precision Syringe', 'Seringa de Precisão'),
    'leviathan_needle': ('Leviathan Needle', 'Agulha Leviatã'), 'marrow_drill': ('Marrow Drill', 'Broca de Medula'),
    'oil_tap': ('Oil Tap', 'Torneira de Óleo'), 'ecto_trap': ('Ecto Trap', 'Armadilha de Ecto'),
    'blood_sample': ('Blood Sample', 'Amostra de Sangue'), 'dna_fraction': ('DNA Fraction', 'Fração de DNA'),
    'plasma_vial': ('Plasma Vial', 'Frasco de Plasma'), 'bio_gel': ('Bio-Gel', 'Biogel'), 'serum_cassette': ('Serum Cassette', 'Cartucho de Soro Genético'),
    'gene_wash': ('Gene Wash', 'Lavagem Genética'), 'chimera_frame': ('Chimera Frame', 'Estrutura Quimera'),
}

BLOCKS = {
    'centrifuge': ('Centrifuge', 'Centrífuga'), 'dna_sequencer': ('DNA Sequencer', 'Sequenciador de DNA'),
    'gene_splicer': ('Gene Splicer', 'Emendador de Genes'), 'injection_rig': ('Injection Rig', 'Estação de Injeção'),
    'leech_jar': ('Leech Jar', 'Pote de Sanguessugas'),
}

LANG = [
    ('entity.headinajar.chimera', 'Chimera', 'Quimera'),
    ('item.headinajar.blood_sample.of', 'Blood Sample (%s)', 'Amostra de Sangue (%s)'),
    ('item.headinajar.dna_fraction.of', 'DNA Fraction (%s)', 'Fração de DNA (%s)'),
    ('item.headinajar.sample.quality', 'Quality %s', 'Qualidade %s'),
    ('item.headinajar.sample.genes', '%s readable genes', '%s genes legíveis'),
    ('item.headinajar.syringe.quality', 'Sample quality %s to %s (healthier creatures give more)', 'Qualidade da amostra %s a %s (criaturas saudáveis dão mais)'),
    ('item.headinajar.syringe.how', 'Use on a creature to draw blood', 'Use numa criatura para tirar sangue'),
    ('item.headinajar.syringe.bloodless', 'Draws %s from the bloodless', 'Tira %s dos sem sangue'),
    ('item.headinajar.serum.gene', '%s at %s%%', '%s a %s%%'),
    ('item.headinajar.serum.stats', 'Stability %s, load %s, mutation %s%%', 'Estabilidade %s, carga %s, mutação %s%%'),
    ('item.headinajar.serum.how', 'Use on a host or a Chimera; use an Injection Rig for yourself', 'Use num hospedeiro ou numa Quimera; para você, use a Estação de Injeção'),
    ('message.headinajar.bloodless', 'No blood here: use a Marrow Drill, Oil Tap or Ecto Trap', 'Sem sangue aqui: use uma Broca de Medula, Torneira de Óleo ou Armadilha de Ecto'),
    ('message.headinajar.wrong_tool', 'This tool does not work on that creature', 'Esta ferramenta não funciona nessa criatura'),
    ('message.headinajar.boss_hide', 'Its hide is too thick: use a Leviathan Needle', 'O couro é grosso demais: use uma Agulha Leviatã'),
    ('message.headinajar.mecha_gene_port', 'This frame has no Gene Ports (build a Chimera)', 'Esta estrutura não tem Portas Genéticas (construa uma Quimera)'),
    ('message.headinajar.inject.ok', 'Serum accepted', 'Soro aceito'),
    ('message.headinajar.inject.mutated', 'Serum accepted... with a mutation', 'Soro aceito... com uma mutação'),
    ('message.headinajar.inject.rejected', 'REJECTED: the blood types fought', 'REJEITADO: os tipos sanguíneos brigaram'),
    ('message.headinajar.inject.full', 'No room for another serum', 'Não cabe outro soro'),
    ('message.headinajar.inject.too_heavy', 'That serum is too heavy for this body', 'Esse soro é pesado demais para este corpo'),
    ('message.headinajar.washed', 'Your genes are back to factory settings', 'Seus genes voltaram às configurações de fábrica'),
    ('message.headinajar.called', 'Called %s of %s bases', 'Acertou %s de %s bases'),
    ('message.headinajar.rig_needs_body', 'The rig needs a body on the neck or on the link', 'A estação precisa de um corpo, com cabeça ou pela conexão'),
    ('message.headinajar.leech_jar', '%s samples', '%s amostras'),
    ('splicer.headinajar.no_genes', 'Pick at least one gene', 'Escolha pelo menos um gene'),
    ('splicer.headinajar.too_heavy', 'Too heavy: a serum carries at most 8 load', 'Pesado demais: um soro leva no máximo 8 de carga'),
    ('splicer.headinajar.no_gel', 'Needs Bio-Gel', 'Precisa de Biogel'),
    ('splicer.headinajar.no_bottle', 'Needs a glass bottle', 'Precisa de uma garrafa de vidro'),
    ('splicer.headinajar.out_full', 'Take the last cassette out first', 'Tire o último cartucho primeiro'),
    ('gui.headinajar.rig.how', 'Stand close, press Inject', 'Fique perto e aperte Injetar'),
    ('gui.headinajar.rig.inject', 'Inject', 'Injetar'),
    ('gui.headinajar.sequencer.auto', 'Auto', 'Auto'),
    ('gui.headinajar.sequencer.insert', 'Insert a DNA Fraction', 'Coloque uma Fração de DNA'),
    ('gui.headinajar.sequencer.nothing', 'No readable genes', 'Nenhum gene legível'),
    ('gui.headinajar.sequencer.reading', 'Reading...', 'Lendo...'),
    ('gui.headinajar.splicer.splice', 'Splice', 'Emendar'),
    ('gui.headinajar.splicer.stability', 'Stab %s%%', 'Estab %s%%'),
    ('gui.headinajar.splicer.mutation', 'Mut %s%%', 'Mut %s%%'),
    ('gui.headinajar.splicer.load', 'Load %s/%s', 'Carga %s/%s'),
    ('hud.headinajar.gene_key', '[%s] %s', '[%s] %s'),
    ('mecha_frame.headinajar.gene_ports', '%s Gene Ports: serums by hand, never rejected', '%s Portas Genéticas: soros à mão, nunca rejeitados'),
    ('mecha_frame.headinajar.genes_kept', 'Keeps %s serums', 'Guarda %s soros'),
]


def run(EN, PT, write, shaped):
    S, W = 'headinajar:surgical_steel_ingot', 'headinajar:neuro_copper_wire'
    for gene, (en, pt) in GENES.items():
        EN[f'gene.headinajar.{gene}'], PT[f'gene.headinajar.{gene}'] = en, pt
    for blood, (en, pt, members) in BLOOD.items():
        EN[f'blood.headinajar.{blood}'], PT[f'blood.headinajar.{blood}'] = en, pt
        if members:
            write(f'data/headinajar/tags/entity_type/blood/{blood}.json',
                  {'values': [m if ':' in m else 'minecraft:' + m for m in members]})
    for name, (en, pt) in ITEMS.items():
        EN[f'item.headinajar.{name}'], PT[f'item.headinajar.{name}'] = en, pt
        write(f'assets/headinajar/models/item/{name}.json', {'parent': 'minecraft:item/handheld' if name in (
            'syringe', 'precision_syringe', 'leviathan_needle', 'marrow_drill', 'oil_tap') else 'minecraft:item/generated',
            'textures': {'layer0': f'headinajar:item/{name}'}})
    for key, en, pt in LANG:
        EN[key], PT[key] = en, pt

    write('data/headinajar/tags/entity_type/thick_hide.json', {'values': ['#c:bosses', 'minecraft:elder_guardian', 'minecraft:warden']})

    # recipes
    shaped('syringe', ['N', 'G', 'S'], dict(N='iron_nugget', G='glass', S=S))
    shaped('precision_syringe', ['GLG', ' Y '], dict(G='gold_nugget', L='headinajar:lens', Y='headinajar:syringe'))
    shaped('leviathan_needle', ['NYN'], dict(N='netherite_scrap', Y='headinajar:precision_syringe'))
    shaped('marrow_drill', [' S ', 'SVS', ' W '], dict(S=S, V='headinajar:servo', W=W))
    shaped('oil_tap', ['S  ', ' B ', '  S'], dict(S=S, B='bucket'))
    shaped('ecto_trap', ['GSG', 'GLG'], dict(G='glass', S=S, L='soul_lantern'))
    shaped('bio_gel', ['PSP'], dict(P='headinajar:plasma_vial', S='slime_ball'), 2)
    shaped('gene_wash', [' M ', 'PBP'], dict(M='milk_bucket', P='headinajar:plasma_vial', B='glass_bottle'), 2)
    shaped('centrifuge', ['SWS', 'CVC', 'SRS'], dict(S=S, W=W, C='copper_ingot', V='headinajar:servo', R='redstone'))
    shaped('dna_sequencer', ['SLS', 'CHC', 'SRS'], dict(S=S, L='headinajar:lens', C='copper_ingot', H='headinajar:synapse_chip', R='redstone'))
    shaped('gene_splicer', ['LHL', 'SBS', 'SRS'], dict(L='headinajar:lens', H='headinajar:synapse_chip', S=S, B='brewing_stand', R='redstone'))
    shaped('injection_rig', ['S S', 'SYS', 'SVS'], dict(S=S, Y='headinajar:syringe', V='headinajar:servo'))
    shaped('leech_jar', ['GKG', 'GBG', 'GGG'], dict(G='glass', K=S, B='slime_ball'))

    # blocks
    rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    lab = ['centrifuge', 'dna_sequencer', 'gene_splicer', 'injection_rig']
    for b in lab:
        write(f'assets/headinajar/models/block/{b}.json', {'parent': 'minecraft:block/orientable', 'textures': {
            'particle': 'headinajar:block/lab_side', 'side': 'headinajar:block/lab_side', 'top': f'headinajar:block/{b}_top',
            'front': f'headinajar:block/{b}_front'}})
        write(f'assets/headinajar/blockstates/{b}.json', {'variants': {f'facing={d}': {'model': f'headinajar:block/{b}', 'y': r} for d, r in rot.items()}})

    def el(f, t, tex):
        return {'from': f, 'to': t, 'faces': {d: {'texture': tex} for d in ('north', 'south', 'east', 'west', 'up', 'down')}}
    write('assets/headinajar/models/block/leech_jar.json', {
        'parent': 'minecraft:block/block', 'render_type': 'minecraft:translucent', 'ambientocclusion': False,
        'textures': {'particle': 'headinajar:block/leech_jar', 'glass': 'headinajar:block/leech_jar', 'lid': 'headinajar:block/leech_jar_lid'},
        'elements': [el([4, 0, 4], [12, 9, 12], '#glass'), el([3.5, 9, 3.5], [12.5, 10, 12.5], '#lid')]})
    write('assets/headinajar/blockstates/leech_jar.json', {'variants': {f'facing={d}': {'model': 'headinajar:block/leech_jar', 'y': r} for d, r in rot.items()}})
    for b in lab + ['leech_jar']:
        write(f'assets/headinajar/models/item/{b}.json', {'parent': f'headinajar:block/{b}'})
        write(f'data/headinajar/loot_table/blocks/{b}.json', {
            'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'headinajar:{b}'}],
                                                 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    for b, (en, pt) in BLOCKS.items():
        EN[f'block.headinajar.{b}'], PT[f'block.headinajar.{b}'] = en, pt

    # the lab is head-operable (a jar can work it) and needs a pickaxe
    res = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources')
    for tag, extra in (('data/headinajar/tags/block/head_operable.json', ['headinajar:' + b for b in lab]),
                       ('data/minecraft/tags/block/mineable/pickaxe.json', ['headinajar:' + b for b in lab + ['leech_jar']])):
        path = os.path.join(res, tag)
        values = json.load(open(path, encoding='utf-8'))['values'] if os.path.exists(path) else []
        write(tag, {'values': values + [v for v in extra if v not in values]})

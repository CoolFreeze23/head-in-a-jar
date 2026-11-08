"""Phase 2 (Life Support) data: recipes, tags, models, language and the Employee Handbook. Called by gen_data.py."""
import json
import os

MODULES2 = {
    'suction_cups': (('Suction Cups', 'Walk into a wall to climb it; sneak to hang on.'),
                     ('Ventosas', 'Ande contra uma parede para escalá-la; agache para se segurar.')),
    'spider_legs': (('Spider Legs', 'The jar walks at 80% speed and steps up blocks.'),
                    ('Pernas de Aranha', 'O pote anda a 80% da velocidade e sobe degraus.')),
    'speaker_grille': (('Speaker Grille', 'What you say in chat floats above your jar.'),
                       ('Grade de Alto-falante', 'O que você diz no chat flutua acima do seu pote.')),
    'saline_recycler': (('Saline Recycler', 'Halves the saline your head uses.'),
                        ('Reciclador de Soro', 'Reduz pela metade o soro que sua cabeça usa.')),
    'shock_lid': (('Shock Lid', 'Zaps anything that hits the jar in melee.'),
                  ('Tampa de Choque', 'Dá choque em quem acerta o pote corpo a corpo.')),
    'self_righting_gyro': (('Self-Righting Gyro', 'No knockback, and no upside-down views.'),
                           ('Giroscópio Autonivelante', 'Sem repulsão e sem ver tudo de cabeça para baixo.')),
    'hub_antenna': (('Hub Antenna', '+50% neuro-link range.'), ('Antena Central', '+50% de alcance da neuroconexão.')),
    'life_sign_monitor': (('Life Sign Monitor', "Shows your body's health while you're in the jar."),
                          ('Monitor de Sinais Vitais', 'Mostra a vida do seu corpo enquanto você está no pote.')),
    'hat_mount': (('Hat Mount', 'Your helmet sits on the lid.'), ('Suporte de Chapéu', 'Seu capacete fica em cima da tampa.')),
}

PLACARDS = [
    ('HEADS MUST BE SECURED AT ALL TIMES', 'CABEÇAS DEVEM FICAR PRESAS O TEMPO TODO'),
    ('DO NOT SHAKE THE JARS', 'NÃO SACUDA OS POTES'),
    ('THIS FACILITY HAS GONE 0 DAYS WITHOUT AN INCIDENT', 'ESTA INSTALAÇÃO ESTÁ HÁ 0 DIAS SEM ACIDENTES'),
    ('LOST HEADS WILL BE HELD AT RECEPTION FOR 30 DAYS', 'CABEÇAS PERDIDAS FICAM NA RECEPÇÃO POR 30 DIAS'),
    ('KEEP HANDS, FEET AND NECK CLEAR OF THE BLADE', 'MANTENHA MÃOS, PÉS E PESCOÇO LONGE DA LÂMINA'),
    ('SALINE IS NOT A BEVERAGE', 'SORO NÃO É BEBIDA'),
    ('REPORT ALL HEADLESS WANDERING TO YOUR SUPERVISOR', 'INFORME QUALQUER CORPO SEM CABEÇA VAGANDO AO SEU SUPERVISOR'),
    ('A CLEAN JAR IS A HAPPY JAR', 'POTE LIMPO É POTE FELIZ'),
    ('NO TELEKINESIS IN THE BREAK ROOM', 'PROIBIDA TELECINESE NA SALA DE DESCANSO'),
    ('YOUR BODY IS COMPANY PROPERTY', 'SEU CORPO É PROPRIEDADE DA EMPRESA'),
    ('IN CASE OF FIRE, TAKE YOUR HEAD', 'EM CASO DE INCÊNDIO, LEVE SUA CABEÇA'),
    ('DOLORES IS LISTENING', 'DOLORES ESTÁ OUVINDO'),
]

HANDBOOK = {
    'en_us': {
        'categories': [
            ('getting_started', 'Orientation', 'Welcome to the company. Please keep your head.', 'headinajar:life_support_jar', 0),
            ('separation', 'Elective Separation', 'The chair, the link, and what to do when it goes wrong.', 'headinajar:separation_chair', 1),
            ('life_support', 'Life Support', 'Saline, glass and power: keeping a head alive.', 'headinajar:saline_bottle', 2),
            ('modules', 'Jar Modules', 'Accessories for the discerning head.', 'headinajar:propeller', 3),
        ],
        'entries': [
            ('getting_started', 'welcome', 'Welcome', 'headinajar:life_support_jar', [
                'Congratulations on joining $(l)Head Office$(). This handbook covers everything an employee needs: from the $(item)Separation Chair$() to keeping your head topped up with $(item)Cranial Saline$().',
                'Start with $(item)Surgical Steel$(): smelt a blend of iron, bone meal and coal. Then make $(item)Neuro-Copper Wire$(), a $(item)Lens$() and a $(item)Servo$(). Power comes from a $(item)Galvanic Pile$() fed with meat.']),
            ('separation', 'the_chair', 'The Separation Chair', 'headinajar:separation_chair', [
                'Load the chair with a $(item)Life Support Jar$(), $(item)Relay Goggles$(), a $(item)Collar Cam$() and four bottles of $(item)Cranial Saline$(), and give it 40 kFE. Sit down and hold $(l)Sign$() on Form 27-B.',
                'You may get up until the cut. After that, DOLORES will finish what she started. A worn blade can botch the job: keep a $(item)Replacement Blade$() handy.']),
            ('separation', 'the_link', 'The Neuro-Link', 'headinajar:relay_goggles', [
                'After the cut you walk around in your own headless body and see through the $(item)Collar Cam$(). Your head wears the goggles and sends the signal. The further apart they are, the worse the picture.',
                'Water, rock, storms, portals and the Warden all hurt the signal. At zero for five seconds you wake up in your head. Press $(k:key.headinajar.swap) to swap between body and head yourself.',
                'Carry your head in a $(item)Head Satchel$() for a perfect link, or dock it in a $(item)Life Support Cradle$(). $(item)Relay Masts$() pass the signal along: masts reach each other and your body within their radius.']),
            ('separation', 'when_it_goes_wrong', 'When It Goes Wrong', 'headinajar:suture_kit', [
                'If your body dies, it becomes a $(l)corpse$() that keeps your things, and you wake up in your head. As a jar, use a $(item)Suture Kit$() on the corpse to get back into it, or print a new body.',
                'If your head is destroyed while you are out walking, that is a real death. Guard it. A $(item)Signal Jammer$() near your enemies works wonders.']),
            ('life_support', 'saline', 'Cranial Saline', 'headinajar:saline_mixer', [
                'A head out in the world uses 1 mB of saline every 12 seconds. A docked or carried head uses none. A dry jar loses $(l)Jar Integrity$(), and integrity is the glass between you and the world.',
                'The $(item)Saline Mixer$() turns water, sugar, kelp and redstone into saline and bottles it. Use bottles on your placed head, put them in the Jar menu, or pipe saline into a Cradle.']),
            ('life_support', 'power', 'Power', 'headinajar:leyden_jar', [
                'Machines share power with their neighbours. A $(item)Leyden Jar$() stores 250 kFE. $(item)Tesla Nodes$() throw power up to 16 blocks: link them with a $(item)Surgical Wrench$() (sending node first).']),
            ('life_support', 'body_printer', 'The Body Printer', 'headinajar:body_printer', [
                'Lost your body? Load the printer with rotten flesh, bone blocks, redstone blocks, slime and 2000 mB of saline, then press Print as a jar. Sixty seconds and 200 kFE later, a fresh body steps out, collar already fitted.']),
            ('modules', 'modules', 'Fitting Modules', 'headinajar:hop_springs', [
                'Open the Jar menu with $(k:key.headinajar.jar_menu) while you are the jar. A Life Support Jar has three slots; a Reinforced Jar has five. A $(item)Compact Jar$() also has three and is head-sized: your head faces the front and the whole jar turns with it (a jar alone in a crafting grid becomes the other kind). Powered modules stop when the charge runs out.']),
        ],
    },
    'pt_br': {
        'categories': [
            ('getting_started', 'Integração', 'Bem-vindo à empresa. Por favor, mantenha sua cabeça.', 'headinajar:life_support_jar', 0),
            ('separation', 'Separação Eletiva', 'A cadeira, a conexão e o que fazer quando dá errado.', 'headinajar:separation_chair', 1),
            ('life_support', 'Suporte Vital', 'Soro, vidro e energia: mantendo uma cabeça viva.', 'headinajar:saline_bottle', 2),
            ('modules', 'Módulos do Pote', 'Acessórios para a cabeça exigente.', 'headinajar:propeller', 3),
        ],
        'entries': [
            ('getting_started', 'welcome', 'Boas-vindas', 'headinajar:life_support_jar', [
                'Parabéns por entrar para a $(l)Cabeça no Pote$(). Este manual cobre tudo o que um funcionário precisa: da $(item)Cadeira de Separação$() a manter sua cabeça abastecida de $(item)Soro Craniano$().',
                'Comece pelo $(item)Aço Cirúrgico$(): funda uma mistura de ferro, farinha de osso e carvão. Depois faça $(item)Fio de Neurocobre$(), uma $(item)Lente$() e um $(item)Servo$(). A energia vem de uma $(item)Pilha Galvânica$() alimentada com carne.']),
            ('separation', 'the_chair', 'A Cadeira de Separação', 'headinajar:separation_chair', [
                'Carregue a cadeira com um $(item)Pote de Suporte Vital$(), $(item)Óculos Retransmissores$(), uma $(item)Câmera de Colarinho$() e quatro frascos de $(item)Soro Craniano$(), e dê 40 kFE a ela. Sente e segure $(l)Assinar$() no Formulário 27-B.',
                'Você pode levantar até o corte. Depois disso, a DOLORES termina o que começou. Uma lâmina gasta pode estragar o serviço: tenha uma $(item)Lâmina de Reposição$() à mão.']),
            ('separation', 'the_link', 'A Neuroconexão', 'headinajar:relay_goggles', [
                'Depois do corte você anda no seu próprio corpo sem cabeça e enxerga pela $(item)Câmera de Colarinho$(). Sua cabeça usa os óculos e envia o sinal. Quanto mais longe, pior a imagem.',
                'Água, rocha, tempestades, portais e o Warden prejudicam o sinal. Com sinal zero por cinco segundos, você acorda na sua cabeça. Aperte $(k:key.headinajar.swap) para trocar entre corpo e cabeça você mesmo.',
                'Leve sua cabeça numa $(item)Bolsa de Cabeça$() para uma conexão perfeita, ou deixe-a num $(item)Berço de Suporte Vital$(). $(item)Mastros Retransmissores$() passam o sinal adiante: alcançam uns aos outros e o seu corpo dentro do raio.']),
            ('separation', 'when_it_goes_wrong', 'Quando Dá Errado', 'headinajar:suture_kit', [
                'Se seu corpo morrer, ele vira um $(l)cadáver$() que guarda suas coisas, e você acorda na sua cabeça. Como pote, use um $(item)Kit de Sutura$() no cadáver para voltar a ele, ou imprima um corpo novo.',
                'Se sua cabeça for destruída enquanto você passeia, é morte de verdade. Proteja-a. Um $(item)Bloqueador de Sinal$() perto dos inimigos faz maravilhas.']),
            ('life_support', 'saline', 'Soro Craniano', 'headinajar:saline_mixer', [
                'Uma cabeça solta no mundo usa 1 mB de soro a cada 12 segundos. Uma cabeça no berço ou carregada não usa nada. Um pote seco perde $(l)Integridade$(), e a integridade é o vidro entre você e o mundo.',
                'O $(item)Misturador de Soro$() transforma água, açúcar, alga e redstone em soro e engarrafa. Use frascos na sua cabeça, coloque no menu do pote ou canalize soro para um Berço.']),
            ('life_support', 'power', 'Energia', 'headinajar:leyden_jar', [
                'Máquinas dividem energia com as vizinhas. Uma $(item)Garrafa de Leyden$() guarda 250 kFE. $(item)Nós de Tesla$() lançam energia a até 16 blocos: ligue-os com uma $(item)Chave Cirúrgica$() (primeiro o nó que envia).']),
            ('life_support', 'body_printer', 'A Impressora de Corpos', 'headinajar:body_printer', [
                'Perdeu o corpo? Carregue a impressora com carne podre, blocos de osso, blocos de redstone, slime e 2000 mB de soro, e aperte Imprimir como pote. Sessenta segundos e 200 kFE depois, sai um corpo novo, já com câmera.']),
            ('modules', 'modules', 'Instalando Módulos', 'headinajar:hop_springs', [
                'Abra o menu do pote com $(k:key.headinajar.jar_menu) quando você for o pote. Um Pote de Suporte Vital tem três encaixes; um Reforçado tem cinco. Um $(item)Pote Compacto$() também tem três e é do tamanho de uma cabeça: sua cabeça fica de frente e o pote inteiro gira com ela (um pote sozinho na grade de criação vira o outro tipo). Módulos com energia param quando a carga acaba.']),
        ],
    },
}


def add(EN, PT, write, shaped):
    def shapeless(name, ingredients, count=1):
        write(f'data/headinajar/recipe/{name}.json', {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
                                                     'ingredients': [{'item': i if ':' in i else 'minecraft:' + i} for i in ingredients],
                                                     'result': {'id': 'headinajar:' + name, 'count': count}})

    S, W = 'headinajar:surgical_steel_ingot', 'headinajar:neuro_copper_wire'
    shaped('saline_mixer', ['SBS', 'WCW', 'SRS'], dict(S=S, B='bucket', W=W, C='cauldron', R='redstone'))
    shaped('body_printer', ['SGS', 'VPV', 'SRS'], dict(S=S, G='glass', V='headinajar:servo', P='piston', R='redstone_block'))
    shaped('leyden_jar', [' W ', 'GCG', 'GSG'], dict(W=W, G='glass', C='copper_block', S=S))
    shaped('tesla_node', [' L ', 'WCW', 'SSS'], dict(L='lightning_rod', W=W, C='copper_block', S=S))
    shaped('relay_mast', [' L ', 'WSW', ' S '], dict(L='headinajar:lens', W=W, S=S))
    shaped('relay_mast_mk2', [' C ', 'WMW', ' G '], dict(C='headinajar:synapse_chip', W=W, M='headinajar:relay_mast', G='gold_block'))
    shaped('signal_jammer', ['WCW', 'ASA', 'WRW'], dict(W=W, C='headinajar:synapse_chip', A='amethyst_shard', S=S, R='redstone_block'))
    shaped('safety_placard', ['YBY', 'YSY'], dict(Y='yellow_dye', B='black_dye', S=S), 4)
    shapeless('glass_patch', ['glass_pane', 'slime_ball'])
    shaped('synapse_chip', ['QWQ', 'GLG'], dict(Q='quartz', W=W, G='gold_ingot', L='lapis_lazuli'))
    shaped('surgical_wrench', [' S ', ' WS', 'S  '], dict(S=S, W=W))
    shaped('relay_goggles_mk2', ['GCG', ' R '], dict(G='gold_ingot', C='headinajar:synapse_chip', R='headinajar:relay_goggles'))
    shaped('relay_goggles_mk3', ['DCD', 'EGE'], dict(D='diamond', C='headinajar:synapse_chip', E='ender_pearl', G='headinajar:relay_goggles_mk2'))
    mods = {
        'suction_cups': (['SCS', ' X '], dict(S='slime_ball', C='copper_ingot', X=S)),
        'spider_legs': (['T T', 'SCS', 'T T'], dict(T='string', S=S, C='headinajar:servo')),
        'speaker_grille': (['NNN', 'WCW'], dict(N='iron_bars', W=W, C='note_block')),
        'saline_recycler': (['GPG', 'WCW'], dict(G='glass', P='hopper', W=W, C='headinajar:servo')),
        'shock_lid': (['LLL', 'WCW'], dict(L='lightning_rod', W=W, C='redstone_block')),
        'self_righting_gyro': ([' G ', 'GCG', ' G '], dict(G='gold_ingot', C='compass')),
        'hub_antenna': ([' L ', ' W ', 'SCS'], dict(L='lightning_rod', W=W, S=S, C='headinajar:synapse_chip')),
        'life_sign_monitor': (['WGW', 'SRS'], dict(W=W, G='glistering_melon_slice', S=S, R='redstone')),
        'hat_mount': (['SBS'], dict(S=S, B='leather_helmet')),
    }
    for name, (pattern, key) in mods.items():
        shaped(name, pattern, key)

    write('data/headinajar/tags/block/head_operable.json', {'values': [
        'headinajar:body_printer', 'headinajar:saline_mixer', 'headinajar:charging_dock', 'headinajar:life_support_cradle',
        'headinajar:galvanic_pile', 'headinajar:leyden_jar']})
    blocks = ['saline_mixer', 'body_printer', 'leyden_jar', 'tesla_node', 'relay_mast', 'relay_mast_mk2', 'signal_jammer', 'safety_placard']
    for b in blocks:
        write(f'data/headinajar/loot_table/blocks/{b}.json', {
            'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'headinajar:{b}'}],
                                                 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    return blocks, mods


def models(write, blocks, mods):
    rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}

    def cube(name, side, top, front=None):
        tex = {'particle': f'headinajar:block/{side}', 'side': f'headinajar:block/{side}', 'top': f'headinajar:block/{top}',
               'front': f'headinajar:block/{front or side}'}
        write(f'assets/headinajar/models/block/{name}.json', {'parent': 'minecraft:block/orientable', 'textures': tex})

    cube('saline_mixer', 'machine_side', 'saline_mixer_top', 'saline_mixer_front')
    cube('body_printer', 'machine_side', 'body_printer_top', 'body_printer_front')
    for b in ('saline_mixer', 'body_printer'):
        write(f'assets/headinajar/blockstates/{b}.json', {'variants': {f'facing={d}': {'model': f'headinajar:block/{b}', 'y': r} for d, r in rot.items()}})

    def el(f, t, tex):
        return {'from': f, 'to': t, 'faces': {d: {'texture': tex} for d in ('north', 'south', 'east', 'west', 'up', 'down')}}

    simple = {
        'leyden_jar': {'parent': 'minecraft:block/block', 'render_type': 'minecraft:translucent',
                       'textures': {'particle': 'headinajar:block/leyden_jar', 'glass': 'headinajar:block/leyden_jar', 'cap': 'headinajar:block/leyden_cap'},
                       'elements': [el([3, 0, 3], [13, 12, 13], '#glass'), el([4, 12, 4], [12, 14, 12], '#cap'), el([7, 14, 7], [9, 15, 9], '#cap')]},
        'tesla_node': {'parent': 'minecraft:block/block',
                       'textures': {'particle': 'headinajar:block/tesla_coil', 'coil': 'headinajar:block/tesla_coil', 'base': 'headinajar:block/surgical_steel_block',
                                    'ball': 'headinajar:block/tesla_ball'},
                       'elements': [el([3, 0, 3], [13, 3, 13], '#base'), el([6, 3, 6], [10, 11, 10], '#coil'), el([4, 11, 4], [12, 16, 12], '#ball')]},
        'relay_mast': {'parent': 'minecraft:block/block', 'ambientocclusion': False,
                       'textures': {'particle': 'headinajar:block/surgical_steel_block', 'pole': 'headinajar:block/mast_pole', 'dish': 'headinajar:block/mast_dish'},
                       'elements': [el([4, 0, 4], [12, 2, 12], '#pole'), el([7, 2, 7], [9, 26, 9], '#pole'), el([3, 22, 6], [13, 30, 7], '#dish'),
                                    el([7, 30, 7], [9, 32, 9], '#dish')]},
        'relay_mast_mk2': {'parent': 'minecraft:block/block', 'ambientocclusion': False,
                           'textures': {'particle': 'headinajar:block/surgical_steel_block', 'pole': 'headinajar:block/mast_pole_gold', 'dish': 'headinajar:block/mast_dish'},
                           'elements': [el([4, 0, 4], [12, 2, 12], '#pole'), el([7, 2, 7], [9, 30, 9], '#pole'), el([2, 24, 6], [14, 32, 7], '#dish'),
                                        el([6, 14, 6], [10, 18, 10], '#dish')]},
        'signal_jammer': {'parent': 'minecraft:block/block', 'ambientocclusion': False,
                          'textures': {'particle': 'headinajar:block/jammer', 'body': 'headinajar:block/jammer', 'pole': 'headinajar:block/mast_pole'},
                          'elements': [el([2, 0, 2], [14, 8, 14], '#body'), el([3, 8, 3], [4, 16, 4], '#pole'), el([12, 8, 12], [13, 18, 13], '#pole'),
                                       el([12, 8, 3], [13, 14, 4], '#pole')]},
    }
    for name, model in simple.items():
        write(f'assets/headinajar/models/block/{name}.json', model)
        write(f'assets/headinajar/blockstates/{name}.json', {'variants': {'': {'model': f'headinajar:block/{name}'}}})
    # placards: one model per notice, on the south face of the block's space, rotated by facing
    variants = {}
    for n in range(12):
        write(f'assets/headinajar/models/block/safety_placard_{n}.json', {
            'parent': 'minecraft:block/block', 'textures': {'particle': f'headinajar:block/placard_{n}', 'face': f'headinajar:block/placard_{n}',
                                                            'back': 'headinajar:block/surgical_steel_block'},
            'elements': [{'from': [1, 2, 15], 'to': [15, 14, 16], 'faces': {
                'north': {'texture': '#face', 'uv': [0, 2, 16, 14]}, 'south': {'texture': '#back'}, 'east': {'texture': '#back'},
                'west': {'texture': '#back'}, 'up': {'texture': '#back'}, 'down': {'texture': '#back'}}}]})
        for d, r in rot.items():
            variants[f'facing={d},notice={n}'] = {'model': f'headinajar:block/safety_placard_{n}', 'y': r}
    write('assets/headinajar/blockstates/safety_placard.json', {'variants': variants})
    # fluid block
    write('assets/headinajar/models/block/cranial_saline.json', {'textures': {'particle': 'minecraft:block/water_still'}})
    write('assets/headinajar/blockstates/cranial_saline.json', {'variants': {'': {'model': 'headinajar:block/cranial_saline'}}})

    for b in blocks:
        parent = 'safety_placard_0' if b == 'safety_placard' else b
        write(f'assets/headinajar/models/item/{b}.json', {'parent': f'headinajar:block/{parent}'})
    for i in ['glass_patch', 'synapse_chip', 'surgical_wrench', 'relay_goggles_mk2', 'relay_goggles_mk3', 'cranial_saline_bucket'] + list(mods):
        write(f'assets/headinajar/models/item/{i}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'headinajar:item/{i}'}})


def handbook(write):
    write('data/headinajar/patchouli_books/handbook/book.json', {
        'name': 'book.headinajar.handbook', 'landing_text': 'book.headinajar.handbook.landing', 'version': 1,
        'use_resource_pack': True, 'i18n': True, 'model': 'patchouli:book_brown', 'creative_tab': 'headinajar:main',
        'book_texture': 'patchouli:textures/gui/book_brown.png', 'show_progress': False})
    for lang, book in HANDBOOK.items():
        base = f'assets/headinajar/patchouli_books/handbook/{lang}'
        for cid, name, desc, icon, order in book['categories']:
            write(f'{base}/categories/{cid}.json', {'name': name, 'description': desc, 'icon': icon, 'sortnum': order})
        for cat, eid, name, icon, pages in book['entries']:
            write(f'{base}/entries/{cat}/{eid}.json', {'name': name, 'icon': icon, 'category': f'headinajar:{cat}',
                                                        'pages': [{'type': 'patchouli:text', 'text': t} for t in pages]})


def lang(EN, PT, mods):
    rows = [
        ('block.headinajar.saline_mixer', 'Saline Mixer', 'Misturador de Soro'),
        ('block.headinajar.body_printer', 'Body Printer', 'Impressora de Corpos'),
        ('block.headinajar.leyden_jar', 'Leyden Jar', 'Garrafa de Leyden'),
        ('block.headinajar.tesla_node', 'Tesla Node', 'Nó de Tesla'),
        ('block.headinajar.relay_mast', 'Relay Mast', 'Mastro Retransmissor'),
        ('block.headinajar.relay_mast_mk2', 'Relay Mast Mk II', 'Mastro Retransmissor Mk II'),
        ('block.headinajar.signal_jammer', 'Signal Jammer', 'Bloqueador de Sinal'),
        ('block.headinajar.safety_placard', 'Safety Placard', 'Placa de Segurança'),
        ('block.headinajar.cranial_saline', 'Cranial Saline', 'Soro Craniano'),
        ('fluid_type.headinajar.cranial_saline', 'Cranial Saline', 'Soro Craniano'),
        ('item.headinajar.cranial_saline_bucket', 'Bucket of Cranial Saline', 'Balde de Soro Craniano'),
        ('item.headinajar.glass_patch', 'Glass Patch', 'Remendo de Vidro'),
        ('item.headinajar.synapse_chip', 'Synapse Chip', 'Chip Sináptico'),
        ('item.headinajar.surgical_wrench', 'Surgical Wrench', 'Chave Cirúrgica'),
        ('item.headinajar.surgical_wrench.how', 'Use a Tesla Node, then the one it should power. Sneak-use to unlink.',
         'Use num Nó de Tesla e depois no nó que ele deve abastecer. Agache e use para desligar.'),
        ('item.headinajar.relay_goggles_mk2', 'Relay Goggles Mk II', 'Óculos Retransmissores Mk II'),
        ('item.headinajar.relay_goggles_mk3', 'Relay Goggles Mk III', 'Óculos Retransmissores Mk III'),
        ('message.headinajar.dehydrating', 'Your head is dehydrating! Find saline.', 'Sua cabeça está desidratando! Encontre soro.'),
        ('message.headinajar.cradle_status', 'Cradle: %s FE, %s mB saline', 'Berço: %s FE, %s mB de soro'),
        ('message.headinajar.energy_status', '%s: %s / %s FE', '%s: %s / %s FE'),
        ('message.headinajar.tesla_from', 'Sending node chosen: now use the receiving node', 'Nó de envio escolhido: agora use o nó receptor'),
        ('message.headinajar.tesla_linked', 'Tesla Nodes linked', 'Nós de Tesla ligados'),
        ('message.headinajar.tesla_unlinked', 'Tesla Node unlinked', 'Nó de Tesla desligado'),
        ('message.headinajar.tesla_too_far', 'Too far: Tesla Nodes reach 16 blocks', 'Longe demais: Nós de Tesla alcançam 16 blocos'),
        ('message.headinajar.printer_has_head', 'Only a head in its jar can order a new body', 'Só uma cabeça no pote pode encomendar um corpo novo'),
        ('message.headinajar.printer_missing', 'The printer needs all its materials and 2000 mB of saline', 'A impressora precisa de todos os materiais e 2000 mB de soro'),
        ('message.headinajar.printed', 'Your new body is ready. It is a little damp.', 'Seu corpo novo está pronto. Está um pouco úmido.'),
        ('gui.headinajar.energy', '%s / %s FE', '%s / %s FE'),
        ('gui.headinajar.water', 'Water: %s / %s mB', 'Água: %s / %s mB'),
        ('gui.headinajar.saline', 'Saline: %s / %s mB', 'Soro: %s / %s mB'),
        ('gui.headinajar.printer.need', '16 flesh, 8 bone blocks, 4 redstone blocks, 8 slime', '16 carnes, 8 blocos de osso, 4 de redstone, 8 slime'),
        ('gui.headinajar.printer.print', 'Print', 'Imprimir'),
        ('hud.headinajar.life_signs', 'BODY %s/%s', 'CORPO %s/%s'),
        ('book.headinajar.handbook', 'Employee Handbook', 'Manual do Funcionário'),
        ('book.headinajar.handbook.landing', 'Everything a new employee needs to know about keeping their head. Please read before signing anything.',
         'Tudo o que um novo funcionário precisa saber para manter a cabeça. Leia antes de assinar qualquer coisa.'),
    ]
    for i, (en, pt) in enumerate(PLACARDS):
        rows.append((f'placard.headinajar.{i}', en, pt))
    for key, ((en_name, en_desc), (pt_name, pt_desc)) in MODULES2.items():
        rows.append((f'item.headinajar.{key}', en_name, pt_name))
        rows.append((f'module.headinajar.{key}.desc', en_desc, pt_desc))
    for key, en, pt in rows:
        EN[key] = en
        PT[key] = pt


def run(EN, PT, write, shaped):
    blocks, mods = add(EN, PT, write, shaped)
    models(write, blocks, mods)
    handbook(write)
    lang(EN, PT, mods)
    # head-operable blocks also need the pickaxe tags
    return blocks

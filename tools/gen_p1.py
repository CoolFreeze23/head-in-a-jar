"""Phase 1 (Head Start) data: recipes, loot, tags, block and item models, and language. Called by gen_data.py."""


def add(EN, PT, write, shaped):
    def shapeless(name, ingredients, count=1):
        ings = []
        for i in ingredients:
            if isinstance(i, dict):
                ings.append(i)
            else:
                ings.append({'item': i if ':' in i else 'minecraft:' + i})
        write(f'data/headinajar/recipe/{name}.json', {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
                                                     'ingredients': ings, 'result': {'id': 'headinajar:' + name, 'count': count}})

    S = 'headinajar:surgical_steel_ingot'
    W = 'headinajar:neuro_copper_wire'
    shapeless('surgical_steel_blend', ['iron_ingot', 'bone_meal', 'coal'])
    for kind, time in (('blasting', 100), ('smelting', 200)):
        write(f'data/headinajar/recipe/surgical_steel_ingot_from_{kind}.json', {
            'type': f'minecraft:{kind}', 'category': 'misc', 'ingredient': {'item': 'headinajar:surgical_steel_blend'},
            'result': {'id': S}, 'experience': 0.4, 'cookingtime': time})
    shaped('surgical_steel_block', ['SSS', 'SSS', 'SSS'], dict(S=S))
    write('data/headinajar/recipe/surgical_steel_ingot_from_block.json', {
        'type': 'minecraft:crafting_shapeless', 'category': 'misc', 'ingredients': [{'item': 'headinajar:surgical_steel_block'}],
        'result': {'id': S, 'count': 9}})
    shaped('neuro_copper_wire', ['CRC'], dict(C='copper_ingot', R='redstone'), 4)
    shaped('lens', ['GAG'], dict(G='glass', A='amethyst_shard'))
    shaped('servo', [' P ', 'SWS'], dict(P='piston', S=S, W=W))
    shaped('excision_head', [' B ', 'VLH', ' R '], dict(B='headinajar:surgical_steel_block', V='headinajar:servo', L='headinajar:lens',
                                                       H='shears', R='redstone_block'))
    shaped('booth_console', ['WPW', 'SCS', ' W '], dict(W=W, P='glass_pane', S=S, C='repeater'))
    shaped('separation_chair', ['WEW', 'SWS', 'CVL'], dict(W='white_wool', E='headinajar:excision_head', S=S,
                                                           C='headinajar:booth_console', V='headinajar:servo', L='lever'))
    shaped('life_support_cradle', ['G G', 'W W', 'SSS'], dict(G='glass', W=W, S=S))
    shaped('galvanic_pile', ['CWC', 'CRC', 'CWC'], dict(C='copper_ingot', W=W, R='rotten_flesh'))
    shaped('charging_dock', ['CWC', 'SRS'], dict(C='copper_ingot', W=W, S=S, R='redstone_block'))
    shapeless('replacement_blade', [S, S, 'diamond'])
    shapeless('saline_bottle', [{'type': 'neoforge:components', 'items': 'minecraft:potion',
                                 'components': {'minecraft:potion_contents': {'potion': 'minecraft:water'}}},
                                'sugar', 'kelp', 'redstone'], 2)
    shaped('relay_goggles', ['LCL', 'GWG'], dict(L='leather', C='copper_ingot', G='headinajar:lens', W=W))
    shaped('collar_cam', [' G ', 'WVW', 'SSS'], dict(G='headinajar:lens', W=W, V='headinajar:servo', S=S))
    shaped('head_satchel', ['TLT', 'L L', 'LLL'], dict(T='string', L='leather'))
    shapeless('suture_kit', ['string', 'slime_ball', 'iron_nugget', 'headinajar:saline_bottle'])
    shaped('life_support_jar', [' C ', 'GSG', 'TRT'], dict(C='copper_ingot', G='glass', S='slime_ball', T=S, R='redstone'))

    blocks = ['surgical_steel_block', 'separation_chair', 'life_support_cradle', 'galvanic_pile', 'charging_dock']
    for b in blocks:
        write(f'data/headinajar/loot_table/blocks/{b}.json', {
            'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'headinajar:{b}'}],
                                                 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    all_blocks = blocks + ['saline_mixer', 'body_printer', 'leyden_jar', 'tesla_node', 'relay_mast', 'relay_mast_mk2', 'signal_jammer', 'safety_placard', 'body_locker', 'assembly_gantry']
    write('data/minecraft/tags/block/mineable/pickaxe.json', {'values': [f'headinajar:{b}' for b in all_blocks]})
    write('data/minecraft/tags/block/needs_stone_tool.json', {'values': [f'headinajar:{b}' for b in all_blocks if b != 'safety_placard']})

    # models
    write('assets/headinajar/models/block/surgical_steel_block.json', {'parent': 'minecraft:block/cube_all',
                                                                     'textures': {'all': 'headinajar:block/surgical_steel_block'}})
    write('assets/headinajar/blockstates/surgical_steel_block.json', {'variants': {'': {'model': 'headinajar:block/surgical_steel_block'}}})

    def el(f, t, tex, faces='nsewud'):
        names = {'n': 'north', 's': 'south', 'e': 'east', 'w': 'west', 'u': 'up', 'd': 'down'}
        return {'from': f, 'to': t, 'faces': {names[c]: {'texture': tex} for c in faces}}

    chair = {
        'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout',
        'textures': {'particle': 'headinajar:block/chair_steel', 'steel': 'headinajar:block/chair_steel',
                     'cushion': 'headinajar:block/chair_cushion', 'head': 'headinajar:block/excision_head',
                     'block': 'headinajar:block/surgical_steel_block'},
        'elements': [
            el([4, 0, 4], [12, 4, 12], '#steel'),
            el([1, 4, 1], [15, 7, 14], '#cushion'),
            el([1, 7, 13], [15, 24, 15], '#cushion'),
            el([0, 7, 2], [2, 11, 13], '#block'),
            el([14, 7, 2], [16, 11, 13], '#block'),
            el([6, 24, 13], [10, 31, 16], '#block'),
            el([3, 29, 3], [13, 32, 13], '#head'),
            el([7, 29, 12], [9, 31, 14], '#block'),
        ],
    }
    write('assets/headinajar/models/block/separation_chair.json', chair)
    rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    write('assets/headinajar/blockstates/separation_chair.json', {'variants': {
        f'facing={d}': {'model': 'headinajar:block/separation_chair', 'y': r} for d, r in rot.items()}})

    cradle = {
        'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout',
        'textures': {'particle': 'headinajar:block/cradle_side', 'top': 'headinajar:block/cradle_top', 'side': 'headinajar:block/cradle_side'},
        'elements': [
            {'from': [1, 0, 1], 'to': [15, 5, 15], 'faces': {
                'up': {'texture': '#top'}, 'down': {'texture': '#side'},
                'north': {'texture': '#side', 'uv': [0, 11, 16, 16]}, 'south': {'texture': '#side', 'uv': [0, 11, 16, 16]},
                'east': {'texture': '#side', 'uv': [0, 11, 16, 16]}, 'west': {'texture': '#side', 'uv': [0, 11, 16, 16]}}},
            el([0, 3, 7], [1, 12, 9], '#side'),
            el([15, 3, 7], [16, 12, 9], '#side'),
        ],
    }
    write('assets/headinajar/models/block/life_support_cradle.json', cradle)
    write('assets/headinajar/blockstates/life_support_cradle.json', {'variants': {'': {'model': 'headinajar:block/life_support_cradle'}}})

    pile = {
        'parent': 'minecraft:block/block',
        'textures': {'particle': 'headinajar:block/galvanic_pile_side', 'side': 'headinajar:block/galvanic_pile_side',
                     'top': 'headinajar:block/galvanic_pile_top', 'steel': 'headinajar:block/surgical_steel_block'},
        'elements': [
            el([3, 0, 3], [13, 2, 13], '#steel'),
            {'from': [4, 2, 4], 'to': [12, 14, 12], 'faces': {
                'north': {'texture': '#side'}, 'south': {'texture': '#side'}, 'east': {'texture': '#side'}, 'west': {'texture': '#side'},
                'up': {'texture': '#top'}}},
            el([3, 14, 3], [13, 15, 13], '#steel'),
            el([7, 15, 7], [9, 16, 9], '#steel'),
        ],
    }
    write('assets/headinajar/models/block/galvanic_pile.json', pile)
    write('assets/headinajar/blockstates/galvanic_pile.json', {'variants': {
        'lit=false': {'model': 'headinajar:block/galvanic_pile'}, 'lit=true': {'model': 'headinajar:block/galvanic_pile'}}})

    for b in blocks:
        write(f'assets/headinajar/models/item/{b}.json', {'parent': f'headinajar:block/{b}'})
    for i in ['surgical_steel_blend', 'surgical_steel_ingot', 'neuro_copper_wire', 'lens', 'servo', 'excision_head', 'booth_console',
              'replacement_blade', 'saline_bottle', 'relay_goggles', 'collar_cam', 'head_satchel', 'suture_kit']:
        write(f'assets/headinajar/models/item/{i}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'headinajar:item/{i}'}})

    # language: (key, en, pt)
    rows = [
        ('itemGroup.headinajar', 'Head Office', 'Cabeça no Pote'),
        ('item.headinajar.surgical_steel_blend', 'Surgical Steel Blend', 'Mistura de Aço Cirúrgico'),
        ('item.headinajar.surgical_steel_ingot', 'Surgical Steel Ingot', 'Lingote de Aço Cirúrgico'),
        ('item.headinajar.neuro_copper_wire', 'Neuro-Copper Wire', 'Fio de Neurocobre'),
        ('item.headinajar.lens', 'Lens', 'Lente'),
        ('item.headinajar.servo', 'Servo', 'Servo'),
        ('item.headinajar.excision_head', 'Excision Head', 'Cabeçote de Excisão'),
        ('item.headinajar.booth_console', 'Booth Console', 'Console da Cabine'),
        ('item.headinajar.replacement_blade', 'Replacement Blade', 'Lâmina de Reposição'),
        ('item.headinajar.saline_bottle', 'Cranial Saline', 'Soro Craniano'),
        ('item.headinajar.relay_goggles', 'Relay Goggles Mk I', 'Óculos Retransmissores Mk I'),
        ('item.headinajar.relay_goggles.range', 'Neuro-link range: %s blocks', 'Alcance da neuroconexão: %s blocos'),
        ('item.headinajar.relay_goggles.how', 'Worn by your head. The Separation Chair fits them.', 'Usados pela sua cabeça. A Cadeira de Separação coloca.'),
        ('item.headinajar.collar_cam', 'Collar Cam', 'Câmera de Colarinho'),
        ('item.headinajar.collar_cam.how', 'Bolted to your neck: your body sees through it.', 'Parafusada no pescoço: seu corpo enxerga por ela.'),
        ('item.headinajar.collar_cam.crooked', 'Crooked (-10% range)', 'Torta (-10% de alcance)'),
        ('item.headinajar.head_satchel', 'Head Satchel', 'Bolsa de Cabeça'),
        ('item.headinajar.head_satchel.how', 'With this in your inventory, use your head to carry it. Sneak-use to set it down.',
         'Com isto no inventário, use sua cabeça para carregá-la. Agache e use para pousá-la.'),
        ('item.headinajar.suture_kit', 'Suture Kit', 'Kit de Sutura'),
        ('item.headinajar.suture_kit.how', 'Carrying your head: hold use. As a jar: use it on your body.',
         'Carregando sua cabeça: segure usar. Como pote: use no seu corpo.'),
        ('item.headinajar.life_support_jar.how', 'Load it into a Separation Chair', 'Coloque numa Cadeira de Separação'),
        ('block.headinajar.surgical_steel_block', 'Block of Surgical Steel', 'Bloco de Aço Cirúrgico'),
        ('block.headinajar.separation_chair', 'Separation Chair', 'Cadeira de Separação'),
        ('block.headinajar.life_support_cradle', 'Life Support Cradle', 'Berço de Suporte Vital'),
        ('block.headinajar.galvanic_pile', 'Galvanic Pile', 'Pilha Galvânica'),
        ('entity.headinajar.head_jar', 'Head in a Jar', 'Cabeça no Pote'),
        ('entity.headinajar.head_jar.owned', "%s's Head", 'Cabeça de %s'),
        ('entity.headinajar.headless_body.corpse', "%s's Corpse", 'Cadáver de %s'),
        ('entity.headinajar.seat', 'Seat', 'Assento'),
        ('container.headinajar.separation_chair', 'Separation Chair', 'Cadeira de Separação'),
        ('key.headinajar.swap', 'Swap body / head', 'Trocar corpo / cabeça'),
        # messages
        ('message.headinajar.link_lost', 'Connection to body lost. Walk back to it, or book the Courier (%s).',
         'Conexão com o corpo perdida. Volte até ele ou chame o Entregador (%s).'),
        ('message.headinajar.link_lost.dimension', 'Your body left the dimension: link lost. An %s spans dimensions.',
         'Seu corpo saiu da dimensão: conexão perdida. Um %s atravessa dimensões.'),
        ('message.headinajar.signal_too_weak', 'Signal too weak to reach your body', 'Sinal fraco demais para alcançar seu corpo'),
        ('message.headinajar.body_died', 'Your body has died. You wake up in your head. A %s on the corpse brings it back.',
         'Seu corpo morreu. Você acorda na sua cabeça. Um %s no cadáver o traz de volta.'),
        ('message.headinajar.portal_blocked', 'Your head is in another dimension. Please bring it.', 'Sua cabeça está em outra dimensão. Traga-a, por favor.'),
        ('message.headinajar.need_satchel', 'You need a Head Satchel to carry your head', 'Você precisa de uma Bolsa de Cabeça para carregar sua cabeça'),
        ('message.headinajar.cradle_empty', 'The Cradle is empty', 'O Berço está vazio'),
        ('message.headinajar.cradle_holds', "The Cradle holds %s's head", 'O Berço guarda a cabeça de %s'),
        ('message.headinajar.suture_how', 'Carry your own head first (Head Satchel), or use the kit on your body as a jar',
         'Carregue sua própria cabeça antes (Bolsa de Cabeça), ou use o kit no seu corpo como pote'),
        ('message.headinajar.pile_status', 'Galvanic Pile: %s / %s FE, %s fuel', 'Pilha Galvânica: %s / %s FE, %s combustível'),
        # DOLORES
        ('dolores.headinajar.start', 'DOLORES: Thank you for choosing Elective Separation. Please keep your arms inside the chair.',
         'DOLORES: Obrigada por escolher a Separação Eletiva. Mantenha os braços dentro da cadeira.'),
        ('dolores.headinajar.gas', 'DOLORES: Administering a pleasant gas. Breathe normally. Or don\'t.',
         'DOLORES: Administrando um gás agradável. Respire normalmente. Ou não.'),
        ('dolores.headinajar.scan', 'DOLORES: Scanning. Please hold still. You have a lovely neck.',
         'DOLORES: Escaneando. Fique parado. Você tem um pescoço lindo.'),
        ('dolores.headinajar.cut', 'DOLORES: Commencing separation. This will not hurt for very long.',
         'DOLORES: Iniciando a separação. Isto não vai doer por muito tempo.'),
        ('dolores.headinajar.jar', 'DOLORES: Your head is now in a jar. Congratulations on your promotion.',
         'DOLORES: Sua cabeça agora está num pote. Parabéns pela promoção.'),
        ('dolores.headinajar.done', 'DOLORES: Neuro-link established. Please collect your head on the way out.',
         'DOLORES: Neuroconexão estabelecida. Retire sua cabeça na saída.'),
        ('dolores.headinajar.oopsie', 'DOLORES: Oopsie.', 'DOLORES: Opa.'),
        ('dolores.headinajar.abort', 'DOLORES: Procedure cancelled. Your head remains attached. For now.',
         'DOLORES: Procedimento cancelado. Sua cabeça continua no lugar. Por enquanto.'),
        ('dolores.headinajar.new_blade', 'DOLORES: New blade fitted. So shiny.', 'DOLORES: Lâmina nova instalada. Que brilho.'),
        ('dolores.headinajar.refuse.power', 'DOLORES: Insufficient power. I refuse to stop halfway.',
         'DOLORES: Energia insuficiente. Eu me recuso a parar no meio.'),
        ('dolores.headinajar.refuse.jar', 'DOLORES: Where would we put your head? Insert a Life Support Jar.',
         'DOLORES: Onde colocaríamos sua cabeça? Insira um Pote de Suporte Vital.'),
        ('dolores.headinajar.refuse.goggles', 'DOLORES: Relay Goggles required. You will want to see.',
         'DOLORES: Óculos Retransmissores necessários. Você vai querer enxergar.'),
        ('dolores.headinajar.refuse.collar', 'DOLORES: Collar Cam required. Your neck needs a hat.',
         'DOLORES: Câmera de Colarinho necessária. Seu pescoço precisa de um chapéu.'),
        ('dolores.headinajar.refuse.saline', 'DOLORES: Four bottles of Cranial Saline, please. Heads get thirsty.',
         'DOLORES: Quatro frascos de Soro Craniano, por favor. Cabeças sentem sede.'),
        ('dolores.headinajar.refuse.already', 'DOLORES: Our records show your head is already off.',
         'DOLORES: Nossos registros mostram que sua cabeça já saiu.'),
        # chair screen
        ('gui.headinajar.chair.energy', '%s kFE', '%s kFE'),
        ('gui.headinajar.chair.running', 'Operation: %s%%', 'Operação: %s%%'),
        ('gui.headinajar.chair.need.power', 'Power (40 kFE)', 'Energia (40 kFE)'),
        ('gui.headinajar.chair.need.jar', 'Life Support Jar', 'Pote de Suporte Vital'),
        ('gui.headinajar.chair.need.goggles', 'Relay Goggles', 'Óculos Retransmissores'),
        ('gui.headinajar.chair.need.collar', 'Collar Cam', 'Câmera de Colarinho'),
        ('gui.headinajar.chair.need.saline', 'Cranial Saline x4', 'Soro Craniano x4'),
        ('gui.headinajar.chair.wear', 'Blade wear: %s%%', 'Desgaste da lâmina: %s%%'),
        ('gui.headinajar.chair.sign', 'Hold to sign', 'Segure para assinar'),
        ('gui.headinajar.chair.signing', 'Signing...', 'Assinando...'),
        ('gui.headinajar.chair.sit', 'Sit in the chair to sign', 'Sente na cadeira para assinar'),
        ('gui.headinajar.chair.form', 'Form 27-B: Consent to Elective Separation', 'Formulário 27-B: Consentimento para Separação Eletiva'),
        ('gui.headinajar.chair.form.body', 'I, %s, consent to the removal of my head, and accept that it is now company property.',
         'Eu, %s, consinto com a remoção da minha cabeça e aceito que ela agora é propriedade da empresa.'),
        ('gui.headinajar.chair.form.hold', 'Hold the button for two seconds. You can still get up before the cut.',
         'Segure o botão por dois segundos. Você ainda pode levantar antes do corte.'),
        # HUD
        ('hud.headinajar.bar', 'LINK %s%%  %s ms  %s %s m', 'LINK %s%%  %s ms  %s %s m'),
        ('hud.headinajar.head', 'HEAD', 'CABEÇA'),
        ('hud.headinajar.body', 'BODY', 'CORPO'),
        ('hud.headinajar.carried', 'ON HIP', 'NO QUADRIL'),
        ('hud.headinajar.weak', 'WEAK SIGNAL', 'SINAL FRACO'),
        ('hud.headinajar.lost', 'CONNECTION TO BODY LOST', 'CONEXÃO COM O CORPO PERDIDA'),
        ('hud.headinajar.packet_loss', 'PACKET LOSS', 'PERDA DE PACOTES'),
        ('hud.headinajar.bubbles', '*muffled bubbling*', '*borbulhar abafado*'),
        ('hud.headinajar.boot', 'NEURO-LINK 2.1 ... %s%%', 'NEUROCONEXÃO 2.1 ... %s%%'),
        # commands
        ('command.headinajar.separated', '%s is now a head in a jar', '%s agora é uma cabeça num pote'),
        ('command.headinajar.restored', "%s's head is back on", 'A cabeça de %s voltou ao lugar'),
        ('command.headinajar.failed', 'Nothing changed for %s', 'Nada mudou para %s'),
        ('command.headinajar.trusted', '%s may now handle your head and body', '%s agora pode mexer na sua cabeça e no seu corpo'),
        ('command.headinajar.untrusted', '%s is no longer trusted', '%s não é mais de confiança'),
        ('command.headinajar.trust_list', 'Trusted: %s', 'Confiáveis: %s'),
        ('command.headinajar.status', '%s: form %s, link %s%%, head %s, body %s, charge %s FE',
         '%s: forma %s, conexão %s%%, cabeça %s, corpo %s, carga %s FE'),
    ]
    for key, en, pt in rows:
        EN[key] = en
        PT[key] = pt
    EN.pop('message.headinajar.sneak_to_detach', None)
    PT.pop('message.headinajar.sneak_to_detach', None)

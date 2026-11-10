"""Phase 6 (Board of Directors) data: frames, modules, heads at work, the socket, quantum links. Called by gen_data.py."""

P, V, C, W = 'headinajar:mecha_plating', 'headinajar:servo', 'headinajar:synapse_chip', 'headinajar:neuro_copper_wire'
S = 'headinajar:surgical_steel_ingot'

FRAMES = {
    # name: (en, pt, neck height, recipe pattern, key)
    'wheelhouse': ('Wheelhouse', 'Casa sobre Rodas', 1.125, ['PCP', 'PMP', 'V V'], dict(P=P, C='headinajar:collar_cam', M='minecart', V=V)),
    'skitter': ('Skitter', 'Rastejador', 1.25, ['VCV', 'PEP', 'V V'], dict(V=V, C='headinajar:collar_cam', P=P, E='fermented_spider_eye')),
    'colossus': ('Colossus', 'Colosso', 4.08, ['PCP', 'NBN', 'PVP'], dict(P=P, C='headinajar:collar_cam', N='netherite_ingot', B='headinajar:bulwark_frame', V=V)),
    'kite': ('Kite', 'Pipa', 1.875, ['FCF', 'PVP', ' V '], dict(F='phantom_membrane', C='headinajar:collar_cam', P=P, V=V)),
    'executive': ('Executive', 'Executivo', 4.08, ['GHG', 'NFN', 'GHG'], dict(G='gold_block', H=C, N='nether_star', F='headinajar:colossus_frame')),
}
CHIMERA_NECK = 2.52

MODULES = {
    # name: (en, en desc, pt, pt desc, pattern, key)
    'sprint_servos': ('Sprint Servos', 'Up to +80% speed (Boost); 15 FE/t while moving.', 'Servos de Corrida', 'Até +80% de velocidade (Impulso); 15 FE/t em movimento.',
                      ['RVR'], dict(R='redstone', V=V)),
    'stilts': ('Stilts', 'Steps up two blocks.', 'Pernas de Pau', 'Sobe dois blocos de uma vez.', ['P P', 'I I', 'I I'], dict(P=P, I='iron_bars')),
    'arachnid_legs': ('Arachnid Legs', 'Walks up walls (Grip sets climbing speed).', 'Pernas Aracnídeas', 'Anda pelas paredes (Aderência define a velocidade).',
                      ['S S', 'VPV', 'S S'], dict(S='string', V=V, P=P)),
    'hover_skirt': ('Hover Skirt', 'Floats 0.5 to 2 blocks over ground, water and lava. No fall damage.', 'Saia Flutuante',
                    'Flutua de 0,5 a 2 blocos sobre o chão, a água e a lava. Sem dano de queda.', ['PPP', 'F F'], dict(P=P, F='phantom_membrane')),
    'lava_treads': ('Lava Treads', 'Walks on lava.', 'Esteiras de Lava', 'Anda sobre a lava.', ['MPM'], dict(M='magma_block', P=P)),
    'ender_satchel': ('Ender Satchel', 'Opens your ender chest (2,000 FE).', 'Bolsa do Ender', 'Abre seu baú do Ender (2.000 FE).', ['PEP'], dict(P=P, E='ender_chest')),
    'smoke_mortar': ('Smoke Mortar', 'A smoke cloud where you aim; mobs inside lose their target.', 'Morteiro de Fumaça',
                     'Uma nuvem de fumaça onde você mira; criaturas dentro perdem o alvo.', [' T ', 'PDP'], dict(T='campfire', P=P, D='dispenser')),
    'missile_pod': ('Missile Pod', 'Four homing micro-missiles (Warhead sets damage and heat). No block damage.', 'Lança-Mísseis',
                    'Quatro micromísseis teleguiados (a Ogiva define dano e calor). Não destrói blocos.', ['FFF', 'PCP'], dict(F='firework_rocket', P=P, C=C)),
    'bio_reactor_core': ('Bio-Reactor Core', '60 FE/t from food, Bio-Gel or plasma in the Cargo Hold.', 'Núcleo Biorreator',
                         '60 FE/t de comida, Biogel ou plasma no compartimento de carga.', ['PGP', 'PSP'], dict(P=P, G='headinajar:bio_gel', S='smoker')),
    'capacitor_bank': ('Capacitor Bank', '+2 MFE battery.', 'Banco de Capacitores', '+2 MFE de bateria.', ['LLL', 'PCP'], dict(L='headinajar:leyden_jar', P=P, C=C)),
    'fusion_bottle': ('Fusion Bottle', 'Up to 600 FE/t from Plasma Pellets (Output); hot.', 'Garrafa de Fusão', 'Até 600 FE/t com Pastilhas de Plasma (Saída); esquenta.',
                      ['GNG', 'PBP'], dict(G='tinted_glass', N='nether_star', P=P, B='beacon')),
    'reactive_armour': ('Reactive Armour', '-40% explosion and projectile damage, 6 charges (one back per 30 s).', 'Blindagem Reativa',
                        '-40% de dano de explosão e projéteis, 6 cargas (uma volta a cada 30 s).', ['TPT'], dict(T='tnt', P=P)),
    'energy_shield': ('Energy Shield', 'Absorbs 25 to 100% of damage at 1,000 FE per HP (Strength).', 'Escudo de Energia',
                      'Absorve de 25 a 100% do dano a 1.000 FE por ponto (Força).', ['APA', 'PCP'], dict(A='amethyst_shard', P=P, C=C)),
    'medical_injector': ('Medical Injector', 'Heals pilot and passengers below the Threshold with Bio-Gel; repairs a Chimera.', 'Injetor Médico',
                         'Cura piloto e passageiros abaixo do Limiar com Biogel; repara uma Quimera.', ['GYG', 'PPP'], dict(G='headinajar:bio_gel', Y='headinajar:syringe', P=P)),
    'stealth_field': ('Stealth Field', 'Mobs beyond 4 blocks lose track of you; attacking drops it for 3 s.', 'Campo Furtivo',
                      'Criaturas a mais de 4 blocos perdem seu rastro; atacar desliga por 3 s.', ['EPE', 'PCP'], dict(E='ender_eye', P=P, C=C)),
    'tesla_coil': ('Tesla Coil', 'Arcs strike nearby hostiles (Voltage sets reach and damage).', 'Bobina de Tesla',
                   'Arcos atingem hostis por perto (Voltagem define alcance e dano).', ['T', 'P'], dict(T='headinajar:tesla_node', P=P)),
    'workbench': ('Workbench', 'A crafting grid on the go.', 'Bancada', 'Uma mesa de trabalho portátil.', ['T', 'P'], dict(T='crafting_table', P=P)),
    'bio_integration_port': ('Bio-Integration Port', 'One Gene Port on any frame; -20% battery.', 'Porta de Biointegração',
                             'Uma Porta Genética em qualquer estrutura; -20% de bateria.', ['GYG', 'PCP'], dict(G='headinajar:bio_gel', Y='headinajar:injection_rig', P=P, C=C)),
    'manipulator_hand': ('Manipulator Hand', 'Uses your selected hotbar item like a hand.', 'Mão Manipuladora', 'Usa o item selecionado na barra como uma mão.',
                         ['III', ' V '], dict(I='iron_ingot', V=V)),
    'excavator_scoop': ('Excavator Scoop', 'Hold: digs 3x3 of dirt, sand and gravel into the Cargo Hold.', 'Pá Escavadeira',
                        'Segure: cava 3x3 de terra, areia e cascalho para o compartimento.', ['S S', 'SVS'], dict(S='iron_shovel', V=V)),
    'flamethrower': ('Flamethrower', 'Hold: a 6-block cone of fire (Spread).', 'Lança-Chamas', 'Segure: um cone de fogo de 6 blocos (Abertura).',
                     ['BFB', ' V '], dict(B='blaze_powder', F='fire_charge', V=V)),
    'arc_cannon': ('Arc Cannon', 'A 16-block bolt that chains to three hostiles (Charge).', 'Canhão de Arco',
                   'Um raio de 16 blocos que salta para três hostis (Carga).', ['LTL', ' V '], dict(L='lightning_rod', T='headinajar:tesla_node', V=V)),
    'hemo_syringe': ('Hemo-Syringe', 'Jabs draw Blood Samples and patch the frame.', 'Hemosseringa', 'Picadas tiram Amostras de Sangue e remendam a estrutura.',
                     ['Y', 'V'], dict(Y='headinajar:precision_syringe', V=V)),
    'harvester': ('Harvester', 'Picks and replants ripe crops in a 5x5.', 'Colheitadeira', 'Colhe e replanta plantações maduras em 5x5.',
                  ['HHH', ' V '], dict(H='iron_hoe', V=V)),
    'welding_torch': ('Welding Torch', 'Hold: repairs the mecha you look at (or yours) with Mecha Plating from the hold.', 'Maçarico',
                      'Segure: repara o mecha que você olha (ou o seu) com Blindagem de Mecha do compartimento.', ['B', 'F', 'V'], dict(B='blaze_rod', F='flint_and_steel', V=V)),
    'plasma_lance': ('Plasma Lance', 'Hold: a 32-block beam, 20 damage a second. Very hot.', 'Lança de Plasma',
                     'Segure: um feixe de 32 blocos, 20 de dano por segundo. Muito quente.', ['EPE', ' V '], dict(E='end_rod', P='headinajar:plasma_pellet', V=V)),
    # a servo under it, so it doesn't share the Collar Cam's Thermal Overlay recipe
    'thermal_imaging': ('Thermal Imaging', 'Living things within 32 blocks glow.', 'Imagem Térmica', 'Seres vivos a até 32 blocos brilham.',
                        ['L', 'G', 'V'], dict(L='headinajar:lens', G='glow_ink_sac', V=V)),
    'ore_scanner': ('Ore Scanner', 'Ping: marks ores within 8 to 24 blocks for 5 s (Radius).', 'Scanner de Minério',
                    'Pulso: marca minérios a 8 a 24 blocos por 5 s (Raio).', ['L', 'C', 'R'], dict(L='headinajar:lens', C='compass', R='redstone_block')),
    # redstone under it, so it doesn't share the Bio-Scanner's recipe
    'target_lock': ('Target Lock', 'Your aim drifts onto hostiles near the crosshair.', 'Trava de Mira', 'Sua mira desliza para hostis perto da cruz.',
                    ['L', 'C', 'R'], dict(L='headinajar:lens', C=C, R='redstone')),
}

SLIDERS = {'boost': ('Boost', 'Impulso'), 'altitude': ('Altitude', 'Altitude'), 'warhead': ('Warhead', 'Ogiva'), 'output': ('Output', 'Saída'),
           'strength': ('Strength', 'Força'), 'threshold': ('Threshold', 'Limiar'), 'voltage': ('Voltage', 'Voltagem'), 'spread': ('Spread', 'Abertura'),
           'charge': ('Charge', 'Carga'), 'radius': ('Radius', 'Raio')}

JAR_MODULES = {
    'wheels': ('Wheels', 'Press the drive key (V) to drop four little wheels and drive at half walking speed. 10 FE/t while moving.', 'Rodinhas',
               'Aperte a tecla de dirigir (V) para baixar quatro rodinhas e andar à metade da velocidade de caminhada. 10 FE/t em movimento.',
               ['I I', 'WCW'], dict(I='iron_nugget', W='black_dye', C='copper_ingot')),
    'cortex_coprocessor': ('Cortex Coprocessor', 'Forgives one miscalled base at the DNA Sequencer; soft-locks your aim in a mecha.', 'Coprocessador Cortical',
                           'Perdoa uma base errada no Sequenciador de DNA; trava suavemente a mira num mecha.', ['GCG', 'RLR'],
                           dict(G='gold_nugget', C=C, R='redstone', L='headinajar:lens')),
    'lacrimal_laser': ('Lacrimal Laser', 'G: the head weeps a beam, 4 damage at 8 blocks, once a second (1,500 FE).', 'Laser Lacrimal',
                       'G: a cabeça chora um feixe, 4 de dano a 8 blocos, uma vez por segundo (1.500 FE).', [' L ', 'GCG'],
                       dict(L='headinajar:lens', G='ghast_tear', C='copper_ingot')),
    'body_recall_beacon': ('Body Recall Beacon', 'Your Headless Body walks to your head while its chunk is loaded.', 'Farol de Chamado do Corpo',
                           'Seu Corpo Sem Cabeça anda até sua cabeça enquanto o chunk estiver carregado.', [' B ', 'CWC'],
                           dict(B='bell', C='copper_ingot', W=W)),
}

UPGRADES = {
    'wide_lens': ('Wide Lens', 'A wider view through the feed.', 'Lente Grande-Angular', 'Visão mais ampla pela transmissão.', ['GLG'], dict(G='glass_pane', L='headinajar:lens')),
    'periscope_stalk': ('Periscope Stalk', 'Your body stands at full height again.', 'Haste de Periscópio', 'Seu corpo volta à altura normal.',
                        ['L', 'I', 'I'], dict(L='headinajar:lens', I='iron_bars')),
    'night_optics': ('Night Optics', 'Night vision through the feed.', 'Óptica Noturna', 'Visão noturna pela transmissão.', ['L', 'G'], dict(L='headinajar:lens', G='golden_carrot')),
    'waterproof_seal': ('Waterproof Seal', 'No signal loss under water.', 'Vedação à Prova d\'Água', 'Sem perda de sinal debaixo d\'água.', ['KSK'], dict(K='dried_kelp', S='slime_ball')),
    'shielded_housing': ('Shielded Housing', 'Immune to EMP.', 'Carcaça Blindada', 'Imune a PEM.', ['ILI'], dict(I='iron_ingot', L='lead')),
    'antenna_whip': ('Antenna Whip', '+25% link range.', 'Antena Chicote', '+25% de alcance da conexão.', ['R', 'C', 'C'], dict(R='lightning_rod', C='copper_ingot')),
    'noise_filter': ('Noise Filter', 'Halves the visual static.', 'Filtro de Ruído', 'Reduz a estática visual pela metade.', ['WAW'], dict(W='white_wool', A='amethyst_shard')),
    'thermal_overlay': ('Thermal Overlay', 'Living things nearby glow.', 'Sobreposição Térmica', 'Seres vivos por perto brilham.', ['L', 'G'], dict(L='headinajar:lens', G='glow_ink_sac')),
    'zoom_optic': ('Zoom Optic', 'Hold the zoom key (C) for x3 zoom.', 'Óptica de Zoom', 'Segure a tecla de zoom (C) para zoom x3.', ['LSL'], dict(L='headinajar:lens', S='spyglass')),
}

BLOCKS = {
    'turret_pedestal': ('Turret Pedestal', 'Pedestal de Torreta', ['BHB', 'SDS', 'SRS'], dict(B='gold_ingot', H='hopper', S=S, D='dispenser', R='redstone')),
    'employee_mount': ('Employee Mount', 'Suporte de Funcionário', ['S S', 'SIS', ' I '], dict(S=S, I='iron_ingot')),
    'cerebral_array': ('Cerebral Array', 'Matriz Cerebral', ['WCW', 'SJS', 'WCW'], dict(W=W, C=C, S=S, J='headinajar:life_support_jar')),
    'neural_socket': ('Neural Socket', 'Soquete Neural', ['RCR', 'WSW', 'RCR'], dict(R='redstone', C=C, W=W, S=S)),
}

ITEMS = {
    'board_room_core': ('Board Room Core', 'Núcleo da Sala de Reuniões', ['GCG', 'CNC', 'GCG'], dict(G='gold_block', C=C, N='nether_star')),
    'quantum_knot': ('Quantum Knot', 'Nó Quântico', ['EAE', 'ASA', 'EAE'], dict(E='ender_eye', A='echo_shard', S='headinajar:synapse_chip')),
    'executive_life_support_jar': ('Executive Life Support Jar', 'Pote de Suporte Vital Executivo', ['SKS', 'GJG'],
                                   dict(S='shulker_shell', K='headinajar:quantum_knot', G='gold_block', J='headinajar:reinforced_life_support_jar')),
    'quantum_goggles': ('Quantum Goggles', 'Óculos Quânticos', ['KGK'], dict(K='headinajar:quantum_knot', G='headinajar:relay_goggles_mk3')),
    'entangled_pair': ('Entangled Pair', 'Par Emaranhado', ['KEK'], dict(K='headinajar:quantum_knot', E='end_crystal')),
    'entangled_shard': ('Entangled Shard', 'Fragmento Emaranhado', None, None),
    'emp_grenade': ('EMP Grenade', 'Granada de PEM', [' I ', 'CRC', ' I '], dict(I='iron_ingot', C='copper_ingot', R='redstone_block')),
}

LANG = [
    ('entity.headinajar.missile', 'Micro-Missile', 'Micromíssil'),
    ('item.headinajar.plasma_pellet', 'Plasma Pellet', 'Pastilha de Plasma'),
    ('hud.headinajar.utility', '[%s] %s   [%s] next', '[%s] %s   [%s] próximo'),
    ('entity.headinajar.emp_grenade', 'EMP Grenade', 'Granada de PEM'),
    ('fluid_type.headinajar.nutrient_broth', 'Nutrient Broth', 'Caldo Nutritivo'),
    ('item.headinajar.nutrient_broth_bucket', 'Nutrient Broth Bucket', 'Balde de Caldo Nutritivo'),
    ('gene.headinajar.decay_aura', 'Decay Aura', 'Aura da Decadência'),
    ('item.headinajar.quantum_goggles.range', 'Range: anywhere in this dimension', 'Alcance: qualquer lugar nesta dimensão'),
    ('item.headinajar.entangled_shard.id', 'Entangled: %s', 'Emaranhado: %s'),
    ('item.headinajar.entangled_shard.how', 'Craft with Relay Goggles or a Collar Cam; give its twin to the other one', 'Combine com Óculos de Retransmissão ou uma Câmera de Colar; dê o gêmeo ao outro'),
    ('collar.headinajar.no_room', 'No Collar Cam to fit it to, or no room left (4 upgrades)', 'Nenhuma Câmera de Colar para encaixar, ou sem espaço (4 melhorias)'),
    ('collar.headinajar.installed', 'Fitted: %s', 'Instalado: %s'),
    ('collar.headinajar.how', 'Use it with a Collar Cam in your other hand, or while your collar is fitted', 'Use com uma Câmera de Colar na outra mão, ou com o colar instalado'),
    ('message.headinajar.emp', 'EMP! The link is down', 'PEM! A conexão caiu'),
    ('turret.headinajar.needs_head', 'Fit a jarred mob head', 'Encaixe uma cabeça de criatura no pote'),
    ('staff.headinajar.needs_villager', 'Only a jarred villager can work here', 'Só um aldeão no pote pode trabalhar aqui'),
    ('staff.headinajar.status_paid', '%s: on the job, paid up', '%s: trabalhando, salário em dia'),
    ('staff.headinajar.status_unpaid', '%s: unpaid (1 emerald a day from a chest touching the mount)', '%s: sem pagamento (1 esmeralda por dia de um baú encostado no suporte)'),
    ('staff.headinajar.says', '<%s> %s', '<%s> %s'),
    ('staff.headinajar.complaint.dental', 'I was promised dental.', 'Me prometeram plano odontológico.'),
    ('staff.headinajar.complaint.overtime', 'Is this overtime? Feels like overtime.', 'Isso é hora extra? Parece hora extra.'),
    ('staff.headinajar.complaint.jar', 'The jar is not an office.', 'O pote não é um escritório.'),
    ('staff.headinajar.complaint.union', 'I am talking to the union.', 'Vou falar com o sindicato.'),
    ('staff.headinajar.complaint.view', 'I had a window at my last job.', 'No meu último emprego eu tinha janela.'),
    ('staff.headinajar.complaint.smell', 'Who keeps microwaving fish?', 'Quem fica esquentando peixe no micro-ondas?'),
    ('staff.headinajar.complaint.pension', 'What about my pension?', 'E a minha aposentadoria?'),
    ('staff.headinajar.complaint.broth', 'The broth is cold again.', 'O caldo está frio de novo.'),
    ('staff.headinajar.complaint.chair', 'I do not even get a chair.', 'Eu nem ganho uma cadeira.'),
    ('staff.headinajar.complaint.manager', 'I would like to speak to the manager.', 'Quero falar com o gerente.'),
    ('staff.headinajar.nitwit.0', 'Hrm.', 'Hum.'),
    ('staff.headinajar.nitwit.1', 'Is it lunch?', 'Já é almoço?'),
    ('staff.headinajar.nitwit.2', 'I pressed the button. Which button?', 'Apertei o botão. Que botão?'),
    ('staff.headinajar.nitwit.3', 'Hrrmm hrm.', 'Hrrmm hum.'),
    ('array.headinajar.vote', '[Board Meeting] Motion: %s. Carried %s to %s.', '[Reunião do Conselho] Moção: %s. Aprovada por %s a %s.'),
    ('array.headinajar.motion.broth', 'warmer broth', 'caldo mais quente'),
    ('array.headinajar.motion.lunch', 'a longer lunch', 'almoço mais longo'),
    ('array.headinajar.motion.windows', 'windows for the jars', 'janelas para os potes'),
    ('array.headinajar.motion.chair', 'a chair for the chair', 'uma cadeira para o presidente'),
    ('array.headinajar.motion.union', 'unionise', 'formar um sindicato'),
    ('array.headinajar.motion.dental', 'dental cover', 'plano odontológico'),
    ('array.headinajar.motion.merger', 'merge with the Board of the other jar', 'fundir com o Conselho do outro pote'),
    ('array.headinajar.motion.picnic', 'a team picnic', 'um piquenique da equipe'),
    ('gui.headinajar.broth', 'Broth: %s / %s mB', 'Caldo: %s / %s mB'),
    ('gui.headinajar.array.output', '%s FE/t', '%s FE/t'),
    ('gui.headinajar.array.heads', '%s heads', '%s cabeças'),
    ('socket.headinajar.plugged', 'Plugged in. Scroll: face, 0-9: level, %s: pulse, %s + %s: unplug', 'Conectado. Rolar: face, 0-9: nível, %s: pulso, %s + %s: desconectar'),
    ('hud.headinajar.socket', 'NEURAL SOCKET', 'SOQUETE NEURAL'),
    ('hud.headinajar.socket.face', '%s  out %s  in %s', '%s  saída %s  entrada %s'),
    ('hud.headinajar.socket.items', 'Items touching: %s', 'Itens encostados: %s'),
    ('hud.headinajar.socket.tank', 'Tank: %s / %s mB', 'Tanque: %s / %s mB'),
    ('hud.headinajar.socket.keys', 'Scroll face, 0-9 level, %s pulse, %s next', 'Rolar face, 0-9 nível, %s pulso, %s próxima'),
    ('direction.headinajar.down', 'Down ', 'Baixo'),
    ('direction.headinajar.up', 'Up   ', 'Cima '),
    ('direction.headinajar.north', 'North', 'Norte'),
    ('direction.headinajar.south', 'South', 'Sul  '),
    ('direction.headinajar.west', 'West ', 'Oeste'),
    ('direction.headinajar.east', 'East ', 'Leste'),
    ('key.headinajar.zoom', 'Collar zoom', 'Zoom do colar'),
]


def run(EN, PT, write, shaped):
    for name, (en, pt, neck, pattern, key) in FRAMES.items():
        EN[f'item.headinajar.{name}_frame'], PT[f'item.headinajar.{name}_frame'] = en + ' Frame', 'Estrutura ' + pt
        EN[f'entity.headinajar.{name}'], PT[f'entity.headinajar.{name}'] = en, pt
        write(f'data/headinajar/headinajar/host/{name}.json', {'entity': f'headinajar:{name}', 'neck': [0, neck, 0], 'blood': 'oil', 'decay_per_minute': 0})
        shaped(f'{name}_frame', pattern, key)
        write(f'assets/headinajar/models/item/{name}_frame.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'headinajar:item/{name}_frame'}})
    write('data/headinajar/headinajar/host/chimera.json', {'entity': 'headinajar:chimera', 'neck': [0, CHIMERA_NECK, 0], 'blood': 'warm', 'decay_per_minute': 0})
    for name, (en, en_desc, pt, pt_desc, pattern, key) in MODULES.items():
        shaped('mecha_' + name, pattern, key)
        EN[f'item.headinajar.mecha_{name}'], PT[f'item.headinajar.mecha_{name}'] = en, pt
        EN[f'mecha_module.headinajar.{name}.desc'], PT[f'mecha_module.headinajar.{name}.desc'] = en_desc, pt_desc
        write(f'assets/headinajar/models/item/mecha_{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'headinajar:item/mecha_{name}'}})
    for key, (en, pt) in SLIDERS.items():
        EN[f'mecha_slider.headinajar.{key}'], PT[f'mecha_slider.headinajar.{key}'] = en, pt
    shaped('plasma_pellet', ['BEG'], dict(B='blaze_rod', E='ender_pearl', G='headinajar:bio_gel'))
    write('assets/headinajar/models/item/plasma_pellet.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'headinajar:item/plasma_pellet'}})
    for key, en, pt in LANG:
        EN[key], PT[key] = en, pt
    for name, (en, en_desc, pt, pt_desc, pattern, key) in JAR_MODULES.items():
        EN[f'item.headinajar.{name}'], EN[f'module.headinajar.{name}.desc'] = en, en_desc
        PT[f'item.headinajar.{name}'], PT[f'module.headinajar.{name}.desc'] = pt, pt_desc
        shaped(name, pattern, key)
        write(f'assets/headinajar/models/item/{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'headinajar:item/{name}'}})
    for name, (en, en_desc, pt, pt_desc, pattern, key) in UPGRADES.items():
        EN[f'item.headinajar.collar_{name}'], EN[f'item.headinajar.collar_{name}.desc'] = en, en_desc
        PT[f'item.headinajar.collar_{name}'], PT[f'item.headinajar.collar_{name}.desc'] = pt, pt_desc
        shaped(f'collar_{name}', pattern, key)
        write(f'assets/headinajar/models/item/collar_{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'headinajar:item/collar_{name}'}})
    for name, (en, pt, pattern, key) in ITEMS.items():
        EN[f'item.headinajar.{name}'], PT[f'item.headinajar.{name}'] = en, pt
        if pattern:
            shaped(name, pattern, key)
        write(f'assets/headinajar/models/item/{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'headinajar:item/{name}'}})
    write('assets/headinajar/models/item/nutrient_broth_bucket.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'headinajar:item/nutrient_broth_bucket'}})
    write('data/headinajar/recipe/entangle.json', {'type': 'headinajar:entangle', 'category': 'equipment'})
    write('data/headinajar/recipe/nutrient_broth_bucket.json', {
        'type': 'minecraft:crafting_shapeless', 'category': 'misc',
        'ingredients': [{'item': 'minecraft:water_bucket'}, {'item': 'minecraft:beetroot_soup'}, {'item': 'minecraft:mushroom_stew'}, {'item': 'minecraft:sugar'}],
        'result': {'id': 'headinajar:nutrient_broth_bucket', 'count': 1}})

    rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}

    def el(f, t, tex):
        return {'from': f, 'to': t, 'faces': {d: {'texture': tex} for d in ('north', 'south', 'east', 'west', 'up', 'down')}}
    models = {
        'turret_pedestal': {'parent': 'minecraft:block/block', 'ambientocclusion': False,
                            'textures': {'particle': 'headinajar:block/turret_base', 'base': 'headinajar:block/turret_base', 'ring': 'headinajar:block/turret_ring'},
                            'elements': [el([2, 0, 2], [14, 4, 14], '#base'), el([5, 4, 5], [11, 8, 11], '#base'), el([3, 8, 3], [13, 10, 13], '#ring')]},
        'employee_mount': {'parent': 'minecraft:block/block', 'ambientocclusion': False,
                           'textures': {'particle': 'headinajar:block/turret_ring', 'plate': 'headinajar:block/mount_plate', 'ring': 'headinajar:block/turret_ring'},
                           'elements': [el([3, 0, 14], [13, 12, 16], '#plate'), el([6, 4, 9], [10, 6, 14], '#ring'), el([4, 10, 4], [12, 12, 12], '#ring')]},
        'neural_socket': {'parent': 'minecraft:block/cube_bottom_top',
                          'textures': {'particle': 'headinajar:block/socket_side', 'side': 'headinajar:block/socket_side', 'top': 'headinajar:block/socket_top',
                                       'bottom': 'headinajar:block/lab_side'}},
        'cerebral_array': {'parent': 'minecraft:block/orientable',
                           'textures': {'particle': 'headinajar:block/lab_side', 'side': 'headinajar:block/lab_side', 'top': 'headinajar:block/array_top',
                                        'front': 'headinajar:block/array_front'}},
    }
    for name, model in models.items():
        write(f'assets/headinajar/models/block/{name}.json', model)
        if name == 'neural_socket':
            write(f'assets/headinajar/blockstates/{name}.json', {'variants': {f'facing={d}': {'model': f'headinajar:block/{name}'} for d in rot}})
        else:
            write(f'assets/headinajar/blockstates/{name}.json', {'variants': {f'facing={d}': {'model': f'headinajar:block/{name}', 'y': r} for d, r in rot.items()}})
        write(f'assets/headinajar/models/item/{name}.json', {'parent': f'headinajar:block/{name}'})
        write(f'data/headinajar/loot_table/blocks/{name}.json', {
            'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'headinajar:{name}'}],
                                                 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    for name, (en, pt, pattern, key) in BLOCKS.items():
        EN[f'block.headinajar.{name}'], PT[f'block.headinajar.{name}'] = en, pt
        shaped(name, pattern, key)
    import json
    import os
    res = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources')
    for tag, extra in (('data/headinajar/tags/block/head_operable.json', ['headinajar:neural_socket', 'headinajar:cerebral_array', 'headinajar:turret_pedestal',
                                                                           'headinajar:employee_mount']),
                       ('data/minecraft/tags/block/mineable/pickaxe.json', ['headinajar:' + b for b in BLOCKS])):
        path = os.path.join(res, tag)
        values = json.load(open(path, encoding='utf-8'))['values'] if os.path.exists(path) else []
        write(tag, {'values': values + [v for v in extra if v not in values]})

    # the Chimera's recipe moves to the design's tier: Bio-Gel grown, a Bulwark-class servo frame
    shaped('chimera_frame', ['GCG', 'PNP', 'V V'], dict(G='headinajar:bio_gel', C='headinajar:collar_cam', P=P, N='nether_wart_block', V=V))

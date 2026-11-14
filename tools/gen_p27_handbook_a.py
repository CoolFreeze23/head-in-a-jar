"""The Employee Handbook, Second Edition: chapters 1 to 5 (Orientation, Facilities, Life Support, Separation, Life as
a Jar), in both languages. Text only: gen_p27_clarity.py turns it into the Patchouli book, the written edition and the
JEI information pages. Every number here is the code's.
"""

CATEGORIES = [  # (id, sortnum, icon, en name, en description, pt name, pt description)
    ('orientation', 1, 'minecraft:book', 'Orientation',
     'Welcome to the company. Please keep your head.',
     'Integração',
     'Bem-vindo à empresa. Por favor, mantenha sua cabeça.'),
    ('facilities', 2, 'headinajar:galvanic_pile', 'Facilities',
     'Power: where it comes from and how it gets to the machines.',
     'Instalações',
     'Energia: de onde vem e como chega às máquinas.'),
    ('life_support', 3, 'headinajar:saline_bottle', 'Life Support',
     'Saline, glass and spare bodies: keeping a head alive.',
     'Suporte Vital',
     'Soro, vidro e corpos de reserva: mantendo uma cabeça viva.'),
    ('separation', 4, 'headinajar:separation_chair', 'Separation',
     'The chair, the link, and what to do when it goes wrong.',
     'Separação',
     'A cadeira, a conexão e o que fazer quando dá errado.'),
    ('jar', 5, 'headinajar:life_support_jar', 'Life as a Jar',
     'No hands, many modules, one charge bar.',
     'A Vida num Pote',
     'Sem mãos, muitos módulos, uma barra de carga.'),
]

ENTRIES = [
    # ------------------------------------------------------------------------------------------- 1. Orientation
    {'cat': 'orientation', 'id': 'welcome', 'sort': 0, 'icon': 'headinajar:life_support_jar',
     'en': 'Welcome', 'pt': 'Boas-vindas',
     'pages': [
         ('text',
          'Welcome to $(l)Head Office$(). Noggin & Sons takes your head off, keeps it alive in a jar and lets '
          'your body walk on without it.$(br2)This handbook is issued on your first day. Lost it? A book and a '
          '$(item)Surgical Steel Blend$() make another.',
          'Bem-vindo à $(l)Sede$(). A Noggin & Sons tira sua cabeça, a mantém viva num pote e deixa seu '
          'corpo seguir sem ela.$(br2)Perdeu este manual? Um livro e uma '
          '$(item)Mistura de Aço Cirúrgico$() fazem outro.'),
         ('form', ('Your First Day', 'Seu Primeiro Dia'),
          '$(li)Smelt Surgical Steel.'
          '$(li)Build a Galvanic Pile; fuel it.'
          '$(li)Make 4 Cranial Saline.'
          '$(li)Build and load the Separation Chair.'
          '$(li)Sit, sign, collect your head.'
          '$(br2)The Shopping List says what to bring.',
          '$(li)Funda Aço Cirúrgico.'
          '$(li)Monte uma Pilha Galvânica e alimente-a.'
          '$(li)Faça 4 Soros.'
          '$(li)Monte e carregue a Cadeira de Separação.'
          '$(li)Sente, assine, pegue a cabeça.'
          '$(br)A Lista de Compras diz o que trazer.'),
     ]},

    {'cat': 'orientation', 'id': 'shopping_list', 'sort': 1, 'icon': 'headinajar:surgical_steel_ingot',
     'en': 'Shopping List', 'pt': 'Lista de Compras',
     'pages': [
         ('form', ('Form 1-A', 'Formulário 1-A'),
          'One operation, satchel included: 29 iron, 24 bone meal, 24 coal, 14 copper, 21 redstone, 19 glass, '
          '4 amethyst shards, 8 leather, 2 string, 3 white wool, 1 slime ball, 2 kelp, 2 sugar, 11 rotten '
          'flesh, wood and stone.',
          'Uma operação, com a bolsa: 29 de ferro, 24 de farinha de osso, 24 de carvão, 14 de cobre, 21 de '
          'redstone, 19 de vidro, 4 ametistas, 8 de couro, 2 de linha, 3 de lã branca, 1 bola de slime, 2 de '
          'alga, 2 de açúcar, 11 de carne podre, madeira e pedra.'),
         ('text',
          'Far from home:$(li)amethyst: a geode$(li)slime ball: a swamp$(li)kelp: the sea$(br2)No Nether trip '
          'is needed. Ten of the rotten flesh are fuel for the chair\'s 40 kFE.',
          'Longe de casa:$(li)ametista: um geodo$(li)bola de slime: um pântano$(li)alga: o mar$(br2)Não precisa '
          'ir ao Nether. Dez das carnes podres são combustível para os 40 kFE da cadeira.'),
         ('smelting', 'headinajar:surgical_steel_ingot_from_smelting',
          '24 ingots, each one Blend: iron, bone meal, coal.',
          '24 lingotes. Mistura: ferro, farinha de osso e carvão.'),
     ],
     'note': ('DOLORES notes: receipts in triplicate.', 'DOLORES observa: recibos em três vias.')},

    {'cat': 'orientation', 'id': 'how_to_read', 'sort': 2, 'icon': 'minecraft:spyglass',
     'en': 'How to Read', 'pt': 'Como Ler',
     'pages': [
         ('text',
          'Keys show as you bound them: the jar menu is $(k:key.headinajar.jar_menu).$(br2)JEI shows recipes; '
          'Jade reads bodies, hosts and mecha. Use a pile or power block empty-handed to see its '
          'charge.$(br2)Advancements are your to-do list.',
          'As teclas aparecem como você as configurou: o menu do pote é $(k:key.headinajar.jar_menu).$(br2)'
          'JEI: receitas. Jade: corpos, hospedeiros e mechas. Pilha ou bloco de energia usado de mão '
          'vazia mostra a carga.$(br)Conquistas: suas tarefas.'),
         ('text', 'Two controls on one key? Another mod may share it: the key shows in red on the Options, Controls screen. Rebind one of the two there. The ability key and sprint may share a key on purpose; the game says so the first time.',
          'Dois controles numa tecla? Outro mod pode usá-la também: a tecla aparece em vermelho na tela de controles, nas Opções. Troque um dos dois ali. A tecla de habilidade e a de correr podem ser a mesma de propósito; o jogo avisa na primeira vez.'),
     ]},

    {'cat': 'orientation', 'id': 'units', 'sort': 3, 'icon': 'minecraft:clock',
     'en': 'Units', 'pt': 'Unidades',
     'pages': [
         ('text',
          '$(l)FE$(): energy.$(br)$(l)kFE$(): 1,000 FE.$(br)$(l)FE/t$(): FE per tick; 20 ticks make a '
          'second.$(br)$(l)mB$(): a thousandth of a bucket. A bottle of saline is 250 mB.$(br2)All times are '
          'real time.',
          '$(l)FE$(): energia.$(br)$(l)kFE$(): 1.000 FE.$(br)$(l)FE/t$(): FE por tique (20 por segundo).'
          '$(br)$(l)mB$(): milésimo de balde; um frasco de soro tem 250 mB.$(br2)Os tempos são reais.'),
     ],
     'note': ('DOLORES notes: one head per employee.', 'DOLORES observa: uma cabeça por pessoa.')},

    # ------------------------------------------------------------------------------------------- 2. Facilities
    {'cat': 'facilities', 'id': 'galvanic_pile', 'sort': 0, 'icon': 'headinajar:galvanic_pile',
     'en': 'The Galvanic Pile', 'pt': 'A Pilha Galvânica',
     'pages': [
         ('crafting', 'headinajar:galvanic_pile',
          'The first generator: 20 FE/t, stores 50 kFE.',
          'O primeiro gerador: 20 FE/t, guarda 50 kFE.'),
         ('text',
          'Use fuel on it, or hopper it in. FE per item:$(li)rotten flesh 4,000$(li)spider eye 6,000$(li)raw '
          'meat or fish 3,000$(br2)Cooked food will not burn. It powers any machine touching it: 10 rotten flesh '
          'give the chair its 40 kFE in 100 s.',
          'Use combustível nela, ou por funil: carne podre dá 4.000 FE, olho de aranha 6.000, carne ou peixe '
          'cru 3.000. Comida cozida não queima.$(br2)Abastece toda máquina encostada: 10 carnes podres dão os '
          '40 kFE da cadeira em 100 s.'),
     ]},

    {'cat': 'facilities', 'id': 'sharing_power', 'sort': 1, 'icon': 'headinajar:leyden_jar',
     'en': 'Sharing Power', 'pt': 'Dividindo a Energia',
     'pages': [
         ('text',
          'Power flows from a Galvanic Pile, Leyden Jar or Tesla Node into every machine touching any face, '
          'shared evenly. Machines do not pass it on: put the source against the machine. FE cables from '
          'other mods work too.',
          'A energia sai de uma Pilha Galvânica, Garrafa de Leyden ou Nó de Tesla para toda máquina '
          'encostada, em partes iguais. Máquinas não repassam: encoste a fonte na máquina. Cabos de FE de '
          'outros mods também servem.'),
         ('text',
          'A $(item)Leyden Jar$() stores 250 kFE. It fills from a source beside it and feeds its other '
          'neighbours, 2,000 FE/t; two do not feed each other.$(br2)A $(item)Tesla Node$() sends up to 2,000 '
          'FE/t to a node within 16 blocks, losing 5%.',
          'A $(item)Garrafa de Leyden$() guarda 250 kFE. Enche com uma fonte ao lado e alimenta os outros '
          'vizinhos, 2.000 FE/t. Duas Garrafas não se alimentam.$(br2)O $(item)Nó de Tesla$() lança até 2.000 FE/t '
          'a um nó a até 16 blocos, perdendo 5%.'),
         ('text',
          'To link two nodes, hold a $(item)Surgical Wrench$(): use the sending node, then the receiving '
          'one. Sneak and use the sender to unlink.$(br2)The source goes against the sender; the receiver '
          'feeds what touches it.',
          'Para ligar dois nós, segure a $(item)Chave Cirúrgica$(): use o nó que envia, depois o que recebe. '
          'Agache e use o que envia para desligar.$(br2)A fonte encosta no que envia; o que recebe alimenta o '
          'que toca nele.'),
     ]},

    {'cat': 'facilities', 'id': 'charging_dock', 'sort': 2, 'icon': 'headinajar:charging_dock',
     'en': 'The Charging Dock', 'pt': 'A Base de Recarga',
     'pages': [
         ('crafting', 'headinajar:charging_dock',
          'Charges a jar standing on it, 500 FE/t, from the 20 kFE it holds.',
          'Carrega o pote em cima dela, 500 FE/t, dos 20 kFE que guarda.'),
         ('text',
          'In your body, set your head on it from the Head Satchel: it charges there too.$(br2)Feed it from a '
          'source beside it. A pile alone gives 20 FE/t; a Leyden Jar keeps it stocked.',
          'No corpo, pouse a cabeça nela pela Bolsa de Cabeça: ali ela também carrega.$(br2)Alimente com uma '
          'fonte ao lado. Uma pilha sozinha dá só 20 FE/t; uma Garrafa de Leyden mantém o estoque.'),
     ],
     'note': ('DOLORES notes: wipe your jar first.', 'DOLORES observa: limpe o pote antes.')},

    # ------------------------------------------------------------------------------------------- 3. Life Support
    {'cat': 'life_support', 'id': 'saline', 'sort': 0, 'icon': 'headinajar:saline_bottle',
     'en': 'Cranial Saline', 'pt': 'Soro Craniano',
     'pages': [
         ('crafting', 'headinajar:saline_bottle',
          'Made by hand, two bottles of 250 mB each. The chair wants four.',
          'Feito à mão: dois frascos de 250 mB cada. A cadeira pede quatro.'),
         ('text',
          'A jar holds 1,000 mB. Your head uses 1 mB per 12 s while you are the jar or it is set down: 3 h 20 '
          'min a full jar. In the Head Satchel or a Cradle, none.$(br2)Top up: use a bottle on your placed '
          'head, or the jar menu\'s feed slot.',
          'Um pote guarda 1.000 mB. A cabeça gasta 1 mB a cada 12 s quando você é o pote ou ela está pousada: '
          '3 h 20 min o pote cheio. Levada na bolsa ou no Berço, nada.$(br2)Reponha: frasco na cabeça '
          'pousada, ou no espaço de recarga.'),
         ('text',
          'The $(item)Saline Mixer$(): 1,000 mB of water, a sugar, a kelp, a redstone and 2,000 FE make 1,000 mB '
          'in 10 s. Water by bucket or pipe.$(br2)It fills glass bottles in its slot. An empty bucket takes a '
          'bucketful, for Cradle and Printer.',
          'O $(item)Misturador de Soro$(): 1.000 mB de água, um açúcar, uma alga, uma redstone e 2.000 FE dão '
          '1.000 mB em 10 s. Água por balde ou cano.$(br2)Enche garrafas no espaço. Um balde vazio tira um '
          'balde cheio, para Berço e Impressora.'),
     ]},

    {'cat': 'life_support', 'id': 'integrity', 'sort': 1, 'icon': 'headinajar:glass_patch',
     'en': 'Jar Integrity', 'pt': 'Integridade do Pote',
     'pages': [
         ('text',
          'Integrity is the glass: 10 points. As the jar, damage takes glass first, then health. A placed head '
          'is only glass: at 0 it breaks. Players cannot harm it; fire and lava crack a point a second.$(br)No '
          'saline: a point lost per 30 s.',
          'Integridade é o vidro: 10 pontos. Sendo o pote, o dano tira vidro antes da vida. A cabeça pousada '
          'é só vidro: em 0, quebra. Jogadores não a ferem; fogo e lava tiram um ponto por segundo.$(br)Sem '
          'soro, perde um ponto a cada 30 s.'),
         ('crafting', 'headinajar:glass_patch',
          'Mends 5 points: use it on your placed head, or the feed slot.',
          'Conserta 5 pontos: use na cabeça pousada, ou no espaço de recarga.'),
     ]},

    {'cat': 'life_support', 'id': 'cradle', 'sort': 2, 'icon': 'headinajar:life_support_cradle',
     'en': 'The Cradle', 'pt': 'O Berço',
     'pages': [
         ('crafting', 'headinajar:life_support_cradle',
          'A head in it uses no saline. It does not extend the link. Fill it by bottle or bucket.',
          'No Berço a cabeça não gasta soro e o alcance não aumenta. Encha com frasco ou balde.'),
         ('text',
          'Each second it refills a docked head\'s saline, moves up to 10 kFE of charge and uses 100 FE; every '
          '10 s it mends a point of glass.$(br)To dock, use it with your head in the Head Satchel; again to '
          'take it out. As the jar: use it, then Swap.',
          'Com sua cabeça, a cada segundo ele repõe o soro, passa até 10 kFE de carga e gasta 100 FE; a cada '
          '10 s conserta um ponto de vidro.$(br2)Para acomodar: com a cabeça na bolsa, use o Berço; de novo '
          'para tirar. Sendo o pote: use e Troque.'),
     ]},

    {'cat': 'life_support', 'id': 'body_printer', 'sort': 3, 'icon': 'headinajar:body_printer',
     'en': 'The Body Printer', 'pt': 'A Impressora',
     'pages': [
         ('spotlight', 'headinajar:body_printer',
          'A new body, Collar Cam fitted, in 60 s, for 16 rotten flesh, 8 bone blocks, 4 redstone blocks, 8 '
          'slime balls, 2,000 mB of saline and 200 kFE stored first.',
          'Um corpo novo, com câmera, em 60 s, por 16 carnes podres, 8 blocos de osso, 4 blocos de redstone, '
          '8 bolas de slime, 2.000 mB de soro e 200 kFE guardados antes.'),
         ('text',
          '$(l)Load it before you need it:$() when you do, you are a jar and your things are on your corpse. '
          'Saline goes in by bucket or bottle. From a pile alone, 200 kFE take over 8 min.$(br2)Then, as the jar, '
          'press Print. Use the new body, or Swap, to step in.',
          '$(l)Carregue antes de precisar:$() aí você já será um pote, e suas coisas estarão no cadáver. Soro '
          'por balde ou frasco. Só com a pilha, 200 kFE levam mais de 8 min.$(br)Depois, como pote, aperte Imprimir. '
          'Para entrar, use o corpo novo ou Troque.'),
     ]},

    # ------------------------------------------------------------------------------------------- 4. Separation
    {'cat': 'separation', 'id': 'the_chair', 'sort': 0, 'icon': 'headinajar:separation_chair',
     'en': 'The Chair', 'pt': 'A Cadeira',
     'pages': [
         ('crafting', 'headinajar:separation_chair',
          'The tray slides out to its left. Keep that side, the front and the top clear.',
          'A bandeja sai pela esquerda. Deixe esse lado, a frente e o alto livres.'),
         ('form', ('Form 27-B', 'Formulário 27-B'),
          '$(li)a Life Support Jar$(li)Relay Goggles$(li)a Collar Cam$(li)4 Cranial Saline$(li)40 '
          'kFE, from a pile or Leyden Jar touching it$(br2)Load them in its panel; sneak and use the chair to '
          'open it without sitting.',
          '$(li)Pote de Suporte Vital$(li)Óculos Retransmissores$(li)Câmera de Colar$(li)4 frascos de '
          'Soro Craniano$(li)40 kFE: pilha ou Garrafa encostada$(br2)Coloque no painel (agache e use para abrir sem '
          'sentar).'),
         ('text',
          'Use it to sit; hold Sign for 2 s. It takes 12 s; in the first 5 you may get up, keeping the parts, '
          'losing 10 kFE.$(br2)Your head goes on the tray (or an empty Cradle within 3 blocks). To carry it, '
          'use it with a Head Satchel in your inventory.',
          'Use para sentar; segure Assinar por 2 s. A operação leva 12 s. Nos primeiros 5 dá para levantar: '
          'peças ficam, 10 kFE se vão.$(br)A cabeça vai à bandeja (ou a um Berço vazio a até 3 blocos). '
          'Use-a com a Bolsa de Cabeça no inventário para levá-la.'),
         ('crafting', 'headinajar:relay_goggles',
          'Range 48 blocks; Mk II 160, Mk III 640. Use new ones on your placed or docked head.',
          'Alcance 48 blocos; Mk II 160, Mk III 640. Para trocar, use na cabeça (no chão ou no Berço).'),
         ('crafting', 'headinajar:collar_cam',
          'Bolted to your neck: your body sees through it.',
          'Parafusada no pescoço: o corpo enxerga por ela.'),
     ]},

    {'cat': 'separation', 'id': 'the_botch', 'sort': 1, 'icon': 'headinajar:replacement_blade',
     'en': 'The Botch', 'pt': 'O Erro e a Lâmina',
     'pages': [
         ('incident', ('Incident 0017: Oopsie', 'Incidente 0017: Opa'),
          'Each operation adds 5% blade wear. Over 80%, a cut may botch: 10% at 85, 40% at 100.$(br)A botch '
          'flips your view for 30 s and bends the Collar Cam: -10% range, for good.',
          'Cada operação soma 5% de desgaste. Acima de 80%, o corte pode errar: 10% a 85, 40% a 100.$(br2)O '
          'erro vira sua visão por 30 s e entorta a câmera: -10% de alcance, para sempre.'),
         ('crafting', 'headinajar:replacement_blade',
          'Resets an idle chair\'s wear to 0.',
          'Zera o desgaste da cadeira parada.'),
     ],
     'note': ('DOLORES notes: so shiny.', 'DOLORES observa: tão brilhante.')},

    {'cat': 'separation', 'id': 'the_link', 'sort': 2, 'icon': 'headinajar:relay_goggles',
     'en': 'The Neuro-Link', 'pt': 'A Conexão Neural',
     'pages': [
         ('text',
          'Linked, you walk your body and see through the Collar Cam. The bar shows LINK %, delay, which end '
          'you are and the distance.$(br2)Signal is full to half the goggles\' range, 0 at the range. Your '
          'head in the Head Satchel gives 100%.',
          'Conectado, você anda no corpo e enxerga pela câmera. A barra mostra LINK %, atraso, qual ponta '
          'você é e a distância.$(br2)O sinal é pleno até metade do alcance dos óculos, 0 no limite. Com a cabeça '
          'na bolsa: 100%.'),
         ('text',
          'Water, deep rock, thunder, portals and Darkness weaken it. Under 20%, clicks get lost; 5 s at 0% '
          'and you wake in your head.$(br2)The swap key, $(k:key.headinajar.swap), takes your body to head '
          'always, but head to body only over 20%.',
          'Água, rocha funda, trovoada, portais e Escuridão enfraquecem. Abaixo de 20%, cliques se perdem; 5 '
          's em 0% e você acorda na cabeça.$(br)A tecla de troca, $(k:key.headinajar.swap), leva do corpo à '
          'cabeça sempre, mas de volta só acima de 20%.'),
     ]},

    {'cat': 'separation', 'id': 'when_it_goes_wrong', 'sort': 3, 'icon': 'headinajar:glass_patch',
     'en': 'When It Goes Wrong', 'pt': 'Quando Dá Errado',
     'pages': [
         ('memo', ('Drying Out', 'Sem Soro'),
          'Out of saline, a placed head loses 1 glass per 30 s; at 0 glass it is a real death. '
          'A full jar lasts 3 h 20 min, then 5 min of warnings.',
          'Sem soro, a cabeça pousada perde 1 de vidro a cada 30 s; sem vidro, é morte de verdade. '
          'Pote cheio: 3 h 20 min, e mais 5 min de avisos.'),
         ('text',
          '$(l)Not a death:$() losing the link, or your body dying (a corpse keeps your things).$(br2)$(l)Real '
          'death:$() your placed head breaking or drying while you walk, or dying as the jar. Modules drop; you '
          'respawn with a head.',
          '$(l)Não é morte:$() perder a conexão, ou o corpo morrer (o cadáver guarda suas coisas).$(br2)'
          '$(l)Morte de verdade:$() a cabeça pousada quebrar ou secar enquanto você anda, ou morrer sendo o '
          'pote. Módulos caem; você renasce com cabeça.'),
     ]},

    # ------------------------------------------------------------------------------------------- 5. Life as a Jar
    {'cat': 'jar', 'id': 'no_hands', 'sort': 0, 'icon': 'headinajar:manipulator_arm',
     'en': 'You Have No Hands', 'pt': 'Você Não Tem Mãos',
     'pages': [
         ('memo', ('Hands', 'Mãos'),
          'Without a Manipulator Arm a jar cannot mine, place, fight, use items or pick things up. Doors and '
          'chests do not answer.',
          'Sem Braço Manipulador o pote não minera, não coloca, não luta, não usa itens nem pega nada. Portas '
          'e baús não respondem.'),
         ('text',
          'A bare jar can still:$(li)use every Noggin & Sons machine$(li)step back into its own body$(li)board '
          'a boat or minecart$(li)ride a host$(li)use its keys and modules$(li)pick up with a Magnet',
          'Um pote sem braço ainda pode:$(li)usar as máquinas da Noggin & Sons$(li)voltar ao próprio '
          'corpo$(li)entrar em barco ou carrinho$(li)montar num hospedeiro$(li)usar teclas e módulos$(li)pegar '
          'itens com Ímã'),
         ('crafting', 'headinajar:manipulator_arm',
          'Hands: half speed, half damage, 200 FE a block or hit.',
          'Mãos: meia velocidade, meio dano, 200 FE por bloco ou golpe.'),
     ]},

    {'cat': 'jar', 'id': 'jar_menu', 'sort': 1, 'icon': 'headinajar:compact_life_support_jar',
     'en': 'The Jar Menu', 'pt': 'O Menu do Pote',
     'pages': [
         ('slot', ('jar_menu', 'headinajar:life_support_jar'),
          'As the jar, press $(k:key.headinajar.jar_menu). Modules go in the top row, one of each. The feed '
          'slot takes redstone, saline and Glass Patches.',
          'Como pote, aperte $(k:key.headinajar.jar_menu). Módulos na fileira de cima, um de cada. O espaço '
          'de recarga aceita redstone, soro e Remendos de Vidro.'),
     ]},

    {'cat': 'jar', 'id': 'modules', 'sort': 2, 'icon': 'headinajar:propeller',
     'en': 'Modules by Group', 'pt': 'Módulos por Grupo',
     'pages': [
         ('text',
          '$(l)Moving$()$(li)Hop Springs: 60% speed, 100 FE a hop$(li)Spider Legs: 80%, steps up$(li)Suction '
          'Cups: climb walls$(li)Propeller: fly, 25 FE/t$(li)Water Jet: swim, 50 FE/t',
          '$(l)Movimento$()$(li)Molas Saltadoras: 60%, 100 FE por pulo$(li)Pernas de Aranha: 80%, sobem '
          'degraus$(li)Ventosas: escalam paredes$(li)Hélice: voa, 25 FE/t$(li)Jato d\'Água: permite nadar, 50 FE/t'),
         ('text',
          '$(l)Wheels$(): $(k:key.headinajar.drive) drops them; half speed, 10 FE/t moving.$(br2)Without '
          'Wheels the same key tips the jar on its side to roll, very slowly, for free.',
          '$(l)Rodinhas$(): $(k:key.headinajar.drive) baixa; metade da velocidade, 10 FE/t andando.$(br2)Sem '
          'Rodinhas, a mesma tecla tomba o pote para rolar, bem devagar, de graça.'),
         ('text',
          '$(l)Reach$()$(li)Drill Bit: the arm mines 6x faster$(li)Magnet: pulls items in 6 blocks$(li)'
          'Telekinesis Module: $(k:key.headinajar.ability) grabs (500 FE) and holds (50 FE/t); attack throws '
          '(2,000 FE). No arm needed.',
          '$(l)Alcance$()$(li)Broca: o braço minera 6x mais rápido$(li)Ímã: puxa itens a 6 blocos$(li)'
          'Módulo de Telecinese: $(k:key.headinajar.ability) agarra (500 FE) e segura (50 FE/t); atacar arremessa '
          '(2.000 FE). Não precisa de braço.'),
         ('text',
          '$(l)Defence$()$(li)Armour Plating: +6 armour$(li)Heat Shield: no fire harm, 100 FE/t$(li)Shock '
          'Lid: zaps hitters, 500 FE$(li)Self-Righting Gyro: no knockback$(li)Lacrimal Laser: '
          '$(k:key.headinajar.secondary), 4 damage, 1,500 FE',
          '$(l)Defesa$()$(li)Blindagem: +6 armadura$(li)Escudo Térmico: sem dano de fogo, 100 '
          'FE/t$(li)Tampa de Choque: dá choque, 500 FE$(li)Giroscópio Autonivelante: sem repulsão$(li)Laser Lacrimal: '
          '$(k:key.headinajar.secondary), 4 de dano, 1.500 FE'),
         ('text',
          '$(l)Upkeep$()$(li)Hub Antenna: +50% range$(li)Saline Recycler: half saline$(li)Body Recall Beacon: '
          'body walks to you, 10 FE/t$(li)Life Sign Monitor: body\'s health$(br2)The rest: see the tooltips.',
          '$(l)Manutenção$()$(li)Antena Central: +50% de alcance$(li)Reciclador de Soro: metade$(li)Farol de '
          'Chamado do Corpo: ele vem a você, 10 FE/t$(li)Monitor de Sinais Vitais: vida do corpo$(br2)O resto: veja as dicas.'),
     ]},

    {'cat': 'jar', 'id': 'charge', 'sort': 3, 'icon': 'minecraft:redstone',
     'en': 'Jar Charge', 'pt': 'Carga do Pote',
     'pages': [
         ('text',
          'A jar holds 100 kFE, full from the chair. As the jar you spend 2 FE/t: about 40 min. In your body, '
          'nothing.$(br)At 0 powered modules stop; Armour Plating, Drill Bit, Magnet, Self-Righting Gyro and '
          'Night Lens still work.',
          'O pote guarda 100 kFE, cheio ao sair da cadeira. Sendo o pote, gasta 2 FE/t: uns 40 min. No '
          'corpo, nada.$(br)Em 0, módulos com energia param; Blindagem, Broca, Ímã, Giroscópio '
          'Autonivelante e Lente Noturna seguem.'),
         ('text',
          'To recharge:$(li)feed slot: redstone, 5 kFE a dust, 45 a block$(li)Charging Dock: stand on it, 500 '
          'FE/t$(li)a Dock under your placed head$(li)a Cradle holding your head: up to 10 kFE a second',
          'Para recarregar:$(li)espaço de recarga: redstone, 5 kFE o pó, 45 o bloco$(li)Base de Recarga: fique '
          'em cima, 500 FE/t$(li)Base sob a cabeça pousada$(li)Berço com sua cabeça: até 10 kFE por segundo'),
     ]},

    {'cat': 'jar', 'id': 'tiers', 'sort': 4, 'icon': 'headinajar:executive_life_support_jar',
     'en': 'Jar Tiers', 'pt': 'Tipos de Pote',
     'pages': [
         ('text',
          '$(li)Basic: 3 slots, 100 kFE, 1,000 mB, 10 glass$(li)Compact: as Basic, head-sized$(li)Reinforced: '
          '5 slots, 250 kFE, 1,500 mB, 20 glass$(li)Executive: 7 slots, 500 kFE, 2,000 mB, 30 glass$(br)The chair takes any.',
          '$(li)Básico: 3 espaços, 100 kFE, 1.000 mB, 10 de vidro$(li)Compacto: igual, do tamanho da '
          'cabeça$(li)Reforçado: 5, 250 kFE, 1.500 mB, 20 vidro$(li)Executivo: 7, 500 kFE, 2.000 mB, 30 vidro$(br)A '
          'cadeira aceita todos.'),
     ]},
]

JEI_INFO = {  # item id -> (en, pt): JEI's information tab, plain sentences
    'headinajar:galvanic_pile': (
        'Burns fuel into 20 FE/t and gives it to the machines touching it. Rotten flesh gives 4,000 FE, a spider eye '
        '6,000 and raw meat or fish 3,000. Use fuel on it, or put it in with a hopper.',
        'Queima combustível em 20 FE/t e dá a energia às máquinas encostadas. Carne podre dá 4.000 FE, olho de '
        'aranha 6.000 e carne ou peixe cru 3.000. Use o combustível nela ou coloque com um funil.'),
    'headinajar:leyden_jar': (
        'Stores 250 kFE. It fills from a power source beside it and feeds its other neighbours at up to 2,000 FE/t. '
        'Two Leyden Jars do not feed each other.',
        'Guarda 250 kFE. Enche com uma fonte de energia ao lado e alimenta os outros vizinhos a até 2.000 FE/t. '
        'Duas Garrafas de Leyden não se alimentam.'),
    'headinajar:tesla_node': (
        'Sends up to 2,000 FE/t to a linked node up to 16 blocks away, losing 5%. Link two with a Surgical Wrench, '
        'the sending node first. The receiving node feeds the machines touching it.',
        'Envia até 2.000 FE/t para um nó ligado a até 16 blocos, perdendo 5%. Ligue dois com uma Chave Cirúrgica, '
        'primeiro o que envia. O nó que recebe alimenta as máquinas encostadas nele.'),
    'headinajar:surgical_wrench': (
        'Links Tesla Nodes: use the sending node, then the receiving one. Sneak and use the sending node to unlink '
        'it.',
        'Liga Nós de Tesla: use o nó que envia, depois o que recebe. Agache e use o nó que envia para desligar.'),
    'headinajar:charging_dock': (
        'Charges a jar standing on it at up to 500 FE/t from the 20 kFE it holds. Your head, set down on it while '
        'you walk your body, charges too. Feed it from a Galvanic Pile, a Leyden Jar or a cable.',
        'Carrega um pote em cima dela a até 500 FE/t, dos 20 kFE que guarda. Sua cabeça pousada nela enquanto você '
        'anda no corpo também carrega. Alimente com uma Pilha Galvânica, uma Garrafa de Leyden ou um cabo.'),
    'headinajar:life_support_cradle': (
        'Holds your head while you walk your body, and a docked head uses no saline. Each second the Cradle refills '
        'your saline from its tank, moves up to 10 kFE into your charge and uses 100 FE; every 10 seconds it mends '
        'a point of glass. It does not extend the link.',
        'Guarda sua cabeça enquanto você anda no corpo, e ali ela não gasta soro. A cada segundo o Berço repõe o '
        'soro do tanque, passa até 10 kFE para sua carga e gasta 100 FE; a cada 10 segundos conserta um ponto de '
        'vidro. Ele não aumenta o alcance da conexão.'),
    'headinajar:saline_mixer': (
        '1,000 mB of water, a sugar, a kelp and a redstone, with 2,000 FE over 10 seconds, make 1,000 mB of Cranial '
        'Saline. Water goes in by bucket or pipe. Glass bottles in its slot are filled at 250 mB each; an empty '
        'bucket takes a bucketful.',
        '1.000 mB de água, um açúcar, uma alga e uma redstone, com 2.000 FE em 10 segundos, fazem 1.000 mB de Soro '
        'Craniano. A água entra por balde ou cano. Garrafas no espaço enchem com 250 mB cada; um balde vazio tira '
        'um balde cheio.'),
    'headinajar:body_printer': (
        'Prints a new headless body for a head in a jar, in 60 seconds. A print takes 16 rotten flesh, 8 bone '
        'blocks, 4 redstone blocks, 8 slime balls, 2,000 mB of saline (by bucket or bottle) and 200 kFE stored '
        'before it starts. Load it before you need it, and press Print as the jar.',
        'Imprime um novo corpo sem cabeça para uma cabeça no pote, em 60 segundos. Gasta 16 carnes podres, 8 '
        'blocos de osso, 4 blocos de redstone, 8 bolas de slime, 2.000 mB de soro (por balde ou frasco) e 200 kFE '
        'guardados antes de começar. Carregue antes de precisar e aperte Imprimir sendo o pote.'),
    'headinajar:separation_chair': (
        'Load a Life Support Jar, Relay Goggles, a Collar Cam and four Cranial Saline, and store 40 kFE in it from a '
        'Galvanic Pile or Leyden Jar touching it. Sit down and hold Sign on Form 27-B: it takes your head off and '
        'leaves your body linked to it. Sneak and use it to open its panel without sitting.',
        'Coloque um Pote de Suporte Vital, Óculos Retransmissores, uma Câmera de Colar e quatro Soros Cranianos, '
        'e guarde nela 40 kFE de uma Pilha Galvânica ou Garrafa de Leyden encostada. Sente e segure Assinar no '
        'Formulário 27-B: ela tira sua cabeça e deixa seu corpo conectado a ela. Agache e use para abrir o painel '
        'sem sentar.'),
    'headinajar:replacement_blade': (
        'Use it on the Separation Chair while it is idle: blade wear goes back to 0. A blade over 80% worn can botch '
        'a cut.',
        'Use na Cadeira de Separação parada: o desgaste da lâmina volta a 0. Uma lâmina acima de 80% de desgaste '
        'pode errar o corte.'),
    'headinajar:glass_patch': (
        'Mends 5 points of Jar Integrity. Use it on your placed head, or put it in the jar menu\'s feed slot.',
        'Conserta 5 pontos de Integridade do Pote. Use na sua cabeça pousada ou no espaço de recarga do menu do '
        'pote.'),
    'headinajar:saline_bottle': (
        'Keeps a head alive: 250 mB a bottle. Use it on your placed head, or put it in the jar menu\'s feed slot. '
        'The Separation Chair takes four.',
        'Mantém a cabeça viva: 250 mB por frasco. Use na sua cabeça pousada ou no espaço de recarga do menu do '
        'pote. A Cadeira de Separação pede quatro.'),
    'headinajar:cranial_saline_bucket': (
        '1,000 mB of Cranial Saline, taken from a Saline Mixer with an empty bucket. It fills a Life Support Cradle '
        'or a Body Printer.',
        '1.000 mB de Soro Craniano, tirados de um Misturador de Soro com um balde vazio. Enche um Berço de Suporte '
        'Vital ou uma Impressora de Corpos.'),
    'headinajar:life_support_jar': (
        'Load it into the Separation Chair: your head lives in it. 3 module slots, 100 kFE of charge, 1,000 mB of '
        'saline and 10 points of glass.',
        'Coloque na Cadeira de Separação: sua cabeça vive nele. 3 espaços de módulo, 100 kFE de carga, 1.000 mB de '
        'soro e 10 pontos de vidro.'),
    'headinajar:compact_life_support_jar': (
        'The same as a Life Support Jar, head-sized: your head faces the front and the whole jar turns with it. '
        'Craft either kind alone to get the other.',
        'Igual ao Pote de Suporte Vital, do tamanho de uma cabeça: a cabeça fica de frente e o pote inteiro gira '
        'com ela. Um sozinho na grade de criação vira o outro.'),
    'headinajar:reinforced_life_support_jar': (
        '5 module slots, 250 kFE of charge, 1,500 mB of saline and 20 points of glass. Load it into the Separation '
        'Chair instead of a plain jar.',
        '5 espaços de módulo, 250 kFE de carga, 1.500 mB de soro e 20 pontos de vidro. Coloque na Cadeira de '
        'Separação no lugar de um pote comum.'),
    'headinajar:executive_life_support_jar': (
        '7 module slots, 500 kFE of charge, 2,000 mB of saline and 30 points of glass. Load it into the Separation '
        'Chair instead of a plain jar.',
        '7 espaços de módulo, 500 kFE de carga, 2.000 mB de soro e 30 pontos de vidro. Coloque na Cadeira de '
        'Separação no lugar de um pote comum.'),
    'headinajar:manipulator_arm': (
        'A jar module. Without it a jar cannot mine, place, fight, use items or pick things up. Each block broken or '
        'placed and each hit costs 200 FE of charge.',
        'Um módulo de pote. Sem ele o pote não minera, não coloca, não luta, não usa itens nem pega nada. Cada '
        'bloco quebrado ou colocado e cada golpe custa 200 FE de carga.'),
}

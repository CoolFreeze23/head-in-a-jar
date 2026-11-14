"""The Employee Handbook, second edition: the back half (Getting Home, Temporary Staffing, Mecha Division, Research and
Human Resources), in both languages, plus JEI information pages for the items of these chapters that had none.

Read by gen_p27_clarity.py, which builds both editions from it and checks that every page fits. Nothing runs on import.
Every number here is the code's own: the link and mast ranges (Config.java, PowerBlockEntities.java), the Courier
(form/Courier.java), hosts (host/*.java and the host data files), mecha (mecha/*.java, MechaEntity.java,
AssemblyGantryBlockEntity.java), the lab (block/LabMachines.java, genetics/*.java) and heads at work (block/HeadJobs.java
and the machines that read it).
"""

# (id, sortnum, icon item id, en name, en description, pt name, pt description)
CATEGORIES = [
    ('getting_home', 6, 'headinajar:relay_goggles', 'Getting Home',
     'How far the link reaches, and every way back to your body.',
     'De Volta para Casa', 'Até onde a conexão vai, e todos os caminhos de volta ao seu corpo.'),
    ('staffing', 7, 'headinajar:surgical_saw', 'Temporary Staffing',
     'Bodies on loan: hosts and robot shells.',
     'Pessoal Temporário', 'Corpos emprestados: hospedeiros e robôs.'),
    ('mecha', 8, 'headinajar:assembly_gantry', 'Mecha Division',
     'The Gantry, the frames, the modules and the gauges.',
     'Divisão de Mechas', 'O Pórtico, as estruturas, os módulos e os mostradores.'),
    ('research', 9, 'headinajar:gene_splicer', 'Research',
     'Blood, genes, serums, and heads put to work.',
     'Pesquisa', 'Sangue, genes, soros e cabeças postas para trabalhar.'),
    ('hr', 10, 'headinajar:safety_placard', 'Human Resources',
     'Frequently asked questions and incident reports.',
     'Recursos Humanos', 'Perguntas frequentes e relatórios de incidente.'),
]

ENTRIES = [
    # ---------------------------------------------------------------- 6. Getting Home
    {'cat': 'getting_home', 'id': 'reach', 'sort': 1, 'icon': 'headinajar:relay_goggles',
     'en': 'Link Range', 'pt': 'O Alcance',
     'pages': [
         ('text',
          'Goggles reach $(l)48$() blocks (Mk I), $(l)160$() (Mk II), $(l)640$() (Mk III) or the whole dimension '
          '(Quantum). Signal is full to half the range, gone at the edge; near another\'s powered Signal Jammer '
          '(16 blocks), 30%. Going back into your body needs over 20%.',
          'Os óculos alcançam $(l)48$() blocos (Mk I), $(l)160$() (Mk II), $(l)640$() (Mk III) ou a dimensão inteira '
          '(Quânticos). O sinal é cheio até a metade e some na borda. Perto do Bloqueador de Sinal ligado de outro (16 '
          'blocos), cai a 30%. Voltar ao corpo pede mais de 20%.'),
         ('spotlight', 'headinajar:relay_mast',
          'A powered mast passes the link on: head to mast in goggle range, then mast to mast and on to the body '
          'within $(l)96$() blocks ($(l)192$() for Mk II). Each mast costs 3%. It draws 1 FE/t.',
          'Um mastro ligado passa a conexão: da cabeça ao mastro no alcance dos óculos, e de mastro em mastro até o '
          'corpo em $(l)96$() blocos ($(l)192$() no Mk II). Cada um custa 3%. Gasta 1 FE/t.'),
     ]},

    {'cat': 'getting_home', 'id': 'courier', 'sort': 2, 'icon': 'minecraft:bell',
     'en': 'The Courier', 'pt': 'O Entregador',
     'pages': [
         ('text',
          'Stranded as a jar? Press $(l)Courier$() in the jar menu, or type /headoffice courier. In 30 s your body, '
          'or its corpse, arrives beside you from anywhere, for 20 kFE (or all your charge). Step into a body with '
          '$(k:key.headinajar.swap); a corpse takes a Suture Kit.',
          'Preso como pote? Aperte $(l)Entregador$() no menu do pote, ou digite /headoffice courier. Em 30 s seu '
          'corpo, ou cadáver, chega ao seu lado de onde estiver: 20 kFE, ou toda a sua carga. Entre no corpo com '
          '$(k:key.headinajar.swap); no cadáver, com Kit de Sutura.'),
         ('spotlight', 'headinajar:body_recall_beacon',
          'A jar module: while you are a jar, your standing body walks to you, 10 FE/t. Same dimension and loaded '
          'chunks only. It halts before lava, fire or a drop of over 3 blocks.',
          'Módulo de pote: sendo você pote, seu corpo em pé anda até você, 10 FE/t. Só na mesma dimensão e em chunk '
          'carregado. Para antes de lava, fogo ou queda de mais de 3 blocos.'),
     ],
     },

    {'cat': 'getting_home', 'id': 'suture', 'sort': 3, 'icon': 'headinajar:suture_kit',
     'en': 'Head Back On', 'pt': 'Cabeça de Volta',
     'pages': [
         ('text',
          'With a $(item)Suture Kit$(): carrying your own head, hold $(k:key.use) for 3 s; or, as a jar, use it on '
          'your body or corpse. You stand up whole and dizzy. Modules and an empty jar come back, goggles and '
          'collar a little worn; a corpse gives your things back.',
          'Com um $(item)Kit de Sutura$(): carregando a própria cabeça, segure $(k:key.use) por 3 s; ou, como pote, '
          'use no corpo ou cadáver. Você levanta tonto. Voltam os módulos e um pote vazio; óculos e '
          'câmera um pouco gastos; o cadáver devolve tudo.'),
     ]},

    {'cat': 'getting_home', 'id': 'corpses', 'sort': 4, 'icon': 'minecraft:skeleton_skull',
     'en': 'Corpses', 'pt': 'Cadáveres',
     'pages': [
         ('text',
          'A linked body that dies becomes a corpse with your armour and inventory, and you wake up in your head. '
          'Nothing hurts a corpse. Only you, and those you trust (/headoffice trust add), may use it to open it.',
          'Um corpo conectado que morre vira um cadáver com sua armadura e inventário, e você acorda na sua cabeça. '
          'Nada fere um cadáver. Só você, e quem tem sua confiança (/headoffice trust add), podem usá-lo para '
          'abri-lo.'),
         ('incident', ('Incident 0119: No Suture Kit', 'Incidente 0119: Sem Kit'),
          'As the jar, use the corpse. A Suture Kit on you or among its things is used at once. With no kit, the '
          'corpse opens its inventory so you can craft one. No body at all: the Body Printer.',
          'Como pote, use o cadáver. Um kit com você ou nos pertences é usado na hora. Sem kit, o cadáver abre o '
          'inventário para você fazer um. Sem corpo: a Impressora de Corpos.'),
     ]},

    {'cat': 'getting_home', 'id': 'lost_property', 'sort': 5, 'icon': 'minecraft:compass',
     'en': 'Lost Property', 'pt': 'Achados e Perdidos',
     'pages': [
         ('text',
          'Type $(l)/headoffice status$(). It prints your form, your link, where your head is (in you, set down, '
          'docked or in the satchel) and where your body is (standing or a corpse), each with dimension and '
          'coordinates, and your charge.',
          'Digite $(l)/headoffice status$(). Ele mostra sua forma, a conexão, onde está a cabeça (em você, no chão, no '
          'Berço ou na bolsa) e onde está o corpo (em pé ou cadáver), cada um com dimensão e coordenadas, e sua '
          'carga.'),
     ]},

    # ---------------------------------------------------------------- 7. Temporary Staffing
    {'cat': 'staffing', 'id': 'saw', 'sort': 1, 'icon': 'headinajar:surgical_saw',
     'en': 'The Surgical Saw', 'pt': 'A Serra Cirúrgica',
     'pages': [
         ('spotlight', 'headinajar:surgical_saw',
          'Use it on a creature at half its health or less: the body stays up as a $(l)host$(). With a jar of any '
          'kind in your off hand, you keep the $(l)head$() and the body falls.',
          'Use numa criatura com metade da vida ou menos: o corpo fica de pé como $(l)hospedeiro$(). Com um pote de '
          'qualquer tipo na outra mão, você fica com a $(l)cabeça$() e o corpo cai.'),
     ]},

    {'cat': 'staffing', 'id': 'riding', 'sort': 2, 'icon': 'minecraft:saddle',
     'en': 'Riding a Host', 'pt': 'Montando',
     'pages': [
         ('text',
          'As a jar, use a host to ride it. Linked and carrying your head, use it too: your body stays standing and '
          'the jar climbs on. The host goes where you look; fliers rise while you hold $(k:key.jump). '
          '$(k:key.attack) attacks as the host does; $(k:key.sneak) dismounts.',
          'Como pote, use um hospedeiro para subir. Conectado, carregando a cabeça na bolsa, também: o corpo fica de '
          'pé e o pote sobe. Ele vai aonde você olha; os que voam sobem com $(k:key.jump). $(k:key.attack) usa o '
          'ataque dele; $(k:key.sneak) desce.'),
     ]},

    {'cat': 'staffing', 'id': 'sync', 'sort': 3, 'icon': 'headinajar:neural_lace',
     'en': 'Sync and Abilities', 'pt': 'Sincronia',
     'pages': [
         ('text',
          'Sync grows 5% a minute while you ride; on the first climb, each ring you hit adds 3%. A body moves its own '
          'way from the first second: a flier flies, a spider climbs, a chicken floats. At 25% its other traits work.',
          'A sincronia sobe 5% por minuto montado; na 1ª subida, +3% por anel acertado. O corpo se move do seu jeito '
          'desde o primeiro segundo: quem voa voa, a aranha escala, a galinha plana. Com 25% valem os outros traços.'),
         ('text',
          'At 50% $(k:key.headinajar.ability) fires its ability, at 75% $(k:key.headinajar.secondary) the second '
          '(blaze, enderman, ravager). At 100%: 10% stronger.',
          'Com 50% $(k:key.headinajar.ability) usa a habilidade, com 75% $(k:key.headinajar.secondary) a segunda '
          '(blaze, enderman, devastador). Com 100%: 10% a mais.'),
     ]},

    {'cat': 'staffing', 'id': 'abilities', 'sort': 4, 'icon': 'minecraft:ender_pearl',
     'en': 'Who Does What', 'pt': 'Quem Faz o Quê',
     'pages': [
         ('form', ('Form 9-H: Host Abilities', 'Formulário 9-H: Habilidades'),
          'What $(k:key.headinajar.ability) does in each host, from 50% sync:$(br)Sprint: horse, donkey, mule, '
          'ocelot$(br)Charge: camel, dolphin, goat, ravager$(br)Pounce: wolf, fox, phantom',
          'O que $(k:key.headinajar.ability) faz em cada hospedeiro, com 50% de sincronia:$(br)Corrida: cavalo, '
          'burro, mula, jaguatirica$(br)Investida: camelo, golfinho, cabra, devastador$(br)Bote: lobo, raposa, '
          'phantom'),
         ('form', ('Form 9-H (2)', 'Formulário 9-H (2)'),
          'Web: spiders$(br)Arrows: skeleton, stray$(br)Fireballs: blaze, ghast$(br)Teleport: enderman$(br)Explode: '
          'creeper (halves your glass)$(br)Call their kind: zombie, husk, drowned$(br)Toss: iron golem',
          'Teia: aranhas$(br)Flechas: esqueleto, errante$(br)Fogo: blaze, ghast$(br)Teleporte: enderman$(br)Explosão: '
          'creeper (metade do vidro)$(br)Chamar os seus: zumbi, zumbi-do-deserto, afogado$(br)Arremesso: golem de ferro'),
         ('form', ('Form 9-H (3)', 'Formulário 9-H (3)'),
          'Roar: polar bear$(br)Ink: squids$(br)Echolocation: bat$(br)Wind charge: breeze$(br)Egg: chicken$(br)'
          'Potion: witch$(br)Sonic boom: warden$(br2)The rest: none.',
          'Rugido: urso-polar$(br)Tinta: lulas$(br)Sonar: morcego$(br)Vento: breeze$(br)Ovo: galinha$(br)Poção: '
          'bruxa$(br)Estrondo: warden$(br2)O resto: nada.'),
     ]},

    {'cat': 'staffing', 'id': 'upkeep', 'sort': 5, 'icon': 'headinajar:embalming_fluid',
     'en': 'Upkeep', 'pt': 'Manutenção',
     'pages': [
         ('text',
          'A host rots 2% a minute and slows below half; at 0 it falls apart. $(item)Embalming Fluid$() makes it fresh '
          'and halves the rot for a day (20 minutes). $(item)Neural Lace$(), used at 50% sync or more, stops it for '
          'good.',
          'O hospedeiro apodrece 2% por minuto: lento abaixo da metade, desfeito em 0. O $(item)Fluido de '
          'Embalsamar$() o renova e, por um dia (20 min), apodrece à metade. Com 50% de sincronia ou mais, a '
          '$(item)Renda Neural$() acaba de vez com o apodrecimento.'),
     ]},

    {'cat': 'staffing', 'id': 'body_locker', 'sort': 6, 'icon': 'headinajar:body_locker',
     'en': 'The Body Locker', 'pt': 'O Armário',
     'pages': [
         ('spotlight', 'headinajar:body_locker',
          'Use it to store the nearest host within 3 blocks, frozen: nothing rots inside. Use it again to let it out '
          'in front. It takes robot shells and your own mecha too.',
          'Use para guardar o hospedeiro mais próximo a até 3 blocos, congelado: nada apodrece lá dentro. Use de novo '
          'para soltá-lo na frente. Aceita robôs e o seu próprio mecha.'),
     ]},

    {'cat': 'staffing', 'id': 'robots', 'sort': 7, 'icon': 'headinajar:lunk_chassis',
     'en': 'Robot Bodies', 'pt': 'Corpos Robóticos',
     'pages': [
         ('text',
          'Set a chassis down and use it as a jar to climb in. Shells never rot, are always synced and run on your '
          'jar\'s charge, 3 FE/t.$(li)$(l)Lunk$(): strong; tosses things$(li)$(l)Rover$(): fast, sprints; 9-slot '
          'hold$(li)$(l)Spider Walker$(): climbs, spins webs',
          'Coloque um chassi e use como pote para entrar. Robôs não apodrecem, já vêm sincronizados e gastam a carga '
          'do pote, 3 FE/t.$(li)$(l)Brutamontes$(): forte; arremessa$(li)$(l)Rover$(): rápido, corre; leva 9 '
          'itens$(li)$(l)Andador Aranha$(): escala, solta teias'),
     ]},

    # ---------------------------------------------------------------- 8. Mecha Division
    {'cat': 'mecha', 'id': 'gantry', 'sort': 1, 'icon': 'headinajar:assembly_gantry',
     'en': 'The Gantry', 'pt': 'O Pórtico',
     'pages': [
         ('text',
          'Frame top left, modules in slots of their kind, then $(l)Assemble$() (10 kFE). The mecha stands on the '
          'Gantry, which charges it at up to 5,000 FE/t. $(l)Repair$() uses Mecha Plating, 20 HP each; '
          '$(l)Take apart$() puts the parts back in the slots.',
          'Estrutura em cima à esquerda, módulos nos espaços do seu tipo, e $(l)Montar$() (10 kFE). O mecha fica '
          'sobre o Pórtico, que o carrega a até 5.000 FE/t. $(l)Reparar$() gasta Placa de Mecha, 20 de vida cada; '
          '$(l)Desmontar$() devolve as peças aos espaços.'),
     ]},

    {'cat': 'mecha', 'id': 'frames', 'sort': 2, 'icon': 'headinajar:hopper_frame',
     'en': 'Frames', 'pt': 'Estruturas',
     'pages': [
         ('form', ('Form 12-M', 'Formulário 12-M'),
          'One of each slot, except:$(br)Hopper: torso 2, arms 2$(br)Bulwark: back 2, torso 3, arms 2, sensor 2'
          '$(br)Chimera: torso 3, arms 2, 3 Gene Ports$(br)Wheelhouse (a car): no legs, back 2, torso 2',
          'Um de cada, exceto:$(br)Saltador: torso 2, braços 2$(br)Baluarte: costas 2, torso 3, braços 2, sensor 2'
          '$(br)Quimera: torso 3, braços 2, 3 Portas Genéticas$(br)Casa sobre Rodas (um carro): sem pernas, costas '
          '2, torso 2'),
         ('text',
          'Skitter: no legs (climbs walls), torso 2, sensor 2$(br)Kite: flies; no back, arms 2$(br)Colossus: back 2, '
          'core 2, torso 5, arms 4, sensor 2$(br)Executive: the same, plus 4 Gene Ports$(br)The Wheelhouse cannot '
          'jump and is slow off-road.',
          'Rastejador: sobe paredes; sem pernas, torso 2, sensor 2$(br)Pipa: voa; sem costas, braços 2$(br)Colosso: '
          'costas 2, núcleo 2, torso 5, braços 4, sensor 2$(br)Executivo: idem, mais 4 Portas Genéticas$(br)A Casa '
          'não pula; é lenta fora da estrada.'),
         ('slot', ('frame_hopper', 'headinajar:hopper_frame'),
          'The Hopper, 60 HP: with Hydraulic Legs and a Galvanic Stack it walks and charges itself. The Colossus '
          'and Executive cannot fit a nether portal.',
          'O Saltador, 60 de vida: com Pernas Hidráulicas e uma Pilha Galvânica Compacta, anda e se carrega. '
          'Colosso e Executivo não passam num portal do Nether.'),
     ]},

    {'cat': 'mecha', 'id': 'piloting', 'sort': 3, 'icon': 'headinajar:mecha_hydraulic_legs',
     'en': 'Piloting', 'pt': 'Pilotagem',
     'pages': [
         ('text',
          'Board your mecha as a jar, or linked while carrying your head: use it. Only its owner and players they '
          'trust can. To get out, hold $(k:key.sneak) and $(k:key.headinajar.jar_menu) for a second. Anyone with a '
          'body may ride in a Passenger Pod or a Wheelhouse seat.',
          'Embarque usando o mecha como pote, ou conectado com a cabeça na bolsa; só o dono e aqueles em quem ele '
          'confia. Saia segurando $(k:key.sneak) e $(k:key.headinajar.jar_menu) por 1 s. Com corpo, vá na Cápsula '
          'de Passageiro ou num assento da Casa sobre Rodas.'),
         ('text',
          '$(k:key.attack) works the left arm, $(k:key.use) the right. $(k:key.jump) jumps, or fires jets in the air. '
          '$(k:key.headinajar.ability) fires the chosen utility, $(k:key.headinajar.secondary) picks the next; add '
          '$(k:key.sneak) for the mecha\'s genes. $(k:key.headinajar.swap) opens the Cargo Hold.',
          '$(k:key.attack) move o braço esquerdo, $(k:key.use) o direito. $(k:key.jump) pula, ou dispara jatos no ar. '
          '$(k:key.headinajar.ability) dispara o utilitário, $(k:key.headinajar.secondary) escolhe o próximo; com '
          '$(k:key.sneak), os genes do mecha. $(k:key.headinajar.swap) abre o Compartimento de Carga.'),
     ]},

    {'cat': 'mecha', 'id': 'gauges', 'sort': 4, 'icon': 'headinajar:mecha_radiator_fins',
     'en': 'Gauges', 'pt': 'Mostradores',
     'pages': [
         ('text',
          'Modules and moving make heat; rain and water shed it faster, the Nether slower. Above 85% heat, '
          'everything works at 70%; at 100% the mecha vents for 5 s. Past 60% load it slows; past 100%, no jumping; '
          'past 125%, no moving.',
          'Módulos e movimento geram calor; chuva e água dissipam mais rápido, o Nether mais devagar. Acima de 85% '
          'de calor, tudo rende 70%; em 100%, o mecha solta vapor por 5 s. Com carga acima de 60% fica lento; de '
          '100%, não pula; de 125%, não anda.'),
         ('form', ('Form 3-P: Pilot Warnings', 'Formulário 3-P: Avisos'),
          '$(l)VENTING$(): nothing works, the steam scalds. Wait.$(br)'
          '$(l)NO POWER$(): 30% speed, no arms. Park on the Gantry.$(br)'
          '$(l)OVERLOADED$(): unload the hold.$(br)'
          '$(l)NO LEGS$(): fit walking legs.',
          '$(l)LIBERANDO VAPOR$(): nada funciona, o vapor queima. Espere.$(br)'
          '$(l)SEM ENERGIA$(): 30% de velocidade, sem braços. Pare no Pórtico.$(br)'
          '$(l)SOBRECARGA$(): esvazie o compartimento.$(br)'
          '$(l)SEM PERNAS$(): instale pernas.'),
     ]},

    {'cat': 'mecha', 'id': 'mecha_modules', 'sort': 5, 'icon': 'headinajar:mecha_drill',
     'en': 'Mecha Modules', 'pt': 'Módulos de Mecha',
     'pages': [
         ('text',
          '$(l)Legs$() that walk: Hydraulic Legs (set the Stride), Arachnid Legs (climb walls), Stilts (2-block '
          'steps), Hover Skirt (over water and lava), Lava Treads. Spring Heels, Sprint Servos, Aqua Fins and Anchor '
          'Clamps do not walk: only the Kite can use them.',
          '$(l)Pernas$() que andam: Pernas Hidráulicas, Pernas Aracnídeas, Pernas de Pau, Saia Flutuante, Esteiras '
          'de Lava. Calcanhares de Mola, Servos de Corrida, Barbatanas Aquáticas e Grampos de Ancoragem não andam: '
          'só a Pipa os usa.'),
         ('text',
          '$(l)Core$(): Galvanic Stack, Capacitor Bank; the Combustion Kernel, Bio-Reactor Core and Fusion Bottle '
          'burn fuel from the Cargo Hold.$(br2)Signal Booster and Bio-Scanner: fitted, they only draw power for '
          'now.',
          '$(l)Núcleo$(): Pilha Galvânica Compacta, Banco de Capacitores; Núcleo de Combustão, Núcleo Biorreator e '
          'Garrafa de Fusão queimam o que há no compartimento.$(br)Por ora só consomem energia: '
          'Amplificador de Sinal, Bioscanner.'),
     ]},

    # ---------------------------------------------------------------- 9. Research
    {'cat': 'research', 'id': 'chain', 'sort': 1, 'icon': 'headinajar:syringe',
     'en': 'The Chain', 'pt': 'A Cadeia',
     'pages': [
         ('text',
          'A $(item)Syringe$() draws a $(item)Blood Sample$(). The $(item)Centrifuge$() spins it into a $(item)DNA '
          'Fraction$(). The $(item)DNA Sequencer$() reads that into your library. The $(item)Gene Splicer$() makes a '
          '$(item)Serum Cassette$(); the $(item)Injection Rig$() puts it in you. All need power.',
          'Uma $(item)Seringa$() tira uma $(item)Amostra de Sangue$(). A $(item)Centrífuga$() a gira em $(item)Fração '
          'de DNA$(). O $(item)Sequenciador de DNA$() a lê para a sua biblioteca. O $(item)Emendador de Genes$() faz um '
          '$(item)Cartucho de Soro Genético$(); a $(item)Estação de Injeção$() o põe em você. Tudo usa energia.'),
     ]},

    {'cat': 'research', 'id': 'blood', 'sort': 2, 'icon': 'headinajar:blood_sample',
     'en': 'Blood Tools', 'pt': 'Coleta',
     'pages': [
         ('text',
          'Syringe (30-60), Precision Syringe (60-90): blood.$(br)Marrow Drill: skeletons, the Wither.$(br)Oil '
          'Tap: iron golems.$(br)Ecto Trap: vexes, allays, breezes, snow golems.$(br)Leviathan Needle: bosses, '
          'Elder Guardian, Warden.',
          'Seringa (30-60) e de Precisão (60-90): sangue.$(br)Broca de Medula: esqueletos, Wither.$(br)'
          'Torneira de Óleo: golens de ferro.$(br)Armadilha de Ecto: vex, allay, breeze, golem de neve.$(br)'
          'Agulha Leviatã: chefões, Warden, Guardião-ancião.'),
     ]},

    {'cat': 'research', 'id': 'carriers', 'sort': 3, 'icon': 'headinajar:dna_fraction',
     'en': 'Genes', 'pt': 'Genes',
     'pages': [
         ('text',
          'An auto-read (10 s) adds 15% of the fraction\'s quality, shared among its creature\'s genes. Call the '
          'twelve bases yourself and it adds up to 25%. At 30% a gene is a $(l)Draft$(): half strength. 60%: three '
          'quarters. 100%: full.',
          'Uma leitura automática (10 s) soma 15% da qualidade da fração, dividida entre os genes da criatura; '
          'chamando você as doze bases, até 25%. Com 30% o gene é um $(l)Rascunho$(): metade da força. 60%: três '
          'quartos. 100%: inteira.'),
         ('gene_list',),
     ]},

    {'cat': 'research', 'id': 'splicing', 'sort': 4, 'icon': 'headinajar:serum_cassette',
     'en': 'Splicing', 'pt': 'Emenda e Injeção',
     'pages': [
         ('text',
          'Up to three Draft genes, Bio-Gel and a glass bottle: 60 s. Stability starts at 100; genes and clashing '
          'pairs cost some, matching pairs add 10. Catalysts add: slime ball 5, amethyst 10, echo shard 20, nether '
          'star 40. Below 60 a serum may mutate.',
          'Até 3 genes Rascunho, Biogel e garrafa: 60 s. Estabilidade: 100, menos genes e pares em conflito; pares '
          'afins, +10. Catalisadores: bola de slime 5, ametista 10, fragmento de eco 20, estrela do Nether 40. '
          'Abaixo de 60, pode mutar.'),
         ('text',
          'Use a serum by hand on a host or a Chimera. Your body takes two serums of load 3 or less; a host, one '
          'up to 6; each Gene Port, one up to 8. Bodies and hosts may reject the wrong blood; mecha never. Active '
          'genes: $(k:key.headinajar.ability) and $(k:key.headinajar.secondary). $(item)Gene Wash$() clears you.',
          'Use o soro à mão num hospedeiro ou Quimera. Corpo: dois soros de carga até 3; hospedeiro: um até 6; '
          'cada Porta Genética: um até 8. Corpo e hospedeiro podem rejeitar sangue errado; mecha, nunca. Ativos: '
          '$(k:key.headinajar.ability) e $(k:key.headinajar.secondary). $(item)Lavagem Genética$() limpa você.'),
     ]},

    {'cat': 'research', 'id': 'turret', 'sort': 5, 'icon': 'headinajar:turret_pedestal',
     'en': 'The Turret', 'pt': 'A Torreta',
     'pages': [
         ('text',
          'Fit a Jarred Head: it fights hostiles within 16 blocks as the mob did. Skeletons need arrows in a chest '
          'beside or below; a creeper head blows up once. Pigs and cows have no job. 1 FE/t watching, 10 firing.',
          'Encaixe uma Cabeça no Pote: ela luta contra hostis a até 16 blocos como a criatura. Esqueletos pedem '
          'flechas num baú ao lado ou embaixo; cabeça de creeper explode uma vez. Porco e vaca não servem. 1 FE/t '
          'vigiando, 10 atirando.'),
     ]},

    {'cat': 'research', 'id': 'array', 'sort': 6, 'icon': 'headinajar:cerebral_array',
     'en': 'The Array', 'pt': 'A Matriz',
     'pages': [
         ('text',
          'Up to nine Jarred Heads make FE from Nutrient Broth (bucket or pipe): each drinks 1 mB a second. Most '
          'make 10 FE/t; villagers 40, endermen 80, evokers 120. Five or more: +10%. A $(item)Board Room Core$() '
          'puts one Dragon Head to work: 300.',
          'Até 9 Cabeças no Pote geram FE; cada uma bebe 1 mB de Caldo Nutritivo (balde ou cano) por segundo. A '
          'maioria faz 10 FE/t; aldeões 40, endermen 80, evocadores 120; com cinco ou mais, +10%. $(item)Núcleo da '
          'Sala de Reuniões$(): Cabeça de Dragão, 300.'),
     ]},

    {'cat': 'research', 'id': 'socket', 'sort': 7, 'icon': 'headinajar:neural_socket',
     'en': 'The Neural Socket', 'pt': 'O Soquete Neural',
     'pages': [
         ('text',
          'As a jar, use it to plug in. Scroll: face; hotbar keys: level 1-15, 0 off; $(k:key.headinajar.ability): '
          'pulse; $(k:key.sneak) with $(k:key.headinajar.jar_menu): unplug. Alone, a zombie head pulses each '
          'second, a villager\'s climbs 8 levels, an enderman\'s fires when stared at.',
          'Como pote, use para conectar. Rolar: face; barra: nível 1-15, 0 desliga; $(k:key.headinajar.ability): pulso; '
          '$(k:key.sneak) com $(k:key.headinajar.jar_menu): sair. Sozinha, cabeça de zumbi pulsa por segundo, de aldeão '
          'sobe 8 degraus, de enderman dispara se encarada.'),
     ]},

    {'cat': 'research', 'id': 'staff', 'sort': 8, 'icon': 'headinajar:employee_mount',
     'en': 'Villager Staff', 'pt': 'Aldeões',
     'pages': [
         ('spotlight', 'headinajar:employee_mount',
          'Hold $(k:key.sneak) and use the mount on a machine to hang it there: without $(k:key.sneak) the machine '
          'opens instead. Then use the mount with a jarred villager. Use it to check on them; $(k:key.sneak) and use '
          'with an empty hand to take them down.',
          'Segure $(k:key.sneak) e use o suporte numa máquina para pendurá-lo: sem $(k:key.sneak), a máquina abre. '
          'Depois use o suporte com um aldeão no pote. Use-o para ver como ele está; $(k:key.sneak) e use com a mão '
          'vazia para tirá-lo.'),
         ('form', ('Form 31-E: Staff', 'Formulário 31-E'),
          'Pay: an emerald a day, from a chest touching the mount; unpaid, half the bonus.$(br)Nitwit: '
          'nothing$(br)Librarian: Sequencer reads at 90%$(br)Cleric: +10 stability$(br)Fisherman: Saline Mixer 25% '
          'faster',
          'Paga: uma esmeralda por dia, de um baú encostado no suporte; sem paga, metade do bônus.$(br)Bobo: '
          'nada$(br)Bibliotecário: o Sequenciador lê a 90%$(br)Clérigo: +10 de estabilidade$(br)Pescador: soro 25% mais '
          'rápido'),
         ('form', ('Form 31-E (2)', 'Formulário 31-E (2)'),
          'Mason: chair blade wears half as fast$(br)Smiths: Gantry repair +1/3$(br)Cartographer: Relay Mast range +50%$(br)Butcher: Leech Jar quality '
          '+50%$(br)Farmer: Array drinks half the broth$(br)Fletcher: turrets fire 25% faster',
          'Pedreiro: lâmina gasta metade$(br)Ferreiros: reparo no Pórtico +1/3$(br)Cartógrafo: mastros +50% de '
          'alcance$(br)Açougueiro: sanguessugas +50%$(br)Fazendeiro: Matriz gasta metade do caldo$(br)Flecheiro: '
          'torretas 25% mais rápidas'),
     ],
     },

    # ---------------------------------------------------------------- 10. Human Resources
    {'cat': 'hr', 'id': 'faq', 'sort': 1, 'icon': 'headinajar:safety_placard',
     'en': 'Questions', 'pt': 'Perguntas',
     'pages': [
         ('text',
          '$(l)Can I drink the saline?$() No.$(br2)'
          '$(l)Can a jar sit in the chair?$() No, but it can look at the panel.$(br2)'
          '$(l)Can my body take a portal?$() Only carrying its head, or over an Entangled Pair. A jar takes any.',
          '$(l)Posso beber o soro?$() Não.$(br2)'
          '$(l)Um pote senta na cadeira?$() Não, mas pode ver o painel.$(br2)'
          '$(l)Meu corpo passa por portal?$() Só com a cabeça na bolsa ou um Par Emaranhado; o pote, por todos.'),
         ('text',
          '$(l)What if my jar breaks?$() A real death: modules, goggles and collar drop, and you respawn whole. The '
          'jar menu\'s Let Go button does this on purpose.$(br)'
          '$(l)Can I saw a colleague?$() No. Nor bosses, named creatures, others\' pets or hosts.',
          '$(l)E se o pote quebrar?$() Morte de verdade: módulos, óculos e câmera caem; você renasce inteiro. O '
          'botão Desistir do menu do pote faz isso de propósito.$(br)'
          '$(l)Posso serrar um colega?$() Não. Nem chefões, mobs com nome, bichos alheios ou hospedeiros.'),
     ]},

    {'cat': 'hr', 'id': 'incidents', 'sort': 2, 'icon': 'minecraft:feather',
     'en': 'Incident Reports', 'pt': 'Ocorrências',
     'pages': [
         ('incident', ('Incident 0311: Mecha wrecked', 'Incidente 0311: Mecha destruído'),
          'Below 10% health, the Golden Parachute threw the pilot clear. A frame destroyed outright throws the '
          'pilot too, with a knock to the glass; about half its modules survive. Next: a Glass Patch.',
          'Abaixo de 10% de vida, o Paraquedas Dourado lançou o piloto. Destruído de uma vez, o mecha também o lança, '
          'com um tranco no vidro; metade dos módulos sobra. Próximo: um Remendo de Vidro.'),
         ('incident', ('Incident 0451: Head left in a fire', 'Incidente 0451: Cabeça no fogo'),
          'The glass lost a point a second until it broke: a real death. Next time: fetch it with the Head Satchel at '
          'once, or fit a Heat Shield.',
          'O vidro perdeu um ponto por segundo até quebrar: morte de verdade. Da próxima vez: busque na hora com a '
          'Bolsa de Cabeça, ou instale um Escudo Térmico.'),
         ('incident', ('Incident 0517: Body died while away', 'Incidente 0517: Corpo morto'),
          'A body left standing was killed and lay down as a corpse. Next: the Courier, then a Suture Kit. A Life '
          'Sign Monitor shows its health.',
          'Um corpo deixado em pé foi morto e virou cadáver. Próximo: o Entregador, depois um Kit de Sutura. O '
          'Monitor de Sinais Vitais mostra sua vida.'),
     ],
     'note': ('HR sends its condolences.', 'Pêsames do RH.')},
]

# item id -> (en, pt): JEI information for the items of this half that had none
JEI_INFO = {
    'headinajar:relay_mast': (
        'Passes the neuro-link on: from your head within goggle range, to other masts and to your body within 96 '
        'blocks. Each mast costs 3% of the signal. Needs 1 FE a tick from a powered neighbour.',
        'Passa a neuroconexão adiante: da cabeça no alcance dos óculos até outros mastros e ao corpo a até 96 blocos. '
        'Cada mastro custa 3% do sinal. Precisa de 1 FE por tique de um vizinho com energia.'),
    'headinajar:relay_mast_mk2': (
        'A Relay Mast with a 192-block radius. The same 1 FE a tick.',
        'Um Mastro Retransmissor com raio de 192 blocos. O mesmo 1 FE por tique.'),
    'headinajar:signal_jammer': (
        'Cuts the neuro-link of anyone within 16 blocks to 30%, except its owner and the players they trust. Needs '
        '4 FE a tick.',
        'Corta para 30% a neuroconexão de qualquer um a até 16 blocos, menos o dono e aqueles em quem ele confia. Precisa '
        'de 4 FE por tique.'),
    'headinajar:body_locker': (
        'Use it to store the nearest host body within 3 blocks; nothing rots inside. Use it again to let it out in '
        'front. Takes robot shells and your own mecha too.',
        'Use para guardar o corpo hospedeiro mais próximo a até 3 blocos; nada apodrece lá dentro. Use de novo para '
        'soltá-lo na frente. Também aceita robôs e o seu próprio mecha.'),
    'headinajar:safety_placard': (
        'Hangs on a wall. Use it to change the notice: there are twelve.',
        'Fica pendurada na parede. Use para trocar o aviso: são doze.'),
    'headinajar:neural_lace': (
        'Use it on a host body at 50% sync or more: it never rots again.',
        'Use num corpo hospedeiro com 50% de sincronia ou mais: ele nunca mais apodrece.'),
    'headinajar:embalming_fluid': (
        'Use it on a host body: fresh again, and it rots at half speed for a day.',
        'Use num corpo hospedeiro: fica fresco de novo e apodrece na metade da velocidade por um dia.'),
    'headinajar:board_room_core': (
        'Goes in the Cerebral Array\'s tenth slot. It lets one Dragon Head sit in the Array, making 300 FE/t.',
        'Vai no décimo espaço da Matriz Cerebral. Deixa uma Cabeça de Dragão se sentar na Matriz, gerando 300 FE/t.'),
    'headinajar:quantum_knot': (
        'A part. It goes into the Entangled Pair and the Quantum Goggles.',
        'Uma peça. Vai no Par Emaranhado e nos Óculos Quânticos.'),
    'headinajar:plasma_vial': (
        'Comes out of the Centrifuge with each DNA Fraction. It makes Bio-Gel and Gene Wash, and fuels a mecha\'s '
        'Bio-Reactor Core from the Cargo Hold.',
        'Sai da Centrífuga junto com cada Fração de DNA. Faz Biogel e Lavagem Genética, e alimenta o Núcleo Biorreator '
        'de um mecha pelo Compartimento de Carga.'),
    'headinajar:precision_syringe': (
        'Draws Blood Samples of quality 60 to 90, without hurting the creature. Bosses still need a Leviathan Needle.',
        'Tira Amostras de Sangue de qualidade 60 a 90 sem ferir a criatura. Chefões ainda pedem uma Agulha Leviatã.'),
    'headinajar:lunk_chassis': (
        'Place it, then use it as a jar to climb in. A strong, slow robot body with 60 HP that tosses what it looks at. '
        'Runs on the jar\'s charge.',
        'Coloque e use como pote para entrar. Um corpo robótico forte e lento, com 60 de vida, que arremessa o que '
        'olha. Funciona com a carga do pote.'),
    'headinajar:rover_chassis': (
        'Place it, then use it as a jar to climb in. A fast robot body; sneak-use it to open its 9-slot hold. Runs on '
        'the jar\'s charge.',
        'Coloque e use como pote para entrar. Um corpo robótico rápido; agache e use para abrir o compartimento de 9 '
        'espaços. Funciona com a carga do pote.'),
    'headinajar:spider_walker_chassis': (
        'Place it, then use it as a jar to climb in. A robot body that climbs walls, takes no fall damage and shoots '
        'webs. Runs on the jar\'s charge.',
        'Coloque e use como pote para entrar. Um corpo robótico que escala paredes, não sofre dano de queda e solta '
        'teias. Funciona com a carga do pote.'),
    'headinajar:body_recall_beacon': (
        'A jar module. While you are a jar, your standing body walks to you, 10 FE a tick, in the same dimension and a '
        'loaded chunk. It stops at lava, fire and big drops.',
        'Um módulo de pote. Enquanto você é pote, seu corpo em pé anda até você, 10 FE por tique, na mesma dimensão e '
        'num chunk carregado. Ele para diante de lava, fogo e quedas grandes.'),
    'headinajar:suture_kit': (
        'Puts your head back on. Carrying your own head, hold use for 3 seconds; as a jar, use it on your body or '
        'corpse. Modules and an empty jar come back.',
        'Põe sua cabeça de volta. Carregando a própria cabeça, segure usar por 3 segundos; como pote, use no seu corpo '
        'ou cadáver. Módulos e um pote vazio voltam.'),
}

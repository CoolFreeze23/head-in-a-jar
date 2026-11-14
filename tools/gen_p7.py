"""Phase 7 (integrations and polish) data: hosts, blood and genes for creatures from other mods in the pack, Curios slots,
advancements, sounds and the rest of the handbook. Called by gen_data.py. Everything here is inert when the other mod
is absent: host files only match registered creatures, and tag entries are optional."""
import json
import os

RES = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources')


def ab(kind, cooldown=None, **params):
    out = {'type': kind, 'params': {k: float(v) for k, v in params.items()}}
    if cooldown:
        out['cooldown'] = cooldown
    return out


# entity: (blood, decay per minute, flags, primary, secondary, neutral_to)
HOSTS = {
    'brazil_legends:headless_mule': ('brimstone', 0, ['headless', 'fire_immune'], ab('fireball', 40, count=1), ab('sprint', 200), []),
    'brazil_legends:saci_perere': ('warm', 2, ['no_fall_damage'], ab('teleport', 60, range=8), None, []),
    'brazil_legends:mapinguari': ('warm', 2, ['knockback_immune'], ab('roar', 200, radius=5, duration=40), ab('charge', 120, damage=8, speed=1.5), []),
    'brazil_legends:capelobo': ('warm', 2, ['night_vision'], ab('pounce', 60, damage=5, speed=1.2), None, []),
    'twilightforest:minotaur': ('warm', 2, [], ab('charge', 80, damage=8, speed=1.8, knockback=2), None, ['twilightforest:minotaur']),
    'twilightforest:troll': ('warm', 2, ['knockback_immune'], ab('toss', 60, power=1.4), None, []),
    'twilightforest:yeti': ('warm', 2, [], ab('toss', 60, power=1.2), ab('roar', 200, radius=4, duration=30), []),
    'twilightforest:boar': ('warm', 2, [], ab('charge', 60, damage=3, speed=1.6), None, []),
    'twilightforest:deer': ('warm', 2, ['no_fall_damage'], ab('sprint', 200), None, []),
    'twilightforest:skeleton_druid': ('marrow', 2, [], ab('potion', 100), None, ['twilightforest:skeleton_druid']),
    'aether:moa': ('warm', 2, ['slow_fall', 'no_fall_damage'], ab('hover', 120, duration=60), ab('sprint', 200), []),
    'aether:phyg': ('warm', 2, ['flight'], None, None, []),
    'aether:flying_cow': ('warm', 2, ['flight'], None, None, []),
    'mowziesmobs:foliaath': ('ichor', 1, ['knockback_immune'], ab('pounce', 40, damage=6, speed=0.6), None, []),
    'mowziesmobs:grottol': ('oil', 0, ['knockback_immune'], ab('charge', 80, damage=4, speed=1.6), None, []),
    'mutantmonsters:mutant_zombie': ('grave', 2, ['knockback_immune'], ab('roar', 160, radius=6, duration=40), ab('toss', 60, power=1.6), ['minecraft:zombie']),
    'mutantmonsters:mutant_creeper': ('ichor', 2, [], ab('explode', 200, power=4), ab('pounce', 60, damage=6, speed=1.4), []),
    'mutantmonsters:mutant_skeleton': ('marrow', 2, [], ab('arrows', 40, count=5), None, ['minecraft:skeleton']),
    'mutantmonsters:mutant_enderman': ('ender', 2, [], ab('teleport', 40, range=32), ab('toss', 60, power=1.5), ['minecraft:enderman']),
    'naturalist:elephant': ('warm', 2, ['knockback_immune'], ab('toss', 60, power=1.3), ab('charge', 100, damage=6, speed=1.4), []),
    'naturalist:rhino': ('warm', 2, ['knockback_immune'], ab('charge', 60, damage=8, speed=1.7, knockback=2), None, []),
    'naturalist:giraffe': ('warm', 2, [], ab('sprint', 200), None, []),
    'naturalist:bear': ('warm', 2, [], ab('roar', 200, radius=4, duration=30), None, []),
    'naturalist:lion': ('warm', 2, [], ab('pounce', 40, damage=6, speed=1.3), ab('roar', 200, radius=5, duration=30), []),
    'naturalist:tiger': ('warm', 2, ['night_vision'], ab('pounce', 40, damage=6, speed=1.3), None, []),
    'naturalist:komodo_dragon': ('cold', 2, [], ab('pounce', 60, damage=5, speed=1.0), None, []),
    'naturalist:alligator': ('cold', 2, ['water_breathing'], ab('pounce', 60, damage=7, speed=1.1), None, []),
    'friendsandfoes:copper_golem': ('oil', 0, [], None, None, []),
    'friendsandfoes:tuff_golem': ('oil', 0, ['knockback_immune'], None, None, []),
    'friendsandfoes:wildfire': ('brimstone', 2, ['fire_immune', 'slow_fall'], ab('fireball', 40, count=3), None, ['minecraft:blaze']),
    'the_bumblezone:beehemoth': ('ichor', 2, ['flight', 'slow_fall'], None, None, ['minecraft:bee']),
    'plantz:peashooter': ('ichor', 1, [], ab('arrows', 20, count=1), None, []),
    'plantz:chomper': ('ichor', 1, [], ab('pounce', 60, damage=8, speed=0.4), None, []),
    'plantz:gargantuar': ('grave', 2, ['knockback_immune'], ab('toss', 60, power=1.6), ab('roar', 200, radius=5, duration=30), []),
    'plantz:sunflower': ('ichor', 1, [], None, None, []),
    'orespawn:trex': ('warm', 2, ['knockback_immune'], ab('roar', 160, radius=8, duration=60), ab('charge', 100, damage=10, speed=1.6), []),
    'orespawn:cryolophosaurus': ('warm', 2, [], ab('charge', 60, damage=6, speed=1.7), None, []),
    'orespawn:baryonyx': ('warm', 2, ['water_breathing'], ab('pounce', 60, damage=6, speed=1.2), None, []),
    'orespawn:emperor_scorpion': ('ichor', 2, ['knockback_immune'], ab('pounce', 60, damage=8, speed=1.0), None, []),
    'orespawn:spider_robot': ('oil', 0, ['climb', 'no_fall_damage'], ab('web', 60, range=16), None, []),
    'orespawn:mothra': ('ichor', 2, ['flight', 'slow_fall'], None, None, []),
}

# extra blood types for modded creatures (optional entries, appended to the P5 tags)
BLOOD = {
    'cold': ['naturalist:alligator', 'naturalist:komodo_dragon', 'naturalist:snake', 'naturalist:lizard', 'naturalist:tortoise', 'naturalist:bass',
             'naturalist:catfish', 'naturalist:piranha', 'naturalist:anglerfish', 'naturalist:great_white_shark', 'naturalist:ray', 'naturalist:jellyfish',
             'cataclysm:deepling', 'cataclysm:lionfish', 'orespawn:baryonyx'],
    'ichor': ['plantz:peashooter', 'plantz:chomper', 'plantz:sunflower', 'plantz:marigold', 'plantz:wallnut', 'mowziesmobs:foliaath',
              'the_bumblezone:beehemoth', 'the_bumblezone:honey_slime', 'twilightforest:hedge_spider', 'twilightforest:king_spider', 'orespawn:emperor_scorpion',
              'orespawn:mothra', 'mutantmonsters:mutant_creeper', 'naturalist:butterfly', 'naturalist:snail'],
    'ender': ['mutantmonsters:mutant_enderman', 'cataclysm:ender_golem', 'cataclysm:endermaptera'],
    'brimstone': ['friendsandfoes:wildfire', 'brazil_legends:headless_mule', 'cataclysm:ignited_revenant', 'cataclysm:ignited_berserker'],
    'grave': ['mutantmonsters:mutant_zombie', 'twilightforest:rising_zombie', 'twilightforest:loyal_zombie', 'cataclysm:draugr', 'cataclysm:elite_draugr',
              'cataclysm:royal_draugr', 'plantz:browncoat', 'plantz:gargantuar', 'plantz:imp', 'plantz:newspaper_zombie', 'plantz:all_star'],
    'arcane': ['friendsandfoes:iceologer', 'friendsandfoes:illusioner', 'aether:valkyrie', 'irons_spellbooks:necromancer', 'irons_spellbooks:cryomancer',
               'irons_spellbooks:pyromancer', 'irons_spellbooks:archevoker', 'twilightforest:skeleton_druid'],
    'marrow': ['mutantmonsters:mutant_skeleton', 'cataclysm:koboleton', 'cataclysm:aptrgangr'],
    'oil': ['friendsandfoes:copper_golem', 'friendsandfoes:tuff_golem', 'twilightforest:carminite_golem', 'mowziesmobs:grottol', 'orespawn:spider_robot',
            'orespawn:ant_robot', 'orespawn:giant_robot', 'orespawn:robot_1', 'orespawn:robot_2', 'orespawn:robot_3', 'orespawn:robot_4', 'orespawn:robot_5',
            'cataclysm:coral_golem', 'cataclysm:netherite_ministrosity'],
    'ecto': ['twilightforest:wraith', 'orespawn:ghost', 'orespawn:ghost_skelly', 'mutantmonsters:endersoul_fragment'],
}
# modded bosses keep a thick hide too
THICK_HIDE = ['twilightforest:hydra', 'twilightforest:naga', 'twilightforest:lich', 'twilightforest:ur_ghast', 'twilightforest:alpha_yeti',
              'twilightforest:snow_queen', 'twilightforest:minoshroom', 'twilightforest:knight_phantom', 'cataclysm:ignis', 'cataclysm:the_harbinger',
              'cataclysm:netherite_monstrosity', 'cataclysm:ender_guardian', 'cataclysm:the_leviathan', 'cataclysm:ancient_remnant', 'cataclysm:maledictus',
              'cataclysm:scylla', 'mowziesmobs:frostmaw', 'mowziesmobs:ferrous_wroughtnaut', 'mowziesmobs:umvuthi', 'mowziesmobs:sculptor',
              'orespawn:godzilla', 'orespawn:the_king', 'orespawn:the_queen', 'orespawn:kraken', 'orespawn:mothra', 'aether:valkyrie_queen', 'aether:sun_spirit',
              'aether:slider', 'the_bumblezone:bee_queen', 'mutantmonsters:mutant_snow_golem']

GENES = {
    'moa_wings': ('Moa Wings', 'Asas de Moa'), 'saci_whirl': ('Saci Whirl', 'Redemoinho do Saci'), 'mule_fire': ('Mule Fire', 'Fogo da Mula'),
    'frost_breath': ('Frost Breath', 'Sopro Gélido'), 'mutant_might': ('Mutant Might', 'Força Mutante'), 'pachyderm_hide': ('Pachyderm Hide', 'Couro de Paquiderme'),
    'photosynthesis': ('Photosynthesis', 'Fotossíntese'), 'regrowth': ('Regrowth', 'Regeneração da Hidra'), 'blazing_soul': ('Blazing Soul', 'Alma Flamejante'),
    'atomic_breath': ('Atomic Breath', 'Sopro Atômico'),
}


# key: (event or None for the root, parent, icon, frame, en title, en description, pt title, pt description)
ADVANCEMENTS = {
    'root': (None, None, 'headinajar:surgical_steel_ingot', 'task', 'Orientation Day', 'Smelt a Surgical Steel Blend (iron ingot, bone meal and coal) into Surgical Steel',
             'Dia de Integração', 'Funda uma Mistura de Aço Cirúrgico (lingote de ferro, farinha de osso e carvão) em Aço Cirúrgico'),
    'signed': ('signed', 'root', 'headinajar:separation_chair', 'task', 'Signed in Triplicate', 'Sign Form 27-B in the Separation Chair',
               'Assinado em Três Vias', 'Assine o Formulário 27-B na Cadeira de Separação'),
    'separated': ('separated', 'signed', 'headinajar:life_support_jar', 'task', 'Head Start', 'Lose your head, on purpose',
                  'Cabeça Fresca', 'Perca a cabeça, de propósito'),
    'link_lost': ('link_lost', 'separated', 'headinajar:relay_goggles', 'task', 'Have You Tried Turning It Off and On Again', 'Lose the neuro-link',
                  'Já Tentou Desligar e Ligar de Novo?', 'Perca a conexão neural'),
    'botched': ('botched', 'separated', 'headinajar:replacement_blade', 'task', 'Cutting Remarks', 'Get a botched cut',
                'Corte de Gastos', 'Sofra um corte malfeito'),
    'headless_horseman': ('headless_horseman', 'separated', 'minecraft:saddle', 'goal', 'Headless Horseman', 'Ride a horse while linked',
                          'Cavaleiro Sem Cabeça', 'Monte um cavalo enquanto conectado'),
    'periscope': ('periscope', 'separated', 'headinajar:collar_periscope_stalk', 'task', 'Head and Shoulders Above', 'Fit a Periscope Stalk to your Collar Cam',
                  'De Cabeça Erguida', 'Instale uma Haste de Periscópio na sua Câmera de Colar'),
    'hosted': ('hosted', 'separated', 'headinajar:surgical_saw', 'task', 'Hostile Takeover', 'Pilot a mob host',
               'Aquisição Hostil', 'Pilote um hospedeiro'),
    'synced': ('synced', 'hosted', 'headinajar:neural_lace', 'goal', 'Fully Synced', 'Reach 100% sync with a host',
               'Sincronia Total', 'Chegue a 100% de sincronia com um hospedeiro'),
    'mula': ('mula', 'hosted', 'minecraft:fire_charge', 'challenge', 'Mula-sem-cabeça', 'Wear the Headless Mule',
             'Mula-sem-cabeça', 'Vista a Mula-sem-cabeça'),
    'out_for_blood': ('out_for_blood', 'root', 'headinajar:syringe', 'task', 'Out for Blood', 'Draw 10 Blood Samples',
                      'Sede de Sangue', 'Tire 10 Amostras de Sangue'),
    'spliced': ('spliced', 'out_for_blood', 'headinajar:serum_cassette', 'task', 'Splice of Life', 'Make your first serum',
                'Emenda Perfeita', 'Faça seu primeiro soro genético'),
    'mutated': ('mutated', 'spliced', 'headinajar:gene_wash', 'task', 'Mutually Assured', 'Suffer a mutation',
                'Mutação Garantida', 'Sofra uma mutação'),
    'synergy': ('synergy', 'spliced', 'headinajar:bio_gel', 'goal', 'Synergy!', 'Inject a serum with a synergy pair',
                'Sinergia!', 'Injete um soro com um par sinérgico'),
    'chimera': ('chimera', 'spliced', 'headinajar:chimera_frame', 'challenge', 'Chimera', 'Give a mecha five or more genes',
                'Quimera', 'Dê cinco ou mais genes a um mecha'),
    'legendary': ('legendary', 'spliced', 'minecraft:dragon_breath', 'challenge', 'Legendary Genes', 'Read a Legendary gene to 100%',
                  'Genes Lendários', 'Leia um gene Lendário até 100%'),
    'assembled': ('assembled', 'root', 'headinajar:assembly_gantry', 'task', 'Corporate Ladder', 'Assemble a mecha',
                  'Subindo na Carreira', 'Monte um mecha'),
    'parachute': ('parachute', 'assembled', 'minecraft:feather', 'task', 'Golden Parachute', 'Be thrown clear of a dying mecha',
                  'Paraquedas Dourado', 'Seja ejetado de um mecha à beira da destruição'),
    'frequent_flyer': ('frequent_flyer', 'assembled', 'headinajar:kite_frame', 'goal', 'Frequent Flyer', 'Log 30 seconds in the air in a Kite',
                       'Milhas Acumuladas', 'Acumule 30 segundos no ar numa Pipa'),
    'heavy_head': ('heavy_head', 'assembled', 'headinajar:colossus_frame', 'goal', 'Heavy Is the Head', 'Pilot a Colossus or an Executive',
                   'Pesada É a Cabeça', 'Pilote um Colosso ou um Executivo'),
    'chairman': ('chairman', 'heavy_head', 'headinajar:executive_frame', 'challenge', 'Chairman of the Board', 'Build an Executive',
                 'Presidente do Conselho', 'Construa um Executivo'),
    'staffed': ('staffed', 'root', 'headinajar:employee_mount', 'task', 'Employee of the Month', 'Staff a machine with a jarred villager',
                'Funcionário do Mês', 'Coloque um aldeão no pote para trabalhar numa máquina'),
    'board_meeting': ('board_meeting', 'staffed', 'headinajar:cerebral_array', 'goal', 'Board Meeting', 'Run a Cerebral Array with five heads',
                      'Reunião do Conselho', 'Faça uma Matriz Cerebral funcionar com cinco cabeças'),
    'brain_trust': ('brain_trust', 'board_meeting', 'headinajar:board_room_core', 'challenge', 'Brain Trust', 'A Cerebral Array making 500 FE/t',
                    'Cérebros da Empresa', 'Uma Matriz Cerebral gerando 500 FE/t'),
    'socket_pulse': ('socket_pulse', 'root', 'headinajar:neural_socket', 'task', 'Ask Me Anything', 'Send a redstone pulse from a Neural Socket',
                     'Pode Perguntar', 'Envie um pulso de redstone de um Soquete Neural'),
}

HANDBOOK = {
    # category: (en name, en desc, pt name, pt desc, icon, sortnum)
    'hosting': ('Bodies and Hosting', 'Borrowing bodies that were never yours.', 'Corpos e Hospedagem', 'Pegando emprestados corpos que nunca foram seus.',
                'headinajar:surgical_saw', 4),
    'mecha': ('Mecha Division', 'Frames, modules and the Gantry.', 'Divisão de Mechas', 'Estruturas, módulos e o Pórtico.', 'headinajar:assembly_gantry', 5),
    'genetics': ('Genetics Department', 'Blood, DNA and serums.', 'Departamento de Genética', 'Sangue, DNA e soros.', 'headinajar:gene_splicer', 6),
    'hr': ('Human Resources', 'Heads at work.', 'Recursos Humanos', 'Cabeças trabalhando.', 'headinajar:employee_mount', 7),
}
# (category, entry, icon, en name, en pages, pt name, pt pages)
ENTRIES = [
    ('separation', 'getting_home', 'headinajar:wheels', 'Getting Home',
     ['Walk too far and the link breaks: you wake up in your head. A jar with the $(item)Wheels$() module drives at half walking speed: press $(k:key.headinajar.drive). Without wheels the same key tips the jar on its side so it can roll, very, very slowly.',
      'Stranded anyway? Press $(l)Courier$() in the jar menu. A Noggin & Sons courier delivers your body to your head in 30 seconds, from anywhere, for 20 kFE of charge (or all you have, if less). Then press $(k:key.headinajar.swap).'],
     'Voltando para Casa',
     ['Ande longe demais e a conexão cai: você acorda na sua cabeça. Um pote com o módulo $(item)Rodinhas$() anda à metade da velocidade de caminhada: aperte $(k:key.headinajar.drive). Sem rodinhas, a mesma tecla tomba o pote de lado para ele rolar, bem, bem devagar.',
      'Sem saída mesmo assim? Aperte $(l)Entregador$() no menu do pote. Um entregador da Noggin & Filhos leva seu corpo até sua cabeça em 30 segundos, de qualquer lugar, por 20 kFE de carga (ou toda a que houver, se for menos). Depois aperte $(k:key.headinajar.swap).']),
    ('hosting', 'the_saw', 'headinajar:surgical_saw', 'The Surgical Saw',
     ['Weaken a creature below a quarter of its health and use the $(item)Surgical Saw$() on it: its head comes off and its body stays up, ready for a jar. With an empty $(item)Life Support Jar$() in your off hand you keep the $(l)head$() instead.',
      'As a jar, use a host to climb on. It moves where you look, its own abilities answer $(k:key.headinajar.ability) and $(k:key.headinajar.secondary), and it slowly rots unless you embalm it. Sync grows the longer you pilot it.'],
     'A Serra Cirúrgica',
     ['Enfraqueça uma criatura abaixo de um quarto da vida e use a $(item)Serra Cirúrgica$(): a cabeça sai e o corpo fica de pé, pronto para um pote. Com um $(item)Pote de Suporte Vital$() vazio na outra mão, você fica com a $(l)cabeça$().',
      'Como pote, use um hospedeiro para subir nele. Ele anda para onde você olha, suas habilidades respondem a $(k:key.headinajar.ability) e $(k:key.headinajar.secondary), e ele apodrece aos poucos se você não o embalsamar. A sincronia cresce quanto mais você pilota.']),
    ('hosting', 'other_worlds', 'minecraft:fire_charge', 'Bodies from Other Worlds',
     ['Creatures from other mods in the pack make hosts too: the $(l)Headless Mule$() takes a jar without any sawing, Moas glide, Rhinos charge, Mutant Zombies roar and Peashooters shoot. If a mod is missing, its creatures are simply skipped.'],
     'Corpos de Outros Mundos',
     ['Criaturas de outros mods do pacote também viram hospedeiros: a $(l)Mula-sem-cabeça$() aceita um pote sem serra nenhuma, Moas planam, Rinocerontes atacam, Zumbis Mutantes rugem e Ervilhas atiram. Se um mod faltar, suas criaturas são simplesmente ignoradas.']),
    ('mecha', 'frames', 'headinajar:hopper_frame', 'Frames',
     ['Put a frame on the $(item)Assembly Gantry$(), fit modules in the slots of the right kind and press Assemble. The $(l)Hopper$() is light, the $(l)Bulwark$() tough, the $(l)Wheelhouse$() a car, the $(l)Skitter$() climbs walls, the $(l)Kite$() flies and the $(l)Colossus$() carries shoulder weapons.',
      'The $(l)Chimera$() has three Gene Ports and the $(l)Executive$() four. Weight against the load rating sets your speed; heat past the cap vents the mecha; an empty battery means limp mode.'],
     'Estruturas',
     ['Coloque uma estrutura no $(item)Pórtico de Montagem$(), encaixe módulos nos espaços certos e aperte Montar. O $(l)Saltador$() é leve, o $(l)Baluarte$() resistente, a $(l)Casa sobre Rodas$() é um carro, o $(l)Rastejador$() sobe paredes, a $(l)Pipa$() voa e o $(l)Colosso$() carrega armas nos ombros.',
      'A $(l)Quimera$() tem três Portas Genéticas e o $(l)Executivo$(), quatro. O peso contra a capacidade define a velocidade; calor acima do limite faz o mecha liberar vapor; bateria vazia é modo de emergência.']),
    ('mecha', 'controls', 'headinajar:mecha_missile_pod', 'Piloting',
     ['The mouse buttons work the arms (and the shoulder weapons with them). $(k:key.headinajar.ability) fires the selected utility module (Ender Satchel, Smoke Mortar, Missile Pod, Workbench, Ore Scanner) and $(k:key.headinajar.secondary) picks the next. Sneak with them to fire the mecha\'s genes. Hold Sneak + $(k:key.headinajar.jar_menu) to eject.'],
     'Pilotagem',
     ['Os botões do mouse controlam os braços (e as armas dos ombros junto). $(k:key.headinajar.ability) dispara o módulo utilitário escolhido (Bolsa do Ender, Morteiro de Fumaça, Lança-Mísseis, Bancada, Scanner de Minério) e $(k:key.headinajar.secondary) escolhe o próximo. Agachado, eles disparam os genes do mecha. Segure Agachar + $(k:key.headinajar.jar_menu) para ejetar.']),
    ('genetics', 'blood', 'headinajar:syringe', 'Blood and DNA',
     ['Draw blood with a $(item)Syringe$(). Bloodless creatures need a $(item)Marrow Drill$(), $(item)Oil Tap$() or $(item)Ecto Trap$(), and bosses a $(item)Leviathan Needle$(). Healthy creatures give better samples. Spin a sample in the $(item)Centrifuge$(), then read the DNA in the $(item)DNA Sequencer$(): call the bases yourself for a better read.'],
     'Sangue e DNA',
     ['Tire sangue com uma $(item)Seringa$(). Criaturas sem sangue pedem uma $(item)Broca de Medula$(), $(item)Torneira de Óleo$() ou $(item)Armadilha de Ecto$(), e chefões uma $(item)Agulha Leviatã$(). Criaturas saudáveis dão amostras melhores. Gire a amostra na $(item)Centrífuga$() e leia o DNA no $(item)Sequenciador de DNA$(): chamar as bases você mesmo dá uma leitura melhor.']),
    ('genetics', 'splicing', 'headinajar:gene_splicer', 'Splicing',
     ['The $(item)Gene Splicer$() puts up to three genes from your library into a $(item)Serum Cassette$(). Watch the stability before you splice: conflicting genes cost stability, synergies add some, and a catalyst helps. Below 60 a serum may mutate whatever it goes into.',
      'Serums go into your own body at the $(item)Injection Rig$(), into a host by hand, or into a Chimera\'s Gene Ports. Mismatched blood can be rejected; mecha never reject. $(item)Gene Wash$() starts you over.'],
     'Emendas',
     ['O $(item)Emendador de Genes$() junta até três genes da sua biblioteca num $(item)Cartucho de Soro Genético$(). Olhe a estabilidade antes de emendar: genes em conflito custam estabilidade, sinergias somam e um catalisador ajuda. Abaixo de 60, o soro pode causar mutações em quem o receber.',
      'Soros vão para o seu próprio corpo na $(item)Estação de Injeção$(), para um hospedeiro à mão, ou para as Portas Genéticas de uma Quimera. Sangue incompatível pode ser rejeitado; mechas nunca rejeitam. A $(item)Lavagem Genética$() recomeça tudo.']),
    ('hr', 'staff', 'headinajar:employee_mount', 'Villager Staff',
     ['Hang a jarred villager on an $(item)Employee Mount$() against a machine. Librarians read DNA better, Clerics steady the Splicer, Fishermen speed the Saline Mixer, Masons spare the blade, smiths stretch your plating, Cartographers widen Relay Masts, Butchers fatten the Leech Jar, Farmers save broth and Fletchers speed turrets.',
      'Staff cost one emerald a day from a chest touching the mount. Unpaid staff work at half the bonus and say so. Nitwits do nothing at all, loudly.'],
     'Funcionários Aldeões',
     ['Pendure um aldeão no pote num $(item)Suporte de Funcionário$() encostado numa máquina. Bibliotecários leem DNA melhor, Clérigos estabilizam o Emendador, Pescadores aceleram o Misturador de Soro, Pedreiros poupam a lâmina, ferreiros esticam a blindagem, Cartógrafos ampliam os Mastros, Açougueiros engordam o Pote de Sanguessugas, Fazendeiros economizam caldo e Flecheiros aceleram torretas.',
      'Cada funcionário custa uma esmeralda por dia de um baú encostado no suporte. Sem pagamento, trabalham pela metade e reclamam. Os bobos não fazem nada, em voz alta.']),
    ('hr', 'heads_at_work', 'headinajar:cerebral_array', 'Heads at Work',
     ['A $(item)Turret Pedestal$() turns a jarred mob head into a guard that fights the way the mob did. A $(item)Cerebral Array$() racks nine heads that make power by thinking about $(item)Nutrient Broth$(); five or more hold a Board Meeting. A $(item)Neural Socket$() lets your own jar drive six redstone channels, or runs a mob head as a little program.'],
     'Cabeças Trabalhando',
     ['Um $(item)Pedestal de Torreta$() transforma a cabeça de uma criatura num guarda que luta como ela lutava. A $(item)Matriz Cerebral$() junta nove cabeças que geram energia pensando em $(item)Caldo Nutritivo$(); com cinco ou mais, há Reunião do Conselho. O $(item)Soquete Neural$() deixa seu próprio pote controlar seis canais de redstone, ou roda a cabeça de uma criatura como um programinha.']),
]

LANG7 = [
    ('key.headinajar.drive', 'Jar wheels / tip over', 'Rodinhas / tombar o pote'),
    ('message.headinajar.wheels_down', 'Wheels down', 'Rodinhas abaixadas'),
    ('message.headinajar.wheels_up', 'Wheels up', 'Rodinhas recolhidas'),
    ('message.headinajar.tipped', 'You tip the jar over. Roll, very slowly. The same key again sits you up.',
     'Você tomba o pote. Role, bem devagar. A mesma tecla de novo te endireita.'),
    ('message.headinajar.upright', 'Upright again', 'De pé de novo'),
    ('gui.headinajar.courier', 'Courier', 'Entregador'),
    ('gui.headinajar.courier.tooltip', 'Stranded? A Noggin & Sons courier brings your body to your head in 30 seconds, from anywhere. Fee: 20 kFE of charge, or all you have if less.',
     'Sem saída? Um entregador da Noggin & Filhos traz seu corpo até sua cabeça em 30 segundos, de qualquer lugar. Taxa: 20 kFE de carga, ou toda a que houver se for menos.'),
    ('courier.headinajar.booked', 'Courier booked: your body arrives in 30 seconds', 'Entregador chamado: seu corpo chega em 30 segundos'),
    ('courier.headinajar.eta', 'Courier: %s seconds', 'Entregador: %s segundos'),
    ('courier.headinajar.delivered', 'Delivery for you: one body, slightly used. Press Swap to get back in.',
     'Entrega para você: um corpo, seminovo. Aperte Trocar para voltar para ele.'),
    ('courier.headinajar.lost', 'The courier could not find your body. Fee refunded. A %s can print a new one.',
     'O entregador não encontrou seu corpo. Taxa devolvida. Uma %s imprime um novo.'),
    ('courier.headinajar.only_jars', 'Only a head in a jar can book the courier', 'Só uma cabeça no pote pode chamar o entregador'),
    ('courier.headinajar.no_body', 'You have no body to deliver', 'Você não tem corpo para entregar'),
    ('courier.headinajar.already_here', 'Your body is right here', 'Seu corpo está bem aqui'),
    ('courier.headinajar.on_its_way', 'The courier is already on the way', 'O entregador já está a caminho'),
    ('fml.menu.mods.info.description.headinajar',
     'Take your head off and live in a life-support jar: drive your own headless body over a neuro-link, borrow mob bodies, build mecha, splice genes and put jarred heads to work.',
     'Tire a cabeça e viva num pote de suporte vital: controle seu próprio corpo sem cabeça por uma conexão neural, pegue corpos de criaturas emprestados, construa mechas, emende genes e coloque cabeças no pote para trabalhar.'),
    ('jei.headinajar.separation_chair', 'Load a jar, goggles, Collar Cam and four bottles of Cranial Saline, power it, sit down and sign Form 27-B. It takes your head off and leaves your body linked to it.',
     'Coloque um pote, óculos, Câmera de Colar e quatro frascos de Soro Craniano, dê energia, sente-se e assine o Formulário 27-B. Ela tira sua cabeça e deixa seu corpo conectado a ela.'),
    ('jei.headinajar.life_support_cradle', 'Docks your head: use it with an empty hand while carrying the head in a Head Satchel. Keeps the head\'s saline, charge and glass topped up from its own tank and power. The link reaches no further than from a placed head.',
     'Acomoda sua cabeça: use com a mão vazia levando a cabeça na Bolsa de Cabeça. Mantém soro, carga e vidro da cabeça cheios com o próprio tanque e energia. A conexão não vai mais longe do que de uma cabeça no chão.'),
    ('jei.headinajar.saline_mixer', 'Water (by bucket or pipe), sugar, kelp and redstone with 10 FE/t make Cranial Saline. Glass bottles in its slot are filled from its tank; an empty bucket used on it takes a Bucket of Cranial Saline.',
     'Água (por balde ou cano), açúcar, alga e redstone com 10 FE/t fazem Soro Craniano. Frascos de vidro no espaço são enchidos pelo tanque; um balde vazio usado nele sai com um Balde de Soro Craniano.'),
    ('jei.headinajar.body_printer', 'Prints a new headless body for a head that has lost its own: load 16 rotten flesh, 8 bone blocks, 4 redstone blocks and 8 slime balls, pour in 2,000 mB of Cranial Saline (bottles or buckets) and store 200 kFE. Then the jar presses Print.',
     'Imprime um corpo sem cabeça novo para uma cabeça que perdeu o seu: coloque 16 carnes podres, 8 blocos de osso, 4 blocos de redstone e 8 bolas de slime, despeje 2.000 mB de Soro Craniano (frascos ou baldes) e guarde 200 kFE. Depois, o pote aperta Imprimir.'),
    ('jei.headinajar.surgical_saw', 'Use on a creature below 25% health: its body becomes a host. With an empty jar in the other hand you keep its head instead.',
     'Use numa criatura com menos de 25% de vida: o corpo vira hospedeiro. Com um pote vazio na outra mão, você fica com a cabeça.'),
    ('jei.headinajar.assembly_gantry', 'Put a frame and modules in, then Assemble (10,000 FE). Take Apart pulls a mecha back into the slots; Repair spends Mecha Plating.',
     'Coloque uma estrutura e módulos e aperte Montar (10.000 FE). Desmontar devolve o mecha aos espaços; Reparar gasta Placa de Mecha.'),
    ('jei.headinajar.centrifuge', 'Spins a Blood Sample into a DNA Fraction and a Plasma Vial. Dragon\'s breath gives Ender Dragon DNA.',
     'Gira uma Amostra de Sangue em Fração de DNA e Frasco de Plasma. Bafo do dragão dá DNA do Dragão do Ender.'),
    ('jei.headinajar.dna_sequencer', 'Reads a DNA Fraction into your gene library, a share at a time. Auto takes 10 s at 60% accuracy; calling the twelve bases yourself is instant, 50 to 100% by how many you get right. Each read costs 4,000 FE.',
     'Lê uma Fração de DNA para a sua biblioteca genética, uma parte por vez. O automático leva 10 s com 60% de precisão; chamar as doze bases você mesmo é na hora, de 50 a 100% conforme os acertos. Cada leitura custa 4.000 FE.'),
    ('jei.headinajar.gene_splicer', 'Splices up to three library genes (at least 30% read) into a Serum Cassette with Bio-Gel, a glass bottle and an optional catalyst.',
     'Emenda até três genes da biblioteca (lidos pelo menos 30%) num Cartucho de Soro Genético com Biogel, um frasco de vidro e um catalisador opcional.'),
    ('jei.headinajar.injection_rig', 'Injects the serum in its slot into your own body (with your head on, or linked).',
     'Injeta o soro do espaço no seu próprio corpo (com cabeça, ou conectado).'),
    ('jei.headinajar.leech_jar', 'Takes a small blood sample every 30 s from a creature standing next to it.', 'Tira uma pequena amostra de sangue a cada 30 s de uma criatura ao lado.'),
    ('jei.headinajar.syringe', 'Use on a living creature to draw a Blood Sample. Healthier creatures give better quality.',
     'Use numa criatura viva para tirar uma Amostra de Sangue. Criaturas mais saudáveis dão qualidade melhor.'),
    ('jei.headinajar.serum_cassette', 'Made at the Gene Splicer. Use it on a host or on a mecha with a Gene Port, or load it into an Injection Rig.',
     'Feito no Emendador de Genes. Use num hospedeiro ou num mecha com Porta Genética, ou coloque numa Estação de Injeção.'),
    ('jei.headinajar.turret_pedestal', 'Fit a Jarred Head: it guards 16 blocks around the way that mob fights. Skeleton, Stray and Bogged heads need arrows in a chest below or beside it. Power: 1 FE/t on watch, 10 FE/t with a target.',
     'Encaixe uma Cabeça no Pote: ela guarda 16 blocos em volta do jeito que a criatura luta. Cabeças de esqueleto, errante e pantanoso precisam de flechas num baú embaixo ou ao lado. Energia: 1 FE/t de vigia, 10 FE/t com alvo.'),
    ('jei.headinajar.cerebral_array', 'Up to nine jarred heads make FE from Nutrient Broth. Five or more hold a Board Meeting (+10%).',
     'Até nove cabeças no pote geram FE com Caldo Nutritivo. Cinco ou mais fazem Reunião do Conselho (+10%).'),
    ('jei.headinajar.employee_mount', 'Hang a jarred villager on a machine: their profession gives that machine a bonus, for an emerald a day.',
     'Pendure um aldeão no pote numa máquina: a profissão dele dá um bônus à máquina, por uma esmeralda por dia.'),
    ('jei.headinajar.neural_socket', 'Plug your jar in to drive six redstone channels, or let a mob head run a program.',
     'Conecte seu pote para controlar seis canais de redstone, ou deixe a cabeça de uma criatura rodar um programa.'),
    ('jei.headinajar.entangled_pair', 'Use it to split it into two shards. Craft one with your goggles and the other with your Collar Cam: the link then crosses dimensions.',
     'Use para dividir em dois fragmentos. Combine um com seus óculos e o outro com sua Câmera de Colar: a conexão passa a atravessar dimensões.'),
    ('jei.headinajar.emp_grenade', 'Throw it: links break (unless shielded), jars and mecha lose power, turrets go dark.',
     'Arremesse: conexões caem (sem blindagem), potes e mechas perdem energia, torretas desligam.'),
    ('jei.headinajar.jarred_head', 'A mob\'s head in a jar, from the Surgical Saw with an empty jar in your off hand. A placed villager keeps trading. Heads work in Turret Pedestals, the Cerebral Array and Neural Sockets; villagers also on an Employee Mount.',
     'A cabeça de uma criatura num pote, da Serra Cirúrgica com um pote vazio na outra mão. Um aldeão colocado continua negociando. Cabeças trabalham em Pedestais de Torreta, na Matriz Cerebral e em Soquetes Neurais; aldeões também num Suporte de Funcionário.'),
    ('jei.headinajar.frame', 'Assemble it at the Assembly Gantry with modules in the slots of the right kind.',
     'Monte no Pórtico de Montagem com módulos nos espaços do tipo certo.'),
    ('jade.headinajar.mecha', '%s kFE  heat %s%%  %s / %s kg', '%s kFE  calor %s%%  %s / %s kg'),
    ('jade.headinajar.host', 'Freshness %s%%, sync %s%%', 'Frescor %s%%, sincronia %s%%'),
    ('jade.headinajar.body', 'Body of %s', 'Pertence a %s'),
    ('jade.headinajar.corpse', 'Corpse of %s', 'Cadáver de %s'),
    ('jade.headinajar.genes', 'Genes: %s', 'Genes: %s'),
    ('jade.headinajar.staff', 'On staff: %s', 'Funcionário: %s'),
    ('config.jade.plugin_headinajar.mecha', 'Mecha status', 'Estado do mecha'),
    ('config.jade.plugin_headinajar.host', 'Host freshness and sync', 'Frescor e sincronia do hospedeiro'),
    ('config.jade.plugin_headinajar.body', 'Whose body', 'De quem é o corpo'),
    ('config.jade.plugin_headinajar.genes', 'Genes carried', 'Genes carregados'),
    ('config.jade.plugin_headinajar.heads', 'Heads at work', 'Cabeças trabalhando'),
]


def opt(ids):
    return [{'id': i, 'required': False} for i in ids]


def merge_tag(write, path, extra):
    full = os.path.join(RES, path)
    values = json.load(open(full, encoding='utf-8'))['values'] if os.path.exists(full) else []
    seen = {v if isinstance(v, str) else v['id'] for v in values}
    write(path, {'values': values + [e for e in extra if (e if isinstance(e, str) else e['id']) not in seen]})


def run(EN, PT, write, shaped):
    for entity, (blood, decay, flags, primary, secondary, neutral) in HOSTS.items():
        ns, name = entity.split(':')
        body = {'entity': entity, 'blood': blood, 'decay_per_minute': float(decay), 'flags': flags, 'neutral_to': neutral,
                'neoforge:conditions': [{'type': 'neoforge:mod_loaded', 'modid': ns}]}
        if primary:
            body['primary'] = primary
        if secondary:
            body['secondary'] = secondary
        write(f'data/headinajar/headinajar/host/compat/{ns}/{name}.json', body)
    for blood, ids in BLOOD.items():
        merge_tag(write, f'data/headinajar/tags/entity_type/blood/{blood}.json', opt(ids))
    merge_tag(write, 'data/headinajar/tags/entity_type/thick_hide.json', opt(THICK_HIDE))
    for gene, (en, pt) in GENES.items():
        EN[f'gene.headinajar.{gene}'], PT[f'gene.headinajar.{gene}'] = en, pt
    for key, en, pt in LANG7:
        EN[key], PT[key] = en, pt

    # advancements
    for key, (event, parent, icon, frame, en_t, en_d, pt_t, pt_d) in ADVANCEMENTS.items():
        display = {'icon': {'id': icon}, 'title': {'translate': f'advancements.headinajar.{key}.title'},
                   'description': {'translate': f'advancements.headinajar.{key}.description'}, 'frame': frame,
                   'show_toast': True, 'announce_to_chat': True, 'hidden': False}
        if event is None:
            display['background'] = 'minecraft:textures/block/white_terracotta.png'
            criteria = {'steel': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': 'headinajar:surgical_steel_ingot'}]}}}
        else:
            criteria = {'done': {'trigger': 'headinajar:event', 'conditions': {'event': event}}}
        body = {'display': display, 'criteria': criteria}
        if parent:
            body['parent'] = f'headinajar:{parent}'
        write(f'data/headinajar/advancement/{key}.json', body)
        EN[f'advancements.headinajar.{key}.title'], EN[f'advancements.headinajar.{key}.description'] = en_t, en_d
        PT[f'advancements.headinajar.{key}.title'], PT[f'advancements.headinajar.{key}.description'] = pt_t, pt_d

    # Curios: the satchel on the belt. Goggles are the jar's (the Separation Chair fits them), so no head slot is
    # offered for them: a pair worn there would have done nothing.
    write('data/curios/tags/item/belt.json', {'values': ['headinajar:head_satchel']})
    write('data/headinajar/curios/entities/head_office.json', {'entities': ['minecraft:player'], 'slots': ['belt']})

    # sounds: subtitles (the files and sounds.json come from tools/make_sounds.py)
    import importlib.util
    spec = importlib.util.spec_from_file_location('make_sounds', os.path.join(os.path.dirname(__file__), 'make_sounds.py'))
    sounds = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(sounds)
    for name, (_d, _e, en, pt) in sounds.SOUNDS.items():
        EN[f'subtitles.headinajar.{name}'], PT[f'subtitles.headinajar.{name}'] = en, pt

    # the rest of the handbook
    for lang_code, idx in (('en_us', 0), ('pt_br', 1)):
        base = f'assets/headinajar/patchouli_books/handbook/{lang_code}'
        for cid, (en_n, en_d, pt_n, pt_d, icon, order) in HANDBOOK.items():
            write(f'{base}/categories/{cid}.json', {'name': en_n if idx == 0 else pt_n, 'description': en_d if idx == 0 else pt_d,
                                                    'icon': icon, 'sortnum': order})
        for cat, eid, icon, en_n, en_pages, pt_n, pt_pages in ENTRIES:
            pages = en_pages if idx == 0 else pt_pages
            write(f'{base}/entries/{cat}/{eid}.json', {'name': en_n if idx == 0 else pt_n, 'icon': icon, 'category': f'headinajar:{cat}',
                                                        'pages': [{'type': 'patchouli:text', 'text': t} for t in pages]})

"""Lines for the 0.6.2 release (DNA for every mob): the forty new genes' names, descriptions and costs, the named gene
pairs and what the Splicer says about each, the Staff Directory's hint for every vanilla creature, the Body Printer's
specimen lines, and the release's memos and advancements, in English and Brazilian Portuguese.

Data only. gen_p39_dna.py imports it, makes the language keys and checks the widths.

Shapes:
- GENES: {gene id: (en name, pt name, en description, pt description, en cost line, pt cost line)}, in the order the
  genes are appended to Gene. A cost line is the whole grey line a cassette shows ("Cost: ...").
- a pair: (gene a, gene b, en name, pt name, en comment, pt comment); a conflict's comment is what the Splicer says.
- HINTS: {species path: (en where, en jar, pt where, pt jar)}: two rows for a blank Directory cell. The second always
  says which jar the head needs (the saw's tags: saw/head_only, saw/reinforced_jar, saw/executive_jar).
- a plain line is (key, en, pt).
Gene ids are Gene.id(); species are vanilla entity paths (namespace minecraft).
"""

# ---------------------------------------------------------------------------------------------------------------
# The forty genes. Numbers are base numbers (bodies and hosts take 70% of them, as every gene does), with the plan's
# fixes: Mimicry reaches 32; Rampage tops out at +3; Steady Aim has no flat +1; Snow Pounce knocks back and slows;
# Spit Gland's calm cannot be renewed for 12 s; Phase costs a mecha 3% of its health; Fang Line and Levitation Bolt
# cost a share of the frame's heat; the real costs of Brute Strength, Rehabilitated, Potion Tolerance and Stew Glands;
# Weeping Glands without the laser; Roll Up curls low (no ball); Tadpole Tail needs no bucket.
GENES = {
    # Cow
    'ruminant_gut': (
        'Ruminant Gut', 'Pança Ruminante',
        'Food fills a quarter more. Sneak and use grass, empty-handed, to graze. Wheat is lunch. A host on grass holds off rot.',
        'Comida rende 25% a mais. De mão vazia, agache e use a grama para pastar. Trigo é almoço. Na grama, o hospedeiro não piora.',
        'Cost: you moo when hurt.',
        'Custo: você muge quando se machuca.'),
    # Mooshroom
    'stew_glands': (
        'Stew Glands', 'Glândulas de Ensopado',
        'Small flowers are food, with their stew effect, most for three times as long. Sneak and use a bowl: stew, every 5 min.',
        'Flores pequenas viram comida, com o efeito do ensopado, em geral triplicado. Agache e use uma tigela: ensopado a cada 5 min.',
        'Cost: stews fill half, and a wither rose withers you.',
        'Custo: ensopado sustenta metade, e rosa de wither faz mal.'),
    # Pig
    'swine_appetite': (
        'Swine Appetite', 'Apetite Suíno',
        'Rotten flesh, spider eyes, raw chicken, poisonous potatoes and pufferfish never make you sick. Rotten flesh fills 2 more.',
        'Carne podre, olho de aranha, frango cru, batata venenosa e baiacu nunca fazem mal. Carne podre sustenta 2 a mais.',
        'Cost: you oink at every bite.',
        'Custo: você grunhe a cada mordida.'),
    # Sheep
    'fleece': (
        'Fleece', 'Lã',
        'You grow a fleece in 5 min. Fleeced, your steps, landings and meals make no vibrations, and powder snow cannot freeze you.',
        'Você cria lã em 5 min. Com lã, seus passos, quedas e refeições não fazem vibração, e a neve fofa não congela você.',
        'Cost: fleeced, fire burns longer and 25% harder.',
        'Custo: com lã, o fogo dura mais e queima 25% mais.'),
    # Goat
    'mountain_hooves': (
        'Mountain Hooves', 'Cascos de Montanha',
        'You walk up full blocks (not while sneaking), falls of up to 7 blocks do not hurt, and you walk on powder snow.',
        'Você sobe blocos inteiros andando (não agachado), quedas de até 7 blocos não machucam, e você anda na neve fofa.',
        'Cost: hooves take no boots.',
        'Custo: casco não calça bota.'),
    # Vex
    'phase': (
        'Phase', 'Atravessar',
        'Active: dash 5 blocks along your look, through up to 2 blocks of wall, into open space. 6 s cooldown. Claims hold.',
        'Ativo: avance 5 blocos para onde olha, por até 2 blocos de parede, até um espaço livre. Recarga de 6 s. Respeita terrenos protegidos.',
        'Cost: each phase takes 2 health from a host, 3% from a mecha.',
        'Custo: travessia tira 2 de vida do hospedeiro, 3% do mecha.'),
    # Panda
    'bamboo_diet': (
        'Bamboo Diet', 'Dieta de Bambu',
        'Bamboo and sugar cane are food, eaten at once. Cake slices fill double.',
        'Bambu e cana-de-açúcar viram comida, comidos na hora. Fatia de bolo sustenta o dobro.',
        'Cost: you sit down to eat.',
        'Custo: você senta para comer.'),
    # Fox
    'snow_pounce': (
        'Snow Pounce', 'Bote na Neve',
        'Active: leap to the block you look at within 8. Creatures where you land take 3, are knocked back and slowed. 4 s cooldown.',
        'Ativo: salte até o bloco para onde olha, a até 8. Quem estiver onde você cai leva 3, é empurrado e fica lento. Recarga de 4 s.',
        'Cost: by day under open sky, the cooldown is 8 s.',
        'Custo: de dia a céu aberto, a recarga é de 8 s.'),
    # Bee
    'pollinator': (
        'Pollinator', 'Polinizador',
        'Crops and saplings you pass get a growth tick, each once a minute at most. Bees never anger at you. Honey fills 4 more.',
        'Plantações e mudas no caminho crescem, cada uma até uma vez por minuto. Abelhas não se irritam com você. Mel sustenta 4 a mais.',
        'Cost: 20% slower in the rain.',
        'Custo: 20% mais lento na chuva.'),
    # Polar Bear
    'arctic_coat': (
        'Arctic Coat', 'Pelagem Ártica',
        'You never freeze. Slowness on you lasts half as long. You swim 30% faster.',
        'Você nunca congela. Lentidão dura metade do tempo em você. Você nada 30% mais rápido.',
        'Cost: heat and the Nether slow you and make you hungry.',
        'Custo: o calor e o Nether deixam você lento e faminto.'),
    # Armadillo
    'roll_up': (
        'Roll Up', 'Retranca',
        'Sneak still for a second and you curl up low: 60% less damage and no knockback. You shed a scute every 20 minutes.',
        'Agachado e parado por um segundo, você se encolhe rente ao chão: 60% menos dano e nada de empurrão. Solta uma escama a cada 20 min.',
        'Cost: curled, you cannot move, hit or use anything.',
        'Custo: encolhido, você não anda, não bate e não usa nada.'),
    # Sniffer
    'keen_nose': (
        'Keen Nose', 'Faro Fino',
        'Creatures whose gene you have not finished reading leave a scent only you see. Suspicious sand puffs. Sneak on grass for seeds.',
        'Criatura com gene que você não terminou de ler deixa um rastro que só você vê. Areia suspeita solta pó. Agache na grama: sementes.',
        'Cost: you sneeze near flowers.',
        'Custo: você espirra perto de flores.'),
    # Parrot
    'mimicry': (
        'Mimicry', 'Imitação',
        'Every 8 s, you imitate a hostile within 32 blocks that you cannot see. Out loud. A creeper\'s hiss, from behind the wall.',
        'A cada 8 s, você imita em voz alta um hostil a até 32 blocos que você não vê. O chiado de um creeper, atrás da parede.',
        'Cost: cookies poison you.',
        'Custo: biscoito envenena você.'),
    # Frog
    'tongue': (
        'Tongue', 'Língua',
        'Active: lick what you look at within 8. Items and creatures up to chicken size come to you; a small slime is eaten. 2 s cooldown.',
        'Ativo: lamba o que olha, a até 8. Itens e bichos do tamanho de uma galinha vêm até você; slime pequeno é engolido. Recarga de 2 s.',
        'Cost: you croak when you jump.',
        'Custo: você coaxa quando pula.'),
    # Tadpole
    'tadpole_tail': (
        'Tadpole Tail', 'Cauda de Girino',
        'You swim 30% faster.',
        'Você nada 30% mais rápido.',
        'Cost: none. It is a tadpole.',
        'Custo: nenhum. É um girino.'),
    # Pufferfish
    'puffer': (
        'Puffer', 'Baiacu',
        'A hostile within 2, or a melee hit, puffs you up for 3 s: 30% bigger, and what touches you is poisoned. Pufferfish is safe to eat.',
        'Um hostil a até 2, ou um golpe corpo a corpo, infla você por 3 s: 30% maior, e quem encosta é envenenado. Comer baiacu não faz mal.',
        'Cost: puffed, you are 30% slower and cannot sprint.',
        'Custo: inflado, você fica 30% mais lento e não corre.'),
    # Llama, Trader Llama
    'spit_gland': (
        'Spit Gland', 'Glândula de Cuspe',
        'Active: spit at what you look at within 12: 1 damage, and a creature loses interest in you for 4 s, once in 12 s. 2 s cooldown.',
        'Ativo: cuspa no que olha, a até 12: 1 de dano, e a criatura perde o interesse em você por 4 s, uma vez a cada 12 s. Recarga de 2 s.',
        'Cost: stand in front of you for 5 s and get spat at.',
        'Custo: quem fica 5 s na sua frente leva cuspe.'),
    # Wandering Trader
    'dusk_draught': (
        'Dusk Draught', 'Trago do Anoitecer',
        'At dusk you turn invisible for 60 s. At night, the first hostile to target you in 2 min makes you vanish for 8 s.',
        'Ao anoitecer você fica invisível por 60 s. À noite, o primeiro hostil que mirar em você em 2 min faz você sumir por 8 s.',
        'Cost: at dawn you drink any milk you carry: every effect goes.',
        'Custo: ao amanhecer, bebe o leite que levar: some todo efeito.'),
    # Zombie
    'undeath': (
        'Undeath', 'Morto-Vivo',
        'Harming heals you and Healing harms. Poison and Regeneration do nothing. Zombies, husks and drowned ignore you till you hit one.',
        'Dano Instantâneo cura e Cura Instantânea fere. Veneno e Regeneração não fazem nada. Zumbis ignoram você até você bater num.',
        'Cost: bare-headed, daylight burns you. Smite hurts.',
        'Custo: sem nada na cabeça, o sol queima. Julgamento dói.'),
    # Husk
    'sun_baked': (
        'Sun-Baked', 'Curtido ao Sol',
        'Daylight never burns you, Undeath or no Undeath. Your melee hits leave players hungry and creatures weakened.',
        'O sol nunca queima você, com Morto-Vivo ou sem. Seus golpes corpo a corpo deixam jogadores com fome e criaturas fracas.',
        'Cost: 30 s in water slows you for a minute.',
        'Custo: 30 s na água deixam você lento por um minuto.'),
    # Zombie Villager
    'rehabilitated': (
        'Rehabilitated', 'Reabilitado',
        'Villagers give you the cured-zombie discount, as if you had cured them yourself.',
        'Aldeões dão a você o desconto de zumbi curado, como se você mesmo os tivesse curado.',
        'Cost: golden apples sicken you. Hit a villager: suspended a day.',
        'Custo: maçã dourada enjoa. Bateu em aldeão: um dia de gancho.'),
    # Zombified Piglin
    'gilded_rot': (
        'Gilded Rot', 'Podridão Dourada',
        'Golden swords and axes do 3 more to the undead and lose no durability on them. A zombified piglin you hit fights you alone.',
        'Espadas e machados de ouro dão 3 a mais em mortos-vivos e não se desgastam neles. Piglin zumbificado em que você bate briga sozinho.',
        'Cost: gold armour wears out twice as fast.',
        'Custo: armadura de ouro gasta duas vezes mais rápido.'),
    # Zoglin
    'rampage': (
        'Rampage', 'Fúria',
        'Each melee hit within 3 s of the last does 1 more, up to 3 more. Three quiet seconds end the streak.',
        'Cada golpe corpo a corpo até 3 s depois do anterior dá 1 a mais, até 3 a mais. Três segundos de calma encerram a sequência.',
        'Cost: on a streak you take 15% more damage and cannot sneak.',
        'Custo: na sequência você leva 15% mais dano e não agacha.'),
    # Skeleton
    'bowstring_arms': (
        'Bowstring Arms', 'Braço de Arqueiro',
        'A bow draws fully in 0.7 s instead of 1, and your arrows do 1 more.',
        'O arco estica todo em 0,7 s em vez de 1, e suas flechas dão 1 a mais.',
        'Cost: wild wolves hunt you.',
        'Custo: lobos selvagens caçam você.'),
    # Stray
    'rime_arrows': (
        'Rime Arrows', 'Flechas de Geada',
        'Your arrows, from a bow or a crossbow, slow what they hit for 5 s.',
        'Suas flechas, de arco ou de besta, deixam lento o que acertam por 5 s.',
        'Cost: fire burns you 20% harder.',
        'Custo: o fogo queima você 20% mais.'),
    # Bogged
    'spore_arrows': (
        'Spore Arrows', 'Flechas de Esporos',
        'Your arrows, from a bow or a crossbow, poison what they hit for 4 s.',
        'Suas flechas, de arco ou de besta, envenenam o que acertam por 4 s.',
        'Cost: poison on you lasts 50% longer.',
        'Custo: veneno dura 50% mais em você.'),
    # Skeleton Horse
    'undertow_stride': (
        'Undertow Stride', 'Passo de Ressaca',
        'You walk the seabed at land speed, jump off it and do not float.',
        'Você anda no fundo do mar como em terra, pula dele e não boia.',
        'Cost: you sink, and swim up half as fast.',
        'Custo: você afunda, e nada para cima na metade da velocidade.'),
    # Snow Golem
    'snow_trail': (
        'Snow Trail', 'Rastro de Neve',
        'You leave snow where you walk, wherever a snow golem would. Your snowballs do 2 and slow for 2 s.',
        'Você deixa neve por onde anda, onde um golem de neve deixaria. Suas bolas de neve dão 2 e deixam lento por 2 s.',
        'Cost: you melt in hot places, the Nether and the rain.',
        'Custo: você derrete no calor, no Nether e na chuva.'),
    # Piglin
    'gold_standard': (
        'Gold Standard', 'Padrão-Ouro',
        'Piglins treat you as if you wore gold. Golden carrots and golden apples give half as much again.',
        'Piglins tratam você como se usasse ouro. Cenoura dourada e maçã dourada rendem 50% a mais.',
        'Cost: gold in sight on the ground slows you 10%.',
        'Custo: ouro à vista no chão deixa você 10% mais lento.'),
    # Piglin Brute
    'brute_strength': (
        'Brute Strength', 'Força Bruta',
        'Axes do 3 more, and Weakness does nothing to you.',
        'Machados dão 3 a mais, e Fraqueza não faz nada com você.',
        'Cost: piglins attack you, gold or no gold, and never barter.',
        'Custo: piglins atacam você, com ouro ou sem, e nunca negociam.'),
    # Vindicator
    'breaking_and_entering': (
        'Breaking and Entering', 'Arrombamento',
        'With an axe, wooden doors, trapdoors and fence gates break in one hit and drop. Axes do 1 more. Claims hold.',
        'Com machado, portas, alçapões e portões de madeira quebram num golpe e caem. Machado dá 1 a mais. Respeita terreno protegido.',
        'Cost: villagers will not trade while you hold an axe.',
        'Custo: aldeão não negocia com você de machado na mão.'),
    # Pillager
    'steady_aim': (
        'Steady Aim', 'Mira Firme',
        'Crossbows load 40% faster. Bolts fly 25% faster, and so hit about a quarter harder.',
        'Bestas carregam 40% mais rápido. Os virotes voam 25% mais rápido, e por isso batem cerca de um quarto mais forte.',
        'Cost: bows draw 20% slower.',
        'Custo: o arco estica 20% mais devagar.'),
    # Evoker
    'fang_line': (
        'Fang Line', 'Fileira de Presas',
        'Active: a line of eight evoker fangs toward your aim within 12, 6 to each creature caught. Look down for a ring. 4 s cooldown.',
        'Ativo: uma fileira de oito presas de invocador rumo à sua mira, a até 12, 6 em cada criatura pega. Olhe para baixo: um anel. Recarga de 4 s.',
        'Cost: blue sheep nearby turn red. Mecha: 15% of its heat limit.',
        'Custo: ovelha azul vira vermelha. Mecha: 15% do limite de calor.'),
    # Witch
    'potion_tolerance': (
        'Potion Tolerance', 'Tolerância a Poções',
        'Potions you drink last 50% longer. Harmful potions splashed on you last half as long.',
        'Poções que você bebe duram 50% mais. Poções nocivas jogadas em você duram metade.',
        'Cost: milk does nothing for you.',
        'Custo: leite não faz nada por você.'),
    # Ravager
    'bulldoze': (
        'Bulldoze', 'Trator',
        'Leaves, crops, saplings, bamboo, vines, flowers, sugar cane and cobwebs break as you walk into them. Claims hold.',
        'Folhas, plantações, mudas, bambu, cipós, flores, cana e teias quebram quando você passa. Respeita terrenos protegidos.',
        'Cost: you trample everything, your own farm included.',
        'Custo: você pisoteia tudo, inclusive a sua horta.'),
    # Ghast
    'weeping_glands': (
        'Weeping Glands', 'Glândulas Chorosas',
        'A Ghast Tear every 20 minutes: at a host\'s feet, or into a mecha\'s Cargo Hold.',
        'Uma Lágrima de Ghast a cada 20 minutos: aos pés do hospedeiro, ou no Compartimento de Carga do mecha.',
        'Cost: it sobs every few minutes, and fogs a mecha\'s glass.',
        'Custo: soluça a cada poucos minutos; embaça o vidro do mecha.'),
    # Endermite
    'pearl_digestion': (
        'Pearl Digestion', 'Digestão de Pérolas',
        'An ender pearl you throw flies half as far again, does you no harm, and its cooldown is halved.',
        'A pérola do Ender que você joga voa 50% mais longe, não machuca você, e a recarga cai pela metade.',
        'Cost: endermen attack you on sight.',
        'Custo: endermen atacam você assim que o veem.'),
    # Shulker
    'levitation_bolt': (
        'Levitation Bolt', 'Disparo Levitante',
        'Active: a homing shulker bullet at what you aim at within 24: 4 damage and 3 s of Levitation. 3 s cooldown.',
        'Ativo: um projétil teleguiado de shulker no que você mira, a até 24: 4 de dano e 3 s de Levitação. Recarga de 3 s.',
        'Cost: a mecha takes 10% of its heat limit a shot.',
        'Custo: o mecha ganha 10% do limite de calor por disparo.'),
    # Silverfish
    'stone_lungs': (
        'Stone Lungs', 'Pulmões de Pedra',
        'You never suffocate inside blocks. Stone, deepslate and cobblestone mine 20% faster.',
        'Você nunca sufoca dentro de blocos. Pedra, ardósia e pedregulho quebram 20% mais rápido.',
        'Cost: one stone block in a hundred lets out a silverfish.',
        'Custo: um bloco de pedra em cem solta uma traça.'),
    # Allay
    'fetch': (
        'Fetch', 'Leva e Traz',
        'Dropped items like the one in your off hand drift to you from 12 blocks, or to a note block played in the last 30 s.',
        'Itens no chão iguais ao da sua mão secundária vêm até você, de até 12 blocos, ou vão a um bloco musical tocado nos últimos 30 s.',
        'Cost: near a playing jukebox, you dance instead of attacking.',
        'Custo: com toca-discos tocando, você dança em vez de bater.'),
}

# ---------------------------------------------------------------------------------------------------------------
# Named pairs. A synergy is +10 stability, a conflict -15. Spitting Cobra and Herbalist each do one thing more, which
# their comment says; every other comment is the Splicer's opinion.
SYNERGIES = [
    ('fleece', 'arctic_coat', 'Winter Range', 'Invernada',
     'Two coats. HR has stopped asking about the heating.', 'Dois casacos. O RH parou de perguntar do aquecimento.'),
    ('snow_trail', 'arctic_coat', 'Abominable', 'Abominável',
     'Leaves tracks. Feels no cold. Never in the photographs.', 'Deixa pegadas. Não sente frio. Nunca sai nas fotos.'),
    ('undeath', 'sun_baked', 'Mummy', 'Múmia',
     'The zombie that doesn\'t burn.', 'O zumbi que não pega fogo.'),
    ('spit_gland', 'dusk_draught', 'Caravan', 'Caravana',
     'Spits, vanishes, keeps walking.', 'Cospe, some e segue viagem.'),
    ('spit_gland', 'venom_sac', 'Spitting Cobra', 'Naja Cuspideira',
     'And the spit poisons, 3 s.', 'E o cuspe envenena, 3 s.'),
    ('mimicry', 'echolocation', 'Early Warning', 'Alarme Antecipado',
     'Hears it coming, and says so.', 'Ouve chegando, e avisa.'),
    ('keen_nose', 'echolocation', 'Field Biologist', 'Biólogo de Campo',
     'Finds the specimen in the dark.', 'Acha o espécime no escuro.'),
    ('pollinator', 'stew_glands', 'Garden Centre', 'Floricultura',
     'Grows the flowers. Eats the flowers.', 'Planta as flores. Come as flores.'),
    ('potion_tolerance', 'stew_glands', 'Herbalist', 'Raizeiro',
     'Suspicious stew counts as a potion.', 'Ensopado suspeito conta como poção.'),
    ('roll_up', 'turtle_shell', 'Hard Shell', 'Casca-Grossa',
     'Two shells. Nothing gets in. Nothing gets done.', 'Dois cascos. Nada entra. Nada sai.'),
    ('bowstring_arms', 'rime_arrows', 'Cold Shoulder', 'Dar um Gelo',
     'A fast arrow and a cold reception.', 'Flecha rápida, recepção fria.'),
    ('rampage', 'hoglin_tusks', 'Hog Wild', 'Javali Solto',
     'Hits harder the longer it goes. So does the bill.', 'Bate mais forte quanto mais dura. A conta também.'),
    ('gold_standard', 'gilded_rot', 'Old Money', 'Berço de Ouro',
     'Gold on both sides of the family.', 'Ouro dos dois lados da família.'),
    ('rehabilitated', 'heros_blood', 'Community Service', 'Serviço Comunitário',
     'Paid its debt to the village. The village pays it back.', 'Pagou a dívida com a vila. A vila paga de volta.'),
    ('phase', 'stone_lungs', 'Wall Walker', 'Fura-Parede',
     'Goes through walls, and breathes in them.', 'Atravessa paredes, e respira lá dentro.'),
    ('levitation_bolt', 'breeze_updraft', 'Air Mail', 'Correio Aéreo',
     'Posted upward. No return address.', 'Postado para cima. Sem remetente.'),
    ('bulldoze', 'golem_frame', 'Demolition', 'Demolição',
     'Facilities approves. Facilities is worried.', 'As Instalações aprovam. As Instalações estão preocupadas.'),
    ('fang_line', 'breaking_and_entering', 'Raid Leader', 'Chefe da Invasão',
     'Doors first, then the fangs.', 'Primeiro as portas, depois as presas.'),
    ('snow_pounce', 'cat_eyes', 'Night Prowler', 'Caçador Noturno',
     'Sees it in the dark. Lands on it.', 'Enxerga no escuro. Cai em cima.'),
    ('undertow_stride', 'gills', 'Seabed Survey', 'Batimetria',
     'Walks the seabed. Breathes down there.', 'Anda no fundo do mar. Respira lá embaixo.'),
    ('weeping_glands', 'guardian_eye', 'Waterworks', 'Chororô',
     'Turns on the waterworks, then the laser.', 'Abre o berreiro, depois o laser.'),
    ('mountain_hooves', 'rabbit_spring', 'Alpine', 'Alpino',
     'Up the cliff, and down it again.', 'Sobe o morro e desce aos pulos.'),
]

CONFLICTS = [
    ('snow_pounce', 'rabbit_spring', 'Food Chain', 'Cadeia Alimentar',
     'The rabbit gene will not share a strand with the fox gene.',
     'O gene do coelho não divide fita com o da raposa.'),
    ('roll_up', 'spider_climb', 'Phobia', 'Fobia',
     'Armadillos are afraid of spiders.', 'Tatu tem medo de aranha.'),
    ('pearl_digestion', 'ender_blink', 'Workplace Bullying', 'Assédio Moral',
     'The enderman gene picks on the endermite gene.', 'O gene do enderman implica com o gene do endermite.'),
    ('arctic_coat', 'blaze_core', 'Thermostat Dispute', 'Briga do Ar-Condicionado',
     'One of these wants a coat, the other a furnace.', 'Um quer casaco, o outro quer fornalha.'),
    ('snow_trail', 'blaze_core', 'Meltdown', 'Derretimento',
     'You cannot be a snowman and a furnace.', 'Não dá para ser boneco de neve e fornalha.'),
    ('undeath', 'axolotl_regrowth', 'Medical History', 'Histórico Médico',
     'Healing is complicated for the dead.', 'Para os mortos, curar é complicado.'),
    ('breaking_and_entering', 'heros_blood', 'Bad Blood', 'Conflito de Interesses',
     'The villager and the vindicator cannot share a strand.', 'O aldeão e o vindicador não dividem a fita.'),
    ('rehabilitated', 'breaking_and_entering', 'Relapse', 'Recaída',
     'Recovery means no more doors.', 'Recuperação é nunca mais arrombar porta.'),
    ('brute_strength', 'gold_standard', 'Class Struggle', 'Luta de Classes',
     'The brute despises the gold-grubber.', 'O bruto despreza o puxa-saco do ouro.'),
    ('spore_arrows', 'rime_arrows', 'Quiver Policy', 'Norma da Aljava',
     'One quiver, one coating.', 'Uma aljava, uma cobertura.'),
    ('tongue', 'spit_gland', 'Job Description', 'Descrição de Cargo',
     'One mouth, one job.', 'Uma boca, uma função.'),
    ('bulldoze', 'pollinator', 'Land Dispute', 'Briga de Terra',
     'One grows them, one flattens them.', 'Um faz crescer, o outro passa por cima.'),
    ('phase', 'golem_frame', 'Health and Safety', 'Segurança do Trabalho',
     'Mass and phasing do not mix.', 'Massa e atravessar paredes não combinam.'),
]

# The pairs Gene.java already has, named (offered: without them the Stab tooltip would name only the new pairs)
OLD_SYNERGIES = [
    ('spider_climb', 'venom_sac', 'The Full Spider', 'A Aranha Completa',
     'Climbs the wall, then bites.', 'Sobe pela parede, depois morde.'),
    ('ender_blink', 'echolocation', 'Blind Jump', 'Salto às Cegas',
     'Hears where it is going before it goes.', 'Ouve aonde vai antes de ir.'),
    ('blaze_core', 'strider_soles', 'Lava Commute', 'Expediente na Lava',
     'Walks to work through the lava.', 'Vai trabalhar pela lava.'),
    ('glow_cells', 'cat_eyes', 'Night Light', 'Luz de Plantão',
     'Glows and sees. The Night Shift\'s favourite.', 'Brilha e enxerga. O queridinho do Turno da Noite.'),
    ('golem_frame', 'hoglin_tusks', 'Battering Ram', 'Aríete',
     'Iron behind, tusks in front.', 'Ferro atrás, presas na frente.'),
]
OLD_CONFLICTS = [
    ('blaze_core', 'gills', 'Steam', 'Vapor',
     'Fire and water. Pick one.', 'Fogo e água. Escolha um.'),
    ('blaze_core', 'axolotl_regrowth', 'Dry Heat', 'Calor Seco',
     'The axolotl dries out next to the furnace.', 'O axolote resseca perto da fornalha.'),
    ('ender_blink', 'golem_frame', 'Excess Baggage', 'Excesso de Bagagem',
     'Iron does not teleport well.', 'Ferro não se teletransporta bem.'),
    ('creeper_gland', 'turtle_shell', 'Pressure Cooker', 'Panela de Pressão',
     'A shell is a bad place to explode.', 'Casco é péssimo lugar para explodir.'),
    ('cat_eyes', 'echolocation', 'Second Opinion', 'Segunda Opinião',
     'The cat sees it, the bat hears it. They argue.', 'O gato vê, o morcego ouve. Os dois discutem.'),
]

# One tooltip row per named pair in the strand: name, then comment
PAIR_LINE = {
    'synergy': ('%1$s (+10): %2$s', '%1$s (+10): %2$s'),
    'conflict': ('%1$s (-15): %2$s', '%1$s (-15): %2$s'),
}

# ---------------------------------------------------------------------------------------------------------------
# The Directory's hint for a blank cell: where to look, then the jar its head needs and one true tip. Blood reads best
# from a creature at full health that is not poisoned, withering or on fire (SyringeItem.quality).
HINTS = {
    # Livestock
    'cow': ('Found near: plains, meadows and most grass.', 'Any jar. Draw before anything else happens to it.',
            'Onde achar: planícies, prados e quase todo pasto.', 'Qualquer pote. Colha antes de qualquer outra coisa.'),
    'mooshroom': ('Found near: mushroom fields, out at sea.', 'Any jar. Healthy specimens give the best blood.',
                  'Onde achar: campos de cogumelos, em alto-mar.', 'Qualquer pote. Espécime saudável dá o melhor sangue.'),
    'pig': ('Found near: plains, forests and meadows.', 'Any jar. Draw before lunch is discussed.',
            'Onde achar: planícies, florestas e prados.', 'Qualquer pote. Colha antes de falar em almoço.'),
    'sheep': ('Found near: plains, meadows and hills.', 'Any jar. Shearing does not spoil the blood.',
              'Onde achar: planícies, prados e morros.', 'Qualquer pote. Tosquiar não estraga o sangue.'),
    'chicken': ('Found near: grassland, and under some zombies.', 'Any jar. It will not be missed.',
                'Onde achar: pastos, e debaixo de alguns zumbis.', 'Qualquer pote. Ninguém vai sentir falta.'),
    'goat': ('Found near: mountain peaks and snowy slopes.', 'Any jar. Mind the horns while you draw.',
             'Onde achar: picos e encostas nevadas.', 'Qualquer pote. Cuidado com os chifres na coleta.'),
    'rabbit': ('Found near: deserts, flower forests and snow.', 'Any jar. Catching it is the hard part.',
               'Onde achar: desertos, florestas floridas e neve.', 'Qualquer pote. Difícil é pegar.'),
    # Field Office
    'fox': ('Found near: taiga and snowy taiga.', 'Any jar. Foxes nap by day. Draw during the nap.',
            'Onde achar: taigas, com ou sem neve.', 'Qualquer pote. Raposa cochila de dia. Colha no cochilo.'),
    'panda': ('Found near: jungles and bamboo jungles.', 'Any jar. It may sneeze on you. Carry on.',
              'Onde achar: selvas e bambuzais.', 'Qualquer pote. Pode espirrar em você. Prossiga.'),
    'bee': ('Found near: hives in plains, meadows and flowers.', 'Bring a jar, any jar: a bee is all head.',
            'Onde achar: colmeias em planícies, prados e flores.', 'Traga um pote, qualquer um: abelha é só cabeça.'),
    'parrot': ('Found near: jungles.', 'Any jar. It will repeat what you say about it.',
               'Onde achar: selvas.', 'Qualquer pote. Vai repetir o que você disser dele.'),
    'armadillo': ('Found near: savannas and badlands.', 'Any jar. It curls up if you rush it. Walk.',
                  'Onde achar: savanas e terras áridas.', 'Qualquer pote. Se correr, ele se fecha. Ande.'),
    'sniffer': ('Found near: warm ocean ruins, as an egg.', 'Reinforced jar or bigger. It is a large head.',
                'Onde achar: ruínas no mar quente, como ovo.', 'Pote Reforçado ou maior. É uma cabeça grande.'),
    'frog': ('Found near: swamps and mangrove swamps.', 'Any jar. Or grow one from a tadpole.',
             'Onde achar: pântanos e manguezais.', 'Qualquer pote. Ou crie um a partir de um girino.'),
    'tadpole': ('Found near: frogspawn, in swamps.', 'Bring a jar, any jar: a tadpole is mostly head.',
                'Onde achar: ovas de sapo, nos pântanos.', 'Traga um pote, qualquer um: girino é quase só cabeça.'),
    # Logistics
    'horse': ('Found near: plains and savannas.', 'Any jar. A healthy horse bleeds best.',
              'Onde achar: planícies e savanas.', 'Qualquer pote. Cavalo saudável sangra melhor.'),
    'donkey': ('Found near: plains, savannas and meadows.', 'Any jar. It carries the rest of its things.',
               'Onde achar: planícies, savanas e prados.', 'Qualquer pote. Ele leva o resto das coisas.'),
    'mule': ('Found near: no biome. Breed a horse and a donkey.', 'Any jar. HR does not ask how.',
             'Onde achar: nenhum bioma. Cruze cavalo com burro.', 'Qualquer pote. O RH não pergunta como.'),
    'llama': ('Found near: savanna plateaus and windswept hills.', 'Any jar. It spits. Draw anyway.',
              'Onde achar: platôs de savana e colinas ventosas.', 'Qualquer pote. Ela cospe. Colha assim mesmo.'),
    'trader_llama': ('Found near: wandering traders, on a lead.', 'Any jar. Its employer will not notice.',
                     'Onde achar: com vendedores ambulantes, na guia.', 'Qualquer pote. O patrão nem vai notar.'),
    'camel': ('Found near: desert villages.', 'Any jar. Two seats, one head.',
              'Onde achar: vilas do deserto.', 'Qualquer pote. Dois assentos, uma cabeça.'),
    'enderman': ('Found near: the End, warped forests and the dark.', 'Any jar. Do not look it in the eye.',
                 'Onde achar: o End, florestas distorcidas e o escuro.', 'Qualquer pote. Não olhe nos olhos dele.'),
    'endermite': ('Found near: thrown ender pearls, now and then.', 'Bring a jar, any jar: it is too small for a body.',
                  'Onde achar: pérolas do Ender jogadas, às vezes.', 'Traga um pote, qualquer um: é pequeno demais para corpo.'),
    'shulker': ('Found near: End cities.', 'Bring a jar, any jar: it already lives in a box.',
                'Onde achar: cidades do End.', 'Traga um pote, qualquer um: já mora numa caixa.'),
    'allay': ('Found near: outposts and mansions, in cages.', 'Any jar. Ecto Trap, not a syringe.',
              'Onde achar: postos e mansões, em gaiolas.', 'Qualquer pote. Armadilha de Ecto, não seringa.'),
    # Security
    'iron_golem': ('Found near: villages. They make their own.', 'Any jar. Oil Tap, not a syringe.',
                   'Onde achar: vilas. Elas fazem os próprios.', 'Qualquer pote. Torneira de Óleo, não seringa.'),
    'wolf': ('Found near: forests, taiga, savannas and badlands.', 'Any jar. Not somebody else\'s dog.',
             'Onde achar: florestas, taigas, savanas e terras áridas.', 'Qualquer pote. Cachorro dos outros, não.'),
    'cat': ('Found near: villages and swamp huts.', 'Any jar. Strays only. Pets have owners.',
            'Onde achar: vilas e cabanas do pântano.', 'Qualquer pote. Só de rua. Gato de casa tem dono.'),
    'ocelot': ('Found near: jungles.', 'Any jar. Approach slowly. It will not wait.',
               'Onde achar: selvas.', 'Qualquer pote. Chegue devagar. Ela não espera.'),
    'polar_bear': ('Found near: snowy plains, ice spikes and frozen seas.', 'Any jar. Leave the cubs out of it.',
                   'Onde achar: planícies nevadas, picos de gelo e mar gelado.', 'Qualquer pote. Deixe os filhotes fora disso.'),
    # Night Shift
    'zombie': ('Found near: anywhere dark, at night.', 'Any jar. Draw before sunrise: burning blood reads badly.',
               'Onde achar: qualquer lugar escuro, à noite.', 'Qualquer pote. Colha antes do sol: sangue em chamas lê mal.'),
    'husk': ('Found near: deserts, at night.', 'Any jar. It does not burn, so draw at leisure.',
             'Onde achar: desertos, à noite.', 'Qualquer pote. Não pega fogo; colha sem pressa.'),
    'drowned': ('Found near: rivers and oceans, under the water.', 'Any jar. Bring your own air.',
                'Onde achar: rios e oceanos, debaixo d\'água.', 'Qualquer pote. Leve o próprio ar.'),
    'zombie_villager': ('Found near: villages at night, and zombie villages.', 'Any jar. Its old job is not your concern.',
                        'Onde achar: vilas à noite, e vilas zumbis.', 'Qualquer pote. O emprego antigo não é da sua conta.'),
    'skeleton': ('Found near: anywhere dark, at night.', 'Any jar. Marrow Drill, not a syringe.',
                 'Onde achar: qualquer lugar escuro, à noite.', 'Qualquer pote. Broca de Medula, não seringa.'),
    'stray': ('Found near: snowy plains, ice spikes and frozen places.', 'Any jar. Marrow Drill, not a syringe.',
              'Onde achar: planícies nevadas, picos de gelo e o gelo.', 'Qualquer pote. Broca de Medula, não seringa.'),
    'bogged': ('Found near: swamps, mangroves and trial chambers.', 'Any jar. Marrow Drill. Mind the mushrooms.',
               'Onde achar: pântanos, manguezais e câmaras de desafio.', 'Qualquer pote. Broca de Medula. Cuidado com os cogumelos.'),
    'skeleton_horse': ('Found near: thunderstorms. Lightning leaves one.', 'Any jar. Marrow Drill. Its riders arrive first.',
                       'Onde achar: tempestades. O raio deixa um.', 'Qualquer pote. Broca de Medula. Os cavaleiros chegam antes.'),
    'phantom': ('Found near: the sky over anyone who has not slept.', 'Any jar. Stay up three nights; it comes to you.',
                'Onde achar: o céu de quem não dorme.', 'Qualquer pote. Passe três noites acordado; ele vem até você.'),
    'zombified_piglin': ('Found near: the Nether, in crowds.', 'Any jar. The needle stings, and the crowd notices.',
                         'Onde achar: o Nether, em bando.', 'Qualquer pote. A agulha arde, e o bando percebe.'),
    'zoglin': ('Found near: hoglins taken out of the Nether.', 'Reinforced jar or bigger. Big head, bad temper.',
               'Onde achar: hoglins tirados do Nether.', 'Pote Reforçado ou maior. Cabeça grande, gênio pior.'),
    'zombie_horse': ('Found near: no biome on our books.', 'Any jar. Officially, it does not exist.',
                     'Onde achar: nenhum bioma que conste.', 'Qualquer pote. Oficialmente, ele não existe.'),
    'giant': ('Found near: no biome on our books.', 'Any jar, somehow. Officially, it does not exist.',
              'Onde achar: nenhum bioma que conste.', 'Qualquer pote, sabe-se lá como. Oficialmente, não existe.'),
    # Facilities
    'blaze': ('Found near: Nether fortresses.', 'Any jar. Draw between fireballs: hot blood reads badly.',
              'Onde achar: fortalezas do Nether.', 'Qualquer pote. Colha fora da chama: sangue quente lê mal.'),
    'magma_cube': ('Found near: the Nether, basalt deltas most of all.', 'Small: any jar. Medium: Reinforced. Big: Executive.',
                   'Onde achar: o Nether, mais nos deltas de basalto.', 'Pequeno: qualquer pote. Médio: Reforçado. Grande: Executivo.'),
    'slime': ('Found near: swamps at night, and deep in slime chunks.', 'Small: any jar. Medium: Reinforced. Big: Executive.',
              'Onde achar: pântanos à noite, e no fundo de certas áreas.', 'Pequeno: qualquer pote. Médio: Reforçado. Grande: Executivo.'),
    'ghast': ('Found near: the Nether\'s open spaces.', 'Executive jar only. A ghast is mostly head.',
              'Onde achar: os espaços abertos do Nether.', 'Só Pote Executivo. Ghast é quase só cabeça.'),
    'strider': ('Found near: the Nether\'s lava seas.', 'Any jar. Draw it in the lava, where it is happy.',
                'Onde achar: os mares de lava do Nether.', 'Qualquer pote. Colha na lava, onde ele é feliz.'),
    'breeze': ('Found near: trial chambers.', 'Any jar. Ecto Trap, not a syringe.',
               'Onde achar: câmaras de desafio.', 'Qualquer pote. Armadilha de Ecto, não seringa.'),
    'snow_golem': ('Found near: nowhere. Build one: snow and a pumpkin.', 'Any jar. Cut once for the pumpkin, then again.',
                   'Onde achar: em lugar nenhum. Monte: neve e abóbora.', 'Qualquer pote. Um corte pela abóbora, depois outro.'),
    'spider': ('Found near: anywhere dark, and up the walls.', 'Any jar. It has eight legs and one head.',
               'Onde achar: qualquer lugar escuro, e pelas paredes.', 'Qualquer pote. Tem oito pernas e uma cabeça.'),
    'cave_spider': ('Found near: abandoned mineshafts, by the spawners.', 'Any jar. It bites back.',
                    'Onde achar: minas abandonadas, perto dos geradores.', 'Qualquer pote. Ela morde de volta.'),
    'silverfish': ('Found near: strongholds, and stone in the mountains.', 'Bring a jar, any jar: it is too small for a body.',
                   'Onde achar: fortalezas, e pedra nas montanhas.', 'Traga um pote, qualquer um: é pequena demais para corpo.'),
    'bat': ('Found near: caves, in the dark.', 'Any jar. Catching it is the hard part.',
            'Onde achar: cavernas, no escuro.', 'Qualquer pote. Difícil é pegar.'),
    # Accounts
    'piglin': ('Found near: crimson forests and Nether wastes.', 'Any jar. Wear gold, or bring friends.',
               'Onde achar: florestas carmesim e as planícies do Nether.', 'Qualquer pote. Use ouro, ou leve amigos.'),
    'piglin_brute': ('Found near: bastion remnants.', 'Any jar. Bring friends. More friends.',
                     'Onde achar: ruínas de bastião.', 'Qualquer pote. Leve amigos. Mais amigos.'),
    'hoglin': ('Found near: crimson forests.', 'Reinforced jar or bigger. A large head, and a cross one.',
               'Onde achar: florestas carmesim.', 'Pote Reforçado ou maior. Cabeça grande, e brava.'),
    'wither_skeleton': ('Found near: Nether fortresses.', 'Any jar. Marrow Drill, not a syringe.',
                        'Onde achar: fortalezas do Nether.', 'Qualquer pote. Broca de Medula, não seringa.'),
    # Legal
    'evoker': ('Found near: woodland mansions, and raids.', 'Any jar. It summons fangs. Invoice it later.',
               'Onde achar: mansões da floresta, e invasões.', 'Qualquer pote. Invoca presas. Mande a fatura depois.'),
    'vindicator': ('Found near: woodland mansions, and raids.', 'Any jar. It brings its own axe.',
                   'Onde achar: mansões da floresta, e invasões.', 'Qualquer pote. Ele traz o próprio machado.'),
    'pillager': ('Found near: outposts, patrols and raids.', 'Any jar. They come in groups. So should you.',
                 'Onde achar: postos avançados, patrulhas e invasões.', 'Qualquer pote. Andam em grupo. Ande também.'),
    'illusioner': ('Found near: no biome on our books.', 'Any jar. Officially, it does not exist.',
                   'Onde achar: nenhum bioma que conste.', 'Qualquer pote. Oficialmente, ele não existe.'),
    'vex': ('Found near: evokers, briefly.', 'Bring a jar, any jar. Be quick: vexes expire.',
            'Onde achar: perto de invocadores, por pouco tempo.', 'Traga um pote, qualquer um. Rápido: vex tem validade.'),
    'ravager': ('Found near: raids, ridden in.', 'Executive jar only. It is mostly head and opinion.',
                'Onde achar: invasões, montado.', 'Só Pote Executivo. É quase só cabeça e opinião.'),
    'witch': ('Found near: swamp huts, and anywhere at night.', 'Any jar. Let it drink its healing; it bleeds better.',
              'Onde achar: cabanas do pântano, e qualquer lugar à noite.', 'Qualquer pote. Deixe beber a cura; sangra melhor.'),
    # Sales
    'villager': ('Found near: villages.', 'Any jar. A Nitwit carries no genes. HR has checked twice.',
                 'Onde achar: vilas.', 'Qualquer pote. Bobo não tem genes. O RH conferiu duas vezes.'),
    'wandering_trader': ('Found near: wherever you are. It finds you.', 'Any jar. It has places to be. Draw quickly.',
                         'Onde achar: onde você estiver. Ele acha você.', 'Qualquer pote. Tem mais o que fazer. Colha rápido.'),
    # Marine Division
    'squid': ('Found near: rivers and oceans.', 'Any jar. Bring your own air.',
              'Onde achar: rios e oceanos.', 'Qualquer pote. Leve o próprio ar.'),
    'glow_squid': ('Found near: underground water, in the dark.', 'Any jar. It glows. It will not help.',
                   'Onde achar: água subterrânea, no escuro.', 'Qualquer pote. Brilha. Não ajuda.'),
    'cod': ('Found near: oceans, but not the warm ones.', 'Any jar. Draw it in the water; air hurts it.',
            'Onde achar: oceanos, menos os quentes.', 'Qualquer pote. Colha na água; o ar machuca.'),
    'salmon': ('Found near: rivers and cold oceans.', 'Any jar. Draw it in the water; air hurts it.',
               'Onde achar: rios e oceanos frios.', 'Qualquer pote. Colha na água; o ar machuca.'),
    'tropical_fish': ('Found near: warm oceans and lush caves.', 'Any jar. Draw it in the water; air hurts it.',
                      'Onde achar: oceanos quentes e cavernas exuberantes.', 'Qualquer pote. Colha na água; o ar machuca.'),
    'pufferfish': ('Found near: warm and lukewarm oceans.', 'Any jar. Draw it in the water, and not puffed.',
                   'Onde achar: oceanos quentes e mornos.', 'Qualquer pote. Colha na água, e desinflado.'),
    'dolphin': ('Found near: most oceans, but not the cold ones.', 'Any jar. Quick: it needs air as much as you.',
                'Onde achar: quase todo oceano, menos os frios.', 'Qualquer pote. Rápido: ele precisa de ar como você.'),
    'turtle': ('Found near: beaches.', 'Any jar. Adults only. Hatchlings are children.',
               'Onde achar: praias.', 'Qualquer pote. Só adultas. Filhote é criança.'),
    'axolotl': ('Found near: lush caves, in the water.', 'Any jar. Keep it wet; a dry axolotl bleeds badly.',
                'Onde achar: cavernas exuberantes, na água.', 'Qualquer pote. Mantenha molhado; axolote seco sangra mal.'),
    'guardian': ('Found near: ocean monuments.', 'Any jar. Draw it between beams.',
                 'Onde achar: monumentos oceânicos.', 'Qualquer pote. Colha entre um raio e outro.'),
    # Demolitions
    'creeper': ('Found near: anywhere dark, and right behind you.', 'Any jar. Draw quickly. Quietly.',
                'Onde achar: qualquer lugar escuro, e bem atrás de você.', 'Qualquer pote. Colha rápido. E quieto.'),
    # Senior Management
    'elder_guardian': ('Found near: ocean monuments, three to each.', 'Reinforced jar or bigger. Its hide needs the Leviathan Needle.',
                       'Onde achar: monumentos oceânicos, três em cada.', 'Pote Reforçado ou maior. O couro pede a Agulha Leviatã.'),
    'warden': ('Found near: the deep dark, if you are loud.', 'Reinforced jar or bigger. Leviathan Needle. Be quiet.',
               'Onde achar: o abismo, se você fizer barulho.', 'Pote Reforçado ou maior. Agulha Leviatã. Silêncio.'),
    'wither': ('Found near: wherever somebody built one.', 'No jar: it is not for cutting. Marrow Drill for the sample.',
               'Onde achar: onde alguém tiver montado um.', 'Nenhum pote: não é para cortar. Amostra com Broca de Medula.'),
    # The Board
    'ender_dragon': ('Found near: the End, on its island.', 'No jar: a Board matter. Bottle its breath and spin that.',
                     'Onde achar: o End, na ilha dele.', 'Nenhum pote: é com o Conselho. Centrifugue o bafo dele.'),
}

# ---------------------------------------------------------------------------------------------------------------
# The Body Printer's specimen prints. Sizes go into the screen's species line as %2$s.
PRINTER = [
    # the screen: the species line in place of today's own-body line (a 148 px box), the sizes, the locked slot
    # today's own-body line with the scaled numbers, in its two rows of the 148 px box (the inventory starts below them)
    ('gui.headinajar.printer.need_scaled', '%1$s flesh, %2$s bone blocks,\n%3$s redstone blocks, %4$s slime',
     '%1$s carnes, %2$s blocos de osso,\n%3$s de redstone, %4$s de slime'),
    # what the template is, for the title strip or the slot's tooltip (one row of 160 px): species, then its size
    ('gui.headinajar.printer.species', '%1$s (%2$s)', '%1$s (%2$s)'),
    ('gui.headinajar.printer.size.tiny', 'tiny', 'miúdo'),
    ('gui.headinajar.printer.size.small', 'small', 'pequeno'),
    ('gui.headinajar.printer.size.medium', 'medium', 'médio'),
    ('gui.headinajar.printer.template', 'Template: a DNA Fraction, quality 40 or better',
     'Molde: uma Fração de DNA, qualidade 40 ou mais'),
    ('gui.headinajar.printer.locked', 'Specimen printing is not licensed on this server.',
     'A impressão de espécimes não tem licença neste servidor.'),
    ('gui.headinajar.printer.villager', 'Printed villagers come without experience.',
     'Aldeão impresso vem sem experiência.'),
    # refusals, in the order the printer reads them
    ('refusal.headinajar.printer.not_licensed', 'Specimen printing is not licensed on this server.',
     'A impressão de espécimes não tem licença neste servidor.'),
    ('refusal.headinajar.printer.not_fraction', 'Templates are DNA Fractions. Spin a Blood Sample first.',
     'Molde é Fração de DNA. Centrifugue uma Amostra de Sangue antes.'),
    ('refusal.headinajar.printer.poor', 'This fraction is too poor to print from (quality %s). Bring 40 or better.',
     'Esta fração é fraca demais para imprimir (qualidade %s). Traga 40 ou mais.'),
    ('refusal.headinajar.printer.player', 'We print one body per employee. Yours is on file.',
     'Imprimimos um corpo por funcionário. O seu já está no arquivo.'),
    ('refusal.headinajar.printer.boss', 'Bosses are not printed. Legal has been very clear.',
     'Chefões não são impressos. O Jurídico foi muito claro.'),
    ('refusal.headinajar.printer.licence', 'Our licence does not cover that model.',
     'Nossa licença não cobre esse modelo.'),
    ('refusal.headinajar.printer.facilities', 'We print flesh. Golems and spirits are Facilities\' business.',
     'Imprimimos carne. Golens e espíritos são assunto das Instalações.'),
    ('refusal.headinajar.printer.all_head', 'A %s is all head. There is no body to print.',
     '%s é só cabeça. Não há corpo para imprimir.'),
    ('refusal.headinajar.printer.too_tall', 'That model is too tall for the booth.',
     'Esse modelo é alto demais para a cabine.'),
    ('refusal.headinajar.printer.too_wide', 'That model will not fit through the door.',
     'Esse modelo não passa pela porta.'),
    ('refusal.headinajar.printer.needs_water', 'Marine models are delivered wet. Flood the doorstep first.',
     'Modelos marinhos são entregues molhados. Alague a soleira antes.'),
    ('refusal.headinajar.printer.not_approved', 'That model is not on our approved list.',
     'Esse modelo não consta da nossa lista de homologados.'),
    ('warning.headinajar.printer.daylight', 'It burns in daylight. Collect it after dark, or under a roof.',
     'Ele queima no sol. Retire depois que escurecer, ou debaixo de um teto.'),
    ('warning.headinajar.printer.doorstep', 'The doorway is blocked. Your %s waits in the vat.',
     'A porta está obstruída. O pedido (%s) espera no tanque.'),
    # delivery
    ('message.headinajar.printed_species', 'Your new %s is ready. It is a little damp.',
     'Seu pedido (%s) está pronto. Está um pouco úmido.'),
    ('staff.headinajar.history.printed', 'Printed day %s.', 'Impresso no dia %s.'),
    ('jade.headinajar.printed', 'Printed', 'Sob encomenda'),
    ('jade.headinajar.printed.no_loot', 'Printed. Drops nothing when it dies.', 'Sob encomenda. Não deixa nada ao morrer.'),
]

# ---------------------------------------------------------------------------------------------------------------
# DOLORES's memos (one chat line each, so no width limit) and the release's advancements
MEMOS = [
    ('memo.headinajar.cow_apology',
     'Following a review, the cow has been found to carry genes after all. HR apologises to the cow.',
     'Após revisão, constatou-se que a vaca carrega genes, sim. O RH pede desculpas à vaca.'),
    ('memo.headinajar.splicer_sorted',
     'The gene list is now sorted by department. HR sorted it. HR would like that noted.',
     'A lista de genes agora está organizada por departamento. Quem organizou foi o RH. O RH pede que conste em ata.'),
    ('memo.headinajar.directory_ten',
     'Your directory is filling up. Lunch is your problem.',
     'Seu cadastro está enchendo. O almoço é problema seu.'),
    ('memo.headinajar.census',
     'Every species has been sampled. Head Office would like to know where you found the time.',
     'Todas as espécies têm amostra. A Sede gostaria de saber de onde você tirou tempo.'),
    ('memo.headinajar.first_species_print',
     'Your specimen is ready. Please do not print colleagues.',
     'Seu espécime está pronto. Favor não imprimir colegas.'),
]

ADVANCEMENTS = [
    ('apology_to_the_cow', 'out_for_blood', 'headinajar:blood_sample', True,
     'An Apology to the Cow', 'Sample a cow, now that it counts',
     'Desculpas à Vaca', 'Colha sangue de uma vaca, agora que conta'),
    ('headhunter', 'staffed', 'headinajar:jarred_head', False,
     'Headhunter', 'Jar the heads of 25 different species',
     'Caça-Talentos', 'Ponha no pote cabeças de 25 espécies diferentes'),
    ('complete_census', 'out_for_blood', 'headinajar:dna_sequencer', False,
     'Complete Census', 'Sample all 77 species on the books',
     'Censo Completo', 'Colha sangue das 77 espécies cadastradas'),
    ('off_the_books', 'complete_census', 'minecraft:paper', True,
     'Off the Books', 'File a creature Head Office has no record of',
     'Por Fora', 'Cadastre uma criatura de que a Sede não tem registro'),
    ('made_to_order', 'spliced', 'headinajar:body_printer', False,
     'Made to Order', 'Print a host from a DNA Fraction',
     'Sob Encomenda', 'Imprima um hospedeiro a partir de uma Fração de DNA'),
]

# ---------------------------------------------------------------------------------------------------------------
# Offered: the book's gene refusals and messages (action bar), and the small lines around them
GENE_LINES = [
    ('refusal.headinajar.gene.phase.no_room', 'No room on the other side. Phasing needs somewhere to arrive.',
     'Não há espaço do outro lado. Para atravessar, é preciso ter onde chegar.'),
    ('refusal.headinajar.gene.phase.claim', 'That wall is protected. Phase somewhere else.',
     'Essa parede é protegida. Atravesse em outro lugar.'),
    ('refusal.headinajar.gene.phase.forever', 'That wall goes on forever. Phase somewhere else.',
     'Essa parede não acaba nunca. Atravesse em outro lugar.'),
    ('refusal.headinajar.gene.phase.off', 'This server keeps its walls.',
     'Este servidor faz questão das paredes.'),
    ('refusal.headinajar.gene.bulldoze.claim', 'This ground is protected. Trample somewhere else.',
     'Este terreno é protegido. Pisoteie em outro lugar.'),
    ('refusal.headinajar.gene.fleece.untrusted', 'That colleague\'s wool is their own. Ask them to trust you.',
     'A lã do colega é dele. Peça que ele confie em você.'),
    ('refusal.headinajar.gene.tongue.player', 'Tongues are for items and small creatures. Not colleagues.',
     'Língua é para itens e bichos pequenos. Colega, não.'),
    ('refusal.headinajar.gene.puffer.no_room', 'No room to puff.', 'Sem espaço para inflar.'),
    ('message.headinajar.gene.hooves_boots', 'Hooves don\'t take boots. They went into your pockets.',
     'Casco não calça bota. Foram para o seu bolso.'),
    ('message.headinajar.gene.silverfish', 'It\'s yours now. HR has opened a file.',
     'Agora é sua. O RH abriu uma ficha.'),
    ('message.headinajar.gene.husk_water', 'Husks do not swim.', 'Quem é do deserto não nada.'),
    ('item.headinajar.sample.nitwit', 'Carries no readable genes. HR has checked twice.',
     'Não carrega genes legíveis. O RH conferiu duas vezes.'),
    ('hud.headinajar.gene_no_key', 'No key: %s', 'Sem tecla: %s'),
    ('gene.headinajar.fetch.no_hand', 'Needs an off hand. Hosts and mecha have none.',
     'Precisa de mão secundária. Hospedeiro e mecha não têm.'),
]

# Offered: the Splicer's hover rows (G1) and the Stab tooltip (G14)
SPLICER = [
    ('gui.headinajar.splicer.chip', '%1$s: %2$s', '%1$s: %2$s'),  # the title strip: department name, its line
    ('gui.headinajar.splicer.tip.department', 'Department: %s', 'Departamento: %s'),
    ('gui.headinajar.splicer.tip.numbers', 'Load %1$s, stability %2$s', 'Peso %1$s, estabilidade %2$s'),
    ('gui.headinajar.splicer.tip.active', 'Active: answers a gene key', 'Ativo: responde a uma tecla de gene'),
    ('gui.headinajar.splicer.pairs', 'Named pairs in this strand:', 'Pares nomeados nesta fita:'),
    ('gui.headinajar.splicer.pairs.none', 'No named pairs. HR finds this restful.',
     'Nenhum par nomeado. O RH acha isso tranquilo.'),
]

# Offered: the Directory tab's own lines (A4, H12, H13)
DIRECTORY = [
    ('gui.headinajar.sequencer.tab.sequencer', 'Sequencer', 'Sequenciador'),
    ('gui.headinajar.sequencer.tab.directory', 'Staff Directory', 'Cadastro de Pessoal'),
    ('gui.headinajar.directory.redacted', '[REDACTED]', '[SIGILOSO]'),
    ('gui.headinajar.directory.no_record', 'Head Office has no record of this creature.',
     'A Sede não tem registro desta criatura.'),
    ('gui.headinajar.directory.filed', '%1$s: filed by %2$s', '%1$s: cadastrado por %2$s'),
    ('gui.headinajar.directory.other_firms', 'Other firms: %s', 'Terceirizadas: %s'),
    ('gui.headinajar.directory.closed', '(firm no longer trading)', '(firma fechou as portas)'),
    ('gui.headinajar.directory.count', '%1$s of %2$s on file', '%1$s de %2$s cadastrados'),
    ('gui.headinajar.directory.stamp.sampled', 'Sampled', 'Amostra colhida'),
    ('gui.headinajar.directory.stamp.jarred', 'Jarred', 'No pote'),
    ('gui.headinajar.directory.stamp.ridden', 'Ridden as a host', 'Montado como hospedeiro'),
    ('gui.headinajar.directory.stamp.hired', 'Hired', 'Contratado'),
    ('gui.headinajar.directory.blood', 'Blood: %s', 'Sangue: %s'),
    ('gui.headinajar.directory.tool', 'Draw with: %s', 'Colha com: %s'),
    ('gui.headinajar.directory.gene', '%1$s: %2$s%% read', '%1$s: %2$s%% lido'),
    ('gui.headinajar.directory.no_genes', 'No readable genes on file', 'Nenhum gene legível no arquivo'),
    ('gui.headinajar.directory.jar', 'Jar: %s', 'Pote: %s'),
    ('gui.headinajar.directory.jar.none', 'Jar: none. Not for cutting.', 'Pote: nenhum. Não é para cortar.'),
    ('hud.headinajar.bio_scan.unfiled', 'Unfiled', 'Sem ficha'),
]

# Offered: subtitles for A5's sound events (headinajar:gene.quirk.<name>, headinajar:gene.<name>)
SUBTITLES = {
    'gene.quirk.moo': ('Colleague moos', 'Colega muge'),
    'gene.quirk.oink': ('Colleague oinks', 'Colega grunhe'),
    'gene.quirk.croak': ('Colleague croaks', 'Colega coaxa'),
    'gene.quirk.sneeze': ('Colleague sneezes', 'Colega espirra'),
    'gene.quirk.sob': ('Colleague sobs', 'Colega soluça'),
    'gene.tongue': ('Tongue lashes', 'Língua estala'),
    'gene.spit': ('Spits', 'Cusparada'),
    'gene.phase': ('Phases through the wall', 'Atravessa a parede'),
    'gene.pounce': ('Pounces', 'Dá o bote'),
    'gene.puff': ('Puffs up', 'Infla'),
}

# ---------------------------------------------------------------------------------------------------------------
# The longest real arguments each %s line was checked with, per language: reuse them in the generator's width assert
NAME16 = 'WWWWWWWWWWWWWWWW'  # a 16-letter player name, the widest letters
WIDE_ARGS = {
    'refusal.headinajar.printer.poor': [('39',), ('39',)],
    'refusal.headinajar.printer.all_head': [('Magma Cube',), ('Cubo de Magma',)],
    'warning.headinajar.printer.doorstep': [('Wandering Trader',), ('Lhama de Comerciante',)],
    'message.headinajar.printed_species': [('Wandering Trader',), ('Lhama de Comerciante',)],
    'hud.headinajar.gene_no_key': [('Breaking and Entering',), ('Fileira de Presas',)],
    'gui.headinajar.directory.filed': [('Zombified Piglin', NAME16), ('Piglin Zumbificado', NAME16)],
    'gui.headinajar.printer.need_scaled': [('16', '8', '4', '8'), ('16', '8', '4', '8')],
    'gui.headinajar.printer.species': [('Wandering Trader', 'medium'), ('Lhama de Comerciante', 'médio')],
}

"""Lines and data for the 0.6.1 release (Heads at work): titles, the bell, lunch, the eighteen desk jobs, Lookouts and
the Living Library. Imported by gen_data.py, after gen_p37_foundations and before the handbook generator
(gen_p27_clarity), whose text check and handbook read the final lines and entries.

What every head says comes from gen_p38_lines (data only); this file turns it into language keys and writes the rest:
- the staff files again (data/headinajar/headinajar/staff/<species>.json): gen_p37's wage and noise, and now how many
  bell lines a species has, its desk job, its Lookout's alarm sound, what it dislikes at dawn and which lines of its own
  it has (a Nitwit's bells, complaints, a goodbye, a Lookout's line);
- the sound events of placed heads (sounds.json, after gen_p35's pattern) and their subtitles;
- the Nether floor block tag, three advancements, the handbook's Desk, Lookouts and Living Library entries, and the
  Pay and Morale and Turret entries brought up to date;
- the plinth's bowl, broth and spoon models from art/heads/plinth_bowl.json, when that file is there.
Every action-bar line it makes is checked under 400 px with its longest real arguments, in both languages.
"""
import json
import os

import gen_p38_lines as L
from gen_p37_foundations import STAFF, _event

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources')
BOWL_ART = os.path.join(HERE, '..', 'art', 'heads', 'plinth_bowl.json')
MC = 'minecraft:'

# ---------------------------------------------------------------------------------------------------------------
# The staff table's new columns

# the species with a desk job this release; the job's id is the species' own path
DESK = ['villager', 'cow', 'sheep', 'mooshroom', 'chicken', 'cat', 'blaze', 'ghast', 'piglin', 'allay', 'frog', 'squid',
        'goat', 'sniffer', 'iron_golem', 'evoker', 'warden', 'zombie']

# the thirteen heads that attack at a Turret Pedestal: no alarm, they shoot
ATTACKERS = ['skeleton', 'stray', 'bogged', 'blaze', 'guardian', 'elder_guardian', 'creeper', 'zombie', 'husk', 'drowned',
             'snow_golem', 'witch', 'evoker']

# what costs a head 5 morale at dawn (the Employee Handbook's table)
DISLIKES = {
    'panda': ['thunder'], 'bee': ['rain'], 'enderman': ['rain'], 'polar_bear': ['hot'],
    'zombie': ['open_sky'], 'drowned': ['open_sky'], 'zombie_villager': ['open_sky'], 'skeleton': ['open_sky'],
    'phantom': ['open_sky'], 'blaze': ['rain', 'water'], 'strider': ['not_nether'], 'snow_golem': ['warm', 'rain'],
    'hoglin': ['warped_fungus'], 'warden': ['noise'],
}

# A Lookout's alarm: the species' own ambient sound; where vanilla gives it none, the sound it is best known by
# (a villager's "Hrm!" is its no; the warden's, its sonic charge). Every id here is a vanilla sound event of 1.21.1.
ALARM = {
    'cow': 'entity.cow.ambient', 'mooshroom': 'entity.cow.ambient', 'pig': 'entity.pig.ambient',
    'sheep': 'entity.sheep.ambient', 'chicken': 'entity.chicken.ambient', 'goat': 'entity.goat.ambient',
    'rabbit': 'entity.rabbit.ambient',
    'fox': 'entity.fox.ambient', 'panda': 'entity.panda.ambient', 'bee': 'entity.bee.loop_aggressive',
    'parrot': 'entity.parrot.ambient', 'armadillo': 'entity.armadillo.ambient', 'sniffer': 'entity.sniffer.idle',
    'frog': 'entity.frog.ambient', 'tadpole': 'entity.tadpole.flop',
    'horse': 'entity.horse.ambient', 'donkey': 'entity.donkey.ambient', 'mule': 'entity.mule.ambient',
    'llama': 'entity.llama.ambient', 'trader_llama': 'entity.llama.ambient', 'camel': 'entity.camel.ambient',
    'enderman': 'entity.enderman.ambient', 'endermite': 'entity.endermite.ambient', 'shulker': 'entity.shulker.ambient',
    'allay': 'entity.allay.ambient_without_item',
    'iron_golem': 'entity.iron_golem.attack', 'wolf': 'entity.wolf.ambient', 'cat': 'entity.cat.ambient',
    'ocelot': 'entity.ocelot.ambient', 'polar_bear': 'entity.polar_bear.ambient',
    'zombie_villager': 'entity.zombie_villager.ambient', 'skeleton_horse': 'entity.skeleton_horse.ambient',
    'phantom': 'entity.phantom.ambient', 'zombified_piglin': 'entity.zombified_piglin.ambient',
    'zoglin': 'entity.zoglin.ambient', 'zombie_horse': 'entity.zombie_horse.ambient', 'giant': 'entity.zombie.ambient',
    'magma_cube': 'entity.magma_cube.squish', 'slime': 'entity.slime.squish', 'ghast': 'entity.ghast.ambient',
    'strider': 'entity.strider.ambient', 'breeze': 'entity.breeze.idle_ground', 'spider': 'entity.spider.ambient',
    'cave_spider': 'entity.spider.ambient', 'silverfish': 'entity.silverfish.ambient', 'bat': 'entity.bat.ambient',
    'piglin': 'entity.piglin.ambient', 'piglin_brute': 'entity.piglin_brute.ambient', 'hoglin': 'entity.hoglin.ambient',
    'wither_skeleton': 'entity.wither_skeleton.ambient',
    'vindicator': 'entity.vindicator.ambient', 'pillager': 'entity.pillager.ambient',
    'illusioner': 'entity.illusioner.ambient', 'vex': 'entity.vex.ambient', 'ravager': 'entity.ravager.ambient',
    'villager': 'entity.villager.no', 'wandering_trader': 'entity.wandering_trader.ambient',
    'squid': 'entity.squid.ambient', 'glow_squid': 'entity.glow_squid.ambient', 'cod': 'entity.cod.flop',
    'salmon': 'entity.salmon.flop', 'tropical_fish': 'entity.tropical_fish.flop',
    'pufferfish': 'entity.puffer_fish.blow_up', 'dolphin': 'entity.dolphin.ambient',
    'turtle': 'entity.turtle.ambient_land', 'axolotl': 'entity.axolotl.idle_air',
    'warden': 'entity.warden.sonic_charge', 'wither': 'entity.wither.ambient',
}

# A villager's title is its profession, in vanilla's own words for it (VILLAGER_TITLES has the unemployed and the
# Nitwit, who have none)
PROFESSIONS = {
    'armorer': ('Armorer', 'Armeiro'), 'butcher': ('Butcher', 'Açougueiro'),
    'cartographer': ('Cartographer', 'Cartógrafo'), 'cleric': ('Cleric', 'Clérigo'), 'farmer': ('Farmer', 'Agricultor'),
    'fisherman': ('Fisherman', 'Pescador'), 'fletcher': ('Fletcher', 'Flecheiro'),
    'leatherworker': ('Leatherworker', 'Coureiro'), 'librarian': ('Librarian', 'Bibliotecário'),
    'mason': ('Mason', 'Pedreiro'), 'shepherd': ('Shepherd', 'Pastor'), 'toolsmith': ('Toolsmith', 'Ferramenteiro'),
    'weaponsmith': ('Weaponsmith', 'Armeiro'),
}

# ---------------------------------------------------------------------------------------------------------------
# Lines: (key, en, pt)
LINES = [
    # desk lines said over the jar and on the ringer's action bar (the writer's DESK_PLAIN has the others)
    ('desk.headinajar.villager.best_offer2', 'Best offer: %2$s x%1$s and %4$s x%3$s for %6$s x%5$s.',
     'Oferta: %2$s x%1$s e %4$s x%3$s por %6$s x%5$s.'),
    ('desk.headinajar.sheep.dyed', 'Now %s. Textiles has updated the file.', 'Cor nova: %s. O Têxtil atualizou a ficha.'),
    ('desk.headinajar.sheep.shorn', 'One fleece, as per contract. It grows back by morning.',
     'Um velo, conforme contrato. Cresce de novo até de manhã.'),
    ('desk.headinajar.mooshroom.stew', "Today's soup. The canteen thanks you for bringing your own bowl.",
     'Sopa do dia. A cantina agradece por trazer a própria tigela.'),
    ('desk.headinajar.mooshroom.suspicious', "Today's special. HR cannot say what is in it.",
     'Prato do chef. O RH não pode dizer o que tem nele.'),
    ('desk.headinajar.mooshroom.flower', 'It has eaten the %s. The next stew will be suspicious.',
     'Comeu: %s. O próximo ensopado vai ser suspeito.'),
    ('desk.headinajar.chicken.hour', "%s o'clock. The Timekeeper has logged everyone.",
     'Ponto batido: %s h. O Controle de Ponto anotou todo mundo.'),
    ('desk.headinajar.chicken.feather', 'One feather, filed under Expenses.', 'Uma pena. Leve, desta vez.'),
    ('desk.headinajar.ghast.tear', 'The Grief Counsellor has cried again. One tear, on the plinth.',
     'O Apoio ao Luto chorou de novo. Uma lágrima, no pedestal.'),
    ('desk.headinajar.ghast.two_tears', 'Two tears. Its sorrow is a renewable resource.',
     'Duas lágrimas. A tristeza dele é um recurso renovável.'),
    ('desk.headinajar.ghast.wail', 'The Grief Counsellor is having a moment. Please do not tap the glass.',
     'O Apoio ao Luto está num momento difícil. Favor não bater no vidro.'),
    ('desk.headinajar.piglin.turned', 'It has turned. HR has moved it to Pensions.',
     'Virou zumbi. O RH o transferiu para os Aposentados.'),
    ('desk.headinajar.allay.took', 'It will look for %s. It loves to help.', 'Procura: %s. Adora ajudar.'),
    # the new item, and the old one (handed back, so not named)
    ('desk.headinajar.allay.swapped', 'Now looking for %1$s. Old one returned.', 'Troca feita. Procura: %1$s.'),
    ('desk.headinajar.allay.called', 'Item Recovery has recovered %s.', 'Achados e Perdidos achou: %s.'),
    ('desk.headinajar.squid.ink', 'One ink sac. Keep the bottle. Printing is out of toner again.',
     'Uma bolsa de tinta. Fique com o frasco. A Gráfica está sem toner de novo.'),
    ('desk.headinajar.goat.scream', 'It is three in the morning. Recruitment is screaming. This is normal.',
     'São três da manhã. O Recrutamento está berrando. É normal.'),
    ('desk.headinajar.sniffer.found', 'It found something. It always does.', 'Achou alguma coisa. Sempre acha.'),
    ('desk.headinajar.evoker.sheep', 'Legal has reviewed your sheep. Finding: red.',
     'O Jurídico analisou a sua ovelha. Parecer: vermelha.'),
    ('desk.headinajar.zombie.knock', 'Knock, knock. It is the Clerk. It is always the Clerk.',
     'Toc, toc. É o Escriturário. É sempre o Escriturário.'),

    # refusals (the refusal sound, the line, a head shake)
    ('refusal.headinajar.desk.bowl_full', 'It has had lunch. It will eat again in %s days.',
     'Já almoçou. Próximo almoço em %s dias.'),
    ('refusal.headinajar.desk.wrong_bucket', 'Heads eat Nutrient Broth. Not %s.',
     'Cabeça só come Caldo Nutritivo. %s, não.'),
    ('refusal.headinajar.desk.too_soon', 'It did that today. Come back tomorrow.', 'Já fez isso hoje. Volte amanhã.'),
    ('refusal.headinajar.desk.unfed', 'It does that on a full stomach. Use a Nutrient Broth Bucket on the plinth.',
     'Isso, só de barriga cheia. Use um Balde de Caldo Nutritivo no pedestal.'),
    ('refusal.headinajar.desk.busy', 'It is still inspecting the last one. Accounts takes its time.',
     'Ainda está examinando o último. A Tesouraria não tem pressa.'),
    ('refusal.headinajar.desk.same_colour', 'It is already %s. Textiles has it on file.',
     'Já é dessa cor: %s. O Têxtil tem na ficha.'),
    ('refusal.headinajar.desk.allay_has', 'Already looking for %s. Very excited about it.',
     'Já procura: %s. Animadíssimo.'),
    ('refusal.headinajar.staff.no_cake', 'It is not hungry for cake. One a day.', 'Não está com fome de bolo. Um por dia.'),
    ('refusal.headinajar.staff.board_cake', 'The Board does not eat cake. It takes broth.',
     'O Conselho não come bolo. Toma caldo.'),
    ('refusal.headinajar.staff.mount_cake', 'Desk staff are paid in emeralds. The cake has been noted.',
     'Funcionário de balcão recebe em esmeralda. O bolo foi registrado.'),
    ('refusal.headinajar.staff.cake_trophy', 'Cake is a staff benefit. A trophy is not staff.',
     'Bolo é benefício de funcionário. Troféu não é funcionário.'),
    ('refusal.headinajar.library.rested', "It gave blood today. HR insists on a day's rest.",
     'Já doou sangue hoje. O RH exige um dia de descanso.'),
    ('refusal.headinajar.library.board_rested', 'The whole Board gave blood today. The meeting continues.',
     'O Conselho inteiro doou sangue hoje. A reunião continua.'),
    ('refusal.headinajar.library.board_not_yours', 'The directors left report to someone else. Ask them to trust you.',
     'Os diretores restantes respondem a outra pessoa. Peça que confie em você.'),

    # warnings (the warning sound: it still happens)
    ('warning.headinajar.desk.piglin_turns', 'It is not used to the Overworld. Stand it on a Nether block, or it will turn.',
     'Não se acostumou à Superfície. Ponha num bloco do Nether, ou ele vira.'),
    ('warning.headinajar.turret.lookout', '%s head: no attack. It will keep watch and raise the alarm.',
     'Cabeça de %s não ataca. Vai vigiar e dar o alarme.'),

    # the action bar
    ('message.headinajar.staff.on_lunch', '%s is on lunch. Lunch is employment. HR has the paperwork.',
     '%s está no almoço. Almoço gera vínculo empregatício.'),
    ('message.headinajar.staff.cake', '%s has had cake. Morale is up. Nobody mention the dentist.',
     '%s comeu bolo. A moral subiu. Ninguém fale do dentista.'),

    # history: the feeder, the day
    ('staff.headinajar.history.lunch', 'Put on lunch by %s day %s.', 'Almoço servido por %s no dia %s.'),

    # DOLORES
    ('memo.headinajar.first_head_placed',
     'Your new colleague is on its desk. It works when it is fed: use a Nutrient Broth Bucket on the plinth. Ring the '
     'bell to say hello.',
     'Seu novo colega está na mesa dele. Ele trabalha quando come: use um Balde de Caldo Nutritivo no pedestal. Toque '
     'a campainha para dar oi.'),
    ('memo.headinajar.disengaged',
     '%s is disengaged. Disengaged staff work at half speed and say so. Pay them, feed them, or both.',
     '%s está desmotivado. Funcionário desmotivado trabalha pela metade e avisa. Pague, alimente, ou os dois.'),
    ('memo.headinajar.first_head_draw',
     'The best donor is a head that cannot run away. Each gives once a day, and minds it a little.',
     'O melhor doador é uma cabeça que não pode fugir. Cada uma doa uma vez por dia, e se chateia um pouco.'),
    ('memo.headinajar.first_station_draw',
     'Staff can be bled without leaving their posts. The Board, without stopping the meeting.',
     'Dá para tirar sangue dos funcionários sem tirá-los do posto. E do Conselho, sem parar a reunião.'),

    # the tooltip, Jade and the file card (client side)
    ('item.headinajar.jarred_head.title', 'Title: %s', 'Cargo: %s'),
    ('jade.headinajar.title', 'Title: %s', 'Cargo: %s'),
    ('jade.headinajar.lunch', 'Lunch: %s mB', 'Almoço: %s mB'),
    ('jade.headinajar.library.bloodless', 'This head is bloodless. Draw it by hand.',
     'Esta cabeça não tem sangue. Colete à mão.'),
    ('jade.headinajar.library.thick', 'Its hide is too thick for leeches. Draw it by hand.',
     'Couro grosso demais para sanguessugas. Colete à mão.'),
    # a big head's first ring in its big jar (HR's part; the noise is the species' own)
    ('bell.headinajar.snug_fit', 'It is a snug fit.', 'Coube. O RH chama de aconchegante.'),
    ('screen.headinajar.file.title_row', 'Title', 'Cargo'),
    ('screen.headinajar.file.lunch', 'Lunch', 'Almoço'),
    ('screen.headinajar.file.lunch_value', '%s mB, %s days', '%s mB, %s dias'),

    # lines an older generator wrote that are no longer true: a trophy is hired by lunch as well as by hand, and a
    # villager or a species with no wage only by lunch
    ('screen.headinajar.file.trophy', 'Trophy. Pay it by hand or put it on lunch to hire it.',
     'Troféu. Pague na mão ou dê almoço para contratar.'),
    ('screen.headinajar.file.trophy.villager', 'Trophy. Villagers are never paid by hand: put it on lunch to hire it.',
     'Troféu. Aldeão não recebe na mão: dê almoço para contratar.'),
    ('refusal.headinajar.staff.no_wage', 'A %s draws no wage. It works for lunch: Nutrient Broth.',
     '%s não tem salário. Trabalha pelo almoço: Caldo Nutritivo.'),
]
REPLACED = {'screen.headinajar.file.trophy', 'screen.headinajar.file.trophy.villager', 'refusal.headinajar.staff.no_wage'}

# ---------------------------------------------------------------------------------------------------------------
# Sounds: event -> (the event it plays, volume, pitch, subtitle en, subtitle pt). All but the warden's shush borrow a
# sound vanilla or the mod already makes, played under an event of its own (gen_p35's pattern), so they ship nothing and
# follow a resource pack. The shush is tools/make_sounds.py's head/warden_shush.
SOUNDS = {
    'head.desk_bell': ('minecraft:block.bell.use', 1.0, 1.0, 'Desk bell rings', 'Campainha toca'),
    'head.slurp': ('minecraft:entity.generic.drink', 1.0, 1.0, 'Head slurps broth', 'Cabeça sorve o caldo'),
    'head.nod': ('minecraft:block.wooden_trapdoor.close', 0.7, 1.25, 'Head nods', 'Cabeça acena'),
    'head.chicken_hour': ('minecraft:entity.chicken.ambient', 1.0, 1.0, 'Chicken clucks the hour',
                          'Galinha cacareja a hora'),
    'head.frog_forecast': ('minecraft:entity.frog.ambient', 1.0, 1.0, 'Frog croaks the forecast', 'Sapo coaxa a previsão'),
    'head.ghast_weep': ('minecraft:entity.ghast.ambient', 1.0, 1.0, 'Ghast head weeps', 'Cabeça de ghast chora'),
    # the desk bell's own ding, a little brighter: the Lookout rings for attention
    'head.lookout_alarm': ('headinajar:house.desk_ding', 1.0, 1.2, 'Lookout raises the alarm', 'Vigia dá o alarme'),
}
SHUSH = ('head.warden_shush', 'headinajar:head/warden_shush', 'Warden head shushes', 'Sentinela pede silêncio')

# The Nether floor a piglin head is calm on
NETHER_FLOOR = ['minecraft:netherrack', '#minecraft:nylium', 'minecraft:soul_sand', 'minecraft:soul_soil',
                'minecraft:blackstone', 'minecraft:basalt', 'minecraft:polished_basalt']

# ---------------------------------------------------------------------------------------------------------------
# Advancements (the headinajar:event trigger): id, parent, icon, en title, en description, pt title, pt description
ADVANCEMENTS = [
    ('lunch_is_served', 'staffed', 'headinajar:nutrient_broth_bucket',
     'Lunch Is Served', 'Feed a jarred head on its plinth',
     'O Almoço Está Servido', 'Alimente uma cabeça no pote no pedestal dela'),
    ('neighbourhood_watch', 'lunch_is_served', 'minecraft:bell',
     'Neighbourhood Watch', 'Have a Lookout raise the alarm',
     'Vizinhança Solidária', 'Faça um Vigia dar o alarme'),
    ('blood_bank', 'out_for_blood', 'headinajar:blood_sample',
     'Blood Bank', 'Bleed the same head on seven different days',
     'Banco de Sangue', 'Tire sangue da mesma cabeça em sete dias diferentes'),
]

# ---------------------------------------------------------------------------------------------------------------
# The handbook. The numbers are the code's: block/DeskJobs.java (lunch, the jobs, the dawn), block/HeadDonor.java (the
# Living Library), block/TurretPedestalBlockEntity.java (Lookouts).
LIBRARY = 'library'  # in Research, beside the blood tools (clarity/Memos.java links research/library)
ENTRIES = [
    {'cat': 'staffing', 'id': 'desk', 'sort': 10, 'icon': 'headinajar:jarred_head',
     'en': 'Heads at Work', 'pt': 'No Batente',
     'pages': [
         ('text',
          'Every placed head has a $(l)title$(): the cow is the Canteen Supervisor.$(br2)Use one with an empty hand: '
          'the bell rings, it nods and says something. HR translates.$(br2)Fed, it does its $(l)desk job$().',
          'Toda cabeça pousada tem um $(l)cargo$(): a vaca é a Supervisora da Cantina.$(br2)Use uma com a mão vazia: '
          'a campainha toca, ela acena e diz algo. O RH traduz.$(br2)Alimentada, trabalha.'),
         ('spotlight', 'headinajar:nutrient_broth_bucket',
          '$(l)Lunch$(): use the bucket on the plinth. The bowl holds 1,000 mB, three days of lunch. A cow head '
          'within 8 makes it last 10% longer. The first lunch hires a trophy; then pipes may feed it too.',
          '$(l)Almoço$(): use o balde no pedestal. A tigela leva 1.000 mB, três dias de almoço. Uma cabeça de vaca '
          'a até 8 faz render 10% mais. O primeiro almoço contrata um troféu; depois, cano também serve.'),
         ('form', ('Form 41-D: Desk Jobs', 'Formulário 41-D: Serviços'),
          'Items are used on the head, fed.$(br)$(l)Villager$(): rung with an emerald in hand, reads its best '
          'offer.$(br)$(l)Cow$(): broth near it lasts longer.$(br)$(l)Sheep$(): shears, wool daily; dye, a new '
          'colour.',
          'Itens se usam na cabeça, alimentada.$(br)$(l)Aldeão$(): com esmeralda na mão, lê a melhor '
          'oferta.$(br)$(l)Vaca$(): o caldo perto dela rende mais.$(br)$(l)Ovelha$(): tesoura, lã por dia; tinta, cor '
          'nova.'),
         ('form', ('Form 41-D (2)', 'Formulário 41-D (2)'),
          '$(l)Mooshroom$(): a bowl, stew daily; a brown one fed a flower, suspicious stew.$(br)$(l)Chicken$(): clucks '
          'the hour; a feather at dawn.$(br)$(l)Blaze$(): furnaces it touches cook 25% faster.$(br)$(l)Evoker$(): '
          'turns a blue sheep within 8 red.',
          '$(l)Mooshroom$(): tigela, ensopado por dia; a marrom que comeu flor dá o suspeito.$(br)$(l)Galinha$(): bate '
          'as horas; uma pena ao amanhecer.$(br)$(l)Blaze$(): fornos encostados, 25% mais '
          'rápidos.$(br)$(l)Invocador$(): ovelha azul a 8 vira vermelha.'),
         ('form', ('Form 41-D (3)', 'Formulário 41-D (3)'),
          '$(l)Cat$(): creepers keep 6 blocks off, phantoms 16; a dawn gift if its employer slept.$(br)$(l)Ghast$(): '
          'a tear at dawn; a wail at night.$(br)$(l)Frog$(): croaks a minute before rain.$(br)$(l)Squid$(): a glass '
          'bottle, an ink sac daily.',
          '$(l)Gato$(): creepers a 6, espectros a 16; presente de manhã se o empregador dormiu.$(br)$(l)Ghast$(): '
          'lágrima de manhã, choro à noite.$(br)$(l)Sapo$(): coaxa antes da chuva.$(br)$(l)Lula$(): frasco, tinta por '
          'dia.'),
         ('form', ('Form 41-D (4)', 'Formulário 41-D (4)'),
          '$(l)Piglin$(): a gold ingot, one barter. Off a Nether block, out of the Nether, it turns in 15 '
          's.$(br)$(l)Allay$(): use an item on it: every 30 s it fetches one like it within 16 into a chest it '
          'touches.',
          '$(l)Piglin$(): lingote de ouro, uma troca. Fora de bloco do Nether, fora do Nether, vira zumbi em 15 '
          's.$(br)$(l)Sereno$(): use um item nele; a cada 30 s traz um igual, de até 16, a um baú encostado.'),
         ('form', ('Form 41-D (5)', 'Formulário 41-D (5)'),
          '$(l)Goat$(): rung, rams its glass: a horn, twice ever.$(br)$(l)Sniffer$(): on dirt, grass, moss or mud, a '
          'seed at dawn.$(br)$(l)Iron Golem$(): villager heads within 16 cheer up; a poppy on the first ring of the '
          'day.',
          '$(l)Cabra$(): tocada, chifra o vidro: um chifre, duas vezes na vida.$(br)$(l)Farejador$(): sobre terra, '
          'grama, musgo ou lama, uma semente por dia.$(br)$(l)Golem de Ferro$(): anima aldeões a até 16; papoula no '
          'primeiro toque do dia.'),
         ('form', ('Form 41-D (6)', 'Formulário 41-D (6)'),
          '$(l)Zombie$(): knocks on a door when someone comes near.$(br)$(l)Warden$(): logs every sound within 16; '
          'rung, reads out the last five. Over 20 a minute: -5 morale. At Grievance, one shriek: Darkness, 10 s, '
          'within 16.',
          '$(l)Zumbi$(): bate na porta quando alguém chega.$(br)$(l)Sentinela$(): registra todo som a até 16; tocada, '
          'lê os cinco últimos. Mais de 20 por minuto: -5 de moral. Em Queixa Formal, um grito: Escuridão, 10 s, a '
          'até 16.'),
         ('text',
          '$(l)Morale$() sets the pace: Engaged, a fifth faster; Disengaged, half speed, and it complains every two '
          'minutes. A Disengaged ghast cries two tears.',
          'A $(l)moral$() dita o ritmo: Engajado, um quinto mais rápido; Desmotivado, pela metade, e reclama a cada '
          'dois minutos. Ghast desmotivado chora duas lágrimas.'),
     ],
     'note': ('A head taken off its plinth says goodbye. It leaves the stapler.',
              'Cabeça tirada do pedestal se despede. Deixa o grampeador.')},

    {'cat': 'staffing', 'id': 'lookouts', 'sort': 11, 'icon': 'headinajar:turret_pedestal',
     'en': 'Lookouts', 'pt': 'Vigias',
     'pages': [
         ('text',
          'Any head takes a $(l)Turret Pedestal$(). The thirteen that fight still fight. Any other is a '
          "$(l)Lookout$(): a hostile within 16, in sight, and it raises its species' alarm every 5 s and makes the "
          'hostile glow. 1 FE/t, 2 alarmed.',
          'Toda cabeça serve num $(l)Pedestal de Torreta$(). As treze que lutam seguem lutando. As outras são '
          '$(l)Vigias$(): com um hostil à vista a até 16, dão o alarme da espécie a cada 5 s e o fazem brilhar. 1 '
          'FE/t, 2 em alarme.'),
         ('text',
          'A $(l)comparator$() on the pedestal reads 15 while it is alarmed, or while a fighting head has a target, '
          'and 2 s after.$(br2)Wire it to a redstone lamp: the cow lights the hall.',
          'Um $(l)comparador$() no pedestal dá 15 em alarme, ou quando a cabeça que luta tem alvo, e por 2 s '
          'depois.$(br2)Ligue numa lâmpada de redstone: a vaca acende o salão.'),
     ],
     'note': ('Cow heads do not bite. That is not the point.', 'Cabeça de vaca não morde. Não é essa a questão.')},

    {'cat': 'research', 'id': LIBRARY, 'sort': 9, 'icon': 'headinajar:blood_sample',
     'en': 'Living Library', 'pt': 'Biblioteca Viva',
     'pages': [
         ('text',
          'A placed head gives blood once a day, dawn to dawn, unharmed: its usual tool, quality 70 to 95; 50 to 75 '
          'from a trophy or a hungry head. Staff lose 10 morale. Only the employer, and those they trust, may draw '
          'one.',
          'Cabeça pousada doa sangue uma vez por dia, sem se ferir: ferramenta de sempre, qualidade 70 a 95; 50 a 75 '
          'se for troféu ou estiver com fome. Funcionário perde 10 de moral. Só o empregador, e quem ele confia, '
          'coleta.'),
         ('text',
          'A $(l)Leech Jar$() touching a plinth draws it once a day. A station draws its head; a Cerebral Array, its '
          'first head not drawn today.',
          'Um $(l)Pote de Sanguessugas$() encostado no pedestal coleta uma vez por dia. Estação doa pela cabeça dela; '
          'a Matriz Cerebral, pela primeira que não doou hoje.'),
     ],
     'note': ('The best donor is a head that cannot run away.', 'O melhor doador é uma cabeça que não pode fugir.')},
]

# Pay and Morale (gen_p37's entry), brought up to date in place: lunch hires, and the dawn has more to it
PAY_PAGES = {
    0: ('text',
        'A placed head is a $(l)trophy$() until someone hires it: by paying its wage by hand (wheat for a cow, two '
        'rotten flesh for a zombie) or by giving it $(l:headinajar:staffing/desk)lunch$(/l). Whoever does is its '
        'employer.',
        'Uma cabeça pousada é um $(l)troféu$() até alguém contratá-la: pagando o salário na mão (trigo para a vaca, '
        'duas carnes podres para o zumbi) ou dando $(l:headinajar:staffing/desk)almoço$(/l). Quem faz isso é o '
        'empregador.'),
    5: ('text',
        'A villager head is never paid by hand: an emerald in hand opens its trades, as it always has. Lunch hires it, '
        'and slimes, ghasts, tadpoles and the others that draw no wage.',
        'Cabeça de aldeão nunca recebe na mão: esmeralda na mão abre as trocas, como sempre. O almoço a contrata, e '
        'também slimes, ghasts, girinos e os outros sem salário.'),
}
PAY_MORE = [
    ('form', ('Form 40-M (3)', 'Formulário 40-M (3)'),
     'At dawn, on lunch: fed, +5 (up to 60); hungry, -5 (down to 30).$(br)Colleague of its '
     'department within 8: +2. Rival within 4: -5. A dislike: -5.$(br)Any time: cake, +40, once a day; sampled, -10.',
     'Ao amanhecer, no almoço: comeu, +5 (até 60); com fome, -5 (até 30).$(br)Colega '
     'do departamento a até 8: +2. Rival a até 4: -5. Algo que detesta: -5.$(br)A qualquer hora: bolo, +40, um por '
     'dia; amostra, -10.'),
    ('text',
     '$(l)Dislikes$(): panda, thunder; bee, enderman, rain; polar bear, heat; zombie, drowned, zombie villager, '
     'skeleton, phantom, open sky; blaze, rain, water; strider, outside the Nether; snow golem, warmth, rain; hoglin, '
     'warped fungus.',
     '$(l)Detesta$(): panda, trovão; abelha, enderman, chuva; urso-polar, calor; zumbi, afogado, aldeão zumbi, '
     'esqueleto, espectro, céu aberto; blaze, chuva, água; marchador, fora do Nether; golem de neve, calor, chuva; '
     'hoglin, fungo distorcido.'),
    ('text',
     '$(l)Rivals$(): villager and zombie villager, pillager or vindicator; skeleton and wolf; piglin and wither '
     'skeleton; creeper and cat or ocelot; spider and armadillo.',
     '$(l)Rivais$(): aldeão e aldeão zumbi, saqueador ou vindicador; esqueleto e lobo; piglin e esqueleto-wither; '
     'creeper e gato ou jaguatirica; aranha e tatu.'),
]

# The Turret's entry: a head with no attack is no longer refused
TURRET_PAGE = ('text',
               'Fit a Jarred Head: it fights hostiles within 16 blocks as the mob did. Skeletons need arrows in a chest '
               'beside or below; a creeper head blows up once. Any other head is a '
               '$(l:headinajar:staffing/lookouts)Lookout$(/l). 1 FE/t watching, 10 firing.',
               'Encaixe uma Cabeça no Pote: ela luta contra hostis a até 16 blocos como a criatura. Esqueletos pedem '
               'flechas num baú ao lado ou embaixo; cabeça de creeper explode uma vez. As outras viram '
               '$(l:headinajar:staffing/lookouts)Vigias$(/l). 1 FE/t vigiando, 10 atirando.')

# The written edition grows by this release's pages; gen_p27_clarity's cap (120) is raised to this here
WRITTEN_PAGES = 132

# ---------------------------------------------------------------------------------------------------------------
# The longest real arguments, for the action-bar width check (vanilla's own names, en and pt, and the mod's)
NAME16 = 'M' * 16
COLOURS = (['Light Gray', 'Light Blue'], ['verde-limão', 'cinza-clara', 'azul-clara'])
FLOWERS = (['Lily of the Valley', 'Orange Tulip'], ['Tulipa Vermelha', 'Rosa-do-Wither', 'Dente-de-Leão'])
BUCKETS = (['Bucket of Tropical Fish', 'Bucket of Cranial Saline', 'Powder Snow Bucket'],
           ['Balde com Peixe Tropical', 'Balde de Soro Craniano', 'Balde de Neve em Pó'])
# the longest item names there are, for a line with one item in it
ITEMS = (['Waxed Weathered Cut Copper Stairs', 'Polished Blackstone Pressure Plate', 'Reinforced Life Support Jar'],
         ['Escadas de Cobre Lapidado Desgastado Encerado', 'Placa Suspensa de Carvalho Escuro de Parede',
          'Pote de Suporte Vital Reforçado'])
# a long, common name, for a line with two (the writer's check uses the same)
ITEM_LONG = (['Light Gray Glazed Terracotta'], ['Terracota Vitrificada Cinza-Clara'])
# a villager's trades with two costs: the librarian's book, the cartographer's compass, the fletcher's arrows
OFFERS2 = ([['64', 'Emerald', '1', 'Compass', '1', 'Woodland Explorer Map'],
            ['64', 'Emerald', '5', 'Arrow', '5', 'Arrow of the Turtle Master'],
            ['64', 'Emerald', '1', 'Book', '1', 'Enchanted Book']],
           [['64', 'Esmeralda', '1', 'Bússola', '1', 'Mapa de Exploração da Floresta'],
            ['64', 'Esmeralda', '5', 'Flecha', '5', 'Flecha de Respiração Aquática'],
            ['64', 'Esmeralda', '1', 'Livro', '1', 'Livro Encantado']])
OFFERS1 = ([['64', 'Emerald', '64', 'Light Gray Glazed Terracotta']],
           [['64', 'Esmeralda', '64', 'Terracota Vitrificada Cinza-Clara']])
# the widest species names a Lookout can be (the staff table's rows that do not attack)
LOOKOUT_NAMES = (['Wandering Trader', 'Wither Skeleton', 'Zombified Piglin'],
                 ['Aranha-das-Cavernas', 'Mercador Ambulante', 'Piglin-Zumbificado'])
NO_WAGE_NAMES = (['Tropical Fish', 'Pufferfish', 'Illusioner', 'Magma Cube'],
                 ['Peixe Tropical', 'Baiacu', 'Ilusionista', 'Girino'])
ACTION_BAR_ARGS = {  # key -> a function of the language index giving every argument list to try
    'desk.headinajar.villager.best_offer': lambda i: OFFERS1[i],
    'desk.headinajar.villager.best_offer2': lambda i: OFFERS2[i],
    'desk.headinajar.sheep.dyed': lambda i: [[c] for c in COLOURS[i]],
    'desk.headinajar.mooshroom.flower': lambda i: [[f] for f in FLOWERS[i]],
    'desk.headinajar.chicken.hour': lambda i: [['12']],
    'desk.headinajar.allay.took': lambda i: [[n] for n in ITEMS[i]],
    'desk.headinajar.allay.called': lambda i: [[n] for n in ITEMS[i]],
    'desk.headinajar.allay.swapped': lambda i: [[n, ITEM_LONG[i][0]] for n in ITEMS[i]],
    'refusal.headinajar.desk.bowl_full': lambda i: [['3.0']],
    'refusal.headinajar.desk.wrong_bucket': lambda i: [[b] for b in BUCKETS[i]],
    'refusal.headinajar.desk.same_colour': lambda i: [[c] for c in COLOURS[i]],
    'refusal.headinajar.desk.allay_has': lambda i: [[n] for n in ITEMS[i]],
    'refusal.headinajar.staff.no_wage': lambda i: [[n] for n in NO_WAGE_NAMES[i]],
    'warning.headinajar.turret.lookout': lambda i: [[n] for n in LOOKOUT_NAMES[i]],
    'message.headinajar.staff.on_lunch': lambda i: [[NAME16]],
    'message.headinajar.staff.cake': lambda i: [[NAME16]],
}
ACTION_BAR_PX = 400


def _say(table, prefix, line):
    """A say line (en_noise, en_hr, pt_noise, pt_hr) as the two keys <prefix>.noise and <prefix>.hr."""
    en_noise, en_hr, pt_noise, pt_hr = line
    table[0][prefix + '.noise'], table[0][prefix + '.hr'] = en_noise, en_hr
    table[1][prefix + '.noise'], table[1][prefix + '.hr'] = pt_noise, pt_hr


def _lang(EN, PT):
    """Every line from the writer's module, as the Java reads it (block/HeadSpeak.java, block/DeskJobs.java)."""
    t = (EN, PT)
    EN['speech.headinajar.frame'], PT['speech.headinajar.frame'] = L.SAY
    for path, (en, pt) in L.TITLES.items():
        EN[f'title.headinajar.minecraft.{path}'], PT[f'title.headinajar.minecraft.{path}'] = en, pt
    for prof, (en, pt) in list(L.VILLAGER_TITLES.items()) + list(PROFESSIONS.items()):
        EN[f'title.headinajar.minecraft.villager.{prof}'], PT[f'title.headinajar.minecraft.villager.{prof}'] = en, pt
    for path, lines in L.BELLS.items():
        for n, line in enumerate(lines, 1):
            _say(t, f'bell.headinajar.minecraft.{path}.{n}', line)
    for n, line in enumerate(L.NITWIT_BELLS, 1):
        _say(t, f'bell.headinajar.minecraft.villager.nitwit.{n}', line)
    for path, own in L.COMPLAINTS.items():
        for cid, (en, pt) in own.items():
            EN[f'complaint.headinajar.minecraft.{path}.{cid}'], PT[f'complaint.headinajar.minecraft.{path}.{cid}'] = en, pt
    EN['goodbye.headinajar.default'], PT['goodbye.headinajar.default'] = L.GOODBYE
    for path, line in L.GOODBYES.items():
        _say(t, f'goodbye.headinajar.minecraft.{path}', line)
    for path, line in L.ALARMS.items():
        _say(t, f'lookout.headinajar.minecraft.{path}', line)
    _say(t, 'lookout.headinajar.default', L.ALARM_DEFAULT)
    for k, (en, pt) in L.DESK_PLAIN.items():
        EN[f'desk.headinajar.{k}'], PT[f'desk.headinajar.{k}'] = en, pt
    for k, line in L.DESK_SAY.items():
        _say(t, f'desk.headinajar.{k}', line)
    for freq, (en_one, en_many, pt_one, pt_many) in L.NOISE_LOG.items():
        EN[f'desk.headinajar.warden.part.{freq}.one'], EN[f'desk.headinajar.warden.part.{freq}.many'] = en_one, en_many
        PT[f'desk.headinajar.warden.part.{freq}.one'], PT[f'desk.headinajar.warden.part.{freq}.many'] = pt_one, pt_many


def _staff_file(path, wage):
    from gen_p37_foundations import _wage
    out = {'entity': MC + path, 'wage': _wage(wage), 'noise': f'staff.headinajar.noise.{path}',
           'bells': len(L.BELLS[path])}
    if path in DESK:
        out['desk'] = path
    if path not in ATTACKERS:
        out['alarm'] = MC + ALARM[path]
    if path in DISLIKES:
        out['dislikes'] = DISLIKES[path]
    lines = {}
    if path == 'villager':
        lines['bells.nitwit'] = len(L.NITWIT_BELLS)
    for cid in L.COMPLAINTS.get(path, {}):
        lines[f'complaint.{cid}'] = 1
    if path in L.GOODBYES:
        lines['goodbye'] = 1
    if path in L.ALARMS:
        lines['lookout'] = 1
    if lines:
        out['lines'] = lines
    return out


def _sounds(EN, PT, write):
    path = os.path.join(RES, 'assets', 'headinajar', 'sounds.json')
    with open(path, encoding='utf-8') as f:
        table = json.load(f)
    for name, (of, volume, pitch, en, pt) in SOUNDS.items():
        assert len(en) <= 24 and len(pt) <= 30, name
        assert not of.startswith('headinajar:') or of.split(':', 1)[1] in table, of
        key = f'subtitles.headinajar.{name}'
        EN[key], PT[key] = en, pt
        sound = {'name': of, 'type': 'event'}
        if volume != 1.0:
            sound['volume'] = volume
        if pitch != 1.0:
            sound['pitch'] = pitch
        table[name] = {'sounds': [sound], 'subtitle': key}
    name, file, en, pt = SHUSH
    assert len(en) <= 24 and len(pt) <= 30, name
    assert os.path.exists(os.path.join(RES, 'assets', 'headinajar', 'sounds', file.split(':', 1)[1] + '.ogg')), \
        'run python tools/make_sounds.py head/warden_shush'
    key = f'subtitles.headinajar.{name}'
    EN[key], PT[key] = en, pt
    table[name] = {'sounds': [{'name': file, 'attenuation_distance': 16}], 'subtitle': key}
    write('assets/headinajar/sounds.json', table)


def _bowl(write):
    """The plinth's bowl, its five broth steps and its spoon, from the art's own file (merged with the art)."""
    if not os.path.exists(BOWL_ART):
        print('plinth bowl: art/heads/plinth_bowl.json is not in the tree yet; its models are not written')
        return
    with open(BOWL_ART, encoding='utf-8') as f:
        art = json.load(f)
    tex = art['texture']

    def model(elements):
        for e in elements:
            for face in e['faces'].values():
                assert face['texture'] == '#bowl', face
            # vanilla's block model loader takes only these element turns, and corners within -16..32
            assert abs(e.get('rotation', {}).get('angle', 0)) in (0, 22.5, 45), e
            assert all(-16 <= c <= 32 for c in e['from'] + e['to']), e
        return {'render_type': 'minecraft:cutout', 'textures': {'bowl': tex, 'particle': tex}, 'elements': elements}

    write('assets/headinajar/models/block/plinth_bowl.json', model(art['bowl']))
    assert sorted(art['broth']) == ['0', '1', '2', '3', '4'], sorted(art['broth'])
    for step in range(5):
        write(f'assets/headinajar/models/block/plinth_broth_{step}.json', model(art['broth'][str(step)]))
    spoon = art['spoon']
    write('assets/headinajar/models/block/plinth_spoon.json', model(spoon['elements']))
    write('assets/headinajar/heads/plinth_spoon.json',
          {k: spoon[k] for k in ('pivot', 'tap', 'tap_ticks', 'tap_sampled')})


def _raise_written_cap():
    """gen_p27_clarity keeps the written edition at 120 pages; this release's three entries need more. Its function
    is re-made here with the higher cap, word for word otherwise."""
    import inspect
    import gen_p27_clarity as c
    src = inspect.getsource(c.emit_handbook)
    if f'len(pages) <= {WRITTEN_PAGES}' in src:
        return
    old = 'assert len(pages) <= 120, f\'the written edition is {len(pages)} pages; keep it at 120 or under\''
    assert src.count(old) == 1, 'gen_p27_clarity.emit_handbook changed: look at its page cap again'
    src = src.replace(old, f"assert len(pages) <= {WRITTEN_PAGES}, f'the written edition is {{len(pages)}} pages; "
                           f"keep it at {WRITTEN_PAGES} or under'")
    exec(compile(src, c.__file__, 'exec'), c.__dict__)


def _handbook(EN, PT):
    from gen_p27_clarity import width, entry_pages, written_lines, wrap, PAGE_LINES, BOOK_WIDTH, TITLE_PX, ENTRY_NAME_PX
    import gen_p27_handbook_b as hb
    by_id = {(e['cat'], e['id']): e for e in hb.ENTRIES}
    pay = by_id[('staffing', 'pay')]
    if len(pay['pages']) == 6:  # gen_p37's six pages: two put right, three more
        for n, page in PAY_PAGES.items():
            pay['pages'][n] = page
        pay['pages'] += PAY_MORE
    turret = by_id[('research', 'turret')]
    turret['pages'][0] = TURRET_PAGE
    for e in ENTRIES:
        if (e['cat'], e['id']) not in by_id:
            hb.ENTRIES.append(e)
    for e in ENTRIES + [pay, turret]:
        for lang, table in (('en', EN), ('pt', PT)):
            assert width(e[lang]) <= ENTRY_NAME_PX, f'entry name {e[lang]!r} is {width(e[lang])} px'
            for n, (kind, title, text) in enumerate(entry_pages(e, lang, table)):
                limit = PAGE_LINES['first' if n == 0 and kind == 'text' else kind]
                if title:
                    assert width(title) <= TITLE_PX, f'title {title!r} is {width(title)} px'
                    limit -= len(wrap(title, BOOK_WIDTH)) - 1
                lines = written_lines(text)
                assert len(lines) <= limit, f'{e["id"]} page {n + 1} ({lang}): {len(lines)} lines, room for {limit}'


def _composed(EN, PT):
    """Every action-bar line this release can show, filled with its longest real arguments: (key, lang, line)."""
    from gen_p27_clarity import fill
    out = []
    for i, table in enumerate((EN, PT)):
        lang = ('en', 'pt')[i]
        frame = L.SAY[i]

        def say(key, noise, hr):
            out.append((key, lang, fill(frame, [noise, hr])))

        for path, lines in list(L.BELLS.items()) + [('villager.nitwit', L.NITWIT_BELLS)]:
            for n, line in enumerate(lines, 1):
                hr = line[1 + 2 * i]
                if path in L.BELL_ARGS:
                    hr = fill(hr, ['999'])
                say(f'bell {path} {n}', line[2 * i], hr)
        for path in STAFF:
            noise = table[f'staff.headinajar.noise.{path}']
            own = L.COMPLAINTS.get(path, {})
            for cid in L.COMPLAINT_IDS:
                hr = own[cid][i] if cid in own else table[f'staff.headinajar.complaint.{cid}']
                say(f'complaint {path} {cid}', noise, hr)
        # a big head's first ring: its noise and "It is a snug fit."
        from gen_p37_foundations import REINFORCED_JAR, EXECUTIVE_JAR
        for path in REINFORCED_JAR + EXECUTIVE_JAR + ['slime', 'magma_cube']:
            say(f'snug {path}', table[f'staff.headinajar.noise.{path}'], table['bell.headinajar.snug_fit'])
        for group in (L.GOODBYES, L.ALARMS, L.DESK_SAY, {'default': L.ALARM_DEFAULT}):
            for k, line in group.items():
                say(k, line[2 * i], line[1 + 2 * i])
        # the warden's log at its widest: five sounds of five kinds
        parts = sorted((table[f'desk.headinajar.warden.part.{f}.one'] for f in L.NOISE_LOG), key=len)[-5:]
        out.append(('warden.log', lang, fill(table['desk.headinajar.warden.log'],
                                             [table['desk.headinajar.warden.log_join'].join(parts)])))
        plain = [k for k, _e, _p in LINES if k.startswith(('desk.', 'refusal.', 'warning.', 'message.'))]
        plain += [f'desk.headinajar.{k}' for k in L.DESK_PLAIN if not k.startswith(('status.', 'warden.'))]
        for key in plain:
            for args in ACTION_BAR_ARGS.get(key, lambda _i: [[]])(i):
                out.append((key, lang, fill(table[key], args)))
    return out


def run(EN, PT, write, shaped):
    from gen_p27_clarity import width

    taken = [key for key, _e, _p in LINES if key in EN and key not in REPLACED]
    assert not taken, f'already in the language files: {taken}'
    for key, en, pt in LINES:
        EN[key], PT[key] = en, pt
    _lang(EN, PT)

    # the writer's module covers every row of the staff table
    assert set(L.BELLS) == set(STAFF), set(L.BELLS) ^ set(STAFF)
    assert set(L.ALARMS) == set(STAFF) - set(ATTACKERS), set(L.ALARMS) ^ (set(STAFF) - set(ATTACKERS))
    assert set(ALARM) == set(STAFF) - set(ATTACKERS), set(ALARM) ^ (set(STAFF) - set(ATTACKERS))
    assert set(DESK) <= set(STAFF) and set(DISLIKES) <= set(STAFF)
    for path, wage in ((p, row[0]) for p, row in STAFF.items()):
        write(f'data/headinajar/headinajar/staff/{path}.json', _staff_file(path, wage))

    write('data/headinajar/tags/block/nether_floor.json', {'values': NETHER_FLOOR})
    _sounds(EN, PT, write)

    for adv, parent, icon, en_t, en_d, pt_t, pt_d in ADVANCEMENTS:
        write(f'data/headinajar/advancement/{adv}.json', {
            'parent': f'headinajar:{parent}', 'criteria': _event(adv),
            'display': {'icon': {'id': icon}, 'title': {'translate': f'advancements.headinajar.{adv}.title'},
                        'description': {'translate': f'advancements.headinajar.{adv}.description'}, 'frame': 'task',
                        'show_toast': True, 'announce_to_chat': True, 'hidden': False}})
        for table, t, d in ((EN, en_t, en_d), (PT, pt_t, pt_d)):
            table[f'advancements.headinajar.{adv}.title'], table[f'advancements.headinajar.{adv}.description'] = t, d

    _bowl(write)
    _raise_written_cap()
    _handbook(EN, PT)

    # the action bar: every composed line under 400 px, in both languages
    lines = _composed(EN, PT)
    over = [(k, lang, width(line), line) for k, lang, line in lines if width(line) >= ACTION_BAR_PX]
    assert not over, f'{len(over)} action-bar lines over {ACTION_BAR_PX} px: {over[:5]}'
    widest = max(lines, key=lambda x: width(x[2]))
    print(f'heads at work: {len(lines)} action-bar lines checked, the widest {width(widest[2])} px ({widest[0]}, '
          f'{widest[1]}): {widest[2]}')

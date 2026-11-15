"""Lines and data for the 0.6.0 release (Foundations): the Surgical Saw's table, the Personnel File, departments,
wages and morale, the Exit Interview and the Condolence Card. Imported by gen_data.py, before the handbook generator
(gen_p27_clarity), whose text check and handbook read the final lines and entries.

Writes the saw's entity type tags (saw/*), the department tags (department/*), one staff file per vanilla species
(data/headinajar/headinajar/staff/<species>.json: its wage and its noise), the Condolence Card's item model, three
advancements, and appends the Personnel and Pay entries to the handbook's Temporary Staffing chapter.
"""
import json
import os

MC = 'minecraft:'

# ---------------------------------------------------------------------------------------------------------------
# The saw's table (SawTable reads these tags; a compat datapack can add rows)
HEAD_ONLY = ['slime', 'magma_cube', 'shulker', 'vex', 'bee', 'silverfish', 'endermite', 'tadpole']
REINFORCED_JAR = ['warden', 'elder_guardian', 'hoglin', 'zoglin', 'sniffer']
EXECUTIVE_JAR = ['ghast', 'ravager']

# ---------------------------------------------------------------------------------------------------------------
# Departments: (id, en name, en line, pt name, pt line, species with a tag or None where the code decides)
DEPARTMENTS = [
    ('livestock', 'Livestock', 'Our longest-serving staff, and our lunch.',
     'Pecuária', 'Nossos funcionários mais antigos, e nosso almoço.',
     ['cow', 'mooshroom', 'pig', 'sheep', 'chicken', 'goat', 'rabbit']),
    ('field_office', 'Field Office', 'Out of the office. Always.',
     'Trabalho de Campo', 'Fora do escritório. Sempre.',
     ['fox', 'panda', 'bee', 'parrot', 'armadillo', 'sniffer', 'frog', 'tadpole']),
    ('logistics', 'Logistics', 'Things go where they are told. Eventually.',
     'Logística', 'As coisas vão aonde mandamos. Uma hora chegam.',
     ['horse', 'donkey', 'mule', 'llama', 'trader_llama', 'camel', 'enderman', 'endermite', 'shulker', 'allay']),
    ('security', 'Security', 'Trained to tell visitors from intruders. Mostly.',
     'Segurança', 'Treinados para distinguir visita de invasor. Quase sempre.',
     ['iron_golem', 'wolf', 'cat', 'ocelot', 'polar_bear']),
    ('night_shift', 'Night Shift', 'Daylight is a scheduling conflict.',
     'Turno da Noite', 'A luz do dia é um conflito de agenda.',
     ['zombie', 'husk', 'drowned', 'zombie_villager', 'skeleton', 'stray', 'bogged', 'skeleton_horse', 'phantom',
      'zombified_piglin', 'zoglin', 'zombie_horse', 'giant']),
    ('facilities', 'Facilities', 'Heating, cooling and the walls.',
     'Instalações', 'Aquecimento, refrigeração e as paredes.',
     ['blaze', 'magma_cube', 'slime', 'ghast', 'strider', 'breeze', 'snow_golem', 'spider', 'cave_spider',
      'silverfish', 'bat']),
    ('accounts', 'Accounts', 'Paid in gold. Collects the rest.',
     'Contabilidade', 'Recebe em ouro. Cobra o resto.',
     ['piglin', 'piglin_brute', 'hoglin', 'wither_skeleton']),
    ('legal', 'Legal', 'Fangs, contracts, axes. All binding.',
     'Jurídico', 'Presas, contratos, machados. Tudo com força de lei.',
     ['evoker', 'vindicator', 'pillager', 'illusioner', 'vex', 'ravager', 'witch']),
    ('sales', 'Sales', 'Hrm.',
     'Vendas', 'Hrm.',
     ['villager', 'wandering_trader']),
    ('marine', 'Marine Division', 'Our offshore office.',
     'Divisão Marítima', 'Nossa filial em alto-mar.',
     ['squid', 'glow_squid', 'cod', 'salmon', 'tropical_fish', 'pufferfish', 'dolphin', 'turtle', 'axolotl',
      'guardian']),
    ('demolitions', 'Demolitions', 'Each member gives the role everything, once.',
     'Demolições', 'Cada membro dá tudo de si pela função, uma vez.',
     ['creeper']),
    ('senior_management', 'Senior Management', 'Every office has one. Nobody knows what it does.',
     'Alta Gerência', 'Todo escritório tem uma. Ninguém sabe o que ela faz.',
     ['elder_guardian', 'warden', 'wither']),
    ('board', 'The Board', 'The Board is not a department. The Board is the Board.',
     'O Conselho', 'O Conselho não é um departamento. O Conselho é o Conselho.', None),
    ('temp_agency', 'Temp Agency', 'Bodies by the hour. Bring your own head.',
     'Agência de Temporários', 'Corpos por hora. Traga a própria cabeça.', None),
    ('general', 'General Staff', 'We are an equal-opportunity employer.',
     'Pessoal Geral', 'Aqui, oportunidade igual para todos.', None),
]

# ---------------------------------------------------------------------------------------------------------------
# The staff table: species -> (wage, en noise, pt noise). A wage is an item id, (item id, count), ('#tag', count) or
# None ("none": it counts as paid). The noise is what the species says, quoted in its Exit Interview. Every vanilla
# creature but the Ender Dragon (never staff) has a row.
STAFF = {
    # Livestock
    'cow': ('wheat', 'Moo.', 'Muu.'),
    'mooshroom': ('wheat', 'Moo.', 'Muu.'),
    'pig': ('carrot', 'Oink.', 'Oinc.'),
    'sheep': ('wheat', 'Baa.', 'Mééé.'),
    'chicken': ('wheat_seeds', 'Bawk.', 'Cocó.'),
    'goat': ('wheat', 'Meh.', 'Béé.'),
    'rabbit': ('carrot', '...', '...'),
    # Field Office
    'fox': ('sweet_berries', 'Yip.', 'Iip.'),
    'panda': ('bamboo', 'Mmm.', 'Hmm.'),
    'bee': (('#minecraft:small_flowers', 1), 'Bzz.', 'Bzz.'),
    'parrot': ('wheat_seeds', 'Squawk.', 'Currupaco.'),
    'armadillo': ('spider_eye', '...', '...'),
    'sniffer': ('torchflower_seeds', 'Hfff.', 'Fffff.'),
    'frog': ('slime_ball', 'Croak.', 'Croac.'),
    'tadpole': (None, '...', '...'),
    # Logistics
    'horse': ('wheat', 'Hrrrr.', 'Hiiin.'),
    'donkey': ('wheat', 'Hrrrr.', 'Hiiin.'),
    'mule': ('wheat', 'Hrrrr.', 'Hiiin.'),
    'llama': ('wheat', 'Mrrp.', 'Mrrp.'),
    'trader_llama': ('wheat', 'Mrrp.', 'Mrrp.'),
    'camel': ('cactus', 'Hmmph.', 'Hmpf.'),
    'enderman': ('chorus_fruit', 'Vwoorp.', 'Vuorp.'),
    'endermite': (None, 'Skrr.', 'Skrr.'),
    'shulker': ('chorus_fruit', '...', '...'),
    'allay': ('amethyst_shard', 'La-la.', 'Lá-lá.'),
    # Security
    'iron_golem': ('iron_nugget', '...', '...'),
    'wolf': ('bone', 'Woof.', 'Au.'),
    'cat': ('cod', 'Mrrow.', 'Miau.'),
    'ocelot': ('salmon', '...', '...'),
    'polar_bear': ('cod', 'Hrrf.', 'Hrrf.'),
    # Night Shift
    'zombie': (('rotten_flesh', 2), 'Braaains.', 'Cérebrooos.'),
    'husk': (('rotten_flesh', 2), 'Hrrrgh.', 'Hrrrgh.'),
    'drowned': (('rotten_flesh', 2), 'Blrrgh.', 'Blrrgh.'),
    'zombie_villager': (('rotten_flesh', 2), 'Braaains.', 'Cérebrooos.'),
    'skeleton': ('bone_meal', '...', '...'),
    'stray': ('bone_meal', '...', '...'),
    'bogged': ('bone_meal', '...', '...'),
    'skeleton_horse': ('bone_meal', '...', '...'),
    'phantom': (None, 'Screee.', 'Iiiih.'),
    'zombified_piglin': ('gold_nugget', 'Hrrng.', 'Hrrng.'),
    'zoglin': (None, 'GRRRAH.', 'GRRRAH.'),
    'zombie_horse': (('rotten_flesh', 2), 'Hrrnnh.', 'Hiiinnh.'),
    'giant': (('rotten_flesh', 8), 'BRAAAINS.', 'CÉREBROOOS.'),
    # Facilities
    'blaze': ('coal', 'Fwoosh.', 'Fuuush.'),
    'magma_cube': ('coal', 'Splorch.', 'Splorch.'),
    'slime': (None, 'Blorp.', 'Blorp.'),
    'ghast': (None, 'Ooooooh.', 'Uuuuuuh.'),
    'strider': ('warped_fungus', 'Brrr.', 'Brrr.'),
    'breeze': (None, 'Whoosh.', 'Vuuush.'),
    'snow_golem': ('snowball', '...', '...'),
    'spider': ('string', 'Hsss.', 'Ssss.'),
    'cave_spider': ('string', 'Hss.', 'Sss.'),
    'silverfish': ('cobblestone', 'Skrt.', 'Skrt.'),
    'bat': (None, 'Eep.', 'Iic.'),
    # Accounts
    'piglin': ('gold_nugget', 'Hrmf.', 'Hrmf.'),
    'piglin_brute': ('gold_nugget', 'HRMF.', 'HRMF.'),
    'hoglin': ('crimson_fungus', 'Hrnk.', 'Ronc.'),
    'wither_skeleton': ('coal', 'Sssss.', 'Sssss.'),
    # Legal
    'evoker': ('emerald', 'Hrmm.', 'Hrmm.'),
    'vindicator': ('emerald', 'Hrrm.', 'Hrrm.'),
    'pillager': ('emerald', 'Hrmph.', 'Hrmph.'),
    'illusioner': (None, '...', '...'),
    'vex': (None, 'Hee.', 'Hihi.'),
    'ravager': ('wheat', 'HRRRRR.', 'HRRRRR.'),
    'witch': ('glass_bottle', 'Hehehe.', 'Hehehe.'),
    # Sales (a Nitwit is never paid: the code decides, as the Employee Mount does)
    'villager': ('emerald', 'Hrm.', 'Hrm.'),
    'wandering_trader': ('emerald', 'I have places to be.', 'Tenho mais o que fazer.'),
    # Marine Division
    'squid': ('dried_kelp', '...', '...'),
    'glow_squid': ('dried_kelp', '...', '...'),
    'cod': (None, '...', '...'),
    'salmon': (None, '...', '...'),
    'tropical_fish': (None, '...', '...'),
    'pufferfish': (None, '...', '...'),
    'dolphin': ('cod', 'Eee.', 'Iii.'),
    'turtle': ('seagrass', '...', '...'),
    'axolotl': ('tropical_fish', '...', '...'),
    'guardian': ('cod', '...', '...'),
    # Demolitions
    'creeper': ('gunpowder', 'Ssss.', 'Ssss.'),
    # Senior Management
    'elder_guardian': ('cod', '...', '...'),
    'warden': (None, 'Shh.', 'Shh.'),
    'wither': (None, 'Hrrrkh.', 'Hrrrkh.'),
}
NOISE_DEFAULT = ('...', '...')  # a creature from another mod

NAMES_EN = ['Bessie', 'Gary', 'Brenda', 'Keith', 'Ernest', 'Doreen', 'Nigel', 'Maureen', 'Trevor', 'Sandra', 'Clive',
            'Pam', 'Derek', 'Shirley', 'Colin', 'Janet']
NAMES_PT = ['Cleide', 'Valdir', 'Rosângela', 'Josué', 'Marlene', 'Edivaldo', 'Sueli', 'Gérson', 'Neide', 'Ademir',
            'Rosimeire', 'Claudionor', 'Vanderléia', 'Josenildo', 'Lucimar', 'Genivaldo']

# ---------------------------------------------------------------------------------------------------------------
# Lines: (key, en, pt)
LINES = [
    # the saw's refusals (Refusals: the refusal sound, the line, a head shake)
    ('refusal.headinajar.saw.baby', "Child labour is against company policy. Come back when it's grown.",
     'Trabalho infantil é contra a política da empresa. Volte quando crescer.'),
    ('refusal.headinajar.saw.jar_only.slime', 'A slime is all head. Bring a jar; there would be no body to keep.',
     'Um slime é só cabeça. Traga um pote; não sobraria corpo para guardar.'),
    ('refusal.headinajar.saw.jar_only.shulker',
     'The shulker is already a head in a box. Bring a jar; it will feel at home.',
     'O shulker já é uma cabeça numa caixa. Traga um pote; vai se sentir em casa.'),
    ('refusal.headinajar.saw.jar_only.vex', 'There is nothing inside a vex to keep but its head. Bring a jar.',
     'Não há nada num vex para guardar além da cabeça. Traga um pote.'),
    ('refusal.headinajar.saw.jar_only.bee', 'A bee is mostly head and stripes. Bring a jar.',
     'Uma abelha é quase só cabeça e listras. Traga um pote.'),
    ('refusal.headinajar.saw.jar_only.small', 'Too small to keep a body. Bring a jar.',
     'Pequeno demais para ter corpo. Traga um pote.'),
    ('refusal.headinajar.saw.jar_only.tadpole', 'Mostly head. Bring a jar, or come back when it is a frog.',
     'Quase só cabeça. Traga um pote, ou volte quando for sapo.'),
    ('refusal.headinajar.saw.jar_only', 'Mostly head. Bring a jar.', 'Quase só cabeça. Traga um pote.'),
    ('refusal.headinajar.saw.jar_small.reinforced', "The %s's head needs a Reinforced Life Support Jar or bigger.",
     'Cabeça de %s: só num Pote de Suporte Vital Reforçado ou maior.'),
    ('refusal.headinajar.saw.jar_small.executive', "The %s's head needs an Executive Life Support Jar.",
     'Cabeça de %s: só num Pote de Suporte Vital Executivo.'),
    ('refusal.headinajar.saw.dragon', 'The Dragon is a Board matter. See the Board Room Core.',
     'O Dragão é assunto do Conselho. Procure o Núcleo da Sala de Reuniões.'),
    ('refusal.headinajar.saw.wither', 'The Wither has three heads and no Human Resources department.',
     'O Wither tem três cabeças e nenhum departamento de Recursos Humanos.'),
    ('refusal.headinajar.saw.boss', 'Bosses are off limits. Legal has been very clear.',
     'Chefões estão fora de questão. O Jurídico foi muito claro.'),
    ('refusal.headinajar.saw.named', 'This one has a name. Somebody cares. The saw does not cut named creatures.',
     'Este tem nome. Alguém se importa. A serra não corta criaturas com nome.'),
    ('refusal.headinajar.saw.pet', "That is %s's pet. The saw does not cut other people's pets.",
     'Esse bicho é de %s. A serra não corta bichos dos outros.'),
    ('refusal.headinajar.saw.host', 'Already a host. One cut per creature.', 'Já é um hospedeiro. Um corte por criatura.'),
    ('refusal.headinajar.saw.machine', 'That is company equipment, not a colleague. Nothing to cut.',
     'Isso é equipamento da empresa, não um colega. Não há o que cortar.'),
    ('refusal.headinajar.saw.furniture', 'That one came headless and empty. It is furniture.',
     'Esse já veio sem cabeça e vazio. É mobília.'),
    ('refusal.headinajar.saw.never', 'This one is not for cutting. HR keeps a list.',
     'Este não é para cortar. O RH tem uma lista.'),
    # (saw.too_lively: today's saw_too_healthy, copied in run())

    # pay and the file
    ('refusal.headinajar.staff.paid_today', 'It was paid today.', 'Já recebeu hoje.'),
    ('refusal.headinajar.staff.wrong_wage', '%s is paid in %s. HR has the paperwork.',
     '%s recebe em %s. O RH tem a papelada.'),
    ('refusal.headinajar.staff.no_wage', 'A %s draws no wage. HR files it as a trophy.',
     'Espécie sem salário: %s. O RH arquiva como troféu.'),
    ('refusal.headinajar.staff.not_yours', '%s reports to %s. Ask them to trust you.',
     '%s responde a %s. Peça que confie em você.'),
    ('refusal.headinajar.staff.too_far', 'Renames are done in person. Come closer to the head.',
     'Renomear, só pessoalmente. Chegue mais perto da cabeça.'),

    # the action bar
    ('message.headinajar.saw.pumpkin', 'Pumpkin removed. The head underneath is snow. Saw again.',
     'Abóbora removida. A cabeça por baixo é de neve. Serre de novo.'),
    ('message.headinajar.staff.hired', '%s is on the payroll. Wages at dawn, from a chest beside it.',
     '%s está na folha. Salário ao amanhecer, de um baú ao lado.'),
    ('message.headinajar.staff.paid', 'Paid. %s is grateful, briefly.', 'Pago. %s agradece, por pouco tempo.'),

    # the file
    ('staff.headinajar.someone', 'another employee', 'outro funcionário'),
    ('staff.headinajar.on_file', 'on file', 'em arquivo'),
    ('staff.headinajar.band.engaged', 'Engaged', 'Engajado'),
    ('staff.headinajar.band.content', 'Content', 'Satisfeito'),
    ('staff.headinajar.band.disengaged', 'Disengaged', 'Desmotivado'),
    ('staff.headinajar.band.grievance', 'Grievance', 'Queixa Formal'),
    ('staff.headinajar.band.trophy', 'Trophy', 'Troféu'),
    # history: a lang key, a day and an argument, so each reader sees their own language
    ('staff.headinajar.history.sampled', 'Sampled day %s.', 'Amostra de sangue colhida no dia %s.'),
    ('staff.headinajar.history.jarred', 'Jarred day %s.', 'Entrou no pote no dia %s.'),
    ('staff.headinajar.history.host', 'Became a host day %s.', 'Virou hospedeiro no dia %s.'),
    ('staff.headinajar.history.shell', 'Taken on through the Temp Agency day %s.',
     'Entrou pela Agência de Temporários no dia %s.'),
    ('staff.headinajar.history.placed', 'Placed by %s day %s.', 'Mesa designada por %s no dia %s.'),
    ('staff.headinajar.history.fitted', 'Posted to the %s by %s day %s.', 'Lotação: %s, por %s, no dia %s.'),
    ('staff.headinajar.history.hired', 'Put on the payroll by %s day %s.', 'Admissão por %s no dia %s.'),
    ('staff.headinajar.history.renamed', 'Renamed by %s day %s.', 'Nome trocado por %s no dia %s.'),
    ('staff.headinajar.history.retro', 'Registered retroactively, day %s.', 'Registro retroativo no dia %s.'),
    ('staff.headinajar.history.left', 'Left the company day %s: %s.', 'Saiu da empresa no dia %s: %s.'),
    ('staff.headinajar.history.board', 'Seated on the Board day %s.', 'Assento no Conselho desde o dia %s.'),
    # leaving (chat, the employer only): name, species, department, days of service, the noise, the reason
    ('staff.headinajar.exit', "[Exit Interview] %s (%s), %s, %s days: '%s' Reason for leaving: %s.",
     "[Entrevista de Desligamento] %s (%s), %s, %s dias: '%s' Motivo da saída: %s."),
    ('staff.headinajar.reason.fall', 'fell from a high place', 'caiu de um lugar alto'),
    ('staff.headinajar.reason.lava', 'lava', 'lava'),
    ('staff.headinajar.reason.drowned', 'drowned', 'afogou-se'),
    ('staff.headinajar.reason.explosion', 'an explosion', 'uma explosão'),
    ('staff.headinajar.reason.management', 'management', 'a gerência'),
    ('staff.headinajar.reason.wastage', 'natural wastage', 'desgaste natural'),
    ('staff.headinajar.reason.other', 'a workplace incident', 'um acidente de trabalho'),

    # items
    ('item.headinajar.condolence_card', 'Condolence Card', 'Cartão de Condolências'),
    ('item.headinajar.condolence_card.sorry', 'HR is sorry for your loss of %s. The desk will be cleared by Tuesday.',
     'O RH lamenta a perda de %s. A mesa será esvaziada até terça.'),
    ('item.headinajar.condolence_card.record', 'No. %s · %s · day %s', 'Nº %s · %s · dia %s'),
    ('item.headinajar.jarred_head.jar', 'In a %s', 'Num %s'),
    ('item.headinajar.jarred_head.staff', 'No. %s · %s', 'Nº %s · %s'),

    # Jade
    ('jade.headinajar.staff', 'No. %s · %s', 'Nº %s · %s'),
    ('jade.headinajar.staff.employer', "No. %s · %s · %s's staff", 'Nº %s · %s · equipe de %s'),
    ('jade.headinajar.staff.on_file', 'No. %s · %s · Employer: on file', 'Nº %s · %s · Empregador: em arquivo'),
    ('jade.headinajar.staff.name', '%s (%s)', '%s (%s)'),
    ('jade.headinajar.staff.board', "The Board · %s directors · %s's staff", 'O Conselho · %s diretores · equipe de %s'),
    ('jade.headinajar.staff.band', 'Morale: %s', 'Moral: %s'),
    ('config.jade.plugin_headinajar.staff', 'Staff numbers, employers and morale',
     'Matrícula, empregador e moral dos funcionários'),

    # the file card
    ('screen.headinajar.file.title', 'PERSONNEL FILE', 'FICHA FUNCIONAL'),
    # the card's tab: the title and the staff number on one line (room 142 px on the card)
    ('screen.headinajar.file.title_number', 'PERSONNEL FILE  No. %s', 'FICHA FUNCIONAL  Nº %s'),
    ('screen.headinajar.file.number', 'Staff No.', 'Matrícula'),
    ('screen.headinajar.file.name', 'Name', 'Nome'),
    ('screen.headinajar.file.species', 'Species', 'Espécie'),
    ('screen.headinajar.file.department', 'Department', 'Departamento'),
    ('screen.headinajar.file.employer', 'Employer', 'Empregador'),
    ('screen.headinajar.file.joined', 'Joined', 'Admissão'),
    ('screen.headinajar.file.morale', 'Morale', 'Moral'),
    ('screen.headinajar.file.wage', 'Wage', 'Salário'),
    ('screen.headinajar.file.jar', 'Jar', 'Pote'),
    ('screen.headinajar.file.genes', 'Genes', 'Genes'),
    ('screen.headinajar.file.history', 'History', 'Histórico'),
    ('screen.headinajar.file.joined_value', "Day %s (%s days' service)", 'Dia %s (%s dias de casa)'),
    ('screen.headinajar.file.wage_value', '%s %s a day', '%s %s por dia'),
    ('screen.headinajar.file.wage_none', 'None. Noted as paid.',
     'Nenhum. Consta como pago.'),
    ('screen.headinajar.file.paid_today', 'Paid today', 'Pago hoje'),
    ('screen.headinajar.file.unpaid', 'Unpaid %s days', 'Sem pagamento há %s dias'),
    ('screen.headinajar.file.genes_none', 'Nothing of note.', 'Nada digno de nota.'),
    ('screen.headinajar.file.left', 'Left the company day %s: %s.', 'Saiu da empresa no dia %s: %s.'),
    ('screen.headinajar.file.trophy', 'Trophy. Pay it its wage by hand to put it on the payroll.',
     'Troféu. Pague o salário na mão para pô-lo na folha.'),
    # beyond the key list: the trophy hint for a villager head, which is never paid by hand (an emerald in hand trades)
    ('screen.headinajar.file.trophy.villager',
     'Trophy. Villagers are never paid by hand: an emerald in hand trades.',
     'Troféu. Aldeão não recebe na mão: esmeralda na mão é negócio.'),
    ('screen.headinajar.file.file_it', 'File it', 'Arquivar'),
    ('screen.headinajar.file.rename', 'New name', 'Novo nome'),
    ('screen.headinajar.file.renamed', 'Now %s. HR has updated the file.', 'Agora é %s. O RH atualizou a ficha.'),

    # DOLORES
    ('memo.headinajar.staff_number',
     'Staff No. %s (%s) has been entered in the register. We do not lose staff. We occasionally misplace them.',
     'O funcionário Nº %s (%s) entrou no cadastro. Nós não perdemos funcionários. Às vezes os extraviamos.'),
    ('memo.headinajar.unpaid_dawn', 'Payroll was short this morning. %1$s noticed. %1$s will remember.',
     'A folha veio curta esta manhã. %1$s percebeu. %1$s não vai esquecer.'),
]

# ---------------------------------------------------------------------------------------------------------------
# Advancements (the headinajar:event trigger): id, parent, icon, hidden, en title, en description, pt title, pt description
ADVANCEMENTS = [
    ('entirely_head', 'separated', 'headinajar:jarred_head', True,
     'Entirely Head', 'The jar holds the whole slime. The slime has opinions about this.',
     'Só Cabeça', 'O pote guarda o slime inteiro. O slime tem opiniões sobre isso.'),
    ('staff_number', 'out_for_blood', 'minecraft:paper', False,
     'Staff Number', 'Have any creature entered in the register',
     'Número de Matrícula', 'Faça uma criatura entrar no cadastro'),
    ('pay_grade', 'staffed', 'minecraft:wheat', False,
     'Pay Grade', 'Pay a jarred head that is not a villager',
     'Faixa Salarial', 'Pague uma cabeça no pote que não seja de aldeão'),
]

# ---------------------------------------------------------------------------------------------------------------
# The handbook: two new entries in Temporary Staffing (the saw's own entry is rewritten in gen_p27_handbook_b)
ENTRIES = [
    {'cat': 'staffing', 'id': 'personnel', 'sort': 8, 'icon': 'minecraft:paper',
     'en': 'Personnel Files', 'pt': 'Fichas Funcionais',
     'pages': [
         ('text',
          'The first time the company touches a creature (a sample, a cut, a head set down or fitted) it gets a staff '
          'number and a Personnel File. Old heads and hosts are filed as they turn up.',
          'Na primeira vez que a empresa encosta numa criatura (amostra, corte, cabeça pousada ou instalada), ela '
          'ganha matrícula e ficha. Cabeças e hospedeiros antigos são fichados quando aparecem.'),
         ('text',
          'Jade shows anyone the number, department and employer; the name only to the employer and those they '
          'trust.$(br2)We do not lose staff. We occasionally misplace them.',
          'O Jade mostra a todos a matrícula, o departamento e o empregador; o nome, só ao empregador e a quem ele '
          'confia.$(br2)Não perdemos funcionários. Às vezes os extraviamos.'),
         ('text',
          '$(k:key.sneak) and use a placed head with an empty hand: its file card opens, with its wage, jar, genes '
          'and last eight lines of history.$(br2)The employer, or anyone they trust, may rename it there.',
          '$(k:key.sneak) e use uma cabeça pousada com a mão vazia: a ficha abre, com salário, pote, genes e as oito '
          'últimas linhas do histórico.$(br2)O empregador, ou quem ele confia, pode renomear ali.'),
         ('text',
          'Names take 16 characters; a blank keeps the old one. A staff name is not a name tag: the saw does not mind '
          'it.$(br2)Departments go by species: cows are Livestock, creepers Demolitions. Shells are Temp Agency.',
          'Nomes levam 16 letras; em branco, fica o antigo. Nome de funcionário não é etiqueta: a serra não '
          'liga.$(br2)O departamento vem da espécie: vacas na Pecuária, creepers em Demolições. Robôs, na Agência.'),
         ('text',
          'A head at a Cerebral Array sits on the Board while it is there.$(br2)A wild creature has a file but no '
          'employer. Nobody is told when it goes.',
          'Uma cabeça na Matriz Cerebral é do Conselho enquanto estiver lá.$(br2)Criatura selvagem tem ficha, mas não '
          'empregador. Ninguém é avisado quando ela se vai.'),
         ('spotlight', 'headinajar:condolence_card',
          'When a host that worked for someone dies, its employer gets an $(l)Exit Interview$() in chat, and this '
          'drops where it fell.',
          'Morreu um hospedeiro com empregador? Este recebe a $(l)Entrevista de Desligamento$() no chat, e isto cai '
          'no lugar.'),
     ],
     'note': ('Your own file is also on file.', 'Sua ficha também está arquivada.')},

    {'cat': 'staffing', 'id': 'pay', 'sort': 9, 'icon': 'minecraft:wheat',
     'en': 'Pay and Morale', 'pt': 'Salário e Moral',
     'pages': [
         ('text',
          'A placed head is a $(l)trophy$() until someone pays it by hand: use its wage on it, wheat for a cow, two '
          'rotten flesh for a zombie. That hires it, and the payer is its employer.',
          'Uma cabeça pousada é um $(l)troféu$() até alguém pagá-la na mão: use o salário nela, trigo para a vaca, '
          'duas carnes podres para o zumbi. Isso a contrata; quem paga é o empregador.'),
         ('text',
          "Staff wear a badge in the employer's team colour, company teal without one.$(br2)At each dawn a head on "
          'the payroll takes one wage from a chest, barrel or other container touching it.',
          'Funcionários usam crachá na cor do time do empregador; sem time, verde-azulado da firma.$(br2)A cada '
          'amanhecer, a cabeça na folha tira um salário de um baú, barril ou outro recipiente encostado.'),
         ('text',
          'A head left unloaded for a week takes one wage, not seven. A trophy never touches the chest beside '
          'it.$(br2)Paying by hand twice in a day does nothing: it was paid today.',
          'Cabeça descarregada por uma semana tira um salário, não sete. Troféu nunca mexe no baú ao lado.$(br2)Pagar '
          'na mão duas vezes no dia não adianta: já recebeu hoje.'),
         ('form', ('Form 40-M: Morale', 'Formulário 40-M'),
          'Starts at 50: +10 a wage paid, -15 a dawn unpaid.$(br)$(l)Engaged$(), 75-100: chin up$(br)$(l)Content$(), 40-74$(br)$(l)Disengaged$(), 15-39: sags, looks '
          'away$(br)$(l)Grievance$(): under 15 two dawns running. A label, for now.',
          'Começa em 50; +10 pago, -15 sem paga.$(br)$(l)Engajado$(), 75-100: queixo erguido$(br)$(l)Satisfeito$(), 40-74$(br)$(l)Desmotivado$(), 15-39: '
          'murcha, olha de lado$(br)$(l)Queixa Formal$(): abaixo de 15 em dois amanheceres seguidos. Só um rótulo, '
          'por ora.'),
         ('form', ('Form 40-M (2)', 'Formulário 40-M (2)'),
          "Heads at stations show Content.$(br)An Employee Mount's villager, by its pay: today and yesterday, "
          'Engaged; today, Content; unpaid 1-2 days, Disengaged; 3 or more, Grievance.',
          'Cabeças em estações: Satisfeito.$(br)O aldeão do Suporte de Funcionário, pela paga: hoje e ontem, '
          'Engajado; só hoje, Satisfeito; sem paga 1-2 dias, Desmotivado; 3 ou mais, Queixa Formal.'),
         ('text',
          'A villager head is never paid by hand: an emerald in hand opens its trades, as it always has.$(br2)Slimes, '
          'ghasts, tadpoles and a few others draw no wage, so they stay trophies.',
          'Cabeça de aldeão nunca recebe na mão: esmeralda na mão abre as trocas, como sempre.$(br2)Slimes, ghasts, '
          'girinos e outros não recebem salário: seguem troféus.'),
     ],
     'note': ('A pizza party is not a pay rise.', 'Festa da pizza não é aumento.')},
]

# The longest real arguments, for the action-bar width check: 16-character names, the longest creature and item names
# (en and pt, vanilla's own names)
NAME16 = 'M' * 16
JAR_REINFORCED_NAMES = (['Elder Guardian', 'Magma Cube', 'Sniffer', 'Warden', 'Hoglin', 'Zoglin', 'Slime'],
                        ['Guardião-Mestre', 'Cubo de Magma', 'Farejador', 'Sentinela', 'Hoglin', 'Zoglin', 'Slime'])
JAR_EXECUTIVE_NAMES = (['Magma Cube', 'Ravager', 'Ghast', 'Slime'], ['Cubo de Magma', 'Devastador', 'Ghast', 'Slime'])
WAGE_NAMES = (['Torchflower Seeds', 'Lily of the Valley', 'Amethyst Shard', 'Crimson Fungus', 'Rotten Flesh'],
              ['Sementes de Plantocha', 'Fragmento de Ametista', 'Lírio-do-Vale', 'Fungo Carmesim', 'Carne Podre'])
NO_WAGE_NAMES = (['Tropical Fish', 'Pufferfish', 'Illusioner', 'Magma Cube'],
                 ['Peixe Tropical', 'Baiacu', 'Ilusionista', 'Girino'])
ACTION_BAR_ARGS = {  # key -> a function of the language index giving every argument list to try
    'refusal.headinajar.saw.jar_small.reinforced': lambda i: [[n] for n in JAR_REINFORCED_NAMES[i]],
    'refusal.headinajar.saw.jar_small.executive': lambda i: [[n] for n in JAR_EXECUTIVE_NAMES[i]],
    'refusal.headinajar.saw.pet': lambda i: [[NAME16], [('another employee', 'outro funcionário')[i]]],
    'refusal.headinajar.staff.wrong_wage': lambda i: [[NAME16, w] for w in WAGE_NAMES[i]],
    'refusal.headinajar.staff.no_wage': lambda i: [[n] for n in NO_WAGE_NAMES[i]],
    'refusal.headinajar.staff.not_yours': lambda i: [[NAME16, NAME16]],
    'message.headinajar.staff.hired': lambda i: [[NAME16]],
    'message.headinajar.staff.paid': lambda i: [[NAME16]],
    'screen.headinajar.file.renamed': lambda i: [[NAME16]],
}
ACTION_BAR_PX = 400

# keys an older generator wrote that this release gives new words: jade.headinajar.staff was the Employee Mount's
# "On staff: %s" (JadeCompat), which moves to jade.headinajar.mount_staff
REPLACED = {'jade.headinajar.staff'}


def _wage(w):
    if w is None:
        return 'none'
    item, count = (w, 1) if isinstance(w, str) else w
    if item.startswith('#'):
        return {'tag': item[1:], 'count': count}
    return {'item': item if ':' in item else MC + item, 'count': count}


def _event(name):
    return {'done': {'trigger': 'headinajar:event', 'conditions': {'event': name}}}


def run(EN, PT, write, shaped):
    from gen_p27_clarity import width, fill, entry_pages, written_lines, wrap, PAGE_LINES, BOOK_WIDTH, TITLE_PX, \
        ENTRY_NAME_PX

    taken = [key for key, _e, _p in LINES if key in EN and key not in REPLACED]
    assert not taken, f'already in the language files: {taken}'
    for key, en, pt in LINES:
        EN[key], PT[key] = en, pt
    # the Employee Mount's Jade line had the staff key; it keeps its words under its own key
    EN['jade.headinajar.mount_staff'], PT['jade.headinajar.mount_staff'] = 'On staff: %s', 'Funcionário: %s'
    # too lively: today's line, word for word (the old key stays)
    EN['refusal.headinajar.saw.too_lively'] = EN['message.headinajar.saw_too_healthy']
    PT['refusal.headinajar.saw.too_lively'] = PT['message.headinajar.saw_too_healthy']

    for i, (en, pt) in enumerate(zip(NAMES_EN, NAMES_PT)):
        EN[f'staff.headinajar.name.{i}'], PT[f'staff.headinajar.name.{i}'] = en, pt
    for dep, en_n, en_l, pt_n, pt_l, species in DEPARTMENTS:
        EN[f'department.headinajar.{dep}'], EN[f'department.headinajar.{dep}.line'] = en_n, en_l
        PT[f'department.headinajar.{dep}'], PT[f'department.headinajar.{dep}.line'] = pt_n, pt_l
        if species is not None:
            write(f'data/headinajar/tags/entity_type/department/{dep}.json', {'values': [MC + s for s in species]})

    # every species in the staff table has one department; none has two
    tagged = [s for d in DEPARTMENTS if d[5] for s in d[5]]
    assert sorted(tagged) == sorted(STAFF), set(tagged) ^ set(STAFF)
    assert len(tagged) == len(set(tagged)), 'a species in two departments'

    for species, (wage, en_noise, pt_noise) in STAFF.items():
        noise = f'staff.headinajar.noise.{species}'
        EN[noise], PT[noise] = en_noise, pt_noise
        write(f'data/headinajar/headinajar/staff/{species}.json',
              {'entity': MC + species, 'wage': _wage(wage), 'noise': noise})
    EN['staff.headinajar.noise.default'], PT['staff.headinajar.noise.default'] = NOISE_DEFAULT

    write('data/headinajar/tags/entity_type/saw/head_only.json', {'values': [MC + s for s in HEAD_ONLY]})
    write('data/headinajar/tags/entity_type/saw/reinforced_jar.json', {'values': [MC + s for s in REINFORCED_JAR]})
    write('data/headinajar/tags/entity_type/saw/executive_jar.json', {'values': [MC + s for s in EXECUTIVE_JAR]})
    write('data/headinajar/tags/entity_type/saw/never.json', {'values': []})

    write('assets/headinajar/models/item/condolence_card.json',
          {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'headinajar:item/condolence_card'}})

    for adv, parent, icon, hidden, en_t, en_d, pt_t, pt_d in ADVANCEMENTS:
        write(f'data/headinajar/advancement/{adv}.json', {
            'parent': f'headinajar:{parent}', 'criteria': _event(adv),
            'display': {'icon': {'id': icon}, 'title': {'translate': f'advancements.headinajar.{adv}.title'},
                        'description': {'translate': f'advancements.headinajar.{adv}.description'}, 'frame': 'task',
                        'show_toast': True, 'announce_to_chat': True, 'hidden': hidden}})
        for table, t, d in ((EN, en_t, en_d), (PT, pt_t, pt_d)):
            table[f'advancements.headinajar.{adv}.title'], table[f'advancements.headinajar.{adv}.description'] = t, d

    # the action bar: every line of this release under 400 px with its longest real arguments
    for key in [k for k, _e, _p in LINES] + ['refusal.headinajar.saw.too_lively']:
        if not key.startswith(('refusal.headinajar.', 'message.headinajar.')) and key not in ACTION_BAR_ARGS:
            continue
        for i, table in enumerate((EN, PT)):
            for args in ACTION_BAR_ARGS.get(key, lambda _i: [[]])(i):
                line = fill(table[key], args)
                assert width(line) < ACTION_BAR_PX, f'{key} ({("en", "pt")[i]}) is {width(line)} px: {line}'

    # the handbook: the two new entries go into the back half, where gen_p27_clarity reads them; every page fits
    import gen_p27_handbook_b as hb
    have = {(e['cat'], e['id']) for e in hb.ENTRIES}
    for e in ENTRIES:
        if (e['cat'], e['id']) not in have:
            hb.ENTRIES.append(e)
        for lang, table in (('en', EN), ('pt', PT)):
            assert width(e[lang]) <= ENTRY_NAME_PX, f'entry name {e[lang]!r} is {width(e[lang])} px'
            for n, (kind, title, text) in enumerate(entry_pages(e, lang, table)):
                limit = PAGE_LINES['first' if n == 0 and kind == 'text' else kind]
                if title:
                    assert width(title) <= TITLE_PX, f'title {title!r} is {width(title)} px'
                    limit -= len(wrap(title, BOOK_WIDTH)) - 1
                lines = written_lines(text)
                assert len(lines) <= limit, f'{e["id"]} page {n + 1} ({lang}): {len(lines)} lines, room for {limit}'

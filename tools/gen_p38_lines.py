"""Lines for the 0.6.1 release (Heads at work): what every jarred head says when its bell is rung, its complaints when
Disengaged, its goodbye, its alarm at a Turret Pedestal and its desk job's lines, in English and Brazilian Portuguese.

Data only. gen_p38_heads.py imports it, makes the language keys and checks every composed action-bar line's width.

Shapes:
- a "say" line is (en_noise, en_hr, pt_noise, pt_hr): the species' own noise and HR's translation of it. On the action
  bar it reads SAY[lang] % (noise, hr); over the jar, the noise then the translation.
- a "plain" line is (en, pt).
Species are vanilla entity paths (namespace minecraft), the rows of gen_p37_foundations.STAFF.
"""

# the action-bar line of a say line: noise, then HR's translation
SAY = ('"%s" (HR translates: "%s")', '"%s" (O RH traduz: "%s")')

# ---------------------------------------------------------------------------------------------------------------
# Titles: tooltip, file card, Jade. The villager's title is its profession (vanilla's name for it); see VILLAGER_TITLES.
TITLES = {
    # Livestock
    'cow': ('Canteen Supervisor', 'Supervisora da Cantina'),
    'mooshroom': ('Canteen, Hot Food', 'Cantina, Pratos Quentes'),
    'pig': ('Waste Management', 'Gestão de Resíduos'),
    'sheep': ('Textiles', 'Têxtil'),
    'chicken': ('Timekeeper', 'Controle de Ponto'),
    'goat': ('Recruitment', 'Recrutamento'),
    'rabbit': ('Lucky Foot, Remote', 'Pé de Coelho, Remoto'),
    # Field Office
    'fox': ('Loss Prevention', 'Prevenção de Perdas'),
    'panda': ('Team Building', 'Dinâmicas de Grupo'),
    'bee': ('Productivity', 'Produtividade'),
    'parrot': ('Reception', 'Recepção'),
    'armadillo': ('Health and Safety', 'Segurança do Trabalho'),
    'sniffer': ('Talent Scout', 'Caça-Talentos'),
    'frog': ('Forecasting', 'Previsões'),
    'tadpole': ('Intern, Pond', 'Estagiário, Lagoa'),
    # Logistics
    'horse': ('Transport Pool', 'Frota'),
    'donkey': ('Transport Pool', 'Frota'),
    'mule': ('Transport Pool', 'Frota'),
    'llama': ('Customer Complaints', 'Reclamações (SAC)'),
    'trader_llama': ("Supplier's Assistant", 'Assistente do Fornecedor'),
    'camel': ('Desert Branch, Water', 'Filial do Deserto, Água'),
    'enderman': ('Procurement', 'Compras'),
    'endermite': ('Temp', 'Temporário'),
    'shulker': ('Records', 'Arquivo'),
    'allay': ('Item Recovery', 'Achados e Perdidos'),
    # Security
    'iron_golem': ('Security Manager', 'Gerente de Segurança'),
    'wolf': ('Security (Canine)', 'Segurança (Canina)'),
    'cat': ("The Chairman's Cat", 'O Gato do Presidente'),
    'ocelot': ('Not Your Cat', 'Não É Seu Gato'),
    'polar_bear': ('Cold Storage', 'Câmara Fria'),
    # Night Shift
    'zombie': ('Clerk', 'Escriturário'),
    'husk': ('Clerk, Desert Branch', 'Escriturário, Filial do Deserto'),
    'drowned': ('Clerk, Wet', 'Escriturário, Molhado'),
    'zombie_villager': ('Probationary Rehire', 'Recontratado em Experiência'),
    'skeleton': ('Night Security', 'Vigia Noturno'),
    'stray': ('Night Security, Cold', 'Vigia Noturno, Frio'),
    'bogged': ('Night Security, Swamp', 'Vigia Noturno, Pântano'),
    'skeleton_horse': ('Night Courier', 'Entregas Noturnas'),
    'phantom': ('Sleep Auditor', 'Auditor do Sono'),
    'zombified_piglin': ('Pensioner', 'Aposentado'),
    'zoglin': ("Nobody's", 'De Ninguém'),
    'zombie_horse': ('Transport Pool, Late', 'Frota, Saudoso'),
    'giant': ('Clerk, Oversized', 'Escriturário, Tamanho GG'),
    # Facilities
    'blaze': ('Facilities: Heating', 'Instalações: Aquecimento'),
    'magma_cube': ('Facilities: Furnace Room', 'Instalações: Casa das Caldeiras'),
    'slime': ('Compliance (Gelatinous)', 'Compliance (Gelatinoso)'),
    'ghast': ('Grief Counsellor', 'Apoio ao Luto'),
    'strider': ('Commuter', 'Mora Longe'),
    'breeze': ('Air Conditioning', 'Ar-Condicionado'),
    'snow_golem': ('Cold Room', 'Sala Gelada'),
    'spider': ('Network Administrator', 'Administrador de Rede'),
    'cave_spider': ('Network Administrator (Junior)', 'Administrador de Rede (Júnior)'),
    'silverfish': ('Pest Control', 'Controle de Pragas'),
    'bat': ('Overnight Monitoring', 'Monitoramento Noturno'),
    # Accounts
    'piglin': ('Treasury', 'Tesouraria'),
    'piglin_brute': ('Bouncer', 'Leão de Chácara'),
    'hoglin': ('Pork, Senior', 'Suíno, Sênior'),
    'wither_skeleton': ('Collections', 'Cobranças'),
    # Legal
    'evoker': ('General Counsel', 'Consultor Jurídico'),
    'vindicator': ('Redundancy Officer', 'Desligamentos'),
    'pillager': ('Acquisitions', 'Aquisições'),
    'illusioner': ('[REDACTED]', '[SIGILOSO]'),
    'vex': ('Internal Audit', 'Auditoria Interna'),
    'ravager': ('Mergers and Acquisitions', 'Fusões e Aquisições'),
    'witch': ('Occupational Health', 'Medicina do Trabalho'),
    # Sales
    'wandering_trader': ('Supplier', 'Fornecedor'),
    # Marine Division
    'squid': ('Printing', 'Gráfica'),
    'glow_squid': ('Lighting', 'Iluminação'),
    'cod': ('Aquarium', 'Aquário'),
    'salmon': ('Aquarium', 'Aquário'),
    'tropical_fish': ('Aquarium', 'Aquário'),
    'pufferfish': ('Stress Ball', 'Bolinha Antiestresse'),
    'dolphin': ('Marine Treasury', 'Tesouraria Marítima'),
    'turtle': ('Long-Term Planning', 'Planejamento de Longo Prazo'),
    'axolotl': ('Wellness', 'Bem-Estar'),
    'guardian': ('Quality Assurance', 'Garantia de Qualidade'),
    # Demolitions
    'creeper': ('Demolition (Probationary)', 'Demolição (Em Experiência)'),
    # Senior Management
    'elder_guardian': ('Non-Executive Director', 'Diretor Não Executivo'),
    'warden': ('Compliance: Noise', 'Compliance: Ruído'),
    'wither': ('Executive Committee', 'Comitê Executivo'),
}
VILLAGER_TITLES = {
    'none': ('Applicant', 'Candidato'),  # an unemployed villager
    'nitwit': ('HR could not agree on one.', 'O RH não chegou a um consenso.'),
}

# ---------------------------------------------------------------------------------------------------------------
# The bell: two to four say lines a species, the first the Employee Handbook's own. Never the same one twice in a row.
BELLS = {
    # Livestock
    'cow': [
        ('Moo.', 'Is there a canteen?', 'Muu.', 'Tem cantina aqui?'),
        ('Moo.', 'The broth is acceptable. Write that down.', 'Muu.', 'O caldo é aceitável. Anote isso.'),
        ('Mooo.', 'I have concerns about the menu.', 'Muuu.', 'Tenho ressalvas quanto ao cardápio.'),
        ('Moo.', 'I used to be on the menu.', 'Muu.', 'Eu já fui prato do dia.'),
    ],
    'mooshroom': [
        ('Moo.', "Soup's on.", 'Muu.', 'A sopa está servida.'),
        ('Moo.', 'Bring your own bowl. It is policy.', 'Muu.', 'Traga sua tigela. É norma.'),
        ('Moo.', 'The mushrooms stay. They have tenure.', 'Muu.', 'Os cogumelos ficam. Eles são concursados.'),
    ],
    'pig': [
        ('Oink.', 'Are you going to finish that?', 'Oinc.', 'Vai comer isso aí?'),
        ('Oink.', 'Nothing is wasted here. Nothing.', 'Oinc.', 'Aqui nada se perde. Tudo se come.'),
        ('Oink.', 'I have read your bins.', 'Oinc.', 'Eu li o seu lixo.'),
    ],
    'sheep': [
        ('Baa.', 'I am mostly wool.', 'Mééé.', 'Sou quase só lã.'),
        ('Baa.', 'I was shorn for this position.', 'Mééé.', 'Fui tosquiada para o cargo.'),
        ('Baa.', 'I am not a cardigan. Yet.', 'Mééé.', 'Ainda não sou um suéter.'),
    ],
    'chicken': [
        ('Bawk.', 'It is twenty past.', 'Cocó.', 'Já são e vinte.'),
        ('Bawk.', 'You are late. I have noted it.', 'Cocó.', 'Você bateu o ponto atrasado.'),
        ('Bawk.', 'Lunch is at noon. It is not noon.', 'Cocó.', 'Almoço é ao meio-dia. Não é meio-dia.'),
        ('Bawk.', 'Every hour, on the hour. Like a professional.', 'Cocó.', 'De hora em hora. Como uma profissional.'),
    ],
    'goat': [
        ('Meh.', 'I will find the right candidate.', 'Béé.', 'Vou achar o candidato certo.'),
        ('Meh.', 'We will be in touch. We will not.', 'Béé.', 'Entraremos em contato. Não entraremos.'),
        ('MEH.', 'Headhunting is a figure of speech. Usually.', 'BÉÉ.', 'Caça-talentos. Ou caça-cabeças, se preferir.'),
    ],
    'rabbit': [
        ('...', 'Where is the rest of me?', '...', 'Cadê o resto de mim?'),
        ('...', 'My foot is out on a contract.', '...', 'Meu pé foi terceirizado.'),
        ('...', 'I am told it brings luck. Not to me.', '...', 'Dizem que dá sorte. Para mim, não deu.'),
    ],
    # Field Office
    'fox': [
        ('Yip.', "That isn't yours, is it.", 'Iip.', 'Isso não é seu, é?'),
        ('Yip.', 'Empty your pockets.', 'Iip.', 'Esvazie os bolsos.'),
        ('Yip.', 'I found it. That makes it mine.', 'Iip.', 'Achado não é roubado.'),
    ],
    'panda': [
        ('Mmm.', 'Is that bamboo?', 'Hmm.', 'Isso é bambu?'),
        ('Mmm.', 'Trust fall. You first.', 'Hmm.', 'Dinâmica da confiança. Você primeiro.'),
        ('Mmm.', 'I have launched an initiative. Now I am lying down.', 'Hmm.',
         'Lancei uma iniciativa. Agora vou deitar.'),
    ],
    'bee': [
        ('Bzz.', 'Have you seen my targets?', 'Bzz.', 'Viu minhas metas?'),
        ('Bzz.', 'Targets met are targets doubled.', 'Bzz.', 'Meta batida é meta dobrada.'),
        ('Bzz.', 'I report to the Queen. Directly.', 'Bzz.', 'Respondo à Rainha. Diretamente.'),
    ],
    'parrot': [
        ('Squawk.', 'Your call is important to us.', 'Currupaco.', 'Sua ligação é muito importante para nós.'),
        ('Squawk.', 'Please hold.', 'Currupaco.', 'Aguarde na linha.'),
        ('Squawk.', 'I repeat: your call is important to us.', 'Currupaco.',
         'O louro não quer biscoito. Quer aumento.'),
    ],
    'armadillo': [
        ('...', 'Have you done the risk assessment?', '...', 'Fez a análise de risco?'),
        ('...', 'Hard hats on. I brought my own.', '...', 'Capacete obrigatório. Eu trouxe o meu.'),
        ('...', 'I have rolled up the incident report.', '...', 'Cadê o seu EPI?'),
    ],
    'sniffer': [
        ('Hfff.', 'I can smell potential.', 'Fffff.', 'Sinto cheiro de potencial.'),
        ('Hfff.', 'Older than the company. Still on probation.', 'Fffff.',
         'Mais velho que a empresa. Ainda em experiência.'),
        ('Hfff.', 'Something is buried here. Probably a predecessor.', 'Fffff.',
         'Tem algo enterrado aqui. Talvez o antecessor.'),
    ],
    'frog': [
        ('Croak.', 'Bring a coat.', 'Croac.', 'Leve um casaco.'),
        ('Croak.', 'The outlook is wet. As always.', 'Croac.', 'Perspectiva: úmida. Como sempre.'),
        ('Croak.', 'The third quarter will be damp.', 'Croac.', 'Terceiro trimestre: chuvoso.'),
    ],
    'tadpole': [
        ('...', 'I will be a frog. Probably.', '...', 'Vou ser sapo. Provavelmente.'),
        ('...', 'Is this a paid internship?', '...', 'Esse estágio é remunerado?'),
        ('...', 'I was told there would be growth.', '...', 'Me prometeram plano de carreira.'),
    ],
    # Logistics
    'horse': [
        ('Hrrrr.', 'When do we go out?', 'Hiiin.', 'Quando a gente sai?'),
        ('Hrrrr.', 'I used to cover a lot of ground.', 'Hiiin.', 'Eu já rodei muito chão.'),
        ('Hrrrr.', 'Saddle not included. Nor is the rest of me.', 'Hiiin.',
         'Sela não inclusa. O resto de mim também não.'),
    ],
    'donkey': [
        ('Hrrrr.', 'When do we go out?', 'Hiiin.', 'Quando a gente sai?'),
        ('Hrrrr.', 'I carry the chests. Nobody carries me.', 'Hiiin.', 'Eu carrego os baús. Ninguém me carrega.'),
        ('Hrrrr.', 'Stubborn, says my file. Underlined.', 'Hiiin.', 'Burro de carga, mas com crachá.'),
    ],
    'mule': [
        ('Hrrrr.', 'When do we go out?', 'Hiiin.', 'Quando a gente sai?'),
        ('Hrrrr.', 'Half horse, half donkey, all staff.', 'Hiiin.', 'Meio cavalo, meio burro, funcionário inteiro.'),
        ('Hrrrr.', 'No family to go home to. The ideal employee.', 'Hiiin.',
         'Sem família para visitar. O funcionário ideal.'),
    ],
    'llama': [
        ('Mrrp.', 'I want to speak to your manager.', 'Mrrp.', 'Quero falar com o seu gerente.'),
        ('Mrrp.', 'I spit on this policy.', 'Mrrp.', 'Eu cuspo nessa política.'),
        ('Mrrp.', 'Your complaint has been received. And filed.', 'Mrrp.',
         'Sua reclamação foi registrada. E arquivada.'),
    ],
    'trader_llama': [
        ('Mrrp.', 'Have you seen my employer?', 'Mrrp.', 'Viu o meu patrão?'),
        ('Mrrp.', 'He said he would be back in ten minutes.', 'Mrrp.', 'Ele disse que voltava em dez minutos.'),
        ('Mrrp.', 'I came with the stall.', 'Mrrp.', 'Eu vim junto com a barraca.'),
    ],
    'camel': [
        ('Hmmph.', 'You use too much water.', 'Hmpf.', 'Você gasta água demais.'),
        ('Hmmph.', 'I have savings. Two humps of them.', 'Hmpf.', 'Eu tenho reservas. Duas corcovas.'),
        ('Hmmph.', 'Saline is still water. I am counting it.', 'Hmpf.', 'Soro também é água. Estou contando.'),
    ],
    'enderman': [
        ('Vwoorp.', "Don't look at me.", 'Vuorp.', 'Não olhe para mim.'),
        ('Vwoorp.', 'I procured that block. You did not need it.', 'Vuorp.',
         'Peguei aquele bloco. Você não precisava dele.'),
        ('Vwoorp.', 'I can be anywhere. I chose a jar.', 'Vuorp.', 'Posso estar em qualquer lugar. Escolhi um pote.'),
    ],
    'endermite': [
        ('Skrr.', "I've been here two minutes.", 'Skrr.', 'Cheguei faz dois minutos.'),
        ('Skrr.', 'Is there a contract? There is never a contract.', 'Skrr.', 'Tem contrato? Nunca tem contrato.'),
        ('Skrr.', 'My agency did not mention the jar.', 'Skrr.', 'A agência não falou nada do pote.'),
    ],
    'shulker': [
        ('...', "We'll keep that on file.", '...', 'Isso vai para o arquivo.'),
        ('...', 'I am a box. I hold things.', '...', 'Sou uma caixa. Eu guardo coisas.'),
        ('...', 'Do not open me. I am filed under Later.', '...', 'Arquivo morto. Bem morto.'),
    ],
    'allay': [
        ('La-la.', "I'd love to help!", 'Lá-lá.', 'Adoraria ajudar!'),
        ('La-la.', "Is this yours? I'll hold it for you!", 'Lá-lá.', 'Isso é seu? Eu guardo para você!'),
        ('La-la-la.', 'I am so happy here. Is that allowed?', 'Lá-lá-lá.', 'Estou tão feliz aqui. Pode isso?'),
    ],
    # Security
    'iron_golem': [
        ('...', 'I protect.', '...', 'Eu protejo.'),
        ('...', 'Villagers first. You second. Possibly.', '...', 'Aldeões primeiro. Você depois. Talvez.'),
        ('...', 'Please wear your badge where I can see it.', '...', 'Crachá visível, por favor.'),
    ],
    'wolf': [
        ('Woof.', "Who's a good employee.", 'Au.', 'Quem é um bom funcionário?'),
        ('Woof.', 'I will fetch. For a bone.', 'Au.', 'Eu busco. Por um osso.'),
        ('Woof.', 'I sniffed your bag. It is fine.', 'Au.', 'Cheirei sua bolsa. Tudo certo.'),
    ],
    'cat': [
        ('Mrrow.', 'This is my office.', 'Miau.', 'Este escritório é meu.'),
        ('Mrrow.', 'I sat on your paperwork. It is approved.', 'Miau.', 'Sentei na sua papelada. Está aprovada.'),
        ('Mrrow.', 'The Chairman knows where I am.', 'Miau.', 'O Presidente sabe onde eu estou.'),
    ],
    'ocelot': [
        ('...', 'Do not.', '...', 'Nem vem.'),
        ('...', 'Still not your cat.', '...', 'Continua não sendo seu gato.'),
        ('...', 'We have not been introduced.', '...', 'Não fomos apresentados.'),
    ],
    'polar_bear': [
        ('Hrrf.', 'Too warm in here.', 'Hrrf.', 'Calor demais aqui.'),
        ('Hrrf.', 'I keep everything cold. Including the atmosphere.', 'Hrrf.',
         'Mantenho tudo gelado. Inclusive o clima.'),
        ('Hrrf.', 'Who touched the thermostat?', 'Hrrf.', 'Quem mexeu no ar-condicionado?'),
    ],
    # Night Shift
    'zombie': [
        ('Braaains.', 'I would like to discuss my pension.', 'Cérebrooos.', 'Quero falar da minha aposentadoria.'),
        ('Braaains.', 'I have been here since before the company.', 'Cérebrooos.',
         'Trabalho aqui desde antes da empresa.'),
        ('Braaains.', 'Is it lunch? It feels like lunch.', 'Cérebrooos.', 'Já é almoço? Parece almoço.'),
        ('Braaains.', 'Knock knock.', 'Cérebrooos.', 'Toc, toc.'),
    ],
    'husk': [
        ('Hrrrgh.', 'Sand gets everywhere.', 'Hrrrgh.', 'A areia entra em tudo.'),
        ('Hrrrgh.', 'The sun does not bother me. Management does.', 'Hrrrgh.',
         'O sol não me incomoda. A gerência, sim.'),
        ('Hrrrgh.', 'Water cooler? We had a sand cooler.', 'Hrrrgh.', 'Bebedouro? Lá era de areia.'),
    ],
    'drowned': [
        ('Blrrgh.', 'Is it Friday?', 'Blrrgh.', 'Já é sexta?'),
        ('Blrrgh.', 'I came in through the plumbing.', 'Blrrgh.', 'Entrei pelo encanamento.'),
        ('Blrrgh.', "This jar is the driest office I've had.", 'Blrrgh.', 'Este pote é a sala mais seca que já tive.'),
    ],
    'zombie_villager': [
        ('Braaains. Hrm.', 'I used to be in Sales.', 'Cérebrooos. Hrm.', 'Eu era de Vendas.'),
        ('Braaains. Hrm.', 'My file mentions a golden apple.', 'Cérebrooos. Hrm.', 'Minha ficha cita uma maçã dourada.'),
        ('Braaains. Hrm.', 'Is the emerald offer still open?', 'Cérebrooos. Hrm.',
         'A proposta em esmeraldas ainda vale?'),
    ],
    'skeleton': [
        ('...', "I'm going to need to see some ID.", '...', 'Vou precisar ver um documento.'),
        ('...', 'Nobody gets past me. Nobody gets near me.', '...', 'Ninguém passa. Ninguém chega perto.'),
        ('...', 'I have a bone to pick with HR.', '...', 'O RH é osso duro de roer.'),
    ],
    'stray': [
        ('...', 'Close the door.', '...', 'Fecha a porta.'),
        ('...', 'I am not cold. I am stern.', '...', 'Não sou frio. Sou rigoroso.'),
        ('...', 'Slow down. That is an instruction.', '...', 'Devagar. Isso é uma ordem.'),
    ],
    'bogged': [
        ('...', 'Mind the mould.', '...', 'Cuidado com o mofo.'),
        ('...', 'I am not unwell. I am seasoned.', '...', 'Não estou doente. Estou curtido.'),
        ('...', 'Report the mushrooms to Facilities. Not me.', '...', 'Os cogumelos são com as Instalações. Não comigo.'),
    ],
    'skeleton_horse': [
        ('...', 'Deliveries after dark only.', '...', 'Entregas só depois que escurece.'),
        ('...', 'I travel light. Very light.', '...', 'Viajo leve. Muito leve.'),
        ('...', 'I came with a storm. I stayed for the dental.', '...',
         'Vim com a tempestade. Fiquei pelo plano odontológico.'),
    ],
    'phantom': [
        ('Screee.', "You haven't slept in %s nights. HR is concerned.", 'Iiiih.',
         'Você não dorme há %s noites. O RH está preocupado.'),
        ('Screee.', 'Lights out at eight. I will check.', 'Iiiih.', 'Luzes apagadas às oito. Eu vou conferir.'),
        ('Screee.', 'Insomnia is a performance issue.', 'Iiiih.', 'Insônia é questão de desempenho.'),
    ],
    'zombified_piglin': [
        ('Hrrng.', 'In my day we had gold.', 'Hrrng.', 'No meu tempo tinha ouro.'),
        ('Hrrng.', 'I worked forty years for this nugget.', 'Hrrng.', 'Trabalhei quarenta anos por esta pepita.'),
        ('Hrrng.', 'Hit one of us, hit all of us. Union rules.', 'Hrrng.',
         'Mexeu com um, mexeu com todos. É o sindicato.'),
    ],
    'zoglin': [
        ('GRRRAH.', 'GRRRAH.', 'GRRRAH.', 'GRRRAH.'),
        ('GRRRAH.', 'HR has declined to translate.', 'GRRRAH.', 'O RH preferiu não traduzir.'),
        ('GRRRAH.', 'GRRRAH. Respectfully.', 'GRRRAH.', 'GRRRAH. Respeitosamente.'),
    ],
    'zombie_horse': [
        ('Hrrnnh.', 'When did we go out?', 'Hiiinnh.', 'Quando foi que a gente saiu?'),
        ('Hrrnnh.', 'I am in no hurry. Not any more.', 'Hiiinnh.', 'Pressa? Não mais.'),
    ],
    'giant': [
        ('BRAAAINS.', 'I WOULD LIKE A BIGGER JAR.', 'CÉREBROOOS.', 'EU QUERIA UM POTE MAIOR.'),
        ('BRAAAINS.', 'DO NOT MIND ME. I DO NOT FIT.', 'CÉREBROOOS.', 'NÃO LIGUEM PARA MIM. EU NÃO CAIBO.'),
    ],
    # Facilities
    'blaze': [
        ('Fwoosh.', 'Is it warm enough for you?', 'Fuuush.', 'Está quentinho para você?'),
        ('Fwoosh.', 'Keep me away from the sprinklers.', 'Fuuush.', 'Longe dos sprinklers, por favor.'),
        ('Fwoosh.', 'I run hot. It is in my appraisal.', 'Fuuush.', 'Sou esquentado. Está na minha avaliação.'),
    ],
    'magma_cube': [
        ('Splorch.', 'Mind the floor.', 'Splorch.', 'Cuidado com o chão.'),
        ('Splorch.', 'Wet floor sign? I am the wet floor.', 'Splorch.', 'Piso molhado? Piso derretido.'),
        ('Splorch.', 'I bounce back. It is in my file.', 'Splorch.', 'Sou resiliente. Está no meu currículo.'),
    ],
    'slime': [
        ('Blorp.', "I am entirely head. Do you know what that's like?", 'Blorp.', 'Sou só cabeça. Sabe como é isso?'),
        ('Blorp.', 'I split under pressure. Please do not.', 'Blorp.', 'Sob pressão, eu me divido. Não faça isso.'),
        ('Blorp.', 'Everything is in order. I absorbed the rest.', 'Blorp.', 'Está tudo em ordem. O resto eu absorvi.'),
    ],
    'ghast': [
        ('Ooooooh.', "I'm fine.", 'Uuuuuuh.', 'Estou bem.'),
        ('Ooooooh.', 'Let it out. I always do.', 'Uuuuuuh.', 'Pode chorar. Eu sempre choro.'),
        ('Ooooooh.', 'My door is always open. I have no door.', 'Uuuuuuh.',
         'Minha porta está sempre aberta. Não tenho porta.'),
        ('Ooooooh.', 'It is not you. It is everything.', 'Uuuuuuh.', 'Não é você. É tudo.'),
    ],
    'strider': [
        ('Brrr.', 'Is the heating on?', 'Brrr.', 'O aquecedor está ligado?'),
        ('Brrr.', 'Two hours each way. On lava.', 'Brrr.', 'Duas horas de condução. Sobre lava.'),
        ('Brrr.', 'I am purple with cold. HR says it suits me.', 'Brrr.', 'Estou roxo de frio. O RH disse que combina.'),
    ],
    'breeze': [
        ('Whoosh.', 'Is it stuffy in here?', 'Vuuush.', 'Está abafado aqui?'),
        ('Whoosh.', 'Somebody opened a window. It was me.', 'Vuuush.', 'Alguém abriu a janela. Fui eu.'),
        ('Whoosh.', 'Your projectiles are returned with thanks.', 'Vuuush.', 'Seus projéteis foram devolvidos. Obrigado.'),
    ],
    'snow_golem': [
        ('...', 'Is it warm in here?', '...', 'Está quente aqui?'),
        ('...', 'The pumpkin is mine. Please do not ask.', '...', 'A abóbora é minha. Não pergunte.'),
        ('...', 'I am melting. Slowly. Professionally.', '...', 'Estou derretendo. Devagar. Com profissionalismo.'),
    ],
    'spider': [
        ('Hsss.', 'Have you tried turning it off and on again?', 'Ssss.', 'Já tentou desligar e ligar de novo?'),
        ('Hsss.', 'The web is down. I am spinning a new one.', 'Ssss.', 'A rede caiu. Estou tecendo outra.'),
        ('Hsss.', 'Eight eyes on the logs.', 'Ssss.', 'Oito olhos nos logs.'),
    ],
    'cave_spider': [
        ('Hss.', 'Is it meant to be blinking?', 'Sss.', 'Era para estar piscando?'),
        ('Hss.', 'I have escalated it.', 'Sss.', 'Já escalei para o sênior.'),
        ('Hss.', 'I will need admin rights.', 'Sss.', 'Vou precisar da senha de administrador.'),
    ],
    'silverfish': [
        ('Skrt.', 'I was here first.', 'Skrt.', 'Eu cheguei primeiro.'),
        ('Skrt.', 'I live in the walls. Rent-free.', 'Skrt.', 'Moro nas paredes. Sem pagar aluguel.'),
        ('Skrt.', 'Pest is a strong word.', 'Skrt.', 'Praga é uma palavra forte.'),
    ],
    'bat': [
        ('Eep.', 'Is it night yet?', 'Iic.', 'Já é noite?'),
        ('Eep.', 'I hang around. That is the job.', 'Iic.', 'Passo o turno de cabeça para baixo.'),
        ('Eep.', 'Nothing to report. As usual.', 'Iic.', 'Nada a relatar. Como sempre.'),
    ],
    # Accounts
    'piglin': [
        ('Hrmf.', 'Is that real gold?', 'Hrmf.', 'Isso é ouro de verdade?'),
        ('Hrmf.', 'Gold armour is business casual.', 'Hrmf.', 'Armadura de ouro é traje esporte fino.'),
        ('Hrmf.', 'I keep the books. And the gold. Mostly the gold.', 'Hrmf.', 'Cuido dos livros. E do ouro. Mais do ouro.'),
    ],
    'piglin_brute': [
        ('HRMF.', 'Not on the list.', 'HRMF.', 'Não está na lista.'),
        ('HRMF.', 'Gold will not get you in.', 'HRMF.', 'Ouro não compra a entrada.'),
        ('HRMF.', 'Dress code.', 'HRMF.', 'Traje inadequado.'),
    ],
    'hoglin': [
        ('Hrnk.', 'Where is the crimson?', 'Ronc.', 'Cadê o carmesim?'),
        ('Hrnk.', 'I charge. That is my whole method.', 'Ronc.', 'Eu avanço. Esse é o método.'),
        ('Hrnk.', 'Keep the warped fungus away from my desk.', 'Ronc.', 'Fungo distorcido longe da minha mesa.'),
    ],
    'wither_skeleton': [
        ('Sssss.', 'Your account is overdue.', 'Sssss.', 'Sua conta está em atraso.'),
        ('Sssss.', 'We have sent three letters. This is the fourth.', 'Sssss.', 'Mandamos três cartas. Esta é a quarta.'),
        ('Sssss.', 'Pay now, or wither.', 'Sssss.', 'Pague, ou seu nome vai para o SPC.'),
    ],
    # Legal
    'evoker': [
        ('Hrmm.', 'That clause is binding.', 'Hrmm.', 'Essa cláusula é vinculante.'),
        ('Hrmm.', 'Read the small print. It bites.', 'Hrmm.', 'Leia as letras miúdas. Elas mordem.'),
        ('Hrmm.', 'Objection. Sustained. By me.', 'Hrmm.', 'Protesto. Deferido. Por mim.'),
    ],
    'vindicator': [
        ('Hrrm.', 'Your position has been made redundant.', 'Hrrm.', 'Seu cargo foi extinto.'),
        ('Hrrm.', 'This is not personal. It is an axe.', 'Hrrm.', 'Não é pessoal. É um machado.'),
        ('Hrrm.', 'We are restructuring. Starting with you.', 'Hrrm.', 'Estamos reestruturando. A começar por você.'),
    ],
    'pillager': [
        ('Hrmph.', 'We should acquire your village.', 'Hrmph.', 'Devíamos adquirir a sua vila.'),
        ('Hrmph.', 'A hostile takeover is still a takeover.', 'Hrmph.', 'Aquisição hostil ainda é aquisição.'),
        ('Hrmph.', 'The banner is our logo.', 'Hrmph.', 'O estandarte é o nosso logotipo.'),
    ],
    'illusioner': [
        ('...', 'We have no record of this employee.', '...', 'Não consta registro deste funcionário.'),
        ('...', 'You did not see me. You are not seeing me now.', '...', 'Você não me viu. Não está me vendo agora.'),
        ('...', 'This line has been redacted.', '...', 'Esta fala está sob sigilo.'),
    ],
    'vex': [
        ('Hee.', "I've seen your chests.", 'Hihi.', 'Eu vi os seus baús.'),
        ('Hee.', 'Walls are no obstacle to an audit.', 'Hihi.', 'Parede não impede auditoria.'),
        ('Hee.', 'I am small. My findings are not.', 'Hihi.', 'Eu sou pequena. Meu relatório, não.'),
    ],
    'ravager': [
        ('HRRRRR.', 'Synergy.', 'HRRRRR.', 'Sinergia.'),
        ('HRRRRR.', 'Restructuring.', 'HRRRRR.', 'Reestruturação.'),
        ('HRRRRR.', 'Disruption.', 'HRRRRR.', 'Disrupção.'),
        ('HRRRRR.', "Let's circle back.", 'HRRRRR.', 'Vamos alinhar.'),
    ],
    'witch': [
        ('Hehehe.', "Take two of these and don't call me.", 'Hehehe.', 'Tome duas destas e não me ligue.'),
        ('Hehehe.', 'Fit for work. Mostly.', 'Hehehe.', 'Apto para o trabalho. Quase.'),
        ('Hehehe.', 'Side effects mean it is working.', 'Hehehe.', 'Efeito colateral é sinal de que funciona.'),
    ],
    # Sales (a Nitwit says NITWIT_BELLS instead)
    'villager': [
        ('Hrm.', 'Is that a jar?', 'Hrm.', 'Isso é um pote?'),
        ('Hrm.', 'Prices have gone up. For you.', 'Hrm.', 'Os preços subiram. Para você.'),
        ('Hrm.', 'Emeralds only. Nothing else.', 'Hrm.', 'Só aceito esmeralda. Nem Pix.'),
        ('Hrm.', 'A good deal for both of us. Mostly me.', 'Hrm.', 'Um bom negócio para nós dois. Mais para mim.'),
    ],
    'wandering_trader': [
        ('I have places to be.', 'I have places to be.', 'Tenho mais o que fazer.', 'Tenho mais o que fazer.'),
        ('I have places to be.', 'HR has nothing to add.', 'Tenho mais o que fazer.', 'O RH não tem nada a acrescentar.'),
        ('I have places to be.', 'My llamas will be in touch.', 'Tenho mais o que fazer.',
         'Minhas lhamas entrarão em contato.'),
    ],
    # Marine Division
    'squid': [
        ('...', 'Toner is low.', '...', 'Acabou o toner.'),
        ('...', 'I print in black. Only black.', '...', 'Imprimo em preto. Só em preto.'),
        ('...', 'Who printed four hundred pages?', '...', 'Quem imprimiu quatrocentas páginas?'),
    ],
    'glow_squid': [
        ('...', 'I am aware that I glow.', '...', 'Eu sei que brilho.'),
        ('...', 'The bulb has not gone. I am the bulb.', '...', 'A lâmpada não queimou. A lâmpada sou eu.'),
        ('...', 'Please do not use me as a night light.', '...', 'Não me use de luz de cabeceira.'),
    ],
    'cod': [
        ('...', '...', '...', '...'),
        ('...', 'My memory lasts three seconds. It is enough.', '...', 'Minha memória dura três segundos. Basta.'),
        ('...', 'Is this the sea?', '...', 'Isso é o mar?'),
    ],
    'salmon': [
        ('...', '...', '...', '...'),
        ('...', 'Upstream. Always upstream.', '...', 'Contra a corrente. Sempre.'),
        ('...', 'I was going to spawn. Then this.', '...', 'Eu ia desovar. Aí veio isto.'),
    ],
    'tropical_fish': [
        ('...', '...', '...', '...'),
        ('...', 'I am decorative. It is in my contract.', '...', 'Sou decorativo. Está no contrato.'),
        ('...', 'Do not tap the glass.', '...', 'Não bata no vidro.'),
    ],
    'pufferfish': [
        ('...', 'It is very stressed. So are you, now.', '...', 'Está muito estressado. Agora você também.'),
        ('...', 'Please do not squeeze.', '...', 'Por favor, não aperte.'),
        ('...', 'Breathe in. Not like that.', '...', 'Respire fundo. Assim não.'),
    ],
    'dolphin': [
        ('Eee.', 'I have a lead.', 'Iii.', 'Tenho uma pista.'),
        ('Eee.', 'Follow me. The treasure is this way.', 'Iii.', 'Me siga. O tesouro é por aqui.'),
        ('Eee.', 'I am very intelligent. Ask me why I am in a jar.', 'Iii.',
         'Sou muito inteligente. Pergunte por que estou num pote.'),
    ],
    'turtle': [
        ('...', 'We will revisit this in a hundred years.', '...', 'Voltamos a isso daqui a cem anos.'),
        ('...', 'Slow and steady. Mostly slow.', '...', 'Devagar e sempre. Mais devagar.'),
        ('...', 'Our five-year plan is on schedule. Year one.', '...', 'O plano de cinco anos vai bem. Estamos no ano um.'),
    ],
    'axolotl': [
        ('...', 'Have you tried breathing?', '...', 'Já tentou respirar?'),
        ('...', 'I grow back. You should try it.', '...', 'Eu me regenero. Experimente.'),
        ('...', 'Smile. It is mandatory.', '...', 'Sorria. É obrigatório.'),
    ],
    'guardian': [
        ('...', 'This will be audited.', '...', 'Isso vai ser auditado.'),
        ('...', 'I have my eye on you. I have only the one.', '...', 'Estou de olho em você. Só tenho um.'),
        ('...', 'Defect found. It is you.', '...', 'Defeito encontrado: você.'),
    ],
    # Demolitions
    'creeper': [
        ('Ssss.', "Don't mind me.", 'Ssss.', 'Não liga para mim.'),
        ('Ssss.', 'I give a hundred per cent. Once.', 'Ssss.', 'Dou cem por cento. Uma vez.'),
        ('Ssss.', 'Can I have a hug?', 'Ssss.', 'Me dá um abraço?'),
        ('Ssss.', 'I have a lot of energy. That is the problem.', 'Ssss.', 'Tenho muita energia. Esse é o problema.'),
    ],
    # Senior Management
    'elder_guardian': [
        ('...', 'Work harder.', '...', 'Trabalhe mais.'),
        ('...', 'You look tired. Good.', '...', 'Você parece cansado. Ótimo.'),
        ('...', 'I attend. I do not participate.', '...', 'Eu compareço. Não participo.'),
    ],
    'warden': [
        ('Shh.', 'Shh.', 'Shh.', 'Shh.'),
        ('Shh.', 'This is a quiet floor.', 'Shh.', 'Este andar é silencioso.'),
        ('Shh.', 'I can hear you thinking.', 'Shh.', 'Estou ouvindo você pensar.'),
        ('Shh.', 'Wool socks are recommended.', 'Shh.', 'Recomendamos meias de lã.'),
    ],
    'wither': [
        ('Hrrrkh.', 'We are of three minds about this.', 'Hrrrkh.', 'Temos três opiniões sobre isso.'),
        ('Hrrrkh.', 'All in favour? All three.', 'Hrrrkh.', 'Votação unânime. Os três.'),
    ],
}
NITWIT_BELLS = [
    ('Hrrm.', 'Hrrm.', 'Hrrm.', 'Hrrm.'),
    ('Hrrm.', 'HR is still looking into it.', 'Hrrm.', 'O RH ainda está vendo isso.'),
]
# what fills a bell line's %s (the hr part only)
BELL_ARGS = {
    'phantom': 'nights',  # the ringer's nights since rest: Stats.TIME_SINCE_REST / 24000, a whole number
}

# ---------------------------------------------------------------------------------------------------------------
# Complaints (a Disengaged head, over its jar every two minutes): the species' noise (staff.headinajar.noise.<path>)
# and one of the Employee Mount's ten (staff.headinajar.complaint.<id>), or the species' own words for it below.
COMPLAINT_IDS = ['dental', 'overtime', 'jar', 'union', 'view', 'smell', 'pension', 'broth', 'chair', 'manager']
COMPLAINTS = {
    # Livestock
    'cow': {
        'broth': ('The broth is cold again. I would know.', 'O caldo está frio de novo. Eu entendo de caldo.'),
        'chair': ('I do not even get a stall.', 'Nem uma baia eu ganho.'),
    },
    'mooshroom': {
        'smell': ('Who keeps microwaving my mushrooms?', 'Quem fica esquentando meus cogumelos?'),
        'broth': ('The stew is cold again. It is my stew.', 'O ensopado esfriou de novo. É o meu ensopado.'),
    },
    'pig': {
        'broth': ('The broth is cold again. I will still have it.', 'O caldo esfriou de novo. Eu tomo assim mesmo.'),
        'smell': ('Who keeps frying bacon? Who?', 'Quem fica fritando bacon? Quem?'),
    },
    'sheep': {
        'view': ('I had a field at my last job.', 'No meu último emprego eu tinha um pasto.'),
        'overtime': ('Is this overtime? I am being fleeced.', 'Hora extra sem pagar? Isso é tosquia.'),
    },
    'chicken': {
        'overtime': ('Is this overtime? I have the times.', 'Isso é hora extra? Eu anotei os horários.'),
        'pension': ('What about my pension? I counted the hours.', 'E a minha aposentadoria? Eu contei as horas.'),
    },
    'goat': {
        'manager': ('I would like to speak to the manager. Head first.', 'Quero falar com o gerente. De cabeça.'),
        'union': ('I am talking to the union. Loudly.', 'Vou falar com o sindicato. Aos berros.'),
    },
    'rabbit': {
        'pension': ('What about my pension? What about my foot?', 'E a minha aposentadoria? E o meu pé?'),
        'view': ('I had a burrow at my last job.', 'No meu último emprego eu tinha uma toca.'),
    },
    # Field Office
    'fox': {
        'smell': ('Who keeps microwaving chicken? Is it for me?', 'Quem fica esquentando frango? É para mim?'),
        'dental': ('I was promised dental. I have checked your drawers.',
                   'Me prometeram dentista. Já revirei suas gavetas.'),
    },
    'panda': {
        'chair': ('I do not even get a beanbag.', 'Nem um pufe eu ganho.'),
        'overtime': ('Is this overtime? I was napping.', 'Isso é hora extra? Eu estava cochilando.'),
    },
    'bee': {
        'overtime': ('Is this overtime? It is always overtime.', 'Isso é hora extra? Sempre é hora extra.'),
        'view': ('I had a meadow at my last job.', 'No meu último emprego eu tinha um jardim.'),
    },
    'parrot': {
        'manager': ('Speak to the manager. Speak to the manager.', 'Chama o gerente. Chama o gerente.'),
        'union': ('Union! Union! Union!', 'Sindicato! Sindicato! Sindicato!'),
    },
    'armadillo': {
        'chair': ('I do not even get a chair. Is that compliant?', 'Nem cadeira eu ganho. Isso está na norma?'),
        'overtime': ('Is this overtime? There is a form for that.', 'Isso é hora extra? Tem formulário para isso.'),
    },
    'sniffer': {
        'view': ('I had a whole age at my last job.', 'No meu último emprego eu tinha uma era inteira.'),
        'pension': ('What about my pension? I am very old.', 'E a minha aposentadoria? Eu sou muito velho.'),
    },
    'frog': {
        'view': ('I had a pond at my last job.', 'No meu último emprego eu tinha um brejo.'),
        'broth': ('The broth is cold again. And no flies in it.', 'O caldo está frio de novo. E sem mosca.'),
    },
    'tadpole': {
        'dental': ('I was promised legs.', 'Me prometeram pernas.'),
        'overtime': ('Is this overtime? Interns do not get overtime.', 'Hora extra? Estagiário não ganha hora extra.'),
    },
    # Logistics
    'horse': {
        'chair': ('I do not even get a saddle.', 'Nem uma sela eu ganho.'),
        'view': ('I had a paddock at my last job.', 'No meu último emprego eu tinha um piquete.'),
    },
    'donkey': {
        'overtime': ('Is this overtime? I will carry it anyway.', 'Isso é hora extra? Eu carrego assim mesmo.'),
        'manager': ('I am not moving until I see the manager.', 'Não saio daqui sem falar com o gerente.'),
    },
    'mule': {
        'pension': ('What about my pension? I have no heirs.', 'E a minha aposentadoria? Não tenho herdeiros.'),
        'union': ('I am talking to the union. Both halves of me.', 'Vou falar com o sindicato. As minhas duas metades.'),
    },
    'llama': {
        'manager': ('I would like to speak to YOUR manager.', 'Quero falar com o SEU gerente.'),
        'smell': ('Who keeps microwaving fish? I will spit on it.', 'Quem esquenta peixe? Vou cuspir nele.'),
    },
    'trader_llama': {
        'pension': ('What about my pension? Ask my employer.', 'E a minha aposentadoria? Pergunte ao patrão.'),
        'view': ('I had the whole road at my last job.', 'No meu último emprego eu tinha a estrada inteira.'),
    },
    'camel': {
        'broth': ('The broth is cold again. And half of it is wasted.', 'O caldo esfriou de novo. E que desperdício.'),
        'view': ('I had a desert at my last job. A lovely desert.', 'No meu último emprego eu tinha um deserto inteiro.'),
    },
    'enderman': {
        'view': ('I had the End at my last job.', 'No meu último emprego eu tinha o End.'),
        'jar': ('The jar is not an office. I could leave. I will not.', 'O pote não é escritório. Eu podia sair. Não saio.'),
    },
    'endermite': {
        'pension': ('My pension? I am on a two-minute contract.', 'Aposentadoria? Meu contrato é de dois minutos.'),
        'union': ('I am talking to the union. They do not take temps.', 'Vou ao sindicato. Eles não aceitam temporário.'),
    },
    'shulker': {
        'jar': ('The jar is not an office. The box was.', 'O pote não é escritório. A caixa era.'),
        'view': ('I had a whole city at my last job.', 'No meu último emprego eu tinha uma cidade.'),
    },
    'allay': {
        'chair': ('I do not even get a chair! That is fine!', 'Nem cadeira eu ganho! Tudo bem!'),
        'union': ('I am talking to the union! They seem nice!', 'Vou falar com o sindicato! Parecem legais!'),
    },
    # Security
    'iron_golem': {
        'dental': ('I was promised rust-proofing.', 'Me prometeram antiferrugem.'),
        'view': ('I had a village at my last job.', 'No meu último emprego eu tinha uma vila.'),
    },
    'wolf': {
        'chair': ('I do not even get a basket.', 'Nem uma caminha eu ganho.'),
        'broth': ('The broth is cold again. Is there a bone in it?', 'O caldo esfriou de novo. Tem osso?'),
    },
    'cat': {
        'chair': ('I do not even get your chair.', 'Nem a sua cadeira eu ganho.'),
        'smell': ('Who keeps microwaving fish? Why is it not for me?', 'Quem esquenta peixe? Por que não é para mim?'),
        'manager': ('I want the manager. I am the manager.', 'Quero o gerente. O gerente sou eu.'),
    },
    'ocelot': {
        'manager': ('I would like to speak to nobody.', 'Não quero falar com ninguém.'),
        'chair': ('I do not even get left alone.', 'Nem sossego eu ganho.'),
    },
    'polar_bear': {
        'smell': ('Who keeps microwaving fish? Bring it here cold.', 'Quem esquenta peixe? Traga frio.'),
        'view': ('I had an iceberg at my last job.', 'No meu último emprego eu tinha um iceberg.'),
    },
    # Night Shift
    'zombie': {
        'pension': ('What about my pension? I am owed decades.', 'E a minha aposentadoria? Me devem décadas.'),
        'broth': ('The broth is cold again. So am I.', 'O caldo esfriou de novo. Eu também.'),
    },
    'husk': {
        'view': ('I had a desert at my last job. Too much view.', 'No meu último emprego eu tinha deserto demais.'),
        'broth': ('The broth is cold again. Good.', 'O caldo esfriou de novo. Ainda bem.'),
    },
    'drowned': {
        'view': ('I had an ocean at my last job.', 'No meu último emprego eu tinha um oceano.'),
        'overtime': ('Is this overtime? I lose count underwater.', "Hora extra? Debaixo d'água eu perco a conta."),
    },
    'zombie_villager': {
        'manager': ('I want the manager. I used to be him.', 'Quero o gerente. Eu já fui o gerente.'),
        'dental': ('I was promised a golden apple.', 'Me prometeram uma maçã dourada.'),
    },
    'skeleton': {
        'dental': ('I was promised dental. I still have all my teeth.', 'Me prometeram dentista. Estou com todos os dentes.'),
        'view': ('I had a dungeon at my last job.', 'No meu último emprego eu tinha uma masmorra.'),
    },
    'stray': {
        'broth': ('The broth is cold again. As it should be.', 'O caldo está frio de novo. Como deve ser.'),
        'view': ('I had a tundra at my last job.', 'No meu último emprego eu tinha uma tundra.'),
    },
    'bogged': {
        'smell': ('Who is microwaving mushrooms? Oh. Me.', 'Quem esquenta cogumelos? Ah. Sou eu.'),
        'dental': ('I was promised a dehumidifier.', 'Me prometeram um desumidificador.'),
    },
    'skeleton_horse': {
        'overtime': ('Is this overtime? I only work nights.', 'Isso é hora extra? Eu só trabalho à noite.'),
        'chair': ('I do not even get a rider.', 'Nem um cavaleiro eu ganho.'),
    },
    'phantom': {
        'overtime': ('Is this overtime? Neither of us has slept.', 'Isso é hora extra? Nenhum de nós dormiu.'),
        'chair': ('I do not even get a bed. Do you?', 'Nem cama eu ganho. E você?'),
    },
    'zombified_piglin': {
        'pension': ('What about my pension? I was promised gold.', 'E a minha aposentadoria? Me prometeram ouro.'),
        'union': ('I am talking to the union. All of us are.', 'Vou falar com o sindicato. Todos nós vamos.'),
    },
    'zoglin': {
        'manager': ('GRRRAH. Manager. GRRRAH.', 'GRRRAH. Gerente. GRRRAH.'),
    },
    'zombie_horse': {
        'chair': ('I do not even get a stable.', 'Nem um estábulo eu ganho.'),
    },
    'giant': {
        'jar': ('THE JAR IS NOT AN OFFICE. IT IS BARELY A HAT.', 'O POTE NÃO É ESCRITÓRIO. NEM CHAPÉU É.'),
        'chair': ('I DO NOT EVEN GET A CHAIR. I WOULD BREAK IT.', 'NEM CADEIRA EU GANHO. EU QUEBRARIA.'),
    },
    # Facilities
    'blaze': {
        'broth': ('The broth is cold again. Pass it here.', 'O caldo esfriou de novo. Passa para cá.'),
        'smell': ('Who keeps microwaving fish? I could do it faster.', 'Quem esquenta peixe? Eu faria mais rápido.'),
    },
    'magma_cube': {
        'chair': ('I do not even get a chair. It would melt.', 'Nem cadeira eu ganho. Ia derreter.'),
        'jar': ('The jar is not an office. It is a crucible.', 'O pote não é escritório. É um cadinho.'),
    },
    'slime': {
        'chair': ('I do not even get a chair. I am all head.', 'Nem cadeira eu ganho. Sou só cabeça.'),
        'jar': ('The jar is not an office. It is my whole body.', 'O pote não é escritório. É o meu corpo inteiro.'),
    },
    'ghast': {
        'view': ('I had the whole Nether at my last job.', 'No meu último emprego eu tinha o Nether inteiro.'),
        'manager': ('I want the manager. About my feelings.', 'Quero o gerente. Sobre os meus sentimentos.'),
        'pension': ('What about my pension? What about any of it?', 'E a minha aposentadoria? E todo o resto?'),
    },
    'strider': {
        'broth': ('The broth is cold again. Everything is cold.', 'O caldo esfriou de novo. Tudo aqui é frio.'),
        'overtime': ('Is this overtime? My commute is overtime.', 'Isso é hora extra? A condução já é hora extra.'),
    },
    'breeze': {
        'smell': ('Who keeps microwaving fish? I will clear the air.', 'Quem esquenta peixe? Eu espalho o cheiro.'),
        'view': ('I had ventilation at my last job.', 'No meu último emprego tinha ventilação.'),
    },
    'snow_golem': {
        'broth': ('The broth is warm again. Please stop.', 'O caldo veio quente de novo. Parem com isso.'),
        'dental': ('I was promised a freezer.', 'Me prometeram um freezer.'),
    },
    'spider': {
        'chair': ('I do not even get a ceiling.', 'Nem um teto eu ganho.'),
        'manager': ('I would like to speak to the manager. Not the junior.', 'Quero falar com o gerente. Não com o júnior.'),
    },
    'cave_spider': {
        'manager': ('I would like to speak to the senior.', 'Quero falar com o sênior.'),
        'overtime': ('Is this overtime? Juniors do overtime.', 'Isso é hora extra? Júnior faz hora extra.'),
    },
    'silverfish': {
        'view': ('I had a wall at my last job. I lived in it.', 'No meu último emprego eu morava na parede.'),
        'union': ('I am talking to the union. There are thousands of us.', 'Vou ao sindicato. Somos milhares.'),
    },
    'bat': {
        'view': ('I had a cave at my last job.', 'No meu último emprego eu tinha uma caverna.'),
        'chair': ('I do not even get a ceiling to hang from.', 'Nem um teto para me pendurar eu ganho.'),
    },
    # Accounts
    'piglin': {
        'pension': ('What about my pension? In gold.', 'E a minha aposentadoria? Em ouro.'),
        'dental': ('I was promised gold teeth.', 'Me prometeram dente de ouro.'),
    },
    'piglin_brute': {
        'manager': ('The manager is not on the list either.', 'O gerente também não está na lista.'),
        'union': ('I am the union.', 'O sindicato sou eu.'),
    },
    'hoglin': {
        'smell': ('Who keeps microwaving pork? Who.', 'Quem fica esquentando pernil? Quem?'),
        'chair': ('I do not even get a pen.', 'Nem um chiqueiro eu ganho.'),
    },
    'wither_skeleton': {
        'pension': ('What about my pension? It is overdue.', 'E a minha aposentadoria? Está em atraso.'),
        'manager': ('I want the manager. About his account.', 'Quero o gerente. Sobre a conta dele.'),
    },
    # Legal
    'evoker': {
        'union': ('I am talking to the union. I drafted their contract.', 'Vou ao sindicato. Fui eu que redigi o estatuto.'),
        'manager': ('I would like to speak to the manager. In writing.', 'Quero falar com o gerente. Por escrito.'),
    },
    'vindicator': {
        'union': ('I am talking to the union. About redundancies.', 'Vou falar com o sindicato. Sobre os desligamentos.'),
        'manager': ('I want the manager. He is next on my list.', 'Quero ver o gerente. Ele é o próximo da lista.'),
    },
    'pillager': {
        'view': ('I had an outpost at my last job.', 'No meu último emprego eu tinha um posto avançado.'),
        'pension': ('What about my pension? I will acquire one.', 'E a minha aposentadoria? Vou adquirir uma.'),
    },
    'illusioner': {
        'manager': ('I want the manager. He does not know I exist.', 'Quero o gerente. Ele não sabe que eu existo.'),
        'pension': ('What about my pension? There is no record of it.', 'E a minha aposentadoria? Não consta.'),
    },
    'vex': {
        'chair': ('I do not even get a chair. I go through them.', 'Nem cadeira eu ganho. Eu atravesso.'),
        'manager': ('I would like to speak to the manager. I have findings.', 'Quero falar com o gerente. Tenho apontamentos.'),
    },
    'ravager': {
        'manager': ('Escalation.', 'Escalonamento.'),
        'overtime': ('No bandwidth.', 'Estou sem braço.'),
    },
    'witch': {
        'dental': ('I was promised dental. I brewed my own.', 'Me prometeram dentista. Fiz a minha poção.'),
        'smell': ('Who is microwaving fish? It is a potion. Leave it.', 'Quem esquenta peixe? É uma poção. Deixe aí.'),
    },
    # Sales
    'villager': {
        'pension': ('What about my pension? In emeralds.', 'E a minha aposentadoria? Em esmeraldas.'),
        'manager': ('I would like to speak to the Librarian.', 'Quero falar com o Bibliotecário.'),
    },
    'wandering_trader': {  # his noise is a whole sentence: short words for the rest
        'view': ('I had the world at my last job.', 'Já tive o mundo inteiro.'),
        'jar': ('I do not do offices.', 'Não paro em escritório.'),
        'smell': ('Who is microwaving fish?', 'Quem esquenta peixe?'),
        'overtime': ('Is this overtime?', 'Isso é hora extra?'),
    },
    # Marine Division
    'squid': {
        'smell': ('Who keeps microwaving calamari?', 'Quem fica esquentando lula?'),
        'overtime': ('Is this overtime? I am out of toner.', 'Isso é hora extra? Acabou o toner.'),
    },
    'glow_squid': {
        'chair': ('I do not even get a lamp. I am the lamp.', 'Nem luminária eu ganho. Eu sou a luminária.'),
        'view': ('I had a cave lake at my last job.', 'No meu último emprego eu tinha um lago na caverna.'),
    },
    'cod': {
        'smell': ('Who keeps microwaving fish? Why are you looking at me?', 'Quem esquenta peixe? Por que olha para mim?'),
        'view': ('I had a sea at my last job.', 'No meu último emprego eu tinha um mar.'),
    },
    'salmon': {
        'smell': ('Who keeps microwaving salmon? I have questions.', 'Quem esquenta salmão? Tenho perguntas.'),
        'overtime': ('Is this overtime? It feels like swimming upstream.', 'Isso é hora extra? Parece nadar contra a corrente.'),
    },
    'tropical_fish': {
        'view': ('I had a reef at my last job.', 'No meu último emprego eu tinha um recife.'),
        'smell': ('Who keeps microwaving fish? I am ornamental.', 'Quem esquenta peixe? Eu sou de enfeite.'),
    },
    'pufferfish': {
        'overtime': ('Is this overtime? I am inflating.', 'Isso é hora extra? Estou inchando.'),
        'manager': ('I would like to speak to the manager. Up close.', 'Quero falar com o gerente. Bem de perto.'),
    },
    'dolphin': {
        'view': ('I had a shipwreck at my last job.', 'No meu último emprego eu tinha um naufrágio.'),
        'smell': ('Who keeps microwaving fish? Not without me.', 'Quem esquenta peixe? Sem mim, não.'),
    },
    'turtle': {
        'pension': ('What about my pension? I can wait a hundred years.', 'E a minha aposentadoria? Espero cem anos.'),
        'overtime': ('Is this overtime? Hard to tell at my speed.', 'Isso é hora extra? No meu ritmo, é difícil saber.'),
    },
    'axolotl': {
        'chair': ('I do not even get a bucket.', 'Nem um balde eu ganho.'),
        'view': ('I had a cave full of plants at my last job.', 'No meu último emprego eu tinha uma caverna com plantas.'),
    },
    'guardian': {
        'view': ('I had a monument at my last job.', 'No meu último emprego eu tinha um monumento.'),
        'overtime': ('Is this overtime? It will be audited.', 'Isso é hora extra? Vai ser auditado.'),
    },
    # Demolitions
    'creeper': {
        'chair': ('I do not even get a chair. Probably for the best.', 'Nem cadeira eu ganho. Melhor assim.'),
        'union': ('I am talking to the union. They keep their distance.', 'Vou ao sindicato. Eles mantêm distância.'),
        'manager': ('I would like to speak to the manager. Closer.', 'Quero falar com o gerente. Mais de perto.'),
    },
    # Senior Management
    'elder_guardian': {
        'overtime': ('Is this overtime? For you, yes.', 'Isso é hora extra? Para você, sim.'),
        'chair': ('I do not even get a chair. I am above chairs.', 'Nem cadeira eu ganho. Estou acima de cadeiras.'),
    },
    'warden': {
        'smell': ('Who keeps microwaving fish? It beeped. Loudly.', 'Quem esquenta peixe? O micro-ondas apitou. Alto.'),
        'manager': ('I would like to speak to the manager. Quietly.', 'Quero falar com o gerente. Baixinho.'),
        'union': ('I am talking to the union. In a whisper.', 'Vou ao sindicato. Sussurrando.'),
    },
    'wither': {
        'manager': ('We want the manager. All three of us.', 'Queremos o gerente. Os três.'),
    },
}

# ---------------------------------------------------------------------------------------------------------------
# Goodbyes: once, over the spot, when a staff head is taken off its plinth or out of a station
GOODBYE = ('Clearing my desk.', 'Esvaziando minha mesa.')
GOODBYES = {  # the zombie family says it in its own words
    'zombie': ('Braaains.', "I'll see myself out.", 'Cérebrooos.', 'Pode deixar, eu saio sozinho.'),
    'husk': ('Hrrrgh.', 'I will take my sand with me.', 'Hrrrgh.', 'Vou levar a minha areia.'),
    'drowned': ('Blrrgh.', 'I will leave by the drain.', 'Blrrgh.', 'Eu saio pelo ralo.'),
    'zombie_villager': ('Braaains. Hrm.', 'Rehired. Refired.', 'Cérebrooos. Hrm.', 'Recontratado. Redemitido.'),
    'zombified_piglin': ('Hrrng.', 'Retired. Again.', 'Hrrng.', 'Aposentado. De novo.'),
    'zoglin': ('GRRRAH.', 'GRRRAH.', 'GRRRAH.', 'GRRRAH.'),
    'zombie_horse': ('Hrrnnh.', "I'll see myself out. At a walk.", 'Hiiinnh.', 'Eu saio sozinho. A passo.'),
    'giant': ('BRAAAINS.', 'I WILL SEE MYSELF OUT. MIND THE DOOR.', 'CÉREBROOOS.', 'EU SAIO SOZINHO. CUIDADO COM A PORTA.'),
}

# ---------------------------------------------------------------------------------------------------------------
# Lookouts: what a head at a Turret Pedestal that has no attack says when it raises the alarm. The thirteen attackers
# (skeleton, stray, bogged, blaze, guardian, elder guardian, creeper, zombie, husk, drowned, snow golem, witch, evoker)
# have no row: they shoot.
ALARMS = {
    'cow': ('Moo!', 'Intruder at the canteen.', 'Muu!', 'Intruso na cantina.'),
    'mooshroom': ('Moo!', 'Someone is not here for the soup.', 'Muu!', 'Alguém não veio pela sopa.'),
    'pig': ('Oink!', 'Something is coming. It is not lunch.', 'Oinc!', 'Vem coisa aí. E não é almoço.'),
    'sheep': ('Baa!', 'Wolf. Probably a wolf.', 'Mééé!', 'Lobo. Deve ser lobo.'),
    'chicken': ('Bawk!', 'Intruder. Time logged.', 'Cocó!', 'Intruso. Horário anotado.'),
    'goat': ('MEH!', 'Unscheduled candidate.', 'BÉÉ!', 'Candidato sem agendamento.'),
    'rabbit': ('...!', 'Run. I cannot.', '...!', 'Corram. Eu não posso.'),
    'fox': ('Yip!', 'Thief. And not me.', 'Iip!', 'Ladrão. E não sou eu.'),
    'panda': ('Mmm!', 'Team building is cancelled.', 'Hmm!', 'A dinâmica foi cancelada.'),
    'bee': ('Bzz!', 'Visitor with no badge.', 'Bzz!', 'Visitante sem crachá.'),
    'parrot': ('Squawk!', 'Your visitor is hostile.', 'Currupaco!', 'Sua visita é hostil.'),
    'armadillo': ('...!', 'Hazard. Roll up.', '...!', 'Perigo. Todo mundo enrolado.'),
    'sniffer': ('Hfff!', 'I smell trouble.', 'Fffff!', 'Sinto cheiro de encrenca.'),
    'frog': ('Croak!', 'Outlook: hostile.', 'Croac!', 'Previsão: hostil.'),
    'tadpole': ('...!', 'Help. I am an intern.', '...!', 'Socorro. Sou estagiário.'),
    'horse': ('Hrrrr!', 'Trouble on the road.', 'Hiiin!', 'Problema na estrada.'),
    'donkey': ('Hrrrr!', 'I am not carrying that.', 'Hiiin!', 'Isso eu não carrego.'),
    'mule': ('Hrrrr!', 'Visitor. Unwelcome.', 'Hiiin!', 'Visita. Indesejada.'),
    'llama': ('Mrrp!', 'A complaint has arrived. Armed.', 'Mrrp!', 'Chegou uma reclamação. Armada.'),
    'trader_llama': ('Mrrp!', 'Is that my employer? No.', 'Mrrp!', 'É o meu patrão? Não.'),
    'camel': ('Hmmph!', 'Dust on the horizon.', 'Hmpf!', 'Poeira no horizonte.'),
    'enderman': ('Vwoorp!', 'It looked at me.', 'Vuorp!', 'Ele olhou para mim.'),
    'endermite': ('Skrr!', 'Someone newer than me.', 'Skrr!', 'Chegou alguém mais novo que eu.'),
    'shulker': ('...!', 'Intruder. Filed.', '...!', 'Intruso. Arquivado.'),
    'allay': ('La-la!', "Someone's here! Not a friend!", 'Lá-lá!', 'Chegou alguém! Não é amigo!'),
    'iron_golem': ('...!', 'Threat. Protecting.', '...!', 'Ameaça. Protegendo.'),
    'wolf': ('WOOF!', 'Intruder. Bad intruder.', 'AU!', 'Intruso. Intruso mau.'),
    'cat': ('MRROW!', 'Someone is in my office.', 'MIAU!', 'Tem alguém no meu escritório.'),
    'ocelot': ('...!', 'Do not.', '...!', 'Nem vem.'),
    'polar_bear': ('HRRF!', 'Hostile. Very warm-blooded.', 'HRRF!', 'Hostil. De sangue quente.'),
    'zombie_villager': ('Braaains. Hrm!', 'Customer. Hostile.', 'Cérebrooos. Hrm!', 'Freguês. Hostil.'),
    'skeleton_horse': ('...!', 'Rider approaching. Not mine.', '...!', 'Cavaleiro chegando. Não é o meu.'),
    'phantom': ('Screee!', 'Intruder. Well rested.', 'Iiiih!', 'Intruso. Bem descansado.'),
    'zombified_piglin': ('Hrrng!', 'Trouble. In my day we went in a crowd.', 'Hrrng!',
                         'Encrenca. No meu tempo a gente ia em bando.'),
    'zoglin': ('GRRRAH!', 'GRRRAH!', 'GRRRAH!', 'GRRRAH!'),
    'zombie_horse': ('Hrrnnh!', 'Something approaches. Slowly. Like me.', 'Hiiinnh!', 'Vem vindo algo. Devagar. Como eu.'),
    'giant': ('BRAAAINS!', 'VISITOR.', 'CÉREBROOOS!', 'VISITA.'),
    'magma_cube': ('Splorch!', 'Hostile on the floor. Mind it.', 'Splorch!', 'Hostil no piso. Cuidado.'),
    'slime': ('Blorp!', 'Non-compliance detected.', 'Blorp!', 'Não conformidade detectada.'),
    'ghast': ('OOOOOOH!', 'Someone is here. Oh no. Oh no.', 'UUUUUUH!', 'Chegou alguém. Ai, não. Ai, não.'),
    'strider': ('Brrr!', 'Hostile. Is it warm, at least?', 'Brrr!', 'Hostil. Pelo menos é quente?'),
    'breeze': ('Whoosh!', 'Something has blown in.', 'Vuuush!', 'O vento trouxe visita.'),
    'spider': ('Hsss!', 'Intrusion detected on the network.', 'Ssss!', 'Invasão detectada na rede.'),
    'cave_spider': ('Hss!', 'The light is blinking red.', 'Sss!', 'A luz está piscando vermelho.'),
    'silverfish': ('Skrt!', 'Pest. Not me. Another one.', 'Skrt!', 'Praga. Não eu. Outra.'),
    'bat': ('EEP!', 'Something to report. At last.', 'IIC!', 'Algo a relatar. Finalmente.'),
    'piglin': ('Hrmf!', 'Intruder. No gold on it.', 'Hrmf!', 'Intruso. Sem ouro nenhum.'),
    'piglin_brute': ('HRMF!', 'NOT on the list.', 'HRMF!', 'NÃO está na lista.'),
    'hoglin': ('Hrnk!', 'Charge. Somebody charge it.', 'Ronc!', 'Avança. Alguém avança.'),
    'wither_skeleton': ('Sssss!', 'Overdue account approaching.', 'Sssss!', 'Devedor se aproximando.'),
    'vindicator': ('Hrrm!', 'Redundancy in sight.', 'Hrrm!', 'Desligamento à vista.'),
    'pillager': ('Hrmph!', 'Hostile bid.', 'Hrmph!', 'Oferta hostil.'),
    'illusioner': ('...!', 'You did not hear this from me.', '...!', 'Você não ouviu isso de mim.'),
    'vex': ('Hee!', "I've seen it. It's coming.", 'Hihi!', 'Eu vi. Está vindo.'),
    'ravager': ('HRRRRR!', 'Hostile takeover.', 'HRRRRR!', 'Aquisição hostil.'),
    'villager': ('Hrm!', 'Customer. The wrong kind.', 'Hrm!', 'Freguês. Do tipo errado.'),
    'wandering_trader': ('I have places to be!', 'Now more than ever.', 'Tenho mais o que fazer!', 'Agora mais do que nunca.'),
    'squid': ('...!', 'Hostile. Printing a warning.', '...!', 'Hostil. Imprimindo aviso.'),
    'glow_squid': ('...!', 'Lights on. Someone is there.', '...!', 'Luz acesa. Tem alguém aí.'),
    'cod': ('...!', '...!', '...!', '...!'),
    'salmon': ('...!', 'Upstream. Now.', '...!', 'Contra a corrente. Já.'),
    'tropical_fish': ('...!', 'Someone tapped the glass.', '...!', 'Bateram no vidro.'),
    'pufferfish': ('...!', 'Very stressed now.', '...!', 'Muito estressado agora.'),
    'dolphin': ('Eee!', 'I have a lead. It is hostile.', 'Iii!', 'Tenho uma pista. É hostil.'),
    'turtle': ('...!', 'Threat. I will react in a century.', '...!', 'Ameaça. Reajo daqui a um século.'),
    'axolotl': ('...!', 'Breathe. Then run.', '...!', 'Respire. Depois corra.'),
    'warden': ('SHH!', 'Footsteps.', 'SHH!', 'Passos.'),
    'wither': ('Hrrrkh!', 'All three of us saw that.', 'Hrrrkh!', 'Nós três vimos isso.'),
}
ALARM_DEFAULT = ('...!', 'Someone is here.', '...!', 'Tem alguém aí.')  # a creature from another mod

# ---------------------------------------------------------------------------------------------------------------
# Desk jobs: say lines (the species' noise) and plain lines. Arguments in the comments, in order.
DESK_SAY = {
    # the iron golem's first ring of the day, holding out the poppy
    'iron_golem.poppy': ('...', 'For you. Security has assessed you as harmless.', '...',
                         'Para você. A Segurança avaliou você como inofensivo.'),
    # the warden, more than twenty vibrations in a minute (morale -5)
    'warden.noise': ('Shh.', 'This floor is too loud. HR has been told.', 'Shh.',
                     'Este andar está barulhento demais. O RH foi avisado.'),
    # the warden's shriek at the Grievance label (Darkness to everyone within 16)
    'warden.shriek': ('SHHHHH.', 'Final warning.', 'SHHHHH.', 'Último aviso.'),
    # a goat head rams its glass and a horn drops
    'goat.horn': ('Meh.', 'Please sign for your horn.', 'Béé.', 'Assine aqui o recebimento do chifre.'),
    # a piglin head starts inspecting a gold ingot
    'piglin.inspect': ('Hrmf.', 'Let me look at this.', 'Hrmf.', 'Deixa eu ver isso.'),
    # the cat's morning gift lands on the plinth
    'cat.gift': ('Mrrow.', 'I left you something. Do not thank me.', 'Miau.', 'Deixei uma coisa para você. Não agradeça.'),
}
DESK_PLAIN = {
    # a fed villager rung with an emerald in hand: emeralds asked, the emerald's name, how many it gives, what it gives
    'villager.best_offer': ('Best offer: %2$s x%1$s for %4$s x%3$s.', 'Melhor oferta: %2$s x%1$s por %4$s x%3$s.'),
    # the same, when every offer is out of stock or locked
    'villager.no_offer': ('Nothing is worth an emerald today. Try after the restock.',
                          'Hoje nada vale uma esmeralda. Tente depois da reposição.'),
    # the warden rung: the last five sounds (one NOISE_LOG entry each, or counted groups, joined with ', ')
    'warden.log': ('Logged: %s.', 'Registrado: %s.'),
    'warden.log_empty': ('Nothing logged. It approves.', 'Nada registrado. Ele aprova.'),
    'warden.log_join': (', ', ', '),  # between two entries of the log
    # the frog, a minute before rain or thunder; everyone within 16
    'frog.rain': ('Frog head croaks: rain is due within the minute.', 'Sapo coaxando, chuva chegando: em um minuto.'),
    'frog.thunder': ('Frog head croaks: thunder is due within the minute.',
                     'Sapo coaxando, trovoada chegando: em um minuto.'),
    # shears on a mooshroom head
    'mooshroom.shears': ('It keeps its mushrooms. They are load-bearing.', 'Os cogumelos ficam. São estruturais.'),
    # the ghast's first tear ever (once per head)
    'ghast.first_tear': ('The Grief Counsellor has produced a tear. Nothing personal.',
                         'O Apoio ao Luto produziu uma lágrima. Não é pessoal.'),
    # what each job does, in a word, for Jade's desk status row and the handbook (no arguments)
    'status.villager': ('Reads its best offer for an emerald.', 'Lê a melhor oferta por uma esmeralda.'),
    'status.cow': ('It knows a good broth.', 'Entende de caldo.'),
    'status.sheep': ('Wool once a day. Takes dye.', 'Lã uma vez por dia. Aceita tinta.'),
    'status.mooshroom': ('Stew once a day.', 'Ensopado uma vez por dia.'),
    'status.chicken': ('Keeps the hours.', 'Bate as horas.'),
    'status.cat': ('Creepers keep their distance.', 'Creepers mantêm distância.'),
    'status.blaze': ('It breathes on the furnaces.', 'Dá um bafo nos fornos.'),
    'status.ghast': ('Its sorrow is a renewable resource.', 'A tristeza dele é um recurso renovável.'),
    'status.piglin': ('Barters, one gold ingot at a time.', 'Faz escambo, um lingote de ouro por vez.'),
    'status.allay': ('Recovers %s.', 'Recupera: %s.'),  # the item it holds
    'status.allay_empty': ('Use an item on it to give it something to find.',
                           'Use um item nele para ter o que procurar.'),
    'status.frog': ('Forecasting.', 'Prevendo o tempo.'),
    'status.squid': ('Ink once a day, for a glass bottle.', 'Tinta uma vez por dia, por um frasco.'),
    'status.goat': ('Horns left: %s.', 'Chifres restantes: %s.'),  # 0 to 2
    'status.sniffer': ('It found something. It always does.', 'Achou alguma coisa. Sempre acha.'),
    'status.iron_golem': ('Security presence.', 'Presença da Segurança.'),
    'status.evoker': ('Legal has reviewed your sheep.', 'O Jurídico analisou a sua ovelha.'),
    'status.warden': ('Logging every sound within 16 blocks.', 'Registrando todo som num raio de 16 blocos.'),
    'status.zombie': ('Answers the door.', 'Atende a porta.'),
    'status.unfed': ('Not on lunch. It looks at you.', 'Sem almoço. Só olha para você.'),
}
# The warden's log, by vanilla vibration frequency (1-15): (en one, en many, pt one, pt many); many has %s, the count.
# The log is the last five sounds, so at most five entries and their counts add up to five.
NOISE_LOG = {
    1: ('1 step', '%s steps', '1 passo', '%s passos'),  # steps, swimming, flapping
    2: ('1 landing', '%s landings', '1 queda', '%s quedas'),  # landing, a projectile landing, a splash
    3: ('1 shot', '%s shots', '1 disparo', '%s disparos'),  # shooting, an instrument, an item used up
    4: ('1 fidget', '%s fidgets', '1 remexida', '%s remexidas'),  # gliding, taking off armour
    5: ('1 outfit', '%s outfits', '1 figurino', '%s figurinos'),  # putting on armour, dismounting
    6: ('1 contact', '%s contacts', '1 contato', '%s contatos'),  # touching a creature, mounting, shearing
    7: ('1 incident', '%s incidents', '1 incidente', '%s incidentes'),  # a creature hurt
    8: ('1 snack', '%s snacks', '1 lanche', '%s lanches'),  # eating, drinking
    9: ('1 closing', '%s closings', '1 batida', '%s batidas'),  # a chest, a door, a lever off
    10: ('1 opening', '%s openings', '1 abertura', '%s aberturas'),  # a chest, a door, a lever on, a fuse lit
    11: ('1 edit', '%s edits', '1 ajuste', '%s ajustes'),  # a block changing state
    12: ('1 breakage', '%s breakages', '1 quebra', '%s quebras'),  # a block broken, a fluid taken
    13: ('1 build', '%s builds', '1 obra', '%s obras'),  # a block placed, a fluid poured
    14: ('1 arrival', '%s arrivals', '1 chegada', '%s chegadas'),  # something placed, lightning, a teleport
    15: ('1 departure', '%s departures', '1 saída', '%s saídas'),  # a death, an explosion
}

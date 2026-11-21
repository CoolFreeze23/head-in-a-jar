"""Lines and data for the 0.7.2 release (The moments): grades and the Performance Review, the Promotion to the Board, the
Grievance (Form 66-G) for every kind of staff, and the Strike. Imported by gen_data.py after gen_p42_secondment and
before the handbook generator (gen_p27_clarity), whose text check and handbook read the final lines and entries.

It writes:
- the Performance Review's item model;
- seven advancements, five memos, two placard notices (15 and 17);
- the new sound events and their subtitles;
- every new language line, en_us and pt_br; each action-bar line is checked under 400 px with its longest real
  arguments, and each floating line over a worker under 200 px in two lines, in both languages;
- the handbook entries "Grades and Reviews", "Promotion to the Board" and "Strikes", and the Grievance on the morale
  page.

The sections marked for a part of the work (MORE_REVIEWS, MORE_CEREMONY, MORE_STRIKES, MORE_GRIEVANCES) are where each
part adds the lines it needs beyond the core table.
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..')
RES = os.path.join(ROOT, 'src', 'main', 'resources')

R = 'refusal.headinajar.'
M = 'message.headinajar.'
S = 'staff.headinajar.'
SLIP = 'item.headinajar.performance_review.'

# ---------------------------------------------------------------------------------------------------------------
# The core table: (key, en, pt).
LINES = [
    # grades
    (S + 'grade.0', 'Intern', 'Estagiário'),
    (S + 'grade.1', 'Associate', 'Associado'),
    (S + 'grade.2', 'Senior Associate', 'Associado Sênior'),
    (S + 'grade.3', 'Team Lead', 'Líder de Equipe'),
    (S + 'review_due', 'Review due', 'Avaliação pendente'),
    (S + 'status.on_strike', 'On strike', 'Em greve'),
    (S + 'status.grievance', 'Grievance: facing the wall', 'Queixa: virado para a parede'),
    (S + 'status.watching', 'Watching the ceremony', 'Assistindo à cerimônia'),

    # the Performance Review slip
    ('item.headinajar.performance_review', 'Performance Review', 'Avaliação de Desempenho'),
    (SLIP + 'blank', 'A blank review. HR prints these from Paper at the Time Clock.',
     'Avaliação em branco. O RH imprime a partir de Papel no Relógio de Ponto.'),
    (SLIP + 'header', 'PERFORMANCE REVIEW · Staff No. %s · %s (%s) · %s', 'AVALIAÇÃO DE DESEMPENHO · Matrícula %s · %s (%s) · %s'),
    (SLIP + 'counts', 'Tasks since last review: %s. Days unpaid: %s. Grievances: %s.',
     'Tarefas desde a última avaliação: %s. Dias sem salário: %s. Queixas: %s.'),
    (SLIP + 'lines', 'Strengths: %s. Areas for growth: %s.', 'Pontos fortes: %s. A desenvolver: %s.'),
    (SLIP + 'rating', 'Rating: %s. Grade: %s, from today.', 'Nota: %s. Cargo: %s, a partir de hoje.'),
    (SLIP + 'rating_same', 'Rating: %s. Grade: %s, unchanged.', 'Nota: %s. Cargo: %s, sem alteração.'),
    (SLIP + 'empty', 'Tasks since last review: 0. Rating: Needs Improvement. HR appreciates the effort.',
     'Tarefas desde a última avaliação: 0. Nota: Precisa Melhorar. O RH agradece o esforço.'),
    (SLIP + 'reviewer', 'Reviewer: %s. p.p. Non-Human Resources.', 'Avaliador: %s. p.p. Recursos Não Humanos.'),
    (SLIP + 'day', 'Filed day %s.', 'Arquivada no dia %s.'),
    (S + 'rating.exceeds', 'Exceeds Expectations', 'Acima das Expectativas'),
    (S + 'rating.meets', 'Meets Expectations', 'Dentro das Expectativas'),
    (S + 'rating.needs', 'Needs Improvement', 'Precisa Melhorar'),
    (S + 'rating.plan', 'Placed on a Performance Plan', 'Colocado em Plano de Desempenho'),

    # the Review's refusals and answers
    (R + 'review.today', '%s was reviewed today. Let them settle.', '%s já foi avaliado hoje. Deixe assentar.'),
    (R + 'review.paper', 'No paper for the review. Bring paper.', 'Sem papel para a avaliação. Traga papel.'),
    (R + 'review.untrusted', "Reviews come from %s's manager, or someone they trust.", 'Avaliações vêm do gestor de %s ou de quem ele confia.'),
    (R + 'review.away', '%s is not within reach of the clock. Reviews are done in person.',
     '%s não está perto do relógio. Avaliação é cara a cara.'),
    (M + 'review.raised', 'Review filed: %s is now %s.', 'Avaliação arquivada: %s agora é %s.'),
    (M + 'review.same', 'Review filed: %s stays %s.', 'Avaliação arquivada: %s continua %s.'),
    (R + 'print.grade', 'This card needs a %s (%s filters a stop).', 'Este cartão pede um %s (%s filtros por parada).'),

    # the Promotion to the Board
    (R + 'promotion.below', '%s is graded %s. The Board takes Team Leads.', '%s é %s. O Conselho só aceita Líderes de Equipe.'),
    (R + 'promotion.intern', 'The Board does not take interns. It has tried.', 'O Conselho não aceita estagiários. Já tentou.'),
    (R + 'promotion.others', "Promotions come from %s's manager, %s.", 'Promoções vêm do gestor de %s: %s.'),
    (R + 'promotion.no_jar', 'Promotions need an empty jar in your off hand. The Board takes heads.',
     'Promoção pede um pote vazio na outra mão. O Conselho só quer cabeças.'),
    (R + 'promotion.operation', 'The Board will wait. An operation is in progress nearby.',
     'O Conselho espera. Há uma operação em andamento por perto.'),
    (R + 'promotion.strike', 'Not during industrial action. Settle the strike first.', 'Não durante a greve. Resolva a greve primeiro.'),
    (R + 'promotion.far', 'Bring %s within 8 blocks of the clock for the ceremony.', 'Traga %s a até 8 blocos do relógio para a cerimônia.'),
    (R + 'promotion.disabled', 'Promotions are frozen on this server.', 'Promoções estão congeladas neste servidor.'),
    (R + 'promotion.busy', 'The Board is already in session nearby. One promotion at a time.',
     'O Conselho já está reunido aqui perto. Uma promoção por vez.'),
    (M + 'promotion.candidate', 'Candidate for the Board: %s', 'Candidato ao Conselho: %s'),
    (M + 'promotion.quiet', 'HR has asked for quiet.', 'O RH pediu silêncio.'),
    (M + 'promotion.whole', 'There is no body to keep. The Board takes all of %s.', 'Não sobra corpo. O Conselho leva %s inteiro.'),
    (M + 'promotion.cancelled', 'The Board will reconvene. Nothing was spent.', 'O Conselho vai se reunir de novo. Nada foi gasto.'),
    (M + 'promotion.body', "%s's body remembers its training.", 'O corpo de %s se lembra do treinamento.'),
    ('dolores.headinajar.promotion_offer',
     'DOLORES: %s, your work has been exceptional. The company would like to offer you a seat on the Board.',
     'DOLORES: %s, seu trabalho tem sido excepcional. A empresa gostaria de lhe oferecer uma cadeira no Conselho.'),
    ('dolores.headinajar.promotion_done', 'DOLORES: Congratulations on your promotion.', 'DOLORES: Parabéns pela promoção.'),

    # the file
    (S + 'history.promoted', 'Promoted to the Board, day %s.', 'Promovido ao Conselho, dia %s.'),
    (S + 'history.reviewed', 'Performance Review filed, day %s.', 'Avaliação de Desempenho arquivada, dia %s.'),
    (S + 'history.grievance', 'Filed Form 66-G (Grievance), day %s.', 'Abriu o Formulário 66-G (Queixa), dia %s.'),
    (S + 'history.grievance_withdrawn', 'Grievance withdrawn, day %s.', 'Queixa retirada, dia %s.'),
    (S + 'history.strike', 'Walked out, day %s.', 'Cruzou os braços, dia %s.'),

    # the Grievance
    (S + 'say.grievance_withdrawn', 'Grievance withdrawn. Form 66-G has been shredded.', 'Queixa retirada. O Formulário 66-G foi picotado.'),
    (S + 'say.grievance', 'Form 66-G filed. I will be facing the wall.', 'Formulário 66-G aberto. Vou ficar de cara para a parede.'),
    (S + 'say.review_due', 'Still waiting on that review.', 'Ainda esperando aquela avaliação.'),

    # the Strike
    (R + 'strike.cake', 'A pizza party is not a pay rise.', 'Pizza não é aumento.'),
    (R + 'strike.hire', "New staff won't cross the picket line. Settle the strike first.", 'Ninguém novo fura o piquete. Resolva a greve primeiro.'),
    (R + 'strike.let_go', 'Not during industrial action. Settle the strike first.', 'Não durante a greve. Resolva a greve primeiro.'),
    (R + 'strike.striker', "I'm on strike. Talk to the rep.", 'Estou em greve. Fale com o representante.'),
    (R + 'strike.gift', 'The rep is not taking gifts. The rep is taking demands.', 'O representante não aceita presentes. Aceita reivindicações.'),
    (R + 'strike.short', 'Back pay short: %s still owed. Put it in the Pay Drawer.', 'Faltam atrasados: %s. Coloque na Gaveta de Pagamento.'),
    (R + 'strike.cards', 'The site is on strike. Cards go back in the rack when it is settled.',
     'O posto está em greve. Cartões só depois do acordo.'),
    (M + 'strike.demands', 'Back pay: %s, %s, %s. And cake.', 'Atrasados: %s, %s, %s. E bolo.'),
    (M + 'strike.demands_more', 'Back pay: %s, %s and more (on the roster). And cake.', 'Atrasados: %s, %s e mais (no quadro). E bolo.'),
    (M + 'strike.demands_cake', 'Back pay: settled. Now the cake.', 'Atrasados: pagos. Agora o bolo.'),
    (M + 'strike.agreed', 'Terms agreed. Back pay settled. There will be cake.', 'Acordo fechado. Atrasados pagos. Haverá bolo.'),
    ('chat.headinajar.strike', '[Strike] Staff at the site at %s, %s, %s have walked out.', '[Greve] A equipe do posto em %s, %s, %s cruzou os braços.'),
    ('chat.headinajar.strike_collapsed', 'The strike collapsed. Nobody feels good about it.', 'A greve acabou sozinha. Ninguém se sente bem com isso.'),
    ('array.headinajar.vote_walkout', '[Board Meeting] Motion: %s. Carried %s to %s. Staff are walking out.',
     '[Reunião do Conselho] Moção: %s. Aprovada por %s a %s. A equipe está saindo.'),
    (S + 'rep', 'Union Rep', 'Representante Sindical'),
    (S + 'say.chant.0', 'What do we want? Dental!', 'O que a gente quer? Dentista!'),
    (S + 'say.chant.1', 'Warmer broth now!', 'Caldo quente já!'),
    (S + 'say.chant.2', 'The jar is not an office!', 'Pote não é escritório!'),
    (S + 'say.chant.3', 'Moo. (HR translates: "No pay, no milk.")', 'Muu. (o RH traduz: "Sem salário, sem leite.")'),
    (S + 'say.chant.4', 'Braaains. (HR translates: "Back pay!")', 'Cééérebros. (o RH traduz: "Atrasados!")'),
    (S + 'complaint.picket', 'I support my colleagues on the picket line.', 'Apoio meus colegas no piquete.'),
    (S + 'complaint.dental_now', 'Dental now.', 'Dentista já.'),
    # the roster's strike section
    ('gui.headinajar.time_clock.strike', 'ON STRIKE', 'EM GREVE'),
    ('gui.headinajar.time_clock.demands', 'Demands: back pay, then cake', 'Reivindicações: atrasados, depois bolo'),
    ('gui.headinajar.time_clock.back_pay', '%s: %s dawns unpaid, owed %s', '%s: %s amanheceres sem salário, devendo %s'),
    ('gui.headinajar.time_clock.review', 'Review', 'Avaliar'),
    ('gui.headinajar.time_clock.promote', 'Promote', 'Promover'),
    ('gui.headinajar.time_clock.grade', 'Grade', 'Cargo'),

    # memos (DOLORES's pager)
    ('memo.headinajar.review_due',
     '%s has earned a promotion. It takes effect once someone fills in the review. Someone is you.',
     '%s mereceu uma promoção. Ela só vale quando alguém preencher a avaliação. Esse alguém é você.'),
    ('memo.headinajar.team_lead',
     '%s is now a Team Lead. The team works faster near them. The Board may want them.',
     '%s agora é Líder de Equipe. A equipe rende mais perto dele. O Conselho pode querê-lo.'),
    ('memo.headinajar.promotion',
     'Congratulations on your first promotion. The body remembers you; the head will remember the broth.',
     'Parabéns pela primeira promoção. O corpo se lembra de você; a cabeça vai se lembrar do caldo.'),
    ('memo.headinajar.grievance',
     'A member of staff has filed Form 66-G. Feed them, pay them, or buy a cake. HR does not do this for you.',
     'Um funcionário abriu o Formulário 66-G. Alimente, pague ou compre um bolo. O RH não faz isso por você.'),
    ('memo.headinajar.strike',
     'Your staff at %s, %s, %s have walked out. Their rep has a list. HR suggests reading it.',
     'Sua equipe em %s, %s, %s cruzou os braços. O representante tem uma lista. O RH sugere ler.'),

    # placards (the next free notices, in shipping order)
    ('placard.headinajar.15', 'STRIKES WILL BE MET WITH CAKE', 'GREVES SERÃO RECEBIDAS COM BOLO'),
    ('placard.headinajar.17', 'CAKE RESOLVES MOST GRIEVANCES', 'BOLO RESOLVE A MAIORIA DAS QUEIXAS'),
]

# The review slip's strengths and areas for growth: the book's species, then one pair per department for the rest.
# (id, en strengths, en areas, pt strengths, pt areas)
STRENGTHS = [
    ('cow', 'punctual, warm', 'hands', 'pontual, calorosa', 'mãos'),
    ('enderman', 'reach', 'eye contact', 'alcance', 'olhar nos olhos'),
    ('fox', 'initiative', 'one item at a time', 'iniciativa', 'um item de cada vez'),
    ('cat', 'presence', 'everything else', 'presença', 'todo o resto'),
    ('wolf', 'enthusiasm', 'none. Good boy', 'entusiasmo', 'nenhuma. Bom garoto'),
    ('zombie', 'commitment', 'punctuality before dusk', 'comprometimento', 'pontualidade antes do anoitecer'),
    ('skeleton', 'aim', 'muscle', 'mira', 'músculo'),
    ('piglin', 'attention to gold', 'the pocket', 'atenção ao ouro', 'o bolso'),
    ('allay', 'everything', 'please stop', 'tudo', 'por favor, pare'),
    ('villager', 'experience', "'Hrm.'", 'experiência', "'Hrm.'"),
    ('llama', 'opinions', 'tone', 'opiniões', 'o tom'),
    ('iron_golem', 'protects', 'feelings', 'protege', 'sentimentos'),
    ('spider', 'reach, all of it', 'legs, fewer', 'alcance, todo ele', 'pernas, menos'),
    ('creeper', 'commitment', 'longevity', 'dedicação total', 'longevidade'),
]
DEPARTMENT_STRENGTHS = [
    ('livestock', 'patience', 'opposable thumbs', 'paciência', 'polegar opositor'),
    ('field_office', 'initiative', 'attendance', 'iniciativa', 'assiduidade'),
    ('logistics', 'stamina', 'putting things down', 'fôlego', 'largar as coisas'),
    ('security', 'vigilance', 'friendliness', 'vigilância', 'simpatia'),
    ('night_shift', 'commitment', 'mornings', 'comprometimento', 'manhãs'),
    ('facilities', 'reliability', 'small talk', 'confiabilidade', 'conversa fiada'),
    ('accounts', 'attention to detail', 'sharing', 'atenção aos detalhes', 'dividir'),
    ('legal', 'persuasion', 'plain speech', 'persuasão', 'falar claro'),
    ('sales', 'charisma', 'follow-through', 'carisma', 'cumprir o combinado'),
    ('marine', 'fluidity', 'dry land', 'fluidez', 'terra firme'),
    ('demolitions', 'impact', 'restraint', 'impacto', 'contenção'),
    ('senior_management', 'gravitas', 'delegation', 'presença de diretoria', 'delegar'),
    ('board', 'vision', 'the floor', 'visão', 'o chão de fábrica'),
    ('temp_agency', 'flexibility', 'staying', 'flexibilidade', 'ficar'),
    ('general', 'attendance', 'everything else', 'assiduidade', 'todo o resto'),
]

# Advancements: (id, parent, icon, hidden, en title, en description, pt title, pt description, also)
# `also`: a second event the advancement asks for too (both criteria needed).
ADVANCEMENTS = [
    ('constructive_feedback', 'clocking_in', 'headinajar:performance_review', False, 'Constructive Feedback',
     'Print a Performance Review', 'Feedback Construtivo', 'Imprima uma Avaliação de Desempenho', None),
    ('middle_management', 'constructive_feedback', 'minecraft:paper', False, 'Middle Management',
     'Have a member of staff reach Team Lead', 'Gerência Intermediária', 'Leve um funcionário a Líder de Equipe', None),
    ('promoted', 'middle_management', 'headinajar:life_support_jar', False, 'Promoted to the Board',
     'Promote a Team Lead to the Board, and hold a Board Meeting', 'Promovido ao Conselho',
     'Promova um Líder de Equipe ao Conselho e faça uma Reunião do Conselho', 'board_meeting'),
    ('let_them_eat_cake', 'pay_grade', 'minecraft:cake', False, 'Let Them Eat Cake', 'End a Grievance with a cake',
     'Que Comam Bolo', 'Encerre uma Queixa com um bolo', None),
    ('industrial_action', 'clocking_in', 'minecraft:oak_sign', False, 'Industrial Action', 'Have a strike',
     'Ação Sindical', 'Tenha uma greve', None),
    ('collective_bargaining', 'industrial_action', 'minecraft:emerald', False, 'Collective Bargaining',
     'End a strike by agreement', 'Negociação Coletiva', 'Encerre uma greve com acordo', None),
    ('pizza_party', 'industrial_action', 'minecraft:cake', True, 'Pizza Party',
     'Give the Union Rep a cake without the back pay', 'Festa da Pizza', 'Dê um bolo ao representante sem pagar os atrasados', None),
]

# ---------------------------------------------------------------------------------------------------------------
# Each part's own lines: (key, en, pt). Floating lines go to MORE_SAY as (id, en, pt) with the part named in a comment.
MORE_REVIEWS = [      # grades, the Review, the slip, the roster's buttons, the work
    # the Roster's career (the Review and Promote buttons are pictures: their words are in the tooltips)
    ('gui.headinajar.time_clock.grade_line', 'Grade: %s', 'Cargo: %s'),
    ('gui.headinajar.time_clock.review_due_line', 'Review due: the tasks say %s', 'Avaliação pendente: as tarefas dizem %s'),
    ('gui.headinajar.time_clock.reviewed_today', 'Reviewed today. Let them settle.', 'Avaliado hoje. Deixe assentar.'),
    ('gui.headinajar.time_clock.review_away', 'Out of reach. Reviews are done in person.', 'Fora de alcance. Avaliação é cara a cara.'),
    ('gui.headinajar.time_clock.review_hint',
     'One Paper from your pockets becomes the slip. The grade goes to the highest the tasks earn. +5 morale. Once a day.',
     'Uma folha de Papel do seu bolso vira a ficha. O cargo vai ao mais alto que as tarefas pagam. +5 de moral. Uma vez por dia.'),
    ('gui.headinajar.time_clock.promote_hint',
     'Team Leads only. A seconded one is promoted here, at the clock. The rest go to the Board by the Surgical Saw.',
     'Só Líderes de Equipe. Um destacado é promovido aqui, no relógio. Os outros vão ao Conselho pela Serra Cirúrgica.'),
    # Sorting's extra filters (cards printed from 0.7.2)
    ('gui.headinajar.clipboard.more',
     'More filters for this Drop-Off, nine in all. Staff read as many as their grade allows: Intern 1, Associate 3, Senior 6, Team Lead 9.',
     'Mais filtros para esta Entrega, nove ao todo. Cada um lê quantos o cargo deixa: Estagiário 1, Associado 3, Sênior 6, Líder 9.'),
    ('gui.headinajar.clipboard.extras', 'More filters for %s', 'Mais filtros para %s'),
    ('gui.headinajar.clipboard.extra', 'An extra filter. Interns stop reading after the first.', 'Filtro extra. Estagiário para de ler no primeiro.'),
    (R + 'clipboard.more_sorting', 'Extra filters are for Sorting. Hauling takes what it is given.',
     'Filtro extra é coisa de Triagem. Transporte leva o que vier.'),
    (R + 'clipboard.more_drop_off', 'Extra filters go on a Drop-Off. A Pick-Up gives everything away.',
     'Filtro extra vai numa Entrega. A Coleta entrega tudo.'),
    (R + 'clipboard.filters_full', 'Nine filters on this Drop-Off. HR has run out of boxes.', 'Nove filtros nesta Entrega. O RH ficou sem caixinhas.'),
    ('tooltip.headinajar.job_card.filters', 'Filters a stop: up to %s', 'Filtros por parada: até %s'),
]
MORE_CEREMONY = []    # the ceremony's client side, the roster's Promote for secondees, the voice
MORE_STRIKES = [      # strikes, the ring, the Rep, the Board's motion
    # the Rep's demands when one or two wages are owed (three: strike.demands; more: strike.demands_more)
    (M + 'strike.demands_one', 'Back pay: %s. And cake.', 'Atrasados: %s. E bolo.'),
    (M + 'strike.demands_two', 'Back pay: %s and %s. And cake.', 'Atrasados: %s e %s. E bolo.'),
    # a striker who is not the Rep: an empty hand, and a cake (the Rep takes the cake, on the strike's terms)
    (R + 'strike.not_rep', '%s is on strike. Talk to the rep: %s.', '%s está em greve. Fale com o representante: %s.'),
    (R + 'strike.cake_rep', 'The cake goes to the rep, %s. Bargaining is collective.',
     'O bolo vai para o representante, %s. A negociação é coletiva.'),
    # the Performance Review slip used in the air or on a worker: it is read, by hovering
    (R + 'review.read', 'A review is filed, not handed over. Hover over it to read it.',
     'Avaliação se arquiva, não se entrega. Passe o cursor nela para ler.'),
]
MORE_GRIEVANCES = []  # the Grievance on plinth heads, site staff and the Employee Mount
MORE_SAY = []
# each part's own action-bar arguments and sound events (one dict each, so no two parts write the same lines)
ARGS_REVIEWS, ARGS_CEREMONY, ARGS_STRIKES, ARGS_GRIEVANCES = {}, {}, {}, {}
# the bill's longest real line: a Team Lead's two a dawn for seven dawns, in a long wage's name
BILL = ('14 Rotten Flesh', '14 Carne Podre')
ARGS_STRIKES = {
    M + 'strike.demands_one': lambda i: [[BILL[i]]],
    M + 'strike.demands_two': lambda i: [[BILL[i], BILL[i]]],
    R + 'strike.not_rep': lambda i: [[LONG, LONG]],
    R + 'strike.cake_rep': lambda i: [[LONG]],
}

# Action-bar lines with arguments: key -> lambda lang_index: [argument lists] (the longest real ones).
NAME = ('Bartholomew', 'Bartolomeu')
LONG = 'Supercalifragili'
SPECIES = ('Zombified Piglin', 'Piglin-Zumbificado')
GRADE = ('Senior Associate', 'Associado Sênior')
WAGE = ('2 Rotten Flesh', '2 Carne Podre')
ACTION_BAR_ARGS = {
    R + 'review.today': lambda i: [[LONG]],
    R + 'review.untrusted': lambda i: [[LONG]],
    R + 'review.away': lambda i: [[LONG]],
    M + 'review.raised': lambda i: [[LONG, GRADE[i]]],
    M + 'review.same': lambda i: [[LONG, GRADE[i]]],
    R + 'print.grade': lambda i: [[GRADE[i], '6']],
    R + 'promotion.below': lambda i: [[LONG, GRADE[i]]],
    R + 'promotion.others': lambda i: [[LONG, LONG]],
    R + 'promotion.far': lambda i: [[LONG]],
    M + 'promotion.candidate': lambda i: [[LONG]],
    M + 'promotion.whole': lambda i: [[LONG]],
    M + 'promotion.body': lambda i: [[LONG]],
    R + 'strike.short': lambda i: [[WAGE[i]]],
    M + 'strike.demands': lambda i: [[WAGE[i], WAGE[i], WAGE[i]]],
    M + 'strike.demands_more': lambda i: [[WAGE[i], WAGE[i]]],
}

# Sound events this release adds (tools/make_sounds.py makes the files): event -> (file, subtitle id, en, pt)
SOUNDS = {}
SOUNDS_REVIEWS, SOUNDS_CEREMONY, SOUNDS_STRIKES, SOUNDS_GRIEVANCES = {}, {}, {}, {}
SOUNDS_GRIEVANCES = {
    # the stamp and the sigh (tools/make_sounds.py staff/grievance)
    'staff.grievance': ('staff/grievance', 'staff.grievance', 'Staff files a grievance', 'Funcionário registra queixa'),
}
SOUNDS_STRIKES = {
    # a crowd's murmur at the clock as a chant starts, under each striker's own voice (the server plays those)
    'staff.chant': ('staff/chant', 'staff.chant', 'Staff chants', 'Equipe entoa palavras de ordem'),
    # the Union Rep accepts terms: a small cheer
    'staff.rep_accepts': ('staff/rep_accepts', 'staff.rep_accepts', 'Union Rep accepts terms', 'Representante aceita o acordo'),
}


# ---------------------------------------------------------------------------------------------------------------
def _models(write):
    """The slip's item model: the art's icon once it is painted; until then the vanilla paper."""
    tex = os.path.join(RES, 'assets', 'headinajar', 'textures', 'item', 'performance_review.png')
    layer = 'headinajar:item/performance_review' if os.path.exists(tex) else 'minecraft:item/paper'
    write('assets/headinajar/models/item/performance_review.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': layer}})


def _advancements(EN, PT, write):
    for adv, parent, icon, hidden, en_t, en_d, pt_t, pt_d, also in ADVANCEMENTS:
        criteria = {'done': {'trigger': 'headinajar:event', 'conditions': {'event': adv}}}
        if also:
            criteria['also'] = {'trigger': 'headinajar:event', 'conditions': {'event': also}}
        body = {
            'parent': f'headinajar:{parent}', 'criteria': criteria,
            'display': {'icon': {'id': icon}, 'title': {'translate': f'advancements.headinajar.{adv}.title'},
                        'description': {'translate': f'advancements.headinajar.{adv}.description'}, 'frame': 'task',
                        'show_toast': True, 'announce_to_chat': not hidden, 'hidden': hidden}}
        if also:
            body['requirements'] = [['done'], ['also']]
        write(f'data/headinajar/advancement/{adv}.json', body)
        for table, t, d in ((EN, en_t, en_d), (PT, pt_t, pt_d)):
            table[f'advancements.headinajar.{adv}.title'], table[f'advancements.headinajar.{adv}.description'] = t, d


def _sounds(EN, PT, write):
    path = os.path.join(RES, 'assets', 'headinajar', 'sounds.json')
    with open(path, encoding='utf-8') as f:
        table = json.load(f)
    for event, (file, subtitle, en, pt) in {**SOUNDS, **SOUNDS_REVIEWS, **SOUNDS_CEREMONY, **SOUNDS_STRIKES, **SOUNDS_GRIEVANCES}.items():
        assert os.path.exists(os.path.join(RES, 'assets', 'headinajar', 'sounds', file + '.ogg')), file
        table[event] = {'sounds': [{'name': f'headinajar:{file}', 'attenuation_distance': 16}],
                        'subtitle': f'subtitles.headinajar.{subtitle}'}
        EN[f'subtitles.headinajar.{subtitle}'], PT[f'subtitles.headinajar.{subtitle}'] = en, pt
    write('assets/headinajar/sounds.json', table)


def _placards(EN, PT, write):
    """Notices 15 and 17 join the cycle: gen_p39's placard models and blockstate again, every notice drawn."""
    import gen_p39_dna as D
    for n in (15, 17):
        if n not in D.PLACARD_SHIPPED:
            D.PLACARD_SHIPPED = sorted(D.PLACARD_SHIPPED + [n])
    D._placards(EN, PT, write)


def _check_say(EN, PT):
    from gen_p27_clarity import wrap
    import gen_p41_workforce as W
    over = []
    for key in EN:
        if not key.startswith(S + 'say.') or key not in OURS:
            continue
        for i, table in enumerate((EN, PT)):
            if len(wrap(table[key], W.SAY_PX)) > 2:
                over.append((key, ('en', 'pt')[i], table[key]))
    assert not over, f'{len(over)} floating lines take more than two lines of {W.SAY_PX} px: {over[:5]}'


OURS = set()


# ---------------------------------------------------------------------------------------------------------------
# The handbook: three entries after Secondment, and the Grievance on the morale page (Form 40-M).
MOMENTS_ENTRIES = [
    {'cat': 'staffing', 'id': 'grades', 'sort': 19, 'icon': 'headinajar:performance_review',
     'en': 'Grades and Reviews', 'pt': 'Cargos e Avaliações',
     'pages': [
         ('text',
          'Staff climb by tasks: $(l)Intern$(), $(l)Associate$() at 50, $(l)Senior Associate$() at 300, $(l)Team '
          'Lead$() at 1,000. A grade takes effect at a $(l)Performance Review$(), never by itself.',
          'A equipe sobe por tarefas: $(l)Estagiário$(), $(l)Associado$() com 50, $(l)Associado Sênior$() com 300, '
          '$(l)Líder de Equipe$() com 1.000. O cargo só vale depois da $(l)Avaliação de Desempenho$().'),
         ('text',
          'When the tasks are there, the Roster says "Review due". Press $(l)Review$() on its line with Paper in your '
          'inventory: one sheet becomes the slip, and the grade jumps to the highest the tasks earn.$(br2)+5 morale, '
          'once a day.',
          'Com as tarefas feitas, o Quadro mostra "Avaliação pendente". Aperte $(l)Avaliar$() na linha com Papel no '
          'inventário: uma folha vira a ficha, e o cargo vai ao mais alto que as tarefas pagam.$(br2)+5 de moral, uma '
          'vez por dia.'),
         ('form', ('Form 50-G: Grades', 'Formulário 50-G'),
          'Associate: 10% faster, 3 filters a stop$(br)Senior: 20% faster, 6 filters, carries half again$(br)Team '
          'Lead: 30% faster, 9 filters, carries half again, two wages a dawn, a tie; staff within 8 blocks work 10% faster',
          'Associado: 10% mais rápido, 3 filtros por parada$(br)Sênior: 20% mais rápido, 6 filtros, leva metade a '
          'mais$(br)Líder: 30% mais rápido, 9 filtros, leva metade a mais, dois salários por dia, gravata; a equipe a '
          '8 blocos rende 10% mais'),
         ('text',
          'The slip: tasks since the last review, days unpaid, grievances, strengths, areas for growth, a rating. A '
          'Grievance since the last review means a Performance Plan, whatever else it did.$(br2)Interns never strike.',
          'A ficha: tarefas desde a última avaliação, dias sem salário, queixas, pontos fortes, a desenvolver e a nota. '
          'Uma Queixa desde a última avaliação dá Plano de Desempenho, faça o que fizer.$(br2)Estagiário não faz greve.'),
     ],
     'note': ('Areas for growth: hands.', 'A desenvolver: mãos.')},
    {'cat': 'staffing', 'id': 'promotion', 'sort': 20, 'icon': 'headinajar:life_support_jar',
     'en': 'Promotion to the Board', 'pt': 'Promoção ao Conselho',
     'pages': [
         ('text',
          'Your own living $(l)Team Lead$(), an empty jar big enough for its head in your off hand, the Surgical Saw '
          'in your main hand: $(l)Use$(). The half-health rule is waived. It has agreed.',
          'Seu próprio $(l)Líder de Equipe$() vivo, um pote vazio do tamanho da cabeça na outra mão, a Serra '
          'Cirúrgica na mão principal: $(l)Use$(). A regra da meia vida é dispensada. Ele concordou.'),
         ('text',
          'Eight seconds: DOLORES makes the offer, staff within 16 blocks stop to watch, a nod, confetti, the jingle, '
          'the cut, the bell, and "Congratulations on your promotion."$(br2)Nobody may hurt the candidate meanwhile.',
          'Oito segundos: DOLORES faz a proposta, a equipe a 16 blocos para e assiste, um aceno, confete, o jingle, '
          'o corte, a campainha e "Parabéns pela promoção."$(br2)Ninguém fere o candidato durante a cerimônia.'),
         ('text',
          'You keep both halves. The $(l)head$() makes half again its species\' rate at a Cerebral Array: three '
          'promoted heads an Array, the rest at the plain rate. The $(l)body$() stays standing, a host at 50% sync '
          'with you.',
          'Você fica com as duas metades. A $(l)cabeça$() rende metade a mais na Matriz Cerebral: três promovidas '
          'por Matriz, as outras no normal. O $(l)corpo$() fica de pé, hospedeiro com 50% de sincronia com você.'),
         ('text',
          'A seconded Team Lead is promoted from the Roster: $(l)Promote$() within 8 blocks of its clock.$(br2)Never '
          'with a Separation Chair running within 32 blocks, never during a strike. Slimes and the like go whole.',
          'Um Líder destacado é promovido pelo Quadro: $(l)Promover$() a até 8 blocos do relógio.$(br2)Nunca com '
          'uma Cadeira de Separação operando a 32 blocos, nunca durante greve. Slimes e afins vão inteiros.'),
     ],
     'note': ('The Board does not take interns. It has tried.', 'O Conselho não aceita estagiários. Já tentou.')},
    {'cat': 'staffing', 'id': 'strikes', 'sort': 21, 'icon': 'minecraft:oak_sign',
     'en': 'Strikes', 'pt': 'Greves',
     'pages': [
         ('text',
          'At dawn a site strikes if half its staff that can strike, two at least, are under 40 morale, or two have '
          'gone unpaid two dawns running. Interns, wolves and Nitwits are not counted.',
          'No amanhecer, um posto entra em greve se metade de quem pode fazer greve, dois no mínimo, está abaixo de 40 '
          'de moral, ou se dois ficaram dois amanheceres sem salário. Estagiários, lobos e Palermas não contam.'),
         ('text',
          'The strikers stand in a ring round the clock with picket signs. The clock pulses red. They chant. Nothing '
          'is broken and nobody is hurt.$(br2)The $(l)Union Rep$() wears a red rosette.',
          'Os grevistas fazem uma roda em volta do relógio, com cartazes. O relógio pisca vermelho. Eles entoam. '
          'Nada quebra e ninguém se fere.$(br2)O $(l)Representante$() usa uma roseta vermelha.'),
         ('text',
          '$(l)Use$() the Rep with an empty hand for the demands: each striker\'s wage for every dawn it went unpaid, '
          'up to 7. Put it in the Pay Drawer, then $(l)Use$() a cake on the Rep. Everyone goes back at Content.',
          '$(l)Use$() o Representante de mão vazia para ouvir as reivindicações: o salário de cada grevista por '
          'amanhecer sem paga, até 7. Ponha na Gaveta de Pagamento e $(l)use$() um bolo no Representante.'),
         ('text',
          'Left alone three loaded days, a strike collapses: everyone at 20. No strike in a world\'s first week, and '
          'none at that clock for three days after.$(br2)If the Board really carries "unionise" near a site that '
          'qualifies, it walks out at once.',
          'Largada três dias carregada, a greve acaba sozinha: todos com 20. Nenhuma greve na primeira semana do '
          'mundo, nem nesse relógio por três dias depois.$(br2)Se o Conselho aprovar de verdade "formar um sindicato" perto '
          'de um posto elegível, ele sai na hora.'),
     ],
     'note': ('Noggin & Sons respects its staff\'s right to organise. Noggin & Sons is also keeping a list.',
              'A Noggin & Sons respeita o direito de organização. A Noggin & Sons também mantém uma lista.')},
]
JEI_MOMENTS = {
    'headinajar:performance_review': ('Printed from Paper at the Time Clock\'s Roster. Hover to read it.',
                                      'Impressa de Papel no Quadro do Relógio de Ponto. Passe o mouse para ler.'),
}
# the morale page's Form 40-M: the Grievance is no longer a label
GRIEVANCE_FORM = (
    'Starts at 50: +10 a wage paid, -15 a dawn unpaid.$(br)$(l)Engaged$(), 75-100: chin up$(br)$(l)Content$(), '
    '40-74$(br)$(l)Disengaged$(), 15-39: sags, looks away$(br)$(l)Grievance$(): under 15 two dawns running. Faces the '
    'wall and stops work; a cake or back to 40 ends it.',
    'Começa em 50; +10 pago, -15 sem paga.$(br)$(l)Engajado$(), 75-100: queixo erguido$(br)$(l)Satisfeito$(), '
    '40-74$(br)$(l)Desmotivado$(), 15-39: murcha, olha de lado$(br)$(l)Queixa Formal$(): abaixo de 15 em dois '
    'amanheceres. Vira para a parede e para; bolo ou 40 de moral encerra.')


def _handbook(EN, PT):
    import gen_p27_handbook_b as hb
    import gen_p39_dna as D
    have = {(e['cat'], e['id']) for e in hb.ENTRIES}
    for e in MOMENTS_ENTRIES:
        if (e['cat'], e['id']) not in have:
            hb.ENTRIES.append(e)
    hb.JEI_INFO.update(JEI_MOMENTS)
    hb.JEI_INFO['headinajar:safety_placard'] = ('Hangs on a wall. Use it to change the notice: there are twenty-one.',
                                               'Fica pendurada na parede. Use para trocar o aviso: são vinte e um.')
    pay = next(e for e in hb.ENTRIES if (e['cat'], e['id']) == ('staffing', 'pay'))
    for n, page in enumerate(pay['pages']):
        if page[0] == 'form' and page[1][0] == 'Form 40-M: Morale':
            pay['pages'][n] = ('form', page[1], GRIEVANCE_FORM[0], GRIEVANCE_FORM[1])
    D.WRITTEN_PAGES = max(D.WRITTEN_PAGES, 232)
    D._raise_written_cap()


# ---------------------------------------------------------------------------------------------------------------
def run(EN, PT, write, shaped):
    lines = LINES + MORE_REVIEWS + MORE_CEREMONY + MORE_STRIKES + MORE_GRIEVANCES
    lines += [(f'{S}say.{sid}', en, pt) for sid, en, pt in MORE_SAY]
    for sid, s_en, a_en, s_pt, a_pt in STRENGTHS:
        lines += [(f'{S}strength.{sid}', s_en, s_pt), (f'{S}growth.{sid}', a_en, a_pt)]
    for sid, s_en, a_en, s_pt, a_pt in DEPARTMENT_STRENGTHS:
        lines += [(f'{S}strength.dept.{sid}', s_en, s_pt), (f'{S}growth.dept.{sid}', a_en, a_pt)]
    keys = [k for k, _e, _p in lines]
    assert len(keys) == len(set(keys)), sorted(k for k in keys if keys.count(k) > 1)
    taken = [key for key in keys if key in EN]
    assert not taken, f'already in the language files: {taken}'
    for key, en, pt in lines:
        EN[key], PT[key] = en, pt
    OURS.update(keys)
    _models(write)
    _strike_models(write)
    _advancements(EN, PT, write)
    _sounds(EN, PT, write)
    _check_say(EN, PT)
    import gen_p39_dna as D
    saved = D.ACTION_BAR_ARGS
    D.ACTION_BAR_ARGS = {**ACTION_BAR_ARGS, **ARGS_REVIEWS, **ARGS_CEREMONY, **ARGS_STRIKES, **ARGS_GRIEVANCES}
    try:
        D._check_action_bar(EN, PT, keys)
    finally:
        D.ACTION_BAR_ARGS = saved
    _placards(EN, PT, write)
    _handbook(EN, PT)


# ---------------------------------------------------------------------------------------------------------------
# The ceremony's part: the Roster's Promote for a seconded Team Lead.
MORE_CEREMONY += [
    (R + 'promotion.in_person', '%s is promoted in person, with the Saw and an empty jar.',
     '%s é promovido pessoalmente, com a Serra e um pote vazio.'),
    (M + 'promotion.summoned', '%s has been asked to the clock. The Board is assembling.',
     '%s foi convocado ao relógio. O Conselho está se reunindo.'),
]
ARGS_CEREMONY.update({
    R + 'promotion.in_person': lambda i: [[LONG]],
    M + 'promotion.summoned': lambda i: [[LONG]],
})

# The strikes' part.
def _strike_models(write):
    """The picket signs StrikeClient draws: the art's held sign, board and card (art/entity/picket_sign/picket_sign_geo.py)
    for each slogan, when the art is in the tree. Until then none are written and the client draws no sign."""
    import importlib
    import sys
    folder = os.path.join(ROOT, 'art', 'entity', 'picket_sign')
    if not os.path.exists(os.path.join(folder, 'picket_sign_geo.py')):
        return
    sys.path.insert(0, folder)
    for path, model in importlib.import_module('picket_sign_geo').java_models().items():
        write(path, model)

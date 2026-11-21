"""Lines and data for the 0.7.0 release (The workforce): the Staff Lanyard, the Time Clock, the Clipboard and Job Cards,
the four jobs, the Hard Hat, the Supervisor's Whistle, the server rules and placard notices 13, 14, 19 and 20. Imported
by gen_data.py after gen_p40_bodies and before the handbook generator (gen_p27_clarity), whose text check and handbook
read the final lines and entries.

It writes:
- the staff files again (data/headinajar/headinajar/staff/<species>.json, as gen_p38 wrote them) with each species'
  "work" object: whether it may be hired, its jobs and speeds, what it cannot do, its shift, carry, hands, walk speed,
  movement family, attack and quirks (read by staff/work/WorkTable.java);
- recipes, the Time Clock's blockstate, models, loot and pickaxe tag; item models;
- five advancements, three memos, four placard notices;
- every new language line, en_us and pt_br; each action-bar line is checked under 400 px with its longest real
  arguments, and each floating stop line under 200 px, in both languages.

The sections marked for a part of the work (MORE_SITE, MORE_MOVERS, MORE_JOBS, MORE_WHISTLE, MORE_RULES, MORE_LOOK) are
where each part adds the lines it needs beyond the core table.
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..')
RES = os.path.join(ROOT, 'src', 'main', 'resources')
MC = 'minecraft:'

# ---------------------------------------------------------------------------------------------------------------
# What each species does at work (the book's aptitude table, Chapter 10). A job not listed as good or cannot is done at
# x1. Hands: shearing, milking, holding a weapon. Carry: stacks; carry_items caps the items (16 for the small ones).
ALL = ('hauling', 'sorting', 'farming', 'security')

# Vanilla movement speed attributes (Attributes.MOVEMENT_SPEED defaults) for the walkers: the "walk" modifier aims a
# worker at about 2 blocks a second. Speed goes as (attribute x modifier)^2 (vanilla's move control sets both the speed
# and the forward input to attribute x modifier). Measured along a path on grass (StaffMoverGameTests): every walker
# goes 1.47 blocks a second at 0.185, so about 42.9 x (attribute x modifier)^2; AIM 0.216 gives 2.0.
SPEED = {
    'villager': 0.5, 'wandering_trader': 0.5, 'zombie': 0.23, 'drowned': 0.23, 'zombie_villager': 0.23, 'husk': 0.23,
    'skeleton': 0.25, 'stray': 0.25, 'bogged': 0.25, 'wither_skeleton': 0.25, 'zombified_piglin': 0.23,
    'vindicator': 0.35, 'pillager': 0.35, 'evoker': 0.5, 'illusioner': 0.5, 'witch': 0.25, 'piglin': 0.35,
    'piglin_brute': 0.35, 'hoglin': 0.3, 'zoglin': 0.3, 'ravager': 0.3, 'polar_bear': 0.25, 'iron_golem': 0.25,
    'enderman': 0.3, 'spider': 0.3, 'cave_spider': 0.3, 'fox': 0.3, 'wolf': 0.3, 'cat': 0.3, 'ocelot': 0.3,
    'snow_golem': 0.2, 'horse': 0.225, 'donkey': 0.175, 'mule': 0.175, 'camel': 0.09, 'llama': 0.175,
    'trader_llama': 0.175, 'skeleton_horse': 0.2, 'zombie_horse': 0.2, 'cow': 0.2, 'mooshroom': 0.2, 'pig': 0.25,
    'sheep': 0.23, 'chicken': 0.25, 'rabbit': 0.3, 'goat': 0.2, 'panda': 0.15, 'sniffer': 0.1, 'frog': 1.0,
    'silverfish': 0.25, 'endermite': 0.25, 'strider': 0.175, 'armadillo': 0.14, 'turtle': 0.25,
}
AIM = 0.216
# Species that do not walk on vanilla's walking move control, their modifier measured to about 2 blocks a second by
# StaffMoverGameTests (swimmers go as the modifier squared as well). A slime's or magma cube's hop is carried at a steady
# pace by Movers (its own air control overshoots or crawls by size), so theirs is 1.
MEASURED = {'dolphin': 0.63, 'axolotl': 0.34, 'slime': 1.0, 'magma_cube': 1.0}
# Horses go a quarter faster than the rest: x1.25 in blocks a second is x1.118 on the modifier.
HORSE = 1.25 ** 0.5


def walk(species, factor=1.0):
    if species in MEASURED:
        return MEASURED[species]
    a = SPEED.get(species)
    if a is None:
        return 1.0
    return round(max(0.25, min(3.0, AIM * factor / a)), 3)


def W(species, good=None, cannot=(), cannot_key=None, shift='day', carry=1, items=0, hands=False, move='walk',
      attack='none', melee=0.0, quirks=(), card=True, water=False, hire=None, factor=1.0, keys=None):
    """A work object. good: {job: speed}; cannot: jobs it cannot do (refused with cannot_key, or keys[job])."""
    jobs = {j: 1.0 for j in ALL if j not in cannot}
    if attack in ('none',) and 'security' in jobs and not (good and 'security' in good):
        del jobs['security']
    jobs.update(good or {})
    out = {'hire': True if hire is None else hire, 'jobs': jobs}
    refusals = dict(keys or {})
    for j in ALL:
        if j not in jobs and cannot_key and j not in refusals:
            refusals[j] = cannot_key
    if refusals:
        out['cannot'] = refusals
    out.update({'shift': shift, 'carry': carry})
    if items:
        out['carry_items'] = items
    out.update({'hands': hands, 'walk': walk(species, factor), 'move': move, 'attack': attack})
    if melee:
        out['melee'] = melee
    if quirks:
        out['quirks'] = list(quirks)
    if not card:
        out['card'] = False
    if water:
        out['water'] = True
    return out


UNDEAD_SORT = {'sorting': 'card.cannot.sorting_undead'}
NOT_HIRED = {  # species with a staff file that a lanyard refuses, and why
    'creeper': 'lanyard.creeper', 'cod': 'lanyard.no_legs', 'salmon': 'lanyard.no_legs', 'pufferfish': 'lanyard.no_legs',
    'tropical_fish': 'lanyard.no_legs', 'tadpole': 'lanyard.no_legs', 'bat': 'lanyard.no_legs', 'guardian': 'lanyard.no_legs',
    'vex': 'lanyard.vex', 'shulker': 'lanyard.shulker', 'warden': 'lanyard.overqualified',
    'elder_guardian': 'lanyard.overqualified', 'wither': 'lanyard.overqualified', 'giant': 'lanyard.overqualified',
}
GENERAL_DUTIES = ['cow', 'mooshroom', 'pig', 'sheep', 'chicken', 'rabbit', 'goat', 'panda', 'sniffer', 'frog',
                  'silverfish', 'endermite', 'strider']
SMALL = {'chicken', 'rabbit', 'frog', 'silverfish', 'endermite'}


def _work_table():
    t = {}
    # villagers: all four at x1; a profession's own row over it
    t['villager'] = W('villager', hands=True, attack='melee', melee=3, quirks=('no_trade_on_shift',))
    t['villager']['professions'] = {
        MC + 'farmer': {'jobs': {**t['villager']['jobs'], 'farming': 1.5}},
        MC + 'librarian': {'jobs': {**t['villager']['jobs'], 'sorting': 1.25}, 'quirks': ['no_trade_on_shift', 'never_misfiles']},
        MC + 'shepherd': {'jobs': {**t['villager']['jobs'], 'farming': 1.5}, 'quirks': ['no_trade_on_shift', 'shepherd']},
        MC + 'leatherworker': {'carry': 2},
        MC + 'fletcher': {'jobs': {**t['villager']['jobs'], 'security': 1.25}, 'attack': 'bow'},
        MC + 'armorer': {'jobs': {**t['villager']['jobs'], 'security': 1.25}, 'melee': 3.75},
        MC + 'weaponsmith': {'jobs': {**t['villager']['jobs'], 'security': 1.25}, 'melee': 3.75},
        MC + 'toolsmith': {'quirks': ['no_trade_on_shift', 'toolsmith']},
        MC + 'cleric': {'quirks': ['no_trade_on_shift', 'cleric']},
        MC + 'nitwit': {'jobs': {}, 'cannot': {j: 'card.cannot.nitwit' for j in ALL}, 'quirks': ['nitwit'], 'card': False, 'carry': 0},
    }
    t['wandering_trader'] = W('wandering_trader', good={'hauling': 1.25}, shift='any', hands=True, attack='melee', melee=2,
                              quirks=('trader_stays',))
    for s in ('zombie', 'drowned', 'zombie_villager'):
        t[s] = W(s, good={'hauling': 1.25}, cannot=('sorting',), keys=UNDEAD_SORT, shift='night', hands=True, attack='melee',
                 quirks=('burns',))
    t['husk'] = W('husk', good={'hauling': 1.25}, cannot=('sorting',), keys=UNDEAD_SORT, shift='any', hands=True, attack='melee')
    for s in ('skeleton', 'stray', 'bogged'):
        t[s] = W(s, good={'security': 1.0}, shift='night', hands=True, attack='bow', quirks=('burns',))
    for s in ('wither_skeleton', 'zombified_piglin'):
        t[s] = W(s, shift='any', hands=True, attack='melee')
    t['vindicator'] = W('vindicator', good={'security': 1.25}, shift='any', hands=True, attack='melee')
    t['pillager'] = W('pillager', good={'security': 1.25}, shift='any', hands=True, attack='crossbow')
    for s in ('evoker', 'illusioner'):
        t[s] = W(s, cannot=ALL, cannot_key='card.cannot.evoker', hands=True, carry=0)
    t['witch'] = W('witch', cannot=('security',), shift='any', hands=True, quirks=('witch_regen',))
    t['piglin'] = W('piglin', shift='any', hands=True, attack='crossbow', quirks=('pocket', 'zombifies'))
    t['piglin_brute'] = W('piglin_brute', good={'security': 1.5}, cannot=('sorting',), shift='any', hands=True, attack='melee',
                          quirks=('zombifies',))
    for s, fast in (('hoglin', 1.0), ('zoglin', 1.0), ('ravager', 1.25), ('polar_bear', 1.0), ('iron_golem', 1.25)):
        t[s] = W(s, good={'security': fast}, cannot=('sorting', 'farming'), shift='any', carry=2, hands=s == 'iron_golem',
                 attack='melee', quirks=('poppy',) if s == 'iron_golem' else ())
    t['enderman'] = W('enderman', good={'hauling': 1.5}, shift='any', items=16, hands=True, move='teleport', attack='melee',
                      quirks=('shy', 'out_of_the_rain'))
    t['blaze'] = W('blaze', cannot=('farming',), shift='any', items=16, move='fly_free', attack='fireball')
    t['ghast'] = W('ghast', cannot=('farming', 'sorting', 'security'), shift='any', carry=6, move='fly_free', factor=0.6)
    for s in ('slime', 'magma_cube'):
        t[s] = W(s, cannot=('farming', 'sorting'), shift='any', items=16, move='hop', attack='melee', quirks=('carried_inside',))
    for s in ('spider', 'cave_spider'):
        t[s] = W(s, cannot=('sorting',), shift='any', move='climb', attack='melee')
    t['phantom'] = W('phantom', good={'security': 1.0}, cannot=('hauling', 'sorting', 'farming'), shift='night', carry=0,
                     move='fly_free', attack='swoop', quirks=('burns',))
    t['fox'] = W('fox', shift='night', items=1, attack='melee', quirks=('mouth', 'berries_x2'))
    t['wolf'] = W('wolf', cannot=('sorting',), shift='any', items=16, attack='melee', quirks=('never_strikes',))
    for s in ('cat', 'ocelot'):
        t[s] = W(s, cannot=ALL, cannot_key='card.cannot.cat', shift='any', carry=0, card=False,
                 quirks=('keeps_creepers_out',) + (('sits_on_chests',) if s == 'cat' else ()))
    t['snow_golem'] = W('snow_golem', shift='any', items=16, attack='snowball')
    t['allay'] = W('allay', good={'sorting': 2.0, 'hauling': 1.0}, items=16, hands=True, move='fly')
    t['bee'] = W('bee', good={'farming': 1.0}, cannot=('hauling', 'sorting', 'security'), carry=0, move='fly', quirks=('pollinates',))
    t['parrot'] = W('parrot', good={'security': 1.0}, cannot=('hauling', 'sorting', 'farming'), shift='any', carry=0, move='fly',
                    attack='lookout')
    t['armadillo'] = W('armadillo', good={'security': 1.0}, cannot=('hauling', 'sorting', 'farming'), shift='any', carry=0,
                       attack='lookout')
    for s in ('horse', 'donkey', 'mule', 'camel', 'llama', 'trader_llama', 'skeleton_horse', 'zombie_horse'):
        t[s] = W(s, good={'hauling': 1.25 if s == 'horse' else 1.0}, cannot=('farming', 'sorting'), carry=2,
                 factor=HORSE if s == 'horse' else 1.0, quirks=('spits_unpaid',) if 'llama' in s else ())
    for s in GENERAL_DUTIES:
        q = ['pleasant']
        q += {'chicken': ['eggs_into_carry'], 'goat': ['rams'], 'panda': ['personality']}.get(s, [])
        t[s] = W(s, cannot=('sorting', 'farming', 'security'), shift='any' if s == 'strider' else 'day',
                 items=16 if s in SMALL else 0, move='lava' if s == 'strider' else 'walk', quirks=q)
    t['squid'] = W('squid', cannot=('sorting', 'farming', 'security'), shift='any', move='squid', water=True)
    t['glow_squid'] = W('glow_squid', cannot=('sorting', 'farming', 'security'), shift='any', move='squid', water=True)
    t['dolphin'] = W('dolphin', good={'hauling': 1.5}, cannot=('sorting', 'farming', 'security'), shift='any', move='water',
                     water=True, quirks=('surfaces',))
    t['turtle'] = W('turtle', cannot=('sorting', 'farming', 'security'), shift='any', move='water', water=True)
    t['axolotl'] = W('axolotl', good={'security': 1.0}, cannot=('sorting', 'farming'), shift='any', move='water', water=True,
                     attack='melee')
    for s, key in NOT_HIRED.items():
        t[s] = {'hire': key}
    return t


WORK = _work_table()

# ---------------------------------------------------------------------------------------------------------------
# The core lines (the plan's table). en, pt.
LINES = [
    # names
    ('item.headinajar.staff_lanyard', 'Staff Lanyard', 'Crachá de Funcionário'),
    ('block.headinajar.time_clock', 'Time Clock', 'Relógio de Ponto'),
    ('item.headinajar.clipboard', 'Clipboard', 'Prancheta'),
    ('item.headinajar.job_card', 'Job Card', 'Cartão de Tarefa'),
    ('item.headinajar.hard_hat', 'Hard Hat', 'Capacete de Obra'),
    ('item.headinajar.supervisors_whistle', "Supervisor's Whistle", 'Apito do Supervisor'),
    ('item.headinajar.separation_notice', 'Notice of Separation (Form 45-S)', 'Aviso de Desligamento (Formulário 45-S)'),
    ('item.headinajar.separation_notice.text',
     'Notice of Separation. Your employment with Noggin & Sons has ended. Please return your lanyard. (You have.) '
     'p.p. Non-Human Resources.',
     'Aviso de Desligamento. Seu vínculo com a Noggin & Sons foi encerrado. Separação de corpos, só na Cadeira; esta foi '
     'amigável. Devolva o crachá. (Já devolveu.) p.p. Recursos Não Humanos.'),
    ('item.headinajar.separation_notice.record', 'No. %s %s (%s), day %s', 'Nº %s %s (%s), dia %s'),
    # jobs, stops, shifts, statuses
    ('staff.headinajar.job.hauling', 'Hauling', 'Transporte'),
    ('staff.headinajar.job.sorting', 'Sorting', 'Triagem'),
    ('staff.headinajar.job.farming', 'Farming', 'Lavoura'),
    ('staff.headinajar.job.security', 'Security', 'Segurança'),
    ('staff.headinajar.job.general', 'General Duties', 'Serviços Gerais'),
    ('staff.headinajar.stop.pick_up', 'Pick-Up', 'Coleta'),
    ('staff.headinajar.stop.drop_off', 'Drop-Off', 'Entrega'),
    ('staff.headinajar.stop.everything_else', 'Everything else', 'Todo o resto'),
    ('staff.headinajar.shift.day', 'Day', 'Dia'),
    ('staff.headinajar.shift.night', 'Night', 'Noite'),
    ('staff.headinajar.shift.redstone', 'Redstone', 'Redstone'),
    ('staff.headinajar.status.on_job', 'On the job', 'No serviço'),
    ('staff.headinajar.status.waiting', 'Waiting', 'Esperando'),
    ('staff.headinajar.status.off_shift', 'Off shift', 'Fora do turno'),
    ('staff.headinajar.status.off_site', 'Off site', 'Fora do local'),
    ('staff.headinajar.status.awaiting', 'Awaiting assignment', 'Aguardando designação'),
    ('staff.headinajar.status.detail', 'On secondment', 'Em destacamento'),
    ('staff.headinajar.status.shoulder', 'On your shoulder', 'No seu ombro'),
    ('staff.headinajar.status.break', 'On break', 'Em pausa'),
    ('staff.headinajar.status.notice', 'Serving notice', 'Cumprindo aviso'),
    ('staff.headinajar.reason.restructuring', 'restructuring', 'reestruturação'),
    ('staff.headinajar.another_employer', 'another employer', 'outro empregador'),
    # the clock's screen
    ('gui.headinajar.time_clock.roster', 'Roster', 'Quadro de Pessoal'),
    ('gui.headinajar.time_clock.rack', 'Job Rack', 'Quadro de Tarefas'),
    ('gui.headinajar.time_clock.drawer', 'Pay Drawer', 'Gaveta de Pagamento'),
    ('gui.headinajar.time_clock.shifts', 'Shifts', 'Turnos'),
    ('gui.headinajar.time_clock.rename', 'Rename', 'Renomear'),
    ('gui.headinajar.time_clock.recall', 'Recall', 'Chamar de Volta'),
    ('gui.headinajar.clipboard.print', 'Print', 'Imprimir'),
    # the Lanyard
    ('refusal.headinajar.lanyard.baby', "Child labour is against company policy. Come back when it's grown.",
     'Trabalho infantil é contra a política da empresa. Volte quando crescer.'),
    ('refusal.headinajar.lanyard.not_ready', 'The candidate is not ready to listen. Wear it down to half its health first.',
     'O candidato não está pronto para ouvir. Deixe-o com metade da vida antes.'),
    ('refusal.headinajar.lanyard.poach', 'Already employed by %s. Poaching staff is against policy.',
     'Já trabalha para %s. Roubar funcionários é contra a política.'),
    ('refusal.headinajar.lanyard.village_golem', 'Already employed by the village. We do not poach from the public sector.',
     'Já trabalha para a vila. Não roubamos gente do setor público.'),
    ('refusal.headinajar.lanyard.host', "Hosts don't wear lanyards. A lanyard needs a neck with a head on it.",
     'Hospedeiro não usa crachá. Crachá precisa de um pescoço com cabeça.'),
    ('refusal.headinajar.lanyard.overqualified', 'This candidate is overqualified. And very loud.',
     'Este candidato é qualificado demais. E barulhento demais.'),
    ('refusal.headinajar.lanyard.creeper', 'A creeper goes off once. HR is drawing up a job for it.',
     'Um creeper só explode uma vez. O RH está criando uma vaga para ele.'),
    ('refusal.headinajar.lanyard.no_legs', 'This candidate has no legs and no prospects.', 'Este candidato não tem pernas nem futuro.'),
    ('refusal.headinajar.lanyard.vex', 'There is nothing to a vex but intention. HR cannot hire intention.',
     'Um vex é só intenção. O RH não contrata intenção.'),
    ('refusal.headinajar.lanyard.shulker', 'Shulkers do not commute.', 'Shulker não pega condução.'),
    ('refusal.headinajar.lanyard.player', 'Colleagues are hired by HR, not by lanyard.', 'Colegas são contratados pelo RH, não por crachá.'),
    ('refusal.headinajar.lanyard.headcount', 'Headcount frozen: %s staff on file. Let someone go first.',
     'Quadro congelado: %s na ficha. Dispense alguém antes.'),
    ('refusal.headinajar.lanyard.disabled', 'Staffing is not licensed on this server.', 'Contratações não são permitidas neste servidor.'),
    ('refusal.headinajar.lanyard.no_hostiles', 'Head Office does not hire hostiles on this server.',
     'A Matriz não contrata hostis neste servidor.'),
    ('refusal.headinajar.lanyard.unknown', 'HR has no job description for a %s yet.', 'O RH ainda não tem descrição de cargo para %s.'),
    ('refusal.headinajar.lanyard.nobody', 'Use it on a creature. HR does not hire thin air.',
     'Use numa criatura. O RH não contrata o vento.'),
    ('message.headinajar.lanyard.hired', '%s is on staff. Awaiting assignment.', '%s agora é do quadro. Aguardando designação.'),
    # staff
    ('refusal.headinajar.staff.let_go_not_yours', '%s reports to %s. Only they may let it go.',
     '%s responde a %s. Só essa pessoa pode dispensar.'),
    ('refusal.headinajar.staff.reports_to', '%s reports to %s.', '%s responde a %s.'),
    ('refusal.headinajar.staff.saw', '%s is staff. Let them go first.', '%s é funcionário. Dispense-o antes.'),
    ('refusal.headinajar.staff.already_let_go', '%s has already been let go. The paperwork is with HR.',
     '%s já foi dispensado. A papelada está com o RH.'),
    # the clock
    ('refusal.headinajar.clock.untrusted', "This clock is %s's. Ask them to trust you.", 'Este relógio é de %s. Peça para confiarem em você.'),
    ('refusal.headinajar.clock.full', 'Site full: %s staff. Open another site with a second Time Clock.',
     'Local lotado: %s. Abra outro local com outro Relógio de Ponto.'),
    ('refusal.headinajar.clock.too_close', 'Too close to another site. Clocks need %s blocks between them.',
     'Outro local perto demais. Relógios pedem %s blocos entre si.'),
    ('refusal.headinajar.clock.disabled', 'Staffing is not licensed on this server. The clock just keeps time.',
     'Contratações não são permitidas neste servidor. O relógio só marca a hora.'),
    # the Clipboard and cards
    ('refusal.headinajar.clipboard.outside', 'Outside the site. Stops must be within %s blocks of the clock.',
     'Fora do local. Paradas ficam a até %s blocos do relógio.'),
    ('refusal.headinajar.clipboard.not_yours', "That chest isn't yours to order about. Ask its owner.",
     'Esse baú não está sob suas ordens. Fale com o dono.'),
    ('refusal.headinajar.clipboard.no_paper', 'No paper to print on. Bring paper.', 'Sem papel para imprimir. Traga papel.'),
    ('refusal.headinajar.clipboard.too_big', 'The area is too big: a field is at most 16 by 16.',
     'A área é grande demais: um campo tem no máximo 16 por 16.'),
    ('refusal.headinajar.clipboard.unbound', 'Bind the clipboard to a Time Clock first: use it on the clock.',
     'Vincule a prancheta a um Relógio de Ponto antes: use-a no relógio.'),
    ('refusal.headinajar.clipboard.empty', 'Nothing on this order yet. Mark a stop first.', 'Nada neste pedido ainda. Marque uma parada antes.'),
    ('refusal.headinajar.clipboard.field_drop_off', 'A field needs a Drop-Off for the harvest.',
     'Um campo precisa de uma Entrega para a colheita.'),
    ('refusal.headinajar.card.other_site', 'This card is for the site at %s. This employee works here.',
     'Este cartão é do local em %s. Este funcionário trabalha aqui.'),
    ('refusal.headinajar.card.closed', 'This site has closed.', 'Este local foi fechado.'),
    ('refusal.headinajar.card.cannot.sorting_undead', 'Zombies cannot read the labels. Give Sorting to someone else.',
     'Zumbi não lê etiqueta. Dê a Triagem para outro.'),
    ('refusal.headinajar.card.cannot.cat', 'Cats have one job. It is not this one.', 'Gato tem uma função. Não é esta.'),
    ('refusal.headinajar.card.cannot.hands', 'This employee has no hands. Assign it to Hauling.',
     'Este funcionário não tem mãos. Coloque-o no Transporte.'),
    ('refusal.headinajar.card.cannot', "A %s can't do %s. Give the card to someone else.", '%s não faz %s. Dê o cartão para outro.'),
    ('refusal.headinajar.card.cannot.evoker', 'Evokers do not do field work. A desk is on order.',
     'Evocadores não fazem trabalho de campo. A mesa já foi encomendada.'),
    ('refusal.headinajar.card.cannot.nitwit', "He's so nice. He does not do anything, but he's so nice.",
     'Ele é tão gente boa. Não faz nada, mas é tão gente boa.'),
    ('refusal.headinajar.card.water', 'This employee works in water. Move the stops to the shore.',
     'Este funcionário trabalha na água. Leve as paradas para a beira.'),
    ('refusal.headinajar.shift.no_hard_hat', 'Night Shift does not do days. Issue a Hard Hat.',
     'O Turno da Noite não trabalha de dia. Entregue um Capacete de Obra.'),
    # the Hard Hat
    ('refusal.headinajar.hard_hat.helmet', 'Policy 4.1: hard hats, not helmets.', 'Norma 4.1: capacete de obra, não elmo.'),
    ('refusal.headinajar.hard_hat.not_staff', 'Hard hats are issued to staff.', 'Capacetes de obra são para funcionários.'),
    ('message.headinajar.hard_hat.first', 'Health and Safety thanks you.', 'A Saúde e Segurança agradece.'),
    # the Whistle
    ('refusal.headinajar.whistle.full', 'Your detail is full: %s. Stand one down.', 'Sua equipe está cheia: %s. Dispense um.'),
    ('refusal.headinajar.whistle.jar', "A jar can't whistle. It has tried.", 'Pote não sabe assobiar. Já tentou.'),
    ('refusal.headinajar.whistle.not_staff', 'Only staff answer the whistle.', 'Só funcionário atende ao apito.'),
    ('message.headinajar.grille', 'The grille whistles for you.', 'A grade assobia por você.'),
    # Let go
    ('message.headinajar.let_go.confirm', 'Let %s go? Do it again to confirm.', 'Dispensar %s? Faça de novo para confirmar.'),
    ('message.headinajar.let_go.lanyard', 'The lanyard comes back. %s does not.', 'O crachá volta. %s, não.'),
    ('message.headinajar.let_go.hostile', '%s has left the company and may not take it well.',
     '%s deixou a empresa e pode não levar isso numa boa.'),
    ('message.headinajar.clipboard.bound', 'Clipboard bound to this site.', 'Prancheta vinculada a este local.'),
    # operators
    ('message.headinajar.staff.pause', 'All staff are on a mandatory break.', 'Todos os funcionários estão em pausa obrigatória.'),
    ('message.headinajar.staff.resume', 'Back to work.', 'De volta ao trabalho.'),
    ('command.headinajar.staff.list', 'No. %s %s (%s), %s, %s: %s', 'Nº %s %s (%s), %s, %s: %s'),
    ('command.headinajar.staff.cost', 'Staff thinking: %s ms a tick, %s paths a second.',
     'Funcionários pensando: %s ms por tick, %s caminhos por segundo.'),
    # DOLORES
    ('dolores.headinajar.escort', 'DOLORES: %s has been let go. Security will see them out.',
     'DOLORES: Dispensa de %s registrada. A Segurança acompanha até a saída.'),
    ('memo.headinajar.first_hire',
     'Welcome your new colleague. Staff need a Time Clock within 16 blocks to know where they work, and pay at dawn. '
     'Unpaid staff are unhappy staff. Ask any villager.',
     'Dê as boas-vindas ao novo colega. Funcionários precisam de um Relógio de Ponto a até 16 blocos para saber onde '
     'trabalham, e de salário ao amanhecer. Funcionário sem salário é funcionário infeliz. Pergunte a qualquer aldeão.'),
    ('memo.headinajar.first_clock', 'Site registered. Staff within 16 blocks of a clock work for it. Mind the payroll.',
     'Local registrado. Funcionários a até 16 blocos de um relógio trabalham para ele. Cuidado com a folha.'),
    ('memo.headinajar.detail_home',
     'Your detail has returned to the office. They were not told where you went. Neither was I.',
     'Sua equipe voltou ao escritório. Ninguém lhes disse aonde você foi. Nem a mim.'),
    # placards (the next shipped notices)
    ('placard.headinajar.13', 'HARD HATS MUST BE WORN BEYOND THIS POINT', 'CAPACETE OBRIGATÓRIO A PARTIR DAQUI'),
    ('placard.headinajar.14', 'NIGHT SHIFT ONLY AFTER DUSK', 'TURNO DA NOITE SÓ APÓS ESCURECER'),
    ('placard.headinajar.19', 'ALL STAFF MUST WEAR LANYARDS. THIS MEANS YOU, BESSIE', 'TODOS DE CRACHÁ. ATÉ VOCÊ, MIMOSA'),
    ('placard.headinajar.20', 'STAFF ARE NOT LUNCH', 'COLEGA NÃO É ALMOÇO'),
    # subtitles
    ('subtitles.headinajar.staff.punch_in', 'Staff punches in', 'Funcionário bate o ponto'),
    ('subtitles.headinajar.staff.clock_bell', 'Clock bell dings', 'Sino do relógio toca'),
    ('subtitles.headinajar.staff.card_printed', 'Job Card printed', 'Cartão de Tarefa impresso'),
    ('subtitles.headinajar.staff.whistle', 'Whistle blows', 'Apito sopra'),
    ('subtitles.headinajar.staff.whistle_twice', 'Whistle blows twice', 'Apito sopra duas vezes'),
    ('subtitles.headinajar.staff.lanyard_on', 'Lanyard fitted', 'Crachá colocado'),
    ('subtitles.headinajar.staff.lanyard_off', 'Lanyard comes off', 'Crachá retirado'),
]

# Floating stop lines over a worker for 5 s (staff.headinajar.say.<id>): wrapped at 200 px, two lines at most.
SAY = [
    ('no_card', 'Nothing on the rack for me.', 'Nada no quadro para mim.'),
    ('no_tool', 'Out of %s. Bring tools to the Pick-Up.', 'Acabou %s. Traga ferramentas para a Coleta.'),
    ('no_arrows', 'Out of arrows. Bring arrows to the Pick-Up.', 'Acabaram as flechas. Traga flechas para a Coleta.'),
    ('cant_get_there', "Can't get there. Moving on.", 'Não consigo chegar lá. Seguindo em frente.'),
    ('drop_off_full', 'Drop-off full. Waiting.', 'Entrega cheia. Esperando.'),
    ('cat_on_chest', 'A cat is on the chest. Hauling paused.', 'Tem um gato no baú. Transporte pausado.'),
    ('night_only', 'Night shift only. Or a hard hat.', 'Só turno da noite. Ou um capacete.'),
    ('do_you_mind', 'Do you mind?', 'Você se importa?'),
    ('unpaid', "I'll get to it when I'm paid.", 'Faço quando me pagarem.'),
    ('off_site', "Where am I? Where's my clock?", 'Onde estou? Cadê meu relógio?'),
    ('wheres_my_clock', "Where's my clock?", 'Cadê meu relógio?'),
    ('misfiled', 'Misfiled.', 'Arquivado no lugar errado.'),
    ('not_our_problem', 'Off site. Not our problem.', 'Fora do local. Não é problema nosso.'),
    ('not_security', '%s is not in Security. It will keep you company instead.', '%s não é da Segurança. Vai te fazer companhia.'),
    ('portal', "Waiting at the portal. They don't do travel.", 'Esperando no portal. Eles não viajam.'),
    ('on_the_clock', "I'm on the clock. Come back after my shift.", 'Estou no expediente. Volte depois do meu turno.'),
    ('out_of_rain', 'Out of the rain.', 'Saindo da chuva.'),
    ('pocket', 'Fine. It was resting in my pocket.', 'Tá. Estava descansando no meu bolso.'),
    ('awaiting', 'Awaiting assignment.', 'Aguardando designação.'),
    ('change_noted', "Change of circumstances noted in %s's file.", 'Mudança de circunstâncias anotada na ficha de %s.'),
    ('mandatory_break', 'All staff are on a mandatory break.', 'Todos os funcionários estão em pausa obrigatória.'),
    ('nitwit', "He's so nice.", 'Ele é tão gente boa.'),
]
SAY_PX = 200
# say lines with arguments: id -> (en args, pt args), the longest real ones
SAY_ARGS = {
    'no_tool': (['Shears'], ['Tesoura']),
    'not_security': (['Zombified Piglin'], ['Piglin-Zumbificado']),
    'change_noted': (['Bartholomew'], ['Bartolomeu']),
}

# Lines each part of the work adds: (key, en, pt). An action-bar line with %s gets an ACTION_BAR_ARGS row.
MORE_SITE = [
    ('refusal.headinajar.clock.recall_detail', '%s is out on a detail. Ask whoever holds the whistle.',
     '%s está em destacamento. Fale com quem tem o apito.'),
    ('refusal.headinajar.clock.not_here', '%s is not within earshot of the clock. HR has left a note.',
     '%s não está ao alcance do relógio. O RH deixou um recado.'),
    ('refusal.headinajar.clock.rack_cards_only', 'The rack takes Job Cards. Nothing else, however hard you push.',
     'O quadro só aceita Cartões de Tarefa. Nada mais, por mais que empurre.'),
    ('refusal.headinajar.clock.card_elsewhere', 'This card was printed for the site at %s. File it there.',
     'Este cartão é do local em %s. Arquive-o lá.'),
    ('message.headinajar.clock.recalled', '%s has been recalled to the clock.', '%s foi chamado de volta ao relógio.'),
    ('gui.headinajar.time_clock.takers', '%s on it', '%s nele'),
    ('gui.headinajar.time_clock.owed', 'Wages owed at dawn:', 'Salários devidos ao amanhecer:'),
    ('gui.headinajar.time_clock.nothing_owed', 'Nothing owed. Accounts find this suspicious.',
     'Nada a pagar. A Contabilidade acha isso suspeito.'),
    ('gui.headinajar.time_clock.nobody', 'Nobody on the roster. Staff within 16 blocks join by themselves.',
     'Ninguém no quadro. Funcionários a até 16 blocos se apresentam sozinhos.'),
    ('gui.headinajar.time_clock.no_job', 'No card', 'Sem cartão'),
    ('gui.headinajar.time_clock.no_rows', 'The roster is kept at Head Office. Your game does not receive it.',
     'O quadro fica na Matriz. Seu jogo não o recebe.'),
    # the Time Clock's screen
    ('gui.headinajar.time_clock.number', 'No. %s', 'Nº %s'),
    ('gui.headinajar.time_clock.job_line', 'Job: %s', 'Tarefa: %s'),
    ('gui.headinajar.time_clock.shift_line', 'Hours: %s', 'Turno: %s'),
    ('gui.headinajar.time_clock.morale_line', 'Morale: %s', 'Moral: %s'),
    ('gui.headinajar.time_clock.pick_first', 'Pick a name off the roster first. Any name will do.',
     'Escolha um nome do quadro primeiro. Qualquer um serve.'),
    ('gui.headinajar.time_clock.out_of_reach', 'Out of reach. Names are changed in person.',
     'Fora de alcance. Nome só se troca pessoalmente.'),
    ('gui.headinajar.time_clock.rack_hint', 'Job Cards for this site only. The rack is not a scrapbook.',
     'Só Cartões de Tarefa deste local. O quadro não é álbum de recortes.'),
]
MORE_MOVERS = []
MORE_JOBS = [  # jobs: the Clipboard, Job Cards, the four jobs
    # marking
    ('message.headinajar.clipboard.pick_up', 'Pick-Up marked. Staff will take from it.', 'Coleta marcada. A equipe vai tirar daqui.'),
    ('message.headinajar.clipboard.drop_off', 'Drop-Off marked. Staff will fill it.', 'Entrega marcada. A equipe vai encher.'),
    ('message.headinajar.clipboard.unmarked', 'Stop unmarked. It never happened.', 'Parada desmarcada. Isso nunca aconteceu.'),
    ('message.headinajar.clipboard.corner', 'First corner marked. Now the opposite corner.', 'Primeiro canto marcado. Agora o canto oposto.'),
    ('message.headinajar.clipboard.corner_cleared', 'Corner cleared.', 'Canto apagado.'),
    ('message.headinajar.clipboard.area', 'Area marked: %s by %s.', 'Área marcada: %s por %s.'),
    ('message.headinajar.clipboard.area_unmarked', 'Area unmarked.', 'Área desmarcada.'),
    ('message.headinajar.clipboard.printed', 'Job Card printed. One sheet of paper has been deducted.',
     'Cartão de Tarefa impresso. Uma folha de papel foi descontada.'),
    ('message.headinajar.clipboard.printed_free', 'Job Card printed. No paper was deducted. Accounts are looking into it.',
     'Cartão de Tarefa impresso. Nenhuma folha descontada. A Contabilidade apura.'),
    ('message.headinajar.clipboard.assigned', '%s is assigned to this site. Report to the clock.',
     '%s foi lotado neste local. Apresente-se ao relógio.'),
    ('message.headinajar.card.given', '%s has the card. It is in writing now.', '%s pegou o cartão. Agora está por escrito.'),
    ('refusal.headinajar.clipboard.closed', 'The site this clipboard was bound to has closed. Bind it to a Time Clock.',
     'O local desta prancheta foi fechado. Vincule-a a um Relógio de Ponto.'),
    ('refusal.headinajar.clipboard.full', 'This order is full: %s stops. Unmark one first.',
     'Este pedido está cheio: %s paradas. Desmarque uma antes.'),
    ('refusal.headinajar.clipboard.areas_full', 'This order has %s areas already. Unmark one first.',
     'Este pedido já tem %s áreas. Desmarque uma antes.'),
    ('refusal.headinajar.clipboard.not_staff', 'Only staff take orders. Hire it first with a Staff Lanyard.',
     'Só funcionário recebe ordens. Contrate antes com um Crachá.'),
    ('refusal.headinajar.clipboard.on_detail', '%s is on a detail. Stand it down with the Whistle first.',
     '%s está em destacamento. Dispense-o com o Apito antes.'),
    ('refusal.headinajar.clipboard.not_their_site', "This site is %s's. %s reports to %s.", 'Este local é de %s. %s responde a %s.'),
    ('refusal.headinajar.clipboard.already_here', '%s already works at this site.', '%s já trabalha neste local.'),
    ('refusal.headinajar.clipboard.needs_both', 'A haul needs a Pick-Up and a Drop-Off. Mark both.',
     'Transporte precisa de Coleta e Entrega. Marque as duas.'),
    ('refusal.headinajar.clipboard.one_pick_up', 'Sorting empties one Pick-Up. Unmark the others.',
     'A Triagem esvazia uma Coleta só. Desmarque as outras.'),
    ('refusal.headinajar.clipboard.no_field', 'Farming needs a field: use two corner blocks.',
     'Lavoura precisa de um campo: use dois blocos de canto.'),
    # cards
    ('refusal.headinajar.card.blank', 'This card is blank. Print one with a Clipboard.', 'Este cartão está em branco. Imprima um na Prancheta.'),
    ('refusal.headinajar.card.no_site', '%s has no site yet. Assign it with a Clipboard first.',
     '%s ainda não tem local. Lote-o com a Prancheta antes.'),
    ('refusal.headinajar.card.how', "Put the card in a Time Clock's Job Rack, or give it to one worker.",
     'Ponha no Quadro de Tarefas de um Relógio, ou entregue a um funcionário.'),
    # tooltips
    ('tooltip.headinajar.clipboard.unbound', 'Not bound to a site. Use it on a Time Clock.', 'Sem local. Use num Relógio de Ponto.'),
    ('tooltip.headinajar.job_card.blank', 'Blank. Print one with a Clipboard.', 'Em branco. Imprima um na Prancheta.'),
    ('tooltip.headinajar.job_card.job', 'Job: %s', 'Tarefa: %s'),
    ('tooltip.headinajar.job_card.stops', 'Pick-Ups %s, Drop-Offs %s, areas %s', 'Coletas %s, Entregas %s, áreas %s'),
    ('tooltip.headinajar.job_card.site', 'Site at %s', 'Local em %s'),
    ('tooltip.headinajar.job_card.printed', 'Printed by %s, day %s', 'Impresso por %s, dia %s'),
    # the order screen
    ('gui.headinajar.clipboard.title', 'Work Order', 'Ordem de Serviço'),
    ('gui.headinajar.clipboard.job', 'Job', 'Tarefa'),
    ('gui.headinajar.clipboard.stops', 'Stops', 'Paradas'),
    ('gui.headinajar.clipboard.areas', 'Areas', 'Áreas'),
    ('gui.headinajar.clipboard.name', 'Order name', 'Nome do pedido'),
    ('gui.headinajar.clipboard.filter', 'Filter: put an item here. Empty: anything.', 'Filtro: ponha um item aqui. Vazio: qualquer coisa.'),
    ('gui.headinajar.clipboard.everything_else', 'Everything else goes here', 'Todo o resto vem para cá'),
    ('gui.headinajar.clipboard.unmark', 'Unmark', 'Desmarcar'),
    ('gui.headinajar.clipboard.stop', '%s at %s', '%s em %s'),
    ('gui.headinajar.clipboard.stop_gone', 'Gone: the block is no longer there', 'Sumiu: o bloco não está mais lá'),
    ('gui.headinajar.clipboard.area', '%s by %s at %s', '%s por %s em %s'),
    ('gui.headinajar.clipboard.corner', 'One corner marked at %s', 'Um canto marcado em %s'),
    ('gui.headinajar.clipboard.nothing', 'Nothing marked yet. Use the clipboard on chests and corners.',
     'Nada marcado ainda. Use a prancheta em baús e cantos.'),
    ('gui.headinajar.clipboard.site', 'Site at %s', 'Local em %s'),
    ('gui.headinajar.clipboard.unbound', 'Not bound. Use the clipboard on a Time Clock.', 'Sem vínculo. Use a prancheta num Relógio de Ponto.'),
    ('gui.headinajar.clipboard.closed', 'This site has closed.', 'Este local foi fechado.'),
    ('gui.headinajar.clipboard.away', 'The site is out of reach. Its records are where you left them.',
     'O local está fora de alcance. A papelada está onde você deixou.'),
    ('gui.headinajar.clipboard.paper', 'Paper: %s', 'Papel: %s'),
    ('gui.headinajar.clipboard.job.hauling', 'Carries from Pick-Ups to Drop-Offs, by filter.', 'Leva das Coletas para as Entregas, por filtro.'),
    ('gui.headinajar.clipboard.job.sorting', 'Empties one Pick-Up into the Drop-Offs by filter.', 'Esvazia uma Coleta nas Entregas, por filtro.'),
    ('gui.headinajar.clipboard.job.farming', 'Harvests and replants a field, shears and milks a pen.', 'Colhe e replanta um campo, tosquia e ordenha.'),
    ('gui.headinajar.clipboard.job.security', 'Patrols an area or the whole site. Sees off hostiles.', 'Patrulha uma área ou o local. Expulsa hostis.'),
]
# the jobs' action-bar lines with arguments
ACTION_BAR_ARGS_JOBS = {
    'message.headinajar.clipboard.area': lambda i: [['16', '16']],
    'message.headinajar.clipboard.assigned': lambda i: [[NAME[i]]],
    'message.headinajar.card.given': lambda i: [[NAME[i]]],
    'refusal.headinajar.clipboard.full': lambda i: [['12']],
    'refusal.headinajar.clipboard.areas_full': lambda i: [['4']],
    'refusal.headinajar.clipboard.on_detail': lambda i: [[NAME[i]]],
    'refusal.headinajar.clipboard.not_their_site': lambda i: [['Supercalifragili', NAME[i], 'Supercalifragili']],
    'refusal.headinajar.clipboard.already_here': lambda i: [[NAME[i]]],
    'refusal.headinajar.card.no_site': lambda i: [[NAME[i]]],
}
MORE_WHISTLE = [  # the whistle part: the detail's lines
    ('message.headinajar.whistle.joined', '%s is seconded to your detail (%s of %s).', '%s foi destacado para você (%s de %s).'),
    ('message.headinajar.whistle.left', '%s is stood down. Back to the office.', '%s foi dispensado. De volta ao escritório.'),
    ('message.headinajar.whistle.follow', 'One blast: the detail follows.', 'Um apito: a equipe vem atrás.'),
    ('message.headinajar.whistle.hold', 'Two blasts: the detail holds.', 'Dois apitos: a equipe fica.'),
    ('message.headinajar.whistle.stand_down', 'Detail of %s stood down. They know the way. Mostly.',
     'Equipe de %s dispensada. Sabem voltar. Em tese.'),
    ('message.headinajar.whistle.sic', 'Security has been asked to see to %s.', 'A Segurança vai dar um jeito em %s.'),
    ('refusal.headinajar.whistle.empty', 'Nobody is on your detail. Whistle at your staff first.',
     'Ninguém na sua equipe. Apite para um funcionário antes.'),
    ('refusal.headinajar.whistle.other_detail', "%s is on %s's detail. One supervisor at a time.",
     '%s está na equipe de %s. Um supervisor por vez.'),
    ('refusal.headinajar.whistle.friendly', '%s is not a threat. Policy says so.', '%s não é ameaça. A norma diz que não.'),
    ('refusal.headinajar.whistle.nothing', 'Point at something. The detail does not do abstract.',
     'Aponte para alguma coisa. A equipe não trabalha no abstrato.'),
    ('refusal.headinajar.whistle.no_fighters', 'Nobody on this detail is in Security.', 'Ninguém nesta equipe é da Segurança.'),
    ('staff.headinajar.another_supervisor', 'another supervisor', 'outro supervisor'),
]
MORE_RULES = []
MORE_LOOK = [
    ('jade.headinajar.staff.job', 'Job: %s · %s', 'Função: %s · %s'),
    ('jade.headinajar.staff.status', 'Status: %s', 'Situação: %s'),
]
# stop lines each part adds (id, en, pt), kept under 200 px like SAY
MORE_SAY = [
    # jobs
    ('no_buckets', 'Out of buckets. Bring buckets to the Pick-Up.', 'Acabaram os baldes. Traga baldes para a Coleta.'),
    ('pick_up_empty', 'Pick-Up is empty. Standing by.', 'Coleta vazia. Aguardando.'),
    ('field_done', 'Field is done. Watching it grow.', 'Lavoura em dia. Vendo crescer.'),
]

ADVANCEMENTS = [  # (id, parent, icon, hidden, en title, en description, pt title, pt description)
    ('non_human_resources', 'root', 'headinajar:staff_lanyard', False, 'Non-Human Resources',
     'Hire your first member of staff with a Staff Lanyard', 'Recursos Não Humanos',
     'Contrate seu primeiro funcionário com um Crachá de Funcionário'),
    ('restructuring', 'non_human_resources', 'headinajar:separation_notice', False, 'Restructuring', 'Let a member of staff go',
     'Reestruturação', 'Dispense um funcionário'),
    ('clocking_in', 'non_human_resources', 'headinajar:time_clock', False, 'Clocking In', 'Have a worker punch in at a Time Clock',
     'Batendo o Ponto', 'Faça um funcionário bater o ponto num Relógio de Ponto'),
    ('in_writing', 'clocking_in', 'headinajar:job_card', False, 'In Writing', 'Give a worker a Job Card', 'Por Escrito',
     'Entregue um Cartão de Tarefa a um funcionário'),
    ('away_day', 'clocking_in', 'headinajar:supervisors_whistle', False, 'Away Day', 'Take a detail of six out of their site',
     'Passeio da Firma', 'Leve uma equipe de seis para fora do local'),
]

# Action-bar lines with arguments: key -> lambda lang_index: [argument lists] (the longest real ones).
NAME = ('Bartholomew', 'Bartolomeu')
ACTION_BAR_ARGS = {
    'refusal.headinajar.lanyard.poach': lambda i: [['Supercalifragili']],
    'refusal.headinajar.lanyard.headcount': lambda i: [['24']],
    'refusal.headinajar.lanyard.unknown': lambda i: [[('Zombified Piglin', 'Piglin-Zumbificado')[i]]],
    'message.headinajar.lanyard.hired': lambda i: [[NAME[i]]],
    'refusal.headinajar.staff.let_go_not_yours': lambda i: [[NAME[i], 'Supercalifragili']],
    'refusal.headinajar.staff.reports_to': lambda i: [[NAME[i], 'Supercalifragili']],
    'refusal.headinajar.staff.saw': lambda i: [[NAME[i]]],
    'refusal.headinajar.staff.already_let_go': lambda i: [[NAME[i]]],
    'refusal.headinajar.clock.untrusted': lambda i: [['Supercalifragili']],
    'refusal.headinajar.clock.full': lambda i: [['8']],
    'refusal.headinajar.clock.too_close': lambda i: [['64']],
    # site
    'refusal.headinajar.clock.recall_detail': lambda i: [[NAME[i]]],
    'refusal.headinajar.clock.not_here': lambda i: [[NAME[i]]],
    'refusal.headinajar.clock.card_elsewhere': lambda i: [['-29999999, 320, -29999999']],
    'message.headinajar.clock.recalled': lambda i: [[NAME[i]]],
    'refusal.headinajar.clipboard.outside': lambda i: [['16']],
    'refusal.headinajar.card.other_site': lambda i: [['-29999, 319, -29999']],
    'refusal.headinajar.card.cannot': lambda i: [[('Zombified Piglin', 'Piglin-Zumbificado')[i], ('Security', 'Segurança')[i]]],
    'refusal.headinajar.whistle.full': lambda i: [['6']],
    'message.headinajar.let_go.confirm': lambda i: [[NAME[i]]],
    'message.headinajar.let_go.lanyard': lambda i: [[NAME[i]]],
}
ACTION_BAR_ARGS.update(ACTION_BAR_ARGS_JOBS)  # jobs


# ---------------------------------------------------------------------------------------------------------------
def _staff_files(write):
    """gen_p38's staff files, each with its species' work object (a file with no row stays as it was)."""
    folder = os.path.join(RES, 'data', 'headinajar', 'headinajar', 'staff')
    have = {f[:-5] for f in os.listdir(folder) if f.endswith('.json')}
    missing = set(WORK) - have
    assert not missing, f'work rows for species with no staff file: {missing}'
    for species in sorted(have):
        path = f'data/headinajar/headinajar/staff/{species}.json'
        with open(os.path.join(RES, path), encoding='utf-8') as f:
            data = json.load(f)
        data.pop('work', None)
        if species in WORK:
            data['work'] = WORK[species]
        write(path, data)


def _recipes(write, shaped):
    shaped('staff_lanyard', ['SWS', ' P ', ' G '], dict(S='string', W='headinajar:neuro_copper_wire', P='paper', G='gold_nugget'), count=2)
    shaped('time_clock', ['ICI', 'WHW', ' I '], dict(I='headinajar:surgical_steel_ingot', C='clock', W='headinajar:neuro_copper_wire',
                                                     H='chest'))
    write('data/headinajar/recipe/clipboard.json', {
        'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': [' N ', 'PPP', ' S '],
        'key': {'N': {'item': MC + 'iron_nugget'}, 'P': {'item': MC + 'paper'}, 'S': {'tag': MC + 'wooden_slabs'}},
        'result': {'id': 'headinajar:clipboard', 'count': 1}})
    shaped('hard_hat', ['YIY', ' H '], dict(Y='yellow_dye', I='iron_ingot', H='leather_helmet'))
    write('data/headinajar/recipe/supervisors_whistle.json', {
        'type': 'minecraft:crafting_shapeless', 'category': 'equipment',
        'ingredients': [{'item': MC + 'iron_nugget'}, {'item': MC + 'copper_ingot'}, {'item': MC + 'string'}],
        'result': {'id': 'headinajar:supervisors_whistle', 'count': 1}})


ITEMS = ['staff_lanyard', 'clipboard', 'job_card', 'hard_hat', 'supervisors_whistle', 'separation_notice']


ART_ITEMS = (('clipboard', 'paint_clipboard'), ('job_card', 'paint_job_card'), ('whistle', 'paint_whistle'),
             ('separation_notice', 'paint_separation_notice'))


def _models(write):
    """Item models (the art's own, from its paint scripts' models(); a flat icon for the rest) and the Time Clock's
    block until the art's model is merged."""
    import importlib
    import sys
    done = set()
    for sub, mod in ART_ITEMS:
        sys.path.insert(0, os.path.join(ROOT, 'art', 'item', sub))
        for name, model in importlib.import_module(mod).models().items():
            write('assets/headinajar/models/item/' + name, model)
            done.add(name[:-5])
    for name in ITEMS:
        if name not in done:
            write(f'assets/headinajar/models/item/{name}.json', {'parent': 'minecraft:item/generated',
                                                                 'textures': {'layer0': f'headinajar:item/{name}'}})
    clock_geo = os.path.join(ROOT, 'art', 'machines', 'time_clock', 'time_clock_geo.py')
    if os.path.exists(clock_geo):
        # the art's clock: the body, the lamp's states and the rack's card slips (blockstate over facing, lamp, cards),
        # its GeckoLib geometry and clips (idle, punch, ding) for the renderer
        sys.path.insert(0, os.path.dirname(clock_geo))
        import time_clock_geo as TC
        for path, model in TC.java_models().items():
            write(path, model)
        write('assets/headinajar/blockstates/time_clock.json', TC.blockstate())
        geo, anim = TC.geo_and_animations()
        write('assets/headinajar/geo/block/time_clock.geo.json', geo)
        write('assets/headinajar/animations/block/time_clock.animation.json', anim)
    else:
        # a stand-in: a steel post block until the art's model is in
        write('assets/headinajar/models/block/time_clock.json', {
            'parent': 'minecraft:block/block', 'textures': {'all': 'headinajar:block/surgical_steel_block',
                                                            'particle': 'headinajar:block/surgical_steel_block'},
            'elements': [{'from': [5, 0, 5], 'to': [11, 10, 11], 'faces': {f: {'texture': '#all'} for f in
                                                                         ('north', 'south', 'east', 'west', 'up', 'down')}},
                         {'from': [3, 10, 6], 'to': [13, 16, 11], 'faces': {f: {'texture': '#all'} for f in
                                                                           ('north', 'south', 'east', 'west', 'up', 'down')}}]})
        write('assets/headinajar/blockstates/time_clock.json', {'variants': {
            f'facing={f}': {'model': 'headinajar:block/time_clock', **({'y': y} if y else {})}
            for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270))}})
        write('assets/headinajar/models/item/time_clock.json', {'parent': 'headinajar:block/time_clock'})


def _block(write):
    write('data/headinajar/loot_table/blocks/time_clock.json', {
        'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'headinajar:time_clock'}],
                                             'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    tag = 'data/minecraft/tags/block/mineable/pickaxe.json'
    values = json.load(open(os.path.join(RES, tag), encoding='utf-8'))['values']
    if 'headinajar:time_clock' not in values:
        write(tag, {'values': values + ['headinajar:time_clock']})


def _placard(EN, PT, write):
    """Notices 13, 14, 19 and 20 join the cycle: gen_p39's placard models and blockstate again, every notice drawn."""
    import gen_p39_dna as D
    for n in (13, 14, 19, 20):
        if n not in D.PLACARD_SHIPPED:
            D.PLACARD_SHIPPED = sorted(D.PLACARD_SHIPPED + [n])
    D._placards(EN, PT, write)


def _advancements(EN, PT, write):
    for adv, parent, icon, hidden, en_t, en_d, pt_t, pt_d in ADVANCEMENTS:
        write(f'data/headinajar/advancement/{adv}.json', {
            'parent': f'headinajar:{parent}', 'criteria': {'done': {'trigger': 'headinajar:event', 'conditions': {'event': adv}}},
            'display': {'icon': {'id': icon}, 'title': {'translate': f'advancements.headinajar.{adv}.title'},
                        'description': {'translate': f'advancements.headinajar.{adv}.description'}, 'frame': 'task',
                        'show_toast': True, 'announce_to_chat': not hidden, 'hidden': hidden}})
        for table, t, d in ((EN, en_t, en_d), (PT, pt_t, pt_d)):
            table[f'advancements.headinajar.{adv}.title'], table[f'advancements.headinajar.{adv}.description'] = t, d


# The sound events (files from tools/make_sounds.py work): event -> (file, subtitle key)
SOUNDS = {
    'time_clock.punch_in': 'time_clock/punch_in', 'time_clock.bell': 'time_clock/bell',
    'time_clock.card_printed': 'time_clock/card_printed', 'whistle.blast': 'whistle/blast',
    'whistle.two_short': 'whistle/two_short', 'lanyard.on': 'lanyard/on', 'lanyard.off': 'lanyard/off',
}
SUBTITLE = {'time_clock.punch_in': 'staff.punch_in', 'time_clock.bell': 'staff.clock_bell',
            'time_clock.card_printed': 'staff.card_printed', 'whistle.blast': 'staff.whistle',
            'whistle.two_short': 'staff.whistle_twice', 'lanyard.on': 'staff.lanyard_on', 'lanyard.off': 'staff.lanyard_off'}


def _sounds(write):
    path = os.path.join(RES, 'assets', 'headinajar', 'sounds.json')
    with open(path, encoding='utf-8') as f:
        table = json.load(f)
    for event, file in SOUNDS.items():
        assert os.path.exists(os.path.join(RES, 'assets', 'headinajar', 'sounds', file + '.ogg')), file
        table[event] = {'sounds': [{'name': f'headinajar:{file}', 'attenuation_distance': 16}],
                        'subtitle': f'subtitles.headinajar.{SUBTITLE[event]}'}
    write('assets/headinajar/sounds.json', table)


def _check_say(EN, PT):
    from gen_p27_clarity import wrap, fill
    over = []
    for sid, _en, _pt in SAY + MORE_SAY:
        key = f'staff.headinajar.say.{sid}'
        for i, table in enumerate((EN, PT)):
            args = SAY_ARGS.get(sid, ([], []))[i]
            line = fill(table[key], args) if args else table[key]
            if len(wrap(line, SAY_PX)) > 2:
                over.append((key, ('en', 'pt')[i], line))
    assert not over, f'{len(over)} stop lines take more than two lines of {SAY_PX} px: {over[:5]}'


def run(EN, PT, write, shaped):
    lines = LINES + MORE_SITE + MORE_MOVERS + MORE_JOBS + MORE_WHISTLE + MORE_RULES + MORE_LOOK
    lines += [(f'staff.headinajar.say.{sid}', en, pt) for sid, en, pt in SAY + MORE_SAY]
    keys = [k for k, _e, _p in lines]
    assert len(keys) == len(set(keys)), sorted(k for k in keys if keys.count(k) > 1)
    taken = [key for key in keys if key in EN]
    assert not taken, f'already in the language files: {taken}'
    for key, en, pt in lines:
        EN[key], PT[key] = en, pt
    _staff_files(write)
    _recipes(write, shaped)
    _models(write)
    _block(write)
    _placard(EN, PT, write)
    _advancements(EN, PT, write)
    _sounds(write)
    _look_models(write)  # the look part: the worn hats, the crate, stand-in fits
    _check_say(EN, PT)
    import gen_p39_dna as D
    saved = D.ACTION_BAR_ARGS
    D.ACTION_BAR_ARGS = ACTION_BAR_ARGS
    try:
        D._check_action_bar(EN, PT, keys)
    finally:
        D.ACTION_BAR_ARGS = saved
    _check_grille(EN, PT)
    _handbook(EN, PT)


# ---------------------------------------------------------------------------------------------------------------
# The rules part: the server rules (WorkRules), the Hard Hat, combat and the operator commands. Its lines join
# MORE_RULES and MORE_SAY here, so run() reads them with the rest.
RULES_LINES = [
    ('staff.headinajar.history.converted', 'Change of circumstances noted day %s.', 'Mudança de circunstâncias anotada no dia %s.'),
    ('refusal.headinajar.hard_hat.already', '%s already has a hard hat. Policy is one a head.',
     '%s já tem capacete. A norma é um por cabeça.'),
    ('staff.headinajar.status.unloaded', 'Out of reach (not loaded)', 'Fora de alcance (não carregado)'),
    ('command.headinajar.staff.header', '%s: %s staff on file.', '%s: %s na ficha.'),
    ('command.headinajar.staff.none', '%s has no staff on file.', '%s não tem funcionários na ficha.'),
    ('command.headinajar.staff.nobody', 'Nobody employs anyone. Head Office finds this restful.',
     'Ninguém emprega ninguém. A Matriz acha isso relaxante.'),
    ('command.headinajar.staff.recalled', '%s staff recalled to their clocks. They were not told why.',
     '%s funcionários chamados de volta aos relógios. Ninguém lhes disse por quê.'),
    ('command.headinajar.staff.status_paused', 'Staff: on a mandatory break (/headoffice staff pause).',
     'Funcionários: em pausa obrigatória (/headoffice staff pause).'),
]
RULES_SAY = [  # rules: stop lines (id, en, pt)
]
RULES_ACTION_BAR_ARGS = {
    'refusal.headinajar.hard_hat.already': lambda i: [[NAME[i]]],
}
RULES_SAY_ARGS = {
}


# ---------------------------------------------------------------------------------------------------------------
# The quirks (rules part, Quirks.java): the vanilla temperaments staff keep.
QUIRK_LINES = [
]
QUIRK_SAY = [  # quirks: stop lines (id, en, pt)
    ('pocket_empty', 'Nothing in my pocket. Searches need a form.', 'Bolso vazio. Revista só com formulário.'),
    ('spit', 'Consider this a formal grievance.', 'Reclamação formal, cuspida e escarrada.'),
    ('cat_sits', 'Stock under inspection. Do not open.', 'Estoque sob inspeção. Não abra.'),
    ('panda_lazy', 'On my break. It is in my contract.', 'No intervalo. Ninguém é de ferro, nem de bambu.'),
    ('panda_worried', 'Not in this weather. See Policy 9.3.', 'Com essa trovoada, eu não saio. Norma 9.3.'),
    ('team_building', 'Team building.', 'Integração da equipe. Na marra.'),
    ('surfacing', 'Back in one breath.', 'Já volto. Fui tomar um ar.'),
    ('occupational_health', 'Occupational health. Hold still.', 'Saúde ocupacional. Fique paradinho.'),
    ('poppy_offer', 'For you. With the compliments of Facilities.', 'Pra você, sua flor de pessoa. Cortesia da Manutenção.'),
]
QUIRK_ACTION_BAR_ARGS = {
}
QUIRK_SAY_ARGS = {
}


MORE_RULES.extend(RULES_LINES + QUIRK_LINES)
MORE_SAY.extend(RULES_SAY + QUIRK_SAY)
ACTION_BAR_ARGS.update({**RULES_ACTION_BAR_ARGS, **QUIRK_ACTION_BAR_ARGS})
SAY_ARGS.update({**RULES_SAY_ARGS, **QUIRK_SAY_ARGS})


# ---------------------------------------------------------------------------------------------------------------
# The whistle part: its action-bar lines' longest arguments, and a jar's lines (the grille's line before them) kept
# under the action bar's width too.
WHISTLE_ARGS = {
    'message.headinajar.whistle.joined': lambda i: [[NAME[i], '6', '6']],
    'message.headinajar.whistle.left': lambda i: [[NAME[i]]],
    'message.headinajar.whistle.stand_down': lambda i: [['16']],
    'message.headinajar.whistle.sic': lambda i: [[('Zombified Piglin', 'Piglin-Zumbificado')[i]]],
    'refusal.headinajar.whistle.other_detail': lambda i: [[NAME[i], 'Supercalifragili']],
    'refusal.headinajar.whistle.friendly': lambda i: [[('Zombified Piglin', 'Piglin-Zumbificado')[i]]],
}
ACTION_BAR_ARGS.update(WHISTLE_ARGS)
GRILLE_TOLD = ['message.headinajar.whistle.joined', 'message.headinajar.whistle.left', 'message.headinajar.whistle.follow',
               'message.headinajar.whistle.hold', 'message.headinajar.whistle.stand_down', 'message.headinajar.whistle.sic']


def _check_grille(EN, PT):
    from gen_p27_clarity import width, fill
    from gen_p39_dna import ACTION_BAR_PX
    over = []
    for key in GRILLE_TOLD:
        for i, table in enumerate((EN, PT)):
            for args in ACTION_BAR_ARGS.get(key, lambda i: [[]])(i):
                line = table['message.headinajar.grille'] + ' ' + (fill(table[key], args) if args else table[key])
                if width(line) >= ACTION_BAR_PX:
                    over.append((key, ('en', 'pt')[i], width(line), line))
    assert not over, f'{len(over)} grille lines over {ACTION_BAR_PX} px: {over[:5]}'


# --- the look part (what everyone sees of a worker) --------------------------------------------------------------
HAT_STANDIN = {'xs': (4, 2), 's': (6, 2), 'm': (8, 3), 'l': (10, 3), 'xl': (14, 4)}  # dome width, dome height (px)


def _box(frm, to, tex):
    return {'from': frm, 'to': to, 'faces': {f: {'texture': '#' + tex} for f in ('north', 'south', 'east', 'west', 'up', 'down')}}


def _ring(b0, b1, c0, c1, kind):
    """A 1 px cord ring hugging a rectangle 1 px out ('h': plane xz at y 0..1; 'v': plane xy at z 0..1)."""
    if kind == 'h':
        return [[b0 - 1, 0, c0 - 1, b1 + 1, 1, c0], [b0 - 1, 0, c1, b1 + 1, 1, c1 + 1], [b0 - 1, 0, c0, b0, 1, c1], [b1, 0, c0, b1 + 1, 1, c1]]
    return [[b0 - 1, c0 - 1, 0, b1 + 1, c0, 1], [b0 - 1, c1, 0, b1 + 1, c1 + 1, 1], [b0 - 1, c0, 0, b0, c1, 1], [b1, c0, 0, b1 + 1, c1, 1]]


def _fit(entity, model, bone, at, ring, pivot, drop, hat, crate):
    return {'format': 1, 'entity': 'minecraft:' + entity, 'model': model,
            'lanyard': {'bone': bone, 'at': at, 'rot': [0, 0, 0], 'cord': ring,
                        'hang': {'pivot': pivot, 'rot': [0, 0, 0], 'cord': [[-1, 0, -1, 1, drop, 0]] if drop else [],
                                 'badge': {'at': [0, max(0, drop - 1), -1.01], 'size': 'full'}, 'sway': 1}},
            'hat': hat, 'crate': crate}


def _look_standin_fits(write):
    """Simple fits for four species, until the art's fit table is in (each the art's format, in each bone's own px)."""
    body, head = {'field': 'body', 'path': ''}, {'field': 'head', 'path': ''}
    write('assets/headinajar/staff_fit/zombie.json', _fit(
        'zombie', 'ZombieModel', body, [0, 0, 0], _ring(-4, 4, -2, 2, 'h'), [0, 1, -2], 3, {'armor': True},
        {'bone': body, 'at': [0, 11, -9], 'rot': [0, 0, 0]}))
    write('assets/headinajar/staff_fit/cow.json', _fit(
        'cow', 'CowModel', head, [0, 0, -1], _ring(-4, 4, -4, 4, 'v'), [0, 5, 0], 1,
        {'bone': head, 'at': [0, -4, -3], 'rot': [0, 0, 0], 'size': 'l'}, {'bone': body, 'at': [0, -1, 3], 'rot': [-90, 0, 0]}))
    spider_head = {'field': 'root', 'path': 'head'}
    write('assets/headinajar/staff_fit/spider.json', _fit(
        'spider', 'SpiderModel', spider_head, [0, 0, -1], _ring(-4, 4, -4, 4, 'v'), [0, 5, 0], 1,
        {'bone': spider_head, 'at': [0, -4, -4], 'rot': [0, 0, 0], 'size': 'l'},
        {'bone': {'field': 'root', 'path': 'body1'}, 'at': [0, -4, 0], 'rot': [0, 0, 0]}))
    ghast = {'field': 'root', 'path': 'body'}
    write('assets/headinajar/staff_fit/ghast.json', _fit(
        'ghast', 'GhastModel', ghast, [0, 0, 0], _ring(-8, 8, -8, 8, 'h'), [0, 1, -9], 1,
        {'bone': ghast, 'at': [0, -8, 0], 'rot': [0, 0, 0], 'size': 'xl'}, {'bone': ghast, 'at': [0, 13, 0], 'rot': [0, 0, 0]}))


def _look_models(write):
    """The baked models StaffLayer draws: the art's five hats and the item hat (art/item/hard_hat/hard_hat_geo.py) and its
    crate (art/entity/crate/crate_geo.py) when they are in the tree; plain stand-ins (and four stand-in fits) until then.
    The art's own fit files (art/entity/lanyard/fit_table.py writes them) are never rewritten here."""
    import importlib
    import sys
    for sub, mod in (('item/hard_hat', 'hard_hat_geo'), ('entity/crate', 'crate_geo')):
        folder = os.path.join(ROOT, 'art', *sub.split('/'))
        if os.path.exists(os.path.join(folder, mod + '.py')):
            sys.path.insert(0, folder)
            for path, model in importlib.import_module(mod).java_models().items():
                write(path, model)
        elif mod == 'hard_hat_geo':
            for size, (dw, dh) in HAT_STANDIN.items():
                h = dw / 2
                write(f'assets/headinajar/models/entity/hard_hat_{size}.json', {
                    'textures': {'hat': 'minecraft:block/yellow_concrete', 'particle': 'minecraft:block/yellow_concrete'},
                    'elements': [_box([8 - h - 1, 0, 8 - h - 2], [8 + h + 1, 1, 8 + h + 1], 'hat'),
                                 _box([8 - h, 1, 8 - h], [8 + h, 1 + dh, 8 + h], 'hat')]})
        else:
            write('assets/headinajar/models/entity/staff_crate.json', {
                'textures': {'board': 'minecraft:block/oak_planks', 'particle': 'minecraft:block/oak_planks'},
                'elements': [_box([5, 0, 5], [11, 5, 11], 'board')]})
    if not os.path.exists(os.path.join(ROOT, 'art', 'entity', 'lanyard', 'fit_table.py')):
        _look_standin_fits(write)


# ---------------------------------------------------------------------------------------------------------------
# The handbook: "The workforce", six entries at the end of Temporary Staffing (the memos open three of them).
WORKFORCE_ENTRIES = [
    {'cat': 'staffing', 'id': 'workforce', 'sort': 12, 'icon': 'headinajar:staff_lanyard',
     'en': 'Non-Human Resources', 'pt': 'Recursos Não Humanos',
     'pages': [
         ('spotlight', 'headinajar:staff_lanyard',
          'Use a Staff Lanyard on a living creature to hire it. It stops, looks at you and stands $(l)awaiting '
          'assignment$() until a Time Clock of yours within 16 blocks takes it.',
          'Use um Crachá de Funcionário numa criatura viva para contratá-la. Ela para, olha para você e fica '
          '$(l)aguardando designação$() até um Relógio de Ponto seu a 16 blocos a receber.'),
         ('text',
          'Its own mind is off: it never despawns, attacks nobody and works for you with its temperament kept as a '
          'quirk.$(br2)Hostiles first need wearing down to half their health. Babies, bosses, hosts, creepers, fish, '
          'bats, vexes and shulkers are not hired.',
          'A mente dela fica desligada: não some, não ataca ninguém e trabalha para você com o temperamento mantido '
          'como mania.$(br2)Hostis precisam antes cair a metade da vida. Filhotes, chefões, hospedeiros, creepers, '
          'peixes, morcegos, vexes e shulkers não são contratados.'),
         ('text',
          "Someone else's pet or staff, and a village's iron golem, are not yours to poach. A golem you built is."
          '$(br2)A player employs 24 at most; a clock takes 8.',
          'Bicho ou funcionário dos outros, e o golem de ferro da vila, não são seus para roubar. O golem que você '
          'construiu é.$(br2)Cada jogador emprega até 24; cada relógio recebe 8.'),
         ('text',
          '$(l)Let go$(): $(k:key.sneak) and use a Clipboard on it, twice within 3 seconds. The lanyard comes back with a '
          "Form 45-S (and the Hard Hat). It serves 10 seconds' notice, head down, then it is a creature again.",
          '$(l)Dispensa$(): $(k:key.sneak) e use uma Prancheta nele, duas vezes em 3 segundos. O crachá volta com um '
          'Formulário 45-S (e o Capacete). Ele cumpre 10 segundos de aviso, cabeça baixa, e volta a ser bicho.'),
         ('text',
          'A Security colleague sees it out. A hostile is hostile again once the notice is up.$(br2)Turret '
          'Pedestals, iron golems and wolves spare staff, unless staff start it.',
          'Um colega da Segurança acompanha até a saída. O hostil volta a ser hostil quando o aviso acaba.$(br2)'
          'Pedestais de Torreta, golens de ferro e lobos poupam funcionários, a menos que eles comecem.'),
     ],
     'note': ('Wear your badge at all times. It is how we know you are staff.',
              'Use o crachá o tempo todo. É assim que sabemos que você é do quadro.')},

    {'cat': 'staffing', 'id': 'time_clock', 'sort': 13, 'icon': 'headinajar:time_clock',
     'en': 'The Time Clock', 'pt': 'O Relógio de Ponto',
     'pages': [
         ('spotlight', 'headinajar:time_clock',
          'A Time Clock makes a $(l)site$(): 16 blocks out on the level, 8 up and 8 down. Clocks need 8 blocks between '
          'them. Use it for the Roster, the Job Rack, the Pay Drawer and the Shifts.',
          'Um Relógio de Ponto cria um $(l)local$(): 16 blocos para os lados, 8 para cima e 8 para baixo. Relógios '
          'precisam de 8 blocos entre si. Use-o para o Quadro de Pessoal, de Tarefas, a Gaveta e os Turnos.'),
         ('text',
          'At each shift staff walk up, punch in one at a time, take a card and go. Off shift they stand in a line by '
          'the clock, facing it. The late get the bell.',
          'A cada turno a equipe chega, bate o ponto um de cada vez, pega um cartão e vai. Fora do turno fica em fila '
          'junto ao relógio, de frente para ele. Quem atrasa ganha o sino.'),
         ('form', ('Form 11-T: Shifts', 'Formulário 11-T: Turnos'),
          'Day: 1,000 to 11,000$(br)Night: 13,000 to 23,000$(br)Redstone: while the clock is powered$(br2)Times are the '
          "Overworld's everywhere. Undead start on Night; Day needs a Hard Hat.",
          'Dia: 1.000 a 11.000$(br)Noite: 13.000 a 23.000$(br)Redstone: enquanto o relógio tiver sinal$(br2)As horas são '
          'as da Superfície em todo lugar. Mortos-vivos começam na Noite; Dia exige Capacete.'),
         ('text',
          'At dawn each worker takes its wage from the Pay Drawer, as a plinth head does from its chest. A hopper may '
          'fill the drawer; nothing takes from it but staff.$(br2)Untrusted players are refused at the clock.',
          'Ao amanhecer cada funcionário tira o salário da Gaveta de Pagamento, como a cabeça no pedestal tira do '
          'baú. Um funil pode encher a gaveta; só a equipe tira dela.$(br2)Quem não tem confiança é barrado.'),
         ('text',
          'Undead within 2 blocks of their own clock do not burn: the clock has an awning.$(br2)A broken clock drops '
          'its drawer and rack; its staff wait for another clock within 16 blocks.',
          'Mortos-vivos a até 2 blocos do próprio relógio não queimam: o relógio tem toldo.$(br2)Relógio quebrado '
          'derruba gaveta e quadro; a equipe espera outro relógio a 16 blocos.'),
     ],
     'note': ('Punch in. Punch out. Punch nothing else.', 'Bata o ponto. Bata a saída. Não bata em mais nada.')},

    {'cat': 'staffing', 'id': 'job_cards', 'sort': 14, 'icon': 'headinajar:clipboard',
     'en': 'Clipboards and Job Cards', 'pt': 'Pranchetas e Cartões',
     'pages': [
         ('spotlight', 'headinajar:clipboard',
          'Use a Clipboard on your clock to bind it. Use a container in the site: a $(l)Pick-Up$(). $(k:key.sneak) and '
          'use one: a $(l)Drop-Off$(). Use two other blocks: the corners of an area.',
          'Use a Prancheta no seu relógio para vinculá-la. Use um recipiente no local: uma $(l)Coleta$(). '
          '$(k:key.sneak) e use um: uma $(l)Entrega$(). Use dois outros blocos: os cantos de uma área.'),
         ('text',
          'Use it in the air: choose the job, a filter for each Drop-Off, a name, and $(l)Print$(): one paper becomes a '
          'Job Card. Put cards in the Job Rack, or give one to a worker by hand.',
          'Use no ar: escolha a tarefa, um filtro por Entrega, um nome e $(l)Imprimir$(): uma folha vira Cartão de '
          'Tarefa. Ponha os cartões no Quadro de Tarefas, ou entregue um na mão.'),
         ('text',
          'Idle staff take the first card they can do with the fewest workers on it: one card, eight workers; eight '
          'cards, one each.$(br2)Fields and pens are at most 16 by 16.',
          'Quem está parado pega o primeiro cartão que sabe fazer com menos gente nele: um cartão, oito; oito '
          'cartões, um para cada.$(br2)Campos e cercados têm no máximo 16 por 16.'),
         ('form', ('Form 4-J: Jobs', 'Formulário 4-J: Tarefas'),
          '$(l)Hauling$(): Pick-Up to Drop-Off$(br)$(l)Sorting$(): one Pick-Up, by filter$(br)$(l)Farming$(): ripe crops, '
          'replanted; sheep and cows in a pen$(br)$(l)Security$(): patrols, sees hostiles off the site',
          '$(l)Transporte$(): da Coleta à Entrega$(br)$(l)Triagem$(): uma Coleta, por filtro$(br)$(l)Lavoura$(): '
          'colheita madura, replantada; ovelhas e vacas no cercado$(br)$(l)Segurança$(): ronda e expulsa hostis'),
         ('text',
          'Each kind has its aptitudes: zombies cannot read the labels, cats have one job, fliers fly. Disengaged '
          'staff misfile one item in twenty. Nobody tramples, breaks a block or leaves the site.',
          'Cada espécie tem aptidões: zumbi não lê etiqueta, gato tem uma função, quem voa voa. Funcionário '
          'desmotivado arquiva errado um item em vinte. Ninguém pisoteia, quebra bloco ou sai do local.'),
     ],
     'note': ('A Job Card is a promise. Staff keep promises. Mostly.',
              'Cartão de Tarefa é promessa. A equipe cumpre promessas. Quase sempre.')},

    {'cat': 'staffing', 'id': 'hard_hat', 'sort': 15, 'icon': 'headinajar:hard_hat',
     'en': 'The Hard Hat', 'pt': 'O Capacete de Obra',
     'pages': [
         ('spotlight', 'headinajar:hard_hat',
          'Use it on a worker to put it on. Undead staff in one may work the Day shift and do not burn. Falling blocks '
          'do staff no harm. It never wears out.',
          'Use num funcionário para colocar. Mortos-vivos com ele podem fazer o turno do Dia e não queimam. Blocos '
          'caindo não ferem a equipe. Nunca se desgasta.'),
         ('text',
          "Only a Hard Hat counts: Policy 4.1, hard hats, not helmets.$(br2)You may wear one too: a leather helmet's "
          'armour, and half the harm from falling blocks. It fits a Hat Mount.',
          'Só Capacete de Obra conta: Norma 4.1, capacete de obra, não elmo.$(br2)Você também pode usar: a armadura '
          'de um capacete de couro, e metade do dano de blocos caindo. Cabe no Suporte de Chapéu.'),
     ],
     'note': ('Health and Safety thanks you.', 'A Saúde e Segurança agradece.')},

    {'cat': 'staffing', 'id': 'whistle', 'sort': 16, 'icon': 'headinajar:supervisors_whistle',
     'en': "The Supervisor's Whistle", 'pt': 'O Apito do Supervisor',
     'pages': [
         ('spotlight', 'headinajar:supervisors_whistle',
          'Use it on your staff to put them on your $(l)detail$() (six at most). Use it in the air: Follow or Hold. '
          '$(k:key.sneak) and use it: they are stood down and go home.',
          'Use na sua equipe para pô-los no seu $(l)destacamento$() (seis no máximo). Use no ar: Seguir ou Parar. '
          '$(k:key.sneak) e use: são dispensados e voltam para casa.'),
         ('text',
          'Attack with it in hand: the fighters go for what you point at. Cats, wolves, parrots and foxes are pets: '
          'they keep you company instead.$(br2)A detail never follows you through a portal.',
          'Ataque com ele na mão: quem luta vai no que você aponta. Gatos, lobos, papagaios e raposas são '
          'mascotes: fazem companhia.$(br2)O destacamento nunca segue você por um portal.'),
         ('text',
          'Left far behind, or five minutes away from you, they take the company shuttle back to their clock. Nothing '
          'is loaded to fetch them.$(br2)A jar needs a Speaker Grille to whistle.',
          'Deixados longe, ou cinco minutos sem você, voltam ao relógio no transporte da firma. Nada é carregado '
          'para buscá-los.$(br2)O pote precisa de uma Grade de Alto-Falante para apitar.'),
     ],
     'note': ('An away day is still a working day.', 'Passeio da firma ainda é dia útil.')},

    {'cat': 'staffing', 'id': 'workforce_rules', 'sort': 17, 'icon': 'minecraft:clock',
     'en': 'Server Rules', 'pt': 'Regras do Servidor',
     'pages': [
         ('text',
          'Staff think twice a second, each in turn, and never load a chunk: an unloaded site stops like a furnace. '
          'Paths stay inside the site.$(br2)Operators have $(l)/headoffice staff list, recall$() and $(l)pause$().',
          'A equipe pensa duas vezes por segundo, cada um na sua vez, e nunca carrega chunk: local descarregado para '
          'como fornalha. Caminhos ficam no local.$(br2)Operadores têm $(l)/headoffice staff list, recall$() e '
          '$(l)pause$().'),
     ]},
]
JEI_WORKFORCE = {
    'headinajar:staff_lanyard': ('Use it on a living creature to hire it. Hostiles at half their health or less.',
                                 'Use numa criatura viva para contratá-la. Hostis com metade da vida ou menos.'),
    'headinajar:time_clock': ('Makes a site 16 blocks out: staff punch in, take Job Cards and are paid at dawn from its drawer.',
                              'Cria um local de 16 blocos: a equipe bate o ponto, pega Cartões e recebe ao amanhecer da gaveta.'),
    'headinajar:clipboard': ('Binds to a Time Clock, marks Pick-Ups, Drop-Offs and areas, and prints Job Cards on paper.',
                             'Vincula a um Relógio de Ponto, marca Coletas, Entregas e áreas, e imprime Cartões em papel.'),
}


def _handbook(EN, PT):
    import gen_p27_handbook_b as hb
    import gen_p39_dna as D
    have = {(e['cat'], e['id']) for e in hb.ENTRIES}
    for e in WORKFORCE_ENTRIES:
        if (e['cat'], e['id']) not in have:
            hb.ENTRIES.append(e)
    hb.JEI_INFO.update(JEI_WORKFORCE)
    hb.JEI_INFO['headinajar:safety_placard'] = ('Hangs on a wall. Use it to change the notice: there are nineteen.',
                                               'Fica pendurada na parede. Use para trocar o aviso: são dezenove.')
    # six more entries: the written edition's cap goes up (gen_p39's function, re-made with the new number)
    D.WRITTEN_PAGES = 220
    D._raise_written_cap()

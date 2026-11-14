"""Lines for the second round of repairs, the ones a new player's playthrough found. Imported by gen_data.py.

Refusal lines follow gen_p9.py's rule: what is wrong, then what to do next; a control is never named by its key (a %s
filled with the player's own binding instead); %s arguments are names filled in by the code.
"""

# id (after 'refusal.headinajar.'): (en, pt)
REFUSALS = {
    # the jar menu key pressed by a whole body
    'body.jar_key': ('Your head is still on. The jar menu is for a head in a jar.',
                     'Sua cabeça ainda está no lugar. O menu do pote é para uma cabeça no pote.'),
    # a Cradle that docks a head with nothing to keep it topped up (said on docking and on a click): %1$s the Galvanic
    # Pile, %2$s Cranial Saline
    'cradle.unpowered': ('This Cradle has no power. Put a %1$s beside it.',
                         'Este Berço está sem energia. Coloque uma %1$s ao lado.'),
    'cradle.dry': ('This Cradle has no saline to give. Fill it with %2$s.', 'Este Berço não tem soro para dar. Encha com %2$s.'),
    'cradle.starved': ('No power, no saline. Put a %1$s beside it; fill with %2$s.',
                       'Sem energia nem soro: %1$s ao lado e %2$s dentro.'),
}

# (key, en, pt)
LANG = [
    # the Charging Dock says what it is doing: a jar standing on it (%s is the charge, in percent)
    ('message.headinajar.dock_charging', 'Charging Dock: charging, %s%%.', 'Base de Recarga: carregando, %s%%.'),
    ('message.headinajar.dock_full', 'Charging Dock: your jar is fully charged.', 'Base de Recarga: seu pote está com carga completa.'),
    # ... and the owner of a head set down on it, out walking their body (%s the charge in percent, or the Galvanic Pile)
    ('message.headinajar.dock_head_charging', 'Your head is charging on the Charging Dock: %s%%.',
     'Sua cabeça está carregando na Base de Recarga: %s%%.'),
    ('message.headinajar.dock_head_full', 'Your head is fully charged on the Charging Dock.',
     'Sua cabeça está com carga completa na Base de Recarga.'),
    ('message.headinajar.dock_head_empty', 'The Charging Dock under your head is empty. Feed it from a %s.',
     'A Base de Recarga sob sua cabeça está vazia. Alimente com uma %s.'),
    # a Rover (or any shell) driven into a wall at speed: one knock, not a grind (%s the shell)
    ('message.headinajar.shell_crash', 'Crash! %s met a wall. The jar took one knock.',
     'Batida! %s encontrou uma parede. O pote levou um tranco.'),
    # getting into one of the mod's bodies, in place of vanilla's dismount hint (%s are the player's own keys)
    ('hud.headinajar.board.mecha', 'Hold %1$s and %2$s to climb out', 'Segure %1$s e %2$s para sair'),
    ('hud.headinajar.board.host', 'Press %s to get down', 'Aperte %s para descer'),
    # a click the weak link dropped: on the link bar at once; the first few times, why (%1$s link quality in percent,
    # %2$s the Head Satchel, %3$s the Relay Mast)
    ('hud.headinajar.packet_lost', 'CLICK LOST', 'CLIQUE PERDIDO'),
    ('hud.headinajar.packet_loss.why', 'Link at %1$s%%: some clicks get lost. Get closer to your head, or build a %3$s.',
     'Link em %1$s%%: alguns cliques se perdem. Chegue perto da cabeça ou construa um %3$s.'),
    # the Galvanic Pile: how much went in (%1$s count, %2$s item, %3$s its FE, %4$s FE still to burn in all)
    ('message.headinajar.pile_fed', '%1$s %2$s in: %3$s FE. %4$s FE to burn in all.',
     '%1$s %2$s dentro: %3$s FE. %4$s FE para queimar no total.'),
    # its status: %1$s/%2$s the buffer (empty while it burns: it passes power straight on), %3$s fuel, %4$s FE to come
    ('message.headinajar.pile_status_burning', 'Galvanic Pile: %1$s/%2$s FE, %3$s fuel, %4$s FE to come. It feeds what it touches.',
     'Pilha Galvânica: %1$s/%2$s FE, %3$s combustível, %4$s FE por vir. Alimenta o que ela toca.'),
    # %3$s rotten flesh, %4$s spider eye
    ('message.headinajar.pile_status_empty', 'Galvanic Pile: %1$s/%2$s FE, no fuel. Feed it %3$s, %4$s or raw meat.',
     'Pilha Galvânica: %1$s/%2$s FE, sem combustível. Alimente com %3$s, %4$s ou carne crua.'),
    # tooltips: the facts a player needs while holding the block
    ('tooltip.headinajar.pile.what', 'Burns meat into power for the machines it touches.',
     'Queima carne e dá energia às máquinas que ela toca.'),
    # %1$s rotten flesh %2$s FE, %3$s spider eye %4$s FE, raw meat %5$s FE
    ('tooltip.headinajar.pile.fuels', '%1$s %2$s FE, %3$s %4$s FE, raw meat or fish %5$s FE each',
     '%1$s %2$s FE, %3$s %4$s FE, carne ou peixe cru %5$s FE cada'),
    # %1$s the Separation Chair, %2$s its FE, %3$s how many of %4$s rotten flesh
    ('tooltip.headinajar.pile.operation', 'One %1$s operation takes %2$s FE: %3$s %4$s.',
     'Uma operação na %1$s gasta %2$s FE: %3$s %4$s.'),
    ('tooltip.headinajar.gantry.pad', 'The mecha is built standing on top of the Gantry: the Gantry is the pad.',
     'O mecha é montado em cima do pórtico: o próprio pórtico é a plataforma.'),
    ('tooltip.headinajar.gantry.cost', 'Assemble takes %s FE from the power beside it.',
     'Montar gasta %s FE da energia ao lado dele.'),
    # %s the Head Satchel
    ('tooltip.headinajar.dock.how', 'Charges a jar standing on it, or your head set down on it from a %s.',
     'Carrega um pote em cima dela, ou sua cabeça posta nela pela %s.'),
    # %s Cranial Saline
    ('tooltip.headinajar.cradle.how', 'Keeps a docked head topped up with charge and %s.',
     'Mantém a cabeça no berço abastecida de carga e %s.'),
    # %s the Galvanic Pile
    ('tooltip.headinajar.fed_by', 'Needs power: put a %s beside it.', 'Precisa de energia: coloque uma %s ao lado.'),
]


def run(EN, PT, write, shaped):
    for key, (en, pt) in REFUSALS.items():
        EN[f'refusal.headinajar.{key}'], PT[f'refusal.headinajar.{key}'] = en, pt
    for key, en, pt in LANG:
        EN[key], PT[key] = en, pt

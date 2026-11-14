"""Machines, genetics and heads at work: their refusal lines, pause lines and socket lines, and the tags that keep
carry mods off the mod's blocks and creatures. Imported by gen_data.py.

Refusal lines follow gen_p9: what is wrong, then what to do next; arguments (%s) are item, block and creature names
filled in by the code, so each language uses its own name.
"""

# id (after 'refusal.headinajar.'): (en, pt)
REFUSALS = {
    # the Surgical Wrench and Tesla Nodes. %s: Tesla Node, or the range in blocks
    'wrench.not_a_node': ('That is not a %s. Link a node to another node.', 'Isso não é um %s. Ligue um nó a outro nó.'),
    'wrench.not_linked': ("This node sends nowhere. Unlink it at the sending node.", 'Este nó não envia para lugar nenhum. Desligue no nó que envia.'),
    'wrench.other_dimension': ("Nodes can't link across dimensions. Start again here.", 'Nós não se ligam entre dimensões. Comece de novo aqui.'),
    'wrench.same_node': ("A node can't send to itself. Use a second node.", 'Um nó não envia para si mesmo. Use um segundo nó.'),
    'wrench.sender_gone': ('The sending node is gone. Start again from a node.', 'O nó que enviava sumiu. Comece de novo por um nó.'),
    'wrench.too_far': ('Too far: nodes reach %s blocks. Put one closer.', 'Longe demais: os nós alcançam %s blocos. Ponha um mais perto.'),
    'wrench.no_use': ('The wrench only links each %s. Nothing to adjust here.', 'A chave só liga cada %s. Nada a ajustar aqui.'),
    # genetics by hand. %s: the Injection Rig, the Surgical Saw
    'gene.syringe_air': ('Nothing to draw from the air. Use it on a creature.', 'Não há o que tirar do ar. Use numa criatura.'),
    'gene.syringe_player': ('Colleagues are not samples. Draw from a creature.', 'Colegas não são amostras. Tire de uma criatura.'),
    'gene.serum_target': ('Serums go in hosts. Use the %1$s, or make one: %2$s.',
                          'Soro é para hospedeiros. Use a %1$s ou crie um: %2$s.'),
    'gene.serum_self': ('No self-injection by hand. Use the %s.', 'Nada de se injetar à mão. Use a %s.'),
    'gene.wash_jar': ('A jar has nothing to wash. Drink it with your body on.', 'Um pote não tem o que lavar. Beba com o corpo no lugar.'),
    # the genetics lab. %s: item and block names, a gene's name, FE
    'lab.no_power': ('Needs %s FE. Feed it from a %s.', 'Precisa de %s FE. Alimente com uma %s.'),
    'lab.sequencer.empty': ('Nothing to read. Put a %s in the slot.', 'Nada para ler. Coloque uma %s no espaço.'),
    'lab.sequencer.unreadable': ("This creature isn't in this world. Take the fraction out.", 'Essa criatura não existe neste mundo. Tire a fração.'),
    'lab.splicer.running': ('Splicing in progress. Wait for the cassette.', 'Emenda em andamento. Espere o cartucho.'),
    'lab.splicer.unread': ("%s isn't in your library. Read it at a DNA Sequencer.", '%s não está na sua biblioteca. Leia num Sequenciador de DNA.'),
    'lab.splicer.duplicate': ('%s is already in the strand. Pick another gene.', '%s já está na fita. Escolha outro gene.'),
    'lab.splicer.empty_library': ('No gene read to 30%% yet. Read more at a %s.', 'Nenhum gene lido até 30%% ainda. Leia mais num %s.'),
    'lab.splicer.no_genes': ('No genes picked. Pick at least one with the arrows.', 'Nenhum gene escolhido. Escolha pelo menos um com as setas.'),
    'lab.splicer.too_heavy': ('Load %s of %s. Drop a gene from the strand.', 'Peso %s de %s. Tire um gene da fita.'),
    'lab.splicer.no_gel': ('Out of %s. Load some in the second slot.', 'Sem %s. Coloque no segundo espaço.'),
    'lab.splicer.no_bottle': ('Needs a %s. Load one in the third slot.', 'Precisa de %s. Coloque no terceiro espaço.'),
    'lab.splicer.out_full': ('The last %s is still in. Take it out first.', 'O último %s ainda está lá. Tire primeiro.'),
    'lab.rig.needs_body': ('The rig injects bodies. Put your head back on, or link.', 'A estação injeta corpos. Volte a cabeça ao corpo, ou conecte.'),
    'lab.rig.empty': ('Nothing to inject. Put a %s in the slot.', 'Nada para injetar. Coloque um %s no espaço.'),
    'lab.rig.full': ('No room for another serum. A Gene Wash clears the body.', 'Não cabe outro soro. Uma Lavagem Genética limpa o corpo.'),
    'lab.rig.too_heavy': ('Too heavy for this body. Splice a lighter serum.', 'Pesado demais para este corpo. Emende um soro mais leve.'),
    # the Body Printer. %s: seconds left; a count and a material; mB and the saline
    'printer.busy': ('Printing already, %s s to go. Wait for the body.', 'Já está imprimindo, faltam %s s. Espere o corpo.'),
    'printer.has_head': ('Bodies are for heads in jars. Come back decapitated.', 'Corpos são para cabeças em potes. Volte decapitado.'),
    'printer.missing': ('Short %s %s. Load it and press Print again.', 'Faltam %s de %s. Coloque e aperte Imprimir de novo.'),
    'printer.no_saline': ('Short %s mB of %s. Pour in buckets or bottles.', 'Faltam %s mB de %s. Despeje baldes ou frascos.'),
    # tanks. %1$s: the fluid, %2$s: the machine
    'machine.wrong_fluid': ("The %2$s doesn't take %1$s. Check what it runs on.", '%2$s não aceita %1$s. Veja do que precisa.'),
    'machine.tank_full': ('The tank is full of %1$s. Use some first.', 'O tanque está cheio de %1$s. Gaste um pouco primeiro.'),
    'pile.full': ('The pile is full of %s. Let it burn some first.', 'A pilha está cheia de %s. Deixe queimar um pouco.'),
    # heads at work. %s: a creature, an item, a player
    'turret.needs_head': ('A turret needs a brain. Fit a %s.', 'Uma torreta precisa de cérebro. Encaixe uma %s.'),
    'turret.no_job': ('That head (%s) has nothing to shoot with. Try a skeleton.', 'Essa cabeça (%s) não tem com que atirar. Tente um esqueleto.'),
    'turret.occupied': ('This turret has its head (%s). Take it out by hand first.', 'Esta torreta já tem cabeça (%s). Tire à mão primeiro.'),
    'mount.needs_villager': ('Staff positions are for a jarred %s only.', 'Vagas de funcionário só para %s no pote.'),
    'socket.occupied': ('%s is plugged in. Wait until they unplug.', '%s está conectado. Espere desconectar.'),
    'socket.has_head': ('A head (%s) runs this socket. Take it out by hand first.', 'Uma cabeça (%s) roda este soquete. Tire à mão primeiro.'),
    'socket.needs_head': ('The socket takes a %s, or a jar plugging in.', 'O soquete aceita uma %s, ou um pote se conectando.'),
    'socket.no_program': ('That head (%s) has no program. Try zombie, villager, enderman.',
                          'Cabeça (%s) sem programa. Tente zumbi, aldeão ou enderman.'),
    'jarred_head.busy': ('This head is with another customer. Come back shortly.', 'Esta cabeça está com outro cliente. Volte já já.'),
}

LANG = [
    # a jar leaves a Neural Socket
    ('socket.headinajar.unplugged', 'Unplugged. Back to a plain jar.', 'Desconectado. De volta a um pote comum.'),
    ('socket.headinajar.lost', 'The socket let go. Back to a plain jar.', 'O soquete soltou. De volta a um pote comum.'),
    ('socket.headinajar.relogged', 'Signed out of the socket. Plug in again to carry on.', 'Saiu do soquete. Conecte de novo para continuar.'),
    # why a machine is paused (top right of its screen)
    ('gui.headinajar.stall.power', 'Paused: no power', 'Pausado: sem energia'),
    ('gui.headinajar.stall.output', 'Paused: output full', 'Pausado: saída cheia'),
    ('gui.headinajar.stall.input', 'Paused: missing input', 'Pausado: falta material'),
    ('gui.headinajar.stall.unreadable', "Can't read this sample", 'Amostra ilegível'),
]

# Blocks a carry mod must not pick up (Carry On reads c:relocation_not_supported): every block with a block entity,
# whose links, occupants and records would not survive being lifted.
CARRY_BLOCKS = ['separation_chair', 'life_support_cradle', 'galvanic_pile', 'charging_dock', 'saline_mixer', 'body_printer', 'leyden_jar',
                'tesla_node', 'relay_mast', 'relay_mast_mk2', 'signal_jammer', 'jarred_head', 'body_locker', 'assembly_gantry', 'centrifuge',
                'dna_sequencer', 'gene_splicer', 'injection_rig', 'leech_jar', 'turret_pedestal', 'employee_mount', 'cerebral_array', 'neural_socket']
# Creatures a carry mod must not pick up (Carry On reads c:capturing_not_supported): bodies, jars, shells and mecha.
CARRY_ENTITIES = ['headless_body', 'held_block', 'head_jar', 'missile', 'emp_grenade', 'seat', 'lunk', 'rover', 'spider_walker',
                  'hopper', 'bulwark', 'chimera', 'wheelhouse', 'skitter', 'colossus', 'kite', 'executive']


def run(EN, PT, write, shaped):
    for key, (en, pt) in REFUSALS.items():
        EN[f'refusal.headinajar.{key}'], PT[f'refusal.headinajar.{key}'] = en, pt
    for key, en, pt in LANG:
        EN[key], PT[key] = en, pt
    write('data/c/tags/block/relocation_not_supported.json', {'replace': False, 'values': ['headinajar:' + b for b in CARRY_BLOCKS]})
    write('data/c/tags/entity_type/capturing_not_supported.json', {'replace': False, 'values': ['headinajar:' + e for e in CARRY_ENTITIES]})

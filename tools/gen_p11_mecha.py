"""Hosts, shells and mecha: the refusal lines and HUD text for their repairs. Imported by gen_data.py.

Refusal lines follow gen_p9.py's rule: what is wrong, then what to do next; a control is never named by its key (the
code passes a keybind component); %s arguments are item and block names filled in by the code, so both languages use
the thing's own name.
"""

# id (after 'refusal.headinajar.'): (en, pt)
REFUSALS = {
    # instruments and vanilla items on a host, shell or mecha
    'host.embalm_shell': ("Machines don't rot. %s is for flesh.", 'Máquina não apodrece. %s é para carne.'),
    'host.lace_shell': ('A machine has no nerves to lace. %s is for flesh.', 'Máquina não tem nervos. %s é para carne.'),
    'host.lead_machine': ("A %s won't hold this. Climb in and drive it.", '%s não segura isso. Entre e dirija.'),
    'host.no_leash': ("A %s won't hold this one. Try a calmer body.", '%s não segura este. Tente um corpo mais manso.'),
    'host.saddle': ('No head to steer by. Put a jar on it instead.', 'Sem cabeça para guiar. Ponha um pote nele.'),
    'host.no_mouth': ('Nothing to eat with. %s keeps it fresh.', 'Não tem boca para comer. %s mantém fresco.'),
    'host.not_a_host': ('%s is for host bodies. Use a %s on it first.', '%s é para corpos hospedeiros. Use uma %s nele antes.'),
    # a jar riding a host
    'host.no_hands': ('A host has no hands for that. Get down first.', 'Hospedeiro não tem mãos para isso. Desça primeiro.'),
    'host.second_host': ('One body at a time. Get down from this one first.', 'Um corpo por vez. Desça deste primeiro.'),
    'host.own_body': ("Your body can't reach you up here. Get down first.", 'Seu corpo não te alcança aí em cima. Desça primeiro.'),
    'host.get_down': ('Not from up here. Get down first.', 'Daqui de cima, não. Desça primeiro.'),
    'host.out_of_reach': ("Out of this body's reach. Get closer.", 'Fora do alcance deste corpo. Chegue mais perto.'),
    # host abilities
    'host.no_ability': ('%s has no trick for that. Try another body.', '%s não tem esse truque. Tente outro corpo.'),
    'host.cooling': ('This body is catching its breath. One moment.', 'Este corpo está recuperando o fôlego. Um instante.'),
    'host.no_target': ('Nothing there to aim at. Look at a target.', 'Nada para mirar aí. Olhe para um alvo.'),
    'host.web_blocked': ('No room for a web there. Aim at open air.', 'Sem espaço para teia aí. Mire no ar livre.'),
    'host.shell_flat': ('The jar is out of charge. Recharge at a %s.', 'O pote está sem carga. Recarregue numa %s.'),
    # the Surgical Saw, keeping the head
    'saw.survived': ('%s pulled through. Weaken it further and try again.', '%s resistiu. Enfraqueça mais e tente de novo.'),
    # the Body Locker
    'locker.not_yours': ("That body is someone else's. Its owner can trust you with /headoffice trust.",
                         'Esse corpo é de outra pessoa. O dono pode confiar em você com /headoffice trust.'),
    'locker.cannot_store': ("%s doesn't fit the paperwork. It stays out.", '%s não cabe na papelada. Fica de fora.'),
    'locker.unknown': ("The file for %s can't be read. Ask an operator.", 'A ficha de %s está ilegível. Chame um operador.'),
    # mecha
    'mecha.not_yours': ('That mecha belongs to someone else. Ask them to trust you.', 'Esse mecha é de outra pessoa. Peça para confiarem em você.'),
    'mecha.seats_full': ('No free seat. Wait for one to empty.', 'Nenhum assento livre. Espere vagar um.'),
    'mecha.no_cargo': ('No %s fitted. Fit one at the %s.', 'Nenhum %s instalado. Instale um no %s.'),
    'mecha.feed_hold': ('Fuel goes in the %s. Sneak-use the mecha to open it.', 'Combustível vai no %s. Agache e use o mecha para abrir.'),
    'mecha.plating_full': ('Nothing to patch. The plating can wait.', 'Nada para remendar. A placa pode esperar.'),
    'mecha.venting': ('Venting heat. Everything comes back once it cools.', 'Expelindo calor. Tudo volta quando esfriar.'),
    'mecha.no_power': ('Battery flat. Park it on the %s to charge.', 'Bateria descarregada. Estacione no %s para carregar.'),
    'mecha.jets_spent': ('Jets spent. They refill when you land.', 'Jatos esgotados. Recarregam quando você pousar.'),
    'mecha.utility.low_power': ('%s needs %s kFE. Park on the %s to charge.', '%s precisa de %s kFE. Estacione no %s para carregar.'),
    'mecha.no_utility': ('No utility module fitted. Fit one at the %s.', 'Nenhum módulo utilitário instalado. Instale um no %s.'),
    'mecha.arm.no_target': ('Nothing in reach. Aim at something.', 'Nada ao alcance. Mire em algo.'),
    'mecha.arm.chainsaw_log': ('The chainsaw wants a creature or a log. Aim at one.', 'A motosserra quer uma criatura ou um tronco. Mire num.'),
    'mecha.arm.hemo_player': ("Players don't give blood here. Aim at a creature.", 'Jogador não doa sangue aqui. Mire numa criatura.'),
    'mecha.arm.nothing_ripe': ('Nothing ripe there. Come back at harvest.', 'Nada maduro aí. Volte na colheita.'),
    'mecha.arm.no_plating': ('No %s in the %s. Load some in.', 'Sem %s no %s. Coloque algumas.'),
    'mecha.arm.beam_refused': ("The beam won't take that. It isn't yours to move.", 'O feixe não pega isso. Não é seu para mover.'),
    'mecha.arm.protected': ('This ground is protected. Dig somewhere else.', 'Este terreno é protegido. Cave em outro lugar.'),
    # the Assembly Gantry's buttons (the old lines, with a next step, drawn over its screen)
    'gantry.no_frame': ('No frame to build on. Put one in the frame slot.', 'Nenhuma estrutura para montar. Ponha uma no espaço da estrutura.'),
    'gantry.pad_busy': ("Something stands on the Gantry. Clear its top first.", 'Tem algo em cima do pórtico. Libere o topo primeiro.'),
    'gantry.no_power': ('Assembly needs 10 kFE. Feed the Gantry power.', 'A montagem precisa de 10 kFE. Alimente o pórtico.'),
    'gantry.pad_empty': ('No mecha on the Gantry. Park one on top of it.', 'Nenhum mecha no pórtico. Estacione um em cima dele.'),
    'gantry.pad_occupied': ('Someone is aboard that mecha. They need to get out first.', 'Tem alguém a bordo do mecha. Precisa sair antes.'),
    'gantry.not_yours': ("That mecha isn't yours. Its owner can trust you.", 'Esse mecha não é seu. O dono pode confiar em você.'),
    'gantry.slots_busy': ('The slots are full. Empty the Gantry first.', 'Os espaços estão cheios. Esvazie o pórtico primeiro.'),
    'gantry.failed': ("The frame wouldn't stand up. Try again.", 'A estrutura não ficou de pé. Tente de novo.'),
    'gantry.slot_empty': ('Nothing in that slot to adjust. Pick a fitted module.', 'Nada nesse espaço para ajustar. Escolha um módulo instalado.'),
    'gantry.no_slider': ('%s has nothing to adjust. Pick another module.', '%s não tem ajuste. Escolha outro módulo.'),
}

LANG = [
    # the pilot's HUD: the swap key opens the Cargo Hold; a frame that can't walk
    ('hud.headinajar.cargo', '[%s] %s', '[%s] %s'),
    ('hud.headinajar.no_legs', 'NO LEGS: fit walking legs at the %s', 'SEM PERNAS: instale pernas no %s'),
    # the host HUD
    ('hud.headinajar.no_abilities', 'No abilities in this body', 'Este corpo não tem habilidades'),
    ('hud.headinajar.shell_flat', 'NO CHARGE: recharge at a %s', 'SEM CARGA: recarregue numa %s'),
    # the Cargo Hold's tooltip: how to get at it (%s is the swap key)
    ('mecha_module.headinajar.cargo_hold.open', 'Sneak-use your parked mecha, or press %s while piloting, to open it.',
     'Agache e use o mecha parado, ou aperte %s pilotando, para abrir.'),
]


def run(EN, PT, write, shaped):
    for key, (en, pt) in REFUSALS.items():
        EN[f'refusal.headinajar.{key}'], PT[f'refusal.headinajar.{key}'] = en, pt
    for key, en, pt in LANG:
        EN[key], PT[key] = en, pt

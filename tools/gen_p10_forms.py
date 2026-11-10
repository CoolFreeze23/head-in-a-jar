"""The player's own forms answer: refusals and lines for the head, the body, the Cradle, the Dock, the Courier and
Telekinesis, and the head pill on the link HUD. Imported by gen_data.py.

Refusal lines (refusal.headinajar.<id>) say what is wrong, then what to do next, and never name a physical key: a
control is passed in by the code as a keybind component, an item or block by its own name.
"""

# id (after 'refusal.headinajar.'): (en, pt)
REFUSALS = {
    # Telekinesis
    'tk.nothing': ('Nothing in reach to lift. Look straight at it.', 'Nada ao alcance para erguer. Olhe direto para o alvo.'),
    'tk.machine': ('Machines and chests stay put. Lift a plain block.', 'Máquinas e baús não saem do lugar. Erga um bloco simples.'),
    'tk.too_hard': ('Too hard to budge. Pick something softer.', 'Duro demais para mexer. Escolha algo mais macio.'),
    'tk.too_big': ('Too awkward to lift in one piece. Try a single block.', 'Desajeitado demais para erguer inteiro. Tente um bloco só.'),
    'tk.protected': ("You may not move that here. It isn't yours to move.", 'Você não pode mover isso aqui. Não é seu.'),
    # keys with nothing behind them in this form (asked for by the client)
    'swap.riding': ('Swap works from a jar or a body. Get down first.', 'A troca é só de pote ou de corpo. Desça primeiro.'),
    'jar.menu_linked': ('The jar menu is with your head. Swap to it first.', 'O menu do pote está com sua cabeça. Troque para ela primeiro.'),
    'drive.linked': ('Your body has legs. Walk.', 'Seu corpo tem pernas. Ande.'),
    # portals: %s is the Head Satchel
    'portal.head_behind': ("Your body can't leave your head behind. Carry it in a %s.",
                           'Seu corpo não pode deixar a cabeça para trás. Leve-a numa %s.'),
    # bodies: %s is the owner's name
    'body.riding': ('Get down first, then use your body as a jar.', 'Desça primeiro e depois use seu corpo como pote.'),
    'body.not_yours': ("That is %s's body. It is spoken for.", 'Esse é o corpo de %s. Já tem dono.'),
    'body.corpse_untrusted': ("%s's effects. They can let you in with /headoffice trust.",
                              'Os pertences de %s. A pessoa pode liberar você com /headoffice trust.'),
    'suture.not_yours': ('Only %s can be sewn back onto that body.', 'Só %s pode ser costurado de volta nesse corpo.'),
    # %s is the Suture Kit
    'suture.no_kit': ('No %s on you or in your effects. Make one, then try again.', 'Nenhum %s com você nem nos seus pertences. Faça um e tente de novo.'),
    # heads: %s is the owner's name
    'head.not_yours': ("That is %s's head. Please don't tap the glass.", 'Essa é a cabeça de %s. Por favor, não bata no vidro.'),
    'head.full': ('The jar is full of saline. Keep the bottle for later.', 'O pote está cheio de soro. Guarde o frasco para depois.'),
    'head.intact': ('The glass is sound. Keep the patch for later.', 'O vidro está inteiro. Guarde o remendo para depois.'),
    # refills used in the air. %1$s is the Manipulator Arm (unused); %s the Cradle
    'jar.refill_feed': ("No hands to pour it. Use the jar menu's feed slot.", 'Sem mãos para despejar. Use o espaço de recarga do menu do pote.'),
    'jar.refill_linked': ('Your head is elsewhere. Use it on your head or its %s.', 'Sua cabeça está em outro lugar. Use na cabeça ou no %s dela.'),
    # the Head Satchel: %s is the sneak key
    'satchel.sneak': ('To set your head down, sneak (%s) and use the satchel.', 'Para pousar a cabeça, agache (%s) e use a bolsa.'),
    'satchel.empty': ('The satchel is empty. Use your head to put it in.', 'A bolsa está vazia. Use sua cabeça para guardá-la.'),
    'satchel.jar': ("A jar can't carry itself. Swap to your body first.", 'Um pote não carrega a si mesmo. Troque para o corpo primeiro.'),
    'satchel.whole': ('Your head is still on. The satchel is for afterwards.', 'Sua cabeça ainda está no lugar. A bolsa é para depois.'),
    # the Life Support Cradle: %s is the Head Satchel, or Cranial Saline
    'cradle.empty': ('The Cradle is empty. Carry your head here in a %s and use it.', 'O Berço está vazio. Traga sua cabeça numa %s e use-o.'),
    'cradle.need_satchel': ('Your head is docked here. Bring a %s to take it out.', 'Sua cabeça está aqui. Traga uma %s para tirá-la.'),
    'cradle.full': ("The Cradle's tank is full. Keep the bottle.", 'O tanque do Berço está cheio. Guarde o frasco.'),
    'cradle.wrong_fluid': ('The Cradle takes %s only.', 'O Berço só aceita %s.'),
    'cradle.blocked': ('No room to climb in. Clear the space above the Cradle.', 'Sem espaço para entrar. Libere o espaço acima do Berço.'),
    # the Courier, from the jar menu: %s is the Body Printer
    'courier.only_jars': ('Only a head in a jar can book the Courier.', 'Só uma cabeça no pote pode chamar o Entregador.'),
    'courier.no_body': ('No body on file to deliver. A %s can print you one.', 'Nenhum corpo registrado para entregar. Uma %s imprime um.'),
    'courier.already_here': ('Your body is right here. Use it.', 'Seu corpo está bem aqui. Use-o.'),
    'courier.on_its_way': ('The Courier is already on its way. Thank you for waiting.', 'O Entregador já está a caminho. Obrigado por aguardar.'),
}

# (key, en, pt)
LINES = [
    # the Charging Dock answers a click: %1$s stored, %2$s capacity, %3$s the Galvanic Pile
    ('message.headinajar.dock_status', 'Charging Dock: %1$s / %2$s FE. Stand your jar on it to charge.',
     'Base de Recarga: %1$s / %2$s FE. Ponha seu pote em cima para carregar.'),
    ('message.headinajar.dock_empty', 'Charging Dock: empty. Feed it FE from a %3$s or a cable, then stand your jar on it.',
     'Base de Recarga: vazia. Alimente com FE de uma %3$s ou de um cabo e ponha seu pote em cima.'),
    ('message.headinajar.cradle_climbed', 'You settle into the Cradle. Swap now and your head stays docked.',
     'Você se acomoda no Berço. Troque agora e sua cabeça fica nele.'),
    # %s is the Suture Kit
    ('courier.headinajar.delivered_corpse', 'Delivery for you: one body, deceased. Use a %s on it to get back in.',
     'Entrega para você: um corpo, falecido. Use um %s nele para voltar.'),
    ('courier.headinajar.cancelled', 'Courier cancelled: you are no longer a jar. Fee refunded.',
     'Entregador cancelado: você não é mais um pote. Taxa devolvida.'),
    # the head pill on the link HUD
    ('hud.headinajar.pill.docked', 'DOCKED', 'NO BERÇO'),
    ('hud.headinajar.pill.placed', 'PLACED', 'NO CHÃO'),
]


def run(EN, PT, write, shaped):
    for key, (en, pt) in REFUSALS.items():
        k = f'refusal.headinajar.{key}'
        assert k not in EN, k
        EN[k], PT[k] = en, pt
    for key, en, pt in LINES:
        assert key not in EN, key
        EN[key], PT[key] = en, pt

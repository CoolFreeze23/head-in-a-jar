"""What a jar may work without an arm, the handbook for every new employee, and the lines that go with the power numbers.
Imported by gen_data.py.

- Every Noggin & Sons block with a menu or a status line answers a bare jar (the head_operable tag); vanilla blocks stay
  locked.
- The handbook: a lost copy is crafted from a book and a Surgical Steel Blend. With Patchouli the recipe makes the
  Patchouli book; without it, a plain written book with the opening chapters. Its pages are translation keys, so each
  reader's client shows them in its own language. The first-day copy handed out on joining is whatever this recipe
  makes (item/Handbook.java).
"""
import json
import os

# blocks of the mod that open a screen or print a status when used, added to the ones already in head_operable
HEAD_OPERABLE = ['separation_chair', 'assembly_gantry', 'body_locker', 'tesla_node', 'relay_mast', 'relay_mast_mk2',
                 'signal_jammer', 'leech_jar', 'safety_placard', 'jarred_head']

# the written edition: the opening chapters of the handbook (Orientation, Elective Separation), as plain pages.
# (en, pt, keybinds filled in as %s, in order)
PAGES = [
    ('Noggin & Sons\nEMPLOYEE HANDBOOK\n\nThe opening chapters.\n\nThe full handbook, with every department, comes '
     'with the Patchouli mod. Lost this copy? A book and a Surgical Steel Blend make another.',
     'Noggin & Filhos\nMANUAL DO FUNCIONÁRIO\n\nOs capítulos iniciais.\n\nO manual completo, com todos os departamentos, '
     'vem com o mod Patchouli. Perdeu esta cópia? Um livro e uma Mistura de Aço Cirúrgico fazem outra.', []),
    ('WELCOME\n\nCongratulations on joining Head Office. This handbook covers what an employee needs, from the '
     'Separation Chair to keeping your head topped up with Cranial Saline.',
     'BOAS-VINDAS\n\nParabéns por entrar para a Cabeça no Pote. Este manual cobre o que um funcionário precisa, da '
     'Cadeira de Separação a manter sua cabeça abastecida de Soro Craniano.', []),
    ('Start with Surgical Steel: smelt a blend of iron, bone meal and coal. Then make Neuro-Copper Wire, a Lens and a '
     'Servo. Power comes from a Galvanic Pile fed with meat or rotten flesh.',
     'Comece pelo Aço Cirúrgico: funda uma mistura de ferro, farinha de osso e carvão. Depois faça Fio de Neurocobre, '
     'uma Lente e um Servo. A energia vem de uma Pilha Galvânica alimentada com carne ou carne podre.', []),
    ('THE SEPARATION CHAIR\n\nLoad the chair with a Life Support Jar, Relay Goggles, a Collar Cam and four bottles of '
     'Cranial Saline, and give it 40 kFE. Sit down and hold Sign on Form 27-B.',
     'A CADEIRA DE SEPARAÇÃO\n\nCarregue a cadeira com um Pote de Suporte Vital, Óculos Retransmissores, uma Câmera de '
     'Colarinho e quatro frascos de Soro Craniano, e dê 40 kFE a ela. Sente e segure Assinar no Formulário 27-B.', []),
    ('You may get up until the cut. After that, DOLORES will finish what she started. A worn blade can botch the job: '
     'keep a Replacement Blade handy.',
     'Você pode levantar até o corte. Depois disso, a DOLORES termina o que começou. Uma lâmina gasta pode estragar o '
     'serviço: tenha uma Lâmina de Reposição à mão.', []),
    ('THE NEURO-LINK\n\nAfter the cut you walk around in your own headless body and see through the Collar Cam. Your '
     'head wears the goggles and sends the signal. The further apart they are, the worse the picture.',
     'A NEUROCONEXÃO\n\nDepois do corte você anda no seu próprio corpo sem cabeça e enxerga pela Câmera de Colarinho. '
     'Sua cabeça usa os óculos e envia o sinal. Quanto mais longe, pior a imagem.', []),
    ('Water, rock, storms, portals and the Warden all hurt the signal. At zero for five seconds you wake up in your '
     'head. Press %s to swap between body and head yourself.',
     'Água, rocha, tempestades, portais e o Warden prejudicam o sinal. Com sinal zero por cinco segundos, você acorda '
     'na sua cabeça. Aperte %s para trocar entre corpo e cabeça você mesmo.', ['key.headinajar.swap']),
    ('Carry your head in a Head Satchel for a perfect link, or dock it in a Life Support Cradle. Relay Masts pass the '
     'signal along: masts reach each other and your body within their radius.',
     'Leve sua cabeça numa Bolsa de Cabeça para uma conexão perfeita, ou deixe-a num Berço de Suporte Vital. Mastros '
     'Retransmissores passam o sinal adiante: alcançam uns aos outros e o seu corpo dentro do raio.', []),
    ('WHEN IT GOES WRONG\n\nIf your body dies, it becomes a corpse that keeps your things, and you wake up in your head. '
     'As a jar, use a Suture Kit on the corpse to get back into it, or print a new body.',
     'QUANDO DÁ ERRADO\n\nSe seu corpo morrer, ele vira um cadáver que guarda suas coisas, e você acorda na sua cabeça. '
     'Como pote, use um Kit de Sutura no cadáver para voltar a ele, ou imprima um corpo novo.', []),
    ('If your head is destroyed while you are out walking, that is a real death. Guard it. A Signal Jammer near your '
     'enemies works wonders.',
     'Se sua cabeça for destruída enquanto você passeia, é morte de verdade. Proteja-a. Um Bloqueador de Sinal perto '
     'dos inimigos faz maravilhas.', []),
    ('GETTING HOME\n\nWalk too far and the link breaks: you wake up in your head. A jar with the Wheels module drives at '
     'half walking speed: press %s. Without wheels the same key tips the jar on its side to roll, very slowly.',
     'VOLTANDO PARA CASA\n\nAnde longe demais e a conexão cai: você acorda na sua cabeça. Um pote com Rodinhas anda à '
     'metade da velocidade de caminhada: aperte %s. Sem rodinhas, a mesma tecla tomba o pote para ele rolar, devagar.',
     ['key.headinajar.drive']),
    ('Stranded anyway? Press Courier in the jar menu. A Noggin & Sons courier delivers your body to your head in 30 '
     'seconds, from anywhere, for 20 kFE of charge (or all you have, if less). Then press %s.',
     'Sem saída mesmo assim? Aperte Entregador no menu do pote. A Noggin & Filhos leva seu corpo até sua cabeça em 30 '
     'segundos, de qualquer lugar, por 20 kFE de carga (ou toda a que houver). Depois aperte %s.', ['key.headinajar.swap']),
]

LANG = [
    # the Courier says what it took (%s: FE, already formatted)
    ('courier.headinajar.booked_fee', 'Courier booked: your body arrives in 30 seconds. Fee taken: %s FE.',
     'Entregador chamado: seu corpo chega em 30 segundos. Taxa cobrada: %s FE.'),
    # the jar menu's feed slot, when it is empty and the pointer is on it (%s: FE, formatted)
    ('gui.headinajar.feed.title', 'Feed slot', 'Espaço de recarga'),
    ('gui.headinajar.feed.redstone', 'Redstone: %s FE a dust, %s FE a block', 'Redstone: %s FE por pó, %s FE por bloco'),
    ('gui.headinajar.feed.saline', 'Saline Bottles and Glass Patches go in here too', 'Frascos de Soro e Remendos de Vidro também vão aqui'),
    # the ability key and sprint share a key: said once (%1$s the key, %2$s "Sprint", %3$s "Controls")
    ('message.headinajar.shared_key', 'Your ability key (%1$s) is also %2$s, so a press does both. Rebind either in %3$s if they get in each other\'s way.',
     'Sua tecla de habilidade (%1$s) também é %2$s, então um toque faz as duas coisas. Troque uma delas em %3$s se atrapalhar.'),
]


def written_book():
    """The written edition as an item stack in recipe-result form."""
    pages = []
    for i, (_en, _pt, keys) in enumerate(PAGES):
        page = {'translate': f'book.headinajar.written.{i + 1}'}
        if keys:
            page['with'] = [{'keybind': k} for k in keys]
        pages.append(json.dumps(page, ensure_ascii=False))
    return {'id': 'minecraft:written_book', 'count': 1, 'components': {
        'minecraft:written_book_content': {'title': 'Employee Handbook', 'author': 'Noggin & Sons', 'generation': 0,
                                           'pages': pages, 'resolved': True},
        'minecraft:custom_name': json.dumps({'translate': 'book.headinajar.handbook', 'italic': False})}}


def run(EN, PT, write, shaped):
    res = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources')
    tag = 'data/headinajar/tags/block/head_operable.json'
    path = os.path.join(res, tag)
    values = json.load(open(path, encoding='utf-8'))['values'] if os.path.exists(path) else []
    write(tag, {'values': values + ['headinajar:' + b for b in HEAD_OPERABLE if 'headinajar:' + b not in values]})

    ingredients = [{'item': 'minecraft:book'}, {'item': 'headinajar:surgical_steel_blend'}]
    patchouli = {'type': 'neoforge:mod_loaded', 'modid': 'patchouli'}
    write('data/headinajar/recipe/handbook_copy.json', {
        'neoforge:conditions': [patchouli],
        'type': 'minecraft:crafting_shapeless', 'category': 'misc', 'ingredients': ingredients,
        'result': {'id': 'patchouli:guide_book', 'count': 1, 'components': {'patchouli:book': 'headinajar:handbook'}}})
    write('data/headinajar/recipe/handbook_copy_written.json', {
        'neoforge:conditions': [{'type': 'neoforge:not', 'value': patchouli}],
        'type': 'minecraft:crafting_shapeless', 'category': 'misc', 'ingredients': ingredients, 'result': written_book()})

    for i, (en, pt, _keys) in enumerate(PAGES):
        EN[f'book.headinajar.written.{i + 1}'], PT[f'book.headinajar.written.{i + 1}'] = en, pt
    for key, en, pt in LANG:
        EN[key], PT[key] = en, pt

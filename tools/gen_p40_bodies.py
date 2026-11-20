"""Lines and data for the 0.6.3 release (Bodies at work): every vanilla host body does something (ten blanks' Abilities
and five Secondaries), the headless chicken, the Corporate Wellness Treadmill and Safety Placard notice 12. Imported by
gen_data.py after gen_p39_dna and before the handbook generator (gen_p27_clarity), whose text check, HUD width checks
and handbook read the final lines, ability names and entries.

It writes:
- the 17 host files whose bodies gain a trick (gen_p3's table with the new primary/secondary, sheep's
  half_fall_damage, the villager's and piglin's neutral_to);
- the Treadmill: blockstate and block models (from the art stream's geometry when it is merged, a plain stand-in
  until then), item model, recipe, loot table, pickaxe tag, its sound event and the data map of rates and refusals
  (data/headinajar/data_maps/entity_type/treadmill.json, every vanilla row);
- notice 12 in the placard's cycle (gen_p39's models and blockstate again, with the notice drawn from its lines);
- three advancements (one hidden), the memo, the handbook's two new entries and the corrected "Who Does What";
- every new language line, en_us and pt_br; each action-bar line is checked under 400 px with its longest real
  arguments, in both languages.
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..')
RES = os.path.join(ROOT, 'src', 'main', 'resources')


def ab(type_, cooldown, **params):
    return {'type': type_, 'cooldown': cooldown, 'params': {k: float(v) for k, v in params.items()}}


# ---------------------------------------------------------------------------------------------------------------
# The tricks: host -> what changes in its gen_p3 file. Cooldowns are the book's (ticks). Numbers marked "measured" are
# set by the GameTest that measures them (Long Jump 6 blocks, Dash 8).
TRICKS = {
    'cow': dict(primary=ab('herd', 400, radius=16, duration=200, cap=24)),
    'pig': dict(primary=ab('rooting', 300, radius=16, duration=200)),
    'sheep': dict(primary=ab('shed', 1200, min=1, max=3), flags=['half_fall_damage']),
    'llama': dict(primary=ab('spit', 40, range=12, damage=1, knockback=0.6)),
    'villager': dict(primary=ab('bell', 1200, radius=32, duration=200), neutral_to=['minecraft:iron_golem']),
    'piglin': dict(primary=ab('barter', 120, delay=120), neutral_to=['minecraft:piglin']),
    'strider': dict(primary=ab('warm_up', 20, heal=1)),
    'turtle': dict(primary=ab('withdraw', 300, duration=100, factor=0.2)),
    'frog': dict(primary=ab('leap', 80, speed=0.69)),  # measured (TrickGameTests.frogLongJumpsSix): lands 6 blocks out on flat ground
    'cat': dict(primary=ab('hiss', 200, radius=8, duration=100)),
    'camel': dict(secondary=ab('dash', 100, speed=0.87)),  # measured (TrickGameTests.camelDashesEight): lands 8 blocks out
    'horse': dict(secondary=ab('rear', 160, radius=3, knockback=1.2)),
    'donkey': dict(secondary=ab('rear', 160, radius=3, knockback=1.2)),
    'mule': dict(secondary=ab('rear', 160, radius=3, knockback=1.2)),
    'iron_golem': dict(secondary=ab('mend', 200, heal=25)),
    'wolf': dict(secondary=ab('howl', 600, radius=16, duration=1200)),
    'dolphin': dict(secondary=ab('treasure', 1200, chunks=4, duration=200)),
}

# The new ability names, added to gen_p27_clarity.ABILITIES (the HUD and the handbook name them from there).
ABILITIES = {
    'herd': ('Moo', 'Mugido'), 'rooting': ('Rooting', 'Fuçar'), 'shed': ('Shed', 'Desprender Lã'),
    'spit': ('Spit', 'Cuspe'), 'bell': ('Sound the Bell', 'Tocar o Sino'), 'barter': ('Barter', 'Escambo'),
    'warm_up': ('Warm Up', 'Esquentar'), 'withdraw': ('Withdraw', 'Recolher'), 'leap': ('Long Jump', 'Salto em Distância'),
    'hiss': ('Hiss', 'Bufar'), 'dash': ('Dash', 'Arrancada'), 'rear': ('Rear', 'Empinar'), 'mend': ('Mend', 'Remendo'),
    'howl': ('Howl', 'Uivo'), 'treasure': ('Treasure Sense', 'Faro de Tesouro'),
}

# ---------------------------------------------------------------------------------------------------------------
# The Treadmill's rates (FE/t) and refusals, by species: the company's table and its extra rows for the other bodies. A host
# with no row: a flier by its file's "flight" flag, a WaterAnimal a swimmer, a shell or mecha a shell, anything else 8.
TREADMILL_FE = {
    2: ['silverfish', 'endermite'],
    4: ['skeleton', 'stray', 'bogged', 'turtle', 'wither_skeleton', 'skeleton_horse'],
    6: ['zombie', 'husk', 'drowned', 'zombified_piglin', 'cat', 'armadillo', 'zombie_villager', 'zombie_horse', 'snow_golem'],
    8: ['pig', 'sheep', 'fox', 'creeper', 'cave_spider', 'frog', 'rabbit', 'ocelot'],
    10: ['cow', 'mooshroom', 'villager', 'goat', 'wolf', 'witch', 'pillager', 'vindicator', 'evoker', 'piglin',
         'wandering_trader', 'piglin_brute', 'illusioner'],
    12: ['chicken', 'panda'],
    14: ['llama', 'spider', 'trader_llama'],
    16: ['donkey', 'enderman'],
    18: ['mule', 'polar_bear'],
    20: ['horse', 'sniffer'],
    24: ['camel'],
}
TREADMILL_REFUSE = {
    'flier': ['bat', 'phantom', 'parrot', 'allay', 'vex', 'blaze', 'breeze', 'bee'],
    'ghast': ['ghast'],
    'swimmer': ['cod', 'salmon', 'tropical_fish', 'pufferfish', 'squid', 'glow_squid', 'dolphin', 'axolotl', 'guardian',
                'elder_guardian', 'tadpole'],
    'strider': ['strider'],
    'heavy': ['iron_golem', 'ravager', 'warden', 'hoglin', 'zoglin'],
    'bounce': ['slime', 'magma_cube'],
}
TREADMILL_DEFAULT_FE = 8

# ---------------------------------------------------------------------------------------------------------------
# Every plain line of this release: (key, en, pt).
LINES = [
    # tricks: refusals (action bar) and what a trick says
    ('refusal.headinajar.host.no_gold', 'No gold in your pockets. Piglins check.', 'Nenhum ouro nos bolsos. Piglins conferem.'),
    ('refusal.headinajar.host.no_iron', 'No iron in your pockets. Golems are particular.',
     'Nenhum ferro nos bolsos. Golens são exigentes.'),
    ('refusal.headinajar.host.no_treasure', 'No treasure in range. Try the sea.', 'Nenhum tesouro por perto. Tente o mar.'),
    ('refusal.headinajar.host.warm_up_lava', 'Warm Up needs lava underfoot. Stand still on it.',
     'Esquentar precisa de lava sob os pés. Fique parado nela.'),
    ('refusal.headinajar.host.no_wolves', 'No wolves of yours within earshot.', 'Nenhum lobo seu ao alcance do uivo.'),
    ('refusal.headinajar.host.no_herd', 'Nobody here eats wheat. The moo has been noted.',
     'Ninguém aqui come trigo. O mugido foi registrado.'),
    ('refusal.headinajar.host.no_creepers', 'No creepers within 8 blocks. The hiss goes on your file.',
     'Nenhum creeper a 8 blocos. O bufo vai para a sua ficha.'),
    ('refusal.headinajar.host.barter_busy', 'Still admiring the last ingot. Piglins take their time.',
     'Ainda admirando o último lingote. Piglin não tem pressa.'),
    ('refusal.headinajar.host.mend_full', 'This body is in one piece. Keep the iron.',
     'Este corpo está inteiro. Guarde o ferro.'),
    ('message.headinajar.host.warm_up_on', 'Warm Up on. Stand still on lava to heal.',
     'Esquentar ligado. Fique parado na lava para se curar.'),
    ('message.headinajar.host.warm_up_off', 'Warm Up off.', 'Esquentar desligado.'),
    ('message.headinajar.host.herded', '%s colleagues are following you. Please do not lose any.',
     '%s colegas estão seguindo você. Favor não perder nenhum.'),
    ('message.headinajar.host.howled', '%s of your wolves are on their way.', '%s dos seus lobos estão a caminho.'),
    ('message.headinajar.host.treasure', "Treasure that way. Head Office will want its share.",
     'Tesouro para lá. A Matriz vai querer a parte dela.'),
    ('hud.headinajar.warm_up_on', 'Warm Up: on', 'Esquentar: ligado'),
    # the headless chicken
    ('message.headinajar.chicken_fine', 'DOLORES: It will be fine. Probably.', 'DOLORES: Vai ficar tudo bem. Provavelmente.'),
    # the Treadmill
    ('block.headinajar.treadmill', 'Corporate Wellness Treadmill', 'Esteira de Bem-Estar Corporativo'),
    ('block.headinajar.treadmill.how', 'A host walks on it and makes power. Ride one on, then get off.',
     'Um hospedeiro anda nela e gera energia. Monte um nela e desça.'),
    ('refusal.headinajar.treadmill.flier', "A %s doesn't walk. Try a cow.", '%s não anda. Tente uma vaca.'),
    ('refusal.headinajar.treadmill.ghast', 'Ghasts do not do cardio.', 'Ghasts não fazem cardio.'),
    ('refusal.headinajar.treadmill.swimmer', 'Out of its depth.', "Peixe fora d'água. Literalmente."),
    ('refusal.headinajar.treadmill.strider', "It won't walk on anything but lava.", 'Só anda em cima de lava.'),
    ('refusal.headinajar.treadmill.heavy', '%s needs the Industrial Treadmill. Head Office has it on order.',
     '%s precisa da Esteira Industrial. A Matriz já encomendou.'),
    ('refusal.headinajar.treadmill.living', 'Staff with heads use the stairs.', 'Funcionários com cabeça usam a escada.'),
    ('refusal.headinajar.treadmill.shell', 'Shells run on charge, not cardio.', 'Cascos andam com carga, não com cardio.'),
    ('refusal.headinajar.treadmill.bounce', 'A %s bounces. The belt needs feet.', '%s quica. A esteira precisa de pés.'),
    ('refusal.headinajar.treadmill.no_room', 'The belt needs two clear blocks in front of you.',
     'A esteira precisa de dois blocos livres à sua frente.'),
    ('message.headinajar.treadmill.clocked_in', 'Clocked in.', 'Ponto batido.'),
    ('message.headinajar.treadmill.idle', 'Belt idle. Ride a body on and get off to clock it in.',
     'Esteira parada. Monte um corpo nela e desça para bater o ponto.'),
    ('message.headinajar.treadmill.status', '%s: %s FE/t. Fresh %s%%, sync %s%%.', '%s: %s FE/t. Frescor %s%%, sincronia %s%%.'),
    ('message.headinajar.treadmill.tired', 'Tired: a laced body has nothing left to burn.',
     'Cansado: um corpo com renda não tem mais o que queimar.'),
    ('message.headinajar.treadmill.stepped_off', '%s steps off the belt and stands about.', '%s sai da esteira e fica parado.'),
    ('memo.headinajar.treadmill_powered', 'Your colleague is walking for the company. Please keep the belt clear and the body fresh.',
     'Seu colega está caminhando pela empresa. Mantenha a esteira livre e o corpo fresco.'),
    ('subtitles.headinajar.treadmill.run', 'Treadmill whirs', 'Esteira zune'),
    # Safety Placard notice 12
    ('placard.headinajar.12', 'A HEALTHY BODY IS A PRODUCTIVE BODY', 'CORPO SAUDÁVEL, CORPO PRODUTIVO'),
]

# Lines a helper adds (its own list, so streams never touch the same lines): (key, en, pt).
MORE_TRICKS = [
    ('refusal.headinajar.host.shed_bare', 'This body came to us shorn. Nothing left to shed.',
     'Este corpo já chegou tosado. Não há o que desprender.'),
    ('message.headinajar.host.rooted', '%s things worth digging up. The snout has filed its report.',
     '%s coisas que valem a escavação. O focinho já entregou o relatório.'),
    ('message.headinajar.host.rooted_none', 'Nothing worth digging up within %s blocks. The snout is never wrong.',
     'Nada que valha cavar a %s blocos. O focinho nunca erra.'),
]
MORE_TREADMILL = []

ADVANCEMENTS = [  # (id, parent, icon, hidden, en title, en description, pt title, pt description)
    ('headless_chicken', 'hosted', 'minecraft:feather', True, 'Running Around Like a Headless Chicken',
     'Watch a headless chicken run for 10 seconds', 'Feito Galinha sem Cabeça',
     'Assista a uma galinha sem cabeça correr por 10 segundos'),
    ('corporate_wellness', 'powered', 'headinajar:treadmill', False, 'Corporate Wellness', 'Power a machine from a Treadmill',
     'Bem-Estar Corporativo', 'Alimente uma máquina com uma Esteira'),
    ('cardio_department', 'corporate_wellness', 'minecraft:leather', False, 'Cardio Department',
     'Make 100 FE/t from Treadmills within 16 blocks', 'Departamento de Cardio', 'Gere 100 FE/t com Esteiras num raio de 16 blocos'),
]

# Action-bar lines with arguments: key -> lambda lang_index: [argument lists] (the longest real ones).
ACTION_BAR_ARGS = {
    'refusal.headinajar.treadmill.flier': lambda i: [['Phantom'], ['Breeze'], ['Parrot']] if i == 0 else [['Papagaio'], ['Morcego'], ['Phantom']],
    'refusal.headinajar.treadmill.heavy': lambda i: [['Iron Golem'], ['Ravager'], ['Warden']] if i == 0 else [['Golem de Ferro'], ['Devastador']],
    'refusal.headinajar.treadmill.bounce': lambda i: [['Magma Cube']] if i == 0 else [['Cubo de Magma']],
    'message.headinajar.treadmill.status': lambda i: [[('Zombified Piglin', 'Piglin-Zumbificado')[i], '26.4', '100', '100']],
    'message.headinajar.treadmill.stepped_off': lambda i: [[('Zombified Piglin', 'Piglin-Zumbificado')[i]]],
    'message.headinajar.host.herded': lambda i: [['24']],
    'message.headinajar.host.howled': lambda i: [['24']],
    'message.headinajar.host.rooted': lambda i: [['256']],
    'message.headinajar.host.rooted_none': lambda i: [['32']],
}

# ---------------------------------------------------------------------------------------------------------------
# The handbook.
TREADMILL_PAGES = [
    ('spotlight', 'headinajar:treadmill',
     'Two blocks long. Ride a host onto the belt and get off with $(k:key.sneak): "Clocked in." It walks in place: 4 '
     'to 24 FE/t by species (a cow 10, a camel 24, anything unlisted 8).',
     'Dois blocos de comprimento. Monte um hospedeiro na esteira e desça com $(k:key.sneak): "Ponto batido." Ele anda '
     'parado: 4 a 24 FE/t pela espécie (vaca 10, camelo 24, o resto 8).'),
    ('text',
     'It holds 10 kFE and pushes up to 1,000 FE/t from its near end into touching machines, as the Galvanic Pile does. '
     'A body on the belt rots twice as fast (embalmed: at the usual rate). A laced body is $(l)Tired$(): half output, '
     'forever. Full sync: +10%.',
     'Guarda 10 kFE e empurra até 1.000 FE/t pela ponta de trás para as máquinas encostadas, como a Pilha Galvânica. '
     'O corpo na esteira apodrece em dobro (embalsamado: no ritmo de sempre). Com renda fica $(l)Cansado$(): metade, '
     'para sempre. Sincronia cheia: +10%.'),
    ('text',
     'Ridden, it makes power too, and forward goes nowhere. Use it with an empty hand to read it. Fliers, swimmers, '
     'ghasts, striders, slimes, shells and the heavy ones (golems, ravagers, wardens, hoglins) refuse.',
     'Montado, também gera, e para a frente não sai do lugar. Use de mão vazia para ler. Os que voam, os que nadam, '
     'ghasts, lavagantes, slimes, cascos e os pesados (golens, devastadores, wardens, hoglins) recusam.'),
]
EVERY_BODY_PAGES = [
    ('form', ('Form 9-T: Tricks', 'Formulário 9-T: Truques'),
     'Every vanilla body does something at 50% sync:$(br)Moo: cow$(br)Rooting: pig$(br)Shed: sheep$(br)Spit: llama$(br)'
     'Sound the Bell: villager$(br)Barter: piglin (a gold ingot)',
     'Todo corpo vanilla faz algo com 50% de sincronia:$(br)Mugido: vaca$(br)Fuçar: porco$(br)Desprender Lã: '
     'ovelha$(br)Cuspe: lhama$(br)Tocar o Sino: aldeão$(br)Escambo: piglin (um lingote de ouro)'),
    ('form', ('Form 9-T (2)', 'Formulário 9-T (2)'),
     'Warm Up: strider, on lava$(br)Withdraw: turtle$(br)Long Jump: frog$(br)Hiss: cat$(br2)At 75%:$(br)Dash: camel'
     '$(br)Rear: horse, donkey, mule',
     'Esquentar: lavagante, na lava$(br)Recolher: tartaruga$(br)Salto em Distância: sapo$(br)Bufar: gato$(br2)'
     'Com 75%:$(br)Arrancada: camelo$(br)Empinar: cavalo, burro, mula'),
    ('form', ('Form 9-T (3)', 'Formulário 9-T (3)'),
     'Mend: iron golem (an iron ingot)$(br)Howl: wolf$(br)Treasure Sense: dolphin$(br2)Gold and iron come from your '
     'pockets.',
     'Remendo: golem de ferro (um lingote de ferro)$(br)Uivo: lobo$(br)Faro de Tesouro: golfinho$(br2)Ouro e ferro saem '
     'dos seus bolsos.'),
    ('text',
     'Traits at 25%: a sheep takes half fall damage; iron golems leave a villager alone, piglins a piglin. A strider '
     'walks on lava and a cat keeps phantoms off, as they always have.$(br2)A chicken sawn without a jar runs in '
     'circles where it was cut, never more than 12 blocks out.',
     'Traços com 25%: a ovelha toma metade do dano de queda; golens deixam o aldeão em paz, piglins o piglin. O '
     'lavagante anda na lava e o gato afasta phantoms, como sempre.$(br2)Galinha serrada sem pote corre em '
     'círculos onde foi cortada, nunca a mais de 12 blocos.'),
]
JEI_TREADMILL = (
    'A host walks on it in place and makes 4 to 24 FE/t by species; it stores 10 kFE and pushes up to 1,000 FE/t from '
    'its near end. Ride a host on and get off to clock it in.',
    'Um hospedeiro anda nela parado e gera 4 a 24 FE/t pela espécie; guarda 10 kFE e empurra até 1.000 FE/t pela ponta '
    'de trás. Monte um hospedeiro nela e desça para bater o ponto.')


# ---------------------------------------------------------------------------------------------------------------
def _hosts(write):
    """The 17 host files with their tricks, written as gen_p3 writes them (its own entries, changed)."""
    import gen_p3
    for name, change in TRICKS.items():
        d = dict(gen_p3.HOSTS[name])
        assert 'primary' not in d or 'secondary' in change, f'{name} already has an Ability'
        assert 'secondary' not in d, f'{name} already has a Secondary'
        for k in ('primary', 'secondary'):
            if k in change:
                d[k] = change[k]
        d['flags'] = list(d.get('flags', [])) + change.get('flags', [])
        d['neutral_to'] = list(d.get('neutral_to', [])) + change.get('neutral_to', [])
        data = {'entity': f'minecraft:{name}', 'neck': d['neck'], 'blood': d.get('blood', 'warm'),
                'decay_per_minute': d.get('decay', 2.0), 'attributes': d.get('attributes', {}), 'flags': d['flags'],
                'drawbacks': d.get('drawbacks', []), 'neutral_to': d['neutral_to']}
        for k in ('primary', 'secondary'):
            if k in d:
                data[k] = d[k]
        write(f'data/headinajar/headinajar/host/{name}.json', data)
        gen_p3.HOSTS[name] = d


def _data_map(write):
    values = {}
    for fe, names in TREADMILL_FE.items():
        for n in names:
            assert f'minecraft:{n}' not in values, n
            values[f'minecraft:{n}'] = {'fe': fe}
    for kind, names in TREADMILL_REFUSE.items():
        for n in names:
            assert f'minecraft:{n}' not in values, n
            values[f'minecraft:{n}'] = {'refuse': kind}
    write('data/headinajar/data_maps/entity_type/treadmill.json', {'values': dict(sorted(values.items()))})


def _treadmill_models(write):
    """The block models, item model and blockstate from the art's geometry script (art/machines/treadmill/treadmill_geo.py:
    the bodies, the belt idle and running, the five dial needles; a multipart blockstate over facing, part, running and
    dial, the head drawing the needle and half the belt)."""
    import sys
    sys.path.insert(0, os.path.join(ROOT, 'art', 'machines', 'treadmill'))
    import treadmill_geo as TG
    for path, model in TG.java_models().items():
        write(path, model)
    write('assets/headinajar/blockstates/treadmill.json', TG.blockstate())


def _treadmill(write, shaped):
    _treadmill_models(write)
    shaped('treadmill', ['LLL', 'SWS', 'III'], dict(L='leather', S='headinajar:servo', W='headinajar:neuro_copper_wire',
                                                    I='headinajar:surgical_steel_ingot'))
    # one item from the foot (breaking either half breaks both)
    write('data/headinajar/loot_table/blocks/treadmill.json', {
        'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'headinajar:treadmill'}],
                                             'conditions': [{'condition': 'minecraft:survives_explosion'},
                                                            {'condition': 'minecraft:block_state_property', 'block': 'headinajar:treadmill',
                                                             'properties': {'part': 'foot'}}]}]})
    tag = 'data/minecraft/tags/block/mineable/pickaxe.json'
    path = os.path.join(RES, tag)
    values = json.load(open(path, encoding='utf-8'))['values']
    if 'headinajar:treadmill' not in values:
        write(tag, {'values': values + ['headinajar:treadmill']})
    _data_map(write)


def _sound(EN, PT, write):
    """The belt's whir (files made by tools/make_sounds.py); its subtitle is in LINES."""
    path = os.path.join(RES, 'assets', 'headinajar', 'sounds.json')
    with open(path, encoding='utf-8') as f:
        table = json.load(f)
    table['treadmill.run'] = {'sounds': [{'name': 'headinajar:treadmill/run', 'attenuation_distance': 10}],
                              'subtitle': 'subtitles.headinajar.treadmill.run'}
    write('assets/headinajar/sounds.json', table)


def _placard(EN, PT, write):
    """Notice 12 joins the cycle: gen_p39's placard models and blockstate again with 12 shipped, every notice drawn."""
    import gen_p39_dna as D
    if 12 not in D.PLACARD_SHIPPED:
        D.PLACARD_SHIPPED = sorted(D.PLACARD_SHIPPED + [12])
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


def _handbook(EN, PT):
    import gen_p27_handbook_b as hb
    entries = [
        {'cat': 'staffing', 'id': 'treadmill', 'sort': 10, 'icon': 'headinajar:treadmill',
         'en': 'The Corporate Wellness Treadmill', 'pt': 'A Esteira de Bem-Estar Corporativo', 'pages': TREADMILL_PAGES,
         'note': ('A healthy body is a productive body.', 'Corpo saudável, corpo produtivo.')},
        {'cat': 'staffing', 'id': 'every_body', 'sort': 11, 'icon': 'minecraft:wheat',
         'en': 'Every Body Does Something', 'pt': 'Todo Corpo Faz Algo', 'pages': EVERY_BODY_PAGES},
    ]
    have = {(e['cat'], e['id']) for e in hb.ENTRIES}
    for e in entries:
        if (e['cat'], e['id']) not in have:
            hb.ENTRIES.append(e)
    # "Who Does What" ended "The rest: none."; the Secondaries' list named three hosts
    who = next(e for e in hb.ENTRIES if (e['cat'], e['id']) == ('staffing', 'abilities'))
    last = who['pages'][2]
    if 'The rest: none.' in last[2]:
        who['pages'][2] = (last[0], last[1], last[2].replace('The rest: none.', 'The rest: Every Body Does Something.'),
                           last[3].replace('O resto: nada.', 'O resto: Todo Corpo Faz Algo.'))
    sync = next(e for e in hb.ENTRIES if (e['cat'], e['id']) == ('staffing', 'sync'))
    page = sync['pages'][1]
    if '(blaze, enderman, ravager)' in page[1]:
        sync['pages'][1] = (page[0], page[1].replace('(blaze, enderman, ravager)', '(blaze, enderman, ravager and the five of Every Body Does Something)'),
                            page[2].replace('(blaze, enderman, devastador)', '(blaze, enderman, devastador e os cinco de Todo Corpo Faz Algo)'))
    hb.JEI_INFO['headinajar:treadmill'] = JEI_TREADMILL
    hb.JEI_INFO['headinajar:safety_placard'] = ('Hangs on a wall. Use it to change the notice: there are fifteen.',
                                               'Fica pendurada na parede. Use para trocar o aviso: são quinze.')


def run(EN, PT, write, shaped):
    import gen_p27_clarity as c
    for t, names in ABILITIES.items():
        assert t not in c.ABILITIES, t
        c.ABILITIES[t] = names
    lines = LINES + MORE_TRICKS + MORE_TREADMILL
    taken = [key for key, _e, _p in lines if key in EN]
    assert not taken, f'already in the language files: {taken}'
    for key, en, pt in lines:
        EN[key], PT[key] = en, pt
    _hosts(write)
    _treadmill(write, shaped)
    _sound(EN, PT, write)
    _placard(EN, PT, write)
    _advancements(EN, PT, write)
    _handbook(EN, PT)
    import gen_p39_dna as D
    saved = D.ACTION_BAR_ARGS
    D.ACTION_BAR_ARGS = ACTION_BAR_ARGS
    try:
        D._check_action_bar(EN, PT, [k for k, _e, _p in lines])
    finally:
        D.ACTION_BAR_ARGS = saved

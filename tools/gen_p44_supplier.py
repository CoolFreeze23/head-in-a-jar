"""Lines and data for the 0.8.0 release (The Supplier Contract): newer creatures, blocks and items that another mod
(the supplier) delivers under vanilla's own minecraft: names, filed by id and loaded only when the id is registered.
Imported by gen_data.py after gen_p43_moments and before the handbook generator (gen_p27_clarity).

The rule for everything here: name a creature, block or item by its minecraft: id string, never the supplier; every
file for one carries the headinajar:registered condition (cond()); a tag entry for one is optional (opt()).

It writes:
- the /headoffice status line for the new arrivals;
- SPECIES: the species rows and the machine tables for every new arrival (staff rows, host files, tags, data map
  entries, the Directory's New Arrivals row, refusals at every tool);
- MACHINES: the new arrivals at every other tool and machine;
- PLUMBING: the capability providers, the Unseating Policy and the discontinued lines.
Every new language line, en_us and pt_br; each action-bar line is checked under 400 px.
"""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..')
RES = os.path.join(ROOT, 'src', 'main', 'resources')

R = 'refusal.headinajar.'
M = 'message.headinajar.'
S = 'staff.headinajar.'
C = 'command.headinajar.'

# The new arrivals, in the order Head Office lists them (Supplier.NEW_ARRIVALS in the code).
NEW_ARRIVALS = ['creaking', 'happy_ghast', 'copper_golem', 'nautilus', 'zombie_nautilus', 'camel_husk', 'parched',
                'sulfur_cube']


def cond(id, registry='entity_type'):
    """The load condition for a file about a supplied thing: it loads exactly when that id is registered."""
    if ':' not in id:
        id = 'minecraft:' + id
    return {'type': 'headinajar:registered', 'registry': registry, 'id': id}


def opt(id):
    """A tag entry for a supplied thing: optional, so the tag loads without it."""
    if ':' not in id:
        id = 'minecraft:' + id
    return {'id': id, 'required': False}


# ---------------------------------------------------------------------------------------------------------------
# The core table: (key, en, pt).
LINES = [
    (C + 'status.arrivals', 'New arrivals on file: %s.', 'Novos contratados em arquivo: %s.'),
    (C + 'status.arrivals.strains', 'New arrivals on file: %s. Strains: %s.',
     'Novos contratados em arquivo: %s. Linhagens: %s.'),
    (C + 'status.arrivals.none', 'New arrivals on file: none. The supplier has not delivered.',
     'Novos contratados em arquivo: nenhum. O fornecedor não entregou.'),
]
# Action-bar lines' longest real arguments, by key (see gen_p39_dna._check_action_bar).
ACTION_BAR_ARGS = {}

# ---------------------------------------------------------------------------------------------------------------
# SPECIES (1.2): the species rows and the machine tables. Lines in SPECIES_LINES, files written by _species().
#
# Each new arrival's row, as the idea book settles it (Chapter 1's list; titles, departments and wages from each
# creature's own chapter). What the later chapters add (desk jobs, bells, genes, new jobs and quirks, host abilities,
# the Array's night, rain, oxidised and contents rates) is not here.
#   title, department, blood (None: warm, the default), wage, work (None: HR has no job description yet), array,
#   turret, socket, alarm (a Lookout's sound; None for an attacker), host neck (None: never a host), host decay,
#   treadmill row (None: never on a belt), extra items the row needs registered
SPECIES = {
    'creaking': dict(
        title=('Night Auditor', 'Auditor Noturno'), noise=('Creak.', 'Créc.'), dept='competition', blood='sap',
        wage=('minecraft:resin_clump', 1),
        # a creaking is never living staff: a heart-bound one answers to its heart (the code reads its saved home), an
        # unbound one has no heart to answer to
        work={'hire': 'lanyard.creaking'},
        array=10, turret='lookout', socket='none', alarm='entity.creaking.activate',
        neck=[0, 1.85, 0], decay=0.0, treadmill={'refuse': 'watched'}, items=['resin_clump']),
    'happy_ghast': dict(
        title=('Chief Happiness Officer', 'Diretor de Felicidade'), noise=('Oooh!', 'Ôôôh!'), dept='logistics', blood=None,
        wage=('minecraft:snowball', 1),
        work={'hire': True, 'jobs': {'hauling': 1.0}, 'shift': 'any', 'carry': 16, 'hands': False, 'walk': 1.0,
              'move': 'fly_free', 'attack': 'none'},
        array=20, turret='lookout', socket='none', alarm='entity.happy_ghast.ambient',
        neck=[0, 4.0, 0], decay=2.0, treadmill={'refuse': 'ghast'}, items=[]),
    'copper_golem': dict(
        title=('Records Clerk', 'Arquivista'), noise=('Tink.', 'Tim.'), dept='logistics', blood='oil',
        wage=('minecraft:copper_nugget', 1),
        work={'hire': True, 'jobs': {'hauling': 1.0, 'sorting': 1.5},
              'cannot': {'farming': 'card.cannot.farming_copper', 'security': 'card.cannot.security_copper'},
              'shift': 'any', 'carry': 1, 'carry_items': 16, 'hands': True, 'walk': 1.0, 'move': 'walk', 'attack': 'none'},
        array=30, turret='lookout', socket='none', alarm='entity.copper_golem.spin',
        neck=[0, 0.69, 0], decay=2.0, treadmill={'fe': 6}, items=['copper_nugget']),
    'nautilus': dict(
        title=('Diving Officer', 'Oficial de Mergulho'), noise=('Glub.', 'Glub.'), dept='marine', blood='cold',
        wage=('#minecraft:fishes', 1),
        work={'hire': True, 'jobs': {'hauling': 1.5, 'security': 1.0}, 'shift': 'any', 'carry': 2, 'hands': False,
              'walk': 0.8, 'move': 'water', 'attack': 'melee', 'melee': 3, 'water': True},
        array=10, turret='lookout', socket='none', alarm='entity.nautilus.ambient',
        neck=[0, 1.15, 0], decay=2.0, treadmill={'refuse': 'swimmer'}, items=[]),
    'zombie_nautilus': dict(
        title=('Salvage', 'Salvados'), noise=('Glurgh.', 'Glurgh.'), dept='marine', blood='grave',
        wage=('minecraft:rotten_flesh', 2),
        work={'hire': True, 'jobs': {'hauling': 1.5, 'security': 1.0}, 'cannot': {'sorting': 'card.cannot.sorting_undead'},
              'shift': 'night', 'carry': 2, 'hands': False, 'walk': 0.8, 'move': 'water', 'attack': 'melee', 'melee': 3,
              'water': True, 'quirks': ['burns']},
        array=20, turret='bite', socket='none', alarm=None,
        neck=[0, 1.15, 0], decay=2.0, treadmill={'refuse': 'swimmer'}, items=[]),
    'camel_husk': dict(
        title=('Desert Operations', 'Operações no Deserto'), noise=('Hhhrrr.', 'Hhhrrr.'), dept='logistics', blood='grave',
        wage=('minecraft:rabbit_foot', 1),
        work={'hire': True, 'jobs': {'hauling': 1.5}, 'cannot': {'sorting': 'card.cannot.sorting_undead'}, 'shift': 'any',
              'carry': 3, 'hands': False, 'walk': 2.4, 'move': 'walk', 'attack': 'none'},
        array=20, turret='lookout', socket='none', alarm='entity.camel_husk.ambient',
        neck=[0, 2.15, 0.7], decay=0.0, treadmill={'fe': 12}, items=[]),
    'parched': dict(
        title=('Compliance (Desert)', 'Compliance (Deserto)'), noise=('Rattle.', 'Tlec.'), dept='night_shift', blood='marrow',
        wage=('minecraft:bone', 1),
        work={'hire': True, 'jobs': {'hauling': 1.0, 'sorting': 1.0, 'farming': 1.0, 'security': 1.0}, 'shift': 'any',
              'carry': 1, 'hands': True, 'walk': 0.864, 'move': 'walk', 'attack': 'bow'},
        array=10, turret='bow_weakness', socket='none', alarm=None,
        neck=[0, 1.5, 0], decay=2.0, treadmill={'fe': 4}, items=[]),
    'sulfur_cube': dict(
        title=('Containment', 'Contenção'), noise=('Gloop.', 'Glup.'), dept='facilities', blood='brimstone',
        wage=('minecraft:slime_ball', 1),
        # its job (the Bouncer) and its hop are a later release's: the Lanyard says HR has no job description yet
        work=None,
        array=10, turret='lookout', socket='none', alarm='entity.sulfur_cube.squish',
        neck=None, decay=None, treadmill=None, items=[]),
}
assert list(SPECIES) == NEW_ARRIVALS

# The heads of the vanilla rows at the stations, as HeadJobs' own tables say (written into every row, so nothing moves).
_ARRAY = {**{s: 20 for s in ('zombie', 'husk', 'drowned')}, **{s: 40 for s in ('villager', 'zombie_villager', 'wandering_trader')},
          **{s: 60 for s in ('witch', 'piglin', 'piglin_brute')}, 'enderman': 80, 'wither_skeleton': 100, 'evoker': 120}
_TURRET = {'skeleton': 'bow', 'stray': 'bow_slowness', 'bogged': 'bow_poison', 'blaze': 'fireball', 'guardian': 'laser',
           'elder_guardian': 'laser', 'creeper': 'pulse', 'zombie': 'bite', 'husk': 'bite', 'drowned': 'bite',
           'snow_golem': 'snowball', 'witch': 'potion', 'evoker': 'fangs'}
_SOCKET = {'zombie': 'beat', 'husk': 'beat', 'drowned': 'beat', 'villager': 'count', 'enderman': 'stare'}
TURRET_ATTACKS = {'lookout', 'bow', 'bow_slowness', 'bow_poison', 'bow_weakness', 'fireball', 'laser', 'pulse', 'bite',
                  'snowball', 'potion', 'fangs'}  # HeadJobs.TURRET_ATTACKS
SOCKET_PROGRAMS = {'none', 'beat', 'count', 'stare'}  # HeadJobs.SOCKET_PROGRAMS

# Rivals and colleagues of the new arrivals (DeskJobs.RIVALS, DeskJobs.COLLEAGUES): the line each pair says at dawn.
PAIR_LINES = [
    ('desk.headinajar.rivals.night_auditor', 'One works when nobody watches. One weeps when everybody does.',
     'Um trabalha quando ninguém olha. O outro chora quando todo mundo olha.'),
    ('desk.headinajar.rivals.copper_golems',
     'There are now two copper golem departments. A merger is under review. Neither department has been told.',
     'Agora existem dois departamentos de golens de cobre. Uma fusão está em estudo. Nenhum dos dois foi avisado.'),
    ('desk.headinajar.colleagues.wellbeing_sleep', 'Wellbeing and Sleep have agreed to share a printer.',
     'Bem-Estar e Sono concordaram em dividir uma impressora.'),
]

# Where the Directory says to look for each, and the jar its head takes (a blank cell's hint).
HINTS = {
    'creaking': (('Found near: pale gardens, at night. It moves when you blink.', 'Any jar. It runs on sap: bring the Oil Tap.'),
                 ('Onde achar: jardins pálidos, à noite. Ele se mexe quando você pisca.',
                  'Qualquer pote. Funciona a seiva: traga a Torneira de Óleo.')),
    'happy_ghast': (('Found near: a dried ghast left in water, and a long wait.', 'Executive jar only. It was a ghast once.'),
                    ('Onde achar: um ghast seco deixado na água, e muita paciência.', 'Só Pote Executivo. Já foi um ghast.')),
    'copper_golem': (('Found near: copper with a carved pumpkin on it. Someone built it.', 'Any jar. Oil Tap, not a syringe.'),
                     ('Onde achar: cobre com uma abóbora esculpida em cima. Alguém montou.',
                      'Qualquer pote. Torneira de Óleo, não seringa.')),
    'nautilus': (('Found near: the open ocean, well below the boats.', 'Any jar. Keep it wet; it will remind you.'),
                 ('Onde achar: mar aberto, bem abaixo dos barcos.', 'Qualquer pote. Mantenha molhado; ele vai lembrar.')),
    'zombie_nautilus': (('Found near: under a drowned with a trident.', 'Any jar. Ask its rider to get down first.'),
                        ('Onde achar: debaixo de um afogado com tridente.', 'Qualquer pote. Peça para o cavaleiro descer antes.')),
    'camel_husk': (('Found near: deserts at night, under a husk.', 'Any jar. It has been dead for some time; the blood keeps.'),
                   ('Onde achar: desertos à noite, debaixo de um zumbi-múmia.',
                    'Qualquer pote. Já morreu faz tempo; o sangue se conserva.')),
    'parched': (('Found near: deserts, in the back seat of a camel husk.', 'Any jar. Marrow Drill, not a syringe.'),
                ('Onde achar: desertos, no banco de trás de um camelo-múmia.', 'Qualquer pote. Broca de Medula, não seringa.')),
    'sulfur_cube': (('Found near: sulfur caves, bouncing on whatever it ate.', 'Reinforced jar, whole. It is all head.'),
                    ('Onde achar: cavernas de enxofre, quicando com o que comeu.', 'Pote Reforçado, inteiro. É só cabeça.')),
}

SPECIES_LINES = [
    ('department.headinajar.competition', 'The Competition', 'A Concorrência'),
    ('department.headinajar.competition.line', 'A rival firm. It works when nobody is looking, so we look.',
     'Firma rival. Trabalha quando ninguém olha, então a gente olha.'),
    ('blood.headinajar.sap', 'Sap (bloodless)', 'Seiva (sem sangue)'),
    (R + 'lanyard.heart', 'Already employed by its heart.', 'Já trabalha para o próprio coração.'),
    (R + 'lanyard.creaking', 'This one has no heart. It is furniture with opinions.',
     'Este aqui não tem coração. É mobília com opinião.'),
    (R + 'card.cannot.farming_copper', 'It has no thumbs for a hoe. It has tried.', 'Não tem polegar para enxada. Já tentou.'),
    (R + 'card.cannot.security_copper', 'It runs away. It is in its nature.', 'Ele foge. Está na natureza dele.'),
    (R + 'treadmill.watched', "It only walks when nobody is watching. The belt's console is watching.",
     'Só anda quando ninguém está olhando. O painel da esteira está olhando.'),
    ('memo.headinajar.new_arrival',
     'A new species has joined the company. HR has made room in the directory. HR has not made room in the budget.',
     'Uma nova espécie entrou para a empresa. O RH abriu espaço no cadastro. O RH não abriu espaço no orçamento.'),
    ('advancements.headinajar.new_starters.title', 'New Starters', 'Recém-Chegados'),
    ('advancements.headinajar.new_starters.description', 'Sample every new arrival. Induction is mandatory.',
     'Colha sangue de cada novo contratado. A integração é obrigatória.'),
    ('gui.headinajar.directory.new_arrivals', 'New Arrivals', 'Novos Contratados'),
] + PAIR_LINES
for _p, _s in SPECIES.items():
    SPECIES_LINES.append((f'title.headinajar.minecraft.{_p}',) + _s['title'])
    SPECIES_LINES.append((f'{S}noise.{_p}',) + _s['noise'])
    (_we, _je), (_wp, _jp) = HINTS[_p]
    SPECIES_LINES.append((f'hint.headinajar.minecraft.{_p}.where', _we, _wp))
    SPECIES_LINES.append((f'hint.headinajar.minecraft.{_p}.jar', _je, _jp))
SPECIES_ARGS = {}


def _conds(path, items=()):
    """The new arrival's own condition, and one for each item its row names that the game may not have."""
    return [cond(path)] + [cond(i, 'item') for i in items]


def _merge_tag(write, path, extra):
    """Adds optional entries to a tag an earlier generator wrote (or starts it), keeping what is there."""
    import json
    full = os.path.join(RES, path)
    values = json.load(open(full, encoding='utf-8'))['values'] if os.path.exists(full) else []
    seen = {v if isinstance(v, str) else v['id'] for v in values}
    write(path, {'values': values + [e for e in extra if e['id'] not in seen]})


def _staff_row(path, s):
    from gen_p37_foundations import _wage
    assert s['turret'] in TURRET_ATTACKS and s['socket'] in SOCKET_PROGRAMS, path
    row = {'entity': 'minecraft:' + path, 'wage': _wage(s['wage']), 'noise': f'{S}noise.{path}'}
    if s['alarm']:
        assert s['turret'] == 'lookout', f'{path}: an attacker raises no alarm'
        row['alarm'] = 'minecraft:' + s['alarm']
    if s['work'] is not None:
        row['work'] = s['work']
    row.update(array=s['array'], turret=s['turret'], socket=s['socket'])
    # the row loads exactly when the creature (and any item it is paid in) is registered: an unregistered wage item
    # would make it a trophy (Wage.NONE)
    row['neoforge:conditions'] = _conds(path, s['items'])
    return row


def _station_fields(write):
    """Every vanilla row gets the head's Array rate, turret attack and socket program, as HeadJobs' tables say today."""
    import json
    folder = os.path.join(RES, 'data', 'headinajar', 'headinajar', 'staff')
    for name in sorted(f[:-5] for f in os.listdir(folder) if f.endswith('.json')):
        if name in SPECIES:
            continue
        path = f'data/headinajar/headinajar/staff/{name}.json'
        with open(os.path.join(RES, path), encoding='utf-8') as f:
            data = json.load(f)
        for k in ('array', 'turret', 'socket'):
            data.pop(k, None)
        data.update(array=_ARRAY.get(name, 10), turret=_TURRET.get(name, 'lookout'), socket=_SOCKET.get(name, 'none'))
        write(path, data)


def _species(EN, PT, write):
    import json
    # the staff rows: the new arrivals' own, then the station fields on every vanilla row
    for path, s in SPECIES.items():
        write(f'data/headinajar/headinajar/staff/{path}.json', _staff_row(path, s))
    _station_fields(write)

    # departments (The Competition is its own tag, outside the company's fourteen), blood, the saw's jars
    by_dept = {}
    for path, s in SPECIES.items():
        by_dept.setdefault(s['dept'], []).append(opt(path))
    for dept, entries in by_dept.items():
        _merge_tag(write, f'data/headinajar/tags/entity_type/department/{dept}.json', entries)
    by_blood = {}
    for path, s in SPECIES.items():
        if s['blood']:
            by_blood.setdefault(s['blood'], []).append(opt(path))
    for blood, entries in by_blood.items():
        _merge_tag(write, f'data/headinajar/tags/entity_type/blood/{blood}.json', entries)
    _merge_tag(write, 'data/headinajar/tags/entity_type/saw/executive_jar.json', [opt('happy_ghast')])
    _merge_tag(write, 'data/headinajar/tags/entity_type/saw/head_only.json', [opt('sulfur_cube')])
    _merge_tag(write, 'data/headinajar/tags/entity_type/saw/reinforced_jar.json', [opt('sulfur_cube')])

    # host files: where the jar sits (the head's place in the creature's model), blood and decay; no abilities yet
    for path, s in SPECIES.items():
        if s['neck'] is None:
            continue
        write(f'data/headinajar/headinajar/host/{path}.json', {
            'entity': 'minecraft:' + path, 'neck': s['neck'], 'blood': s['blood'] or 'warm', 'decay_per_minute': s['decay'],
            'neoforge:conditions': [cond(path)]})

    # the Treadmill: one entry each, each with its own condition (an entry for an unregistered id is an error)
    tread = 'data/headinajar/data_maps/entity_type/treadmill.json'
    with open(os.path.join(RES, tread), encoding='utf-8') as f:
        values = json.load(f)['values']
    for path, s in SPECIES.items():
        if s['treadmill'] is not None:
            values['minecraft:' + path] = {'neoforge:conditions': [cond(path)], **s['treadmill']}
    write(tread, {'values': dict(sorted(values.items()))})

    # New Starters: hidden, loaded only where at least one new arrival is registered
    write('data/headinajar/advancement/new_starters.json', {
        'parent': 'headinajar:out_for_blood',
        'criteria': {'done': {'trigger': 'headinajar:event', 'conditions': {'event': 'new_starters'}}},
        'display': {'icon': {'id': 'headinajar:blood_sample'}, 'title': {'translate': 'advancements.headinajar.new_starters.title'},
                    'description': {'translate': 'advancements.headinajar.new_starters.description'}, 'frame': 'task',
                    'show_toast': True, 'announce_to_chat': False, 'hidden': True},
        'neoforge:conditions': [{'type': 'neoforge:or', 'values': [cond(p) for p in NEW_ARRIVALS]}]})


# ---------------------------------------------------------------------------------------------------------------
# MACHINES (1.2, the machine tables): every new arrival at every tool and machine that is not a species row: the
# Embalming Fluid, Neural Lace and honeycomb, the Body Locker, the Galvanic Pile, Saline Mixer, Charging Dock, Tesla
# Node, mecha refusals, bundles, Exit Interview reasons, memos and the placard. Lines in MACHINES_LINES, files written
# by _machines().
#
# The Java side is supplier/SuppliedMachines.java (and the machines' own classes, each marked 0.8.0). Reason keys are the
# Refusals' ids under refusal.headinajar.
MACHINES_LINES = [
    # Embalming Fluid and Neural Lace on a body that never rots (its host file decays at 0): book 5.1, 6.1, S58
    (R + 'host.embalm_creaking', 'It does not rot. It has never been alive enough.',
     'Não apodrece. Nunca esteve vivo o bastante para isso.'),
    (R + 'host.embalm_camel_husk', 'Already embalmed. The desert did it for free.',
     'Já veio embalsamado. O deserto fez de graça.'),
    (R + 'host.embalm_copper_golem', 'Copper does not rot. It weathers. Try honeycomb.',
     'Cobre não apodrece. Oxida. Tente favo de mel.'),
    (R + 'host.lace_creaking', 'There is nothing to lace. It is all grain.',
     'Não tem nervo onde pôr renda. É tudo veio de madeira.'),
    (R + 'host.lace_camel_husk', 'Nothing left to preserve. Keep the Neural Lace.',
     'Não sobrou nada para conservar. Guarde a Renda Neural.'),
    (R + 'host.lace_copper_golem', 'No nerves to lace. Try honeycomb.', 'Sem nervos para a renda. Tente favo de mel.'),
    # honeycomb on a new arrival's body (1.2, S58; 4.6 for a golem already waxed)
    (R + 'host.honeycomb', 'Honeycomb is for copper. HR has had to say this more than once.',
     'Favo de mel é para cobre. O RH já teve que repetir isso mais de uma vez.'),
    (R + 'host.honeycomb_wood', 'It is wood, not copper.', 'É madeira, não cobre. Cupim é com outro departamento.'),
    (R + 'host.honeycomb_again', 'Already rust-proofed. Twice is a coating, not a benefit.',
     'Já tem antiferrugem. Duas vezes é verniz, não benefício.'),
    # the Body Locker (S60; 5.1 for the creaking)
    (R + 'locker.too_happy', 'It does not fit in the locker. Nothing that happy fits in the locker.',
     'Não cabe no armário. Felicidade assim não cabe em armário nenhum.'),
    (R + 'locker.heart', 'The Garden reclaims its staff at dawn. We cannot keep what is not ours.',
     'O Jardim recolhe sua equipe ao amanhecer. Não guardamos o que não é nosso.'),
    (M + 'locker_stored_copper', '%s stored. It will keep. Copper keeps better in a cupboard.',
     'Entrada registrada: %s. Cobre se conserva melhor no armário.'),
    # the Galvanic Pile, Saline Mixer, Charging Dock, Surgical Wrench (S61, S63)
    (R + 'pile.resin', "The pile eats meat. Resin is a tree's problem.", 'A pilha come carne. Resina é problema da árvore.'),
    (R + 'mixer.dried_ghast', 'It wants saline, not to be saline. For now, plain water will do.',
     'Ele quer soro, não virar soro. Por enquanto, água serve.'),
    (R + 'dock.copper', 'Copper conducts. It does not store. The dock does.',
     'Cobre conduz. Não armazena. Quem armazena é a base.'),
    (R + 'wrench.copper_golem', "Facilities' current is not weather. Please wait for a storm.",
     'A corrente das Instalações não é clima. Aguarde uma tempestade.'),
    # a mecha (S64)
    (R + 'mecha.copper', 'Copper is soft. Mecha Plating is not.', 'Cobre é mole. Placa de Mecha, não.'),
    (R + 'mecha.harness', 'Harnesses are for happy ghasts. This is senior management.',
     'Arreio é para ghast feliz. Isto aqui é a diretoria.'),
    (R + 'mecha.nautilus_armor', 'That armour is for a nautilus. The %s is not a nautilus.',
     'Essa armadura é de náutilo. %s não é náutilo.'),
    # statues (1.2 at the Cerebral Array; S47 at the saw)
    (R + 'array.statue', 'Statues do not attend meetings. They have been to enough.',
     'Estátua não vai a reunião. Já ficou parada em reuniões demais.'),
    (R + 'saw.statue', 'That is a statue of a colleague. Use an axe, not a saw.',
     'Isso é a estátua de um colega. Use machado, não serra.'),
    # bundles (1.2, S65); the Head Satchel carries only your own head, so it is not named
    (R + 'bundle.colleague', 'A colleague is not luggage. Carry it by hand, like a professional.',
     'Colega não é bagagem. Leve na mão, como um profissional.'),
    # Exit Interview reasons the code can see (1.2, S95): "Reason for leaving: %s."
    (S + 'reason.heart_broken', 'its heart was broken', 'teve o coração partido'),
    (S + 'reason.heart_far', 'wandered too far from its heart', 'longe dos olhos, longe do coração'),
    (S + 'reason.retired', 'retired in place', 'aposentou-se no próprio posto'),
    (S + 'reason.dried_out', 'dried out', 'ficou a ver navios'),
]
_FRAMES = (['Hopper', 'Bulwark', 'Chimera', 'Wheelhouse', 'Skitter', 'Colossus', 'Kite', 'Executive'],
           ['Saltador', 'Baluarte', 'Quimera', 'Casa sobre Rodas', 'Rastejador', 'Colosso', 'Pipa', 'Executivo'])
MACHINES_ARGS = {
    M + 'locker_stored_copper': lambda i: [[('Copper Golem', 'Golem de Cobre')[i]], [('Bartholomew', 'Bartolomeu')[i]]],
    R + 'mecha.nautilus_armor': lambda i: [[f] for f in _FRAMES[i]],
}
# The new rows' Lookout alarms, each a sound event of 26.2 (net/minecraft/sounds/SoundEvents.java), so a row never names
# a sound that exists only in a supplier (S88). The rows are gated, and the turret falls back to the creature's ambient
# sound if the game has no such sound all the same.
SOUNDS_262 = {'entity.creaking.activate', 'entity.happy_ghast.ambient', 'entity.copper_golem.spin', 'entity.nautilus.ambient',
              'entity.camel_husk.ambient', 'entity.sulfur_cube.squish'}


def _machines(EN, PT, write):
    for path, s in SPECIES.items():
        assert s['alarm'] is None or s['alarm'] in SOUNDS_262, f'{path}: alarm {s["alarm"]} is not a 26.2 sound event'

    # the company's own item tags for supplied things the game has no tag for (every entry optional)
    write('data/headinajar/tags/item/supplied/resin.json', {'values': [opt('resin_clump'), opt('resin_block')]})
    write('data/headinajar/tags/item/supplied/copper_parts.json', {'values': [opt('#minecraft:copper')] + [opt(i) for i in (
        'copper_nugget', 'copper_helmet', 'copper_chestplate', 'copper_leggings', 'copper_boots', 'copper_horse_armor')]})
    write('data/headinajar/tags/item/supplied/nautilus_armor.json', {'values': [opt(f'{m}_nautilus_armor') for m in (
        'copper', 'iron', 'golden', 'diamond', 'netherite')]})


# ---------------------------------------------------------------------------------------------------------------
# PLUMBING (1.3 to 1.6): capability providers, the slot adapter, the Unseating Policy, stored records and discontinued
# lines. Lines in PLUMBING_LINES, files written by _plumbing().
PLUMBING_LINES = [
    # 1.3: the copper chests and shelves staff can now reach, and the statue that is not a stop
    (M + 'clipboard.pick_up_copper', 'Pick-Up. A copper chest is a chest. HR has checked with the copper golems.',
     'Coleta. Baú de cobre é baú. O RH conferiu com os golens de cobre.'),
    (M + 'clipboard.drop_off_copper', 'Drop-Off. A copper chest is a chest. HR has checked with the copper golems.',
     'Entrega. Baú de cobre é baú. O RH conferiu com os golens de cobre.'),
    (R + 'clipboard.statue', 'Statues are not a stop. They are a monument.', 'Estátua não é parada. É monumento.'),
    # 1.5: the Unseating Policy
    (M + 'lance.pilot', 'Restraint held. The lance has been noted.', 'O cinto aguentou. A lança foi anotada.'),
    (M + 'lance.lunk', 'The Lunk does not do falling over.', 'O Brutamontes não faz essa coisa de cair.'),
    (M + 'lance.pod', 'The pod is glazed. Passengers stay in.', 'A cápsula é envidraçada. Passageiro fica dentro.'),
    (M + 'lance.held', 'The neck seal held. Server policy: heads stay on.',
     'O selo do pescoço aguentou. Política do servidor: cabeça fica no lugar.'),
    (M + 'lance.socket', 'The clamps held. Please stop stabbing the switchboard.',
     'As garras aguentaram. Por favor, pare de espetar a central telefônica.'),
    (M + 'lance.unseated', 'Unseated by %s. %s is where you left her.', 'Desmontado por %s. %s está onde você a deixou.'),
    (M + 'lance.unseated_shell', 'Unseated. The %s kept going for a bit.', 'Desmontado. O %s seguiu sem você por um tempo.'),
    (M + 'lance.by', "%s's %s", '%2$s de %1$s'),
    (M + 'lance.collar', 'The collar holds. Secondees are bolted on.', 'A coleira segura. Destacado vem parafusado.'),
    ('memo.headinajar.unseated',
     'You were unseated at %s, %s, %s. The company reminds you that bodies are hired with the head on top.',
     'Você foi desmontado em %s, %s, %s. A empresa lembra que corpos são contratados com a cabeça em cima.'),
    ('advancements.headinajar.relieved_of_your_post.title', 'Relieved of Your Post', 'Dispensado do Posto'),
    ('advancements.headinajar.relieved_of_your_post.description', 'Be unseated from a host by a lance. The body stayed on.',
     'Ser desmontado de um hospedeiro por uma lança. O corpo ficou no posto.'),
    # 1.6: discontinued lines
    ('item.headinajar.jarred_head.discontinued', 'Discontinued model: %s. Its supplier has left the building.',
     'Modelo fora de linha: %s. O fornecedor saiu pela porta dos fundos.'),
    (R + 'head.discontinued', 'Head Office cannot seat a creature this world has never heard of.',
     'A Sede não acomoda uma criatura de que este mundo nunca ouviu falar.'),
    ('jade.headinajar.on_leave', 'On leave.', 'De licença.'),
    ('memo.headinajar.discontinued',
     'The following staff have been seconded to a version of the world that no longer exists: %s. Their desks have been cleared.',
     'Os funcionários a seguir foram destacados para uma versão do mundo que não existe mais: %s. As mesas deles foram esvaziadas.'),
    ('memo.headinajar.discontinued.entry', '%s (%s)', '%s (%s)'),
    ('memo.headinajar.discontinued.and', ', ', ', '),
    ('memo.headinajar.discontinued.more', ' and %s more', ' e mais %s'),
]
# Action-bar lines' longest real arguments: the zombie horseman's spear and a named cow; the longer shell.
PLUMBING_ARGS = {
    M + 'lance.unseated': lambda i: [[("Zombie's Iron Spear", 'Lança de Ferro de Zumbi')[i], 'Bessie']],
    M + 'lance.unseated_shell': lambda i: [[('Spider Walker', 'Andador Aranha')[i]]],
    M + 'lance.by': lambda i: [[('Zombified Piglin', 'Piglin Zumbificado')[i], ('Netherite Spear', 'Lança de Netherite')[i]]],
}

# The seven spears the supplier delivers (the lance's advancement loads where any of them is registered).
SPEARS = ['wooden_spear', 'stone_spear', 'copper_spear', 'iron_spear', 'golden_spear', 'diamond_spear', 'netherite_spear']


def _plumbing(EN, PT, write):
    # Relieved of Your Post: hidden, under Hostile Takeover, loaded only where a spear is registered
    write('data/headinajar/advancement/relieved_of_your_post.json', {
        'parent': 'headinajar:hosted',
        'criteria': {'done': {'trigger': 'headinajar:event', 'conditions': {'event': 'relieved_of_your_post'}}},
        'display': {'icon': {'id': 'headinajar:life_support_jar'},
                    'title': {'translate': 'advancements.headinajar.relieved_of_your_post.title'},
                    'description': {'translate': 'advancements.headinajar.relieved_of_your_post.description'}, 'frame': 'task',
                    'show_toast': True, 'announce_to_chat': False, 'hidden': True},
        'neoforge:conditions': [{'type': 'neoforge:or', 'values': [cond(s, 'item') for s in SPEARS]}]})


# ---------------------------------------------------------------------------------------------------------------
def run(EN, PT, write, shaped):
    from gen_p43_moments import OURS
    lines = LINES + SPECIES_LINES + MACHINES_LINES + PLUMBING_LINES
    keys = [k for k, _e, _p in lines]
    assert len(keys) == len(set(keys)), sorted(k for k in keys if keys.count(k) > 1)
    taken = [key for key in keys if key in EN]
    assert not taken, f'already in the language files: {taken}'
    for key, en, pt in lines:
        EN[key], PT[key] = en, pt
    OURS.update(keys)
    _species(EN, PT, write)
    _machines(EN, PT, write)
    _plumbing(EN, PT, write)
    import gen_p39_dna as D
    saved = D.ACTION_BAR_ARGS
    D.ACTION_BAR_ARGS = {**ACTION_BAR_ARGS, **SPECIES_ARGS, **MACHINES_ARGS, **PLUMBING_ARGS}
    try:
        D._check_action_bar(EN, PT, keys)
    finally:
        D.ACTION_BAR_ARGS = saved

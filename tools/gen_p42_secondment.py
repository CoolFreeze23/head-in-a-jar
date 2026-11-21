"""Lines and data for the 0.7.1 release (Secondment): the Secondment Collar, seconded heads on host bodies, team sync,
the collar's canister and the Time Clock's Broth tank, and the Redundancy Package. Imported by gen_data.py after
gen_p41_workforce and before the handbook generator (gen_p27_clarity), whose text check and handbook read the final
lines and entries.

It writes:
- the Secondment Collar's recipe and item model, the Redundancy Package's name;
- three advancements, one memo, the sixteen pairing lines DOLORES says once per player;
- the clasp's sound event and subtitle;
- every new language line, en_us and pt_br; each action-bar line is checked under 400 px with its longest real
  arguments, and each floating line over a worker under 200 px in two lines, in both languages;
- the handbook entry "Secondment" and a line on the Neural Lace's page.

The sections marked for a part of the work (MORE_BRAIN, MORE_BROTH, MORE_PACKAGE, MORE_LOOK) are where each part adds
the lines it needs beyond the core table.
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..')
RES = os.path.join(ROOT, 'src', 'main', 'resources')
MC = 'minecraft:'

R = 'refusal.headinajar.secondment.'
M = 'message.headinajar.secondment.'

# ---------------------------------------------------------------------------------------------------------------
# The core table: (key, en, pt).
LINES = [
    # the item, the entity, the sound
    ('item.headinajar.secondment_collar', 'Secondment Collar', 'Coleira de Destacamento'),
    ('item.headinajar.secondment_collar.how', 'Fit to a host, then seat a jarred head on it.',
     'Coloque num hospedeiro e encaixe uma cabeça no pote.'),
    ('item.headinajar.secondment_collar.note', 'Broth sold separately.', 'Caldo vendido à parte.'),
    ('entity.headinajar.redundancy_package', 'Redundancy Package', 'Pacote de Demissão'),
    ('subtitles.headinajar.staff.collar_fitted', 'Collar fitted', 'Coleira colocada'),

    # refusals, in the book's order
    (R + 'not_host', 'The collar fits a host. Make one with the %s.', 'A coleira é para hospedeiros. Faça um com a %s.'),
    (R + 'machine', 'Machines take your jar, not a collar. A mob head would not know the controls.',
     'Máquinas levam o seu pote, não coleira. Cabeça de bicho não pilota.'),
    (R + 'ridden', 'Someone is already in that body.', 'Já tem alguém nesse corpo.'),
    (R + 'already', 'This host already wears a collar.', 'Este hospedeiro já está de coleira.'),
    (R + 'no_collar', 'Fit a %s first. The head needs somewhere to sit.', 'Coloque uma %s antes. A cabeça precisa de onde sentar.'),
    (R + 'player_head', 'Seconding colleagues is not policy. A mob head only.', 'Destacar colegas não é política. Só cabeça de bicho.'),
    (R + 'trophy', 'That is a trophy, not a colleague. Bring a jarred head.', 'Isso é troféu, não colega. Traga uma cabeça no pote.'),
    (R + 'second_head', 'One head per body. HR is very clear.', 'Uma cabeça por corpo. O RH foi bem claro.'),
    (R + 'dragon', 'The Dragon is on the Board. It does not take a field position.', 'O Dragão é do Conselho. Não aceita cargo de campo.'),
    (R + 'creeper', 'A creeper head goes off once. It cannot be seconded.', 'Cabeça de creeper só explode uma vez. Não dá para destacar.'),
    (R + 'boarding_seconded', 'This body has a head on staff. Unseat it first (Sneak and Use it).',
     'Este corpo já tem cabeça na equipe. Tire-a antes (agache e use).'),
    (R + 'cap', 'Headcount frozen: %s staff on file. Let someone go first.', 'Quadro congelado: %s funcionários. Dispense alguém antes.'),
    (R + 'others', '%s reports to %s.', '%s responde a %s.'),
    (R + 'others_head', "That head is on someone else's staff: %s. Ask for their trust.", 'Essa cabeça é da equipe de %s. Peça a confiança.'),
    (R + 'seated_collar', 'Unseat the head first: Sneak and Use the host.', 'Tire a cabeça primeiro: agache e use o hospedeiro.'),
    (R + 'canister_full', 'The canister is full. The rest would go down the drain.', 'O cantil está cheio. O resto iria pelo ralo.'),
    (R + 'glass_full', 'The jar is sound. Keep the patch for a worse day.', 'O pote está inteiro. Guarde o remendo para um dia pior.'),
    (R + 'disabled', 'Secondments are not approved on this server.', 'Destacamentos não são aprovados neste servidor.'),
    (R + 'let_go', 'Secondees are not let go. Unseat the head instead: Sneak and Use.',
     'Destacado não se dispensa. Tire a cabeça: agache e use.'),
    (R + 'saw', '%s is on secondment. Unseat the head first: Sneak and Use.',
     '%s está em destacamento. Tire a cabeça antes: agache e use.'),

    # what the player is told
    (M + 'fitted', 'Collar fitted.', 'Coleira colocada.'),
    (M + 'locked', 'Jar locks on.', 'O pote trava no lugar.'),
    (M + 'unseated', '%s unseated. File and grade kept.', '%s fora do corpo. Ficha e cargo mantidos.'),
    (M + 'collar_off', 'Collar removed. It goes back in the drawer.', 'Coleira retirada. Volta para a gaveta.'),
    (M + 'topped', 'Canister topped up: %s mB of broth.', 'Cantil cheio: %s mB de caldo.'),
    (M + 'patched', 'Jar mended: glass %s/%s.', 'Pote remendado: vidro %s/%s.'),
    (M + 'status', 'Team sync %s%%. Broth %s mB.', 'Sincronia %s%%. Caldo %s mB.'),
    (M + 'tangled', 'Package tangled. Brace for landing.', 'Pacote embolou. Preparar para o impacto.'),
    ('message.headinajar.lace_needs_sync', 'The lace needs at least 50% sync, or team sync, to take.',
     'A renda precisa de 50% de sincronia, ou de sincronia de equipe.'),

    # the file
    ('staff.headinajar.history.seconded', 'Seconded to a %s body day %s.', 'Destacamento para um corpo de %s, dia %s.'),
    ('staff.headinajar.history.unseconded', 'Taken off a %s body day %s.', 'Retirada de um corpo de %s, dia %s.'),
    ('staff.headinajar.history.redundancy', 'Survived the loss of a %s body day %s.',
     'Perdeu um corpo de %s e seguiu no cargo, dia %s.'),

    # DOLORES
    ('chat.headinajar.redundancy', "Noggin & Sons regrets the loss of %s's body at %s, %s, %s. %s is in the post.",
     'A Noggin & Sons lamenta a perda do corpo de %s em %s, %s, %s. %s segue pelo correio.'),
    ('memo.headinajar.secondment',
     'A head and a body, together at last. Neither knows the other yet: they bond as they work, and the body\'s tricks '
     'open at 50% team sync. Keep the broth coming.',
     'Uma cabeça e um corpo, enfim juntos. Ainda não se conhecem: criam laços trabalhando, e os truques do corpo abrem '
     'com 50% de sincronia de equipe. Mantenha o caldo chegando.'),
]

# Floating lines over a secondee (staff.headinajar.say.<id>), at most two lines of 200 px: (id, en, pt).
SAY = [
    ('secondment.new_team', 'A new body is a new team.', 'Corpo novo, equipe nova.'),
    ('secondment.dry', 'Out of broth: standing by.', 'Sem caldo: aguardando.'),
    ('secondment.dry_home', 'Out of broth: going back to the office.', 'Sem caldo: voltando para o escritório.'),
    ('secondment.no_explode', 'Staff do not explode. Policy 9.2.', 'Funcionários não explodem. Norma 9.2.'),
    # a swimmer (a squid body) at a clock with no water within reach of it
    ('secondment.clock_on_land', 'The clock is on dry land. I am not.', 'O relógio fica em terra firme. Eu não.'),
]

# The sixteen pairings, head then body: (pair, en, pt). Said once per player when the jar locks on (Secondment.PAIRS).
PAIRINGS = [
    ('villager_iron_golem', 'Middle management. It will see you now.', 'Gerência intermediária. Ela vai recebê-lo agora.'),
    ('skeleton_horse', 'We appear to have reinvented the skeleton horse.', 'Parece que reinventamos o cavalo esqueleto.'),
    ('zombie_horse', 'And the zombie horse. R&D is thrilled.', 'E o cavalo zumbi. P&D está radiante.'),
    ('skeleton_spider', 'Security has taken the stairs. All of them.', 'A Segurança subiu pelas escadas. Por todas.'),
    ('enderman_pig', 'Please do not make eye contact with the pig.', 'Por favor, não encare o porco.'),
    ('cow_creeper', 'Fair warning is company policy.', 'Aviso prévio é política da empresa.'),
    ('chicken_ghast', 'A very large chicken. Do not ask about the eggs.', 'Uma galinha enorme. Não pergunte dos ovos.'),
    ('zombie_ghast', 'Logistics is now airborne.', 'A Logística agora é aérea.'),
    ('blaze_strider', 'Facilities, Nether branch.', 'Instalações, filial do Nether.'),
    ('wolf_spider', 'Good boy. All eight legs of him.', 'Bom garoto. Todas as oito patas.'),
    ('cat_iron_golem', 'Security, with whiskers.', 'Segurança, com bigodes.'),
    ('warden_chicken', 'The most frightening thing in the building.', 'A coisa mais assustadora do prédio.'),
    ('sniffer_pig', 'Truffle season.', 'Temporada de trufas.'),
    ('villager_llama', 'Field Sales.', 'Vendas de campo.'),
    ('wandering_trader_camel', 'The bazaar comes to you.', 'O bazar vem até você.'),
    ('piglin_strider', 'Accounts Receivable.', 'Contas a Receber.'),
]

# ---------------------------------------------------------------------------------------------------------------
# Each part's own lines: (key, en, pt). Floating lines go to MORE_SAY as (id, en, pt) with the part named in a comment.
MORE_BRAIN = []    # team sync, the body's abilities at work
MORE_BROTH = [    # the canister, the Time Clock's Broth tab and tank
    ('gui.headinajar.time_clock.broth', 'Broth', 'Caldo'),
    ('gui.headinajar.time_clock.broth_tank', 'Broth tank', 'Tanque de caldo'),
    ('gui.headinajar.time_clock.broth_mb', '%s / %s mB', '%s / %s mB'),
    ('gui.headinajar.time_clock.canister_mb', '%s mB', '%s mB'),
    ('gui.headinajar.time_clock.canister_line', 'Canister: %s / %s mB', 'Cantil: %s / %s mB'),
    ('gui.headinajar.time_clock.broth_hint',
     'Pour a bucket on the clock, or pipe it in. Seconded heads drink it at the punch. The tank gives nothing back.',
     'Despeje um balde no relógio ou ligue um cano. As cabeças destacadas bebem no ponto. O tanque não devolve nada.'),
    ('gui.headinajar.time_clock.no_secondees', 'No seconded heads on this roster. The broth keeps better than they do.',
     'Nenhuma cabeça destacada neste quadro. O caldo dura mais do que elas.'),
    ('refusal.headinajar.clock.broth_full', 'The Broth tank is full. Spillage comes out of your wages.',
     'O tanque de caldo está cheio. Derramamento sai do seu salário.'),
    ('message.headinajar.clock.broth_poured', 'Poured. Broth tank: %s of %s mB.', 'Despejado. Tanque de caldo: %s de %s mB.'),
]
MORE_PACKAGE = [  # the Redundancy Package, the jar's glass
    # the Exit Interview's reason for a head lost with a body that fell out of the world
    ('staff.headinajar.reason.void', 'the void', 'o vazio'),
]
MORE_LOOK = [      # the collar drawn, Jade
    ('jade.headinajar.secondee', '%s head on a %s body · team sync %s%%', 'Cabeça de %s num corpo de %s · sincronia de equipe %s%%'),
    ('jade.headinajar.secondee.upkeep', 'Broth %s mB · Glass %s/%s', 'Caldo %s mB · Vidro %s/%s'),
    ('jade.headinajar.secondee.vacant', 'Collar fitted, position vacant · Broth %s mB', 'Coleira colocada, vaga em aberto · Caldo %s mB'),
    # the kind of a secondee on /headoffice staff list (in the brackets after its name)
    ('command.headinajar.staff.secondee', '%s head, %s body, team sync %s%%', 'cabeça de %s, corpo de %s, sincronia de equipe %s%%'),
]
MORE_SAY = []
MORE_ACTION_BAR_ARGS = {
    # the saw on a seated secondee (a long name)
    R + 'saw': lambda i: [['Genivaldo']],
    # broth
    'message.headinajar.clock.broth_poured': lambda i: [['4000', '4000']],
}

ADVANCEMENTS = [  # (id, parent, icon, hidden, en title, en description, pt title, pt description)
    ('secondment', 'hosted', 'headinajar:secondment_collar', False, 'Secondment',
     'Seat a mob head on a host with a Secondment Collar', 'Destacamento',
     'Coloque uma cabeça de bicho num hospedeiro com uma Coleira de Destacamento'),
    ('team_spirit', 'secondment', 'headinajar:neural_lace', False, 'Team Spirit', 'A secondee reaches 100% team sync',
     'Espírito de Equipe', 'Um destacado chega a 100% de sincronia de equipe'),
    ('redundancy', 'secondment', 'minecraft:gold_ingot', False, 'Redundancy Package',
     'Have a seconded head land safely under its canopy', 'Pacote de Demissão',
     'Faça uma cabeça destacada pousar a salvo sob o seu dossel'),
]

# Action-bar lines with arguments: key -> lambda lang_index: [argument lists] (the longest real ones).
NAME = ('Bartholomew', 'Bartolomeu')
BODY = ('Zombified Piglin', 'Piglin-Zumbificado')
ACTION_BAR_ARGS = {
    R + 'not_host': lambda i: [[('Surgical Saw', 'Serra Cirúrgica')[i]]],
    R + 'no_collar': lambda i: [[('Secondment Collar', 'Coleira de Destacamento')[i]]],
    R + 'cap': lambda i: [['1000']],
    R + 'others': lambda i: [[NAME[i], 'Supercalifragili'], [BODY[i], 'Supercalifragili']],
    R + 'others_head': lambda i: [['Supercalifragili']],
    M + 'unseated': lambda i: [[NAME[i]]],
    M + 'topped': lambda i: [['500']],
    M + 'patched': lambda i: [['30', '30']],
    M + 'status': lambda i: [['100', '500']],
    'refusal.headinajar.lanyard.unknown': lambda i: [[BODY[i]]],
}

# The clasp (tools/make_sounds.py makes the file): event -> (file, subtitle key under subtitles.headinajar.)
SOUNDS = {'staff.collar_fitted': ('staff/collar_fitted', 'staff.collar_fitted')}


# ---------------------------------------------------------------------------------------------------------------
def _recipes(shaped):
    shaped('secondment_collar', ['III', 'WCW', 'IBI'], dict(I='headinajar:surgical_steel_ingot', W='headinajar:neuro_copper_wire',
                                                           C='headinajar:synapse_chip', B='glass_bottle'))


def _models(write):
    """The collar's item model: the art's icon once it is painted (art/textures/item/secondment_collar.png); until then
    a stand-in drawn from the Neural Lace's icon."""
    tex = os.path.join(RES, 'assets', 'headinajar', 'textures', 'item', 'secondment_collar.png')
    layer = 'headinajar:item/secondment_collar' if os.path.exists(tex) else 'headinajar:item/neural_lace'
    write('assets/headinajar/models/item/secondment_collar.json', {'parent': 'minecraft:item/generated',
                                                                   'textures': {'layer0': layer}})


def _advancements(EN, PT, write):
    for adv, parent, icon, hidden, en_t, en_d, pt_t, pt_d in ADVANCEMENTS:
        write(f'data/headinajar/advancement/{adv}.json', {
            'parent': f'headinajar:{parent}', 'criteria': {'done': {'trigger': 'headinajar:event', 'conditions': {'event': adv}}},
            'display': {'icon': {'id': icon}, 'title': {'translate': f'advancements.headinajar.{adv}.title'},
                        'description': {'translate': f'advancements.headinajar.{adv}.description'}, 'frame': 'task',
                        'show_toast': True, 'announce_to_chat': not hidden, 'hidden': hidden}})
        for table, t, d in ((EN, en_t, en_d), (PT, pt_t, pt_d)):
            table[f'advancements.headinajar.{adv}.title'], table[f'advancements.headinajar.{adv}.description'] = t, d


def _sounds(write):
    path = os.path.join(RES, 'assets', 'headinajar', 'sounds.json')
    with open(path, encoding='utf-8') as f:
        table = json.load(f)
    for event, (file, subtitle) in SOUNDS.items():
        assert os.path.exists(os.path.join(RES, 'assets', 'headinajar', 'sounds', file + '.ogg')), file
        table[event] = {'sounds': [{'name': f'headinajar:{file}', 'attenuation_distance': 16}],
                        'subtitle': f'subtitles.headinajar.{subtitle}'}
    write('assets/headinajar/sounds.json', table)


def _check_say(EN, PT):
    from gen_p27_clarity import wrap
    import gen_p41_workforce as W
    over = []
    for sid, _en, _pt in SAY + MORE_SAY:
        key = f'staff.headinajar.say.{sid}'
        for i, table in enumerate((EN, PT)):
            if len(wrap(table[key], W.SAY_PX)) > 2:
                over.append((key, ('en', 'pt')[i], table[key]))
    assert not over, f'{len(over)} floating lines take more than two lines of {W.SAY_PX} px: {over[:5]}'


# ---------------------------------------------------------------------------------------------------------------
# The handbook: "Secondment", after the workforce's entries in Temporary Staffing (the memo opens it), and a line on
# the Sync page about team sync and the lace.
SECONDMENT_ENTRIES = [
    {'cat': 'staffing', 'id': 'secondment', 'sort': 18, 'icon': 'headinajar:secondment_collar',
     'en': 'Secondment', 'pt': 'Destacamento',
     'pages': [
         ('spotlight', 'headinajar:secondment_collar',
          'Use a Secondment Collar on a host, with hands. Then Use a jarred head on it: the jar twists on and the head '
          'drives the body. It is staff now, on its own file, and looks for a Time Clock of yours.',
          'Use uma Coleira de Destacamento num hospedeiro, com mãos. Depois use uma cabeça no pote nele: o pote trava '
          'e a cabeça dirige o corpo. Agora é da equipe, com a própria ficha, e procura um Relógio de Ponto seu.'),
         ('text',
          '$(l)The head is skills$(): its jobs and how fast, its wage, its turret post (an attacking head shoots from '
          'the jar), its temper.$(br2)$(l)The body is hardware$(): how it moves, hands, carry, health, and its tricks.',
          '$(l)A cabeça é a competência$(): tarefas e ritmo, salário, posto de torre (cabeça que ataca atira do pote), '
          'temperamento.$(br2)$(l)O corpo é o equipamento$(): como anda, mãos, carga, vida e os truques.'),
         ('form', ('Form 13-S: Team Sync', 'Formulário 13-S: Sincronia'),
          '1% every 24 s of work, on shift with a card; half when the blood does not mix (an oil body takes any '
          'head).$(br)25%: the body\'s traits$(br)50%: its Ability; the lace takes$(br)75%: its Secondary$(br)100%: '
          '10% faster, carries 10% more',
          '1% a cada 24 s de trabalho, no turno e com cartão; metade se o sangue não combina (corpo a óleo aceita '
          'qualquer cabeça).$(br)25%: traços do corpo$(br)50%: a Habilidade; a renda pega$(br)75%: a Secundária'
          '$(br)100%: 10% mais rápido e mais carga'),
         ('text',
          'The collar\'s canister holds 500 mB: a mB every 6 seconds, about 50 minutes. It tops up at each punch-in '
          'from the clock\'s Broth tank. Dry, the head sags and the body stands by.$(br2)The body still rots. A mixed '
          'team needs Embalming Fluid before the lace.',
          'O cantil da coleira leva 500 mB: 1 mB a cada 6 segundos, uns 50 minutos. Enche a cada ponto batido, do '
          'tanque de Caldo do relógio. Seca, a cabeça murcha e o corpo aguarda.$(br2)O corpo ainda apodrece. Equipe '
          'mista precisa de Fluido de Embalsamar antes da renda.'),
         ('text',
          'When the body dies the collar fires the $(l)Redundancy Package$(): the head floats down under a little '
          'gold canopy and lands as a placed head, file and grade kept.$(br2)It is lost only if its glass was gone, it '
          'fell into the void, or it came down in lava.',
          'Quando o corpo morre, a coleira dispara o $(l)Pacote de Demissão$(): a cabeça desce sob um pequeno dossel '
          'dourado e pousa como cabeça colocada, ficha e cargo mantidos.$(br2)Só se perde sem vidro, no vazio ou '
          'caindo em lava.'),
         ('text',
          'Sneak and Use with an empty hand: the head comes off into your hand. Shears take an empty collar back. A '
          'Glass Patch mends the jar, a bucket of broth fills the canister, a syringe held sneaking draws the head.',
          'Agache e use com a mão vazia: a cabeça sai para a sua mão. Tesoura tira a coleira vazia. Remendo de Vidro '
          'conserta o pote, balde de caldo enche o cantil, seringa agachado tira sangue da cabeça.'),
     ],
     'note': ('A body with no mind meets a mind with no body. HR calls it synergy.',
              'Corpo sem mente encontra mente sem corpo. O RH chama de sinergia.')},
]
JEI_SECONDMENT = {
    'headinajar:secondment_collar': ('Fits round a host\'s neck; a jarred head seated on it drives the body and works your sites.',
                                     'Vai no pescoço de um hospedeiro; uma cabeça encaixada nela dirige o corpo e trabalha.'),
}
# the Sync page gains a line (appended to its second page, in both languages)
SYNC_LINE = ('$(br2)A seconded head earns $(l)team sync$() at work instead; it counts for the lace.',
             '$(br2)Cabeça destacada ganha $(l)sincronia de equipe$() no trabalho; ela vale para a renda.')


def _handbook(EN, PT):
    import gen_p27_handbook_b as hb
    have = {(e['cat'], e['id']) for e in hb.ENTRIES}
    for e in SECONDMENT_ENTRIES:
        if (e['cat'], e['id']) not in have:
            hb.ENTRIES.append(e)
    hb.JEI_INFO.update(JEI_SECONDMENT)
    sync = next(e for e in hb.ENTRIES if (e['cat'], e['id']) == ('staffing', 'sync'))
    kind, en, pt = sync['pages'][1]
    if not en.endswith(SYNC_LINE[0]):
        sync['pages'][1] = (kind, en + SYNC_LINE[0], pt + SYNC_LINE[1])


# ---------------------------------------------------------------------------------------------------------------
def run(EN, PT, write, shaped):
    lines = LINES + MORE_BRAIN + MORE_BROTH + MORE_PACKAGE + MORE_LOOK
    lines += [(f'staff.headinajar.say.{sid}', en, pt) for sid, en, pt in SAY + MORE_SAY]
    lines += [(f'dolores.headinajar.pairing.{pair}', en, pt) for pair, en, pt in PAIRINGS]
    keys = [k for k, _e, _p in lines]
    assert len(keys) == len(set(keys)), sorted(k for k in keys if keys.count(k) > 1)
    replaced = {'message.headinajar.lace_needs_sync'}  # the lace's line now names team sync too
    taken = [key for key in keys if key in EN and key not in replaced]
    assert not taken, f'already in the language files: {taken}'
    for key, en, pt in lines:
        EN[key], PT[key] = en, pt
    _recipes(shaped)
    _models(write)
    _advancements(EN, PT, write)
    _sounds(write)
    _check_say(EN, PT)
    import gen_p39_dna as D
    saved = D.ACTION_BAR_ARGS
    D.ACTION_BAR_ARGS = {**ACTION_BAR_ARGS, **MORE_ACTION_BAR_ARGS}
    try:
        D._check_action_bar(EN, PT, keys)
    finally:
        D.ACTION_BAR_ARGS = saved
    _handbook(EN, PT)
    _package(write)


# ---------------------------------------------------------------------------------------------------------------
# package: the Redundancy Package's canopy, two models (the wide jars' and the Compact Jar's, its lid 1 px lower)
PACKAGE = 'redundancy_package'


def _package(write):
    """art/entity/redundancy_package/redundancy_package{,_compact}.bbmodel become geo/entity/<name>.geo.json and
    animations/entity/<name>.animation.json, exported as gen_p23_winged exports the Golden Parachute (the stand-in jar
    left out: the game draws the real one). Both share textures/entity/redundancy_package.png (painted, not written
    here); the clips keep the parachute's names, timings, locators and sound keys."""
    import gen_p17_mecha as mecha
    import gen_p23_winged as winged
    base = os.path.join('assets', 'headinajar')
    for name in (PACKAGE, PACKAGE + '_compact'):
        art = os.path.join(mecha.ROOT, 'art', 'entity', PACKAGE, f'{name}.bbmodel')
        with open(art, encoding='utf-8') as f:
            model = json.load(f)
        g = mecha.geo(model, name)
        desc = g['minecraft:geometry'][0]['description']
        # the landed canopy lies 20 px to the jar's side
        desc['visible_bounds_width'], desc['visible_bounds_height'], desc['visible_bounds_offset'] = 4, 3, [0, 1.2, 0]
        geo_rel = f'{base}/geo/entity/{name}.geo.json'
        write(geo_rel, g)
        anims = mecha.animations(art, os.path.join(mecha.RES, geo_rel))
        chute_names = {'envelope_pop', 'pilot_whoosh', 'brocade_stream', 'brocade_breathe', 'brocade_spill', 'brocade_flare',
                       'brocade_flump', 'brocade_rustle', 'brocade_slide', 'brocade_slap', 'brocade_flap', 'tassel_jingle',
                       'cords_creak', 'cords_slack', 'gold_sink'}
        winged._flat_keys(anims, chute_names & winged._own_names('headinajar:mecha.'))
        write(f'{base}/animations/entity/{name}.animation.json', anims)

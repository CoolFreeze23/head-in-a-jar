"""Phase 3 (Hostile Takeover) data: host files, tags, recipes, models and language. Called by gen_data.py."""


def ab(type_, cooldown=60, **params):
    return {'type': type_, 'cooldown': cooldown, 'params': {k: float(v) for k, v in params.items()}}


ZOMBIES = ['minecraft:zombie', 'minecraft:husk', 'minecraft:drowned', 'minecraft:zombie_villager']
HOSTS = {
    'zombie': dict(neck=[0, 1.45, 0], blood='grave', neutral_to=ZOMBIES, primary=ab('groan', 200)),
    'husk': dict(neck=[0, 1.45, 0], blood='grave', neutral_to=ZOMBIES, primary=ab('groan', 200)),
    'drowned': dict(neck=[0, 1.45, 0], blood='grave', flags=['water_breathing'], neutral_to=ZOMBIES, primary=ab('groan', 200)),
    'skeleton': dict(neck=[0, 1.5, 0], blood='marrow', attributes={'minecraft:generic.max_health': -5}, primary=ab('arrows', 120, count=3)),
    'stray': dict(neck=[0, 1.5, 0], blood='marrow', primary=ab('arrows', 120, count=3)),
    'creeper': dict(neck=[0, 1.25, 0], blood='ichor', primary=ab('explode', 0, power=3)),
    'spider': dict(neck=[0, 0.75, 0.55], blood='ichor', flags=['climb', 'night_vision'], primary=ab('web', 60, range=16)),
    'cave_spider': dict(neck=[0, 0.45, 0.35], blood='ichor', flags=['climb', 'night_vision'], primary=ab('web', 60, range=12)),
    'enderman': dict(neck=[0, 2.45, 0], blood='ender', neutral_to=['minecraft:enderman'], primary=ab('teleport', 60, range=24),
                     secondary=ab('teleport', 200, range=48)),
    'blaze': dict(neck=[0, 1.3, 0], blood='brimstone', flags=['fire_immune', 'slow_fall'], primary=ab('fireball', 100, count=3),
                  secondary=ab('hover', 160, duration=80)),
    'iron_golem': dict(neck=[0, 2.35, -0.05], blood='oil', flags=['knockback_immune'], drawbacks=['no_jump'], primary=ab('toss', 40, power=1.2)),
    'ravager': dict(neck=[0, 1.8, 1.0], blood='warm', primary=ab('charge', 100, damage=12, speed=1.8, knockback=2), secondary=ab('roar', 200, radius=6, duration=40)),
    'horse': dict(neck=[0, 1.55, 0.6], blood='warm', primary=ab('sprint', 200)),
    'donkey': dict(neck=[0, 1.35, 0.55], blood='warm', primary=ab('sprint', 200)),
    'mule': dict(neck=[0, 1.45, 0.55], blood='warm', primary=ab('sprint', 200)),
    'camel': dict(neck=[0, 2.15, 0.7], blood='warm', primary=ab('charge', 80, damage=0, speed=1.6)),
    'wolf': dict(neck=[0, 0.75, 0.45], blood='warm', primary=ab('pounce', 60, damage=4, speed=1.1)),
    'cat': dict(neck=[0, 0.6, 0.35], blood='warm', flags=['no_fall_damage']),
    'ocelot': dict(neck=[0, 0.6, 0.35], blood='warm', flags=['no_fall_damage'], primary=ab('sprint', 160)),
    'fox': dict(neck=[0, 0.6, 0.4], blood='warm', flags=['night_vision'], primary=ab('pounce', 60, damage=3, speed=1.0)),
    'chicken': dict(neck=[0, 0.65, 0.15], blood='warm', flags=['slow_fall'], primary=ab('lay_egg', 1200)),
    'pig': dict(neck=[0, 0.75, 0.55], blood='warm'),
    'cow': dict(neck=[0, 1.3, 0.6], blood='warm'),
    'sheep': dict(neck=[0, 1.2, 0.55], blood='warm'),
    'polar_bear': dict(neck=[0, 1.3, 0.75], blood='warm', primary=ab('roar', 200, radius=4, duration=30)),
    'dolphin': dict(neck=[0, 0.5, 0.6], blood='warm', flags=['water_breathing', 'speed_aura'], primary=ab('charge', 60, damage=4, speed=1.6)),
    'squid': dict(neck=[0, 0.6, 0], blood='cold', flags=['water_breathing'], primary=ab('ink', 200)),
    'glow_squid': dict(neck=[0, 0.6, 0], blood='cold', flags=['water_breathing', 'night_vision'], primary=ab('ink', 200)),
    'turtle': dict(neck=[0, 0.35, 0.6], blood='cold', flags=['water_breathing']),
    'strider': dict(neck=[0, 1.6, 0], blood='brimstone', flags=['fire_immune']),
    'goat': dict(neck=[0, 1.1, 0.55], blood='warm', flags=['no_fall_damage'], primary=ab('charge', 80, damage=2, knockback=3, speed=1.4)),
    'bat': dict(neck=[0, 0.6, 0], blood='warm', flags=['flight', 'night_vision'], primary=ab('echolocation', 200)),
    'phantom': dict(neck=[0, 0.4, 0.4], blood='grave', flags=['flight'], primary=ab('pounce', 80, damage=6, speed=1.4)),
    'ghast': dict(neck=[0, 4.0, 0], blood='brimstone', flags=['flight', 'fire_immune'], primary=ab('fireball', 60, count=1, large=1)),
    'piglin': dict(neck=[0, 1.45, 0], blood='brimstone'),
    'villager': dict(neck=[0, 1.45, 0], blood='warm'),
    'witch': dict(neck=[0, 1.5, 0], blood='arcane', primary=ab('potion', 100)),
    'breeze': dict(neck=[0, 1.5, 0], blood='ecto', flags=['no_fall_damage'], primary=ab('wind_charge', 40)),
    'warden': dict(neck=[0, 2.6, 0.2], blood='grave', flags=['night_vision', 'knockback_immune'], primary=ab('sonic_boom', 200, damage=10, range=15)),
    'frog': dict(neck=[0, 0.5, 0.2], blood='cold', flags=['no_fall_damage']),
    'llama': dict(neck=[0, 1.75, 0.5], blood='warm'),
}
SHELLS = {
    'lunk': dict(neck=[0, 2.5, 0], blood='oil', flags=['knockback_immune'], primary=ab('toss', 30, power=1.4, range=4)),
    'rover': dict(neck=[0, 0.875, 0], blood='oil', primary=ab('sprint', 200, duration=100, amplifier=2)),
    'spider_walker': dict(neck=[0, 0.81, 0], blood='oil', flags=['climb', 'no_fall_damage'], primary=ab('web', 60, range=16)),
}

LANG = [
    ('item.headinajar.surgical_saw', 'Surgical Saw', 'Serra Cirúrgica'),
    ('item.headinajar.surgical_saw.how', 'Use on a mob at half its health or less to make it a host body a jar can pilot.',
     'Use numa criatura com metade da vida ou menos para torná-la um corpo hospedeiro que um pote pode pilotar.'),
    ('item.headinajar.surgical_saw.how2', 'With an empty Life Support Jar in your off hand, keep the head instead.',
     'Com um Pote de Suporte Vital vazio na outra mão, fique com a cabeça.'),
    ('item.headinajar.embalming_fluid', 'Embalming Fluid', 'Fluido de Embalsamar'),
    ('item.headinajar.neural_lace', 'Neural Lace', 'Renda Neural'),
    ('item.headinajar.jarred_head', 'Jarred Head', 'Cabeça no Pote'),
    ('item.headinajar.jarred_head.of', 'Jarred %s Head', 'Cabeça de %s no Pote'),
    ('item.headinajar.jarred_head.trades', 'Still trades', 'Ainda negocia'),
    ('item.headinajar.jarred_head.how', 'Place it anywhere.', 'Coloque em qualquer lugar.'),
    ('item.headinajar.lunk_chassis', 'Lunk Chassis', 'Chassi do Brutamontes'),
    ('item.headinajar.lunk_chassis.desc', 'Strong, slow, hits like a truck.', 'Forte, lento, bate como um caminhão.'),
    ('item.headinajar.rover_chassis', 'Rover Chassis', 'Chassi do Rover'),
    ('item.headinajar.rover_chassis.desc', 'Fast, with 9 slots of cargo. Sneak-use it to open the hold.',
     'Rápido, com 9 espaços de carga. Agache e use para abrir o compartimento.'),
    ('item.headinajar.spider_walker_chassis', 'Spider Walker Chassis', 'Chassi do Andador Aranha'),
    ('item.headinajar.spider_walker_chassis.desc', 'Climbs walls and never takes fall damage.', 'Escala paredes e nunca toma dano de queda.'),
    ('item.headinajar.shell.how', 'Place it, then use it as a jar to climb in. Runs on your charge.',
     'Coloque e use como pote para entrar. Funciona com a sua carga.'),
    ('entity.headinajar.lunk', 'Lunk', 'Brutamontes'),
    ('entity.headinajar.rover', 'Rover', 'Rover'),
    ('entity.headinajar.spider_walker', 'Spider Walker', 'Andador Aranha'),
    ('block.headinajar.jarred_head', 'Jarred Head', 'Cabeça no Pote'),
    ('block.headinajar.body_locker', 'Body Locker', 'Armário de Corpos'),
    ('key.headinajar.secondary', 'Secondary (laser, host, mecha, genes)', 'Secundária (laser, hospedeiro, mecha, genes)'),
    ('message.headinajar.saw_refused', 'The saw refuses: bosses, named mobs, other people\'s pets and hosts are off limits.',
     'A serra se recusa: chefões, criaturas com nome, bichos de outras pessoas e hospedeiros estão fora de questão.'),
    ('message.headinajar.saw_too_healthy', 'Too lively. Wear it down to half its health or less first.',
     'Vivo demais. Deixe-o com metade da vida ou menos antes.'),
    ('message.headinajar.host_made', '%s is now a host body. As a jar, use it to climb on.', '%s agora é um corpo hospedeiro. Como pote, use-o para subir.'),
    ('message.headinajar.host_busy', 'Someone is already in that body', 'Alguém já está nesse corpo'),
    ('message.headinajar.host_status', 'Host: freshness %s%%, sync %s%%. Climb on as a jar.', 'Hospedeiro: frescor %s%%, sincronia %s%%. Suba nele como pote.'),
    ('message.headinajar.sync_locked', 'Needs %s%% sync. Keep riding: it grows 5%% a minute.', 'Precisa de %s%% de sincronia. Continue montado: sobe 5%% por minuto.'),
    ('message.headinajar.embalmed', 'Embalmed: fresh again, and it keeps longer', 'Embalsamado: fresco de novo e dura mais'),
    ('message.headinajar.laced', 'Neural Lace grafted: this body will never decay', 'Renda Neural enxertada: este corpo nunca vai apodrecer'),
    ('message.headinajar.lace_needs_sync', 'The lace needs at least 50% sync to take', 'A renda precisa de pelo menos 50% de sincronia'),
    ('message.headinajar.no_cargo', 'Only the Rover has a cargo hold', 'Só o Rover tem compartimento de carga'),
    ('message.headinajar.locker_none', 'No host body within 3 blocks', 'Nenhum corpo hospedeiro num raio de 3 blocos'),
    ('message.headinajar.locker_stored', '%s stored. It will keep.', '%s guardado. Vai se conservar.'),
    ('message.headinajar.locker_released', '%s released', '%s liberado'),
    ('message.headinajar.jarred_head', 'A jarred %s head. It looks at you.', 'Uma cabeça de %s no pote. Ela olha para você.'),
    ('hud.headinajar.freshness', 'Fresh %s%%', 'Frescor %s%%'),
    ('hud.headinajar.sync', 'Sync %s%%', 'Sincronia %s%%'),
    ('hud.headinajar.primary_ready', '%s: ability', '%s: habilidade'),
    ('hud.headinajar.primary_locked', '%s at 50%%', '%s em 50%%'),
    ('hud.headinajar.secondary_ready', '%s: ability', '%s: habilidade'),
    ('hud.headinajar.secondary_locked', '%s at 75%%', '%s em 75%%'),
    ('hud.headinajar.handshake', 'NEURAL HANDSHAKE %s/3: press %s on the ring', 'APERTO DE MÃO NEURAL %s/3: aperte %s no anel'),
]


def run(EN, PT, write, shaped):
    def host_file(name, ns, d):
        data = {'entity': f'{ns}:{name}', 'neck': d['neck'], 'blood': d.get('blood', 'warm'),
                'decay_per_minute': d.get('decay', 2.0), 'attributes': d.get('attributes', {}), 'flags': d.get('flags', []),
                'drawbacks': d.get('drawbacks', []), 'neutral_to': d.get('neutral_to', [])}
        for k in ('primary', 'secondary'):
            if k in d:
                data[k] = d[k]
        write(f'data/headinajar/headinajar/host/{name}.json', data)

    for name, d in HOSTS.items():
        host_file(name, 'minecraft', d)
    for name, d in SHELLS.items():
        host_file(name, 'headinajar', d)
    write('data/headinajar/tags/entity_type/not_a_host.json', {'values': [
        'minecraft:player', 'minecraft:armor_stand', 'headinajar:headless_body', 'headinajar:head_jar']})

    S, W = 'headinajar:surgical_steel_ingot', 'headinajar:neuro_copper_wire'
    shaped('surgical_saw', ['  S', ' VS', 'D  '], dict(S=S, V='headinajar:servo', D='diamond'))
    write('data/headinajar/recipe/embalming_fluid.json', {'type': 'minecraft:crafting_shapeless', 'category': 'misc', 'ingredients': [
        {'item': 'headinajar:saline_bottle'}, {'item': 'minecraft:spider_eye'}, {'item': 'minecraft:sugar'}, {'item': 'minecraft:bone_meal'}],
        'result': {'id': 'headinajar:embalming_fluid', 'count': 1}})
    shaped('neural_lace', ['GSG', 'ECE', 'GSG'], dict(G='gold_nugget', S='string', E='spider_eye', C='headinajar:synapse_chip'))
    shaped('body_locker', ['SIS', 'SGS', 'SRS'], dict(S=S, I='iron_door', G='glass', R='packed_ice'))
    shaped('lunk_chassis', ['VSV', 'SCS', 'S S'], dict(V='headinajar:servo', S='iron_block', C='headinajar:collar_cam'))
    shaped('rover_chassis', [' C ', 'SHS', 'W W'], dict(C='headinajar:collar_cam', S=S, H='chest', W='headinajar:servo'))
    shaped('spider_walker_chassis', ['TCT', 'VSV', 'TST'], dict(T='string', C='headinajar:collar_cam', V='headinajar:servo', S=S))

    write('data/headinajar/loot_table/blocks/body_locker.json', {
        'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'headinajar:body_locker'}],
                                             'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})

    rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    write('assets/headinajar/models/block/body_locker.json', {'parent': 'minecraft:block/orientable', 'textures': {
        'particle': 'headinajar:block/machine_side', 'side': 'headinajar:block/machine_side', 'top': 'headinajar:block/saline_mixer_top',
        'front': 'headinajar:block/body_locker_front'}})
    write('assets/headinajar/blockstates/body_locker.json', {'variants': {f'facing={d}': {'model': 'headinajar:block/body_locker', 'y': r} for d, r in rot.items()}})
    write('assets/headinajar/models/item/body_locker.json', {'parent': 'headinajar:block/body_locker'})
    write('assets/headinajar/models/block/jarred_head.json', {'textures': {'particle': 'minecraft:block/glass'}})
    write('assets/headinajar/blockstates/jarred_head.json', {'variants': {f'facing={d}': {'model': 'headinajar:block/jarred_head'} for d in rot}})
    for i in ['surgical_saw', 'embalming_fluid', 'neural_lace', 'jarred_head', 'lunk_chassis', 'rover_chassis', 'spider_walker_chassis']:
        write(f'assets/headinajar/models/item/{i}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'headinajar:item/{i}'}})

    for key, en, pt in LANG:
        EN[key] = en
        PT[key] = pt
    return ['body_locker']

"""Phase 4 (Mecha Division) data: host files, recipes, models and language. Called by gen_data.py."""

MODULES = {
    # name: (en name, en desc, pt name, pt desc, recipe pattern, key)
    'hydraulic_legs': ('Hydraulic Legs', 'Lets the mecha walk. Stride trades speed for power.', 'Pernas Hidráulicas', 'Faz o mecha andar. A passada troca velocidade por energia.',
                       ['V V', 'P P', 'P P'], dict(V='headinajar:servo', P='headinajar:mecha_plating')),
    'spring_heels': ('Spring Heels', 'Jump 1.5 to 4 blocks.', 'Calcanhares de Mola', 'Pula de 1,5 a 4 blocos.',
                     ['P P', 'S S'], dict(P='piston', S='slime_block')),
    'anchor_clamps': ('Anchor Clamps', 'Sneak to brace: no knockback, no moving.', 'Grampos de Ancoragem', 'Agache para se firmar: sem repulsão e sem andar.',
                      ['A A', 'P P'], dict(A='anvil', P='headinajar:mecha_plating')),
    'aqua_fins': ('Aqua Fins', 'Swim twice as fast.', 'Barbatanas Aquáticas', 'Nada duas vezes mais rápido.',
                  ['K K', 'P P'], dict(K='prismarine_shard', P='headinajar:mecha_plating')),
    'jump_jets': ('Jump Jets', 'Three mid-air bursts (jump again in the air).', 'Jatos de Salto', 'Três impulsos no ar (pule de novo no ar).',
                  ['F F', 'BCB'], dict(F='fire_charge', B='blast_furnace', C='headinajar:synapse_chip')),
    'rocket_pack': ('Rocket Pack', 'Flight, if its thrust beats the mecha\'s mass.', 'Mochila Foguete', 'Voo, se o empuxo vencer a massa do mecha.',
                    ['BCB', 'F F'], dict(B='blaze_rod', C='headinajar:synapse_chip', F='fire_charge')),
    'glider_wings': ('Glider Wings', 'Hold jump while falling to glide.', 'Asas de Planador', 'Segure pular ao cair para planar.',
                     ['PMP'], dict(P='phantom_membrane', M='headinajar:mecha_plating')),
    'cargo_hold': ('Cargo Hold', '27 slots; drills and the Combustion Kernel use it.', 'Compartimento de Carga', '27 espaços; brocas e o Núcleo de Combustão usam.',
                   ['PCP', 'PPP'], dict(P='headinajar:mecha_plating', C='chest')),
    'passenger_pod': ('Passenger Pod', 'A seat for a friend in any form.', 'Cápsula de Passageiro', 'Um assento para um amigo em qualquer forma.',
                      ['GGG', 'PSP'], dict(G='glass', P='headinajar:mecha_plating', S='white_wool')),
    'radiator_fins': ('Radiator Fins', '+4 HU/t cooling.', 'Aletas de Radiador', '+4 HU/t de resfriamento.',
                      ['III', 'PPP'], dict(I='iron_bars', P='headinajar:mecha_plating')),
    'battery_rack': ('Battery Rack', '+125 kFE battery.', 'Rack de Baterias', '+125 kFE de bateria.',
                     ['LLL', 'PPP'], dict(L='headinajar:leyden_jar', P='headinajar:mecha_plating')),
    'galvanic_stack': ('Galvanic Stack', 'Makes 10 FE/t, always.', 'Pilha Galvânica Compacta', 'Gera 10 FE/t, sempre.',
                       ['G', 'P'], dict(G='headinajar:galvanic_pile', P='headinajar:mecha_plating')),
    'combustion_kernel': ('Combustion Kernel', '80 FE/t from fuel in the Cargo Hold.', 'Núcleo de Combustão', '80 FE/t com combustível do compartimento de carga.',
                          ['PFP', 'PWP'], dict(P='headinajar:mecha_plating', F='blast_furnace', W='headinajar:neuro_copper_wire')),
    'armor_plating': ('Mecha Armour Plating', '10 to 30% less damage. Thickness trades protection for mass.', 'Blindagem de Mecha', '10 a 30% menos dano. A espessura troca proteção por massa.',
                      ['PPP', 'PIP'], dict(P='headinajar:mecha_plating', I='iron_block')),
    'thorn_plating': ('Thorn Plating', 'Reflects 30% of melee damage.', 'Blindagem de Espinhos', 'Reflete 30% do dano corpo a corpo.',
                      ['CPC'], dict(C='cactus', P='headinajar:mecha_plating')),
    'fire_suppression': ('Fire Suppression', 'Fire immunity for mecha and pilot.', 'Supressão de Incêndio', 'Imunidade a fogo para mecha e piloto.',
                         ['WPW'], dict(W='water_bucket', P='headinajar:mecha_plating')),
    'life_support_recycler': ('Life Support Recycler', 'Keeps the pilot\'s saline and charge full.', 'Reciclador de Suporte Vital', 'Mantém o soro e a carga do piloto cheios.',
                              ['SPS'], dict(S='headinajar:saline_recycler', P='headinajar:mecha_plating')),
    'signal_booster': ('Signal Booster', 'A moving relay for your link.', 'Amplificador de Sinal', 'Um retransmissor ambulante para sua conexão.',
                       [' M ', 'PCP'], dict(M='headinajar:relay_mast', P='headinajar:mecha_plating', C='headinajar:synapse_chip')),
    'solar_skin': ('Solar Skin', '15 FE/t in daylight.', 'Pele Solar', '15 FE/t sob a luz do dia.',
                   ['DDD', 'PPP'], dict(D='daylight_detector', P='headinajar:mecha_plating')),
    'coolant_loop': ('Coolant Loop', '+6 HU/t cooling.', 'Circuito de Refrigeração', '+6 HU/t de resfriamento.',
                     ['IPI'], dict(I='packed_ice', P='headinajar:mecha_plating')),
    'drill': ('Drill Arm', 'Hold to mine. Torque over half: a 3x3 head.', 'Braço Broca', 'Segure para minerar. Torque acima da metade: cabeça 3x3.',
              [' D ', 'DID', ' P '], dict(D='diamond', I='iron_block', P='headinajar:servo')),
    'chainsaw': ('Chainsaw Arm', 'Hold on a log to fell the whole tree; 8 damage.', 'Braço Motosserra', 'Segure num tronco para derrubar a árvore toda; 8 de dano.',
                 ['III', 'SVS'], dict(I='iron_ingot', S='shears', V='headinajar:servo')),
    'hydraulic_fist': ('Hydraulic Fist', '10 to 22 damage punches with knockback.', 'Punho Hidráulico', 'Socos de 10 a 22 de dano com repulsão.',
                       ['PPP', 'PVP'], dict(P='piston', V='headinajar:servo')),
    'grapple_hook': ('Grapple Hook', 'Pulls you to a block, or a creature to you.', 'Gancho', 'Puxa você até um bloco, ou uma criatura até você.',
                     ['  T', ' T ', 'V  '], dict(T='tripwire_hook', V='headinajar:servo')),
    'tractor_beam': ('Tractor Beam', 'Telekinesis at mecha scale, 24 blocks.', 'Raio Trator', 'Telecinese em escala de mecha, 24 blocos.',
                     ['T', 'V'], dict(T='headinajar:telekinesis', V='headinajar:servo')),
    'rivet_gun': ('Rivet Gun', 'Five shots a second; uses iron nuggets.', 'Rebitadeira', 'Cinco tiros por segundo; usa pepitas de ferro.',
                  ['IIC', ' V '], dict(I='iron_ingot', C='crossbow', V='headinajar:servo')),
    'riot_shield': ('Riot Shield', 'Hold: 80% less damage from the front.', 'Escudo de Choque', 'Segure: 80% menos dano pela frente.',
                    ['PSP', 'PPP'], dict(P='headinajar:mecha_plating', S='shield')),
    # a servo under it, so it doesn't share the Collar Cam's Night Optics recipe
    'night_optics': ('Night Optics', 'Night vision for the pilot.', 'Óptica Noturna', 'Visão noturna para o piloto.',
                     ['L', 'G', 'V'], dict(L='headinajar:lens', G='golden_carrot', V='headinajar:servo')),
    'bio_scanner': ('Bio-Scanner', 'Shows what you look at in more detail.', 'Bioscanner', 'Mostra o que você olha com mais detalhes.',
                    ['L', 'C'], dict(L='headinajar:lens', C='headinajar:synapse_chip')),
}

LANG = [
    ('block.headinajar.assembly_gantry', 'Assembly Gantry', 'Pórtico de Montagem'),
    ('item.headinajar.mecha_plating', 'Mecha Plating', 'Placa de Mecha'),
    ('item.headinajar.hopper_frame', 'Hopper Frame', 'Estrutura Saltador'),
    ('item.headinajar.bulwark_frame', 'Bulwark Frame', 'Estrutura Baluarte'),
    ('entity.headinajar.hopper', 'Hopper', 'Saltador'),
    ('entity.headinajar.bulwark', 'Bulwark', 'Baluarte'),
    ('mecha_frame.headinajar.stats', '%s HP, %s armour, %s kg load, %s kFE', '%s de vida, %s de armadura, %s kg de carga, %s kFE'),
    ('mecha_module.headinajar.stats', '%s slot, %s kg', 'Encaixe de %s, %s kg'),
    ('mecha_module.headinajar.slider', '%s: %s%%', '%s: %s%%'),
    ('mecha_slot.headinajar.legs', 'Legs', 'Pernas'),
    ('mecha_slot.headinajar.back', 'Back', 'Costas'),
    ('mecha_slot.headinajar.core', 'Core', 'Núcleo'),
    ('mecha_slot.headinajar.torso', 'Torso', 'Torso'),
    ('mecha_slot.headinajar.arm', 'Arm', 'Braço'),
    ('mecha_slot.headinajar.sensor', 'Sensor', 'Sensor'),
    ('mecha_slider.headinajar.stride', 'Stride', 'Passada'),
    ('mecha_slider.headinajar.tension', 'Tension', 'Tensão'),
    ('mecha_slider.headinajar.thrust', 'Thrust', 'Empuxo'),
    ('mecha_slider.headinajar.thickness', 'Thickness', 'Espessura'),
    ('mecha_slider.headinajar.torque', 'Torque', 'Torque'),
    ('mecha_slider.headinajar.force', 'Force', 'Força'),
    ('gui.headinajar.gantry.assemble', 'Assemble', 'Montar'),
    ('gui.headinajar.gantry.disassemble', 'Take apart', 'Desmontar'),
    ('gui.headinajar.gantry.repair', 'Repair', 'Reparar'),
    ('gui.headinajar.gantry.mass', '%s/%s kg', '%s/%s kg'),
    ('gui.headinajar.gantry.slider', '%s %s%%', '%s %s%%'),
    ('gui.headinajar.gantry.power', '%s FE/t idle', '%s FE/t parado'),
    ('gui.headinajar.gantry.need_frame', 'Put a frame in the top-left slot', 'Coloque uma estrutura no espaço de cima à esquerda'),
    ('gantry.headinajar.no_frame', 'No frame to build on', 'Nenhuma estrutura para montar'),
    ('gantry.headinajar.pad_busy', 'Clear the pad first', 'Libere a plataforma primeiro'),
    ('gantry.headinajar.no_power', 'Assembly needs 10 kFE', 'A montagem precisa de 10 kFE'),
    ('gantry.headinajar.pad_empty', 'No mecha on the pad', 'Nenhum mecha na plataforma'),
    ('gantry.headinajar.not_yours', 'That mecha is not yours', 'Esse mecha não é seu'),
    ('gantry.headinajar.slots_busy', 'Empty the gantry first', 'Esvazie o pórtico primeiro'),
    ('message.headinajar.not_your_mecha', 'That mecha belongs to someone else', 'Esse mecha pertence a outra pessoa'),
    ('message.headinajar.mecha_status', 'Mecha: %s kFE, %s / %s HP', 'Mecha: %s kFE, %s / %s de vida'),
    ('message.headinajar.parachute', 'GOLDEN PARACHUTE DEPLOYED', 'PARAQUEDAS DOURADO ACIONADO'),
    ('hud.headinajar.battery', '%s kFE', '%s kFE'),
    ('hud.headinajar.heat', 'Heat %s%%', 'Calor %s%%'),
    ('hud.headinajar.load', 'Mass %s / %s kg', 'Massa %s / %s kg'),
    ('hud.headinajar.arms', 'L: %s   R: %s', 'E: %s   D: %s'),
    ('hud.headinajar.venting', 'VENTING', 'LIBERANDO VAPOR'),
    ('hud.headinajar.limp', 'NO POWER: limp mode', 'SEM ENERGIA: modo de emergência'),
    ('hud.headinajar.servos_locked', 'OVERLOADED: servos locked', 'SOBRECARGA: servos travados'),
    ('hud.headinajar.ejecting', 'Ejecting... %s%%', 'Ejetando... %s%%'),
]


def run(EN, PT, write, shaped):
    S, W = 'headinajar:surgical_steel_ingot', 'headinajar:neuro_copper_wire'
    for name, neck in (('hopper', [0, 2.25, 0]), ('bulwark', [0, 2.875, 0])):
        write(f'data/headinajar/headinajar/host/{name}.json', {'entity': f'headinajar:{name}', 'neck': neck, 'blood': 'oil', 'decay_per_minute': 0})
    shaped('mecha_plating', ['SSS', 'SIS', 'SSS'], dict(S=S, I='iron_block'), 2)
    shaped('assembly_gantry', ['WCW', 'SAS', 'PPP'], dict(W=W, C='headinajar:synapse_chip', S=S, A='anvil', P='headinajar:mecha_plating'))
    shaped('hopper_frame', [' C ', 'PVP', 'V V'], dict(C='headinajar:collar_cam', P='headinajar:mecha_plating', V='headinajar:servo'))
    shaped('bulwark_frame', ['PCP', 'PNP', 'V V'], dict(P='headinajar:mecha_plating', C='headinajar:collar_cam', N='netherite_scrap', V='headinajar:servo'))
    for name, (en, en_desc, pt, pt_desc, pattern, key) in MODULES.items():
        shaped('mecha_' + name, pattern, key)
        EN[f'item.headinajar.mecha_{name}'], PT[f'item.headinajar.mecha_{name}'] = en, pt
        EN[f'mecha_module.headinajar.{name}.desc'], PT[f'mecha_module.headinajar.{name}.desc'] = en_desc, pt_desc
        write(f'assets/headinajar/models/item/mecha_{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'headinajar:item/mecha_{name}'}})
    for i in ['mecha_plating', 'hopper_frame', 'bulwark_frame']:
        write(f'assets/headinajar/models/item/{i}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'headinajar:item/{i}'}})
    rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    write('assets/headinajar/models/block/assembly_gantry.json', {'parent': 'minecraft:block/block', 'textures': {
        'particle': 'headinajar:block/gantry_side', 'top': 'headinajar:block/gantry_top', 'side': 'headinajar:block/gantry_side'},
        'elements': [{'from': [0, 0, 0], 'to': [16, 6, 16], 'faces': {
            'up': {'texture': '#top'}, 'down': {'texture': '#side'}, 'north': {'texture': '#side', 'uv': [0, 10, 16, 16]},
            'south': {'texture': '#side', 'uv': [0, 10, 16, 16]}, 'east': {'texture': '#side', 'uv': [0, 10, 16, 16]},
            'west': {'texture': '#side', 'uv': [0, 10, 16, 16]}}}]})
    write('assets/headinajar/blockstates/assembly_gantry.json', {'variants': {f'facing={d}': {'model': 'headinajar:block/assembly_gantry', 'y': r} for d, r in rot.items()}})
    write('assets/headinajar/models/item/assembly_gantry.json', {'parent': 'headinajar:block/assembly_gantry'})
    write('data/headinajar/loot_table/blocks/assembly_gantry.json', {
        'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'headinajar:assembly_gantry'}],
                                             'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    for key, en, pt in LANG:
        EN[key] = en
        PT[key] = pt

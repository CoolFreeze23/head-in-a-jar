"""Writes recipes, loot tables, tags and the en_us / pt_br language files. Run: python tools/gen_data.py"""
import json
import os

RES = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources')
MC = 'minecraft:'


def write(path, data):
    p = os.path.join(RES, path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write('\n')


def shaped(name, pattern, key, count=1):
    write(f'data/headinajar/recipe/{name}.json', {
        'type': 'minecraft:crafting_shaped', 'category': 'equipment', 'pattern': pattern,
        'key': {k: {'item': v if ':' in v else MC + v} for k, v in key.items()},
        'result': {'id': 'headinajar:' + name, 'count': count}})


shaped('reinforced_life_support_jar', [' N ', 'TJT', ' O '], dict(N='netherite_scrap', T='tinted_glass', J='headinajar:life_support_jar', O='obsidian'))
shaped('hop_springs', [' P ', 'ICI', ' S '], dict(P='piston', I='iron_ingot', C='copper_ingot', S='slime_ball'))
shaped('propeller', ['FIF', 'ICI', 'FRF'], dict(F='feather', I='iron_ingot', C='copper_ingot', R='redstone'))
shaped('manipulator_arm', [' II', 'RC ', ' I '], dict(I='iron_ingot', R='redstone', C='copper_ingot'))
shaped('telekinesis', ['AEA', 'ECE', 'ARA'], dict(A='amethyst_shard', E='ender_pearl', C='copper_ingot', R='redstone'))
shaped('drill_bit', [' D ', ' G ', 'ICI'], dict(D='diamond', G='gold_ingot', I='iron_ingot', C='copper_ingot'))
shaped('armor_plating', ['III', 'ICI', 'III'], dict(I='iron_ingot', C='copper_ingot'))
shaped('heat_shield', ['BMB', 'MCM', 'BMB'], dict(B='blaze_powder', M='magma_cream', C='copper_ingot'))
shaped('night_lens', [' Q ', 'GCG', ' K '], dict(Q='glass', G='gold_nugget', C='copper_ingot', K='golden_carrot'))
shaped('magnet', ['R L', 'ICI', ' I '], dict(R='redstone', L='lapis_lazuli', I='iron_ingot', C='copper_ingot'))
shaped('water_jet', [' K ', 'PCP', 'IRI'], dict(K='kelp', P='prismarine_shard', C='copper_ingot', I='iron_ingot', R='redstone'))

write('data/headinajar/loot_table/blocks/charging_dock.json', {
    'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'headinajar:charging_dock'}],
                                         'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
write('data/minecraft/tags/block/mineable/pickaxe.json', {'values': ['headinajar:charging_dock']})
write('data/minecraft/tags/block/needs_stone_tool.json', {'values': ['headinajar:charging_dock']})

MODULES = {
    'hop_springs': (('Hop Springs', 'Hops the jar along when you walk, at 60% speed.'),
                    ('Molas Saltadoras', 'O pote anda aos pulinhos quando você anda, a 60% da velocidade.')),
    'propeller': (('Propeller', 'Lets the jar fly (double-tap jump).'),
                  ('Hélice', 'Faz o pote voar (toque duas vezes em pular).')),
    'manipulator_arm': (('Manipulator Arm', 'A little arm: mine, place, use items, fight and pick things up.'),
                        ('Braço Manipulador', 'Um bracinho: minere, coloque blocos, use itens, lute e pegue coisas.')),
    'telekinesis': (('Telekinesis Module', 'R grabs the block, creature or item you look at; R drops it, attack throws it.'),
                    ('Módulo de Telecinese', 'R agarra o bloco, criatura ou item que você olha; R solta, atacar arremessa.')),
    'drill_bit': (('Drill Bit', 'Mines three times as fast. Needs an arm or a body.'),
                  ('Broca', 'Minera três vezes mais rápido. Precisa de um braço ou de um corpo.')),
    'armor_plating': (('Armour Plating', '+6 armour, +2 toughness.'),
                      ('Blindagem', '+6 de armadura, +2 de resistência.')),
    'heat_shield': (('Heat Shield', "Fire and lava can't hurt the jar."),
                    ('Escudo Térmico', 'Fogo e lava não machucam o pote.')),
    'night_lens': (('Night Lens', 'See in the dark.'),
                   ('Lente Noturna', 'Enxergue no escuro.')),
    'magnet': (('Magnet', 'Pulls in items within 6 blocks.'),
               ('Ímã', 'Puxa itens num raio de 6 blocos.')),
    'water_jet': (('Water Jet', 'Swim fast; hop along the bottom.'),
                  ("Jato d'Água", 'Nade rápido; pule pelo fundo.')),
}

EN = {
    'itemGroup.headinajar': 'Head in a Jar',
    'item.headinajar.life_support_jar': 'Life Support Jar',
    'item.headinajar.reinforced_life_support_jar': 'Reinforced Life Support Jar',
    'item.headinajar.life_support_jar.slots': '%s module slots',
    'item.headinajar.life_support_jar.how': 'Sneak and hold use to put your head in it',
    'block.headinajar.charging_dock': 'Charging Dock',
    'entity.headinajar.headless_body': 'Headless Body',
    'entity.headinajar.headless_body.owned': "%s's Body",
    'entity.headinajar.held_block': 'Lifted Block',
    'container.headinajar.jar': 'Life Support Jar',
    'gui.headinajar.modules': 'Modules',
    'gui.headinajar.charge': 'Charge %s%%',
    'gui.headinajar.let_go': 'Let go',
    'gui.headinajar.let_go.tooltip': 'Give up on this jar. You die and wake up with a fresh body.',
    'gui.headinajar.let_go.title': 'Let go?',
    'gui.headinajar.let_go.body': 'Your head gives up. You die, your modules drop, and you respawn with a body.',
    'message.headinajar.sneak_to_detach': 'Sneak and hold use to unscrew your head into the jar',
    'module.headinajar.powered': 'Uses charge',
    'key.categories.headinajar': 'Head in a Jar',
    'key.headinajar.jar_menu': 'Jar menu',
    'key.headinajar.ability': 'Jar ability (Telekinesis)',
}
PT = {
    'itemGroup.headinajar': 'Cabeça no Pote',
    'item.headinajar.life_support_jar': 'Pote de Suporte Vital',
    'item.headinajar.reinforced_life_support_jar': 'Pote de Suporte Vital Reforçado',
    'item.headinajar.life_support_jar.slots': '%s encaixes de módulo',
    'item.headinajar.life_support_jar.how': 'Agache e segure usar para colocar sua cabeça nele',
    'block.headinajar.charging_dock': 'Base de Recarga',
    'entity.headinajar.headless_body': 'Corpo sem Cabeça',
    'entity.headinajar.headless_body.owned': 'Corpo de %s',
    'entity.headinajar.held_block': 'Bloco Erguido',
    'container.headinajar.jar': 'Pote de Suporte Vital',
    'gui.headinajar.modules': 'Módulos',
    'gui.headinajar.charge': 'Carga %s%%',
    'gui.headinajar.let_go': 'Desistir',
    'gui.headinajar.let_go.tooltip': 'Desista deste pote. Você morre e acorda com um corpo novo.',
    'gui.headinajar.let_go.title': 'Desistir?',
    'gui.headinajar.let_go.body': 'Sua cabeça desiste. Você morre, seus módulos caem e você renasce com um corpo.',
    'message.headinajar.sneak_to_detach': 'Agache e segure usar para desatarraxar sua cabeça para dentro do pote',
    'module.headinajar.powered': 'Usa carga',
    'key.categories.headinajar': 'Cabeça no Pote',
    'key.headinajar.jar_menu': 'Menu do pote',
    'key.headinajar.ability': 'Habilidade do pote (Telecinese)',
}
for key, ((en_name, en_desc), (pt_name, pt_desc)) in MODULES.items():
    EN[f'item.headinajar.{key}'], EN[f'module.headinajar.{key}.desc'] = en_name, en_desc
    PT[f'item.headinajar.{key}'], PT[f'module.headinajar.{key}.desc'] = pt_name, pt_desc

import sys
sys.path.insert(0, os.path.dirname(__file__))
import gen_p1
gen_p1.add(EN, PT, write, shaped)
import gen_p2
p2_blocks = gen_p2.run(EN, PT, write, shaped)
import gen_p3
gen_p3.run(EN, PT, write, shaped)
import gen_p4
gen_p4.run(EN, PT, write, shaped)
import gen_p5
gen_p5.run(EN, PT, write, shaped)
import gen_p6
gen_p6.run(EN, PT, write, shaped)
import gen_p7
gen_p7.run(EN, PT, write, shaped)
import gen_p8
gen_p8.run(EN, PT, write, shaped)
import gen_p9
gen_p9.run(EN, PT, write, shaped)
import gen_p10_forms
gen_p10_forms.run(EN, PT, write, shaped)
import gen_p11_mecha; gen_p11_mecha.run(EN, PT, write, shaped)
assert set(EN) == set(PT), set(EN) ^ set(PT)
write('assets/headinajar/lang/en_us.json', EN)
write('assets/headinajar/lang/pt_br.json', PT)
print(len(EN), 'language keys')

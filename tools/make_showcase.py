"""Writes the README showcase smoke scripts (tools/showcase-*.txt). Run them with tools/smoke.sh, then
tools/collect_showcase.py turns the screenshots and bursts into docs/images."""
import math
import os

TOOLS = os.path.dirname(__file__)
HEADER = '''wait 60
/gamemode survival
/time set 6000
/weather clear
/gamerule doDaylightCycle false
/gamerule doMobSpawning false
/gamerule announceAdvancements false
/difficulty peaceful
'''

# 1: the operation, the linked body, the life-support machines
ONE = HEADER + '''/tp @s 0.5 -60 0.5 0 0
/setblock 2 -60 2 headinajar:separation_chair[facing=west]
/setblock 4 -60 2 headinajar:galvanic_pile
/setblock -1 -60 2 headinajar:life_support_cradle
/item replace entity @s armor.chest with minecraft:iron_chestplate
/item replace entity @s armor.legs with minecraft:iron_leggings
/give @s headinajar:head_satchel
chair 2 -60 2
wait 20
/tp @s -1.5 -59 2.5 -90 15
view 0
wait 20
shot chair
useblock 2 -60 2
wait 20
sign 2 -60 2
burst op 115
wait 30
/tp @s -5.5 -60 2.5 -90 10
wait 20
view 2
wait 20
shot linked
view 0
/setblock 0 -60 9 headinajar:saline_mixer[facing=north]
/setblock 2 -60 9 headinajar:body_printer[facing=north]
/setblock -2 -60 9 headinajar:leyden_jar
/setblock -4 -60 9 headinajar:tesla_node
/setblock 4 -60 9 headinajar:relay_mast
/setblock 6 -60 9 headinajar:relay_mast_mk2
/setblock -6 -60 9 headinajar:signal_jammer
/tp @s 0.5 -59 4.5 0 15
wait 40
shot life_support
report done
'''

# 2: hosts, mecha, the Gantry, a Colossus turntable
TWO = HEADER + '''/tp @s 0.5 -60 0.5 0 0
hostify minecraft:cow -1 -60 4
hostify minecraft:enderman 2 -60 5
hostify minecraft:spider 5 -60 5
hostify minecraft:wolf -3 -60 3
/summon headinajar:lunk -5 -60 6 {Rotation:[180f,0f]}
/summon headinajar:rover -7 -60 4 {Rotation:[180f,0f]}
/summon headinajar:spider_walker 7 -60 7 {Rotation:[180f,0f]}
jarhead minecraft:villager 1 -60 2
/effect give @e[type=!player] minecraft:slowness 99999 255 true
wait 60
clienthosts
shot hosts
/tp @s -0.5 -60 2.5 0 0
jar
wait 5
mount
wait 120
sync 60
wait 30
view 2
wait 20
shot hosted
report done
'''

# 4: mecha, the Gantry, a Colossus turntable, drilling
FOUR = HEADER + '''/tp @s 0.5 -60 30.5 0 10
mecha wheelhouse -9 -60 34 battery_rack,cargo_hold,galvanic_stack,armor_plating,rivet_gun,night_optics
mecha skitter -5 -60 34 cargo_hold,galvanic_stack,armor_plating,drill,thermal_imaging
mecha chimera -1 -60 34 hydraulic_legs,jump_jets,galvanic_stack,armor_plating,hemo_syringe,flamethrower,night_optics
mecha kite 3 -60 34 hydraulic_legs,galvanic_stack,armor_plating,rivet_gun,grapple_hook,target_lock
mecha hopper 7 -60 34 hydraulic_legs,jump_jets,galvanic_stack,armor_plating,drill,rivet_gun,night_optics
mecha bulwark 11 -60 35 hydraulic_legs,rocket_pack,cargo_hold,galvanic_stack,armor_plating,coolant_loop,hydraulic_fist,riot_shield,night_optics
wait 60
/tp @s 1.5 -60 24.5 0 5
wait 20
shot frames
/setblock 40 -61 -3 headinajar:assembly_gantry[facing=south]
gantry 40 -61 -3 colossus
/tp @s 40.5 -60 0.5 180 30
useblock 40 -61 -3
wait 20
shot gantry
closegui
mecha colossus 60 -60 6 hydraulic_legs,missile_pod,cargo_hold,galvanic_stack,fusion_bottle,armor_plating,energy_shield,tesla_coil,drill,hydraulic_fist,arc_cannon,plasma_lance,ore_scanner,target_lock
wait 40
{orbit}
/tp @s 79.5 -60 0.5 0 0
mecha hopper 79 -60 3 hydraulic_legs,jump_jets,galvanic_stack,armor_plating,drill,rivet_gun,night_optics
/fill 77 -60 8 81 -57 8 minecraft:stone
wait 20
/tp @s 79.5 -60 2.5 0 0
jar
wait 5
mount
wait 20
view 2
wait 20
arms 1
burst drill 45
arms 0
report done
'''

# 3: the lab, heads at work, the socket, the handbook, Jade
THREE = HEADER + '''/tp @s 0.5 -60 0.5 0 15
/setblock -3 -60 3 headinajar:centrifuge[facing=north]
/setblock -1 -60 3 headinajar:dna_sequencer[facing=north]
/setblock 1 -60 3 headinajar:gene_splicer[facing=north]
/setblock 3 -60 3 headinajar:injection_rig[facing=north]
/setblock 5 -60 3 headinajar:leech_jar
mecha chimera -4 -60 7 hydraulic_legs,jump_jets,galvanic_stack,armor_plating,drill,night_optics
wait 20
lab
wait 30
shot lab
useblock 1 -60 3
wait 10
shot splicer
closegui
/tp @s 0.5 -60 -20.5 180 20
/setblock -3 -60 -24 headinajar:turret_pedestal[facing=south]
headblock -3 -60 -24 minecraft:blaze
/setblock -1 -60 -25 headinajar:cerebral_array[facing=south]
headblock -1 -60 -25 minecraft:zombie,minecraft:villager,minecraft:witch,minecraft:enderman,minecraft:wither_skeleton,minecraft:evoker,minecraft:husk,minecraft:piglin,dragon_head
/setblock 1 -60 -25 headinajar:dna_sequencer[facing=south]
/setblock 1 -60 -24 headinajar:employee_mount[facing=south]
headblock 1 -60 -24 minecraft:villager
/setblock 3 -60 -24 headinajar:neural_socket
headblock 3 -60 -24 minecraft:zombie
wait 60
shot heads_at_work
useblock -1 -60 -25
wait 10
shot array
closegui
/give @s patchouli:guide_book[patchouli:book="headinajar:handbook"]
wait 10
use
wait 30
shot handbook
closegui
/gamemode creative
/tp @s 30.5 -60 0.5 0 0
mecha colossus 30 -60 3 hydraulic_legs,galvanic_stack,armor_plating,drill,target_lock
wait 30
look 0 -20
wait 20
shot jade
/gamemode survival
/setblock 6 -60 -24 headinajar:neural_socket
/tp @s 6.5 -59 -23.5 0 0
jar
wait 5
pilotgenes
wait 30
shot socket
report done
'''


def orbit(cx, cz, radius, frames, y=-58.5):
    lines = []
    for i in range(frames):
        a = 2 * math.pi * i / frames
        x, z = cx + 0.5 + radius * math.sin(a), cz + 0.5 - radius * math.cos(a)
        dx, dz = cx + 0.5 - x, cz + 0.5 - z
        yaw = math.degrees(math.atan2(-dx, dz))
        lines.append(f'/tp @s {x:.2f} {y} {z:.2f} {yaw:.1f} -12')
        lines.append('wait 2')
        lines.append(f'shot colossus_{i:03d}')
    return '\n'.join(lines)


TURNTABLE = 'hud 0\n' + orbit(60, 6, 6.5, 40, y=-59.6) + '\nhud 1'
for name, text in (('1', ONE), ('2', TWO), ('3', THREE), ('4', FOUR.replace('{orbit}', TURNTABLE))):
    with open(os.path.join(TOOLS, f'showcase-{name}.txt'), 'w', encoding='utf-8') as f:
        f.write('# README showcase, part ' + name + '\n' + text)
print('ok')

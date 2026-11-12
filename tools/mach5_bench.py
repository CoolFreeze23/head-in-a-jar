"""Writes tools/mach5-bench.txt: the fifth batch's cost as an A/B inside one client session. Window A: thirty of the
five machines in mixed states (six of each); window B: the same thirty cells holding a furnace (a one-block cube with a
block entity and no renderer, the stand-in the earlier benches used). Three of each, alternating, after a 1,200-tick
warm-up. The cows beside the Leech Jars stay in both windows.

States (by column 0-5): Leyden Jars at 0, 1, 2, 3, 4 cells and one charging from a burning Galvanic Pile behind it;
Neural Sockets with a zombie's head (its clock: the bars flip), a villager's (the staircase), a creeper's (no program),
and three empty; Leech Jars with a cow beside (awake: the press loop and the turned carrier), full (sulking, held),
two empty and resting, and two with a few samples; Jarred Heads (four villagers, two zombies) looking at the camera's
player within six blocks or not; Safety Placards on a wall (static)."""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
Y = -60
XS = [-5, -3, -1, 1, 3, 5]
ROW = {'leyden': 6, 'socket': 10, 'leech': 14, 'head': 18, 'placard': 22}
FUEL = 'Fuel:{Size:1,Items:[{Slot:0b,id:"minecraft:rotten_flesh",count:64}]}'
SAMPLE = '{Slot:%db,id:"headinajar:blood_sample",count:1}'
VILLAGER = 'Entity:{VillagerData:{profession:"minecraft:librarian",type:"minecraft:plains",level:1}}'


def head_item(kind):
    return ('{id:"headinajar:jarred_head",count:1,components:{"minecraft:custom_data":{Type:"minecraft:%s"%s}}}'
            % (kind, ',' + VILLAGER if kind == 'villager' else ''))


def machines():
    out = []
    z = ROW['leyden']
    for i, x in enumerate(XS):
        out.append(f'setblock {x} {Y} {z} headinajar:leyden_jar[facing=north]')
        out.append(f'data merge block {x} {Y} {z} {{Energy:{[0, 40000, 100000, 170000, 250000, 20000][i]}}}')
    out.append(f'setblock {XS[5]} {Y} {z + 1} headinajar:galvanic_pile')
    out.append(f'data merge block {XS[5]} {Y} {z + 1} {{{FUEL}}}')
    z = ROW['socket']
    for i, x in enumerate(XS):
        out.append(f'setblock {x} {Y} {z} headinajar:neural_socket[facing=north]')
        if i < 3:
            kind = ['zombie', 'villager', 'creeper'][i]
            out.append(f'data merge block {x} {Y} {z} {{Items:{{Size:1,Items:[{{Slot:0b,' + head_item(kind)[1:] + ']}}')
    z = ROW['leech']
    for i, x in enumerate(XS):
        out.append(f'setblock {x} {Y} {z} headinajar:leech_jar[facing=north]')
        n = [0, 9, 0, 0, 3, 5][i]
        if n:
            out.append(f'data merge block {x} {Y} {z} {{Items:{{Size:9,Items:[' + ','.join(SAMPLE % s for s in range(n)) + ']}}')
    z = ROW['head']
    for i, x in enumerate(XS):
        out.append(f'setblock {x} {Y} {z} headinajar:jarred_head[facing=south]')
        kind = 'zombie' if i in (1, 4) else 'villager'
        out.append(f'data merge block {x} {Y} {z} {{Type:"minecraft:{kind}"' + (',' + VILLAGER if kind == 'villager' else '') + '}')
    z = ROW['placard']
    for i, x in enumerate(XS):
        out.append(f'setblock {x} {Y} {z} headinajar:safety_placard[facing=north,notice={i * 2}]')
    return out


def cubes():
    out = []
    for kind, z in ROW.items():
        for x in XS:
            out.append(f'setblock {x} {Y} {z} minecraft:furnace')
    return out


lines = ['# The fifth batch bench as an A/B in one session (written by tools/mach5_bench.py).',
         '@observe 1', 'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false', 'gamerule doMobSpawning false',
         'time set noon', 'weather clear', 'hud off',
         # the placards' wall, behind their row
         f'fill -6 {Y} {ROW["placard"] + 1} 6 {Y + 1} {ROW["placard"] + 1} minecraft:stone_bricks']
# the leeches' cows, east of the first leech (awake) and kept through both windows
lines.append(f'summon minecraft:cow {XS[0] + 1.5} {Y} {ROW["leech"] + 0.5} {{NoAI:1b,Silent:1b}}')
import sys
HIGH = 'high' in sys.argv[1:]
lines += ['tp @s 0.5 -50 -6.5', 'look 0 28'] if HIGH else ['tp @s 0.5 -54 -6.5', 'look 0 14']
lines += machines() + ['wait 100', 'shot b5_machines', 'perf warmup 1200', 'wait 1205']
for n in (1, 2, 3):
    lines += machines() + ['kill @e[type=item]', 'wait 100', f'perf machines_{n} 200', 'wait 205']
    lines += cubes() + ['kill @e[type=item]', 'wait 100', f'perf cubes_{n} 200', 'wait 205']
lines += ['shot b5_cubes', 'report bench done']
with open(os.path.join(HERE, 'mach5-bench' + ('-high' if HIGH else '') + '.txt'), 'w', newline='\n') as f:
    f.write('\n'.join(lines) + '\n')

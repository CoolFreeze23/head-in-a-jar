"""Writes tools/mach-bench.txt (or: mach_bench_gen.py MODES OUT, MODES a comma list of machdebug values, one window each): the machine line's render-cost bench (ten of each of the three machines in mixed
states, plus ten other machines, all in view), the same script for the old blocks and the new ones."""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
lines = ['# The machine line bench: 10 Piles, 10 Mixers, 10 Centrifuges in mixed states and 10 other machines, in view.',
         '# Written by tools/mach_bench_gen.py; the same script runs against the old blocks and the new ones.',
         '@observe 1', 'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false', 'gamerule doMobSpawning false',
         'time set noon', 'weather clear', 'hud off', 'tp @s 0.5 -54 -6.5', 'look 0 14']
xs = [-7, -5, -3, -1, 1, 3, 5, 7, -9, 9]
Y = -60
rows = {'pile': 6, 'mixer': 10, 'centrifuge': 14, 'other': 18}
FUEL = 'Fuel:{Size:1,Items:[{Slot:0b,id:"minecraft:rotten_flesh",count:64}]}'
for i, x in enumerate(xs):
    z = rows['pile']
    lines.append(f'setblock {x} {Y} {z} headinajar:galvanic_pile')
    if i < 5:
        lines.append(f'data merge block {x} {Y} {z} {{{FUEL}}}')            # burning
    elif i < 8:
        lines.append(f'data merge block {x} {Y} {z} {{{FUEL},Energy:50000,Burning:2000}}')  # full, paused
    # 8, 9: empty
for i, x in enumerate(xs):
    z = rows['mixer']
    lines.append(f'setblock {x} {Y} {z} headinajar:saline_mixer')
    stock = ('Items:{Size:5,Items:[{Slot:0b,id:"minecraft:sugar",count:64},{Slot:1b,id:"minecraft:kelp",count:64},'
             '{Slot:2b,id:"minecraft:redstone",count:64}]},Water:{Fluid:{id:"minecraft:water",amount:4000}}')
    if i < 5:
        lines.append(f'data merge block {x} {Y} {z} {{{stock},Energy:20000}}')            # working
    elif i < 7:
        lines.append(f'data merge block {x} {Y} {z} {{{stock},Energy:0,Progress:60}}')     # browned out mid-batch
    else:
        lines.append(f'data merge block {x} {Y} {z} {{Items:{{Size:5,Items:[{{Slot:4b,id:"headinajar:saline_bottle",count:6}}]}}}}')  # bottles waiting
for i, x in enumerate(xs):
    z = rows['centrifuge']
    lines.append(f'setblock {x} {Y} {z} headinajar:centrifuge')
    if i < 3:
        lines.append(f'data merge block {x} {Y} {z} {{Energy:20000,Items:{{Size:3,Items:[{{Slot:0b,id:"minecraft:dragon_breath",count:64}}]}}}}')
    elif i < 6:
        lines.append(f'data merge block {x} {Y} {z} {{Energy:20000,Items:{{Size:3,Items:[{{Slot:0b,id:"minecraft:dragon_breath",count:3}}]}}}}')
    else:
        lines.append(f'data merge block {x} {Y} {z} {{Items:{{Size:3,Items:[{{Slot:1b,id:"headinajar:dna_fraction",count:2}},'
                     f'{{Slot:2b,id:"headinajar:plasma_vial",count:2}}]}}}}')
others = ['cerebral_array'] * 2 + ['turret_pedestal'] * 4 + ['neural_socket'] * 4
for x, block in zip(xs, others):
    lines.append(f'setblock {x} {Y} {rows["other"]} headinajar:{block}')
# before every window the jobs are topped up, so the scene is the same in each (working machines keep working)
restock = []
for i, x in enumerate(xs[:5]):
    restock.append(f'data merge block {x} {Y} {rows["mixer"]} {{Water:{{Fluid:{{id:"minecraft:water",amount:4000}}}},Saline:{{}},Energy:20000}}')
for i, x in enumerate(xs[:3]):
    restock.append(f'data merge block {x} {Y} {rows["centrifuge"]} {{Energy:20000}}')
for i, x in enumerate(xs[3:6]):
    restock.append(f'data merge block {x} {Y + 0} {rows["centrifuge"]} {{Energy:20000,Items:{{Size:3,Items:[{{Slot:0b,id:"minecraft:dragon_breath",count:3}}]}}}}')
import sys
modes = [int(m) for m in (sys.argv[1].split(',') if len(sys.argv) > 1 else [])]
lines += ['wait 100', 'shot bench_scene', 'perf warmup 1200', 'wait 1205']
for n in (1, 2, 3):
    for m in (modes or [None]):
        if m is not None:
            lines += [f'machdebug {m}']
        lines += restock + ['wait 20', f'perf bench{"" if m is None else "_m" + str(m)}_{n} 200', 'wait 205']
lines += ['machdebug 0'] if modes else []
lines += ['shot bench_end', 'report bench done']
with open(os.path.join(HERE, sys.argv[2] if len(sys.argv) > 2 else 'mach-bench.txt'), 'w', newline='\n') as f:
    f.write('\n'.join(lines) + '\n')

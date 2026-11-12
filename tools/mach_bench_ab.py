"""Writes tools/mach-bench-ab.txt: the machine line's bench as an A/B inside one client session, so the laptop's
run-to-run noise (which moved the same old build by 20 % between sessions) cancels. Window A: the 30 machines of the
bench scene (tools/mach_bench_gen.py), topped up so they keep working; window B: the same 30 cells holding a furnace,
standing in for the old one-block cube machines (a cube model with a block entity and no renderer, as they were).
Three of each, alternating, after a 1,200-tick warm-up. The ten other machines stay throughout."""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
xs = [-7, -5, -3, -1, 1, 3, 5, 7, -9, 9]
Y = -60
rows = {'pile': 6, 'mixer': 10, 'centrifuge': 14, 'other': 18, 'splicer': 2, 'printer': 22, 'cradle': 26}
import sys
# 'two': the three batch-two machines added (ten each), the Splicer nearest since its renderer stops at 32 blocks
TWO = 'two' in sys.argv[1:]
# 'six': thirty machines, five of each of the six kinds, in mixed states (cells 0, 1, 5, 6 and 8 of each row)
SIX = 'six' in sys.argv[1:]
TWO = TWO or SIX
PICK = (0, 1, 5, 6, 8)
# 'pile': thirty Galvanic Piles, all paused (their buffers full, fuel in the slot), and nothing else
PILE = 'pile' in sys.argv[1:]


def cells():
    return [(i, x) for i, x in enumerate(xs) if not SIX or i in PICK]
FUEL = 'Fuel:{Size:1,Items:[{Slot:0b,id:"minecraft:rotten_flesh",count:64}]}'


def paused_piles():
    out = []
    for kind in ('pile', 'mixer', 'centrifuge'):
        for x in xs:
            out.append(f'setblock {x} {Y} {rows[kind]} headinajar:galvanic_pile')
            out.append(f'data merge block {x} {Y} {rows[kind]} {{{FUEL},Energy:50000}}')
    return out


def machines():
    if PILE:
        return paused_piles()
    out = []
    for i, x in cells():
        z = rows['pile']
        out.append(f'setblock {x} {Y} {z} headinajar:galvanic_pile')
        if i < 5:
            out.append(f'data merge block {x} {Y} {z} {{{FUEL}}}')
        elif i < 8:
            out.append(f'data merge block {x} {Y} {z} {{{FUEL},Energy:50000,Burning:2000}}')
    stock = ('Items:{Size:5,Items:[{Slot:0b,id:"minecraft:sugar",count:64},{Slot:1b,id:"minecraft:kelp",count:64},'
             '{Slot:2b,id:"minecraft:redstone",count:64}]},Water:{Fluid:{id:"minecraft:water",amount:4000}}')
    for i, x in cells():
        z = rows['mixer']
        out.append(f'setblock {x} {Y} {z} headinajar:saline_mixer')
        if i < 5:
            out.append(f'data merge block {x} {Y} {z} {{{stock},Energy:20000}}')
        elif i < 7:
            out.append(f'data merge block {x} {Y} {z} {{{stock},Energy:0,Progress:60}}')
        else:
            out.append(f'data merge block {x} {Y} {z} {{Items:{{Size:5,Items:[{{Slot:4b,id:"headinajar:saline_bottle",count:6}}]}}}}')
    for i, x in cells():
        z = rows['centrifuge']
        out.append(f'setblock {x} {Y} {z} headinajar:centrifuge')
        if i < 3:
            out.append(f'data merge block {x} {Y} {z} {{Energy:20000,Items:{{Size:3,Items:[{{Slot:0b,id:"minecraft:dragon_breath",count:64}}]}}}}')
        elif i < 6:
            out.append(f'data merge block {x} {Y} {z} {{Energy:20000,Items:{{Size:3,Items:[{{Slot:0b,id:"minecraft:dragon_breath",count:3}}]}}}}')
        else:
            out.append(f'data merge block {x} {Y} {z} {{Items:{{Size:3,Items:[{{Slot:1b,id:"headinajar:dna_fraction",count:2}},'
                       f'{{Slot:2b,id:"headinajar:plasma_vial",count:2}}]}}}}')
    if TWO:
        out += batch_two()
    return out


SALINE = 'Saline:{Fluid:{id:"headinajar:cranial_saline",amount:4000}}'
MATS = ('Items:{Size:4,Items:[{Slot:0b,id:"minecraft:rotten_flesh",count:16},{Slot:1b,id:"minecraft:bone_block",count:8},'
        '{Slot:2b,id:"minecraft:redstone_block",count:4},{Slot:3b,id:"minecraft:slime_ball",count:8}]}')
SPLICE = ('Items:{Size:4,Items:[{Slot:0b,id:"minecraft:glowstone_dust",count:2},{Slot:1b,id:"headinajar:bio_gel",count:2},'
          '{Slot:2b,id:"minecraft:glass_bottle",count:2}]}')


def batch_two():
    out = []
    for i, x in cells():
        z = rows['cradle']
        out.append(f'setblock {x} {Y} {z} headinajar:life_support_cradle')
        if i < 5:
            out.append(f'data merge block {x} {Y} {z} {{Energy:40000,{SALINE}}}')
            # a head docked as a dock leaves it (registered, or the Cradle lets it go): an offline player's
            out.append(f'cradlehead {x} {Y} {z} {i + 1}' + (' compact' if i % 2 else ''))
        elif i < 8:
            out.append(f'data merge block {x} {Y} {z} {{Energy:40000,{SALINE}}}')
        else:
            out.append(f'cradlehead {x} {Y} {z} {i + 1}')
    for i, x in cells():
        z = rows['printer']
        out.append(f'setblock {x} {Y} {z} headinajar:body_printer')
        if i < 4:
            out.append(f'data merge block {x} {Y} {z} {{Energy:250000,Progress:{100 + 150 * i},Client:[I;9,9,9,{i}],ClientName:"Dev"}}')
        elif i < 7:
            out.append(f'data merge block {x} {Y} {z} {{Energy:250000,{SALINE},{MATS}}}')
    for i, x in cells():
        z = rows['splicer']
        out.append(f'setblock {x} {Y} {z} headinajar:gene_splicer')
        if i < 4:
            out.append(f'data merge block {x} {Y} {z} {{Energy:40000,Gene0:"spider_climb",Gene1:"golem_frame",Running:1b,Progress:{100 * i},{SPLICE}}}')
        elif i < 7:
            out.append(f'data merge block {x} {Y} {z} {{Items:{{Size:4,Items:[{{Slot:3b,id:"headinajar:serum_cassette",count:1}}]}}}}')
    return out


def cubes():
    out = []
    for kind in ('pile', 'mixer', 'centrifuge') + (('cradle', 'printer', 'splicer') if TWO else ()):
        for i, x in cells():
            out.append(f'setblock {x} {Y} {rows[kind]} minecraft:furnace')
    return out


lines = ['# The machine line bench as an A/B in one session (written by tools/mach_bench_ab.py).',
         '@observe 1', 'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false', 'gamerule doMobSpawning false',
         'time set noon', 'weather clear', 'hud off', 'tp @s 0.5 -54 -6.5', 'look 0 14']
for x, block in zip(xs, ['cerebral_array'] * 2 + ['turret_pedestal'] * 4 + ['neural_socket'] * 4):
    lines.append(f'setblock {x} {Y} {rows["other"]} headinajar:{block}')
# the warm-up restocks halfway, so no print reaches its end (a delivery) and no splice finishes inside a window
lines += machines() + ['wait 100', 'shot ab_machines', 'perf warmup 1200'] + (['wait 600'] + machines() + ['wait 605'] if TWO else ['wait 1205'])
particles = 'particles' in sys.argv[1:]
# 'split': the same machines without their contents (machdebug 1), then with their renderer drawing nothing (2)
split = 'split' in sys.argv[1:]
# 'levers': the same machines drawn as before each cost lever (machdebug 32: no shared atlas buffers; 64: no baked rest
# pose; 96: neither)
levers = 'levers' in sys.argv[1:]
# 'shadow': the same machines with their parts drawn in a shader pack's shadow pass too (machdebug 256)
shadow = 'shadow' in sys.argv[1:]
for n in (1, 2, 3):
    lines += machines() + ['kill @e[type=item]', 'wait 100', f'perf machines_{n} 200', 'wait 205']
    if particles:
        # the same machines without their running particles (machdebug 16)
        lines += ['machdebug 16', 'wait 20', f'perf noparticles_{n} 200', 'wait 205', 'machdebug 0']
    if split:
        lines += ['machdebug 1', 'wait 20', f'perf nocontents_{n} 200', 'wait 205',
                  'machdebug 2', 'wait 20', f'perf norenderer_{n} 200', 'wait 205', 'machdebug 0']
    if levers:
        for bits, label in ((32, 'noatlas'), (64, 'norest'), (96, 'before')):
            lines += [f'machdebug {bits}', 'wait 40', f'perf {label}_{n} 200', 'wait 205']
        lines += ['machdebug 0', 'wait 40']
    if shadow:
        lines += ['machdebug 256', 'wait 40', f'perf shadows_{n} 200', 'wait 205', 'machdebug 1', 'wait 40', f'perf nocontents_{n} 200',
                  'wait 205', 'machdebug 2', 'wait 40', f'perf norenderer_{n} 200', 'wait 205', 'machdebug 0', 'wait 40']
    lines += cubes() + ['kill @e[type=item]', 'wait 100', f'perf cubes_{n} 200', 'wait 205']
lines += ['shot ab_cubes', 'report ab done']
name = 'mach-bench-ab' + ('-pile' if PILE else '-six' if SIX else '-two' if TWO else '') + ('-particles' if particles else '') + ('-split' if split else '') + ('-levers' if levers else '') + ('-shadow' if shadow else '') + '.txt'
with open(os.path.join(HERE, name), 'w', newline='\n') as f:
    f.write('\n'.join(lines) + '\n')

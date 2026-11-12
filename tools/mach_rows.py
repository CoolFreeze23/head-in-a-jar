"""Writes tools/mach-18-row12.txt and tools/mach-19-row23.txt: the machine line working in one row beside the Separation
Chair, two blocks apart, facing south along z 0. Twelve: the chair, the first six machines and batch three, by day, at
night and by torchlight. Twenty-three: everything on the kit, by day and at night, with the row seen whole and in three
stretches from about ten blocks."""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
Y = -60
SALINE = 'Saline:{Fluid:{id:"headinajar:cranial_saline",amount:3000}}'


def tesla_target(x, y, z):
    return ((x & 0x3FFFFFF) << 38) | ((z & 0x3FFFFFF) << 12) | (y & 0xFFF)


# (block, setup lines after the setblock; {x} is the machine's x)
FIRST = [
    ('separation_chair', []),
    ('galvanic_pile', ['/data merge block {x} -60 0 {{Fuel:{{Size:1,Items:[{{Slot:0b,id:"minecraft:rotten_flesh",count:12}}]}}}}']),
    ('saline_mixer', ['/data merge block {x} -60 0 {{Energy:20000,Saline:{{Fluid:{{id:"headinajar:cranial_saline",amount:2500}}}},'
                      'Water:{{Fluid:{{id:"minecraft:water",amount:4000}}}},Items:{{Size:5,Items:[{{Slot:0b,id:"minecraft:sugar",count:16}},'
                      '{{Slot:1b,id:"minecraft:kelp",count:16}},{{Slot:2b,id:"minecraft:redstone",count:16}},{{Slot:4b,id:"headinajar:saline_bottle",count:4}}]}}}}']),
    ('centrifuge', ['/data merge block {x} -60 0 {{Energy:20000,Items:{{Size:3,Items:[{{Slot:0b,id:"minecraft:dragon_breath",count:16}}]}}}}']),
    ('life_support_cradle', ['/data merge block {x} -60 0 {{Energy:40000,' + SALINE.replace('{', '{{').replace('}', '}}') + '}}', 'cradlehead {x} -60 0 1']),
    ('body_printer', ['/data merge block {x} -60 0 {{Energy:250000,Progress:200,Client:[I;55,66,77,88],ClientName:"Dev"}}']),
    ('gene_splicer', ['/data merge block {x} -60 0 {{Energy:40000,Gene0:"spider_climb",Gene1:"golem_frame",Running:1b,Progress:0,'
                      'Items:{{Size:4,Items:[{{Slot:0b,id:"minecraft:glowstone_dust",count:2}},{{Slot:1b,id:"headinajar:bio_gel",count:2}},'
                      '{{Slot:2b,id:"minecraft:glass_bottle",count:2}}]}}}}']),
]
THREE = [
    # the dock: the second player's jar on the plate (set below, once the bystander is a jar)
    ('charging_dock', ['/data merge block {x} -60 0 {{Energy:20000}}']),
    # the node sends to a second node behind it
    ('tesla_node', ['/setblock {x} -60 -6 headinajar:tesla_node[facing=south]', '/data merge block {x} -60 -6 {{Energy:0}}',
                    '/data merge block {x} -60 0 {{Energy:10000,Target:{target}L}}']),
    ('injection_rig', []),
    ('dna_sequencer', []),
    ('cerebral_array', ['headblock {x} -60 0 villager,villager,zombie,villager,creeper,villager,skeleton,villager,pig',
                        '/data merge block {x} -60 0 {{Broth:{{Fluid:{{id:"headinajar:nutrient_broth",amount:6000}}}}}}']),
    ('employee_mount', ['headblock {x} -60 0 villager']),
]
FOUR = [
    ('relay_mast', ['/data merge block {x} -60 0 {{Energy:50000}}', 'mastroute {x} -60 0 {x} -60 -20']),
    ('relay_mast_mk2', ['/data merge block {x} -60 0 {{Energy:50000}}', 'mastroute {x} -60 0 -20 -60 0']),
    ('signal_jammer', ['/data merge block {x} -60 0 {{Energy:50000}}', 'jam {x} -60 0']),
    ('turret_pedestal', ['/data merge block {x} -60 0 {{Energy:40000}}', 'headblock {x} -60 0 blaze',
                         '/summon minecraft:zombie {x} -60 -8 {{NoAI:1b}}']),
    # the Cloakroom: a host body checked in by the player (below, from beside it); a husk, which does not burn by day
    ('body_locker', ['hostify minecraft:husk {x} -60 2']),
]
FIVE = [
    ('leyden_jar', ['/data merge block {x} -60 0 {{Energy:200000}}']),
    ('neural_socket', ['headblock {x} -60 0 zombie']),
    ('leech_jar', ['/summon minecraft:cow {x}.5 -60 1.6 {{NoAI:1b,Rotation:[90f,0f]}}']),
    ('jarred_head', None),
    ('safety_placard', None),
]


def row(machines):
    out = []
    for i, (block, setup) in enumerate(machines):
        x = 2 * i
        if block == 'jarred_head':
            out.append(f'jarhead minecraft:villager {x} -60 0')
            continue
        if block == 'safety_placard':
            out += [f'/setblock {x} -60 -1 minecraft:stone', f'/setblock {x} -60 0 headinajar:safety_placard[facing=south,notice=3]']
            continue
        out.append(f'/setblock {x} -60 0 headinajar:{block}[facing=south]')
        out += [s.format(x=x, target=tesla_target(x, Y, -6)) for s in setup]
    return out


def xs(machines, block):
    return 2 * [b for b, _ in machines].index(block)


def at_work(machines):
    """The second player's parts: a jar on the dock, the sequencer read; the player checks the host into the Cloakroom."""
    out = []
    names = [b for b, _ in machines]
    if 'dna_sequencer' in names:
        q = xs(machines, 'dna_sequencer')
        out += [f'/tp @s {q}.5 -59 3 180 0', 'lab', 'wait 5',
                f'bystander spawn {q} -60 3 180', f'bystander useblock {q} -60 0 north', 'bystander button 1', 'bystander close', 'wait 10']
    if 'charging_dock' in names:
        d = xs(machines, 'charging_dock')
        # its body is left where it changes form: behind the row's far end, clear of every machine
        far = 2 * len(machines) + 2
        out += [f'bystander tp {far} -60 -4 180 0', 'bystander form jar', 'wait 20', f'bystander tp {d} -59.7 0 180 0', 'wait 10']
    if 'body_locker' in names:
        k = xs(machines, 'body_locker')
        out += [f'/tp @s {k + 2}.5 -59 3.5 150 10', 'wait 20', f'useblock {k} -60 0 north', 'wait 40']
    return out


def torches(n):
    return [f'/setblock {2 * i + 1} -60 2 minecraft:torch' for i in range(0, n, 2)]


def script(name, machines, head, torch):
    n = len(machines)
    mid = n - 1  # the row's centre x (2 * (n - 1) / 2)
    whole = f'/tp @s {mid}.5 -57.6 {max(12, n + 8)} 180 8'
    lines = [f'# {head} (written by tools/mach_rows.py)', '@observe 20', 'wait 40', '/gamerule doMobSpawning false',
             '/gamerule doDaylightCycle false', '/time set 6000', '/clear @s', 'hud off']
    lines += row(machines) + at_work(machines)
    lines += [whole, 'wait 60', f'shot {name}_day', f'frames {name}_day 100', 'wait 105']
    # stretches of about eight machines seen from about ten blocks
    for s, start in enumerate(range(0, n, 8)):
        cx = 2 * min(start + 3.5, (n - 1 + start) / 2)
        lines += [f'/tp @s {cx + 0.5:.1f} -58.6 10 180 8', 'wait 10', f'shot {name}_ten_{s + 1}', f'frames {name}_ten_{s + 1} 100', 'wait 105']
    lines += ['/time set 18000', whole, 'wait 20', f'shot {name}_night', f'frames {name}_night 80', 'wait 85']
    if torch:
        lines += torches(n) + ['wait 20', f'shot {name}_torch', f'frames {name}_torch 80', 'wait 85']
    lines += ['/time set 6000', f'report {name} done']
    with open(os.path.join(HERE, f'mach-{"18" if n == 13 else "19"}-{name}.txt'), 'w', newline='\n') as f:
        f.write('\n'.join(lines) + '\n')


script('row12', FIRST + THREE, 'The first twelve machines working in a row beside the Separation Chair: by day, at night, by torchlight', True)
script('row23', FIRST + THREE + FOUR + FIVE, 'Every machine on the kit working in one row beside the Separation Chair: whole and in stretches, by day and at night', False)

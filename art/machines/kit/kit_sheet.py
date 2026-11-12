"""The kit on one sheet: every shared part alone, side by side, with the house clips. Review only (not a machine).

    python art/machines/kit/kit_sheet.py [--review OUT.bbmodel]

Writes kit_parts.bbmodel (+ placeholder PNG) next to this file. Slots along x, every part on its own small enamel stub
(a stand-in for a machine body, in the group `standin_stubs`) so the part's own construction is what is seen.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import kit  # noqa: E402

m = kit.Model('kit_parts', budget=4)


def stub(name, x0, x1, y1, z0=-2, z1=2):
    if not any(b[0] == 'standin_stubs' for b in m.bones):
        m.group('standin_stubs', None)
    m.box('stub_' + name, [x0, 0, z0], [x1, y1, z1], 'enamel2', 'standin_stubs')


def at(x):
    m.off = [x, 0, 0]


# K1: plinth and feet
at(-96)
kit.plinth(m, -7, 7, -7, 7, prefix='k1_')

# K2: casing with rimmed panels, on its own K1 plinth (the plinth 1 px inside the body)
at(-76)
kit.plinth(m, -5, 5, -5, 5, prefix='k2_')
kit.casing(m, 'k2_casing', -7, 7, -7, 7, 3, 15, rims=('north', 'east', 'west'))

# K3: the lamp, and its six states side by side (lens materials stand for the state textures)
at(-56)
stub('lamp', -4, 4, 10)
kit.lamp(m, 'north', -2, -2, 4, prefix='k3_')
at(-38)
stub('lamp_states', -14, 18, 8)
for i, st in enumerate(['off', 'ready', 'wait', 'work', 'low', 'fault']):
    kit.lamp(m, 'north', -2, -13 + 5 * i, 3, prefix='k3%s_' % st, lens_mat='lens_' + st)

# K4: gauge, standard and small
at(-16)
stub('gauge6', -4, 4, 10)
kit.gauge(m, 'north', -2, -3, 3, 6, prefix='k4_gauge6')
at(-4)
stub('gauge4', -3, 3, 8)
kit.gauge(m, 'north', -2, -2, 3, 4, prefix='k4_gauge4')

# K6b: side port (on the stub's front so it is seen square on)
at(8)
stub('port', -4, 4, 10)
kit.side_port(m, 'north', -2, 0, 2, depth=2, prefix='k6b_port')

# K7: maker's plate
at(19)
stub('plate', -3, 3, 6)
kit.makers_plate(m, 'north', -2, -2, 2, name='k7_plate')

# K10: terminal on a stub's top
at(29)
stub('terminal', -3, 3, 6)
kit.terminal(m, 0, 0, 6, 'k10_terminal')

# K8: a striped moving rim sample (4 px band)
at(40)
stub('stripe', -4, 4, 6)
kit.stripe_box(m, 'k8_band', [-5, 6, -3], [5, 10, 3])

# K5: hatch (hinged) on a stub
at(54)
stub('hatch', -5, 5, 12, -3, 3)
HATCH = kit.hatch(m, 'north', -3, -3, 3, 6, 6, bone='hatch', hinge='left')

# K9: flag on the stub's east side
at(68)
stub('flag', -4, 4, 12, -4, 4)
FLAG = kit.flag(m, 4, 8, 3, side='east', bone='flag')

# K12: the lab bench with its K5 drawer and K3 lamp
at(88)
DRAWER = kit.bench(m, with_lamp=True, drawer_bone='drawer')

m.off = [0, 0, 0]
PATH = os.path.join(HERE, 'kit_parts.bbmodel')

if __name__ == '__main__':
    m.write(PATH, keep_animations=False)
    op, sh = kit.hatch_clips(HATCH)
    do, dc = kit.drawer_clips(DRAWER)
    up, dn = kit.flag_clips(FLAG)
    clips = [('hatch_open', op), ('hatch_shut', sh), ('drawer_open', do), ('drawer_close', dc),
             ('flag_up', up), ('flag_down', dn), ('refuse', kit.refuse_clip('hatch', 'rotation', 1, 3.0))]
    review = sys.argv[sys.argv.index('--review') + 1] if '--review' in sys.argv else None
    kit.write_clips(PATH, clips, review=review)
    print('wrote', PATH)

"""Review tool: hang module models on a frame's socket bones, the way the game's module layer will, in a copy.

    python art/mecha/fit_modules.py FRAME.bbmodel OUT.bbmodel slot=module [slot=module ...]
    e.g. legs=hydraulic_legs arm_l=drill back=jump_jets

For each module bone named `mount` the module is drawn at the slot's socket; for `mount_<part>` at socket_<part>_l and
socket_<part>_r. Module cubes and bones are copied under the socket with their coordinates offset by the socket's
pivot, so they take the socket's whole transform (rest rotation included) and move with the limb. Each module keeps its
own texture. The frame file is never edited: OUT is a review copy.
"""
import copy
import json
import os
import sys
import uuid

HERE = os.path.dirname(os.path.abspath(__file__))
SOCKET = {'back': 'socket_back', 'core': 'socket_core', 'torso_0': 'socket_torso_0', 'torso_1': 'socket_torso_1',
          'arm_l': 'socket_arm_l', 'arm_r': 'socket_arm_r', 'sensor': 'socket_sensor'}


def add(a, b):
    return [a[i] + b[i] for i in range(3)]


def main():
    frame, out = sys.argv[1], sys.argv[2]
    fits = [a.split('=') for a in sys.argv[3:]]
    m = json.load(open(frame, encoding='utf-8'))
    groups = {g['name']: g for g in m['groups']}

    def find_outline(nodes, u):
        for n in nodes:
            if isinstance(n, dict):
                if n['uuid'] == u:
                    return n
                r = find_outline(n['children'], u)
                if r:
                    return r
        return None

    for slot, mod in fits:
        mm = json.load(open(os.path.join(HERE, 'modules', mod + '.bbmodel'), encoding='utf-8'))
        tex = copy.deepcopy(mm['textures'][0])
        tex['uuid'] = str(uuid.uuid4())
        tex['id'] = str(len(m['textures']))
        ti = len(m['textures'])
        # module UVs are in the module's own 64x64 resolution; the frame's project resolution may differ, so scale
        sx = m['resolution']['width'] / mm['resolution']['width']
        sy = m['resolution']['height'] / mm['resolution']['height']
        m['textures'].append(tex)
        mgroups = {g['uuid']: g for g in mm['groups']}
        mel = {e['uuid']: e for e in mm['elements']}
        for top in mm['outliner']:
            tname = mgroups[top['uuid']]['name']
            if tname == 'mount':
                sockets = [SOCKET[slot]]
            else:
                part = tname[len('mount_'):]
                sockets = ['socket_%s_l' % part, 'socket_%s_r' % part]
            for sock in sockets:
                sg = groups[sock]
                piv = sg['origin']

                def copy_node(node, parent_out):
                    if isinstance(node, str):
                        e = copy.deepcopy(mel[node])
                        e['uuid'] = str(uuid.uuid4())
                        if e.get('type', 'cube') == 'cube':
                            e['from'], e['to'], e['origin'] = add(e['from'], piv), add(e['to'], piv), add(e['origin'], piv)
                            for f in e['faces'].values():
                                f['texture'] = ti
                                pass  # UVs stay in the module texture's own uv_width/uv_height
                            e['box_uv'] = False
                        else:
                            e['position'] = add(e['position'], piv)
                            e['name'] = '%s_%s_%s' % (mod, sock, e['name'])
                        m['elements'].append(e)
                        parent_out.append(e['uuid'])
                        return
                    g = copy.deepcopy(mgroups[node['uuid']])
                    g['uuid'] = str(uuid.uuid4())
                    g['name'] = '%s_%s_%s' % (mod, sock, g['name'])
                    g['origin'] = add(g['origin'], piv)
                    m['groups'].append(g)
                    o = {'uuid': g['uuid'], 'isOpen': False, 'children': []}
                    parent_out.append(o)
                    for ch in node['children']:
                        copy_node(ch, o['children'])

                so = find_outline(m['outliner'], sg['uuid'])
                copy_node(top, so['children'])
    json.dump(m, open(out, 'w', encoding='utf-8'), indent=1)
    print('wrote', out)


if __name__ == '__main__':
    main()

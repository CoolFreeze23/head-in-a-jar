"""Render frames headlessly and compose a labelled contact sheet.
usage: python sheet.py OUT.png MODEL.bbmodel COLS SIZE spec [spec ...]
(review only; frames are written beside MODEL; render_many.ts must sit next to this file)
spec: clip:tick:view  (clip '-' = rest pose), view one of front back left right three-quarter iso top
      append :o for orthographic, :f for flat lighting, :b to turn the model round (iso then looks from behind and above)
"""
import json
import os
import subprocess
import sys
from PIL import Image, ImageDraw

SCR = os.path.dirname(os.path.abspath(__file__))
# the blockbench-mcp install's node_modules folder (set BLOCKBENCH_MCP_MODULES)
BBM = os.environ['BLOCKBENCH_MCP_MODULES'].replace(os.sep, '/')

out, model, cols, size = sys.argv[1], os.path.abspath(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])
specs = sys.argv[5:]
FR = os.path.join(os.path.dirname(model), '_review_frames')  # delete after use
os.makedirs(FR, exist_ok=True)
reqs, paths, labels = [], [], []
def turned(m):
    q = m.replace('.bbmodel', '_back.bbmodel')
    d = json.load(open(m, encoding='utf-8'))
    for g in d['groups']:
        if g['name'] == 'root':
            g['rotation'] = [0, 180, 0]
    json.dump(d, open(q, 'w', encoding='utf-8'))
    return q
_turned = {}
for i, sp in enumerate(specs):
    parts = sp.split(':')
    clip, tick, view = parts[0], parts[1], parts[2]
    flags = parts[3:]
    p = os.path.join(FR, 'f%03d_%s_%s_%s.png' % (i, clip, tick, view))
    mf = model
    if 'b' in flags:
        _turned.setdefault(model, turned(model))
        mf = _turned[model]
    r = {'file': mf, 'view': view, 'width': size, 'height': size, 'output': p,
         'lighting': 'flat' if 'f' in flags else 'studio', 'orthographic': 'o' in flags}
    if clip != '-':
        r['clip'] = clip
        r['time'] = float(tick) / 20.0
    reqs.append(r)
    paths.append(p)
    labels.append('%s t%s %s%s' % (clip, tick, 'behind-' if 'b' in flags else '', view))
rq = os.path.join(SCR, 'req.json')
json.dump(reqs, open(rq, 'w'))
env = dict(os.environ)
env['PATH'] = BBM + '/bun/bin;' + env.get('NODE_HOME', '') + ';' + env['PATH']
r = subprocess.run([BBM + '/bun/bin/bun.exe', 'run', os.path.join(SCR, 'render_many.ts'), rq], env=env,
                   capture_output=True, text=True)
print(r.stdout[-2000:], r.stderr[-2000:])
rows = (len(paths) + cols - 1) // cols
sheet = Image.new('RGB', (cols * size, rows * (size + 14)), (30, 32, 38))
d = ImageDraw.Draw(sheet)
for i, (p, lab) in enumerate(zip(paths, labels)):
    x, y = (i % cols) * size, (i // cols) * (size + 14)
    if os.path.exists(p):
        im = Image.open(p).convert('RGB')
        sheet.paste(im, (x, y + 14))
    d.text((x + 4, y + 1), lab, fill=(230, 230, 230))
sheet.save(out)
print('sheet', out)

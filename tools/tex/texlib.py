"""Texture toolchain library: palette, pixel maps, compiler, manifest and its lock.

Nothing here uses randomness. Compiling the same map with the same palette always gives the same bytes.
"""
import hashlib
import io
import json
import os
import re
import time

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
ASSET_TEX = os.path.join('src', 'main', 'resources', 'assets', 'headinajar', 'textures')
MAP_EXT = '.txt'


class TexError(Exception):
    pass


class Paths:
    def __init__(self, root=None):
        self.root = os.path.abspath(root or DEFAULT_ROOT)
        self.palette = os.path.join(self.root, 'art', 'palette.json')
        self.art = os.path.join(self.root, 'art', 'textures')
        self.manifest = os.path.join(self.art, 'manifest.json')
        self.lock = os.path.join(self.art, '.manifest.lock')
        self.assets = os.path.join(self.root, ASSET_TEX)

    def asset(self, rel):
        return os.path.join(self.assets, *rel.split('/'))

    def map_for(self, png_rel):
        return os.path.join(self.art, *png_rel[:-4].split('/')) + MAP_EXT

    def map_rel(self, map_path):
        return os.path.relpath(os.path.abspath(map_path), self.art).replace('\\', '/')

    def asset_rel(self, path):
        """Accepts block/x.png, a path inside the assets folder, or a map path; returns block/x.png."""
        p = path.replace('\\', '/')
        ab = os.path.abspath(path)
        for base in (self.assets, self.art):
            if ab.lower().startswith(base.lower() + os.sep):
                p = os.path.relpath(ab, base).replace('\\', '/')
                break
        if p.endswith(MAP_EXT):
            p = p[:-len(MAP_EXT)] + '.png'
        if not p.endswith('.png') and not p.endswith('.png.mcmeta'):
            p += '.png'
        return p


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def file_sha(path):
    with open(path, 'rb') as fh:
        return sha256(fh.read())


def hex_rgb(h):
    h = h.lstrip('#')
    if not re.fullmatch(r'[0-9a-fA-F]{6}', h):
        raise TexError('bad hex colour %r' % h)
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


def rgb_hex(c):
    return '%02x%02x%02x' % tuple(c[:3])


# ---------------------------------------------------------------------------------------- palette

class Palette:
    """keys: 'brass.3' -> rgb; aliases: 'b3' -> 'brass.3' (or 'teal_glass.1@110', or 'clear')."""

    def __init__(self, data, set_name=None):
        self.data = data
        self.set = set_name or data['default']
        if self.set not in data['sets']:
            raise TexError('palette has no set %r (sets: %s)' % (self.set, ', '.join(data['sets'])))
        self.keys = {}
        self.aliases = {}
        self.labels = {}
        self.order = []
        for name, m in data['house'].items():
            self._add(name, m['alias'], m[self.set], m['label'][self.set])
        for name, m in data['shared'].items():
            self._add(name, m['alias'], m['ramp'], m['label'])
        for a, v in data.get('aliases', {}).items():
            if a in self.aliases:
                raise TexError('palette alias %r defined twice' % a)
            self.aliases[a] = v

    def _add(self, name, letter, ramp, label):
        self.order.append(name)
        self.labels[name] = label
        for i, h in enumerate(ramp):
            key = '%s.%d' % (name, i)
            alias = '%s%d' % (letter, i)
            if alias in self.aliases:
                raise TexError('palette alias %r used by two materials' % alias)
            self.keys[key] = hex_rgb(h)
            self.aliases[alias] = key

    def ramp(self, name):
        out = []
        i = 0
        while '%s.%d' % (name, i) in self.keys:
            out.append(self.keys['%s.%d' % (name, i)])
            i += 1
        return out

    def resolve(self, value):
        """value: 'clear', 'brass.3', 'b3', '#rrggbb', each optionally with '@alpha'. Returns (rgba, key or None)."""
        v = value.strip()
        alpha = 255
        if '@' in v:
            v, a = v.split('@', 1)
            v = v.strip()
            try:
                alpha = int(a)
            except ValueError:
                raise TexError('bad alpha in %r' % value)
            if not 0 <= alpha <= 255:
                raise TexError('alpha out of range in %r' % value)
        if v == 'clear':
            return (0, 0, 0, 0), 'clear'
        if v in self.aliases:
            target = self.aliases[v]
            rgba, key = self.resolve(target)
            if '@' in target and '@' not in value:
                return rgba, key
            return rgba[:3] + (alpha if rgba[3] else 0,), key
        if v in self.keys:
            return self.keys[v] + (alpha,), v
        if v.startswith('#'):
            return hex_rgb(v) + (alpha,), None
        raise TexError('%r is not a palette key, palette alias, #hex or clear' % value)

    def colour_keys(self):
        """rgb -> list of keys in this set (for exact-hex swaps and palette membership)."""
        out = {}
        for k, c in self.keys.items():
            out.setdefault(c, []).append(k)
        return out


def load_palette(paths, set_name=None):
    with open(paths.palette, encoding='utf-8') as fh:
        data = json.load(fh)
    return Palette(data, set_name)


# ---------------------------------------------------------------------------------------- maps

KEY_LINE = re.compile(r'^(\S{1,2})\s*(?:=\s*)?(.*)$')


class PixelMap:
    def __init__(self):
        self.path = None
        self.texture = None
        self.w = self.h = None
        self.frames = 1
        self.frametime = None
        self.interpolate = False
        self.key = {}          # alias -> (value string, glow flag)
        self.key_order = []
        self.rows = []         # list of lists of aliases
        self.notes = []


def _strip_comment(line):
    # A comment is a whole line starting with '#', or ' # ' / tab-# later on the line. '#rrggbb' values stay.
    s = line.strip()
    if s.startswith('#') and not re.match(r'#[0-9a-fA-F]{6}\b', s):
        return ''
    m = re.search(r'\s#(\s|$)', line)
    if m:
        line = line[:m.start()]
    return line.rstrip()


def parse_map(path):
    with open(path, encoding='utf-8') as fh:
        lines = fh.read().splitlines()
    pm = PixelMap()
    pm.path = path
    section = None
    for n, raw in enumerate(lines, 1):
        where = '%s:%d' % (os.path.basename(path), n)
        if section == 'pixels':
            s = raw.strip()
            if not s or s.startswith('#'):
                continue
            pm.rows.append((n, re.sub(r'\s+', '', s)))
            continue
        line = _strip_comment(raw)
        if not line.strip():
            continue
        m = re.match(r'^(texture|size|frames|frametime|interpolate|key|pixels)\s*:\s*(.*)$', line.strip())
        if m and not raw[:1].isspace():
            field, val = m.group(1), m.group(2).strip()
            if field == 'texture':
                pm.texture = val.replace('\\', '/')
                if not pm.texture.endswith('.png'):
                    raise TexError('%s: texture must name a .png' % where)
            elif field == 'size':
                sm = re.fullmatch(r'(\d+)\s*x\s*(\d+)', val)
                if not sm:
                    raise TexError('%s: size must be WxH, e.g. 16x16' % where)
                pm.w, pm.h = int(sm.group(1)), int(sm.group(2))
            elif field == 'frames':
                pm.frames = int(val)
            elif field == 'frametime':
                pm.frametime = int(val)
            elif field == 'interpolate':
                pm.interpolate = val.lower() in ('true', 'yes', '1')
            elif field == 'key':
                section = 'key'
            elif field == 'pixels':
                section = 'pixels'
            continue
        if section == 'key':
            km = KEY_LINE.match(line.strip())
            alias, rest = km.group(1), km.group(2).strip()
            glow = False
            toks = rest.split()
            if toks and toks[-1] == 'glow':
                glow = True
                toks = toks[:-1]
            value = ''.join(toks) if toks else alias
            if alias in pm.key:
                raise TexError('%s: alias %r defined twice' % (where, alias))
            pm.key[alias] = (value, glow)
            pm.key_order.append(alias)
            continue
        raise TexError('%s: unexpected line %r (fields are texture, size, frames, frametime, interpolate, key, pixels)'
                       % (where, raw.strip()))
    if not pm.texture:
        raise TexError('%s: no texture: line' % path)
    if not pm.w:
        raise TexError('%s: no size: line' % path)
    if not pm.key:
        raise TexError('%s: no key: section' % path)
    lens = {len(a) for a in pm.key}
    if len(lens) != 1:
        raise TexError('%s: every alias in one map must have the same length (found %s)'
                       % (path, sorted(lens)))
    cw = lens.pop()
    want_rows = pm.h * pm.frames
    if len(pm.rows) != want_rows:
        raise TexError('%s: %d pixel rows, expected %d (%d high x %d frames)'
                       % (path, len(pm.rows), want_rows, pm.h, pm.frames))
    cells = []
    for n, s in pm.rows:
        if len(s) != pm.w * cw:
            raise TexError('%s:%d: row has %d characters, expected %d (%d pixels x %d)'
                           % (os.path.basename(path), n, len(s), pm.w * cw, pm.w, cw))
        row = [s[i:i + cw] for i in range(0, len(s), cw)]
        for a in row:
            if a not in pm.key:
                raise TexError('%s:%d: %r is not in the key' % (os.path.basename(path), n, a))
        cells.append(row)
    pm.rows = cells
    if pm.frames > 1 and any(g for _, g in pm.key.values()):
        raise TexError('%s: GeckoLib glowmasks cannot be combined with animated textures' % path)
    used = {a for r in cells for a in r}
    pm.notes = ['alias %r is in the key but never used' % a for a in pm.key_order if a not in used]
    return pm


class Compiled:
    def __init__(self, pm, image, glow, mcmeta, offpalette):
        self.map = pm
        self.image = image
        self.glow = glow
        self.mcmeta = mcmeta
        self.offpalette = offpalette

    def outputs(self):
        """[(asset rel path, bytes)] in a fixed order."""
        rel = self.map.texture
        out = [(rel, png_bytes(self.image))]
        if self.glow is not None:
            out.append((rel[:-4] + '_glowmask.png', png_bytes(self.glow)))
        if self.mcmeta is not None:
            out.append((rel + '.mcmeta', self.mcmeta))
        return out


def compile_map(pm, palette):
    colours = {}
    glow = set()
    offpal = []
    for a, (value, g) in pm.key.items():
        try:
            rgba, key = palette.resolve(value)
        except TexError as e:
            raise TexError('%s: alias %r: %s' % (os.path.basename(pm.path), a, e))
        colours[a] = rgba
        if key is None:
            offpal.append((a, value))
        if g:
            glow.add(a)
    H = pm.h * pm.frames
    im = Image.new('RGBA', (pm.w, H), (0, 0, 0, 0))
    gm = Image.new('RGBA', (pm.w, H), (0, 0, 0, 0)) if glow else None
    pix = im.load()
    gpix = gm.load() if gm else None
    for y, row in enumerate(pm.rows):
        for x, a in enumerate(row):
            c = colours[a]
            pix[x, y] = c
            if gpix is not None and a in glow and c[3]:
                gpix[x, y] = c
    mc = None
    if pm.frames > 1 or pm.frametime:
        anim = {}
        if pm.frametime:
            anim['frametime'] = pm.frametime
        if pm.interpolate:
            anim['interpolate'] = True
        mc = (json.dumps({'animation': anim}, indent=2, sort_keys=True) + '\n').encode('utf-8')
    return Compiled(pm, im, gm, mc, offpal)


def png_bytes(im):
    buf = io.BytesIO()
    im.save(buf, format='PNG', optimize=False, compress_level=9)
    return buf.getvalue()


def find_maps(paths):
    out = []
    for r, ds, fs in os.walk(paths.art):
        ds[:] = sorted(d for d in ds if not d.startswith('_') and not d.startswith('.'))
        for f in sorted(fs):
            if f.endswith(MAP_EXT) and not f.startswith('_'):
                out.append(os.path.join(r, f))
    return out


def asset_files(paths):
    out = []
    for r, ds, fs in os.walk(paths.assets):
        ds.sort()
        for f in sorted(fs):
            if f.endswith('.png') or f.endswith('.png.mcmeta'):
                out.append(os.path.relpath(os.path.join(r, f), paths.assets).replace('\\', '/'))
    return out


# ---------------------------------------------------------------------------------------- manifest

def load_manifest(paths):
    if not os.path.exists(paths.manifest):
        raise TexError('no manifest at %s' % paths.manifest)
    with open(paths.manifest, encoding='utf-8') as fh:
        data = json.load(fh)
    return data['textures']


def write_manifest(paths, entries):
    """One entry per line, sorted, so concurrent edits merge line by line. Written atomically."""
    lines = []
    for k in sorted(entries):
        e = entries[k]
        fields = [('source', e['source'])]
        if e.get('map'):
            fields.append(('map', e['map']))
        fields.append(('sha256', e.get('sha256')))
        body = ', '.join('%s: %s' % (json.dumps(a), json.dumps(b)) for a, b in fields)
        lines.append('    %s: {%s}' % (json.dumps(k), body))
    text = ('{\n  "about": "Who owns each texture. legacy = written by tools/draw_textures.py and draw_p2-p6.py; '
            'map = compiled from the pixel map named; painted = the PNG itself is the source. sha256 = what the '
            'tools last wrote or adopted. Edit only through tools/tex/tex.py.",\n'
            '  "format": 1,\n  "textures": {\n' + ',\n'.join(lines) + '\n  }\n}\n')
    tmp = paths.manifest + '.%d.tmp' % os.getpid()
    with open(tmp, 'w', encoding='utf-8', newline='\n') as fh:
        fh.write(text)
    os.replace(tmp, paths.manifest)


class ManifestLock:
    """Exclusive lock around every read-modify-write of the manifest (and the PNG writes it guards)."""

    def __init__(self, paths, wait=120, stale=300):
        self.paths = paths
        self.wait = wait
        self.stale = stale
        self.fd = None

    def __enter__(self):
        t0 = time.time()
        told = False
        while True:
            try:
                self.fd = os.open(self.paths.lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
                os.write(self.fd, ('pid %d at %s\n' % (os.getpid(), time.strftime('%Y-%m-%d %H:%M:%S'))).encode())
                return self
            except FileExistsError:
                try:
                    age = time.time() - os.path.getmtime(self.paths.lock)
                except OSError:
                    continue
                if age > self.stale:
                    print('tex: removing a stale manifest lock (%.0f s old)' % age)
                    try:
                        os.remove(self.paths.lock)
                    except OSError:
                        pass
                    continue
                if time.time() - t0 > self.wait:
                    raise TexError('manifest is locked by another tex.py run (%s); waited %d s'
                                   % (self.paths.lock, self.wait))
                if not told:
                    print('tex: waiting for another tex.py run to release the manifest lock')
                    told = True
                time.sleep(0.2)

    def __exit__(self, *a):
        os.close(self.fd)
        try:
            os.remove(self.paths.lock)
        except OSError:
            pass

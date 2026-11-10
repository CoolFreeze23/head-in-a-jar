"""Head in a Jar texture tool. Run from the project root: python tools/tex/tex.py <command> ...

Commands (details in art/README.md):
  palette [--set B]                      print every palette key, alias and hex
  build [MAP_OR_PNG ...]                 compile pixel maps into the mod's textures (all maps if none named)
  check                                  no writes: every map PNG matches its map, every PNG is in the manifest
  adopt PNG ...                          mark PNGs as painted (the PNG becomes the source)
  import PNG [--out MAP] [--force]       write a pixel map from an existing PNG by nearest palette colour
  render MAP --out PNG [--scale N]       compile one map anywhere, for a look (no manifest, no assets)
  swap --set B --out DIR [MAP_OR_PNG ...]  recolour maps (and painted PNGs) to another set in a separate folder
  set-default B                          make B the house set and rebuild every map
  measure PNG_OR_MAP [--kind block]      the style-rule measurements with pass or fail
  seed-legacy                            create the manifest, listing every existing PNG as legacy (once)
Global option: --root DIR (a project root other than this checkout, for tests).
"""
import argparse
import json
import math
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image  # noqa: E402

import measure as M  # noqa: E402
from texlib import (Paths, TexError, ManifestLock, compile_map, parse_map, load_palette, load_manifest,  # noqa: E402
                    write_manifest, find_maps, asset_files, file_sha, sha256, png_bytes, rgb_hex, MAP_EXT)


def say(*a):
    print(*a)
    sys.stdout.flush()


# ---------------------------------------------------------------------------------------- helpers

def resolve_maps(paths, targets):
    """Targets may be map files, asset paths or asset-relative names. Returns map file paths."""
    if not targets:
        return find_maps(paths)
    out = []
    for t in targets:
        if t.endswith(MAP_EXT) and os.path.exists(t):
            out.append(os.path.abspath(t))
            continue
        rel = paths.asset_rel(t)
        if rel.endswith('_glowmask.png'):
            rel = rel[:-len('_glowmask.png')] + '.png'
        mp = paths.map_for(rel)
        if not os.path.exists(mp):
            raise TexError('no pixel map for %s (looked for %s)' % (rel, mp))
        out.append(mp)
    return out


def check_mirror(paths, mp, pm):
    rel_map = paths.map_rel(mp)
    want = rel_map[:-len(MAP_EXT)] + '.png'
    if pm.texture != want:
        raise TexError('%s says texture: %s but its place under art/textures/ means %s; move the file or fix '
                       'the line' % (rel_map, pm.texture, want))


# ---------------------------------------------------------------------------------------- build

def cmd_build(paths, args):
    pal = load_palette(paths)
    maps = resolve_maps(paths, args.targets)
    if not maps:
        say('no pixel maps under %s' % paths.art)
        return 0
    compiled = []
    bad = 0
    for mp in maps:
        try:
            pm = parse_map(mp)
            check_mirror(paths, mp, pm)
            compiled.append((mp, compile_map(pm, pal)))
            for note in pm.notes:
                say('note %s: %s' % (paths.map_rel(mp), note))
        except TexError as e:
            say('ERROR %s' % e)
            bad += 1
    wrote = same = refused = 0
    with ManifestLock(paths):
        man = load_manifest(paths)
        for mp, c in compiled:
            map_rel = paths.map_rel(mp)
            outs = c.outputs()
            problems = []
            plan = []
            if man.get(c.map.texture, {}).get('source') == 'painted':
                say('skip %s: %s is painted, so this map is only a reference now (rename it to start with _)'
                    % (map_rel, c.map.texture))
                continue
            for rel, data in outs:
                e = man.get(rel)
                disk = paths.asset(rel)
                have = file_sha(disk) if os.path.exists(disk) else None
                if e is None:
                    if have is not None:
                        problems.append('%s exists but is not in the manifest; run "tex.py adopt %s" if it is '
                                        'painted work, or delete it' % (rel, rel))
                        continue
                elif e['source'] == 'painted':
                    problems.append('%s is painted (the PNG is the source); the map will not overwrite it' % rel)
                    continue
                elif e['source'] == 'legacy':
                    if have is not None and have != e['sha256']:
                        problems.append('%s is legacy but was changed by hand since the manifest recorded it; '
                                        'adopt it ("tex.py adopt %s") or restore it' % (rel, rel))
                        continue
                    say('claim %s from legacy (the old draw scripts will now skip it)' % rel)
                elif e['source'] == 'map':
                    if e.get('map') != map_rel:
                        problems.append('%s belongs to another map (%s)' % (rel, e.get('map')))
                        continue
                    if have is not None and have != e['sha256']:
                        problems.append('%s was changed since tex.py last wrote it (someone painted over it). '
                                        'Keep the painting with "tex.py adopt %s", or delete the PNG to rebuild '
                                        'from the map' % (rel, rel))
                        continue
                plan.append((rel, data, have))
            if problems:
                for p in problems:
                    say('REFUSED %s: %s' % (map_rel, p))
                refused += 1
                continue
            for rel, data, have in plan:
                new = sha256(data)
                if have != new:
                    disk = paths.asset(rel)
                    os.makedirs(os.path.dirname(disk), exist_ok=True)
                    with open(disk, 'wb') as fh:
                        fh.write(data)
                    say('wrote %s' % rel)
                    wrote += 1
                else:
                    same += 1
                man[rel] = {'source': 'map', 'map': map_rel, 'sha256': new}
            # outputs this map used to make and no longer does (a glowmask or mcmeta dropped from the map)
            made = {rel for rel, _ in outs}
            for rel in sorted(k for k, e in man.items() if e.get('source') == 'map' and e.get('map') == map_rel):
                if rel in made:
                    continue
                disk = paths.asset(rel)
                if os.path.exists(disk) and file_sha(disk) != man[rel]['sha256']:
                    say('REFUSED %s: stale output %s was changed by hand; adopt or delete it' % (map_rel, rel))
                    continue
                if os.path.exists(disk):
                    os.remove(disk)
                del man[rel]
                say('removed %s (the map no longer makes it)' % rel)
            for a, v in c.offpalette:
                say('warning %s: alias %r is off the palette (%s); R1 wants it listed as an exception'
                    % (map_rel, a, v))
        write_manifest(paths, man)
    say('build: %d written, %d unchanged, %d maps refused, %d maps with errors' % (wrote, same, refused, bad))
    return 1 if (refused or bad) else 0


# ---------------------------------------------------------------------------------------- check

def cmd_check(paths, args):
    pal = load_palette(paths)
    man = load_manifest(paths)
    errors = []
    warns = []
    on_disk = set(asset_files(paths))
    for rel in sorted(on_disk - set(man)):
        if rel.endswith('.png'):
            errors.append('%s is not in the manifest (adopt it, or build its map)' % rel)
        else:
            errors.append('%s is not in the manifest' % rel)
    for rel in sorted(set(man) - on_disk):
        errors.append('%s is in the manifest (%s) but missing on disk' % (rel, man[rel]['source']))
    by_map = {}
    drifted = set()
    for rel, e in sorted(man.items()):
        disk = paths.asset(rel)
        if not os.path.exists(disk):
            continue
        have = file_sha(disk)
        if e['source'] == 'legacy' and have != e['sha256']:
            errors.append('%s is legacy but changed since recorded: the old draw scripts now skip it; adopt it '
                          'as painted or restore it' % rel)
        elif e['source'] == 'painted':
            mp = paths.map_for(rel)
            if os.path.exists(mp):
                warns.append('%s is painted but a map still exists (%s); delete or rename the map'
                             % (rel, paths.map_rel(mp)))
        elif e['source'] == 'map':
            by_map.setdefault(e.get('map'), []).append(rel)
            if have != e['sha256']:
                drifted.add(rel)
                errors.append('%s differs from what tex.py last wrote (painted over?); adopt it or delete it and '
                              'rebuild' % rel)
    maps = {paths.map_rel(m): m for m in find_maps(paths)}
    for map_rel, rels in sorted(by_map.items()):
        mp = maps.get(map_rel)
        if mp is None:
            errors.append('%s are compiled from %s, which does not exist' % (', '.join(rels), map_rel))
            continue
        try:
            pm = parse_map(mp)
            check_mirror(paths, mp, pm)
            c = compile_map(pm, pal)
        except TexError as e:
            errors.append(str(e))
            continue
        outs = dict(c.outputs())
        for rel in rels:
            if rel not in outs:
                errors.append('%s is listed as made by %s, which no longer makes it; run build' % (rel, map_rel))
                continue
            disk = paths.asset(rel)
            if not os.path.exists(disk) or rel in drifted:
                continue
            with open(disk, 'rb') as fh:
                data = fh.read()
            if rel.endswith('.png'):
                want = c.glow if rel.endswith('_glowmask.png') and rel != pm.texture else c.image
                got = Image.open(disk).convert('RGBA')
                same = got.size == want.size and got.tobytes() == want.tobytes()
                if not same:
                    errors.append('%s does not match its map %s (map edited without a build?)' % (rel, map_rel))
            elif json.loads(data) != json.loads(outs[rel]):
                errors.append('%s does not match its map %s' % (rel, map_rel))
        for rel in outs:
            if rel not in rels:
                errors.append('%s would be made by %s but has not been built' % (rel, map_rel))
    for map_rel in sorted(set(maps) - set(by_map)):
        png = map_rel[:-len(MAP_EXT)] + '.png'
        if man.get(png, {}).get('source') == 'painted':
            continue  # already warned above
        errors.append('map %s has never been built (run build)' % map_rel)
    counts = {}
    for e in man.values():
        counts[e['source']] = counts.get(e['source'], 0) + 1
    for w in warns:
        say('warning: ' + w)
    for e in errors:
        say('FAIL: ' + e)
    say('check: %d files listed (%s); %d PNGs on disk; %d problems, %d warnings'
        % (len(man), ', '.join('%d %s' % (v, k) for k, v in sorted(counts.items())),
           sum(1 for r in on_disk if r.endswith('.png')), len(errors), len(warns)))
    return 1 if errors else 0


# ---------------------------------------------------------------------------------------- adopt, seed

def cmd_adopt(paths, args):
    with ManifestLock(paths):
        man = load_manifest(paths)
        for t in args.targets:
            rel = paths.asset_rel(t)
            disk = paths.asset(rel)
            if not os.path.exists(disk):
                raise TexError('%s does not exist' % disk)
            old = man.get(rel, {}).get('source', 'unlisted')
            man[rel] = {'source': 'painted', 'sha256': file_sha(disk)}
            say('adopted %s as painted (was %s)' % (rel, old))
            mp = paths.map_for(rel)
            if rel.endswith('.png') and os.path.exists(mp):
                say('  its map %s is no longer the source: delete it, or rename it to start with _ to keep it '
                    'as a reference' % paths.map_rel(mp))
        write_manifest(paths, man)
    return 0


def cmd_seed(paths, args):
    if os.path.exists(paths.manifest):
        raise TexError('the manifest already exists; seed-legacy only creates it')
    os.makedirs(paths.art, exist_ok=True)
    with ManifestLock(paths):
        man = {rel: {'source': 'legacy', 'sha256': file_sha(paths.asset(rel))} for rel in asset_files(paths)}
        write_manifest(paths, man)
    say('manifest created with %d legacy files' % len(man))
    return 0


# ---------------------------------------------------------------------------------------- palette, render

def cmd_palette(paths, args):
    pal = load_palette(paths, args.set)
    say('set %s: %s' % (pal.set, pal.data['sets'][pal.set]))
    for name in pal.order:
        say('%-16s %s' % (name, pal.labels[name]))
        ramp = pal.ramp(name)
        letter = (pal.data['house'].get(name) or pal.data['shared'].get(name))['alias']
        say('    ' + '  '.join('%s%d=%s.%d #%s' % (letter, i, name, i, rgb_hex(c)) for i, c in enumerate(ramp)))
    extra = {a: v for a, v in pal.aliases.items() if not re.fullmatch(r'[A-Za-z]\d', a)}
    say('extra aliases: ' + '  '.join('%s=%s' % kv for kv in extra.items()))
    return 0


def upscale(im, n):
    return im.resize((im.width * n, im.height * n), Image.NEAREST) if n > 1 else im


def cmd_render(paths, args):
    pal = load_palette(paths, args.set)
    pm = parse_map(args.map)
    c = compile_map(pm, pal)
    out = args.out
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    upscale(c.image, args.scale).save(out)
    say('rendered %s (set %s) -> %s' % (pm.texture, pal.set, out))
    if c.glow is not None:
        g = out[:-4] + '_glowmask.png'
        upscale(c.glow, args.scale).save(g)
        say('rendered glowmask -> %s' % g)
    return 0


# ---------------------------------------------------------------------------------------- import

def _lab(c):
    def lin(v):
        v /= 255.0
        return v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = (lin(v) for v in c[:3])
    x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
    y = 0.2126 * r + 0.7152 * g + 0.0722 * b
    z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883

    def f(t):
        return t ** (1 / 3) if t > 0.008856 else 7.787 * t + 16 / 116
    fx, fy, fz = f(x), f(y), f(z)
    return (116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))


def delta_e(a, b):
    return math.dist(_lab(a), _lab(b))


def cmd_import(paths, args):
    pal = load_palette(paths, args.set)
    rel = paths.asset_rel(args.png)
    src = args.png if os.path.exists(args.png) else paths.asset(rel)
    im = Image.open(src).convert('RGBA')
    w, h = im.size
    frames = 1
    mcmeta = src + '.mcmeta'
    anim = None
    if os.path.exists(mcmeta):
        with open(mcmeta, encoding='utf-8') as fh:
            anim = json.load(fh).get('animation', {})
        fw = anim.get('width', w)
        fh_ = anim.get('height', fw)
        if h % fh_ == 0 and h > fh_:
            frames = h // fh_
            h = fh_
    gpath = src[:-4] + '_glowmask.png'
    glow = Image.open(gpath).convert('RGBA') if os.path.exists(gpath) else None
    # alias for each palette key (first alias that names it exactly)
    key_alias = {}
    for a, k in pal.aliases.items():
        if k in pal.keys and k not in key_alias and re.fullmatch(r'[A-Za-z]\d', a):
            key_alias[k] = a
    entries = [(k, c) for k, c in pal.keys.items()]
    cache = {}

    def nearest(c):
        if c not in cache:
            k, pc = min(entries, key=lambda kc: delta_e(c, kc[1]))
            cache[c] = (k, delta_e(c, pc))
        return cache[c]

    local = {}       # (key, alpha, glow) -> alias
    key_lines = {}   # alias -> (value, glow)
    gen = iter('%s%s' % (p, q) for p in '~^*+' for q in '0123456789abcdefghijklmnopqrstuvwxyz')
    px = im.load()
    gpx = glow.load() if glow else None
    rows = []
    dist = []
    misses = {}
    for y in range(h * frames):
        row = []
        for x in range(w):
            c = px[x, y]
            if c[3] == 0:
                row.append('..')
                key_lines['..'] = ('clear', False)
                continue
            k, d = nearest(c[:3])
            dist.append(d)
            if d > 0.5:
                misses.setdefault(c[:3], [k, d, 0])[2] += 1
            is_glow = bool(gpx and gpx[x, y][3])
            if c[3] == 255 and not is_glow:
                a = key_alias.get(k)
                if a is None:
                    a = local.setdefault((k, 255, False), next(gen))
                key_lines[a] = (k, False)
            else:
                a = local.get((k, c[3], is_glow))
                if a is None:
                    a = local[(k, c[3], is_glow)] = next(gen)
                key_lines[a] = (k + ('@%d' % c[3] if c[3] != 255 else ''), is_glow)
            row.append(a)
        rows.append(row)
    out = args.out or paths.map_for(rel)
    if os.path.exists(out) and not args.force:
        raise TexError('%s exists; pass --force to replace it' % out)
    lines = ['# Imported from %s by nearest %s-set palette colour. Edit freely; build with:' % (rel, pal.set),
             '#   python tools/tex/tex.py build %s' % rel,
             'texture: %s' % rel, 'size: %dx%d' % (w, h)]
    if frames > 1:
        lines.append('frames: %d' % frames)
    if anim and anim.get('frametime'):
        lines.append('frametime: %d' % anim['frametime'])
    if anim and anim.get('interpolate'):
        lines.append('interpolate: true')
    lines.append('key:')
    for a in sorted(key_lines, key=lambda s: (s != '..', s)):
        v, g = key_lines[a]
        lines.append('  %s  %s%s' % (a, v, '  glow' if g else ''))
    lines.append('pixels:')
    for i, row in enumerate(rows):
        if frames > 1 and i % h == 0:
            lines.append('# frame %d' % (i // h))
        lines.append(' '.join(row))
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    with open(out, 'w', encoding='utf-8', newline='\n') as fh:
        fh.write('\n'.join(lines) + '\n')
    n = len(dist)
    exact = sum(1 for d in dist if d <= 0.5)
    say('imported %s -> %s' % (rel, out))
    if n:
        say('  %d opaque pixels: %d exact palette colours (%.0f%%); distance to the palette in CIE76 delta E: '
            'mean %.1f, worst %.1f' % (n, exact, 100 * exact / n, sum(dist) / n, max(dist)))
        say('  (delta E under 2 is invisible, 2-10 visible side by side, over 10 a different colour)')
        for c, (k, d, cnt) in sorted(misses.items(), key=lambda t: -t[1][2] * t[1][1])[:12]:
            say('  #%s x%-4d -> %-16s #%s  delta E %.1f' % (rgb_hex(c), cnt, k, rgb_hex(pal.keys[k]), d))
        if len(misses) > 12:
            say('  ... and %d more off-palette colours' % (len(misses) - 12))
    return 0


# ---------------------------------------------------------------------------------------- swap

def cmd_swap(paths, args):
    pal_to = load_palette(paths, args.set)
    pal_from = load_palette(paths)
    out = os.path.abspath(args.out)
    if out.lower().startswith(os.path.abspath(paths.assets).lower()) or \
            os.path.abspath(paths.assets).lower().startswith(out.lower() + os.sep):
        raise TexError('the output folder must be outside the mod assets')
    man = load_manifest(paths)
    if args.targets:
        rels = [paths.asset_rel(t) for t in args.targets]
    else:
        rels = sorted(k for k, e in man.items() if e['source'] in ('map', 'painted') and k.endswith('.png'))
        if args.all:
            rels = sorted(k for k in man if k.endswith('.png'))
    # exact hex substitution for PNG sources: a colour of the current set -> the same key in the target set
    sub = {}
    for c, keys in pal_from.colour_keys().items():
        targets = {pal_to.keys[k] for k in keys}
        if len(targets) == 1:
            sub[c] = targets.pop()
    done = 0
    for rel in rels:
        if rel.endswith('_glowmask.png') and man.get(rel, {}).get('source') == 'map':
            continue
        e = man.get(rel, {'source': 'unlisted'})
        if e['source'] == 'map':
            pm = parse_map(os.path.join(paths.art, *e['map'].split('/')))
            c = compile_map(pm, pal_to)
            for orel, data in c.outputs():
                dst = os.path.join(out, *orel.split('/'))
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                with open(dst, 'wb') as fh:
                    fh.write(data)
                done += 1
                say('map     %s -> %s' % (orel, dst))
        else:
            src = paths.asset(rel)
            im = Image.open(src).convert('RGBA')
            pix = im.load()
            changed = 0
            for y in range(im.height):
                for x in range(im.width):
                    p = pix[x, y]
                    if p[3] and p[:3] in sub and sub[p[:3]] != p[:3]:
                        pix[x, y] = sub[p[:3]] + (p[3],)
                        changed += 1
            dst = os.path.join(out, *rel.split('/'))
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            with open(dst, 'wb') as fh:
                fh.write(png_bytes(im))
            done += 1
            say('%-7s %s -> %s (%d pixels swapped)' % (e['source'], rel, dst, changed))
    say('swap to set %s: %d files under %s; the mod assets were not touched' % (pal_to.set, done, out))
    return 0


def cmd_set_default(paths, args):
    pal = load_palette(paths)
    if args.set not in pal.data['sets']:
        raise TexError('no set %r' % args.set)
    with open(paths.palette, encoding='utf-8') as fh:
        text = fh.read()
    new, n = re.subn(r'("default"\s*:\s*")[^"]*(")', r'\g<1>%s\g<2>' % args.set, text, count=1)
    if n != 1:
        raise TexError('could not find "default" in %s' % paths.palette)
    with open(paths.palette, 'w', encoding='utf-8', newline='\n') as fh:
        fh.write(new)
    say('house set is now %s (%s); rebuilding every map' % (args.set, pal.data['sets'][args.set]))
    args.targets = []
    return cmd_build(paths, args)


# ---------------------------------------------------------------------------------------- measure

def lit_pixels(im):
    hist = im.convert('RGBA').getchannel('A').histogram()
    return sum(hist) - hist[0]


def cmd_measure(paths, args):
    pal = load_palette(paths, args.set)
    t = args.target
    glow_n = None
    if t.endswith(MAP_EXT):
        pm = parse_map(t)
        c = compile_map(pm, pal)
        rel = pm.texture
        im = c.image
        frame_h = pm.h
        if c.glow is not None:
            glow_n = lit_pixels(c.glow)
    else:
        rel = paths.asset_rel(t)
        src = t if os.path.exists(t) else paths.asset(rel)
        im = Image.open(src)
        frame_h = None
        if os.path.exists(src + '.mcmeta'):
            frame_h = im.width
        g = src[:-4] + '_glowmask.png'
        if os.path.exists(g):
            glow_n = lit_pixels(Image.open(g))
    m = M.metrics(im, frame_h)
    allowed = set(pal.keys.values())
    say('%s  %dx%d  kind %s  set %s  opaque %d px' % (rel, m['w'], m['h'], args.kind or M.kind_of(rel), pal.set,
                                                   m['opaque']))
    if not m.get('opaque'):
        say('  fully transparent')
        return 1
    say('  colours %d | largest colour share %.0f%% | neighbour-jump ratio %.2f | regions %d | lone-pixel share '
        '%.0f%%' % (m['colours'], 100 * m['top_share'], m['jumps'], m['regions'], 100 * m['lone_share']))
    fails = 0
    for rule, v, text in M.verdicts(m, rel, allowed, args.kind, glow_n):
        say('  %-4s %-4s %s' % (rule, v, text))
        fails += v == 'FAIL'
    say('  %s' % ('PASS' if not fails else '%d rule(s) failed' % fails))
    return 1 if fails else 0


# ---------------------------------------------------------------------------------------- main

def main(argv=None):
    ap = argparse.ArgumentParser(prog='tex.py', description=__doc__.split('\n')[0])
    ap.add_argument('--root', help='project root (default: this checkout)')
    sp = ap.add_subparsers(dest='cmd', required=True)
    p = sp.add_parser('palette'); p.add_argument('--set')
    p = sp.add_parser('build'); p.add_argument('targets', nargs='*')
    sp.add_parser('check')
    p = sp.add_parser('adopt'); p.add_argument('targets', nargs='+')
    sp.add_parser('seed-legacy')
    p = sp.add_parser('import'); p.add_argument('png'); p.add_argument('--out'); p.add_argument('--force',
                                                                                              action='store_true')
    p.add_argument('--set')
    p = sp.add_parser('render'); p.add_argument('map'); p.add_argument('--out', required=True)
    p.add_argument('--scale', type=int, default=1); p.add_argument('--set')
    p = sp.add_parser('swap'); p.add_argument('targets', nargs='*'); p.add_argument('--set', required=True)
    p.add_argument('--out', required=True); p.add_argument('--all', action='store_true',
                                                          help='also legacy PNGs (exact-hex swap)')
    p = sp.add_parser('set-default'); p.add_argument('set')
    p = sp.add_parser('measure'); p.add_argument('target'); p.add_argument('--kind',
                                                                          choices=['block', 'item', 'entity', 'gui'])
    p.add_argument('--set')
    args = ap.parse_args(argv)
    paths = Paths(args.root)
    fn = {'palette': cmd_palette, 'build': cmd_build, 'check': cmd_check, 'adopt': cmd_adopt, 'seed-legacy': cmd_seed,
          'import': cmd_import, 'render': cmd_render, 'swap': cmd_swap, 'set-default': cmd_set_default,
          'measure': cmd_measure}[args.cmd]
    try:
        return fn(paths, args)
    except TexError as e:
        say('ERROR %s' % e)
        return 2


if __name__ == '__main__':
    sys.exit(main())

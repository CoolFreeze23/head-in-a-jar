"""Audit every sound event the mod registers and every sound its code, clips and data play.

    python tools/snd/audit.py [--md OUT.md] [--json OUT.json] [--loud]

Reads the Java sources (registrations and play sites), assets/headinajar/sounds.json, both lang files, every
animation clip's sound keys (block and entity clips, instruction keyframes naming a sound), tools/snd/events.json (the
recipes' loudness classes and categories), and with --loud decodes every file an event lists and measures its maximum
momentary loudness (nsynth.m_max, the same meter measure.py uses). Prints the counts:

- silent ids: played (by code, clip or data) but with no registered event, or registered with no sounds.json entry or
  no file behind it;
- orphans: registered, never played anywhere;
- no subtitle: an event with files but no subtitle key, or a key missing from either lang file;
- borrowed: played by a thing that is not the event's own family (its name or subtitle names another machine);
- off class: an event's loudest take more than 2 LU from its class target.

Every row comes from reading files and measuring them.
"""
import argparse
import glob
import json
import os
import re
import sys
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
JAVA = os.path.join(ROOT, 'src', 'main', 'java')
RES = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'headinajar')
NS = 'headinajar'

DECL = re.compile(r'(?:public|private|protected)?\s*static\s+final\s+DeferredHolder<SoundEvent,\s*SoundEvent>\s+(\w+)\s*=\s*'
                  r'(?:(?:ModSounds\.)?SOUNDS\.register\("([^"]+)"|(\w+)\("([^"]+)")')
HELPER = re.compile(r'static\s+DeferredHolder<SoundEvent,\s*SoundEvent>\s+(\w+)\(String\s+(\w+)[^)]*\)\s*\{(.*?)\n    \}', re.S)
VANILLA = re.compile(r'SoundEvents\.([A-Z_0-9]+)')
STRING = re.compile(r'"(?:headinajar:)?([a-z0-9_]+(?:\.[a-z0-9_]+)+)"')
SOURCE = re.compile(r'SoundSource\.([A-Z]+)')


def java_files():
    for p in glob.glob(os.path.join(JAVA, '**', '*.java'), recursive=True):
        yield p, open(p, encoding='utf-8').read()


def rel(p):
    return os.path.relpath(p, os.path.join(JAVA, 'com', 'coolfreeze23', 'headinajar')).replace('\\', '/')


def method_at(lines, i):
    sig = re.compile(r'^\s+(?:public|private|protected|static|final|synchronized|\s)*[\w<>\[\], ?.]+\s+(\w+)\([^;]*$')
    for j in range(i, -1, -1):
        m = sig.match(lines[j])
        if m and m.group(1) not in ('if', 'for', 'while', 'switch', 'return', 'new', 'catch'):
            return m.group(1)
    return '?'


def registrations():
    """id -> dict(cls, field, how); class -> {field: id}."""
    reg, fields = {}, defaultdict(dict)
    for p, src in java_files():
        cls = os.path.basename(p)[:-5]
        helpers = {}
        for m in HELPER.finditer(src):
            body = m.group(3)
            pm = re.search(r'register\("([^"]*)"\s*\+\s*' + m.group(2), body)
            if pm:
                helpers[m.group(1)] = pm.group(1)
            elif re.search(r'register\(' + m.group(2), body):
                helpers[m.group(1)] = ''
        for m in DECL.finditer(src):
            field = m.group(1)
            if m.group(2):
                sid = m.group(2)
            elif m.group(3) in helpers:
                sid = helpers[m.group(3)] + m.group(4)
            else:
                continue
            reg[sid] = dict(cls=cls, field=field, file=rel(p))
            fields[cls][field] = sid
        # registrations from a data table (DOLORES's lines)
        if 'dolores_voice.json' in src and 'register("dolores."' in src:
            table = json.load(open(os.path.join(RES, 'dolores_voice.json'), encoding='utf-8'))
            for line, d in table['lines'].items():
                for lang in ('en', 'pt'):
                    reg[f'dolores.{lang}.{line}'] = dict(cls=cls, field='(table)', file=rel(p))
                    if d.get('muffled'):
                        reg[f'dolores.{lang}.{line}.muffled'] = dict(cls=cls, field='(table)', file=rel(p))
        # the mecha's tables: {"name", low, high} rows (or bare names) registered as "mecha." + prefix + name
        if cls == 'HopperSounds':
            for tm in re.finditer(r'(?:Object\[\]\[\]|String\[\])\s+(\w+)\s*=\s*\{(.*?)\};', src, re.S):
                var, body = tm.group(1), tm.group(2)
                loop = re.search(r'for \(Object\[\] row : ' + var + r'\)\s*\{\s*String name = ("(\w+)\." \+ )?', src)
                prefix = {'chimera': 'chimera.', 'lunk': 'lunk.'}.get(var, '')
                if loop and loop.group(2):
                    prefix = loop.group(2) + '.'
                for nm in re.findall(r'"([a-z_.]+)"', body):
                    reg['mecha.' + prefix + nm] = dict(cls=cls, field='(table ' + var + ')', file=rel(p))
            for am in re.finditer(r'\{"([a-z_.]+)",\s*"([a-z_.]+)"\}', src[src.find('ALIASES'):src.find('static {')]):
                reg['mecha.' + am.group(1)] = dict(cls=cls, field='(alias of mecha.%s)' % am.group(2), file=rel(p))
            for fm in re.finditer(r'register\("(mecha\.[a-z_.]+)"', src):
                reg[fm.group(1)] = dict(cls=cls, field='(fixed range)', file=rel(p))
            for k in [k for k in reg if k.startswith('mecha.') and k.count('.') == 1 and k[6:] in ('chimera', 'lunk')]:
                del reg[k]
        # aliases: a clip's sound name played as another registered event (PLAY.put("a", PLAY.get("b")))
        for m in re.finditer(r'(?:PLAY|ALIASES)\.put\("([^"]+)",\s*(?:PLAY\.get\("([^"]+)"\)|new Play\((\w+)\.(\w+))', src):
            reg.setdefault('alias:' + m.group(1), dict(cls=cls, field='alias', file=rel(p),
                                                        to=m.group(2) or fields.get(m.group(3), {}).get(m.group(4), m.group(4))))
    return reg, fields


def plays(reg, fields):
    """id -> [(where, source)], vanilla id -> [...]; strings the code plays that match no registration."""
    out, vanilla, loose = defaultdict(list), defaultdict(list), []
    known = {k for k in reg if not k.startswith('alias:')}
    for p, src in java_files():
        cls = os.path.basename(p)[:-5]
        lines = src.split('\n')
        own = fields.get(cls, {})
        for i, line in enumerate(lines):
            s = line.strip()
            if s.startswith('*') or s.startswith('//') or s.startswith('/*'):
                continue
            if DECL.search(line) or 'PLAY.put' in line:
                continue
            where = f'{rel(p)}:{i + 1} {method_at(lines, i)}'
            srcm = SOURCE.search(line) or SOURCE.search(' '.join(lines[i:i + 3]))
            source = srcm.group(1) if srcm else ''
            for c, fs in fields.items():
                for f, sid in fs.items():
                    if re.search(r'\b' + c + r'\.' + f + r'\b', line) or (c == cls and re.search(r'(?<![\w.])' + f + r'\b(?!\s*=)', line)):
                        out[sid].append((where, source))
            for m in VANILLA.finditer(line):
                vanilla['minecraft:' + m.group(1).lower()].append((where, source))
            if 'HopperSounds' in src or 'RoverClient' in cls:
                # a mecha's code names its sounds short ("motor", "lunk.knuckle"); the frame picks its own take at runtime
                for m in re.finditer(r'"([a-z_]+(?:\.[a-z_]+)?)"', line):
                    for k in [k for k in known if k.startswith('mecha.') and (k == 'mecha.' + m.group(1) or k.endswith('.' + m.group(1)) and k.count('.') == 2)]:
                        out[k].append((where + f' "{m.group(1)}"', source))
            if re.search(r'play|Sound|byId|sound|Keyframes', line):
                for m in STRING.finditer(line):
                    sid = m.group(1)
                    if sid in known or 'alias:' + sid in reg:
                        out[sid if sid in known else reg['alias:' + sid]['to']].append((where + f' "{sid}"', source))
                    elif re.search(r'(byId|Keyframes\.play|MachineSounds\.\w+|ChairSounds\.\w+)\(\s*"', line) and sid.split('.')[0] in {k.split('.')[0] for k in known}:
                        loose.append((sid, where))
        for m in re.finditer(r'HeadInAJar\.id\("([^"]+)"\)', src):
            ln = src[:m.start()].count('\n')
            ctx = '\n'.join(lines[max(0, ln - 1):ln + 2])
            if 'Sound' in ctx or 'BEEP' in ctx or 'sound' in ctx:
                if m.group(1) in known and not DECL.search(lines[ln]) and 'register' not in lines[ln] and 'createFixedRangeEvent' not in lines[ln]:
                    out[m.group(1)].append((f'{rel(p)}:{ln + 1} {method_at(lines, ln)} (by id)', ''))
    return out, vanilla, loose


def clips():
    """[(clip file, animation, kind, sound id)] for every sound key in a clip (sound_effects, instruction sounds)."""
    res = []
    for p in glob.glob(os.path.join(RES, 'animations', '**', '*.json'), recursive=True):
        d = json.load(open(p, encoding='utf-8'))
        name = os.path.relpath(p, os.path.join(RES, 'animations')).replace('\\', '/')
        for anim, a in d.get('animations', {}).items():
            for t, k in (a.get('sound_effects') or {}).items():
                eff = k.get('effect', '')
                res.append((name, anim, 'sound', eff.split('|')[0]))
            if not name.startswith('block/'):
                continue  # a mecha's or shell's instructions are its code's moments (footfalls, taps), not sound names
            for t, k in (a.get('timeline') or {}).items():
                txt = k if isinstance(k, str) else ' '.join(k) if isinstance(k, list) else ''
                for m in re.finditer(r'([a-z_]+):([a-z_]+);', txt):
                    res.append((name, anim, 'instruction', f'{NS}:{m.group(1)}.{m.group(2)}'))
    return res


def rig_borrows():
    """frame id -> {clip name: event name} from MechaRig's borrow() calls and the shells' BORROWED maps."""
    out = defaultdict(dict)
    p = os.path.join(JAVA, 'com', 'coolfreeze23', 'headinajar', 'mecha', 'MechaRig.java')
    if os.path.exists(p):
        src = open(p, encoding='utf-8').read()
        for m in re.finditer(r'new MechaRig\("(\w+)"(.*?);\n', src, re.S):
            for b in re.finditer(r'\.borrow\((.*?)\)', m.group(2), re.S):
                names = re.findall(r'"([a-z_.]+)"', b.group(1))
                out[m.group(1)].update(dict(zip(names[::2], names[1::2])))
        for m in re.finditer(r'\b([A-Z_]+)\.borrow\((.*?)\);', src, re.S):
            names = re.findall(r'"([a-z_.]+)"', m.group(2))
            out[m.group(1).lower()].update(dict(zip(names[::2], names[1::2])))
    p = os.path.join(JAVA, 'com', 'coolfreeze23', 'headinajar', 'mecha', 'ShellSounds.java')
    if os.path.exists(p):
        src = open(p, encoding='utf-8').read()
        pairs = re.findall(r'Map\.entry\("([a-z_.]+)",\s*"([a-z_.]+)"\)', src)
        out['rover'].update(dict(pairs))
        out['spider_walker'].update(dict(pairs))
    return out


def resolve_mecha(clip, name, reg, borrows):
    """The event a mecha or shell clip's sound name plays, as HopperSounds.get(frame, id) / RoverClient resolve it."""
    frame = os.path.basename(clip).split('.')[0]
    bare = name[6:] if name.startswith('mecha.') else name
    sets = {'colossus': ['colossus', 'bulwark'], 'executive': ['executive', 'bulwark'], 'lunk': ['lunk', 'bulwark'],
            'spider_walker': [], 'rover': []}.get(frame, [frame])
    if '.' not in bare:
        for st in sets:
            if 'mecha.' + st + '.' + bare in reg:
                return 'mecha.' + st + '.' + bare
    if 'mecha.' + bare not in reg and bare in borrows.get(frame, {}):
        return 'mecha.' + borrows[frame][bare]
    return 'mecha.' + bare


def lang(code):
    return json.load(open(os.path.join(RES, 'lang', f'{code}.json'), encoding='utf-8'))


def loudness(files):
    sys.path.insert(0, HERE)
    import numpy as np  # noqa: F401
    import subprocess
    import nsynth
    vals = []
    for f in files:
        name = f['name'] if isinstance(f, dict) else f
        if isinstance(f, dict) and f.get('type') == 'event':
            continue
        ns, path = name.split(':', 1)
        if ns != NS:
            continue
        p = os.path.join(RES, 'sounds', *(path + '.ogg').split('/'))
        if not os.path.exists(p):
            continue
        raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', p, '-ac', '1', '-ar', '44100', '-f', 'f32le', '-'], capture_output=True, check=True).stdout
        x = np.frombuffer(raw, '<f4').astype(np.float64)
        loop = isinstance(f, dict) and f.get('stream')
        vals.append((round(nsynth.m_max(np.tile(x, 3) if loop else x), 1), round(20 * np.log10(np.abs(x).max() + 1e-12), 1)))
    return vals


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--md')
    ap.add_argument('--json')
    ap.add_argument('--loud', action='store_true')
    a = ap.parse_args(argv)
    reg, fields = registrations()
    played, vanilla, loose = plays(reg, fields)
    cl = clips()
    sj = json.load(open(os.path.join(RES, 'sounds.json'), encoding='utf-8'))
    en, pt = lang('en_us'), lang('pt_br')
    ev = {e['id'].split(':', 1)[1]: e for e in json.load(open(os.path.join(HERE, 'events.json'), encoding='utf-8'))['events']}
    clip_by = defaultdict(list)
    borrows = rig_borrows()
    for f, anim, kind, sid in cl:
        if sid.startswith(NS + ':'):
            name = sid.split(':', 1)[1]
            if f.startswith('entity/'):
                to = resolve_mecha(f, name, reg, borrows)
                clip_by[to].append(f'{f} {anim}' + (f' as "{name}"' if to != name else ''))
            else:
                clip_by[name].append(f'{f} {anim}' + (' (instruction)' if kind == 'instruction' else ''))
    ids = sorted(set(k for k in reg if not k.startswith('alias:')) | set(sj) | set(clip_by) | set(played))
    rows = []
    for sid in ids:
        if sid.startswith('dolores.'):
            continue
        r = dict(id=sid)
        r['registered'] = sid in reg
        alias = reg.get('alias:' + sid)
        r['alias_to'] = alias['to'] if alias else None
        r['in_sounds_json'] = sid in sj
        ent = sj.get(sid, {})
        r['files'] = [f['name'] if isinstance(f, dict) else f for f in ent.get('sounds', [])]
        missing = []
        for f in ent.get('sounds', []):
            n = f['name'] if isinstance(f, dict) else f
            if isinstance(f, dict) and f.get('type') == 'event':
                continue
            ns, path = n.split(':', 1)
            if ns == NS and not os.path.exists(os.path.join(RES, 'sounds', *(path + '.ogg').split('/'))):
                missing.append(n)
        r['missing_files'] = missing
        key = ent.get('subtitle')
        r['subtitle_key'] = key
        r['en'] = en.get(key) if key else None
        r['pt'] = pt.get(key) if key else None
        r['code'] = played.get(sid, [])
        r['clips'] = clip_by.get(sid, [])
        e = ev.get(sid)
        r['class'] = e.get('loudness_class') if e else None
        r['category'] = e.get('category') if e else None
        r['code_sources'] = sorted({s for _, s in r['code'] if s})
        r['loop'] = bool(e and e.get('loop'))
        if a.loud and ent:
            r['loud'] = loudness(ent.get('sounds', []))
        rows.append(r)
    # aliases played by clips count for their target
    for k, v in reg.items():
        if k.startswith('alias:'):
            name = k[6:]
            for r in rows:
                if r['id'] == v['to']:
                    r['clips'] += [c + f' as "{name}"' for c in clip_by.get(name, [])]
    # an event whose sounds.json entry plays another of the mod's events plays that one whenever it plays
    byid = {r['id']: r for r in rows}
    for r in rows:
        for f in sj.get(r['id'], {}).get('sounds', []):
            if isinstance(f, dict) and f.get('type') == 'event' and f['name'].startswith(NS + ':'):
                to = byid.get(f['name'].split(':', 1)[1])
                if to is not None and (r['code'] or r['clips']):
                    to['code'] = to['code'] + [(f"via {r['id']}", '')]
    alias_names = {k[6:] for k in reg if k.startswith('alias:')}
    silent = [r for r in rows if (r['code'] or r['clips']) and (not r['registered'] or not r['in_sounds_json'] or r['missing_files'] or not r['files'])
              and r['id'] not in alias_names]
    orphans = [r for r in rows if r['registered'] and not r['code'] and not r['clips']]
    nosub = [r for r in rows if r['in_sounds_json'] and r['files'] and (not r['subtitle_key'] or not r['en'] or not r['pt'])]
    summary = dict(registered=sum(r['registered'] for r in rows), sounds_json=sum(r['in_sounds_json'] for r in rows),
                   dolores=sum(1 for k in reg if k.startswith('dolores.')), silent=[r['id'] for r in silent],
                   orphans=[r['id'] for r in orphans], no_subtitle=[r['id'] for r in nosub],
                   vanilla=len(vanilla), loose=loose)
    if a.json:
        with open(a.json, 'w', encoding='utf-8') as fh:
            json.dump(dict(summary=summary, rows=rows, vanilla={k: v for k, v in vanilla.items()}), fh, indent=1, ensure_ascii=False)
    print(f"registered {summary['registered']} (+{summary['dolores']} DOLORES lines), sounds.json {summary['sounds_json']}, "
          f"vanilla events played {len(vanilla)}")
    print(f"silent ids {len(silent)}: {' '.join(summary['silent'])}")
    print(f"orphans {len(orphans)}: {' '.join(summary['orphans'])}")
    print(f"no subtitle {len(nosub)}: {' '.join(summary['no_subtitle'])}")
    if loose:
        print('string ids the code plays that match no registration:', loose)
    if a.md:
        with open(a.md, 'w', encoding='utf-8') as fh:
            fh.write('| id | reg | files | subtitle en / pt | category | class | M max (peak) | plays in code | plays in clips |\n|---|---|---|---|---|---|---|---|---|\n')
            for r in rows:
                loud = ', '.join(f'{m} ({p})' for m, p in r.get('loud', [])) if r.get('loud') else ''
                fh.write(f"| `{r['id']}` | {'y' if r['registered'] else 'NO'}{' alias->' + r['alias_to'] if r['alias_to'] else ''} | "
                         f"{len(r['files'])}{' MISSING ' + str(r['missing_files']) if r['missing_files'] else ''} | "
                         f"{r['en']} / {r['pt']} | {r['category'] or ','.join(r['code_sources'])} | {r['class'] or ''} | {loud} | "
                         f"{'; '.join(w for w, _ in r['code'][:6])}{' (+%d)' % (len(r['code']) - 6) if len(r['code']) > 6 else ''} | "
                         f"{'; '.join(sorted(set(r['clips']))[:4])}{' (+%d)' % (len(set(r['clips'])) - 4) if len(set(r['clips'])) > 4 else ''} |\n")
            fh.write('\n| vanilla event | plays in code |\n|---|---|\n')
            for k in sorted(vanilla):
                fh.write(f"| `{k}` | {'; '.join(w + (' ' + s if s else '') for w, s in vanilla[k])} |\n")


if __name__ == '__main__':
    main(sys.argv[1:])

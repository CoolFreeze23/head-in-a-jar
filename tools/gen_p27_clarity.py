"""What tells a player what they are, what they can do next and what just happened: HUD lines, screen text,
tooltips, the career advancements, DOLORES's memos and the Employee Handbook (second edition) in both its editions.
Called by gen_data.py, last, so its checks see every language line of the mod.

Run on its own, `python tools/gen_p27_clarity.py fit` checks that every handbook page fits its page (both editions)
without writing anything.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources')

# ---------------------------------------------------------------------------------------------------------------
# Text widths: an estimate of the default font's advance per character (glyph plus its one pixel of spacing). The
# game's own measure decides; this catches what is plainly too long before anyone has to look.
_NARROW = {
    ' ': 4, '!': 2, '"': 4, "'": 2, '(': 4, ')': 4, '*': 4, ',': 2, '.': 2, ':': 2, ';': 2, '<': 5, '>': 5, '@': 7,
    'I': 4, '[': 4, ']': 4, '`': 3, 'f': 5, 'i': 2, 'k': 5, 'l': 3, 't': 4, '{': 4, '|': 2, '}': 4, '~': 7,
    'í': 3, 'ì': 3, 'ï': 3, 'î': 4, '•': 4, ' ': 4,
}


def char_width(c, bold=False):
    return _NARROW.get(c, 6) + (1 if bold else 0)


def plain(text):
    """A lang value as it reads on screen: formatting codes out, arguments as typical fills."""
    text = re.sub('§.', '', text)
    text = re.sub(r'%(\d+\$)?s', 'Manipulator Arm', text)  # an item name or a key: a long one
    text = re.sub(r'%(\d+\$)?d', '100,000', text)
    return text.replace('%%', '%')


def width(text, bold=False):
    return sum(char_width(c, bold) for c in plain(text))


def wrap(text, max_px):
    """Lines a text wraps to at max_px (word wrap on spaces, \\n breaks), as the book and Patchouli wrap it."""
    lines = []
    for para in text.split('\n'):
        line, w = '', 0
        bold = False
        for word in re.split(r'(\s+)', para):
            if not word:
                continue
            ww = sum(char_width(c, bold) for c in re.sub('§.', '', word))
            if '§l' in word:
                bold = True
            if '§r' in word:
                bold = False
            if w + ww > max_px and line.strip():
                lines.append(line.rstrip())
                line, w = word.lstrip(), sum(char_width(c) for c in re.sub('§.', '', word.lstrip()))
            else:
                line += word
                w += ww
        lines.append(line.rstrip())
    return lines


BOOK_WIDTH, BOOK_LINES = 104, 14  # a written book's page (114 px; the estimate runs about 9% narrow, so 104)

# ---------------------------------------------------------------------------------------------------------------
# The handbook's markup (Patchouli's own) and the written edition made from the same text.
#
# Allowed in page text: $(br) $(br2) $(li) for a list item, $(l)bold$() , $(o)italic$() , $(item)name$() ,
# $(thing)name$() , $(k:key.headinajar.ability) for a key as the reader has bound it (never a letter),
# $(l:headinajar:category/entry)a link$(/l) to another entry.

KEY = re.compile(r'\$\(k:([a-z0-9_.]+)\)')


def to_written(text, order=None):
    """(lang value, [keybind names]) of a page's text for a vanilla written book: Patchouli's codes as section-sign
    formatting, keys as %N$s arguments filled with keybind components."""
    keys = []

    def key(m):
        if order is not None:
            keys.append(m.group(1))
            return f'%{order.index(m.group(1)) + 1}$s'
        keys.append(m.group(1))
        return f'%{len(keys)}$s'

    t = text.replace('%', '%%')
    t = KEY.sub(key, t)
    t = re.sub(r'\$\(l:[^)]*\)(.*?)\$\(/l\)', r'\1', t)
    t = t.replace('$(br2)', '\n\n').replace('$(br)', '\n').replace('$(li)', '\n• ')
    t = t.replace('$(l)', '').replace('$(o)', '').replace('$(n)', '')
    t = re.sub(r'\$\((item|thing)\)', '', t)
    t = re.sub(r'\$\(([0-9a-f])\)', r'§\1', t)
    t = re.sub(r'\$\(#[0-9a-fA-F]{6}\)', '', t)
    t = t.replace('$()', '')
    if '$(' in t:
        raise ValueError(f'handbook markup the written edition does not know: {text!r}')
    return t, keys


def written_lines(text, keys_long=True):
    t, _ = to_written(text)
    t = re.sub(r'%\d+\$s', 'Keypad /' if keys_long else 'R', t).replace('%%', '%')
    return wrap(t.lstrip('\n'), BOOK_WIDTH)


# How many lines of text each kind of page holds. The Patchouli page is a little roomier than a written book's in
# every case, so the written book (14 lines, the first one taken by the header where a page has one) is the limit,
# except under a spotlight or a recipe, where Patchouli's picture takes the room.
PAGE_LINES = {'text': 14, 'first': 13, 'titled': 13, 'spotlight': 10, 'crafting': 5, 'smelting': 7, 'slot': 9}

TITLE_PX = 100  # a Patchouli page title is one line
ENTRY_NAME_PX = 100  # so is an entry's name at the top of its first page
MEMO_HEAD = {'en': ('MEMO', 'FROM: DOLORES$(br)TO: All Staff$(br)RE: %s$(br2)'),
             'pt': ('MEMORANDO', 'DE: DOLORES$(br)PARA: Todos os Funcionários$(br)ASSUNTO: %s$(br2)')}
INCIDENT_HEAD = {'en': 'Incident Report', 'pt': 'Ocorrência'}


def page_parts(page, lang):
    """(kind, title or None, text) of one handbook page in one language ('en' or 'pt'), the way both editions show it."""
    i = 0 if lang == 'en' else 1
    kind = page[0]
    if kind == 'text':
        return 'text', None, page[1 + i]
    if kind in ('spotlight', 'crafting', 'smelting'):
        return kind, None, page[2 + i]
    if kind == 'memo':
        head, sub = MEMO_HEAD[lang]
        return 'titled', head, sub.replace('%s', page[1][i]) + page[2 + i]
    if kind == 'form':
        title = page[1][i]
        if width(title) > TITLE_PX and ': ' in title:
            head, subject = title.split(': ', 1)
            return 'titled', head, '$(l)' + subject + '$()$(br)' + page[2 + i]
        return 'titled', title, page[2 + i]
    if kind == 'incident':
        return 'titled', INCIDENT_HEAD[lang], '$(l)' + page[1][i] + '$()$(br2)' + page[2 + i]
    if kind == 'slot':
        return 'slot', None, page[2 + i]
    raise ValueError(f'unknown handbook page kind {kind!r}')


GENES_PER_PAGE = 5


def gene_pages(lang, names):
    """The generated list of every gene and its vanilla carriers (read from Gene.java), as text pages."""
    import gen_p16_repairs3 as g
    i = 0 if lang == 'en' else 1
    lines = []
    for gene, creatures in g.genes_from_source():
        name = names.get(f'gene.headinajar.{gene}', gene)
        lines.append(f'$(item){name}$(): ' + ', '.join(g.CREATURES[c][i] for c in creatures))
    return [('text', '$(br)'.join(lines[k:k + GENES_PER_PAGE]), '$(br)'.join(lines[k:k + GENES_PER_PAGE]))
            for k in range(0, len(lines), GENES_PER_PAGE)]


def expand(entry, lang, names):
    out = []
    for p in entry['pages']:
        out += gene_pages(lang, names) if p[0] == 'gene_list' else [p]
    return out


def lang_table(code):
    path = os.path.join(RES, 'assets', 'headinajar', 'lang', f'{code}.json')
    return json.load(open(path, encoding='utf-8')) if os.path.exists(path) else {}


def entry_pages(entry, lang, names=None):
    """Every page of an entry as page_parts, with DOLORES's footnote added to the last one."""
    i = 0 if lang == 'en' else 1
    if names is None:
        names = lang_table('en_us' if lang == 'en' else 'pt_br')
    parts = [list(page_parts(p, lang)) for p in expand(entry, lang, names)]
    note = entry.get('note')
    if note:
        parts[-1][2] = parts[-1][2] + '$(br2)$(o)' + note[i] + '$()'
    return parts


def load_handbook():
    """The second edition's categories, entries and JEI pages, from the handbook text modules beside this file."""
    sys.path.insert(0, HERE)
    cats, entries, jei = [], [], {}
    for name in ('gen_p27_handbook_a', 'gen_p27_handbook_b'):
        try:
            mod = __import__(name)
        except ImportError:
            continue
        cats += getattr(mod, 'CATEGORIES', [])
        entries += getattr(mod, 'ENTRIES', [])
        jei.update(getattr(mod, 'JEI_INFO', {}))
    return cats, entries, jei


def fit_problems(entries):
    """Every page that would not fit, in either language: (entry id, page number, language, lines, limit)."""
    out = []
    for e in entries:
        for lang in ('en', 'pt'):
            for n, (kind, title, text) in enumerate(entry_pages(e, lang)):
                limit = PAGE_LINES['first' if n == 0 and kind == 'text' else kind]
                if n == 0 and e['pages'][0][0] == 'memo':
                    limit -= 1  # the written edition shows the entry's name and the memo's heading above it
                lines = written_lines(text)
                if title:
                    for tl in wrap(title, BOOK_WIDTH)[1:]:
                        limit -= 1
                if len(lines) > limit:
                    out.append((e['id'], n + 1, lang, len(lines), limit))
                if title and width(title) > TITLE_PX:
                    out.append((e['id'], n + 1, lang, f'title "{title}" is {width(title)} px, over {TITLE_PX}', 0))
            if width(e[lang]) > ENTRY_NAME_PX:
                out.append((e['id'], 0, lang, f'entry name "{e[lang]}" is {width(e[lang])} px, over {ENTRY_NAME_PX}', 0))
                try:
                    to_written(text)
                except ValueError as err:
                    out.append((e['id'], n + 1, lang, str(err), 0))
    return out


# ---------------------------------------------------------------------------------------------------------------
# Writing both editions.

HANDBOOK_DIR = 'assets/headinajar/patchouli_books/handbook'
PICTURES = 'assets/headinajar/textures/gui/handbook'


# Item names too wide for a Patchouli page's title (the page heads a recipe or spotlight with the item's name): a shorter
# title for that page, by the page's item or recipe and language.
SHORT_TITLES = {
    ('headinajar:relay_goggles', 'pt'): 'Óculos Retransm. Mk I',
    ('headinajar:body_recall_beacon', 'pt'): 'Farol de Chamado',
    ('headinajar:surgical_steel_ingot_from_smelting', 'pt'): 'Aço Cirúrgico',
}


def short_title(out, page, lang):
    short = SHORT_TITLES.get((page[1], lang)) if len(page) > 1 and isinstance(page[1], str) else None
    if short and out['type'] in ('patchouli:spotlight', 'patchouli:crafting', 'patchouli:smelting'):
        out['title'] = short
    return out


def patchouli_page(page, kind, title, text):
    text = text.replace('%', '%%')
    title = title.replace('%', '%%') if title else title
    if kind in ('text', 'titled'):
        out = {'type': 'patchouli:text', 'text': text}
        if title:
            out['title'] = title
        return out
    if kind == 'spotlight':
        return {'type': 'patchouli:spotlight', 'item': page[1], 'text': text}
    if kind in ('crafting', 'smelting'):
        return {'type': f'patchouli:{kind}', 'recipe': page[1], 'text': text}
    if kind == 'slot':
        slot, fallback = page[1]
        if os.path.exists(os.path.join(RES, PICTURES, f'{slot}.png')):
            return {'type': 'patchouli:image', 'images': [f'headinajar:textures/gui/handbook/{slot}.png'], 'border': True,
                    'text': text}
        return {'type': 'patchouli:spotlight', 'item': fallback, 'text': text}
    raise ValueError(kind)


def item_name_key(item, EN):
    ns, path = item.split(':', 1)
    if item == 'minecraft:potion':
        return 'item.minecraft.potion.effect.water'
    if ns != 'headinajar':
        return f'item.{ns}.{path}' if path not in VANILLA_BLOCKS else f'block.{ns}.{path}'
    for k in (f'item.{ns}.{path}', f'block.{ns}.{path}'):
        if k in EN:
            return k
    raise KeyError(f'no name for {item}')


VANILLA_BLOCKS = {'white_wool', 'lever', 'glass', 'chest', 'iron_block', 'redstone_block', 'bone_block', 'crafting_table',
                  'piston', 'hopper', 'observer', 'dispenser', 'furnace', 'slime_block', 'gold_block', 'copper_block',
                  'iron_bars', 'stone', 'cobblestone', 'smooth_stone', 'tinted_glass', 'glass_pane', 'kelp', 'redstone_torch',
                  'repeater', 'comparator', 'daylight_detector', 'note_block', 'tripwire_hook', 'amethyst_block',
                  'sculk_sensor', 'beacon', 'end_rod', 'lightning_rod', 'chain', 'anvil', 'cauldron', 'brewing_stand',
                  'target', 'quartz_block', 'obsidian', 'crying_obsidian', 'magma_block', 'blast_furnace', 'smoker',
                  'barrel', 'iron_trapdoor', 'heavy_weighted_pressure_plate', 'light_weighted_pressure_plate', 'sponge',
                  'tnt', 'dried_kelp_block', 'honey_block', 'conduit', 'lodestone', 'respawn_anchor', 'netherite_block',
                  'diamond_block', 'emerald_block', 'lapis_block', 'coal_block', 'oak_planks', 'black_wool',
                  'red_wool', 'iron_door', 'oak_trapdoor', 'stone_button', 'oak_button', 'stone_pressure_plate', 'torch'}


def recipe_component(recipe_id, EN):
    """The ingredients of a recipe in words, for the written edition (it has no recipe grid)."""
    ns, path = recipe_id.split(':', 1)
    r = json.load(open(os.path.join(RES, 'data', ns, 'recipe', f'{path}.json'), encoding='utf-8'))
    counts = {}

    def add(ing, n=1):
        if isinstance(ing, list):
            ing = ing[0]
        if 'items' in ing:  # a NeoForge compound ingredient: name its first item
            first = ing['items'][0] if isinstance(ing['items'], list) else ing['items']
            ing = {'tag': first[1:]} if first.startswith('#') else {'item': first}
        key = ('item', ing['item']) if 'item' in ing else ('tag', ing['tag'])
        counts[key] = counts.get(key, 0) + n

    if r['type'] == 'minecraft:crafting_shaped':
        for row in r['pattern']:
            for c in row:
                if c != ' ':
                    add(r['key'][c])
    elif r['type'] == 'minecraft:crafting_shapeless':
        for ing in r['ingredients']:
            add(ing)
    else:
        add(r['ingredient'])
    parts = [{'translate': 'book.headinajar.written2.recipe'}]
    for n, ((kind, ident), count) in enumerate(counts.items()):
        parts.append(', ' if n else ' ')
        name = {'translate': item_name_key(ident, EN)} if kind == 'item' else '#' + ident.split(':', 1)[1].replace('_', ' ')
        parts += [f'{count} ', name]
    parts.append('\n')
    return parts


def emit_handbook(EN, PT, write):
    """Both editions from the one text. Returns the written edition's page count."""
    import shutil
    cats, entries, jei = load_handbook()
    for code in ('en_us', 'pt_br'):
        shutil.rmtree(os.path.join(RES, HANDBOOK_DIR, code), ignore_errors=True)
    tables = {'en': EN, 'pt': PT}
    book = json.load(open(os.path.join(RES, 'data/headinajar/patchouli_books/handbook/book.json'), encoding='utf-8'))
    # the edition, in the reader's language: Patchouli shows the subtitle only for version 0 (any other version is drawn
    # as its own "2nd Edition" in English), and the version is used for nothing else
    book['version'] = 0
    book['subtitle'] = 'book.headinajar.handbook.subtitle'
    write('data/headinajar/patchouli_books/handbook/book.json', book)
    for lang, code in (('en', 'en_us'), ('pt', 'pt_br')):
        i = 0 if lang == 'en' else 1
        base = f'{HANDBOOK_DIR}/{code}'
        for cid, sort, icon, en_n, en_d, pt_n, pt_d in cats:
            write(f'{base}/categories/{cid}.json', {'name': (en_n, pt_n)[i], 'description': (en_d, pt_d)[i], 'icon': icon,
                                                    'sortnum': sort})
        for e in entries:
            raw = expand(e, lang, tables[lang])
            parts = entry_pages(e, lang, tables[lang])
            write(f'{base}/entries/{e["cat"]}/{e["id"]}.json', {
                'name': e[lang], 'icon': e['icon'], 'category': f'headinajar:{e["cat"]}', 'sortnum': e.get('sort', 0),
                'pages': [short_title(patchouli_page(p, *parts[n]), p, lang) for n, p in enumerate(raw)]})
    for item, (en, pt) in jei.items():
        key = 'jei.headinajar.' + item.split(':', 1)[1]
        EN[key], PT[key] = en, pt
    for key, en, pt in WRITTEN_LANG:
        EN[key], PT[key] = en, pt

    # the written edition: a title page, a contents page, then every entry, page for page
    order = {c[0]: c[1] for c in cats}
    entries = sorted(entries, key=lambda e: (order[e['cat']], e.get('sort', 0)))
    pages = [[{'translate': 'book.headinajar.written2.title'}]]
    contents = [{'translate': 'book.headinajar.written2.contents', 'bold': True}, '\n\n']
    pages.append(contents)
    first_of = {}
    for e in entries:
        raw = expand(e, 'en', EN)
        en_parts, pt_parts = entry_pages(e, 'en', EN), entry_pages(e, 'pt', PT)
        for n, p in enumerate(raw):
            first_of.setdefault(e['cat'], len(pages) + 1)
            key = f'book.headinajar.written2.{e["cat"]}.{e["id"]}.{n + 1}'
            (kind, en_title, en_text), (_k, pt_title, pt_text) = en_parts[n], pt_parts[n]
            en_v, keys = to_written(en_text)
            pt_v, pt_keys = to_written(pt_text, keys)
            assert sorted(keys) == sorted(pt_keys), (key, keys, pt_keys)
            EN[key], PT[key] = en_v, pt_v
            page = []
            if n == 0:
                ek = f'book.headinajar.entry.{e["cat"]}.{e["id"]}'
                EN[ek], PT[ek] = e['en'], e['pt']
                page += [{'translate': ek, 'bold': True}, '\n']
            if en_title and (n or kind == 'titled' and e['pages'][0][0] == 'memo'):
                tk = key + '.title'
                EN[tk], PT[tk] = en_title, pt_title
                page += [{'translate': tk, 'bold': True}, '\n']
            if kind in ('spotlight', 'slot') and n:
                item = p[1] if kind == 'spotlight' else p[1][1]
                page += [{'translate': item_name_key(item, EN), 'bold': True}, '\n']
            if kind in ('crafting', 'smelting'):
                # the recipe in words (a book has no grid); its item's name only past the entry's first page
                if n:
                    result = json.load(open(os.path.join(RES, 'data', 'headinajar', 'recipe', p[1].split(':', 1)[1] + '.json'),
                                            encoding='utf-8'))['result']['id']
                    page += [{'translate': item_name_key(result, EN), 'bold': True}, '\n']
                page += recipe_component(p[1], EN) + ['\n']
            body = {'translate': key}
            if keys:
                body['with'] = [{'keybind': k} for k in keys]
            pages.append(page + [body])
    for cid, sort, icon, en_n, en_d, pt_n, pt_d in sorted(cats, key=lambda c: c[1]):
        ck = f'book.headinajar.written2.cat.{cid}'
        EN[ck], PT[ck] = en_n, pt_n
        contents += [{'translate': ck, 'color': 'dark_blue', 'underlined': True,
                      'clickEvent': {'action': 'change_page', 'value': str(first_of[cid])}}, '\n']
    assert len(pages) <= 100, f'the written edition is {len(pages)} pages; a written book holds 100'
    book = {'id': 'minecraft:written_book', 'count': 1, 'components': {
        'minecraft:written_book_content': {'title': 'Employee Handbook', 'author': 'Noggin & Sons', 'generation': 0,
                                           'pages': [json.dumps(p[0] if len(p) == 1 else [''] + p, ensure_ascii=False) for p in pages],
                                           'resolved': True},
        'minecraft:custom_name': json.dumps({'translate': 'book.headinajar.handbook', 'italic': False})}}
    patchouli = {'type': 'neoforge:mod_loaded', 'modid': 'patchouli'}
    write('data/headinajar/recipe/handbook_copy_written.json', {
        'neoforge:conditions': [{'type': 'neoforge:not', 'value': patchouli}],
        'type': 'minecraft:crafting_shapeless', 'category': 'misc',
        'ingredients': [{'item': 'minecraft:book'}, {'item': 'headinajar:surgical_steel_blend'}], 'result': book})
    return len(pages)


WRITTEN_LANG = [
    ('book.headinajar.written2.title',
     'Employee Handbook\nSecond Edition\n\nNoggin & Sons\nHead Office\n\nThe same handbook as the one with '
     'pictures, which comes with Patchouli. The next page lists the chapters: click one to go there.\n\nLost it? A book '
     'and a Surgical Steel Blend make another.',
     'Manual do Funcionário\nSegunda Edição\n\nNoggin & Sons\nSede Central\n\nO mesmo manual da edição com figuras, '
     'que vem com o Patchouli. A próxima página lista os capítulos: clique num para ir até ele.\n\nPerdeu? Um livro e '
     'uma Mistura de Aço Cirúrgico fazem outro.'),
    ('book.headinajar.written2.contents', 'Contents', 'Sumário'),
    ('book.headinajar.written2.recipe', 'Recipe:', 'Receita:'),
]



# ---------------------------------------------------------------------------------------------------------------
# The career ladder: guidepost advancements whose descriptions say how. Vanilla triggers where an item or a placed
# block is the step; the mod's event trigger, fired by clarity/Guideposts from the player's state, for the rest.
# Advancement descriptions can't carry a key, so they name the action, never a key.

def _inv(item):
    return {'got': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': item}]}}}


def _placed(block):
    return {'placed': {'trigger': 'minecraft:placed_block', 'conditions': {'location': [
        {'condition': 'minecraft:location_check', 'predicate': {'block': {'blocks': block}}}]}}}


def _used_on(block):
    return {'fed': {'trigger': 'minecraft:item_used_on_block', 'conditions': {'location': [
        {'condition': 'minecraft:location_check', 'predicate': {'block': {'blocks': block}}}]}}}


def _event(name):
    return {'done': {'trigger': 'headinajar:event', 'conditions': {'event': name}}}


LADDER = [
    # id, parent, icon, criteria, en title, en description, pt title, pt description
    ('steel', 'root', 'headinajar:surgical_steel_ingot', _inv('headinajar:surgical_steel_ingot'),
     'Steel Yourself', 'Smelt a Surgical Steel Blend (iron ingot, bone meal and coal) into Surgical Steel',
     'Nervos de Aço', 'Funda uma Mistura de Aço Cirúrgico (lingote de ferro, farinha de osso e carvão) em Aço Cirúrgico'),
    ('saline', 'root', 'headinajar:saline_bottle', _inv('headinajar:saline_bottle'),
     'Stay Hydrated', 'Craft Cranial Saline: a water bottle, sugar, kelp and redstone',
     'Mantenha-se Hidratado', 'Fabrique Soro Craniano: frasco de água, açúcar, alga e redstone'),
    ('powered', 'steel', 'headinajar:galvanic_pile', _used_on('headinajar:galvanic_pile'),
     'Meat-Based Economy', 'Feed a Galvanic Pile: use rotten flesh or raw meat on it',
     'Economia de Carne', 'Alimente uma Pilha Galvânica: use carne podre ou carne crua nela'),
    ('chair', 'powered', 'headinajar:separation_chair', _event('chair_powered'),
     'Take a Seat', 'Place a Separation Chair touching a fed Galvanic Pile',
     'Sente-se', 'Coloque uma Cadeira de Separação encostada numa Pilha Galvânica alimentada'),
    ('head_carried', 'separated', 'headinajar:head_satchel', _event('head_carried'),
     'Hands-On Management', 'Carry your own head: use it with a Head Satchel on you',
     'Gestão Prática', 'Carregue a própria cabeça: use-a com uma Bolsa de Cabeça consigo'),
    ('first_module', 'separated', 'headinajar:wheels', _event('first_module'),
     'Some Assembly Required', 'As a jar, fit a module from the jar menu',
     'Montagem Necessária', 'Como pote, instale um módulo pelo menu do pote'),
    ('armed', 'first_module', 'headinajar:manipulator_arm', _event('armed'),
     'Hands-Free No More', 'Fit a Manipulator Arm: then a jar can open, hold and dig',
     'Mãos à Obra', 'Instale um Braço Manipulador: aí um pote abre, segura e cava'),
    ('charged', 'separated', 'headinajar:charging_dock', _event('charged'),
     'Plugged In', 'Charge your jar: a fed Charging Dock, a Cradle, or redstone in the feed slot',
     'Na Tomada', 'Carregue seu pote: uma Base de Recarga alimentada, um Berço ou redstone no espaço de recarga'),
    ('reattached', 'separated', 'headinajar:suture_kit', _event('reattached'),
     'Back on Your Shoulders', 'Put your head back on: use a Suture Kit on your body',
     'De Volta aos Ombros', 'Ponha a cabeça de volta: use um Kit de Sutura no seu corpo'),
    ('shell', 'separated', 'headinajar:rover_chassis', _event('shell'),
     'Temp Agency', 'As a jar, use a Lunk, Rover or Spider Walker to climb in',
     'Agência de Temporários', 'Como pote, use um Brutamontes, Rover ou Andador Aranha para entrar'),
    ('gantry', 'steel', 'headinajar:assembly_gantry', _placed('headinajar:assembly_gantry'),
     'Facilities Request', 'Place an Assembly Gantry: it is also the pad the mecha stands on',
     'Pedido à Manutenção', 'Coloque um Pórtico de Montagem: ele também é a plataforma do mecha'),
    ('boarded', 'assembled', 'headinajar:hopper_frame', _event('boarded'),
     'Corner Office', 'As your jar, use your mecha to climb into the cockpit',
     'Sala da Diretoria', 'Como pote, use seu mecha para entrar na cabine'),
]
# existing advancements that now hang from a step of the ladder (where they draw in the tab; progress is kept by id)
REPARENT = {'signed': 'chair', 'assembled': 'gantry'}

ROOT = ('Orientation Day', 'Welcome to Noggin & Sons. Your Handbook is in your pocket. Start with Surgical Steel.',
        'Dia de Integração', 'Bem-vindo à Noggin & Sons. Seu Manual está no bolso. Comece pelo Aço Cirúrgico.')


def emit_ladder(EN, PT, write):
    root = json.load(open(os.path.join(RES, 'data/headinajar/advancement/root.json'), encoding='utf-8'))
    # granted on the first tick in the world, so the tab and its next steps show from the start; not announced
    root['criteria'] = {'arrived': {'trigger': 'minecraft:tick'}}
    root['display']['announce_to_chat'] = False
    write('data/headinajar/advancement/root.json', root)
    EN['advancements.headinajar.root.title'], EN['advancements.headinajar.root.description'] = ROOT[0], ROOT[1]
    PT['advancements.headinajar.root.title'], PT['advancements.headinajar.root.description'] = ROOT[2], ROOT[3]
    for adv, parent in REPARENT.items():
        path = os.path.join(RES, f'data/headinajar/advancement/{adv}.json')
        body = json.load(open(path, encoding='utf-8'))
        body['parent'] = f'headinajar:{parent}'
        write(f'data/headinajar/advancement/{adv}.json', body)
    for key, parent, icon, criteria, en_t, en_d, pt_t, pt_d in LADDER:
        write(f'data/headinajar/advancement/{key}.json', {
            'parent': f'headinajar:{parent}', 'criteria': criteria,
            'display': {'icon': {'id': icon}, 'title': {'translate': f'advancements.headinajar.{key}.title'},
                        'description': {'translate': f'advancements.headinajar.{key}.description'}, 'frame': 'task',
                        'show_toast': True, 'announce_to_chat': True, 'hidden': False}})
        for table, t, d in ((EN, en_t, en_d), (PT, pt_t, pt_d)):
            table[f'advancements.headinajar.{key}.title'], table[f'advancements.headinajar.{key}.description'] = t, d


# ---------------------------------------------------------------------------------------------------------------
# Lines of the HUD, the screens, the tooltips and DOLORES's memos. (key, en, pt)

LANG = [
    # DOLORES's pager (clarity/Memos.java): one line each, the first time; keys are the reader's own bindings
    ('memo.headinajar.from', '[Memo] DOLORES:', '[Memorando] DOLORES:'),
    ('memo.headinajar.link', '[Handbook]', '[Manual]'),
    ('memo.headinajar.link.hover', 'Open the handbook at this entry', 'Abrir o manual nesta seção'),
    ('memo.headinajar.no_hands',
     'You are a jar now, and jars have no hands. The company’s own machines answer you; nothing else will. Fit a %2$s from the jar menu (%1$s): craft it with your hands on (3 iron ingots, redstone, a copper ingot) and keep it in your pocket.',
     'Agora você é um pote, e potes não têm mãos. As máquinas da firma atendem você; o resto, não. Instale um %2$s pelo menu do pote (%1$s): fabrique-o ainda com mãos (3 lingotes de ferro, redstone, um lingote de cobre) e guarde-o no bolso.'),
    ('memo.headinajar.head_left',
     'You are your body now. Your head waits where it was set down (%2$s switches to it) and drinks saline while you are away. Carried in a %1$s, it drinks none.',
     'Agora você é o seu corpo. Sua cabeça espera onde foi deixada (%2$s troca para ela) e bebe soro enquanto você está longe. Carregada numa %1$s, não bebe nada.'),
    ('memo.headinajar.welcome',
     'Welcome to Noggin & Sons. Your Employee Handbook is in your inventory: hold it and use it to read. Your first jobs are on the Head in a Jar tab of the advancements.',
     'Bem-vindo à Noggin & Sons. Seu Manual do Funcionário está no inventário: segure-o e use-o para ler. Suas primeiras tarefas estão na aba Cabeça no Pote das conquistas.'),
    ('memo.headinajar.head_drying',
     'Your head is down to a quarter of its saline. When it runs out the glass cracks, then it is a real death. Use %s on it, or carry it in a %s.',
     'Sua cabeça está com um quarto do soro. Quando acabar, o vidro racha, e aí a morte é de verdade. Use %s nela, ou leve-a numa %s.'),
    ('memo.headinajar.charge_low',
     'Charge under a quarter. Stand on a fed %s, or put redstone in the jar menu\'s feed slot. A head docked in a %s charges too.',
     'Carga abaixo de um quarto. Fique numa %s alimentada, ou ponha redstone no espaço de recarga do menu do pote. Uma cabeça num %s também carrega.'),
    ('memo.headinajar.first_host',
     'You are riding. Sync grows about 5%% a minute; the first ability opens at 50%% (%s), the second at 75%%. To get down: %s.',
     'Você está montado. A sincronia sobe uns 5%% por minuto; a primeira habilidade abre em 50%% (%s), a segunda em 75%%. Para descer: %s.'),
    ('memo.headinajar.mecha_built',
     'Your mecha is on the pad. Swap to your jar (%1$s) and use the mecha to climb in. To get out: hold %2$s and %3$s.',
     'Seu mecha está na plataforma. Troque para o pote (%1$s) e use o mecha para entrar. Para sair: segure %2$s e %3$s.'),

    # the jar's HUD and menu
    ('hud.headinajar.clarity.mb', '%s mB', '%s mB'),
    ('gui.headinajar.clarity.charge_hover', '%s / %s FE', '%s / %s FE'),
    ('gui.headinajar.clarity.slot_empty', 'Module slot. Put a jar module here from your inventory: a Manipulator Arm first.',
     'Espaço de módulo. Ponha aqui um módulo do inventário: primeiro um Braço Manipulador.'),
    ('gui.headinajar.clarity.slot_shut', 'Shut on this jar. A %s has 5 slots, an %s 7.',
     'Fechado neste pote. Um %s tem 5 espaços, um %s, 7.'),

    # the pilot's and the rider's HUD
    ('hud.headinajar.clarity.battery', 'Battery %s kFE', 'Bateria %s kFE'),
    ('hud.headinajar.clarity.arm', '%s: %s', '%s: %s'),
    ('ability.headinajar.clarity.further', '%s (further)', '%s (mais longe)'),
    ('hud.headinajar.clarity.ability_ready', '%s: %s', '%s: %s'),
    ('hud.headinajar.clarity.ability_locked', '%2$s: %3$s%% sync', '%2$s: sinc. %3$s%%'),

    ('message.headinajar.clarity.suture_head_on', 'Your head is already on. Keep the kit for when it isn’t.',
     'Sua cabeça já está no lugar. Guarde o kit para quando não estiver.'),

    ('message.headinajar.clarity.key_clash', 'Your key for %1$s (%2$s) also does %3$s. If they get in each other\u2019s way, rebind one in %4$s.',
     'Sua tecla de %1$s (%2$s) também faz %3$s. Se um atrapalhar o outro, troque um deles em %4$s.'),

    # /headoffice status, every word translated
    ('command.headinajar.status.where', '%s at %s', '%s em %s'),
    ('command.headinajar.status.head.player', 'you are it', 'é você'),
    ('command.headinajar.status.head.entity', 'set down', 'pousada'),
    ('command.headinajar.status.head.cradle', 'in a Cradle', 'num Berço'),
    ('command.headinajar.status.head.carried', 'carried', 'carregada na bolsa'),
    ('command.headinajar.status.corpse', 'a corpse', 'cadáver'),
    ('command.headinajar.status.standing', 'standing', 'de pé'),
    ('command.headinajar.status.form.body', 'whole', 'inteiro'),
    ('command.headinajar.status.form.jar', 'jar', 'pote'),
    ('command.headinajar.status.form.hosted', 'riding a host', 'num hospedeiro'),
    ('command.headinajar.status.form.socket', 'in a socket', 'num soquete'),
    ('command.headinajar.status.form.linked', 'linked body', 'corpo conectado'),
    ('command.headinajar.status.form.pilot', 'piloting', 'pilotando'),
    # a serum that curdled: nothing took (the blood types did not fight)
    ('message.headinajar.inject.dud', 'DUD: the serum curdled. Nothing took.', 'FALHOU: o soro talhou. Nada pegou.'),

    # Jade
    ('jade.headinajar.clarity.ability', '%s (%s%%)', '%s (%s%%)'),
    ('jade.headinajar.clarity.abilities', 'Abilities: %s %s', 'Habilidades: %s %s'),
    ('jade.headinajar.clarity.own_head', 'Your head: saline %s/%s mB, glass %s/%s', 'Sua cabeça: soro %s/%s mB, vidro %s/%s'),
    ('config.jade.plugin_headinajar.own_head', "Your own head's saline and glass", 'Soro e vidro da sua cabeça'),

    # tooltips (clarity/Tooltips.java)
    ('tooltip.headinajar.clarity.module.fit', 'Fit it from the jar menu (%s).', 'Instale pelo menu do pote (%s).'),
    ('tooltip.headinajar.clarity.module.telekinesis', '%1$s grabs what you look at; %1$s again sets it down.',
     '%1$s agarra o que você olha; %1$s de novo o solta.'),
    ('tooltip.headinajar.clarity.module.lacrimal_laser', 'Fires with %s.', 'Dispara com %s.'),
    ('tooltip.headinajar.clarity.module.wheels', '%s puts the wheels down and up.', '%s baixa e recolhe as rodinhas.'),
    ('tooltip.headinajar.clarity.telekinesis.range', 'Reaches %s blocks.', 'Alcança %s blocos.'),
    ('tooltip.headinajar.clarity.saline', '+%s mB for a head in a jar: the feed slot, or use it on your set-down head.',
     '+%s mB para uma cabeça no pote: no espaço de recarga, ou use na cabeça deixada.'),
    ('tooltip.headinajar.clarity.saline.lasts', 'A jar holds %s mB: about %s h %s min for a head out in the world. Carried or docked, it drinks none.',
     'Um pote leva %s mB: umas %s h %s min para uma cabeça largada no mundo. Carregada ou no berço, não bebe nada.'),
    ('tooltip.headinajar.clarity.patch', '+%s Jar Integrity: the feed slot, or use it on your set-down head.',
     '+%s de Integridade do Pote: no espaço de recarga, ou use na cabeça deixada.'),
    ('tooltip.headinajar.clarity.blade', 'Use it on an idle %s to fit a new blade. Above 80%% wear a cut can botch.',
     'Use numa %s parada para trocar a lâmina. Acima de 80%% de desgaste, o corte pode sair torto.'),
]

# what each host ability is called on the rider's HUD (the ability types of host data files)
ABILITIES = {
    'arrows': ('Arrow Volley', 'Saraivada de Flechas'), 'charge': ('Charge', 'Investida'),
    'echolocation': ('Echolocation', 'Ecolocalização'), 'explode': ('Explode', 'Explodir'), 'fireball': ('Fireball', 'Bola de Fogo'),
    'groan': ('Groan', 'Gemido'), 'hover': ('Hover', 'Pairar'), 'ink': ('Ink Cloud', 'Nuvem de Tinta'), 'lay_egg': ('Lay an Egg', 'Botar um Ovo'),
    'potion': ('Throw a Potion', 'Jogar Poção'), 'pounce': ('Pounce', 'Bote'), 'roar': ('Roar', 'Rugido'),
    'sonic_boom': ('Sonic Boom', 'Estrondo Sônico'), 'sprint': ('Sprint', 'Disparada'), 'teleport': ('Teleport', 'Teleporte'),
    'toss': ('Toss', 'Arremesso'), 'web': ('Web Shot', 'Teia'), 'wind_charge': ('Wind Charge', 'Carga de Vento'),
}


def emit_lang(EN, PT):
    for key, en, pt in LANG:
        EN[key], PT[key] = en, pt
    for t, (en, pt) in ABILITIES.items():
        EN[f'ability.headinajar.{t}'], PT[f'ability.headinajar.{t}'] = en, pt


# ---------------------------------------------------------------------------------------------------------------
# Checks over every line of the mod (B43): one name for one thing, no physical keys, lines that fit where they show.

GLOSSARY_PT = {  # a Portuguese variant the mod no longer uses -> the term it uses
    'Câmera de Colarinho': 'Câmera de Colar',
    'Óculos de Retransmissão': 'Óculos Retransmissores', 'Estação de Recarga': 'Base de Recarga',
}
VOICED = ('dolores.headinajar.',)  # lines DOLORES speaks from a recording: changed with the recording, not alone
PHYSICAL_KEY = re.compile(r'\b(Press|press|Hold|hold|Sneak\+)\s+[A-Z]\b|\([A-Z]\)|\bShift\b|\b[Rr]ight-click|\b[Ll]eft-click')
WIDTH_LIMITS = [  # (key prefix, en px, pt px): an action-bar line, a HUD line
    # the action bar at GUI 3 on a 1280-wide window is 426 px wide: 400 leaves its margins
    ('refusal.headinajar.', 400, 400), ('warning.headinajar.', 400, 400),
]


# Every line the HUD's side column (client/HudStack) can show, with long but real arguments: it is at most a third of the
# screen wide, 142 px at GUI 3 on a 1280-wide window. A line should fit; one that doesn't wraps, and more than two lines
# is an error. (bar: the line is a bar's label, which leaves at least 30 px of bar beside it.)
COLUMN_PX = 142
COLUMN = [
    ('hud.headinajar.freshness', ('100',), 'bar'), ('hud.headinajar.sync', ('100',), 'bar'),
    ('hud.headinajar.clarity.battery', ('2000',), 'bar'), ('hud.headinajar.heat', ('100',), 'bar'),
    ('hud.headinajar.clarity.ability_ready', ('R', 'Teleport (further)'), None),
    ('hud.headinajar.clarity.ability_locked', ('R', 'Teleport (further)', '75'), None),
    ('hud.headinajar.no_abilities', (), None), ('hud.headinajar.shell_flat', ('Charging Dock',), None),
    ('hud.headinajar.utility', ('R', 'Ore Scanner', 'Button 5'), None), ('hud.headinajar.cargo', ('Home', 'Cargo Hold'), None),
    ('hud.headinajar.load', ('1200', '1000'), None), ('hud.headinajar.clarity.arm', ('Right Button', 'Hydraulic Fist'), None),
    ('hud.headinajar.venting', (), None), ('hud.headinajar.limp', (), None), ('hud.headinajar.servos_locked', (), None),
    ('hud.headinajar.no_legs', ('Assembly Gantry',), None), ('hud.headinajar.ejecting', ('100',), None),
    ('hud.headinajar.gene_key', ('R', 'Ender Blink'), None), ('hud.headinajar.release5.moves_now', ('25',), None),
]


def fill(value, args):
    value = value.replace('%%', '\0')
    for n, a in enumerate(args):
        value = value.replace(f'%{n + 1}$s', a)
    for a in args:
        value = value.replace('%s', a, 1)
    value = re.sub(r'%\d+\$s', '', value)
    return value.replace('\0', '%')


def column_check(EN, PT):
    problems, wrapped = [], []
    for key, args, kind in COLUMN:
        for lang, table in (('en', EN), ('pt', PT)):
            line = fill(table[key], args)
            room = COLUMN_PX - 34 if kind == 'bar' else COLUMN_PX
            n = len(wrap(line, room))
            if kind == 'bar' and n > 1 or n > 2:
                problems.append((key, f'{lang} "{line}" takes {n} lines in the side column ({room} px)'))
            elif n == 2:
                wrapped.append(f'{key} ({lang})')
    assert not problems, problems
    return wrapped


def lint(EN, PT, mine):
    """Problems in every line, as (key, what); the generator fails on any in the lines this file wrote."""
    out = []
    for key, pt in PT.items():
        for bad, good in GLOSSARY_PT.items():
            if bad in pt and not key.startswith(VOICED) and not key.startswith('book.headinajar.written.'):
                out.append((key, f'pt says "{bad}"; the mod calls it "{good}"'))
    for key, en in EN.items():
        if PHYSICAL_KEY.search(en) and not key.startswith(VOICED):
            out.append((key, f'names a physical key: {en[:60]!r}'))
        for prefix, en_px, pt_px in WIDTH_LIMITS:
            if key.startswith(prefix):
                if width(en) > en_px:
                    out.append((key, f'en {width(en)} px, over {en_px}'))
                if width(PT.get(key, '')) > pt_px:
                    out.append((key, f'pt {width(PT[key])} px, over {pt_px}'))
    for key, what in out:
        print('  text check:', key, '-', what)
    bad = [p for p in out if p[0] in mine or p[0].startswith(('refusal.headinajar.', 'warning.headinajar.'))]
    assert not bad, f'{len(bad)} lines fail the text check (listed above)'
    return out

def run(EN, PT, write, shaped):
    emit_lang(EN, PT)
    emit_ladder(EN, PT, write)
    before = set(EN)
    pages = emit_handbook(EN, PT, write)
    print('handbook, written edition:', pages, 'pages')
    mine = {k for k, _e, _p in LANG} | {f'ability.headinajar.{a}' for a in ABILITIES} | (set(EN) - before)
    mine |= {k for k in EN if k.startswith(('advancements.headinajar.',)) and k.split('.')[2] in {l[0] for l in LADDER}}
    problems = lint(EN, PT, mine)
    print('side column: these wrap to two lines:', ', '.join(column_check(EN, PT)) or 'none')
    print(len(problems), 'text check notes on older lines')


if __name__ == '__main__' and sys.argv[1:2] == ['fit']:
    _cats, _entries, _jei = load_handbook()
    total = sum(len(expand(e, 'en', lang_table('en_us'))) for e in _entries)
    bad = fit_problems(_entries)
    for b in bad:
        print('TOO LONG' if isinstance(b[3], int) else 'BAD MARKUP', b)
    print(f'{len(_entries)} entries, {total} pages (the written edition holds 100 with its title and contents pages: '
          f'keep this at 98 or under); {len(bad)} problems')
    for k, (en, pt) in _jei.items():
        for lang, t in (('en', en), ('pt', pt)):
            if '$(' in t:
                print('JEI page uses handbook markup', k, lang)

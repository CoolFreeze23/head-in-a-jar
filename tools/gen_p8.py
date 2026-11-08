"""The Compact Jar: names, recipes (each jar crafts into the other) and its item model. Imported by gen_data.py."""

LANG8 = [
    ('item.headinajar.compact_life_support_jar', 'Compact Jar', 'Pote Compacto'),
    ('item.headinajar.life_support_jar.compact', 'Head-sized: your head faces the front and the whole jar turns with it',
     'Do tamanho de uma cabeça: sua cabeça fica de frente e o pote inteiro gira com ela'),
    ('item.headinajar.life_support_jar.wide', 'Roomy: your head turns right round inside',
     'Espaçoso: sua cabeça gira por completo lá dentro'),
]


def run(EN, PT, write, shaped):
    for key, en, pt in LANG8:
        EN[key], PT[key] = en, pt
    write('assets/headinajar/models/item/compact_life_support_jar.json',
          {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'headinajar:item/compact_life_support_jar'}})
    # a jar on its own in the crafting grid becomes the other kind
    for name, source in (('compact_life_support_jar', 'life_support_jar'), ('life_support_jar_from_compact', 'compact_life_support_jar')):
        write(f'data/headinajar/recipe/{name}.json', {
            'type': 'minecraft:crafting_shapeless', 'category': 'equipment',
            'ingredients': [{'item': f'headinajar:{source}'}],
            'result': {'id': 'headinajar:' + ('compact_life_support_jar' if source == 'life_support_jar' else 'life_support_jar'), 'count': 1}})

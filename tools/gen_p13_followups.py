"""Lines for the small repairs made after the repairs were first seen in the game. Imported by gen_data.py.

Refusal lines follow gen_p9.py's rule: what is wrong, then what to do next; a control is never named by its key; %s
arguments are names filled in by the code.
"""

# id (after 'refusal.headinajar.'): (en, pt)
REFUSALS = {
    # a linked player carrying their head clicks a host: the head comes out of the satchel, then the climb fails
    'host.mount_failed': ("Couldn't get on %s. You're in your jar now.", 'Não deu para subir em %s. Você está no seu pote agora.'),
}

LANG = []


def run(EN, PT, write, shaped):
    for key, (en, pt) in REFUSALS.items():
        EN[f'refusal.headinajar.{key}'], PT[f'refusal.headinajar.{key}'] = en, pt
    for key, en, pt in LANG:
        EN[key], PT[key] = en, pt

"""Lines for the fourth round of repairs: no input that does nothing, no rule that contradicts itself. Imported by
gen_data.py.

Refusal lines follow gen_p9.py's rule (what is wrong, then what to do next; about 60 characters; a key is named by a
keybind argument, never by its letter). Warnings follow gen_p16_repairs3.py's (what is happening and what to do).
%s arguments are filled in by the code.
"""

# id (after 'refusal.headinajar.'): (en, pt)
REFUSALS = {
    # Embalming Fluid (%s) on a host that has a Neural Lace: it never rots, so the fluid would be wasted
    'host.embalm_laced': ('Laced bodies never rot. Keep the %s for one that does.',
                          'Corpo com Renda Neural não apodrece. Guarde o %s para outro.'),
    # a second Neural Lace (%s) on a host that has one
    'host.lace_again': ('This body is laced already. Keep the %s.',
                        'Este corpo já tem renda. Guarde a %s.'),
    # the ability or secondary key on a body (yours, a host, a mecha) with no active gene: %s the Injection Rig
    'gene.none': ('No active gene in this body. The %s can give it one.',
                  'Nenhum gene ativo neste corpo. A %s pode dar um.'),
    # the secondary key with one active gene: %1$s the gene, %2$s the ability key that fires it
    'gene.one_only': ('Only one active gene here: %1$s, on %2$s.',
                      'Só um gene ativo aqui: %1$s, em %2$s.'),
    # the gene (%s) fired a moment ago
    'gene.cooling': ('%s is still recovering. Give it a moment.',
                     '%s ainda está se recuperando. Um instante.'),
    # the gene (%s) needs a creature or a block in reach and the look found none
    'gene.no_target': ('%s needs something in reach to aim at.',
                       '%s precisa de algo ao alcance para mirar.'),
    # sneak on a flying host (%s) high above the ground: the jar is not dropped
    'host.too_high': ('Too high to get off %s. Fly lower first.',
                      'Alto demais para descer de %s. Voe mais baixo antes.'),
    # the swap key with the head still on
    'swap.body': ('Your head is still on. The Separation Chair sees to that.',
                  'Sua cabeça ainda está no lugar. A Cadeira de Separação resolve.'),
    # the drive key on foot
    'drive.body': ('Nothing to drive on foot. Drive is for jars and mecha.',
                   'Nada para dirigir a pé. Isso é para potes e mechas.'),
    # Repair pressed on a dented mecha with the plating slot empty: %s Mecha Plating
    'gantry.no_plating': ('Nothing to mend it with. Put %s in the plating slot.',
                          'Nada para consertar. Ponha %s no espaço de placas.'),
    # Repair pressed on a mecha (%s) with full health and a full battery
    'gantry.nothing_to_repair': ('%s is in perfect order. Nothing to repair.',
                                 '%s está em perfeita ordem. Nada a consertar.'),
}

# id (after 'warning.headinajar.'): (en, pt)
WARNINGS = {
    # the jar ran flat with its wheels down: %1$s the Charging Dock, %2$s the drive key (tips the jar to roll)
    'jar.wheels_flat': ('Wheels flat: folded away. Charge at a %1$s, or %2$s to roll.',
                        'Rodinhas sem carga: recolhidas. Carregue numa %1$s ou %2$s para rolar.'),
    # a bow head fitted in a Turret Pedestal with no arrows in a container under or beside it: %s a chest
    'turret.no_arrows': ('No arrows in reach. Put a %s of them under it.',
                         'Sem flechas ao alcance. Ponha um %s delas embaixo.'),
    # a creeper head fitted in a Turret Pedestal: its one blast uses the head up
    'turret.creeper_once': ('A creeper head goes off once and is spent. Choose the spot.',
                            'Cabeça de creeper explode uma vez e acabou. Escolha o lugar.'),
}

# (key, en, pt)
LANG = [
    # a powered jar module that stops when the jar is flat but spends nothing itself
    ('module.headinajar.needs_charge', 'Needs some charge', 'Precisa de alguma carga'),
    # the tip-over line with the drive key as bound (%s)
    ('message.headinajar.tipped_keyed', 'You tip the jar over. Roll, very slowly. Press %s again to sit up.',
     'Você tomba o pote. Role, bem devagar. Aperte %s de novo para se endireitar.'),
    # the Zoom Optic's tooltip with the zoom key as bound (%s)
    ('item.headinajar.collar_zoom_optic.keyed', 'Hold %s for x3 zoom.', 'Segure %s para zoom x3.'),
    # /headoffice trust add for someone already trusted; remove for someone never trusted
    ('command.headinajar.already_trusted', '%s is already trusted', '%s já é de confiança'),
    ('command.headinajar.never_trusted', '%s was not on your trust list', '%s não estava na sua lista de confiança'),
]


def run(EN, PT, write, shaped):
    for key, (en, pt) in REFUSALS.items():
        EN[f'refusal.headinajar.{key}'], PT[f'refusal.headinajar.{key}'] = en, pt
    for key, (en, pt) in WARNINGS.items():
        EN[f'warning.headinajar.{key}'], PT[f'warning.headinajar.{key}'] = en, pt
    for key, en, pt in LANG:
        EN[key], PT[key] = en, pt

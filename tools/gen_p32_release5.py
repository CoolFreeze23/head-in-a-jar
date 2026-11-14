"""Lines for the 0.5.0 release's own fixes. Imported by gen_data.py, before the handbook generator (gen_p27_clarity),
whose text check reads the final lines."""


def run(EN, PT, write, shaped):
    # older lines over the action bar's width at GUI 3 (400 px), shortened
    PT['refusal.headinajar.locker.not_yours'] = 'Corpo de outra pessoa. O dono pode confiar em você: /headoffice trust.'
    EN['refusal.headinajar.pile.not_fuel'] = 'The Pile burns %2$s and %3$s, not %1$s.'
    PT['refusal.headinajar.pile.not_fuel'] = 'A Pilha queima %2$s e %3$s, não %1$s.'
    PT['refusal.headinajar.mixer.nothing_to_fill'] = 'Falta %2$s no Misturador para encher um %1$s.'
    EN['warning.headinajar.jar.wheels_flat'] = 'Wheels flat: folded. Charge at a %1$s, or %2$s to roll.'
    PT['warning.headinajar.jar.wheels_flat'] = 'Rodinhas recolhidas. Carregue numa %1$s; %2$s rola.'

    # a Body Locker holding someone's body is theirs to break (or someone they trust)
    EN['refusal.headinajar.locker.not_yours_break'] = "That locker holds someone else's %s. Its owner can break it."
    PT['refusal.headinajar.locker.not_yours_break'] = 'Esse armário guarda %s de outra pessoa. Só o dono o quebra.'
    # Telekinesis: an owner this server has never seen by name
    EN['message.headinajar.another_player'] = 'Another player'
    PT['message.headinajar.another_player'] = 'Outro jogador'
    # a host's own movement is the rider's at once; its other traits open with sync
    EN['hud.headinajar.release5.moves_now'] = 'Moves as itself now; traits at %s%% sync'
    PT['hud.headinajar.release5.moves_now'] = 'Já anda como ele; traços com %s%% de sinc.'
    # a flier too high to get off: holding sneak flies it down, and lets the jar off once it is low
    EN['refusal.headinajar.host.too_high'] = 'Too high to get off %1$s. Hold %2$s to fly down and off.'
    PT['refusal.headinajar.host.too_high'] = 'Alto demais para sair de %1$s. Segure %2$s: desce e sai.'
    # the handbook's edition on its landing page, in the reader's language
    EN['book.headinajar.handbook.subtitle'] = 'Second Edition'
    PT['book.headinajar.handbook.subtitle'] = 'Segunda Edição'
    # goggles from a Curios head slot an older version offered: handed back, and the player told where
    EN['message.headinajar.curios_head.inventory'] = 'Goggles count only on the jar, so Curios no longer holds them: your %s are back in your inventory.'
    PT['message.headinajar.curios_head.inventory'] = 'Óculos só contam no pote, então o Curios não os guarda mais: seus %s voltaram ao inventário.'
    EN['message.headinajar.curios_head.ender_chest'] = 'Goggles count only on the jar, so Curios no longer holds them: your %s are in your Ender Chest (your inventory was full).'
    PT['message.headinajar.curios_head.ender_chest'] = 'Óculos só contam no pote, então o Curios não os guarda mais: seus %s estão no seu Baú do Ender (inventário cheio).'
    EN['message.headinajar.curios_head.floor'] = "Goggles count only on the jar, so Curios no longer holds them: your %s are at your feet, and won't despawn."
    PT['message.headinajar.curios_head.floor'] = 'Óculos só contam no pote, então o Curios não os guarda mais: seus %s estão aos seus pés e não somem.'

    # 0.5.2: the Employee Mount says how it hangs on a machine (the machine's own use comes first without sneak, as
    # with any block placed against a chest)
    EN['tooltip.headinajar.mount.how'] = 'Hang it on a machine: hold %s as you use it on the machine.'
    PT['tooltip.headinajar.mount.how'] = 'Pendure numa máquina: segure %s ao usá-lo na máquina.'
    EN['tooltip.headinajar.mount.staff'] = 'Then use it with a jarred villager, paid an emerald a day from a chest beside it.'
    PT['tooltip.headinajar.mount.staff'] = 'Depois use com um aldeão no pote, pago com uma esmeralda por dia de um baú ao lado.'

    # the Separation Chair's panel: the checklist and the wear line from x 74 to the panel's edge, the Sign button's
    # label inside the button, in both languages
    from gen_p27_clarity import width
    for lang in (EN, PT):
        for key in ('power', 'jar', 'goggles', 'collar', 'saline'):
            line = '- ' + lang['gui.headinajar.chair.need.' + key]
            assert width(line) <= 95, f'chair checklist line too wide ({width(line)} px): {line}'
        assert width(lang['gui.headinajar.chair.wear'].replace('%s', '100')) <= 95, 'chair wear line too wide: ' + lang['gui.headinajar.chair.wear']
        for key in ('sign', 'signing', 'sit'):
            assert width(lang['gui.headinajar.chair.' + key]) <= 88, 'chair Sign button label too wide: ' + lang['gui.headinajar.chair.' + key]

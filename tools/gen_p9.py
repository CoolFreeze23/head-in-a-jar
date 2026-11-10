"""Refusals (Form 40-R): every refusal line, and the sounds a refusal plays in sounds.json. Imported by gen_data.py.

A refusal line says what is wrong, then what to do next. Lines never name a physical key: a control is passed in by
the code as a keybind component. Arguments (%s) are filled in by the code, mostly with item names, so both languages
use the item's own name.
"""
import json
import os

# id (after 'refusal.headinajar.'): (en, pt)
REFUSALS = {
    # the Nudge: a bare jar reaching for something. %1$s is the Manipulator Arm, %2$s the Magnet
    'jar.no_arm.use': ('A jar has no hands. Fit a %1$s.', 'Pote não tem mãos. Instale um %1$s.'),
    'jar.no_arm.container': ('Nothing to open it with. Fit a %1$s.', 'Sem mãos, não dá para abrir. Instale um %1$s.'),
    'jar.no_arm.creature': ('Nothing to reach it with. Fit a %1$s.', 'Sem mãos, não dá para alcançar. Instale um %1$s.'),
    'jar.no_arm.attack': ('Glaring is not an attack. Fit a %1$s.', 'Encarar não é atacar. Instale um %1$s.'),
    'jar.no_arm.villager': ('Nobody trades with a jar. Fit a %1$s.', 'Ninguém negocia com um pote. Instale um %1$s.'),
    'jar.no_arm.mine': ("A jar can't dig. Fit a %1$s.", 'Pote não cava. Instale um %1$s.'),
    'jar.no_arm.pickup': ('No hands to pick that up. Fit a %1$s or a %2$s.', 'Sem mãos para pegar isso. Instale um %1$s ou um %2$s.'),
    # the Glug: a jar key with nothing behind it. %1$s the module, %2$s the jar menu key
    'jar.no_module': ('No %1$s fitted. Fit one from the jar menu (%2$s).', 'Nenhum %1$s instalado. Instale pelo menu do pote (%2$s).'),
    'jar.no_charge': ('The jar is out of charge. Recharge at a %s.', 'O pote está sem carga. Recarregue numa %s.'),
    'jar.laser_low': ('Not enough charge for the laser. Recharge at a %s.', 'Carga insuficiente para o laser. Recarregue numa %s.'),
    'jar.laser_cooling': ('The laser is still cooling. Give it a second.', 'O laser ainda está esfriando. Espere um segundo.'),
    # the Swap key
    'swap.no_body': ('No body on file. A %s can print you one.', 'Nenhum corpo registrado. Uma %s imprime um para você.'),
    'swap.corpse': ('Your body is deceased. Use a %s on it to get back in.', 'Seu corpo faleceu. Use um %s nele para voltar.'),
    'swap.no_collar': ('No %s on this jar. Use a %s on your body instead.', 'Este pote está sem %s. Use um %s no seu corpo.'),
    'swap.weak_signal': ('Signal at %s%%. Get closer to your body or a %s.', 'Sinal em %s%%. Chegue mais perto do corpo ou de um %s.'),
    'swap.unlink_disabled': ("This server doesn't allow swapping out. Walk back to your head.",
                             'Este servidor não deixa trocar. Volte a pé até sua cabeça.'),
    'swap.no_head': ("Your head isn't on file. An operator can sort it out.", 'Sua cabeça não está registrada. Um operador resolve.'),
    # mecha arms: one refusal per press. %s is the Assembly Gantry where it takes one
    'mecha.arm.empty': ('Nothing fitted on that arm. Fit one at the %s.', 'Nada instalado nesse braço. Instale um no %s.'),
    'mecha.arm.venting': ('Venting heat. The arms come back once it cools.', 'Expelindo calor. Os braços voltam quando esfriar.'),
    'mecha.arm.no_power': ('Battery flat. Recharge it with Repair at the %s.', 'Bateria descarregada. Recarregue com Reparar no %s.'),
    'mecha.arm.low_power': ('That arm needs %s kFE. Recharge at the %s.', 'Esse braço precisa de %s kFE. Recarregue no %s.'),
    'mecha.arm.cooling': ('That arm is still cycling. One moment.', 'Esse braço ainda está recarregando. Um instante.'),
    'mecha.arm.no_rivets': ('Out of rivets. Carry iron nuggets.', 'Sem rebites. Leve pepitas de ferro.'),
    # the Assembly Gantry. The five walking Legs modules, by name
    'gantry.no_legs': ("This frame can't walk. Fit %s, %s, %s, %s or %s.", 'Esta estrutura não anda. Instale %s, %s, %s, %s ou %s.'),
}

# The sounds the refusal code plays. Their files, ranges and subtitles come from the sound hand-off,
# tools/snd/events.json (written by tools/snd/build.py); these defaults are what that hand-off promises, so the data
# is complete before it lands. Volume and pitch ranges are applied per play by the code (feedback/Refusals.java).
# event: (files, attenuation distance, subtitle en, subtitle pt); subtitle None = a layer under another sound
SOUND_EVENTS = {
    'feedback.refused': ([f'headinajar:feedback/refused_{i}' for i in (1, 2, 3)], 8, 'Noggin & Sons declines', 'Noggin & Sons lamenta'),
    'feedback.done': ([f'headinajar:feedback/done_{i}' for i in (1, 2)], 8, 'Noggin & Sons approves', 'Noggin & Sons aprova'),
    'feedback.warning': ([f'headinajar:feedback/warning_{i}' for i in (1, 2)], 8, 'Management coughs', 'A gerência pigarreia'),
    'feedback.danger': (['headinajar:feedback/danger_1'], 48, 'Alarm sounds', 'Alarme dispara'),
    'jar.tink': ([f'headinajar:jar/tink_{i}' for i in (1, 2, 3)], 8, 'Jar taps', 'Pote dá batidinhas'),
    'jar.glug': ([f'headinajar:jar/glug_{i}' for i in (1, 2)], 8, 'Jar gurgles', 'Pote borbulha'),
    'jar.glug_texture': ([f'minecraft:block/bubble_column/bubble{i}' for i in (1, 2, 3)], 8, None, None),
}

HERE = os.path.dirname(__file__)
HANDOFF = os.path.join(HERE, 'snd', 'events.json')


def sound_events():
    """SOUND_EVENTS, updated from the hand-off file where it lists the same event."""
    events = {name: dict(files=files, attenuation=att, subtitle={'en_us': en, 'pt_br': pt} if en else None)
              for name, (files, att, en, pt) in SOUND_EVENTS.items()}
    if os.path.exists(HANDOFF):
        with open(HANDOFF, encoding='utf-8') as f:
            handoff = json.load(f)
        for e in handoff.get('events', []):
            name = e['id'].split(':', 1)[-1]
            if name not in events:
                continue
            files = e.get('files') or e.get('vanilla_files')
            if files:
                events[name]['files'] = files
            if 'attenuation' in e:
                events[name]['attenuation'] = e['attenuation']
            if 'subtitle' in e:
                events[name]['subtitle'] = e['subtitle']
    return events


def run(EN, PT, write, shaped):
    for key, (en, pt) in REFUSALS.items():
        EN[f'refusal.headinajar.{key}'], PT[f'refusal.headinajar.{key}'] = en, pt

    # sounds.json: the synthesised sounds (tools/make_sounds.py lists them) plus the refusal sounds
    import importlib.util
    spec = importlib.util.spec_from_file_location('make_sounds', os.path.join(HERE, 'make_sounds.py'))
    sounds = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(sounds)
    table = {name: {'sounds': [f'headinajar:{name}'], 'subtitle': f'subtitles.headinajar.{name}'} for name in sounds.SOUNDS}
    for name, event in sound_events().items():
        entry = {'sounds': [{'name': f, 'attenuation_distance': event['attenuation']} for f in event['files']]}
        if event['subtitle']:
            entry['subtitle'] = f'subtitles.headinajar.{name}'
            EN[entry['subtitle']], PT[entry['subtitle']] = event['subtitle']['en_us'], event['subtitle']['pt_br']
        table[name] = entry
    write('assets/headinajar/sounds.json', table)

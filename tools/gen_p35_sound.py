"""The sound families as one: the house sounds (the jingles, the desk bell, the nine first sounds remade under their old
names), a jar getting about, the link's static, the Neural Socket's switchboard, the machines' clip sounds that had no
event, the events of their own a borrowed sound now plays under, and a subtitle on every event. Imported by
gen_data.py, after every other generator that writes sounds.json.

Every file named below comes from a seeded recipe in tools/snd/ and was checked by measurement
(tools/snd/measure.py), never by ear.
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources')
SND_EVENTS = os.path.join(HERE, 'snd', 'events.json')

# events made by tools/snd/recipes_house.py, recipes_travel.py and recipes_switchboard.py: files, attenuation and
# subtitles from the hand-off. The first nine sounds' remakes (booth_cut ... staff_complain) replace the files of the
# same events, so nothing that plays them changes; the old files stay in the folder for any resource pack naming them.
FROM_HANDOFF = [
    'house.jingle', 'house.jingle_shutdown', 'house.jingle_botch', 'house.desk_ding', 'gantry.patch',
    'booth_cut', 'link_lost', 'link_restored', 'mecha_boot', 'gene_splice', 'gene_mutation', 'staff_complain',
    'jar.wheels_down', 'jar.wheels_up', 'jar.drive', 'jar.tip_over', 'jar.sit_up', 'jar.roll', 'jar.hop',
    'jar.hop_land', 'jar.propeller', 'jar.shock_lid',
    'link.static_faint', 'link.static', 'link.static_heavy',
    'socket.relay_up', 'socket.relay_down', 'socket.channel_on', 'socket.channel_off', 'socket.metronome',
    'dock.lever', 'dock.unclack', 'dock.tap', 'rig.cancel', 'rig.fill', 'rig.drain', 'leech.bite',
    'module.arc_cannon', 'module.tesla_coil',
]

# A sound another event (the mod's or vanilla's) already makes, played under an event of its own whose subtitle names
# what the player hears: event -> (the event it plays, subtitle en, subtitle pt). The sound is exactly what played
# before (and follows a resource pack that changes that event); only the subtitle is new.
SAME_SOUND = {
    # the company's own, under their right names
    'booth_oopsie': ('headinajar:house.jingle_botch', None, None),
    'array_murmur': ('headinajar:array.murmur', None, None),
    'memo.beep': ('headinajar:chair.beep', 'DOLORES sends a memo', 'DOLORES manda um memorando'),
    'body.reattach': ('headinajar:body.bayonet', 'Head goes back on', 'Cabeça volta ao lugar'),
    'body.embalm': ('headinajar:chair.hiss', 'Embalming spray hisses', 'Spray de embalsamar chia'),
    # vanilla's, under subtitles that say what this mod is doing
    'gantry.strip': ('minecraft:block.chain.break', 'Mecha taken apart', 'Mecha desmontado'),
    'socket.zombie_groan': ('minecraft:entity.zombie.ambient', 'Socketed zombie groans', 'Zumbi no soquete geme'),
    'body.flies': ('minecraft:entity.bee.loop', 'Flies buzz', 'Moscas zumbem'),
    'body.syringe_jab': ('minecraft:entity.bee.sting', 'Syringe jabs', 'Seringa espeta'),
    'body.snip': ('minecraft:entity.sheep.shear', 'Saw snips through', 'Serra corta rente'),
    'body.clothes': ('minecraft:item.armor.equip_leather', 'Clothes rustle', 'Roupas farfalham'),
    'body.slump': ('minecraft:block.wool.step', 'Body slumps', 'Corpo desaba'),
    'body.suture': ('minecraft:block.slime_block.place', 'Head sutured on', 'Cabeça suturada'),
    'body.detach': ('minecraft:block.slime_block.break', 'Head comes off', 'Cabeça se solta'),
    'body.lace': ('minecraft:block.slime_block.place', 'Host laced', 'Hospedeiro tratado'),
    'jar.knock': ('minecraft:block.glass.hit', 'Jar glass takes a knock', 'Vidro do pote leva pancada'),
    'jar.shatter': ('minecraft:block.glass.break', 'Jar shatters', 'Pote se estilhaça'),
    'jar.patch': ('minecraft:block.slime_block.place', 'Jar glass patched', 'Vidro do pote remendado'),
    'jar.bagged': ('minecraft:item.armor.equip_leather', 'Head bagged', 'Cabeça ensacada'),
    'jar.goggles': ('minecraft:item.armor.equip_leather', 'Goggles fitted', 'Óculos ajustados'),
    'jar.plug_in': ('minecraft:block.slime_block.place', 'Jar plugs in', 'Pote se conecta'),
    'gene.accepted': ('minecraft:block.brewing_stand.brew', 'Serum takes', 'Soro pega'),
    'gene.rejected': ('minecraft:entity.slime.hurt', 'Body rejects serum', 'Corpo rejeita o soro'),
    'gene.regrowth': ('minecraft:item.totem.use', 'Body regrows', 'Corpo se regenera'),
    'gene.frost': ('minecraft:block.powder_snow.step', 'Frost spreads', 'Geada se espalha'),
    'centrifuge.settle': ('minecraft:block.brewing_stand.brew', 'Sample settles', 'Amostra decanta'),
    'sequencer.read': ('minecraft:ui.cartography_table.take_result', 'Sequence printed', 'Sequência impressa'),
    'courier.booked': ('minecraft:ui.cartography_table.take_result', 'Courier booked', 'Entregador agendado'),
    'turret.dry_click': ('minecraft:block.dispenser.fail', 'Turret clicks: empty', 'Torreta estala: vazia'),
    'turret.thump': ('minecraft:entity.zombie.attack_wooden_door', 'Visitor headbutted', 'Visitante leva cabeçada'),
    'turret.fit': ('minecraft:item.armor.equip_iron', 'Head mounted', 'Cabeça montada'),
    'dock.charging': ('minecraft:block.beacon.power_select', 'Dock starts charging', 'Doca começa a carregar'),
    'emp.burst': ('minecraft:block.beacon.deactivate', 'EMP discharges', 'PEM descarrega'),
    'emp.throw': ('minecraft:entity.snowball.throw', 'EMP grenade lobbed', 'Granada PEM arremessada'),
    'chair.new_blade': ('minecraft:block.smithing_table.use', 'Blade replaced', 'Lâmina trocada'),
    'chair.pipe_away': ('minecraft:block.piston.contract', 'Jar piped to Cradle', 'Pote enviado ao Berço'),
    'collar.install': ('minecraft:block.smithing_table.use', 'Collar upgrade fitted', 'Melhoria da coleira instalada'),
    'quantum.entangle': ('minecraft:block.amethyst_block.resonate', 'Shards entangle', 'Fragmentos se entrelaçam'),
    'printer.squelch': ('minecraft:entity.slime.squish', 'Print gel squelches', 'Gel de impressão chapinha'),
    'mount.work': ('minecraft:entity.villager.work_librarian', 'Employee files paperwork', 'Funcionário arquiva papéis'),
    # the mecha line's
    'mecha.fist_strike': ('minecraft:entity.iron_golem.attack', 'Mecha fist strikes', 'Punho do mecha golpeia'),
    'module.grapple_fire': ('minecraft:entity.fishing_bobber.throw', 'Grapple fires', 'Gancho dispara'),
    'module.rivet_fire': ('minecraft:item.crossbow.shoot', 'Rivet gun fires', 'Rebitadeira dispara'),
    'module.chainsaw': ('minecraft:item.axe.strip', 'Chainsaw bites', 'Motosserra morde'),
    'mecha.jets': ('minecraft:item.firecharge.use', 'Jump jets fire', 'Jatos de salto disparam'),
    'mecha.vent_steam': ('minecraft:block.lava.extinguish', 'Vents steam', 'Respiros soltam vapor'),
    'module.medical': ('minecraft:block.brewing_stand.brew', 'Med injector bubbles', 'Injetor médico borbulha'),
    'module.mortar': ('minecraft:entity.firework_rocket.launch', 'Smoke mortar thumps', 'Morteiro de fumaça dispara'),
    'module.missiles': ('minecraft:entity.firework_rocket.launch', 'Missiles launch', 'Mísseis disparam'),
    'module.ore_scan': ('minecraft:block.beacon.activate', 'Ore scanner pings', 'Scanner de minério apita'),
    'mecha.cargo_open': ('minecraft:block.iron_trapdoor.open', 'Cargo hold opens', 'Porão de carga abre'),
    'mecha.shell_crash': ('minecraft:entity.iron_golem.damage', 'Shell crumples', 'Casco amassa'),
    'mecha.line_shot': ('minecraft:item.crossbow.shoot', 'Line gun fires', 'Lançador de cabo dispara'),
    'mecha.line_anchor': ('minecraft:block.chain.place', 'Line catches', 'Cabo prende'),
    'mecha.line_snip': ('minecraft:entity.sheep.shear', 'Line cut', 'Cabo cortado'),
}

# Events that had no subtitle: their own line, or (a layer under another sound on the same tick, or a part of a
# sequence another event names) that event's line, which the subtitle list shows once.
SUBTITLES = {
    'body.snick': ('Suture stitches', 'Sutura dá um ponto'),
    'chair.drawer_stop': ('Jar tray stops', 'Bandeja do pote para'),
    'chair.plonk': ('Head plonks down', 'Cabeça cai com um baque'),
    'chair.servo_tick': ('Scanner ticks', 'Scanner tiquetaqueia'),
    'chair.tap': ('Barrier taps, waiting', 'Barreira bate, à espera'),
    'cradle.blanket_whoosh': ('Blanket swishes', 'Cobertor farfalha'),
    'cradle.hose_swing': ('Hose swings over', 'Mangueira balança'),
    'dock.spark': ('Dock sparks', 'Doca solta faísca'),
    'locker.portrait_off': ('Card flips to VACANT', 'Cartão vira para VAGO'),
    'locker.portrait_on': ('Portrait slides in', 'Retrato entra no quadro'),
    'machine.drawer_stop': ('Drawer stops', 'Gaveta para'),
    'mecha.brocade_breathe': ('Parachute breathes', 'Paraquedas respira'),
    'mecha.brocade_rustle': ('Parachute rustles', 'Paraquedas farfalha'),
    'mecha.bulwark.grip_clack': ('Grip clacks', 'Garra estala'),
    # borrowed by other frames and the shells, so a line of their own rather than the sequence's
    'mecha.bar_clank': ('Bar clanks home', 'Barra bate no batente'),
    'mecha.servo_sag': ('Servo sags', 'Servo cede'),
    'mecha.bulwark.servo_sag': ('Servo sags', 'Servo cede'),
    'mecha.wheelhouse.buffer_spring': ('Spring buffers', 'Mola amortece'),
    'mecha.bulwark.motor': ('Bulwark motor hums', 'Motor do Bulwark zumbe'),
    'mecha.bulwark.rattle': ('Plating rattles', 'Blindagem chacoalha'),
    'mecha.bulwark.servo_settle': ('Servo settles', 'Servo se acomoda'),
    'mecha.cords_slack': ('Cords go slack', 'Cordas afrouxam'),
    'mecha.motor': ('Mecha motor hums', 'Motor do mecha zumbe'),
    'mecha.rattle': ('Plating rattles', 'Blindagem chacoalha'),
    'mecha.relay_tick': ('Relay ticks', 'Relé estala'),
    'mecha.servo_settle': ('Servo settles', 'Servo se acomoda'),
    'mecha.servo_tick': ('Servo ticks', 'Servo tiquetaqueia'),
    'mecha.standby_blip': ('Mecha blips on standby', 'Mecha pisca em espera'),
    'mecha.tap': ('Mecha foot taps', 'Pé do mecha bate'),
    'mecha.wheelhouse.cooling_tick': ('Engine ticks, cooling', 'Motor estala, esfriando'),
    'mecha.wheelhouse.flap_clank': ('Exhaust flap clanks', 'Aba do escape bate'),
    'mecha.wheelhouse.motor': ('Wheelhouse engine runs', 'Motor da Wheelhouse roda'),
    'mecha.wheelhouse.ram_stroke': ('Buffer ram strokes', 'Aríete do para-choque avança'),
    'mecha.wheelhouse.steer_servo': ('Steering servo whirs', 'Servo da direção zumbe'),
    'mecha.wheelhouse.suspension_creak': ('Springs creak', 'Molas rangem'),
    'mount.stamp_lift': ('Stamp lifts', 'Carimbo se ergue'),
    'printer.door_swing': ('Booth door swings', 'Porta da cabine balança'),
    'rig.prime': ('Syringe cocks', 'Seringa engatilha'),
    'socket.drop': ('Socket clamps settle', 'Garras do soquete assentam'),
    'socket.servo': ('Socket clamps climb', 'Garras do soquete sobem'),
    'tesla.look': ('Tesla node looks round', 'Nó de Tesla olha em volta'),
    'tesla.swing': ('Tesla ball swings', 'Bola de Tesla balança'),
    'turret.servo': ('Turret servo tracks', 'Servo da torreta segue'),
}
# The mecha line's alias pattern (HopperSounds.ALIASES): a clip sound with no take of its own plays another's files
# under an event of its own, because the take it borrowed was subtitled for another machine's part.
MECHA_ALIASES = {
    'spider_walker.tin_scrape': ('wheelhouse.tyre_scrub', 'Tin feet scrape', 'Pés de lata raspam'),
    'spider_walker.tin_clatter': ('wheelhouse.wheel_clatter', 'Tin legs clatter', 'Pernas de lata chacoalham'),
    'spider_walker.tin_creak': ('wheelhouse.suspension_creak', 'Tin body creaks', 'Corpo de lata range'),
    'spider_walker.key_rundown': ('servo_fail', 'Clockwork runs down', 'A corda acaba'),
    'spider_walker.nozzle_tap': ('tap', 'Web nozzle taps', 'Bico de teia bate'),
    'rover.drawer_knock': ('vent_clank', 'Drawer knocks', 'Gaveta bate'),
    'rover.drawer_slam': ('bar_clank', 'Drawer slams', 'Gaveta se fecha com força'),
    'rover.drawer_slide': ('servo', 'Drawer slides', 'Gaveta desliza'),
    'rover.motor_rev': ('servo_chord', 'Rover motor revs', 'Motor do Rover acelera'),
    'rover.jar_hop': ('servo_tick', 'Jar hops aboard', 'Pote pula a bordo'),
    'rover.castor_squeak': ('servo_tick', 'Castor squeaks', 'Rodízio chia'),
    'rover.strap_slap': ('rattle', 'Strap slaps', 'Correia estala'),
    'skitter.line_creak': ('wheelhouse.suspension_creak', 'Line creaks', 'Cabo range'),
}

SAME_LINE = {
    'jar.glug_texture': 'jar.glug',
    'machine.rattle': 'feedback.refused', 'locker.ticket_rattle': 'feedback.refused', 'mount.bell_ting': 'feedback.refused',
    'chair.barrier_settle': 'chair.barrier_latch', 'splicer.seat': 'splicer.thread',
    'mecha.bulwark.whoosh': 'mecha.bulwark.throw_release', 'mecha.whoosh': 'mecha.pogo_launch',
    'mecha.cords_creak': 'mecha.brocade_flare', 'mecha.pilot_whoosh': 'mecha.envelope_pop',
    'mecha.wheelhouse.exhaust_pop': 'mecha.wheelhouse.rush_wind_down',
    'mecha.wheelhouse.flag_flop': 'mecha.wheelhouse.rush_refused', 'mecha.wheelhouse.ring_clank': 'mecha.wheelhouse.ring_blow',
}


def run(EN, PT, write, shaped):
    with open(SND_EVENTS, encoding='utf-8') as f:
        events = {e['id'].split(':', 1)[1]: e for e in json.load(f)['events']}
    path = os.path.join(RES, 'assets', 'headinajar', 'sounds.json')
    with open(path, encoding='utf-8') as f:
        table = json.load(f)
    for name in FROM_HANDOFF:
        e = events[name]
        entry = {'sounds': [{'name': f, 'attenuation_distance': e['attenuation']} for f in e['files']]}
        if e.get('subtitle'):
            entry['subtitle'] = f'subtitles.headinajar.{name}'
            EN[entry['subtitle']], PT[entry['subtitle']] = e['subtitle']['en_us'], e['subtitle']['pt_br']
        table[name] = entry
    for name, (of, en, pt) in SAME_SOUND.items():
        key = f'subtitles.headinajar.{name}'
        if en is None:
            assert key in EN, name  # keeps the line it always had
        else:
            assert len(en) <= 24 and len(pt) <= 30, name
            EN[key], PT[key] = en, pt
        table[name] = {'sounds': [{'name': of, 'type': 'event'}], 'subtitle': key}
    for name, (en, pt) in SUBTITLES.items():
        assert name in table and 'subtitle' not in table[name], name
        assert len(en) <= 24 and len(pt) <= 30, name
        key = f'subtitles.headinajar.{name}'
        EN[key], PT[key] = en, pt
        table[name]['subtitle'] = key
    for name, (of, en, pt) in MECHA_ALIASES.items():
        assert len(en) <= 24 and len(pt) <= 30, name
        key = f'subtitles.headinajar.mecha.{name}'
        EN[key], PT[key] = en, pt
        table['mecha.' + name] = {'sounds': [{'name': f'headinajar:mecha.{of}', 'type': 'event'}], 'subtitle': key}
    for name, carrier in SAME_LINE.items():
        assert name in table and 'subtitle' not in table[name], name
        key = table[carrier]['subtitle']
        assert key in EN, carrier
        table[name]['subtitle'] = key
    missing = [k for k, v in table.items() if 'subtitle' not in v]
    assert not missing, ('events with no subtitle', missing)
    write('assets/headinajar/sounds.json', table)

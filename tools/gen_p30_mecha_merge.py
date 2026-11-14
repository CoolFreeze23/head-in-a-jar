"""Lines for the whole mecha line together: what holds across every frame and shell. Imported by gen_data.py.

- the load lock said before assembly (the Gantry's mass line and its tooltip), as the machine is handed over, and on
  the pilot's HUD with what to do;
- the shoulder weapons' line on the HUD says they fire with the arm below (one button a side) outside Siege;
- the Bio-Scanner's reading of what the crosshair is on;
- borrowed takes whose own subtitle named the wrong thing (a pogo spring on the Skitter's line gun, an engine on a web
  nozzle, the Wheelhouse reversing on a Rover) get events of their own in sounds.json: the same files, a plain subtitle
  of their own (ALIASES, kept in step with HopperSounds.ALIASES).
"""
import json
import os

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources')

LINES = {
    'hud.headinajar.servos_locked_why': ('OVERLOADED: servos locked. Lighten it at the %s', 'SOBRECARGA: servos travados. Alivie no %s'),
    'hud.headinajar.shoulders_with_arms': ('Shoulders, with the arm below  L: %s   R: %s', 'Ombros, com o braço de baixo  E: %s   D: %s'),
    'hud.headinajar.bio_scan': ('%s: %s / %s health, %s', '%s: %s / %s de vida, %s'),
    'gui.headinajar.gantry.mass_locked': ('%s/%s kg: locked', '%s/%s kg: travado'),
    'gui.headinajar.gantry.locked_tip': ('Over %s%% of its rating the servos lock: it stands, but it will not move. Take a module out.',
                                         'Acima de %s%% da carga os servos travam: fica de pé, mas não anda. Tire um módulo.'),
    'warning.headinajar.gantry.over_lock': ('%s/%s kg, too heavy to move. Remove a module.',
                                            '%s/%s kg: pesado demais para andar. Tire um módulo.'),
}

# alias: (the event whose files it plays, English subtitle, Portuguese subtitle)
ALIASES = {
    'skitter.line_charge': ('pogo_charge', 'Line gun charges', 'Lançador de cabo carrega'),
    'skitter.reel': ('pogo_ratchet', 'Reel ratchets', 'Carretel estala'),
    'skitter.line_fire': ('pogo_launch', 'Line gun fires', 'Lançador de cabo dispara'),
    'skitter.line_cough': ('wheelhouse.engine_misfire', 'Line gun coughs', 'Lançador de cabo engasga'),
    'skitter.line_cut': ('bar_unlatch', 'Line cut', 'Cabo cortado'),
    'spider_walker.key_wind': ('pogo_ratchet', 'Clockwork key winds', 'Chave de corda gira'),
    'spider_walker.gland_squeeze': ('pogo_charge', 'Web gland squeezes', 'Glândula de teia aperta'),
    'spider_walker.nozzle_cough': ('wheelhouse.engine_misfire', 'Web nozzle coughs', 'Bico de teia engasga'),
    'rover.reverse_beep': ('wheelhouse.reverse_beep', 'Rover reverses', 'Rover dá ré'),
    'rover.bell': ('ding', 'Rover bell dings', 'Sino do Rover toca'),
}


def run(EN, PT, write, shaped):
    for key, (en, pt) in LINES.items():
        EN[key], PT[key] = en, pt
    path = os.path.join(RES, 'assets', 'headinajar', 'sounds.json')
    with open(path, encoding='utf-8') as f:
        table = json.load(f)
    for alias, (source, en, pt) in ALIASES.items():
        assert len(en) <= 24 and len(pt) <= 30, alias
        entry = dict(table['mecha.' + source])
        key = 'subtitles.headinajar.mecha.' + alias
        entry['subtitle'] = key
        table['mecha.' + alias] = entry
        EN[key], PT[key] = en, pt
    write('assets/headinajar/sounds.json', table)

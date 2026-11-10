"""Choose one take per DOLORES line, fit the operation's voiced lines to the chair's ticks, and deliver.

python -I fit_set.py            (system Python; after gen_set_vox.py, set_treat.py and set_asr.py)

The voice: VoxCPM2, a voice designed from a written description, through treat.py's 'ringmod' chain.
- CHOICE: the variant spoken per line and language (see set_script.json).
- Voiced during the operation: start, gas, scan (it must end before the latch at 100) and jar (started during the head's
  transfer, 152 on, and ending by 205); then done or oopsie after 240. The cut and privacy lines are text only during
  the operation (nothing may play over 114-120); their files stay in the set.
- start, gas and scan are chained: each starts at its trigger, or when the line before it has ended (heard length,
  -40 dB, rounded up to whole ticks), whichever is later. No line overlaps the next line's start.
- Writes the game files into the mod, lines.json for the game data generator, and a preview MP3 of the operation's voiced lines.
"""
import os, json, math, os, shutil, subprocess, sys
import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import treat as T  # noqa: E402

N = T.N
ROOT = os.environ['VOICE_WORK']
SETDIR = 'set_vox'
WT = os.environ.get('MOD_ROOT', os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
SOUNDS = WT + r'\src\main\resources\assets\headinajar\sounds\voice'
TOOLS = WT + r'\tools\voice'
PREVIEW_MP3 = os.path.join(os.environ['VOICE_WORK'], 'dolores_operation.mp3')
MUFFLE_FC = 1400   # 2nd-order low-pass for the patient's muffled copies
GAP = 0            # ticks of air between one line's heard end and the next line's start
OP_END = 240
SCAN_END_MAX = 100          # the scan line must end before the latch at 100
JAR_WINDOW = (152, 205)     # the jar line starts during the head's transfer (rail up at 152) and ends by about 205
JAR_PREFERRED_START = 161   # the measured quiet stretch of the transfer (161-169) when the line is short enough

CHOICE = {  # id: {lang: variant}
    'start': {'en': 'alt0', 'pt': 'alt1'},
    'gas': {'en': 'short', 'pt': 'short'},
    'scan': {'en': 'short', 'pt': 'short'},
    'cut': {'en': 'main', 'pt': 'main'},
    'privacy': {'en': 'alt0', 'pt': 'alt0'},
    'jar': {'en': 'short', 'pt': 'alt0'},
    'done': {'en': 'main', 'pt': 'main'},
    'oopsie': {'en': 'main', 'pt': 'alt0'},
    'refuse_goggles': {'en': 'main', 'pt': 'alt0'},
    'refuse_saline': {'en': 'main', 'pt': 'alt0'},
    'refuse_collar': {'en': 'main', 'pt': 'alt0'},
}
FIXED_OFFSET = {
    'done': 24,    # lets the level-up chime at 240 (vanilla, volume 0.6, 1.17 s heard) and the hand-over land first
    'oopsie': 34,  # booth_oopsie (1.7 s heard, M max -12) first, then her verdict
}
CHAIN = ['start', 'gas', 'scan']
VOICED_OP = CHAIN + ['jar']
TEXT_ONLY_IN_OP = ['cut', 'privacy']

# chair effects by operation tick (event names and keys of the re-cut chair sounds), and how far each ducks (dB) when
# it starts while DOLORES speaks from the same chair. Ducking rule: 6 dB for the servos, hydraulics, hood nod, gas hiss,
# scan ticks, saline glugs, sparks and drawers; never for the cut, the latch at 100, the barrier latch, the jaws' clamp
# and the ding (they carry the story).
EFFECTS = [
    (0, 'chair.servo_barrier', -6), (3, 'chair.barrier_drop', 0), (11, 'chair.barrier_latch', 0),
    (17, 'chair.barrier_settle', 0), (21, 'chair.hood_nod', -6), (30, 'chair.gas_hiss', -6),
    (40, 'chair.beep', 0), (47, 'chair.beep', 0), (54, 'chair.beep', 0), (56, 'chair.hood_nod', -6),
    (60, 'chair.scan_chirp', -6), (68, 'chair.servo_tick', -6), (74, 'chair.servo_tick', -6),
    (80, 'chair.servo_tick', -6), (86, 'chair.servo_tick', -6), (94, 'chair.beep', 0), (100, 'chair.latch', 0),
    (104, 'chair.whine_rise', 0), (107, 'chair.curtain', 0), (120, 'chair.cut', 0), (122, 'chair.plonk', 0),
    (128, 'chair.ding', 0), (140, 'chair.saline_glug', -6), (141, 'chair.lid_pop', 0), (148, 'chair.clamp', 0),
    (152, 'chair.hydraulic', -6), (166, 'chair.servo', -6), (174, 'chair.servo_hiccup', -6),
    (177, 'chair.oopsie_squeak', 0), (184, 'chair.hydraulic', -6), (198, 'chair.plop', 0),
    (199, 'chair.saline_glug', -6), (204, 'chair.hydraulic_short', -6), (205, 'chair.saline_glug', -6),
    (212, 'chair.servo_tick', -6), (214, 'chair.handshake', 0), (215, 'chair.clink', 0), (218, 'chair.servo_home', -6),
    (221, 'chair.spark', -6), (222, 'chair.curtain', 0), (228, 'chair.ratchet', 0), (228, 'chair.spark', -6),
    (230, 'chair.ratchet', 0), (230, 'chair.ding', 0), (232, 'chair.ratchet', 0), (234, 'chair.unlatch', 0),
    (235, 'chair.spark', -6), (236, 'chair.barrier_lift', 0),
    (240, 'minecraft:entity.player.levelup', 0), (240, 'headinajar:booth_oopsie', 0),
    (241, 'chair.hiss (aftercare)', -6), (254, 'chair.bow_chime (aftercare)', -6),
]
# measured room on the bystander soundtrack: windows where a line has no room
NO_ROOM = [(0, 20, 'restraint (the barrier latch at 13 is -22)'), (100, 114, 'cut wind-up'),
           (114, 120, 'the silence before the strike, which must stay silent'), (120, 140, 'the cut and the ding'),
           (140, 152, 'lid pop and jaws (-20 to -24)'), (200, 240, 'lid, collar and handshake, the loudest stretch')]
SUBTITLE = {
    'speaks': ('subtitles.headinajar.dolores.speaks', 'DOLORES speaks', 'DOLORES fala'),
    'refuses': ('subtitles.headinajar.dolores.refuses', 'DOLORES refuses', 'DOLORES recusa'),
    'apologises': ('subtitles.headinajar.dolores.apologises', 'DOLORES apologises', 'DOLORES se desculpa'),
}


def subtitle_of(lid):
    if lid.startswith(('refuse_', 'chair_')):
        return 'refuses'
    if lid == 'oopsie':
        return 'apologises'
    return 'speaks'


def text_of(L, lang, var):
    if var == 'main':
        return L[lang]
    if var == 'short':
        return L['short'][lang]
    return L['alt'][lang][int(var[3:])]


def heard(path):
    """Heard length: end of the last 10 ms frame within 40 dB of the loudest (measure.py's eff_len)."""
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', path, '-ac', '1', '-f', 'f32le', '-'], capture_output=True,
                         check=True).stdout
    x = np.frombuffer(raw, '<f4').astype(np.float64)
    h = 441; m = len(x) // h
    e = np.sqrt((x[:m * h].reshape(m, h) ** 2).mean(1))
    above = np.where(e > e.max() * 10 ** (-40 / 20))[0]
    return (above[-1] + 1) * h / T.SR, len(x) / T.SR, x


def place(s, trig, start):
    s['offset_ticks'] = start - trig
    s['start_tick'] = start
    s['end_tick'] = start + s['heard_ticks']


def main():
    script = {L['id']: L for L in json.load(open(os.path.join(os.path.dirname(__file__), 'set_script.json'),
                                                   encoding='utf-8'))['lines']}
    wer = {(r['lang'], r['id'], r['variant'], r['muffled']): r for r in
           json.load(open(os.path.join(ROOT, SETDIR, 'wer.json'), encoding='utf-8'))['rows']}
    lines = {}
    for lid, L in script.items():
        e = dict(id=lid, key=L['key'], trigger=L['trigger'], trigger_tick=L['tick'], muffled=bool(L.get('muffled')),
                 voiced_in_operation=(lid not in TEXT_ONLY_IN_OP))
        for lang in ('en', 'pt'):
            var = CHOICE.get(lid, {}).get(lang, 'main')
            src = os.path.join(ROOT, SETDIR, lang, f'{lid}__{var}')
            hl, dur, _ = heard(src + '.ogg')
            e[lang] = dict(variant=var, spoken=text_of(L, lang, var), src=src, heard_s=round(hl, 3), file_s=round(dur, 3),
                           heard_ticks=math.ceil(hl * 20), wer=wer[(lang, lid, var, None)]['wer'],
                           asr_heard=wer[(lang, lid, var, None)]['heard'])
            if e['muffled']:
                w = wer[(lang, lid, var, MUFFLE_FC)]
                e[lang]['muffled_wer'] = w['wer']; e[lang]['muffled_asr_heard'] = w['heard']
        lines[lid] = e
    for lang in ('en', 'pt'):
        prev_end = None
        for i, lid in enumerate(CHAIN):
            s, trig = lines[lid][lang], lines[lid]['trigger_tick']
            place(s, trig, trig if prev_end is None else max(trig, prev_end + GAP))
            prev_end = s['end_tick']
        for i, lid in enumerate(CHAIN):
            s = lines[lid][lang]
            nxt = lines[CHAIN[i + 1]][lang]['start_tick'] if i + 1 < len(CHAIN) else SCAN_END_MAX
            cue = (lines[CHAIN[i + 1]]['trigger_tick'] if i + 1 < len(CHAIN) else SCAN_END_MAX) - lines[lid]['trigger_tick']
            s['window'] = f"{s['start_tick']}-{nxt}" + (' (ends before the latch at 100)' if lid == 'scan' else '')
            s['fits'] = s['end_tick'] <= nxt
            s['air_after_ticks'] = nxt - s['end_tick']
            s['cue_gap_ticks'] = cue
        s, trig = lines['jar'][lang], lines['jar']['trigger_tick']
        a, b = JAR_WINDOW
        place(s, trig, JAR_PREFERRED_START if JAR_PREFERRED_START + s['heard_ticks'] <= b else max(a, b - s['heard_ticks']))
        s['window'] = f'{a}-{b} (during the head\'s transfer, ending by {b})'
        s['fits'] = s['start_tick'] >= a and s['end_tick'] <= b
        for lid, off in FIXED_OFFSET.items():
            s = lines[lid][lang]
            place(s, OP_END, OP_END + off)
            s['fits'] = True
            s['window'] = f'after {OP_END}'
        for lid in TEXT_ONLY_IN_OP:
            s = lines[lid][lang]
            s['offset_ticks'] = s['start_tick'] = s['end_tick'] = None
            s['fits'] = None
            s['window'] = 'text only during the operation (nothing may play over 114-120)'
        for lid, e in lines.items():
            s = e[lang]
            s.setdefault('offset_ticks', 0)
            if s.get('start_tick') is None:
                s['duck'] = []
                s['overlaps_without_duck'] = []
                s['over_no_room'] = []
                continue
            s['duck'] = [dict(tick=t, event=ev, db=db) for t, ev, db in EFFECTS if s['start_tick'] <= t < s['end_tick'] and db < 0]
            s['overlaps_without_duck'] = [f'{ev}@{t}' for t, ev, db in EFFECTS if s['start_tick'] <= t < s['end_tick'] and db == 0]
            s['over_no_room'] = [f'{a}-{b} {why}' for a, b, why in NO_ROOM
                                 if s['start_tick'] < b and a < s['end_tick'] and s['start_tick'] < OP_END]
    # deliver the game files
    for lang in ('en', 'pt'):
        os.makedirs(os.path.join(SOUNDS, lang), exist_ok=True)
    for lid, e in lines.items():
        for lang in ('en', 'pt'):
            src = e[lang]['src']
            dst = os.path.join(SOUNDS, lang, f'{lid}.ogg')
            shutil.copyfile(src + '.ogg', dst)
            e[lang]['file'] = f'assets/headinajar/sounds/voice/{lang}/{lid}.ogg'
            e[lang]['sound_id'] = f'headinajar:voice/{lang}/{lid}'
            e[lang]['event'] = f'headinajar:dolores.{lang}.{lid}'
            e[lang]['bytes'] = os.path.getsize(dst)
            if e['muffled']:
                mdst = os.path.join(SOUNDS, lang, f'{lid}_muffled.ogg')
                shutil.copyfile(f'{src}_muffled{MUFFLE_FC}.ogg', mdst)
                e[lang]['file_muffled'] = f'assets/headinajar/sounds/voice/{lang}/{lid}_muffled.ogg'
                e[lang]['sound_id_muffled'] = f'headinajar:voice/{lang}/{lid}_muffled'
                e[lang]['event_muffled'] = f'headinajar:dolores.{lang}.{lid}.muffled'
                e[lang]['bytes_muffled'] = os.path.getsize(mdst)
            else:
                e[lang]['file_muffled'] = e[lang]['sound_id_muffled'] = e[lang]['event_muffled'] = None
            del e[lang]['src']
        k = subtitle_of(lid)
        e['subtitle'] = dict(key=SUBTITLE[k][0], en_us=SUBTITLE[k][1], pt_br=SUBTITLE[k][2])
    totals = {lang: dict(lines=len(lines), heard_s=round(sum(e[lang]['heard_s'] for e in lines.values()), 1),
                         bytes=sum(e[lang]['bytes'] + e[lang].get('bytes_muffled', 0) for e in lines.values()))
              for lang in ('en', 'pt')}
    doc = dict(
        about='DOLORES, the Separation Chair\'s assistant: machine-generated speech (VoxCPM2, a voice designed from a '
              'written description, through a 30 Hz ring modulator and a small-speaker chain; see README.md). One entry per '
              'line; ticks are the chair operation\'s (ChairTimeline).',
        requirements=[
            'The client picks the English or the Portuguese event from its own language setting: '
            'Minecraft.getInstance().getLanguageManager().getSelected() starting with "pt" plays the pt event, anything else '
            'the en event. The server never chooses; it sends which line (and the chair) to every player tracking the chair.',
            'Sound category VOICE (the Voice/Speech slider), so players can turn DOLORES down on her own.',
            'Positional at the chair block centre, mono, volume 1.0, pitch 1.0 (never randomised), linear attenuation, '
            'range 16 blocks.',
            'Voiced during the operation: start, gas, scan, jar, then done or oopsie. cut and privacy have '
            'voiced_in_operation false: text only during the operation (their files are kept, unused).',
            'Each language has its own spacing: start tick = trigger tick + en.offset_ticks or pt.offset_ticks (the '
            'language the client plays). A line never overlaps the next line\'s start; a new DOLORES line from the same '
            'chair stops the one still playing.',
            'The patient hears the *_muffled event for voiced lines marked muffled (gas, scan, jar); everyone else hears '
            'the clear event.',
            'Ducking: a chair effect from the same chair that starts inside a line\'s window plays at the listed dB '
            '(volume x 10^(dB/20)) for that client; the cut, the latch at 100, the barrier latch, the clamp and the ding '
            'never duck.',
            'Refusal and blade lines: at most one per chair every 60 ticks, and the same refusal at most once per sitting.',
            'Off switch: a client config doloresVoice (default true); off means the payload is ignored and the text stays.',
            'Subtitles: register the subtitle key on each event; the action-bar line keeps the full written text.',
        ],
        category='voice', range_blocks=16, volume=1.0, pitch=1.0, stream=False,
        loudness='every clear file M max -24 (vanilla villager idle1 -24.1); muffled files 6 dB under (-30); peak <= -1 dBFS',
        muffle=dict(lowpass_hz=MUFFLE_FC, order=2, gain_db=-6.0),
        room=dict(source='the chair\'s own sounds measured per tick as a bystander hears them (short-term loudness, 400 ms)',
                  conflicts={f"{lid}.{lang}": lines[lid][lang]['over_no_room'] for lid in VOICED_OP for lang in ('en', 'pt')
                             if lines[lid][lang].get('over_no_room')}),
        totals=totals, lines=list(lines.values()))
    os.makedirs(TOOLS, exist_ok=True)
    json.dump(doc, open(os.path.join(TOOLS, 'lines.json'), 'w', encoding='utf-8'), indent=1, ensure_ascii=False)
    # preview: the operation's voiced lines at their real spacing, English then Portuguese, and the botch ending
    seq = []
    for lang in ('en', 'pt'):
        tl = np.zeros(int((OP_END + 120) / 20 * T.SR))
        for lid in VOICED_OP + ['done']:
            _, _, x = heard(os.path.join(SOUNDS, lang, f'{lid}.ogg'))
            N.place(tl, x, lines[lid][lang]['start_tick'] / 20)
        end = (lines['done'][lang]['end_tick'] + 30) / 20
        seq.append(tl[:int(end * T.SR)])
        bt = np.zeros(int(4 * T.SR))
        _, _, x = heard(os.path.join(SOUNDS, lang, 'oopsie.ogg'))
        N.place(bt, x, 1.0 + lines['oopsie'][lang]['offset_ticks'] / 20)
        seq += [bt, np.zeros(int(2 * T.SR))]
    y = np.concatenate(seq)
    wav = PREVIEW_MP3[:-4] + '.wav'
    N.write_wav(wav, y)
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-ac', '1', '-c:a', 'libmp3lame', '-q:a', '4',
                    '-map_metadata', '-1', '-fflags', '+bitexact', PREVIEW_MP3], check=True)
    os.remove(wav)
    for lang in ('en', 'pt'):
        for lid in VOICED_OP + ['done', 'oopsie']:
            s = lines[lid][lang]
            print(f"{lang} {lid:8} trig {lines[lid]['trigger_tick']:>3} +{s['offset_ticks']:<3} {s['start_tick']:>3}-{s['end_tick']:<3} "
                  f"len {s['heard_ticks']:>3} window {s['window']} fits {s['fits']} wer {s['wer']} "
                  f"muff {s.get('muffled_wer', '-')} {s['spoken']!r}")
    print(json.dumps(totals))


if __name__ == '__main__':
    main()

"""Synthesises Head Office's sound effects with ffmpeg (no samples, all generated from formulas) into
src/main/resources/assets/headinajar/sounds/*.ogg, and writes sounds.json. Run: python tools/make_sounds.py
One or more by name, sounds.json untouched: python tools/make_sounds.py time_clock/bell head/warden_shush
The workforce's seven: python tools/make_sounds.py work"""
import json
import os
import shutil
import subprocess

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'headinajar', 'sounds')
FFMPEG = shutil.which('ffmpeg') or os.path.expandvars(
    r'%LOCALAPPDATA%\Microsoft\WinGet\Packages\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\ffmpeg-8.1.2-full_build\bin\ffmpeg.exe')

# name: (duration s, aevalsrc expression in t, subtitle en, subtitle pt)
SOUNDS = {
    'booth_cut': (0.45, '(random(0)*2-1)*exp(-6*t)*0.8 + sin(2*PI*2200*t)*exp(-20*t)*0.3', 'Blade cuts', 'Lâmina corta'),
    'booth_oopsie': (1.7, 'sin(2*PI*(if(lt(t,0.4),311,if(lt(t,0.8),294,if(lt(t,1.2),277,262*(1+0.03*sin(2*PI*6*t)))))*t))*0.5*(1-exp(-30*t))*exp(-0.6*t)',
                     'Something went wrong', 'Algo deu errado'),
    'link_lost': (0.7, 'sin(2*PI*(900-1100*t/1.4)*t)*exp(-3*t)*0.6 + (random(1)*2-1)*0.15*exp(-4*t)', 'Link lost', 'Conexão perdida'),
    'link_restored': (0.45, 'sin(2*PI*(200+900*t/0.9)*t)*(1-exp(-40*t))*exp(-2*t)*0.6', 'Link restored', 'Conexão restabelecida'),
    'mecha_boot': (0.9, 'sin(2*PI*(if(lt(t,0.15),523,if(lt(t,0.3),659,if(lt(t,0.45),784,1047))))*t)*0.45*(1-exp(-60*mod(t,0.15)))*exp(-1.5*t)',
                   'Mecha powers up', 'Mecha liga'),
    'gene_splice': (0.6, 'sin(2*PI*600*t + 4*sin(2*PI*18*t))*0.5*exp(-3*t)*(1-exp(-50*t))', 'Genes splice', 'Genes se unem'),
    'gene_mutation': (0.9, '(sin(2*PI*220*t)+sin(2*PI*233*t)+sin(2*PI*(330+40*sin(2*PI*5*t))*t))*0.22*exp(-1.8*t)*(1-exp(-30*t))',
                      'Something mutates', 'Algo sofre mutação'),
    'array_murmur': (2.0, '((random(2)*2-1)*0.35)*(0.6+0.4*sin(2*PI*3.1*t))*(0.5+0.5*sin(2*PI*0.7*t))*(1-exp(-6*t))*exp(-0.6*t)',
                     'The Board murmurs', 'O Conselho murmura'),
    'staff_complain': (0.35, '(2*mod((300-250*t)*t,1)-1)*0.35*exp(-5*t)*(1-exp(-60*t))', 'Staff complains', 'Funcionário reclama'),
}


# Heads at work: the warden head's shush (event head.warden_shush; its sounds.json entry and subtitle come from
# tools/gen_p38_heads.py). Breath noise through a voiceless "sh" band, 2-6 kHz with its peak near 3 kHz, swelling over
# 80 ms, held, and let go by 0.55 s. name: (duration s, aevalsrc expression in t, ffmpeg filters)
HEAD = {
    'head/warden_shush': (0.6, '(random(0)*2-1)*0.7*min(1,t/0.08)*if(lt(t,0.32),1,exp(-14*(t-0.32)))',
                          'highpass=f=1900,highpass=f=1900,lowpass=f=6200,equalizer=f=3000:t=q:w=1.2:g=6,volume=0.24'),
}


# The Corporate Wellness Treadmill's whir (event treadmill.run, played every 40 ticks while a belt runs; its sounds.json
# entry and subtitle come from tools/gen_p40_bodies.py). Two seconds of soft rubber-belt hiss over a low motor hum, a
# faint slap each time the belt's seam passes the roller (four a second), faded in and out so back-to-back plays join.
# Nobody has listened to it in the game yet. name: (duration s, aevalsrc expression in t, ffmpeg filters)
BELT = {
    'treadmill/run': (2.0, '((random(0)*2-1)*0.5*(0.75+0.25*sin(2*PI*4*t)) + sin(2*PI*96*t)*0.35 + sin(2*PI*192*t)*0.12'
                           ' + (random(1)*2-1)*0.6*exp(-60*mod(t,0.25)))*min(1,t/0.12)*min(1,(2-t)/0.12)',
                      'highpass=f=70,lowpass=f=1800,volume=0.35'),
}


# The workforce (0.7.0): the Time Clock, the Job Card, the Supervisor's Whistle and the Staff Lanyard. Their sounds.json
# entries and subtitles come from tools/gen_p41_workforce.py. Each sound is a few layers mixed: a tone layer is an
# aevalsrc expression in t; a noise layer is seeded white noise (anoisesrc) shaped by an envelope expression in t; each
# layer has its own filters, the mix its own. Struck metal is a few decaying sines (no FM: the FM bell is the company
# talking, and these are its machines). Nobody has listened to them in the game yet.
# name: (duration s, [(noise seed or None for a tone, expression in t, layer filters)], mix filters)
_P = 0.30      # the punch's stamp lands 6 ticks after the card goes in
_E6 = 1318.5   # the clock bell's note


def _struck(t0, f, ratios, amps, decays):
    """Decaying sines from t0 (s): a struck brass or iron part, 1 ms onset."""
    modes = '+'.join(f'{a}*sin(2*PI*{f * r:.2f}*(t-{t0}))*exp(-{d}*(t-{t0}))' for r, a, d in zip(ratios, amps, decays))
    return f'if(gte(t,{t0}),({modes})*min(1,(t-{t0})/0.001),0)'


def _pea(f, t):
    """A pea whistle on f Hz at local time t: it settles 4 % up over the first 30 ms, the pea rattles it at 34 Hz in
    pitch and level, with a weak second harmonic."""
    ph = f'(2*PI*({f}*{t}-{f}*0.04*(1-exp(-30*{t}))/30))'
    return f'(sin({ph}+2.2*sin(2*PI*34*t))+0.12*sin(2*{ph}+4.4*sin(2*PI*34*t)))*(0.62+0.38*sin(2*PI*34*t+1))'


_BLAST = 'min(1,t/0.025)*if(lt(t,0.62),1,exp(-30*(t-0.62)))'
_SHORT = 'min(1,mod(t,0.26)/0.02)*if(lt(mod(t,0.26),0.13),1,exp(-40*(mod(t,0.26)-0.13))*max(0,(0.25-mod(t,0.26))/0.12))*lt(t,0.52)'
WORK = {
    # Staff punches in: the card slides into the slot, the lever ratchets down over four teeth, the stamp comes down
    # with a cast-iron "chunk" (a falling thud, three body modes and a sharp tick), and the lever springs back.
    'time_clock/punch_in': (0.75, [
        (11, 'if(lt(t,0.09),sin(PI*t/0.09)^2,0)*0.22', 'highpass=f=1800,lowpass=f=6000'),
        (None, 'if(between(t,0.12,0.26),sin(2*PI*2600*t)*exp(-260*mod(t-0.12,0.035)),0)*0.35', 'highpass=f=900'),
        (None, f'if(gte(t,{_P}),sin(2*PI*(90*(t-{_P})+70*(1-exp(-40*(t-{_P})))/40))*exp(-22*(t-{_P}))'
               f'*min(1,(t-{_P})/0.002)*0.9,0)', 'lowpass=f=600'),
        (None, _struck(_P, 610, (1, 2.35, 3.76), (0.32, 0.2, 0.13), (16, 22, 30)), 'anull'),
        (12, f'if(gte(t,{_P}),exp(-180*(t-{_P})),0)*0.8', 'highpass=f=1500'),
        (None, _struck(0.47, 3100, (1,), (0.3,), (220,)), 'anull'),
    ], 'highpass=f=40,volume=0.444'),
    # Clock bell dings (a worker punched in late): a small hammer tick, then a brass dome bell on E6, a twin 2.5 Hz
    # above it for a slow beat, the dome's partials at 2.32, 4.25 and 6.63 times; the hammer bounces once at 110 ms.
    'time_clock/bell': (2.0, [
        (21, 'exp(-400*t)*0.35', 'highpass=f=3000'),
        (None, _struck(0, _E6, (1, 1 + 2.5 / _E6, 2.32, 4.25, 6.63), (0.55, 0.25, 0.22, 0.12, 0.06), (2.6, 2.6, 4.5, 8, 13))
         + '+' + _struck(0.11, _E6, (1, 2.32), (0.19, 0.08), (3.4, 5)), 'anull'),
    ], 'highpass=f=200,volume=0.138'),
    # Job Card printed: seven type bars strike the card (a clack each, 35 ms apart, none quite alike), the platen
    # ratchets three teeth, and the card slides out of the slot.
    'time_clock/card_printed': (0.85, [
        (31, 'if(lt(t,0.245),exp(-240*mod(t,0.035))*(0.7+0.3*sin(17*floor(t/0.035))),0)', 'highpass=f=600,lowpass=f=4000'),
        (None, 'if(lt(t,0.245),sin(2*PI*(1700+300*sin(11*floor(t/0.035)))*mod(t,0.035))*exp(-140*mod(t,0.035))'
               '*min(1,mod(t,0.035)/0.0005)*0.55,0)', 'anull'),
        (None, 'if(between(t,0.30,0.36),sin(2*PI*3200*(t-0.30))*exp(-300*mod(t-0.30,0.02))*0.25,0)', 'highpass=f=1500'),
        (32, 'if(between(t,0.40,0.72),sin(PI*(t-0.40)/0.32)^2,0)*0.35', 'highpass=f=900,lowpass=f=3500'),
    ], 'highpass=f=80,volume=0.347'),
    # Whistle blows (one blast: Follow or Hold): a pea whistle on 2.85 kHz, a little breath under it, let go over 100 ms.
    'whistle/blast': (0.85, [
        (None, f'{_pea(2850, "t")}*{_BLAST}', 'anull'),
        (41, f'0.12*{_BLAST}', 'highpass=f=900,lowpass=f=8000'),
    ], 'highpass=f=300,volume=0.117'),
    # Whistle blows twice (two short blasts): the same whistle, 150 ms each, 260 ms apart.
    'whistle/two_short': (0.62, [
        (None, f'{_pea(2850, "mod(t,0.26)")}*{_SHORT}', 'anull'),
        (42, f'0.12*{_SHORT}', 'highpass=f=900,lowpass=f=8000'),
    ], 'highpass=f=300,volume=0.15'),
    # Lanyard fitted: the cord swishes over the head, the brass clip is pressed and snaps shut, the badge taps the chest.
    'lanyard/on': (0.45, [
        (51, 'if(lt(t,0.21),sin(PI*t/0.21)^2,0)*0.36', 'highpass=f=1200,lowpass=f=4500'),
        (None, _struck(0.18, 3400, (1,), (0.3,), (500,)) + '+' + _struck(0.205, 4100, (1, 1.54), (0.5, 0.3), (60, 90))
         + '+' + _struck(0.26, 1100, (1,), (0.25,), (150,)), 'anull'),
        (52, 'if(gte(t,0.205),exp(-600*(t-0.205)),0)*0.5', 'highpass=f=2500'),
    ], 'highpass=f=150,volume=0.55'),
    # Lanyard comes off: the clip unsnaps, the cord whips off, the badge flutters away end over end (four ticks, each
    # quieter).
    'lanyard/off': (0.5, [
        (None, _struck(0, 3800, (1, 1.55), (0.5, 0.25), (80, 120)), 'anull'),
        (61, 'exp(-400*t)*0.4', 'highpass=f=2500'),
        (62, 'if(between(t,0.03,0.24),exp(-((t-0.12)/0.04)^2),0)*0.45', 'highpass=f=2000,lowpass=f=7000'),
        (None, 'if(between(t,0.24,0.42),sin(2*PI*2200*mod(t-0.24,0.045))*exp(-260*mod(t-0.24,0.045))'
               '*min(1,mod(t-0.24,0.045)/0.0005)*exp(-6*(t-0.24))*0.25,0)', 'highpass=f=800'),
    ], 'highpass=f=150,volume=0.55'),
    # The Secondment Collar is fitted (0.7.1; its sounds.json entry and subtitle come from tools/gen_p42_secondment.py):
    # the open brass ring slides round the neck (a dry rasp), its two halves meet with a heavy click, the spring catch
    # drops a beat later, and the ring hums a moment on its own note.
    'staff/collar_fitted': (0.6, [
        (71, 'if(lt(t,0.16),sin(PI*t/0.16)^2,0)*0.3', 'highpass=f=700,lowpass=f=3200'),
        (None, _struck(0.16, 1250, (1, 2.4, 3.9), (0.45, 0.25, 0.12), (40, 70, 110)), 'anull'),
        (72, 'if(gte(t,0.16),exp(-500*(t-0.16)),0)*0.6', 'highpass=f=2200'),
        (None, _struck(0.23, 3600, (1, 1.48), (0.35, 0.2), (180, 260)), 'anull'),
        (None, _struck(0.24, 880, (1, 2.32), (0.12, 0.05), (7, 11)), 'anull'),
    ], 'highpass=f=120,volume=0.62'),
}


def work(names):
    """Renders the named WORK sounds only (python tools/make_sounds.py time_clock/punch_in ...); sounds.json is not
    touched. Mono OGG Vorbis, 44.1 kHz, quality 5, bit-exact."""
    for name in names:
        dur, layers, mix = WORK[name]
        out = os.path.join(OUT, name + '.ogg')
        os.makedirs(os.path.dirname(out), exist_ok=True)
        graph = []
        for i, (seed, expr, filters) in enumerate(layers):
            if seed is None:
                src = f"aevalsrc='{expr}':s=44100:d={dur}"
            else:
                src = f"anoisesrc=r=44100:d={dur}:c=white:a=1:seed={seed},aeval='val(0)*({expr})':c=same"
            graph.append(f'{src},{filters}[l{i}]')
        ins = ''.join(f'[l{i}]' for i in range(len(layers)))
        # 3 ms of silence first (the encoder's pre-echo stays under the onset), a 20 ms fade at the end
        fade = f'adelay=delays=3:all=1,atrim=end={dur},afade=t=in:st=0.003:d=0.001,afade=t=out:st={dur - 0.02:.3f}:d=0.02'
        graph.append(f'{ins}amix=inputs={len(layers)}:normalize=0:duration=longest,{mix},{fade}')
        cmd = [FFMPEG, '-y', '-loglevel', 'error', '-filter_complex', ';'.join(graph), '-ac', '1', '-ar', '44100',
               '-c:a', 'libvorbis', '-q:a', '5', '-fflags', '+bitexact', '-flags:a', '+bitexact', '-map_metadata', '-1',
               out]
        subprocess.run(cmd, check=True)
    print(len(names), 'sounds')


def head(names):
    """Renders the named HEAD (or BELT) sounds only (python tools/make_sounds.py head/warden_shush); sounds.json is not
    touched."""
    for name in names:
        dur, expr, filters = {**HEAD, **BELT}[name]
        out = os.path.join(OUT, name + '.ogg')
        os.makedirs(os.path.dirname(out), exist_ok=True)
        cmd = [FFMPEG, '-y', '-loglevel', 'error', '-f', 'lavfi', '-i', f"aevalsrc='{expr}':s=44100:d={dur}",
               '-af', filters, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '4', '-fflags', '+bitexact', '-flags:a', '+bitexact',
               '-map_metadata', '-1', out]
        subprocess.run(cmd, check=True)
    print(len(names), 'sounds')


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, (dur, expr, *_rest) in SOUNDS.items():
        out = os.path.join(OUT, name + '.ogg')
        cmd = [FFMPEG, '-y', '-loglevel', 'error', '-f', 'lavfi', '-i', f"aevalsrc='{expr}':s=44100:d={dur}",
               '-af', 'lowpass=f=9000,volume=0.9', '-ac', '1', '-c:a', 'libvorbis', '-q:a', '4', out]
        subprocess.run(cmd, check=True)
    sounds = {name: {'sounds': [f'headinajar:{name}'], 'subtitle': f'subtitles.headinajar.{name}'} for name in SOUNDS}
    with open(os.path.join(OUT, '..', 'sounds.json'), 'w', encoding='utf-8') as f:
        json.dump(sounds, f, indent=2)
        f.write('\n')
    print(len(SOUNDS), 'sounds')


if __name__ == '__main__':
    import sys
    if sys.argv[1:] == ['work']:
        work(list(WORK))
    elif sys.argv[1:]:
        if any(n in WORK for n in sys.argv[1:]):
            work([n for n in sys.argv[1:] if n in WORK])
        if any(n not in WORK for n in sys.argv[1:]):
            head([n for n in sys.argv[1:] if n not in WORK])
    else:
        main()

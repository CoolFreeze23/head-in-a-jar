"""Synthesises Head Office's sound effects with ffmpeg (no samples, all generated from formulas) into
src/main/resources/assets/headinajar/sounds/*.ogg, and writes sounds.json. Run: python tools/make_sounds.py"""
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
    main()

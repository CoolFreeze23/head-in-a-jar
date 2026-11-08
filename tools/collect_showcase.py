"""Copies showcase screenshots into docs/images and builds the GIFs with ffmpeg.
usage: python tools/collect_showcase.py   (after each showcase run; it picks up whatever is in run-smoke/smoke-out)"""
import glob
import os
import shutil
import subprocess

ROOT = os.path.join(os.path.dirname(__file__), '..')
SHOTS = os.path.join(ROOT, 'run-smoke', 'smoke-out', 'screenshots')
OUT = os.path.join(ROOT, 'docs', 'images')
FFMPEG = shutil.which('ffmpeg') or os.path.expandvars(
    r'%LOCALAPPDATA%\Microsoft\WinGet\Packages\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\ffmpeg-8.1.2-full_build\bin\ffmpeg.exe')
STILLS = ['chair', 'linked', 'life_support', 'hosts', 'hosted', 'frames', 'gantry', 'lab', 'splicer', 'heads_at_work', 'array', 'handbook', 'jade', 'socket']
# gif: (frame prefix, fps, width)
GIFS = {'operation': ('op', 12, 560), 'drill': ('drill', 12, 560), 'colossus': ('colossus', 10, 480)}


def main():
    os.makedirs(OUT, exist_ok=True)
    for name in STILLS:
        src = os.path.join(SHOTS, name + '.png')
        if os.path.exists(src):
            # a 960-wide copy keeps the repo light
            subprocess.run([FFMPEG, '-y', '-loglevel', 'error', '-i', src, '-vf', 'scale=960:-1:flags=lanczos', os.path.join(OUT, name + '.png')], check=True)
            print('still', name)
    for gif, (prefix, fps, width) in GIFS.items():
        frames = sorted(glob.glob(os.path.join(SHOTS, prefix + '_*.png')))
        if not frames:
            continue
        pattern = os.path.join(SHOTS, prefix + '_%03d.png')
        palette = os.path.join(SHOTS, prefix + '_palette.png')
        subprocess.run([FFMPEG, '-y', '-loglevel', 'error', '-framerate', str(fps), '-i', pattern,
                        '-vf', f'scale={width}:-1:flags=lanczos,palettegen=max_colors=128', palette], check=True)
        subprocess.run([FFMPEG, '-y', '-loglevel', 'error', '-framerate', str(fps), '-i', pattern, '-i', palette,
                        '-lavfi', f'scale={width}:-1:flags=lanczos[x];[x][1:v]paletteuse=dither=bayer:bayer_scale=3', '-loop', '0',
                        os.path.join(OUT, gif + '.gif')], check=True)
        print('gif', gif, len(frames), 'frames')


if __name__ == '__main__':
    main()

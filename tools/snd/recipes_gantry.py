"""The Assembly Gantry: a works assembly bay with an Anglepoise arm on a brass column, a steel turntable deck and a pad.

The arm is a sprung Anglepoise in cream enamel with a small enamel head on its end: a welder, a wrench, a gripper and an
inked rubber stamp slide out of the head on a rod, and at rest the head sits in a brass cup on the column. A build
starts with the works klaxon (two brassy tones, a falling minor third, no glide: a klaxon, not a siren); the arm's servo
whirrs as it swings, its springs singing faintly, and stops with a soft clunk; the welder slides out with a dry click;
each module socket is spot-welded (a short arc crackle and zap over the welding set's 100-120 Hz buzz, the spot
sizzling as it sets), and a high socket is reached by a longer arc jumping the gap; the turntable turns the frame on its
ball race and clacks into its detent; the inspection lamp sweeps the frame bottom to top (a soft rising valve-oscillator
sweep, not the company's FM voice); the rubber stamp strikes the finished frame (the build's signature: a rubber
thock, the paper slapped on the steel, the frame ringing); the head clinks home in its brass cup. While the pad charges a
mecha the lead ticks softly every 2 s; repairs are a wrench tapped on steel plate, and taking a frame apart is a ratchet
wrench, three or four clicks.

Times inside a file are seconds; one tick is 50 ms. Every file is played from a key of the Gantry's clips, at the
Gantry block, and has its hit at 3 ms. Takes differ by construction (a per-take table of frequencies, decays and
timings), not by luck of the seed.
"""
import numpy as np
from nsynth import *
from recipes_machines import small_tick
from recipes_machines2 import env_seg, smooth, creaks, knock, brass_ring
from recipes_machines3 import buzz, pad, thum

T = TICK


# ---------------------------------------------------------------- the start: the works klaxon

def klaxon(take, r):
    """The works klaxon at the start of an assembly (build key 0): two tones, G4 then E4 (a falling minor third from the
    company motif's notes), 0.27 s and 0.30 s with a 30 ms dip between them, no glide between the tones. Each a buzzy
    band-limited sawtooth (18 partials, 1/k^0.85) driven into a soft clip so it brays like brass, its diaphragm scooping
    up 6 % into the note in 20 ms, through a horn's 220-3200 Hz throat with its bell's 900-1500 Hz formant, a 31 Hz
    growl in it. Low-passed at 2.8 kHz (4th order): loud, brassy, never shrill."""
    dur = 1.00
    b = buf(dur)
    for start, f, L, g in ((0.003, 392.0, 0.27, 1.0), (0.305, 329.6, 0.30, 0.95)):
        d = L + 0.25
        t = tt(d)
        fc = f * (1 - 0.06 * np.exp(-t / 0.020))
        x = np.tanh(2.2 * norm(osc_harm(fc, 18, r, 0.85)))
        x = bandpass(x, 220, 3200) + 0.7 * bandpass(x, 900, 1500)
        x = x * (1 - 0.18 * (0.5 + 0.5 * np.cos(2 * np.pi * 31 * t)))
        e = np.clip(t / 0.010, 0, 1) * np.where(t < L, 1.0, np.exp(-(t - L) / 0.022))
        place(b, g * norm(x * e), start)
    return lowpass(b, 2800, order=4)


# ---------------------------------------------------------------- the arm

SERVO = [dict(m=1.00, L=0.40, grit=0.38, air=0.14, spring=2300.0, rock=13.0),
         dict(m=1.07, L=0.46, grit=0.40, air=0.15, spring=2500.0, rock=15.0),
         dict(m=0.92, L=0.52, grit=0.32, air=0.12, spring=2150.0, rock=11.0)]


def servo(take, r):
    """The Anglepoise arm swinging (build key 3 and before each stand-off; load, unload, open and open_loaded key 0):
    the drive's relay pulls in at 3 ms, the house motor (whine, commutator grit, air) runs the eased swing (sin^2 speed
    over 0.40-0.52 s), the arm's long springs singing faintly as they stretch (a narrow 2.15-2.5 kHz band following the
    speed, rocking at 11-15 Hz) and the elbow's hinge giving a few dry stick-slip pings. The swing eases out to nothing:
    the arrival is gantry.servo_stop."""
    p = SERVO[take - 1]
    m, L = p['m'], p['L']
    dur = L + 0.30
    b = buf(dur)
    t = tt(dur)
    s = smooth(env_seg(t, 0.003, 0.003 + L), 8)
    b += motor(t, s, r, 150 * m, 470 * m, grit=p['grit'], air=p['air'], whine=0.30)
    sp = bandpass(noise(dur, r), p['spring'] * 0.92, p['spring'] * 1.08) * s ** 1.2 * (0.7 + 0.3 * np.cos(2 * np.pi * p['rock'] * t))
    b += 0.10 * norm(sp)
    creaks(r, b, 0.06, L - 0.05, 1700 * m, gap=(0.030, 0.070), level=0.05)
    place(b, relay_click(r, 1700 * m, release_after=0.026), 0.003, 0.45)
    return b


SSTOP = [dict(m=1.00, d=1.00, spring=430.0, settle=0.095), dict(m=1.08, d=0.85, spring=470.0, settle=0.085),
         dict(m=0.93, d=1.15, spring=400.0, settle=0.110)]


def servo_stop(take, r):
    """The arm arriving (build key 11 and after the stamp; load key 10): a soft clunk at 3 ms as the gear train meets its
    stop (a damped steel body about 1.1-1.3 kHz, a thunk 200 -> 130 Hz, a tooth of the gear), the motor's last half turn
    running down into it (backlash, 30 ms), the springs settling (a stretched-spring mode set about 400-470 Hz, 60 ms)
    and the elbow's settle tick 85-110 ms on."""
    p = SSTOP[take - 1]
    m, d = p['m'], p['d']
    dur = 0.55
    b = buf(dur)
    t = tt(dur)
    clunk = mix(0.8 * knock(r, 1200 * m, 0.9 * d, th=(200, 130), th_gain=0.30, nz=0.35, band=(800, 5000), hp=1800),
                0.50 * tooth(r, 2600 * m))
    place(b, norm(clunk), 0.003, 1.0)
    s = 0.3 * np.clip((t - 0.003) / 0.003, 0, 1) * np.exp(-np.maximum(t - 0.003, 0) / 0.030) * (t > 0.003)
    b += 0.25 * motor(t, s, r, 150 * m, 470 * m, grit=0.35, air=0.12, whine=0.30)
    place(b, norm(modal(p['spring'], [1, 2.04, 3.1, 4.3], [1, 0.5, 0.3, 0.15],
                        [0.060 * d, 0.040 * d, 0.025 * d, 0.015 * d], r, 0.02)), 0.005, 0.12)
    place(b, small_tick(r, 2200 * m, 0.004), p['settle'], 0.18)
    return b


TOOL = [dict(m=1.00, L=0.120, band=(2200.0, 7000.0), res=3600.0), dict(m=1.08, L=0.105, band=(2500.0, 7500.0), res=3900.0),
        dict(m=0.93, L=0.140, band=(2000.0, 6500.0), res=3300.0)]


def tool_out(take, r):
    """A tool sliding out of the head on its rod (build key 50, the welder out over 3 ticks, easeOutCubic): the catch
    lets go at 3 ms (a tiny steel tick about 3 kHz), the rod slides out fast and slows (speed (1 - u)^2 over 105-140 ms:
    dry 2-7.5 kHz steel-on-brass noise with the sleeve's narrow ring about 3.3-3.9 kHz) and clicks into its detent at
    the end (a tooth and a short brass body about 1.8 kHz)."""
    p = TOOL[take - 1]
    m, L = p['m'], p['L']
    dur = 0.45
    b = buf(dur)
    t = tt(dur)
    place(b, small_tick(r, 3000 * m, 0.004), 0.003, 0.55)
    u = np.clip((t - 0.006) / L, 0, 1)
    s = np.where((t > 0.006) & (t < 0.006 + L), np.clip((t - 0.006) / 0.006, 0, 1) * (1 - u) ** 2, 0.0)
    s = smooth(s, 3)
    sl = bandpass(noise(dur, r), p['band'][0] * m, p['band'][1] * m) * s
    b += 0.55 * norm(sl) + 0.30 * norm(bandpass(sl, p['res'] * 0.95, p['res'] * 1.05))
    click = mix(tooth(r, 3200 * m), 0.5 * norm(modal(1800 * m, [1, 2.756], [1, 0.4], [0.010, 0.006], r, 0.02)))
    place(b, norm(click), 0.006 + L, 0.75)
    return b


def _ramp(x):
    """0 -> 1 smoothstep of x (clipped), for the speed curves of the telescope strokes."""
    x = np.clip(x, 0, 1)
    return x * x * (3 - 2 * x)


def _stroke(r, m, L, f, cut, air, s):
    """The common body of a telescope stroke over 0.003 .. 0.003 + L under the speed curve s (0..1, already let go):
    a low-passed whoosh of oil through the rod's port whose cutoff runs cut[0] -> cut[1] across the stroke, the
    cylinder's air (0.8-5 kHz) and the groan, a 6-partial harmonic tone gliding f[0] -> f[1] (exponentially) with a
    4 Hz waver. Returns the summed layers."""
    dur = len(s) / SR
    t = tt(dur)
    u = np.clip((t - 0.003) / L, 0, 1)
    fc = cut[0] + (cut[1] - cut[0]) * u
    whoosh = sweep_bandpass(noise(dur, r), np.full(len(t), 60.0), fc * (0.6 + 0.4 * s), block=128) * s
    hiss = bandpass(noise(dur, r), 800 * m, 5000 * m) * s ** 1.5
    fg = f[0] * (f[1] / f[0]) ** u * (1 + 0.03 * np.sin(2 * np.pi * 4 * t))
    groan = osc_harm(fg, 6, r, 1.3) * s ** 0.6
    return 0.50 * norm(whoosh) + air * norm(hiss) + 0.26 * norm(groan)


EXTEND = [dict(m=1.00, L=0.15, f=(72.0, 104.0), cut=(320.0, 1500.0), air=0.42, valve=0.30, stop=520.0, land=0.40),
          dict(m=1.07, L=0.12, f=(78.0, 110.0), cut=(360.0, 1650.0), air=0.50, valve=0.24, stop=560.0, land=0.48),
          dict(m=0.93, L=0.20, f=(70.0, 98.0), cut=(300.0, 1350.0), air=0.36, valve=0.36, stop=490.0, land=0.34)]


def extend(take, r):
    """A telescope of the arm sliding out (build and repair clips, a key at the start of each stroke; the forearm's two
    stages out by up to 43 px, the upper arm by up to 15 px, at about 10 px a tick, so 1-5 ticks): the valve opens at
    3 ms (a dry 'tsk' and a short 3-10 kHz hiss as the pressure goes in), the oil pushes the rod out over 0.12-0.20 s,
    the speed coming up in the first quarter and the pressure still building (level 0.75 -> 1.0), the whoosh's cutoff
    climbing 300-360 -> 1350-1650 Hz and the groan rising 70-78 -> 98-110 Hz, easing over the last fifth onto its
    end-stop (a soft thunk: a small iron body about 490-560 Hz, a thunk 130 -> 85 Hz, at 0.34-0.48). It reads as rising
    and ends gently; the retraction is the firmer of the two."""
    p = EXTEND[take - 1]
    m, L = p['m'], p['L']
    dur = L + 0.42
    b = buf(dur)
    t = tt(dur)
    u = (t - 0.003) / L
    s = _ramp(u / 0.25) * (1 - 0.55 * _ramp((u - 0.8) / 0.2)) * (0.75 + 0.25 * np.clip(u, 0, 1))
    s = np.where(u < 1, s, 0.45 * np.exp(-(t - 0.003 - L) / 0.015))  # 0.45: the curve's value at the stop
    s = smooth(s * (u > 0), 3)
    b += _stroke(r, m, L, p['f'], p['cut'], p['air'], s)
    place(b, strike(r, 2500, 0.0008), 0.003, 0.35)
    place(b, norm(noise_burst(3000 * m, 10000, 0.030, r, attack=0.004)), 0.003, p['valve'])
    stop = mix(0.45 * norm(iron_body(r, p['stop'], p['stop'] * 1.6, 0.8, noise_gain=0.30, band_scale=m)),
               0.55 * thunk(r, 130 * m, 85 * m, 0.040, 0.015), 0.25 * strike(r, 1500))
    place(b, norm(stop), 0.003 + L, p['land'])
    return b


RETRACT = [dict(m=1.00, L=0.15, f=(106.0, 72.0), cut=(1500.0, 380.0), air=0.40, valve=0.20, stop=430.0, land=0.80,
                puff=0.20, settle=0.050),
           dict(m=1.08, L=0.12, f=(112.0, 76.0), cut=(1650.0, 420.0), air=0.48, valve=0.16, stop=470.0, land=0.88,
                puff=0.16, settle=0.044),
           dict(m=0.92, L=0.19, f=(102.0, 70.0), cut=(1350.0, 340.0), air=0.36, valve=0.24, stop=400.0, land=0.74,
                puff=0.24, settle=0.058)]


def retract(take, r):
    """A telescope of the arm drawing back in (build and repair clips, a key at the start of each stroke, 1-5 ticks):
    the return valve clicks open at 3 ms (a 'tsk' and a smaller hiss), the rod is drawn in over 0.12-0.19 s at full
    speed from the first sixth to the end, the whoosh's cutoff falling 1350-1650 -> 340-420 Hz and the groan falling
    102-112 -> 70-76 Hz; it arrives still moving, so it ends on a firmer end-stop than the extension (an iron body
    about 400-470 Hz, a thunk 150 -> 80 Hz and a strike, at 0.74-0.88), the spent oil's vent puff 12 ms after it
    (1.5-7 kHz) and the stage seating with a quieter tick 44-58 ms after the stop."""
    p = RETRACT[take - 1]
    m, L = p['m'], p['L']
    dur = L + 0.45
    b = buf(dur)
    t = tt(dur)
    u = (t - 0.003) / L
    s = _ramp(u / 0.16) * (0.9 + 0.1 * np.clip(u, 0, 1))
    s = np.where(u < 1, s, np.exp(-(t - 0.003 - L) / 0.012))
    s = smooth(s * (u > 0), 3)
    b += _stroke(r, m, L, p['f'], p['cut'], p['air'], s)
    place(b, strike(r, 2500, 0.0008), 0.003, 0.30)
    place(b, norm(noise_burst(2500 * m, 9000, 0.022, r, attack=0.003)), 0.003, p['valve'])
    stop = mix(0.45 * norm(iron_body(r, p['stop'], p['stop'] * 1.55, 0.7, noise_gain=0.35, band_scale=m)),
               0.60 * thunk(r, 150 * m, 80 * m, 0.038, 0.016), 0.35 * strike(r, 1400))
    end = 0.003 + L
    place(b, norm(stop), end, p['land'])
    place(b, norm(noise_burst(1500 * m, 7000, 0.025, r, attack=0.005)), end + 0.012, p['puff'])
    place(b, mix(norm(modal(p['stop'] * 1.25, [1, 2.756], [1, 0.5], [0.018, 0.011], r)), 0.6 * thunk(r, 120, 90, 0.022, 0.010)),
          end + p['settle'], 0.16)
    return b


# ---------------------------------------------------------------- the welder

WELD = [dict(m=1.00, L=0.20, hum=100.0, lo=2200.0, hi=11000.0, sp=(0.004, 0.014)),
        dict(m=1.10, L=0.25, hum=120.0, lo=2600.0, hi=12500.0, sp=(0.004, 0.012)),
        dict(m=0.90, L=0.29, hum=100.0, lo=1900.0, hi=10000.0, sp=(0.005, 0.016))]


def weld(take, r):
    """One spot weld at a socket (build, each slot's contact + 2 ticks; the plating pass): the electrode touches with a
    snap at 3 ms (a strike and a 0.9-9 kHz zap, 5 ms), the arc crackles for 0.20-0.29 s (1.9-12.5 kHz bursts 4-16 ms
    apart, thinning) over the welding set's buzz (100 or 120 Hz, harmonics 1/k^0.7 from 150 Hz to 6 kHz, dying with the
    arc), and the spot sizzles as it sets (a fine 4-14 kHz crackle thinning over 0.1 s)."""
    p = WELD[take - 1]
    m, L = p['m'], p['L']
    dur = L + 0.40
    b = buf(dur)
    place(b, strike(r, 2500, 0.0008), 0.003, 1.0)
    place(b, norm(noise_burst(900, 9000, 0.005, r)), 0.003, 0.75)
    cr = crackle(r, L, p['lo'], p['hi'], spacing=p['sp'], width=(0.002, 0.006), first_gain=0.8)
    cr *= np.clip(1 - tt(len(cr) / SR) / (L + 0.04), 0, 1) ** 0.8
    place(b, norm(cr), 0.004, 0.55)
    tb = tt(L)
    bz = buzz(r, np.full(len(tb), p['hum'] * m), L, nharm=30, tilt=0.7, band=(150, 6000))
    bz *= np.clip(tb / 0.004, 0, 1) * (1 - tb / L) ** 1.2
    place(b, norm(pad(bz, 0.03)), 0.004, 0.50)
    sz = crackle(r, 0.10, 4000, 14000, spacing=(0.006, 0.020), width=(0.001, 0.003))
    sz *= np.clip(1 - tt(len(sz) / SR) / 0.12, 0, 1) ** 1.5
    place(b, norm(sz), 0.004 + 0.8 * L, 0.22)
    return b


ARC = [dict(m=1.00, L=0.50, f=(95.0, 140.0), lo=2000.0, hi=11000.0), dict(m=1.08, L=0.60, f=(105.0, 160.0), lo=2300.0, hi=12000.0)]


def arc(take, r):
    """A longer arc jumping the gap from the welder to a high socket: a hard snap at 3 ms (a strike and a 0.8-9 kHz
    crack), the arc crackling for 0.50-0.60 s (2-12 kHz bursts 6-30 ms apart) over a buzz that rises 95-105 -> 140-160 Hz
    as the arc stretches, a faint ozone sizzle (5-14 kHz) under it, and a small snap as it lets go at the end."""
    p = ARC[take - 1]
    m, L = p['m'], p['L']
    dur = L + 0.40
    b = buf(dur)
    t = tt(dur)
    place(b, strike(r, 2500, 0.0008), 0.003, 1.0)
    place(b, norm(noise_burst(800, 9000, 0.004, r)), 0.003, 0.70)
    cr = crackle(r, L, p['lo'] * m, p['hi'], spacing=(0.006, 0.030), width=(0.002, 0.006), first_gain=0.7)
    cr *= np.clip(1 - tt(len(cr) / SR) / (L + 0.06), 0, 1) ** 0.5
    place(b, norm(cr), 0.004, 0.55)
    tb = tt(L)
    f = p['f'][0] + (p['f'][1] - p['f'][0]) * tb / L
    bz = buzz(r, f, L, nharm=30, tilt=0.65, band=(180, 6000)) * np.clip(tb / 0.008, 0, 1) * (1 - tb / L) ** 0.6
    place(b, norm(pad(bz, 0.03)), 0.004, 0.45)
    e = (t > 0.004) * np.clip((t - 0.004) / 0.02, 0, 1) * np.clip(1 - (t - 0.004) / L, 0, 1) ** 0.8
    b += 0.10 * norm(bandpass(noise(dur, r), 5000, 14000) * e)
    place(b, strike(r, 3000, 0.0008), 0.003 + L, 0.45)
    place(b, norm(noise_burst(1500, 10000, 0.003, r)), 0.003 + L, 0.35)
    return b


# ---------------------------------------------------------------- the deck

TURN = [dict(m=1.00, L=0.45, balls=38.0, clack=720.0), dict(m=1.07, L=0.40, balls=46.0, clack=780.0)]


def turntable(take, r):
    """The turntable turning the frame (build, each turn and the presenting turn; disassemble and repair key 2): the
    drive's clutch engages at 3 ms (a relay click), the steel deck turns on its ball race over 0.40-0.45 s (sin^2 speed):
    a 60-380 Hz rumble chopped at the ball-pass rate (38-46 Hz at full speed), the balls' 1.5-5 kHz hiss and the
    reduction gear's low whine, then the indexing pin drops into its detent as the deck stops (a clack: a steel knock about
    720-780 Hz over the deck's iron body, a tooth, a thunk 170 -> 100 Hz) and settles with a tick 60 ms on."""
    p = TURN[take - 1]
    m, L = p['m'], p['L']
    dur = L + 0.45
    b = buf(dur)
    t = tt(dur)
    s = smooth(env_seg(t, 0.003, 0.003 + L), 10)
    gate = (0.5 + 0.5 * np.cos(2 * np.pi * np.cumsum(p['balls'] * s) / SR)) ** 2
    b += 0.55 * norm(bandpass(noise(dur, r), 60, 380) * (0.4 + 0.6 * gate) * s)
    b += 0.32 * norm(bandpass(noise(dur, r), 1500 * m, 5000 * m) * s ** 1.3 * (0.6 + 0.4 * gate))
    b += 0.35 * motor(t, s, r, 90 * m, 260 * m, grit=0.30, air=0.10, whine=0.30)
    place(b, relay_click(r, 1300 * m, release_after=0.024), 0.003, 0.40)
    clack = mix(knock(r, p['clack'] * m, 1.0, th=(170, 100), th_gain=0.5, nz=0.3, band=(600, 5000), hp=1500),
                0.6 * norm(iron_body(r, 560 * m, 900 * m, 0.7, noise_gain=0.25)), 0.4 * tooth(r, 2400 * m))
    place(b, norm(clack), 0.003 + L, 0.90)
    place(b, small_tick(r, 2000 * m, 0.005), 0.063 + L, 0.15)
    return b


# ---------------------------------------------------------------- the inspection

SCAN = [dict(L=0.50, f=(520.0, 1560.0), flut=9.0, hum=100.0), dict(L=0.58, f=(480.0, 1720.0), flut=11.0, hum=120.0)]


def scan(take, r):
    """The inspection lamp sweeping the frame bottom to top (build, the inspection key; the head travels over 10 ticks):
    the lamp's switch ticks at 3 ms, then a soft valve oscillator (a sine with a little second and third harmonic) rises
    a minor-tenth-plus, 520 -> 1560 Hz or 480 -> 1720 Hz (exponential, 0.50-0.58 s), growing a little as it climbs and
    fluttering at 9-11 Hz with the lamp's raster, over a faint 100-120 Hz filament hum; it lets go in about 30 ms at the
    top. No FM: the company's bell is not in it."""
    p = SCAN[take - 1]
    L = p['L']
    dur = L + 0.30
    b = buf(dur)
    t = tt(dur)
    u = np.clip((t - 0.003) / L, 0, 1)
    f = p['f'][0] * (p['f'][1] / p['f'][0]) ** u
    ph = 2 * np.pi * np.cumsum(f) / SR
    tone = np.sin(ph) + 0.25 * np.sin(2 * ph) + 0.12 * np.sin(3 * ph)
    e = (t > 0.003) * np.clip((t - 0.003) / 0.015, 0, 1) * np.where(t < 0.003 + L, 0.7 + 0.3 * u,
                                                                     np.exp(-(t - 0.003 - L) / 0.030))
    flutter = 1 - 0.25 * (0.5 + 0.5 * np.cos(2 * np.pi * p['flut'] * t))
    b += 0.80 * norm(tone * e * flutter)
    b += 0.10 * norm(buzz(r, np.full(len(t), p['hum']), dur, nharm=20, tilt=1.0, band=(200, 2000)) * e)
    place(b, small_tick(r, 2400, 0.004), 0.003, 0.35)
    return lowpass(b, 6000)


# ---------------------------------------------------------------- the stamp

STAMP = [dict(m=1.00, d=1.10, slap=0.40, rock=0.016, bounce=0.060), dict(m=1.07, d=0.85, slap=0.32, rock=0.013, bounce=0.054)]


def stamp(take, r):
    """The inked rubber stamp striking the finished frame (build, the stamp key, easeInExpo into the chest): the build's
    signature. At 3 ms the rubber die's thock (a damped body about 360-385 Hz, ratios 1, 2.3, 3.9, 22-65 ms, over a thunk
    190 -> 105 Hz), the form's paper slapped flat on the steel (0.9-8 kHz, 14 ms), the chest plate ringing (an iron body
    about 820-880 Hz with its plate about 1.35-1.45 kHz and its noise band) and the stamp mount's small brass ping (about
    2.7 kHz); 13-16 ms later the die's face rocks down after its heel (a second thock, a little lower, at 0.85), and the
    die settles back onto the paper a tick on, quietly."""
    p = STAMP[take - 1]
    m, d = p['m'], p['d']
    b = buf(1.40)
    thock = mix(0.06 * strike(r, 1200), 0.25 * thunk(r, 190 * m, 105 * m, 0.045, 0.015),
                0.70 * norm(modal(360 * m, [1, 2.3, 3.9], [1, 0.45, 0.2], [0.065, 0.038, 0.022], r, 0.02)))
    place(b, norm(thock), 0.003, 1.0)
    place(b, norm(iron_body(r, 820 * m, 1350 * m, 2.0 * d, noise_gain=0.50)), 0.005, 0.60)
    place(b, norm(noise_burst(900, 8000, 0.014, r, attack=0.0008)), 0.003, p['slap'])
    place(b, brass_ring(r, 2700 * m, 0.8 * d), 0.004, 0.15)
    face = mix(0.25 * thunk(r, 180 * m, 100 * m, 0.040, 0.015),
               0.70 * norm(modal(345 * m, [1, 2.3, 3.9], [1, 0.45, 0.2], [0.065, 0.038, 0.022], r, 0.02)),
               0.45 * norm(iron_body(r, 840 * m, 1380 * m, 1.6 * d, noise_gain=0.50)))
    place(b, norm(face), 0.003 + p['rock'], 0.85)
    seat = mix(0.5 * thunk(r, 200 * m, 120 * m, 0.012, 0.006), 0.6 * norm(modal(380 * m, [1, 2.3], [1, 0.4], [0.010, 0.006], r)),
               0.3 * norm(noise_burst(900, 6000, 0.004, r)))
    place(b, norm(seat), 0.003 + p['bounce'], 0.22)
    return b


# ---------------------------------------------------------------- rest and charge

PARK = [dict(m=1.00, d=1.00, bounce=0.052, settle=0.150), dict(m=1.11, d=0.85, bounce=0.047, settle=0.140),
        dict(m=0.93, d=1.15, bounce=0.058, settle=0.165)]


def park(take, r):
    """The head settling into its brass cup at rest (unload key 13, close key 5; easeInQuad, then a 1 px bounce): a light
    clink at 3 ms (a strike, the cup's brass ring about 1.9-2.3 kHz, the cup's wider modes about 1.2-1.45 kHz and the
    enamel head's soft knock about 850 Hz), the bounce touching down again a tick on, softer, and a tiny settle tick
    140-165 ms on."""
    p = PARK[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.60)
    clink = mix(0.35 * strike(r, 2500), 0.55 * brass_ring(r, 2050 * m, 1.1 * d),
                0.35 * norm(modal(1300 * m, [1, 1.59, 2.14], [1, 0.5, 0.3], [0.035 * d, 0.022 * d, 0.014 * d], r, 0.01)),
                0.25 * knock(r, 850 * m, 0.8, th=(240, 170), th_gain=0.2, nz=0.2))
    place(b, norm(clink), 0.003, 1.0)
    place(b, norm(mix(0.4 * brass_ring(r, 2080 * m, 0.7 * d), 0.3 * knock(r, 880 * m, 0.6, th_gain=0.15, nz=0.2))),
          0.003 + p['bounce'], 0.32)
    place(b, small_tick(r, 2300 * m, 0.004), p['settle'], 0.10)
    return b


CTICK = [dict(m=1.00, hum=100.0, d=1.00), dict(m=1.10, hum=120.0, d=0.85), dict(m=0.88, hum=100.0, d=1.20)]


def charge_tick(take, r):
    """The charge lead's soft electric tick (charge, loop 40, key 0: once every 2 s while the pad charges a mecha): the
    lead's relay ticks at 3 ms (a small damped body about 600-750 Hz, 5-10 ms), its contact gives a tiny spark (1.5-6
    kHz, 1 ms) and the lead's field pulses once (a 10 ms buzz on 100 or 120 Hz), all low-passed at 2.2 kHz. Very soft:
    it ticks all through a charge."""
    p = CTICK[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.25)
    x = mix(0.6 * norm(modal(680 * m, [1, 1.7, 2.6], [1, 0.4, 0.2], [0.008 * d, 0.005 * d, 0.003 * d], r, 0.02)),
            0.25 * norm(noise_burst(1500, 6000, 0.0012, r)),
            0.30 * thum(r, p['hum'], 0.010, nharm=20, tilt=0.9))
    place(b, norm(lowpass(pad(x), 2200)), 0.003, 1.0)
    return b


# ---------------------------------------------------------------- the wrench

TAP = [dict(m=1.00, d=1.00), dict(m=1.12, d=0.85)]


def tap(take, r):
    """A wrench tapped on steel plate (repair keys 16, 22, 28, rising 1.0, 1.05, 1.1): at 3 ms a strike, the plate's iron
    body (free-bar modes about 1.05-1.2 kHz, its plate about 1.65-1.85 kHz and a 1-7 kHz noise band, 45-55 ms), the
    wrench's own short ring (about 2.35-2.65 kHz) and a light thunk 220 -> 150 Hz."""
    p = TAP[take - 1]
    m, d = p['m'], p['d']
    b = buf(0.60)
    plate = iron_body(r, 1050 * m, 1650 * m, 0.8 * d, noise_gain=0.30)
    wrench = norm(modal(2350 * m, [1, 2.756, 5.404], [1, 0.45, 0.2], [0.018 * d, 0.010 * d, 0.006 * d], r, 0.01))
    hit = mix(0.45 * strike(r, 2000), 0.75 * norm(plate), 0.35 * wrench, 0.20 * thunk(r, 220 * m, 150 * m, 0.015, 0.008))
    place(b, norm(hit), 0.003, 1.0)
    return b


RATCH = [dict(m=1.00, n=3, gap=0.050), dict(m=1.06, n=4, gap=0.040), dict(m=0.94, n=4, gap=0.046)]


def ratchet(take, r):
    """A ratchet wrench turning a bolt (disassemble keys 16, 28, 40 and 3 ticks after each, at pitch 1.0 and 1.1): three
    or four clicks 40-50 ms apart from 3 ms, each a ratchet tooth (about 2.8-3.2 kHz) on the pawl's small steel body
    (about 1.9-2.1 kHz), and the wrench's handle answering the last click (a short steel ring about 1.1 kHz). The two
    plays of a visit, 150 ms apart, run on as one stroke."""
    p = RATCH[take - 1]
    m, n, gap = p['m'], p['n'], p['gap']
    b = buf(0.45)
    for k in range(n):
        click = mix(tooth(r, 3000 * m * r.uniform(0.98, 1.02)),
                    0.3 * norm(modal(2000 * m, [1, 2.756], [1, 0.4], [0.008, 0.005], r, 0.02)))
        place(b, norm(click), 0.003 + k * gap, [1.0, 0.85, 0.9, 0.8][k])
    place(b, norm(modal(1100 * m, [1, 2.756], [1, 0.3], [0.020, 0.012], r, 0.02)), 0.003 + (n - 1) * gap, 0.10)
    return b


# ---------------------------------------------------------------- references (measured as the game plays them)

PISTON_IN = dict(event='block.piston.contract', file='tile__piston__in.ogg', vol=0.5, pitch=0.675)
PISTON_OUT = dict(event='block.piston.extend', file='tile__piston__out.ogg', vol=0.5, pitch=0.725)
CRAFTER = dict(event='block.crafter.craft', file='block__crafter__craft.ogg', vol=1.0, pitch=1.0)
SPYGLASS = dict(event='item.spyglass.use', file='item__spyglass__use.ogg', vol=1.0, pitch=1.0)
IRON_DOOR_CLOSE = dict(event='block.iron_door.close', file='block__iron_door__close1.ogg', vol=0.9, pitch=0.95)
IRON_TD_OPEN = dict(event='block.iron_trapdoor.open', file='block__iron_trapdoor__open1.ogg', vol=0.9, pitch=0.95)
COPPER_TD_CLOSE = dict(event='block.copper_trapdoor.close', file='block__copper_trapdoor__toggle1.ogg', vol=1.0, pitch=0.95)
LANTERN = dict(event='block.lantern.place', file='block__lantern__place1.ogg', vol=1.0, pitch=0.88)
VAULT_ACTIVATE = dict(event='block.vault.activate', file='block__vault__activate.ogg', vol=1.0, pitch=1.0)
BELL = dict(event='block.note_block.bell', file='note__bell.ogg', vol=1.0, pitch=1.0)
GOLEM_HURT = dict(event='entity.iron_golem.hurt', file='mob__irongolem__hit1.ogg', vol=1.0, pitch=1.0)

BASE = dict(category='BLOCKS', attenuation=16, fixed_range=False, volume=1.0, ticks=None, muffled=False)
KEY_NOTE = ' Keyframe volume 1.0 (the file carries the loudness); the key\'s pitch is kept.'


def ev(name, **kw):
    d = dict(BASE)
    d.update(id='gantry.' + name, file='gantry/' + name)
    d.update(kw)
    return d


SOUNDS = [
    # ---- the start
    ev('klaxon', fn=klaxon, takes=1, target=-22.0, cls='alarm (works klaxon)', kind='stinger', fade_ms=30, hit_ms=3,
       ref=IRON_DOOR_CLOSE, pitch=[1.0, 1.0], attenuation=24,
       subtitle_en='Gantry klaxon sounds', subtitle_pt='Buzina do pórtico toca',
       clip_key='build key 0: gantry.klaxon|0.8|1.0 -> gantry.klaxon|1.0|1.0',
       play='Gantry build clips, key 0 (the start of an assembly), at the Gantry block: G4 from 3 ms, E4 from 305 ms, gone by about 0.7 s. Fixed pitch.' + KEY_NOTE),
    # ---- the arm
    ev('servo', fn=servo, takes=3, target=-27.0, cls='servo (the arm swinging)', kind=('servo', 0.40), fade_ms=20,
       hit_ms=3, ref=PISTON_IN, pitch=[0.97, 1.03],
       subtitle_en='Gantry arm swings', subtitle_pt='Braço do pórtico se move',
       clip_key='build key 3 (|0.5|0.8) and 2 ticks before each stand-off (|0.5|1.0); load and unload key 0 (|0.5|0.8); open and open_loaded key 0 (|0.4|1.2) -> volume 1.0, same pitches',
       play='Gantry clips build, load, unload, open and open_loaded: the relay at 3 ms, the eased whirr over 0.40-0.52 s (longer at the keys\' pitch 0.8), easing out to nothing; the arrival is gantry.servo_stop.' + KEY_NOTE),
    ev('servo_stop', fn=servo_stop, takes=3, target=-26.5, cls='servo stop (soft clunk)', kind='latch', fade_ms=15,
       hit_ms=3, ref=COPPER_TD_CLOSE, pitch=[0.95, 1.05], subtitle_en='Gantry arm stops', subtitle_pt='Braço do pórtico para',
       clip_key='build key 11 (|0.4|0.8) and the stand-off after the stamp (|0.4|0.9); load key 10 (|0.4|0.8) -> volume 1.0, same pitches',
       play='Gantry clips build and load, where the arm arrives at stand-off: the clunk at 3 ms, the springs settling, the settle tick 85-110 ms on.' + KEY_NOTE),
    ev('tool_out', fn=tool_out, takes=3, target=-31.0, cls='tick (slide and click)', kind='latch', fade_ms=12,
       hit_ms=3, ref=SPYGLASS, pitch=[0.95, 1.05],
       subtitle_en='Gantry tool slides out', subtitle_pt='Ferramenta desliza para fora',
       clip_key='build key 50: gantry.tool_out|0.4|1.2 -> gantry.tool_out|1.0|1.2',
       play='Gantry build clips, key 50 (the welder out over 3 ticks, easeOutCubic): the catch at 3 ms, the slide slowing over 105-140 ms, the detent click at its end.' + KEY_NOTE),
    ev('extend', fn=extend, takes=3, target=-27.5, cls='servo (hydraulic stroke, the arm telescoping out)',
       kind=('servo', 0.20), fade_ms=20, hit_ms=3, ref=PISTON_OUT, pitch=[0.94, 1.06],
       subtitle_en='Gantry arm extends', subtitle_pt='Braço do pórtico se estende',
       clip_key='build and repair clips, a key at the start of each telescope stroke (|1.0|1.0)',
       play='Gantry clips build and repair, a key at the start of each telescope stroke out (the forearm\'s two stages, up to 43 px, and the upper arm, up to 15 px, 1-5 ticks): the valve at 3 ms, the stroke rising over 0.12-0.20 s, the soft end-stop at its end, gone by about 0.4 s.' + KEY_NOTE),
    ev('retract', fn=retract, takes=3, target=-27.0, cls='servo (hydraulic stroke, the arm telescoping in)',
       kind=('servo', 0.20), fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[0.94, 1.06],
       subtitle_en='Gantry arm retracts', subtitle_pt='Braço do pórtico se recolhe',
       clip_key='build and repair clips, a key at the start of each telescope stroke (|1.0|1.0)',
       play='Gantry clips build and repair, a key at the start of each telescope stroke in (1-5 ticks): the valve at 3 ms, the stroke falling over 0.12-0.19 s, the firmer end-stop and vent puff at its end, the seating tick 44-58 ms on, gone by about 0.45 s.' + KEY_NOTE),
    # ---- the welder
    ev('weld', fn=weld, takes=3, target=-26.0, cls='arc crackle (one spot weld)', kind='latch', fade_ms=15, hit_ms=3,
       ref=VAULT_ACTIVATE, pitch=[0.94, 1.06],
       subtitle_en='Gantry spot-welds', subtitle_pt='Pórtico solda um ponto',
       clip_key='build, each slot\'s contact + 2 ticks (|0.5|1.0); the plating pass (|0.5|0.9, |0.5|1.0) -> volume 1.0, same pitches',
       play='Gantry build clips, one key per weld, with the gantry_weld particles a tick before: the snap at 3 ms, the crackle and the buzz over 0.20-0.29 s, the sizzle after. A random take each time.' + KEY_NOTE),
    ev('arc', fn=arc, takes=2, target=-25.0, cls='arc crackle (a gap jumped)', kind=('servo', 0.50), fade_ms=20, hit_ms=3,
       ref=VAULT_ACTIVATE, pitch=[0.95, 1.05],
       subtitle_en='Welding arc crackles', subtitle_pt='Arco de solda crepita',
       play='Gantry build clips, at the key where the welder arcs across the gap to a high socket, with its particles: the snap at 3 ms, the arc for 0.50-0.60 s, the small snap as it lets go.' + KEY_NOTE),
    # ---- the deck
    ev('turntable', fn=turntable, takes=2, target=-27.0, cls='servo class (bearing rumble and detent)',
       kind=('servo', 0.42), fade_ms=20, hit_ms=3, ref=PISTON_IN, pitch=[0.97, 1.03],
       subtitle_en='Turntable turns', subtitle_pt='Plataforma giratória gira',
       clip_key='build, each turn (|0.6|1.0) and the presenting turn (|0.6|1.1); disassemble and repair key 2 (|0.6|1.0) -> volume 1.0, same pitches',
       play='Gantry clips build, disassemble and repair, on the deck, at the start of the turn: the clutch at 3 ms, the rumble over 0.40-0.45 s, the detent\'s clack at the end of the file\'s turn, the settle tick 60 ms on.' + KEY_NOTE),
    # ---- the inspection
    ev('scan', fn=scan, takes=2, target=-26.5, cls='servo class (inspection sweep)', kind=('servo', 0.50), fade_ms=25,
       hit_ms=3, ref=BELL, pitch=[1.0, 1.0],
       subtitle_en='Inspection lamp sweeps', subtitle_pt='Lâmpada de inspeção varre',
       clip_key='build, the inspection key: gantry.scan|0.4|1.0 -> gantry.scan|1.0|1.0',
       play='Gantry build clips, the inspection key (the head lamp sweeps the frame bottom to top over 10 ticks): the switch tick at 3 ms, the sweep rising over 0.50-0.58 s. Fixed pitch.' + KEY_NOTE),
    # ---- the stamp
    ev('stamp', fn=stamp, takes=2, target=-16.5, cls='signature moment (the stamp)', kind=('step', 0.30), fade_ms=30,
       hit_ms=3, ref=GOLEM_HURT, pitch=[0.97, 1.03], attenuation=24,
       subtitle_en='Frame stamped: approved', subtitle_pt='Chassi carimbado: aprovado',
       clip_key='build, the stamp strike: gantry.stamp|0.7|1.0 -> gantry.stamp|1.0|1.0',
       play='Gantry build clips, the stamp key (the strike on the chest, easeInExpo): the thock, the slap and the frame\'s ring at 3 ms, the die settling a tick on.' + KEY_NOTE),
    # ---- rest and charge
    ev('park', fn=park, takes=3, target=-26.5, cls='glass clink class (brass cup)', kind='latch', fade_ms=15, hit_ms=3,
       ref=LANTERN, pitch=[0.95, 1.05],
       subtitle_en='Gantry head parks', subtitle_pt='Cabeça do pórtico repousa',
       clip_key='unload key 13 and close key 5: gantry.park|0.5|1.0 -> gantry.park|1.0|1.0',
       play='Gantry clips unload (key 13) and close (key 5), the head into its brass cup: the clink at 3 ms, the bounce a tick on, the settle tick at 140-165 ms.' + KEY_NOTE),
    ev('charge_tick', fn=charge_tick, takes=3, target=-31.5, cls='tick (soft, repeating)', kind='click', fade_ms=8,
       hit_ms=3, ref=CRAFTER, pitch=[0.97, 1.03],
       subtitle_en='Gantry charges mecha', subtitle_pt='Pórtico carrega o mecha',
       clip_key='charge (loop 40) key 0: gantry.charge_tick|0.25|1.0 -> gantry.charge_tick|1.0|1.0',
       play='Gantry clip charge (a 40-tick loop while the pad charges a mecha), key 0, once every 2 s: the tick at 3 ms, gone by about 60 ms. A random take each time.' + KEY_NOTE),
    # ---- the wrench
    ev('tap', fn=tap, takes=2, target=-28.0, cls='tap (wrench on steel plate)', kind='latch', fade_ms=15, hit_ms=3,
       ref=IRON_TD_OPEN, pitch=[0.97, 1.03],
       subtitle_en='Wrench taps steel', subtitle_pt='Chave bate no aço',
       clip_key='repair keys 16, 22, 28: gantry.tap|0.6|1.0, 1.05, 1.1 -> volume 1.0, same pitches',
       play='Gantry repair clips, keys 16, 22 and 28 at pitch 1.0, 1.05 and 1.1, at the tap locator: the tap at 3 ms, the plate ringing out by about 0.25 s.' + KEY_NOTE),
    ev('ratchet', fn=ratchet, takes=3, target=-31.0, cls='ratchet (tick class, a short run)', kind=('servo', 0.14),
       fade_ms=10, hit_ms=3, ref=SPYGLASS, pitch=[0.97, 1.03],
       subtitle_en='Ratchet wrench clicks', subtitle_pt='Catraca da chave estala',
       clip_key='disassemble keys 16, 28, 40 (|0.5|1.0) and 19, 31, 43 (|0.5|1.1) -> volume 1.0, same pitches',
       play='Gantry disassemble clips, two keys per bolt 3 ticks apart: three or four clicks 40-50 ms apart from 3 ms, so the two plays run on as one stroke.' + KEY_NOTE),
]

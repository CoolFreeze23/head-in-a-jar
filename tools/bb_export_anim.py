#!/usr/bin/env python3
"""Export the animations of a Blockbench .bbmodel to a GeckoLib 4.9.3 .animation.json.

Standard library only. Reproduces what Blockbench 5.2.x writes from
File > Export > Export GeckoLib Animations with the "GeckoLib Models & Animations"
plugin 4.2.5 (format id geckolib_model), so the file can be made headless.

Usage:
  python tools/bb_export_anim.py MODEL.bbmodel -o OUT.animation.json [--geo GEO.json]
  python tools/bb_export_anim.py MODEL.bbmodel --check [--geo GEO.json]
Options:
  --animation NAME   export only this animation (repeatable)
  --geo FILE         check the animated bone names against a .geo.json
  --check            validate and report, write nothing
  --werror           treat warnings as errors
  --any-format       allow a model whose format is not geckolib_model
Run with --help for the options.

Sources followed (file : function), cited again at each step below:
  BB  = github.com/JannisX11/blockbench @ ec0b282 (5.2.2)
  GLP = github.com/JannisX11/blockbench-plugins @ b11ad01, plugins/geckolib/src/ts (4.2.5)
  GL  = GeckoLib 4.9.3 (branch 1.21.1 @ cce0980), common/src/main/java/software/bernie/geckolib
"""

import argparse
import json
import math
import re
import sys
from decimal import Decimal, ROUND_HALF_UP


class ExportError(Exception):
    pass


WARNINGS = []


def warn(msg):
    WARNINGS.append(msg)


# --------------------------------------------------------------------------------------------
# JavaScript number helpers (Blockbench is JS; these reproduce its numeric/string behaviour)
# --------------------------------------------------------------------------------------------

_JS_DEC = re.compile(r'^[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?$')
_JS_PREFIX = re.compile(r'^[+-]?(Infinity|(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?)')


def js_is_numeric_string(s):
    """not isNaN(s) for a JS string (Number(s) is a number)."""
    t = s.strip()
    if t == '':
        return True
    if _JS_DEC.match(t) or t in ('Infinity', '+Infinity', '-Infinity'):
        return True
    if re.match(r'^0[xX][0-9a-fA-F]+$|^0[oO][0-7]+$|^0[bB][01]+$', t):
        return True
    return False


def js_parse_float(s):
    t = s.lstrip()
    m = _JS_PREFIX.match(t)
    if not m:
        return float('nan')
    return float(m.group(0).replace('Infinity', 'inf'))


def js_num(v):
    """Normalise a float for JSON the way JSON.stringify prints it (70 not 70.0, 0 not -0)."""
    if isinstance(v, bool):
        return v
    if isinstance(v, (int, float)):
        if isinstance(v, float) and (math.isnan(v) or math.isinf(v)):
            raise ExportError('non-finite number %r would be written as null' % v)
        if v == 0:
            return 0
        if isinstance(v, float) and v.is_integer() and abs(v) < 1e21:
            return int(v)
    return v


def js_num_to_string(v):
    """Number.prototype.toString for the values that occur here."""
    v = js_num(v)
    if isinstance(v, int):
        return str(v)
    r = repr(v)
    if 'e' in r:
        mant, exp = r.split('e')
        r = mant + 'e' + ('-' if exp.startswith('-') else '+') + exp.lstrip('+-').lstrip('0')
    return r


def js_round(x):
    """Math.round: halves go up."""
    return math.floor(x + 0.5)


def js_max(a, b):
    if math.isnan(a) or math.isnan(b):
        return float('nan')
    return max(a, b)


def js_clamp(n, lo, hi):
    # BB util/math_util.js Math.clamp
    if n > hi:
        n = hi
    if n < lo or math.isnan(n):
        n = lo
    return n


def trim_float_number(val, max_digits=4):
    # BB util/math_util.js trimFloatNumber: val.toFixed(4) then strip zeros. toFixed rounds the exact
    # binary value, ties to the larger n, so ROUND_HALF_UP on the exact Decimal (times are >= 0).
    q = Decimal(val).quantize(Decimal(1).scaleb(-max_digits), rounding=ROUND_HALF_UP)
    s = format(q, 'f')
    s = re.sub(r'0+$', '', s)
    s = re.sub(r'\.$', '', s)
    if s == '-0':
        return '0'
    return s


# --------------------------------------------------------------------------------------------
# Molang helpers
# --------------------------------------------------------------------------------------------

def export_molang(value):
    """BB util/util.js exportMolang."""
    if value is None or value == '' or value == 0 or value is False:
        return 0
    if isinstance(value, bool):
        raise ExportError('boolean %r where a Molang value was expected' % value)
    if isinstance(value, (int, float)):
        return value
    if isinstance(value, str):
        if js_is_numeric_string(value):
            if re.match(r'^\s*0[xXoObB]', value):
                raise ExportError('value %r: Blockbench would export it as 0 (parseFloat of a hex/octal/binary literal)' % value)
            num = js_parse_float(value)
            if math.isinf(num):
                raise ExportError('value %r is infinite' % value)
            return 0 if math.isnan(num) else num
        return value.replace('\n', '')
    raise ExportError('unsupported Molang value %r' % (value,))


_STRING_NUM = re.compile(r'^-?\d+(\.\d+f?)?$')


def process_molang_return(molang, callback):
    """BB util/molang.ts processMolangReturn."""
    if 'return ' in molang:
        return re.sub(r'return (.+?)(;|\Z)', lambda m: 'return ' + callback(m.group(1)) + m.group(2), molang)
    molang = re.sub(r';+\Z', '', molang)
    idx = molang.rfind(';')
    if idx == -1:
        if '=' in molang:
            return molang + ';' + callback('')
        return callback(molang)
    before, after = molang[:idx], molang[idx + 1:]
    if '=' in after:
        return molang + ';' + callback('')
    return before + ';' + callback(after)


def _invert_expression(expression):
    # BB util/molang.ts invertMolang, the per-character inverter
    invert = True
    depth = 0
    last_operator = None
    result = ''
    for char in expression:
        if not depth:
            operator = None
            had_input = True
            if char == '-' and last_operator != '*' and last_operator != '/':
                if not invert and not last_operator:
                    result += '+'
                invert = False
                continue
            elif char == ' ' or char == '\n':
                had_input = False
            elif char == '+' and last_operator != '*' and last_operator != '/':
                result += '-'
                invert = False
                continue
            elif char in '?:':
                invert = True
                operator = char
            elif invert:
                result += '-'
                invert = False
            elif char in '+-*/&|':
                operator = char
            if had_input:
                last_operator = operator
        if char in '{([':
            depth += 1
        elif char in '})]':
            depth -= 1
        result += char
    return result


def invert_molang(molang):
    """BB util/molang.ts invertMolang."""
    if isinstance(molang, (int, float)) and not isinstance(molang, bool):
        return -molang
    if molang == '' or molang == '0':
        return molang
    if _STRING_NUM.match(molang):
        return js_num_to_string(-float(molang.rstrip('f')))
    return process_molang_return(molang, _invert_expression)


def is_number(v):
    return isinstance(v, (int, float)) and not isinstance(v, bool)


# --------------------------------------------------------------------------------------------
# GeckoLib easing names (GL animation/EasingType.java registry; GLP easing.ts easingFunctions)
# --------------------------------------------------------------------------------------------

GL_EASINGS = {
    'linear', 'none', 'step', 'catmullrom',
    'easeinsine', 'easeoutsine', 'easeinoutsine', 'easeinquad', 'easeoutquad', 'easeinoutquad',
    'easeincubic', 'easeoutcubic', 'easeinoutcubic', 'easeinquart', 'easeoutquart', 'easeinoutquart',
    'easeinquint', 'easeoutquint', 'easeinoutquint', 'easeinexpo', 'easeoutexpo', 'easeinoutexpo',
    'easeincirc', 'easeoutcirc', 'easeinoutcirc', 'easeinback', 'easeoutback', 'easeinoutback',
    'easeinelastic', 'easeoutelastic', 'easeinoutelastic', 'easeinbounce', 'easeoutbounce', 'easeinoutbounce',
}


def is_args_easing(easing):
    # GLP easing.ts isArgsEasing
    e = easing or ''
    return 'Back' in e or 'Elastic' in e or 'Bounce' in e or e == 'step'


# --------------------------------------------------------------------------------------------
# Model reading
# --------------------------------------------------------------------------------------------

ANIM_KEYS = {'uuid', 'name', 'loop', 'override', 'length', 'snapping', 'selected', 'saved', 'saved_name', 'path',
             'anim_time_update', 'blend_weight', 'start_delay', 'loop_delay', 'animators', 'markers', 'bones'}
ANIMATOR_KEYS = {'name', 'type', 'keyframes', 'rotation_global', 'quaternion_interpolation'}
KEYFRAME_KEYS = {'channel', 'data_points', 'uuid', 'time', 'color', 'interpolation', 'uniform', 'bezier_linked',
                 'bezier_left_time', 'bezier_left_value', 'bezier_right_time', 'bezier_right_value',
                 'easing', 'easingArgs'}
POINT_KEYS = {'x', 'y', 'z', 'effect', 'locator', 'script', 'file', 'bind_to_actor'}
TRANSFORM = ('rotation', 'position', 'scale')   # BB timeline_animators.js BoneAnimator.prototype.channels order
EFFECT = ('sound', 'particle', 'timeline')      # BB timeline_animators.js EffectAnimator channels


def version_lt(a, b):
    def parts(v):
        return [int(p) for p in re.findall(r'\d+', str(v))]
    pa, pb = parts(a), parts(b)
    n = max(len(pa), len(pb))
    pa += [0] * (n - len(pa))
    pb += [0] * (n - len(pb))
    return pa < pb


class DataPoint:
    def __init__(self, raw, channel, where):
        unknown = set(raw) - POINT_KEYS
        if unknown:
            raise ExportError('%s: unknown data point field(s) %s' % (where, sorted(unknown)))
        default = '1' if channel == 'scale' else '0'
        self.raw = raw
        # Property defaults (BB keyframe.js KeyframeDataPoint properties): missing x/y/z -> '0' ('1' for scale)
        self.axes = {a: raw.get(a, default) for a in 'xyz'}
        self.effect = raw.get('effect')
        self.locator = raw.get('locator')
        self.script = raw.get('script')
        self.bind_to_actor = raw.get('bind_to_actor', True)


class Kf:
    def __init__(self, raw, where):
        unknown = set(raw) - KEYFRAME_KEYS
        if unknown:
            raise ExportError('%s: unknown keyframe field(s) %s' % (where, sorted(unknown)))
        self.channel = raw.get('channel')
        self.where = where
        self.time = float(raw.get('time', 0))
        self.interpolation = raw.get('interpolation', 'linear')
        if self.interpolation not in ('linear', 'step', 'catmullrom', 'bezier'):
            raise ExportError('%s: unknown interpolation %r' % (where, self.interpolation))
        pts = raw.get('data_points') or []
        if not pts:
            raise ExportError('%s: keyframe has no data points' % where)
        if self.channel in TRANSFORM and len(pts) > 2:
            raise ExportError('%s: %d data points (Blockbench allows 2)' % (where, len(pts)))
        self.points = [DataPoint(p, self.channel, where) for p in pts]
        self.transform = self.channel in TRANSFORM
        self.easing = raw.get('easing')
        self.easing_args = raw.get('easingArgs')
        # BB keyframe.js Keyframe properties defaults
        self.bezier_left_time = list(raw.get('bezier_left_time', [-0.1, -0.1, -0.1]))
        self.bezier_right_time = list(raw.get('bezier_right_time', [0.1, 0.1, 0.1]))
        self.bezier_left_value = list(raw.get('bezier_left_value', [0, 0, 0]))
        self.bezier_right_value = list(raw.get('bezier_right_value', [0, 0, 0]))

    # BB keyframe.js Keyframe.get: missing or empty -> 0, else exportMolang
    def get(self, axis, dp=0):
        dp = int(js_clamp(dp, 0, len(self.points) - 1)) if dp else 0
        v = self.points[dp].axes.get(axis)
        if v is None or v == '' or v == 0:
            return 0 if self.transform else ''
        return export_molang(v)

    # BB keyframe.js Keyframe.getArray
    def get_array(self, dp=0):
        arr = [self.get(a, dp) for a in 'xyz']
        return [a.replace('\n', '') if isinstance(a, str) else a for a in arr]

    def calc(self, axis, dp=0):
        v = self.get(axis, dp)
        if not is_number(v):
            raise ExportError('%s: bezier segment uses Molang %r; Blockbench bakes beziers numerically, '
                              'this tool cannot evaluate Molang' % (self.where, v))
        return float(v)


def load_model(path):
    with open(path, 'r', encoding='utf-8') as f:
        model = json.load(f)
    meta = model.get('meta', {})
    fmt_version = str(meta.get('format_version', '0'))
    # BB formats/bbmodel.js (project parse): files saved before 5.0 store x (position, rotation) and y (rotation)
    # with the opposite sign; Blockbench inverts them on load. Do the same so one export path serves both.
    legacy = version_lt(fmt_version, '5.0')
    if legacy:
        for anim in model.get('animations') or []:
            for animator in (anim.get('animators') or {}).values():
                for kf in animator.get('keyframes') or []:
                    ch = kf.get('channel')
                    for dp in kf.get('data_points') or []:
                        if ch in ('position', 'rotation') and dp.get('x'):
                            dp['x'] = invert_molang(dp['x'])
                        if ch == 'rotation' and dp.get('y'):
                            dp['y'] = invert_molang(dp['y'])
                    if kf.get('interpolation') == 'bezier' and kf.get('bezier_left_value'):
                        if ch in ('position', 'rotation'):
                            kf['bezier_left_value'][0] *= -1
                            kf['bezier_right_value'][0] *= -1
                        if ch == 'rotation':
                            kf['bezier_left_value'][1] *= -1
                            kf['bezier_right_value'][1] *= -1
    return model, fmt_version, legacy


def group_names(model):
    names = {}
    for g in model.get('groups') or []:
        names[g['uuid']] = g.get('name')

    def walk(items):
        for it in items:
            if isinstance(it, dict):
                if 'uuid' in it and 'name' in it:
                    names.setdefault(it['uuid'], it['name'])
                walk(it.get('children') or [])
    walk(model.get('outliner') or [])
    return names


# --------------------------------------------------------------------------------------------
# Keyframe compilation (GLP keyframe.ts keyframeCompileBedrock / geckolibGetArray / checkAndPatchKeyframeValues)
# --------------------------------------------------------------------------------------------

def patch_vector(vec, channel):
    # GLP keyframe.ts checkAndPatchKeyframeValues: '' -> 0, then invert x (rotation, position) and y (rotation)
    vec = [0 if v == '' else v for v in vec]
    if channel in ('rotation', 'position'):
        vec[0] = invert_molang(vec[0])
        if channel == 'rotation':
            vec[1] = invert_molang(vec[1])
    return vec


def gecko_get_array(kf, dp):
    """GLP keyframe.ts geckolibGetArray(data_point, channel). dp mirrors the JS argument (0 or [n] == n)."""
    result = kf.get_array(dp)
    easing = kf.easing
    out = {}
    if len(kf.points) != 1:
        post = kf.get_array(1)
        if not easing and result == post:
            out = {'vector': result}
            if easing is not None:
                out['easing'] = easing
        else:
            out = {'pre': patch_vector(result, kf.channel), 'post': patch_vector(kf.get_array(1), kf.channel)}
            if easing is not None:
                out['easing'] = easing
    else:
        out = {'vector': result}
        if easing is not None:
            out['easing'] = easing
    if is_args_easing(easing) and kf.easing_args is not None:
        out['easingArgs'] = kf.easing_args
    if 'vector' in out:
        out['vector'] = patch_vector(out['vector'], kf.channel)
    return out


def previous_keyframe(kf, keys):
    # BB keyframe.js getPreviousKeyframe
    earlier = [k for k in keys if k.time < kf.time]
    return max(earlier, key=lambda k: k.time) if earlier else None


def compile_transform_keyframe(kf, keys):
    """GLP keyframe.ts keyframeCompileBedrock, plus step handling (see note in BB-EXPORT.md)."""
    prev = previous_keyframe(kf, keys)
    if kf.interpolation == 'catmullrom':
        include_pre = (prev is None and kf.time > 0) or (prev is not None and prev.interpolation != 'catmullrom')
        out = {}
        if include_pre:
            out['pre'] = gecko_get_array(kf, 0)
        out['post'] = gecko_get_array(kf, 1 if include_pre else 0)
        out['lerp_mode'] = 'catmullrom'
        if prev is not None and prev.interpolation == 'step':
            out['pre'] = {'vector': patch_vector(prev.get_array(1), kf.channel)}
        return out
    if prev is not None and prev.interpolation == 'step':
        # BB keyframe.js compileBedrockKeyframe (vanilla codec) step rule: the key after a step key starts
        # from the step key's value, so GeckoLib holds it (pre at t-0.001, see GL BakedAnimationsAdapter).
        post = gecko_get_array(kf, 0)
        post_vec = post.get('vector', post.get('post'))
        return {'pre': patch_vector(prev.get_array(1), kf.channel), 'post': post_vec}
    if len(kf.points) == 1:
        return gecko_get_array(kf, 0)
    return gecko_get_array(kf, 0)


# --------------------------------------------------------------------------------------------
# Bezier baking (BB formats/bedrock/bedrock_animation.js compileAnimation, "Bake bezier keyframe curve")
# --------------------------------------------------------------------------------------------

def _cubic_bezier(t, p0, p1, p2, p3):
    # three.js extras/core/Interpolations.js CubicBezier
    k = 1 - t
    return k * k * k * p0 + 3 * k * k * t * p1 + 3 * (1 - t) * t * t * p2 + t * t * t * p3


def bezier_lerp(before, after, axis, alpha):
    """BB keyframe.js getBezierLerp."""
    n = 'xyz'.index(axis)
    val_before = before.calc(axis, 1)
    val_after = after.calc(axis, 0)
    gap = after.time - before.time
    th_before = js_clamp(before.bezier_right_time[n] or 0, 0, gap)
    th_after = js_clamp(after.bezier_left_time[n] or 0, -gap, 0)
    v0 = (before.time, val_before)
    v1 = (before.time + th_before, (val_before + before.bezier_right_value[n]) or 0)
    v2 = (after.time + th_after, (val_after + after.bezier_left_value[n]) or 0)
    v3 = (after.time, val_after)
    time = before.time + (after.time - before.time) * alpha
    pts = [(_cubic_bezier(i / 200, v0[0], v1[0], v2[0], v3[0]), _cubic_bezier(i / 200, v0[1], v1[1], v2[1], v3[1]))
           for i in range(201)]
    ci, cd = None, float('inf')
    for i, p in enumerate(pts):
        d = abs(p[0] - time)
        if d < cd:
            cd, ci = d, i
    si, sd = None, float('inf')
    for i, p in enumerate(pts):
        if i == ci:
            continue
        d = abs(p[0] - time)
        if d < sd:
            sd, si = d, i
    c, s = pts[ci], pts[si]
    denom = s[0] - c[0]
    if denom == 0:
        m = float('nan') if (time - c[0]) == 0 else math.copysign(float('inf'), (time - c[0]))
    else:
        m = (time - c[0]) / denom
    m = js_clamp(m, 0, 1)
    return c[1] + (s[1] - c[1]) * m


def snap_time(t, snapping):
    fps = js_clamp(snapping, 1, 120)
    return js_clamp(js_round(t * fps) / fps, 0, float('inf'))


def timecode(t, snapping):
    s = trim_float_number(snap_time(t, snapping))
    if '.' not in s:
        s += '.0'
    return s


def bake_bezier(kf, nxt, channel, snapping, channel_tag, kf_timecode):
    interval = 1 / snapping
    interpolated = {}
    time = kf.time + interval
    while time < nxt.time + (interval / 2):
        itc = trim_float_number(snap_time(time, snapping))
        if '.' not in itc:
            itc += '.0'
        lerp = (time - kf.time) / (nxt.time - kf.time)
        value = [bezier_lerp(kf, nxt, a, lerp) for a in 'xyz']
        if channel in ('position', 'rotation'):
            value[0] = -value[0]
        if channel == 'rotation':
            value[1] = -value[1]
        interpolated[itc] = value
        time += interval
    codes = list(interpolated)
    skipped = 0
    threshold = 0.005 if channel == 'scale' else (0.1 if channel == 'rotation' else 0.01)
    for ti, itc in enumerate(codes):
        value = interpolated[itc]
        last = interpolated[codes[ti - 1]] if ti > 0 else channel_tag[kf_timecode]
        nxt_v = interpolated[codes[ti + 1]] if ti + 1 < len(codes) else None
        if nxt_v is None:
            continue
        max_diff = 0.0
        all_irrelevant = True
        for axis in range(3):
            lv = last[axis] if isinstance(last, list) and is_number(last[axis]) else float('nan')
            diff = abs((lv - value[axis]) - (value[axis] - nxt_v[axis]))
            max_diff = js_max(max_diff, diff)
            if not diff < threshold:
                all_irrelevant = False
                break
        if all_irrelevant:
            limit = js_clamp(2 * (threshold / max_diff) if max_diff else float('inf'), 0, 12)
            if skipped < limit:
                skipped += 1
                continue
        channel_tag[itc] = value
        skipped = 0


# --------------------------------------------------------------------------------------------
# Effects (BB keyframe.js compileBedrockKeyframe, non-transform branches; GLP leaves these to Blockbench)
# --------------------------------------------------------------------------------------------

def compile_effect_keyframe(kf, anim_name):
    if kf.channel == 'timeline':
        scripts = []
        for p in kf.points:
            if p.script:
                scripts.extend(str(p.script).split('\n'))
        scripts = [s for s in scripts if re.sub(r'[\n\s;.]+', '', s)]
        scripts = [s if (re.search(r';\s*$', s) or s.startswith('/')) else s + ';' for s in scripts]
        if not scripts:
            raise ExportError('%s: timeline keyframe at %s has no script; Blockbench would drop it' % (anim_name, kf.time))
        if len(scripts) > 1:
            warn('%s: timeline keyframe at %s has %d lines; GeckoLib 4.9.3 hands them to the handler as one string '
                 'formatted like a Java list ("[a;, b;]") (GL KeyFramesAdapter.buildCustomFrameData)'
                 % (anim_name, kf.time, len(scripts)))
            return scripts
        return scripts[0]
    points = []
    for p in kf.points:
        if p.effect:
            script = p.script or None
            if script and not re.sub(r'[\n\s;.]+', '', script):
                script = None
            if script and not script.endswith(';'):
                script += ';'
            o = {'effect': p.effect}
            if p.locator:
                o['locator'] = p.locator
            if p.bind_to_actor is False:
                o['bind_to_actor'] = False
                warn('%s: %s keyframe at %s sets bind_to_actor false; GeckoLib 4.9.3 does not read it'
                     % (anim_name, kf.channel, kf.time))
            if script:
                o['pre_effect_script'] = script
            if kf.channel == 'sound' and p.locator:
                warn('%s: sound keyframe at %s names locator %r; GeckoLib 4.9.3 reads only "effect" for sounds '
                     '(GL KeyFramesAdapter.buildSoundFrameData)' % (anim_name, kf.time, p.locator))
            points.append(o)
    if not points:
        raise ExportError('%s: %s keyframe at %s has no effect name; Blockbench would drop it'
                          % (anim_name, kf.channel, kf.time))
    if len(points) > 1:
        raise ExportError('%s: %s keyframe at %s has %d effects; Blockbench writes an array, which GeckoLib 4.9.3 '
                          'cannot read (getAsJsonObject in GL KeyFramesAdapter) and the whole animation would fail '
                          'to load. Put each effect on its own time.' % (anim_name, kf.channel, kf.time, len(points)))
    return points[0]


# --------------------------------------------------------------------------------------------
# Animation compilation (BB formats/bedrock/bedrock_animation.js compileAnimation)
# --------------------------------------------------------------------------------------------

def max_length(anim, animators):
    # BB animations/animation.js getMaxLength
    length = anim.get('length') or 0
    for animator in animators.values():
        keys = sorted(animator.get('keyframes') or [], key=lambda k: k.get('time', 0))
        for i, k in enumerate(keys):
            if k.get('interpolation') == 'catmullrom' and i == len(keys) - 1:
                continue
            length = max(length, k.get('time', 0))
    return length


def check_easing(kf, anim_name, bone):
    where = '%s / %s / %s @ %s' % (anim_name, bone, kf.channel, kf.time)
    if kf.easing is None:
        if kf.easing_args is not None:
            warn('%s: easingArgs without easing; ignored' % where)
        return
    if not isinstance(kf.easing, str) or kf.easing.lower() not in GL_EASINGS:
        raise ExportError('%s: easing %r is not one GeckoLib 4.9.3 knows; it would silently fall back to linear '
                          '(GL EasingType.fromString)' % (where, kf.easing))
    if kf.interpolation != 'linear':
        raise ExportError('%s: easing %r on a %s keyframe; the GeckoLib plugin clears easing on non-linear keys '
                          '(GLP animationUi.ts renderFrameCallback), so Blockbench would not export it'
                          % (where, kf.easing, kf.interpolation))
    if is_args_easing(kf.easing):
        args = kf.easing_args
        if args is None:
            if kf.easing == 'step':
                raise ExportError('%s: step easing without easingArgs; Blockbench previews 5 steps (GLP easing.ts '
                                  'getEasingArgDefault) but GeckoLib uses 2 (GL EasingType.step). Set easingArgs.' % where)
            return
        if not isinstance(args, list) or not args or not all(is_number(a) for a in args):
            raise ExportError('%s: easingArgs %r must be a list of numbers' % (where, args))
        if kf.easing == 'step' and args[0] < 2:
            raise ExportError('%s: step easingArgs %r; GeckoLib throws below 2 (GL EasingType.step)' % (where, args))
    elif kf.easing_args is not None:
        warn('%s: easingArgs %r on %s is not exported (GLP easing.ts isArgsEasing)' % (where, kf.easing_args, kf.easing))
    if len(kf.points) == 2:
        warn('%s: easing %r on a keyframe with separate pre and post values; GeckoLib 4.9.3 drops easing on pre/post '
             'keyframes (GL BakedAnimationsAdapter.addBedrockKeyframes), so the game plays it linear'
             % (where, kf.easing))


def compile_animation(anim, names):
    name = anim.get('name')
    unknown = set(anim) - ANIM_KEYS
    if unknown:
        raise ExportError('animation %r: unknown field(s) %s' % (name, sorted(unknown)))
    animators = anim.get('animators') or anim.get('bones') or {}
    loop = anim.get('loop', 'once')
    if loop not in ('once', 'loop', 'hold'):
        raise ExportError('animation %r: unknown loop mode %r' % (name, loop))
    snapping = js_clamp(anim.get('snapping', 24), 10, 500)   # BB animation.js extend
    tag = {}
    if loop == 'hold':
        tag['loop'] = 'hold_on_last_frame'
    elif loop == 'loop' or max_length(anim, animators) == 0:
        tag['loop'] = True
    length = anim.get('length') or 0
    if length:
        tag['animation_length'] = js_num(js_round(length * 10000) / 10000)
    if anim.get('override'):
        tag['override_previous_animation'] = True
        warn('%s: override_previous_animation is exported but GeckoLib 4.9.3 ignores it' % name)
    for key in ('anim_time_update', 'blend_weight', 'start_delay'):
        if anim.get(key):
            tag[key] = js_num(export_molang(anim[key]))
            warn('%s: %s is exported but GeckoLib 4.9.3 ignores it (GL BakedAnimationsAdapter.bakeAnimation reads only '
                 'animation_length, loop, bones and effects)' % (name, key))
    if anim.get('loop_delay') and 'loop' in tag:
        tag['loop_delay'] = js_num(export_molang(anim['loop_delay']))
        warn('%s: loop_delay is exported but GeckoLib 4.9.3 ignores it' % name)
    if anim.get('markers'):
        warn('%s: %d timeline marker(s) are editor-only and not exported' % (name, len(anim['markers'])))
    tag['bones'] = {}
    effects = {}
    for uuid, animator in animators.items():
        unknown = set(animator) - ANIMATOR_KEYS
        if unknown:
            raise ExportError('%s / %s: unknown animator field(s) %s' % (name, uuid, sorted(unknown)))
        raw_keys = animator.get('keyframes') or []
        atype = animator.get('type', 'bone')
        if animator.get('rotation_global'):
            raise ExportError('%s / %s: "global rotation" (relative_to entity) is not supported by GeckoLib 4.9.3'
                              % (name, animator.get('name')))
        if not raw_keys:
            continue
        if atype == 'effect' or uuid == 'effects':
            for i, rk in enumerate(raw_keys):
                kf = Kf(rk, '%s / effects / #%d' % (name, i))
                if kf.channel not in EFFECT:
                    raise ExportError('%s: effect keyframe with channel %r' % (name, kf.channel))
                effects.setdefault(kf.channel, []).append(kf)
            continue
        if atype != 'bone':
            raise ExportError('%s / %s: animator type %r is not exported by this tool (Blockbench skips it or bakes IK)'
                              % (name, animator.get('name'), atype))
        if animator.get('quaternion_interpolation'):
            warn('%s / %s: quaternion interpolation is a Blockbench preview setting; GeckoLib interpolates Euler angles'
                 % (name, animator.get('name')))
        bone = names.get(uuid) or animator.get('name')
        if not bone:
            raise ExportError('%s: animator %s has no bone name' % (name, uuid))
        if uuid not in names:
            warn('%s: animator %r has no matching group in the model; exported under its own name' % (name, bone))
        if bone in tag['bones']:
            raise ExportError('%s: two animators for bone %r' % (name, bone))
        bone_tag = tag['bones'][bone] = {}
        by_channel = {}
        for i, rk in enumerate(raw_keys):
            kf = Kf(rk, '%s / %s / #%d' % (name, bone, i))
            if kf.channel not in TRANSFORM:
                raise ExportError('%s / %s: bone keyframe with channel %r' % (name, bone, kf.channel))
            by_channel.setdefault(kf.channel, []).append(kf)
        for channel in TRANSFORM:
            keys = by_channel.get(channel)
            if not keys:
                continue
            keys = sorted(keys, key=lambda k: k.time)
            for k in keys:
                check_easing(k, name, bone)
            for a, b in zip(keys, keys[1:]):
                if a.interpolation == 'catmullrom' and b.interpolation == 'bezier' or \
                        a.interpolation == 'bezier' and b.interpolation == 'catmullrom':
                    warn('%s / %s / %s: catmull-rom next to bezier between %s and %s; Blockbench previews catmull-rom '
                         'but exports the baked bezier' % (name, bone, channel, a.time, b.time))
            ctag = bone_tag[channel] = {}
            for i, kf in enumerate(keys):
                tc = timecode(kf.time, snapping)
                if tc in ctag and i > 0 and timecode(keys[i - 1].time, snapping) == tc:
                    raise ExportError('%s / %s / %s: keyframes at %s and %s snap to the same time %s at %s fps'
                                      % (name, bone, channel, keys[i - 1].time, kf.time, tc, snapping))
                ctag[tc] = compile_transform_keyframe(kf, keys)
                nxt = keys[i + 1] if i + 1 < len(keys) else None
                if nxt is not None and (kf.interpolation == 'bezier' or nxt.interpolation == 'bezier'):
                    bake_bezier(kf, nxt, channel, snapping, ctag, tc)
            # BB compileAnimation "Compressing keyframes"
            if len(ctag) == 1 and len(keys[0].points) == 1 and keys[0].interpolation != 'catmullrom':
                only = next(iter(ctag.values()))
                bone_tag[channel] = only
                if channel == 'scale' and isinstance(only, list) and all(v == only[0] for v in only):
                    bone_tag[channel] = only[0]
                if keys[0].time != 0:
                    warn('%s / %s / %s: a single keyframe at %s becomes a constant pose (its time is not kept)'
                         % (name, bone, channel, keys[0].time))
    for channel, key_name in (('sound', 'sound_effects'), ('particle', 'particle_effects'), ('timeline', 'timeline')):
        keys = sorted(effects.get(channel, []), key=lambda k: k.time)
        for kf in keys:
            tc = timecode(kf.time, snapping)
            out = tag.setdefault(key_name, {})
            if tc in out:
                raise ExportError('%s: two %s keyframes snap to the same time %s' % (name, channel, tc))
            out[tc] = compile_effect_keyframe(kf, name)
    if not tag['bones']:
        del tag['bones']
    # Key order as Blockbench writes it: bones before effects only when the bone animators come first in the
    # file; GeckoLib does not care about this order.
    return tag


def normalise(o):
    if isinstance(o, dict):
        return {k: normalise(v) for k, v in o.items()}
    if isinstance(o, list):
        return [normalise(v) for v in o]
    if is_number(o):
        return js_num(o)
    return o


def dumps(o, indent=0):
    """Tab-indented JSON with short number arrays on one line (like Blockbench's compileJSON)."""
    t = '\t'
    if isinstance(o, dict):
        if not o:
            return '{}'
        items = ['%s%s: %s' % (t * (indent + 1), json.dumps(k), dumps(v, indent + 1)) for k, v in o.items()]
        return '{\n' + ',\n'.join(items) + '\n' + t * indent + '}'
    if isinstance(o, list):
        if all(not isinstance(v, (dict, list)) for v in o):
            return '[' + ', '.join(json.dumps(v) for v in o) + ']'
        items = [t * (indent + 1) + dumps(v, indent + 1) for v in o]
        return '[\n' + ',\n'.join(items) + '\n' + t * indent + ']'
    return json.dumps(o)


def export(model_path, only=None, any_format=False):
    model, fmt_version, legacy = load_model(model_path)
    fmt = model.get('meta', {}).get('model_format')
    if fmt not in ('geckolib_model', 'animated_entity_model') and not any_format:
        raise ExportError('model format is %r, not geckolib_model; the GeckoLib plugin conversions apply only to '
                          'GeckoLib models (pass --any-format to export anyway)' % fmt)
    names = group_names(model)
    anims = {}
    seen = set()
    for anim in model.get('animations') or []:
        n = anim.get('name')
        if only and n not in only:
            continue
        if n in seen:
            raise ExportError('two animations named %r' % n)
        seen.add(n)
        anims[n] = compile_animation(anim, names)
    if only:
        missing = set(only) - seen
        if missing:
            raise ExportError('no animation named %s' % sorted(missing))
    if not anims:
        raise ExportError('the model has no animations')
    return normalise({'format_version': '1.8.0', 'animations': anims}), fmt_version, legacy


def geo_bone_names(path):
    with open(path, 'r', encoding='utf-8') as f:
        geo = json.load(f)
    names = set()
    geos = geo.get('minecraft:geometry')
    if not isinstance(geos, list):
        raise ExportError('%s has no minecraft:geometry list' % path)
    for g in geos:
        for b in g.get('bones') or []:
            names.add(b.get('name'))
    return names


def main(argv=None):
    ap = argparse.ArgumentParser(description='Export Blockbench .bbmodel animations to a GeckoLib 4.9.3 .animation.json')
    ap.add_argument('model')
    ap.add_argument('-o', '--output')
    ap.add_argument('--geo', help='geometry .geo.json to check animated bone names against')
    ap.add_argument('--check', action='store_true', help='validate only, write nothing')
    ap.add_argument('--animation', action='append', help='only this animation (repeatable)')
    ap.add_argument('--werror', action='store_true', help='treat warnings as errors')
    ap.add_argument('--any-format', action='store_true')
    args = ap.parse_args(argv)
    if not args.check and not args.output:
        ap.error('give -o OUTPUT or --check')
    try:
        result, fmt_version, legacy = export(args.model, args.animation, args.any_format)
        problems = []
        if args.geo:
            bones = geo_bone_names(args.geo)
            for an, a in result['animations'].items():
                for b in a.get('bones', {}):
                    if b not in bones:
                        hint = [x for x in bones if x and x.lower() == b.lower()]
                        problems.append('animation %s animates bone %r, which %s does not have%s'
                                        % (an, b, args.geo, (' (case differs: %r)' % hint[0]) if hint else ''))
    except ExportError as e:
        print('ERROR: %s' % e, file=sys.stderr)
        return 1
    for w in WARNINGS:
        print('warning: %s' % w, file=sys.stderr)
    for p in problems:
        print('ERROR: %s' % p, file=sys.stderr)
    if problems or (args.werror and WARNINGS):
        return 1
    for an, a in result['animations'].items():
        chans = sum(len([c for c in TRANSFORM if c in b]) for b in a.get('bones', {}).values())
        fx = sum(len(a.get(k, {})) for k in ('sound_effects', 'particle_effects', 'timeline'))
        print('%s: %d bone(s), %d channel(s), %d effect keyframe(s), loop=%s, length=%s'
              % (an, len(a.get('bones', {})), chans, fx, a.get('loop', 'once'), a.get('animation_length', '-')))
    if legacy:
        print('note: model saved with format_version %s (< 5.0); x/y signs converted as Blockbench 5 does on load'
              % fmt_version)
    if args.check:
        print('check passed, nothing written')
        return 0
    with open(args.output, 'w', encoding='utf-8', newline='\n') as f:
        f.write(dumps(result) + '\n')
    print('wrote %s' % args.output)
    return 0


if __name__ == '__main__':
    sys.exit(main())

"""The Golden Parachute (shared by all eight frames' ejects): geometry, UV layout, placeholder sheet.

Source of truth for the parachute's cubes, bones and locators. Entity space in pixels, 16 px to a block: origin at the
jar's base (the player's feet: where the game draws the jar), y up, the jar facing -Z.

    python art/entity/golden_parachute/golden_parachute_geo.py                    # placeholder, UV map, .bbmodel
    python art/entity/golden_parachute/golden_parachute_geo.py --scale --out X    # preview copy: a player beside it

What it is: a severance package. A gold brocade canopy (an octagon of four hinged panels, 40 px across, round a one-step
crown with an open apex vent framed by a crimson rosette; a crimson valance; four brass tassels) on four white cords to a swivel on top of an
ivory envelope (the pack, with a crimson seal) that rides on a brass ring round the jar's lid. Four crimson straps run
down the jar's corners to a steel cradle under its foot: the jar is handed down on a silver platter. A manila tag on a
cord under the cradle carries the form number (P45, the form you are given on leaving).

The jar is NOT part of this model: the game draws it at 1:1 at the origin (`jar_anchor`, with a stand-in for previews).
The harness fits the jar exactly (base 14 x 1, glass 13 x 12, lid 14 x 1, valve 4 x 2) and nothing enters the glass.
Nothing is scaled: the canopy opens by hinging its four panels, not by stretching; parts are shown or hidden only by
step keys.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.dirname(os.path.dirname(HERE))
MECHA = os.path.join(ART, 'mecha')
sys.path.insert(0, os.path.join(MECHA, 'wheelhouse'))
import wheelhouse_geo as W  # noqa: E402

TEX_W, TEX_H = 256, 256

# ---- rig numbers (shared with golden_parachute_anim.py) ----
SWIVEL_Y = 21.0          # where the cords meet, on top of the envelope: the canopy's pivot (the code's swing)
CROWN_Y = 43.0           # the panels' hinge height
CROWN = 8.0              # the crown is 16 x 16; the panels hinge on its edges
PANEL = 12.0             # each panel reaches 12 px out (the open canopy spans 40, an octagon: 2.9 x the jar)
WIDTH = 14.0             # each panel is 28 across; the corners between panels are left open (the octagon)
OPEN = 32.0              # the panels' droop when open (degrees): the dome
CORNERS = {'fl': (-1, -1), 'fr': (1, -1), 'rl': (-1, 1), 'rr': (1, 1)}

MATERIALS = dict(W.MATERIALS)
MATERIALS['ribbon'] = ('7d323a', 255)    # placeholder crimson (the palette has no crimson ramp: the paint's call)
ORDER = list(W.ORDER) + ['ribbon']
PARTS = [('canopy', ('crown', 'panel', 'hem', 'tassel', 'rosette')), ('cords', ('cord', 'bridle', 'pilot')),
         ('harness', ('ring', 'strap', 'cradle', 'swivel')), ('pack', ('pack', 'flap', 'seal')), ('tag', ('tag',))]


def corner(sx, sz, droop=None):
    """Where a cord meets the canopy: the outer corner of the front (sz -1) or back (sz +1) panel's underside, at a
    given droop (degrees below level), in the cloth's frame."""
    d = math.radians(OPEN if droop is None else droop)
    r = CROWN + PANEL * math.cos(d)
    return (sx * WIDTH, CROWN_Y - 1 - PANEL * math.sin(d), sz * r)


def cord_aim(sx, sz, droop=None):
    """(tilt from vertical, yaw) of a cord from the swivel to its corner; a cord is modelled up +Y, turned by x then y."""
    x, y, z = corner(sx, sz, droop)
    dy = y - SWIVEL_Y
    return math.degrees(math.atan2(math.hypot(x, z), dy)), math.degrees(math.atan2(x, z))


def cord_rot(sx, sz):
    t, yw = cord_aim(sx, sz)
    return [round(t, 2), round(yw, 2), 0]


def cord_len():
    x, y, z = corner(1, 1)
    return round(math.sqrt(x * x + (y - SWIVEL_Y) ** 2 + z * z))


def bones():
    S, Y = SWIVEL_Y, CROWN_Y
    b = [
        ('root', [0, 0, 0], None, None),
        ('jar_anchor', [0, 0, 0], 'root', None),             # the game draws the jar here, at 1:1
        ('harness', [0, 0, 0], 'root', None),                # cradle, straps, ring: fixed to the jar
        ('pack', [0, 15, 0], 'harness', None),               # the envelope on the lid ring
        ('flap', [0, 19, 4], 'pack', None),                  # its flap: rot x + opens it (hinged at the back)
        ('tag', [0, -1, -8], 'harness', [0, 20, 0]),         # the form-number tag, hung turned 20: rot x swings it
        ('canopy', [0, S, 0], 'root', None),                 # pivot at the swivel: the code's swing goes here
        ('cloth', [0, S, 0], 'canopy', None),                # the crown and panels: rises out of the envelope, falls
        ('pilot', [0, 20, 0], 'cloth', None),                # the pilot chute (modelled on the envelope; keyed up)
        ('panel_f', [0, Y, -CROWN], 'cloth', [-OPEN, 0, 0]),
        ('panel_b', [0, Y, CROWN], 'cloth', [OPEN, 0, 0]),
        ('panel_l', [-CROWN, Y, 0], 'cloth', [0, 0, OPEN]),
        ('panel_r', [CROWN, Y, 0], 'cloth', [0, 0, -OPEN]),
        ('tassels_l', [-CROWN - PANEL, Y - 4, 0], 'panel_l', None),    # shown or hidden by key (hidden afloat)
        ('tassels_r', [CROWN + PANEL, Y - 4, 0], 'panel_r', None),
        ('cords', [0, S, 0], 'cloth', None),                  # all four cords (they follow the panels' corners)
    ]
    for c, (sx, sz) in CORNERS.items():
        b.append(('cord_' + c, [0, S, 0], 'cords', cord_rot(sx, sz)))
    b.append(('standin_jar', [0, 0, 0], 'jar_anchor', None))
    return b


C = W.C


def cubes():
    S, Y = SWIVEL_Y, CROWN_Y
    L = cord_len()
    c = [
        # ---- the harness: a steel cradle under the jar's foot (a silver platter), a crimson strap up each corner of
        # the jar (outside the glass), a brass ring round the lid ----
        C('cradle', [-8, -1, -8], [8, 0, 8], 'steel', 'harness'),
        C('ring_f', [-8, 13, -8], [8, 15, -7], 'brass', 'harness'),
        C('ring_b', [-8, 13, 7], [8, 15, 8], 'brass', 'harness'),
        C('ring_l', [-8, 13, -7], [-7, 15, 7], 'brass', 'harness'),
        C('ring_r', [7, 13, -7], [8, 15, 7], 'brass', 'harness'),
        # ---- the envelope (the pack) on the ring, round the valve; its flap and crimson seal; the swivel on top ----
        C('pack', [-5, 15, -4], [5, 19, 4], 'enamel', 'pack'),
        C('flap', [-5, 19, -4], [5, 20, 4], 'enamel', 'flap'),
        C('seal', [-1, 18.5, -4.5], [1, 20.5, -3.5], 'ribbon', 'flap'),
        C('swivel', [-1, 20, -1], [1, 22, 1], 'brass', 'canopy'),
        # ---- the tag under the cradle: a cord and a 12 x 7 manila tag (P45) ----
        C('tag_cord', [-0.5, -3, -8.5], [0.5, -1, -7.5], 'enamel', 'tag'),
        C('tag', [-6, -10, -8.5], [6, -3, -7.5], 'enamel', 'tag'),
        # ---- the pilot chute: a white bridle and a small brass dome ----
        C('bridle', [-0.5, 20.5, -0.5], [0.5, 27.5, 0.5], 'enamel', 'pilot'),
        C('pilot_dome', [-3, 27, -3], [3, 29, 3], 'brass', 'pilot'),
        C('pilot_rim', [-3.5, 26, -3.5], [3.5, 27, 3.5], 'ribbon', 'pilot'),
        # ---- the canopy (v2): a one-step gold crown round an open apex vent 6 px across (the bridle passes through
        # it; it is also where the pilot's camera sees through from straight above), framed by a crimson rosette ----
        C('crown_f', [-CROWN, Y, -CROWN], [CROWN, Y + 2, -3], 'brass', 'cloth'),
        C('crown_b', [-CROWN, Y, 3], [CROWN, Y + 2, CROWN], 'brass', 'cloth'),
        C('crown_l', [-CROWN, Y, -3], [-3, Y + 2, 3], 'brass', 'cloth'),
        C('crown_r', [3, Y, -3], [CROWN, Y + 2, 3], 'brass', 'cloth'),
        C('rosette_f', [-4, Y + 2, -4], [4, Y + 3, -3], 'ribbon', 'cloth'),
        C('rosette_b', [-4, Y + 2, 3], [4, Y + 3, 4], 'ribbon', 'cloth'),
        C('rosette_l', [-4, Y + 2, -3], [-3, Y + 3, 3], 'ribbon', 'cloth'),
        C('rosette_r', [3, Y + 2, -3], [4, Y + 3, 3], 'ribbon', 'cloth'),
        # ---- stand-in jar (never exported) ----
        C('standin_jar_base', [-7, 0, -7], [7, 1, 7], 'standm', 'standin_jar'),
        C('standin_jar_glass', [-6.5, 1, -6.5], [6.5, 13, 6.5], 'standj', 'standin_jar'),
        C('standin_jar_lid', [-7, 13, -7], [7, 14, 7], 'standm', 'standin_jar'),
        C('standin_jar_valve', [-2, 14, -2], [2, 16, 2], 'standm', 'standin_jar'),
        C('standin_head', [-4, 1.5, -4], [4, 9.5, 4], 'stand', 'standin_jar'),
    ]
    # the straps: one up each corner of the jar, from the cradle to the ring
    for cn, (sx, sz) in CORNERS.items():
        x0, x1 = sorted((sx * 7, sx * 8))
        z0, z1 = sorted((sz * 7, sz * 8))
        c.append(C('strap_' + cn, [x0, 0, z0], [x1, 13, z1], 'ribbon', 'harness'))
    # the four panels (gold brocade), each with a crimson valance hanging 3 px under its outer edge (the dark band
    # seen from below); four brass tassels in all, at the ends of the side panels' valances
    w = WIDTH
    for side in ('f', 'b'):
        s = -1 if side == 'f' else 1
        z0, z1 = sorted((s * CROWN, s * (CROWN + PANEL)))
        h0, h1 = sorted((s * (CROWN + PANEL - 1), s * (CROWN + PANEL)))
        c += [C('panel_' + side, [-w, Y - 1, z0], [w, Y + 1, z1], 'brass', 'panel_' + side),
              C('hem_' + side, [-w, Y - 4, h0], [w, Y - 1, h1], 'ribbon', 'panel_' + side)]
    for side in ('l', 'r'):
        s = -1 if side == 'l' else 1
        x0, x1 = sorted((s * CROWN, s * (CROWN + PANEL)))
        h0, h1 = sorted((s * (CROWN + PANEL - 1), s * (CROWN + PANEL)))
        c += [C('panel_' + side, [x0, Y - 1, -w], [x1, Y + 1, w], 'brass', 'panel_' + side),
              C('hem_' + side, [h0, Y - 4, -w], [h1, Y - 1, w], 'ribbon', 'panel_' + side),
              C('tassel_%s_f' % side, [h0, Y - 7, -w], [h1, Y - 4, -w + 1], 'brass', 'tassels_' + side),
              C('tassel_%s_b' % side, [h0, Y - 7, w - 1], [h1, Y - 4, w], 'brass', 'tassels_' + side)]
    # the cords: white, each modelled straight up from the swivel and turned out to its corner by its bone
    for cn in CORNERS:
        c.append(C('cord_' + cn, [-0.5, S, -0.5], [0.5, S + L, 0.5], 'enamel', 'cord_' + cn))
    return c


def locators():
    return [('fx_crown', [0, CROWN_Y + 3, 0], 'cloth'), ('fx_pack', [0, 20, 0], 'pack'),
            ('fx_pilot', [0, 29, 0], 'pilot'), ('fx_tag', [0, -10, -8], 'tag'), ('fx_cradle', [0, -1, 0], 'harness'),
            ('fx_hem_l', [-CROWN - PANEL, CROWN_Y - 4, 0], 'panel_l'), ('fx_hem_r', [CROWN + PANEL, CROWN_Y - 4, 0], 'panel_r')]


def scale_standins():
    px = -26
    bs = [('standin_player', [px, 0, 0], None, None)]
    cs = [C('sp_legs', [px - 4, 0, -2], [px + 4, 12, 2], 'player', 'standin_player'),
          C('sp_body', [px - 4, 12, -2], [px + 4, 24, 2], 'player', 'standin_player'),
          C('sp_head', [px - 4, 24, -4], [px + 4, 32, 4], 'stand', 'standin_player')]
    return bs, cs


def main():
    sys.path.insert(0, MECHA)
    import bbbuild
    W.MATERIALS, W.ORDER, W.PARTS = MATERIALS, ORDER, PARTS
    scale = '--scale' in sys.argv
    bs, cs, locs = bones(), cubes(), locators()
    if scale:
        b2, c2 = scale_standins()
        bs, cs = bs + b2, cs + c2
    W.check(cs)
    if not scale:
        cp = W.coplanar([c for c in cs if not c[0].startswith('standin')], W.rest_frame_of(bs))
        sys.stderr.write('coplanar faces: %s\n' % (cp or 'none'))
    tex = (TEX_W, TEX_H) if not scale else (512, 512)
    model = os.path.join(HERE, 'golden_parachute.bbmodel')
    if not scale and os.path.exists(model) and '--repack' not in sys.argv:
        offs, regions = W.pin(cs, model, None, *tex)
    else:
        offs, regions = W.pack(cs, *tex)
    png = os.path.join(HERE, 'golden_parachute_placeholder.png' if not scale else '_preview_scale.png')
    W.draw(cs, offs, png, *tex)
    if not scale:
        W.uvmap([c for c in cs if not c[0].startswith('standin')], offs, os.path.join(HERE, 'golden_parachute_uv.png'),
                regions)
    out = sys.argv[sys.argv.index('--out') + 1] if '--out' in sys.argv else model
    bbbuild.build(out, 'golden_parachute', bs, cs, locs, offs, png, tex)
    sys.stderr.write('cords: rest %s, length %d\n' % (cord_rot(1, 1), cord_len()))
    sys.stderr.write('%d bones (%d exported), %d cubes (%d exported), %d locators\n'
                     % (len(bs), len([b for b in bs if not b[0].startswith('standin')]), len(cs),
                        len([c for c in cs if not c[4].startswith('standin')]), len(locs)))


if __name__ == '__main__':
    main()

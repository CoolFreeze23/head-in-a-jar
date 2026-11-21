"""The Redundancy Package (a seconded head's jar fired clear of a dying body): geometry, UV layout, placeholder sheet.

Derived from the Golden Parachute (art/entity/golden_parachute/golden_parachute_geo.py): the same bones under the same
names, the same locators, so the parachute's renderer and its eight clips (redundancy_package_anim.py) drive it with
only the model, clips and texture swapped. Entity space in pixels, 16 px to a block: origin at the jar's base (where
the game draws the jar), y up, the jar facing -Z.

    python art/entity/redundancy_package/redundancy_package_geo.py              # Life Support (wide) jar: .bbmodel
    python art/entity/redundancy_package/redundancy_package_geo.py --compact    # the Compact Jar's copy
    python art/entity/redundancy_package/redundancy_package_geo.py --scale --out X   # preview: a player beside it

What it is: the company's parting parcel. A small ivory envelope strapped onto the jar's lid by four crimson ties, each
tie clipped to a brass lug on the lid's edge (the collar's bayonet lugs, again); a brass swivel on the envelope; four
white cords up to a tiny gold brocade canopy, half the Golden Parachute (an octagon of four hinged panels round a
one-step crown with a 2 px apex vent and a crimson rosette, a crimson valance, four brass tassels), 20 px across: a
little more than the jar. A manila address label hangs on a cord from the back lug (never over the face inside, and
clear of the jar's side pipes). Nothing is scaled: half size is made of whole pixels, and the canopy opens by hinging its panels.

The jar is NOT part of this model: the game draws it at 1:1 at the origin (`jar_anchor`, with a stand-in for previews).
Wide jar (Life Support, Reinforced, Executive as the game draws them): base 14 x 1, glass 13 x 12, lid 14 x 1 with its
top at 14 (the game's is 14.3: the 11 px frame at 1.1 tall), valve 4 x 2. Compact: base 11, glass 10 x 11, lid 11 with
its top at 13, valve 3 x 2. Only the lugs, ties and straps and the height of everything above the lid differ.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.dirname(os.path.dirname(HERE))
MECHA = os.path.join(ART, 'mecha')
sys.path.insert(0, os.path.join(MECHA, 'wheelhouse'))
import wheelhouse_geo as W  # noqa: E402

TEX_W, TEX_H = 128, 128
COMPACT = '--compact' in sys.argv

# ---- the jar under it (stand-in and the lugs' fit) ----
JARS = {
    'wide': dict(lid_top=14.0, lid_half=7.0, base=7.0, glass=6.5, glass_top=13.0, valve=2.0, pack=3.0),
    'compact': dict(lid_top=13.0, lid_half=5.5, base=5.5, glass=5.0, glass_top=12.0, valve=1.5, pack=2.5),
}
JAR = JARS['compact' if COMPACT else 'wide']
LT, LH = JAR['lid_top'], JAR['lid_half']

# ---- rig numbers (shared with redundancy_package_anim.py) ----
PACK = JAR['pack']       # the envelope is 6 x 3 x 6 on the lid, round the valve (5 x 3 x 5 on the Compact Jar)
SWIVEL_Y = LT + 4.5      # where the cords meet, on top of the envelope's flap: the canopy's pivot (the code's swing)
SWIVEL_TOP = LT + 5.5    # the swivel is 3 tall, its foot inside the envelope (no gap once the flap is torn off)
CROWN_Y = SWIVEL_Y + 11.5  # the panels' hinge height (the crown is 1 px thick on top of it)
CROWN = 4.0              # the crown is 8 x 8; the panels hinge on its edges
PANEL = 6.0              # each panel reaches 6 px out (open, 20 across with the valance: half the Golden Parachute)
WIDTH = 7.0              # each panel is 14 across; the corners between panels are left open (the octagon)
OPEN = 32.0              # the panels' droop when open (degrees): the dome (the parachute's)
HEM = 2.0                # the valance hangs 2 px under each panel's outer edge
CORNERS = {'fl': (-1, -1), 'fr': (1, -1), 'rl': (-1, 1), 'rr': (1, 1)}
NAME = 'redundancy_package_compact' if COMPACT else 'redundancy_package'

MATERIALS = dict(W.MATERIALS)
MATERIALS['ribbon'] = ('7d323a', 255)    # placeholder crimson (flesh 1, as the parachute's)
MATERIALS['manila'] = ('cba66a', 255)
ORDER = list(W.ORDER) + ['ribbon', 'manila']
PARTS = [('canopy', ('crown', 'panel', 'hem', 'tassel', 'rosette')), ('cords', ('cord', 'bridle', 'pilot')),
         ('harness', ('lug', 'tie', 'strap', 'swivel')), ('pack', ('pack', 'flap', 'seal')), ('tag', ('tag',))]


def corner(sx, sz, droop=None):
    """Where a cord meets the canopy: the outer corner of the front (sz -1) or back (sz +1) panel's underside, at a
    given droop (degrees below level), in the cloth's frame."""
    d = math.radians(OPEN if droop is None else droop)
    r = CROWN + PANEL * math.cos(d)
    return (sx * WIDTH, CROWN_Y - 0.5 - PANEL * math.sin(d), sz * r)


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
        ('harness', [0, 0, 0], 'root', None),                # lugs, ties and straps: fixed to the jar's lid
        ('pack', [0, LT + 1, 0], 'harness', None),           # the envelope on the lid
        ('flap', [0, LT + 3, PACK], 'pack', None),           # its flap: rot x + opens it (hinged at the back)
        ('tag', [0, LT - 1, LH + 0.5], 'harness', None),     # the address label, from the back lug: rot z swings it
        ('canopy', [0, S, 0], 'root', None),                 # pivot at the swivel: the code's swing goes here
        ('cloth', [0, S, 0], 'canopy', None),                # the crown and panels: rises out of the envelope, falls
        ('pilot', [0, S + 1, 0], 'cloth', None),             # the pilot chute (modelled on the envelope; keyed up)
        ('panel_f', [0, Y, -CROWN], 'cloth', [-OPEN, 0, 0]),
        ('panel_b', [0, Y, CROWN], 'cloth', [OPEN, 0, 0]),
        ('panel_l', [-CROWN, Y, 0], 'cloth', [0, 0, OPEN]),
        ('panel_r', [CROWN, Y, 0], 'cloth', [0, 0, -OPEN]),
        ('tassels_l', [-CROWN - PANEL, Y - 2, 0], 'panel_l', None),    # shown or hidden by key (hidden afloat)
        ('tassels_r', [CROWN + PANEL, Y - 2, 0], 'panel_r', None),
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
    P = PACK
    c = [
        # ---- the envelope on the lid, round the valve; its flap and crimson seal; the swivel on the flap ----
        C('pack', [-P, LT, -P], [P, LT + 3, P], 'enamel', 'pack'),
        C('flap', [-P, LT + 3, -P], [P, LT + 4, P], 'enamel', 'flap'),
        C('seal', [-1.5, LT + 2.5, -P - 0.5], [1.5, LT + 4.5, -P + 0.5], 'ribbon', 'flap'),   # on the front strap's end
        C('swivel', [-1, LT + 2.5, -1], [1, SWIVEL_TOP, 1], 'brass', 'canopy'),
        # ---- the address label on its cord, hanging from the back lug behind the glass ----
        C('tag_cord', [-0.5, LT - 3, LH], [0.5, LT - 1, LH + 1], 'enamel', 'tag'),
        C('tag', [-2, LT - 6, LH], [2, LT - 3, LH + 1], 'manila', 'tag'),
        # ---- the pilot chute: a white bridle and a small brass dome on a crimson rim ----
        C('bridle', [-0.5, S + 0.5, -0.5], [0.5, S + 3.5, 0.5], 'enamel', 'pilot'),
        C('pilot_rim', [-2, S + 3.5, -2], [2, S + 4.5, 2], 'ribbon', 'pilot'),
        C('pilot_dome', [-1.5, S + 4.5, -1.5], [1.5, S + 5.5, 1.5], 'brass', 'pilot'),
        # ---- the canopy: a one-step gold crown round an open apex vent 2 px across, framed by a crimson rosette ----
        C('crown_f', [-CROWN, Y, -CROWN], [CROWN, Y + 1, -1], 'brass', 'cloth'),
        C('crown_b', [-CROWN, Y, 1], [CROWN, Y + 1, CROWN], 'brass', 'cloth'),
        C('crown_l', [-CROWN, Y, -1], [-1, Y + 1, 1], 'brass', 'cloth'),
        C('crown_r', [1, Y, -1], [CROWN, Y + 1, 1], 'brass', 'cloth'),
        C('rosette_f', [-2, Y + 1, -2], [2, Y + 2, -1], 'ribbon', 'cloth'),
        C('rosette_b', [-2, Y + 1, 1], [2, Y + 2, 2], 'ribbon', 'cloth'),
        C('rosette_l', [-2, Y + 1, -1], [-1, Y + 2, 1], 'ribbon', 'cloth'),
        C('rosette_r', [1, Y + 1, -1], [2, Y + 2, 1], 'ribbon', 'cloth'),
        # ---- stand-in jar (never exported) ----
        C('standin_jar_base', [-JAR['base'], 0, -JAR['base']], [JAR['base'], 1, JAR['base']], 'standm', 'standin_jar'),
        C('standin_jar_glass', [-JAR['glass'], 1, -JAR['glass']], [JAR['glass'], JAR['glass_top'], JAR['glass']],
          'standj', 'standin_jar'),
        C('standin_jar_lid', [-LH, LT - 1, -LH], [LH, LT, LH], 'standm', 'standin_jar'),
        C('standin_jar_valve', [-JAR['valve'], LT, -JAR['valve']], [JAR['valve'], LT + 2, JAR['valve']], 'standm',
          'standin_jar'),
        C('standin_head', [-4, 1.5, -4], [4, 9.5, 4], 'stand', 'standin_jar'),
    ]
    # the lugs (brass, on the lid's edge at the four sides), each with a crimson tie across the lid to the envelope and
    # a strap up the envelope's side (1 px proud of it)
    for side, (sx, sz) in (('f', (0, -1)), ('b', (0, 1)), ('l', (-1, 0)), ('r', (1, 0))):
        if sx:
            x0, x1 = sorted((sx * LH, sx * (LH + 1)))
            c.append(C('lug_' + side, [x0, LT - 1, -1], [x1, LT + 1, 1], 'brass', 'harness'))
            t0, t1 = sorted((sx * LH, sx * P))
            c.append(C('tie_' + side, [t0, LT, -1], [t1, LT + 1, 1], 'ribbon', 'harness'))
            s0, s1 = sorted((sx * P, sx * (P + 1)))
            c.append(C('strap_' + side, [s0, LT + 1, -1], [s1, LT + 3, 1], 'ribbon', 'harness'))
        else:
            z0, z1 = sorted((sz * LH, sz * (LH + 1)))
            c.append(C('lug_' + side, [-1, LT - 1, z0], [1, LT + 1, z1], 'brass', 'harness'))
            t0, t1 = sorted((sz * LH, sz * P))
            c.append(C('tie_' + side, [-1, LT, t0], [1, LT + 1, t1], 'ribbon', 'harness'))
            s0, s1 = sorted((sz * P, sz * (P + 1)))
            c.append(C('strap_' + side, [-1, LT + 1, s0], [1, LT + 3, s1], 'ribbon', 'harness'))
    # the four panels (gold brocade), each with a crimson valance hanging under its outer edge; four brass tassels in
    # all, at the ends of the side panels' valances
    w = WIDTH
    for side in ('f', 'b'):
        s = -1 if side == 'f' else 1
        z0, z1 = sorted((s * CROWN, s * (CROWN + PANEL)))
        h0, h1 = sorted((s * (CROWN + PANEL - 1), s * (CROWN + PANEL)))
        c += [C('panel_' + side, [-w, Y - 0.5, z0], [w, Y + 0.5, z1], 'brass', 'panel_' + side),
              C('hem_' + side, [-w, Y - 0.5 - HEM, h0], [w, Y - 0.5, h1], 'ribbon', 'panel_' + side)]
    for side in ('l', 'r'):
        s = -1 if side == 'l' else 1
        x0, x1 = sorted((s * CROWN, s * (CROWN + PANEL)))
        h0, h1 = sorted((s * (CROWN + PANEL - 1), s * (CROWN + PANEL)))
        c += [C('panel_' + side, [x0, Y - 0.5, -w], [x1, Y + 0.5, w], 'brass', 'panel_' + side),
              C('hem_' + side, [h0, Y - 0.5 - HEM, -w], [h1, Y - 0.5, w], 'ribbon', 'panel_' + side),
              C('tassel_%s_f' % side, [h0, Y - 0.5 - HEM - 2, -w], [h1, Y - 0.5 - HEM, -w + 1], 'brass', 'tassels_' + side),
              C('tassel_%s_b' % side, [h0, Y - 0.5 - HEM - 2, w - 1], [h1, Y - 0.5 - HEM, w], 'brass', 'tassels_' + side)]
    # the cords: white, each modelled straight up from the swivel and turned out to its corner by its bone
    for cn in CORNERS:
        c.append(C('cord_' + cn, [-0.5, S, -0.5], [0.5, S + L, 0.5], 'enamel', 'cord_' + cn))
    return c


def locators():
    """The parachute's locators, under the same names (its clips and renderer name them)."""
    return [('fx_crown', [0, CROWN_Y + 2, 0], 'cloth'), ('fx_pack', [0, LT + 4, 0], 'pack'),
            ('fx_pilot', [0, SWIVEL_Y + 5.5, 0], 'pilot'), ('fx_tag', [0, LT - 6, LH + 0.5], 'tag'),
            ('fx_cradle', [0, 0, 0], 'harness'),
            ('fx_hem_l', [-CROWN - PANEL, CROWN_Y - 2, 0], 'panel_l'), ('fx_hem_r', [CROWN + PANEL, CROWN_Y - 2, 0], 'panel_r')]


def scale_standins():
    px = -20
    bs = [('standin_player', [px, 0, 0], None, None)]
    cs = [C('sp_legs', [px - 4, 0, -2], [px + 4, 12, 2], 'player', 'standin_player'),
          C('sp_body', [px - 4, 12, -2], [px + 4, 24, 2], 'player', 'standin_player'),
          C('sp_head', [px - 4, 24, -4], [px + 4, 32, 4], 'stand', 'standin_player')]
    return bs, cs


def share_sheet(cs, wide_model):
    """The Compact copy paints from the same sheet: every cube the wide model has at the same size keeps its island;
    the others (the envelope, its ties, straps and seal, the stand-ins) go to space no wide island uses."""
    import json
    with open(wide_model, encoding='utf-8') as fh:
        wide = {e['name']: e for e in json.load(fh)['elements'] if e.get('type') == 'cube'}
    occ = [[False] * TEX_W for _ in range(TEX_H)]

    def mark(x, y, w, h):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                occ[yy][xx] = True

    def free(x, y, w, h):
        if x + w > TEX_W or y + h > TEX_H:
            return False
        return not any(occ[yy][xx] for yy in range(y, y + h) for xx in range(x, x + w))
    for e in wide.values():
        if not e['name'].startswith('standin'):
            w, h = W.box_uv_wh((e['name'], e['from'], e['to']))
            mark(e['uv_offset'][0], e['uv_offset'][1], w, h)
    offs, later = {}, []
    for c in cs:
        e = wide.get(c[0])
        if e and [abs(e['to'][i] - e['from'][i]) for i in range(3)] == W.size(c):
            offs[c[0]] = list(e['uv_offset'])
        else:
            later.append(c)
    for c in later:
        w, h = W.box_uv_wh(c)
        spot = next(([x, y] for y in range(TEX_H) for x in range(TEX_W) if free(x, y, w, h)), None)
        if spot is None:
            sys.exit('no free space for %s' % c[0])
        offs[c[0]] = spot
        mark(spot[0], spot[1], w, h)
    regions = {}
    for c in cs:
        y0, y1 = offs[c[0]][1], offs[c[0]][1] + W.box_uv_wh(c)[1]
        r = regions.get(c[3], (y0, y1))
        regions[c[3]] = (min(r[0], y0), max(r[1], y1))
    return offs, regions


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
    tex = (TEX_W, TEX_H)
    model = os.path.join(HERE, NAME + '.bbmodel')
    if COMPACT and not scale:
        offs, regions = share_sheet(cs, os.path.join(HERE, 'redundancy_package.bbmodel'))
    elif not scale and os.path.exists(model) and '--repack' not in sys.argv:
        offs, regions = W.pin(cs, model, None, *tex)
    else:
        offs, regions = W.pack(cs, *tex)
    png = os.path.join(HERE, NAME + '_placeholder.png' if not scale else '_preview_scale.png')
    W.draw(cs, offs, png, *tex)
    if not scale:
        W.uvmap([c for c in cs if not c[0].startswith('standin')], offs, os.path.join(HERE, NAME + '_uv.png'), regions)
    out = sys.argv[sys.argv.index('--out') + 1] if '--out' in sys.argv else model
    bbbuild.build(out, NAME, bs, cs, locs, offs, png, tex)
    sys.stderr.write('cords: rest %s, length %d\n' % (cord_rot(1, 1), cord_len()))
    sys.stderr.write('%d bones (%d exported), %d cubes (%d exported), %d locators\n'
                     % (len(bs), len([b for b in bs if not b[0].startswith('standin')]), len(cs),
                        len([c for c in cs if not c[4].startswith('standin')]), len(locs)))


if __name__ == '__main__':
    main()

"""Centrifuge (the Chair-o-plane): geometry, from the machine's design with the changes listed in the notes.

    python art/machines/centrifuge/centrifuge_geo.py     # writes centrifuge.bbmodel and its placeholder PNG
    python art/machines/centrifuge/centrifuge_anim.py    # then writes the clips into it (and the review copy)

A fairground chair-o-plane on a black-topped lab bench: the K12 bench (open shelf, drawer, lamp), a steel drum on the
worktop with a motor housing, a brass vial rack in front, a dark 4x4 pole holding a baked canopy (a hollow valance in
vertical yellow and enamel bands round a dark rubber ceiling, an enamel deck), and right under
the valance the rotor: one tier, a brass hub round the pole and four arms whose tips carry the hangers (a steel link,
a holder ring, a sample tube). Faces -Z. 32 px tall: the shared hidden top cell.

v2 vertical stack (px): worktop top 13; rack plate 13-14 (vials 0.25, sunk 1 px: tops at 17); drum 13-15; motor
housing 15-17; pole 17-30; tubes 20-23 (3 px over the vial tops); rings 23-24; links 24-26; arms 26-27, hub 25-28;
valance 28-32 with its ceiling at 30 (2 px deep dark); deck 31-32, 1 px in from the valance.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
import kit  # noqa: E402

PATH = os.path.join(HERE, 'centrifuge.bbmodel')
REVIEW = 'build/machines-review/centrifuge_review.bbmodel'

# hanger pivots (the arm's underside, radius 5) and the outward swing: (bone, pivot x z, channel axis index, sign).
# outward = the tube's foot moving away from the pole. Checked by render (_review_axes, _review_full): +rotX swings the
# north hanger to -Z, -rotX the south one to +Z, +rotZ the east one to +X, -rotZ the west one to -X.
HANGERS = [('hanger_n', (0, -5), 0, +1), ('hanger_e', (5, 0), 2, +1),
           ('hanger_s', (0, 5), 0, -1), ('hanger_w', (-5, 0), 2, -1)]
PIVOT_Y = 26
V0, V1 = 28, 32       # the valance


def bands(m, tag, axis, lo, hi, fixed, sz):
    """A straight valance wall along `axis` ('x' or 'z') from lo to hi, 1 px thick at `fixed`..`fixed+sz`, cut into
    2 px vertical bands alternating yellow and enamel (the fairground canopy; one part, so no flush flags)."""
    i = 0
    u = lo
    while u < hi:
        w = min(2, hi - u)
        mat = 'yellow' if i % 2 == 0 else 'enamel'
        if axis == 'x':
            f, t = [u, V0, fixed], [u + w, V1, fixed + sz]
        else:
            f, t = [fixed, V0, u], [fixed + sz, V1, u + w]
        m.box('canopy_%s_%d' % (tag, i), f, t, mat, part='canopy_wall')
        u += w
        i += 1


def canopy(m):
    """The big shape, baked, static (K8: it does not move, so no hazard stripes; the paint paints broad vertical
    fairground bands, here modelled as 2 px yellow and enamel boxes). A hollow valance (outline 14x10 crossed with
    10x14, walls 1 px thick, 4 px tall), a rubber ceiling 2 px up inside it (the ride's dark interior), an enamel deck
    on the ceiling 1 px in from the walls (a 1 px dark gutter), its top at 32."""
    bands(m, 'n', 'x', -5, 5, -7, 1)
    bands(m, 's', 'x', -5, 5, 6, 1)
    bands(m, 'w', 'z', -5, 5, -7, 1)
    bands(m, 'e', 'z', -5, 5, 6, 1)
    for sx in (-1, 1):
        for sz in (-1, 1):
            tag = ('n' if sz < 0 else 's') + ('w' if sx < 0 else 'e')
            # the stepped corner: x 4..5 z 5..6 and x 4..6 z 4..5 (mirrored)
            m.box('canopy_c%s1' % tag, [4 * sx, V0, 5 * sz], [5 * sx, V1, 6 * sz], 'yellow', part='canopy_wall')
            m.box('canopy_c%s2' % tag, [4 * sx, V0, 4 * sz], [6 * sx, V1, 5 * sz], 'yellow', part='canopy_wall')
    # the ceiling fills the inner outline (12x8 crossed with 8x12) at y 30-31, split so no two faces overlap
    m.box('canopy_ceiling', [-6, 30, -4], [6, 31, 4], 'rubber', part='canopy_ceiling')
    m.box('canopy_ceiling_n', [-4, 30, -6], [4, 31, -4], 'rubber', part='canopy_ceiling')
    m.box('canopy_ceiling_s', [-4, 30, 4], [4, 31, 6], 'rubber', part='canopy_ceiling')
    # the deck: crossed 10x1x6 / 6x1x10 on the ceiling, 1 px in from the walls all round (a 1 px dark gutter), its
    # top at 32 with the walls' (not touching). v1's brass feed collar is gone: the rotor needed that pixel so the
    # tubes clear the vials by 2 px; a hopper above sits on the deck (the top cell's top face).
    m.box('canopy_deck', [-5, 31, -3], [5, 32, 3], 'enamel', part='canopy_deck')
    m.box('canopy_deck_n', [-3, 31, -5], [3, 32, -3], 'enamel', part='canopy_deck')
    m.box('canopy_deck_s', [-3, 31, 3], [3, 32, 5], 'enamel', part='canopy_deck')


def model():
    m = kit.Model('centrifuge', budget=7)

    # ---- the lower half: the K12 bench with its K5 drawer and K3 lamp (shelf floor dsteel since the kit's change) --
    drawer_spec = kit.bench(m)
    # (v1's rubber shelf liner is gone: with the kit's floor now dsteel, its lit front edge was the one light line
    # in the opening)
    # a second pull 1 px proud of the kit's pull (the pull reads 2 deep from the three-quarter; a 2x2 face would
    # meet the 3 px drawer front's top or bottom edge flush)
    m.box('drawer_pull_2', [-3, 9, -6], [-1, 10, -5], 'brass', 'drawer', part='drawer_pull')

    # ---- K9 flag on the right side (+X), hinged on the worktop's side face (x 8): the arm lies along the worktop's
    # edge at rest (x 8..9, y 10-12, z -3..3; plate x 9..11, y 9-13) and stands up beside the worktop with its plate
    # at y 13-16, above the worktop, seen from the front three-quarter. Never inside the worktop (x <= 8).
    flag_spec = kit.flag(m, 8, 10, 3, side='east')

    # ---- on the worktop: the drum (K2 crossed, steel), the motor housing, the vial rack in front ---------------
    kit.casing(m, 'drum', -5, 5, -5, 5, 13, 15, mat='steel')
    m.box('housing', [-3, 15, -3], [3, 17, 3], 'steel')
    # the rack: one brass plate with four holes (texture); the vials stand sunk 1 px in it (anchors at y 13)
    m.box('rack', [-7, 13, -7], [7, 14, -5], 'brass')

    # ---- the pole: dark, 4x4, from the housing to the canopy's ceiling (the canopy never floats) -----------------
    m.box('post', [-2, 17, -2], [2, 30, 2], 'dsteel')

    canopy(m)

    # ---- the rotor (code: rotY accumulated, rotX the imbalance tilt). One tier right under the valance: a hub
    # round the pole and four arms 4 wide reaching to radius 7 (under the valance's edge, 1 px below it)
    m.bone('rotor', [0, PIVOT_Y, 0], code=('rotation',))
    m.box('hub', [-3, 25, -3], [3, 28, 3], 'brass', 'rotor', part='spider')
    m.box('arm_e', [3, 26, -2], [7, 27, 2], 'brass', 'rotor', part='spider')
    m.box('arm_w', [-7, 26, -2], [-3, 27, 2], 'brass', 'rotor', part='spider')
    m.box('arm_n', [-2, 26, -7], [2, 27, -3], 'brass', 'rotor', part='spider')
    m.box('arm_s', [-2, 26, 3], [2, 27, 7], 'brass', 'rotor', part='spider')

    # ---- the hangers (code: the outward swing about the arm's underside). link, holder ring, the tube -----------
    for bone, (px, pz), ax, sg in HANGERS:
        m.bone(bone, [px, PIVOT_Y, pz], 'rotor', code=('rotation',))
        p = bone
        m.box(bone + '_link', [px - 1, 24, pz - 1], [px + 1, 26, pz + 1], 'steel', bone, part=p)
        # rings steel (brass rings read as a second crossbar tier); hanger_n's is copper: the marked hanger, so the
        # rotation reads at low speed
        m.box(bone + '_ring', [px - 2, 23, pz - 2], [px + 2, 24, pz + 2], 'copper' if bone == 'hanger_n' else 'steel',
              bone, part=p)
        # the tube: hidden by code (cubes 2 and 3 of the bone) when this hanger has no sample
        # 3 tall (v1: 4): with the 2 px link a 4 px tube's foot reaches 11.6 px at 60 degrees, past the render box
        m.box(bone + '_glass', [px - 1, 22, pz - 1], [px + 1, 23, pz + 1], 'jglass', bone, part=p)
        m.box(bone + '_sample', [px - 1, 20, pz - 1], [px + 1, 22, pz + 1], 'sample', bone, part=p)
        m.locator(bone.replace('hanger', 'tube'), [px, 20, pz], bone)

    # ---- locators ----------------------------------------------------------------------------------------------
    m.locator('whine', [0, 21, 0], None)          # the spin loop's sound source (between the hangers)
    m.locator('rack', [0, 17, -6], None)          # the finish: over the rack where the vials appear
    m.locator('drawer_front', [-2, 9, -5], None)  # the drawer's sounds and the refusal rattle

    # ---- contents (BER item anchors; stand-ins never exported) -------------------------------------------------
    # scale 0.25 (the textures are 15-16 rows tall: 4 px), bottoms sunk 1 px into the rack plate (y 13): tops at 17,
    # 3 px under the tubes' feet (20) in the front view
    note_dna = 'DNA Fraction, FIXED, standing upright facing -Z, scale 0.25, bottom at y 13 (sunk 1 px in the rack)'
    note_pl = 'Plasma Vial (or glass bottle), FIXED, upright facing -Z, scale 0.25, bottom at y 13 (sunk in the rack)'
    m.anchor('vial_1', [-5, 13, -6], note_dna + '; shown for slot 1 count >= 1', standin=((2, 4, 1), 'standi'))
    m.anchor('vial_2', [-2, 13, -6], note_dna + '; shown for slot 1 count >= 2', standin=((2, 4, 1), 'standi'))
    m.anchor('vial_3', [2, 13, -6], note_pl + '; shown for slot 2 count >= 1', standin=((2, 4, 1), 'stand'))
    m.anchor('vial_4', [5, 13, -6], note_pl + '; shown for slot 2 count >= 2', standin=((2, 4, 1), 'stand'))
    # the balance sign: BER quads 6x2 on the valance's front and back faces, 0.5 px proud, drawn only while spinning
    # with 1-3 samples (texture "PLEASE BALANCE ROTOR")
    m.anchor('balance_front', [0, 29, -7.5], 'quad 6x2 x -3..3, y 29-31, at z -7.5 facing -Z; only while unbalanced')
    m.anchor('balance_back', [0, 29, 7.5], 'quad 6x2 x -3..3, y 29-31, at z 7.5 facing +Z; only while unbalanced')
    m.group('standin_balance', None, hidden=True)     # shown only for the unbalanced review frame
    m.box('si_balance_front', [-3, 29, -8], [3, 31, -7], 'standi', 'standin_balance')
    m.box('si_balance_back', [-3, 29, 7], [3, 31, 8], 'standi', 'standin_balance')

    # ---- the cell shapes (Java cell coordinates; upper = the machine_top cell) ---------------------------------
    m.shape('lower', (0, 0, 0, 16, 13, 16))      # the bench (kick, ends, shelf, band, drawer, worktop)
    m.shape('lower', (3, 13, 3, 13, 15, 13))     # the drum
    m.shape('lower', (1, 13, 1, 15, 14, 3))      # the rack (a click on a vial must answer)
    m.shape('lower', (5, 15, 5, 11, 16, 11))     # the housing's lower half
    m.shape('upper', (5, 0, 5, 11, 1, 11))       # the housing's upper half
    m.shape('upper', (6, 1, 6, 10, 14, 10))      # the pole
    m.shape('upper', (1, 3, 1, 15, 12, 15))      # spider and hangers at rest
    m.shape('upper', (1, 12, 1, 15, 16, 15))     # the canopy
    return m, drawer_spec, flag_spec


if __name__ == '__main__':
    m, _, _ = model()
    m.write(PATH)
    print(kit.shapes_java(m))

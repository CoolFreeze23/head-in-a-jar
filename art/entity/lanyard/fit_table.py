"""The per-species staff fit: where StaffLayer draws the lanyard, the Hard Hat and the carried crate on each hireable
vanilla creature. Writes `assets/headinajar/staff_fit/<species>.json` (never edited by hand).

    python art/entity/lanyard/fit_table.py            # writes every file
    python art/entity/lanyard/fit_table.py --check    # rebuilds in memory and compares with the files

How a placement is written here: in the creature's MODEL space at rest (px, y down, the front is -z, x to the
creature's left: the space vanilla's model parts are built in, before the entity renderer flips it), because that is
where "round the neck" and "on the back" are easy to read and check. The script then moves every placement into its
bone's own space with the rest poses in vanilla_bones.py, so the files say what StaffLayer needs: a bone, and
positions in that bone's local space.

The lanyard (see the format in the notes): a 1 px cord ring round the neck, hugging the neck's section 1 px out, and a
hang (two 1 px strands side by side, then the badge) that swings. Ring kinds:
- 'h': horizontal ring (an upright neck: bipeds, llamas, camels), at y0..y0+1 round x0..x1, z0..z1; the hang leaves
  the ring's front;
- 'v': vertical ring (a neck that runs forward: most quadrupeds; the ring sits on the back of the head and moves with
  it), at z0..z0+1 round x0..x1, y0..y1; the hang leaves the ring's bottom (the badge hangs under the chin);
- either kind with `hang='left'`: the badge hangs on the creature's left side of the ring, facing out (low creatures
  whose chin is on the ground);
- `tilt` (degrees about x) turns the ring with a sloped neck (horses, hoglins); the hang is turned back upright.
Badges are 'full' (rel60's 5 x 5) or 'mini' (3 x 3) for creatures under 5 px across the chest.

The hat: the baked size that fits the head (hard_hat_geo.SIZES), its brim's underside centre at `at`; by default the
dome's top clears the head's top by 1 px: at = (cx, top + dome height, cz).
The crate: the baked crate's underside centre at `at`; or a vanilla way to carry; or None (does not haul).
"""
import json
import math
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, '..', '..', '..'))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(ROOT, 'art', 'item', 'hard_hat'))
from vanilla_bones import BONES  # noqa: E402
import hard_hat_geo  # noqa: E402

OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'headinajar', 'staff_fit')
FORMAT = 1

# ---------------------------------------------------------------- how StaffLayer reaches a part from the model
# 'fields': the first part is a Java field of the model class (Mojang names; the rest of the path is getChild below it)
# 'root': HierarchicalModel.root() is the baked root (the whole path is getChild below it)
# 'root_child': HierarchicalModel.root() is the baked root's child "root" (the path below it drops "root/")
HUMANOID = ('fields', {'head': 'head', 'body': 'body'})
QUAD = ('fields', {'head': 'head', 'body': 'body'})
ACCESS = {
    'allay': ('root_child', 'AllayModel'), 'armadillo': ('root', 'ArmadilloModel'),
    'axolotl': (('fields', {'body': 'body'}), 'AxolotlModel'), 'bee': (('fields', {'bone': 'bone'}), 'BeeModel'),
    'blaze': ('root', 'BlazeModel'), 'bogged': (HUMANOID, 'BoggedModel'), 'camel': ('root', 'CamelModel'),
    'cat': (QUAD, 'CatModel'), 'cave_spider': ('root', 'SpiderModel'), 'chicken': (QUAD, 'ChickenModel'),
    'cow': (QUAD, 'CowModel'), 'dolphin': ('root', 'DolphinModel'),
    'donkey': (('fields', {'head_parts': 'headParts', 'body': 'body'}), 'ChestedHorseModel'),
    'drowned': (HUMANOID, 'DrownedModel'), 'enderman': (HUMANOID, 'EndermanModel'),
    'endermite': ('root', 'EndermiteModel'), 'evoker': ('root', 'IllagerModel'), 'fox': (QUAD, 'FoxModel'),
    'frog': ('root_child', 'FrogModel'), 'ghast': ('root', 'GhastModel'), 'glow_squid': ('root', 'SquidModel'),
    'giant': (HUMANOID, 'GiantZombieModel'), 'goat': (QUAD, 'GoatModel'), 'hoglin': (QUAD, 'HoglinModel'),
    'horse': (('fields', {'head_parts': 'headParts', 'body': 'body'}), 'HorseModel'),
    'husk': (HUMANOID, 'ZombieModel'), 'illusioner': ('root', 'IllagerModel'),
    'iron_golem': ('root', 'IronGolemModel'), 'llama': (QUAD, 'LlamaModel'), 'magma_cube': ('root', 'LavaSlimeModel'),
    'mooshroom': (QUAD, 'CowModel'),
    'mule': (('fields', {'head_parts': 'headParts', 'body': 'body'}), 'ChestedHorseModel'),
    'ocelot': (QUAD, 'OcelotModel'), 'panda': (QUAD, 'PandaModel'), 'parrot': ('root', 'ParrotModel'),
    'phantom': ('root', 'PhantomModel'), 'pig': (QUAD, 'PigModel'), 'piglin': (HUMANOID, 'PiglinModel'),
    'piglin_brute': (HUMANOID, 'PiglinModel'), 'pillager': ('root', 'IllagerModel'),
    'polar_bear': (QUAD, 'PolarBearModel'), 'rabbit': (QUAD, 'RabbitModel'), 'ravager': ('root', 'RavagerModel'),
    'sheep': (QUAD, 'SheepModel'), 'silverfish': ('root', 'SilverfishModel'), 'skeleton': (HUMANOID, 'SkeletonModel'),
    'skeleton_horse': (('fields', {'head_parts': 'headParts', 'body': 'body'}), 'HorseModel'),
    'slime': ('root', 'SlimeModel'), 'sniffer': ('root_child', 'SnifferModel'),
    'snow_golem': ('root', 'SnowGolemModel'), 'spider': ('root', 'SpiderModel'), 'squid': ('root', 'SquidModel'),
    'stray': (HUMANOID, 'SkeletonModel'), 'strider': ('root', 'StriderModel'),
    'trader_llama': (QUAD, 'LlamaModel'), 'turtle': (QUAD, 'TurtleModel'), 'villager': ('root', 'VillagerModel'),
    'vindicator': ('root', 'IllagerModel'), 'wandering_trader': ('root', 'VillagerModel'),
    'witch': ('root', 'WitchModel'), 'wither_skeleton': (HUMANOID, 'SkeletonModel'),
    'wolf': (('fields', {'head': 'head', 'body': 'body', 'upper_body': 'upperBody'}), 'WolfModel'),
    'zoglin': (QUAD, 'HoglinModel'), 'zombie': (HUMANOID, 'ZombieModel'),
    'zombie_horse': (('fields', {'head_parts': 'headParts', 'body': 'body'}), 'HorseModel'),
    'zombie_villager': (HUMANOID, 'ZombieVillagerModel'), 'zombified_piglin': (HUMANOID, 'PiglinModel'),
}
# renderers that already carry HumanoidArmorLayer: the armour model draws the Hard Hat on these
ARMOR = {'giant', 'zombie', 'husk', 'drowned', 'zombie_villager', 'skeleton', 'stray', 'wither_skeleton', 'bogged', 'piglin',
         'piglin_brute', 'zombified_piglin'}


# ---------------------------------------------------------------- 4x4 maths (rows), px
def _mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def _t(x, y, z):
    return [[1, 0, 0, x], [0, 1, 0, y], [0, 0, 1, z], [0, 0, 0, 1]]


def _rx(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    return [[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]]


def _ry(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    return [[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]]


def _rz(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    return [[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]


def _rot(r):
    """ZYX like ModelPart.translateAndRotate: R = Rz Ry Rx (x applied first)."""
    return _mul(_rz(r[2]), _mul(_ry(r[1]), _rx(r[0])))


def _inv(m):
    """Inverse of a rigid transform."""
    r = [[m[j][i] for j in range(3)] for i in range(3)]
    t = [-sum(r[i][k] * m[k][3] for k in range(3)) for i in range(3)]
    return [r[0] + [t[0]], r[1] + [t[1]], r[2] + [t[2]], [0, 0, 0, 1]]


def _euler(m):
    """Degrees (x, y, z) with R = Rz Ry Rx."""
    b = math.asin(max(-1.0, min(1.0, -m[2][0])))
    a = math.atan2(m[2][1], m[2][2])
    c = math.atan2(m[1][0], m[0][0])
    return [math.degrees(a), math.degrees(b), math.degrees(c)]


def bone_matrix(species, path):
    m = _t(0, 0, 0)
    for x, y, z, rx, ry, rz in BONES[species][path]:
        m = _mul(m, _mul(_t(x, y, z), _rot((rx, ry, rz))))
    return m


def _r(v, n=3):
    out = []
    for x in v:
        x = round(x, n)
        out.append(0.0 if x == 0 else x)
    return out


def bone_ref(species, path):
    kind, _ = ACCESS[species]
    if kind == 'root':
        return {'field': 'root', 'path': path}
    if kind == 'root_child':
        assert path.startswith('root/'), path
        return {'field': 'root', 'path': path[5:]}
    fields = kind[1]
    first, _, rest = path.partition('/')
    return {'field': fields[first], 'path': rest}


def placement(species, path, at, rot=(0, 0, 0)):
    """A frame given in model space at rest (origin `at`, rotation `rot` degrees) as at/rot in the bone's space."""
    f = _mul(_t(*at), _rot(rot))
    local = _mul(_inv(bone_matrix(species, path)), f)
    return _r([local[0][3], local[1][3], local[2][3]]), _r(_euler(local), 2)


# ---------------------------------------------------------------- the lanyard
CORD = 0.5   # the cord's thickness off the skin (it is 1 px wide along the skin)


def ring_boxes(kind, a0, b0, b1, c0, c1):
    """Cord boxes of a ring hugging a rectangle CORD px out, relative to the ring's own frame (see lanyard()).
    'h': plane xz at y 0..1, rect x b0..b1, z c0..c1.  'v': plane xy at z 0..1, rect x b0..b1, y c0..c1."""
    t = CORD
    if kind == 'h':
        return [[b0 - t, 0, c0 - t, b1 + t, 1, c0], [b0 - t, 0, c1, b1 + t, 1, c1 + t],
                [b0 - t, 0, c0, b0, 1, c1], [b1, 0, c0, b1 + t, 1, c1]]
    return [[b0 - t, c0 - t, 0, b1 + t, c0, 1], [b0 - t, c1, 0, b1 + t, c1 + t, 1],
            [b0 - t, c0, 0, b0, c1, 1], [b1, c0, 0, b1 + t, c1, 1]]


def lanyard(species, bone, kind, at, rect, drop=2, badge='full', hang='front', tilt=0.0, sway=1.0):
    """at: the ring frame's origin in model space (for 'h' the ring's top face centre's y and the neck's centre x, z;
    for 'v' the ring's back face z and the neck's centre x, y); rect: the neck section (b0, b1, c0, c1) relative to
    `at`. Everything below is relative to that frame (upright model axes, turned by `tilt` about x)."""
    b0, b1, c0, c1 = rect
    cord = ring_boxes(kind, 0, b0, b1, c0, c1)
    cx = (b0 + b1) / 2
    if hang == 'left':
        # the badge hangs on the creature's left side of the ring, facing out (+x)
        if kind == 'h':
            pivot = [b1 + CORD, 1, (c0 + c1) / 2]
        else:
            pivot = [b1 + CORD, (c0 + c1) / 2, 1]
        hang_rot = [-tilt, -90.0, 0]
    elif kind == 'h':
        pivot = [cx, 1, c0]
        hang_rot = [-tilt, 0, 0]
    else:
        pivot = [cx, c1 + 1, 0]
        hang_rot = [-tilt, 0, 0]
    # the two sides of the lanyard, 1 px apart, meeting the clip's ends
    strands = [[-1.5, 0, -CORD, -0.5, drop, 0], [0.5, 0, -CORD, 1.5, drop, 0]] if drop > 0 else []
    if badge == 'mini':
        strands = [[-0.5, 0, -CORD, 0.5, drop, 0]] if drop > 0 else []
    frame_at, frame_rot = placement(species, bone, at, (tilt, 0, 0))
    return {
        'bone': bone_ref(species, bone),
        'at': frame_at, 'rot': frame_rot,
        'cord': [_r(b) for b in cord],
        'hang': {'pivot': _r(pivot), 'rot': _r(hang_rot, 2), 'cord': [_r(b) for b in strands],
                 'badge': {'at': _r([0, drop - 1, -CORD - 0.01]), 'size': badge}, 'sway': sway},
        '_model': {'at': list(at), 'tilt': tilt},
    }


def collar(species, bone, at, half_w, front, back, tilt=55.0, drop=1, badge='full'):
    """A quadruped's collar: a ring round the neck where the head meets the body, its plane tilted forward `tilt`
    degrees (over the shoulders behind the head, under the throat in front), the badge hanging from its lowest
    front point. at: model space, at the head's back face, three quarters of the way down the head."""
    return lanyard(species, bone, 'h', at, (-half_w + CORD, half_w - CORD, front, back), drop=drop, badge=badge,
                   tilt=tilt)


def hat(species, bone, top, size, tilt=0.0, at=None, rot=None, local=None, perch=False):
    """top: the head's top centre in model space (x, y, z); the brim's underside goes dome-height below it, or on the
    head's top (perch: a dome narrower than the head would leave the head showing through its steps).
    local: (x, y, z) of the brim's underside centre in the bone's own space, the hat square to the bone (a head that
    is turned at rest, like a horse's, wears its hat turned with it)."""
    if local is not None:
        return {'bone': bone_ref(species, bone), 'at': _r(local), 'rot': _r(rot or (0, 0, 0), 2), 'size': size}
    dh = hard_hat_geo.SPEC[size]['dh']
    p = at if at is not None else (top[0], top[1] + (0 if perch else dh), top[2])
    a, r = placement(species, bone, p, rot if rot is not None else (tilt, 0, 0))
    return {'bone': bone_ref(species, bone), 'at': a, 'rot': r, 'size': size}


def crate(species, bone, at, yaw=0.0, tilt=0.0, hat_lift=0):
    """hat_lift: the crate rides where the hat sits (on top of the head); while both are worn StaffLayer raises the
    hat by this many px (model up), so the hat sits on the crate."""
    a, r = placement(species, bone, at, (tilt, yaw, 0))
    out = {'bone': bone_ref(species, bone), 'at': a, 'rot': r}
    if hat_lift:
        out['hat_lift'] = hat_lift
    return out


# A model that draws itself through a transform of its own (RabbitModel.renderToBuffer: an adult at scale 0.6, then
# down 1 block in that scale): StaffLayer applies it before every bone chain, so the fit is written in the model's
# own px like every other species. 'offset' is px in the scaled space.
RABBIT_POSE = {'scale': 0.6, 'offset': [0, 16, 0]}

ARMOR_HAT = {'armor': True}
HANDS = {'vanilla': 'hands'}
MOUTH = {'vanilla': 'mouth'}
BLOCK = {'vanilla': 'block'}

# a biped's crate: held in both hands (HumanoidModelCarryMixin sets the arms forward, see the notes), in front of the
# body at hand height; its sides meet the hands
BIPED_CRATE = (0, 11, -9)


def biped(s, scale_head=False):
    return dict(
        lanyard=lanyard(s, 'body', 'h', (0, 0, 0), (-4, 4, -2, 2), drop=3),
        hat=ARMOR_HAT,
        crate=crate(s, 'body', BIPED_CRATE),
    )


def enderman_crate():
    """An enderman carries its load the vanilla way: a barrel as its carried block, in vanilla's carried-block pose.
    While it has no carried block to show (a carry the server has not dressed yet), StaffLayer draws the crate where
    vanilla's carried block sits (CarriedBlockLayer: down 11, forward 12, turned 20 about x then 45 about y; the
    block's centre at (0, 10.06, -12.34) in model px), the crate's centre on that centre."""
    m = _mul(_rx(20), _ry(45))
    up = [m[i][1] * 2.5 for i in range(3)]   # half the crate's height along its own up (bone +y is down)
    centre = (0, 11 - 0.9397 * 1, -12 - 0.3420 * 1)
    at = tuple(centre[i] + up[i] for i in range(3))
    a, r = placement('enderman', 'body', at, _euler(m))
    return {'vanilla': 'block', 'bone': bone_ref('enderman', 'body'), 'at': a, 'rot': r}


def villager_like(s, crate_ok=True):
    # the badge hangs below the crossed arms (the strands pass behind them), on the robe
    return dict(
        lanyard=lanyard(s, 'body', 'h', (0, 0, 0), (-4, 4, -3, 3), drop=9),
        hat=hat(s, 'head', (0, -10, 0), 'l'),
        crate=crate(s, 'body', (0, 7, -7)) if crate_ok else None,
    )


def table():
    F = {}
    for s in ('zombie', 'husk', 'drowned', 'skeleton', 'stray', 'wither_skeleton', 'bogged', 'piglin', 'piglin_brute',
              'zombified_piglin'):
        F[s] = biped(s)
    F['giant'] = biped('giant')
    F['zombie_villager'] = dict(lanyard=lanyard('zombie_villager', 'body', 'h', (0, 0, 0), (-4, 4, -3, 3), drop=3),
                                hat=ARMOR_HAT, crate=crate('zombie_villager', 'body', (0, 11, -10)))
    F['enderman'] = dict(lanyard=lanyard('enderman', 'body', 'h', (0, -14, 0), (-4, 4, -2, 2), drop=3),
                         hat=hat('enderman', 'head', (0, -21, 0), 'l'), crate=enderman_crate())
    for s in ('villager', 'wandering_trader', 'witch'):
        F[s] = villager_like(s)
    F['witch']['hat'] = hat('witch', 'head', (0.5, -18, 1), 's')
    F['vindicator'] = villager_like('vindicator')
    # a pillager's arms hang at its sides (it never crosses them): it holds the crate in both hands like a biped
    # (StaffPoses brings an illager's free arms forward while it carries)
    F['pillager'] = villager_like('pillager')
    F['pillager']['crate'] = crate('pillager', 'body', BIPED_CRATE)
    for s in ('evoker', 'illusioner'):
        F[s] = villager_like(s, crate_ok=False)
    F['iron_golem'] = dict(lanyard=lanyard('iron_golem', 'head', 'h', (0, -9, -3.5), (-4, 4, -4, 4), drop=2),
                           hat=hat('iron_golem', 'head', (0, -19, -1.5), 'l'),
                           crate=crate('iron_golem', 'body', (7, -9, 0)))
    F['snow_golem'] = dict(lanyard=lanyard('snow_golem', 'upper_body', 'h', (0, 3, 0), (-5, 5, -5, 5), drop=2),
                           hat=hat('snow_golem', 'head', (0, -6.5, 0), 'xl'),
                           crate=crate('snow_golem', 'head', (0, -6.5, 0), hat_lift=8))
    F['allay'] = dict(lanyard=lanyard('allay', 'root/body', 'h', (0, 19.5, 0), (-1.5, 1.5, -1, 1), drop=1,
                                      badge='mini'),
                      hat=hat('allay', 'root/head', (0, 14.5, 0), 's'), crate=HANDS)
    F['blaze'] = dict(lanyard=lanyard('blaze', 'head', 'h', (0, 3, 0), (-4, 4, -4, 4), drop=1),
                      hat=hat('blaze', 'head', (0, -4, 0), 'l'), crate=crate('blaze', 'head', (0, 11, 0)))

    # quadrupeds: the ring on the back of the head, the badge under the chin
    F['cow'] = dict(lanyard=collar('cow', 'body', (0, 6, -8.5), 5, -6, 5),
                    hat=hat('cow', 'head', (0, 0, -11), 's'), crate=crate('cow', 'body', (0, 2, 1)))
    F['mooshroom'] = dict(lanyard=collar('mooshroom', 'body', (0, 6, -8.5), 5, -6, 5),
                          hat=hat('mooshroom', 'head', (0, 0, -11), 's'), crate=crate('mooshroom', 'body', (0, 2, 1)))
    F['pig'] = dict(lanyard=collar('pig', 'body', (0, 14, -6.5), 5, -6, 5),
                    hat=hat('pig', 'head', (0, 8, -10), 'l'), crate=crate('pig', 'body', (0, 10, 1)))
    F['sheep'] = dict(lanyard=collar('sheep', 'body', (0, 7, -10.25), 5, -6, 5),
                      hat=hat('sheep', 'head', (0, 1, -10), 'm', at=(0, 4, -10)), crate=crate('sheep', 'body', (0, 4, 1)))
    F['wolf'] = dict(lanyard=collar('wolf', 'upper_body', (0, 15, -6.5), 4, -5, 4),
                     hat=hat('wolf', 'head', (0, 10.5, -7), 's'), crate=crate('wolf', 'body', (0, 11, 4)))
    # fox, cat, ocelot: a collar round the front of the body, just behind the head (where a neck would be), hugging
    # it; the badge hangs below it in front of the forelegs. Hats sized to the head: the brim as wide as the head
    # (fox) or a pixel wider (cat, ocelot); the ears poke up beside the dome.
    F['fox'] = dict(lanyard=lanyard('fox', 'body', 'v', (0, 16.5, -2), (-3, 3, -3, 3), drop=1, badge='mini'),
                    hat=hat('fox', 'head', (0, 14.5, -5), 's', perch=True), crate=MOUTH)
    for s in ('cat', 'ocelot'):
        F[s] = dict(lanyard=lanyard(s, 'body', 'v', (0, 17, -7), (-2, 2, -3, 3), drop=0, badge='mini'),
                    hat=hat(s, 'head', (0, 13, -9.5), 'xs', perch=True), crate=None)
    F['chicken'] = dict(lanyard=lanyard('chicken', 'head', 'h', (0, 12, -4.5), (-2, 2, -1.5, 1.5), drop=2),
                        hat=hat('chicken', 'head', (0, 9, -4.5), 's'), crate=crate('chicken', 'body', (0, 13, 1)))
    # the rabbit's model draws an adult at 0.6 (RABBIT_POSE); everything here is in its unscaled model space
    F['rabbit'] = dict(lanyard=lanyard('rabbit', 'head', 'v', (0, 14, -2), (-2.5, 2.5, -2, 2), drop=0, badge='mini'),
                       hat=hat('rabbit', 'head', (0, 12, -3.5), 'xs', perch=True), crate=crate('rabbit', 'body', (0, 14, 4)),
                       render=RABBIT_POSE)
    for s in ('horse', 'donkey', 'mule', 'skeleton_horse', 'zombie_horse'):
        F[s] = dict(lanyard=lanyard(s, 'head_parts', 'h', (0, 1.5, -12), (-3, 3, -3, 3), drop=2, tilt=-60),
                    hat=hat(s, 'head_parts/head', None, 'm', local=(0, -8.5, 2), rot=(-20, 0, 0)),
                    crate=crate(s, 'body', (0, 3, 0)))
    for s in ('llama', 'trader_llama'):
        F[s] = dict(lanyard=lanyard(s, 'head', 'h', (0, 0, -9), (-4, 4, -3, 3), drop=2),
                    hat=hat(s, 'head', (0, -9, -9), 'l'), crate=crate(s, 'body', (0, 2, 1)))
    F['camel'] = dict(lanyard=lanyard('camel', 'body/head', 'h', (0, -12, -21.5), (-3.5, 3.5, -3.5, 3.5), drop=2),
                      hat=hat('camel', 'body/head', (0, -20, -24.5), 'm'), crate=crate('camel', 'body', (0, -8, 8)))
    F['goat'] = dict(lanyard=lanyard('goat', 'body', 'v', (0.5, 13, -8), (-5.5, 5.5, -7, 7), drop=0, hang='left'),
                     hat=hat('goat', 'head', (0.5, 6, -12), 's'), crate=crate('goat', 'body', (0.5, 6, 1)))
    F['panda'] = dict(lanyard=collar('panda', 'body', (0, 14, -13.5), 7.5, -6, 5),
                      hat=hat('panda', 'head', (0, 6.5, -16.5), 'l'), crate=crate('panda', 'body', (0, 3.5, 1)))
    F['polar_bear'] = dict(lanyard=collar('polar_bear', 'body', (0, 12, -13.5), 4.5, -5, 4.5),
                           hat=hat('polar_bear', 'head', (0, 7, -15.5), 'm'), crate=crate('polar_bear', 'body', (0, 5, 1)))
    for s in ('hoglin', 'zoglin'):
        F[s] = dict(lanyard=lanyard(s, 'body', 'v', (0, 7, -11), (-8, 8, -7, 7), drop=0),
                    hat=hat(s, 'head', (0, 0, 0), 'xl', at=(0, 4, -16)), crate=crate(s, 'body', (0, 0, 8)))
    F['ravager'] = dict(lanyard=lanyard('ravager', 'neck/head', 'v', (0, 0, -11), (-8, 8, -11, 11), drop=0),
                        hat=hat('ravager', 'neck/head', (0, -11, -19.5), 'xl'), crate=crate('ravager', 'body', (0, -12, 2)))
    F['sniffer'] = dict(lanyard=lanyard('sniffer', 'root/bone/body', 'v', (0, 5.5, -19), (-12.5, 12.5, -14.5, 14.5), drop=0, hang='left'),
                        hat=hat('sniffer', 'root/bone/body/head', (0, 4, -25.5), 'xl'),
                        crate=crate('sniffer', 'root/bone/body', (0, -9.5, 0)))
    F['turtle'] = dict(lanyard=lanyard('turtle', 'head', 'v', (0, 20.5, -9), (-3, 3, -2.5, 2.5), drop=0,
                                       badge='mini', hang='left'),
                       hat=hat('turtle', 'head', (0, 18, -10), 'm'), crate=crate('turtle', 'body', (0, 15, 3)))
    F['armadillo'] = dict(lanyard=lanyard('armadillo', 'body/head', 'v', (0, 20, -6.5), (-1.5, 1.5, -2, 2), drop=0,
                                          badge='mini', hang='left'),
                          hat=hat('armadillo', 'body/head', (0, 17.5, -7.5), 'xs'), crate=None)
    F['axolotl'] = dict(lanyard=lanyard('axolotl', 'body/head', 'v', (0, 19.5, -6), (-4, 4, -2.5, 2.5), drop=0,
                                        badge='mini', hang='left'),
                        hat=hat('axolotl', 'body/head', (0, 17, -7), 's', perch=True), crate=crate('axolotl', 'body', (0, 17, 1)))
    F['frog'] = dict(lanyard=lanyard('frog', 'root/body', 'v', (0, 20.5, -3), (-3.5, 3.5, -1.5, 1.5), drop=0,
                                     badge='mini', hang='left'),
                     # the hat rests on the eyes (they peer out under the brim); a carried crate sits behind them
                     hat=hat('frog', 'root/body/head', (0, 16, -1.5), 's', perch=True),
                     crate=crate('frog', 'root/body/head', (0, 18, 3.5)))
    for s in ('spider', 'cave_spider'):
        F[s] = dict(lanyard=lanyard(s, 'head', 'v', (0, 15, -5), (-4, 4, -4, 4), drop=1),
                    hat=hat(s, 'head', (0, 11, -7), 'l'), crate=crate(s, 'body1', (0, 11, 9)))
    F['strider'] = dict(lanyard=lanyard('strider', 'body', 'h', (0, 3, 0), (-8, 8, -8, 8), drop=1),
                        hat=hat('strider', 'body', (0, -5, 0), 'xl'), crate=None)
    F['endermite'] = dict(lanyard=lanyard('endermite', 'segment1', 'v', (0, 22, -1.5), (-3, 3, -2, 2), drop=0,
                                          badge='mini', hang='left'),
                          # the hat sits across the low head and the front of the body segment behind it (its brim
                          # level with that segment's top, over the head); the crate rides on the body behind it
                          hat=hat('endermite', 'segment0', None, 'xs', at=(0, 20, -2.5)),
                          crate=crate('endermite', 'segment1', (0, 20, 2)))
    F['silverfish'] = dict(lanyard=lanyard('silverfish', 'segment2', 'v', (0, 22, -0.5), (-3, 3, -2, 2), drop=0,
                                           badge='mini', hang='left'),
                           hat=hat('silverfish', 'segment1', None, 'xs', at=(0, 19, -1.5)), crate=crate('silverfish', 'segment2', (0, 20, 4)))

    # the shapeless ones
    F['ghast'] = dict(lanyard=lanyard('ghast', 'body', 'h', (0, 13.6, 0), (-8, 8, -8, 8), drop=1),
                      hat=hat('ghast', 'body', (0, 9.6, 0), 'l'), crate=crate('ghast', 'body', (0, 30.6, 0)))
    for s in ('squid', 'glow_squid'):
        F[s] = dict(lanyard=lanyard(s, 'body', 'h', (0, 12, 0), (-6, 6, -6, 6), drop=1),
                    hat=hat(s, 'body', (0, 0, 0), 'xl'), crate=crate(s, 'body', (0, 0, 0)))
    F['dolphin'] = dict(lanyard=lanyard('dolphin', 'body/head', 'v', (0, 18.5, -6), (-4, 4, -3.5, 3.5), drop=0,
                                        badge='mini', hang='left'),
                        hat=hat('dolphin', 'body/head', (0, 15, -8), 'l'), crate=crate('dolphin', 'body', (0, 15, 2)))
    F['phantom'] = dict(lanyard=lanyard('phantom', 'body/head', 'v', (-0.5, 0, -9), (-3.5, 3.5, -1.7, 1.8), drop=0, badge='mini', hang='left'),
                        hat=hat('phantom', 'body/head', (-0.5, -1.7, -9.5), 'm'), crate=None)
    F['parrot'] = dict(lanyard=lanyard('parrot', 'body', 'h', (0, 17, -3), (-1.5, 1.5, -1.5, 1.5), drop=0,
                                       badge='mini'),
                       hat=hat('parrot', 'head', (0, 13.2, -3.5), 'xs'), crate=None)
    F['bee'] = dict(lanyard=lanyard('bee', 'bone/body', 'v', (0, 18.5, 0), (-3.5, 3.5, -3.5, 3.5), drop=0, badge='mini', hang='left'),
                    hat=hat('bee', 'bone/body', (0, 15, -1), 'm'), crate=None)
    F['slime'] = dict(lanyard=lanyard('slime', 'cube', 'h', (0, 21, 0), (-4, 4, -4, 4), drop=0, badge='mini'),
                      hat=hat('slime', 'cube', None, 'm', at=(0, 16, 0)), crate=crate('slime', 'cube', (0, 16, 0), hat_lift=5))
    F['magma_cube'] = dict(lanyard=lanyard('magma_cube', 'cube4', 'h', (0, 21, 0), (-4, 4, -4, 4), drop=0, badge='mini'),
                           hat=hat('magma_cube', 'cube4', None, 'm', at=(0, 16, 0)), crate=crate('magma_cube', 'cube4', (0, 16, 0), hat_lift=5))
    return F


def species_file(species, fit):
    kind, model = ACCESS[species]
    out = {'format': FORMAT, 'entity': 'minecraft:' + species, 'model': model}
    lan = {k: v for k, v in fit['lanyard'].items() if not k.startswith('_')}
    out['lanyard'] = lan
    out['hat'] = fit['hat']
    out['crate'] = fit['crate']
    if fit.get('render'):
        out['render'] = fit['render']
    return out


def files():
    return {s: species_file(s, f) for s, f in sorted(table().items())}


def _clean(o):
    """Numbers to 3 decimals, whole ones as ints."""
    if isinstance(o, dict):
        return {k: _clean(v) for k, v in o.items()}
    if isinstance(o, list):
        return [_clean(v) for v in o]
    if isinstance(o, float):
        r = round(o, 3)
        return int(r) if r == int(r) else r
    return o


def dump(d):
    """Two-space JSON with every list of numbers on one line."""
    text = json.dumps(_clean(d), indent=2)
    text = re.sub(r'\[\s*(-?[\d.]+(?:,\s*-?[\d.]+)*)\s*\]',
                  lambda m: '[' + ', '.join(x.strip() for x in m.group(1).split(',')) + ']', text)
    return text + '\n'


def main():
    check = '--check' in sys.argv
    bad = 0
    out = files()
    for s, d in out.items():
        path = os.path.join(OUT, s + '.json')
        text = dump(d)
        if check:
            same = os.path.exists(path) and open(path, encoding='utf-8').read() == text
            bad += not same
            if not same:
                print('DIFF', s)
        else:
            os.makedirs(OUT, exist_ok=True)
            with open(path, 'w', encoding='utf-8', newline='\n') as fh:
                fh.write(text)
    print('%d species %s' % (len(out), 'checked' if check else 'written'))
    sys.exit(1 if bad else 0)


if __name__ == '__main__':
    main()

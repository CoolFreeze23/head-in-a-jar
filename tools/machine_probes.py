"""Writes the smoke-only render-cost probe assets into src/smoke/resources (never shipped).

Blocks (registered by src/smoke/.../MachineProbe.java):
  anim_probe_a, anim_probe_b  full cube / half slab whose texture is a 4-frame .mcmeta strip (red, green, blue, yellow,
                              10 ticks a frame); the two use different sprites with the same timing, to check phase
  lamp_probe[lamp=0..5]       a busy baked model (48 elements) with one face whose texture follows the lamp value
  gecko_loop / gecko_stop / gecko_skip
                              the same 8-bone, 32-cube GeckoLib model: playing a loop / controller stopped (drawn at
                              rest) / renderer returns without drawing
Run: python tools/machine_probes.py
"""
import json
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "smoke", "resources", "assets", "headinajar")


def path(*parts):
    p = os.path.join(ROOT, *parts)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    return p


def write_json(obj, *parts):
    with open(path(*parts), "w", newline="\n") as f:
        json.dump(obj, f, indent=1)
        f.write("\n")


def solid(color, border, size=16):
    img = Image.new("RGBA", (size, size), color)
    for i in range(size):
        for x, y in ((i, 0), (i, size - 1), (0, i), (size - 1, i)):
            img.putpixel((x, y), border)
    return img


FRAMES = [(220, 40, 40, 255), (40, 200, 60, 255), (40, 80, 230, 255), (230, 210, 40, 255)]


def strip(border):
    img = Image.new("RGBA", (16, 16 * len(FRAMES)))
    for i, c in enumerate(FRAMES):
        img.paste(solid(c, border), (0, 16 * i))
    return img


def main():
    tex = ("textures", "block")
    for name, border in (("anim_probe_a", (20, 20, 20, 255)), ("anim_probe_b", (250, 250, 250, 255))):
        strip(border).save(path(*tex, name + ".png"))
        write_json({"animation": {"frametime": 10}}, *tex, name + ".png.mcmeta")
    write_json({"parent": "minecraft:block/cube_all", "textures": {"all": "headinajar:block/anim_probe_a"}},
               "models", "block", "anim_probe_a.json")
    write_json({"parent": "minecraft:block/slab", "textures": {"bottom": "headinajar:block/anim_probe_b",
                "top": "headinajar:block/anim_probe_b", "side": "headinajar:block/anim_probe_b"}},
               "models", "block", "anim_probe_b.json")
    for name in ("anim_probe_a", "anim_probe_b"):
        write_json({"variants": {"": {"model": "headinajar:block/" + name}}}, "blockstates", name + ".json")

    # lamp probe: a steel body of 48 elements and a 4x4 lens whose texture is chosen by the state
    lamp_colors = [(30, 30, 30, 255), (60, 200, 70, 255), (230, 160, 30, 255), (250, 200, 60, 255),
                   (120, 80, 20, 255), (220, 40, 40, 255)]
    solid((150, 150, 160, 255), (90, 90, 100, 255)).save(path(*tex, "lamp_probe_body.png"))
    for i, c in enumerate(lamp_colors):
        solid(c, (40, 40, 40, 255)).save(path(*tex, "lamp_probe_%d.png" % i))
    elements = [{"from": [1, 0, 1], "to": [15, 2, 15],
                 "faces": {d: {"texture": "#body"} for d in ("north", "south", "east", "west", "up", "down")}}]
    n = 0
    for y in range(2, 14, 4):
        for x in range(1, 15, 4):
            for z in range(1, 15, 4):
                if n >= 46:
                    break
                elements.append({"from": [x, y, z], "to": [x + 2, y + 3, z + 2],
                                 "faces": {d: {"texture": "#body"} for d in ("north", "south", "east", "west", "up", "down")}})
                n += 1
    elements.append({"from": [6, 6, 0], "to": [10, 10, 1],
                     "faces": {d: {"texture": "#lamp"} for d in ("north", "south", "east", "west", "up", "down")}})
    for i in range(6):
        write_json({"parent": "minecraft:block/block", "textures": {"particle": "headinajar:block/lamp_probe_body",
                    "body": "headinajar:block/lamp_probe_body", "lamp": "headinajar:block/lamp_probe_%d" % i},
                    "elements": elements}, "models", "block", "lamp_probe_%d.json" % i)
    write_json({"variants": {"lamp=%d" % i: {"model": "headinajar:block/lamp_probe_%d" % i} for i in range(6)}},
               "blockstates", "lamp_probe.json")

    # GeckoLib probe: 8 bones in a ring, 4 cubes each
    solid((170, 160, 140, 255), (60, 60, 60, 255), 64).save(path(*tex, "machprobe.png"))
    bones = [{"name": "root", "pivot": [0, 0, 0]}]
    anim_bones = {}
    for b in range(8):
        px = [-5, 0, 5, -5, 5, -5, 0, 5][b]
        pz = [-5, -5, -5, 0, 0, 5, 5, 5][b]
        cubes = [{"origin": [px - 1, 2 + 3 * c, pz - 1], "size": [2, 2, 2], "uv": [0, 0]} for c in range(4)]
        bones.append({"name": "b%d" % b, "parent": "root", "pivot": [px, 2, pz], "cubes": cubes})
        anim_bones["b%d" % b] = {"rotation": {"0.0": [0, 0, 0], "1.0": [0, 180, 0], "2.0": [0, 360, 0]},
                                 "position": {"0.0": [0, 0, 0], "1.0": [0, 1, 0], "2.0": [0, 0, 0]}}
    write_json({"format_version": "1.12.0", "minecraft:geometry": [{"description": {
        "identifier": "geometry.machprobe", "texture_width": 64, "texture_height": 64,
        "visible_bounds_width": 2, "visible_bounds_height": 2, "visible_bounds_offset": [0, 0.75, 0]},
        "bones": bones}]}, "geo", "block", "machprobe.geo.json")
    write_json({"format_version": "1.8.0", "animations": {"animation.machprobe.loop": {
        "loop": True, "animation_length": 2, "bones": anim_bones}}}, "animations", "block", "machprobe.animation.json")
    for name in ("gecko_loop", "gecko_stop", "gecko_skip"):
        write_json({"variants": {"": {"model": "minecraft:block/stone"}}}, "blockstates", name + ".json")


if __name__ == "__main__":
    main()

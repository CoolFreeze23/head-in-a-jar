# How Head in a Jar's textures are made

Every texture in `src/main/resources/assets/headinajar/textures/` has exactly one source, and
`art/textures/manifest.json` says which:

| source | what is edited | who writes the PNG |
|---|---|---|
| `map` | a pixel map, `art/textures/<same path>.txt` | `tools/tex/tex.py build` |
| `painted` | the PNG itself, in any pixel editor or Blockbench | the painter |
| `legacy` | nothing yet: an early placeholder | `tools/draw_textures.py` (and `draw_p2.py` to `draw_p6.py`) |

The manifest also records the SHA-256 of what the tools last wrote. No tool overwrites a PNG whose bytes differ
from that record, so painted work is never lost to a regeneration: the build stops and asks for the file to be
adopted as `painted` instead. The old draw scripts write only `legacy` files, and only while they are unchanged.

## The palette

`art/palette.json` holds every colour the mod uses, as named ramps from darkest (step 0) to lightest: enamel,
brass, steel, teal glass, rubber, warning yellow, jar glass, saline, flesh, bone, plus copper, clear glass and
iron taken unchanged from vanilla, and the emissive ramps for indicator lights and screens. A colour is named
`brass.3` or by its alias `b3`. The house materials come in three style sets (A, B, C) with the same names, so
one command recolours every pixel-mapped texture:

    python tools/tex/tex.py set-default B

Print the palette with `python tools/tex/tex.py palette` (add `--set B` to see another set).

## Pixel maps

A pixel map is a small text file: a header, a key from aliases to palette colours, and one row of aliases per
pixel row. It is mirrored under `art/textures/`: `art/textures/block/foo.txt` makes `block/foo.png`.

    texture: block/foo.png
    size: 16x16
    key:
      ..  clear
      s3  steel.3
      s4  steel.4
      tb  teal_glass.1 @110      # alpha after @
      R3  indicator_red.3  glow  # also written to foo_glowmask.png
    pixels:
    s4 s4 s4 s4 s4 s4 s4 s4 s4 s4 s4 s4 s4 s4 s4 s3
    ...

- Aliases are one or two characters, all the same length within a file. Two-character rows may be spaced for
  reading; one-character keys (`a  steel.3`) keep wide entity and model sheets compact.
- A key value is a palette key (`steel.3`), a palette alias (`s3`), `clear`, or `#rrggbb` for a deliberate
  exception to the palette (the build and the checker flag it).
- `size:` is any width and height. Entity and GeckoLib sheets work the same way as 16x16 faces.
- `glow` on a key line writes those pixels into a `_glowmask.png` companion, the way GeckoLib draws emissive
  parts.
- `frames: N` with N frames stacked top to bottom (and optional `frametime:` in ticks and `interpolate: true`)
  makes an animated strip and its `.png.mcmeta`.
- Lines starting with `#` are comments. Files and folders whose names start with `_` are never built;
  `art/textures/_templates/` holds commented examples of each kind.

The compiler is deterministic: there is no randomness anywhere in it, and the same map and palette always give
the same bytes.

## Commands

Run from the repository root with Python 3 and Pillow.

| command | does |
|---|---|
| `tex.py build` / `tex.py build block/foo.png` | compile every map, or one |
| `tex.py check` | no writes; fails if a mapped PNG does not match its map, a PNG is not in the manifest, or a tracked PNG changed by hand |
| `tex.py adopt item/foo.png` | make a PNG `painted` (after painting over a compiled file, or for a new painted file) |
| `tex.py import block/foo.png` | start a map from an existing PNG by nearest palette colour, reporting how far each colour moved |
| `tex.py render art/textures/block/foo.txt --out preview.png --scale 8 --set C` | look at a map without touching the mod |
| `tex.py swap --set B --out some/folder` | recolour mapped and painted textures to another style set in a separate folder |
| `tex.py measure block/foo.png` | the style-rule measurements (colour count, largest colour share, neighbour jumps, regions, lone pixels, palette membership, alpha, emissive share) with pass or fail |

All commands are `python tools/tex/tex.py <command>`.

## Painting by hand

Paint the PNG, then run `tex.py adopt` on it. From then on the PNG is the source; delete its map or rename it to
start with `_` if you want to keep it for reference. Variants of a painted texture (another paint colour, an
on and off state) are best made as an exact colour swap between two ramps of the palette.

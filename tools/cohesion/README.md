# Cohesion walker

The cohesion walker tries item-on-target pairs in the game with real clicks and records what happened. Each pair is
one click: an item in the hand (or none), optionally an off-hand item, optionally sneaking, in one of the mod's forms
(whole body, jar, linked body, hosted, pilot, socket), on one target (a block, a mob, a host, a shell, a mecha, a
placed head, another player, the air). The walker exists to catch the worst kind of bug: a click a player would
plausibly try that answers with nothing at all.

It runs on the self-driving dev client (`tools/smoke.sh`). The action is always made through the real input path,
by key binding (`key use`, `key attack`, `sneak`), so it means the same under rebound keys. Only the scene around it
is built with set-up verbs and server commands.

## Files

| File | What it is |
|---|---|
| `pairs.tsv` | The pairs, one per line: `id item offhand sneak form target setup outcome effect lang_key code_ref surprise`. `outcome` is the predicted result (`works`, `refuses`, `nothing`, `misrouted`, `unclear`, or `unpredicted`). |
| `setup.json` | How each `form`, `target` and `setup` value becomes script lines. Its `_doc` entry lists the tokens and fields. |
| `gen_walk.py` | Writes the scripts `walk-NN.txt` and `walk-index.tsv`, and lints them. |
| `walk-00.txt` | A short proof batch, one pair for each kind of set-up. Run it first after any harness change. |
| `walk-01.txt` ... | The full walk, in batches that each fit well inside the client's 15-minute limit. |
| `walk-index.tsv` | For every pair: which batch and spot it is walked in, the form the pre-check expects, and any note on where the set-up only approximates the row (or why the row cannot be set up). |
| `collect.py` | Reads the run reports and writes the results table and summary. |

## Generate

```bash
python tools/cohesion/gen_walk.py --lint      # regenerate every batch and lint it
python tools/cohesion/gen_walk.py --list      # how each pair resolves
python tools/cohesion/gen_walk.py --only P001,P099 --lint   # walk-only.txt with just these pairs
```

Adding a pair is one line in `pairs.tsv`. If its `target` or `setup` text is new, add it to `setup.json` (or reuse an
existing wording). A pair whose wording is not mapped is listed as unreachable with the reason, and is not walked.

The lint parses every generated line against a table of the harness verbs and their arguments. It also checks the
server commands used, bracket and quote balance in NBT, `headinajar:` ids against the mod's lang file, and jar
module, mecha frame, mecha module and gene names against the mod's enums. Scripts must be LF; the lint checks that.

## Run

One batch per client run:

```bash
bash tools/smoke.sh tools/cohesion/walk-00.txt
cp -r run-smoke/smoke-out /d/cohesion-runs/walk-00      # keep each batch's report and screenshots
cp build/smoke.log /d/cohesion-runs/walk-00/smoke.log   # optional: lets the collector attribute exceptions
```

Each pair is on its own spot of a flat survival world, 24 blocks from its neighbours. A pair:

1. writes `PAIR <id> begin`;
2. builds the scene and puts the player in the row's form;
3. aims, puts the items in the hands, and records the state (`riding PRE-<id>`, `inspect me`);
4. writes `PAIR <id> act`;
5. makes the click;
6. takes `screenshots/cw-<id>.png`;
7. records the state again and writes `PAIR <id> end`.

The reset that follows kills the player (a real death puts every form back to a whole body, and the inventory is
kept and then cleared), removes every entity on the spot, clears its blocks, and checks the form (`riding RESET-<id>`).

## Collect

```bash
python tools/cohesion/collect.py /d/cohesion-runs/walk-01 /d/cohesion-runs/walk-02 ... OUTDIR \
    --log /d/cohesion-runs/walk-01/smoke.log --previous OLD/cohesion-results.tsv
```

`OUTDIR` gets `cohesion-results.tsv` (one row per pair), `cohesion-results.md` and `screenshots/`. The summary in
the `.md` file has counts, the list of does-nothings, the mismatches with the prediction, and, with `--previous`,
what was fixed, what regressed and what is new. `screenshots/` holds copies of the shots of pairs that did nothing,
glitched or differed from the prediction.

Observed outcomes:

| Outcome | Meaning |
|---|---|
| `works` | A change of world, inventory, form, vehicle or a screen. |
| `refuses` | A message (action bar, chat or title) and no change. The text is matched to a lang key where possible. |
| `feedback` | Only an arm swing, a sound or a particle. Something answered, but nothing changed and nothing was said. |
| `nothing` | The harness recorded `NOTHING`. |
| `glitch` | An exception, no record of the click (a crash or time-out), or the form left wrong after the reset. |
| `unreached` | The set-up did not produce the expected form, so the click was not the one the row means. The pair is not judged. |

The collector also reads plain harness reports without `PAIR` markers. It then lists every input it finds with its
outcome, which is a quick way to read any smoke report.

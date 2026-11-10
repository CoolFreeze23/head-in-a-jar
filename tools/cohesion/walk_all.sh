#!/usr/bin/env bash
# The whole cohesion walk in one command: regenerate and lint the batches, run each one in the dev client, keep each
# batch's report, screenshots and log, and collect them into one results table.
# usage: tools/cohesion/walk_all.sh OUTDIR [--previous OLD/cohesion-results.tsv] [BATCH ...]
#   OUTDIR   where each batch's run (OUTDIR/runs/<batch>) and the results (OUTDIR/results) go; keep it off the project
#   BATCH    only these batches (e.g. walk-03 walk-h-P170); default every walk-NN and walk-h-* batch but the proof batch
# Runs through tools/smoke.sh, so GAME_LOCK, SMOKE_MODS, SMOKE_PROFILE and the other variables apply as usual. With
# GAME_LOCK set, each batch takes and releases the lock on its own, so other queues get a turn between batches.
# Extra arguments for the generator: WALK_GEN_ARGS (e.g. --no-head-guard once jarred heads draw without crashing).
set -u
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
cd "$ROOT"
OUT=${1:?usage: walk_all.sh OUTDIR [--previous FILE] [BATCH ...]}
shift
PREV=()
if [ "${1:-}" = --previous ]; then PREV=(--previous "$2"); shift 2; fi
python tools/cohesion/gen_walk.py --lint ${WALK_GEN_ARGS:-} > /dev/null || { python tools/cohesion/gen_walk.py --lint-only | tail -20; echo "lint failed"; exit 1; }
if [ $# -gt 0 ]; then
  batches=("$@")
else
  batches=($(ls tools/cohesion/walk-*.txt | xargs -n1 basename | sed 's/\.txt$//' | grep -v -e '^walk-00$' -e '^walk-only$'))
fi
mkdir -p "$OUT/runs"
walk() {
  local start=$(date +%s)
  for b in "${batches[@]}"; do
    echo "=== $b $(date +%T)"
    bash tools/smoke.sh "tools/cohesion/$b.txt" > "$OUT/runs/$b.out" 2>&1
    grep -E "client ran|gradle exit|^--- report" "$OUT/runs/$b.out"
    rm -rf "$OUT/runs/$b"
    mkdir -p "$OUT/runs/$b"
    cp -r run-smoke/smoke-out/. "$OUT/runs/$b/" 2>/dev/null
    cp build/smoke.log "$OUT/runs/$b/smoke.log" 2>/dev/null
  done
  echo "the walk ran $(( ($(date +%s) - start) / 60 )) min of wall time (waits for the lock included)"
}
walk
runs=()
logs=()
for b in "${batches[@]}"; do
  runs+=("$OUT/runs/$b")
  logs+=(--log "$OUT/runs/$b/smoke.log")
done
python tools/cohesion/collect.py "${runs[@]}" "$OUT/results" "${logs[@]}" "${PREV[@]}"

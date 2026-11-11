#!/usr/bin/env bash
# Old-save test: copies the saved baseline world into the dev client as baseline_check, opens it, reads every thing
# it should hold (world-check.txt) and compares the result with world-expect.json.
# usage: tools/world/check_world.sh [WORLD_DIR]   (default $BASELINE_WORLD, else build/worlds/baseline-before)
# The result table goes to run-smoke/smoke-out/world-check.md; captures of each row to run-smoke/smoke-out/screenshots.
set -u
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
WORLD="${1:-${BASELINE_WORLD:-build/worlds/baseline-before}}"
cd "$ROOT"
[ -f "$WORLD/level.dat" ] || { echo "no world at $WORLD"; exit 2; }
game=run-smoke
[ "${SMOKE_PROFILE:-}" = pack ] && game=run-smoke-pack
rm -rf "$game/saves/baseline_check"
mkdir -p "$game/saves"
cp -r "$WORLD" "$game/saves/baseline_check"
rm -f "$game/saves/baseline_check/session.lock"
bash tools/smoke.sh tools/world/world-check.txt
rc=$?
python tools/world/check_world.py run-smoke/smoke-out/report.txt --out run-smoke/smoke-out/world-check.md
crc=$?
[ $rc -ne 0 ] && exit $rc
exit $crc

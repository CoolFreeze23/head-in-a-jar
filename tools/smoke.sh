#!/usr/bin/env bash
# Runs the hidden dev client against a script.
# usage: tools/smoke.sh [script file]   results land in run-smoke/smoke-out (report.txt, screenshots)
set -u
ROOT=$(cd "$(dirname "$0")/.." && pwd)
SCRIPT=${1:-$ROOT/tools/smoke-basic.txt}
export MSYS_NO_PATHCONV=1
cd "$ROOT"
mkdir -p run-smoke/config
rm -rf run-smoke/saves/smoke run-smoke/smoke-out
printf '%s\n' 'earlyWindowControl = false' > run-smoke/config/fml.toml
if [ ! -f run-smoke/options.txt ]; then
  printf '%s\n' 'onboardAccessibility:false' 'soundCategory_master:0.0' 'pauseOnLostFocus:false' 'narrator:0' 'tutorialStep:none' \
    'skipMultiplayerWarning:true' 'joinedFirstServer:true' 'renderDistance:6' 'simulationDistance:6' 'maxFps:60' 'guiScale:2' > run-smoke/options.txt
fi
cp "$SCRIPT" run-smoke/smoke-script.txt
run() { ./gradlew runSmokeClient --console=plain > build/smoke.log 2>&1; }
# GAME_LOCK: optional script defining with_game_lock, for machines that run several games one at a time
if [ -n "${GAME_LOCK:-}" ]; then
  . "$GAME_LOCK"
  with_game_lock run
else
  run
fi
rc=$?
echo "gradle exit $rc"
grep -nE 'Critical injection|InjectionError|MixinTransformerError|SMOKE:|Unable to load model|Failed to load|Couldn.t parse|Missing textures|Exception in|Caused by:|Reported exception|Crash report saved|FATAL' build/smoke.log | cut -c1-260 | head -40
[ -f run-smoke/smoke-out/report.txt ] && { echo "--- report ($(wc -l < run-smoke/smoke-out/report.txt) lines)"; head -${REPORT_LINES:-60} run-smoke/smoke-out/report.txt | cut -c1-200; }
exit $rc

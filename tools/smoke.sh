#!/usr/bin/env bash
# Runs the hidden dev client against a script. The verbs and directives are implemented in src/smoke (SmokeClient and Harness).
# usage: tools/smoke.sh [script file]   results land in run-smoke/smoke-out (report.txt, screenshots)
# Environment:
#   SMOKE_PROFILE=pack    a modpack instance's settings (keys, render distance, configs, Fresh Animations packs), copied
#                         from SMOKE_PACK_DIR into run-smoke-pack (the instance is only read); default: plain dev settings
#   SMOKE_PACK_DIR        the instance's minecraft folder (default: the Prism Launcher instance "CrazyCraft 5.0")
#   SMOKE_MODS=a,b,...    extra mod groups from libs-smoke/<group> (sodium iris shoulder camera culling dynlights),
#                         "anim" for Punchy and Not Enough Animations, "noemf" to leave EMF/ETF out
#   SMOKE_PACK_MODS=1     the same as SMOKE_MODS=anim (kept for old scripts)
#   SMOKE_SHADER=FILE     a shader pack zip from the instance's shaderpacks folder, switched on through Iris (needs iris);
#                         "off" or unset writes enableShaders=false
#   SMOKE_FRAMES_DIR      where "frames" captures go (default D:\hij-study\frames)
#   SMOKE_ARCHIVE         where the previous run's smoke-out is moved (default /d/hij-study/smoke-archive); a script
#                         line "@clean" deletes it instead
#   SMOKE_TIMEOUT_MIN     minutes before the client gives up (default 15)
#   SMOKE_WIDTH/HEIGHT    window size (default 1280x720)
set -u
ROOT=$(cd "$(dirname "$0")/.." && pwd)
SCRIPT=$(cd "$(dirname "${1:-$ROOT/tools/smoke-basic.txt}")" && pwd)/$(basename "${1:-$ROOT/tools/smoke-basic.txt}")
export MSYS_NO_PATHCONV=1
cd "$ROOT"
PACK="${SMOKE_PACK_DIR:-${APPDATA:-}/PrismLauncher/instances/CrazyCraft 5.0/minecraft}"

prepare() {
  mkdir -p run-smoke
  # the last run's captures are moved out of the way, never deleted unless the script says @clean
  if [ -d run-smoke/smoke-out ]; then
    if grep -qE '^@clean\b' "$SCRIPT"; then
      rm -rf run-smoke/smoke-out
    else
      local dest="${SMOKE_ARCHIVE:-/d/hij-study/smoke-archive}/$(date +%Y%m%d-%H%M%S)"
      mkdir -p "$dest" && mv run-smoke/smoke-out "$dest/" && echo "previous smoke-out moved to $dest"
    fi
  fi
  cp "$SCRIPT" run-smoke/smoke-script.txt
  local game=run-smoke
  if [ "${SMOKE_PROFILE:-}" = pack ]; then
    game=run-smoke-pack
    export SMOKE_GAME_DIR=$game
    mkdir -p $game/config $game/resourcepacks
    # the instance's options, then what a hidden test client needs on top (muted, no pause, no onboarding), and the dev window's GUI scale
    grep -vE '^(soundCategory_master|pauseOnLostFocus|onboardAccessibility|tutorialStep|skipMultiplayerWarning|joinedFirstServer|narrator|guiScale|enableVsync|fullscreen|resourcePacks|incompatibleResourcePacks):' \
      "$PACK/options.txt" > $game/options.txt
    printf '%s\n' 'soundCategory_master:0.0' 'pauseOnLostFocus:false' 'onboardAccessibility:false' 'tutorialStep:none' \
      'skipMultiplayerWarning:true' 'joinedFirstServer:true' 'narrator:0' 'guiScale:3' 'enableVsync:false' 'fullscreen:false' \
      'resourcePacks:["vanilla","mod_resources","file/FreshAnimations_v1.10.4.zip","file/FA+Emissive-v1.6.zip","file/FA+Player-v1.1.zip"]' >> $game/options.txt
    for f in shouldersurfing-client.toml cameraoverhaul.toml entityculling.json sodium-options.json notenoughanimations.json \
             entity_model_features.json entity_texture_features.json perception.yaml; do
      [ -f "$PACK/config/$f" ] && cp "$PACK/config/$f" $game/config/
    done
    [ -d "$PACK/config/punchy" ] && rm -rf $game/config/punchy && cp -r "$PACK/config/punchy" $game/config/
    for f in FreshAnimations_v1.10.4.zip FA+Emissive-v1.6.zip FA+Player-v1.1.zip; do
      [ -f $game/resourcepacks/$f ] || cp "$PACK/resourcepacks/$f" $game/resourcepacks/ 2>/dev/null || echo "resource pack $f not found in the instance"
    done
  else
    unset SMOKE_GAME_DIR
    mkdir -p $game/config
    if [ ! -f $game/options.txt ]; then
      printf '%s\n' 'onboardAccessibility:false' 'soundCategory_master:0.0' 'pauseOnLostFocus:false' 'narrator:0' 'tutorialStep:none' \
        'skipMultiplayerWarning:true' 'joinedFirstServer:true' 'renderDistance:6' 'simulationDistance:6' 'maxFps:60' 'guiScale:2' > $game/options.txt
    fi
  fi
  rm -rf $game/saves/smoke
  printf '%s\n' 'earlyWindowControl = false' > $game/config/fml.toml
  # Iris: shaders off unless SMOKE_SHADER names a pack from the instance's shaderpacks folder (copied, never moved)
  if [ -n "${SMOKE_SHADER:-}" ] && [ "${SMOKE_SHADER}" != off ]; then
    mkdir -p $game/shaderpacks
    [ -f "$game/shaderpacks/$SMOKE_SHADER" ] || cp "$PACK/shaderpacks/$SMOKE_SHADER" $game/shaderpacks/
    printf '%s\n' 'enableShaders=true' "shaderPack=$SMOKE_SHADER" 'allowUnknownShaders=false' 'disableUpdateMessage=true' > $game/config/iris.properties
  else
    printf '%s\n' 'enableShaders=false' 'disableUpdateMessage=true' > $game/config/iris.properties
  fi
  GAME=$game
}
run() {
  prepare
  local start=$(date +%s)
  ./gradlew runSmokeClient --console=plain > build/smoke.log 2>&1
  local rc=$?
  echo "client ran $(( $(date +%s) - start )) s (game folder $GAME, profile ${SMOKE_PROFILE:-default}, mods ${SMOKE_MODS:-none}${SMOKE_PACK_MODS:+ +anim}, shader ${SMOKE_SHADER:-off})"
  return $rc
}
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
[ -f run-smoke/smoke-out/report.txt ] && { echo "--- report ($(wc -l < run-smoke/smoke-out/report.txt) lines, $(grep -c '^INPUT' run-smoke/smoke-out/report.txt) inputs, $(grep -c -- '-> NOTHING' run-smoke/smoke-out/report.txt) NOTHING)"; head -${REPORT_LINES:-60} run-smoke/smoke-out/report.txt | cut -c1-${REPORT_WIDTH:-200}; }
exit $rc

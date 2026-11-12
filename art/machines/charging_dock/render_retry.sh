#!/bin/bash
# render.sh with up to four tries (the headless renderer fails now and then on back-to-back calls).
OUT="$2"
for i in 1 2 3 4; do
  rm -f "$OUT"
  bash "$(dirname "$0")/../kit/render.sh" "$@" > /dev/null
  [ -s "$OUT" ] && exit 0
  sleep 1
done
echo "render failed after 4 tries: $OUT"

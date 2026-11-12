#!/bin/bash
# Renders the Charging Dock's three review boards from the review copies (run from the repository root, one render at a
# time). Writes the PNGs under the captures folder.
#   bash art/machines/charging_dock/dock_boards.sh [prefix]
P="${1:-}"
CAP=${CAPTURES:-captures}/charging_dock
O=$CAP/work
C=$CAP/boards
RV=build/machines-review
R=$RV/dock_review.bbmodel
E=$RV/dock_empty.bbmodel
RS=art/machines/charging_dock/render_retry.sh
B=art/machines/kit/board.py
mkdir -p "$O" "$C"

# board_rest: empty (rest pose, asleep) and loaded (a jar on it, the switch shut, charging at 60 %)
args=""
for v in front three-quarter right back iso top; do
  bash $RS $E $O/${P}r_empty_$v.png $v 300; args="$args empty_$v=$O/${P}r_empty_$v.png"
done
for v in front three-quarter right back iso top; do
  bash $RS $R $O/${P}r_jar_$v.png $v 300 _review_shut 0; args="$args charging_$v=$O/${P}r_jar_$v.png"
done
python $B $C/${P}board_rest.png 6 $args

# board_far: 128 px (about ten blocks), three-quarter and front, empty and loaded open (rest) against shut (work)
far=""
for spec in "e_rest|$E|three-quarter|" "e_rest_f|$E|front|" "j_open|$R|three-quarter|" "j_shut|$R|three-quarter|_review_shut" \
            "j_open_f|$R|front|" "j_shut_f|$R|front|_review_shut" "j_low|$R|three-quarter|_review_low" "j_done|$R|three-quarter|_review_done"; do
  IFS='|' read n m v c <<< "$spec"
  if [ -n "$c" ]; then set -- $c; bash $RS $m $O/${P}f_$n.png $v 128 $1 ${2:-0}; else bash $RS $m $O/${P}f_$n.png $v 128; fi
done
python - "$O" "$C/${P}board_far.png" "$P" <<'PY'
import sys
from PIL import Image, ImageDraw
o, out, p = sys.argv[1], sys.argv[2], sys.argv[3]
rows = [("empty, asleep: 3q", "e_rest"), ("empty, asleep: front", "e_rest_f"),
        ("jar on, switch OPEN (first tick): 3q", "j_open"), ("jar on, CHARGING (switch shut): 3q", "j_shut"),
        ("jar on, OPEN: front", "j_open_f"), ("jar on, CHARGING: front", "j_shut_f"),
        ("brown-out (droop): 3q", "j_low"), ("FULL, switch tripped open, jar still on: 3q", "j_done")]
W, H = 128 + 384 + 12, 384 + 20
B = Image.new('RGB', (2 * W, 4 * H), (24, 26, 32))
d = ImageDraw.Draw(B)
for i, (label, n) in enumerate(rows):
    im = Image.open('%s/%sf_%s.png' % (o, p, n)).convert('RGB')
    x, y = (i % 2) * W, (i // 2) * H
    d.text((x + 4, y + 3), label + "  (128 px, and 3x)", fill=(230, 230, 230))
    B.paste(im, (x + 4, y + 20 + 128))
    B.paste(im.resize((384, 384), Image.NEAREST), (x + 128 + 8, y + 20))
B.save(out)
PY

# board_work: clip frames
args=""
for f in "occupy 0 three-quarter" "occupy 3 three-quarter" "occupy 7 three-quarter" "occupy 9 three-quarter" "occupy 10 three-quarter" "occupy 12 three-quarter" \
         "vacate 2 three-quarter" "vacate 6 three-quarter" "vacate 8 three-quarter" "tap 3 front" "tap 7 front" "tap_on 4 front" \
         "brownout 16 front" "full 8 three-quarter" "refuse 2 front" "refuse 4 front" "_review_stepon 14 front" "_review_tap 20 front"; do
  set -- $f; n=${P}w_$1_$2_$3; bash $RS $R $O/$n.png $3 240 $1 $2; args="$args $1_t$2_$3=$O/$n.png"
done
python $B $C/${P}board_work.png 6 $args

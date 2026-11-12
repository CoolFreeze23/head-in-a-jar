#!/bin/bash
# Renders the DNA Sequencer's three review boards from the review copy, one render at a time.
#   bash art/machines/dna_sequencer/sequencer_boards.sh [prefix]
# Uses the kit's render.sh/board.py from art/machines/kit, or the copies in the scratch folder when the worktree has
# lost them.
P="${1:-}"
S=build/machines-review/scratch-dna_sequencer
K=art/machines/kit
RS=$K/render.sh; [ -f $K/kit.py ] || RS=$S/render.sh
BD=$K/board.py; [ -f $BD ] || BD=$S/board.py
O=${CAPTURES:-captures}/dna_sequencer/work
C=${CAPTURES:-captures}/dna_sequencer/boards
R=build/machines-review/dna_sequencer_review.bbmodel
for v in front three-quarter right left back iso top; do bash $RS $R $O/${P}r_$v.png $v 360; done
bash $RS $R $O/${P}r_work_three-quarter.png three-quarter 360 _review_working 0
python $BD $C/${P}board_rest.png 4 front=$O/${P}r_front.png three-quarter=$O/${P}r_three-quarter.png \
  "right (+X)"=$O/${P}r_right.png "left (-X, the crank side)"=$O/${P}r_left.png back=$O/${P}r_back.png iso=$O/${P}r_iso.png \
  top=$O/${P}r_top.png "working pose 3q (receipt full, crank 90, tab up)"=$O/${P}r_work_three-quarter.png
for v in front three-quarter left; do
  bash $RS $R $O/${P}f_rest_$v.png $v 128; bash $RS $R $O/${P}f_work_$v.png $v 128 _review_working 0
done
python - <<PY
from PIL import Image
import glob
for p in glob.glob('$O/${P}f_*.png'):
    if not p.endswith('_x3.png'):
        Image.open(p).resize((384, 384), Image.NEAREST).save(p[:-4] + '_x3.png')
PY
python $S/farboard.py $C/${P}board_far.png $O \
  "rest front=${P}f_rest_front" "working front=${P}f_work_front" "rest 3q=${P}f_rest_three-quarter" \
  "working 3q=${P}f_work_three-quarter" "rest left=${P}f_rest_left" "working left=${P}f_work_left"
args=""
for f in "_review_read 0 front" "_review_read 9 front" "_review_read 12 left" "_review_read 15 front"          "_review_read 104 three-quarter" "_review_read 199 front" "_review_read 206 front" "_review_read 209 three-quarter"          "_review_minigame 0 three-quarter" "_review_minigame 130 front" "_review_minigame 212 three-quarter"          "_review_brownout 200 left" "ring 5 left" "press_c 2 front" "refuse 5 front" "load 6 front" "tear 4 three-quarter"          "ding 5 three-quarter"; do
  set -- $f; n=${P}w_$1_$2_$3; bash $RS $R $O/$n.png $3 240 $1 $2; args="$args $1_t$2_$3=$O/$n.png"
done
python $BD $C/${P}board_work.png 6 $args

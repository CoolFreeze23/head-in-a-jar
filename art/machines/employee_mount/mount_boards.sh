#!/bin/bash
# Renders the Employee Mount's three review boards from the review copy (run from the repository root; one render at a time).
#   bash art/machines/employee_mount/mount_boards.sh [prefix]
P="${1:-}"
CAP=${CAPTURES:-captures}/employee_mount
O=$CAP/work
C=$CAP/boards
RV=build/machines-review
R=$RV/mount_review.bbmodel
RS=art/machines/kit/render.sh
HERE=art/machines/employee_mount
mkdir -p $O $C

# ---- rest: loaded and paid (head, emerald, lamp green), empty (no head, lamp off), unpaid facing the wall
for v in front three-quarter right back iso top; do bash $RS $R $O/${P}r_$v.png $v 360; done
for v in front three-quarter; do bash $RS $RV/mount_empty.bbmodel $O/${P}r_empty_$v.png $v 360; done
bash $RS $RV/mount_unpaid.bbmodel $O/${P}r_wall_three-quarter.png three-quarter 360 _review_wall 0
python art/machines/kit/board.py $C/${P}board_rest.png 4 "front (loaded, paid)"=$O/${P}r_front.png \
  three-quarter=$O/${P}r_three-quarter.png "right (+X)"=$O/${P}r_right.png back=$O/${P}r_back.png iso=$O/${P}r_iso.png \
  top=$O/${P}r_top.png "empty front (lamp off)"=$O/${P}r_empty_front.png "empty three-quarter"=$O/${P}r_empty_three-quarter.png \
  "unpaid 3 days: facing the wall"=$O/${P}r_wall_three-quarter.png

# ---- ten blocks: rest, the complaint's held receiver and the stamp at the top of its lift, front and three-quarter,
# at 84 px (see farboard.py)
for v in front three-quarter; do
  bash $RS $R $O/${P}f_rest_$v.png $v 84; bash $RS $R $O/${P}f_held_$v.png $v 84 complaint 20
  bash $RS $R $O/${P}f_stamp_$v.png $v 84 stamp 8
done
python $HERE/farboard.py $C/${P}board_far.png $O "rest front=${P}f_rest_front" "complaint t20 front=${P}f_held_front"   "stamp t8 front=${P}f_stamp_front" "rest 3q=${P}f_rest_three-quarter" "complaint t20 3q=${P}f_held_three-quarter"   "stamp t8 3q=${P}f_stamp_three-quarter"

# ---- clip frames
args=""
for f in "complaint 3 front" "complaint 5 front" "complaint 8 front" "complaint 11 three-quarter" "_review_complaint 16 front"          "_review_complaint 22 three-quarter" "complaint 61 front" "complaint 62 front" "complaint 64 front"          "stamp 0 front" "stamp 5 front" "stamp 9 three-quarter" "stamp 11 front" "stamp_3 15 front" "refuse 1 front"          "refuse 3 front" "_review_payday 7 front" "_review_tic_farmer 20 right" "_review_tic_mason 6 right"          "_review_glance 20 front" "_review_turn_to_wall 20 front" "_review_complaint_wall 20 three-quarter" "_review_unpaid 0 right"; do
  set -- $f; n=${P}w_$1_$2_$3; bash $RS $R $O/$n.png $3 240 $1 $2; args="$args $1_t$2_$3=$O/$n.png"
done
bash $RS $RV/mount_nitwit.bbmodel $O/${P}w_nitwit_front.png front 240 _review_nitwit 0
args="$args nitwit_(lamp_amber)=$O/${P}w_nitwit_front.png"
python art/machines/kit/board.py $C/${P}board_work.png 6 $args

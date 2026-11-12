#!/bin/bash
# Renders the Injection Rig's review boards from the review copies (run from the repository root, one render at a time).
#   bash art/machines/injection_rig/rig_boards.sh [prefix]
P="${1:-}"
CAP=${CAPTURES:-captures}/injection_rig
O=$CAP/work
C=$CAP/boards
R=build/machines-review/injection_rig_review.bbmodel
E=build/machines-review/injection_rig_empty_review.bbmodel
RS=art/machines/kit/render.sh
FB=build/machines-review/scratch-injection_rig/farboard.py
mkdir -p $O $C
args=""
for v in front three-quarter right back iso top; do
  bash $RS $E $O/${P}re_$v.png $v 360
  bash $RS $R $O/${P}rl_$v.png $v 360 _review_loaded 0
  args="$args empty_$v=$O/${P}re_$v.png loaded_$v=$O/${P}rl_$v.png"
done
python art/machines/kit/board.py $C/${P}board_rest.png 4 $args
for v in three-quarter front right; do
  bash $RS $R $O/${P}f_rest_$v.png $v 128 _review_loaded 0
  bash $RS $R $O/${P}f_aim_$v.png $v 128 _review_ok90 25
  bash $RS $R $O/${P}f_jab_$v.png $v 128 _review_ok90 30
done
python $FB $C/${P}board_far.png 3 \
  "rest 3q=$O/${P}f_rest_three-quarter.png" "inject t25 aimed (phi 90) 3q=$O/${P}f_aim_three-quarter.png" "inject t30 the jab 3q=$O/${P}f_jab_three-quarter.png" \
  "rest front=$O/${P}f_rest_front.png" "t25 aimed front=$O/${P}f_aim_front.png" "t30 jab front=$O/${P}f_jab_front.png" \
  "rest right=$O/${P}f_rest_right.png" "t25 aimed right=$O/${P}f_aim_right.png" "t30 jab right=$O/${P}f_jab_right.png"
args=""
for f in "_review_ok90 0 three-quarter inject_t0" "_review_ok90 6 three-quarter inject_t6_lift" "_review_ok90 9 three-quarter inject_t9_windup"          "_review_ok90 14 three-quarter inject_t14_swing" "_review_ok90 21 three-quarter inject_t21_tipping" "_review_ok90 25 right inject_t25_aimed"          "_review_ok90 27 right inject_t27_windup" "_review_ok90 30 right inject_t30_JAB" "_review_ok90 30 three-quarter inject_t30_JAB"          "_review_ok90 34 three-quarter accepted_t34_withdrawn" "_review_ok90 39 three-quarter accepted_t39_home" "_review_ok90 45 three-quarter accepted_t45_holster"          "_review_rej90 30 top rejected90_t30" "_review_rej90 33 top rejected90_t33_recoil" "_review_mut45 31 top mutated45_t31_shudder"          "_review_ok30 30 three-quarter aim30_t30" "_review_ok180 30 three-quarter aim180_t30" "_review_ok90 30 front aim90_t30_front"          "_review_cancel20 21 three-quarter cancel_at20_t21" "_review_cancel20 28 three-quarter cancel_at20_t28" "_review_cancel20 32 three-quarter cancel_at20_t32_holster"          "refuse 2 front refuse_t2" "refuse 6 front refuse_t6" "prime 5 three-quarter prime_t5"; do
  set -- $f; n=${P}w_$4_$3; bash $RS $R $O/$n.png $3 240 $1 $2; args="$args $4($3)=$O/$n.png"
done
python art/machines/kit/board.py $C/${P}board_work.png 6 $args

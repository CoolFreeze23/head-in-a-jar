#!/bin/bash
# Renders the Cerebral Array's three review boards (run from the repository root, one render at a time).
#   bash art/machines/cerebral_array/array_boards.sh [prefix]
# rest: the plain review copy (cards pale, as at rest in game) and an empty copy (no heads, no cards: hidden on empty
# seats); work and far: the vote copy (cards coloured as the game tints them from their rise: seats 2, 6, 7 nay)
# for frames from the ripple on, the plain copy before it (the cards are pale until they rise).
P="${1:-}"
CAP=${CAPTURES:-captures}/cerebral_array
O=$CAP/work; C=$CAP/boards
R=build/machines-review/cerebral_array_review.bbmodel
RV=build/machines-review/cerebral_array_review_vote.bbmodel
RE=build/machines-review/cerebral_array_review_empty.bbmodel
RS=art/machines/kit/render.sh
SC=build/machines-review/scratch-cerebral_array
python - <<PY
import json
m = json.load(open('$R', encoding='utf-8'))
for e in m['elements']:
    if e['name'].startswith('si_') or e['name'].endswith('_card'):
        e['visibility'] = False
m['animations'] = []
json.dump(m, open('$RE', 'w', encoding='utf-8'))
PY
for v in front three-quarter right back iso top; do bash $RS $R $O/${P}r_$v.png $v 360; done
for v in front three-quarter; do bash $RS $RE $O/${P}e_$v.png $v 360; done
python art/machines/kit/board.py $C/${P}board_rest.png 4 "front (loaded: stand-in heads)"=$O/${P}r_front.png \
  three-quarter=$O/${P}r_three-quarter.png "right (+X)"=$O/${P}r_right.png back=$O/${P}r_back.png iso=$O/${P}r_iso.png \
  top=$O/${P}r_top.png "front, empty"=$O/${P}e_front.png "three-quarter, empty"=$O/${P}e_three-quarter.png
for v in front three-quarter; do
  bash $RS $R $O/${P}f_rest_$v.png $v 128 vote 0
  bash $RS $R $O/${P}f_gavel_$v.png $v 128 vote 8
  bash $RS $RV $O/${P}f_cards_$v.png $v 128 vote_anyway 41
done
python $SC/farboard.py $C/${P}board_far.png $O "rest front=${P}f_rest_front" "vote t8 gavel up, front=${P}f_gavel_front" \
  "anyway t41 cards up, gavel up, front=${P}f_cards_front" "rest 3q=${P}f_rest_three-quarter" \
  "vote t8 gavel up, 3q=${P}f_gavel_three-quarter" "anyway t41, 3q=${P}f_cards_three-quarter"
args=""
for f in "vote 0 front" "vote 6 three-quarter" "vote 8 front" "vote 10 three-quarter" "vote 16 three-quarter" \
         "vote 24 front" "vote 32 three-quarter" "vote 42 front" "vote 52 three-quarter" "vote_anyway 41 front" \
         "vote_anyway 43 three-quarter" "vote_anyway 48 front" "vote_anyway 51 three-quarter" "refuse 2 front" \
         "refuse 4 front" "vote 42 right"; do
  set -- $f; n=${P}w_$1_$2_$3; M=$RV; if [ $2 -le 13 ] || [ $1 = refuse ]; then M=$R; fi
  bash $RS $M $O/$n.png $3 240 $1 $2; args="$args $1_t$2_$3=$O/$n.png"
done
python art/machines/kit/board.py $C/${P}board_work.png 6 $args

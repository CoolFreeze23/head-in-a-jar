# README pictures of the Assembly Gantry: a Bulwark build from the front-right three-quarter on the arm's side, about
# six blocks off, then a Colossus build from seven blocks, pitched up, the arm telescoping to its shoulders. The
# stand-in second player presses Assemble (the use packet, then the screen's button packet) and steps out of shot.
@observe 20
wait 40
/time set 6000
/gamerule doMobSpawning false
/gamerule doDaylightCycle false
/gamerule doWeatherCycle false
/kill @e[type=!player]
/clear @s
/setblock 0 -60 0 headinajar:assembly_gantry[facing=north]
gantry 0 -60 0 bulwark
/tp @s 4.8 -59.4 -3.8 45 -1.7
bystander spawn 0 -60 -2 0
wait 20
hud off
bystander useblock 0 -60 0 north
wait 5
frames rm_bulwark_build 240
wait 4
bystander button 1
wait 2
bystander close
bystander tp -8 -60 -10 0
wait 250
/kill @e[type=headinajar:bulwark]
wait 20
gantry 0 -60 0 colossus
/tp @s 5.6 -59.0 -4.6 45 -13.9
bystander tp 0 -60 -2 0
wait 20
bystander useblock 0 -60 0 north
wait 5
frames rm_colossus_build 360
wait 4
bystander button 1
wait 2
bystander close
bystander tp -8 -60 -10 0
wait 370
inspect near colossus 4
report gantry readme builds done

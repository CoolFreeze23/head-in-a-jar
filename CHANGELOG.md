# Changelog

What changed for players in each release of Head in a Jar, newest first.

## 0.5.1 (2026-11-14)

- **Wear and tear you can read from across the yard.** A damaged mecha's panels dent on the side it was hit from
  most, a stage further at three quarters, half and a quarter of its health, and the dents come out again as the
  Assembly Gantry puts the plates on. Below a quarter of its health a walking frame limps at its usual speed, dragging
  the leg on the side it was hit from more, the arm on that side hanging. A 0.5.0 game and a 0.5.1 server (or the
  other way round) now decline to connect, the game's disconnect screen naming Head in a Jar's channels as the
  mismatch, rather than disagree about what a mecha is doing.
- **One rule for handling a player.** The Bulwark's lift, the Kite's talons, the Tractor Beam and the Grapple Hook take
  a player only if that player trusts you or is on your team, as the Lunk's toss does; the Hopper's landing and the
  Wheelhouse's ram move players by the same rule. Anyone else: "... has not authorised handling. Ask them to trust
  you." A refused hook costs nothing.
- **Getting off a flier puts you on the ground** beside it, instead of on its back to fall from there (four blocks from
  a ghast, and a knock on the glass).
- **Old goggles wait for you.** Goggles coming back from the old Curios head slot to a creative player with no room
  anywhere stay at their feet, as they do for everyone else, instead of vanishing at the first touch.
- **Parked mecha are cheap to draw under shader packs too.** With Iris 1.8.12 and BSL Classic v10.0 or
  Complementary Reimagined r5.5.1, a parked machine is drawn from its kept look through the pack's own programs, its
  shadow included, for a fifth or less of what it cost, and looks the same; any other pack or Iris version draws it as
  before.
- **The Assembly Gantry reaches the tall frames.** Its arm telescopes: for a Colossus or an Executive the upper arm
  and the forearm slide out along steel rods, so the welder reaches their shoulders instead of striking an arc from
  the knees, and most welds now land on the tick their part does. A few of the highest still come a moment late or
  as an arc.
- **Power beside the Gantry no longer stops a build.** A frame wider than the pad may now overhang what stands beside
  it, such as the Galvanic Pile that powers the Gantry. Before, a pile set beside it, as its tooltip says, made
  Assemble answer that something blocks the space over the pad. The space straight over the pad must still be clear,
  and so must the blocks beside it at the frame's head height: "The frame would stand with its head in the blocks beside the pad. Clear them up to its head height."
- Client and server must both be on 0.5.1.

## 0.5.0 (2026-11-14)

- **The mecha line, rebuilt.** All eight frames and the three Utility Shells are fully animated, from the build on
  the Assembly Gantry, module by module, to the collapse when wrecked, with your jar in the seat and every module on
  its socket. **Drive** in a cockpit is each frame's own move: the Hopper's Pogo Protocol, the Bulwark's Heavy Lifting,
  the Colossus's Siege, the Executive's premium jingle, the Wheelhouse's Rush Hour, the Skitter's Dragline, the Kite's
  Talon Lift and the Chimera's Graft. The Lunk holds what it tosses overhead first.
- **The Golden Parachute is a real parachute**: a klaxon, the restraint blown open, a gold canopy (or a tangle, with no
  room overhead) and a letter from the company.
- **Mecha modules that now do what they say**: the Signal Booster carries your link like a walking Relay Mast, the
  Bio-Scanner reads out the creature under your crosshair, Anchor Clamps grip the ground against knock-back, and
  shoulder hardpoints fire with the arm below them (alone in Siege). The Gantry and the HUD say when a mecha is built
  over its load lock; parked mecha no longer vent at passers-by; servers that forbid flight no longer kick pilots of
  flying, swinging or leaping mecha.
- **Every sound says what it is.** Every sound the mod plays has its own subtitle in both languages, and none borrows
  another thing's: the company jingle ends an operation and a printed body (no more level-up ding), a desk bell after
  the saw, a jar's wheels, roll, propeller and Hop Springs are heard, a weak link is static in your head that worsens
  with it, the Neural Socket clicks like a switchboard, and the Arc Cannon cracks instead of thundering. A room full of
  machines is heard as at most six sounds, identical machines together as one.
- **Parked mecha cost a tenth to draw**, kept as a copy of their look while nobody is aboard; boarding shows only the
  mod's own climb-out line, never the game's "Press ... to Dismount".
- **The Assembly Gantry, rebuilt.** A turntable and a welding arm: the arm welds each module home as the frame builds
  itself, the turntable presents the finished machine to whoever pressed Assemble, Repair ends with a weld pass and a
  charge lead from the reel, and Take apart is the arm's work with a wrench. Its lamp says ready, waiting, working,
  short of power or no.
- **Arms at work.** Every beam leaves its tool's muzzle and lands where you aim: the Plasma Lance (with a dimming
  visor), the Arc Cannon's jumping bolt, the Tractor Beam's ribbons, the Flamethrower's jet, the Welding Torch's arc.
  Each tool spins, kicks, braces, strikes or guards as it should, a fist's blow lands when the arm gets there, and the
  Manipulator Hand shows what it holds. A hot mecha's vents glow and its metal ticks; a damaged one sparks, smokes and
  limps.
- **DOLORES pages you.** The first time something important happens (your first day, your first moment as a jar
  with no hands, your head left out to dry, low charge, your first host, your first mecha), a memo arrives in chat
  with a beep, naming your keys and, with Patchouli, linking to the handbook page. `memos` in the client config turns
  them off.
- **A career ladder.** Twelve new advancements lead from your first ingot to your first mecha, each saying what to do
  next; **Orientation Day** is yours the moment you arrive.
- **The Employee Handbook, Second Edition.** Ten chapters in the order you need them, with the company's forms, memos
  and incident reports. Without Patchouli the written book has the same text page for page, with a contents page you
  can click. The first-day copy goes into your hotbar, not your hand.
- **A clearer screen.** The jar's three bars carry their own marks, a low bar is hatched as well as red, and the
  numbers show when they matter (`hudNumbers` keeps them up). The link bar points to your other half. The host, mecha
  and gene panels share one column at the top left, clear of chat, and the Neural Handshake's ring sits above the
  jar. Tooltips name the keys as you bound them, the Jar menu explains its slots, JEI has pages for some thirty more
  items, and Jade shows a host's abilities and your own head's saline and glass.
- **Taking a body, made visible.** The saw pops a head like a cork, the body shakes itself or pats the air where its
  head was, a jar locks onto a host with a brass bayonet collar, the handshake plays the company jingle, and sync
  shows as rings down the body.
- **The Commute.** Swapping pulls the old view away, with a teal band up into the body or down into the jar; the body
  powers down and up with three beeps.
- **Your body waits for you**: at attention, then fidgeting, then sitting down; it turns to you, waves and holds out its
  arms. A body that dies falls over, HR sends a card, and your own corpse sends up a flare only you can see.
- **Ripe hosts slump, sway, stumble and draw flies.** Embalming goes in with a sparkle.
- **Every jar shows its life support**: the saline stands at its level in the glass, a lamp on the lid shows the
  charge, and failing glass is heard to crack.
- **New default keys: Swap is Home, Secondary is mouse button 5**, so neither shares a key with Curios any more.
  Keys you set yourself are kept.
- **Fliers fly.** A ridden bat takes off now (it used to stay asleep), and high up on any flier, holding **Sneak**
  flies you down and lets you off near the ground instead of dropping you.
- **The Body Locker guards what it holds.** A locker with someone's body in it breaks only for its owner or someone
  they trust, and it no longer takes power from cables. The Curios head slot for goggles is gone: goggles count only on
  the jar, and goggles left in it come back to your inventory, Ender Chest or feet.
- **Every key answers.** **Swap** and **Drive** with your head on and gene keys that cannot fire all say why; a host's active genes fire with **Sneak**; a key another mod also uses is named once when you
  join.
- **One set of rules.** The Lacrimal Laser stops at walls. Lifting someone's mecha, or anything they ride, needs their
  trust. The Shock Lid zaps with no glass left, the Saline Recycler works for a set-down head, and a jar resting in an
  empty Cradle is topped up. Flat wheels fold away and say so. The Leech Jar never takes the last point of health.
  Turrets need to see their target, and their shots and blasts spare whoever they would never aim at. A Neural
  Socket's hotbar keys reach level 1 and leave your hotbar alone. Employee Mounts count on either half of a tall
  machine. A laced body refuses Embalming Fluid and a second lace, and keeps them.
- **Fixed:** a host's head stays hidden under Fresh Animations and other re-modelling packs; a corpse no longer lies
  half under the ground; a renamed Head Satchel counts; a dud serum says it was a dud; `/headoffice status` speaks
  your language, and `/headoffice trust` says when nothing changed.
- Client and server must both be on 0.5.0.

## 0.4.0 (2026-11-12)

- **The machine line, rebuilt.** All twenty-two company machines, from the Galvanic Pile to the Leech Jar, are working
  machines now: presses, paddles, dishes, shutters and spools move when they work, each has its own sounds, and a lamp
  on its front says ready, working, waiting or short of power.
- **Most machines are two blocks tall.** Leave the block above free when you place one ("No headroom. Needs two
  blocks of height."). Clicks, hoppers, pipes and cables work on either half. A machine from an older world under a
  ceiling keeps working at one block and takes its upper half within two seconds of the block above being cleared.
- **Every refusal shows on the machine**: a red lamp for two seconds and a rattle of the part concerned, with the line
  that says why. New lines: an empty Charging Dock, cooked food at the Galvanic Pile, an empty bucket at a Saline
  Mixer with too little saline.
- **The Body Locker keeps a body for its owner.** A body checked in from now on goes back only to whoever checked it
  in, or to someone they trust (`/headoffice trust`). Bodies already stored go to anyone, as before.
- **The Injection Rig takes a moment.** The shot lands a second and a half after **Inject**; step away and it is
  called off, with nothing used.
- **Machines that watch you.** Relay Masts turn their dishes along the link they carry, the Signal Jammer's turbine
  spins up when it cuts a link, a Turret Pedestal patrols and swings onto its target, and a placed Jarred Head turns to
  look at you.
- **Tesla Nodes face each other.** Each node's ball turns to its partner, the arc runs ball to ball, and a pair linked
  in an older world finds its sender on the first tick.
- **The Safety Placard's notices are painted on the board**, in English or Brazilian Portuguese with your game.

## 0.3.0 (2026-11-12)

- **Telekinesis you can see.** A held creature, item or block hangs out in front of the jar where you can see all of
  it, joined to the jar by a glowing line, and swings and lags behind with its weight. The ability key sets it down,
  attack throws it, and the hold says why it ended when it runs out of charge or reach. A grab that fails says why
  (too heavy, a boss, a machine, nothing in reach). Lifting another player, their placed head or their body takes
  their trust: `/headoffice trust add`.
- **Effects for the big moments.** Swapping between your body and your head, the Lacrimal Laser, the arcs between
  linked Tesla Nodes and a guardian turret's lock beam are drawn properly now, most with their own sounds. `screenShake` in
  the client config (0-100) sets how much they may move the camera; the photosensitive option turns it off.
- **Warned before it costs you.** A jar's glass says every knock it takes, and warns at the last ones before a hit is
  a real death. A head left near fire, a jar smashed by a mob and a wrecked mecha all say what happened (a wrecked
  mecha throws you clear). The Syringe never takes a creature's last health, and a printed body that
  has to wait for you says so.
- **More clicks answer.** The company's items used at nothing or on a colleague say where they go; a friend you trust
  can top up your jar with a Saline Bottle or a Glass Patch; clicks while plugged into a Neural Socket say how to
  unplug; hitting your own placed head no longer cracks it.
- **The Neural Handshake now always starts when you first board a host** (it used to be skipped now and then), and
  the HUD says that the host's legs wait until it is done.
- **Hosts ride at a sensible pace.** A ridden cow, pig or other land creature now tops out near a sprint (a cow was
  nearly twice that). Horses and the other vanilla mounts are unchanged.
- **The chair:** a seated patient can reopen the panel with use, and DOLORES says so truly when your head is piped to a
  Cradle instead of the tray.

## 0.2.0 (2026-11-12)

- **A new Separation Chair.** Rebuilt and fully animated: the barrier comes down, the hood scans you, the blade swings,
  and a claw carries your head over into the jar waiting on the chair's tray, then hands it to you. Everyone nearby sees
  the same operation at the same moment, even if they walk up halfway through. The chair needs a little room to its left
  and in front for the tray and the hood, and says so if something is in the way.
- **DOLORES speaks.** The chair's attendant talks you through the operation out loud, in English or Brazilian
  Portuguese (whichever language your game is in); the patient hears her muffled. Her volume is the **Voice/Speech** slider in
  Music & Sounds, and `doloresVoice` in the client config turns her off (the lines still show on screen).
- **Every click answers.** Things that used to do nothing when clicked now say why not and what to do instead, in one
  short line: a bare jar at a chest, the jar menu key with your head still on, a chair already in use, a Cradle with no
  power, and many more. A bare jar can now work every Noggin & Sons block with a menu or a status, while vanilla blocks
  still need a Manipulator Arm.
- **The handbook on your first day.** Every player is handed the Employee Handbook when they first join (players in
  existing worlds get theirs at their next login). A book and a Surgical Steel Blend make a new copy; without Patchouli
  it is a written book.
- **The power numbers, on one scale.** Mecha batteries are a quarter of their old size, and a mecha parked on the
  Assembly Gantry charges from it; Reinforced and Executive jars hold more charge than a basic one; shells, the
  Propeller, the Signal Jammer and idle turrets drain far less; only one Dragon Head counts in a Cerebral Array. Older
  saves load: a mecha charged above its new battery size starts full.
- **The jar crash is fixed.** Placed jarred heads, and turrets, mounts, Arrays and sockets holding a head, no longer
  crash the game when they come into view.
- **And more:** the Charging Dock charges a jar standing on it or a head set down on it; a Rover drives at a Rover's
  pace and a crash costs one knock, not the whole jar; held keys no longer carry over when your link breaks; getting out
  of a mecha or a shell puts you beside it; a weak link shows the clicks it drops; the Galvanic Pile, the Gantry, the
  Dock and the Cradle explain themselves in their tooltips; a key shared by Sprint and the ability is explained once.

## 0.1.0 (2026-11-08)

- First public version: the Separation Chair, life-support jars and the linked body, mob hosts and Utility Shells,
  mecha frames and modules, the Genetics Department, heads at work, the handbook, in English and Brazilian Portuguese.

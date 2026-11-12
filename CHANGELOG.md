# Changelog

What changed for players in each release of Head in a Jar, newest first.

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

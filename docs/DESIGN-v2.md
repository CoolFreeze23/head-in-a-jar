# Head in a Jar v2: design (NeoForge 1.21.1, CrazyCraft 5.0)

This document replaces `DESIGN.md` (v1) as the plan of record. v1 stays valid as the description of the code that
exists today. Every number here is a starting value for tuning, not a measured value.

**Conventions.** t = ticks (20 t = 1 s). FE = Forge Energy. mB = millibuckets. HU = heat units. kg = mecha mass.
"Player walk" speed is the attribute value 0.1. Q = link quality (0 to 100). Tiers T0 to T5 are defined in section 5.
**Core / Nice / Stretch** mark how essential an integration or feature is.

---

## 0. Names and pitch

### 0.1 Name

The mod id stays `headinajar` so v1 saves, configs and lang keys keep working. The display name changes.

| Candidate | Why | Verdict |
|---|---|---|
| **Head Office** | The whole mod is a fictional company. "Head Office" is a pun that keeps working: the guide is the employee handbook, tiers are job titles, machines are departments | **Recommended** |
| Noggin & Sons | The company name inside Head Office; good as a brand on machines | Use as the in-world company |
| Headspace | Clean, but sounds like a meditation app | Runner-up |
| Neck & Neck | Funny once | No |
| Jarhead Industries | Military slang clash | No |
| Out of Body Experience | Describes the link, not the mecha or genetics | No |
| Brain Trust | Good for the Cerebral Array, too narrow for the mod | Use for an advancement |

pt_br display name: **Cabeça no Pote** (the pun does not translate; the literal name is funnier in Portuguese).

### 0.2 One-line pitch

*Noggin & Sons would like to congratulate you on your elective decapitation.* Build a booth that takes your head off,
walk your headless body around by wireless telepresence, park your head somewhere safe, wear dead monsters, sequence
their blood, and bolt it all into a modular mecha that you pilot from a jar of blue fluid in its chest.

### 0.3 Tone

Deadpan corporate safety culture applied to obviously insane science. Nobody in the fiction thinks any of this is
strange. The humour is in the paperwork, never in gore: there is no blood spray, no visible wound. Cuts happen behind
frosted glass with a sound effect and a screen fade. The neck stump is a neat brass collar with a lens in it.

The voice of the mod is **Dr. Cornelius Noggin**, founder of Noggin & Sons, who has been a head in a jar since "the
incident of '62". He narrates the guide, signs every safety notice, and speaks through every machine's status line.
The Separation Booth has its own voice, **DOLORES** (Decapitation Operations Liaison, Outpatient Removal and Elective
Separation), who is relentlessly polite.

Sample lines (all text, all localised; machines make non-verbal chirps and burbles, no voice acting):

| Where | Line |
|---|---|
| Booth, on sit-down | "Welcome to Separation Services. Please keep your arms inside the booth. You will not be needing your head." |
| Booth, countdown | "Please count backwards from ten. You may stop at any time. Most clients stop at seven." |
| Booth, done | "Separation complete. Your head will be with you shortly. It is, technically, already with you." |
| Booth, botched cut | "Oopsie." |
| Link HUD, low signal | "Your neck is experiencing higher than normal latency. Please hold." |
| Link lost | "Connection to body lost. Have you tried turning your neck off and on again?" |
| Gene Editor, low stability | "Mutation risk is within acceptable limits for an employee of your seniority." |
| Mecha, overheating | "Your chassis is now hotter than the sun's opinion of you." |
| Cerebral Array | "Array employees are compensated in broth." |
| Guide, title page | "Noggin & Sons Employee Handbook, 14th edition. Now with fewer lawsuits." |
| Safety placard (block) | "THIS MACHINE HAS REMOVED 0 HEADS WITHOUT CONSENT. LET'S KEEP IT THAT WAY." |

Safety Placards are a decorative block (like a sign, 12 random notices, right-click to cycle). They are the cheapest
way to make a base feel like Head Office.

### 0.4 What v2 changes from v1

| Area | v1 | v2 |
|---|---|---|
| Taking your head off | Use the Life Support Jar item | Build and sit in the **Cranial Separation Booth** |
| Seeing and moving | You *are* the jar; a bare jar can't move | You can be the **jar** or your **linked headless body**, which sees through a collar camera; swap between them |
| Charge | 0 to 1000 abstract charge | FE (1 v1 charge = 100 FE; old saves migrate by x100) |
| Life support | Charge only | Charge (FE) for modules, **Cranial Saline** for the head, **Jar Integrity** as the jar's own health |
| Robot bodies | Three fixed robots | Three fixed **Utility Shells** (the v1 robots) plus fully modular **mecha** |
| Mob bodies | Saw a mob, wear it | Saw a mob, preserve it, **neuro-sync** with it over time; Necromancy Minions as custom hosts |
| Genetics | None | Blood, DNA sequencing, gene library, splicing, serums, mutations |
| Heads of others | Villager traders, player heads | Plus villager **staff** for machines, mob heads as **turret brains** and **generators** |

---

## 1. Gameplay loops

### 1.1 Ten seconds

- Walk your headless body through a cave, watching the signal bars. Bars drop: you hear static, the image tears,
  and you choose between pushing on (risking a link drop) or placing a Relay Mast.
- In a mecha: hold the left trigger to drill, watch the heat bar climb, let go before 85%, vent, keep going.
- In a host body: press the ability key and blink 24 blocks as an enderman with your jar where its head was.
- At the sequencer: hit the right base as each letter crosses the read head.

### 1.2 Ten minutes

- A run: dock your head at home, walk your body out to a dangerous place, fight. If the body dies you wake up at home
  in your jar, and you plan the retrieval.
- A hunt: take blood samples from five species you haven't sequenced, come home, centrifuge, sequence, file the genes.
- A build session: put a new arm module on the mecha at the Assembly Gantry, tune its sliders, take it out for a test,
  come back and re-tune because it overheats.

### 1.3 Ten hours

- Climb the corporate ladder from **Intern** to **Chairman of the Board** (T0 to T5).
- Build a home lab: booth, cradle, power, saline line, gene lab, mecha bay, a Cerebral Array humming in the basement
  and jarred villagers staffing the machines.
- Collect genes from across the pack (OreSpawn, Twilight Forest, Aether, Mowzie's, Cataclysm, Brazil Legends,
  Naturalist...) and combine them into a Chimera bio-mecha that is uniquely yours.
- Sync with signature host bodies (enderman, ravager, T-Rex, Headless Mule) and unlock their deepest abilities.

---

## 2. Forms

The player is always in exactly one form. The form is stored in the v1 `JarState` attachment (extended), synced to
the player and everyone tracking them.

| Form | What the player entity is | Camera | Hitbox | Moves by |
|---|---|---|---|---|
| `BODY` | normal player | vanilla | vanilla | walking |
| `LINKED` | **your own headless body**, driven over the neuro-link | collar camera, eye height 1.32 | 0.6 x 1.45 | walking (fits under 1.5-block gaps) |
| `JAR` | the jar (v1) | inside the jar | 0.62 x 0.72 | jar modules only |
| `HOSTED` | the jar riding a mob body or Utility Shell | at the host's neck anchor | jar size, riding the host | steering the host |
| `PILOT` | the jar seated in a mecha cockpit | cockpit, or chase camera | jar size, riding the mecha | steering the mecha |
| `SOCKETED` | the jar plugged into a Neural Socket (v1) | the jar, or a linked camera | jar size | does not move |

**Where the head is** is tracked separately from the form, in a server `HeadRegistry` (SavedData keyed by player UUID):

| Head location | Meaning |
|---|---|
| `ON_NECK` | BODY form |
| `PLAYER` | the player entity *is* the jar (JAR, HOSTED, PILOT, SOCKETED) |
| `ENTITY` | a `HeadJarEntity` standing in the world (you are LINKED elsewhere) |
| `CRADLE` | docked in a Life Support Cradle block entity |
| `CARRIED` | carried by a player (your own body, or a friend), stored on the carrier's attachment |
| `MECHA` | sitting unoccupied in a mecha cockpit while you walk around LINKED |

**The head is never an ItemStack.** It is always a player, an entity, a block entity's content or an attachment on a
carrier. This removes every duplication and "head destroyed in a modded container" problem. (Jarred *villager* and
mob heads are items; only player heads follow this rule.)

### 2.1 Layered life

A decapitated player effectively has two health pools, which is the main reward for the risk and the cost:

1. **The body** (LINKED form, or the host, or the mecha). When it reaches 0, you do not die. You wake up in your
   head, wherever the head is. See 4.7 and 14.1.
2. **The jar** has **Jar Integrity**: 10 (Life Support Jar), 20 (Reinforced), 30 (Executive). Damage to a JAR-form
   player, and overflow damage from a destroyed host or mecha, goes to integrity first, then to the player's health.
   When both are gone, the player dies for real and respawns as a normal `BODY` (v1 rule: death always resets).

Docked heads (Cradle) take no damage at all. Carried heads take 25% of explosion damage the carrier takes.

---

## 3. The vision and neuro-link system

### 3.1 The problem

Once the head is off, where is the camera and what does the player control? The brief's idea: goggles on the head,
a wireless device on the torso, and you "see as if your head was still on", then move the head to other machines.

### 3.2 Models considered

| # | Model | Summary | Verdict |
|---|---|---|---|
| A | **Puppet Body (chosen)** | The player entity *is* the headless body. The camera sits in the Collar Cam on the neck. The head is a separate thing in the world that must stay within link range. Switching to the head is a **swap** (the player entity moves to the head; the body is left as v1's Headless Body) | **Chosen** |
| B | Remote Pilot | The player entity stays the jar. The body is an NPC; the client's camera is set to the body (`setCameraEntity`) and inputs are forwarded | Rejected: the player's server position stays at the jar, so chunks, reach checks, mining, item use, damage and every other mod see the wrong entity. It means re-implementing the player |
| C | Umbilical Cord | Head and body joined by a physical tube of fixed length | Rejected: a 16-block leash is no fun. Kept as a cosmetic: the Booth's training cord in the tutorial |
| D | Cosmetic only | Head comes off visually, nothing else changes | Rejected: no gameplay |
| E | Camera Drone | The head flies around as a drone and the body is passive | Rejected as the main model, but it is what JAR form with a Propeller module already gives you |

**Why A.** Every vanilla and modded system keeps working because the player entity is still a full player with hands,
reach, inventory, chunk loading and hitbox. The only new rules are the link (a distance check on the server), the
camera height (`EntityEvent.Size`, which v1 already uses), the render (hide head and hat, draw the collar) and the
swap (spawn and remove entities, teleport the player, which v1's detach and reattach already do). It also makes the
brief's goggles idea literal: the **goggles on the head** show the **Collar Cam's** feed, and the head's motor signals go
back to the **Collar Receiver**. The link is two-way and both ends are items you craft and upgrade.

### 3.3 Hardware

| Item | Where | Job | Tiers |
|---|---|---|---|
| **Relay Goggles** | worn by the head (rendered on the jar) | receives the video feed, transmits motor signals; sets base range | Mk I copper 48 blocks; Mk II gold 160; Mk III diamond 640; Quantum Goggles: unlimited in a dimension |
| **Collar Cam** | bolted to the neck stump (rendered as a brass cap with a lens) | camera plus motor receiver; holds Collar upgrades | one item, 4 upgrade slots |
| **Relay Mast** | block, 1 x 1 x 3 | extends the network: radius 96 (Mk I), 192 (Mk II); stores 100k FE, uses 1 FE/t while relaying | 2 |
| **Entangled Pair** | 2 items, one in the goggles, one in the collar | makes the link work across dimensions | T4 |
| **Signal Jammer** | block | makes Q x0.3 for others' links within 16 blocks (PvP and base defence; owner and trusted players immune) | 1 |

**Collar Cam upgrades** (4 slots, install in the jar menu or at the Booth):

| Upgrade | Effect |
|---|---|
| Wide Lens | FOV +10 |
| Periscope Stalk | eye height back to 1.62 (vanilla); hitbox back to 1.8 tall |
| Night Optics | night vision through the feed |
| Waterproof Seal | removes the underwater penalty |
| Shielded Housing | immune to EMP events |
| Antenna Whip | +25% range |
| Noise Filter | halves visual static (gameplay effects of low Q still apply) |
| Thermal Overlay | living entities glow (red warm blood, blue cold, grey undead) |
| Zoom Optic | hold the zoom key for x3 zoom |

### 3.4 What the player controls, and where the camera is

| Situation | You are | Camera | You can |
|---|---|---|---|
| LINKED, head docked | your body | Collar Cam (1.32 high, or 1.62 with Periscope) | everything a player can do |
| LINKED, head carried on your belt | your body | Collar Cam | everything; link is always perfect (distance 0) |
| LINKED, "Jar Cam" key held | your body (standing still) | the head's own eyes, wherever it is, if the client can see that entity | look around only |
| JAR | the jar | the jar | jar modules; **Link** key to swap into your body if Q > 20 |
| HOSTED / PILOT | the jar in a host or mecha | host neck or cockpit; F5 gives a chase camera | the host's or mecha's controls |

The **Swap** key (default `K`, polled, controller: hold D-pad down) moves your consciousness between body and head:

- **LINKED to JAR:** a `HeadlessBody` (v1 entity) is spawned where you stand, wearing your armour. The player entity
  is teleported to the head and becomes JAR form. The head entity is removed (or the Cradle marks itself occupied).
- **JAR to LINKED:** requires Q > 20 at the body. The player is teleported to the body, the body entity is removed,
  and a `HeadJarEntity` is left where the jar was (or the Cradle keeps it).
- The swap plays a 0.5 s static wipe on the client.

### 3.5 Link quality

The server computes Q every 10 t for each LINKED player and syncs it (one byte) when it changes.

```
range    = goggles range x (1 + 0.25 per Antenna Whip) x (2 if a mecha Signal Booster is the hub)
distance = shortest path head -> (relay masts, each hop within the mast's radius) -> body
base     = 100                                 if distance <= 0.5 x range
         = 100 x (1 - (distance - 0.5r)/(0.5r)) if 0.5r < distance <= r
         = 0                                   beyond range
Q        = base x product of environment factors, x 0.97 per relay hop
```

| Environment factor | x | Notes |
|---|---|---|
| Body underwater | 0.6 | removed by Waterproof Seal |
| Body deep underground (sky light 0 and more than 16 below the heightmap) | 0.8 | radio in rock |
| Thunderstorm | 0.85 | |
| Within 8 blocks of an active nether portal, end gateway, Immersive Portals or Portal Gun portal | 0.7 | "portal hum" |
| Darkness effect (Warden, sculk) | 0.5 | "the sculk is eating your bandwidth" |
| Elder Guardian's Mining Fatigue | 0.5 | neural interference |
| Jar Integrity below 25% | 0.7 | |
| Inside an enemy Signal Jammer's radius | 0.3 | |
| HBM radiation (if HBM is present and the body's dose is high) | 0.7 | Nice |

**Ping** shown on the HUD is cosmetic: 5 ms + 2 ms per 16 blocks + 15 ms per relay hop.

### 3.6 What low Q does

| Q | Visual | Gameplay |
|---|---|---|
| 70 to 100 | clean, faint scanlines | none |
| 40 to 69 | scanlines, occasional horizontal tear, colour fringing | none |
| 20 to 39 | heavy static, HUD flicker, picture drops a frame now and then | input stutter: every ~2 s, movement input is zeroed for 2 t (client side, in the v1 `MovementInputUpdateEvent` hook) |
| 1 to 19 | "WEAK SIGNAL" banner, near-black picture with static snow | stutter every 1 s for 4 t; 25% of attack and use clicks drop ("PACKET LOSS") |
| 0 for 5 s, or an instant break | "CONNECTION TO BODY LOST" | **link break**: automatic swap into the head (section 3.7) |

**Instant breaks** (no 5 s grace): the body changes dimension without an Entangled Pair, an EMP event, the head's jar is
destroyed, the Collar Cam is removed.

**EMP events:** lightning within 6 blocks of the body; a charged creeper explosion within 8; our EMP Grenade
(T3 throwable, also shuts mecha down for 5 s); HBM nuclear blasts or EMP devices if HBM fires an event we can hook
(Stretch; otherwise our own check for HBM's explosion entities near the body). Shielded Housing makes the body immune.

**Accessibility:** a client option turns off flashing and tears (*Photosensitive mode*) and a slider scales static
intensity from 0 to 100%. The gameplay effects of low Q stay, shown as a plain text bar instead.

### 3.7 Link break

1. The body stays where it was as a `HeadlessBody` (v1), standing, wearing your armour. It is not dead.
2. You are swapped into your head (JAR form) at the head's location.
3. To get the body back, go within range (as a jar with modules, in a mecha, via masts) and press Swap, or have a
   friend carry your head out. A Body Recall Beacon (T3 jar module) walks the body home on its own along a straight
   line at walking speed while its chunk is loaded.

**"The commute is free; the body isn't."** A voluntary unlink (Swap at any Q) is effectively a one-way trip home that
leaves your body behind. This is intended. Config `allowManualUnlink` (default true).

### 3.8 HUD

```
+--------------------------------------------------------------+
| [|||||] 92%  12 ms   HEAD 140 m  NE         * REC  DAY 14 |  <- top bar, fades after 5 s
|                                                              |
|              (scanlines, alpha = (100 - Q) x 0.6%)           |
|                                                              |
| SALINE [#######---] 71%     CHARGE [#########-] 88 kFE       |
| JAR INTEGRITY [##########] 20/20                             |
+--------------------------------------------------------------+
```

- Rendered as GUI layers (`RegisterGuiLayersEvent`), not post-processing shaders, so it works with Iris and Sodium.
- Scanlines and snow are two 64 x 64 tiling textures scrolled per frame. Tears offset strips of the HUD texture, not
  the world (cheap, shader-safe).
- `autohud` (installed) can hide it like other HUD layers.

### 3.9 Carrying heads

- **Head Satchel** (T1, Curios `belt` slot): carry your own head. Pick it up (sneak-use the head entity or the Cradle)
  while LINKED. The head renders at your hip. Link distance is 0.
- **Carrying someone else's head:** sneak-use their jar. Only allowed if they trust you (`/headoffice trust`, a team,
  or they are JAR form and accept a prompt). They can always Let Go (v1) or press Swap to leave.
- Placing a carried head: use on the ground, on a Cradle, or into a mecha cockpit.

---

## 4. The Cranial Separation Booth

### 4.1 Structure

A 3 x 3 x 4 multiblock, like a phone booth crossed with a dentist's chair.

```
Rows are back / middle / front. Front is where you walk in.

Layer 4 (roof)     Layers 2 and 3        Layer 1 (floor)
C C C              C C C                 C C C        C = Surgical Casing (or a Port)
C X C              C . C                 C H C        G = Booth Glass
C C C              G . G   <- doorway    C C C        X = Excision Head (above the chair)
                                                      H = Operating Chair
```

- 32 blocks: 26 Surgical Casing, 4 Booth Glass, 1 Operating Chair, 1 Excision Head. The middle of the front row on
  layers 2 and 3 is the open doorway; the space above the chair is open.
- Any casing may be swapped for a **Port**: Power Port (FE), Fluid Port (saline), Item Port (jars, goggles, collars).
- The **Booth Console** goes on any outside face. Use a **Surgical Wrench** on it to form the booth. While holding the
  **Booth Blueprint** (a paper item from the guide), a hologram of the missing blocks shows (ghost blocks, like
  Immersive Engineering's manual). Formed casing renders as one connected model; unformed it is plain blocks.
- Fallback for phase 1 (if the multiblock slips): a single **Separation Chair** block with the same GUI and sequence,
  no hologram, no ports. Its recipe needs the same parts, so nothing is lost when the multiblock arrives.

### 4.2 Recipe gates (T1)

| Block | Recipe |
|---|---|
| Surgical Casing (x4) | 4 Surgical Steel, 4 stone bricks, 1 Neuro-Copper Wire |
| Booth Glass (x2) | 2 glass, 1 Surgical Steel, frosted with 1 white dye |
| Operating Chair | 3 white wool, 2 Surgical Steel, 1 Servo, 1 lever |
| Excision Head | 1 Surgical Steel block, 1 Servo, 1 Lens, 1 shears, 1 redstone block |
| Booth Console | 1 comparator, 3 Neuro-Copper Wire, 1 glass pane, 2 Surgical Steel |

### 4.3 Running costs

| Input | Per separation |
|---|---|
| Empty Life Support Jar (or Reinforced, Executive) | 1 |
| Relay Goggles (any tier) | 1 |
| Collar Cam | 1 |
| Cranial Saline | 1000 mB |
| Energy | 40,000 FE. The booth refuses to start unless its 50,000 FE buffer holds the whole cost |
| Blade wear | +5% |

### 4.4 Operating sequence (240 t, 12 s)

The client sits in the chair (use it). The Console GUI opens on the seated client only.

**Waiver.** A paper-styled panel: "Form 27-B: Consent to Elective Separation. I, `<name>`, consent..." and a **Sign**
button that must be held for 2 s (controller: hold A). Only the seated player can sign. Nobody else can start a
booth with you in it.

| t | Beat | Sound | Client view |
|---|---|---|---|
| 0 to 20 | restraints clamp, front shutter slides down | two clanks, servo whine | vignette closes in |
| 20 to 60 | anaesthetic gas, DOLORES counts down | hiss, soft beeps | screen dims to 60%, green haze particles |
| 60 to 100 | scan: a green laser plane sweeps down | rising chirp | green line passes over the view |
| 100 to 140 | **the cut** (critical phase) | "shhk" at t = 120 | black at t = 120 |
| 140 to 180 | gantry claw lifts the head into the jar; saline pours; lid screws on | glug, squeak | black with muffled bubbles |
| 180 to 210 | goggles fitted; Collar Cam bolted to the torso | three ratchet clanks | black |
| 210 to 240 | handshake | static burst | static resolves into the Collar Cam view; HUD boots "NEURO-LINK 2.1 ... OK" |

**Result.** The player stands up in `LINKED` form. A `HeadJarEntity` with the player's skin head, goggles on, sits
on the booth's outlet shelf (rendered inside the glass). If a **Life Support Cradle** is within 3 blocks of the booth,
the head is docked into it directly. Upgrades: **Speed Cassette** (-25% time, stacks twice), **Anaesthetic Cassette**
(no Nausea afterwards), **Precision Blade** (no botch chance), **Auto-Dock** (Cradle range 8).

Without an Anaesthetic Cassette the player gets Nausea for 8 s after standing up.

### 4.5 Failure modes

| Failure | When | Result |
|---|---|---|
| Not enough power, saline or parts | before start | Console refuses with a red checklist line. Nothing is consumed |
| Power cut | t < 100 | abort: restraints release, nothing consumed except 25% of the energy |
| Power cut | t >= 100 | impossible by design: the buffer was full at start, so the critical phase always finishes |
| Someone breaks a booth block | during an op | booth blocks are unbreakable (hardness -1, blast resistance 3600) while an op runs |
| Seated player logs out | t < 100 | abort and refund |
| Seated player logs out | t >= 100 | op completes instantly; they log in LINKED |
| Server stops | any | op state is saved in the Console block entity; on load: t >= 100 finishes, t < 100 reverts |
| Seated player dies (poison, wither) | any | normal death; op aborts; goggles and collar are refunded |
| **Botched cut** | blade wear > 80% | chance = (wear - 80) x 2%. The head ends up upside down: the camera is rolled 180 degrees for 30 s (client camera roll via `ViewportEvent.ComputeCameraAngles`). DOLORES: "Oopsie." The Collar Cam is crooked: -10% range until refitted at the booth |

Blade wear is repaired with a **Replacement Blade** (Surgical Steel + diamond) or reduced by a Mason villager staffing
the booth (section 13.2).

### 4.6 Reversal

| Method | Where | Cost | Effects |
|---|---|---|---|
| **Suture Kit** | anywhere: as LINKED, use the kit on your own head jar while holding it (or as JAR, use it on your body) | string, slime ball, iron nugget, 1 Cranial Saline bottle | 3 s channel; Nausea 30 s; goggles and collar drop at 80% durability |
| **Booth, Reattach mode** | in the booth, with your body seated and your head in its Item Port or carried | 10,000 FE | no Nausea; goggles and collar returned intact; heals 4 hearts |
| v1 reattach (use your Headless Body as JAR) | anywhere | free | kept only for heads with no Collar Cam fitted (old v1 saves) |
| Death | | | resets to BODY (v1 rule) |

### 4.7 Getting a new body

If your body is lost (killed, rotted, stolen into lava):

- **Body Printer** (v1, now T2): 16 rotten flesh, 8 bone blocks, 4 redstone blocks, 4,000 mB Bio-Gel, 200,000 FE,
  60 s. Prints a fresh Headless Body owned by whoever started it, with a Collar Cam already fitted.
- A mob host, a Utility Shell or a mecha.
- Death.

### 4.8 Consent in multiplayer

- The booth only ever operates on the player who sat down and held **Sign**.
- Players cannot be seated by others (no Carry On or leash tricks: sitting requires the player's own use action).
- Field decapitation of players with the Surgical Saw is off by default and needs **mutual opt-in** (section 13.4).

---

## 5. Progression

Corporate job titles are the tiers. Advancements are named after them, and the guide unlocks chapters by tier.

| Tier | Title | Gate materials | Unlocks | Rough time in a fresh world |
|---|---|---|---|---|
| T0 | **Intern** | iron, copper, bone meal, glass | Employee Handbook (guide), Surgical Steel, Neuro-Copper Wire, Galvanic Pile, Saline Mixer, Syringe, Safety Placards, Leyden Jar battery | 0 to 1 h |
| T1 | **Associate** | redstone, amethyst, lapis | Separation Booth, Relay Goggles Mk I, Collar Cam, Life Support Jar, Life Support Cradle, Charging Dock (v1), Head Satchel, Suture Kit, basic jar modules, Utility Shells (Rover, Lunk) | 1 to 3 h |
| T2 | **Manager** | gold, diamond, nether quartz (Nether access) | Surgical Saw (mob hosts), Body Locker, Embalming Fluid, Neural Lace, Centrifuge, DNA Sequencer, Gene Archive, Goggles Mk II, Relay Mast Mk I, Assembly Gantry, **Hopper** light mecha, Wheelhouse, Neural Socket and Optic Sensors, villager heads, Body Printer, Bio-Reactor, Tesla Nodes | 3 to 8 h |
| T3 | **Director** | netherite scrap, blaze rods, ghast tears, prismarine | Gene Splicer, serums, **Chimera** bio-mecha, **Bulwark** medium mecha, **Skitter** quad, Goggles Mk III, Relay Mast Mk II, Cerebral Array, villager staff, EMP Grenade, Reinforced Jar, Spider Walker shell | 8 to 20 h |
| T4 | **Vice-President** | End: shulker shells, chorus, echo shards, elytra | **Colossus** heavy mecha, **Kite** ornithopter, Executive Jar, Entangled Pair, Quantum Goggles, Fusion Bottle core, Plasma Lance, boss-blood sequencing | 20 to 40 h |
| T5 | **Chairman of the Board** | nether star, dragon's breath, boss blood, OreSpawn apex drops | **Executive** mecha (4 gene ports, 2 co-pilots), Legendary genes, Board Room (multi-head Cerebral Array core), Dragon-head brain | 40 h+ |

**Gating rule.** Each tier's key item needs one material from the previous tier's world gate, so a player cannot skip
the Nether to reach mecha or the End to reach flight. Cross-mod materials are always *alternatives* behind tags
(`c:ingots/steel` from Railcraft or HBM can replace Surgical Steel in plating), never requirements.

### 5.1 Base materials

| Material | Recipe | Used for |
|---|---|---|
| Surgical Steel Ingot | blast: Surgical Steel Blend (iron ingot + bone meal + coal, shapeless) | casings, frames, tools |
| Neuro-Copper Wire (x4) | copper ingot + redstone + copper ingot (row) | every electric part |
| Lens | glass + amethyst shard + glass (row) | goggles, cameras, sensors |
| Servo | piston, 2 Surgical Steel, 1 Neuro-Copper Wire | motors, booth, mecha joints |
| Synapse Chip (T2) | gold ingot, 2 nether quartz, 2 Neuro-Copper Wire, 1 lapis | goggles Mk II, gene lab, mecha |
| Mecha Plating (T2) | 3 Surgical Steel, 1 iron block (or `c:ingots/steel` x4, or OreSpawn titanium) | frames, armour modules |
| Ender Coil (T3) | ender pearl wrapped in 4 Neuro-Copper Wire + 4 gold nuggets | link range, teleport modules |
| Quantum Knot (T4) | echo shard, shulker shell, Ender Coil, 2 popped chorus fruit | Quantum Goggles, Entangled Pair |
| Neural Tissue | spider eye + Bio-Gel bottle (or a Necromancy Brains organ) | Neural Lace, Ghost Core |

---

## 6. Power and fluids

### 6.1 Power: FE

FE is the energy system for every machine, jar and mecha. The pack has no mainstream FE generator line (no Mekanism,
Thermal or Create), so Head Office ships its own small, themed set. Railcraft Reborn's electric blocks expose FE and
connect directly. HBM's NTM uses its own HE network (`api.hbm.energymk2`): an **HE Transducer** block is a Stretch
integration (section 15).

There are no cables. Power moves by **adjacency** (machines push to neighbours, batteries in between) and by
**Tesla Nodes** (wireless, see below). This fits the "everything is wireless" theme and saves a cable network.

| Block | Tier | Output / storage | Fuel | Notes |
|---|---|---|---|---|
| **Galvanic Pile** | T0 | 20 FE/t, buffer 50k | rotten flesh 4,000 FE; spider eye 6,000; Necromancy organs 20,000; any `#c:foods/raw_meat` 3,000 | a stack of copper discs and frog legs. Twitches |
| **Leyden Jar** | T0 / T2 / T4 | 250k / 2M / 16M FE | | battery block; glows by charge |
| **Bio-Reactor** | T2 | 80 FE/t, buffer 200k | food: 2,000 FE per hunger point; Plasma 10 FE per mB; Bio-Sludge 20 FE per mB | burbling glass vat; Farmer staff +25% |
| **Tesla Node** | T2 | moves 2,000 FE/t over a wireless link | | link two nodes with the Surgical Wrench, range 16 (Mk I) or 48 (Mk II); 5% loss per link; arcs between linked nodes once a second |
| **Cerebral Array** | T3 | 20 to 1000 FE/t per head | Nutrient Broth | section 13.3 |
| **Charging Dock** (v1) | T1 | charges a jar standing on it at 500 FE/t | FE input | v1 block, now FE |

### 6.2 Fluids

A real fluid system, because saline is the core resource of being a head. Every machine exposes NeoForge fluid and item
handler capabilities, so any mod's pipes work (HBM ducts, Railcraft tanks). Inside Head Office, fluids move by
adjacency (machines output to a tank or machine on a configured face) and by bucket or bottle.

| Fluid | Made in | From | Used by |
|---|---|---|---|
| **Cranial Saline** (pale teal) | Saline Mixer | 1000 mB water + 1 sugar + 1 kelp + 1 redstone dust, 2,000 FE, 10 s | jars, Cradle, Booth, Body Locker |
| **Nutrient Broth** (amber) | Saline Mixer | 500 mB saline + 2 hunger points of any food | Cerebral Array, heads on the Neural Socket |
| **Bio-Gel** (pink, thick) | Gel Vat | 250 mB Plasma + 250 mB saline + 1 slime ball (or 1 Necromancy Muscle) | Body Printer, Chimera frames, mecha repair, Splicer base |
| **Embalming Fluid** (yellow-green) | Saline Mixer | 250 mB saline + spider eye + sugar + bone meal | preserving host bodies |
| **Blood** | Exsanguinator, Centrifuge input | from mobs; also accepts any fluid in `#c:blood` (Necromancy's blood fluid) | Centrifuge |
| **Plasma** (straw yellow) | Centrifuge | blood | Bio-Gel, Bio-Reactor |
| **Cryo Coolant** (ice blue) | Saline Mixer | 1000 mB water + 2 packed ice + 1 snowball, 1,000 FE | mecha Coolant Loop (x3 water's cooling) |
| **Bio-Sludge** (brown) | Bio-Reactor byproduct | 100 mB per 10 food points burned | Bio-Reactor fuel, Flamethrower fuel |

Bottles: every fluid has a bottle (250 mB) and a bucket (1000 mB). Bottles make early game simple: a Saline Bottle on
your own jar refills it.

### 6.3 Head life support

| Resource | Capacity (basic / reinforced / executive) | Drain | Refill | At zero |
|---|---|---|---|---|
| **Charge** (FE) | 100k / 200k / 400k | 2 FE/t idle (v1 rule x100), plus modules | Charging Dock, Cradle, redstone dust (5,000), redstone block (45,000), mecha | modules offline (v1) |
| **Cranial Saline** | 1000 / 1500 / 2000 mB | 1 mB per 12 s in JAR, HOSTED, PILOT and SOCKETED forms; none while docked, carried, or LINKED with the head docked | Saline Bottle, Cradle, Booth, mecha Life Support Recycler | "Dehydrating": -1 Jar Integrity per 30 s |
| **Jar Integrity** | 10 / 20 / 30 | damage | Glass Patch (glass pane + slime ball) +5; Cradle +1 per 10 s | overflow to player health |

1000 mB lasts 3 h 20 min of jar play, so saline is a soft timer for long expeditions, not a chore at home.

---

## 7. Machines and blocks

All machines share one GUI frame: vanilla grey panel, a teal "CRT" inset for status text (Dr. Noggin's line of the
moment), an energy bar on the left, fluid tanks on the right, an upgrade strip of 2 to 4 slots at the bottom, and a
side-config tab (which face pushes or pulls what). Every machine has a Jade tooltip (progress, energy, fluid, staff).

| Block | Tier | Purpose | Inputs | Outputs | Power | Upgrades |
|---|---|---|---|---|---|---|
| Cranial Separation Booth | T1 | take heads off and put them back | jar, goggles, collar, saline | LINKED player, head | 40k FE per op | Speed, Anaesthetic, Precision Blade, Auto-Dock |
| **Life Support Cradle** | T1 | dock a head: safe, refilled, and the hub of its owner's link | head, saline, FE | | 5 FE/t idle | Range Amplifier (+50% link range from this hub), Camera Mount, Speaker |
| Saline Mixer | T0 | make saline, broth, embalming fluid, coolant | water + items | fluids | 2,000 FE per batch | Speed, Tank |
| Galvanic Pile | T0 | early power | organic items | 20 FE/t | | |
| Leyden Jar | T0+ | battery | FE | FE | | |
| Charging Dock (v1) | T1 | charge a jar standing on it | FE | | 500 FE/t | |
| Surgical Workbench | T1 | crafting table for medical items, holds tools | | | | |
| Body Locker | T2 | store and preserve one body (yours or a host) | body, saline | | 10 FE/t | Cryo (stops decay fully) |
| Exsanguinator | T2 | draw blood from a restrained mob each 30 s | mob in a Restraint Frame on top | Blood fluid | 20 FE/t | Butcher staff x1.5 |
| Centrifuge | T2 | blood to DNA and Plasma | Blood Sample or 250 mB blood | DNA Fraction + 50 mB Plasma | 3,000 FE, 8 s | Speed |
| Gel Vat | T2 | make Bio-Gel | Plasma, saline, slime | Bio-Gel | 1,500 FE per 500 mB | |
| DNA Sequencer | T2 | read DNA into the gene library | DNA Fraction | Readout progress | 5,000 FE per read | Read Head Mk II, Librarian staff |
| Gene Archive | T2 | stores the gene library (team-wide) and blank Gene Disks | Gene Disks | | | Expansion Racks |
| Gene Splicer | T3 | build serums from genes | genes, Bio-Gel, catalysts | Serum Cassette | 20,000 FE, 60 s | Precision (+5 stability), Cleric staff |
| Injection Rig | T3 | inject serums into a host body, or into yourself | serum + target standing on the pad | | 5,000 FE | |
| Assembly Gantry | T2 | build, edit, repair and paint mecha | frame, modules, plating | mecha | 50 FE/t idle, 10k per build | Paint Bay, Repair Arm |
| Body Printer (v1) | T2 | print a fresh Headless Body | see 4.7 | Headless Body | 200k FE | |
| Neural Socket (v1) | T2 | organic computer | jar or mob head | redstone | 2 FE/t | |
| Optic Sensor (v1) | T2 | camera for sockets | | | | |
| Relay Mast | T2 / T3 | extend links | FE (own buffer) | | 1 FE/t relaying | |
| Tesla Node | T2 | wireless FE | | | 5% loss | Mk II range |
| Signal Jammer | T3 | block enemy links | FE | | 40 FE/t | |
| Cerebral Array | T3 | heads make power | jarred mob heads, broth | FE | | Board Room core (T5) |
| Employee Mount | T3 | hangs a jarred villager on a machine as staff | villager head, emeralds | | | |
| Turret Pedestal | T3 | a mob head as a turret brain | jarred mob head, ammo | | 10 FE/t | |
| Safety Placard | T0 | decoration, 12 notices | | | | |

### 7.1 GUI sketches for the key machines

**Life Support Cradle**

```
+---------------------------------------------+
| [FE]  ( jar render: your head, slow spin )  [SAL] |
| [##]   Owner: Alvin        Integrity 20/20  [###] |
| [##]   Link hub: ON   Range 160 m           [###] |
|        Body: 140 m NE  Q 92%                      |
|  [Swap into body]   [Jar Cam]   [Release]         |
|  upgrades: [ ][ ]                                 |
+---------------------------------------------+
```

Only the owner (and trusted players) can open it. "Release" pops the head out as an entity.

**DNA Sequencer** (minigame in 12.4)

```
+-----------------------------------------------+
| [FE]  sample: Enderman  quality 74            |
|        A  T  C  G        read head ---------- |
|        |  |  |  |                             |
|        ...letters scroll down the 4 lanes...  |
| accuracy 87%   gene progress: Ender Blink 64% |
|  [Read (minigame)]  [Auto-read x4 time, 60%]  |
+-----------------------------------------------+
```

**Assembly Gantry** and **Gene Splicer**: sections 11.5 and 12.5.

---

## 8. The jar and its modules

### 8.1 Jars

| Jar | Tier | Slots | Integrity | Charge | Saline | Recipe |
|---|---|---|---|---|---|---|
| Life Support Jar | T1 | 3 | 10 | 100k FE | 1000 mB | v1 recipe + 1 Lens |
| Reinforced Jar | T3 | 5 | 20 | 200k | 1500 | v1 recipe (netherite scrap) |
| Executive Jar | T4 | 7 | 30 | 400k | 2000 | Reinforced Jar, 2 shulker shells, 1 Quantum Knot, gold block |

Upgrading a jar while your head is in it happens at the Booth ("Transfer to new jar", 5,000 FE, no cut).

### 8.2 Modules

v1's ten modules stay, with numbers converted to FE. New modules are marked **new**. A module fitted in the jar keeps
working inside hosts and mecha where it makes sense (sensor and support modules do; locomotion modules don't).

| Module | Tier | Effect | Power | Works in host/mecha |
|---|---|---|---|---|
| Hop Springs (v1) | T1 | walk as hops at 60% speed | 100 FE per hop | no |
| Propeller (v1) | T2 | slow creative-style flight | 100 FE/t flying | no |
| Manipulator Arm (v1) | T1 | mine x0.5, place, use, attack 1, pick up | 200 FE per action | no |
| Telekinesis (v1) | T2 | grab and throw blocks, mobs, items, 16 blocks | 200 FE/t held, 2,000 per throw | yes (mecha uses Tractor Beam instead) |
| Drill Bit (v1) | T1 | x3 mining with an arm or a body | | no |
| Armour Plating (v1) | T1 | +6 armour, +2 toughness | | yes, counts toward the jar only |
| Heat Shield (v1) | T2 | fire and lava immunity | 100 FE/t burning | yes |
| Night Lens (v1) | T1 | night vision | | yes |
| Magnet (v1) | T1 | pulls items within 6 | | yes |
| Water Jet (v1) | T1 | swim fast | 100 FE/t | no |
| **Suction Cups** | T2 | the jar climbs walls and hangs from ceilings | 20 FE/t climbing | no |
| **Spider Legs** | T2 | the jar walks at 80% speed and climbs 1 block | 10 FE/t | no |
| **Speaker Grille** | T1 | chat messages show as a speech bubble above the jar; "Shout" key scares passive mobs | | yes |
| **Saline Recycler** | T2 | halves saline drain | 2 FE/t | yes |
| **Shock Lid** | T2 | anything that hits the jar in melee takes 3 damage and knockback | 500 FE per zap | yes |
| **Self-Righting Gyro** | T1 | no knock-over when hit; no botch roll from the booth | | yes |
| **Hub Antenna** | T2 | +50% link range while the head is the link hub (docked or placed) | 1 FE/t | n/a |
| **Cortex Coprocessor** | T3 | slows the DNA minigame by 25%; soft-lock aim in mecha | 5 FE/t | yes |
| **Lacrimal Laser** | T3 | the head weeps a short-range beam: 4 damage, 8 blocks, 1 s cooldown | 1,500 FE per shot | yes (fires from the cockpit) |
| **Hat Mount** | T1 | a helmet or Hats Renewed hat renders on the lid and gives its armour to the jar | | yes |
| **Body Recall Beacon** | T3 | your Headless Body walks toward the head while its chunk is loaded | 10 FE/t | n/a |
| **Life Sign Monitor** | T1 | HUD shows your body's health and position while you're in the jar | | yes |

---

## 9. Bodies

### 9.1 Your own body

- Created by the booth. While you're LINKED, *you are* the body. While you're in the jar, it's a v1 `HeadlessBody`
  standing where you left it, wearing your armour, with the neck stump now a Collar Cam.
- **Protected when idle:** an idle body takes no damage from players unless PvP is on, and takes no damage at all
  while its owner is offline (config `protectOfflineBodies`, default true). Mobs can still hurt an idle body while the
  owner is online; it has 20 HP and your armour.
- **Inventory rule.** Main inventory and Curios travel with the player entity (it is the same player, whichever form).
  Armour stays on the body when you leave it, because the body is wearing it. The Employee Handbook explains this as
  "Noggin & Sons pocket-dimension lid technology, policy 7-B".
- **Genes on your body** (section 12.7) only apply in BODY and LINKED forms.

### 9.2 Mob hosts

**Getting one.** Use the **Surgical Saw** (T2: Surgical Steel blade, Servo, diamond) on a mob below 25% health. The
mob stops thinking (its goals are cleared and it is flagged as a host; it keeps its entity type and data), its head is
hidden and capped with a neck seal, and it stands still. If an empty jar is in your off hand, the saw takes the
**head** instead (a jarred mob head item for turrets and arrays; section 13) and the body falls as loot. Bosses
(`#c:bosses`), tamed animals owned by someone else, named mobs and players are refused.

**Wearing one.** As JAR form, use the host. The Neural Handshake starts (9.4), then you are HOSTED. As LINKED, use the
host while holding your carried head: you are asked "Transfer to host?" and your body is left standing.

**Freshness.** A host starts at 100% freshness and loses 2% per minute (50 min to 0) unless preserved.

| Freshness | Effect |
|---|---|
| 50 to 100 | none |
| 25 to 49 | **Stiff**: -15% speed |
| 1 to 24 | **Ripe**: -30% speed, fly particles, hostile mobs notice you from 50% further |
| 0 | the host collapses: drops its normal loot plus Necromancy organs (if present) and a Blood Sample (quality 30); you pop out as JAR |

| Preservation | Effect |
|---|---|
| Embalming Fluid (bottle on the host) | freshness back to 100%, decay halved for 1 in-game day |
| Body Locker | no decay while stored (with saline); with the Cryo upgrade, no saline needed |
| **Neural Lace** graft (T2: Neural Tissue, Synapse Chip, gold wire) | permanent: no decay at all, once the host's sync is 50% or more |
| Bio-Gel bottle | heals the host 6 HP |

### 9.3 Neuro-sync

Each host has a **sync** value from 0 to 100%. Sync rises 1% per 12 s of piloting (20 min to full) and is stored on
the host entity, so a host you return to keeps its sync. Injecting the host's own species serum (a Kindred serum)
doubles the rate.

| Sync | What you get |
|---|---|
| 0 to 24 | movement and the host's base attributes; **clumsy**: 5% chance per second to stumble (lose 0.5 s of input) |
| 25 | passive traits and flags (climbing, fire immunity, water breathing...) |
| 50 | the **primary ability** (R) |
| 75 | the **secondary ability** (G) or a hidden perk |
| 100 | **Fully Synced**: +10% to the host's health, speed and damage. With Walkers installed, the host's Walkers shape unlocks for you (15.2) |

### 9.4 The Neural Handshake

The first time you mount a host, a ring closes on a target circle three times. Press the action key (controller: A)
as it lines up. Each hit is +3% starting sync; each miss is 2 s of Nausea. It takes 3 s, can be turned off in the
client config (you get +5% flat), and plays the same on keyboard and controller.

### 9.5 Host abilities by creature

Every host's numbers come from a data file (9.7). The table is the design intent; the creature ids are checked in the
data pass. Hosts move at their own speed attributes. "Blood" is the creature's blood type (12.2).

**Vanilla**

| Creature | Blood | Passive (25%) | Primary (50%) | Secondary (75%) | Drawback |
|---|---|---|---|---|---|
| Zombie | grave | other zombies are neutral | Groan: zombies within 16 target your target | break wooden doors | burns in sun without a helmet |
| Husk | grave | no sunburn | bite inflicts Hunger | | slow |
| Skeleton | marrow | bow damage +25% | arrow volley (3 arrows, 6 s) | | fragile: 15 HP |
| Creeper | ichor | cats make you flee | **Detonate**: power 3 (no block damage unless config), destroys the host; you pop out with -50% integrity unless you have a Shock Lid | | can't use weapons |
| Spider | ichor | climb walls, night vision | Web Shot: a cobweb at the target for 10 s | | |
| Enderman | ender | endermen are neutral; pick up any block (v1 telekinesis rules) | **Blink**: teleport to the block you look at, 24 blocks, 3 s cooldown | dodge 50% of projectiles by teleporting | water and rain hurt you |
| Blaze | brimstone | fire immunity, slow fall | fireball volley (3, 5 s) | hover 4 s | water and snowballs hurt you |
| Iron Golem | oil | 100 HP, knockback immune | Toss: throw the target 6 blocks up | villagers give you trade discounts (Hero of the Village I while worn) | no sprint, no jump |
| Ravager | warm | break leaves and crops by walking | Charge: 3 s run, 12 damage, knockback 2 | Roar: stun everything within 6 for 2 s | 2.2 blocks tall, 2 wide |
| Horse / Donkey / Mule | warm | speed, jump boost | **Carry**: another player can ride you | Donkey/Mule: 15-slot pack | can't use items |
| Camel | warm | 2 riders, tall eye height | Dash | | slow turning |
| Wolf | warm | speed | Pounce | tamed wolves of yours follow you | 8 HP base |
| Cat | warm | no fall damage; creepers and phantoms avoid you | | | tiny |
| Chicken | warm | slow fall | lay an egg (60 s) | | 4 HP |
| Pig | warm | | | a player with a carrot on a stick can ride and steer you (you can cancel any time) | |
| Dolphin | warm | swim very fast, Dolphin's Grace to players near you | Swim Dash | | dries out after 2 min on land (1 damage per 2 s) |
| Squid / Glow Squid | cold | breathe water; glow squid lights the area | Ink Cloud: blinds attackers | | slowly suffocates on land |
| Turtle | cold | breathe water, shell: -20% projectile damage | | | very slow on land |
| Strider | brimstone | walk on lava | | | shivers (slow) outside the Nether |
| Goat | warm | jump boost II, no fall damage | Ram: knockback 3 | | |
| Bat | warm | true flight (shape control, 9.6) | Echolocation pulse: glow entities within 16 | | 6 HP |
| Phantom | grave | flight (shape control) | Swoop | | burns in sun |
| Ghast | brimstone | flight (shape control) | fireball | | 4 x 4 hitbox, can't fit anywhere |
| Piglin | brimstone | gold armour counts double | barter: drop gold to get barter loot | | turns into a Zombified Piglin host after 15 s in the Overworld (vanilla rule, played for laughs) |
| Villager | warm | full hands: use items like a player | | iron golems protect you | raids target you |
| Witch | arcane | potion effects last 50% shorter on you | throw a harmful potion from your hotbar or a default Weakness | | |
| Breeze | ecto | high jump, no fall damage | Wind Charge | | |
| Armadillo | warm | | Roll Up: -80% damage, can't move | | |
| Sniffer | warm | | Sniff: dig up a seed (10 min) | | 1.75 wide, slow |
| Frog | cold | jump | Tongue: eat small slimes and magma cubes (froglights) | | |
| Warden (T5) | grave | sense vibrations: entities glow within 32 | Sonic Boom (10 s cooldown) | | **blind**: your screen is black except for glowing outlines. Sawing a Warden needs it under 25% of 500 HP |

**OreSpawn port** (abilities follow the Necromancy OreSpawn spec's trait choices, so a creature means the same thing in
both mods):

| Creature | Signature as a host |
|---|---|
| T-Rex | Roar (Weakness aura 8 blocks), bite that tosses; 2.5 blocks tall |
| Basilisk | gaze: Slowness to everything you look at within 12 |
| Leonopteryx | strong glide (shape control); another player can ride you |
| Cave Fisher | Line: reel a target in from 8 blocks |
| Molenoid | burrow: no suffocation, dig dirt and sand by walking |
| Hercules Beetle | Lift: throw the target straight up |
| Emperor Scorpion | pincer pin + poison sting; spawns 2 temporary scorpions on hit (60 s cooldown) |
| Kyuubi | fire immune, fox fire projectiles, claws ignite; rain hurts |
| Giant Robot | laser eye (explosion, no block damage), oil blood |
| Ender Reaper | kills bottle Necromancy souls (if Necromancy is present) |
| Triffid | rooted: can't move, takes 25% damage while still; venom snap |
| Gazelle / Ostrich | top land speed |
| Beaver | fell a tree by hitting its base |
| Whale | water mount for up to 3 friends |

**Other installed mods** (Nice; ids checked in the data pass):

| Creature | Mod | Signature |
|---|---|---|
| **Headless Mule** (Mula-sem-cabeça) | Brazil Legends | **no saw needed**: it is already headless. Mount it as a jar and you sit in its neck fire: you need a Heat Shield. Fire trail, fire immunity, kicks that ignite. The flagship pt_br joke |
| Saci | Brazil Legends | one-legged hop, whirlwind teleport, steals an item from a mob's hand |
| Mapinguari | Brazil Legends | heavy, strong, smells awful (mobs flee) |
| Minotaur | Twilight Forest | charge |
| Moa | Aether | double jump and glide; a friend can ride you |
| Phyg | Aether | flying pig (shape control) |
| Foliaath | Mowzie's | rooted snap; looks like a plant to mobs |
| Grottol | Mowzie's | ore sense; mines faster |
| Elephant, Rhino, Giraffe | Naturalist | elephant: trunk spray puts out fire and carries 2; rhino: charge; giraffe: +3 reach and a 3-block-tall eye height (see over walls) |
| Komodo Dragon | Naturalist | venom bite |
| Copper Golem | Friends & Foes | presses copper buttons on its own; oil blood |
| Beehemoth | Bumblezone | flight, a friend can ride |
| Peashooter, Chomper, Gargantuar | Plants & Zombies | peashooter: you are a turret (can't walk), pea volleys; chomper: devour a mob; gargantuar: huge smash and throw an imp |
| Mutant Zombie | Mutant Monsters | T4 heavy host: ground slam |

Excluded as hosts: bosses (`#c:bosses`, Cataclysm and Twilight bosses, OreSpawn bosses per the Necromancy spec),
pets owned by another player (DoggyTalentsNext dogs, tamed animals, Inventory Pets), Custom NPCs, and anything in the
tag `headinajar:not_a_host`.

### 9.6 Control styles

| Style | How | Used for |
|---|---|---|
| `vehicle` (default) | the jar player rides the host; the host moves from the rider's input like a horse | ground and water creatures, Necromancy Minions, mecha |
| `shape` (fallback) | the host entity is stored on the player (v1 SHELL plan); the player takes its hitbox and attributes and a client-side copy is drawn | fliers and creatures whose movement code ignores a rider (bat, phantom, ghast, Phyg, Leonopteryx) |

### 9.7 Data format

`data/<ns>/headinajar/host/<entity>.json`, loaded by a reload listener. Trait entries use **the same type names and
parameters as Necromancy's 32 trait types** (`docs/ORESPAWN_REVIEW.md` section 3.3), plus a `trigger` that says what
fires them when a player is in control (12.9).

```json
{
  "entity": "minecraft:enderman",
  "control": "vehicle",
  "neck": { "offset": [0.0, 2.55, 0.0], "hide": ["head", "hat"] },
  "blood": "ender",
  "genes": ["headinajar:ender_blink", "headinajar:ender_hands"],
  "decay_per_minute": 2.0,
  "sync": {
    "0":  { "attributes": { "minecraft:generic.step_height": 0.4 } },
    "25": { "traits": [
              { "type": "flag", "flags": ["hurt_by_water"] },
              { "type": "neutral_to", "entities": "#headinajar:endermen" } ] },
    "50": { "primary": { "type": "teleport", "mode": "look", "range": 24, "cooldown": 60 } },
    "75": { "traits": [ { "type": "teleport", "mode": "away_when_hurt", "chance": 0.5, "cooldown": 100 } ] },
    "100": { "attributes": { "minecraft:generic.max_health": 4.0 } }
  }
}
```

A creature with no file still works as a host with its own attributes and no traits.

### 9.8 Necromancy Minions as custom hosts (Core when Necromancy is present)

Necromancy lets you sew a body from parts. Head in a Jar adds one more head part:

- **Neural Socket Head** (Necromancy head part, registered only when Necromancy is loaded): stats
  `(0, 0, 0, 0, 0)`, weight 1, trait flag `jar_socket`. Sewn from a Synapse Chip, a Lens and Necromancy skin.
- Raise a Minion with it at the Summoning Altar as usual. The Minion stands idle until you mount your jar on it.
- **Every part trait keeps running** because the Minion is still a `MinionEntity` driven as a vehicle. A golem-torso,
  enderman-legs, Cave-Fisher-armed Frankenbody plays exactly as Necromancy built it, with your jar on top.
- Pilot mapping: Necromancy's "combat gate" (has a target, not sitting, can see it) becomes "the pilot attacked or
  pressed an ability key at a looked-at target". Ranged and leap traits fire on the primary key, `aura`, `regen` and
  passive traits run as normal.
- Hook needed on the Necromancy side (both ports are ours): a small `MinionPilot` interface on `MinionEntity`
  (`isPiloted()`, `pilotTarget()`, `pilotTrigger(key)`) that `DataTraits` consults. About a day's work.
- **Sync** works on Minions too, starting at 50% (a body sewn for you already fits).
- Minion veterancy (Necromancy #12) counts your kills while you pilot.
- Necromancy's soul potency still caps the body's weight, so heavy Frankenbodies still need a strong soul.

Note: Necromancy is not in the CrazyCraft 5.0 mods folder today (it is a private build). Everything in this section
is a soft dependency and disappears cleanly without it.

### 9.9 Utility Shells (v1 robot bodies)

The three v1 robots become cheap, fixed **Utility Shells**: no modules, no tinkering, the stepping stone to mecha.
They run on the jar's charge.

| Shell | Tier | Size | Health | Notes |
|---|---|---|---|---|
| **Lunk** (v1 Golem Chassis) | T1 | 1.4 x 2.7 | 60 | 9 attack damage, knockback resistance, +1.5 reach, slow |
| **Rover** (v1 Rover Chassis, the "car body") | T1 | 1.2 x 0.9 | 30 | fast, step 1.5, 9 cargo slots, can't mine |
| **Spider Walker** | T3 | 1.4 x 0.9 | 30 | climbs walls, no fall damage |

---

## 10. Mecha: frames and balance

### 10.1 What Modular Powersuits got right, and how it maps

| MPS idea | Why it worked | Head Office version |
|---|---|---|
| A tinker table where you install modules on a rendered suit | building is visible and tactile | the **Assembly Gantry**: the mecha stands on the pad, you click its parts |
| Per-module tinker sliders (e.g. drill speed vs energy) | the same module fits many playstyles | every module has 1 to 3 sliders that trade effect for power, heat or weight |
| One power pool, many drains | every choice has a cost you can read | one FE battery per mecha, every module lists its drain |
| Weight slowing you down | stops "install everything" | load rating per frame; overload costs speed, jump and power |
| Heat (Numina and later versions) | rewards pacing instead of holding a button | per-frame heat capacity, module heat, dissipation, vents |
| Mode-change radial | many tools without a full hotbar | the **Module Wheel** (hold R / LB), 8 segments |
| Cosmetic tinkering | players love painting their suit | per-part colour, glow on/off, hide parts |

What MPS got wrong, and we avoid: modules that were strictly better at max slider (every slider here has a real
downside at both ends), and power numbers so large they stopped mattering (frame batteries are small enough that
a long fight needs a plan).

### 10.2 Frames

A mecha is one entity built from a **frame** (the chassis) plus modules. Frames are crafted at the Gantry from Mecha
Plating, Servos and a frame-specific core part.

| Frame | Tier | Size (w x h) | HP | Armour | Load (kg) | Dry mass (kg) | Base speed | Battery | Heat cap | Dissipation (HU/t) | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **Hopper** (light biped) | T2 | 1.2 x 2.5 | 60 | 6 | 350 | 150 | 0.13 | 400k | 400 | 3 | first mecha; the only frame light enough for an early Rocket Pack |
| **Wheelhouse** (car) | T2 | 2.2 x 1.5 | 90 | 10 | 1000 | 300 | 0.24 on flat, 0.08 off-road | 1.2M | 600 | 4 | treads built in; no jump; step 1.0; 2 passenger pods built in |
| **Bulwark** (medium golem) | T3 | 1.6 x 3.1 | 140 | 12 | 800 | 320 | 0.11 | 1.2M | 800 | 5 | the all-rounder, golem-shaped |
| **Skitter** (quad spider) | T3 | 2.0 x 1.6 | 100 | 10 | 600 | 260 | 0.12 | 800k | 600 | 4 | climbs walls natively; one mandible arm |
| **Chimera** (bio-mecha) | T3 | 1.6 x 2.8 | 120 | 8 | 700 | 220 | 0.12 | 800k | 700 | 6 (sweats) | grown from Bio-Gel; **3 Gene Ports**; regenerates 1 HP/5 s with Bio-Gel in its veins |
| **Colossus** (heavy biped) | T4 | 2.4 x 4.4 | 300 | 18 | 1800 | 700 | 0.09 | 4M | 1600 | 8 | 2 extra shoulder hardpoints; too big for a vanilla nether portal |
| **Kite** (ornithopter) | T4 | 1.8 x 2.0 | 70 | 6 | 280 | 120 | 0.12 walk, 0.6 flight | 1M | 500 | 6 in flight | flight built in (flapping wings in the Back slot) |
| **Executive** (Colossus Mk II) | T5 | 2.4 x 4.4 | 360 | 20 | 2000 | 750 | 0.10 | 8M | 2000 | 10 | 4 Gene Ports, 2 Co-pilot sockets |

Vanilla scale check: Iron Golem 100 HP, Ravager 100 HP, Warden 500 HP. A Colossus is a walking golem-and-a-half that
costs a lot to run; it is never invulnerable.

### 10.3 Slot layout

| Slot | Hopper | Wheelhouse | Bulwark | Skitter | Chimera | Colossus | Kite | Executive |
|---|---|---|---|---|---|---|---|---|
| Cockpit (the jar) | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 |
| Core (power) | 1 | 1 | 1 | 1 | 1 | 2 | 1 | 2 |
| Torso | 2 | 2 | 3 | 2 | 3 | 5 | 1 | 5 |
| Arms (left, right) | 2 | 1 (roof turret) | 2 | 1 (mandible) | 2 | 2 + 2 shoulders | 2 | 2 + 2 |
| Legs | 1 | fixed treads | 1 | fixed spider legs | 1 | 1 | 1 | 1 |
| Back | 1 | 2 | 2 | 1 | 1 | 2 | fixed wings | 2 |
| Sensor | 1 | 1 | 2 | 2 | 1 | 2 | 1 | 2 |
| Gene Port | 0 | 0 | 0 | 0 | 3 | 0 | 0 | 4 |

Any mechanical frame can take a **Bio-Integration Port** (a Torso module) for one Gene Port, at -20% battery.

### 10.4 Power, heat and weight

**Power.** One FE battery per mecha. Idle drain: 2 FE/t (light) to 10 FE/t (heavy). Walking adds the Legs module's
drain. A Hopper walking with nothing else running (6 FE/t) lasts about 55 minutes on a full battery. Cores refill it (11.1).
At 0 FE: every module goes offline, speed drops to 30% ("limp mode"), the cockpit's life support switches to the jar's
own charge.

**Heat.** Each module adds HU per tick of use (or per action). The frame dissipates HU every tick.

```
heat(t+1) = heat(t) + sum(module heat) - dissipation x env
env: water x3 (submerged), rain x1.5, snow biome x1.25, Nether x0.5, standing in lava: +10 HU/t, on fire: +3 HU/t
```

| Heat (% of capacity) | Effect |
|---|---|
| 0 to 59 | none |
| 60 to 84 | yellow bar, warning chirp |
| 85 to 99 | **Throttle**: all module effects -30%, steam particles |
| 100 | **Emergency vent**: all modules off for 5 s, a scalding steam cloud (3 radius, 2 damage/s to non-allies, blinds mobs), heat drops to 50%. The pilot takes 2 damage unless a Heat Shield is fitted in the jar |

**Weight.** Total mass = dry mass + modules + cargo (1 kg per full stack in Cargo Holds).

| Load | Speed | Jump | Power drain |
|---|---|---|---|
| up to 60% of rating | 100% | 100% | 100% |
| 60 to 100% | linear down to 60% | linear down to 50% | 100% |
| 100 to 125% (**Overloaded**) | 30% | none | 150% |
| over 125% | servos lock: the mecha cannot move until you unload | | |

**Flight rule.** A Rocket Pack's thrust (kg it can lift, set by its slider) must exceed the total mass, or it only
slows falls. In practice only a Hopper or a Kite flies until T4 thrusters. This makes "build light to fly" a design
choice instead of a blanket ban.

---

## 11. Mecha: modules, building and piloting

### 11.1 Module list

Sliders are 0 to 100%. "Power" and "Heat" are at the slider defaults (50%) unless a range is given.

**Legs (locomotion)**

| Module | Tier | Effect | Power | Heat | Mass | Sliders |
|---|---|---|---|---|---|---|
| Hydraulic Legs | T2 | walking at the frame's base speed | 4 FE/t walking | 0.2 HU/t | 60 | Stride: speed 60 to 140%, power x0.6 to x2 |
| Sprint Servos | T2 | sprint +40% | 15 FE/t sprinting | 1 | 25 | Boost 0 to 80% |
| Spring Heels | T2 | jump 1.5 to 4 blocks | 100 to 400 FE per jump | 2 to 6 per jump | 20 | Tension |
| Stilts | T2 | +1.5 height, step 2, reach +1 | | | 35 | none; no sprint |
| Anchor Clamps | T2 | Brace key: knockback immune and no fall damage under 10 blocks while braced; can't move while braced | | | 30 | |
| Arachnid Legs | T3 | climb walls | 12 FE/t climbing | 0.5 | 90 | Grip: climb speed |
| Aqua Fins | T2 | swim x2, controlled diving | 6 FE/t swimming | | 15 | |
| Hover Skirt | T3 | float 0.5 to 2 blocks above ground, water and lava; no fall damage | 30 FE/t | 2 | 50 | Altitude |
| Lava Treads | T3 | walk on lava | 20 FE/t on lava | +3 on lava | 40 | |

**Back**

| Module | Tier | Effect | Power | Heat | Mass | Sliders |
|---|---|---|---|---|---|---|
| Jump Jets | T2 | 3 mid-air bursts, recharge 1 per 4 s | 600 FE per burst | 15 per burst | 45 | Thrust |
| Rocket Pack | T3 | flight if thrust > mass (10.4) | 120 to 300 FE/t | 6 to 12 | 110 | Thrust (lift 200 to 800 kg), Hover Assist |
| Glider Wings | T2 | glide 1:6 | | | 40 | Camber: glide ratio vs speed |
| Cargo Hold | T2 | 27 slots | | | 80 | |
| Passenger Pod | T2 | 1 seat for any player in any form | | | 120 | |
| Radiator Fins | T2 | +4 HU/t dissipation | | | 30 | Deploy: +4 more but -20% speed |
| Battery Rack | T2 | +500k FE | | | 70 | |
| Ender Satchel | T3 | open your ender chest | 2,000 FE per open | | 15 | |
| Smoke Mortar | T3 | smoke cloud: mobs inside lose their target | 2,000 per shot | 10 | 35 | |
| Missile Pod | T4 | 4 homing micro-missiles, 8 damage each, no block damage by default | 4,000 per missile | 20 | 90 | Warhead: damage vs heat |

**Core (power)**

| Module | Tier | Effect | Fuel | Heat | Mass |
|---|---|---|---|---|---|
| Galvanic Stack | T2 | 10 FE/t, always | none | 0 | 25 |
| Combustion Kernel | T2 | 80 FE/t while burning | furnace fuels: coal = 16,000 FE | 4 HU/t burning | 60 |
| Bio-Reactor Core | T2 | 60 FE/t | food 2,000 FE per hunger point; Bio-Gel, Plasma | 2 | 50 |
| Solar Skin | T2 | 15 FE/t in daylight (takes a Torso slot, not the Core) | sun | 0 | 20 |
| Capacitor Bank | T3 | +2M FE, burst output for weapons | | | 50 |
| Necrotic Furnace | T3 | 150 FE/t | Necromancy souls: 100k (Faint) to 2M (Legendary); organs 20k | 6 | 80 |
| Fusion Bottle | T4 | 600 FE/t | Plasma Pellet (blaze rod + ender pearl + Bio-Gel): 1M FE | 15 | 140; slider Output 25 to 100% |
| RTG Pile | T4 | 200 FE/t forever while loaded | HBM Pu-238 pellets (Stretch, only with HBM) | 1 | 100 |

**Torso (defence and utility)**

| Module | Tier | Effect | Power | Heat | Mass | Sliders |
|---|---|---|---|---|---|---|
| Armour Plating | T2 | +2 to +6 armour, +1 toughness | | | 30 to 90 | Thickness |
| Reactive Armour | T3 | -40% explosion and projectile damage, 6 charges, 1 charge per 30 s | 5,000 per charge | | 70 | |
| Energy Shield | T3 | absorbs 25 to 100% of damage at 1,000 FE per HP | 40 FE/t upkeep | 4/t + 1 per HP | 40 | Strength |
| Thorn Plating | T2 | reflects 20 to 50% melee damage | | | 50 | Spikes: reflect vs mass |
| Fire Suppression | T2 | fire immunity for frame and pilot; puts out fire nearby | 5 FE/t burning | | 25 | |
| Coolant Loop | T2 | +6 HU/t dissipation; holds 4,000 mB water or Cryo Coolant (x3) | uses 1 mB per HU above 50% heat | | 45 | |
| Life Support Recycler | T2 | keeps the pilot's saline and charge full; +1 Jar Integrity per 30 s | 10 FE/t | | 20 | |
| Medical Injector | T3 | heals the pilot and passengers 4 HP when hurt below the threshold; repairs a Chimera 1 HP/s | 250 mB Bio-Gel per injection | | 30 | Threshold |
| Stealth Field | T4 | mobs beyond 4 blocks can't see you; shimmer effect; attacking drops it for 3 s | 200 FE/t | 8 | 30 | |
| Tesla Coil | T3 | arcs hit hostiles within 3 to 6 blocks for 2 to 6 damage/s, chaining | 100 to 400 FE/t | 10 | 60 | Radius, Voltage |
| Signal Booster | T2 | doubles the pilot's link range and acts as a moving Relay Mast for allies | 20 FE/t | | 15 | |
| Workbench | T2 | crafting grid on the go | | | 10 | |
| Bio-Integration Port | T3 | +1 Gene Port on a mechanical frame, battery -20% | | | 40 | |
| Gunner Cupola | T3 | a second seat that controls one Arm or shoulder weapon | | | 100 | |
| Co-pilot Socket | T3 | a second jar seat: controls Shield, Sensors and Back weapons | | | 40 | |
| Ghost Core | T3 | the mecha follows, guards or stays without a pilot (Necromancy soul of Common potency or better; without Necromancy: a jarred Witch or Evoker head) | 20 FE/t | | 10 | Aggression |

**Arms (left mouse / left trigger, right mouse / right trigger)**

| Module | Tier | Effect | Power | Heat | Mass | Sliders |
|---|---|---|---|---|---|---|
| Manipulator Hand | T2 | uses your selected hotbar item like a player's hand (place, use, attack with the item) | 50 FE per action | | 30 | |
| Drill | T2 | mining x4 to x16; area 1x1, 3x3 or 5x5; harvest level iron (diamond and netherite bits are upgrades) | 50 FE per block x area | 1 per block | 70 | Torque, Area |
| Chainsaw | T2 | fells a whole tree (up to 128 logs); 8 melee damage | 40 FE per log | 0.5 per log | 60 | |
| Excavator Scoop | T2 | dirt, sand, gravel 3x3 at x10; drops into Cargo | 20 FE per block | | 80 | |
| Hydraulic Fist | T2 | 10 to 22 melee damage, knockback | up to 1,500 per punch | up to 4 per punch | 90 | Force |
| Grapple Hook | T2 | 32-block hook: pulls you to a block or a mob to you | 800 per shot | | 30 | |
| Tractor Beam | T3 | v1 Telekinesis at mecha scale: 24 blocks, any block or mob under 2,000 kg | 80 FE/t held | 3/t | 50 | Grip |
| Rivet Gun | T2 | 5 shots/s, 2 damage, uses iron nuggets | 30 per shot | 0.5 | 45 | |
| Flamethrower | T3 | 6-block cone, ignites, 4 damage/s; uses Bio-Sludge | 60 FE/t | 8/t | 50 | Spread |
| Arc Cannon | T3 | hitscan bolt 16 blocks, 12 damage, chains to 3 | 6,000 per shot | 25 | 85 | Charge time |
| Riot Shield | T2 | -80% frontal damage while held | | | 80 | |
| Hemo-Syringe | T2 | melee hits draw a Blood Sample (quality 50 to 80) and heal the frame 10% of damage | 20 per hit | | 25 | |
| Harvester | T2 | harvest and replant crops 5x5 | 10 per block | | 10 | |
| Welding Torch | T3 | repairs another mecha or your own frame 2 HP/s from Mecha Plating | 80 FE/t | 6/t | 30 | |
| Plasma Lance | T4 | 32-block beam, 20 damage/s, sets fire | 600 FE/t | 30/t | 120 | |

**Sensors**

| Module | Tier | Effect | Power | Heat | Mass |
|---|---|---|---|---|---|
| Night Optics | T2 | night vision | 2 FE/t | | 5 |
| Thermal Imaging | T3 | living things glow within 32, coloured by blood type | 10 FE/t | 1 | 8 |
| Ore Scanner | T3 | outlines ores within 8 to 24 blocks for 5 s | 4,000 per ping (x radius) | 6 | 12 |
| Bio-Scanner | T2 | shows a target's health, blood type, your sequencing progress for it, and host suitability | 1 FE/t | | 5 |
| Target Lock | T2 | soft lock-on (11.7) | 2 FE/t | | 5 |
| Periscope Mast | T2 | camera 4 blocks above the cockpit | | | 5 |

That is 62 modules across 6 slot types.

### 11.2 Gene Ports

A Gene Port takes one **Serum Cassette** (section 12). Mecha ignore blood type (Bio-Gel is synthetic), so mecha are the
universal platform for genes: anything you can sequence, a Chimera can carry. This is the "inject the DNA into the
mecha" fantasy from the brief, made the centre of the endgame.

### 11.3 Mecha life cycle

- **Build:** place a frame item on the Gantry pad, fit modules in the GUI, press **Assemble** (10,000 FE). The mecha
  entity stands on the pad.
- **Board:** as JAR form, use the mecha's cockpit. As LINKED, hold your carried head and use the cockpit: your body is
  left standing (or climbs into a Passenger Pod if one is free, so it comes with you).
- **Eject:** hold Sneak + J for 1 s (controller: hold D-pad down 1 s). You drop out as JAR beside the mecha.
- **Golden Parachute:** below 10% HP the cockpit offers auto-eject (config default on): the jar is thrown 6 blocks
  up with a tiny parachute and slow fall.
- **Destroyed mecha:** a small explosion with no block damage (config), 50% of modules drop, the rest become Scrap
  (recyclable at the Gantry), the pilot is ejected; any damage past 0 HP goes to Jar Integrity.
- **Repair:** at the Gantry (1 Mecha Plating per 20 HP), with a Welding Torch arm on another mecha, or Bio-Gel for a
  Chimera.
- **Ownership:** a mecha belongs to whoever assembled it. Only the owner and trusted players can board, edit or
  remove modules. Unpiloted mecha are immune to Carry On, Telekinesis, Gravity Gun pick-up and pushing.

### 11.4 Rendering

- Frames are GeckoLib models (GeckoLib 4.9 is in the pack) with named attachment bones: `cockpit`, `core`,
  `torso_front`, `torso_back`, `torso_l`, `torso_r`, `shoulder_l`, `shoulder_r`, `arm_l`, `arm_r`, `hand_l`,
  `hand_r`, `legs`, `back`, `sensor`.
- Each module is a small GeckoLib model of its own, drawn at its bone by a render layer. One model per module per
  **frame size class** (light, medium, heavy), never scaled: all parts use the vanilla texel density
  (1 texel = 1/16 block), so a heavy drill is a bigger model, not a stretched light one.
- The pilot's jar renders in the cockpit with the v1 `JarRenderer` (skin head, goggles, fluid).
- Animations (Blockbench): idle, walk, run, jump, land, brace, arm swing L/R, drill spin, jet fire, vent, eject.
  Controllers read synced state (speed, heat, firing flags).
- **Paint Bay** (Gantry upgrade): each part has a 3-channel mask (primary, secondary, trim) tinted with any RGB colour,
  and an emissive layer for lights and screens that can be switched off. Parts can be hidden (cosmetic only).
- Damage states: sparks below 50% HP, black smoke below 25%. Heat glow: vents redden above 60%.

### 11.5 The Assembly Gantry GUI

```
+--------------------------------------------------------------------------+
| [FE]   +-----------------------+   MODULES (filter: All v)   [search]    |
| [##]   |                       |   > Drill            T2  70 kg          |
| [##]   |   live 3D model of    |   > Chainsaw         T2  60 kg          |
| [##]   |   the mecha on the    |   > Arc Cannon       T3  85 kg          |
|        |   pad; click a part   |   ...                                   |
|        |   to select its slot  |  -------------------------------------  |
|        |   (drag to rotate)    |   SELECTED: Right Arm - Drill           |
|        +-----------------------+   Torque  [=====|-----] 50%  x10        |
|  MASS  [##########----] 290/350 kg Area  [1x1 | 3x3 | 5x5]               |
|  POWER   idle 2  walk 6  peak 410 FE/t   -> 450 FE per block, 9 HU      |
|  HEAT    +9 /block  dissipation 3/t      [Install] [Remove] [Paint]     |
|  [Assemble 10k FE]  [Repair]  [Scrap]    inventory below                |
+--------------------------------------------------------------------------+
```

- The left panel is a live render of the mecha entity on the pad (or a preview model before assembly). Hovering a
  part highlights it; clicking selects its slot.
- The stat block shows totals as you change things, with red where a limit is passed (overweight, heat positive at
  rest, peak power above what the cores and battery can deliver).
- **Test Mode** button: board the mecha on the pad with infinite power and no damage for 30 s, so you can feel a
  slider change before you commit.
- Controller: the GUI is fully navigable with the D-pad (slot list, then slider row); triggers move sliders by 5%.

### 11.6 Piloting controls

All keys are `KeyMapping`s **read by polling in the client tick**, never only from key events. This is what lets
Controlify (on the Legion) drive them; the pack's controller notes show Controlify drives modded keys only by polling.

| Action | Keyboard and mouse (default) | Controller (Controlify) |
|---|---|---|
| Move, look | WASD, mouse | left stick, right stick |
| Jump / Jump Jets / thrust | Space (hold for jets) | A (hold for jets) |
| Sprint | Ctrl | left stick click |
| Left arm | left mouse | left trigger |
| Right arm | right mouse | right trigger |
| Primary ability (selected module or gene) | R tap | right bumper |
| Module Wheel (8 segments: pick active modules and modes) | R hold | left bumper hold |
| Secondary ability | G | Y |
| Brace (Anchor Clamps, heavy weapons) | B | B hold |
| Vision mode (normal, night, thermal, scanner) | V | D-pad up |
| Cycle shoulder weapon | Z / X | D-pad left / right |
| Eject (hold 1 s) | Sneak + J | D-pad down hold |
| Jar menu (v1) | J | in the CrazyCraft Controls context wheel |
| Camera (cockpit / chase) | F5 or F7 | right stick click |
| Swap body/head (LINKED) | K | D-pad down hold (outside mecha) |

- **Controller integration.** Register the bindings with Controlify's API when it is present (soft dependency) and add
  Head Office entries to the CrazyCraft Controls context wheel (the pack's own controller layer). Never use the
  View + Menu chord (reserved by Prism Console).
- **Handheld options:** HUD scale +25%, larger Module Wheel text, Target Lock on by default.

### 11.7 Target Lock (handheld friendly)

Hold the left trigger (or left mouse) on a target for 0.5 s: if a hostile is within a 10 degree cone and 32 blocks,
the aim softly tracks it while the trigger is held (the camera eases 30% of the way per tick). Breaks on line of sight
loss. Off by default on keyboard, on by default when Controlify reports a controller.

### 11.8 Camera

- **Cockpit view:** the jar's eye inside the cockpit, with a cockpit frame overlay (glass edges, gauges).
- **Chase view:** 4 blocks back and 1.5 up for light frames, 7 back for heavy. Uses Shoulder Surfing's plugin API
  when present (it is installed) so the over-shoulder offset and crosshair work; otherwise our own camera offset in
  `ViewportEvent.ComputeCameraAngles` plus a third-person distance override.

### 11.9 Multiplayer

- **Passenger Pod:** any player in any form. A LINKED friend rides along; a JAR friend sits in a glass bubble.
- **Gunner Cupola:** the gunner aims with their own look and fires one Arm or shoulder weapon on their trigger. The
  weapon's power and heat come from the mecha.
- **Co-pilot Socket:** a second jar controls the Shield slider, Sensors and Back weapons, and sees the pilot's HUD.
- **Executive:** two Co-pilot sockets. "Two heads are better than one."

---

## 12. Genetics

### 12.1 Pipeline

```
living mob --(Syringe, Leech Jar, Exsanguinator, Hemo-Syringe arm)--> Blood Sample
Blood Sample --(Centrifuge)--> DNA Fraction + Plasma
DNA Fraction --(DNA Sequencer, minigame or auto)--> Readout % for that creature's genes
Gene Archive: the library of genes and their completeness
Gene Splicer: genes + Bio-Gel + catalysts --> Serum Cassette
Serum --> Chimera / any mecha with a Gene Port, mob hosts, Necromancy Minions, your own body
```

### 12.2 Blood

**Collection tools**

| Tool | Tier | How | Quality |
|---|---|---|---|
| Syringe (16 uses) | T0 | use on a living mob: 1 damage, 1 Blood Sample | 40 to 60 |
| Gold Syringe | T2 | as above | 60 to 80 |
| Surgical Syringe | T3 | as above, no damage | 70 to 90 |
| Diamond-Tipped Syringe | T4 | as above; works on armoured mobs and bosses | 85 to 100 |
| **Leech Jar** | T1 | a block; leeches attach to mobs within 4 blocks and fill one sample every 2 min (max 4 stored) | 30 to 50 |
| **Exsanguinator** | T2 | a mob held in a Restraint Frame (a lead-and-iron cage block) gives 250 mB blood every 30 s at 1 damage | 50 to 70 |
| **Hemo-Syringe** arm | T2 | in combat | 50 to 80 |

**Quality modifiers:** the mob's health fraction (a healthy mob gives better blood: x0.5 at 10% health, x1 at full),
-20 if it is poisoned, burning or withered, +10 for ominous or raid-captain mobs. So blood wants healthy, calm mobs,
the opposite of the saw that wants them nearly dead.

**Freshness:** a sample spoils after 1 in-game day (quality halves) unless kept in a **Sample Fridge** (a small cold
storage block, 27 slots, 2 FE/t) or centrifuged.

**Blood types** (lore and rules)

| Type | Creatures | Rules |
|---|---|---|
| Warm | mammals, birds, villagers, illagers | |
| Cold | fish, reptiles, amphibians, squid | |
| Ichor | arthropods, creepers | |
| Ender | endermen, endermites, shulkers, End mobs | |
| Brimstone | Nether creatures | |
| Grave | zombies, phantoms, Warden | |
| Arcane | witches, evokers, magic creatures (Iron's Spells, Ars Nouveau) | |
| **Marrow** | skeletons, bogged, wither skeletons | bloodless: collect with a **Marrow Drill** (Syringe + flint) |
| **Oil** | iron and snow golems, OreSpawn robots, Copper Golem | bloodless: collect with an **Oil Tap** (Syringe + bucket) |
| **Ecto** | vex, allay, breeze, ghosts | bloodless: collect with an **Ecto Trap** (bottle + soul sand) |

Entity tag `headinajar:blood/<type>` assigns types; mobs in `necromancy:bloodless` default to marrow, oil or ecto by
a second tag. Untagged mobs default to Warm.

**Compatibility.** Serums injected into a *mob host* or *your own body* have a +30% rejection chance if any gene comes
from an incompatible type (warm-cold-ichor are mutually compatible "flesh"; the rest only match themselves). A
**Universal Donor** catalyst (villager blood plasma; "it explains a lot about villagers") removes the penalty. Mecha
never reject.

### 12.3 Centrifuge

Blood Sample (or 250 mB blood) -> DNA Fraction (keeps species and quality) + 50 mB Plasma. 3,000 FE, 8 s.
Necromancy's Jar of Blood has no species: it centrifuges to Plasma only.

### 12.4 Sequencing

Each creature has 1 to 3 genes. Each gene has a **Readout** (0 to 100%) in your team's Gene Archive. A read adds to all
of that creature's genes:

```
gain per gene = sample quality x accuracy x 0.25 / number of the creature's genes
(a 70-quality sample read at 90% accuracy gives a one-gene creature +15.75%)
```

**The minigame: Base Calling.** Four lanes (A, T, C, G), colour-coded. Template bases scroll down; press the
*complementary* base (A for T, T for A, C for G, G for C) as each one crosses the read head. 24 bases per read, about
10 s. Accuracy is the share hit within the timing window; a perfect streak of 8 doubles the next base's value.

| Input | Lanes |
|---|---|
| Keyboard | 1 2 3 4, or arrow keys |
| Controller | A, B, X, Y (lane colours match Controlify's button colours) |
| Mouse | click the lane |

Difficulty scales with the creature's tier (scroll speed 1x for vanilla passive mobs, up to 2x for Legendary
creatures). The Cortex Coprocessor jar module slows it 25%. **Auto-read** skips the minigame: 4x the time and a fixed
60% accuracy (80% with Read Head Mk II, 90% with a Librarian on staff). Nobody is forced to play it.

**Completeness bands**

| Readout | Band | Gene potency | Stability cost |
|---|---|---|---|
| 30 to 59% | Draft | 50% | x1.5 |
| 60 to 99% | Working | 75% | x1.2 |
| 100% | Complete | 100% | x1 |

### 12.5 The Gene Splicer

```
+----------------------------------------------------------------------+
| GENE ARCHIVE [search]     |  SERUM STRAND (helix)      | STABILITY    |
| Ender Blink   100% ****   |   (1) Ender Blink      L4  |  [#######--] |
| Spider Climb   64% **.    |   (2) Spider Climb     L2  |     72       |
| Blaze Core    100% ****   |   (3) [ empty ]            |  mutation    |
| Gills          31% *..    |   (4) [ locked: T4 ]       |  risk 0%     |
| ...                       |   ~~ synergy: Blink+Climb  |  LOAD 6 / 8  |
|                           |   xx conflict lines in red |  REJECT +0%  |
| catalyst [ ]  base vial [ ]  Bio-Gel [####]  [SPLICE 20k FE]  out [ ] |
+----------------------------------------------------------------------+
```

- **Strand slots:** 3 (T3 Splicer), 5 (with the Precision upgrade at T4), 7 (T5).
- **Load:** each gene has a load of 1 to 6. A Serum's load must fit the Gene Port it will go into (port capacity 8 on
  a Chimera, 10 on an Executive, 6 on a Bio-Integration Port; mob hosts 6; your body 3).
- **Stability** starts at 100. Each gene subtracts its stability cost; conflicts subtract 15 per pair; synergies add
  10 per pair. Catalysts add: slime ball +5, amethyst shard +10, echo shard +20, nether star +40 (cap 100). A Cleric on
  staff +10.
- Cost per splice: 20,000 FE, 500 mB Bio-Gel, a base vial (glass bottle), 60 s.

### 12.6 Stability, mutation and rejection

```
mutation chance on injection = max(0, 60 - stability) x 1.5%     (stability 60+ is safe; 20 gives 60%)
```

A mutation rolls once on the Mutation Table. Most mutations are funny, some hurt, a few are lucky. Every mutation is a
gene: once it has happened you can sequence it from the mutated body's blood and use it on purpose later.

| Roll | Mutation | Effect | Kind |
|---|---|---|---|
| 1 | Hiccups | a small involuntary hop every 20 to 40 s | silly |
| 2 | Flatulence | every few minutes a Nausea cloud (2 radius) with OreSpawn's fart sound if present | silly |
| 3 | Clucking | your ambient sound is a chicken | silly |
| 4 | Bioluminescence | you emit light 8 | handy |
| 5 | Brittle Bones | +50% fall damage | bad |
| 6 | Photophobia | -15% speed in daylight | bad |
| 7 | Glass Jaw | +10% damage taken | bad |
| 8 | Cravings | the Bio-Reactor core eats food from your inventory first | bad |
| 9 | Gigantism | scale +10% | mixed |
| 10 | Shrinkage | scale -10% | mixed |
| 11 | Extra Eye | a fisheye vignette and +5 FOV | silly |
| 12 | Magnetic Personality | iron items within 4 blocks drift toward you | handy |
| 13 | Rejection | the serum is inert and wasted | bad |
| 14 | Hybrid Vigour | +10% max health | good (rare) |
| 15 | Lucky Sequence | one gene in the serum works at +25% potency | good (rare) |
| 16 | Chimeric Harmony | unlocks a gene that gives +10 stability to any serum it is in | good (rare) |

Weights: 1 to 12 common, 13 uncommon, 14 to 16 rare. Mecha can mutate too: a mutated Chimera hiccups.

### 12.7 Injection targets

| Target | Where | Limit | Permanence |
|---|---|---|---|
| **Chimera / Gene Ports** | Assembly Gantry (Gene Port slot) | one serum per port, port load cap | until removed (removal destroys the serum) |
| **Mob host** | Injection Rig, or a Syringe of serum on the host | one serum, load 6, blood-type rejection applies | stays with the host |
| **Necromancy Minion** | Syringe of serum on your own Minion | one serum, load 4 | until the Minion dies (Remains keep it) |
| **Your own body** | Injection Rig with you standing on it in BODY or LINKED form | 2 serums, load 3 each, rejection applies | until washed out with **Gene Wash** (milk + saline + charcoal); config `allowSelfEditing` (default true) |

Genes live in **bodies**, never in the head. "Your head is you; your body is equipment." Swapping bodies swaps your
genes, which makes a collection of prepared hosts and mecha the real endgame wardrobe.

### 12.8 Gene list

Load (L) and stability cost (S) are at Complete. Effects are given for a mecha; hosts and bodies get the same effect at
70% strength unless noted. "Trait" is the shared trait type that implements it (12.9).

| Gene | Sources | Category | Effect | L | S | Trait |
|---|---|---|---|---|---|---|
| Spider Climb | spider, cave spider, OreSpawn Creeping Horror | movement | climb walls | 2 | 10 | `flag:climbs` |
| Ender Blink | enderman, Ender Knight | movement | blink to the looked-at block, 24 range, 3 s; downside: rain and water hurt | 4 | 20 | `teleport` |
| Rabbit Spring | rabbit, Aerbunny, Gazelle | movement | jump +2 | 1 | 5 | attribute |
| Strider Soles | strider | movement | walk on lava | 3 | 15 | `environment` |
| Phantom Membrane | phantom, Leonopteryx, bat | movement | glide (mecha: as Glider Wings, free) | 3 | 15 | `glide` |
| Dolphin Grace | dolphin, OreSpawn whale | movement | swim x2 | 2 | 10 | attribute |
| Equine Gait | horse, zebra, camel | movement | +15% speed | 2 | 10 | attribute |
| Breeze Updraft | breeze | movement | Wind Charge on secondary; no fall damage | 3 | 15 | `ranged` |
| Shulker Levity | shulker | movement | hover: Levitation on demand for 3 s | 5 | 25 | `last_stand`-style self effect |
| Gills | squid, cod, salmon, drowned, OreSpawn fish | movement | breathe underwater; conflicts with Blaze Core | 2 | 10 | `flag:breathes_water` |
| Golem Frame | iron golem (oil) | defence | +4 armour, knockback resistance 0.5 | 3 | 15 | attribute |
| Armadillo Scutes | armadillo | defence | below 30% HP: roll up, -80% damage for 3 s (60 s cooldown) | 2 | 10 | `last_stand` |
| Turtle Shell | turtle, tortoise | defence | -30% projectile damage | 2 | 10 | `damage_filter` |
| Blaze Core | blaze, magma cube, Kyuubi | defence | fire and lava immunity; conflicts with Gills | 3 | 15 | `damage_filter` |
| Slime Body | slime, Blue Swet | defence | no fall damage; melee attackers bounce off | 2 | 10 | `on_hurt_knockback` |
| Wither Ward | wither skeleton (marrow) | defence | immune to Wither; hits inflict Wither I | 3 | 15 | `on_hit_effect` |
| Pufferfish Spines | pufferfish, Urchin | defence | melee attackers are poisoned | 2 | 10 | `on_hurt_effect` |
| Echolocation | bat | senses | entities within 16 glow every 4 s | 1 | 5 | aura (owner glow) |
| Cat Eyes | cat, ocelot | senses | night vision | 1 | 5 | effect |
| Fox Nose | fox | senses | highlights dropped items and the nearest chest within 24 | 1 | 5 | sense |
| Glow Cells | glow squid | senses | emit light 10 | 1 | 5 | `emits_light` |
| Guardian Eye | guardian, elder guardian | offence | charged laser: 2 s charge, 8 damage (elder: 14) | 4 | 20 | `ranged` |
| Creeper Gland | creeper | offence | Self-Destruct (mecha: ejects the pilot first); on death explode power 3 | 3 | 20 | `on_death_explode` |
| Venom Sac | cave spider, Emperor Scorpion, Komodo | offence | hits poison | 2 | 10 | `on_hit_effect` |
| Pack Instinct | wolf | offence | +1 damage per allied player or tamed wolf within 8 (max +3) | 2 | 10 | `pack` |
| Ravager Ram | ravager, rhino | offence | charge: 3-block run gives x1.5 damage and knockback | 4 | 20 | `charge` |
| Evoker Fangs | evoker | offence | a line of fangs (secondary) | 5 | 25 | `ranged`/summon |
| Witch Brew | witch | offence | lob a harmful potion every 8 s | 3 | 15 | bespoke |
| Hoglin Tusks | hoglin | offence | hits toss the target up | 2 | 10 | `on_hit_toss` |
| Frog Tongue | frog, Cave Fisher | utility | pull a mob or item from 8 blocks | 2 | 10 | `pull` |
| Axolotl Regrowth | axolotl, lizard | metabolism | regenerate 1 HP/2 s in water or rain | 2 | 10 | `regen` |
| Bovine Udder | cow, mooshroom | metabolism | the mecha makes a milk bottle every 5 min (cures your effects) | 1 | 5 | `produce` |
| Feather Bones | chicken | metabolism | slow fall | 1 | 5 | effect |
| Panda Gut | panda | metabolism | Bio-Reactor cores get x2 energy from food | 2 | 10 | bespoke |
| Sniffer Snout | sniffer | utility | 5% chance per dirt block mined to dig up a seed or sherd | 2 | 10 | bespoke |
| Hero's Blood | villager | metabolism | Universal Donor catalyst when used as a gene; villagers trade 10% cheaper | 1 | 0 | bespoke |
| T-Rex Roar | OreSpawn T-Rex | offence | Weakness to everything within 8 (secondary, 20 s) | 5 | 25 | `aura` |
| Basilisk Gaze | OreSpawn Basilisk | offence | Slowness to what you look at within 12 | 4 | 20 | `aura` |
| Hydrolisc Transfusion | OreSpawn Hydrolisc | metabolism | the frame bleeds HP into the pilot when the pilot is low | 3 | 15 | `transfuse` |
| Robot Laser Eye | OreSpawn Giant Robot (oil) | offence | laser bolt, small explosion, no block damage | 5 | 25 | `ranged` |
| Soul Reaper | OreSpawn Ender Reaper | utility | kills bottle Necromancy souls | 4 | 20 | `flag:reaps` |
| Burrower | OreSpawn Molenoid | utility | no suffocation; dig dirt by walking | 3 | 15 | `flag:in_wall_proof` |
| Frost Breath | Mowzie's Frostmaw, Iron's Spells Cryomancer | offence | cone of Slowness and freezing | 4 | 20 | `ranged` |
| Wroughtnaut Plate | Mowzie's Ferrous Wroughtnaut | defence | -40% frontal melee damage | 4 | 20 | `damage_filter` |
| Moa Wings | Aether Moa | movement | double jump and glide | 3 | 15 | `glide` |
| Saci Whirl | Brazil Legends Saci | movement | whirlwind teleport 8 blocks, leaves a dust devil | 3 | 15 | `teleport` |
| Mule Fire | Brazil Legends Headless Mule | offence | fire trail behind you; immune to fire | 3 | 15 | `environment` + immunity |
| **Draconic Wings** (Legendary) | Ender Dragon (Diamond-Tipped Syringe, or a bottle of dragon's breath) | movement | **true flight** on a Chimera regardless of mass | 6 | 30 | bespoke |
| **Decay Aura** (Legendary) | Wither | offence | Wither to hostiles within 6; wither skull on secondary | 6 | 30 | `aura` + `ranged` |
| **Sonic Boom** (Legendary) | Warden | offence | the Warden's sonic boom (15 s) | 6 | 30 | `ranged` |
| **Regrowth** (Legendary) | Twilight Forest Hydra | metabolism | once per 10 min, survive a lethal hit at 30% HP | 5 | 25 | `last_stand` |
| **Blazing Soul** (Legendary) | Cataclysm Ignis | offence | flame strikes around you on landing | 6 | 30 | bespoke |
| **Atomic Breath** (Legendary) | OreSpawn Mobzilla | offence | a long fire beam; huge heat | 6 | 35 | `ranged` |

Legendary genes need a Diamond-Tipped Syringe used on the living boss (one hit's worth of risk, no extra anger).
The Ender Dragon can also be sampled from a bottle of dragon's breath (quality 60), because it is the one boss you
only fight once per world.

### 12.9 Shared trait vocabulary with Necromancy

Both mods describe creature powers with the same 32 parameterised trait types (Necromancy's `ORESPAWN_REVIEW.md` 3.3:
`flag`, `on_hit_effect`, `ranged`, `teleport`, `glide`, `charge`, `aura`, `pull`, `regen`, `damage_filter`,
`last_stand`, `transfuse`, `emits_light` and the rest). Head in a Jar adds:

- a `trigger` field: `passive` (default), `primary`, `secondary`, `on_attack`, `on_hurt`. Necromancy's "combat gate"
  maps to `on_attack` or `primary` when a player is in control;
- three types Necromancy doesn't need: `neutral_to{entities}`, `sense{what, radius}` and `attribute{id, amount}`.

Implementation choice (open question 4): either a small shared library both mods depend on, or two copies of the
same executors with one shared JSON format and a shared test file. Either way, a creature's power means the same thing
whether it is sewn into a Minion, worn as a host or spliced into a mecha.

### 12.10 Stacking, conflicts and synergies

- The same gene twice (in two ports): the second copy is 50% as strong, a third 25%, then nothing.
- Same-category effects of the same kind take the strongest (two glides: best glide wins).
- **Conflicts** (-15 stability each): Blaze Core + Gills or Axolotl Regrowth ("steam lungs"); Ender Blink + Golem
  Frame ("too heavy to blink" also halves blink range); Creeper Gland + Armadillo Scutes; Phantom Membrane + Burrower;
  Cat Eyes + Echolocation (redundant senses fight).
- **Synergies** (+10 stability each, plus a bonus): Spider Climb + Venom Sac ("Recluse": climbing hits poison twice
  as long); Ender Blink + Echolocation ("Blink Sense": blink to a glowing target's back); Ravager Ram + Golem Frame
  ("Battering Ram": charge breaks wooden doors and leaves); Blaze Core + Strider Soles ("Lava Strider": walk on lava
  at full speed); Glow Cells + Cat Eyes ("Deep Sea": underwater vision), Mule Fire + Blaze Core ("Mula Completa":
  the fire trail doubles).

---

## 13. Heads of others

### 13.1 Villager heads (v1, extended)

The Surgical Saw on a villager with an empty jar in your off hand gives a **Jarred Villager** item that keeps its
profession, level and trades (v1). Placed, it trades through the normal screen, restocks twice a day and still levels.
New in v2: it can be **staff**.

### 13.2 Villager staff

Hang a Jarred Villager on an **Employee Mount** attached to a machine. Staff need **payroll**: 1 emerald per in-game
day from a chest touching the mount. Unpaid staff work at half bonus and complain in the machine's status line
("I was promised dental."). Nitwits do nothing and say so.

| Profession | Staffs | Bonus |
|---|---|---|
| Librarian | DNA Sequencer | auto-read accuracy 90% |
| Cleric | Gene Splicer | +10 stability |
| Butcher | Exsanguinator | x1.5 blood |
| Farmer | Bio-Reactor | +25% fuel value |
| Mason | Separation Booth | half blade wear |
| Armorer / Weaponsmith / Toolsmith | Assembly Gantry | -25% repair cost |
| Cartographer | Relay Mast | +50% radius |
| Fisherman | Saline Mixer | +25% output |
| Leatherworker | Body Locker | decay stopped without saline |
| Shepherd | Paint Bay | dyes are free |
| Fletcher | Turret Pedestal | +25% fire rate |
| Nitwit | anything | nothing; random complaints |

### 13.3 Mob heads as components

The saw with an empty jar in the off hand takes a mob's **head** instead of its body: a **Jarred Mob Head** item
(`headinajar:mob_head` with an entity component). Uses:

**Turret brains** (Turret Pedestal, T3: the head sits in a rotating brass mount; 10 FE/t; range 16; never targets
players unless the owner turns on PvP targeting, never targets the owner's team, tamed animals or villagers):

| Head | Turret |
|---|---|
| Skeleton / Stray / Bogged | arrows (from a chest below); stray: Slowness, bogged: Poison |
| Blaze | fireballs (no block fire unless mobGriefing) |
| Guardian | laser after a 2 s lock |
| Creeper | proximity mine: explodes once, no block damage by default |
| Zombie | bonks anything adjacent (1 block, 4 damage). Mostly moral support |
| Snow Golem (oil) | snowballs that slow |
| Witch | splash Weakness |
| Evoker | fangs in a line |

**Cerebral Array** (T3): a 3 x 3 rack block taking up to 9 heads. Each head makes FE "by thinking very hard about
broth". Uses 1 mB Nutrient Broth per second per head.

| Head | FE/t |
|---|---|
| Zombie, husk, drowned | 20 |
| Villager (any) | 40 |
| Witch, piglin | 60 |
| Enderman | 80 |
| Wither skeleton | 100 |
| Evoker | 120 |
| vanilla Dragon Head item, Twilight Forest boss trophies | 1000 (T5 **Board Room** core needed) |

Five or more heads in one Array form a **Board Meeting**: +10% output, and the heads occasionally "vote" in chat.

**Other head jobs:** an Enderman head on a **Teleport Pad** pairs two pads (16 blocks, 2,000 FE per trip); a Witch
head on a brewing stand adds a fourth bottle; a Piglin head on a **Barter Box** turns gold ingots into barter loot.

### 13.4 Player heads (opt-in only)

- Field decapitation of players with the Surgical Saw needs **both** players to have opted in with an **Organ Donor
  Card** (an item; right-click to toggle; a small card icon shows on the nameplate) and server config
  `pvpDecapitation` (default **false** in v2).
- The victim must be at 30% health or below. They become JAR form where they stand; their body stays as their
  Headless Body. Their Collar Cam is *not* fitted (that only happens in a booth), so they can't link until they get
  their head back on.
- A captured head can be carried (3.9). The victim can **Let Go** (v1: confirm, then die and respawn whole) at any
  time, so nobody is ever held hostage longer than they agree to.

### 13.5 The organic computer (v1 Neural Socket, extended)

A jar in a **Neural Socket**:

- **6 channels:** a separate redstone output 0 to 15 on each face (number keys set the selected face's level, scroll
  picks the face); R sends a 1-tick pulse on the selected face.
- **Reads:** the HUD shows each face's redstone input, the item count of an inventory touching the socket, and the
  fill level of a touching tank.
- **Cameras:** Optic Sensors linked with the Linking Wire (v1). With SecurityCraft present, also its Security Cameras
  (Nice).
- **Jar Cam from the socket:** a docked or placed head of yours can be one of the cameras.
- **Mob heads in sockets** run fixed programs: a Zombie head is a 1 Hz clock, a Villager head is a programmable
  sequencer (a small GUI of 8 steps with delays), an Enderman head emits a signal when a player looks at it.
- Leave with Sneak + J (v1).

### 13.6 Head as security camera

A placed `HeadJarEntity` (yours) is a camera: while LINKED, hold the Jar Cam key to look through it, as long as the
client can see that entity (it must be within your render distance). A Cradle with a Camera Mount upgrade turns the
head toward motion and logs who passed in the Cradle GUI.

---

## 14. Death, saves and safeguards

### 14.1 Death rules

| What happens | Result |
|---|---|
| BODY form dies | vanilla death |
| LINKED body reaches 0 HP | **not death.** `LivingDeathEvent` is cancelled; the body becomes a **Corpse** (a Headless Body in the "deceased" state) holding the armour, main inventory and off hand; it never despawns and only the owner and trusted players can open it. You wake up in your head (JAR form, full player health). Curios and XP stay with you. Config `linkLossInventory`: `corpse` (default), `keep` (nothing drops), `vanilla` (items drop on the ground) |
| A host or Utility Shell reaches 0 | the host dies (normal loot); you pop out as JAR; overflow damage goes to Jar Integrity |
| A mecha reaches 0 | 11.3; you are ejected; overflow to Jar Integrity |
| JAR form: integrity and health both reach 0 | real death: vanilla rules, respawn as BODY at your bed. Fitted jar modules, goggles and collar drop with your inventory. If you had an idle Headless Body, it collapses and drops its armour (v1) |
| The head is destroyed while you are LINKED (a creeper blows up the placed jar) | real death, at the body's position |
| `/kill`, void, `outOfWorld` damage | real death in every form |

A Corpse can be **revived** at the Body Printer for half the normal cost (it reuses the body).

### 14.2 Logout and chunk loading

| State at logout | What is saved | On login |
|---|---|---|
| LINKED, head docked in a Cradle | player as normal; the Cradle keeps the head | same |
| LINKED, head placed in the world as an entity | the head entity is removed and its position saved in the HeadRegistry | the head entity is put back first, then the player |
| LINKED, head carried by a friend | the friend's carried-head attachment keeps it; on *your* logout nothing changes for them; on *their* logout the head is placed at their position | normal |
| JAR, body idle in the world | the Headless Body stays in the world, invulnerable while you are offline | normal |
| HOSTED or PILOT | vanilla saves the root vehicle with the player (as it does for horses and boats) | you log in still riding |

- The link check only needs positions from the HeadRegistry, never a loaded chunk. Heads and bodies in unloaded chunks
  can't be hurt, because nothing ticks there.
- The swap teleports the player, which loads the destination chunk as any teleport does. No permanent chunk tickets.
- Relay Masts use lazy energy accounting: when a mast's chunk loads, it subtracts the drain for the time it relayed
  while unloaded (tracked by game time in SavedData).

### 14.3 Dimension travel

| Form | Through a portal |
|---|---|
| LINKED, head not carried and no Entangled Pair | the portal is blocked with a message ("Your head is in another dimension. Please bring it.") Config `linkedPortalRule`: `block` (default), `break` (travel, then the link breaks and you swap home) |
| LINKED, head carried, or Entangled Pair fitted | allowed |
| JAR, HOSTED, PILOT | allowed if the vehicle fits the portal (Colossus doesn't fit a vanilla portal: "Heavy frames need a bigger portal") |

Waystones, Time Vortex interiors, Immersive Portals and Portal Gun portals follow the same rule: we hook
`EntityTravelToDimensionEvent` for cross-dimension moves and the generic teleport event for same-dimension ones. A
modded teleport that dismounts riders leaves the mecha behind; the **Recall Beacon** mecha upgrade (T4, 50,000 FE)
calls an owned mecha to you within the same dimension.

### 14.4 Server restarts

All state is in saved data: the extended `JarState` attachment, the `HeadRegistry` SavedData, entity NBT (heads,
bodies, corpses, hosts, mecha), block entities (Cradle, Booth op state, machines), and the team Gene Archive
(SavedData keyed by team or player). Nothing important lives in client state.

### 14.5 Theft and griefing

- **Bodies and mecha:** only the owner and trusted players can wear, board, edit, loot or remove modules.
  `/headoffice trust <player>` and NeoForge teams (scoreboard teams) define trust.
- **Offline protection:** idle bodies, placed heads and parked mecha of offline players are invulnerable (config).
- **Block damage:** mecha drills, fists, scoops and the Tractor Beam fire `BlockEvent.BreakEvent` as the pilot, so spawn
  protection, SecurityCraft-owned blocks and any claim system can cancel them. Explosive modules and genes obey
  `mobGriefing` and their own config switch (`mechaBlockDamage`, default false).
- **Telekinesis and Tractor Beam** can't lift block entities, unbreakable blocks or SecurityCraft reinforced blocks
  (v1 rule: hardness above obsidian or -1).
- **Mob farms:** sawn hosts and the Exsanguinator are in a tag that stops them dropping loot more than once.
- **Duplication:** the head is never an item (2); hosts are entities; serums are consumed on install.

### 14.6 Config

| Key | Default | What |
|---|---|---|
| `booth.energyPerOp` | 40000 | FE per separation |
| `booth.botchEnabled` | true | the upside-down head gag |
| `link.goggleRanges` | 48, 160, 640 | Mk I to III |
| `link.portalRule` | block | 14.3 |
| `link.allowManualUnlink` | true | 3.7 |
| `link.inputStutter` | true | low-Q input drops |
| `life.linkLossInventory` | corpse | 14.1 |
| `life.salineDrainSeconds` | 12 | per mB |
| `life.protectOfflineBodies` | true | 14.5 |
| `host.decayPerMinute` | 2.0 | default freshness loss |
| `host.syncSecondsPerPercent` | 12 | |
| `host.allowVillagerHosts` | true | |
| `host.blacklist` | (tag) | `headinajar:not_a_host` |
| `pvpDecapitation` | false | 13.4, still needs mutual Donor Cards |
| `mecha.blockDamage` | false | explosive modules |
| `mecha.allowMeatPilots` | true | non-jarred players can pilot at -50% module effect (open question 7) |
| `mecha.maxPerPlayer` | 4 | |
| `genetics.allowSelfEditing` | true | 12.7 |
| `genetics.mutationScale` | 1.0 | x mutation chance |
| `genetics.minigame` | true | false = auto-read only |
| `staff.payroll` | true | emeralds per day |
| `client.photosensitive` | false | 3.6 |
| `client.staticIntensity` | 100 | % |
| `client.handshake` | true | 9.4 |
| `client.targetLock` | auto | on with a controller |

---

## 15. Cross-mod integration

All integrations are soft: guarded by `ModList.isLoaded`, data behind `neoforge:mod_loaded` conditions, and no
compile-time dependency except where an API jar is noted.

### 15.1 Core

| Mod (installed) | Integration | Hook |
|---|---|---|
| **OreSpawn port** (the user's) | host data for the creatures in 9.5, genes in 12.8, titanium and uranium (if present in the port) as alternative plating and core fuel; OreSpawn's Ant Robot and Spider Robot kits as a recipe ingredient for the Skitter frame | data files with `mod_loaded`; item tags; ids checked against the port's registry |
| **Necromancy** (the user's; private build, not in the mods folder today) | Minions as custom hosts (9.8); shared trait vocabulary (12.9); blood fluid via `#c:blood`; organs as fuel and ingredients; souls for the Necrotic Furnace and Ghost Core; Soul Reaper gene | Neural Socket Head part through Necromancy's `NecroMobRegistry`; a new `MinionPilot` hook in Necromancy; fluid tags |
| **Curios** | Head Satchel (belt), Relay Goggles as a cosmetic for BODY form (head) | Curios slot tags and `ICurioItem` |
| **JEI** | categories for every machine, the Booth, the Splicer (shows gene inputs), the Gantry (frame recipes) | `IModPlugin` |
| **Jade** | link Q on bodies and heads, host freshness and sync, machine progress, staff, mecha HP/heat/power | `IWailaPlugin` |
| **Patchouli** | the Employee Handbook (16.2) | book JSON |
| **Controlify** (on the Legion through Prism Console) and the pack's **CrazyCraft Controls** layer | bindings, context-wheel entries, guide hints (11.6) | Controlify entrypoint and binding API; polled keys only |
| **ProjectE** | no EMC for heads, serums, gene disks, Jarred Villagers, mob heads, corpses' contents are normal items | EMC blacklist through ProjectE's custom-conversion data / config |
| **Carry On** | can't pick up bodies, heads, hosts, mecha or Booth parts | Carry On's entity and block blacklist config (ship in the pack's config) |
| **Railcraft Reborn** | FE in and out works by adjacency; `c:ingots/steel` as a plating alternative | capabilities, tags |
| **Iris / Sodium** | HUD and static drawn as GUI layers, no post shaders | design rule |
| **GeckoLib** | mecha and module models (hard dependency; it is in the pack) | `GeoEntity`, render layers |
| **Shoulder Surfing** | chase camera for mecha | its plugin entry (`shouldersurfing_plugin.json`) |

### 15.2 Nice

| Mod | Integration | Hook |
|---|---|---|
| **Walkers** | a host at 100% sync unlocks its Walkers shape for you ("you remember how that body moved") | `tocraft.walkers.api.PlayerShapeChanger` / unlock callbacks; data-gated |
| **Brazil Legends** | Headless Mule host (no saw needed), Saci and Mapinguari hosts and genes, Mula Fire gene | data |
| **Plants & Zombies** (the user's port) | plant hosts (Peashooter turret body, Chomper, Gargantuar); PvZ zombies target jarred heads first ("BRAAAINS"); a jarred PvZ zombie head in the Cerebral Array gives +50% ("it really wants it") | data, a target-priority goal added through `EntityJoinLevelEvent` for PvZ zombie ids |
| **Twilight Forest** | Minotaur host, Hydra Regrowth gene, boss trophies as Board Room brains | data, item tags |
| **Aether** | Moa and Phyg hosts, Moa Wings gene, gravitite as an alternative Rocket Pack ingredient, ambrosium as Bio-Reactor fuel | data, tags |
| **Mowzie's Mobs** | Foliaath and Grottol hosts; Frostmaw and Wroughtnaut genes | data |
| **L_Ender's Cataclysm** | Ignis Blazing Soul gene; ignitium and witherite as T4/T5 alternatives | data, tags |
| **Mutant Monsters** | Mutant Zombie heavy host; its Chemical X as a Splicer catalyst: +25% potency, +20 mutation chance | data, item id |
| **Naturalist** | animal hosts and genes (elephant, rhino, giraffe, komodo, lizard, tortoise) | data |
| **Friends & Foes** | Copper and Tuff Golem hosts (oil blood); Wildfire blood as a Blaze Core source | data |
| **Bumblezone** | Beehemoth flying host; bee genes | data |
| **Iron's Spells 'n Spellbooks** | Cryomancer Frost Breath source; Iron's mobs' blood is Arcane; a **Spell Lens** arm module casts a scroll from the Cargo Hold (Stretch within Nice) | data; Iron's API for scroll casting |
| **Ars Nouveau** | an **Arcane Transducer** turns Source into FE (1 Source = 50 FE); Starbuncle heads are refused ("too cute, HR said no") | Ars `ISourceTile`-style capability |
| **SecurityCraft** | Security Cameras show in the Neural Socket camera list; Retinal Scanners refuse jarred heads wearing goggles ("please remove eyewear"); reinforced blocks can't be lifted | its camera entity/block API; tags |
| **Hats Renewed** | hats render on the jar lid (Hat Mount module) | its render hook or a curios render |
| **JourneyMap** | link range circles, Relay Mast markers, your head's and body's positions as waypoints | `IClientPlugin` |
| **Apothic Attributes** | genes and modules may add Apothic attributes (crit chance, life steal) when present | attribute ids in data |
| **Lootr / YUNG's / Dungeons Arise** | rare loot: Booth Blueprint, Gene Disks with pre-read Draft genes, Noggin & Sons memos | global loot modifiers |
| **Better Advancements** | advancement tree layout | data only |
| **MCHeli** | a LINKED body flies helicopters normally (it is a player); nothing to do | none |
| **Marvel Superheroes** | Iron Man suit parts as an alternative ingredient for Kite thrusters | item ids in recipes |

### 15.3 Stretch

| Mod | Integration | Hook / risk |
|---|---|---|
| **HBM's Nuclear Tech** | HE Transducer block (HE <-> FE); RTG Pile core with Pu-238; nukes and EMP devices as EMP events; radiation interferes with links | HBM's `api.hbm.energymk2` interfaces; detecting its blasts reliably is the risk |
| **Immersive Portals / Portal Gun** | an Optic Sensor rendered as a live portal "monitor screen" | heavy; their rendering API changes often |
| **Thaumaturge** (the user's private fork) | aspects on Head Office items; a Cognitio essentia jar as a Cerebral Array booster | private API, low value |
| **Neo Origins / Origin Awakening** | a "Head in a Jar" origin that starts the game already jarred | data packs; tone check |
| **Weeping Angels** | a stone-construct host that can't move while observed but takes no damage while observed | fun, risky movement code |
| **Gravity Gun Revival** | blacklist bodies and heads; allow throwing unpiloted light mecha for laughs | config |

### 15.4 If added to the pack later

Create (mecha parts from Create assemblies, Mechanical Arm loading the Gantry), Mekanism (chemical tanks for saline,
Mekanism energy cables), Modular Routers or AE2 (fluid and item logistics for the lab), ComputerCraft (the Neural
Socket as a peripheral), Tinkers' Construct (bone and slime alloys for hosts), Iron Jetpacks (jetpack modules as
ingredients).

---

## 16. Presentation

### 16.1 Advancements

Root: **Orientation Day** (obtain the Employee Handbook). Titles in pt_br are adapted, not translated literally.

| Advancement | Trigger |
|---|---|
| Signed in Triplicate | sign the Booth waiver |
| Head Start | first separation |
| Out of Body Experience | walk 500 blocks LINKED |
| Can You Hear Me Now? | recover from Q below 10 without losing the link |
| Have You Tried Turning It Off and On Again | lose the link |
| Cutting Remarks | get a botched cut |
| Hostile Takeover | pilot a mob host |
| Fully Synced | reach 100% sync |
| **Headless Horseman** | ride a horse while LINKED |
| **Mula-sem-cabeça** | wear the Headless Mule |
| Frankenstein's Intern | pilot a Necromancy Minion |
| Out for Blood | take 10 Blood Samples |
| Splice of Life | make your first serum |
| Mutually Assured | suffer a mutation |
| Chimera | a mecha with 5 or more genes |
| Synergy! | any synergy pair |
| Employee of the Month | staff a machine with a villager |
| Brain Trust | a Cerebral Array making 500 FE/t |
| Board Meeting | 5 heads in one Array |
| Ask Me Anything | send a redstone pulse from a Neural Socket |
| Corporate Ladder | assemble a mecha |
| Golden Parachute | auto-eject below 10% HP |
| Head and Shoulders Above | fit a Periscope Stalk |
| Two Heads Are Better Than One | co-pilot a mecha |
| Heavy Is the Head | pilot a Colossus |
| Frequent Flyer | fly a Kite 1000 blocks |
| Legendary Genes | complete a Legendary gene |
| Chairman of the Board | build an Executive |

### 16.2 Employee Handbook (Patchouli)

| Category | Entries |
|---|---|
| Welcome Aboard | a letter from Dr. Noggin; tiers as job titles; safety placards |
| Health and Safety | layered life; consent; Let Go; photosensitive options |
| Separation Services | the Booth: build (with a multiblock page), sequence, upgrades, failure modes, reversal |
| Telepresence | goggles, Collar Cam and upgrades, Q, HUD, masts, swap, link break, carrying heads |
| Life Support | saline, charge, integrity, the Cradle, jars |
| Jar Accessories | every jar module |
| Bodies and Hosting | own body, corpses, the saw, freshness, sync, the handshake, host table, Utility Shells |
| Necromancy Liaison Office | (only with Necromancy) Neural Socket Head, piloting Minions |
| Mecha Division | frames, slots, power/heat/weight, every module, the Gantry, controls, passengers |
| Genetics Department | blood, types, centrifuge, sequencing, the Splicer, stability, mutations, targets, the gene list |
| Human Resources | villager staff, payroll, complaints |
| Facilities | power, Tesla Nodes, fluids and machines |
| Appendix | Form 27-B, Form 9 "Request for Return of Head", the Mutation Table |

### 16.3 Sounds

booth.clamp, booth.hiss, booth.scan, booth.cut, booth.pour, booth.ratchet, booth.chime, booth.oopsie (a sad trombone
blip); link.static (looped, volume by Q), link.lost, link.restored, link.swap; jar.slosh, jar.bump, jar.crack;
cradle.hum; host.handshake_hit, host.handshake_miss, host.squelch (mount); mecha.step_light/medium/heavy,
mecha.servo, mecha.jets, mecha.vent, mecha.overheat_alarm, mecha.eject, mecha.drill_loop, mecha.arc, mecha.boot
(a cheerful corporate jingle); gene.centrifuge_loop, gene.sequencer_blip (per base, pitch by base), gene.splice,
gene.mutation; array.murmur (a crowd of tiny voices); staff.complain. All short, original, non-verbal.

### 16.4 Particles

Saline bubbles (in jars), anaesthetic haze (green), scan plane (a quad, not particles), static sparks around a body
at low Q, Tesla arcs (line renderer), steam vents, heat shimmer above hot mecha, blood droplets as dark-red dust only
for syringe use (no gore), DNA helix sparkle at the Splicer, fly swarm on Ripe hosts, smoke and sparks on damaged
mecha.

### 16.5 Art direction

- **Vanilla-plus.** 16 x 16 block textures, 1 texel = 1/16 block on every model, no stretched or squashed pixels,
  no texel-density mixing inside a model. Larger mecha get larger models, not scaled ones.
- **Palette:** "1950s corporate lab": ivory enamel, brass, teal glass, rubber black, warning-stripe yellow, with
  Noggin & Sons green-glass jars. Emissive pixels only for screens, lenses and indicator lights.
- **Models** in Blockbench: blocks as Java block models; mecha, modules, heads, Turret Pedestal and the Booth's moving
  parts as GeckoLib models. Each part is checked from all angles (front, back, top, underside) before it ships.
- **Hosts** reuse the vanilla or modded mob's own model with the head part hidden and a neck seal drawn at the neck
  anchor; no new mob art.
- **GUIs:** vanilla grey panels with a teal CRT inset; Noggin & Sons logo (a jar with a bow tie) in the corner.
- **Heads:** the player's own skin head (v1 JarRenderer), with goggles and the fluid tint by jar tier.

---

## 17. Localisation

The mod ships native `en_us` and `pt_br`, written side by side from one source (`src/main/lang-src`), with a check
tool that proves every key exists in both (the same approach as the Necromancy port). pt_br uses Minecraft's official
Portuguese names for every vanilla creature and item, and the pack's names for modded ones. Jokes are adapted, not
translated: DOLORES keeps her name; "Head Office" becomes "Cabeça no Pote"; puns that don't land in Portuguese get
new Portuguese jokes (the Headless Mule advancement exists for exactly this audience). All machine "speech" is text,
so there is no voice to localise.

---

## 18. Technical feasibility (NeoForge 1.21.1)

### 18.1 What v1 gives us

| v1 piece | Reused for |
|---|---|
| `JarState` attachment, Codec and sync codec | extended with: link state, head location, saline, integrity, collar upgrades, goggles tier, body gene list. Charge migrates x100 to FE |
| `Form` enum | gains `LINKED`, `HOSTED` (renames `SHELL`), `PILOT`; `SOCKET` becomes `SOCKETED` |
| `EntityEvent.Size` hitbox code | LINKED (0.6 x 1.45, eye 1.32) and the jar size inside vehicles |
| Interaction lockout events | unchanged for JAR; extended rules per form |
| `HeadlessBody` entity and renderer | the idle body, the Corpse state, the Collar Cam render instead of the neck stump |
| `JarRenderer` and `JarModel` | `HeadJarEntity`, the Cradle, mecha cockpits, carried heads, hosts' necks |
| `FormActions.detach / reattach` | the swap (3.4) and the Suture Kit |
| `Telekinesis` and `HeldBlockEntity` | the Tractor Beam arm |
| `JarMenu` and `JarScreen` | jar modules and collar upgrades |
| `ChargingDock` | unchanged, FE |
| `ModuleType`, `ModuleEffects` | jar modules (now data plus enum) |
| `Network` payloads | template for link-quality sync, mecha input and swap |
| GameTests and the hidden smoke client | extended per phase |

### 18.2 Item by item

| Feature | Approach | Risk | Fallback |
|---|---|---|---|
| Camera relay (LINKED) | no relay at all: the player *is* the body; eye height from `EntityEvent.Size`; head and hat hidden in `RenderPlayerEvent.Pre` by setting model part visibility, Collar Cam drawn by a render layer | low | |
| Static and HUD | GUI layers; strip-offset tears of our own HUD only | low | flat overlay alpha only |
| Input stutter | client `MovementInputUpdateEvent` (v1 hook) and `InputEvent.InteractionKeyMappingTriggered` | low | turn off by config |
| Jar Cam peek | client `Minecraft.setCameraEntity(head)` while the key is held; requires the head entity tracked by the client (within its tracking range) | medium: entities outside tracking range don't exist client-side | grey "OUT OF RANGE" card; give `HeadJarEntity` a large `clientTrackingRange` (10 chunks) |
| Swap | server teleport (`teleportTo` with level), spawn/remove `HeadlessBody` and `HeadJarEntity`, resync form; one tick, behind a static wipe | medium: other mods' teleport hooks fire | treat the swap as a teleport cause other mods can see |
| Body death interception | cancel `LivingDeathEvent` at highest priority for LINKED players, set health, move items to the Corpse, swap | medium: other mods that act on death (Curios drop rules, totems) | run after totems; ignore if another mod already cancelled |
| Booth multiblock | a pattern check on wrench use; formed state in the Console BE; ghost-block hologram as a client render of missing states | medium (UI work) | the single Separation Chair block (4.1) |
| Booth animation | the chair and Excision Head are GeckoLib block entities; the cut is a camera fade, so no gore animation is needed | low | static models + sounds |
| Mob hosts as vehicles | mixin into `Mob.getControllingPassenger` (return the jar player when the mob is a host) plus `LivingEntity` ridden-input hooks (`getRiddenInput`, `tickRidden`, `getRiddenSpeed`), the same path camels and horses use | **high**: each creature's own movement control may fight the rider (fliers, swimmers, jumpers) | `control: shape` (9.6): store the host on the player, borrow its hitbox and attributes, draw a client copy (the v1 SHELL plan) |
| Host abilities | our trait executors run on the host entity (vehicle) or the player (shape) | medium | ship fewer abilities per creature at first |
| Necromancy Minion hosts | `MinionEntity` as a vehicle; a small pilot hook in Necromancy | medium (two codebases) | Minions without pilot-mode abilities: passive traits only |
| Mecha entity | `LivingEntity` subclass (not `Mob`: no goals, no despawn), ridden by the jar player; client-authoritative movement when the local player controls it (vanilla vehicle rules) | **high**: large hitboxes trip "moved wrongly"; step-up and collision for 2.4 x 4.4 | keep heavy frames to 2.4 wide max; tune the server's vehicle move check via the event; fall back to server-side movement for heavy frames |
| Mining with arms | the pilot (a player at the cockpit) mines with raised `block_interaction_range`; break speed from `PlayerEvent.BreakSpeed`; area mining server-side with a `BreakEvent` per extra block | medium | 1 x 1 only at first |
| Damage routing | `LivingIncomingDamageEvent` on the pilot redirects to the mecha or host; `LivingChangeTargetEvent` retargets mobs to the vehicle | low | |
| Modular mecha rendering | GeckoLib `GeoEntityRenderer`; a render layer draws each module's model at its bone's matrix; module models cached per frame class | medium (performance with many mecha) | merge static parts into the frame model at bake time; LOD: hide small modules beyond 32 blocks |
| Paint | grayscale mask textures tinted per channel in the layer | low | fixed colour presets |
| Mecha sync | module list and paint as a custom `EntityDataSerializer` (sent on change); heat, power and HP as plain synced data | low | custom payload on change |
| Mecha input | one `ServerboundMechaInput` payload per tick only when the button state changes (arms, ability, brace, wheel choice); server authoritative for weapons, heat and power | low | |
| Link computation | server, every 10 t, positions from HeadRegistry; BFS over Relay Masts (dozens at most) | low | |
| Sequencer minigame | client screen; sends only the final accuracy, which the server clamps and rate-limits (one read per real read time) | low: a cheater can only skip a minigame they could auto-read anyway | |
| Gene effects | data-driven executors; attributes as `AttributeModifier`s with ids `headinajar:gene/<gene>/<slot>` | medium (breadth) | ship 20 genes first |
| Controller | Controlify binding API behind a soft check; polled `KeyMapping`s | low | keyboard-only plus Controlify's generic key mapping |
| Shoulder Surfing camera | its plugin JSON | low | our own camera offset |
| Walkers unlock | call the Walkers API behind a soft dependency check | medium (API stability) | skip |
| HBM HE Transducer | implement HBM's MK2 receiver/provider interfaces in a compat class loaded only with HBM | medium | leave out |

### 18.3 Networking summary

| Payload | Direction | When |
|---|---|---|
| `FormSync` (v1, extended) | S to C, tracking | on change |
| `LinkSync` (Q, ping, head distance, flags) | S to C, self | Q changed by 1+ or flags changed |
| `SwapRequest` | C to S | key |
| `MechaInput` | C to S | button state change |
| `MechaConfigure` (install, remove, slider, paint) | C to S | Gantry GUI actions, validated server-side |
| `SequencerResult` | C to S | end of a read |
| `SpliceRequest` | C to S | Splicer button |
| `HandshakeResult` | C to S | end of the handshake |

---

## 19. Build plan

Each phase is a playable slice. GameTests run on the hub test server; render checks use the v1 hidden smoke client.

| Phase | Name | Content | v1 reuse | GameTests |
|---|---|---|---|---|
| **P0** | Groundwork | rename to Head Office (display only), FE charge migration (x100), extended `JarState` codecs with defaults for old saves, HeadRegistry, trust and consent commands, polled keys, config | everything | v1 tests still green; `oldSaveMigratesChargeToFe`; `trustListPersists` |
| **P1** | Head Start | Separation Booth (Chair block first, multiblock second), Goggles Mk I, Collar Cam, `HeadJarEntity`, Life Support Cradle, LINKED form, link Q and HUD, swap, link break, Corpse rule, Suture Kit, Head Satchel | detach/reattach, HeadlessBody, JarRenderer, Size | `boothRefusesWithoutFullBuffer`, `boothAbortsBeforeCutOnPowerLoss`, `boothFinishesAfterCut`, `onlySeatedPlayerCanSign`, `linkedPlayerHasCollarEyeHeight`, `qualityFallsWithDistance`, `linkBreaksAndSwapsHome`, `bodyDeathMakesCorpseNotDeath`, `jarDeathIsRealDeath`, `portalBlockedWhileLinked`, `carriedHeadIsPerfectLink`, `swapRoundTrip` |
| **P2** | Life Support | saline, integrity, Saline Mixer, Galvanic Pile, Leyden Jar, Tesla Nodes, Charging Dock to FE, refined jar modules, Goggles Mk II and III, Relay Masts, Signal Jammer, Body Printer, Safety Placards, the guide skeleton | modules, JarMenu, dock | `salineDrainsOnlyAwayFromCradle`, `dehydrationHurtsIntegrity`, `teslaLinkMovesEnergyWithLoss`, `relayChainExtendsRange`, `jammerCutsOthersNotOwner`, `newModulesTick` |
| **P3** | Hostile Takeover | Surgical Saw, hosts (vehicle with shape fallback), freshness, Embalming, Body Locker, sync and handshake, 15 vanilla host files, Utility Shells, villager heads, Necromancy Neural Socket Head (if present) | SHELL plan, saw design | `sawRefusesHealthyMobs`, `sawRefusesBossesAndPets`, `hostDecaysAndCollapses`, `lockerStopsDecay`, `syncUnlocksAtThresholds`, `hostDeathEjectsJar`, `endermanBlinkRespectsCooldown`, `minionHostKeepsPartTraits` (with Necromancy), `villagerHeadTrades` |
| **P4** | Mecha Division | Assembly Gantry, Hopper and Bulwark frames, 25 modules (legs, arms, core, basic torso, sensors), power/heat/weight, GeckoLib rendering with bone attachments, controls (keyboard and Controlify), chase camera, eject, Golden Parachute, Passenger Pod | Telekinesis (Tractor Beam) | `overweightLocksServos`, `heatThrottlesAt85`, `ventAt100`, `noPowerLimpMode`, `drillFiresBreakEvent`, `damageRoutesToMecha`, `ejectDropsJar`, `onlyOwnerCanBoard`, `flightNeedsThrustAboveMass` |
| **P5** | Genetics Department | Syringes, Leech Jar, Exsanguinator, Centrifuge, Sample Fridge, Sequencer with minigame and auto-read, Gene Archive, Splicer, serums, Chimera frame, Gene Ports, Injection Rig, self-editing and Gene Wash, 25 genes, mutation table | | `bloodlessNeedsRightTool`, `qualityScalesWithHealth`, `readoutAccumulates`, `draftGeneHalfPotency`, `stabilityMath`, `mutationChanceFormula`, `mechaNeverRejects`, `hostRejectsWrongBloodType`, `genesStayWithBody`, `sameGeneDiminishes`, `conflictCostsStability` |
| **P6** | Board of Directors | Skitter, Wheelhouse, Colossus, Kite, Executive frames; remaining modules; Cerebral Array and Board Room; villager staff and payroll; turret brains; Neural Socket channels and mob-head programs; Entangled Pair and Quantum Goggles; Legendary genes; EMP | Neural Socket and Optic Sensor designs | `arrayOutputPerHead`, `unpaidStaffHalfBonus`, `turretNeverTargetsOwner`, `socketSixChannels`, `entangledLinkCrossesDimensions`, `empBreaksLinkUnlessShielded` |
| **P7** | Integrations and polish | Core integrations, then Nice; full Patchouli handbook; advancements; sounds and particles; art pass; pt_br pass; balance pass with measured numbers | | data-load test with each integrated mod present and absent; `everyLangKeyInBothLanguages`; hidden-client screenshots of every frame and module from four angles |

Effort guide (one developer): P0 S, P1 L, P2 M, P3 L, P4 L, P5 L, P6 L, P7 M to L. The first fun slice is the end of
P1: sit in the booth, walk around headless, lose signal in a cave and wake up at home.

---

## 20. Open questions

| # | Question | Recommendation |
|---|---|---|
| 1 | Display name | **Head Office** (pt_br "Cabeça no Pote"); mod id stays `headinajar` |
| 2 | What a LINKED body's death costs | **Corpse holds the inventory** (14.1): death-like loss without real death |
| 3 | Field decapitation of players | **Off by default**, and only with mutual Donor Cards when on |
| 4 | Shared trait code with Necromancy | **One JSON vocabulary, two copies of the executors** for now (Necromancy is a private build and Head Office could be public); extract a shared library later only if both keep growing |
| 5 | Add Necromancy to CrazyCraft 5.0? | **Yes**, it is the best host source in the pack; everything still works without it |
| 6 | Self gene editing | **Yes, limited**: 2 serums of load 3, rejection by blood type, washable |
| 7 | Can non-jarred players pilot mecha? | **Yes, as "meat pilots" at -50% module effect**, so friends can join without the booth |
| 8 | True flight | **Only** the Kite frame, light builds with enough thrust, and the Draconic Wings gene |
| 9 | Mob host control | **Vehicle by default, shape fallback per creature** in data |
| 10 | Is the DNA minigame mandatory? | **No**: auto-read at 4x time and 60% accuracy |
| 11 | Publishing | Head Office is original code and art, so it can be public on GitHub; the Necromancy integration ships as data and a soft hook only, never Necromancy code |

# Head in a Jar — design (NeoForge 1.21.1)

A sci-fi survival mod built around one idea: take your own head off and keep living in a
life-support jar. A bare jar can do nothing at all. Modules, robot bodies, borrowed mob bodies
and machine sockets give back everything a body could do, and some things it never could.

Inspired by a 2020 r/feedthebeast idea post and GlebTiK's unfinished 1.12.2 prototype
"Head In A Jar". That prototype has no license of its own, so none of its code or art is used.
This is an original implementation of the idea.

## 1. Forms

The player is always in exactly one form. It is stored in a player attachment, synced to every
client that tracks the player, and reset to `BODY` on death. Death is therefore the escape hatch
from every form, so a player can never be soft-locked.

| Form     | What it is | Hitbox |
|----------|------------|--------|
| `BODY`   | normal player | vanilla |
| `JAR`    | a head in a jar on the ground | 0.5 × 0.55, eyes at 0.3 |
| `SHELL`  | the jar riding a robot or mob body that the player steers as their own | the body's size |
| `SOCKET` | the jar plugged into a Neural Socket block | does not move |

Your own body does not vanish. When you take your head off, a **Headless Body** entity stands
where you were. It wears your skin and armour, and its slim or wide arm shape follows your skin
model. Look at it within reach and press *use* to put your head back on. If it is killed, its
armour drops, and from then on you need a robot body, a borrowed mob body, a Body Printer or a
death to get back to normal.

## 2. Life support

* Jar form never gets hungry. Instead the jar holds **Charge** (0–1000 by default). Modules spend
  it, and every tick in a jar costs a little.
* Recharge it from the jar menu by feeding redstone dust (+50) or a redstone block (+450), or by
  standing on a **Charging Dock** (+5 per tick).
* At 0 charge every module goes offline. The head itself stays alive.
* A sealed jar can't drown and takes no fall damage under 6 blocks.
* Damage goes to the player's normal health, with 2 armour from the glass.

## 3. What a bare jar can do

* It can see, talk in chat, open its inventory and open the **Jar menu** (default key `J`).
* It can't walk, jump, mine, place, use items, attack or pick things up.
* The Jar menu fits modules from your own inventory, feeds charge, and has the *Let go* button.
  *Let go* asks for confirmation, then kills you, which resets your form.

## 4. Modules (3 slots; a Reinforced Jar has 5)

| Module | Effect | Charge |
|--------|--------|--------|
| Hop Springs | walking returns as small hops at 60% speed | 1 per hop |
| Propeller | creative-style slow flight (NeoForge `creative_flight` attribute) | 1/tick while flying |
| Manipulator Arm | mine (×0.5 speed), place, use items, attack (1 damage), pick up items | 2 per action |
| Telekinesis | `R` grabs the block, mob or item you look at (16 blocks); `R` again drops it; *attack* throws it | 2/tick held, 20 per throw |
| Drill Bit | ×3 mining speed; needs an arm or a body | none |
| Armour Plating | +6 armour, +2 toughness | none |
| Heat Shield | immune to fire and lava | 1/tick while burning |
| Night Lens | night vision | none |
| Magnet | pulls items within 6 blocks | none |
| Water Jet | swim fast | 1/tick while swimming |

Modules are items. Their effects are data components on the fitted jar and run each tick from
the player attachment. Telekinesis holds blocks as a `HeldBlock` entity. A thrown block becomes a
vanilla falling block that hurts what it hits. Block entities, unbreakable blocks and blocks
harder than obsidian can't be lifted.

## 5. Robot bodies (Shell form)

Each robot is crafted as a chassis item and placed as an entity. A jar player who uses one climbs
in, and the robot is stored on the player while it is worn. The player takes the robot's hitbox,
attributes and abilities, and their jar renders in its head socket. Sneak + `J` climbs out and
puts the robot back in the world, with damage kept.

| Body | Size | Health | Notes |
|------|------|--------|-------|
| Golem Chassis | 1.4 × 2.7 | 60 | 9 attack damage, knockback resistance, +1.5 reach, slow |
| Rover Chassis | 1.2 × 0.9 | 30 | fast, step-up 1.5, 9 slots of cargo, can't mine |
| Spider Walker | 1.4 × 0.9 | 30 | climbs walls, no fall damage |

Robots run on the jar's charge. While worn, their health stands in for yours, and when they break
you drop back to `JAR` form.

## 6. Mob bodies (Shell form)

A **Surgical Saw** used on a mob below 25% health takes its head off. The mob loses its AI and
stays standing, headless. A jar player who uses that body takes it over, the same way as a robot
body: the player becomes that mob's shape and abilities with the jar on its neck. Abilities are
data-driven (`data/<ns>/headinajar/body/<entity>.json`):

* attributes: health, speed, jump strength, armour, attack damage, step height, scale
* flags: `fire_immune`, `water_breathing`, `climb`, `slow_fall`, `no_fall_damage`, `flight`
* one active on `R`: `teleport` (enderman), `explode` (creeper; destroys the body), `fireball`
  (blaze, ghast), `web` (spider), `swim_dash` (dolphin), `charge` (ravager)

Bodies that aren't covered by a data file get attributes from the entity type and no flags. The
head is hidden through the model's `HeadedModel` head part. For other models the jar sits on top
of the bounding box, and per-type offsets live in the same data file.

## 7. Heads of others

* **Villagers.** The saw on a villager with an empty Life Support Jar in your inventory produces a
  *Jarred Villager* that keeps its profession, level and trades. Place it on any surface. Using it
  opens the normal trading screen. It restocks twice a day and still levels up from trades.
* **Players.** The saw on a player at 30% health or below (`pvpDecapitation`, on by default)
  puts them in `JAR` form with no modules, and their Headless Body stays behind. A jar player can
  be picked up (sneak + use) and rides on your shoulder until you sneak again.

## 8. Organic computer

A **Neural Socket** block takes a jar player (use it while within 2 blocks). While socketed:

* number keys or scroll set a redstone output of 0–15 on all sides; `R` sends a 1-tick pulse;
* the HUD shows the strongest redstone input reaching the socket;
* **Optic Sensors** linked with a Linking Wire act as cameras; `←`/`→` cycles through them;
* sneak + `J` unplugs you.

## 9. Getting a body back

* Your own Headless Body (use it).
* **Body Printer**: a multiblock-free machine. Insert 16 rotten flesh, 8 bone blocks and 4
  redstone blocks, and 60 seconds later it prints a fresh Headless Body owned by whoever started
  it.
* Death.

## 10. Crafting and progression

Iron and glass early, redstone for charge, and gold, diamond and copper for the advanced modules.
The Reinforced Jar needs a netherite scrap. The Neural Socket needs a comparator and an ender eye.
Every recipe gets an advancement and a Guide entry (the *Field Manual for the Recently
Decapitated* item).

## 11. Technical plan

* `PlayerForm` attachment (Codec + StreamCodec), with `copyOnDeath = false`. A
  `ClientboundFormSync` payload goes to the player and everyone tracking them.
* Hitbox and eye height through `EntityEvent.Size`; `refreshDimensions()` runs on every form change.
* Lockout via `PlayerInteractEvent.*`, `AttackEntityEvent`, `BreakSpeed`, `ItemEntityPickupEvent`
  and a client `MovementInputUpdateEvent`.
* Rendering: `RenderPlayerEvent.Pre` is cancelled in `JAR` and `SHELL` form. The jar is drawn by a
  `JarRenderer` (glass, fluid and the player's skin head). In Shell form a client-side copy of the
  body entity takes its position, rotation and walk animation from the player and is drawn with
  the jar at its head anchor. First-person hands are hidden in `JAR` form without an arm module.
* Headless Body: a `LivingEntity` with armour slots and the owner's UUID and name. On the client its
  skin comes from `PlayerInfo`, then from the profile cache, then from the default skin.
* Robots: `PathfinderMob` subclasses with no AI goals and saved health and cargo.
* Tests: GameTests on the hub (form changes, lockout, module effects, telekinesis, villager jar,
  body round trip) and a hidden client for render screenshots.
* Native en_us and pt_br.

## 12. Build order

1. Forms, Life Support Jar, Headless Body, lockout, jar render, hitbox. **(first)**
2. Modules, charge, Jar menu, Charging Dock, telekinesis.
3. Robot bodies.
4. Mob bodies and the Surgical Saw.
5. Villager jars, player decapitation, carrying.
6. Neural Socket, Optic Sensors.
7. Body Printer, the guide book, advancements, pt_br, art pass.

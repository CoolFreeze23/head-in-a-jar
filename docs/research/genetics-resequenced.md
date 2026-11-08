# Research: Genetics Resequenced (ideas only)

Source studied: the public Genetics Resequenced mod (NeoForge 1.21, LGPL-3.0, a modern remake of Genetics Reborn): its
documentation pages, its gene and mob data tables and its default recipe tables. Nothing here is copied; this note
records what the mod does, in our own words, and what we could borrow. Our reference is DESIGN-v2.md section 12.

Notation: "w" is the weight a mob has in a gene draw (chance = w / sum of that mob's weights). "Basic" is a filler
result that nearly every mob can also give. The number in brackets after a gene is its DNA point cost (how full a
plasmid must be) where the data gives one.

---

## 1. Every gene

### 1.1 Standard genes (natural drops from mobs)

| Gene | Effect | Mobs (weight) | Notes |
|---|---|---|---|
| Basic | filler, cannot be injected, only adds plasmid points | almost every mob, w 2 to 9 | 1 point per helix |
| Bioluminescence (16) | holder drops a short-lived light block where it stands | Blaze 3, Glow Squid 4, Magma Cube 4 | also a direct recipe: cell growth + glowstone or blaze powder, 85% |
| Chilling (20) | melee hits may freeze the target (75%, 15 s, configurable) | Husk 4, Snow Golem 2 | |
| Claws (20) | unarmed damage +8, chance to cause Bleeding (1 dmg/s) | Polar Bear 3 | evolves to Claws II |
| Emerald Heart (30) | mob drops an emerald on death | Evoker 3, Pillager 2, Villager 2, Vindicator 2, Zombie Villager 4 | Villager cell + emerald recipe, 85%; also the cure-side of the villager zombify potion |
| Ender Dragon Health (60) | damage is diverted into a held Dragon Health Crystal (1000 points, repaired with ghast tears) | Ender Dragon 3 | needs the crystal item |
| Fire Proof (24) | fire immunity | Blaze 1, Magma Cube 3, Strider 4, Zombified Piglin 3 | prerequisite of Lava Proof; virus parent of Flambe |
| Johnny (20) | axe melee damage +25% | Vindicator 3 | |
| Knockback (20) | attack knockback +2 | Donkey 3, Goat 4 | |
| Lay Egg (12) | occasionally lays an egg | Chicken 4, Parrot 4 | Chicken cell + egg recipe, 100% |
| Meaty (12) | can be sheared for a raw porkchop (players: sneak with shears) | Hoglin 5, Pig 2, Piglin Brute 4, Piglin 3, Zoglin 4, Zombified Piglin 3 | evolves to Meaty II; Pig cell + porkchop recipe, 100% |
| Milky (12) | can be milked with a bucket | Cow 4, Mooshroom 4 | |
| More Hearts (40) | max health +20 (10 hearts) | Enderman 1, Iron Golem 2, Ravager 3, Warden 5 | evolves to More Hearts II; Enderman cell + golden apple, 20% |
| No Fall Damage (30) | fall damage immunity | Chicken 1, Parrot 2, Slime 4 | prerequisite of Flight |
| Poison Immunity (24) | poison immunity | Cave Spider 1, Witch 2 | virus parent of Poison |
| Scare Creepers (20) | creepers flee the holder | Cat 2, Ocelot 3 | virus parent of Green Death |
| Scare Skeletons (20) | skeletons flee | Wolf 2 | virus parent of Un-Undeath |
| Scare Spiders (50) | spiders flee | none natural | mutation recipe only |
| Scare Zombies (50) | zombies flee | none natural | mutation recipe only |
| Step Assist (10) | step height +1 block | Donkey 3, Horse 3, Llama 3, Polar Bear 4, Skeleton Horse 4, Zombie Horse 4 | prerequisite of Flight |
| Thorns (12) | 15% to reflect 1.5 damage to melee attackers (needs no chestplate or leather) | Bee 3, Pufferfish 4 | prerequisite of Photosynthesis |
| Wither Hit (20) | melee inflicts Wither for 5 s | Wither Skeleton 2, Wither 4 | virus parent of Poison IV |
| Wither Proof (40) | wither immunity | Wither 3 | virus parent of Wither |
| Wooly (12) | can be sheared for wool (players lose "outer layers", a joke) | Goat 3, Sheep 4 | |
| Web Walker (15) | walk through cobwebs freely | Spider 1, Cave Spider 1 | requires Weaving (a negative gene) first |
| Web Defense (25) | chance to spawn a cobweb on attackers | Spider 1, Cave Spider 1 | requires Weaving first |
| Lava Proof (30) | lava damage immunity | Strider 2 | requires Fire Proof first |

### 1.2 Potion-effect genes (permanent effect while held)

| Gene | Effect | Mobs (weight) | Notes |
|---|---|---|---|
| Haste (30) | Haste | Silverfish 3 | Haste II by mutation |
| Invisible (50) | Invisibility | Phantom 3 | |
| Jump Boost (10) | Jump Boost | Breeze 5, Dolphin 2, Donkey 3, Fox 2, Frog 3, Horse 3, Rabbit 5, Skeleton Horse 4, Zombie Horse 4 | |
| Luck (50) | Luck | Rabbit 4 | Rabbit cell + emerald, 75%; virus parent of Cursed |
| Night Vision (16) | Night Vision | Bat 1, Cave Spider 5, Spider 3, Wolf 3 | virus parent of Blindness |
| Regeneration (60) | Regeneration | Iron Golem 2 | Regeneration IV by mutation; Iron Golem cell + golden apple, 30%; also the key ingredient of the cure potion |
| Resistance (30) | Resistance | Drowned 4, Iron Golem 4, Ravager 4, Shulker 4, Zombie 1 | Resistance II by mutation; virus parent of Gray Death |
| Speed (20) | Speed | Dolphin 2, Fox 2, Horse 3, Ocelot 2, Rabbit 3, Skeleton Horse 4, Zombie Horse 4 | II and IV by mutation; Rabbit cell + golden boots, 65% |
| Strength (20) | Strength | Iron Golem 3, Polar Bear 4, Ravager 5 | Strength II by mutation |
| Water Breathing (16) | Water Breathing | Axolotl 5, Cod 2, Elder Guardian 4, Glow Squid 4, Guardian 4, Pufferfish 4, Salmon 3, Squid 2, Tadpole 4, Tropical Fish 2, Turtle 2 | |

### 1.3 Player-only genes (cannot go into mobs)

| Gene | Effect | Mobs (weight) | Notes |
|---|---|---|---|
| Chatterbox (20) | narrator reads your chat aloud | Allay 6, Parrot 6 | novelty |
| Dragon Breath (20) | key: fire a dragon fireball | Ender Dragon 6 | virus parent of White Death |
| Eat Grass (16) | right-click grass to eat it (+1 health, +1 saturation; turns to dirt) | Cow 3, Goat 3, Mooshroom 4, Sheep 3 | jokes with Wooly and Cringe; prerequisite of Photosynthesis |
| Efficiency (30) | mining speed bonus that stacks with Haste and the enchant | Silverfish 1 | Efficiency IV by mutation |
| Flight (50, mutation) | creative-style flight | Wither 3, Ender Dragon 2 | requires Teleport + Step Assist + No Fall Damage; Ender Dragon cell + elytra, 55% |
| Infinity (30) | bows and crossbows need no arrows | Skeleton 1, Stray 3 | |
| Item Magnet (30) | pick up items from 8 blocks | Allay 5, Endermite 2 | off while sneaking or near an anti-field |
| Keep Inventory (40) | keep items on death | Endermite 1 | Shulker cell + emerald block, 45% |
| Mob Sight (16) | see mobs through walls, 32 blocks | Bat 3, Elder Guardian 3, Guardian 3, Warden 5 | |
| No Hunger (30) | hunger bar refills to a floor every 4 s | Wolf 1 | virus parent of Hunger |
| Photosynthesis (40, mutation) | sunlight refills hunger | Mooshroom 3 | requires Eat Grass + Thorns; Mooshroom cell + mushroom stem, 70% |
| Reaching (50) | reach +25% | Iron Golem 3 | |
| Shoot Fireballs (24) | right-click a blaze rod to throw a fireball | Blaze 3, Ghast 4 | |
| Slimy Death (60) | death is cancelled; support slimes (wearing your head) spawn to defend you | Slime 2, Support Slime 1 | |
| Teleport (24) | key: teleport up to 10 blocks, stops at walls | Enderman 3, Phantom 3 | Enderman cell + ender pearl, 45%; prerequisite of Flight |
| Wall Climbing (40) | climb walls, cling to ceilings | Cave Spider 2, Spider 2 | |
| XP Magnet (30) | pull xp orbs from 8 blocks | Endermite 3 | anti-field target |

### 1.4 Mob-only genes (cannot go into players)

Livestock and farm improvement, not combat.

| Gene | Effect | Mobs (weight) | Notes |
|---|---|---|---|
| Bountiful (30) | holder's death loot counts as Looting +1 | Mooshroom 2 | |
| Bountiful II (40) | Looting +2 | Sniffer 3 | requires Bountiful |
| Experienced (50) | +100% xp on death | Villager 2, Sniffer 4 | |
| Fertile | breeding makes extra children (each carrying parent adds one; both = 3) | Rabbit 3 | |
| Frenzied | attacks everything nearby, 3 damage even if it normally cannot attack | Zoglin 3 | incompatible with Placid |
| Placid | cannot attack (most target goals are cancelled) | Cow 3 | incompatible with Frenzied |

### 1.5 Negative genes: natural sources

| Gene | Effect | Mobs (weight) |
|---|---|---|
| Explosive Exit (20) | explodes on death (no block damage); a player needs 5 gunpowder to trigger it | Creeper 3 |
| Infested (10) | Infested effect | Silverfish 4 |
| Levitation | Levitation effect | Shulker 4 |
| Oozing (10) | Oozing effect | Slime 5 |
| Weaving (10) | Weaving effect (cobwebs on death) | Spider 4 |
| Wind Charged (10) | Wind Charged effect | Breeze 4 |
| Cringe (20) | player chat is uwu-ified; optional language change | the Player itself (a joke gene) |

### 1.6 Negative genes: virus recipes (Potion of Viral Agents + a helix, in the incubator)

| Gene | Effect | Made from the helix of |
|---|---|---|
| Blindness | Blindness | Night Vision |
| Cursed | Cursed (bad luck) | Luck |
| Flambe (1) | permanently on fire | Fire Proof |
| Hunger | Hunger | No Hunger |
| Mining Fatigue | Mining Fatigue | Haste |
| Nausea | Nausea | Milky, Meaty or Lay Egg |
| Poison / Poison IV | Poison | Poison Immunity / Wither Hit |
| Slowness / IV / VI | Slowness | Speed / Speed II / Speed IV |
| Weakness | Weakness | Strength |
| Wither | Wither | Wither Proof |

Rule: a negative gene is the evil twin of a good gene and is made from that good gene's helix.

### 1.7 Plague genes (special viruses, same Viral Agents base)

| Gene | Effect | Made from |
|---|---|---|
| Green Death | kills creepers | Scare Creepers |
| Un-Undeath | kills the undead | Scare Skeletons or Scare Zombies |
| Gray Death | kills ageable mobs | Resistance |
| White Death | kills monsters | Dragon Breath |
| Black Death | kills everything | every enabled negative gene together |

### 1.8 Mutation genes (stronger tier-2 versions)

Cannot be drawn from a mob. Made with a cell of a named mob plus a valuable item, in the low-temperature incubator,
with a failure chance. Plasmid cost is 40 to 50 points.

| Gene | Needs | Recipe (mob cell + item, success chance) |
|---|---|---|
| Claws II | Claws | Polar Bear + diamond sword, 75% |
| Efficiency IV | Efficiency | Silverfish + netherite pickaxe, 25% |
| Haste II | Haste | Rabbit + netherite pickaxe, 35% |
| Speed II | Speed | Rabbit + diamond boots, 50% |
| Speed IV | Speed, Speed II | Ocelot + netherite boots, 50% |
| Resistance II | Resistance | Shulker + netherite chestplate, 50% |
| Strength II | Strength | Polar Bear + netherite sword, 50% |
| Regeneration IV | Regeneration | Ender Dragon + enchanted golden apple, 35% |
| More Hearts II | More Hearts | Enderman + golden apple, 25% |
| Meaty II | Meaty | Pig + blaze powder, 75% (occasionally drops cooked porkchops) |
| Flight | Teleport, Step Assist, No Fall Damage | Ender Dragon + elytra, 55% |
| Scare Spiders | none | Spider + fermented spider eye, 50% |
| Scare Zombies | none | Zombie + fermented spider eye, 50% |

Totals: about 55 base genes, 13 mutation genes, 6 mob-only genes, about 16 negative genes, 5 plagues. Cure items: the
Anti-Plasmid (removes one named gene) and the Panacea potion (removes every negative gene and harmful effect; made
from Viral Agents + a Regeneration helix).

---

## 2. Pipeline and machines

Whole chain: scrape (or bleed) a mob, make a Cell, extract a DNA Helix (gene unknown), decrypt it (weighted random
draw from the species list), pour helices into a plasmid until it is full, put plasmids into a decontaminated
syringe, inject the blood back into its owner.

| Stage | What it does | Fun | Tedious |
|---|---|---|---|
| Scraper | tool: scrape Organic Matter off a mob (hurts it unless enchanted soft); sneak-use scrapes yourself | cheap and hands-on, no machine | |
| Cell Analyzer | Organic Matter into a Cell of that species | | one more single-input box |
| DNA Extractor | Cell into a Helix (gene unknown) | | same |
| DNA Decryptor | reveals the gene by a weighted draw | the gamble, a pig is mostly filler | rolled again and again to find one gene; filler feels like a loss |
| Plasmid Infuser | first helix fixes the gene; then matching helices give 2 points, filler gives 1; stops at the gene's cost | filler is not wasted | one species, one gene at a time; 50 to 60 point genes need dozens of helices |
| Incubator | brewing-stand style: 3 slots plus one shared ingredient; makes substrate, cell-growth potions, duplicates cells, makes viruses | duplicating a cell means one good kill lasts forever | |
| Advanced Incubator | low-temperature mode for mutation cells: slow, with a failure chance; overclockers raise it, chorus fruit lowers it; the GUI shows the exact chance on hover | open odds; real items spent as a gamble | failures eat rare items |
| Plasmid Injector | loads finished plasmids into a clean syringe | | |
| Blood Purifier | cleans a used syringe (blood is always contaminated) | | a pure tax step |
| Syringe / Metal Syringe | takes blood from you or a target; only the blood's owner can accept the genes; the target glows through walls while you hold their blood | blood is personal | |
| Anti-Plasmid | craft any filled plasmid with a blank anti-plasmid to make its remover | simple, readable undo for any gene | |
| Anti-Field Block / Orb | a block (radius 25) or an inventory toggle that switches off the magnet genes | a base-design tool against nuisance genes | only affects two genes |
| Gene Checker | tool: lists a target's genes and the species list with weights | removes guessing | |
| Overclocker | up to 8 per machine, +100% speed and +25% power each | clear speed-for-power trade | |
| Coal Generator | burns fuel into FE | | |

Shape of the experience: a row of small single-input boxes. The interesting parts are the weighted roll, the
duplicate-cell trick and the mutation gamble; the rest is plumbing that recipe viewers soften.

---

## 3. Ideas worth adopting

Section numbers are those of DESIGN-v2.md chapter 12.

### 3.1 New genes for our host and mecha context

Genes live in bodies (12.7), so these are body genes. L = load, S = stability cost, slotting into the 12.8 table.

| Proposed gene | Source mob | Proposed effect (mecha; hosts at 70%) | L | S | Slot |
|---|---|---|---|---|---|
| Fleece | sheep, goat | the frame can be sheared for wool every 3 min | 1 | 5 | 12.8 metabolism, `produce` |
| Egg Layer | chicken, parrot | lays an egg every few minutes; free creature-egg/food source | 1 | 5 | 12.8 metabolism, `produce` |
| Brood | rabbit, OreSpawn Aerbunny | mob-host gene: extra offspring when breeding (host farming) | 1 | 5 | 12.8 new "farm" category |
| Placid | cow | mob host only: will never attack; excludes Frenzy | 1 | 0 | 12.8 farm |
| Frenzy | zoglin | mob host only: attacks everything within 8; excludes Placid; good for arena hosts | 2 | 10 | 12.8 farm |
| Bountiful | mooshroom, sniffer | mob host only: its death counts as Looting +1 (a loot-farm host) | 2 | 10 | 12.8 farm |
| Learned Mind | villager, sniffer | +100% xp from its kills or death; the Cleric NPC works better near it | 2 | 10 | 12.8 farm/utility |
| Bleed Claws | polar bear | unarmed hits cause Bleeding (1 dmg/s for 4 s); pairs with a mecha claw arm | 2 | 10 | 12.8 offence, `on_hit_effect` |
| Frost Touch | husk, snow golem | hits chill with brief Freezing | 2 | 10 | 12.8 offence, `on_hit_effect` |
| Axe Instinct | vindicator | +25% axe damage | 1 | 5 | 12.8 offence |
| Item Magnet | allay, endermite | pull items in 8 blocks to the frame; a mecha version feeds a pickup bay | 2 | 10 | 12.8 utility |
| XP Magnet | endermite | pull xp orbs | 1 | 5 | 12.8 utility |
| Mob Sight | warden, guardian, bat | outline mobs through walls to 32; cheaper cousin of Echolocation, conflicts with it | 2 | 10 | 12.8 senses |
| Long Reach | iron golem | +25% interaction reach; synergy with Golem Frame | 2 | 10 | 12.8 utility |
| Fireball | blaze, ghast, OreSpawn Kyuubi | secondary: lob a fireball; a synergy with Blaze Core ("Furnace Gunner") | 3 | 15 | 12.8 offence, `ranged` |
| Endless Quiver | skeleton, stray | no ammo use for bows and crossbows | 2 | 10 | 12.8 offence |
| Dragon Shield (legendary) | ender dragon | damage is absorbed by a held Crystal item, repaired with ghast tears; a boss-fight hit sponge | 5 | 25 | 12.8 legendary |
| Slime Guard | slime, support slime | the first lethal hit is cancelled and two slime allies wearing your head appear (Necromancy minions) | 4 | 20 | 12.8 defence, `last_stand` + summon |
| Steady Step | horse, llama, polar bear | step height +1 | 1 | 5 | 12.8 movement, attribute |
| Giant Fist | OreSpawn Giant Robot (oil) | knockback +2 on melee; slower | 3 | 15 | 12.8 offence, attribute |

OreSpawn sources that fit naturally: Aerbunny (brood, jump), Kyuubi (fireball), Giant Robot (fist), Dodo-style birds
(egg layer), plus the creatures already named in 12.8. All are optional rows, so a pack without OreSpawn loses nothing.

### 3.2 Mechanics worth borrowing

1. **Negative genes as the evil twin of a good gene** (12.6). Add a catalyst, Viral Agents, that turns any sequenced
   gene into its negative (Speed into Slowness, Strength into Weakness). They suit curses, hosts used as traps, and
   gifts to rivals. Slots into 12.5 as a catalyst and 12.6 as a second mutation table.
2. **Gene cures** (12.7). We only have Gene Wash for a body. Add a targeted anti-serum (a spare serum of a gene crafted
   with a blank vial), and one single-use Panacea that clears every negative gene and mutation. Slots beside Gene Wash
   in 12.7.
3. **Tier-2 mutations** (12.5, 12.8). A "II" column: strong versions of common genes (Equine Gait II, Golem Frame II)
   that cannot be sequenced and are crafted from a gene plus a valuable item, with a visible success chance in the
   Splicer's catalyst slot.
4. **Prerequisite genes** (12.4, 12.8). Advanced genes require other genes already in the Archive (Flight needs
   Ender Blink, Rabbit Spring and Feather Bones; a Legendary needs two lesser ones). Shown as a lock icon with a
   tooltip on the Archive screen. Free progression with no new content.
5. **Open odds on hover** (12.5). Show exact success and failure chances in the Splicer, including catalyst changes.
6. **Filler is still progress** (12.4). Their filler helix adds partial points. Our Readout already behaves that way:
   never let a read give nothing.
7. **Hard exclusion flag** (12.10). A "forbids" list on a gene (Placid vs Frenzy) beside our stability conflicts, for
   pairs that must never coexist.
8. **Quiet Zone** (12.10). A control block, and a hand-held Orb for travelling, that switches off one gene category in
   a radius (magnets, Self-Destruct, Blink). Lets a base sit beside a pen of mutated hosts.
9. **Owner-locked serums** (12.2, 12.7). Their blood only works for its owner. Our rejection rules cover mob hosts; an
   optional owner lock for self-edits keeps multiplayer fair.
10. **A hand lens** (12.4). A cheap Gene Checker trinket (or a Librarian ability) listing a mob's gene names, not the
    percentages, so planning needs no wiki.
11. **A duplicate-the-good-one step** (12.5). They let any successful rare cell be duplicated. A Serum duplicator
    (substrate in the Splicer) would soften the cost of a rare mutation result.

### 3.3 Things to avoid

- **A draw that can waste a long chain.** Their decryptor can burn machine time to reveal filler. Never make a stage
  that can return nothing after real effort.
- **Many one-input boxes in a row.** Five blocks for one gene is a lot; keep our centrifuge, sequencer, splicer count.
- **Decontamination as a pure tax.** A purifier between every syringe use is not a decision; our freshness and
  fridge design is better.
- **Heavy plasmid costs (50 to 60 points).** Dozens of identical helices feel like farming; keep cost in
  minutes of play, not item counts.
- **Joke genes that touch chat, language or narration.** Keep chat and locale alone; a narrator gene is fine only as
  an opt-in accessibility option.
- **Plague genes that kill classes of mobs.** On a server this is griefing; if added, keep them local, short, and
  off by default.
- **Genes that exist only as a recipe** (Scare Spiders and Scare Zombies have no natural source). Give every gene a
  creature that holds it.
- **Separate genes per tier** (Speed, Speed II, Speed IV each with their own plasmid chain). Prefer our potency bands
  (Draft, Working, Complete) plus one "II" tier.

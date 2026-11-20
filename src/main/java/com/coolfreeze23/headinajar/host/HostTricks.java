package com.coolfreeze23.headinajar.host;

import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Refuser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.entity.animal.horse.Mule;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The tricks of 0.6.3's bodies (every vanilla host does something): the ten blanks' Abilities and five Secondaries,
 * and the server trackers a trick leaves running (herded animals, fleeing creepers, howled wolves, treasure streams,
 * spit in the air, a horse held up on its hind legs, a frog in mid-jump, a Rooting scan for a game without the cue).
 * Fired from {@link HostAbilities#fire} for the types it does not know itself.
 * <p>
 * Cost: every tracker is keyed by entity id in its own level, ticked only while it holds something, and dropped with
 * the level; nothing is scanned that a trick did not start, and nothing reads a chunk that is not already loaded.
 */
public final class HostTricks {
    /** {@link #fire}'s answer for a type that is not one of these tricks (said with the host's name). */
    public static final String UNKNOWN = "host.no_ability";

    /** Paths Moo has asked for since the game started (for its cost test: one per herded animal each 10 ticks at most). */
    public static int herdPaths;

    /** How far up the look Spit is aimed, per block of distance, to land its arc on what the pilot looks at. */
    private static final double SPIT_LIFT = 0.15;
    /** What Rooting finds when the pilot's game has no outlines: at most this many, each shown with a puff. */
    private static final int ROOT_CAP = 128;

    private HostTricks() {
    }

    /** Puts the tricks' event handlers on the game's event bus (from the mod's constructor). */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(HostTricks::spitLands);
        NeoForge.EVENT_BUS.addListener(HostTricks::retarget);
        NeoForge.EVENT_BUS.addListener(HostTricks::startTracking);
        NeoForge.EVENT_BUS.addListener(HostTricks::unload);
    }

    // --- what each level has running -------------------------------------------------------------------------

    private record Follow(UUID pilot, long until) {
    }

    private record Flee(int host, UUID pilot, Vec3 from, long until) {
    }

    private record Stream(Vec3 to, long until) {
    }

    private record Spit(int host, UUID pilot, float damage, double knockback, long until) {
    }

    private static final class RootScan {
        final UUID pilot;
        final BlockPos centre;
        final int radius;
        int step, found;

        RootScan(UUID pilot, BlockPos centre, int radius) {
            this.pilot = pilot;
            this.centre = centre;
            this.radius = radius;
        }
    }

    private static final class Tracks {
        final Map<Integer, Follow> herded = new HashMap<>();
        final Map<Integer, Flee> hissed = new HashMap<>();
        final Map<Integer, Follow> howled = new HashMap<>();
        final Map<Integer, Stream> streams = new HashMap<>();
        final Map<Integer, Spit> spits = new HashMap<>();
        /** A horse stood up by hand (its own rear refused a ridden body): when it comes down. */
        final Map<Integer, Long> rearing = new HashMap<>();
        /** A frog in its Long Jump: when it left the ground. */
        final Map<Integer, Long> leaps = new HashMap<>();
        /** A warming strider: where it stood a second ago (it heals only standing still). */
        final Map<Integer, Vec3> warmSpots = new HashMap<>();
        final List<RootScan> scans = new ArrayList<>();

        boolean idle() {
            return herded.isEmpty() && hissed.isEmpty() && howled.isEmpty() && streams.isEmpty() && spits.isEmpty() && rearing.isEmpty() && scans.isEmpty();
        }
    }

    private static final Map<ResourceKey<Level>, Tracks> TRACKS = new HashMap<>();
    /** What the last server-side Rooting scan found for each pilot (for tests; a game with the cue counts its own). */
    private static final Map<UUID, Integer> ROOTED = new HashMap<>();

    private static Tracks tracks(ServerLevel level) {
        return TRACKS.computeIfAbsent(level.dimension(), k -> new Tracks());
    }

    private static Tracks existing(Level level) {
        return level instanceof ServerLevel ? TRACKS.get(level.dimension()) : null;
    }

    private static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) TRACKS.remove(level.dimension());
    }

    // --- firing ----------------------------------------------------------------------------------------------

    /**
     * Fires a trick: null when it fired, else the refusal reason (under {@code refusal.headinajar.}), {@code ""} for a
     * refusal already said (a type that is not a trick: "no trick for that", with the body's name).
     */
    public static String fire(ServerPlayer pilot, Mob host, HostDefinition.Ability a) {
        ServerLevel level = (ServerLevel) host.level();
        long time = level.getGameTime();
        HostData data = Hosts.data(host);
        return switch (a.type()) {
            case "herd" -> herd(pilot, host, a, level, time);
            case "rooting" -> {
                int radius = (int) a.param("radius", 16);
                if (!TrickNet.cueRooting(pilot, (int) a.param("duration", 200), radius)) {
                    tracks(level).scans.add(new RootScan(pilot.getUUID(), host.blockPosition(), radius));
                }
                sound(level, host, SoundEvents.PIG_AMBIENT, 1, 0.9f);
                yield null;
            }
            case "shed" -> shed(host, a, level);
            case "spit" -> spit(pilot, host, a, level, time);
            case "bell" -> {
                double r = a.param("radius", 32);
                int glow = (int) a.param("duration", 200);
                // what a village bell does: raiders show through walls, villagers hear it and go in
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, host.getBoundingBox().inflate(r),
                        e -> e != host && e.isAlive() && e.distanceToSqr(host) <= r * r)) {
                    if (e.getType().is(EntityTypeTags.RAIDERS)) e.addEffect(new MobEffectInstance(MobEffects.GLOWING, glow));
                    else if (!Hosts.isHost(e)) e.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, time);
                }
                sound(level, host, SoundEvents.BELL_BLOCK, 2, 1);
                yield null;
            }
            case "barter" -> {
                if (data.barterDue != 0) yield "host.barter_busy";
                if (!spend(pilot, Items.GOLD_INGOT)) yield "host.no_gold";
                ItemStack held = host.getOffhandItem();
                if (!held.isEmpty()) host.spawnAtLocation(held.copy());
                // the ingot is admired in the off hand (the vanilla pose), and never dropped from it
                host.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.GOLD_INGOT));
                host.setDropChance(EquipmentSlot.OFFHAND, 0);
                data.barterDue = time + (long) a.param("delay", 120);
                sound(level, host, SoundEvents.PIGLIN_ADMIRING_ITEM, 1, 1);
                yield null;
            }
            case "warm_up" -> {
                if (data.warming) {
                    data.warming = false;
                    tracks(level).warmSpots.remove(host.getId());
                    pilot.displayClientMessage(Component.translatable("message.headinajar.host.warm_up_off"), true);
                    sound(level, host, SoundEvents.STRIDER_AMBIENT, 1, 1);
                } else {
                    if (!onLava(host)) yield "host.warm_up_lava";
                    data.warming = true;
                    pilot.displayClientMessage(Component.translatable("message.headinajar.host.warm_up_on"), true);
                    sound(level, host, SoundEvents.STRIDER_HAPPY, 1, 1);
                }
                TrickNet.send(host);
                yield null;
            }
            case "withdraw" -> {
                data.withdrawnUntil = time + (long) a.param("duration", 100);
                TrickNet.send(host);
                sound(level, host, SoundEvents.ARMOR_EQUIP_TURTLE.value(), 1, 0.8f);
                yield null;
            }
            case "leap" -> {
                bound(host, pilot, a.param("speed", 1));
                host.setPose(Pose.LONG_JUMPING);
                data.leaping = true;
                tracks(level).leaps.put(host.getId(), time);
                TrickNet.send(host);
                sound(level, host, SoundEvents.FROG_LONG_JUMP, 1, 1);
                yield null;
            }
            case "hiss" -> hiss(pilot, host, a, level, time);
            case "dash" -> {
                bound(host, pilot, a.param("speed", 1.4));
                if (host instanceof Camel camel) camel.setDashing(true);
                sound(level, host, SoundEvents.CAMEL_DASH, 1, 1);
                yield null;
            }
            case "rear" -> rear(pilot, host, a, level, time);
            case "mend" -> {
                if (host.getHealth() >= host.getMaxHealth()) yield "host.mend_full";
                if (!spend(pilot, Items.IRON_INGOT)) yield "host.no_iron";
                host.heal((float) a.param("heal", 25));
                sound(level, host, SoundEvents.IRON_GOLEM_REPAIR, 1, 1);
                yield null;
            }
            case "howl" -> howl(pilot, host, a, level, time);
            case "treasure" -> {
                // the Nether has no sea and no wrecks; nothing is searched there
                Vec3 to = level.dimension() == Level.NETHER ? null : treasure(level, host.blockPosition(), (int) a.param("chunks", 4));
                if (to == null) yield "host.no_treasure";
                tracks(level).streams.put(host.getId(), new Stream(to, time + (long) a.param("duration", 200)));
                pilot.displayClientMessage(Component.translatable("message.headinajar.host.treasure"), true);
                sound(level, host, SoundEvents.DOLPHIN_PLAY, 1.5f, 1);
                yield null;
            }
            default -> {
                // a modded blank (or a type nobody knows): the line names the body
                Refusals.deny(pilot, Refuser.at(host), UNKNOWN, host.getDisplayName());
                yield HostAbilities.SAID;
            }
        };
    }

    private static void sound(ServerLevel level, Entity at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundSource.NEUTRAL, volume, pitch);
    }

    /** Takes one of {@code item} from the pilot's pockets (a creative player keeps it); false if there is none. */
    private static boolean spend(ServerPlayer pilot, Item item) {
        var inv = pilot.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) {
                if (!pilot.hasInfiniteMaterials()) {
                    s.shrink(1);
                    inv.setChanged();
                }
                return true;
            }
        }
        return false;
    }

    /** The pounce without the blow: up and along the pilot's look (Long Jump, Dash). */
    private static void bound(Mob host, ServerPlayer pilot, double speed) {
        Vec3 look = pilot.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1e-6) look = Vec3.directionFromRotation(0, host.getYRot());
        Vec3 push = look.normalize().scale(speed);
        Vec3 v = new Vec3(push.x, 0.5, push.z);
        // a ridden host is moved by its pilot's client, which the server's velocity never reaches: the push goes there.
        // That client loses more of the run on the take-off (it counts the first ticks on the ground's grip): measured
        // in the game, the same push landed a frog 3.4 blocks out and a camel 4.2, where unridden they land 6 and 8. The
        // run sent there is raised by RIDDEN_LIFT for a take-off from the ground.
        double lift = host.onGround() ? RIDDEN_LIFT : 1;
        if (!(Hosts.pilot(host) instanceof ServerPlayer rider)
                || !com.coolfreeze23.headinajar.mecha.HopperNet.push(rider, host, new Vec3(v.x * lift, v.y, v.z * lift))) {
            host.setDeltaMovement(v);
            host.hurtMarked = true;
        }
    }

    /** How much more run a ridden leap needs from the ground to land where the unridden one does (measured, above). */
    private static final double RIDDEN_LIFT = 1.8;

    private static String herd(ServerPlayer pilot, Mob host, HostDefinition.Ability a, ServerLevel level, long time) {
        double r = a.param("radius", 16);
        ItemStack wheat = new ItemStack(Items.WHEAT);
        List<Animal> eaters = level.getEntitiesOfClass(Animal.class, host.getBoundingBox().inflate(r),
                e -> e != host && e.isAlive() && !Hosts.isHost(e) && e.distanceToSqr(host) <= r * r && e.isFood(wheat));
        if (eaters.isEmpty()) return "host.no_herd";
        eaters.sort(Comparator.comparingDouble(e -> e.distanceToSqr(host)));
        int cap = (int) a.param("cap", 24);
        if (eaters.size() > cap) eaters = eaters.subList(0, cap);
        Tracks t = tracks(level);
        long until = time + (long) a.param("duration", 200);
        for (Animal e : eaters) {
            t.herded.put(e.getId(), new Follow(pilot.getUUID(), until));
            lure(e, pilot);
        }
        pilot.displayClientMessage(Component.translatable("message.headinajar.host.herded", eaters.size()), true);
        sound(level, host, SoundEvents.COW_AMBIENT, 2, 0.8f);
        return null;
    }

    /** One step of Moo's lure: the animal walks after the pilot, by its goals' navigation or its brain's walk target. */
    private static void lure(Mob animal, Player pilot) {
        if (animal.distanceToSqr(pilot) < 2.5 * 2.5) return;
        herdPaths++;
        if (animal.getBrain().checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) {
            animal.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pilot, 1.1f, 2));
        } else {
            animal.getNavigation().moveTo(pilot, 1.1);
        }
    }

    private static String shed(Mob host, HostDefinition.Ability a, ServerLevel level) {
        // the fleece stays on: a sheared host never grows it back (eating grass is its own mind's work)
        if (host instanceof Sheep sheep && sheep.isSheared()) return "host.shed_bare";
        DyeColor colour = host instanceof Sheep sheep ? sheep.getColor() : DyeColor.WHITE;
        Item wool = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(colour.getName() + "_wool"));
        int min = (int) a.param("min", 1), max = Math.max(min, (int) a.param("max", 3));
        int n = min + level.random.nextInt(max - min + 1);
        for (int i = 0; i < n; i++) {
            ItemEntity drop = host.spawnAtLocation(new ItemStack(wool), 1);
            if (drop != null) {
                drop.setDeltaMovement(drop.getDeltaMovement().add((level.random.nextFloat() - level.random.nextFloat()) * 0.1F,
                        level.random.nextFloat() * 0.05F, (level.random.nextFloat() - level.random.nextFloat()) * 0.1F));
            }
        }
        sound(level, host, SoundEvents.SHEEP_SHEAR, 1, 1);
        return null;
    }

    private static String spit(ServerPlayer pilot, Mob host, HostDefinition.Ability a, ServerLevel level, long time) {
        double range = a.param("range", 12);
        Vec3 eye = pilot.getEyePosition(), look = pilot.getLookAngle();
        Vec3 flat = look.multiply(1, 0, 1).lengthSqr() < 1e-6 ? Vec3.directionFromRotation(0, host.getYRot()) : look.multiply(1, 0, 1).normalize();
        // from the mouth, as a llama spits
        Vec3 from = new Vec3(host.getX(), host.getEyeY() - 0.1, host.getZ()).add(flat.scale((host.getBbWidth() + 1) * 0.5));
        Vec3 end = eye.add(look.scale(range));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(pilot, eye, end, new AABB(eye, end).inflate(1),
                e -> e != pilot && e != host && e instanceof LivingEntity && e.isAlive(), range * range);
        Vec3 aim;
        if (hit != null) aim = hit.getEntity().getBoundingBox().getCenter();
        else {
            var block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, pilot));
            aim = block.getType() == HitResult.Type.BLOCK ? block.getLocation() : end;
        }
        Vec3 d = aim.subtract(from);
        LlamaSpit spit = new LlamaSpit(EntityType.LLAMA_SPIT, level);
        spit.setOwner(host);
        spit.setPos(from.x, from.y, from.z);
        spit.shoot(d.x, d.y + Math.sqrt(d.x * d.x + d.z * d.z) * SPIT_LIFT, d.z, 1.5f, 1f);
        level.addFreshEntity(spit);
        tracks(level).spits.put(spit.getId(), new Spit(host.getId(), pilot.getUUID(), (float) a.param("damage", 1), a.param("knockback", 0.6), time + 100));
        sound(level, host, SoundEvents.LLAMA_SPIT, 1, 1 + (level.random.nextFloat() - level.random.nextFloat()) * 0.2f);
        return null;
    }

    /** A host's spit lands on someone: its own blow (1, credited to the pilot, with a little push), not vanilla's. */
    private static void spitLands(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof LlamaSpit spit) || !(event.getRayTraceResult() instanceof EntityHitResult hit)) return;
        Tracks t = existing(spit.level());
        Spit s = t == null ? null : t.spits.get(spit.getId());
        if (s == null) return;
        ServerLevel level = (ServerLevel) spit.level();
        Entity host = level.getEntity(s.host());
        Player pilot = level.getPlayerByUUID(s.pilot());
        Entity victim = hit.getEntity();
        event.setCanceled(true);
        if (victim == host || victim == pilot) return;
        if (host != null && pilot != null) {
            if (victim.hurt(HostEvents.byPilot(host, pilot, DamageTypes.SPIT), s.damage()) && victim instanceof LivingEntity living) {
                Vec3 v = spit.getDeltaMovement().multiply(1, 0, 1);
                if (v.lengthSqr() > 1e-6) living.knockback(s.knockback(), -v.x, -v.z);
            }
        }
        t.spits.remove(spit.getId());
        spit.discard();
    }

    private static String hiss(ServerPlayer pilot, Mob host, HostDefinition.Ability a, ServerLevel level, long time) {
        double r = a.param("radius", 8);
        List<Creeper> creepers = level.getEntitiesOfClass(Creeper.class, host.getBoundingBox().inflate(r),
                c -> c.isAlive() && !Hosts.isHost(c) && c.distanceToSqr(host) <= r * r);
        if (creepers.isEmpty()) return "host.no_creepers";
        Tracks t = tracks(level);
        long until = time + (long) a.param("duration", 100);
        for (Creeper c : creepers) {
            Flee f = new Flee(host.getId(), pilot.getUUID(), host.position(), until);
            t.hissed.put(c.getId(), f);
            scatter(c, f.from());
        }
        sound(level, host, SoundEvents.CAT_HISS, 1.5f, 1);
        return null;
    }

    /** One step of a creeper's flight from a hiss: it forgets its target, its fuse winds back, it walks away. */
    private static void scatter(Creeper c, Vec3 from) {
        c.setTarget(null);
        ((com.coolfreeze23.headinajar.mixin.CreeperSwellAccess) c).headinajar$setSwell(0);
        c.setSwellDir(-1);
        Vec3 away = DefaultRandomPos.getPosAway(c, 16, 7, from);
        if (away != null) c.getNavigation().moveTo(away.x, away.y, away.z, 1.25);
    }

    /** A creeper fleeing a hiss does not turn on the one who hissed (or the body) until it is over. */
    private static void retarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target == null) return;
        Tracks t = existing(event.getEntity().level());
        if (t == null || t.hissed.isEmpty()) return;
        Flee f = t.hissed.get(event.getEntity().getId());
        if (f == null || event.getEntity().level().getGameTime() >= f.until()) return;
        if (target.getUUID().equals(f.pilot()) || target.getId() == f.host()) event.setCanceled(true);
    }

    private static String rear(ServerPlayer pilot, Mob host, HostDefinition.Ability a, ServerLevel level, long time) {
        double r = a.param("radius", 3), knock = a.param("knockback", 1.2);
        double reach = r + host.getBbWidth() / 2;
        boolean pvp = level.getServer().isPvpAllowed();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, host.getBoundingBox().inflate(r),
                e -> e != host && e != pilot && e.isAlive() && e.getVehicle() != host && e.distanceToSqr(host) <= reach * reach)) {
            // a player only where players may hurt each other, and only one the pilot may hurt (teams)
            if (e instanceof Player other && (!pvp || !pilot.canHarmPlayer(other))) continue;
            e.knockback(knock, host.getX() - e.getX(), host.getZ() - e.getZ());
            e.hurtMarked = true;
        }
        if (host instanceof AbstractHorse horse) {
            horse.standIfPossible();
            if (!horse.isStanding()) {
                // its own rear refused: stood up by hand, and let down in a second
                horse.setStanding(true);
                tracks(level).rearing.put(host.getId(), time + 20);
            }
        }
        SoundEvent angry = host instanceof Donkey ? SoundEvents.DONKEY_ANGRY : host instanceof Mule ? SoundEvents.MULE_ANGRY : SoundEvents.HORSE_ANGRY;
        sound(level, host, angry, 1, 1);
        return null;
    }

    private static String howl(ServerPlayer pilot, Mob host, HostDefinition.Ability a, ServerLevel level, long time) {
        double r = a.param("radius", 16);
        List<Wolf> wolves = level.getEntitiesOfClass(Wolf.class, host.getBoundingBox().inflate(r),
                w -> w != host && w.isAlive() && w.isTame() && w.isOwnedBy(pilot) && !Hosts.isHost(w) && w.distanceToSqr(host) <= r * r);
        if (wolves.isEmpty()) return "host.no_wolves";
        Tracks t = tracks(level);
        long until = time + (long) a.param("duration", 1200);
        for (Wolf w : wolves) {
            w.setOrderedToSit(false);
            w.setInSittingPose(false);
            t.howled.put(w.getId(), new Follow(pilot.getUUID(), until));
            if (w.distanceToSqr(pilot) > 16) w.getNavigation().moveTo(pilot, 1.2);
        }
        pilot.displayClientMessage(Component.translatable("message.headinajar.host.howled", wolves.size()), true);
        sound(level, host, SoundEvents.WOLF_HOWL, 2, 1);
        return null;
    }

    /**
     * The nearest shipwreck, ocean ruin or buried treasure whose start lies in a loaded chunk within {@code chunks} of
     * {@code pos}, or null. Only chunks already loaded are read (never one generated or loaded for this).
     */
    public static Vec3 treasure(ServerLevel level, BlockPos pos, int chunks) {
        var structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        Vec3 best = null;
        double bestD = Double.MAX_VALUE;
        Vec3 at = Vec3.atCenterOf(pos);
        for (int dx = -chunks; dx <= chunks; dx++) {
            for (int dz = -chunks; dz <= chunks; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) continue;
                for (Map.Entry<Structure, StructureStart> e : chunk.getAllStarts().entrySet()) {
                    if (!e.getValue().isValid()) continue;
                    Holder<Structure> holder = structures.wrapAsHolder(e.getKey());
                    if (!holder.is(StructureTags.DOLPHIN_LOCATED) && !holder.is(BuiltinStructures.BURIED_TREASURE)) continue;
                    Vec3 c = Vec3.atCenterOf(e.getValue().getBoundingBox().getCenter());
                    double d = c.distanceToSqr(at);
                    if (d < bestD) {
                        bestD = d;
                        best = c;
                    }
                }
            }
        }
        return best;
    }

    // --- each tick -------------------------------------------------------------------------------------------

    /** Each tick of each host, server side (after its decay and sync): barters due, the end of a Withdraw or a Long Jump, Warm Up's heal. */
    static void tick(Mob host, HostData data, HostDefinition def, Player pilot, long time) {
        if (data.barterDue != 0 && time >= data.barterDue) payBarter(host, data);
        if (data.withdrawnUntil != 0 && time >= data.withdrawnUntil) {
            data.withdrawnUntil = 0;
            TrickNet.send(host);
        }
        if (data.leaping) land(host, data, time);
        if (data.warming && time % 20 == 0) warm(host, def);
    }

    private static void payBarter(Mob host, HostData data) {
        data.barterDue = 0;
        if (host.getOffhandItem().is(Items.GOLD_INGOT)) host.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        host.setDropChance(EquipmentSlot.OFFHAND, 0.085f);
        ServerLevel level = (ServerLevel) host.level();
        var table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.PIGLIN_BARTERING);
        for (ItemStack stack : table.getRandomItems(new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, host).create(LootContextParamSets.PIGLIN_BARTER))) {
            host.spawnAtLocation(stack);
        }
        sound(level, host, SoundEvents.PIGLIN_CELEBRATE, 1, 1);
    }

    private static void land(Mob host, HostData data, long time) {
        Tracks t = existing(host.level());
        Long start = t == null ? null : t.leaps.get(host.getId());
        // a moment in the air first: the press lands between ticks, while the body is still on the ground
        if (start != null && time - start < 3) return;
        if (start != null && time - start < 100 && !host.onGround() && !host.isInWater() && !host.isInLava()) return;
        data.leaping = false;
        if (t != null) t.leaps.remove(host.getId());
        if (host.getPose() == Pose.LONG_JUMPING) host.setPose(Pose.STANDING);
        TrickNet.send(host);
    }

    private static boolean onLava(Mob host) {
        BlockPos at = host.blockPosition();
        return host.level().getFluidState(at).is(FluidTags.LAVA) || host.level().getFluidState(at.below()).is(FluidTags.LAVA);
    }

    private static void warm(Mob host, HostDefinition def) {
        Tracks t = tracks((ServerLevel) host.level());
        Vec3 was = t.warmSpots.put(host.getId(), host.position());
        if (was == null || was.distanceToSqr(host.position()) > 0.05 || !onLava(host) || host.getHealth() >= host.getMaxHealth()) return;
        double heal = def != null && def.primary().isPresent() && def.primary().get().type().equals("warm_up") ? def.primary().get().param("heal", 1) : 1;
        host.heal((float) heal);
        ((ServerLevel) host.level()).sendParticles(ParticleTypes.FLAME, host.getX(), host.getY() + 0.2, host.getZ(), 3, 0.3, 0.05, 0.3, 0.01);
    }

    /** Damage coming to a host (before the traits' cancels): Withdraw's share. */
    static void hurt(LivingIncomingDamageEvent event, Mob host, HostData data) {
        if (host.level().isClientSide || !data.withdrawn(host.level().getGameTime())) return;
        HostDefinition def = Hosts.definition(host);
        double factor = def != null && def.primary().isPresent() && def.primary().get().type().equals("withdraw") ? def.primary().get().param("factor", 0.2) : 0.2;
        event.setAmount(event.getAmount() * (float) factor);
    }

    /** Each server level tick: the trackers a trick left running. */
    static void levelTick(ServerLevel level) {
        Tracks t = TRACKS.get(level.dimension());
        if (t == null || t.idle()) return;
        long time = level.getGameTime();
        if (!t.scans.isEmpty()) scanStep(level, t);
        if (time % 5 == 0 && !t.streams.isEmpty()) streams(level, t, time);
        if (time % 10 != 0) return;
        for (Iterator<Map.Entry<Integer, Follow>> it = t.herded.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Player pilot = level.getPlayerByUUID(e.getValue().pilot());
            if (!(level.getEntity(e.getKey()) instanceof Mob m) || !m.isAlive() || pilot == null || time >= e.getValue().until()) it.remove();
            else lure(m, pilot);
        }
        for (Iterator<Map.Entry<Integer, Flee>> it = t.hissed.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (!(level.getEntity(e.getKey()) instanceof Creeper c) || !c.isAlive() || time >= e.getValue().until()) it.remove();
            else {
                Entity host = level.getEntity(e.getValue().host());
                scatter(c, host != null ? host.position() : e.getValue().from());
            }
        }
        for (Iterator<Map.Entry<Integer, Follow>> it = t.howled.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (!(level.getEntity(e.getKey()) instanceof Wolf w) || !w.isAlive()) {
                it.remove();
                continue;
            }
            Player pilot = level.getPlayerByUUID(e.getValue().pilot());
            if (time >= e.getValue().until() || pilot == null) {
                // the call is over: it sits where it is
                w.getNavigation().stop();
                w.setTarget(null);
                w.setOrderedToSit(true);
                w.setInSittingPose(true);
                it.remove();
            } else if (w.distanceToSqr(pilot) > 16 && !w.isOrderedToSit()) {
                w.getNavigation().moveTo(pilot, 1.2);
            }
        }
        for (Iterator<Map.Entry<Integer, Long>> it = t.rearing.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (time < e.getValue()) continue;
            if (level.getEntity(e.getKey()) instanceof AbstractHorse horse) horse.setStanding(false);
            it.remove();
        }
        t.spits.entrySet().removeIf(e -> time >= e.getValue().until() || level.getEntity(e.getKey()) == null);
    }

    private static void streams(ServerLevel level, Tracks t, long time) {
        for (Iterator<Map.Entry<Integer, Stream>> it = t.streams.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Entity host = level.getEntity(e.getKey());
            if (host == null || !host.isAlive() || time >= e.getValue().until()) {
                it.remove();
                continue;
            }
            Vec3 from = host.position().add(0, host.getBbHeight() * 0.5, 0);
            Vec3 dir = e.getValue().to().subtract(from);
            if (dir.lengthSqr() < 1) continue;
            dir = dir.normalize();
            for (int i = 1; i <= 6; i++) {
                Vec3 p = from.add(dir.scale(i * 0.8));
                level.sendParticles(ParticleTypes.BUBBLE, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.02);
                level.sendParticles(ParticleTypes.DOLPHIN, p.x, p.y, p.z, 2, 0.15, 0.15, 0.15, 0);
            }
        }
    }

    /** What a pig's snout is after: root crops and mushrooms. */
    public static boolean rootable(BlockState state) {
        return state.is(Blocks.CARROTS) || state.is(Blocks.POTATOES) || state.is(Blocks.BEETROOTS) || state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM);
    }

    /** A quarter of a Rooting scan for a game without the cue: a puff for the pilot alone at each find, the count at the end. */
    private static void scanStep(ServerLevel level, Tracks t) {
        for (Iterator<RootScan> it = t.scans.iterator(); it.hasNext(); ) {
            RootScan s = it.next();
            ServerPlayer pilot = level.getPlayerByUUID(s.pilot) instanceof ServerPlayer p ? p : null;
            if (pilot == null) {
                it.remove();
                continue;
            }
            int side = s.radius * 2 + 1, per = (side + 3) / 4;
            int y0 = s.centre.getY() - s.radius + s.step * per, y1 = Math.min(s.centre.getY() + s.radius, y0 + per - 1);
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int x = s.centre.getX() - s.radius; x <= s.centre.getX() + s.radius; x++) {
                for (int z = s.centre.getZ() - s.radius; z <= s.centre.getZ() + s.radius; z++) {
                    if (!level.hasChunk(x >> 4, z >> 4)) continue;
                    for (int y = y0; y <= y1 && s.found < ROOT_CAP; y++) {
                        if (rootable(level.getBlockState(m.set(x, y, z)))) {
                            s.found++;
                            level.sendParticles(pilot, ParticleTypes.HAPPY_VILLAGER, true, x + 0.5, y + 0.5, z + 0.5, 6, 0.3, 0.3, 0.3, 0);
                        }
                    }
                }
            }
            if (++s.step >= 4) {
                ROOTED.put(s.pilot, s.found);
                pilot.displayClientMessage(s.found == 0 ? Component.translatable("message.headinajar.host.rooted_none", s.radius)
                        : Component.translatable("message.headinajar.host.rooted", s.found), true);
                it.remove();
            }
        }
    }

    /** What the last server-side Rooting scan found for this player, or -1 if it never ran one for them. */
    public static int rootedFor(UUID player) {
        return ROOTED.getOrDefault(player, -1);
    }

    // --- both sides ------------------------------------------------------------------------------------------

    /** Whether the host's rider's input moves it nowhere (a withdrawn turtle). Either side. */
    public static boolean heldStill(LivingEntity host) {
        return Hosts.isHost(host) && Hosts.data(host).withdrawn(host.level().getGameTime());
    }

    private static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Mob mob && Hosts.isHost(mob) && event.getEntity() instanceof ServerPlayer viewer) TrickNet.sendTo(viewer, mob);
    }
}

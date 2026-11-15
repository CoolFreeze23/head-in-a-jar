package com.coolfreeze23.headinajar.genetics;

import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.HeadRegistry;
import com.coolfreeze23.headinajar.fx.Fx;
import com.coolfreeze23.headinajar.fx.GeneSounds;
import com.coolfreeze23.headinajar.host.HostData;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.SuspiciousEffectHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.NoteBlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import static com.coolfreeze23.headinajar.genetics.GeneNumbers.*;

/**
 * The 0.6.2 genes of the gentler creatures (livestock, the field office, the trader, the allay): food, fleece,
 * hooves, pollen, coats, curling up, noses, mimicry, tails, puffing, dusk and fetching. Numbers in {@link GeneNumbers}.
 * <p>
 * Food, bows, trades and armour work through a player's own systems, so those parts are players only; everything else
 * works on a player's body, a host and a mecha alike. A carrier's working state (curled, puffed, dancing) lives in a
 * small record per body and side; the client's copy of the curl and the puff is what the server's memos say.
 */
public final class GentleGenes {
    private GentleGenes() {
    }

    /** Keen Nose's scent trail, drawn only by the carrier's own client (sprites {@code textures/particle/scent_0/1}). */
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.core.particles.ParticleType<?>, net.minecraft.core.particles.SimpleParticleType>
            SCENT = com.coolfreeze23.headinajar.fx.FxParticles.TYPES.register("scent", () -> new net.minecraft.core.particles.SimpleParticleType(false));

    /** Puts this class's event handlers on the game's bus (called once, from the mod's constructor; this also registers {@link #SCENT}). */
    public static void register() {
        NeoForge.EVENT_BUS.register(Events.class);
    }

    /** Bamboo Diet's cost: a carrier sits down to eat (drawn by each client while the meal lasts). */
    public static boolean sitsToEat(LivingEntity e) {
        return e instanceof Player && e.isUsingItem() && carries(e) && has(e, Gene.BAMBOO_DIET) && e.getUseItem().getFoodProperties(e) != null;
    }

    // --- per-body state ------------------------------------------------------------------------------------------

    /** What a carrier is doing, kept per body and side (the client's curl and puff come from the server's memos). */
    static final class State {
        double lx, ly, lz, px, py, pz;
        boolean seen, curled, poseForced, puffShown, curlShown;
        int still, sneakStill;
        long flinchUntil, puffedUntil, nextPuff, grazeReady, flowerReady, danceUntil, nextDance, nightReady;
        long dayLast = -1;
    }

    private static final Map<Entity, State> SERVER = new WeakHashMap<>();
    private static final Map<Entity, State> CLIENT = new WeakHashMap<>();

    static State state(Entity e) {
        return (e.level().isClientSide ? CLIENT : SERVER).computeIfAbsent(e, k -> new State());
    }

    /** Saved on the body: when it next sheds a scute (Roll Up) and when its nose may next find a seed (Keen Nose). */
    static final String SCUTE_AT = "headinajar_scute_at", NOSE_READY = "headinajar_nose_ready";

    private static boolean has(LivingEntity e, Gene g) {
        return GeneEffects.has(e, g);
    }

    private static boolean carries(Entity e) {
        return e instanceof LivingEntity && e.hasData(ModRegistry.GENES) && !e.getData(ModRegistry.GENES).isEmpty();
    }

    /** Whether a Fleece carrier's fleece has grown and is working now. */
    public static boolean fleeced(LivingEntity e) {
        return has(e, Gene.FLEECE) && Genetics.genes(e).fleeced();
    }

    /** Whether a Roll Up carrier is curled now (on this side). */
    public static boolean curled(LivingEntity e) {
        if (!has(e, Gene.ROLL_UP)) return false;
        State st = state(e);
        return st.curled || e.level().getGameTime() < st.flinchUntil;
    }

    /** Whether a Puffer carrier is puffed up now (on this side). */
    public static boolean puffed(LivingEntity e) {
        return has(e, Gene.PUFFER) && e.level().getGameTime() < state(e).puffedUntil;
    }

    /** Whether a Fetch carrier is dancing to a jukebox now. */
    public static boolean dancing(LivingEntity e) {
        return has(e, Gene.FETCH) && e.level().getGameTime() < state(e).danceUntil;
    }

    /**
     * The client's copy of a curl or a puff, from the server's memo ({@link Fx#GENE_ROLL_UP} or {@link Fx#GENE_PUFFER}):
     * {@code ticks} the time it lasts, -1 until told otherwise, 0 over.
     */
    public static void mirror(LivingEntity e, int kind, int ticks) {
        State st = state(e);
        long now = e.level().getGameTime();
        if (kind == Fx.GENE_ROLL_UP) {
            st.curled = ticks < 0;
            st.flinchUntil = ticks > 0 ? now + ticks : 0;
        } else if (kind == Fx.GENE_PUFFER) {
            st.puffedUntil = ticks > 0 ? now + ticks : 0;
        }
    }

    // --- every tick, both sides ----------------------------------------------------------------------------------

    /**
     * Every tick, both sides, after {@link GeneEffects}' own attributes. {@code on}: the body's genes work now (false:
     * take every modifier of this class off); {@code k}: the 70% scale (1 in a mecha).
     */
    static void tick(LivingEntity e, boolean on, float k) {
        var add = AttributeModifier.Operation.ADD_VALUE;
        var base = AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
        var total = AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
        // Mountain Hooves: whole numbers or nothing (a step of 0.88 climbs no block), so not scaled; off while sneaking
        boolean hooves = on && has(e, Gene.MOUNTAIN_HOOVES);
        GeneEffects.set(e, Attributes.STEP_HEIGHT, "mountain_hooves", hooves && !e.isShiftKeyDown() ? HOOVES_STEP : 0, add);
        GeneEffects.set(e, Attributes.SAFE_FALL_DISTANCE, "mountain_hooves", hooves ? HOOVES_SAFE_FALL : 0, add);
        // Arctic Coat and Tadpole Tail: each its own swim modifier, so they stack with each other and Dolphin Grace
        GeneEffects.set(e, NeoForgeMod.SWIM_SPEED, "arctic_coat", on ? ARCTIC_SWIM * GeneEffects.s(e, Gene.ARCTIC_COAT) * k : 0, base);
        GeneEffects.set(e, NeoForgeMod.SWIM_SPEED, "tadpole_tail", on ? TADPOLE_SWIM * GeneEffects.s(e, Gene.TADPOLE_TAIL) * k : 0, base);
        // Pollinator's cost: slower in the rain
        boolean wet = on && has(e, Gene.POLLINATOR) && e.level().isRainingAt(e.blockPosition());
        GeneEffects.set(e, Attributes.MOVEMENT_SPEED, "pollinator", wet ? POLLINATOR_RAIN_SLOW : 0, base);
        // Fleece: fire burns longer while fleeced; Fleece and Arctic Coat never freeze
        boolean fleeced = on && fleeced(e);
        GeneEffects.set(e, Attributes.BURNING_TIME, "fleece", fleeced ? FLEECE_FIRE_LONGER : 0, base);
        if (on && (fleeced || has(e, Gene.ARCTIC_COAT)) && e.getTicksFrozen() > 0) e.setTicksFrozen(0);
        // Roll Up: curled, you go nowhere
        boolean curled = on && curled(e);
        GeneEffects.set(e, Attributes.MOVEMENT_SPEED, "roll_up", curled ? -1 : 0, total);
        GeneEffects.set(e, Attributes.JUMP_STRENGTH, "roll_up", curled ? -1 : 0, total);
        State st = state(e);
        if (e instanceof Player p) {
            if (curled) {
                if (p.getForcedPose() != Pose.SWIMMING) p.setForcedPose(Pose.SWIMMING);
                st.poseForced = true;
                if (p.isSprinting()) p.setSprinting(false);
            } else if (st.poseForced) {
                if (p.getForcedPose() == Pose.SWIMMING) p.setForcedPose(null);
                st.poseForced = false;
            }
        }
        // Puffer: bigger, slower, no sprinting (the size has its own modifier, apart from Gigantism's)
        boolean puffed = on && puffed(e);
        GeneEffects.set(e, Attributes.SCALE, "puffer", puffed ? PUFFER_SCALE : 0, base);
        GeneEffects.set(e, Attributes.MOVEMENT_SPEED, "puffer", puffed ? PUFFER_SLOW : 0, base);
        if (puffed && e.isSprinting()) e.setSprinting(false);
    }

    /** Movement, both sides (also called from host and mecha steering). */
    static void movement(LivingEntity e) {
        if (!(e instanceof Player) && curled(e)) {
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(0, Math.min(v.y, 0), 0);
        }
    }

    // --- the server's tick ---------------------------------------------------------------------------------------

    /** Fleeced carriers counted this tick, and in the last: the game-event check reads only {@link #fleecedLastTick}. */
    private static int fleecedThisTick, fleecedLastTick;

    /** The server's tick of a body whose genes work. */
    static void serverTick(LivingEntity e, ServerLevel level) {
        State st = state(e);
        long now = level.getGameTime();
        // stillness, for Roll Up, Keen Nose and Pollinator
        double dx = e.getX() - st.lx, dy = e.getY() - st.ly, dz = e.getZ() - st.lz;
        boolean moved = st.seen && dx * dx + dy * dy + dz * dz > 1.0e-4;
        st.lx = e.getX();
        st.ly = e.getY();
        st.lz = e.getZ();
        st.seen = true;
        st.still = moved ? 0 : st.still + 1;
        st.sneakStill = !moved && e.isShiftKeyDown() && e.onGround() ? st.sneakStill + 1 : 0;

        if (has(e, Gene.RUMINANT_GUT)) ruminantHost(e, level);
        if (has(e, Gene.FLEECE)) fleeceTick(e);
        if (e instanceof ServerPlayer p && has(e, Gene.MOUNTAIN_HOOVES)) hooves(p);
        if (has(e, Gene.POLLINATOR) && e.tickCount % POLLINATOR_EVERY == 0) pollinate(e, level, st, now);
        if (has(e, Gene.ARCTIC_COAT) && e.tickCount % 40 == 0) arcticHeat(e, level);
        if (has(e, Gene.ROLL_UP)) rollUp(e, level, st, now);
        if (has(e, Gene.KEEN_NOSE)) nose(e, level, st, now);
        if (has(e, Gene.MIMICRY) && e.tickCount % MIMICRY_EVERY == 0) mimic(e, level);
        if (has(e, Gene.PUFFER)) pufferTick(e, level, st, now);
        if (has(e, Gene.DUSK_DRAUGHT) && e.tickCount % 20 == 0) dusk(e, level, st);
        if (e instanceof Player p && has(e, Gene.FETCH)) fetch(p, level, st, now);
    }

    // --- Livestock -----------------------------------------------------------------------------------------------

    /** Ruminant Gut on a host: standing on grass, it grazes back what rot takes (2% a minute: holds off rot, no cure). */
    private static void ruminantHost(LivingEntity e, ServerLevel level) {
        if (e instanceof Player || !Hosts.isHost(e) || e.tickCount % RUMINANT_HOST_TICKS != 0) return;
        BlockState grass = e.getBlockStateOn();
        if (!grass.is(Blocks.GRASS_BLOCK)) return;
        HostData data = Hosts.data(e);
        if (data.shell) return;
        data.freshness = Math.min(100f, data.freshness + RUMINANT_HOST_GRAZE);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, grass), e.getX(), e.getY() + 0.1, e.getZ(), 8, 0.3, 0.05, 0.3, 0.05);
    }

    /** Fleece grows back over five minutes; a grown fleece is counted for the vibration check. */
    private static void fleeceTick(LivingEntity e) {
        GeneSet genes = Genetics.genes(e);
        if (genes.fleeced()) {
            fleecedThisTick++;
            return;
        }
        genes.fleece++;
        if (genes.fleeced()) {
            Network.syncGenes(e);
            e.level().playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.SHEEP_AMBIENT, e.getSoundSource(), 0.5f, 1.2f);
            if (e instanceof ServerPlayer p) p.displayClientMessage(Component.translatable("message.headinajar.gene.fleece_grown"), true);
        }
    }

    /** The wool a fleece of this colour gives (vanilla's own wool blocks). */
    static Item wool(int colour) {
        DyeColor dye = DyeColor.byId(colour);
        return BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(dye.getName() + "_wool"));
    }

    /** Shears a grown fleece: 1 to 3 wool, the shears worn, and the fleece starts again. */
    static void shear(LivingEntity sheep, Player by, ItemStack shears, InteractionHand hand) {
        GeneSet genes = Genetics.genes(sheep);
        int n = FLEECE_WOOL_MIN + sheep.getRandom().nextInt(FLEECE_WOOL_MAX - FLEECE_WOOL_MIN + 1);
        for (int i = 0; i < n; i++) {
            ItemEntity drop = sheep.spawnAtLocation(new ItemStack(wool(genes.fleeceColour)), 1);
            if (drop != null) drop.setDeltaMovement(drop.getDeltaMovement().add((sheep.getRandom().nextFloat() - 0.5) * 0.1, sheep.getRandom().nextFloat() * 0.05,
                    (sheep.getRandom().nextFloat() - 0.5) * 0.1));
        }
        genes.fleece = 0;
        Network.syncGenes(sheep);
        sheep.level().playSound(null, sheep.getX(), sheep.getY(), sheep.getZ(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1, 1);
        sheep.gameEvent(GameEvent.SHEAR, by);
        shears.hurtAndBreak(1, by, LivingEntity.getSlotForHand(hand));
    }

    /** Dyes a grown fleece. */
    static void dye(LivingEntity sheep, Player by, ItemStack stack, DyeColor colour) {
        GeneSet genes = Genetics.genes(sheep);
        genes.fleeceColour = colour.getId();
        Network.syncGenes(sheep);
        sheep.level().playSound(null, sheep.getX(), sheep.getY(), sheep.getZ(), SoundEvents.DYE_USE, SoundSource.PLAYERS, 1, 1);
        if (!by.getAbilities().instabuild) stack.shrink(1);
    }

    /** Mountain Hooves: hooves take no boots; they go into the pockets. */
    private static void hooves(ServerPlayer p) {
        ItemStack boots = p.getItemBySlot(EquipmentSlot.FEET);
        if (boots.isEmpty()) return;
        p.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        FormActions.give(p, boots);
        p.displayClientMessage(Component.translatable("message.headinajar.gene.hooves_boots"), true);
    }

    /** Whether Mountain Hooves walks this body on powder snow (the powder snow mixin asks). */
    public static boolean walksOnPowderSnow(Entity e) {
        return e instanceof LivingEntity living && carries(e) && has(living, Gene.MOUNTAIN_HOOVES);
    }

    // --- Field Office --------------------------------------------------------------------------------------------

    /** The plants a Pollinator has given a growth tick, per level: position to the game time it may have another. */
    private static final Map<ServerLevel, Long2LongOpenHashMap> POLLINATED = new WeakHashMap<>();
    /** Growth ticks Pollinator has given since the server started (for tests). */
    public static int pollinations;

    /** Pollinator: crops and saplings within 2 of a carrier on the move get a growth tick, each at most once a minute. */
    private static void pollinate(LivingEntity e, ServerLevel level, State st, long now) {
        double dx = e.getX() - st.px, dz = e.getZ() - st.pz, dy = e.getY() - st.py;
        st.px = e.getX();
        st.py = e.getY();
        st.pz = e.getZ();
        if (dx * dx + dy * dy + dz * dz < 0.01) return;
        Long2LongOpenHashMap next = POLLINATED.computeIfAbsent(level, l -> new Long2LongOpenHashMap());
        if (next.size() > 4096) next.values().removeIf((java.util.function.LongPredicate) t -> t <= now);
        BlockPos c = e.blockPosition();
        int r = POLLINATOR_RADIUS;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -1, -r), c.offset(r, 1, r))) {
            BlockState s = level.getBlockState(p);
            if (!s.is(BlockTags.CROPS) && !s.is(BlockTags.SAPLINGS)) continue;
            long key = p.asLong();
            if (next.get(key) > now) continue;
            next.put(key, now + POLLINATOR_PLANT_TICKS);
            s.randomTick(level, p.immutable(), level.random);
            pollinations++;
            level.sendParticles(ParticleTypes.FALLING_NECTAR, p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 2, 0.25, 0.2, 0.25, 0);
        }
    }

    /** Slowness added by Arctic Coat itself, which its own halving leaves alone. */
    private static boolean ownSlowness;

    /** Arctic Coat's cost: in hot biomes and the Nether, Slowness I and a quicker hunger. */
    private static void arcticHeat(LivingEntity e, ServerLevel level) {
        boolean hot = level.dimensionType().ultraWarm() || level.getBiome(e.blockPosition()).value().getBaseTemperature() > 1.0f;
        if (!hot) return;
        ownSlowness = true;
        try {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, true, true));
        } finally {
            ownSlowness = false;
        }
        if (e instanceof Player p) p.causeFoodExhaustion(ARCTIC_HOT_EXHAUSTION);
    }

    /** Roll Up: sneak still for a second to curl (players); hurt while standing still, anyone curls for 2 s; scutes. */
    private static void rollUp(LivingEntity e, ServerLevel level, State st, long now) {
        if (e instanceof Player p) {
            if (!st.curled && st.sneakStill >= ROLL_UP_STILL) {
                st.curled = true;
                if (p instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("message.headinajar.gene.roll_up"), true);
            } else if (st.curled && !p.isShiftKeyDown()) {
                st.curled = false;
            }
        }
        boolean curled = st.curled || now < st.flinchUntil;
        if (curled != st.curlShown) {
            st.curlShown = curled;
            int arg = !curled ? 0 : st.curled ? -1 : (int) (st.flinchUntil - now);
            Fx.send(level, Fx.of(Fx.GENE_ROLL_UP, e.position(), null, e.getId(), -1, arg));
            level.playSound(null, e.getX(), e.getY(), e.getZ(), curled ? SoundEvents.ARMADILLO_ROLL : SoundEvents.ARMADILLO_UNROLL_FINISH,
                    e.getSoundSource(), 0.8f, 1);
        }
        var data = e.getPersistentData();
        long at = data.getLong(SCUTE_AT);
        if (at == 0 || at > now + ROLL_UP_SCUTE_TICKS) {
            data.putLong(SCUTE_AT, now + ROLL_UP_SCUTE_TICKS);
        } else if (now >= at) {
            e.spawnAtLocation(Items.ARMADILLO_SCUTE);
            level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ARMADILLO_SCUTE_DROP, e.getSoundSource(), 1, 1);
            data.putLong(SCUTE_AT, now + ROLL_UP_SCUTE_TICKS);
        }
    }

    /** Keen Nose: sneak still on grass, moss or dirt and, one time in five, it finds a seed (then not for 10 min); flowers make you sneeze. */
    private static void nose(LivingEntity e, ServerLevel level, State st, long now) {
        if (st.sneakStill > 0 && st.sneakStill % NOSE_DIG_STILL == 0 && e.getBlockStateOn().is(BlockTags.DIRT)) {
            var data = e.getPersistentData();
            BlockPos on = e.getOnPos();
            level.sendParticles(ParticleTypes.DUST_PLUME, e.getX(), on.getY() + 1.05, e.getZ(), 6, 0.3, 0.05, 0.3, 0.01);
            long ready = data.getLong(NOSE_READY);
            if (ready > now + NOSE_DIG_COOLDOWN) ready = 0;
            if (now >= ready && e.getRandom().nextInt(NOSE_DIG_ODDS) == 0) {
                e.spawnAtLocation(e.getRandom().nextBoolean() ? Items.TORCHFLOWER_SEEDS : Items.PITCHER_POD);
                level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.SNIFFER_DROP_SEED, e.getSoundSource(), 1, 1);
                data.putLong(NOSE_READY, now + NOSE_DIG_COOLDOWN);
                if (e instanceof ServerPlayer p) p.displayClientMessage(Component.translatable("message.headinajar.gene.nose_found"), true);
            }
        }
        if (e.tickCount % 200 == 0 && e.getRandom().nextInt(3) == 0) {
            BlockPos c = e.blockPosition();
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-2, -1, -2), c.offset(2, 1, 2))) {
                if (level.getBlockState(p).is(BlockTags.FLOWERS)) {
                    level.playSound(null, e.getX(), e.getY(), e.getZ(), GeneSounds.SNEEZE.get(), e.getSoundSource(), 1, 0.9f + e.getRandom().nextFloat() * 0.2f);
                    level.sendParticles(ParticleTypes.SNEEZE, e.getX(), e.getEyeY(), e.getZ(), 4, 0.1, 0.1, 0.1, 0.02);
                    break;
                }
            }
        }
    }

    /** Mimicry: a hostile within 32 that the carrier cannot see is imitated, out loud, in its own voice. */
    private static void mimic(LivingEntity e, ServerLevel level) {
        List<Mob> near = level.getEntitiesOfClass(Mob.class, e.getBoundingBox().inflate(MIMICRY_RANGE),
                m -> m instanceof Enemy && m.isAlive() && m.distanceToSqr(e) <= MIMICRY_RANGE * MIMICRY_RANGE);
        near.sort(java.util.Comparator.comparingDouble(m -> m.distanceToSqr(e)));
        int rays = 0;
        for (Mob m : near) {
            if (rays++ >= 4) break;
            if (e.hasLineOfSight(m)) continue;
            SoundEvent voice = m.getType() == EntityType.CREEPER ? SoundEvents.CREEPER_PRIMED
                    : ((com.coolfreeze23.headinajar.mixin.MobAmbientInvoker) m).headinajar$ambient();
            if (voice == null) continue;
            level.playSound(null, e.getX(), e.getEyeY(), e.getZ(), voice, e.getSoundSource(), 0.8f, 1.15f + e.getRandom().nextFloat() * 0.1f);
            return;
        }
    }

    /** Puffer: a hostile within 2 puffs you; while puffed, what touches you is poisoned. */
    private static void pufferTick(LivingEntity e, ServerLevel level, State st, long now) {
        boolean puffed = now < st.puffedUntil;
        if (puffed && e.tickCount % 5 == 0) {
            AABB touch = e.getBoundingBox().inflate(0.15);
            for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, touch, o -> o != e && o.isAlive() && o != e.getVehicle()
                    && !e.getPassengers().contains(o))) {
                other.addEffect(new MobEffectInstance(MobEffects.POISON, PUFFER_POISON, 0), e);
            }
        }
        if (!puffed && st.puffShown) {
            st.puffShown = false;
            Fx.send(level, Fx.of(Fx.GENE_PUFFER, e.position(), null, e.getId(), -1, 0));
        }
        if (!puffed && e.tickCount % 5 == 0 && now >= st.nextPuff
                && !level.getEntitiesOfClass(Mob.class, e.getBoundingBox().inflate(PUFFER_NEAR), m -> m instanceof Enemy && m.isAlive()).isEmpty()) {
            puff(e, level);
        }
    }

    /** Puffs a Puffer carrier up for 3 s if there is room (8 s between puffs); with no room, says so once. */
    static boolean puff(LivingEntity e, ServerLevel level) {
        State st = state(e);
        long now = level.getGameTime();
        if (now < st.puffedUntil || now < st.nextPuff) return false;
        st.nextPuff = now + PUFFER_BETWEEN;
        if (!roomToPuff(e)) {
            if (e instanceof ServerPlayer p) Refusals.deny(p, "gene.puffer.no_room");
            return false;
        }
        st.puffedUntil = now + PUFFER_TICKS;
        st.puffShown = true;
        level.playSound(null, e.getX(), e.getY(), e.getZ(), GeneSounds.PUFF.get(), e.getSoundSource(), 1, 1);
        level.sendParticles(ParticleTypes.BUBBLE, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 12, e.getBbWidth() / 2, e.getBbHeight() / 3, e.getBbWidth() / 2, 0.05);
        Fx.send(level, Fx.of(Fx.GENE_PUFFER, e.position(), null, e.getId(), -1, PUFFER_TICKS));
        return true;
    }

    /** Whether the body, 30% bigger, would fit where it stands (a puff into a ceiling would suffocate it). */
    static boolean roomToPuff(LivingEntity e) {
        float scale = e.getScale();
        float f = (float) ((scale + PUFFER_SCALE) / scale);
        EntityDimensions d = e.getDimensions(e.getPose()).scale(f);
        return e.level().noCollision(e, d.makeBoundingBox(e.position()).deflate(1.0e-5));
    }

    // --- Sales and Logistics -------------------------------------------------------------------------------------

    /** Dusk Draught: invisible at dusk; at dawn, any milk carried is drunk. (The night vanish is on targeting, below.) */
    private static void dusk(LivingEntity e, ServerLevel level, State st) {
        long day = level.getDayTime() % 24000L;
        long last = st.dayLast;
        st.dayLast = day;
        if (last < 0) return;
        if (last < 12000 && day >= 12000) {
            e.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, DUSK_INVISIBLE, 0, false, false, true));
        } else if (day < last && e instanceof ServerPlayer p) {
            var inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (!stack.is(Items.MILK_BUCKET)) continue;
                p.removeEffectsCuredBy(EffectCures.MILK);
                if (!p.getAbilities().instabuild) {
                    stack.shrink(1);
                    if (stack.isEmpty()) inv.setItem(i, new ItemStack(Items.BUCKET));
                    else FormActions.give(p, new ItemStack(Items.BUCKET));
                }
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_DRINK, p.getSoundSource(), 1, 1);
                p.displayClientMessage(Component.translatable("message.headinajar.gene.dusk_milk"), true);
                break;
            }
        }
    }

    /** Note blocks played lately, per level: position to game time (Fetch sends items to them). */
    private static final Map<ServerLevel, Map<BlockPos, Long>> NOTES = new WeakHashMap<>();

    /** Fetch: dropped items like the off hand's drift to you, or to a note block played lately; dancing shows notes. */
    private static void fetch(Player p, ServerLevel level, State st, long now) {
        if (now < st.danceUntil && p.tickCount % 5 == 0) {
            level.sendParticles(ParticleTypes.NOTE, p.getX(), p.getY() + p.getBbHeight() + 0.3, p.getZ(), 1, 0.3, 0.1, 0.3, 1);
        }
        if (p.tickCount % FETCH_EVERY != 0) return;
        ItemStack off = p.getOffhandItem();
        if (off.isEmpty()) return;
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(FETCH_RANGE),
                i -> i.isAlive() && ItemStack.isSameItem(i.getItem(), off));
        if (items.isEmpty()) return;
        Vec3 to = p.position().add(0, 0.3, 0);
        Map<BlockPos, Long> notes = NOTES.get(level);
        if (notes != null) {
            notes.values().removeIf(t -> now - t > FETCH_NOTE_TICKS);
            double best = FETCH_RANGE * FETCH_RANGE;
            for (BlockPos pos : notes.keySet()) {
                double d = pos.distToCenterSqr(p.position());
                if (d <= best) {
                    best = d;
                    to = Vec3.atCenterOf(pos).add(0, 0.6, 0);
                }
            }
        }
        for (ItemEntity item : items) {
            Vec3 pull = to.subtract(item.position());
            double d = pull.length();
            if (d < 0.8) continue;
            Vec3 v = pull.scale(FETCH_SPEED / d);
            item.setDeltaMovement(v.x, Math.max(item.getDeltaMovement().y, v.y + 0.04), v.z);
        }
    }

    /** Whether a jukebox within Fetch's reach is playing a disc. */
    private static boolean jukeboxNear(ServerLevel level, Vec3 at) {
        int r = FETCH_RANGE;
        int x0 = ((int) Math.floor(at.x) - r) >> 4, x1 = ((int) Math.floor(at.x) + r) >> 4;
        int z0 = ((int) Math.floor(at.z) - r) >> 4, z1 = ((int) Math.floor(at.z) + r) >> 4;
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof JukeboxBlockEntity jukebox && jukebox.getSongPlayer().isPlaying()
                            && be.getBlockPos().distToCenterSqr(at) <= r * r) return true;
                }
            }
        }
        return false;
    }

    // --- food ----------------------------------------------------------------------------------------------------

    /** Foods whose sickness Swine Appetite (all of them) and Puffer (pufferfish) shrug off. */
    private static boolean swineFood(ItemStack stack) {
        return stack.is(Items.ROTTEN_FLESH) || stack.is(Items.SPIDER_EYE) || stack.is(Items.POISONOUS_POTATO) || stack.is(Items.CHICKEN)
                || stack.is(Items.PUFFERFISH);
    }

    /** An instant bite: hunger and a little saturation, the item eaten, the eating sound and its vibration. */
    private static void bite(ServerPlayer p, ItemStack stack, int food) {
        p.getFoodData().eat(food, 0.1f);
        ServerLevel level = p.serverLevel();
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack.copyWithCount(1)), p.getX(), p.getEyeY() - 0.1, p.getZ(),
                6, 0.15, 0.1, 0.15, 0.05);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EAT, p.getSoundSource(), 0.6f, 0.9f + p.getRandom().nextFloat() * 0.2f);
        p.gameEvent(GameEvent.EAT);
        if (has(p, Gene.SWINE_APPETITE)) oink(p);
        if (!p.getAbilities().instabuild) stack.shrink(1);
    }

    private static void oink(LivingEntity e) {
        e.level().playSound(null, e.getX(), e.getY(), e.getZ(), GeneSounds.OINK.get(), e.getSoundSource(), 1, 0.9f + e.getRandom().nextFloat() * 0.2f);
    }

    /**
     * Stew Glands: a small flower is a bite of food with its stew effect (saturation flowers at vanilla's time, the rest
     * three times as long), one flower each 30 s.
     */
    private static boolean eatFlower(ServerPlayer p, ItemStack stack, SuspiciousEffectHolder holder) {
        State st = state(p);
        long now = p.level().getGameTime();
        if (now < st.flowerReady) return Refusals.deny(p, "gene.stew.digesting");
        st.flowerReady = now + STEW_FLOWER_COOLDOWN;
        for (SuspiciousStewEffects.Entry entry : holder.getSuspiciousEffects().effects()) {
            boolean saturation = entry.effect().is(MobEffects.SATURATION);
            int ticks = saturation ? entry.duration() : entry.duration() * STEW_FLOWER_TIMES;
            p.addEffect(new MobEffectInstance(entry.effect(), ticks));
        }
        bite(p, stack, STEW_FLOWER_FOOD);
        return true;
    }

    /** What a player's food stood at when they began eating, for Stew Glands' half-filling stews. */
    private static final Map<Player, float[]> MEAL_START = new WeakHashMap<>();

    // --- the events ----------------------------------------------------------------------------------------------

    /** The gentler genes' event handlers. */
    public static final class Events {
        private Events() {
        }

        /** The fleeced count the vibration check reads is last tick's. */
        @SubscribeEvent
        public static void serverTick(ServerTickEvent.Post event) {
            fleecedLastTick = fleecedThisTick;
            fleecedThisTick = 0;
        }

        /** Fleece: a fleeced carrier's steps, landings and meals make no vibrations. One field read for everyone else. */
        @SubscribeEvent
        public static void vibration(VanillaGameEvent event) {
            if (fleecedLastTick == 0) return;
            Holder<GameEvent> what = event.getVanillaEvent();
            if (what != GameEvent.STEP && what != GameEvent.HIT_GROUND && what != GameEvent.EAT) return;
            if (event.getCause() instanceof LivingEntity e && carries(e) && fleeced(e)) event.setCanceled(true);
        }

        /** The sicknesses Swine Appetite and Puffer ignore, and Arctic Coat's halved Slowness. */
        @SubscribeEvent
        public static void effect(MobEffectEvent.Applicable event) {
            LivingEntity e = event.getEntity();
            if (e.level().isClientSide || !carries(e)) return;
            var effect = event.getEffectInstance().getEffect();
            if ((effect.is(MobEffects.HUNGER) || effect.is(MobEffects.POISON) || effect.is(MobEffects.CONFUSION)) && e.isUsingItem()) {
                ItemStack food = e.getUseItem();
                if (has(e, Gene.SWINE_APPETITE) && swineFood(food) || has(e, Gene.PUFFER) && food.is(Items.PUFFERFISH)) {
                    event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
                    return;
                }
            }
            if (effect.is(MobEffects.MOVEMENT_SLOWDOWN) && !ownSlowness && has(e, Gene.ARCTIC_COAT)) {
                MobEffectInstance in = event.getEffectInstance();
                if (in.isInfiniteDuration() || in.getDuration() < 2) return;
                event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
                ownSlowness = true;
                try {
                    e.addEffect(new MobEffectInstance(in.getEffect(), Math.max(1, Math.round(in.getDuration() * ARCTIC_SLOW_SHARE)), in.getAmplifier(),
                            in.isAmbient(), in.isVisible(), in.showIcon()), event.getEffectSource());
                } finally {
                    ownSlowness = false;
                }
            }
        }

        @SubscribeEvent
        public static void startEating(LivingEntityUseItemEvent.Start event) {
            if (event.getEntity() instanceof ServerPlayer p && carries(p) && has(p, Gene.STEW_GLANDS)) {
                MEAL_START.put(p, new float[]{p.getFoodData().getFoodLevel(), p.getFoodData().getSaturationLevel()});
            }
        }

        /** A meal finished: the gentler genes' food rules (players only: hosts and mecha do not eat). */
        @SubscribeEvent
        public static void ate(LivingEntityUseItemEvent.Finish event) {
            if (!(event.getEntity() instanceof ServerPlayer p) || !carries(p) || !GeneEffects.active(p)) return;
            ItemStack item = event.getItem();
            FoodProperties food = item.getFoodProperties(p);
            var data = p.getFoodData();
            if (food != null) {
                if (has(p, Gene.SWINE_APPETITE)) oink(p);
                if (has(p, Gene.RUMINANT_GUT) && food.nutrition() > 0) {
                    float k = GeneEffects.s(p, Gene.RUMINANT_GUT) * GeneEffects.scale(p);
                    int extra = Math.max(1, Math.round(food.nutrition() * RUMINANT_FOOD * k));
                    data.eat(extra, food.saturation() / (2f * food.nutrition()));
                }
            }
            if (has(p, Gene.SWINE_APPETITE) && item.is(Items.ROTTEN_FLESH)) data.eat(SWINE_ROTTEN_EXTRA, 0.1f);
            if (has(p, Gene.POLLINATOR) && item.is(Items.HONEY_BOTTLE)) data.eat(POLLINATOR_HONEY_EXTRA, 0.1f);
            if (has(p, Gene.STEW_GLANDS) && (item.is(Items.MUSHROOM_STEW) || item.is(Items.SUSPICIOUS_STEW))) {
                float[] before = MEAL_START.remove(p);
                if (before != null) {
                    int gained = Math.max(0, data.getFoodLevel() - (int) before[0]);
                    float sat = Math.max(0, data.getSaturationLevel() - before[1]);
                    data.setFoodLevel(data.getFoodLevel() - Math.round(gained * (1 - STEW_STEW_SHARE)));
                    data.setSaturation(Math.max(0, Math.min(data.getFoodLevel(), data.getSaturationLevel() - sat * (1 - STEW_STEW_SHARE))));
                }
            }
            if (has(p, Gene.MIMICRY) && item.is(Items.COOKIE)) {
                p.addEffect(new MobEffectInstance(MobEffects.POISON, MIMICRY_COOKIE_POISON, 1));
                p.hurt(p.damageSources().magic(), MIMICRY_COOKIE_DAMAGE);
            }
        }

        /**
         * Use in the air: Stew Glands' flowers and bowl, Ruminant Gut's wheat, Bamboo Diet's bamboo and sugar cane, and a
         * Fleece carrier's own shears and dye (sneaking). The client swings; the server eats, or says why not.
         */
        @SubscribeEvent
        public static void useItem(PlayerInteractEvent.RightClickItem event) {
            Player player = event.getEntity();
            if (!carries(player) || !GeneEffects.active(player)) return;
            if (curled(player)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                if (player instanceof ServerPlayer sp) Refusals.deny(sp, "gene.roll_up.curled");
                return;
            }
            ItemStack stack = event.getItemStack();
            boolean sneak = player.isShiftKeyDown();
            SuspiciousEffectHolder flower = stack.is(ItemTags.SMALL_FLOWERS) ? SuspiciousEffectHolder.tryGet(stack.getItem()) : null;
            int bite = 0;
            boolean mine;
            if (flower != null && has(player, Gene.STEW_GLANDS)) mine = true;
            else if (sneak && stack.is(Items.BOWL) && has(player, Gene.STEW_GLANDS)) mine = true;
            else if (stack.is(Items.WHEAT) && has(player, Gene.RUMINANT_GUT)) mine = (bite = RUMINANT_WHEAT_FOOD) > 0;
            else if ((stack.is(Items.BAMBOO) || stack.is(Items.SUGAR_CANE)) && has(player, Gene.BAMBOO_DIET)) mine = (bite = BAMBOO_FOOD) > 0;
            else mine = sneak && (stack.is(Items.SHEARS) || stack.getItem() instanceof DyeItem) && has(player, Gene.FLEECE);
            if (!mine) return;
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (!(player instanceof ServerPlayer p)) return;
            if (flower != null && has(p, Gene.STEW_GLANDS)) {
                eatFlower(p, stack, flower);
            } else if (stack.is(Items.BOWL)) {
                ItemStack stew = new ItemStack(Items.MUSHROOM_STEW);
                if (!p.getAbilities().instabuild) stack.shrink(1);
                FormActions.give(p, stew);
                p.getCooldowns().addCooldown(Items.BOWL, STEW_BOWL_COOLDOWN);
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.MOOSHROOM_MILK, p.getSoundSource(), 1, 1);
            } else if (bite > 0) {
                if (!p.canEat(false)) Refusals.deny(p, "gene.full");
                else bite(p, stack, bite);
            } else if (stack.is(Items.SHEARS)) {
                if (!fleeced(p)) Refusals.deny(p, "gene.fleece.shorn");
                else shear(p, p, stack, event.getHand());
            } else if (stack.getItem() instanceof DyeItem dye) {
                if (!fleeced(p)) Refusals.deny(p, "gene.fleece.shorn");
                else if (Genetics.genes(p).fleeceColour == dye.getDyeColor().getId()) Refusals.deny(p, "gene.fleece.dyed");
                else dye(p, p, stack, dye.getDyeColor());
            }
        }

        /**
         * Shears or dye on a fleeced colleague (a player, or a host: its rider's trust decides). Ahead of a host's own
         * answer to a click (its status line), which would otherwise take the shears' click first.
         */
        @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
        public static void useOn(PlayerInteractEvent.EntityInteract event) {
            Player player = event.getEntity();
            if (carries(player) && curled(player)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                if (player instanceof ServerPlayer sp) Refusals.deny(sp, "gene.roll_up.curled");
                return;
            }
            ItemStack stack = event.getItemStack();
            if (!(stack.is(Items.SHEARS) || stack.getItem() instanceof DyeItem)) return;
            if (!(event.getTarget() instanceof LivingEntity target) || !carries(target) || !has(target, Gene.FLEECE)) return;
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (!(player instanceof ServerPlayer p)) return;
            Player owner = target instanceof Player tp ? tp : Hosts.pilot(target);
            if (owner != null && owner != p && !HeadRegistry.get(p).trusts(owner.getUUID(), p)) {
                Refusals.deny(p, "gene.fleece.untrusted");
                return;
            }
            if (!fleeced(target)) {
                Refusals.deny(p, "gene.fleece.shorn");
            } else if (stack.getItem() instanceof DyeItem dye) {
                if (Genetics.genes(target).fleeceColour == dye.getDyeColor().getId()) Refusals.deny(p, "gene.fleece.dyed");
                else dye(target, p, stack, dye.getDyeColor());
            } else {
                shear(target, p, stack, event.getHand());
            }
        }

        /** Ruminant Gut's graze (sneak, empty hand, grass) and Bamboo Diet's double cake slice; nothing while curled. */
        @SubscribeEvent
        public static void useBlock(PlayerInteractEvent.RightClickBlock event) {
            Player player = event.getEntity();
            if (!carries(player) || !GeneEffects.active(player)) return;
            if (curled(player)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                if (player instanceof ServerPlayer sp) Refusals.deny(sp, "gene.roll_up.curled");
                return;
            }
            BlockPos pos = event.getPos();
            BlockState state = player.level().getBlockState(pos);
            ItemStack held = event.getItemStack();
            if (event.getHand() == InteractionHand.MAIN_HAND && held.isEmpty() && player.isShiftKeyDown() && state.is(Blocks.GRASS_BLOCK)
                    && has(player, Gene.RUMINANT_GUT)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                if (player instanceof ServerPlayer p) graze(p, pos, state);
                return;
            }
            if (state.getBlock() instanceof CakeBlock && has(player, Gene.BAMBOO_DIET) && player.canEat(false)
                    && !(held.is(ItemTags.CANDLES) && state.getValue(CakeBlock.BITES) == 0) && !(player.isSecondaryUseActive() && !held.isEmpty())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                if (player instanceof ServerPlayer p) cake(p, pos, state);
            }
        }

        @SubscribeEvent
        public static void mine(PlayerInteractEvent.LeftClickBlock event) {
            Player player = event.getEntity();
            if (carries(player) && curled(player)) {
                event.setCanceled(true);
                if (player instanceof ServerPlayer sp && event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) {
                    Refusals.deny(sp, "gene.roll_up.curled");
                }
            }
        }

        @SubscribeEvent
        public static void useOnSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
            Player player = event.getEntity();
            if (carries(player) && curled(player)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
            }
        }

        /** Curled or dancing, a carrier does not attack. */
        @SubscribeEvent
        public static void attack(AttackEntityEvent event) {
            Player player = event.getEntity();
            if (!carries(player) || !GeneEffects.active(player)) return;
            if (curled(player)) {
                event.setCanceled(true);
                if (player instanceof ServerPlayer sp) Refusals.deny(sp, "gene.roll_up.curled");
                return;
            }
            if (!has(player, Gene.FETCH) || !(player instanceof ServerPlayer p)) return;
            State st = state(p);
            long now = p.level().getGameTime();
            if (now < st.danceUntil) {
                event.setCanceled(true);
                return;
            }
            if (now >= st.nextDance && jukeboxNear(p.serverLevel(), p.position())) {
                st.danceUntil = now + FETCH_DANCE;
                st.nextDance = now + FETCH_DANCE_EVERY;
                event.setCanceled(true);
                p.displayClientMessage(Component.translatable("message.headinajar.gene.fetch_dance"), true);
                p.serverLevel().sendParticles(ParticleTypes.NOTE, p.getX(), p.getY() + p.getBbHeight() + 0.3, p.getZ(), 4, 0.4, 0.1, 0.4, 1);
            }
        }

        /** Fleece's fire, Roll Up's 60% less, and a Puffer hit in melee puffs. */
        @SubscribeEvent
        public static void incoming(LivingIncomingDamageEvent event) {
            LivingEntity victim = event.getEntity();
            if (!carries(victim) || !GeneEffects.active(victim)) return;
            var source = event.getSource();
            if (source.is(DamageTypeTags.IS_FIRE) && fleeced(victim)) event.setAmount(event.getAmount() * FLEECE_FIRE_HARDER);
            if (curled(victim) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) event.setAmount(event.getAmount() * ROLL_UP_DAMAGE);
            if (has(victim, Gene.PUFFER) && victim.level() instanceof ServerLevel level && source.getDirectEntity() instanceof LivingEntity attacker
                    && attacker == source.getEntity() && attacker != victim) {
                puff(victim, level);
            }
        }

        /** Ruminant Gut moos when hurt; Roll Up, hurt standing still, curls for 2 s. */
        @SubscribeEvent
        public static void hurt(LivingDamageEvent.Post event) {
            LivingEntity victim = event.getEntity();
            if (event.getNewDamage() <= 0 || victim.level().isClientSide || !carries(victim) || !GeneEffects.active(victim)) return;
            if (has(victim, Gene.RUMINANT_GUT)) {
                victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(), GeneSounds.MOO.get(), victim.getSoundSource(), 1,
                        0.9f + victim.getRandom().nextFloat() * 0.2f);
            }
            if (has(victim, Gene.ROLL_UP)) {
                State st = state(victim);
                long now = victim.level().getGameTime();
                if (!st.curled && st.still >= 5 && now >= st.flinchUntil) st.flinchUntil = now + ROLL_UP_FLINCH;
            }
        }

        @SubscribeEvent
        public static void knockback(LivingKnockBackEvent event) {
            LivingEntity e = event.getEntity();
            if (carries(e) && curled(e)) event.setCanceled(true);
        }

        /** Bees never anger at a Pollinator; at night a hostile's first look in two minutes vanishes a Dusk Draught carrier. */
        @SubscribeEvent
        public static void target(LivingChangeTargetEvent event) {
            LivingEntity target = event.getNewAboutToBeSetTarget();
            if (target == null || target.level().isClientSide || !carries(target)) return;
            if (event.getEntity() instanceof Bee && has(target, Gene.POLLINATOR)) {
                event.setCanceled(true);
                return;
            }
            if (event.getEntity() instanceof Enemy && has(target, Gene.DUSK_DRAUGHT)) {
                long day = target.level().getDayTime() % 24000L;
                if (day < 13000 || day > 23000) return;
                State st = state(target);
                long now = target.level().getGameTime();
                if (now < st.nightReady) return;
                st.nightReady = now + DUSK_NIGHT_EVERY;
                target.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, DUSK_NIGHT_INVISIBLE, 0, false, false, true));
            }
        }

        /** Fetch: a note block played is where fetched items go for the next 30 s. */
        @SubscribeEvent
        public static void note(NoteBlockEvent.Play event) {
            if (event.getLevel() instanceof ServerLevel level) {
                NOTES.computeIfAbsent(level, l -> new HashMap<>()).put(event.getPos().immutable(), level.getGameTime());
            }
        }
    }

    /** Ruminant Gut's graze: 1 hunger, the grass to dirt, at most every 3 s; claims hold. */
    private static void graze(ServerPlayer p, BlockPos pos, BlockState grass) {
        State st = state(p);
        long now = p.level().getGameTime();
        if (now < st.grazeReady) {
            Refusals.deny(p, "gene.graze.chewing");
            return;
        }
        if (!p.canEat(false)) {
            Refusals.deny(p, "gene.full");
            return;
        }
        if (!GeneClaims.mayChange(p, pos)) {
            Refusals.deny(p, "gene.graze.claim");
            return;
        }
        st.grazeReady = now + RUMINANT_GRAZE_TICKS;
        ServerLevel level = p.serverLevel();
        level.levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(grass));
        level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
        p.getFoodData().eat(1, 0.1f);
        p.gameEvent(GameEvent.EAT);
        if (has(p, Gene.SWINE_APPETITE)) oink(p);
        p.swing(InteractionHand.MAIN_HAND, true);
    }

    /** Bamboo Diet: a cake slice fills double (vanilla's slice, with twice its food). */
    private static void cake(ServerPlayer p, BlockPos pos, BlockState state) {
        ServerLevel level = p.serverLevel();
        p.awardStat(net.minecraft.stats.Stats.EAT_CAKE_SLICE);
        p.getFoodData().eat(2 + BAMBOO_CAKE_EXTRA, 0.1f);
        int bites = state.getValue(CakeBlock.BITES);
        level.gameEvent(p, GameEvent.EAT, pos);
        if (bites < 6) {
            level.setBlock(pos, state.setValue(CakeBlock.BITES, bites + 1), 3);
        } else {
            level.removeBlock(pos, false);
            level.gameEvent(p, GameEvent.BLOCK_DESTROY, pos);
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EAT, p.getSoundSource(), 0.6f, 1);
        if (has(p, Gene.SWINE_APPETITE)) oink(p);
    }
}

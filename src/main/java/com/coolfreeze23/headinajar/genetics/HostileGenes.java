package com.coolfreeze23.headinajar.genetics;

import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.fx.Fx;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The 0.6.2 genes of the hostile creatures (the night shift, accounts, legal, the witch, the endermite, the
 * silverfish): undeath, sun, rehabilitation, gold, rampages, bows and arrows, the seabed, snow, doors, potions, pearls
 * and stone. Numbers in {@link GeneNumbers}. What no event reaches is changed by the mixins in {@code mixin.genes},
 * each of which asks a method here (one attachment read for anyone who carries no genes).
 * <p>
 * A benefit's size is scaled as every gene's is (70% in bodies and hosts, full in mecha, times the gene's strength);
 * costs, how long an effect lasts and the bow and crossbow draw times are not.
 */
public final class HostileGenes {
    /** Rampage tells the carrier's client it is on a streak (it cannot sneak): entityA = the body, arg = ticks left. */
    public static final int FX_RAMPAGE = Fx.GENE_SPARE_C;
    /** On a zombie the carrier has struck: the carriers' UUIDs it may now fight (Undeath). */
    static final String GRUDGES = "headinajar_undeath_grudges";
    /** Rehabilitated: the game time until which this player's discount is suspended (they hit a villager). */
    static final String REHAB_SUSPENDED = "headinajar_rehab_suspended";

    /** Rampage: per attacker, {last hit's game time, hits in the streak}. Server only. */
    private static final Map<Entity, long[]> STREAKS = new WeakHashMap<>();
    /** Sun-Baked: ticks spent in water without a break. Server only. */
    private static final Map<Entity, int[]> SOAKED = new WeakHashMap<>();
    /** Snow Trail: the block the body last stood in. Server only. */
    private static final Map<Entity, BlockPos> TRAIL = new WeakHashMap<>();
    /** Gold Standard: whether gold was in sight at the last look. Server only. */
    private static final Map<Entity, Boolean> GOLD_IN_SIGHT = new WeakHashMap<>();
    /** Bowstring Arms and Steady Aim: real ticks since the bow or crossbow came up, per side. */
    private static final Map<Entity, int[]> DRAW_SERVER = new WeakHashMap<>(), DRAW_CLIENT = new WeakHashMap<>();
    /** Undertow Stride: the horse a carrier rides that wears the gene's modifier, per side. */
    private static final Map<Entity, LivingEntity> MOUNT_SERVER = new WeakHashMap<>(), MOUNT_CLIENT = new WeakHashMap<>();
    /** Brute Strength: stacks a carrier threw (piglins will not pick them up to barter). Server only. */
    private static final Set<ItemStack> SPURNED = Collections.newSetFromMap(new WeakHashMap<>());
    /** Potion Tolerance: where and when the last splash potion landed (its harmful effects on a carrier are halved). */
    private static long splashTick = Long.MIN_VALUE;
    private static ServerLevel splashLevel;
    /** Set while an effect is put back on at its new length, so putting it back is not changed again. */
    private static boolean readding;

    private HostileGenes() {
    }

    /** Puts this class's event handlers on the game's bus (called once, from the mod's constructor). */
    public static void register() {
        NeoForge.EVENT_BUS.register(Events.class);
    }

    // --- shared ------------------------------------------------------------------------------------------------------

    private static boolean carries(Entity e) {
        return e instanceof LivingEntity && e.hasData(ModRegistry.GENES) && !e.getData(ModRegistry.GENES).isEmpty();
    }

    /** Whether {@code e} carries {@code g} and its genes are working (one attachment read for anyone with no genes). */
    static boolean has(Entity e, Gene g) {
        return carries(e) && GeneEffects.has((LivingEntity) e, g);
    }

    /** A benefit's size for this body: {@code amount} times the gene's strength, at 70% outside a mecha. */
    static float scaled(LivingEntity e, Gene g, float amount) {
        return amount * GeneEffects.s(e, g) * GeneEffects.scale(e);
    }

    private static void say(LivingEntity e, String key) {
        if (e instanceof ServerPlayer p) p.displayClientMessage(Component.translatable(key), true);
    }

    private static void puff(LivingEntity e, ParticleOptions particle, int count) {
        if (e.level() instanceof ServerLevel level) {
            level.sendParticles(particle, e.getX(), e.getY(0.5), e.getZ(), count, e.getBbWidth() * 0.4, e.getBbHeight() * 0.3, e.getBbWidth() * 0.4, 0.02);
        }
    }

    /** Whether a gene pair sits in one serum of this body (a pair works per strand: Herbalist). */
    static boolean sameSerum(LivingEntity e, Gene a, Gene b) {
        if (!carries(e) || !GeneEffects.active(e)) return false;
        for (Serum serum : Genetics.genes(e).serums) {
            boolean hasA = false, hasB = false;
            for (Serum.Entry entry : serum.genes()) {
                if (entry.gene() == a) hasA = true;
                if (entry.gene() == b) hasB = true;
            }
            if (hasA && hasB) return true;
        }
        return false;
    }

    private static boolean is(Holder<MobEffect> effect, Holder<MobEffect> other) {
        return effect.is(other.unwrapKey().orElseThrow());
    }

    // --- the per-tick hooks ------------------------------------------------------------------------------------------

    /**
     * Every tick, both sides, after {@link GeneEffects}' own attributes. {@code on}: the body's genes work now (false:
     * take every modifier of this class off); {@code k}: the 70% scale (1 in a mecha).
     */
    static void tick(LivingEntity e, boolean on, float k) {
        // Undertow Stride: land speed on the seabed (unscaled: anything less never reaches it), on a ridden horse too
        boolean undertow = on && GeneEffects.has(e, Gene.UNDERTOW_STRIDE);
        GeneEffects.set(e, Attributes.WATER_MOVEMENT_EFFICIENCY, "undertow_stride", undertow ? GeneNumbers.UNDERTOW_EFFICIENCY : 0, AttributeModifier.Operation.ADD_VALUE);
        Map<Entity, LivingEntity> mounts = e.level().isClientSide ? MOUNT_CLIENT : MOUNT_SERVER;
        LivingEntity horse = undertow && e.getVehicle() instanceof AbstractHorse h ? h : null;
        LivingEntity was = mounts.get(e);
        if (was != null && was != horse) GeneEffects.set(was, Attributes.WATER_MOVEMENT_EFFICIENCY, "undertow_stride_mount", 0, AttributeModifier.Operation.ADD_VALUE);
        if (horse != null) {
            GeneEffects.set(horse, Attributes.WATER_MOVEMENT_EFFICIENCY, "undertow_stride_mount", GeneNumbers.UNDERTOW_EFFICIENCY, AttributeModifier.Operation.ADD_VALUE);
            mounts.put(e, horse);
        } else if (was != null) {
            mounts.remove(e);
        }
        // Gold Standard's look at gold on the ground is the server's (every 10 ticks); the attribute reaches the client
        if (!e.level().isClientSide) {
            boolean slowed = on && GeneEffects.has(e, Gene.GOLD_STANDARD) && GOLD_IN_SIGHT.getOrDefault(e, false);
            GeneEffects.set(e, Attributes.MOVEMENT_SPEED, "gold_standard", slowed ? GeneNumbers.GOLD_SLOW : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            if (!on) {
                STREAKS.remove(e);
                SOAKED.remove(e);
                TRAIL.remove(e);
                GOLD_IN_SIGHT.remove(e);
            }
        }
    }

    /** Movement, both sides (also called from host and mecha steering). */
    static void movement(LivingEntity e) {
        if (!GeneEffects.has(e, Gene.UNDERTOW_STRIDE)) return;
        undertow(e, e.jumping);
        // a horse you ride walks the seabed too, and does not float
        if (e.getVehicle() instanceof AbstractHorse horse) undertow(horse, horse.jumping || e.jumping);
    }

    /** Undertow Stride in water: you sink, swim up half as fast, and jump off the seabed. */
    private static void undertow(LivingEntity e, boolean jumping) {
        if (!e.isInWater()) return;
        Vec3 v = e.getDeltaMovement();
        double y = v.y;
        if (e.onGround() && jumping) {
            y = 0.42;
        } else if (y > 0) {
            y *= GeneNumbers.UNDERTOW_RISE;
        } else {
            y -= 0.02;
        }
        e.setDeltaMovement(v.x, y, v.z);
    }

    /** The server's tick of a body whose genes work. */
    static void serverTick(LivingEntity e, ServerLevel level) {
        long now = level.getGameTime();
        // Undeath's cost: daylight burns you as it burns a zombie, unless your head is covered (Sun-Baked: never)
        if (GeneEffects.has(e, Gene.UNDEATH) && !GeneEffects.has(e, Gene.SUN_BAKED) && !(e instanceof MechaEntity) && e.isAlive() && sunBurnTick(e, level)) {
            ItemStack helmet = e.getItemBySlot(EquipmentSlot.HEAD);
            if (!helmet.isEmpty()) {
                if (helmet.isDamageableItem()) {
                    helmet.setDamageValue(helmet.getDamageValue() + e.getRandom().nextInt(2));
                    if (helmet.getDamageValue() >= helmet.getMaxDamage()) {
                        e.onEquippedItemBroken(helmet.getItem(), EquipmentSlot.HEAD);
                        e.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                    }
                }
            } else {
                e.igniteForSeconds(8);
            }
        }
        // Sun-Baked's cost: husks do not swim (30 s in water, a minute of Slowness I)
        if (GeneEffects.has(e, Gene.SUN_BAKED)) {
            int[] soaked = SOAKED.computeIfAbsent(e, x -> new int[1]);
            soaked[0] = e.isInWater() ? soaked[0] + 1 : 0;
            if (soaked[0] >= GeneNumbers.SUN_WATER) {
                soaked[0] = 0;
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, GeneNumbers.SUN_WATER_SLOW, 0));
                say(e, "message.headinajar.gene.husk_water");
            }
        }
        // Rampage: a streak ends after three quiet seconds; while it lasts you cannot sneak
        long[] streak = STREAKS.get(e);
        if (streak != null) {
            if (now - streak[0] > GeneNumbers.RAMPAGE_WINDOW) STREAKS.remove(e);
            else if (streak[1] >= 2 && e instanceof Player p && p.isShiftKeyDown()) p.setShiftKeyDown(false);
        }
        // Snow Trail: snow where a snow golem would leave it; you melt in hot places, the Nether and the rain
        if (GeneEffects.has(e, Gene.SNOW_TRAIL)) snowTrail(e, level);
        // Gold Standard: gold on the ground in sight within 8 makes you stop to look
        if (GeneEffects.has(e, Gene.GOLD_STANDARD) && e.tickCount % GeneNumbers.GOLD_EVERY == 0) {
            boolean seen = !level.getEntitiesOfClass(ItemEntity.class, e.getBoundingBox().inflate(GeneNumbers.GOLD_SIGHT),
                    i -> i.getItem().is(ItemTags.PIGLIN_LOVED) && e.hasLineOfSight(i)).isEmpty();
            GOLD_IN_SIGHT.put(e, seen);
        }
    }

    /** A port of vanilla's {@code Mob.isSunBurnTick} for any body (players and mecha have none of their own). */
    private static boolean sunBurnTick(LivingEntity e, ServerLevel level) {
        if (!level.isDay()) return false;
        float light = e.getLightLevelDependentMagicValue();
        BlockPos eyes = BlockPos.containing(e.getX(), e.getEyeY(), e.getZ());
        boolean wet = e.isInWaterRainOrBubble() || e.isInPowderSnow || e.wasInPowderSnow;
        return light > 0.5f && e.getRandom().nextFloat() * 30 < (light - 0.4f) * 2 && !wet && level.canSeeSky(eyes);
    }

    /** Where a snow golem would melt (and Snow Trail leaves no snow): hot biomes and the Nether. */
    private static boolean hot(ServerLevel level, BlockPos at) {
        return level.dimensionType().ultraWarm() || level.getBiome(at).is(BiomeTags.SNOW_GOLEM_MELTS);
    }

    /** Snow Trail: a port of the snow golem's trail ({@code SnowGolem.aiStep}), once per new block, with claims. */
    private static void snowTrail(LivingEntity e, ServerLevel level) {
        BlockPos at = e.blockPosition();
        if (e.tickCount % GeneNumbers.SNOW_MELT_EVERY == 0 && (hot(level, at) || level.isRainingAt(at.above()))) {
            e.hurt(level.damageSources().onFire(), GeneNumbers.SNOW_MELT);
        }
        if (at.equals(TRAIL.put(e, at)) || !e.onGround() || hot(level, at)) return;
        BlockState snow = Blocks.SNOW.defaultBlockState();
        for (int i = 0; i < 4; i++) {
            int x = Mth.floor(e.getX() + (i % 2 * 2 - 1) * 0.25f);
            int y = Mth.floor(e.getY());
            int z = Mth.floor(e.getZ() + (i / 2 % 2 * 2 - 1) * 0.25f);
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getBlockState(pos).isAir() && snow.canSurvive(level, pos) && GeneClaims.mayChange(e, pos)) {
                level.setBlockAndUpdate(pos, snow);
                level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(e, snow));
            }
        }
    }

    // --- what the mixins ask -----------------------------------------------------------------------------------------

    /** Undeath: Instant Damage heals this body and Instant Health hurts it ({@code LivingEntity.isInvertedHealAndHarm}). */
    public static boolean invertsHealAndHarm(LivingEntity e) {
        return has(e, Gene.UNDEATH);
    }

    /**
     * What piglins make of this body's gold ({@code PiglinAi.isWearingGold}): true for Gold Standard, false for Brute
     * Strength (piglins are hostile to a brute even in gold; it wins over Gold Standard), null to let vanilla look.
     */
    public static Boolean piglinsSeeGold(LivingEntity e) {
        if (!carries(e)) return null;
        if (GeneEffects.has(e, Gene.BRUTE_STRENGTH)) return Boolean.FALSE;
        return GeneEffects.has(e, Gene.GOLD_STANDARD) ? Boolean.TRUE : null;
    }

    /** Brute Strength: a piglin will not pick up what a brute threw ({@code PiglinAi.wantsToPickup}). */
    public static boolean spurned(ItemStack stack) {
        synchronized (SPURNED) {
            return !SPURNED.isEmpty() && SPURNED.contains(stack);
        }
    }

    /** Pearl Digestion: an enderman that can see this player attacks it ({@code EnderMan.isLookingAtMe}). */
    public static boolean provokesEndermen(Player player, Entity enderman) {
        return has(player, Gene.PEARL_DIGESTION) && player.hasLineOfSight(enderman);
    }

    /** Gilded Rot: a golden sword or axe loses no durability on the undead ({@code ItemStack.postHurtEnemy}). */
    public static boolean keepsGoldEdge(ItemStack weapon, LivingEntity target, LivingEntity attacker) {
        return (weapon.is(Items.GOLDEN_SWORD) || weapon.is(Items.GOLDEN_AXE)) && target.getType().is(EntityTypeTags.UNDEAD) && has(attacker, Gene.GILDED_ROT);
    }

    /** Rehabilitated: the cured-zombie reputation this player is owed (0: none, or suspended for hitting a villager). */
    public static int rehabilitatedReputation(Player player) {
        if (!has(player, Gene.REHABILITATED)) return 0;
        if (player.getPersistentData().getLong(REHAB_SUSPENDED) > player.level().getGameTime()) return 0;
        return (int) scaled(player, Gene.REHABILITATED, GeneNumbers.REHAB_REPUTATION);
    }

    /** Rehabilitated, at the tail of {@code Villager.updateSpecialPrices}: the discount of a villager this player cured. */
    public static void rehabilitatedPrices(Villager villager, Player player) {
        int reputation = rehabilitatedReputation(player);
        if (reputation <= 0) return;
        for (MerchantOffer offer : villager.getOffers()) offer.addToSpecialPriceDiff(-Mth.floor(reputation * offer.getPriceMultiplier()));
    }

    // --- numbers the events use --------------------------------------------------------------------------------------

    /** How much faster than vanilla this body draws {@code stack} (1: unchanged): Bowstring Arms, Steady Aim. */
    static double drawSpeed(LivingEntity e, ItemStack stack) {
        if (!carries(e) || !GeneEffects.active(e)) return 1;
        double f = 1;
        if (stack.getItem() instanceof BowItem) {
            if (GeneEffects.has(e, Gene.BOWSTRING_ARMS)) f *= 20.0 / (20 - GeneNumbers.BOWSTRING_SKIP);
            if (GeneEffects.has(e, Gene.STEADY_AIM)) f /= 1 + GeneNumbers.STEADY_BOW_SLOW;
        } else if (stack.getItem() instanceof CrossbowItem && GeneEffects.has(e, Gene.STEADY_AIM)) {
            f /= 1 - GeneNumbers.STEADY_LOAD;
        }
        return f;
    }

    /** Rampage: the bonus of this hit (before scaling) and the streak it makes (once per melee hit). */
    private static float rampage(LivingEntity attacker, long now) {
        long[] streak = STREAKS.get(attacker);
        if (streak == null || now - streak[0] > GeneNumbers.RAMPAGE_WINDOW) streak = new long[]{now, 0};
        streak[0] = now;
        streak[1]++;
        STREAKS.put(attacker, streak);
        float bonus = Math.min(GeneNumbers.RAMPAGE_MAX, streak[1] - 1);
        if (bonus > 0) {
            puff(attacker, ParticleTypes.ANGRY_VILLAGER, 1);
            if (attacker.level() instanceof ServerLevel level) {
                Fx.send(level, Fx.of(FX_RAMPAGE, attacker.position(), null, attacker.getId(), -1, GeneNumbers.RAMPAGE_WINDOW));
            }
        }
        return bonus;
    }

    private static boolean onStreak(LivingEntity e, long now) {
        long[] streak = STREAKS.get(e);
        return streak != null && streak[1] >= 2 && now - streak[0] <= GeneNumbers.RAMPAGE_WINDOW;
    }

    /** Zombies, husks, drowned and zombie villagers (not zombified piglins: Gilded Rot's business). */
    private static boolean isZombie(Entity e) {
        return e instanceof Zombie && !(e instanceof ZombifiedPiglin);
    }

    /** Puts an effect back on at {@code factor} times its length (the change is not made again on the way back on). */
    private static void lengthen(LivingEntity e, MobEffectInstance effect, float factor, Entity source) {
        int duration = Math.max(1, Math.round(effect.getDuration() * factor));
        readding = true;
        try {
            e.addEffect(new MobEffectInstance(effect.getEffect(), duration, effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon()), source);
        } finally {
            readding = false;
        }
    }

    /** Potion Tolerance's drunk potions (and Herbalist's suspicious stew): every effect again, half as long again. */
    private static void tolerate(LivingEntity e, Iterable<MobEffectInstance> effects) {
        float factor = 1 + scaled(e, Gene.POTION_TOLERANCE, GeneNumbers.POTION_LONGER - 1);
        for (MobEffectInstance effect : effects) {
            if (effect.getEffect().value().isInstantenous() || effect.isInfiniteDuration()) continue;
            e.addEffect(new MobEffectInstance(effect.getEffect(), Math.round(effect.getDuration() * factor), effect.getAmplifier(),
                    effect.isAmbient(), effect.isVisible(), effect.showIcon()));
        }
    }

    /** Stone, deepslate and cobblestone (Stone Lungs). */
    static boolean stony(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Tags.Blocks.COBBLESTONES);
    }

    /** The handlers (on the game's bus; {@link #register}). */
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void damage(LivingIncomingDamageEvent event) {
            LivingEntity victim = event.getEntity();
            if (victim.level().isClientSide) return;
            DamageSource source = event.getSource();
            long now = victim.level().getGameTime();
            Entity cause = source.getEntity();
            Entity direct = source.getDirectEntity();
            if (carries(victim) && GeneEffects.active(victim)) {
                // Stone Lungs: you never suffocate inside blocks
                if (source.is(DamageTypes.IN_WALL) && GeneEffects.has(victim, Gene.STONE_LUNGS)) {
                    event.setCanceled(true);
                    return;
                }
                // Undeath's cost: Smite hurts you as it hurts a zombie
                ItemStack weapon = source.getWeaponItem();
                if (weapon != null && !weapon.isEmpty() && GeneEffects.has(victim, Gene.UNDEATH) && !victim.getType().is(EntityTypeTags.SENSITIVE_TO_SMITE)) {
                    Holder<Enchantment> smite = victim.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SMITE);
                    int level = EnchantmentHelper.getItemEnchantmentLevel(smite, weapon);
                    if (level > 0) event.setAmount(event.getAmount() + 2.5f * level);
                }
                // Rime Arrows' cost: fire burns you 20% harder
                if (source.is(DamageTypeTags.IS_FIRE) && GeneEffects.has(victim, Gene.RIME_ARROWS)) event.setAmount(event.getAmount() * GeneNumbers.RIME_FIRE_HARDER);
                // Rampage's cost: on a streak you take 15% more
                if (GeneEffects.has(victim, Gene.RAMPAGE) && onStreak(victim, now)) event.setAmount(event.getAmount() * GeneNumbers.RAMPAGE_TAKEN);
            }
            if (!(cause instanceof LivingEntity attacker) || !carries(attacker) || !GeneEffects.active(attacker)) return;
            // Undeath: a zombie you strike may fight you from now on
            if (isZombie(victim) && GeneEffects.has(attacker, Gene.UNDEATH)) {
                CompoundTag data = victim.getPersistentData();
                CompoundTag grudges = data.getCompound(GRUDGES);
                grudges.putBoolean(attacker.getUUID().toString(), true);
                data.put(GRUDGES, grudges);
            }
            // Rehabilitated's cost: hit a villager and the discount is gone for a day
            if (victim instanceof Villager && attacker instanceof Player p && GeneEffects.has(attacker, Gene.REHABILITATED)) {
                p.getPersistentData().putLong(REHAB_SUSPENDED, now + GeneNumbers.REHAB_FORGET);
                say(p, "message.headinajar.gene.rehab_suspended");
            }
            if (direct == attacker && !source.is(DamageTypeTags.IS_PROJECTILE)) {
                ItemStack hand = attacker.getMainHandItem();
                // Sun-Baked: your melee hits leave players hungry and creatures weak
                if (GeneEffects.has(attacker, Gene.SUN_BAKED)) {
                    if (victim instanceof Player) victim.addEffect(new MobEffectInstance(MobEffects.HUNGER, GeneNumbers.SUN_HUNGER, 0), attacker);
                    else victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, GeneNumbers.SUN_WEAKNESS, 0), attacker);
                }
                float extra = 0;
                if (GeneEffects.has(attacker, Gene.RAMPAGE)) extra += scaled(attacker, Gene.RAMPAGE, rampage(attacker, now));
                if (hand.is(ItemTags.AXES)) {
                    if (GeneEffects.has(attacker, Gene.BRUTE_STRENGTH)) extra += scaled(attacker, Gene.BRUTE_STRENGTH, GeneNumbers.BRUTE_AXE);
                    if (GeneEffects.has(attacker, Gene.BREAKING_AND_ENTERING)) extra += scaled(attacker, Gene.BREAKING_AND_ENTERING, GeneNumbers.BREAKING_AXE);
                }
                if ((hand.is(Items.GOLDEN_SWORD) || hand.is(Items.GOLDEN_AXE)) && victim.getType().is(EntityTypeTags.UNDEAD) && GeneEffects.has(attacker, Gene.GILDED_ROT)) {
                    extra += scaled(attacker, Gene.GILDED_ROT, GeneNumbers.GILDED_DAMAGE);
                }
                if (extra > 0) event.setAmount(event.getAmount() + extra);
            } else if (direct instanceof AbstractArrow) {
                // Bowstring Arms: +1 at impact; Rime and Spore Arrows coat what they hit
                if (GeneEffects.has(attacker, Gene.BOWSTRING_ARMS)) event.setAmount(event.getAmount() + scaled(attacker, Gene.BOWSTRING_ARMS, GeneNumbers.BOWSTRING_DAMAGE));
                if (GeneEffects.has(attacker, Gene.RIME_ARROWS)) {
                    victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, GeneNumbers.RIME_SLOW, 1), attacker);
                    puff(victim, ParticleTypes.SNOWFLAKE, 12);
                }
                if (GeneEffects.has(attacker, Gene.SPORE_ARROWS)) {
                    victim.addEffect(new MobEffectInstance(MobEffects.POISON, GeneNumbers.SPORE_POISON, 0), attacker);
                    puff(victim, ParticleTypes.SPORE_BLOSSOM_AIR, 12);
                }
            }
        }

        /** Undeath: zombies leave you alone until you strike one. Gilded Rot: a zombified piglin you hit fights you alone. */
        @SubscribeEvent
        public static void target(LivingChangeTargetEvent event) {
            LivingEntity target = event.getNewAboutToBeSetTarget();
            if (target == null || !carries(target)) return;
            LivingEntity mob = event.getEntity();
            if (isZombie(mob) && GeneEffects.has(target, Gene.UNDEATH)
                    && !mob.getPersistentData().getCompound(GRUDGES).getBoolean(target.getUUID().toString())) {
                event.setCanceled(true);
            } else if (mob instanceof ZombifiedPiglin && mob.getLastHurtByMob() != target && GeneEffects.has(target, Gene.GILDED_ROT)) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void effect(MobEffectEvent.Applicable event) {
            if (readding) return;
            LivingEntity e = event.getEntity();
            if (!carries(e) || !GeneEffects.active(e)) return;
            MobEffectInstance effect = event.getEffectInstance();
            Holder<MobEffect> type = effect.getEffect();
            // Undeath: Poison and Regeneration do nothing; Brute Strength: nor does Weakness
            if (GeneEffects.has(e, Gene.UNDEATH) && (is(type, MobEffects.POISON) || is(type, MobEffects.REGENERATION))
                    || GeneEffects.has(e, Gene.BRUTE_STRENGTH) && is(type, MobEffects.WEAKNESS)) {
                event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
                return;
            }
            if (type.value().isInstantenous() || effect.isInfiniteDuration()) return;
            float factor = 1;
            // Spore Arrows' cost: Poison on you lasts 50% longer
            if (GeneEffects.has(e, Gene.SPORE_ARROWS) && is(type, MobEffects.POISON)) factor *= GeneNumbers.SPORE_POISON_LONGER;
            // Potion Tolerance: harmful potions splashed on you last half as long
            if (GeneEffects.has(e, Gene.POTION_TOLERANCE) && type.value().getCategory() == MobEffectCategory.HARMFUL
                    && e.level() == splashLevel && e.level().getGameTime() == splashTick) {
                factor *= 1 - scaled(e, Gene.POTION_TOLERANCE, 1 - GeneNumbers.POTION_SPLASH_SHARE);
            }
            if (factor == 1) return;
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            lengthen(e, effect, factor, event.getEffectSource());
        }

        /** Potion Tolerance's cost: milk does nothing for you. */
        @SubscribeEvent
        public static void cured(MobEffectEvent.Remove event) {
            if (event.getCure() == EffectCures.MILK && has(event.getEntity(), Gene.POTION_TOLERANCE)) event.setCanceled(true);
        }

        @SubscribeEvent
        public static void ate(LivingEntityUseItemEvent.Finish event) {
            LivingEntity e = event.getEntity();
            if (e.level().isClientSide || !(e instanceof Player p) || !carries(e) || !GeneEffects.active(e)) return;
            ItemStack stack = event.getItem();
            boolean apple = stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE);
            // Gold Standard: golden carrots and golden apples give half as much again
            if ((apple || stack.is(Items.GOLDEN_CARROT)) && GeneEffects.has(e, Gene.GOLD_STANDARD)) {
                float share = scaled(e, Gene.GOLD_STANDARD, GeneNumbers.GOLD_FOOD - 1);
                FoodProperties food = stack.getFoodProperties(e);
                if (food != null) {
                    int more = Math.max(1, Math.round(food.nutrition() * share));
                    p.getFoodData().eat(more, food.saturation() * share / (2f * more));
                    for (FoodProperties.PossibleEffect possible : food.effects()) {
                        MobEffectInstance it = possible.effect();
                        e.addEffect(new MobEffectInstance(it.getEffect(), Math.round(it.getDuration() * (1 + share)), it.getAmplifier(), it.isAmbient(), it.isVisible(), it.showIcon()));
                    }
                }
            }
            // Rehabilitated's cost: a golden apple sickens you
            if (apple && GeneEffects.has(e, Gene.REHABILITATED)) e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, GeneNumbers.REHAB_NAUSEA, 0));
            if (GeneEffects.has(e, Gene.POTION_TOLERANCE)) {
                PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
                if (stack.is(Items.POTION) && potion != null) tolerate(e, potion.getAllEffects());
                // Herbalist (with Stew Glands in the same serum): suspicious stew counts as a potion
                SuspiciousStewEffects stew = stack.get(DataComponents.SUSPICIOUS_STEW_EFFECTS);
                if (stack.is(Items.SUSPICIOUS_STEW) && stew != null && sameSerum(e, Gene.POTION_TOLERANCE, Gene.STEW_GLANDS)) {
                    tolerate(e, stew.effects().stream().map(SuspiciousStewEffects.Entry::createEffectInstance).toList());
                }
                // the cost says itself: the bucket is drunk, nothing is cured
                if (stack.is(Items.MILK_BUCKET)) say(e, "message.headinajar.gene.milk");
            }
        }

        /** Bowstring Arms and Steady Aim: the draw starts (both sides). */
        @SubscribeEvent
        public static void drawStart(LivingEntityUseItemEvent.Start event) {
            LivingEntity e = event.getEntity();
            Map<Entity, int[]> draws = e.level().isClientSide ? DRAW_CLIENT : DRAW_SERVER;
            if (drawSpeed(e, event.getItem()) != 1) draws.put(e, new int[1]);
            else draws.remove(e);
        }

        /**
         * Bowstring Arms (a bow draws in 14 ticks, not 20), Steady Aim (a crossbow loads 40% faster, a bow draws 20%
         * slower): the use timer is set to where it would be at the new speed, both sides, so the pull drawn matches.
         */
        @SubscribeEvent
        public static void drawTick(LivingEntityUseItemEvent.Tick event) {
            LivingEntity e = event.getEntity();
            int[] drawn = (e.level().isClientSide ? DRAW_CLIENT : DRAW_SERVER).get(e);
            if (drawn == null || drawn[0] > 60) return;
            double f = drawSpeed(e, event.getItem());
            if (f == 1) return;
            int n = ++drawn[0];
            event.setDuration(event.getItem().getUseDuration(e) - (int) Math.floor(n * f + 1e-4) + 1);
        }

        @SubscribeEvent
        public static void joined(EntityJoinLevelEvent event) {
            if (event.getLevel().isClientSide) return;
            Entity e = event.getEntity();
            // Bowstring Arms' cost: wild wolves hunt you
            if (e instanceof Wolf wolf) {
                wolf.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(wolf, LivingEntity.class, 10, true, false,
                        t -> !wolf.isTame() && has(t, Gene.BOWSTRING_ARMS)));
                return;
            }
            if (event.loadedFromDisk()) return;
            // Steady Aim: bolts fly a quarter faster (and so hit about a quarter harder)
            if (e instanceof AbstractArrow arrow && arrow.getOwner() instanceof LivingEntity owner && has(owner, Gene.STEADY_AIM)
                    && arrow.getWeaponItem() != null && arrow.getWeaponItem().getItem() instanceof CrossbowItem) {
                arrow.setDeltaMovement(arrow.getDeltaMovement().scale(1 + scaled(owner, Gene.STEADY_AIM, (float) (GeneNumbers.STEADY_BOLT - 1))));
            }
            // Pearl Digestion: a pearl flies further, and its cooldown is shorter
            if (e instanceof ThrownEnderpearl pearl && pearl.getOwner() instanceof LivingEntity owner && has(owner, Gene.PEARL_DIGESTION)) {
                pearl.setDeltaMovement(pearl.getDeltaMovement().scale(1 + scaled(owner, Gene.PEARL_DIGESTION, (float) (GeneNumbers.PEARL_SPEED - 1))));
                if (owner instanceof Player p) {
                    int cooldown = Math.max(1, Math.round(20 * (1 - scaled(owner, Gene.PEARL_DIGESTION, 1 - GeneNumbers.PEARL_COOLDOWN_SHARE))));
                    p.getCooldowns().addCooldown(Items.ENDER_PEARL, cooldown);
                }
            }
        }

        /** Pearl Digestion: a pearl does you no harm. */
        @SubscribeEvent
        public static void pearl(EntityTeleportEvent.EnderPearl event) {
            if (has(event.getPlayer(), Gene.PEARL_DIGESTION)) event.setAttackDamage(0);
        }

        @SubscribeEvent
        public static void impact(ProjectileImpactEvent event) {
            Entity projectile = event.getProjectile();
            if (!(projectile.level() instanceof ServerLevel level)) return;
            // Potion Tolerance: a splash potion lands this tick (its harmful effects on a carrier are halved)
            if (projectile instanceof ThrownPotion) {
                splashLevel = level;
                splashTick = level.getGameTime();
                return;
            }
            // Snow Trail: your snowballs do 2 (blazes take vanilla's 3) and slow for 2 s
            if (projectile instanceof Snowball ball && ball.getOwner() instanceof LivingEntity owner && has(owner, Gene.SNOW_TRAIL)
                    && event.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity target && target != owner) {
                if (!(target instanceof Blaze)) target.hurt(level.damageSources().thrown(ball, owner), scaled(owner, Gene.SNOW_TRAIL, GeneNumbers.SNOW_BALL));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, GeneNumbers.SNOW_BALL_SLOW, 0), owner);
            }
        }

        /** Gilded Rot's cost: gold armour you wear wears out twice as fast. */
        @SubscribeEvent
        public static void armour(ArmorHurtEvent event) {
            if (!has(event.getEntity(), Gene.GILDED_ROT)) return;
            for (var entry : event.getArmorMap().entrySet()) {
                ItemStack stack = entry.getValue().armorItemStack;
                if (stack.getItem() instanceof ArmorItem armor && armor.getMaterial().is(ArmorMaterials.GOLD)) {
                    event.setNewDamage(entry.getKey(), event.getNewDamage(entry.getKey()) * 2);
                }
            }
        }

        /** Breaking and Entering: wooden doors, trapdoors and gates in one axe hit. Stone Lungs: stone mines faster. */
        @SubscribeEvent
        public static void breakSpeed(PlayerEvent.BreakSpeed event) {
            Player p = event.getEntity();
            if (!carries(p) || !GeneEffects.active(p)) return;
            BlockState state = event.getState();
            if (GeneEffects.has(p, Gene.BREAKING_AND_ENTERING) && p.getMainHandItem().is(ItemTags.AXES)
                    && (state.is(BlockTags.WOODEN_DOORS) || state.is(BlockTags.WOODEN_TRAPDOORS) || state.is(BlockTags.FENCE_GATES))) {
                event.setNewSpeed(Math.max(event.getNewSpeed(), 10_000));
            } else if (GeneEffects.has(p, Gene.STONE_LUNGS) && stony(state)) {
                event.setNewSpeed(event.getNewSpeed() * (1 + scaled(p, Gene.STONE_LUNGS, GeneNumbers.STONE_MINE - 1)));
            }
        }

        /** Stone Lungs' cost: one stone block in a hundred lets out a silverfish, which is yours now. */
        @SubscribeEvent
        public static void broke(BlockEvent.BreakEvent event) {
            if (!(event.getPlayer() instanceof ServerPlayer p) || p.isCreative() || !(event.getLevel() instanceof ServerLevel level)) return;
            if (!stony(event.getState()) || !has(p, Gene.STONE_LUNGS) || p.getRandom().nextInt(GeneNumbers.STONE_SILVERFISH_ODDS) != 0) return;
            Silverfish fish = EntityType.SILVERFISH.create(level);
            if (fish == null) return;
            BlockPos pos = event.getPos();
            fish.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, p.getRandom().nextFloat() * 360, 0);
            level.addFreshEntity(fish);
            fish.spawnAnim();
            com.coolfreeze23.headinajar.staff.Staff.register(fish, p, "stone_lungs", true);
            say(p, "message.headinajar.gene.silverfish");
        }

        /** Brute Strength: what a brute throws, a piglin will not pick up. */
        @SubscribeEvent
        public static void tossed(ItemTossEvent event) {
            if (!event.getPlayer().level().isClientSide && has(event.getPlayer(), Gene.BRUTE_STRENGTH)) {
                synchronized (SPURNED) {
                    SPURNED.add(event.getEntity().getItem());
                }
            }
        }

        /**
         * Brute Strength's cost: piglins never barter with you. Breaking and Entering's: villagers will not trade while you
         * hold an axe. Both refuse with their line.
         */
        @SubscribeEvent
        public static void interact(PlayerInteractEvent.EntityInteract event) {
            Player player = event.getEntity();
            if (!carries(player) || !GeneEffects.active(player)) return;
            String reason = null;
            if (event.getTarget() instanceof Piglin piglin && !piglin.isBaby() && GeneEffects.has(player, Gene.BRUTE_STRENGTH)
                    && (event.getItemStack().is(ItemTags.PIGLIN_LOVED) || event.getItemStack().isPiglinCurrency())) {
                reason = "gene.brute_strength.barter";
            } else if (event.getTarget() instanceof Villager && GeneEffects.has(player, Gene.BREAKING_AND_ENTERING)
                    && (player.getMainHandItem().is(ItemTags.AXES) || player.getOffhandItem().is(ItemTags.AXES))) {
                reason = "gene.breaking_and_entering.axe";
            }
            if (reason == null) return;
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            if (player instanceof ServerPlayer sp) Refusals.deny(sp, reason);
        }
    }
}

package com.coolfreeze23.headinajar.staff.work;

import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.mind.MobMotor;
import com.coolfreeze23.headinajar.mixin.QuirkPiglinAccessor;
import com.coolfreeze23.headinajar.mixin.QuirkVillagerAccessor;
import com.coolfreeze23.headinajar.mixin.VillagerAccessor;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffFile;
import com.coolfreeze23.headinajar.staff.StaffShake;
import com.coolfreeze23.headinajar.staff.StaffTable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Vanilla temperaments kept as job quirks (the staff file's {@code quirks}): none breaks a block or costs an item. The
 * jobs ask {@link #holds} before each step and hand {@link #taken} what a worker takes; {@link #think} runs at each think
 * of every worker and returns at once for a species with no quirk that thinks; the rest are events (the handlers below,
 * on the game bus through {@code WorkRules.register}).
 * <ul>
 * <li>Enderman, shy: stops while a player within 32 looks at it as vanilla's stare does ("Do you mind?").</li>
 * <li>Enderman, out of the rain: in rain or water it hops under its clock's awning and waits there while it rains,
 * taking no water damage; staff endermen never teleport by themselves.</li>
 * <li>Cat: idle, sits on a chest that is a stop on one of its site's cards (vanilla then reads it as blocked), then goes back.</li>
 * <li>Cat and ocelot: once a second, sends any creeper inside the site out past the nearest edge.</li>
 * <li>Piglin: skims one item in 64 into its pocket, and hands it back to an empty hand.</li>
 * <li>Piglin and brute: zombify after 300 ticks outside a piglin-safe dimension, as vanilla, and stay on staff.</li>
 * <li>Llama: unpaid, spits once a day at whoever opens its clock; the spit lands on nobody.</li>
 * <li>Panda: worried stops in thunder; lazy takes a break as long as each spell of work; playful rolls at its stops;
 * aggressive swats a hostile within 4.</li>
 * <li>Goat: now and then rams a colleague within 4: knockback only.</li>
 * <li>Chicken: lays into what it is carrying; carrying nothing, it lays as vanilla does.</li>
 * <li>Dolphin: below 30% air it surfaces before anything else, until its air is back.</li>
 * <li>Villager: no trading on the job; off the job it levels up as vanilla does and restocks once a dawn.</li>
 * <li>Wandering trader: never leaves (its despawn delay stays 0 while it is staff).</li>
 * <li>Iron golem: holds out a poppy to villager staff it passes.</li>
 * <li>Witch: every 600 ticks a splash potion of Regeneration at the most hurt colleague within 8, at no cost.</li>
 * </ul>
 */
public final class Quirks {
    /** How far a player's stare reaches an enderman. */
    public static final int STARE = 32;
    /** A dolphin surfaces below this share of its air. */
    public static final float SURFACE_AT = 0.3f;
    /** A piglin skims one item in this many. */
    public static final int SKIM = 64;
    /** Ticks outside the Nether before a piglin zombifies (vanilla's). */
    public static final int CONVERSION_TIME = 300;
    /** How far from whoever opens its clock an unpaid llama spits. */
    public static final int SPIT_RANGE = 24;
    /** Ticks between a witch's splashes, and how far it reaches. */
    public static final int SPLASH_TICKS = 600, SPLASH_RANGE = 8;
    /** Ticks between a cat's sweeps for creepers. */
    public static final int SWEEP_TICKS = 20;
    /** How far past the site's edge a creeper is sent. */
    public static final int SHOO = 4;
    /** A lazy panda's spell of work, and of rest after it. */
    public static final int LAZY_TICKS = 600;
    /** How far a goat rams, a golem offers its poppy and an aggressive panda swats. */
    public static final int NEAR = 4;
    /** How long a cat sits on a chest at least (up to twice that), and waits before the next (up to twice that). */
    public static final int SIT_TICKS = 600, SIT_AGAIN = 1200;

    /** The quirks that do something at a think (the others are events, or another part's). */
    private static final Set<String> THINKING = Set.of("shy", "out_of_the_rain", "sits_on_chests", "keeps_creepers_out", "zombifies",
            "personality", "rams", "eggs_into_carry", "surfaces", "no_trade_on_shift", "trader_stays", "poppy", "witch_regen");

    private static final Map<Mob, QuirkState> STATES = new WeakHashMap<>();
    /** Piglins whose time is up, converted after the server tick (never in the middle of their own). */
    private static final List<AbstractPiglin> CONVERTING = new ArrayList<>();

    private Quirks() {
    }

    // --- the contract -----------------------------------------------------------------------------------------------

    /** Whether a quirk holds this worker up this think (stared at, out of the rain, a cat on a chest, a dolphin surfacing...). */
    public static boolean holds(Mob mob, WorkerData d) {
        QuirkState q = STATES.get(mob);
        return q != null && q.holdKey != null && q.holdUntil >= mob.level().getGameTime();
    }

    /** A stack a worker has just taken from a Pick-Up, after any quirk (a piglin's skim). */
    public static ItemStack taken(Mob mob, WorkerData d, ItemStack stack) {
        if (stack.isEmpty() || d == null) return stack;
        WorkTable.Spec spec = WorkTable.of(mob);
        if (spec == null || !spec.quirk("pocket")) return stack;
        QuirkState q = state(mob);
        q.skimTally += stack.getCount();
        if (q.skimTally < SKIM) return stack;
        int skims = q.skimTally / SKIM;
        q.skimTally %= SKIM;
        // one stack in the pocket: nothing is skimmed that would not fit in it
        if (!d.pocket.isEmpty() && !ItemStack.isSameItemSameComponents(d.pocket, stack)) return stack;
        int room = d.pocket.isEmpty() ? stack.getMaxStackSize() : d.pocket.getMaxStackSize() - d.pocket.getCount();
        int n = Math.min(Math.min(skims, room), stack.getCount());
        if (n <= 0) return stack;
        ItemStack left = stack.copy();
        ItemStack skim = left.split(n);
        if (d.pocket.isEmpty()) d.pocket = skim;
        else d.pocket.grow(n);
        return left;
    }

    /** Each think of a worker, whatever it is doing, before its state's own step. */
    public static void think(Mob mob, WorkerData d) {
        if (d.state == WorkerData.State.NOTICE || !(mob.level() instanceof ServerLevel level)) return;
        WorkTable.Spec spec = WorkTable.of(mob);
        if (spec == null || !thinks(spec.quirks())) return;
        Set<String> has = spec.quirks();
        QuirkState q = state(mob);
        long now = level.getGameTime();
        String hold = null;
        if (mob instanceof EnderMan e) {
            if (has.contains("out_of_the_rain")) hold = outOfTheRain(e, d, level);
            if (hold == null && has.contains("shy")) hold = shy(e, level);
        }
        if (hold == null && mob instanceof Cat cat && has.contains("sits_on_chests")) hold = sitOnAChest(cat, d, q, level, now);
        if (has.contains("keeps_creepers_out")) keepCreepersOut(mob, d, q, level, now);
        if (mob instanceof AbstractPiglin piglin && has.contains("zombifies")) zombify(piglin);
        if (hold == null && mob instanceof Panda panda && has.contains("personality")) hold = personality(panda, d, q, level, now);
        if (mob instanceof Goat goat && has.contains("rams")) ram(goat, d, q, level, now);
        if (mob instanceof Chicken chicken && has.contains("eggs_into_carry")) layIntoTheCarry(chicken, d, spec);
        if (hold == null && has.contains("surfaces")) hold = surface(mob, q, level);
        if (mob instanceof Villager v && has.contains("no_trade_on_shift") && !onShift(d)) offShift(v, q, level);
        if (mob instanceof WanderingTrader trader && has.contains("trader_stays") && trader.getDespawnDelay() != 0) trader.setDespawnDelay(0);
        if (mob instanceof IronGolem golem && has.contains("poppy")) poppy(golem, q, level, now);
        if (mob instanceof Witch witch && has.contains("witch_regen")) splash(witch, q, level, now);
        q.hold(hold, now, Workforce.thinkTicks());
        if (hold != null) d.status = "staff.headinajar.say." + hold;
    }

    private static boolean thinks(Set<String> quirks) {
        for (String id : quirks) if (THINKING.contains(id)) return true;
        return false;
    }

    /** A worker's quirk state (made on first use). Tests reach its timers through it ({@link QuirkState#hurry}). */
    public static QuirkState state(Mob mob) {
        return STATES.computeIfAbsent(mob, m -> new QuirkState(m.level().getGameTime(), m.getRandom()));
    }

    /**
     * A worker comes off duty (Let go, the end of its record): a cat sitting on a chest or a panda on its break gets up,
     * a roll or a lowered head ends, and its quirk state is forgotten.
     */
    public static void offDuty(Mob mob) {
        QuirkState q = STATES.remove(mob);
        if (q == null) return;
        if (mob instanceof Cat cat && q.sitting) cat.setInSittingPose(false);
        if (mob instanceof Panda panda) {
            if (q.lazing) panda.sit(false);
            panda.roll(false);
        }
        if (mob instanceof Goat && q.ramTarget != null) mob.level().broadcastEntityEvent(mob, (byte) 59);
    }

    /** On the job, as the villager's "no trading on shift" reads it. */
    public static boolean onShift(WorkerData d) {
        return d.state == WorkerData.State.ON_JOB;
    }

    // --- the enderman -----------------------------------------------------------------------------------------------

    /** Shy: the first player within {@link #STARE} looking at it as vanilla's stare does stops it. */
    private static String shy(EnderMan e, ServerLevel level) {
        Player p = staring(e, level);
        if (p == null) return null;
        MobMotor.halt(e);
        e.getLookControl().setLookAt(p, 10, 10);
        Workforce.say(e, "do_you_mind");
        return "do_you_mind";
    }

    /** The first player looking at the enderman, or null (only the level's players count). */
    public static Player staring(EnderMan e, ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive() || p.distanceToSqr(e) > STARE * STARE) continue;
            if (looksAt(e, p)) return p;
        }
        return null;
    }

    /** Vanilla's stare ({@code EnderMan.isLookingAtMe}): within the cone, no carved pumpkin on, and in sight. */
    static boolean looksAt(EnderMan e, Player p) {
        Vec3 view = p.getViewVector(1.0F).normalize();
        Vec3 to = new Vec3(e.getX() - p.getX(), e.getEyeY() - p.getEyeY(), e.getZ() - p.getZ());
        double dist = to.length();
        if (dist < 1.0E-4 || view.dot(to.normalize()) <= 1.0 - 0.025 / dist) return false;
        if (CommonHooks.shouldSuppressEnderManAnger(e, p, p.getInventory().armor.get(3))) return false;
        return p.hasLineOfSight(e);
    }

    /** Out of the rain: wet, it hops under its clock's awning ("Out of the rain.") and waits there while it rains. */
    private static String outOfTheRain(EnderMan e, WorkerData d, ServerLevel level) {
        boolean wet = e.isInWaterRainOrBubble();
        if (WorkRules.underAwning(e, d)) {
            if (!wet && !raining(level, e.blockPosition())) return null;
            MobMotor.halt(e);
            return "out_of_rain";
        }
        if (!wet) return null;
        MobMotor.halt(e);
        Workforce.say(e, "out_of_rain");
        hopUnderTheAwning(e, d, level);
        return "out_of_rain";
    }

    private static boolean raining(ServerLevel level, BlockPos p) {
        return level.isRaining() && level.getBiome(p).value().getPrecipitationAt(p) == Biome.Precipitation.RAIN;
    }

    /** A hop (vanilla's own teleport, sound and particles) to a standable spot within 2 of its clock, a dry one first. */
    static boolean hopUnderTheAwning(EnderMan e, WorkerData d, ServerLevel level) {
        if (!Workforce.clockHere(e, d) || !level.isLoaded(d.clock.pos())) return false;
        BlockPos c = d.clock.pos();
        List<BlockPos> dry = new ArrayList<>(), wet = new ArrayList<>();
        int r = WorkRules.AWNING;
        for (int dy : new int[]{0, -1, 1, -2, 2}) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = c.offset(dx, dy, dz);
                    if (standable(level, p, 3)) (raining(level, p) ? wet : dry).add(p);
                }
            }
        }
        dry.addAll(wet);
        Vec3 from = e.position();
        for (BlockPos p : dry) {
            if (!e.randomTeleport(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, true)) continue;
            level.gameEvent(GameEvent.TELEPORT, from, GameEvent.Context.of(e));
            if (!e.isSilent()) {
                level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, e.getSoundSource(), 1.0F, 1.0F);
                e.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
            }
            return true;
        }
        return false;
    }

    /** Solid ground under it, and {@code tall} blocks of open, dry air from it up. */
    static boolean standable(ServerLevel level, BlockPos p, int tall) {
        if (!level.isLoaded(p) || !level.getBlockState(p.below()).blocksMotion()) return false;
        for (int i = 0; i < tall; i++) {
            BlockPos a = p.above(i);
            if (!level.getFluidState(a).isEmpty() || !level.getBlockState(a).getCollisionShape(level, a).isEmpty()) return false;
        }
        return true;
    }

    /** Staff endermen never teleport by themselves (hurt, in daylight, by a projectile); the mod's hop is not this event. */
    @SubscribeEvent
    public static void enderTeleport(EntityTeleportEvent.EnderEntity event) {
        if (Workforce.isWorker(event.getEntityLiving())) event.setCanceled(true);
    }

    /** Under its awning, rain and water do not hurt a staff enderman. */
    @SubscribeEvent
    public static void awning(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof EnderMan e) || !event.getSource().is(DamageTypes.DROWN)) return;
        WorkerData d = Workforce.data(e);
        if (d != null && WorkRules.underAwning(e, d)) event.setCanceled(true);
    }

    // --- the cat ----------------------------------------------------------------------------------------------------

    /** Whether a cat has nothing else to do: at its own site, no card, no task, not on a detail. */
    private static boolean idle(Mob mob, WorkerData d) {
        return (d.state == WorkerData.State.TO_CLOCK || d.state == WorkerData.State.OFF_SHIFT || d.state == WorkerData.State.ON_JOB)
                && d.card.isEmpty() && d.task == null && d.rackSlot < 0 && Workforce.clockHere(mob, d);
    }

    /**
     * A cat with nothing to do picks a chest that is a stop on one of its site's cards, walks beside it, hops up and sits
     * (vanilla then reads the chest as blocked), gets up after a while and leaves the rest to its site. It is held all the
     * while, so the site leaves it be.
     */
    private static String sitOnAChest(Cat cat, WorkerData d, QuirkState q, ServerLevel level, long now) {
        if (q.chest != null) {
            boolean still = idle(cat, d) && level.isLoaded(q.chest) && level.getBlockState(q.chest).getBlock() instanceof ChestBlock;
            if (!still || now >= q.sitUntil) {
                getUp(cat, q, now);
                return null;
            }
            if (q.sitting) {
                cat.setInSittingPose(true);
                return "cat_sits";
            }
            if (onTop(cat, q.chest)) {
                MobMotor.halt(cat);
                cat.setInSittingPose(true);
                q.sitting = true;
                q.sitUntil = now + SIT_TICKS + cat.getRandom().nextInt(SIT_TICKS);
                Workforce.say(cat, "cat_sits");
                return "cat_sits";
            }
            Vec3 top = Vec3.atBottomCenterOf(q.chest).add(0, 0.875, 0);
            if (cat.position().subtract(top).horizontalDistanceSqr() < 2.25) {
                // beside it: a hop up, as a cat does (and up it goes after a few tries, as cats do)
                MobMotor.halt(cat);
                if (++q.hops > 3) cat.moveTo(top.x, top.y, top.z, cat.getYRot(), cat.getXRot());
                else cat.getMoveControl().setWantedPosition(top.x, top.y, top.z, 1.0);
                return "cat_sits";
            }
            BlockPos beside = beside(level, cat, q.chest);
            if (beside != null && (WorkerPaths.arrived(cat, beside) || cat.position().subtract(top).horizontalDistanceSqr() < 9)) {
                // the path has brought it beside the chest (as near as a path goes): the last stretch to the chest's top
                // by its own move control, a few thinks at most, then the hop up
                WorkerPaths.stop(cat);
                if (++q.hops > 3) cat.moveTo(top.x, top.y, top.z, cat.getYRot(), cat.getXRot());
                else cat.getMoveControl().setWantedPosition(top.x, top.y, top.z, 1.0);
                return "cat_sits";
            }
            if (MobMotor.idle(cat)) {
                WorkerPaths.Result r = beside == null ? WorkerPaths.Result.FAILED
                        : WorkerPaths.go(cat, d, beside, 1.0, Workforce.box(d.clock.pos()), false);
                if (r == WorkerPaths.Result.FAILED) {
                    getUp(cat, q, now);
                    return null;
                }
            }
            return "cat_sits";
        }
        if (now < q.nextSit || !idle(cat, d) || !MobMotor.idle(cat)) return null;
        q.nextSit = now + SIT_AGAIN / 2;
        BlockPos chest = aChestOnTheCards(cat, d, level);
        if (chest == null) return null;
        q.chest = chest;
        q.sitting = false;
        q.hops = 0;
        q.sitUntil = now + 400; // the time it has to get there
        return "cat_sits";
    }

    private static void getUp(Cat cat, QuirkState q, long now) {
        if (q.sitting) cat.setInSittingPose(false);
        q.chest = null;
        q.sitting = false;
        q.nextSit = now + SIT_AGAIN + cat.getRandom().nextInt(SIT_AGAIN);
    }

    private static boolean onTop(Mob mob, BlockPos chest) {
        return mob.getY() >= chest.getY() + 0.75 && mob.getY() < chest.getY() + 1.5
                && Mth.floor(mob.getX()) == chest.getX() && Mth.floor(mob.getZ()) == chest.getZ();
    }

    /** A standable spot beside a chest, the nearest to the cat. */
    private static BlockPos beside(ServerLevel level, Mob mob, BlockPos chest) {
        BlockPos best = null;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos p = chest.relative(dir);
            if (standable(level, p, 1) && (best == null || mob.distanceToSqr(Vec3.atCenterOf(p)) < mob.distanceToSqr(Vec3.atCenterOf(best)))) best = p;
        }
        return best;
    }

    /** A chest stop on one of its site's rack cards, open on top and with no cat on it yet (random among them), or null. */
    private static BlockPos aChestOnTheCards(Mob cat, WorkerData d, ServerLevel level) {
        TimeClockBlockEntity clock = Sites.clock(level, d.clock);
        if (clock == null) return null;
        List<BlockPos> chests = new ArrayList<>();
        for (int i = 0; i < TimeClockBlockEntity.RACK; i++) {
            Order.Card card = Order.card(clock.rack().getStackInSlot(i));
            if (card == null) continue;
            for (Order.Stop stop : card.order().stops()) {
                BlockPos p = stop.pos();
                if (chests.contains(p) || !Workforce.inBox(clock.getBlockPos(), p) || !level.isLoaded(p)) continue;
                if (!(level.getBlockState(p).getBlock() instanceof ChestBlock) || ChestBlock.isChestBlockedAt(level, p)) continue;
                BlockPos up = p.above();
                if (!level.getBlockState(up).getCollisionShape(level, up).isEmpty()) continue;
                chests.add(p);
            }
        }
        return chests.isEmpty() ? null : chests.get(cat.getRandom().nextInt(chests.size()));
    }

    /** Once a second at most, one box query: each creeper inside the site is pointed past its nearest edge. */
    private static void keepCreepersOut(Mob cat, WorkerData d, QuirkState q, ServerLevel level, long now) {
        if (now < q.nextSweep || !Workforce.clockHere(cat, d)) return;
        q.nextSweep = now + SWEEP_TICKS;
        BlockPos c = d.clock.pos();
        for (Creeper creeper : level.getEntitiesOfClass(Creeper.class, Workforce.box(c), cr -> cr.isAlive() && !Workforce.isWorker(cr))) {
            showOut(creeper, c);
        }
    }

    /** Points a creeper's navigation past the nearest edge of the site of the clock at {@code c}, its fuse put out. */
    public static void showOut(Creeper creeper, BlockPos c) {
        int r = Workforce.radius() + SHOO;
        double dx = creeper.getX() - (c.getX() + 0.5), dz = creeper.getZ() - (c.getZ() + 0.5);
        double x = creeper.getX(), z = creeper.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) x = c.getX() + 0.5 + Math.copySign(r, dx == 0 ? 1 : dx);
        else z = c.getZ() + 0.5 + Math.copySign(r, dz);
        creeper.setSwellDir(-1);
        creeper.getNavigation().moveTo(x, creeper.getY(), z, 1.2);
    }

    // --- the piglin -------------------------------------------------------------------------------------------------

    /** Counts its time outside a piglin-safe dimension as vanilla does; past 300 it is converted after this tick. */
    private static void zombify(AbstractPiglin piglin) {
        QuirkPiglinAccessor a = (QuirkPiglinAccessor) piglin;
        if (!piglin.isConverting()) {
            a.headinajar$setTimeInOverworld(0);
            return;
        }
        int t = a.headinajar$timeInOverworld() + Workforce.thinkTicks();
        a.headinajar$setTimeInOverworld(t);
        if (t > CONVERSION_TIME && !CONVERTING.contains(piglin)
                && EventHooks.canLivingConvert(piglin, EntityType.ZOMBIFIED_PIGLIN, a::headinajar$setTimeInOverworld)) {
            CONVERTING.add(piglin);
        }
    }

    /** Converts the piglins whose time is up, as vanilla's {@code finishConversion} does (the record follows: WorkRules). */
    @SubscribeEvent
    public static void afterTheTick(ServerTickEvent.Post event) {
        if (CONVERTING.isEmpty()) return;
        List<AbstractPiglin> now = new ArrayList<>(CONVERTING);
        CONVERTING.clear();
        for (AbstractPiglin piglin : now) {
            if (!piglin.isAlive() || !(piglin.level() instanceof ServerLevel level) || !piglin.isConverting() || !Workforce.isWorker(piglin)) continue;
            QuirkPiglinAccessor a = (QuirkPiglinAccessor) piglin;
            a.headinajar$playConvertedSound();
            a.headinajar$finishConversion(level);
        }
    }

    /**
     * An empty hand on a piglin: it hands its pocket back ("Fine. It was resting in my pocket."), or says it is empty. A
     * villager on the job does not trade ("I'm on the clock."). The workforce's own items (the Lanyard, Clipboard, Hard
     * Hat, Job Card, Whistle) are theirs, at a higher priority.
     */
    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (event.isCanceled() || !(event.getTarget() instanceof Mob mob) || !(event.getEntity() instanceof ServerPlayer player)) return;
        WorkerData d = Workforce.data(mob);
        if (d == null || d.state == WorkerData.State.NOTICE) return;
        WorkTable.Spec spec = WorkTable.of(mob);
        if (spec == null) return;
        ItemStack held = player.getMainHandItem();
        if (spec.quirk("pocket") && held.isEmpty()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (event.getHand() == InteractionHand.MAIN_HAND) handBack(player, mob, d);
            return;
        }
        if (spec.quirk("no_trade_on_shift") && mob instanceof AbstractVillager v && onShift(d) && !player.isSecondaryUseActive() && !workforceItem(held)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (event.getHand() != InteractionHand.MAIN_HAND) return;
            v.setUnhappyCounter(40);
            mob.playSound(SoundEvents.VILLAGER_NO, 1.0F, 1.0F);
            Workforce.say(mob, "on_the_clock");
        }
    }

    private static boolean workforceItem(ItemStack s) {
        return s.is(WorkRegistry.STAFF_LANYARD.get()) || s.is(WorkRegistry.CLIPBOARD.get()) || s.is(WorkRegistry.HARD_HAT.get())
                || s.is(WorkRegistry.JOB_CARD.get()) || s.is(WorkRegistry.SUPERVISORS_WHISTLE.get()) || s.is(WorkRegistry.SEPARATION_NOTICE.get());
    }

    private static void handBack(ServerPlayer player, Mob mob, WorkerData d) {
        mob.getLookControl().setLookAt(player, 30, 30);
        if (d.pocket.isEmpty()) {
            StaffShake.shake(mob);
            mob.playSound(SoundEvents.PIGLIN_JEALOUS, 1.0F, 1.0F);
            answer(mob, d, "pocket_empty");
            return;
        }
        ItemStack back = d.pocket;
        d.pocket = ItemStack.EMPTY;
        Workforce.give(player, back);
        mob.playSound(SoundEvents.PIGLIN_ANGRY, 0.8F, 1.0F);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.2F);
        answer(mob, d, "pocket");
    }

    /** A floating line in answer to a press: said now, whatever was said a moment ago (a press always answers). */
    private static void answer(Mob mob, WorkerData d, String key) {
        d.status = "staff.headinajar.say." + key;
        d.lastSaid = mob.level().getGameTime();
        Workforce.sayNow(mob, Component.translatable("staff.headinajar.say." + key));
    }

    // --- the llama --------------------------------------------------------------------------------------------------

    /** Whoever opens a Time Clock: each unpaid llama on its roster within range spits at them, once a day (the use goes on). */
    @SubscribeEvent
    public static void clockOpened(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level) || player.isSecondaryUseActive()) return;
        if (!level.getBlockState(event.getPos()).is(WorkRegistry.TIME_CLOCK.get())) return;
        if (!(level.getBlockEntity(event.getPos()) instanceof TimeClockBlockEntity clock)) return;
        long day = Staff.day(level);
        for (UUID id : List.copyOf(clock.roster())) {
            if (!(level.getEntity(id) instanceof Llama llama)) continue;
            WorkerData d = Workforce.data(llama);
            WorkTable.Spec spec = WorkTable.of(llama);
            if (d == null || d.state == WorkerData.State.NOTICE || spec == null || !spec.quirk("spits_unpaid")) continue;
            if (llama.distanceToSqr(player) > SPIT_RANGE * SPIT_RANGE || !unpaid(llama, level)) continue;
            QuirkState q = state(llama);
            if (q.spatDay == day) continue;
            q.spatDay = day;
            spit(llama, player, level);
            answer(llama, d, "spit");
        }
    }

    /** Whether its Personnel File shows it unpaid today. */
    static boolean unpaid(Mob mob, ServerLevel level) {
        StaffFile f = Staff.of(mob);
        return f != null && f.staff() && f.paidThrough < Staff.day(level) && !StaffTable.wage(mob.getType()).none();
    }

    /** Vanilla's spit, aimed as {@code Llama.spit} aims it; it lands on nobody ({@link #spitLands}). */
    static void spit(Llama llama, LivingEntity target, ServerLevel level) {
        float yaw = (float) (Mth.atan2(target.getZ() - llama.getZ(), target.getX() - llama.getX()) * Mth.RAD_TO_DEG) - 90.0F;
        llama.setYRot(yaw);
        llama.setYBodyRot(yaw);
        llama.setYHeadRot(yaw);
        LlamaSpit spit = new LlamaSpit(level, llama);
        double dx = target.getX() - llama.getX();
        double dy = target.getY(1.0 / 3.0) - spit.getY();
        double dz = target.getZ() - llama.getZ();
        double lift = Math.sqrt(dx * dx + dz * dz) * 0.2F;
        spit.shoot(dx, dy + lift, dz, 1.5F, 10.0F);
        if (!llama.isSilent()) {
            level.playSound(null, llama.getX(), llama.getY(), llama.getZ(), SoundEvents.LLAMA_SPIT, llama.getSoundSource(), 1.0F,
                    1.0F + (llama.getRandom().nextFloat() - llama.getRandom().nextFloat()) * 0.2F);
        }
        level.addFreshEntity(spit);
    }

    /** A staff llama's spit on someone: no hit, only the splat. */
    @SubscribeEvent
    public static void spitLands(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof LlamaSpit spit) || !(event.getRayTraceResult() instanceof EntityHitResult)) return;
        if (!(spit.getOwner() instanceof Llama llama) || !Workforce.isWorker(llama)) return;
        event.setCanceled(true);
        if (spit.level() instanceof ServerLevel level) {
            Vec3 at = event.getRayTraceResult().getLocation();
            level.sendParticles(ParticleTypes.SPIT, at.x, at.y, at.z, 7, 0.1, 0.1, 0.1, 0.02);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.NEUTRAL, 0.6F, 1.4F);
            spit.discard();
        }
    }

    // --- the panda --------------------------------------------------------------------------------------------------

    /**
     * By its personality: worried stops in thunder; lazy, on the job, works a spell and then, once its hands are free,
     * takes a break as long; playful rolls at its stops (never towards the site's edge); aggressive swats the nearest
     * hostile within 4 every two seconds (a hit with its own attack).
     */
    private static String personality(Panda panda, WorkerData d, QuirkState q, ServerLevel level, long now) {
        if (panda.isWorried() && level.isThundering()) {
            MobMotor.halt(panda);
            Workforce.say(panda, "panda_worried");
            return "panda_worried";
        }
        if (panda.isLazy()) {
            if (d.state != WorkerData.State.ON_JOB) {
                if (q.lazing) panda.sit(false);
                q.lazing = q.lazeDue = false;
            } else {
                if (now >= q.nextLaze) {
                    q.nextLaze = now + LAZY_TICKS;
                    if (q.lazing) {
                        q.lazing = false;
                        panda.sit(false);
                    } else {
                        q.lazeDue = true;
                    }
                }
                if (q.lazeDue && !d.carrying()) {
                    q.lazeDue = false;
                    q.lazing = true;
                    q.nextLaze = now + LAZY_TICKS;
                    panda.sit(true);
                }
                if (q.lazing) {
                    MobMotor.halt(panda);
                    Workforce.say(panda, "panda_lazy");
                    return "panda_lazy";
                }
            }
        }
        if (panda.isPlayful() && d.state == WorkerData.State.ON_JOB && now >= q.nextRoll && MobMotor.idle(panda) && !panda.isRolling()
                && panda.onGround() && Workforce.clockHere(panda, d)) {
            q.nextRoll = now + 200 + panda.getRandom().nextInt(400);
            float f = panda.getYRot() * Mth.DEG_TO_RAD;
            BlockPos ahead = BlockPos.containing(panda.getX() - Mth.sin(f) * 7, panda.getY(), panda.getZ() + Mth.cos(f) * 7);
            if (Workforce.inBox(d.clock.pos(), ahead)) panda.roll(true);
        }
        if (panda.isAggressive() && now >= q.nextGuard) {
            q.nextGuard = now + 40;
            LivingEntity foe = nearest(level, panda, LivingEntity.class, NEAR, e -> FriendOrFoe.mayTarget(d.employer, level, e));
            if (foe != null) {
                panda.getLookControl().setLookAt(foe, 30, 30);
                panda.swing(InteractionHand.MAIN_HAND);
                panda.doHurtTarget(foe);
                panda.playSound(SoundEvents.PANDA_AGGRESSIVE_AMBIENT, 1.0F, 1.0F);
            }
        }
        return null;
    }

    // --- the goat ---------------------------------------------------------------------------------------------------

    /** Now and then: head down (vanilla's event), then at the next think a ram into a colleague within 4: knockback only. */
    private static void ram(Goat goat, WorkerData d, QuirkState q, ServerLevel level, long now) {
        if (q.ramTarget != null) {
            Entity t = level.getEntity(q.ramTarget);
            q.ramTarget = null;
            q.nextRam = now + 1200 + goat.getRandom().nextInt(2400);
            level.broadcastEntityEvent(goat, (byte) 59);
            if (!(t instanceof LivingEntity target) || !target.isAlive() || target.distanceToSqr(goat) > (NEAR + 1) * (NEAR + 1)) return;
            target.knockback(0.8, goat.getX() - target.getX(), goat.getZ() - target.getZ());
            target.hurtMarked = true;
            goat.playSound(goat.isScreamingGoat() ? SoundEvents.GOAT_SCREAMING_RAM_IMPACT : SoundEvents.GOAT_RAM_IMPACT, 1.0F, 1.0F);
            Workforce.say(goat, "team_building");
            return;
        }
        if (now < q.nextRam) return;
        Mob mate = nearest(level, goat, Mob.class, NEAR, m -> m != goat && Workforce.isWorker(m) && d.employer.equals(Workforce.employer(m))
                && Workforce.data(m).state != WorkerData.State.NOTICE);
        if (mate == null) {
            q.nextRam = now + 600;
            return;
        }
        q.ramTarget = mate.getUUID();
        goat.getLookControl().setLookAt(mate, 30, 30);
        level.broadcastEntityEvent(goat, (byte) 58);
        goat.playSound(goat.isScreamingGoat() ? SoundEvents.GOAT_SCREAMING_PREPARE_RAM : SoundEvents.GOAT_PREPARE_RAM, 1.0F, 1.0F);
    }

    // --- the chicken ------------------------------------------------------------------------------------------------

    /** Carrying something, its egg goes into the carry on vanilla's timer, before vanilla would lay it on the ground. */
    private static void layIntoTheCarry(Chicken chicken, WorkerData d, WorkTable.Spec spec) {
        if (!d.carrying() || chicken.isBaby() || chicken.isChickenJockey() || chicken.eggTime > Workforce.thinkTicks() * 2) return;
        if (!intoTheCarry(d, spec, new ItemStack(Items.EGG))) return; // no room: vanilla lays it on the ground
        chicken.playSound(SoundEvents.CHICKEN_EGG, 1.0F, (chicken.getRandom().nextFloat() - chicken.getRandom().nextFloat()) * 0.2F + 1.0F);
        chicken.gameEvent(GameEvent.ENTITY_PLACE);
        chicken.eggTime = chicken.getRandom().nextInt(6000) + 6000;
        WorkerLooks.changed(chicken);
    }

    /** One item into what a worker carries: merged, or a new stack (six at most), within its carry limit in items. */
    static boolean intoTheCarry(WorkerData d, WorkTable.Spec spec, ItemStack one) {
        int items = 0;
        for (ItemStack s : d.carried) items += s.getCount();
        if (items + one.getCount() > spec.carryLimit(one.getMaxStackSize())) return false;
        for (ItemStack s : d.carried) {
            if (ItemStack.isSameItemSameComponents(s, one) && s.getCount() + one.getCount() <= s.getMaxStackSize()) {
                s.grow(one.getCount());
                return true;
            }
        }
        d.carried.removeIf(ItemStack::isEmpty);
        if (d.carried.size() >= WorkerData.MAX_CARRIED) return false;
        d.carried.add(one);
        return true;
    }

    // --- the dolphin ------------------------------------------------------------------------------------------------

    /** Below 30% air it swims up to the air before anything else, until its air is back. */
    private static String surface(Mob mob, QuirkState q, ServerLevel level) {
        int air = mob.getAirSupply(), max = mob.getMaxAirSupply();
        if (!q.surfacing && air < max * SURFACE_AT) q.surfacing = true;
        if (q.surfacing && air >= max) q.surfacing = false;
        if (!q.surfacing) return null;
        // as vanilla's BreathAirGoal goes: a path to just above the air it found, and a kick upwards while it is under
        BlockPos up = air(level, mob);
        if (MobMotor.idle(mob)) mob.getNavigation().moveTo(up.getX() + 0.5, up.getY() + 1, up.getZ() + 0.5, 1.2);
        if (mob.isInWater()) mob.setDeltaMovement(mob.getDeltaMovement().add(0, 0.1, 0));
        Workforce.say(mob, "surfacing");
        return "surfacing";
    }

    /** The nearest air above it ({@code BreathAirGoal}'s search: a 3 x 3 column 8 up), else 8 straight up. */
    private static BlockPos air(ServerLevel level, Mob mob) {
        for (BlockPos p : BlockPos.betweenClosed(Mth.floor(mob.getX() - 1), mob.getBlockY(), Mth.floor(mob.getZ() - 1),
                Mth.floor(mob.getX() + 1), Mth.floor(mob.getY() + 8), Mth.floor(mob.getZ() + 1))) {
            if (!level.isLoaded(p)) continue;
            var state = level.getBlockState(p);
            if ((level.getFluidState(p).isEmpty() || state.is(Blocks.BUBBLE_COLUMN)) && state.isPathfindable(PathComputationType.LAND)) return p.immutable();
        }
        return BlockPos.containing(mob.getX(), mob.getY() + 8, mob.getZ());
    }

    // --- the villager -----------------------------------------------------------------------------------------------

    /**
     * Off the job, what vanilla's {@code Villager.customServerAiStep} does after a trade (switched off for staff): the
     * level up when its timer runs out, Regeneration, the trading player's reputation; and its trades restock once at
     * each dawn.
     */
    private static void offShift(Villager v, QuirkState q, ServerLevel level) {
        VillagerAccessor a = (VillagerAccessor) v;
        int timer = a.headinajar$updateMerchantTimer();
        if (!v.isTrading() && timer > 0) {
            timer -= Workforce.thinkTicks();
            a.headinajar$setUpdateMerchantTimer(Math.max(0, timer));
            if (timer <= 0) {
                if (a.headinajar$levelUp()) {
                    a.headinajar$increaseMerchantCareer();
                    a.headinajar$setLevelUp(false);
                }
                v.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
            }
        }
        QuirkVillagerAccessor t = (QuirkVillagerAccessor) v;
        Player traded = t.headinajar$lastTradedPlayer();
        if (traded != null) {
            level.onReputationEvent(ReputationEventType.TRADE, traded, v);
            level.broadcastEntityEvent(v, (byte) 14);
            t.headinajar$setLastTradedPlayer(null);
        }
        long day = Staff.day(level);
        if (q.restockDay < 0) q.restockDay = day;
        else if (day > q.restockDay) {
            q.restockDay = day;
            v.restock();
        }
    }

    // --- the iron golem and the witch -------------------------------------------------------------------------------

    /** Now and then it holds out a poppy (vanilla's) to villager staff within 4. */
    private static void poppy(IronGolem golem, QuirkState q, ServerLevel level, long now) {
        if (now < q.nextPoppy || golem.getOfferFlowerTick() > 0) return;
        AbstractVillager v = nearest(level, golem, AbstractVillager.class, NEAR, Workforce::isWorker);
        if (v == null) {
            q.nextPoppy = now + 100;
            return;
        }
        q.nextPoppy = now + 800 + golem.getRandom().nextInt(800);
        golem.getLookControl().setLookAt(v, 30, 30);
        golem.offerFlower(true);
        Workforce.say(golem, "poppy_offer");
    }

    /** Every 600 ticks: a splash potion of Regeneration at the most hurt colleague within 8, thrown as a witch throws. */
    private static void splash(Witch witch, QuirkState q, ServerLevel level, long now) {
        if (now < q.nextSplash || witch.isDrinkingPotion()) return;
        Mob worst = null;
        float least = 1;
        for (Mob m : level.getEntitiesOfClass(Mob.class, witch.getBoundingBox().inflate(SPLASH_RANGE), m -> m != witch && Workforce.isWorker(m) && m.isAlive())) {
            float share = m.getHealth() / m.getMaxHealth();
            if (share < least && m.distanceToSqr(witch) <= SPLASH_RANGE * SPLASH_RANGE && witch.hasLineOfSight(m)) {
                least = share;
                worst = m;
            }
        }
        if (worst == null) {
            q.nextSplash = now + 40;
            return;
        }
        q.nextSplash = now + SPLASH_TICKS;
        Vec3 v = worst.getDeltaMovement();
        double dx = worst.getX() + v.x - witch.getX();
        double dz = worst.getZ() + v.z - witch.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        witch.getLookControl().setLookAt(worst, 30, 30);
        ThrownPotion potion = new ThrownPotion(level, witch);
        // vanilla's throw, aimed at the colleague's feet rather than at a foe's chest: a splash needs only land near
        double dy = worst.getY() - potion.getY();
        potion.setItem(PotionContents.createItemStack(Items.SPLASH_POTION, Potions.REGENERATION));
        potion.setXRot(potion.getXRot() + 20.0F);
        potion.shoot(dx, dy + flat * 0.2, dz, 0.75F, 8.0F);
        if (!witch.isSilent()) {
            level.playSound(null, witch.getX(), witch.getY(), witch.getZ(), SoundEvents.WITCH_THROW, witch.getSoundSource(), 1.0F,
                    0.8F + witch.getRandom().nextFloat() * 0.4F);
        }
        level.addFreshEntity(potion);
        Workforce.say(witch, "occupational_health");
    }

    // --- helpers ----------------------------------------------------------------------------------------------------

    private static <T extends Entity> T nearest(ServerLevel level, Mob from, Class<T> type, int within, Predicate<? super T> ok) {
        T best = null;
        double bestD = within * within;
        for (T e : level.getEntitiesOfClass(type, from.getBoundingBox().inflate(within), ok)) {
            double dd = e.distanceToSqr(from);
            if (dd <= bestD) {
                bestD = dd;
                best = e;
            }
        }
        return best;
    }

    /** A level unloads: its workers' quirk state goes with it. */
    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        STATES.keySet().removeIf(m -> m.level() == event.getLevel());
        CONVERTING.removeIf(m -> m.level() == event.getLevel());
    }
}

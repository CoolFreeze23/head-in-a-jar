package com.coolfreeze23.headinajar.genetics;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.fx.Fx;
import com.coolfreeze23.headinajar.fx.GeneSounds;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The 0.6.2 active genes (Phase, Snow Pounce, Tongue, Spit Gland, Fang Line, Levitation Bolt) and the two heavy
 * passives that act on the world (Bulldoze, Weeping Glands). Numbers in {@link GeneNumbers}.
 * <p>
 * "You", for a gene: the body that carries it, and the jar riding it or piloting it. Every blow, fang and bullet these
 * genes make touches another player only where the server allows PvP; the spit never hurts a player at all.
 */
public final class GeneActives {
    /** What {@link #fire} answers for a gene that is not one of these. */
    public static final String NOT_MINE = "";
    /** On a spit, a fang or a bullet these genes made: which body made it (its entity id). */
    public static final String MADE_BY = "headinajar_gene_by";
    /** On a spit: it was the Personal Space spit (harmless to everyone). */
    public static final String HARMLESS = "headinajar_gene_harmless";
    /** Weeping Glands: ticks worn since the last tear, saved with the body. */
    public static final String WEEP_WORN = "headinajar_weep_worn";
    /** Bulldoze: the claim line is said at most this often (ticks). */
    public static final int BULLDOZE_LINE_EVERY = 100;
    /** Snow Pounce: a pounce that has not landed after this long is over (ticks). */
    public static final int POUNCE_LONGEST = 60;
    /** The ballistic leap's flight time: this plus a tick per block, at most {@link #POUNCE_FLIGHT_MAX}. */
    private static final int POUNCE_FLIGHT = 6, POUNCE_FLIGHT_MAX = 16;

    /** Snow Pounce: the bodies in the air, {start, landed at (-1 until then)}. */
    private static final Map<Entity, long[]> POUNCING = new WeakHashMap<>();
    /** Spit Gland: a creature spat at, {calm until, cannot be calmed again until, the body it was calmed from}. */
    private static final Map<Entity, long[]> CALM = new WeakHashMap<>();
    /** Levitation Bolt: creatures a bolt of ours just hit, with the game time, whose Levitation is trimmed. */
    private static final Map<Entity, Long> TRIM = new WeakHashMap<>();
    /** Bulldoze: where each carrier was at its last tick, and when it last said the claim line. */
    private static final Map<Entity, Vec3> LAST_AT = new WeakHashMap<>();
    private static final Map<Entity, Long> BULLDOZE_SAID = new WeakHashMap<>();
    /** Personal Space: {ticks a player has stood in front, game time of the last such spit}. */
    private static final Map<Entity, long[]> PERSONAL = new WeakHashMap<>();

    private GeneActives() {
    }

    /** Puts this class's event handlers on the game's bus (called once, from the mod's constructor). */
    public static void register() {
        NeoForge.EVENT_BUS.register(Events.class);
    }

    /** Every tick, both sides; {@code on} false: take every modifier of this class off. None of these genes has one. */
    static void tick(LivingEntity e, boolean on, float k) {
    }

    /** Movement, both sides. */
    static void movement(LivingEntity e) {
    }

    /** The server's tick of a body whose genes work. */
    static void serverTick(LivingEntity e, ServerLevel level) {
        long[] pounce = POUNCING.get(e);
        if (pounce != null) pounceTick(e, level, pounce);
        if (GeneEffects.has(e, Gene.BULLDOZE)) bulldoze(e, level);
        else LAST_AT.remove(e);
        if (GeneEffects.has(e, Gene.WEEPING_GLANDS)) weep(e, level);
        if (GeneEffects.has(e, Gene.SPIT_GLAND) && e.tickCount % 20 == 0) personalSpace(e, level);
    }

    /**
     * Fires {@code gene} for {@code body} aimed by {@code aim}: null when it fired (and set {@code cd[which]}), else a
     * refusal reason (a key under {@code refusal.headinajar.}); {@link #NOT_MINE} for a gene this class does not fire.
     */
    static String fire(Gene gene, LivingEntity body, LivingEntity aim, ServerLevel level, int[] cd, int which) {
        String why = switch (gene) {
            case PHASE -> phase(body, aim, level);
            case SNOW_POUNCE -> pounce(body, aim, level);
            case TONGUE -> tongue(body, aim, level);
            case SPIT_GLAND -> spit(body, aim, level, GeneEffects.target(aim, body, GeneNumbers.SPIT_REACH), false);
            case FANG_LINE -> fangs(body, aim, level);
            case LEVITATION_BOLT -> bolt(body, aim, level);
            default -> NOT_MINE;
        };
        if (why == null) cd[which] = switch (gene) {
            case PHASE -> GeneNumbers.PHASE_COOLDOWN;
            case SNOW_POUNCE -> daylight(body, level) ? GeneNumbers.POUNCE_COOLDOWN_DAY : GeneNumbers.POUNCE_COOLDOWN;
            case TONGUE -> GeneNumbers.TONGUE_COOLDOWN;
            case SPIT_GLAND -> GeneNumbers.SPIT_COOLDOWN;
            case FANG_LINE -> GeneNumbers.FANG_COOLDOWN;
            default -> GeneNumbers.BOLT_COOLDOWN;
        };
        return why;
    }

    // --- who is "you" ------------------------------------------------------------------------------------------------

    /** Whether {@code other} is {@code body}, rides it (the jar on a host, the pilot of a mecha), or carries it. */
    static boolean you(Entity body, Entity other) {
        if (other == null || body == null) return false;
        return other == body || other.getVehicle() == body || body.getVehicle() == other;
    }

    /** Players are touched only where the server allows PvP. */
    private static boolean mayTouch(ServerLevel level, Entity other) {
        return !(other instanceof Player) || level.getServer().isPvpAllowed();
    }

    private static DamageSource blow(ServerLevel level, LivingEntity body) {
        return body instanceof Player p ? level.damageSources().playerAttack(p) : level.damageSources().mobAttack(body);
    }

    private static Entity mover(LivingEntity body) {
        return body.getVehicle() != null ? body.getVehicle() : body;
    }

    private static void mark(Entity made, LivingEntity body) {
        made.getPersistentData().putInt(MADE_BY, body.getId());
    }

    /** The body that made this spit, fang or bullet, if it is one of ours. */
    private static LivingEntity maker(Entity made) {
        if (made == null || !made.getPersistentData().contains(MADE_BY)) return null;
        Entity owner = made instanceof Projectile p ? p.getOwner() : made instanceof EvokerFangs f ? f.getOwner() : null;
        return owner instanceof LivingEntity body ? body : null;
    }

    // --- Phase -------------------------------------------------------------------------------------------------------

    /**
     * A dash {@link GeneNumbers#PHASE_REACH} blocks along the flat look through at most {@link GeneNumbers#PHASE_WALL}
     * blocks of wall, into the farthest open space within reach. A wall that cannot be broken, or the world's edge, goes
     * on forever; a protected wall or landing is refused; a wall too thick, or no room beyond it, is "no room".
     */
    static String phase(LivingEntity body, LivingEntity aim, ServerLevel level) {
        if (!Config.PHASE_THROUGH_WALLS.get()) return "gene.phase.off";
        Entity mover = mover(body);
        Vec3 look = aim.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        if (dir.lengthSqr() < 1e-6) dir = Vec3.directionFromRotation(0, aim.getYRot());
        dir = dir.normalize();
        Vec3 start = mover.position();
        AABB box = mover.getBoundingBox();
        // the run through solid ground the box may make: the wall, plus the box's own width going in and out
        double limit = GeneNumbers.PHASE_WALL + Math.max(box.getXsize(), box.getZsize()) + 0.25;
        double step = 0.25, run = 0;
        boolean walled = false, crossed = false;
        Vec3 dest = null;
        Set<BlockPos> wall = new HashSet<>();
        for (double d = step; d <= GeneNumbers.PHASE_REACH + 1e-6; d += step) {
            Vec3 p = start.add(dir.scale(d));
            AABB at = box.move(p.subtract(start));
            if (!level.getWorldBorder().isWithinBounds(at) || at.minY < level.getMinBuildHeight() || at.maxY > level.getMaxBuildHeight()) {
                return "gene.phase.forever";
            }
            if (level.noCollision(mover, at)) {
                if (run > 0) crossed = true;
                run = 0;
                dest = p;
                continue;
            }
            walled = true;
            run += step;
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(at.minX, at.minY, at.minZ),
                    BlockPos.containing(at.maxX - 1e-4, at.maxY - 1e-4, at.maxZ - 1e-4))) {
                BlockState state = level.getBlockState(pos);
                if (state.getCollisionShape(level, pos).isEmpty() || !wall.add(pos.immutable())) continue;
                if (state.getDestroySpeed(level, pos) < 0) return "gene.phase.forever";
                if (!GeneClaims.mayChange(body, pos)) return "gene.phase.claim";
            }
            if (run > limit) break;
        }
        // through a wall into open space, or a clear dash; a wall not got through is no room on the other side
        if (dest == null || walled && !crossed) return "gene.phase.no_room";
        if (!GeneClaims.mayChange(body, BlockPos.containing(dest))) return "gene.phase.claim";
        mover.teleportTo(dest.x, dest.y, dest.z);
        mover.fallDistance = 0;
        level.playSound(null, start.x, start.y, start.z, GeneSounds.PHASE.get(), SoundSource.PLAYERS, 1, 1);
        level.playSound(null, dest.x, dest.y, dest.z, GeneSounds.PHASE.get(), SoundSource.PLAYERS, 1, 1.2f);
        Vec3 mid = new Vec3(0, box.getYsize() / 2, 0);
        Fx.send(level, Fx.of(Fx.GENE_PHASE, start.add(mid), dest.add(mid), body.getId(), -1, 0));
        // what phasing costs: a host its health, a mecha a share of its own
        if (body instanceof MechaEntity mecha) cost(mecha, mecha.getMaxHealth() * GeneNumbers.PHASE_MECHA_SHARE, level);
        else if (!(body instanceof Player)) cost(body, GeneNumbers.PHASE_HOST_COST, level);
        return null;
    }

    private static void cost(LivingEntity body, float amount, ServerLevel level) {
        if (body.getHealth() > amount) body.setHealth(body.getHealth() - amount);
        else body.hurt(level.damageSources().magic(), amount);
    }

    // --- Snow Pounce -------------------------------------------------------------------------------------------------

    /** By day under open sky (Snow Pounce's longer cooldown). */
    static boolean daylight(LivingEntity body, ServerLevel level) {
        return level.isDay() && level.canSeeSky(BlockPos.containing(body.getEyePosition()));
    }

    /** A high leap to the block looked at within reach: a ballistic throw that lands there; landing hurts what is near. */
    static String pounce(LivingEntity body, LivingEntity aim, ServerLevel level) {
        Vec3 eye = aim.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(aim.getLookAngle().scale(GeneNumbers.POUNCE_REACH)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, aim));
        if (hit.getType() != HitResult.Type.BLOCK) return "gene.no_target";
        Vec3 to = Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection()));
        Entity mover = mover(body);
        Vec3 d = to.subtract(mover.position());
        int t = Math.min(POUNCE_FLIGHT_MAX, POUNCE_FLIGHT + (int) Math.ceil(d.horizontalDistance()));
        Vec3 v = launch(d, t, firstFriction(mover));
        // a ridden host or mecha is moved by its pilot's client, which the server's velocity never reaches: the push goes there
        if (!(com.coolfreeze23.headinajar.host.Hosts.pilot(mover) instanceof net.minecraft.server.level.ServerPlayer pilot)
                || !com.coolfreeze23.headinajar.mecha.HopperNet.push(pilot, mover, v)) {
            mover.setDeltaMovement(v);
            mover.hurtMarked = true;
        }
        mover.setOnGround(false);
        mover.fallDistance = 0;
        POUNCING.put(body, new long[]{level.getGameTime(), -1});
        level.playSound(null, body.getX(), body.getY(), body.getZ(), GeneSounds.POUNCE.get(), SoundSource.PLAYERS, 1, 1);
        return null;
    }

    /**
     * The take-off velocity that carries a body by {@code d} in {@code t} ticks under vanilla's air physics: each tick it
     * moves, then gravity 0.08 and drag 0.98 act on the rise and fall, friction 0.91 on the run.
     */
    public static Vec3 launch(Vec3 d, int t) {
        return launch(d, t, 0.91);
    }

    /**
     * As above, for a body that takes off from where it stands: on its first tick the game still counts it on the ground,
     * so the run is slowed by the ground's grip ({@code first}: the block's friction x 0.91, 0.546 on grass or stone)
     * instead of the air's 0.91. Ignoring that landed a pounce at about two thirds of its aim.
     */
    public static Vec3 launch(Vec3 d, int t, double first) {
        double f = 0.91, g = 0.08, drag = 0.98, c = g * drag / (1 - drag);
        double h = 1 / (1 + first * (1 - Math.pow(f, t - 1)) / (1 - f));
        double vy = (d.y + c * t) * (1 - drag) / (1 - Math.pow(drag, t)) - c;
        return new Vec3(d.x * h, vy, d.z * h);
    }

    /** The run's friction on a body's first tick of a launch: the ground's grip when it stands on a block, else the air's. */
    static double firstFriction(Entity mover) {
        if (!mover.onGround()) return 0.91;
        BlockPos below = BlockPos.containing(mover.getX(), mover.getBoundingBox().minY - 0.5000001, mover.getZ());
        return mover.level().getBlockState(below).getBlock().getFriction() * 0.91;
    }

    private static void pounceTick(LivingEntity e, ServerLevel level, long[] pounce) {
        long now = level.getGameTime();
        if (pounce[1] >= 0) {
            // landed: kept a few ticks so the fall that comes with the landing does not hurt
            if (now - pounce[1] > 5) POUNCING.remove(e);
            return;
        }
        if (now - pounce[0] < 3) return;
        Entity mover = mover(e);
        if (mover.onGround() || mover.isInWater() || now - pounce[0] > POUNCE_LONGEST) land(e, level);
    }

    /** The pounce lands: what is within the splash takes its damage, is knocked back and slowed. Returns how many. */
    public static int land(LivingEntity body, ServerLevel level) {
        long[] pounce = POUNCING.computeIfAbsent(body, k -> new long[]{level.getGameTime(), -1});
        pounce[1] = level.getGameTime();
        Entity mover = mover(body);
        mover.fallDistance = 0;
        float damage = GeneNumbers.POUNCE_DAMAGE * GeneEffects.scale(body);
        int caught = 0;
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, mover.getBoundingBox().inflate(GeneNumbers.POUNCE_SPLASH),
                o -> o.isAlive() && !you(body, o) && o != mover && !you(mover, o) && mayTouch(level, o))) {
            other.hurt(blow(level, body), damage);
            other.knockback(GeneNumbers.POUNCE_KNOCKBACK, mover.getX() - other.getX(), mover.getZ() - other.getZ());
            other.hurtMarked = true;
            other.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, GeneNumbers.POUNCE_SLOW_TICKS, GeneNumbers.POUNCE_SLOW_LEVEL), body);
            caught++;
        }
        level.playSound(null, mover.getX(), mover.getY(), mover.getZ(), SoundEvents.SNOW_BREAK, SoundSource.PLAYERS, 1, 0.8f);
        Fx.send(level, Fx.of(Fx.GENE_POUNCE, mover.position(), null, body.getId(), -1, caught));
        return caught;
    }

    /** Whether {@code body} is in the air from a pounce (no fall damage), for tests. */
    public static boolean pouncing(Entity body) {
        return POUNCING.containsKey(body);
    }

    // --- Tongue ------------------------------------------------------------------------------------------------------

    /** Whether a creature is small enough for the tongue to pull (a chicken's size or smaller). */
    public static boolean tongueSized(Entity e) {
        return e.getBbWidth() <= GeneNumbers.TONGUE_MAX_WIDTH + 1e-3 && e.getBbHeight() <= GeneNumbers.TONGUE_MAX_HEIGHT + 1e-3;
    }

    /** What the tongue makes of a small slime or magma cube: a slime ball, or the froglight of the climate the body stands in. */
    public static ItemStack eaten(Entity food, LivingEntity body) {
        if (!(food instanceof MagmaCube)) return new ItemStack(Items.SLIME_BALL);
        var biome = body.level().getBiome(body.blockPosition());
        if (biome.is(BiomeTags.SPAWNS_WARM_VARIANT_FROGS)) return new ItemStack(Items.PEARLESCENT_FROGLIGHT);
        if (biome.is(BiomeTags.SPAWNS_COLD_VARIANT_FROGS)) return new ItemStack(Items.VERDANT_FROGLIGHT);
        return new ItemStack(Items.OCHRE_FROGLIGHT);
    }

    /** Licks what the aim looks at within reach: an item comes, a small creature is pulled, a small slime is eaten. Never a player. */
    static String tongue(LivingEntity body, LivingEntity aim, ServerLevel level) {
        Vec3 eye = aim.getEyePosition(), end = eye.add(aim.getLookAngle().scale(GeneNumbers.TONGUE_REACH));
        BlockHitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, aim));
        if (wall.getType() == HitResult.Type.BLOCK) end = wall.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(aim, eye, end, new AABB(eye, end).inflate(1),
                o -> o != aim && !you(body, o) && (o instanceof LivingEntity l && l.isAlive() || o instanceof ItemEntity), eye.distanceToSqr(end));
        if (hit == null) return "gene.no_target";
        Entity caught = hit.getEntity();
        if (caught instanceof Player) return "gene.tongue.player";
        boolean eat = caught instanceof Slime slime && slime.getSize() == 1;
        if (!eat && !(caught instanceof ItemEntity) && !tongueSized(caught)) return "gene.tongue.too_big";
        Vec3 tip = caught.getBoundingBox().getCenter();
        Entity mover = mover(body);
        if (eat) {
            caught.discard();
            body.spawnAtLocation(eaten(caught, body));
            level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.FROG_EAT, SoundSource.PLAYERS, 1, 1);
        } else if (caught instanceof ItemEntity item) {
            item.teleportTo(mover.getX(), mover.getY() + 0.25, mover.getZ());
            item.setDeltaMovement(Vec3.ZERO);
            item.setNoPickUpDelay();
        } else {
            Vec3 d = mover.position().subtract(caught.position());
            caught.setDeltaMovement(launch(d, POUNCE_FLIGHT + (int) Math.ceil(d.horizontalDistance()), firstFriction(caught)));
            caught.hurtMarked = true;
        }
        level.playSound(null, body.getX(), body.getY(), body.getZ(), GeneSounds.TONGUE.get(), SoundSource.PLAYERS, 1, 1);
        Fx.send(level, Fx.of(Fx.GENE_TONGUE, eye.subtract(0, 0.15, 0), tip, body.getId(), eat ? -1 : caught.getId(), eat ? 1 : 0));
        return null;
    }

    // --- Spit Gland --------------------------------------------------------------------------------------------------

    /** Spits vanilla's llama spit at {@code target} (or along the look when there is none). Always fires. */
    static String spit(LivingEntity body, LivingEntity aim, ServerLevel level, LivingEntity target, boolean harmless) {
        Vec3 look = aim.getLookAngle();
        Vec3 mouth = aim.getEyePosition().add(look.scale(0.4)).subtract(0, 0.1, 0);
        LlamaSpit spit = new LlamaSpit(EntityType.LLAMA_SPIT, level);
        spit.setOwner(body);
        spit.setPos(mouth.x, mouth.y, mouth.z);
        mark(spit, body);
        if (harmless) spit.getPersistentData().putBoolean(HARMLESS, true);
        if (target != null) {
            double dx = target.getX() - mouth.x, dy = target.getY(0.3333) - mouth.y, dz = target.getZ() - mouth.z;
            spit.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.1, dz, 1.5f, 1f);
        } else {
            spit.shoot(look.x, look.y, look.z, 1.5f, 1f);
        }
        level.addFreshEntity(spit);
        level.playSound(null, mouth.x, mouth.y, mouth.z, GeneSounds.SPIT.get(), SoundSource.PLAYERS, 1, 0.8f + level.random.nextFloat() * 0.4f);
        Fx.send(level, Fx.of(Fx.GENE_SPIT, mouth, null, body.getId(), -1, 0));
        return null;
    }

    /** Whether a serum in this body has both genes on one strand (the named pairs are per strand). */
    static boolean sameSerum(LivingEntity body, Gene a, Gene b) {
        for (Serum serum : Genetics.genes(body).serums) {
            boolean hasA = false, hasB = false;
            for (Serum.Entry entry : serum.genes()) {
                hasA |= entry.gene() == a;
                hasB |= entry.gene() == b;
            }
            if (hasA && hasB) return true;
        }
        return false;
    }

    /** A creature spat at by {@code body}: it loses interest for a while, unless it was calmed too recently. */
    public static void calm(Mob mob, LivingEntity body) {
        long now = mob.level().getGameTime();
        long[] c = CALM.get(mob);
        if (c != null && now < c[1]) return;
        CALM.put(mob, new long[]{now + GeneNumbers.SPIT_CALM, now + GeneNumbers.SPIT_IMMUNE, body.getId()});
        if (you(body, mob.getTarget())) mob.setTarget(null);
        // a brain without the memory registered throws when asked for it: only a brain that holds one is asked
        var brain = mob.getBrain();
        if (brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET) && you(body, brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null))) {
            brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
    }

    /** Whether {@code mob} is calmed now (it will not take the body that spat at it as its target), for tests. */
    public static boolean calmed(Entity mob) {
        long[] c = CALM.get(mob);
        return c != null && mob.level().getGameTime() < c[0];
    }

    /** Personal Space: a player who stands a block in front of a spitter for 5 s is spat at, harmlessly, once a minute. */
    private static void personalSpace(LivingEntity e, ServerLevel level) {
        long now = level.getGameTime();
        long[] p = PERSONAL.computeIfAbsent(e, k -> new long[]{0, -GeneNumbers.SPIT_PERSONAL_EVERY});
        Vec3 look = e.getLookAngle().multiply(1, 0, 1);
        Player near = null;
        if (look.lengthSqr() > 1e-6) {
            Vec3 flat = look.normalize();
            for (Player other : level.getEntitiesOfClass(Player.class, e.getBoundingBox().inflate(1.5, 0.5, 1.5), o -> !you(e, o) && !o.isSpectator())) {
                Vec3 to = other.position().subtract(e.position()).multiply(1, 0, 1);
                if (to.length() <= 2 && to.lengthSqr() > 1e-6 && to.normalize().dot(flat) > 0.7) near = other;
            }
        }
        if (near == null) {
            p[0] = 0;
            return;
        }
        p[0] += 20;
        if (p[0] >= GeneNumbers.SPIT_PERSONAL_TICKS && now - p[1] >= GeneNumbers.SPIT_PERSONAL_EVERY) {
            p[0] = 0;
            p[1] = now;
            LivingEntity aim = com.coolfreeze23.headinajar.host.Hosts.pilot(e) instanceof Player pilot ? pilot : e;
            spit(e, aim, level, near, true);
        }
    }

    // --- Fang Line ---------------------------------------------------------------------------------------------------

    /** Vanilla's evoker fangs: a line of eight toward the aim within reach, or, looking straight down, a ring round the body. */
    static String fangs(LivingEntity body, LivingEntity aim, ServerLevel level) {
        LivingEntity target = GeneEffects.target(aim, body, GeneNumbers.FANG_REACH);
        boolean ring = aim.getXRot() > 60;
        double minY = body.getY() - 3, maxY = body.getY() + 1;
        if (target != null && !ring) {
            minY = Math.min(target.getY(), body.getY());
            maxY = Math.max(target.getY(), body.getY()) + 1;
        }
        int placed = 0;
        if (ring) {
            float f = (float) Math.toRadians(body.getYRot());
            for (int i = 0; i < 5; i++) {
                float a = f + i * Mth.PI * 0.4f;
                placed += fang(level, body, body.getX() + Mth.cos(a) * 1.5, body.getZ() + Mth.sin(a) * 1.5, minY, maxY, a, 0);
            }
            for (int i = 0; i < 8; i++) {
                float a = f + i * Mth.PI * 2 / 8 + Mth.PI * 2 / 5;
                placed += fang(level, body, body.getX() + Mth.cos(a) * 2.5, body.getZ() + Mth.sin(a) * 2.5, minY, maxY, a, 3);
            }
        } else {
            Vec3 look = aim.getLookAngle();
            float f = target != null ? (float) Mth.atan2(target.getZ() - body.getZ(), target.getX() - body.getX()) : (float) Mth.atan2(look.z, look.x);
            double spacing = GeneNumbers.FANG_REACH / GeneNumbers.FANG_COUNT;
            // the first fang clears the body's own feet
            double first = Math.max(spacing, body.getBbWidth() / 2 + 0.75);
            for (int i = 0; i < GeneNumbers.FANG_COUNT; i++) {
                double d = first + spacing * i;
                placed += fang(level, body, body.getX() + Mth.cos(f) * d, body.getZ() + Mth.sin(f) * d, minY, maxY, f, i);
            }
        }
        if (placed == 0) return "gene.no_target";
        level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1, 1);
        if (body instanceof MechaEntity mecha) mecha.setHeat(mecha.heat() + mecha.frame().heatCap * GeneNumbers.FANG_HEAT_SHARE);
        // the cost: blue sheep nearby turn red, as an evoker's do
        boolean wololo = false;
        for (Sheep sheep : level.getEntitiesOfClass(Sheep.class, body.getBoundingBox().inflate(GeneNumbers.FANG_SHEEP), s -> s.getColor() == DyeColor.BLUE)) {
            sheep.setColor(DyeColor.RED);
            wololo = true;
        }
        if (wololo) level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.EVOKER_PREPARE_WOLOLO, SoundSource.PLAYERS, 1, 1);
        Fx.send(level, Fx.of(Fx.GENE_FANGS, body.position(), null, body.getId(), -1, ring ? 1 : 0));
        return null;
    }

    /** One fang on the first sturdy ground between the heights (vanilla's {@code Evoker.createSpellEntity}). 1 if placed. */
    private static int fang(ServerLevel level, LivingEntity body, double x, double z, double minY, double maxY, float yRot, int warmup) {
        BlockPos pos = BlockPos.containing(x, maxY, z);
        double top = 0;
        do {
            BlockPos below = pos.below();
            if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                if (!level.isEmptyBlock(pos)) {
                    VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
                    if (!shape.isEmpty()) top = shape.max(Direction.Axis.Y);
                }
                EvokerFangs fang = new EvokerFangs(level, x, pos.getY() + top, z, yRot, warmup, body);
                mark(fang, body);
                level.addFreshEntity(fang);
                return 1;
            }
            pos = pos.below();
        } while (pos.getY() >= Mth.floor(minY) - 1);
        return 0;
    }

    // --- Levitation Bolt ---------------------------------------------------------------------------------------------

    /** Vanilla's homing shulker bullet at the creature aimed at within reach; its Levitation is trimmed to 3 s. */
    static String bolt(LivingEntity body, LivingEntity aim, ServerLevel level) {
        LivingEntity target = GeneEffects.target(aim, body, GeneNumbers.BOLT_REACH);
        if (target == null || you(body, target) || !mayTouch(level, target)) return "gene.no_target";
        ShulkerBullet bullet = new ShulkerBullet(level, body, target, Direction.Axis.Y);
        mark(bullet, body);
        level.addFreshEntity(bullet);
        level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.SHULKER_SHOOT, SoundSource.PLAYERS, 2, 1);
        if (body instanceof MechaEntity mecha) mecha.setHeat(mecha.heat() + mecha.frame().heatCap * GeneNumbers.BOLT_HEAT_SHARE);
        return null;
    }

    // --- Bulldoze ----------------------------------------------------------------------------------------------------

    /** The soft things Bulldoze walks through. */
    public static boolean soft(BlockState s) {
        return s.is(BlockTags.LEAVES) || s.is(BlockTags.CROPS) || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.CAVE_VINES)
                || s.is(Blocks.BAMBOO) || s.is(Blocks.BAMBOO_SAPLING) || s.is(Blocks.VINE) || s.is(Blocks.WEEPING_VINES) || s.is(Blocks.WEEPING_VINES_PLANT)
                || s.is(Blocks.TWISTING_VINES) || s.is(Blocks.TWISTING_VINES_PLANT) || s.is(Blocks.SUGAR_CANE) || s.is(Blocks.COBWEB);
    }

    private static void bulldoze(LivingEntity e, ServerLevel level) {
        Vec3 at = mover(e).position(), last = LAST_AT.put(e, at);
        if (last == null || last.distanceToSqr(at) < 1e-4 || !Config.BULLDOZE_BREAKS_BLOCKS.get()) return;
        trample(e, level);
    }

    /** Breaks the soft blocks in the body's way (with drops); a protected one stays, and whoever is answerable is told. Returns how many broke. */
    public static int trample(LivingEntity e, ServerLevel level) {
        AABB box = mover(e).getBoundingBox().inflate(0.2);
        int broke = 0;
        boolean refused = false;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (!soft(level.getBlockState(pos))) continue;
            if (!GeneClaims.mayChange(e, pos)) {
                refused = true;
                continue;
            }
            if (level.destroyBlock(pos, true, e)) broke++;
        }
        if (refused) {
            long now = level.getGameTime();
            Long said = BULLDOZE_SAID.get(e);
            ServerPlayer player = GeneClaims.answerable(e);
            if (player != null && (said == null || now - said >= BULLDOZE_LINE_EVERY)) {
                BULLDOZE_SAID.put(e, now);
                Refusals.deny(player, "gene.bulldoze.claim");
            }
        }
        return broke;
    }

    // --- Weeping Glands ----------------------------------------------------------------------------------------------

    private static void weep(LivingEntity e, ServerLevel level) {
        var data = e.getPersistentData();
        int worn = data.getInt(WEEP_WORN) + 1;
        if (worn >= GeneNumbers.WEEP_TEAR) {
            worn = 0;
            tear(e, level);
        }
        data.putInt(WEEP_WORN, worn);
        // the cost: a sob every few minutes (spread by the body's id, so a stable of hosts does not sob in chorus)
        if ((e.tickCount + e.getId() * 37) % GeneNumbers.WEEP_SOB == 0) sob(e, level);
    }

    /** A Ghast Tear: into a mecha's Cargo Hold (what does not fit falls out), else at the body's feet. */
    public static void tear(LivingEntity e, ServerLevel level) {
        ItemStack tear = new ItemStack(Items.GHAST_TEAR);
        if (e instanceof MechaEntity mecha && mecha.has(MechaModule.CARGO_HOLD)) tear = mecha.cargo().addItem(tear);
        if (!tear.isEmpty()) e.spawnAtLocation(tear);
        level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, SoundSource.PLAYERS, 1, 0.8f);
        Fx.send(level, Fx.of(Fx.GENE_WEEP, e.getEyePosition(), null, e.getId(), -1, 1));
    }

    /** A sob, heard by everyone near (a mecha's cockpit glass fogs for its pilot). */
    public static void sob(LivingEntity e, ServerLevel level) {
        level.playSound(null, e.getX(), e.getY(), e.getZ(), GeneSounds.SOB.get(), e.getSoundSource(), 0.8f, 1);
        Fx.send(level, Fx.of(Fx.GENE_WEEP, e.getEyePosition(), null, e.getId(), -1, 0));
    }

    // --- events ------------------------------------------------------------------------------------------------------

    /** The genes' hooks on the game's events (server side). */
    public static final class Events {
        private Events() {
        }

        /** No fall damage from a pounce (for the body, or what it rides). */
        @SubscribeEvent
        public static void fall(LivingFallEvent event) {
            LivingEntity e = event.getEntity();
            if (POUNCING.containsKey(e) || e.getPassengers().stream().anyMatch(POUNCING::containsKey)) {
                event.setDistance(0);
                event.setDamageMultiplier(0);
            }
        }

        @SubscribeEvent
        public static void damage(LivingIncomingDamageEvent event) {
            LivingEntity victim = event.getEntity();
            if (victim.level().isClientSide) return;
            DamageSource source = event.getSource();
            if (source.is(DamageTypeTags.IS_FALL) && POUNCING.containsKey(victim)) {
                event.setCanceled(true);
                return;
            }
            Entity direct = source.getDirectEntity();
            LivingEntity body = maker(direct);
            if (body == null) return;
            // our spit, fangs and bullets: never you; other players only with PvP (the spit never hurts a player)
            if (you(body, victim) || direct.getPersistentData().getBoolean(HARMLESS)
                    || victim instanceof Player && (direct instanceof LlamaSpit || !mayTouch((ServerLevel) victim.level(), victim))) {
                event.setCanceled(true);
                return;
            }
            // the fangs' vanilla 6 is a host's 70%, a mecha's in full
            if (direct instanceof EvokerFangs) event.setAmount(event.getAmount() * GeneEffects.scale(body));
        }

        @SubscribeEvent
        public static void impact(ProjectileImpactEvent event) {
            Projectile shot = event.getProjectile();
            if (shot.level().isClientSide || !(event.getRayTraceResult() instanceof EntityHitResult hit)) return;
            LivingEntity body = maker(shot);
            if (body == null) return;
            Entity what = hit.getEntity();
            if (shot instanceof LlamaSpit && what instanceof Mob mob && !shot.getPersistentData().getBoolean(HARMLESS)) {
                calm(mob, body);
                // Spitting Cobra: with Venom Sac on the same strand the spit poisons
                if (sameSerum(body, Gene.SPIT_GLAND, Gene.VENOM_SAC)) mob.addEffect(new MobEffectInstance(MobEffects.POISON, GeneNumbers.SPIT_COBRA_POISON, 0), body);
            }
            if (shot instanceof ShulkerBullet && what instanceof LivingEntity living) TRIM.put(living, shot.level().getGameTime());
        }

        /** A bolt of ours gives Levitation for 3 s, not vanilla's 10. */
        @SubscribeEvent
        public static void effect(MobEffectEvent.Applicable event) {
            LivingEntity e = event.getEntity();
            MobEffectInstance effect = event.getEffectInstance();
            if (e.level().isClientSide || effect == null || !effect.is(MobEffects.LEVITATION) || effect.getDuration() <= GeneNumbers.BOLT_LEVITATION) return;
            Long at = TRIM.get(e);
            if (at == null || at != e.level().getGameTime()) return;
            TRIM.remove(e);
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, GeneNumbers.BOLT_LEVITATION, effect.getAmplifier()), event.getEffectSource());
        }

        /** A creature spat at does not take you back as its target while it is calm. */
        @SubscribeEvent
        public static void target(LivingChangeTargetEvent event) {
            long[] c = CALM.get(event.getEntity());
            if (c == null || event.getNewAboutToBeSetTarget() == null || event.getEntity().level().getGameTime() >= c[0]) return;
            Entity body = event.getEntity().level().getEntity((int) c[2]);
            if (body != null && you(body, event.getNewAboutToBeSetTarget())) event.setCanceled(true);
        }

        /** Tongue's cost: you croak when you jump. */
        @SubscribeEvent
        public static void jump(LivingEvent.LivingJumpEvent event) {
            LivingEntity e = event.getEntity();
            if (e.level().isClientSide || !GeneEffects.has(e, Gene.TONGUE)) return;
            e.level().playSound(null, e.getX(), e.getY(), e.getZ(), GeneSounds.CROAK.get(), e.getSoundSource(), 0.7f, 0.9f + e.getRandom().nextFloat() * 0.2f);
        }

        /** Bulldoze: your knockback is half as strong again; knockback on you a third weaker. */
        @SubscribeEvent
        public static void knockback(LivingKnockBackEvent event) {
            LivingEntity e = event.getEntity();
            if (e.level().isClientSide) return;
            float k = 1;
            if (GeneEffects.has(e, Gene.BULLDOZE)) k *= GeneNumbers.BULLDOZE_KNOCK_IN;
            LivingEntity by = e.getLastHurtByMob();
            if (by != null && e.getLastHurtByMobTimestamp() == e.tickCount && GeneEffects.has(by, Gene.BULLDOZE)) k *= GeneNumbers.BULLDOZE_KNOCK_OUT;
            if (k != 1) event.setStrength(event.getStrength() * k);
        }
    }
}

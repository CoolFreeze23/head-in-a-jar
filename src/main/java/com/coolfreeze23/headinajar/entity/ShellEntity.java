package com.coolfreeze23.headinajar.entity;

import com.coolfreeze23.headinajar.host.HostData;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;


/**
 * A Utility Shell: a cheap robot body for a jar. It never decays, is always fully synced, and runs on the jar's charge.
 * The Lunk is strong, the Rover is fast with cargo, the Spider Walker climbs.
 */
public class ShellEntity extends PathfinderMob implements software.bernie.geckolib.animatable.GeoEntity {
    public enum Kind { LUNK, ROVER, SPIDER_WALKER }

    private final Kind kind;
    private final SimpleContainer cargo = new SimpleContainer(9);
    /**
     * The moments the Rover's clips are chosen from (game times, -1 for none), each sent once when it changes: boarded
     * (negative: left at), got off (time x 4 + where: 0 behind, 1 right, 2 left, 3 in front), crashed, sprinted. Never
     * saved.
     */
    private static final EntityDataAccessor<Long> BOARDED = SynchedEntityData.defineId(ShellEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> GOT_OFF = SynchedEntityData.defineId(ShellEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> CRASHED = SynchedEntityData.defineId(ShellEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> SPRINTED = SynchedEntityData.defineId(ShellEntity.class, EntityDataSerializers.LONG);
    /** The Lunk's toss: its last moment (time x 4 + {@link #TOSS} or {@link #TOSS_REFUSED}), or -1. Never saved. */
    private static final EntityDataAccessor<Long> TOSSED = SynchedEntityData.defineId(ShellEntity.class, EntityDataSerializers.LONG);
    public static final int TOSS = 0, TOSS_REFUSED = 1;
    /** What the Lunk's fists hold through the toss (entity id, -1 none), so every client keeps it there too. */
    private static final EntityDataAccessor<Integer> TOSS_HELD = SynchedEntityData.defineId(ShellEntity.class, EntityDataSerializers.INT);
    /** Server: what the Lunk's fists are tossing and how hard, pushed up at the clip's release. */
    private net.minecraft.world.entity.Entity tossTarget;
    private double tossPower;
    /**
     * The Spider Walker's web: its last moment (time x 2 + 1 for a dud) and where the web goes; the last blow (time x 2 +
     * 1 when it came from the shell's right). Synced, never saved.
     */
    private static final EntityDataAccessor<Long> WEB = SynchedEntityData.defineId(ShellEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<net.minecraft.core.BlockPos> WEB_AT = SynchedEntityData.defineId(ShellEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Long> HIT = SynchedEntityData.defineId(ShellEntity.class, EntityDataSerializers.LONG);
    /** Server: a web on its way (placed when the spit lands), and when. */
    private net.minecraft.core.BlockPos webDue;
    private long webDueAt;
    private net.minecraft.server.level.ServerPlayer webBy;
    /** Where a jar getting out was put, as the place's number: behind, the body's right, its left, in front. */
    public static final int BEHIND = 0, RIGHT = 1, LEFT = 2, FRONT = 3;
    private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache geoCache = software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);
    /** Client: what this client measures of the Rover for its clips (wheels, steering, the last pilot). */
    public final RoverAnimation.View view = new RoverAnimation.View();
    /** Client: what this client measures of the Spider Walker for its clips (its legs, its wall, the last pilot). */
    public final SpiderWalkerAnimation.View walker = new SpiderWalkerAnimation.View();

    public ShellEntity(EntityType<? extends ShellEntity> type, Level level, Kind kind) {
        super(type, level);
        this.kind = kind;
        HostData data = Hosts.data(this);
        data.host = true;
        data.shell = true;
        data.handshaken = true;
        setPersistenceRequired();
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        for (var key : java.util.List.of(BOARDED, GOT_OFF, CRASHED, SPRINTED, TOSSED, WEB, HIT)) builder.define(key, -1L);
        builder.define(TOSS_HELD, -1);
        builder.define(WEB_AT, net.minecraft.core.BlockPos.ZERO);
    }

    /** The web's last moment: game time x 2 + 1 for a dud, or -1. */
    public long webAt() {
        return entityData.get(WEB);
    }

    /** Where the last web went. */
    public net.minecraft.core.BlockPos webTarget() {
        return entityData.get(WEB_AT);
    }

    /** The last blow: game time x 2 + 1 when it came from the right, or -1. */
    public long hitAt() {
        return entityData.get(HIT);
    }

    /**
     * The web shot: the clip rears and spits, and the cobweb is placed when the spit lands (the clip's tick 4), if
     * the spot is still free then.
     */
    public void spit(net.minecraft.core.BlockPos at, net.minecraft.server.level.ServerPlayer by) {
        if (level().isClientSide) return;
        entityData.set(WEB_AT, at);
        entityData.set(WEB, level().getGameTime() * 2);
        webDue = at;
        webDueAt = level().getGameTime() + SpiderWalkerAnimation.SPIT;
        webBy = by;
    }

    /** A refused web: it rears, the nozzle coughs, and it taps the nozzle (the line says why). */
    public void webDud() {
        if (!level().isClientSide && kind == Kind.SPIDER_WALKER) entityData.set(WEB, level().getGameTime() * 2 + 1);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide) {
            // which side the blow came from: the flinch is authored for the left and mirrored for the right
            Vec3 from = source.getSourcePosition();
            boolean right = from != null && from.subtract(position()).dot(Vec3.directionFromRotation(0, yBodyRot + 90)) > 0;
            entityData.set(HIT, level().getGameTime() * 2 + (right ? 1 : 0));
        }
        return hurt;
    }

    public long boardedAt() {
        return entityData.get(BOARDED);
    }

    /** The last getting-off: game time x 4 + the place ({@link #BEHIND}, ...), or -1. */
    public long gotOffAt() {
        return entityData.get(GOT_OFF);
    }

    public long crashedAt() {
        return entityData.get(CRASHED);
    }

    public long sprintedAt() {
        return entityData.get(SPRINTED);
    }

    /** The sprint ability fired (its clip plays). */
    public void markSprint() {
        if (!level().isClientSide) entityData.set(SPRINTED, level().getGameTime());
    }

    /** The entity the Lunk's fists hold through its toss, or -1. Synced, never saved. */
    public int tossHeldId() {
        return entityData.get(TOSS_HELD);
    }

    public long tossAt() {
        return entityData.get(TOSSED);
    }

    /**
     * The Lunk's toss: the clip plays from now and the push comes at its release (tick 19), when the fists let go; the
     * target is pushed then only if it is still alive and near.
     */
    public void startToss(net.minecraft.world.entity.Entity target, double power) {
        tossTarget = target;
        tossPower = power;
        entityData.set(TOSS_HELD, target.getId());
        entityData.set(TOSSED, level().getGameTime() * 4 + TOSS);
    }

    /** The Lunk's toss was refused: the arms come up to scoop and drop. Not while a toss is still playing. */
    public void tossRefused() {
        long at = tossAt();
        if (at >= 0 && (at & 3) == TOSS && level().getGameTime() - at / 4 < LunkAnimation.TOSS_TICKS) return;
        entityData.set(TOSSED, level().getGameTime() * 4 + TOSS_REFUSED);
    }

    /** Every Utility Shell is drawn by GeckoLib from its own model: the Rover, the Lunk and the Spider Walker. */
    public boolean animated() {
        return kind == Kind.ROVER || kind == Kind.LUNK || kind == Kind.SPIDER_WALKER;
    }

    @Override
    public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
        if (kind == Kind.ROVER) RoverAnimation.register(this, controllers);
        else if (kind == Kind.LUNK) LunkAnimation.register(this, controllers);
        else if (kind == Kind.SPIDER_WALKER) SpiderWalkerAnimation.register(this, controllers);
    }

    @Override
    public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    @Override
    protected void addPassenger(net.minecraft.world.entity.Entity passenger) {
        boolean first = getPassengers().isEmpty();
        super.addPassenger(passenger);
        if (!level().isClientSide && first && passenger instanceof net.minecraft.world.entity.player.Player) entityData.set(BOARDED, level().getGameTime());
    }

    @Override
    protected void removePassenger(net.minecraft.world.entity.Entity passenger) {
        boolean pilot = passenger == getFirstPassenger();
        super.removePassenger(passenger);
        if (!level().isClientSide && pilot && passenger instanceof net.minecraft.world.entity.player.Player) entityData.set(BOARDED, -level().getGameTime());
    }

    /**
     * Drawn past its box: the Rover's aerial and pennant and the jar on top; the Spider Walker's legs (2.4 blocks across)
     * and its front legs raised for the web.
     */
    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        if (kind == Kind.LUNK) return getBoundingBox().inflate(0.35, 0, 0.35).expandTowards(0, 0.8, 0);
        if (kind == Kind.SPIDER_WALKER) return getBoundingBox().inflate(0.5, 0, 0.5).expandTowards(0, 1.3, 0);
        return animated() ? getBoundingBox().inflate(0.2, 0, 0.2).expandTowards(0, 1.3, 0) : super.getBoundingBoxForCulling();
    }

    /**
     * The Rover comes apart by its own clip (a castor off, the tray down on that corner), the Spider Walker kicks itself
     * over onto its back and curls its legs, before it goes.
     */
    @Override
    protected void tickDeath() {
        if (!animated()) {
            super.tickDeath();
            return;
        }
        int length = switch (kind) {
            case SPIDER_WALKER -> SpiderWalkerAnimation.DESTROYED;
            case LUNK -> LunkAnimation.DESTROYED;
            default -> RoverAnimation.DESTROYED;
        };
        if (++deathTime >= length && !level().isClientSide && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    public static AttributeSupplier.Builder lunk() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 60).add(Attributes.MOVEMENT_SPEED, 0.18)
                .add(Attributes.ATTACK_DAMAGE, 9).add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.STEP_HEIGHT, 1.0)
                .add(Attributes.ENTITY_INTERACTION_RANGE, 4.5);
    }

    public static AttributeSupplier.Builder rover() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.MOVEMENT_SPEED, 0.38)
                .add(Attributes.ATTACK_DAMAGE, 2).add(Attributes.STEP_HEIGHT, 1.5);
    }

    public static AttributeSupplier.Builder spiderWalker() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ATTACK_DAMAGE, 4).add(Attributes.STEP_HEIGHT, 1.0).add(Attributes.SAFE_FALL_DISTANCE, 64);
    }

    /**
     * A ridden shell's speed attribute is read as a mob's walking pace; ridden, the rider's full input would make it four
     * times a sprint. Ridden, a shell goes at this share of it: the Rover fast, the Spider Walker brisk, the Lunk slow.
     */
    public static final double RIDDEN_SCALE = 0.5;
    /** Blocks a tick (about six a second) at or above which running into a wall is a crash. */
    public static final double CRASH_SPEED = 0.3;
    private Vec3 lastPos;
    private double lastSpeed;
    /** The last three ticks' speeds: a rider's moves can arrive two to a tick and none the next, so the pace is their peak. */
    private final double[] recent = new double[3];
    private int recentAt;
    private Vec3 lastMove = Vec3.ZERO;
    private int crashCooldown;

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        // the Lunk's toss: held in the fists from the scoop, pushed at the clip's release, when the fists let go
        long toss = tossAt();
        if (tossTarget != null && toss >= 0 && (toss & 3) == TOSS) {
            long t = level().getGameTime() - toss / 4;
            if (t >= LunkAnimation.TOSS_RELEASE) {
                var target = tossTarget;
                tossTarget = null;
                HELD.remove(target);
                entityData.set(TOSS_HELD, -1);
                if (target.isAlive() && target.distanceToSqr(this) < 6 * 6) {
                    // up, and a little forward so it clears the cabinet it was held over
                    Vec3 ahead = Vec3.directionFromRotation(0, yBodyRot).scale(LunkAnimation.TOSS_FORWARD);
                    target.setDeltaMovement(ahead.x, tossPower, ahead.z);
                    target.hurtMarked = true;
                }
            } else if (t >= LunkAnimation.TOSS_GRAB) {
                hold(tossTarget, t);
            }
        }
        if (webDue != null && level().getGameTime() >= webDueAt) web();
        // a rider moves the shell from their client (the client stops it at the wall, so the server never sees it
        // collide): its speed is read between ticks, and a crash is a fast shell stopped with a solid block just ahead
        Vec3 moved = lastPos == null ? Vec3.ZERO : position().subtract(lastPos).multiply(1, 0, 1);
        double speed = moved.length();
        if (crashCooldown > 0) crashCooldown--;
        double pace = 0;
        for (double s : recent) pace = Math.max(pace, s);
        if (pace >= CRASH_SPEED && speed < pace * 0.5 && crashCooldown == 0 && getFirstPassenger() instanceof ServerPlayer pilot
                && (horizontalCollision || !level().noCollision(this, getBoundingBox().move(lastMove.normalize().scale(0.25))))) {
            crash(pilot);
        }
        lastSpeed = speed;
        recent[recentAt++ % recent.length] = speed;
        if (speed > 1e-3) lastMove = moved;
        lastPos = position();
    }

    /** The spit lands: the cobweb where it was aimed, if that spot is still free air (it melts after 10 seconds). */
    private void web() {
        var at = webDue;
        var by = webBy;
        webDue = null;
        webBy = null;
        if (!(level() instanceof net.minecraft.server.level.ServerLevel server)) return;
        if (!server.getBlockState(at).isAir() || by != null && !server.mayInteract(by, at)) return;
        var web = net.minecraft.world.level.block.Blocks.COBWEB.defaultBlockState();
        server.setBlockAndUpdate(at, web);
        com.coolfreeze23.headinajar.host.WebShots.get(server).add(at, server.getGameTime() + 200);
        var sound = web.getSoundType();
        server.playSound(null, at, sound.getPlaceSound(), net.minecraft.sounds.SoundSource.NEUTRAL, sound.getVolume(), sound.getPitch());
    }

    /** Into a wall at speed: one bump to the jar's glass, a clang and a line, then a moment before the next one counts. */

    private void crash(ServerPlayer pilot) {
        crashCooldown = 40;
        entityData.set(CRASHED, level().getGameTime());
        var state = com.coolfreeze23.headinajar.form.FormActions.state(pilot);
        if (state.integrity() > 1) state.setIntegrity(state.integrity() - 1);
        level().playSound(null, getX(), getY(), getZ(), com.coolfreeze23.headinajar.fx.HouseSounds.SHELL_CRASH.get(), net.minecraft.sounds.SoundSource.NEUTRAL, 0.8f, 1.4f);
        level().playSound(null, pilot.getX(), pilot.getY(), pilot.getZ(), com.coolfreeze23.headinajar.fx.HouseSounds.JAR_KNOCK.get(), net.minecraft.sounds.SoundSource.PLAYERS, 1, 0.9f);
        if (level() instanceof net.minecraft.server.level.ServerLevel server) {
            // at the bumper's face (the Rover's fx_bumper, 10.5 px ahead and 9 up); the other shells at their front
            Vec3 ahead = Vec3.directionFromRotation(0, yBodyRot);
            Vec3 front = kind == Kind.ROVER ? position().add(ahead.scale(10.5 / 16)).add(0, 9 / 16.0, 0)
                    : position().add(getLookAngle().multiply(1, 0, 1).scale(getBbWidth() * 0.6)).add(0, getBbHeight() * 0.5, 0);
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, front.x, front.y, front.z, 8, 0.2, 0.2, 0.2, 0.1);
        }
        pilot.displayClientMessage(Component.translatable("message.headinajar.shell_crash", getDisplayName()), true);
    }

    /**
     * Where a jar getting out of a wide body stands: just clear of its box, behind it or to a side (in front last),
     * on ground if there is any, so a click on the jar, or on what it leaves there, reaches it and not the body.
     */
    public static Vec3 clearOf(net.minecraft.world.entity.Entity vehicle, net.minecraft.world.entity.LivingEntity passenger) {
        Exit exit = exit(vehicle, passenger);
        return exit == null ? null : exit.at();
    }

    /** Where {@link #clearOf} put the jar, and which of its four places that was ({@link #BEHIND}, ...). */
    public record Exit(Vec3 at, int place) {
    }

    /** As {@link #clearOf}, saying which place it chose; null when none is clear. */
    public static Exit exit(net.minecraft.world.entity.Entity vehicle, net.minecraft.world.entity.LivingEntity passenger) {
        var level = vehicle.level();
        // sized as the plain jar it is about to be (a pilot is player-sized in the cockpit)
        var box = passenger instanceof net.minecraft.world.entity.player.Player player && player.hasData(ModRegistry.JAR_STATE)
                && player.getData(ModRegistry.JAR_STATE).form().isJarred()
                ? (player.getData(ModRegistry.JAR_STATE).compact() ? com.coolfreeze23.headinajar.form.FormEvents.COMPACT_SIZE
                        : com.coolfreeze23.headinajar.form.FormEvents.JAR_SIZE).makeBoundingBox(Vec3.ZERO)
                : passenger.getBoundingBox().move(passenger.position().reverse());
        double reach = vehicle.getBbWidth() / 2 + box.getXsize() / 2 + 0.1;
        // square to the box (whose sides are the world's axes), the side nearest behind the body first
        var facing = net.minecraft.core.Direction.fromYRot(vehicle.getYRot());
        Exit fallback = null;
        var order = new net.minecraft.core.Direction[]{facing.getOpposite(), facing.getClockWise(), facing.getCounterClockWise(), facing};
        // the Lunk lifts its jar down to the floor in front of it: in front first there, then the others in order
        int[] tries = vehicle instanceof ShellEntity s && s.kind == Kind.LUNK ? new int[]{FRONT, BEHIND, RIGHT, LEFT} : new int[]{BEHIND, RIGHT, LEFT, FRONT};
        for (int place : tries) {
            var dir = order[place];
            Vec3 at = new Vec3(vehicle.getX() + dir.getStepX() * reach, vehicle.getY(), vehicle.getZ() + dir.getStepZ() * reach);
            if (!level.noCollision(passenger, box.move(at))) continue;
            boolean ground = !level.noCollision(passenger, box.move(at).move(0, -1.1, 0).setMaxY(at.y));
            if (ground) return new Exit(at, place);
            if (fallback == null) fallback = new Exit(at, place);
        }
        return fallback;
    }

    /** What a Lunk holds in its fists now, by the load: kept at the fists after the load's own tick too. */
    static final java.util.Map<net.minecraft.world.entity.Entity, ShellEntity> HELD = new java.util.WeakHashMap<>();

    /**
     * Puts a tossed creature in the fists at this tick of the toss (the clip's grip point, its feet on the fists), as the
     * Bulwark's overhead carry does; a load too big for the fists, or with no room there, is left where it is until the
     * release. A player is moved by teleport (they move themselves). Clients keep it there too between the server's updates.
     */
    public void hold(net.minecraft.world.entity.Entity load, double t) {
        if (!load.isAlive() || load.isPassenger() || load.isVehicle() || load.getBbWidth() > 1.5f || load.getBbHeight() > 2.0f
                || load.distanceToSqr(this) > 6 * 6) {
            HELD.remove(load);
            return;
        }
        Vec3 px = LunkAnimation.grip(t);
        Vec3 ahead = Vec3.directionFromRotation(0, yBodyRot);
        Vec3 at = position().add(ahead.scale(-px.z / 16)).add(0, px.y / 16, 0);
        var box = load.getDimensions(load.getPose()).makeBoundingBox(at);
        if (!level().noCollision(load, box)) return;
        load.setDeltaMovement(Vec3.ZERO);
        load.fallDistance = 0;
        if (level().isClientSide) {
            load.setPos(at.x, at.y, at.z);
            return;
        }
        HELD.put(load, this);
        if (load instanceof ServerPlayer player) player.teleportTo(at.x, at.y, at.z);
        else load.setPos(at.x, at.y, at.z);
        com.coolfreeze23.headinajar.mecha.Aloft.carried(load);
    }

    /** After a held load's own tick (its gravity, its AI): back in the fists, so every client is sent the hold point. */
    @net.neoforged.fml.common.EventBusSubscriber(modid = com.coolfreeze23.headinajar.HeadInAJar.MOD_ID)
    public static final class Holds {
        private Holds() {
        }

        @net.neoforged.bus.api.SubscribeEvent
        public static void afterTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) {
            var load = event.getEntity();
            if (load.level().isClientSide || HELD.isEmpty()) return;
            ShellEntity lunk = HELD.get(load);
            if (lunk == null) return;
            long toss = lunk.tossAt();
            double t = toss < 0 ? -1 : lunk.level().getGameTime() - toss / 4;
            if (lunk.isRemoved() || lunk.tossTarget != load || t < LunkAnimation.TOSS_GRAB || t >= LunkAnimation.TOSS_RELEASE) {
                HELD.remove(load);
                return;
            }
            lunk.hold(load, t);
        }
    }

    /**
     * A jar getting out stands beside the shell, not inside its box (where it would board it again at its next click).
     * The Lunk turns to face the place chosen before its clip lifts the jar down there (it has one clip: in front).
     */
    @Override
    public Vec3 getDismountLocationForPassenger(net.minecraft.world.entity.LivingEntity passenger) {
        Exit exit = exit(this, passenger);
        if (kind == Kind.LUNK && exit != null && exit.place() != FRONT && !level().isClientSide) {
            Vec3 d = exit.at().subtract(position());
            float yaw = (float) (Math.atan2(-d.x, d.z) * 180 / Math.PI);
            setYRot(yaw);
            setYBodyRot(yaw);
            setYHeadRot(yaw);
        }
        // the Rover's get-off clip goes the way the jar was put (behind when nowhere was clear)
        if (!level().isClientSide && passenger instanceof net.minecraft.world.entity.player.Player) {
            entityData.set(GOT_OFF, level().getGameTime() * 4 + (exit == null ? BEHIND : exit.place()));
        }
        return exit != null ? exit.at() : super.getDismountLocationForPassenger(passenger);
    }

    public void openCargo(ServerPlayer player) {
        if (kind != Kind.ROVER) {
            player.displayClientMessage(Component.translatable("message.headinajar.no_cargo"), true);
            return;
        }
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x1, id, inv, new EntityCargo(cargo, this), 1), getDisplayName()));
    }

    private net.minecraft.world.item.Item chassis() {
        return switch (kind) {
            case LUNK -> ModRegistry.LUNK_CHASSIS.get();
            case ROVER -> ModRegistry.ROVER_CHASSIS.get();
            case SPIDER_WALKER -> ModRegistry.SPIDER_WALKER_CHASSIS.get();
        };
    }

    @Override
    protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        spawnAtLocation(new ItemStack(ModRegistry.SERVO.get(), 2));
        spawnAtLocation(new ItemStack(ModRegistry.SURGICAL_STEEL_INGOT.get(), 3));
        Containers.dropContents(level, blockPosition(), cargo);
    }

    /** A shell has no lungs. */
    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return kind != Kind.SPIDER_WALKER && super.causeFallDamage(distance, multiplier, source);
    }

    @Override
    public boolean onClimbable() {
        return kind == Kind.SPIDER_WALKER && horizontalCollision || super.onClimbable();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Cargo", cargo.createTag(registryAccess()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        cargo.fromTag(tag.getList("Cargo", 10), registryAccess());
        HostData data = Hosts.data(this);
        data.host = true;
        data.shell = true;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(chassis());
    }
}

package com.coolfreeze23.headinajar.entity;

import com.coolfreeze23.headinajar.host.HostData;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.item.MechaModuleItem;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaLogic;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * A mecha: a frame with modules, its own FE battery, heat and mass. A jar pilots it from the cockpit (PILOT form) through
 * the same vehicle control as host bodies; its arms answer the pilot's mouse buttons.
 */
public class MechaEntity extends PathfinderMob implements software.bernie.geckolib.animatable.GeoEntity {
    /**
     * The moments an animated frame's clips are chosen from (game times, -1 for none), each sent once when it changes:
     * boarded (negative: left at), built, pogo charge started, leap, pogo landing, ejected, an arm's dry fire (time x 2 +
     * side). Never saved: a build or a jump in progress ends with the session.
     */
    private static final EntityDataAccessor<Long> BOARDED = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> BUILT = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> POGO = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> LEAP = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> LANDED = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> EJECTED = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DRY = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.LONG);
    /** The last hit (time x 2 + 1 when it came from the machine's right) and the last ordinary landing (time x 2 + 1 heavy). */
    private static final EntityDataAccessor<Long> HIT = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> LAND = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.LONG);
    /** Heavy Lifting: the last lift moment (time x 4 + grab, refused, throw or set down) and the held load's id (-1 none). */
    private static final EntityDataAccessor<Long> LIFT = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> HELD = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.INT);
    /** A module's last one-off action (a utility fired, the hold opened, a dose given): game time x 64 + the module. */
    private static final EntityDataAccessor<Long> ACT = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.LONG);
    /** What each arm's Tractor Beam holds: (left id + 1) | (right id + 1) << 32, 0 for nothing. Synced, never saved. */
    private static final EntityDataAccessor<Long> BEAMS = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.LONG);
    /** The frame's own verb's last moment (time x 8 + kind: Siege's, the jingle's) and the Gene Ports resting after a gene fired (a bit each). */
    private static final EntityDataAccessor<Long> VERB = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Byte> PORTS = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.BYTE);
    /** Server: how far the machine moved over the last ticks, chased (blocks a tick; Siege starts only from a standstill). */
    public double siegeSpeed;
    public Vec3 siegeLast;
    /** Rush Hour (the Wheelhouse): the last press (time x 4 + 0 boosting, 1 refused) and the buffer's last ram (time). */
    private static final EntityDataAccessor<Long> RUSH = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> RAM = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.LONG);
    /** The Chimera's Graft: the grafted gene (time taken x 256 + the gene) and the blood colour its veins run in. */
    private static final EntityDataAccessor<Long> GRAFT = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> GRAFT_TINT = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.INT);
    /** Server: the arm whose tick runs now (-1 between), what an arm last hit (its type, blood colour, arm and time). */
    public int armActing = -1;
    public EntityType<?> tasted;
    public int tastedBlood, tastedSide;
    public long tastedAt;
    /** Server: the gene a Graft under way will take at its tick 14, and its colour. */
    public com.coolfreeze23.headinajar.genetics.Gene graftPending;
    public int graftPendingTint;
    /**
     * The Dragline (the Skitter): its last moment (time x 4 + fired, cut or refused), the anchor it is fixed to, the
     * line's length (0: no line; -1: slack, following the machine) and what the reel does (slack, winding in, paying out,
     * braked). Synced, never saved: a line in use is let go when the session ends.
     */
    private static final EntityDataAccessor<Long> LINE = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<net.minecraft.core.BlockPos> ANCHOR_BLOCK = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<org.joml.Vector3f> ANCHOR = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> LINE_LEN = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> LINE_MODE = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.BYTE);
    /** Server: when the drive key went down with the line out (a tap cuts, a hold reels in), or -1. */
    public long linePressAt = -1;
    /** Server: the block the line is fixed to (it is lost if that block goes). */
    public net.minecraft.core.BlockPos lineBlock;
    /** Server: the pilot has been told this time on a wall that it cannot hold on (flat or venting). */
    public boolean clingSaid;
    /** Where it is moved (its pilot's client): the pilot sneaked off this wall; it holds on again once off it or pushed into it. */
    public boolean clingLetGo;
    /** Server: when the last boost ended (the cooldown runs from it), and when each creature was last rammed. */
    public long rushEnded = Long.MIN_VALUE / 2;
    public Vec3 rushLastPos;
    public final java.util.Map<java.util.UUID, Long> rammed = new java.util.HashMap<>();
    /** A winged frame: the takeoff's start (game time, -1 none) and whether the pilot holds the climb (bit 0). Never saved. */
    private static final EntityDataAccessor<Long> TAKEOFF = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Byte> FLY = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.BYTE);
    /** Server: a winged frame's place a tick ago, its motion since, and how fast it was coming down before it touched the ground. */
    public Vec3 lastPos, motion = Vec3.ZERO;
    public double sinkRate;
    /** Server: what the fists are reaching for (an entity, or a block position) and where the load was when taken. */
    public Entity liftTarget;
    public net.minecraft.core.BlockPos liftBlock;
    public Vec3 liftFrom;
    /** Server: the last throw's velocity (for checks). */
    public Vec3 lastThrow = Vec3.ZERO;
    /** Server: ticks in the air and the highest point since leaving the ground (for the landing's weight). */
    public int airTicks;
    public double peakY, lastDrop;
    private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache geoCache = software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);
    /** Server: the pogo leap's impulse is due (the tick after the release), and its charge. */
    public float pogoCharge = -1;
    /** Server: in the air from its own leap (no fall damage until it lands), and whether it has left the ground yet. */
    public boolean pogoFlight, pogoAirborne;
    /** Server: the last push given through MechaLogic.impulse. */
    public net.minecraft.world.phys.Vec3 lastImpulse = net.minecraft.world.phys.Vec3.ZERO;
    /** Server: the last leap's launch velocity (the pilot's client applies it). */
    public net.minecraft.world.phys.Vec3 launch = net.minecraft.world.phys.Vec3.ZERO;
    /** Entity event: the Jump Jets fired (an animated frame's jets kick back and snuff a pilot lamp). */
    public static final byte JET_BURST = 108;

    @Override
    public void handleEntityEvent(byte id) {
        if (id == JET_BURST) {
            view.jetAt = level().getGameTime();
            view.jetsUsed++;
            return;
        }
        super.handleEntityEvent(id);
    }

    /** Client: what this client measures of the machine for its clips (speed, walk phase, landings, arm presses). */
    public final com.coolfreeze23.headinajar.mecha.HopperView view = new com.coolfreeze23.headinajar.mecha.HopperView();
    private static final EntityDataAccessor<Integer> BATTERY = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> HEAT = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> VENT = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> MODULES = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Byte> ARMS = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> UTILITY = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.INT);

    private final MechaFrame frame;
    private final NonNullList<ItemStack> modules;
    private final SimpleContainer cargo = new SimpleContainer(27);
    private UUID owner;
    /** Server: arm buttons the pilot holds (bit 0 left, bit 1 right) and the previous state, for press edges. */
    public int armsHeld, armsBefore;
    public boolean ejecting;
    public int jetsLeft = 3, jetRecharge;
    /** Server-side work state for arms. */
    public final MechaLogic.ArmState[] armState = {new MechaLogic.ArmState(), new MechaLogic.ArmState(), new MechaLogic.ArmState(), new MechaLogic.ArmState()};

    public MechaEntity(EntityType<? extends MechaEntity> type, Level level, MechaFrame frame) {
        super(type, level);
        this.frame = frame;
        this.modules = NonNullList.withSize(frame.totalSlots(), ItemStack.EMPTY);
        HostData data = Hosts.data(this);
        data.host = true;
        data.shell = true;
        data.handshaken = true;
        setPersistenceRequired();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(frame.health);
        getAttribute(Attributes.ARMOR).setBaseValue(frame.armor);
        setHealth(frame.health);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 140).add(Attributes.MOVEMENT_SPEED, 0.13)
                .add(Attributes.ATTACK_DAMAGE, 4).add(Attributes.KNOCKBACK_RESISTANCE, 0.8).add(Attributes.STEP_HEIGHT, 1.0)
                .add(Attributes.ARMOR, 6).add(Attributes.ENTITY_INTERACTION_RANGE, 5);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BATTERY, 0);
        builder.define(HEAT, 0f);
        builder.define(VENT, 0);
        builder.define(MODULES, new CompoundTag());
        builder.define(ARMS, (byte) 0);
        builder.define(UTILITY, 0);
        for (var key : java.util.List.of(BOARDED, BUILT, POGO, LEAP, LANDED, EJECTED, DRY, HIT, LAND, LIFT, VERB, RUSH, RAM, GRAFT)) builder.define(key, -1L);
        builder.define(GRAFT_TINT, 0);
        builder.define(HELD, -1);
        builder.define(ACT, -1L);
        builder.define(BEAMS, 0L);
        builder.define(PORTS, (byte) 0);
        builder.define(LINE, -1L);
        builder.define(ANCHOR_BLOCK, net.minecraft.core.BlockPos.ZERO);
        builder.define(ANCHOR, new org.joml.Vector3f());
        builder.define(LINE_LEN, 0f);
        builder.define(LINE_MODE, (byte) 0);
        builder.define(TAKEOFF, -1L);
        builder.define(FLY, (byte) 0);
    }

    // --- the moments the clips are chosen from (animated frames) -----------------------------------------

    public long boardedAt() {
        return entityData.get(BOARDED);
    }

    public long builtAt() {
        return entityData.get(BUILT);
    }

    /** Starts the build sequence (the Gantry's assembly), which boarding skips. */
    public void startBuild() {
        if (frame.animated()) entityData.set(BUILT, level().getGameTime());
    }

    public void endBuild() {
        entityData.set(BUILT, -1L);
    }

    public long pogoAt() {
        return entityData.get(POGO);
    }

    public void setPogoAt(long time) {
        entityData.set(POGO, time);
    }

    public long leapAt() {
        return entityData.get(LEAP);
    }

    public void setLeapAt(long time) {
        entityData.set(LEAP, time);
    }

    public long landedAt() {
        return entityData.get(LANDED);
    }

    public void setLandedAt(long time) {
        entityData.set(LANDED, time);
    }

    public long ejectedAt() {
        return entityData.get(EJECTED);
    }

    public void setEjectedAt(long time) {
        entityData.set(EJECTED, time);
    }

    public long hitAt() {
        return entityData.get(HIT);
    }

    public long landAt() {
        return entityData.get(LAND);
    }

    public void setLandAt(long time, boolean heavy) {
        entityData.set(LAND, time * 2 + (heavy ? 1 : 0));
    }

    /** Heavy Lifting's last moment: game time x 4 + kind ({@code Bulwark.GRAB}, ...), or -1. */
    public long liftAt() {
        return entityData.get(LIFT);
    }

    public void setLiftAt(long time, int kind) {
        entityData.set(LIFT, time * 4 + (kind & 3));
    }

    /** The frame's own verb's last moment: game time x 8 + kind ({@code Siege.ENTER}, ...), or -1. Never saved. */
    public long verbAt() {
        return entityData.get(VERB);
    }

    public void setVerbAt(long time, int kind) {
        entityData.set(VERB, time < 0 ? -1L : time * 8 + (kind & 7));
    }

    /** Gene Ports whose gene is resting after it fired (bit n: port n). */
    public int portsResting() {
        return entityData.get(PORTS) & 0xFF;
    }

    public void setPortsResting(int mask) {
        if (portsResting() != (mask & 0xFF)) entityData.set(PORTS, (byte) mask);
    }

    /** The entity the fists hold, or -1. Never saved: a load is let go when the session ends. */
    public int heldId() {
        return entityData.get(HELD);
    }

    public void setHeldId(int id) {
        entityData.set(HELD, id);
    }

    /** A winged frame's takeoff: when the crouch began (it leaves the ground at the clip's push), or -1. */
    public long takeoffAt() {
        return entityData.get(TAKEOFF);
    }

    public void setTakeoffAt(long time) {
        entityData.set(TAKEOFF, time);
    }

    /** A winged frame's pilot holds the climb (the jump key), as the server last saw it. */
    public boolean climbing() {
        return (entityData.get(FLY) & 1) != 0;
    }

    public void setClimbing(boolean held) {
        entityData.set(FLY, (byte) (held ? 1 : 0));
    }

    /** A module's last one-off action: game time x 64 + the module's ordinal, or -1. Synced, never saved. */
    public long actAt() {
        return entityData.get(ACT);
    }

    /** The entity arm {@code side}'s Tractor Beam holds (0 left, 1 right), or -1. */
    public int beamHeld(int side) {
        long v = entityData.get(BEAMS);
        return (int) ((side == 0 ? v : v >>> 32) & 0xFFFFFFFFL) - 1;
    }

    public void setBeamHeld(int side, int id) {
        if (side > 1) return;
        long v = entityData.get(BEAMS), part = (id + 1L) & 0xFFFFFFFFL;
        v = side == 0 ? (v & 0xFFFFFFFF00000000L) | part : (v & 0xFFFFFFFFL) | part << 32;
        if (v != entityData.get(BEAMS)) entityData.set(BEAMS, v);
    }

    /** A fitted module did its one-off action now: every client plays its clip from this moment. */
    public void act(MechaModule module) {
        if (!level().isClientSide) entityData.set(ACT, level().getGameTime() * 64 + module.ordinal());
    }

    /** Rush Hour's last press: game time x 4 + kind ({@code Wheelhouse.BOOST}, {@code REFUSED}), or -1. */
    public long rushAt() {
        return entityData.get(RUSH);
    }

    public void setRushAt(long time, int kind) {
        entityData.set(RUSH, time * 4 + (kind & 3));
    }

    /** The buffer beam's last ram (game time), or -1. */
    public long ramAt() {
        return entityData.get(RAM);
    }

    public void setRamAt(long time) {
        entityData.set(RAM, time);
    }

    /** The grafted gene: time taken x 256 + the gene's ordinal, or -1. Synced, never saved. */
    public long graftAt() {
        return entityData.get(GRAFT);
    }

    public int graftTint() {
        return entityData.get(GRAFT_TINT);
    }

    public void setGraft(long time, com.coolfreeze23.headinajar.genetics.Gene gene, int tint) {
        entityData.set(GRAFT, gene == null ? -1L : time * 256 + gene.ordinal());
        entityData.set(GRAFT_TINT, tint);
    }

    /** The Dragline's last moment: game time x 4 + kind ({@code Skitter.FIRED}, {@code CUT}, {@code REFUSED}), or -1. */
    public long lineAt() {
        return entityData.get(LINE);
    }

    public void setLineAt(long time, int kind) {
        entityData.set(LINE, time < 0 ? -1L : time * 4 + (kind & 3));
    }

    /** Where the line is fixed (world coordinates): a block, and the point within it (exact far from the origin). */
    public Vec3 anchor() {
        net.minecraft.core.BlockPos b = entityData.get(ANCHOR_BLOCK);
        org.joml.Vector3f a = entityData.get(ANCHOR);
        return new Vec3(b.getX() + (double) a.x, b.getY() + (double) a.y, b.getZ() + (double) a.z);
    }

    public void setAnchor(Vec3 at) {
        net.minecraft.core.BlockPos b = net.minecraft.core.BlockPos.containing(at);
        entityData.set(ANCHOR_BLOCK, b);
        entityData.set(ANCHOR, new org.joml.Vector3f((float) (at.x - b.getX()), (float) (at.y - b.getY()), (float) (at.z - b.getZ())));
    }

    /** The line's length in blocks: 0 no line, -1 slack (paid out or taken up as the machine moves). */
    public float lineLength() {
        return entityData.get(LINE_LEN);
    }

    public void setLineLength(float blocks) {
        if (entityData.get(LINE_LEN) != blocks) entityData.set(LINE_LEN, blocks);
    }

    /** What the reel does now: {@code Skitter.SLACK}, {@code REEL}, {@code RAPPEL}, {@code HANG}. */
    public int lineMode() {
        return entityData.get(LINE_MODE);
    }

    public void setLineMode(int mode) {
        if (entityData.get(LINE_MODE) != mode) entityData.set(LINE_MODE, (byte) mode);
    }

    /** The last dry fire: game time x 2 + side (0 left, 1 right), or -1. */
    public long dryAt() {
        return entityData.get(DRY);
    }

    /** An arm says no: it winds up, clicks and sags (shown by the arm's clip; the click is played here). */
    public void dryFire(int side) {
        if (!frame.animated() || level().isClientSide) return;
        long now = level().getGameTime();
        if (dryAt() >= 0 && now - dryAt() / 2 < 6) return;
        // a frame with one tool says no with it, whichever button was pressed
        if (frame.slots(MechaModule.Slot.ARM) < 2) side = 0;
        entityData.set(DRY, now * 2 + (side & 1));
        level().playSound(null, getX(), getY() + 1.5, getZ(), com.coolfreeze23.headinajar.mecha.HopperSounds.get(frame, "dry_fire"),
                net.minecraft.sounds.SoundSource.NEUTRAL, 1, com.coolfreeze23.headinajar.mecha.HopperSounds.pitch("dry_fire", 1, random));
    }

    @Override
    protected void addPassenger(Entity passenger) {
        boolean first = getPassengers().isEmpty();
        super.addPassenger(passenger);
        if (!level().isClientSide && first && passenger instanceof Player && frame.animated()) {
            // boarding skips whatever is left of the build, straight to the boot
            endBuild();
            entityData.set(BOARDED, level().getGameTime());
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        boolean pilot = passenger == getFirstPassenger();
        super.removePassenger(passenger);
        if (!level().isClientSide && pilot && passenger instanceof Player && frame.animated()) {
            entityData.set(BOARDED, -level().getGameTime());
            setPogoAt(-1);
            pogoCharge = -1;
            // whatever the fists held falls when the pilot goes, however they go; the Dragline lets go
            com.coolfreeze23.headinajar.mecha.Bulwark.release(this, null);
            // a Siege ends with its pilot: the machine stands up out of it by its own clip, with nobody aboard
            com.coolfreeze23.headinajar.mecha.Siege.release(this);
            com.coolfreeze23.headinajar.mecha.Skitter.letGo(this);
            // whatever the fists or talons held falls when the pilot goes, however they go
            com.coolfreeze23.headinajar.mecha.Kite.release(this, null);
            com.coolfreeze23.headinajar.mecha.Bulwark.release(this, null);
            setTakeoffAt(-1);
            setClimbing(false);
        }
    }

    @Override
    public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
        if (frame.animated()) com.coolfreeze23.headinajar.mecha.HopperAnimation.register(this, controllers);
    }

    @Override
    public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    /** An animated frame is drawn a little taller than its hitbox (the hoop, the ejected jar) and wider (arms, tools). */
    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        if (!frame.animated()) return super.getBoundingBoxForCulling();
        var rig = com.coolfreeze23.headinajar.mecha.MechaRig.of(frame);
        return getBoundingBox().inflate(rig.cullSide, 0, rig.cullSide).expandTowards(0, rig.cullUp, 0);
    }

    /** An animated frame topples over 40 ticks (its clip) before the existing destruction happens. */
    @Override
    protected void tickDeath() {
        if (!frame.animated()) {
            super.tickDeath();
            return;
        }
        deathTime++;
        // wrecked in Siege, its collapse follows a short stand-up
        int rise = com.coolfreeze23.headinajar.mecha.Siege.riseBeforeCollapse(this);
        if (!level().isClientSide && deathTime >= rise) com.coolfreeze23.headinajar.mecha.Hopper.dying(this, deathTime - rise);
        if (deathTime >= com.coolfreeze23.headinajar.mecha.Hopper.COLLAPSE_TICKS + rise && !level().isClientSide && !isRemoved()) {
            MechaLogic.destroyed(this);
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        return frame.animated() ? com.coolfreeze23.headinajar.mecha.HopperSounds.get(frame, "hurt") : super.getHurtSound(source);
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return frame.animated() ? null : super.getDeathSound();
    }

    /** An animated frame's feet sound from its walk clip, at the plant. */
    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (!frame.animated()) super.playStepSound(pos, state);
    }

    public MechaFrame frame() {
        return frame;
    }

    // --- modules -----------------------------------------------------------------------------------

    /** Changes (by identity) whenever the fitted modules do: a key for caches of what is drawn where. */
    public Object modulesKey() {
        return entityData.get(MODULES);
    }

    public NonNullList<ItemStack> modules() {
        return modules;
    }

    public void setModules(java.util.List<ItemStack> list) {
        for (int i = 0; i < modules.size(); i++) modules.set(i, i < list.size() ? list.get(i).copy() : ItemStack.EMPTY);
        syncModules();
    }

    /** Sends the fitted modules (type and slider per slot) to clients, which need them for movement and drawing. */
    public void syncModules() {
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < modules.size(); i++) {
            if (modules.get(i).getItem() instanceof MechaModuleItem m) {
                tag.putString("m" + i, m.type().name());
                tag.putInt("s" + i, MechaModuleItem.slider(modules.get(i)));
            }
        }
        entityData.set(MODULES, tag);
    }

    /** The synced module tag as last decoded, and what it says (worked out once per change, both sides). */
    private CompoundTag decodedTag;
    private MechaModule[] decoded = new MechaModule[0];
    private int[] decodedSliders = new int[0];
    private final java.util.EnumSet<MechaModule> fitted = java.util.EnumSet.noneOf(MechaModule.class);
    private int modulesMass;

    private void decode() {
        CompoundTag tag = entityData.get(MODULES);
        if (tag == decodedTag) return;
        int n = frame.totalSlots();
        MechaModule[] mods = new MechaModule[n];
        int[] sliders = new int[n];
        fitted.clear();
        int mass = 0;
        for (int i = 0; i < n; i++) {
            String name = tag.getString("m" + i);
            if (!name.isEmpty()) {
                try {
                    mods[i] = MechaModule.valueOf(name);
                    fitted.add(mods[i]);
                    mass += mods[i].mass;
                } catch (IllegalArgumentException ignored) {
                }
            }
            sliders[i] = tag.contains("s" + i) ? tag.getInt("s" + i) : 50;
        }
        decoded = mods;
        decodedSliders = sliders;
        modulesMass = mass;
        decodedTag = tag;
    }

    /** The module type in a flat slot (client and server). */
    public MechaModule moduleAt(int index) {
        decode();
        return index >= 0 && index < decoded.length ? decoded[index] : null;
    }

    public int sliderAt(int index) {
        decode();
        return index >= 0 && index < decodedSliders.length ? decodedSliders[index] : 50;
    }

    public boolean has(MechaModule module) {
        decode();
        return fitted.contains(module);
    }

    /** Index of the first slot holding this module, or -1. */
    public int indexOf(MechaModule module) {
        decode();
        if (!fitted.contains(module)) return -1;
        for (int i = 0; i < decoded.length; i++) if (decoded[i] == module) return i;
        return -1;
    }

    /** Gene Ports: the frame's own plus one for a Bio-Integration Port. */
    public int genePorts() {
        return frame.genePorts() + (has(MechaModule.BIO_INTEGRATION_PORT) ? 1 : 0);
    }

    /** The module on arm slot {@code side}: 0 left, 1 right, 2 and 3 the shoulder hardpoints (left, right). */
    public MechaModule arm(int side) {
        int first = frame.firstIndex(MechaModule.Slot.ARM);
        return side < frame.slots(MechaModule.Slot.ARM) ? moduleAt(first + side) : null;
    }

    public int armSlider(int side) {
        return sliderAt(frame.firstIndex(MechaModule.Slot.ARM) + side);
    }

    public SimpleContainer cargo() {
        return cargo;
    }

    /**
     * Opens the Cargo Hold: for the owner (or someone they trust) at a parked mecha, or the pilot from the cockpit. The
     * screen closes when the mecha is stored, leaves or is destroyed.
     */
    public boolean openCargo(net.minecraft.server.level.ServerPlayer player) {
        var gantry = net.minecraft.network.chat.Component.translatable("block.headinajar.assembly_gantry");
        var hold = net.minecraft.network.chat.Component.translatable(com.coolfreeze23.headinajar.registry.ModRegistry.mechaModule(MechaModule.CARGO_HOLD).getDescriptionId());
        var source = com.coolfreeze23.headinajar.feedback.Refuser.at(this);
        if (!isOwnedBy(player)) return com.coolfreeze23.headinajar.feedback.Refusals.deny(player, source, "mecha.not_yours");
        if (!has(MechaModule.CARGO_HOLD)) return com.coolfreeze23.headinajar.feedback.Refusals.deny(player, source, "mecha.no_cargo", hold, gantry);
        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, p) -> net.minecraft.world.inventory.ChestMenu.threeRows(id, inv, new EntityCargo(cargo, this)), hold));
        act(MechaModule.CARGO_HOLD);
        playSound(com.coolfreeze23.headinajar.fx.HouseSounds.CARGO_OPEN.get(), 0.8f, 0.8f);
        return true;
    }

    // --- power, heat, weight ------------------------------------------------------------------------

    public int battery() {
        return entityData.get(BATTERY);
    }

    public int batteryCapacity() {
        int cap = frame.battery + (has(MechaModule.BATTERY_RACK) ? 125_000 : 0) + (has(MechaModule.CAPACITOR_BANK) ? 500_000 : 0);
        return has(MechaModule.BIO_INTEGRATION_PORT) ? cap * 4 / 5 : cap;
    }

    public void setBattery(int fe) {
        entityData.set(BATTERY, Math.max(0, Math.min(batteryCapacity(), fe)));
    }

    /** Spends FE if there is any (returns whether the action is powered). */
    public boolean spend(int fe) {
        int now = battery();
        if (now <= 0) return false;
        setBattery(now - fe);
        return true;
    }

    public float heat() {
        return entityData.get(HEAT);
    }

    public void setHeat(float heat) {
        entityData.set(HEAT, Math.max(0, heat));
    }

    public int vent() {
        return entityData.get(VENT);
    }

    public void setVent(int ticks) {
        entityData.set(VENT, ticks);
    }

    /** Which fitted utility module R fires (an index into the fitted ones). */
    public int utility() {
        return entityData.get(UTILITY);
    }

    public void setUtility(int index) {
        entityData.set(UTILITY, index);
    }

    /** Arm buttons as the client last drew them (for animation). */
    public int armsShown() {
        return entityData.get(ARMS);
    }

    public int mass() {
        decode();
        int mass = frame.dryMass + modulesMass;
        for (int i = 0; i < cargo.getContainerSize(); i++) if (!cargo.getItem(i).isEmpty()) mass += 1;
        return mass;
    }

    public float load() {
        return mass() / (float) frame.load;
    }

    public UUID owner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    // --- life ------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        // the Chimera's grafted gene works on both sides (movement genes run on the pilot's client too)
        com.coolfreeze23.headinajar.mecha.Chimera.genes(this);
        if (!level().isClientSide) {
            MechaLogic.serverTick(this);
            if (frame.animated() && isAlive()) com.coolfreeze23.headinajar.mecha.Hopper.serverTick(this);
            // Heavy Lifting: the hold is kept (or let go) every tick, dying or not
            if (frame.animated()) com.coolfreeze23.headinajar.mecha.Bulwark.serverTick(this);
            if (frame.animated()) com.coolfreeze23.headinajar.mecha.Siege.serverTick(this);
            if (frame.animated()) com.coolfreeze23.headinajar.mecha.Executive.serverTick(this);
            if (frame.animated()) com.coolfreeze23.headinajar.mecha.Wheelhouse.serverTick(this);
            if (frame.animated()) com.coolfreeze23.headinajar.mecha.Chimera.serverTick(this);
            if (frame.animated()) com.coolfreeze23.headinajar.mecha.Skitter.serverTick(this);
            // the Kite's takeoff, climb and Talon Lift
            if (frame.animated()) com.coolfreeze23.headinajar.mecha.Kite.serverTick(this);
            entityData.set(ARMS, (byte) armsHeld);
        }
    }

    /**
     * Moves as every mob does, then the Dragline holds it: where the machine is moved (its pilot's client), a taut line
     * keeps the hang point within the line's length of the anchor and takes away the speed outward along it.
     */
    @Override
    public void travel(Vec3 input) {
        super.travel(input);
        com.coolfreeze23.headinajar.mecha.Skitter.hold(this);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        float dealt = com.coolfreeze23.headinajar.mecha.Siege.incoming(this, source, MechaLogic.incomingDamage(this, source, amount));
        boolean hurt = dealt > 0 && super.hurt(source, dealt);
        if (hurt && !level().isClientSide && frame.animated()) {
            // which side the blow came from: the flinch is authored for the left and mirrored for the right
            Vec3 from = source.getSourcePosition();
            boolean right = from != null && from.subtract(position()).dot(Vec3.directionFromRotation(0, yBodyRot + 90)) > 0;
            entityData.set(HIT, level().getGameTime() * 2 + (right ? 1 : 0));
        }
        if (hurt && !level().isClientSide) MechaLogic.afterDamage(this);
        return hurt;
    }

    /** Anchored in Siege, or standing on Anchor Clamps, the machine takes no knock-back. */
    @Override
    public void knockback(double strength, double x, double z) {
        if (com.coolfreeze23.headinajar.mecha.Siege.locked(this) || has(MechaModule.ANCHOR_CLAMPS) && onGround()) return;
        super.knockback(strength, x, z);
    }

    /** Server: whoever was aboard when the machine was wrecked; its blast spares them. Never saved. */
    public final java.util.Set<Entity> spared = new java.util.HashSet<>();

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) {
            // wrecked in Siege it stands up out of it (briefly) before it collapses
            com.coolfreeze23.headinajar.mecha.Siege.release(this);
            // an animated frame collapses first (tickDeath) and is destroyed when it lands; its pilot is thrown clear now
            if (!frame.animated()) MechaLogic.destroyed(this);
            else MechaLogic.throwClear(this);
        }
        super.die(source);
    }

    /** Removed, unloaded or sent elsewhere: whatever the fists held falls where it is. */
    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) {
            com.coolfreeze23.headinajar.mecha.Kite.release(this, null);
            com.coolfreeze23.headinajar.mecha.Bulwark.release(this, null);
        }
        super.remove(reason);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < 1 + frame.seats() + (has(MechaModule.PASSENGER_POD) ? 1 : 0);
    }

    /** The jar gets out clear of the frame's box: inside it, a click on the jar or on its head would reach the mecha. */
    @Override
    public Vec3 getDismountLocationForPassenger(net.minecraft.world.entity.LivingEntity passenger) {
        Vec3 clear = ShellEntity.clearOf(this, passenger);
        return clear != null ? clear : super.getDismountLocationForPassenger(passenger);
    }

    /** Passengers (Passenger Pod, the Wheelhouse's seats) sit behind the cockpit, side by side. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        int index = Math.max(1, getPassengers().indexOf(passenger));
        var rig = com.coolfreeze23.headinajar.mecha.MechaRig.of(frame);
        if (rig != null && index - 1 < rig.seats.length) {
            // the frame's own seats (the Wheelhouse's bench): model left (-x) is the entity's +x, the model's front (-z) its +z
            double[] seat = rig.seats[index - 1];
            return new Vec3(-seat[0] / 16, seat[1] / 16, -seat[2] / 16);
        }
        double side = getPassengers().size() > 2 ? (index % 2 == 1 ? -0.5 : 0.5) : 0;
        return new Vec3(side, frame.height * 0.55, -frame.width * 0.6);
    }

    /** Colossus-sized frames don't fit through a nether portal. */
    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return frame.height < 4 && super.canUsePortal(allowPassengers);
    }

    /** A frame has no lungs (and Aqua Fins invite it into the water). */
    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return has(MechaModule.FIRE_SUPPRESSION) || super.fireImmune();
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        if (has(MechaModule.ANCHOR_CLAMPS) || has(MechaModule.HOVER_SKIRT) || frame.locomotion == MechaFrame.Locomotion.WINGS) return false;
        // the Pogo Protocol lands on its springs
        if (pogoFlight) return false;
        return super.causeFallDamage(distance - 3, multiplier, source);
    }

    /** The Skitter (and Arachnid Legs) cling to walls. */
    @Override
    public boolean onClimbable() {
        return horizontalCollision && (frame.locomotion == MechaFrame.Locomotion.SPIDER || has(MechaModule.ARACHNID_LEGS)) || super.onClimbable();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ListTag list = new ListTag();
        for (ItemStack stack : modules) list.add(stack.saveOptional(registryAccess()));
        tag.put("Modules", list);
        tag.put("Cargo", cargo.createTag(registryAccess()));
        tag.putInt("Battery", battery());
        tag.putFloat("Heat", heat());
        if (owner != null) tag.putUUID("Owner", owner);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ListTag list = tag.getList("Modules", Tag.TAG_COMPOUND);
        for (int i = 0; i < modules.size() && i < list.size(); i++) modules.set(i, ItemStack.parseOptional(registryAccess(), list.getCompound(i)));
        syncModules();
        cargo.fromTag(tag.getList("Cargo", Tag.TAG_COMPOUND), registryAccess());
        setBattery(tag.getInt("Battery"));
        setHeat(tag.getFloat("Heat"));
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        HostData data = Hosts.data(this);
        data.host = true;
        data.shell = true;
    }

    public boolean isOwnedBy(Player player) {
        return owner == null || owner.equals(player.getUUID())
                || player.getServer() != null && com.coolfreeze23.headinajar.form.HeadRegistry.get(player.getServer()).trusts(owner, player);
    }

    public ServerLevel serverLevel() {
        return (ServerLevel) level();
    }
}

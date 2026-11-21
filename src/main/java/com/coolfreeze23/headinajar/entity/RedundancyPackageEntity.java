package com.coolfreeze23.headinajar.entity;

import com.coolfreeze23.headinajar.Advance;
import com.coolfreeze23.headinajar.mecha.HopperNet;
import com.coolfreeze23.headinajar.mecha.HopperSounds;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.registry.SecondRegistry;
import com.coolfreeze23.headinajar.staff.second.RedundancyPackage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * The Redundancy Package: a seconded head on its way down under its little gold canopy after its body died. Fired up
 * and back off the collar (or toward the nearest bank, out of lava), its canopy opens on the deploy's tick 20 (sooner,
 * played faster, for a short drop) unless there was no room overhead (then it is tangled and drops straight down), it
 * sinks at 0.08 a tick drifting with the launch, and it lands as a placed Jarred Head, file and grade kept (placed as
 * any block is, so claim and protection mods can say no). In water, or where the head may not be placed, the Jarred
 * Head item is left where it came down; in lava, or out of the world, the head is lost. Nothing hurts it, nothing burns
 * it, nobody picks it up, and it is saved with its chunk (a package in flight lands after a reload). Once down, the
 * folded canopy stays drawn where it came down for the fold's two seconds (three in water), then it is gone.
 */
public class RedundancyPackageEntity extends Entity {
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(RedundancyPackageEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> FIRED_AT = SynchedEntityData.defineId(RedundancyPackageEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> RATE = SynchedEntityData.defineId(RedundancyPackageEntity.class, EntityDataSerializers.FLOAT);
    /** What clients draw: the head's creature, its villager data and size, and its jar (never the stored mob or file). */
    private static final EntityDataAccessor<ItemStack> SHOWN = SynchedEntityData.defineId(RedundancyPackageEntity.class, EntityDataSerializers.ITEM_STACK);

    /** Sink rate under the canopy (blocks a tick), gravity rising and falling before it opens, and the drift's drag. */
    public static final double SINK = 0.08, GRAVITY = 0.08, SLOW = 0.01, DRIFT = 0.91;
    /** Ticks the folded canopy is drawn where it came down, on land and in water (the parachute's fold clips). */
    static final int LAND = 40, WATER = 60;
    /** The longest a package flies before it comes down wherever it is (ticks). */
    static final int MAX_FLIGHT = 1200;

    /** The head it carries (server only): the whole Jarred Head item with its file. */
    protected ItemStack head = ItemStack.EMPTY;
    /** Who employs the head (told when it tangles; given the advancement when it lands). */
    private UUID employer;
    /** Ticks since it came down (the fold). */
    private int folded;
    /** Client only: the canopy state this client's canopy was last told ({@code Parachute.follow}). */
    public int toldState = -1;

    public RedundancyPackageEntity(EntityType<? extends RedundancyPackageEntity> type, Level level) {
        super(type, level);
    }

    /**
     * Fires a package from {@code at} with velocity {@code v}, carrying {@code head}: tangled (straight down, from rest)
     * when there are not three blocks of air over it, else fired with its deploy timed to the drop.
     */
    public static RedundancyPackageEntity launch(ServerLevel level, Vec3 at, Vec3 v, float yaw, ItemStack head, UUID employer) {
        RedundancyPackageEntity p = new RedundancyPackageEntity(SecondRegistry.REDUNDANCY_PACKAGE.get(), level);
        p.moveTo(at.x, at.y, at.z, yaw, 0);
        p.setHead(head);
        p.employer = employer;
        p.entityData.set(FIRED_AT, level.getGameTime());
        if (p.room()) {
            p.setDeltaMovement(v);
            p.entityData.set(STATE, (byte) HopperNet.FIRED);
            p.entityData.set(RATE, p.deployRate(v));
        } else {
            // no room for the canopy: it tangles, and the package drops where it is
            p.setDeltaMovement(Vec3.ZERO);
            p.entityData.set(STATE, (byte) HopperNet.TANGLED);
            p.entityData.set(RATE, HopperNet.FASTEST);
            ServerPlayer boss = p.employer();
            if (boss != null) boss.displayClientMessage(Component.translatable("message.headinajar.secondment.tangled"), true);
        }
        level.addFreshEntity(p);
        // the collar lets go: its clasp, a little higher than when it locks
        level.playSound(null, at.x, at.y, at.z, SecondRegistry.COLLAR_FITTED.get(), SoundSource.NEUTRAL, 1, RedundancyPackage.PITCH);
        return p;
    }

    public ItemStack head() {
        return head;
    }

    public void setHead(ItemStack head) {
        this.head = head.copy();
        entityData.set(SHOWN, shown(this.head));
    }

    public UUID employerId() {
        return employer;
    }

    /** Its canopy's state: {@link HopperNet#FIRED}, {@code OPEN}, {@code TANGLED}, or once down {@code FOLDED} or {@code SUNK}. */
    public int state() {
        return entityData.get(STATE);
    }

    /** The game tick it was fired. */
    public long firedAt() {
        return entityData.get(FIRED_AT);
    }

    /** The deploy's rate (1 as drawn, up to twice for a short drop). */
    public float rate() {
        return entityData.get(RATE);
    }

    /** The head as clients see it. */
    public ItemStack shownHead() {
        return entityData.get(SHOWN);
    }

    /** It has come down (the head is placed, left, or lost); only the folded canopy is left. */
    public boolean down() {
        int s = state();
        return s == HopperNet.FOLDED || s == HopperNet.SUNK;
    }

    /** The ticks since it was fired. */
    public long age() {
        return level().getGameTime() - firedAt();
    }

    private static ItemStack shown(ItemStack head) {
        ItemStack out = new ItemStack(ModRegistry.JARRED_HEAD.get());
        CustomData data = head.get(DataComponents.CUSTOM_DATA);
        if (data == null) return out;
        CompoundTag tag = data.getUnsafe(), kept = new CompoundTag(), entity = new CompoundTag();
        kept.putString("Type", tag.getString("Type"));
        if (tag.contains("Jar")) kept.putString("Jar", tag.getString("Jar"));
        CompoundTag stored = tag.getCompound("Entity");
        if (stored.contains("VillagerData")) entity.put("VillagerData", stored.getCompound("VillagerData").copy());
        if (stored.contains("Size")) entity.putInt("Size", stored.getInt("Size"));
        kept.put("Entity", entity);
        CustomData.set(DataComponents.CUSTOM_DATA, out, kept);
        return out;
    }

    private ServerPlayer employer() {
        return employer == null || !(level() instanceof ServerLevel server) ? null : server.getServer().getPlayerList().getPlayer(employer);
    }

    // --- flight -------------------------------------------------------------------------------------------------------

    /** Three blocks of air over it: room for the canopy to open (the Golden Parachute's rule). */
    boolean room() {
        AABB box = getBoundingBox();
        return level().noBlockCollision(this, new AABB(box.minX, box.maxY, box.minZ, box.maxX, box.maxY + HopperNet.ROOM, box.maxZ));
    }

    /** One tick of its flight from velocity {@code v} in canopy state {@code state}. */
    public static Vec3 step(Vec3 v, int state) {
        if (state == HopperNet.TANGLED) return new Vec3(0, (v.y - GRAVITY) * 0.98, 0);
        double vy = v.y > 0 ? (v.y - GRAVITY) * 0.98 : Math.max(v.y - SLOW, -SINK);
        return new Vec3(v.x * DRIFT, vy, v.z * DRIFT);
    }

    /**
     * How fast the deploy plays for this throw (the Golden Parachute's rule): as drawn when it has a second and a half
     * of air, faster (up to twice) so the canopy is open half a second before it touches down; twice for anything
     * shorter (it lands under a half-open canopy).
     */
    float deployRate(Vec3 v) {
        AABB box = getBoundingBox();
        int touch = 600;
        for (int t = 1; t < 600; t++) {
            v = step(v, HopperNet.FIRED);
            AABB moved = box.move(v);
            if (!level().noBlockCollision(this, moved)) {
                if (v.y > 0) {
                    // a roof: the climb stops there
                    v = new Vec3(v.x, 0, v.z);
                    moved = box;
                } else {
                    touch = t;
                    break;
                }
            }
            box = moved;
            if (level().containsAnyLiquid(box)) {
                touch = t;
                break;
            }
        }
        int room = touch - HopperNet.OPEN_BEFORE;
        if (room >= HopperNet.SNAP) return 1;
        if (room * HopperNet.FASTEST < HopperNet.SNAP) return HopperNet.FASTEST;
        return HopperNet.SNAP / (float) room;
    }

    @Override
    public void tick() {
        super.tick();
        clearFire();
        if (level().isClientSide) return;
        if (down()) {
            setDeltaMovement(Vec3.ZERO);
            if (++folded >= (state() == HopperNet.SUNK ? WATER : LAND)) discard();
            return;
        }
        if (head.isEmpty()) {
            discard();
            return;
        }
        long age = age();
        if (state() == HopperNet.FIRED && age * rate() >= HopperNet.SNAP) {
            entityData.set(STATE, (byte) HopperNet.OPEN);
            sound("parachute_pop");
        }
        Vec3 v = step(getDeltaMovement(), state());
        move(MoverType.SELF, v);
        if (horizontalCollision) v = new Vec3(0, v.y, 0);
        if (verticalCollision && v.y > 0) v = new Vec3(v.x, 0, v.z);
        setDeltaMovement(v);
        BlockPos at = blockPosition();
        // (a package fired out of lava is in it while it climbs)
        boolean sinking = v.y <= 0 && age > 3;
        if (sinking && (isInLava() || level().getFluidState(at).is(FluidTags.LAVA))) {
            // the jar breaks in lava
            RedundancyPackage.lose((ServerLevel) level(), position(), head, RedundancyPackage.LAVA, null);
            head = ItemStack.EMPTY;
            discard();
        } else if (isInWater() || level().getFluidState(at).is(FluidTags.WATER)) {
            land(true);
        } else if (onGround() || age > MAX_FLIGHT) {
            land(false);
        }
    }

    /** Down: placed as a Jarred Head where it may be, else the item left where it is; the fold starts. */
    private void land(boolean water) {
        ServerLevel level = (ServerLevel) level();
        if (water || !RedundancyPackage.place(level, this, head)) {
            ItemEntity item = new ItemEntity(level, getX(), getY() + 0.2, getZ(), head.copy());
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }
        head = ItemStack.EMPTY;
        RedundancyPackage.landed(this, water);
        ServerPlayer boss = employer();
        if (boss != null) Advance.grant(boss, "redundancy");
        entityData.set(STATE, (byte) (water ? HopperNet.SUNK : HopperNet.FOLDED));
        setDeltaMovement(Vec3.ZERO);
        sound("parachute_fold");
    }

    private void sound(String name) {
        var sound = HopperSounds.get(name);
        if (sound != null) level().playSound(null, getX(), getY() + 1, getZ(), sound, SoundSource.NEUTRAL, 1,
                HopperSounds.pitch(name, RedundancyPackage.PITCH, getRandom()));
    }

    /** Out of the world: the head is lost with it. */
    @Override
    protected void onBelowWorld() {
        if (!level().isClientSide && !head.isEmpty()) {
            RedundancyPackage.lose((ServerLevel) level(), position(), head, RedundancyPackage.VOID, null);
            head = ItemStack.EMPTY;
        }
        super.onBelowWorld();
    }

    @Override
    public void onClientRemoval() {
        com.coolfreeze23.headinajar.client.Parachute.forget(getId());
    }

    // --- nothing touches it -------------------------------------------------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean ignoreExplosion(net.minecraft.world.level.Explosion explosion) {
        return true;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    // --- saved with its chunk -----------------------------------------------------------------------------------------

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STATE, (byte) HopperNet.FIRED);
        builder.define(FIRED_AT, 0L);
        builder.define(RATE, 1f);
        builder.define(SHOWN, ItemStack.EMPTY);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Head")) setHead(ItemStack.parseOptional(registryAccess(), tag.getCompound("Head")));
        // a package summoned with only its head is fired now
        entityData.set(STATE, tag.contains("State") ? tag.getByte("State") : (byte) HopperNet.FIRED);
        entityData.set(FIRED_AT, tag.contains("FiredAt") ? tag.getLong("FiredAt") : level().getGameTime());
        entityData.set(RATE, tag.contains("Rate") ? tag.getFloat("Rate") : 1f);
        employer = tag.hasUUID("Employer") ? tag.getUUID("Employer") : null;
        folded = tag.getInt("Folded");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (!head.isEmpty()) tag.put("Head", head.save(registryAccess()));
        tag.putByte("State", (byte) state());
        tag.putLong("FiredAt", firedAt());
        tag.putFloat("Rate", rate());
        if (employer != null) tag.putUUID("Employer", employer);
        if (folded > 0) tag.putInt("Folded", folded);
    }
}

package com.coolfreeze23.headinajar.entity;

import com.coolfreeze23.headinajar.host.HostData;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.nbt.CompoundTag;
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
public class ShellEntity extends PathfinderMob {
    public enum Kind { LUNK, ROVER, SPIDER_WALKER }

    private final Kind kind;
    private final SimpleContainer cargo = new SimpleContainer(9);

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
    private int crashCooldown;

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        // a rider moves the shell from their client, so its speed is read between ticks, not inside one
        double speed = lastPos == null ? 0 : position().subtract(lastPos).horizontalDistance();
        if (crashCooldown > 0) crashCooldown--;
        if (horizontalCollision && Math.max(speed, lastSpeed) >= CRASH_SPEED && crashCooldown == 0 && getFirstPassenger() instanceof ServerPlayer pilot) crash(pilot);
        lastSpeed = speed;
        lastPos = position();
    }

    /** Into a wall at speed: one bump to the jar's glass, a clang and a line, then a moment before the next one counts. */
    private void crash(ServerPlayer pilot) {
        crashCooldown = 40;
        var state = com.coolfreeze23.headinajar.form.FormActions.state(pilot);
        if (state.integrity() > 1) state.setIntegrity(state.integrity() - 1);
        level().playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.IRON_GOLEM_DAMAGE, net.minecraft.sounds.SoundSource.NEUTRAL, 0.8f, 1.4f);
        level().playSound(null, pilot.getX(), pilot.getY(), pilot.getZ(), net.minecraft.sounds.SoundEvents.GLASS_HIT, net.minecraft.sounds.SoundSource.PLAYERS, 1, 0.9f);
        if (level() instanceof net.minecraft.server.level.ServerLevel server) {
            Vec3 front = position().add(getLookAngle().multiply(1, 0, 1).scale(getBbWidth() * 0.6)).add(0, getBbHeight() * 0.5, 0);
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, front.x, front.y, front.z, 8, 0.2, 0.2, 0.2, 0.1);
        }
        pilot.displayClientMessage(Component.translatable("message.headinajar.shell_crash", getDisplayName()), true);
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

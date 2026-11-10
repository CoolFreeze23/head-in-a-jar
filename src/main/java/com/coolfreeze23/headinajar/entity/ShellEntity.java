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

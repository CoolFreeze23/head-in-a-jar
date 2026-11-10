package com.coolfreeze23.headinajar.entity;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.HeadRegistry;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/**
 * A player's head in its jar, set down in the world while the player walks around in their body.
 * Breaking it while they are linked kills them for real.
 */
public class HeadJarEntity extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(HeadJarEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> OWNER_NAME = SynchedEntityData.defineId(HeadJarEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<ItemStack> GOGGLES = SynchedEntityData.defineId(HeadJarEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> COMPACT = SynchedEntityData.defineId(HeadJarEntity.class, EntityDataSerializers.BOOLEAN);
    public static final float MAX_HEALTH = 10;
    private float health = MAX_HEALTH;
    private int hurtTicks;

    public HeadJarEntity(EntityType<? extends HeadJarEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, Optional.empty());
        builder.define(OWNER_NAME, "");
        builder.define(GOGGLES, ItemStack.EMPTY);
        builder.define(COMPACT, false);
    }

    public void setOwner(UUID id, String name) {
        entityData.set(OWNER, Optional.of(id));
        entityData.set(OWNER_NAME, name);
    }

    public UUID ownerId() {
        return entityData.get(OWNER).orElse(null);
    }

    public String ownerName() {
        return entityData.get(OWNER_NAME);
    }

    /** The head is in a Compact Jar (head-sized). */
    public boolean compact() {
        return entityData.get(COMPACT);
    }

    public void setCompact(boolean compact) {
        entityData.set(COMPACT, compact);
        refreshDimensions();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (COMPACT.equals(key)) refreshDimensions();
    }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        return compact() ? com.coolfreeze23.headinajar.form.FormEvents.COMPACT_SIZE : super.getDimensions(pose);
    }

    public ItemStack goggles() {
        return entityData.get(GOGGLES);
    }

    public void setGoggles(ItemStack goggles) {
        entityData.set(GOGGLES, goggles.copy());
    }

    public int hurtTicks() {
        return hurtTicks;
    }

    @Override
    protected Component getTypeName() {
        String name = ownerName();
        return name.isEmpty() ? super.getTypeName() : Component.translatable("entity.headinajar.head_jar.owned", name);
    }

    private ServerPlayer owner() {
        return ownerId() == null || level().getServer() == null ? null : level().getServer().getPlayerList().getPlayer(ownerId());
    }

    @Override
    public void tick() {
        super.tick();
        if (hurtTicks > 0) hurtTicks--;
        if (!isNoGravity()) setDeltaMovement(getDeltaMovement().add(0, -0.04, 0));
        move(MoverType.SELF, getDeltaMovement());
        setDeltaMovement(getDeltaMovement().multiply(0.6, 0.98, 0.6));
        if (onGround()) setDeltaMovement(getDeltaMovement().multiply(0.5, -0.5, 0.5));
        if (level().isClientSide || ownerId() == null || tickCount % 10 != 0 || level().getServer() == null) return;
        // the registry is the truth: a head that is no longer here (the player swapped into it) disappears
        HeadRegistry registry = HeadRegistry.get(level().getServer());
        HeadRegistry.Head head = registry.head(ownerId());
        if (head == null || head.where() != HeadRegistry.Where.ENTITY || !getUUID().equals(head.entity())) {
            discard();
            return;
        }
        registry.moveHead(ownerId(), getUUID(), HeadRegistry.Spot.of(this));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) return false;
        boolean bypass = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        if (!bypass && (isInvulnerableTo(source) || hurtTicks > 0)) return false;
        if (!bypass && Config.get(Config.PROTECT_OFFLINE, true) && owner() == null) return false;
        hurtTicks = 10;
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_HIT, SoundSource.PLAYERS, 1, 1.2f);
        ServerPlayer owner = owner();
        if (owner != null) {
            // the jar's glass is the owner's Jar Integrity
            var state = owner.getData(ModRegistry.JAR_STATE);
            state.setIntegrity(state.integrity() - (int) Math.ceil(amount));
            if (state.integrity() <= 0) shatter();
        } else {
            health -= amount;
            if (health <= 0) shatter();
        }
        return true;
    }

    /** The jar breaks. If its owner is out walking their body, that is a real death ({@link #remove}). */
    private void shatter() {
        ServerLevel level = (ServerLevel) level();
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2f, 0.8f);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GLASS)), getX(), getY() + 0.4, getZ(), 30, 0.25, 0.25, 0.25, 0.1);
        discard();
    }

    /**
     * However the head leaves the world for good (shattered, killed by command, fallen into the void), its record goes
     * with it, and an owner out walking their body dies for real. The mod's own moves (picking it up, swapping into it)
     * change the record first, so they are not read as a loss.
     */
    @Override
    public void remove(RemovalReason reason) {
        boolean lost = reason.shouldDestroy() && !level().isClientSide && !isRemoved();
        super.remove(reason);
        if (lost) lost();
    }

    private void lost() {
        UUID id = ownerId();
        if (id == null || level().getServer() == null) return;
        HeadRegistry registry = HeadRegistry.get(level().getServer());
        HeadRegistry.Head head = registry.head(id);
        if (head == null || head.where() != HeadRegistry.Where.ENTITY || !getUUID().equals(head.entity())) return;
        registry.setHead(id, null);
        ServerPlayer owner = owner();
        if (owner != null && owner.getData(ModRegistry.JAR_STATE).form() == Form.LINKED) {
            FormActions.REAL_DEATH.add(owner.getUUID());
            owner.hurt(owner.damageSources().genericKill(), Float.MAX_VALUE);
            FormActions.REAL_DEATH.remove(owner.getUUID());
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!player.getUUID().equals(ownerId())) {
            if (level().isClientSide) return InteractionResult.PASS;
            return othersUse((ServerPlayer) player, player.getItemInHand(hand));
        }
        if (level().isClientSide) return InteractionResult.SUCCESS;
        ServerPlayer server = (ServerPlayer) player;
        ItemStack held = player.getItemInHand(hand);
        if (FormActions.fitGoggles(server, held, this, null)) {
            held.shrink(1);
            return InteractionResult.CONSUME;
        }
        boolean saline = held.is(ModRegistry.SALINE_BOTTLE.get()), patch = held.is(ModRegistry.GLASS_PATCH.get());
        if (saline || patch) {
            if (com.coolfreeze23.headinajar.form.LifeSupport.refill(server, player.getData(ModRegistry.JAR_STATE), held.getItem())) {
                held.consume(1, player);
                return InteractionResult.CONSUME;
            }
            // a full jar keeps the bottle (and stays where it is: picking it up is an empty hand's job)
            com.coolfreeze23.headinajar.feedback.Refusals.deny(server, saline ? "head.full" : "head.intact");
            return InteractionResult.FAIL;
        }
        if (FormActions.pickUpHead(server, this)) return InteractionResult.CONSUME;
        if (player.getData(ModRegistry.JAR_STATE).form() == Form.LINKED && !FormActions.hasSatchel(player)) {
            player.displayClientMessage(Component.translatable("message.headinajar.need_satchel"), true);
        }
        return InteractionResult.FAIL;
    }

    /**
     * Someone else's head. A player its owner trusts may top it up with saline or patch its glass (the owner must be
     * online: the jar's levels live with them); anything else is politely refused.
     */
    private InteractionResult othersUse(ServerPlayer player, ItemStack held) {
        ServerPlayer owner = owner();
        boolean saline = held.is(ModRegistry.SALINE_BOTTLE.get()), patch = held.is(ModRegistry.GLASS_PATCH.get());
        if ((saline || patch) && owner != null && HeadRegistry.get(owner.getServer()).trusts(ownerId(), player)) {
            var state = owner.getData(ModRegistry.JAR_STATE);
            if (saline && state.saline() < state.maxSaline()) {
                state.setSaline(state.saline() + 250);
                held.consume(1, player);
                FormActions.give(player, new ItemStack(Items.GLASS_BOTTLE));
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 1, 1);
                return InteractionResult.CONSUME;
            }
            if (patch && state.integrity() < state.maxIntegrity()) {
                state.setIntegrity(state.integrity() + 5);
                held.consume(1, player);
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 1, 1.4f);
                return InteractionResult.CONSUME;
            }
            com.coolfreeze23.headinajar.feedback.Refusals.deny(player, saline ? "head.full" : "head.intact");
            return InteractionResult.FAIL;
        }
        com.coolfreeze23.headinajar.feedback.Refusals.deny(player, com.coolfreeze23.headinajar.feedback.Refuser.at(this), "head.not_yours", ownerName());
        return InteractionResult.FAIL;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) setOwner(tag.getUUID("Owner"), tag.getString("OwnerName"));
        setGoggles(ItemStack.parseOptional(registryAccess(), tag.getCompound("Goggles")));
        health = tag.contains("Health") ? tag.getFloat("Health") : MAX_HEALTH;
        setCompact(tag.getBoolean("Compact"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId() != null) tag.putUUID("Owner", ownerId());
        tag.putString("OwnerName", ownerName());
        tag.put("Goggles", goggles().saveOptional(registryAccess()));
        tag.putFloat("Health", health);
        tag.putBoolean("Compact", compact());
    }
}

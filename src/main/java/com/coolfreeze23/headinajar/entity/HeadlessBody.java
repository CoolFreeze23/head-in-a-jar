package com.coolfreeze23.headinajar.entity;

import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/** A player's body standing where they took their head off. It wears their armour and waits for its head. */
public class HeadlessBody extends LivingEntity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(HeadlessBody.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> OWNER_NAME = SynchedEntityData.defineId(HeadlessBody.class, EntityDataSerializers.STRING);

    private final NonNullList<ItemStack> armor = NonNullList.withSize(4, ItemStack.EMPTY);
    private final NonNullList<ItemStack> hands = NonNullList.withSize(2, ItemStack.EMPTY);

    public HeadlessBody(EntityType<? extends HeadlessBody> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 20.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, Optional.empty());
        builder.define(OWNER_NAME, "");
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

    @Override
    protected Component getTypeName() {
        String name = ownerName();
        return name.isEmpty() ? super.getTypeName() : Component.translatable("entity.headinajar.headless_body.owned", name);
    }

    @Override
    public void tick() {
        super.tick();
        // a body whose owner has moved on (died, or took another body) gives out
        if (!level().isClientSide && tickCount % 40 == 0 && ownerId() != null && level().getServer() != null) {
            ServerPlayer owner = level().getServer().getPlayerList().getPlayer(ownerId());
            if (owner != null) {
                var state = owner.getData(ModRegistry.JAR_STATE);
                if (!getUUID().equals(state.body())) collapse();
            }
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!player.getUUID().equals(ownerId()) || player.getData(ModRegistry.JAR_STATE).form() != Form.JAR) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server) FormActions.reattach(server, this);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** The body falls down for good, dropping what it wears. */
    public void collapse() {
        if (isAlive()) kill();
    }

    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        for (int i = 0; i < armor.size(); i++) {
            if (!armor.get(i).isEmpty()) {
                spawnAtLocation(armor.get(i));
                armor.set(i, ItemStack.EMPTY);
            }
        }
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return armor;
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return switch (slot.getType()) {
            case HAND -> hands.get(slot.getIndex());
            case HUMANOID_ARMOR -> armor.get(slot.getIndex());
            default -> ItemStack.EMPTY;
        };
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        verifyEquippedItem(stack);
        switch (slot.getType()) {
            case HAND -> onEquipItem(slot, hands.set(slot.getIndex(), stack), stack);
            case HUMANOID_ARMOR -> onEquipItem(slot, armor.set(slot.getIndex(), stack), stack);
            default -> {
            }
        }
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    /** A body stays where it was left (the jar spawns inside it); Telekinesis can still carry it. */
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId() != null) tag.putUUID("Owner", ownerId());
        tag.putString("OwnerName", ownerName());
        ListTag list = new ListTag();
        for (ItemStack stack : armor) list.add(stack.saveOptional(registryAccess()));
        tag.put("ArmorItems", list);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) setOwner(tag.getUUID("Owner"), tag.getString("OwnerName"));
        ListTag list = tag.getList("ArmorItems", Tag.TAG_COMPOUND);
        for (int i = 0; i < armor.size() && i < list.size(); i++) {
            armor.set(i, ItemStack.parseOptional(registryAccess(), list.getCompound(i)));
        }
    }
}

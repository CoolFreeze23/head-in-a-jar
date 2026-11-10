package com.coolfreeze23.headinajar.entity;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Refuser;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.HeadRegistry;
import com.coolfreeze23.headinajar.form.JarState;
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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * A player's body standing where they left it: wearing their armour, with a Collar Cam on the neck if it came out
 * of the Separation Chair. When it "dies" it becomes a Corpse that holds the player's things until they come back.
 */
public class HeadlessBody extends LivingEntity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(HeadlessBody.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> OWNER_NAME = SynchedEntityData.defineId(HeadlessBody.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> COLLAR = SynchedEntityData.defineId(HeadlessBody.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CORPSE = SynchedEntityData.defineId(HeadlessBody.class, EntityDataSerializers.BOOLEAN);
    public static final int CORPSE_SLOTS = 54;

    private final NonNullList<ItemStack> armor = NonNullList.withSize(4, ItemStack.EMPTY);
    private final NonNullList<ItemStack> hands = NonNullList.withSize(2, ItemStack.EMPTY);
    /** The corpse's things. A view of them closes once the corpse is gone or left behind. */
    private final SimpleContainer belongings = new SimpleContainer(CORPSE_SLOTS) {
        @Override
        public boolean stillValid(Player player) {
            return HeadlessBody.this.isAlive() && player.distanceToSqr(HeadlessBody.this) < 64;
        }
    };

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
        builder.define(COLLAR, false);
        builder.define(CORPSE, false);
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

    public boolean hasCollar() {
        return entityData.get(COLLAR);
    }

    public void setCollar(boolean collar) {
        entityData.set(COLLAR, collar);
    }

    public boolean isCorpse() {
        return entityData.get(CORPSE);
    }

    /** The body has died: it lies down and keeps its things. */
    public void becomeCorpse(Player from) {
        if (from != null) takeInventory(from);
        entityData.set(CORPSE, true);
        setHealth(getMaxHealth());
        clearFire();
    }

    /** Moves the player's main inventory and off hand into this corpse. */
    public void takeInventory(Player player) {
        Inventory inv = player.getInventory();
        int slot = 0;
        for (int i = 0; i < inv.items.size() && slot < CORPSE_SLOTS; i++) {
            if (!inv.items.get(i).isEmpty()) belongings.setItem(slot++, inv.items.get(i).copy());
            inv.items.set(i, ItemStack.EMPTY);
        }
        for (int i = 0; i < inv.offhand.size() && slot < CORPSE_SLOTS; i++) {
            if (!inv.offhand.get(i).isEmpty()) belongings.setItem(slot++, inv.offhand.get(i).copy());
            inv.offhand.set(i, ItemStack.EMPTY);
        }
    }

    /** Hands everything the corpse holds back to its owner. */
    public void giveInventoryTo(ServerPlayer player) {
        for (int i = 0; i < CORPSE_SLOTS; i++) {
            ItemStack stack = belongings.removeItemNoUpdate(i);
            FormActions.give(player, stack);
        }
    }

    public SimpleContainer belongings() {
        return belongings;
    }

    @Override
    protected Component getTypeName() {
        String name = ownerName();
        if (name.isEmpty()) return super.getTypeName();
        return Component.translatable(isCorpse() ? "entity.headinajar.headless_body.corpse" : "entity.headinajar.headless_body.owned", name);
    }

    private ServerPlayer owner() {
        return ownerId() == null || level().getServer() == null ? null : level().getServer().getPlayerList().getPlayer(ownerId());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || ownerId() == null) return;
        ServerPlayer owner = owner();
        if (owner != null && !isCorpse()) {
            JarState state = owner.getData(ModRegistry.JAR_STATE);
            if (getUUID().equals(state.body())) {
                // the owner linked back in: step into them
                if (state.form() == Form.LINKED) {
                    FormActions.absorbBody(owner, this);
                    return;
                }
            } else if (tickCount % 40 == 0) {
                // a body printed while its owner was away (offline, or riding something) is theirs: they take it on
                HeadRegistry.Body record = level().getServer() == null ? null : HeadRegistry.get(level().getServer()).body(ownerId());
                if (record != null && record.entity().equals(getUUID()) && state.form().isJarred()) {
                    state.setBody(getUUID());
                    return;
                }
                // a body whose owner has moved on gives out
                collapse();
                return;
            }
        }
        if (tickCount % 20 == 0 && level().getServer() != null) {
            HeadRegistry.get(level().getServer()).moveBody(ownerId(), getUUID(), HeadRegistry.Spot.of(this));
        }
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (isCorpse()) return true;
        if (!level().isClientSide && Config.get(Config.PROTECT_OFFLINE, true) && ownerId() != null && owner() == null) return true;
        return super.isInvulnerableTo(source);
    }

    @Override
    public void die(DamageSource source) {
        if (!isCorpse() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && ownerId() != null && level().getServer() != null) {
            // an idle body that is killed lies down as a corpse instead of vanishing
            becomeCorpse(null);
            HeadRegistry registry = HeadRegistry.get(level().getServer());
            HeadRegistry.Body record = registry.body(ownerId());
            if (record != null && record.entity().equals(getUUID())) {
                registry.setBody(ownerId(), new HeadRegistry.Body(getUUID(), HeadRegistry.Spot.of(this), true));
            }
            return;
        }
        // killed for good (by command, or by the void): nothing is left to link into
        if (!level().isClientSide) forget();
        super.die(source);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        boolean own = player.getUUID().equals(ownerId());
        Form form = player.getData(ModRegistry.JAR_STATE).form();
        if (level().isClientSide) {
            return own || isCorpse() ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        ServerPlayer server = (ServerPlayer) player;
        ItemStack held = player.getItemInHand(hand);
        boolean kit = held.is(ModRegistry.SUTURE_KIT.get());
        if (ownerId() == null || level().getServer() == null) return InteractionResult.PASS;
        if (!own) {
            if (isCorpse() && HeadRegistry.get(level().getServer()).trusts(ownerId(), player)) {
                openBelongings(player);
                return InteractionResult.CONSUME;
            }
            String why = kit ? "suture.not_yours" : isCorpse() ? "body.corpse_untrusted" : "body.not_yours";
            Refusals.deny(server, Refuser.at(this), why, ownerName());
            return InteractionResult.FAIL;
        }
        if (form == Form.HOSTED || form == Form.PILOT || form == Form.SOCKET) {
            // the head has to be a plain jar again before it can go back on
            return fail(Refusals.deny(server, "body.riding"));
        }
        if (form == Form.JAR) {
            if (kit) {
                if (FormActions.suture(server, this)) {
                    held.shrink(1);
                    return InteractionResult.CONSUME;
                }
                return InteractionResult.FAIL;
            }
            if (isCorpse()) return selfService(server);
            boolean done = hasCollar() ? FormActions.jarToLinked(server) : FormActions.reattach(server, this);
            return done ? InteractionResult.CONSUME : InteractionResult.FAIL;
        }
        if (isCorpse()) {
            openBelongings(player);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    private static InteractionResult fail(boolean ignored) {
        return InteractionResult.FAIL;
    }

    private void openBelongings(Player player) {
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> ChestMenu.sixRows(id, inv, belongings), getDisplayName()));
    }

    /**
     * A jar at its own corpse, hands or no hands: a Suture Kit on the jar or among the corpse's things is used, and the
     * head goes back on. With no kit anywhere, the corpse's things open, so the parts for one can be taken out.
     */
    private InteractionResult selfService(ServerPlayer player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(ModRegistry.SUTURE_KIT.get())) {
                ItemStack kit = inv.getItem(i);
                if (!FormActions.suture(player, this)) return InteractionResult.FAIL;
                kit.shrink(1);
                return InteractionResult.CONSUME;
            }
        }
        for (int i = 0; i < CORPSE_SLOTS; i++) {
            if (belongings.getItem(i).is(ModRegistry.SUTURE_KIT.get())) {
                ItemStack kit = belongings.removeItem(i, 1);
                if (FormActions.suture(player, this)) return InteractionResult.CONSUME;
                FormActions.give(player, kit);
                return InteractionResult.FAIL;
            }
        }
        openBelongings(player);
        Refusals.deny(player, "suture.no_kit", Component.translatable("item.headinajar.suture_kit"));
        return InteractionResult.CONSUME;
    }

    /** A body that leaves the world for good (collapsed, killed by command, lost to the void) leaves no record behind. */
    @Override
    public void remove(RemovalReason reason) {
        boolean gone = reason.shouldDestroy() && !level().isClientSide && !isRemoved();
        super.remove(reason);
        if (gone) forget();
    }

    private void forget() {
        if (ownerId() == null || level().getServer() == null) return;
        HeadRegistry registry = HeadRegistry.get(level().getServer());
        HeadRegistry.Body record = registry.body(ownerId());
        if (record != null && record.entity().equals(getUUID())) registry.setBody(ownerId(), null);
        ServerPlayer owner = owner();
        if (owner != null && getUUID().equals(owner.getData(ModRegistry.JAR_STATE).body())) owner.getData(ModRegistry.JAR_STATE).setBody(null);
    }

    /** The body falls down for good, dropping what it wears and holds. */
    public void collapse() {
        if (isAlive()) {
            entityData.set(CORPSE, false);
            kill();
        }
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
        for (int i = 0; i < CORPSE_SLOTS; i++) {
            ItemStack stack = belongings.removeItemNoUpdate(i);
            if (!stack.isEmpty()) spawnAtLocation(stack);
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

    /** A body stays where it was left; Telekinesis can still carry it. */
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId() != null) tag.putUUID("Owner", ownerId());
        tag.putString("OwnerName", ownerName());
        tag.putBoolean("Collar", hasCollar());
        tag.putBoolean("Corpse", isCorpse());
        ListTag list = new ListTag();
        for (ItemStack stack : armor) list.add(stack.saveOptional(registryAccess()));
        tag.put("ArmorItems", list);
        tag.put("Belongings", belongings.createTag(registryAccess()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) setOwner(tag.getUUID("Owner"), tag.getString("OwnerName"));
        setCollar(tag.getBoolean("Collar"));
        entityData.set(CORPSE, tag.getBoolean("Corpse"));
        ListTag list = tag.getList("ArmorItems", Tag.TAG_COMPOUND);
        for (int i = 0; i < armor.size() && i < list.size(); i++) {
            armor.set(i, ItemStack.parseOptional(registryAccess(), list.getCompound(i)));
        }
        belongings.fromTag(tag.getList("Belongings", Tag.TAG_COMPOUND), registryAccess());
    }
}

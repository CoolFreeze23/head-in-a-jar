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
public class MechaEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> BATTERY = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> HEAT = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> VENT = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> MODULES = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Byte> ARMS = SynchedEntityData.defineId(MechaEntity.class, EntityDataSerializers.BYTE);

    private final MechaFrame frame;
    private final NonNullList<ItemStack> modules;
    private final SimpleContainer cargo = new SimpleContainer(27);
    private UUID owner;
    /** Server: arm buttons the pilot holds (bit 0 left, bit 1 right) and the previous state, for press edges. */
    public int armsHeld, armsBefore;
    public boolean ejecting;
    public int jetsLeft = 3, jetRecharge;
    /** Server-side work state for arms. */
    public final MechaLogic.ArmState[] armState = {new MechaLogic.ArmState(), new MechaLogic.ArmState()};

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
    }

    public MechaFrame frame() {
        return frame;
    }

    // --- modules -----------------------------------------------------------------------------------

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

    /** The module type in a flat slot (client and server). */
    public MechaModule moduleAt(int index) {
        String name = entityData.get(MODULES).getString("m" + index);
        if (name.isEmpty()) return null;
        try {
            return MechaModule.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public int sliderAt(int index) {
        CompoundTag tag = entityData.get(MODULES);
        return tag.contains("s" + index) ? tag.getInt("s" + index) : 50;
    }

    public boolean has(MechaModule module) {
        for (int i = 0; i < frame.totalSlots(); i++) if (moduleAt(i) == module) return true;
        return false;
    }

    /** Index of the first slot holding this module, or -1. */
    public int indexOf(MechaModule module) {
        for (int i = 0; i < frame.totalSlots(); i++) if (moduleAt(i) == module) return i;
        return -1;
    }

    /** The module on the left (0) or right (1) arm. */
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

    // --- power, heat, weight ------------------------------------------------------------------------

    public int battery() {
        return entityData.get(BATTERY);
    }

    public int batteryCapacity() {
        return frame.battery + (has(MechaModule.BATTERY_RACK) ? 500_000 : 0);
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

    /** Arm buttons as the client last drew them (for animation). */
    public int armsShown() {
        return entityData.get(ARMS);
    }

    public int mass() {
        int mass = frame.dryMass;
        for (int i = 0; i < frame.totalSlots(); i++) {
            MechaModule m = moduleAt(i);
            if (m != null) mass += m.mass;
        }
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
        if (!level().isClientSide) {
            MechaLogic.serverTick(this);
            entityData.set(ARMS, (byte) armsHeld);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        float dealt = MechaLogic.incomingDamage(this, source, amount);
        boolean hurt = dealt > 0 && super.hurt(source, dealt);
        if (hurt && !level().isClientSide) MechaLogic.afterDamage(this);
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) MechaLogic.destroyed(this);
        super.die(source);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < (has(MechaModule.PASSENGER_POD) ? 2 : 1);
    }

    /** A Passenger Pod rider sits behind the cockpit. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return new Vec3(0, frame.height * 0.55, -frame.width * 0.6);
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
        return !has(MechaModule.ANCHOR_CLAMPS) && super.causeFallDamage(distance - 3, multiplier, source);
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

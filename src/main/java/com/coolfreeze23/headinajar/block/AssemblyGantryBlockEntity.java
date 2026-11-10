package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.item.MechaFrameItem;
import com.coolfreeze23.headinajar.item.MechaModuleItem;
import com.coolfreeze23.headinajar.mecha.MechaFrame;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.menu.AssemblyGantryMenu;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * The Assembly Gantry: put a frame and modules in, press Assemble (10 kFE) and the mecha stands on the pad above.
 * Disassemble pulls a mecha on the pad back into the slots for editing; Repair spends Mecha Plating (20 HP each). A
 * mecha standing on the pad charges from the Gantry's buffer.
 */
public class AssemblyGantryBlockEntity extends BlockEntity implements MenuProvider, HasItems {
    public static final int SLOT_FRAME = 0, SLOT_PLATING = 1, FIRST_MODULE = 2, MODULE_SLOTS = MechaFrame.maxSlots();
    public static final int ASSEMBLE_COST = 10_000;
    /**
     * What automation sees: a hopper may keep the Mecha Plating topped up (from the top and sides); the frame and the
     * modules are the builder's own, set by hand, and never taken by a pipe.
     */
    public static final MachineItems.Role[] ROLES = roles();

    private static MachineItems.Role[] roles() {
        MachineItems.Role[] roles = MachineItems.all(FIRST_MODULE + MODULE_SLOTS, MachineItems.Role.HAND);
        roles[SLOT_PLATING] = MachineItems.Role.INPUT;
        return roles;
    }

    private final Buffer energy = new Buffer(100_000, 5_000, 0, this::setChanged);
    private final ItemStackHandler items = new ItemStackHandler(FIRST_MODULE + MODULE_SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == SLOT_FRAME) return stack.getItem() instanceof MechaFrameItem;
            if (slot == SLOT_PLATING) return stack.is(ModRegistry.MECHA_PLATING.get());
            MechaFrame frame = frame();
            if (frame == null || !(stack.getItem() instanceof MechaModuleItem module)) return false;
            return frame.slotAt(slot - FIRST_MODULE) == module.type().slot;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == SLOT_PLATING ? 64 : 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private int selected;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> energy.getEnergyStored() / 10;
                case 1 -> selected;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
            if (i == 1) selected = v;
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public AssemblyGantryBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.GANTRY_BE.get(), pos, state);
    }

    public Buffer energy() {
        return energy;
    }

    @Override
    public ItemStackHandler items() {
        return items;
    }

    public MechaFrame frame() {
        return items.getStackInSlot(SLOT_FRAME).getItem() instanceof MechaFrameItem f ? f.frame() : null;
    }

    /** The mecha standing on the pad (owned by this player or nobody), if any. */
    public MechaEntity onPad() {
        if (level == null) return null;
        List<MechaEntity> list = level.getEntitiesOfClass(MechaEntity.class, new AABB(worldPosition.above()).inflate(1, 2, 1), m -> !m.isVehicle());
        return list.isEmpty() ? null : list.get(0);
    }

    /** FE a tick the Gantry moves from its own buffer into a mecha standing on its pad (its intake rate). */
    public static final int PAD_CHARGE = 5_000;

    /** Every tick: a mecha on the pad, piloted or parked, charges from the Gantry's buffer; what it gains the Gantry loses. */
    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, AssemblyGantryBlockEntity gantry) {
        if (gantry.energy.getEnergyStored() <= 0) return;
        List<MechaEntity> list = level.getEntitiesOfClass(MechaEntity.class, new AABB(pos.above()).inflate(1, 2, 1), MechaEntity::isAlive);
        if (list.isEmpty()) return;
        MechaEntity mecha = list.get(0);
        int moved = Math.max(0, Math.min(Math.min(PAD_CHARGE, gantry.energy.getEnergyStored()), mecha.batteryCapacity() - mecha.battery()));
        if (moved <= 0) return;
        mecha.setBattery(mecha.battery() + moved);
        gantry.energy.set(gantry.energy.getEnergyStored() - moved);
    }

    /** Whether a mecha with someone aboard stands on the pad (it can't be worked on until they get out). */
    private boolean padOccupied() {
        return level != null && !level.getEntitiesOfClass(MechaEntity.class, new AABB(worldPosition.above()).inflate(1, 2, 1), MechaEntity::isVehicle).isEmpty();
    }

    /** "Pad empty", or "someone's aboard" when the only mecha there has a pilot or passenger. */
    private boolean noMecha(ServerPlayer player) {
        return say(player, padOccupied() ? "pad_occupied" : "pad_empty");
    }

    public boolean button(ServerPlayer player, int id) {
        MechaFrame frame = frame();
        switch (id) {
            case AssemblyGantryMenu.ASSEMBLE -> {
                if (frame == null) return say(player, "no_frame");
                if (onPad() != null || padOccupied()) return say(player, "pad_busy");
                if (!com.coolfreeze23.headinajar.mecha.MechaLogic.walks(frame, this::fitted)) {
                    Object[] legs = com.coolfreeze23.headinajar.mecha.MechaLogic.WALKING_LEGS.stream()
                            .map(leg -> Component.translatable(ModRegistry.mechaModule(leg).getDescriptionId())).toArray();
                    return com.coolfreeze23.headinajar.feedback.Refusals.deny(player, com.coolfreeze23.headinajar.feedback.Refuser.at(this), "gantry.no_legs", legs);
                }
                if (energy.getEnergyStored() < ASSEMBLE_COST) return say(player, "no_power");
                MechaEntity mecha = ModRegistry.mechaType(frame).create(level);
                if (mecha == null) return say(player, "failed");
                List<ItemStack> modules = new ArrayList<>();
                for (int i = 0; i < frame.totalSlots(); i++) modules.add(items.extractItem(FIRST_MODULE + i, 1, false));
                mecha.setModules(modules);
                com.coolfreeze23.headinajar.item.MechaFrameItem.genes(items.getStackInSlot(SLOT_FRAME)).ifPresent(g -> mecha.getData(ModRegistry.GENES).copyFrom(g));
                mecha.setOwner(player.getUUID());
                // the new battery holds what the Gantry has left after the assembly (as much as it takes), and the Gantry
                // keeps the rest; the pad goes on charging it from there
                int left = energy.getEnergyStored() - ASSEMBLE_COST;
                int given = Math.max(0, Math.min(left, mecha.batteryCapacity()));
                mecha.setBattery(given);
                mecha.moveTo(worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, player.getYRot() + 180, 0);
                mecha.setYBodyRot(player.getYRot() + 180);
                mecha.setYHeadRot(player.getYRot() + 180);
                items.extractItem(SLOT_FRAME, 1, false);
                energy.set(left - given);
                level.addFreshEntity(mecha);
                com.coolfreeze23.headinajar.Advance.grant(player, "assembled");
                if (frame == MechaFrame.EXECUTIVE) com.coolfreeze23.headinajar.Advance.grant(player, "chairman");
                level.playSound(null, worldPosition, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1, 0.7f);
                level.playSound(null, worldPosition, com.coolfreeze23.headinajar.registry.ModSounds.MECHA_BOOT.get(), SoundSource.BLOCKS, 1, 1);
                return true;
            }
            case AssemblyGantryMenu.DISASSEMBLE -> {
                MechaEntity mecha = onPad();
                if (mecha == null) return noMecha(player);
                if (!mecha.isOwnedBy(player)) return say(player, "not_yours");
                if (frame != null) return say(player, "slots_busy");
                for (int i = 0; i < MODULE_SLOTS; i++) if (!items.getStackInSlot(FIRST_MODULE + i).isEmpty()) return say(player, "slots_busy");
                ItemStack frameStack = new ItemStack(ModRegistry.frameItem(mecha.frame()));
                com.coolfreeze23.headinajar.item.MechaFrameItem.setGenes(frameStack, mecha.getData(ModRegistry.GENES));
                items.setStackInSlot(SLOT_FRAME, frameStack);
                for (int i = 0; i < mecha.modules().size(); i++) items.setStackInSlot(FIRST_MODULE + i, mecha.modules().get(i).copy());
                // the battery's charge comes back into the Gantry, as much as it has room for
                energy.set(energy.getEnergyStored() + mecha.battery());
                for (int i = 0; i < mecha.cargo().getContainerSize(); i++) net.minecraft.world.Containers.dropItemStack(level,
                        worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ(), mecha.cargo().removeItemNoUpdate(i));
                mecha.discard();
                level.playSound(null, worldPosition, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1, 0.8f);
                return true;
            }
            case AssemblyGantryMenu.REPAIR -> {
                MechaEntity mecha = onPad();
                if (mecha == null) return noMecha(player);
                if (!mecha.isOwnedBy(player)) return say(player, "not_yours");
                ItemStack plating = items.getStackInSlot(SLOT_PLATING);
                while (!plating.isEmpty() && mecha.getHealth() < mecha.getMaxHealth()) {
                    plating.shrink(1);
                    // smiths on staff make each plate go a third further
                    mecha.heal(20 / (1 - 0.25f * com.coolfreeze23.headinajar.block.HeadJobs.staff(level, worldPosition, "armorer", "weaponsmith", "toolsmith")));
                }
                // the pad charges a mecha standing on it all the time; Repair also tops it up at once, honestly:
                // the charge moves from the Gantry into the battery, what the mecha gains, the Gantry loses
                int moved = Math.max(0, Math.min(energy.getEnergyStored(), mecha.batteryCapacity() - mecha.battery()));
                mecha.setBattery(mecha.battery() + moved);
                energy.set(energy.getEnergyStored() - moved);
                level.playSound(null, worldPosition, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.4f);
                return true;
            }
            case AssemblyGantryMenu.PREV, AssemblyGantryMenu.NEXT -> {
                int n = frame == null ? MODULE_SLOTS : frame.totalSlots();
                selected = Math.floorMod(selected + (id == AssemblyGantryMenu.NEXT ? 1 : -1), n);
                return true;
            }
            case AssemblyGantryMenu.MINUS, AssemblyGantryMenu.PLUS -> {
                ItemStack stack = items.getStackInSlot(FIRST_MODULE + selected);
                if (!(stack.getItem() instanceof MechaModuleItem module)) return say(player, "slot_empty");
                if (module.type().slider == null) return say(player, "no_slider", stack.getHoverName());
                MechaModuleItem.setSlider(stack, MechaModuleItem.slider(stack) + (id == AssemblyGantryMenu.PLUS ? 10 : -10));
                setChanged();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** Whether a module of this kind sits in one of the frame's slots. */
    private boolean fitted(MechaModule module) {
        MechaFrame frame = frame();
        if (frame == null) return false;
        for (int i = 0; i < frame.totalSlots(); i++) {
            if (items.getStackInSlot(FIRST_MODULE + i).getItem() instanceof MechaModuleItem item && item.type() == module) return true;
        }
        return false;
    }

    /** A refusal from the Gantry (drawn over its screen, where the action bar can't be seen). */
    private boolean say(ServerPlayer player, String reason, Object... args) {
        return com.coolfreeze23.headinajar.feedback.Refusals.deny(player, com.coolfreeze23.headinajar.feedback.Refuser.at(this), "gantry." + reason, args);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Energy", energy.serializeNBT(registries));
        tag.put("Items", items.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Energy")) energy.deserializeNBT(registries, tag.get("Energy"));
        // older Gantries had fewer module slots: read into a handler of the saved size, then copy across
        CompoundTag saved = tag.getCompound("Items");
        ItemStackHandler old = new ItemStackHandler();
        old.deserializeNBT(registries, saved);
        for (int i = 0; i < items.getSlots(); i++) items.setStackInSlot(i, i < old.getSlots() ? old.getStackInSlot(i) : ItemStack.EMPTY);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.headinajar.assembly_gantry");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new AssemblyGantryMenu(id, inventory, items, data, worldPosition);
    }

    public ServerLevel serverLevel() {
        return (ServerLevel) level;
    }
}

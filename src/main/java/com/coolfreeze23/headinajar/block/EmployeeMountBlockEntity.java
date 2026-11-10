package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * An Employee Mount: a bracket on a machine's side holding a Jarred Villager, who works there for 1 emerald a day from a
 * chest touching the mount. Unpaid staff work at half bonus and complain; nitwits do nothing and say so.
 */
public class EmployeeMountBlockEntity extends BlockEntity implements HasItems, HeadHolderBlock.Holder {
    public static final int COMPLAINT_TICKS = 2400;
    private static final String[] COMPLAINTS = {"dental", "overtime", "jar", "union", "view", "smell", "pension", "broth", "chair", "manager"};
    private final ItemStackHandler items = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return HeadJobs.type(stack) == EntityType.VILLAGER;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    };
    private long paidThrough = -1;
    private final HeadInfo.Cache info = new HeadInfo.Cache();

    public EmployeeMountBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.MOUNT_BE.get(), pos, state);
    }

    @Override
    public ItemStackHandler items() {
        return items;
    }

    public ItemStack head() {
        return items.getStackInSlot(0);
    }

    /** What the head is (cached until the slot changes). */
    public HeadInfo headInfo() {
        return info.get(head());
    }

    /** The villager's profession, or "" (cached until the slot changes). */
    public String profession() {
        return headInfo().profession();
    }

    /** The machine this mount is bolted to: the block behind it (as placed facing the machine), or the one under it. */
    public boolean facesInto(BlockPos machine) {
        Direction facing = getBlockState().getValue(MachineBlock.FACING);
        return worldPosition.relative(facing.getOpposite()).equals(machine) || worldPosition.below().equals(machine);
    }

    private long day() {
        return level == null ? 0 : level.getDayTime() / 24000L;
    }

    public boolean paid() {
        return !head().isEmpty() && !"nitwit".equals(profession()) && paidThrough >= day();
    }

    /** Takes today's emerald from any container touching the mount (never the machine itself). Returns whether it paid. */
    public boolean payroll() {
        if (level == null || head().isEmpty() || paidThrough >= day()) return paidThrough >= day();
        for (Direction d : Direction.values()) {
            BlockPos at = worldPosition.relative(d);
            if (facesInto(at)) continue;
            var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, at, d.getOpposite());
            if (handler == null) continue;
            for (int i = 0; i < handler.getSlots(); i++) {
                if (handler.getStackInSlot(i).is(Items.EMERALD) && !handler.extractItem(i, 1, false).isEmpty()) {
                    paidThrough = day();
                    setChanged();
                    return true;
                }
            }
        }
        return false;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EmployeeMountBlockEntity mount) {
        if (mount.head().isEmpty()) return;
        if (level.getGameTime() % 100 == 0) mount.payroll();
        if (level.getGameTime() % COMPLAINT_TICKS == pos.asLong() % COMPLAINT_TICKS && !mount.paid()) mount.complain();
    }

    private void complain() {
        String job = profession();
        String key = "nitwit".equals(job) ? "staff.headinajar.nitwit." + level.random.nextInt(4)
                : "staff.headinajar.complaint." + COMPLAINTS[level.random.nextInt(COMPLAINTS.length)];
        Component line = Component.translatable("staff.headinajar.says", Component.translatable("entity.minecraft.villager." + job), Component.translatable(key));
        for (Player p : level.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(worldPosition).inflate(12))) p.displayClientMessage(line, false);
        level.playSound(null, worldPosition, com.coolfreeze23.headinajar.registry.ModSounds.STAFF_COMPLAIN.get(), SoundSource.BLOCKS, 0.8f, 1);
    }

    /** Use: hang a jarred villager, check on your staff, or (sneaking) take them down. */
    @Override
    public void use(ServerPlayer player, ItemStack held) {
        if (head().isEmpty()) {
            if (HeadJobs.type(held) != EntityType.VILLAGER) {
                com.coolfreeze23.headinajar.feedback.Refusals.deny(player, com.coolfreeze23.headinajar.feedback.Refuser.at(this), "mount.needs_villager", EntityType.VILLAGER.getDescription());
                return;
            }
            items.setStackInSlot(0, held.split(1));
            payroll();
            com.coolfreeze23.headinajar.Advance.grant(player, "staffed");
            level.playSound(null, worldPosition, SoundEvents.VILLAGER_WORK_LIBRARIAN, SoundSource.BLOCKS, 1, 1);
            return;
        }
        if (player.isShiftKeyDown()) {
            com.coolfreeze23.headinajar.form.FormActions.give(player, items.extractItem(0, 1, false));
            return;
        }
        String job = profession();
        player.displayClientMessage(Component.translatable(paid() ? "staff.headinajar.status_paid" : "staff.headinajar.status_unpaid",
                Component.translatable("entity.minecraft.villager." + job)), true);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", items.serializeNBT(registries));
        tag.putLong("PaidThrough", paidThrough);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        MachineItems.load(items, tag.getCompound("Items"), registries);
        paidThrough = tag.contains("PaidThrough") ? tag.getLong("PaidThrough") : -1;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("Items", HeadInfo.syncTag(items, registries));
        tag.putBoolean("Synced", true);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.menu.LabMenu;
import com.coolfreeze23.headinajar.registry.ModFluids;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * The Cerebral Array: up to nine jarred heads make FE by thinking very hard about broth (1 mB of Nutrient Broth per head per
 * second). Five or more heads hold a Board Meeting: +10% output, and now and then a vote. A Dragon Head needs the Board Room
 * Core in the tenth slot. Power goes out to every neighbour that takes FE.
 */
public class CerebralArrayBlockEntity extends LabMachine {
    public static final int HEAD_SLOTS = 9, SLOT_CORE = 9, TANK = 8000, MEETING = 5, VOTE_TICKS = 6000;
    private static final String[] MOTIONS = {"broth", "lunch", "windows", "chair", "union", "dental", "merger", "picnic"};
    private final FluidTank broth = new FluidTank(TANK, f -> f.getFluid().isSame(ModFluids.BROTH.source.get())) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private int output;
    /** What the clients last saw in the head slots (the rack is drawn from it). */
    private int shownHeads = -1;

    public CerebralArrayBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.ARRAY_BE.get(), pos, state, LabMenu.Kind.CEREBRAL_ARRAY, 500_000, 20_000);
    }

    @Override
    protected boolean valid(int slot, ItemStack stack) {
        if (slot == SLOT_CORE) return stack.is(ModRegistry.BOARD_ROOM_CORE.get());
        return HeadJobs.type(stack) != null || stack.is(Items.DRAGON_HEAD);
    }

    @Override
    protected int value(int i) {
        return switch (i) {
            case 2 -> broth.getFluidAmount();
            case 3 -> heads();
            default -> 0;
        };
    }

    public IFluidHandler broth() {
        return broth;
    }

    public int heads() {
        int n = 0;
        for (int i = 0; i < HEAD_SLOTS; i++) if (!items.getStackInSlot(i).isEmpty()) n++;
        return n;
    }

    /** FE/t right now, before broth: each head's output, +10% at a Board Meeting. */
    public int potential() {
        boolean boardRoom = !items.getStackInSlot(SLOT_CORE).isEmpty();
        int sum = 0;
        for (int i = 0; i < HEAD_SLOTS; i++) sum += HeadJobs.arrayOutput(items.getStackInSlot(i), boardRoom);
        return heads() >= MEETING ? Math.round(sum * 1.1f) : sum;
    }

    public int output() {
        return output;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CerebralArrayBlockEntity a) {
        int heads = a.heads();
        // broth: 1 mB per head per second (a paid Farmer halves it)
        if (heads > 0 && level.getGameTime() % 20 == 0) {
            float farmer = HeadJobs.staff(level, pos, "farmer");
            int need = Math.max(1, Math.round(heads * (1 - 0.5f * farmer)));
            FluidStack got = a.broth.drain(need, IFluidHandler.FluidAction.EXECUTE);
            a.getPersistentData().putBoolean("Fed", got.getAmount() >= need);
        }
        boolean fed = a.getPersistentData().getBoolean("Fed");
        a.output = fed ? a.potential() : 0;
        a.progress = a.output;
        if (a.output > 0) a.energy.set(a.energy.getEnergyStored() + a.output);
        a.push(level, pos);
        if (level.getGameTime() % 10 == 0) {
            int signature = 0;
            for (int i = 0; i <= SLOT_CORE; i++) signature = signature * 31 + ItemStack.hashItemAndComponents(a.items.getStackInSlot(i));
            if (signature != a.shownHeads) {
                a.shownHeads = signature;
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }
        if (heads >= MEETING && fed && level.getGameTime() % VOTE_TICKS == pos.asLong() % VOTE_TICKS) a.vote(level);
        if (fed && level.getGameTime() % 100 == 0) {
            if (heads >= MEETING) com.coolfreeze23.headinajar.Advance.nearby(level, pos.getCenter(), 16, "board_meeting");
            if (a.output >= 500) com.coolfreeze23.headinajar.Advance.nearby(level, pos.getCenter(), 16, "brain_trust");
        }
    }

    /** Sends FE to every neighbour that takes it. */
    private void push(Level level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (energy.getEnergyStored() <= 0) return;
            var target = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(d), d.getOpposite());
            if (target == null || !target.canReceive()) continue;
            int sent = target.receiveEnergy(Math.min(energy.getEnergyStored(), 20_000), false);
            if (sent > 0) energy.set(energy.getEnergyStored() - sent);
        }
    }

    private void vote(Level level) {
        int ayes = 1 + level.random.nextInt(heads());
        Component line = Component.translatable("array.headinajar.vote", Component.translatable("array.headinajar.motion." + MOTIONS[level.random.nextInt(MOTIONS.length)]),
                ayes, heads() - ayes);
        for (Player p : level.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(worldPosition).inflate(32))) p.displayClientMessage(line, false);
        level.playSound(null, worldPosition, com.coolfreeze23.headinajar.registry.ModSounds.ARRAY_MURMUR.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1, 1);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("Items", items.serializeNBT(registries));
        tag.putBoolean("Synced", true);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Broth", broth.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        broth.readFromNBT(registries, tag.getCompound("Broth"));
    }
}

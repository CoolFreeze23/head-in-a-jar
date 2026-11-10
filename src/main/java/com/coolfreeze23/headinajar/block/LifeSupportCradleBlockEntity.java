package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.entity.HeadJarEntity;
import com.coolfreeze23.headinajar.form.HeadRegistry;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Holds one player's head, safe from harm, while they walk around in their body. */
public class LifeSupportCradleBlockEntity extends BlockEntity {
    private UUID owner;
    private String ownerName = "";
    private ItemStack goggles = ItemStack.EMPTY;
    /** The docked head is in a Compact Jar, so it comes out as one even when its owner is offline. */
    private boolean compact;
    private final Buffer energy = new Buffer(50_000, 2_000, 0, this::setChanged);
    private final net.neoforged.neoforge.fluids.capability.templates.FluidTank saline =
            new net.neoforged.neoforge.fluids.capability.templates.FluidTank(4000, f -> f.getFluid().isSame(com.coolfreeze23.headinajar.registry.ModFluids.SALINE.source.get())) {
                @Override
                protected void onContentsChanged() {
                    setChanged();
                }
            };

    public Buffer energy() {
        return energy;
    }

    public net.neoforged.neoforge.fluids.capability.templates.FluidTank saline() {
        return saline;
    }

    public LifeSupportCradleBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.CRADLE_BE.get(), pos, state);
    }

    /** The Cradle the player stands on (or in), if any. */
    public static LifeSupportCradleBlockEntity under(Player player) {
        BlockPos pos = player.blockPosition();
        for (BlockPos p : new BlockPos[]{pos, pos.below()}) {
            if (player.level().getBlockEntity(p) instanceof LifeSupportCradleBlockEntity cradle) return cradle;
        }
        return null;
    }

    /** The Cradle within this many blocks of a position that is free, nearest first. */
    public static LifeSupportCradleBlockEntity nearbyEmpty(Level level, BlockPos center, int radius) {
        LifeSupportCradleBlockEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (level.getBlockEntity(p) instanceof LifeSupportCradleBlockEntity cradle && cradle.isEmpty()) {
                double d = p.distSqr(center);
                if (d < bestDist) {
                    bestDist = d;
                    best = cradle;
                }
            }
        }
        return best;
    }

    public boolean isEmpty() {
        return owner == null;
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public ItemStack goggles() {
        return goggles;
    }

    public void dock(UUID owner, String name, ItemStack goggles) {
        this.owner = owner;
        this.ownerName = name;
        this.goggles = goggles.copy();
        changed();
    }

    public void setCompact(boolean compact) {
        this.compact = compact;
        setChanged();
    }

    public void clear() {
        owner = null;
        ownerName = "";
        goggles = ItemStack.EMPTY;
        compact = false;
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LifeSupportCradleBlockEntity cradle) {
        if (cradle.owner == null || level.getGameTime() % 20 != 0 || level.getServer() == null) return;
        cradle.upkeep(level.getServer().getPlayerList().getPlayer(cradle.owner), level.getGameTime());
        // the registry is the truth: if the player swapped into this head, the Cradle is empty again
        HeadRegistry.Head head = HeadRegistry.get(level.getServer()).head(cradle.owner);
        if (head == null || head.where() != HeadRegistry.Where.CRADLE || !head.spot().dimension().equals(level.dimension())
                || !BlockPos.containing(head.spot().pos()).equals(pos)) {
            cradle.clear();
        }
    }

    /** Keeps the docked head topped up: saline from the tank, charge from the buffer, +1 integrity every 10 s. */
    private void upkeep(net.minecraft.server.level.ServerPlayer owner, long time) {
        if (owner == null) return;
        var state = owner.getData(ModRegistry.JAR_STATE);
        if (!state.form().isDecapitated()) return;
        energy.set(energy.getEnergyStored() - 100);
        int want = state.maxSaline() - state.saline();
        if (want > 0) {
            int got = saline.drain(want, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE).getAmount();
            state.setSaline(state.saline() + got);
        }
        int room = com.coolfreeze23.headinajar.form.JarState.maxCharge() - state.charge();
        int moved = Math.min(Math.min(10_000, room), energy.getEnergyStored());
        if (moved > 0) {
            energy.set(energy.getEnergyStored() - moved);
            state.setCharge(state.charge() + moved);
        }
        if (time % 200 == 0 && state.integrity() < state.maxIntegrity()) state.setIntegrity(state.integrity() + 1);
    }

    /** The Cradle is being broken: its head is set down where it stood. */
    public void ejectHead() {
        if (owner == null || !(level instanceof ServerLevel server)) return;
        // only a head the registry still has here: one its owner already swapped into is not left behind as a ghost
        HeadRegistry.Head record = HeadRegistry.get(server.getServer()).head(owner);
        if (record == null || record.where() != HeadRegistry.Where.CRADLE || !record.spot().dimension().equals(server.dimension())
                || !BlockPos.containing(record.spot().pos()).equals(worldPosition)) {
            owner = null;
            return;
        }
        HeadJarEntity head = new HeadJarEntity(ModRegistry.HEAD_JAR.get(), server);
        Vec3 at = Vec3.atBottomCenterOf(worldPosition);
        head.moveTo(at.x, at.y, at.z, 0, 0);
        head.setOwner(owner, ownerName);
        head.setGoggles(goggles);
        head.setCompact(compact);
        if (server.getServer().getPlayerList().getPlayer(owner) instanceof net.minecraft.server.level.ServerPlayer online) {
            head.setCompact(com.coolfreeze23.headinajar.form.FormActions.state(online).compact());
        }
        server.addFreshEntity(head);
        HeadRegistry.get(server.getServer()).setHead(owner, new HeadRegistry.Head(HeadRegistry.Where.ENTITY, new HeadRegistry.Spot(server.dimension(), at), head.getUUID()));
        owner = null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Energy", energy.serializeNBT(registries));
        tag.put("Saline", saline.writeToNBT(registries, new CompoundTag()));
        if (owner != null) {
            tag.putUUID("Owner", owner);
            tag.putString("OwnerName", ownerName);
            tag.put("Goggles", goggles.saveOptional(registries));
            if (compact) tag.putBoolean("Compact", true);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        if (tag.contains("Energy")) energy.deserializeNBT(registries, tag.get("Energy"));
        if (tag.contains("Saline")) saline.readFromNBT(registries, tag.getCompound("Saline"));
        ownerName = tag.getString("OwnerName");
        goggles = ItemStack.parseOptional(registries, tag.getCompound("Goggles"));
        compact = tag.getBoolean("Compact");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        // never empty: an empty update is skipped by the client, which would keep drawing a head that left
        tag.putBoolean("Synced", true);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

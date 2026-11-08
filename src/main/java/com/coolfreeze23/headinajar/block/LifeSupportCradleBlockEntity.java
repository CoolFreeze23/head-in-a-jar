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

    public void clear() {
        owner = null;
        ownerName = "";
        goggles = ItemStack.EMPTY;
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LifeSupportCradleBlockEntity cradle) {
        if (cradle.owner == null || level.getGameTime() % 20 != 0 || level.getServer() == null) return;
        // the registry is the truth: if the player swapped into this head, the Cradle is empty again
        HeadRegistry.Head head = HeadRegistry.get(level.getServer()).head(cradle.owner);
        if (head == null || head.where() != HeadRegistry.Where.CRADLE || !head.spot().dimension().equals(level.dimension())
                || !BlockPos.containing(head.spot().pos()).equals(pos)) {
            cradle.clear();
        }
    }

    /** The Cradle is being broken: its head is set down where it stood. */
    public void ejectHead() {
        if (owner == null || !(level instanceof ServerLevel server)) return;
        HeadJarEntity head = new HeadJarEntity(ModRegistry.HEAD_JAR.get(), server);
        Vec3 at = Vec3.atBottomCenterOf(worldPosition);
        head.moveTo(at.x, at.y, at.z, 0, 0);
        head.setOwner(owner, ownerName);
        head.setGoggles(goggles);
        server.addFreshEntity(head);
        HeadRegistry.get(server.getServer()).setHead(owner, new HeadRegistry.Head(HeadRegistry.Where.ENTITY, new HeadRegistry.Spot(server.dimension(), at), head.getUUID()));
        owner = null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID("Owner", owner);
            tag.putString("OwnerName", ownerName);
            tag.put("Goggles", goggles.saveOptional(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        goggles = ItemStack.parseOptional(registries, tag.getCompound("Goggles"));
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

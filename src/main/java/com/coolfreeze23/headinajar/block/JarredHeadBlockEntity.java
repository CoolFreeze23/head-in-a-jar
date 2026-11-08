package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A placed jarred head. A villager's head keeps its whole villager record, so it can trade: using the jar opens the
 * normal trading screen with a stand-in villager that is saved back when trading ends. It restocks twice a day.
 */
public class JarredHeadBlockEntity extends BlockEntity {
    private String type = "";
    private CompoundTag entity = new CompoundTag();
    private long lastRestock;
    private Villager trader;

    public JarredHeadBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.JARRED_HEAD_BE.get(), pos, state);
    }

    public void load(CompoundTag tag) {
        type = tag.getString("Type");
        entity = tag.getCompound("Entity");
        setChanged();
    }

    public EntityType<?> entityType() {
        ResourceLocation id = ResourceLocation.tryParse(type);
        return id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
    }

    public ItemStack toItem() {
        ItemStack stack = new ItemStack(ModRegistry.JARRED_HEAD.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", type);
        tag.put("Entity", entity.copy());
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        return stack;
    }

    /** Opens trading for a jarred villager. Returns false for other heads, or while someone else is trading. */
    public boolean trade(ServerPlayer player) {
        if (entityType() != EntityType.VILLAGER || level == null) return false;
        if (trader != null && trader.getTradingPlayer() != null) return false;
        trader = EntityType.VILLAGER.create(level);
        if (trader == null) return false;
        trader.load(entity);
        trader.setPos(worldPosition.getX() + 0.5, worldPosition.getY(), worldPosition.getZ() + 0.5);
        long now = level.getGameTime();
        if (now - lastRestock > 12_000) {
            trader.restock();
            lastRestock = now;
        }
        trader.setTradingPlayer(player);
        trader.openTradingScreen(player, trader.getDisplayName(), trader.getVillagerData().getLevel());
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, JarredHeadBlockEntity head) {
        // trading has ended: keep what changed (uses, experience) and drop the stand-in
        if (head.trader != null && head.trader.getTradingPlayer() == null) {
            CompoundTag tag = new CompoundTag();
            head.trader.saveWithoutId(tag);
            head.entity = tag;
            head.trader = null;
            head.setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Type", type);
        tag.put("Entity", entity);
        tag.putLong("LastRestock", lastRestock);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        type = tag.getString("Type");
        entity = tag.getCompound("Entity");
        lastRestock = tag.getLong("LastRestock");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", type);
        // the client only needs what draws the head: a villager's profession and biome type
        if (entity.contains("VillagerData")) tag.put("VillagerData", entity.get("VillagerData"));
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        type = tag.getString("Type");
        CompoundTag e = new CompoundTag();
        if (tag.contains("VillagerData")) e.put("VillagerData", tag.get("VillagerData"));
        entity = e;
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** The client-side entity record (for drawing). */
    public CompoundTag entityTag() {
        return entity;
    }
}

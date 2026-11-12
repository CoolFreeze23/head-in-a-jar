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
public class JarredHeadBlockEntity extends BlockEntity implements com.coolfreeze23.headinajar.feedback.Refuser {
    /** Block events: the head shakes no (a refusal), the bell (trading opens), a nod (a trade done). */
    public static final int EV_REFUSE = 1, EV_BELL = 2, EV_NOD = 3;
    /** The head looks at the nearest player this close (client only). */
    public static final double LOOK = 6;
    private String type = "";
    /** While trading: the stand-in's offers' total uses, to see a trade go through. */
    private int uses = -1;
    /** Client: the head's turn and nod toward the nearest player (degrees), and last tick's; when each moment was seen. */
    public float yaw, yawPrev, pitch, pitchPrev;
    private final long[] seen = {Long.MIN_VALUE / 2, Long.MIN_VALUE / 2, Long.MIN_VALUE / 2, Long.MIN_VALUE / 2};
    private CompoundTag entity = new CompoundTag();
    private long lastRestock;
    private Villager trader;
    /** {@link #type} parsed once (it is drawn every frame). */
    private String parsed;
    private EntityType<?> parsedType;

    public JarredHeadBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.JARRED_HEAD_BE.get(), pos, state);
    }

    /** From the item being placed: the head, and when its trades last restocked (so breaking and re-placing doesn't restock). */
    public void load(CompoundTag tag) {
        type = tag.getString("Type");
        entity = tag.getCompound("Entity");
        if (tag.contains("LastRestock")) lastRestock = tag.getLong("LastRestock");
        setChanged();
    }

    public EntityType<?> entityType() {
        if (!type.equals(parsed)) {
            parsed = type;
            ResourceLocation id = ResourceLocation.tryParse(type);
            parsedType = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        }
        return parsedType;
    }

    public ItemStack toItem() {
        ItemStack stack = new ItemStack(ModRegistry.JARRED_HEAD.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", type);
        tag.put("Entity", entity.copy());
        if (lastRestock != 0) tag.putLong("LastRestock", lastRestock);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        return stack;
    }

    /** Someone other than {@code player} is trading with this head now. */
    public boolean busy(ServerPlayer player) {
        return trader != null && trader.getTradingPlayer() != null && trader.getTradingPlayer() != player;
    }

    /**
     * Ends any trade now and keeps what it changed (uses, experience) in the head's record. The block is about to go
     * (broken, blown up, carried off), so the trade screen is closed rather than left working on a stand-in.
     */
    public void settle() {
        if (trader == null) return;
        var trading = trader.getTradingPlayer();
        trader.setTradingPlayer(null);
        if (trading instanceof ServerPlayer player && player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu) player.closeContainer();
        CompoundTag tag = new CompoundTag();
        trader.saveWithoutId(tag);
        entity = tag;
        trader = null;
        setChanged();
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
        uses = uses(trader);
        // the push bell on the plinth's front
        level.blockEvent(worldPosition, getBlockState().getBlock(), EV_BELL, 0);
        return true;
    }

    private static int uses(Villager trader) {
        int n = 0;
        for (var offer : trader.getOffers()) n += offer.getUses();
        return n;
    }

    /** The house refusal at a placed head: it shakes its head (the line and the sound come from the refusal). */
    @Override
    public net.minecraft.world.phys.Vec3 refusalOrigin() {
        return net.minecraft.world.phys.Vec3.atCenterOf(worldPosition).add(0, 0.3, 0);
    }

    @Override
    public void showRefusal(ServerPlayer player, String reason) {
        if (level != null) level.blockEvent(worldPosition, getBlockState().getBlock(), EV_REFUSE, 0);
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id < 1 || id > 3) return false;
        if (level != null && level.isClientSide) {
            seen[id] = level.getGameTime();
            if (id == EV_BELL) {
                // the push bell, at the plinth's front (the model's bell at (5, 4, -8) px, turned with the head)
                var p = net.minecraft.world.phys.Vec3.atBottomCenterOf(worldPosition)
                        .add(new net.minecraft.world.phys.Vec3(5 / 16.0, 4 / 16.0, -8 / 16.0).yRot((float) Math.toRadians(-getBlockState().getValue(JarredHeadBlock.FACING).toYRot())));
                level.playLocalSound(p.x, p.y, p.z, net.minecraft.sounds.SoundEvents.BELL_BLOCK, net.minecraft.sounds.SoundSource.BLOCKS, 0.3f, 2.0f, false);
            }
        }
        return true;
    }

    /** Ticks (with the partial tick) since moment {@code id} was seen on this client. */
    public double since(int id, double now) {
        return now - seen[id];
    }

    /**
     * Client: the head turns to the nearest player within {@link #LOOK} blocks (and back to the front when none),
     * chased a little each tick so it looks round rather than snaps.
     */
    public void clientTick() {
        yawPrev = yaw;
        pitchPrev = pitch;
        if (level == null) return;
        float wantYaw = 0, wantPitch = 0;
        var c = net.minecraft.world.phys.Vec3.atBottomCenterOf(worldPosition).add(0, 12.5 / 16.0, 0);
        net.minecraft.world.entity.player.Player p = level.getNearestPlayer(c.x, c.y, c.z, LOOK, false);
        if (p != null) {
            var eye = p.getEyePosition();
            double dx = eye.x - c.x, dy = eye.y - c.y, dz = eye.z - c.z;
            float to = (float) Math.toDegrees(Math.atan2(-dx, dz));
            // relative to the way the jar faces, clockwise from above as a player's head turns
            wantYaw = net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(to - getBlockState().getValue(JarredHeadBlock.FACING).getOpposite().toYRot()), -75, 75);
            wantPitch = (float) net.minecraft.util.Mth.clamp(-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))), -20, 20);
        }
        yaw += (wantYaw - yaw) * 0.2f;
        pitch += (wantPitch - pitch) * 0.2f;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, JarredHeadBlockEntity head) {
        // a trade went through: the head nods
        if (head.trader != null) {
            int now = uses(head.trader);
            if (head.uses >= 0 && now > head.uses) level.blockEvent(pos, state.getBlock(), EV_NOD, 0);
            head.uses = now;
        }
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

package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Refuser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Stores one host body, frozen in time: no decay while it is inside. Use it to store the nearest host, again to let it out. */
public class BodyLockerBlockEntity extends BlockEntity implements com.coolfreeze23.headinajar.machine.KitMachine, software.bernie.geckolib.animatable.GeoBlockEntity {
    /** Block events: a check-in (the shutter rolls down; the body is already in), a check-out (the body is already out: the show follows). */
    public static final int EV_CHECKIN = com.coolfreeze23.headinajar.machine.MachineKit.EV_OWN, EV_CHECKOUT = EV_CHECKIN + 1;
    /** The refusals' clips: nothing to take or can't (empty), the shutter down (full), not your ticket. */
    public static final int REFUSE_EMPTY = 0, REFUSE_FULL = 1, REFUSE_TICKET = 2;
    private final com.coolfreeze23.headinajar.machine.MachineKit kit = new com.coolfreeze23.headinajar.machine.MachineKit(this);
    /** What the clients are told: 0 empty, 1 a body, 2 a mecha; the entity type shown in the portrait. */
    private int shownStored;
    private String look = "";
    private CompoundTag stored;
    private String storedName = "";
    /**
     * Who checked the body in: only they, or someone they trust, take it out. Saved as {@code StoredOwner}; a locker
     * saved before the key existed has none, and lets anyone take its body out as it always did.
     */
    private java.util.UUID storedOwner;
    private final Buffer energy = new Buffer(20_000, 1_000, 0, this::setChanged);

    public BodyLockerBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.LOCKER_BE.get(), pos, state);
    }

    public Buffer energy() {
        return energy;
    }

    public boolean full() {
        return stored != null;
    }

    public void use(Player player) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) return;
        var source = Refuser.at(this);
        if (stored == null) {
            Vec3 centre = Vec3.atCenterOf(worldPosition);
            var near = server.getEntitiesOfClass(Mob.class, new AABB(worldPosition).inflate(3), m -> Hosts.isHost(m) && !m.isVehicle());
            Mob host = near.stream().filter(m -> mayStore(sp, m)).min((a, b) -> Double.compare(a.distanceToSqr(centre), b.distanceToSqr(centre))).orElse(null);
            if (host == null) {
                // a body is there, but it is someone else's
                if (!near.isEmpty()) {
                    Refusals.deny(sp, source, "locker.not_yours");
                    return;
                }
                // nothing in reach is an answer, not a refusal (a status block refuses nothing to a bare jar)
                player.displayClientMessage(Component.translatable("message.headinajar.locker_none"), true);
                return;
            }
            CompoundTag tag = new CompoundTag();
            if (!host.save(tag)) {
                Refusals.deny(sp, source, "locker.cannot_store", host.getDisplayName());
                return;
            }
            stored = tag;
            storedName = host.getDisplayName().getString();
            storedOwner = sp.getUUID();
            host.discard();
            kit.event(EV_CHECKIN, 0);
            server.playSound(null, worldPosition, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1, 0.8f);
            player.displayClientMessage(Component.translatable("message.headinajar.locker_stored", storedName), true);
        } else {
            // the ticket: the body goes back to whoever checked it in, or to someone they trust
            if (storedOwner != null && !com.coolfreeze23.headinajar.form.HeadRegistry.get(server.getServer()).trusts(storedOwner, sp)) {
                Refusals.deny(sp, source, "locker.not_yours");
                return;
            }
            if (!release(server)) {
                Refusals.deny(sp, source, "locker.unknown", storedName);
                return;
            }
            // the release is instant (the body is out on the click); the shutter's show plays after
            kit.event(EV_CHECKOUT, 0);
            server.playSound(null, worldPosition, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1, 0.8f);
            player.displayClientMessage(Component.translatable("message.headinajar.locker_released", storedName), true);
        }
        setChanged();
    }

    /** Whether this player may lock this body away: not someone else's mecha, nor someone else's pet. */
    private static boolean mayStore(ServerPlayer player, Mob host) {
        if (host instanceof com.coolfreeze23.headinajar.entity.MechaEntity mecha) return mecha.isOwnedBy(player);
        if (host instanceof net.minecraft.world.entity.TamableAnimal pet && pet.isTame() && pet.getOwnerUUID() != null
                && !pet.getOwnerUUID().equals(player.getUUID())) {
            return com.coolfreeze23.headinajar.form.HeadRegistry.get(player.server).trusts(pet.getOwnerUUID(), player);
        }
        return true;
    }

    /** Lets the stored body out in front of the locker. False (and it stays stored) if it can't be loaded. */
    private boolean release(ServerLevel server) {
        Entity entity = EntityType.loadEntityRecursive(stored, server, e -> e);
        if (entity == null) return false;
        Direction front = getBlockState().getValue(MachineBlock.FACING);
        Vec3 at = Vec3.atBottomCenterOf(worldPosition.relative(front));
        entity.moveTo(at.x, at.y, at.z, front.toYRot(), 0);
        server.addFreshEntity(entity);
        stored = null;
        storedOwner = null;
        return true;
    }

    /** The locker is being broken (or blown up): what it holds walks out rather than vanishing with it. */
    public void releaseOnBreak() {
        if (stored == null || !(level instanceof ServerLevel server)) return;
        if (!release(server)) {
            com.coolfreeze23.headinajar.HeadInAJar.LOGGER.warn("Body Locker at {} broken holding {}, which can no longer be loaded; it is lost", worldPosition, storedName);
            stored = null;
            storedOwner = null;
        }
        server.playSound(null, worldPosition, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1, 0.8f);
    }

    /** The server's tick: what the kiosk shows (the lamp: waiting while it holds a body, ready when empty). */
    public static void kitTick(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, BlockState state, BodyLockerBlockEntity locker) {
        locker.shownStored = locker.stored == null ? 0 : locker.stored.getString("id").contains("mecha") ? 2 : 1;
        locker.look = locker.stored == null ? "" : locker.stored.getString("id");
        com.coolfreeze23.headinajar.machine.MachineLamp lamp = locker.stored != null ? com.coolfreeze23.headinajar.machine.MachineLamp.WAIT : com.coolfreeze23.headinajar.machine.MachineLamp.READY;
        locker.kit.tick(lamp, false, false, 0);
    }

    @Override
    public com.coolfreeze23.headinajar.machine.MachineKit kit() {
        return kit;
    }

    /** 0 empty, 1 a body, 2 a mecha (as the clients see it). */
    public int shownStored() {
        return shownStored;
    }

    /** The stored entity's type id (the portrait). */
    public String look() {
        return look;
    }

    @Override
    public int refusalPart(String reason) {
        if (reason.equals("locker.not_yours")) return REFUSE_TICKET;
        return stored != null ? REFUSE_FULL : REFUSE_EMPTY;
    }

    @Override
    public void writeSync(CompoundTag tag) {
        if (shownStored != 0) tag.putByte("stored", (byte) shownStored);
        if (!look.isEmpty()) tag.putString("look", look);
    }

    @Override
    public void readSync(CompoundTag tag) {
        shownStored = tag.getByte("stored");
        look = tag.getString("look");
    }

    @Override
    public void clientTick() {
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = kit.syncTag();
        tag.putBoolean("Synced", true);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        kit.read(tag);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        kit.read(packet.getTag());
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        return kit.triggerEvent(id, param);
    }

    private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache cache = software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
        com.coolfreeze23.headinajar.client.MachineAnimations4.locker(this, controllers);
    }

    @Override
    public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object blockEntity) {
        return com.coolfreeze23.headinajar.client.MachineAnimations.now(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (stored != null) tag.put("Stored", stored);
        tag.putString("StoredName", storedName);
        if (storedOwner != null) tag.putUUID("StoredOwner", storedOwner);
        tag.put("Energy", energy.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stored = tag.contains("Stored") ? tag.getCompound("Stored") : null;
        storedName = tag.getString("StoredName");
        storedOwner = tag.hasUUID("StoredOwner") ? tag.getUUID("StoredOwner") : null;
        if (tag.contains("Energy")) energy.deserializeNBT(registries, tag.get("Energy"));
    }
}

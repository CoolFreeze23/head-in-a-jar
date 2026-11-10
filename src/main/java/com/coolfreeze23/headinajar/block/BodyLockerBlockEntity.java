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
public class BodyLockerBlockEntity extends BlockEntity {
    private CompoundTag stored;
    private String storedName = "";
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
            host.discard();
            server.playSound(null, worldPosition, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1, 0.8f);
            player.displayClientMessage(Component.translatable("message.headinajar.locker_stored", storedName), true);
        } else {
            if (!release(server)) {
                Refusals.deny(sp, source, "locker.unknown", storedName);
                return;
            }
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
        return true;
    }

    /** The locker is being broken (or blown up): what it holds walks out rather than vanishing with it. */
    public void releaseOnBreak() {
        if (stored == null || !(level instanceof ServerLevel server)) return;
        if (!release(server)) {
            com.coolfreeze23.headinajar.HeadInAJar.LOGGER.warn("Body Locker at {} broken holding {}, which can no longer be loaded; it is lost", worldPosition, storedName);
            stored = null;
        }
        server.playSound(null, worldPosition, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1, 0.8f);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (stored != null) tag.put("Stored", stored);
        tag.putString("StoredName", storedName);
        tag.put("Energy", energy.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stored = tag.contains("Stored") ? tag.getCompound("Stored") : null;
        storedName = tag.getString("StoredName");
        if (tag.contains("Energy")) energy.deserializeNBT(registries, tag.get("Energy"));
    }
}

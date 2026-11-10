package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.Comparator;
import java.util.UUID;

/**
 * A Turret Pedestal: a jarred mob head in a brass mount does what that mob does best, at range 16, for 10 FE/t.
 * Turrets never target their owner's team, players (unless the server allows PvP turrets), tamed animals, villagers or
 * golems.
 */
public class TurretPedestalBlockEntity extends BlockEntity implements HasItems, HeadHolderBlock.Holder {
    public static final int RANGE = 16, FE_PER_TICK = 10;
    private final Buffer energy = new Buffer(40_000, 2_000, 0, this::setChanged);
    private final ItemStackHandler items = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return HeadJobs.type(stack) != null;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    };
    private final HeadInfo.Cache info = new HeadInfo.Cache();
    private UUID owner;
    private int cooldown, lockTicks, lockedId = -1;
    /** Client: where the head looks. */
    public float yaw, yawO;
    /** A guardian head's lock: the target and the game time it began (synced on change; -1 when none). */
    private int syncedLock = -1;
    private long lockStart;

    public TurretPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.TURRET_BE.get(), pos, state);
    }

    @Override
    public ItemStackHandler items() {
        return items;
    }

    public Buffer energy() {
        return energy;
    }

    public ItemStack head() {
        return items.getStackInSlot(0);
    }

    /** What the head is (cached until the slot changes). */
    public HeadInfo headInfo() {
        return info.get(head());
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    /** Whether this turret would ever shoot at {@code e}. */
    public boolean mayTarget(LivingEntity e) {
        if (!e.isAlive() || e.isInvulnerable()) return false;
        if (e instanceof Player p) {
            if (p.isCreative() || p.isSpectator() || p.getUUID().equals(owner)) return false;
            if (!Config.get(Config.PVP_TURRETS, false)) return false;
            Player o = owner == null || level == null ? null : level.getPlayerByUUID(owner);
            return o == null || !o.isAlliedTo(p);
        }
        if (e instanceof OwnableEntity pet && pet.getOwnerUUID() != null) return false;
        if (e instanceof AbstractVillager || e instanceof IronGolem) return false;
        if (owner != null && level != null) {
            Player o = level.getPlayerByUUID(owner);
            if (o != null && o.isAlliedTo(e)) return false;
        }
        return e instanceof Enemy;
    }

    public LivingEntity findTarget() {
        Vec3 c = Vec3.atCenterOf(worldPosition).add(0, 0.8, 0);
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(worldPosition).inflate(RANGE), this::mayTarget).stream()
                .filter(e -> e.distanceToSqr(c) <= RANGE * RANGE)
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(c))).orElse(null);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TurretPedestalBlockEntity t) {
        EntityType<?> type = t.headInfo().type();
        if (type == null || t.energy.getEnergyStored() < FE_PER_TICK) {
            t.showLock(-1);
            return;
        }
        t.energy.set(t.energy.getEnergyStored() - FE_PER_TICK);
        if (t.cooldown > 0) {
            t.cooldown--;
            return;
        }
        LivingEntity target = t.findTarget();
        if (target == null) {
            t.lockTicks = 0;
            t.lockedId = -1;
            t.showLock(-1);
            return;
        }
        t.fire((ServerLevel) level, type, target);
    }

    /** Fletcher staff: +25% fire rate (paid), half that unpaid. */
    private int rate(int ticks) {
        return Math.round(ticks / (1 + 0.25f * HeadJobs.staff(level, worldPosition, "fletcher")));
    }

    private void fire(ServerLevel level, EntityType<?> type, LivingEntity target) {
        Vec3 from = Vec3.atCenterOf(worldPosition).add(0, 0.9, 0);
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.6, 0);
        Vec3 dir = to.subtract(from);
        if (type == EntityType.SKELETON || type == EntityType.STRAY || type == EntityType.BOGGED) {
            if (!takeArrow()) return;
            Arrow arrow = new Arrow(level, from.x, from.y, from.z, new ItemStack(Items.ARROW), null);
            if (type == EntityType.STRAY) arrow.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200));
            if (type == EntityType.BOGGED) arrow.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
            arrow.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
            arrow.shoot(dir.x, dir.y + dir.horizontalDistance() * 0.15, dir.z, 1.8f, 2f);
            level.addFreshEntity(arrow);
            level.playSound(null, worldPosition, SoundEvents.SKELETON_SHOOT, SoundSource.BLOCKS, 1, 1);
            cooldown = rate(20);
        } else if (type == EntityType.BLAZE) {
            Vec3 v = dir.normalize();
            SmallFireball ball = new SmallFireball(level, from.x, from.y, from.z, v);
            level.addFreshEntity(ball);
            level.playSound(null, worldPosition, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 1, 1);
            cooldown = rate(30);
        } else if (type == EntityType.GUARDIAN || type == EntityType.ELDER_GUARDIAN) {
            // a 2 s lock, then the laser
            if (target.getId() != lockedId) {
                lockedId = target.getId();
                lockTicks = 0;
            }
            // the beam is drawn by every watching client from the synced lock: no particle packets
            if (lockTicks == 0) showLock(lockedId);
            if (++lockTicks >= 40) {
                target.hurt(level.damageSources().magic(), 6);
                level.playSound(null, worldPosition, SoundEvents.GUARDIAN_ATTACK, SoundSource.BLOCKS, 1, 1);
                com.coolfreeze23.headinajar.fx.Fx.send(level, com.coolfreeze23.headinajar.fx.Fx.of(com.coolfreeze23.headinajar.fx.Fx.TURRET_SHOT, to, null, -1, target.getId(), 0));
                lockTicks = 0;
                cooldown = rate(20);
                showLock(-1);
            }
        } else if (type == EntityType.CREEPER) {
            if (target.distanceToSqr(from) > 9) return;
            level.explode(null, from.x, from.y, from.z, 3, Level.ExplosionInteraction.NONE);
            items.setStackInSlot(0, ItemStack.EMPTY);
        } else if (type == EntityType.ZOMBIE || type == EntityType.HUSK || type == EntityType.DROWNED) {
            if (target.distanceToSqr(from) > 2.5 * 2.5) return;
            target.hurt(level.damageSources().generic(), 4);
            level.playSound(null, worldPosition, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.BLOCKS, 0.6f, 1.4f);
            cooldown = rate(20);
        } else if (type == EntityType.SNOW_GOLEM) {
            Snowball ball = new Snowball(level, from.x, from.y, from.z);
            ball.shoot(dir.x, dir.y + dir.horizontalDistance() * 0.2, dir.z, 1.6f, 1f);
            level.addFreshEntity(ball);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            cooldown = rate(10);
        } else if (type == EntityType.WITCH) {
            ThrownPotion potion = new ThrownPotion(level, from.x, from.y, from.z);
            potion.setItem(PotionContents.createItemStack(Items.SPLASH_POTION, Potions.WEAKNESS));
            potion.shoot(dir.x, dir.y + dir.horizontalDistance() * 0.2, dir.z, 0.9f, 2f);
            level.addFreshEntity(potion);
            cooldown = rate(60);
        } else if (type == EntityType.EVOKER) {
            Vec3 step = new Vec3(dir.x, 0, dir.z).normalize();
            for (int i = 1; i <= Math.min(16, (int) dir.horizontalDistance() + 1); i++) {
                Vec3 p = from.add(step.scale(i));
                level.addFreshEntity(new EvokerFangs(level, p.x, worldPosition.getY(), p.z, (float) Math.atan2(step.z, step.x), i, null));
            }
            cooldown = rate(60);
        } else {
            cooldown = 20;
        }
        setChanged();
    }

    /** Tells watchers a lock began (target id) or ended (-1); sent only when it changes. */
    private void showLock(int id) {
        if (id == syncedLock || level == null || level.isClientSide) return;
        syncedLock = id;
        lockStart = level.getGameTime();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** Client: the locked target's id, or -1. */
    public int lockId() {
        return syncedLock;
    }

    /** Client: when the current lock began. */
    public long lockStart() {
        return lockStart;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) com.coolfreeze23.headinajar.client.fx.BlockBeams.loaded(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) com.coolfreeze23.headinajar.client.fx.BlockBeams.unloaded(this);
    }

    /** Skeleton turrets take arrows from a container under (or beside) the pedestal. */
    private boolean takeArrow() {
        for (Direction d : Direction.values()) {
            if (d == Direction.UP) continue;
            var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, worldPosition.relative(d), d.getOpposite());
            if (handler == null) continue;
            for (int i = 0; i < handler.getSlots(); i++) {
                if (handler.getStackInSlot(i).is(Items.ARROW) && !handler.extractItem(i, 1, false).isEmpty()) return true;
            }
        }
        return false;
    }

    @Override
    public void placedBy(net.minecraft.world.entity.LivingEntity placer) {
        setOwner(placer.getUUID());
    }

    /** Use: fit a jarred head, or take it out with an empty hand. */
    @Override
    public void use(ServerPlayer player, ItemStack held) {
        var refuser = com.coolfreeze23.headinajar.feedback.Refuser.at(this);
        if (head().isEmpty()) {
            EntityType<?> type = HeadJobs.type(held);
            if (type == null) {
                com.coolfreeze23.headinajar.feedback.Refusals.deny(player, refuser, "turret.needs_head", Component.translatable("item.headinajar.jarred_head"));
                return;
            }
            // a head with nothing to fire (a pig, a cow) would sit there drawing power: say so instead
            if (!HeadJobs.turretJob(type)) {
                com.coolfreeze23.headinajar.feedback.Refusals.deny(player, refuser, "turret.no_job", type.getDescription());
                return;
            }
            items.setStackInSlot(0, held.split(1));
            if (owner == null) setOwner(player.getUUID());
            level.playSound(null, worldPosition, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.BLOCKS, 1, 1.2f);
        } else if (held.isEmpty()) {
            com.coolfreeze23.headinajar.form.FormActions.give(player, items.extractItem(0, 1, false));
        } else {
            EntityType<?> type = headInfo().type();
            com.coolfreeze23.headinajar.feedback.Refusals.deny(player, refuser, "turret.occupied", type == null ? head().getHoverName() : type.getDescription());
        }
    }

    /** Client, 20 times a second: the head turns toward the nearest hostile it can see (a guess; the server picks the real target). */
    public static void clientTick(Level level, BlockPos pos, BlockState state, TurretPedestalBlockEntity t) {
        t.yawO = t.yaw;
        if (t.head().isEmpty()) return;
        var c = pos.getCenter();
        LivingEntity near = level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(RANGE), e -> e instanceof Enemy && e.isAlive()).stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(c))).orElse(null);
        float want = near == null ? t.yaw + 0.5f : (float) (Math.atan2(near.getZ() - c.z, near.getX() - c.x) * (180 / Math.PI)) - 90;
        t.yaw = net.minecraft.util.Mth.wrapDegrees(t.yaw + net.minecraft.util.Mth.wrapDegrees(want - t.yaw) * 0.3f);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", items.serializeNBT(registries));
        tag.put("Energy", energy.serializeNBT(registries));
        if (owner != null) tag.putUUID("Owner", owner);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        MachineItems.load(items, tag.getCompound("Items"), registries);
        if (tag.contains("Energy")) energy.deserializeNBT(registries, tag.get("Energy"));
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        // only the update tag carries the lock; a saved turret starts without one
        if (tag.contains("Lock")) {
            syncedLock = tag.getInt("Lock");
            lockStart = tag.getLong("LockStart");
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("Items", HeadInfo.syncTag(items, registries));
        tag.putBoolean("Synced", true);
        tag.putInt("Lock", syncedLock);
        tag.putLong("LockStart", lockStart);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

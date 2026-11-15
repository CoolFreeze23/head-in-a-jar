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
 * A Turret Pedestal: a jarred mob head in a brass mount does what that mob does best, at range 16, for 1 FE/t while it watches and 10 FE/t while it has a target.
 * Turrets never target their owner's team, players (unless the server allows PvP turrets), tamed animals, villagers or
 * golems.
 */
public class TurretPedestalBlockEntity extends BlockEntity implements HasItems, HeadHolderBlock.Holder, com.coolfreeze23.headinajar.machine.KitMachine,
        software.bernie.geckolib.animatable.GeoBlockEntity, com.coolfreeze23.headinajar.staff.Badged {
    /** The staff badge's colour (-1 none), worked out on the server and synced with the kit. */
    private int badge = -1;

    @Override
    public int badge() {
        return badge;
    }

    @Override
    public void setBadge(int colour) {
        badge = colour;
    }

    /** Who fitted the turret first (its employer for the head's file), or null. */
    public UUID owner() {
        return owner;
    }
    /** Block event: a shot, its param the kind (0 a shot, 1 a blaze's charge, 2 a lunge, 3 a creeper's pulse, 4 a lob, 5 a beam, 6 a nod). */
    public static final int EV_SHOT = com.coolfreeze23.headinajar.machine.MachineKit.EV_OWN;
    public static final int SHOT = 0, CHARGE = 1, LUNGE = 2, PULSE = 3, LOB = 4, BEAM = 5, NOD = 6;
    private final com.coolfreeze23.headinajar.machine.MachineKit kit = new com.coolfreeze23.headinajar.machine.MachineKit(this);
    /** The target (kept through the cooldown) and power, as the clients are told; -1 none. */
    private int targetId = -1;
    private boolean powered;
    /** Client: the mount's pitch (degrees, + up) and last tick's. */
    public float pitch, pitchO;
    public static final int RANGE = 16, FE_PER_TICK = 10, IDLE_FE_PER_TICK = 1;
    /** Has a target (shooting or reloading): runs at the firing rate. Not saved. */
    private boolean engaged;
    /** A bow head with a target and no arrow to fire: it clicks, waits, and its lamp says so. Not saved. */
    private boolean dry;
    /** What the turret's shots carry, so their splash, slowness, fangs and blast spare whoever it would never aim at. */
    public static final String SHOT_TAG = "headinajar_turret";
    private final Buffer energy = new Buffer(40_000, 2_000, 0, this::setChanged);
    private final ItemStackHandler items = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return HeadJobs.type(stack) != null;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            dry = false;
            if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    };
    private final HeadInfo.Cache info = new HeadInfo.Cache();
    private UUID owner;
    private int cooldown, lockTicks, lockedId = -1;
    /** Client: where the head looks. */
    public float yaw, yawO;
    /** Client: the bore's mouth in the world as this turret was last drawn (null until then), for the lock beam. */
    public net.minecraft.world.phys.Vec3 muzzleSeen;
    /** A guardian head's lock: the target and the game time it began (synced on change; -1 when none). */
    private int syncedLock = -1;
    private long lockStart;
    /** A Lookout: until when it is alarmed (2 FE/t, the comparator at 15), when it may sound again, and the alarm as synced. */
    private long alarmUntil, alarmAt;
    private boolean alarmShown;
    /** The alarm's second ding is due then (0 none). */
    private long dingAt;
    /** An attacker: when it last had a target (the comparator stays at 15 for 2 s after). */
    private long engagedAt = Long.MIN_VALUE / 2;
    /** The comparator signal as last told to neighbours. */
    private int signal;
    /** A Lookout's alarm repeats this often; its target glows this long; the comparator holds this long after. */
    public static final int ALARM_EVERY = 100, GLOW = 100, HOLD = 40, LOOKOUT_FE = 2;
    /** What the head says over the jar (synced with the kit, each client translating it). */
    private net.minecraft.network.chat.Component sayFirst, saySecond;
    private long sayT = Long.MIN_VALUE / 2;
    /** How high above the pedestal's floor its head's line is drawn (blocks). */
    public static final double SAY_HEIGHT = 2.55;

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

    @Override
    public ItemStack donorHead() {
        return head();
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
                .filter(e -> e.distanceToSqr(c) <= RANGE * RANGE && sees(e))
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(c))).orElse(null);
    }

    /**
     * Line of sight as a mob has it: nothing solid between the head and the target's eyes. The look starts where it
     * leaves the turret's own two cells (the head sits inside the upper one).
     */
    public boolean sees(LivingEntity e) {
        Vec3 from = Vec3.atCenterOf(worldPosition).add(0, 0.9, 0), to = e.getEyePosition();
        var own = new AABB(worldPosition).expandTowards(0, 1, 0);
        if (own.contains(to)) return true;
        Vec3 out = own.clip(to, from).orElse(from);
        from = out.add(to.subtract(out).normalize().scale(0.01));
        return level.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, e)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    /** The turret whose shot this is, from the tag its shots carry, or null. */
    public static TurretPedestalBlockEntity shooter(Level level, net.minecraft.world.entity.Entity shot) {
        if (shot == null || !shot.getPersistentData().contains(SHOT_TAG)) return null;
        BlockPos at = BlockPos.of(shot.getPersistentData().getLong(SHOT_TAG));
        return level.isLoaded(at) && level.getBlockEntity(at) instanceof TurretPedestalBlockEntity t ? t : null;
    }

    private <T extends net.minecraft.world.entity.Entity> T tagged(T shot) {
        shot.getPersistentData().putLong(SHOT_TAG, worldPosition.asLong());
        return shot;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TurretPedestalBlockEntity t) {
        EntityType<?> type = t.headInfo().type();
        if (type == null) {
            t.showLock(-1);
            t.updateSignal();
            return;
        }
        if (!HeadJobs.turretJob(type)) {
            t.watch((ServerLevel) level, type);
            t.updateSignal();
            return;
        }
        if (t.engaged) t.engagedAt = level.getGameTime();
        t.updateSignal();
        // a watching turret ticks over at 1 FE/t; one with a target (shooting, or reloading between shots) runs at 10
        int cost = t.engaged ? FE_PER_TICK : IDLE_FE_PER_TICK;
        if (t.energy.getEnergyStored() < cost) {
            t.engaged = false;
            t.showLock(-1);
            return;
        }
        t.energy.set(t.energy.getEnergyStored() - cost);
        if (t.cooldown > 0) {
            t.cooldown--;
            return;
        }
        LivingEntity target = t.findTarget();
        t.targetId = target == null ? -1 : target.getId();
        if (target == null) {
            t.lockTicks = 0;
            t.engaged = false;
            t.lockedId = -1;
            t.showLock(-1);
            return;
        }
        if (!t.engaged) {
            // waking up to fire: the rest of this tick's firing power
            if (t.energy.getEnergyStored() < FE_PER_TICK - IDLE_FE_PER_TICK) return;
            t.energy.set(t.energy.getEnergyStored() - (FE_PER_TICK - IDLE_FE_PER_TICK));
            t.engaged = true;
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
        // what it throws leaves from the mount's mouth, outside its own two cells: spawned inside them an arrow sticks
        // in the turret and a fireball or a potion bursts on it
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        Vec3 muzzle = flat.lengthSqr() < 1.0E-4 ? from.add(0, 0.7, 0) : from.add(flat.normalize().scale(0.8));
        if (type == EntityType.SKELETON || type == EntityType.STRAY || type == EntityType.BOGGED) {
            if (!takeArrow()) {
                // nothing to fire: one dry click, then a second's wait before it looks again (at the watching rate)
                if (!dry) level.playSound(null, worldPosition, com.coolfreeze23.headinajar.fx.HouseSounds.TURRET_DRY_CLICK.get(), SoundSource.BLOCKS, 1, 1.2f);
                dry = true;
                engaged = false;
                cooldown = 20;
                return;
            }
            dry = false;
            Arrow arrow = tagged(new Arrow(level, muzzle.x, muzzle.y, muzzle.z, new ItemStack(Items.ARROW), null));
            if (type == EntityType.STRAY) arrow.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200));
            if (type == EntityType.BOGGED) arrow.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
            arrow.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
            arrow.shoot(dir.x, dir.y + dir.horizontalDistance() * 0.15, dir.z, 1.8f, 2f);
            level.addFreshEntity(arrow);
            level.playSound(null, worldPosition, SoundEvents.SKELETON_SHOOT, SoundSource.BLOCKS, 1, 1);
            kit.event(EV_SHOT, SHOT);
            cooldown = rate(20);
        } else if (type == EntityType.BLAZE) {
            Vec3 v = dir.normalize();
            SmallFireball ball = tagged(new SmallFireball(level, muzzle.x, muzzle.y, muzzle.z, v));
            level.addFreshEntity(ball);
            level.playSound(null, worldPosition, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 1, 1);
            kit.event(EV_SHOT, CHARGE);
            cooldown = rate(30);
        } else if (type == EntityType.GUARDIAN || type == EntityType.ELDER_GUARDIAN) {
            // a 2 s lock, then the laser
            if (target.getId() != lockedId) {
                lockedId = target.getId();
                lockTicks = 0;
            }
            // the beam is drawn by every watching client from the synced lock: no particle packets
            if (lockTicks == 0) {
                showLock(lockedId);
                // the lock has its own sound: the target hears it coming
                level.playSound(null, worldPosition, com.coolfreeze23.headinajar.machine.MachineSounds.TURRET_LOCK.get(), SoundSource.BLOCKS, 1, 1);
            }
            if (++lockTicks >= 40) {
                target.hurt(level.damageSources().magic(), 6);
                level.playSound(null, worldPosition, SoundEvents.GUARDIAN_ATTACK, SoundSource.BLOCKS, 1, 1);
                kit.event(EV_SHOT, BEAM);
                com.coolfreeze23.headinajar.fx.Fx.send(level, com.coolfreeze23.headinajar.fx.Fx.of(com.coolfreeze23.headinajar.fx.Fx.TURRET_SHOT, to, null, -1, target.getId(), 0));
                lockTicks = 0;
                cooldown = rate(20);
                showLock(-1);
            }
        } else if (type == EntityType.CREEPER) {
            if (target.distanceToSqr(from) > 9) return;
            kit.event(EV_SHOT, PULSE);
            // the blast hurts and throws only what the turret would aim at; its owner, pets and villagers stand fast
            level.explode(null, null, new net.minecraft.world.level.ExplosionDamageCalculator() {
                @Override
                public boolean shouldDamageEntity(net.minecraft.world.level.Explosion explosion, net.minecraft.world.entity.Entity entity) {
                    return entity instanceof LivingEntity living ? mayTarget(living) : super.shouldDamageEntity(explosion, entity);
                }

                @Override
                public float getKnockbackMultiplier(net.minecraft.world.entity.Entity entity) {
                    return entity instanceof LivingEntity living && !mayTarget(living) ? 0 : super.getKnockbackMultiplier(entity);
                }
            }, from.x, from.y, from.z, 3, false, Level.ExplosionInteraction.NONE, ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER,
                    SoundEvents.GENERIC_EXPLODE);
            items.setStackInSlot(0, ItemStack.EMPTY);
        } else if (type == EntityType.ZOMBIE || type == EntityType.HUSK || type == EntityType.DROWNED) {
            if (target.distanceToSqr(from) > 2.5 * 2.5) return;
            target.hurt(level.damageSources().generic(), 4);
            level.playSound(null, worldPosition, com.coolfreeze23.headinajar.fx.HouseSounds.TURRET_THUMP.get(), SoundSource.BLOCKS, 0.6f, 1.4f);
            kit.event(EV_SHOT, LUNGE);
            cooldown = rate(20);
        } else if (type == EntityType.SNOW_GOLEM) {
            // the slowness comes with the snowball, on whatever it hits (TurretShots)
            Snowball ball = tagged(new Snowball(level, muzzle.x, muzzle.y, muzzle.z));
            ball.shoot(dir.x, dir.y + dir.horizontalDistance() * 0.2, dir.z, 1.6f, 1f);
            level.addFreshEntity(ball);
            kit.event(EV_SHOT, SHOT);
            cooldown = rate(10);
        } else if (type == EntityType.WITCH) {
            ThrownPotion potion = tagged(new ThrownPotion(level, muzzle.x, muzzle.y, muzzle.z));
            potion.setItem(PotionContents.createItemStack(Items.SPLASH_POTION, Potions.WEAKNESS));
            potion.shoot(dir.x, dir.y + dir.horizontalDistance() * 0.2, dir.z, 0.9f, 2f);
            level.addFreshEntity(potion);
            kit.event(EV_SHOT, LOB);
            cooldown = rate(60);
        } else if (type == EntityType.EVOKER) {
            Vec3 step = new Vec3(dir.x, 0, dir.z).normalize();
            for (int i = 1; i <= Math.min(16, (int) dir.horizontalDistance() + 1); i++) {
                Vec3 p = from.add(step.scale(i));
                level.addFreshEntity(tagged(new EvokerFangs(level, p.x, worldPosition.getY(), p.z, (float) Math.atan2(step.z, step.x), i, null)));
            }
            kit.event(EV_SHOT, NOD);
            cooldown = rate(60);
        } else {
            cooldown = 20;
        }
        setChanged();
    }

    // --- Lookouts -------------------------------------------------------------------------------------------------------

    /** Whether the head here is a Lookout (a head with no attack). */
    public boolean lookout() {
        EntityType<?> type = headInfo().type();
        return type != null && !HeadJobs.turretJob(type);
    }

    /**
     * A Lookout's second: 1 FE/t watching, 2 while alarmed. It scans once a second (an attacker's scan is every tick),
     * and on a hostile in sight raises its species' alarm every 5 s, makes it glow for everyone, and turns to it.
     */
    private void watch(ServerLevel level, EntityType<?> type) {
        long now = level.getGameTime();
        if (dingAt != 0 && now >= dingAt) {
            dingAt = 0;
            level.playSound(null, worldPosition.above(), com.coolfreeze23.headinajar.fx.HeadSounds.LOOKOUT_ALARM.get(), SoundSource.BLOCKS, 1.5f, 1.12f);
        }
        int cost = now < alarmUntil ? LOOKOUT_FE : IDLE_FE_PER_TICK;
        if (energy.getEnergyStored() < cost) {
            showAlarm(false);
            return;
        }
        energy.set(energy.getEnergyStored() - cost);
        engaged = false;
        if (cooldown > 0) {
            cooldown--;
            showAlarm(now < alarmUntil);
            return;
        }
        cooldown = 20;
        LivingEntity target = findTarget();
        targetId = target == null ? -1 : target.getId();
        if (target == null) {
            showAlarm(now < alarmUntil);
            return;
        }
        boolean quiet = now >= alarmUntil;
        alarmUntil = now + HOLD;
        if (now >= alarmAt) {
            alarmAt = now + ALARM_EVERY;
            var sound = alarmSound(level, type);
            if (sound != null) level.playSound(null, worldPosition.above(), sound, SoundSource.BLOCKS, 1.5f, 1);
            level.playSound(null, worldPosition.above(), com.coolfreeze23.headinajar.fx.HeadSounds.LOOKOUT_ALARM.get(), SoundSource.BLOCKS, 1.5f, 1);
            dingAt = now + 4;
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW, 0, false, false));
            if (quiet) {
                var line = com.coolfreeze23.headinajar.block.HeadSpeak.lookout(type);
                say(line[0], line[1]);
                ServerPlayer o = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
                if (o != null) com.coolfreeze23.headinajar.Advance.grant(o, "neighbourhood_watch");
            }
        }
        showAlarm(true);
        setChanged();
    }

    /** A species' alarm: its staff file's sound, else its own ambient sound (a creature from another mod), or none. */
    static net.minecraft.sounds.SoundEvent alarmSound(ServerLevel level, EntityType<?> type) {
        var id = com.coolfreeze23.headinajar.staff.StaffTable.alarm(type);
        if (id != null) {
            var s = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(id);
            if (s != null) return s;
        }
        STAND_INS.computeIfAbsent(type, t -> t.create(level) instanceof net.minecraft.world.entity.Mob mob ? mob : null);
        net.minecraft.world.entity.Mob mob = STAND_INS.get(type);
        return mob == null ? null : ((com.coolfreeze23.headinajar.mixin.MobAmbientInvoker) mob).headinajar$ambient();
    }

    /** One stand-in per species, never added to a world, to ask for its ambient sound. */
    private static final java.util.Map<EntityType<?>, net.minecraft.world.entity.Mob> STAND_INS = new java.util.HashMap<>();

    /** Server (tests): whether the Lookout is alarmed now. */
    public boolean alarmed() {
        return level != null && level.getGameTime() < alarmUntil;
    }

    /** Client: whether the Lookout is alarmed (its saline ripples). */
    public boolean alarmShown() {
        return alarmShown;
    }

    private void showAlarm(boolean on) {
        if (on == alarmShown) return;
        alarmShown = on;
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** The comparator: 15 while a Lookout is alarmed, or an attacker has a target, and for 2 s after; else 0. */
    public int signal() {
        if (level == null || head().isEmpty()) return 0;
        long now = level.getGameTime();
        return now < alarmUntil || now - engagedAt <= HOLD ? 15 : 0;
    }

    private void updateSignal() {
        int s = signal();
        if (s == signal || level == null) return;
        signal = s;
        level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        BlockPos top = worldPosition.above();
        level.updateNeighbourForOutputSignal(top, level.getBlockState(top).getBlock());
    }

    /** Server: the head says a line over the jar for 5 s (each watching client in its own language). */
    public void say(net.minecraft.network.chat.Component first, net.minecraft.network.chat.Component second) {
        if (level == null || level.isClientSide) return;
        sayFirst = first;
        saySecond = second;
        sayT = level.getGameTime();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public net.minecraft.network.chat.Component said() {
        return sayFirst;
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

    private static boolean bow(EntityType<?> type) {
        return type == EntityType.SKELETON || type == EntityType.STRAY || type == EntityType.BOGGED;
    }

    /** Whether a container under or beside the pedestal holds an arrow (nothing is taken). */
    private boolean arrowsInReach() {
        for (Direction d : Direction.values()) {
            if (d == Direction.UP) continue;
            var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, worldPosition.relative(d), d.getOpposite());
            if (handler == null) continue;
            for (int i = 0; i < handler.getSlots(); i++) if (handler.getStackInSlot(i).is(Items.ARROW)) return true;
        }
        return false;
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
            // a head with nothing to fire (a pig, a cow) keeps watch instead; with Lookouts off, it is refused as it was
            boolean lookout = !HeadJobs.turretJob(type);
            if (lookout && !Config.get(Config.LOOKOUTS, true)) {
                com.coolfreeze23.headinajar.feedback.Refusals.deny(player, refuser, "turret.no_job", type.getDescription());
                return;
            }
            items.setStackInSlot(0, held.split(1));
            com.coolfreeze23.headinajar.staff.StaffBlocks.fitted(this, head(), player);
            if (owner == null) setOwner(player.getUUID());
            level.playSound(null, worldPosition, com.coolfreeze23.headinajar.fx.HouseSounds.TURRET_FIT.get(), SoundSource.BLOCKS, 1, 1.2f);
            // what the head will need, or use up, said as it goes in
            if (bow(type) && !arrowsInReach()) {
                com.coolfreeze23.headinajar.feedback.Warnings.warn(player, 0, "turret.no_arrows", Component.translatable("block.minecraft.chest"));
            } else if (type == EntityType.CREEPER) {
                com.coolfreeze23.headinajar.feedback.Warnings.warn(player, 0, "turret.creeper_once");
            } else if (lookout) {
                com.coolfreeze23.headinajar.feedback.Warnings.warn(player, 0, "turret.lookout", type.getDescription());
            }
        } else if (held.isEmpty()) {
            ItemStack out = items.extractItem(0, 1, false);
            goodbye(out);
            com.coolfreeze23.headinajar.form.FormActions.give(player, out);
        } else {
            EntityType<?> type = headInfo().type();
            com.coolfreeze23.headinajar.feedback.Refusals.deny(player, refuser, "turret.occupied", type == null ? head().getHoverName() : type.getDescription());
        }
    }

    /** A head that worked here leaves: a member of staff says goodbye over the empty mount. */
    private void goodbye(ItemStack head) {
        var f = com.coolfreeze23.headinajar.staff.Staff.of(head);
        EntityType<?> type = HeadJobs.type(head);
        if (f == null || f.employer == null || type == null) return;
        var bye = com.coolfreeze23.headinajar.block.HeadSpeak.goodbye(type);
        say(bye[0], bye[1]);
    }

    /** The server's tick, then what the mount shows: the lamp, its target and power. */
    public static void kitTick(Level level, BlockPos pos, BlockState state, TurretPedestalBlockEntity t) {
        serverTick(level, pos, state, t);
        com.coolfreeze23.headinajar.staff.StaffBlocks.stationTick(t, t.items, 1, t.owner);
        t.powered = t.energy.getEnergyStored() > 0;
        com.coolfreeze23.headinajar.machine.MachineLamp lamp;
        if (t.head().isEmpty()) lamp = com.coolfreeze23.headinajar.machine.MachineLamp.OFF;
        else if (!t.powered) lamp = com.coolfreeze23.headinajar.machine.MachineLamp.LOW;
        else if (t.dry) lamp = com.coolfreeze23.headinajar.machine.MachineLamp.WAIT;
        else lamp = t.engaged || t.alarmed() ? com.coolfreeze23.headinajar.machine.MachineLamp.WORK : com.coolfreeze23.headinajar.machine.MachineLamp.READY;
        t.kit.tick(lamp, t.engaged, t.engaged, 0);
    }

    @Override
    public com.coolfreeze23.headinajar.machine.MachineKit kit() {
        return kit;
    }

    public int targetId() {
        return targetId;
    }

    public boolean powered() {
        return powered;
    }

    @Override
    public void writeSync(CompoundTag tag) {
        if (level != null) tag.put("Items", HeadInfo.syncTag(items, level.registryAccess()));
        tag.putInt("target", targetId);
        if (powered) tag.putBoolean("on", true);
        // a guardian head's lock, for the beam every watcher draws (-1 none)
        tag.putInt("Lock", syncedLock);
        tag.putLong("LockStart", lockStart);
        if (badge >= 0) tag.putInt("Badge", badge);
        // 0.6.1: a Lookout's alarm, and a line said in the last five seconds (a 0.6.0 game reads neither)
        if (alarmShown) tag.putBoolean("Alarm", true);
        if (sayFirst != null && level != null && level.getGameTime() - sayT < com.coolfreeze23.headinajar.block.JarredHeadBlockEntity.SAY_TICKS) {
            tag.putString("Say", net.minecraft.network.chat.Component.Serializer.toJson(sayFirst, level.registryAccess()));
            if (saySecond != null) tag.putString("Say2", net.minecraft.network.chat.Component.Serializer.toJson(saySecond, level.registryAccess()));
            tag.putLong("SayT", sayT);
        }
    }

    @Override
    public void readSync(CompoundTag tag) {
        if (level != null && tag.contains("Items")) MachineItems.load(items, tag.getCompound("Items"), level.registryAccess());
        targetId = tag.contains("target") ? tag.getInt("target") : -1;
        powered = tag.getBoolean("on");
        syncedLock = tag.contains("Lock") ? tag.getInt("Lock") : -1;
        lockStart = tag.getLong("LockStart");
        badge = tag.contains("Badge") ? tag.getInt("Badge") : -1;
        alarmShown = tag.getBoolean("Alarm");
        if (level != null && tag.contains("SayT") && tag.getLong("SayT") != sayT) {
            sayT = tag.getLong("SayT");
            sayFirst = net.minecraft.network.chat.Component.Serializer.fromJson(tag.getString("Say"), level.registryAccess());
            saySecond = tag.contains("Say2") ? net.minecraft.network.chat.Component.Serializer.fromJson(tag.getString("Say2"), level.registryAccess()) : null;
            if (level.isClientSide && sayFirst != null) com.coolfreeze23.headinajar.client.HeadSpeech.heard(worldPosition, SAY_HEIGHT, sayT, sayFirst, saySecond);
        }
    }

    /**
     * Client, 20 times a second: on a target (the server's), the mount snaps round and chases it (x0.3 a tick) and tilts
     * toward it; with none, it patrols 60 degrees either side of its front; unpowered it droops.
     */
    @Override
    public void clientTick() {
        yawO = yaw;
        pitchO = pitch;
        if (level == null || head().isEmpty()) return;
        float front = switch (getBlockState().getValue(MachineBlock.FACING)) {
            case SOUTH -> 0;
            case WEST -> 90;
            case EAST -> -90;
            default -> 180;
        };
        float wantYaw, wantPitch;
        var c = worldPosition.getCenter();
        var target = targetId >= 0 ? level.getEntity(targetId) : null;
        if (!powered) {
            wantYaw = yaw;
            wantPitch = -10;
        } else if (target != null) {
            double dx = target.getX() - c.x, dz = target.getZ() - c.z, dy = target.getY() + target.getBbHeight() * 0.6 - (c.y + 0.5);
            wantYaw = (float) Math.toDegrees(Math.atan2(dx, dz));
            wantPitch = net.minecraft.util.Mth.clamp((float) Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))), -10, 25);
        } else {
            long t = level.getGameTime() + (worldPosition.asLong() & 63);
            wantYaw = front + (float) (60 * Math.sin(2 * Math.PI * t / 130));
            wantPitch = 0;
        }
        yaw = yaw + net.minecraft.util.Mth.wrapDegrees(wantYaw - yaw) * (target != null ? 0.3f : 0.5f);
        pitch += (wantPitch - pitch) * 0.2f;
        // the yaw servo steps while it chases a target: a step on a tick it turns more than a degree, at most every fourth
        if (target != null && powered && Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - yawO)) > 1 && level.getGameTime() % 4 == 0) {
            level.playLocalSound(c.x, c.y + 0.5, c.z, com.coolfreeze23.headinajar.machine.MachineSounds.TURRET_SERVO.get(), net.minecraft.sounds.SoundSource.BLOCKS,
                    1, 0.94f + level.random.nextFloat() * 0.12f, false);
        }
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        kit.read(tag);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        kit.read(packet.getTag());
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        return kit.triggerEvent(id, param);
    }

    private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache cache = software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
        com.coolfreeze23.headinajar.client.MachineAnimations4.turret(this, controllers);
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
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = kit.syncTag();
        tag.putBoolean("Synced", true);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.Advance;
import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.clarity.Memos;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Refuser;
import com.coolfreeze23.headinajar.host.HostData;
import com.coolfreeze23.headinajar.host.HostDefinition;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.machine.MachineKit;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The foot of a Corporate Wellness Treadmill. Clocks in a host standing on the belt (checked every second, from the
 * entities over the deck only), makes its species' FE while it walks, holds an unridden walker at the seam facing the
 * head, and pushes up to 1,000 FE/t from the foot into the machines touching it, as the Galvanic Pile does. A jar riding
 * a host on the belt makes the FE too (and syncs, as on any ride). The belt's running and its Wellness Meter are block
 * states on both halves, changed at most once per {@link MachineKit#HOLD} ticks.
 */
public class TreadmillBlockEntity extends BlockEntity implements Refuser {
    public static final int CAPACITY = 10_000;
    public static final int PUSH = 1_000;
    /** Ticks between looks for a walker. */
    public static final int CHECK = 20;
    public static final int WHIR = 40;
    public static final int CARDIO = 100;
    /** A land host with no row of its own. */
    public static final double DEFAULT_FE = 8;
    public static final double CARDIO_FE = 100;
    public static final double CARDIO_RANGE = 16;
    /** How long after its pilot got off a body still counts as just ridden (for "Clocked in." and its refusal). */
    private static final int JUST_RIDDEN = 40;

    /** What a body on the belt makes, or the kind of refusal it meets ({@code refuse} null: it walks). */
    public record Verdict(double fe, String refuse) {
        public boolean walks() {
            return refuse == null;
        }
    }

    private final Buffer energy = new Buffer(CAPACITY, 0, PUSH, this::setChanged);
    private UUID walkerId;
    private Mob walker;
    private UUID owner;
    /** FE made but not yet whole. */
    private double carry;
    /** Whether the first push into a machine has been credited (memo and advancement). */
    private boolean credited;
    private long creditRetryAt;
    /** The jar last seen riding the walker, while it rides, so "Clocked in." goes to the one who just got off. */
    private UUID rider;
    /** What the walker makes this tick (0: nobody walking). */
    private double making;
    private long stateChangedAt = Long.MIN_VALUE / 2;
    /** The id of the last just-ridden body told why it won't walk (said once). */
    private int refusalTold = -1;

    public TreadmillBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.TREADMILL_BE.get(), pos, state);
    }

    public Buffer energy() {
        return energy;
    }

    public Mob walker() {
        return walker;
    }

    /** FE/t the walker makes now (before the push). */
    public double making() {
        return making;
    }

    public UUID owner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    private Direction facing() {
        return getBlockState().getValue(TreadmillBlock.FACING);
    }

    /** The middle of the belt, between the two cells, on the deck. */
    public Vec3 seam() {
        Direction f = facing();
        return new Vec3(worldPosition.getX() + 0.5 + f.getStepX() * 0.5, worldPosition.getY() + TreadmillBlock.DECK,
                worldPosition.getZ() + 0.5 + f.getStepZ() * 0.5);
    }

    /** Where a walker is held: the seam, moved toward the foot as far as a wide body needs to keep clear of the hood. */
    public Vec3 standPoint(Entity e) {
        double back = Math.max(0, e.getBbWidth() / 2 - TreadmillBlock.HOOD_CLEARANCE + 1 / 32.0);
        Direction f = facing();
        return seam().add(-f.getStepX() * back, 0, -f.getStepZ() * back);
    }

    /** The space over both cells' deck. */
    private AABB deck() {
        BlockPos head = worldPosition.relative(facing());
        return new AABB(Math.min(worldPosition.getX(), head.getX()), worldPosition.getY(), Math.min(worldPosition.getZ(), head.getZ()),
                Math.max(worldPosition.getX(), head.getX()) + 1, worldPosition.getY() + 1, Math.max(worldPosition.getZ(), head.getZ()) + 1);
    }

    /** Whether an entity's feet are over the belt (either cell). */
    public boolean onDeck(Entity e) {
        Vec3 s = seam();
        Direction f = facing();
        double dx = e.getX() - s.x, dz = e.getZ() - s.z;
        double along = dx * f.getStepX() + dz * f.getStepZ();
        double across = dx * f.getStepZ() - dz * f.getStepX();
        double y = e.getY() - worldPosition.getY();
        return Math.abs(along) <= 1 && Math.abs(across) <= 0.5 && y >= -0.01 && y <= 1;
    }

    // --- who walks, and for how much --------------------------------------------------------------------------

    /** What a body on the belt makes at its species' rate, or why it won't walk. A living mob never makes anything. */
    public static Verdict verdict(Mob mob) {
        if (!Hosts.isHost(mob)) return new Verdict(0, "living");
        HostData data = Hosts.data(mob);
        if (data.shell || mob instanceof com.coolfreeze23.headinajar.entity.MechaEntity) return new Verdict(0, "shell");
        TreadmillRate row = TreadmillRate.of(mob.getType());
        if (row != null) return row.fe().isPresent() ? new Verdict(row.fe().get(), null) : new Verdict(0, row.refuse().get());
        HostDefinition def = Hosts.definition(mob);
        if (def != null && def.has("flight")) return new Verdict(0, "flier");
        if (mob instanceof WaterAnimal) return new Verdict(0, "swimmer");
        return new Verdict(DEFAULT_FE, null);
    }

    /** FE a tick this body makes walking: its rate, the server's output setting, half if laced, +10% at full sync. */
    public static double rate(Mob mob) {
        Verdict v = verdict(mob);
        if (!v.walks()) return 0;
        HostData data = Hosts.data(mob);
        double fe = v.fe() * Config.TREADMILL_OUTPUT.get() / 100.0;
        if (data.laced) fe *= 0.5;
        if (data.effectiveSync() >= 100) fe *= 1.1;
        return fe;
    }

    /** The Wellness Meter's reading for an output: 0, 1-6, 7-12, 13-18, 19+ FE/t. */
    public static int dial(double fe) {
        if (fe <= 0) return 0;
        if (fe <= 6) return 1;
        if (fe <= 12) return 2;
        if (fe <= 18) return 3;
        return 4;
    }

    /** "10", "26.4": one decimal only when it has one. */
    public static String fe(double fe) {
        double r = Math.round(fe * 10) / 10.0;
        return r == Math.rint(r) ? Long.toString((long) r) : String.format(Locale.ROOT, "%.1f", r);
    }

    // --- the tick ---------------------------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, TreadmillBlockEntity belt) {
        belt.tick((ServerLevel) level, state);
    }

    private void tick(ServerLevel level, BlockState state) {
        long now = level.getGameTime();
        long phase = Math.floorMod(worldPosition.asLong(), (long) CHECK);
        if (walker != null && (walker.isRemoved() || !walker.isAlive() || walker.level() != level)) release(false);
        if ((now + phase) % CHECK == 0) check();
        making = 0;
        if (walker != null) {
            if (walker.isVehicle()) {
                // a jar drives it: the rider's client walks it in place; off the belt, it is no longer walking
                Player pilot = Hosts.pilot(walker);
                if (pilot != null) rider = pilot.getUUID();
                if (!onDeck(walker)) release(false);
            } else {
                if (rider != null) {
                    // the pilot just got off: it walks on without them
                    if (level.getPlayerByUUID(rider) instanceof ServerPlayer p) clockedIn(p);
                    rider = null;
                }
                hold(level);
            }
            if (walker != null) making = rate(walker);
        }
        if (making > 0 && energy.getEnergyStored() < CAPACITY) {
            carry += making;
            int whole = (int) carry;
            carry -= whole;
            if (whole > 0) energy.set(energy.getEnergyStored() + whole);
        } else if (making <= 0) {
            carry = 0;
        }
        Treadmills.output(level, worldPosition, making);

        int before = energy.getEnergyStored();
        if (before > 0) PowerBlockEntities.share(level, worldPosition, energy, PUSH);
        if (energy.getEnergyStored() < before && !credited && now >= creditRetryAt) credit(level, now);

        show(level, state, now);
        if (state.getValue(TreadmillBlock.RUNNING) && (now + phase) % WHIR == 0) {
            Vec3 s = seam();
            level.playSound(null, s.x, s.y + 0.2, s.z, TreadmillRate.RUN.get(), SoundSource.BLOCKS, 0.45f, 0.95f + 0.1f * level.random.nextFloat());
        }
        if (making > 0 && (now + phase) % CARDIO == 0) cardio(level);
    }

    /** Looks for a walker: the one it had (alive, a host, on the deck, not a shell), else a host standing on the belt. */
    public void check() {
        if (!(level instanceof ServerLevel server)) return;
        if (walker == null && walkerId != null) {
            // after a load: the body it had, if it is still here
            Entity e = server.getEntity(walkerId);
            walkerId = null;
            if (e instanceof Mob mob && onDeck(mob) && verdict(mob).walks() && !Treadmills.isWalker(mob)) clockIn(mob);
        }
        if (walker != null) {
            if (!walker.isAlive() || !Hosts.isHost(walker) || !onDeck(walker) || !verdict(walker).walks()) release(!walker.isVehicle() && walker.isAlive());
            return;
        }
        long now = server.getGameTime();
        for (Mob mob : server.getEntitiesOfClass(Mob.class, deck(), m -> m.isAlive() && Hosts.isHost(m) && onDeck(m) && !Treadmills.isWalker(m))) {
            Verdict v = verdict(mob);
            if (v.walks()) {
                clockIn(mob);
                return;
            }
            // a body its pilot just got off that won't walk: say why, once
            HostData data = Hosts.data(mob);
            if (mob.isVehicle() || now - data.lastRidden > JUST_RIDDEN || refusalTold == mob.getId()) continue;
            if (server.getNearestPlayer(mob, 6) instanceof ServerPlayer p) {
                refusalTold = mob.getId();
                refuse(p, mob, v.refuse());
            }
        }
    }

    private void clockIn(Mob mob) {
        walker = mob;
        walkerId = mob.getUUID();
        Treadmills.clockIn(level, mob, worldPosition);
        Player pilot = Hosts.pilot(mob);
        if (pilot != null) rider = pilot.getUUID();
        else if (level.getGameTime() - Hosts.data(mob).lastRidden <= JUST_RIDDEN && level.getNearestPlayer(mob, 6) instanceof ServerPlayer p) {
            // its pilot got off before the belt looked
            clockedIn(p);
        }
        setChanged();
    }

    /** The walker is no longer walking; {@code steppedOff}: it was knocked off the belt and stands where it landed. */
    private void release(boolean steppedOff) {
        if (walker == null) return;
        Mob was = walker;
        Treadmills.clockOut(level, was.getId());
        walker = null;
        walkerId = null;
        rider = null;
        setChanged();
        if (steppedOff && owner != null && level.getPlayerByUUID(owner) instanceof ServerPlayer p && p.distanceToSqr(was) < 32 * 32) {
            p.displayClientMessage(Component.translatable("message.headinajar.treadmill.stepped_off", was.getType().getDescription()), true);
        }
    }

    private static void clockedIn(ServerPlayer p) {
        p.displayClientMessage(Component.translatable("message.headinajar.treadmill.clocked_in"), true);
    }

    /** An unridden walker stays at the seam, facing the head: drifted under a block, it is put back; further, it steps off. */
    private void hold(ServerLevel level) {
        Vec3 at = standPoint(walker);
        double dx = walker.getX() - at.x, dz = walker.getZ() - at.z;
        double dy = walker.getY() - at.y;
        if (dx * dx + dz * dz >= 1 || dy < -0.5 || dy > 2) {
            release(true);
            return;
        }
        float yaw = facing().toYRot();
        if (dx * dx + dz * dz > 0.0004 || Math.abs(net.minecraft.util.Mth.wrapDegrees(walker.getYRot() - yaw)) > 1) {
            // never into a wall: a wide body with walls beside the seam walks where it stands
            boolean clear = dx * dx + dz * dz <= 0.0004 || level.noCollision(walker, walker.getBoundingBox().move(-dx, 0, -dz));
            if (clear) walker.setPos(at.x, walker.getY(), at.z);
            walker.setDeltaMovement(0, Math.min(0, walker.getDeltaMovement().y), 0);
            walker.setYRot(yaw);
            walker.setYBodyRot(yaw);
            walker.setYHeadRot(yaw);
        }
    }

    /** The first FE pushed into a machine: the owner's memo and advancement; with the owner away, the nearest player's. */
    private void credit(ServerLevel level, long now) {
        if (owner != null && level.getServer().getPlayerList().getPlayer(owner) instanceof ServerPlayer p) {
            Memos.send(p, Memos.Memo.TREADMILL_POWERED);
            Advance.grant(p, "corporate_wellness");
            credited = true;
        } else {
            Vec3 s = seam();
            if (level.getNearestPlayer(s.x, s.y, s.z, 16, false) instanceof ServerPlayer p) {
                Advance.grant(p, "corporate_wellness");
                credited = true;
            }
        }
        if (credited) setChanged();
        else creditRetryAt = now + CHECK;
    }

    /** The Cardio Department: 100 FE/t from the running belts within 16 blocks. */
    private void cardio(ServerLevel level) {
        double sum = 0;
        for (Map.Entry<BlockPos, Double> e : Treadmills.belts(level).entrySet()) {
            if (e.getKey().distSqr(worldPosition) <= CARDIO_RANGE * CARDIO_RANGE) sum += e.getValue();
        }
        if (sum >= CARDIO_FE - 1e-6) Advance.nearby(level, Vec3.atCenterOf(worldPosition), CARDIO_RANGE, "cardio_department");
    }

    /** Running and the meter, on both halves, at most once per {@link MachineKit#HOLD} ticks. */
    private void show(ServerLevel level, BlockState state, long now) {
        boolean running = making > 0;
        int dial = dial(making);
        if (state.getValue(TreadmillBlock.RUNNING) == running && state.getValue(TreadmillBlock.DIAL) == dial) return;
        if (now - stateChangedAt < MachineKit.HOLD) return;
        stateChangedAt = now;
        level.setBlock(worldPosition, state.setValue(TreadmillBlock.RUNNING, running).setValue(TreadmillBlock.DIAL, dial), 2 | 16);
        BlockPos headPos = worldPosition.relative(facing());
        BlockState head = level.getBlockState(headPos);
        if (head.getBlock() instanceof TreadmillBlock) {
            level.setBlock(headPos, head.setValue(TreadmillBlock.RUNNING, running).setValue(TreadmillBlock.DIAL, dial), 2 | 16);
        }
    }

    // --- use --------------------------------------------------------------------------------------------------

    /** Empty hand on the belt: the walker's status, or the refusal for what stands on it, or "Belt idle". */
    public void report(ServerPlayer player) {
        check();
        if (walker != null) {
            HostData data = Hosts.data(walker);
            player.displayClientMessage(Component.translatable("message.headinajar.treadmill.status", walker.getType().getDescription(), fe(rate(walker)),
                    Math.round(Math.max(0, data.freshness)), Math.round(data.effectiveSync())), true);
            if (data.laced) player.sendSystemMessage(Component.translatable("message.headinajar.treadmill.tired"));
            return;
        }
        Mob refused = null, living = null;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, deck(), this::onDeck)) {
            if (Hosts.isHost(mob)) refused = mob;
            else living = mob;
        }
        if (refused != null) refuse(player, refused, verdict(refused).refuse());
        else if (living != null) refuse(player, living, "living");
        else player.displayClientMessage(Component.translatable("message.headinajar.treadmill.idle"), true);
    }

    private void refuse(ServerPlayer player, Mob mob, String kind) {
        Refusals.deny(player, this, "treadmill." + kind, mob.getType().getDescription());
    }

    @Override
    public Vec3 refusalOrigin() {
        return seam().add(0, 0.3, 0);
    }

    @Override
    public void showRefusal(ServerPlayer player, String reason) {
        // a puff off the belt where it would have walked
        Vec3 s = seam();
        player.serverLevel().sendParticles(player, ParticleTypes.SMOKE, true, s.x, s.y + 0.2, s.z, 8, 0.3, 0.1, 0.3, 0.01);
    }

    // --- lifetime ---------------------------------------------------------------------------------------------

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            if (walker != null) Treadmills.clockOut(level, walker.getId());
            walker = null;
            Treadmills.output(level, worldPosition, 0);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Energy", energy.serializeNBT(registries));
        UUID w = walker != null ? walker.getUUID() : walkerId;
        if (w != null) tag.putUUID("Walker", w);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putDouble("Carry", carry);
        tag.putBoolean("Credited", credited);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Energy")) energy.deserializeNBT(registries, tag.get("Energy"));
        walkerId = tag.hasUUID("Walker") ? tag.getUUID("Walker") : null;
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        carry = tag.getDouble("Carry");
        credited = tag.getBoolean("Credited");
    }
}

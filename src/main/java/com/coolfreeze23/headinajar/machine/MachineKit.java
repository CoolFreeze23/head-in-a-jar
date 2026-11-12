package com.coolfreeze23.headinajar.machine;

import com.coolfreeze23.headinajar.menu.MachineMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The machine kit's server and client plumbing, owned by each machine's block entity (composition, so a machine keeps
 * whatever base class it has). On the server the machine calls {@link #tick} once a tick with the lamp it wants, the
 * levels it shows, whether its job advanced and its progress; the kit holds each blockstate change at least
 * {@link #HOLD} ticks, keeps the clock and the work rate, counts the players with the screen open, places the hidden
 * top cell, and sends the small update tag only when something in it changed. Moments go as block events.
 */
public final class MachineKit {
    /** A lamp or level shown at least this long before it changes again (a re-mesh is cheap, but not every tick). */
    public static final int HOLD = 15;
    /** How long the lamp stays red after a refusal. */
    public static final int FAULT_TICKS = 40;
    /** Block events: 1 the refusal (param: which part); 2 and up are the machine's own. */
    public static final int EV_REFUSE = 1, EV_PLOP = 2, EV_FINISH = 3, EV_OWN = 4;
    private static final int EVENTS = 8;
    /** The work rate is counted over this many ticks. */
    private static final int WINDOW = 20;

    private final BlockEntity be;

    // --- server ------------------------------------------------------------------------------------------------
    private long lampChangedAt = Long.MIN_VALUE / 2;
    private long faultUntil = Long.MIN_VALUE / 2;
    private final Map<IntegerProperty, Integer> levels = new HashMap<>();
    private final Map<IntegerProperty, Long> levelChangedAt = new HashMap<>();
    private boolean topChecked;
    /** Server: a tall machine that could not put its top cell on (something stands above it). */
    private boolean topMissing;
    private final Set<UUID> openers = new HashSet<>();
    private int window;
    private long rateChangedAt = Long.MIN_VALUE / 2;
    private CompoundTag lastSent;
    /** Client: the machine's rest pose, baked into its block model while nothing moves (the renderer's; null when it draws). */
    public volatile Object restBake;
    private int sends;

    // --- both sides (synced) -------------------------------------------------------------------------------------
    private int rate;
    private boolean open;
    /** The clock: progress {@code anchorProgress} at game time {@code anchorTime}, advancing {@code clockRate}/4 a tick. */
    private long anchorTime = -1;
    private int anchorProgress;
    private int clockRate;

    // --- client ----------------------------------------------------------------------------------------------
    private final long[] eventAt = new long[EVENTS];
    private final int[] eventParam = new int[EVENTS];

    public MachineKit(BlockEntity be) {
        this.be = be;
        java.util.Arrays.fill(eventAt, Long.MIN_VALUE / 2);
    }

    public BlockPos pos() {
        return be.getBlockPos();
    }

    // =========================================================================================================
    // server

    /**
     * Once a server tick. {@code lamp}: what the lamp should say now (a refusal's red overrides it for a moment);
     * {@code advanced}: the job moved on this tick (counts toward the work rate); {@code active}: a job is under way
     * (the clock runs); {@code progress}: its ticks so far. Set levels with {@link #level} before calling this.
     */
    public void tick(MachineLamp lamp, boolean advanced, boolean active, int progress) {
        Level level = be.getLevel();
        if (!(level instanceof ServerLevel server)) return;
        long now = level.getGameTime();
        BlockPos pos = be.getBlockPos();
        if (!topChecked || topMissing && now % 40 == Math.floorMod(pos.asLong(), 40L)) {
            topChecked = true;
            // a machine placed before it had a top cell (an old world) gets one now if there is room; else it works on,
            // and looks again every two seconds, so clearing the block above gives it its top
            topMissing = be.getBlockState().getBlock() instanceof TallMachine tall && tall.hasTop() && !TallMachine.ensureTop(level, pos);
        }
        // the work rate, in quarters of full speed, held like a blockstate
        window = ((window << 1) | (advanced ? 1 : 0)) & ((1 << WINDOW) - 1);
        int wantRate = Math.round(Integer.bitCount(window) * 4f / WINDOW);
        if (!active && !advanced) wantRate = 0;
        if (wantRate != rate && (now - rateChangedAt >= HOLD || wantRate == 0 && !active)) {
            rate = wantRate;
            rateChangedAt = now;
        }
        clock(now, active, progress, advanced);
        if (now % 5 == 0) recountOpeners(server);
        // blockstates: one setBlock for every change this tick
        BlockState state = be.getBlockState();
        BlockState next = state;
        if (state.hasProperty(MachineLamp.LAMP)) {
            MachineLamp want = now < faultUntil ? MachineLamp.FAULT : lamp;
            if (state.getValue(MachineLamp.LAMP) != want && (want == MachineLamp.FAULT || now - lampChangedAt >= HOLD)) {
                next = next.setValue(MachineLamp.LAMP, want);
                lampChangedAt = now;
            }
        }
        for (var e : levels.entrySet()) {
            IntegerProperty p = e.getKey();
            if (!state.hasProperty(p) || state.getValue(p).equals(e.getValue())) continue;
            if (now - levelChangedAt.getOrDefault(p, Long.MIN_VALUE / 2) >= HOLD) {
                next = next.setValue(p, e.getValue());
                levelChangedAt.put(p, now);
            }
        }
        if (next != state) level.setBlock(pos, next, net.minecraft.world.level.block.Block.UPDATE_ALL);
        sync();
    }

    /** A level blockstate the machine shows (applied on the next {@link #tick}, held {@link #HOLD} ticks). */
    public void level(IntegerProperty property, int value) {
        levels.put(property, value);
    }

    private void clock(long now, boolean active, int progress, boolean advanced) {
        if (!active) {
            anchorTime = -1;
            anchorProgress = 0;
            clockRate = 0;
            return;
        }
        // re-anchored only when the client's own reckoning would be more than 2 ticks out: on a start, a stall, a
        // resume, a change of pace
        double predicted = anchorTime < 0 ? -100 : anchorProgress + (now - anchorTime) * clockRate / 4.0;
        if (Math.abs(predicted - progress) > 2) {
            anchorTime = now;
            anchorProgress = progress;
            int recent = Integer.bitCount(window & 0xFF);
            clockRate = advanced ? Math.max(1, Math.round(recent / 2f)) : 0;
        }
    }

    /** The refusal on the machine: the lead part (or {@code part}) rattles, the lamp is red for a moment. */
    public void refuse(int part) {
        Level level = be.getLevel();
        if (level == null || level.isClientSide) return;
        faultUntil = level.getGameTime() + FAULT_TICKS;
        level.blockEvent(be.getBlockPos(), be.getBlockState().getBlock(), EV_REFUSE, part);
        // the red lamp now, not on the next tick
        BlockState state = be.getBlockState();
        if (state.hasProperty(MachineLamp.LAMP) && state.getValue(MachineLamp.LAMP) != MachineLamp.FAULT) {
            level.setBlock(be.getBlockPos(), state.setValue(MachineLamp.LAMP, MachineLamp.FAULT), net.minecraft.world.level.block.Block.UPDATE_ALL);
            lampChangedAt = level.getGameTime();
        }
    }

    /** A one-off moment every player within 64 blocks sees (a late joiner does not). */
    public void event(int id, int param) {
        Level level = be.getLevel();
        if (level == null || level.isClientSide) return;
        level.blockEvent(be.getBlockPos(), be.getBlockState().getBlock(), id, param);
        // kept on the server too (what a test or the server's own rules ask of it)
        if (id >= 0 && id < EVENTS) {
            eventAt[id] = level.getGameTime();
            eventParam[id] = param;
        }
    }

    /** A player opened the machine's screen (call from the server side of {@code createMenu}). */
    public void startOpen(Player player) {
        if (player.level().isClientSide) return;
        openers.add(player.getUUID());
        open = true;
        sync();
    }

    /** A player closed it (from the menu's {@code removed}). */
    public void stopOpen(Player player) {
        if (player.level().isClientSide) return;
        openers.remove(player.getUUID());
        open = !openers.isEmpty();
        sync();
    }

    /** As vanilla's chest: count again who really has the screen open, so a crash or a teleport can't leave it open. */
    private void recountOpeners(ServerLevel level) {
        openers.removeIf(id -> {
            Player p = level.getPlayerByUUID(id);
            return p == null || !(p.containerMenu instanceof MachineMenu menu) || !menu.pos().equals(be.getBlockPos());
        });
        for (Player p : level.players()) {
            if (p.containerMenu instanceof MachineMenu menu && menu.pos().equals(be.getBlockPos())) openers.add(p.getUUID());
        }
        open = !openers.isEmpty();
    }

    public int openers() {
        return openers.size();
    }

    /** Sends the update tag if anything in it changed since it was last sent. */
    public void sync() {
        Level level = be.getLevel();
        if (level == null || level.isClientSide) return;
        CompoundTag tag = syncTag();
        if (tag.equals(lastSent)) return;
        lastSent = tag;
        sends++;
        level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }

    /** How many update tags this machine has sent (tests). */
    public int sends() {
        return sends;
    }

    /** The whole update tag: the kit's fields and the machine's own. Also what a late joiner gets with the chunk. */
    public CompoundTag syncTag() {
        CompoundTag tag = new CompoundTag();
        if (rate != 0) tag.putByte("rate", (byte) rate);
        if (open) tag.putBoolean("open", true);
        if (anchorTime >= 0) {
            tag.putLong("t0", anchorTime);
            tag.putInt("p0", anchorProgress);
            tag.putByte("cr", (byte) clockRate);
        }
        if (be instanceof KitMachine machine) machine.writeSync(tag);
        return tag;
    }

    // =========================================================================================================
    // client

    /** Client: the update tag arrived. */
    public void read(CompoundTag tag) {
        rate = tag.getByte("rate");
        open = tag.getBoolean("open");
        if (tag.contains("t0")) {
            anchorTime = tag.getLong("t0");
            anchorProgress = tag.getInt("p0");
            clockRate = tag.getByte("cr");
        } else {
            anchorTime = -1;
            anchorProgress = 0;
            clockRate = 0;
        }
        if (be instanceof KitMachine machine) machine.readSync(tag);
    }

    /** From the block's {@code triggerEvent}: remembers the moment on the client. True on both sides (the event is sent). */
    public boolean triggerEvent(int id, int param) {
        Level level = be.getLevel();
        if (level != null && level.isClientSide && id >= 0 && id < EVENTS) {
            eventAt[id] = level.getGameTime();
            eventParam[id] = param;
        }
        return id >= 0 && id < EVENTS;
    }

    /** Ticks (with the partial tick) since block event {@code id} was last seen, or a large number. */
    public double since(int id, double now) {
        return now - eventAt[id];
    }

    public int eventParam(int id) {
        return eventParam[id];
    }

    /** The job's progress in ticks now ({@code now} = game time + partial tick), or -1 when no job runs. */
    public double elapsed(double now) {
        if (anchorTime < 0) return -1;
        return anchorProgress + Math.max(0, now - anchorTime) * clockRate / 4.0;
    }

    public boolean running() {
        return anchorTime >= 0;
    }

    /** The clock advances (a job runs and is not stalled). */
    public boolean advancing() {
        return anchorTime >= 0 && clockRate > 0;
    }

    /** The work rate, 0-4 quarters of full speed (both sides). */
    public int rate() {
        return rate;
    }

    /** Some player has the screen open (both sides). */
    public boolean open() {
        return open;
    }
}

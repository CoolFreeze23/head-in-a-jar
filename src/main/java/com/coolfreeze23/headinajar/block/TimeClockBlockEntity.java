package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.anim.SeekingControllerV2;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.machine.KitMachine;
import com.coolfreeze23.headinajar.machine.MachineKit;
import com.coolfreeze23.headinajar.machine.MachineLamp;
import com.coolfreeze23.headinajar.menu.TimeClockMenu;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.work.Order;
import com.coolfreeze23.headinajar.staff.work.Shifts;
import com.coolfreeze23.headinajar.staff.work.SiteIndex;
import com.coolfreeze23.headinajar.staff.work.WorkSounds;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.WorkerIndex;
import com.coolfreeze23.headinajar.staff.work.WorkerLooks;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import software.bernie.geckolib.animation.RawAnimation;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A Time Clock's state: its owner, its roster of staff (UUIDs, at most {@code staff.maxPerClock}), the Job Rack (8 cards:
 * postings that stay in the rack; idle staff take the one with the fewest takers), the Pay Drawer (9 slots, filled by
 * hand or hopper, taken from at dawn), the arrivals list (staff the company shuttle is bringing home, as entity records,
 * set down beside the clock the next time it ticks), the queue at the punch (one worker at a time, 20 ticks each: the
 * card in, the lever down on tick 6 with the punch's sound, the bell for a late one), and who has punched in for which
 * shift. Its own tick runs the dawn (each worker on the roster takes its wage from the drawer), the lamp, the punch and
 * the arrivals; the workers' day itself is {@link Shifts}'.
 */
public class TimeClockBlockEntity extends BlockEntity implements KitMachine, MenuProvider, software.bernie.geckolib.animatable.GeoBlockEntity {
    public static final int RACK = 8, DRAWER = 9;
    /** Block events: a punch starts (the clip), the bell rings (a late punch). */
    public static final int EV_PUNCH = MachineKit.EV_OWN, EV_DING = MachineKit.EV_OWN + 1;
    /**
     * A punch takes this long (the {@code punch} clip). Its sound starts with the clip: the sound's stamp lands 303 ms in,
     * with the lever on tick 6. A late worker's {@code ding} follows at once ({@link #DING_TICKS}); the bell's hammer
     * falls on the ding's tick 2.
     */
    public static final int PUNCH_TICKS = 20, DING_TICKS = 8, BELL_AT = 2;
    /** The lamp is worked out again this often; the roster is tidied this often. */
    private static final int LAMP_TICKS = 20, TIDY_TICKS = 100;
    /** The worker at the head of the queue may take this long to step up before it goes to the back. */
    private static final int STEP_UP_TICKS = 100;
    /**
     * How near the clock a worker must stay while it punches, and from how near one that cannot reach its spot at the
     * slot punches after {@link #STEP_UP_TICKS} (blocks, horizontal).
     */
    public static final double PUNCH_REACH = 3.0;
    /** How near its spot at the slot (in front of the clock) a worker must be to begin its punch (blocks, horizontal). */
    public static final double AT_SLOT = 1.0;

    private final MachineKit kit = new MachineKit(this);
    private UUID owner;
    private String ownerName = "";
    private final List<UUID> roster = new ArrayList<>();
    private final List<CompoundTag> arrivals = new ArrayList<>();
    /** The last day a dawn was run here (-1 none). */
    public long lastDawn = -1;
    /** Who works which rack posting (not saved: staff take their postings again after a load). */
    private final Map<UUID, Integer> takers = new HashMap<>();
    /** Who has punched in for which shift ({@link Shifts#stamp}); saved, so a restart does not send everyone back to the punch. */
    private final Map<UUID, Long> punched = new HashMap<>();
    /** Whether a redstone signal reaches the clock, and since when (game time). */
    private boolean powered;
    private long poweredAt;

    // not saved: the punch queue, and per-worker timers
    private record Queued(UUID id, long stamp, boolean late) {
    }

    private final List<Queued> queue = new ArrayList<>();
    private Queued punching;
    private long punchStart, headSince = -1, bellAt = -1;
    /** The last punch's start (game time) and whether it was late: synced, so the clip plays for whoever sees it. */
    private long shownPunch = Long.MIN_VALUE / 2;
    private boolean shownLate;
    private final Map<UUID, Long> overSince = new HashMap<>();
    private final Map<UUID, Long> nextLook = new HashMap<>();
    private MachineLamp lamp = MachineLamp.OFF;
    /** Tests: a time of day (0-23999) this clock's shifts read in place of the Overworld's, or -1. */
    public long timeOverride = -1;
    /** Client: how many cards the rack shows. */
    private int cardsShown;

    private final ItemStackHandler rack = new ItemStackHandler(RACK) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return forHere(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final ItemStackHandler drawer = new ItemStackHandler(DRAWER) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    /** The Broth tank (0.7.1): 4,000 mB for the site's seconded heads, filled by bucket and pipe, drunk only by them. */
    private final net.neoforged.neoforge.fluids.capability.templates.FluidTank broth = com.coolfreeze23.headinajar.staff.second.BrothTank.make(this::setChanged);
    private final net.neoforged.neoforge.fluids.capability.IFluidHandler brothPipes = com.coolfreeze23.headinajar.staff.second.BrothTank.pipes(broth);

    public TimeClockBlockEntity(BlockPos pos, BlockState state) {
        super(WorkRegistry.TIME_CLOCK_BE.get(), pos, state);
    }

    // --- who --------------------------------------------------------------------------------------------------------

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public void setOwner(Player p) {
        owner = p.getUUID();
        ownerName = p.getGameProfile().getName();
        setChanged();
        registerSite();
    }

    /** Whether a player may open this clock and give its staff orders: its owner, or someone the owner trusts. */
    public boolean trusts(Player p) {
        return owner == null || owner.equals(p.getUUID()) || com.coolfreeze23.headinajar.form.HeadRegistry.get(p).trusts(owner, p);
    }

    public GlobalPos site() {
        return GlobalPos.of(level.dimension(), worldPosition);
    }

    public AABB box() {
        return Workforce.box(worldPosition);
    }

    public Direction facing() {
        BlockState s = getBlockState();
        return s.hasProperty(TimeClockBlock.FACING) ? s.getValue(TimeClockBlock.FACING) : Direction.NORTH;
    }

    /** The cell in front of the dial, where a worker punches. */
    public BlockPos front() {
        return worldPosition.relative(facing());
    }

    /** Registers this clock in its dimension's site index (placement, first load). */
    public void registerSite() {
        if (level instanceof ServerLevel server) SiteIndex.get(server).add(worldPosition, owner, ownerName);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            registerSite();
            if (lastDawn < 0) lastDawn = Staff.day(server);
        }
    }

    // --- the roster -------------------------------------------------------------------------------------------------

    public List<UUID> roster() {
        return roster;
    }

    public boolean has(UUID worker) {
        return roster.contains(worker);
    }

    public int room() {
        return Math.max(0, com.coolfreeze23.headinajar.Config.get(com.coolfreeze23.headinajar.Config.MAX_PER_CLOCK, 8) - roster.size());
    }

    /** Takes a worker of its owner's onto the roster and ties it here. Returns whether there was room. */
    public boolean join(Mob mob) {
        if (!has(mob.getUUID())) {
            if (room() <= 0) return false;
            roster.add(mob.getUUID());
            setChanged();
        }
        Workforce.assign(mob, site());
        return true;
    }

    public void leave(UUID worker) {
        takers.remove(worker);
        punched.remove(worker);
        overSince.remove(worker);
        nextLook.remove(worker);
        queue.removeIf(q -> q.id.equals(worker));
        if (punching != null && punching.id.equals(worker)) punching = null;
        if (roster.remove(worker)) setChanged();
    }

    // --- the rack ---------------------------------------------------------------------------------------------------

    public ItemStackHandler rack() {
        return rack;
    }

    /** Whether a stack is a Job Card printed for this site. */
    public boolean forHere(ItemStack stack) {
        Order.Card c = Order.card(stack);
        return c != null && c.order().clock().isPresent() && level != null && c.order().clock().get().equals(site());
    }

    /** How many staff work the posting in a rack slot now. */
    public int takers(int slot) {
        int n = 0;
        for (int s : takers.values()) if (s == slot) n++;
        return n;
    }

    /** A worker takes (or, with -1, drops) a rack posting. */
    public void take(UUID worker, int slot) {
        if (slot < 0) takers.remove(worker);
        else takers.put(worker, slot);
    }

    /** A personal card back to the rack (Let go, Recall); false when the rack is full or the card is not this site's. */
    public boolean returnCard(ItemStack card) {
        if (!forHere(card)) return false;
        for (int i = 0; i < RACK; i++) {
            if (rack.getStackInSlot(i).isEmpty()) {
                rack.setStackInSlot(i, card);
                return true;
            }
        }
        return false;
    }

    /** Cards in the rack now. */
    public int cards() {
        int n = 0;
        for (int i = 0; i < RACK; i++) if (!rack.getStackInSlot(i).isEmpty()) n++;
        return n;
    }

    /** Whether a worker may look at the rack again (it looks at most once a {@code ticks}); marks the look. */
    public boolean mayLook(UUID worker, int ticks) {
        long now = level.getGameTime();
        Long next = nextLook.get(worker);
        if (next != null && now < next) return false;
        nextLook.put(worker, now + ticks);
        return true;
    }

    // --- the drawer -------------------------------------------------------------------------------------------------

    public ItemStackHandler drawer() {
        return drawer;
    }

    /** What automation sees: the Pay Drawer, insert only, from every side. */
    public IItemHandler automation(Direction side) {
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return DRAWER;
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return drawer.getStackInSlot(slot);
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return drawer.insertItem(slot, stack, simulate);
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot) {
                return drawer.getSlotLimit(slot);
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return true;
            }
        };
    }

    // --- the Broth tank (0.7.1) -------------------------------------------------------------------------------------

    public net.neoforged.neoforge.fluids.capability.templates.FluidTank broth() {
        return broth;
    }

    /** What pipes see: broth in from every side, nothing out. */
    public net.neoforged.neoforge.fluids.capability.IFluidHandler brothPipes() {
        return brothPipes;
    }

    /** Takes one wage from the drawer, counted first so a short pile is never half taken. Returns whether it was there. */
    public boolean takeWage(com.coolfreeze23.headinajar.staff.StaffTable.Wage wage) {
        if (wage.none()) return true;
        int have = 0;
        for (int i = 0; i < DRAWER && have < wage.count(); i++) {
            ItemStack s = drawer.getStackInSlot(i);
            if (wage.matches(s)) have += s.getCount();
        }
        if (have < wage.count()) return false;
        int left = wage.count();
        for (int i = 0; i < DRAWER && left > 0; i++) {
            if (wage.matches(drawer.getStackInSlot(i))) left -= drawer.extractItem(i, left, false).getCount();
        }
        return true;
    }

    // --- the punch --------------------------------------------------------------------------------------------------

    /** Where a worker stands with its punch. */
    public enum Punch { NONE, QUEUED, PUNCHING, DONE }

    public Punch punch(UUID worker, long stamp) {
        Long p = punched.get(worker);
        if (p != null && p == stamp) return Punch.DONE;
        if (punching != null && punching.id.equals(worker)) return Punch.PUNCHING;
        for (Queued q : queue) if (q.id.equals(worker)) return Punch.QUEUED;
        return Punch.NONE;
    }

    /** Joins the queue at the punch for the shift {@code stamp} ({@code late}: the bell rings for it). */
    public void queuePunch(UUID worker, long stamp, boolean late) {
        if (punch(worker, stamp) != Punch.NONE) return;
        queue.removeIf(q -> q.id.equals(worker));
        queue.add(new Queued(worker, stamp, late));
    }

    /** A worker's place in the queue: 0 next, 1 behind it...; -1 not in it (or punching now). */
    public int queuePlace(UUID worker) {
        for (int i = 0; i < queue.size(); i++) if (queue.get(i).id.equals(worker)) return i;
        return -1;
    }

    /** Whether it has punched in for the shift {@code stamp}. */
    public boolean punchedFor(UUID worker, long stamp) {
        Long p = punched.get(worker);
        return p != null && p == stamp;
    }

    /** Forgets a worker's punch (it punches in again next time). */
    public void unpunch(UUID worker) {
        if (punched.remove(worker) != null) setChanged();
    }

    /** When its shift was first seen to be over while it was on the job (the grace to finish a trip), or now. */
    public long overSince(UUID worker) {
        return overSince.computeIfAbsent(worker, k -> level.getGameTime());
    }

    public void stillOn(UUID worker) {
        overSince.remove(worker);
    }

    private void punchTick(ServerLevel level, long now) {
        Vec3 at = Vec3.atCenterOf(worldPosition);
        if (bellAt >= 0 && now >= bellAt) {
            bellAt = -1;
            level.playSound(null, worldPosition, WorkSounds.CLOCK_BELL.get(), SoundSource.BLOCKS, 1, 1);
        }
        if (punching != null) {
            long t = now - punchStart;
            Entity e = level.getEntity(punching.id);
            if (!(e instanceof Mob mob) || !mob.isAlive() || horizontal(mob.position(), at) > PUNCH_REACH + 1) {
                punching = null;
                return;
            }
            mob.getLookControl().setLookAt(at.x, at.y, at.z, 30, 30);
            if (t >= PUNCH_TICKS) {
                Queued done = punching;
                punching = null;
                if (done.late) {
                    kit.event(EV_DING, 0);
                    bellAt = now + BELL_AT;
                }
                punched.put(done.id, done.stamp);
                setChanged();
                Shifts.punchedIn(this, mob);
            }
            return;
        }
        while (!queue.isEmpty()) {
            Queued q = queue.get(0);
            Entity e = level.getEntity(q.id);
            if (!(e instanceof Mob mob) || !mob.isAlive() || !has(q.id)) {
                queue.remove(0);
                headSince = -1;
                continue;
            }
            // it punches at the slot: on its spot in front of the clock; one that cannot get there punches from within
            // reach once it has had its time to step up
            BlockPos spot = com.coolfreeze23.headinajar.staff.work.Shifts.punchSpot(this);
            boolean atSlot = horizontal(mob.position(), Vec3.atBottomCenterOf(spot)) <= AT_SLOT && Math.abs(mob.getY() - spot.getY()) < 1.5;
            boolean inReach = horizontal(mob.position(), at) <= PUNCH_REACH && Math.abs(mob.getY() - worldPosition.getY()) < 2.5;
            if (atSlot || inReach && headSince >= 0 && now - headSince > STEP_UP_TICKS) {
                queue.remove(0);
                headSince = -1;
                punching = q;
                punchStart = now;
                shownPunch = now;
                shownLate = q.late;
                kit.event(EV_PUNCH, q.late ? 1 : 0);
                level.playSound(null, worldPosition, WorkSounds.PUNCH_IN.get(), SoundSource.BLOCKS, 1, 1);
                return;
            }
            // still stepping up: wait for it a while, then send it to the back
            if (headSince < 0) headSince = now;
            if (now - headSince > STEP_UP_TICKS && queue.size() > 1) {
                queue.add(queue.remove(0));
                headSince = -1;
            }
            return;
        }
    }

    static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    // --- redstone ---------------------------------------------------------------------------------------------------

    public boolean powered() {
        return powered;
    }

    /** Game time the power last came on or went off. */
    public long poweredAt() {
        return poweredAt;
    }

    public void checkPower() {
        if (level == null || level.isClientSide) return;
        boolean p = level.hasNeighborSignal(worldPosition);
        if (p != powered) {
            powered = p;
            poweredAt = level.getGameTime();
            setChanged();
        }
    }

    // --- the shuttle home -------------------------------------------------------------------------------------------

    /** A worker's whole record, to be set down here the next time this clock ticks (the company shuttle home). */
    public void arrive(CompoundTag entity) {
        arrivals.add(entity);
        setChanged();
    }

    public List<CompoundTag> arrivals() {
        return arrivals;
    }

    /** Sets down the arrivals beside the clock: never one whose UUID is already in the world. */
    protected void setDownArrivals(ServerLevel level) {
        if (arrivals.isEmpty()) return;
        setDown(level, Vec3.atBottomCenterOf(front()), WorkerData.State.TO_CLOCK);
    }

    private void setDown(ServerLevel level, Vec3 at, WorkerData.State state) {
        List<CompoundTag> now = new ArrayList<>(arrivals);
        arrivals.clear();
        setChanged();
        for (CompoundTag tag : now) {
            if (tag.hasUUID("UUID") && level.getEntity(tag.getUUID("UUID")) != null) continue;
            Entity e = EntityType.loadEntityRecursive(tag, level, x -> {
                x.moveTo(at.x, at.y, at.z, x.getYRot(), x.getXRot());
                return x;
            });
            if (e == null || !level.addFreshEntity(e)) {
                // not set down: kept for the next tick, unless it is already back
                if (e != null && level.getEntity(e.getUUID()) == null) arrivals.add(tag);
                continue;
            }
            WorkerData d = Workforce.data(e);
            if (d != null) {
                d.state = state;
                d.detail = null;
                d.dropTask();
                if (state == WorkerData.State.OFF_SITE) d.clock = null;
            }
            level.playSound(null, at.x, at.y, at.z, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.6f, 1.2f);
        }
    }

    // --- the tick ---------------------------------------------------------------------------------------------------

    public void serverTick() {
        if (!(level instanceof ServerLevel server)) return;
        long now = server.getGameTime();
        setDownArrivals(server);
        long day = Staff.day(server);
        if (day > lastDawn) {
            lastDawn = day;
            setChanged();
            if (Workforce.enabled()) {
                for (UUID id : List.copyOf(roster)) {
                    if (server.getEntity(id) instanceof Mob mob && Workforce.isWorker(mob)) Shifts.dawn(this, mob);
                }
            }
        }
        punchTick(server, now);
        long phase = Math.floorMod(now + worldPosition.asLong(), (long) TIDY_TICKS);
        if (phase == 0) tidy(server);
        if (phase % LAMP_TICKS == 0) lamp = lampNow(server);
        kit.level(TimeClockBlock.CARDS, cards());
        kit.tick(lamp, false, false, 0);
    }

    /** What the lamp says: nobody on the roster OFF; anyone held up WAIT; anyone on the job WORK; else READY. */
    private MachineLamp lampNow(ServerLevel server) {
        if (roster.isEmpty() || !Workforce.enabled()) return MachineLamp.OFF;
        boolean work = false;
        for (UUID id : roster) {
            WorkerData d = server.getEntity(id) instanceof Mob mob ? Workforce.data(mob) : null;
            if (d == null || d.clock == null || !d.clock.pos().equals(worldPosition)) continue;
            if (d.heldUp) return MachineLamp.WAIT;
            if (d.state == WorkerData.State.ON_JOB) work = true;
        }
        return work ? MachineLamp.WORK : MachineLamp.READY;
    }

    /** Drops roster entries that are no longer this clock's: let go, dead, or on another clock's roster. */
    private void tidy(ServerLevel server) {
        WorkerIndex index = WorkerIndex.get(server.getServer());
        boolean changed = roster.removeIf(id -> {
            Entity e = server.getEntity(id);
            if (e != null) {
                WorkerData d = Workforce.data(e);
                boolean stale = d == null || d.state == WorkerData.State.NOTICE || d.clock == null || !d.clock.equals(site());
                if (stale) forget(id);
                return stale;
            }
            boolean onFile = false;
            for (var map : index.all().values()) {
                if (map.containsKey(id)) {
                    onFile = true;
                    break;
                }
            }
            if (!onFile) forget(id);
            return !onFile;
        });
        if (changed) setChanged();
    }

    private void forget(UUID id) {
        takers.remove(id);
        punched.remove(id);
        overSince.remove(id);
        nextLook.remove(id);
    }

    public void clientTick() {
    }

    // --- use and break ----------------------------------------------------------------------------------------------

    /** Use on the clock: its screen, or the refusal (staffing off; someone the owner does not trust). */
    public void use(ServerPlayer player) {
        if (!Workforce.enabled()) {
            Refusals.deny(player, this, "clock.disabled");
            return;
        }
        if (!trusts(player)) {
            Refusals.deny(player, this, "clock.untrusted", ownerName);
            return;
        }
        player.openMenu(this, buf -> buf.writeBlockPos(worldPosition));
    }

    /**
     * The clock is gone (broken, blown up, replaced): the drawer and the rack fall out, arrivals are set down where it
     * stood, its staff are off site with no clock (they join a clock within 16 by themselves), and the site is struck
     * from the register.
     */
    public void broken(ServerLevel server) {
        Vec3 at = Vec3.atCenterOf(worldPosition);
        for (ItemStackHandler h : new ItemStackHandler[]{rack, drawer}) {
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                if (!s.isEmpty()) Containers.dropItemStack(server, at.x, at.y, at.z, s.copy());
                h.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        if (!arrivals.isEmpty()) setDown(server, Vec3.atBottomCenterOf(worldPosition), WorkerData.State.OFF_SITE);
        for (UUID id : List.copyOf(roster)) {
            if (!(server.getEntity(id) instanceof Mob mob)) continue;
            WorkerData d = Workforce.data(mob);
            if (d == null || d.clock == null || !d.clock.equals(site())) continue;
            Shifts.siteClosed(mob, d);
        }
        roster.clear();
        takers.clear();
        queue.clear();
        punching = null;
        SiteIndex.get(server).remove(worldPosition);
    }

    // --- the screen -------------------------------------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.headinajar.time_clock");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new TimeClockMenu(id, inventory, this);
    }

    // --- the kit ----------------------------------------------------------------------------------------------------

    @Override
    public MachineKit kit() {
        return kit;
    }

    @Override
    public void writeSync(CompoundTag tag) {
        int n = cards();
        if (n > 0) tag.putByte("cards", (byte) n);
        // the last punch, while its clip (and a late one's ding) can still be playing
        if (level != null && level.getGameTime() - shownPunch <= PUNCH_TICKS + DING_TICKS) {
            tag.putLong("punch", shownPunch);
            if (shownLate) tag.putBoolean("late", true);
        }
    }

    @Override
    public void readSync(CompoundTag tag) {
        cardsShown = tag.getByte("cards");
        if (tag.contains("punch")) {
            shownPunch = tag.getLong("punch");
            shownLate = tag.getBoolean("late");
        }
    }

    /** Client: cards the rack shows. */
    public int cardsShown() {
        return cardsShown;
    }

    /** Both sides: the game time the last punch started (its clip's tick 0), and whether it was late (a ding follows). */
    public long punchStarted() {
        return shownPunch;
    }

    public boolean punchLate() {
        return shownLate;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return kit.syncTag();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        kit.read(tag);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        kit.read(packet.getTag());
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        return kit.triggerEvent(id, param);
    }

    // --- the moving parts (GeckoLib: drawn by TimeClockRenderer) ----------------------------------------------------

    private static final RawAnimation DIAL = RawAnimation.begin().thenLoop("dial"), MINUTE = RawAnimation.begin().thenLoop("minute"),
            IDLE = RawAnimation.begin().thenLoop("idle"), PUNCH = RawAnimation.begin().thenPlay("punch"), DING = RawAnimation.begin().thenPlay("ding");
    /** Clip lengths in ticks: the dial goes round once a day, the minute hand once an in-game hour. */
    private static final int DAY = 24000, HOUR = 1000, IDLE_TICKS = 40;
    private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache cache = software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

    /**
     * Three controllers, each a function of synced state and the time, so a late joiner sees what everyone sees: the hour
     * hand to the Overworld's time of day (the day's 24 hours on the dial, noon up), the minute hand to the hour (it
     * jumps once a minute), and the punch: {@code punch} from the synced start, a late worker's {@code ding} straight
     * after, else {@code idle}.
     */
    @Override
    public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new SeekingControllerV2<TimeClockBlockEntity>(this, "dial", (state, c) -> {
            if (level == null) return c.none();
            return c.show("dial", DIAL, Math.floorMod(level.getDayTime(), (long) DAY) + state.getPartialTick());
        }));
        controllers.add(new SeekingControllerV2<TimeClockBlockEntity>(this, "minute", (state, c) -> {
            if (level == null) return c.none();
            return c.show("minute", MINUTE, Math.floorMod(level.getDayTime(), (long) HOUR) + state.getPartialTick());
        }));
        controllers.add(new SeekingControllerV2<TimeClockBlockEntity>(this, "main", (state, c) -> {
            if (level == null) return c.none();
            double now = level.getGameTime() + state.getPartialTick();
            double t = now - shownPunch;
            if (t >= 0 && t < PUNCH_TICKS) return c.show("punch", PUNCH, t);
            if (shownLate && t >= PUNCH_TICKS && t < PUNCH_TICKS + DING_TICKS) return c.show("ding", DING, t - PUNCH_TICKS);
            return c.show("idle", IDLE, now % IDLE_TICKS);
        }));
    }

    @Override
    public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // --- saving -----------------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName);
        ListTag list = new ListTag();
        for (UUID id : roster) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", id);
            Long p = punched.get(id);
            if (p != null) t.putLong("Punched", p);
            list.add(t);
        }
        tag.put("Roster", list);
        tag.put("Rack", rack.serializeNBT(registries));
        tag.put("Drawer", drawer.serializeNBT(registries));
        ListTag arr = new ListTag();
        arr.addAll(arrivals);
        tag.put("Arrivals", arr);
        tag.putLong("LastDawn", lastDawn);
        if (powered) tag.putBoolean("Powered", true);
        tag.putLong("PoweredAt", poweredAt);
        if (!broth.isEmpty()) tag.put("Broth", broth.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        roster.clear();
        punched.clear();
        for (Tag t : tag.getList("Roster", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            if (!c.hasUUID("Id")) continue;
            roster.add(c.getUUID("Id"));
            if (c.contains("Punched")) punched.put(c.getUUID("Id"), c.getLong("Punched"));
        }
        if (tag.contains("Rack")) rack.deserializeNBT(registries, tag.getCompound("Rack"));
        if (tag.contains("Drawer")) drawer.deserializeNBT(registries, tag.getCompound("Drawer"));
        arrivals.clear();
        for (Tag t : tag.getList("Arrivals", Tag.TAG_COMPOUND)) arrivals.add((CompoundTag) t);
        lastDawn = tag.contains("LastDawn") ? tag.getLong("LastDawn") : -1;
        powered = tag.getBoolean("Powered");
        poweredAt = tag.getLong("PoweredAt");
        broth.setFluid(net.neoforged.neoforge.fluids.FluidStack.EMPTY);
        if (tag.contains("Broth")) broth.readFromNBT(registries, tag.getCompound("Broth"));
    }
}

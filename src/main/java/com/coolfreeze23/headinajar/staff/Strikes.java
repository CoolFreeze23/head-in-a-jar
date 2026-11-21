package com.coolfreeze23.headinajar.staff;

import com.coolfreeze23.headinajar.Advance;
import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.block.Operations;
import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.clarity.Memos;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.fx.Fx;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.second.Secondees;
import com.coolfreeze23.headinajar.staff.work.Picket;
import com.coolfreeze23.headinajar.staff.work.SiteIndex;
import com.coolfreeze23.headinajar.staff.work.WorkSounds;
import com.coolfreeze23.headinajar.staff.work.WorkTable;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.WorkerLooks;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Chapter 15's Strike (0.7.2). At dawn a site walks out when half or more of its staff that can strike (never Interns,
 * wolves or Nitwits; two at least) are under 40 morale, or two of them have gone unpaid two dawns running; never in a
 * world's first week, never within three days of the last strike, and only while {@code staff.strikes} and
 * {@code staff.morale} allow it. The strikers stand in a ring round the clock with their brains off but for the chant;
 * the Union Rep has the demands; back pay in the Pay Drawer and a cake on the Rep end it, three loaded dawns of waiting
 * collapse it. The Board's "unionise", honestly carried within 48 blocks of a site that meets the test, starts one at
 * once. Nothing is broken or hurt: the strikers walk by the site's own paths and then stand.
 */
public final class Strikes {
    private Strikes() {
    }

    /**
     * A clock's strike, saved on its block entity ({@code Strike}): the day it started (-1: none), the loaded dawns it has
     * lasted, the Rep, who stands in the ring (in slot order, a Nitwit included), the dawns owed to each striker that is
     * counted (capped at 7), and the day until which no new strike starts here. A clock saved before 0.7.2 has none.
     */
    public static final class Record {
        long since = -1;
        int dawns;
        UUID rep;
        final List<UUID> ring = new ArrayList<>();
        final Map<UUID, Integer> owed = new LinkedHashMap<>();
        long cooldownUntil = -1;
        /** Tests: the day the strike rules read (the first week, the cool-down) in place of the world's, or -1. */
        public long dayOverride = -1;
        /** Not saved: the game time of the chant now going round (-1 none). */
        long chantAt = -1;
        /** Not saved: the ring's slots, worked out once a strike (and again after a load). */
        List<BlockPos> slots;
        /**
         * Not saved: the strikers walking out to the ring since this strike started in this session. After a load the set
         * is empty, and a striker found standing in the ring stays there (its slot is not walked to again).
         */
        final java.util.Set<UUID> walking = new java.util.HashSet<>();

        public boolean active() {
            return since >= 0;
        }

        public long since() {
            return since;
        }

        public int dawns() {
            return dawns;
        }

        public UUID rep() {
            return rep;
        }

        public List<UUID> ring() {
            return ring;
        }

        /** Dawns owed to a striker (0 for none, a Nitwit, or nobody on strike). */
        public int owed(UUID striker) {
            return owed.getOrDefault(striker, 0);
        }

        public long cooldownUntil() {
            return cooldownUntil;
        }

        void clear() {
            since = -1;
            dawns = 0;
            rep = null;
            ring.clear();
            owed.clear();
            chantAt = -1;
            slots = null;
            walking.clear();
        }

        public void save(CompoundTag tag) {
            if (!active() && cooldownUntil < 0) return;
            CompoundTag t = new CompoundTag();
            t.putLong("Since", since);
            t.putInt("Dawns", dawns);
            if (rep != null) t.putUUID("Rep", rep);
            ListTag list = new ListTag();
            for (UUID id : ring) {
                CompoundTag s = new CompoundTag();
                s.putUUID("Id", id);
                if (owed.containsKey(id)) s.putInt("Owed", owed.get(id));
                list.add(s);
            }
            t.put("Ring", list);
            t.putLong("CooldownUntil", cooldownUntil);
            tag.put("Strike", t);
        }

        public void load(CompoundTag tag) {
            clear();
            cooldownUntil = -1;
            if (!tag.contains("Strike", Tag.TAG_COMPOUND)) return;
            CompoundTag t = tag.getCompound("Strike");
            since = t.getLong("Since");
            dawns = t.getInt("Dawns");
            rep = t.hasUUID("Rep") ? t.getUUID("Rep") : null;
            for (Tag e : t.getList("Ring", Tag.TAG_COMPOUND)) {
                CompoundTag s = (CompoundTag) e;
                if (!s.hasUUID("Id")) continue;
                UUID id = s.getUUID("Id");
                ring.add(id);
                if (s.contains("Owed")) owed.put(id, Math.max(0, Math.min(BACK_PAY_DAWNS, s.getInt("Owed"))));
            }
            cooldownUntil = t.contains("CooldownUntil") ? t.getLong("CooldownUntil") : -1;
        }
    }

    /** The test's numbers: morale under this, unpaid this many dawns running. */
    public static final int LOW_MORALE = 40, UNPAID_RUN = 2, AT_LEAST = 2;
    /** Back pay is owed for at most this many dawns per striker. */
    public static final int BACK_PAY_DAWNS = 7;
    /** A strike collapses after this many loaded dawns; no new one for this many days after one ends; none before day 7. */
    public static final int COLLAPSE_DAWNS = 3, COOLDOWN_DAYS = 3, FIRST_WEEK = 7;
    /** Morale on agreement (Content) and on collapse. */
    public static final int AGREED_MORALE = 50, COLLAPSED_MORALE = 20;
    /** A chant every this many ticks while loaded; strikers' beats this far apart, each one's two beats this far apart. */
    public static final int CHANT_TICKS = 400, STAGGER = 3, BEAT = 6;
    /** Who hears the strike's chat line; how far the Board's motion reaches; a chair this near holds the chant. */
    public static final double TELL = 32, BOARD_REACH = 48, CHAIR_QUIET = 32;

    /** Tests: what happened, oldest first ("<clock> <what>"). */
    public static final List<String> HISTORY = new ArrayList<>();

    private static void note(TimeClockBlockEntity c, String what) {
        HISTORY.add(c.getBlockPos().toShortString() + " " + what);
        if (HISTORY.size() > 256) HISTORY.remove(0);
    }

    /** One line in {@code MomentsEvents}: the hooks in {@link SiteState} and the Rep's Use. */
    public static void register() {
        SiteState.striking = c -> c != null && c.strike.active();
        SiteState.striker = m -> clockOf(m) != null;
        SiteState.rep = m -> {
            TimeClockBlockEntity c = clockOf(m);
            return c != null && m.getUUID().equals(c.strike.rep);
        };
        SiteState.demands = Strikes::roster;
        SiteState.AFTER_DAWN.add(Strikes::afterDawn);
        SiteState.HOLDS.add(Strikes::hold);
        Promotion.striking = (level, pos) -> level.getBlockEntity(pos) instanceof TimeClockBlockEntity c && c.strike.active();
        NeoForge.EVENT_BUS.addListener(Strikes::interact);
    }

    // --- who --------------------------------------------------------------------------------------------------------

    public static boolean enabled() {
        return Config.get(Config.STRIKES, true) && Config.get(Config.MORALE, true);
    }

    /** The day the strike rules read: the world's day, or a test's. */
    public static long day(TimeClockBlockEntity c) {
        return c.strike.dayOverride >= 0 ? c.strike.dayOverride : Staff.day(c.getLevel());
    }

    /** The species a worker is as far as the strike goes: a seated secondee's head's. */
    private static EntityType<?> species(Mob mob) {
        return Secondees.payType(mob);
    }

    private static boolean wolf(Mob mob) {
        return species(mob) == EntityType.WOLF;
    }

    private static boolean nitwit(Mob mob) {
        WorkTable.Spec spec = WorkTable.of(mob);
        return spec != null && spec.quirk("nitwit");
    }

    /** Whether it stands in a ring: anyone but an Intern or a wolf (a Nitwit joins it). */
    public static boolean walksOut(Mob mob) {
        StaffFile f = Staff.of(mob);
        return f != null && Grades.of(f) > Grades.INTERN && !wolf(mob);
    }

    /** Whether it counts for the test, is owed back pay and may be the Rep: it walks out and is not a Nitwit. */
    public static boolean counts(Mob mob) {
        return walksOut(mob) && !nitwit(mob);
    }

    /** The clock's staff here now: loaded, alive, on its roster and its site, on the payroll, not let go. */
    private static List<Mob> members(TimeClockBlockEntity c) {
        List<Mob> out = new ArrayList<>();
        if (!(c.getLevel() instanceof ServerLevel level)) return out;
        for (UUID id : c.roster()) {
            if (!(level.getEntity(id) instanceof Mob mob) || !mob.isAlive()) continue;
            WorkerData d = Workforce.data(mob);
            StaffFile f = Staff.of(mob);
            if (d == null || f == null || !f.staff() || d.state == WorkerData.State.NOTICE) continue;
            if (d.clock == null || !d.clock.equals(c.site())) continue;
            out.add(mob);
        }
        return out;
    }

    /** Whether the site meets the strike test now, from its staff's stored morale and unpaid dawns (no scan). */
    public static boolean meetsTest(TimeClockBlockEntity c) {
        int counted = 0, low = 0, unpaid = 0;
        for (Mob mob : members(c)) {
            if (!counts(mob)) continue;
            StaffFile f = Staff.of(mob);
            counted++;
            if (f.morale < LOW_MORALE) low++;
            if (f.career.unpaidRun >= UNPAID_RUN) unpaid++;
        }
        if (counted < AT_LEAST) return false;
        return low >= AT_LEAST && 2 * low >= counted || unpaid >= AT_LEAST;
    }

    /** Whether a strike may start at this clock now: the switches, the first week, the cool-down, none running. */
    public static boolean mayStart(TimeClockBlockEntity c) {
        if (!enabled() || !Workforce.enabled() || c.strike.active()) return false;
        long day = day(c);
        return day >= FIRST_WEEK && day >= c.strike.cooldownUntil;
    }

    /** The striking clock a worker stands in the ring of, or null. */
    public static TimeClockBlockEntity clockOf(Mob mob) {
        WorkerData d = Workforce.data(mob);
        if (d == null || d.clock == null || !(mob.level() instanceof ServerLevel level) || !Workforce.clockHere(mob, d)) return null;
        BlockPos pos = d.clock.pos();
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof TimeClockBlockEntity c) || !c.strike.active()) return null;
        return c.strike.ring.contains(mob.getUUID()) ? c : null;
    }

    /** The striking clock of this worker's site (it may be an Intern, working on), or null. */
    public static TimeClockBlockEntity siteOnStrike(Mob mob) {
        WorkerData d = Workforce.data(mob);
        if (d == null || d.clock == null || !(mob.level() instanceof ServerLevel level) || !Workforce.clockHere(mob, d)) return null;
        BlockPos pos = d.clock.pos();
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof TimeClockBlockEntity c && c.strike.active() ? c : null;
    }

    // --- the dawn ---------------------------------------------------------------------------------------------------

    /** After a clock's dawn (every worker on its roster has had theirs): the test, or a running strike's dawn. */
    static void afterDawn(TimeClockBlockEntity c) {
        if (!(c.getLevel() instanceof ServerLevel level)) return;
        Record r = c.strike;
        if (!r.active()) {
            if (mayStart(c) && meetsTest(c)) start(c, "dawn");
            return;
        }
        // a dawn of the strike: each striker that went unpaid at it is owed one more (one paid at it is not)
        for (Mob mob : members(c)) {
            if (!r.owed.containsKey(mob.getUUID())) continue;
            StaffFile f = Staff.of(mob);
            if (f.career.unpaidRun > 0 && !Grades.wage(mob).none()) r.owed.merge(mob.getUUID(), 1, (a, b) -> Math.min(BACK_PAY_DAWNS, a + b));
        }
        r.dawns++;
        c.setChanged();
        note(c, "dawn " + r.dawns);
        boolean anyone = false;
        for (UUID id : r.ring) anyone |= c.has(id);
        if (r.dawns >= COLLAPSE_DAWNS || !anyone) collapse(c, level);
    }

    /**
     * The walk-out: everyone who strikes leaves its work and goes to its slot in the ring; the Rep is the most senior
     * (grade, then tasks; never a Nitwit); each is owed its unpaid dawns so far. The memo (the first strike), the chat
     * line to the employer and everyone within 32, Industrial Action, and any ceremony here is called off. False when
     * fewer than two would count.
     */
    public static boolean start(TimeClockBlockEntity c, String why) {
        if (!(c.getLevel() instanceof ServerLevel level)) return false;
        List<Mob> ring = new ArrayList<>();
        Mob rep = null;
        int counted = 0;
        for (Mob mob : members(c)) {
            if (!walksOut(mob)) continue;
            ring.add(mob);
            if (!counts(mob)) continue;
            counted++;
            if (rep == null || senior(mob, rep)) rep = mob;
        }
        if (counted < AT_LEAST) return false;
        Picket.roundTheClock(ring, c);
        Record r = c.strike;
        r.clear();
        long day = Staff.day(level);
        r.since = day(c);
        r.rep = rep.getUUID();
        for (Mob mob : ring) {
            r.ring.add(mob.getUUID());
            r.walking.add(mob.getUUID());
            StaffFile f = Staff.of(mob);
            if (counts(mob)) r.owed.put(mob.getUUID(), Grades.wage(mob).none() ? 0 : Math.min(BACK_PAY_DAWNS, f.career.unpaidRun));
            f.note("strike", day, "");
            mob.setData(ModRegistry.STAFF, f);
            com.coolfreeze23.headinajar.staff.work.Quirks.offDuty(mob);
            Picket.walkOut(mob, Workforce.data(mob), c);
        }
        for (Mob mob : ring) WorkerLooks.changed(mob);
        c.setChanged();
        synced(c, level);
        note(c, "start " + why + " " + ring.size());
        BlockPos at = c.getBlockPos();
        Component line = Component.translatable("chat.headinajar.strike", at.getX(), at.getY(), at.getZ());
        ServerPlayer employer = c.owner() == null ? null : level.getServer().getPlayerList().getPlayer(c.owner());
        tell(level, at, employer, line);
        if (employer != null) {
            Memos.send(employer, Memos.Memo.STRIKE, at.getX(), at.getY(), at.getZ());
            Advance.grant(employer, "industrial_action");
        }
        Promotion.strikeStarted(level, at);
        return true;
    }

    /** Whether {@code a} outranks {@code b} for Rep: a higher grade, then more tasks. */
    private static boolean senior(Mob a, Mob b) {
        StaffFile fa = Staff.of(a), fb = Staff.of(b);
        int ga = Grades.of(fa), gb = Grades.of(fb);
        return ga != gb ? ga > gb : fa.tasks > fb.tasks;
    }

    /** A line to the employer (wherever they are in the level's server) and to everyone within 32 of the clock, once each. */
    private static void tell(ServerLevel level, BlockPos at, ServerPlayer employer, Component line) {
        List<ServerPlayer> told = new ArrayList<>();
        if (employer != null) {
            employer.sendSystemMessage(line);
            told.add(employer);
        }
        for (ServerPlayer p : level.getPlayers(p -> p.distanceToSqr(Vec3.atCenterOf(at)) <= TELL * TELL)) {
            if (!told.contains(p)) p.sendSystemMessage(line);
        }
    }

    /** The clock's look changes: the card and the pulse (the lamp follows at its next look, within a second). */
    private static void synced(TimeClockBlockEntity c, ServerLevel level) {
        level.sendBlockUpdated(c.getBlockPos(), c.getBlockState(), c.getBlockState(), 3);
    }

    /** Three loaded dawns and nobody paid: everyone back to work at 20. */
    static void collapse(TimeClockBlockEntity c, ServerLevel level) {
        end(c, level, COLLAPSED_MORALE, false);
        note(c, "collapsed");
        BlockPos at = c.getBlockPos();
        ServerPlayer employer = c.owner() == null ? null : level.getServer().getPlayerList().getPlayer(c.owner());
        tell(level, at, employer, Component.translatable("chat.headinajar.strike_collapsed"));
    }

    /** The end: the ring back to work at {@code morale}; the cool-down starts. */
    private static void end(TimeClockBlockEntity c, ServerLevel level, int morale, boolean agreed) {
        Record r = c.strike;
        long day = Staff.day(level);
        List<Mob> back = new ArrayList<>();
        for (UUID id : List.copyOf(r.ring)) {
            if (!(level.getEntity(id) instanceof Mob mob) || !mob.isAlive()) continue;
            back.add(mob);
            StaffFile f = Staff.of(mob);
            if (f != null && f.staff()) {
                f.morale = morale;
                if (f.morale >= Band.DISENGAGED_AT) f.lowDawns = 0;
                if (agreed && r.owed.containsKey(id)) {
                    // the back pay settles every dawn it went unpaid, today's with them
                    f.career.unpaidRun = 0;
                    f.paidThrough = Math.max(f.paidThrough, day);
                }
                mob.setData(ModRegistry.STAFF, f);
            }
            STATE.remove(mob);
            SET_OFF.remove(mob);
            WorkerData d = Workforce.data(mob);
            if (d != null) Picket.backToWork(mob, d);
        }
        r.cooldownUntil = day(c) + COOLDOWN_DAYS;
        r.clear();
        // they are off the ring now: their looks lose the sign and the rosette (told after the record is cleared: the
        // look sent on the way back to work above still read them as striking)
        for (Mob mob : back) WorkerLooks.changed(mob);
        c.setChanged();
        synced(c, level);
    }

    // --- the ring ---------------------------------------------------------------------------------------------------

    /** Per striker: whether it has reached its slot (not saved: after a load it walks there again from where it stands). */
    private static final Map<Mob, Boolean> STATE = new WeakHashMap<>();
    /** Per striker on its way: the game time it set off (one that has not got there in {@link #WALK_TICKS} stands where it is). */
    private static final Map<Mob, Long> SET_OFF = new WeakHashMap<>();
    public static final int WALK_TICKS = 600;

    /** The strike's hold on a striker's brain: walk to the slot, then stand facing the clock, nothing else. */
    static boolean hold(Mob mob, WorkerData d) {
        TimeClockBlockEntity c = clockOf(mob);
        if (c == null) {
            STATE.remove(mob);
            SET_OFF.remove(mob);
            return false;
        }
        Record r = c.strike;
        // the status is not saved with the worker: after a load it reads "On strike" again
        String striking = "staff.headinajar.status.on_strike";
        if (!striking.equals(d.status)) d.status = striking;
        if (r.slots == null) r.slots = Picket.ring(c, r.ring.size());
        int i = r.ring.indexOf(mob.getUUID());
        BlockPos slot = i >= 0 && i < r.slots.size() ? r.slots.get(i) : null;
        if (Boolean.TRUE.equals(STATE.get(mob))) {
            Picket.stand(mob);
            if ((mob.tickCount + mob.getId()) % 40 == 0) Picket.face(mob, c);
            return true;
        }
        // after a load (the standing flags are not saved): a striker already standing in the ring stays where it is,
        // so the ring stands and chants again at once instead of walking out to its slots a second time
        if (!r.walking.contains(mob.getUUID()) && inRing(mob, c)) {
            STATE.put(mob, true);
            Picket.stand(mob);
            Picket.face(mob, c);
            return true;
        }
        // no slot left on the ground for it, or no way there: it stands where it stopped
        long now = mob.level().getGameTime();
        long off = SET_OFF.computeIfAbsent(mob, m -> now);
        boolean there = slot == null || Picket.toSlot(mob, d, slot, c.box()) || now - off > WALK_TICKS;
        if (there) {
            SET_OFF.remove(mob);
            STATE.put(mob, true);
            Picket.stand(mob);
            Picket.face(mob, c);
        }
        return true;
    }

    /** Whether a striker stands within the ring's widest radius of its clock (a block's grace), on the clock's level. */
    static boolean inRing(Mob mob, TimeClockBlockEntity c) {
        double r = Picket.RADII[Picket.RADII.length - 1] + 1.0;
        double dx = mob.getX() - (c.getBlockPos().getX() + 0.5), dz = mob.getZ() - (c.getBlockPos().getZ() + 0.5);
        return dx * dx + dz * dz <= r * r && Math.abs(mob.getY() - c.getBlockPos().getY()) <= 3;
    }

    /** Tests: a striker's slot in the ring (null: none, or not worked out yet). */
    public static BlockPos slotOf(Mob mob) {
        TimeClockBlockEntity c = clockOf(mob);
        if (c == null || c.strike.slots == null) return null;
        int i = c.strike.ring.indexOf(mob.getUUID());
        return i >= 0 && i < c.strike.slots.size() ? c.strike.slots.get(i) : null;
    }

    /** Whether a striker stands in the ring (it chants). */
    public static boolean standing(Mob mob) {
        return Boolean.TRUE.equals(STATE.get(mob));
    }

    // --- the chant --------------------------------------------------------------------------------------------------

    /** Each loaded tick of a clock: the chant (nothing while there is no strike). */
    public static void tick(TimeClockBlockEntity c, ServerLevel level, long now) {
        Record r = c.strike;
        if (!r.active()) return;
        if (Math.floorMod(now + c.getBlockPos().asLong(), (long) CHANT_TICKS) == 0) {
            // chants wait while a chair operates within 32 (house rule 1)
            if (Operations.near(level, c.getBlockPos(), CHAIR_QUIET)) note(c, "chant held");
            else {
                r.chantAt = now;
                chanted(c, level);
            }
        }
        if (r.chantAt < 0) return;
        long k = now - r.chantAt;
        if (k > (long) STAGGER * r.ring.size() + BEAT) {
            r.chantAt = -1;
            return;
        }
        // each striker's two beats, staggered by 3 ticks: its own voice (a secondee its head's), and its pump on the first
        for (int i = 0; i < r.ring.size(); i++) {
            long first = (long) STAGGER * i;
            if (k != first && k != first + BEAT) continue;
            if (!(level.getEntity(r.ring.get(i)) instanceof Mob mob) || !mob.isAlive() || !standing(mob)) continue;
            SoundEvent voice = voice(level, mob);
            if (voice != null) level.playSound(null, mob.getX(), mob.getEyeY(), mob.getZ(), voice, mob.getSoundSource(), 0.6f, 0.95f + 0.1f * level.random.nextFloat());
            if (k == first) Fx.send(level, Fx.of(Fx.CHANT, mob.getEyePosition(), Vec3.atCenterOf(c.getBlockPos()), mob.getId(), -1, 2 * BEAT));
        }
    }

    /** The round starts: the crowd's murmur at the clock and one striker's line over its head. */
    private static void chanted(TimeClockBlockEntity c, ServerLevel level) {
        List<Mob> standing = new ArrayList<>();
        for (UUID id : c.strike.ring) if (level.getEntity(id) instanceof Mob mob && mob.isAlive() && standing(mob)) standing.add(mob);
        note(c, "chant " + standing.size());
        if (standing.isEmpty()) return;
        level.playSound(null, c.getBlockPos(), WorkSounds.STAFF_CHANT.get(), SoundSource.NEUTRAL, 1, 1);
        Mob who = standing.get(level.random.nextInt(standing.size()));
        Workforce.sayNow(who, Component.translatable("staff.headinajar.say.chant." + chantLine(who, level.random.nextInt(4))));
    }

    /** Which chant line a striker floats: the cows moo, the zombie family groans for brains, everyone says the rest. */
    static int chantLine(Mob mob, int roll) {
        EntityType<?> t = species(mob);
        if ((t == EntityType.COW || t == EntityType.MOOSHROOM) && roll == 0) return 3;
        if ((t == EntityType.ZOMBIE || t == EntityType.HUSK || t == EntityType.DROWNED || t == EntityType.ZOMBIE_VILLAGER
                || t == EntityType.ZOMBIFIED_PIGLIN) && roll == 0) return 4;
        return roll % 3;
    }

    /** One stand-in per species, never added to the world: a seated head's own voice. */
    private static final Map<EntityType<?>, Mob> STAND_INS = new HashMap<>();

    private static SoundEvent voice(ServerLevel level, Mob mob) {
        Mob speaker = mob;
        if (Secondees.seated(mob)) {
            EntityType<?> head = Secondees.headType(mob);
            if (head != null && head != mob.getType()) {
                speaker = STAND_INS.computeIfAbsent(head, t -> t.create(level) instanceof Mob m ? m : null);
                if (speaker == null) return null;
            }
        }
        return ((com.coolfreeze23.headinajar.mixin.MobAmbientInvoker) speaker).headinajar$ambient();
    }

    // --- the Rep ----------------------------------------------------------------------------------------------------

    /** One wage owed in all: the item (or tag) and how many. */
    public record Owed(StaffTable.Wage wage, int count) {
        public Component line() {
            return Component.literal(count + " ").append(wage.name());
        }
    }

    /** What the strike's back pay comes to, per wage: each counted striker here, its wage for every dawn owed. */
    public static List<Owed> bill(TimeClockBlockEntity c) {
        Map<Object, Owed> out = new LinkedHashMap<>();
        for (Mob mob : members(c)) {
            int dawns = c.strike.owed(mob.getUUID());
            StaffTable.Wage w = Grades.wage(mob);
            if (dawns <= 0 || w.none()) continue;
            Object key = w.item() != null ? w.item() : w.tag();
            Owed was = out.get(key);
            out.put(key, new Owed(was == null ? w : was.wage(), (was == null ? 0 : was.count()) + w.count() * dawns));
        }
        return new ArrayList<>(out.values());
    }

    /** How much of a wage the drawer holds. */
    private static int inDrawer(TimeClockBlockEntity c, StaffTable.Wage w) {
        int n = 0;
        for (int i = 0; i < TimeClockBlockEntity.DRAWER; i++) {
            ItemStack s = c.drawer().getStackInSlot(i);
            if (w.matches(s)) n += s.getCount();
        }
        return n;
    }

    /** The Rep's demands as the action bar says them: at most three wages, else two and "more (on the roster)". */
    public static Component demands(TimeClockBlockEntity c) {
        List<Owed> bill = bill(c);
        String m = "message.headinajar.strike.";
        return switch (bill.size()) {
            case 0 -> Component.translatable(m + "demands_cake");
            case 1 -> Component.translatable(m + "demands_one", bill.get(0).line());
            case 2 -> Component.translatable(m + "demands_two", bill.get(0).line(), bill.get(1).line());
            case 3 -> Component.translatable(m + "demands", bill.get(0).line(), bill.get(1).line(), bill.get(2).line());
            default -> Component.translatable(m + "demands_more", bill.get(0).line(), bill.get(1).line());
        };
    }

    /** The Roster's strike section: one line a striker that is owed (name, dawns, what it comes to). */
    static List<Component> roster(TimeClockBlockEntity c) {
        List<Component> out = new ArrayList<>();
        if (c == null || !c.strike.active()) return out;
        for (Mob mob : members(c)) {
            int dawns = c.strike.owed(mob.getUUID());
            StaffTable.Wage w = Grades.wage(mob);
            if (!c.strike.owed.containsKey(mob.getUUID())) continue;
            Component owes = w.none() || dawns == 0 ? Component.literal("0") : new Owed(w, w.count() * dawns).line();
            out.add(Component.translatable("gui.headinajar.time_clock.back_pay", Workforce.name(mob), dawns, owes));
        }
        return out;
    }

    /** Use on a creature: the Rep of a strike answers an empty hand and a cake, and takes no gifts; the staff tools pass. */
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof Mob mob)) return;
        TimeClockBlockEntity c = clockOf(mob);
        if (c == null) return;
        if (!mob.getUUID().equals(c.strike.rep)) {
            notRep(event, player, mob, c);
            return;
        }
        if (event.getHand() == InteractionHand.OFF_HAND) {
            // the main hand has answered already (or was a staff tool, which answers for itself)
            if (player.getMainHandItem().isEmpty() || !tool(player.getMainHandItem())) cancel(event, InteractionResult.SUCCESS);
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            player.displayClientMessage(demands(c), true);
            mob.level().playSound(null, mob.getX(), mob.getEyeY(), mob.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 0.8f, 0.9f);
            note(c, "demands");
        } else if (held.is(Items.CAKE)) cake(player, held, c, mob);
        else if (tool(held)) return;
        else Refusals.deny(player, StaffShake.at(mob), "strike.gift");
        cancel(event, InteractionResult.SUCCESS);
    }

    /**
     * A striker who is not the Rep: an empty hand is sent to the Rep, a cake is kept for the Rep (it is not eaten); the
     * staff tools answer for themselves, and anything else is the creature's own business (a bucket still milks a cow).
     */
    private static void notRep(PlayerInteractEvent.EntityInteract event, ServerPlayer player, Mob mob, TimeClockBlockEntity c) {
        ItemStack held = player.getMainHandItem();
        boolean empty = held.isEmpty(), cake = held.is(Items.CAKE);
        if (!empty && !cake) return;
        cancel(event, InteractionResult.SUCCESS);
        if (event.getHand() == InteractionHand.OFF_HAND) return;
        Component rep = c.strike.rep != null && mob.level() instanceof ServerLevel level && level.getEntity(c.strike.rep) instanceof Mob r
                ? Workforce.name(r) : null;
        if (rep == null) Refusals.deny(player, StaffShake.at(mob), "strike.striker");
        else if (cake) Refusals.deny(player, StaffShake.at(mob), "strike.cake_rep", rep);
        else Refusals.deny(player, StaffShake.at(mob), "strike.not_rep", Workforce.name(mob), rep);
    }

    private static void cancel(PlayerInteractEvent.EntityInteract event, InteractionResult result) {
        event.setCanceled(true);
        event.setCancellationResult(result);
    }

    /** The staff's own tools answer a striker themselves (the card, the Clipboard, the Whistle, the Lanyard, the saw). */
    private static boolean tool(ItemStack held) {
        return held.is(WorkRegistry.JOB_CARD.get()) || held.is(WorkRegistry.CLIPBOARD.get()) || held.is(WorkRegistry.SUPERVISORS_WHISTLE.get())
                || held.is(WorkRegistry.STAFF_LANYARD.get()) || held.is(ModRegistry.SURGICAL_SAW.get());
    }

    /**
     * A cake on the Rep: with the whole back pay in the Pay Drawer, it is taken and the strike ends by agreement (everyone
     * at 50, Collective Bargaining, the Rep's cheer); short, the Rep eats the cake anyway and the strike goes on (Pizza
     * Party). Anyone may bring the cake; the drawer is filled by whoever can open the clock.
     */
    public static void cake(ServerPlayer player, ItemStack cake, TimeClockBlockEntity c, Mob rep) {
        if (!(c.getLevel() instanceof ServerLevel level)) return;
        cake.consume(1, player);
        level.playSound(null, rep.getX(), rep.getEyeY(), rep.getZ(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 1, 1);
        level.sendParticles(new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.CAKE)),
                rep.getX(), rep.getEyeY(), rep.getZ(), 8, 0.2, 0.1, 0.2, 0.05);
        List<Owed> bill = bill(c);
        List<Owed> short_ = new ArrayList<>();
        int had = 0;
        for (Owed o : bill) {
            int have = inDrawer(c, o.wage());
            had += Math.min(have, o.count());
            if (have < o.count()) short_.add(new Owed(o.wage(), o.count() - have));
        }
        if (!short_.isEmpty()) {
            // a pizza party is not a pay rise (nothing in the drawer), or the drawer is short of what is owed
            if (had == 0) Refusals.deny(player, StaffShake.at(rep), "strike.cake");
            else Refusals.deny(player, StaffShake.at(rep), "strike.short", short_.get(0).line());
            Advance.grant(player, "pizza_party");
            note(c, "cake short");
            return;
        }
        for (Owed o : bill) c.takeWage(new StaffTable.Wage(o.wage().item(), o.wage().tag(), o.count()));
        end(c, level, AGREED_MORALE, true);
        note(c, "agreed");
        player.displayClientMessage(Component.translatable("message.headinajar.strike.agreed"), true);
        level.playSound(null, rep.getX(), rep.getEyeY(), rep.getZ(), WorkSounds.REP_ACCEPTS.get(), SoundSource.NEUTRAL, 1, 1);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, rep.getX(), rep.getY() + rep.getBbHeight(), rep.getZ(), 8, 0.3, 0.2, 0.3, 0);
        Advance.grant(player, "collective_bargaining");
    }

    // --- refusals for strikers --------------------------------------------------------------------------------------

    /** A Job Card handed to a worker of a striking site: a striker's line, or the site's (an Intern keeps its own cards). */
    public static String cardRefusal(Mob mob) {
        if (clockOf(mob) != null) return "strike.striker";
        return siteOnStrike(mob) != null ? "strike.cards" : null;
    }

    /** Whether hiring this creature for this player would put it on a striking site (the nearest of theirs it stands in). */
    public static boolean hireRefused(ServerPlayer player, Entity target) {
        if (!(target.level() instanceof ServerLevel level)) return false;
        for (SiteIndex.Site s : SiteIndex.get(level).around(target.blockPosition(), player.getUUID())) {
            if (!level.isLoaded(s.pos()) || !(level.getBlockEntity(s.pos()) instanceof TimeClockBlockEntity c)) continue;
            if (c.has(target.getUUID()) || c.room() > 0) return c.strike.active();
        }
        return false;
    }

    // --- the Board --------------------------------------------------------------------------------------------------

    /**
     * The Board has honestly carried "unionise" ({@code CerebralArrayBlockEntity.vote}): every Time Clock within 48 blocks
     * whose site meets the test now (stored morale and unpaid dawns), outside the first week and its cool-down, walks out
     * at once. Returns how many did. Nothing when {@code staff.boardUnionises} is off. {@code ayes} and {@code nays}
     * are the vote's own count, for the record.
     */
    public static int boardCarried(ServerLevel level, BlockPos array, int ayes, int nays) {
        if (!Config.get(Config.BOARD_UNIONISES, true)) return 0;
        int n = 0;
        for (SiteIndex.Site s : List.copyOf(SiteIndex.get(level).all())) {
            if (Vec3.atCenterOf(s.pos()).distanceToSqr(Vec3.atCenterOf(array)) > BOARD_REACH * BOARD_REACH || !level.isLoaded(s.pos())) continue;
            if (level.getBlockEntity(s.pos()) instanceof TimeClockBlockEntity c && mayStart(c) && meetsTest(c) && start(c, "board " + ayes + " to " + nays)) n++;
        }
        return n;
    }

    // --- the Employee Mount -----------------------------------------------------------------------------------------

    /** Whether a block stands in the box of a striking site (an Employee Mount there faces the wall in solidarity). */
    public static boolean solidarity(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return false;
        for (SiteIndex.Site s : SiteIndex.get(server).all()) {
            if (!Workforce.inBox(s.pos(), pos) || !server.isLoaded(s.pos())) continue;
            if (server.getBlockEntity(s.pos()) instanceof TimeClockBlockEntity c && c.strike.active()) return true;
        }
        return false;
    }
}

package com.coolfreeze23.headinajar.staff;

import com.coolfreeze23.headinajar.Advance;
import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.body.BodyServer;
import com.coolfreeze23.headinajar.clarity.Memos;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.host.HostData;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.mind.MobMotor;
import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.staff.second.SecondeeData;
import com.coolfreeze23.headinajar.staff.second.Secondees;
import com.coolfreeze23.headinajar.staff.second.Secondment;
import com.coolfreeze23.headinajar.staff.work.Movers;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.WorkerPaths;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * The Roster's Promote, for a seconded Team Lead (book line 2336): a head on a collar has no neck of its own to saw, so
 * the Board sends for it. Its employer presses Promote on the Time Clock's roster with the secondee within
 * {@value Promotion#ROSTER_REACH} blocks of the clock; it walks to the clock, and the Promotion's own timeline runs
 * there (the offer, the staff turning to watch, the nod, the confetti, the jingle, the bell, the closing line). The
 * commit at tick {@value Promotion#COMMIT} is the collar's unclip: the head comes off through the secondment's own path
 * and goes to whoever pressed, its file marked promoted; the body stays where it stands, an empty collared host already
 * half synced (50 or more) and handshaken. No jar and no saw are spent: the head is already in its jar.
 * <p>
 * Walks to the clock are a list per server, ticked from the level tick (no entity scan) and never saved: a server stop
 * or an unloaded candidate has spent nothing. While it walks, the secondee's brain yields to the walk (a hold).
 */
public final class RosterPromotion {
    /** Close enough to the clock for the ceremony to begin, in blocks from the clock's foot. */
    public static final double ARRIVED = 2.5;
    /** A walk to the clock that has not arrived after this many ticks is called off (nothing spent). */
    public static final int WALK_LIMIT = 300;

    /** One secondee on its way to the clock. */
    public record Walk(ServerLevel level, UUID candidate, UUID player, BlockPos clock, long since) {
    }

    private static final List<Walk> WALKING = new ArrayList<>();

    private RosterPromotion() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.register(RosterPromotion.class);
        SiteState.HOLDS.add(RosterPromotion::hold);
    }

    /**
     * The press. Its rules in order: on this clock's roster; its employer only (trusted players may review, never
     * promote); a seconded head (living staff are promoted in person, with the saw); a Team Lead; within reach of the
     * clock; not already walking; then the rules the saw shares (the server's switch, a strike, an operation near by,
     * a ceremony near by). Every refusal says its line, plays the refusal sound and shakes the head. Always true: a
     * refusal counts as something happening.
     */
    public static boolean press(ServerPlayer player, TimeClockBlockEntity clock, Mob mob) {
        Component name = Workforce.name(mob);
        WorkerData d = Workforce.data(mob);
        StaffFile f = Staff.of(mob);
        BlockPos at = clock.getBlockPos();
        String reason;
        Object[] args = {};
        if (d == null || f == null || !clock.has(mob.getUUID()) || mob.level() != clock.getLevel()) {
            reason = "promotion.far";
            args = new Object[]{name};
        } else if (!player.getUUID().equals(d.employer)) {
            reason = "promotion.others";
            args = new Object[]{name, Workforce.employerName(mob)};
        } else if (!Secondees.seated(mob)) {
            reason = "promotion.in_person";
            args = new Object[]{name};
        } else if (Grades.of(f) < Grades.TEAM_LEAD) {
            if (Grades.of(f) == Grades.INTERN) reason = "promotion.intern";
            else {
                reason = "promotion.below";
                args = new Object[]{name, Grades.name(f)};
            }
        } else if (mob.position().distanceToSqr(Vec3.atBottomCenterOf(at)) > Promotion.ROSTER_REACH * Promotion.ROSTER_REACH) {
            reason = "promotion.far";
            args = new Object[]{name};
        } else if (walking(player.getUUID(), mob.getUUID())) {
            reason = "promotion.busy";
        } else {
            reason = Promotion.gate(player, mob, at);
        }
        if (reason != null) {
            Refusals.deny(player, StaffShake.at(mob), reason, args);
            return true;
        }
        ServerLevel level = player.serverLevel();
        WALKING.add(new Walk(level, mob.getUUID(), player.getUUID(), at.immutable(), level.getGameTime()));
        note(level, mob.getUUID(), "summoned");
        WorkerPaths.stop(mob);
        // a fresh errand: tries it ran out of on its last stop do not count against the walk
        d.pathFails = 0;
        player.displayClientMessage(Component.translatable("message.headinajar.promotion.summoned", name), true);
        return true;
    }

    private static boolean walking(UUID player, UUID candidate) {
        for (Walk w : WALKING) if (w.player.equals(player) || w.candidate.equals(candidate)) return true;
        return false;
    }

    /** The secondee's walk to the clock, or null. */
    public static Walk walk(Mob mob) {
        if (WALKING.isEmpty()) return null;
        for (Walk w : WALKING) if (w.candidate.equals(mob.getUUID()) && w.level == mob.level()) return w;
        return null;
    }

    /** Its brain yields while it walks to the clock: it goes there and does nothing else. */
    static boolean hold(Mob mob, WorkerData d) {
        Walk w = walk(mob);
        if (w == null) return false;
        WorkerPaths.go(mob, d, w.clock, 1.0, null, false);
        MobMotor.tick(mob);
        Movers.tick(mob);
        return true;
    }

    private static boolean arrived(Mob mob, BlockPos clock) {
        return mob.position().distanceToSqr(Vec3.atBottomCenterOf(clock)) <= ARRIVED * ARRIVED || WorkerPaths.arrived(mob, clock);
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post event) {
        if (WALKING.isEmpty() || !(event.getLevel() instanceof ServerLevel level)) return;
        List<Walk> ready = new ArrayList<>();
        for (Iterator<Walk> it = WALKING.iterator(); it.hasNext(); ) {
            Walk w = it.next();
            if (w.level != level) continue;
            Mob mob = level.getEntity(w.candidate) instanceof Mob m ? m : null;
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(w.player);
            WorkerData d = mob == null ? null : Workforce.data(mob);
            String why = null;
            if (mob == null || !mob.isAlive() || mob.isRemoved() || d == null || !Secondees.seated(mob)) why = "candidate";
            else if (player == null || player.level() != level || player.distanceToSqr(mob) > Promotion.LEASH * Promotion.LEASH) why = "player";
            else if (Promotion.striking.test(level, w.clock)) why = "strike";
            else if (arrived(mob, w.clock)) {
                it.remove();
                ready.add(w);
                continue;
            } else if (level.getGameTime() - w.since > WALK_LIMIT || WorkerPaths.givenUp(d)) why = "walk";
            if (why != null) {
                it.remove();
                note(level, w.candidate, "cancelled " + why);
                if (mob != null) WorkerPaths.stop(mob);
                if (player != null) player.displayClientMessage(Component.translatable("message.headinajar.promotion.cancelled"), true);
            }
        }
        // at the clock: the ceremony itself, after one more look at the shared rules (a chair may have started meanwhile)
        for (Walk w : ready) {
            Mob mob = (Mob) level.getEntity(w.candidate);
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(w.player);
            WorkerPaths.stop(mob);
            String why = Promotion.gate(player, mob, w.clock);
            if (why != null) {
                note(level, w.candidate, "refused " + why);
                Refusals.deny(player, StaffShake.at(mob), why);
                continue;
            }
            Promotion.Ceremony c = Promotion.start(player, mob);
            c.roster = true;
        }
    }

    private static void note(ServerLevel level, UUID candidate, String what) {
        Promotion.HISTORY.add(level.getGameTime() + " " + candidate + " " + what);
        if (Promotion.HISTORY.size() > 256) Promotion.HISTORY.remove(0);
    }

    /**
     * The commit for a secondee (tick {@value Promotion#COMMIT}): the collar unclips, the head goes to the presser with
     * its file marked promoted, and the body stays, an empty collared host that remembers its training. False (and
     * cancelled, nothing spent) when the head is no longer seated or no longer a Team Lead.
     */
    static boolean commit(Promotion.Ceremony c, Mob mob, ServerPlayer player) {
        SecondeeData d = Secondees.data(mob);
        StaffFile f = Staff.of(mob);
        if (d == null || !d.seated() || f == null || Grades.of(f) < Grades.TEAM_LEAD) {
            Promotion.cancel(c, "candidate");
            return false;
        }
        c.committed = true;
        ServerLevel level = c.level;
        long day = Staff.day(level);
        Vec3 neck = BodyServer.neck(mob);
        c.committedAt = neck;
        int number = f.number;
        Component name = Staff.name(f);
        // the head first: off the collar by the secondment's own path (off the workforce, the body's own file put back)
        ItemStack head = Secondment.takeHead(mob, d, null);
        StaffFile headFile = Staff.of(head);
        if (headFile == null) headFile = f.copy();
        headFile.career.promotedDay = day;
        StaffFile body = Staff.of(mob);
        if (body != null) {
            headFile.career.link = body.number;
            body.career.link = number;
            mob.setData(ModRegistry.STAFF, body);
        }
        headFile.note("promoted", day, "");
        Staff.write(head, headFile);
        FormActions.give(player, head);
        // the unclip: the seal, the jar off the neck (each client draws it), the body left standing where it is
        BodyServer.unseat(mob);
        HostData data = Hosts.data(mob);
        data.host = true;
        data.sync = Math.max(data.sync, 50);
        data.handshaken = true;
        mob.setPersistenceRequired();
        mob.setTarget(null);
        mob.getNavigation().stop();
        mob.setXRot(0);
        Network.syncHost(mob);
        BodyServer.later(level, 30, () -> player.displayClientMessage(Component.translatable("message.headinajar.promotion.body", name), true));
        Advance.grant(player, "promoted");
        // the advancement also asks for a Board Meeting: one already held counts
        var board = player.server.getAdvancements().get(com.coolfreeze23.headinajar.HeadInAJar.id("board_meeting"));
        if (board != null && player.getAdvancements().getOrStartProgress(board).isDone()) Advance.grant(player, "board_meeting");
        Memos.send(player, Memos.Memo.PROMOTION);
        Promotion.note(c, "commit");
        return true;
    }

    /** The walks under way (tests). */
    public static List<Walk> walking() {
        return WALKING;
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        WALKING.clear();
    }
}

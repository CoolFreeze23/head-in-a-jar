package com.coolfreeze23.headinajar.staff;

import com.coolfreeze23.headinajar.Advance;
import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.clarity.Memos;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.item.PerformanceReviewItem;
import com.coolfreeze23.headinajar.staff.work.ClockNet;
import com.coolfreeze23.headinajar.staff.work.GradeWork;
import com.coolfreeze23.headinajar.staff.work.WorkSounds;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.WorkerLooks;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Performance Review (0.7.2): the Roster's <b>Review</b> on a worker within reach of its Time Clock, by its employer
 * or someone the employer trusts, once a day. One Paper from the presser's inventory becomes the slip, put in their hand;
 * the grade goes to the highest its tasks earn (a review may skip grades); the counts since the last review start again;
 * +5 morale whatever the slip says. The rating: "Placed on a Performance Plan" if it filed a Grievance since the last
 * review; else "Exceeds Expectations" for 100 tasks or more and Engaged now; "Meets Expectations" for 25 or more; else
 * "Needs Improvement". A review with nothing since the last still prints (the empty review).
 */
public final class Reviews {
    /** Morale a review gives, whatever it says. */
    public static final int MORALE = 5;
    /** Tasks since the last review for each rating. */
    public static final int EXCEEDS_TASKS = 100, MEETS_TASKS = 25;

    private Reviews() {
    }

    /** The rating a file would get now. */
    public static int rating(StaffFile f) {
        if (f.career.grievancesSinceReview > 0) return PerformanceReviewItem.PLAN;
        int since = Math.max(0, f.tasks - f.career.tasksAtReview);
        if (since >= EXCEEDS_TASKS && Band.of(f) == Band.ENGAGED) return PerformanceReviewItem.EXCEEDS;
        if (since >= MEETS_TASKS) return PerformanceReviewItem.MEETS;
        return PerformanceReviewItem.NEEDS;
    }

    /** Whether this file was reviewed today. */
    public static boolean reviewedToday(StaffFile f, long day) {
        return f != null && f.career.reviewedDay == day;
    }

    /**
     * The Review press on a worker of {@code clock}'s roster that is here ({@code mob}). Returns whether a slip was
     * printed; every other outcome is a refusal at the worker, with its line.
     */
    public static boolean review(ServerPlayer player, TimeClockBlockEntity clock, Mob mob) {
        Component name = Workforce.name(mob);
        if (!Workforce.mayOrder(mob, player)) return Refusals.deny(player, StaffShake.at(mob), "review.untrusted", name);
        StaffFile f = Staff.of(mob);
        if (f == null) return Refusals.deny(player, StaffShake.at(mob), "review.away", name);
        ServerLevel level = player.serverLevel();
        long day = Staff.day(level);
        if (reviewedToday(f, day)) return Refusals.deny(player, StaffShake.at(mob), "review.today", name);
        if (!player.getAbilities().instabuild && !takePaper(player)) return Refusals.deny(player, StaffShake.at(mob), "review.paper");
        int was = Grades.of(f), now = Math.max(was, Grades.earned(f.tasks));
        int rating = rating(f);
        String reviewer = player.getGameProfile().getName();
        ItemStack slip = PerformanceReviewItem.of(f, Staff.department(f, null), rating, now, now > was, reviewer, day);
        // the file: the new grade, the counts start again, +5 morale, a history line
        f.career.grade = now;
        f.career.tasksAtReview = f.tasks;
        f.career.reviewedDay = day;
        f.career.grievancesSinceReview = 0;
        f.career.unpaidSinceReview = 0;
        if (f.staff()) f.morale = Math.min(100, f.morale + MORALE);
        f.note("reviewed", day, "");
        WorkerLooks.changed(mob);
        WorkerData d = Workforce.data(mob);
        if (d != null) GradeWork.forget(d.clock);
        // the slip into the hand (else the inventory, else at the feet)
        if (player.getMainHandItem().isEmpty()) player.setItemInHand(InteractionHand.MAIN_HAND, slip);
        else if (!player.getInventory().add(slip)) player.drop(slip, false);
        level.playSound(null, clock.getBlockPos(), WorkSounds.CARD_PRINTED.get(), SoundSource.BLOCKS, 1, 0.9f);
        player.displayClientMessage(Component.translatable(now > was ? "message.headinajar.review.raised" : "message.headinajar.review.same", name,
                Grades.name(now)), true);
        Advance.grant(player, "constructive_feedback");
        if (now == Grades.TEAM_LEAD && was < Grades.TEAM_LEAD) {
            Advance.grant(player, "middle_management");
            ServerPlayer boss = f.employer == null ? null : level.getServer().getPlayerList().getPlayer(f.employer);
            if (boss != null && boss != player) Advance.grant(boss, "middle_management");
            Memos.send(boss != null ? boss : player, Memos.Memo.TEAM_LEAD, name);
        }
        HISTORY.add(level.getGameTime() + " " + mob.getUUID() + " " + was + ">" + now + " " + rating);
        if (HISTORY.size() > 256) HISTORY.remove(0);
        return true;
    }

    /** What each review did, for tests: "gameTime worker was>now rating" (newest last). */
    public static final List<String> HISTORY = new ArrayList<>();

    private static boolean takePaper(ServerPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.PAPER)) {
                s.shrink(1);
                inv.setChanged();
                return true;
            }
        }
        return false;
    }

    // --- the Roster's career rows ------------------------------------------------------------------------------------

    /** The career rows and the strike's section for a clock's open menu ({@code menu}: its container id). */
    public static ClockNet.ClockCareer career(int menu, TimeClockBlockEntity clock) {
        List<ClockNet.CareerRow> rows = new ArrayList<>();
        boolean striking = false;
        List<Component> demands = List.of();
        if (clock.getLevel() instanceof ServerLevel level) {
            long day = Staff.day(level);
            for (UUID id : clock.roster()) {
                Entity e = level.getEntity(id);
                if (!(e instanceof Mob mob) || Workforce.data(mob) == null) continue;
                StaffFile f = Staff.of(mob);
                int flags = 0;
                if (Grades.due(f)) flags |= ClockNet.CareerRow.DUE;
                if (reviewedToday(f, day)) flags |= ClockNet.CareerRow.TODAY;
                if (SiteState.striker.test(mob)) flags |= ClockNet.CareerRow.STRIKER;
                if (com.coolfreeze23.headinajar.staff.second.Secondees.seated(mob)) flags |= ClockNet.CareerRow.SECONDED;
                rows.add(new ClockNet.CareerRow(id, Grades.of(f), Grades.earned(f == null ? 0 : f.tasks), flags));
            }
            striking = SiteState.striking.test(clock);
            if (striking) demands = SiteState.demands.apply(clock);
        }
        return new ClockNet.ClockCareer(menu, List.copyOf(rows), striking, List.copyOf(demands.size() > 64 ? demands.subList(0, 64) : demands));
    }
}

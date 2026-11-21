package com.coolfreeze23.headinajar.staff;

import com.coolfreeze23.headinajar.Advance;
import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.block.DeskJobs;
import com.coolfreeze23.headinajar.block.JarredHeadBlockEntity;
import com.coolfreeze23.headinajar.clarity.Memos;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.WorkerLooks;
import com.coolfreeze23.headinajar.staff.work.WorkerPaths;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Chapter 3's Grievance (Form 66-G) for every kind of staff on the payroll: under 15 morale at two dawns running files
 * one (the stamp and the sigh, a history line, the first-Grievance memo to the employer). Living staff stop work, turn
 * to the nearest solid face within 4 blocks and stand; a plinth head turns away from its facing and stops turning to
 * players; both hold it until morale is back to 40 ("Grievance withdrawn. Form 66-G has been shredded."). A cake that
 * ends one grants Let Them Eat Cake. The Employee Mount's villager is read from its pay record (its 3-day wall-facing is
 * the Grievance); only the bookkeeping is new there. No number of theirs is changed by the Grievance itself. With
 * {@code staff.morale} off there are no grievances.
 */
public final class Grievances {
    /** A grievance ends at this morale (Content). */
    public static final int ENDS_AT = Band.CONTENT_AT;
    /** How far a living worker looks for a wall to face. */
    public static final int WALL_REACH = 4;

    /** Where each living worker with a grievance faces (worked out once, when it is first held). */
    private static final Map<Mob, Float> WALL = new WeakHashMap<>();

    private Grievances() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.register(Grievances.class);
        // living staff with a grievance stand facing the wall (a striker's own hold comes first: it stands in the ring)
        SiteState.HOLDS.add(Grievances::hold);
    }

    public static boolean enabled() {
        return Config.get(Config.MORALE, true);
    }

    // --- filing --------------------------------------------------------------------------------------------------------

    /** Whether this file's dawn files Form 66-G now. */
    static boolean files(StaffFile f) {
        return enabled() && f != null && f.staff() && !f.career.grievance && f.morale < Band.DISENGAGED_AT && f.lowDawns >= 2;
    }

    /** Files it: the file's side (shared by every kind of staff). */
    static void file(Level level, StaffFile f) {
        f.career.grievance = true;
        f.career.grievancesSinceReview++;
        f.note("grievance", Staff.day(level), "");
        ServerPlayer employer = f.employer == null || level.getServer() == null ? null : level.getServer().getPlayerList().getPlayer(f.employer);
        if (employer != null) Memos.send(employer, Memos.Memo.GRIEVANCE);
    }

    /** The stamp and the sigh ({@code staff.grievance}). */
    static void stamp(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, com.coolfreeze23.headinajar.registry.ModSounds.STAFF_GRIEVANCE.get(), SoundSource.NEUTRAL, 1, 1);
    }

    /** A living worker's dawn has run (its morale and {@code lowDawns} are today's). */
    public static void dawn(Mob mob, StaffFile f) {
        if (!files(f)) return;
        file(mob.level(), f);
        mob.setData(ModRegistry.STAFF, f);
        stamp(mob.level(), mob.getX(), mob.getY(), mob.getZ());
        WorkerData d = Workforce.data(mob);
        if (d != null) {
            d.dropTask();
            d.status = "staff.headinajar.status.grievance";
        }
        WorkerPaths.stop(mob);
        Workforce.sayNow(mob, Component.translatable("staff.headinajar.say.grievance"));
        WorkerLooks.changed(mob);
    }

    /** A plinth head's dawn has run. */
    public static void dawn(JarredHeadBlockEntity head, StaffFile f) {
        if (!files(f) || head.getLevel() == null) return;
        file(head.getLevel(), f);
        BlockPos pos = head.getBlockPos();
        stamp(head.getLevel(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        head.say(Component.translatable("staff.headinajar.say.grievance"), null);
    }

    // --- ending --------------------------------------------------------------------------------------------------------

    /** Whether an open grievance on this file ends now (morale back to 40, or morale switched off). */
    public static boolean ends(StaffFile f) {
        return f != null && f.career.grievance && (!enabled() || f.morale >= ENDS_AT);
    }

    /** The file's side of the end: the flag off and a history line. */
    static void withdraw(Level level, StaffFile f) {
        f.career.grievance = false;
        f.note("grievance_withdrawn", Staff.day(level), "");
    }

    /** A living worker's grievance ends: back to work, with the line floating over it. */
    public static void end(Mob mob, StaffFile f, ServerPlayer cake) {
        withdraw(mob.level(), f);
        mob.setData(ModRegistry.STAFF, f);
        WALL.remove(mob);
        mob.setXRot(0);
        WorkerData d = Workforce.data(mob);
        if (d != null) d.status = null;
        Workforce.sayNow(mob, Component.translatable("staff.headinajar.say.grievance_withdrawn"));
        WorkerLooks.changed(mob);
        if (cake != null) Advance.grant(cake, "let_them_eat_cake");
    }

    /**
     * A plinth head's file was just changed (a cake, a lunch, a wage, a dawn): its grievance ends if morale is back. The
     * caller saves the file ({@code head.setStaff}).
     */
    public static void check(JarredHeadBlockEntity head, StaffFile f, ServerPlayer cake) {
        if (!ends(f) || head.getLevel() == null) return;
        withdraw(head.getLevel(), f);
        head.say(Component.translatable("staff.headinajar.say.grievance_withdrawn"), null);
        if (cake != null) Advance.grant(cake, "let_them_eat_cake");
    }

    // --- living staff: the hold ------------------------------------------------------------------------------------------

    /** A worker with a grievance open stands facing the wall and does no work; it goes back once morale is back. */
    static boolean hold(Mob mob, WorkerData d) {
        StaffFile f = Staff.of(mob);
        if (f == null || !f.career.grievance) return false;
        if (ends(f)) {
            end(mob, f, null);
            return false;
        }
        if (!mob.getNavigation().isDone()) WorkerPaths.stop(mob);
        Float yaw = WALL.get(mob);
        if (yaw == null) {
            yaw = wall(mob);
            WALL.put(mob, yaw);
        }
        mob.setYRot(yaw);
        mob.setYBodyRot(yaw);
        mob.setYHeadRot(yaw);
        mob.setXRot(20);
        d.status = "staff.headinajar.status.grievance";
        return true;
    }

    /** The yaw that faces the nearest solid block face within {@link #WALL_REACH} at its feet or eyes (none: as it is). */
    static float wall(Mob mob) {
        Level level = mob.level();
        BlockPos at = mob.blockPosition();
        int best = Integer.MAX_VALUE;
        float yaw = mob.getYRot();
        for (int dy = 0; dy <= 1; dy++) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                for (int n = 1; n <= WALL_REACH && n < best; n++) {
                    BlockPos p = at.relative(dir, n).above(dy);
                    if (level.getBlockState(p).isFaceSturdy(level, p, dir.getOpposite())) {
                        best = n;
                        yaw = dir.toYRot();
                        break;
                    }
                }
            }
        }
        return Mth.wrapDegrees(yaw);
    }

    // --- cake ----------------------------------------------------------------------------------------------------------

    /**
     * A cake used on a living member of staff: +40 morale, once a day, and the cake is eaten; one that brings a
     * grievance's morale back ends it (Let Them Eat Cake). A striking site's Rep takes cake on the strike's terms (its
     * own handler runs first).
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void cake(PlayerInteractEvent.EntityInteract event) {
        if (event.isCanceled() || !(event.getTarget() instanceof Mob mob) || !event.getItemStack().is(Items.CAKE)) return;
        if (!Workforce.isWorker(mob) || SiteState.striker.test(mob)) return;
        StaffFile f = Staff.of(mob);
        if (f == null || !f.staff()) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) return;
        give(player, mob, f, event.getItemStack());
    }

    /** The cake itself (the event's body, for tests). */
    public static void give(ServerPlayer player, Mob mob, StaffFile f, ItemStack stack) {
        long day = Staff.day(mob.level());
        if (f.career.cakeDay == day) {
            Refusals.deny(player, StaffShake.at(mob), "staff.no_cake");
            return;
        }
        stack.consume(1, player);
        f.career.cakeDay = day;
        StaffBlocks.adjust(f, DeskJobs.CAKE);
        mob.setData(ModRegistry.STAFF, f);
        mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 1, 1);
        if (mob.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 6, 0.25, 0.2, 0.25, 0);
        }
        player.displayClientMessage(Component.translatable("message.headinajar.staff.cake", Workforce.name(mob)), true);
        if (ends(f)) end(mob, f, player);
    }
}

package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.staff.Staff;
import com.coolfreeze23.headinajar.staff.StaffFile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.EnumSet;

/**
 * Heads at work, as the world sees them: nobody but its employer (and those they trust) may take a head that has an
 * employer off its plinth; creepers keep away from a fed cat head as from a live cat; a cat head's employer who slept
 * is left a gift; and a level's desk index goes with the level.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID)
public final class HeadEvents {
    private HeadEvents() {
    }

    /** A head with an employer is broken only by them, those they trust, or an operator in creative. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void broke(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof JarredHeadBlockEntity head)) return;
        StaffFile f = head.staff();
        if (f == null || f.employer == null || mayRemove(player, f)) return;
        event.setCanceled(true);
        com.coolfreeze23.headinajar.feedback.Refusals.deny(player, com.coolfreeze23.headinajar.feedback.Refuser.at(head), "staff.not_yours",
                Staff.name(f), Staff.employer(f));
    }

    public static boolean mayRemove(ServerPlayer player, StaffFile f) {
        if (f == null || f.employer == null) return true;
        if (player.isCreative() && player.hasPermissions(2)) return true;
        return com.coolfreeze23.headinajar.form.HeadRegistry.get(player).trusts(f.employer, player);
    }

    /** Creepers get vanilla's cat fear for fed cat heads too (6 blocks, as from a live cat). A goodbye saved by a stop goes. */
    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof Creeper creeper) creeper.goalSelector.addGoal(3, new AvoidCatHead(creeper));
        if (event.loadedFromDisk() && event.getEntity().getTags().contains(HeadSpeak.GOODBYE_TAG)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void ticked(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) HeadSpeak.tidy(level);
    }

    /** A player who slept: their fed cat heads leave a gift at the next dawn. */
    @SubscribeEvent
    public static void woke(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        for (BlockPos pos : DeskIndex.all(level, "cat")) {
            if (level.getBlockEntity(pos) instanceof JarredHeadBlockEntity head && head.staff() != null && player.getUUID().equals(head.staff().employer)) {
                head.giftDue = true;
                head.setChanged();
            }
        }
    }

    @SubscribeEvent
    public static void unloaded(LevelEvent.Unload event) {
        DeskIndex.unload(event.getLevel());
    }

    /** A creeper's flight from a fed cat head: looked for every 10 ticks; with no fed cat head in the level, one map read. */
    public static final class AvoidCatHead extends Goal {
        private final PathfinderMob mob;
        private int wait;
        private Vec3 from;

        public AvoidCatHead(PathfinderMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (--wait > 0) return false;
            wait = 10;
            BlockPos cat = DeskIndex.near(mob.level(), "cat", mob.position(), DeskJobs.CAT_CREEPERS);
            if (cat == null) return false;
            from = cat.getCenter();
            Vec3 away = DefaultRandomPos.getPosAway(mob, 16, 7, from);
            if (away == null || away.distanceToSqr(from) < mob.distanceToSqr(from)) return false;
            return mob.getNavigation().moveTo(away.x, away.y, away.z, 1.2);
        }

        @Override
        public boolean canContinueToUse() {
            return !mob.getNavigation().isDone() && from != null && mob.distanceToSqr(from) < 7 * 7;
        }
    }
}

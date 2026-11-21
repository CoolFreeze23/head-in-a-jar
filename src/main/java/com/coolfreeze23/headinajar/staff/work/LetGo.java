package com.coolfreeze23.headinajar.staff.work;

import com.coolfreeze23.headinajar.mind.MobMotor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Let go's notice period: 200 ticks in which the worker targets nobody and walks, head down, to a spot 12 blocks from
 * the nearest player, escorted by a Security colleague of its site if it has one, while DOLORES says so to everyone
 * within 16. Then the record comes off and it is an ordinary creature again.
 */
public final class LetGo {
    public static final String REASON = "staff.headinajar.reason.restructuring";
    /** How far it walks away from the nearest player. */
    public static final int AWAY = 12;
    /** The head held down while it goes (degrees of pitch). */
    public static final float DROOP = 40;
    /** Where each one on notice walks to, and who escorts it (not saved: a reload mid-notice just lets it stand). */
    private static final Map<UUID, BlockPos> TO = new HashMap<>();
    /** Escort -> the one it is seeing out. */
    private static final Map<UUID, UUID> ESCORTS = new HashMap<>();

    private LetGo() {
    }

    static void start(ServerLevel level, Mob mob, WorkerData d) {
        Workforce.calm(level, mob);
        Player near = level.getNearestPlayer(mob, 32);
        // a spot 12 away from the nearest player, by its own way of getting about (a ghast or a slime too)
        BlockPos to = near == null ? null : Movers.randomPoint(mob, null, near.position(), AWAY);
        if (to != null) TO.put(mob.getUUID(), to);
        Mob escort = escort(level, mob, d);
        if (escort != null) ESCORTS.put(escort.getUUID(), mob.getUUID());
        // DOLORES, to everyone within 16 (the voiced line has no name; the text has)
        Component line = Component.translatable("dolores.headinajar.escort", Workforce.name(mob));
        if (escort != null) {
            for (ServerPlayer p : level.getPlayers(p -> p.distanceToSqr(mob) < 16 * 16)) p.displayClientMessage(line, true);
            com.coolfreeze23.headinajar.block.DoloresVoice.say(level, mob.blockPosition(), "escort", "");
        }
    }

    /** The nearest Security colleague of its site, within 32 (null: it walks out alone). */
    private static Mob escort(ServerLevel level, Mob mob, WorkerData d) {
        if (d.clock == null) return null;
        Mob best = null;
        for (Mob m : level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(32), Workforce::isWorker)) {
            WorkerData o = Workforce.data(m);
            if (m == mob || o.state == WorkerData.State.NOTICE || !d.clock.equals(o.clock) || !WorkTable.SECURITY.equals(Order.jobOf(level, o))) continue;
            if (best == null || m.distanceToSqr(mob) < best.distanceToSqr(mob)) best = m;
        }
        return best;
    }

    /** Each tick of a worker on notice. */
    static void tick(Mob mob, WorkerData d) {
        if (!(mob.level() instanceof ServerLevel level)) return;
        mob.setTarget(null);
        if (level.getGameTime() >= d.noticeUntil) {
            end(level, mob, d);
            return;
        }
        BlockPos to = TO.get(mob.getUUID());
        if (to != null && (mob.tickCount & 15) == 0 && MobMotor.idle(mob) && mob.blockPosition().distSqr(to) > 4) {
            WorkerPaths.go(mob, d, to, 0.8, null, false);
        }
        MobMotor.tick(mob);
        Movers.tick(mob);
        // head down after the look control has had its say
        mob.setXRot(DROOP);
        mob.xRotO = DROOP;
    }

    private static void end(ServerLevel level, Mob mob, WorkerData d) {
        TO.remove(mob.getUUID());
        ESCORTS.values().removeIf(id -> id.equals(mob.getUUID()));
        UUID employer = d.employer;
        Workforce.release(mob);
        if (mob instanceof Enemy) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(employer);
            if (p != null) p.sendSystemMessage(Component.translatable("message.headinajar.let_go.hostile", Workforce.name(mob)));
        }
    }

    /**
     * An escort's think, before its own job: it walks beside the one it is seeing out until that one's notice ends.
     * Returns true while it is escorting (its job waits).
     */
    static boolean escorting(Mob mob) {
        UUID out = ESCORTS.get(mob.getUUID());
        if (out == null || !(mob.level() instanceof ServerLevel level)) return false;
        if (!(level.getEntity(out) instanceof Mob fired) || !Workforce.isWorker(fired) || Workforce.data(fired).state != WorkerData.State.NOTICE) {
            ESCORTS.remove(mob.getUUID());
            return false;
        }
        if (mob.distanceToSqr(fired) > 4) WorkerPaths.go(mob, Workforce.data(mob), fired.blockPosition(), 1.0, null, true);
        return true;
    }

    /** Whether this worker is seeing someone out now. */
    public static boolean isEscort(Mob mob) {
        return ESCORTS.containsKey(mob.getUUID());
    }

    static double walk(Mob mob) {
        WorkTable.Spec spec = WorkTable.of(mob);
        return spec == null ? 1 : spec.walk();
    }
}

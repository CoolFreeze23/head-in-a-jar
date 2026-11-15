package com.coolfreeze23.headinajar.staff;

import com.coolfreeze23.headinajar.feedback.Refuser;
import com.coolfreeze23.headinajar.fx.Fx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.phys.Vec3;

/**
 * A creature the saw refused, as the refusing thing: the refusal sound comes from it, and it shakes its head once. A
 * villager shakes its own head (the game's), anything else is told to every client nearby ({@link Fx#STAFF_SHAKE}),
 * which turns the drawn head; the Ender Dragon and the Wither do not shake.
 */
public final class StaffShake implements Refuser {
    private final Mob mob;

    private StaffShake(Mob mob) {
        this.mob = mob;
    }

    public static Refuser at(Mob mob) {
        return new StaffShake(mob);
    }

    @Override
    public Vec3 refusalOrigin() {
        return mob.position().add(0, mob.getBbHeight() * 0.5, 0);
    }

    @Override
    public SoundSource refusalSource() {
        return SoundSource.NEUTRAL;
    }

    @Override
    public void showRefusal(ServerPlayer player, String reason) {
        shake(mob);
    }

    /** The head shake on its own (no line, no sound). */
    public static void shake(Mob mob) {
        if (mob.getType() == EntityType.ENDER_DRAGON || mob.getType() == EntityType.WITHER
                || mob.getType().is(net.neoforged.neoforge.common.Tags.EntityTypes.BOSSES)) return;
        if (mob instanceof AbstractVillager villager) {
            villager.setUnhappyCounter(40);
            return;
        }
        if (mob.level() instanceof ServerLevel level) {
            Fx.send(level, Fx.of(Fx.STAFF_SHAKE, mob.position().add(0, mob.getBbHeight() * 0.5, 0), null, -1, mob.getId(), 0));
        }
    }
}

package com.coolfreeze23.headinajar.staff.work;

import com.coolfreeze23.headinajar.Config;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * The company's friend-or-foe rule, word for word the Turret Pedestal's: never creative or spectator players or the
 * owner; other players only with {@code genetics.pvpTurrets} on and only if not allied with the owner; never owned pets,
 * villagers, traders, iron golems or the owner's allies; and never any staff. Otherwise only {@link Enemy}.
 */
public final class FriendOrFoe {
    private FriendOrFoe() {
    }

    public static boolean mayTarget(UUID owner, Level level, LivingEntity e) {
        if (e == null || !e.isAlive() || e.isInvulnerable()) return false;
        if (e instanceof Player p) {
            if (p.isCreative() || p.isSpectator() || p.getUUID().equals(owner)) return false;
            if (!Config.get(Config.PVP_TURRETS, false)) return false;
            Player o = owner == null || level == null ? null : level.getPlayerByUUID(owner);
            return o == null || !o.isAlliedTo(p);
        }
        if (Workforce.isWorker(e)) return false;
        if (e instanceof OwnableEntity pet && pet.getOwnerUUID() != null) return false;
        if (e instanceof AbstractVillager || e instanceof IronGolem) return false;
        if (owner != null && level != null) {
            Player o = level.getPlayerByUUID(owner);
            if (o != null && o.isAlliedTo(e)) return false;
        }
        return e instanceof Enemy;
    }
}

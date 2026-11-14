package com.coolfreeze23.headinajar.mecha;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.form.FormActions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;

/**
 * A server that does not allow flight ({@code allow-flight=false}, a dedicated server's default) disconnects a player
 * whose vehicle, or who, hangs in the air with no block round it for about four seconds ("Flying is not enabled on this
 * server"). The mod holds things in the air on purpose: a Kite flying or hovering, a Rocket Pack's burn, a Hover Skirt,
 * a Skitter on its Dragline, a player carried or thrown by a mecha's verb, a player held by Telekinesis, a jar under the
 * Golden Parachute, a jar flying a winged host. While one of those holds, the server's own count for that player is set
 * back to nothing each tick. Nothing about gravity changes: the machine and the player move exactly as before.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID)
public final class Aloft {
    private Aloft() {
    }

    /** How long after a mecha's verb last held a player they still count as carried: the arc of its throw. */
    public static final int THROWN_TICKS = 60;
    /** Server: the last game tick a mecha's verb held each player (carried by fists or talons, scooped by a Lunk). */
    private static final Map<UUID, Long> CARRIED = new java.util.HashMap<>();

    /** A mecha's verb holds this entity now (it is put where the fists, talons or scoop hold it). */
    public static void carried(Entity e) {
        if (e instanceof ServerPlayer p) CARRIED.put(p.getUUID(), p.level().getGameTime());
    }

    /** The machine's own tick: its pilot's vehicle count is set back while the machine is legitimately aloft. */
    public static void vehicle(MechaEntity m, ServerPlayer pilot) {
        if (pilot.connection != null && MechaLogic.aloft(m)) pilot.connection.aboveGroundVehicleTickCount = 0;
    }

    /** Whether the mod holds this player up now: carried or just thrown by a mecha, held by Telekinesis, under the parachute. */
    public static boolean heldUp(ServerPlayer p) {
        Long at = CARRIED.get(p.getUUID());
        if (at != null) {
            if (p.level().getGameTime() - at <= THROWN_TICKS) return true;
            CARRIED.remove(p.getUUID());
        }
        if (p.getPersistentData().getInt(HopperNet.CHUTE) != HopperNet.FOLDED) return true;
        for (ServerPlayer other : p.server.getPlayerList().getPlayers()) {
            if (other != p && FormActions.state(other).heldEntity() == p.getId()) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.connection == null) return;
        if (heldUp(p)) p.connection.aboveGroundTickCount = 0;
        // a jar steering a flying host (a phantom, a ghast): the host flies by the jar's own rules
        if (p.getVehicle() instanceof net.minecraft.world.entity.LivingEntity host && !(host instanceof MechaEntity) && host.getControllingPassenger() == p) {
            var def = com.coolfreeze23.headinajar.host.Hosts.definition(host);
            if (def != null && def.has("flight")) p.connection.aboveGroundVehicleTickCount = 0;
        }
    }
}

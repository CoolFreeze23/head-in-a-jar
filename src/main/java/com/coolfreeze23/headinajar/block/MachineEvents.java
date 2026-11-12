package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Machines and the players they hold: a jar is never left in a Neural Socket that no longer holds it. */
public final class MachineEvents {
    private MachineEvents() {
    }

    /** Lightning in the rain near a Relay Mast: its dish flinches. */
    @SubscribeEvent
    public static void lightning(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.LightningBolt bolt && event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            PowerBlockEntities.Station.lightning(level, bolt.position());
        }
    }

    /** The machines each player has been told stand cut off (dimension and position), for this session. */
    private static final java.util.Map<java.util.UUID, java.util.Set<String>> TOLD_CUT_OFF = new java.util.HashMap<>();

    /**
     * A two-cell machine placed before it was two cells tall, with a see-through block over it (glass, a fence, a slab,
     * water): it is drawn cut off at the top of its cell. The first time a player clicks it in a session they are told
     * why and what to do; the click still does its job. Under an opaque block there is nothing to see, so nothing is said.
     */
    @SubscribeEvent
    public static void cutOff(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var level = player.serverLevel();
        var pos = event.getPos();
        if (!(level.getBlockState(pos).getBlock() instanceof com.coolfreeze23.headinajar.machine.TallMachine tall) || !tall.hasTop()) return;
        var above = pos.above();
        var up = level.getBlockState(above);
        if (up.is(com.coolfreeze23.headinajar.registry.ModRegistry.MACHINE_TOP.get()) || up.isSolidRender(level, above)) return;
        // room it will take on its own within two seconds (air, grass, snow): nothing to say
        if (up.canBeReplaced() && up.getFluidState().isEmpty()) return;
        if (!toldCutOff(player).add(level.dimension().location() + "@" + pos.asLong())) return;
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.headinajar.machine.cut_off",
                level.getBlockState(pos).getBlock().getName(), up.getBlock().getName()));
    }

    /** The machines this player has been told about this session (tests read it). */
    public static java.util.Set<String> toldCutOff(ServerPlayer player) {
        return TOLD_CUT_OFF.computeIfAbsent(player.getUUID(), id -> new java.util.HashSet<>());
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        TOLD_CUT_OFF.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 10 == 0) NeuralSocketBlockEntity.reconcile(player);
    }

    /** Logging back in unplugs: the socket let go of its jar when the player left. */
    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && FormActions.state(player).form() == Form.SOCKET) {
            NeuralSocketBlockEntity.unplug(player, "socket.headinajar.relogged");
        }
    }
}

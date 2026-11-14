package com.coolfreeze23.headinajar.clarity;

import com.coolfreeze23.headinajar.Advance;
import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.host.Hosts;
import com.coolfreeze23.headinajar.module.ModuleType;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The career ladder's steps that are a change of state rather than an item or a placed block: the first module, the
 * arm, a charge, the head carried, the head back on, a shell, the cockpit. Read once a second from the player's own
 * state, so nothing elsewhere has to remember to fire them; each step's advancement is earned once and kept by id. The
 * same pass hands the state to {@link Memos}, which says the few things a new employee must hear the first time.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID)
public final class Guideposts {
    /** What was seen last second, per player (not saved: a step is the advancement itself, which is). */
    private static final Map<ServerPlayer, Seen> SEEN = new WeakHashMap<>();

    record Seen(Form form, int charge, boolean assembled) {
    }

    private Guideposts() {
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        check(player);
    }

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SEEN.remove(player);
    }

    /** One look at a player's state: the steps it shows, then the memos it calls for. Public for tests. */
    public static void check(ServerPlayer player) {
        JarState state = player.getData(ModRegistry.JAR_STATE);
        Form form = state.form();
        var holder = player.server.getAdvancements().get(HeadInAJar.id("assembled"));
        boolean assembled = holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
        Seen last = SEEN.put(player, new Seen(form, state.charge(), assembled));
        if (form.isJarred() && state.modules().stream().anyMatch(s -> !s.isEmpty())) Advance.grant(player, "first_module");
        if (state.has(ModuleType.MANIPULATOR_ARM)) Advance.grant(player, "armed");
        if (state.carryingHead()) Advance.grant(player, "head_carried");
        if (form == Form.PILOT && player.getVehicle() instanceof MechaEntity) Advance.grant(player, "boarded");
        if (form == Form.HOSTED && player.getVehicle() != null && Hosts.isHost(player.getVehicle()) && Hosts.data(player.getVehicle()).shell) {
            Advance.grant(player, "shell");
        }
        if (last != null) {
            // charge that rose by a real amount between two looks: a Dock, a Cradle or the feed slot
            if (form.isJarred() && last.form().isJarred() && state.charge() >= last.charge() + 500) Advance.grant(player, "charged");
            if (form == Form.BODY && last.form().isDecapitated()) Advance.grant(player, "reattached");
        }
        Memos.check(player, state, last != null && assembled && !last.assembled());
    }

    /** Forget what was seen (a test that changes a player's form by hand starts again from here). */
    public static void forget(ServerPlayer player) {
        SEEN.remove(player);
    }
}

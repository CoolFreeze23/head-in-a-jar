package com.coolfreeze23.headinajar.feedback;

import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Memo 12-W: before anything costly happens to a head, its owner hears of it. A warning is a line (shown over any open
 * screen, as a refusal is) and a sound only its owner hears: {@code feedback.warning} while there is time, {@code
 * feedback.danger} when the next knock may be the last. The same warning is repeated at most once in {@code every}
 * ticks, so a fire or a fight says it while it lasts without saying it every tick.
 * <p>
 * A reason is a short id such as {@code "jar.glass_low"}; its text is the lang key {@code warning.headinajar.<id>}.
 */
@EventBusSubscriber(modid = com.coolfreeze23.headinajar.HeadInAJar.MOD_ID)
public final class Warnings {
    public static final String PREFIX = "warning.headinajar.";
    private static final int HISTORY = 32;
    private static final Map<UUID, Map<String, Integer>> LAST = new HashMap<>();
    private static final Map<UUID, Deque<String>> DELIVERED = new HashMap<>();

    private Warnings() {
    }

    /** Warns with time to act. Returns false when the same warning was given in the last {@code every} ticks. */
    public static boolean warn(ServerPlayer player, int every, String reason, Object... args) {
        return deliver(player, false, every, reason, args);
    }

    /** Warns that the next knock may be the last. */
    public static boolean danger(ServerPlayer player, int every, String reason, Object... args) {
        return deliver(player, true, every, reason, args);
    }

    public static Component line(String reason, Object... args) {
        return Component.translatable(PREFIX + reason, args);
    }

    /** The warnings given to this player, oldest first (the last {@value #HISTORY}). For tests. */
    public static List<String> delivered(ServerPlayer player) {
        Deque<String> seen = DELIVERED.get(player.getUUID());
        return seen == null ? List.of() : new ArrayList<>(seen);
    }

    private static boolean deliver(ServerPlayer player, boolean danger, int every, String reason, Object... args) {
        int now = player.server.getTickCount();
        Map<String, Integer> last = LAST.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
        Integer before = last.get(reason);
        if (before != null && now >= before && now - before < every) return false;
        last.put(reason, now);
        Deque<String> seen = DELIVERED.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        seen.addLast(reason);
        while (seen.size() > HISTORY) seen.removeFirst();
        Network.refused(player, line(reason, args));
        player.playNotifySound((danger ? ModSounds.FEEDBACK_DANGER : ModSounds.FEEDBACK_WARNING).get(), SoundSource.PLAYERS, 1, 1);
        return true;
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
        DELIVERED.remove(event.getEntity().getUUID());
    }
}

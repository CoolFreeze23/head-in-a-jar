package com.coolfreeze23.headinajar.feedback;

import com.coolfreeze23.headinajar.network.Network;
import com.coolfreeze23.headinajar.registry.ModSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Form 40-R: every "no" gets an answer. A refusal tells the player what is wrong and what to do next (an action-bar
 * line, or a line over the open screen), plays the refusal sound for everyone nearby, and lets the thing that refused
 * show it ({@link Refuser}). The same reason repeated within {@link #DEBOUNCE} ticks is answered once.
 * <p>
 * A reason is a short id such as {@code "jar.no_arm.block"}; its text is the lang key {@code refusal.headinajar.<id>},
 * with any arguments filled in. Something that stays true every tick (no legs, no power) is not a refusal: that belongs
 * on the HUD. Server side; the client twin is {@code client.ClientRefusals}.
 */
public final class Refusals {
    public static final String PREFIX = "refusal.headinajar.";
    public static final int DEBOUNCE = 10;
    private static final int HISTORY = 32;
    /** A bare jar's Nudge plays the refusal sound at most this often; the tink and the line answer every time. */
    private static final int NUDGE_QUIET = 60;
    private static final String NUDGE_SOUND = " nudge sound";

    /** What plays at the player's jar besides the refusal sound. */
    public enum Cue {
        /** Nothing extra. */
        PLAIN,
        /** A bare jar reaching for something: a glass tink and a few glass flecks. */
        NUDGE,
        /** A jar key with no module behind it: a burst of bubbles inside the jar. */
        GLUG
    }

    private static final Map<UUID, Map<String, Integer>> LAST = new HashMap<>();
    private static final Map<UUID, Deque<String>> DELIVERED = new HashMap<>();
    private static final Map<String, Cue> FROM_CLIENT = new HashMap<>();

    static {
        for (String reason : Nudge.REASONS) FROM_CLIENT.put(reason, Cue.NUDGE);
    }

    private Refusals() {
    }

    /** Refuses at the player. Always returns false, so a handler can {@code return Refusals.deny(...)}. */
    public static boolean deny(ServerPlayer player, String reason, Object... args) {
        deliver(player, null, Cue.PLAIN, reason, args);
        return false;
    }

    /** Refuses at the thing that said no ({@link Refuser#at} wraps a block entity or an entity). */
    public static boolean deny(ServerPlayer player, Refuser source, String reason, Object... args) {
        deliver(player, source, Cue.PLAIN, reason, args);
        return false;
    }

    /** A bare jar's refusal, the Nudge, with one of the {@link Nudge} reasons (their lines name the Manipulator Arm). */
    public static boolean nudge(ServerPlayer player, String reason) {
        deliver(player, null, Cue.NUDGE, reason, Nudge.args());
        return false;
    }

    /** A jar key pressed with no module to answer it: the Glug. */
    public static boolean glug(ServerPlayer player, String reason, Object... args) {
        deliver(player, null, Cue.GLUG, reason, args);
        return false;
    }

    /** The text of a refusal. */
    public static Component line(String reason, Object... args) {
        return Component.translatable(PREFIX + reason, args);
    }

    /**
     * Lets the client ask for this refusal, for input it cancels before the server hears of it. Only reasons
     * registered here are accepted from a client. They carry no arguments, except that a Nudge gets {@link Nudge#args()}.
     */
    public static void allowFromClient(String reason, Cue cue) {
        FROM_CLIENT.put(reason, cue);
    }

    /**
     * A refusal the client decided on (the payload's handler). {@code target} is the entity the player was aiming at,
     * or -1; a villager aimed at shakes its head.
     */
    public static void fromClient(ServerPlayer player, String reason, int target) {
        Cue cue = FROM_CLIENT.get(reason);
        if (cue == null) return;
        Entity entity = target >= 0 ? player.level().getEntity(target) : null;
        if (entity != null && entity.distanceToSqr(player) > 64) entity = null;
        Object[] args = cue == Cue.NUDGE ? Nudge.args() : new Object[0];
        if (deliver(player, null, cue, reason, args) && entity instanceof AbstractVillager villager) {
            villager.setUnhappyCounter(40);
            villager.playSound(SoundEvents.VILLAGER_NO, 1, villager.getVoicePitch());
        }
    }

    /** The reasons delivered to this player, oldest first (the last {@value #HISTORY}). For tests and commands. */
    public static List<String> delivered(ServerPlayer player) {
        Deque<String> seen = DELIVERED.get(player.getUUID());
        return seen == null ? List.of() : new ArrayList<>(seen);
    }

    /** The last reason delivered to this player, or null. */
    public static String lastDelivered(ServerPlayer player) {
        Deque<String> seen = DELIVERED.get(player.getUUID());
        return seen == null ? null : seen.peekLast();
    }

    /** Answers on every channel; false when the debounce swallowed it. */
    private static boolean deliver(ServerPlayer player, Refuser source, Cue cue, String reason, Object... args) {
        int now = player.server.getTickCount();
        Map<String, Integer> last = LAST.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
        Integer before = last.get(reason);
        if (before != null && now >= before && now - before < DEBOUNCE) return false;
        last.put(reason, now);
        Deque<String> seen = DELIVERED.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        seen.addLast(reason);
        while (seen.size() > HISTORY) seen.removeFirst();

        Network.refused(player, line(reason, args));
        ServerLevel level = player.serverLevel();
        var random = player.getRandom();
        Vec3 jar = player.position().add(0, Math.min(0.5, player.getBbHeight() * 0.5), 0);
        // a bare jar says it with the tink alone after the first time in a while; anything else says it every time
        boolean refusedSound = true;
        switch (cue) {
            case NUDGE -> {
                level.playSound(null, jar.x, jar.y, jar.z, ModSounds.JAR_TINK.get(), SoundSource.PLAYERS, 0.9f * (0.8f + 0.2f * random.nextFloat()), 0.94f + 0.12f * random.nextFloat());
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState()), jar.x, jar.y, jar.z, 6, 0.22, 0.2, 0.22, 0.05);
                Integer quiet = last.get(NUDGE_SOUND);
                refusedSound = quiet == null || now < quiet || now - quiet >= NUDGE_QUIET;
                if (refusedSound) last.put(NUDGE_SOUND, now);
            }
            case GLUG -> {
                float pitch = 0.95f + 0.1f * random.nextFloat();
                level.playSound(null, jar.x, jar.y, jar.z, ModSounds.JAR_GLUG.get(), SoundSource.PLAYERS, 1, pitch);
                level.playSound(null, jar.x, jar.y, jar.z, ModSounds.JAR_GLUG_TEXTURE.get(), SoundSource.PLAYERS, 0.5f, 0.9f + 0.2f * random.nextFloat());
                level.sendParticles(ParticleTypes.BUBBLE_POP, jar.x, jar.y - 0.1, jar.z, 10, 0.14, 0.12, 0.14, 0.03);
            }
            default -> {
            }
        }
        if (refusedSound) {
            Vec3 at = source != null ? source.refusalOrigin() : player.position().add(0, player.getBbHeight() * 0.5, 0);
            SoundSource category = source != null ? source.refusalSource() : SoundSource.PLAYERS;
            // after a glug the refusal comes a beat later, so the bubbles are heard first
            if (cue == Cue.GLUG) LATER.add(new Later(level, at, category, now + 2));
            else level.playSound(null, at.x, at.y, at.z, ModSounds.FEEDBACK_REFUSED.get(), category, 1, 1);
        }
        if (source != null) source.showRefusal(player, reason);
        return true;
    }

    private record Later(ServerLevel level, Vec3 at, SoundSource category, int tick) {
    }

    private static final List<Later> LATER = new ArrayList<>();

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        if (LATER.isEmpty()) return;
        int now = event.getServer().getTickCount();
        LATER.removeIf(l -> {
            if (now < l.tick() && now >= l.tick() - 2) return false;
            l.level().playSound(null, l.at().x, l.at().y, l.at().z, ModSounds.FEEDBACK_REFUSED.get(), l.category(), 1, 1);
            return true;
        });
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
        DELIVERED.remove(event.getEntity().getUUID());
    }
}

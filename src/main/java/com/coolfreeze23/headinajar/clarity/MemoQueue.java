package com.coolfreeze23.headinajar.clarity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DOLORES keeps to the house rules: at most one memo a minute to each reader, and none while a Separation Chair near
 * them is operating. A memo due sooner waits here and goes out on the reader's own tick once both are clear. Waiting
 * memos are dropped at logout and may be earned again later.
 */
public final class MemoQueue {
    /** Ticks between two memos to one reader. */
    public static final int SPACING = 1200;

    private record Pending(Memos.Memo memo, Object[] args) {
    }

    private static final Map<UUID, Long> LAST = new HashMap<>();
    private static final Map<UUID, Deque<Pending>> WAITING = new HashMap<>();

    private MemoQueue() {
    }

    private static long now(Player player) {
        return player.getServer() == null ? 0 : player.getServer().getTickCount();
    }

    /** Whether a memo for this player must wait now. */
    static boolean mustWait(ServerPlayer player) {
        Deque<Pending> q = WAITING.get(player.getUUID());
        if (q != null && !q.isEmpty()) return true;
        return !clear(player);
    }

    private static boolean clear(ServerPlayer player) {
        Long last = LAST.get(player.getUUID());
        long now = now(player);
        if (last != null && now >= last && now - last < SPACING) return false;
        return !com.coolfreeze23.headinajar.block.ChairQuiet.near(player);
    }

    static void hold(ServerPlayer player, Memos.Memo memo, Object... args) {
        WAITING.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>()).addLast(new Pending(memo, args));
    }

    static void delivered(ServerPlayer player) {
        LAST.put(player.getUUID(), now(player));
    }

    /** Tests: as if this player's last memo went out {@code ticks} ago. */
    public static void backdate(Player player, int ticks) {
        LAST.computeIfPresent(player.getUUID(), (id, t) -> t - ticks);
    }

    /** How many memos of this kind wait for this player (tests). */
    public static int waiting(Player player, Memos.Memo memo) {
        Deque<Pending> q = WAITING.get(player.getUUID());
        return q == null ? 0 : (int) q.stream().filter(p -> p.memo() == memo).count();
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Deque<Pending> q = WAITING.get(player.getUUID());
        if (q == null || q.isEmpty() || !clear(player)) return;
        Pending next = q.removeFirst();
        Memos.deliver(player, next.memo(), next.args());
    }

    /** Waiting memos are dropped at logout, and their bits cleared so they can be earned again. */
    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        Deque<Pending> q = WAITING.remove(player.getUUID());
        LAST.remove(player.getUUID());
        if (q == null || q.isEmpty()) return;
        CompoundTag kept = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        int bits = kept.getInt(Memos.SENT);
        for (Pending p : q) bits &= ~(1 << p.memo().ordinal());
        kept.putInt(Memos.SENT, bits);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, kept);
    }
}

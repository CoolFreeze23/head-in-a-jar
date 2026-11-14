package com.coolfreeze23.headinajar.client;

/**
 * Where another mod's overlay at the top centre of the screen (Jade's look-at box) was last drawn, so the link bar can
 * stand under it rather than over it. Told by the compat plugin each frame the box is drawn; forgotten after two ticks.
 */
public final class OverlayRoom {
    private static int x, y, width, height;
    private static long seenAt = Long.MIN_VALUE;

    private OverlayRoom() {
    }

    public static void seen(int bx, int by, int bw, int bh) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        x = bx;
        y = by;
        width = bw;
        height = bh;
        seenAt = mc.level == null ? 0 : mc.level.getGameTime();
    }

    /** The box drawn there now, as {x, y, width, height}; null when none has been drawn in the last two ticks. */
    public static int[] box() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null || mc.level.getGameTime() - seenAt > 2 || width <= 0 || height <= 0) return null;
        return new int[]{x, y, width, height};
    }

    /** The lowest y a strip from {@code left} to {@code right}, wanting to sit at {@code top}, must start at to clear the box. */
    public static int below(int left, int right, int top, int stripHeight) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null || mc.level.getGameTime() - seenAt > 2) return top;
        boolean across = left < x + width && right > x;
        boolean down = top < y + height && top + stripHeight > y;
        return across && down ? y + height + 2 : top;
    }
}

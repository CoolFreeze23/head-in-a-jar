package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The one column every side panel of the mod's HUD writes into: the host's, the pilot's and the gene keys. It lives in
 * the top-left corner, where vanilla draws nothing in play (chat, the hotbar, hearts and the action bar are all at the
 * bottom), and starts under the link bar when that is shown. Each line asks for its row and the column moves down, so
 * two panels or two lines can never be drawn on top of each other, whichever layer draws first. Reset every frame by
 * the first of the mod's layers ({@link ChargeHud#render}).
 */
public final class HudStack {
    public static final int X = 6;
    private static int y = -1, right;

    private HudStack() {
    }

    /** A new frame: the column is empty again. */
    public static void reset() {
        y = -1;
        right = 0;
    }

    private static int start() {
        var player = Minecraft.getInstance().player;
        if (player == null) return 6;
        JarState s = player.getData(ModRegistry.JAR_STATE);
        boolean linkBar = s.form() == Form.LINKED || s.form() == Form.JAR && !s.collar().isEmpty();
        return linkBar ? 34 : 6;
    }

    /** The top of the next row, {@code height} tall and {@code width} wide; the column moves down past it. */
    public static int row(int width, int height) {
        if (y < 0) y = start();
        int at = y;
        y += height;
        right = Math.max(right, X + width);
        return at;
    }

    /**
     * How wide the column may be: a third of the screen, so it stays clear of what vanilla and other mods put at the top
     * centre (boss bars, Jade's box). The lines are worded to fit; a longer one wraps.
     */
    public static int maxWidth(GuiGraphics g) {
        return Math.max(60, g.guiWidth() / 3 - X);
    }

    /** A line of text in the column, wrapped at the column's width. */
    public static void text(GuiGraphics g, Component line, int color) {
        Font font = Minecraft.getInstance().font;
        int max = maxWidth(g);
        if (font.width(line) <= max) {
            g.drawString(font, line, X, row(font.width(line), 10), color);
            return;
        }
        for (var part : font.split(line, max)) g.drawString(font, part, X, row(font.width(part), 10), color);
    }

    /** A bar with an optional label beside it; the bar gives up width before the label does. */
    public static void bar(GuiGraphics g, int width, float fraction, int color, int shade, Component label) {
        bar(g, width, fraction, color, shade, label, true);
    }

    /** As above; {@code lowIsBad} false for a bar where little is good (heat), so a nearly empty one is not drawn red. */
    public static void bar(GuiGraphics g, int width, float fraction, int color, int shade, Component label, boolean lowIsBad) {
        Font font = Minecraft.getInstance().font;
        int labelW = label == null ? 0 : 4 + font.width(label);
        width = Math.max(30, Math.min(width, maxWidth(g) - labelW));
        int at = row(width + labelW, 9);
        ChargeHud.drawBar(g, X, at + 1, width, fraction, color, shade, lowIsBad);
        if (label != null) g.drawString(font, label, X + width + 4, at, 0xC0C0C0);
    }

    /** A gap between two panels. */
    public static void gap() {
        if (y >= 0) y += 4;
    }

    /** How far down the column reaches this frame (0 when empty) and how far right. */
    public static int bottom() {
        return Math.max(0, y);
    }

    public static int right() {
        return right;
    }
}

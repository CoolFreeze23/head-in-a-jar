package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** The jar's charge, drawn where the hunger bar would be. */
public final class ChargeHud {
    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        HudStack.reset(); // the first of the mod's layers each frame
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) return;
        JarState state = player.getData(ModRegistry.JAR_STATE);
        if (!state.form().isJarred()) return;
        int right = graphics.guiWidth() / 2 + 91;
        int y = graphics.guiHeight() - mc.gui.rightHeight + 2;
        int width = 81;
        int left = right - width;
        float fraction = state.charge() / (float) state.chargeCapacity();
        float saline = state.saline() / (float) state.maxSaline(), glass = state.integrity() / (float) state.maxIntegrity();
        drawBar(graphics, left, y, width, fraction);
        icon(graphics, BOLT, left - 9, y - 1, 0xFF3FD8F0);
        mc.gui.rightHeight += 10;
        // saline (teal) and Jar Integrity (glass) under the charge, each with its own mark so no bar is only a colour
        int y2 = graphics.guiHeight() - mc.gui.rightHeight + 2;
        drawBar(graphics, left, y2, width, saline, 0xFF5FC8C0, 0xFF3A8A84);
        icon(graphics, DROP, left - 9, y2 - 1, 0xFF5FC8C0);
        if (saline < 0.15f) hatch(graphics, left, y2, width);
        mc.gui.rightHeight += 10;
        int y3 = graphics.guiHeight() - mc.gui.rightHeight + 2;
        drawBar(graphics, left, y3, width, glass, 0xFFD8F4FF, 0xFF8AB8D0);
        icon(graphics, PANE, left - 9, y3 - 1, 0xFFD8F4FF);
        if (glass < 0.15f) hatch(graphics, left, y3, width);
        mc.gui.rightHeight += 10;
        if (numbersShown(mc, state, Math.min(fraction, Math.min(saline, glass)))) {
            // each number just right of its own bar, past the hotbar's end: nothing of the jar's HUD reaches left of the
            // hotbar's centre, where a long chat line ends
            int[] rows = {y, y2, y3};
            String[] text = {Math.round(100 * fraction) + "%", Component.translatable("hud.headinajar.clarity.mb", state.saline()).getString(),
                    state.integrity() + "/" + state.maxIntegrity()};
            for (int i = 0; i < 3; i++) graphics.drawString(mc.font, text[i], right + 4, rows[i] - 1, 0xFFE0E0E0);
        }
        if (state.has(com.coolfreeze23.headinajar.module.ModuleType.LIFE_SIGN_MONITOR) && state.bodyMaxHealth() > 0) {
            String text = net.minecraft.network.chat.Component.translatable("hud.headinajar.life_signs",
                    Math.round(state.bodyHealth()), Math.round(state.bodyMaxHealth())).getString();
            graphics.drawString(mc.font, text, left, graphics.guiHeight() - mc.gui.rightHeight - 8, state.bodyHealth() > 0 ? 0x55FF77 : 0xFF5555);
        }
    }

    private static final String[] BOLT = {"...##", "..##.", ".####", "..##.", ".##..", "##..."},
            DROP = {"..#..", ".###.", "#####", "#####", ".###."},
            PANE = {"#####", "#..##", "#.#.#", "##..#", "#####"};

    /** A small mark drawn from a pixel map ('#' is lit): which bar is which, without a word to translate. */
    static void icon(GuiGraphics g, String[] map, int x, int y, int color) {
        for (int r = 0; r < map.length; r++) {
            for (int c = 0; c < map[r].length(); c++) {
                if (map[r].charAt(c) == '#') g.fill(x + c, y + r, x + c + 1, y + r + 1, color);
            }
        }
    }

    private static int lastSum = -1, shownTicks;

    /** The numbers show for three seconds after a change, while low, while sneaking, or always with the option on. */
    static boolean numbersShown(Minecraft mc, JarState state, float lowest) {
        int sum = state.charge() / 1000 + state.saline() + state.integrity() * 7919;
        if (sum != lastSum) {
            if (lastSum != -1) shownTicks = 60;
            lastSum = sum;
        }
        return shownTicks > 0 || lowest < 0.25f || mc.options.keyShift.isDown()
                || com.coolfreeze23.headinajar.Config.CLIENT_SPEC.isLoaded() && com.coolfreeze23.headinajar.Config.HUD_NUMBERS.get();
    }

    /** Called every client tick. */
    public static void tick() {
        if (shownTicks > 0) shownTicks--;
    }

    /** A segmented cyan bar with a dark frame; red when nearly empty. */
    public static void drawBar(GuiGraphics graphics, int left, int y, int width, float fraction, int color, int shade) {
        drawBar(graphics, left, y, width, fraction, color, shade, true);
    }

    /** As above; {@code lowIsBad} false for a bar where little is good (heat): it keeps its own colour when nearly empty. */
    public static void drawBar(GuiGraphics graphics, int left, int y, int width, float fraction, int color, int shade, boolean lowIsBad) {
        graphics.fill(left - 1, y - 1, left + width + 1, y + 6, 0xFF101418);
        graphics.fill(left, y, left + width, y + 5, 0xFF26323A);
        int filled = Math.round(width * Math.max(0, Math.min(1, fraction)));
        boolean low = lowIsBad && fraction < 0.15f;
        graphics.fill(left, y, left + filled, y + 5, low ? 0xFFE04848 : color);
        graphics.fill(left, y + 3, left + filled, y + 5, low ? 0xFF9A2E2E : shade);
        // (no marks here: this bar is also heat and health, where little is not "low")
    }

    /** Diagonal marks across a low bar's empty part, so "low" reads without its red. */
    static void hatch(GuiGraphics g, int left, int y, int width) {
        for (int x = left; x < left + width; x += 4) {
            for (int r = 0; r < 5; r++) {
                int px = x + (4 - r) % 4;
                if (px < left + width) g.fill(px, y + r, px + 1, y + r + 1, 0x80E04848);
            }
        }
    }

    public static void drawBar(GuiGraphics graphics, int left, int y, int width, float fraction) {
        graphics.fill(left - 1, y - 1, left + width + 1, y + 6, 0xFF101418);
        graphics.fill(left, y, left + width, y + 5, 0xFF26323A);
        int filled = Math.round(width * Math.max(0, Math.min(1, fraction)));
        int color = fraction < 0.15f ? 0xFFE04848 : 0xFF3FD8F0;
        int shade = fraction < 0.15f ? 0xFF9A2E2E : 0xFF1F9AB8;
        graphics.fill(left, y, left + filled, y + 5, color);
        graphics.fill(left, y + 3, left + filled, y + 5, shade);
        if (fraction < 0.15f) hatch(graphics, left, y, width);
        for (int x = left + 9; x < left + width; x += 9) graphics.fill(x, y, x + 1, y + 5, 0x60101418);
    }

    private ChargeHud() {
    }
}

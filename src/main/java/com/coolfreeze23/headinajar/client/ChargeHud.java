package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

/** The jar's charge, drawn where the hunger bar would be. */
public final class ChargeHud {
    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) return;
        JarState state = player.getData(ModRegistry.JAR_STATE);
        if (!state.form().isJarred()) return;
        int right = graphics.guiWidth() / 2 + 91;
        int y = graphics.guiHeight() - mc.gui.rightHeight + 2;
        int width = 81;
        int left = right - width;
        float fraction = state.charge() / (float) JarState.maxCharge();
        drawBar(graphics, left, y, width, fraction);
        mc.gui.rightHeight += 10;
        // saline (teal) and Jar Integrity (glass) under the charge
        int y2 = graphics.guiHeight() - mc.gui.rightHeight + 2;
        drawBar(graphics, left, y2, width, state.saline() / (float) state.maxSaline(), 0xFF5FC8C0, 0xFF3A8A84);
        mc.gui.rightHeight += 10;
        int y3 = graphics.guiHeight() - mc.gui.rightHeight + 2;
        drawBar(graphics, left, y3, width, state.integrity() / (float) state.maxIntegrity(), 0xFFD8F4FF, 0xFF8AB8D0);
        mc.gui.rightHeight += 10;
        if (state.has(com.coolfreeze23.headinajar.module.ModuleType.LIFE_SIGN_MONITOR) && state.bodyMaxHealth() > 0) {
            String text = net.minecraft.network.chat.Component.translatable("hud.headinajar.life_signs",
                    Math.round(state.bodyHealth()), Math.round(state.bodyMaxHealth())).getString();
            graphics.drawString(mc.font, text, left, graphics.guiHeight() - mc.gui.rightHeight - 8, state.bodyHealth() > 0 ? 0x55FF77 : 0xFF5555);
        }
    }

    /** A segmented cyan bar with a dark frame; red when nearly empty. */
    public static void drawBar(GuiGraphics graphics, int left, int y, int width, float fraction, int color, int shade) {
        graphics.fill(left - 1, y - 1, left + width + 1, y + 6, 0xFF101418);
        graphics.fill(left, y, left + width, y + 5, 0xFF26323A);
        int filled = Math.round(width * Math.max(0, Math.min(1, fraction)));
        boolean low = fraction < 0.15f;
        graphics.fill(left, y, left + filled, y + 5, low ? 0xFFE04848 : color);
        graphics.fill(left, y + 3, left + filled, y + 5, low ? 0xFF9A2E2E : shade);
    }

    public static void drawBar(GuiGraphics graphics, int left, int y, int width, float fraction) {
        graphics.fill(left - 1, y - 1, left + width + 1, y + 6, 0xFF101418);
        graphics.fill(left, y, left + width, y + 5, 0xFF26323A);
        int filled = Math.round(width * Math.max(0, Math.min(1, fraction)));
        int color = fraction < 0.15f ? 0xFFE04848 : 0xFF3FD8F0;
        int shade = fraction < 0.15f ? 0xFF9A2E2E : 0xFF1F9AB8;
        graphics.fill(left, y, left + filled, y + 5, color);
        graphics.fill(left, y + 3, left + filled, y + 5, shade);
        for (int x = left + 9; x < left + width; x += 9) graphics.fill(x, y, x + 1, y + 5, 0x60101418);
    }

    private ChargeHud() {
    }
}

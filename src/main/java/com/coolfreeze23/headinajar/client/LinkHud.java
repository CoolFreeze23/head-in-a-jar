package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.SeparationChairBlockEntity;
import com.coolfreeze23.headinajar.entity.SeatEntity;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * The neuro-link HUD: signal bar, scanlines and static that grow as the signal weakens, warning banners, the static
 * wipe when swapping, and what the patient sees while the Separation Chair works.
 */
public final class LinkHud {
    private static final ResourceLocation SCANLINES = HeadInAJar.id("textures/gui/scanlines.png");
    private static final ResourceLocation STATIC = HeadInAJar.id("textures/gui/static.png");
    private static Form lastForm = Form.BODY;
    private static int wipeTicks;
    private static int lastQuality = 100;
    private static int barTicks;

    /** Called every client tick. */
    public static void tick(Player player) {
        JarState state = player.getData(ModRegistry.JAR_STATE);
        if (state.form() != lastForm) {
            if (lastForm.isDecapitated() || state.form().isDecapitated()) wipeTicks = 10;
            lastForm = state.form();
            barTicks = 100;
        }
        if (Math.abs(state.quality() - lastQuality) >= 5) {
            lastQuality = state.quality();
            barTicks = 100;
        }
        if (wipeTicks > 0) wipeTicks--;
        if (barTicks > 0) barTicks--;
    }

    private static boolean photosensitive() {
        return Config.CLIENT_SPEC.isLoaded() && Config.PHOTOSENSITIVE.get();
    }

    private static float intensity() {
        return Config.CLIENT_SPEC.isLoaded() ? Config.STATIC_INTENSITY.get() / 100f : 1f;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        int w = g.guiWidth(), h = g.guiHeight();
        if (player.getVehicle() instanceof SeatEntity seat && seat.chair() != null && seat.chair().running()) {
            boothView(g, seat.chair(), w, h, delta.getGameTimeDeltaPartialTick(false));
            return;
        }
        JarState state = player.getData(ModRegistry.JAR_STATE);
        if (state.form() == Form.LINKED && !mc.options.hideGui) {
            linkedOverlay(g, mc, state, w, h);
        }
        if ((state.form() == Form.LINKED || state.form() == Form.JAR && !state.collar().isEmpty()) && !mc.options.hideGui) {
            topBar(g, mc, state, w);
        }
        if (wipeTicks > 0) {
            float a = wipeTicks / 10f;
            if (photosensitive()) g.fill(0, 0, w, h, ((int) (a * 200) << 24));
            else noise(g, w, h, a * intensity(), true);
        }
    }

    private static void linkedOverlay(GuiGraphics g, Minecraft mc, JarState state, int w, int h) {
        int q = state.quality();
        float k = intensity();
        // faint scanlines at full signal, heavier as it drops
        float lines = (0.08f + (100 - q) * 0.006f) * k;
        if (!photosensitive() && lines > 0.01f) tile(g, SCANLINES, w, h, lines, 0, mc.level.getGameTime() % 64);
        if (q < 40 && !photosensitive()) noise(g, w, h, (40 - q) / 40f * 0.7f * k, false);
        if (q < 20) {
            String key = q == 0 ? "hud.headinajar.lost" : "hud.headinajar.weak";
            boolean show = photosensitive() || (mc.level.getGameTime() / 10) % 2 == 0;
            if (show) {
                Component text = Component.translatable(key).withStyle(q == 0 ? ChatFormatting.RED : ChatFormatting.YELLOW, ChatFormatting.BOLD);
                g.drawCenteredString(mc.font, text, w / 2, h / 3, 0xFFFFFF);
            }
        }
        // the botched cut: a dark stripe where the crooked collar sees its own housing
        if (state.rollTicks() > 0) g.fill(0, h - 14, w, h, 0x60000000);
    }

    private static void topBar(GuiGraphics g, Minecraft mc, JarState state, int w) {
        int q = state.quality();
        boolean always = q < 70 || state.form() == Form.JAR;
        if (!always && barTicks <= 0) return;
        float alpha = always ? 1 : Math.min(1, barTicks / 20f);
        int a = (int) (alpha * 255) << 24;
        int x = w / 2 - 90, y = 4;
        g.fill(x, y, x + 180, y + 12, (int) (alpha * 0x90) << 24);
        int bars = q >= 80 ? 5 : q >= 60 ? 4 : q >= 40 ? 3 : q >= 20 ? 2 : q > 0 ? 1 : 0;
        int color = q >= 60 ? 0x55FF77 : q >= 20 ? 0xFFD040 : 0xFF4040;
        for (int i = 0; i < 5; i++) {
            int bh = 2 + i * 2;
            g.fill(x + 4 + i * 4, y + 10 - bh, x + 7 + i * 4, y + 10, (i < bars ? color : 0x404040) | a);
        }
        String dist = state.headDistance() < 0 ? "--" : String.valueOf(Math.round(state.headDistance()));
        int ping = state.headDistance() < 0 ? 999 : 5 + Math.round(state.headDistance() / 8);
        String label = state.form() == Form.LINKED ? "hud.headinajar.head" : "hud.headinajar.body";
        Component text = Component.translatable("hud.headinajar.bar", q, ping, Component.translatable(label), dist);
        g.drawString(mc.font, text, x + 28, y + 2, 0xE0FFFFFF & 0xFFFFFF | a, false);
        if (state.carryingHead()) g.drawString(mc.font, Component.translatable("hud.headinajar.carried"), x + 140, y + 2, 0x55FF77 | a, false);
    }

    /** What the patient sees during the operation. */
    private static void boothView(GuiGraphics g, SeparationChairBlockEntity chair, int w, int h, float partial) {
        Minecraft mc = Minecraft.getInstance();
        float t = chair.opTick() + partial;
        if (t < 20) {
            vignette(g, w, h, t / 20f * 0.6f);
        } else if (t < 60) {
            vignette(g, w, h, 0.6f);
            g.fill(0, 0, w, h, ((int) (Math.min(1, (t - 20) / 20f) * 110) << 24) | 0x0A2A12);
        } else if (t < 100) {
            g.fill(0, 0, w, h, 0x6E0A2A12);
            vignette(g, w, h, 0.6f);
            int line = (int) ((t - 60) / 40f * h);
            g.fill(0, line - 1, w, line + 2, 0xC040FF60);
            g.fill(0, line - 6, w, line - 1, 0x3040FF60);
        } else if (t < 120) {
            g.fill(0, 0, w, h, ((int) Mth.clamp((t - 100) / 20f * 255, 110, 255) << 24));
        } else if (t < 210) {
            g.fill(0, 0, w, h, 0xFF000000);
            if (t > 140 && t < 180 && !photosensitive()) {
                g.drawCenteredString(mc.font, Component.translatable("hud.headinajar.bubbles").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), w / 2, h / 2, 0x606060);
            }
        } else {
            float clear = (t - 210) / 30f;
            g.fill(0, 0, w, h, ((int) ((1 - clear) * 255) << 24));
            if (!photosensitive()) noise(g, w, h, (1 - clear) * intensity(), true);
            g.drawCenteredString(mc.font, Component.translatable("hud.headinajar.boot", Math.min(100, Math.round(clear * 100))), w / 2, h / 2 + 20, 0x55FF77);
        }
    }

    private static void vignette(GuiGraphics g, int w, int h, float strength) {
        int a = (int) (strength * 255);
        int band = Math.max(1, (int) (Math.min(w, h) * 0.25f * strength));
        for (int i = 0; i < band; i++) {
            int alpha = (int) (a * (1 - i / (float) band)) << 24;
            g.fill(0, i, w, i + 1, alpha);
            g.fill(0, h - i - 1, w, h - i, alpha);
            g.fill(i, 0, i + 1, h, alpha);
            g.fill(w - i - 1, 0, w - i, h, alpha);
        }
    }

    /** Snow: the static texture at a random offset each frame. */
    private static void noise(GuiGraphics g, int w, int h, float alpha, boolean full) {
        if (alpha <= 0.01f) return;
        var random = Minecraft.getInstance().level == null ? new java.util.Random() : new java.util.Random(System.nanoTime());
        tile(g, STATIC, w, h, Math.min(1, alpha), random.nextInt(64), random.nextInt(64));
        if (full) tile(g, STATIC, w, h, Math.min(1, alpha) * 0.6f, random.nextInt(64), random.nextInt(64));
    }

    private static void tile(GuiGraphics g, ResourceLocation tex, int w, int h, float alpha, float u, float v) {
        RenderSystem.enableBlend();
        g.setColor(1, 1, 1, alpha);
        // textures clamp at the edge, so tile by hand from a shifted origin
        int ox = -(int) u, oy = -(int) v;
        for (int x = ox; x < w; x += 64) {
            for (int y = oy; y < h; y += 64) g.blit(tex, x, y, 0, 0, 64, 64, 64, 64);
        }
        g.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    private LinkHud() {
    }
}

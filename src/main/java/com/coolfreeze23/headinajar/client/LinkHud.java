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

    /** Ticks left of the link bar's "lost" flash, after a click the weak link dropped. */
    private static int lossTicks;
    /** How many dropped clicks this session were explained in a line; after a few the flash says it alone. */
    private static int lossExplained;
    public static final int LOSS_EXPLAINED_TIMES = 3;

    /**
     * A click the weak link dropped: the link bar flashes it at once, and the first few times a line says the link is weak
     * and what makes it stronger.
     */
    public static void packetLost(Player player, JarState state) {
        lossTicks = 20;
        barTicks = Math.max(barTicks, 40);
        if (lossExplained < LOSS_EXPLAINED_TIMES) {
            lossExplained++;
            player.displayClientMessage(Component.translatable("hud.headinajar.packet_loss.why", state.quality(),
                    Component.translatable("item.headinajar.head_satchel"), Component.translatable("block.headinajar.relay_mast")), true);
        } else {
            player.displayClientMessage(Component.translatable("hud.headinajar.packet_loss").withStyle(ChatFormatting.RED), true);
        }
    }

    /** Whether the link bar is flashing a dropped click (for the HUD and tests). */
    public static boolean flashingLoss() {
        return lossTicks > 0;
    }

    /** Called every client tick. */
    public static void tick(Player player) {
        if (lossTicks > 0) lossTicks--;
        JarState state = player.getData(ModRegistry.JAR_STATE);
        if (state.form() != lastForm) {
            if (lastForm.isDecapitated() || state.form().isDecapitated()) {
                wipeTicks = 10;
                // the commute: the old view pulls back and up, one burst of static, the new view drops into place
                com.coolfreeze23.headinajar.client.body.CommuteFx.start(state.form() == Form.LINKED || state.form() == Form.BODY);
                // under the photosensitive option the wipe is a plain crossfade from the old view's last frame,
                // kept when the new form arrived (SwapFx.formChanging, from the sync packet)
            }
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

    static boolean photosensitive() {
        return Config.CLIENT_SPEC.isLoaded() && Config.PHOTOSENSITIVE.get();
    }

    private static float intensity() {
        float base = Config.CLIENT_SPEC.isLoaded() ? Config.STATIC_INTENSITY.get() / 100f : 1f;
        var player = net.minecraft.client.Minecraft.getInstance().player;
        boolean filter = player != null && com.coolfreeze23.headinajar.item.QuantumItems.has(player, com.coolfreeze23.headinajar.item.QuantumItems.Upgrade.NOISE_FILTER);
        return filter ? base * 0.5f : base;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        int w = g.guiWidth(), h = g.guiHeight();
        if (player.getVehicle() instanceof SeatEntity seat && seat.chair() != null && seat.chair().running()) {
            boothView(g, seat.chair(), w, h);
            return;
        }
        JarState state = player.getData(ModRegistry.JAR_STATE);
        if (state.form() == Form.LINKED && !mc.options.hideGui) {
            linkedOverlay(g, mc, state, w, h);
        }
        if ((state.form() == Form.LINKED || state.form() == Form.JAR && !state.collar().isEmpty()) && !mc.options.hideGui) {
            topBar(g, mc, state, w);
        }
        if (state.form() == Form.LINKED && !state.carryingHead() && state.headAt() != JarState.HEAD_UNKNOWN && !mc.options.hideGui) {
            headPill(g, mc, state, w);
        }
        if (wipeTicks > 0) {
            float a = wipeTicks / 10f;
            if (photosensitive()) com.coolfreeze23.headinajar.client.fx.SwapFx.renderCrossfade(g, delta.getGameTimeDeltaPartialTick(false));
            else {
                com.coolfreeze23.headinajar.client.body.CommuteFx.render(g, delta.getGameTimeDeltaPartialTick(false), wipeTicks);
                noise(g, w, h, com.coolfreeze23.headinajar.client.body.CommuteFx.staticAlpha(wipeTicks, a) * intensity(), true);
                com.coolfreeze23.headinajar.client.body.CommuteFx.band(g, delta.getGameTimeDeltaPartialTick(false), wipeTicks);
            }
        }
    }

    private static void linkedOverlay(GuiGraphics g, Minecraft mc, JarState state, int w, int h) {
        int q = state.quality();
        float k = intensity();
        // faint scanlines at full signal, heavier as it drops
        float lines = (0.08f + (100 - q) * 0.006f) * k;
        // both stand clear of Jade's look-at box when it is showing at the top, so its lines stay readable
        if (!photosensitive() && lines > 0.01f) tileAround(g, SCANLINES, w, h, lines, 0, mc.level.getGameTime() % 64);
        if (q < 40 && !photosensitive() && (40 - q) / 40f * 0.7f * k > 0.01f) {
            var random = new java.util.Random(System.nanoTime());
            tileAround(g, STATIC, w, h, Math.min(1, (40 - q) / 40f * 0.7f * k), random.nextInt(64), random.nextInt(64));
        }
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

    /** Where the link bar was drawn this frame (the head pill sits beside it, or under it). */
    private static int barY = 4;

    private static boolean pillShown(JarState state) {
        return state.form() == Form.LINKED && !state.carryingHead() && state.headAt() != JarState.HEAD_UNKNOWN;
    }

    private static void topBar(GuiGraphics g, Minecraft mc, JarState state, int w) {
        int q = state.quality();
        // a head left out in the world keeps the bar up: its distance, the chevron and the pill beside it belong together
        boolean always = q < 70 || state.form() == Form.JAR
                || state.form() == Form.LINKED && !state.carryingHead() && state.headAt() != JarState.HEAD_UNKNOWN;
        if (!always && barTicks <= 0) return;
        float alpha = always ? 1 : Math.min(1, barTicks / 20f);
        int a = (int) (alpha * 255) << 24;
        // under Jade's look-at box when it is showing there, never across it (the chevron left of the bar, the pill right)
        int x = w / 2 - 90, y = OverlayRoom.below(x - 12, x + 180 + (pillShown(state) ? 90 : 0), 4, 12);
        barY = y;
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
        // after the text, wherever it ends (it is longer in some languages), never on top of it
        if (state.carryingHead()) g.drawString(mc.font, Component.translatable("hud.headinajar.carried"), Math.min(x + 176 - mc.font.width(Component.translatable("hud.headinajar.carried")),
                Math.max(x + 140, x + 32 + mc.font.width(text))), y + 2, 0x55FF77 | a, false);
        // which way the other half is, when this client can see it: just left of the bar
        var other = com.coolfreeze23.headinajar.clarity.Wayfinding.otherHalf(mc.player, state.form() == Form.LINKED);
        String arrow = other == null ? null : com.coolfreeze23.headinajar.clarity.Wayfinding.chevron(mc.player, other.position());
        if (arrow != null) {
            g.fill(x - 12, y, x - 1, y + 12, (int) (alpha * 0x90) << 24);
            g.drawCenteredString(mc.font, arrow, x - 6, y + 2, 0x55FF77 | a);
        }
        if (lossTicks > 0) {
            // a dropped click: the bar's frame goes red and says so, under the bar
            int red = ((lossTicks / 3) % 2 == 0 ? 0xFF4040 : 0xB02020) | a;
            g.fill(x, y, x + 180, y + 1, red);
            g.fill(x, y + 11, x + 180, y + 12, red);
            g.fill(x, y, x + 1, y + 12, red);
            g.fill(x + 179, y, x + 180, y + 12, red);
            Component lost = Component.translatable("hud.headinajar.packet_lost").withStyle(ChatFormatting.BOLD);
            g.drawCenteredString(mc.font, lost, w / 2, y + 14, 0xFF5050);
        }
    }

    /**
     * The head pill: a linked body's view of the head it left behind, its saline and its glass. A placed head drying out
     * is the one way a linked player dies for real, so this is always shown while the head is away. Right of the link
     * bar, or under it when the screen is too narrow (a large GUI scale).
     */
    private static void headPill(GuiGraphics g, Minecraft mc, JarState state, int w) {
        boolean docked = state.headAt() == JarState.HEAD_DOCKED;
        Component label = Component.translatable(docked ? "hud.headinajar.pill.docked" : "hud.headinajar.pill.placed");
        int barW = 24, pad = 3;
        int pw = pad + mc.font.width(label) + 4 + barW + pad;
        int barX = w / 2 - 90;
        int x = barX + 184, y = barY;
        if (x + pw > w - 2) {
            x = Math.max(2, w / 2 - pw / 2);
            y = barY + 14;
        }
        float saline = state.saline() / (float) Math.max(1, state.maxSaline());
        float glass = state.integrity() / (float) Math.max(1, state.maxIntegrity());
        // a placed head is drying: low saline goes red, steady (no blinking)
        boolean dry = !docked && saline < 0.1f;
        g.fill(x, y, x + pw, y + 12, 0x90000000);
        g.drawString(mc.font, label, x + pad, y + 2, dry ? 0xFF5050 : 0xE0E0E0, false);
        int bx = x + pad + mc.font.width(label) + 4;
        bar(g, bx, y + 2, barW, saline, dry ? 0xFF4040 : 0x50A8FF);
        bar(g, bx, y + 7, barW, glass, glass < 0.3f ? 0xFF4040 : 0xD8E8F0);
    }

    private static void bar(GuiGraphics g, int x, int y, int width, float fill, int color) {
        g.fill(x, y, x + width, y + 3, 0xFF303030);
        int f = Math.round(Mth.clamp(fill, 0, 1) * width);
        if (f > 0) g.fill(x, y, x + f, y + 3, 0xFF000000 | color);
    }

    /** What the patient sees during the operation, on the chair's own clock (the machine's animation reads it too). */
    private static void boothView(GuiGraphics g, SeparationChairBlockEntity chair, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        float t = ChairClock.elapsed(chair);
        if (t < 20) {
            vignette(g, w, h, t / 20f * 0.6f);
        } else if (t < 60) {
            vignette(g, w, h, 0.6f);
            g.fill(0, 0, w, h, ((int) (Math.min(1, (t - 20) / 20f) * 110) << 24) | 0x0A2A12);
        } else if (t < 100) {
            g.fill(0, 0, w, h, 0x6E0A2A12);
            vignette(g, w, h, 0.6f);
            // the line is where the scan ring is: read from the ring as the chair's animation placed it this frame
            int line = (int) (SeparationChairRenderer.scanLine(t) * h);
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
        // the textures repeat (no clamp in their metadata), so one quad covers the screen: one draw per layer
        g.blit(tex, 0, 0, u, v, w, h, 64, 64);
        g.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    /** As {@link #tile}, but around another mod's box at the top (Jade's): the screen in up to four bands, the box left clear. */
    private static void tileAround(GuiGraphics g, ResourceLocation tex, int w, int h, float alpha, float u, float v) {
        int[] box = OverlayRoom.box();
        if (box == null) {
            tile(g, tex, w, h, alpha, u, v);
            return;
        }
        int bx = Math.max(0, box[0]), by = Math.max(0, box[1]), bx2 = Math.min(w, box[0] + box[2]), by2 = Math.min(h, box[1] + box[3]);
        RenderSystem.enableBlend();
        g.setColor(1, 1, 1, alpha);
        band(g, tex, 0, 0, w, by, u, v);
        band(g, tex, 0, by2, w, h - by2, u, v);
        band(g, tex, 0, by, bx, by2 - by, u, v);
        band(g, tex, bx2, by, w - bx2, by2 - by, u, v);
        g.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    /** One band of a repeating texture, its pattern lined up with the rest of the screen's. */
    private static void band(GuiGraphics g, ResourceLocation tex, int x, int y, int width, int height, float u, float v) {
        if (width > 0 && height > 0) g.blit(tex, x, y, u + x, v + y, width, height, 64, 64);
    }

    private LinkHud() {
    }
}

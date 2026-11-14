package com.coolfreeze23.headinajar.client.body;

import com.coolfreeze23.headinajar.client.fx.CameraDesk;
import com.coolfreeze23.headinajar.client.fx.SwapFx;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;

/**
 * The Commute, for the one swapping: the old view pulls back and up out of the screen, one burst of static, and the new
 * view drops in from a little above with the field of view easing back. The wipe stays 10 ticks and control passes at
 * once (the server swaps on the key). A band of light sweeps up the screen going out into the body and down going
 * back into the glass, with the rising or falling static sound. Under the photosensitive option none of this plays:
 * the swap is the plain crossfade.
 */
public final class CommuteFx {
    private static boolean up;

    /** The swap has happened; {@code toBody} when the mind went out into a body. */
    public static void start(boolean toBody) {
        up = toBody;
        // the new view arrives wide and from slightly above, and settles (both x the screen-shake setting)
        CameraDesk.fov(10, 1, 9);
        CameraDesk.kick(-4, 1, 9);
    }

    private static float elapsed(int wipeTicks, float pt) {
        return 10 - wipeTicks + pt;
    }

    /** One burst of static in the middle of the wipe (instead of a flat fade from full). */
    public static float staticAlpha(int wipeTicks, float linear) {
        float e = 10 - wipeTicks;
        return 0.85f * Mth.sin(Mth.clamp((e + 0.5f) / 8f, 0, 1) * Mth.PI);
    }

    /** The old view's last frame, shrinking and rising away over the first five ticks. */
    public static void render(GuiGraphics g, float pt, int wipeTicks) {
        var last = SwapFx.lastFrame();
        float e = elapsed(wipeTicks, pt);
        if (last == null || e >= 5) return;
        float u = Mth.clamp(e / 5f, 0, 1);
        float ease = u * u * u;
        float scale = 1 - 0.45f * ease, a = 1 - u * u;
        int w = g.guiWidth(), h = g.guiHeight();
        float cx = w / 2f, cy = h / 2f - h * 0.3f * ease;
        float hw = w / 2f * scale, hh = h / 2f * scale;
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, last.getColorTextureId());
        RenderSystem.setShaderColor(1, 1, 1, a);
        var mat = g.pose().last().pose();
        var b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.addVertex(mat, cx - hw, cy + hh, 0).setUv(0, 0);
        b.addVertex(mat, cx + hw, cy + hh, 0).setUv(1, 0);
        b.addVertex(mat, cx + hw, cy - hh, 0).setUv(1, 1);
        b.addVertex(mat, cx - hw, cy - hh, 0).setUv(0, 1);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    /** A pale teal band sweeping the screen: up into the body, down into the jar. */
    public static void band(GuiGraphics g, float pt, int wipeTicks) {
        float u = Mth.clamp(elapsed(wipeTicks, pt) / 10f, 0, 1);
        int h = g.guiHeight(), w = g.guiWidth();
        float s = u * u * (3 - 2 * u);
        int y = Math.round(up ? h * (1 - s) : h * s);
        int a = (int) (Mth.sin(u * Mth.PI) * 150);
        if (a <= 2) return;
        g.fill(0, y - 1, w, y + 1, a << 24 | 0xbeede1);
        g.fill(0, up ? y + 1 : y - 6, w, up ? y + 6 : y - 1, (a / 3) << 24 | 0x86cac1);
    }

    private CommuteFx() {
    }
}

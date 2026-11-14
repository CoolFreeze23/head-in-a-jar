package com.coolfreeze23.headinajar.client.fx;

import com.coolfreeze23.headinajar.fx.Fx;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

/**
 * The swap made visible. A bystander sees a teal mote leave the goggles and arc to the collar, or back (straight up and
 * away when the other end is over 32 blocks off), a ring at each end, the body left behind power down and the one
 * entered shake awake. The doer keeps the 10-tick static wipe; under the photosensitive option that wipe is a plain
 * crossfade from the last frame of the old view.
 */
public final class SwapFx {
    private record Trip(Vec3 from, Vec3 to, long start, int length, boolean away) {
    }

    private static final List<Trip> TRIPS = new ArrayList<>();
    private static RenderTarget last;
    private static int fadeTicks;

    static void handle(Fx fx) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        long now = level.getGameTime();
        int dir = Math.round(fx.arg());
        boolean arriveOnly = dir == 2;
        if (!arriveOnly) {
            ring(fx.a(), 0.35f);
            boolean away = dir == 0;
            double d = fx.a().distanceTo(fx.b());
            int length = away ? 16 : Mth.clamp((int) (10 + d * 0.5), 10, 20);
            TRIPS.add(new Trip(fx.a(), away ? fx.a().add(0, 12, 0) : fx.b(), now, length, away));
            if (!away) FxManager.later(length, () -> ring(fx.b(), 0.4f));
        } else {
            ring(fx.b(), 0.4f);
        }
        // entityA: the body left behind (powers down); entityB: the body entered (shakes awake)
        if (fx.entityA() >= 0) PoseFx.powerDown(fx.entityA());
        if (fx.entityB() >= 0) {
            int delay = arriveOnly || dir == 0 ? 0 : Mth.clamp((int) (10 + fx.a().distanceTo(fx.b()) * 0.5), 10, 20);
            FxManager.later(delay, () -> PoseFx.wake(fx.entityB()));
        }
    }

    private static void ring(Vec3 at, float size) {
        FxParticle r = FxParticle.make(FxParticle.Look.RING, at, Vec3.ZERO);
        if (r != null) r.color(FxColors.SIGNAL_PALE).size(size).grow(1.8f).life(8).add();
    }

    private static Vec3 at(Trip t, float time) {
        float u = Mth.clamp(time / t.length, 0, 1);
        if (t.away) return t.from.lerp(t.to, u * u);
        double arc = Math.min(6, 1 + t.from.distanceTo(t.to) * 0.25);
        return t.from.lerp(t.to, u * u * (3 - 2 * u)).add(0, Math.sin(Math.PI * u) * arc, 0);
    }

    public static void tick() {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            TRIPS.clear();
            return;
        }
        long now = level.getGameTime();
        for (Trip t : TRIPS) {
            float time = now - t.start;
            // a six-mote trail behind the head of the mote
            FxParticle m = FxParticle.make(FxParticle.Look.MOTE, at(t, time), Vec3.ZERO);
            if (m != null) m.color(FxColors.SIGNAL).size(0.08f).life(6).add();
        }
        TRIPS.removeIf(t -> now - t.start > t.length);
        if (fadeTicks > 0) fadeTicks--;
    }

    /** The mote itself is drawn as a glow, not a particle, so it shows under every particle setting. */
    public static void render(Beams.Frame f) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        for (Trip t : TRIPS) {
            float time = level.getGameTime() - t.start + f.partialTick();
            Vec3 p = at(t, time);
            float a = t.away ? 1 - Mth.clamp(time / t.length, 0, 1) : 1;
            Beams.bead(f, p, FxColors.SIGNAL_PALE, 0.1f, a);
            Beams.bead(f, p, FxColors.SIGNAL_DARK, 0.22f, a * 0.35f);
        }
    }

    // --- the doer's crossfade under the photosensitive option -----------------------------------------------------

    /**
     * Called as the local player's new form arrives, before anything is drawn in it: under the photosensitive option,
     * when the swap's wipe would play, keeps the last frame of the old view to crossfade from.
     */
    public static void formChanging(com.coolfreeze23.headinajar.form.Form from, com.coolfreeze23.headinajar.form.Form to) {
        // the last frame of the old view: crossfaded from under the photosensitive option, else pulled back from
        if (from.isDecapitated() || to.isDecapitated()) captureForCrossfade();
    }

    /** The old view's last frame, kept as the form changed (null before the first swap). */
    public static RenderTarget lastFrame() {
        return last;
    }

    /** Keeps the last frame of the old view to fade out of. */
    public static void captureForCrossfade() {
        var mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        if (last == null || last.width != main.width || last.height != main.height) {
            if (last != null) last.destroyBuffers();
            last = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, last.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, last.width, last.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        fadeTicks = 10;
    }

    /** Draws the old view over the new one, fading out over 10 ticks with an ease (no step anywhere). */
    public static void renderCrossfade(GuiGraphics g, float partialTick) {
        if (fadeTicks <= 0 || last == null) return;
        float u = Mth.clamp((fadeTicks - partialTick) / 10f, 0, 1);
        float a = u * u * (3 - 2 * u);
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, last.getColorTextureId());
        RenderSystem.setShaderColor(1, 1, 1, a);
        var mat = g.pose().last().pose();
        int w = g.guiWidth(), h = g.guiHeight();
        var b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.addVertex(mat, 0, h, 0).setUv(0, 0);
        b.addVertex(mat, w, h, 0).setUv(1, 0);
        b.addVertex(mat, w, 0, 0).setUv(1, 1);
        b.addVertex(mat, 0, 0, 0).setUv(0, 1);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    public static boolean crossfading() {
        return fadeTicks > 0;
    }

    private SwapFx() {
    }
}

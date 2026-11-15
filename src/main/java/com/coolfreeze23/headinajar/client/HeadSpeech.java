package com.coolfreeze23.headinajar.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Client: the lines heads say over their jars (a plinth's bell, a complaint, a Lookout's alarm, a goodbye), kept by
 * block position for {@code JarredHeadBlockEntity.SAY_TICKS} ticks from when they were said, so a goodbye outlives its
 * plinth. Drawing: noise and translation flow together as one text wrapped at 120 px (the noise white, HR's words grey),
 * the Speaker Grille's background and scale, only within 16 blocks of the camera. Drawn from the level's own render
 * pass (not from the block's renderer), so a line stays up after its block has gone; every client that watches the
 * block hears it, each in its own language.
 */
public final class HeadSpeech {
    /** How long a line stays up (ticks), and the last of them over which it fades. */
    public static final int TICKS = 100, FADE = 6;
    /** Drawn only this close to the camera (blocks). */
    public static final double RANGE = 16;
    /** Wrapped at this width (font px) into at most this many lines. */
    public static final int WRAP = 120, MAX_LINES = 4;
    /** The Speaker Grille's scale and background. */
    private static final float SCALE = 0.022f;
    private static final int BACKGROUND = 0x80000000;
    /** A text colour the font keeps transparent (an alpha under 4 it would make opaque), for the background pass. */
    private static final int INVISIBLE = 0x04FFFFFF;
    /** How far in front of the background the text is drawn (font units, toward the camera). */
    private static final float FRONT = 0.03f;

    /** One line: where (block, and how high above its floor), when it was said, and its two parts (the second may be null). */
    public record Line(BlockPos pos, double height, long said, Component first, Component second, Wrapped wrapped) {
    }

    /** A line's text as it was wrapped, and the language it was wrapped in (wrapped again if the language changes). */
    public static final class Wrapped {
        String language;
        List<FormattedCharSequence> rows;
        int[] widths;
    }

    private static final Map<BlockPos, Line> LINES = new HashMap<>();
    /** The client level the lines were heard in: another one (a dimension change, a new world) drops them. */
    private static ClientLevel heardIn;

    private HeadSpeech() {
    }

    /** A line arrived from the server for the block at {@code pos}. */
    public static void heard(BlockPos pos, double height, long said, Component first, Component second) {
        var mc = Minecraft.getInstance();
        if (mc.level != heardIn) {
            LINES.clear();
            heardIn = mc.level;
        }
        LINES.put(pos.immutable(), new Line(pos.immutable(), height, said, first, second, new Wrapped()));
    }

    public static Map<BlockPos, Line> lines() {
        return LINES;
    }

    /** Leaving the server: nothing said there is kept. */
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        LINES.clear();
        heardIn = null;
    }

    /** Draws every live line within range, after the entities (as name tags are drawn), and drops those that have expired. */
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || LINES.isEmpty()) return;
        var mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level != heardIn) {
            LINES.clear();
            heardIn = level;
            return;
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = level.getGameTime() + pt;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack() == null ? new PoseStack() : event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Font font = mc.font;
        String language = mc.getLanguageManager().getSelected();
        boolean drew = false;
        for (Iterator<Line> it = LINES.values().iterator(); it.hasNext(); ) {
            Line line = it.next();
            // the server's clock said when; a client a tick behind shows it from its start
            double age = Math.max(0, now - line.said());
            if (age >= TICKS) {
                it.remove();
                continue;
            }
            double x = line.pos().getX() + 0.5, y = line.pos().getY() + line.height(), z = line.pos().getZ() + 0.5;
            double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z;
            if (dx * dx + dy * dy + dz * dz > RANGE * RANGE) continue;
            Wrapped w = wrap(line, font, language);
            if (w.rows.isEmpty()) continue;
            // the last few ticks fade, so it goes rather than vanishes
            float alpha = (float) Mth.clamp((TICKS - age) / FADE, 0, 1);
            int text = argb(alpha, 0xFFFFFF), back = argb(alpha * ((BACKGROUND >>> 24) / 255f), 0);
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(x, y, z));
            pose.pushPose();
            pose.translate(dx, dy, dz);
            pose.mulPose(event.getCamera().rotation());
            // as vanilla's name tags are set up in 1.21 (the camera's turn, then x and z as they are, y down)
            pose.scale(SCALE, -SCALE, SCALE);
            // the last row ends at the line's height, the others stacked above it. The font draws a background 0.01 in
            // front of its text (toward the camera here), which would dim a depth-tested text to half: the background
            // goes first with an invisible text, then the text again just in front of it
            int n = w.rows.size();
            for (int i = 0; i < n; i++) {
                float rx = -w.widths[i] / 2f, ry = (i - n) * (font.lineHeight + 1);
                font.drawInBatch(w.rows.get(i), rx, ry, INVISIBLE, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, back, light);
            }
            pose.translate(0, 0, FRONT);
            for (int i = 0; i < n; i++) {
                float rx = -w.widths[i] / 2f, ry = (i - n) * (font.lineHeight + 1);
                font.drawInBatch(w.rows.get(i), rx, ry, text, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, light);
            }
            pose.popPose();
            drew = true;
        }
        if (drew) buffers.endBatch();
    }

    /** The line as one text (the noise white, HR's words grey after it), wrapped at {@link #WRAP} px, once per language. */
    private static Wrapped wrap(Line line, Font font, String language) {
        Wrapped w = line.wrapped();
        if (w.rows != null && language.equals(w.language)) return w;
        var text = Component.empty().append(line.first().copy().withStyle(ChatFormatting.WHITE));
        if (line.second() != null) text.append(" ").append(line.second().copy().withStyle(ChatFormatting.GRAY));
        List<FormattedCharSequence> rows = font.split(text, WRAP);
        if (rows.size() > MAX_LINES) rows = rows.subList(0, MAX_LINES);
        w.rows = rows;
        w.widths = new int[rows.size()];
        for (int i = 0; i < rows.size(); i++) w.widths[i] = font.width(rows.get(i));
        w.language = language;
        return w;
    }

    /** A colour with its alpha scaled (never quite 0: the font draws an alpha under 4 as opaque). */
    private static int argb(float alpha, int rgb) {
        int a = Math.max(5, Math.round(255 * alpha));
        return a << 24 | rgb;
    }
}

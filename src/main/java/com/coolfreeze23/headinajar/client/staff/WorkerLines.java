package com.coolfreeze23.headinajar.client.staff;

import com.coolfreeze23.headinajar.staff.work.WorkerNet;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

import java.util.Iterator;
import java.util.List;

/**
 * On the client: workers' looks (the loop's colour and what it is doing, by entity id, as the server sends them), their
 * floating stop lines (5 seconds over the head, wrapped at 200 px into two lines at most, drawn as heads' lines are),
 * and the nameplate: the given name over the worker the viewer looks at within 8 blocks, as vanilla draws a name tag
 * (the client option {@code staffNameplates} hides it; the lines stay, they are the worker's answers).
 */
public final class WorkerLines {
    /** How long a line stays up, the last ticks over which it fades, how far it is drawn, how it wraps. */
    public static final int TICKS = Workforce.SAY_SHOW, FADE = 6, WRAP = 200, MAX_LINES = 2;
    public static final double RANGE = 32, NAME_RANGE = 8;
    private static final float SCALE = 0.025f;
    private static final int INVISIBLE = 0x04FFFFFF;
    private static final float FRONT = 0.03f;
    static final long NEVER = Long.MIN_VALUE / 2;

    /** A worker's look as this client knows it, with when its moments began (client game time). */
    public static final class Look {
        public int colour, bits;
        public Component name = Component.empty();
        public long hiredAt = NEVER, poppedAt = NEVER, readAt = NEVER;
        /** The hang's swing (StaffLayer's spring: angles and speeds in degrees) and the frame it was last stepped to. */
        final float[] swing = new float[4];
        float swingAt = Float.NaN, lastYaw = Float.NaN;
        /** The bipeds' arm blend (carry), chased each frame, and the frame it was chased to. */
        float carry, carryAt = Float.NaN;

        public boolean has(int bit) {
            return (bits & bit) != 0;
        }
    }

    private record Line(int entity, long said, Component text, Wrapped wrapped) {
    }

    private static final class Wrapped {
        String language;
        List<FormattedCharSequence> rows;
        int[] widths;
    }

    private static final Int2ObjectMap<Look> LOOKS = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<Line> LINES = new Int2ObjectOpenHashMap<>();
    private static ClientLevel heardIn;
    /** The worker under the crosshair within {@link #NAME_RANGE}, or -1. */
    private static int lookedAt = -1;

    private WorkerLines() {
    }

    private static long now() {
        var mc = Minecraft.getInstance();
        if (mc.level != heardIn) {
            LOOKS.clear();
            LINES.clear();
            heardIn = mc.level;
        }
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    // --- from the server --------------------------------------------------------------------------------------------

    public static void say(WorkerNet.Say msg) {
        LINES.put(msg.entity(), new Line(msg.entity(), now(), msg.line(), new Wrapped()));
    }

    public static void look(WorkerNet.Look msg) {
        long now = now();
        if (msg.colour() < 0) {
            LOOKS.remove(msg.entity());
            return;
        }
        Look before = LOOKS.get(msg.entity());
        Look l = before != null ? before : new Look();
        int was = before == null ? 0 : before.bits;
        l.colour = msg.colour();
        l.bits = msg.bits();
        l.name = msg.name();
        if (l.has(WorkerNet.HIRED) && (was & WorkerNet.HIRED) == 0) l.hiredAt = now;
        // seen for the first time already serving notice: the badge came off before we looked
        if (l.has(WorkerNet.NOTICE) && before != null && (was & WorkerNet.NOTICE) == 0) l.poppedAt = now;
        if (l.has(WorkerNet.READING) && (was & WorkerNet.READING) == 0) l.readAt = now;
        LOOKS.put(msg.entity(), l);
    }

    /** A creature's worker look, or null for one that is not staff (as far as this client was told). */
    public static Look look(Entity e) {
        if (heardIn == null || e.level() != heardIn) return null;
        return LOOKS.get(e.getId());
    }

    /** Ticks (with the partial tick) since a moment, or a large number for one that never happened. */
    public static float since(long at, float partialTick) {
        if (at == NEVER || heardIn == null) return 1e6f;
        return heardIn.getGameTime() - at + partialTick;
    }

    // --- events -----------------------------------------------------------------------------------------------------

    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        LOOKS.clear();
        LINES.clear();
        heardIn = null;
        lookedAt = -1;
    }

    public static void left(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getLevel() == heardIn) LOOKS.remove(event.getEntity().getId());
    }

    /** Once a tick: which worker the viewer looks at, for the nameplate. */
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        lookedAt = -1;
        Entity viewer = mc.getCameraEntity();
        if (mc.level == null || viewer == null || LOOKS.isEmpty() || !nameplates()) return;
        Vec3 eye = viewer.getEyePosition(1);
        Vec3 end = eye.add(viewer.getViewVector(1).scale(NAME_RANGE));
        AABB box = viewer.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(viewer, eye, end, box,
                e -> e instanceof Mob && LOOKS.containsKey(e.getId()) && !e.isInvisible(), NAME_RANGE * NAME_RANGE);
        if (hit == null) return;
        HitResult wall = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, viewer));
        if (wall.getType() != HitResult.Type.MISS && wall.getLocation().distanceToSqr(eye) < hit.getLocation().distanceToSqr(eye)) return;
        lookedAt = hit.getEntity().getId();
    }

    public static boolean nameplates() {
        return !com.coolfreeze23.headinajar.Config.CLIENT_SPEC.isLoaded() || com.coolfreeze23.headinajar.Config.STAFF_NAMEPLATES.get();
    }

    /** The nameplate: the worker's given name, drawn by vanilla's own name tag, while the viewer looks at it. */
    public static void nameTag(RenderNameTagEvent event) {
        Entity e = event.getEntity();
        if (e.getId() != lookedAt || e.hasCustomName() || !nameplates()) return;
        Look l = look(e);
        if (l == null || l.name.getString().isEmpty() || l.has(WorkerNet.NOTICE)) return;
        event.setContent(l.name);
        event.setCanRender(TriState.TRUE);
    }

    /** Whether a name tag is showing over this worker (the stop line then sits above it). */
    private static boolean named(Entity e) {
        if (e.hasCustomName()) return e == Minecraft.getInstance().crosshairPickEntity;
        if (e.getId() != lookedAt || !nameplates()) return false;
        Look l = LOOKS.get(e.getId());
        return l != null && !l.name.getString().isEmpty() && !l.has(WorkerNet.NOTICE);
    }

    // --- the lines --------------------------------------------------------------------------------------------------

    /** Draws every live line over its worker, after the entities (as name tags are), and drops those that have expired. */
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || LINES.isEmpty()) return;
        var mc = Minecraft.getInstance();
        long time = now();
        ClientLevel level = mc.level;
        if (level == null) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack() == null ? new PoseStack() : event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Font font = mc.font;
        String language = mc.getLanguageManager().getSelected();
        float back0 = mc.options.getBackgroundOpacity(0.25f);
        boolean drew = false;
        for (Iterator<Line> it = LINES.values().iterator(); it.hasNext(); ) {
            Line line = it.next();
            double age = Math.max(0, time - line.said() + pt);
            if (age >= TICKS) {
                it.remove();
                continue;
            }
            Entity e = level.getEntity(line.entity());
            if (e == null || e.isInvisible()) continue;
            Vec3 at = e.getPosition(pt);
            Vec3 tag = e.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, e.getViewYRot(pt));
            double y = at.y + (tag == null ? e.getBbHeight() : tag.y) + 0.5 + (named(e) ? 0.3 : 0.05);
            double dx = at.x - cam.x, dy = y - cam.y, dz = at.z - cam.z;
            if (dx * dx + dy * dy + dz * dz > RANGE * RANGE) continue;
            Wrapped w = wrap(line, font, language);
            if (w.rows.isEmpty()) continue;
            float alpha = (float) Mth.clamp((TICKS - age) / FADE, 0, 1);
            int text = argb(alpha, 0xFFFFFF), back = argb(alpha * back0, 0);
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(at.x, y, at.z));
            pose.pushPose();
            pose.translate(dx, dy, dz);
            pose.mulPose(event.getCamera().rotation());
            pose.scale(SCALE, -SCALE, SCALE);
            // the last row ends at the line's height, any other above it; background first with an invisible text, then
            // the text just in front of it (a depth-tested text over its own background would be dimmed to half)
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

    private static Wrapped wrap(Line line, Font font, String language) {
        Wrapped w = line.wrapped();
        if (w.rows != null && language.equals(w.language)) return w;
        List<FormattedCharSequence> rows = font.split(line.text().copy().withStyle(ChatFormatting.WHITE), WRAP);
        if (rows.size() > MAX_LINES) rows = rows.subList(0, MAX_LINES);
        w.rows = rows;
        w.widths = new int[rows.size()];
        for (int i = 0; i < rows.size(); i++) w.widths[i] = font.width(rows.get(i));
        w.language = language;
        return w;
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.max(5, Math.round(255 * alpha));
        return a << 24 | rgb;
    }
}

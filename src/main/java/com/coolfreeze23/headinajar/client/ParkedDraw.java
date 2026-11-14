package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.mecha.MechaModule;
import com.coolfreeze23.headinajar.mecha.ParkedLook;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A parked frame drawn from a vertex buffer of its own. While a machine stands parked ({@link ParkedLook#parked}: nobody
 * aboard, nothing happening to it) it shows one settled pose, and every frame the ordinary path walked its bones and
 * wrote every cube of the frame and its modules again. Here the ordinary path is drawn once more, as always, while what it
 * writes is also kept (about the machine's feet, per render type: the frame's sheet, the modules' atlas, their lit cubes);
 * from the next frame the machine is that kept geometry, one draw per render type, all parked machines together after
 * the entities. Nothing about it is guessed: the kept vertices are the ones the ordinary path wrote, so the hand-over in
 * either direction changes nothing on screen.
 * <p>
 * The kept geometry is used while {@link ParkedLook.Key} stays the same and is rebuilt (by drawing the ordinary way once
 * more) the frame it changes; it is checked against the ordinary drawing every few seconds and dropped when the machine
 * is boarded, unloads or is not drawn for a while. Whenever it cannot be used (a shader pack, the outline of a glowing
 * machine, a screen's preview, a machine in motion) the machine is drawn the ordinary way, never not at all.
 */
@EventBusSubscriber(modid = HeadInAJar.MOD_ID, value = Dist.CLIENT)
public final class ParkedDraw {
    /** Parked machines from their kept geometry (on), or every machine the ordinary way (off, for measuring). */
    public static boolean ON = true;
    /** Frames a kept machine may go undrawn (out of view) before its geometry is let go. */
    static final int IDLE_FRAMES = 1200;

    private ParkedDraw() {
    }

    // --- the kept geometry --------------------------------------------------------------------------------------

    /** One render type's kept vertices: its buffer on the GPU and the order it is drawn in (lit cubes last). */
    private record Part(RenderType type, VertexBuffer vbo, boolean translucent, int vertices) {
    }

    private static final class Cache {
        Part[] parts;
        ParkedLook.Key key;
        long hash;
        long lastFrame;
        int bytes;
        final ParkedLook.Schedule schedule = new ParkedLook.Schedule();

        void close() {
            if (parts != null) for (Part p : parts) p.vbo().close();
            parts = null;
            bytes = 0;
        }
    }

    private static final Map<MechaEntity, Cache> CACHES = new IdentityHashMap<>();

    /** A draw waiting for the end of the entities: a part and where the machine stands from the camera. */
    private record Queued(Part part, float x, float y, float z, int order) {
    }

    private static final List<Queued> QUEUE = new ArrayList<>();
    private static long frame;
    /** Inside the level's entity pass (after the sky, before the entities' end), where queued draws will be flushed. */
    private static boolean entityPass;
    /** Set if queued draws were ever left unflushed (another renderer of the level): parked machines are then always drawn the ordinary way. */
    private static boolean broken;
    private static ShaderInstance cutout, translucent;

    // the harness's figures
    static int builds, kept, verified, changed, dropped;
    /** When the last look was kept: game time with the partial tick. */
    static double lastBuild = -1;
    /** Why kept looks were drawn the ordinary way again, by what changed ("check": the regular comparison). */
    static final Map<String, Integer> REASONS = new java.util.TreeMap<>();

    // --- the capture -------------------------------------------------------------------------------------------

    /** What one render type writes while the ordinary path draws, about the machine's feet. */
    private static final class Recorder {
        final ByteBufferBuilder bytes = new ByteBufferBuilder(36 * 2048);
        final BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        final boolean translucent;
        final int order;

        Recorder(boolean translucent, int order) {
            this.translucent = translucent;
            this.order = order;
        }
    }

    /** Passes everything on to the frame's real buffers and keeps a copy of what the machine itself draws. */
    private static final class Tee implements MultiBufferSource {
        final MultiBufferSource out;
        final MechaEntity m;
        final ParkedLook.Key key;
        final float ox, oy, oz;
        final Map<RenderType, Recorder> recorders = new LinkedHashMap<>();
        boolean recording = true, failed;
        Matrix4f pose;

        Tee(MultiBufferSource out, MechaEntity m, ParkedLook.Key key, float ox, float oy, float oz) {
            this.out = out;
            this.m = m;
            this.key = key;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            VertexConsumer real = out.getBuffer(type);
            if (!recording || failed) return real;
            Recorder r = recorders.get(type);
            if (r == null) {
                int kind = kind(type);
                if (kind < 0) {
                    failed = true;
                    return real;
                }
                r = new Recorder(kind == 2, kind * 1000 + recorders.size());
                recorders.put(type, r);
            }
            return new Both(real, r.builder, this);
        }

        void free() {
            for (Recorder r : recorders.values()) r.bytes.close();
            recorders.clear();
        }
    }

    /**
     * Which of the parked shaders draws a render type: 0 a cut-out entity type on its own sheet, 1 one on the block atlas,
     * 2 the translucent one the lit cubes use; -1 none (the machine is then not kept).
     */
    static int kind(RenderType type) {
        if (type.format() != DefaultVertexFormat.NEW_ENTITY || type.mode() != VertexFormat.Mode.QUADS) return -1;
        String name = type.toString();
        if (name.startsWith("RenderType[entity_translucent_cull:")) return 2;
        if (name.startsWith("RenderType[entity_cutout_no_cull:") || name.startsWith("RenderType[entity_cutout:")) return name.contains("textures/atlas/blocks") ? 1 : 0;
        return -1;
    }

    /** Writes each vertex to the real buffer and, moved to the machine's feet, to the kept one. */
    private static final class Both implements VertexConsumer {
        private final VertexConsumer real;
        private final BufferBuilder kept;
        private final Tee tee;

        Both(VertexConsumer real, BufferBuilder kept, Tee tee) {
            this.real = real;
            this.kept = kept;
            this.tee = tee;
        }

        private boolean keeping() {
            return tee.recording && !tee.failed;
        }

        private void fail(RuntimeException e) {
            tee.failed = true;
            HeadInAJar.LOGGER.debug("A parked machine could not be kept", e);
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
            // drawn about the machine's feet (the pose's move from the camera is held back while it is kept): moved back for the frame
            real.addVertex(x + tee.ox, y + tee.oy, z + tee.oz, color, u, v, overlay, light, nx, ny, nz);
            if (!keeping()) return;
            try {
                kept.addVertex(x, y, z, color, u, v, overlay, light, nx, ny, nz);
            } catch (RuntimeException e) {
                fail(e);
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            real.addVertex(x + tee.ox, y + tee.oy, z + tee.oz);
            if (keeping()) {
                try {
                    kept.addVertex(x, y, z);
                } catch (RuntimeException e) {
                    fail(e);
                }
            }
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            real.setColor(r, g, b, a);
            if (keeping()) kept.setColor(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            real.setUv(u, v);
            if (keeping()) kept.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            real.setUv1(u, v);
            if (keeping()) kept.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            real.setUv2(u, v);
            if (keeping()) kept.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            real.setNormal(x, y, z);
            if (keeping()) kept.setNormal(x, y, z);
            return this;
        }
    }

    private static Tee current;

    // --- the renderer's side -----------------------------------------------------------------------------------

    /**
     * Called as a frame starts drawing. Returns null when the machine has been queued from its kept geometry (the
     * renderer then draws only its name tag and lead); else the buffers to draw it with the ordinary way: the real ones,
     * or ones that also keep what it draws, when it is parked and its look must be (re)built or checked.
     */
    static MultiBufferSource begin(HopperRenderer renderer, MechaEntity m, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!ON || broken || !HopperRenderer.LEAN || !ModuleLayer.ATLAS || !ModuleLayer.DEFER || current != null) return buffers;
        Cache cache = CACHES.get(m);
        var mc = Minecraft.getInstance();
        double now = m.level().getGameTime() + partial;
        if (!usable(m, now, pose, buffers, mc)) {
            // boarded, moving, glowing, under a shader pack, in a screen: the ordinary way; a boarded machine's geometry goes
            if (cache != null && !ParkedLook.parked(m, now)) drop(m, cache);
            return buffers;
        }
        Matrix4f at = pose.last().pose();
        float x = at.m30(), y = at.m31(), z = at.m32();
        ParkedLook.Key key = ParkedLook.key(m, light, renderer.getPackedOverlay(m, 0, partial), Mth.rotLerp(partial, m.yBodyRotO, m.yBodyRot),
                m.view.roll, m.view.pitch, extra(m));
        if (cache != null && cache.parts != null && key.matches(cache.key) && !ParkedLook.Schedule.verifyDue(m.getId(), frame)) {
            cache.lastFrame = frame;
            m.view.still = true;
            for (Part p : cache.parts) QUEUE.add(new Queued(p, x, y, z, p.translucent() ? 1 : 0));
            kept++;
            return null;
        }
        if (cache != null && cache.schedule.resting(frame)) return buffers;
        if (cache != null && cache.parts != null) {
            String why = key.differs(cache.key);
            REASONS.merge(why == null ? "check" : why, 1, Integer::sum);
        }
        current = new Tee(buffers, m, key, x, y, z);
        // the machine is drawn about its own feet while it is kept, so what is kept does not depend on where the camera
        // stands; what goes to the frame's buffers is moved back (Both), and the move is restored for the name tag and after
        current.pose = at;
        at.m30(0).m31(0).m32(0);
        return current;
    }

    /** The name tag and the lead are drawn every frame, never kept. */
    static void pause() {
        if (current == null) return;
        current.recording = false;
        current.pose.m30(current.ox).m31(current.oy).m32(current.oz);
    }

    static void resume() {
        if (current == null) return;
        current.recording = true;
        current.pose.m30(0).m31(0).m32(0);
    }

    /** The machine has been drawn the ordinary way: keep what it drew, or keep what was kept if it is the same. */
    static void end(MechaEntity m) {
        Tee tee = current;
        if (tee == null || tee.m != m) return;
        current = null;
        tee.recording = false;
        tee.pose.m30(tee.ox).m31(tee.oy).m32(tee.oz);
        Cache cache = CACHES.computeIfAbsent(m, k -> new Cache());
        if (tee.failed || tee.recorders.isEmpty()) {
            tee.free();
            cache.close();
            cache.key = null;
            cache.schedule.built(frame);
            return;
        }
        List<MeshData> meshes = new ArrayList<>();
        List<Recorder> from = new ArrayList<>();
        long hash = 1;
        for (Recorder r : tee.recorders.values()) {
            MeshData mesh = r.builder.build();
            if (mesh == null) continue;
            meshes.add(mesh);
            from.add(r);
            hash = hash * 1_000_003L + hash(mesh.vertexBuffer()) + r.order;
        }
        if (cache.parts != null && cache.hash == hash && tee.key.matches(cache.key)) {
            // checked: what is kept is exactly what the ordinary path drew
            for (MeshData mesh : meshes) mesh.close();
            tee.free();
            cache.lastFrame = frame;
            verified++;
            return;
        }
        if (cache.parts != null && tee.key.matches(cache.key)) changed++;
        cache.close();
        List<Part> parts = new ArrayList<>();
        int bytes = 0;
        for (Map.Entry<RenderType, Recorder> e : tee.recorders.entrySet()) {
            int at = from.indexOf(e.getValue());
            if (at < 0) continue;
            MeshData mesh = meshes.get(at);
            int vertices = mesh.drawState().vertexCount();
            bytes += vertices * DefaultVertexFormat.NEW_ENTITY.getVertexSize();
            VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vbo.bind();
            vbo.upload(mesh);
            VertexBuffer.unbind();
            parts.add(new Part(e.getKey(), vbo, e.getValue().translucent, vertices));
        }
        tee.free();
        parts.sort(java.util.Comparator.comparingInt(p -> p.translucent() ? 1 : 0));
        cache.parts = parts.toArray(new Part[0]);
        cache.key = tee.key;
        cache.hash = hash;
        cache.bytes = bytes;
        cache.lastFrame = frame;
        builds++;
        lastBuild = m.level().getGameTime() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        if (cache.schedule.built(frame)) drop(m, cache);
    }

    /**
     * What the vertices say, to compare a check with what is kept: each place to a 64th of a pixel (where the camera
     * stood when they were written moves them by a few millionths of a block), everything else exactly, the format's
     * padding byte left out (it is never written).
     */
    private static long hash(ByteBuffer b) {
        int size = DefaultVertexFormat.NEW_ENTITY.getVertexSize();
        long h = 1;
        for (int v = b.position(); v + size <= b.limit(); v += size) {
            for (int c = 0; c < 3; c++) h = h * 31 + Math.round(b.getFloat(v + 4 * c) * 1024);
            h = h * 31 + b.getLong(v + 12);
            h = h * 31 + b.getLong(v + 20);
            h = h * 31 + b.getInt(v + 28);
            for (int c = 32; c < 35; c++) h = h * 31 + b.get(v + c);
        }
        return h;
    }

    /** What the client's own module motion still holds on a parked machine (a drill's resting angle, the jets' spent lamps). */
    private static int extra(MechaEntity m) {
        int h = 0;
        if (m.has(MechaModule.DRILL)) h = Float.floatToIntBits(m.view.drillAngle);
        if (m.has(MechaModule.JUMP_JETS)) h = h * 31 + m.view.jetsUsed;
        // a name can turn a mob upside down: a new name is a new look
        return h * 31 + System.identityHashCode(m.getCustomName());
    }

    /** Whether this machine may be drawn from kept geometry in this draw. */
    private static boolean usable(MechaEntity m, double now, PoseStack pose, MultiBufferSource buffers, Minecraft mc) {
        if (!entityPass || cutout == null || translucent == null || buffers != mc.renderBuffers().bufferSource() || shaderPack()) return false;
        if (!ParkedLook.parked(m, now) || m.isCurrentlyGlowing() || m.isPassenger() || m.isFullyFrozen()) return false;
        if (com.coolfreeze23.headinajar.mecha.Skitter.out(m) || m.view.drillSpeed != 0 || m.view.jetAt >= 0 && now - m.view.jetAt <= 6) return false;
        HopperRenderer.FrameModel model = HopperRenderer.MODELS.get(m.frame());
        if (model == null || !model.settled()) return false;
        // in the level the pose is only the move from the camera to the machine; anything else (a screen's preview) is drawn the ordinary way
        Matrix4f p = pose.last().pose();
        return Math.abs(p.m00() - 1) < 1e-6f && Math.abs(p.m11() - 1) < 1e-6f && Math.abs(p.m22() - 1) < 1e-6f && Math.abs(p.m01()) < 1e-6f
                && Math.abs(p.m02()) < 1e-6f && Math.abs(p.m10()) < 1e-6f && Math.abs(p.m12()) < 1e-6f && Math.abs(p.m20()) < 1e-6f
                && Math.abs(p.m21()) < 1e-6f && p.m03() == 0 && p.m13() == 0 && p.m23() == 0 && p.m33() == 1;
    }

    private static void drop(MechaEntity m, Cache cache) {
        cache.close();
        cache.key = null;
        if (!cache.schedule.resting(frame)) CACHES.remove(m);
        dropped++;
    }

    // --- drawing the kept machines -------------------------------------------------------------------------------

    /** Every queued machine, by render type: each type's state and shader set once, then one draw per machine. */
    private static void flush() {
        if (QUEUE.isEmpty()) return;
        QUEUE.sort(java.util.Comparator.comparingInt(Queued::order).thenComparingInt(q -> System.identityHashCode(q.part().type())));
        Matrix4f view = RenderSystem.getModelViewMatrix(), projection = RenderSystem.getProjectionMatrix();
        var window = Minecraft.getInstance().getWindow();
        RenderType type = null;
        ShaderInstance shader = null;
        for (Queued q : QUEUE) {
            Part p = q.part();
            if (p.type() != type) {
                if (type != null) {
                    shader.clear();
                    type.clearRenderState();
                }
                type = p.type();
                type.setupRenderState();
                shader = p.translucent() ? translucent : cutout;
                shader.setDefaultUniforms(VertexFormat.Mode.QUADS, view, projection, window);
                shader.apply();
            }
            if (shader.CHUNK_OFFSET != null) {
                shader.CHUNK_OFFSET.set(q.x(), q.y(), q.z());
                shader.CHUNK_OFFSET.upload();
            }
            p.vbo().bind();
            p.vbo().draw();
        }
        shader.clear();
        type.clearRenderState();
        VertexBuffer.unbind();
        QUEUE.clear();
    }

    @SubscribeEvent
    public static void stage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            entityPass = true;
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            entityPass = false;
            flush();
        }
    }

    @SubscribeEvent
    public static void frameStart(RenderFrameEvent.Pre event) {
        frame++;
        entityPass = false;
        if (current != null) {
            current.free();
            current = null;
        }
        if (!QUEUE.isEmpty()) {
            // the level was drawn without the entities' end being announced: never queue again this session
            broken = true;
            QUEUE.clear();
            HeadInAJar.LOGGER.warn("Parked machines will be drawn the ordinary way: the level's entity pass ended unannounced");
        }
    }

    /** Lets go of the geometry of machines that unloaded, left this level, or have not been drawn for a while. */
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (CACHES.isEmpty() || mc.level != null && mc.level.getGameTime() % 20 != 0) return;
        for (Iterator<Map.Entry<MechaEntity, Cache>> it = CACHES.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            MechaEntity m = e.getKey();
            if (mc.level == null || m.isRemoved() || m.level() != mc.level || frame - e.getValue().lastFrame > IDLE_FRAMES) {
                e.getValue().close();
                it.remove();
            }
        }
    }

    static void clear() {
        for (Cache c : CACHES.values()) c.close();
        CACHES.clear();
        QUEUE.clear();
    }

    // --- shader packs ------------------------------------------------------------------------------------------

    private static java.lang.reflect.Method irisInUse;
    private static Object iris;
    private static boolean irisLooked;

    /** An Iris shader pack is on: its own programs draw entities (and their shadows), so parked machines are drawn the ordinary way. */
    static boolean shaderPack() {
        if (!irisLooked) {
            irisLooked = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                iris = api.getMethod("getInstance").invoke(null);
                irisInUse = api.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | LinkageError e) {
                iris = null;
            }
        }
        if (iris == null) return false;
        try {
            return (boolean) irisInUse.invoke(iris);
        } catch (ReflectiveOperationException e) {
            return true;
        }
    }

    // --- the harness's figures ---------------------------------------------------------------------------------

    /** How many machines are kept, their memory, and what happened since the game started. */
    public static String describe() {
        int bytes = 0, vertices = 0, n = 0;
        for (Cache c : CACHES.values()) {
            if (c.parts == null) continue;
            n++;
            bytes += c.bytes;
            for (Part p : c.parts) vertices += p.vertices();
        }
        return String.format(java.util.Locale.ROOT, "parked: %s, %d kept (%d vertices, %.1f KB, %.1f KB each), %d builds, %d kept draws, %d checks the same, "
                        + "%d changed outside the key, %d dropped, last kept at tick %.2f, redrawn for %s%s%s", ON ? "on" : "off", n, vertices, bytes / 1024.0, n == 0 ? 0 : bytes / 1024.0 / n,
                builds, kept, verified, changed, dropped, lastBuild, REASONS, broken ? ", BROKEN (entity pass unannounced)" : "", shaderPack() ? ", shader pack on (ordinary drawing)" : "");
    }

    @EventBusSubscriber(modid = HeadInAJar.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        public static void shaders(RegisterShadersEvent event) throws IOException {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), HeadInAJar.id("parked_cutout"), DefaultVertexFormat.NEW_ENTITY),
                    s -> cutout = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), HeadInAJar.id("parked_translucent"), DefaultVertexFormat.NEW_ENTITY),
                    s -> translucent = s);
        }

        /** New textures or models: every kept machine is drawn again from them. */
        @SubscribeEvent
        public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> RenderSystem.recordRenderCall(ParkedDraw::clear));
        }
    }
}

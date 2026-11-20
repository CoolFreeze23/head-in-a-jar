package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.host.HostTricks;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/**
 * A pig host's Rooting, on its pilot's own client: the loaded blocks within the radius are read over four ticks (about
 * 9,000 a tick), and every root crop and mushroom found is outlined through the ground, breathing slowly, for ten
 * seconds. Only this client draws them: nobody else sees what the pig smelt.
 */
public final class RootingOutlines {
    /** Lines drawn over everything (no depth test), for what lies in the ground. */
    private static final RenderType THROUGH = RenderType.create("headinajar_rooting", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(2)))
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .createCompositeState(false));
    /** At most this many finds are kept (the nearest rows first, as the scan goes). */
    private static final int CAP = 256;

    private record Find(BlockPos pos, int rgb) {
    }

    private static final List<Find> FINDS = new ArrayList<>();
    private static BlockPos centre;
    private static int radius, step = -1, ticksLeft, total;

    private RootingOutlines() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(RootingOutlines::tick);
        NeoForge.EVENT_BUS.addListener(RootingOutlines::render);
    }

    /** The cue from the server: scan from where the pilot is now, and show what is found for {@code ticks}. */
    public static void start(int ticks, int r) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        FINDS.clear();
        centre = mc.player.blockPosition();
        radius = Math.max(1, Math.min(r, 32));
        step = 0;
        ticksLeft = ticks;
        total = ticks;
    }

    /** Whether any outlines are showing (for tests and the bench). */
    public static int showing() {
        return ticksLeft > 0 ? FINDS.size() : 0;
    }

    private static int colour(BlockState state) {
        if (state.is(Blocks.CARROTS)) return 0xFF9A2E;
        if (state.is(Blocks.POTATOES)) return 0xE8C878;
        if (state.is(Blocks.BEETROOTS)) return 0xD0354F;
        return 0xD8B48A;
    }

    private static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            FINDS.clear();
            ticksLeft = 0;
            step = -1;
            return;
        }
        if (ticksLeft <= 0) return;
        ticksLeft--;
        if (step >= 0 && step < 4) {
            int side = radius * 2 + 1, per = (side + 3) / 4;
            int y0 = centre.getY() - radius + step * per, y1 = Math.min(centre.getY() + radius, y0 + per - 1);
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int x = centre.getX() - radius; x <= centre.getX() + radius; x++) {
                for (int z = centre.getZ() - radius; z <= centre.getZ() + radius; z++) {
                    if (!level.hasChunk(x >> 4, z >> 4)) continue;
                    for (int y = y0; y <= y1 && FINDS.size() < CAP; y++) {
                        BlockState s = level.getBlockState(m.set(x, y, z));
                        if (HostTricks.rootable(s)) FINDS.add(new Find(m.immutable(), colour(s)));
                    }
                }
            }
            if (++step == 4) {
                mc.player.displayClientMessage(FINDS.isEmpty() ? Component.translatable("message.headinajar.host.rooted_none", radius)
                        : Component.translatable("message.headinajar.host.rooted", FINDS.size()), true);
            }
        }
        // dug up or eaten meanwhile: its outline goes
        if (level.getGameTime() % 10 == 0) FINDS.removeIf(f -> !HostTricks.rootable(level.getBlockState(f.pos())));
        if (ticksLeft == 0) FINDS.clear();
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || FINDS.isEmpty() || ticksLeft <= 0) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float t = mc.level.getGameTime() + pt;
        // breathing slowly, never flashing; the last second fades out
        float alpha = (0.55f + 0.25f * Mth.sin(t * Mth.TWO_PI / 40f)) * Math.min(1, (ticksLeft - pt) / 20f) * Math.min(1, (total - ticksLeft + pt) / 6f);
        PoseStack pose = event.getPoseStack() == null ? new PoseStack() : event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(THROUGH);
        Vec3 cam = event.getCamera().getPosition();
        var last = pose.last();
        for (Find f : FINDS) {
            AABB box = new AABB(f.pos()).deflate(0.06);
            if (event.getFrustum() != null && !event.getFrustum().isVisible(box)) continue;
            float r = (f.rgb() >> 16 & 255) / 255f, g = (f.rgb() >> 8 & 255) / 255f, b = (f.rgb() & 255) / 255f;
            edges(vc, last, cam, box, r, g, b, alpha);
        }
        // NO_DEPTH_TEST leaves the depth test as it finds it, and at this stage the level's is still on: without this the
        // outlines were hidden by the ground they are meant to show through. Every depth passes while they draw; then the
        // game's usual test (less or equal) is put back.
        com.mojang.blaze3d.systems.RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_ALWAYS);
        buffers.endBatch(THROUGH);
        com.mojang.blaze3d.systems.RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
    }

    private static void edges(VertexConsumer vc, PoseStack.Pose pose, Vec3 o, AABB b, float r, float g, float bl, float a) {
        double[] xs = {b.minX, b.maxX}, ys = {b.minY, b.maxY}, zs = {b.minZ, b.maxZ};
        for (int j = 0; j < 2; j++) for (int k = 0; k < 2; k++) {
            line(vc, pose, o, xs[0], ys[j], zs[k], xs[1], ys[j], zs[k], r, g, bl, a, 1, 0, 0);
            line(vc, pose, o, xs[j], ys[0], zs[k], xs[j], ys[1], zs[k], r, g, bl, a, 0, 1, 0);
            line(vc, pose, o, xs[j], ys[k], zs[0], xs[j], ys[k], zs[1], r, g, bl, a, 0, 0, 1);
        }
    }

    private static void line(VertexConsumer vc, PoseStack.Pose pose, Vec3 o, double x0, double y0, double z0, double x1, double y1, double z1,
                             float r, float g, float b, float a, float nx, float ny, float nz) {
        vc.addVertex(pose, (float) (x0 - o.x), (float) (y0 - o.y), (float) (z0 - o.z)).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, (float) (x1 - o.x), (float) (y1 - o.y), (float) (z1 - o.z)).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
    }
}

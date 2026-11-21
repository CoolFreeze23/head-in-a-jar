package com.coolfreeze23.headinajar.client.staff;

import com.coolfreeze23.headinajar.Config;
import com.coolfreeze23.headinajar.registry.WorkRegistry;
import com.coolfreeze23.headinajar.staff.work.Order;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.OptionalDouble;

/**
 * The Clipboard's chalk: while this client's player holds a Clipboard (either hand) and the {@code staffChalk} client
 * option is on, its marks are outlined in the world for that player alone: each stop's block (a Pick-Up in warm chalk, a
 * Drop-Off in a cooler one), each area as a box on the level of its corners, a first corner waiting for its second (its
 * block and a peg on top), and the bound site's box, faintly. Nothing is kept or scanned: each frame reads the held
 * Clipboard's order and draws it.
 */
public final class ChalkOutlines {
    /** Chalk lines, hidden behind blocks as chalk on a wall would be. */
    private static final RenderType CHALK = RenderType.create("headinajar_chalk", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(2)))
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));
    /** Chalk tints: a Pick-Up, a Drop-Off, an area, a waiting corner, the site. */
    private static final int PICK_UP = 0xF4F1E6, DROP_OFF = 0xD6E4F2, AREA = 0xF0EDE2, CORNER = 0xFFF3C4, SITE = 0xF4F1E6;

    private ChalkOutlines() {
    }

    /** Whether this client wants the chalk (on when the client options have not loaded). */
    private static boolean wanted() {
        return !Config.CLIENT_SPEC.isLoaded() || Config.STAFF_CHALK.get();
    }

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        ItemStack board = mc.player.getMainHandItem();
        if (!board.is(WorkRegistry.CLIPBOARD.get())) board = mc.player.getOffhandItem();
        if (!board.is(WorkRegistry.CLIPBOARD.get())) return;
        Order o = Order.of(board);
        // marks are made in the site's dimension; elsewhere they mean nothing
        if (o.clock().isEmpty() || !o.clock().get().dimension().equals(mc.level.dimension()) || !wanted()) return;
        PoseStack pose = event.getPoseStack() == null ? new PoseStack() : event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(CHALK);
        Vec3 cam = event.getCamera().getPosition();
        var last = pose.last();
        draw(event, vc, last, cam, Workforce.box(o.clock().get().pos()), SITE, 0.22f);
        for (Order.Stop s : o.stops()) draw(event, vc, last, cam, new AABB(s.pos()).inflate(0.02), s.dropOff() ? DROP_OFF : PICK_UP, 0.9f);
        for (Order.Area a : o.areas()) {
            BlockPos lo = a.min(), hi = a.max();
            draw(event, vc, last, cam, new AABB(lo.getX(), lo.getY(), lo.getZ(), hi.getX() + 1, hi.getY() + 1, hi.getZ() + 1).inflate(0.03), AREA, 0.75f);
        }
        if (o.corner().isPresent()) {
            BlockPos c = o.corner().get();
            draw(event, vc, last, cam, new AABB(c).inflate(0.02), CORNER, 0.85f);
            draw(event, vc, last, cam, new AABB(c.getX() + 0.35, c.getY() + 1, c.getZ() + 0.35, c.getX() + 0.65, c.getY() + 1.4, c.getZ() + 0.65), CORNER, 0.95f);
        }
        buffers.endBatch(CHALK);
    }

    private static void draw(RenderLevelStageEvent event, VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, AABB box, int rgb, float alpha) {
        if (event.getFrustum() != null && !event.getFrustum().isVisible(box)) return;
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        double[] xs = {box.minX, box.maxX}, ys = {box.minY, box.maxY}, zs = {box.minZ, box.maxZ};
        for (int j = 0; j < 2; j++) for (int k = 0; k < 2; k++) {
            line(vc, pose, cam, xs[0], ys[j], zs[k], xs[1], ys[j], zs[k], r, g, b, alpha, 1, 0, 0);
            line(vc, pose, cam, xs[j], ys[0], zs[k], xs[j], ys[1], zs[k], r, g, b, alpha, 0, 1, 0);
            line(vc, pose, cam, xs[j], ys[k], zs[0], xs[j], ys[k], zs[1], r, g, b, alpha, 0, 0, 1);
        }
    }

    private static void line(VertexConsumer vc, PoseStack.Pose pose, Vec3 o, double x0, double y0, double z0, double x1, double y1, double z1,
                             float r, float g, float b, float a, float nx, float ny, float nz) {
        vc.addVertex(pose, (float) (x0 - o.x), (float) (y0 - o.y), (float) (z0 - o.z)).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, (float) (x1 - o.x), (float) (y1 - o.y), (float) (z1 - o.z)).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
    }
}

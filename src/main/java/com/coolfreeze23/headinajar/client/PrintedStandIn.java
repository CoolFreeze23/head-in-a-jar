package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.block.BodyPrinterBlockEntity;
import com.coolfreeze23.headinajar.host.Hosts;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * A species print in the Body Printer's vat: a client-only stand-in of the template's species, drawn by its own renderer
 * (so it is the vanilla model, or whatever a resource pack makes of it), headless as a host is. As for the own-body
 * print it takes shape under the print bar in the base material, develops from the top down, then walks out through
 * the door; the real host steps out where it stops.
 */
final class PrintedStandIn {
    /** The base material, as the own-body print's. */
    private static final int BASE = 0xFFD9B8A6;
    private static LivingEntity dummy;
    private static long walkedAt = Long.MIN_VALUE;

    private PrintedStandIn() {
    }

    private static LivingEntity dummy(EntityType<?> type, Level level) {
        if (dummy == null || dummy.getType() != type || dummy.level() != level) {
            Entity e = type.create(level);
            dummy = e instanceof LivingEntity living ? living : null;
            // a host's head is not drawn: the stand-in is one from the start
            if (dummy != null) Hosts.data(dummy).host = true;
        }
        return dummy;
    }

    /** {@code t}: the print's tick (0-1,200); {@code plane}: the print bar's height in the vat, in pixels. */
    static void render(BodyPrinterBlockEntity p, PoseStack pose, MultiBufferSource buffers, int light, double t, float plane, float partial) {
        if (t >= 1200 || p.getLevel() == null) return;
        LivingEntity e = dummy(p.species(), p.getLevel());
        if (e == null) return;
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(e);
        if (renderer == null) return;
        double z = 0, yaw = 45, y = 3, amount = 0;
        if (t >= 1178) {
            // out through the door, turning to face the front (the own-body print's walk, without its duck)
            double s = (t - 1178) / 22;
            z = -16 * (-(Math.cos(Math.PI * Mth.clamp(s, 0, 1)) - 1) / 2);
            yaw = 45 * (1 - Mth.clamp((s - 0.72) / 0.2, 0, 1));
            y = 3 - 3 * (s < 0.88 ? 0 : Math.pow((s - 0.88) / 0.12, 2));
            // the legs at the game's own pace for this walk (4 x the blocks moved a tick, as a creature's walk is told):
            // a block in 22 ticks is a slow amble, so a short swing, not a gallop
            amount = 0.3 * Math.sin(Math.PI * s);
        }
        // the legs walk once a game tick while it walks out, and stand still while it prints
        long now = p.getLevel().getGameTime();
        if (now != walkedAt) {
            walkedAt = now;
            e.walkAnimation.update((float) amount, 0.4f);
        }
        // facing the door: the renderer turns a creature by 180 - its body yaw, so 180 leaves it as the vat has it
        e.yBodyRot = e.yBodyRotO = 180;
        e.yHeadRot = e.yHeadRotO = 180;
        e.setYRot(180);
        e.yRotO = 180;
        // sized to the booth: no taller than a printed body (1.8 blocks) and no wider than 0.9
        float scale = Math.min(0.9375f, Math.min(1.8f / Math.max(0.1f, e.getBbHeight()), 0.9f / Math.max(0.1f, e.getBbWidth())));
        float worldPlane = pose.last().pose().transformPosition(0, plane / 16f, 0, new org.joml.Vector3f()).y;
        pose.pushPose();
        pose.translate(0, y / 16, z / 16);
        pose.mulPose(Axis.YP.rotationDegrees((float) yaw));
        pose.scale(scale, scale, scale);
        MultiBufferSource source;
        if (t < 1000) {
            // printing: only what is below the bar, in the base material
            source = clipped(buffers, worldPlane, true, true);
        } else if (t < 1008) {
            source = type -> new Tint(buffers.getBuffer(type));
        } else if (t < 1140) {
            // the develop sweeps down with the bar: the creature above it, base below
            MultiBufferSource above = clipped(buffers, worldPlane, false, false), below = clipped(buffers, worldPlane, true, true);
            source = type -> new Both(above.getBuffer(type), below.getBuffer(type));
        } else {
            source = buffers;
        }
        try {
            renderer.render(e, 180, partial, pose, source, light);
        } catch (RuntimeException ex) {
            // a creature whose renderer wants the world around it (it never ticked): no stand-in, the print goes on
            dummy = null;
        }
        pose.popPose();
    }

    private static MultiBufferSource clipped(MultiBufferSource buffers, float plane, boolean below, boolean tint) {
        return type -> {
            VertexConsumer out = buffers.getBuffer(type);
            return new ClipConsumer(tint ? new Tint(out) : out, plane, below);
        };
    }

    /** The base material: every vertex's colour multiplied by {@link #BASE}. */
    private record Tint(VertexConsumer out) implements VertexConsumer {
        private static int mul(int a, int b, int shift) {
            return (((a >>> shift) & 0xFF) * ((b >>> shift) & 0xFF) / 255) << shift;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
            int c = mul(color, BASE, 24) | mul(color, BASE, 16) | mul(color, BASE, 8) | mul(color, BASE, 0);
            out.addVertex(x, y, z, c, u, v, overlay, light, nx, ny, nz);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return out.addVertex(x, y, z);
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return out.setColor(r * ((BASE >> 16) & 0xFF) / 255, g * ((BASE >> 8) & 0xFF) / 255, b * (BASE & 0xFF) / 255, a);
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return out.setUv(u, v);
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return out.setUv1(u, v);
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return out.setUv2(u, v);
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return out.setNormal(x, y, z);
        }
    }

    /** One model drawn into two consumers (the half above the bar and the half below), each keeping its own side. */
    private record Both(VertexConsumer a, VertexConsumer b) implements VertexConsumer {
        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
            a.addVertex(x, y, z, color, u, v, overlay, light, nx, ny, nz);
            b.addVertex(x, y, z, color, u, v, overlay, light, nx, ny, nz);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            b.addVertex(x, y, z);
            return a.addVertex(x, y, z);
        }

        @Override
        public VertexConsumer setColor(int r, int g, int bl, int al) {
            b.setColor(r, g, bl, al);
            return a.setColor(r, g, bl, al);
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            b.setUv(u, v);
            return a.setUv(u, v);
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            b.setUv1(u, v);
            return a.setUv1(u, v);
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            b.setUv2(u, v);
            return a.setUv2(u, v);
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            b.setNormal(x, y, z);
            return a.setNormal(x, y, z);
        }
    }
}

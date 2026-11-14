package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.mecha.Skitter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The Dragline drawn as the company's hi-vis safety line: a flat ribbon, banded warning yellow and black, from the line
 * gun's muzzle ({@code fx_line} as the model was last drawn) to the anchor, lit by the blocks along it as a lead is. A
 * line under tension is straight; a slack one (the machine walking, the line trailing) sags a little.
 */
final class DraglineRibbon {
    private static final int SEGMENTS = 24;
    private static final float HALF = 0.035f;

    private DraglineRibbon() {
    }

    static void draw(MechaEntity m, float partial, PoseStack pose, MultiBufferSource buffers) {
        Vec3 from = HopperKeyframes.locator(m, "fx_line"), to = m.anchor();
        Vec3 origin = new Vec3(Mth.lerp(partial, m.xo, m.getX()), Mth.lerp(partial, m.yo, m.getY()), Mth.lerp(partial, m.zo, m.getZ()));
        Vec3 a = from.subtract(origin), d = to.subtract(from);
        double length = d.length();
        if (length < 0.05) return;
        double sag = m.lineMode() == Skitter.SLACK ? Math.min(0.6, 0.04 * length) : 0;
        // the ribbon faces sideways to the line: across it, level
        Vec3 side = d.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-6) side = new Vec3(1, 0, 0);
        side = side.normalize().scale(HALF);
        Vec3 flat = d.cross(side).normalize().scale(HALF);
        var level = m.level();
        BlockPos fromPos = BlockPos.containing(from), toPos = BlockPos.containing(to);
        int b0 = level.getBrightness(LightLayer.BLOCK, fromPos), b1 = level.getBrightness(LightLayer.BLOCK, toPos);
        int s0 = level.getBrightness(LightLayer.SKY, fromPos), s1 = level.getBrightness(LightLayer.SKY, toPos);
        Matrix4f matrix = pose.last().pose();
        for (Vec3 across : new Vec3[]{side, flat}) {
            // each ribbon its own strip (a lead's render type never joins one strip to the next)
            VertexConsumer out = buffers.getBuffer(RenderType.leash());
            for (int i = 0; i <= SEGMENTS; i++) {
                float f = i / (float) SEGMENTS;
                Vec3 p = a.add(d.scale(f)).subtract(0, sag * 4 * f * (1 - f), 0);
                boolean yellow = (i / 2) % 2 == 0;
                float r = yellow ? 0.98f : 0.12f, g = yellow ? 0.80f : 0.11f, bl = yellow ? 0.10f : 0.10f;
                int light = LightTexture.pack((int) Mth.lerp(f, b0, b1), (int) Mth.lerp(f, s0, s1));
                out.addVertex(matrix, (float) (p.x + across.x), (float) (p.y + across.y), (float) (p.z + across.z)).setColor(r, g, bl, 1).setLight(light);
                out.addVertex(matrix, (float) (p.x - across.x), (float) (p.y - across.y), (float) (p.z - across.z)).setColor(r, g, bl, 1).setLight(light);
            }
        }
    }
}

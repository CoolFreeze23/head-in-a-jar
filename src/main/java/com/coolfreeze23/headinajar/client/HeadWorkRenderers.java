package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity;
import com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity;
import com.coolfreeze23.headinajar.block.HeadInfo;
import com.coolfreeze23.headinajar.block.MachineBlock;
import com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/** Heads at work: on a turret (turning to face the nearest hostile), on an employee mount, and racked in a Cerebral Array. */
public final class HeadWorkRenderers {
    private HeadWorkRenderers() {
    }

    /**
     * A head drawn on a holder rises above its block: the box the renderer is culled by (vanilla, Sodium, Entity Culling)
     * covers the block and one more above it, a little wider for a head that turns.
     */
    static AABB above(net.minecraft.core.BlockPos pos) {
        return new AABB(pos).expandTowards(0, 1, 0).inflate(0.25, 0, 0.25);
    }

    static void drawHead(ItemStack head, HeadInfo info, PoseStack pose, MultiBufferSource buffers, int light, float age, float scale) {
        if (head.is(Items.DRAGON_HEAD)) {
            pose.pushPose();
            pose.translate(0, 0.25, 0);
            pose.scale(scale, scale, scale);
            Minecraft.getInstance().getItemRenderer().renderStatic(head, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, null, 0);
            pose.popPose();
            return;
        }
        EntityType<?> type = info.type();
        if (type == null) return;
        pose.pushPose();
        pose.scale(scale, scale, scale);
        JarredHeadRenderer.drawJar(type, info.villagerData(), pose, buffers, light, age);
        pose.popPose();
    }

    public static class Turret implements BlockEntityRenderer<TurretPedestalBlockEntity> {
        public Turret(BlockEntityRendererProvider.Context context) {
        }

        @Override
        public void render(TurretPedestalBlockEntity t, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            if (t.getLevel() == null || t.head().isEmpty()) return;
            float age = t.getLevel().getGameTime() + partialTick;
            // the head is turned on the client tick (TurretPedestalBlockEntity.clientTick); here it is only drawn between ticks
            pose.pushPose();
            pose.translate(0.5, 10 / 16f, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, t.yawO, t.yaw)));
            drawHead(t.head(), t.headInfo(), pose, buffers, light, age, 0.7f);
            pose.popPose();
        }

        @Override
        public AABB getRenderBoundingBox(TurretPedestalBlockEntity t) {
            return above(t.getBlockPos());
        }
    }

    public static class Mount implements BlockEntityRenderer<EmployeeMountBlockEntity> {
        public Mount(BlockEntityRendererProvider.Context context) {
        }

        @Override
        public void render(EmployeeMountBlockEntity m, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            if (m.getLevel() == null || m.head().isEmpty()) return;
            pose.pushPose();
            pose.translate(0.5, 12 / 16f, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-m.getBlockState().getValue(MachineBlock.FACING).toYRot()));
            drawHead(m.head(), m.headInfo(), pose, buffers, light, m.getLevel().getGameTime() + partialTick, 0.6f);
            pose.popPose();
        }

        @Override
        public AABB getRenderBoundingBox(EmployeeMountBlockEntity m) {
            return above(m.getBlockPos());
        }
    }

    public static class Socket implements BlockEntityRenderer<com.coolfreeze23.headinajar.block.NeuralSocketBlockEntity> {
        public Socket(BlockEntityRendererProvider.Context context) {
        }

        @Override
        public void render(com.coolfreeze23.headinajar.block.NeuralSocketBlockEntity s, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            if (s.getLevel() == null || s.head().isEmpty()) return;
            pose.pushPose();
            pose.translate(0.5, 1, 0.5);
            drawHead(s.head(), s.headInfo(), pose, buffers, light, s.getLevel().getGameTime() + partialTick, 0.7f);
            pose.popPose();
        }

        @Override
        public AABB getRenderBoundingBox(com.coolfreeze23.headinajar.block.NeuralSocketBlockEntity s) {
            return above(s.getBlockPos());
        }
    }

    public static class Array implements BlockEntityRenderer<CerebralArrayBlockEntity> {
        public Array(BlockEntityRendererProvider.Context context) {
        }

        @Override
        public void render(CerebralArrayBlockEntity a, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            if (a.getLevel() == null) return;
            float age = a.getLevel().getGameTime() + partialTick;
            pose.pushPose();
            pose.translate(0.5, 0, 0.5);
            // block models face north unrotated; this matches the blockstate's y rotation
            pose.mulPose(Axis.YP.rotationDegrees(180 - a.getBlockState().getValue(MachineBlock.FACING).toYRot()));
            // a 3 x 3 rack on the front face
            for (int i = 0; i < CerebralArrayBlockEntity.HEAD_SLOTS; i++) {
                ItemStack head = a.items().getStackInSlot(i);
                if (head.isEmpty()) continue;
                pose.pushPose();
                pose.translate((i % 3 - 1) * 0.3, 0.68 - (i / 3) * 0.3, -0.5);
                drawHead(head, a.headInfo(i), pose, buffers, light, age + i * 7, 0.26f);
                pose.popPose();
            }
            pose.popPose();
        }

        /** The rack stands proud of the front face. */
        @Override
        public AABB getRenderBoundingBox(CerebralArrayBlockEntity a) {
            return new AABB(a.getBlockPos()).inflate(0.3, 0.1, 0.3);
        }
    }
}

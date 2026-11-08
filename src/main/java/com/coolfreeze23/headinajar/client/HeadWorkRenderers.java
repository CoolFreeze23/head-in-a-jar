package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity;
import com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity;
import com.coolfreeze23.headinajar.block.HeadJobs;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/** Heads at work: on a turret (turning to face the nearest hostile), on an employee mount, and racked in a Cerebral Array. */
public final class HeadWorkRenderers {
    private HeadWorkRenderers() {
    }

    static void drawHead(ItemStack head, PoseStack pose, MultiBufferSource buffers, int light, float age, float scale) {
        if (head.is(Items.DRAGON_HEAD)) {
            pose.pushPose();
            pose.translate(0, 0.25, 0);
            pose.scale(scale, scale, scale);
            Minecraft.getInstance().getItemRenderer().renderStatic(head, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, null, 0);
            pose.popPose();
            return;
        }
        EntityType<?> type = HeadJobs.type(head);
        if (type == null) return;
        pose.pushPose();
        pose.scale(scale, scale, scale);
        JarredHeadRenderer.drawJar(type, HeadJobs.entityTag(head), pose, buffers, light, age);
        pose.popPose();
    }

    public static class Turret implements BlockEntityRenderer<TurretPedestalBlockEntity> {
        public Turret(BlockEntityRendererProvider.Context context) {
        }

        @Override
        public void render(TurretPedestalBlockEntity t, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            if (t.getLevel() == null || t.head().isEmpty()) return;
            float age = t.getLevel().getGameTime() + partialTick;
            // turn toward the nearest hostile within range (client-side guess; the server picks the real target)
            var c = t.getBlockPos().getCenter();
            LivingEntity near = t.getLevel().getEntitiesOfClass(LivingEntity.class, new AABB(t.getBlockPos()).inflate(TurretPedestalBlockEntity.RANGE),
                    e -> e instanceof Enemy && e.isAlive()).stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(c))).orElse(null);
            float want = near == null ? t.yaw + 0.5f : (float) (Mth.atan2(near.getZ() - c.z, near.getX() - c.x) * Mth.RAD_TO_DEG) - 90;
            t.yawO = t.yaw;
            t.yaw = t.yaw + Mth.wrapDegrees(want - t.yaw) * 0.1f;
            pose.pushPose();
            pose.translate(0.5, 10 / 16f, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-Mth.lerp(partialTick, t.yawO, t.yaw)));
            drawHead(t.head(), pose, buffers, light, age, 0.7f);
            pose.popPose();
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
            drawHead(m.head(), pose, buffers, light, m.getLevel().getGameTime() + partialTick, 0.6f);
            pose.popPose();
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
            drawHead(s.head(), pose, buffers, light, s.getLevel().getGameTime() + partialTick, 0.7f);
            pose.popPose();
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
                drawHead(head, pose, buffers, light, age + i * 7, 0.26f);
                pose.popPose();
            }
            pose.popPose();
        }
    }
}

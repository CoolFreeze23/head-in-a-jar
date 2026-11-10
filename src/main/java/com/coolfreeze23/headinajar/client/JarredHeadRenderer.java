package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.block.JarredHeadBlock;
import com.coolfreeze23.headinajar.block.JarredHeadBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/** A jarred mob head: the jar, with the mob's own head (from its own model and texture) floating inside. */
public class JarredHeadRenderer implements BlockEntityRenderer<JarredHeadBlockEntity> {
    /** One stand-in mob per creature, for its model and texture. Made in the current client world; let go with it. */
    private static final Map<EntityType<?>, Entity> STAND_INS = new HashMap<>();
    private static boolean listening;

    public JarredHeadRenderer(BlockEntityRendererProvider.Context context) {
        if (!listening) {
            listening = true;
            // leaving a world (or the server): the stand-ins hold the old client world, so they go
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) -> STAND_INS.clear());
        }
    }

    @Override
    public void render(JarredHeadBlockEntity head, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        EntityType<?> type = head.entityType();
        if (type == null || head.getLevel() == null) return;
        float age = head.getLevel().getGameTime() + partialTick;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-head.getBlockState().getValue(JarredHeadBlock.FACING).toYRot()));
        pose.scale(0.8f, 0.8f, 0.8f);
        CompoundTag record = head.entityTag();
        drawJar(type, record.contains("VillagerData") ? record.getCompound("VillagerData") : null, pose, buffers, light, age);
        pose.popPose();
    }

    /** A whole jarred head (jar and the mob's head inside), at the pose's origin; {@code entityTag}: the villager data, or null. Used by turrets, mounts and the Array too. */
    public static void drawJar(EntityType<?> type, net.minecraft.nbt.CompoundTag entityTag, PoseStack pose, MultiBufferSource buffers, int light, float age) {
        JarRenderer.drawWith(pose, buffers, light, age, (p, bob) -> drawMobHead(type, entityTag, p, buffers, light, bob));
    }

    /** Draws the mob's head part, centred on the jar's neck (in jar model space). */
    @SuppressWarnings("unchecked")
    static void drawMobHead(EntityType<?> type, net.minecraft.nbt.CompoundTag entityTag, PoseStack pose, MultiBufferSource buffers, int light, float bob) {
        var mc = Minecraft.getInstance();
        Entity entity = STAND_INS.get(type);
        if (entity != null && entity.level() != mc.level) {
            // another world since they were made (a dimension change makes a new client world)
            STAND_INS.clear();
            entity = null;
        }
        if (entity == null && mc.level != null) {
            entity = type.create(mc.level);
            if (entity != null) STAND_INS.put(type, entity);
        }
        if (!(entity instanceof LivingEntity living)) return;
        if (entity instanceof net.minecraft.world.entity.npc.Villager villager && entityTag != null && !entityTag.isEmpty()) {
            net.minecraft.world.entity.npc.VillagerData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, entityTag)
                    .result().ifPresent(villager::setVillagerData);
        }
        if (!(mc.getEntityRenderDispatcher().getRenderer(entity) instanceof LivingEntityRenderer<?, ?> renderer)) return;
        if (!(renderer.getModel() instanceof HeadedModel headed)) return;
        ModelPart part = headed.getHead();
        ResourceLocation texture = ((LivingEntityRenderer<LivingEntity, ?>) renderer).getTextureLocation(living);
        PartPose saved = part.storePose();
        part.setPos(0, 0, 0);
        part.setRotation(0, 0, 0);
        pose.pushPose();
        pose.translate(0, (JarModel.NECK_Y + bob) / 16f, 0);
        pose.scale(0.85f, 0.85f, 0.85f);
        part.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        part.loadPose(saved);
    }
}

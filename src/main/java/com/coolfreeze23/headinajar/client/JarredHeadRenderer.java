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

    /** Beyond this the head stops turning (it is drawn looking ahead). */
    private static final double TURN_DISTANCE = 32;

    /**
     * The real jar at 1:1 on its plinth (the block's model), its base at (0, 7, 0) px, facing the way the head was set
     * down; the head inside turns to the nearest player, nods when a trade goes through and shakes when it refuses.
     */
    @Override
    public void render(JarredHeadBlockEntity head, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        EntityType<?> type = head.entityType();
        if (type == null || head.getLevel() == null) return;
        float age = head.getLevel().getGameTime() + partialTick;
        float yaw = net.minecraft.util.Mth.lerp(partialTick, head.yawPrev, head.yaw), pitch = net.minecraft.util.Mth.lerp(partialTick, head.pitchPrev, head.pitch);
        double now = head.getLevel().getGameTime() + partialTick;
        double nod = head.since(JarredHeadBlockEntity.EV_NOD, now), no = head.since(JarredHeadBlockEntity.EV_REFUSE, now);
        if (nod >= 0 && nod < 10) pitch += (float) (20 * Math.sin(Math.PI * nod / 10));
        if (no >= 0 && no < 12) yaw += (float) (25 * Math.sin(2 * Math.PI * no / 6) * (1 - no / 12));
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (head.getBlockPos().getCenter().distanceToSqr(camera) > TURN_DISTANCE * TURN_DISTANCE) yaw = pitch = 0;
        pose.pushPose();
        pose.translate(0.5, 7 / 16.0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-head.getBlockState().getValue(JarredHeadBlock.FACING).toYRot()));
        CompoundTag record = head.entityTag();
        drawJar(type, record.contains("VillagerData") ? record.getCompound("VillagerData") : null, pose, buffers, light, age, yaw, pitch);
        pose.popPose();
    }

    /** The jar's lid and valve rise 7 px over the cell. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(JarredHeadBlockEntity head) {
        return new net.minecraft.world.phys.AABB(head.getBlockPos()).expandTowards(0, 0.5, 0);
    }

    /** A whole jarred head (jar and the mob's head inside), at the pose's origin; {@code entityTag}: the villager data, or null. Used by turrets, mounts and the Array too. */
    public static void drawJar(EntityType<?> type, net.minecraft.nbt.CompoundTag entityTag, PoseStack pose, MultiBufferSource buffers, int light, float age) {
        drawJar(type, entityTag, pose, buffers, light, age, 0, 0);
    }

    /** As above, the head inside turned {@code yaw} degrees (clockwise from above, as a player turns) and tipped {@code pitch} (down). */
    public static void drawJar(EntityType<?> type, net.minecraft.nbt.CompoundTag entityTag, PoseStack pose, MultiBufferSource buffers, int light, float age,
                               float yaw, float pitch) {
        JarRenderer.drawWith(pose, buffers, light, age, (p, bob) -> drawMobHead(type, entityTag, p, buffers, light, bob, yaw, pitch));
    }

    /** A villager's look by its data tag, parsed once (it is drawn every frame). */
    private static final Map<CompoundTag, net.minecraft.world.entity.npc.VillagerData> VILLAGER_DATA = new HashMap<>();

    private static net.minecraft.world.entity.npc.VillagerData villagerData(CompoundTag tag) {
        var known = VILLAGER_DATA.get(tag);
        if (known != null) return known;
        var parsed = net.minecraft.world.entity.npc.VillagerData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).result().orElse(null);
        if (parsed != null) {
            if (VILLAGER_DATA.size() > 256) VILLAGER_DATA.clear();
            VILLAGER_DATA.put(tag.copy(), parsed);
        }
        return parsed;
    }

    /** A mob's head part and its texture (the part as the mob's model has it; restore its pose after drawing). */
    record Head(ModelPart part, ResourceLocation texture) {
    }

    /** The head of {@code type} (a villager dressed by its data), or null when its model has none. */
    @SuppressWarnings("unchecked")
    static Head head(EntityType<?> type, net.minecraft.nbt.CompoundTag entityTag) {
        var mc = Minecraft.getInstance();
        Entity entity = STAND_INS.get(type);
        if (entity != null && entity.level() != mc.level) {
            STAND_INS.clear();
            entity = null;
        }
        if (entity == null && mc.level != null) {
            entity = type.create(mc.level);
            if (entity != null) STAND_INS.put(type, entity);
        }
        if (!(entity instanceof LivingEntity living)) return null;
        if (entity instanceof net.minecraft.world.entity.npc.Villager villager && entityTag != null && !entityTag.isEmpty()) {
            var data = villagerData(entityTag);
            if (data != null && !data.equals(villager.getVillagerData())) villager.setVillagerData(data);
        }
        if (!(mc.getEntityRenderDispatcher().getRenderer(entity) instanceof LivingEntityRenderer<?, ?> renderer)) return null;
        if (!(renderer.getModel() instanceof HeadedModel headed)) return null;
        return new Head(headed.getHead(), ((LivingEntityRenderer<LivingEntity, ?>) renderer).getTextureLocation(living));
    }

    /** Draws the mob's head part, centred on the jar's neck (in jar model space). */
    @SuppressWarnings("unchecked")
    static void drawMobHead(EntityType<?> type, net.minecraft.nbt.CompoundTag entityTag, PoseStack pose, MultiBufferSource buffers, int light, float bob) {
        drawMobHead(type, entityTag, pose, buffers, light, bob, 0, 0);
    }

    /** As above, turned {@code yaw} and tipped {@code pitch} degrees about its neck. */
    static void drawMobHead(EntityType<?> type, net.minecraft.nbt.CompoundTag entityTag, PoseStack pose, MultiBufferSource buffers, int light, float bob,
                            float yaw, float pitch) {
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
            var data = villagerData(entityTag);
            if (data != null && !data.equals(villager.getVillagerData())) villager.setVillagerData(data);
        }
        if (!(mc.getEntityRenderDispatcher().getRenderer(entity) instanceof LivingEntityRenderer<?, ?> renderer)) return;
        if (!(renderer.getModel() instanceof HeadedModel headed)) return;
        ModelPart part = headed.getHead();
        ResourceLocation texture = ((LivingEntityRenderer<LivingEntity, ?>) renderer).getTextureLocation(living);
        PartPose saved = part.storePose();
        part.setPos(0, 0, 0);
        part.setRotation(pitch * net.minecraft.util.Mth.DEG_TO_RAD, yaw * net.minecraft.util.Mth.DEG_TO_RAD, 0);
        pose.pushPose();
        pose.translate(0, (JarModel.NECK_Y + bob) / 16f, 0);
        pose.scale(0.85f, 0.85f, 0.85f);
        part.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        part.loadPose(saved);
    }
}
